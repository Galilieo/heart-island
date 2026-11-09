package com.xinyu.vo;

/**
 * 心情保存结果；保存成功与 AI 任务完成分别表达。
 */
public class MoodSaveVO {

    /** 已保存的心情记录 ID。 */
    private Long id;

    /** 本次保存后的来源版本。 */
    private Long contentVersion;

    /** 本版本是否需要 AI 回应。 */
    private Boolean aiReplyEnabled;

    /** 当前回应状态，由来源意愿和任务状态共同决定。 */
    private String aiReplyState;

    /** 关联任务 ID；关闭 AI 回应时为空。 */
    private Long jobId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getAiReplyState() {
        return aiReplyState;
    }

    public void setAiReplyState(String aiReplyState) {
        this.aiReplyState = aiReplyState;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }
}
