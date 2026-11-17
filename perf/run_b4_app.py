"""B4（应用层）：限流降级切换实测（任务书 3.4）。

前置：本机 Redis 已在 127.0.0.1:6390（独立实例、独立库、键前缀 xinyu:test:，用完清空）。
Java 以 REDIS_URL 指向该实例启动（其余配置默认）。

测量：
1. Redis 正常时的保存确认延迟（含限流 check；每用户 ≤2 次，30 用户，避开额度）。
2. 停掉 Redis 后的第 1 次保存（预期 ≈ 常规延迟 + redis-timeout 200ms）。
3. 冷却期（30s）内的后续保存（预期回到本地计数速度）。
4. 冷却到期后的第一次保存（预期再次吃 200ms 超时并重新进入冷却）。
对照组：同一隔离库同一负载、REDIS_URL 为空（本机计数）重跑一遍保存延迟。

组件级单次 check 微基准见 perf/bench/RateLimitBench.java（本脚本之外运行）。
"""

import json
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib
import services as S

NEW = "http://127.0.0.1:18080"
DB = "xinyu_test_eval_b4_new"
REDIS_PORT = 6390
N_NORMAL = 60
CONTENT = "限流降级测评：处理完手头的事，准备休息"


def timed_saves(api, tokens, n):
    lats = []
    for i in range(n):
        t = api.with_token(tokens[i % len(tokens)])
        t0 = lib.now_ms()
        resp = t.save_mood("平静", f"{CONTENT}（{i}）")
        lats.append(lib.now_ms() - t0)
        if resp.get("code") != 200:
            raise RuntimeError(f"save failed: {resp}")
    return lats


def main():
    out_dir = os.path.join(lib.RESULTS_ROOT, "b4")
    os.makedirs(out_dir, exist_ok=True)
    lib.create_database(DB, lib.new_schema_parts())

    result = {}

    # ---- 前置：独立 Redis 实例（可能因上次收尾被关闭） ----
    probe = subprocess.run(["redis-cli", "-p", str(REDIS_PORT), "PING"],
                           capture_output=True, text=True)
    if "PONG" not in probe.stdout:
        subprocess.run(["redis-server", "--port", str(REDIS_PORT), "--daemonize", "yes",
                        "--save", "", "--appendonly", "no",
                        "--pidfile", "/tmp/redis-eval-6390.pid"], check=True)
        time.sleep(0.5)

    # ---- 对照：本机计数 ----
    S.stop("java_new"); time.sleep(2)
    S.start_java_new(DB)
    api = lib.Api(NEW, timeout=30)
    tokens_local = [lib.ensure_user(api, f"eval_b4_loc_u{u:02d}", "Passw0rd!123")
                    for u in range(1, 31)]
    local_lats = timed_saves(api, tokens_local, N_NORMAL)
    result["local_backend_save_ms"] = lib.stats(local_lats)

    # ---- Redis 后端 ----
    subprocess.run(["redis-cli", "-p", str(REDIS_PORT), "FLUSHDB"], check=True)
    S.stop("java_new"); time.sleep(2)
    S.start_java_new(DB, redis_url=f"redis://127.0.0.1:{REDIS_PORT}/0")
    api = lib.Api(NEW, timeout=30)
    tokens_redis = [lib.ensure_user(api, f"eval_b4_rd_u{u:02d}", "Passw0rd!123")
                    for u in range(1, 31)]
    redis_lats = timed_saves(api, tokens_redis, N_NORMAL)
    result["redis_backend_save_ms"] = lib.stats(redis_lats)

    # ---- 降级切换 ----
    degrade = {}
    tokens_deg = [lib.ensure_user(api, f"eval_b4_deg_u{u:02d}", "Passw0rd!123")
                  for u in range(1, 9)]

    def one(i):
        t = api.with_token(tokens_deg[i % len(tokens_deg)])
        t0 = lib.now_ms()
        resp = t.save_mood("平静", f"{CONTENT}-降级（{i}）")
        return lib.now_ms() - t0, resp

    subprocess.run(["redis-cli", "-p", str(REDIS_PORT), "SHUTDOWN", "NOSAVE"],
                   capture_output=True)
    time.sleep(0.5)
    lat, resp = one(0)
    degrade["first_after_redis_down"] = {"ms": lat, "code": resp.get("code")}
    cooldown_lats = []
    for i in range(1, 5):
        lat, resp = one(i)
        cooldown_lats.append(lat)
    degrade["in_cooldown_ms"] = lib.stats(cooldown_lats)
    print("waiting cooldown 31s ...")
    time.sleep(31)
    lat, resp = one(5)
    degrade["first_after_cooldown_expired"] = {"ms": lat, "code": resp.get("code")}
    result["degrade_switch"] = degrade

    # ---- 恢复 Redis 并清空测试键 ----
    subprocess.run(["redis-server", "--port", str(REDIS_PORT), "--daemonize", "yes",
                    "--save", "", "--appendonly", "no",
                    "--pidfile", "/tmp/redis-eval-6390.pid"], check=True)
    time.sleep(0.5)
    subprocess.run(["redis-cli", "-p", str(REDIS_PORT), "FLUSHALL"], check=True)

    with open(os.path.join(out_dir, "app_level.json"), "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    lib.write_meta(out_dir, {"test": "b4_ratelimit_overhead_app",
                             "db": DB, "redis": f"127.0.0.1:{REDIS_PORT}/0",
                             "redis_timeout_ms": 200, "fallback_cooldown_s": 30,
                             "component_bench": "perf/bench/RateLimitBench.java"})
    print(json.dumps(result, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
