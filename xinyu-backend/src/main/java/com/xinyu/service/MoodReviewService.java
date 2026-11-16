package com.xinyu.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xinyu.dto.ReviewAiRequest;
import com.xinyu.dto.ReviewAiResponse;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.MoodRecord;
import com.xinyu.entity.MoodReview;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.mapper.MoodReviewMapper;
import com.xinyu.ratelimit.RateLimiter;
import com.xinyu.ratelimit.RateLimitType;
import com.xinyu.vo.AiJobVO;
import com.xinyu.vo.MoodReviewStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 本人回顾的保存与版本入口。创建时用户行锁跨实例去重；来源集合在短事务内读取。
 * 网络等待由 worker 管理，任何数据库锁都不跨越模型调用。
 */
@Service
public class MoodReviewService {

    /** 阶段 2 契约第 5 节：报告独立模板，不改变日记回复模板。 */
    public static final String TEMPLATE = "mood-review-v1";
    public static final String TASK_TYPE = "MOOD_REVIEW";

    private static final int MAX_QUEUED = 100;
    private static final int MAX_USER_ACTIVE = 3;
    private static final int HISTORY_PAGE_SIZE = 5;
    private static final int MAX_HISTORY_PAGE = 100_000;

    /** 与 {@link AiServiceClient} 及 Python {@code main.py} 错误分类一致的安全错误码集合。 */
    private static final Set<String> ALLOWED_ERROR_CODES = Set.of(
            "invalid_request", "unauthorized", "provider_timeout", "provider_error",
            "invalid_model_response", "overloaded", "execution_interrupted",
            "capacity_exceeded", "internal_error");

    /** 与 {@link ReviewAiResponse} 同口径，从已保存正文中识别引用标记。 */
    private static final Pattern CITED_REF = Pattern.compile("\\{\\{(r[1-9][0-9]*)}}");

    private final MoodReviewMapper reports;
    private final MoodReviewStatsService stats;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final AiJobAdmission admission;
    private final RateLimiter rateLimiter;
    private AiJobMapper reviewJobs;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** 报告引用的本人有效记录对照；content 返回当前全文且不截断。 */
    public record Source(String ref, long recordId, LocalDate date, String weekday, String moodType, String content) { }

    /** 对外报告仅返回正文实际引用到的有效本人来源；删除任何来源时隐藏旧信并清空 sources。 */
    public record Report(long id, int revision, String status, String body, String ps, String resultKind,
                         boolean stale, long jobId, LocalDate rangeStart, LocalDate rangeEnd,
                         int aiEnabledCount, String generatedAt, List<Source> sources) { }

    public record Page(MoodReviewStatsVO stats, Report report, Report active, Report failed, int aiEnabledCount) { }

    public record History(long id, LocalDate rangeStart, LocalDate rangeEnd, int revision, String status, int total) { }

    /** worker 的发送预检；短事务结束后才可执行网络。 */
    public record Prepared(ReviewAiRequest request, ReviewAiResponse support) { }

    private record AttemptContext(MoodReview row, AiJob job, List<MoodRecord> records, boolean crisis) { }

    public MoodReviewService(MoodReviewMapper reports, MoodReviewStatsService stats,
                             @Qualifier("reviewClock") Clock clock, PlatformTransactionManager manager,
                             AiJobAdmission admission, RateLimiter rateLimiter) {
        this.reports = reports;
        this.stats = stats;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        this.admission = admission;
        this.rateLimiter = rateLimiter;
    }

    @Autowired
    public void setReviewJobs(AiJobMapper reviewJobs) {
        this.reviewJobs = reviewJobs;
    }

    // =====================================================================
    // 一、用户端读写与重试入口
    // =====================================================================

