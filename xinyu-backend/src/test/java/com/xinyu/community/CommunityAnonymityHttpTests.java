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
import com.xinyu.entity.Topic;
import com.xinyu.entity.User;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import com.xinyu.mapper.FavoriteMapper;
import com.xinyu.mapper.PostLikeMapper;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.service.AiReplyLogService;
import com.xinyu.service.AiReplyService;
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

import static org.mockito.ArgumentMatchers.any;
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
    private AiReplyService aiReplyService;

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
        when(communityPostMapper.selectPage(any(), any())).thenReturn(pageOf(post));

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
    void anotherUserCannotSeeUserIdAndReceivesMineFalse() throws Exception {
        mvc.perform(get("/community/post/list").header("token", otherUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].mine").value(false));

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
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].mine").value(false));

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
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].mine").value(true));

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
