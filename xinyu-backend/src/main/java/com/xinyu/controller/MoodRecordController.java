package com.xinyu.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.Result;
import com.xinyu.entity.MoodRecord;
import com.xinyu.service.MoodRecordService;
import com.xinyu.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


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
    public Result<Page<MoodRecord>> list(@RequestHeader(required = false) String token,
                                         @RequestParam(required = false) String moodType,
                                         @RequestParam(required = false) String startDate,
                                         @RequestParam(required = false) String endDate,
                                         @RequestParam(required = false) Long pageNum,
                                         @RequestParam(required = false) Long pageSize) {
        if (token == null || token.trim().isEmpty()) {
            return Result.error("请先登录");
        }
        if (pageNum == null) {
            pageNum = 1L;
        }

        if (pageSize == null) {
            pageSize = 5L;
        }
        if(pageNum<1){
            return Result.error("页码不能小于1");
        }

        if (pageSize<1){
            return Result.error("每页条数不能小于1");
        }

        Long userId = jwtUtil.getUserId(token);

        Page<MoodRecord> page = moodRecordService.listByCondition(userId, moodType, startDate, endDate, pageNum, pageSize);
        return Result.success("查询成功", page);
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
        Long userId = jwtUtil.getUserId(token);

        Boolean success = moodRecordService.delete(id, userId);

        if (!success) {
            return Result.error("心情记录不存在或者无权删除");
        }

        return Result.success("删除成功", true);
    }
    @GetMapping("/detail/{id}")
    public Result<MoodRecord> detail(@RequestHeader(required = false) String token,
                                    @PathVariable Long id) {
        if(token ==null||token.trim().isEmpty()){
            return Result.error("请先登录");
        }
        if(id == null){
            return Result.error("记录id不能为空");
        }

        Long userId = jwtUtil.getUserId(token);

        MoodRecord moodRecord = moodRecordService.getDetail(id,userId);

        if(moodRecord==null){
            return Result.error("心情记录不存在或者无权查看");
        }

        return Result.success("查询成功", moodRecord);
    }

    @PutMapping("/update/{id}")
    public Result<Boolean> update(@RequestHeader(required = false) String token,
                                  @PathVariable Long id,
                                  @RequestBody MoodRecord moodRecord) {
        if (token == null || token.trim().isEmpty()) {
            return Result.error("请先登录");
        }

        if(id == null){
            return Result.error("记录id不能为空");
        }

        if(moodRecord.getMoodType()==null||moodRecord.getContent().trim().isEmpty()){
            return Result.error("心情内容不能为空");
        }

        Long userId = jwtUtil.getUserId(token);
        Boolean success =moodRecordService.update(id,userId,moodRecord);

        if (!success) {
            return Result.error("心情记录不存在或者无权修改");
        }

        return Result.success("修改成功", true);
    }
}