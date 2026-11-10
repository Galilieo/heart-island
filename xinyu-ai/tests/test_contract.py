"""验证内部接口的认证、字段约束、响应语义和请求总时限。"""

import asyncio
import json
from pathlib import Path

import pytest

from app.main import create_app
from tests.stub_provider import AUTH, StubProvider, client_for, make_settings, payload


def application(provider=None, **settings):
    """创建使用独立配置和离线模型替身的测试应用。"""
    return create_app(make_settings(**settings), provider or StubProvider())


@pytest.mark.parametrize("content", ["文" * 500, "😀" * 250])
async def test_model_reply_accepts_exactly_500_utf16_and_echoes_correlation(content):
    """恰好 500 个 UTF-16 代码单元的输入应成功，并完整回传任务关联字段。"""
    provider = StubProvider()
    async with client_for(application(provider)) as client:
        response = await client.post("/internal/v1/mood-replies", json=payload(content=content), headers=AUTH)
    assert response.status_code == 200
    assert response.json() == {
        "request_id": "request-1", "job_id": "9223372036854775807", "attempt": 1,
        "source_version": "1", "reply_content": provider.text, "result_kind": "MODEL",
        "model_called": True, "template_version": "mood-reply-v1", "model_name": "test-model",
    }
    assert len(provider.calls) == 1


@pytest.mark.parametrize("headers", [{}, {"Authorization": "Bearer wrong"}, {"Authorization": "Bearer 测试"}])
async def test_unauthorized_requests_never_call_provider(headers):
    """缺失或错误的认证头必须在模型调用前被拒绝。"""
    provider = StubProvider()
    # 将该无效认证样例编码为 Latin-1 字节，避免 HTTP 客户端提前拒绝中文头值。
    headers = {k: v.replace("测试", "ÿ") for k, v in headers.items()}
    headers = {k.encode(): v.encode("latin-1") for k, v in headers.items()}
    async with client_for(application(provider)) as client:
        response = await client.post("/internal/v1/mood-replies", json=payload(), headers=headers)
    assert response.status_code == 401
    assert response.json() == {"request_id": None, "code": "unauthorized"}
    assert provider.calls == []


@pytest.mark.parametrize("changes", [
    {"job_id": 1}, {"job_id": "0"}, {"job_id": "-1"}, {"job_id": "01"},
    {"job_id": "9223372036854775808"}, {"source_version": "x"},
    {"source_version": "0"}, {"request_id": ""}, {"request_id": "x" * 65},
    {"request_id": "raw\ntext"}, {"attempt": True}, {"attempt": 0},
    {"budget_ms": 0}, {"budget_ms": 40001}, {"budget_ms": "40"},
    {"content": "文" * 501}, {"content": "😀" * 251}, {"content": ""},
    {"content": " \n"}, {"content": "\ud800"}, {"content": "\udfff"},
    {"mood_type": "unknown"}, {"extra": "private-original-sentinel"},
])
async def test_invalid_requests_are_redacted_and_do_not_generate(changes):
    """非法字段应返回脱敏错误，且不得触发模型生成。"""
    provider = StubProvider()
    async with client_for(application(provider)) as client:
        response = await client.post("/internal/v1/mood-replies", content=json.dumps(payload(**changes)), headers=AUTH)
    assert response.status_code == 400
    assert response.json()["code"] == "invalid_request"
    assert set(response.json()) == {"request_id", "code"}
    assert "private-original-sentinel" not in response.text
    assert provider.calls == []


@pytest.mark.parametrize("body", ["not-json", "[]", "null", '{}', '{"content":'])
async def test_malformed_body_is_a_safe_invalid_request(body):
    """非 JSON 或不完整请求体应返回固定的参数错误响应。"""
    async with client_for(application()) as client:
        response = await client.post("/internal/v1/mood-replies", content=body, headers=AUTH)
    assert response.status_code == 400
    assert response.json() == {"request_id": None, "code": "invalid_request"}


