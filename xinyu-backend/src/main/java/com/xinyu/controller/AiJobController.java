package com.xinyu.controller;

import com.xinyu.common.Result;
import com.xinyu.service.AiJobService;
import com.xinyu.utils.JwtUtil;
import com.xinyu.vo.AiJobVO;
import org.springframework.web.bind.annotation.*;

/** 心情回应任务入口；复用心情模块的登录拦截器，归属及来源状态由服务校验。 */
@RestController
@RequestMapping("/mood/ai-job")
public class AiJobController {
    private final AiJobService jobs;
    private final JwtUtil jwt;

    public AiJobController(AiJobService jobs, JwtUtil jwt) {
        this.jobs = jobs;
        this.jwt = jwt;
    }

    @GetMapping("/{id}")
    public Result<AiJobVO> get(@RequestHeader("token") String token, @PathVariable long id) {
        return Result.success("查询成功", jobs.getOwned(jwt.getUserId(token), id));
    }

    @PostMapping("/{id}/retry")
    public Result<AiJobVO> retry(@RequestHeader("token") String token, @PathVariable long id) {
        return Result.success("任务状态已更新", jobs.retry(jwt.getUserId(token), id));
    }
}
