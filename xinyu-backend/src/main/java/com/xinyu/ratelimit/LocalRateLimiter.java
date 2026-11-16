package com.xinyu.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内限流，规则与 Redis 版一致（同一规则表、同一键名计算）；用于未配置 Redis 的本机开发
 * 和 Redis 故障时的降级。已知代价（契约 2.3）：降级期间计数与 Redis 互不相通，服务重启归零。
 */
public class LocalRateLimiter implements RateLimiter {

    /** 单个计数单元：窗口起点（epoch 秒）、窗口长度与该窗口内的已用次数；创建即计入本次调用。 */
    private static final class Cell {
        final long windowStartEpochSecond;
        final long windowLengthSeconds;
        long count;

        Cell(long windowStartEpochSecond, long windowLengthSeconds) {
            this.windowStartEpochSecond = windowStartEpochSecond;
            this.windowLengthSeconds = windowLengthSeconds;
            this.count = 1;
        }
    }

    /** 一次检查中单个范围的中间状态：写入时按窗口起点区分新单元与延续单元。 */
    private record ScopeState(String key, long windowStartEpochSecond, RateLimitScope scope) { }

    private final Map<String, Cell> counts = new ConcurrentHashMap<>();
    private final RateLimitProperties config;
    private final Clock clock;
    private final ZoneId zone;

    public LocalRateLimiter(RateLimitProperties config, Clock clock, ZoneId zone) {
        this.config = config;
        this.clock = clock;
        this.zone = zone;
    }

    /**
     * 多范围一起检查、一起递增；方法级同步保证“任一超额则全部不加”的原子性。
     * 降级路径吞吐远低于 Redis，粗粒度锁足够，不引入 CAS 循环。
     */
    @Override
    public synchronized Decision check(RateLimitType type, long userId) {
        Instant now = clock.instant();

        // 先只读检查：任何一个范围超额就整体拒绝，不消耗其他范围。
        List<ScopeState> states = new ArrayList<>();
        RateLimitScope blockedScope = null;
        long retryAfterSeconds = 0;
        for (RateLimitScope scope : type.scopes()) {
            ZonedDateTime start = RateLimitWindow.windowStart(scope, now, zone);
            String key = RateLimitWindow.key(scope, config.keyPrefix(), userId, start);
            long startSecond = start.toEpochSecond();
            Cell cell = counts.get(key);
            long used = cell != null && cell.windowStartEpochSecond == startSecond ? cell.count : 0;
            states.add(new ScopeState(key, startSecond, scope));
            if (used >= config.limitFor(scope)) {
                // 必须等全部超额范围都重置才能再试：触发范围与等待秒数取最晚重置者。
                long waitSeconds = RateLimitWindow.retryAfterSeconds(scope, now, zone);
                if (blockedScope == null || waitSeconds > retryAfterSeconds) {
                    blockedScope = scope;
                    retryAfterSeconds = waitSeconds;
                }
            }
        }
        if (blockedScope != null) {
            return Decision.deny(blockedScope, (int) Math.min(retryAfterSeconds, Integer.MAX_VALUE));
        }
        for (ScopeState state : states) {
            // 延续单元自增；键上遗留的旧窗口单元被新单元替换。
            counts.compute(state.key(), (ignored, existing) -> {
                if (existing == null || existing.windowStartEpochSecond != state.windowStartEpochSecond()) {
                    return new Cell(state.windowStartEpochSecond(), state.scope().windowLength().toSeconds());
                }
                existing.count++;
                return existing;
            });
        }
        return Decision.allow();
    }

    /**
     * 清理早于“上一窗口”的条目，只保留当前与上一个窗口的数据（契约 2.3）；
     * 由配置注册的调度线程周期调用，也可在测试中手动触发。
     *
     * @return 清理的条目数
     */
    public synchronized int purgeExpired() {
        long nowSecond = clock.instant().getEpochSecond();
        int removed = 0;
        Iterator<Map.Entry<String, Cell>> entries = counts.entrySet().iterator();
        while (entries.hasNext()) {
            Cell cell = entries.next().getValue();
            // 窗口起点加上两个窗口长度仍不超过当前时刻：既不是当前窗口也不是上一个窗口。
            if (cell.windowStartEpochSecond + 2 * cell.windowLengthSeconds <= nowSecond) {
                entries.remove();
                removed++;
            }
        }
        return removed;
    }

    /** 当前条目数，供测试与观测使用。 */
    public synchronized int size() {
        return counts.size();
    }
}
