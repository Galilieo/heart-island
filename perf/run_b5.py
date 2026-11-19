"""B5：SQL 计量（任务书 3.5，只测不改）——社区列表（N+1 候选）、心情历史、任务回查。

两档数据量（各自独立隔离库，分布相同只有行数差异）：
- 低档：心情 1,000 条 + 社区每话题 100 帖
- 高档：心情 20,000 条 + 社区每话题 1,000 帖

每个请求的 SQL 计数用 performance_schema.events_statements_summary_by_digest
（过滤 SCHEMA_NAME=隔离库，前后快照差值），包含鉴权等完整请求的 SQL；分项按 digest 文本归类。
EXPLAIN ANALYZE 单独对手工代入参数的代表 SQL 执行，保存原始输出（它的计时不是接口 P95）。

产物：perf/results/b5/{tier}_requests.json、summary.json、explain/*.txt
"""

import argparse
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import sys
import tempfile
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib
import services as S

NEW = "http://127.0.0.1:18080"
TIERS = [
    {"tag": "t1", "mood": 1000, "posts_per_topic": 100},
    {"tag": "t2", "mood": 20000, "posts_per_topic": 1000},
]
REPS = 20
SQL_PROBES = 3

DIGEST_SNAPSHOT = ("SELECT DIGEST, SCHEMA_NAME, COUNT_STAR, SUM_TIMER_WAIT "
                   "FROM performance_schema.events_statements_summary_by_digest "
                   "WHERE SCHEMA_NAME = '{db}'")


def digest_snapshot(db):
    rows = lib.mysql(DIGEST_SNAPSHOT.format(db=db))
    return {r[0]: [int(r[2]), int(r[3])] for r in rows if r[0]}


def digest_delta(before, after):
    deltas = {}
    for digest, (count, timer) in after.items():
        prev = before.get(digest, [0, 0])
        d_count = count - prev[0]
        if d_count > 0:
            deltas[digest] = [d_count, timer - prev[1]]
    return deltas


def sql_count_for_request(db, func):
    before = digest_snapshot(db)
    result = func()
    time.sleep(0.3)  # 等 performance_schema 聚合
    after = digest_snapshot(db)
    return result, digest_delta(before, after)


