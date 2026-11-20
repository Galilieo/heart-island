package com.xinyu.service;

import com.xinyu.dto.MoodAiRequest;
import com.xinyu.dto.MoodAiResponse;
import com.xinyu.dto.ReviewAiResponse;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.MoodRecord;

import java.util.Objects;

import static com.xinyu.common.AiJobValues.MOOD_REVIEW;

/** 把日记回应与回顾接到共享 Worker，保留两种来源各自的预检和提交规则。 */
final class MoodJobStrategy implements AiJobWorker.Strategy {
    // Python 的心情生成预算上限，为 Java 内部传输和校验留出余量。
    private static final int PROVIDER_BUDGET_MS = 40_000;

    private final AiJobService jobs;
    private final AiServiceClient client;

    MoodJobStrategy(AiJobService jobs, AiServiceClient client) {
        this.jobs = Objects.requireNonNull(jobs);
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public AiJob claim() {
        return jobs.claimNext();
    }

    @Override
    public AiJobWorker.Prepared<?> prepare(AiJobWorker.Attempt attempt) {
        if (MOOD_REVIEW.equals(attempt.taskType())) {
            return prepareReview(attempt);
        }
        return prepareMoodReply(attempt);
    }

    private AiJobWorker.Prepared<MoodAiResponse> prepareMoodReply(AiJobWorker.Attempt attempt) {
        long jobId = attempt.jobId();
        int count = attempt.attemptNumber();
        String requestId = attempt.requestId();
        MoodRecord source = jobs.sourceForAttempt(jobId, count, requestId);
        if (source == null) {
            return null;
        }

        MoodAiRequest request = new MoodAiRequest(requestId, Long.toString(jobId), count,
                Long.toString(attempt.sourceVersion()), source.getMoodType(), source.getContent(), PROVIDER_BUDGET_MS);
        return new AiJobWorker.Prepared<>(null, remaining -> {
            var generation = client.startGeneration(request, remaining);
            return new AiServiceClient.Exchange<>(generation.response(), generation.terminated());
        }, reply -> AiJobWorker.Settlement.of(jobs.complete(jobId, count, requestId, reply)));
    }

    private AiJobWorker.Prepared<ReviewAiResponse> prepareReview(AiJobWorker.Attempt attempt) {
        long jobId = attempt.jobId();
        int count = attempt.attemptNumber();
        String requestId = attempt.requestId();
        MoodReviewService.Prepared review = jobs.prepareReview(jobId, count, requestId);
        if (review == null) {
            return null;
        }

        return new AiJobWorker.Prepared<>(review.support(), remaining -> {
            var generation = client.startReviewGeneration(review.request(), remaining);
            return new AiServiceClient.Exchange<>(generation.response(), generation.terminated());
        }, reply -> AiJobWorker.Settlement.of(jobs.completeReview(jobId, count, requestId, reply)));
    }

    @Override
    public AiJobWorker.Settlement fail(AiJobWorker.Attempt attempt, String code) {
        return AiJobWorker.Settlement.of(jobs.fail(attempt.jobId(), attempt.attemptNumber(), attempt.requestId(), code));
    }
}
