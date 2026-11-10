package com.xinyu.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.xinyu.entity.MoodRecord;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.mapper.AiReplyMapper;
import com.xinyu.dto.MoodAiResponse;
import com.xinyu.dto.MoodAiRequest;
import com.xinyu.service.AiJobWorker;
import com.xinyu.service.AiJobRuntime;
import com.xinyu.service.AiServiceClient;
import com.xinyu.service.AiServiceException;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.service.AiJobAdmission;
import com.xinyu.service.AiJobService;
import com.xinyu.service.MoodRecordService;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 使用实际 MySQL、MyBatis 与 Spring 事务；没有指定隔离库时明确跳过。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiJobPersistenceIT {
    private final AtomicLong userSequence = new AtomicLong(System.currentTimeMillis());
    private JdbcTemplate jdbc;
    private MoodRecordMapper moods;
    private AiJobMapper jobs;
    private AiJobService jobService;
    private AiReplyMapper replies;
    private DataSourceTransactionManager manager;
    private AiJobAdmission admission;
    private MoodRecordService service;
    private TransactionTemplate tx;
    private SqlSessionTemplate sessions;
    private long userId;

    @BeforeAll
    void connectAndMigrate() throws Exception {
        String url = System.getenv("AI_TEST_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(), "未配置显式的 AI_TEST_JDBC_URL 隔离测试库");
        URI uri = URI.create(url.substring(5));
        if (!url.startsWith("jdbc:mysql://") || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || uri.getPath() == null
                || !uri.getPath().matches("/xinyu_test_[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("测试仅接受无额外参数的 xinyu_test_ 前缀 MySQL JDBC URL");
        }
        DriverManagerDataSource source = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("AI_TEST_DB_USER", "root"),
                System.getenv().getOrDefault("AI_TEST_DB_PASSWORD", ""));
        jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).matches("xinyu_test_[A-Za-z0-9_]+");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class))
                .as("必须指向人工创建的空测试库").isZero();
        new ResourceDatabasePopulator(new ClassPathResource("ai/schema.sql")).execute(source);
        // 先在旧表上独立执行扩列，证明其不依赖任务表迁移。
        Path modelNameMigration = Path.of("../sql/migrations/20261107_02_ai_reply_model_name.sql").toAbsolutePath();
        new ResourceDatabasePopulator(new FileSystemResource(modelNameMigration)).execute(source);
        Path migration = Path.of("../sql/migrations/20261107_01_ai_reply_job.sql").toAbsolutePath();
        new ResourceDatabasePopulator(new FileSystemResource(migration)).execute(source);
        manager = new DataSourceTransactionManager(source);
        tx = new TransactionTemplate(manager);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(MoodRecordMapper.class);
        configuration.addMapper(AiJobMapper.class);
        configuration.addMapper(AiReplyMapper.class);
        factory.setConfiguration(configuration);
        com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor pagination = new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.MYSQL));
        factory.setPlugins(pagination);
        sessions = new SqlSessionTemplate(factory.getObject());
        moods = sessions.getMapper(MoodRecordMapper.class);
        jobs = sessions.getMapper(AiJobMapper.class);
        replies = sessions.getMapper(AiReplyMapper.class);
        admission = new AiJobAdmission(manager);
        jobService = new AiJobService(jobs, moods, replies, admission, true);
        service = new MoodRecordService(moods, jobService, admission);
    }

    @BeforeEach
    void prepareUser() {
        userId = userSequence.incrementAndGet();
        jdbc.update("INSERT INTO user(id) VALUES (?)", userId);
    }

    @AfterEach
    void cleanupOwnedTestRows() {
        jdbc.execute("DROP TRIGGER IF EXISTS xinyu_test_fail_job_insert");
        jdbc.execute("DROP TRIGGER IF EXISTS xinyu_test_fail_job_update");
        jdbc.execute("DROP TRIGGER IF EXISTS xinyu_test_fail_log_insert");
        jdbc.update("DELETE FROM ai_reply WHERE user_id=?", userId);
        jdbc.update("DELETE FROM ai_job WHERE user_id=?", userId);
        jdbc.update("DELETE FROM mood_record WHERE user_id=?", userId);
        jdbc.update("DELETE FROM user WHERE id=?", userId);
    }

    MoodRecord input() {
        MoodRecord value = new MoodRecord();
        value.setUserId(userId);
        value.setMoodType("平静");
        value.setContent("原始内容");
        return value;
    }

    @Test
    void modelNameMigrationPreservesExistingLogsAndAccepts128Characters() {
        assertThat(jdbc.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='ai_reply' AND column_name='model_name'", Long.class))
                .as("数据库必须容纳内部契约允许的完整模型名称").isEqualTo(128L);
        var old = jdbc.queryForMap("SELECT model_name,prompt,reply_content,status,create_time FROM ai_reply WHERE id=501");
        assertThat(old.get("model_name")).isEqualTo("m".repeat(100));
        assertThat(old.get("prompt")).isEqualTo("历史合成提示");
        assertThat(old.get("reply_content")).isEqualTo("历史合成回复");
        assertThat(((Number) old.get("status")).intValue()).isEqualTo(1);
        assertThat(old.get("create_time").toString()).startsWith("2000-01-03");
        com.xinyu.entity.AiReply log = new com.xinyu.entity.AiReply();
        log.setUserId(userId); log.setTargetType("MOOD_RECORD"); log.setTargetId(101L);
        log.setModelName("m".repeat(128)); log.setReplyContent("合成回复"); log.setStatus(1);
        assertThat(replies.insert(log)).isEqualTo(1);
        assertThat(replies.selectById(log.getId()).getModelName()).isEqualTo("m".repeat(128));
        assertThat(replies.selectById(log.getId()).getPrompt()).isNull();
    }

    @Test
    void migrationOnlyBackfillsActiveHistoricalReplies() {
        assertThat(jdbc.queryForList("SELECT ai_reply_enabled FROM mood_record WHERE id IN (101,102,103) ORDER BY id", Integer.class))
                .containsExactly(1, 0, 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mood_record WHERE id IN (101,102,103) AND content_version=1 AND deleted_at IS NULL", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE user_id=1", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mood_record WHERE id IN (101,102,103) AND create_time='2000-01-01 00:00:00' AND update_time='2000-01-02 00:00:00'", Integer.class))
                .as("回填开关不能改写历史日记的创建与修改时间").isEqualTo(3);
    }

    @Test
    void saveUpdateCloseAndDeletePersistOneCoherentVersionChain() {
        var saved = service.add(input());
        assertThat(saved.getAiReplyState()).isEqualTo("queued");
        assertThat(moods.selectById(saved.getId()).getContentVersion()).isEqualTo(1);
        MoodRecord edit = input();
        edit.setContent("修改的内容");
        var updated = service.update(saved.getId(), userId, edit);
        assertThat(updated.getContentVersion()).isEqualTo(2);
        assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("superseded");
        assertThat(jobs.selectById(updated.getJobId()).getTargetVersion()).isEqualTo(2);
        edit.setNeedAiReply(false);
        var closed = service.update(saved.getId(), userId, edit);
        assertThat(closed.getJobId()).isNull();
        assertThat(moods.selectById(saved.getId()).getAiReplyEnabled()).isFalse();
        assertThat(service.delete(saved.getId(), userId)).isTrue();
        MoodRecord deleted = moods.selectById(saved.getId());
        assertThat(deleted.getStatus()).isZero();
        assertThat(deleted.getContentVersion()).isEqualTo(4);
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getAiReply()).isNull();
        assertThat(service.delete(saved.getId(), userId)).isFalse();
        assertThat(service.update(saved.getId(), userId, edit)).isNull();
        assertThat(moods.selectById(saved.getId()).getDeletedAt()).isEqualTo(deleted.getDeletedAt());
        assertThat(moods.selectById(saved.getId()).getContentVersion()).isEqualTo(4);
        assertThat(service.getDetail(saved.getId(), userId)).isNull();
        assertThat(service.listByUserId(userId)).isEmpty();
    }

    @Test
    void deletedHistoricalUnknownTimeDoesNotGetFabricated() {
        assertThat(service.delete(103L, 1L)).isFalse();
        assertThat(moods.selectById(103L).getDeletedAt()).isNull();
        assertThat(moods.selectById(103L).getContentVersion()).isEqualTo(1);
    }

    @Test
    void uniqueVersionRejectsDuplicateJobs() {
        var saved = service.add(input());
        var duplicate = jobs.selectById(saved.getJobId());
        duplicate.setId(null);
        assertThatThrownBy(() -> jobs.insert(duplicate)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void userCannotUpdateOrDeleteAnothersSource() {
        var saved = service.add(input());
        assertThat(service.update(saved.getId(), userId + 100, input())).isNull();
        assertThat(service.delete(saved.getId(), userId + 100)).isFalse();
        assertThat(moods.selectById(saved.getId()).getContentVersion()).isEqualTo(1);
    }

    @Test
    void jobInsertFailureRollsBackSourceAndInvalidation() {
        var saved = service.add(input());
        jdbc.execute("CREATE TRIGGER xinyu_test_fail_job_insert BEFORE INSERT ON ai_job FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic failure'");
        MoodRecord edit = input();
        edit.setContent("不能部分保存");
        assertThatThrownBy(() -> service.update(saved.getId(), userId, edit)).isInstanceOf(RuntimeException.class);
        MoodRecord source = moods.selectById(saved.getId());
        assertThat(source.getContent()).isEqualTo("原始内容");
        assertThat(source.getContentVersion()).isEqualTo(1);
        assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("queued");
        assertThatThrownBy(() -> service.add(input())).isInstanceOf(RuntimeException.class);
        assertThat(service.listByUserId(userId)).hasSize(1);
    }

    @Test
    void taskInvalidationFailureRollsBackWholeSoftDelete() {
        var saved = service.add(input());
        jdbc.update("UPDATE mood_record SET ai_reply='保留的回复' WHERE id=?", saved.getId());
        jdbc.execute("CREATE TRIGGER xinyu_test_fail_job_update BEFORE UPDATE ON ai_job FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic failure'");
        assertThatThrownBy(() -> service.delete(saved.getId(), userId)).isInstanceOf(RuntimeException.class);
        MoodRecord source = moods.selectById(saved.getId());
        assertThat(source.getStatus()).isEqualTo(1);
        assertThat(source.getContentVersion()).isEqualTo(1);
        assertThat(source.getDeletedAt()).isNull();
        assertThat(source.getAiReplyEnabled()).isTrue();
        assertThat(source.getAiReply()).isEqualTo("保留的回复");
        assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("queued");
    }

    @Test
    void failedAndRunningJobsAreAlsoInvalidatedOnDelete() {
        var first = service.add(input());
        jdbc.update("UPDATE ai_job SET status='running',attempt_count=1,request_id='request-1' WHERE id=?", first.getJobId());
        assertThat(service.delete(first.getId(), userId)).isTrue();
        assertThat(jobs.selectById(first.getJobId()).getStatus()).isEqualTo("superseded");
        var second = service.add(input());
        jdbc.update("UPDATE ai_job SET status='failed' WHERE id=?", second.getJobId());
        assertThat(service.delete(second.getId(), userId)).isTrue();
        assertThat(jobs.selectById(second.getJobId()).getStatus()).isEqualTo("superseded");
    }

    @Test
    void statusAndVersionConditionsRejectStaleOrDeletedWrites() {
        var saved = service.add(input());
        assertThat(moods.updateContentAtVersion(saved.getId(), userId, 2L, "开心", "过期", true)).isZero();
        assertThat(moods.softDeleteAtVersion(saved.getId(), userId, 2L)).isZero();
        jdbc.update("UPDATE mood_record SET status=0 WHERE id=?", saved.getId());
        assertThat(moods.updateContentAtVersion(saved.getId(), userId, 1L, "开心", "异常同版本", true)).isZero();
        assertThat(moods.softDeleteAtVersion(saved.getId(), userId, 1L)).isZero();
    }

    @Test
    void concurrentSavesRespectUserCapacityThroughCommit() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var tasks = java.util.stream.IntStream.range(0, 4).mapToObj(i -> executor.submit(() -> {
                start.await();
                return service.add(input());
            })).toList();
            start.countDown();
            for (var task : tasks) assertThat(task.get(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mood_record WHERE user_id=?", Integer.class, userId)).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE user_id=? AND status='queued'", Integer.class, userId)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE user_id=? AND status='failed' AND attempt_count=0 AND last_error_code='capacity_exceeded'", Integer.class, userId)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void deleteHoldingRealRowLockMakesUpdateWaitThenRejectsResurrection() throws Exception {
        var saved = service.add(input());
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> delete = executor.submit(() -> tx.execute(status -> {
                MoodRecord source = moods.selectOwnedForUpdate(saved.getId(), userId);
                assertThat(moods.softDeleteAtVersion(saved.getId(), userId, source.getContentVersion())).isEqualTo(1);
                jobs.supersedeUnfinished(saved.getId(), userId);
                locked.countDown();
                try { assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
                return null;
            }));
            assertThat(locked.await(3, TimeUnit.SECONDS)).isTrue();
            Future<?> update = executor.submit(() -> service.update(saved.getId(), userId, input()));
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            boolean waited = false;
            while (System.nanoTime() < deadline && !update.isDone()) {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class) > 0) {
                    waited = true;
                    break;
                }
                Thread.yield();
            }
            assertThat(waited).as("数据库明确观察到第二连接等待来源行锁").isTrue();
            assertThat(update.isDone()).isFalse();
            release.countDown();
            delete.get(3, TimeUnit.SECONDS);
            assertThat(update.get(3, TimeUnit.SECONDS)).isNull();
            MoodRecord source = moods.selectById(saved.getId());
            assertThat(source.getStatus()).isZero();
            assertThat(source.getContentVersion()).isEqualTo(2);
            assertThat(source.getContent()).isEqualTo("原始内容");
            assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("superseded");
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void updateCommitsBeforeWaitingDeleteAndFinalSourceStaysDeleted() throws Exception {
        var saved = service.add(input());
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> update = executor.submit(() -> tx.execute(status -> {
                MoodRecord source = moods.selectOwnedForUpdate(saved.getId(), userId);
                assertThat(moods.updateContentAtVersion(saved.getId(), userId, source.getContentVersion(),
                        "开心", "先提交的新内容", false)).isEqualTo(1);
                jobs.supersedeUnfinished(saved.getId(), userId);
                locked.countDown();
                try { assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
                return null;
            }));
            assertThat(locked.await(3, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> delete = executor.submit(() -> service.delete(saved.getId(), userId));
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            boolean waited = false;
            while (System.nanoTime() < deadline && !delete.isDone()) {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class) > 0) {
                    waited = true;
                    break;
                }
                Thread.yield();
            }
            assertThat(waited).as("数据库明确观察到删除等待修改事务释放行锁").isTrue();
            release.countDown();
            update.get(3, TimeUnit.SECONDS);
            assertThat(delete.get(3, TimeUnit.SECONDS)).isTrue();
            MoodRecord source = moods.selectById(saved.getId());
            assertThat(source.getStatus()).isZero();
            assertThat(source.getContentVersion()).isEqualTo(3);
            assertThat(source.getContent()).isEqualTo("先提交的新内容");
            assertThat(source.getDeletedAt()).isNotNull();
            assertThat(source.getAiReplyEnabled()).isFalse();
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    MoodAiResponse response(com.xinyu.entity.AiJob job) {
        return new MoodAiResponse(job.getRequestId(), job.getId().toString(), job.getAttemptCount(),
                job.getTargetVersion().toString(), "  合成回复。  ", "MODEL", true, "mood-reply-v1", "m".repeat(128));
    }

    int logs() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ai_reply WHERE user_id=?", Integer.class, userId);
    }

    @Test
    void claimCompleteAndDeletePreserveVisibilityAndCompatibleLog() {
        var saved = service.add(input());
        var job = jobService.claimNext();
        assertThat(job.getId()).isEqualTo(saved.getJobId());
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getRequestId()).isNotBlank();
        assertThat(jobService.sourceForAttempt(job.getId(), 1, job.getRequestId())).isNotNull();
        assertThat(jobService.complete(job.getId(), 1, job.getRequestId(), response(job))).isTrue();
        assertThat(jobService.complete(job.getId(), 1, job.getRequestId(), response(job))).isFalse();
        assertThat(moods.selectById(saved.getId()).getAiReply()).isEqualTo("合成回复。");
        assertThat(moods.selectById(saved.getId()).getContentVersion()).isEqualTo(1);
        assertThat(moods.selectById(saved.getId()).getDeletedAt()).isNull();
        assertThat(logs()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT model_name FROM ai_reply WHERE user_id=?", String.class, userId)).hasSize(128);
        assertThat(jdbc.queryForObject("SELECT prompt FROM ai_reply WHERE user_id=?", String.class, userId)).isNull();
        assertThat(jobService.getOwned(userId, job.getId()).replyContent()).isEqualTo("合成回复。");
        assertThat(service.delete(saved.getId(), userId)).isTrue();
        assertThat(moods.selectById(saved.getId()).getAiReply()).isNull();
        assertThat(logs()).isEqualTo(1);
        assertThatThrownBy(() -> jobService.getOwned(userId, job.getId())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void deleteBeforeCompleteAndMatchingVersionDeletedRowsRejectAllWriteback() {
        for (boolean abnormal : new boolean[]{false, true}) {
            var saved = service.add(input());
            var job = jobService.claimNext();
            if (abnormal) jdbc.update("UPDATE mood_record SET status=0 WHERE id=?", saved.getId());
            else service.delete(saved.getId(), userId);
            assertThat(jobService.sourceForAttempt(job.getId(), 1, job.getRequestId())).isNull();
            assertThat(jobService.complete(job.getId(), 1, job.getRequestId(), response(job))).isFalse();
            assertThat(moods.selectById(saved.getId()).getStatus()).isZero();
            assertThat(moods.selectById(saved.getId()).getAiReply()).isNull();
            assertThat(jobs.selectById(job.getId()).getStatus()).isEqualTo("superseded");
            assertThat(logs()).isZero();
        }
    }

    @Test
    void supportSkipsProviderLogAndLogFailureRollsBackWholeCompletion() {
        var saved = service.add(input());
        var job = jobService.claimNext();
        jdbc.execute("CREATE TRIGGER xinyu_test_fail_log_insert BEFORE INSERT ON ai_reply FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic failure'");
        assertThatThrownBy(() -> jobService.complete(job.getId(), 1, job.getRequestId(), response(job))).isInstanceOf(RuntimeException.class);
        assertThat(jobs.selectById(job.getId()).getStatus()).isEqualTo("running");
        assertThat(moods.selectById(saved.getId()).getAiReply()).isNull();
        assertThat(logs()).isZero();
        jdbc.execute("DROP TRIGGER xinyu_test_fail_log_insert");
        var support = new MoodAiResponse(job.getRequestId(), job.getId().toString(), 1, "1", "合成支持回复", "SUPPORT", false, "mood-reply-v1", null);
        assertThat(jobService.complete(job.getId(), 1, job.getRequestId(), support)).isTrue();
        assertThat(jobService.getOwned(userId, job.getId()).resultKind()).isEqualTo("SUPPORT");
        assertThat(jobs.selectById(job.getId()).getModelCalled()).isFalse();
        assertThat(logs()).isZero();
    }

    @Test
    void retriesReuseJobAdvanceAttemptAndRejectOldResults() {
        var saved = service.add(input());
        var first = jobService.claimNext();
        assertThat(jobService.fail(first.getId(), 1, first.getRequestId(), "provider_timeout")).isTrue();
        assertThat(jobs.selectById(first.getId()).getModelCalled()).isNull();
        assertThat(jobService.retry(userId, first.getId()).id()).isEqualTo(saved.getJobId());
        var second = jobService.claimNext();
        assertThat(second.getAttemptCount()).isEqualTo(2);
        assertThat(second.getRequestId()).isNotEqualTo(first.getRequestId());
        assertThat(jobService.complete(first.getId(), 1, first.getRequestId(), response(first))).isFalse();
        assertThat(jobService.fail(first.getId(), 1, first.getRequestId(), "provider_error")).isFalse();
        assertThat(jobService.fail(second.getId(), 2, second.getRequestId(), "provider_error")).isTrue();
        jobService.retry(userId, first.getId());
        var third = jobService.claimNext();
        assertThat(third.getAttemptCount()).isEqualTo(3);
        jobService.fail(third.getId(), 3, third.getRequestId(), "provider_error");
        assertThat(jobService.getOwned(userId, first.getId()).retryable()).isFalse();
        assertThatThrownBy(() -> jobService.retry(userId, first.getId())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE user_id=?", Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void interruptedRecoveryOnlyTouchesRunningAndWrongCorrelationFailsSafely() {
        var a = service.add(input());
        var b = service.add(input());
        var job = jobService.claimNext();
        assertThat(jobService.recoverInterrupted()).isEqualTo(1);
        assertThat(jobs.selectById(a.getJobId()).getLastErrorCode()).isEqualTo("execution_interrupted");
        assertThat(jobs.selectById(b.getJobId()).getStatus()).isEqualTo("queued");
        var next = jobService.claimNext();
        MoodAiResponse wrong = new MoodAiResponse(next.getRequestId(), next.getId().toString(), 1, "999", "错误关联", "MODEL", true, "mood-reply-v1", "m");
        assertThat(jobService.complete(next.getId(), 1, next.getRequestId(), wrong)).isFalse();
        assertThat(jobs.selectById(next.getId()).getLastErrorCode()).isEqualTo("invalid_model_response");
        assertThat(logs()).isZero();
    }

    @Test
    void concurrentClaimsOnlyOneAttemptWins() throws Exception {
        var saved = service.add(input());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> { start.await(); return jobService.claimNext(); });
            var b = executor.submit(() -> { start.await(); return jobService.claimNext(); });
            start.countDown();
            var results = java.util.Arrays.asList(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
            assertThat(results.stream().filter(java.util.Objects::nonNull).count()).isEqualTo(1);
            assertThat(jobs.selectById(saved.getJobId()).getAttemptCount()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    /** 以数据库实际锁等待作为屏障，不能用 sleep 猜测第二个事务被阻塞。 */
    void assertDatabaseWait(Future<?> blocked) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        boolean waited = false;
        while (System.nanoTime() < deadline && !blocked.isDone()) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class) > 0) {
                waited = true; break;
            }
            Thread.yield();
        }
        assertThat(waited).as("第二连接确实在数据库等待锁，而非已提前提交").isTrue();
        assertThat(blocked.isDone()).isFalse();
    }

    void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    @Test
    void rowLockSerializesDeleteAndCompleteInBothOrders() throws Exception {
        for (boolean deleteFirst : new boolean[]{true, false}) {
            var saved = service.add(input());
            var job = jobService.claimNext();
            CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> first = executor.submit(() -> tx.execute(status -> {
                    boolean result = deleteFirst ? service.delete(saved.getId(), userId)
                            : jobService.complete(job.getId(), 1, job.getRequestId(), response(job));
                    locked.countDown(); await(release); return result;
                }));
                await(locked);
                Future<Boolean> second = executor.submit(() -> deleteFirst
                        ? jobService.complete(job.getId(), 1, job.getRequestId(), response(job))
                        : service.delete(saved.getId(), userId));
                assertDatabaseWait(second);
                release.countDown();
                assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
                assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(!deleteFirst);
                MoodRecord source = moods.selectById(saved.getId());
                assertThat(source.getStatus()).isZero();
                assertThat(source.getContentVersion()).isEqualTo(2);
                assertThat(source.getDeletedAt()).isNotNull();
                assertThat(source.getAiReply()).isNull();
                assertThat(jobs.selectById(job.getId()).getStatus()).isEqualTo(deleteFirst ? "superseded" : "succeeded");
                assertThat(logs()).isEqualTo(deleteFirst ? 0 : 1);
                assertThat(service.getDetailView(saved.getId(), userId)).isNull();
            } finally { release.countDown(); executor.shutdownNow(); }
        }
    }

    @Test
    void completionIndependentlyChecksStatusEnabledAndVersion() {
        for (String mutation : new String[]{"status=0", "ai_reply_enabled=0", "content_version=2"}) {
            var saved = service.add(input());
            var job = jobService.claimNext();
            jdbc.update("UPDATE mood_record SET " + mutation + " WHERE id=?", saved.getId());
            assertThat(jobService.complete(job.getId(), 1, job.getRequestId(), response(job))).isFalse();
            assertThat(moods.selectById(saved.getId()).getAiReply()).isNull();
            assertThat(jobs.selectById(job.getId()).getStatus()).isEqualTo("superseded");
            assertThat(moods.writeReplyAtVersion(saved.getId(), userId, 1L, "不能写入")).isZero();
            assertThat(logs()).isZero();
        }
    }

    @Test
    void retrySnapshotBeforeDeleteCannotRequeueAfterDeletion() throws Exception {
        var saved = service.add(input());
        var job = jobService.claimNext();
        jobService.fail(job.getId(), 1, job.getRequestId(), "provider_error");
        CountDownLatch checked = new CountDownLatch(1), release = new CountDownLatch(1);
        // 只在真实 Mapper 的来源快照读取之后插入屏障，SQL 与事务仍使用实际连接。
        MoodRecordMapper paused = org.mockito.Mockito.mock(MoodRecordMapper.class, org.mockito.AdditionalAnswers.delegatesTo(moods));
        org.mockito.Mockito.doAnswer(call -> {
            MoodRecord snapshot = moods.selectById(saved.getId());
            checked.countDown(); await(release); return snapshot;
        }).when(paused).selectById(saved.getId());
        AiJobService retrying = new AiJobService(jobs, paused, replies, admission, true);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> retry = executor.submit(() -> retrying.retry(userId, job.getId()));
            await(checked);
            assertThat(service.delete(saved.getId(), userId)).isTrue();
            release.countDown();
            assertThatThrownBy(() -> retry.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThat(jobs.selectById(job.getId()).getStatus()).isEqualTo("superseded");
            assertThat(moods.selectById(saved.getId()).getStatus()).isZero();
            assertThat(jobService.sourceForAttempt(job.getId(), 1, job.getRequestId())).isNull();
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void retryClaimedBeforeDeleteCommitsWithoutReverseSourceLock() throws Exception {
        var saved = service.add(input());
        var job = jobService.claimNext();
        jobService.fail(job.getId(), 1, job.getRequestId(), "provider_error");
        CountDownLatch requeued = new CountDownLatch(1), release = new CountDownLatch(1);
        AiJobMapper paused = org.mockito.Mockito.mock(AiJobMapper.class, org.mockito.AdditionalAnswers.delegatesTo(jobs));
        org.mockito.Mockito.doAnswer(call -> {
            int rows = jobs.requeueAfterCooldown(call.getArgument(0), call.getArgument(1));
            requeued.countDown(); await(release); return rows;
        }).when(paused).requeueAfterCooldown(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        AiJobService retrying = new AiJobService(paused, moods, replies, admission, true);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> retry = executor.submit(() -> retrying.retry(userId, job.getId()));
            await(requeued);
            Future<Boolean> delete = executor.submit(() -> service.delete(saved.getId(), userId));
            assertDatabaseWait(delete);
            release.countDown(); retry.get(5, TimeUnit.SECONDS);
            assertThat(delete.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(jobs.selectById(job.getId()).getStatus()).isEqualTo("superseded");
            assertThat(jobs.selectById(job.getId()).getAttemptCount()).isEqualTo(1);
            assertThat(moods.selectById(saved.getId()).getAiReply()).isNull();
            assertThat(logs()).isZero();
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void retryCasCannotTouchAnotherFailedAttemptAndConcurrentRetriesReuseOneJob() throws Exception {
        var saved = service.add(input());
        var job = jobService.claimNext();
        jobService.fail(job.getId(), 1, job.getRequestId(), "provider_error");
        var oldFailed = jobs.selectById(job.getId());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> { start.await(); return jobService.retry(userId, job.getId()); });
            var b = executor.submit(() -> { start.await(); return jobService.retry(userId, job.getId()); });
            start.countDown();
            assertThat(a.get(5, TimeUnit.SECONDS).id()).isEqualTo(saved.getJobId());
            assertThat(b.get(5, TimeUnit.SECONDS).id()).isEqualTo(saved.getJobId());
        } finally { executor.shutdownNow(); }
        var second = jobService.claimNext();
        jobService.fail(second.getId(), 2, second.getRequestId(), "provider_error");
        assertThat(jobs.requeueAfterCooldown(oldFailed, 0)).isZero();
        assertThat(jobs.selectById(job.getId()).getAttemptCount()).isEqualTo(2);
        assertThat(jobs.selectById(job.getId()).getStatus()).isEqualTo("failed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE user_id=?", Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void failedRetryRechecksUserCapacityWithoutLosingOriginalTask() {
        var saved = service.add(input());
        var job = jobService.claimNext();
        jobService.fail(job.getId(), 1, job.getRequestId(), "provider_timeout");
        service.add(input()); service.add(input()); service.add(input());
        assertThatThrownBy(() -> jobService.retry(userId, job.getId())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .extracting(e -> ((org.springframework.web.server.ResponseStatusException)e).getStatusCode().value()).isEqualTo(503);
        assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("failed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE user_id=?", Integer.class, userId)).isEqualTo(4);
    }

    @Test
    void currentViewsPreserveHistoryAndDoNotGenerateOnReads() {
        assertThat(service.getDetailView(101L, 1L).aiReplyState()).isEqualTo("legacy");
        assertThat(service.getDetailView(102L, 1L).aiReplyState()).isEqualTo("disabled");
        var saved = service.add(input());
        assertThat(service.getDetailView(saved.getId(), userId).aiReplyState()).isEqualTo("queued");
        var job = jobService.claimNext();
        assertThat(service.getDetailView(saved.getId(), userId).aiReplyState()).isEqualTo("running");
        jobService.complete(job.getId(), 1, job.getRequestId(), response(job));
        var view = service.getDetailView(saved.getId(), userId);
        assertThat(view.aiReplyState()).isEqualTo("succeeded");
        assertThat(view.aiReply()).isEqualTo("合成回复。");
        assertThat(view.resultKind()).isEqualTo("MODEL");
        assertThat(logs()).isEqualTo(1);
        assertThatThrownBy(() -> jobService.getOwned(userId+1, job.getId())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        MoodRecord edit = input();edit.setNeedAiReply(false);
        service.update(saved.getId(), userId, edit);
        assertThat(service.getDetailView(saved.getId(), userId).aiReplyState()).isEqualTo("disabled");
        assertThat(service.getDetailView(saved.getId(), userId).aiReply()).isNull();
        assertThatThrownBy(() -> jobService.getOwned(userId, job.getId())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(logs()).isEqualTo(1);
    }

    @Test
    void globalCapacityFailureStillSavesAndCanRetryWhenQueueFrees() {
        try {
            for (long id=9000000; id<9000100; id++) {
                jdbc.update("INSERT INTO ai_job(user_id,target_id,task_type,target_version,status,attempt_count) VALUES (1,?,'MOOD_REPLY',1,'queued',0)", id);
            }
            var saved = service.add(input());
            assertThat(saved.getAiReplyState()).isEqualTo("failed");
            assertThat(moods.selectById(saved.getId()).getContent()).isEqualTo("原始内容");
            var failed = jobService.getOwned(userId, saved.getJobId());
            assertThat(failed.errorCode()).isEqualTo("capacity_exceeded");
            assertThat(failed.attemptCount()).isZero();
            assertThat(failed.retryable()).isTrue();
            assertThatThrownBy(() -> jobService.retry(userId, saved.getJobId())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            jdbc.update("DELETE FROM ai_job WHERE user_id=1 AND target_id>=9000000 AND target_id<9000100");
            assertThat(jobService.retry(userId, saved.getJobId()).status()).isEqualTo("queued");
            assertThat(jobService.getOwned(userId, saved.getJobId()).attemptCount()).isZero();
        } finally { jdbc.update("DELETE FROM ai_job WHERE user_id=1 AND target_id>=9000000 AND target_id<9000100"); }
    }

    @Test
    void listViewsKeepPaginationAndExcludeDeletedOrForeignSources() {
        var first = service.add(input());
        service.add(input()); service.add(input());
        service.delete(first.getId(), userId);
        var page = service.listViews(userId, null, null, null, 1L, 1L);
        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).userId()).isEqualTo(userId);
        assertThat(page.getRecords().get(0).aiReplyState()).isEqualTo("queued");
        assertThat(page.getRecords().get(0).aiReply()).isNull();
    }

    @Test
    void unchangedUpdatePreservesEveryCurrentJobStateAndReply() {
        for (String state : new String[]{"queued", "running", "failed", "succeeded"}) {
            var saved = service.add(input());
            if (!"queued".equals(state)) {
                var job = jobService.claimNext();
                if ("failed".equals(state)) jobService.fail(job.getId(), 1, job.getRequestId(), "provider_error");
                if ("succeeded".equals(state)) jobService.complete(job.getId(), 1, job.getRequestId(), response(job));
            }
            jdbc.update("UPDATE mood_record SET update_time='2000-01-02 00:00:00' WHERE id=?", saved.getId());
            var before = moods.selectById(saved.getId());
            var result = service.update(saved.getId(), userId, input());
            assertThat(result.getContentVersion()).isEqualTo(1);
            assertThat(result.getJobId()).isEqualTo(saved.getJobId());
            assertThat(result.getAiReplyState()).isEqualTo(state);
            assertThat(moods.selectById(saved.getId()).getAiReply()).isEqualTo(before.getAiReply());
            assertThat(moods.selectById(saved.getId()).getUpdateTime()).isEqualTo(before.getUpdateTime());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE target_id=?", Integer.class, saved.getId())).isEqualTo(1);
            assertThat(service.delete(saved.getId(), userId)).isTrue();
        }
    }

    @Test
    void unchangedUpdateKeepsDisabledLegacyAndUnavailableWithoutCreatingTasks() {
        for (String state : new String[]{"disabled", "legacy", "unavailable"}) {
            MoodRecord request = input(); request.setNeedAiReply(false);
            var saved = service.add(request);
            if (!"disabled".equals(state)) {
                jdbc.update("UPDATE mood_record SET ai_reply_enabled=1,ai_reply=? WHERE id=?",
                        "legacy".equals(state) ? "保留旧回复" : null, saved.getId());
                request.setNeedAiReply(true);
            }
            var result = service.update(saved.getId(), userId, request);
            assertThat(result.getContentVersion()).isEqualTo(1);
            assertThat(result.getAiReplyState()).isEqualTo(state);
            assertThat(result.getJobId()).isNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE target_id=?", Integer.class, saved.getId())).isZero();
            assertThat(service.getDetailView(saved.getId(), userId).aiReply()).isEqualTo("legacy".equals(state) ? "保留旧回复" : null);
        }
    }

    @Test
    void completeFailureIsMarkedFailedInNewTransactionAndCanRetry() throws Exception {
        var saved = service.add(input());
        jdbc.execute("CREATE TRIGGER xinyu_test_fail_log_insert BEFORE INSERT ON ai_reply "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic log failure'");

        try (WorkerExecution execution = new WorkerExecution()) {
            var first = execution.worker.runOne();
            ModelCall firstModel = execution.nextModelCall();
            firstModel.succeed();

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.FAILED);
            var failed = jobs.selectById(saved.getJobId());
            assertThat(failed.getStatus()).isEqualTo("failed");
            assertThat(failed.getLastErrorCode()).isEqualTo("internal_error");
            assertThat(failed.getAttemptCount()).isEqualTo(1);
            assertThat(failed.getModelCalled()).isNull();
            assertThat(moods.selectById(saved.getId()).getAiReply()).isNull();
            assertThat(logs()).isZero();
            assertThat(jobService.getOwned(userId, saved.getJobId()).retryable()).isTrue();

            jdbc.execute("DROP TRIGGER xinyu_test_fail_log_insert");
            assertThat(jobService.retry(userId, saved.getJobId()).id()).isEqualTo(saved.getJobId());
            var second = execution.worker.runOne();
            ModelCall secondModel = execution.nextModelCall();
            assertThat(secondModel.request().attempt()).isEqualTo(2);
            assertThat(secondModel.request().requestId()).isNotEqualTo(firstModel.request().requestId());
            secondModel.succeed();

            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.SUCCEEDED);
            assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("succeeded");
            assertThat(moods.selectById(saved.getId()).getAiReply()).isEqualTo("合成worker回复。");
            assertThat(logs()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT prompt FROM ai_reply WHERE user_id=?", String.class, userId)).isNull();
            verify(execution.client, times(2)).startGeneration(any(), any());
        }
    }

    @Test
    void workerLateFailureCannotOverwriteSupersededSource() throws Exception {
        var saved = service.add(input());
        try (WorkerExecution execution = new WorkerExecution()) {
            var result = execution.worker.runOne();
            ModelCall oldModel = execution.nextModelCall();
            MoodRecord edit = input();
            edit.setContent("新版本合成内容");
            var updated = service.update(saved.getId(), userId, edit);
            oldModel.reply().completeExceptionally(new AiServiceException("provider_error"));

            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.SKIPPED);
            assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("superseded");
            assertThat(jobs.selectById(updated.getJobId()).getStatus()).isEqualTo("queued");
            assertThat(moods.selectById(saved.getId()).getContentVersion()).isEqualTo(2);
            assertThat(moods.selectById(saved.getId()).getAiReply()).isNull();
            assertThat(logs()).isZero();
        }
    }

    @Test
    void workerLateFailureCannotOverwriteNewAttempt() throws Exception {
        var saved = service.add(input());
        try (WorkerExecution execution = new WorkerExecution()) {
            var oldResult = execution.worker.runOne();
            ModelCall oldModel = execution.nextModelCall();
            assertThat(jobService.fail(saved.getJobId(), 1, oldModel.request().requestId(), "provider_timeout")).isTrue();
            jobService.retry(userId, saved.getJobId());
            var current = jobService.claimNext();
            assertThat(current.getAttemptCount()).isEqualTo(2);
            oldModel.reply().completeExceptionally(new AiServiceException("provider_error"));

            assertThat(oldResult.get(5, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.SKIPPED);
            var after = jobs.selectById(saved.getJobId());
            assertThat(after.getStatus()).isEqualTo("running");
            assertThat(after.getAttemptCount()).isEqualTo(2);
            assertThat(after.getRequestId()).isEqualTo(current.getRequestId());
            assertThat(after.getLastErrorCode()).isNull();
            assertThat(logs()).isZero();
        }
    }

    @Test
    void workerLateSuccessDoesNotRestoreReplyAfterDeletion() throws Exception {
        var saved = service.add(input());
        try (WorkerExecution execution = new WorkerExecution()) {
            var result = execution.worker.runOne();
            ModelCall model = execution.nextModelCall();
            assertThat(service.delete(saved.getId(), userId)).isTrue();
            model.succeed();

            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.SKIPPED);
            assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("superseded");
            assertThat(moods.selectById(saved.getId()).getStatus()).isZero();
            assertThat(moods.selectById(saved.getId()).getAiReply()).isNull();
            assertThat(logs()).isZero();
        }
    }

    @Test
    void completionRowLockTimeoutRollsBackAndFailureMarkCanCommit() throws Exception {
        var saved = service.add(input());
        ExecutorService locker = Executors.newSingleThreadExecutor();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (WorkerExecution execution = new WorkerExecution()) {
            var result = execution.worker.runOne();
            ModelCall model = execution.nextModelCall();
            Future<?> holding = locker.submit(() -> tx.execute(status -> {
                moods.selectOwnedForUpdate(saved.getId(), userId);
                locked.countDown();
                awaitProbeRelease(release);
                return null;
            }));
            assertThat(locked.await(3, TimeUnit.SECONDS)).isTrue();
            // 服务器行锁上限须大于5秒，避免把服务器锁超时误认为Spring语句超时。
            assertThat(jdbc.queryForObject("SELECT @@innodb_lock_wait_timeout", Integer.class)).isGreaterThan(5);
            long started = System.nanoTime();
            model.succeed();

            assertThat(result.get(9, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.FAILED);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            assertThat(elapsed).isBetween(Duration.ofSeconds(3), Duration.ofSeconds(9));
            assertThat(holding).isNotDone();
            assertThat(release.getCount()).isEqualTo(1);
            assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("failed");
            assertThat(jobs.selectById(saved.getJobId()).getLastErrorCode()).isEqualTo("internal_error");
            assertThat(moods.selectById(saved.getId()).getAiReply()).isNull();
            assertThat(logs()).isZero();
            System.out.println("3C_REAL_DB completion_lock_timeout_ms=" + elapsed.toMillis());
            release.countDown();
            holding.get(3, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            locker.shutdownNow();
            assertThat(locker.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void cleanupRowLockTimeoutReturnsUnresolvedAndLeavesRunning() throws Exception {
        var saved = service.add(input());
        ExecutorService locker = Executors.newSingleThreadExecutor();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (WorkerExecution execution = new WorkerExecution()) {
            var result = execution.worker.runOne();
            ModelCall model = execution.nextModelCall();
            Future<?> holding = locker.submit(() -> tx.execute(status -> {
                jobs.selectForUpdate(saved.getJobId());
                locked.countDown();
                awaitProbeRelease(release);
                return null;
            }));
            assertThat(locked.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT @@innodb_lock_wait_timeout", Integer.class)).isGreaterThan(5);
            long started = System.nanoTime();
            model.reply().completeExceptionally(new AiServiceException("provider_error"));

            assertThat(result.get(9, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.UNRESOLVED);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            assertThat(elapsed).isBetween(Duration.ofSeconds(3), Duration.ofSeconds(9));
            assertThat(holding).isNotDone();
            assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("running");
            assertThat(jobs.selectById(saved.getJobId()).getLastErrorCode()).isNull();
            assertThat(moods.selectById(saved.getId()).getAiReply()).isNull();
            assertThat(logs()).isZero();
            System.out.println("3C_REAL_DB cleanup_lock_timeout_ms=" + elapsed.toMillis());
            release.countDown();
            holding.get(3, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            locker.shutdownNow();
            assertThat(locker.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void claimRowLockTimeoutLeavesJobQueuedAndDoesNotCallModel() throws Exception {
        var saved = service.add(input());
        ExecutorService locker = Executors.newSingleThreadExecutor();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (WorkerExecution execution = new WorkerExecution()) {
            Future<?> holding = locker.submit(() -> tx.execute(status -> {
                jobs.selectForUpdate(saved.getJobId());
                locked.countDown();
                awaitProbeRelease(release);
                return null;
            }));
            try {
                assertThat(locked.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(jdbc.queryForObject("SELECT @@innodb_lock_wait_timeout", Integer.class)).isGreaterThan(5);
                long started = System.nanoTime();
                var result = execution.worker.runOne();

                assertThatThrownBy(() -> result.get(9, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasRootCauseInstanceOf(java.sql.SQLTimeoutException.class);
                Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
                assertThat(elapsed).isBetween(Duration.ofSeconds(3), Duration.ofSeconds(9));
                assertThat(holding).isNotDone();
                assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("queued");
                assertThat(jobs.selectById(saved.getJobId()).getAttemptCount()).isZero();
                verifyNoInteractions(execution.client);
                System.out.println("3C_REAL_DB claim_lock_timeout_ms=" + elapsed.toMillis());
            } finally {
                release.countDown();
                holding.get(3, TimeUnit.SECONDS);
            }
        } finally {
            release.countDown();
            locker.shutdownNow();
            assertThat(locker.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void invalidSourcePrecheckRowLockTimeoutDoesNotKeepDatabaseThreadWaiting() throws Exception {
        var saved = service.add(input());
        // 暂存交接步骤，便于在真实领取提交后、来源预检前构造失效来源和行锁。
        BlockingQueue<Runnable> steps = new ArrayBlockingQueue<>(3);
        ExecutorService locker = Executors.newSingleThreadExecutor();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (WorkerExecution execution = new WorkerExecution(steps::add)) {
            var result = execution.worker.runOne();
            execution.database.submit(steps.poll(3, TimeUnit.SECONDS)).get(3, TimeUnit.SECONDS);
            assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("running");
            jdbc.update("UPDATE mood_record SET status=0 WHERE id=?", saved.getId());

            Future<?> holding = locker.submit(() -> tx.execute(status -> {
                jobs.selectForUpdate(saved.getJobId());
                locked.countDown();
                awaitProbeRelease(release);
                return null;
            }));
            try {
                assertThat(locked.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(jdbc.queryForObject("SELECT @@innodb_lock_wait_timeout", Integer.class)).isGreaterThan(5);
                long started = System.nanoTime();
                Future<?> precheck = execution.database.submit(steps.poll(3, TimeUnit.SECONDS));

                // 预检更新和后续fail各等最多5秒；锁不释放，两者都应退出，不能假称已落库。
                assertThat(result.get(13, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.UNRESOLVED);
                precheck.get(1, TimeUnit.SECONDS);
                Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
                assertThat(elapsed).isBetween(Duration.ofSeconds(8), Duration.ofSeconds(13));
                assertThat(holding).isNotDone();
                assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("running");
                verifyNoInteractions(execution.client);
                System.out.println("3C_REAL_DB precheck_and_cleanup_lock_timeout_ms=" + elapsed.toMillis());
            } finally {
                release.countDown();
                holding.get(3, TimeUnit.SECONDS);
            }
        } finally {
            release.countDown();
            locker.shutdownNow();
            assertThat(locker.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void runtimeRunsThreeAcrossUsersAndLeavesFourthInDatabaseUntilSlotReturns() throws Exception {
        long secondUser = userSequence.incrementAndGet();
        jdbc.update("INSERT INTO user(id) VALUES (?)", secondUser);
        try (WorkerExecution execution = new WorkerExecution()) {
            service.add(input());
            service.add(input());
            MoodRecord other = input();
            other.setUserId(secondUser);
            service.add(other);
            var fourth = service.add(other);
            doAnswer(invocation -> {
                for (ModelCall call : execution.inflight) {
                    call.reply().completeExceptionally(new AiServiceException("execution_interrupted"));
                }
                return null;
            }).when(execution.client).close();
            when(execution.client.awaitTermination(any())).thenReturn(true);

            try (AiJobRuntime runtime = new AiJobRuntime(jobService, execution.client, manager,
                    3, Duration.ofMillis(20), Duration.ofSeconds(10))) {
                runtime.start();
                ModelCall first = execution.nextModelCall();
                ModelCall second = execution.nextModelCall();
                ModelCall third = execution.nextModelCall();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE status='running'", Integer.class))
                        .isEqualTo(3);
                assertThat(jobs.selectById(fourth.getJobId()).getStatus()).isEqualTo("queued");
                assertThat(execution.calls.poll(100, TimeUnit.MILLISECONDS)).isNull();

                first.succeed();
                ModelCall replacement = execution.nextModelCall();
                assertThat(replacement.request().jobId()).isEqualTo(fourth.getJobId().toString());
                assertThat(second.reply()).isNotDone();
                assertThat(third.reply()).isNotDone();
                second.succeed();
                third.succeed();
                replacement.succeed();
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE status='succeeded'", Integer.class))
                    .isEqualTo(4);
        } finally {
            jdbc.update("DELETE FROM ai_reply WHERE user_id=?", secondUser);
            jdbc.update("DELETE FROM ai_job WHERE user_id=?", secondUser);
            jdbc.update("DELETE FROM mood_record WHERE user_id=?", secondUser);
            jdbc.update("DELETE FROM user WHERE id=?", secondUser);
        }
    }

    @Test
    void subsecondCooldownProbeRoundsUpUntilExactDeadline() throws Exception {
        // 复用实际Mapper的SELECT，仅将来源和当前时刻换为固定探针输入；不改DATETIME表结构。
        var method = AiJobMapper.class.getMethod("selectRetryAfterSeconds", com.xinyu.entity.AiJob.class, int.class);
        String query = String.join(" ", method.getAnnotation(org.apache.ibatis.annotations.Select.class).value())
                .replace("#{cooldownSeconds}", "180")
                .replace("#{job.id}", "123")
                .replace("#{job.attemptCount}", "3")
                .replace("CURRENT_TIMESTAMP", "CAST(? AS DATETIME(6))");
        String probe = "WITH ai_job AS (SELECT 123 AS id, 3 AS attempt_count, 'failed' AS status, "
                + "CAST(? AS DATETIME(6)) AS finished_at) " + query;

        assertThat(jdbc.queryForObject(probe, Integer.class,
                "2026-01-01 12:00:00.500000", "2026-01-01 12:03:00.499999")).isEqualTo(1);
        assertThat(jdbc.queryForObject(probe, Integer.class,
                "2026-01-01 12:00:00.500000", "2026-01-01 12:03:00.500000")).isZero();
        assertThat(jdbc.queryForObject(probe, Integer.class,
                "2026-01-01 12:00:00.500000", "2026-01-01 12:03:00.500001")).isZero();
    }

    @Test
    void cooldownStateComesFromDatabaseAndExpiryOnlyEnablesManualRetry() {
        var saved = failedJobAtAttempt(3);
        var cooling = jobService.getOwned(userId, saved.getJobId());
        assertThat(cooling.retryAfterSeconds()).isBetween(175, 180);
        assertThat(cooling.retryable()).isFalse();
        assertThatThrownBy(() -> jobService.retry(userId, saved.getJobId()))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(429);
                    assertThat(Integer.parseInt(failure.getHeaders().getFirst("Retry-After"))).isBetween(175, 180);
                });

        // 服务对象重建不改变资格，冷却依据保存在数据库中。
        AiJobService restarted = new AiJobService(jobs, moods, replies, admission, true);
        assertThat(restarted.getOwned(userId, saved.getJobId()).retryable()).isFalse();
        jdbc.update("UPDATE ai_job SET finished_at=TIMESTAMPADD(SECOND,-181,CURRENT_TIMESTAMP) WHERE id=?",
                saved.getJobId());
        assertThat(restarted.getOwned(userId, saved.getJobId()).retryAfterSeconds()).isZero();
        assertThat(restarted.getOwned(userId, saved.getJobId()).retryable()).isTrue();
        assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("failed");
        assertThat(jobService.retry(userId, saved.getJobId()).status()).isEqualTo("queued");
        assertThat(jobs.selectById(saved.getJobId()).getAttemptCount()).isEqualTo(3);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {3, 6})
    void expiredFailureRoundCanGenerateAndCommitNextAttempt(int previousAttempts) throws Exception {
        var saved = failedJobAtAttempt(previousAttempts);
        jdbc.update("UPDATE ai_job SET finished_at=TIMESTAMPADD(SECOND,-3601,CURRENT_TIMESTAMP) WHERE id=?",
                saved.getJobId());
        assertThat(jobService.retry(userId, saved.getJobId()).status()).isEqualTo("queued");

        try (WorkerExecution execution = new WorkerExecution()) {
            var result = execution.worker.runOne();
            ModelCall model = execution.nextModelCall();
            assertThat(model.request().attempt()).isEqualTo(previousAttempts + 1);
            model.succeed();
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(AiJobWorker.Outcome.SUCCEEDED);
            assertThat(jobs.selectById(saved.getJobId()).getAttemptCount()).isEqualTo(previousAttempts + 1);
            assertThat(moods.selectById(saved.getId()).getAiReply()).isEqualTo("合成worker回复。");
            assertThat(logs()).isEqualTo(1);
        }
    }

    @Test
    void nullFinishTimeAllowsRetryAndNonFailedTaskHasNoCooldown() {
        var saved = failedJobAtAttempt(3);
        jdbc.update("UPDATE ai_job SET finished_at=NULL WHERE id=?", saved.getJobId());
        assertThat(jobService.getOwned(userId, saved.getJobId()).retryAfterSeconds()).isZero();
        assertThat(jobService.getOwned(userId, saved.getJobId()).retryable()).isTrue();
        assertThat(jobService.retry(userId, saved.getJobId()).status()).isEqualTo("queued");

        var running = jobService.claimNext();
        assertThat(running.getAttemptCount()).isEqualTo(4);
        assertThat(jobService.getOwned(userId, saved.getJobId()).retryAfterSeconds()).isZero();
        assertThat(jobService.getOwned(userId, saved.getJobId()).retryable()).isFalse();
    }

    @Test
    void sourceDeletionIsCheckedBeforeCooldownAndReturns404() {
        var saved = failedJobAtAttempt(3);
        assertThat(service.delete(saved.getId(), userId)).isTrue();
        assertThatThrownBy(() -> jobService.retry(userId, saved.getJobId()))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void simultaneousRetriesAfterCooldownRequeueOnlyOnePersistentJob() throws Exception {
        var saved = failedJobAtAttempt(3);
        jdbc.update("UPDATE ai_job SET finished_at=TIMESTAMPADD(SECOND,-181,CURRENT_TIMESTAMP) WHERE id=?",
                saved.getJobId());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> {
                await(start);
                assertThat(jobService.retry(userId, saved.getJobId()).status()).isEqualTo("queued");
            });
            Future<?> second = executor.submit(() -> {
                await(start);
                assertThat(jobService.retry(userId, saved.getJobId()).status()).isEqualTo("queued");
            });
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE user_id=?", Integer.class, userId))
                    .isEqualTo(1);
            assertThat(jobs.selectById(saved.getJobId()).getAttemptCount()).isEqualTo(3);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    private com.xinyu.vo.MoodSaveVO failedJobAtAttempt(int attemptCount) {
        var saved = service.add(input());
        jdbc.update("UPDATE ai_job SET status='failed', attempt_count=?, finished_at=CURRENT_TIMESTAMP, "
                + "last_error_code='provider_error' WHERE id=?", attemptCount, saved.getJobId());
        return saved;
    }

    @Test
    void cooldownRequeueRejectsBeforeDeadlineAndAllowsAtDeadline() {
        var saved = service.add(input());
        jdbc.update("UPDATE ai_job SET status='failed', attempt_count=3, "
                + "finished_at=CURRENT_TIMESTAMP WHERE id=?", saved.getJobId());
        var failed = jobs.selectById(saved.getJobId());

        assertThat(jobs.selectRetryAfterSeconds(failed, 180)).isBetween(175, 180);
        assertThat(jobs.requeueAfterCooldown(failed, 180)).isZero();
        assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("failed");

        // 调整结束时间模拟到期，无需真实等待180秒。
        jdbc.update("UPDATE ai_job SET finished_at=TIMESTAMPADD(SECOND,-180,CURRENT_TIMESTAMP) WHERE id=?",
                saved.getJobId());
        assertThat(jobs.selectRetryAfterSeconds(failed, 180)).isZero();
        assertThat(jobs.requeueAfterCooldown(failed, 180)).isEqualTo(1);
        assertThat(jobs.selectById(saved.getJobId()).getStatus()).isEqualTo("queued");
        assertThat(jobs.selectById(saved.getJobId()).getAttemptCount()).isEqualTo(3);
    }

    private static void awaitProbeRelease(CountDownLatch release) {
        try {
            assertThat(release.await(15, TimeUnit.SECONDS)).as("锁探针必须在15秒内显式释放").isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("隔离锁探针被中断");
        }
    }

    /** 真实数据库执行器及worker，只替换模型返回；不使用直接执行器隐藏线程交接。 */
    private final class WorkerExecution implements AutoCloseable {
        private final ExecutorService database = Executors.newSingleThreadExecutor();
        private final BlockingQueue<ModelCall> calls = new LinkedBlockingQueue<>(3);
        private final ConcurrentLinkedQueue<ModelCall> inflight = new ConcurrentLinkedQueue<>();
        private final AiServiceClient client = mock(AiServiceClient.class);
        private final AiJobWorker worker;

        WorkerExecution() {
            this(null);
        }

        WorkerExecution(Executor stagedDatabase) {
            worker = new AiJobWorker(jobService, client, manager,
                    stagedDatabase == null ? database : stagedDatabase, System::nanoTime);
            when(client.startGeneration(any(), any())).thenAnswer(invocation -> {
                ModelCall call = new ModelCall(invocation.getArgument(0), new CompletableFuture<>());
                inflight.add(call);
                if (!calls.offer(call)) {
                    throw new IllegalStateException("测试模型请求队列已满");
                }
                return new AiServiceClient.Generation(call.reply(), CompletableFuture.completedFuture(null));
            });
        }

        ModelCall nextModelCall() throws InterruptedException {
            ModelCall call = calls.poll(3, TimeUnit.SECONDS);
            assertThat(call).as("worker应在预检后发出一次合成模型请求").isNotNull();
            return call;
        }

        @Override
        public void close() throws InterruptedException {
            for (ModelCall call : inflight) {
                call.reply().completeExceptionally(new AiServiceException("execution_interrupted"));
            }
            database.shutdown();
            if (!database.awaitTermination(10, TimeUnit.SECONDS)) {
                database.shutdownNow();
                assertThat(database.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private record ModelCall(MoodAiRequest request, CompletableFuture<MoodAiResponse> reply) {
        void succeed() {
            reply.complete(new MoodAiResponse(request.requestId(), request.jobId(), request.attempt(),
                    request.sourceVersion(), "合成worker回复。", "MODEL", true, "mood-reply-v1", "synthetic-model"));
        }
    }

}
