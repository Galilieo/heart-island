package com.xinyu.service;

/** 跨服务失败只携带允许的分类，不附加原始响应、密钥、请求正文或异常链。 */
public final class AiServiceException extends RuntimeException {
    private final String code;

    public AiServiceException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
