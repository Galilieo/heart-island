package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
    public Page<MoodRecord> listByCondition(Long userId,
                                            String moodType,
                                            String startDate,
                                            String endDate,
                                            Long pageNum,
                                            Long pageSize) {
        QueryWrapper<MoodRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);

        if(moodType != null && !moodType.trim().isEmpty()) {
            queryWrapper.eq("mood_type", moodType);
        }

        if(startDate != null && !startDate.trim().isEmpty()) {
            queryWrapper.ge("create_time", startDate+" 00:00:00");
        }

        if(endDate != null && !endDate.trim().isEmpty()) {
            queryWrapper.le("create_time", endDate+" 23:59:59");
        }

        queryWrapper.orderByDesc("create_time");

        Page<MoodRecord> page = new Page<>(pageNum, pageSize);

        return moodRecordMapper.selectPage(page,queryWrapper);
    }

    public MoodRecord getDetail(Long id,Long userId){
        MoodRecord moodRecord =moodRecordMapper.selectById(id);

        if(moodRecord==null){
            return null;
        }

        if(!moodRecord.getUserId().equals(userId)){
            return  null;
        }

        return moodRecord;
    }

    public Boolean update(Long id, Long userId, MoodRecord moodRecord) {
        MoodRecord dbRecord = moodRecordMapper.selectById(id);

        if(dbRecord==null){
            return false;
        }

        if(!dbRecord.getUserId().equals(userId)){
            return false;
        }

        dbRecord.setMoodType(moodRecord.getMoodType());
        dbRecord.setContent(moodRecord.getContent());

        int rows = moodRecordMapper.updateById(dbRecord);
        return rows > 0;
    }
}