package com.xinyu.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** 测试用可推进时钟：限流窗口切换与降级冷却都靠它驱动，不影响生产代码。 */
class MutableTestClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    MutableTestClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    void advanceTo(Instant next) {
        this.instant = next;
    }

    void advance(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    ZonedDateTime wallTime() {
        return instant.atZone(zone);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId override) {
        return new MutableTestClock(instant, override);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
