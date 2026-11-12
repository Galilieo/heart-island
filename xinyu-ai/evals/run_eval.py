"""心情回复合成测评运行脚本（手册 AI_WORKLOG/plans/2026-11-11-ai-reply-prompt-v2-eval-plan.md 第 3.4 节）。

候选提示词在运行期替换 app.provider.SYSTEM_PROMPT 模块变量——messages() 在
调用时读取该全局——因此不改生产代码签名。替换前逐字比对「# 安全」小节，
与现行提示词不一致即拒绝运行，防止候选稿弱化安全段（手册第 0、4 节）。

密钥只从进程环境读取；results.jsonl / summary.md / run-meta.json 只包含合成
输入、模型输出与非敏感运行信息，不写入密钥或 base URL 的敏感部分。

用法示例（在仓库根目录）：
  uv run --directory xinyu-ai --locked python evals/run_eval.py \
      --label v1 --set evals/mood_reply_set.jsonl --dry-run
  真实运行需先在进程环境注入 Settings.from_env() 要求的全部变量。
"""

import argparse
import asyncio
import hashlib
import json
import subprocess
import sys
import time
from pathlib import Path
from urllib.parse import urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import provider
from app.contracts import MoodReplyRequest
from app.safety import build_support_reply
from app.settings import Settings
from evals.checks import run_checks

# 手册第 2 节：单遍全集的模型调用上限为 100（正则命中不调用）。
MANUAL_RUN_CALL_CAP = 100

# dry-run 使用的固定假回复（轮换），仅验证链路与汇总，不访问网络。
DRY_RUN_REPLIES = (
    "听起来散步给你带来了一些平静，可以给自己留一点这样的时间。",
    "两周啊，总算不用再惦记它了。今晚能睡个踏实觉。",
    "嗯，先这样，回头再说。",
)


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def extract_section(text: str, heading: str) -> str:
    """提取「# 标题」小节全文（到下一个一级标题），用于安全段逐字比对。"""
    lines = text.splitlines()
    captured = []
    inside = False
    for line in lines:
        if line.startswith("# "):
            if inside:
                break
            inside = line.strip() == f"# {heading}"
            if inside:
                captured.append(line)
        elif inside:
            captured.append(line)
    return "\n".join(captured).strip()


def git_commit() -> str:
    """记录当前提交，便于追溯基线；不在 git 环境时如实标注。"""
    try:
        return subprocess.run(
            ["git", "rev-parse", "HEAD"],
            capture_output=True, text=True, check=True,
        ).stdout.strip()
    except (subprocess.CalledProcessError, OSError):
        return "not-a-git-worktree"


def load_samples(path: Path) -> list[dict]:
    samples = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            samples.append(json.loads(line))
    return samples


def make_request(sample: dict, index: int) -> MoodReplyRequest:
    """把测评样本包装成合法内部请求；关联字段使用测评专用合成值。"""
    return MoodReplyRequest(
        request_id=f"eval-{index:03d}",
        job_id=str(1000 + index),
        attempt=1,
        source_version="1",
        mood_type=sample["mood_type"],
        content=sample["content"],
        budget_ms=40000,
    )


def percentile(values: list[float], fraction: float) -> int:
    """简单最近邻百分位，样本少于 2 个时返回 0。"""
    if not values:
        return 0
    ordered = sorted(values)
    rank = min(len(ordered) - 1, round(fraction * (len(ordered) - 1)))
    return int(ordered[rank])


def hit_rate(flags: list[bool | None]) -> tuple[int, int]:
    """统计非 None 标志的命中数与分母（None = 该样本不适用此项检查）。"""
    applied = [flag for flag in flags if flag is not None]
    return sum(1 for flag in applied if flag), len(applied)


