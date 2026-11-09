package com.xinyu.vo;

/** 任务回查结果，不暴露追踪标识、模型密钥、prompt 或上游原始错误。 */
public record AiJobVO(Long id, Long targetId, Long targetVersion, String status,
                      Integer attemptCount, String resultKind, String replyContent,
                      String errorCode, boolean retryable) {
}
