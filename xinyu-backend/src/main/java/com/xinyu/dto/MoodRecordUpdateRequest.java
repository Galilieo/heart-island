package com.xinyu.dto;

/** 修改心情请求；needAiReply 缺省 true，兼容不传该字段的旧客户端。 */
public class MoodRecordUpdateRequest {

    /** 心情类型，必填。 */
    private String moodType;

    /** 心情正文，必填。 */
    private String content;

    /** 是否需要 AI 回复，缺省 true。 */
    private Boolean needAiReply = true;

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

    public Boolean getNeedAiReply() {
        return needAiReply;
    }

    public void setNeedAiReply(Boolean needAiReply) {
        this.needAiReply = needAiReply;
    }
}
