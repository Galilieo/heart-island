package com.xinyu.ratelimit;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 限流拒绝的进程内观测计数（契约 2.6）：按天、按范围累加，日期切换自动重置。
 * 只用于后台概览卡片，重启归零，不代表跨实例的精确统计。
 */
public class RateLimitMetrics {

    private final Clock clock;
    private LocalDate day;
    private final EnumMap<RateLimitScope, Long> counts = new EnumMap<>(RateLimitScope.class);

    public RateLimitMetrics(Clock clock) {
        this.clock = clock;
        this.day = LocalDate.now(clock);
    }

    /** 记录一次拒绝；写频率与拒绝同量级，简单同步即可。 */
    public synchronized void recordRejection(RateLimitScope scope) {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            counts.clear();
        }
        counts.merge(scope, 1L, Long::sum);
    }

    /** 今天各范围的拒绝次数快照，顺序与枚举一致；未发生过的范围为 0。 */
    public synchronized Map<String, Long> todayRejections() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            counts.clear();
        }
        Map<String, Long> snapshot = new LinkedHashMap<>();
        for (RateLimitScope scope : RateLimitScope.values()) {
            snapshot.put(scope.keyName(), counts.getOrDefault(scope, 0L));
        }
        return snapshot;
    }
}
