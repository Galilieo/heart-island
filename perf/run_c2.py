"""C2：重启恢复取证（任务书 4.2）——真实 kill -9，三时点各 ≥10 次，新版隔离环境。

时点定义与证据（每次运行按上游日志证据归类，不凭时序猜测）：
- P1 领取后/上游未收到：先 SIGSTOP uvicorn 子进程再保存——任务被领取（running）后
  Java 的内部调用必然阻塞，Python→上游这一步未发生；kill 时上游日志无该标记即成立。
- P2 生成中：内容标记 #FAULT:hold#，上游日志出现 held 后 kill -9。
- P3 完成前：内容标记 #FAULT:slow_body#（响应头已发、正文分块慢发），日志出现
  slow_body_started 后 kill -9——此时"供应商侧已生成"（slow_body_finished 稍后仍会记录），
  用于佐证"数据库状态不能说明供应商侧是否已执行/计费"。

每轮流程：save（专属用户）→ 观测 running/上游证据 → kill -9 Java → 释放挂起/解冻 Python →
重启 Java（SmartLifecycle 启动 recoverInterrupted）→ 等恢复 → 断言：
遗留 running 数（全局应为 0）、本任务终态 failed/execution_interrupted、
重启后再次上游调用次数（应为 0，无自动重试）、mood_record.ai_reply 保持 NULL、无 ai_reply 日志行。
P1 解冻后允许出现至多 1 次"孤儿调用"（冻结期间积压的请求在解冻后被处理，客户端已死）——
它发生在 kill 之后、重启之前，单列记录不计为重复执行。
"""

import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib
import services as S

NEW = "http://127.0.0.1:18080"
DB = "xinyu_test_eval_c2_new"
N_PER_POINT = 10
POLL = 0.1


def upstream_events(marker):
    return [e for e in lib.read_jsonl(lib.upstream_log_path())
            if marker in e.get("user_text", "")]


def job_status_safe(t, job_id):
    """重启窗口内连接会短暂失败，按未就绪处理。"""
    import httpx
    try:
        j = t.job(job_id)
        return (j.get("data") or {})
    except httpx.HTTPError:
        return {}


def wait_running(t, job_id, timeout=15):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        d = job_status_safe(t, job_id)
        if d.get("status") == "running":
            return True
        if d.get("status") in ("succeeded", "failed", "superseded", "cancelled"):
            return False
        time.sleep(POLL)
    return False


def kill_and_restart(point):
    S.kill9("java_new")
    if point == "P2":
        S.fake_release("ok")
    time.sleep(1)
    S.start_java_new(DB)


