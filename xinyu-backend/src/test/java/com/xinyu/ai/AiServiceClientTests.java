package com.xinyu.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyu.dto.MoodAiRequest;
import com.xinyu.service.AiServiceClient;
import com.xinyu.service.AiServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/** 真正建立回环 HTTP；只替换 Python 服务，不替换客户端的读取、解析和取消。 */
class AiServiceClientTests {
    static final String SECRET = "synthetic-internal-secret";
    static final String GOOD = """
        {"request_id":"req-A","job_id":"10","attempt":1,"source_version":"7",
         "reply_content":"合成回复。","result_kind":"MODEL","model_called":true,
         "template_version":"mood-reply-v1","model_name":"test-model"}
        """;
    static MoodAiRequest request() {
        return new MoodAiRequest("req-A", "10", 1, "7", "平静", "仅用于回环测试的合成记录。", 40000);
    }

    @Test
    void sendsMinimalSnakeCaseWithInternalAuthAndAcceptsBothResultKinds() throws Exception {
        try (Peer peer = new Peer((socket, input, wire) -> respond(socket, 200, GOOD));
             AiServiceClient client = new AiServiceClient(peer.url(), SECRET, 3)) {
            assertThat(client.generate(request(), Duration.ofSeconds(2)).resultKind()).isEqualTo("MODEL");
            Wire wire = peer.received.get(2, TimeUnit.SECONDS);
            assertThat(wire.path()).isEqualTo("/internal/v1/mood-replies");
            assertThat(wire.headers().get("authorization")).isEqualTo("Bearer " + SECRET);
            var body = new ObjectMapper().readTree(wire.body());
            assertThat(body.properties().stream().map(Map.Entry::getKey).toList())
                    .containsExactlyInAnyOrder("request_id", "job_id", "attempt", "source_version", "mood_type", "content", "budget_ms");
            assertThat(body.get("job_id").isTextual()).isTrue();
            assertThat(body.get("attempt").isIntegralNumber()).isTrue();
            assertThat(body.get("budget_ms").intValue()).isBetween(1, 2000);
        }
        String support = GOOD.replace("\"MODEL\"", "\"SUPPORT\"").replace("\"model_called\":true", "\"model_called\":false")
                .replace("\"test-model\"", "null");
        try (Peer peer = new Peer((socket, input, wire) -> respond(socket, 200, support));
             AiServiceClient client = new AiServiceClient(peer.url(), SECRET, 3)) {
            assertThat(client.generate(request(), Duration.ofSeconds(2)).modelCalled()).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "bad-json", "duplicate", "trailing", "unknown", "missing", "integer-string",
            "integer-float", "boolean-string", "id-number", "wrong-job", "wrong-attempt", "wrong-version",
            "wrong-request", "wrong-template", "blank", "oversized-reply", "support-called", "surrogate", "oversized-body"})
    void rejectsUntrustedSuccessWithoutLeakingBody(String kind) throws Exception {
        String body = switch (kind) {
            case "empty" -> "";
            case "bad-json" -> "private-upstream-sentinel";
            case "duplicate" -> GOOD.replace("\"attempt\":1", "\"attempt\":1,\"attempt\":1");
            case "trailing" -> GOOD + " {}";
            case "unknown" -> GOOD.replace("\"attempt\":1", "\"extra\":\"private-upstream-sentinel\",\"attempt\":1");
            case "missing" -> GOOD.replace(",\"model_name\":\"test-model\"", "");
            case "integer-string" -> GOOD.replace("\"attempt\":1", "\"attempt\":\"1\"");
            case "integer-float" -> GOOD.replace("\"attempt\":1", "\"attempt\":1.0");
            case "boolean-string" -> GOOD.replace("\"model_called\":true", "\"model_called\":\"true\"");
            case "id-number" -> GOOD.replace("\"job_id\":\"10\"", "\"job_id\":10");
            case "wrong-job" -> GOOD.replace("\"job_id\":\"10\"", "\"job_id\":\"11\"");
            case "wrong-attempt" -> GOOD.replace("\"attempt\":1", "\"attempt\":2");
            case "wrong-version" -> GOOD.replace("\"source_version\":\"7\"", "\"source_version\":\"8\"");
            case "wrong-request" -> GOOD.replace("req-A", "req-B");
            case "wrong-template" -> GOOD.replace("mood-reply-v1", "unknown");
            case "blank" -> GOOD.replace("合成回复。", "   ");
            case "oversized-reply" -> GOOD.replace("合成回复。", "文".repeat(2001));
            case "support-called" -> GOOD.replace("\"MODEL\"", "\"SUPPORT\"");
            case "surrogate" -> GOOD.replace("合成回复。", "\\ud800");
            case "oversized-body" -> " ".repeat(16385);
            default -> throw new AssertionError(kind);
        };
        try (Peer peer = new Peer((socket, input, wire) -> respond(socket, 200, body));
             AiServiceClient client = new AiServiceClient(peer.url(), SECRET, 3)) {
            assertFailure(() -> client.generate(request(), Duration.ofSeconds(2)), "invalid_model_response");
        }
    }

