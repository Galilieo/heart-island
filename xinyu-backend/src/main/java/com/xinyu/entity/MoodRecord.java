package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;

import java.time.LocalDateTime;

public class MoodRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String moodType;
    private String content;
    private String aiReply;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 生成依据的版本；AI 只写回复时不递增。 */
    private Long contentVersion;
    /** 当前版本是否需要 AI 回应。 */
    private Boolean aiReplyEnabled;
    /** 首次软删除的时间；历史未知时间保留空值。 */
    private LocalDateTime deletedAt;

    public Long getContentVersion() { return contentVersion; }
    public void setContentVersion(Long contentVersion) { this.contentVersion = contentVersion; }
    public Boolean getAiReplyEnabled() { return aiReplyEnabled; }
    public void setAiReplyEnabled(Boolean aiReplyEnabled) { this.aiReplyEnabled = aiReplyEnabled; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

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

    /** 是否需要 AI 回复（仅请求传输用，不入库）。缺省 true，兼容旧客户端。 */
    @TableField(exist = false)
    private Boolean needAiReply = true;

    public Boolean getNeedAiReply() {
        return needAiReply;
    }

    public void setNeedAiReply(Boolean needAiReply) {
        this.needAiReply = needAiReply;
    }
}
