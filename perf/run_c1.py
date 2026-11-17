"""C1：并发写入正确性取证（任务书 4.1）——6 种先后顺序各 50 轮，全部黑盒走 API。

时点控制：内容标记 #FAULT:hold# 让合成上游挂起该次调用（只挂这一条，全局模式保持 ok），
保证「修改/软删」确实发生在生成中；释放经 POST /_release。

六种顺序与预期（新版保护逻辑）：
S1 修改在生成中：complete 版本检查 → J1 superseded，v2 由新任务成功后才可见回复。
S2 完成后修改：J1 succeeded 合法；修改产生 v2 + 新任务，旧回复不覆盖新版本。
S3 软删在生成中：J1 superseded；已删除记录不得被写入回复（ai_reply 保持 NULL）。
S4 完成后软删：回复先合法写入；软删后详情接口不再返回该记录。
S5 失败任务 retry vs 修改：J1 失败后版本推进 → retry 被拒（冲突），不得重排旧任务。
S6 失败任务 retry vs 软删：J1 失败后来源被删 → retry 被拒（不存在），不得发起上游调用。

「错误写入」= 违反上述预期的任何终态（每轮按断言计数，只写观察到的次数，不写“永不出错”）。
已有真实锁等待与两种提交顺序证据（AiJobPersistenceIT）在报告中引用，不在此重复。
"""

import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib
import services as S

NEW = "http://127.0.0.1:18080"
DB = "xinyu_test_eval_c1_new"
N = 50
POLL = 0.2
WAIT_TERMINAL = 30.0


def wait_terminal(t, job_id, timeout=WAIT_TERMINAL):
    """轮询任务终态。走数据库而不是 API：来源版本推进/删除后，任务查询 API 按设计返回
    409/404（data=null），API 口径无法观察过期任务的终态（首轮 S1/S3 误报的根因）。"""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        row = lib.mysql(
            f"SELECT status, last_error_code FROM ai_job WHERE id = {job_id}", DB)
        if row and row[0][0] in ("succeeded", "failed", "cancelled", "superseded"):
            return {"status": row[0][0], "errorCode": row[0][1]}
        time.sleep(POLL)
    return {"status": "poll_timeout", "errorCode": None}


def job_status_db(job_id):
    row = lib.mysql(
        f"SELECT status, last_error_code FROM ai_job WHERE id = {job_id}", DB)
    return {"status": row[0][0], "errorCode": row[0][1]} if row else {"status": "missing"}


def wait_held(marker, timeout=15.0):
    """轮询上游日志，直到出现该标记的 held 记录。"""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        for e in lib.read_jsonl(lib.upstream_log_path()):
            if marker in e.get("user_text", "") and e.get("outcome") == "held":
                return True
        time.sleep(0.15)
    return False


def upstream_call_count(marker):
    return sum(1 for e in lib.read_jsonl(lib.upstream_log_path())
               if marker in e.get("user_text", ""))


def scenario_s1(t, i):
    marker = f"C1S1-{i}"
    save = t.save_mood("平静", f"并发取证A：{marker} 一周的事处理完，想早点休息 #FAULT:hold#")
    job1 = save["data"]["jobId"]
    rec_id = save["data"]["id"]
    held = wait_held(marker)
    upd = t.update_mood(rec_id, "平静", f"并发取证B：{marker} 改成了另一件烦心事，还在想怎么办")
    v2 = upd["data"]["contentVersion"]
    job2 = upd["data"]["jobId"]
    S.fake_release("ok")
    j1 = wait_terminal(t, job1)
    j2 = wait_terminal(t, job2) if job2 else {"status": "no_job"}
    detail = t.mood_detail(rec_id)["data"]
    bad = []
    if not held:
        bad.append("hold_not_observed")
    if j1.get("status") != "superseded":
        bad.append(f"j1_status={j1.get('status')}")
    if j2.get("status") != "succeeded":
        bad.append(f"j2_status={j2.get('status')}")
    if detail.get("contentVersion") != v2 or "并发取证B" not in detail.get("content", ""):
        bad.append("content_not_v2")
    if not detail.get("aiReply"):
        bad.append("v2_no_reply")
    return {"scenario": "S1_update_during_generation", "iter": i, "bad": bad,
            "j1": j1.get("status"), "j2": j2.get("status")}


