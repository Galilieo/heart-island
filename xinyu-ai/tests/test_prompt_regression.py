"""锁定社区迁移前心情和回顾的完整 system 提示词。"""

from hashlib import sha256

from app.provider import SYSTEM_PROMPT
from app.review_prompt import REVIEW_SYSTEM_PROMPT


def test_existing_system_prompts_keep_exact_utf8_bytes():
    # 摘要取自基线 52772eb 的运行时字符串，覆盖空格和换行。
    assert sha256(SYSTEM_PROMPT.encode()).hexdigest() == (
        "cc9b7d74e61d935759357058579b3f45c2eee7eec48a4ab588fcf3878b632cdd"
    )
    assert sha256(REVIEW_SYSTEM_PROMPT.encode()).hexdigest() == (
        "d16f767cb73276fd1b01b4faee644ddf739c8acf6259fd49b945bce95e9373af"
    )
