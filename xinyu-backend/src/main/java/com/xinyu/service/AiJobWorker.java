package com.xinyu.service;

import com.xinyu.dto.MoodAiRequest;
import com.xinyu.dto.MoodAiResponse;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.MoodRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * 只执行一条任务的编排单元，不注册扫描、不恢复任务、不拥有线程池。
 * 领取、来源预检及结果提交均交给显式数据库执行器，等待网络时不占该执行器线程。
 * 自动扫描与线程池生命周期由调用方管理。
 *
 * <p>线程约定：claim/generate/settle 都跑在数据库执行器上；供应商等待在 HTTP 线程，
 * 结果回调再回到数据库执行器；terminated 信号保证 HTTP 清理完成后才归还执行槽位。</p>
 */
public final class AiJobWorker {
    private static final Logger logger = LoggerFactory.getLogger(AiJobWorker.class);

    // Task3预算约定：生成阶段共50秒，其中传给Python的处理预算最多40秒。
    private static final Duration GENERATION_LIMIT = Duration.ofSeconds(50);
    private static final int PROVIDER_BUDGET_MS = 40_000;
    // Task3短事务的SQL超时配置为5秒，不涵盖连接获取、网络及提交／回滚的全部等待。
    private static final int DATABASE_TIMEOUT_SECONDS = 5;
    private static final String PROVIDER_TIMEOUT_CODE = "provider_timeout";
    private static final String INTERNAL_ERROR_CODE = "internal_error";

    /** 一次执行的结果：无任务、跳过、成功、已标记失败、失败收尾也未完成。 */
    public enum Outcome { IDLE, SKIPPED, SUCCEEDED, FAILED, UNRESOLVED }

    private final AiJobService jobs;
    private final AiServiceClient client;
    private final Executor database;
    private final LongSupplier nanoTime;
    private final BooleanSupplier acceptingClaims;
    /** 领取预检、结果提交和失败收尾各自独立：每次调用都是 REQUIRES_NEW + 5 秒。 */
    private final TransactionTemplate shortTransactions;

    /** nanoTime须提供与System.nanoTime同口径的单调纳秒时钟，不能传入墙上时间毫秒。 */
    public AiJobWorker(AiJobService jobs, AiServiceClient client, PlatformTransactionManager manager,
                       Executor database, LongSupplier nanoTime) {
        this(jobs, client, manager, database, nanoTime, () -> true);
    }

    /** 生命周期闸门在领取真正开始前读取；已经开始的领取仍须按在途执行完成收尾。 */
    public AiJobWorker(AiJobService jobs, AiServiceClient client, PlatformTransactionManager manager,
                       Executor database, LongSupplier nanoTime, BooleanSupplier acceptingClaims) {
        this.jobs = Objects.requireNonNull(jobs);
        this.client = Objects.requireNonNull(client);
        this.database = Objects.requireNonNull(database);
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.acceptingClaims = Objects.requireNonNull(acceptingClaims);
        shortTransactions = shortTransaction(manager);
    }

    /** 领取至多一条：领取与生成发起都在数据库执行器上，等待网络不占该执行器线程。 */
    public CompletableFuture<Outcome> runOne() {
        return CompletableFuture.supplyAsync(this::claim, database)
                .thenComposeAsync(this::generate, database);
    }

    private Claimed claim() {
        if (!acceptingClaims.getAsBoolean()) {
            return null;
        }

        AiJob job = shortTransactions.execute(status -> jobs.claimNext());
        if (job == null) {
            return null;
        }

        long deadlineNanos = nanoTime.getAsLong() + GENERATION_LIMIT.toNanos();
        // 保存领取时的身份快照，后续不再依赖可变实体的 attempt 或 requestId。
        Attempt attempt = new Attempt(job.getId(), job.getAttemptCount(),
                job.getRequestId(), job.getTargetVersion());
        return new Claimed(attempt, deadlineNanos);
    }

