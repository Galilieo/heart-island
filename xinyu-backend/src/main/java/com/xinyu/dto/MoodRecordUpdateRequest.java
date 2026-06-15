package com.xinyu.dto;

public class MoodRecordUpdateRequest {
    private String moodType;
    private String content;

    public String getMoodType() {
        return moodType;
    }

    public void setMoodType(String moodType) {
        this.moodType = moodType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
