package com.xinyu.config;

import com.xinyu.service.AiServiceClient;
import com.xinyu.service.CommunityAiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

/** 社区客户端始终创建。心情和回顾客户端只在启用 worker 时创建。 */
@Configuration(proxyBeanMethods = false)
public class AiServiceClientConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "AI_WORKER_ENABLED", havingValue = "true")
    public AiServiceClient aiServiceClient(@Value("${AI_SERVICE_BASE_URL:}") String baseUrl,
                                         @Value("${AI_INTERNAL_TOKEN:}") String token,
                                         @Value("${AI_WORKER_CONCURRENCY:3}") int concurrency,
                                         @Value("${AI_DNS_FAILURE_CACHE_SECONDS:10}") long dnsFailureCacheSeconds) {
        return new AiServiceClient(baseUrl, token, concurrency, AiServiceClient.Limits.PRODUCTION,
                System::nanoTime, Duration.ofSeconds(dnsFailureCacheSeconds));
    }

    @Bean(destroyMethod = "close")
    public CommunityAiClient communityAiClient(@Value("${AI_SERVICE_BASE_URL:}") String baseUrl,
                                               @Value("${AI_INTERNAL_TOKEN:}") String token,
                                               @Value("${AI_COMMUNITY_CONCURRENCY:3}") int concurrency) {
        return new CommunityAiClient(baseUrl, token, concurrency, AiServiceClient.Limits.COMMUNITY);
    }
}
