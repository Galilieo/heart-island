package com.xinyu.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyu.dto.ReviewAiRequest;
import com.xinyu.dto.ReviewAiResponse;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.mapper.AiReplyMapper;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.mapper.MoodReviewMapper;
import com.xinyu.ratelimit.AlwaysAllowRateLimiter;
import com.xinyu.service.AiJobAdmission;
import com.xinyu.service.AiJobService;
import com.xinyu.service.AiJobWorker;
import com.xinyu.service.AiServiceClient;
import com.xinyu.service.MoodReviewService;
import com.xinyu.service.MoodReviewStatsService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 显式新建空 MySQL 库，验证真实迁移与报告事务；不接受业务库名称。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoodReviewPersistenceIT {
    private JdbcTemplate jdbc;
    private DriverManagerDataSource source;
    private MoodReviewService reviews;
    private long nextUser = 2000;
    private SqlSessionTemplate sessions;
    private Clock clock;
    private DataSourceTransactionManager manager;
    private Process python;
    private String pythonUrl;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void services() throws Exception {
        manager = new DataSourceTransactionManager(source);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(MoodRecordMapper.class);
        configuration.addMapper(AiJobMapper.class);
        factory.setConfiguration(configuration);
        sessions = new SqlSessionTemplate(factory.getObject());
        clock = Clock.fixed(Instant.parse("2026-11-13T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        reviews = new MoodReviewService(
                new MoodReviewMapper(jdbc),
                new MoodReviewStatsService(sessions.getMapper(MoodRecordMapper.class), clock),
                clock, manager, new AiJobAdmission(manager), new AlwaysAllowRateLimiter());
        reviews.setReviewJobs(sessions.getMapper(AiJobMapper.class));
    }

    MoodReviewService independentReviews() {
        var instance = new MoodReviewService(
                new MoodReviewMapper(jdbc),
                new MoodReviewStatsService(sessions.getMapper(MoodRecordMapper.class), clock),
                clock, manager, new AiJobAdmission(manager), new AlwaysAllowRateLimiter());
        instance.setReviewJobs(sessions.getMapper(AiJobMapper.class));
        return instance;
    }

    @AfterEach
    void finishPendingFixtures() {
        jdbc.update("UPDATE ai_job SET status='cancelled' WHERE task_type='MOOD_REVIEW' AND status IN ('queued','running')");
        jdbc.update("UPDATE mood_review SET status='failed' WHERE status IN ('queued','running')");
    }

    void startPython() throws Exception {
        if (python != null) {
            return;
        }
        python = new ProcessBuilder(".venv/bin/python", "-m", "tests.review_stack_server")
                .directory(Path.of("../xinyu-ai").toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        var reader = new BufferedReader(new InputStreamReader(python.getInputStream()));
        pythonUrl = "http://127.0.0.1:" + Integer.parseInt(reader.readLine());
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (true) {
            try {
                httpGet("/health");
                break;
            } catch (IOException unavailable) {
                if (System.nanoTime() >= deadline) {
                    throw unavailable;
                }
                Thread.sleep(20);
            }
        }
    }

    JsonNode httpGet(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(pythonUrl + path)).GET().build();
        return json.readTree(HttpClient.newHttpClient().send(request,
                HttpResponse.BodyHandlers.ofString()).body());
    }

    @AfterAll
    void stopPython() throws Exception {
        if (python != null) {
            python.destroy();
            assertThat(python.waitFor(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    AiJobService jobsService() {
        var configuration = sessions.getConfiguration();
        if (!configuration.hasMapper(AiReplyMapper.class)) {
            configuration.addMapper(AiReplyMapper.class);
        }
        var jobs = new AiJobService(sessions.getMapper(AiJobMapper.class),
                sessions.getMapper(MoodRecordMapper.class), sessions.getMapper(AiReplyMapper.class),
                new AiJobAdmission(manager), new AlwaysAllowRateLimiter(), true);
        jobs.setReviews(reviews);
        return jobs;
    }

    long userWithRecord(boolean enabled, String content) {
        long user = ++nextUser;
        jdbc.update("INSERT INTO user(id) VALUES (?)", user);
        jdbc.update("INSERT INTO mood_record(user_id,mood_type,content,ai_reply_enabled,content_version,create_time) VALUES (?,'平静',?,?,1,'2026-11-03 12:00:00')", user, content, enabled);
        return user;
    }

    @BeforeAll
    void migrateEmptyIsolatedDatabase() {
        String url = System.getenv("AI_TEST_JDBC_URL");
        assumeTrue(url != null, "需要显式隔离库");
        URI uri = URI.create(url.substring(5));
        if (!url.startsWith("jdbc:mysql://") || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || !uri.getPath().matches("/xinyu_test_[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("只接受 xinyu_test_ 隔离库");
        }
        source = new DriverManagerDataSource(url, "root", System.getenv("AI_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class))
                .as("只迁移全新空测试库").isZero();
        try {
            String baseline = new ClassPathResource("ai/schema.sql").getContentAsString(StandardCharsets.UTF_8)
                    .split("-- STAGE2_REVIEW_TABLES")[0];
            new ResourceDatabasePopulator(new ByteArrayResource(
                    baseline.getBytes(StandardCharsets.UTF_8))).execute(source);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        for (String migration : new String[]{"20261107_02_ai_reply_model_name.sql", "20261107_01_ai_reply_job.sql",
                "20261113_01_mood_review.sql"}) {
            new ResourceDatabasePopulator(new FileSystemResource(Path.of("../sql/migrations", migration))).execute(source);
        }
    }

    @Test
    void migrationCreatesReviewAndSourcesWithoutChangingHistoricalRecords() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mood_review WHERE user_id=1", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mood_review_source WHERE review_id IN (SELECT id FROM mood_review WHERE user_id=1)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT content FROM mood_record WHERE id=101", String.class)).isEqualTo("历史有效有回复");
    }

    @Test
    void concurrentClicksCreateExactlyOneReportAndJob() throws Exception {
        long user = userWithRecord(true, "散步");
        ExecutorService executor = Executors.newFixedThreadPool(6);
        var gate = new CountDownLatch(1);
        try {
            var calls = new ArrayList<Future<Long>>();
            for (int index = 0; index < 6; index++) {
                calls.add(executor.submit(() -> {
                    gate.await();
                    return independentReviews().generate(user, "WEEK", "2026-11-02", false).id();
                }));
            }
            gate.countDown();
            var ids = new HashSet<Long>();
            for (var call : calls) {
                ids.add(call.get(10, TimeUnit.SECONDS));
            }
            assertThat(ids).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mood_review WHERE user_id=?", Integer.class, user)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE user_id=? AND task_type='MOOD_REVIEW'", Integer.class, user)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    long claim(long user) {
        var report = reviews.generate(user, "WEEK", "2026-11-02", false);
        jdbc.update("UPDATE ai_job SET status='running',attempt_count=1,request_id='review-test' WHERE id=?", report.jobId());
        jdbc.update("UPDATE mood_review SET status='running' WHERE id=?", report.id());
        return report.jobId();
    }

    ReviewAiResponse response(ReviewAiRequest request, String body) {
        return new ReviewAiResponse(request.requestId(), request.jobId(), request.attempt(),
                request.sourceVersion(), body, null, "MODEL", true, "mood-review-v1", "stub-model");
    }

    @Test
    void changedSourcesAreRejectedBeforeSendAndBeforeCommit() {
        long user = userWithRecord(true, "散步");
        long job = claim(user);
        jdbc.update("UPDATE mood_record SET content_version=2 WHERE user_id=?", user);
        assertThat(reviews.prepare(job, 1, "review-test")).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_job WHERE id=?", String.class, job)).isEqualTo("superseded");

        long other = userWithRecord(true, "看书");
        long otherJob = claim(other);
        var prepared = reviews.prepare(otherJob, 1, "review-test");
        jdbc.update("UPDATE mood_record SET content_version=2 WHERE user_id=?", other);
        assertThat(reviews.complete(otherJob, 1, "review-test", response(prepared.request(), "看完书了{{r1}}"))).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_job WHERE id=?", String.class, otherJob)).isEqualTo("superseded");
    }

    @Test
    void d1OnlyEnabledRecordsReachPythonRequestAndStatsStillIncludeAll() {
        long user = userWithRecord(true, "公开授权文本");
        jdbc.update("INSERT INTO mood_record(user_id,mood_type,content,ai_reply_enabled,content_version,create_time) VALUES (?,'难过','不应发送的私密文本',0,1,'2026-11-04 12:00:00')", user);
        var prepared = reviews.prepare(claim(user), 1, "review-test");
        assertThat(prepared.support()).isNull();
        assertThat(prepared.request().records()).hasSize(1);
        assertThat(prepared.request().records().get(0).content()).isEqualTo("公开授权文本");
        assertThat(prepared.request().records().get(0).ref()).isEqualTo("r1");
        assertThat(prepared.request().stats().total()).isEqualTo(2);
        assertThat(prepared.request().aiEnabledTotal()).isEqualTo(1);
    }

    @Test
    void d2DisabledCrisisRecordBeyondTruncationBlocksAllModelCalls() {
        long user = userWithRecord(true, "普通的散步");
        jdbc.update("INSERT INTO mood_record(user_id,mood_type,content,ai_reply_enabled,content_version,create_time) VALUES (?,'难过',?,0,1,'2026-11-04 12:00:00')", user, "文".repeat(350) + "我想自杀");
        var prepared = reviews.prepare(claim(user), 1, "review-test");
        assertThat(prepared.support()).isNotNull();
        assertThat(prepared.support().modelCalled()).isFalse();
        assertThat(prepared.support().resultKind()).isEqualTo("SUPPORT");
        assertThat(prepared.request().records()).isEmpty();
    }

    @Test
    void reportBecomesStaleOnEditAndHidesBodyAfterDeletionWithoutWritingOnGet() {
        long user = userWithRecord(true, "散步");
        long job = claim(user);
        var prepared = reviews.prepare(job, 1, "review-test");
        assertThat(reviews.complete(job, 1, "review-test", response(prepared.request(), "散步了{{r1}}"))).isTrue();
        assertThat(reviews.get(user, "WEEK", "2026-11-02").report().stale()).isFalse();
        jdbc.update("UPDATE mood_record SET content_version=2 WHERE user_id=?", user);
        assertThat(reviews.get(user, "WEEK", "2026-11-02").report().stale()).isTrue();
        assertThat(reviews.get(user, "WEEK", "2026-11-02").report().body()).isEqualTo("散步了{{r1}}");
        jdbc.update("UPDATE mood_record SET status=0,content_version=3 WHERE user_id=?", user);
        assertThat(reviews.get(user, "WEEK", "2026-11-02").report().body()).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_job WHERE id=?", String.class, job)).isEqualTo("succeeded");
    }

    @Test
    void actualWorkerHttpFiltersD1AndD2KeepsProviderCallCountUnchanged() throws Exception {
        startPython();
        long user = userWithRecord(true, "实际HTTP授权正文");
        jdbc.update("INSERT INTO mood_record(user_id,mood_type,content,ai_reply_enabled,content_version,create_time) VALUES (?,'难过','关闭AI的正文不能发送',0,1,'2026-11-04 12:00:00')", user);
        var report = reviews.generate(user, "WEEK", "2026-11-02", false);
        int before = httpGet("/test/state").get("calls").size();
        try (var client = new AiServiceClient(pythonUrl, "test-internal-secret", 3)) {
            var worker = new AiJobWorker(jobsService(), client, manager, Runnable::run, System::nanoTime);
            assertThat(worker.runOne().get(10, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.SUCCEEDED);
            var calls = httpGet("/test/state").get("calls");
            assertThat(calls.size()).isEqualTo(before + 1);
            var sent = calls.get(before);
            assertThat(sent.get("records").size()).isEqualTo(1);
            assertThat(sent.get("records").get(0).get("content").asText()).isEqualTo("实际HTTP授权正文");
            assertThat(sent.get("records").get(0).get("weekday").asText()).isEqualTo("周二");
            assertThat(sent.toString()).doesNotContain("关闭AI的正文不能发送", "user_id", "record_id");
            assertThat(sent.get("stats").get("total").asInt()).isEqualTo(2);
            assertThat(jobsService().getOwned(user, report.jobId()).status()).isEqualTo("succeeded");

            long crisisUser = userWithRecord(true, "散步");
            jdbc.update("INSERT INTO mood_record(user_id,mood_type,content,ai_reply_enabled,content_version,create_time) VALUES (?,'难过',?,0,1,'2026-11-04 12:00:00')", crisisUser, "文".repeat(400) + "我想自杀");
            reviews.generate(crisisUser, "WEEK", "2026-11-02", false);
            assertThat(worker.runOne().get(10, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.SUCCEEDED);
            assertThat(httpGet("/test/state").get("calls").size()).isEqualTo(before + 1);
            assertThat(reviews.get(crisisUser, "WEEK", "2026-11-02").report().resultKind()).isEqualTo("SUPPORT");
        }
    }

    @Test
    void failedRegenerationKeepsOldSuccessAndRetryUsesSameJobWithCooldownAndRecovery() {
        long user = userWithRecord(true, "散步");
        long firstJob = claim(user);
        var prepared = reviews.prepare(firstJob, 1, "review-test");
        assertThat(reviews.complete(firstJob, 1, "review-test", response(prepared.request(), "旧信{{r1}}"))).isTrue();
        long oldId = reviews.get(user, "WEEK", "2026-11-02").report().id();
        assertThat(reviews.generate(user, "WEEK", "2026-11-02", false).id()).isEqualTo(oldId);
        var next = reviews.generate(user, "WEEK", "2026-11-02", true);
        var service = jobsService();
        var tx = new TransactionTemplate(manager);
        var job = tx.execute(status -> service.claimNext());
        assertThat(job.getId()).isEqualTo(next.jobId());
        boolean failed = tx.execute(status -> service.fail(job.getId(), 1, job.getRequestId(), "provider_error"));
        assertThat(failed).isTrue();
        assertThat(reviews.get(user, "WEEK", "2026-11-02").report().id()).isEqualTo(oldId);
        assertThat(service.retry(user, job.getId()).id()).isEqualTo(job.getId());
        var second = tx.execute(status -> service.claimNext());
        assertThat(second.getAttemptCount()).isEqualTo(2);
        tx.execute(status -> service.recoverInterrupted());
        assertThat(service.getOwned(user, second.getId()).errorCode()).isEqualTo("execution_interrupted");
        jdbc.update("UPDATE ai_job SET attempt_count=3,finished_at=CURRENT_TIMESTAMP WHERE id=?", second.getId());
        assertThatThrownBy(() -> service.retry(user, second.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(problem -> assertThat(((ResponseStatusException) problem).getStatusCode().value()).isEqualTo(429));
        assertThatThrownBy(() -> service.getOwned(user + 10, second.getId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void foreignCitationFailsWithoutReplacingOldContentAndHistoryContainsNoBody() {
        long user = userWithRecord(true, "散步");
        long job = claim(user);
        var prepared = reviews.prepare(job, 1, "review-test");
        assertThat(reviews.complete(job, 1, "review-test", response(prepared.request(), "未知引用{{r99}}"))).isFalse();
        assertThat(jobsService().getOwned(user, job).errorCode()).isEqualTo("invalid_model_response");
        assertThat(reviews.get(user, "WEEK", "2026-11-02").report()).isNull();
        assertThat(reviews.history(user, 1)).hasSize(1);
        assertThat(reviews.history(user + 10, 1)).isEmpty();
    }

    @Test
    void recent30RollsOverAndSampleIsBoundedWhileCrisisChecksUnsampledRecords() {
        long user = userWithRecord(true, "头" + "😀".repeat(200));
        for (int index = 0; index < 70; index++) {
            jdbc.update("INSERT INTO mood_record(user_id,mood_type,content,ai_reply_enabled,content_version,create_time) VALUES (?,'平静',?,1,1,'2026-11-04 12:00:00')", user, "记录" + index);
        }
        var report = reviews.generate(user, "RECENT_30", null, false);
        var tx = new TransactionTemplate(manager);
        var job = tx.execute(status -> jobsService().claimNext());
        var prepared = reviews.prepare(job.getId(), 1, job.getRequestId());
        assertThat(prepared.request().records()).hasSize(60);
        assertThat(prepared.request().aiEnabledTotal()).isEqualTo(71);
        assertThat(prepared.request().records().stream().mapToInt(e -> e.content().length()).sum()).isLessThanOrEqualTo(12000);
        assertThat(prepared.request().records().get(0).content()).isEqualTo("头" + "😀".repeat(200));
        assertThat(prepared.request().records().get(59).content()).isEqualTo("记录69");
        assertThat(reviews.complete(job.getId(), 1, job.getRequestId(), response(prepared.request(), "旧回顾{{r1}}"))).isTrue();
        assertThat(reviews.get(user, "RECENT_30", null).report().aiEnabledCount()).isEqualTo(71);
        var tomorrow = Clock.offset(clock, Duration.ofDays(1));
        var nextDay = new MoodReviewService(new MoodReviewMapper(jdbc),
                new MoodReviewStatsService(sessions.getMapper(MoodRecordMapper.class), tomorrow),
                tomorrow, manager, new AiJobAdmission(manager), new AlwaysAllowRateLimiter());
        assertThat(nextDay.get(user, "RECENT_30", null).report().stale()).isTrue();
        assertThat(nextDay.get(user, "RECENT_30", null).report().id()).isEqualTo(report.id());

        jdbc.update("UPDATE mood_record SET content='I　want to kill myself',content_version=2,ai_reply_enabled=0 WHERE user_id=? AND content='记录1'", user);
        var next = reviews.generate(user, "RECENT_30", null, false);
        var crisisJob = tx.execute(status -> jobsService().claimNext());
        assertThat(reviews.prepare(crisisJob.getId(), 1, crisisJob.getRequestId()).support()).isNotNull();
    }

    @Test
    void emptyDisabledAndUnfinishedWeekDoNotCreateJobs() {
        long user = userWithRecord(false, "不授权");
        assertThatThrownBy(() -> reviews.generate(user, "WEEK", "2026-11-02", false))
                .hasMessageContaining("no_ai_enabled_records");
        assertThatThrownBy(() -> reviews.generate(user, "WEEK", "2026-10-26", false))
                .hasMessageContaining("no_records");
        assertThatThrownBy(() -> reviews.generate(user, "WEEK", "2026-11-09", false))
                .hasMessageContaining("week_not_finished");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE user_id=?", Integer.class, user)).isZero();
    }

    @Test
    void lateAttemptCannotSubmitOrOverwriteNewerAttempt() {
        long user = userWithRecord(true, "散步");
        long job = claim(user);
        var prepared = reviews.prepare(job, 1, "review-test");
        jdbc.update("UPDATE ai_job SET attempt_count=2,request_id='new-attempt' WHERE id=?", job);
        assertThat(reviews.complete(job, 1, "review-test", response(prepared.request(), "迟到结果{{r1}}"))).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_job WHERE id=?", String.class, job)).isEqualTo("running");
    }

    @Test
    void failedOldRevisionCannotRetryWhenNewRevisionIsAlreadyQueued() {
        long user = userWithRecord(true, "散步");
        long oldJob = claim(user);
        var tx = new TransactionTemplate(manager);
        tx.execute(status -> reviews.fail(oldJob, 1, "review-test", "provider_error"));
        var newer = reviews.generate(user, "WEEK", "2026-11-02", true);
        assertThatThrownBy(() -> reviews.retry(user, oldJob))
                .hasMessageContaining("review_already_active");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE user_id=? AND status IN ('queued','running')", Integer.class, user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM ai_job WHERE id=?", String.class, newer.jobId())).isEqualTo("queued");
    }

    @Test
    void budgetAllocatesFull60LongRecordsAndPreservesEmojiBoundariesAndOriginalCrisisCheck() {
        long user = ++nextUser;
        jdbc.update("INSERT INTO user(id) VALUES (?)", user);
        for (int index = 0; index < 60; index++) {
            String content = String.format("%02d", index) + "😀".repeat(498) + "尾" + index;
            if (content.length() > 1000) content = content.substring(0, 999);
            String time = String.format("2026-11-%02d %02d:00:00", 2 + (index / 10), 8 + (index % 10));
            jdbc.update("INSERT INTO mood_record(user_id,mood_type,content,ai_reply_enabled,content_version,create_time) VALUES (?,'平静',?,1,1,?)",
                    user, content, time);
        }
        reviews.generate(user, "WEEK", "2026-11-02", false);
        var tx = new TransactionTemplate(manager);
        var job = tx.execute(status -> jobsService().claimNext());
        var prepared = reviews.prepare(job.getId(), 1, job.getRequestId());
        assertThat(prepared.support()).isNull();
        assertThat(prepared.request().records()).hasSize(60);
        assertThat(prepared.request().records().stream().mapToInt(e -> e.content().length()).sum()).isLessThanOrEqualTo(12000);
        for (int i = 0; i < 60; i++) {
            var entry = prepared.request().records().get(i);
            assertThat(entry.ref()).isEqualTo("r" + (i + 1));
            assertThat(entry.content().length()).isBetween(190, 200);
            assertThat(entry.content()).startsWith(String.format("%02d", i)).contains("〔中间省略〕");
            assertThat(hasLoneSurrogate(entry.content())).isFalse();
        }
        assertThat(reviews.complete(job.getId(), 1, job.getRequestId(), response(prepared.request(), "收尾{{r1}}"))).isTrue();

        long crisisUser = ++nextUser;
        jdbc.update("INSERT INTO user(id) VALUES (?)", crisisUser);
        for (int index = 0; index < 59; index++) {
            jdbc.update("INSERT INTO mood_record(user_id,mood_type,content,ai_reply_enabled,content_version,create_time) VALUES (?,'平静',?,1,1,'2026-11-03 10:00:00')",
                    crisisUser, "甲".repeat(1000));
        }
        String hiddenCrisis = "头".repeat(300) + "我想自杀" + "尾".repeat(300);
        jdbc.update("INSERT INTO mood_record(user_id,mood_type,content,ai_reply_enabled,content_version,create_time) VALUES (?,'难过',?,1,1,'2026-11-04 10:00:00')",
                crisisUser, hiddenCrisis);
        reviews.generate(crisisUser, "WEEK", "2026-11-02", false);
        var crisisJob = tx.execute(status -> jobsService().claimNext());
        var crisisPrepared = reviews.prepare(crisisJob.getId(), 1, crisisJob.getRequestId());
        assertThat(crisisPrepared.support()).isNotNull();
        assertThat(crisisPrepared.request().records()).isEmpty();
    }

    @Test
    void reconstructedSourcesMatchSentRequestAcrossSamplingAndDisabledRecords() throws Exception {
        startPython();
        long user = ++nextUser;
        jdbc.update("INSERT INTO user(id) VALUES (?)", user);
        for (int index = 0; index < 75; index++) {
            String time = String.format("2026-11-%02d %02d:00:00", 2 + (index / 15), 8 + (index % 15));
            String content = index == 0 ? "长首段" + "甲".repeat(900) + "长尾段" : "授权正文-" + index;
            jdbc.update("INSERT INTO mood_record(user_id,mood_type,content,ai_reply_enabled,content_version,create_time) VALUES (?,'平静',?,1,1,?)",
                    user, content, time);
        }
        for (int index = 0; index < 10; index++) {
            jdbc.update("INSERT INTO mood_record(user_id,mood_type,content,ai_reply_enabled,content_version,create_time) VALUES (?,'难过',?,0,1,'2026-11-05 12:30:00')",
                    user, "关闭AI-" + index);
        }

        reviews.generate(user, "WEEK", "2026-11-02", false);
        int before = httpGet("/test/state").get("calls").size();
        try (var client = new AiServiceClient(pythonUrl, "test-internal-secret", 3)) {
            var worker = new AiJobWorker(jobsService(), client, manager, Runnable::run, System::nanoTime);
            assertThat(worker.runOne().get(10, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.SUCCEEDED);
        }

        var sentRecords = httpGet("/test/state").get("calls").get(before).get("records");
        assertThat(sentRecords.size()).isEqualTo(60);

        var firstPage = reviews.get(user, "WEEK", "2026-11-02");
        assertThat(firstPage.report().sources()).hasSize(1);
        var firstSource = firstPage.report().sources().get(0);
        assertThat(firstSource.ref()).isEqualTo(sentRecords.get(0).get("ref").asText());
        assertThat(firstSource.date().toString()).isEqualTo(sentRecords.get(0).get("date").asText());
        assertThat(firstSource.weekday()).isEqualTo(sentRecords.get(0).get("weekday").asText());
        assertThat(firstSource.moodType()).isEqualTo(sentRecords.get(0).get("mood_type").asText());
        assertThat(firstSource.content()).isEqualTo("长首段" + "甲".repeat(900) + "长尾段");

        var regen = reviews.generate(user, "WEEK", "2026-11-02", true);
        var tx = new TransactionTemplate(manager);
        var claimed = tx.execute(status -> jobsService().claimNext());
        assertThat(claimed.getId()).isEqualTo(regen.jobId());
        var prepared = reviews.prepare(claimed.getId(), 1, claimed.getRequestId());
        assertThat(prepared.request().records()).hasSize(60);
        assertThat(reviews.complete(claimed.getId(), 1, claimed.getRequestId(),
                response(prepared.request(), "提到{{r60}}、{{r1}}和{{r30}}。"))).isTrue();

        var reloaded = reviews.get(user, "WEEK", "2026-11-02");
        var sources = reloaded.report().sources();
        assertThat(sources).extracting(MoodReviewService.Source::ref).containsExactly("r1", "r30", "r60");
        for (var source : sources) {
            int idx = Integer.parseInt(source.ref().substring(1)) - 1;
            var sent = prepared.request().records().get(idx);
            assertThat(source.date().toString()).isEqualTo(sent.date());
            assertThat(source.weekday()).isEqualTo(sent.weekday());
            assertThat(source.moodType()).isEqualTo(sent.moodType());
            assertThat(source.content()).doesNotContain("关闭AI-");
        }
        assertThat(sources.get(2).content()).isEqualTo(prepared.request().records().get(59).content());
        assertThat(sources.get(0).content()).isEqualTo("长首段" + "甲".repeat(900) + "长尾段");

        jdbc.update("UPDATE mood_record SET status=0,content_version=2 WHERE user_id=? AND content LIKE '关闭AI-%' LIMIT 1", user);
        assertThat(reviews.get(user, "WEEK", "2026-11-02").report().sources()).isEmpty();
    }

    @Test
    void historyDeduplicatesRevisionsPerWeekAndPaginatesByFive() {
        long user = ++nextUser;
        jdbc.update("INSERT INTO user(id) VALUES (?)", user);
        LocalDate baseMonday = LocalDate.of(2026, 9, 28);
        var tx = new TransactionTemplate(manager);
        for (int week = 0; week < 6; week++) {
            LocalDate weekStart = baseMonday.plusWeeks(week);
            jdbc.update("INSERT INTO mood_record(user_id,mood_type,content,ai_reply_enabled,content_version,create_time) VALUES (?,'平静',?,1,1,?)",
                    user, "第" + week + "周记录", weekStart.atTime(10, 0));
            var generated = reviews.generate(user, "WEEK", weekStart.toString(), false);
            var claimed = tx.execute(status -> jobsService().claimNext());
            assertThat(claimed.getId()).isEqualTo(generated.jobId());
            var prepared = reviews.prepare(claimed.getId(), 1, claimed.getRequestId());
            assertThat(reviews.complete(claimed.getId(), 1, claimed.getRequestId(),
                    response(prepared.request(), "第" + week + "周{{r1}}。"))).isTrue();
        }
        String newestWeek = baseMonday.plusWeeks(5).toString();
        var regen = reviews.generate(user, "WEEK", newestWeek, true);
        assertThat(regen.revision()).isEqualTo(2);

        var page1 = reviews.history(user, 1);
        var page2 = reviews.history(user, 2);

        assertThat(page1).hasSize(5);
        assertThat(page1.get(0).rangeStart()).isEqualTo(baseMonday.plusWeeks(5));
        assertThat(page1.get(0).revision()).isEqualTo(2);
        assertThat(page1.stream().map(MoodReviewService.History::rangeStart).distinct()).hasSize(5);
        assertThat(page2).hasSize(1);
        assertThat(page2.get(0).rangeStart()).isEqualTo(baseMonday);
    }

    private static boolean hasLoneSurrogate(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) return true;
            } else if (Character.isLowSurrogate(ch)) {
                return true;
            }
        }
        return false;
    }
}
