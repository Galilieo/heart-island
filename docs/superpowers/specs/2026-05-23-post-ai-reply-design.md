# 帖子 AI 回复设计

**日期**: 2026-05-23
**范围**: 后端 `xinyu-backend` + 前端 `xinyu-vue`
**目标**: 让匿名社区帖子在发布与修改时自动获得「心屿」AI 陪伴回复，与现有心情记录 AI 回复套路对称。

---

## 1. 背景与现状

- 心情记录已有完整的 AI 回复闭环：`mood_record` 表有 `ai_reply` 字段；`ai_reply` 表记录每次调用日志（含 prompt、回复、状态、错误）；新增/修改心情时自动调 DeepSeek。
- `ai_reply` 表的 `target_type` 字段从设计之初就预留了 `MOOD_RECORD`、`POST`、`SUMMARY` 三个取值，目前只用了 `MOOD_RECORD`。
- 匿名社区目前只有用户之间的回复，没有 AI 参与。

本次工作把同一套 AI 能力套用到帖子上，复用现有 `AiReplyService` 与 `AiReplyLogService`。

---

## 2. 关键产品决策

1. **AI 角色**：以「心屿」身份在帖子下作一条公开可见的陪伴回复，所有人都能看到。
2. **触发时机**：发帖时同步生成（用户等待 AI 返回再看到发布成功，与心情记录一致）。
3. **展示位置**：帖子详情页的独立卡片，位于帖子正文下方、用户回复列表上方；列表页不展示。
4. **修改帖子**：重新生成 AI 回复（覆盖旧的）。
5. **存量老帖**：不动，老帖 `ai_reply` 字段保持为 `NULL`，详情页不展示 AI 卡片。
6. **失败处理**：AI 调用失败时写入兜底文案，发帖流程不阻断。

---

## 3. 数据层

### 3.1 schema 变更

`community_post` 增加一列：

```sql
ALTER TABLE community_post
  ADD COLUMN ai_reply TEXT DEFAULT NULL COMMENT 'AI 心屿回复内容' AFTER content;
```

`sql/xinyu.sql` 同步建表语句。

### 3.2 实体

`CommunityPost` 实体增加字段：

```java
private String aiReply;
// getter / setter
```

### 3.3 ai_reply 表

无 schema 变更。`AiReplyLogService` 增加常量：

```java
public static final String TARGET_TYPE_POST = "POST";
```

每次为帖子生成 AI 回复时，向 `ai_reply` 表写一行：

- `target_type` = `POST`
- `target_id` = 帖子 id
- `prompt` / `reply_content` / `model_name` / `status` / `error_message`

---

## 4. AI Service 改造

在 `AiReplyService` 中新增方法：

```java
public AiReplyResult generateReplyForPost(String topicName, String moodType, String content)
```

实现思路：

- 复用 `doGenerateReply` 的 HTTP 流程，仅 prompt 不同。
- 新增私有方法 `buildPostPromptText(topicName, moodType, content)`，构造社区版 prompt。
- 失败时返回默认兜底文案 + 错误信息，与心情版方法一致。

### 4.1 帖子 prompt 设计

与心情版的差异：

- **场景**：帖子是匿名公开发布，AI 回复也公开可见；心情记录是私密写给自己。
- **称呼**：不点名楼主，使用「这位朋友」「写下这段话的人」等中性指代。
- **边界**：不评判、不诊断、不给医疗建议，不引导线下行动。
- **长度**：120 字以内（心情版 300 字以内更克制）。
- **注入信息**：话题名（如「学习压力」）、情绪类型（如「焦虑」）、帖子内容。

示例 system 角色描述：

> 你是「心屿」，一位匿名情绪互助社区里的温柔陪伴者。有人在话题「{topicName}」下匿名分享了情绪为「{moodType}」的内容。请用 120 字以内、克制温和的语气回应他，不评判、不诊断、不给医疗建议。

---

## 5. 后端集成

### 5.1 发布帖子流程

`CommunityPostService.add(dto, userId)` 改为：

1. 构造 `CommunityPost` 实体并 `insert`，拿到 `post.id`。
2. 通过 `topicMapper.selectById(dto.getTopicId())` 拿到 `topicName`。
3. 调 `aiReplyService.generateReplyForPost(topicName, moodType, content)`，得到 `AiReplyResult`。
4. `post.setAiReply(result.getReplyContent())`，`updateById(post)` 回写。
5. 通过 `AiReplyLogService` 写一行 `ai_reply` 日志（target_type=POST, target_id=post.id）。
6. 返回成功。

设计要点：

