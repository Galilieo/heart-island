package com.xinyu.controller;

import com.xinyu.common.Result;
import com.xinyu.service.AiJobService;
import com.xinyu.service.CommunityReplySource;
import com.xinyu.utils.JwtUtil;
import com.xinyu.vo.AiJobVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 社区作者的回复任务查询与手动重试。身份由社区鉴权入口校验，任务归属由 Service 校验。 */
@RestController
@RequestMapping("/community/ai-job")
public class CommunityAiJobController {
    private final AiJobService jobs;
    private final CommunityReplySource source;
    private final JwtUtil jwt;

    public CommunityAiJobController(AiJobService jobs, CommunityReplySource source, JwtUtil jwt) {
        this.jobs = jobs;
        this.source = source;
        this.jwt = jwt;
    }

    @GetMapping("/{id}")
    public Result<AiJobVO> get(@RequestHeader("token") String token, @PathVariable long id) {
        return Result.success("查询成功", jobs.getOwnedCommunity(jwt.getUserId(token), id, source));
    }

    @PostMapping("/{id}/retry")
    public Result<AiJobVO> retry(@RequestHeader("token") String token, @PathVariable long id) {
        return Result.success("任务状态已更新", jobs.retryCommunity(jwt.getUserId(token), id, source));
    }
}
