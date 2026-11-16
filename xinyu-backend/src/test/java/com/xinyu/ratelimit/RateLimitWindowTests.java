package com.xinyu.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 固定窗口的起点、键名、过期与再试秒数纯函数；时区固定 Asia/Shanghai（契约 2.1）。 */
class RateLimitWindowTests {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void 小时窗口按当地整点对齐并生成到小时的键标签() {
        Instant now = Instant.parse("2026-11-16T06:30:00Z");

        ZonedDateTime start = RateLimitWindow.windowStart(RateLimitScope.MOOD_HOUR, now, SHANGHAI);

        assertThat(start).isEqualTo(ZonedDateTime.of(2026, 11, 16, 14, 0, 0, 0, SHANGHAI));
        String key = RateLimitWindow.key(RateLimitScope.MOOD_HOUR, "xinyu:dev:", 42, start);
        assertThat(key).isEqualTo("xinyu:dev:rl:mood_hour:42:2026111614");
    }

    @Test
    void 自然日窗口按当地零点对齐并生成到天的键标签() {
        // 当地 2026-11-17 00:30，日窗口起点是 17 日零点而不是 UTC 日期。
        Instant now = Instant.parse("2026-11-16T16:30:00Z");

        ZonedDateTime start = RateLimitWindow.windowStart(RateLimitScope.MOOD_DAY, now, SHANGHAI);

        assertThat(start).isEqualTo(ZonedDateTime.of(2026, 11, 17, 0, 0, 0, 0, SHANGHAI));
        String key = RateLimitWindow.key(RateLimitScope.MOOD_DAY, "xinyu:test:t:", 7, start);
        assertThat(key).isEqualTo("xinyu:test:t:rl:mood_day:7:20261117");
    }

    @Test
    void 全站范围的键持有者固定为global() {
        ZonedDateTime start = ZonedDateTime.of(2026, 11, 16, 14, 0, 0, 0, SHANGHAI);

        String key = RateLimitWindow.key(RateLimitScope.GLOBAL_HOUR, "xinyu:dev:", 42, start);

        assertThat(key).isEqualTo("xinyu:dev:rl:global_hour:global:2026111614");
    }

    @Test
    void 跨天前后的日窗口起点归属正确的自然日() {
        ZonedDateTime beforeMidnight = RateLimitWindow.windowStart(
                RateLimitScope.REVIEW_DAY, Instant.parse("2026-11-16T15:59:00Z"), SHANGHAI);
        ZonedDateTime afterMidnight = RateLimitWindow.windowStart(
                RateLimitScope.REVIEW_DAY, Instant.parse("2026-11-16T16:01:00Z"), SHANGHAI);

        assertThat(beforeMidnight.toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 11, 16));
        assertThat(afterMidnight.toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 11, 17));
    }

    @Test
    void 再试秒数是到下一窗口边界的剩余时间且向上取整() {
        // 当地 14:30:00.5，距 15:00 剩 1799.5 秒，向上取整为 1800。
        Instant now = Instant.parse("2026-11-16T06:30:00.500Z");

        long hourWait = RateLimitWindow.retryAfterSeconds(RateLimitScope.MOOD_HOUR, now, SHANGHAI);

        assertThat(hourWait).isEqualTo(1800);

        // 当地 23:59 整，日窗口再过 60 秒重置。
        Instant evening = Instant.parse("2026-11-16T15:59:00Z");
        assertThat(RateLimitWindow.retryAfterSeconds(RateLimitScope.MOOD_DAY, evening, SHANGHAI))
                .isEqualTo(60);
    }

    @Test
    void 过期秒数是窗口长度加60秒余量() {
        assertThat(RateLimitWindow.ttlSeconds(RateLimitScope.MOOD_HOUR)).isEqualTo(3660);
        assertThat(RateLimitWindow.ttlSeconds(RateLimitScope.MOOD_DAY)).isEqualTo(86460);
    }
}
