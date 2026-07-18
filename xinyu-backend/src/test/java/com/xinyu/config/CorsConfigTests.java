package com.xinyu.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTests {

    @Test
    void allowsLocalDevelopmentAndProductionHttpsOrigins() {
        TestCorsRegistry registry = new TestCorsRegistry();

        new CorsConfig().corsConfigurer().addCorsMappings(registry);

        CorsConfiguration configuration = registry.configurations().get("/**");
        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly(
                "http://localhost:5173",
                "https://heart-island.cn",
                "https://www.heart-island.cn"
        );
    }

    private static final class TestCorsRegistry extends CorsRegistry {

        private Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
