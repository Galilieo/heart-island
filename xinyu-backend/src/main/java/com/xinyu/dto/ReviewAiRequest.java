package com.xinyu.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xinyu.vo.MoodReviewStatsVO;
import java.util.List;

/** 回顾内部协议：只含授权且抽样后的记录，使用 rN 别名而非数据库ID。 */
public record ReviewAiRequest(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("job_id") String jobId,
        int attempt,
        @JsonProperty("source_version") String sourceVersion,
        @JsonProperty("review_type") String reviewType,
        List<Entry> records,
        @JsonProperty("ai_enabled_total") int aiEnabledTotal,
        MoodReviewStatsVO stats,
        @JsonProperty("budget_ms") int budgetMs) {
    public record Entry(String ref, String date, String weekday,
                        @JsonProperty("mood_type") String moodType, String content) { }
}
