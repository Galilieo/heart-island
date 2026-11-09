"""验证供应商请求参数、错误分类、零自动重试及真实连接清理。"""

import asyncio
import time

import httpx
import pytest

from app.main import create_app
from app.provider import HttpProvider
from tests.stub_provider import AUTH, client_for, make_settings, payload


@pytest.mark.parametrize("body,status,code", [
    ({"choices": [{"message": {"content": "  一段合规回复。  "}}]}, 200, None),
    ({"choices": []}, 200, "invalid_model_response"),
    ({"choices": [{"message": {"content": " "}}]}, 200, "invalid_model_response"),
    ({"choices": [{"message": {"content": 123}}]}, 200, "invalid_model_response"),
    ({"choices": [{"message": {"content": "x"}, "finish_reason": "length"}]}, 200, "invalid_model_response"),
    (None, 200, "invalid_model_response"),
    ({"error": "private-upstream-sentinel"}, 401, "provider_error"),
    ({"error": "private-upstream-sentinel"}, 429, "provider_error"),
    ({"error": "private-upstream-sentinel"}, 500, "provider_error"),
])
async def test_http_provider_classifies_results_without_retries_or_body_leaks(body, status, code, caplog):
    """成功仅接受合法正文；失败必须正确分类，不重试且不泄露上游内容。"""
    import json
    calls = []
    def upstream(request):
        calls.append(request)
        return httpx.Response(status, json=body) if body is not None else httpx.Response(status, text="private-upstream-sentinel")
    settings = make_settings()
    provider = HttpProvider(settings, transport=httpx.MockTransport(upstream))
    async with client_for(create_app(settings, provider)) as client:
        response = await client.post("/internal/v1/mood-replies", json=payload(), headers=AUTH)
    assert len(calls) == 1
    request = calls[0]
    assert str(request.url) == "https://provider.example.invalid/v1/chat/completions"
    assert request.headers["Authorization"] == "Bearer test-provider-secret"
    data = json.loads(request.content)
    assert data["model"] == "test-model"
    assert data["max_tokens"] == 4096
    assert data["thinking"] == {"type": "disabled"}
    assert data["stream"] is False
    assert [m["role"] for m in data["messages"]] == ["system", "user"]
    assert payload()["content"] not in data["messages"][0]["content"]
    assert payload()["content"] in data["messages"][1]["content"]
    assert request.extensions["timeout"]["connect"] <= 2
    assert request.extensions["timeout"]["read"] <= 35
    assert "private-upstream-sentinel" not in response.text + caplog.text
    assert "test-provider-secret" not in response.text + caplog.text
    assert payload()["content"] not in caplog.text
    if code:
        assert response.status_code == 502
        assert response.json() == {"request_id": "request-1", "code": code}
    else:
        assert response.status_code == 200
        assert response.json()["reply_content"] == "一段合规回复。"


@pytest.mark.parametrize("mode", ["disabled", "enabled"])
async def test_configured_token_budget_and_thinking_are_sent_without_exposing_reasoning(mode):
    """两种模式均发送配置预算；成功只返回正文，不向调用方暴露思考内容。"""
    import json
    calls = []

    def upstream(request):
        calls.append(json.loads(request.content))
        return httpx.Response(200, json={"choices": [{
            "message": {"content": "一段合规回复。", "reasoning_content": "private-reasoning-sentinel"},
            "finish_reason": "stop",
        }]})

    settings = make_settings(model_thinking_mode=mode, model_max_tokens=8192)
    provider = HttpProvider(settings, transport=httpx.MockTransport(upstream))
    async with client_for(create_app(settings, provider)) as client:
        response = await client.post("/internal/v1/mood-replies", json=payload(), headers=AUTH)
    assert response.status_code == 200
    assert len(calls) == 1
    assert calls[0]["max_tokens"] == 8192
    assert calls[0]["thinking"] == {"type": mode}
    assert response.json()["reply_content"] == "一段合规回复。"
    assert "private-reasoning-sentinel" not in response.text


@pytest.mark.parametrize("error,expected,status", [
    (httpx.ReadTimeout("private-upstream-sentinel"), "provider_timeout", 504),
    (httpx.ConnectTimeout("private-upstream-sentinel"), "provider_timeout", 504),
    (httpx.ConnectError("private-upstream-sentinel"), "provider_error", 502),
])
async def test_transport_errors_are_safe_and_never_retried(error, expected, status):
    """连接和读取异常应转换为固定分类，且每次请求仅调用供应商一次。"""
    calls = []
    def upstream(request):
        calls.append(request)
        raise error
    settings = make_settings()
    provider = HttpProvider(settings, transport=httpx.MockTransport(upstream))
    async with client_for(create_app(settings, provider)) as client:
        response = await client.post("/internal/v1/mood-replies", json=payload(), headers=AUTH)
    assert response.status_code == status
    assert response.json() == {"request_id": "request-1", "code": expected}
    assert len(calls) == 1


async def test_real_slow_socket_is_cancelled_and_closed_by_deadline():
    """通过真实回环连接确认超时会关闭本地连接，而非只结束表面等待。"""
    accepted, closed = asyncio.Event(), asyncio.Event()
    async def upstream(reader, writer):
        try:
            await reader.readuntil(b"\r\n\r\n")
            accepted.set()
            # 读取正文后不返回响应，通过客户端 EOF 确认超时已关闭连接。
            while await reader.read(4096):
                pass
            closed.set()
        finally:
            writer.close()
            await writer.wait_closed()
    server = await asyncio.start_server(upstream, "127.0.0.1", 0)
    port = server.sockets[0].getsockname()[1]
    settings = make_settings()
    settings = settings.model_copy(update={"model_base_url": f"http://127.0.0.1:{port}/v1"})
    async with server, client_for(create_app(settings)) as client:
        start = time.monotonic()
        response = await asyncio.wait_for(client.post("/internal/v1/mood-replies", json=payload(budget_ms=150), headers=AUTH), 2)
        assert response.status_code == 504
        assert time.monotonic() - start < 1.5
        assert accepted.is_set()
        await asyncio.wait_for(closed.wait(), 1)