def scenario_s2(t, i):
    marker = f"C1S2-{i}"
    save = t.save_mood("平静", f"完成后修改A：{marker} 今天整理完桌面，晚上想安静待着")
    job1 = save["data"]["jobId"]
    rec_id = save["data"]["id"]
    j1 = wait_terminal(t, job1)
    detail1 = t.mood_detail(rec_id)["data"]
    upd = t.update_mood(rec_id, "平静", f"完成后修改B：{marker} 内容改成另一件小事")
    v2 = upd["data"]["contentVersion"]
    job2 = upd["data"]["jobId"]
    j2 = wait_terminal(t, job2) if job2 else {"status": "no_job"}
    detail2 = t.mood_detail(rec_id)["data"]
    bad = []
    if j1.get("status") != "succeeded":
        bad.append(f"j1={j1.get('status')}")
    if not detail1.get("aiReply"):
        bad.append("v1_reply_missing")
    if j2.get("status") != "succeeded":
        bad.append(f"j2={j2.get('status')}")
    if detail2.get("contentVersion") != v2 or not detail2.get("aiReply"):
        bad.append("v2_final_state_wrong")
    if detail2.get("jobId") != job2:
        bad.append("stale_job_visible")
    return {"scenario": "S2_update_after_complete", "iter": i, "bad": bad,
            "j1": j1.get("status"), "j2": j2.get("status")}


def scenario_s3(t, i):
    marker = f"C1S3-{i}"
    save = t.save_mood("平静", f"删除竞争A：{marker} 攒了一周的邮件还没清 #FAULT:hold#")
    job1 = save["data"]["jobId"]
    rec_id = save["data"]["id"]
    held = wait_held(marker)
    dele = t.delete_mood(rec_id)
    S.fake_release("ok")
    j1 = wait_terminal(t, job1)
    row = lib.mysql(f"SELECT status, ai_reply FROM mood_record WHERE id = {rec_id}", DB)[0]
    detail = t.mood_detail(rec_id)
    bad = []
    if not held:
        bad.append("hold_not_observed")
    if j1.get("status") != "superseded":
        bad.append(f"j1={j1.get('status')}")
    if row[0] != "0":
        bad.append("row_not_deleted")
    if row[1] not in (None, ""):
        bad.append("reply_written_to_deleted")
    if detail.get("code") == 200:
        bad.append("deleted_still_visible")
    return {"scenario": "S3_delete_during_generation", "iter": i, "bad": bad,
            "j1": j1.get("status"), "delete_code": dele.get("code")}


def scenario_s4(t, i):
    marker = f"C1S4-{i}"
    save = t.save_mood("平静", f"完成后删除：{marker} 今天走了六公里，腿有点酸")
    job1 = save["data"]["jobId"]
    rec_id = save["data"]["id"]
    j1 = wait_terminal(t, job1)
    detail1 = t.mood_detail(rec_id)["data"]
    dele = t.delete_mood(rec_id)
    detail2 = t.mood_detail(rec_id)
    bad = []
    if j1.get("status") != "succeeded" or not detail1.get("aiReply"):
        bad.append("pre_delete_state_wrong")
    if dele.get("code") != 200:
        bad.append(f"delete_code={dele.get('code')}")
    if detail2.get("code") == 200:
        bad.append("deleted_still_visible")
    return {"scenario": "S4_delete_after_complete", "iter": i, "bad": bad,
            "j1": j1.get("status")}


def scenario_s5(t, i):
    marker = f"C1S5a-{i}"   # 仅内容A使用；内容B不带此标记，避免新任务调用被计入A的重复执行
    save = t.save_mood("平静", f"重试与修改A：{marker} 一件事没办好 #FAULT:http_500#")
    job1 = save["data"]["jobId"]
    rec_id = save["data"]["id"]
    j1 = wait_terminal(t, job1)
    upd = t.update_mood(rec_id, "平静", f"重试与修改B：另一件新事项（S5b-{i}）")
    job2 = upd["data"]["jobId"]
    before_calls = upstream_call_count(marker)
    retry = t.job_retry(job1)
    time.sleep(2.5)
    j1_after = job_status_db(job1)
    j2 = wait_terminal(t, job2)
    after_calls = upstream_call_count(marker)
    bad = []
    if j1.get("status") != "failed" or j1.get("errorCode") != "provider_error":
        bad.append(f"j1={j1.get('status')}:{j1.get('errorCode')}")
    if retry.get("code") == 200:
        bad.append("stale_retry_accepted")
    # 版本推进后旧任务按设计转为 superseded（attempt_count 不变，无重复执行）
    if j1_after.get("status") != "superseded":
        bad.append(f"j1_after_retry={j1_after.get('status')}")
    if after_calls != before_calls:
        bad.append(f"upstream_calls_changed {before_calls}->{after_calls}")
    if j2.get("status") != "succeeded":
        bad.append(f"j2={j2.get('status')}")
    return {"scenario": "S5_retry_vs_update", "iter": i, "bad": bad,
            "retry_code": retry.get("code"), "retry_msg": str(retry.get("message"))[:60]}