def one_run(api, token, point, idx):
    marker = f"C2{point}-{idx}"
    if point == "P2":
        content = f"重启取证：{marker} 又是忙碌的一天 #FAULT:hold#"
    elif point == "P3":
        content = f"重启取证：{marker} 又是忙碌的一天 #FAULT:slow_body#"
    else:
        content = f"重启取证：{marker} 又是忙碌的一天"
    t = api.with_token(token)
    if point == "P1":
        S.freeze_python()
    try:
        save = t.save_mood("平静", content)
        job_id = save["data"]["jobId"]
        rec_id = save["data"]["id"]
        running = wait_running(t, job_id)
        if point == "P2":
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline and not upstream_events(marker):
                time.sleep(0.1)
        elif point == "P3":
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline:
                if any(e.get("outcome") == "slow_body_started" for e in upstream_events(marker)):
                    break
                time.sleep(0.1)
            time.sleep(2.0)  # 正文已开始分块传输、尚未完成

        calls_at_kill = len(upstream_events(marker))
        kill_and_restart(point)
        if point == "P1":
            S.thaw_python()
            time.sleep(3)  # 冻结期间积压的孤儿请求在解冻后走完
            orphan_calls = len(upstream_events(marker)) - calls_at_kill
        else:
            orphan_calls = 0
        calls_before_restart = len(upstream_events(marker))
    finally:
        if point == "P1":
            try:
                S.thaw_python()
            except ProcessLookupError:
                pass

    deadline = time.monotonic() + 60
    final = None
    while time.monotonic() < deadline:
        d = job_status_safe(t, job_id)
        if d.get("status") in ("succeeded", "failed", "superseded", "cancelled"):
            final = d
            break
        time.sleep(0.5)
    time.sleep(3)  # 等 slow_body 收尾（P3 的"供应商已完成"证据）
    events = upstream_events(marker)
    calls_total = len({e["ts"] for e in events})
    finished_body = any(e.get("outcome") == "slow_body_finished" for e in events)

    if point == "P1":
        classified = "P1" if calls_at_kill == 0 else "P1_lost_race(P2)"
    else:
        classified = point

    leftover_running = lib.mysql(
        "SELECT COUNT(*) FROM ai_job WHERE status = 'running'", DB)[0][0]
    row = lib.mysql(
        f"SELECT ai_reply FROM mood_record WHERE id = {rec_id}", DB)[0]
    log_rows = lib.mysql(
        f"SELECT COUNT(*) FROM ai_reply WHERE target_id = {rec_id}", DB)[0][0]
    calls_after_restart = calls_total - calls_before_restart

    bad = []
    if not running:
        bad.append("never_observed_running")
    if final is None or final.get("status") != "failed":
        bad.append(f"final={final and final.get('status')}")
    if final and final.get("errorCode") != "execution_interrupted":
        bad.append(f"error_code={final.get('errorCode')}")
    if leftover_running != "0":
        bad.append(f"leftover_running={leftover_running}")
    if calls_after_restart > 0:
        bad.append(f"calls_after_restart={calls_after_restart}")
    if orphan_calls > 1:
        bad.append(f"orphan_calls={orphan_calls}")
    if row[0] not in (None, ""):
        bad.append("reply_written")
    if log_rows != "0":
        bad.append(f"ai_reply_log_rows={log_rows}")

    return {"point": point, "classified": classified, "iter": idx, "bad": bad,
            "final_status": final and final.get("status"),
            "final_error": final and final.get("errorCode"),
            "upstream_calls_total": calls_total, "calls_at_kill": calls_at_kill,
            "orphan_calls_after_kill": orphan_calls,
            "calls_after_restart": calls_after_restart,
            "upstream_finished_body": finished_body}


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--point", choices=["P1", "P2", "P3"], default=None,
                        help="只跑一个时点（补跑用；结果替换该时点记录）")
    args = parser.parse_args()
    out_dir = os.path.join(lib.RESULTS_ROOT, "c2")
    os.makedirs(out_dir, exist_ok=True)
    lib.create_database(DB, lib.new_schema_parts())
    S.stop("java_new")
    time.sleep(2)
    S.start_java_new(DB)
    api = lib.Api(NEW, timeout=60)
    points = (args.point,) if args.point else ("P1", "P2", "P3")
    all_records = []
    if args.point:
        existing_path = os.path.join(out_dir, "records.jsonl")
        if os.path.exists(existing_path):
            all_records = [r for r in lib.read_jsonl(existing_path)
                           if r["point"] != args.point]
    for point in points:
        users = [lib.ensure_user(api, f"eval_c2_{point.lower()}_u{u:02d}", "Passw0rd!123")
                 for u in range(1, N_PER_POINT + 1)]
        for i in range(1, N_PER_POINT + 1):
            rec = one_run(api, users[i - 1], point, i)
            all_records.append(rec)
            print(f"{point}#{i} -> {rec['classified']} final={rec['final_status']}"
                  f"/{rec['final_error']} calls={rec['upstream_calls_total']}"
                  f" orphan={rec['orphan_calls_after_kill']} bad={rec['bad']}")

    with open(os.path.join(out_dir, "records.jsonl"), "w", encoding="utf-8") as f:
        for r in all_records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    summary = {}
    for r in all_records:
        s = summary.setdefault(r["classified"], {"n": 0, "bad": 0, "bad_kinds": {},
                                                 "ok_interrupted": 0})
        s["n"] += 1
        if r["bad"]:
            s["bad"] += 1
            for b in r["bad"]:
                s["bad_kinds"][b] = s["bad_kinds"].get(b, 0) + 1
        else:
            s["ok_interrupted"] += 1
    with open(os.path.join(out_dir, "summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    lib.write_meta(out_dir, {"test": "c2_restart_recovery",
                             "n_per_point": N_PER_POINT, "db": DB,
                             "kill": "SIGKILL (kill -9) java 进程",
                             "recovery": "SmartLifecycle 启动 recoverInterrupted"})
    print(json.dumps(summary, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
