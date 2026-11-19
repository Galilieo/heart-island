package com.xinyu.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台帖子详情弹窗视图，包含全文、小芽回复及关联待处理举报摘要。
 */
public class AdminPostDetailVO {

    private String id;
    private String userId;
    private Long topicId;
    private String topicName;
    private String moodType;
    private String resultKind;
    private String content;
    private String aiReply;
    private String anonymousName;
    private Integer replyCount;
    private Integer likeCount;
    private Integer status;
    private String hideReason;
    private Long pendingReportCount;
    private List<String> pendingReasons;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public String getMoodType() {
        return moodType;
    }

    public void setMoodType(String moodType) {
        this.moodType = moodType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAiReply() {
        return aiReply;
    }

    public void setAiReply(String aiReply) {
        this.aiReply = aiReply;
    }

    public String getAnonymousName() {
        return anonymousName;
    }

    public void setAnonymousName(String anonymousName) {
        this.anonymousName = anonymousName;
    }

    public Integer getReplyCount() {
        return replyCount;
    }

    public void setReplyCount(Integer replyCount) {
        this.replyCount = replyCount;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getHideReason() {
        return hideReason;
    }

    public void setHideReason(String hideReason) {
        this.hideReason = hideReason;
    }

    public Long getPendingReportCount() {
        return pendingReportCount;
    }

    public void setPendingReportCount(Long pendingReportCount) {
        this.pendingReportCount = pendingReportCount;
    }

    public List<String> getPendingReasons() {
        return pendingReasons;
    }

    public void setPendingReasons(List<String> pendingReasons) {
        this.pendingReasons = pendingReasons;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getResultKind() {
        return resultKind;
    }

    public void setResultKind(String resultKind) {
        this.resultKind = resultKind;
    }
}
