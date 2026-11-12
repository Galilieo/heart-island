"""盲评表生成脚本（手册第 3.5 节）。

从两份 results.jsonl（v1 与 v2）中，对 normal / short / mismatch 类且两版
都有成功回复的样本，按心情与正文长度档分层抽 30 题配对，随机决定甲乙顺序，
输出 blind.md（不含版本标签）与 blind-key.json（对应关系，用户完成前不得
展示）。两条回复完全相同的题目剔除并从剩余池补抽。

用法：
  uv run --directory xinyu-ai --locked python evals/blind_sheet.py \
      --a <v1>/results.jsonl --b <v2>/results.jsonl --out <目录> [--seed 11]
"""

import argparse
import json
import random
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from evals.checks import BAND_SHORT_CONTENT_MAX, BAND_MID_CONTENT_MAX

# 手册 3.5：只从这三类里抽，危机与注入样本不进入盲评。
BLIND_CATEGORIES = {"normal", "short", "mismatch"}
# 30 题为手册 5.3 的小样本设定。
BLIND_COUNT = 30


def content_band(content: str) -> str:
    if len(content) <= BAND_SHORT_CONTENT_MAX:
        return "short"
    if len(content) <= BAND_MID_CONTENT_MAX:
        return "mid"
    return "long"


def load_results(path: Path) -> dict[str, dict]:
    records = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            record = json.loads(line)
            records[record["id"]] = record
    return records


def build_pairs(a_results: dict[str, dict], b_results: dict[str, dict]) -> list[dict]:
    """两版都成功生成 MODEL 回复、且文案不同的样本进入候选池。"""
    pool = []
    for sample_id, record_a in a_results.items():
        record_b = b_results.get(sample_id)
        if not record_b:
            continue
        if record_a["category"] not in BLIND_CATEGORIES:
            continue
        reply_a = record_a.get("reply")
        reply_b = record_b.get("reply")
        if not reply_a or not reply_b:
            continue
        if reply_a == reply_b:
            continue
        pool.append({
            "id": sample_id,
            "category": record_a["category"],
            "mood_type": record_a["mood_type"],
            "content": record_a["content"],
            "band": content_band(record_a["content"]),
            "reply_a": reply_a,
            "reply_b": reply_b,
        })
    return pool


def stratified_sample(pool: list[dict], count: int, rng: random.Random) -> list[dict]:
    """按心情 × 长度档分组，轮转抽取，保证覆盖面；组内顺序由种子打乱。"""
    groups = defaultdict(list)
    for item in pool:
        groups[(item["mood_type"], item["band"])].append(item)
    for group in groups.values():
        rng.shuffle(group)
    keys = sorted(groups.keys())
    rng.shuffle(keys)
    picked = []
    cursor = 0
    while len(picked) < count and any(groups[key] for key in keys):
        key = keys[cursor % len(keys)]
        if groups[key]:
            picked.append(groups[key].pop())
        cursor += 1
        # 所有组取尽仍不足时退出
        if cursor > len(keys) * (count + 1):
            break
    return picked


def assign_sides(item: dict, first_is_a: bool,
                 label_a: str, label_b: str) -> tuple[str, str, str, str]:
    """按随机对调返回甲乙的回复文本与版本标签。

    甲乙的展示文本对调时，key 里的版本标签必须跟着同一方向对调，
    否则对应关系颠倒、计票方向相反（2026-11-12 用户复核发现并修复）。
    """
    if first_is_a:
        return item["reply_a"], item["reply_b"], label_a, label_b
    return item["reply_b"], item["reply_a"], label_b, label_a


def render_blind_md(items: list[dict]) -> str:
    lines = ["# 心情回复盲评", ""]
    lines.append("每题给出同一条记录的两个回复（甲、乙来自不同版本，顺序随机）。")
    lines.append("请从中选：「甲更像朋友 / 乙更像朋友 / 差不多」，并随手标出任何读着仍像 AI 的回复。")
    lines.append("")
    for index, item in enumerate(items, start=1):
        lines.append(f"## 第 {index} 题")
        lines.append(f"- 心情：{item['mood_type']}")
        lines.append(f"- 正文：{item['content']}")
        lines.append("")
        lines.append("回复甲：")
        lines.append("")
        lines.append(f"> {item['first']}")
        lines.append("")
        lines.append("回复乙：")
        lines.append("")
        lines.append(f"> {item['second']}")
        lines.append("")
        lines.append("选择：甲更像朋友 / 乙更像朋友 / 差不多")
        lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="生成盲评表与对应关系")
    parser.add_argument("--a", required=True, help="版本 A 的 results.jsonl（记录中 label 值作为版本名）")
    parser.add_argument("--b", required=True, help="版本 B 的 results.jsonl")
    parser.add_argument("--out", required=True, help="输出目录")
    parser.add_argument("--seed", type=int, default=11, help="随机种子，需写入记录")
    parser.add_argument("--count", type=int, default=BLIND_COUNT)
    args = parser.parse_args()

    a_results = load_results(Path(args.a))
    b_results = load_results(Path(args.b))
    pool = build_pairs(a_results, b_results)

    rng = random.Random(args.seed)
    picked = stratified_sample(pool, args.count, rng)
    if len(picked) < args.count:
        print(f"警告：候选池只能配出 {len(picked)} 题，少于目标 {args.count} 题", file=sys.stderr)

    items = []
    for item in picked:
        first, second, first_label, second_label = assign_sides(
            item, rng.random() < 0.5,
            a_results[item["id"]]["label"], b_results[item["id"]]["label"])
        items.append({
            **item,
            "first": first,
            "second": second,
            "first_label": first_label,
            "second_label": second_label,
        })

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "blind.md").write_text(render_blind_md(items), encoding="utf-8")
    key = {
        "seed": args.seed,
        "items": [
            {
                "no": index,
                "id": item["id"],
                "甲": item["first_label"],
                "乙": item["second_label"],
            }
            for index, item in enumerate(items, start=1)
        ],
    }
    (out_dir / "blind-key.json").write_text(
        json.dumps(key, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"盲评表已生成：{len(items)} 题，种子 {args.seed}")
    print(f"产物：{out_dir / 'blind.md'}（给用户）")
    print(f"对应关系：{out_dir / 'blind-key.json'}（用户完成前不得展示）")


if __name__ == "__main__":
    main()
