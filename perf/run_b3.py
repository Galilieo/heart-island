"""B3：三槽吞吐与排队（任务书 3.3，仅新版）。

到达率分档：30/60/120/240 每分钟（开环定速发送），每档 5 分钟；另加一组证据档
「240/min + 上游延迟 300ms」用于区分瓶颈来自上游延迟×槽位还是 DB/Java 本身。

口径：
- 开环到达：请求 i 的目标发送时刻 = 档起点 + i / rate；发送线程池 32（保存毫秒级，够用）。
- 排队 P95 = 合成上游首次收到该任务请求的时刻 − 客户端保存完成时刻（含 1s 扫描间隔；
  上游日志毫秒级，通过内容标记与客户端记录逐条 join）。
- 执行 P95 = 上游日志 done−recv（1300ms 固定延迟 + 少量开销）。
- 队列深度 = 每秒采样 ai_job 的 queued/running 计数；资源 = ps 采样 Java/Python 的
  CPU%、RSS；DB 连接 = 该隔离库的 PROCESSLIST 连接数。
- 容量拒绝（capacity_exceeded）与失败不入分母剔除，全部单列。
- 限流为本项单独调高（mood 100000/h、day 1000000、global 100000/h，启动参数覆盖），
  与产品默认额度的差异写入报告；排队上限 100/用户活跃 3 为产品常量，不做修改。

运行前提：B1/B2 已结束；Java 以调高额度环境变量重启（见 services.start_java_new）。
"""

import json
import os
import re
import subprocess
import sys
import threading
import time
from queue import Queue

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib
import services as S

NEW = "http://127.0.0.1:18080"
DB = "xinyu_test_eval_b3_new"
LEVELS = [
    {"tag": "L30", "rpm": 30, "minutes": 5, "latency_ms": 1300},
    {"tag": "L60", "rpm": 60, "minutes": 5, "latency_ms": 1300},
    {"tag": "L120", "rpm": 120, "minutes": 5, "latency_ms": 1300},
    {"tag": "L240", "rpm": 240, "minutes": 5, "latency_ms": 1300},
    {"tag": "E240x300ms", "rpm": 240, "minutes": 5, "latency_ms": 300},
]
USERS_PER_LEVEL = 30
DRAIN_TIMEOUT = 900

MOODS = ["平静", "疲惫", "焦虑", "开心", "难过", "期待"]


def ps_sample(pid):
    try:
        out = subprocess.run(["ps", "-o", "pcpu=,rss=", "-p", str(pid)],
                             capture_output=True, text=True, timeout=3).stdout.split()
        return float(out[0]), int(out[1])  # cpu%, rss KB
    except Exception:
        return None, None


def db_sample():
    rows = lib.mysql(
        "SELECT status, COUNT(*) FROM ai_job GROUP BY status", DB)
    counts = {r[0]: int(r[1]) for r in rows}
    conn = lib.mysql(
        "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB = %r" % DB)[0][0]
    return counts, int(conn)


