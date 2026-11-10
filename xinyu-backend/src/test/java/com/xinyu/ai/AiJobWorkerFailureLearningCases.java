package com.xinyu.ai;

import com.xinyu.service.AiJobWorker;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 失败收尾的替身编排验收，须显式指定此类及测试方法运行，默认*Tests不包含它。
 * 验证事务退出顺序、条件更新未命中及收尾失败，不代替真实MySQL回滚或重试验证。
 */
class AiJobWorkerFailureLearningCases extends AiJobWorkerTests {
    @Test
    void completeFailureRollsBackBeforeNewTransactionFailureMark() throws Exception {
        TransactionStatus cleanup = mock(TransactionStatus.class);
        when(jobs.complete(10, 1, "req-A", response())).thenThrow(new IllegalStateException("private-original-error"));
        when(jobs.fail(10, 1, "req-A", "internal_error")).thenReturn(true);

        var result = worker.runOne();
        database.drain();
        // 领取和预检已提交；此处只观察后面的完成事务与失败收尾顺序。
        clearInvocations(manager, jobs);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(transaction, cleanup);
        generated.complete(response());
        database.drain();

        assertThat(result.get(1, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.FAILED);
        var order = inOrder(manager, jobs);
        order.verify(manager).getTransaction(any(TransactionDefinition.class));
        order.verify(jobs).complete(10, 1, "req-A", response());
        order.verify(manager).rollback(transaction);
        order.verify(manager).getTransaction(argThat(definition -> definition.getTimeout() == 5
                && definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        order.verify(jobs).fail(10, 1, "req-A", "internal_error");
        order.verify(manager).commit(cleanup);
        verify(client, times(1)).generateAsync(any(), any());
    }

    @Test
    void lateFailureDoesNotReportFailedWhenConditionalUpdateMisses() throws Exception {
        when(jobs.complete(10, 1, "req-A", response())).thenThrow(new IllegalStateException("private-original-error"));
        when(jobs.fail(10, 1, "req-A", "internal_error")).thenReturn(false);
        var result = worker.runOne();
        database.drain();
        generated.complete(response());
        database.drain();
        assertThat(result.get(1, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.SKIPPED);
        verify(jobs, times(1)).fail(10, 1, "req-A", "internal_error");
    }

    @Test
    void failureTransactionFailureDoesNotPretendTaskWasMarkedFailed() throws Exception {
        when(jobs.complete(10, 1, "req-A", response())).thenThrow(new IllegalStateException("private-original-error"));
        when(jobs.fail(10, 1, "req-A", "internal_error")).thenThrow(new IllegalStateException("private-cleanup-error"));
        var result = worker.runOne();
        database.drain();
        generated.complete(response());
        database.drain();
        assertThat(result.get(1, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.UNRESOLVED);
        verify(client, times(1)).generateAsync(any(), any());
    }
}
