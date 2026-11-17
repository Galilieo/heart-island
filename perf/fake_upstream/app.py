"""OpenAI 兼容的合成上游：固定延迟、可控故障注入，只用于本机测评（任务书 A 段第 3 条）。

不实现真实模型，只按控制模式返回预设正文或故障。所有请求写入 JSONL 日志，
用于上下游调用对账（重复执行检测、到达时间统计）。

故障模式（#FAULT:<mode># 或全局 /_control 切换）：
  ok              正常返回（默认，走 FAKE_LATENCY_MS/FAKE_JITTER_MS 延迟）
  empty_content   合法 JSON，message.content 为空串
  blank_content   合法 JSON，content 为纯空白
  truncated_json  合法 JSON 前缀被截断（HTTP 层完整、JSON 不可解析）
  invalid_json    完全非法的 JSON 文本
  http_500        HTTP 500
  http_429        HTTP 429
  read_timeout    睡 FAKE_TIMEOUT_MS（默认 50s，超过两侧读超时）后再正常返回
  hold            收到请求后挂起，直到 POST /_release（用于 C 段时点控制）
  slow_body       立即发响应头，正文按 FAKE_SLOW_CHUNK_MS 慢速分块发送（"完成前"时点）

控制端点：
  GET  /_state           当前模式、计数、挂起请求数
  POST /_control         {"mode": "..."} 切换全局默认模式
  POST /_release         释放全部 hold 请求（可带 {"mode":"ok"} 指定释放后的返回模式）

运行：python3 perf/fake_upstream/app.py   （默认 127.0.0.1:18100，仅标准库）
"""

import json
import os
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = os.environ.get("FAKE_HOST", "127.0.0.1")
PORT = int(os.environ.get("FAKE_PORT", "18100"))
LATENCY_MS = int(os.environ.get("FAKE_LATENCY_MS", "1300"))
JITTER_MS = int(os.environ.get("FAKE_JITTER_MS", "0"))
TIMEOUT_MS = int(os.environ.get("FAKE_TIMEOUT_MS", "50000"))
SLOW_CHUNK_MS = int(os.environ.get("FAKE_SLOW_CHUNK_MS", "1000"))
LOG_PATH = os.environ.get(
    "FAKE_LOG", os.path.join(os.path.dirname(__file__), "..", "results", "fake_upstream.log")
)

# 与旧版兜底文案刻意不同，避免把正常回复误判为兜底。
OK_CONTENT = "连着两天弄到这个点，是真的顶。今天先别再压自己了，早点睡。"

MODES = {
    "ok", "empty_content", "blank_content", "truncated_json", "invalid_json",
    "http_500", "http_429", "read_timeout", "hold", "slow_body",
}

FAULT_MARKER = re.compile(r"#FAULT:([a-z_0-9]+)#")

state_lock = threading.Lock()
state = {"mode": "ok", "received": 0, "faults": 0, "held": 0, "released_mode": None}
release_event = threading.Event()

log_lock = threading.Lock()


def ensure_log():
    os.makedirs(os.path.dirname(LOG_PATH), exist_ok=True)