def run_level(level, api, tokens, out_dir):
    tag, rpm, minutes = level["tag"], level["rpm"], level["minutes"]
    total = rpm * minutes
    interval = 60.0 / rpm
    results = [None] * total
    work_q = Queue()
    lock = threading.Lock()
    done_count = [0]

    def worker():
        t = None
        while True:
            item = work_q.get()
            if item is None:
                return
            idx, due = item
            delay = due - time.time()
            if delay > 0:
                time.sleep(delay)
            token = tokens[idx % len(tokens)]
            content = f"B3负载：处理一周积压事务后休息（{tag}-{idx}）"
            record = {"idx": idx, "t_send": lib.now_ms()}
            try:
                if t is None:
                    t = api.with_token(token)
                else:
                    t.headers = {"token": token}
                resp = t.save_mood(MOODS[idx % len(MOODS)], content)
                record["t_done"] = lib.now_ms()
                record["save_ms"] = record["t_done"] - record["t_send"]
                record["http"] = resp.get("code")
                record["content"] = content
                data = resp.get("data") or {}
                record["job_id"] = data.get("jobId")
                record["initial_state"] = data.get("aiReplyState")
            except Exception as exc:
                record["client_error"] = f"{type(exc).__name__}"[:80]
                record["t_done"] = lib.now_ms()
            results[idx] = record
            with lock:
                done_count[0] += 1

    threads = [threading.Thread(target=worker, daemon=True) for _ in range(32)]
    for th in threads:
        th.start()

    # 采样器
    samples = []
    stop_flag = threading.Event()

    def sampler():
        java_pid, py_pid = S.read_pid("java_new"), S.read_pid("python")
        while not stop_flag.is_set():
            try:
                counts, conn = db_sample()
                jcpu, jrss = ps_sample(java_pid)
                pcpu, prss = ps_sample(py_pid)
                samples.append({"t": lib.now_ms(), "jobs": counts, "db_conn": conn,
                                "java_cpu": jcpu, "java_rss_kb": jrss,
                                "py_cpu": pcpu, "py_rss_kb": prss})
            except Exception:
                pass
            time.sleep(1.0)

    sampler_th = threading.Thread(target=sampler, daemon=True)
    sampler_th.start()

    t0 = time.time()
    for i in range(total):
        work_q.put((i, t0 + i * interval))
    for _ in threads:
        work_q.put(None)
    for th in threads:
        th.join()
    send_done = time.time()
    print(f"[{tag}] all {total} sends finished in {send_done - t0:.1f}s")

    # 等全部任务终态（排队上限内最长 ~backlog/2.3s）
    drain_deadline = time.time() + DRAIN_TIMEOUT
    while time.time() < drain_deadline:
        counts, _ = db_sample()
        active = counts.get("queued", 0) + counts.get("running", 0)
        finished = counts.get("succeeded", 0) + counts.get("failed", 0)
        if active == 0:
            break
        time.sleep(2)
    time.sleep(1)
    stop_flag.set()
    sampler_th.join(timeout=3)

    path = os.path.join(out_dir, f"{tag}_requests.jsonl")
    with open(path, "w", encoding="utf-8") as f:
        for r in results:
            if r:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")
    with open(os.path.join(out_dir, f"{tag}_samples.json"), "w", encoding="utf-8") as f:
        json.dump(samples, f, ensure_ascii=False)
    return results, samples


