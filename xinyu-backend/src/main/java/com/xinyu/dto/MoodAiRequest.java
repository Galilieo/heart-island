package com.xinyu.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 内部单次生成输入；来源归属和版本由 Java 业务层确定，不接受前端指定。 */
public record MoodAiRequest(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("job_id") String jobId,
        int attempt,
        @JsonProperty("source_version") String sourceVersion,
        @JsonProperty("mood_type") String moodType,
        String content,
        @JsonProperty("budget_ms") int budgetMs) {
}
