package com.xinyu.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.xinyu.entity.MoodRecord;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.mapper.MoodReviewMapper;
import com.xinyu.service.AiJobAdmission;
import com.xinyu.service.MoodReviewService;
import com.xinyu.service.MoodReviewStatsService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段 2 B 段：在显式 xinyu_test_ 隔离库构造 60 天 2000 条混合长度与 AI 开关记录的合成用户，
 * 实测 10 次耗时与 EXPLAIN ANALYZE，评估共享数据源 socketTimeout=6000ms 预算是否安全。
 * 本测试只评估，不修改任何数据源配置、也不触发模型调用。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoodReviewTimeoutBenchmarkIT {

    private static final long BENCH_USER_ID = 990001L;
    private static final long BG_USER_START = 990002L;
    private static final int BG_USERS = 10;
    private static final int TOTAL_RECORDS = 2000;
    private static final int DAYS_SPAN = 60;
    private static final int ITERATIONS = 10;
    private static final long SOCKET_TIMEOUT_MS = 6000L;
    private static final List<String> MOODS = List.of("开心", "平静", "焦虑", "疲惫", "期待", "难过");

    private JdbcTemplate jdbc;
    private SqlSessionTemplate sessions;
    private MoodReviewStatsService statsService;
    private MoodReviewService reviewService;
    private MoodRecordMapper recordMapper;

    @BeforeAll
    void setupIsolatedBenchmarkDatabase() throws Exception {
        String url = System.getenv("AI_TEST_JDBC_URL");
        Assumptions.assumeTrue(url != null, "需要显式 xinyu_test_ 隔离库才能运行超时评估");

        URI uri = URI.create(url.substring(5));
        if (!url.startsWith("jdbc:mysql://") || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || !uri.getPath().matches("/xinyu_test_[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("只接受 xinyu_test_ 隔离库");
        }

        var source = new DriverManagerDataSource(url, "root", System.getenv("AI_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class);
        if (tables != null && tables == 0) {
            String baseline = new ClassPathResource("ai/schema.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .split("-- STAGE2_REVIEW_TABLES")[0];
            new ResourceDatabasePopulator(new ByteArrayResource(baseline.getBytes(StandardCharsets.UTF_8))).execute(source);
            for (String migration : new String[]{
                    "20261107_02_ai_reply_model_name.sql",
                    "20261107_01_ai_reply_job.sql",
                    "20261113_01_mood_review.sql"}) {
                new ResourceDatabasePopulator(new FileSystemResource(Path.of("../sql/migrations", migration))).execute(source);
            }
        }

        var manager = new DataSourceTransactionManager(source);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(MoodRecordMapper.class);
        configuration.addMapper(AiJobMapper.class);
        factory.setConfiguration(configuration);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        factory.setPlugins(interceptor);

        sessions = new SqlSessionTemplate(factory.getObject());
        recordMapper = sessions.getMapper(MoodRecordMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-13T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        statsService = new MoodReviewStatsService(recordMapper, clock);
        reviewService = new MoodReviewService(
                new MoodReviewMapper(jdbc), statsService, clock, manager, new AiJobAdmission(manager));
        reviewService.setReviewJobs(sessions.getMapper(AiJobMapper.class));

        cleanupBenchmarkUser();
        seedSyntheticRecords();
    }

    @AfterAll
    void cleanupAfterBenchmark() {
        if (jdbc != null) {
            cleanupBenchmarkUser();
        }
    }

    private void cleanupBenchmarkUser() {
        long maxId = BG_USER_START + BG_USERS - 1;
        jdbc.update("DELETE FROM ai_job WHERE user_id BETWEEN ? AND ?", BENCH_USER_ID, maxId);
        jdbc.update("DELETE FROM mood_review WHERE user_id BETWEEN ? AND ?", BENCH_USER_ID, maxId);
        jdbc.update("DELETE FROM mood_record WHERE user_id BETWEEN ? AND ?", BENCH_USER_ID, maxId);
        jdbc.update("DELETE FROM user WHERE id BETWEEN ? AND ?", BENCH_USER_ID, maxId);
    }

    private void seedSyntheticRecords() {
        seedRecordsForUser(BENCH_USER_ID, TOTAL_RECORDS);
        jdbc.queryForList("ANALYZE TABLE mood_record, mood_review");
    }

    private void seedBackgroundUsers() {
        for (int offset = 0; offset < BG_USERS; offset++) {
            seedRecordsForUser(BG_USER_START + offset, TOTAL_RECORDS);
        }
        jdbc.queryForList("ANALYZE TABLE mood_record, mood_review");
    }

    private void seedRecordsForUser(long userId, int count) {
        jdbc.update("INSERT INTO user(id) VALUES (?)", userId);
        String longText = "今".repeat(1000);
        String shortText = "记录一下今天的心情波动与呼吸节奏。";
        LocalDate endDay = LocalDate.of(2026, 11, 13);
        LocalDate startDay = endDay.minusDays(DAYS_SPAN - 1L);

        List<Object[]> batch = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int dayOffset = index * DAYS_SPAN / count;
            LocalDateTime createdAt = startDay.plusDays(dayOffset).atTime(8 + (index % 14), index % 60, 0);
            boolean useLong = index % 3 == 0;
            boolean aiEnabled = index % 7 != 0;
            String mood = MOODS.get(index % MOODS.size());
            batch.add(new Object[]{
                    userId, mood, useLong ? longText : shortText,
                    aiEnabled ? 1 : 0, 1L, 1, Timestamp.valueOf(createdAt)
            });
        }

        jdbc.batchUpdate(
                "INSERT INTO mood_record(user_id,mood_type,content,ai_reply_enabled,content_version,status,create_time) VALUES (?,?,?,?,?,?,?)",
                batch);
    }

    @Test
    void evaluateTimeoutBudgetAndExplainAnalyze() {
        System.out.println("=== SCENARIO_A_SOLO_2000_ROWS ===");
        var soloRecent30 = runTimingSuite();
        printExplainSuite("SOLO");

        seedBackgroundUsers();
        System.out.println("=== SCENARIO_B_MULTI_USER_22000_ROWS ===");
        var multiRecent30 = runTimingSuite();
        Map<String, String> multiExplains = printExplainSuite("MULTI");

        assertThat(multiExplains.get("week")).contains("idx_mood_record_user_status_time");
        assertThat(multiExplains.get("recent30")).contains("idx_mood_record_user_status_time");
        assertThat(multiExplains.get("sources")).contains("idx_mood_record_user_status_time");
        assertThat(multiExplains.get("history50")).contains("idx_mood_record_user_status_time");
        assertThat(soloRecent30.maxMs()).isLessThan(SOCKET_TIMEOUT_MS);
        assertThat(multiRecent30.maxMs()).isLessThan(SOCKET_TIMEOUT_MS);
    }

    private TimingSummary runTimingSuite() {
        var weekStatsTimings = measure(10, () -> statsService.getWeekStats(BENCH_USER_ID, "2026-11-02"));
        var recent30Timings = measure(10, () -> statsService.getRecent30Stats(BENCH_USER_ID));
        var historyPage1Timings = measure(10, () -> recordMapper.selectPage(
                new Page<>(1, 10),
                new QueryWrapper<MoodRecord>().eq("user_id", BENCH_USER_ID).eq("status", 1).orderByDesc("create_time")));
        var historyPage50Timings = measure(10, () -> recordMapper.selectPage(
                new Page<>(50, 10),
                new QueryWrapper<MoodRecord>().eq("user_id", BENCH_USER_ID).eq("status", 1).orderByDesc("create_time")));

        var prepareWeekTimings = measurePrepare("WEEK", "2026-11-02", true);
        var prepareRecent30Timings = measurePrepare("RECENT_30", null, false);

        printTiming("week_stats", weekStatsTimings);
        printTiming("recent30_stats", recent30Timings);
        printTiming("history_page1", historyPage1Timings);
        printTiming("history_page50", historyPage50Timings);
        printTiming("prepare_week", prepareWeekTimings);
        printTiming("prepare_recent30", prepareRecent30Timings);
        return prepareRecent30Timings;
    }

    private Map<String, String> printExplainSuite(String tag) {
        String weekExplain = explainAnalyze(
                "EXPLAIN ANALYZE SELECT * FROM mood_record WHERE user_id=? AND status=1 AND create_time>=? AND create_time<? ORDER BY create_time",
                BENCH_USER_ID, Timestamp.valueOf("2026-11-02 00:00:00"), Timestamp.valueOf("2026-11-09 00:00:00"));
        String recent30Explain = explainAnalyze(
                "EXPLAIN ANALYZE SELECT * FROM mood_record WHERE user_id=? AND status=1 AND create_time>=? AND create_time<? ORDER BY create_time",
                BENCH_USER_ID, Timestamp.valueOf("2026-10-15 00:00:00"), Timestamp.valueOf("2026-11-14 00:00:00"));
        String sourcesExplain = explainFormatTree(
                "EXPLAIN FORMAT=TREE SELECT * FROM mood_record WHERE user_id=? AND status=1 AND create_time>=? AND create_time<? ORDER BY create_time,id FOR UPDATE",
                BENCH_USER_ID, Timestamp.valueOf("2026-10-15 00:00:00"), Timestamp.valueOf("2026-11-14 00:00:00"));
        String historyPageExplain = explainAnalyze(
                "EXPLAIN ANALYZE SELECT * FROM mood_record WHERE user_id=? AND status=1 ORDER BY create_time DESC LIMIT 10 OFFSET 490",
                BENCH_USER_ID);
        String reviewHistoryExplain = explainAnalyze(
                "EXPLAIN ANALYZE SELECT * FROM mood_review WHERE user_id=? AND review_type='WEEK' ORDER BY range_start DESC,revision DESC LIMIT 5 OFFSET 0",
                BENCH_USER_ID);

        System.out.println("=== EXPLAIN_ANALYZE_" + tag + "_WEEK_STATS ===\n" + weekExplain);
        System.out.println("=== EXPLAIN_ANALYZE_" + tag + "_RECENT30_STATS ===\n" + recent30Explain);
        System.out.println("=== EXPLAIN_" + tag + "_SOURCES_FOR_UPDATE ===\n" + sourcesExplain);
        System.out.println("=== EXPLAIN_ANALYZE_" + tag + "_HISTORY_PAGE50 ===\n" + historyPageExplain);
        System.out.println("=== EXPLAIN_ANALYZE_" + tag + "_REVIEW_HISTORY ===\n" + reviewHistoryExplain);
        return Map.of("week", weekExplain, "recent30", recent30Explain, "sources", sourcesExplain, "history50", historyPageExplain);
    }

    private TimingSummary measurePrepare(String type, String start, boolean regenerate) {
        // 预热一次后清理未终态，再连续实测 10 次 prepare() 写入事务。
        reviewService.generate(BENCH_USER_ID, type, start, regenerate);
        markBenchmarkJobsFailed();

        double[] samples = new double[ITERATIONS];
        for (int index = 0; index < ITERATIONS; index++) {
            long begin = System.nanoTime();
            reviewService.generate(BENCH_USER_ID, type, start, regenerate);
            samples[index] = (System.nanoTime() - begin) / 1_000_000.0;
            markBenchmarkJobsFailed();
        }
        return TimingSummary.of(samples);
    }

    private void markBenchmarkJobsFailed() {
        jdbc.update("UPDATE ai_job SET status='failed' WHERE user_id=? AND status IN ('queued','running')", BENCH_USER_ID);
        jdbc.update("UPDATE mood_review SET status='failed' WHERE user_id=? AND status IN ('queued','running')", BENCH_USER_ID);
    }

    private TimingSummary measure(int count, Runnable action) {
        action.run();
        double[] samples = new double[count];
        for (int index = 0; index < count; index++) {
            long begin = System.nanoTime();
            action.run();
            samples[index] = (System.nanoTime() - begin) / 1_000_000.0;
        }
        return TimingSummary.of(samples);
    }

    private String explainAnalyze(String sql, Object... args) {
        return collectPlanText(jdbc.queryForList(sql, args));
    }

    private String explainFormatTree(String sql, Object... args) {
        return collectPlanText(jdbc.queryForList(sql, args));
    }

    private String collectPlanText(List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> row : rows) {
            for (Object value : row.values()) {
                builder.append(value);
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private void printTiming(String name, TimingSummary summary) {
        System.out.printf("TIMING %s medianMs=%.2f maxMs=%.2f minMs=%.2f%n",
                name, summary.medianMs(), summary.maxMs(), summary.minMs());
    }

    private record TimingSummary(double minMs, double medianMs, double maxMs) {
        static TimingSummary of(double[] values) {
            double[] sorted = Arrays.copyOf(values, values.length);
            Arrays.sort(sorted);
            double median = sorted.length % 2 == 0
                    ? (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0
                    : sorted[sorted.length / 2];
            return new TimingSummary(sorted[0], median, sorted[sorted.length - 1]);
        }
    }
}