def write_log(record):
    line = json.dumps(record, ensure_ascii=False)
    with log_lock:
        with open(LOG_PATH, "a", encoding="utf-8") as f:
            f.write(line + "\n")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # 安静模式：访问记录进 JSONL，不进 stderr
        pass

    # ---- 控制面 ----
    def _json_body(self):
        length = int(self.headers.get("Content-Length", 0) or 0)
        raw = self.rfile.read(length) if length else b"{}"
        return json.loads(raw or b"{}")

    def _send_json(self, obj, status=200):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/_state":
            with state_lock:
                snapshot = dict(state)
            self._send_json(snapshot)
            return
        self._send_json({"error": "not_found"}, 404)

    def do_POST(self):
        global state
        if self.path == "/_control":
            data = self._json_body()
            mode = data.get("mode")
            if mode not in MODES:
                self._send_json({"error": "unknown_mode", "modes": sorted(MODES)}, 400)
                return
            with state_lock:
                state["mode"] = mode
            self._send_json({"mode": mode})
            return
        if self.path == "/_release":
            data = self._json_body()
            mode = data.get("mode", "ok")
            if mode not in MODES:
                self._send_json({"error": "unknown_mode"}, 400)
                return
            release_event.set()
            with state_lock:
                state["released_mode"] = mode
            self._send_json({"released": True, "mode": mode})
            return
        if self.path == "/chat/completions":
            try:
                self.handle_completion()
            except (BrokenPipeError, ConnectionResetError):
                # 注入读超时后客户端已断开，写回失败属于预期路径。
                self.close_connection = True
            return
        self._send_json({"error": "not_found"}, 404)

    # ---- 数据面 ----
    def read_request_body(self):
        """读完整请求体；兼容 Content-Length 与 Transfer-Encoding: chunked。

        旧版 Java（2036e7af 的 RestTemplate）对含中文的 JSON 一律用 chunked 发送，
        只按 Content-Length 读会得到空体（2026-11-17 B2 旧侧故障注入失效的根因）。
        """
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            body = bytearray()
            while True:
                size_line = self.rfile.readline(64).strip()
                chunk_size = int(size_line.split(b";")[0], 16)
                if chunk_size == 0:
                    self.rfile.readline(4)  # 尾部 CRLF
                    return bytes(body)
                body.extend(self.rfile.read(chunk_size))
                self.rfile.readline(4)  # 块尾 CRLF
                if len(body) > 4 * 1024 * 1024:
                    raise ValueError("chunked body too large")
        length = int(self.headers.get("Content-Length", 0) or 0)
        return self.rfile.read(length) if length else b""

    def handle_completion(self):
        raw = self.read_request_body()
        recv_ns = time.time_ns()
        recv_ms = recv_ns // 1_000_000

        mode = None
        user_text = ""
        try:
            payload = json.loads(raw)
            for message in payload.get("messages", []):
                if message.get("role") == "user":
                    user_text += str(message.get("content", ""))
            match = FAULT_MARKER.search(user_text)
            if match and match.group(1) in MODES:
                mode = match.group(1)
        except (ValueError, AttributeError):
            user_text = "<unparseable>"

        with state_lock:
            if mode is None:
                mode = state["mode"]
            state["received"] += 1
            if mode != "ok":
                state["faults"] += 1
            current_mode = state["mode"]

        record = {
            "ts": recv_ms,
            "mode": mode,
            "global_mode": current_mode,
            "user_text": user_text[:2000],
        }

        if mode == "http_500":
            record["outcome"] = "http_500"
            write_log(record)
            self._send_json({"error": "internal_error"}, 500)
            return
        if mode == "http_429":
            record["outcome"] = "http_429"
            write_log(record)
            self._send_json({"error": "rate_limited"}, 429)
            return
        if mode == "hold":
            release_event.clear()
            with state_lock:
                state["held"] += 1
            record["outcome"] = "held"
            write_log(record)
            release_event.wait()
            with state_lock:
                state["held"] -= 1
                release_mode = state.get("released_mode") or "ok"
            self.respond_ok(record, note=f"released:{release_mode}")
            return
        if mode == "read_timeout":
            record["outcome"] = "read_timeout_sleep"
            write_log(record)
            time.sleep(TIMEOUT_MS / 1000)
            self.respond_ok(record, note="after_timeout_sleep")
            return
        if mode == "slow_body":
            self.slow_body(record)
            return

        jitter = 0
        if JITTER_MS > 0:
            import random
            jitter = random.randint(0, JITTER_MS)
        time.sleep((LATENCY_MS + jitter) / 1000)

        if mode == "empty_content":
            record["outcome"] = "empty_content"
            write_log(record)
            body = self.completion_body("")
            self._send_raw(body)
            return
        if mode == "blank_content":
            record["outcome"] = "blank_content"
            write_log(record)
            body = self.completion_body("   \n\t ")
            self._send_raw(body)
            return
        if mode == "truncated_json":
            record["outcome"] = "truncated_json"
            write_log(record)
            cut = self.completion_body(OK_CONTENT)[:37]
            self._send_raw(cut.encode("utf-8"))
            return
        if mode == "invalid_json":
            record["outcome"] = "invalid_json"
            write_log(record)
            self._send_raw(b"not-json{[")
            return

        self.respond_ok(record)

    @staticmethod
    def completion_body(content):
        payload = {
            "id": "chatcmpl-fake",
            "object": "chat.completion",
            "created": int(time.time()),
            "model": "fake-model",
            "choices": [
                {"index": 0, "message": {"role": "assistant", "content": content},
                 "finish_reason": "stop"}
            ],
            "usage": {"prompt_tokens": 800, "completion_tokens": 40,
                      "total_tokens": 840},
        }
        return json.dumps(payload, ensure_ascii=False).encode("utf-8")

    def _send_raw(self, body, status=200, chunked=False):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        if chunked:
            self.send_header("Transfer-Encoding", "chunked")
            self.end_headers()
        else:
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    def respond_ok(self, record, note="ok"):
        record["outcome"] = f"ok:{note}" if note != "ok" else "ok"
        record["done_ts"] = time.time_ns() // 1_000_000
        write_log(record)
        self._send_raw(self.completion_body(OK_CONTENT))

    def slow_body(self, record):
        """响应头立即发出，正文每 SLOW_CHUNK_MS 发 8 字节，模拟“上游已完成生成、消费端未收完”。"""
        record["outcome"] = "slow_body_started"
        write_log(record)
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Transfer-Encoding", "chunked")
        self.end_headers()
        body = self.completion_body(OK_CONTENT)
        for i in range(0, len(body), 8):
            chunk = body[i:i + 8]
            self.wfile.write(f"{len(chunk):x}\r\n".encode() + chunk + b"\r\n")
            self.wfile.flush()
            time.sleep(SLOW_CHUNK_MS / 1000)
        self.wfile.write(b"0\r\n\r\n")
        record2 = dict(record)
        record2["outcome"] = "slow_body_finished"
        record2["done_ts"] = time.time_ns() // 1_000_000
        write_log(record2)


def main():
    ensure_log()
    # request_queue_size 默认 5，会被 JDK HttpURLConnection 的并发连接打爆（Connection reset），
    # 必须放大；实测 2026-11-17 B1 旧版 c5/c10 因此丢过上游调用。
    ThreadingHTTPServer.request_queue_size = 256
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    server.daemon_threads = True
    print(f"fake upstream listening on http://{HOST}:{PORT} latency={LATENCY_MS}ms log={LOG_PATH}")
    server.serve_forever()


if __name__ == "__main__":
    main()
