package com.xinyu.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.xinyu.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 在真实 Spring 配置入口验证 JWT 启动保护，不连接数据库或模型。 */
@ExtendWith(OutputCaptureExtension.class)
class JwtSecretTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JwtUtil.class)
            .withInitializer(context -> {
                // 缺失配置用例不受运行者机器上的环境变量或系统属性影响。
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            });

    @Test
    void missingSecretPreventsStartup() {
        runner.run(context -> assertThat(context).hasFailed()
                .getFailure().hasRootCauseMessage("invalid_jwt_secret"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234567890123456789012345678901", "短短短短短短短短短短", "xinyu-secret-key"})
    void shortAndLegacySecretsPreventStartupWithoutEchoingValues(String secret, CapturedOutput output) {
        runner.withPropertyValues("JWT_SECRET=" + secret).run(context -> {
            assertThat(context).hasFailed().getFailure().hasRootCauseMessage("invalid_jwt_secret");
            if (!secret.isEmpty()) {
                assertThat(context.getStartupFailure()).hasStackTraceContaining("invalid_jwt_secret")
                        .hasStackTraceContaining("IllegalArgumentException");
                assertThat(output.getAll()).doesNotContain(secret);
            }
        });
    }

    @Test
    void configuredSecretSignsAndVerifiesIdentity() throws IOException {
        String secret = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"))
                .getProperty("JWT_SECRET");
        runner.withPropertyValues("JWT_SECRET=" + secret).run(context -> {
            assertThat(context).hasNotFailed();
            JwtUtil jwt = context.getBean(JwtUtil.class);
            String token = jwt.generateToken(11L, "fixture-member");
            assertThat(jwt.getUserId(token)).isEqualTo(11L);
            assertThat(jwt.getUsername(token)).isEqualTo("fixture-member");
        });
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() throws IOException {
        String secret = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"))
                .getProperty("JWT_SECRET");
        String foreignToken = JWT.create().withClaim("userId", 11L)
                .sign(Algorithm.HMAC256("different-fixture-signing-key-32-bytes"));
        runner.withPropertyValues("JWT_SECRET=" + secret).run(context -> {
            assertThat(context).hasNotFailed();
            assertThatThrownBy(() -> context.getBean(JwtUtil.class).parseToken(foreignToken))
                    .isInstanceOf(SignatureVerificationException.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678901234567890123456789012", "合成合成合成合成合成合"})
    void minimumLengthUsesUtf8Bytes(String secret) {
        runner.withPropertyValues("JWT_SECRET=" + secret).run(context -> {
            assertThat(context).hasNotFailed();
            JwtUtil jwt = context.getBean(JwtUtil.class);
            assertThat(jwt.getUserId(jwt.generateToken(11L, "fixture-member"))).isEqualTo(11L);
        });
    }
}
