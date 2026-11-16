package com.xinyu.ratelimit;

import java.util.List;

/**
 * 调用到限流范围的规则表（契约 2.1 额度表）：一次检查同时受列出的所有范围约束。
 * 额度数值本身在 {@link RateLimitProperties#limitFor(RateLimitScope)}，改额度只动那一处和配置。
 */
public enum RateLimitType {
    /** 心情记录保存创建任务、手动重试。 */
    MOOD_REPLY(List.of(RateLimitScope.MOOD_HOUR, RateLimitScope.MOOD_DAY, RateLimitScope.GLOBAL_HOUR)),

    /** 社区发帖、编辑时的同步生成。 */
    COMMUNITY_POST(List.of(RateLimitScope.COMMUNITY_HOUR, RateLimitScope.GLOBAL_HOUR)),

    /** 回顾信生成与重试。 */
    REVIEW(List.of(RateLimitScope.REVIEW_DAY, RateLimitScope.GLOBAL_HOUR));

    private final List<RateLimitScope> scopes;

    RateLimitType(List<RateLimitScope> scopes) {
        this.scopes = scopes;
    }

    public List<RateLimitScope> scopes() {
        return scopes;
    }
}
