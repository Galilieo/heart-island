package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xinyu.dto.CommunityPostAddDTO;
import com.xinyu.dto.CommunityPostUpdateDTO;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.Favorite;
import com.xinyu.entity.PostLike;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.FavoriteMapper;
import com.xinyu.mapper.PostLikeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.vo.AdminPostVO;
import com.xinyu.entity.Topic;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.dto.AiReplyResult;

@Service
public class CommunityPostService {

    @Autowired
    private CommunityPostMapper communityPostMapper;

    @Autowired
    private PostLikeMapper postLikeMapper;

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private TopicMapper topicMapper;

    @Autowired
    private AiReplyService aiReplyService;

    @Autowired
    private AiReplyLogService aiReplyLogService;

    public List<CommunityPost> listByTopicId(Long topicId, Long userId, Boolean mine, String sort) {
        QueryWrapper<CommunityPost> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("status", 1);

        if (topicId != null) {
            queryWrapper.eq("topic_id", topicId);
        }

        if (Boolean.TRUE.equals(mine)) {
            queryWrapper.eq("user_id", userId);
        }

        // 排序：hot 按点赞数（时间兜底），其余一律按最新
        if ("hot".equals(sort)) {
            queryWrapper.orderByDesc("like_count");
            queryWrapper.orderByDesc("create_time");
        } else {
            queryWrapper.orderByDesc("create_time");
        }

        List<CommunityPost> posts = communityPostMapper.selectList(queryWrapper);

        for (CommunityPost post : posts) {
            post.setLiked(isLiked(post.getId(), userId));
            post.setFavorited(isFavorited(post.getId(), userId));
        }

        return posts;
    }

    public Page<AdminPostVO> pageAdminPosts(Integer status, Long topicId, Long postId, Long userId, String keyword, Long pageNum, Long pageSize){
        QueryWrapper<CommunityPost> queryWrapper = new QueryWrapper<>();

        if(status != null){
            queryWrapper.eq("status", status);
        }

        if(topicId != null){
            queryWrapper.eq("topic_id", topicId);
        }

        if(postId != null){
            queryWrapper.eq("id", postId);
        }

        if(userId != null){
            queryWrapper.eq("user_id", userId);
        }

        if(keyword != null && !keyword.trim().isEmpty()){
            queryWrapper.like("content", keyword.trim());
        }

        queryWrapper.orderByDesc("create_time");

        Page<CommunityPost> postPage = communityPostMapper.selectPage(
                new Page<>(pageNum,pageSize),
                queryWrapper
        );

        Page<AdminPostVO> voPage = new Page<>(
                postPage.getCurrent(),
                postPage.getSize(),
                postPage.getTotal()
        );

        voPage.setRecords(postPage.getRecords().stream()
                .map(post -> new AdminPostVO(
                        String.valueOf(post.getId()),
                        String.valueOf(post.getUserId()),
                        post.getTopicId(),
                        post.getMoodType(),
                        post.getContent(),
                        post.getAnonymousName(),
                        post.getReplyCount(),
                        post.getLikeCount(),
                        post.getStatus(),
                        post.getCreateTime(),
                        post.getUpdateTime()
                ))
                .toList());

        return voPage;
    }

    public CommunityPost updateAdminStatus(Long id,Integer status){
        CommunityPost post = communityPostMapper.selectById(id);

        if(post == null){
            return null;
        }

        post.setStatus(status);
        communityPostMapper.updateById(post);

        return post;
    }
    /**
     * 查询当前用户收藏的帖子，按收藏时间倒序。
     * 已被删除/隐藏的帖子会自动跳过。
     */
    public List<CommunityPost> listFavorites(Long userId) {
        QueryWrapper<Favorite> favQuery = new QueryWrapper<>();
        favQuery.eq("user_id", userId);
        favQuery.orderByDesc("create_time");

        List<Favorite> favorites = favoriteMapper.selectList(favQuery);

        List<CommunityPost> result = new ArrayList<>();

        for (Favorite favorite : favorites) {
            CommunityPost post = communityPostMapper.selectById(favorite.getPostId());

            if (post == null || post.getStatus() == null || post.getStatus() == 0) {
                continue;
            }

            post.setLiked(isLiked(post.getId(), userId));
            post.setFavorited(true);
            result.add(post);
        }

        return result;
    }
    public Boolean isTopicAvailable(Long topicId){
        if(topicId == null){
            return false;
        }

        Topic topic = topicMapper.selectById(topicId);

        if(topic == null){
            return false;
        }

        return topic.getStatus() !=null && topic.getStatus() == 1;
    }

    public Boolean add(CommunityPostAddDTO dto, Long userId){
        if(!isTopicAvailable(dto.getTopicId())){
            return false;
        }
        CommunityPost post = new CommunityPost();

        post.setUserId(userId);
        post.setTopicId(dto.getTopicId());
        post.setMoodType(dto.getMoodType().trim());
        post.setContent(dto.getContent().trim());
        post.setAnonymousName("匿名岛民");
        post.setReplyCount(0);
        post.setLikeCount(0);
        post.setStatus(1);

        int rows = communityPostMapper.insert(post);
        if (rows <= 0) {
            return false;
        }

        // 先 insert 拿到 post.id，再调 AI 回写。AI 失败不阻断发帖。
        attachAiReply(post);

        return true;
    }

