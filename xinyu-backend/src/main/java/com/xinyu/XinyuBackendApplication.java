package com.xinyu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;

/**
 * 排除 Redis 自动配置：限流客户端由 RateLimitConfig 按 spring.data.redis.url 条件自建
 * （带命令超时），url 为空时完全不创建 Redis 组件；默认自动配置会无条件建各种 RedisTemplate，
 * 在没有连接工厂时导致启动失败。
 */
@SpringBootApplication(exclude = {RedisAutoConfiguration.class, RedisReactiveAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class})
public class XinyuBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(XinyuBackendApplication.class, args);
    }

}
