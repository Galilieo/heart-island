package com.xinyu.vo;

/**
 * 当前用户在指定帖子下已举报的目标摘要，用于前端「⋯」菜单禁用重复举报。
 */
public class ReportedTargetVO {

    private String targetType;
    private String targetId;
    private String status;

    public ReportedTargetVO() {
    }

    public ReportedTargetVO(String targetType, String targetId, String status) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.status = status;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
