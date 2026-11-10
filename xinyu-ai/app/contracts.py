"""定义 Java 与 Python 之间的请求、响应及 UTF-16 长度校验规则。"""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


TEMPLATE_VERSION = "mood-reply-v1"

# 以下范围与 Java 侧契约一致：正文按 UTF-16 代码单元计数，
# budget_ms 与 ID／版本上限来自 Java 的字段定义和 signed long 范围。
MAX_CONTENT_UTF16 = 500
MAX_REPLY_UTF16 = 2000
MAX_BUDGET_MS = 40000
JAVA_LONG_MAX = 9223372036854775807

# 与前端、Java 统一的心情单选值，顺序与产品选项保持一致。
MoodType = Literal[
    "开心", "满足", "期待", "感动", "平静",
    "焦虑", "难过", "生气", "委屈", "孤独", "迷茫", "疲惫", "其他",
]


def utf16_length(text: str) -> int:
    """计算 UTF-16 代码单元数；包含孤立代理字符时抛出 ValueError。"""
    # 严格编码同时拒绝孤立代理字符，长度口径与 Java String.length() 保持一致。
    try:
        return len(text.encode("utf-16-le")) // 2
    except UnicodeEncodeError:
        raise ValueError("invalid_unicode") from None


def validate_reply(text: str) -> str:
    """去除回复首尾空白，并校验非空及最多 2000 个 UTF-16 代码单元。"""
    text = text.strip()
    if not text or utf16_length(text) > MAX_REPLY_UTF16:
        raise ValueError("invalid_reply")
    return text


class Contract(BaseModel):
    """协议模型基类：禁止额外字段、隐式类型转换和错误信息中的输入回显。"""

    model_config = ConfigDict(extra="forbid", strict=True, hide_input_in_errors=True)


class Correlation(Contract):
    """关联一次任务尝试，供 Java 校验来源版本和迟到结果。"""

    request_id: str = Field(pattern=r"^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")
    job_id: str = Field(pattern=r"^[1-9][0-9]{0,18}$")
    attempt: int = Field(ge=1, le=3)
    source_version: str = Field(pattern=r"^[1-9][0-9]{0,18}$")

    @field_validator("job_id", "source_version")
    @classmethod
    def signed_long(cls, value: str) -> str:
        """确保字符串形式的 ID 和版本号不超过 Java 有符号 long 的上限。"""
        if int(value) > JAVA_LONG_MAX:
            raise ValueError("invalid_id")
        return value


class MoodReplyRequest(Correlation):
    """单次心情回应请求，包含最少必要的内容和本次处理预算。"""

    mood_type: MoodType
    content: str
    budget_ms: int = Field(ge=1, le=MAX_BUDGET_MS)

    @field_validator("content")
    @classmethod
    def valid_content(cls, value: str) -> str:
        """拒绝空白、超长或包含孤立代理字符的心情内容。"""
        if not value.strip() or utf16_length(value) > MAX_CONTENT_UTF16:
            raise ValueError("invalid_content")
        return value


class ModelReply(Contract):
    """经过校验的供应商回复，仅保留回复正文和模型名称。"""

    reply_content: str
    model_name: str = Field(min_length=1, max_length=128)

    @field_validator("reply_content")
    @classmethod
    def valid_reply(cls, value: str) -> str:
        """按统一规则校验并规范化模型回复。"""
        return validate_reply(value)

    @field_validator("model_name")
    @classmethod
    def nonempty_model(cls, value: str) -> str:
        """拒绝仅包含空白的模型名称。"""
        if not value.strip():
            raise ValueError("invalid_model_name")
        return value


class MoodReplyResponse(Correlation):
    """内部接口成功响应，明确区分模型回复和未调用模型的支持性回应。"""

    reply_content: str
    result_kind: Literal["MODEL", "SUPPORT"]
    model_called: bool
    template_version: Literal["mood-reply-v1"] = TEMPLATE_VERSION
    model_name: str | None

    @field_validator("reply_content")
    @classmethod
    def valid_reply(cls, value: str) -> str:
        """确保所有成功分支都遵守同一回复正文约束。"""
        return validate_reply(value)

    @model_validator(mode="after")
    def result_semantics(self):
        """校验结果类型、模型调用标记和模型名称之间的一致性。"""
        if self.result_kind == "MODEL":
            if not self.model_called or not self.model_name or not self.model_name.strip():
                raise ValueError("invalid_model_result")
        elif self.model_called or self.model_name is not None:
            raise ValueError("invalid_support_result")
        return self
