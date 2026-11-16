package com.xinyu.service;

import com.xinyu.entity.AiJob;
import com.xinyu.entity.MoodRecord;
import com.xinyu.entity.MoodReview;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.mapper.MoodReviewMapper;
import com.xinyu.ratelimit.RateLimiter;
import com.xinyu.ratelimit.RateLimitScope;
import com.xinyu.ratelimit.RateLimitType;
import com.xinyu.vo.MoodReviewStatsVO;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 回顾生成与重试的限流行为：429 错误码、Retry-After、危机来源豁免与不创建任务。 */
class MoodReviewRateLimitTests {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final LocalDate START = LocalDate.of(2026, 11, 2);
    private static final LocalDate END = LocalDate.of(2026, 11, 9);

    /** 可控替身：返回预设决定并记录调用。 */
    private static final class ControlledRateLimiter implements RateLimiter {
        int calls;
        RateLimiter.Decision decision = Decision.allow();

        @Override
        public RateLimiter.Decision check(RateLimitType type, long userId) {
            calls++;
            return decision;
        }
    }

    @Test
    void 生成超额返回429不创建任务() {
        MoodReviewMapper reports = mock(MoodReviewMapper.class);
        MoodReviewStatsService stats = mockSnapshot(List.of(record("普通记录")));
        ControlledRateLimiter rateLimiter = new ControlledRateLimiter();
        rateLimiter.decision = RateLimiter.Decision.deny(RateLimitScope.REVIEW_DAY, 7200);
        when(reports.sources(1L, START, END, false)).thenReturn(List.of(record("普通记录")));
        MoodReviewService service = service(reports, stats, rateLimiter);

        ResponseStatusException rejected = catchThrowableOfType(
                () -> service.generate(1L, "WEEK", null, false), ResponseStatusException.class);

        assertThat(rejected.getStatusCode().value()).isEqualTo(429);
        assertThat(rejected.getReason()).isEqualTo("review_rate_limited");
        assertThat(rejected.getHeaders().getFirst("Retry-After")).isEqualTo("7200");
        // 限流在准入事务外生效：用户锁、报告插入都不该发生。
        verify(reports, never()).lockUser(anyLong());
        verify(reports, never()).insert(anyLong(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void 来源含危机记录时豁免并进入原流程() {
        MoodReviewMapper reports = mock(MoodReviewMapper.class);
        MoodReviewStatsService stats = mockSnapshot(List.of(record("我真的想自杀")));
        ControlledRateLimiter rateLimiter = new ControlledRateLimiter();
        rateLimiter.decision = RateLimiter.Decision.deny(RateLimitScope.REVIEW_DAY, 7200);
        when(reports.sources(1L, START, END, false)).thenReturn(List.of(record("我真的想自杀")));
        MoodReviewService service = service(reports, stats, rateLimiter);
        // 进入准入事务后先锁用户；让锁返回 false 使流程在已知位置停止。
        when(reports.lockUser(1L)).thenReturn(false);

        ResponseStatusException stopped = catchThrowableOfType(
                () -> service.generate(1L, "WEEK", null, false), ResponseStatusException.class);

        // 走到了用户锁说明危机豁免放行进入原流程；额度未被消耗。
        assertThat(stopped.getStatusCode().value()).isEqualTo(404);
        assertThat(rateLimiter.calls).isZero();
    }

    @Test
    void 指纹未变的幂等返回不扣额度() {
        // 已有同指纹成功报告且非 regenerate：直接返回旧报告，不创建任务也不扣额度。
        MoodReviewMapper reports = mock(MoodReviewMapper.class);
        java.util.List<MoodRecord> records = java.util.List.of(record("普通记录"));
        MoodReviewStatsService stats = mockSnapshot(records);
        ControlledRateLimiter rateLimiter = new ControlledRateLimiter();
        rateLimiter.decision = RateLimiter.Decision.deny(RateLimitScope.REVIEW_DAY, 7200);
        MoodReview succeeded = new MoodReview(30L, 1L, "WEEK", START, END, 1,
                MoodReviewService.fingerprint(records), "succeeded", "{}", "旧报告正文", null, "MODEL", null);
        when(reports.lockUser(1L)).thenReturn(true);
        when(reports.latest(1L, "WEEK", START, true)).thenReturn(null);
        when(reports.latest(1L, "WEEK", START, false)).thenReturn(succeeded);
        when(reports.deletedSourceCount(1L, 30L)).thenReturn(0);
        when(reports.jobId(30L, 1)).thenReturn(10L);
        when(reports.sources(1L, START, END, false)).thenReturn(records);
        when(reports.sources(1L, START, END, true)).thenReturn(records);
        MoodReviewService service = service(reports, stats, rateLimiter);

        MoodReviewService.Report report = service.generate(1L, "WEEK", null, false);

        assertThat(report.id()).isEqualTo(30L);
        assertThat(rateLimiter.calls).isZero();
    }

    @Test
    void 已有进行中任务时不扣额度() {
        MoodReviewMapper reports = mock(MoodReviewMapper.class);
        java.util.List<MoodRecord> records = java.util.List.of(record("普通记录"));
        MoodReviewStatsService stats = mockSnapshot(records);
        ControlledRateLimiter rateLimiter = new ControlledRateLimiter();
        rateLimiter.decision = RateLimiter.Decision.deny(RateLimitScope.REVIEW_DAY, 7200);
        MoodReview active = new MoodReview(31L, 1L, "WEEK", START, END, 2,
                MoodReviewService.fingerprint(records), "queued", "{}", null, null, null, null);
        when(reports.lockUser(1L)).thenReturn(true);
        when(reports.latest(1L, "WEEK", START, true)).thenReturn(active);
        when(reports.deletedSourceCount(1L, 31L)).thenReturn(0);
        when(reports.sources(1L, START, END, false)).thenReturn(records);
        when(reports.sources(1L, START, END, true)).thenReturn(records);
        MoodReviewService service = service(reports, stats, rateLimiter);

        MoodReviewService.Report report = service.generate(1L, "WEEK", null, false);

        assertThat(report.id()).isEqualTo(31L);
        assertThat(rateLimiter.calls).isZero();
    }

    @Test
    void 本周未结束或无记录时不扣额度() {
        MoodReviewMapper reports = mock(MoodReviewMapper.class);
        ControlledRateLimiter rateLimiter = new ControlledRateLimiter();
        rateLimiter.decision = RateLimiter.Decision.deny(RateLimitScope.REVIEW_DAY, 7200);

        // 本周未结束：statistics 返回一个 rangeEnd 在明天之后的周窗口。
        MoodReviewStatsVO unfinished = new MoodReviewStatsVO(
                START, LocalDate.now(Clock.fixed(Instant.parse("2026-11-13T04:00:00Z"), SHANGHAI)).plusDays(1),
                0, 0, null, null);
        MoodReviewStatsService stats = mock(MoodReviewStatsService.class);
        when(stats.getWeekStats(1L, null)).thenReturn(unfinished);
        when(reports.lockUser(1L)).thenReturn(true);
        MoodReviewService service = service(reports, stats, rateLimiter);

        ResponseStatusException week = catchThrowableOfType(
                () -> service.generate(1L, "WEEK", null, false), ResponseStatusException.class);
        assertThat(week.getStatusCode().value()).isEqualTo(400);
        assertThat(week.getReason()).isEqualTo("week_not_finished");

        // 无记录：sources 返回空集合（Mockito 集合默认值），同样不扣。
        when(reports.sources(eq(1L), any(), any(), eq(false))).thenReturn(java.util.List.of());
        MoodReviewStatsVO empty = new MoodReviewStatsVO(START, END, 0, 0, null, null);
        when(stats.getWeekStats(1L, null)).thenReturn(empty);
        ResponseStatusException none = catchThrowableOfType(
                () -> service.generate(1L, "WEEK", null, false), ResponseStatusException.class);
        assertThat(none.getStatusCode().value()).isEqualTo(400);
        assertThat(none.getReason()).isEqualTo("no_records");

        assertThat(rateLimiter.calls).isZero();
    }

    @Test
    void 重试超额返回429不改任务() {
        MoodReviewMapper reports = mock(MoodReviewMapper.class);
        AiJobMapper reviewJobs = mock(AiJobMapper.class);
        ControlledRateLimiter rateLimiter = new ControlledRateLimiter();
        rateLimiter.decision = RateLimiter.Decision.deny(RateLimitScope.REVIEW_DAY, 7200);

        AiJob job = new AiJob();
        job.setId(10L);
        job.setUserId(1L);
        job.setTaskType(MoodReviewService.TASK_TYPE);
        job.setTargetId(20L);
        job.setTargetVersion(1L);
        job.setStatus("failed");
        job.setAttemptCount(1);
        when(reviewJobs.selectById(10L)).thenReturn(job);
        MoodReview review = new MoodReview(20L, 1L, "WEEK", START, END, 1,
                MoodReviewService.fingerprint(List.of(record("普通记录"))), "failed", "{}", null, null, null, null);
        when(reports.load(20L)).thenReturn(review);
        when(reports.sources(1L, START, END, false)).thenReturn(List.of(record("普通记录")));

        MoodReviewService service = new MoodReviewService(reports,
                mockSnapshot(List.of(record("普通记录"))), Clock.fixed(Instant.parse("2026-11-13T04:00:00Z"), SHANGHAI),
                mockTxManager(), new AiJobAdmission(mockTxManager()), rateLimiter);
        service.setReviewJobs(reviewJobs);

        ResponseStatusException rejected = catchThrowableOfType(
                () -> service.retry(1L, 10L), ResponseStatusException.class);

        assertThat(rejected.getStatusCode().value()).isEqualTo(429);
        assertThat(rejected.getReason()).isEqualTo("review_rate_limited");
        verify(reviewJobs, never()).requeueReviewAfterCooldown(any(), anyInt());
    }

    /** 组装被测服务；snapshot 固定为 2026-11-02 ～ 2026-11-09 的周窗口。 */
    private static MoodReviewService service(MoodReviewMapper reports, MoodReviewStatsService stats,
                                             RateLimiter rateLimiter) {
        return new MoodReviewService(reports, stats,
                Clock.fixed(Instant.parse("2026-11-13T04:00:00Z"), SHANGHAI),
                mockTxManager(), new AiJobAdmission(mockTxManager()), rateLimiter);
    }

    private static MoodReviewStatsService mockSnapshot(List<MoodRecord> records) {
        MoodReviewStatsService stats = mock(MoodReviewStatsService.class);
        MoodReviewStatsVO snapshot = MoodReviewStatsService.aggregate(records, START, END);
        when(stats.getWeekStats(1L, null)).thenReturn(snapshot);
        return stats;
    }

    private static MoodRecord record(String content) {
        MoodRecord value = new MoodRecord();
        value.setId(1L);
        value.setUserId(1L);
        value.setMoodType("平静");
        value.setContent(content);
        value.setStatus(1);
        value.setContentVersion(1L);
        value.setAiReplyEnabled(true);
        value.setCreateTime(START.atTime(9, 0));
        return value;
    }

    private static PlatformTransactionManager mockTxManager() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        return manager;
    }
}
