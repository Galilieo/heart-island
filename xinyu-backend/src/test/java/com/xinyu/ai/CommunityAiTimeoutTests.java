package com.xinyu.ai;

import com.xinyu.service.AiServiceClient;
import com.xinyu.service.AiServiceException;
import com.xinyu.service.CommunityAiClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/** 模拟 Python 在响应前或响应体中途停顿，验证超时与本地连接关闭。 */
class CommunityAiTimeoutTests {
    private static final Duration SHORT_PHASE = Duration.ofMillis(200);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stalledPythonTimesOutAndDisconnects(boolean sendPartialBody) throws Exception {
        CompletableFuture<Boolean> disconnected = new CompletableFuture<>();
        AtomicInteger requests = new AtomicInteger();
        try (var peer = stalledPeer(disconnected, requests, sendPartialBody);
             var client = shortTimeoutClient(peer.url())) {
            long started = System.nanoTime();
            assertThatThrownBy(() -> client.generateReplyForPost("合成话题", "平静", "合成帖子"))
                    .isInstanceOf(AiServiceException.class).hasMessage("provider_timeout");
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
            assertThat(disconnected.get(2, TimeUnit.SECONDS)).isTrue();
            assertThat(requests).hasValue(1);
        }
    }

    static CommunityAiClient shortTimeoutClient(String url) {
        var limits = new AiServiceClient.Limits(SHORT_PHASE, SHORT_PHASE, Duration.ofSeconds(1));
        return new CommunityAiClient(url, CommunityAiServiceTests.TEST_TOKEN, 3, limits);
    }

    static AiServiceClientTests.Peer stalledPeer(
            CompletableFuture<Boolean> disconnected, AtomicInteger requests, boolean sendPartialBody) throws IOException {
        return new AiServiceClientTests.Peer((socket, input, wire) -> {
            requests.incrementAndGet();
            if (sendPartialBody) {
                socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                        + "Content-Length: 100\r\n\r\n{").getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
            }
            try {
                disconnected.complete(input.read() == -1);
            } catch (SocketException reset) {
                disconnected.complete(true);
            }
        });
    }
}