    @Transactional
    public Boolean delete(Long id, Long userId) {
        CommunityPost post = communityPostMapper.selectById(id);

        if (post == null) {
            return false;
        }

        if (post.getStatus() == null || post.getStatus() == 0) {
            return false;
        }

        if (!post.getUserId().equals(userId)) {
            return false;
        }

        post.setStatus(0);

        return communityPostMapper.updateById(post) > 0;
    }

    @Transactional
    public Boolean update(CommunityPostUpdateDTO dto, Long userId) {
        if(!isTopicAvailable(dto.getTopicId())){
            return false;
        }

        CommunityPost post = communityPostMapper.selectById(dto.getId());

        if (post == null) {
            return false;
        }

        if (post.getStatus() == null || post.getStatus() == 0) {
            return false;
        }

        if (!post.getUserId().equals(userId)) {
            return false;
        }

        post.setTopicId(dto.getTopicId());
        post.setMoodType(dto.getMoodType().trim());
        post.setContent(dto.getContent().trim());

        int rows = communityPostMapper.updateById(post);
        if (rows <= 0) {
            return false;
        }

        // 内容已落库，AI 回复同步重新生成
        attachAiReply(post);

        return true;
    }

    @Transactional
    public Boolean like(Long postId, Long userId) {
        CommunityPost post = communityPostMapper.selectById(postId);

        if (post == null || post.getStatus() == null || post.getStatus() == 0) {
            return false;
        }

        if (isLiked(postId, userId)) {
            return true;
        }

        PostLike postLike = new PostLike();
        postLike.setPostId(postId);
        postLike.setUserId(userId);

        int rows = postLikeMapper.insert(postLike);

        if (rows > 0) {
            Integer likeCount = post.getLikeCount();
            if (likeCount == null) {
                likeCount = 0;
            }

            post.setLikeCount(likeCount + 1);
            communityPostMapper.updateById(post);
        }

        return rows > 0;
    }

    @Transactional
    public Boolean unlike(Long postId, Long userId) {
        QueryWrapper<PostLike> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("post_id", postId);
        queryWrapper.eq("user_id", userId);

        int rows = postLikeMapper.delete(queryWrapper);

        if (rows > 0) {
            CommunityPost post = communityPostMapper.selectById(postId);

            if (post != null) {
                Integer likeCount = post.getLikeCount();
                if (likeCount == null || likeCount <= 0) {
                    likeCount = 0;
                } else {
                    likeCount = likeCount - 1;
                }

                post.setLikeCount(likeCount);
                communityPostMapper.updateById(post);
            }
        }

        return rows > 0;
    }

    private Boolean isLiked(Long postId, Long userId) {
        if (postId == null || userId == null) {
            return false;
        }

        QueryWrapper<PostLike> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("post_id", postId);
        queryWrapper.eq("user_id", userId);

        return postLikeMapper.selectCount(queryWrapper) > 0;
    }

    public Boolean favorite(Long postId, Long userId) {
        CommunityPost post = communityPostMapper.selectById(postId);

        if (post == null || post.getStatus() == null || post.getStatus() == 0) {
            return false;
        }

        if (isFavorited(postId, userId)) {
            return true;
        }

        Favorite favorite = new Favorite();
        favorite.setPostId(postId);
        favorite.setUserId(userId);

        return favoriteMapper.insert(favorite) > 0;
    }

    public Boolean unfavorite(Long postId, Long userId) {
        QueryWrapper<Favorite> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("post_id", postId);
        queryWrapper.eq("user_id", userId);

        return favoriteMapper.delete(queryWrapper) > 0;
    }

    private Boolean isFavorited(Long postId, Long userId) {
        if (postId == null || userId == null) {
            return false;
        }

        QueryWrapper<Favorite> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("post_id", postId);
        queryWrapper.eq("user_id", userId);

        return favoriteMapper.selectCount(queryWrapper) > 0;
    }
    public CommunityPost detail(Long id,Long userId){
        CommunityPost post = communityPostMapper.selectById(id);

        if(post == null){
            return null;
        }

        if(post.getStatus()==null||post.getStatus()==0){
            return null;
        }

        post.setLiked(isLiked(post.getId(), userId));
        post.setFavorited(isFavorited(post.getId(), userId));

        return post;
    }

    /**
     * 为指定帖子生成 AI 回复并回写到 post.aiReply，
     * 同时通过 AiReplyLogService 写一行日志。
     * 失败时使用兜底文案，不抛异常，不阻断主流程。
     */
    private void attachAiReply(CommunityPost post) {
        String topicName = null;
        Topic topic = topicMapper.selectById(post.getTopicId());
        if (topic != null) {
            topicName = topic.getName();
        }

        AiReplyResult result = aiReplyService.generateReplyForPost(
                topicName, post.getMoodType(), post.getContent());

        post.setAiReply(result.getReplyContent());
        communityPostMapper.updateById(post);

        aiReplyLogService.savePostReply(post.getUserId(), post.getId(), result);
    }
}
