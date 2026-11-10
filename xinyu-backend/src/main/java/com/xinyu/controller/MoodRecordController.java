package com.xinyu.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.Result;
import com.xinyu.dto.MoodRecordAddRequest;
import com.xinyu.dto.MoodRecordUpdateRequest;
import com.xinyu.entity.MoodRecord;
import com.xinyu.service.MoodRecordService;
import com.xinyu.utils.JwtUtil;
import com.xinyu.vo.MoodRecordVO;
import com.xinyu.vo.MoodSaveVO;
import com.xinyu.vo.MoodTrendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 心情日记接口；登录身份从请求头 token 解析，业务校验与归属由 Service 承担。 */
@RestController
@RequestMapping("/mood")
public class MoodRecordController {

    @Autowired
    private MoodRecordService moodRecordService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/add")
    public Result<MoodSaveVO> add(@RequestHeader(value = "token", required = false) String token,
                                  @RequestBody MoodRecordAddRequest request) {
        Long userId = currentUserId(token);
        if (userId == null) {
            return Result.error("请先登录");
        }

        if (request == null) {
            return Result.error("请求参数不能为空");
        }

        if (request.getMoodType() == null || request.getMoodType().trim().isEmpty()) {
            return Result.error("心情类型不能为空");
        }

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            return Result.error("心情内容不能为空");
        }

        MoodRecord moodRecord = moodInput(request.getMoodType(), request.getContent(), request.getNeedAiReply());
        moodRecord.setUserId(userId);

        try {
            return Result.success("心情已保存", moodRecordService.add(moodRecord));
        } catch (IllegalArgumentException invalid) {
            return Result.error(invalid.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<Page<MoodRecordVO>> list(@RequestHeader(value = "token", required = false) String token,
                                           @RequestParam(required = false) String moodType,
                                           @RequestParam(required = false) String startDate,
                                           @RequestParam(required = false) String endDate,
                                           @RequestParam(required = false) Long pageNum,
                                           @RequestParam(required = false) Long pageSize) {
        Long userId = currentUserId(token);
        if (userId == null) {
            return Result.error("请先登录");
        }

        if (pageNum == null) {
            pageNum = 1L;
        }

        if (pageSize == null) {
            pageSize = 5L;
        }

        if (pageNum < 1) {
            return Result.error("页码不能小于1");
        }

        if (pageSize < 1) {
            return Result.error("每页条数不能小于1");
        }

        Page<MoodRecordVO> page = moodRecordService.listViews(userId, moodType, startDate, endDate, pageNum, pageSize);
        return Result.success("查询成功", page);
    }

    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@RequestHeader(value = "token", required = false) String token,
                                  @PathVariable Long id) {
        Long userId = currentUserId(token);
        if (userId == null) {
            return Result.error("请先登录");
        }

        if (id == null) {
            return Result.error("记录id不能为空");
        }

        Boolean success = moodRecordService.delete(id, userId);

        if (!success) {
            return Result.error("心情记录不存在或者无权删除");
        }

        return Result.success("删除成功", true);
    }

    @GetMapping("/detail/{id}")
    public Result<MoodRecordVO> detail(@RequestHeader(value = "token", required = false) String token,
                                       @PathVariable Long id) {
        Long userId = currentUserId(token);
        if (userId == null) {
            return Result.error("请先登录");
        }

        if (id == null) {
            return Result.error("记录id不能为空");
        }

        MoodRecordVO moodRecord = moodRecordService.getDetailView(id, userId);

        if (moodRecord == null) {
            return Result.error("心情记录不存在或者无权查看");
        }

        return Result.success("查询成功", moodRecord);
    }

    @PutMapping("/update/{id}")
    public Result<MoodSaveVO> update(@RequestHeader(value = "token", required = false) String token,
                                     @PathVariable Long id,
                                     @RequestBody MoodRecordUpdateRequest request) {
        Long userId = currentUserId(token);
        if (userId == null) {
            return Result.error("请先登录");
        }

        if (id == null) {
            return Result.error("记录id不能为空");
        }

        if (request == null) {
            return Result.error("请求参数不能为空");
        }

        if (request.getMoodType() == null || request.getMoodType().trim().isEmpty()) {
            return Result.error("心情类型不能为空");
        }

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            return Result.error("心情内容不能为空");
        }

        MoodRecord moodRecord = moodInput(request.getMoodType(), request.getContent(), request.getNeedAiReply());

        try {
            MoodSaveVO saved = moodRecordService.update(id, userId, moodRecord);
            if (saved == null) return Result.error("心情记录不存在或者无权修改");
            return Result.success("心情已保存", saved);
        } catch (IllegalArgumentException invalid) {
            return Result.error(invalid.getMessage());
        }
    }

    @GetMapping("/trend/recent7")
    public Result<MoodTrendVO> recentSevenDaysTrend(@RequestHeader(value = "token", required = false) String token) {
        Long userId = currentUserId(token);
        if (userId == null) {
            return Result.error("请先登录");
        }

        MoodTrendVO trend = moodRecordService.getRecentSevenDaysTrend(userId);

        return Result.success("查询成功", trend);
    }

    /** 从请求头 token 解析登录用户；缺失或空白返回 null。 */
    private Long currentUserId(String token) {
        if (isBlankToken(token)) {
            return null;
        }
        return jwtUtil.getUserId(token);
    }

    /** 把新增、修改请求的公共字段组装成实体；userId 由调用方按登录身份补上。 */
    private static MoodRecord moodInput(String moodType, String content, Boolean needAiReply) {
        MoodRecord moodRecord = new MoodRecord();
        moodRecord.setMoodType(moodType);
        moodRecord.setContent(content);
        moodRecord.setNeedAiReply(needAiReply);
        return moodRecord;
    }

    /** 请求头 token 缺失或仅含空白时视为未登录；签名与过期仍由 JwtUtil 校验。 */
    private static boolean isBlankToken(String token) {
        return token == null || token.trim().isEmpty();
    }
}
