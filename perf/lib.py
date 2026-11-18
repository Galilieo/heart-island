"""测评脚本共享工具：HTTP 客户端、MySQL 访问、分位数与运行元信息。

只依赖 Python 标准库与 xinyu-ai 已有依赖（httpx）；统一用
`uv run --directory xinyu-ai --locked python ../perf/<script>.py` 执行。

口径约定（任务书 10.2）：
- 分位数用最近邻秩（nearest-rank）：排序后取第 ceil(p*n) 个样本，与商业工具默认不同时以本口径为准。
- 失败、超时不从分母剔除，调用方必须把分类计数一并写进结果。
- Long id 经 Jackson 序列化为字符串，客户端一律按字符串处理。
"""

import json
import math
import os
import re
import subprocess
import time

import httpx

# ---- 环境与数据库 ----


def load_test_env(path="~/.zshenv"):
    """读取 ~/.zshenv 中 xinyu_test 连接变量；不执行 shell，只解析 export 行。"""
    expanded = os.path.expanduser(path)
    values = {}
    with open(expanded, encoding="utf-8") as f:
        for line in f:
            match = re.match(r"export\s+([A-Z_]+)='([^']*)'", line.strip())
            if match:
                values[match.group(1)] = match.group(2)
    required = ["AI_TEST_JDBC_URL", "AI_TEST_DB_USER", "AI_TEST_DB_PASSWORD"]
    missing = [key for key in required if key not in values]
    if missing:
        raise RuntimeError(f"missing env in {expanded}: {missing}")
    return values


TEST_ENV = load_test_env()


def db_name_from_jdbc():
    url = TEST_ENV["AI_TEST_JDBC_URL"]
    return re.search(r"/([^/?]+)", url.split("://", 1)[1]).group(1)


def mysql(query, database=None, timeout=120):
    """执行 SQL 并返回 TSV 行列表；只用于隔离库 xinyu_test_*。"""
    args = [
        "mysql", "-h127.0.0.1", "-u", TEST_ENV["AI_TEST_DB_USER"],
        f"-p{TEST_ENV['AI_TEST_DB_PASSWORD']}",
        "--batch", "--raw", "--skip-column-names",
        "-e", query,
    ]
    if database:
        args.insert(-2, database)
    proc = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    if proc.returncode != 0:
        raise RuntimeError(f"mysql failed: {proc.stderr.strip()[:500]}")
    rows = []
    for line in proc.stdout.splitlines():
        if line:
            # --batch 把 NULL 输出为字面量 "NULL"，统一还原为 None
            rows.append([None if cell == "NULL" else cell for cell in line.split("\t")])
    return rows


def assert_isolated(database):
    if not database.startswith("xinyu_test_"):
        raise RuntimeError(f"refuse to touch non-isolated database: {database}")


def sanitize_isolated_sql(sql):
    """剥离会切换目标库的语句（CREATE DATABASE / USE）。

    xinyu.sql 自带 `USE xinyu`，若不剥离，灌入隔离库的管道会把 DROP/CREATE 实际执行到
    业务库上（2026-11-17 事故：业务库 7 张表被误 DROP）。任何含 USE 的行一律删除。
    """
    lines = []
    skipping = False
    for line in sql.splitlines():
        stripped = line.strip()
        if skipping:
            if stripped.endswith(";"):
                skipping = False
            continue
        if stripped.startswith("USE ") or stripped.startswith("USE`"):
            continue
        if stripped.startswith("CREATE DATABASE"):
            if not stripped.endswith(";"):
                skipping = True
            continue
        lines.append(line)
    return "\n".join(lines)


