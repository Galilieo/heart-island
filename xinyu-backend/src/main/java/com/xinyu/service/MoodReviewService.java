package com.xinyu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.MoodRecord;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.vo.MoodReviewStatsVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import com.xinyu.mapper.MoodReviewMapper;
import com.xinyu.entity.MoodReview;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 本人回顾的保存与版本入口。创建时用户行锁跨实例去重；来源集合在短事务内读取。
 * 网络等待由 worker 管理，任何数据库锁都不跨越模型调用。
 */
@Service
public class MoodReviewService {
    /** 阶段2契约第5节：报告独立模板，不改变日记回复模板。 */
    public static final String TEMPLATE = "mood-review-v1";
    public static final String TASK_TYPE = "MOOD_REVIEW";
    private static final int MAX_QUEUED = 100;
    private static final int MAX_USER_ACTIVE = 3;
    private static final int HISTORY_PAGE_SIZE = 20;
    /** 实施方案5.3节与既有生成预算：60条、每条300 UTF-16单元、Python最多40秒。 */
    private static final int MAX_SENT_RECORDS = 60;
    private static final int MAX_RECORD_UTF16 = 300;
    private static final int PROVIDER_BUDGET_MS = 40_000;
    private final MoodReviewMapper reports;
    private final MoodReviewStatsService stats;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final AiJobAdmission admission;
    private AiJobMapper reviewJobs;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** 对外报告不暴露用户或来源正文；删除任何来源时隐藏旧信。 */
    public record Report(long id, int revision, String status, String body, String ps, String resultKind,
                         boolean stale, long jobId, LocalDate rangeStart, LocalDate rangeEnd,
                         int aiEnabledCount, String generatedAt) { }
    public record Page(MoodReviewStatsVO stats, Report report, Report active) { }
    public record History(long id, LocalDate rangeStart, LocalDate rangeEnd, int revision, String status, int total) { }
    public MoodReviewService(MoodReviewMapper reports, MoodReviewStatsService stats,
                             @Qualifier("reviewClock") Clock clock, PlatformTransactionManager manager,
                             AiJobAdmission admission) {
        this.reports = reports;
        this.stats = stats;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        this.admission = admission;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setReviewJobs(AiJobMapper reviewJobs) {
        this.reviewJobs = reviewJobs;
    }

    /** 创建或复用当前范围的任务；未结束的周、无来源和无AI授权分别拒绝。 */
    public Report generate(long userId, String type, String start, boolean regenerate) {
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
            long id = reports.insert(userId, type, snapshot.rangeStart(), snapshot.rangeEnd(), revision, fingerprint, encodeSnapshot(snapshot, records));
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
            MoodReview success = latest(userId, type, "WEEK".equals(type) ? snapshot.rangeStart() : null, false);
            MoodReview active = latest(userId, type, snapshot.rangeStart(), true);
            List<MoodRecord> records = sources(userId, snapshot.rangeStart(), snapshot.rangeEnd(), false);
            boolean rangeChanged = success != null && (!success.start().equals(snapshot.rangeStart()) || !success.end().equals(snapshot.rangeEnd()));
            return new Page(snapshot, success == null ? null : view(success, records, rangeChanged),
                    active == null ? null : view(active, records, false));
        });
    }

    /** 周报历史仅返回元信息与快照条数，不含正文。 */
    public List<History> history(long userId, int page) {
        if (page < 1 || page > 100_000) {
            throw http(HttpStatus.BAD_REQUEST, "invalid_page");
        }
        return reports.history(userId, HISTORY_PAGE_SIZE, (page - 1) * HISTORY_PAGE_SIZE).stream()
                .map(row -> new History(row.id(), row.start(), row.end(), row.revision(), row.status(),
                        decodeStats(row.statsJson()).total())).toList();
    }

    /** worker 的发送预检；短事务结束后才可执行网络。 */
    public record Prepared(com.xinyu.dto.ReviewAiRequest request, com.xinyu.dto.ReviewAiResponse support) { }

    /** 预检全部来源，先危机检查再构造最小授权请求；来源变化返回 null 并失效任务。 */
    public Prepared prepare(long jobId, int attempt, String requestId) {
        return transactions.execute(status -> {
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
            com.xinyu.dto.ReviewAiRequest request = buildRequest(row, locked, records, crisis);
            // 保存本次真正发出的子集：抽样顺序由 create_time,id 稳定确定，别名按子集从 r1 编号。
            reports.resetSentSources(row.id());
            if (!crisis) {
                for (MoodRecord record : sampled(records)) {
                    reports.markSentSource(row.id(), record.getId());
                }
            }
            com.xinyu.dto.ReviewAiResponse support = crisis
                    ? new com.xinyu.dto.ReviewAiResponse(requestId, request.jobId(), attempt, request.sourceVersion(),
                            MoodReviewSafety.SUPPORT_TEXT, null, "SUPPORT", false, TEMPLATE, null)
                    : null;
            return new Prepared(request, support);
        });
    }

    /** 来源范围先锁、任务后锁；结果校验及两表终态在一个事务提交，拒绝迟到结果。 */
    public boolean complete(long jobId, int attempt, String requestId, com.xinyu.dto.ReviewAiResponse response) {
        return transactions.execute(status -> {
            AiJob observed = job(jobId);
            if (!current(observed, attempt, requestId)) {
                return false;
            }
            MoodReview row = load(observed.getTargetId());
            List<MoodRecord> records = sources(row.userId(), row.start(), row.end(), true);
            AiJob locked = lockJob(jobId);
            if (!current(locked, attempt, requestId)) {
                return false;
            }
            if (!matches(row, locked, records)) {
                finish(locked, "superseded", null, null);
                return false;
            }
            boolean crisis = records.stream().anyMatch(record -> MoodReviewSafety.matches(record.getContent()));
            try {
                if (response == null) {
                    throw new IllegalArgumentException("invalid_model_response");
                }
                response.validate(buildRequest(row, locked, records, crisis));
                if (crisis && (!"SUPPORT".equals(response.resultKind()) || !MoodReviewSafety.SUPPORT_TEXT.equals(response.body()))) {
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

    private boolean matches(MoodReview row, AiJob job, List<MoodRecord> records) {
        return row != null && row.userId() == job.getUserId() && row.revision() == job.getTargetVersion()
                && row.fingerprint().equals(fingerprint(records));
    }

    private com.xinyu.dto.ReviewAiRequest buildRequest(MoodReview row, AiJob job, List<MoodRecord> records, boolean crisis) {
        List<com.xinyu.dto.ReviewAiRequest.Entry> entries = new ArrayList<>();
        if (!crisis) {
            for (MoodRecord record : sampled(records)) {
                String content = truncate(record.getContent());
                entries.add(new com.xinyu.dto.ReviewAiRequest.Entry("r" + (entries.size() + 1),
                        record.getCreateTime().toLocalDate().toString(), record.getCreateTime().getDayOfWeek().name(),
                        record.getMoodType(), content));
            }
        }
        int enabled = (int) records.stream().filter(record -> Boolean.TRUE.equals(record.getAiReplyEnabled())).count();
        return new com.xinyu.dto.ReviewAiRequest(job.getRequestId(), job.getId().toString(), job.getAttemptCount(),
                job.getTargetVersion().toString(), row.type(), List.copyOf(entries), enabled, decodeStats(row.statsJson()), PROVIDER_BUDGET_MS);
    }

    /** 阶段2第5.3节：最多60条，超过时等距取样，含首尾；300 UTF-16单元避免切断代理对。 */
    private static List<MoodRecord> sampled(List<MoodRecord> records) {
        List<MoodRecord> enabled = records.stream().filter(record -> Boolean.TRUE.equals(record.getAiReplyEnabled())).toList();
        if (enabled.size() <= MAX_SENT_RECORDS) {
            return enabled;
        }
        List<MoodRecord> sample = new ArrayList<>();
        for (int index = 0; index < MAX_SENT_RECORDS; index++) {
            sample.add(enabled.get(index * (enabled.size() - 1) / (MAX_SENT_RECORDS - 1)));
        }
        return sample;
    }

    private static String truncate(String content) {
        if (content.length() <= MAX_RECORD_UTF16) {
            return content;
        }
        int end = Character.isHighSurrogate(content.charAt(MAX_RECORD_UTF16 - 1)) ? MAX_RECORD_UTF16 - 1 : MAX_RECORD_UTF16;
        return content.substring(0, end);
    }

    /** 仅在 worker 的领取短事务中调用；领取后同步报告状态。 */
    public AiJob claimNext() {
        AiJob candidate = reviewJobs.selectNextReviewQueued();
        if (candidate == null) {
            return null;
        }
        if (reviewJobs.claimReview(candidate.getId(), candidate.getAttemptCount(), java.util.UUID.randomUUID().toString()) != 1) {
            return null;
        }
        reports.markActive(candidate.getTargetId(), "running");
        return reviewJobs.selectForUpdate(candidate.getId());
    }

    /** 在 worker 的失败短事务中以尝试身份条件收尾，不推测是否调用模型。 */
    public boolean fail(long id, int attempt, String requestId, String code) {
        AiJob job = job(id);
        if (!current(job, attempt, requestId)) {
            return false;
        }
        String safe = java.util.Set.of("invalid_request", "unauthorized", "provider_timeout", "provider_error",
                "invalid_model_response", "overloaded", "execution_interrupted", "capacity_exceeded", "internal_error")
                .contains(code == null ? "" : code) ? code : "internal_error";
        return finish(job, "failed", safe, null);
    }

    private boolean finish(AiJob job, String state, String error, com.xinyu.dto.ReviewAiResponse response) {
        int changed = reviewJobs.finishReviewAttempt(job, state, error, response == null ? null : response.resultKind(),
                response == null ? null : response.modelCalled(), response == null ? null : response.modelName());
        if (changed == 1) {
            reports.finish(job.getTargetId(), "superseded".equals(state) ? "failed" : state,
                    error == null && "superseded".equals(state) ? "source_changed" : error);
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
        return job != null && TASK_TYPE.equals(job.getTaskType()) && "running".equals(job.getStatus())
                && job.getAttemptCount() == attempt && Objects.equals(job.getRequestId(), requestId);
    }

    /** 在唯一 worker 启动恢复事务中同步两表，保留历史成功报告。 */
    public int recoverInterrupted() {
        int changed = reviewJobs.failInterruptedReviews();
        reports.recoverInterrupted();
        return changed;
    }

    /** 任务查询沿用统一 VO，仍从报告来源计算删除隐藏；不返回原始模型异常。 */
    public com.xinyu.vo.AiJobVO getOwned(long userId, long jobId) {
        return transactions.execute(status -> ownedView(ownedJob(userId, jobId)));
    }

    /** 用户显式重试同一任务，复用1a冷却与容量门禁，不自动调用模型。 */
    public com.xinyu.vo.AiJobVO retry(long userId, long jobId) {
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
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "review_cooldown") {
                    @Override
                    public org.springframework.http.HttpHeaders getHeaders() {
                        var headers = new org.springframework.http.HttpHeaders();
                        headers.set("Retry-After", Integer.toString(remaining));
                        return headers;
                    }
                };
            }
            requireCapacity(userId);
            if (reviewJobs.requeueReviewAfterCooldown(job, cooldown) != 1) {
                throw http(HttpStatus.CONFLICT, "job_changed");
            }
            reports.markActive(row.id(), "queued");
            return ownedView(job(jobId));
        });
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

    private com.xinyu.vo.AiJobVO ownedView(AiJob job) {
        MoodReview row = load(job.getTargetId());
        Report report = view(row, sources(row.userId(), row.start(), row.end(), false), false);
        boolean failed = "failed".equals(job.getStatus());
        int remaining = failed ? reviewJobs.selectRetryAfterSeconds(job, AiJobRetryPolicy.cooldownSeconds(job.getAttemptCount())) : 0;
        return new com.xinyu.vo.AiJobVO(job.getId(), job.getTargetId(), job.getTargetVersion(), job.getStatus(),
                job.getAttemptCount(), "succeeded".equals(job.getStatus()) ? job.getResultKind() : null,
                "succeeded".equals(job.getStatus()) ? report.body() : null, failed ? job.getLastErrorCode() : null,
                failed && remaining == 0 && !report.stale(), remaining);
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

    /** id/version 集合按 id 排序，不受日内记录顺序影响。 */
    static String fingerprint(List<MoodRecord> records) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            records.stream().sorted(java.util.Comparator.comparing(MoodRecord::getId)).forEach(record ->
                    digest.update((record.getId() + ":" + record.getContentVersion() + "\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private Report view(MoodReview row, List<MoodRecord> current, boolean rangeChanged) {
        boolean stale = rangeChanged || !row.fingerprint().equals(fingerprint(current));
        int deleted = reports.deletedSourceCount(row.userId(), row.id());
        long jobId = reports.jobId(row.id(), row.revision());
        int enabled = enabledTotal(row.statsJson());
        return new Report(row.id(), row.revision(), row.status(), deleted > 0 ? null : row.body(),
                deleted > 0 ? null : row.ps(), row.kind(), stale || deleted > 0, jobId,
                row.start(), row.end(), enabled, row.finishedAt());
    }

    private String encodeSnapshot(MoodReviewStatsVO snapshot, List<MoodRecord> records) {
        com.fasterxml.jackson.databind.node.ObjectNode value = json.valueToTree(snapshot);
        int enabled = (int) records.stream().filter(record -> Boolean.TRUE.equals(record.getAiReplyEnabled())).count();
        value.put("aiEnabledTotal", enabled);
        return encode(value);
    }

    private int enabledTotal(String snapshot) {
        try {
            return json.readTree(snapshot).path("aiEnabledTotal").asInt();
        } catch (java.io.IOException invalid) {
            throw new IllegalStateException("invalid_review_snapshot", invalid);
        }
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (java.io.IOException invalid) {
            throw new IllegalStateException("invalid_review_snapshot", invalid);
        }
    }

    private MoodReviewStatsVO decodeStats(String value) {
        try {
            return json.readValue(value, MoodReviewStatsVO.class);
        } catch (java.io.IOException invalid) {
            throw new IllegalStateException("invalid_review_snapshot", invalid);
        }
    }

    private static ResponseStatusException http(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
