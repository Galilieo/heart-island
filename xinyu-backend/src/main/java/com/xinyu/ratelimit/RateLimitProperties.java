package com.xinyu.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 限流配置（契约第 8 节配置项表）；全部可用环境变量覆盖（Spring 宽松绑定，如 XINYU_RATE_LIMIT_MOOD_PER_HOUR）。
 *
 * @param enabled          关闭时所有检查直接通过，用于临时摘除限流
 * @param keyPrefix        Redis 计数键前缀，按环境区分（xinyu:dev: / xinyu:prod: / 测试 xinyu:test:）
 * @param redisTimeout     单次 Redis 命令超时；超时即视为故障，本次改走本机计数
 * @param fallbackCooldown 进入本机降级后的冷却时长，期内不再尝试 Redis，避免每个请求等一次超时
 */
@ConfigurationProperties("xinyu.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        String keyPrefix,
        Duration redisTimeout,
        Duration fallbackCooldown,
        int moodPerHour,
        int moodPerDay,
        int communityPerHour,
        int reviewPerDay,
        int globalPerHour) {

    /** application.yml 未提供时的默认值，与契约额度表一致。 */
    public static RateLimitProperties defaults() {
        return new RateLimitProperties(true, "xinyu:dev:", Duration.ofMillis(200), Duration.ofSeconds(30),
                20, 100, 10, 10, 1000);
    }

    /** 范围到配置项的唯一映射；改额度只改这里与配置文件。 */
    public int limitFor(RateLimitScope scope) {
        return switch (scope) {
            case MOOD_HOUR -> moodPerHour;
            case MOOD_DAY -> moodPerDay;
            case COMMUNITY_HOUR -> communityPerHour;
            case REVIEW_DAY -> reviewPerDay;
            case GLOBAL_HOUR -> globalPerHour;
        };
    }
}
