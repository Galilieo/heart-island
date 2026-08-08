package com.xinyu.dto;

public class CommunityPostUpdateDTO {

    private Long id;

    private Long topicId;

    private String moodType;

    private String content;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

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
