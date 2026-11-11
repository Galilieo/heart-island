package com.xinyu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xinyu.dto.AiReplyResult;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 社区同步生成与旧调用兼容入口；失败不发布兜底，诊断详情只供管理员日志使用。 */
@Service
public class AiReplyService {
    private static final Logger logger = LoggerFactory.getLogger(AiReplyService.class);
    // 保留原社区／旧心情请求参数；2000 UTF-16上限沿用1a回复正文约定，prompt仍要求社区120字。
    private static final int POST_MAX_TOKENS = 200;
    private static final int LEGACY_MOOD_MAX_TOKENS = 300;
    private static final int MAX_REPLY_UTF16 = 2000;
    private static final int NEXT_LINE = 0x85;
    // ai_reply.error_message为TEXT；限制详情规模，避免异常长响应占满调用记录。
    private static final int MAX_ERROR_DETAIL_UTF16 = 8_192;
    private static final String PROVIDER_TIMEOUT = "provider_timeout";
    private static final String PROVIDER_ERROR = "provider_error";
    private static final String INVALID_MODEL_RESPONSE = "invalid_model_response";

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model}")
    private String model;

    @Autowired
    private RestTemplate restTemplate;

    /** 旧调用兼容入口；失败返回null，不能将公开兜底当成生成成功。 */
    public String generateReply(String moodType, String content) {
        return generateReplyResult(moodType, content).getReplyContent();
    }

    public AiReplyResult generateReplyResult(String moodType, String content) {
        return generate(buildMessages(moodType, content), LEGACY_MOOD_MAX_TOKENS);
    }

    /** 社区使用原公开语境prompt；同步请求不自动重试，失败不影响已保存的帖子。 */
    public AiReplyResult generateReplyForPost(String topicName, String moodType, String content) {
        return generate(buildPostMessages(topicName, moodType, content), POST_MAX_TOKENS);
    }

    private AiReplyResult generate(List<Map<String, String>> messages, int maxTokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("thinking", Map.of("type", "disabled"));
        body.put("max_tokens", maxTokens);

        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    baseUrl + "/chat/completions", new HttpEntity<>(body, headers), JsonNode.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                return failure(PROVIDER_ERROR);
            }

            String reply = parseReply(response.getBody());
            if (reply == null) {
                return failure(INVALID_MODEL_RESPONSE);
            }

            return AiReplyResult.success(reply, null, model);
        } catch (RestClientException problem) {
            return failure(classifyFailure(problem), problem);
        } catch (RuntimeException problem) {
            return failure(PROVIDER_ERROR, problem);
        }
    }

    private static String parseReply(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }

        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }

        JsonNode first = choices.get(0);
        if (!"stop".equals(first.path("finish_reason").textValue())) {
            return null;
        }

        JsonNode content = first.path("message").path("content");
        if (!content.isTextual()) {
            return null;
        }

        String text = content.textValue().strip();
        boolean blank = text.codePoints().allMatch(unit ->
                Character.isWhitespace(unit) || Character.isSpaceChar(unit) || unit == NEXT_LINE);
        if (blank || text.length() > MAX_REPLY_UTF16) {
            return null;
        }
        return text;
    }

    private AiReplyResult failure(String code) {
        logger.warn("同步AI调用失败 code={}", code);
        return AiReplyResult.failure(null, null, model, code);
    }

    private AiReplyResult failure(String code, RuntimeException problem) {
        AiReplyResult result = failure(code);
        String detail = problem.getClass().getSimpleName() + ": " + problem.getMessage();
        if (apiKey != null && !apiKey.isBlank()) {
            detail = detail.replace(apiKey, "[REDACTED]");
        }
        if (detail.length() > MAX_ERROR_DETAIL_UTF16) {
            detail = detail.substring(0, MAX_ERROR_DETAIL_UTF16);
        }
        result.setErrorMessage(code + ": " + detail);
        return result;
    }

    private static String classifyFailure(Throwable problem) {
        for (Throwable current = problem; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof ConnectionRequestTimeoutException) {
                return PROVIDER_TIMEOUT;
            }
            if (current instanceof HttpMessageNotReadableException) {
                return INVALID_MODEL_RESPONSE;
            }
        }
        return PROVIDER_ERROR;
    }

    private List<Map<String, String>> buildPostMessages(String topicName, String moodType, String content) {
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> system = new HashMap<>();
        system.put("role", "system");
        system.put("content", buildPostSystemPrompt(topicName, moodType));
        messages.add(system);

        Map<String, String> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", "帖子内容：\n" + content);
        messages.add(user);

        return messages;
    }

    private String buildPostSystemPrompt(String topicName, String moodType) {
        String safeTopic = topicName == null || topicName.trim().isEmpty() ? "未指定话题" : topicName;
        String safeMood = moodType == null || moodType.trim().isEmpty() ? "未指定心情" : moodType;

        return "你是「心屿」，匿名情绪互助社区里的一位温柔陪伴者。"
                + "有人在话题「" + safeTopic + "」下匿名发了一段情绪为「" + safeMood + "」的内容。"
                + "请用不超过 120 字的中文，对这段帖子做一个温和、克制的回应。"
                + "要求：用「这位朋友」「写下这段话的人」之类的中性指代，"
                + "不预设性别、不评判、不诊断、不给医疗建议；"
                + "像在公开社区里轻轻接住对方一句，不要长篇大论；"
                + "不要逐字引用原话，不要使用列表、表情符号或英文。";
    }

    private List<Map<String, String>> buildMessages(String moodType, String content) {
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content",
                "你是“心屿 AI 情绪陪伴系统”的情绪陪伴助手。" +
                        "你的任务是根据用户记录的心情，生成一段简短、温和、真诚的陪伴回复。" +
                        "回复要像朋友一样理解用户，不要说教，不要鸡汤，不要夸张鼓励。" +
                        "不要做心理疾病诊断，不要提供医疗建议，不要使用“你一定会”“必须”“应该马上”等强硬表达。" +
                        "可以适当给一个很小、很轻的行动建议，但不要列清单。" +
                        "回复控制在60到100字之间，只输出回复正文。"
        );
        messages.add(systemMessage);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content",
                "用户当前心情类型：" + moodType + "\n" +
                        "用户记录的内容：" + content + "\n" +
                        "请基于用户的真实表达进行回应，先接住情绪，再给出一句轻量的陪伴或建议。"
        );
        messages.add(userMessage);

        return messages;
    }

}
