"""显式读取 AI 服务配置，不自动加载 .env 或复用 Java、实验项目的配置。"""

import os
from typing import Literal
from urllib.parse import urlsplit

from pydantic import BaseModel, ConfigDict, Field, SecretStr, field_validator


class Settings(BaseModel):
    """服务启动配置，校验密钥、模型适配方式和处理时限。"""

    model_config = ConfigDict(extra="forbid", hide_input_in_errors=True)

    internal_token: SecretStr
    model_base_url: str
    model_api_key: SecretStr
    model_name: str = Field(min_length=1, max_length=128)
    # 当前供应商以 thinking.type 接收思考模式，必须显式配置，不允许静默省略。
    model_thinking_mode: Literal["disabled", "enabled"]
    request_timeout_seconds: float = Field(default=40, gt=0, le=40)

    @field_validator("internal_token", "model_api_key")
    @classmethod
    def required_secret(cls, value: SecretStr) -> SecretStr:
        """校验密钥为非空、无空白且可用于 HTTP 认证头的 ASCII 字符串。"""
        secret = value.get_secret_value()
        if not secret.strip() or any(ord(c) < 33 or ord(c) > 126 for c in secret):
            raise ValueError("invalid_secret")
        return value

    @field_validator("model_base_url")
    @classmethod
    def valid_base_url(cls, value: str) -> str:
        """校验 HTTP 地址，并拒绝包含用户凭据、查询参数或片段的配置。"""
        url = urlsplit(value)
        if (url.scheme not in {"http", "https"} or not url.hostname or url.username
                or url.password or url.query or url.fragment):
            raise ValueError("invalid_model_base_url")
        return value.rstrip("/")

    @field_validator("model_name")
    @classmethod
    def nonempty_name(cls, value: str) -> str:
        """拒绝仅包含空白的模型名称。"""
        if not value.strip():
            raise ValueError("invalid_model_name")
        return value

    @classmethod
    def from_env(cls):
        """读取五项必需的环境配置；缺失或非法时抛出不含配置值的 ValueError。"""
        try:
            return cls(**{
                field: os.environ[env]
                for field, env in {
                    "internal_token": "XY_AI_INTERNAL_TOKEN",
                    "model_base_url": "XY_MODEL_BASE_URL",
                    "model_api_key": "XY_MODEL_API_KEY",
                    "model_name": "XY_MODEL_NAME",
                    "model_thinking_mode": "XY_MODEL_THINKING_MODE",
                }.items()
            })
        except (KeyError, ValueError):
            raise ValueError("invalid_ai_service_configuration") from None
