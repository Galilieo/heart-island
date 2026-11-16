package com.xinyu.dto;

/**
 * 社区举报提交请求体；目标类型与原因枚举见 {@link com.xinyu.common.CommunityReportConstants}。
 */
public class CommunityReportCreateDTO {

    private String targetType;
    private Long targetId;
    private String reason;
    private String detail;

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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
