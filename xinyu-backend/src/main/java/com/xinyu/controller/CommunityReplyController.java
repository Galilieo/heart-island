package com.xinyu.controller;

import com.xinyu.common.Result;
import com.xinyu.dto.CommunityReplyAddDTO;
import com.xinyu.dto.CommunityReplyUpdateDTO;
import com.xinyu.entity.CommunityReply;
import com.xinyu.service.CommunityReplyService;
import com.xinyu.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/community/reply")
public class CommunityReplyController {

    @Autowired
    private CommunityReplyService communityReplyService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/list")
    public Result<List<CommunityReply>> list(@RequestParam(required = false) Long postId) {
        List<CommunityReply> replies = communityReplyService.listByPostId(postId);
        return Result.success(replies);
    }

    @PostMapping("/add")
    public Result<?> add(@RequestBody CommunityReplyAddDTO dto,
                         @RequestHeader("token") String token) {
        if (dto.getPostId() == null) {
            return Result.error("请选择要回复的帖子");
        }

        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            return Result.error("请写一点回复内容");
        }

        if (dto.getContent().length() > 500) {
            return Result.error("回复不能超过500个字");
        }

        Long userId = jwtUtil.getUserId(token);

        Boolean success = communityReplyService.add(dto, userId);

        if (success) {
            return Result.success("回复成功");
        }

        return Result.error("回复失败");
    }

    @PostMapping("/delete")
    public Result<?> delete(Long id, @RequestHeader("token") String token) {
        if (id == null) {
            return Result.error("请选择要删除的回复");
        }

        Long userId = jwtUtil.getUserId(token);

        Boolean success = communityReplyService.delete(id, userId);

        if (success) {
            return Result.success("删除成功");
        }

        return Result.error("删除失败");
    }

    @PostMapping("/update")
    public Result<?> update(@RequestBody CommunityReplyUpdateDTO dto,
                            @RequestHeader("token") String token) {
        if (dto.getId() == null) {
            return Result.error("请选择要修改的回复");
        }

        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            return Result.error("请写一点回复内容");
        }

        if (dto.getContent().length() > 500) {
            return Result.error("回复不能超过500个字");
        }

        Long userId = jwtUtil.getUserId(token);
        Boolean success = communityReplyService.update(dto, userId);

        if (success) {
            return Result.success("修改成功");
        }

        return Result.error("修改失败");
    }
}
