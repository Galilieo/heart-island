package com.xinyu.ratelimit;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scripting.support.ResourceScriptSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 需要本机真实 Redis：用户设置 AI_TEST_REDIS_URL（指向专用库，例如 redis://127.0.0.1:6379/15）。
 * 未设置时明确跳过并说明原因，不算通过。键前缀 xinyu:test:{随机串}:，结束删除自己的键。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "AI_TEST_REDIS_URL", matches = ".+",
        disabledReason = "未设置 AI_TEST_REDIS_URL（本机 Redis 专用库，例如 redis://127.0.0.1:6379/15），Redis 限流集成测试跳过")
class RateLimitRedisIT {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String REDIS_URL = System.getenv("AI_TEST_REDIS_URL");

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static String prefix;
    private static RedisScript<List> script;

    @BeforeAll
    void connect() {
        // 未设置 AI_TEST_REDIS_URL 时整个类被条件注解跳过，不会走到这里。
        factory = factory(REDIS_URL, Duration.ofMillis(500));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        prefix = "xinyu:test:" + java.util.UUID.randomUUID().toString().substring(0, 8) + ":";
        script = loadScript();
    }

    @AfterAll
    static void cleanup() {
        if (redis == null) {
            return;
        }
        // 只删除自己前缀的键，不碰专用库里的其他数据。
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(prefix + "*").count(500).build())) {
            while (cursor.hasNext()) {
                redis.delete(cursor.next());
            }
        } finally {
            factory.destroy();
        }
    }

    @Test
    void 多范围检查原子_一个超额时其他计数不变() {
        RateLimitProperties config = properties(1, 100, 10, 10, 1000);
        RedisRateLimiter limiter = new RedisRateLimiter(redis, config, Clock.system(SHANGHAI), SHANGHAI, script);
        // 全站键被所有用例共享：按增量断言，避免用例执行顺序影响结果。
        long globalBefore = parseCount(hourCount("global_hour", "global"));

        assertThat(limiter.check(RateLimitType.MOOD_REPLY, 101).allowed()).isTrue();
        RateLimiter.Decision denied = limiter.check(RateLimitType.MOOD_REPLY, 101);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.blockedScope()).isEqualTo(RateLimitScope.MOOD_HOUR);
        assertThat(denied.retryAfterSeconds()).isBetween(1, 3600);
        // 拒绝不消耗任何范围：每人键停在 1，全站键只增加本次通过的一次。
        assertThat(hourCount("mood_hour", 101)).isEqualTo("1");
        assertThat(dayCount("mood_day", 101)).isEqualTo("1");
        assertThat(parseCount(hourCount("global_hour", "global"))).isEqualTo(globalBefore + 1);
    }

    private static long parseCount(String value) {
        return value == null ? 0L : Long.parseLong(value);
    }

    @Test
    void 多范围同时超额时取最晚重置的范围与等待秒数() {
        // 每小时与每天额度都是 1：第二次请求两个范围同时超额，
        // 触发范围取最晚重置的 MOOD_DAY，等待秒数到当天零点（不超过一天的秒数）。
        RateLimitProperties config = properties(1, 1, 10, 10, 1000);
        RedisRateLimiter limiter = new RedisRateLimiter(redis, config, Clock.system(SHANGHAI), SHANGHAI, script);

        assertThat(limiter.check(RateLimitType.MOOD_REPLY, 111).allowed()).isTrue();
        RateLimiter.Decision denied = limiter.check(RateLimitType.MOOD_REPLY, 111);

        assertThat(denied.blockedScope()).isEqualTo(RateLimitScope.MOOD_DAY);
        assertThat(denied.retryAfterSeconds()).isBetween(1, 86400);
    }

    @Test
    void 计数键的Ttl存在且不超过窗口加60秒() {
        RateLimitProperties config = properties(20, 100, 10, 10, 1000);
        RedisRateLimiter limiter = new RedisRateLimiter(redis, config, Clock.system(SHANGHAI), SHANGHAI, script);

        limiter.check(RateLimitType.MOOD_REPLY, 201);

        Long hourTtl = redis.getExpire(hourKey("mood_hour", 201));
        Long dayTtl = redis.getExpire(dayKey("mood_day", 201));
        assertThat(hourTtl).isBetween(1L, 3660L);
        assertThat(dayTtl).isBetween(1L, 86460L);
    }

    @Test
    void 并发下恰好只有额度数量的请求通过() throws Exception {
        // 单用户社区每小时 20 次；同一用户 50 个并发请求应恰好 20 个通过、30 个被拒。
        // 全站范围 1000 不会先触发，被拒范围必然是 community_hour。
        RateLimitProperties config = properties(20, 100, 20, 10, 1000);
        RedisRateLimiter limiter = new RedisRateLimiter(redis, config, Clock.system(SHANGHAI), SHANGHAI, script);
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger deniedCount = new AtomicInteger();
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (limiter.check(RateLimitType.COMMUNITY_POST, 302L).allowed()) {
                        allowed.incrementAndGet();
                    } else {
                        deniedCount.incrementAndGet();
                    }
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(allowed.get()).isEqualTo(20);
        assertThat(deniedCount.get()).isEqualTo(30);
        assertThat(hourCount("community_hour", 302)).isEqualTo("20");
    }

    @Test
    void 故障时降级本机冷却后重试恢复() {
        // 坏地址的客户端模拟 Redis 不可用；可切换替身在冷却后切回真实 Redis 验证恢复。
        LettuceConnectionFactory brokenFactory = factory("redis://127.0.0.1:1/0", Duration.ofMillis(300));
        brokenFactory.afterPropertiesSet();
        SwitchableRedis switchable = new SwitchableRedis();
        MutableTestClock clock = new MutableTestClock(Instant.now(), SHANGHAI);
        RateLimitProperties config = new RateLimitProperties(true, prefix, Duration.ofMillis(300),
                Duration.ofSeconds(30), 20, 100, 10, 10, 1000);
        LocalRateLimiter local = new LocalRateLimiter(config, clock, SHANGHAI);
        FallbackRateLimiter fallback = new FallbackRateLimiter(switchable, local, config, clock,
                new RateLimitMetrics(clock));
        try {
            StringRedisTemplate broken = new StringRedisTemplate(brokenFactory);
            broken.afterPropertiesSet();
            switchable.current = new RedisRateLimiter(broken, config, clock, SHANGHAI, script);

            assertThat(fallback.check(RateLimitType.MOOD_REPLY, 401).allowed()).isTrue();
            assertThat(fallback.backend()).isEqualTo(FallbackRateLimiter.BACKEND_LOCAL_DEGRADED);

            // 冷却期内不再尝试 Redis。
            clock.advance(Duration.ofSeconds(10));
            fallback.check(RateLimitType.MOOD_REPLY, 401);
            assertThat(switchable.calls).isEqualTo(1);

            // 冷却结束后切回真实 Redis，下一次请求恢复共享计数。
            clock.advance(Duration.ofSeconds(21));
            switchable.current = new RedisRateLimiter(redis, config, clock, SHANGHAI, script);
            assertThat(fallback.check(RateLimitType.MOOD_REPLY, 401).allowed()).isTrue();
            assertThat(fallback.backend()).isEqualTo(FallbackRateLimiter.BACKEND_REDIS);
        } finally {
            brokenFactory.destroy();
        }
    }

    /** 测试用可切换 Redis 后端：先坏后好，驱动 FallbackRateLimiter 的降级与恢复。 */
    private static final class SwitchableRedis implements RateLimiter {
        volatile RateLimiter current;
        int calls;

        @Override
        public RateLimiter.Decision check(RateLimitType type, long userId) {
            calls++;
            return current.check(type, userId);
        }
    }

    /** 当前小时窗口键上的计数值；标签与 RateLimitWindow 的 yyyyMMddHH 一致。 */
    private String hourCount(String scope, Object owner) {
        String label = java.time.ZonedDateTime.now(SHANGHAI)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHH"));
        return redis.opsForValue().get(prefix + "rl:" + scope + ":" + owner + ":" + label);
    }

    /** 当前自然日窗口键上的计数值；标签与 RateLimitWindow 的 yyyyMMdd 一致。 */
    private String dayCount(String scope, Object owner) {
        String label = java.time.LocalDate.now(SHANGHAI)
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        return redis.opsForValue().get(prefix + "rl:" + scope + ":" + owner + ":" + label);
    }

    private String hourKey(String scope, long userId) {
        String label = java.time.ZonedDateTime.now(SHANGHAI)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHH"));
        return prefix + "rl:" + scope + ":" + userId + ":" + label;
    }

    private String dayKey(String scope, long userId) {
        String label = java.time.LocalDate.now(SHANGHAI)
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        return prefix + "rl:" + scope + ":" + userId + ":" + label;
    }

    private static RateLimitProperties properties(int moodPerHour, int moodPerDay,
                                                  int communityPerHour, int reviewPerDay, int globalPerHour) {
        return new RateLimitProperties(true, prefix, Duration.ofMillis(500), Duration.ofSeconds(30),
                moodPerHour, moodPerDay, communityPerHour, reviewPerDay, globalPerHour);
    }

    private static LettuceConnectionFactory factory(String url, Duration timeout) {
        RedisURI uri = RedisURI.create(url);
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(uri.getHost(), uri.getPort());
        standalone.setDatabase(uri.getDatabase());
        if (uri.getPassword() != null) {
            standalone.setPassword(RedisPassword.of(uri.getPassword()));
        }
        // 与生产 RateLimitConfig 一致：连接建立超时同样受入参约束。
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(timeout).build())
                        .build())
                .commandTimeout(timeout).build();
        return new LettuceConnectionFactory(standalone, client);
    }

    private static RedisScript<List> loadScript() {
        DefaultRedisScript<List> loaded = new DefaultRedisScript<>();
        loaded.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("ratelimit/check-and-increment.lua")));
        loaded.setResultType(List.class);
        return loaded;
    }
}