def run_endpoint(db, name, func, records):
    lat, counts, timers = [], [], {}
    for i in range(REPS):
        t0 = lib.now_ms()
        func()
        lat.append(lib.now_ms() - t0)
    if SQL_PROBES:
        probe_counts = []
        for _ in range(SQL_PROBES):
            _, delta = sql_count_for_request(db, func)
            probe_counts.append(sum(v[0] for v in delta.values()))
            for digest, (cnt, tmr) in delta.items():
                timers[digest] = timers.get(digest, [0, 0])
                timers[digest][0] += cnt
                timers[digest][1] += tmr
        counts = probe_counts
    records[name] = {
        "latency_ms": lib.stats(lat),
        "sql_counts_probes": counts,
        "sql_count_median": sorted(counts)[len(counts) // 2] if counts else None,
        "digests": {k: v for k, v in sorted(timers.items(),
                                             key=lambda kv: -kv[1][1])[:12]},
    }


def explain(out_dir, db, name, sql):
    proc = subprocess.run(
        ["mysql", "-h127.0.0.1", "-u", lib.TEST_ENV["AI_TEST_DB_USER"],
         f"-p{lib.TEST_ENV['AI_TEST_DB_PASSWORD']}", "-D", db, "-e", sql],
        capture_output=True, text=True, timeout=120)
    path = os.path.join(out_dir, "explain", f"{name}.txt")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(f"-- {sql}\n{proc.stdout}\n{proc.stderr}")
    return path


def setup_tier(tier, *, run_id=None, service="java_new"):
    db = f"xinyu_test_eval_b5_{tier['tag']}"
    if run_id is not None:
        db = f"xinyu_test_eval_b5_paging_{tier['tag']}_{run_id}"
    # B5 启动当前工作区构建的 Java，社区查询需要基线之后新增的回复类型列。
    migration = "sql/migrations/20261119_01_community_ai_result_kind.sql"
    schema = lib.new_schema_parts()
    if all(name != migration for name, _ in schema):
        path = Path(lib.REPO_ROOT) / migration
        schema.append((migration, path.read_text(encoding="utf-8")))
    lib.create_database(db, schema, replace_existing=run_id is None)
    # 先把 Java 指向本档库再注册用户（否则注册会写进上一段的库）
    S.stop(service, wait_s=4 if run_id is not None else 40)
    time.sleep(2)
    if run_id is not None:
        try:
            with socket.create_connection(("127.0.0.1", S.NEW_PORT), timeout=2):
                raise RuntimeError("B5 回环端口已在使用")
        except ConnectionRefusedError:
            pass

    pid = S.start_java_new(db, name=service, worker=False)  # 关 worker 避免任务扫描噪声
    if run_id is not None:
        require_owned_listener(pid)
    api = lib.Api(NEW, timeout=60)
    token = lib.ensure_user(api, "eval_sql_u1", "Passw0rd!123")
    for a in range(1, 6):
        lib.ensure_user(api, f"eval_sql_a{a}", "Passw0rd!123")
    viewer_row = lib.mysql("SELECT id FROM user WHERE username='eval_sql_u1'", db)[0][0]
    author_rows = ",".join(r[0] for r in lib.mysql(
        "SELECT id FROM user WHERE username LIKE 'eval_sql_a%'", db))
    # 话题
    lib.mysql("INSERT INTO topic (name, description, sort, status) VALUES "
              "('测评话题一','t',1,1),('测评话题二','t',2,1),('测评话题三','t',3,1)", db)
    # 数据文件（固定种子；心情+社区合并生成一份）
    with tempfile.TemporaryDirectory(prefix="xinyu-b5-data-") as fixture_dir:
        data_sql = os.path.join(fixture_dir, f"data_{tier['tag']}.sql")
        subprocess.run(
            [sys.executable, os.path.join(lib.REPO_ROOT, "perf", "gen_data.py"),
             "--mood", str(tier["mood"]), "--user-id", viewer_row,
             "--posts", str(tier["posts_per_topic"] * 3),
             "--viewer-id", viewer_row, "--author-ids", author_rows, "--out", data_sql],
            check=True, cwd=lib.REPO_ROOT,
        )
        with open(data_sql, encoding="utf-8") as fixture:
            proc = subprocess.run(
                ["mysql", "-h127.0.0.1", "-u", lib.TEST_ENV["AI_TEST_DB_USER"],
                 f"-p{lib.TEST_ENV['AI_TEST_DB_PASSWORD']}", "-D", db],
                stdin=fixture, capture_output=True, text=True, timeout=300,
            )
        if proc.returncode != 0:
            raise RuntimeError(f"load {data_sql}: {proc.stderr[:300]}")
    return db, token, viewer_row


def require_owned_listener(pid):
    """向测试后端提交注册前，确认端口由本次指定隔离库的 JVM 持有。"""
    result = subprocess.run(
        ["lsof", "-nP", "-a", "-p", str(pid), f"-iTCP:{S.NEW_PORT}", "-sTCP:LISTEN", "-Fpn"],
        capture_output=True, text=True, timeout=5,
    )
    lines = set(result.stdout.splitlines())
    loopback_listeners = {f"n127.0.0.1:{S.NEW_PORT}", f"n[::ffff:127.0.0.1]:{S.NEW_PORT}"}
    if result.returncode != 0 or f"p{pid}" not in lines or not lines.intersection(loopback_listeners):
        raise RuntimeError("B5 回环端口的监听进程未通过核对，停止注册")


def community_page(api, sort, total):
    """校验 HTTP 和业务响应，失败请求不能进入延迟样本。"""
    params = {"topicId": 1, "pageNum": 1, "pageSize": 10}
    if sort == "hot":
        params["sort"] = "hot"
    response = api.client.get("/community/post/list", params=params, headers=api.headers)
    response.raise_for_status()
    body = response.json()
    if body["code"] != 200:
        raise RuntimeError("社区列表请求失败")
    page = body["data"]
    if int(page["total"]) != total or len(page["records"]) != 10:
        raise RuntimeError(f"分页样本不符合预期，预期总数 {total}、返回 10 条")


def run_community_paging():
    """按原 B5 档位和计量方法，单独复测两个社区列表端点。"""
    out_dir = os.path.join(lib.RESULTS_ROOT, "b5-paging")
    run_id = str(time.time_ns())
    service = f"java_b5_paging_{run_id}"
    os.environ.update({
        "SERVER_ADDRESS": "127.0.0.1",
    })
    summary = {}
    try:
        for tier, expected_total in zip(TIERS, (115, 1050), strict=True):
            db, token, _ = setup_tier(tier, run_id=run_id, service=service)
            api = lib.Api(NEW, timeout=120).with_token(token)
            records = {}
            run_endpoint(db, "community_list_topic1",
                         lambda: community_page(api, "latest", expected_total), records)
            run_endpoint(db, "community_list_hot",
                         lambda: community_page(api, "hot", expected_total), records)
            for result in records.values():
                probes = result["sql_counts_probes"]
                if len(probes) != SQL_PROBES or any(count <= 0 for count in probes):
                    raise RuntimeError("SQL 摘要探测未获得完整非零计数")
            summary[tier["tag"]] = {"tier": tier, "topic1_posts": expected_total, "records": records}
    finally:
        S.stop(service, wait_s=4)

    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "summary.json"), "w", encoding="utf-8") as output:
        json.dump(summary, output, ensure_ascii=False, indent=2)
        output.write("\n")
    lib.write_meta(out_dir, {
        "test": "b5_community_paging", "tiers": TIERS, "reps": REPS, "sql_probes": SQL_PROBES,
        "sql_probe_method": "performance_schema digest delta", "page_num": 1, "page_size": 10,
    })
    print("saved", out_dir)


