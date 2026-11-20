package com.xinyu.community;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.config.AuthWebConfig;
import com.xinyu.config.CorsConfig;
import com.xinyu.config.JacksonConfig;
import com.xinyu.controller.AdminOverviewController;
import com.xinyu.controller.AdminPostController;
import com.xinyu.controller.AdminReplyController;
import com.xinyu.controller.CommunityPostController;
import com.xinyu.controller.CommunityReplyController;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.CommunityReply;
import com.xinyu.entity.Favorite;
import com.xinyu.entity.PostLike;
import com.xinyu.entity.Topic;
import com.xinyu.entity.User;
import com.xinyu.mapper.AiReplyMapper;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import com.xinyu.mapper.FavoriteMapper;
import com.xinyu.mapper.PostLikeMapper;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.mapper.UserMapper;
import com.xinyu.ratelimit.FallbackRateLimiter;
import com.xinyu.ratelimit.RateLimitMetrics;
import com.xinyu.service.AiReplyLogService;
import com.xinyu.service.AiJobAdmission;
import com.xinyu.service.AiJobService;
import com.xinyu.service.CommunityPostService;
import com.xinyu.service.CommunityReplySource;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.service.CommunityReplyService;
import com.xinyu.service.MoodReviewSafety;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import java.util.function.Supplier;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        CommunityPostController.class,
        CommunityReplyController.class,
        AdminPostController.class,
        AdminReplyController.class,
        AdminOverviewController.class
})
@Import({
        JwtUtil.class,
        AuthWebConfig.class,
        CorsConfig.class,
        JacksonConfig.class,
        CommunityPostService.class,
        CommunityReplySource.class,
        CommunityReplyService.class
})
class CommunityAnonymityHttpTests {


    private static final long AUTHOR_ID = 101L;
    private static final long OTHER_USER_ID = 202L;
    private static final long ADMIN_ID = 303L;
    private static final long POST_ID = 11L;
    private static final long REPLY_ID = 21L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private CommunityPostMapper communityPostMapper;

    @MockitoBean
    private CommunityReplyMapper communityReplyMapper;

    @MockitoBean
    private PostLikeMapper postLikeMapper;

    @MockitoBean
    private FavoriteMapper favoriteMapper;

    @MockitoBean
    private TopicMapper topicMapper;

    @MockitoBean
    private AiJobService aiJobService;

    @MockitoBean
    private AiJobAdmission admission;

    @MockitoBean
    private AiJobMapper aiJobMapper;

    @MockitoBean
    private AiReplyLogService aiReplyLogService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private AiReplyMapper aiReplyMapper;

    @MockitoBean
    private FallbackRateLimiter fallbackRateLimiter;

    @MockitoBean
    private RateLimitMetrics rateLimitMetrics;

    private String authorToken;
    private String otherUserToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        when(userService.getById(AUTHOR_ID)).thenReturn(user(AUTHOR_ID, "USER"));
        when(userService.getById(OTHER_USER_ID)).thenReturn(user(OTHER_USER_ID, "USER"));
        when(userService.getById(ADMIN_ID)).thenReturn(user(ADMIN_ID, "ADMIN"));
        when(userService.isAdmin(ADMIN_ID)).thenReturn(true);

        authorToken = jwtUtil.generateToken(AUTHOR_ID, "author");
        otherUserToken = jwtUtil.generateToken(OTHER_USER_ID, "other");
        adminToken = jwtUtil.generateToken(ADMIN_ID, "admin");

        CommunityPost post = samplePost();
        CommunityReply reply = sampleReply();
        Favorite favorite = new Favorite();
        favorite.setId(1L);
        favorite.setPostId(POST_ID);
        favorite.setUserId(OTHER_USER_ID);

