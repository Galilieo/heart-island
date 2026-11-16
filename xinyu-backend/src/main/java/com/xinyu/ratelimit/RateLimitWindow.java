package com.xinyu.ratelimit;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** 固定窗口的起点、键名、过期与再试秒数计算；纯函数，时钟与时区由调用方传入。 */
public final class RateLimitWindow {

    /** 小时窗口的键标签按墙上时间取到小时，例如 2026111614。 */
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("yyyyMMddHH");

    /** 自然日窗口的键标签取到天，例如 20261116。 */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 过期在窗口长度之外再加固定余量，保证计数键一定会消失（契约 2.2）。 */
    private static final Duration TTL_MARGIN = Duration.ofSeconds(60);

    private RateLimitWindow() { }

    /** 当前时刻所在的窗口起点：小时窗口按整点，自然日窗口按当地零点。 */
    public static ZonedDateTime windowStart(RateLimitScope scope, Instant now, ZoneId zone) {
        ZonedDateTime wall = now.atZone(zone);
        return scope.isDayWindow()
                ? wall.toLocalDate().atStartOfDay(zone)
                : wall.withMinute(0).withSecond(0).withNano(0);
    }

    /** 限流计数键：前缀 + rl + 范围 + 用户（全站范围固定 global）+ 窗口标签；不含任何内容文本。 */
    public static String key(RateLimitScope scope, String keyPrefix, long userId, ZonedDateTime start) {
        DateTimeFormatter label = scope.isDayWindow() ? DAY_LABEL : HOUR_LABEL;
        String owner = scope.isGlobal() ? "global" : Long.toString(userId);
        return keyPrefix + "rl:" + scope.keyName() + ":" + owner + ":" + start.format(label);
    }

    /** 距当前窗口结束的秒数，向上取整且至少 1；用作拒绝时的最早可再试提示。 */
    public static long retryAfterSeconds(RateLimitScope scope, Instant now, ZoneId zone) {
        ZonedDateTime start = windowStart(scope, now, zone);
        ZonedDateTime end = scope.isDayWindow() ? start.plusDays(1) : start.plusHours(1);
        long remaining = Duration.between(now, end).toMillis();
        long seconds = (remaining + 999) / 1000;
        return Math.max(1, seconds);
    }

    /** 计数键的过期秒数：窗口长度 + 60 秒余量。 */
    public static long ttlSeconds(RateLimitScope scope) {
        return scope.windowLength().plus(TTL_MARGIN).toSeconds();
    }
}
