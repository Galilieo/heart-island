package com.xinyu.ratelimit;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.lang.Nullable;
import org.springframework.scripting.support.ResourceScriptSource;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 限流装配：spring.data.redis.url 非空时创建 Redis 客户端与 {@link RedisRateLimiter}，
 * 留空时只有本机计数器，应用照常启动（本机开发默认，见契约第 9 节本机开发说明）。
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitConfig.class);

    /** 本机计数的清理周期；清理规则见 LocalRateLimiter.purgeExpired。 */
    private static final Duration LOCAL_PURGE_INTERVAL = Duration.ofSeconds(60);

    // 限流不新增 Clock/ZoneId bean：CommunityReportService 等按类型裸注入 Clock，
    // 全上下文只允许一个该类型 bean。窗口时区沿用 reviewZoneId（默认 Asia/Shanghai，
    // 契约 2.1 要求整点与自然日对齐），与回顾统计共用项目墙上时间约定。

    /**
     * Redis 连接工厂：url 为空时返回 null（容器跳过该 bean），下游全部按未配置处理。
     * 命令超时取 xinyu.rate-limit.redis-timeout，超时本次即降级，不拖住请求线程。
     */
    @Bean
    @Nullable
    public LettuceConnectionFactory rateLimitRedisConnectionFactory(RateLimitProperties config,
                                                                   @Value("${spring.data.redis.url:}") String redisUrl) {
        if (redisUrl == null || redisUrl.isBlank()) {
            logger.info("未配置 Redis（spring.data.redis.url 为空），限流使用本机计数");
            return null;
        }
        RedisURI uri = RedisURI.create(redisUrl);
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(uri.getHost(), uri.getPort());
        standalone.setDatabase(uri.getDatabase());
        if (uri.getUsername() != null) {
            standalone.setUsername(uri.getUsername());
        }
        if (uri.getPassword() != null) {
            standalone.setPassword(RedisPassword.of(uri.getPassword()));
        }
        LettuceClientConfiguration client = uri.isSsl()
                ? LettuceClientConfiguration.builder()
                        .clientOptions(connectOptions(config)).commandTimeout(config.redisTimeout()).useSsl().build()
                : LettuceClientConfiguration.builder()
                        .clientOptions(connectOptions(config)).commandTimeout(config.redisTimeout()).build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(standalone, client);
        factory.afterPropertiesSet();
        return factory;
    }

    /** TCP 连接建立超时与命令超时同量级：不设时 Lettuce 默认约 10 秒，网络黑洞会让首次降级等太久。 */
    private static ClientOptions connectOptions(RateLimitProperties config) {
        return ClientOptions.builder()
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(config.redisTimeout())
                        .build())
                .build();
    }

    @Bean
    @Nullable
    public StringRedisTemplate rateLimitRedisTemplate(
            @Nullable @Qualifier("rateLimitRedisConnectionFactory") LettuceConnectionFactory factory) {
        if (factory == null) {
            return null;
        }
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @Nullable
    public RedisRateLimiter redisRateLimiter(RateLimitProperties config,
                                             @Qualifier("reviewClock") Clock clock,
                                             @Qualifier("reviewZoneId") ZoneId zone,
                                             @Nullable StringRedisTemplate rateLimitRedisTemplate,
                                             RedisScript<List> rateLimitScript) {
        if (rateLimitRedisTemplate == null) {
            return null;
        }
        return new RedisRateLimiter(rateLimitRedisTemplate, config, clock, zone, rateLimitScript);
    }

    @Bean
    public LocalRateLimiter localRateLimiter(RateLimitProperties config,
                                             @Qualifier("reviewClock") Clock clock,
                                             @Qualifier("reviewZoneId") ZoneId zone) {
        return new LocalRateLimiter(config, clock, zone);
    }

    /**
     * 本机计数的定期清理线程：单线程守护调度，每 60 秒清掉早于上一窗口的条目；
     * 容器关闭时 shutdownNow 中断等待中的调度，不阻塞停机。
     */
    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService rateLimitLocalPurger(LocalRateLimiter localRateLimiter) {
        ScheduledExecutorService purger = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "rate-limit-local-purger");
            thread.setDaemon(true);
            return thread;
        });
        purger.scheduleAtFixedRate(localRateLimiter::purgeExpired,
                LOCAL_PURGE_INTERVAL.toSeconds(), LOCAL_PURGE_INTERVAL.toSeconds(), TimeUnit.SECONDS);
        return purger;
    }

    @Bean
    public RateLimitMetrics rateLimitMetrics(@Qualifier("reviewClock") Clock clock) {
        return new RateLimitMetrics(clock);
    }

    /** 对外唯一限流入口；@Primary 使业务入口注入 RateLimiter 时拿到它。 */
    @Bean
    @Primary
    public FallbackRateLimiter fallbackRateLimiter(RateLimitProperties config,
                                                   @Qualifier("reviewClock") Clock clock,
                                                   LocalRateLimiter localRateLimiter,
                                                   RateLimitMetrics rateLimitMetrics,
                                                   @Nullable RedisRateLimiter redisRateLimiter) {
        return new FallbackRateLimiter(redisRateLimiter, localRateLimiter, config, clock, rateLimitMetrics);
    }

    /** 预加载限流 Lua 脚本并做一次结果类型校验；脚本内容见 resources/ratelimit/check-and-increment.lua。 */
    @Bean
    public RedisScript<List> rateLimitScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("ratelimit/check-and-increment.lua")));
        script.setResultType(List.class);
        return script;
    }
}
