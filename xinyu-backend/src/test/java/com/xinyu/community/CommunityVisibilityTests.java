package com.xinyu.community;

import com.xinyu.dto.CommunityPostUpdateDTO;
import com.xinyu.dto.CommunityReplyAddDTO;
import com.xinyu.dto.CommunityReplyUpdateDTO;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.CommunityReply;
import com.xinyu.entity.Favorite;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import com.xinyu.mapper.FavoriteMapper;
import com.xinyu.mapper.PostLikeMapper;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.service.AiReplyLogService;
import com.xinyu.service.AiReplyService;
import com.xinyu.service.CommunityPostService;
import com.xinyu.service.CommunityReplyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4.1 统一按正常状态（status=1）判断可见性回归测试。
 */
@ExtendWith(MockitoExtension.class)
class CommunityVisibilityTests {

    private static final long AUTHOR_ID = 101L;
    private static final long OTHER_USER_ID = 202L;
    private static final long POST_ID = 11L;
    private static final long REPLY_ID = 21L;

    @Mock
    private CommunityPostMapper communityPostMapper;

    @Mock
    private CommunityReplyMapper communityReplyMapper;

    @Mock
    private PostLikeMapper postLikeMapper;

    @Mock
    private FavoriteMapper favoriteMapper;

    @Mock
    private TopicMapper topicMapper;

    @Mock
    private AiReplyService aiReplyService;

    @Mock
    private AiReplyLogService aiReplyLogService;

    @InjectMocks
    private CommunityPostService postService;

    @InjectMocks
    private CommunityReplyService replyService;

    @BeforeEach
    void stubDefaults() {
        lenient().when(postLikeMapper.selectCount(any())).thenReturn(0L);
        lenient().when(favoriteMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    void nonVisiblePostRejectsInteractionsAndFavorites() {
        CommunityPost nonVisible = post(0);
        when(communityPostMapper.selectById(POST_ID)).thenReturn(nonVisible);

        Favorite fav = new Favorite();
        fav.setPostId(POST_ID);
        fav.setUserId(OTHER_USER_ID);
        when(favoriteMapper.selectList(any())).thenReturn(List.of(fav));

        assertThat(postService.like(POST_ID, OTHER_USER_ID)).isFalse();
        assertThat(postService.unlike(POST_ID, OTHER_USER_ID)).isFalse();
        assertThat(postService.favorite(POST_ID, OTHER_USER_ID)).isFalse();
        assertThat(postService.unfavorite(POST_ID, OTHER_USER_ID)).isFalse();
        verify(postLikeMapper, never()).insert(any(com.xinyu.entity.PostLike.class));
        verify(postLikeMapper, never()).delete(any());
        verify(favoriteMapper, never()).insert(any(Favorite.class));
        verify(favoriteMapper, never()).delete(any());
        verify(communityPostMapper, never()).adjustLikeCount(anyLong(), anyInt());

        CommunityPostUpdateDTO editDto = new CommunityPostUpdateDTO();
        editDto.setId(POST_ID);
        editDto.setTopicId(1L);
        editDto.setMoodType("平静");
        editDto.setContent("试图修改非正常帖子");
        assertThat(postService.update(editDto, AUTHOR_ID)).isFalse();

        assertThat(postService.listFavorites(OTHER_USER_ID)).isEmpty();
        assertThat(postService.detail(POST_ID, OTHER_USER_ID)).isNull();
        assertThat(postService.detail(POST_ID, null)).isNull();
    }

    @Test
    void nonVisiblePostRejectsNewRepliesAndReplyListRequiresVisiblePost() {
        assertThat(replyService.listByPostId(null, OTHER_USER_ID)).isEmpty();
        verify(communityReplyMapper, never()).selectList(any());

        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(0));
        assertThat(replyService.listByPostId(POST_ID, OTHER_USER_ID)).isEmpty();

        CommunityReplyAddDTO dto = new CommunityReplyAddDTO();
        dto.setPostId(POST_ID);
        dto.setContent("尝试回复不可见帖子");
        assertThat(replyService.add(dto, OTHER_USER_ID)).isFalse();

        when(communityReplyMapper.selectById(REPLY_ID)).thenReturn(reply(1));
        CommunityReplyUpdateDTO editDto = new CommunityReplyUpdateDTO();
        editDto.setId(REPLY_ID);
        editDto.setContent("主帖不可见时改回复");
        assertThat(replyService.update(editDto, AUTHOR_ID)).isFalse();

        verify(communityReplyMapper, never()).insert(any(CommunityReply.class));
        verify(communityReplyMapper, never()).update(any(), any());
        verify(communityPostMapper, never()).adjustReplyCount(anyLong(), anyInt());
    }

    private static CommunityPost post(int status) {
        CommunityPost post = new CommunityPost();
        post.setId(POST_ID);
        post.setUserId(AUTHOR_ID);
        post.setTopicId(1L);
        post.setContent("测试内容");
        post.setStatus(status);
        return post;
    }

    private static CommunityReply reply(int status) {
        CommunityReply reply = new CommunityReply();
        reply.setId(REPLY_ID);
        reply.setPostId(POST_ID);
        reply.setUserId(AUTHOR_ID);
        reply.setContent("测试回复");
        reply.setStatus(status);
        return reply;
    }
}