def render_summary(label: str, samples: list[dict], results: list[dict],
                   regex_matrix: dict, meta: dict) -> str:
    """按手册 3.4 生成 summary.md：检查命中率、长度分布、混淆矩阵与人工审阅清单。"""
    lines = [f"# 心情回复测评汇总 · {label}", ""]
    lines.append(f"- 日期：{meta['date']}　提示词：{meta['prompt_file']}（sha256 {meta['prompt_sha256'][:12]}…）")
    lines.append(f"- 模型：{meta['model']}　thinking：{meta['thinking_mode']}　max_tokens：{meta['max_tokens']}")
    lines.append(f"- 样本：{len(samples)} 条　模型调用：{meta['calls']} 次　失败：{meta['failures']} 条　dry-run：{meta['dry_run']}")
    lines.append("")

    by_id = {r["id"]: r for r in results}
    model_results = [r for r in results if r["result_kind"] == "MODEL" and r["reply"] is not None]

    # —— 各检查命中率：整体 + 按类别，分母为适用样本数 ——
    lines.append("## 检查命中率（命中数/适用数，None 表示样本不适用）")
    lines.append("")
    lines.append("| 检查 | 整体 | normal | short | mismatch | injection | crisis_boundary | crisis_other |")
    lines.append("| --- | --- | --- | --- | --- | --- | --- | --- |")
    check_keys = [
        "opens_with_echo", "three_beat", "permission_ending",
        "evaluative_phrases_hit", "advice_on_positive",
        "length_band_ok", "script_mismatch", "banned_cliche",
        "fabricated_contact", "single_paragraph_bad",
    ]
    check_names = {
        "opens_with_echo": "开头复述",
        "three_beat": "三段式",
        "permission_ending": "许可收尾",
        "evaluative_phrases_hit": "评价式短语",
        "advice_on_positive": "正向给建议",
        "length_band_ok": "长度档合规",
        "script_mismatch": "字形不一致",
        "banned_cliche": "禁忌套话",
        "fabricated_contact": "编造联系方式",
        "single_paragraph_bad": "单段违规",
    }
    # 命中方向说明：length_band_ok / script_match 为“合格”，其余为“问题命中”。
    def cell(key: str, rows: list[dict]) -> str:
        flags = []
        for r in rows:
            checks = r.get("checks")
            if not checks:
                continue
            if key == "evaluative_phrases_hit":
                flags.append(bool(checks["evaluative_phrases"]))
            elif key == "advice_on_positive":
                # 仅正向样本适用；非正向样本计 None
                applicable = r.get("expect", {}).get("positive_mood")
                flags.append(bool(checks["advice_markers"]) if applicable else None)
            elif key == "script_mismatch":
                # script_match=True 为合格；此处报“不一致”命中
                flags.append(not checks["script_match"] if r.get("script") else None)
            elif key == "single_paragraph_bad":
                flags.append(not checks["single_paragraph"])
            else:
                flags.append(checks[key])
        hit, denom = hit_rate(flags)
        return f"{hit}/{denom}" if denom else "—"

    category_order = ["normal", "short", "mismatch", "injection", "crisis_boundary", "crisis_other"]
    for key in check_keys:
        row = [check_names[key], cell(key, results)]
        for category in category_order:
            row.append(cell(key, [r for r in results if r["category"] == category]))
        lines.append("| " + " | ".join(row) + " |")
    lines.append("")

    # —— 手册 5.1 门槛口径的专用分母 ——
    non_crisis = [r for r in results if r["category"] not in {"crisis_pos"}]
    positive = [r for r in results if r.get("expect", {}).get("positive_mood")]
    hant = [r for r in results if r.get("script") == "hant"]
    gates = [
        ("opens_with_echo（非危机样本）", [r["checks"]["opens_with_echo"] for r in non_crisis if r.get("checks")]),
        ("permission_ending（正向样本）", [r["checks"]["permission_ending"] for r in positive if r.get("checks")]),
        ("advice_on_positive（正向样本）", [bool(r["checks"]["advice_markers"]) for r in positive if r.get("checks")]),
        ("script 一致（繁体样本）", [r["checks"]["script_match"] for r in hant if r.get("checks")]),
    ]
    lines.append("## 门槛口径")
    lines.append("")
    for name, flags in gates:
        hit, denom = hit_rate(flags)
        rate = f"{hit / denom:.1%}" if denom else "—"
        lines.append(f"- {name}：{hit}/{denom} = {rate}")
    lines.append("")

    # —— MODEL 回复长度分布 ——
    lengths = [r["checks"]["length_chars"] for r in model_results]
    lines.append("## MODEL 回复长度（字符）")
    if lengths:
        lines.append("")
        lines.append(f"- 全部 MODEL 回复 {len(lengths)} 条：P10={percentile(lengths, 0.1)}、P50={percentile(lengths, 0.5)}、P90={percentile(lengths, 0.9)}")
        for category in ("normal", "short", "mismatch"):
            subset = [r["checks"]["length_chars"] for r in model_results if r["category"] == category]
            if subset:
                lines.append(f"- {category} {len(subset)} 条：P10={percentile(subset, 0.1)}、P50={percentile(subset, 0.5)}、P90={percentile(subset, 0.9)}")
    else:
        lines.append("")
        lines.append("- 无 MODEL 回复")
    lines.append("")

    # —— 正则层混淆矩阵（不依赖模型）——
    lines.append("## 正则层混淆矩阵")
    lines.append("")
    matrix = regex_matrix
    lines.append(f"- TP={matrix['tp']}　FP={matrix['fp']}　FN={matrix['fn']}　TN={matrix['tn']}")
    lines.append(f"- 精确率={matrix['precision']:.3f}　召回率={matrix['recall']:.3f}　F1={matrix['f1']:.3f}")
    lines.append(f"- FN（应为 SUPPORT 未被正则命中，走到模型）：{', '.join(matrix['fn_ids']) or '无'}")
    lines.append(f"- FP（不应 SUPPORT 却被正则命中）：{', '.join(matrix['fp_ids']) or '无'}")
    lines.append("")

    # —— 人工审阅清单（手册 5.2）——
    review = [r for r in results
              if r["category"] == "injection"
              or (r.get("expect", {}).get("should_support") and r["result_kind"] == "MODEL")]
    lines.append("## 人工审阅清单（注入样本 + 正则未命中走到模型的危机样本）")
    lines.append("")
    if review:
        for r in review:
            reply = r["reply"] if r["reply"] is not None else f"[失败:{r.get('error_code')}]"
            lines.append(f"### {r['id']}（{r['category']}·{r['mood_type']}）")
            lines.append(f"- 正文：{r['content']}")
            lines.append(f"- 回复：{reply}")
            lines.append("")
    else:
        lines.append("- 无")
        lines.append("")

    # —— crisis_boundary 走到模型的过度反应审阅 ——
    boundary_model = [r for r in results
                      if r["category"] == "crisis_boundary" and r["result_kind"] == "MODEL"]
    lines.append(f"## crisis_boundary 走到模型（{len(boundary_model)} 条，回复见 results.jsonl）")
    lines.append("")
    lines.append("-ID 列表：" + ", ".join(r["id"] for r in boundary_model))
    lines.append("")
    return "\n".join(lines)


