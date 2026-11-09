package com.xinyu.service;

import org.apache.hc.client5.http.DnsResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.*;

/** 固定内部服务地址在初始化时解析一次；生成线程只读快照，不等待不可中断的系统 DNS。 */
final class FixedAiDnsResolver implements DnsResolver {
    private final String host;
    private final InetAddress[] addresses;

    static FixedAiDnsResolver create(String host, Duration timeout) {
        return snapshot(host, timeout, () -> InetAddress.getAllByName(host));
    }

    /** 初始化解析失败只缓存不可用状态，不阻止业务应用启动；修改服务地址后需重建客户端。 */
    static FixedAiDnsResolver snapshot(String host, Duration timeout, Callable<InetAddress[]> lookup) {
        ExecutorService initializer = Executors.newSingleThreadExecutor(work -> {
            Thread thread = new Thread(work, "ai-http-dns-init"); thread.setDaemon(true); return thread;
        });
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
        this.host = normalized(host);
        this.addresses = addresses;
    }

    @Override
    public InetAddress[] resolve(String requestedHost) throws UnknownHostException {
        if (!host.equalsIgnoreCase(normalized(requestedHost)) || addresses.length == 0) {
            throw new UnknownHostException("ai_service_dns_unavailable");
        }
        return addresses.clone();
    }

    @Override
    public String resolveCanonicalHostname(String requestedHost) throws UnknownHostException {
        resolve(requestedHost);
        return requestedHost;
    }

    private static String normalized(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }
}
