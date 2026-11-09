package com.xinyu.ai;

import com.xinyu.entity.AiJob;
import com.xinyu.entity.MoodRecord;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.service.AiJobAdmission;
import com.xinyu.service.AiJobService;
import com.xinyu.service.MoodRecordService;
import com.xinyu.vo.MoodSaveVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实业务与事务编排配合 Mapper 替身；不把这些测试作为数据库锁竞争证据。 */
class AiJobStateTests {
    MoodRecordMapper moods;
    AiJobMapper jobs;
    PlatformTransactionManager manager;
    TransactionStatus transaction;
    MoodRecordService service;

    @BeforeEach
    void setup() {
        moods = mock(MoodRecordMapper.class);
        jobs = mock(AiJobMapper.class);
        manager = mock(PlatformTransactionManager.class);
        transaction = mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(transaction);
        AiJobAdmission admission = new AiJobAdmission(manager);
        AiJobService jobService = new AiJobService(jobs, moods, mock(com.xinyu.mapper.AiReplyMapper.class), admission, true);
        service = new MoodRecordService(moods, jobService, admission);
        when(moods.insert(any(MoodRecord.class))).thenAnswer(invocation -> {
            invocation.<MoodRecord>getArgument(0).setId(9L);
            return 1;
        });
        when(jobs.insert(any(AiJob.class))).thenAnswer(invocation -> {
            invocation.<AiJob>getArgument(0).setId(20L);
            return 1;
        });
        when(jobs.selectCount(any())).thenReturn(0L);
    }

    MoodRecord input() {
        MoodRecord value = new MoodRecord();
        value.setUserId(2L);
        value.setMoodType("平静");
        value.setContent("今天散步了");
        return value;
    }

    MoodRecord stored() {
        MoodRecord value = input();
        value.setId(9L);
        value.setStatus(1);
        value.setContentVersion(7L);
        value.setAiReplyEnabled(true);
        value.setAiReply("旧回复");
        value.setContent("修改前的内容");
        return value;
    }

