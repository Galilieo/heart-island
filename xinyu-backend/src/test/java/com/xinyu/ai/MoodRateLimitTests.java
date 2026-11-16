package com.xinyu.ai;

import com.xinyu.entity.AiJob;
import com.xinyu.entity.MoodRecord;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.mapper.AiReplyMapper;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.ratelimit.RateLimiter;
import com.xinyu.ratelimit.RateLimitScope;
import com.xinyu.ratelimit.RateLimitType;
import com.xinyu.service.AiJobAdmission;
import com.xinyu.service.AiJobService;
import com.xinyu.service.MoodRecordService;
import com.xinyu.vo.MoodSaveVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 心情保存与手动重试的限流行为：记录照常保存、危机文本豁免、重试 429 不改任务。 */
class MoodRateLimitTests {

    /** 可控替身：记录调用次数并返回预设决定。 */
    private static final class ControlledRateLimiter implements RateLimiter {
        int calls;
        RateLimiter.Decision decision = Decision.allow();

        @Override
        public RateLimiter.Decision check(RateLimitType type, long userId) {
            calls++;
            return decision;
        }
    }

    MoodRecordMapper moods;
    AiJobMapper jobs;
    PlatformTransactionManager manager;
    TransactionStatus transaction;
    ControlledRateLimiter rateLimiter;
    MoodRecordService service;
    AiJobService jobService;

    @BeforeEach
    void setup() {
        moods = mock(MoodRecordMapper.class);
        jobs = mock(AiJobMapper.class);
        manager = mock(PlatformTransactionManager.class);
        transaction = mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(transaction);
        rateLimiter = new ControlledRateLimiter();
        jobService = new AiJobService(jobs, moods, mock(AiReplyMapper.class),
                new AiJobAdmission(manager), rateLimiter, true);
        service = new MoodRecordService(moods, jobService, new AiJobAdmission(manager), rateLimiter);
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

    private static MoodRecord input(String content) {
        MoodRecord value = new MoodRecord();
        value.setUserId(2L);
        value.setMoodType("平静");
        value.setContent(content);
        return value;
    }

    private MoodRecord stored(String content) {
        MoodRecord value = input(content);
        value.setId(9L);
        value.setStatus(1);
        value.setContentVersion(7L);
        value.setAiReplyEnabled(true);
        return value;
    }

    @Test
    void 超额时记录照常保存并创建failed任务() {
        rateLimiter.decision = RateLimiter.Decision.deny(RateLimitScope.MOOD_DAY, 3600);

        MoodSaveVO result = service.add(input("今天散步了"));

        assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getAiReplyState()).isEqualTo("failed");
        assertThat(result.getJobId()).isEqualTo(20L);
        ArgumentCaptor<AiJob> captor = ArgumentCaptor.forClass(AiJob.class);
        verify(jobs).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("failed");
        assertThat(captor.getValue().getAttemptCount()).isZero();
        assertThat(captor.getValue().getLastErrorCode()).isEqualTo("rate_limited_day");
        assertThat(captor.getValue().getFinishedAt()).isNotNull();
        verify(moods).insert(any(MoodRecord.class));
    }

    @Test
    void 关闭Ai的保存不消耗额度() {
        rateLimiter.decision = RateLimiter.Decision.deny(RateLimitScope.MOOD_DAY, 3600);
        MoodRecord value = input("今天散步了");
        value.setNeedAiReply(false);

        MoodSaveVO result = service.add(value);

        assertThat(result.getAiReplyState()).isEqualTo("disabled");
        assertThat(result.getJobId()).isNull();
        // 不创建任务就不发起付费调用，额度不应被消耗。
        assertThat(rateLimiter.calls).isZero();
    }

    @Test
    void 危机文本不计额度照常入队() {
        rateLimiter.decision = RateLimiter.Decision.deny(RateLimitScope.MOOD_DAY, 3600);

        MoodSaveVO result = service.add(input("我真的想自杀"));

        assertThat(result.getAiReplyState()).isEqualTo("queued");
        ArgumentCaptor<AiJob> captor = ArgumentCaptor.forClass(AiJob.class);
        verify(jobs).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("queued");
        // 豁免意味着限流组件根本不应被调用，额度不会被危机请求消耗。
        assertThat(rateLimiter.calls).isZero();
    }

    @Test
    void 无变化保存不消耗额度() {
        MoodRecord current = stored("修改前的内容");
        when(moods.selectById(9L)).thenReturn(current);

        service.update(9L, 2L, input("修改前的内容"));

        assertThat(rateLimiter.calls).isZero();
        verify(jobs, never()).insert(any(AiJob.class));
    }

