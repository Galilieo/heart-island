package com.xinyu.service;

import com.xinyu.dto.CommunityAiRequest;
import com.xinyu.dto.CommunityAiResponse;

import java.time.Duration;
import java.util.UUID;

/** 社区任务客户端，独占一套传输容量和生命周期。 */
public final class CommunityAiClient implements AutoCloseable, AiJobRuntime.Transport {
    private final AiServiceClient transport;
    private final Duration total;

    /** 创建拥有独立名额和连接资源的客户端，地址和凭据在此校验。 */
    public CommunityAiClient(String baseUrl, String token, int concurrency, AiServiceClient.Limits limits) {
        transport = new AiServiceClient(baseUrl, token, concurrency, limits);
        total = limits.total();
    }

    /** 协议和超时测试用的同步入口；应用 Worker 使用 startGeneration。 */
    public CommunityAiResponse generateReplyForPost(String topicName, String moodType, String content) {
        var request = new CommunityAiRequest(UUID.randomUUID().toString(),
                topicName, moodType, content, CommunityAiRequest.MAX_BUDGET_MS);
        return transport.generateCommunity(request, total);
    }

    /** 发送本次尝试的请求，生成期间不占数据库执行器。 */
    public AiServiceClient.Exchange<CommunityAiResponse> startGeneration(
            CommunityAiRequest request, Duration remaining) {
        return transport.startCommunityGeneration(request, remaining);
    }

    @Override
    public boolean awaitTermination(Duration remaining) throws InterruptedException {
        return transport.awaitTermination(remaining);
    }

    @Override
    public void close() {
        transport.close();
    }
}
