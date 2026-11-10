package com.xinyu.vo;

/**
 * 任务回查结果，不暴露追踪标识、模型密钥、prompt 或上游原始错误。
 *
 * @param id 任务 ID
 * @param targetId 来源日记 ID
 * @param targetVersion 任务对应的来源版本
 * @param status 任务状态：queued、running、succeeded、failed、cancelled、superseded
 * @param attemptCount 累计尝试次数，不等于供应商调用次数
 * @param resultKind 结果类型 MODEL 或 SUPPORT；未成功时为空
 * @param replyContent 仅成功时返回的回复正文
 * @param errorCode 失败时的安全错误分类
 * @param retryable 是否可重试：failed 且累计尝试次数未达上限
 */
public record AiJobVO(Long id, Long targetId, Long targetVersion, String status,
                      Integer attemptCount, String resultKind, String replyContent,
                      String errorCode, boolean retryable) {
}
