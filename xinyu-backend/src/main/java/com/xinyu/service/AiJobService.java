package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xinyu.dto.MoodAiResponse;
import com.xinyu.dto.ReviewAiResponse;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.AiReply;
import com.xinyu.entity.MoodRecord;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.mapper.AiReplyMapper;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.ratelimit.RateLimiter;
import com.xinyu.ratelimit.RateLimitType;
import com.xinyu.vo.AiJobVO;
import com.xinyu.vo.MoodRecordVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static com.xinyu.common.AiJobValues.COMMUNITY_REPLY;
import static com.xinyu.common.AiJobValues.DISABLED;
import static com.xinyu.common.AiJobValues.FAILED;
import static com.xinyu.common.AiJobValues.LEGACY;
import static com.xinyu.common.AiJobValues.MODEL;
import static com.xinyu.common.AiJobValues.MOOD_REPLY;
import static com.xinyu.common.AiJobValues.MOOD_REVIEW;
import static com.xinyu.common.AiJobValues.QUEUED;
import static com.xinyu.common.AiJobValues.RUNNING;
import static com.xinyu.common.AiJobValues.SUCCEEDED;
import static com.xinyu.common.AiJobValues.SUPERSEDED;
import static com.xinyu.common.AiJobValues.UNAVAILABLE;

/** 管理回复任务的准入、查询、重排与日记执行结果；具体生成请求由 Worker 的策略发出。 */
@Service
public class AiJobService {
    private static final Logger logger = LoggerFactory.getLogger(AiJobService.class);

    public static final String TASK_TYPE_COMMUNITY_REPLY = COMMUNITY_REPLY;

    private static final Set<String> MOOD_TASK_TYPES = Set.of(MOOD_REPLY, MOOD_REVIEW);
    private static final Set<String> COMMUNITY_TASK_TYPES = Set.of(COMMUNITY_REPLY);

    /** 模板版本，须与 Python contracts.TEMPLATE_VERSION 保持一致。 */
    private static final String TEMPLATE_VERSION = "mood-reply-v2";

    /** 持久排队容量，来源：1a 功能设计第 8 节（全局 queued 100、单用户 queued/running 3）。 */
    private static final int MAX_QUEUED_JOBS = 100;
    private static final int MAX_ACTIVE_JOBS_PER_USER = 3;

    /** 错误分类，来源：Python 响应契约与 1a 失败分类；数据库只存分类，不存上游原文。 */
    private static final String ERROR_INVALID_MODEL_RESPONSE = "invalid_model_response";
    private static final String ERROR_CAPACITY_EXCEEDED = "capacity_exceeded";
    private static final String ERROR_INTERNAL = "internal_error";

    /** 限流拒绝的错误码集合，取值见 RateLimitScope.errorCode；任务 attempt=0，不在冷却中。 */
    private static final String ERROR_RATE_LIMITED_HOUR = "rate_limited_hour";
    private static final String ERROR_RATE_LIMITED_DAY = "rate_limited_day";
    private static final String ERROR_RATE_LIMITED_GLOBAL = "rate_limited_global";

    /** mood_record.status=1 表示有效，软删除写 0。 */
    private static final int MOOD_RECORD_ACTIVE_STATUS = 1;

    /** 与 Python str.strip 口径一致：U+0085 NEXT LINE 在 Java 中不算空白，需单独排除。 */
    private static final int NEXT_LINE = 0x85;

    /** 幂等返回与重试 CAS 可以直接接受的任务状态。 */
    private static final Set<String> ACTIVE_OR_SUCCEEDED =
            Set.of(QUEUED, RUNNING, SUCCEEDED);

    private static final Set<String> ERROR_CODES = Set.of(
            "invalid_request", "unauthorized", "provider_timeout", "provider_error",
            ERROR_INVALID_MODEL_RESPONSE, "overloaded", "execution_interrupted",
            ERROR_CAPACITY_EXCEEDED, ERROR_INTERNAL,
            ERROR_RATE_LIMITED_HOUR, ERROR_RATE_LIMITED_DAY, ERROR_RATE_LIMITED_GLOBAL);

