package com.xinyu.service;

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
        try {
            return doGenerateReply(moodType, content);
        } catch (Exception e) {
            e.printStackTrace();
            return getDefaultReply();
        }
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