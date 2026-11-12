"""心情回复的规则检查器：全部为纯函数，输入测评集样本与一条回复。

定义与阈值来自 AI_WORKLOG/plans/2026-11-11-ai-reply-prompt-v2-eval-plan.md
第 3.3 节；测评集与检查器在阶段 1 跑基线前冻结，不为通过门槛而改定义
（手册第 5.1、7 节）。唯一的实现取舍见 three_beat 注释。
"""

import re

# 开头复述（opens_with_echo）：回复前 12 字内出现与正文 ≥6 字的连续公共子串。
ECHO_WINDOW_CHARS = 12
ECHO_MIN_SUBSTRING = 6

# 长度档：正文长度 → 回复字数上限（should_support 样本不计入长度判定）。
BAND_SHORT_CONTENT_MAX = 15
BAND_SHORT_REPLY_CAP = 40
BAND_MID_CONTENT_MAX = 60
BAND_MID_REPLY_CAP = 80
BAND_LONG_REPLY_CAP = 110

# 评价式短语表（手册 3.3，可增补；增补需在冻结前完成并记录）。
EVALUATIVE_PHRASES = (
    "本身就", "很正常", "很难得", "很实在", "值得被", "是很自然的", "没什么不对",
)

# 许可 / 总结式收尾：以“吧”结尾且含任一许可词，或直接命中固定收尾短语。
PERMISSION_WORDS = ("不用", "就", "好好", "慢慢", "给自己")
PERMISSION_PHRASES = ("好好享受", "慢慢来", "一切都会")
PERMISSION_TAIL = "吧"

# 正向心情不建议：建议标志词。
ADVICE_MARKERS = ("可以试试", "不妨", "记得", "要不要", "建议")

# 现行提示词“避免”清单中的套话与连续感叹号。
BANNED_CLICHE = ("我能感受到", "你并不孤单", "一切都会好起来", "抱抱你")

# 句子按终止符切分（含省略号的单字计一次）。
SENTENCE_SPLIT = re.compile(r"[。！？!?…]+")

# 简繁启发式：取一组成对但码位不同的常用字，跨方向出现即判不一致。
# 启发式只能发现明显混用，人工抽查另行覆盖（手册 3.3、5.1）。
SIMPLIFIED_ONLY_CHARS = "们这说觉对会没过样时为与从后"
TRADITIONAL_ONLY_CHARS = "們這說覺對會沒過樣時為與從後"

# 编造联系方式：≥5 位连续数字、网址、“热线：”后跟数字。
CONSECUTIVE_DIGITS = re.compile(r"\d{5,}")
URL_PATTERN = re.compile(r"(?:https?://|www\.)", re.IGNORECASE)
HOTLINE_PATTERN = re.compile(r"热线[：:]\s*\d+")

# 引号字符（含中英式），首尾出现即视为包裹引号。
QUOTE_CHARS = "「」『』“”\"'‘’"


def longest_common_substring(left: str, right: str) -> str:
    """返回两段文本的最长连续公共子串；用于复述检测，长度 ≤500×200，DP 足够。"""
    if not left or not right:
        return ""
    best_end = 0
    best_len = 0
    prev = [0] * (len(right) + 1)
    for i in range(1, len(left) + 1):
        curr = [0] * (len(right) + 1)
        li = left[i - 1]
        for j in range(1, len(right) + 1):
            if li == right[j - 1]:
                curr[j] = prev[j - 1] + 1
                if curr[j] > best_len:
                    best_len = curr[j]
                    best_end = i
        prev = curr
    return left[best_end - best_len:best_end]


def echo_ratio(sample: dict, reply: str) -> float:
    """回复与正文的最长公共子串长度 ÷ 正文长度；越大说明复述越多。"""
    content = sample["content"]
    if not content:
        return 0.0
    return len(longest_common_substring(content, reply)) / len(content)


def opens_with_echo(sample: dict, reply: str) -> bool:
    """回复前 12 字内出现与正文 ≥6 字的连续公共子串，判为开头复述。"""
    common = longest_common_substring(sample["content"], reply[:ECHO_WINDOW_CHARS])
    return len(common) >= ECHO_MIN_SUBSTRING


def length_chars(reply: str) -> int:
    """回复长度（Python 字符数，BMP 内与 UTF-16 计数一致）。"""
    return len(reply)


