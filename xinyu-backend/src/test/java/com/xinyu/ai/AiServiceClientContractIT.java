package com.xinyu.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyu.dto.MoodAiRequest;
import com.xinyu.service.AiServiceClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/** 真 Java→Python HTTP，Python 只替换供应商，明确不连接数据库或真实模型。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiServiceClientContractIT {
    private Process python;
    private AiServiceClient client;
    private String baseUrl;
    private final HttpClient observer = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    @BeforeAll
    void startPython() throws Exception {
        Path ai = Path.of("../xinyu-ai").toAbsolutePath().normalize();
        Path interpreter = ai.resolve(".venv/bin/python");
        assertThat(interpreter).as("先准备已锁定依赖的 Python 测试环境").isExecutable();
        ProcessBuilder builder = new ProcessBuilder(interpreter.toString(), "-m", "tests.java_contract_server")
                .directory(ai.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT);
        // 子进程只使用测试字面量配置，避免继承真实供应商／内部凭据。
        builder.environment().clear();
        builder.environment().put("PYTHONUNBUFFERED", "1");
        python = builder.start();
        ExecutorService startup = Executors.newSingleThreadExecutor();
        try {
            String port = startup.submit(() -> python.inputReader().readLine()).get(10, TimeUnit.SECONDS);
            assertThat(port).matches("[0-9]+");
            baseUrl = "http://127.0.0.1:" + Integer.parseInt(port);
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (true) {
                try { if (get("/health").contains("true")) break; }
                catch (Exception notReady) { if (!python.isAlive() || System.nanoTime() >= until) throw notReady; }
                if (System.nanoTime() >= until) throw new AssertionError("测试 Python 未就绪");
                Thread.sleep(25);
            }
            client = new AiServiceClient(baseUrl, AiServiceClientTests.SECRET, 3);
        } finally { startup.shutdownNow(); }
    }

    static Stream<String> moods() throws Exception {
        var values = new ObjectMapper().readTree(Files.readString(Path.of("../test-contracts/mood-types.json")));
        assertThat(values).hasSize(13);
        return java.util.stream.StreamSupport.stream(values.spliterator(), false).map(node -> node.textValue());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {4, 7})
    void laterAttemptsCrossBothJavaAndPythonContracts(int attempt) {
        var result = client.generate(new MoodAiRequest("req-later", "10", attempt, "7", "平静",
                "继续尝试的合成内容。", 40000), Duration.ofSeconds(3));
        assertThat(result.attempt()).isEqualTo(attempt);
        assertThat(result.resultKind()).isEqualTo("MODEL");
    }

    @ParameterizedTest
    @MethodSource("moods")
    void eachSharedMoodCrossesActualPythonContract(String mood) throws Exception {
        int before = calls();
        var result = client.generate(new MoodAiRequest("req-A", "10", 1, "7", mood,
                "今天完成了一件小事，这是合成测试。", 40000), Duration.ofSeconds(3));
        assertThat(result.resultKind()).isEqualTo("MODEL");
        assertThat(result.modelCalled()).isTrue();
        assertThat(result.jobId()).isEqualTo("10");
        assertThat(result.replyContent()).isNotBlank();
        assertThat(calls()).isEqualTo(before + 1);
    }

    @Test
    void supportCrossesHttpWithoutCallingSyntheticProvider() throws Exception {
        int before = calls();
        var result = client.generate(new MoodAiRequest("req-A", "10", 1, "7", "难过", "我想死。", 40000), Duration.ofSeconds(3));
        assertThat(result.resultKind()).isEqualTo("SUPPORT");
        assertThat(result.modelCalled()).isFalse();
        assertThat(result.modelName()).isNull();
        assertThat(calls()).isEqualTo(before);
    }

    @Test
    void wrongTokenIsRejectedByActualPythonBeforeProvider() throws Exception {
        int before = calls();
        try (AiServiceClient wrong = new AiServiceClient(baseUrl, "synthetic-wrong-token", 1)) {
            AiServiceClientTests.assertFailure(() -> wrong.generate(AiServiceClientTests.request(), Duration.ofSeconds(3)), "unauthorized");
        }
        assertThat(calls()).isEqualTo(before);
    }

    private int calls() throws Exception { return new ObjectMapper().readTree(get("/test/provider-calls")).get("count").intValue(); }
    private String get(String path) throws Exception {
        return observer.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(1)).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    @AfterAll
    void stopPython() throws Exception {
        if (client != null) client.close();
        if (python != null) {
            python.destroy();
            if (!python.waitFor(3, TimeUnit.SECONDS)) { python.destroyForcibly(); assertThat(python.waitFor(3, TimeUnit.SECONDS)).isTrue(); }
        }
    }
}
