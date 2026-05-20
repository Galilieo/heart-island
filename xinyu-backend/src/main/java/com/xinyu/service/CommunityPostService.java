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

@Service
public class CommunityPostService {

    @Autowired
    private CommunityPostMapper communityPostMapper;

    @Autowired
    private PostLikeMapper postLikeMapper;

    @Autowired
    private FavoriteMapper favoriteMapper;

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

    public Boolean add(CommunityPostAddDTO dto, Long userId){
        CommunityPost post = new CommunityPost();

        post.setUserId(userId);
        post.setTopicId(dto.getTopicId());
        post.setMoodType(dto.getMoodType().trim());
        post.setContent(dto.getContent().trim());
        post.setAnonymousName("匿名岛民");
        post.setReplyCount(0);
        post.setLikeCount(0);
        post.setStatus(1);

        return communityPostMapper.insert(post) > 0;
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

        return communityPostMapper.updateById(post) > 0;
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
}