def main():
    out_dir = os.path.join(lib.RESULTS_ROOT, "b5")
    os.makedirs(out_dir, exist_ok=True)
    summary = {}
    for tier in TIERS:
        db, token, viewer_id = setup_tier(tier)
        # setup_tier 已把 Java 指向本档库并注册用户
        api = lib.Api(NEW, timeout=120)
        t = api.with_token(token)

        # 准备参照对象：一条心情 + 一个 succeeded 任务（直接造一条已完成任务，不走模型）
        lib.mysql(
            f"INSERT INTO mood_record (user_id, mood_type, content, status, ai_reply_enabled, create_time, update_time) "
            f"VALUES ({viewer_id}, '平静', 'B5参照记录', 1, 0, NOW(), NOW())", db)
        mid = lib.mysql(
            f"SELECT id FROM mood_record WHERE user_id={viewer_id} AND content='B5参照记录'", db)[0][0]
        lib.mysql(
            f"INSERT INTO ai_job (user_id, target_id, task_type, target_version, status, attempt_count, "
            f"result_kind, model_called, create_time, update_time, started_at, finished_at) VALUES "
            f"({viewer_id}, {mid}, 'MOOD_REPLY', 1, 'succeeded', 1, 'MODEL', 1, NOW(), NOW(), NOW(), NOW())", db)
        job_id = lib.mysql(
            f"SELECT id FROM ai_job WHERE target_id={mid}", db)[0][0]
        deep_page = (tier["mood"] // 10) - 9 if tier["mood"] >= 100 else 2

        records = {}
        run_endpoint(db, "mood_list_page1", lambda: t.mood_list(1, 10), records)
        run_endpoint(db, "mood_list_deep", lambda: t.mood_list(deep_page, 10), records)
        run_endpoint(db, "mood_list_filter",
                     lambda: t.mood_list(1, 10, mood_type="平静"), records)
        run_endpoint(db, "mood_detail", lambda: t.mood_detail(mid), records)
        run_endpoint(db, "job_get", lambda: t.job(job_id), records)
        run_endpoint(db, "community_list_topic1",
                     lambda: t.community_list(1), records)
        run_endpoint(db, "community_list_hot",
                     lambda: api.client.get("/community/post/list",
                                            params={"topicId": 1, "sort": "hot"},
                                            headers=t.headers).json(), records)
        summary[tier["tag"]] = {"tier": tier, "records": records}
        print(json.dumps({k: {"lat": v["latency_ms"]["p95"],
                              "sql": v["sql_count_median"]}
                          for k, v in records.items()}, ensure_ascii=False, indent=1))

        # EXPLAIN ANALYZE 代表 SQL（手工代入参数；输出含 actual time/rows/loops）
        explain(out_dir, db, f"{tier['tag']}_community_list",
                f"EXPLAIN ANALYZE SELECT * FROM community_post WHERE status=1 AND topic_id=1 "
                f"ORDER BY create_time DESC")
        explain(out_dir, db, f"{tier['tag']}_post_like_lookup",
                f"EXPLAIN ANALYZE SELECT COUNT(*) FROM post_like WHERE post_id=1 AND user_id={viewer_id}")
        explain(out_dir, db, f"{tier['tag']}_mood_list",
                f"EXPLAIN ANALYZE SELECT id FROM mood_record WHERE user_id={viewer_id} AND status=1 "
                f"ORDER BY create_time DESC LIMIT 10 OFFSET 0")
        explain(out_dir, db, f"{tier['tag']}_mood_list_deep",
                f"EXPLAIN ANALYZE SELECT id FROM mood_record WHERE user_id={viewer_id} AND status=1 "
                f"ORDER BY create_time DESC LIMIT 10 OFFSET {(deep_page - 1) * 10}")
        explain(out_dir, db, f"{tier['tag']}_job_lookup",
                f"EXPLAIN ANALYZE SELECT * FROM ai_job WHERE task_type='MOOD_REPLY' AND target_id={mid} "
                f"AND target_version=1 AND user_id={viewer_id}")

    with open(os.path.join(out_dir, "summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    lib.write_meta(out_dir, {"test": "b5_sql_metering", "tiers": TIERS, "reps": REPS,
                             "sql_probe_method": "performance_schema digest delta",
                             "note": "worker 关闭后测读路径；EXPLAIN ANALYZE 单独执行"})
    print("saved", os.path.join(out_dir, "summary.json"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--community-paging", action="store_true", help="只复测分页后的两个社区列表端点")
    if parser.parse_args().community_paging:
        run_community_paging()
    else:
        main()
