package com.xinyu.config;

import com.xinyu.service.AiServiceClient;
import com.xinyu.service.CommunityAiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityAiClientConfigTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(AiServiceClientConfig.class)
            .withPropertyValues("AI_WORKER_ENABLED=false");

    @Test
    void createsCommunityClientWithWorkerDisabled() {
        context.withPropertyValues("AI_SERVICE_BASE_URL=http://127.0.0.1:1",
                        "AI_INTERNAL_TOKEN=fixture-only-token")
                .run(app -> {
                    assertThat(app).hasSingleBean(CommunityAiClient.class);
                    assertThat(app).doesNotHaveBean(AiServiceClient.class);
                });
    }

    @ParameterizedTest
    @CsvSource({
            "'',fixture-only-token", "http://127.0.0.1:1,''",
            "ftp://127.0.0.1:1,fixture-only-token", "http://127.0.0.1:1,invalid token",
            "http://127.0.0.1:0,fixture-only-token"
    })
    void invalidOrMissingInternalConfigurationFailsAtStartup(String url, String token) {
        context.withPropertyValues("AI_SERVICE_BASE_URL=" + url, "AI_INTERNAL_TOKEN=" + token)
                .run(app -> assertThat(app).getFailure().hasRootCauseMessage("invalid_ai_service_configuration"));
    }
}
