package com.xinyu.service;

import com.sun.net.httpserver.HttpServer;
import com.xinyu.dto.MoodAiRequest;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/** 注入可控DNS，HTTP仍走真实回环连接；不访问Python、模型或业务库。 */
class AiServiceClientDnsTests {
    private static final Duration TTL = Duration.ofSeconds(10);
    private static final MoodAiRequest REQUEST = new MoodAiRequest("req-A", "10", 1, "7", "平静", "合成记录", 40000);

    @Test
    void completionCanImmediatelyChainAnotherRequestWithOneHttpThread() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        try (Peer peer = new Peer(); AiServiceClient client = new AiServiceClient(peer.url(), "synthetic-token", 1,
                AiServiceClient.Limits.PRODUCTION, System::nanoTime, TTL, () -> {
                    gate.await();
                    return loopback();
                })) {
            var first = client.generateAsync(REQUEST, Duration.ofSeconds(3));
            var second = first.thenCompose(value -> client.generateAsync(REQUEST, Duration.ofSeconds(3)));
            gate.countDown();
            assertThat(second.get(3, TimeUnit.SECONDS).resultKind()).isEqualTo("MODEL");
            assertThat(peer.calls).hasValue(2);
        } finally { gate.countDown(); }
    }

    @Test
    void threeRequestsShareLookupAndReturnCallerThreadBeforeDnsFinishes() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger lookups = new AtomicInteger();
        try (Peer peer = new Peer(); AiServiceClient client = client(peer, System::nanoTime, () -> {
            lookups.incrementAndGet();
            entered.countDown();
            release.await();
            return loopback();
        })) {
            var a = client.generateAsync(REQUEST, Duration.ofSeconds(3));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            var b = client.generateAsync(REQUEST, Duration.ofSeconds(3));
            var c = client.generateAsync(REQUEST, Duration.ofSeconds(3));
            assertThat(a).isNotDone();
            assertThat(b).isNotDone();
            assertThat(c).isNotDone();
            assertThat(peer.calls).hasValue(0);
            assertThat(client.httpActiveCount()).as("DNS等待不能占用HTTP执行线程").isZero();
            assertFailure(client.generateAsync(REQUEST, Duration.ofSeconds(3)), "overloaded");
            release.countDown();
            for (var future : new CompletableFuture[]{a, b, c}) {
                assertThat(future.get(3, TimeUnit.SECONDS)).isNotNull();
            }
            assertThat(peer.calls).hasValue(3);
            assertThat(lookups).hasValue(1);
            assertThat(peer.host).startsWith("service.test:");
        } finally { release.countDown(); }
    }

    @Test
    void lookupFailureFansOutAndFailureCacheExpiresAtTenSeconds() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        AtomicLong clock = new AtomicLong(System.nanoTime());
        CountDownLatch release = new CountDownLatch(1);
        try (Peer peer = new Peer(); AiServiceClient client = client(peer, clock::get, () -> {
            if (lookups.incrementAndGet() == 1) {
                release.await();
                throw new UnknownHostException("private-synthetic-dns-error");
            }
            return loopback();
        })) {
            var a = client.generateAsync(REQUEST, Duration.ofSeconds(3));
            var b = client.generateAsync(REQUEST, Duration.ofSeconds(3));
            var c = client.generateAsync(REQUEST, Duration.ofSeconds(3));
            release.countDown();
            for (var future : new CompletableFuture[]{a, b, c}) assertFailure(future, "provider_error");
            clock.addAndGet(TTL.toNanos() - 1);
            assertFailure(client.generateAsync(REQUEST, Duration.ofSeconds(3)), "provider_error");
            assertThat(lookups).hasValue(1);
            clock.incrementAndGet();
            assertThat(client.generateAsync(REQUEST, Duration.ofSeconds(3)).get(3, TimeUnit.SECONDS).resultKind()).isEqualTo("MODEL");
            assertThat(lookups).hasValue(2);
            assertThat(peer.calls).hasValue(1);
        } finally { release.countDown(); }
    }

    @Test
    void oneTimeoutAndOneCancellationDoNotCancelOtherDnsWaiter() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (Peer peer = new Peer(); AiServiceClient client = client(peer, System::nanoTime, () -> {
            release.await();
            return loopback();
        })) {
            var a = client.generateAsync(REQUEST, Duration.ofMillis(100));
            var b = client.generateAsync(REQUEST, Duration.ofSeconds(3));
            var c = client.generateAsync(REQUEST, Duration.ofSeconds(3));
            assertThat(c.cancel(true)).isTrue();
            assertFailure(a, "provider_timeout");
            release.countDown();
            assertThat(b.get(3, TimeUnit.SECONDS).resultKind()).isEqualTo("MODEL");
            assertThat(peer.calls).hasValue(1);
        } finally { release.countDown(); }
    }

    @Test
    void dnsWaitConsumesOriginalBudgetInsteadOfRestartingIt() throws Exception {
        AtomicLong clock = new AtomicLong(System.nanoTime());
        CountDownLatch gate = new CountDownLatch(1);
        try (Peer peer = new Peer(); AiServiceClient client = client(peer, clock::get, () -> {
            gate.await();
            return loopback();
        })) {
            var pending = client.generateAsync(REQUEST, Duration.ofSeconds(3));
            clock.addAndGet(Duration.ofSeconds(2).toNanos());
            gate.countDown();
            assertThat(pending.get(3, TimeUnit.SECONDS).resultKind()).isEqualTo("MODEL");
            assertThat(peer.budgetMs).isBetween(1, 1000);
        } finally { gate.countDown(); }
    }

    @Test
    void lateDnsSuccessDoesNotSendTimedOutRequestButCanServeNewRequest() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        try (Peer peer = new Peer(); AiServiceClient client = client(peer, System::nanoTime, () -> {
            gate.await();
            return loopback();
        })) {
            var expired = client.generateAsync(REQUEST, Duration.ofMillis(80));
            assertFailure(expired, "provider_timeout");
            var valid = client.generateAsync(REQUEST, Duration.ofSeconds(3));
            gate.countDown();
            assertThat(valid.get(3, TimeUnit.SECONDS).resultKind()).isEqualTo("MODEL");
            assertThat(peer.calls).hasValue(1);
        } finally { gate.countDown(); }
    }

    @Test
    void closeEndsWaitersAndLateDnsCannotStartHttp() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), exited = new CountDownLatch(1);
        try (Peer peer = new Peer(); AiServiceClient client = client(peer, System::nanoTime, () -> {
            entered.countDown();
            try { awaitIgnoringInterrupt(release); return loopback(); }
            finally { exited.countDown(); }
        })) {
            var pending = client.generateAsync(REQUEST, Duration.ofSeconds(3));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            client.close();
            assertFailure(pending, "execution_interrupted");
            release.countDown();
            assertThat(exited.await(1, TimeUnit.SECONDS)).isTrue();
            assertFailure(client.generateAsync(REQUEST, Duration.ofSeconds(3)), "execution_interrupted");
            assertThat(peer.calls).hasValue(0);
        } finally { release.countDown(); }
    }

    static AiServiceClient client(Peer peer, java.util.function.LongSupplier clock, Callable<InetAddress[]> lookup) {
        return new AiServiceClient(peer.url(), "synthetic-token", 3, AiServiceClient.Limits.PRODUCTION, clock, TTL, lookup);
    }

    static InetAddress[] loopback() throws Exception {
        return new InetAddress[]{InetAddress.getByAddress(new byte[]{127, 0, 0, 1})};
    }

    static void assertFailure(CompletableFuture<?> future, String code) {
        assertThatThrownBy(() -> future.get(3, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).cause()
                .isInstanceOf(AiServiceException.class).hasMessage(code).hasNoCause();
    }

    static void awaitIgnoringInterrupt(CountDownLatch gate) {
        while (gate.getCount() != 0) {
            try { gate.await(); } catch (InterruptedException ignored) { }
        }
    }

    static final class Peer implements AutoCloseable {
        final HttpServer server;
        final ExecutorService threads = Executors.newFixedThreadPool(3);
        final AtomicInteger calls = new AtomicInteger();
        volatile String host;
        volatile int budgetMs;

        Peer() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(threads);
            server.createContext("/internal/v1/mood-replies", exchange -> {
                calls.incrementAndGet();
                host = exchange.getRequestHeaders().getFirst("Host");
                byte[] requestBody = exchange.getRequestBody().readAllBytes();
                budgetMs = new com.fasterxml.jackson.databind.ObjectMapper().readTree(requestBody).get("budget_ms").intValue();
                byte[] body = ("{\"request_id\":\"req-A\",\"job_id\":\"10\",\"attempt\":1,\"source_version\":\"7\","
                        + "\"reply_content\":\"合成回复\",\"result_kind\":\"MODEL\",\"model_called\":true,"
                        + "\"template_version\":\"mood-reply-v1\",\"model_name\":\"synthetic\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
                exchange.close();
            });
            server.start();
        }

        String url() { return "http://service.test:" + server.getAddress().getPort(); }

        @Override public void close() { server.stop(0); threads.shutdownNow(); }
    }
}
