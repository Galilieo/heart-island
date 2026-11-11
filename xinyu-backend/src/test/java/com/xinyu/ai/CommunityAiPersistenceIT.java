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
import com.xinyu.mapper.AiReplyMapper;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.service.AiReplyLogService;
import com.xinyu.service.CommunityPostService;
import com.xinyu.config.RestTemplateConfig;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
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

        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AiReplyMapper.class);
        configuration.addMapper(CommunityPostMapper.class);
        configuration.addMapper(TopicMapper.class);
        factory.setConfiguration(configuration);
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        factory.setPlugins(pagination);
        var sessions = new SqlSessionTemplate(factory.getObject());
        logs = new AiReplyLogService();
        ReflectionTestUtils.setField(logs, "aiReplyMapper", sessions.getMapper(AiReplyMapper.class));
        posts = new CommunityPostService();
        ReflectionTestUtils.setField(posts, "communityPostMapper", sessions.getMapper(CommunityPostMapper.class));
        ReflectionTestUtils.setField(posts, "topicMapper", sessions.getMapper(TopicMapper.class));
        ReflectionTestUtils.setField(posts, "aiReplyLogService", logs);
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
    void choosingNoAiClearsReplyWithoutProviderCallOrLog() {
        long id = existingPost();
        assertThat(posts.update(edit(id, false), 1L)).isTrue();
        assertThat(jdbc.queryForObject("SELECT ai_reply FROM community_post WHERE id=?", String.class, id)).isNull();
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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_reply", Integer.class)).isZero();
        upstream.verify();
    }

    private long existingPost() {
        jdbc.update("INSERT INTO community_post(user_id,topic_id,mood_type,content,ai_reply,like_count) "
                + "VALUES (1,1,'平静','合成旧内容','合成旧回复',7)");
        return jdbc.queryForObject("SELECT id FROM community_post", Long.class);
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
