package com.xinyu.ratelimit;

/** 测试替身：所有检查直接通过，用于不关心限流的既有用例，保持原有行为语义。 */
public final class AlwaysAllowRateLimiter implements RateLimiter {

    @Override
    public Decision check(RateLimitType type, long userId) {
        return Decision.allow();
    }
}
