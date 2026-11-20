package com.xinyu.community;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.config.AuthWebConfig;
import com.xinyu.config.CorsConfig;
import com.xinyu.config.JacksonConfig;
import com.xinyu.controller.AdminReportController;
import com.xinyu.controller.CommunityPostController;
import com.xinyu.controller.CommunityReplyController;
import com.xinyu.controller.CommunityReportController;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.CommunityReply;
import com.xinyu.entity.User;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import com.xinyu.mapper.ContentReportMapper;
import com.xinyu.mapper.FavoriteMapper;
import com.xinyu.mapper.PostLikeMapper;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.service.AiReplyLogService;
import com.xinyu.service.CommunityAiClient;
import com.xinyu.service.CommunityPostService;
import com.xinyu.service.CommunityReplyService;
import com.xinyu.service.CommunityReportService;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import com.xinyu.vo.MyReportVO;
import com.xinyu.vo.ReportedTargetVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 4.4 / 4.5 HTTP 接口层鉴权、响应脱敏与作者可见被隐藏内容测试。
 */
@WebMvcTest(controllers = {
        CommunityReportController.class,
        AdminReportController.class,
        CommunityPostController.class,
        CommunityReplyController.class
})
@Import({
        JwtUtil.class,
        AuthWebConfig.class,
        CorsConfig.class,
        JacksonConfig.class,
        CommunityPostService.class,
        CommunityReplyService.class
})
class CommunityReportHttpTests {

    @MockitoBean com.xinyu.ratelimit.RateLimiter rateLimiter;

    private static final long AUTHOR_ID = 101L;
    private static final long OTHER_USER_ID = 202L;
    private static final long ADMIN_ID = 303L;
    private static final long POST_ID = 11L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private CommunityReportService communityReportService;

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
    private CommunityAiClient communityAiClient;

    @MockitoBean
    private AiReplyLogService aiReplyLogService;

    @MockitoBean
    private UserService userService;

    private String authorToken;
    private String otherToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        when(userService.getById(AUTHOR_ID)).thenReturn(user(AUTHOR_ID, "USER"));
        when(userService.getById(OTHER_USER_ID)).thenReturn(user(OTHER_USER_ID, "USER"));
        when(userService.getById(ADMIN_ID)).thenReturn(user(ADMIN_ID, "ADMIN"));
        when(userService.isAdmin(ADMIN_ID)).thenReturn(true);

        authorToken = jwtUtil.generateToken(AUTHOR_ID, "author");
        otherToken = jwtUtil.generateToken(OTHER_USER_ID, "other");
        adminToken = jwtUtil.generateToken(ADMIN_ID, "admin");
    }

    @Test
    void myReportsEndpointHidesNoteHandlerAndTargetUser() throws Exception {
        MyReportVO vo = new MyReportVO();
        vo.setId("1");
        vo.setTargetType("POST");
        vo.setTargetId("11");
        vo.setPostId("11");
        vo.setExcerpt("举报摘录");
        vo.setReason("PRIVACY");
        vo.setStatus("HIDDEN");
        vo.setHideReason("PRIVACY");
        vo.setCreateTime(LocalDateTime.now());
        Page<MyReportVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(vo));
        when(communityReportService.listMine(OTHER_USER_ID, 1L, 10L)).thenReturn(page);

        mvc.perform(get("/community/report/mine").header("token", otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].status").value("HIDDEN"))
                .andExpect(jsonPath("$.data.records[0].handleNote").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].handlerId").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].targetUserId").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].riskStatus").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.records[0].riskHitTime").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.records[0].riskReviewedTime").doesNotHaveJsonPath());

        when(communityReportService.listReportedTargets(OTHER_USER_ID, POST_ID))
                .thenReturn(List.of(new ReportedTargetVO("POST", String.valueOf(POST_ID), "PENDING")));
        mvc.perform(get("/community/report/reported-targets")
                        .param("postId", String.valueOf(POST_ID))
                        .header("token", otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].targetType").value("POST"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].riskStatus").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data[0].riskHitTime").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data[0].riskReviewedTime").doesNotHaveJsonPath());
    }

    @Test
    void hiddenPostAndReplyAreVisibleToAuthorWithHiddenFlagAndReason() throws Exception {
        CommunityPost hiddenPost = new CommunityPost();
        hiddenPost.setId(POST_ID);
        hiddenPost.setUserId(AUTHOR_ID);
        hiddenPost.setContent("作者可见被隐藏正文");
        hiddenPost.setStatus(2);
        hiddenPost.setHideReason("PRIVACY");
        hiddenPost.setRiskStatus("REVIEWED");
        hiddenPost.setRiskHitTime(LocalDateTime.now());
        hiddenPost.setRiskReviewedTime(LocalDateTime.now());
        when(communityPostMapper.selectById(POST_ID)).thenReturn(hiddenPost);
        when(postLikeMapper.selectCount(any())).thenReturn(0L);
        when(favoriteMapper.selectCount(any())).thenReturn(0L);

        mvc.perform(get("/community/post/detail").param("id", String.valueOf(POST_ID))
                        .header("token", authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.hidden").value(true))
                .andExpect(jsonPath("$.data.hideReason").value("PRIVACY"))
                .andExpect(jsonPath("$.data.mine").value(true))
                .andExpect(jsonPath("$.data.riskStatus").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.riskHitTime").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.riskReviewedTime").doesNotHaveJsonPath());

        mvc.perform(get("/community/post/detail").param("id", String.valueOf(POST_ID))
                        .header("token", otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));

        CommunityReply authorHiddenReply = new CommunityReply();
        authorHiddenReply.setId(21L);
        authorHiddenReply.setPostId(POST_ID);
        authorHiddenReply.setUserId(AUTHOR_ID);
        authorHiddenReply.setContent("被隐藏回复");
        authorHiddenReply.setStatus(2);
        authorHiddenReply.setHideReason("AD");
        authorHiddenReply.setRiskStatus("REVIEWED");
        authorHiddenReply.setRiskHitTime(LocalDateTime.now());
        authorHiddenReply.setRiskReviewedTime(LocalDateTime.now());
        when(communityReplyMapper.selectList(any())).thenReturn(List.of(authorHiddenReply));

        mvc.perform(get("/community/reply/list").param("postId", String.valueOf(POST_ID))
                        .header("token", authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].hidden").value(true))
                .andExpect(jsonPath("$.data[0].hideReason").value("AD"))
                .andExpect(jsonPath("$.data[0].riskStatus").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data[0].riskHitTime").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data[0].riskReviewedTime").doesNotHaveJsonPath());
    }

    @Test
    void reportSubmitAndAdminHandleValidateAuth() throws Exception {
        doNothing().when(communityReportService).submitReport(any(), any());
        mvc.perform(post("/community/report")
                        .header("token", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":11,\"reason\":\"AD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mvc.perform(post("/admin/report/handle")
                        .header("token", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":11,\"action\":\"DISMISS\"}"))
                .andExpect(status().isForbidden());

        when(communityReportService.handleReport(any(), any())).thenReturn("已标记不违规，相关举报已关闭");
        mvc.perform(post("/admin/report/handle")
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":11,\"action\":\"DISMISS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mvc.perform(post("/admin/report/handle")
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":\"9007199254740993\",\"action\":\"DISMISS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private static User user(Long id, String role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setStatus(1);
        return u;
    }
}
