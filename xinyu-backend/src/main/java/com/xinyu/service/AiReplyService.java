package com.xinyu.service;

import com.xinyu.dto.AiReplyResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiReplyService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model}")
    private String model;

    @Autowired
    private RestTemplate restTemplate;

    public String generateReply(String moodType, String content) {
        return generateReplyResult(moodType, content).getReplyContent();
    }

    public AiReplyResult generateReplyResult(String moodType, String content) {
        String prompt = buildPromptText(moodType, content);

        try {
            String replyContent = doGenerateReply(moodType, content);
            return AiReplyResult.success(replyContent, prompt, model);
        } catch (Exception e) {
            e.printStackTrace();
            return AiReplyResult.failure(getDefaultReply(), prompt, model, e.getMessage());
        }
    }

    /**
     * 为社区帖子生成 AI 回复。和 mood 版的区别：
     * - prompt 是公开社区语境，不是私人陪伴
     * - 长度限制 120 字
     * - 注入话题名以让 AI 知道讨论背景
     */
    public AiReplyResult generateReplyForPost(String topicName, String moodType, String content) {
        String prompt = buildPostPromptText(topicName, moodType, content);

        try {
            String replyContent = doGenerateReplyForPost(topicName, moodType, content);
            return AiReplyResult.success(replyContent, prompt, model);
        } catch (Exception e) {
            e.printStackTrace();
            return AiReplyResult.failure(getDefaultReply(), prompt, model, e.getMessage());
        }
    }

    private String doGenerateReplyForPost(String topicName, String moodType, String content) {
        String url = baseUrl + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", buildPostMessages(topicName, moodType, content));

        Map<String, Object> thinking = new HashMap<>();
        thinking.put("type", "disabled");
        requestBody.put("thinking", thinking);

        // 社区回复要求 120 字内，给的额度比 mood 小
        requestBody.put("max_tokens", 200);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

        if (response.getBody() == null) {
            return getDefaultReply();
        }

        return parseReply(response.getBody());
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

    /**
     * 用于记录到 ai_reply 表的 prompt 字段。汇总 system + user 消息。
     */
    private String buildPostPromptText(String topicName, String moodType, String content) {
        return "[system]\n" + buildPostSystemPrompt(topicName, moodType)
                + "\n\n[user]\n帖子内容：\n" + content;
    }

    private String doGenerateReply(String moodType, String content) {
        String url = baseUrl + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", buildMessages(moodType, content));

        /*
         * 建议先关闭 thinking，减少响应时间和成本。
         * DeepSeek V4 Flash 支持 thinking 参数；当前情绪陪伴回复不需要复杂推理。
         */
        Map<String, Object> thinking = new HashMap<>();
        thinking.put("type", "disabled");
        requestBody.put("thinking", thinking);

        requestBody.put("max_tokens", 300);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

        if (response.getBody() == null) {
            return getDefaultReply();
        }

        return parseReply(response.getBody());
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

    private String buildPromptText(String moodType, String content) {
        StringBuilder promptBuilder = new StringBuilder();

        for (Map<String, String> message : buildMessages(moodType, content)) {
            promptBuilder
                    .append(message.get("role"))
                    .append(": ")
                    .append(message.get("content"))
                    .append("\n");
        }

        return promptBuilder.toString().trim();
    }

    private String parseReply(Map responseBody) {
        Object choicesObj = responseBody.get("choices");

        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            return getDefaultReply();
        }

        Object firstChoiceObj = choices.get(0);

        if (!(firstChoiceObj instanceof Map<?, ?> firstChoice)) {
            return getDefaultReply();
        }

        Object messageObj = firstChoice.get("message");

        if (!(messageObj instanceof Map<?, ?> message)) {
            return getDefaultReply();
        }

        Object contentObj = message.get("content");

        if (contentObj == null) {
            return getDefaultReply();
        }

        String reply = contentObj.toString().trim();

        if (reply.isEmpty()) {
            return getDefaultReply();
        }

        return reply;
    }

    private String getDefaultReply() {
        return "谢谢你愿意记录下此刻的心情。无论现在感受如何，都可以先给自己一点时间慢慢整理，心屿会陪你一起慢慢来。";
    }
}