def analyze(level, results, samples):
    tag = level["tag"]
    total = len([r for r in results if r])
    http_ok = [r for r in results if r and r.get("http") == 200]
    save_lat = [r["save_ms"] for r in http_ok]
    by_content = {r["content"]: r for r in http_ok if "content" in r}

    # 上游日志 join：窗口=本档第一条 t_send 前一秒到 drain 结束；按内容序号对齐
    t_start = min(r["t_send"] for r in results if r) / 1000 - 1
    by_idx = {}
    for r in http_ok:
        m = re.search(r"（%s-(\d+)）" % tag, r.get("content", ""))
        if m:
            by_idx[int(m.group(1))] = r
    entries = []
    for e in lib.read_jsonl(lib.upstream_log_path()):
        if e["ts"] / 1000 >= t_start and f"（{tag}-" in e.get("user_text", ""):
            entries.append(e)
    queue_waits, exec_durations = [], []
    for e in entries:
        m = re.search(r"（%s-(\d+)）" % tag, e.get("user_text", ""))
        if not m:
            continue
        r = by_idx.get(int(m.group(1)))
        if not r or "t_done" not in r:
            continue
        queue_waits.append(e["ts"] - r["t_done"])
        if "done_ts" in e and e.get("outcome", "").startswith("ok"):
            exec_durations.append(e["done_ts"] - e["ts"])

    depths = [s["jobs"].get("queued", 0) + s["jobs"].get("running", 0) for s in samples]
    job_rows = lib.mysql(
        "SELECT j.status, j.last_error_code, COUNT(*) FROM ai_job j "
        "JOIN mood_record m ON j.target_id = m.id "
        f"WHERE m.content LIKE '%{tag}-%' GROUP BY j.status, j.last_error_code", DB)
    # 每分钟完成数：上游 ok 完成时间分布
    if entries:
        minute0 = min(e["ts"] for e in entries) // 60000
        per_min = {}
        for e in entries:
            if e.get("outcome", "").startswith("ok") and "done_ts" in e:
                per_min[e["done_ts"] // 60000 - minute0] = per_min.get(e["done_ts"] // 60000 - minute0, 0) + 1
    else:
        per_min = {}
    return {
        "tag": tag, "rpm_target": level["rpm"], "minutes": level["minutes"],
        "sent": total, "http_ok": len(http_ok),
        "client_errors": sum(1 for r in results if r and r.get("client_error")),
        "save_latency": lib.stats(save_lat),
        "upstream_calls": len(entries),
        "queue_wait_ms": lib.stats(queue_waits),
        "exec_duration_ms": lib.stats(exec_durations),
        "max_queue_depth": max(depths) if depths else None,
        "java_cpu_max": max((s["java_cpu"] or 0) for s in samples) if samples else None,
        "java_rss_max_mb": round(max((s["java_rss_kb"] or 0) for s in samples) / 1024, 1) if samples else None,
        "py_cpu_max": max((s["py_cpu"] or 0) for s in samples) if samples else None,
        "py_rss_max_mb": round(max((s["py_rss_kb"] or 0) for s in samples) / 1024, 1) if samples else None,
        "db_conn_max": max((s["db_conn"] or 0) for s in samples) if samples else None,
        "job_outcomes": {f"{r[0]}:{r[1]}": int(r[2]) for r in job_rows},
        "completions_per_minute": per_min,
    }


def main():
    out_dir = os.path.join(lib.RESULTS_ROOT, "b3")
    os.makedirs(out_dir, exist_ok=True)
    lib.create_database(DB, lib.new_schema_parts())
    # 本项专属：调高额度的服务重启（与默认值差异写进 meta 与报告）
    S.stop("java_new")
    time.sleep(2)
    S.start_java_new(DB, rate_limits={"mood_per_hour": 100000, "mood_per_day": 1000000,
                                      "global_per_hour": 100000})
    api = lib.Api(NEW, timeout=30)

    summaries = []
    for level in LEVELS:
        # 证据档需要重启合成上游换延迟（日志追加保留）
        if S.is_running("fake") and level is LEVELS[0]:
            pass
        else:
            S.stop("fake")
            time.sleep(1)
            S.start_fake(latency_ms=level["latency_ms"])
        tokens = []
        for u in range(1, USERS_PER_LEVEL + 1):
            tokens.append(lib.ensure_user(api, f"eval_b3_{level['tag']}_u{u:02d}", "Passw0rd!123"))
        results, samples = run_level(level, api, tokens, out_dir)
        summary = analyze(level, results, samples)
        summaries.append(summary)
        print(json.dumps({k: v for k, v in summary.items() if k != "completions_per_minute"},
                         ensure_ascii=False, indent=1))
        time.sleep(3)

    with open(os.path.join(out_dir, "summary.json"), "w", encoding="utf-8") as f:
        json.dump(summaries, f, ensure_ascii=False, indent=2)
    lib.write_meta(out_dir, {
        "test": "b3_throughput", "levels": LEVELS, "users_per_level": USERS_PER_LEVEL,
        "rate_limits_overridden": {"mood_per_hour": 100000, "mood_per_day": 1000000,
                                   "global_per_hour": 100000},
        "queue_caps_product_constants": {"max_queued": 100, "max_active_per_user": 3},
        "note": "E240x300ms 为瓶颈归因证据档：上游延迟改 300ms，其余与 L240 相同",
    })
    print("saved summary.json")


if __name__ == "__main__":
    main()
