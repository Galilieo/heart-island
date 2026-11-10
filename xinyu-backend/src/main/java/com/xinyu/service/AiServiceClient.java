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
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Java 调用 Python 内部生成服务的 HTTP 客户端。
 *
 * <p>每次 {@link #generateAsync} 都受同一个总截止时间约束：DNS、连接、读取和本地校验都计入其中，
 * 到期由定时器直接取消底层请求。失败只抛出带安全分类码的 {@link AiServiceException}，
 * 不携带原始响应、密钥或异常链。资源在 {@link #close} 时统一回收，业务事务由调用方管理。</p>
 */
public final class AiServiceClient implements AutoCloseable {

    /** response给出业务结果；terminated在本次HTTP执行及资源清理结束后完成，供worker归还槽位。 */
    public record Generation(CompletableFuture<MoodAiResponse> response, CompletableFuture<Void> terminated) { }

    /** 单次调用的时间上限；只允许收紧，不允许超过生产初值。 */
    public record Limits(Duration connect, Duration read, Duration total) {
        private static final Duration MAX_CONNECT = Duration.ofSeconds(2);
        private static final Duration MAX_READ = Duration.ofSeconds(45);
        private static final Duration MAX_TOTAL = Duration.ofSeconds(50);
        public static final Limits PRODUCTION = new Limits(MAX_CONNECT, MAX_READ, MAX_TOTAL);

        public Limits {
            if (!isWithin(connect, MAX_CONNECT) || !isWithin(read, MAX_READ) || !isWithin(total, MAX_TOTAL)) {
                throw new IllegalArgumentException(INVALID_CONFIGURATION);
            }
        }

        private static boolean isWithin(Duration value, Duration max) {
            return value != null && !value.isNegative() && !value.isZero() && value.compareTo(max) <= 0;
        }
    }

    // ---- 对外错误分类码 ----
    private static final String INVALID_CONFIGURATION = "invalid_ai_service_configuration";
    private static final String EXECUTION_INTERRUPTED = "execution_interrupted";
    private static final String INVALID_REQUEST = "invalid_request";
    private static final String PROVIDER_TIMEOUT = "provider_timeout";
    private static final String PROVIDER_ERROR = "provider_error";
    private static final String INVALID_MODEL_RESPONSE = "invalid_model_response";

    /** Python 允许返回的错误码及其必须搭配的 HTTP 状态码；不一致即视为响应不可信。 */
    private static final Map<String, Integer> ERROR_STATUS = Map.of(
            "invalid_request", 400,
            "unauthorized", 401,
            "provider_timeout", 504,
            "provider_error", 502,
            "invalid_model_response", 502,
            "overloaded", 503);
    private static final Set<Integer> ACCEPTED_STATUS = Set.of(200, 400, 401, 502, 503, 504);

    // ---- 协议与请求边界 ----
    private static final String ENDPOINT_PATH = "/internal/v1/mood-replies";
    private static final int BODY_LIMIT_BYTES = 16 * 1024;
    private static final int MAX_HEADER_LINE = 8192;
    private static final int MAX_HEADER_COUNT = 128;
    private static final int MAX_JSON_DEPTH = 16;
    private static final int MAX_JSON_NUMBER_LENGTH = 32;
    private static final int MAX_BUDGET_MS = 40_000;
    /** 给协议处理留的本地余量：剩余时间的 10%，最多 250ms。是本服务初值，不是供应商 SLA。 */
    private static final long LOCAL_MARGIN_CAP_MS = 250;
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_ATTEMPT = 3;
    private static final String REQUEST_ID_PATTERN = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}";
    private static final String POSITIVE_LONG_PATTERN = "[1-9][0-9]{0,18}";
    private static final Set<String> MOODS = Set.of(
            "开心", "满足", "期待", "感动", "平静", "焦虑", "难过", "生气", "委屈", "孤独", "迷茫", "疲惫", "其他");

    private static final Set<String> ERROR_FIELDS = Set.of("request_id", "code");
    private static final Set<String> SUCCESS_FIELDS = Set.of("request_id", "job_id", "attempt", "source_version",
            "reply_content", "result_kind", "model_called", "template_version", "model_name");

    private final URI endpoint;
    private final String token;
    private final Limits limits;
    private final LongSupplier clock;
    private final CloseableHttpClient http;
    private final ScheduledThreadPoolExecutor deadlines;
    private final Object lifecycle = new Object();
    private final Set<Call> active = ConcurrentHashMap.newKeySet();
    private final FixedAiDnsResolver dns;
    private final ThreadPoolExecutor httpWorkers;
    private final int concurrency;
    private static final Duration DEFAULT_DNS_FAILURE_TTL = Duration.ofSeconds(10);
    private volatile boolean closed;

    /** 独立解析器：不继承全局 Long 序列化或宽松转换配置，禁止重复键和尾随 JSON。 */
    private final ObjectMapper json = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(MAX_JSON_DEPTH)
                    .maxStringLength(BODY_LIMIT_BYTES)
                    .maxNumberLength(MAX_JSON_NUMBER_LENGTH)
                    .build())
            .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public AiServiceClient(String baseUrl, String token, int concurrency) {
        this(baseUrl, token, concurrency, Limits.PRODUCTION);
    }

    public AiServiceClient(String baseUrl, String token, int concurrency, Limits limits) {
        this(baseUrl, token, concurrency, limits, System::nanoTime);
    }

    public AiServiceClient(String baseUrl, String token, int concurrency, Limits limits, LongSupplier clock) {
        this(baseUrl, token, concurrency, limits, clock, DEFAULT_DNS_FAILURE_TTL);
    }

    public AiServiceClient(String baseUrl, String token, int concurrency, Limits limits,
                           LongSupplier clock, Duration dnsFailureTtl) {
        this(baseUrl, token, concurrency, limits, clock, dnsFailureTtl, null);
    }

    /** 测试可替换系统解析与时钟；正式调用仍使用固定配置中的主机和系统DNS。 */
    AiServiceClient(String baseUrl, String token, int concurrency, Limits limits,
                    LongSupplier clock, Duration dnsFailureTtl, Callable<InetAddress[]> lookup) {
        try {
            URI base = URI.create(baseUrl);
            if (!isSafeBaseUri(base) || !isValidToken(token) || concurrency < 1 || limits == null || clock == null
                    || dnsFailureTtl == null || dnsFailureTtl.isNegative() || dnsFailureTtl.isZero()
                    || dnsFailureTtl.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalArgumentException();
            }
            endpoint = URI.create(baseUrl.replaceAll("/+$", "") + ENDPOINT_PATH);
        } catch (RuntimeException invalid) {
            // 不附带原始配置或异常链，避免启动失败时回显密钥和地址中的凭据。
            throw new IllegalArgumentException(INVALID_CONFIGURATION);
        }
        this.token = token;
        this.limits = limits;
        this.clock = clock;
        this.concurrency = concurrency;
        this.dns = new FixedAiDnsResolver(endpoint.getHost(), limits.connect(), dnsFailureTtl, clock,
                lookup != null ? lookup : () -> InetAddress.getAllByName(endpoint.getHost()));
        // 队列只承接旧任务结束／取消时尚未归还的物理线程；逻辑准入仍限制为concurrency。
        this.httpWorkers = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(concurrency), work -> daemonThread(work, "ai-http-execute"),
                new ThreadPoolExecutor.AbortPolicy());
        this.http = HttpClients.custom()
                .setConnectionManager(connectionManager(dns, concurrency, limits))
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .disableContentCompression()
                .disableCookieManagement()
                .build();
        this.deadlines = new ScheduledThreadPoolExecutor(1, work -> daemonThread(work, "ai-http-deadline"));
        this.deadlines.setRemoveOnCancelPolicy(true);
    }

    // =====================================================================
    // 生成调用主流程
    // =====================================================================

    /** 同步兼容入口，会阻塞调用线程；worker必须使用generateAsync避免占线程等待DNS。 */
    public MoodAiResponse generate(MoodAiRequest request, Duration remaining) {
        CompletableFuture<MoodAiResponse> result = generateAsync(request, remaining);
        try {
            return result.get();
        } catch (InterruptedException interrupted) {
            result.cancel(true);
            Thread.currentThread().interrupt();
            throw failure(EXECUTION_INTERRUPTED);
        } catch (ExecutionException problem) {
            if (problem.getCause() instanceof AiServiceException safe) throw safe;
            throw failure(PROVIDER_ERROR);
        }
    }

    /**
     * DNS阶段只登记续接；每个请求的deadline、取消和结果独立，HTTP阶段才占用有界执行线程。
     * 调用方的同步续接必须保持轻量；数据库操作等阻塞任务应使用whenCompleteAsync及显式执行器。
     */
    public CompletableFuture<MoodAiResponse> generateAsync(MoodAiRequest request, Duration remaining) {
        return startGeneration(request, remaining).response();
    }

    /** worker同时观察结果与清理信号；取消response只取消本请求，不取消共享DNS。 */
    public Generation startGeneration(MoodAiRequest request, Duration remaining) {
        Call call;
        try {
            if (Thread.currentThread().isInterrupted() || closed) throw failure(EXECUTION_INTERRUPTED);
            if (remaining == null || remaining.isNegative() || remaining.isZero()) throw failure(PROVIDER_TIMEOUT);
            Duration allowed = remaining.compareTo(limits.total()) < 0 ? remaining : limits.total();
            long deadline = clock.getAsLong() + allowed.toNanos();
            validateInput(request);
            call = new Call(request, deadline);
            synchronized (lifecycle) {
                if (closed) throw failure(EXECUTION_INTERRUPTED);
                if (active.size() >= concurrency) throw failure("overloaded");
                active.add(call);
                call.alarm = deadlines.schedule(() -> {
                    call.expired.set(true);
                    call.cancelPost();
                    finish(call, null, failure(PROVIDER_TIMEOUT));
                }, Math.max(0, nanosUntil(deadline)), TimeUnit.NANOSECONDS);
            }
        } catch (AiServiceException invalid) {
            return new Generation(CompletableFuture.failedFuture(invalid), CompletableFuture.completedFuture(null));
        }
        call.result.whenComplete((value, problem) -> release(call));
        dns.prepare().whenComplete((ignored, problem) -> afterDns(call, problem));
        return new Generation(call.result, call.terminated);
    }

    private void afterDns(Call call, Throwable dnsFailure) {
        if (call.result.isDone()) return;
        try {
            synchronized (lifecycle) {
                checkDeadline(call.deadline, call.expired);
                if (call.result.isDone()) return;
                if (dnsFailure != null) throw failure(PROVIDER_ERROR);
                // DNS等待已经消耗预算，此处重新计算，不能查到地址后再获得完整50秒。
                long remainingMs = nanosUntil(call.deadline) / 1_000_000;
                int budgetMs = providerBudgetMs(call.request.budgetMs(), remainingMs);
                if (budgetMs < 1) throw failure(PROVIDER_TIMEOUT);
                call.post = buildPost(withBudget(call.request, budgetMs), remainingMs);
                call.task = () -> executeHttp(call);
                httpWorkers.execute(call.task);
            }
        } catch (RejectedExecutionException full) {
            // 任务未进入HTTP执行器，没有等待中的HTTP资源。
            call.terminated.complete(null);
            finish(call, null, failure(closed ? EXECUTION_INTERRUPTED : "overloaded"));
        } catch (AiServiceException safe) {
            finish(call, null, safe);
        }
    }

    private void executeHttp(Call call) {
        try {
            if (call.result.isDone()) return;
            checkDeadline(call.deadline, call.expired);
            MoodAiResponse result = http.execute(call.post, response -> {
                try {
                    MoodAiResponse parsed = parse(response, call.request);
                    checkDeadline(call.deadline, call.expired);
                    return parsed;
                } catch (IOException | RuntimeException problem) {
                    call.cancelPost();
                    throw problem;
                }
            });
            checkDeadline(call.deadline, call.expired);
            finish(call, result, null);
        } catch (AiServiceException safe) {
            finish(call, null, safe);
        } catch (Exception problem) {
            finish(call, null, classifyTransportFailure(problem, call.deadline, call.expired));
        } finally {
            // http.execute已退出，响应处理和连接清理均已结束；超时结果可能比这里先发布。
            call.terminated.complete(null);
        }
    }

    private void finish(Call call, MoodAiResponse value, AiServiceException problem) {
        if (!call.finished.compareAndSet(false, true)) return;
        // 先释放逻辑容量再公开结果，允许调用方在完成回调里立即提交下一请求。
        release(call);
        if (problem == null) call.result.complete(value);
        else call.result.completeExceptionally(problem);
    }

    private void release(Call call) {
        synchronized (lifecycle) {
            call.alarm.cancel(false);
            call.cancelPost();
            if (call.task == null || httpWorkers.remove(call.task)) {
                // 尚在DNS阶段，或HTTP任务还在队列内，取消后无HTTP执行需要等待。
                call.terminated.complete(null);
            }
            active.remove(call);
        }
    }

    /** 测试观察实际HTTP线程占用，区别于等待DNS的逻辑请求数。 */
    int httpActiveCount() { return httpWorkers.getActiveCount(); }

    private static final class Call {
        final MoodAiRequest request;
        final long deadline;
        final AtomicBoolean expired = new AtomicBoolean();
        final AtomicBoolean finished = new AtomicBoolean();
        final CompletableFuture<MoodAiResponse> result = new CompletableFuture<>();
        final CompletableFuture<Void> terminated = new CompletableFuture<>();
        volatile HttpPost post;
        Runnable task;
        ScheduledFuture<?> alarm;

        Call(MoodAiRequest request, long deadline) {
            this.request = request;
            this.deadline = deadline;
        }

        void cancelPost() {
            HttpPost current = post;
            if (current != null) current.cancel();
        }
    }

    /** 传给 Python 的预算：不超过调用方请求值、上限 40 秒，并扣掉本地协议余量。 */
    private static int providerBudgetMs(int requestedMs, long remainingMs) {
        long localMargin = Math.min(LOCAL_MARGIN_CAP_MS, remainingMs / 10);
        long available = Math.min(MAX_BUDGET_MS, remainingMs - localMargin);
        return (int) Math.min(requestedMs, available);
    }

    private static MoodAiRequest withBudget(MoodAiRequest r, int budgetMs) {
        return new MoodAiRequest(r.requestId(), r.jobId(), r.attempt(), r.sourceVersion(), r.moodType(), r.content(), budgetMs);
    }

    private HttpPost buildPost(MoodAiRequest input, long remainingMs) {
        HttpPost post = new HttpPost(endpoint);
        try {
            post.setHeader("Authorization", "Bearer " + token);
            post.setHeader("Accept", "application/json");
            post.setEntity(new ByteArrayEntity(json.writeValueAsBytes(input), ContentType.APPLICATION_JSON));
        } catch (IOException invalid) {
            throw failure(INVALID_REQUEST);
        }
        post.setConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(atLeastOneMs(remainingMs))
                .setResponseTimeout(atLeastOneMs(Math.min(limits.read().toMillis(), remainingMs)))
                .build());
        return post;
    }

    /** 把 HTTP 层的未知异常收敛为安全分类码；判断顺序即优先级。 */
    private AiServiceException classifyTransportFailure(Exception problem, long deadline, AtomicBoolean expired) {
        if (closed || Thread.currentThread().isInterrupted()) return failure(EXECUTION_INTERRUPTED);
        if (expired.get() || nanosUntil(deadline) <= 0 || hasCause(problem, SocketTimeoutException.class)) {
            return failure(PROVIDER_TIMEOUT);
        }
        if (hasCause(problem, MessageConstraintException.class)) return failure(INVALID_MODEL_RESPONSE);
        return failure(PROVIDER_ERROR);
    }

    private void checkDeadline(long deadline, AtomicBoolean expired) {
        if (closed || Thread.currentThread().isInterrupted()) throw failure(EXECUTION_INTERRUPTED);
        if (expired.get() || nanosUntil(deadline) <= 0) throw failure(PROVIDER_TIMEOUT);
    }

    private long nanosUntil(long deadline) {
        return deadline - clock.getAsLong();
    }

    // =====================================================================
    // 响应解析：状态码 → 正文读取 → JSON 结构 → 业务关联校验
    // =====================================================================

    private MoodAiResponse parse(ClassicHttpResponse response, MoodAiRequest request) throws IOException {
        int status = response.getCode();
        requireAcceptedStatus(status);
        JsonNode root = readJsonBody(response.getEntity());
        if (status != 200) throw parseErrorResponse(root, status, request);
        return parseSuccessResponse(root, request);
    }

    private static void requireAcceptedStatus(int status) {
        if (ACCEPTED_STATUS.contains(status)) return;
        // 未约定的 5xx 视为上游故障；其他未约定状态视为协议不符。
        throw failure(status >= 500 ? PROVIDER_ERROR : INVALID_MODEL_RESPONSE);
    }

    /** 只接受不超过上限的 application/json 严格 UTF-8 正文。 */
    private JsonNode readJsonBody(HttpEntity entity) throws IOException {
        if (entity == null || entity.getContentLength() > BODY_LIMIT_BYTES) throw failure(INVALID_MODEL_RESPONSE);
        String type = entity.getContentType();
        if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
            throw failure(INVALID_MODEL_RESPONSE);
        }
        // 先检查累计字节再交还响应资源；提前 close 流可能继续排空超大剩余正文。
        byte[] body = entity.getContent().readNBytes(BODY_LIMIT_BYTES + 1);
        if (body.length > BODY_LIMIT_BYTES) throw failure(INVALID_MODEL_RESPONSE);
        try {
            return json.readTree(decodeStrictUtf8(body));
        } catch (IOException invalid) {
            throw failure(INVALID_MODEL_RESPONSE);
        }
    }

    private static String decodeStrictUtf8(byte[] body) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body))
                .toString();
    }

    /** 错误响应必须形如 {request_id, code}，且 code 与状态码配对；通过后原样透传 code。 */
    private static AiServiceException parseErrorResponse(JsonNode root, int status, MoodAiRequest request) {
        requireExactFields(root, ERROR_FIELDS);
        JsonNode idNode = root.get("request_id");
        JsonNode codeNode = root.get("code");
        if (!codeNode.isTextual() || !(idNode.isNull() || idNode.isTextual())) throw failure(INVALID_MODEL_RESPONSE);

        String id = idNode.textValue();
        String code = codeNode.textValue();
        boolean idMatches = id == null || id.equals(request.requestId());
        boolean statusMatches = Objects.equals(ERROR_STATUS.get(code), status);
        if (!idMatches || !statusMatches) throw failure(INVALID_MODEL_RESPONSE);
        return failure(code);
    }

    private MoodAiResponse parseSuccessResponse(JsonNode root, MoodAiRequest request) throws IOException {
        requireExactFields(root, SUCCESS_FIELDS);
        for (String field : SUCCESS_FIELDS) {
            if (!hasExpectedType(field, root.get(field))) throw failure(INVALID_MODEL_RESPONSE);
        }
        MoodAiResponse result = json.treeToValue(root, MoodAiResponse.class);
        try {
            result.validatedReply(Long.parseLong(request.jobId()), request.attempt(),
                    request.requestId(), Long.parseLong(request.sourceVersion()));
        } catch (IllegalArgumentException invalid) {
            throw failure(INVALID_MODEL_RESPONSE);
        }
        return result;
    }

    /** 逐字段校验 JSON 类型，避免 Jackson 把字符串宽松转换为数字或布尔值。 */
    private static boolean hasExpectedType(String field, JsonNode value) {
        return switch (field) {
            case "attempt" -> value.isIntegralNumber() && value.canConvertToInt();
            case "model_called" -> value.isBoolean();
            case "model_name" -> value.isNull() || value.isTextual();
            default -> value.isTextual();
        };
    }

    private static void requireExactFields(JsonNode root, Set<String> expected) {
        if (root == null || !root.isObject()) throw failure(INVALID_MODEL_RESPONSE);
        Set<String> actual = new HashSet<>();
        root.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw failure(INVALID_MODEL_RESPONSE);
    }

    // =====================================================================
    // 输入与配置校验
    // =====================================================================

    private static void validateInput(MoodAiRequest request) {
        try {
            require(request != null);
            require(matches(request.requestId(), REQUEST_ID_PATTERN));
            require(isPositiveLong(request.jobId()));
            require(isPositiveLong(request.sourceVersion()));
            require(request.attempt() >= 1 && request.attempt() <= MAX_ATTEMPT);
            require(MOODS.contains(request.moodType() == null ? "" : request.moodType()));
            require(request.budgetMs() >= 1 && request.budgetMs() <= MAX_BUDGET_MS);
            require(isValidContent(request.content()));
        } catch (NumberFormatException overflow) {
            throw failure(INVALID_REQUEST);
        }
    }

    private static void require(boolean condition) {
        if (!condition) throw failure(INVALID_REQUEST);
    }

    private static boolean matches(String value, String pattern) {
        return value != null && value.matches(pattern);
    }

    /** 19 位以内的正整数字符串；超出 long 范围时 parseLong 抛出 NumberFormatException。 */
    private static boolean isPositiveLong(String value) {
        return matches(value, POSITIVE_LONG_PATTERN) && Long.parseLong(value) > 0;
    }

    /** 正文非空、不超长、不全是空白，且不含孤立的代理字符。 */
    private static boolean isValidContent(String content) {
        return content != null
                && content.length() <= MAX_CONTENT_LENGTH
                && content.codePoints().anyMatch(c -> !isUnicodeWhitespace(c))
                && hasPairedSurrogates(content);
    }

    /** 与 Python str.strip 的 Unicode 白字符口径保持一致。 */
    private static boolean isUnicodeWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0x85;
    }

    private static boolean hasPairedSurrogates(String text) {
        for (int i = 0; i < text.length(); i++) {
            char unit = text.charAt(i);
            if (Character.isHighSurrogate(unit)) {
                i++;
                if (i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) return false;
            } else if (Character.isLowSurrogate(unit)) {
                return false;
            }
        }
        return true;
    }

    /** 只允许 http(s)，且地址中不得带用户信息、查询串或片段。 */
    private static boolean isSafeBaseUri(URI base) {
        boolean httpScheme = "http".equals(base.getScheme()) || "https".equals(base.getScheme());
        return httpScheme
                && base.getHost() != null
                && base.getUserInfo() == null
                && base.getRawQuery() == null
                && base.getRawFragment() == null
                && base.getPort() <= 65535;
    }

    /** 令牌只能由可见 ASCII 字符组成，防止头部注入。 */
    private static boolean isValidToken(String token) {
        return token != null && !token.isEmpty() && token.chars().allMatch(c -> c >= 33 && c <= 126);
    }

    // =====================================================================
    // 基础设施
    // =====================================================================

    /** 连接池只读取已解析的地址快照；系统DNS由异步准备阶段负责。 */
    private static PoolingHttpClientConnectionManager connectionManager(FixedAiDnsResolver dns, int concurrency, Limits limits) {
        var connectionFactory = ManagedHttpClientConnectionFactory.builder()
                .http1Config(Http1Config.custom()
                        .setMaxLineLength(MAX_HEADER_LINE)
                        .setMaxHeaderCount(MAX_HEADER_COUNT)
                        .build())
                .responseParserFactory(BoundedAiResponseParser::create)
                .build();
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setConnectionFactory(connectionFactory)
                .setDnsResolver(dns)
                .setMaxConnTotal(concurrency)
                .setMaxConnPerRoute(concurrency)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(atLeastOneMs(limits.connect().toMillis()))
                        .setSocketTimeout(atLeastOneMs(limits.read().toMillis()))
                        .build())
                .build();
    }

    private static Timeout atLeastOneMs(long millis) {
        return Timeout.ofMilliseconds(Math.max(1, millis));
    }

    static Thread daemonThread(Runnable work, String name) {
        Thread thread = new Thread(work, name);
        thread.setDaemon(true);
        return thread;
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    private static AiServiceException failure(String code) {
        return new AiServiceException(code);
    }

    @Override
    public void close() {
        Call[] pending;
        List<Runnable> abandoned;
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            pending = active.toArray(Call[]::new);
            for (Call call : pending) call.cancelPost();
            deadlines.shutdownNow();
            abandoned = httpWorkers.shutdownNow();
        }
        dns.close();
        http.close(CloseMode.IMMEDIATE);
        // 不持有生命周期锁执行Future续接；迟到DNS和HTTP结果不能覆盖已终止的结果。
        for (Call call : pending) {
            if (abandoned.contains(call.task)) {
                call.terminated.complete(null);
            }
            finish(call, null, failure(EXECUTION_INTERRUPTED));
        }
    }

    /** close后等待HTTP执行器实际退出；不等待可能忽略中断的系统DNS daemon。 */
    public boolean awaitTermination(Duration remaining) throws InterruptedException {
        return httpWorkers.awaitTermination(Math.max(0, remaining.toNanos()), TimeUnit.NANOSECONDS);
    }
}
