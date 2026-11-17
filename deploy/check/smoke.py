#!/usr/bin/env python3
"""只执行 GET 健康、权限和列表检查；令牌仅从进程环境读取，不打印响应正文。"""

import json
import os
import urllib.error
import urllib.request
from urllib.parse import urlsplit


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def get(base, path, token=None):
    """不跟随重定向，防止测试令牌被带到意外站点。"""
    url = urlsplit(base)
    if url.scheme not in {'http', 'https'} or not url.hostname or url.username or url.password or url.query or url.fragment:
        raise ValueError('检查地址格式无效')
    request = urllib.request.Request(base.rstrip('/') + path)
    if token:
        request.add_header('token', token)
    try:
        with urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect()).open(request, timeout=10) as response:
            return response.status, response.read(1048576)
    except urllib.error.HTTPError as error:
        return error.code, error.read(1048576)
    except OSError:
        raise RuntimeError('检查连接失败，地址与响应不回显') from None


def expect_api(base, path, token):
    status, raw = get(base, path, token)
    assert status == 200, '接口 HTTP 状态不符合预期'
    body = json.loads(raw)
    assert body.get('code') == 200, '接口业务结果不符合预期'
    return body.get('data')


def main():
    backend = os.environ.get('SMOKE_BACKEND_URL', 'http://127.0.0.1:8080')
    python = os.environ.get('SMOKE_PYTHON_URL', 'http://127.0.0.1:8000')
    assert get(backend, '/hello')[0] == 200, 'Java 健康检查失败'
    status, body = get(python, '/health')
    assert status == 200 and json.loads(body) == {'ready': True}, 'Python 健康检查失败'
    assert get(backend, '/mood/list')[0] == 401, '未登录心情接口未被保护'
    assert get(backend, '/admin/overview')[0] == 401, '未登录后台未被保护'
    checks = ['java_health', 'python_health', 'anonymous_auth_guard']

    member = os.environ.get('SMOKE_USER_TOKEN')
    if member:
        for path in ['/mood/list', '/mood/trend/recent7', '/mood/analysis', '/mood/report/week',
                     '/community/report/mine', '/community/post/list']:
            expect_api(backend, path, member)
        assert get(backend, '/admin/overview', member)[0] == 403, '普通用户可访问后台'
        checks.append('member_reads_and_admin_guard')

    admin = os.environ.get('SMOKE_ADMIN_TOKEN')
    if admin:
        overview = expect_api(backend, '/admin/overview', admin)
        assert overview['rateLimitBackend'] == 'redis', '限流后端尚未显示 Redis'
        for path in ['/admin/user/page', '/admin/post/page', '/admin/reply/page', '/admin/report/page',
                     '/admin/topic/list', '/admin/ai-replies/page']:
            expect_api(backend, path, admin)
        checks.append('admin_lists_and_redis')

    frontend = os.environ.get('SMOKE_FRONTEND_URL')
    if frontend:
        for path in ['/', '/review', '/profile/reports', '/admin/reports']:
            status, body = get(frontend, path)
            assert status == 200 and b'<div id="app">' in body, '前端 history 回退检查失败'
        for path in ['/internal/', '/api/internal/', '/api/internal/v1/mood-replies']:
            assert get(frontend, path)[0] == 404, '内部接口公网路径未封闭'
        checks.append('frontend_history_and_internal_denial')
    print(json.dumps({'passed': checks, 'member_checked': bool(member), 'admin_checked': bool(admin)}, ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # 不打印HTTP响应、请求头、环境内容或第三方错误，防止令牌/原文进入报告。
        raise SystemExit('smoke failed: ' + type(error).__name__) from None
