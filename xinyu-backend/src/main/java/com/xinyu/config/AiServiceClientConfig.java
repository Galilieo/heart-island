package com.xinyu.config;

import com.xinyu.service.AiServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 关闭 worker 的实例不创建内部客户端，也不要求提供内部服务凭据。 */
@Configuration(proxyBeanMethods = false)
public class AiServiceClientConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "AI_WORKER_ENABLED", havingValue = "true")
    public AiServiceClient aiServiceClient(@Value("${AI_SERVICE_BASE_URL:}") String baseUrl,
                                         @Value("${AI_INTERNAL_TOKEN:}") String token,
                                         @Value("${AI_WORKER_CONCURRENCY:3}") int concurrency) {
        return new AiServiceClient(baseUrl, token, concurrency);
    }
}