async def test_health_does_not_expose_configuration():
    """健康检查仅返回就绪状态，且不开放 API 文档端点。"""
    async with client_for(application()) as client:
        response = await client.get("/health")
        docs = await client.get("/openapi.json")
    assert response.json() == {"ready": True}
    assert docs.status_code == 404


@pytest.mark.parametrize("text", ["", "  ", "文" * 2001, "😀" * 1001, "\ud800"])
async def test_even_an_injected_provider_cannot_return_invalid_text(text):
    """注入的模型替身也不能绕过成功回复的正文校验。"""
    async with client_for(application(StubProvider(text))) as client:
        response = await client.post("/internal/v1/mood-replies", json=payload(), headers=AUTH)
    assert response.status_code == 502
    assert response.json() == {"request_id": "request-1", "code": "invalid_model_response"}


async def test_header_and_streamed_body_limits():
    """超大请求头和累计超限的分块正文均应被拒绝。"""
    provider = StubProvider()
    async def oversized_body():
        yield b" " * 9000
        yield b" " * 9000
    async with client_for(application(provider)) as client:
        for kwargs in [{"headers": {**AUTH, "X-Padding": "x" * 9000}},
                       {"headers": AUTH, "content": oversized_body()}]:
            response = await client.post("/internal/v1/mood-replies", **kwargs)
            assert response.status_code == 400
            assert response.json()["code"] == "invalid_request"
    assert provider.calls == []


async def test_total_deadline_cancels_provider_and_next_request_can_run():
    """总时限到期应取消生成并释放处理名额，使下一条请求能够执行。"""
    cancelled = asyncio.Event()
    class SlowOnce(StubProvider):
        async def generate(self, request, deadline):
            if not cancelled.is_set():
                try:
                    await asyncio.Event().wait()
                finally:
                    cancelled.set()
            return await super().generate(request, deadline)
    async with client_for(application(SlowOnce())) as client:
        response = await asyncio.wait_for(client.post("/internal/v1/mood-replies", json=payload(budget_ms=30), headers=AUTH), 1)
        assert response.status_code == 504
        assert response.json() == {"request_id": "request-1", "code": "provider_timeout"}
        assert cancelled.is_set()
        assert (await client.post("/internal/v1/mood-replies", json=payload(), headers=AUTH)).status_code == 200


async def test_request_body_read_is_also_bounded_by_total_deadline():
    """请求正文持续不结束时，总时限仍应终止本地等待。"""
    async def slow_body():
        yield b"{"
        await asyncio.Event().wait()
    async with client_for(application(request_timeout_seconds=0.03)) as client:
        response = await asyncio.wait_for(client.post("/internal/v1/mood-replies", content=slow_body(), headers=AUTH), 1)
    assert response.status_code == 504
    assert response.json() == {"request_id": None, "code": "provider_timeout"}


async def test_exhausted_budget_cannot_return_support_after_slow_request_body():
    """读取正文已耗尽预算时，支持性回应也不得返回成功。"""
    provider = StubProvider()
    async def slow_body():
        yield b" "
        await asyncio.sleep(0.03)
        yield json.dumps(payload(content="我想自杀", budget_ms=1)).encode()
    async with client_for(application(provider)) as client:
        response = await client.post("/internal/v1/mood-replies", content=slow_body(), headers=AUTH)
        assert response.status_code == 504
        assert response.json() == {"request_id": "request-1", "code": "provider_timeout"}
        assert (await client.post("/internal/v1/mood-replies", json=payload(), headers=AUTH)).status_code == 200
    assert len(provider.calls) == 1


async def test_synchronous_risk_branch_must_check_deadline_before_success(monkeypatch):
    """同步风险判断超过预算后，必须通过显式时钟检查返回超时。"""
    import time
    def slow_support(content):
        time.sleep(0.03)
        return "一段测试支持回应。"
    monkeypatch.setattr("app.main.build_support_reply", slow_support)
    async with client_for(application()) as client:
        response = await client.post("/internal/v1/mood-replies", json=payload(budget_ms=1), headers=AUTH)
    assert response.status_code == 504
    assert response.json() == {"request_id": "request-1", "code": "provider_timeout"}


