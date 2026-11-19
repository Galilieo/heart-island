package com.xinyu.community;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.config.AuthWebConfig;
import com.xinyu.config.CorsConfig;
import com.xinyu.config.JacksonConfig;
import com.xinyu.controller.AdminPostController;
import com.xinyu.controller.AdminReplyController;
import com.xinyu.controller.CommunityPostController;
import com.xinyu.controller.CommunityReplyController;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.CommunityReply;
import com.xinyu.entity.Favorite;
import com.xinyu.entity.PostLike;
import com.xinyu.entity.Topic;
import com.xinyu.entity.User;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import com.xinyu.mapper.FavoriteMapper;
import com.xinyu.mapper.PostLikeMapper;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.service.AiReplyLogService;
import com.xinyu.service.CommunityAiClient;
import com.xinyu.service.CommunityPostService;
import com.xinyu.service.CommunityReplyService;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        CommunityPostController.class,
        CommunityReplyController.class,
        AdminPostController.class,
        AdminReplyController.class
})
@Import({
        JwtUtil.class,
        AuthWebConfig.class,
        CorsConfig.class,
        JacksonConfig.class,
        CommunityPostService.class,
        CommunityReplyService.class
})
class CommunityAnonymityHttpTests {

    @MockitoBean com.xinyu.ratelimit.RateLimiter rateLimiter;

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
    private CommunityAiClient communityAiClient;

    @MockitoBean
    private AiReplyLogService aiReplyLogService;

    @MockitoBean
    private UserService userService;

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
                .andExpect(jsonPath("$.data.records[0].userId").value(String.valueOf(AUTHOR_ID)));

        mvc.perform(get("/admin/reply/page").header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].userId").value(String.valueOf(AUTHOR_ID)));
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
        post.setUserId(AUTHOR_ID);
        post.setTopicId(1L);
        post.setMoodType("焦虑");
        post.setContent("匿名发帖合成内容");
        post.setAnonymousName("匿名小鹿");
        post.setReplyCount(1);
        post.setLikeCount(0);
        post.setStatus(1);
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
