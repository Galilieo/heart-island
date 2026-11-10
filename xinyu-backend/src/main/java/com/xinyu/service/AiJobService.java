package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xinyu.dto.MoodAiResponse;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.AiReply;
import com.xinyu.entity.MoodRecord;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.mapper.AiReplyMapper;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.vo.AiJobVO;
import com.xinyu.vo.MoodRecordVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 管理持久任务；本阶段保存路径仅做数据库操作，不调用模型。 */
@Service
public class AiJobService {
    private static final Logger logger = LoggerFactory.getLogger(AiJobService.class);

    /** 任务类型，来源：ai_job.task_type 列注释；当前 worker 只处理日记回应。 */
    private static final String TASK_TYPE_MOOD_REPLY = "MOOD_REPLY";

    /** 任务状态，来源：ai_job.status 列注释。 */
    private static final String STATUS_QUEUED = "queued";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCEEDED = "succeeded";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_SUPERSEDED = "superseded";

    /** 结果类型，来源：ai_job.result_kind 列注释；SUPPORT 不写 ai_reply 日志。 */
    private static final String RESULT_KIND_MODEL = "MODEL";

    /** 模板版本，须与 Python contracts.TEMPLATE_VERSION 保持一致。 */
    private static final String TEMPLATE_VERSION = "mood-reply-v1";

    /** 持久排队容量，来源：1a 功能设计第 8 节（全局 queued 100、单用户 queued/running 3）。 */
    private static final int MAX_QUEUED_JOBS = 100;
    private static final int MAX_ACTIVE_JOBS_PER_USER = 3;

    /** 错误分类，来源：Python 响应契约与 1a 失败分类；数据库只存分类，不存上游原文。 */
    private static final String ERROR_INVALID_MODEL_RESPONSE = "invalid_model_response";
    private static final String ERROR_CAPACITY_EXCEEDED = "capacity_exceeded";
    private static final String ERROR_INTERNAL = "internal_error";

    /** 无任务时的来源状态：历史回复 legacy、待生成 unavailable、已关闭 disabled。 */
    private static final String STATE_LEGACY = "legacy";
    private static final String STATE_UNAVAILABLE = "unavailable";
    private static final String STATE_DISABLED = "disabled";

    /** mood_record.status=1 表示有效，软删除写 0。 */
    private static final int MOOD_RECORD_ACTIVE_STATUS = 1;

    /** 与 Python str.strip 口径一致：U+0085 NEXT LINE 在 Java 中不算空白，需单独排除。 */
    private static final int NEXT_LINE = 0x85;

    /** 幂等返回与重试 CAS 可以直接接受的任务状态。 */
    private static final Set<String> ACTIVE_OR_SUCCEEDED =
            Set.of(STATUS_QUEUED, STATUS_RUNNING, STATUS_SUCCEEDED);

    private static final Set<String> ERROR_CODES = Set.of(
            "invalid_request", "unauthorized", "provider_timeout", "provider_error",
            ERROR_INVALID_MODEL_RESPONSE, "overloaded", "execution_interrupted",
            ERROR_CAPACITY_EXCEEDED, ERROR_INTERNAL);

    private final AiJobMapper jobs;
    private final MoodRecordMapper moods;
    private final AiReplyMapper replies;
    private final AiJobAdmission admission;
    private final boolean workerEnabled;

    public AiJobService(AiJobMapper jobs, MoodRecordMapper moods, AiReplyMapper replies,
                        AiJobAdmission admission, @Value("${AI_WORKER_ENABLED:false}") boolean workerEnabled) {
        this.jobs = jobs;
        this.moods = moods;
        this.replies = replies;
        this.admission = admission;
        this.workerEnabled = workerEnabled;
    }

