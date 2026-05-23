package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("community_post")
public class CommunityPost {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long topicId;

    private String moodType;

    private String content;

    private String aiReply;

    private String anonymousName;

    private Integer replyCount;

    private Integer likeCount;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField(exist = false)
    private Boolean liked;

    @TableField(exist = false)
    private Boolean favorited;

    public Long getId() {
        return id;
    }

    public Long getUserId() {
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

    public String getAiReply() {
        return aiReply;
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

    public Boolean getLiked() {
        return liked;
    }

    public Boolean getFavorited() {
        return favorited;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setUserId(Long userId) {
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

    public void setAiReply(String aiReply) {
        this.aiReply = aiReply;
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

    public void setLiked(Boolean liked) {
        this.liked = liked;
    }

    public void setFavorited(Boolean favorited) {
        this.favorited = favorited;
    }
}
