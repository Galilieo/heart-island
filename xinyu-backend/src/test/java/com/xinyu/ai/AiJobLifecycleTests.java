package com.xinyu.ai;

import com.xinyu.dto.MoodAiResponse;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.AiReply;
import com.xinyu.entity.MoodRecord;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.mapper.AiReplyMapper;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.service.AiJobAdmission;
import com.xinyu.service.AiJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 任务编排用真实服务，Mapper 与事务管理器为替身；数据库原子性另用 MySQL IT 验证。 */
class AiJobLifecycleTests {
    AiJobMapper jobs;
    MoodRecordMapper moods;
    AiReplyMapper replies;
    PlatformTransactionManager manager;
    TransactionStatus transaction;
    AiJobAdmission admission;
    AiJobService service;

    @BeforeEach
    void setup() {
        jobs = mock(AiJobMapper.class);
        moods = mock(MoodRecordMapper.class);
        replies = mock(AiReplyMapper.class);
        manager = mock(PlatformTransactionManager.class);
        transaction = mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(transaction);
        admission = new AiJobAdmission(manager);
        service = new AiJobService(jobs, moods, replies, admission, true);
    }

    /** 构造一条属于用户 2、来源版本 7 的任务，由各用例指定状态与尝试次数。 */
    AiJob job(String status, int attempt) {
        AiJob j = new AiJob();
        j.setId(10L);
        j.setUserId(2L);
        j.setTargetId(9L);
        j.setTaskType("MOOD_REPLY");
        j.setTargetVersion(7L);
        j.setStatus(status);
        j.setAttemptCount(attempt);
        j.setRequestId("req-A");
        return j;
    }

    /** 构造一条有效、已开启 AI 回应、版本 7 的来源日记。 */
    MoodRecord source() {
        MoodRecord s = new MoodRecord();
        s.setId(9L);
        s.setUserId(2L);
        s.setStatus(1);
        s.setContentVersion(7L);
        s.setAiReplyEnabled(true);
        s.setMoodType("平静");
        s.setContent("合成内容");
        return s;
    }

    MoodAiResponse result() {
        return new MoodAiResponse("req-A", "10", 1, "7", "  合成回复。  ",
                "MODEL", true, "mood-reply-v1", "test-model");
    }

    @Test
    void workerOffNeverClaimsOrRecovers() {
        service = new AiJobService(jobs, moods, replies, admission, false);
        assertThat(service.claimNext()).isNull();
        assertThat(service.recoverInterrupted()).isZero();
        verifyNoInteractions(jobs, moods, replies, manager);
    }

    @Test
    void lateAttemptNeverReadsSourceOrWritesLog() {
        when(jobs.selectById(10L)).thenReturn(job("running", 2));
        assertThat(service.complete(10, 1, "req-A", result())).isFalse();
        assertThat(service.sourceForAttempt(10, 1, "req-A")).isNull();
        verifyNoInteractions(moods, replies);
    }

    @ParameterizedTest
    @ValueSource(strings = {"failed", "succeeded", "superseded", "cancelled", "queued"})
    void onlyCurrentRunningAttemptCanSubmit(String status) {
        when(jobs.selectById(10L)).thenReturn(job(status, 1));
        assertThat(service.complete(10, 1, "req-A", result())).isFalse();
        verifyNoInteractions(moods, replies);
    }

    @Test
    void disabledOrDeletedSourcesCannotReachGeneration() {
        when(jobs.selectById(10L)).thenReturn(job("running", 1));
        MoodRecord source = source();
        source.setStatus(0);
        when(moods.selectById(9L)).thenReturn(source);
        assertThat(service.sourceForAttempt(10, 1, "req-A")).isNull();
        verifyNoInteractions(replies);
    }

    @Test
    void readRejectsForeignAndDeletedSourcesBeforeReturningReply() {
        when(jobs.selectById(10L)).thenReturn(job("succeeded", 1));
        assertThatThrownBy(() -> service.getOwned(3, 10)).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403);
        MoodRecord source = source();
        source.setStatus(0);
        source.setAiReply("不能泄露");
        when(moods.selectById(9L)).thenReturn(source);
        assertThatThrownBy(() -> service.getOwned(2, 10)).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404);
        assertThatThrownBy(() -> service.retry(2, 10)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void retryLimitAndCapacityNeverCreateANewJob() {
        when(jobs.selectById(10L)).thenReturn(job("failed", 3));
        when(moods.selectById(9L)).thenReturn(source());
        assertThat(service.getOwned(2, 10).retryable()).isFalse();
        assertThatThrownBy(() -> service.retry(2, 10)).isInstanceOf(ResponseStatusException.class);
        when(jobs.selectById(10L)).thenReturn(job("failed", 0));
        when(jobs.selectCount(any())).thenReturn(100L);
        assertThatThrownBy(() -> service.retry(2, 10)).isInstanceOf(ResponseStatusException.class);
        verify(jobs, never()).insert(any(AiJob.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"queued", "running", "succeeded"})
    void retryOfCurrentActiveOrSuccessfulJobIsIdempotent(String state) {
        when(jobs.selectById(10L)).thenReturn(job(state, 1));
        when(moods.selectById(9L)).thenReturn(source());
        assertThat(service.retry(2, 10).status()).isEqualTo(state);
        verify(jobs, never()).insert(any(AiJob.class));
    }

    @Test
    void historyWithoutJobIsLegacyAndDisabledIsNotUnavailable() {
        MoodRecord s = source();
        s.setAiReply("历史回复");
        assertThat(service.toMoodView(s).aiReplyState()).isEqualTo("legacy");
        assertThat(service.toMoodView(s).resultKind()).isNull();
        s.setAiReply(null);
        s.setAiReplyEnabled(false);
        assertThat(service.toMoodView(s).aiReplyState()).isEqualTo("disabled");
        s.setAiReplyEnabled(true);
        assertThat(service.toMoodView(s).aiReplyState()).isEqualTo("unavailable");
        s.setStatus(0);
        assertThatThrownBy(() -> service.toMoodView(s)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void staleVersionCannotBeReadOrRetried() {
        when(jobs.selectById(10L)).thenReturn(job("failed", 1));
        MoodRecord s = source();
        s.setContentVersion(8L);
        when(moods.selectById(9L)).thenReturn(s);
        assertThatThrownBy(() -> service.retry(2, 10)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.getOwned(2, 10)).isInstanceOf(ResponseStatusException.class);
    }
}
