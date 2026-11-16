package com.xinyu.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台举报详情弹窗视图，含首次快照、当前内容对比、作者被隐藏次数与全部举报记录。
 */
public class AdminReportDetailVO {

    private String targetType;
    private String targetId;
    private String postId;
    private String targetUserId;
    private Integer authorHiddenCount;
    private Integer reporterCount;
    private LocalDateTime firstReportTime;
    private Boolean hasSafety;
    private String suggestedHideReason;
    private String snapshotContent;
    private String currentContent;
    private Integer currentStatus;
    private Boolean contentChanged;
    private String currentHideReason;
    private String resultStatus;
    private String hideReason;
    private String handlerId;
    private String handlerName;
    private LocalDateTime handleTime;
    private String handleNote;
    private List<AdminReportRecordVO> records;

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public Integer getAuthorHiddenCount() {
        return authorHiddenCount;
    }

    public void setAuthorHiddenCount(Integer authorHiddenCount) {
        this.authorHiddenCount = authorHiddenCount;
    }

    public Integer getReporterCount() {
        return reporterCount;
    }

    public void setReporterCount(Integer reporterCount) {
        this.reporterCount = reporterCount;
    }

    public LocalDateTime getFirstReportTime() {
        return firstReportTime;
    }

    public void setFirstReportTime(LocalDateTime firstReportTime) {
        this.firstReportTime = firstReportTime;
    }

    public Boolean getHasSafety() {
        return hasSafety;
    }

    public void setHasSafety(Boolean hasSafety) {
        this.hasSafety = hasSafety;
    }

    public String getSuggestedHideReason() {
        return suggestedHideReason;
    }

    public void setSuggestedHideReason(String suggestedHideReason) {
        this.suggestedHideReason = suggestedHideReason;
    }

    public String getSnapshotContent() {
        return snapshotContent;
    }

    public void setSnapshotContent(String snapshotContent) {
        this.snapshotContent = snapshotContent;
    }

    public String getCurrentContent() {
        return currentContent;
    }

    public void setCurrentContent(String currentContent) {
        this.currentContent = currentContent;
    }

    public Integer getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(Integer currentStatus) {
        this.currentStatus = currentStatus;
    }

    public Boolean getContentChanged() {
        return contentChanged;
    }

    public void setContentChanged(Boolean contentChanged) {
        this.contentChanged = contentChanged;
    }

    public String getCurrentHideReason() {
        return currentHideReason;
    }

    public void setCurrentHideReason(String currentHideReason) {
        this.currentHideReason = currentHideReason;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }

    public String getHideReason() {
        return hideReason;
    }

    public void setHideReason(String hideReason) {
        this.hideReason = hideReason;
    }

    public String getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(String handlerId) {
        this.handlerId = handlerId;
    }

    public String getHandlerName() {
        return handlerName;
    }

    public void setHandlerName(String handlerName) {
        this.handlerName = handlerName;
    }

    public LocalDateTime getHandleTime() {
        return handleTime;
    }

    public void setHandleTime(LocalDateTime handleTime) {
        this.handleTime = handleTime;
    }

    public String getHandleNote() {
        return handleNote;
    }

    public void setHandleNote(String handleNote) {
        this.handleNote = handleNote;
    }

    public List<AdminReportRecordVO> getRecords() {
        return records;
    }

    public void setRecords(List<AdminReportRecordVO> records) {
        this.records = records;
    }
}
