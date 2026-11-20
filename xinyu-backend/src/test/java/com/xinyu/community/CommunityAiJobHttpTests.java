package com.xinyu.community;

import com.xinyu.config.AuthWebConfig;
import com.xinyu.config.JacksonConfig;
import com.xinyu.controller.AiJobController;
import com.xinyu.controller.CommunityAiJobController;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.User;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.mapper.AiReplyMapper;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.ratelimit.RateLimitScope;
import com.xinyu.ratelimit.RateLimitType;
import com.xinyu.ratelimit.RateLimiter;
import com.xinyu.service.AiJobAdmission;
import com.xinyu.service.AiJobService;
import com.xinyu.service.CommunityReplySource;
import com.xinyu.service.MoodReviewService;
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
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AiJobController.class, CommunityAiJobController.class})
@Import({AuthWebConfig.class, JacksonConfig.class, JwtUtil.class, AiJobService.class, CommunityReplySource.class})
class CommunityAiJobHttpTests {
    private static final long AUTHOR_ID = 21L;
    private static final long OTHER_ID = 22L;
    private static final long POST_ID = 50L;
    private static final long JOB_ID = 80L;
    private static final String ROUTE = "/community/ai-job/" + JOB_ID;

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwt;
    @MockitoBean private UserService users;
    @MockitoBean private RateLimiter limiter;
    @MockitoBean private AiJobAdmission admission;
    @MockitoBean private AiJobMapper jobs;
    @MockitoBean private CommunityPostMapper posts;
    @MockitoBean private MoodRecordMapper moods;
    @MockitoBean private AiReplyMapper replies;
    @MockitoBean private MoodReviewService reviews;

    private AiJob job;
    private String author;
    private String other;

    @BeforeEach
    void fixtures() {
        author = jwt.generateToken(AUTHOR_ID, "author");
        other = jwt.generateToken(OTHER_ID, "other");
        when(users.getById(AUTHOR_ID)).thenReturn(user(AUTHOR_ID));
        when(users.getById(OTHER_ID)).thenReturn(user(OTHER_ID));
        when(admission.transaction(any())).thenAnswer(call -> call.<Supplier<?>>getArgument(0).get());
        when(admission.save(any())).thenAnswer(call -> call.<Supplier<?>>getArgument(0).get());

        job = new AiJob();
        job.setId(JOB_ID);
        job.setUserId(AUTHOR_ID);
        job.setTaskType(AiJobService.TASK_TYPE_COMMUNITY_REPLY);
        job.setTargetId(POST_ID);
        job.setTargetVersion(2L);
        job.setStatus("failed");
        job.setAttemptCount(3);
        job.setFinishedAt(LocalDateTime.now());
        job.setLastErrorCode("provider_error");
        when(jobs.selectById(JOB_ID)).thenReturn(job);

        CommunityPost source = new CommunityPost();
        source.setId(POST_ID);
        source.setUserId(AUTHOR_ID);
        source.setStatus(1);
        source.setContentVersion(2L);
        source.setContent("合成普通正文");
        when(posts.selectById(POST_ID)).thenReturn(source);
    }

    @Test
    void onlyAuthorOfTheCorrectTaskTypeCanQueryOrRetry() throws Exception {
        when(jobs.selectRetryAfterSeconds(job, 180)).thenReturn(110);
        mvc.perform(get(ROUTE).header("token", author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetId").value("50"))
                .andExpect(jsonPath("$.data.retryable").value(false))
                .andExpect(jsonPath("$.data.retryAfterSeconds").value(110));

        mvc.perform(get(ROUTE).header("token", other)).andExpect(status().isNotFound());
        mvc.perform(post(ROUTE + "/retry").header("token", other)).andExpect(status().isNotFound());
        mvc.perform(get(ROUTE)).andExpect(status().isNotFound());
        mvc.perform(post(ROUTE + "/retry")).andExpect(status().isNotFound());
        mvc.perform(get("/mood/ai-job/" + JOB_ID).header("token", other)).andExpect(status().isNotFound());

        job.setTaskType("MOOD_REPLY");
        mvc.perform(get(ROUTE).header("token", author)).andExpect(status().isNotFound());
        mvc.perform(post(ROUTE + "/retry").header("token", author)).andExpect(status().isNotFound());
        verifyNoInteractions(limiter);
    }

    @Test
    void cooldownAndQuotaShareRetryAfterHeader() throws Exception {
        when(jobs.selectRetryAfterSeconds(job, 180)).thenReturn(111);
        mvc.perform(post(ROUTE + "/retry").header("token", author))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "111"));
        verifyNoInteractions(limiter);

        when(jobs.selectRetryAfterSeconds(job, 180)).thenReturn(0);
        when(limiter.check(RateLimitType.COMMUNITY_POST, AUTHOR_ID))
                .thenReturn(RateLimiter.Decision.deny(RateLimitScope.COMMUNITY_HOUR, 42));
        mvc.perform(post(ROUTE + "/retry").header("token", author))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "42"));
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        user.setRole("USER");
        user.setStatus(1);
        return user;
    }
}
