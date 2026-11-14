"""evals.review_blind_sheet 的单元测试：分层抽样配额、日期标签替换与关闭记录标灰。"""

import random

from evals.review_blind_sheet import (
    format_date_label,
    render_review_sheet,
    replace_refs_with_dates,
    stratified_pick,
)


def _fake_result(sample_id: str, category: str, review_type: str = "WEEK"):
    return {
        "id": sample_id,
        "category": category,
        "review_type": review_type,
        "result_kind": "MODEL",
        "records": [
            {"date": "2026-11-02", "time": "20:00", "mood_type": "疲惫", "content": "加班", "ai_enabled": True},
            {"date": "2026-11-04", "time": "21:00", "mood_type": "难过", "content": "没发给小芽的事", "ai_enabled": False},
        ],
        "sent_entries": [
            {"ref": "r1", "date": "2026-11-02", "weekday": "周一", "mood_type": "疲惫", "content": "加班"},
        ],
        "body": "周一加班辛苦了{{r1}}。",
        "ps": None,
    }


def test_stratified_pick_matches_section6_quotas_and_excludes_injection_crisis():
    pool = (
        [_fake_result(f"W-normal-{i:02d}", "normal_week") for i in range(1, 11)]
        + [_fake_result(f"W-sparse-{i:02d}", "sparse") for i in range(1, 5)]
        + [_fake_result(f"W-tie-{i:02d}", "tie") for i in range(1, 5)]
        + [_fake_result(f"W-partial-{i:02d}", "partial") for i in range(1, 5)]
        + [_fake_result(f"R-recent-{i:02d}", "recent30", "RECENT_30") for i in range(1, 7)]
        + [_fake_result("W-inj-01", "injection")]
        + [{**_fake_result("W-cri-01", "crisis"), "result_kind": "SUPPORT"}]
    )

    picked = stratified_pick(pool, random.Random(11))
    assert len(picked) == 15
    counts = {}
    for item in picked:
        counts[item["category"]] = counts.get(item["category"], 0) + 1
    assert counts == {
        "normal_week": 6,
        "sparse": 2,
        "tie": 2,
        "partial": 2,
        "recent30": 3,
    }


def test_replace_refs_and_render_sheet():
    assert format_date_label("2026-11-02") == "[11/2]"
    assert format_date_label("2026-10-09") == "[10/9]"

    entries = [
        {"ref": "r1", "date": "2026-11-02"},
        {"ref": "r2", "date": "2026-10-09"},
    ]
    replaced = replace_refs_with_dates("前阵子忙{{r2}}，这周一还在加班{{r1}}。", entries)
    assert replaced == "前阵子忙[10/9]，这周一还在加班[11/2]。"

    sheet = render_review_sheet([_fake_result("W-partial-01", "partial")], seed=11)
    assert "未发给小芽" in sheet
    assert "color:#888" in sheet
    assert "周一加班辛苦了[11/2]。" in sheet
    assert "P.S. （无）" in sheet
    assert "哪句不舒服" in sheet