    private final AiJobMapper jobs;
    private final MoodRecordMapper moods;
    private final AiReplyMapper replies;
    private final AiJobAdmission admission;
    private final RateLimiter rateLimiter;
    private final boolean workerEnabled;

    public AiJobService(AiJobMapper jobs, MoodRecordMapper moods, AiReplyMapper replies,
                        AiJobAdmission admission, RateLimiter rateLimiter,
                        @Value("${AI_WORKER_ENABLED:false}") boolean workerEnabled) {
        this.jobs = jobs;
        this.moods = moods;
        this.replies = replies;
        this.admission = admission;
        this.rateLimiter = rateLimiter;
        this.workerEnabled = workerEnabled;
    }

    private MoodReviewService reviews;

    @Autowired
    public void setReviews(MoodReviewService reviews) {
        this.reviews = reviews;
    }

    public MoodReviewService.Prepared prepareReview(long id, int attempt, String requestId) {
        return reviews.prepare(id, attempt, requestId);
    }

    public boolean completeReview(long id, int attempt, String requestId, ReviewAiResponse response) {
        return reviews.complete(id, attempt, requestId, response);
    }

    /**
     * 原子领取一条待办；没有领取成功时由下一次扫描继续，不在这里反复抢占。
     *
     * @return 本次领取并加锁的任务；无待办或 worker 未启用时返回 null
     */
    public AiJob claimNext() {
        if (!workerEnabled) {
            return null;
        }
        return admission.transaction(() -> {
            AiJob candidate = jobs.selectNextQueued(MOOD_REPLY);
            if (candidate == null) {
                return reviews == null ? null : reviews.claimNext();
            }
            String requestId = UUID.randomUUID().toString();
            if (jobs.claimByType(MOOD_REPLY, candidate.getId(), candidate.getAttemptCount(), requestId) != 1) {
                return null;
            }
            // 同一事务内重新读取，返回 CAS 之后的真实行并锁到提交。
            return jobs.selectForUpdate(candidate.getId());
        });
    }

    /**
     * 外部调用前检查来源；此处不持锁等待网络，提交时仍须再次核验。
     *
     * @return 来源仍属于本次尝试时的日记行；尝试过期返回 null，来源失效则记录 superseded 后返回 null
     */
    public MoodRecord sourceForAttempt(long jobId, int attempt, String requestId) {
        AiJob job = jobs.selectById(jobId);
        if (!isCurrentAttempt(job, attempt, requestId)) {
            return null;
        }
        MoodRecord source = moods.selectById(job.getTargetId());
        if (validSource(source, job)) {
            return source;
        }
        finishWithoutReply(job, SUPERSEDED, null);
        return null;
    }

    /**
     * 提交一次成功结果：更新任务终态、写入来源回复并补兼容日志。
     * 来源行先锁、任务行后锁；回复、日志与终态在同一短事务提交。
     *
     * @return 终态已提交时返回 true；尝试过期、来源失效或结果非法时返回 false
     * @throws IllegalStateException 条件更新意外失败，事务整体回滚
     */
    public boolean complete(long jobId, int attempt, String requestId, MoodAiResponse result) {
        AiJob observed = jobs.selectById(jobId);
        if (!isCurrentAttempt(observed, attempt, requestId)) {
            return false;
        }

        return admission.transaction(() -> completeMoodReplyUnderLock(jobId, observed, attempt, requestId, result));
    }

