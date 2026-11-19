package com.xinyu.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 社区回复成功结果，公开前须校验请求关联、模板和回复类型。 */
public record CommunityAiResponse(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("reply_content") String replyContent,
        @JsonProperty("result_kind") String resultKind,
        @JsonProperty("model_called") Boolean modelCalled,
        @JsonProperty("template_version") String templateVersion,
        @JsonProperty("model_name") String modelName) {

    private static final String TEMPLATE_VERSION = "community-reply-v1";

    /** 校验请求对应的模板与生成语义，返回清理首尾空白后的正文。 */
    public String validatedReply(String expectedRequestId) {
        if (!expectedRequestId.equals(requestId) || !TEMPLATE_VERSION.equals(templateVersion)) {
            throw ReplyResultValidation.invalid();
        }
        return ReplyResultValidation.validatedReply(replyContent, resultKind, modelCalled, modelName);
    }
}
