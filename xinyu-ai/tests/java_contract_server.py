"""仅供 Java 回环契约测试启动的真实 Python 应用，供应商层固定为离线替身。"""

import socket

import uvicorn

from app.main import create_app
from tests.stub_provider import StubProvider, make_settings


def main():
    """绑定随机回环端口；stdout 只输出端口，测试进程不读取真实模型配置。"""
    provider = StubProvider()
    app = create_app(make_settings(internal_token="synthetic-internal-secret"), provider)

    @app.get("/test/provider-calls")
    async def calls():
        """仅测试进程注册：返回合成供应商调用数，不返回请求正文。"""
        return {"count": len(provider.calls)}

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        listener.bind(("127.0.0.1", 0))
        listener.listen(16)
        print(listener.getsockname()[1], flush=True)
        config = uvicorn.Config(app, log_level="critical", access_log=False)
        uvicorn.Server(config).run(sockets=[listener])


if __name__ == "__main__":
    main()
