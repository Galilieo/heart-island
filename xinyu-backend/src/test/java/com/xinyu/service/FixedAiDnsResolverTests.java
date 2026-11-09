package com.xinyu.service;

import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

/** 验证共享解析、失败恢复及逻辑超时与物理查询生命周期的区别。 */
class FixedAiDnsResolverTests {
    @Test
    void resolvesOnceAndReturnsDefensiveSnapshotWithoutFurtherLookup() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        InetAddress address = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        try (var resolver = new FixedAiDnsResolver("service.test", Duration.ofSeconds(1), Duration.ofSeconds(10),
                System::nanoTime, () -> {
                    lookups.incrementAndGet();
                    return new InetAddress[]{address};
                })) {
            resolver.prepare().toCompletableFuture().get(1, TimeUnit.SECONDS);
            for (int i = 0; i < 10; i++) assertThat(resolver.resolve("service.test")).containsExactly(address);
            resolver.resolve("service.test")[0] = null;
            assertThat(resolver.resolve("service.test")).containsExactly(address);
            assertThat(resolver.resolveCanonicalHostname("service.test")).isEqualTo("service.test");
            assertThat(lookups).hasValue(1);
            assertThatThrownBy(() -> resolver.resolve("another.test")).isInstanceOf(UnknownHostException.class);
        }
    }

    @Test
    void failureIsSafeAndCallerCannotCancelSharedLookup() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        try (var resolver = new FixedAiDnsResolver("service.test", Duration.ofSeconds(1), Duration.ofSeconds(10),
                System::nanoTime, () -> {
                    gate.await();
                    throw new UnknownHostException("synthetic-private-error");
                })) {
            var a = resolver.prepare().toCompletableFuture();
            var b = resolver.prepare().toCompletableFuture();
            a.cancel(true);
            gate.countDown();
            assertThatThrownBy(() -> b.get(1, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOf(UnknownHostException.class).hasMessage("ai_service_dns_unavailable").hasNoCause();
            assertThatThrownBy(() -> resolver.resolve("service.test")).isInstanceOf(UnknownHostException.class)
                    .hasMessage("ai_service_dns_unavailable").hasNoCause();
        } finally { gate.countDown(); }
    }

    @Test
    void stalledNativeLookupCannotSpawnMoreLookupsAfterLogicalTimeoutOrCacheExpiry() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), exited = new CountDownLatch(1);
        AtomicInteger lookups = new AtomicInteger();
        AtomicLong clock = new AtomicLong();
        try (var resolver = new FixedAiDnsResolver("service.test", Duration.ofMillis(80), Duration.ofSeconds(10),
                clock::get, () -> {
                    lookups.incrementAndGet();
                    entered.countDown();
                    try {
                        awaitIgnoringInterrupt(release);
                        return loopback();
                    } finally { exited.countDown(); }
                })) {
            var first = resolver.prepare().toCompletableFuture();
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> first.get(1, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            clock.addAndGet(Duration.ofSeconds(11).toNanos());
            for (int i = 0; i < 100; i++) {
                assertThatThrownBy(() -> resolver.prepare().toCompletableFuture().get(1, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class);
            }
            assertThat(lookups).hasValue(1);
            resolver.close();
            assertThat(exited.getCount()).isEqualTo(1);
        } finally {
            release.countDown();
            assertThat(exited.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }
    private static InetAddress[] loopback() throws Exception {
        return new InetAddress[]{InetAddress.getByAddress(new byte[]{127, 0, 0, 1})};
    }

    private static void awaitIgnoringInterrupt(CountDownLatch gate) {
        while (gate.getCount() != 0) {
            try { gate.await(); } catch (InterruptedException ignored) { }
        }
    }
}
