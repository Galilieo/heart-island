import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;


import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/**
 * B4 组件微基准：单次 RateLimiter.check 的耗时，Redis 后端 vs 本机计数。
 * 只依赖已编译的业务类与既有依赖，不修改业务代码；连接独立测试 Redis（默认 127.0.0.1:6390），
 * 键前缀 xinyu:test:，结束清理本前缀全部键。
 *
 * 编译运行方式见 perf/README.md（javac -cp target/classes+依赖）。
 */
public class RateLimitBench {

    static final int WARMUP = 300;
    static final int SAMPLES = 3000;
    static final int USER_SPREAD = 200; // 轮转用户，绝大多数为 allow，避免额度拒绝主导样本

    public static void main(String[] args) throws Exception {
        String host = System.getenv().getOrDefault("BENCH_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("BENCH_REDIS_PORT", "6390"));
        var props = new com.xinyu.ratelimit.RateLimitProperties(
                true, "xinyu:test:", Duration.ofMillis(200), Duration.ofSeconds(30),
                20, 100, 10, 10, 1000);
        Clock clock = Clock.systemDefaultZone();
        ZoneId zone = ZoneId.of("Asia/Shanghai");

        // ---- 本机计数 ----
        var local = new com.xinyu.ratelimit.LocalRateLimiter(props, clock, zone);
        long[] localLat = bench(local);
        System.out.println("local " + report(localLat));

        // ---- Redis 后端 ----
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(host, port);
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(200))
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder()
                                .connectTimeout(Duration.ofMillis(2000)).build())
                        .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(200))).build())
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(standalone, client);
        factory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(factory);
        String luaText = new String(java.nio.file.Files.readAllBytes(
                new File(args[0]).toPath()), java.nio.charset.StandardCharsets.UTF_8);
        RedisScript<List> script = new DefaultRedisScript<>(luaText, List.class);
        // 预热连接
        redis.getConnectionFactory().getConnection().ping();

        var redisLimiter = new com.xinyu.ratelimit.RedisRateLimiter(redis, props, clock, zone, script);
        long[] redisLat = bench(redisLimiter);
        System.out.println("redis " + report(redisLat));

        // ---- 清理 xinyu:test:* ----
        var keys = redis.keys("xinyu:test:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        System.out.println("cleaned_keys=" + (keys == null ? 0 : keys.size()));
        factory.destroy();
    }

    static long[] bench(com.xinyu.ratelimit.RateLimiter limiter) {
        long[] lat = new long[SAMPLES];
        for (int i = 0; i < WARMUP; i++) {
            limiter.check(com.xinyu.ratelimit.RateLimitType.MOOD_REPLY, 1 + i % USER_SPREAD);
        }
        for (int i = 0; i < SAMPLES; i++) {
            long t0 = System.nanoTime();
            limiter.check(com.xinyu.ratelimit.RateLimitType.MOOD_REPLY, 1 + i % USER_SPREAD);
            lat[i] = (System.nanoTime() - t0) / 1_000;
        }
        return lat;
    }

    static String report(long[] lat) {
        long[] sorted = lat.clone();
        Arrays.sort(sorted);
        return "n=" + sorted.length
                + " p50us=" + pct(sorted, 0.50)
                + " p95us=" + pct(sorted, 0.95)
                + " p99us=" + pct(sorted, 0.99)
                + " maxus=" + sorted[sorted.length - 1];
    }

    static long pct(long[] sorted, double p) {
        return sorted[(int) Math.ceil(p * sorted.length) - 1];
    }
}
