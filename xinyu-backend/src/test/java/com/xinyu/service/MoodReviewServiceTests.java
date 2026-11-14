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
}
