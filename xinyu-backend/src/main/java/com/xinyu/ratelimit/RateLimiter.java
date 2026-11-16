package com.xinyu.ratelimit;

/**
 * 限流检查结果。拒绝时给出触发范围与最早可再试秒数；通过时不携带范围信息。
 * 触发范围用于区分错误码（rate_limited_hour / rate_limited_day / rate_limited_global）与拒绝日志。
 */
public interface RateLimiter {

    /**
     * 检查并扣减一次额度：所有相关范围都未满才一起加一，任一超额则全部不加。
     *
     * @param type   调用类型，决定受哪些范围约束
     * @param userId 当前用户；全站范围忽略该值
     * @return 通过或“拒绝 + 触发范围 + 再试秒数”
     */
    Decision check(RateLimitType type, long userId);

    record Decision(boolean allowed, RateLimitScope blockedScope, int retryAfterSeconds) {

        public static Decision allow() {
            return new Decision(true, null, 0);
        }

        public static Decision deny(RateLimitScope blockedScope, int retryAfterSeconds) {
            return new Decision(false, blockedScope, retryAfterSeconds);
        }
    }
}
