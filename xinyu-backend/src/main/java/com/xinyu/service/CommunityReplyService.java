package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xinyu.dto.CommunityReplyAddDTO;
import com.xinyu.dto.CommunityReplyUpdateDTO;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.CommunityReply;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommunityReplyService {

    @Autowired
    private CommunityReplyMapper communityReplyMapper;

    @Autowired
    private CommunityPostMapper communityPostMapper;

    public List<CommunityReply> listByPostId(Long postId) {
        QueryWrapper<CommunityReply> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("status", 1);

        if (postId != null) {
            queryWrapper.eq("post_id", postId);
        }

        queryWrapper.orderByAsc("create_time");

        return communityReplyMapper.selectList(queryWrapper);
    }

    @Transactional
    public Boolean add(CommunityReplyAddDTO dto, Long userId) {
        CommunityReply reply = new CommunityReply();

        reply.setPostId(dto.getPostId());
        reply.setUserId(userId);
        reply.setContent(dto.getContent().trim());
        reply.setAnonymousName("匿名岛民");
        reply.setStatus(1);

        int rows =communityReplyMapper.insert(reply);

        if(rows >0){
            CommunityPost post = communityPostMapper.selectById(dto.getPostId());

            if (post != null) {
                Integer replyCount = post.getReplyCount();
                if(replyCount ==null){
                    replyCount = 0;
                }

                post.setReplyCount(replyCount+1);
                communityPostMapper.updateById(post);
            }
        }

        return rows > 0;
    }

    @Transactional
    public Boolean delete(Long id, Long userId) {
        CommunityReply reply = communityReplyMapper.selectById(id);

        if (reply == null) {
            return false;
        }

        if (reply.getStatus() == null || reply.getStatus() == 0) {
            return false;
        }

        if (!reply.getUserId().equals(userId)) {
            return false;
        }

        reply.setStatus(0);
        int rows = communityReplyMapper.updateById(reply);

        if (rows > 0) {
            CommunityPost post = communityPostMapper.selectById(reply.getPostId());

            if (post != null) {
                Integer replyCount = post.getReplyCount();
                if (replyCount == null || replyCount <= 0) {
                    replyCount = 0;
                } else {
                    replyCount = replyCount - 1;
                }

                post.setReplyCount(replyCount);
                communityPostMapper.updateById(post);
            }
        }

        return rows > 0;
    }

    @Transactional
    public Boolean update(CommunityReplyUpdateDTO dto, Long userId) {
        CommunityReply reply = communityReplyMapper.selectById(dto.getId());

        if (reply == null) {
            return false;
        }

        if (reply.getStatus() == null || reply.getStatus() == 0) {
            return false;
        }

        if (!reply.getUserId().equals(userId)) {
            return false;
        }

        reply.setContent(dto.getContent().trim());

        return communityReplyMapper.updateById(reply) > 0;
    }
}
