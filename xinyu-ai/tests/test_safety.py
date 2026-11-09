"""验证无需资源配置的启动、有限风险分支及规则匹配的耗时边界。"""

import subprocess
import sys

import pytest

from app.main import create_app
from app.settings import Settings
from tests.stub_provider import AUTH, StubProvider, client_for, make_settings, payload


@pytest.mark.parametrize("content", [
    "我想自杀", "今晚我要结束生命", "我正在割腕", "I want to kill myself",
    "我想死", "我想去死", "不想活了", "我不想活了", "想自杀", "活不下去了", "我活不下去了",
    "我现在真的想死", "今天很难受，不想活了", "我不想自杀了，但今晚我想死",
])
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


@pytest.mark.parametrize("content", [
    "今天考试好难，压力很大", "我不想自杀，我想好好生活", "新闻提到了自杀预防", "散步后很平静",
    "我不想自杀了，现在好多了", "我朋友说他想自杀", "他想死", "累死了", "笑死我了", "想死你了", "我想死你了",
    "我不想活在过去了", "不想活成别人期待的样子", "我想死磕这道题",
])
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
        "AI_INTERNAL_TOKEN": "test-internal-secret",
        "OPENAI_BASE_URL": "https://provider.example.invalid/v1",
        "OPENAI_API_KEY": "test-provider-secret",
        "OPENAI_MODEL": "test-model",
        "DEEPSEEK_THINKING_MODE": "disabled",
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


@pytest.mark.parametrize("value", [0, -1, 8193, True, 1.5])
def test_token_budget_rejects_invalid_values(value):
    """本服务预算必须是 1～8192 的严格整数，避免误配置超出本地范围。"""
    with pytest.raises(ValueError):
        make_settings(model_max_tokens=value)


@pytest.mark.parametrize("value,expected", [(None, 4096), ("8192", 8192)])
def test_token_budget_reads_optional_environment_setting(monkeypatch, value, expected):
    """环境未指定预算时使用较大默认值，指定时读取独立的 token 配置。"""
    for name, content in {
        "AI_INTERNAL_TOKEN": "test-internal-secret",
        "OPENAI_BASE_URL": "https://provider.example.invalid/v1",
        "OPENAI_API_KEY": "test-provider-secret",
        "OPENAI_MODEL": "test-model",
        "DEEPSEEK_THINKING_MODE": "enabled",
    }.items():
        monkeypatch.setenv(name, content)
    monkeypatch.delenv("OPENAI_MAX_TOKENS", raising=False)
    if value is not None:
        monkeypatch.setenv("OPENAI_MAX_TOKENS", value)
    assert Settings.from_env().model_max_tokens == expected


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
