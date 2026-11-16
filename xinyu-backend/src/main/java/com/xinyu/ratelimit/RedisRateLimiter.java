package com.xinyu.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** 用单个 Lua 脚本做多范围原子检查与递增（脚本见 resources/ratelimit/check-and-increment.lua）。 */
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final RateLimitProperties config;
    private final Clock clock;
    private final ZoneId zone;
    private final RedisScript<List> checkAndIncrement;

    public RedisRateLimiter(StringRedisTemplate redis, RateLimitProperties config,
                            Clock clock, ZoneId zone, RedisScript<List> checkAndIncrement) {
        this.redis = redis;
        this.config = config;
        this.clock = clock;
        this.zone = zone;
        this.checkAndIncrement = checkAndIncrement;
    }

    /**
     * 执行限流脚本；命令超时、连接失败或脚本报错都作为异常抛给调用方（FallbackRateLimiter）降级，
     * 本类不做任何降级决策。
     */
    @Override
    public Decision check(RateLimitType type, long userId) {
        Instant now = clock.instant();
        List<RateLimitScope> scopes = type.scopes();
        List<String> keys = new ArrayList<>(scopes.size());
        List<String> limits = new ArrayList<>(scopes.size());
        List<String> ttls = new ArrayList<>(scopes.size());
        List<String> windowEnds = new ArrayList<>(scopes.size());

        // 参数按脚本注释排列：额度 N 个、过期 N 个、窗口结束 N 个、当前时刻 1 个。
        for (RateLimitScope scope : scopes) {
            ZonedDateTime start = RateLimitWindow.windowStart(scope, now, zone);
            keys.add(RateLimitWindow.key(scope, config.keyPrefix(), userId, start));
            limits.add(Integer.toString(config.limitFor(scope)));
            ttls.add(Long.toString(RateLimitWindow.ttlSeconds(scope)));
            windowEnds.add(Long.toString(start.plus(scope.windowLength()).toEpochSecond()));
        }
        List<String> args = new ArrayList<>(limits);
        args.addAll(ttls);
        args.addAll(windowEnds);
        args.add(Long.toString(now.getEpochSecond()));

        List<Long> result = redis.execute(checkAndIncrement, keys, args.toArray());
        return parse(result, scopes);
    }

    /** 脚本返回 {0} 表示通过；{1, 再试秒数, 触发范围序号} 表示拒绝，序号对应传入顺序，
     *  再试秒数与触发范围取所有超额范围里最晚重置的那一个。 */
    private static Decision parse(List<Long> result, List<RateLimitScope> scopes) {
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("限流脚本返回为空");
        }
        if (result.get(0) == 0) {
            return Decision.allow();
        }
        int retryAfterSeconds = Math.max(1, result.get(1).intValue());
        RateLimitScope blocked = scopes.get(result.get(2).intValue() - 1);
        return Decision.deny(blocked, retryAfterSeconds);
    }
}
