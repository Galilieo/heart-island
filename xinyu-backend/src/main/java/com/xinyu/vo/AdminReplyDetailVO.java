package com.xinyu.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台回复详情弹窗视图，包含全文、所属帖子正文及关联待处理举报摘要。
 */
public class AdminReplyDetailVO {

    private String id;
    private String postId;
    private String userId;
    private String postContent;
    private String content;
    private String anonymousName;
    private Integer status;
    private String hideReason;

    private String riskStatus;
    private LocalDateTime riskHitTime;
    private LocalDateTime riskReviewedTime;
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

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPostContent() {
        return postContent;
    }

    public void setPostContent(String postContent) {
        this.postContent = postContent;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAnonymousName() {
        return anonymousName;
    }

    public void setAnonymousName(String anonymousName) {
        this.anonymousName = anonymousName;
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

    public String getRiskStatus() {
        return riskStatus;
    }

    public void setRiskStatus(String riskStatus) {
        this.riskStatus = riskStatus;
    }

    public LocalDateTime getRiskHitTime() {
        return riskHitTime;
    }

    public void setRiskHitTime(LocalDateTime riskHitTime) {
        this.riskHitTime = riskHitTime;
    }

    public LocalDateTime getRiskReviewedTime() {
        return riskReviewedTime;
    }

    public void setRiskReviewedTime(LocalDateTime riskReviewedTime) {
        this.riskReviewedTime = riskReviewedTime;
    }
}
