package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.MoodRecord;
import com.xinyu.entity.AiReply;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.xinyu.mapper.AiJobMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.mapper.AiReplyMapper;
import com.xinyu.dto.MoodAiResponse;
import com.xinyu.vo.AiJobVO;
import com.xinyu.vo.MoodRecordVO;

import java.time.LocalDateTime;

/** 管理持久任务；本阶段保存路径仅做数据库操作，不调用模型。 */
@Service
public class AiJobService {
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

    private static final Set<String> ACTIVE_OR_SUCCEEDED = Set.of("queued", "running", "succeeded");
    private static final Set<String> ERROR_CODES = Set.of(
            "invalid_request", "unauthorized", "provider_timeout", "provider_error", "invalid_model_response",
            "overloaded", "execution_interrupted", "capacity_exceeded", "internal_error");

    /** 原子领取一条待办；没有领取成功时由下一次扫描继续，不在这里反复抢占。 */
    public AiJob claimNext() {
        if (!workerEnabled) return null;
        return admission.transaction(() -> {
            AiJob candidate = jobs.selectNextQueued();
            if (candidate == null) return null;
            String requestId = UUID.randomUUID().toString();
            if (jobs.claim(candidate.getId(), candidate.getAttemptCount(), requestId) != 1) return null;
            return jobs.selectForUpdate(candidate.getId());
        });
    }

    /** 外部调用前检查来源；此处不持锁等待网络，提交时仍须再次核验。 */
    public MoodRecord sourceForAttempt(long jobId, int attempt, String requestId) {
        AiJob job = jobs.selectById(jobId);
        if (!isCurrentAttempt(job, attempt, requestId)) return null;
        MoodRecord source = moods.selectById(job.getTargetId());
        if (validSource(source, job)) return source;
        finish(job, "superseded", null, null);
        return null;
    }

    /** 来源行先锁、任务行后锁；回复、兼容日志与终态在同一短事务提交。 */
    public boolean complete(long jobId, int attempt, String requestId, MoodAiResponse result) {
        AiJob observed = jobs.selectById(jobId);
        if (!isCurrentAttempt(observed, attempt, requestId)) return false;
        return admission.transaction(() -> {
            MoodRecord source = moods.selectOwnedForUpdate(observed.getTargetId(), observed.getUserId());
            AiJob current = jobs.selectForUpdate(jobId);
            if (!isCurrentAttempt(current, attempt, requestId) || !sameTarget(observed, current)) return false;
            if (!validSource(source, current)) {
                finish(current, "superseded", null, null);
                return false;
            }
            String reply;
            try {
                if (result == null) throw new IllegalArgumentException("invalid_model_response");
                reply = result.validatedReply(jobId, attempt, requestId, current.getTargetVersion());
            } catch (IllegalArgumentException invalid) {
                finish(current, "failed", "invalid_model_response", null);
                return false;
            }
            if (!finish(current, "succeeded", null, result)) return false;
            if (moods.writeReplyAtVersion(current.getTargetId(), current.getUserId(), current.getTargetVersion(), reply) != 1) {
                throw new IllegalStateException("回应提交条件已变化");
            }
            if ("MODEL".equals(result.resultKind())) {
                AiReply log = new AiReply();
                log.setUserId(current.getUserId());
                log.setTargetType("MOOD_RECORD");
                log.setTargetId(current.getTargetId());
                log.setReplyContent(reply);
                log.setModelName(result.modelName());
                log.setStatus(1);
                // 不复制日记全文或提示词；SUPPORT 不伪造成模型调用成功记录。
                if (replies.insert(log) != 1) throw new IllegalStateException("回应日志保存未成功");
            }
            return true;
        });
    }

    /** 只结束仍属于本次尝试的 running，不据失败猜测供应商是否执行。 */
    public boolean fail(long jobId, int attempt, String requestId, String errorCode) {
        AiJob job = jobs.selectById(jobId);
        if (!isCurrentAttempt(job, attempt, requestId)) return false;
        String safeCode = ERROR_CODES.contains(errorCode == null ? "" : errorCode) ? errorCode : "internal_error";
        return finish(job, "failed", safeCode, null);
    }

    /** 失败重试沿用任务；准入锁覆盖容量检查到事务提交，但不反向锁定来源。 */
    public AiJobVO retry(long userId, long jobId) {
        return admission.save(() -> {
            AiJob job = ownedJob(userId, jobId);
            MoodRecord source = ownedCurrentSource(job);
            if (ACTIVE_OR_SUCCEEDED.contains(job.getStatus())) return view(job, source);
            if (!"failed".equals(job.getStatus()) || job.getAttemptCount() >= 3) {
                throw http(HttpStatus.CONFLICT, "当前任务不可重试");
            }
            if (capacityFull(userId)) throw http(HttpStatus.SERVICE_UNAVAILABLE, "回应任务较多，请稍后重试");
            int changed = jobs.requeue(job);
            // CAS 失败后只回查一次当前行；不循环重置已完成了另一轮的失败任务。
            AiJob current = jobs.selectForUpdate(jobId);
            if (current == null || !sameTarget(job, current)
                    || (changed == 0 && !ACTIVE_OR_SUCCEEDED.contains(current.getStatus()))) {
                throw http(HttpStatus.CONFLICT, "任务状态已变化，请刷新");
            }
            return view(current, source);
        });
    }

