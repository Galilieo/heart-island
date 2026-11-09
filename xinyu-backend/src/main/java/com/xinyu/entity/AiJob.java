package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * AI 异步任务的持久化元数据，来源与最终回复由业务表保存。
 */
@TableName("ai_job")
public class AiJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户，由服务端身份确定。 */
    private Long userId;

    /** 来源日记 ID；任务不复制日记正文。 */
    private Long targetId;

    /** 任务类型，当前仅 MOOD_REPLY。 */
    private String taskType;

    /** 创建任务时的来源版本，用于拒绝迟到结果。 */
    private Long targetVersion;

    /** 任务状态：queued、running、succeeded、failed、cancelled、superseded。 */
    private String status;

    /** 执行尝试次数，领取时递增，不等于供应商调用次数。 */
    private Integer attemptCount;

    /** 每次领取生成新的追踪标识，与尝试次数共同识别本次执行。 */
    private String requestId;

    private String templateVersion;

    private String modelName;

    /** 结果类型：MODEL 或 SUPPORT；未取得结果时为空。 */
    private String resultKind;

    /** 是否已发起供应商调用；无法确认时保留 null。 */
    private Boolean modelCalled;

    /** 仅保存错误分类，不保存上游正文或日记内容。 */
    private String lastErrorCode;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 尚未运行时为空。 */
    private LocalDateTime startedAt;

    /** 尚未结束时为空。 */
    private LocalDateTime finishedAt;

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

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Long getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(Long targetVersion) {
        this.targetVersion = targetVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTemplateVersion() {
        return templateVersion;
    }

    public void setTemplateVersion(String templateVersion) {
        this.templateVersion = templateVersion;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getResultKind() {
        return resultKind;
    }

    public void setResultKind(String resultKind) {
        this.resultKind = resultKind;
    }

    public Boolean getModelCalled() {
        return modelCalled;
    }

    public void setModelCalled(Boolean modelCalled) {
        this.modelCalled = modelCalled;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
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

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
