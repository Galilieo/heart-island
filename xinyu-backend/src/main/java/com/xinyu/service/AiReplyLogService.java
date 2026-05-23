package com.xinyu.service;

import com.xinyu.dto.AiReplyResult;
import com.xinyu.entity.AiReply;
import com.xinyu.mapper.AiReplyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Service
public class AiReplyLogService {

    public static final String TARGET_TYPE_MOOD_RECORD = "MOOD_RECORD";
    public static final String TARGET_TYPE_POST = "POST";

    @Autowired
    private AiReplyMapper aiReplyMapper;

    public void saveMoodReply(Long userId, Long moodRecordId, AiReplyResult result) {
        AiReply aiReply = new AiReply();
        aiReply.setUserId(userId);
        aiReply.setTargetType(TARGET_TYPE_MOOD_RECORD);
        aiReply.setTargetId(moodRecordId);
        aiReply.setPrompt(result.getPrompt());
        aiReply.setReplyContent(result.getReplyContent());
        aiReply.setModelName(result.getModelName());
        aiReply.setStatus(Boolean.TRUE.equals(result.getSuccess()) ? 1 : 0);
        aiReply.setErrorMessage(result.getErrorMessage());

        aiReplyMapper.insert(aiReply);
    }

    public void savePostReply(Long userId, Long postId, AiReplyResult result) {
        AiReply aiReply = new AiReply();
        aiReply.setUserId(userId);
        aiReply.setTargetType(TARGET_TYPE_POST);
        aiReply.setTargetId(postId);
        aiReply.setPrompt(result.getPrompt());
        aiReply.setReplyContent(result.getReplyContent());
        aiReply.setModelName(result.getModelName());
        aiReply.setStatus(Boolean.TRUE.equals(result.getSuccess()) ? 1 : 0);
        aiReply.setErrorMessage(result.getErrorMessage());

        aiReplyMapper.insert(aiReply);
    }

    public Page<AiReply> pageAdminAiReplies(Long userId,
                                            String targetType,
                                            Integer status,
                                            String keyword,
                                            Long pageNum,
                                            Long pageSize) {
        QueryWrapper<AiReply> queryWrapper = new QueryWrapper<>();

        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }

        if (targetType != null && !targetType.trim().isEmpty()) {
            queryWrapper.eq("target_type", targetType.trim());
        }

        if (status != null) {
            queryWrapper.eq("status", status);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                    .like("reply_content", keyword.trim())
                    .or()
                    .like("error_message", keyword.trim())
                    .or()
                    .like("prompt", keyword.trim())
            );
        }

        queryWrapper.orderByDesc("create_time");

        return aiReplyMapper.selectPage(new Page<>(pageNum, pageSize), queryWrapper);
    }
}
