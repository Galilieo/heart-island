package com.xinyu.service;

import org.apache.hc.client5.http.DnsResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** 固定内部服务主机的可恢复解析器；HTTP线程只读地址，系统查询由单独线程执行。 */
final class FixedAiDnsResolver implements DnsResolver, AutoCloseable {
    private final String host;
    private final long lookupTimeoutNanos;
    private final long failureTtlNanos;
    private final LongSupplier clock;
    private final Callable<InetAddress[]> lookup;
    private final ExecutorService queries = Executors.newSingleThreadExecutor(
            work -> AiServiceClient.daemonThread(work, "ai-http-dns"));
    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1,
            work -> AiServiceClient.daemonThread(work, "ai-dns-deadline"));
    private final Object state = new Object();
    private InetAddress[] addresses = new InetAddress[0];
    private Lookup running;
    private boolean failed;
    private long retryAt;
    private boolean closed;

    FixedAiDnsResolver(String host, Duration timeout, Duration failureTtl,
                       LongSupplier clock, Callable<InetAddress[]> lookup) {
        this.host = normalized(host);
        this.lookupTimeoutNanos = timeout.toNanos();
        this.failureTtlNanos = failureTtl.toNanos();
        this.clock = clock;
        this.lookup = lookup;
        timer.setRemoveOnCancelPolicy(true);
    }

    static FixedAiDnsResolver create(String host, Duration timeout) {
        return snapshot(host, timeout, () -> InetAddress.getAllByName(host));
    }

    /** 初始化解析失败只缓存不可用状态，不阻止业务应用启动；修改服务地址后需重建客户端。 */
    static FixedAiDnsResolver snapshot(String host, Duration timeout, Callable<InetAddress[]> lookup) {
        ExecutorService initializer = Executors.newSingleThreadExecutor(
                work -> AiServiceClient.daemonThread(work, "ai-http-dns-init"));
        Future<InetAddress[]> pending = initializer.submit(lookup);
        InetAddress[] resolved = new InetAddress[0];
        try {
            InetAddress[] value = pending.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
            if (value != null) resolved = value.clone();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException unavailable) {
            // 不记录系统异常文本或猜测可用地址；generate 将得到安全的 provider_error。
        } finally {
            pending.cancel(true);
            initializer.shutdownNow();
        }
        return new FixedAiDnsResolver(host, resolved);
    }

    private FixedAiDnsResolver(String host, InetAddress[] addresses) {
        this(host, Duration.ofSeconds(2), Duration.ofSeconds(10), System::nanoTime, () -> addresses.clone());
        this.addresses = addresses;
    }

    /** 请求只登记后续动作；返回只读stage，调用方不能取消所有等待者共享的查询结果。 */
    CompletionStage<Void> prepare() {
        synchronized (state) {
            if (closed) return CompletableFuture.failedFuture(unavailable());
            if (addresses.length != 0) return CompletableFuture.completedFuture(null);
            // 实际查询尚未返回时，即使逻辑超时和失败缓存均已过期，也不启动第二项。
            if (running != null) return running.ready.minimalCompletionStage();
            if (failed && retryAt - clock.getAsLong() > 0) {
                return CompletableFuture.failedFuture(unavailable());
            }
            Lookup next = new Lookup();
            running = next;
            next.alarm = timer.schedule(() -> timeout(next), lookupTimeoutNanos, TimeUnit.NANOSECONDS);
            queries.execute(() -> resolveInBackground(next));
            return next.ready.minimalCompletionStage();
        }
    }

    private void resolveInBackground(Lookup current) {
        InetAddress[] found = null;
        try {
            InetAddress[] value = lookup.call();
            if (value != null && value.length != 0) {
                found = value.clone();
                for (InetAddress address : value) {
                    if (address == null) {
                        found = null;
                        break;
                    }
                }
            }
        } catch (Exception unavailable) {
            // 不保留系统异常、主机信息或异常链；所有等待者只收到安全失败。
        }

        boolean success;
        synchronized (state) {
            running = null;
            current.alarm.cancel(false);
            // 超时或关闭后返回的地址不复活本次查询，也不触发迟到的HTTP请求。
            if (closed || current.finished) return;
            current.finished = true;
            success = found != null;
            if (success) {
                addresses = found;
                failed = false;
            } else {
                cacheFailure();
            }
        }
        // 完成Future可能同步运行续接动作，不能持有resolver锁。
        if (success) current.ready.complete(null);
        else current.ready.completeExceptionally(unavailable());
    }

    private void timeout(Lookup current) {
        synchronized (state) {
            if (closed || current.finished) return;
            current.finished = true;
            cacheFailure();
            // 保留running，直到底层实际退出；不以逻辑超时伪装成原生查询已停止。
        }
        current.ready.completeExceptionally(unavailable());
    }

    private void cacheFailure() {
        failed = true;
        retryAt = clock.getAsLong() + failureTtlNanos;
    }

    @Override
    public InetAddress[] resolve(String requestedHost) throws UnknownHostException {
        synchronized (state) {
            if (closed || !host.equalsIgnoreCase(normalized(requestedHost)) || addresses.length == 0) {
                throw unavailable();
            }
            return addresses.clone();
        }
    }

    @Override
    public String resolveCanonicalHostname(String requestedHost) throws UnknownHostException {
        resolve(requestedHost);
        return requestedHost;
    }

    @Override
    public void close() {
        Lookup pending;
        synchronized (state) {
            if (closed) return;
            closed = true;
            pending = running;
            if (pending != null) pending.finished = true;
            timer.shutdownNow();
            queries.shutdownNow();
        }
        if (pending != null) pending.ready.completeExceptionally(unavailable());
    }

    private static UnknownHostException unavailable() {
        return new UnknownHostException("ai_service_dns_unavailable");
    }

    private static String normalized(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private static final class Lookup {
        final CompletableFuture<Void> ready = new CompletableFuture<>();
        ScheduledFuture<?> alarm;
        boolean finished;
    }
}
