package com.xinyu.vo;

import java.time.LocalDateTime;
import java.util.List;

public class MoodTrendVO {

    private Integer total;
    private String mainMoodType;
    private String lastMoodType;
    private LocalDateTime lastRecordTime;
    private List<MoodCountVO> moodCounts;

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public String getMainMoodType() {
        return mainMoodType;
    }

    public void setMainMoodType(String mainMoodType) {
        this.mainMoodType = mainMoodType;
    }

    public String getLastMoodType() {
        return lastMoodType;
    }

    public void setLastMoodType(String lastMoodType) {
        this.lastMoodType = lastMoodType;
    }

    public LocalDateTime getLastRecordTime() {
        return lastRecordTime;
    }

    public void setLastRecordTime(LocalDateTime lastRecordTime) {
        this.lastRecordTime = lastRecordTime;
    }

    public List<MoodCountVO> getMoodCounts() {
        return moodCounts;
    }

    public void setMoodCounts(List<MoodCountVO> moodCounts) {
        this.moodCounts = moodCounts;
    }

    public static class MoodCountVO {
        private String moodType;
        private Integer count;

        public MoodCountVO() {
        }

        public MoodCountVO(String moodType, Integer count) {
            this.moodType = moodType;
            this.count = count;
        }

        public String getMoodType() {
            return moodType;
        }

        public void setMoodType(String moodType) {
            this.moodType = moodType;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }
    }
}
