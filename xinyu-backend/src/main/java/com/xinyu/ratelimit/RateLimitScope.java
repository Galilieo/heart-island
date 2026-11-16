package com.xinyu.ratelimit;

import java.time.Duration;

/**
 * 限流范围：窗口长度、是否全站计数与键名集中在此，两种限流实现共用。
 * 额度取值见 {@link RateLimitProperties#limitFor(RateLimitScope)}，调用类型到范围的组合见 {@link RateLimitType}。
 */
public enum RateLimitScope {
    /** 每人每小时心情回复，键名出现在 Redis 计数键里。 */
    MOOD_HOUR("mood_hour", false, false),

    /** 每人每天心情回复，当地零点重置。 */
    MOOD_DAY("mood_day", false, true),

    /** 每人每小时社区 AI 回复。 */
    COMMUNITY_HOUR("community_hour", false, false),

    /** 每人每天回顾信。 */
    REVIEW_DAY("review_day", false, true),

    /** 全站每小时所有付费 AI 调用，键里的持有者固定为 global。 */
    GLOBAL_HOUR("global_hour", true, false);

    private final String keyName;
    private final boolean global;
    private final boolean dayWindow;

    RateLimitScope(String keyName, boolean global, boolean dayWindow) {
        this.keyName = keyName;
        this.global = global;
        this.dayWindow = dayWindow;
    }

    public String keyName() {
        return keyName;
    }

    public boolean isGlobal() {
        return global;
    }

    public boolean isDayWindow() {
        return dayWindow;
    }

    public Duration windowLength() {
        return dayWindow ? Duration.ofDays(1) : Duration.ofHours(1);
    }

    /**
     * 超额时的错误码后缀（契约第 6 节错误码与前端文案表）：按窗口类型与是否全站映射，
     * 与前端 MoodAiStatus.vue 的文案分支一一对应；跨端映射单点维护在此。
     */
    public String errorCode() {
        if (global) {
            return "rate_limited_global";
        }
        return dayWindow ? "rate_limited_day" : "rate_limited_hour";
    }
}
