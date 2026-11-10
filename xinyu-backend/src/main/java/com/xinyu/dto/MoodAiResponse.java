package com.xinyu.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/** Python 成功结果的数据契约；提交前仍须校验字段语义及数据库当前关联状态。 */
public record MoodAiResponse(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("job_id") String jobId,
        Integer attempt,
        @JsonProperty("source_version") String sourceVersion,
        @JsonProperty("reply_content") String replyContent,
        @JsonProperty("result_kind") String resultKind,
        @JsonProperty("model_called") Boolean modelCalled,
        @JsonProperty("template_version") String templateVersion,
        @JsonProperty("model_name") String modelName) {
    /** 校验关联字段和结果语义，返回清理首尾白字符后的正文。 */
    public String validatedReply(long expectedJobId, int expectedAttempt, String expectedRequestId, long expectedVersion) {
        if (expectedJobId <= 0 || expectedVersion <= 0 || expectedAttempt < 1) {
            throw invalid();
        }
        if (expectedRequestId == null || expectedRequestId.isBlank()) {
            throw invalid();
        }

        if (!Long.toString(expectedJobId).equals(jobId)
                || !Integer.valueOf(expectedAttempt).equals(attempt)
                || !Objects.equals(expectedRequestId, requestId)
                || !Long.toString(expectedVersion).equals(sourceVersion)
                || !"mood-reply-v1".equals(templateVersion)) {
            throw invalid();
        }
        String text = stripUnicode(replyContent);
        if (text == null || text.isEmpty() || text.length() > 2000) throw invalid();
        requireValidUnicode(text);
        if ("MODEL".equals(resultKind)) {
            String name = stripUnicode(modelName);
            if (!Boolean.TRUE.equals(modelCalled) || name == null || name.isEmpty()
                    || modelName.length() > 128) throw invalid();
            requireValidUnicode(modelName);
        } else if ("SUPPORT".equals(resultKind)) {
            if (!Boolean.FALSE.equals(modelCalled) || modelName != null) throw invalid();
        } else {
            throw invalid();
        }
        return text;
    }

    /** 与 Python str.strip 的 Unicode 白字符口径保持一致。 */
    private static String stripUnicode(String value) {
        if (value == null) return null;
        int start = 0, end = value.length();
        while (start < end && isWhitespace(value.charAt(start))) start++;
        while (end > start && isWhitespace(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }

    private static boolean isWhitespace(char unit) {
        return Character.isWhitespace(unit) || Character.isSpaceChar(unit) || unit == 0x85;
    }

    private static void requireValidUnicode(String text) {
        for (int i = 0; i < text.length(); i++) {
            char unit = text.charAt(i);
            if (Character.isHighSurrogate(unit)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw invalid();
            } else if (Character.isLowSurrogate(unit)) {
                throw invalid();
            }
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid_model_response");
    }
}
