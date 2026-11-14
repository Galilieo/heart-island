package com.xinyu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyu.dto.ReviewAiRequest;
import com.xinyu.dto.ReviewAiResponse;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.MoodRecord;
import com.xinyu.entity.MoodReview;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.mapper.MoodReviewMapper;
import com.xinyu.vo.MoodReviewStatsVO;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证发往 Python 的回顾请求使用中文星期且不依赖 Locale。 */
class MoodReviewServiceTests {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T04:00:00Z"), SHANGHAI);

    @Test
    void weekdayMapsAllSevenDaysToChinese() {
        assertThat(MoodReviewService.weekday(DayOfWeek.MONDAY)).isEqualTo("周一");
        assertThat(MoodReviewService.weekday(DayOfWeek.TUESDAY)).isEqualTo("周二");
        assertThat(MoodReviewService.weekday(DayOfWeek.WEDNESDAY)).isEqualTo("周三");
        assertThat(MoodReviewService.weekday(DayOfWeek.THURSDAY)).isEqualTo("周四");
        assertThat(MoodReviewService.weekday(DayOfWeek.FRIDAY)).isEqualTo("周五");
        assertThat(MoodReviewService.weekday(DayOfWeek.SATURDAY)).isEqualTo("周六");
        assertThat(MoodReviewService.weekday(DayOfWeek.SUNDAY)).isEqualTo("周日");
    }

    @Test
    void prepareEmitsChineseWeekdaysAcrossFullWeek() throws Exception {
        MoodReviewMapper reports = mock(MoodReviewMapper.class);
        AiJobMapper reviewJobs = mock(AiJobMapper.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));

        MoodReviewService service = new MoodReviewService(
                reports,
                new MoodReviewStatsService(mock(MoodRecordMapper.class), CLOCK),
                CLOCK,
                manager,
                new AiJobAdmission(manager));
        service.setReviewJobs(reviewJobs);

        List<MoodRecord> records = List.of(
                record(1L, LocalDateTime.of(2026, 11, 2, 9, 0)),
                record(2L, LocalDateTime.of(2026, 11, 3, 9, 0)),
                record(3L, LocalDateTime.of(2026, 11, 4, 9, 0)),
                record(4L, LocalDateTime.of(2026, 11, 5, 9, 0)),
                record(5L, LocalDateTime.of(2026, 11, 6, 9, 0)),
                record(6L, LocalDateTime.of(2026, 11, 7, 9, 0)),
                record(7L, LocalDateTime.of(2026, 11, 8, 9, 0)));

        LocalDate start = LocalDate.of(2026, 11, 2);
        LocalDate end = LocalDate.of(2026, 11, 9);
        MoodReviewStatsVO snapshot = MoodReviewStatsService.aggregate(records, start, end);
        String statsJson = new ObjectMapper().findAndRegisterModules().writeValueAsString(snapshot);
        String fingerprint = MoodReviewService.fingerprint(records);

        AiJob job = new AiJob();
        job.setId(10L);
        job.setUserId(1L);
        job.setTargetId(20L);
        job.setTaskType(MoodReviewService.TASK_TYPE);
        job.setTargetVersion(1L);
        job.setStatus("running");
        job.setAttemptCount(1);
        job.setRequestId("req-weekdays");

        MoodReview review = new MoodReview(
                20L, 1L, "WEEK", start, end, 1, fingerprint, "running", statsJson, null, null, null, null);

        when(reviewJobs.selectById(10L)).thenReturn(job);
        when(reviewJobs.selectForUpdate(10L)).thenReturn(job);
        when(reports.load(20L)).thenReturn(review);
        when(reports.sources(1L, start, end, true)).thenReturn(records);

        MoodReviewService.Prepared prepared = service.prepare(10L, 1, "req-weekdays");