    private CompletableFuture<Outcome> generate(Claimed claimed) {
        if (claimed == null) {
            return CompletableFuture.completedFuture(Outcome.IDLE);
        }

        Attempt attempt = claimed.attempt();
        try {
            // 交接排队可能已经耗尽预算，过期时不再发起来源查询。
            requireBudget(claimed);

            // 预检可能将失效来源标为superseded，因而也必须限制更新时的锁等待。
            MoodRecord source = shortTransactions.execute(status ->
                    jobs.sourceForAttempt(attempt.jobId(), attempt.attemptNumber(), attempt.requestId()));
            if (source == null) {
                return CompletableFuture.completedFuture(Outcome.SKIPPED);
            }

            long remainingNanos = budgetLeftNanos(claimed);
            if (remainingNanos <= 0) {
                throw new AiServiceException(PROVIDER_TIMEOUT_CODE);
            }

            MoodAiRequest request = new MoodAiRequest(attempt.requestId(), Long.toString(attempt.jobId()),
                    attempt.attemptNumber(), Long.toString(attempt.sourceVersion()), source.getMoodType(),
                    source.getContent(), PROVIDER_BUDGET_MS);

            AiServiceClient.Generation generation = client.startGeneration(request, Duration.ofNanos(remainingNanos));
            CompletableFuture<Outcome> settled = generation.response()
                    .handleAsync((reply, problem) -> settle(attempt, reply, problem), database);
            // 数据库收尾与 HTTP 清理都结束后，生命周期层才可以归还本次执行槽位。
            return settled.thenCombine(generation.terminated(), (outcome, ignored) -> outcome);
        } catch (RuntimeException problem) {
            return CompletableFuture.completedFuture(finishFailure(attempt, errorCode(problem)));
        }
    }

    private Outcome settle(Attempt attempt, MoodAiResponse reply, Throwable problem) {
        if (problem != null) {
            return finishFailure(attempt, errorCode(problem));
        }

        try {
            Boolean completed = shortTransactions.execute(status ->
                    jobs.complete(attempt.jobId(), attempt.attemptNumber(), attempt.requestId(), reply));
            // false 可能已由服务标记 superseded 或 failed；不得再次覆盖它的条件终态。
            return Boolean.TRUE.equals(completed) ? Outcome.SUCCEEDED : Outcome.SKIPPED;
        } catch (RuntimeException completionFailure) {
            // 捕获点在 execute 之外，原提交事务已结束；这里不能重新调用模型。
            return finishFailure(attempt, INTERNAL_ERROR_CODE);
        }
    }

    private Outcome finishFailure(Attempt attempt, String code) {
        // 在独立短事务中按本次身份条件标记 failed；条件未命中不能覆盖旧状态。
        // 收尾事务也失败时，只记录安全追踪字段，不能打印原异常、正文或堆栈。
        try {
            Boolean marked = shortTransactions.execute(status -> jobs.fail(
                    attempt.jobId(),
                    attempt.attemptNumber(),
                    attempt.requestId(),
                    code));

            return Boolean.TRUE.equals(marked) ? Outcome.FAILED : Outcome.SKIPPED;
        } catch (RuntimeException cleanupFailure) {
            logger.warn("任务失败收尾未完成 jobId={} attempt={} code={}",
                    attempt.jobId(),
                    attempt.attemptNumber(),
                    code);
            return Outcome.UNRESOLVED;
        }
    }

    /** 生成阶段剩余预算；deadline 以领取时的单调纳秒记下。 */
    private long budgetLeftNanos(Claimed claimed) {
        return claimed.deadlineNanos() - nanoTime.getAsLong();
    }

    private void requireBudget(Claimed claimed) {
        if (budgetLeftNanos(claimed) <= 0) {
            throw new AiServiceException(PROVIDER_TIMEOUT_CODE);
        }
    }

    private static TransactionTemplate shortTransaction(PlatformTransactionManager manager) {
        TransactionTemplate template = new TransactionTemplate(Objects.requireNonNull(manager));
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setTimeout(DATABASE_TIMEOUT_SECONDS);
        return template;
    }

    /** 去掉 CompletableFuture 的包装异常，只保留可安全落库的错误分类。 */
    private static String errorCode(Throwable problem) {
        Throwable cause = problem;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof AiServiceException safe ? safe.code() : INTERNAL_ERROR_CODE;
    }

    private record Attempt(long jobId, int attemptNumber, String requestId, long sourceVersion) { }

    private record Claimed(Attempt attempt, long deadlineNanos) { }
}