    /** 使用同一数据库快照读取任务与来源，先检查归属、删除及版本再返回回复。 */
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
        if (source == null || !Integer.valueOf(1).equals(source.getStatus())) {
            throw http(HttpStatus.NOT_FOUND, "心情记录不存在");
        }
        AiJob job = jobs.selectOne(new QueryWrapper<AiJob>().eq("task_type", "MOOD_REPLY")
                .eq("target_id", source.getId()).eq("target_version", source.getContentVersion())
                .eq("user_id", source.getUserId()));
        String state;
        if (job != null) state = job.getStatus();
        else if (hasText(source.getAiReply())) state = "legacy";
        else state = Boolean.TRUE.equals(source.getAiReplyEnabled()) ? "unavailable" : "disabled";
        String reply = "succeeded".equals(state) || "legacy".equals(state) ? source.getAiReply() : null;
        return new MoodRecordVO(source.getId(), source.getUserId(), source.getMoodType(), source.getContent(), reply,
                source.getStatus(), source.getCreateTime(), source.getUpdateTime(), source.getContentVersion(),
                source.getAiReplyEnabled(), state, job == null ? null : job.getId(),
                job != null && "succeeded".equals(state) ? job.getResultKind() : null);
    }

    private AiJob ownedJob(long userId, long jobId) {
        AiJob job = jobs.selectById(jobId);
        if (job == null) throw http(HttpStatus.NOT_FOUND, "回应任务不存在");
        if (!Objects.equals(job.getUserId(), userId)) throw http(HttpStatus.FORBIDDEN, "无权访问该任务");
        if (!"MOOD_REPLY".equals(job.getTaskType())) throw http(HttpStatus.NOT_FOUND, "回应任务不存在");
        return job;
    }

    private MoodRecord ownedCurrentSource(AiJob job) {
        MoodRecord source = moods.selectById(job.getTargetId());
        if (source == null || !Integer.valueOf(1).equals(source.getStatus())
                || !Objects.equals(source.getUserId(), job.getUserId())) {
            throw http(HttpStatus.NOT_FOUND, "心情记录不存在");
        }
        if (!validSource(source, job)) throw http(HttpStatus.CONFLICT, "回应任务已失效，请刷新记录");
        return source;
    }

    private static boolean validSource(MoodRecord source, AiJob job) {
        return source != null && Integer.valueOf(1).equals(source.getStatus())
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

    private static boolean isCurrentAttempt(AiJob job, int attempt, String requestId) {
        return job != null && "MOOD_REPLY".equals(job.getTaskType()) && "running".equals(job.getStatus())
                && attempt >= 1 && attempt <= 3 && Integer.valueOf(attempt).equals(job.getAttemptCount())
                && requestId != null && !requestId.isBlank() && requestId.equals(job.getRequestId());
    }

    private boolean finish(AiJob job, String status, String errorCode, MoodAiResponse result) {
        return jobs.finishAttempt(job, status, errorCode, result == null ? null : result.resultKind(),
                result == null ? null : result.modelCalled(), result == null ? null : result.modelName()) == 1;
    }

    private boolean capacityFull(long userId) {
        return jobs.selectCount(new QueryWrapper<AiJob>().eq("status", "queued")) >= 100
                || jobs.selectCount(new QueryWrapper<AiJob>().eq("user_id", userId).in("status", "queued", "running")) >= 3;
    }

    private static boolean hasText(String text) {
        return text != null && text.codePoints().anyMatch(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c) && c != 0x85);
    }

    private static AiJobVO view(AiJob job, MoodRecord source) {
        boolean succeeded = "succeeded".equals(job.getStatus());
        return new AiJobVO(job.getId(), job.getTargetId(), job.getTargetVersion(), job.getStatus(), job.getAttemptCount(),
                succeeded ? job.getResultKind() : null, succeeded ? source.getAiReply() : null,
                "failed".equals(job.getStatus()) ? job.getLastErrorCode() : null,
                "failed".equals(job.getStatus()) && job.getAttemptCount() < 3);
    }

    private static ResponseStatusException http(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    /** 调用方必须持有单实例准入锁，并处于来源保存的同一事务中。 */
    public AiJob createForVersion(MoodRecord source) {
        if (!Boolean.TRUE.equals(source.getAiReplyEnabled())) return null;
        boolean full = capacityFull(source.getUserId());
        AiJob job = new AiJob();
        job.setUserId(source.getUserId());
        job.setTargetId(source.getId());
        job.setTaskType("MOOD_REPLY");
        job.setTargetVersion(source.getContentVersion());
        job.setAttemptCount(0);
        job.setStatus(full ? "failed" : "queued");
        job.setTemplateVersion("mood-reply-v1");
        if (full) {
            job.setLastErrorCode("capacity_exceeded");
            job.setFinishedAt(LocalDateTime.now());
        }
        if (jobs.insert(job) != 1) throw new IllegalStateException("任务保存未成功");
        return job;
    }

    public void supersedeUnfinished(Long targetId, Long userId) {
        jobs.supersedeUnfinished(targetId, userId);
    }
}
