package com.xinyu.mapper;

import java.time.LocalDateTime;

/**
 * 后台举报错按内容对象聚合的分组查询投影。
 */
public class ReportTargetGroup {

    private String targetType;
    private Long targetId;
    private Long postId;
    private Long targetUserId;
    private Integer reporterCount;
    private Integer hasSafety;
    private LocalDateTime firstReportTime;
    private LocalDateTime latestHandleTime;

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(Long targetUserId) {
        this.targetUserId = targetUserId;
    }

    public Integer getReporterCount() {
        return reporterCount;
    }

    public void setReporterCount(Integer reporterCount) {
        this.reporterCount = reporterCount;
    }

    public Integer getHasSafety() {
        return hasSafety;
    }

    public void setHasSafety(Integer hasSafety) {
        this.hasSafety = hasSafety;
    }

    public LocalDateTime getFirstReportTime() {
        return firstReportTime;
    }

    public void setFirstReportTime(LocalDateTime firstReportTime) {
        this.firstReportTime = firstReportTime;
    }

    public LocalDateTime getLatestHandleTime() {
        return latestHandleTime;
    }

    public void setLatestHandleTime(LocalDateTime latestHandleTime) {
        this.latestHandleTime = latestHandleTime;
    }
}
