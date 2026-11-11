package com.xinyu.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyu.dto.AiReplyResult;
import com.xinyu.dto.CommunityPostAddDTO;
import com.xinyu.dto.CommunityPostUpdateDTO;
import com.xinyu.dto.CommunityReplyAddDTO;
import com.xinyu.mapper.AiReplyMapper;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import com.xinyu.mapper.PostLikeMapper;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.service.AiReplyLogService;
import com.xinyu.service.CommunityPostService;
import com.xinyu.service.CommunityReplyService;
import com.xinyu.config.RestTemplateConfig;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** 真实MySQL与Mapper，供应商传输替换为合成响应；专用空测试库与心情IT分开运行。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommunityAiPersistenceIT {
    private JdbcTemplate jdbc;
    private CommunityPostService posts;
    private AiReplyLogService logs;
    private MockRestServiceServer upstream;
    private CommunityReplyService comments;
    private final PostCounterInterleaving counterInterleaving = new PostCounterInterleaving();

    @BeforeAll
    void connectToEmptyTestDatabase() throws Exception {
        String url = System.getenv("AI_TEST_JDBC_URL");
        assumeTrue(url != null, "需要显式配置隔离测试库");
        URI uri = URI.create(url.substring(5));
        assertThat(url).startsWith("jdbc:mysql://");
        assertThat(uri.getPath()).matches("/xinyu_test_[A-Za-z0-9_]+");
        assertThat(uri.getQuery()).isNull();
        assertThat(uri.getUserInfo()).isNull();
        assertThat(uri.getFragment()).isNull();
        var source = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("AI_TEST_DB_USER", "root"),
                System.getenv().getOrDefault("AI_TEST_DB_PASSWORD", ""));
        jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE()", Integer.class)).isZero();
        new ResourceDatabasePopulator(new ClassPathResource("ai/schema.sql"),
                new ClassPathResource("ai/community-schema.sql")).execute(source);

        // 在旧表插入历史回复，再执行与部署相同的迁移，确认NULL标识不会清除历史数据。
        jdbc.update("INSERT INTO topic(id,name,status) VALUES (1,'合成话题',1)");
        jdbc.update("INSERT INTO community_post(user_id,topic_id,mood_type,content,ai_reply) "
                + "VALUES (1,1,'平静','迁移前内容','迁移前回复')");
        Path migration = Path.of("../sql/migrations/20261111_01_community_ai_generation.sql").toAbsolutePath();
        new ResourceDatabasePopulator(new FileSystemResource(migration)).execute(source);
        var legacy = jdbc.queryForMap("SELECT content,ai_reply,ai_generation_id FROM community_post");
        assertThat(legacy.get("content")).isEqualTo("迁移前内容");
        assertThat(legacy.get("ai_reply")).isEqualTo("迁移前回复");
        assertThat(legacy.get("ai_generation_id")).isNull();

        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AiReplyMapper.class);
        configuration.addMapper(CommunityPostMapper.class);
        configuration.addMapper(TopicMapper.class);
        configuration.addMapper(PostLikeMapper.class);
        configuration.addMapper(CommunityReplyMapper.class);
        factory.setConfiguration(configuration);
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        factory.setPlugins(pagination, counterInterleaving);
        var sessions = new SqlSessionTemplate(factory.getObject());
        logs = new AiReplyLogService();
        ReflectionTestUtils.setField(logs, "aiReplyMapper", sessions.getMapper(AiReplyMapper.class));
        posts = new CommunityPostService();
        ReflectionTestUtils.setField(posts, "communityPostMapper", sessions.getMapper(CommunityPostMapper.class));
        ReflectionTestUtils.setField(posts, "topicMapper", sessions.getMapper(TopicMapper.class));
        ReflectionTestUtils.setField(posts, "aiReplyLogService", logs);
        ReflectionTestUtils.setField(posts, "postLikeMapper", sessions.getMapper(PostLikeMapper.class));
        comments = new CommunityReplyService();
        ReflectionTestUtils.setField(comments, "communityPostMapper", sessions.getMapper(CommunityPostMapper.class));
        ReflectionTestUtils.setField(comments, "communityReplyMapper", sessions.getMapper(CommunityReplyMapper.class));
    }

    @BeforeEach
    void resetSyntheticRowsAndProvider() {
        jdbc.update("DELETE FROM community_post");
        jdbc.update("DELETE FROM ai_reply");
        jdbc.update("INSERT IGNORE INTO topic(id,name,status) VALUES (1,'合成话题',1)");
        RestTemplate http = new RestTemplate();
        upstream = MockRestServiceServer.bindTo(http).build();
        ReflectionTestUtils.setField(posts, "aiReplyService", CommunityAiServiceTests.service(http));
    }

    @Test
    void failureLogRetainsDiagnosticButDiscardsPromptAndFallback() {
        logs.savePostReply(1L, 11L, AiReplyResult.failure(
                "synthetic-fallback", "synthetic-private-prompt", "synthetic-model", "synthetic-private-error"));
        var row = jdbc.queryForMap("SELECT * FROM ai_reply");
        assertThat(row.get("prompt")).isNull();
        assertThat(row.get("reply_content")).isNull();
        assertThat(row.get("error_message")).isEqualTo("synthetic-private-error");
        assertThat(row.get("status")).isEqualTo(0);
    }

    @Test
    void globalAdminCanReadAndSearchStoredPromptReplyAndOriginalError() throws Exception {
        jdbc.update("INSERT INTO ai_reply(user_id,target_type,target_id,prompt,reply_content,model_name,status,error_message) "
                + "VALUES (1,'POST',11,'hidden-prompt','hidden-reply','synthetic-model',0,'hidden-error')");
        var page = logs.pageAdminAiReplies(null, null, null, null, 1L, 10L);
        var row = new ObjectMapper().findAndRegisterModules().valueToTree(page).path("records").get(0);
        assertThat(row.properties().stream().map(java.util.Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("id", "userId", "targetType", "targetId", "modelName",
                        "status", "prompt", "replyContent", "errorMessage", "createTime");
        assertThat(row.path("prompt").asText()).isEqualTo("hidden-prompt");
        assertThat(row.path("replyContent").asText()).isEqualTo("hidden-reply");
        assertThat(row.path("errorMessage").asText()).isEqualTo("hidden-error");
        for (String storedText : new String[]{"hidden-prompt", "hidden-reply", "hidden-error"}) {
            assertThat(logs.pageAdminAiReplies(null, null, null, storedText, 1L, 10L).getTotal()).isEqualTo(1);
        }
        assertThat(logs.pageAdminAiReplies(1L, "POST", 0, "synthetic-model", 1L, 1L).getTotal())
                .isEqualTo(1);
        // 全站管理员可以跨用户、跨业务类型查看；普通用户是否准入由真实MVC测试验证。
        jdbc.update("INSERT IGNORE INTO user(id) VALUES (2)");
        jdbc.update("INSERT INTO ai_reply(user_id,target_type,target_id,reply_content,status) "
                + "VALUES (2,'MOOD_RECORD',22,'另一用户的合成回复',1)");
        assertThat(logs.pageAdminAiReplies(null, null, null, null, 1L, 10L).getTotal()).isEqualTo(2);
    }

    @Test
    void editClearsOldReplyBeforeProviderRunsAndFailureKeepsItNull() {
        long id = existingPost();
        upstream.expect(anything()).andRespond(request -> {
            assertThat(jdbc.queryForObject("SELECT ai_reply FROM community_post WHERE id=?", String.class, id))
                    .as("旧回复必须在网络请求前清除").isNull();
            return withSuccess("{}", MediaType.APPLICATION_JSON).createResponse(request);
        });
        assertThat(posts.update(edit(id, true), 1L)).isTrue();
        var saved = jdbc.queryForMap("SELECT * FROM community_post WHERE id=?", id);
        assertThat(saved.get("content")).isEqualTo("合成新内容");
        assertThat(saved.get("ai_reply")).isNull();
        assertThat(saved.get("like_count")).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT status FROM ai_reply", Integer.class)).isZero();
        upstream.verify();
    }

    @Test
    void disablingAiWithUnchangedContentRejectsAlreadyGeneratingReply() {
        long id = existingPost();
        upstream.expect(anything()).andRespond(request -> {
            // 旧请求尚未返回时保存相同内容并关闭AI，随后才放行旧响应。
            assertThat(posts.update(edit(id, false), 1L)).isTrue();
            return withSuccess(
                    "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"过期回复\"}}]}",
                    MediaType.APPLICATION_JSON).createResponse(request);
        });

        assertThat(posts.update(edit(id, true), 1L)).isTrue();

        assertThat(jdbc.queryForObject("SELECT ai_reply FROM community_post WHERE id=?", String.class, id))
                .as("关闭AI后，进行中的旧响应也不能恢复回复").isNull();
        upstream.verify();
    }

    @ParameterizedTest(name = "旧回复不得覆盖后续编辑：{0}")
    @ValueSource(strings = {"正文", "心情", "话题", "相同内容", "ABA"})
    void lateReplyCannotOverwriteReplyFromLaterEdit(String change) {
        long id = existingPost();
        jdbc.update("INSERT IGNORE INTO topic(id,name,status) VALUES (2,'另一个合成话题',1)");
        var newer = edit(id, true);
        switch (change) {
            case "正文" -> newer.setContent("合成后续正文");
            case "心情" -> newer.setMoodType("开心");
            case "话题" -> newer.setTopicId(2L);
            default -> { /* 相同内容和ABA最终均保存原文，仍必须区分生成代次。 */ }
        }

        upstream.expect(anything()).andRespond(request -> {
            // 旧响应返回前完成后续编辑及其生成，稳定构造迟到覆盖顺序，无需sleep。
            if ("ABA".equals(change)) {
                var intermediate = edit(id, false);
                intermediate.setContent("合成中间正文B");
                assertThat(posts.update(intermediate, 1L)).isTrue();
            }
            assertThat(posts.update(newer, 1L)).isTrue();
            return generatedReply("过期回复").createResponse(request);
        });
        upstream.expect(anything()).andRespond(generatedReply("当前回复"));

        assertThat(posts.update(edit(id, true), 1L)).isTrue();

        var saved = jdbc.queryForMap("SELECT content,mood_type,topic_id,ai_reply FROM community_post WHERE id=?", id);
        assertThat(saved.get("content")).isEqualTo(newer.getContent());
        assertThat(saved.get("mood_type")).isEqualTo(newer.getMoodType());
        assertThat(((Number) saved.get("topic_id")).longValue()).isEqualTo(newer.getTopicId());
        assertThat(saved.get("ai_reply")).isEqualTo("当前回复");
        upstream.verify();
    }

    @ParameterizedTest(name = "旧生成在帖子失效后不得回写：{0}")
    @ValueSource(strings = {"删除", "隐藏", "隐藏后恢复", "删除后恢复"})
    void removingOrHidingPostInvalidatesPendingReply(String action) {
        long id = existingPost();
        upstream.expect(anything()).andRespond(request -> {
            if (action.startsWith("删除")) {
                assertThat(posts.delete(id, 1L)).isTrue();
            } else {
                assertThat(posts.updateAdminStatus(id, 0)).isNotNull();
            }
            if (action.endsWith("恢复")) {
                assertThat(posts.updateAdminStatus(id, 1)).isNotNull();
            }
            return generatedReply("过期回复").createResponse(request);
        });

        assertThat(posts.update(edit(id, true), 1L)).isTrue();

        var saved = jdbc.queryForMap("SELECT status,ai_reply,ai_generation_id FROM community_post WHERE id=?", id);
        int expectedStatus = action.endsWith("恢复") ? 1 : 0;
        assertThat(((Number) saved.get("status")).intValue()).isEqualTo(expectedStatus);
        assertThat(saved.get("ai_reply")).isNull();
        assertThat(saved.get("ai_generation_id")).isNull();
        upstream.verify();
    }

    @Test
    void concurrentEditorsKeepNewReplyWhenOldHttpResponseArrivesLast() throws Exception {
        long id = existingPost();
        CountDownLatch oldWaiting = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        ExecutorService editor = Executors.newSingleThreadExecutor();
        upstream.expect(anything()).andRespond(request -> {
            oldWaiting.countDown();
            try {
                assertThat(releaseOld.await(10, TimeUnit.SECONDS)).as("旧响应必须显式放行").isTrue();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("合成响应等待被中断", interrupted);
            }
            return generatedReply("过期回复").createResponse(request);
        });
        upstream.expect(anything()).andRespond(generatedReply("当前回复"));

        try {
            var oldSave = editor.submit(() -> posts.update(edit(id, true), 1L));
            assertThat(oldWaiting.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(oldSave).as("旧HTTP响应仍在另一个线程等待").isNotDone();

            assertThat(posts.update(edit(id, true), 1L)).isTrue();
            assertThat(jdbc.queryForObject("SELECT ai_reply FROM community_post WHERE id=?", String.class, id))
                    .isEqualTo("当前回复");
            releaseOld.countDown();
            assertThat(oldSave.get(3, TimeUnit.SECONDS)).isTrue();

            assertThat(jdbc.queryForObject("SELECT ai_reply FROM community_post WHERE id=?", String.class, id))
                    .as("真实双线程交错下，迟到旧响应不能覆盖已保存的新回复").isEqualTo("当前回复");
            upstream.verify();
        } finally {
            releaseOld.countDown();
            editor.shutdownNow();
            assertThat(editor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void generationIdIsNotExposedInPublicPostJson() {
        upstream.expect(anything()).andRespond(generatedReply("合成回复"));
        assertThat(posts.add(add(), 1L)).isTrue();
        long id = jdbc.queryForObject("SELECT id FROM community_post", Long.class);

        var saved = posts.listByTopicId(null, null, false, "latest").get(0);
        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getAiGenerationId()).isNotBlank();
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(saved);
        assertThat(json.has("aiGenerationId")).isFalse();
        assertThat(json.path("aiReply").asText()).isEqualTo("合成回复");
    }

    @ParameterizedTest(name = "互动计数不得恢复旧正文或生成标识：{0}")
    @ValueSource(strings = {"点赞", "取消点赞", "评论", "删除评论"})
    void interactionCounterCannotRestoreSnapshotBeforeAnEdit(String action) {
        long id = existingPost();
        jdbc.update("UPDATE community_post SET ai_generation_id=? WHERE id=?",
                "00000000-0000-0000-0000-000000000001", id);
        if ("取消点赞".equals(action)) {
            jdbc.update("INSERT INTO post_like(post_id,user_id) VALUES (?,1)", id);
        }
        if ("删除评论".equals(action)) {
            jdbc.update("INSERT INTO community_reply(post_id,user_id,content) VALUES (?,1,'合成评论')", id);
            jdbc.update("UPDATE community_post SET reply_count=1 WHERE id=?", id);
        }

        // 计数写入前先完成一次编辑／关闭AI，稳定检查旧快照是否会被整个写回来。
        counterInterleaving.beforeNextPostUpdate = () -> {
            assertThat(posts.update(edit(id, false), 1L)).isTrue();
        };
        try {
            boolean changed;
            switch (action) {
                case "点赞" -> changed = posts.like(id, 1L);
                case "取消点赞" -> changed = posts.unlike(id, 1L);
                case "评论" -> {
                    var dto = new CommunityReplyAddDTO();
                    dto.setPostId(id);
                    dto.setContent("合成评论");
                    changed = comments.add(dto, 1L);
                }
                default -> {
                    long replyId = jdbc.queryForObject("SELECT id FROM community_reply WHERE post_id=?", Long.class, id);
                    changed = comments.delete(replyId, 1L);
                }
            }

            assertThat(changed).isTrue();
            assertThat(counterInterleaving.beforeNextPostUpdate).as("已实际执行交错编辑").isNull();
            var saved = jdbc.queryForMap("SELECT * FROM community_post WHERE id=?", id);
            assertThat(saved.get("content")).isEqualTo("合成新内容");
            assertThat(saved.get("ai_reply")).isNull();
            assertThat(saved.get("ai_generation_id")).isNull();
            int expectedLikes = switch (action) {
                case "点赞" -> 8;
                case "取消点赞" -> 6;
                default -> 7;
            };
            assertThat(((Number) saved.get("like_count")).intValue()).isEqualTo(expectedLikes);
            int expectedReplies = "评论".equals(action) ? 1 : 0;
            assertThat(((Number) saved.get("reply_count")).intValue()).isEqualTo(expectedReplies);
        } finally {
            counterInterleaving.beforeNextPostUpdate = null;
        }
    }

    @Test
    void choosingNoAiClearsReplyWithoutProviderCallOrLog() {
        long id = existingPost();
        assertThat(posts.update(edit(id, false), 1L)).isTrue();
        assertThat(jdbc.queryForObject("SELECT ai_reply FROM community_post WHERE id=?", String.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT ai_generation_id FROM community_post WHERE id=?", String.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_reply", Integer.class)).isZero();
        upstream.verify();
    }

    @Test
    void validProviderReplyStillAppearsOnSavedPost() {
        upstream.expect(anything()).andRespond(withSuccess(
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"合成回复\"}}]}",
                MediaType.APPLICATION_JSON));
        assertThat(posts.add(add(), 1L)).isTrue();
        assertThat(jdbc.queryForObject("SELECT ai_reply FROM community_post", String.class)).isEqualTo("合成回复");
        assertThat(jdbc.queryForObject("SELECT prompt FROM ai_reply", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_reply", Integer.class)).isEqualTo(1);
    }

    @Test
    void newProviderFailureRetainsDetailsForAdminWithoutConfiguredApiKey() {
        upstream.expect(anything()).andRespond(withStatus(org.springframework.http.HttpStatus.BAD_GATEWAY)
                .body("合成上游详细错误 synthetic-private-token").contentType(MediaType.TEXT_PLAIN));
        assertThat(posts.add(add(), 1L)).isTrue();

        var row = jdbc.queryForMap("SELECT * FROM ai_reply");
        assertThat(row.get("status")).isEqualTo(0);
        assertThat(row.get("reply_content")).isNull();
        assertThat((String) row.get("error_message")).contains("502", "合成上游详细错误", "[REDACTED]")
                .doesNotContain("synthetic-private-token");
        assertThat(jdbc.queryForObject("SELECT ai_reply FROM community_post", String.class)).isNull();
        upstream.verify();
    }

    @Test
    void realSlowProviderPreservesNewPostAndClearsReplyOnEdit() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        try (var peer = CommunityAiTimeoutTests.stalledPeer(new CompletableFuture<>(), requests, false);
             var transport = RestTemplateConfig.createClient(Duration.ofMillis(150), Duration.ofMillis(150))) {
            var http = new RestTemplate(new HttpComponentsClientHttpRequestFactory(transport));
            var generator = CommunityAiServiceTests.service(http);
            ReflectionTestUtils.setField(generator, "baseUrl", peer.url());
            ReflectionTestUtils.setField(posts, "aiReplyService", generator);

            assertThat(posts.add(add(), 1L)).isTrue();
            long id = jdbc.queryForObject("SELECT id FROM community_post", Long.class);
            assertThat(jdbc.queryForObject("SELECT ai_reply FROM community_post", String.class)).isNull();
            jdbc.update("UPDATE community_post SET ai_reply='合成历史回复' WHERE id=?", id);
            assertThat(posts.update(edit(id, true), 1L)).isTrue();
            assertThat(jdbc.queryForObject("SELECT ai_reply FROM community_post", String.class)).isNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_reply WHERE status=0 "
                    + "AND error_message LIKE 'provider_timeout%' AND prompt IS NULL AND reply_content IS NULL", Integer.class))
                    .isEqualTo(2);
            assertThat(requests).hasValue(2);
        }
    }

    @Test
    void logDatabaseFailureDoesNotTurnSavedPostIntoFailedPublication() {
        jdbc.execute("CREATE TRIGGER xinyu_test_fail_community_log BEFORE INSERT ON ai_reply "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic-private-db-error'");
        try {
            upstream.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
            assertThat(posts.add(add(), 1L)).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM community_post", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT ai_reply FROM community_post", String.class)).isNull();
        } finally {
            jdbc.execute("DROP TRIGGER xinyu_test_fail_community_log");
        }
    }

    @Test
    void newPostWithoutAiDoesNotCallProviderOrCreateLog() {
        var dto = add();
        dto.setNeedAiReply(false);
        assertThat(posts.add(dto, 1L)).isTrue();
        assertThat(jdbc.queryForObject("SELECT ai_reply FROM community_post", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT ai_generation_id FROM community_post", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_reply", Integer.class)).isZero();
        upstream.verify();
    }

    private long existingPost() {
        jdbc.update("INSERT INTO community_post(user_id,topic_id,mood_type,content,ai_reply,like_count) "
                + "VALUES (1,1,'平静','合成旧内容','合成旧回复',7)");
        return jdbc.queryForObject("SELECT id FROM community_post", Long.class);
    }

    private static ResponseCreator generatedReply(String content) {
        return withSuccess("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"%s\"}}]}"
                .formatted(content), MediaType.APPLICATION_JSON);
    }

    /** 仅在测试指定的一次帖子写入前安排编辑，不替换原SQL或结果；其余调用直接透传。 */
    @Intercepts(@Signature(type = org.apache.ibatis.executor.Executor.class, method = "update",
            args = {MappedStatement.class, Object.class}))
    public static class PostCounterInterleaving implements Interceptor {
        private Runnable beforeNextPostUpdate;

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            Runnable edit = beforeNextPostUpdate;
            if (edit != null && statement.getId().startsWith(CommunityPostMapper.class.getName() + ".")) {
                beforeNextPostUpdate = null;
                edit.run();
            }
            return invocation.proceed();
        }
    }

    private static CommunityPostAddDTO add() {
        var dto = new CommunityPostAddDTO();
        dto.setTopicId(1L);
        dto.setMoodType("平静");
        dto.setContent("合成新内容");
        return dto;
    }

    private static CommunityPostUpdateDTO edit(long id, boolean needAi) {
        var dto = new CommunityPostUpdateDTO();
        dto.setId(id);
        dto.setTopicId(1L);
        dto.setMoodType("平静");
        dto.setContent("合成新内容");
        dto.setNeedAiReply(needAi);
        return dto;
    }
}
