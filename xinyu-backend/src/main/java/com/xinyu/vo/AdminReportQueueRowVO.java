package com.xinyu.vo;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 后台举报队列按内容对象聚合后的列表行视图。
 */
public class AdminReportQueueRowVO {

    private String targetType;
    private String targetId;
    private String postId;
    private String excerpt;
    private Integer reporterCount;
    private Map<String, Integer> reasonCounts;
    private Boolean hasSafety;
    private LocalDateTime firstReportTime;
    private String targetUserId;
    private String resultStatus;
    private String hideReason;
    private String handlerId;
    private String handlerName;
    private LocalDateTime handleTime;
    private String handleNote;

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

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public Integer getReporterCount() {
        return reporterCount;
    }

    public void setReporterCount(Integer reporterCount) {
        this.reporterCount = reporterCount;
    }

    public Map<String, Integer> getReasonCounts() {
        return reasonCounts;
    }

    public void setReasonCounts(Map<String, Integer> reasonCounts) {
        this.reasonCounts = reasonCounts;
    }

    public Boolean getHasSafety() {
        return hasSafety;
    }

    public void setHasSafety(Boolean hasSafety) {
        this.hasSafety = hasSafety;
    }

    public LocalDateTime getFirstReportTime() {
        return firstReportTime;
    }

    public void setFirstReportTime(LocalDateTime firstReportTime) {
        this.firstReportTime = firstReportTime;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
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
}
