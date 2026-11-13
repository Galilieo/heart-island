package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xinyu.entity.MoodRecord;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.vo.MoodReviewStatsVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回顾页统计：按配置时区计算自然周或近 30 天范围，
 * 统计有效记录次数、有记录的天数、每天的心情和心情次数分布。
 * 范围一律左闭右开，只读，不产生模型调用，也不写库。
 */
@Service
public class MoodReviewStatsService {

    /** 产品心情枚举与固定排序，来源：test-contracts/mood-types.json 与前端 MOOD_OPTIONS。 */
    private static final List<String> MOOD_ORDER = List.of(
            "开心", "满足", "期待", "感动", "平静",
            "焦虑", "难过", "生气", "委屈", "孤独", "迷茫", "疲惫", "其他");

    private static final Map<String, Integer> MOOD_RANK = buildMoodRank();

    /** mood_record.status=1 表示有效，软删除写 0。 */
    private static final int STATUS_ACTIVE = 1;

    private final MoodRecordMapper moodRecordMapper;
    private final Clock reviewClock;

    public MoodReviewStatsService(MoodRecordMapper moodRecordMapper,
                                  @Qualifier("reviewClock") Clock reviewClock) {
        this.moodRecordMapper = moodRecordMapper;
        this.reviewClock = reviewClock;
    }

    /** 周统计；startDate 为空时取上一个完整自然周。 */
    public MoodReviewStatsVO getWeekStats(Long userId, String startDate) {
        LocalDate today = LocalDate.now(reviewClock);
        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate start = resolveWeekStart(startDate, currentMonday);

        return buildStats(userId, start, start.plusWeeks(1));
    }

    /** 近 30 天统计：[今天-29 天 00:00, 明天 00:00)。 */
    public MoodReviewStatsVO getRecent30Stats(Long userId) {
        LocalDate today = LocalDate.now(reviewClock);

        return buildStats(userId, today.minusDays(29), today.plusDays(1));
    }

    /**
     * 解析并校验周起始日：显式传入时必须是周一，且不能晚于本周周一。
     * 空值表示上一个完整周，即本周周一往前一周。
     */
    private static LocalDate resolveWeekStart(String startDate, LocalDate currentMonday) {
        if (startDate == null || startDate.trim().isEmpty()) {
            return currentMonday.minusWeeks(1);
        }

        LocalDate start;
        try {
            start = LocalDate.parse(startDate.trim());
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("周起始日期格式无效，应为 YYYY-MM-DD");
        }

        if (start.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("周起始日期必须是周一");
        }

        if (start.isAfter(currentMonday)) {
            throw new IllegalArgumentException("不能查看未来周");
        }

        return start;
    }

    /** 查询 [start, endExclusive) 的有效记录，并在 Java 侧按同一范围再过滤一次。 */
    private MoodReviewStatsVO buildStats(Long userId, LocalDate start, LocalDate endExclusive) {
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = endExclusive.atStartOfDay();

        QueryWrapper<MoodRecord> query = new QueryWrapper<MoodRecord>()
                .eq("user_id", userId)
                .eq("status", STATUS_ACTIVE)
                .ge("create_time", from)
                .lt("create_time", to)
                .orderByAsc("create_time");

        return aggregate(moodRecordMapper.selectList(query), start, endExclusive);
    }

    /**
     * 聚合统计：按日去重、同日多条计多次；心情次数先按次数降序，
     * 次数相同时按固定心情顺序排，未知历史心情排在已知项之后。
     */
    static MoodReviewStatsVO aggregate(List<MoodRecord> records, LocalDate start, LocalDate endExclusive) {
        Map<LocalDate, List<String>> moodsByDay = new LinkedHashMap<>();
        for (LocalDate date = start; date.isBefore(endExclusive); date = date.plusDays(1)) {
            moodsByDay.put(date, new ArrayList<>());
        }

        Map<String, Integer> moodCounts = new HashMap<>();
        int total = 0;

        if (records != null) {
            for (MoodRecord record : records) {
                if (record == null || record.getCreateTime() == null || record.getMoodType() == null) {
                    continue;
                }

                List<String> moods = moodsByDay.get(record.getCreateTime().toLocalDate());
                if (moods == null) {
                    continue;
                }

                moods.add(record.getMoodType());
                moodCounts.merge(record.getMoodType(), 1, Integer::sum);
                total++;
            }
        }

        List<MoodReviewStatsVO.Day> days = moodsByDay.entrySet().stream()
                .map(entry -> new MoodReviewStatsVO.Day(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();

        int daysWithRecords = (int) days.stream()
                .filter(day -> !day.moods().isEmpty())
                .count();

        List<MoodReviewStatsVO.MoodCount> sortedCounts = moodCounts.entrySet().stream()
                .map(entry -> new MoodReviewStatsVO.MoodCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingInt(MoodReviewStatsVO.MoodCount::count).reversed()
                        .thenComparingInt(count -> moodRank(count.moodType()))
                        .thenComparing(MoodReviewStatsVO.MoodCount::moodType))
                .toList();

        return new MoodReviewStatsVO(start, endExclusive, total, daysWithRecords, days, sortedCounts);
    }

    /** 固定心情顺序的排名；未知历史值返回最大序号，保持稳定排序。 */
    private static int moodRank(String moodType) {
        return MOOD_RANK.getOrDefault(moodType, Integer.MAX_VALUE);
    }

    private static Map<String, Integer> buildMoodRank() {
        Map<String, Integer> rank = new HashMap<>();
        for (int index = 0; index < MOOD_ORDER.size(); index++) {
            rank.put(MOOD_ORDER.get(index), index);
        }
        return Map.copyOf(rank);
    }
}
