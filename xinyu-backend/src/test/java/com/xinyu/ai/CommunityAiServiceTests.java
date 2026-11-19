package com.xinyu.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyu.dto.CommunityAiRequest;
import com.xinyu.service.AiServiceClient;
import com.xinyu.service.AiServiceException;
import com.xinyu.service.MoodReviewSafety;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.xinyu.ai.AiServiceClientTests.respond;
import static org.assertj.core.api.Assertions.*;

class CommunityAiServiceTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    static final String TEST_TOKEN = "synthetic-community-internal-token";

    @ParameterizedTest
    @ValueSource(strings = {"旧标签", "\u3000"})
    void sendsOnlyGenerationInputsAndCapsPythonBudget(String moodType) throws Exception {
        try (var peer = new AiServiceClientTests.Peer((socket, input, wire) ->
                respond(socket, 200, success("request-1", "MODEL", " 合成模型回复 ")));
             var client = new AiServiceClient(peer.url(), TEST_TOKEN, 3, AiServiceClient.Limits.COMMUNITY)) {
            var request = new CommunityAiRequest("request-1", "", moodType, "合成正文", 15000);
            var result = client.generateCommunity(request, Duration.ofSeconds(18));

            assertThat(result.replyContent()).isEqualTo("合成模型回复");
            assertThat(result.modelCalled()).isTrue();
            var received = peer.received.get(2, TimeUnit.SECONDS);
            assertThat(received.path()).isEqualTo("/internal/v1/community-replies");
            assertThat(received.headers().get("authorization")).isEqualTo("Bearer " + TEST_TOKEN);
            assertThat(JSON.readTree(received.body())).isEqualTo(JSON.valueToTree(Map.of(
                    "request_id", "request-1", "topic_name", "", "mood_type", moodType,
                    "content", "合成正文", "budget_ms", 15000)));
        }
    }

    @Test
    void supportRequiresFixedTextAndUsesRemainingBudget() throws Exception {
        try (var peer = new AiServiceClientTests.Peer((socket, input, wire) ->
                respond(socket, 200, success("request-1", "SUPPORT", MoodReviewSafety.SUPPORT_TEXT)));
             var client = new AiServiceClient(peer.url(), TEST_TOKEN, 3, AiServiceClient.Limits.COMMUNITY)) {
            var result = client.generateCommunity(request(), Duration.ofMillis(1400));

            assertThat(result.replyContent()).isEqualTo(MoodReviewSafety.SUPPORT_TEXT);
            assertThat(result.resultKind()).isEqualTo("SUPPORT");
            assertThat(result.modelCalled()).isFalse();
            var received = peer.received.get(2, TimeUnit.SECONDS);
            assertThat(JSON.readTree(received.body()).path("budget_ms").asInt()).isBetween(1, 1260);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "400,invalid_request", "401,unauthorized", "502,provider_error",
            "502,invalid_model_response", "503,overloaded", "504,provider_timeout"
    })
    void returnsOnlyValidatedErrorCategory(int status, String code) throws Exception {
        try (var peer = new AiServiceClientTests.Peer((socket, input, wire) ->
                respond(socket, status, "{\"request_id\":\"request-1\",\"code\":\"" + code + "\"}"));
             var client = new AiServiceClient(peer.url(), TEST_TOKEN, 3)) {
            assertThatThrownBy(() -> client.generateCommunity(request(), Duration.ofSeconds(2)))
                    .isInstanceOf(AiServiceException.class).hasMessage(code);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"extra", "modelFlag", "wrongId", "supportText", "template", "privateError"})
    void rejectsInvalidSuccessOrErrorWithoutLeakingBody(String variant) throws Exception {
        String model = success("request-1", "MODEL", "合成正文");
        String body = switch (variant) {
            case "extra" -> model.replaceFirst("\\}$", ",\"job_id\":\"1\"}");
            case "modelFlag" -> model.replace("true", "false");
            case "wrongId" -> model.replace("request-1", "another-request");
            case "supportText" -> success("request-1", "SUPPORT", "不是固定文案");
            case "template" -> model.replace("community-reply-v1", "mood-reply-v2");
            case "privateError" -> "上游原文哨兵";
            default -> throw new AssertionError(variant);
        };
        int status = "privateError".equals(variant) ? 502 : 200;
        try (var peer = new AiServiceClientTests.Peer((socket, input, wire) -> respond(socket, status, body));
             var client = new AiServiceClient(peer.url(), TEST_TOKEN, 3)) {
            assertThatThrownBy(() -> client.generateCommunity(request(), Duration.ofSeconds(2)))
                    .isInstanceOf(AiServiceException.class).hasMessage("invalid_model_response");
        }
    }

    @Test
    void fullClientRejectsWithoutSendingAnotherRequest() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (var peer = new AiServiceClientTests.Peer((socket, input, wire) -> {
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            respond(socket, 200, success("request-1", "MODEL", "合成回复"));
        }); var client = new AiServiceClient(peer.url(), TEST_TOKEN, 1)) {
            var first = CompletableFuture.supplyAsync(() -> client.generateCommunity(request(), Duration.ofSeconds(5)));
            try {
                peer.received.get(2, TimeUnit.SECONDS);
                assertThatThrownBy(() -> client.generateCommunity(request(), Duration.ofSeconds(5)))
                        .isInstanceOf(AiServiceException.class).hasMessage("overloaded");
                assertThat(peer.connections).hasValue(1);
            } finally {
                release.countDown();
            }
            assertThat(first.get(2, TimeUnit.SECONDS).replyContent()).isEqualTo("合成回复");
        }
    }

    private static CommunityAiRequest request() {
        return new CommunityAiRequest("request-1", "", "旧标签", "合成正文", 15000);
    }

    static String success(String requestId, String kind, String reply) throws Exception {
        boolean called = "MODEL".equals(kind);
        var body = JSON.createObjectNode().put("request_id", requestId).put("reply_content", reply)
                .put("result_kind", kind).put("model_called", called).put("template_version", "community-reply-v1");
        if (called) {
            body.put("model_name", "synthetic-model");
        } else {
            body.putNull("model_name");
        }
        return JSON.writeValueAsString(body);
    }
}
