"""提供测试配置和供应商替身，避免测试调用真实模型。"""

from types import SimpleNamespace

import httpx

from app.settings import Settings


def make_settings(**changes):
    """创建独立的测试配置，允许用例覆盖待验证字段。"""
    return Settings(**{
        "internal_token": "test-internal-secret",
        "model_base_url": "https://provider.example.invalid/v1",
        "model_api_key": "test-provider-secret",
        "model_name": "test-model",
        "model_thinking_mode": "disabled",
        **changes,
    })


def payload(**changes):
    """构造合法的内部请求样例，允许用例覆盖待验证字段。"""
    return {
        "request_id": "request-1", "job_id": "9223372036854775807",
        "attempt": 1, "source_version": "1", "mood_type": "平静",
        "content": "今天散步以后心情平静了些。", "budget_ms": 40000,
        **changes,
    }


class StubProvider:
    """返回固定文本并记录调用信息的离线模型替身。"""

    def __init__(self, text="听起来散步给你带来了一些平静，可以给自己留一点这样的时间。"):
        self.calls = []
        self.text = text

    async def generate(self, request, deadline):
        """记录本次请求和截止时间，返回供接口执行真实校验的回复。"""
        self.calls.append((request, deadline))
        return SimpleNamespace(reply_content=self.text, model_name="test-model")


def client_for(app):
    """创建直接访问 ASGI 应用的测试客户端，不建立外部网络连接。"""
    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test")


AUTH = {"Authorization": "Bearer test-internal-secret"}
