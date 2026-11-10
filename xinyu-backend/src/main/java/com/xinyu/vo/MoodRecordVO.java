package com.xinyu.vo;

import java.time.LocalDateTime;

/**
 * 日记公开给其所属用户的视图，回应状态与来源版本一起返回。
 *
 * @param id 日记 ID
 * @param userId 所属用户 ID
 * @param moodType 心情类型
 * @param content 用户原文
 * @param aiReply 可展示的 AI 回复；非成功或非历史留存状态为空
 * @param status 1 有效；0 已软删除
 * @param createTime 创建时间
 * @param updateTime 更新时间
 * @param contentVersion 生成依据版本
 * @param aiReplyEnabled 当前版本是否开启 AI 回应
 * @param aiReplyState 回应状态：任务状态（queued/running/succeeded/failed/superseded）
 *                      或 legacy（历史回复）、unavailable（待生成）、disabled（已关闭）
 * @param jobId 当前版本关联的任务 ID；无任务时为空
 * @param resultKind 任务结果类型；仅成功时有值
 */
public record MoodRecordVO(Long id, Long userId, String moodType, String content, String aiReply,
                           Integer status, LocalDateTime createTime, LocalDateTime updateTime,
                           Long contentVersion, Boolean aiReplyEnabled, String aiReplyState,
                           Long jobId, String resultKind) {
}
