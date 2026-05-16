package com.xinyu.service;

import com.xinyu.dto.AiReplyResult;
import com.xinyu.entity.AiReply;
import com.xinyu.mapper.AiReplyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiReplyLogService {

    public static final String TARGET_TYPE_MOOD_RECORD = "MOOD_RECORD";

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
}