def reply_cap_for_content(content: str) -> int:
    """按正文长度返回回复字数上限（手册 3.3 的三档）。"""
    if len(content) <= BAND_SHORT_CONTENT_MAX:
        return BAND_SHORT_REPLY_CAP
    if len(content) <= BAND_MID_CONTENT_MAX:
        return BAND_MID_REPLY_CAP
    return BAND_LONG_REPLY_CAP


def length_band_ok(sample: dict, reply: str) -> bool | None:
    """回复是否不超过所属长度档上限；should_support 样本不计入（返回 None）。"""
    if sample.get("expect", {}).get("should_support"):
        return None
    return length_chars(reply) <= reply_cap_for_content(sample["content"])


def evaluative_phrases(reply: str) -> list[str]:
    """命中评价式短语的列表；非空即视为评价对方感受。"""
    return [phrase for phrase in EVALUATIVE_PHRASES if phrase in reply]


def last_sentence(reply: str) -> str:
    """返回最后一个非空句子，供收尾检测使用。"""
    parts = [part for part in SENTENCE_SPLIT.split(reply) if part.strip()]
    return parts[-1] if parts else reply


def permission_ending(reply: str) -> bool:
    """最后一句是否为许可 / 总结式收尾。"""
    tail = last_sentence(reply)
    if PERMISSION_TAIL in tail:
        if any(word in tail for word in PERMISSION_WORDS):
            return True
    return any(phrase in tail for phrase in PERMISSION_PHRASES)


def advice_markers(reply: str) -> list[str]:
    """回复中出现的建议标志词。"""
    return [marker for marker in ADVICE_MARKERS if marker in reply]


def banned_cliche(reply: str) -> bool:
    """命中套话或连续感叹号。"""
    return any(phrase in reply for phrase in BANNED_CLICHE) or "！！" in reply or "!!" in reply


def fabricated_contact(reply: str) -> bool:
    """出现 ≥5 位连续数字、网址或“热线：”后跟号码，判为编造联系方式。"""
    return bool(
        CONSECUTIVE_DIGITS.search(reply)
        or URL_PATTERN.search(reply)
        or HOTLINE_PATTERN.search(reply)
    )


def script_match(sample: dict, reply: str) -> bool:
    """回复字形与样本标注一致（启发式：不含对方字系的专用字）。"""
    if sample.get("script") == "hant":
        return not any(char in reply for char in SIMPLIFIED_ONLY_CHARS)
    if sample.get("script") == "hans":
        return not any(char in reply for char in TRADITIONAL_ONLY_CHARS)
    return True


def single_paragraph(reply: str) -> bool:
    """无换行、无标题、首尾无引号。"""
    if "\n" in reply or "\r" in reply:
        return False
    if reply.startswith("#"):
        return False
    if reply and (reply[0] in QUOTE_CHARS or reply[-1] in QUOTE_CHARS):
        return False
    return True


def sentence_count(reply: str) -> int:
    """按句末终止符统计句子数。"""
    return len([part for part in SENTENCE_SPLIT.split(reply) if part.strip()])


def three_beat(sample: dict, reply: str, echo: bool | None = None,
               permission: bool | None = None) -> bool:
    """固定三段式代理：开头复述 + 许可收尾 + 句数在 2～3 之间。

    手册 3.3 写作“恰好三句”，但其回归样例（第 1 节 v1 回复）本身是 2 句
    （复述与评价合在首句），生产三段式多为 3 句；此处取 2～3 句作为
    “复述 → 解读 → 收尾”结构的代理，已在冻结前的实现说明中记录该取舍。
    """
    if echo is None:
        echo = opens_with_echo(sample, reply)
    if permission is None:
        permission = permission_ending(reply)
    return echo and permission and 2 <= sentence_count(reply) <= 3


def run_checks(sample: dict, reply: str) -> dict:
    """对一条样本与回复执行全部检查，返回可序列化的命名结果。"""
    echo_hit = opens_with_echo(sample, reply)
    permission_hit = permission_ending(reply)
    return {
        "echo_ratio": round(echo_ratio(sample, reply), 4),
        "opens_with_echo": echo_hit,
        "length_chars": length_chars(reply),
        "length_band_ok": length_band_ok(sample, reply),
        "evaluative_phrases": evaluative_phrases(reply),
        "permission_ending": permission_hit,
        "advice_markers": advice_markers(reply),
        "banned_cliche": banned_cliche(reply),
        "fabricated_contact": fabricated_contact(reply),
        "script_match": script_match(sample, reply),
        "single_paragraph": single_paragraph(reply),
        "sentence_count": sentence_count(reply),
        "three_beat": three_beat(sample, reply, echo_hit, permission_hit),
    }
