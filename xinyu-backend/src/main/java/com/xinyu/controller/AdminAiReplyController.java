package com.xinyu.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.Result;
import com.xinyu.entity.AiReply;
import com.xinyu.service.AiReplyLogService;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ai-replies")
public class AdminAiReplyController {

    @Autowired
    private AiReplyLogService aiReplyLogService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/page")
    public Result<Page<AiReply>> page(@RequestHeader(value = "token", required = false) String token,
                                      @RequestParam(required = false) Long userId,
                                      @RequestParam(required = false) String targetType,
                                      @RequestParam(required = false) Integer status,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "1") Long pageNum,
                                      @RequestParam(defaultValue = "10") Long pageSize){
        Long adminId = getAdminId(token);

        if (adminId == null) {
            return Result.error("权限不足");
        }

        Page<AiReply> page = aiReplyLogService.pageAdminAiReplies(
                userId,
                targetType,
                status,
                keyword,
                pageNum,
                pageSize
        );

        return Result.success(page);
    }

    private Long getAdminId(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        Long userId = jwtUtil.getUserId(token);
        return userService.isAdmin(userId) ? userId : null;
    }
}
