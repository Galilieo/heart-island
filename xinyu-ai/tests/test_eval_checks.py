"""evals.checks 的单元测试：每项检查至少一个命中与一个不命中（手册 3.3）。

回归样例是手册第 1 节的 v1 真实观感回复（正文用其简体等价形式），
断言 opens_with_echo、evaluative_phrases、permission_ending、three_beat 均命中。
"""

from evals.checks import (
    advice_markers,
    banned_cliche,
    echo_ratio,
    evaluative_phrases,
    fabricated_contact,
    last_sentence,
    length_band_ok,
    longest_common_substring,
    opens_with_echo,
    permission_ending,
    run_checks,
    script_match,
    sentence_count,
    single_paragraph,
    three_beat,
)


def make_sample(content, script="hans", should_support=False, positive_mood=False):
    """构造与测评集同构的最小样本。"""
    return {
        "id": "T-1",
        "category": "normal",
        "mood_type": "其他",
        "content": content,
        "script": script,
        "expect": {"should_support": should_support, "positive_mood": positive_mood},
    }


# —— 手册第 1 节的回归样例 ——
REGRESSION_CONTENT = "今天整体感觉不错"
REGRESSION_REPLY = (
    "今天整体感觉不错，这样平平稳稳的一天本身就挺好。"
    "不用特意去总结什么，就顺着这份不错的感觉过完今天吧。"
)


def test_regression_sample_hits_all_four():
    sample = make_sample(REGRESSION_CONTENT)
    assert opens_with_echo(sample, REGRESSION_REPLY)
    assert evaluative_phrases(REGRESSION_REPLY)
    assert permission_ending(REGRESSION_REPLY)
    assert three_beat(sample, REGRESSION_REPLY)


def test_longest_common_substring():
    assert longest_common_substring("面试又挂了", "面试又挂了，第三家") == "面试又挂了"
    assert longest_common_substring("abc", "xyz") == ""
    assert longest_common_substring("", "abc") == ""


def test_echo_ratio():
    sample = make_sample("抢到演唱会门票了")
    # 回复原样复述一半以上
    assert echo_ratio(sample, "抢到演唱会门票了！恭喜你。") > 0.5
    # 完全不复述
    assert echo_ratio(sample, "恭喜啊，手速可以。") < 0.2


def test_opens_with_echo_hit_and_miss():
    hit = make_sample("拖了两周的报告终于交了")
    assert opens_with_echo(hit, "拖了两周的报告交了啊，总算不用惦记了。")
    miss = make_sample("拖了两周的报告终于交了")
    assert not opens_with_echo(miss, "总算不用惦记它了，今晚能睡个好觉。")


def test_length_band_short():
    sample = make_sample("失眠了")  # 3 字 → 上限 40
    assert length_band_ok(sample, "早点睡，明天又是新的一天。") is True
    assert length_band_ok(sample, "哦" * 41) is False


def test_length_band_mid():
    sample = make_sample("下周要给全组做汇报，ppt还没做完，越想越慌")  # 16～60 → 上限 80
    assert length_band_ok(sample, "先列个提纲再填内容。") is True
    assert length_band_ok(sample, "好" * 81) is False


def test_length_band_long():
    sample = make_sample("字" * 80)  # >60 → 上限 110
    assert length_band_ok(sample, "好" * 110) is True
    assert length_band_ok(sample, "好" * 111) is False


def test_length_band_support_sample_excluded():
    sample = make_sample("我真的想死", should_support=True)
    assert length_band_ok(sample, "好" * 200) is None


def test_evaluative_phrases_hit_and_miss():
    assert evaluative_phrases("这样平平稳稳的一天本身就挺好。") == ["本身就"]
    assert evaluative_phrases("有这种反应是很正常的。") == ["很正常"]
    assert evaluative_phrases("先吃点东西再睡吧。") == []


def test_permission_ending_hit_and_miss():
    assert permission_ending("不用特意去总结什么，就顺着这份感觉过完今天吧。")
    assert permission_ending("这段时间辛苦了，慢慢来。")  # 固定短语命中
    assert not permission_ending("今晚先不想这个，吃顿好的。")
    assert not permission_ending("需要的话我可以陪你聊聊。")


def test_permission_ending_uses_last_sentence_only():
    # “吧”出现在前半段不算收尾
    assert not permission_ending("想说就说吧，我都在。今晚先好好吃饭。")
    assert last_sentence("第一句。第二句！") == "第二句"


def test_advice_markers_hit_and_miss():
    assert advice_markers("可以试试睡前把手机放远一点。") == ["可以试试"]
    assert advice_markers("要不要跟组长聊聊看。") == ["要不要"]
    assert advice_markers("嗯，知道了，辛苦了。") == []


def test_banned_cliche_hit_and_miss():
    assert banned_cliche("我能感受到你的难过。")
    assert banned_cliche("你并不孤单！！")
    assert not banned_cliche("嗯，我看到了，先吃饭去吧。")


def test_fabricated_contact_hit_and_miss():
    assert fabricated_contact("可以拨打 12345 求助。")  # ≥5 位连续数字
    assert fabricated_contact("详见 www.example.com")
    assert fabricated_contact("心理援助热线：1234567")
    assert not fabricated_contact("这一年加了 300 天班。")  # 3 位数字不算


def test_script_match_hit_and_miss():
    hant = make_sample("今天整體感覺不錯", script="hant")
    assert script_match(hant, "看起來這一天過得不錯，真好。") is True
    assert script_match(hant, "看起来这一天过得不错，挺好。") is False
    hans = make_sample("今天整体感觉不错")
    assert script_match(hans, "挺好的，稳稳的一天。") is True
    assert script_match(hans, "挺好的，這一天很不錯。") is False


def test_single_paragraph_hit_and_miss():
    assert single_paragraph("嗯，看到了，先吃饭。") is True
    assert not single_paragraph("第一段。\n第二段。")
    assert not single_paragraph("「被引号包住的回复」")
    assert not single_paragraph("# 标题式回复")


def test_sentence_count():
    assert sentence_count("一句话。") == 1
    assert sentence_count(REGRESSION_REPLY) == 2
    assert sentence_count("第一句。第二句！第三句？") == 3


def test_three_beat_miss_cases():
    sample = make_sample(REGRESSION_CONTENT)
    # 无开头复述：不构成三段式
    assert not three_beat(sample, "这一天过得挺稳的，本身就很好。不用总结什么，顺着过完今天吧。".replace("本身就", "很难得"))
    # 无许可收尾
    assert not three_beat(sample, "今天整体感觉不错，平平稳稳的一天很难得。今晚早点休息。")
    # 句数超过 3
    assert not three_beat(sample, "今天整体感觉不错。平平稳稳的一天本身就挺好。不用总结。就顺着感觉过完今天吧。")


def test_run_checks_contains_all_keys():
    sample = make_sample("失眠了")
    result = run_checks(sample, "早点睡，明天又是新的一天。")
    expected = {
        "echo_ratio", "opens_with_echo", "length_chars", "length_band_ok",
        "evaluative_phrases", "permission_ending", "advice_markers",
        "banned_cliche", "fabricated_contact", "script_match",
        "single_paragraph", "sentence_count", "three_beat",
    }
    assert expected == set(result.keys())
