package com.xinyu.community;

import com.xinyu.config.AuthWebConfig;
import com.xinyu.config.CorsConfig;
import com.xinyu.config.JacksonConfig;
import com.xinyu.controller.AdminPostController;
import com.xinyu.controller.AdminReplyController;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.CommunityReply;
import com.xinyu.entity.ContentReport;
import com.xinyu.entity.Topic;
import com.xinyu.entity.User;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import com.xinyu.mapper.ContentReportMapper;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminPostController.class, AdminReplyController.class})
@Import({
        JwtUtil.class,
        AuthWebConfig.class,
        CorsConfig.class,
        JacksonConfig.class,
        CommunityPostService.class,
        CommunityReplyService.class
})
class AdminModerationHttpTests {

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
    private ContentReportMapper contentReportMapper;

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

    private String adminToken;

    @BeforeEach
    void setUp() {
        User admin = new User();
        admin.setId(ADMIN_ID);
        admin.setRole("ADMIN");
        admin.setStatus(1);
        when(userService.getById(ADMIN_ID)).thenReturn(admin);
        when(userService.isAdmin(ADMIN_ID)).thenReturn(true);
        adminToken = jwtUtil.generateToken(ADMIN_ID, "admin");
    }

    @Test
    void adminPostRejectsStatusZeroAndMissingHideReason() throws Exception {
        mvc.perform(put("/admin/post/{id}/status", POST_ID)
                        .param("status", "0")
                        .header("token", adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mvc.perform(put("/admin/post/{id}/status", POST_ID)
                        .param("status", "2")
                        .header("token", adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mvc.perform(delete("/admin/post/{id}", POST_ID).header("token", adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void adminReplyRejectsStatusZeroAndInvalidOrMissingHideReason() throws Exception {
        mvc.perform(put("/admin/reply/{id}/status", REPLY_ID)
                        .param("status", "0")
                        .header("token", adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mvc.perform(put("/admin/reply/{id}/status", REPLY_ID)
                        .param("status", "2")
                        .param("hideReason", "SAFETY")
                        .header("token", adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mvc.perform(delete("/admin/reply/{id}", REPLY_ID).header("token", adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void adminPostAndReplyDetailExposeReportSummary() throws Exception {
        CommunityPost post = new CommunityPost();
        post.setId(POST_ID);
        post.setUserId(101L);
        post.setTopicId(1L);
        post.setMoodType("焦虑");
        post.setContent("测试帖子全文");
        post.setAiReply("小芽回复内容");
        post.setStatus(1);
        post.setLikeCount(4);
        post.setReplyCount(3);
        when(communityPostMapper.selectById(POST_ID)).thenReturn(post);

        Topic topic = new Topic();
        topic.setId(1L);
        topic.setName("情绪低落");
        when(topicMapper.selectById(1L)).thenReturn(topic);

        CommunityReply reply = new CommunityReply();
        reply.setId(REPLY_ID);
        reply.setPostId(POST_ID);
        reply.setUserId(102L);
        reply.setContent("测试回复全文");
        reply.setStatus(1);
        when(communityReplyMapper.selectById(REPLY_ID)).thenReturn(reply);

        ContentReport pending = new ContentReport();
        pending.setReason("SAFETY");
        when(contentReportMapper.selectList(any())).thenReturn(List.of(pending));

        mvc.perform(get("/admin/post/{id}", POST_ID).header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.aiReply").value("小芽回复内容"))
                .andExpect(jsonPath("$.data.topicName").value("情绪低落"))
                .andExpect(jsonPath("$.data.pendingReportCount").value(1))
                .andExpect(jsonPath("$.data.pendingReasons[0]").value("SAFETY"));

        mvc.perform(get("/admin/reply/{id}", REPLY_ID).header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.postContent").value("测试帖子全文"))
                .andExpect(jsonPath("$.data.pendingReportCount").value(1));
    }

    @Test
    void adminPostAndReplyConditionalStatusChangeSucceed() throws Exception {
        when(communityPostMapper.update(any(), any())).thenReturn(1);
        mvc.perform(put("/admin/post/{id}/status", POST_ID)
                        .param("status", "2")
                        .param("hideReason", "AD")
                        .header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        when(communityReplyMapper.update(any(), any())).thenReturn(1);
        mvc.perform(delete("/admin/reply/{id}", REPLY_ID)
                        .param("hideReason", "PRIVACY")
                        .header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
