"""B1：保存接口延迟对照（旧同步 vs 新异步），任务书 3.1。

设计要点（写进报告的口径）：
- 三档并发（1/5/10）× 每档 200 次，两侧各自独立跑；同一合成上游延迟 1300ms、0 抖动。
- 闭环（closed-loop）并发：旧版 worker 完成一次同步保存即下一单；新版 worker 完成
  「保存 + 轮询到任务终态」才下一单。这使两侧到达过程近似一致（≈ c/(上游延迟+开销)），
  否则异步侧保存毫秒级返回会把 200 单瞬间灌满队列，测的就变成容量拒绝而非保存确认。
  突发到达下的排队/容量行为由 B3 专门测。
- 每档独立 20 个合成用户（新版限流默认 20 次/时/用户；三档共 60 用户避免额度干扰，
  全局 1000/时内）。
- 新版任务终态轮询间隔 200ms，超时 60s；结果可见耗时=首次观测到终态的时刻-保存发起时刻，
  含轮询粒度（+0~200ms），报告注明。
- 失败、超时不出分母：单独计数（业务错误码、客户端超时）。
"""

import argparse
import json
import os
import sys
import threading
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib
import services as S

NEW = "http://127.0.0.1:18080"
OLD = "http://127.0.0.1:18081"
PROFILES = [(1, 200), (5, 200), (10, 200)]
POLL_INTERVAL = 0.2
JOB_WAIT_TIMEOUT = 60.0

MOODS = ["平静", "疲惫", "焦虑", "开心", "难过", "委屈", "期待", "满足", "迷茫", "生气", "感动", "孤独", "其他"]
CONTENT = "测评负载内容：今天把攒了几天的事情处理完，晚上想早点休息"


def truncate_upstream_log():
    path = lib.upstream_log_path()
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w").close()


def run_profile(side, api_factory, tokens, concurrency, total, out_path, label):
    """闭环压一档；tokens 为该档专属用户 token 列表。"""
    results = [None] * total
    next_index = [0]
    lock = threading.Lock()
    start_gate = threading.Event()

    def worker():
        api = api_factory()
        start_gate.wait()
        while True:
            with lock:
                idx = next_index[0]
                if idx >= total:
                    return
                next_index[0] += 1
            token = tokens[idx % len(tokens)]
            content = f"{CONTENT}（{label}-{idx + 1}）"
            mood = MOODS[idx % len(MOODS)]
            record = {"idx": idx, "user_slot": idx % len(tokens), "mood": mood,
                      "t_send": lib.now_ms()}
            try:
                if side == "old":
                    t = api.with_token(token)
                    resp = t.save_mood(mood, content)
                    record["t_save_done"] = lib.now_ms()
                    record["http"] = resp.get("code")
                    record["save_ms"] = record["t_save_done"] - record["t_send"]
                    # 旧版保存返回即结果可见（同步链路）
                    record["terminal"] = "succeeded" if resp.get("code") == 200 else f"http_{resp.get('code')}"
                    record["visible_ms"] = record["save_ms"]
                else:
                    t = api.with_token(token)
                    resp = t.save_mood(mood, content)
                    record["t_save_done"] = lib.now_ms()
                    record["http"] = resp.get("code")
                    record["save_ms"] = record["t_save_done"] - record["t_send"]
                    data = resp.get("data") or {}
                    job_id = data.get("jobId")
                    record["job_id"] = job_id
                    record["initial_state"] = data.get("aiReplyState")
                    if resp.get("code") != 200 or job_id is None:
                        record["terminal"] = f"http_{resp.get('code')}"
                    else:
                        deadline = time.monotonic() + JOB_WAIT_TIMEOUT
                        terminal = "poll_timeout"
                        while time.monotonic() < deadline:
                            j = t.job(job_id)
                            status = (j.get("data") or {}).get("status")
                            if status in ("succeeded", "failed", "cancelled", "superseded"):
                                terminal = status
                                record["error_code"] = (j.get("data") or {}).get("errorCode")
                                break
                            time.sleep(POLL_INTERVAL)
                        record["terminal"] = terminal
                        record["t_visible"] = lib.now_ms()
                        record["visible_ms"] = record["t_visible"] - record["t_send"]
            except Exception as exc:  # 客户端异常也算样本，不丢
                record["client_error"] = f"{type(exc).__name__}: {exc}"[:200]
            results[idx] = record

    threads = [threading.Thread(target=worker) for _ in range(concurrency)]
    for th in threads:
        th.start()
    start_gate.set()
    for th in threads:
        th.join()

    with open(out_path, "a", encoding="utf-8") as f:
        for record in results:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")
    return results


