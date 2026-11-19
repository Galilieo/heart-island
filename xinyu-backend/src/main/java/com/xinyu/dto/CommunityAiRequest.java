package com.xinyu.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 社区同步生成只向 Python 传递生成所需的数据，不传业务对象标识。 */
public record CommunityAiRequest(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("topic_name") String topicName,
        @JsonProperty("mood_type") String moodType,
        String content,
        @JsonProperty("budget_ms") int budgetMs) {

    /** 社区内部接口预算上限，见 1a 契约第 6 节。 */
    public static final int MAX_BUDGET_MS = 15_000;

    /** 在 DNS 等待之后，以本次剩余时间重新计算发送预算。 */
    public CommunityAiRequest withBudget(int remainingBudgetMs) {
        return new CommunityAiRequest(requestId, topicName, moodType, content, remainingBudgetMs);
    }
}
