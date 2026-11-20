package com.xinyu.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.CommunityReportConstants;
import com.xinyu.common.Result;
import com.xinyu.service.AdminStatusResult;
import com.xinyu.service.CommunityPostService;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import com.xinyu.vo.AdminPostDetailVO;
import com.xinyu.vo.AdminPostVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/post")
public class AdminPostController {

    @Autowired
    private CommunityPostService communityPostService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/page")
    public Result<Page<AdminPostVO>> page(@RequestHeader(value = "token", required = false) String token,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false) Long topicId,
                                          @RequestParam(required = false) Long postId,
                                          @RequestParam(required = false) Long userId,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String resultKind,
                                          @RequestParam(required = false) String riskStatus,
                                          @RequestParam(required = false) Long pageNum,
                                          @RequestParam(required = false) Long pageSize) {
        Long adminId = AdminContentStatusHelper.resolveAdminId(token, jwtUtil, userService);

        if (adminId == null) {
            return Result.error("权限不足");
        }

        if (pageNum == null) {
            pageNum = 1L;
        }

        if (pageSize == null) {
            pageSize = 10L;
        }

        if (pageNum < 1) {
            return Result.error("页码不能小于1");
        }

        if (pageSize < 1) {
            return Result.error("每页条数不能小于1");
        }

        if (resultKind != null && !"MODEL".equals(resultKind) && !"SUPPORT".equals(resultKind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "回复类型不合法");
        }

        AdminContentStatusHelper.validateRiskStatus(riskStatus);

        Page<AdminPostVO> page = communityPostService.pageAdminPosts(
                status,
                topicId,
                postId,
                userId,
                keyword,
                resultKind,
                riskStatus,
                pageNum,
                pageSize
        );

        return Result.success("查询成功", page);
    }

    @GetMapping("/{id}")
    public Result<AdminPostDetailVO> detail(@RequestHeader(value = "token", required = false) String token,
                                            @PathVariable Long id) {
        Long adminId = AdminContentStatusHelper.resolveAdminId(token, jwtUtil, userService);

        if (adminId == null) {
            return Result.error("权限不足");
        }

        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "帖子id不能为空");
        }

        AdminPostDetailVO detail = communityPostService.getAdminDetail(id);
        if (detail == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在");
        }

        return Result.success("查询成功", detail);
    }

    /** 管理员将一条待关注帖子标为已关注。 */
    @PutMapping("/{id}/risk-review")
    public Result<Boolean> reviewRisk(
            @RequestHeader(value = "token", required = false) String token, @PathVariable Long id) {
        if (AdminContentStatusHelper.resolveAdminId(token, jwtUtil, userService) == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }

        AdminStatusResult outcome = communityPostService.reviewRisk(id);
        return AdminContentStatusHelper.toRiskReviewResult(outcome, "帖子");
    }

    @PutMapping("/{id}/status")
    public Result<Boolean> updateStatus(@RequestHeader(value = "token", required = false) String token,
                                        @PathVariable Long id,
                                        @RequestParam Integer status,
                                        @RequestParam(required = false) String hideReason) {
        Long adminId = AdminContentStatusHelper.resolveAdminId(token, jwtUtil, userService);

        if (adminId == null) {
            return Result.error("权限不足");
        }

        String normalizedReason = AdminContentStatusHelper.validateAndNormalizeStatusUpdate(
                id, status, hideReason, "帖子");
        AdminStatusResult outcome = communityPostService.updateAdminStatusWithOutcome(id, status, normalizedReason);
        return AdminContentStatusHelper.toResult(outcome, "帖子", "修改成功");
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@RequestHeader(value = "token", required = false) String token,
                                  @PathVariable Long id,
                                  @RequestParam(required = false) String hideReason) {
        Long adminId = AdminContentStatusHelper.resolveAdminId(token, jwtUtil, userService);

        if (adminId == null) {
            return Result.error("权限不足");
        }

        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "帖子id不能为空");
        }

        String normalizedReason = AdminContentStatusHelper.validateHideReason(hideReason);
        AdminStatusResult outcome = communityPostService.updateAdminStatusWithOutcome(
                id, CommunityReportConstants.STATUS_ADMIN_HIDDEN, normalizedReason);
        return AdminContentStatusHelper.toResult(outcome, "帖子", "已隐藏");
    }
}