    @Test
    void 手动重试超额返回429且不改任务() {
        AiJob job = new AiJob();
        job.setId(20L);
        job.setUserId(2L);
        job.setTaskType("MOOD_REPLY");
        job.setTargetId(9L);
        job.setTargetVersion(7L);
        job.setStatus("failed");
        job.setAttemptCount(1);
        when(jobs.selectById(20L)).thenReturn(job);
        when(moods.selectById(9L)).thenReturn(stored("今天散步了"));
        rateLimiter.decision = RateLimiter.Decision.deny(RateLimitScope.MOOD_HOUR, 1800);

        ResponseStatusException rejected = catchThrowableOfType(
                () -> jobService.retry(2L, 20L), ResponseStatusException.class);

        assertThat(rejected.getStatusCode().value()).isEqualTo(429);
        assertThat(rejected.getHeaders().getFirst("Retry-After")).isEqualTo("1800");
        // 只做了限流预检（一次快照读），没有进入准入事务，任务状态未被触碰。
        verify(jobs, never()).requeueAfterCooldown(any(), anyInt());
        verify(jobs, never()).selectForUpdate(anyLong());
    }

    @Test
    void 幂等状态的重试不消耗额度() {
        AiJob job = new AiJob();
        job.setId(20L);
        job.setUserId(2L);
        job.setTaskType("MOOD_REPLY");
        job.setTargetId(9L);
        job.setTargetVersion(7L);
        job.setStatus("queued");
        when(jobs.selectById(20L)).thenReturn(job);
        when(jobs.selectForUpdate(20L)).thenReturn(job);
        when(moods.selectById(9L)).thenReturn(stored("今天散步了"));
        rateLimiter.decision = RateLimiter.Decision.deny(RateLimitScope.MOOD_DAY, 3600);

        // queued 的重试按幂等返回当前视图，不重新入队也就不扣额度。
        assertThat(jobService.retry(2L, 20L).status()).isEqualTo("queued");

        assertThat(rateLimiter.calls).isZero();
    }

    @Test
    void 冷却中的重试不消耗额度() {
        AiJob job = new AiJob();
        job.setId(20L);
        job.setUserId(2L);
        job.setTaskType("MOOD_REPLY");
        job.setTargetId(9L);
        job.setTargetVersion(7L);
        job.setStatus("failed");
        // 累计 3 次失败进入 180 秒冷却，还剩 175 秒；冷却判定需要结束时间存在。
        job.setAttemptCount(3);
        job.setFinishedAt(java.time.LocalDateTime.now());
        when(jobs.selectById(20L)).thenReturn(job);
        when(jobs.selectForUpdate(20L)).thenReturn(job);
        when(moods.selectById(9L)).thenReturn(stored("今天散步了"));
        when(jobs.selectRetryAfterSeconds(any(), anyInt())).thenReturn(175);
        rateLimiter.decision = RateLimiter.Decision.deny(RateLimitScope.MOOD_DAY, 3600);

        ResponseStatusException cooling = catchThrowableOfType(
                () -> jobService.retry(2L, 20L), ResponseStatusException.class);

        // 冷却 429 由事务内的既有逻辑返回；限流预检没有扣额度。
        assertThat(cooling.getStatusCode().value()).isEqualTo(429);
        assertThat(cooling.getReason()).contains("冷却");
        assertThat(rateLimiter.calls).isZero();
    }

    @Test
    void 危机任务重试不受额度影响() {
        AiJob job = new AiJob();
        job.setId(20L);
        job.setUserId(2L);
        job.setTaskType("MOOD_REPLY");
        job.setTargetId(9L);
        job.setTargetVersion(7L);
        job.setStatus("failed");
        job.setAttemptCount(1);
        when(jobs.selectById(20L)).thenReturn(job);
        // requeue CAS 命中后回读的是 queued 视图；mock 不改原对象，单独返回。
        AiJob requeued = new AiJob();
        requeued.setId(20L);
        requeued.setUserId(2L);
        requeued.setTaskType("MOOD_REPLY");
        requeued.setTargetId(9L);
        requeued.setTargetVersion(7L);
        requeued.setStatus("queued");
        requeued.setAttemptCount(1);
        when(jobs.selectForUpdate(20L)).thenReturn(requeued);
        when(moods.selectById(9L)).thenReturn(stored("我真的想自杀"));
        when(jobs.requeueAfterCooldown(any(), anyInt())).thenReturn(1);
        rateLimiter.decision = RateLimiter.Decision.deny(RateLimitScope.MOOD_DAY, 3600);

        // 豁免后进入原流程：failed 且冷却结束，正常重新入队而不是 429。
        assertThat(jobService.retry(2L, 20L).status()).isEqualTo("queued");

        assertThat(rateLimiter.calls).isZero();
    }
}
