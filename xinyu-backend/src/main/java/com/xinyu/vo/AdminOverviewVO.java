package com.xinyu.vo;

public class AdminOverviewVO {

    private Long userTotal;
    private Long userDisabled;
    private Long postTotal;
    private Long postHidden;
    private Long replyTotal;
    private Long replyHidden;
    private Long aiReplyTotal;
    private Long aiReplySuccess;
    private Long aiReplyFailed;

    public AdminOverviewVO() {
    }

    public AdminOverviewVO(Long userTotal, Long userDisabled,
                           Long postTotal, Long postHidden,
                           Long replyTotal, Long replyHidden,
                           Long aiReplyTotal, Long aiReplySuccess, Long aiReplyFailed) {
        this.userTotal = userTotal;
        this.userDisabled = userDisabled;
        this.postTotal = postTotal;
        this.postHidden = postHidden;
        this.replyTotal = replyTotal;
        this.replyHidden = replyHidden;
        this.aiReplyTotal = aiReplyTotal;
        this.aiReplySuccess = aiReplySuccess;
        this.aiReplyFailed = aiReplyFailed;
    }

    public Long getUserTotal() {
        return userTotal;
    }

    public Long getUserDisabled() {
        return userDisabled;
    }

    public Long getPostTotal() {
        return postTotal;
    }

    public Long getPostHidden() {
        return postHidden;
    }

    public Long getReplyTotal() {
        return replyTotal;
    }

    public Long getReplyHidden() {
        return replyHidden;
    }

    public Long getAiReplyTotal() {
        return aiReplyTotal;
    }

    public Long getAiReplySuccess() {
        return aiReplySuccess;
    }

    public Long getAiReplyFailed() {
        return aiReplyFailed;
    }

    public void setUserTotal(Long userTotal) {
        this.userTotal = userTotal;
    }

    public void setUserDisabled(Long userDisabled) {
        this.userDisabled = userDisabled;
    }

    public void setPostTotal(Long postTotal) {
        this.postTotal = postTotal;
    }

    public void setPostHidden(Long postHidden) {
        this.postHidden = postHidden;
    }

    public void setReplyTotal(Long replyTotal) {
        this.replyTotal = replyTotal;
    }

    public void setReplyHidden(Long replyHidden) {
        this.replyHidden = replyHidden;
    }

    public void setAiReplyTotal(Long aiReplyTotal) {
        this.aiReplyTotal = aiReplyTotal;
    }

    public void setAiReplySuccess(Long aiReplySuccess) {
        this.aiReplySuccess = aiReplySuccess;
    }

    public void setAiReplyFailed(Long aiReplyFailed) {
        this.aiReplyFailed = aiReplyFailed;
    }
}