async def test_busy_service_rejects_excess_work_without_queueing():
    """已有生成占用处理名额时，应拒绝额外工作而非在内存中排队。"""
    started, release = asyncio.Event(), asyncio.Event()
    class Blocked(StubProvider):
        async def generate(self, request, deadline):
            started.set()
            await release.wait()
            return await super().generate(request, deadline)
    async with client_for(application(Blocked(), max_concurrent_requests=1)) as client:
        first = asyncio.create_task(client.post("/internal/v1/mood-replies", json=payload(), headers=AUTH))
        await asyncio.wait_for(started.wait(), 1)
        try:
            response = await client.post("/internal/v1/mood-replies", json=payload(), headers=AUTH)
            assert response.status_code == 503
            assert response.json() == {"request_id": "request-1", "code": "overloaded"}
        finally:
            release.set()
            await first


@pytest.mark.parametrize("mood", json.loads((Path(__file__).resolve().parents[2] / "test-contracts/mood-types.json").read_text()))
async def test_all_product_moods_reach_provider_without_changing_their_value(mood):
    """产品单选的全部十三种心情均可通过内部协议，且原值传给模型适配层。"""
    provider = StubProvider()
    async with client_for(application(provider)) as client:
        response = await client.post("/internal/v1/mood-replies", json=payload(mood_type=mood), headers=AUTH)
    assert response.status_code == 200
    assert len(provider.calls) == 1
    assert provider.calls[0][0].mood_type == mood


async def test_default_three_slots_reject_fourth_and_reuse_only_finished_slot():
    """三项生成同时等待时拒绝第四项；一项结束后名额可复用，不影响另外两项。"""
    entered = asyncio.Queue()
    releases = {}

    class GatedProvider(StubProvider):
        async def generate(self, request, deadline):
            release = releases.setdefault(request.request_id, asyncio.Event())
            await entered.put(request.request_id)
            await release.wait()
            return await super().generate(request, deadline)

    async with client_for(application(GatedProvider())) as client:
        pending = [asyncio.create_task(client.post(
            "/internal/v1/mood-replies", json=payload(request_id=f"request-{i}"), headers=AUTH,
        )) for i in range(3)]
        try:
            for _ in range(3):
                await asyncio.wait_for(entered.get(), 1)
            rejected = await client.post("/internal/v1/mood-replies", json=payload(request_id="fourth"), headers=AUTH)
            assert rejected.status_code == 503
            releases["request-0"].set()
            assert (await pending[0]).status_code == 200
            replacement = asyncio.create_task(client.post(
                "/internal/v1/mood-replies", json=payload(request_id="replacement"), headers=AUTH,
            ))
            pending.append(replacement)
            assert await asyncio.wait_for(entered.get(), 1) == "replacement"
            assert not pending[1].done()
            assert not pending[2].done()
        finally:
            for release in releases.values():
                release.set()
            for task in pending:
                task.cancel()
            await asyncio.gather(*pending, return_exceptions=True)


@pytest.mark.parametrize("capacity", [0, -1, True, "3"])
def test_service_concurrency_requires_positive_integer(capacity):
    """门禁配置拒绝零、负数和隐式类型转换。"""
    with pytest.raises(ValueError):
        make_settings(max_concurrent_requests=capacity)


@pytest.mark.parametrize("attempt", [4, 7])
async def test_later_attempts_preserve_monotonic_correlation(attempt):
    """冷却后允许继续尝试，回包保持累计attempt而非重置为1。"""
    async with client_for(application()) as client:
        response = await client.post("/internal/v1/mood-replies", json=payload(attempt=attempt), headers=AUTH)
    assert response.status_code == 200
    assert response.json()["attempt"] == attempt
