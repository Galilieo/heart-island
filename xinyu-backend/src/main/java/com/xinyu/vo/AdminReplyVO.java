package com.xinyu.vo;

import java.time.LocalDateTime;

public class AdminReplyVO {

    private String id;
    private String postId;
    private String userId;
    private String content;
    private String anonymousName;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public AdminReplyVO() {
    }

    public AdminReplyVO(String id, String postId, String userId, String content,
                        String anonymousName, Integer status,
                        LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.content = content;
        this.anonymousName = anonymousName;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    public String getId() {
        return id;
    }

    public String getPostId() {
        return postId;
    }

    public String getUserId() {
        return userId;
    }

    public String getContent() {
        return content;
    }

    public String getAnonymousName() {
        return anonymousName;
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

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setAnonymousName(String anonymousName) {
        this.anonymousName = anonymousName;
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