    /** 创建或复用当前范围的任务；未结束的周、无来源和无 AI 授权分别拒绝。 */
    public Report generate(long userId, String type, String start, boolean regenerate) {
        requireGenerateQuota(userId, type, start, regenerate);
        return admission.save(() -> {
            // 用户锁先于所有一致性读取，等待后不会读到加锁前的 RR 快照。
            lockUser(userId);
            MoodReviewStatsVO snapshot = statistics(userId, type, start);
            if ("WEEK".equals(type) && snapshot.rangeEnd().isAfter(LocalDate.now(clock))) {
                throw http(HttpStatus.BAD_REQUEST, "week_not_finished");
            }

            List<MoodRecord> records = sources(userId, snapshot.rangeStart(), snapshot.rangeEnd(), true);
            snapshot = MoodReviewStatsService.aggregate(records, snapshot.rangeStart(), snapshot.rangeEnd());
            if (records.isEmpty()) {
                throw http(HttpStatus.BAD_REQUEST, "no_records");
            }
            if (records.stream().noneMatch(record -> Boolean.TRUE.equals(record.getAiReplyEnabled()))) {
                throw http(HttpStatus.BAD_REQUEST, "no_ai_enabled_records");
            }

            MoodReview active = latest(userId, type, snapshot.rangeStart(), true);
            if (active != null) {
                return view(active, records, false);
            }
            MoodReview success = latest(userId, type, snapshot.rangeStart(), false);
            String fingerprint = fingerprint(records);
            if (!regenerate && success != null && success.fingerprint().equals(fingerprint)) {
                return view(success, records, false);
            }

            requireCapacity(userId);
            int revision = reports.nextRevision(userId, type, snapshot.rangeStart());
            long id = reports.insert(userId, type, snapshot.rangeStart(), snapshot.rangeEnd(),
                    revision, fingerprint, encodeSnapshot(snapshot, records));
            for (MoodRecord record : records) {
                reports.insertSource(id, record.getId(), record.getContentVersion());
            }
            reports.insertJob(userId, id, revision, TEMPLATE);
            return view(load(id), records, false);
        });
    }

    /** 同一只读快照计算过期状态；GET 不写库，也不会排任务。 */
    public Page get(long userId, String type, String start) {
        return transactions.execute(status -> {
            MoodReviewStatsVO snapshot = statistics(userId, type, start);
            LocalDate successScope = "WEEK".equals(type) ? snapshot.rangeStart() : null;
            MoodReview success = latest(userId, type, successScope, false);
            MoodReview active = latest(userId, type, snapshot.rangeStart(), true);
            List<MoodRecord> records = sources(userId, snapshot.rangeStart(), snapshot.rangeEnd(), false);

            int aiEnabledCount = (int) records.stream()
                    .filter(record -> Boolean.TRUE.equals(record.getAiReplyEnabled()))
                    .count();
            boolean rangeChanged = success != null
                    && (!success.start().equals(snapshot.rangeStart()) || !success.end().equals(snapshot.rangeEnd()));

            Report successView = success == null ? null : view(success, records, rangeChanged);
            Report activeView = active == null ? null : view(active, records, false);
            Report failedView = latestFailed(userId, type, snapshot.rangeStart(), records);
            return new Page(snapshot, successView, activeView, failedView, aiEnabledCount);
        });
    }

    /** 周报历史仅返回元信息与快照条数，不含正文。 */
    public List<History> history(long userId, int page) {
        if (page < 1 || page > MAX_HISTORY_PAGE) {
            throw http(HttpStatus.BAD_REQUEST, "invalid_page");
        }
        int offset = (page - 1) * HISTORY_PAGE_SIZE;
        return reports.history(userId, HISTORY_PAGE_SIZE, offset).stream()
                .map(row -> new History(
                        row.id(),
                        row.start(),
                        row.end(),
                        row.revision(),
                        row.status(),
                        decodeStats(row.statsJson()).total()))
                .toList();
    }

    /** 任务查询沿用统一 VO，仍从报告来源计算删除隐藏；不返回原始模型异常。 */
    public AiJobVO getOwned(long userId, long jobId) {
        return transactions.execute(status -> ownedView(ownedJob(userId, jobId)));
    }

    /** 用户显式重试同一任务，复用 1a 冷却与容量门禁，不自动调用模型。 */
    public AiJobVO retry(long userId, long jobId) {
        requireRetryQuota(userId, jobId);
        return admission.save(() -> {
            lockUser(userId);
            AiJob observed = ownedJob(userId, jobId);
            MoodReview row = load(observed.getTargetId());
            List<MoodRecord> records = sources(userId, row.start(), row.end(), true);

            AiJob job = lockJob(jobId);
            if (!matches(row, job, records)) {
                throw http(HttpStatus.CONFLICT, "source_changed");
            }
            if (List.of("queued", "running", "succeeded").contains(job.getStatus())) {
                return ownedView(job);
            }
            if (!"failed".equals(job.getStatus())) {
                throw http(HttpStatus.CONFLICT, "job_not_retryable");
            }

            MoodReview active = latest(userId, row.type(), row.start(), true);
            if (active != null && active.id() != row.id()) {
                throw http(HttpStatus.CONFLICT, "review_already_active");
            }

            int cooldown = AiJobRetryPolicy.cooldownSeconds(job.getAttemptCount());
            int remaining = reviewJobs.selectRetryAfterSeconds(job, cooldown);
            if (remaining > 0) {
                throw tooManyRequestsWithRetryAfter(remaining, "review_cooldown");
            }

            requireCapacity(userId);
            if (reviewJobs.requeueReviewAfterCooldown(job, cooldown) != 1) {
                throw http(HttpStatus.CONFLICT, "job_changed");
            }
            reports.markActive(row.id(), "queued");
            return ownedView(job(jobId));
        });
    }

