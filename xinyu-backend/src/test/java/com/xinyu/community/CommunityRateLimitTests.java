package com.xinyu.community;

import com.xinyu.common.CommunityReportConstants;
import com.xinyu.dto.CommunityPostAddDTO;
import com.xinyu.dto.CommunityPostUpdateDTO;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.Topic;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.ratelimit.RateLimiter;
import com.xinyu.ratelimit.RateLimitScope;
import com.xinyu.service.AiReplyLogService;
import com.xinyu.service.AiReplyService;
import com.xinyu.service.CommunityPostService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 社区发帖与编辑的限流行为：帖子照常保存、不发起生成、不写兜底与失败记录。 */
@ExtendWith(MockitoExtension.class)
class CommunityRateLimitTests {

    @Mock
    private CommunityPostMapper communityPostMapper;

    @Mock
    private TopicMapper topicMapper;

    @Mock
    private AiReplyService aiReplyService;

    @Mock
    private AiReplyLogService aiReplyLogService;

    @Mock
    private RateLimiter rateLimiter;

    @InjectMocks
    private CommunityPostService postService;

    private static CommunityPostAddDTO add() {
        CommunityPostAddDTO dto = new CommunityPostAddDTO();
        dto.setTopicId(1L);
        dto.setMoodType("平静");
        dto.setContent("今天的风很舒服");
        return dto;
    }

    @Test
    void 超额时帖子照常保存但不发起生成() {
        when(topicMapper.selectById(1L)).thenReturn(activeTopic());
        when(communityPostMapper.insert(any(CommunityPost.class))).thenReturn(1);
        when(rateLimiter.check(any(), anyLong()))
                .thenReturn(RateLimiter.Decision.deny(RateLimitScope.COMMUNITY_HOUR, 900));

        CommunityPostService.PostSaveResult result = postService.add(add(), 1L);

        assertThat(result.saved()).isTrue();
        assertThat(result.aiReplyLimited()).isTrue();
        // 不发起同步生成，也不把限流当成生成失败写日志。
        verifyNoInteractions(aiReplyService);
        verifyNoInteractions(aiReplyLogService);
        verify(communityPostMapper, never()).update(any(), any());
    }

    @Test
    void 编辑超额时帖子保存且不再生成() {
        CommunityPost existing = new CommunityPost();
        existing.setId(5L);
        existing.setUserId(1L);
        existing.setStatus(CommunityReportConstants.STATUS_VISIBLE);
        existing.setTopicId(1L);
        existing.setAiReply("旧回复");
        CommunityPostUpdateDTO edit = new CommunityPostUpdateDTO();
        edit.setId(5L);
        edit.setTopicId(1L);
        edit.setMoodType("平静");
        edit.setContent("改成新内容");
        when(topicMapper.selectById(1L)).thenReturn(activeTopic());
        when(communityPostMapper.selectById(5L)).thenReturn(existing);
        when(communityPostMapper.update(any(), any())).thenReturn(1);
        when(rateLimiter.check(any(), anyLong()))
                .thenReturn(RateLimiter.Decision.deny(RateLimitScope.GLOBAL_HOUR, 300));

        CommunityPostService.PostSaveResult result = postService.update(edit, 1L);

        assertThat(result.saved()).isTrue();
        assertThat(result.aiReplyLimited()).isTrue();
        verifyNoInteractions(aiReplyService);
        verifyNoInteractions(aiReplyLogService);
    }

    @Test
    void 关闭Ai时不消耗额度() {
        when(topicMapper.selectById(1L)).thenReturn(activeTopic());
        when(communityPostMapper.insert(any(CommunityPost.class))).thenReturn(1);
        CommunityPostAddDTO dto = add();
        dto.setNeedAiReply(false);

        CommunityPostService.PostSaveResult result = postService.add(dto, 1L);

        assertThat(result.saved()).isTrue();
        assertThat(result.aiReplyLimited()).isFalse();
        verifyNoInteractions(rateLimiter);
        verifyNoInteractions(aiReplyService);
    }

    @Test
    void 额度充足时照常生成() {
        when(topicMapper.selectById(1L)).thenReturn(activeTopic());
        when(communityPostMapper.insert(any(CommunityPost.class))).thenReturn(1);
        when(rateLimiter.check(any(), anyLong())).thenReturn(RateLimiter.Decision.allow());
        com.xinyu.dto.AiReplyResult reply = new com.xinyu.dto.AiReplyResult();
        reply.setSuccess(Boolean.FALSE);
        when(aiReplyService.generateReplyForPost(anyString(), anyString(), anyString())).thenReturn(reply);

        CommunityPostService.PostSaveResult result = postService.add(add(), 1L);

        assertThat(result.saved()).isTrue();
        assertThat(result.aiReplyLimited()).isFalse();
        verify(aiReplyService).generateReplyForPost(anyString(), anyString(), anyString());
    }

    private static Topic activeTopic() {
        Topic topic = new Topic();
        topic.setId(1L);
        topic.setName("日常");
        topic.setStatus(1);
        return topic;
    }
}