        when(communityPostMapper.selectList(any())).thenReturn(List.of(post));
        when(communityPostMapper.selectById(POST_ID)).thenReturn(post);
        when(communityPostMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<CommunityPost> page = invocation.getArgument(0);
            page.setRecords(List.of(post));
            page.setTotal(1);
            return page;
        });

        when(communityReplyMapper.selectList(any())).thenReturn(List.of(reply));
        when(communityReplyMapper.selectPage(any(), any())).thenReturn(pageOf(reply));

        when(favoriteMapper.selectList(any())).thenReturn(List.of(favorite));
        when(postLikeMapper.selectCount(any())).thenReturn(0L);
        when(favoriteMapper.selectCount(any())).thenReturn(0L);

        Topic topic = new Topic();
        topic.setId(1L);
        topic.setName("情绪树洞");
        when(topicMapper.selectList(any())).thenReturn(List.of(topic));
    }

    @Test
    void publicPostListReturnsDefaultPage() throws Exception {
        mvc.perform(get("/community/post/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current").value("1"))
                .andExpect(jsonPath("$.data.size").value("10"))
                .andExpect(jsonPath("$.data.total").value("1"))
                .andExpect(jsonPath("$.data.records.length()").value(1));
    }

    @Test
    void publicPostListRejectsPageNumberBelowOne() throws Exception {
        mvc.perform(get("/community/post/list").param("pageNum", "0"))
                .andExpect(jsonPath("$.message").value("页码不能小于1"));
    }

    @Test
    void publicPostListRejectsPageSizeBelowOne() throws Exception {
        mvc.perform(get("/community/post/list").param("pageSize", "0"))
                .andExpect(jsonPath("$.message").value("每页条数不能小于1"));
    }

    @Test
    void anonymousPostListCapsPageSizeWithoutReadingViewerRelations() throws Exception {
        mvc.perform(get("/community/post/list").param("pageSize", "51"))
                .andExpect(jsonPath("$.data.size").value("50"))
                .andExpect(jsonPath("$.data.records[0].liked").value(false))
                .andExpect(jsonPath("$.data.records[0].favorited").value(false));

        verifyNoInteractions(postLikeMapper, favoriteMapper);
    }

    @Test
    void viewerRelationsUseOneBatchEachForOneOrThirtyPosts() throws Exception {
        PostLike liked = new PostLike();
        liked.setPostId(POST_ID);
        when(postLikeMapper.selectList(any())).thenReturn(List.of(liked));

        Favorite saved = new Favorite();
        saved.setPostId(POST_ID + 1);
        when(favoriteMapper.selectList(any())).thenReturn(List.of(saved));

        for (int recordCount : List.of(1, 30)) {
            List<CommunityPost> records = LongStream.range(POST_ID, POST_ID + recordCount)
                    .mapToObj(id -> {
                        CommunityPost post = samplePost();
                        post.setId(id);
                        return post;
                    })
                    .toList();
            doAnswer(invocation -> {
                Page<CommunityPost> page = invocation.getArgument(0);
                page.setRecords(records);
                page.setTotal(40);
                return page;
            }).when(communityPostMapper).selectPage(any(), any());

            var response = mvc.perform(get("/community/post/list")
                            .param("pageSize", "30").header("token", authorToken))
                    .andExpect(jsonPath("$.data.total").value("40"))
                    .andExpect(jsonPath("$.data.records.length()").value(recordCount))
                    .andExpect(jsonPath("$.data.records[0].mine").value(true))
                    .andExpect(jsonPath("$.data.records[0].liked").value(true))
                    .andExpect(jsonPath("$.data.records[0].favorited").value(false));

            if (recordCount == 30) {
                response.andExpect(jsonPath("$.data.records[1].liked").value(false))
                        .andExpect(jsonPath("$.data.records[1].favorited").value(true))
                        .andExpect(jsonPath("$.data.records[2].liked").value(false))
                        .andExpect(jsonPath("$.data.records[2].favorited").value(false));
            }
            verify(postLikeMapper).selectList(any());
            verify(favoriteMapper).selectList(any());
            verifyNoMoreInteractions(postLikeMapper, favoriteMapper);
            clearInvocations(postLikeMapper, favoriteMapper);
        }
    }

    @Test
    void favoritesKeepFavoriteOrderSkipInvisiblePostsAndBatchLikes() throws Exception {
        CommunityPost liked = samplePost();
        CommunityPost newestFavorite = samplePost();
        newestFavorite.setId(13L);
        CommunityPost hidden = samplePost();
        hidden.setId(12L);
        hidden.setStatus(2);
        when(communityPostMapper.selectList(any())).thenReturn(List.of(liked, hidden, newestFavorite));

        List<Favorite> favorites = List.of(13L, 12L, 14L, POST_ID).stream().map(id -> {
            Favorite favorite = new Favorite();
            favorite.setPostId(id);
            return favorite;
        }).toList();
        when(favoriteMapper.selectList(any())).thenReturn(favorites);

        PostLike knownLike = new PostLike();
        knownLike.setPostId(POST_ID);
        when(postLikeMapper.selectList(any())).thenReturn(List.of(knownLike));

        mvc.perform(get("/community/post/favorites").header("token", otherUserToken))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("13"))
                .andExpect(jsonPath("$.data[0].liked").value(false))
                .andExpect(jsonPath("$.data[0].favorited").value(true))
                .andExpect(jsonPath("$.data[1].id").value("11"))
                .andExpect(jsonPath("$.data[1].liked").value(true));
        verify(postLikeMapper).selectList(any());
        verify(favoriteMapper).selectList(any());
        verify(communityPostMapper).selectList(any());
        verifyNoMoreInteractions(postLikeMapper, favoriteMapper, communityPostMapper);
    }

    @Test
    void anotherUserCannotSeeUserIdAndReceivesMineFalse() throws Exception {
        mvc.perform(get("/community/post/list").header("token", otherUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].mine").value(false));

        mvc.perform(get("/community/post/detail").param("id", String.valueOf(POST_ID))
                        .header("token", otherUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.mine").value(false));

        mvc.perform(get("/community/post/favorites").header("token", otherUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].mine").value(false));

        mvc.perform(get("/community/reply/list").param("postId", String.valueOf(POST_ID))
                        .header("token", otherUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].mine").value(false));
    }

    @Test
    void anonymousVisitorCannotSeeUserIdAndReceivesMineFalse() throws Exception {
        mvc.perform(get("/community/post/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].mine").value(false));

        mvc.perform(get("/community/post/detail").param("id", String.valueOf(POST_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.mine").value(false));

        mvc.perform(get("/community/reply/list").param("postId", String.valueOf(POST_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].mine").value(false));
    }

    @Test
    void authorSeesMineTrueWithoutUserId() throws Exception {
        mvc.perform(get("/community/post/list").header("token", authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].mine").value(true));

        mvc.perform(get("/community/post/detail").param("id", String.valueOf(POST_ID))
                        .header("token", authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.mine").value(true));

        mvc.perform(get("/community/post/favorites").header("token", authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].mine").value(true));

        mvc.perform(get("/community/reply/list").param("postId", String.valueOf(POST_ID))
                        .header("token", authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].mine").value(true));
    }

    @Test
    void adminEndpointsStillReturnUserId() throws Exception {
        mvc.perform(get("/admin/post/page").header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].userId").value(String.valueOf(AUTHOR_ID)))
                .andExpect(jsonPath("$.data.records[0].riskStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.records[0].riskHitTime").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].riskReviewedTime").isNotEmpty());

        mvc.perform(get("/admin/reply/page").header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].userId").value(String.valueOf(AUTHOR_ID)))
                .andExpect(jsonPath("$.data.records[0].riskStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.records[0].riskHitTime").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].riskReviewedTime").isNotEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "add,true,我不想活了,true",
            "add,false,我不想活了,true",
            "add,false,今天很平静,false",
            "update,true,我不想活了,true",
            "update,false,我不想活了,true",
            "update,false,今天很平静,false"
    })
    void postWritesReturnPrivateSupportText(String operation, boolean needAiReply, String content, boolean hit)
            throws Exception {
        Topic topic = new Topic();
        topic.setStatus(1);
        topic.setName("合成话题");
        when(topicMapper.selectById(1L)).thenReturn(topic);
        when(communityPostMapper.insert(any(CommunityPost.class))).thenAnswer(invocation -> {
            CommunityPost saved = invocation.getArgument(0);
            saved.setId(POST_ID);
            return 1;
        });
        when(communityPostMapper.update(any(), any())).thenReturn(1);
        when(admission.save(any())).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        when(communityPostMapper.selectByIdForUpdate(POST_ID)).thenReturn(samplePost());
        AiJob queued = new AiJob();
        queued.setId(44L);
        queued.setStatus("queued");
        when(aiJobService.createCommunityJob(anyLong(), anyLong(), anyLong(), any())).thenReturn(queued);

        String body = """
                {"id":11,"topicId":1,"moodType":"低落","content":"  %s  ","needAiReply":%s}
                """.formatted(content, needAiReply);
        mvc.perform(post("/community/post/" + operation).header("token", authorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.postId").value(String.valueOf(POST_ID)))
                .andExpect(jsonPath("$.data.aiReplyState").value(needAiReply ? "queued" : "disabled"))
                .andExpect(needAiReply ? jsonPath("$.data.jobId").value("44") : jsonPath("$.data.jobId").value(nullValue()))
                .andExpect(jsonPath("$.data.riskStatus").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.riskHitTime").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.riskReviewedTime").doesNotHaveJsonPath())
                .andExpect(hit ? jsonPath("$.data.supportText").value(MoodReviewSafety.SUPPORT_TEXT)
                        : jsonPath("$.data.supportText").value(nullValue()));
    }

    @ParameterizedTest
    @CsvSource({"add,我不想活了,true", "add,今天很平静,false",
            "update,我不想活了,true", "update,今天很平静,false"})
    void replyWritesReturnPrivateSupportText(String operation, String content, boolean hit) throws Exception {
        when(communityReplyMapper.selectById(REPLY_ID)).thenReturn(sampleReply());
        when(communityReplyMapper.insert(any(CommunityReply.class))).thenReturn(1);
        when(communityReplyMapper.update(any(), any())).thenReturn(1);

        String body = """
                {"id":21,"postId":11,"content":"  %s  "}
                """.formatted(content);
        mvc.perform(post("/community/reply/" + operation).header("token", authorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.riskStatus").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.riskHitTime").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.riskReviewedTime").doesNotHaveJsonPath())
                .andExpect(hit ? jsonPath("$.data.supportText").value(MoodReviewSafety.SUPPORT_TEXT)
                        : jsonPath("$.data.supportText").value(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"add", "update"})
    void replyStillRejectsMoreThan500Characters(String operation) throws Exception {
        String body = """
                {"id":21,"postId":11,"content":"%s"}
                """.formatted("我不想活了" + "字".repeat(501));
        mvc.perform(post("/community/reply/" + operation).header("token", authorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.message").value("回复不能超过500个字"))
                .andExpect(jsonPath("$.data.supportText").doesNotHaveJsonPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {"post", "reply"})
    void riskReviewRequiresActiveAdminAndPendingContent(String target) throws Exception {
        long id = "post".equals(target) ? POST_ID : REPLY_ID;
        when(communityReplyMapper.selectById(REPLY_ID)).thenReturn(sampleReply());
        when(communityPostMapper.update(any(), any())).thenReturn(1, 0, 0);
        when(communityReplyMapper.update(any(), any())).thenReturn(1, 0, 0);
        User disabledAdmin = user(404L, "ADMIN");
        disabledAdmin.setStatus(0);
        when(userService.getById(404L)).thenReturn(disabledAdmin);
        String disabledToken = jwtUtil.generateToken(404L, "disabled-admin");

        String path = "/admin/" + target + "/{id}/risk-review";
        mvc.perform(put(path, id).header("token", authorToken)).andExpect(status().isForbidden());
        mvc.perform(put(path, id).header("token", disabledToken)).andExpect(status().isForbidden());
        mvc.perform(put(path, id).header("token", adminToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(put(path, id).header("token", adminToken)).andExpect(status().isConflict());
        mvc.perform(put(path, 999L).header("token", adminToken)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"post", "reply"})
    void adminRejectsInvalidRiskStatus(String target) throws Exception {
        mvc.perform(get("/admin/" + target + "/page").param("riskStatus", "INVALID").header("token", adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overviewCountsPendingPostsAndRepliesWithoutVisibilityFilter() throws Exception {
        when(communityPostMapper.selectCount(any())).thenAnswer(invocation -> {
            return isPendingRiskCount(invocation.getArgument(0)) ? 2L : 0L;
        });
        when(communityReplyMapper.selectCount(any())).thenAnswer(invocation -> {
            return isPendingRiskCount(invocation.getArgument(0)) ? 3L : 0L;
        });

        mvc.perform(get("/admin/overview").header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskPendingPosts").value("2"))
                .andExpect(jsonPath("$.data.riskPendingReplies").value("3"));
    }

    private static boolean isPendingRiskCount(QueryWrapper<?> query) {
        if (query == null) {
            return false;
        }

        String sql = query.getCustomSqlSegment();
        return sql.startsWith("WHERE (risk_status = ") && sql.endsWith("})")
                && Set.copyOf(query.getParamNameValuePairs().values()).equals(Set.of("PENDING"));
    }

    @Test
    void riskFieldsStayPrivateOnEveryCommunityContentRead() throws Exception {
        for (String token : List.of(authorToken, otherUserToken, "")) {
            assertNoRiskFields(mvc.perform(get("/community/post/list").header("token", token)),
                    "$.data.records[0]");
            assertNoRiskFields(mvc.perform(get("/community/post/detail").param("id", "11").header("token", token)),
                    "$.data");
            assertNoRiskFields(mvc.perform(get("/community/reply/list").param("postId", "11").header("token", token)),
                    "$.data[0]");
        }
        assertNoRiskFields(mvc.perform(get("/community/post/list").param("mine", "true").header("token", authorToken)),
                "$.data.records[0]");
        assertNoRiskFields(mvc.perform(get("/community/post/favorites").header("token", authorToken)), "$.data[0]");
    }

    private static void assertNoRiskFields(ResultActions response, String path) throws Exception {
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath(path + ".id").exists())
                .andExpect(jsonPath(path + ".riskStatus").doesNotHaveJsonPath())
                .andExpect(jsonPath(path + ".riskHitTime").doesNotHaveJsonPath())
                .andExpect(jsonPath(path + ".riskReviewedTime").doesNotHaveJsonPath());
    }

    @Test
    void taskFieldsAppearOnlyOnAuthorListAndDetail() throws Exception {
        AiJob job = new AiJob();
        job.setId(81L);
        job.setTargetId(POST_ID);
        job.setStatus("running");
        when(aiJobMapper.selectList(any())).thenReturn(List.of(job));

        for (String path : List.of("/community/post/list", "/community/post/detail")) {
            String data = path.endsWith("list") ? "$.data.records[0]" : "$.data";
            mvc.perform(get(path).param("id", String.valueOf(POST_ID)).header("token", authorToken))
                    .andExpect(jsonPath(data + ".jobId").value("81"))
                    .andExpect(jsonPath(data + ".aiReplyState").value("running"))
                    .andExpect(jsonPath(data + ".contentVersion").doesNotHaveJsonPath());
            mvc.perform(get(path).param("id", String.valueOf(POST_ID)).header("token", otherUserToken))
                    .andExpect(jsonPath(data + ".jobId").doesNotHaveJsonPath())
                    .andExpect(jsonPath(data + ".aiReplyState").doesNotHaveJsonPath());
            mvc.perform(get(path).param("id", String.valueOf(POST_ID)))
                    .andExpect(jsonPath(data + ".jobId").doesNotHaveJsonPath())
                    .andExpect(jsonPath(data + ".aiReplyState").doesNotHaveJsonPath());
        }
    }

    private static User user(Long id, String role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setStatus(1);
        return u;
    }

    private static CommunityPost samplePost() {
        CommunityPost post = new CommunityPost();
        post.setId(POST_ID);
        post.setContentVersion(1L);
        post.setUserId(AUTHOR_ID);
        post.setTopicId(1L);
        post.setMoodType("焦虑");
        post.setContent("匿名发帖合成内容");
        post.setAnonymousName("匿名小鹿");
        post.setReplyCount(1);
        post.setLikeCount(0);
        post.setStatus(1);
        post.setRiskStatus("PENDING");
        post.setRiskHitTime(LocalDateTime.now());
        post.setRiskReviewedTime(LocalDateTime.now());
        post.setCreateTime(LocalDateTime.now());
        return post;
    }

    private static CommunityReply sampleReply() {
        CommunityReply reply = new CommunityReply();
        reply.setId(REPLY_ID);
        reply.setPostId(POST_ID);
        reply.setUserId(AUTHOR_ID);
        reply.setContent("匿名回复合成内容");
        reply.setAnonymousName("匿名微风");
        reply.setStatus(1);
        reply.setRiskStatus("PENDING");
        reply.setRiskHitTime(LocalDateTime.now());
        reply.setRiskReviewedTime(LocalDateTime.now());
        reply.setCreateTime(LocalDateTime.now());
        return reply;
    }

    private static <T> Page<T> pageOf(T record) {
        Page<T> page = new Page<>(1, 10);
        page.setRecords(List.of(record));
        page.setTotal(1);
        return page;
    }
}
