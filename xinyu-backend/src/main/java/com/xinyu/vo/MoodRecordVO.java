package com.xinyu.vo;

import java.time.LocalDateTime;

/** 日记公开给其所属用户的视图，回应状态与来源版本一起返回。 */
public record MoodRecordVO(Long id, Long userId, String moodType, String content, String aiReply,
                           Integer status, LocalDateTime createTime, LocalDateTime updateTime,
                           Long contentVersion, Boolean aiReplyEnabled, String aiReplyState,
                           Long jobId, String resultKind) {
}
