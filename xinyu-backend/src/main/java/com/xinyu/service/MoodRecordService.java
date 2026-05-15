package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xinyu.entity.MoodRecord;
import com.xinyu.mapper.MoodRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MoodRecordService {

    @Autowired
    private MoodRecordMapper moodRecordMapper;

    public Boolean add(MoodRecord moodRecord) {
        int rows = moodRecordMapper.insert(moodRecord);
        return rows > 0;
    }

    public List<MoodRecord> listByUserId(Long userId) {
        QueryWrapper<MoodRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.orderByDesc("create_time");

        return moodRecordMapper.selectList(queryWrapper);
    }

    public Boolean delete(Long id) {
        int rows = moodRecordMapper.deleteById(id);
        return rows > 0;
    }
}