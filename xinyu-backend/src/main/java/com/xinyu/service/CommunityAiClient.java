package com.xinyu.service;

import com.xinyu.dto.CommunityAiRequest;
import com.xinyu.dto.CommunityAiResponse;

import java.time.Duration;
import java.util.UUID;

/** 社区同步客户端，独占一套传输容量和生命周期。 */
public final class CommunityAiClient implements AutoCloseable {
    private final AiServiceClient transport;
    private final Duration total;

    /** 创建拥有独立名额和连接资源的客户端，地址和凭据在此校验。 */
    public CommunityAiClient(String baseUrl, String token, int concurrency, AiServiceClient.Limits limits) {
        transport = new AiServiceClient(baseUrl, token, concurrency, limits);
        total = limits.total();
    }

    /** 同步请求 Python；失败以内部安全分类抛出 AiServiceException。 */
    public CommunityAiResponse generateReplyForPost(String topicName, String moodType, String content) {
        var request = new CommunityAiRequest(UUID.randomUUID().toString(),
                topicName, moodType, content, CommunityAiRequest.MAX_BUDGET_MS);
        return transport.generateCommunity(request, total);
    }

    @Override
    public void close() {
        transport.close();
    }
}
