package com.xinyu.controller;

import com.xinyu.common.Result;
import com.xinyu.service.MoodReviewStatsService;
import com.xinyu.utils.JwtUtil;
import com.xinyu.vo.MoodReviewStatsVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 回顾页统计接口；只读，不产生模型调用，也不写库。 */
@RestController
@RequestMapping("/mood")
public class MoodReviewController {

    private final MoodReviewStatsService moodReviewStatsService;
    private final JwtUtil jwtUtil;

    public MoodReviewController(MoodReviewStatsService moodReviewStatsService, JwtUtil jwtUtil) {
        this.moodReviewStatsService = moodReviewStatsService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 周统计。start 缺省时取上一个完整自然周；只允许周一，且不能是未来的周。
     * 本周只返回统计，不生成报告。
     */
    @GetMapping("/report/week")
    public Result<MoodReviewStatsVO> week(@RequestHeader(value = "token", required = false) String token,
                                          @RequestParam(required = false) String start) {
        Long userId = currentUserId(token);
        if (userId == null) {
            return Result.error("请先登录");
        }

        try {
            return Result.success("查询成功", moodReviewStatsService.getWeekStats(userId, start));
        } catch (IllegalArgumentException invalid) {
            return Result.error(invalid.getMessage());
        }
    }

    /** 近 30 天统计；当前只支持 range=30d。 */
    @GetMapping("/analysis")
    public Result<MoodReviewStatsVO> analysis(@RequestHeader(value = "token", required = false) String token,
                                              @RequestParam(required = false, defaultValue = "30d") String range) {
        Long userId = currentUserId(token);
        if (userId == null) {
            return Result.error("请先登录");
        }

        if (range != null && !range.isBlank() && !"30d".equals(range)) {
            return Result.error("不支持的统计范围");
        }

        return Result.success("查询成功", moodReviewStatsService.getRecent30Stats(userId));
    }

    /** 从请求头 token 解析登录用户；缺失或空白返回 null。 */
    private Long currentUserId(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        return jwtUtil.getUserId(token);
    }
}
