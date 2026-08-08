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

    /** 是否需要 AI 回复，缺省 true（兼容不传该字段的旧客户端） */
    private Boolean needAiReply = true;

    public Boolean getNeedAiReply() {
        return needAiReply;
    }

    public void setNeedAiReply(Boolean needAiReply) {
        this.needAiReply = needAiReply;
    }
}
