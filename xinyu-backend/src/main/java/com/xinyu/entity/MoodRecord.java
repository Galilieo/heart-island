package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;

import java.time.LocalDateTime;

/** 心情日记；内容版本、AI 回复与首次删除时间由服务端维护。 */
public class MoodRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户，由登录身份确定而不是客户端传入。 */
    private Long userId;

    /** 心情类型，取值见 test-contracts/mood-types.json。 */
    private String moodType;

    /** 用户原文，最多 500 个 UTF-16 单位。 */
    private String content;

    /** 当前版本的 AI 回复；未生成或已清空时为空。 */
    private String aiReply;

    /** 1 有效；0 已软删除。 */
    private Integer status;

    /** 生成依据的版本；AI 只写回复时不递增。 */
    private Long contentVersion;

    /** 当前版本是否需要 AI 回应。 */
    private Boolean aiReplyEnabled;

    /** 首次软删除的时间；历史未知时间保留空值。 */
    private LocalDateTime deletedAt;

    /** 创建时间，由数据库维护。 */
    private LocalDateTime createTime;

    /** 更新时间，由数据库或写操作维护。 */
    private LocalDateTime updateTime;

    /** 是否需要 AI 回复（仅请求传输用，不入库）。缺省 true，兼容旧客户端。 */
    @TableField(exist = false)
    private Boolean needAiReply = true;

    public Long getContentVersion() {
        return contentVersion;
    }

    public void setContentVersion(Long contentVersion) {
        this.contentVersion = contentVersion;
    }

    public Boolean getAiReplyEnabled() {
        return aiReplyEnabled;
    }

    public void setAiReplyEnabled(Boolean aiReplyEnabled) {
        this.aiReplyEnabled = aiReplyEnabled;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getAiReply() {
        return aiReply;
    }

    public void setAiReply(String aiReply) {
        this.aiReply = aiReply;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Boolean getNeedAiReply() {
        return needAiReply;
    }

    public void setNeedAiReply(Boolean needAiReply) {
        this.needAiReply = needAiReply;
    }
}
