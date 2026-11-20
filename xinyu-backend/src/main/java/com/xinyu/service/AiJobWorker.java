package com.xinyu.service;

import com.xinyu.entity.AiJob;
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
import java.util.function.Function;
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

    // 心情组生成阶段最多 50 秒；Python 预算最多 40 秒，余量给连接、传输和本地校验。
    static final Duration GENERATION_LIMIT = Duration.ofSeconds(50);
    // SQL 超时不覆盖连接获取、JDBC 网络等待和提交，数据源另设有界等待。
    private static final int DATABASE_TIMEOUT_SECONDS = 5;
    private static final String PROVIDER_TIMEOUT_CODE = "provider_timeout";
    private static final String INTERNAL_ERROR_CODE = "internal_error";

    /** 一次执行的结果：无任务、跳过、成功、已标记失败、失败收尾也未完成。 */
    public enum Outcome { IDLE, SKIPPED, SUCCEEDED, FAILED, UNRESOLVED }

    private final Strategy strategy;
    private final Duration generationLimit;
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
        this(new MoodJobStrategy(jobs, client), GENERATION_LIMIT, manager, database, nanoTime, acceptingClaims);
    }

    /** 使用单调纳秒时钟计算期限，领取前通过 acceptingClaims 检查运行时是否还接受新任务。 */
    public AiJobWorker(Strategy strategy, Duration generationLimit, PlatformTransactionManager manager,
                       Executor database, LongSupplier nanoTime, BooleanSupplier acceptingClaims) {
        this.strategy = Objects.requireNonNull(strategy);
        this.generationLimit = Objects.requireNonNull(generationLimit);
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

        AiJob job = shortTransactions.execute(status -> strategy.claim());
        if (job == null) {
            return null;
        }

        long deadlineNanos = nanoTime.getAsLong() + generationLimit.toNanos();
        // 保存领取时的身份快照，后续不再依赖可变实体的 attempt 或 requestId。
        Attempt attempt = new Attempt(job.getId(), job.getAttemptCount(),
                job.getRequestId(), job.getTargetVersion(), job.getTaskType());
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

            Prepared<?> prepared = shortTransactions.execute(status -> strategy.prepare(attempt));
            if (prepared == null) {
                return CompletableFuture.completedFuture(Outcome.SKIPPED);
            }

            requireBudget(claimed);
            return execute(attempt, prepared, Duration.ofNanos(budgetLeftNanos(claimed)));
        } catch (RuntimeException problem) {
            return CompletableFuture.completedFuture(finishFailure(attempt, errorCode(problem)));
        }
    }

    private <R> CompletableFuture<Outcome> execute(Attempt attempt, Prepared<R> prepared, Duration remaining) {
        if (prepared.localReply() != null) {
            return CompletableFuture.completedFuture(settle(attempt, prepared, prepared.localReply(), null));
        }

        AiServiceClient.Exchange<R> generation = prepared.start().apply(remaining);
        CompletableFuture<Outcome> settled = generation.response()
                .handleAsync((reply, problem) -> settle(attempt, prepared, reply, problem), database);
        // 归还槽位要等数据库收尾与 HTTP 清理均结束。
        return settled.thenCombine(generation.terminated(), (outcome, ignored) -> outcome);
    }

    private <R> Outcome settle(Attempt attempt, Prepared<R> prepared, R reply, Throwable problem) {
        if (problem != null) {
            return finishFailure(attempt, errorCode(problem));
        }

        try {
            Settlement completed = shortTransactions.execute(status -> prepared.complete().apply(reply));
            afterCommit(completed);
            return completed.changed() ? Outcome.SUCCEEDED : Outcome.SKIPPED;
        } catch (RuntimeException failure) {
            // 原事务已经结束，失败收尾使用新的短事务。
            return finishFailure(attempt, INTERNAL_ERROR_CODE);
        }
    }

    private Outcome finishFailure(Attempt attempt, String code) {
        // 在独立短事务中按本次身份条件标记 failed；条件未命中不能覆盖旧状态。
        // 收尾事务也失败时，只记录安全追踪字段，不能打印原异常、正文或堆栈。
        try {
            Settlement marked = shortTransactions.execute(status -> strategy.fail(attempt, code));
            afterCommit(marked);
            return marked.changed() ? Outcome.FAILED : Outcome.SKIPPED;
        } catch (RuntimeException cleanupFailure) {
            logger.warn("任务失败收尾未完成 jobId={} attempt={} code={}",
                    attempt.jobId(),
                    attempt.attemptNumber(),
                    code);
            return Outcome.UNRESOLVED;
        }
    }

    private static void afterCommit(Settlement settlement) {
        if (settlement.afterCommit() == null) {
            return;
        }
        try {
            settlement.afterCommit().run();
        } catch (RuntimeException problem) {
            logger.warn("任务提交后附加写入失败 code=internal_error");
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

    /** 数据库执行器上的领域策略，方法均在 Worker 管理的短事务中运行。 */
    public interface Strategy {
        /** @return 已领取的任务快照；若队列为空或领取条件更新未命中，返回 null。 */
        AiJob claim();

        /** @return 本次准备结果；尝试或来源已失效时返回 null，Worker 跳过生成。 */
        Prepared<?> prepare(Attempt attempt);

        /** @return 失败状态是否已写入，以及提交后可选的附加动作。 */
        Settlement fail(Attempt attempt, String code);
    }

    /**
     * 预检产物。有本地回复时直接完成；否则在事务外启动传输。
     * 两条路径的 complete 都由 Worker 在新的数据库短事务中调用。
     */
    public record Prepared<R>(R localReply, Function<Duration, AiServiceClient.Exchange<R>> start,
                              Function<R, Settlement> complete) { }

    /** 提交后的附加动作不应改变原事务的结果。 */
    public record Settlement(boolean changed, Runnable afterCommit) {
        static Settlement of(boolean changed) {
            return new Settlement(changed, null);
        }
    }

    public record Attempt(long jobId, int attemptNumber, String requestId, long sourceVersion, String taskType) { }

    private record Claimed(Attempt attempt, long deadlineNanos) { }
}