    /**
     * 原子领取一条待办；没有领取成功时由下一次扫描继续，不在这里反复抢占。
     *
     * @return 本次领取并加锁的任务；无待办或 worker 未启用时返回 null
     */
    public AiJob claimNext() {
        if (!workerEnabled) return null;
        return admission.transaction(() -> {
            AiJob candidate = jobs.selectNextQueued();
            if (candidate == null) return null;
            String requestId = UUID.randomUUID().toString();
            if (jobs.claim(candidate.getId(), candidate.getAttemptCount(), requestId) != 1) return null;
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
        if (!isCurrentAttempt(job, attempt, requestId)) return null;
        MoodRecord source = moods.selectById(job.getTargetId());
        if (validSource(source, job)) return source;
        finish(job, STATUS_SUPERSEDED, null, null);
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
        // 加锁前先读一次任务，快速拒绝已过期的尝试，避免无谓锁定来源与任务。
        AiJob observed = jobs.selectById(jobId);
        if (!isCurrentAttempt(observed, attempt, requestId)) return false;
        return admission.transaction(() -> {
            // 锁顺序固定为来源行→任务行；拿锁后重新核验尝试身份和目标，防止等待期间被其他执行取代。
            MoodRecord source = moods.selectOwnedForUpdate(observed.getTargetId(), observed.getUserId());
            AiJob current = jobs.selectForUpdate(jobId);
            if (!isCurrentAttempt(current, attempt, requestId) || !sameTarget(observed, current)) return false;
            if (!validSource(source, current)) {
                finish(current, STATUS_SUPERSEDED, null, null);
                return false;
            }
            String reply;
            try {
                if (result == null) throw new IllegalArgumentException(ERROR_INVALID_MODEL_RESPONSE);
                reply = result.validatedReply(jobId, attempt, requestId, current.getTargetVersion());
            } catch (IllegalArgumentException invalid) {
                finish(current, STATUS_FAILED, ERROR_INVALID_MODEL_RESPONSE, null);
                return false;
            }
            // 先做任务终态 CAS 再写回复：终态是本次提交的闸门，条件不满足时不写来源。
            if (!finish(current, STATUS_SUCCEEDED, null, result)) return false;
            if (moods.writeReplyAtVersion(current.getTargetId(), current.getUserId(), current.getTargetVersion(), reply) != 1) {
                throw new IllegalStateException("回应提交条件已变化");
            }
            if (RESULT_KIND_MODEL.equals(result.resultKind())) {
                AiReply log = new AiReply();
                log.setUserId(current.getUserId());
                log.setTargetType(AiReplyLogService.TARGET_TYPE_MOOD_RECORD);
                log.setTargetId(current.getTargetId());
                log.setReplyContent(reply);
                log.setModelName(result.modelName());
                log.setStatus(1);
                // 不复制日记全文或提示词；SUPPORT 不伪造成模型调用成功记录。
                if (replies.insert(log) != 1) throw new IllegalStateException("回应日志保存未成功");
            }
            // 终态、来源回复与日志同事务提交，任一步失败都整体回滚，不留下“已完成但没有回复”的任务。
            return true;
        });
    }

    /**
     * 只结束仍属于本次尝试的 running，不据失败猜测供应商是否执行。
     *
     * @return 条件更新命中时返回 true；尝试已过期时返回 false
     */
    public boolean fail(long jobId, int attempt, String requestId, String errorCode) {
        AiJob job = jobs.selectById(jobId);
        if (!isCurrentAttempt(job, attempt, requestId)) return false;
        String safeCode = ERROR_CODES.contains(errorCode == null ? "" : errorCode) ? errorCode : ERROR_INTERNAL;
        return finish(job, STATUS_FAILED, safeCode, null);
    }

    /**
     * 失败重试沿用任务；准入锁覆盖容量检查到事务提交，但不反向锁定来源。
     *
     * @return 任务当前视图；已处于 queued/running/succeeded 时按幂等返回
     * @throws ResponseStatusException 任务不可重试、容量已满或状态已变化
     */
    public AiJobVO retry(long userId, long jobId) {
        return admission.save(() -> {
            AiJob job = ownedJob(userId, jobId);
            MoodRecord source = ownedCurrentSource(job);
            if (ACTIVE_OR_SUCCEEDED.contains(job.getStatus())) {
                return view(job, source);
            }
            if (!STATUS_FAILED.equals(job.getStatus())) {
                throw http(HttpStatus.CONFLICT, "当前任务不可重试");
            }

            int cooldownSeconds = AiJobRetryPolicy.cooldownSeconds(job.getAttemptCount());
            requireCooldownElapsed(job, cooldownSeconds);
            if (capacityFull(userId)) {
                throw http(HttpStatus.SERVICE_UNAVAILABLE, "回应任务较多，请稍后重试");
            }

            int changed = jobs.requeueAfterCooldown(job, cooldownSeconds);
            // CAS 失败后只回查一次当前行：用来区分“已进入 queued/running/succeeded 的幂等返回”
            // 与真实状态冲突，不循环重置已经完成了另一轮的失败任务。
            AiJob current = jobs.selectForUpdate(jobId);
            if (retryConflict(job, current, changed)) {
                throw http(HttpStatus.CONFLICT, "任务状态已变化，请刷新");
            }
            return view(current, source);
        });
    }

    /**
     * 使用同一数据库快照读取任务与来源，先检查归属、删除及版本再返回回复。
     *
     * @throws ResponseStatusException 任务不存在、不属于当前用户或来源已失效
     */
    public AiJobVO getOwned(long userId, long jobId) {
        return admission.transaction(() -> {
            AiJob job = ownedJob(userId, jobId);
            return view(job, ownedCurrentSource(job));
        });
    }

    /** 仅供唯一启用 worker 且旧进程已停止后的启动流程调用；此阶段不自动注册调度。 */
    public int recoverInterrupted() {
        return workerEnabled ? jobs.failInterrupted() : 0;
    }

    /** 调用方先按用户过滤来源，并在同一读取事务内组合列表或详情。 */
    public MoodRecordVO toMoodView(MoodRecord source) {
        if (source == null || !Integer.valueOf(MOOD_RECORD_ACTIVE_STATUS).equals(source.getStatus())) {
            throw http(HttpStatus.NOT_FOUND, "心情记录不存在");
        }
        AiJob job = jobs.selectOne(new QueryWrapper<AiJob>()
                .eq("task_type", TASK_TYPE_MOOD_REPLY)
                .eq("target_id", source.getId())
                .eq("target_version", source.getContentVersion())
                .eq("user_id", source.getUserId()));
        String state = replyState(source, job);
        String reply = replyVisible(state) ? source.getAiReply() : null;
        return new MoodRecordVO(source.getId(), source.getUserId(), source.getMoodType(), source.getContent(), reply,
                source.getStatus(), source.getCreateTime(), source.getUpdateTime(), source.getContentVersion(),
                source.getAiReplyEnabled(), state, job == null ? null : job.getId(),
                job != null && STATUS_SUCCEEDED.equals(state) ? job.getResultKind() : null);
    }

    private AiJob ownedJob(long userId, long jobId) {
        AiJob job = jobs.selectById(jobId);
        if (job == null) throw http(HttpStatus.NOT_FOUND, "回应任务不存在");
        if (!Objects.equals(job.getUserId(), userId)) throw http(HttpStatus.FORBIDDEN, "无权访问该任务");
        if (!TASK_TYPE_MOOD_REPLY.equals(job.getTaskType())) throw http(HttpStatus.NOT_FOUND, "回应任务不存在");
        return job;
    }

    private MoodRecord ownedCurrentSource(AiJob job) {
        MoodRecord source = moods.selectById(job.getTargetId());
        if (source == null || !Integer.valueOf(MOOD_RECORD_ACTIVE_STATUS).equals(source.getStatus())
                || !Objects.equals(source.getUserId(), job.getUserId())) {
            throw http(HttpStatus.NOT_FOUND, "心情记录不存在");
        }
        if (!validSource(source, job)) throw http(HttpStatus.CONFLICT, "回应任务已失效，请刷新记录");
        return source;
    }

    private static boolean validSource(MoodRecord source, AiJob job) {
        return source != null && Integer.valueOf(MOOD_RECORD_ACTIVE_STATUS).equals(source.getStatus())
                && Objects.equals(source.getId(), job.getTargetId())
                && Objects.equals(source.getUserId(), job.getUserId())
                && Objects.equals(source.getContentVersion(), job.getTargetVersion())
                && Boolean.TRUE.equals(source.getAiReplyEnabled());
    }

    private static boolean sameTarget(AiJob a, AiJob b) {
        return Objects.equals(a.getUserId(), b.getUserId())
                && Objects.equals(a.getTaskType(), b.getTaskType())
                && Objects.equals(a.getTargetId(), b.getTargetId())
                && Objects.equals(a.getTargetVersion(), b.getTargetVersion());
    }

    /** 尝试身份由任务状态、尝试次数与每次领取的 requestId 共同确定。 */
    private static boolean isCurrentAttempt(AiJob job, int attempt, String requestId) {
        return job != null
                && TASK_TYPE_MOOD_REPLY.equals(job.getTaskType())
                && STATUS_RUNNING.equals(job.getStatus())
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

    private boolean finish(AiJob job, String status, String errorCode, MoodAiResponse result) {
        return jobs.finishAttempt(job, status, errorCode, result == null ? null : result.resultKind(),
                result == null ? null : result.modelCalled(), result == null ? null : result.modelName()) == 1;
    }

    private boolean capacityFull(long userId) {
        return jobs.selectCount(new QueryWrapper<AiJob>().eq("status", STATUS_QUEUED)) >= MAX_QUEUED_JOBS
                || jobs.selectCount(new QueryWrapper<AiJob>()
                        .eq("user_id", userId)
                        .in("status", STATUS_QUEUED, STATUS_RUNNING)) >= MAX_ACTIVE_JOBS_PER_USER;
    }

    /** 无任务的来源按自身状态表达：留存旧回复为 legacy，待生成为 unavailable，关闭为 disabled。 */
    private static String replyState(MoodRecord source, AiJob job) {
        if (job != null) {
            return job.getStatus();
        }
        if (hasVisibleText(source.getAiReply())) {
            return STATE_LEGACY;
        }
        return Boolean.TRUE.equals(source.getAiReplyEnabled()) ? STATE_UNAVAILABLE : STATE_DISABLED;
    }

    /** 只有成功或历史留存的回复可以返回正文，其他状态不暴露旧回复。 */
    private static boolean replyVisible(String state) {
        return STATUS_SUCCEEDED.equals(state) || STATE_LEGACY.equals(state);
    }

    /** 是否含非空白 Unicode 字符；与 Python str.strip 的口径一致，不用 String.trim 判断。 */
    private static boolean hasVisibleText(String text) {
        return text != null && text.codePoints()
                .anyMatch(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c) && c != NEXT_LINE);
    }

    private AiJobVO view(AiJob job, MoodRecord source) {
        boolean succeeded = STATUS_SUCCEEDED.equals(job.getStatus());
        boolean failed = STATUS_FAILED.equals(job.getStatus());
        String resultKind = succeeded ? job.getResultKind() : null;
        String replyContent = succeeded ? source.getAiReply() : null;
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
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.RETRY_AFTER, Integer.toString(remainingSeconds));
            // ResponseStatusException默认返回只读空头；覆盖取头方法，沿用既有异常响应结构。
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "回应任务冷却中，请稍后重试") {
                @Override
                public HttpHeaders getHeaders() {
                    return headers;
                }
            };
        }
    }

    private static ResponseStatusException http(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    /**
     * 为来源当前版本创建任务；来源未开启 AI 回应时返回 null。
     * 调用方必须持有单实例准入锁，并处于来源保存的同一事务中。
     *
     * @param source 已保存且版本为当前值的来源
     * @return 新建任务；来源未开启 AI 回应时返回 null
     */
    public AiJob createForVersion(MoodRecord source) {
        if (!Boolean.TRUE.equals(source.getAiReplyEnabled())) return null;
        boolean full = capacityFull(source.getUserId());
        AiJob job = new AiJob();
        job.setUserId(source.getUserId());
        job.setTargetId(source.getId());
        job.setTaskType(TASK_TYPE_MOOD_REPLY);
        job.setTargetVersion(source.getContentVersion());
        job.setAttemptCount(0);
        job.setStatus(full ? STATUS_FAILED : STATUS_QUEUED);
        job.setTemplateVersion(TEMPLATE_VERSION);
        if (full) {
            job.setLastErrorCode(ERROR_CAPACITY_EXCEEDED);
            job.setFinishedAt(LocalDateTime.now());
        }
        if (jobs.insert(job) != 1) throw new IllegalStateException("任务保存未成功");
        return job;
    }

    /** 失效来源的未完成任务；调用方负责事务边界。 */
    public void supersedeUnfinished(Long targetId, Long userId) {
        jobs.supersedeUnfinished(targetId, userId);
    }
}
