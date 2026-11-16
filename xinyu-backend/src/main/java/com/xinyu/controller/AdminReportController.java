package com.xinyu.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.Result;
import com.xinyu.dto.AdminReportHandleDTO;
import com.xinyu.service.CommunityReportService;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import com.xinyu.vo.AdminReportDetailVO;
import com.xinyu.vo.AdminReportQueueRowVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 后台管理员举报队列、详情、裁决与待处理计数接口。
 */
@RestController
@RequestMapping("/admin/report")
public class AdminReportController {

    @Autowired
    private CommunityReportService communityReportService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/page")
    public Result<Page<AdminReportQueueRowVO>> page(@RequestHeader(value = "token", required = false) String token,
                                                    @RequestParam(defaultValue = "PENDING") String status,
                                                    @RequestParam(required = false) Long pageNum,
                                                    @RequestParam(required = false) Long pageSize) {
        if (getAdminId(token) == null) {
            return Result.error("权限不足");
        }
        long resolvedPageNum = pageNum == null ? 1L : pageNum;
        long resolvedPageSize = pageSize == null ? 10L : pageSize;
        if (resolvedPageNum < 1 || resolvedPageSize < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不合法");
        }
        return Result.success("查询成功",
                communityReportService.pageAdminReports(status, resolvedPageNum, resolvedPageSize));
    }

    @GetMapping("/detail")
    public Result<AdminReportDetailVO> detail(@RequestHeader(value = "token", required = false) String token,
                                              @RequestParam String targetType,
                                              @RequestParam Long targetId) {
        if (getAdminId(token) == null) {
            return Result.error("权限不足");
        }
        AdminReportDetailVO detail = communityReportService.getAdminReportDetail(targetType, targetId);
        if (detail == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "举报记录不存在");
        }
        return Result.success("查询成功", detail);
    }

    @PostMapping("/handle")
    public Result<Boolean> handle(@RequestHeader(value = "token", required = false) String token,
                                  @RequestBody(required = false) AdminReportHandleDTO dto) {
        Long adminId = getAdminId(token);
        if (adminId == null) {
            return Result.error("权限不足");
        }
        String message = communityReportService.handleReport(adminId, dto);
        return Result.success(message, true);
    }

    @GetMapping("/pending-count")
    public Result<Long> pendingCount(@RequestHeader(value = "token", required = false) String token) {
        if (getAdminId(token) == null) {
            return Result.error("权限不足");
        }
        return Result.success("查询成功", communityReportService.getPendingTargetCount());
    }

    private Long getAdminId(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        Long userId = jwtUtil.getUserId(token);
        return userService.isAdmin(userId) ? userId : null;
    }
}