    private boolean completeMoodReplyUnderLock(
            long jobId, AiJob observed, int attempt, String requestId, MoodAiResponse result) {
        // 与日记编辑、删除使用同一锁顺序；等待行锁后重新核验任务身份。
        MoodRecord source = moods.selectOwnedForUpdate(observed.getTargetId(), observed.getUserId());
        AiJob current = jobs.selectForUpdate(jobId);
        if (!isCurrentAttempt(current, attempt, requestId) || !sameTarget(observed, current)) {
            return false;
        }
        if (!validSource(source, current)) {
            finishWithoutReply(current, SUPERSEDED, null);
            return false;
        }

        String reply;
        try {
            if (result == null) {
                throw new IllegalArgumentException(ERROR_INVALID_MODEL_RESPONSE);
            }
            reply = result.validatedReply(jobId, attempt, requestId, current.getTargetVersion());
        } catch (IllegalArgumentException invalid) {
            finishWithoutReply(current, FAILED, ERROR_INVALID_MODEL_RESPONSE);
            return false;
        }

        if (!finishWithReply(current, result)) {
            return false;
        }
        if (moods.writeReplyAtVersion(current.getTargetId(), current.getUserId(), current.getTargetVersion(), reply) != 1) {
            throw new IllegalStateException("回应提交条件已变化");
        }
        if (MODEL.equals(result.resultKind())) {
            writeMoodReplyLog(current, reply, result.modelName());
        }
        return true;
    }

    private void writeMoodReplyLog(AiJob job, String reply, String modelName) {
        AiReply log = new AiReply();
        log.setUserId(job.getUserId());
        log.setTargetType(AiReplyLogService.TARGET_TYPE_MOOD_RECORD);
        log.setTargetId(job.getTargetId());
        log.setReplyContent(reply);
        log.setModelName(modelName);
        log.setStatus(1);
        if (replies.insert(log) != 1) {
            throw new IllegalStateException("回应日志保存未成功");
        }
    }

    /**
     * 只结束仍属于本次尝试的 running，不据失败猜测供应商是否执行。
     *
     * @return 条件更新命中时返回 true；尝试已过期时返回 false
     */
    public boolean fail(long jobId, int attempt, String requestId, String errorCode) {
        AiJob job = jobs.selectById(jobId);
        if (job != null && MOOD_REVIEW.equals(job.getTaskType())) {
            return reviews.fail(jobId, attempt, requestId, errorCode);
        }
        if (!isCurrentAttempt(job, attempt, requestId)) {
            return false;
        }
        String safeCode = safeErrorCode(errorCode);
        return finishWithoutReply(job, FAILED, safeCode);
    }

    /**
     * 失败重试沿用任务；准入锁覆盖容量检查到事务提交，但不反向锁定来源。
     *
     * @return 任务当前视图；已处于 queued/running/succeeded 时按幂等返回
     * @throws ResponseStatusException 任务不可重试、额度不足（429）、容量已满或状态已变化
     */
    public AiJobVO retry(long userId, long jobId) {
        AiJob observed = jobs.selectById(jobId);
        if (observed != null && MOOD_REVIEW.equals(observed.getTaskType())) {
            return reviews.retry(userId, jobId);
        }
        return retryReply(userId, jobId, observed, MOOD_REPLY,
                job -> ownedCurrentSource(job).getAiReply(),
                job -> {
                    MoodRecord source = moods.selectById(job.getTargetId());
                    return source == null ? null : source.getContent();
                });
    }

    /** 社区任务归属、版本和可见性确认后，复用回复任务的额度、冷却与重排。 */
    public AiJobVO retryCommunity(long userId, long jobId, CommunityReplySource source) {
        return retryReply(userId, jobId, jobs.selectById(jobId), COMMUNITY_REPLY,
                source::currentReply, source::retryContent);
    }

