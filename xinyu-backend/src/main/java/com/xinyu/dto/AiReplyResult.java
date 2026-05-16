package com.xinyu.dto;

public class AiReplyResult {

    private String replyContent;
    private String prompt;
    private String modelName;
    private Boolean success;
    private String errorMessage;

    public static AiReplyResult success(String replyContent, String prompt, String modelName) {
        AiReplyResult result = new AiReplyResult();
        result.setReplyContent(replyContent);
        result.setPrompt(prompt);
        result.setModelName(modelName);
        result.setSuccess(true);
        return result;
    }

    public static AiReplyResult failure(String replyContent, String prompt, String modelName, String errorMessage) {
        AiReplyResult result = new AiReplyResult();
        result.setReplyContent(replyContent);
        result.setPrompt(prompt);
        result.setModelName(modelName);
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }

    public String getReplyContent() {
        return replyContent;
    }

    public void setReplyContent(String replyContent) {
        this.replyContent = replyContent;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
