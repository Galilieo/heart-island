package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xinyu.entity.MoodRecord;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.vo.MoodReviewStatsVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 回顾统计口径测试：不连数据库、不调用模型，固定上海时区时钟。
 * 覆盖跨日、跨周边界、同日多条、0 条和心情次数并列的排序规则。
 */
class MoodReviewStatsServiceTests {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    /** 2026-11-13 周五 12:00（上海），所在周周一为 11 月 9 日。 */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T04:00:00Z"), SHANGHAI);

    private final MoodRecordMapper mapper = mock(MoodRecordMapper.class);
    private final MoodReviewStatsService service = new MoodReviewStatsService(mapper, CLOCK);

    private void records(MoodRecord... records) {
        when(mapper.selectList(any())).thenReturn(List.of(records));
    }

    private static MoodRecord mood(String moodType, LocalDateTime createTime) {
        MoodRecord record = new MoodRecord();
        record.setMoodType(moodType);
        record.setCreateTime(createTime);
        return record;
    }

    @Test
    void defaultWeekIsPreviousCompleteWeek() {
        records();

        MoodReviewStatsVO stats = service.getWeekStats(1L, null);

        assertThat(stats.rangeStart()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(stats.rangeEnd()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(stats.total()).isZero();
        assertThat(stats.daysWithRecords()).isZero();
        assertThat(stats.days()).hasSize(7);
        assertThat(stats.days()).allSatisfy(day -> assertThat(day.moods()).isEmpty());
        assertThat(stats.moodCounts()).isEmpty();
    }

    @Test
    void sameDayMultipleRecordsCountedAndDayDeduplicated() {
        records(
                mood("疲惫", LocalDateTime.of(2026, 11, 2, 9, 0)),
                mood("平静", LocalDateTime.of(2026, 11, 2, 18, 30)),
                mood("开心", LocalDateTime.of(2026, 11, 2, 23, 59)));

        MoodReviewStatsVO stats = service.getWeekStats(1L, null);

        assertThat(stats.total()).isEqualTo(3);
        assertThat(stats.daysWithRecords()).isEqualTo(1);
        assertThat(stats.days().get(0).date()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(stats.days().get(0).moods()).containsExactly("疲惫", "平静", "开心");
    }

    @Test
    void rangeIsLeftClosedRightOpenAtDayBoundary() {
        records(
                mood("平静", LocalDateTime.of(2026, 11, 2, 0, 0)),
                mood("开心", LocalDateTime.of(2026, 11, 8, 23, 59, 59)),
                mood("难过", LocalDateTime.of(2026, 11, 9, 0, 0)));

        MoodReviewStatsVO stats = service.getWeekStats(1L, null);

        assertThat(stats.total()).isEqualTo(2);
        assertThat(stats.daysWithRecords()).isEqualTo(2);
        assertThat(stats.days().get(0).moods()).containsExactly("平静");
        assertThat(stats.days().get(6).moods()).containsExactly("开心");
    }

    @Test
    void weekBoundaryKeepsSundayAndDropsNextMonday() {
        records(
                mood("期待", LocalDateTime.of(2026, 11, 1, 23, 59, 59)),
                mood("开心", LocalDateTime.of(2026, 11, 2, 0, 0)),
                mood("疲惫", LocalDateTime.of(2026, 11, 8, 23, 59, 59)),
                mood("焦虑", LocalDateTime.of(2026, 11, 9, 0, 0)));

        MoodReviewStatsVO stats = service.getWeekStats(1L, null);

        assertThat(stats.total()).isEqualTo(2);
        assertThat(stats.daysWithRecords()).isEqualTo(2);
        assertThat(stats.days().get(0).moods()).containsExactly("开心");
        assertThat(stats.days().get(6).moods()).containsExactly("疲惫");
    }

    @Test
    void tiesFollowFixedMoodOrder() {
        records(
                mood("疲惫", LocalDateTime.of(2026, 11, 2, 8, 0)),
                mood("疲惫", LocalDateTime.of(2026, 11, 3, 8, 0)),
                mood("焦虑", LocalDateTime.of(2026, 11, 3, 9, 0)),
                mood("焦虑", LocalDateTime.of(2026, 11, 4, 9, 0)),
                mood("平静", LocalDateTime.of(2026, 11, 4, 10, 0)),
                mood("平静", LocalDateTime.of(2026, 11, 5, 10, 0)),
                mood("开心", LocalDateTime.of(2026, 11, 5, 11, 0)),
                mood("开心", LocalDateTime.of(2026, 11, 6, 11, 0)));

        MoodReviewStatsVO stats = service.getWeekStats(1L, null);

        assertThat(stats.moodCounts())
                .extracting(MoodReviewStatsVO.MoodCount::moodType)
                .containsExactly("开心", "平静", "焦虑", "疲惫");
        assertThat(stats.moodCounts())
                .extracting(MoodReviewStatsVO.MoodCount::count)
                .containsOnly(2);
    }

    @Test
    void tieOrderUsesCanonicalMoodListAcrossMoreTypes() {
        records(
                mood("其他", LocalDateTime.of(2026, 11, 2, 8, 0)),
                mood("迷茫", LocalDateTime.of(2026, 11, 2, 9, 0)),
                mood("委屈", LocalDateTime.of(2026, 11, 2, 10, 0)),
                mood("生气", LocalDateTime.of(2026, 11, 2, 11, 0)),
                mood("难过", LocalDateTime.of(2026, 11, 2, 12, 0)),
                mood("感动", LocalDateTime.of(2026, 11, 2, 13, 0)));

        MoodReviewStatsVO stats = service.getWeekStats(1L, null);

        assertThat(stats.moodCounts())
                .extracting(MoodReviewStatsVO.MoodCount::moodType)
                .containsExactly("感动", "难过", "生气", "委屈", "迷茫", "其他");
    }

    @Test
    void explicitMondayStartIsAccepted() {
        records();

        MoodReviewStatsVO stats = service.getWeekStats(1L, "2026-10-26");

        assertThat(stats.rangeStart()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(stats.rangeEnd()).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void nonMondayStartIsRejected() {
        assertThatThrownBy(() -> service.getWeekStats(1L, "2026-11-03"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("周一");
    }

    @Test
    void futureWeekStartIsRejected() {
        assertThatThrownBy(() -> service.getWeekStats(1L, "2026-11-16"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未来");
    }

    @Test
    void malformedStartIsRejected() {
        assertThatThrownBy(() -> service.getWeekStats(1L, "2026-11-31"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("日期");
    }

    @Test
    void queryUsesZoneBasedHalfOpenBounds() {
        records();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<MoodRecord>> captor = ArgumentCaptor.forClass(QueryWrapper.class);

        service.getWeekStats(1L, null);

        org.mockito.Mockito.verify(mapper).selectList(captor.capture());
        // MyBatis-Plus 在生成 SQL 片段时才填充参数表。
        captor.getValue().getSqlSegment();
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        assertThat(params.values()).contains(
                LocalDateTime.of(2026, 11, 2, 0, 0),
                LocalDateTime.of(2026, 11, 9, 0, 0),
                1);
    }

    @Test
    void recent30DaysEndsTomorrowAndKeepsBoundaries() {
        records(
                mood("平静", LocalDateTime.of(2026, 10, 15, 0, 0)),
                mood("开心", LocalDateTime.of(2026, 11, 13, 23, 59, 59)),
                mood("疲惫", LocalDateTime.of(2026, 11, 14, 0, 0)));

        MoodReviewStatsVO stats = service.getRecent30Stats(1L);

        assertThat(stats.rangeStart()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(stats.rangeEnd()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(stats.days()).hasSize(30);
        assertThat(stats.total()).isEqualTo(2);
        assertThat(stats.daysWithRecords()).isEqualTo(2);
        assertThat(stats.days().get(0).date()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(stats.days().get(29).moods()).containsExactly("开心");
    }

    @Test
    void clockZoneDecidesTodayAtShanghaiMidnight() {
        // UTC 11 月 12 日 16:00 = 上海 11 月 13 日 00:00；若按 UTC 会错一天。
        Clock shanghaiMidnight = Clock.fixed(Instant.parse("2026-11-12T16:00:00Z"), SHANGHAI);
        MoodReviewStatsService midnightService = new MoodReviewStatsService(mapper, shanghaiMidnight);
        records();

        MoodReviewStatsVO stats = midnightService.getRecent30Stats(1L);

        assertThat(stats.rangeEnd()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(stats.days().get(29).date()).isEqualTo(LocalDate.of(2026, 11, 13));
    }

    @Test
    void unknownMoodStillCountedWithStableOrder() {
        records(
                mood("不存在的旧心情", LocalDateTime.of(2026, 11, 2, 8, 0)),
                mood("开心", LocalDateTime.of(2026, 11, 2, 9, 0)));

        MoodReviewStatsVO stats = service.getWeekStats(1L, null);

        assertThat(stats.total()).isEqualTo(2);
        assertThat(stats.moodCounts())
                .extracting(MoodReviewStatsVO.MoodCount::moodType)
                .containsExactly("开心", "不存在的旧心情");
    }
}