def create_database(database, sql_parts, *, replace_existing=True):
    """从冻结的 git 内容创建隔离库；sql_parts 为 [(label, sql_text), ...]。

    迁移冲突（Duplicate column / already exists）视为“基线已含该变更”，跳过并记录，
    因为 sql/xinyu.sql 的基线内容与迁移合并状态随上线工作变动。
    """
    assert_isolated(database)
    create_sql = f"CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    if replace_existing:
        create_sql = f"DROP DATABASE IF EXISTS `{database}`; " + create_sql
    mysql(create_sql)
    skipped = []
    for label, sql in sql_parts:
        schema = sanitize_isolated_sql(sql)
        if not replace_existing:
            # 新建隔离库无需执行初始化脚本的删表语句。
            schema = re.sub(r"(?im)^\s*DROP TABLE IF EXISTS [^;]+;\s*$", "", schema)
        proc = subprocess.run(
            ["mysql", "-h127.0.0.1", "-u", TEST_ENV["AI_TEST_DB_USER"],
             f"-p{TEST_ENV['AI_TEST_DB_PASSWORD']}", "-D", database],
            input=schema, capture_output=True, text=True, timeout=300,
        )
        if proc.returncode != 0:
            err = proc.stderr
            if re.search(r"Duplicate column name|already exists", err):
                skipped.append(label)
                continue
            raise RuntimeError(f"load {label} failed: {err.strip()[:800]}")
    if skipped:
        print(f"[create_database] migrations already in base, skipped: {skipped}")
    return database


def drop_database(database):
    assert_isolated(database)
    mysql(f"DROP DATABASE IF EXISTS `{database}`;")


REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def git_sql(commit, path):
    """取指定提交内的 SQL 文件内容，保证建库脚本不受并行上线的未提交改动影响。"""
    proc = subprocess.run(["git", "show", f"{commit}:{path}"],
                          capture_output=True, text=True, timeout=30, cwd=REPO_ROOT)
    if proc.returncode != 0:
        raise RuntimeError(f"git show {commit}:{path} failed: {proc.stderr.strip()[:200]}")
    return proc.stdout


def git_blob_sha(path):
    proc = subprocess.run(["git", "rev-parse", f"HEAD:{path}"],
                          capture_output=True, text=True, timeout=30, cwd=REPO_ROOT)
    return proc.stdout.strip()


NEW_COMMIT = "eafc7cc156d02c7e27ef00466305be160e98a393"
OLD_COMMIT = "2036e7af11be3c8b099679fd7ca04afd7f7b0b2f"


def new_schema_parts():
    """新版隔离库：基线 xinyu.sql + 按文件名序的全部迁移。"""
    parts = [("base", git_sql(NEW_COMMIT, "sql/xinyu.sql"))]
    listing = subprocess.run(["git", "ls-tree", "-r", "--name-only", NEW_COMMIT, "sql/migrations"],
                             capture_output=True, text=True, cwd=REPO_ROOT).stdout.split()
    for name in sorted(listing):
        parts.append((name, git_sql(NEW_COMMIT, name)))
    return parts


def old_schema_parts():
    """旧版隔离库：旧提交自带的 xinyu.sql（当时已含该提交自身的模型名长度对齐）。"""
    return [("old-base", git_sql(OLD_COMMIT, "sql/xinyu.sql"))]


# ---- HTTP 客户端 ----


class Api:
    """心屿后端 HTTP 客户端；token 通过登录获取。"""

    def __init__(self, base_url, timeout=70.0):
        self.base = base_url.rstrip("/")
        self.client = httpx.Client(base_url=self.base, timeout=timeout)

    def close(self):
        self.client.close()

    def register(self, username, password, nickname=None):
        body = {"username": username, "password": password}
        if nickname:
            body["nickname"] = nickname
        r = self.client.post("/user/register", json=body)
        return r.json()

    def login(self, username, password):
        r = self.client.post("/user/login", json={"username": username, "password": password})
        payload = r.json()
        if payload.get("code") != 200:
            raise RuntimeError(f"login failed: {payload}")
        return payload["data"]["token"]

    def with_token(self, token):
        headers = {"token": token}
        return TokenApi(self.client, headers)


