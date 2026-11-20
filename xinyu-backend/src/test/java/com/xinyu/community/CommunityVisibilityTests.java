package com.xinyu.community;

import com.xinyu.dto.CommunityPostUpdateDTO;
import com.xinyu.dto.CommunityReplyAddDTO;
import com.xinyu.dto.CommunityReplyUpdateDTO;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.CommunityReply;
import com.xinyu.entity.Favorite;
import com.xinyu.entity.PostLike;
import com.xinyu.entity.Topic;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import com.xinyu.mapper.FavoriteMapper;
import com.xinyu.mapper.PostLikeMapper;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.service.AiReplyLogService;
import com.xinyu.service.CommunityAiClient;
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
 * 4.1 / 4.2 可见性与状态回归测试：确保 status=2（已隐藏）对所有社区交互入口不可用，
 * 仅允许作者本人删除且不再重复扣减回复数。
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
    private CommunityAiClient communityAiClient;

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
    void hiddenPostRejectsLikeAndFavorite() {
        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(2));

        assertThat(postService.like(POST_ID, OTHER_USER_ID)).isFalse();
        verify(postLikeMapper, never()).insert(any(PostLike.class));
        verify(communityPostMapper, never()).adjustLikeCount(anyLong(), anyInt());

        assertThat(postService.favorite(POST_ID, OTHER_USER_ID)).isFalse();
        verify(favoriteMapper, never()).insert(any(Favorite.class));
    }

    @Test
    void hiddenPostRejectsAuthorEdit() {
        Topic activeTopic = new Topic();
        activeTopic.setId(1L);
        activeTopic.setStatus(1);
        when(topicMapper.selectById(1L)).thenReturn(activeTopic);
        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(2));

        CommunityPostUpdateDTO editDto = new CommunityPostUpdateDTO();
        editDto.setId(POST_ID);
        editDto.setTopicId(1L);
        editDto.setMoodType("平静");
        editDto.setContent("试图修改被隐藏帖子");
        assertThat(postService.update(editDto, AUTHOR_ID).saved()).isFalse();
    }

    @Test
    void hiddenPostIsExcludedFromFavoritesUnlikeUnfavoriteAndNonAuthorDetail() {
        when(communityPostMapper.selectList(any())).thenReturn(List.of(post(2)));
        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(2));
        Favorite fav = new Favorite();
        fav.setPostId(POST_ID);
        fav.setUserId(OTHER_USER_ID);
        when(favoriteMapper.selectList(any())).thenReturn(List.of(fav));

        assertThat(postService.listFavorites(OTHER_USER_ID)).isEmpty();
        assertThat(postService.unlike(POST_ID, OTHER_USER_ID)).isFalse();
        assertThat(postService.unfavorite(POST_ID, OTHER_USER_ID)).isFalse();
        verify(postLikeMapper, never()).delete(any());
        verify(favoriteMapper, never()).delete(any());
        assertThat(postService.detail(POST_ID, OTHER_USER_ID)).isNull();
        assertThat(postService.detail(POST_ID, null)).isNull();
    }

    @Test
    void replyListRejectsMissingPostIdAndUpdateRejectsReplyUnderHiddenPost() {
        assertThat(replyService.listByPostId(null, OTHER_USER_ID)).isEmpty();
        verify(communityReplyMapper, never()).selectList(any());

        when(communityReplyMapper.selectById(REPLY_ID)).thenReturn(reply(1));
        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(2));
        CommunityReplyUpdateDTO editDto = new CommunityReplyUpdateDTO();
        editDto.setId(REPLY_ID);
        editDto.setContent("帖子隐藏后仍试图改回复");
        assertThat(replyService.update(editDto, AUTHOR_ID).saved()).isFalse();
        verify(communityReplyMapper, never()).update(any(), any());
    }

    @Test
    void hiddenAndDeletedPostsRejectNewReplies() {
        CommunityReplyAddDTO dto = new CommunityReplyAddDTO();
        dto.setPostId(POST_ID);
        dto.setContent("尝试回复被隐藏帖子");

        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(2));
        assertThat(replyService.add(dto, OTHER_USER_ID).saved()).isFalse();

        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(0));
        assertThat(replyService.add(dto, OTHER_USER_ID).saved()).isFalse();

        verify(communityReplyMapper, never()).insert(any(CommunityReply.class));
        verify(communityPostMapper, never()).adjustReplyCount(anyLong(), anyInt());
    }

    @Test
    void hiddenReplyRejectsAuthorEditAndDeleteDoesNotDecrementReplyCountAgain() {
        CommunityReply hiddenReply = reply(2);
        when(communityReplyMapper.selectById(REPLY_ID)).thenReturn(hiddenReply);

        CommunityReplyUpdateDTO editDto = new CommunityReplyUpdateDTO();
        editDto.setId(REPLY_ID);
        editDto.setContent("试图修改被隐藏回复");
        assertThat(replyService.update(editDto, AUTHOR_ID).saved()).isFalse();

        when(communityReplyMapper.update(any(), any())).thenReturn(1);
        assertThat(replyService.delete(REPLY_ID, AUTHOR_ID)).isTrue();
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
