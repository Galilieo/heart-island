package com.xinyu.controller;

import com.xinyu.common.Result;
import com.xinyu.service.MoodReviewService;
import com.xinyu.utils.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 回顾报告接口；用户由登录 token 决定，GET 只读取统计与已保存报告。 */
@RestController
@RequestMapping("/mood")
public class MoodReviewController {
    private final MoodReviewService reviews;
    private final JwtUtil jwtUtil;

    public MoodReviewController(MoodReviewService reviews, JwtUtil jwtUtil) {
        this.reviews = reviews;
        this.jwtUtil = jwtUtil;
    }

    public record GenerateRequest(String start) { }

    @GetMapping("/report/week")
    public Result<Map<String, Object>> week(@RequestHeader(value = "token", required = false) String token,
                                            @RequestParam(required = false) String start) {
        try {
            return Result.success("查询成功", page(reviews.get(currentUserId(token), "WEEK", start)));
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }

    @GetMapping("/analysis")
    public Result<Map<String, Object>> analysis(@RequestHeader(value = "token", required = false) String token,
                                                @RequestParam(defaultValue = "30d") String range) {
        long user = currentUserId(token);
        if (!"30d".equals(range)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_range");
        }
        return Result.success("查询成功", page(reviews.get(user, "RECENT_30", null)));
    }

    @PostMapping("/report/week/generate")
    public Result<MoodReviewService.Report> generate(@RequestHeader(value = "token", required = false) String token,
                                                      @RequestBody(required = false) GenerateRequest request) {
        return generateWeek(token, request, false);
    }

    @PostMapping("/report/week/regenerate")
    public Result<MoodReviewService.Report> regenerate(@RequestHeader(value = "token", required = false) String token,
                                                        @RequestBody(required = false) GenerateRequest request) {
        return generateWeek(token, request, true);
    }

    @PostMapping("/analysis/generate")
    public Result<MoodReviewService.Report> generateRecent(@RequestHeader(value = "token", required = false) String token) {
        return Result.success("生成任务已保存", reviews.generate(currentUserId(token), "RECENT_30", null, false));
    }

    @GetMapping("/report/week/history")
    public Result<List<MoodReviewService.History>> history(@RequestHeader(value = "token", required = false) String token,
                                                           @RequestParam(defaultValue = "1") int page) {
        return Result.success("查询成功", reviews.history(currentUserId(token), page));
    }

    private Result<MoodReviewService.Report> generateWeek(String token, GenerateRequest request, boolean regenerate) {
        try {
            return Result.success("生成任务已保存", reviews.generate(currentUserId(token), "WEEK",
                    request == null ? null : request.start(), regenerate));
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }

    /** 保留第1步统计字段在顶层，只追加 report/active，现有统计页面继续可读。 */
    private Map<String, Object> page(MoodReviewService.Page value) {
        var result = new LinkedHashMap<String, Object>();
        var stats = value.stats();
        result.put("rangeStart", stats.rangeStart());
        result.put("rangeEnd", stats.rangeEnd());
        result.put("total", stats.total());
        result.put("daysWithRecords", stats.daysWithRecords());
        result.put("days", stats.days());
        result.put("moodCounts", stats.moodCounts());
        result.put("report", value.report());
        result.put("active", value.active());
        return result;
    }

    private long currentUserId(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login_required");
        }
        Long user = jwtUtil.getUserId(token);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login_required");
        }
        return user;
    }
}
