package com.xinyu.service;

/**
 * 管理员条件更新内容状态的结果状态，用于区分成功、对象不存在与并发状态冲突。
 */
public enum AdminStatusResult {
    UPDATED,
    NOT_FOUND,
    CONFLICT
}
