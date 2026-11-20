package com.xinyu.common;

/** 与 ai_job 持久值和任务 API 一致的类型、状态和结果值。 */
public final class AiJobValues {
    public static final String MOOD_REPLY = "MOOD_REPLY";
    public static final String MOOD_REVIEW = "MOOD_REVIEW";

    public static final String QUEUED = "queued";
    public static final String RUNNING = "running";
    public static final String SUCCEEDED = "succeeded";
    public static final String FAILED = "failed";
    public static final String SUPERSEDED = "superseded";

    public static final String MODEL = "MODEL";
    public static final String SUPPORT = "SUPPORT";

    // 来源没有关联任务时使用的展示状态，不写入 ai_job。
    public static final String LEGACY = "legacy";
    public static final String UNAVAILABLE = "unavailable";

    private AiJobValues() {
    }
}
