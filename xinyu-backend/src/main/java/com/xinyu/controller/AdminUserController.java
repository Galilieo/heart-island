package com.xinyu.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.Result;
import com.xinyu.dto.UserStatusUpdateDTO;
import com.xinyu.entity.User;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import com.xinyu.vo.AdminUserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/user")
public class AdminUserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/page")
    public Result<Page<AdminUserVO>> page(@RequestHeader(value = "token", required = false) String token,
                                          @RequestParam(required = false) String username,
                                          @RequestParam(required = false) Integer status,
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

        Page<AdminUserVO> page = userService.pageUsers(username, status, pageNum, pageSize);
        return Result.success("查询成功", page);
    }

    @PutMapping("/{id}/status")
    public Result<AdminUserVO> updateStatus(@RequestHeader(value = "token", required = false) String token,
                                            @PathVariable Long id,
                                            @RequestBody UserStatusUpdateDTO dto) {
        Long adminId = getAdminId(token);

        if (adminId == null) {
            return Result.error("权限不足");
        }

        if (id == null) {
            return Result.error("用户id不能为空");
        }

        if (adminId.equals(id)) {
            return Result.error("不能禁用或启用当前管理员账号");
        }

        if (dto.getStatus() == null || (dto.getStatus() != 0 && dto.getStatus() != 1)) {
            return Result.error("用户状态只能是0或1");
        }

        User user = userService.updateStatus(id, dto.getStatus());

        if (user == null) {
            return Result.error("用户不存在");
        }

        AdminUserVO userVO = new AdminUserVO(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getNickname(),
                user.getRole(),
                user.getStatus(),
                user.getCreateTime(),
                user.getUpdateTime()
        );

        return Result.success("修改成功", userVO);
    }

    private Long getAdminId(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        Long userId = jwtUtil.getUserId(token);
        return userService.isAdmin(userId) ? userId : null;
    }
}
