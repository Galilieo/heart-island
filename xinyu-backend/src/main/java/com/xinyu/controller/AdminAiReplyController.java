package com.xinyu.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.Result;
import com.xinyu.entity.AiReply;
import com.xinyu.service.AiReplyLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ai-replies")
public class AdminAiReplyController {

    @Autowired
    private AiReplyLogService aiReplyLogService;

    @GetMapping("/page")
    public Result<Page<AiReply>> page(@RequestParam(required = false) Long userId,
                                      @RequestParam(required = false) String targetType,
                                      @RequestParam(required = false) Integer status,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "1") Long pageNum,
                                      @RequestParam(defaultValue = "10") Long pageSize){
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
}
