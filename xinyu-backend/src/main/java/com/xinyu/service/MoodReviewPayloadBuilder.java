package com.xinyu.service;

import com.xinyu.dto.ReviewAiRequest;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.MoodRecord;
import com.xinyu.entity.MoodReview;
import com.xinyu.vo.MoodReviewStatsVO;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 回顾模型请求构造、等距抽样、水位配额与 UTF-16 安全截断助手。
 * 所有阈值与 Python {@code xinyu-ai/app/reviews.py} 及 {@code evals/run_review_eval.py} 保持同值。
 */
final class MoodReviewPayloadBuilder {

    /** 阶段 2 实施方案第 5.3 节：最多 60 条、正文合计 12000 UTF-16 单元、Python 预算 40 秒。 */
    private static final int MAX_SENT_RECORDS = 60;
    private static final int MAX_TOTAL_CONTENT_UTF16 = 12_000;
    private static final String TRUNCATION_MARKER = "〔中间省略〕";
    private static final int PROVIDER_BUDGET_MS = 40_000;

    /** 与 Python 回顾提示词示例约定一致的中文星期映射，不依赖系统 Locale。 */
    private static final Map<DayOfWeek, String> WEEKDAYS = Map.of(
            DayOfWeek.MONDAY, "周一",
            DayOfWeek.TUESDAY, "周二",
            DayOfWeek.WEDNESDAY, "周三",
            DayOfWeek.THURSDAY, "周四",
            DayOfWeek.FRIDAY, "周五",
            DayOfWeek.SATURDAY, "周六",
            DayOfWeek.SUNDAY, "周日");

    private MoodReviewPayloadBuilder() {
    }

    static ReviewAiRequest buildRequest(MoodReview row, AiJob job, List<MoodRecord> records,
                                        MoodReviewStatsVO stats, boolean crisis) {
        List<ReviewAiRequest.Entry> entries = new ArrayList<>();
        if (!crisis) {
            List<MoodRecord> selected = sampled(records);
            List<Integer> lengths = selected.stream().map(record -> record.getContent().length()).toList();
            List<Integer> budgets = allocateBudgets(lengths, MAX_TOTAL_CONTENT_UTF16);
            for (int index = 0; index < selected.size(); index++) {
                MoodRecord record = selected.get(index);
                String content = clip(record.getContent(), budgets.get(index));
                entries.add(new ReviewAiRequest.Entry(
                        "r" + (index + 1),
                        record.getCreateTime().toLocalDate().toString(),
                        weekday(record.getCreateTime().getDayOfWeek()),
                        record.getMoodType(),
                        content));
            }
        }
        int enabled = (int) records.stream().filter(record -> Boolean.TRUE.equals(record.getAiReplyEnabled())).count();
        return new ReviewAiRequest(
                job.getRequestId(),
                job.getId().toString(),
                job.getAttemptCount(),
                job.getTargetVersion().toString(),
                row.type(),
                List.copyOf(entries),
                enabled,
                stats,
                PROVIDER_BUDGET_MS);
    }

    static String weekday(DayOfWeek day) {
        return WEEKDAYS.get(day);
    }

    /** 阶段 2 第 5.3 节：最多 60 条授权记录，超过时等距取样，含首尾。 */
    static List<MoodRecord> sampled(List<MoodRecord> records) {
        List<MoodRecord> enabled = records.stream()
                .filter(record -> Boolean.TRUE.equals(record.getAiReplyEnabled()))
                .toList();
        if (enabled.size() <= MAX_SENT_RECORDS) {
            return enabled;
        }
        List<MoodRecord> sample = new ArrayList<>();
        for (int index = 0; index < MAX_SENT_RECORDS; index++) {
            sample.add(enabled.get(index * (enabled.size() - 1) / (MAX_SENT_RECORDS - 1)));
        }
        return sample;
    }

    /**
     * 水位分配（与 {@code evals/run_review_eval.py} 保持零偏差）：
     * 按 (UTF-16 长度, 原下标) 稳定升序逐条分派 floor(剩余预算 / 剩余条数)，短记录留余量顺延。
     * 在最多 60 条且总预算 12000 下，单条最小份额 >= 12000/60 = 200 > 标记长度 6，不会出现 <= 6 的份额。
     */
    static List<Integer> allocateBudgets(List<Integer> lengths, int totalBudget) {
        int count = lengths.size();
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.<Integer>comparingInt(lengths::get).thenComparingInt(i -> i));

        int[] allocated = new int[count];
        int remainingBudget = totalBudget;
        for (int rank = 0; rank < count; rank++) {
            int idx = order[rank];
            int share = remainingBudget / (count - rank);
            int granted = Math.min(lengths.get(idx), share);
            allocated[idx] = granted;
            remainingBudget -= granted;
        }

        List<Integer> result = new ArrayList<>(count);
        for (int value : allocated) {
            result.add(value);
        }
        return result;
    }

    /** 保留首尾（前约 2/3 + 标记 + 后约 1/3，标记计入份额），两端都不切断 UTF-16 代理对。 */
    static String clip(String content, int budget) {
        if (content.length() <= budget) {
            return content;
        }
        if (budget <= TRUNCATION_MARKER.length()) {
            return TRUNCATION_MARKER.substring(0, Math.max(0, budget));
        }

        int keep = budget - TRUNCATION_MARKER.length();
        int headLimit = (keep * 2) / 3;
        int tailLimit = keep - headLimit;
        int headEnd = (headLimit > 0 && Character.isHighSurrogate(content.charAt(headLimit - 1)))
                ? headLimit - 1
                : headLimit;
        int tailStart = content.length() - tailLimit;
        if (tailStart < content.length() && Character.isLowSurrogate(content.charAt(tailStart))) {
            tailStart++;
        }
        return content.substring(0, headEnd) + TRUNCATION_MARKER + content.substring(tailStart);
    }
}
