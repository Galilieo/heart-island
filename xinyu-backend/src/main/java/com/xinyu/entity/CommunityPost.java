package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xinyu.common.CommunityReportConstants;

import java.time.LocalDateTime;

@TableName("community_post")
public class CommunityPost {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作者用户ID仅用于服务端权限校验与后台管理视角，不向社区接口响应输出。 */
    @JsonIgnore
    private Long userId;

    private Long topicId;

    private String moodType;

    private String content;

    private String aiReply;

    private String aiResultKind;

    /** 后端用于匹配当前版本任务。 */
    @JsonIgnore
    private Long contentVersion;

    /** 作者视图临时字段，通过 mine 决定是否序列化。 */
    @TableField(exist = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String aiReplyState;

    @TableField(exist = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long jobId;

    private String anonymousName;

    private Integer replyCount;

    private Integer likeCount;

    /** 内容可见状态（0 作者删、1 正常、2 管理员隐藏），见 {@link CommunityReportConstants}。 */
    private Integer status;

    /** 管理员隐藏原因编码，见 {@link CommunityReportConstants#VALID_HIDE_REASONS}。 */
    private String hideReason;

    @JsonIgnore
    private String riskStatus;
    @JsonIgnore
    private LocalDateTime riskHitTime;
    @JsonIgnore
    private LocalDateTime riskReviewedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField(exist = false)
    private Boolean liked;

    @TableField(exist = false)
    private Boolean favorited;

    @TableField(exist = false)
    private Boolean mine;

    /** 是否处于管理员隐藏状态（仅作者可见自身被隐藏内容时为 true）。 */
    @TableField(exist = false)
    private Boolean hidden;

    @JsonIgnore
    public boolean isVisible() {
        return Integer.valueOf(CommunityReportConstants.STATUS_VISIBLE).equals(status);
    }

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

    @JsonProperty("resultKind")
    public String getAiResultKind() {
        return aiResultKind;
    }

    public void setAiResultKind(String aiResultKind) {
        this.aiResultKind = aiResultKind;
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

    public Boolean getMine() {
        return mine;
    }

    public String getHideReason() {
        return hideReason;
    }

    public Boolean getHidden() {
        return hidden;
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

    public void setMine(Boolean mine) {
        this.mine = mine;
    }

    public void setHideReason(String hideReason) {
        this.hideReason = hideReason;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
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

    public Long getContentVersion() {
        return contentVersion;
    }

    public void setContentVersion(Long contentVersion) {
        this.contentVersion = contentVersion;
    }

    public String getAiReplyState() {
        return Boolean.TRUE.equals(mine) ? aiReplyState : null;
    }

    public void setAiReplyState(String aiReplyState) {
        this.aiReplyState = aiReplyState;
    }

    public Long getJobId() {
        return Boolean.TRUE.equals(mine) ? jobId : null;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }
}
