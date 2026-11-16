package com.xinyu.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.CommunityReportConstants;
import com.xinyu.common.Result;
import com.xinyu.service.AdminStatusResult;
import com.xinyu.service.CommunityReplyService;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import com.xinyu.vo.AdminReplyDetailVO;
import com.xinyu.vo.AdminReplyVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/reply")
public class AdminReplyController {

    @Autowired
    private CommunityReplyService communityReplyService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/page")
    public Result<Page<AdminReplyVO>> page(@RequestHeader(value = "token", required = false) String token,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) Long postId,
                                           @RequestParam(required = false) Long userId,
                                           @RequestParam(required = false) String keyword,
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

        Page<AdminReplyVO> page = communityReplyService.pageAdminReplies(
                status,
                postId,
                userId,
                keyword,
                pageNum,
                pageSize
        );

        return Result.success("查询成功", page);
    }

    @GetMapping("/{id}")
    public Result<AdminReplyDetailVO> detail(@RequestHeader(value = "token", required = false) String token,
                                             @PathVariable Long id) {
        Long adminId = AdminContentStatusHelper.resolveAdminId(token, jwtUtil, userService);

        if (adminId == null) {
            return Result.error("权限不足");
        }

        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "回复id不能为空");
        }

        AdminReplyDetailVO detail = communityReplyService.getAdminDetail(id);
        if (detail == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "回复不存在");
        }

        return Result.success("查询成功", detail);
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
                id, status, hideReason, "回复");
        AdminStatusResult outcome = communityReplyService.updateAdminStatusWithOutcome(id, status, normalizedReason);
        return AdminContentStatusHelper.toResult(outcome, "回复", "修改成功");
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "回复id不能为空");
        }

        String normalizedReason = AdminContentStatusHelper.validateHideReason(hideReason);
        AdminStatusResult outcome = communityReplyService.updateAdminStatusWithOutcome(
                id, CommunityReportConstants.STATUS_ADMIN_HIDDEN, normalizedReason);
        return AdminContentStatusHelper.toResult(outcome, "回复", "已隐藏");
    }
}
