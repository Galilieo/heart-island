package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xinyu.common.CommunityReportConstants;

import java.time.LocalDateTime;

@TableName("community_reply")
public class CommunityReply {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;

    /** 作者用户ID仅用于服务端权限校验与后台管理视角，不向社区接口响应输出。 */
    @JsonIgnore
    private Long userId;

    private String content;

    private String anonymousName;

    private Integer status;

    private String hideReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField(exist = false)
    private Boolean mine;

    @TableField(exist = false)
    private Boolean hidden;

    @JsonIgnore
    public boolean isVisible() {
        return Integer.valueOf(CommunityReportConstants.STATUS_VISIBLE).equals(status);
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getUserId() {
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

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public void setUserId(Long userId) {
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