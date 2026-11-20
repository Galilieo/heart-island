package com.xinyu.community;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.xinyu.dto.AdminReportHandleDTO;
import com.xinyu.dto.CommunityReportCreateDTO;
import com.xinyu.dto.CommunityPostAddDTO;
import com.xinyu.dto.CommunityPostUpdateDTO;
import com.xinyu.dto.CommunityReplyAddDTO;
import com.xinyu.dto.CommunityReplyUpdateDTO;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.CommunityReply;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import com.xinyu.mapper.ContentReportMapper;
import com.xinyu.mapper.FavoriteMapper;
import com.xinyu.mapper.PostLikeMapper;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.mapper.UserMapper;
import com.xinyu.service.AdminStatusResult;
import com.xinyu.service.CommunityPostService;
import com.xinyu.service.CommunityReplyService;
import com.xinyu.service.CommunityReportService;
import com.xinyu.vo.AdminPostVO;
import com.xinyu.vo.AdminReplyVO;
import com.xinyu.vo.AdminReportQueueRowVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 隔离 MySQL（xinyu_test_*）真实集成测试：验证唯一约束、90 天与日限额、
 * 队列聚合排序（SAFETY 优先），以及双管理员并发处理下回复数只扣减一次。
 */
class CommunityReportPersistenceIT {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static JdbcTemplate jdbc;
    private static TransactionTemplate txTemplate;
    private static CommunityReportService reportService;
    private static CommunityPostService postService;
    private static CommunityReplyService replyService;

    @BeforeAll
    static void setUpIsolatedDatabase() throws Exception {
        String url = System.getenv("AI_TEST_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(), "未配置显式的 AI_TEST_JDBC_URL 隔离测试库");
        assertThat(url).startsWith("jdbc:mysql://");
        URI uri = URI.create(url.substring("jdbc:".length()));
        assertThat(uri.getPath()).matches("/xinyu_test_[a-zA-Z0-9_]+");
        assertThat(uri.getQuery()).isNull();
        assertThat(uri.getUserInfo()).isNull();
        assertThat(uri.getFragment()).isNull();

        var source = new DriverManagerDataSource(
                url,
                System.getenv().getOrDefault("AI_TEST_DB_USER", "root"),
                System.getenv().getOrDefault("AI_TEST_DB_PASSWORD", ""));
        jdbc = new JdbcTemplate(source);
        txTemplate = new TransactionTemplate(new DataSourceTransactionManager(source));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",
                Integer.class)).isZero();

        new ResourceDatabasePopulator(
                new ClassPathResource("ai/schema.sql"),
                new ClassPathResource("ai/community-schema.sql"),
                new FileSystemResource(Path.of("../sql/migrations/20261111_01_community_ai_generation.sql").toAbsolutePath()),
                new FileSystemResource(Path.of("../sql/migrations/20261114_01_community_report.sql").toAbsolutePath()),
                new FileSystemResource(Path.of("../sql/migrations/20261119_01_community_ai_result_kind.sql").toAbsolutePath())
        ).execute(source);
        // 补建测试所需的最小 user 列（ai/schema.sql 仅建 id 列），供 UserMapper 查询管理员名称
        jdbc.execute("ALTER TABLE user ADD COLUMN username VARCHAR(50) NULL, ADD COLUMN nickname VARCHAR(50) NULL, "
                + "ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER', ADD COLUMN status TINYINT NOT NULL DEFAULT 1");