    // =====================================================================
    // 二、Worker 生命周期衔接（短事务预检、收尾与启动恢复）
    // =====================================================================

    /** 仅在 worker 的领取短事务中调用；领取后同步报告状态。 */
    public AiJob claimNext() {
        AiJob candidate = reviewJobs.selectNextReviewQueued();
        if (candidate == null) {
            return null;
        }
        String requestId = UUID.randomUUID().toString();
        if (reviewJobs.claimReview(candidate.getId(), candidate.getAttemptCount(), requestId) != 1) {
            return null;
        }
        reports.markActive(candidate.getTargetId(), "running");
        return reviewJobs.selectForUpdate(candidate.getId());
    }

    /** 预检全部来源，先危机检查再构造最小授权请求；来源变化返回 null 并失效任务。 */
    public Prepared prepare(long jobId, int attempt, String requestId) {
        return transactions.execute(status -> {
            AttemptContext context = lockAndVerifyAttempt(jobId, attempt, requestId);
            if (context == null) {
                return null;
            }

            MoodReview row = context.row();
            ReviewAiRequest request = buildRequest(row, context.job(), context.records(), context.crisis());

            // 保存本次真正发出的子集：抽样顺序由 create_time,id 稳定确定，别名按子集从 r1 编号。
            reports.resetSentSources(row.id());
            if (!context.crisis()) {
                for (MoodRecord record : MoodReviewPayloadBuilder.sampled(context.records())) {
                    reports.markSentSource(row.id(), record.getId());
                }
            }

            ReviewAiResponse support = context.crisis()
                    ? new ReviewAiResponse(requestId, request.jobId(), attempt, request.sourceVersion(),
                            MoodReviewSafety.SUPPORT_TEXT, null, "SUPPORT", false, TEMPLATE, null)
                    : null;
            return new Prepared(request, support);
        });
    }

    /** 来源范围先锁、任务后锁；结果校验及两表终态在一个事务提交，拒绝迟到结果。 */
    public boolean complete(long jobId, int attempt, String requestId, ReviewAiResponse response) {
        return transactions.execute(status -> {
            AttemptContext context = lockAndVerifyAttempt(jobId, attempt, requestId);
            if (context == null) {
                return false;
            }

            MoodReview row = context.row();
            AiJob locked = context.job();
            try {
                if (response == null) {
                    throw new IllegalArgumentException("invalid_model_response");
                }
                ReviewAiRequest expected = buildRequest(row, locked, context.records(), context.crisis());
                response.validate(expected);
                if (context.crisis()
                        && (!"SUPPORT".equals(response.resultKind())
                                || !MoodReviewSafety.SUPPORT_TEXT.equals(response.body()))) {
                    throw new IllegalArgumentException("invalid_model_response");
                }
            } catch (IllegalArgumentException invalid) {
                finish(locked, "failed", "invalid_model_response", null);
                return false;
            }

            if (!finish(locked, "succeeded", null, response)) {
                return false;
            }
            reports.saveResult(row.id(), response);
            return true;
        });
    }

    /** 在 worker 的失败短事务中以尝试身份条件收尾，不推测是否调用模型。 */
    public boolean fail(long id, int attempt, String requestId, String code) {
        AiJob job = job(id);
        if (!current(job, attempt, requestId)) {
            return false;
        }
        String safe = ALLOWED_ERROR_CODES.contains(code == null ? "" : code) ? code : "internal_error";
        return finish(job, "failed", safe, null);
    }

    /** 在唯一 worker 启动恢复事务中同步两表，保留历史成功报告。 */
    public int recoverInterrupted() {
        int changed = reviewJobs.failInterruptedReviews();
        reports.recoverInterrupted();
        return changed;
    }

    // =====================================================================
    // 三、包内纯函数委托与内部辅助
    // =====================================================================

