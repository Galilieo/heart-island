package com.xinyu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 回顾统计的时间来源：自然周与近 30 天都按配置时区的墙上时间计算。
 * 默认 Asia/Shanghai；测试可直接注入固定 Clock，不依赖系统时区。
 */
@Configuration
public class ReviewTimeConfig {

    @Bean
    public ZoneId reviewZoneId(@Value("${xinyu.review.time-zone:Asia/Shanghai}") String timeZone) {
        return ZoneId.of(timeZone);
    }

    @Bean
    public Clock reviewClock(ZoneId reviewZoneId) {
        return Clock.system(reviewZoneId);
    }
}
