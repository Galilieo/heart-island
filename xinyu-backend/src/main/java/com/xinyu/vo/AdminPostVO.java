package com.xinyu.vo;

import java.time.LocalDateTime;

public class AdminPostVO {

    private String id;
    private String userId;
    private Long topicId;
    private String moodType;
    private String content;
    private String anonymousName;
    private Integer replyCount;
    private Integer likeCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public AdminPostVO() {
    }

    public AdminPostVO(String id, String userId, Long topicId, String moodType, String content,
                       String anonymousName, Integer replyCount, Integer likeCount, Integer status,
                       LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.userId = userId;
        this.topicId = topicId;
        this.moodType = moodType;
        this.content = content;
        this.anonymousName = anonymousName;
        this.replyCount = replyCount;
        this.likeCount = likeCount;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public Long getTopicId() {
        return topicId;
    }

    public String getMoodType() {
        return moodType;
    }

    public String getContent() {
        return content;
    }

    public String getAnonymousName() {
        return anonymousName;
    }

    public Integer getReplyCount() {
        return replyCount;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public Integer getStatus() {
        return status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public void setMoodType(String moodType) {
        this.moodType = moodType;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setAnonymousName(String anonymousName) {
        this.anonymousName = anonymousName;
    }

    public void setReplyCount(Integer replyCount) {
        this.replyCount = replyCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}