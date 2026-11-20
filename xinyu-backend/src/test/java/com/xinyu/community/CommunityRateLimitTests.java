package com.xinyu.community;

import com.xinyu.dto.CommunityPostAddDTO;
import com.xinyu.dto.CommunityPostUpdateDTO;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.Topic;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.ratelimit.RateLimiter;
import com.xinyu.ratelimit.RateLimitScope;
import com.xinyu.ratelimit.RateLimitType;
import com.xinyu.service.AiJobAdmission;
import com.xinyu.service.AiJobService;
import com.xinyu.service.CommunityPostService;
import com.xinyu.service.MoodReviewSafety;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityRateLimitTests {
    @Mock private CommunityPostMapper posts;
    @Mock private TopicMapper topics;
    @Mock private AiJobAdmission admission;
    @Mock private AiJobService jobs;
    @Mock private RateLimiter limiter;
    @InjectMocks private CommunityPostService service;

    @BeforeEach
    void topicAndAdmission() {
        Topic topic = new Topic();
        topic.setStatus(1);
        when(topics.selectById(1L)).thenReturn(topic);
        when(admission.save(any())).thenAnswer(call -> call.<Supplier<?>>getArgument(0).get());
    }

    @Test
    void deniedPostStillSavesAndReturnsFailedJob() {
        generatedPostId();
        var denied = RateLimiter.Decision.deny(RateLimitScope.COMMUNITY_HOUR, 900);
        when(limiter.check(RateLimitType.COMMUNITY_POST, 1L)).thenReturn(denied);
        when(jobs.createCommunityJob(1L, 1L, 1L, denied)).thenReturn(job("failed"));

        var response = service.add(add(), 1L).response();

        assertThat(response.jobId()).isEqualTo(10L);
        assertThat(response.aiReplyState()).isEqualTo("failed");
    }

    @Test
    void deniedEditInvalidatesOldVersionAndReturnsFailedJob() {
        CommunityPost post = new CommunityPost();
        post.setId(1L);
        post.setUserId(1L);
        post.setStatus(1);
        post.setContentVersion(1L);
        when(posts.selectById(1L)).thenReturn(post);
        when(posts.selectByIdForUpdate(1L)).thenReturn(post);
        when(posts.update(any(), any())).thenReturn(1);

        var denied = RateLimiter.Decision.deny(RateLimitScope.GLOBAL_HOUR, 900);
        when(limiter.check(RateLimitType.COMMUNITY_POST, 1L)).thenReturn(denied);
        when(jobs.createCommunityJob(1L, 1L, 2L, denied)).thenReturn(job("failed"));
        CommunityPostUpdateDTO edit = new CommunityPostUpdateDTO();
        edit.setId(1L);
        edit.setTopicId(1L);
        edit.setMoodType("平静");
        edit.setContent("合成编辑");

        assertThat(service.update(edit, 1L).response().aiReplyState()).isEqualTo("failed");
        verify(jobs).supersedeCommunityJobs(1L, 1L);
    }

    @Test
    void disabledPostChecksNeitherQuotaNorJobs() {
        generatedPostId();
        var dto = add();
        dto.setNeedAiReply(false);

        assertThat(service.add(dto, 1L).response().aiReplyState()).isEqualTo("disabled");
        verifyNoInteractions(limiter, jobs);
    }

    @Test
    void crisisPostQueuesWithoutQuotaAndKeepsPrivateSupportText() {
        generatedPostId();
        var dto = add();
        dto.setContent("我不想活了");
        when(jobs.createCommunityJob(1L, 1L, 1L, RateLimiter.Decision.allow())).thenReturn(job("queued"));

        var response = service.add(dto, 1L).response();

        assertThat(response.aiReplyState()).isEqualTo("queued");
        assertThat(response.supportText()).isEqualTo(MoodReviewSafety.SUPPORT_TEXT);
        verifyNoInteractions(limiter);
    }

    private void generatedPostId() {
        when(posts.insert(any(CommunityPost.class))).thenAnswer(invocation -> {
            invocation.<CommunityPost>getArgument(0).setId(1L);
            return 1;
        });
    }

    private static AiJob job(String status) {
        AiJob job = new AiJob();
        job.setId(10L);
        job.setStatus(status);
        return job;
    }

    private static CommunityPostAddDTO add() {
        CommunityPostAddDTO dto = new CommunityPostAddDTO();
        dto.setTopicId(1L);
        dto.setMoodType("平静");
        dto.setContent("今天的风很舒服");
        return dto;
    }
}
