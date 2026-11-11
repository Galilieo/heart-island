"""本机全链路测试：真实Python应用及HttpProvider，只有供应商HTTP响应为可控合成数据。"""

import asyncio
import json
import os
import socket

import uvicorn
from fastapi import Request

from app.main import create_app
from tests.stub_provider import make_settings


def build_app(port: int):
    """上游和内部接口监听同一本机端口；测试控制入口不能用于生产。"""
    app = create_app(make_settings(
        model_base_url=f"http://127.0.0.1:{port}/v1",
        request_timeout_seconds=2,
    ))
    calls = []
    gates = {}

    @app.get("/test/calls")
    async def observed_calls():
        return {"calls": calls}

    @app.post("/test/release")
    async def release(request: Request):
        content = (await request.json())["content"]
        gates.setdefault(content, asyncio.Event()).set()
        return {"ok": True}

    @app.post("/v1/chat/completions")
    async def supplier(request: Request):
        body = await request.json()
        raw = body["messages"][-1]["content"]
        try:
            content = json.loads(raw)["content"]
        except (ValueError, TypeError, KeyError):
            # 社区Java入口沿用文本prompt，日记Python入口使用JSON；两条真实传输共用同一替身。
            content = raw.removeprefix("帖子内容：\n")
        calls.append(content)
        if content.startswith("等待"):
            await gates.setdefault(content, asyncio.Event()).wait()
        if content.startswith("超时"):
            await asyncio.sleep(3)
        reply = "" if content.startswith("空回复") else "这是本地合成回复，记录已被认真读到。"
        return {"model": "test-model", "choices": [{
            "finish_reason": "stop", "message": {"content": reply},
        }]}

    return app


if __name__ == "__main__":
    listener = socket.socket()
    listener.bind(("127.0.0.1", int(os.environ.get("FULL_STACK_TEST_PORT", "0"))))
    port = listener.getsockname()[1]
    print(port, flush=True)
    uvicorn.run(build_app(port), log_level="warning", access_log=False, host="127.0.0.1",
                port=port, fd=listener.fileno())
