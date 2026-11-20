package com.xinyu.service;

import com.xinyu.dto.AiReplyResult;
import com.xinyu.dto.CommunityAiRequest;
import com.xinyu.dto.CommunityAiResponse;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.Topic;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.TopicMapper;

import java.util.Objects;
import java.util.UUID;

import static com.xinyu.common.AiJobValues.COMMUNITY_REPLY;
import static com.xinyu.common.AiJobValues.FAILED;
import static com.xinyu.common.AiJobValues.MODEL;
import static com.xinyu.common.AiJobValues.SUCCEEDED;
import static com.xinyu.common.AiJobValues.SUPERSEDED;

/** 社区 Worker 的来源与传输适配。每个数据库阶段由共用 Worker 管理短事务。 */
public final class CommunityReplyJobHandler implements AiJobWorker.Strategy {
    private static final String INVALID_MODEL_RESPONSE = "invalid_model_response";
    static final String TEMPLATE_VERSION = "community-reply-v1";

    private final AiJobMapper jobs;
    private final CommunityPostMapper posts;
    private final TopicMapper topics;
    private final CommunityAiClient client;
    private final AiReplyLogService logs;

    public CommunityReplyJobHandler(AiJobMapper jobs, CommunityPostMapper posts, TopicMapper topics,
                                    CommunityAiClient client, AiReplyLogService logs) {
        this.jobs = jobs;
        this.posts = posts;
        this.topics = topics;
        this.client = client;
        this.logs = logs;
    }

    public int recoverInterrupted() {
        return jobs.failInterruptedByType(COMMUNITY_REPLY);
    }

    @Override
    public AiJob claim() {
        AiJob next = jobs.selectNextQueued(COMMUNITY_REPLY);
        if (next == null) {
            return null;
        }

        String requestId = UUID.randomUUID().toString();
        if (jobs.claimByType(COMMUNITY_REPLY, next.getId(), next.getAttemptCount(), requestId) != 1) {
            return null;
        }
        return jobs.selectForUpdate(next.getId());
    }

    @Override
    public AiJobWorker.Prepared<CommunityAiResponse> prepare(AiJobWorker.Attempt attempt) {
        AiJob job = jobs.selectById(attempt.jobId());
        if (!isAttempt(job, attempt)) {
            return null;
        }
        CommunityPost post = posts.selectById(job.getTargetId());
        if (!CommunityReplySource.matches(post, job)) {
            finishWithoutReply(job, SUPERSEDED, null);
            return null;
        }

        Topic topic = topics.selectById(post.getTopicId());
        String topicName = topic == null ? "" : topic.getName();
        CommunityAiRequest request = new CommunityAiRequest(attempt.requestId(), topicName,
                post.getMoodType(), post.getContent(), CommunityAiRequest.MAX_BUDGET_MS);
        return new AiJobWorker.Prepared<>(null, remaining -> client.startGeneration(request, remaining),
                reply -> complete(attempt, reply));
    }

    private AiJobWorker.Settlement complete(AiJobWorker.Attempt attempt, CommunityAiResponse result) {
        AiJob current = lockCurrentAttempt(attempt);
        if (current == null) {
            return AiJobWorker.Settlement.of(false);
        }

        String reply;
        try {
            reply = validateReply(attempt, result);
        } catch (IllegalArgumentException invalid) {
            AiJobWorker.Settlement failed = failCurrent(current, INVALID_MODEL_RESPONSE);
            return new AiJobWorker.Settlement(false, failed.afterCommit());
        }

        if (!finishWithReply(current, result)) {
            return AiJobWorker.Settlement.of(false);
        }
        writePostReply(current, reply, result.resultKind());
        return successfulSettlement(current, result, reply);
    }

    private AiJob lockCurrentAttempt(AiJobWorker.Attempt attempt) {
        AiJob observed = jobs.selectById(attempt.jobId());
        if (!isAttempt(observed, attempt)) {
            return null;
        }

        // 与编辑、删除和隐藏统一先锁帖子，再锁任务。拿锁后重查归属、版本和可见性。
        CommunityPost post = posts.selectByIdForUpdate(observed.getTargetId());
        AiJob current = jobs.selectForUpdate(attempt.jobId());
        if (!isAttempt(current, attempt) || !AiJobService.sameTarget(observed, current)) {
            return null;
        }
        if (!CommunityReplySource.matches(post, current)) {
            finishWithoutReply(current, SUPERSEDED, null);
            return null;
        }
        return current;
    }

    private static String validateReply(AiJobWorker.Attempt attempt, CommunityAiResponse result) {
        if (result == null) {
            throw new IllegalArgumentException(INVALID_MODEL_RESPONSE);
        }
        return result.validatedReply(attempt.requestId());
    }

    private void writePostReply(AiJob job, String reply, String resultKind) {
        int written = posts.writeAiReplyAtVisibleVersion(
                job.getTargetId(), job.getUserId(), job.getTargetVersion(), reply, resultKind);
        if (written != 1) {
            throw new IllegalStateException("社区回应提交条件已变化");
        }
    }

    private AiJobWorker.Settlement successfulSettlement(AiJob job, CommunityAiResponse result, String reply) {
        if (MODEL.equals(result.resultKind())) {
            return withLog(job, AiReplyResult.success(reply, null, result.modelName()));
        }
        return AiJobWorker.Settlement.of(true);
    }

    @Override
    public AiJobWorker.Settlement fail(AiJobWorker.Attempt attempt, String code) {
        AiJob job = jobs.selectById(attempt.jobId());
        if (!isAttempt(job, attempt)) {
            return AiJobWorker.Settlement.of(false);
        }
        return failCurrent(job, AiJobService.safeErrorCode(code));
    }

    private AiJobWorker.Settlement failCurrent(AiJob job, String code) {
        if (!finishWithoutReply(job, FAILED, code)) {
            return AiJobWorker.Settlement.of(false);
        }
        return withLog(job, AiReplyResult.failure(null, null, null, code));
    }

    private boolean finishWithoutReply(AiJob job, String status, String errorCode) {
        return jobs.finishAttemptByType(COMMUNITY_REPLY, job, status, errorCode, null, null, null) == 1;
    }

    private boolean finishWithReply(AiJob job, CommunityAiResponse reply) {
        return jobs.finishAttemptByType(COMMUNITY_REPLY, job, SUCCEEDED, null,
                reply.resultKind(), reply.modelCalled(), reply.modelName()) == 1;
    }

    private static boolean isAttempt(AiJob job, AiJobWorker.Attempt attempt) {
        return AiJobService.isCurrentAttempt(job, COMMUNITY_REPLY,
                attempt.attemptNumber(), attempt.requestId())
                && Objects.equals(job.getTargetVersion(), attempt.sourceVersion());
    }

    private AiJobWorker.Settlement withLog(AiJob job, AiReplyResult result) {
        return new AiJobWorker.Settlement(true,
                () -> logs.savePostReply(job.getUserId(), job.getTargetId(), result));
    }
}
