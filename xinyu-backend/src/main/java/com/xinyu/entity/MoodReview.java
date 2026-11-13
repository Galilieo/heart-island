package com.xinyu.entity;

import java.time.LocalDate;

/** 已保存回顾的数据库快照；对外接口使用独立 VO，不返回原始行。 */
public record MoodReview(long id, long userId, String type, LocalDate start, LocalDate end, int revision,
                         String fingerprint, String status, String statsJson, String body, String ps,
                         String kind, String finishedAt) { }
