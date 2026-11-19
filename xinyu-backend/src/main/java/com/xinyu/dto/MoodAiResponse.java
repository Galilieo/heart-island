package com.xinyu.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Set;

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
    /**
     * 滚动升级期间 Java 与 Python 的模板版本可能不一致，两者都接受；
     * v1 保留到确认不再有旧 Python 实例后再移除。部署顺序：先 Java，后 Python
     * （见 ops/features/ai-reply-service-v1a.md 第 6 节契约）。
     */
    private static final Set<String> ACCEPTED_TEMPLATE_VERSIONS = Set.of("mood-reply-v1", "mood-reply-v2");
    /** 校验关联字段和结果语义，返回清理首尾白字符后的正文。 */
    public String validatedReply(long expectedJobId, int expectedAttempt, String expectedRequestId, long expectedVersion) {
        if (expectedJobId <= 0 || expectedVersion <= 0 || expectedAttempt < 1) {
            throw ReplyResultValidation.invalid();
        }
        if (expectedRequestId == null || expectedRequestId.isBlank()) {
            throw ReplyResultValidation.invalid();
        }

        if (!Long.toString(expectedJobId).equals(jobId)
                || !Integer.valueOf(expectedAttempt).equals(attempt)
                || !Objects.equals(expectedRequestId, requestId)
                || !Long.toString(expectedVersion).equals(sourceVersion)
                || !ACCEPTED_TEMPLATE_VERSIONS.contains(templateVersion)) {
            throw ReplyResultValidation.invalid();
        }
        return ReplyResultValidation.validatedReply(replyContent, resultKind, modelCalled, modelName);
    }
}