def scenario_s6(t, i):
    marker = f"C1S6-{i}"
    save = t.save_mood("平静", f"重试与删除A：{marker} 又一件没做好的事 #FAULT:http_500#")
    job1 = save["data"]["jobId"]
    rec_id = save["data"]["id"]
    j1 = wait_terminal(t, job1)
    dele = t.delete_mood(rec_id)
    before_calls = upstream_call_count(marker)
    retry = t.job_retry(job1)
    time.sleep(2.5)
    j1_after = job_status_db(job1)
    after_calls = upstream_call_count(marker)
    bad = []
    if j1.get("status") != "failed":
        bad.append(f"j1={j1.get('status')}")
    if dele.get("code") != 200:
        bad.append("delete_failed")
    if retry.get("code") == 200:
        bad.append("deleted_source_retry_accepted")
    # 来源删除后旧任务按设计转为 superseded，不得重新入队或发起调用
    if j1_after.get("status") != "superseded":
        bad.append(f"j1_after_retry={j1_after.get('status')}")
    if after_calls != before_calls:
        bad.append(f"upstream_calls_changed {before_calls}->{after_calls}")
    return {"scenario": "S6_retry_vs_delete", "iter": i, "bad": bad,
            "retry_code": retry.get("code")}


SCENARIOS = [scenario_s1, scenario_s2, scenario_s3, scenario_s4, scenario_s5, scenario_s6]


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", default=None,
                        help="只跑指定场景（逗号分隔 s1..s6），用于修正断言后的补跑")
    args = parser.parse_args()
    out_dir = os.path.join(lib.RESULTS_ROOT, "c1")
    os.makedirs(out_dir, exist_ok=True)
    # 独立隔离库 + 默认额度（quota 默认 20/时/用户；每场景 20 用户轮转，单用户 ≤5 次额度消耗）
    if not args.only:
        lib.create_database(DB, lib.new_schema_parts())
        S.stop("java_new")
        time.sleep(2)
        S.start_java_new(DB)
    api = lib.Api(NEW, timeout=60)
    scenarios = SCENARIOS
    if args.only:
        keys = {k.strip().lower() for k in args.only.split(",")}
        scenarios = [fn for fn in SCENARIOS if fn.__name__[-2:].lower() in keys]
    else:
        open(lib.upstream_log_path(), "w").close()

    all_records = []
    # --only 补跑时保留已有场景的记录，只替换指定场景（记录的 scenario 以 S1..S6 开头）
    if args.only:
        existing_path = os.path.join(out_dir, "records.jsonl")
        if os.path.exists(existing_path):
            all_records = [r for r in lib.read_jsonl(existing_path)
                           if r["scenario"][:2].lower() not in keys]
    for fn in scenarios:
        name = fn.__name__
        users = [lib.ensure_user(api, f"eval_c1_{name[-2:]}_u{u:02d}", "Passw0rd!123")
                 for u in range(1, 21)]
        for i in range(1, N + 1):
            token = users[(i - 1) % 20]
            t = api.with_token(token)
            rec = fn(t, i)
            all_records.append(rec)
            if rec["bad"]:
                print(f"{name} iter{i} BAD: {rec['bad']}")
        bad_n = sum(1 for r in all_records if r["scenario"] == rec["scenario"] and r["bad"])
        print(f"{name}: done {N}, bad={bad_n}")

    with open(os.path.join(out_dir, "records.jsonl"), "w", encoding="utf-8") as f:
        for r in all_records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    summary = {}
    for r in all_records:
        s = summary.setdefault(r["scenario"], {"n": 0, "bad": 0, "bad_kinds": {}})
        s["n"] += 1
        if r["bad"]:
            s["bad"] += 1
            for b in r["bad"]:
                s["bad_kinds"][b] = s["bad_kinds"].get(b, 0) + 1
    with open(os.path.join(out_dir, "summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    lib.write_meta(out_dir, {"test": "c1_concurrent_writes", "n_per_scenario": N,
                             "scenarios": [fn.__name__ for fn in SCENARIOS], "db": DB})
    print(json.dumps(summary, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
