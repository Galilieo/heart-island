package com.xinyu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** 两端共享正反例与固定支持文案，防止1b后续调整只修改一端。 */
class MoodReviewSafetyTests {
    @Test
    void sharedCorpusAndSupportTextMatchPythonContract() throws Exception {
        var corpus = new ObjectMapper().readTree(Path.of("../test-contracts/review-safety-cases.json").toFile());
        assertThat(MoodReviewSafety.SUPPORT_TEXT).isEqualTo(corpus.get("supportText").asText());
        for (var entry : corpus.get("cases")) {
            assertThat(MoodReviewSafety.matches(entry.get("content").asText()))
                    .as(entry.get("content").asText()).isEqualTo(entry.get("support").asBoolean());
        }
    }
}
