package com.xinyu.service;

/** 失败任务的冷却规则；只计算时长，是否到期由数据库时间与重排CAS判定。 */
public final class AiJobRetryPolicy {
    // Task3B契约：每3次失败一轮，从180秒翻倍，最多冷却3600秒。
    private static final int ATTEMPTS_PER_ROUND = 3;
    private static final int INITIAL_COOLDOWN_SECONDS = 180;
    private static final int MAX_COOLDOWN_SECONDS = 3600;

    private AiJobRetryPolicy() {
    }

    /**
     * 根据失败任务的累计尝试次数计算本轮冷却秒数。
     *
     * @param attemptCount 非负累计尝试次数；0表示尚未执行，不产生冷却
     * @return 非轮次末尾返回0；轮次末尾返回本轮时长，上限3600秒
     */
    public static int cooldownSeconds(int attemptCount) {
        if (attemptCount <= 0 || attemptCount % ATTEMPTS_PER_ROUND != 0) {
            return 0;
        }

        int rounds = attemptCount / ATTEMPTS_PER_ROUND;
        int cooldownSeconds = INITIAL_COOLDOWN_SECONDS;
        for (int round = 1; round < rounds; round++) {
            cooldownSeconds = Math.min(cooldownSeconds * 2, MAX_COOLDOWN_SECONDS);

            if (cooldownSeconds == MAX_COOLDOWN_SECONDS) {
                break;
            }
        }

        return cooldownSeconds;
    }
}
