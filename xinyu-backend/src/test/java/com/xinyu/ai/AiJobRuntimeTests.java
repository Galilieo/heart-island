package com.xinyu.ai;

import com.xinyu.dto.MoodAiRequest;
import com.xinyu.dto.MoodAiResponse;
import com.xinyu.entity.AiJob;
import com.xinyu.service.AiJobRuntime;
import com.xinyu.service.AiJobService;
import com.xinyu.service.AiServiceClient;
import com.xinyu.service.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实扫描与数据库执行器，替换任务服务、事务管理器和HTTP边界，不冒充真实数据库证据。 */
class AiJobRuntimeTests {
    private AiJobService jobs;
    private AiServiceClient client;
    private PlatformTransactionManager manager;
    private final Queue<AiJob> queued = new ConcurrentLinkedQueue<>();
    private final BlockingQueue<ModelCall> calls = new LinkedBlockingQueue<>();
    private final Queue<ModelCall> active = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void setup() throws Exception {
        jobs = mock(AiJobService.class);
        client = mock(AiServiceClient.class);
        manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));
        when(jobs.claimNext()).thenAnswer(invocation -> queued.poll());
        when(jobs.sourceForAttempt(anyLong(), anyInt(), anyString())).thenReturn(AiJobWorkerTests.source());
        when(jobs.complete(anyLong(), anyInt(), anyString(), any())).thenReturn(true);
        when(jobs.fail(anyLong(), anyInt(), anyString(), anyString())).thenReturn(true);
        when(client.awaitTermination(any())).thenReturn(true);
        when(client.startGeneration(any(), any())).thenAnswer(invocation -> {
            ModelCall call = new ModelCall(invocation.getArgument(0),
                    new CompletableFuture<>(), new CompletableFuture<>());
            active.add(call);
            calls.add(call);
            return new AiServiceClient.Generation(call.response(), call.terminated());
        });
        doAnswer(invocation -> {
            for (ModelCall call : active) {
                call.response().completeExceptionally(new AiServiceException("execution_interrupted"));
                call.terminated().complete(null);
            }
            return null;
        }).when(client).close();
    }

    @Test
    void startupRecoversOnceBeforeFirstScan() throws Exception {
        queued.add(job(1));
        try (AiJobRuntime runtime = runtime()) {
            runtime.start();
            nextCall();
            runtime.start();
            var order = inOrder(jobs);
            order.verify(jobs).recoverInterrupted();
            order.verify(jobs, atLeastOnce()).claimNext();
            verify(jobs, times(1)).recoverInterrupted();
            assertThat(runtime.isRunning()).isTrue();
        }
    }

    @Test
    void recoveryFailureDoesNotStartScanning() {
        when(jobs.recoverInterrupted()).thenThrow(new IllegalStateException("private-db-error"));
        try (AiJobRuntime runtime = runtime()) {
            assertThatThrownBy(runtime::start).hasMessage("ai_worker_recovery_failed").hasNoCause();
            assertThat(runtime.isRunning()).isFalse();
            verify(jobs, never()).claimNext();
            verifyNoInteractions(client);
        }
    }

    @Test
    void threeSlotsKeepFourthQueuedUntilDatabaseAndHttpBothFinish() throws Exception {
        for (int id = 1; id <= 4; id++) {
            queued.add(job(id));
        }
        try (AiJobRuntime runtime = runtime()) {
            runtime.start();
            ModelCall first = nextCall();
            nextCall();
            nextCall();
            assertThat(calls.poll(100, TimeUnit.MILLISECONDS)).isNull();
            assertThat(queued).hasSize(1);
            assertThat(runtime.activeExecutions()).isEqualTo(3);

            first.succeed();
            assertThat(calls.poll(100, TimeUnit.MILLISECONDS)).isNull();
            first.terminated().complete(null);
            nextCall();
            assertThat(queued).isEmpty();
            assertThat(runtime.activeExecutions()).isEqualTo(3);
        }
    }

    @Test
    void failedSlotCanBeReusedWhileOtherTwoKeepRunning() throws Exception {
        for (int id = 1; id <= 4; id++) {
            queued.add(job(id));
        }
        try (AiJobRuntime runtime = runtime()) {
            runtime.start();
            ModelCall first = nextCall();
            ModelCall second = nextCall();
            ModelCall third = nextCall();
            first.response().completeExceptionally(new AiServiceException("provider_timeout"));
            first.terminated().complete(null);
            nextCall();
            assertThat(second.response()).isNotDone();
            assertThat(third.response()).isNotDone();
        }
    }

    @Test
    void timedOutStopCanFinishCleanupAfterPendingHttpFinallyExits() throws Exception {
        queued.add(job(1));
        doAnswer(invocation -> {
            for (ModelCall call : active) {
                call.response().completeExceptionally(new AiServiceException("execution_interrupted"));
            }
            return null;
        }).when(client).close();
        AiJobRuntime runtime = new AiJobRuntime(jobs, client, manager,
                3, Duration.ofMillis(20), Duration.ofMillis(100));
        runtime.start();
        ModelCall call = nextCall();
        assertThatThrownBy(runtime::stop).hasMessage("ai_worker_shutdown_incomplete");
        assertThat(runtime.activeExecutions()).isEqualTo(1);

        call.terminated().complete(null);
        runtime.stop();
        assertThat(runtime.activeExecutions()).isZero();
        assertThat(runtime.isRunning()).isFalse();
    }

    @Test
    void stopWaitsForHttpCleanupAfterCancellationHasBeenSettled() throws Exception {
        queued.add(job(1));
        CountDownLatch closed = new CountDownLatch(1);
        doAnswer(invocation -> {
            for (ModelCall call : active) {
                call.response().completeExceptionally(new AiServiceException("execution_interrupted"));
            }
            closed.countDown();
            return null;
        }).when(client).close();
        AiJobRuntime runtime = runtime();
        runtime.start();
        ModelCall call = nextCall();
        CompletableFuture<Void> stopping = CompletableFuture.runAsync(runtime::stop);
        try {
            assertThat(closed.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(stopping).isNotDone();
            assertThat(runtime.activeExecutions()).isEqualTo(1);
        } finally {
            call.terminated().complete(null);
            stopping.get(3, TimeUnit.SECONDS);
        }
        assertThat(runtime.activeExecutions()).isZero();
    }

    @Test
    void stopAlsoWaitsForStartupRecoveryToExit() throws Exception {
        CountDownLatch recovering = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        when(jobs.recoverInterrupted()).thenAnswer(invocation -> {
            recovering.countDown();
            assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
            return 0;
        });
        doAnswer(invocation -> {
            closed.countDown();
            return null;
        }).when(client).close();
        AiJobRuntime runtime = runtime();
        CompletableFuture<Void> starting = CompletableFuture.runAsync(runtime::start);
        assertThat(recovering.await(1, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> stopping = CompletableFuture.runAsync(runtime::stop);
        try {
            assertThat(closed.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(stopping).isNotDone();
        } finally {
            release.countDown();
            starting.get(3, TimeUnit.SECONDS);
            stopping.get(3, TimeUnit.SECONDS);
        }
        verify(jobs, never()).claimNext();
    }

    @Test
    void stopKeepsDatabaseExecutorAliveUntilCancelledRequestsAreSettled() throws Exception {
        queued.add(job(1));
        AiJobRuntime runtime = runtime();
        runtime.start();
        ModelCall call = nextCall();
        runtime.stop();
        runtime.stop();

        verify(jobs).fail(1, 1, "req-1", "execution_interrupted");
        assertThat(call.terminated()).isCompleted();
        assertThat(runtime.activeExecutions()).isZero();
        assertThat(runtime.isRunning()).isFalse();
        verify(client, times(1)).close();
        int claims = mockingDetails(jobs).getInvocations().size();
        runtime.start();
        assertThat(mockingDetails(jobs).getInvocations()).hasSize(claims);
    }

    private AiJobRuntime runtime() {
        return new AiJobRuntime(jobs, client, manager, 3, Duration.ofMillis(20), Duration.ofSeconds(3));
    }

    private ModelCall nextCall() throws InterruptedException {
        ModelCall call = calls.poll(3, TimeUnit.SECONDS);
        assertThat(call).as("预期发生一次合成生成调用").isNotNull();
        return call;
    }

    private static AiJob job(long id) {
        AiJob job = AiJobWorkerTests.job();
        job.setId(id);
        job.setRequestId("req-" + id);
        return job;
    }

    private record ModelCall(MoodAiRequest request, CompletableFuture<MoodAiResponse> response,
                             CompletableFuture<Void> terminated) {
        void succeed() {
            response.complete(new MoodAiResponse(request.requestId(), request.jobId(), request.attempt(),
                    request.sourceVersion(), "合成回复", "MODEL", true, "mood-reply-v1", "synthetic"));
        }
    }
}
