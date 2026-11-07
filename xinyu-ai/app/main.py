"""提供内部 HTTP 接口；用户身份、业务归属和持久化由 Java 负责。"""

import asyncio
import hmac
import json
import logging

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.contracts import ModelReply, MoodReplyRequest, MoodReplyResponse
from app.provider import HttpProvider, Provider, ProviderFailure
from app.safety import build_support_reply
from app.settings import Settings


logger = logging.getLogger("xinyu_ai")


def create_app(settings: Settings, provider: Provider | None = None) -> FastAPI:
    """创建内部回应服务，风险分支使用内置文案，无需外部资源文件。

    Args:
        settings: 已校验的服务配置。
        provider: 可选的模型适配器；未传入时使用 HTTP 适配器。

    Returns:
        注册了健康检查和单次回应接口的 FastAPI 应用。

    """
    provider = provider if provider is not None else HttpProvider(settings)
    app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)
    active = 0

    def error(code: str, request_id: str | None = None) -> JSONResponse:
        """将内部错误分类映射为 HTTP 响应，仅记录安全追踪字段。"""
        status = {
            "invalid_request": 400, "unauthorized": 401, "provider_timeout": 504,
            "provider_error": 502, "invalid_model_response": 502, "overloaded": 503,
        }[code]
        logger.info("request_finished request_id=%s code=%s", request_id, code)
        return JSONResponse({"request_id": request_id, "code": code}, status_code=status)

    @app.get("/health")
    async def health():
        """返回应用就绪状态，不探测供应商，也不暴露配置。"""
        return {"ready": True}

    @app.post("/internal/v1/mood-replies")
    async def reply(request: Request):
        """在总时限内完成认证、输入校验、风险分流和单次回应。"""
        nonlocal active
        start = asyncio.get_running_loop().time()
        request_id = None
        try:
            async with asyncio.timeout_at(start + settings.request_timeout_seconds):
                headers = request.scope["headers"]
                if sum(len(k) + len(v) + 4 for k, v in headers) > 8192:
                    return error("invalid_request")
                auth = [value for key, value in headers if key.lower() == b"authorization"]
                expected = b"Bearer " + settings.internal_token.get_secret_value().encode("ascii")
                if len(auth) != 1 or not hmac.compare_digest(auth[0], expected):
                    return error("unauthorized")
                body = bytearray()
                async for chunk in request.stream():
                    body.extend(chunk)
                    if len(body) > 16384:
                        return error("invalid_request")
                try:
                    data = MoodReplyRequest.model_validate(json.loads(body))
                except (ValueError, TypeError, RecursionError):
                    return error("invalid_request")
                request_id = data.request_id
                if active >= 1:
                    return error("overloaded", request_id)
                # 检查与计数递增之间不能出现 await，避免同一事件循环中的请求交错准入。
                active += 1
                try:
                    deadline = min(start + settings.request_timeout_seconds, start + data.budget_ms / 1000)
                    if asyncio.get_running_loop().time() >= deadline:
                        raise TimeoutError
                    async with asyncio.timeout_at(deadline):
                        support = build_support_reply(data.content)
                        if asyncio.get_running_loop().time() >= deadline:
                            raise TimeoutError
                        if support is not None:
                            text, kind, called, model = support, "SUPPORT", False, None
                        else:
                            raw = await provider.generate(data, deadline)
                            try:
                                validated = ModelReply(reply_content=raw.reply_content, model_name=raw.model_name)
                            except (ValidationError, AttributeError):
                                return error("invalid_model_response", request_id)
                            text, kind, called, model = validated.reply_content, "MODEL", True, validated.model_name
                        response = MoodReplyResponse(
                            **data.model_dump(include={"request_id", "job_id", "attempt", "source_version"}),
                            reply_content=text, result_kind=kind, model_called=called, model_name=model,
                        )
                        result = JSONResponse(response.model_dump())
                        # 异步超时无法抢占同步校验和序列化，返回成功前必须再次检查截止时间。
                        if asyncio.get_running_loop().time() >= deadline:
                            raise TimeoutError
                        return result
                finally:
                    active -= 1
        except TimeoutError:
            return error("provider_timeout", request_id)
        except ProviderFailure as failure:
            return error(failure.code, request_id)
        except Exception:
            # 异常文本和堆栈可能携带原文、密钥或上游响应，因此只返回固定错误分类。
            return error("provider_error", request_id)

    return app


def app_from_env() -> FastAPI:
    """从进程环境读取配置，供 Uvicorn 以应用工厂模式启动服务。"""
    return create_app(Settings.from_env())
