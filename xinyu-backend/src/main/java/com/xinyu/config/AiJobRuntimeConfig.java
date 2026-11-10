package com.xinyu.config;

import com.xinyu.service.AiJobRuntime;
import com.xinyu.service.AiJobService;
import com.xinyu.service.AiServiceClient;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 仅启用 worker 时接入自动生命周期；复用业务数据源，拒绝会覆盖有限等待配置的 JDBC 参数。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "AI_WORKER_ENABLED", havingValue = "true")
public class AiJobRuntimeConfig {
    // 与 application.yml 的默认值一致；较短的值可用于隔离测试，不允许放宽停止等待预算。
    private static final int CONNECTION_WAIT_MS = 2000;
    private static final int VALIDATION_WAIT_MS = 1000;
    private static final int SOCKET_WAIT_MS = 6000;
    private static final String CONNECT_TIMEOUT_PARAM = "connectTimeout";
    private static final String SOCKET_TIMEOUT_PARAM = "socketTimeout";
    private static final Duration SCAN_INTERVAL = Duration.ofSeconds(1);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(25);

    @Bean
    public AiJobRuntime aiJobRuntime(AiJobService jobs, AiServiceClient client,
                                     PlatformTransactionManager manager, DataSource dataSource,
                                     @Value("${AI_WORKER_CONCURRENCY:3}") int concurrency) {
        requireBoundedDatabase(dataSource);
        return new AiJobRuntime(jobs, client, manager, concurrency, SCAN_INTERVAL, SHUTDOWN_TIMEOUT);
    }

    /** 数据源必须能限制连接获取、验证和 JDBC 网络读取，否则停止路径无法在预算内退出。 */
    private static void requireBoundedDatabase(DataSource dataSource) {
        if (!(dataSource instanceof HikariDataSource source)) {
            throw invalidTimeouts();
        }
        requireTimeout(source.getConnectionTimeout(), CONNECTION_WAIT_MS);
        requireTimeout(source.getValidationTimeout(), VALIDATION_WAIT_MS);
        requireTimeout(source.getDataSourceProperties().getProperty(CONNECT_TIMEOUT_PARAM), CONNECTION_WAIT_MS);
        requireTimeout(source.getDataSourceProperties().getProperty(SOCKET_TIMEOUT_PARAM), SOCKET_WAIT_MS);
        requireUrlOverrides(source.getJdbcUrl());
    }

    /** URL 中的同名参数可能覆盖 dataSourceProperties；逐项核验，不记录 URL 或配置值。 */
    private static void requireUrlOverrides(String url) {
        if (url == null || !url.contains("?")) {
            return;
        }
        String query = url.substring(url.indexOf('?') + 1);
        for (String parameter : query.split("&")) {
            requireUrlParameter(parameter);
        }
    }

    private static void requireUrlParameter(String parameter) {
        String[] pair = parameter.split("=", 2);
        String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
        boolean connectTimeout = key.equalsIgnoreCase(CONNECT_TIMEOUT_PARAM);
        boolean socketTimeout = key.equalsIgnoreCase(SOCKET_TIMEOUT_PARAM);
        if (!connectTimeout && !socketTimeout) {
            return;
        }
        if (pair.length != 2) {
            throw invalidTimeouts();
        }
        String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
        requireTimeout(value, connectTimeout ? CONNECTION_WAIT_MS : SOCKET_WAIT_MS);
    }

    private static void requireTimeout(String value, int maximumMs) {
        try {
            requireTimeout(Long.parseLong(value), maximumMs);
        } catch (NumberFormatException invalid) {
            throw invalidTimeouts();
        }
    }

    private static void requireTimeout(long valueMs, int maximumMs) {
        if (valueMs < 1 || valueMs > maximumMs) {
            throw invalidTimeouts();
        }
    }

    private static IllegalArgumentException invalidTimeouts() {
        return new IllegalArgumentException("invalid_ai_worker_database_timeouts");
    }
}
