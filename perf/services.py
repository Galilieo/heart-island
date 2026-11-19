"""服务生命周期编排：合成上游 / Python 服务 / 新旧 Java，端口固定 18100/18000/18080/18081。

只管理本任务自己的进程（pid 文件在 perf/results/pids/）；C 段的重启恢复直接复用这里的
start/stop/kill9。所有服务日志落在 perf/results/logs/。
"""

import os
import signal
import subprocess
import time

import httpx

import lib

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
PERF = os.path.join(REPO, "perf")
LOG_DIR = os.path.join(PERF, "results", "logs")
PID_DIR = os.path.join(PERF, "results", "pids")

FAKE_PORT = 18100
PY_PORT = 18000
NEW_PORT = 18080
OLD_PORT = 18081

AI_TOKEN = "eval-internal-token"

JDBC_PARAMS = ("?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8"
               "&useSSL=false&allowPublicKeyRetrieval=true")


def _env_path(name):
    return os.path.join(PID_DIR, name + ".pid")


def _log_path(name):
    os.makedirs(LOG_DIR, exist_ok=True)
    return os.path.join(LOG_DIR, name + ".log")


def _write_pid(name, pid):
    os.makedirs(PID_DIR, exist_ok=True)
    with open(_env_path(name), "w") as f:
        f.write(str(pid))


def read_pid(name):
    try:
        with open(_env_path(name)) as f:
            return int(f.read().strip())
    except (FileNotFoundError, ValueError):
        return None


def is_running(name):
    pid = read_pid(name)
    if pid is None:
        return False
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True


def wait_http(url, timeout_s=120, expect_status=(200, 401, 404, 405, 400)):
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        try:
            r = httpx.get(url, timeout=2)
            if r.status_code in expect_status:
                return True
        except httpx.HTTPError:
            pass
        time.sleep(0.5)
    raise RuntimeError(f"service not ready: {url}")


def start_fake(latency_ms=1300, jitter_ms=0, extra_env=None, name="fake"):
    env = dict(os.environ)
    env.update({"FAKE_PORT": str(FAKE_PORT), "FAKE_LATENCY_MS": str(latency_ms),
                "FAKE_JITTER_MS": str(jitter_ms),
                "FAKE_LOG": lib.upstream_log_path()})
    if extra_env:
        env.update(extra_env)
    log = open(_log_path(name), "a")
    proc = subprocess.Popen(["python3", os.path.join(PERF, "fake_upstream", "app.py")],
                            stdout=log, stderr=subprocess.STDOUT, env=env)
    _write_pid(name, proc.pid)
    wait_http(f"http://127.0.0.1:{FAKE_PORT}/_state")
    return proc.pid


def fake_control(mode):
    httpx.post(f"http://127.0.0.1:{FAKE_PORT}/_control", json={"mode": mode}, timeout=5)
    return fake_state()


def fake_state():
    return httpx.get(f"http://127.0.0.1:{FAKE_PORT}/_state", timeout=5).json()


def fake_release(mode="ok"):
    return httpx.post(f"http://127.0.0.1:{FAKE_PORT}/_release", json={"mode": mode},
                      timeout=30).json()


def start_python(concurrency=3, name="python"):
    env = dict(os.environ)
    env.update({
        "AI_INTERNAL_TOKEN": AI_TOKEN,
        "OPENAI_BASE_URL": f"http://127.0.0.1:{FAKE_PORT}",
        "OPENAI_API_KEY": "eval-fake-key",
        "OPENAI_MODEL": "fake-model",
        "DEEPSEEK_THINKING_MODE": "disabled",
        "AI_SERVICE_CONCURRENCY": str(concurrency),
    })
    log = open(_log_path(name), "a")
    proc = subprocess.Popen(
        ["uv", "run", "--directory", os.path.join(REPO, "xinyu-ai"), "--locked",
         "uvicorn", "app.main:app_from_env", "--factory", "--host", "127.0.0.1",
         "--port", str(PY_PORT), "--log-level", "warning"],
        stdout=log, stderr=subprocess.STDOUT, env=env, cwd=REPO)
    _write_pid(name, proc.pid)
    wait_http(f"http://127.0.0.1:{PY_PORT}/health")
    return proc.pid


