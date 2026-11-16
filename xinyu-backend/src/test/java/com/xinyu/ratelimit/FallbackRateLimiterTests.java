package com.xinyu.ratelimit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** Redis 故障时退回本机限流：降级进入、冷却期内不再尝试 Redis、恢复与日志各一条。 */
class FallbackRateLimiterTests {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    /** Redis 替身：可切换“抛异常”与“正常放行”，并统计被调次数。 */
    private static final class FlakyRedis implements RateLimiter {
        int calls;
        boolean broken = true;

        @Override
        public Decision check(RateLimitType type, long userId) {
            calls++;
            if (broken) {
                throw new IllegalStateException("connection refused");
            }
            return Decision.allow();
        }
    }

    private final Logger logger = (Logger) LoggerFactory.getLogger(FallbackRateLimiter.class);
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void attachLogCollector() {
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachLogCollector() {
        logger.detachAppender(logs);
    }

    private static long warnCount(ListAppender<ILoggingEvent> logs) {
        return logs.list.stream().filter(event -> event.getLevel() == ch.qos.logback.classic.Level.WARN).count();
    }

    private static long recoverCount(ListAppender<ILoggingEvent> logs) {
        return logs.list.stream()
                .filter(event -> event.getLevel() == ch.qos.logback.classic.Level.INFO)
                .filter(event -> event.getFormattedMessage().contains("rate_limit_backend_recovered"))
                .count();
    }

    @Test
    void redis异常时本次改走本机并进入降级冷却() {
        MutableTestClock clock = new MutableTestClock(Instant.parse("2026-11-16T06:00:00Z"), SHANGHAI);
        FlakyRedis redis = new FlakyRedis();
        RateLimitProperties config = new RateLimitProperties(true, "xinyu:test:t:",
                Duration.ofMillis(200), Duration.ofSeconds(30), 1, 100, 10, 10, 1000);
        LocalRateLimiter local = new LocalRateLimiter(config, clock, SHANGHAI);
        FallbackRateLimiter limiter = new FallbackRateLimiter(redis, local, config, clock,
                new RateLimitMetrics(clock));

        RateLimiter.Decision first = limiter.check(RateLimitType.MOOD_REPLY, 1);
        RateLimiter.Decision second = limiter.check(RateLimitType.MOOD_REPLY, 1);

        // 第一次走本机计数放行；本机额度 1 次已用，第二次被本机拒绝。
        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isFalse();
        assertThat(second.blockedScope()).isEqualTo(RateLimitScope.MOOD_HOUR);
        assertThat(limiter.backend()).isEqualTo(FallbackRateLimiter.BACKEND_LOCAL_DEGRADED);
        assertThat(warnCount(logs)).isEqualTo(1);
    }

    @Test
    void 冷却期内不再尝试Redis冷却结束后重试并恢复() {
        MutableTestClock clock = new MutableTestClock(Instant.parse("2026-11-16T06:00:00Z"), SHANGHAI);
        FlakyRedis redis = new FlakyRedis();
        RateLimitProperties config = new RateLimitProperties(true, "xinyu:test:t:",
                Duration.ofMillis(200), Duration.ofSeconds(30), 20, 100, 10, 10, 1000);
        LocalRateLimiter local = new LocalRateLimiter(config, clock, SHANGHAI);
        FallbackRateLimiter limiter = new FallbackRateLimiter(redis, local, config, clock,
                new RateLimitMetrics(clock));

        limiter.check(RateLimitType.MOOD_REPLY, 1);
        assertThat(redis.calls).isEqualTo(1);

        // 冷却 30 秒内：不再碰 Redis，直接走本机。
        clock.advance(Duration.ofSeconds(10));
        limiter.check(RateLimitType.MOOD_REPLY, 1);
        limiter.check(RateLimitType.MOOD_REPLY, 1);
        assertThat(redis.calls).isEqualTo(1);
        assertThat(limiter.backend()).isEqualTo(FallbackRateLimiter.BACKEND_LOCAL_DEGRADED);

        // 冷却结束后下一次请求重试 Redis；成功即恢复，各只记一条日志。
        clock.advance(Duration.ofSeconds(21));
        redis.broken = false;
        RateLimiter.Decision recovered = limiter.check(RateLimitType.MOOD_REPLY, 1);

        assertThat(redis.calls).isEqualTo(2);
        assertThat(recovered.allowed()).isTrue();
        assertThat(limiter.backend()).isEqualTo(FallbackRateLimiter.BACKEND_REDIS);
        assertThat(warnCount(logs)).isEqualTo(1);
        assertThat(recoverCount(logs)).isEqualTo(1);
    }

    @Test
    void 未配置Redis时直接使用本机计数() {
        MutableTestClock clock = new MutableTestClock(Instant.parse("2026-11-16T06:00:00Z"), SHANGHAI);
        RateLimitProperties config = new RateLimitProperties(true, "xinyu:test:t:",
                Duration.ofMillis(200), Duration.ofSeconds(30), 20, 100, 10, 10, 1000);
        LocalRateLimiter local = new LocalRateLimiter(config, clock, SHANGHAI);
        FallbackRateLimiter limiter = new FallbackRateLimiter(null, local, config, clock,
                new RateLimitMetrics(clock));

        assertThat(limiter.check(RateLimitType.MOOD_REPLY, 1).allowed()).isTrue();
        assertThat(limiter.backend()).isEqualTo(FallbackRateLimiter.BACKEND_LOCAL_ONLY);
    }

    @Test
    void 关闭限流时所有检查直接通过() {
        MutableTestClock clock = new MutableTestClock(Instant.parse("2026-11-16T06:00:00Z"), SHANGHAI);
        RateLimitProperties config = new RateLimitProperties(false, "xinyu:test:t:",
                Duration.ofMillis(200), Duration.ofSeconds(30), 1, 100, 10, 10, 1000);
        FlakyRedis redis = new FlakyRedis();
        LocalRateLimiter local = new LocalRateLimiter(config, clock, SHANGHAI);
        FallbackRateLimiter limiter = new FallbackRateLimiter(redis, local, config, clock,
                new RateLimitMetrics(clock));

        assertThat(limiter.check(RateLimitType.MOOD_REPLY, 1).allowed()).isTrue();
        assertThat(limiter.check(RateLimitType.MOOD_REPLY, 1).allowed()).isTrue();
        assertThat(redis.calls).isEqualTo(0);
        assertThat(limiter.backend()).isEqualTo(FallbackRateLimiter.BACKEND_DISABLED);
    }

    @Test
    void 拒绝时按范围记录进程内计数() {
        MutableTestClock clock = new MutableTestClock(Instant.parse("2026-11-16T06:00:00Z"), SHANGHAI);
        RateLimitProperties config = new RateLimitProperties(true, "xinyu:test:t:",
                Duration.ofMillis(200), Duration.ofSeconds(30), 1, 100, 10, 10, 1000);
        LocalRateLimiter local = new LocalRateLimiter(config, clock, SHANGHAI);
        RateLimitMetrics metrics = new RateLimitMetrics(clock);
        FallbackRateLimiter limiter = new FallbackRateLimiter(null, local, config, clock, metrics);

        limiter.check(RateLimitType.MOOD_REPLY, 1);
        limiter.check(RateLimitType.MOOD_REPLY, 1);
        limiter.check(RateLimitType.MOOD_REPLY, 1);

        assertThat(metrics.todayRejections().get("mood_hour")).isEqualTo(2L);
    }
}
