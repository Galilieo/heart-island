"""仅隔离集成测试启动：真实 ASGI 端点，供应商强制为内存 stub，无真实模型配置。"""
import socket
import uvicorn
from app.main import create_app
from tests.stub_provider import StubProvider, make_settings


def application():
    provider = StubProvider('{"body":"散步的记录在这里{{r1}}","ps":null}')
    app = create_app(make_settings(), provider)

    @app.get('/test/state')
    async def state():
        return {'calls': [request.model_dump() for request, deadline in provider.calls]}

    return app


if __name__ == '__main__':
    listener = socket.socket()
    listener.bind(('127.0.0.1', 0))
    port = listener.getsockname()[1]
    print(port, flush=True)
    uvicorn.run(application(), host='127.0.0.1', port=port, fd=listener.fileno(), access_log=False, log_level='warning')
