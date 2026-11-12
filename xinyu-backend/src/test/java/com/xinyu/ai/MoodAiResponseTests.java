package com.xinyu.ai;

import com.xinyu.dto.MoodAiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/** 校验 Python 回包的关联身份、结果语义与 UTF-16 边界，不访问模型或数据库。 */
class MoodAiResponseTests {
    MoodAiResponse result(String text, String kind, Boolean called, String model) {
        return new MoodAiResponse("req-A", "10", 1, "7", text, kind, called, "mood-reply-v1", model);
    }

    @Test
    void normalizesUnicodeWhitespaceAndAcceptsBothKinds() {
        assertThat(result("\u0085\u00a0合成回复。\u3000", "MODEL", true, "test-model")
                .validatedReply(10, 1, "req-A", 7)).isEqualTo("合成回复。");
        assertThat(result("支持回复", "SUPPORT", false, null)
                .validatedReply(10, 1, "req-A", 7)).isEqualTo("支持回复");
        assertThat(result("😀".repeat(1000), "MODEL", true, "test-model")
                .validatedReply(10, 1, "req-A", 7)).hasSize(2000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\u0085\u00a0\u3000", "\uD800", "\uDC00"})
    void rejectsBlankAndUnpairedSurrogates(String text) {
        assertThatThrownBy(() -> result(text, "MODEL", true, "test-model").validatedReply(10, 1, "req-A", 7))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("invalid_model_response");
    }

    @Test
    void rejectsOversizedReplyAndContradictoryResultMetadata() {
        for (MoodAiResponse r : new MoodAiResponse[]{
                result("😀".repeat(1001), "MODEL", true, "test-model"),
                result(null, "MODEL", true, "test-model"),
                result("回复", "MODEL", false, "test-model"),
                result("回复", "MODEL", true, " "),
                result("回复", "MODEL", true, "x".repeat(129)),
                result("回复", "SUPPORT", true, null),
                result("回复", "SUPPORT", false, "test-model"),
                result("回复", "UNKNOWN", false, null)}) {
            assertThatThrownBy(() -> r.validatedReply(10, 1, "req-A", 7))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("invalid_model_response");
        }
    }

    @Test
    void rejectsEveryMismatchedCorrelationField() {
        MoodAiResponse r = result("回复", "MODEL", true, "test-model");
        assertThatThrownBy(() -> r.validatedReply(11, 1, "req-A", 7)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r.validatedReply(10, 2, "req-A", 7)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r.validatedReply(10, 1, "req-B", 7)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r.validatedReply(10, 1, "req-A", 8)).isInstanceOf(IllegalArgumentException.class);
        MoodAiResponse badTemplate = new MoodAiResponse("req-A", "10", 1, "7", "回复", "MODEL", true, "unknown", "m");
        assertThatThrownBy(() -> badTemplate.validatedReply(10, 1, "req-A", 7)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsBothTemplateVersionsDuringRollingUpgrade() {
        // 滚动升级期间 Python 可能回 v1 或 v2，两者都须接受；未知版本仍拒绝（见 rejectsEveryMismatchedCorrelationField）。
        MoodAiResponse v2 = new MoodAiResponse("req-A", "10", 1, "7", "回复", "MODEL", true, "mood-reply-v2", "test-model");
        assertThat(v2.validatedReply(10, 1, "req-A", 7)).isEqualTo("回复");
    }
}
