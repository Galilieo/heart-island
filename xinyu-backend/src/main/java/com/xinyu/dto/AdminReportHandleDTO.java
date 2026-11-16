package com.xinyu.dto;

/**
 * 管理员处理举报请求体；目标类型、动作与隐藏原因取值见 {@link com.xinyu.common.CommunityReportConstants}。
 */
public class AdminReportHandleDTO {

    private String targetType;
    private Long targetId;
    private String action;
    private String hideReason;
    private String note;

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

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getHideReason() {
        return hideReason;
    }

    public void setHideReason(String hideReason) {
        this.hideReason = hideReason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