- **先 insert 再回写**：因为 `ai_reply` 表需要 `target_id`，先 insert 拿到帖子 id 是最简单的做法，与 mood 模块一致。
- **事务边界**：AI 调用不进事务（外部 HTTP 太慢）。即使 AI 失败，post 已经发出去；ai_reply 兜底文案也会写进 post 与日志，不阻断用户。
- **失败处理**：`AiReplyResult` 失败时 `replyContent` 为默认兜底文案，`status` 为失败，`errorMessage` 记入 ai_reply 表，便于排查。

### 5.2 修改帖子流程

`CommunityPostService.update(dto, userId)` 在权限校验之后：

1. 更新 `topicId` / `moodType` / `content` 字段，`updateById(post)`。
2. 通过 `topicMapper.selectById` 拿到当前话题名，调 `generateReplyForPost`，覆盖 `post.ai_reply` 并写新日志。
3. 即使本次 update 实际字段未变，仍然重新生成 AI 回复，与心情记录 update 行为一致；不引入「内容是否改变」的判断。

### 5.3 接口字段

无新接口。`GET /community/post/detail` 与 `GET /community/post/list` 返回的 post 对象天然带上 `aiReply` 字段（实体新增字段后 MyBatis-Plus 自动序列化）。

---

## 6. 前端展示

### 6.1 PostDetail.vue 调整

帖子详情页主区域结构变为：

```
PostCard (variant=detail)
PostAiReply (新增独立卡片)
PostReplyForm
PostReplyList
```

仅在帖子的 `aiReply` 字段非空时渲染 `PostAiReply` 组件；老帖 `aiReply` 为 `null` 时自动隐藏。

### 6.2 PostAiReply.vue（新增）

- 输入：`reply` 字符串（即 `post.aiReply`）。
- 视觉风格参考 MoodCard 内的 AI 回复区：
  - 杏粉底色 + 左侧 accent 竖条。
  - 顶部一行：心屿头像 / 图标 + 「心屿 · AI 陪伴」徽章。
  - 正文：白底盒子，line-height 1.85，font-size 略偏舒展。
- 无任何交互（不点赞、不回复、不重新生成）。
- 失败兜底的默认文案与正常文案视觉一致（不特别区分 status）。

### 6.3 communityStore 与 PostList

- store 不需要新 action；`fetchPostDetail` 拉回的 post 已带 `aiReply` 字段。
- 发帖 / 改帖完成后 store 重新拉详情，AI 回复自动刷新。
- `PostList.vue` 列表卡片不展示 AI 回复预览，避免列表拥挤；与心情记录列表的差异是合理的（心情私密 vs 社区公开）。

---

## 7. 失败与边界处理

| 场景 | 行为 |
| --- | --- |
| DeepSeek 超时或返回错误 | 写入默认兜底文案到 post.ai_reply；日志表记一行失败 status |
| 帖子被软删除（status=0） | ai_reply 字段保留；帖子查不到自然不展示 |
| 老帖（aiReply=null） | 详情页 PostAiReply 不渲染 |
| AI 调用慢（>3s） | 用户在发帖按钮上看到 loading；与 mood 体验一致 |

---

## 8. 顺带要同步的文件

实施时一并处理：

1. `sql/xinyu.sql` —— community_post 建表语句加 ai_reply 列。
2. `docs/04-数据库设计说明书.md` —— 补这一列字段说明。
3. `docs/05-接口设计说明书.md` —— 发帖 / 改帖 / 帖子详情响应中说明含 `aiReply`。
4. `docs/06-详细设计说明书.md` —— 10.1 发布帖子流程加 AI 回写步骤；新增 10.x 帖子 AI 回复设计小节。
5. `docs/08-测试说明书.md` —— 社区模块新增「发帖后详情页可见 AI 回复」「AI 失败时显示兜底文案」等用例。

---

## 9. 不做的事（YAGNI）

- 不做异步生成 / @Async / 前端轮询。
- 不做老帖批量回填脚本。
- 不做 PostList 列表卡 AI 预览。
- 不给 AI 回复加点赞、回复、举报等交互。
- 不做手动「重新生成 AI」按钮。
- 不引入新的中间件、缓存或线程池。

---

## 10. 完成标准

1. 新发布的帖子在详情页能看到一张「心屿 · AI 陪伴」卡片，内容由 DeepSeek 生成。
2. 修改帖子内容后，详情页 AI 卡片刷新为新回复。
3. AI 调用失败时帖子仍能正常发布，卡片显示兜底文案，`ai_reply` 表记录失败原因。
4. 老帖详情页不显示 AI 卡片，行为与重构前一致。
5. ai_reply 表中可以查到 target_type=POST 的日志记录。
6. 接口文档、数据库文档、测试文档同步更新。
