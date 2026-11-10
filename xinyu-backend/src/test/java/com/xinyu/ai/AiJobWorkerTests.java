package com.xinyu.ai;

import com.xinyu.dto.MoodAiRequest;
import com.xinyu.dto.MoodAiResponse;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.MoodRecord;
import com.xinyu.service.AiJobService;
import com.xinyu.service.AiJobWorker;
import com.xinyu.service.AiServiceClient;
import com.xinyu.service.AiServiceException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 只替换任务服务、网络客户端和事务管理器；验证编排，不冒充MySQL事务／锁证据。 */
class AiJobWorkerTests {
    private static final String REQUEST_TEXT = "合成记录";

    AiJobService jobs;
    AiServiceClient client;
    PlatformTransactionManager manager;
    TransactionStatus transaction;
    ManualExecutor database;
    AtomicLong clock;
    AiJobWorker worker;
    CompletableFuture<MoodAiResponse> generated;

    @BeforeEach
    void setup() {
        jobs = mock(AiJobService.class);
        client = mock(AiServiceClient.class);
        manager = mock(PlatformTransactionManager.class);
        transaction = mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(transaction);
        database = new ManualExecutor();
        clock = new AtomicLong();
        worker = new AiJobWorker(jobs, client, manager, database, clock::get);
        generated = new CompletableFuture<>();
        when(jobs.claimNext()).thenReturn(job());
        when(jobs.sourceForAttempt(10, 1, "req-A")).thenReturn(source());
        when(client.generateAsync(any(), any())).thenReturn(generated);
    }

