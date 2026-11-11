package com.xinyu.dto;

/** 同步生成结果；errorCode用于分类，errorMessage可携带仅供后台查看的错误详情。 */
public class AiReplyResult {

    private String replyContent;
    private String prompt;
    private String modelName;
    private Boolean success;
    private String errorCode;
    private String errorMessage;

    public static AiReplyResult success(String replyContent, String prompt, String modelName) {
        AiReplyResult result = new AiReplyResult();
        result.setReplyContent(replyContent);
        result.setPrompt(prompt);
        result.setModelName(modelName);
        result.setSuccess(true);
        return result;
    }

    public static AiReplyResult failure(String replyContent, String prompt, String modelName, String errorCode) {
        AiReplyResult result = new AiReplyResult();
        result.setReplyContent(replyContent);
        result.setPrompt(prompt);
        result.setModelName(modelName);
        result.setSuccess(false);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorCode);
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

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