        verifyRiskMigration(source);

        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ContentReportMapper.class);
        configuration.addMapper(CommunityPostMapper.class);
        configuration.addMapper(CommunityReplyMapper.class);
        configuration.addMapper(TopicMapper.class);
        configuration.addMapper(PostLikeMapper.class);
        configuration.addMapper(FavoriteMapper.class);
        configuration.addMapper(UserMapper.class);
        factory.setConfiguration(configuration);
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        factory.setPlugins(pagination);

        var sessions = new SqlSessionTemplate(factory.getObject());
        ContentReportMapper reportMapper = sessions.getMapper(ContentReportMapper.class);
        CommunityPostMapper postMapper = sessions.getMapper(CommunityPostMapper.class);
        CommunityReplyMapper replyMapper = sessions.getMapper(CommunityReplyMapper.class);
        TopicMapper topicMapper = sessions.getMapper(TopicMapper.class);
        PostLikeMapper postLikeMapper = sessions.getMapper(PostLikeMapper.class);
        FavoriteMapper favoriteMapper = sessions.getMapper(FavoriteMapper.class);
        UserMapper userMapper = sessions.getMapper(UserMapper.class);

        reportService = new CommunityReportService();
        ReflectionTestUtils.setField(reportService, "contentReportMapper", reportMapper);
        ReflectionTestUtils.setField(reportService, "communityPostMapper", postMapper);
        ReflectionTestUtils.setField(reportService, "communityReplyMapper", replyMapper);
        ReflectionTestUtils.setField(reportService, "userMapper", userMapper);

        postService = new CommunityPostService();
        ReflectionTestUtils.setField(postService, "communityPostMapper", postMapper);
        ReflectionTestUtils.setField(postService, "topicMapper", topicMapper);
        ReflectionTestUtils.setField(postService, "postLikeMapper", postLikeMapper);
        ReflectionTestUtils.setField(postService, "favoriteMapper", favoriteMapper);
        ReflectionTestUtils.setField(postService, "contentReportMapper", reportMapper);

        replyService = new CommunityReplyService();
        ReflectionTestUtils.setField(replyService, "communityReplyMapper", replyMapper);
        ReflectionTestUtils.setField(replyService, "communityPostMapper", postMapper);
        ReflectionTestUtils.setField(replyService, "contentReportMapper", reportMapper);
    }

    private static void verifyRiskMigration(DriverManagerDataSource source) {
        jdbc.update("INSERT INTO topic(id,name,status) VALUES (1,'测试话题',1)");
        jdbc.update("INSERT INTO community_post(id,user_id,topic_id,mood_type,content) VALUES (10,1,1,'低落','我不想活了')");
        jdbc.update("INSERT INTO community_reply(id,post_id,user_id,content) VALUES (20,10,1,'我不想活了')");

        new ResourceDatabasePopulator(new FileSystemResource(
                Path.of("../sql/migrations/20261120_01_community_risk_status.sql"))).execute(source);

        for (String table : List.of("community_post", "community_reply")) {
            List<String> columns = jdbc.queryForList("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema=DATABASE() AND table_name=?
                      AND ordinal_position BETWEEN
                        (SELECT ordinal_position FROM information_schema.columns
                         WHERE table_schema=DATABASE() AND table_name=? AND column_name='hide_reason')
                        AND
                        (SELECT ordinal_position FROM information_schema.columns
                         WHERE table_schema=DATABASE() AND table_name=? AND column_name='create_time')
                    ORDER BY ordinal_position
                    """, String.class, table, table, table);
            assertThat(columns).containsExactly(
                    "hide_reason", "risk_status", "risk_hit_time", "risk_reviewed_time", "create_time");
            assertThat(jdbc.queryForList("SELECT risk_status, risk_hit_time, risk_reviewed_time FROM " + table))
                    .singleElement().satisfies(row -> assertThat(row.values()).containsOnlyNulls());
        }
    }

    @BeforeEach
    void resetFixtures() {
        jdbc.update("DELETE FROM content_report");
        jdbc.update("DELETE FROM community_reply");
        jdbc.update("DELETE FROM community_post");
        jdbc.update("DELETE FROM topic");
        jdbc.update("DELETE FROM ai_reply");
        jdbc.update("DELETE FROM mood_record");
        jdbc.update("DELETE FROM user");

        jdbc.update("INSERT INTO user(id,username,nickname,role,status) VALUES "
                + "(1,'author','作者','USER',1),"
                + "(2,'reporter1','举报者1','USER',1),"
                + "(3,'reporter2','举报者2','USER',1),"
                + "(9,'admin','管理员','ADMIN',1)");
        jdbc.update("INSERT INTO topic(id,name,status) VALUES (1,'情绪树洞',1)");
        ReflectionTestUtils.setField(reportService, "clock", Clock.system(SHANGHAI));
    }

    @Test
    void postRiskAndContentAreSavedTogetherAndFailedEditLeavesBothUntouched() {
        CommunityPostAddDTO add = new CommunityPostAddDTO();
        add.setTopicId(1L);
        add.setMoodType("低落");
        add.setContent("  我不想活了  ");
        add.setNeedAiReply(false);
        assertThat(postService.add(add, 1L).saved()).isTrue();
        long id = jdbc.queryForObject("SELECT id FROM community_post", Long.class);
        String table = "community_post";
        assertPending(table, id);
        assertThat(riskRow(table, id)).containsEntry("content", "我不想活了");

        CommunityPostUpdateDTO edit = new CommunityPostUpdateDTO();
        edit.setId(id);
        edit.setTopicId(1L);
        edit.setMoodType("低落");
        edit.setNeedAiReply(false);
        edit.setContent("比较平静的一天");
        var beforeEdit = riskRow(table, id);
        assertThat(postService.update(edit, 2L).saved()).isFalse();
        assertThat(riskRow(table, id)).isEqualTo(beforeEdit);

        assertThat(postService.update(edit, 1L).saved()).isTrue();
        assertThat(riskRow(table, id)).containsEntry("content", "比较平静的一天")
                .containsEntry("risk_status", "PENDING")
                .containsEntry("risk_hit_time", beforeEdit.get("risk_hit_time"));

        assertThat(postService.reviewRisk(id)).isEqualTo(AdminStatusResult.UPDATED);
        assertThat(riskRow(table, id)).containsEntry("risk_status", "REVIEWED")
                .extractingByKey("risk_reviewed_time").isNotNull();
        assertThat(postService.reviewRisk(id)).isEqualTo(AdminStatusResult.CONFLICT);
        assertThat(postService.reviewRisk(999L)).isEqualTo(AdminStatusResult.NOT_FOUND);

        var beforeFailedEdit = riskRow(table, id);
        edit.setContent("我不想活了" + "x".repeat(70_000));
        assertThatThrownBy(() -> postService.update(edit, 1L))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(riskRow(table, id)).isEqualTo(beforeFailedEdit);

        edit.setContent("我不想活了");
        assertThat(postService.update(edit, 1L).saved()).isTrue();
        assertPending(table, id);

        assertThat(postService.delete(id, 1L)).isTrue();
        assertPending(table, id);
        assertThat(postService.pageAdminPosts(null, null, null, null, null, null, "PENDING", 1L, 10L).getRecords())
                .extracting(AdminPostVO::getId).containsExactly(Long.toString(id));
    }

    @Test
    void replyRiskSurvivesNonMatchingEditAndAdminHide() {
        jdbc.update("INSERT INTO community_post(id,user_id,topic_id,mood_type,content) VALUES (100,1,1,'低落','测试正文')");
        CommunityReplyAddDTO add = new CommunityReplyAddDTO();
        add.setPostId(100L);
        add.setContent("  我不想活了  ");
        txTemplate.executeWithoutResult(status -> replyService.add(add, 2L));
        long id = jdbc.queryForObject("SELECT id FROM community_reply", Long.class);
        String table = "community_reply";
        assertPending(table, id);
        assertThat(riskRow(table, id)).containsEntry("content", "我不想活了");

        CommunityReplyUpdateDTO edit = new CommunityReplyUpdateDTO();
        edit.setId(id);
        edit.setContent("比较平静的一天");
        var beforeEdit = riskRow(table, id);
        txTemplate.executeWithoutResult(status -> replyService.update(edit, 1L));
        assertThat(riskRow(table, id)).isEqualTo(beforeEdit);

        txTemplate.executeWithoutResult(status -> replyService.update(edit, 2L));
        assertThat(riskRow(table, id)).containsEntry("content", "比较平静的一天")
                .containsEntry("risk_status", "PENDING")
                .containsEntry("risk_hit_time", beforeEdit.get("risk_hit_time"));

        assertThat(replyService.reviewRisk(id)).isEqualTo(AdminStatusResult.UPDATED);
        assertThat(riskRow(table, id)).containsEntry("risk_status", "REVIEWED")
                .extractingByKey("risk_reviewed_time").isNotNull();
        assertThat(replyService.reviewRisk(id)).isEqualTo(AdminStatusResult.CONFLICT);
        assertThat(replyService.reviewRisk(999L)).isEqualTo(AdminStatusResult.NOT_FOUND);

        var beforeFailedEdit = riskRow(table, id);
        edit.setContent("我不想活了" + "x".repeat(70_000));
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> replyService.update(edit, 2L)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(riskRow(table, id)).isEqualTo(beforeFailedEdit);

        edit.setContent("我不想活了");
        txTemplate.executeWithoutResult(status -> replyService.update(edit, 2L));
        assertPending(table, id);

        txTemplate.executeWithoutResult(status -> assertThat(
                replyService.updateAdminStatusWithOutcome(id, 2, "AD")).isEqualTo(AdminStatusResult.UPDATED));
        assertPending(table, id);
        assertThat(replyService.pageAdminReplies(null, null, null, null, "PENDING", 1L, 10L).getRecords())
                .extracting(AdminReplyVO::getId).containsExactly(Long.toString(id));
    }

    private static Map<String, Object> riskRow(String table, long id) {
        return jdbc.queryForMap(
                "SELECT content,risk_status,risk_hit_time,risk_reviewed_time FROM " + table + " WHERE id=?", id);
    }

    private static void assertPending(String table, long id) {
        assertThat(riskRow(table, id)).containsEntry("risk_status", "PENDING")
                .containsEntry("risk_reviewed_time", null)
                .extractingByKey("risk_hit_time").isNotNull();
    }

    @Test
    void adminRiskFiltersComposeWithVisibilityAndOrderPendingByHitTime() {
        jdbc.update("""
                INSERT INTO community_post(id,user_id,topic_id,mood_type,content,status,risk_status,risk_hit_time,create_time)
                VALUES (100,1,1,'低落','旧命中',1,'PENDING','2026-11-18','2026-11-17'),
                       (101,1,1,'低落','新命中并隐藏',2,'PENDING','2026-11-19','2026-11-16'),
                       (102,1,1,'低落','已关注',0,'REVIEWED','2026-11-20','2026-11-18'),
                       (103,1,1,'平静','未命中',1,NULL,NULL,'2026-11-19')
                """);
        jdbc.update("""
                INSERT INTO community_reply(id,post_id,user_id,content,status,risk_status,risk_hit_time,create_time)
                VALUES (200,100,2,'旧命中',1,'PENDING','2026-11-18','2026-11-17'),
                       (201,100,2,'新命中并删除',0,'PENDING','2026-11-19','2026-11-16'),
                       (202,100,2,'已关注',2,'REVIEWED','2026-11-20','2026-11-18'),
                       (203,100,2,'未命中',1,NULL,NULL,'2026-11-19')
                """);

        assertThat(postService.pageAdminPosts(null, null, null, null, null, null, "PENDING", 1L, 10L).getRecords())
                .extracting(AdminPostVO::getId).containsExactly("101", "100");
        assertThat(postService.pageAdminPosts(1, null, null, null, null, null, "PENDING", 1L, 10L).getRecords())
                .extracting(AdminPostVO::getId).containsExactly("100");
        assertThat(postService.pageAdminPosts(null, null, null, null, null, null, "REVIEWED", 1L, 10L).getRecords())
                .extracting(AdminPostVO::getId).containsExactly("102");
        assertThat(postService.pageAdminPosts(null, null, null, null, null, null, null, 1L, 10L).getRecords())
                .extracting(AdminPostVO::getId).containsExactly("103", "102", "100", "101");

        assertThat(replyService.pageAdminReplies(null, null, null, null, "PENDING", 1L, 10L).getRecords())
                .extracting(AdminReplyVO::getId).containsExactly("201", "200");
        assertThat(replyService.pageAdminReplies(1, null, null, null, "PENDING", 1L, 10L).getRecords())
                .extracting(AdminReplyVO::getId).containsExactly("200");
        assertThat(replyService.pageAdminReplies(null, null, null, null, "REVIEWED", 1L, 10L).getRecords())
                .extracting(AdminReplyVO::getId).containsExactly("202");
        assertThat(replyService.pageAdminReplies(null, null, null, null, null, 1L, 10L).getRecords())
                .extracting(AdminReplyVO::getId).containsExactly("203", "202", "200", "201");
    }

    @Test
    void uniqueConstraintNinetyDayFilterAndSafetyFirstQueueOrderingWorkOnRealMysql() {
        jdbc.update("INSERT INTO community_post(id,user_id,topic_id,mood_type,content,status) "
                + "VALUES (100,1,1,'低落','较早被举报的广告帖',1),"
                + "(101,1,1,'焦虑','较晚被举报的安全担心帖',1)");

        Instant base = LocalDateTime.of(2026, 11, 14, 12, 0).atZone(SHANGHAI).toInstant();
        ReflectionTestUtils.setField(reportService, "clock", Clock.fixed(base, SHANGHAI));
        reportService.submitReport(2L, createDto("POST", 100L, "AD", null));

        // 同一人重复举报同一帖子触发友好冲突，不抛未捕获 500
        assertThatThrownBy(() -> reportService.submitReport(2L, createDto("POST", 100L, "AD", null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        // 较晚时间点提交 SAFETY 举报，待处理队列仍应排在更早的 AD 之前
        ReflectionTestUtils.setField(reportService, "clock", Clock.fixed(base.plusSeconds(3600), SHANGHAI));
        reportService.submitReport(3L, createDto("POST", 101L, "SAFETY", "担心状态"));

        List<AdminReportQueueRowVO> rows = reportService.pageAdminReports("PENDING", 1L, 10L).getRecords();
        assertThat(rows).extracting(AdminReportQueueRowVO::getTargetId).containsExactly("101", "100");
        assertThat(reportService.getPendingTargetCount()).isEqualTo(2L);

        // 90 天过滤：插入 1 条 91 天前历史举报不出现在「我的举报」中
        jdbc.update("INSERT INTO content_report(reporter_id,target_type,target_id,post_id,target_user_id,"
                        + "reason,content_snapshot,status,create_time) VALUES (2,'POST',101,101,1,'OTHER','91天前快照','DISMISSED',?)",
                Timestamp.valueOf(LocalDateTime.of(2026, 8, 10, 10, 0)));

        ReflectionTestUtils.setField(reportService, "clock", Clock.fixed(base.plusSeconds(7200), SHANGHAI));
        assertThat(reportService.listMine(2L, 1L, 10L).getRecords())
                .extracting(r -> r.getTargetId())
                .containsExactly("100");
    }

    @Test
    void concurrentAdminHandleHideAllowsOnlyOneWinnerAndDecrementsReplyCountOnce() throws Exception {
        jdbc.update("INSERT INTO community_post(id,user_id,topic_id,mood_type,content,reply_count,status) "
                + "VALUES (200,1,1,'低落','正常帖子',1,1)");
        jdbc.update("INSERT INTO community_reply(id,post_id,user_id,content,status) "
                + "VALUES (300,200,1,'违规回复内容',1)");
        reportService.submitReport(2L, createDto("REPLY", 300L, "AD", null));
        reportService.submitReport(3L, createDto("REPLY", 300L, "AD", null));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Callable<String> task = () -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                try {
                    txTemplate.executeWithoutResult(status -> reportService.handleReport(9L, handleHide("REPLY", 300L, "AD")));
                    return "OK";
                } catch (ResponseStatusException conflict) {
                    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(conflict.getReason()).isEqualTo("已被其他管理员处理");
                    return "CONFLICT";
                }
            };
            futures.add(pool.submit(task));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> future : futures) {
            outcomes.add(future.get(10, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertThat(outcomes).containsExactlyInAnyOrder("OK", "CONFLICT");
        assertThat(jdbc.queryForObject("SELECT status FROM community_reply WHERE id=300", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT reply_count FROM community_post WHERE id=200", Integer.class)).isZero();

        // 恢复显示时 reply_count 加一，作者本人可见自己被隐藏的回复，他人不可见
        assertThat(replyService.updateAdminStatusWithOutcome(300L, 1, null)).isEqualTo(AdminStatusResult.UPDATED);
        assertThat(jdbc.queryForObject("SELECT reply_count FROM community_post WHERE id=200", Integer.class)).isEqualTo(1);

        // 隐藏后再由作者删除(2->0)：reply_count 保持 0 不再减成负数或重复扣减
        assertThat(replyService.updateAdminStatusWithOutcome(300L, 2, "AD")).isEqualTo(AdminStatusResult.UPDATED);
        assertThat(jdbc.queryForObject("SELECT reply_count FROM community_post WHERE id=200", Integer.class)).isZero();
        List<CommunityReply> authorReplies = replyService.listByPostId(200L, 1L);
        assertThat(authorReplies).hasSize(1);
        assertThat(authorReplies.get(0).getHidden()).isTrue();
        assertThat(authorReplies.get(0).getHideReason()).isEqualTo("AD");
        assertThat(replyService.listByPostId(200L, 2L)).isEmpty();

        assertThat(replyService.delete(300L, 1L)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM community_reply WHERE id=300", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT reply_count FROM community_post WHERE id=200", Integer.class)).isZero();
    }

    private static CommunityReportCreateDTO createDto(String type, Long targetId, String reason, String detail) {
        CommunityReportCreateDTO dto = new CommunityReportCreateDTO();
        dto.setTargetType(type);
        dto.setTargetId(targetId);
        dto.setReason(reason);
        dto.setDetail(detail);
        return dto;
    }

    private static AdminReportHandleDTO handleHide(String type, Long targetId, String hideReason) {
        AdminReportHandleDTO dto = new AdminReportHandleDTO();
        dto.setTargetType(type);
        dto.setTargetId(targetId);
        dto.setAction("HIDE");
        dto.setHideReason(hideReason);
        return dto;
    }
}
