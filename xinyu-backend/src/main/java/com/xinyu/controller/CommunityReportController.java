package com.xinyu.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.Result;
import com.xinyu.dto.CommunityReportCreateDTO;
import com.xinyu.service.CommunityReportService;
import com.xinyu.utils.JwtUtil;
import com.xinyu.vo.MyReportVO;
import com.xinyu.vo.ReportedTargetVO;
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

import java.util.List;

/**
 * 普通登录用户的社区举报提交与个人举报查询接口。
 */
@RestController
@RequestMapping("/community/report")
public class CommunityReportController {

    @Autowired
    private CommunityReportService communityReportService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping
    public Result<Boolean> create(@RequestBody(required = false) CommunityReportCreateDTO dto,
                                  @RequestHeader("token") String token) {
        Long userId = jwtUtil.getUserId(token);
        communityReportService.submitReport(userId, dto);
        return Result.success("已收到举报，会尽快处理", true);
    }

    @GetMapping("/mine")
    public Result<Page<MyReportVO>> mine(@RequestHeader("token") String token,
                                         @RequestParam(required = false) Long pageNum,
                                         @RequestParam(required = false) Long pageSize) {
        Long userId = jwtUtil.getUserId(token);
        long resolvedPageNum = pageNum == null ? 1L : pageNum;
        long resolvedPageSize = pageSize == null ? 10L : pageSize;
        if (resolvedPageNum < 1 || resolvedPageSize < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不合法");
        }
        return Result.success("查询成功", communityReportService.listMine(userId, resolvedPageNum, resolvedPageSize));
    }

    @GetMapping("/reported-targets")
    public Result<List<ReportedTargetVO>> reportedTargets(@RequestHeader("token") String token,
                                                          @RequestParam Long postId) {
        if (postId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "帖子id不能为空");
        }
        Long userId = jwtUtil.getUserId(token);
        return Result.success("查询成功", communityReportService.listReportedTargets(userId, postId));
    }
}
