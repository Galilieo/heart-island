"""B2：假成功率对照（任务书 3.2）——7 类上游故障注入，旧版 vs 新版各一张矩阵。

故障经内容标记 #FAULT:<mode># 逐请求注入（内容经 Java 校验、Python 组包后原样出现在
发往合成上游的 user 消息里；旧版直连组包同样包含）。每类 ≥20 次 + ok 对照 20 次。

统计口径（分母=注入且完成观测的请求数）：
- 旧版「日志误记成功」：ai_reply.status=1（注入故障却记成功）。
- 旧版「公开发布兜底」：mood_record.ai_reply == 固定兜底文案（用户可见无效回复）。
- 新版「日志误记成功」：ai_job.status=succeeded 或 ai_reply 表出现 status=1 行。
- 新版「公开发布无效回复」：mood_record.ai_reply 非空。
- 读超时类：合成上游睡 50s。新版 Python 读超时 35s → provider_timeout；旧版 RestTemplate
  无读超时 → 请求挂到上游睡醒（50s）后正常返回，本身作为行为差异如实记录。

限流与容量：新侧每故障类一个专属用户（20 次 ≤ 20/时额度）；读超时类 3 用户并发
（每人 ≤7 次，且不超过 Python 门禁 3）。
"""

import json
import os
import sys
import time
from concurrent.futures import ThreadPoolExecutor

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib
import services as S

NEW = "http://127.0.0.1:18080"
OLD = "http://127.0.0.1:18081"
FAULTS = ["empty_content", "blank_content", "truncated_json", "invalid_json",
          "http_500", "http_429", "read_timeout"]
N_PER_FAULT = 20
N_CONTROL = 20
FALLBACK_PREFIX = "谢谢你愿意记录下此刻的心情"  # 旧版兜底文案前缀（全文比对前缀即可）
POLL = 0.2
JOB_WAIT = 70.0
BASE_CONTENT = "故障注入测评：今天把一周的邮件清完了，晚上准备早点休息"


def wait_job(t, job_id):
    deadline = time.monotonic() + JOB_WAIT
    while time.monotonic() < deadline:
        j = t.job(job_id)
        data = j.get("data") or {}
        if data.get("status") in ("succeeded", "failed", "cancelled", "superseded"):
            return data
        time.sleep(POLL)
    return {"status": "poll_timeout"}


def one_new(api, token, fault, idx):
    t = api.with_token(token)
    mode = fault if fault else "ok"
    content = f"{BASE_CONTENT}（{mode}-{idx}）" + (f" #FAULT:{fault}#" if fault else "")
    send = lib.now_ms()
    resp = t.save_mood("平静", content)
    save_ms = lib.now_ms() - send
    data = resp.get("data") or {}
    job_id = data.get("jobId")
    rec = {"fault": fault or "control_ok", "idx": idx, "http": resp.get("code"),
           "save_ms": save_ms}
    if resp.get("code") != 200 or job_id is None:
        rec["note"] = "save_failed"
        return rec
    job = wait_job(t, job_id)
    rec["job_status"] = job.get("status")
    rec["error_code"] = job.get("errorCode")
    detail = t.mood_detail(data.get("id"))
    rec["mood_ai_reply"] = (detail.get("data") or {}).get("aiReply")
    rec["ai_reply_state"] = (detail.get("data") or {}).get("aiReplyState")
    return rec


def one_old(api, token, fault, idx):
    t = api.with_token(token)
    mode = fault if fault else "ok"
    content = f"{BASE_CONTENT}（{mode}-{idx}）" + (f" #FAULT:{fault}#" if fault else "")
    send = lib.now_ms()
    resp = t.save_mood("平静", content)
    save_ms = lib.now_ms() - send
    rec = {"fault": fault or "control_ok", "idx": idx, "http": resp.get("code"),
           "save_ms": save_ms}
    if resp.get("code") != 200:
        rec["note"] = "save_http_error"
        return rec
    # 直接查库取证（响应体不含日志状态）
    log = lib.mysql(
        "SELECT a.status, a.reply_content FROM ai_reply a JOIN mood_record m "
        "ON a.target_id = m.id AND m.content = %r ORDER BY a.id DESC LIMIT 1" % content,
        "xinyu_test_eval_b2_old")
    mood = lib.mysql("SELECT ai_reply FROM mood_record WHERE content = %r "
                     "ORDER BY id DESC LIMIT 1" % content, "xinyu_test_eval_b2_old")
    if log:
        rec["log_status"] = int(log[0][0])
        rec["log_is_fallback"] = log[0][1].startswith(FALLBACK_PREFIX)
    else:
        rec["log_status"] = None
    if mood:
        rec["mood_is_fallback"] = (mood[0][0] or "").startswith(FALLBACK_PREFIX)
        rec["mood_has_reply"] = bool(mood[0][0])
    return rec