def compute_regex_matrix(samples: list[dict]) -> dict:
    """对全集计算正则层混淆矩阵（纯函数，不依赖模型调用）。"""
    tp, fp, fn, tn = [], [], [], []
    for sample in samples:
        want = bool(sample["expect"]["should_support"])
        hit = build_support_reply(sample["content"]) is not None
        if want and hit:
            tp.append(sample["id"])
        elif want and not hit:
            fn.append(sample["id"])
        elif not want and hit:
            fp.append(sample["id"])
        else:
            tn.append(sample["id"])
    precision = len(tp) / (len(tp) + len(fp)) if tp or fp else 0.0
    recall = len(tp) / (len(tp) + len(fn)) if tp or fn else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {
        "tp": len(tp), "fp": len(fp), "fn": len(fn), "tn": len(tn),
        "precision": precision, "recall": recall, "f1": f1,
        "tp_ids": tp, "fp_ids": fp, "fn_ids": fn, "tn_ids": tn,
    }


class DryRunProvider:
    """离线替身：按序轮换固定回复，不访问网络（阶段 0 验收用）。"""

    def __init__(self, settings):
        self.settings = settings
        self.calls = 0

    async def generate(self, request, deadline):
        self.calls += 1
        reply = DRY_RUN_REPLIES[self.calls % len(DRY_RUN_REPLIES)]
        from types import SimpleNamespace
        return SimpleNamespace(reply_content=reply, model_name="dry-run-model")


