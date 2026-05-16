package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.dto.AiReplyResult;
import com.xinyu.entity.MoodRecord;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.vo.MoodTrendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MoodRecordService {
    @Autowired
    private AiReplyService aiReplyService;

    @Autowired
    private AiReplyLogService aiReplyLogService;

    @Autowired
    private MoodRecordMapper moodRecordMapper;

    public Boolean add(MoodRecord moodRecord) {
        int insertRows = moodRecordMapper.insert(moodRecord);
        if (insertRows <= 0) {
            return false;
        }

        AiReplyResult aiReplyResult = aiReplyService.generateReplyResult(
                moodRecord.getMoodType(),
                moodRecord.getContent()
        );

        moodRecord.setAiReply(aiReplyResult.getReplyContent());
        int updateRows = moodRecordMapper.updateById(moodRecord);

        aiReplyLogService.saveMoodReply(
                moodRecord.getUserId(),
                moodRecord.getId(),
                aiReplyResult
        );

        return updateRows > 0;
    }

    public List<MoodRecord> listByUserId(Long userId) {
        QueryWrapper<MoodRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.orderByDesc("create_time");

        return moodRecordMapper.selectList(queryWrapper);
    }

    public Boolean delete(Long id,Long userId) {
        MoodRecord moodRecord =moodRecordMapper.selectById(id);

        if(moodRecord == null){
            return false;
        }

        if(!moodRecord.getUserId().equals(userId)){
            return false;
        }
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

        AiReplyResult aiReplyResult = aiReplyService.generateReplyResult(
                moodRecord.getMoodType(),
                moodRecord.getContent()
        );

        dbRecord.setAiReply(aiReplyResult.getReplyContent());

        int rows = moodRecordMapper.updateById(dbRecord);

        if (rows > 0) {
            aiReplyLogService.saveMoodReply(
                    dbRecord.getUserId(),
                    dbRecord.getId(),
                    aiReplyResult
            );
        }

        return rows > 0;
    }

    public MoodTrendVO getRecentSevenDaysTrend(Long userId) {
        LocalDateTime startTime = LocalDate.now().minusDays(6).atStartOfDay();

        QueryWrapper<MoodRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.ge("create_time", startTime);
        queryWrapper.orderByDesc("create_time");

        List<MoodRecord> records = moodRecordMapper.selectList(queryWrapper);

        Map<String, Long> moodCountMap = records.stream()
                .collect(Collectors.groupingBy(MoodRecord::getMoodType, Collectors.counting()));

        List<MoodTrendVO.MoodCountVO> moodCounts = moodCountMap.entrySet()
                .stream()
                .map(entry -> new MoodTrendVO.MoodCountVO(entry.getKey(), entry.getValue().intValue()))
                .sorted((first, second) -> second.getCount().compareTo(first.getCount()))
                .collect(Collectors.toList());

        MoodTrendVO trend = new MoodTrendVO();
        trend.setTotal(records.size());
        trend.setMoodCounts(moodCounts);

        if (!records.isEmpty()) {
            MoodRecord lastRecord = records.get(0);
            trend.setLastMoodType(lastRecord.getMoodType());
            trend.setLastRecordTime(lastRecord.getCreateTime());
        }

        if (!moodCounts.isEmpty()) {
            trend.setMainMoodType(moodCounts.get(0).getMoodType());
        }

        return trend;
    }

}
