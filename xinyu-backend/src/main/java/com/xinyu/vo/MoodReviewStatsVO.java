package com.xinyu.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 回顾统计快照：范围、总次数、有记录的天数、每天的心情和心情次数分布。
 * 范围为左闭右开 [rangeStart, rangeEnd)，日期按配置时区计算。
 */
public record MoodReviewStatsVO(
        LocalDate rangeStart,
        LocalDate rangeEnd,
        int total,
        int daysWithRecords,
        List<Day> days,
        List<MoodCount> moodCounts) {

    /** 一天的心情列表，按记录创建时间升序；没有记录时为空列表。 */
    public record Day(LocalDate date, List<String> moods) {
    }

    /** 心情次数；次数相同时由 Service 按固定心情顺序排列。 */
    public record MoodCount(String moodType, int count) {
    }
}
