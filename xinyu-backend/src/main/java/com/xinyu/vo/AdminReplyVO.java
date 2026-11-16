package com.xinyu.vo;

import java.time.LocalDateTime;

/**
 * 后台回复管理分页行视图；状态与隐藏原因取值见 {@link com.xinyu.common.CommunityReportConstants}。
 */
public class AdminReplyVO {

    private String id;
    private String postId;
    private String userId;
    private String content;
    private String anonymousName;
    /** 内容可见状态（0 作者删、1 正常、2 管理员隐藏）。 */
    private Integer status;
    /** 隐藏原因编码（ATTACK / AD / PRIVACY / ILLEGAL）。 */
    private String hideReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public AdminReplyVO() {
    }

    public AdminReplyVO(String id, String postId, String userId, String content,
                        String anonymousName, Integer status,
                        LocalDateTime createTime, LocalDateTime updateTime) {
        this(id, postId, userId, content, anonymousName, status, null, createTime, updateTime);
    }

    public AdminReplyVO(String id, String postId, String userId, String content,
                        String anonymousName, Integer status, String hideReason,
                        LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.content = content;
        this.anonymousName = anonymousName;
        this.status = status;
        this.hideReason = hideReason;
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

    public String getHideReason() {
        return hideReason;
    }

    public void setHideReason(String hideReason) {
        this.hideReason = hideReason;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}