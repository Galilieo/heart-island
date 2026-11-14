"""小芽回顾第一轮盲评打分表生成脚本（任务书第 6 节）。

从 results.jsonl 中按类别分层抽取 15 份模型输出：
normal_week 6、sparse 2、tie 2、partial 2、recent30 3；
注入与危机样例不进入盲评。

用法（在仓库根目录）：
  uv run --directory xinyu-ai --locked python evals/review_blind_sheet.py \
      --results <run目录>/results.jsonl --out <run目录>/review-sheet.md [--seed 11]
"""

import argparse
import json
import random
import re
import sys
from collections import defaultdict
from pathlib import Path

# 引用标记正则，与 app.reviews.REFERENCE 保持同一格式。
REFERENCE = re.compile(r"\{\{(r[1-9][0-9]*)}}")

# 第 6 节分层抽样配额（共 15 份）。
STRATA_QUOTA = (
    ("normal_week", 6),
    ("sparse", 2),
    ("tie", 2),
    ("partial", 2),
    ("recent30", 3),
)


def load_results(path: Path) -> list[dict]:
    records = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            records.append(json.loads(line))
    return records


def stratified_pick(results: list[dict], rng: random.Random) -> list[dict]:
    """按第 6 节配额从成功生成 MODEL 正文的样本中分层抽样。"""
    by_category: dict[str, list[dict]] = defaultdict(list)
    for record in results:
        if record.get("result_kind") == "MODEL" and record.get("body"):
            by_category[record["category"]].append(record)

    picked = []
    for category, quota in STRATA_QUOTA:
        pool = list(by_category.get(category, []))
        rng.shuffle(pool)
        if len(pool) < quota:
            print(f"警告：类别 {category} 有效样本仅 {len(pool)} 份，少于配额 {quota}", file=sys.stderr)
        picked.extend(pool[:quota])
    return picked


def format_date_label(iso_date: str) -> str:
    """将 YYYY-MM-DD 转为 [11/2] 样式（月和日不补零）。"""
    _, month, day = iso_date.split("-")
    return f"[{int(month)}/{int(day)}]"


def replace_refs_with_dates(text: str, sent_entries: list[dict]) -> str:
    """将正文或 P.S. 中的 {{rN}} 替换为对应记录的 [M/D] 日期标签。"""
    mapping = {entry["ref"]: format_date_label(entry["date"]) for entry in sent_entries}
    return REFERENCE.sub(lambda match: mapping.get(match.group(1), match.group(0)), text)


def render_review_sheet(items: list[dict], seed: int) -> str:
    """生成给用户逐份打分与批注的 review-sheet.md。"""
    lines = ["# 小芽回顾盲评打分表", ""]
    lines.append(f"- 抽样数：{len(items)} 份（normal_week 6 / sparse 2 / tie 2 / partial 2 / recent30 3），随机种子：{seed}")
    lines.append("- 每份请按 1～5 分打分（5 分为最好），并在“哪句不舒服”写下具体批注。")
    lines.append("- 通过参考：三项平均分均 ≥ 4.0，且没有一份出现事实编造。")
    lines.append("")

    for index, item in enumerate(items, start=1):
        kind_label = "周报" if item["review_type"] == "WEEK" else "近 30 天"
        lines.append(f"## 第 {index} 份 · {item['id']}（{kind_label} · {item['category']}）")
        lines.append("")
        lines.append("### 原始记录")
        lines.append("")
        ordered = sorted(item["records"], key=lambda r: (r["date"], r["time"]))
        for rec in ordered:
            stamp = f"{rec['date']} {rec['time']}"
            if rec.get("ai_enabled"):
                lines.append(f"- {stamp} · {rec['mood_type']}：{rec['content']}")
            else:
                lines.append(
                    f"- <span style=\"color:#888\">{stamp} · {rec['mood_type']}（未发给小芽）：{rec['content']}</span>"
                )
        lines.append("")

        body_display = replace_refs_with_dates(item["body"], item["sent_entries"])
        ps_display = (
            replace_refs_with_dates(item["ps"], item["sent_entries"])
            if item.get("ps") is not None
            else "（无）"
        )
        lines.append("### 小芽写的信")
        lines.append("")
        lines.append(f"> {body_display}")
        lines.append("")
        lines.append(f"P.S. {ps_display}")
        lines.append("")
        lines.append("### 打分（1～5 分）")
        lines.append("")
        lines.append("- 像朋友写的（口吻自然，没有机器感，也不老气）：")
        lines.append("- 说到点上（挑的事情对，没有编造）：")
        lines.append("- 引用自然（日期标签放的位置不突兀）：")
        lines.append("- 哪句不舒服：")
        lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="生成小芽回顾盲评打分表")
    parser.add_argument("--results", required=True, help="results.jsonl 路径")
    parser.add_argument("--out", required=True, help="输出 review-sheet.md 路径")
    parser.add_argument("--seed", type=int, default=11, help="随机种子（默认 11）")
    args = parser.parse_args()

    results = load_results(Path(args.results))
    rng = random.Random(args.seed)
    items = stratified_pick(results, rng)

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(render_review_sheet(items, args.seed), encoding="utf-8")
    print(f"盲评打分表已生成：{len(items)} 份，种子 {args.seed} -> {out_path}")


if __name__ == "__main__":
    main()
