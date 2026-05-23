package com.xinyu.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xinyu.common.Result;
import com.xinyu.entity.AiReply;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.CommunityReply;
import com.xinyu.entity.User;
import com.xinyu.mapper.AiReplyMapper;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import com.xinyu.mapper.UserMapper;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import com.xinyu.vo.AdminOverviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/overview")
public class AdminOverviewController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CommunityPostMapper communityPostMapper;

    @Autowired
    private CommunityReplyMapper communityReplyMapper;

    @Autowired
    private AiReplyMapper aiReplyMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping
    public Result<AdminOverviewVO> overview(
            @RequestHeader(value = "token", required = false) String token
    ) {
        Long adminId = getAdminId(token);

        if (adminId == null) {
            return Result.error("权限不足");
        }

        Long userTotal = userMapper.selectCount(null);
        Long userDisabled = userMapper.selectCount(
                new QueryWrapper<User>().eq("status", 0)
        );

        Long postTotal = communityPostMapper.selectCount(null);
        Long postHidden = communityPostMapper.selectCount(
                new QueryWrapper<CommunityPost>().eq("status", 0)
        );

        Long replyTotal = communityReplyMapper.selectCount(null);
        Long replyHidden = communityReplyMapper.selectCount(
                new QueryWrapper<CommunityReply>().eq("status", 0)
        );

        Long aiReplyTotal = aiReplyMapper.selectCount(null);
        Long aiReplySuccess = aiReplyMapper.selectCount(
                new QueryWrapper<AiReply>().eq("status", 1)
        );
        Long aiReplyFailed = aiReplyMapper.selectCount(
                new QueryWrapper<AiReply>().eq("status", 0)
        );

        AdminOverviewVO overview = new AdminOverviewVO(
                userTotal,
                userDisabled,
                postTotal,
                postHidden,
                replyTotal,
                replyHidden,
                aiReplyTotal,
                aiReplySuccess,
                aiReplyFailed
        );

        return Result.success("查询成功", overview);
    }

    private Long getAdminId(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        Long userId = jwtUtil.getUserId(token);
        return userService.isAdmin(userId) ? userId : null;
    }
}