        assertThat(prepared).isNotNull();
        assertThat(prepared.request().records())
                .extracting(ReviewAiRequest.Entry::weekday)
                .containsExactly("周一", "周二", "周三", "周四", "周五", "周六", "周日");
    }

    private static MoodRecord record(long id, LocalDateTime createTime) {
        MoodRecord record = new MoodRecord();
        record.setId(id);
        record.setUserId(1L);
        record.setMoodType("平静");
        record.setContent("日常记录" + id);
        record.setAiReplyEnabled(true);
        record.setContentVersion(1L);
        record.setCreateTime(createTime);
        record.setStatus(1);
        return record;
    }

    @Test
    void allocateBudgetsCoversAllShortOneLongAndAllLong() {
        assertThat(MoodReviewService.allocateBudgets(List.of(10, 200, 1000), 12000))
                .containsExactly(10, 200, 1000);

        List<Integer> mixed = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) mixed.add(100);
        for (int i = 0; i < 10; i++) mixed.add(900);
        for (int i = 0; i < 5; i++) mixed.add(1000);
        List<Integer> expectedMixed = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) expectedMixed.add(100);
        for (int i = 0; i < 10; i++) expectedMixed.add(733);
        for (int i = 0; i < 5; i++) expectedMixed.add(734);
        assertThat(MoodReviewService.allocateBudgets(mixed, 12000)).isEqualTo(expectedMixed);

        List<Integer> allLong = java.util.Collections.nCopies(60, 1000);
        assertThat(MoodReviewService.allocateBudgets(allLong, 12000))
                .isEqualTo(java.util.Collections.nCopies(60, 200));
    }

    @Test
    void clipPreservesHeadTailWithMarkerAndAvoidsSplittingSurrogates() {
        String text = "甲".repeat(8) + "😀" + "中".repeat(20) + "😀" + "乙".repeat(4);
        String clipped = MoodReviewService.clip(text, 20);
        assertThat(clipped).isEqualTo("甲".repeat(8) + "〔中间省略〕" + "乙".repeat(4));
        assertThat(clipped.length()).isLessThanOrEqualTo(20);
    }

    @Test
    void responseValidateEnforcesNewBodyHardLimits() {
        var entry = new ReviewAiRequest.Entry("r1", "2026-11-02", "周一", "平静", "散步");
        var stats = new MoodReviewStatsVO(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9), 1, 1, List.of(), List.of());
        var weekReq = new ReviewAiRequest("req-1", "10", 1, "1", "WEEK", List.of(entry), 1, stats, 40000);
        var recentReq = new ReviewAiRequest("req-1", "10", 1, "1", "RECENT_30", List.of(entry), 1, stats, 40000);

        new ReviewAiResponse("req-1", "10", 1, "1", "文".repeat(500), null, "MODEL", true, "mood-review-v1", "m").validate(weekReq);
        assertThatThrownBy(() -> new ReviewAiResponse("req-1", "10", 1, "1", "文".repeat(501), null, "MODEL", true, "mood-review-v1", "m").validate(weekReq))
                .isInstanceOf(IllegalArgumentException.class);

        new ReviewAiResponse("req-1", "10", 1, "1", "文".repeat(600), null, "MODEL", true, "mood-review-v1", "m").validate(recentReq);
        assertThatThrownBy(() -> new ReviewAiResponse("req-1", "10", 1, "1", "文".repeat(601), null, "MODEL", true, "mood-review-v1", "m").validate(recentReq))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getReconstructsCitedSourcesInRefOrderAndExposesLatestFailedAttempt() {
        MoodReviewMapper reports = mock(MoodReviewMapper.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));

        MoodRecordMapper recordMapper = mock(MoodRecordMapper.class);
        MoodReviewService service = new MoodReviewService(
                reports,
                new MoodReviewStatsService(recordMapper, CLOCK),
                CLOCK,
                manager,
                new AiJobAdmission(manager));

        LocalDate start = LocalDate.of(2026, 11, 2);
        LocalDate end = LocalDate.of(2026, 11, 9);
        MoodRecord first = record(101L, LocalDateTime.of(2026, 11, 2, 10, 0));
        first.setContent("第一条记录完整全文".repeat(60));
        MoodRecord second = record(102L, LocalDateTime.of(2026, 11, 4, 15, 0));
        second.setContent("第二条未被引用的记录");
        MoodRecord third = record(103L, LocalDateTime.of(2026, 11, 6, 20, 0));
        third.setMoodType("开心");
        third.setContent("第三条记录全文");
        MoodRecord disabled = record(104L, LocalDateTime.of(2026, 11, 7, 9, 0));
        disabled.setAiReplyEnabled(false);

        List<MoodRecord> allRecords = List.of(first, second, third, disabled);
        MoodReviewStatsVO snapshot = MoodReviewStatsService.aggregate(allRecords, start, end);
        String statsJson;
        try {
            statsJson = new ObjectMapper().findAndRegisterModules()
                    .writeValueAsString(snapshot)
                    .replace("}", ",\"aiEnabledTotal\":3}");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        String fingerprint = MoodReviewService.fingerprint(allRecords);

        MoodReview succeeded = new MoodReview(
                20L, 1L, "WEEK", start, end, 1, fingerprint, "succeeded", statsJson,
                "周初提到{{r3}}，还有{{r1}}。", null, "MODEL", "2026-11-09 08:00:00");
        MoodReview failed = new MoodReview(
                21L, 1L, "WEEK", start, end, 2, fingerprint, "failed", statsJson,
                null, null, null, null);

        when(recordMapper.selectList(any())).thenReturn(allRecords);
        when(reports.latest(1L, "WEEK", start, false)).thenReturn(succeeded);
        when(reports.latest(1L, "WEEK", start, true)).thenReturn(null);
        when(reports.latestAttempt(1L, "WEEK", start)).thenReturn(failed);
        when(reports.sources(1L, start, end, false)).thenReturn(allRecords);
        when(reports.deletedSourceCount(1L, 20L)).thenReturn(0);
        when(reports.deletedSourceCount(1L, 21L)).thenReturn(0);
        when(reports.jobId(20L, 1)).thenReturn(30L);
        when(reports.jobId(21L, 2)).thenReturn(31L);
        when(reports.jobStatus(21L, 2)).thenReturn("failed");
        when(reports.sentSources(1L, 20L)).thenReturn(List.of(first, second, third));

        MoodReviewService.Page page = service.get(1L, "WEEK", "2026-11-02");

        assertThat(page.aiEnabledCount()).isEqualTo(3);
        assertThat(page.failed()).isNotNull();
        assertThat(page.failed().jobId()).isEqualTo(31L);
        assertThat(page.report().sources()).containsExactly(
                new MoodReviewService.Source("r1", 101L, LocalDate.of(2026, 11, 2), "周一", "平静", first.getContent()),
                new MoodReviewService.Source("r3", 103L, LocalDate.of(2026, 11, 6), "周五", "开心", "第三条记录全文"));
    }

    @Test
    void reportSerializationIncludesSourcesField() throws Exception {
        var source = new MoodReviewService.Source("r1", 101L, LocalDate.of(2026, 11, 2), "周一", "平静", "散步");
        var report = new MoodReviewService.Report(
                20L, 1, "succeeded", "散步{{r1}}", null, "MODEL", false, 30L,
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9), 1, "2026-11-09 08:00:00", List.of(source));

        String serialized = new ObjectMapper().findAndRegisterModules().writeValueAsString(report);
        assertThat(serialized).contains("\"sources\":[", "\"ref\":\"r1\"", "\"recordId\":101");
    }

    @Test
    void historyUsesPageSizeFiveForBothLimitAndOffset() {
        MoodReviewMapper reports = mock(MoodReviewMapper.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        MoodReviewService service = new MoodReviewService(
                reports,
                new MoodReviewStatsService(mock(MoodRecordMapper.class), CLOCK),
                CLOCK,
                manager,
                new AiJobAdmission(manager));
        when(reports.history(1L, 5, 0)).thenReturn(List.of());
        when(reports.history(1L, 5, 5)).thenReturn(List.of());

        assertThat(service.history(1L, 1)).isEmpty();
        assertThat(service.history(1L, 2)).isEmpty();
        org.mockito.Mockito.verify(reports).history(1L, 5, 0);
        org.mockito.Mockito.verify(reports).history(1L, 5, 5);
    }
}
