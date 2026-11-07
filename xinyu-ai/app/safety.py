"""以有限规则识别风险表达并生成内置支持性回应，不依赖地区资源配置。"""

import re

from app.contracts import validate_reply


def support_text() -> str:
    """生成具体的支持性回应，不推测地区或提供未经核验的联系方式。"""
    return validate_reply(
        "你写下的这些，听起来真的很难熬。"
        "如果你有伤害自己的想法，请先把身边可能伤到自己的东西放远一点，"
        "然后联系一个你信任的人，告诉对方你现在需要有人陪。"
        "可以直接说：“我现在状态很不好，想请你陪我一会儿。”"
        "如果危险就在眼前，请立即联系当地急救或警方，或请身边的人陪你前往急诊。"
    )


_RISK = re.compile(
    # 较长候选优先，并使用占有量词禁止回退重分组，
    # 避免“想要／想／要”等重叠短语在后缀不匹配时产生指数级回溯。
    r"(?:我(?:现在|今晚|真的|已经|正在|打算|准备|想要去|想要|想|要|很想|决定|就要)*+"
    r"(?:自杀|结束生命|结束自己的生命|割腕|跳楼|伤害自己))"
    r"|(?:i\s+(?:want\s+to|am\s+going\s+to|plan\s+to)\s+(?:kill\s+myself|end\s+my\s+life))",
    re.IGNORECASE,
)


def build_support_reply(content: str) -> str | None:
    """命中有限风险规则时返回内置支持性回应，否则进入普通生成分支。"""
    if _RISK.search(content):
        return support_text()
    return None
