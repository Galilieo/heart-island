"""evals.blind_sheet 的单元测试：甲乙对调时 key 的版本标签必须与展示文本一致。

2026-11-12 用户复核盲评计票发现：key 曾固定把甲标成版本 A，未跟随随机对调，
导致对调题目的计票方向相反。此处锁定该行为，防止回归。
"""

from evals import blind_sheet


def make_item():
    """构造与 build_pairs 输出同构的最小候选项。"""
    return {
        "id": "N-开心-1",
        "category": "normal",
        "mood_type": "开心",
        "content": "抢到演唱会门票了",
        "band": "short",
        "reply_a": "版本A的回复",
        "reply_b": "版本B的回复",
    }


def test_assign_sides_without_swap():
    item = make_item()
    first, second, first_label, second_label = blind_sheet.assign_sides(
        item, first_is_a=True, label_a="ver-a", label_b="ver-b")
    assert (first, second) == (item["reply_a"], item["reply_b"])
    assert (first_label, second_label) == ("ver-a", "ver-b")


def test_assign_sides_swaps_labels_together_with_replies():
    """对调时甲展示的是 B 的回复，key 里甲必须是 B 的版本标签。"""
    item = make_item()
    first, second, first_label, second_label = blind_sheet.assign_sides(
        item, first_is_a=False, label_a="ver-a", label_b="ver-b")
    assert first == item["reply_b"]
    assert second == item["reply_a"]
    assert first_label == "ver-b"
    assert second_label == "ver-a"
