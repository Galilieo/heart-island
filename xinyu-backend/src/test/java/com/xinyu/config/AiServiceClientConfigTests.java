package com.xinyu.config;

import com.xinyu.service.AiServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

/** 只启动内部客户端配置，不装载业务库、用户配置或真实模型。 */
class AiServiceClientConfigTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(AiServiceClientConfig.class);

    @Test
    void disabledDoesNotRequireCredentialsOrCreateClient() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(AiServiceClient.class));
    }

    @Test
    void enabledRequiresConfigurationAndValidatesWithoutEchoingSecrets() {
        runner.withPropertyValues("AI_WORKER_ENABLED=true").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("AI_WORKER_ENABLED=true", "AI_SERVICE_BASE_URL=http://synthetic-sensitive-user@127.0.0.1:1",
                "AI_INTERNAL_TOKEN=synthetic-sensitive-token").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("invalid_ai_service_configuration")
                    .hasStackTraceContaining("invalid_ai_service_configuration");
            assertThat(context.getStartupFailure().toString()).doesNotContain("synthetic-sensitive");
        });
    }

    @Test
    void validConfigurationCreatesClientWithoutProbingService() {
        runner.withPropertyValues("AI_WORKER_ENABLED=true", "AI_SERVICE_BASE_URL=http://127.0.0.1:1",
                "AI_INTERNAL_TOKEN=synthetic-token", "AI_WORKER_CONCURRENCY=3")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(AiServiceClient.class));
    }
}