def java_env(database, port, worker=True, rate_limits=None, redis_url=""):
    env = dict(os.environ)
    jdbc = (f"jdbc:mysql://127.0.0.1:3306/{database}{JDBC_PARAMS}")
    env.update({
        "SERVER_PORT": str(port),
        "DB_URL": jdbc,
        "DB_USERNAME": lib.TEST_ENV["AI_TEST_DB_USER"],
        "DB_PASSWORD": lib.TEST_ENV["AI_TEST_DB_PASSWORD"],
        "REDIS_URL": redis_url,
        "AI_WORKER_ENABLED": "true" if worker else "false",
        "AI_SERVICE_BASE_URL": f"http://127.0.0.1:{PY_PORT}",
        "AI_INTERNAL_TOKEN": AI_TOKEN,
        # 仅本机测评用的一次性签名密钥（≥32 字节），不入库不入仓。
        "JWT_SECRET": "eval-only-jwt-secret-0123456789abcdef",
    })
    if rate_limits:
        for key, value in rate_limits.items():
            env[f"XINYU_RATE_LIMIT_{key.upper()}"] = str(value)
    return env


def start_java_new(database, name="java_new", rate_limits=None, redis_url="",
                   worker=True, app_log=None):
    env = java_env(database, NEW_PORT, worker=worker,
                   rate_limits=rate_limits, redis_url=redis_url)
    jar = _find_jar(os.path.join(REPO, "xinyu-backend", "target"))
    log = open(app_log or _log_path(name), "a")
    proc = subprocess.Popen(["java", "-jar", jar], stdout=log,
                            stderr=subprocess.STDOUT, env=env, cwd=REPO)
    _write_pid(name, proc.pid)
    wait_http(f"http://127.0.0.1:{NEW_PORT}/user/test/1")
    return proc.pid


def start_java_old(database, name="java_old"):
    env = dict(os.environ)
    env.update({
        "SERVER_PORT": str(OLD_PORT),
        "DB_URL": f"jdbc:mysql://127.0.0.1:3306/{database}{JDBC_PARAMS}",
        "DB_USERNAME": lib.TEST_ENV["AI_TEST_DB_USER"],
        "DB_PASSWORD": lib.TEST_ENV["AI_TEST_DB_PASSWORD"],
        "DEEPSEEK_BASE_URL": f"http://127.0.0.1:{FAKE_PORT}",
        "DEEPSEEK_API_KEY": "eval-fake-key",
        "JWT_SECRET": "eval-only-jwt-secret-0123456789abcdef",
    })
    jar = _find_jar(os.path.join("/tmp/xinyu-eval-old-2036e7af/xinyu-backend/target"))
    log = open(_log_path(name), "a")
    proc = subprocess.Popen(["java", "-jar", jar], stdout=log,
                            stderr=subprocess.STDOUT, env=env, cwd=REPO)
    _write_pid(name, proc.pid)
    wait_http(f"http://127.0.0.1:{OLD_PORT}/user/test/1")
    return proc.pid


def _find_jar(target_dir):
    candidates = [f for f in os.listdir(target_dir) if f.endswith(".jar")
                  and not f.endswith("-sources.jar")]
    if not candidates:
        raise RuntimeError(f"no jar in {target_dir}")
    return os.path.join(target_dir, candidates[0])


def stop(name, sig=signal.SIGTERM, wait_s=40):
    pid = read_pid(name)
    if pid is None:
        return False
    try:
        os.kill(pid, sig)
    except ProcessLookupError:
        return False
    deadline = time.time() + wait_s
    while time.time() < deadline:
        try:
            os.kill(pid, 0)
            time.sleep(0.3)
        except ProcessLookupError:
            return True
    return False


def kill9(name):
    """kill -9 并等待进程消失；返回是否成功。"""
    pid = read_pid(name)
    if pid is None:
        return False
    try:
        os.kill(pid, signal.SIGKILL)
    except ProcessLookupError:
        return True
    deadline = time.time() + 15
    while time.time() < deadline:
        try:
            os.kill(pid, 0)
            time.sleep(0.2)
        except ProcessLookupError:
            return True
    return False


def java_pid(name="java_new"):
    """Java 直接 java -jar 启动，pid 即 JVM 进程；供 C 段按 pid kill。"""
    return read_pid(name)


def python_child_pid():
    """uvicorn 真实进程：uv 包装层的直接子进程（uv run 会 fork 一个 .venv python）。"""
    import subprocess
    pid = read_pid("python")
    if pid is None:
        return None
    out = subprocess.run(["pgrep", "-P", str(pid)], capture_output=True, text=True).stdout.split()
    return int(out[0]) if out else pid


def freeze_python():
    os.kill(python_child_pid(), signal.SIGSTOP)


def thaw_python():
    os.kill(python_child_pid(), signal.SIGCONT)


def stop_all():
    for name in ("java_new", "java_old", "python", "fake"):
        stop(name)
