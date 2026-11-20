package com.xinyu.vo;

import java.util.Map;

public class AdminOverviewVO {

    private Long userTotal;
    private Long userDisabled;
    private Long postTotal;
    private Long postHidden;
    private Long replyTotal;
    private Long replyHidden;
    private Long riskPendingPosts;
    private Long riskPendingReplies;
    private Long aiReplyTotal;
    private Long aiReplySuccess;
    private Long aiReplyFailed;
    private String rateLimitBackend;
    private Map<String, Long> rateLimitRejections;

    public AdminOverviewVO() {
    }

    public AdminOverviewVO(Long userTotal, Long userDisabled,
                           Long postTotal, Long postHidden,
                           Long replyTotal, Long replyHidden,
                           Long riskPendingPosts, Long riskPendingReplies,
                           Long aiReplyTotal, Long aiReplySuccess, Long aiReplyFailed,
                           String rateLimitBackend, Map<String, Long> rateLimitRejections) {
        this.userTotal = userTotal;
        this.userDisabled = userDisabled;
        this.postTotal = postTotal;
        this.postHidden = postHidden;
        this.replyTotal = replyTotal;
        this.replyHidden = replyHidden;
        this.riskPendingPosts = riskPendingPosts;
        this.riskPendingReplies = riskPendingReplies;
        this.aiReplyTotal = aiReplyTotal;
        this.aiReplySuccess = aiReplySuccess;
        this.aiReplyFailed = aiReplyFailed;
        this.rateLimitBackend = rateLimitBackend;
        this.rateLimitRejections = rateLimitRejections;
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

    public Long getRiskPendingPosts() {
        return riskPendingPosts;
    }

    public Long getRiskPendingReplies() {
        return riskPendingReplies;
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

    public String getRateLimitBackend() {
        return rateLimitBackend;
    }

    public void setRateLimitBackend(String rateLimitBackend) {
        this.rateLimitBackend = rateLimitBackend;
    }

    public Map<String, Long> getRateLimitRejections() {
        return rateLimitRejections;
    }

    public void setRateLimitRejections(Map<String, Long> rateLimitRejections) {
        this.rateLimitRejections = rateLimitRejections;
    }
}
