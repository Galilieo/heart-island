package com.xinyu.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 回顾输出只接收正文与 P.S.；数字始终读取 Java 保存的统计快照。 */
public record ReviewAiResponse(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("job_id") String jobId,
        int attempt,
        @JsonProperty("source_version") String sourceVersion,
        String body, String ps,
        @JsonProperty("result_kind") String resultKind,
        @JsonProperty("model_called") boolean modelCalled,
        @JsonProperty("template_version") String templateVersion,
        @JsonProperty("model_name") String modelName) {
    /** 阶段2方案第6节的正文/P.S.上限，模型名沿用内部响应契约。 */
    private static final int MAX_WEEK_BODY_UTF16 = 220;
    private static final int MAX_RECENT_BODY_UTF16 = 260;
    private static final int MAX_PS_UTF16 = 60;
    private static final int MAX_MODEL_NAME_UTF16 = 128;
    private static final String INVALID_MODEL_RESPONSE = "invalid_model_response";
    private static final Pattern REFERENCE = Pattern.compile("\\{\\{(r[1-9][0-9]*)}}");

    /** 验证尝试身份、结果语义、UTF-16长度及引用归属，失败不保留响应正文。 */
    public void validate(ReviewAiRequest request) {
        boolean correlation = Objects.equals(requestId, request.requestId()) && Objects.equals(jobId, request.jobId())
                && attempt == request.attempt() && Objects.equals(sourceVersion, request.sourceVersion());
        boolean model = "MODEL".equals(resultKind) && modelCalled && modelName != null
                && !modelName.isBlank() && modelName.length() <= MAX_MODEL_NAME_UTF16;
        boolean support = "SUPPORT".equals(resultKind) && !modelCalled && modelName == null && ps == null;
        int limit = "WEEK".equals(request.reviewType()) ? MAX_WEEK_BODY_UTF16 : MAX_RECENT_BODY_UTF16;
        if (!correlation || !"mood-review-v1".equals(templateVersion) || (!model && !support)
                || body == null || body.isBlank() || body.length() > limit || (ps != null && ps.length() > MAX_PS_UTF16)) {
            throw new IllegalArgumentException(INVALID_MODEL_RESPONSE);
        }
        Set<String> allowed = request.records().stream().map(ReviewAiRequest.Entry::ref).collect(java.util.stream.Collectors.toSet());
        for (String text : new String[]{body, ps == null ? "" : ps}) {
            var matcher = REFERENCE.matcher(text);
            while (matcher.find()) {
                if (!model || !allowed.contains(matcher.group(1))) {
                    throw new IllegalArgumentException(INVALID_MODEL_RESPONSE);
                }
            }
            String stripped = REFERENCE.matcher(text).replaceAll("");
            if (stripped.contains("{{") || stripped.contains("}}")) {
                throw new IllegalArgumentException(INVALID_MODEL_RESPONSE);
            }
        }
    }
}
