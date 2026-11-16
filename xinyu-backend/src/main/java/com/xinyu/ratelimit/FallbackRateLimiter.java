package com.xinyu.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;

/**
 * 限流唯一入口：Redis 正常时走共享计数，命令超时、连接失败或脚本报错时退回本机计数并照常服务。
 * 进入降级后的冷却期内不再尝试 Redis（契约 2.3），避免每个请求都等一次超时；
 * 冷却结束后下一次请求重试 Redis，成功即恢复。
 */
public class FallbackRateLimiter implements RateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(FallbackRateLimiter.class);

    /** 后端状态标识，供后台概览与测试读取；对应契约第 5 节观测口径。 */
    public static final String BACKEND_REDIS = "redis";
    public static final String BACKEND_LOCAL_DEGRADED = "local_degraded";
    public static final String BACKEND_LOCAL_ONLY = "local_only";
    public static final String BACKEND_DISABLED = "disabled";

    /** Redis 后端；类型取接口以便测试注入替身，运行期是 RedisRateLimiter，未配置时为 null。 */
    private final RateLimiter redis;
    private final LocalRateLimiter local;
    private final RateLimitProperties config;
    private final Clock clock;
    private final RateLimitMetrics metrics;
    private final Duration fallbackCooldown;

    /** 降级截止时刻（epoch 毫秒）；MIN_VALUE 表示从未降级。volatile：读多写少的状态开关。 */
    private volatile long fallbackNotBeforeMillis = Long.MIN_VALUE;

    public FallbackRateLimiter(RateLimiter redis, LocalRateLimiter local,
                               RateLimitProperties config, Clock clock, RateLimitMetrics metrics) {
        this.redis = redis;
        this.local = local;
        this.config = config;
        this.clock = clock;
        this.metrics = metrics;
        this.fallbackCooldown = config.fallbackCooldown();
    }

    @Override
    public Decision check(RateLimitType type, long userId) {
        if (!config.enabled()) {
            return Decision.allow();
        }
        if (redis == null) {
            return observe(type, local.check(type, userId), userId, BACKEND_LOCAL_ONLY);
        }
        long nowMillis = clock.millis();
        if (fallbackNotBeforeMillis > nowMillis) {
            return observe(type, local.check(type, userId), userId, BACKEND_LOCAL_DEGRADED);
        }
        try {
            Decision decision = redis.check(type, userId);
            if (fallbackNotBeforeMillis != Long.MIN_VALUE) {
                fallbackNotBeforeMillis = Long.MIN_VALUE;
                logger.info("rate_limit_backend_recovered backend=redis");
            }
            return observe(type, decision, userId, BACKEND_REDIS);
        } catch (RuntimeException unavailable) {
            // 降级原因只记分类，不写异常原文，避免连接串里的凭据进入日志。
            fallbackNotBeforeMillis = nowMillis + fallbackCooldown.toMillis();
            logger.warn("rate_limit_fallback_entered reason={} cooldownSeconds={} backend=local",
                    failureClass(unavailable), fallbackCooldown.toSeconds());
            return observe(type, local.check(type, userId), userId, BACKEND_LOCAL_DEGRADED);
        }
    }

    /** 当前限流后端，供后台概览显示；与实际检查路径同源。 */
    public String backend() {
        if (!config.enabled()) {
            return BACKEND_DISABLED;
        }
        if (redis == null) {
            return BACKEND_LOCAL_ONLY;
        }
        return fallbackNotBeforeMillis > clock.millis() ? BACKEND_LOCAL_DEGRADED : BACKEND_REDIS;
    }

    /** 拒绝时统一记一条 INFO 日志与进程内计数（契约 2.6）；日志只含范围与身份，不含内容文本。 */
    private Decision observe(RateLimitType type, Decision decision, long userId, String backend) {
        if (!decision.allowed()) {
            metrics.recordRejection(decision.blockedScope());
            logger.info("rate_limited type={} scope={} backend={} userId={} retryAfterSeconds={}",
                    type, decision.blockedScope(), backend, userId, decision.retryAfterSeconds());
        }
        return decision;
    }

    private static String failureClass(RuntimeException failure) {
        return failure.getClass().getSimpleName();
    }
}