class TokenApi:
    def __init__(self, client, headers):
        self.client = client
        self.headers = headers

    def save_mood(self, mood_type, content, need_ai_reply=True):
        r = self.client.post("/mood/add",
                             json={"moodType": mood_type, "content": content,
                                   "needAiReply": need_ai_reply},
                             headers=self.headers)
        return r.json()

    def update_mood(self, record_id, mood_type, content, need_ai_reply=True):
        r = self.client.put(f"/mood/update/{record_id}",
                            json={"moodType": mood_type, "content": content,
                                  "needAiReply": need_ai_reply},
                            headers=self.headers)
        return r.json()

    def delete_mood(self, record_id):
        r = self.client.delete(f"/mood/delete/{record_id}", headers=self.headers)
        return r.json()

    def mood_detail(self, record_id):
        r = self.client.get(f"/mood/detail/{record_id}", headers=self.headers)
        return r.json()

    def mood_list(self, page_num=1, page_size=10, mood_type=None, start=None, end=None):
        params = {"pageNum": page_num, "pageSize": page_size}
        if mood_type:
            params["moodType"] = mood_type
        if start:
            params["startDate"] = start
        if end:
            params["endDate"] = end
        r = self.client.get("/mood/list", params=params, headers=self.headers)
        return r.json()

    def job(self, job_id):
        r = self.client.get(f"/mood/ai-job/{job_id}", headers=self.headers)
        return r.json()

    def job_retry(self, job_id):
        r = self.client.post(f"/mood/ai-job/{job_id}/retry", headers=self.headers)
        try:
            return r.json()
        except ValueError:
            # 冲突等状态可能返回非 JSON 包装体；保留 HTTP 状态码供断言
            return {"code": r.status_code, "message": "non_json_body", "http_status": r.status_code}

    def community_list(self, topic_id, page_num=1, page_size=10):
        r = self.client.get("/community/post/list",
                            params={"topicId": topic_id, "pageNum": page_num,
                                    "pageSize": page_size},
                            headers=self.headers)
        return r.json()


def ensure_user(api: Api, username, password):
    """注册（已存在则忽略）并登录，返回 token。昵称必填以兼容旧版（eafc7cc 起才改选填）。"""
    payload = api.register(username, password, nickname=username)
    if payload.get("code") != 200:
        message = str(payload.get("message", ""))
        if "已存在" not in message:
            raise RuntimeError(f"register failed: {payload}")
    return api.login(username, password)


# ---- 统计 ----


def percentile(samples, p):
    """最近邻秩分位数：P50 输入 0.5；样本为空时抛错。"""
    if not samples:
        raise ValueError("empty samples")
    ordered = sorted(samples)
    rank = max(1, math.ceil(p * len(ordered)))
    return ordered[rank - 1]


def stats(samples):
    """输出报告用摘要：n/mean/P50/P95/P99（min/max 备查）。"""
    if not samples:
        return {"n": 0}
    ordered = sorted(samples)
    return {
        "n": len(ordered),
        "mean": round(sum(ordered) / len(ordered), 2),
        "p50": percentile(ordered, 0.50),
        "p95": percentile(ordered, 0.95),
        "p99": percentile(ordered, 0.99) if len(ordered) >= 100 else None,
        "min": ordered[0],
        "max": ordered[-1],
    }


def now_ms():
    return time.time_ns() // 1_000_000


def write_meta(out_dir, meta):
    os.makedirs(out_dir, exist_ok=True)
    meta = dict(meta)
    meta["written_at"] = time.strftime("%Y-%m-%d %H:%M:%S")
    with open(os.path.join(out_dir, "run-meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)


def append_jsonl(path, record):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "a", encoding="utf-8") as f:
        f.write(json.dumps(record, ensure_ascii=False) + "\n")


def read_jsonl(path):
    records = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    return records


def upstream_log_path():
    return os.path.join(os.path.dirname(__file__), "results", "fake_upstream.log")


RESULTS_ROOT = os.path.join(os.path.dirname(__file__), "results")
