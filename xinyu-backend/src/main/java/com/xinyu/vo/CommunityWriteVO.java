package com.xinyu.vo;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/** 提交者私有保存响应。任务字段只在帖子写入时返回，评论保持原响应。 */
public record CommunityWriteVO(
        @JsonInclude(JsonInclude.Include.ALWAYS) String supportText,
        @JsonIgnore Long postId, @JsonIgnore Long jobId, @JsonIgnore String aiReplyState) {

    public CommunityWriteVO(String supportText) {
        this(supportText, null, null, null);
    }

    @JsonAnyGetter
    public Map<String, Object> postTaskFields() {
        if (postId == null) {
            return Map.of();
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("postId", postId);
        fields.put("jobId", jobId);
        fields.put("aiReplyState", aiReplyState);
        return fields;
    }
}