    private AiJobVO retryReply(long userId, long jobId, AiJob observed, String taskType,
                               Function<AiJob, String> currentReply, Function<AiJob, String> retryContent) {
        boolean community = COMMUNITY_REPLY.equals(taskType);
        requireRetryQuota(userId, observed, taskType, retryContent);
        return admission.save(() -> {
            AiJob job = community ? ownedCommunityJob(userId, jobId) : ownedJob(userId, jobId);
            String reply = currentReply.apply(job);
            if (ACTIVE_OR_SUCCEEDED.contains(job.getStatus())) {
                return view(job, reply);
            }
            if (!FAILED.equals(job.getStatus())) {
                throw http(HttpStatus.CONFLICT, "当前任务不可重试");
            }

            int cooldownSeconds = AiJobRetryPolicy.cooldownSeconds(job.getAttemptCount());
            requireCooldownElapsed(job, cooldownSeconds);
            if (capacityFull(userId, taskType)) {
                throw http(HttpStatus.SERVICE_UNAVAILABLE, "回应任务较多，请稍后重试");
            }

            int changed = community
                    ? jobs.requeueAfterCooldownByType(taskType, job, cooldownSeconds)
                    : jobs.requeueAfterCooldown(job, cooldownSeconds);
            // CAS 失败后只回查一次当前行：用来区分“已进入 queued/running/succeeded 的幂等返回”
            // 与真实状态冲突，不循环重置已经完成了另一轮的失败任务。
            AiJob current = jobs.selectForUpdate(jobId);
            if (retryConflict(job, current, changed)) {
                throw http(HttpStatus.CONFLICT, "任务状态已变化，请刷新");
            }
            return view(current, reply);
        });
    }

    /**
     * 使用同一数据库快照读取任务与来源，先检查归属、删除及版本再返回回复。
     *
     * @throws ResponseStatusException 任务不存在、不属于当前用户或来源已失效
     */
    public AiJobVO getOwned(long userId, long jobId) {
        return admission.transaction(() -> {
            AiJob observed = jobs.selectById(jobId);
            if (observed != null && MOOD_REVIEW.equals(observed.getTaskType())) {
                return reviews.getOwned(userId, jobId);
            }
            AiJob job = ownedJob(userId, jobId);
            return view(job, ownedCurrentSource(job).getAiReply());
        });
    }

    /** 在同一读事务内验证任务归属和帖子版本，只返回任务公开视图。 */
    public AiJobVO getOwnedCommunity(long userId, long jobId, CommunityReplySource source) {
        return admission.transaction(() -> {
            AiJob job = ownedCommunityJob(userId, jobId);
            return view(job, source.currentReply(job));
        });
    }

    private AiJob ownedCommunityJob(long userId, long jobId) {
        AiJob job = jobs.selectById(jobId);
        if (job == null || !COMMUNITY_REPLY.equals(job.getTaskType())
                || !Objects.equals(job.getUserId(), userId)) {
            throw http(HttpStatus.NOT_FOUND, "回应任务不存在");
        }
        return job;
    }

    /** 仅供唯一启用 worker 且旧进程已停止后的启动流程调用；此阶段不自动注册调度。 */
    public int recoverInterrupted() {
        return workerEnabled ? jobs.failInterruptedByType(MOOD_REPLY) + (reviews == null ? 0 : reviews.recoverInterrupted()) : 0;
    }

    /** 调用方先按用户过滤来源，并在同一读取事务内组合列表或详情。 */
    public MoodRecordVO toMoodView(MoodRecord source) {
        if (source == null || !Integer.valueOf(MOOD_RECORD_ACTIVE_STATUS).equals(source.getStatus())) {
            throw http(HttpStatus.NOT_FOUND, "心情记录不存在");
        }
        AiJob job = jobs.selectOne(new QueryWrapper<AiJob>()
                .eq("task_type", MOOD_REPLY)
                .eq("target_id", source.getId())
                .eq("target_version", source.getContentVersion())
                .eq("user_id", source.getUserId()));
        String state = replyState(source, job);
        String reply = replyVisible(state) ? source.getAiReply() : null;
        return new MoodRecordVO(source.getId(), source.getUserId(), source.getMoodType(), source.getContent(), reply,
                source.getStatus(), source.getCreateTime(), source.getUpdateTime(), source.getContentVersion(),
                source.getAiReplyEnabled(), state, job == null ? null : job.getId(),
                job != null && SUCCEEDED.equals(state) ? job.getResultKind() : null);
    }

    private AiJob ownedJob(long userId, long jobId) {
        AiJob job = jobs.selectById(jobId);
        if (job == null || COMMUNITY_REPLY.equals(job.getTaskType())) {
            throw http(HttpStatus.NOT_FOUND, "回应任务不存在");
        }
        if (!Objects.equals(job.getUserId(), userId)) {
            throw http(HttpStatus.FORBIDDEN, "无权访问该任务");
        }
        if (!MOOD_REPLY.equals(job.getTaskType())) {
            throw http(HttpStatus.NOT_FOUND, "回应任务不存在");
        }
        return job;
    }

