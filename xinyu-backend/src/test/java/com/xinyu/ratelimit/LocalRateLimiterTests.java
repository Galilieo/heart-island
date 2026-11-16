package com.xinyu.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** 本机限流：额度边界、多范围原子性、窗口重置与旧数据清理；规则与 Redis 版共用同一规则表。 */
class LocalRateLimiterTests {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    /** 每人小时 1 次、全站小时 2 次，其余放宽，用来构造“部分范围超额”。 */
    private static RateLimitProperties limits(int moodPerHour, int globalPerHour) {
        return new RateLimitProperties(true, "xinyu:test:t:", Duration.ofMillis(200), Duration.ofSeconds(30),
                moodPerHour, 100, 10, 10, globalPerHour);
    }

    @Test
    void 恰好达到额度时通过下一次拒绝() {
        MutableTestClock clock = new MutableTestClock(Instant.parse("2026-11-16T06:00:00Z"), SHANGHAI);
        LocalRateLimiter limiter = new LocalRateLimiter(limits(3, 1000), clock, SHANGHAI);

        assertThat(limiter.check(RateLimitType.MOOD_REPLY, 1).allowed()).isTrue();
        assertThat(limiter.check(RateLimitType.MOOD_REPLY, 1).allowed()).isTrue();
        assertThat(limiter.check(RateLimitType.MOOD_REPLY, 1).allowed()).isTrue();

        RateLimiter.Decision fourth = limiter.check(RateLimitType.MOOD_REPLY, 1);
        assertThat(fourth.allowed()).isFalse();
        assertThat(fourth.blockedScope()).isEqualTo(RateLimitScope.MOOD_HOUR);
    }

    @Test
    void 某一范围超额时整体拒绝且不消耗其他范围() {
        MutableTestClock clock = new MutableTestClock(Instant.parse("2026-11-16T06:00:00Z"), SHANGHAI);
        // mood 每小时只允许 1 次；全站允许 2 次。
        LocalRateLimiter limiter = new LocalRateLimiter(limits(1, 2), clock, SHANGHAI);

        assertThat(limiter.check(RateLimitType.MOOD_REPLY, 1).allowed()).isTrue();
        assertThat(limiter.check(RateLimitType.MOOD_REPLY, 1).allowed()).isFalse();

        // 心情小时范围已满后的拒绝不能继续消耗全站：社区调用还剩 1 个全站名额。
        assertThat(limiter.check(RateLimitType.COMMUNITY_POST, 2).allowed()).isTrue();
        RateLimiter.Decision exhausted = limiter.check(RateLimitType.COMMUNITY_POST, 3);
        assertThat(exhausted.allowed()).isFalse();
        assertThat(exhausted.blockedScope()).isEqualTo(RateLimitScope.GLOBAL_HOUR);
    }

    @Test
    void 多范围同时超额时取最晚重置的范围与等待秒数() {
        // 每小时与每天额度都是 1：第二次请求两个范围同时超额。
        // 必须等全部超额范围重置才能再试，因此触发范围取最晚重置的 MOOD_DAY，
        // 等待秒数到当天零点（当地 14:00 起 10 小时）而不是到下一个整点。
        MutableTestClock clock = new MutableTestClock(Instant.parse("2026-11-16T06:00:00Z"), SHANGHAI);
        RateLimitProperties bothOne = new RateLimitProperties(true, "xinyu:test:t:",
                Duration.ofMillis(200), Duration.ofSeconds(30), 1, 1, 10, 10, 1000);
        LocalRateLimiter limiter = new LocalRateLimiter(bothOne, clock, SHANGHAI);

        assertThat(limiter.check(RateLimitType.MOOD_REPLY, 1).allowed()).isTrue();
        RateLimiter.Decision denied = limiter.check(RateLimitType.MOOD_REPLY, 1);

        assertThat(denied.blockedScope()).isEqualTo(RateLimitScope.MOOD_DAY);
        assertThat(denied.retryAfterSeconds()).isEqualTo(10 * 3600);
    }

    @Test
    void 窗口切换后计数重置且不同用户互不影响() {
        MutableTestClock clock = new MutableTestClock(Instant.parse("2026-11-16T06:00:00Z"), SHANGHAI);
        LocalRateLimiter limiter = new LocalRateLimiter(limits(1, 1000), clock, SHANGHAI);

        assertThat(limiter.check(RateLimitType.MOOD_REPLY, 1).allowed()).isTrue();
        assertThat(limiter.check(RateLimitType.MOOD_REPLY, 1).allowed()).isFalse();
        assertThat(limiter.check(RateLimitType.MOOD_REPLY, 2).allowed()).isTrue();

        clock.advance(Duration.ofHours(1));
        assertThat(limiter.check(RateLimitType.MOOD_REPLY, 1).allowed()).isTrue();
    }

    @Test
    void 拒绝时返回到下一窗口边界的再试秒数() {
        // 当地 14:30，小时窗口 15:00 结束，剩余 1800 秒。
        MutableTestClock clock = new MutableTestClock(Instant.parse("2026-11-16T06:30:00Z"), SHANGHAI);
        LocalRateLimiter limiter = new LocalRateLimiter(limits(1, 1000), clock, SHANGHAI);

        limiter.check(RateLimitType.MOOD_REPLY, 1);
        RateLimiter.Decision denied = limiter.check(RateLimitType.MOOD_REPLY, 1);

        assertThat(denied.retryAfterSeconds()).isEqualTo(1800);
    }

    @Test
    void 清理只保留当前与上一个窗口的数据() {
        MutableTestClock clock = new MutableTestClock(Instant.parse("2026-11-16T06:00:00Z"), SHANGHAI);
        LocalRateLimiter limiter = new LocalRateLimiter(limits(1, 1000), clock, SHANGHAI);

        limiter.check(RateLimitType.MOOD_REPLY, 1);
        // 推进一个小时：旧的小时条目属于上一窗口，应保留；日窗口条目当前有效。
        clock.advance(Duration.ofHours(1));
        limiter.check(RateLimitType.MOOD_REPLY, 1);
        assertThat(limiter.purgeExpired()).isEqualTo(0);

        // 再推进两小时：14、15 点的小时条目都早于上一窗口，被清理；当日的日窗口条目保留。
        clock.advance(Duration.ofHours(2));
        assertThat(limiter.purgeExpired()).isEqualTo(4);
        assertThat(limiter.size()).isEqualTo(1);
    }
}