    @ParameterizedTest
    @CsvSource({"400,invalid_request", "401,unauthorized", "504,provider_timeout", "502,provider_error",
            "502,invalid_model_response", "503,overloaded"})
    void mapsOnlyMatchingSafeErrorStatusAndCode(int status, String code) throws Exception {
        try (Peer peer = new Peer((socket, input, wire) -> respond(socket, status,
                "{\"request_id\":null,\"code\":\"" + code + "\"}"));
             AiServiceClient client = new AiServiceClient(peer.url(), SECRET, 3)) {
            assertFailure(() -> client.generate(request(), Duration.ofSeconds(2)), code);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-id", "mismatched-status", "unknown-code", "bad-types", "raw-body", "oversized-header", "many-headers"})
    void rejectsInvalidErrorsAndHeaders(String kind) throws Exception {
        try (Peer peer = new Peer((socket, input, wire) -> {
            switch (kind) {
                case "wrong-id" -> respond(socket, 502, "{\"request_id\":\"other\",\"code\":\"provider_error\"}");
                case "mismatched-status" -> respond(socket, 401, "{\"request_id\":null,\"code\":\"provider_timeout\"}");
                case "unknown-code" -> respond(socket, 502, "{\"request_id\":null,\"code\":\"secret-raw-error\"}");
                case "bad-types" -> respond(socket, 502, "{\"request_id\":42,\"code\":\"provider_error\"}");
                case "raw-body" -> respond(socket, 502, "secret-raw-error");
                case "oversized-header" -> socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nX-Large: " + "x".repeat(8300) + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                case "many-headers" -> socket.getOutputStream().write(("HTTP/1.1 200 OK\r\n" + "X-Pad: ".concat("x".repeat(1000)).concat("\r\n").repeat(9) + "\r\n").getBytes(StandardCharsets.US_ASCII));
            }
        }); AiServiceClient client = new AiServiceClient(peer.url(), SECRET, 3)) {
            assertFailure(() -> client.generate(request(), Duration.ofSeconds(2)), "invalid_model_response");
        }
    }

    @Test
    void doesNotFollowRedirectOrRetryUnknownFailure() throws Exception {
        try (Peer target = new Peer((socket, input, wire) -> respond(socket, 200, GOOD));
             Peer redirect = new Peer((socket, input, wire) -> socket.getOutputStream().write(
                     ("HTTP/1.1 302 Found\r\nLocation: " + target.url() + "/elsewhere\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.US_ASCII)));
             AiServiceClient client = new AiServiceClient(redirect.url(), SECRET, 3)) {
            assertFailure(() -> client.generate(request(), Duration.ofSeconds(2)), "invalid_model_response");
            assertThat(target.connections.get()).isZero();
        }
        try (Peer peer = new Peer((socket, input, wire) -> respond(socket, 500, "synthetic-server-fault"));
             AiServiceClient client = new AiServiceClient(peer.url(), SECRET, 3)) {
            assertFailure(() -> client.generate(request(), Duration.ofSeconds(2)), "provider_error");
            assertThat(peer.connections.get()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deadlineClosesActualConnectionAndSameClientCanContinue(boolean slowBody) throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CompletableFuture<Boolean> disconnected = new CompletableFuture<>();
        try (Peer peer = new Peer((socket, input, wire) -> {
            if (attempts.incrementAndGet() > 1) { respond(socket, 200, GOOD); return; }
            if (slowBody) {
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 10000\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                Thread writer = new Thread(() -> {
                    try { while (!socket.isClosed()) { socket.getOutputStream().write(' '); socket.getOutputStream().flush(); Thread.sleep(20); } }
                    catch (Exception ignored) { }
                });
                writer.setDaemon(true); writer.start();
            }
            try { disconnected.complete(input.read() == -1); }
            catch (SocketException reset) { disconnected.complete(true); }
        }); AiServiceClient client = new AiServiceClient(peer.url(), SECRET, 1)) {
            long start = System.nanoTime();
            assertFailure(() -> client.generate(request(), Duration.ofMillis(600)), "provider_timeout");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
            assertThat(disconnected.get(2, TimeUnit.SECONDS)).isTrue();
            assertThat(client.generate(request(), Duration.ofSeconds(2)).resultKind()).isEqualTo("MODEL");
        }
    }

    @Test
    void closeCancelsInflightAndRejectsFurtherCalls() throws Exception {
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try (Peer peer = new Peer((socket, input, wire) -> { try { input.read(); } catch (SocketException ignored) { } });
             AiServiceClient client = new AiServiceClient(peer.url(), SECRET, 1)) {
            Future<?> running = caller.submit(() -> assertFailure(() -> client.generate(request(), Duration.ofSeconds(5)), "execution_interrupted"));
            peer.received.get(2, TimeUnit.SECONDS);
            client.close();
            running.get(2, TimeUnit.SECONDS);
            assertFailure(() -> client.generate(request(), Duration.ofSeconds(2)), "execution_interrupted");
        } finally { caller.shutdownNow(); }
    }

    @Test
    void exhaustedBudgetAndInvalidInputNeverOpenConnection() throws Exception {
        try (Peer peer = new Peer((socket, input, wire) -> respond(socket, 200, GOOD));
             AiServiceClient client = new AiServiceClient(peer.url(), SECRET, 1)) {
            assertFailure(() -> client.generate(request(), Duration.ZERO), "provider_timeout");
            assertFailure(() -> client.generate(new MoodAiRequest("req-A", "10", 1, "7", "unknown", "text", 40000), Duration.ofSeconds(2)), "invalid_request");
            assertThat(peer.connections.get()).isZero();
        }
    }

    @Test
    void capsLargeCallerBudgetBeforeConvertingToNanoseconds() throws Exception {
        try (Peer peer = new Peer((socket, input, wire) -> respond(socket, 200, GOOD));
             AiServiceClient client = new AiServiceClient(peer.url(), SECRET, 1)) {
            // 调用方给出的时间再大，也应先收紧到客户端上限，不能在单位转换时溢出。
            assertThat(client.generate(request(), Duration.ofSeconds(Long.MAX_VALUE)).resultKind())
                    .isEqualTo("MODEL");
        }
    }

    @Test
    void refusedConnectionIsSafeProviderError() throws Exception {
        int port;
        try (ServerSocket reserved = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { port = reserved.getLocalPort(); }
        try (AiServiceClient client = new AiServiceClient("http://127.0.0.1:" + port, SECRET, 1)) {
            assertFailure(() -> client.generate(request(), Duration.ofSeconds(2)), "provider_error");
        }
    }

    @Test
    void readWaitIsBoundedIndependentlyOfTotalBudget() throws Exception {
        try (Peer peer = new Peer((socket, input, wire) -> { try { input.read(); } catch (SocketException ignored) { } });
             AiServiceClient client = new AiServiceClient(peer.url(), SECRET, 1,
                     new AiServiceClient.Limits(Duration.ofSeconds(1), Duration.ofMillis(100), Duration.ofSeconds(3)))) {
            long before = System.nanoTime();
            assertFailure(() -> client.generate(request(), Duration.ofSeconds(3)), "provider_timeout");
            assertThat(Duration.ofNanos(System.nanoTime() - before)).isLessThan(Duration.ofSeconds(2));
        }
    }

    @Test
    void oversizedChunkedBodyIsAbortedWithoutWaitingForRemainder() throws Exception {
        CompletableFuture<Boolean> disconnected = new CompletableFuture<>();
        try (Peer peer = new Peer((socket, input, wire) -> {
            var out = socket.getOutputStream();
            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\n\r\n4001\r\n".getBytes(StandardCharsets.US_ASCII));
            out.write(new byte[16385]); out.write("\r\n".getBytes(StandardCharsets.US_ASCII)); out.flush();
            // 故意不发下一块和结束块，确认客户端不会在异常清理时继续排空正文。
            try { disconnected.complete(input.read() == -1); }
            catch (SocketException reset) { disconnected.complete(true); }
        }); AiServiceClient client = new AiServiceClient(peer.url(), SECRET, 1)) {
            assertFailure(() -> client.generate(request(), Duration.ofSeconds(2)), "invalid_model_response");
            assertThat(disconnected.get(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"blank", "long", "emoji-long", "bad-unicode", "zero-id", "overflow-id", "bad-request", "attempt-zero", "budget-zero"})
    void invalidRequestBoundariesNeverReachPeer(String kind) throws Exception {
        MoodAiRequest r = switch (kind) {
            case "blank" -> new MoodAiRequest("req-A", "10", 1, "7", "平静", "\u0085\u00a0\u3000", 40000);
            case "long" -> new MoodAiRequest("req-A", "10", 1, "7", "平静", "文".repeat(501), 40000);
            case "emoji-long" -> new MoodAiRequest("req-A", "10", 1, "7", "平静", "😀".repeat(251), 40000);
            case "bad-unicode" -> new MoodAiRequest("req-A", "10", 1, "7", "平静", "\ud800", 40000);
            case "zero-id" -> new MoodAiRequest("req-A", "0", 1, "7", "平静", "text", 40000);
            case "overflow-id" -> new MoodAiRequest("req-A", "9223372036854775808", 1, "7", "平静", "text", 40000);
            case "bad-request" -> new MoodAiRequest("req\nA", "10", 1, "7", "平静", "text", 40000);
            case "attempt-zero" -> new MoodAiRequest("req-A", "10", 0, "7", "平静", "text", 40000);
            case "budget-zero" -> new MoodAiRequest("req-A", "10", 1, "7", "平静", "text", 0);
            default -> throw new AssertionError(kind);
        };
        try (Peer peer = new Peer((socket, input, wire) -> respond(socket, 200, GOOD));
             AiServiceClient client = new AiServiceClient(peer.url(), SECRET, 1)) {
            assertFailure(() -> client.generate(r, Duration.ofSeconds(2)), "invalid_request");
            assertThat(peer.connections.get()).isZero();
        }
    }

    static void assertFailure(Throwing action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(AiServiceException.class).hasMessage(code)
                .hasNoCause().hasMessageNotContaining(SECRET).hasMessageNotContaining("secret-raw-error");
    }
    interface Throwing { void run() throws Exception; }
    interface Reply { void send(Socket socket, InputStream input, Wire wire) throws Exception; }
    record Wire(String path, Map<String, String> headers, byte[] body) { }

    /** 用原始 socket 观察真实关闭，避免把服务端自己超时关闭误当作客户端取消。 */
    static final class Peer implements AutoCloseable {
        final ServerSocket server;
        final ExecutorService threads = Executors.newCachedThreadPool();
        final AtomicInteger connections = new AtomicInteger();
        final CompletableFuture<Wire> received = new CompletableFuture<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Peer(Reply reply) throws IOException {
            server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
            threads.submit(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket socket = server.accept(); connections.incrementAndGet();
                        threads.submit(() -> {
                            try (socket) {
                                socket.setSoTimeout(4000);
                                InputStream input = socket.getInputStream();
                                StringBuilder head = new StringBuilder();
                                while (!head.toString().endsWith("\r\n\r\n")) {
                                    int value = input.read(); if (value < 0) throw new EOFException(); head.append((char) value);
                                }
                                String[] lines = head.toString().split("\r\n");
                                Map<String, String> headers = new HashMap<>();
                                for (int i = 1; i < lines.length; i++) {
                                    String[] parts = lines[i].split(":", 2); headers.put(parts[0].toLowerCase(Locale.ROOT), parts[1].trim());
                                }
                                byte[] body = input.readNBytes(Integer.parseInt(headers.getOrDefault("content-length", "0")));
                                Wire wire = new Wire(lines[0].split(" ")[1], headers, body);
                                received.complete(wire); reply.send(socket, input, wire);
                            } catch (Throwable problem) { failure.compareAndSet(null, problem); }
                        });
                    } catch (SocketException stopping) { if (!server.isClosed()) failure.compareAndSet(null, stopping); }
                    catch (IOException problem) { failure.compareAndSet(null, problem); }
                }
            });
        }
        String url() { return "http://127.0.0.1:" + server.getLocalPort(); }
        public void close() throws Exception { server.close(); threads.shutdownNow(); assertThat(threads.awaitTermination(6, TimeUnit.SECONDS)).isTrue(); }
    }
    static void respond(Socket socket, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        socket.getOutputStream().write(("HTTP/1.1 " + status + " Test\r\nContent-Type: application/json\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().write(body); socket.getOutputStream().flush();
    }
}