def summarize(records):
    saves_ok = [r for r in records if r.get("http") == 200]
    save_lat = [r["save_ms"] for r in records if "save_ms" in r]
    err = [r for r in records if r.get("http") != 200 or "client_error" in r]
    terminals = {}
    for r in records:
        terminals[r.get("terminal", "missing")] = terminals.get(r.get("terminal", "missing"), 0) + 1
    summary = {
        "n": len(records),
        "save_latency": lib.stats(save_lat),
        "http_ok": len(saves_ok),
        "errors": len(err),
        "error_kinds": {},
        "terminals": terminals,
    }
    for r in err:
        key = r.get("client_error") or f"http_{r.get('http')}"
        summary["error_kinds"][key] = summary["error_kinds"].get(key, 0) + 1
    visible = [r["visible_ms"] for r in records
               if r.get("terminal") == "succeeded" and "visible_ms" in r]
    summary["visible_latency_succeeded_only"] = lib.stats(visible)
    codes = {}
    for r in records:
        if r.get("error_code"):
            codes[r["error_code"]] = codes.get(r["error_code"], 0) + 1
    if codes:
        summary["job_error_codes"] = codes
    return summary


def upstream_window_stats():
    entries = lib.read_jsonl(lib.upstream_log_path())
    durations = []
    for e in entries:
        if "done_ts" in e and e.get("outcome", "").startswith("ok"):
            durations.append(e["done_ts"] - e["ts"])
    return {"calls": len(entries), "duration_ms": lib.stats(durations) if durations else None}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=os.path.join(lib.RESULTS_ROOT, "b1"))
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    api_new = lib.Api(NEW, timeout=JOB_WAIT_TIMEOUT + 30)
    api_old = lib.Api(OLD, timeout=JOB_WAIT_TIMEOUT + 30)

    tokens_new, tokens_old = [], []
    for i in range(1, 61):
        name = f"eval_b1_u{i:02d}"
        tokens_new.append(lib.ensure_user(api_new, name, "Passw0rd!123"))
        tokens_old.append(lib.ensure_user(api_old, name, "Passw0rd!123"))

    all_summaries = {"new": {}, "old": {}, "upstream": {}}
    for side, api, tokens in (("new", api_new, tokens_new), ("old", api_old, tokens_old)):
        detail_path = os.path.join(args.out, f"{side}_requests.jsonl")
        open(detail_path, "w").close()
        for slot, (concurrency, total) in enumerate(PROFILES):
            token_slice = tokens[slot * 20:(slot + 1) * 20]
            truncate_upstream_log()
            label = f"{side}-c{concurrency}"
            print(f"running {label}: {total} requests, closed-loop concurrency {concurrency}")
            records = run_profile(side, lambda: api, token_slice, concurrency, total,
                                  detail_path, label)
            summary = summarize(records)
            summary["upstream"] = upstream_window_stats()
            all_summaries[side][f"c{concurrency}"] = summary
            print(json.dumps(summary, ensure_ascii=False, indent=1))
            time.sleep(2)

    lib.write_meta(args.out, {
        "test": "b1_save_latency",
        "profiles": [{"concurrency": c, "total": n} for c, n in PROFILES],
        "poll_interval_ms": int(POLL_INTERVAL * 1000),
        "upstream_latency_ms": 1300,
        "users_per_profile": 20,
        "note": "闭环并发=完整用户旅程（新版含等待任务终态）；详见脚本 docstring",
    })
    with open(os.path.join(args.out, "summary.json"), "w", encoding="utf-8") as f:
        json.dump(all_summaries, f, ensure_ascii=False, indent=2)
    print("saved", os.path.join(args.out, "summary.json"))


if __name__ == "__main__":
    main()
