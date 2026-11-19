package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /** 当前保存发起的AI生成标识；NULL表示无有效生成，仅服务端用于条件回写。 */
    @JsonIgnore
    private String aiGenerationId;

    private String anonymousName;

    private Integer replyCount;

    private Integer likeCount;

    /** 内容可见状态（0 作者删、1 正常、2 管理员隐藏），见 {@link CommunityReportConstants}。 */
    private Integer status;

    /** 管理员隐藏原因编码，见 {@link CommunityReportConstants#VALID_HIDE_REASONS}。 */
    private String hideReason;

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

    public String getAiGenerationId() {
        return aiGenerationId;
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

    public void setAiGenerationId(String aiGenerationId) {
        this.aiGenerationId = aiGenerationId;
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
}