def summarize(records):
    matrix = {}
    for fault in set(r["fault"] for r in records):
        rows = [r for r in records if r["fault"] == fault]
        denom = len(rows)
        entry = {"denom": denom}
        if rows and "log_status" in rows[0]:  # 旧版
            entry["log_success"] = sum(1 for r in rows if r.get("log_status") == 1)
            entry["public_fallback"] = sum(1 for r in rows if r.get("mood_is_fallback"))
            entry["public_any_reply"] = sum(1 for r in rows if r.get("mood_has_reply"))
            entry["save_ms"] = lib.stats([r["save_ms"] for r in rows if "save_ms" in r])
        else:  # 新版
            entry["job_succeeded"] = sum(1 for r in rows if r.get("job_status") == "succeeded")
            entry["log_success"] = entry["job_succeeded"]
            entry["public_any_reply"] = sum(1 for r in rows if r.get("mood_ai_reply"))
            entry["public_fallback"] = sum(1 for r in rows
                                           if (r.get("mood_ai_reply") or "").startswith(FALLBACK_PREFIX))
            codes = {}
            for r in rows:
                if r.get("error_code"):
                    codes[r["error_code"]] = codes.get(r["error_code"], 0) + 1
            entry["error_codes"] = codes
            entry["save_ms"] = lib.stats([r["save_ms"] for r in rows if "save_ms" in r])
        matrix[fault] = entry
    return matrix


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--side", choices=["new", "old"], default=None,
                        help="只跑一侧（另一侧结果保留）")
    args = parser.parse_args()
    out_dir = os.path.join(lib.RESULTS_ROOT, "b2")
    os.makedirs(out_dir, exist_ok=True)
    api_new = lib.Api(NEW, timeout=140)
    api_old = lib.Api(OLD, timeout=140)

    all_records = {"new": [], "old": []}
    # 已有结果续跑：不重置 records.jsonl，按 side 覆盖对应部分
    existing_path = os.path.join(out_dir, "records.jsonl")
    if os.path.exists(existing_path):
        for line in lib.read_jsonl(existing_path):
            all_records[line["side"]].append(line)
        if not args.side:
            all_records = {"new": [], "old": []}

    sides = (("new", api_new, one_new, "xinyu_test_eval_b2_new"),
             ("old", api_old, one_old, "xinyu_test_eval_b2_old"))
    if args.side:
        sides = tuple(s for s in sides if s[0] == args.side)
        all_records[args.side] = []

    for side, api, one_fn, db in sides:
        print(f"=== side {side} ===")
        # 对照组（ok）
        token = lib.ensure_user(api, f"eval_b2_{side}_ctrl", "Passw0rd!123")
        for i in range(N_CONTROL):
            all_records[side].append(one_fn(api, token, None, i))
        print(f"control done: {all_records[side][-1].get('job_status') or all_records[side][-1].get('log_status')}")

        for fault in FAULTS:
            if fault == "read_timeout" and side == "new":
                # 3 用户并发分摊，避免 Python 门禁 3 造成 overloaded 混入
                users = [lib.ensure_user(api, f"eval_b2_{side}_rt{u}", "Passw0rd!123") for u in range(3)]
                def work(k):
                    u, i = k % 3, k // 3
                    return one_fn(api, users[u], fault, k)
                with ThreadPoolExecutor(max_workers=3) as pool:
                    all_records[side].extend(pool.map(work, range(N_PER_FAULT)))
            elif fault == "read_timeout" and side == "old":
                users = [lib.ensure_user(api, f"eval_b2_{side}_rt{u}", "Passw0rd!123") for u in range(10)]
                def work(k):
                    return one_fn(api, users[k % 10], fault, k)
                with ThreadPoolExecutor(max_workers=10) as pool:
                    all_records[side].extend(pool.map(work, range(N_PER_FAULT)))
            else:
                token = lib.ensure_user(api, f"eval_b2_{side}_{fault}", "Passw0rd!123")
                for i in range(N_PER_FAULT):
                    all_records[side].append(one_fn(api, token, fault, i))
            last = [r for r in all_records[side] if r["fault"] == fault][-1]
            print(f"{fault} done: save_ms={last['save_ms']} "
                  f"job={last.get('job_status')} log={last.get('log_status')}")

    with open(os.path.join(out_dir, "records.jsonl"), "w", encoding="utf-8") as f:
        for side in ("new", "old"):
            for r in all_records[side]:
                f.write(json.dumps({"side": side, **r}, ensure_ascii=False) + "\n")

    summary = {"new": summarize(all_records["new"]), "old": summarize(all_records["old"])}
    with open(os.path.join(out_dir, "summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    lib.write_meta(out_dir, {
        "test": "b2_fault_matrix", "faults": FAULTS, "n_per_fault": N_PER_FAULT,
        "control": N_CONTROL, "read_timeout_upstream_sleep_ms": 50000,
        "dbs": ["xinyu_test_eval_b2_new", "xinyu_test_eval_b2_old"],
    })
    print(json.dumps(summary, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
