package com.xinyu.ai;

import com.xinyu.config.RestTemplateConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 上游使用真实回环TCP，验证读等待到期后请求退出并断开；不访问模型。 */
class CommunityAiTimeoutTests {
    @Test
    void stalledUpstreamTimesOutAndDisconnectsWithoutFallback() throws Exception {
        CompletableFuture<Boolean> disconnected = new CompletableFuture<>();
        AtomicInteger requests = new AtomicInteger();
        try (AiServiceClientTests.Peer peer = stalledPeer(disconnected, requests, false);
             CloseableHttpClient transport = RestTemplateConfig.createClient(
                     Duration.ofMillis(150), Duration.ofMillis(150))) {
            assertTimeoutResult(peer, transport, disconnected, requests);
        }
    }

    @Test
    void stalledResponseBodyIsAlsoTimeoutRatherThanInvalidJson() throws Exception {
        CompletableFuture<Boolean> disconnected = new CompletableFuture<>();
        AtomicInteger requests = new AtomicInteger();
        try (AiServiceClientTests.Peer peer = stalledPeer(disconnected, requests, true);
             CloseableHttpClient transport = RestTemplateConfig.createClient(
                     Duration.ofMillis(150), Duration.ofMillis(150))) {
            assertTimeoutResult(peer, transport, disconnected, requests);
        }
    }

    static AiServiceClientTests.Peer stalledPeer(
            CompletableFuture<Boolean> disconnected, AtomicInteger requests, boolean sendPartialBody) throws IOException {
        return new AiServiceClientTests.Peer((socket, input, wire) -> {
            requests.incrementAndGet();
            // RestTemplate使用chunked请求；先消费完请求体，再观察客户端因读超时断开。
            if ("chunked".equals(wire.headers().get("transfer-encoding"))) {
                consumeChunks(input);
            }
            if (sendPartialBody) {
                socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                        + "Content-Length: 100\r\n\r\n{").getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
            }
            try {
                disconnected.complete(input.read() == -1);
            } catch (SocketException reset) {
                // 超时关闭也可能发送RST；同样证明客户端已结束这条TCP连接。
                disconnected.complete(true);
            }
        });
    }

    private static void assertTimeoutResult(AiServiceClientTests.Peer peer, CloseableHttpClient transport,
            CompletableFuture<Boolean> disconnected, AtomicInteger requests) throws Exception {
        RestTemplate http = new RestTemplate(new HttpComponentsClientHttpRequestFactory(transport));
        var service = CommunityAiServiceTests.service(http);
        ReflectionTestUtils.setField(service, "baseUrl", peer.url());
        long started = System.nanoTime();
        var result = service.generateReplyForPost("合成话题", "平静", "合成帖子");

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getReplyContent()).isNull();
        assertThat(result.getErrorCode()).isEqualTo("provider_timeout");
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
        assertThat(disconnected.get(2, TimeUnit.SECONDS)).isTrue();
        assertThat(requests).hasValue(1);
    }

    private static void consumeChunks(InputStream input) throws IOException {
        while (true) {
            int length = Integer.parseInt(readLine(input), 16);
            input.readNBytes(length);
            readLine(input);
            if (length == 0) {
                return;
            }
        }
    }

    private static String readLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = input.read()) != -1 && value != '\n') {
            if (value != '\r') {
                line.append((char) value);
            }
        }
        return line.toString();
    }
}
