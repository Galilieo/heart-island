package com.xinyu.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.Result;
import com.xinyu.entity.CommunityPost;
import com.xinyu.service.CommunityPostService;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import com.xinyu.vo.AdminPostVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
                                          @RequestParam(required = false) Long pageNum,
                                          @RequestParam(required = false) Long pageSize) {
        Long adminId = getAdminId(token);

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

        Page<AdminPostVO> page = communityPostService.pageAdminPosts(
                status,
                topicId,
                postId,
                userId,
                keyword,
                pageNum,
                pageSize
        );

        return Result.success("查询成功", page);
    }

    @PutMapping("/{id}/status")
    public Result<Boolean> updateStatus(@RequestHeader(value = "token", required = false) String token,
                                        @PathVariable Long id,
                                        @RequestParam Integer status) {
        Long adminId = getAdminId(token);

        if (adminId == null) {
            return Result.error("权限不足");
        }

        if (id == null) {
            return Result.error("帖子id不能为空");
        }

        if (status == null || (status != 0 && status != 1)) {
            return Result.error("帖子状态只能是0或1");
        }

        CommunityPost post = communityPostService.updateAdminStatus(id, status);

        if (post == null) {
            return Result.error("帖子不存在");
        }

        return Result.success("修改成功", true);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@RequestHeader(value = "token", required = false) String token,
                                  @PathVariable Long id) {
        Long adminId = getAdminId(token);

        if (adminId == null) {
            return Result.error("权限不足");
        }

        if (id == null) {
            return Result.error("帖子id不能为空");
        }

        CommunityPost post = communityPostService.updateAdminStatus(id, 0);

        if (post == null) {
            return Result.error("帖子不存在");
        }

        return Result.success("删除成功", true);
    }

    private Long getAdminId(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        Long userId = jwtUtil.getUserId(token);
        return userService.isAdmin(userId) ? userId : null;
    }
}
