"""合成数据生成：固定种子 20261117，输出可直接灌入隔离库的 SQL 文件。

用途与口径：
- B1/B2/B3/C 段的负载内容来自 CONTENT_POOL（非危机、一般长度档），加唯一序号后缀；
- B5 段的存量数据（心情历史两档 1k/20k、社区帖子与点赞收藏）由本脚本按种子生成
  INSERT 语句，经 mysql CLI 灌入隔离库，保证两档之间只有行数差异、分布相同。

生成规则（写死在代码里，复跑即复现）：
- mood_type 均匀取自 13 类；content 从 CONTENT_POOL[i % len] 取并附 "（序号 n）"；
- create_time 从结束时刻往前按 Deterministic Random (seed) 均匀撒在 N 天内，时间部分随机；
- 社区帖子：每帖属于 topic 1..3，user_id 轮转；like_count/favorite 冗余字段与实际行数一致
  （由插入 post_like/favorite 行后 UPDATE 同步，保持与真实链路相同的冗余状态）；
- 查询用户（B5 视角用户）固定注册为 eval_sql_u1，全部历史数据归属该用户（心情）
  或由多用户分摊（帖子）。

用法（经 mysql CLI 灌库）：
  uv run --directory xinyu-ai --locked python ../perf/gen_data.py --mood 20000 --out perf/results/data/mood20k.sql
  mysql ... -D <隔离库> < perf/results/data/mood20k.sql
"""

import argparse
import datetime as dt
import os
import random

SEED = 20261117

MOOD_TYPES = ["开心", "满足", "期待", "感动", "平静", "焦虑", "难过", "生气", "委屈", "孤独", "迷茫", "疲惫", "其他"]

# 非危机、口语化、一般长度档（>15 字）；避开危机正则词（想死/自杀 等）。
CONTENT_POOL = [
    "今天开了一天的会，下午的方案终于过了，晚上想早点休息",
    "通勤路上把播客听完了，地铁居然有座，心情不错",
    "午饭和同事去吃了新开的面馆，分量足味道也好",
    "下午改 bug 改到眼花，不过最后定位到问题那一下挺爽",
    "晚上跑了两公里，比上周多坚持了五百米",
    "帮朋友搬家，累是累，晚上一起吃了火锅很开心",
    "今天下雨没带伞，淋了一段路，回家喝了热汤",
    "把攒了一周的衣物洗完了，晒在阳台上看着很舒服",
    "领导临时加了个任务，加班到八点半才走",
    "早上差点迟到，一路小跑赶上了打卡",
    "中午晒了会儿太阳，感觉整个人都松下来了",
    "晚上和老家的爸妈视频，聊了快一个小时",
    "下午喝了杯新豆子的手冲，香气很足",
    "整理了书桌，把不要的纸都清掉了，清爽多了",
    "楼下便利店的关东煮买一送一，捡到便宜了",
    "练了半小时琴，比上周流畅一些了",
    "今天被客户夸了两句，一周的疲惫少了一半",
    "晚上地铁上看到有人给老人让座，心里挺暖",
    "做了个很奇怪的梦，醒来想了半天",
    "尝试了新的午休方式，趴着睡果然脖子疼",
]

ANON_NAMES = ["匿名岛民", "路过的风", "柠檬汽水", "晚风信箱", "一颗青椒", "海绵月亮", "北岛旅人", "小满"]


def esc(text):
    return text.replace("\\", "\\\\").replace("'", "\\'")


def gen_mood_sql(user_id, count, end_time, days):
    """生成 count 条心情历史 INSERT；时间均匀撒在 days 天内。"""
    rng = random.Random(SEED)
    lines = ["INSERT INTO mood_record (user_id, mood_type, content, ai_reply, status, ai_reply_enabled, create_time, update_time) VALUES"]
    values = []
    for i in range(count):
        mood = MOOD_TYPES[rng.randrange(len(MOOD_TYPES))]
        content = CONTENT_POOL[i % len(CONTENT_POOL)] + f"（序号{i + 1}）"
        offset_seconds = rng.randrange(0, days * 86400)
        ts = end_time - dt.timedelta(seconds=offset_seconds)
        # 少量记录带 AI 回复（约 30%），模拟历史开启过 AI 的真实分布
        ai_reply = "以前的AI回复内容示例。" if rng.random() < 0.3 else "NULL"
        if ai_reply != "NULL":
            ai_reply = f"'{esc(ai_reply)}'"
        enabled = 1 if rng.random() < 0.3 else 0
        values.append(
            f"({user_id}, '{mood}', '{esc(content)}', {ai_reply}, 1, {enabled}, "
            f"'{ts.strftime('%Y-%m-%d %H:%M:%S')}', '{ts.strftime('%Y-%m-%d %H:%M:%S')}')"
        )
    lines.append(",\n".join(values) + ";")
    return "\n".join(lines)