    @Test
    void saveCreatesDiaryAndQueuedJobWithoutCallingModel() {
        MoodSaveVO result = service.add(input());
        assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getContentVersion()).isEqualTo(1L);
        assertThat(result.getJobId()).isEqualTo(20L);
        assertThat(result.getAiReplyState()).isEqualTo("queued");
        ArgumentCaptor<AiJob> captor = ArgumentCaptor.forClass(AiJob.class);
        verify(jobs).insert(captor.capture());
        assertThat(captor.getValue().getAttemptCount()).isZero();
        assertThat(captor.getValue().getTargetVersion()).isEqualTo(1L);
        assertThat(captor.getValue().getModelCalled()).isNull();
        verify(manager).commit(transaction);
    }

    @Test
    void disabledSaveDoesNotCreateJobOrReturnClientSuppliedReply() {
        MoodRecord value = input();
        value.setNeedAiReply(false);
        value.setAiReply("不能相信的客户端回复");
        MoodSaveVO result = service.add(value);
        assertThat(result.getAiReplyState()).isEqualTo("disabled");
        assertThat(result.getJobId()).isNull();
        ArgumentCaptor<MoodRecord> captor = ArgumentCaptor.forClass(MoodRecord.class);
        verify(moods).insert(captor.capture());
        assertThat(captor.getValue().getAiReply()).isNull();
        assertThat(captor.getValue().getAiReplyEnabled()).isFalse();
        verifyNoInteractions(jobs);
    }

    @Test
    void fullGlobalQueueStillSavesWithCapacityFailure() {
        when(jobs.selectCount(any())).thenReturn(100L);
        MoodSaveVO result = service.add(input());
        assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getAiReplyState()).isEqualTo("failed");
        ArgumentCaptor<AiJob> captor = ArgumentCaptor.forClass(AiJob.class);
        verify(jobs).insert(captor.capture());
        assertThat(captor.getValue().getLastErrorCode()).isEqualTo("capacity_exceeded");
        assertThat(captor.getValue().getAttemptCount()).isZero();
        verify(manager).commit(transaction);
    }

    @Test
    void fullUserQuotaStillSavesDiary() {
        when(jobs.selectCount(any())).thenReturn(0L, 3L);
        assertThat(service.add(input()).getAiReplyState()).isEqualTo("failed");
        verify(moods).insert(any(MoodRecord.class));
    }

    @Test
    void updateLocksSourceAndSupersedesBeforeNewVersionJob() {
        when(moods.selectOwnedForUpdate(9L, 2L)).thenReturn(stored());
        when(moods.updateContentAtVersion(9L, 2L, 7L, "平静", "今天散步了", true)).thenReturn(1);
        MoodSaveVO result = service.update(9L, 2L, input());
        assertThat(result.getContentVersion()).isEqualTo(8L);
        var order = inOrder(moods, jobs, manager);
        order.verify(moods).selectOwnedForUpdate(9L, 2L);
        order.verify(moods).updateContentAtVersion(9L, 2L, 7L, "平静", "今天散步了", true);
        order.verify(jobs).supersedeUnfinished(9L, 2L);
        order.verify(jobs).insert(any(AiJob.class));
        order.verify(manager).commit(transaction);
        verify(moods, never()).updateById(any(MoodRecord.class));
    }

    @Test
    void closingReplySupersedesButDoesNotCreateNewJob() {
        when(moods.selectOwnedForUpdate(9L, 2L)).thenReturn(stored());
        when(moods.updateContentAtVersion(anyLong(), anyLong(), anyLong(), anyString(), anyString(), eq(false))).thenReturn(1);
        MoodRecord edit = input();
        edit.setNeedAiReply(false);
        assertThat(service.update(9L, 2L, edit).getAiReplyState()).isEqualTo("disabled");
        verify(jobs).supersedeUnfinished(9L, 2L);
        verify(jobs, never()).insert(any(AiJob.class));
    }

    @Test
    void deletionLocksAndInvalidatesAllUnfinishedJobs() {
        when(moods.selectOwnedForUpdate(9L, 2L)).thenReturn(stored());
        when(moods.softDeleteAtVersion(9L, 2L, 7L)).thenReturn(1);
        assertThat(service.delete(9L, 2L)).isTrue();
        var order = inOrder(moods, jobs, manager);
        order.verify(moods).selectOwnedForUpdate(9L, 2L);
        order.verify(moods).softDeleteAtVersion(9L, 2L, 7L);
        order.verify(jobs).supersedeUnfinished(9L, 2L);
        order.verify(manager).commit(transaction);
    }

    @Test
    void repeatedDeletionOrEditCannotRestoreDeletedSource() {
        MoodRecord deleted = stored();
        deleted.setStatus(0);
        when(moods.selectOwnedForUpdate(9L, 2L)).thenReturn(deleted);
        assertThat(service.delete(9L, 2L)).isFalse();
        assertThat(service.update(9L, 2L, input())).isNull();
        verify(moods, never()).softDeleteAtVersion(anyLong(), anyLong(), anyLong());
        verify(moods, never()).updateContentAtVersion(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyBoolean());
        verifyNoInteractions(jobs);
    }

    @Test
    void jobFailureRollsBackTheWholeSave() {
        when(jobs.insert(any(AiJob.class))).thenThrow(new IllegalStateException("模拟数据库故障"));
        assertThatThrownBy(() -> service.add(input())).isInstanceOf(IllegalStateException.class);
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }

    @Test
    void lostConditionalUpdateDoesNotCreateJob() {
        when(moods.selectOwnedForUpdate(9L, 2L)).thenReturn(stored());
        assertThatThrownBy(() -> service.update(9L, 2L, input())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jobs);
        verify(manager).rollback(transaction);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\uD800", "\uDC00", "\u00A0", "\u0085"})
    void invalidContentNeverTouchesDatabase(String text) {
        MoodRecord value = input();
        value.setContent(text);
        assertThatThrownBy(() -> service.add(value)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(moods, jobs, manager);
    }

    @Test
    void exactUtf16LimitAcceptedAndOverflowRejected() {
        MoodRecord value = input();
        value.setContent("😀".repeat(250));
        assertThat(service.add(value)).isNotNull();
        clearInvocations(moods, jobs, manager);
        value.setContent("😀".repeat(251));
        assertThatThrownBy(() -> service.add(value)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(moods, jobs, manager);
    }

    @Test
    void invalidMoodNeverTouchesDatabase() {
        MoodRecord value = input();
        value.setMoodType("不是合法枚举");
        assertThatThrownBy(() -> service.add(value)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(moods, jobs, manager);
    }

    @Test
    void admissionRejectsAmbientTransactionBeforeTakingCapacityLock() {
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.add(input())).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(moods, jobs, manager);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("productMoods")
    void allProductMoodsCanBeSavedAndKeepTheirOriginalValue(String mood) {
        MoodRecord value = input();
        value.setMoodType(mood);
        value.setNeedAiReply(false);
        assertThat(service.add(value)).isNotNull();
        ArgumentCaptor<MoodRecord> captor = ArgumentCaptor.forClass(MoodRecord.class);
        verify(moods).insert(captor.capture());
        assertThat(captor.getValue().getMoodType()).isEqualTo(mood);
    }

    /** 与Python接口回归共用输入基准，避免两边各维护一份测试枚举。 */
    static String[] productMoods() throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                java.nio.file.Files.readString(java.nio.file.Path.of("../test-contracts/mood-types.json")), String[].class);
    }

}
