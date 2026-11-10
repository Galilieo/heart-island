package com.xinyu.config;

import com.xinyu.service.AiJobRuntime;
import com.xinyu.service.AiJobService;
import com.xinyu.service.AiServiceClient;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 只装载worker配置与合成依赖，验证条件接入及实际数据源超时门槛，不连接数据库。 */
class AiJobRuntimeConfigTests {
    @Test
    void workerDisabledDoesNotRecoverOrClaim() {
        AiJobService jobs = mock(AiJobService.class);
        new ApplicationContextRunner().withUserConfiguration(AiJobRuntimeConfig.class)
                .withBean(AiJobService.class, () -> jobs)
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(AiJobRuntime.class);
                    verifyNoInteractions(jobs);
                });
    }

    @Test
    void enabledWorkerRejectsUnboundedDatasourceBeforeRecovery() {
        AiJobService jobs = mock(AiJobService.class);
        runner(jobs, false).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("invalid_ai_worker_database_timeouts");
            verifyNoInteractions(jobs);
        });
    }

    @Test
    void applicationYamlActuallyBindsFiniteTimeoutsToBootDatasource() {
        AiJobService jobs = mock(AiJobService.class);
        dependencies(jobs).withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=jdbc:mysql://127.0.0.1:1/synthetic",
                        "spring.datasource.username=synthetic", "spring.datasource.password=synthetic")
                .withInitializer(context -> {
                    try {
                        var sources = new YamlPropertySourceLoader()
                                .load("application", new ClassPathResource("application.yml"));
                        sources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                    } catch (IOException missingConfiguration) {
                        throw new AssertionError(missingConfiguration);
                    }
                }).run(context -> {
                    assertThat(context).hasNotFailed();
                    HikariDataSource source = context.getBean(HikariDataSource.class);
                    assertThat(source.getConnectionTimeout()).isEqualTo(2000);
                    assertThat(source.getValidationTimeout()).isEqualTo(1000);
                    assertThat(source.getDataSourceProperties().getProperty("connectTimeout")).isEqualTo("2000");
                    assertThat(source.getDataSourceProperties().getProperty("socketTimeout")).isEqualTo("6000");
                });
    }

    @Test
    void enabledWorkerRecoversBeforeAutomaticScanning() {
        AiJobService jobs = mock(AiJobService.class);
        runner(jobs, true).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AiJobRuntime.class);
            assertThat(context.getBean(AiJobRuntime.class).isRunning()).isTrue();
            verify(jobs, times(1)).recoverInterrupted();
        });
    }

    private ApplicationContextRunner runner(AiJobService jobs, boolean bounded) {
        HikariDataSource source = new HikariDataSource();
        if (bounded) {
            source.setConnectionTimeout(2000);
            source.setValidationTimeout(1000);
            source.addDataSourceProperty("connectTimeout", "2000");
            source.addDataSourceProperty("socketTimeout", "6000");
        }
        return dependencies(jobs).withBean(HikariDataSource.class, () -> source);
    }

    private ApplicationContextRunner dependencies(AiJobService jobs) {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        AiServiceClient client = mock(AiServiceClient.class);
        try {
            when(client.awaitTermination(any())).thenReturn(true);
        } catch (InterruptedException impossible) {
            throw new AssertionError(impossible);
        }
        return new ApplicationContextRunner().withUserConfiguration(AiJobRuntimeConfig.class)
                .withPropertyValues("AI_WORKER_ENABLED=true")
                .withBean(AiJobService.class, () -> jobs)
                .withBean(AiServiceClient.class, () -> client)
                .withBean(PlatformTransactionManager.class, () -> manager);
    }
}