def gen_community_sql(user_ids, topic_ids, post_count, viewer_id, like_ratio=0.5, fav_ratio=0.3):
    """生成帖子、回复、点赞、收藏；冗余计数在插入后 UPDATE 同步。"""
    rng = random.Random(SEED + 1)
    out = []
    posts = []
    for i in range(post_count):
        topic = topic_ids[rng.randrange(len(topic_ids))]
        author = user_ids[rng.randrange(len(user_ids))]
        mood = MOOD_TYPES[rng.randrange(len(MOOD_TYPES))]
        content = CONTENT_POOL[(i * 7) % len(CONTENT_POOL)] + f"（帖 {i + 1}）"
        anon = ANON_NAMES[rng.randrange(len(ANON_NAMES))]
        offset = rng.randrange(0, 30 * 86400)
        ts = dt.datetime(2026, 11, 17, 21, 0, 0) - dt.timedelta(seconds=offset)
        posts.append((topic, author, mood, content, anon, ts))
    out.append("INSERT INTO community_post (topic_id, user_id, mood_type, content, anonymous_name, reply_count, like_count, status, create_time, update_time) VALUES")
    out.append(",\n".join(
        f"({t}, {u}, '{m}', '{esc(c)}', '{a}', 0, 0, 1, '{ts}', '{ts}')"
        for t, u, m, c, a, ts in posts
    ) + ";")
    # 查询用户（viewer）对帖子的点赞/收藏，按比例生成 → listByTopicId 的逐条 LIKE 查询有数据可查
    likes, favs = [], []
    for idx, (t, u, m, c, a, ts) in enumerate(posts, start=1):
        if rng.random() < like_ratio:
            likes.append((idx, viewer_id))
        if rng.random() < fav_ratio:
            favs.append((idx, viewer_id))
    if likes:
        out.append("INSERT INTO post_like (post_id, user_id) VALUES " +
                   ",".join(f"({p}, {u})" for p, u in likes) + ";")
    if favs:
        out.append("INSERT INTO favorite (post_id, user_id) VALUES " +
                   ",".join(f"({p}, {u})" for p, u in favs) + ";")
    out.append("UPDATE community_post p SET like_count = (SELECT COUNT(*) FROM post_like l WHERE l.post_id = p.id), "
               "reply_count = (SELECT COUNT(*) FROM community_reply r WHERE r.post_id = p.id AND r.status = 1);")
    return "\n".join(out)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mood", type=int, default=0, help="心情历史条数")
    parser.add_argument("--posts", type=int, default=0, help="社区帖子条数")
    parser.add_argument("--user-id", type=int, required=True, help="心情数据归属用户")
    parser.add_argument("--viewer-id", type=int, default=None, help="社区查询视角用户")
    parser.add_argument("--author-ids", type=str, default="", help="帖子作者 id 逗号分隔")
    parser.add_argument("--topic-ids", type=str, default="1,2,3")
    parser.add_argument("--days", type=int, default=90)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    end = dt.datetime(2026, 11, 17, 21, 0, 0)
    parts = []
    if args.mood:
        parts.append(gen_mood_sql(args.user_id, args.mood, end, args.days))
    if args.posts:
        authors = [int(x) for x in args.author_ids.split(",") if x] or [args.user_id]
        topics = [int(x) for x in args.topic_ids.split(",") if x]
        parts.append(gen_community_sql(authors, topics, args.posts,
                                       args.viewer_id or args.user_id))
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n\n".join(parts) + "\n")
    print(f"wrote {args.out}: mood={args.mood} posts={args.posts}")


if __name__ == "__main__":
    main()
