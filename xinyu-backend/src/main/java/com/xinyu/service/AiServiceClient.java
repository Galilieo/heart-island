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
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Java 调用 Python 内部生成服务的 HTTP 客户端。
 *
 * <p>每次 {@link #generate} 都受同一个总截止时间约束：连接、等待响应、读取正文和本地校验都计入其中，
 * 到期由定时器直接取消底层请求。失败只抛出带安全分类码的 {@link AiServiceException}，
 * 不携带原始响应、密钥或异常链。资源在 {@link #close} 时统一回收，业务事务由调用方管理。</p>
 */
public final class AiServiceClient implements AutoCloseable {

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
    private final Set<HttpPost> active = ConcurrentHashMap.newKeySet();
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
        try {
            URI base = URI.create(baseUrl);
            if (!isSafeBaseUri(base) || !isValidToken(token) || concurrency < 1 || limits == null || clock == null) {
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
        this.http = HttpClients.custom()
                .setConnectionManager(connectionManager(endpoint.getHost(), concurrency, limits))
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

    public MoodAiResponse generate(MoodAiRequest request, Duration remaining) {
        if (Thread.currentThread().isInterrupted() || closed) throw failure(EXECUTION_INTERRUPTED);
        if (remaining == null || remaining.isNegative() || remaining.isZero()) throw failure(PROVIDER_TIMEOUT);

        // 先按 Duration 限幅，再转纳秒，避免调用方传入极大时间时转换溢出。
        Duration allowed = remaining.compareTo(limits.total()) < 0 ? remaining : limits.total();
        long deadline = clock.getAsLong() + allowed.toNanos();
        validateInput(request);

        long remainingMs = nanosUntil(deadline) / 1_000_000;
        int budgetMs = providerBudgetMs(request.budgetMs(), remainingMs);
        if (budgetMs < 1) throw failure(PROVIDER_TIMEOUT);

        HttpPost post = buildPost(withBudget(request, budgetMs), remainingMs);
        AtomicBoolean expired = new AtomicBoolean();
        ScheduledFuture<?> alarm = register(post, deadline, expired);
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
            throw classifyTransportFailure(problem, deadline, expired);
        } finally {
            alarm.cancel(false);
            active.remove(post);
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

    /**
     * 登记进行中的请求并排定截止定时器。与 close 在同一把锁下执行，保证关闭后不会再有新请求漏网。
     * 定时器直接取消底层请求；执行调用仍在当前线程，退出后才由 worker 归还槽位。
     */
    private ScheduledFuture<?> register(HttpPost post, long deadline, AtomicBoolean expired) {
        synchronized (lifecycle) {
            if (closed) throw failure(EXECUTION_INTERRUPTED);
            active.add(post);
            return deadlines.schedule(() -> {
                expired.set(true);
                post.cancel();
            }, Math.max(0, nanosUntil(deadline)), TimeUnit.NANOSECONDS);
        }
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

    /** 连接池：限制响应头大小、使用启动时固定的 DNS 快照，并设置连接与读取超时。 */
    private static PoolingHttpClientConnectionManager connectionManager(String host, int concurrency, Limits limits) {
        var connectionFactory = ManagedHttpClientConnectionFactory.builder()
                .http1Config(Http1Config.custom()
                        .setMaxLineLength(MAX_HEADER_LINE)
                        .setMaxHeaderCount(MAX_HEADER_COUNT)
                        .build())
                .responseParserFactory(BoundedAiResponseParser::create)
                .build();
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setConnectionFactory(connectionFactory)
                .setDnsResolver(FixedAiDnsResolver.create(host, limits.connect()))
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
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            active.forEach(HttpPost::cancel);
            deadlines.shutdownNow();
        }
        http.close(CloseMode.IMMEDIATE);
    }
}
