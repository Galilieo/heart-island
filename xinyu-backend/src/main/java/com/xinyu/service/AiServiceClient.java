package com.xinyu.service;

import com.xinyu.dto.MoodAiRequest;
import com.xinyu.dto.MoodAiResponse;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** 内部生成调用入口；资源在客户端关闭时统一回收，业务事务由调用方管理。 */
public final class AiServiceClient implements AutoCloseable {
    public record Limits(Duration connect, Duration read, Duration total) {
        public static final Limits PRODUCTION = new Limits(Duration.ofSeconds(2), Duration.ofSeconds(45), Duration.ofSeconds(50));

        public Limits {
            if (connect == null || read == null || total == null || connect.isNegative() || connect.isZero()
                    || read.isNegative() || read.isZero() || total.isNegative() || total.isZero()
                    || connect.compareTo(Duration.ofSeconds(2)) > 0 || read.compareTo(Duration.ofSeconds(45)) > 0
                    || total.compareTo(Duration.ofSeconds(50)) > 0) throw new IllegalArgumentException("invalid_ai_service_configuration");
        }
    }

    private static final int BODY_LIMIT = 16 * 1024;
    private static final Set<String> SUCCESS_FIELDS = Set.of("request_id", "job_id", "attempt", "source_version",
            "reply_content", "result_kind", "model_called", "template_version", "model_name");
    private static final Set<String> MOODS = Set.of("开心", "满足", "期待", "感动", "平静", "焦虑", "难过", "生气", "委屈", "孤独", "迷茫", "疲惫", "其他");
    private static final Map<String, Integer> ERRORS = Map.of("invalid_request", 400, "unauthorized", 401,
            "provider_timeout", 504, "provider_error", 502, "invalid_model_response", 502, "overloaded", 503);
    private final URI endpoint;
    private final String token;
    private final Limits limits;
    private final LongSupplier clock;
    private final CloseableHttpClient http;
    private final ScheduledThreadPoolExecutor deadlines;
    private final Object lifecycle = new Object();
    private final Set<HttpPost> active = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    // 独立解析器不继承全局 Long 序列化或宽松转换配置，禁止重复键和尾随 JSON。
    private final ObjectMapper json = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(BODY_LIMIT).maxNumberLength(32).build())
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public AiServiceClient(String baseUrl, String token, int concurrency) {
        this(baseUrl, token, concurrency, Limits.PRODUCTION);
    }

    public AiServiceClient(String baseUrl, String token, int concurrency, Limits limits) {
        this(baseUrl, token, concurrency, limits, System::nanoTime);
    }

    public AiServiceClient(String baseUrl, String token, int concurrency, Limits limits, LongSupplier clock) {
        try {
            URI base = URI.create(baseUrl);
            if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) || base.getHost() == null
                    || base.getUserInfo() != null || base.getRawQuery() != null || base.getRawFragment() != null
                    || base.getPort() > 65535 || concurrency < 1 || limits == null || clock == null
                    || token == null || token.isEmpty() || token.chars().anyMatch(c -> c < 33 || c > 126)) {
                throw new IllegalArgumentException();
            }
            endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/internal/v1/mood-replies");
        } catch (RuntimeException invalid) {
            // 不附带原始配置或异常链，避免启动失败时回显密钥和地址中的凭据。
            throw new IllegalArgumentException("invalid_ai_service_configuration");
        }
        this.token = token;
        this.limits = limits;
        this.clock = clock;
        var factory = ManagedHttpClientConnectionFactory.builder()
                .http1Config(Http1Config.custom().setMaxLineLength(8192).setMaxHeaderCount(128).build())
                .responseParserFactory(BoundedAiResponseParser::create).build();
        var connections = PoolingHttpClientConnectionManagerBuilder.create().setConnectionFactory(factory)
                .setDnsResolver(FixedAiDnsResolver.create(endpoint.getHost(), limits.connect()))
                .setMaxConnTotal(concurrency).setMaxConnPerRoute(concurrency)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(Math.max(1, limits.connect().toMillis())))
                        .setSocketTimeout(Timeout.ofMilliseconds(Math.max(1, limits.read().toMillis()))).build()).build();
        http = HttpClients.custom().setConnectionManager(connections)
                .disableAutomaticRetries().disableRedirectHandling().disableContentCompression().disableCookieManagement().build();
        deadlines = new ScheduledThreadPoolExecutor(1, work -> {
            Thread thread = new Thread(work, "ai-http-deadline"); thread.setDaemon(true); return thread;
        });
        deadlines.setRemoveOnCancelPolicy(true);
    }

    public MoodAiResponse generate(MoodAiRequest request, Duration remaining) {
        if (Thread.currentThread().isInterrupted() || closed) throw failure("execution_interrupted");
        if (remaining == null || remaining.isNegative() || remaining.isZero()) throw failure("provider_timeout");
        long allotted = remaining.compareTo(limits.total()) < 0 ? remaining.toNanos() : limits.total().toNanos();
        long deadline = clock.getAsLong() + allotted;
        validateInput(request);
        // 为协议处理留少量本地余量；这是本服务初值，不是供应商 SLA 或数据库提交预算。
        long millis = Math.max(0, deadline - clock.getAsLong()) / 1_000_000;
        int budget = (int) Math.min(request.budgetMs(), Math.min(40000, millis - Math.min(250, millis / 10)));
        if (budget < 1) throw failure("provider_timeout");
        MoodAiRequest input = new MoodAiRequest(request.requestId(), request.jobId(), request.attempt(),
                request.sourceVersion(), request.moodType(), request.content(), budget);
        HttpPost post = new HttpPost(endpoint);
        try {
            post.setHeader("Authorization", "Bearer " + token);
            post.setHeader("Accept", "application/json");
            post.setEntity(new ByteArrayEntity(json.writeValueAsBytes(input), ContentType.APPLICATION_JSON));
        } catch (IOException invalid) { throw failure("invalid_request"); }
        post.setConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(Math.max(1, millis)))
                .setResponseTimeout(Timeout.ofMilliseconds(Math.max(1, Math.min(limits.read().toMillis(), millis))))
                .build());
        AtomicBoolean expired = new AtomicBoolean();
        ScheduledFuture<?> alarm;
        synchronized (lifecycle) {
            if (closed) throw failure("execution_interrupted");
            active.add(post);
            // 定时器直接取消底层请求；执行调用仍在当前线程，退出后才由 worker 归还槽位。
            alarm = deadlines.schedule(() -> { expired.set(true); post.cancel(); }, Math.max(0, deadline - clock.getAsLong()), TimeUnit.NANOSECONDS);
        }
        try {
            checkDeadline(deadline, expired);
            MoodAiResponse result = http.execute(post, response -> {
                try {
                    MoodAiResponse parsed = parse(response, request);
                    checkDeadline(deadline, expired);
                    return parsed;
                } catch (IOException | RuntimeException problem) {
                    // 防止响应处理器关闭时继续消费未知／超大剩余正文。
                    post.cancel();
                    throw problem;
                }
            });
            checkDeadline(deadline, expired);
            return result;
        } catch (AiServiceException safe) {
            throw safe;
        } catch (Exception problem) {
            if (closed || Thread.currentThread().isInterrupted()) throw failure("execution_interrupted");
            if (expired.get() || clock.getAsLong() - deadline >= 0 || hasCause(problem, SocketTimeoutException.class)) throw failure("provider_timeout");
            if (hasCause(problem, MessageConstraintException.class)) throw failure("invalid_model_response");
            throw failure("provider_error");
        } finally {
            alarm.cancel(false);
            active.remove(post);
        }
    }

    private MoodAiResponse parse(ClassicHttpResponse response, MoodAiRequest request) throws IOException {
        int status = response.getCode();
        if (status >= 500 && status != 502 && status != 503 && status != 504) throw failure("provider_error");
        if (status != 200 && status != 400 && status != 401 && status != 502 && status != 503 && status != 504) throw failure("invalid_model_response");
        if (response.getEntity() == null || response.getEntity().getContentLength() > BODY_LIMIT) throw failure("invalid_model_response");
        String type = response.getEntity().getContentType();
        if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) throw failure("invalid_model_response");
        // 先检查累计字节再交还响应资源；提前 close 流可能继续排空超大剩余正文。
        InputStream stream = response.getEntity().getContent();
        byte[] body = stream.readNBytes(BODY_LIMIT + 1);
        if (body.length > BODY_LIMIT) throw failure("invalid_model_response");
        JsonNode root;
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
            root = json.readTree(text);
        } catch (IOException invalid) { throw failure("invalid_model_response"); }
        if (status != 200) {
            requireFields(root, Set.of("request_id", "code"));
            if (!root.get("code").isTextual() || !(root.get("request_id").isNull() || root.get("request_id").isTextual())) throw failure("invalid_model_response");
            String id = root.get("request_id").textValue(), code = root.get("code").textValue();
            if ((id != null && !id.equals(request.requestId())) || !Objects.equals(ERRORS.get(code), status)) throw failure("invalid_model_response");
            throw failure(code);
        }
        requireFields(root, SUCCESS_FIELDS);
        for (String field : SUCCESS_FIELDS) {
            JsonNode value = root.get(field);
            boolean valid = switch (field) {
                case "attempt" -> value.isIntegralNumber() && value.canConvertToInt();
                case "model_called" -> value.isBoolean();
                case "model_name" -> value.isNull() || value.isTextual();
                default -> value.isTextual();
            };
            if (!valid) throw failure("invalid_model_response");
        }
        MoodAiResponse result = json.treeToValue(root, MoodAiResponse.class);
        try {
            result.validatedReply(Long.parseLong(request.jobId()), request.attempt(), request.requestId(), Long.parseLong(request.sourceVersion()));
        } catch (IllegalArgumentException invalid) { throw failure("invalid_model_response"); }
        return result;
    }

    private static void requireFields(JsonNode root, Set<String> expected) {
        if (root == null || !root.isObject()) throw failure("invalid_model_response");
        Set<String> actual = new HashSet<>(); root.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw failure("invalid_model_response");
    }

    private static void validateInput(MoodAiRequest request) {
        try {
            if (request == null || request.requestId() == null || !request.requestId().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
                    || request.jobId() == null || !request.jobId().matches("[1-9][0-9]{0,18}") || Long.parseLong(request.jobId()) <= 0
                    || request.sourceVersion() == null || !request.sourceVersion().matches("[1-9][0-9]{0,18}") || Long.parseLong(request.sourceVersion()) <= 0
                    || request.attempt() < 1 || request.attempt() > 3 || !MOODS.contains(request.moodType() == null ? "" : request.moodType())
                    || request.budgetMs() < 1 || request.budgetMs() > 40000 || request.content() == null
                    || request.content().length() > 500 || request.content().codePoints().noneMatch(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c) && c != 0x85)) throw failure("invalid_request");
            for (int i = 0; i < request.content().length(); i++) {
                char unit = request.content().charAt(i);
                if (Character.isHighSurrogate(unit)) {
                    if (++i >= request.content().length() || !Character.isLowSurrogate(request.content().charAt(i))) throw failure("invalid_request");
                } else if (Character.isLowSurrogate(unit)) throw failure("invalid_request");
            }
        } catch (NumberFormatException invalid) { throw failure("invalid_request"); }
    }

    private void checkDeadline(long deadline, AtomicBoolean expired) {
        if (closed || Thread.currentThread().isInterrupted()) throw failure("execution_interrupted");
        if (expired.get() || clock.getAsLong() - deadline >= 0) throw failure("provider_timeout");
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) if (type.isInstance(current)) return true;
        return false;
    }

    private static AiServiceException failure(String code) { return new AiServiceException(code); }

    @Override
    public void close() {
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            active.forEach(HttpPost::cancel);
            deadlines.shutdownNow();
        }
        http.close(CloseMode.IMMEDIATE);
    }
}
