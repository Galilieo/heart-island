package com.xinyu.controller;

import com.xinyu.common.CommunityReportConstants;
import com.xinyu.common.Result;
import com.xinyu.service.AdminStatusResult;
import com.xinyu.service.CommunityRisk;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 后台管理控制器共用鉴权、状态校验与结果转换，防止各控制器出现分叉逻辑。
 */
final class AdminContentStatusHelper {

    private AdminContentStatusHelper() {
    }

    /**
     * 解析请求头中的管理员身份；token 为空或用户无 ADMIN 角色时返回 null。
     */
    static Long resolveAdminId(String token, JwtUtil jwtUtil, UserService userService) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        Long userId = jwtUtil.getUserId(token);
        return userService.isAdmin(userId) ? userId : null;
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

    static void validateRiskStatus(String riskStatus) {
        if (riskStatus != null && !CommunityRisk.PENDING.equals(riskStatus)
                && !CommunityRisk.REVIEWED.equals(riskStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关注状态不合法");
        }
    }

    static Result<Boolean> toRiskReviewResult(AdminStatusResult outcome, String targetLabel) {
        if (outcome == AdminStatusResult.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, targetLabel + "不存在");
        }
        if (outcome == AdminStatusResult.CONFLICT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, targetLabel + "已不在待关注状态，列表已变化");
        }
        return Result.success("已标为关注", true);
    }
}
