package com.xinyu.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyu.XinyuBackendApplication;
import com.xinyu.service.AiJobRuntime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 完整Spring HTTP／鉴权／事务／worker → Python应用／HttpProvider → 合成供应商HTTP → 隔离MySQL。
 * 使用显式本机空库，不读取.env，不调用真实模型。Python测试预算2秒，Java仍用生产50秒生成预算。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiReplyContractIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration WAIT_LIMIT = Duration.ofSeconds(10);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private JdbcTemplate jdbc;
    private Process python;
    private ConfigurableApplicationContext application;
    private String jdbcUrl;
    private String databaseUser;
    private String databasePassword;
    private String pythonUrl;
    private String baseUrl;
    private String token;
    private long userId;

    @BeforeAll
    void prepareIsolatedApplication() throws Exception {
        jdbcUrl = System.getenv("AI_TEST_JDBC_URL");
        assumeTrue(jdbcUrl != null, "显式提供本机空xinyu_test_库后运行，不使用业务数据源");
        URI target = URI.create(jdbcUrl.substring(5));
        assertThat(jdbcUrl).startsWith("jdbc:mysql://127.0.0.1:");
        assertThat(target.getPath()).matches("/xinyu_test_[A-Za-z0-9_]+");
        assertThat(target.getQuery()).isNull();
        assertThat(target.getUserInfo()).isNull();
        assertThat(target.getFragment()).isNull();
        databaseUser = System.getenv().getOrDefault("AI_TEST_DB_USER", "root");
        databasePassword = System.getenv().getOrDefault("AI_TEST_DB_PASSWORD", "");
        jdbc = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl, databaseUser, databasePassword));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE()", Integer.class)).isZero();

        // 只提取建表语句；不执行初始化脚本中的CREATE DATABASE／USE／DROP，也不导入业务数据。
        String schema = Files.readString(Path.of("../sql/xinyu.sql"));
        var tables = Pattern.compile("CREATE TABLE .*?;(?=\\s*(?:CREATE|INSERT|$))", Pattern.DOTALL)
                .matcher(schema).results().map(match -> match.group()).toList();
        assertThat(tables).hasSize(8);
        tables.forEach(jdbc::execute);
        String migration = Files.readString(Path.of("../sql/migrations/20261107_01_ai_reply_job.sql"));
        for (String statement : migration.split(";")) {
            if (!statement.isBlank()) {
                jdbc.execute(statement);
            }
        }

        startPython();
        startJava();
        Reply registration = javaRequest("POST", "/user/register", Map.of(
                "username", "contract_fixture", "password", "fixture-only-password", "nickname", "合成联调用户"));
        assertThat(registration.body().path("code").asInt()).isEqualTo(200);
        JsonNode login = javaRequest("POST", "/user/login", Map.of(
                "username", "contract_fixture", "password", "fixture-only-password")).body().path("data");
        token = login.path("token").asText();
        userId = login.path("user").path("id").asLong();
        assertThat(token).isNotBlank();
        assertThat(userId).isPositive();
    }

    private void startPython() throws Exception {
        Path directory = Path.of("../xinyu-ai").toAbsolutePath().normalize();
        ProcessBuilder builder = new ProcessBuilder(directory.resolve(".venv/bin/python").toString(),
                "-m", "tests.full_stack_server").directory(directory.toFile());
        builder.environment().clear();
        builder.environment().put("PYTHONUNBUFFERED", "1");
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        python = builder.start();
        var reader = Executors.newSingleThreadExecutor();
        try {
            String port = reader.submit(() -> python.inputReader().readLine()).get(10, TimeUnit.SECONDS);
            assertThat(port).matches("[0-9]+");
            pythonUrl = "http://127.0.0.1:" + port;
            await(() -> {
                try {
                    return request("GET", pythonUrl + "/health", null, false).status() == 200;
                } catch (Exception notReady) {
                    return false;
                }
            });
        } finally {
            reader.shutdownNow();
        }
    }

    private void startJava() {
        // 命令行属性高于测试进程／宿主环境，固定所有依赖为隔离库与本机合成服务。
        application = new SpringApplicationBuilder(XinyuBackendApplication.class).run(
                "--spring.config.location=optional:classpath:/isolated-db-test.yml",
                "--spring.datasource.url=" + jdbcUrl,
                "--spring.datasource.username=" + databaseUser,
                "--spring.datasource.password=" + databasePassword,
                "--spring.datasource.hikari.connection-timeout=2000",
                "--spring.datasource.hikari.validation-timeout=1000",
                "--spring.datasource.hikari.data-source-properties.connectTimeout=2000",
                "--spring.datasource.hikari.data-source-properties.socketTimeout=6000",
                "--server.address=127.0.0.1", "--server.port=0",
                "--AI_WORKER_ENABLED=true", "--AI_WORKER_CONCURRENCY=3",
                "--AI_SERVICE_BASE_URL=" + pythonUrl, "--AI_INTERNAL_TOKEN=test-internal-secret",
                "--deepseek.api-key=test-provider-secret", "--deepseek.base-url=" + pythonUrl + "/v1",
                "--deepseek.model=test-model");
        int port = ((ServletWebServerApplicationContext) application).getWebServer().getPort();
        baseUrl = "http://127.0.0.1:" + port;
    }

    @Test
    @Order(1)
    void savingReturnsBeforeModelAndQueriesNeverGenerate() throws Exception {
        String content = "等待保存确认合成";
        JsonNode saved = save(content, true);
        await(() -> supplierCalls().contains(content));
        assertThat(job(saved).path("status").asText()).isEqualTo("running");
        List<String> before = supplierCalls();
        for (int query = 0; query < 3; query++) {
            assertThat(job(saved).path("status").asText()).isEqualTo("running");
        }
        assertThat(supplierCalls()).isEqualTo(before);

        release(content);
        await(() -> job(saved).path("status").asText().equals("succeeded"));
        JsonNode detail = data("GET", "/mood/detail/" + saved.path("id").asText(), null);
        assertThat(detail.path("aiReply").asText()).isEqualTo(job(saved).path("replyContent").asText());
    }

    @Test
    @Order(2)
    void supportSkipsSupplierAndDisabledCreatesNoTask() throws Exception {
        List<String> before = supplierCalls();
        JsonNode support = save("我想死。", true);
        await(() -> job(support).path("status").asText().equals("succeeded"));
        assertThat(job(support).path("resultKind").asText()).isEqualTo("SUPPORT");
        JsonNode disabled = save("关闭AI的合成记录", false);
        assertThat(disabled.path("aiReplyState").asText()).isEqualTo("disabled");
        assertThat(disabled.path("jobId").isNull()).isTrue();
        assertThat(supplierCalls()).isEqualTo(before);
    }

    @Test
    @Order(3)
    void invalidReplyFailsAndManualRetryKeepsJobIdentity() throws Exception {
        JsonNode saved = save("空回复合成", true);
        await(() -> job(saved).path("status").asText().equals("failed"));
        assertThat(job(saved).path("errorCode").asText()).isEqualTo("invalid_model_response");
        assertThat(data("GET", "/mood/detail/" + saved.path("id").asText(), null).path("aiReply").isNull()).isTrue();
        JsonNode retried = data("POST", "/mood/ai-job/" + saved.path("jobId").asText() + "/retry", null);
        assertThat(retried.path("id").asText()).isEqualTo(saved.path("jobId").asText());
        await(() -> job(saved).path("attemptCount").asInt() == 2
                && job(saved).path("status").asText().equals("failed"));
    }

    @Test
    @Order(4)
    void deleteDuringGenerationDiscardsLateReply() throws Exception {
        String content = "等待删除合成";
        JsonNode saved = save(content, true);
        await(() -> supplierCalls().contains(content));
        assertThat(data("DELETE", "/mood/delete/" + saved.path("id").asText(), null).asBoolean()).isTrue();
        release(content);
        // DELETE本身已会标记superseded；还必须等待真实HTTP结果／清理及worker收尾退出，才检查没有迟到写入。
        await(() -> application.getBean(AiJobRuntime.class).activeExecutions() == 0);

        String jobPath = "/mood/ai-job/" + saved.path("jobId").asText();
        assertThat(javaRequest("GET", jobPath, null).status()).isEqualTo(404);
        assertThat(javaRequest("POST", jobPath + "/retry", null).status()).isEqualTo(404);
        await(() -> jdbc.queryForObject("SELECT status FROM ai_job WHERE id=?", String.class,
                saved.path("jobId").asLong()).equals("superseded"));
        assertThat(jdbc.queryForObject("SELECT ai_reply FROM mood_record WHERE id=?", String.class,
                saved.path("id").asLong())).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_reply WHERE user_id=? AND target_id=?",
                Integer.class, userId, saved.path("id").asLong())).isZero();
    }

    @Test
    @Order(5)
    void shortenedPythonTimeoutReleasesWorkerForNextTask() throws Exception {
        JsonNode slow = save("超时合成", true);
        await(() -> job(slow).path("status").asText().equals("failed"));
        assertThat(job(slow).path("errorCode").asText()).isEqualTo("provider_timeout");
        // 仅观察下一个任务成功可能借用另外两槽；直接核验原执行连同HTTP清理已归还槽位。
        await(() -> application.getBean(AiJobRuntime.class).activeExecutions() == 0);
        JsonNode next = save("恢复后的普通合成", true);
        await(() -> job(next).path("status").asText().equals("succeeded"));
    }

    @Test
    @Order(6)
    void restartingApplicationMarksOldRunningFailedWithoutCallingSupplierAgain() throws Exception {
        JsonNode saved = save("重启前合成", true);
        await(() -> job(saved).path("status").asText().equals("succeeded"));
        application.close();
        // 构造崩溃后持久化遗留状态，覆盖真实应用启动恢复；不冒称真实杀进程测试。
        jdbc.update("UPDATE mood_record SET ai_reply=NULL WHERE id=?", saved.path("id").asLong());
        jdbc.update("UPDATE ai_job SET status='running' WHERE id=?", saved.path("jobId").asLong());
        List<String> before = supplierCalls();
        startJava();

        assertThat(job(saved).path("status").asText()).isEqualTo("failed");
        assertThat(job(saved).path("errorCode").asText()).isEqualTo("execution_interrupted");
        assertThat(supplierCalls()).isEqualTo(before);
    }

    @Test
    @Order(7)
    void unavailablePythonStillAllowsSavingAndExposesGenerationFailure() throws Exception {
        python.destroy();
        assertThat(python.waitFor(5, TimeUnit.SECONDS)).isTrue();
        JsonNode saved = save("Python关闭后的合成记录", true);
        assertThat(saved.path("id").asLong()).isPositive();
        await(() -> job(saved).path("status").asText().equals("failed"));
        assertThat(job(saved).path("errorCode").asText()).isEqualTo("provider_error");
    }

    private JsonNode save(String content, boolean enabled) throws Exception {
        return data("POST", "/mood/add", Map.of("content", content, "moodType", "平静", "needAiReply", enabled));
    }

    private JsonNode job(JsonNode saved) {
        try {
            return data("GET", "/mood/ai-job/" + saved.path("jobId").asText(), null);
        } catch (Exception failure) {
            throw new AssertionError("任务GET失败", failure);
        }
    }

    private List<String> supplierCalls() {
        try {
            JsonNode calls = request("GET", pythonUrl + "/test/calls", null, false).body().path("calls");
            return JSON.convertValue(calls, JSON.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception failure) {
            throw new AssertionError("合成供应商观测失败", failure);
        }
    }

    private void release(String content) throws Exception {
        assertThat(request("POST", pythonUrl + "/test/release", Map.of("content", content), false).status()).isEqualTo(200);
    }

    private JsonNode data(String method, String path, Object body) throws Exception {
        Reply result = javaRequest(method, path, body);
        assertThat(result.status()).as(path).isEqualTo(200);
        assertThat(result.body().path("code").asInt()).as(path).isEqualTo(200);
        return result.body().path("data");
    }

    private Reply javaRequest(String method, String path, Object body) throws Exception {
        return request(method, baseUrl + path, body, true);
    }

    private Reply request(String method, String url, Object body, boolean authenticate) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json");
        if (authenticate && token != null) {
            builder.header("token", token);
        }
        var payload = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
        HttpResponse<String> response = http.send(builder.method(method, payload).build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), JSON.readTree(response.body()));
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT_LIMIT.toNanos();
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("真实组件须在有限时间内完成").isLessThan(deadline);
            Thread.sleep(20);
        }
    }

    @AfterAll
    void closeOwnedServices() throws Exception {
        if (application != null) {
            application.close();
        }
        if (python != null && python.isAlive()) {
            python.destroy();
            if (!python.waitFor(5, TimeUnit.SECONDS)) {
                python.destroyForcibly();
                assertThat(python.waitFor(3, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private record Reply(int status, JsonNode body) {}
}