    private MoodRecord ownedCurrentSource(AiJob job) {
        MoodRecord source = moods.selectById(job.getTargetId());
        if (source == null || !Integer.valueOf(MOOD_RECORD_ACTIVE_STATUS).equals(source.getStatus())
                || !Objects.equals(source.getUserId(), job.getUserId())) {
            throw http(HttpStatus.NOT_FOUND, "心情记录不存在");
        }
        if (!validSource(source, job)) {
            throw http(HttpStatus.CONFLICT, "回应任务已失效，请刷新记录");
        }
        return source;
    }

    private static boolean validSource(MoodRecord source, AiJob job) {
        return source != null && Integer.valueOf(MOOD_RECORD_ACTIVE_STATUS).equals(source.getStatus())
                && Objects.equals(source.getId(), job.getTargetId())
                && Objects.equals(source.getUserId(), job.getUserId())
                && Objects.equals(source.getContentVersion(), job.getTargetVersion())
                && Boolean.TRUE.equals(source.getAiReplyEnabled());
    }

    static boolean sameTarget(AiJob a, AiJob b) {
        return Objects.equals(a.getUserId(), b.getUserId())
                && Objects.equals(a.getTaskType(), b.getTaskType())
                && Objects.equals(a.getTargetId(), b.getTargetId())
                && Objects.equals(a.getTargetVersion(), b.getTargetVersion());
    }

    /** 尝试身份由任务状态、尝试次数与每次领取的 requestId 共同确定。 */
    private static boolean isCurrentAttempt(AiJob job, int attempt, String requestId) {
        return isCurrentAttempt(job, MOOD_REPLY, attempt, requestId);
    }

    static boolean isCurrentAttempt(AiJob job, String taskType, int attempt, String requestId) {
        return job != null
                && taskType.equals(job.getTaskType())
                && RUNNING.equals(job.getStatus())
                && attempt >= 1
                && Integer.valueOf(attempt).equals(job.getAttemptCount())
                && requestId != null && !requestId.isBlank() && requestId.equals(job.getRequestId());
    }

    /** 重排后关联或状态不符即视为冲突；CAS 未命中但已进入可接受状态时按幂等返回。 */
    private static boolean retryConflict(AiJob job, AiJob current, int changed) {
        return current == null
                || !sameTarget(job, current)
                || (changed == 0 && !ACTIVE_OR_SUCCEEDED.contains(current.getStatus()));
    }

    static String safeErrorCode(String errorCode) {
        return errorCode != null && ERROR_CODES.contains(errorCode) ? errorCode : ERROR_INTERNAL;
    }

    private boolean finishWithoutReply(AiJob job, String status, String errorCode) {
        return jobs.finishAttemptByType(MOOD_REPLY, job, status, errorCode, null, null, null) == 1;
    }

    private boolean finishWithReply(AiJob job, MoodAiResponse reply) {
        return jobs.finishAttemptByType(MOOD_REPLY, job, SUCCEEDED, null,
                reply.resultKind(), reply.modelCalled(), reply.modelName()) == 1;
    }

    private boolean capacityFull(long userId, String taskType) {
        Set<String> types = COMMUNITY_REPLY.equals(taskType) ? COMMUNITY_TASK_TYPES : MOOD_TASK_TYPES;
        return jobs.selectCount(new QueryWrapper<AiJob>().eq("status", QUEUED)
                        .in("task_type", types)) >= MAX_QUEUED_JOBS
                || jobs.selectCount(new QueryWrapper<AiJob>()
                        .eq("user_id", userId)
                        .in("task_type", types)
                        .in("status", QUEUED, RUNNING)) >= MAX_ACTIVE_JOBS_PER_USER;
    }

