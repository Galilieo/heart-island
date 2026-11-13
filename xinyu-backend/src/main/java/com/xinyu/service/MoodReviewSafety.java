package com.xinyu.service;

import java.util.regex.Pattern;

/**
 * D1 禁止把关闭 AI 的正文送到 Python，故 Java 在抽样和截断前检查完整来源。
 * 规则与固定文案逐字对应 xinyu-ai/app/safety.py；跨端一致性由集成用例核验。
 */
public final class MoodReviewSafety {
    public static final String SUPPORT_TEXT = "你写下的这些，听起来真的很难熬。如果你有伤害自己的想法，请先把身边可能伤到自己的东西放远一点，然后联系一个你信任的人，告诉对方你现在需要有人陪。可以直接说：“我现在状态很不好，想请你陪我一会儿。”如果危险就在眼前，请立即联系当地急救或警方，或请身边的人陪你前往急诊。";
    private static final Pattern RISK = Pattern.compile("(?:我(?:现在|今晚|真的|已经|正在|打算|准备|想要去|想要|想|要|很想|决定|就要|就|会|一直|总是|每天(?:都)?|在|计划(?:怎么)?|怎么|好|太)*+(?:自杀|结束生命|结束自己的生命|割腕|跳楼|伤害自己))|(?:i\\s+(?:want\\s+to|am\\s+going\\s+to|plan\\s+to)\\s+(?:kill\\s+myself|end\\s+my\\s+life))", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS | Pattern.UNICODE_CASE);
    private static final Pattern COLLOQUIAL = Pattern.compile("(?:我(?:现在|今晚|真的|已经|正在|很|就|会|一直|总是|每天(?:都)?|在|计划(?:怎么)?|怎么|好|太)*+(?:(?:(?:准备好?|打算|想要|想|要)去?|去)死(?![你磕])|不想活(?![在成])|活不下去))|(?:^|[，。！？!?；;\\n])\\s*(?:(?:好|太|很|真的|每天(?:都在)?|最近总是|一直)*+想(?:要)?(?:去)?(?:自杀|死(?![你磕])|结束(?:自己的)?生命)|不想活(?![在成])|活不下去)", Pattern.UNICODE_CHARACTER_CLASS);

    private MoodReviewSafety() { }

    public static boolean matches(String content) {
        return RISK.matcher(content).find() || COLLOQUIAL.matcher(content).find();
    }
}
