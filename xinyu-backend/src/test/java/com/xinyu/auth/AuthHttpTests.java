package com.xinyu.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.xinyu.config.AuthWebConfig;
import com.xinyu.config.CorsConfig;
import com.xinyu.config.JacksonConfig;
import com.xinyu.vo.AiJobVO;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.xinyu.entity.User;
import com.xinyu.mapper.*;
import com.xinyu.service.*;
import com.xinyu.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// MVC、路由和 JWT 使用真实实现；只替换数据库业务依赖，不启动 MySQL/AI。
@WebMvcTest
@Import({JwtUtil.class, AuthWebConfig.class, CorsConfig.class, JacksonConfig.class})
@MockitoBean(types = {MoodRecordService.class, AiJobService.class, CommunityPostService.class,
        CommunityReplyService.class, TopicService.class, AiReplyLogService.class,
        UserMapper.class, CommunityPostMapper.class, CommunityReplyMapper.class, AiReplyMapper.class})
class AuthHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @Autowired AiJobService jobs;
    @Autowired MoodRecordService moods;
    @MockitoBean UserService userService;

    private String memberToken;
    private String adminToken;

    @BeforeEach
    void users() {
        User member = user(11L, "USER", 1);
        User admin = user(22L, "ADMIN", 1);
        when(userService.getById(11L)).thenReturn(member);
        when(userService.getById(22L)).thenReturn(admin);
        when(userService.isAdmin(22L)).thenReturn(true);
        memberToken = jwtUtil.generateToken(11L, "member");
        adminToken = jwtUtil.generateToken(22L, "admin");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/mood/list", "/mood/trend/recent7", "/user/profile-stats",
            "/user/test/11", "/community/post/favorites", "/admin/user/page",
            "/admin/post/page", "/admin/reply/page", "/admin/topic/list",
            "/admin/ai-replies/page", "/admin/overview"})
    void missingTokenReturns401(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-a-jwt"})
    void invalidTokenReturns401(String token) throws Exception {
        mvc.perform(get("/mood/list").header("token", token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        String secret = (String) ReflectionTestUtils.getField(JwtUtil.class, "SECRET");
        String expired = JWT.create().withClaim("userId", 11L)
                .withExpiresAt(new Date(1)).sign(Algorithm.HMAC256(secret));
        mvc.perform(get("/mood/list").header("token", expired))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void signedTokenWithoutUserIdReturns401() throws Exception {
        mvc.perform(get("/mood/list").header("token", jwtUtil.generateToken(null, "missing-id")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletedUserReturns401() throws Exception {
        mvc.perform(get("/mood/list").header("token", jwtUtil.generateToken(99L, "deleted")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledUserReturns403() throws Exception {
        when(userService.getById(11L)).thenReturn(user(11L, "USER", 0));
        mvc.perform(get("/mood/list").header("token", memberToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/user/page", "/admin/post/page", "/admin/reply/page",
            "/admin/topic/list", "/admin/ai-replies/page", "/admin/overview"})
    void memberCannotAccessAdmin(String path) throws Exception {
        mvc.perform(get(path).header("token", memberToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void validUsersKeepAccess() throws Exception {
        mvc.perform(get("/mood/list").header("token", memberToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/admin/user/page").header("token", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void communityMineNeedsLoginButPublicReadsStayPublic() throws Exception {
        mvc.perform(get("/community/post/list").param("mine", "true"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/community/post/list"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/community/reply/list").param("postId", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/topic/list")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "on", "yes", " true "})
    void allSupportedMineTrueValuesRequireLogin(String mine) throws Exception {
        mvc.perform(get("/community/post/list").param("mine", mine))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "0", "off", "no", ""})
    void falseMineValuesKeepAnonymousReads(String mine) throws Exception {
        mvc.perform(get("/community/post/list").param("mine", mine))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void contextPathAndCurrentDatabaseRoleStillControlAdminAccess() throws Exception {
        when(userService.getById(22L)).thenReturn(user(22L, "USER", 1));
        mvc.perform(get("/api/admin/user/page").contextPath("/api").header("token", adminToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void incorrectSignatureReturns401() throws Exception {
        String forged = JWT.create().withClaim("userId", 22L)
                .sign(Algorithm.HMAC256("fixture-untrusted-signing-key"));
        mvc.perform(get("/admin/user/page").header("token", forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void writesNeedLoginBeforeBodyValidation() throws Exception {
        for (String path : new String[]{"/mood/add", "/community/post/add", "/community/reply/add",
                "/user/update-profile", "/user/update-password", "/admin/topic"}) {
            mvc.perform(post(path).contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        }
    }

    @Test
    void loginRegisterAndCorsRemainAvailable() throws Exception {
        mvc.perform(post("/user/login").contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("用户名不能为空"));
        mvc.perform(post("/user/register").contentType("application/json").content("{}"))
                .andExpect(status().isOk());
        mvc.perform(options("/mood/list").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void jobRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/mood/ai-job/10")).andExpect(status().isUnauthorized());
        mvc.perform(post("/mood/ai-job/10/retry")).andExpect(status().isUnauthorized());
    }

    @Test
    void jobReadAndRetryUseJwtIdentityAndSerializeLongsAsStrings() throws Exception {
        AiJobVO view = new AiJobVO(10L, 2054153819121537036L, 7L, "queued", 0, null, null, null, false, 0);
        when(jobs.getOwned(11, 10)).thenReturn(view);
        when(jobs.retry(11, 10)).thenReturn(view);
        mvc.perform(get("/mood/ai-job/10").header("token", memberToken).param("userId", "22"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("10"))
                .andExpect(jsonPath("$.data.targetId").value("2054153819121537036"))
                .andExpect(jsonPath("$.data.targetVersion").value("7"));
        mvc.perform(post("/mood/ai-job/10/retry").header("token", memberToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("queued"));
    }

    @Test
    void coolingRetryPreservesRetryAfterAndNormalErrorBody() throws Exception {
        ResponseStatusException cooling = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "回应任务冷却中") {
            @Override
            public org.springframework.http.HttpHeaders getHeaders() {
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.set("Retry-After", "120");
                return headers;
            }
        };
        when(jobs.retry(11, 10)).thenThrow(cooling);

        mvc.perform(post("/mood/ai-job/10/retry").header("token", memberToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "120"))
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void jobErrorsKeepHttpStatusAndHideResult() throws Exception {
        when(jobs.getOwned(11, 10)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该任务"));
        when(jobs.retry(11, 10)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "心情记录不存在"));
        mvc.perform(get("/mood/ai-job/10").header("token", memberToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(post("/mood/ai-job/10/retry").header("token", memberToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void moodListAndDetailExposeVersionedViewsWithCompatibleFields() throws Exception {
        var view = new com.xinyu.vo.MoodRecordVO(9L, 11L, "平静", "合成内容", null, 1, null, null,
                7L, true, "running", 10L, null);
        when(moods.getDetailView(9L, 11L)).thenReturn(view);
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.xinyu.vo.MoodRecordVO>(1, 5, 1);
        page.setRecords(java.util.List.of(view));
        when(moods.listViews(11L, null, null, null, 1L, 5L)).thenReturn(page);
        mvc.perform(get("/mood/detail/9").header("token", memberToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("9"))
                .andExpect(jsonPath("$.data.contentVersion").value("7"))
                .andExpect(jsonPath("$.data.content").value("合成内容"))
                .andExpect(jsonPath("$.data.aiReplyState").value("running"))
                .andExpect(jsonPath("$.data.deletedAt").doesNotExist());
        mvc.perform(get("/mood/list").header("token", memberToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value("1"))
                .andExpect(jsonPath("$.data.records[0].jobId").value("10"))
                .andExpect(jsonPath("$.data.records[0].moodType").value("平静"));
    }

    private static User user(Long id, String role, int status) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
