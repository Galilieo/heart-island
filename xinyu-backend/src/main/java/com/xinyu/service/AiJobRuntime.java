package com.xinyu.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 单进程 worker 生命周期：恢复成功后才扫描，空闲槽位才领取，数据库仍是唯一待办队列。
 *
 * <p>线程约定：扫描线程只补空槽；领取、来源预检、结果提交和失败收尾都在数据库执行器上执行；
 * 网络等待由内部客户端的 HTTP 线程承担。停止时先关领取闸门与真实 I/O，再等待执行退出并关闭执行器。</p>
 */
public final class AiJobRuntime implements SmartLifecycle, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(AiJobRuntime.class);
    private static final int RECOVERY_TIMEOUT_SECONDS = 5;
    private static final String STARTUP_RECOVERY_FAILED = "ai_worker_recovery_failed";
    private static final String SHUTDOWN_INTERRUPTED = "ai_worker_shutdown_interrupted";
    private static final String SHUTDOWN_INCOMPLETE = "ai_worker_shutdown_incomplete";

    private enum State { NEW, STARTING, RUNNING, FAILED, STOPPING, STOPPED }

    private final Object lifecycle = new Object();
    private final AiJobService jobs;
    private final AiServiceClient client;
    private final AiJobWorker worker;
    private final TransactionTemplate recoveryTransactions;
    private final ScheduledExecutorService scanner;
    private final ThreadPoolExecutor database;
    private final Set<CompletableFuture<AiJobWorker.Outcome>> executions = new HashSet<>();
    private final CompletableFuture<Void> recoveryFinished = new CompletableFuture<>();
    private final int concurrency;
    private final Duration scanInterval;
    private final Duration shutdownTimeout;
    private CompletableFuture<Void> stopAttempt = new CompletableFuture<>();
    private State state = State.NEW;
    private ScheduledFuture<?> scanTask;

    public AiJobRuntime(AiJobService jobs, AiServiceClient client, PlatformTransactionManager manager,
                        int concurrency, Duration scanInterval, Duration shutdownTimeout) {
        requirePositive(scanInterval);
        requirePositive(shutdownTimeout);
        if (concurrency < 1) {
            throw invalidConfiguration();
        }

        this.jobs = jobs;
        this.client = client;
        this.concurrency = concurrency;
        this.scanInterval = scanInterval;
        this.shutdownTimeout = shutdownTimeout;
        scanner = Executors.newSingleThreadScheduledExecutor(task -> daemon(task, "ai-job-scan"));
        database = newDatabaseExecutor(concurrency);
        worker = new AiJobWorker(jobs, client, manager, database, System::nanoTime, this::isRunning);
        recoveryTransactions = new TransactionTemplate(manager);
        recoveryTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        recoveryTransactions.setTimeout(RECOVERY_TIMEOUT_SECONDS);
    }

    @Override
    public void start() {
        if (!markStarting()) {
            return;
        }
        recoverInterrupted();
        beginScanning();
    }

    private boolean markStarting() {
        synchronized (lifecycle) {
            if (state != State.NEW) {
                return false;
            }
            state = State.STARTING;
            return true;
        }
    }

    /** 启动恢复只执行一次；失败时保留 FAILED 状态且不扫描，异常不携带 SQL、地址或凭据。 */
    private void recoverInterrupted() {
        try {
            recoveryTransactions.execute(status -> jobs.recoverInterrupted());
        } catch (RuntimeException failure) {
            synchronized (lifecycle) {
                if (state == State.STARTING) {
                    state = State.FAILED;
                }
            }
            // 启动异常可能带 SQL、地址或凭据，不把原异常挂到公开异常链。
            throw new IllegalStateException(STARTUP_RECOVERY_FAILED);
        } finally {
            recoveryFinished.complete(null);
        }
    }

    /** 恢复完成后才开始扫描；并发 stop 已把状态改成 STOPPING 时，这里不再启动调度。 */
    private void beginScanning() {
        synchronized (lifecycle) {
            if (state != State.STARTING) {
                return;
            }
            state = State.RUNNING;
            scanTask = scanner.scheduleWithFixedDelay(this::fillFreeSlots, 0,
                    scanInterval.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    /** 扫描只补空槽；数据库阶段交给执行器，等待HTTP时不占数据库线程，槽位由执行完成回调归还。 */
    private void fillFreeSlots() {
        synchronized (lifecycle) {
            if (state != State.RUNNING) {
                return;
            }
            int available = concurrency - executions.size();
            for (int slot = 0; slot < available; slot++) {
                startExecution();
            }
        }
    }

    private void startExecution() {
        CompletableFuture<AiJobWorker.Outcome> execution = worker.runOne();
        executions.add(execution);
        execution.whenComplete((outcome, problem) -> executionFinished(execution, problem));
    }

    private void executionFinished(CompletableFuture<AiJobWorker.Outcome> execution, Throwable problem) {
        synchronized (lifecycle) {
            executions.remove(execution);
        }
        if (problem != null) {
            logger.warn("任务执行退出 code=worker_execution_failed");
        }
    }

    @Override
    public boolean isRunning() {
        synchronized (lifecycle) {
            return state == State.RUNNING;
        }
    }

    /** 当前持有的执行槽位数，含生成、数据库收尾和 HTTP 清理阶段。 */
    public int activeExecutions() {
        synchronized (lifecycle) {
            return executions.size();
        }
    }

    @Override
    public void stop() {
        StopPlan plan = beginStopping();
        if (plan == null) {
            return;
        }
        if (plan.joinedOngoingStop()) {
            awaitStopped(plan.attempt());
            return;
        }

        long deadlineNanos = System.nanoTime() + shutdownTimeout.toNanos();
        try {
            awaitExecutionsAndHttp(plan, deadlineNanos);
            awaitDatabaseAndScanner(deadlineNanos);
            markStopped(plan.attempt());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            plan.attempt().completeExceptionally(new IllegalStateException(SHUTDOWN_INTERRUPTED));
            throw new IllegalStateException(SHUTDOWN_INTERRUPTED);
        } catch (ExecutionException | TimeoutException | RuntimeException failure) {
            // 不清空槽位，也不伪称资源已退出；运维必须确认旧进程退出后再启动新 worker。
            plan.attempt().completeExceptionally(new IllegalStateException(SHUTDOWN_INCOMPLETE));
            throw new IllegalStateException(SHUTDOWN_INCOMPLETE);
        }
    }

    /** 状态与等待目标在同一把锁内确定；已有 stop 在等待时直接复用它的完成信号。 */
    private StopPlan beginStopping() {
        synchronized (lifecycle) {
            if (state == State.STOPPED) {
                return null;
            }
            if (state == State.STOPPING && !stopAttempt.isDone()) {
                return new StopPlan(stopAttempt, List.of(), true);
            }

            // 上次等待失败后允许重试清理；并发 stop 则共享本轮结果。
            stopAttempt = new CompletableFuture<>();
            if (state == State.NEW) {
                recoveryFinished.complete(null);
            }
            state = State.STOPPING;
            if (scanTask != null) {
                scanTask.cancel(false);
            }
            scanner.shutdown();

            List<CompletableFuture<?>> pending = new ArrayList<>(executions);
            pending.add(recoveryFinished);
            return new StopPlan(stopAttempt, pending, false);
        }
    }

    /** 先取消 HTTP 并等待执行与清理信号，再让数据库执行器退出；等待期间保留执行器接住失败收尾。 */
    private void awaitExecutionsAndHttp(StopPlan plan, long deadlineNanos)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> settled = CompletableFuture
                .allOf(plan.pending().toArray(new CompletableFuture<?>[0]))
                .handle((ignored, failure) -> null);
        // 本轮等待即便失败，在途执行稍后结束时也会继续关闭数据库执行器。
        settled.thenRun(database::shutdown);
        // 先取消真实 I/O；此时必须保留数据库执行器，接住取消引起的失败收尾。
        client.close();
        settled.get(remainingNanos(deadlineNanos), TimeUnit.NANOSECONDS);
        if (!client.awaitTermination(Duration.ofNanos(remainingNanos(deadlineNanos)))) {
            throw new TimeoutException();
        }
    }

    private void awaitDatabaseAndScanner(long deadlineNanos) throws InterruptedException, TimeoutException {
        database.shutdown();
        if (!database.awaitTermination(remainingNanos(deadlineNanos), TimeUnit.NANOSECONDS)) {
            throw new TimeoutException();
        }
        if (!scanner.awaitTermination(remainingNanos(deadlineNanos), TimeUnit.NANOSECONDS)) {
            throw new TimeoutException();
        }
    }

    private void markStopped(CompletableFuture<Void> attempt) {
        synchronized (lifecycle) {
            state = State.STOPPED;
        }
        attempt.complete(null);
    }

    private void awaitStopped(CompletableFuture<Void> attempt) {
        try {
            attempt.get(shutdownTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(SHUTDOWN_INTERRUPTED);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException(SHUTDOWN_INCOMPLETE);
        }
    }

    private static long remainingNanos(long deadlineNanos) {
        return Math.max(0, deadlineNanos - System.nanoTime());
    }

    private static ThreadPoolExecutor newDatabaseExecutor(int concurrency) {
        return new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(concurrency), task -> daemon(task, "ai-job-database"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    private static void requirePositive(Duration value) {
        if (value.isZero() || value.isNegative()) {
            throw invalidConfiguration();
        }
    }

    private static IllegalArgumentException invalidConfiguration() {
        return new IllegalArgumentException("invalid_ai_worker_configuration");
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    @Override
    public void close() {
        stop();
    }

    private record StopPlan(CompletableFuture<Void> attempt, List<CompletableFuture<?>> pending,
                            boolean joinedOngoingStop) {
    }
}