    @Test
    void noJobEndsWithoutReadingSourceOrCallingClient() throws Exception {
        when(jobs.claimNext()).thenReturn(null);
        var result = worker.runOne();
        verifyNoInteractions(jobs);
        database.drain();
        assertThat(result.get(1, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.IDLE);
        verify(jobs, times(1)).claimNext();
        verifyNoInteractions(client);
        verify(manager).commit(transaction);
    }

    @Test
    void invalidSourceSkipsWithoutCallingPythonOrOverwritingState() throws Exception {
        when(jobs.sourceForAttempt(10, 1, "req-A")).thenReturn(null);
        var result = worker.runOne();
        database.drain();
        assertThat(result.get(1, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.SKIPPED);
        verifyNoInteractions(client);
        verify(manager, times(2)).commit(transaction);
        verify(jobs, never()).fail(anyLong(), anyInt(), anyString(), anyString());
    }

    @Test
    void networkCompletionOnlyQueuesDatabaseWorkAndPreservesAttemptIdentity() throws Exception {
        AiJob claimed = job();
        when(jobs.claimNext()).thenReturn(claimed);
        when(jobs.complete(10, 1, "req-A", response())).thenReturn(true);
        var result = worker.runOne();
        database.drain();
        assertThat(result).isNotDone();
        verify(manager, times(2)).commit(transaction);
        // 实体被其他代码修改，不得改变已经保存的本次尝试身份。
        claimed.setAttemptCount(2);
        claimed.setRequestId("req-B");
        generated.complete(response());
        verify(jobs, never()).complete(anyLong(), anyInt(), anyString(), any());
        verify(manager, times(2)).commit(transaction);
        database.drain();
        assertThat(result.get(1, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.SUCCEEDED);
        verify(jobs).complete(10, 1, "req-A", response());
        verify(manager, times(3)).commit(transaction);
    }

    @Test
    void conditionalCompletionFalseDoesNotFailAgain() throws Exception {
        var result = worker.runOne();
        database.drain();
        generated.complete(response());
        database.drain();
        assertThat(result.get(1, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.SKIPPED);
        verify(jobs, never()).fail(anyLong(), anyInt(), anyString(), anyString());
    }

    @Test
    void sourcePrecheckConsumesGenerationBudgetAndRequestUsesSnapshot() {
        when(jobs.sourceForAttempt(10, 1, "req-A")).thenAnswer(invocation -> {
            clock.addAndGet(Duration.ofSeconds(2).toNanos());
            return source();
        });
        worker.runOne();
        database.drain();
        verify(client).generateAsync(eq(new MoodAiRequest("req-A", "10", 1, "7", "平静", "合成记录", 40000)),
                eq(Duration.ofSeconds(48)));
    }

    @Test
    void delayAfterClaimAlsoConsumesGenerationBudget() {
        worker.runOne();
        database.pending.remove().run();
        clock.addAndGet(Duration.ofSeconds(2).toNanos());
        database.drain();
        verify(client).generateAsync(any(), eq(Duration.ofSeconds(48)));
    }

    @Test
    void expiredBeforePrecheckDoesNotReadSourceOrCallModel() throws Exception {
        when(jobs.fail(10, 1, "req-A", "provider_timeout")).thenReturn(true);
        var result = worker.runOne();
        database.pending.remove().run();
        clock.addAndGet(Duration.ofSeconds(50).toNanos());
        database.drain();

        assertThat(result.get(1, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.FAILED);
        verify(jobs, never()).sourceForAttempt(anyLong(), anyInt(), anyString());
        verifyNoInteractions(client);
        verify(jobs).fail(10, 1, "req-A", "provider_timeout");
    }

    @Test
    void completeUsesIndependentFiveSecondTransaction() {
        worker.runOne();
        database.drain();
        generated.complete(response());
        database.drain();
        verify(manager, times(3)).getTransaction(argThat(definition -> definition.getTimeout() == 5
                && definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @ParameterizedTest
    @ValueSource(strings = {"provider_timeout", "provider_error", "invalid_model_response",
            "overloaded", "unauthorized", "execution_interrupted"})
    void clientFailurePreservesClassificationWithoutSubmittingReply(String code) throws Exception {
        when(jobs.fail(10, 1, "req-A", code)).thenReturn(true);
        var result = worker.runOne();
        database.drain();
        generated.completeExceptionally(new AiServiceException(code));
        database.drain();

        assertThat(result.get(1, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.FAILED);
        verify(jobs).fail(10, 1, "req-A", code);
        verify(jobs, never()).complete(anyLong(), anyInt(), anyString(), any());
        verify(client, times(1)).generateAsync(any(), any());
    }

    @Test
    void cleanupFailureLogContainsSafeFieldsWithoutRawExceptions() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(AiJobWorker.class);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            when(jobs.complete(10, 1, "req-A", response()))
                    .thenThrow(new IllegalStateException("private-completion-error"));
            when(jobs.fail(10, 1, "req-A", "internal_error"))
                    .thenThrow(new IllegalStateException("private-cleanup-error"));
            var result = worker.runOne();
            database.drain();
            generated.complete(response());
            database.drain();

            assertThat(result.get(1, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.UNRESOLVED);
            assertThat(captured.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).contains("jobId=10", "attempt=1", "code=internal_error")
                        .doesNotContain("private-completion-error", "private-cleanup-error", REQUEST_TEXT);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    static AiJob job() {
        AiJob job = new AiJob();
        job.setId(10L);
        job.setAttemptCount(1);
        job.setRequestId("req-A");
        job.setTargetVersion(7L);
        return job;
    }

    static MoodRecord source() {
        MoodRecord source = new MoodRecord();
        source.setMoodType("平静");
        source.setContent(REQUEST_TEXT);
        return source;
    }

    static MoodAiResponse response() {
        return new MoodAiResponse("req-A", "10", 1, "7", "合成回复", "MODEL", true, "mood-reply-v1", "test-model");
    }

    static final class ManualExecutor implements Executor {
        final Queue<Runnable> pending = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            pending.add(task);
        }

        void drain() {
            while (!pending.isEmpty()) {
                pending.remove().run();
            }
        }
    }
}
