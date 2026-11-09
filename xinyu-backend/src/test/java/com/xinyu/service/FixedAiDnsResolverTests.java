package com.xinyu.service;

import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** 验证生成期间没有同步系统 DNS，初始化超时或失败不会变成启动错误。 */
class FixedAiDnsResolverTests {
    @Test
    void resolvesOnceAndReturnsDefensiveSnapshotWithoutFurtherLookup() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        InetAddress address = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        var resolver = FixedAiDnsResolver.snapshot("service.test", Duration.ofSeconds(1), () -> {
            lookups.incrementAndGet(); return new InetAddress[]{address};
        });
        for (int i = 0; i < 10; i++) assertThat(resolver.resolve("service.test")).containsExactly(address);
        resolver.resolve("service.test")[0] = null;
        assertThat(resolver.resolve("service.test")).containsExactly(address);
        assertThat(resolver.resolveCanonicalHostname("service.test")).isEqualTo("service.test");
        assertThat(lookups).hasValue(1);
        assertThatThrownBy(() -> resolver.resolve("another.test")).isInstanceOf(UnknownHostException.class);
    }

    @Test
    void initializationFailureIsCachedAndNeverLeaksRawHostOrError() {
        var resolver = FixedAiDnsResolver.snapshot("service.test", Duration.ofSeconds(1), () -> {
            throw new UnknownHostException("synthetic-private-error");
        });
        assertThatThrownBy(() -> resolver.resolve("service.test")).isInstanceOf(UnknownHostException.class)
                .hasMessage("ai_service_dns_unavailable").hasNoCause();
    }

    @Test
    void stalledNativeStyleLookupIsNotWaitedOnByGeneration() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), exited = new CountDownLatch(1);
        try {
            long before = System.nanoTime();
            var resolver = FixedAiDnsResolver.snapshot("service.test", Duration.ofMillis(100), () -> {
                entered.countDown();
                try {
                    // 模拟系统原生解析忽略线程中断；测试最后显式释放，不能遗留测试线程。
                    while (release.getCount() != 0) { try { release.await(); } catch (InterruptedException ignored) { } }
                    return new InetAddress[]{InetAddress.getByAddress(new byte[]{127, 0, 0, 1})};
                } finally { exited.countDown(); }
            });
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(Duration.ofNanos(System.nanoTime() - before)).isLessThan(Duration.ofSeconds(1));
            assertThat(exited.getCount()).isEqualTo(1);
            assertThatThrownBy(() -> resolver.resolve("service.test")).isInstanceOf(UnknownHostException.class);
        } finally { release.countDown(); assertThat(exited.await(1, TimeUnit.SECONDS)).isTrue(); }
    }
}
