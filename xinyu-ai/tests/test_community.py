"""验证社区内部接口的合约、危机分流和独立名额。"""

import asyncio
import json

import pytest

from app.main import create_app
from app.provider import ProviderFailure, messages
from app.safety import support_text
from tests.stub_provider import AUTH, StubProvider, client_for, make_settings, payload

PATH = "/internal/v1/community-replies"
MOOD_PATH = "/internal/v1/mood-replies"


def community_payload(**changes):
    return {
        "request_id": "community-1",
        "topic_name": "",
        "mood_type": "旧心情标签",
        "content": "散步时看到了桂花。",
        "budget_ms": 15000,
        **changes,
    }


@pytest.mark.parametrize("content,kind", [("我不想活了", "SUPPORT"), ("散步看到了桂花。", "MODEL")])
async def test_community_result(content, kind):
    provider = StubProvider()
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post(PATH, json=community_payload(content=content), headers=AUTH)
    called = kind == "MODEL"
    assert response.status_code == 200
    assert response.json() == {
        "request_id": "community-1",
        "reply_content": provider.text if called else support_text(),
        "result_kind": kind,
        "model_called": called,
        "template_version": "community-reply-v1",
        "model_name": "test-model" if called else None,
    }
    assert len(provider.calls) == int(called)


@pytest.mark.parametrize("changes", [
    {"request_id": "?"}, {"extra": 1}, {"topic_name": None}, {"topic_name": "😀" * 26},
    {"topic_name": "\ud800"}, {"mood_type": ""}, {"mood_type": 2},
    {"mood_type": "文" * 31}, {"content": "😀" * 401}, {"content": " \n"},
    {"content": "\udfff"}, {"budget_ms": True}, {"budget_ms": 0}, {"budget_ms": 15001},
])
async def test_invalid_community_request(changes):
    provider = StubProvider()
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post(PATH, content=json.dumps(community_payload(**changes)), headers=AUTH)
    assert response.status_code == 400
    assert response.json() == {"request_id": None, "code": "invalid_request"}
    assert provider.calls == []


async def test_community_requires_fields_and_authentication():
    provider = StubProvider()
    async with client_for(create_app(make_settings(), provider)) as client:
        missing = await client.post(PATH, json={}, headers=AUTH)
        unauthorized = await client.post(PATH, json=community_payload())
    assert missing.status_code == 400
    assert unauthorized.status_code == 401
    assert unauthorized.json()["code"] == "unauthorized"
    assert provider.calls == []


@pytest.mark.parametrize("topic,mood", [
    ("", "旧心情标签"), ("话题数据哨兵", "旧心情标签"), ("", "\u3000"),
])
async def test_community_prompt_keeps_user_data_out_of_system(topic, mood):
    provider = StubProvider()
    data = community_payload(topic_name=topic, mood_type=mood)
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post(PATH, json=data, headers=AUTH)
    assert response.status_code == 200
    system, user = messages(provider.calls[0][0])
    assert system["role"] == "system"
    assert user["role"] == "user"
    assert all(value not in system["content"] for value in (topic, mood, data["content"]) if value.strip())
    assert json.loads(user["content"]) == {key: data[key] for key in ("topic_name", "mood_type", "content")}


@pytest.mark.parametrize("fill_community", [True, False])
async def test_community_and_mood_use_independent_generation_slots(fill_community):
    started = asyncio.Event()
    release = asyncio.Event()

    class BlockingProvider(StubProvider):
        async def generate(self, request, deadline):
            if request.content == "等待释放":
                self.calls.append(request)
                if len(self.calls) == 3:
                    started.set()
                await release.wait()
            return await StubProvider().generate(request, deadline)

    async with client_for(create_app(make_settings(), BlockingProvider())) as client:
        full_path, full_payload = (PATH, community_payload) if fill_community else (MOOD_PATH, payload)
        free_path, free_payload = (MOOD_PATH, payload) if fill_community else (PATH, community_payload)
        waiting = [
            asyncio.create_task(client.post(full_path, json=full_payload(content="等待释放"), headers=AUTH))
            for _ in range(3)
        ]
        try:
            await asyncio.wait_for(started.wait(), timeout=1)
            overloaded = await client.post(full_path, json=full_payload(), headers=AUTH)
            accepted = await client.post(free_path, json=free_payload(), headers=AUTH)
            assert overloaded.status_code == 503
            assert overloaded.json()["code"] == "overloaded"
            assert accepted.status_code == 200
        finally:
            release.set()
            results = await asyncio.gather(*waiting)
        assert all(result.status_code == 200 for result in results)


@pytest.mark.parametrize("code,status", [
    ("provider_timeout", 504), ("provider_error", 502), ("invalid_model_response", 502),
])
async def test_community_provider_failures(code, status):
    class FailingProvider(StubProvider):
        async def generate(self, request, deadline):
            if code == "provider_timeout":
                await asyncio.sleep(1)
            if code == "provider_error":
                raise ProviderFailure(code)
            return await super().generate(request, deadline)

    provider = FailingProvider(" ")
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post(PATH, json=community_payload(budget_ms=30), headers=AUTH)
    assert response.status_code == status
    assert response.json() == {"request_id": "community-1", "code": code}
