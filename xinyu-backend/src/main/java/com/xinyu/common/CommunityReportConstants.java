package com.xinyu.common;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 社区内容可见性与举报审核共用常量与规则校验（来源：G1 社区举报与处理任务书第 2、3、4 节）。
 */
public final class CommunityReportConstants {

    public static final int STATUS_AUTHOR_DELETED = 0;
    public static final int STATUS_VISIBLE = 1;
    public static final int STATUS_ADMIN_HIDDEN = 2;

    public static final String TARGET_POST = "POST";
    public static final String TARGET_REPLY = "REPLY";

    public static final String REPORT_PENDING = "PENDING";
    public static final String REPORT_HIDDEN = "HIDDEN";
    public static final String REPORT_DISMISSED = "DISMISSED";
    public static final String REPORT_CLOSED = "CLOSED";

    public static final String ACTION_HIDE = "HIDE";
    public static final String ACTION_DISMISS = "DISMISS";

    public static final String REASON_ATTACK = "ATTACK";
    public static final String REASON_AD = "AD";
    public static final String REASON_PRIVACY = "PRIVACY";
    public static final String REASON_ILLEGAL = "ILLEGAL";
    public static final String REASON_SAFETY = "SAFETY";
    public static final String REASON_OTHER = "OTHER";

    public static final int MAX_DETAIL_LENGTH = 200;
    public static final int MAX_HANDLE_NOTE_LENGTH = 200;
    public static final int DAILY_REPORT_LIMIT = 20;
    public static final int MY_REPORTS_WINDOW_DAYS = 90;
    public static final int EXCERPT_LIMIT = 60;

    public static final List<String> ALL_REASONS = List.of(
            REASON_ATTACK, REASON_AD, REASON_PRIVACY, REASON_ILLEGAL, REASON_SAFETY, REASON_OTHER
    );

    public static final Set<String> VALID_REASONS = Set.of(
            REASON_ATTACK, REASON_AD, REASON_PRIVACY, REASON_ILLEGAL, REASON_SAFETY, REASON_OTHER
    );

    public static final Set<String> VALID_HIDE_REASONS = Set.of(
            REASON_ATTACK, REASON_AD, REASON_PRIVACY, REASON_ILLEGAL
    );

    public static final Map<String, String> REASON_LABELS = Map.of(
            REASON_ATTACK, "人身攻击或骚扰",
            REASON_AD, "广告或引流",
            REASON_PRIVACY, "泄露他人隐私",
            REASON_ILLEGAL, "色情、暴力或违法内容",
            REASON_SAFETY, "担心发帖人的安全",
            REASON_OTHER, "其他"
    );

    private CommunityReportConstants() {
    }

    public static boolean isValidTargetType(String targetType) {
        return TARGET_POST.equals(targetType) || TARGET_REPLY.equals(targetType);
    }

    public static boolean isValidReason(String reason) {
        return reason != null && VALID_REASONS.contains(reason);
    }

    public static boolean isValidHideReason(String hideReason) {
        return hideReason != null && VALID_HIDE_REASONS.contains(hideReason);
    }

    /** 统一生成单行摘录：规范换行并截取前 60 个字符。 */
    public static String buildExcerpt(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= EXCERPT_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, EXCERPT_LIMIT) + "…";
    }
}