def parse_only_ids(raw: str) -> set[str] | None:
    """解析 --only-ids：逗号分隔列表或每行一个 id 的文件路径。"""
    if not raw:
        return None
    path = Path(raw)
    if path.is_file():
        return {line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()}
    return {part.strip() for part in raw.split(",") if part.strip()}


async def run(args) -> int:
    set_path = Path(args.set)
    full_samples = load_samples(set_path)
    only_ids = parse_only_ids(args.only_ids)
    samples = full_samples if only_ids is None else [s for s in full_samples if s["id"] in only_ids]
    missing = only_ids - {s["id"] for s in full_samples} if only_ids else set()
    if missing:
        print(f"以下 id 不在测评集中：{sorted(missing)}", file=sys.stderr)
        return 2

    # 提示词：缺省用生产 SYSTEM_PROMPT；候选文件先比对安全段与「# 输入」前两行。
    if args.prompt_file:
        prompt_path = Path(args.prompt_file)
        candidate = prompt_path.read_text(encoding="utf-8").strip()
        current_safety = extract_section(provider.SYSTEM_PROMPT, "安全")
        candidate_safety = extract_section(candidate, "安全")
        if current_safety != candidate_safety:
            print("候选提示词的「# 安全」小节与现行不一致，拒绝运行", file=sys.stderr)
            return 2
        current_intro = extract_section(provider.SYSTEM_PROMPT, "输入").splitlines()[:3]
        candidate_intro = extract_section(candidate, "输入").splitlines()[:3]
        if current_intro != candidate_intro:
            print("候选提示词的「# 输入」前两行与现行不一致，拒绝运行", file=sys.stderr)
            return 2
        provider.SYSTEM_PROMPT = candidate
        prompt_name = prompt_path.name
        prompt_sha = sha256_text(candidate)
    else:
        prompt_name = "app.provider.SYSTEM_PROMPT"
        prompt_sha = sha256_text(provider.SYSTEM_PROMPT)

    settings = None
    model_display = "dry-run"
    thinking_display = "n/a"
    max_tokens_display = "n/a"
    base_host = ""
    if not args.dry_run:
        settings = Settings.from_env()
        model_display = settings.model_name
        thinking_display = settings.model_thinking_mode
        max_tokens_display = str(settings.model_max_tokens)
        base_host = urlsplit(settings.model_base_url).hostname or ""

    out_dir = Path(args.out) if args.out else (
        Path(__file__).resolve().parents[2] / "AI_WORKLOG" / "evals" / "2026-11-11-mood-reply"
        / f"{args.label}-{time.strftime('%H%M%S')}"
    )
    out_dir.mkdir(parents=True, exist_ok=True)

    matrix = compute_regex_matrix(full_samples)

    http_provider = None
    if not args.dry_run:
        http_provider = provider.HttpProvider(settings)
    else:
        http_provider = DryRunProvider(settings)

    results = []
    model_calls = 0
    failures = 0
    loop = asyncio.get_running_loop()

    for index, sample in enumerate(samples):
        support = build_support_reply(sample["content"])
        if support is not None:
            reply = support
            result_kind = "SUPPORT"
            model_called = False
            latency_ms = 0
            error_code = None
        elif args.limit is not None and model_calls >= args.limit:
            reply = None
            result_kind = "SKIPPED"
            model_called = False
            latency_ms = 0
            error_code = "skipped_limit"
        else:
            request = make_request(sample, index)
            started = loop.time()
            try:
                model_reply = await http_provider.generate(
                    request, loop.time() + (settings.request_timeout_seconds if settings else 40))
                reply = model_reply.reply_content
                result_kind = "MODEL"
                model_called = True
                error_code = None
            except provider.ProviderFailure as failure:
                reply = None
                result_kind = "MODEL"
                model_called = True
                error_code = failure.code
                failures += 1
            latency_ms = int((loop.time() - started) * 1000)
            model_calls += 1

        record = {
            "label": args.label,
            "id": sample["id"],
            "category": sample["category"],
            "mood_type": sample["mood_type"],
            "content": sample["content"],
            "script": sample["script"],
            "expect": sample["expect"],
            "result_kind": result_kind,
            "model_called": model_called,
            "reply": reply,
            "latency_ms": latency_ms,
            "error_code": error_code,
            "checks": run_checks(sample, reply) if reply is not None else None,
        }
        results.append(record)
        if (index + 1) % 20 == 0:
            print(f"进度 {index + 1}/{len(samples)}，模型调用 {model_calls} 次")

    if model_calls > MANUAL_RUN_CALL_CAP:
        print(f"警告：本遍模型调用 {model_calls} 次超过手册单遍上限 {MANUAL_RUN_CALL_CAP}", file=sys.stderr)

    meta = {
        "date": time.strftime("%Y-%m-%d %H:%M:%S"),
        "label": args.label,
        "prompt_file": prompt_name,
        "prompt_sha256": prompt_sha,
        "set_file": set_path.name,
        "set_sha256": sha256_file(set_path),
        "model": model_display,
        "thinking_mode": thinking_display,
        "max_tokens": max_tokens_display,
        "provider_host": base_host,
        "calls": model_calls,
        "failures": failures,
        "samples": len(samples),
        "only_ids": sorted(only_ids) if only_ids else None,
        "dry_run": args.dry_run,
        "limit": args.limit,
        "git_commit": git_commit(),
        "regex_matrix": matrix,
    }

    results_path = out_dir / "results.jsonl"
    with results_path.open("w", encoding="utf-8") as handle:
        for record in results:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")

    (out_dir / "run-meta.json").write_text(
        json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")

    summary = render_summary(args.label, samples, results, matrix, meta)
    (out_dir / "summary.md").write_text(summary, encoding="utf-8")

    print(f"完成：模型调用 {model_calls} 次，失败 {failures} 条")
    print(f"产物目录：{out_dir}")
    return 0


def main():
    parser = argparse.ArgumentParser(description="心情回复合成测评运行脚本")
    parser.add_argument("--set", required=True, help="测评集 jsonl 路径")
    parser.add_argument("--prompt-file", default=None, help="候选提示词；缺省用生产 SYSTEM_PROMPT")
    parser.add_argument("--label", required=True, help="版本标签，如 v1 / v2")
    parser.add_argument("--out", default=None, help="产物目录；缺省 AI_WORKLOG/evals/2026-11-11-mood-reply/<label>-<时间>")
    parser.add_argument("--limit", type=int, default=None, help="模型调用次数上限，达到即停")
    parser.add_argument("--only-ids", default=None,
                        help="只执行指定样本（逗号分隔 id 或每行一个 id 的文件），用于失败定向重试；"
                             "混淆矩阵仍按完整测评集计算")
    parser.add_argument("--dry-run", action="store_true", help="使用离线替身，不访问网络")
    args = parser.parse_args()
    raise SystemExit(asyncio.run(run(args)))


if __name__ == "__main__":
    main()
