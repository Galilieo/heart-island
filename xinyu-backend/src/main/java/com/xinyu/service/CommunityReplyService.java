package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.dto.CommunityReplyAddDTO;
import com.xinyu.dto.CommunityReplyUpdateDTO;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.CommunityReply;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import com.xinyu.vo.AdminReplyVO;
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

    public Page<AdminReplyVO> pageAdminReplies(Integer status, Long postId, Long userId, String keyword, Long pageNum, Long pageSize) {
        QueryWrapper<CommunityReply> queryWrapper = new QueryWrapper<>();

        if (status != null) {
            queryWrapper.eq("status", status);
        }

        if (postId != null) {
            queryWrapper.eq("post_id", postId);
        }

        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.like("content", keyword.trim());
        }

        queryWrapper.orderByDesc("create_time");

        Page<CommunityReply> replyPage = communityReplyMapper.selectPage(
                new Page<>(pageNum, pageSize),
                queryWrapper
        );

        Page<AdminReplyVO> voPage = new Page<>(
                replyPage.getCurrent(),
                replyPage.getSize(),
                replyPage.getTotal()
        );

        voPage.setRecords(replyPage.getRecords().stream()
                .map(reply -> new AdminReplyVO(
                        String.valueOf(reply.getId()),
                        String.valueOf(reply.getPostId()),
                        String.valueOf(reply.getUserId()),
                        reply.getContent(),
                        reply.getAnonymousName(),
                        reply.getStatus(),
                        reply.getCreateTime(),
                        reply.getUpdateTime()
                ))
                .toList());

        return voPage;
    }

    public CommunityReply updateAdminStatus(Long id, Integer status) {
        CommunityReply reply = communityReplyMapper.selectById(id);

        if (reply == null) {
            return null;
        }

        reply.setStatus(status);
        communityReplyMapper.updateById(reply);

        return reply;
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