    static String weekday(DayOfWeek day) {
        return MoodReviewPayloadBuilder.weekday(day);
    }

    static List<Integer> allocateBudgets(List<Integer> lengths, int totalBudget) {
        return MoodReviewPayloadBuilder.allocateBudgets(lengths, totalBudget);
    }

    static String clip(String content, int budget) {
        return MoodReviewPayloadBuilder.clip(content, budget);
    }

    /** id/version 集合按 id 排序，不受日内记录顺序影响。 */
    static String fingerprint(List<MoodRecord> records) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            records.stream()
                    .sorted(Comparator.comparing(MoodRecord::getId))
                    .forEach(record -> digest.update(
                            (record.getId() + ":" + record.getContentVersion() + "\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private AttemptContext lockAndVerifyAttempt(long jobId, int attempt, String requestId) {
        AiJob observed = job(jobId);
        if (!current(observed, attempt, requestId)) {
            return null;
        }

        MoodReview row = load(observed.getTargetId());
        List<MoodRecord> records = sources(row.userId(), row.start(), row.end(), true);
        AiJob locked = lockJob(jobId);
        if (!current(locked, attempt, requestId)) {
            return null;
        }
        if (!matches(row, locked, records)) {
            finish(locked, "superseded", null, null);
            return null;
        }

        boolean crisis = records.stream().anyMatch(record -> MoodReviewSafety.matches(record.getContent()));
        return new AttemptContext(row, locked, records, crisis);
    }

    private ReviewAiRequest buildRequest(MoodReview row, AiJob job, List<MoodRecord> records, boolean crisis) {
        return MoodReviewPayloadBuilder.buildRequest(row, job, records, decodeStats(row.statsJson()), crisis);
    }

    private Report latestFailed(long userId, String type, LocalDate start, List<MoodRecord> records) {
        MoodReview latestAttempt = reports.latestAttempt(userId, type, start);
        if (latestAttempt == null || !"failed".equals(latestAttempt.status())) {
            return null;
        }
        if (!"failed".equals(reports.jobStatus(latestAttempt.id(), latestAttempt.revision()))) {
            return null;
        }
        Report candidate = view(latestAttempt, records, false);
        return candidate.stale() ? null : candidate;
    }

    private boolean matches(MoodReview row, AiJob job, List<MoodRecord> records) {
        return row != null
                && row.userId() == job.getUserId()
                && row.revision() == job.getTargetVersion()
                && row.fingerprint().equals(fingerprint(records));
    }

    private boolean finish(AiJob job, String state, String error, ReviewAiResponse response) {
        String resultKind = response == null ? null : response.resultKind();
        Boolean modelCalled = response == null ? null : response.modelCalled();
        String modelName = response == null ? null : response.modelName();
        int changed = reviewJobs.finishReviewAttempt(job, state, error, resultKind, modelCalled, modelName);
        if (changed == 1) {
            boolean superseded = "superseded".equals(state);
            String reportState = superseded ? "failed" : state;
            String reportError = error == null && superseded ? "source_changed" : error;
            reports.finish(job.getTargetId(), reportState, reportError);
        }
        return changed == 1;
    }

    private AiJob job(long id) {
        return reviewJobs.selectById(id);
    }

    private AiJob lockJob(long id) {
        return reviewJobs.selectForUpdate(id);
    }

    private static boolean current(AiJob job, int attempt, String requestId) {
        return job != null
                && TASK_TYPE.equals(job.getTaskType())
                && "running".equals(job.getStatus())
                && job.getAttemptCount() == attempt
                && Objects.equals(job.getRequestId(), requestId);
    }

    private AiJob ownedJob(long userId, long jobId) {
        AiJob job = job(jobId);
        if (job == null || !TASK_TYPE.equals(job.getTaskType())) {
            throw http(HttpStatus.NOT_FOUND, "job_not_found");
        }
        if (job.getUserId() != userId) {
            throw http(HttpStatus.FORBIDDEN, "job_forbidden");
        }
        return job;
    }

    private AiJobVO ownedView(AiJob job) {
        MoodReview row = load(job.getTargetId());
        Report report = view(row, sources(row.userId(), row.start(), row.end(), false), false);
        boolean succeeded = "succeeded".equals(job.getStatus());
        boolean failed = "failed".equals(job.getStatus());
        int remaining = failed
                ? reviewJobs.selectRetryAfterSeconds(job, AiJobRetryPolicy.cooldownSeconds(job.getAttemptCount()))
                : 0;

        String resultKind = succeeded ? job.getResultKind() : null;
        String replyBody = succeeded ? report.body() : null;
        String errorCode = failed ? job.getLastErrorCode() : null;
        boolean retryable = failed && remaining == 0 && !report.stale();
        return new AiJobVO(
                job.getId(),
                job.getTargetId(),
                job.getTargetVersion(),
                job.getStatus(),
                job.getAttemptCount(),
                resultKind,
                replyBody,
                errorCode,
                retryable,
                remaining);
    }

    private MoodReviewStatsVO statistics(long userId, String type, String start) {
        if ("WEEK".equals(type)) {
            return stats.getWeekStats(userId, start);
        }
        if ("RECENT_30".equals(type)) {
            return stats.getRecent30Stats(userId);
        }
        throw http(HttpStatus.BAD_REQUEST, "invalid_review_type");
    }

    private void lockUser(long userId) {
        if (!reports.lockUser(userId)) {
            throw http(HttpStatus.NOT_FOUND, "user_not_found");
        }
    }

    /**
     * 生成入口的限流预检（准入事务外，契约 2.5）：按快照预判“确定会新建生成任务”才扣额度——
     * 直接返回已有报告（指纹未变且非 regenerate）、已有进行中任务、本周未结束、无记录或无授权记录
     * 都不会创建任务，不扣（做法同 MoodRecordService 的修改入口预检）。
     * 危机豁免在其后：含危机记录的回顾由固定 SUPPORT 回应、不产生费用，不计额度也不应被额度挡住（契约 2.1）。
     * 快照与事务内锁定读取存在窗口，误判按“已扣不退”接受；正常业务校验仍在事务内原样执行。
     */
    private void requireGenerateQuota(long userId, String type, String start, boolean regenerate) {
        MoodReviewStatsVO snapshot = statistics(userId, type, start);
        if ("WEEK".equals(type) && snapshot.rangeEnd().isAfter(LocalDate.now(clock))) {
            return;
        }
        List<MoodRecord> records = sources(userId, snapshot.rangeStart(), snapshot.rangeEnd(), false);
        if (records.isEmpty()) {
            return;
        }
        if (records.stream().noneMatch(record -> Boolean.TRUE.equals(record.getAiReplyEnabled()))) {
            return;
        }
        if (latest(userId, type, snapshot.rangeStart(), true) != null) {
            return;
        }
        MoodReview success = latest(userId, type, snapshot.rangeStart(), false);
        if (!regenerate && success != null && success.fingerprint().equals(fingerprint(records))) {
            return;
        }
        if (records.stream().anyMatch(record -> MoodReviewSafety.matches(record.getContent()))) {
            return;
        }
        RateLimiter.Decision decision = rateLimiter.check(RateLimitType.REVIEW, userId);
        if (!decision.allowed()) {
            throw tooManyRequestsWithRetryAfter(decision.retryAfterSeconds(), "review_rate_limited");
        }
    }

    /**
     * 重试入口的限流预检（准入事务外）：快照判断确定会重新入队才扣——幂等状态、不可重试状态、
     * 冷却中、已有其他进行中任务或来源已变化都不扣；危机豁免同生成入口。
     * 归属与状态的正式校验仍在准入事务内。
     */
    private void requireRetryQuota(long userId, long jobId) {
        AiJob observed = job(jobId);
        if (observed == null || !TASK_TYPE.equals(observed.getTaskType())
                || observed.getUserId() != userId) {
            return;
        }
        if (List.of("queued", "running", "succeeded").contains(observed.getStatus())) {
            return;
        }
        if (!"failed".equals(observed.getStatus())) {
            return;
        }
        MoodReview row = load(observed.getTargetId());
        if (row == null) {
            return;
        }
        List<MoodRecord> records = sources(userId, row.start(), row.end(), false);
        if (records.isEmpty() || !row.fingerprint().equals(fingerprint(records))) {
            return;
        }
        MoodReview active = latest(userId, row.type(), row.start(), true);
        if (active != null && active.id() != row.id()) {
            return;
        }
        if (records.stream().anyMatch(record -> MoodReviewSafety.matches(record.getContent()))) {
            return;
        }
        int cooldown = AiJobRetryPolicy.cooldownSeconds(observed.getAttemptCount());
        if (cooldown > 0) {
            Integer remaining = reviewJobs.selectRetryAfterSeconds(observed, cooldown);
            if (remaining == null || remaining > 0) {
                return;
            }
        }
        RateLimiter.Decision decision = rateLimiter.check(RateLimitType.REVIEW, userId);
        if (!decision.allowed()) {
            throw tooManyRequestsWithRetryAfter(decision.retryAfterSeconds(), "review_rate_limited");
        }
    }

    private void requireCapacity(long userId) {
        int queued = reports.queuedCount();
        int active = reports.activeCount(userId);
        if (queued >= MAX_QUEUED || active >= MAX_USER_ACTIVE) {
            throw http(HttpStatus.SERVICE_UNAVAILABLE, "capacity_exceeded");
        }
    }

    private MoodReview latest(long userId, String type, LocalDate start, boolean active) {
        return reports.latest(userId, type, start, active);
    }

    private MoodReview load(long id) {
        return reports.load(id);
    }

    private List<MoodRecord> sources(long userId, LocalDate start, LocalDate end, boolean lock) {
        return reports.sources(userId, start, end, lock);
    }

    private Report view(MoodReview row, List<MoodRecord> current, boolean rangeChanged) {
        boolean stale = rangeChanged || !row.fingerprint().equals(fingerprint(current));
        int deleted = reports.deletedSourceCount(row.userId(), row.id());
        long jobId = reports.jobId(row.id(), row.revision());
        int enabled = enabledTotal(row.statsJson());

        boolean hidden = deleted > 0;
        String visibleBody = hidden ? null : row.body();
        String visiblePs = hidden ? null : row.ps();
        List<Source> citedSources = hidden ? List.of() : citedSources(row);
        return new Report(
                row.id(),
                row.revision(),
                row.status(),
                visibleBody,
                visiblePs,
                row.kind(),
                stale || hidden,
                jobId,
                row.start(),
                row.end(),
                enabled,
                row.finishedAt(),
                citedSources);
    }

    private List<Source> citedSources(MoodReview row) {
        if (!"succeeded".equals(row.status()) || !"MODEL".equals(row.kind()) || row.body() == null) {
            return List.of();
        }
        Set<String> cited = extractCitedRefs(row.body(), row.ps());
        if (cited.isEmpty()) {
            return List.of();
        }

        List<MoodRecord> sent = reports.sentSources(row.userId(), row.id());
        List<Source> result = new ArrayList<>();
        for (int index = 0; index < sent.size(); index++) {
            String ref = "r" + (index + 1);
            if (!cited.contains(ref)) {
                continue;
            }
            MoodRecord record = sent.get(index);
            result.add(new Source(
                    ref,
                    record.getId(),
                    record.getCreateTime().toLocalDate(),
                    weekday(record.getCreateTime().getDayOfWeek()),
                    record.getMoodType(),
                    record.getContent()));
        }
        return List.copyOf(result);
    }

    private static Set<String> extractCitedRefs(String body, String ps) {
        Set<String> refs = new HashSet<>();
        for (String text : new String[]{body, ps == null ? "" : ps}) {
            var matcher = CITED_REF.matcher(text);
            while (matcher.find()) {
                refs.add(matcher.group(1));
            }
        }
        return refs;
    }

    private String encodeSnapshot(MoodReviewStatsVO snapshot, List<MoodRecord> records) {
        ObjectNode value = json.valueToTree(snapshot);
        int enabled = (int) records.stream().filter(record -> Boolean.TRUE.equals(record.getAiReplyEnabled())).count();
        value.put("aiEnabledTotal", enabled);
        return encode(value);
    }

    private int enabledTotal(String snapshot) {
        try {
            return json.readTree(snapshot).path("aiEnabledTotal").asInt();
        } catch (IOException invalid) {
            throw new IllegalStateException("invalid_review_snapshot", invalid);
        }
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (IOException invalid) {
            throw new IllegalStateException("invalid_review_snapshot", invalid);
        }
    }

    private MoodReviewStatsVO decodeStats(String value) {
        try {
            return json.readValue(value, MoodReviewStatsVO.class);
        } catch (IOException invalid) {
            throw new IllegalStateException("invalid_review_snapshot", invalid);
        }
    }

    /** 冷却与限流共用的 429 返回；限流消息 review_rate_limited 由前端文案表单独映射。 */
    private static ResponseStatusException tooManyRequestsWithRetryAfter(int remaining, String code) {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, code) {
            @Override
            public HttpHeaders getHeaders() {
                var headers = new HttpHeaders();
                headers.set("Retry-After", Integer.toString(remaining));
                return headers;
            }
        };
    }

    private static ResponseStatusException http(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
