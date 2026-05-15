package com.xinyu.controller;

import com.xinyu.common.Result;
import com.xinyu.entity.MoodRecord;
import com.xinyu.service.MoodRecordService;
import com.xinyu.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/mood")
public class MoodRecordController {

    @Autowired
    private MoodRecordService moodRecordService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/add")
    public Result<Boolean> add(@RequestHeader(required = false) String token,
                               @RequestBody MoodRecord moodRecord) {
        if (token == null || token.trim().isEmpty()) {
            return Result.error("请先登录");
        }

        if (moodRecord.getMoodType() == null || moodRecord.getMoodType().trim().isEmpty()) {
            return Result.error("心情类型不能为空");
        }

        if (moodRecord.getContent() == null || moodRecord.getContent().trim().isEmpty()) {
            return Result.error("心情内容不能为空");
        }

        Long userId = jwtUtil.getUserId(token);
        moodRecord.setUserId(userId);

        Boolean success = moodRecordService.add(moodRecord);

        if (!success) {
            return Result.error("新增心情记录失败");
        }

        return Result.success("新增成功", true);
    }

    @GetMapping("/list")
    public Result<List<MoodRecord>> list(@RequestHeader(required = false) String token) {
        if (token == null || token.trim().isEmpty()) {
            return Result.error("请先登录");
        }

        Long userId = jwtUtil.getUserId(token);

        List<MoodRecord> list = moodRecordService.listByUserId(userId);
        return Result.success("查询成功", list);
    }

    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@RequestHeader(required = false) String token,
                                  @PathVariable Long id) {
        if (token == null || token.trim().isEmpty()) {
            return Result.error("请先登录");
        }

        if (id == null) {
            return Result.error("记录id不能为空");
        }

        Boolean success = moodRecordService.delete(id);

        if (!success) {
            return Result.error("删除失败");
        }

        return Result.success("删除成功", true);
    }


}