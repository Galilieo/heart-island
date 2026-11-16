package com.xinyu.controller;

import com.xinyu.common.CommunityReportConstants;
import com.xinyu.common.Result;
import com.xinyu.service.AdminStatusResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 后台帖子/回复状态变更共用校验与结果转换，防止两套控制器出现分叉校验逻辑。
 */
final class AdminContentStatusHelper {

    private AdminContentStatusHelper() {
    }

    static String validateAndNormalizeStatusUpdate(Long id, Integer status, String hideReason, String targetLabel) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, targetLabel + "id不能为空");
        }
        if (status == null || (status != CommunityReportConstants.STATUS_VISIBLE
                && status != CommunityReportConstants.STATUS_ADMIN_HIDDEN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    targetLabel + "状态只能是1（恢复）或2（隐藏）");
        }
        if (status == CommunityReportConstants.STATUS_ADMIN_HIDDEN) {
            return validateHideReason(hideReason);
        }
        return null;
    }

    static String validateHideReason(String hideReason) {
        String normalized = hideReason == null ? "" : hideReason.trim();
        if (!CommunityReportConstants.isValidHideReason(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "隐藏内容必须选择合法的隐藏原因");
        }
        return normalized;
    }

    static Result<Boolean> toResult(AdminStatusResult outcome, String targetLabel, String successMessage) {
        if (outcome == AdminStatusResult.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, targetLabel + "不存在");
        }
        if (outcome == AdminStatusResult.CONFLICT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, targetLabel + "状态已变化，请刷新后重试");
        }
        return Result.success(successMessage, true);
    }
}
