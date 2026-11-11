package com.xinyu.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/** 社区同步HTTP资源；独立于心情异步客户端，应用关闭时释放连接池。 */
@Configuration(proxyBeanMethods = false)
public class RestTemplateConfig {
    @Bean(destroyMethod = "close")
    public CloseableHttpClient communityHttpClient(
            @Value("${COMMUNITY_AI_CONNECT_TIMEOUT_MS:2000}") long connectMs,
            @Value("${COMMUNITY_AI_READ_TIMEOUT_MS:15000}") long readMs) {
        return createClient(Duration.ofMillis(connectMs), Duration.ofMillis(readMs));
    }

    @Bean
    public RestTemplate restTemplate(@Qualifier("communityHttpClient") CloseableHttpClient client) {
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(client));
    }

    /** 正式配置与短预算回环测试共用；只限制阶段等待，不承诺整条发帖请求的总deadline。 */
    public static CloseableHttpClient createClient(Duration connectTimeout, Duration readTimeout) {
        if (connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("invalid_community_connect_timeout");
        }
        if (readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("invalid_community_read_timeout");
        }
        var connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.of(connectTimeout))
                        .setSocketTimeout(Timeout.of(readTimeout))
                        .build())
                .build();
        return HttpClients.custom()
                .setConnectionManager(connections)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.of(connectTimeout))
                        .setResponseTimeout(Timeout.of(readTimeout))
                        .build())
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .build();
    }
}