    /** 无任务的来源按自身状态表达：留存旧回复为 legacy，待生成为 unavailable，关闭为 disabled。 */
    private static String replyState(MoodRecord source, AiJob job) {
        if (job != null) {
            return job.getStatus();
        }
        if (hasVisibleText(source.getAiReply())) {
            return LEGACY;
        }
        return Boolean.TRUE.equals(source.getAiReplyEnabled()) ? UNAVAILABLE : DISABLED;
    }

    /** 只有成功或历史留存的回复可以返回正文，其他状态不暴露旧回复。 */
    private static boolean replyVisible(String state) {
        return SUCCEEDED.equals(state) || LEGACY.equals(state);
    }

    /** 是否含非空白 Unicode 字符；与 Python str.strip 的口径一致，不用 String.trim 判断。 */
    private static boolean hasVisibleText(String text) {
        return text != null && text.codePoints()
                .anyMatch(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c) && c != NEXT_LINE);
    }

    private AiJobVO view(AiJob job, String sourceReply) {
        boolean succeeded = SUCCEEDED.equals(job.getStatus());
        boolean failed = FAILED.equals(job.getStatus());
        String resultKind = succeeded ? job.getResultKind() : null;
        String replyContent = succeeded ? sourceReply : null;
        String errorCode = failed ? job.getLastErrorCode() : null;
        int retryAfterSeconds = failed
                ? retryAfterSeconds(job, AiJobRetryPolicy.cooldownSeconds(job.getAttemptCount()))
                : 0;
        boolean retryable = failed && retryAfterSeconds == 0;
        return new AiJobVO(job.getId(), job.getTargetId(), job.getTargetVersion(), job.getStatus(),
                job.getAttemptCount(), resultKind, replyContent, errorCode, retryable, retryAfterSeconds);
    }

    /** GET与retry预检共用数据库时间；真正放行仍由UPDATE中的完整CAS决定。 */
    private int retryAfterSeconds(AiJob job, int cooldownSeconds) {
        if (cooldownSeconds == 0) {
            return 0;
        }
        if (job.getFinishedAt() == null) {
            logger.warn("冷却任务缺少结束时间 jobId={} attempt={} code=missing_finished_at",
                    job.getId(), job.getAttemptCount());
            return 0;
        }
        Integer remainingSeconds = jobs.selectRetryAfterSeconds(job, cooldownSeconds);
        if (remainingSeconds == null) {
            throw http(HttpStatus.CONFLICT, "任务状态已变化，请刷新");
        }
        return remainingSeconds;
    }

    private void requireCooldownElapsed(AiJob job, int cooldownSeconds) {
        int remainingSeconds = retryAfterSeconds(job, cooldownSeconds);
        if (remainingSeconds > 0) {
            throw tooManyRequests(remainingSeconds, "回应任务冷却中，请稍后重试");
        }
    }

    /** 冷却与限流共用的 429 返回：携带 Retry-After 头，沿用既有异常响应结构。 */
    private static ResponseStatusException tooManyRequests(int retryAfterSeconds, String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Integer.toString(retryAfterSeconds));
        // ResponseStatusException默认返回只读空头；覆盖取头方法，沿用既有异常响应结构。
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message) {
            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }

    private static ResponseStatusException http(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    /**
     * 为来源当前版本创建任务；来源未开启 AI 回应时返回 null。
     * 调用方必须持有单实例准入锁，并处于来源保存的同一事务中。
     * 限流决定由调用方在事务外先做出并传入：额度检查不进入数据库事务与准入锁（契约 2.5）。
     *
     * @param source 已保存且版本为当前值的来源
     * @param rateLimit 调用方在事务外完成的限流结果
     * @return 新建任务；来源未开启 AI 回应时返回 null
     */
    public AiJob createForVersion(MoodRecord source, RateLimiter.Decision rateLimit) {
        if (!Boolean.TRUE.equals(source.getAiReplyEnabled())) {
            return null;
        }
        return createForVersion(MOOD_REPLY, TEMPLATE_VERSION,
                source.getId(), source.getUserId(), source.getContentVersion(), rateLimit);
    }

    /** 由帖子准入事务调用，将新版本任务与正文一起提交。 */
    public AiJob createCommunityJob(long postId, long userId, long version, RateLimiter.Decision rateLimit) {
        return createForVersion(COMMUNITY_REPLY, CommunityReplyJobHandler.TEMPLATE_VERSION,
                postId, userId, version, rateLimit);
    }

    private AiJob createForVersion(String taskType, String templateVersion, long targetId,
                                   long userId, long targetVersion, RateLimiter.Decision rateLimit) {
        boolean rateLimited = rateLimit != null && !rateLimit.allowed();
        boolean full = !rateLimited && capacityFull(userId, taskType);
        AiJob job = new AiJob();
        job.setUserId(userId);
        job.setTargetId(targetId);
        job.setTaskType(taskType);
        job.setTargetVersion(targetVersion);
        job.setAttemptCount(0);
        job.setStatus(rateLimited || full ? FAILED : QUEUED);
        job.setTemplateVersion(templateVersion);
        if (rateLimited) {
            // 记录仍照常保存（契约 2.4）；任务 attempt=0 不在冷却中，retryable 为真。
            job.setLastErrorCode(rateLimit.blockedScope().errorCode());
            job.setFinishedAt(LocalDateTime.now());
        } else if (full) {
            job.setLastErrorCode(ERROR_CAPACITY_EXCEEDED);
            job.setFinishedAt(LocalDateTime.now());
        }
        if (jobs.insert(job) != 1) {
            throw new IllegalStateException("任务保存未成功");
        }
        return job;
    }

    /**
     * 重试的限流预检：在准入事务外完成（Redis 调用不进事务与锁，契约 2.5）。
     * 快照判断“确定会重新入队”才扣额度——幂等返回（queued/running/succeeded）、不可重试状态、
     * 冷却中都不重新入队，不扣；容量满留给事务内 503，按“已扣不退”契约处理。
     * 归属、类型与来源的正式校验仍在事务内；危机文本豁免——命中安全正则的来源由固定
     * SUPPORT 回应，不应被额度挡住（契约 2.1）。
     */
    private void requireRetryQuota(long userId, AiJob observed, String taskType,
                                   Function<AiJob, String> retryContent) {
        if (observed == null
                || !taskType.equals(observed.getTaskType())
                || !Objects.equals(observed.getUserId(), userId)) {
            return;
        }
        if (ACTIVE_OR_SUCCEEDED.contains(observed.getStatus())) {
            return;
        }
        if (!FAILED.equals(observed.getStatus())) {
            return;
        }
        int cooldownSeconds = AiJobRetryPolicy.cooldownSeconds(observed.getAttemptCount());
        if (cooldownSeconds > 0) {
            Integer remainingSeconds = jobs.selectRetryAfterSeconds(observed, cooldownSeconds);
            if (remainingSeconds == null || remainingSeconds > 0) {
                return;
            }
        }
        String content = retryContent.apply(observed);
        if (content == null || MoodReviewSafety.matches(content)) {
            return;
        }

        RateLimitType limit = COMMUNITY_REPLY.equals(taskType)
                ? RateLimitType.COMMUNITY_POST : RateLimitType.MOOD_REPLY;
        RateLimiter.Decision decision = rateLimiter.check(limit, userId);
        if (!decision.allowed()) {
            // 不改任务状态；沿用冷却 429 的返回方式，Retry-After 取限流组件给的再试秒数。
            throw tooManyRequests(decision.retryAfterSeconds(), "回应次数已用完，请稍后重试");
        }
    }

    /** 失效来源的未完成任务；调用方负责事务边界。 */
    public void supersedeUnfinished(Long targetId, Long userId) {
        jobs.supersedeUnfinished(targetId, userId);
    }

    /** 与帖子改版、删除或隐藏同事务执行。 */
    public void supersedeCommunityJobs(long postId, long userId) {
        jobs.supersedeUnfinishedByType(COMMUNITY_REPLY, postId, userId);
    }
}
