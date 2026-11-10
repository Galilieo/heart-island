package com.xinyu.ai;

import com.xinyu.service.AiJobRetryPolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 只验证累计尝试次数到冷却秒数的业务规则，不使用数据库、时钟或模型。 */
class AiJobRetryPolicyTests {
    @ParameterizedTest(name = "累计尝试{0}次，冷却{1}秒")
    @CsvSource({
            "0, 0",
            "1, 0",
            "2, 0",
            "3, 180",
            "4, 0",
            "5, 0",
            "6, 360",
            "9, 720",
            "12, 1440",
            "15, 2880",
            "18, 3600",
            "19, 0",
            "21, 3600",
            "30, 3600"
    })
    void cooldownDependsOnCompletedFailureRound(int attemptCount, int expectedSeconds) {
        assertThat(AiJobRetryPolicy.cooldownSeconds(attemptCount)).isEqualTo(expectedSeconds);
    }
}
