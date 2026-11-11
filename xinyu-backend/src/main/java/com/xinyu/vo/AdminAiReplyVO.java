package com.xinyu.vo;

import java.time.LocalDateTime;

/** 全站管理员调用记录，包含已存提示词、回复正文和错误详情；仅经/admin鉴权接口返回。 */
public record AdminAiReplyVO(Long id, Long userId, String targetType, Long targetId,
                             String prompt, String replyContent, String modelName,
                             Integer status, String errorMessage, LocalDateTime createTime) {
}
