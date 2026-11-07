"""验证无需资源配置的启动、有限风险分支及规则匹配的耗时边界。"""

import subprocess
import sys

import pytest

from app.main import create_app
from app.settings import Settings
from tests.stub_provider import AUTH, StubProvider, client_for, make_settings, payload


@pytest.mark.parametrize("content", ["我想自杀", "今晚我要结束生命", "我正在割腕", "I want to kill myself"])
async def test_support_returns_fixed_reply_without_calling_model(content):
    """风险样例应返回合法的固定回应，并明确标记未调用模型。"""
    provider = StubProvider()
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post("/internal/v1/mood-replies", json=payload(content=content), headers=AUTH)
    assert response.status_code == 200
    data = response.json()
    assert data["result_kind"] == "SUPPORT"
    assert data["model_called"] is False
    assert data["model_name"] is None
    assert data["reply_content"].strip()
    assert len(data["reply_content"].encode("utf-16-le")) // 2 <= 2000
    assert provider.calls == []


@pytest.mark.parametrize("content", ["今天考试好难，压力很大", "我不想自杀，我想好好生活", "新闻提到了自杀预防", "散步后很平静"])
async def test_control_examples_take_the_model_branch(content):
    """普通、明确否定和第三方新闻对照样例应进入模型生成分支。"""
    provider = StubProvider()
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post("/internal/v1/mood-replies", json=payload(content=content), headers=AUTH)
    assert response.json()["result_kind"] == "MODEL"
    assert len(provider.calls) == 1


@pytest.mark.parametrize("legacy_resource_file", [None, "missing-resource-file.json"])
async def test_service_starts_and_handles_both_branches_without_resource_configuration(monkeypatch, legacy_resource_file):
    """无需求助资源配置即可启动；遗留的无效资源路径也不得影响两条回应分支。"""
    for name, value in {
        "XY_AI_INTERNAL_TOKEN": "test-internal-secret",
        "XY_MODEL_BASE_URL": "https://provider.example.invalid/v1",
        "XY_MODEL_API_KEY": "test-provider-secret",
        "XY_MODEL_NAME": "test-model",
        "XY_MODEL_THINKING_MODE": "disabled",
    }.items():
        monkeypatch.setenv(name, value)
    monkeypatch.delenv("XY_HELP_RESOURCES_FILE", raising=False)
    if legacy_resource_file is not None:
        monkeypatch.setenv("XY_HELP_RESOURCES_FILE", legacy_resource_file)
    provider = StubProvider()
    app = create_app(Settings.from_env(), provider)
    async with client_for(app) as client:
        assert (await client.get("/health")).json() == {"ready": True}
        normal = await client.post("/internal/v1/mood-replies", json=payload(), headers=AUTH)
        assert normal.status_code == 200
        assert normal.json()["result_kind"] == "MODEL"
        assert len(provider.calls) == 1
        support = await client.post("/internal/v1/mood-replies", json=payload(content="我想自杀"), headers=AUTH)
        assert support.status_code == 200
        assert support.json()["result_kind"] == "SUPPORT"
        assert support.json()["model_called"] is False
        assert len(provider.calls) == 1


@pytest.mark.parametrize("change", [{"internal_token": ""}, {"model_thinking_mode": "unsupported"}])
def test_missing_secret_or_unsupported_adaptation_cannot_start(change):
    """空密钥或不支持的模型适配配置应阻止启动。"""
    # 复制合法配置后覆盖目标字段，以隔离验证当前配置错误。
    settings = make_settings()
    with pytest.raises(ValueError):
        create_app(Settings(**{**settings.model_dump(), **change}))


def test_repeated_risk_prefix_cannot_block_the_service_process():
    """合法长度的重复短语不得因正则回溯阻塞接口及健康检查。"""
    # 使用独立子进程执行，确保正则阻塞事件循环时测试仍能由外部时限终止。
    script = '''
import asyncio
from app.main import create_app
from tests.stub_provider import AUTH, StubProvider, client_for, make_settings, payload
async def check():
    provider = StubProvider()
    app = create_app(make_settings(), provider)
    async with client_for(app) as client:
        for tail, kind in [("散步", "MODEL"), ("自杀", "SUPPORT")]:
            result = await client.post("/internal/v1/mood-replies", json=payload(content="我" + "想要" * 240 + tail), headers=AUTH)
            assert result.status_code == 200
            assert result.json()["result_kind"] == kind
        assert (await client.get("/health")).json() == {"ready": True}
    assert len(provider.calls) == 1
asyncio.run(check())
'''
    try:
        result = subprocess.run([sys.executable, "-c", script, str()], capture_output=True, timeout=2)
    except subprocess.TimeoutExpired:
        pytest.fail("合法的重复短语阻塞了风险判断及事件循环（外部2秒上限）")
    assert result.returncode == 0, result.stderr.decode()
