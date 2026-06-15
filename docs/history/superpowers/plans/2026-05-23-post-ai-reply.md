# 帖子 AI 回复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让匿名社区帖子在发布与修改时自动获得「心屿」AI 陪伴回复，复用现有 `AiReplyService` / `ai_reply` 表，与心情记录 AI 回复套路对称。

**Architecture:** 后端在 `community_post` 表加 `ai_reply` 字段（denormalize 最新一次成功回复），每次调用同时通过 `ai_reply` 表记录完整日志（`target_type=POST`）。AI 调用同步进行，失败回兜底文案不阻断用户。前端在帖子详情页新增一个独立 `PostAiReply` 卡片，列表页不展示预览。

**Tech Stack:** Spring Boot · MyBatis-Plus · DeepSeek API · Vue 3 · Pinia

**Verification adaptation:** 本项目没有单元测试框架。每个任务的验证使用 `mvn compile`（后端）/ `npm run lint`（前端）/ 浏览器手动 smoke test，并明确给出预期结果。所有 git add 都列具体文件路径，**不要使用 `git add -A`**，避免误把用户其它在写的代码（如 admin 模块）扫进同一个 commit。

---

## File Structure

实施时会改动的文件：

```
Backend
─────────────────────────────────────
xinyu-backend/src/main/java/com/xinyu/entity/CommunityPost.java
  → 加 aiReply 字段 + getter/setter

xinyu-backend/src/main/java/com/xinyu/service/AiReplyLogService.java
  → 加常量 TARGET_TYPE_POST = "POST"

xinyu-backend/src/main/java/com/xinyu/service/AiReplyService.java
  → 加 generateReplyForPost / buildPostPromptText / buildPostMessages

xinyu-backend/src/main/java/com/xinyu/service/CommunityPostService.java
  → add() 与 update() 接入 AI；注入 TopicMapper

SQL
─────────────────────────────────────
sql/xinyu.sql
  → community_post 建表语句加 ai_reply 列
（线上数据库另跑 ALTER TABLE 见 Task 1）

Frontend
─────────────────────────────────────
xinyu-vue/src/components/community/PostAiReply.vue   (新增)
xinyu-vue/src/views/PostDetail.vue                   (插入 PostAiReply)

Docs
─────────────────────────────────────
docs/project/04-数据库设计说明书.md
docs/project/05-接口设计说明书.md
docs/project/06-详细设计说明书.md
docs/project/08-测试说明书.md
```

---

## Task 1: 数据库 schema 变更

**Files:**
- Modify: `sql/xinyu.sql`

- [ ] **Step 1: 修改 sql/xinyu.sql 的 community_post 建表语句**

在 `content` 字段后插入 `ai_reply` 列。找到原来的：

```sql
  `content` TEXT NOT NULL COMMENT '帖子内容',
  `anonymous_name` VARCHAR(50) NOT NULL DEFAULT '匿名岛民' COMMENT '匿名展示名',
```

改成：

```sql
  `content` TEXT NOT NULL COMMENT '帖子内容',
  `ai_reply` TEXT DEFAULT NULL COMMENT 'AI 心屿回复内容（最新一次成功回复的快照）',
  `anonymous_name` VARCHAR(50) NOT NULL DEFAULT '匿名岛民' COMMENT '匿名展示名',
```

- [ ] **Step 2: 对本地正在运行的 MySQL 执行 ALTER TABLE**

```sql
USE `xinyu`;

ALTER TABLE `community_post`
  ADD COLUMN `ai_reply` TEXT DEFAULT NULL COMMENT 'AI 心屿回复内容（最新一次成功回复的快照）' AFTER `content`;
```

预期：执行成功，`DESC community_post;` 能看到 `ai_reply` 列在 `content` 之后。

- [ ] **Step 3: 验证 schema**

```bash
mysql -u root -p xinyu -e "DESC community_post;" | grep ai_reply
```

预期输出：`ai_reply | text | YES | | NULL |`。

- [ ] **Step 4: 提交**

```bash
git add sql/xinyu.sql
git commit -m "feat(db): add ai_reply column to community_post"
```

---

## Task 2: CommunityPost 实体加 aiReply 字段

**Files:**
- Modify: `xinyu-backend/src/main/java/com/xinyu/entity/CommunityPost.java`

- [ ] **Step 1: 在实体里 status 之前加 aiReply 字段**

找到现有的：

```java
    private String content;

    private String anonymousName;
```

改为：

```java
    private String content;

    private String aiReply;

    private String anonymousName;
```

- [ ] **Step 2: 加 getter 与 setter**

在所有 getter 中（`getContent` 之后），加：

```java
    public String getAiReply() {
        return aiReply;
    }
```

在所有 setter 中（`setContent` 之后），加：

```java
    public void setAiReply(String aiReply) {
        this.aiReply = aiReply;
    }
```

- [ ] **Step 3: 后端编译**

Run: `cd xinyu-backend && mvn -q compile`
Expected: 退出码 0，无编译错误。

- [ ] **Step 4: 提交**

```bash
git add xinyu-backend/src/main/java/com/xinyu/entity/CommunityPost.java
git commit -m "feat(community): add aiReply field on CommunityPost entity"
```

---

## Task 3: AiReplyLogService 加 POST 常量

**Files:**
- Modify: `xinyu-backend/src/main/java/com/xinyu/service/AiReplyLogService.java`

- [ ] **Step 1: 在已有 MOOD_RECORD 常量旁加 POST 常量**

找到现有的：

```java
    public static final String TARGET_TYPE_MOOD_RECORD = "MOOD_RECORD";
```

在它后面加：

```java
    public static final String TARGET_TYPE_POST = "POST";
```

- [ ] **Step 2: 后端编译**

Run: `cd xinyu-backend && mvn -q compile`
Expected: 退出码 0。

- [ ] **Step 3: 提交**

```bash
git add xinyu-backend/src/main/java/com/xinyu/service/AiReplyLogService.java
git commit -m "feat(community): add TARGET_TYPE_POST constant to AiReplyLogService"
```

---

## Task 4: AiReplyService 加帖子版 prompt 与生成方法

**Files:**
- Modify: `xinyu-backend/src/main/java/com/xinyu/service/AiReplyService.java`

- [ ] **Step 1: 加 public generateReplyForPost 方法**

在 `generateReplyResult(String moodType, String content)` 方法**之后**插入：

```java
    /**
     * 为社区帖子生成 AI 回复。和 mood 版的区别：
     * - prompt 是公开社区语境，不是私人陪伴
     * - 长度限制 120 字
     * - 注入话题名以让 AI 知道讨论背景
     */
    public AiReplyResult generateReplyForPost(String topicName, String moodType, String content) {
        String prompt = buildPostPromptText(topicName, moodType, content);

        try {
            String replyContent = doGenerateReplyForPost(topicName, moodType, content);
            return AiReplyResult.success(replyContent, prompt, model);
        } catch (Exception e) {
            e.printStackTrace();
            return AiReplyResult.failure(getDefaultReply(), prompt, model, e.getMessage());
        }
    }
```

- [ ] **Step 2: 加 private doGenerateReplyForPost**

在文件内任何 private 方法附近加：

```java
    private String doGenerateReplyForPost(String topicName, String moodType, String content) {
        String url = baseUrl + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", buildPostMessages(topicName, moodType, content));

        Map<String, Object> thinking = new HashMap<>();
        thinking.put("type", "disabled");
        requestBody.put("thinking", thinking);

        // 社区回复要求 120 字内，给的额度比 mood 小
        requestBody.put("max_tokens", 200);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

        if (response.getBody() == null) {
            return getDefaultReply();
        }

        return parseReply(response.getBody());
    }
```

- [ ] **Step 3: 加 private buildPostMessages**

```java
    private List<Map<String, String>> buildPostMessages(String topicName, String moodType, String content) {
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> system = new HashMap<>();
        system.put("role", "system");
        system.put("content", buildPostSystemPrompt(topicName, moodType));
        messages.add(system);

        Map<String, String> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", "帖子内容：\n" + content);
        messages.add(user);

        return messages;
    }
```

- [ ] **Step 4: 加 private buildPostSystemPrompt 和 buildPostPromptText**

```java
    private String buildPostSystemPrompt(String topicName, String moodType) {
        String safeTopic = topicName == null || topicName.trim().isEmpty() ? "未指定话题" : topicName;
        String safeMood = moodType == null || moodType.trim().isEmpty() ? "未指定心情" : moodType;

        return "你是「心屿」，匿名情绪互助社区里的一位温柔陪伴者。"
                + "有人在话题「" + safeTopic + "」下匿名发了一段情绪为「" + safeMood + "」的内容。"
                + "请用不超过 120 字的中文，对这段帖子做一个温和、克制的回应。"
                + "要求：用「这位朋友」「写下这段话的人」之类的中性指代，"
                + "不预设性别、不评判、不诊断、不给医疗建议；"
                + "像在公开社区里轻轻接住对方一句，不要长篇大论；"
                + "不要逐字引用原话，不要使用列表、表情符号或英文。";
    }

    /**
     * 用于记录到 ai_reply 表的 prompt 字段。汇总 system + user 消息。
     */
    private String buildPostPromptText(String topicName, String moodType, String content) {
        return "[system]\n" + buildPostSystemPrompt(topicName, moodType)
                + "\n\n[user]\n帖子内容：\n" + content;
    }
```

- [ ] **Step 5: 后端编译**

Run: `cd xinyu-backend && mvn -q compile`
Expected: 退出码 0。

- [ ] **Step 6: 提交**

```bash
git add xinyu-backend/src/main/java/com/xinyu/service/AiReplyService.java
git commit -m "feat(community): add generateReplyForPost with community-tone prompt"
```

---

## Task 5: CommunityPostService.add 接入 AI

**Files:**
- Modify: `xinyu-backend/src/main/java/com/xinyu/service/CommunityPostService.java`

- [ ] **Step 1: 注入依赖**

在 class 顶部已有 `@Autowired` 注入的下方追加：

```java
    @Autowired
    private com.xinyu.mapper.TopicMapper topicMapper;

    @Autowired
    private AiReplyService aiReplyService;

    @Autowired
    private AiReplyLogService aiReplyLogService;
```

如果 `TopicMapper` 已通过 import 导入，则去掉全限定名；保险起见用全限定写法。

- [ ] **Step 2: 改写 add 方法**

把现有的 `add(CommunityPostAddDTO dto, Long userId)` 方法整体替换为：

```java
    public Boolean add(CommunityPostAddDTO dto, Long userId){
        CommunityPost post = new CommunityPost();

        post.setUserId(userId);
        post.setTopicId(dto.getTopicId());
        post.setMoodType(dto.getMoodType().trim());
        post.setContent(dto.getContent().trim());
        post.setAnonymousName("匿名岛民");
        post.setReplyCount(0);
        post.setLikeCount(0);
        post.setStatus(1);

        int rows = communityPostMapper.insert(post);
        if (rows <= 0) {
            return false;
        }

        // 先 insert 拿到 post.id，再调 AI 回写。AI 失败不阻断发帖。
        attachAiReply(post);

        return true;
    }
```

- [ ] **Step 3: 在 service 末尾加 private attachAiReply**

```java
    /**
     * 为指定帖子生成 AI 回复并回写到 post.aiReply，
     * 同时通过 AiReplyLogService 写一行日志。
     * 失败时使用兜底文案，不抛异常，不阻断主流程。
     */
    private void attachAiReply(CommunityPost post) {
        String topicName = null;
        com.xinyu.entity.Topic topic = topicMapper.selectById(post.getTopicId());
        if (topic != null) {
            topicName = topic.getName();
        }

        com.xinyu.dto.AiReplyResult result = aiReplyService.generateReplyForPost(
                topicName, post.getMoodType(), post.getContent());

        post.setAiReply(result.getReplyContent());
        communityPostMapper.updateById(post);

        aiReplyLogService.save(
                post.getUserId(),
                AiReplyLogService.TARGET_TYPE_POST,
                post.getId(),
                result
        );
    }
```

⚠️ 如果 `AiReplyLogService.save` 的签名与上面不同，先到 `AiReplyLogService.java` 看一下实际签名（mood 模块怎么调它），按实际签名调整这一步。预期它接收 `userId, targetType, targetId, AiReplyResult` 这些信息，无返回值或返回 boolean。

- [ ] **Step 4: 后端编译**

Run: `cd xinyu-backend && mvn -q compile`
Expected: 退出码 0。

如果编译失败提示 `aiReplyLogService.save` 签名不对：参考 `MoodRecordService` 里调它的写法把 attachAiReply 内部调用调整一致。

- [ ] **Step 5: 提交**

```bash
git add xinyu-backend/src/main/java/com/xinyu/service/CommunityPostService.java
git commit -m "feat(community): generate AI reply when creating a post"
```

---

## Task 6: CommunityPostService.update 接入 AI

**Files:**
- Modify: `xinyu-backend/src/main/java/com/xinyu/service/CommunityPostService.java`

- [ ] **Step 1: 改 update 方法的尾部**

找到现有的 update 方法尾部：

```java
        post.setTopicId(dto.getTopicId());
        post.setMoodType(dto.getMoodType().trim());
        post.setContent(dto.getContent().trim());

        return communityPostMapper.updateById(post) > 0;
    }
```

改为：

```java
        post.setTopicId(dto.getTopicId());
        post.setMoodType(dto.getMoodType().trim());
        post.setContent(dto.getContent().trim());

        int rows = communityPostMapper.updateById(post);
        if (rows <= 0) {
            return false;
        }

        // 内容已落库，AI 回复同步重新生成
        attachAiReply(post);

        return true;
    }
```

- [ ] **Step 2: 后端编译**

Run: `cd xinyu-backend && mvn -q compile`
Expected: 退出码 0。

- [ ] **Step 3: 后端启动 smoke test**

启动后端，用 Apifox 或 curl 发一个新帖：

```bash
curl -X POST http://localhost:8080/community/post/add \
  -H "Content-Type: application/json" \
  -H "token: <你的有效 token>" \
  -d '{"topicId":1,"moodType":"焦虑","content":"测试 AI 回复"}'
```

然后查详情：

```bash
curl "http://localhost:8080/community/post/detail?id=<刚发布的 post id>" \
  -H "token: <token>"
```

预期：返回的 post 对象含 `aiReply` 字段，且不为 `null`/空字符串。

数据库验证：

```sql
SELECT id, content, LEFT(ai_reply, 60) FROM community_post ORDER BY id DESC LIMIT 1;
SELECT target_type, target_id, status, LEFT(reply_content, 60) FROM ai_reply WHERE target_type='POST' ORDER BY id DESC LIMIT 1;
```

预期：两条都能查到对应记录。

- [ ] **Step 4: 提交**

```bash
git add xinyu-backend/src/main/java/com/xinyu/service/CommunityPostService.java
git commit -m "feat(community): regenerate AI reply when updating a post"
```

---

## Task 7: 前端 PostAiReply 组件

**Files:**
- Create: `xinyu-vue/src/components/community/PostAiReply.vue`

- [ ] **Step 1: 创建组件**

完整文件内容：

```vue
<script setup>
// 帖子的「心屿」AI 陪伴回复卡片。
// 仅在 reply 非空时使用；老帖 aiReply 为 null 时父级条件渲染不挂载本组件。
defineProps({
  reply: { type: String, required: true }
})
</script>

<template>
  <section class="ai-card">
    <header class="ai-card__head">
      <span class="ai-card__avatar" aria-hidden="true">心</span>
      <div class="ai-card__title">
        <p class="ai-card__name">心屿 · AI 陪伴</p>
        <p class="ai-card__hint">这条回复由 AI 自动生成，不代表平台立场</p>
      </div>
    </header>
    <div class="ai-card__body">
      <p class="ai-card__text">{{ reply }}</p>
    </div>
  </section>
</template>

<style scoped>
.ai-card {
  position: relative;
  padding: 18px 20px 18px 26px;
  background: linear-gradient(135deg, var(--accent-soft), var(--bg-soft));
  border: 1px solid #f1cdc2;
  border-radius: var(--radius-3);
  overflow: hidden;
}

.ai-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 14%;
  bottom: 14%;
  width: 3px;
  border-radius: var(--radius-pill);
  background: var(--accent);
}

.ai-card__head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.ai-card__avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent), var(--brand));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: var(--fs-md);
  flex-shrink: 0;
}

.ai-card__title {
  min-width: 0;
}

.ai-card__name {
  margin: 0;
  font-size: var(--fs-md);
  font-weight: 700;
  color: #a45a4a;
  letter-spacing: 1px;
}

.ai-card__hint {
  margin: 2px 0 0;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.ai-card__body {
  padding: 12px 14px;
  background: var(--bg-card);
  border-radius: var(--radius-2);
  border: 1px solid var(--line);
}

.ai-card__text {
  margin: 0;
  color: var(--ink-1);
  line-height: 1.85;
  font-size: var(--fs-md);
  white-space: pre-wrap;
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add xinyu-vue/src/components/community/PostAiReply.vue
git commit -m "feat(community): add PostAiReply card component"
```

---

## Task 8: PostDetail 接入 PostAiReply

**Files:**
- Modify: `xinyu-vue/src/views/PostDetail.vue`

- [ ] **Step 1: 引入新组件**

在 `<script setup>` 顶部 import 块加：

```javascript
import PostAiReply from '../components/community/PostAiReply.vue'
```

- [ ] **Step 2: 在模板里插入卡片**

找到帖子卡片渲染处：

```vue
        <PostCard
          :post="currentPost"
          :topics="topics"
          :login-user-id="userStore.loginUser?.id"
          variant="detail"
          @toggle-like="handleLike"
          @toggle-favorite="handleFavorite"
          @edit="editOpen = true"
          @delete="handleDeletePost"
        />

        <PostReplyForm
```

在 `</PostCard>` 与 `<PostReplyForm>` 之间插入：

```vue
        <PostAiReply v-if="currentPost.aiReply" :reply="currentPost.aiReply" />

```

- [ ] **Step 3: 前端 lint**

Run: `cd xinyu-vue && npm run lint`
Expected: `Found 0 warnings and 0 errors.`

- [ ] **Step 4: 浏览器 smoke test**

启动 dev server（如未在跑）：

```bash
cd xinyu-vue && npm run dev
```

- 登录后到社区，**发一条新帖**，进入帖子详情，**预期**看到帖子卡片下方新出现一张奶油色的「心屿 · AI 陪伴」卡片，内容由 DeepSeek 生成。
- **打开任意一条老帖**（数据库里 `ai_reply` 为 NULL 的），预期看不到 AI 卡片。
- **编辑刚发的新帖**，保存后回到详情，预期 AI 卡片内容已刷新成新的一段。

- [ ] **Step 5: 提交**

```bash
git add xinyu-vue/src/views/PostDetail.vue
git commit -m "feat(community): render PostAiReply card on post detail page"
```

---

## Task 9: 文档同步

**Files:**
- Modify: `docs/project/04-数据库设计说明书.md`
- Modify: `docs/project/05-接口设计说明书.md`
- Modify: `docs/project/06-详细设计说明书.md`
- Modify: `docs/project/08-测试说明书.md`

- [ ] **Step 1: 04-数据库设计 加 community_post.ai_reply 字段说明**

定位到 `community_post` 表字段表，在 `content` 字段下方插入一行：

```
| ai_reply | TEXT | 是 | NULL | AI 心屿回复内容（最新一次成功回复的快照，老帖为 NULL） |
```

（实际格式请按文档当前表格列对齐。）

- [ ] **Step 2: 05-接口设计 在帖子接口处加 aiReply 字段说明**

在 10.1 发布帖子、10.4 帖子详情、10.5 修改帖子的「说明」段补一句：

> 响应或后续详情查询中，返回的 post 对象包含 `aiReply` 字段，即「心屿」AI 自动生成的陪伴回复内容；老帖（创建于此功能上线前）该字段可能为 `null`。

- [ ] **Step 3: 06-详细设计 10.1 加 AI 回写步骤**

在「## 10.1 发布帖子」的 10.1.1 处理流程文本块里，把：

```text
-> 保存帖子，匿名昵称默认「匿名岛民」，status 置 1
-> 返回发布成功
```

改成：

```text
-> 保存帖子，匿名昵称默认「匿名岛民」，status 置 1
-> 根据话题名 + 心情 + 内容调用 AiReplyService.generateReplyForPost
-> 将 AI 回复回写到 community_post.ai_reply，并向 ai_reply 表写一行 target_type=POST 的日志
-> AI 调用失败时使用默认陪伴文案兜底，不阻断发帖
-> 返回发布成功
```

并在「## 10.5 收藏帖子」之后追加一个新小节：

```markdown
## 10.6 帖子 AI 回复

### 10.6.1 触发时机

发帖（10.1）和修改帖子（见 5.x 修改帖子流程）时同步生成；老帖不回填。

### 10.6.2 数据落点

1. 最新一次成功回复（或失败兜底文案）冗余到 `community_post.ai_reply`。
2. 每次调用（无论成功失败）都向 `ai_reply` 表写一行，target_type 固定为 `POST`，target_id 为帖子 id。

### 10.6.3 失败处理

AiReplyService.generateReplyForPost 内部捕获异常返回默认陪伴文案，外层只读 `replyContent` 写库，不抛出。AI 调用不进事务。
```

- [ ] **Step 4: 08-测试说明 加帖子 AI 回复用例**

在「## 11. 社区模块测试」末尾追加：

```markdown
### 11.22 发帖后 AI 回复

| 项目 | 内容 |
| --- | --- |
| 测试目标 | 验证新发布的帖子在详情页能看到 AI 回复卡片 |
| 操作步骤 | 登录后发一条新帖，进入帖子详情 |
| 预期结果 | 帖子下方显示「心屿 · AI 陪伴」卡片，内容非空 |

### 11.23 修改帖子重新生成 AI 回复

| 项目 | 内容 |
| --- | --- |
| 测试目标 | 验证修改帖子后 AI 回复刷新 |
| 操作步骤 | 编辑自己刚发的帖子内容并保存 |
| 预期结果 | 详情页 AI 回复卡片更新为新的一段内容 |

### 11.24 AI 调用失败兜底

| 项目 | 内容 |
| --- | --- |
| 测试目标 | 验证 AI 失败时帖子仍可发布并显示兜底文案 |
| 操作步骤 | 在 application.properties 临时把 DEEPSEEK_API_KEY 改成错的，重启后端，发一条新帖 |
| 预期结果 | 发帖仍然成功；详情页 AI 卡片显示默认陪伴文案；ai_reply 表对应记录 status=0、error_message 非空 |

### 11.25 老帖兼容

| 项目 | 内容 |
| --- | --- |
| 测试目标 | 验证此功能上线前的老帖不显示 AI 卡片 |
| 操作步骤 | 打开数据库中 ai_reply 为 NULL 的某条帖子详情 |
| 预期结果 | 帖子详情不渲染 AI 卡片，其他展示正常 |
```

- [ ] **Step 5: 提交**

```bash
git add docs/project/04-数据库设计说明书.md docs/project/05-接口设计说明书.md docs/project/06-详细设计说明书.md docs/project/08-测试说明书.md
git commit -m "docs: sync docs with post AI reply feature"
```

---

## Self-Review

(Done — verifies spec coverage against tasks)

| Spec 要求 | 对应任务 |
| --- | --- |
| §3.1 community_post 加 ai_reply 列 | Task 1, Task 9 step 1 |
| §3.2 实体加 aiReply 字段 | Task 2 |
| §3.3 AiReplyLogService TARGET_TYPE_POST 常量 | Task 3 |
| §4 AiReplyService 加 generateReplyForPost + prompt | Task 4 |
| §5.1 add 流程接入 AI（先 insert 再回写） | Task 5 |
| §5.2 update 流程重新生成 | Task 6 |
| §5.3 接口字段（aiReply 自动随 post 返回） | Task 5 / Task 6 自然带出，Task 9 step 2 加说明 |
| §6.1 PostDetail 插入 PostAiReply | Task 8 |
| §6.2 PostAiReply 新组件 | Task 7 |
| §6.3 store 不需要新 action / 列表不展示预览 | 默认行为，无需任务 |
| §7 失败 / 老帖 / 软删 / 慢调用 | Task 5 attachAiReply 失败不抛、Task 8 step 4 老帖验证 |
| §8 顺带要做的事（sql / 04 / 05 / 06 / 08 文档） | Task 1, Task 9 |

类型一致性：贯穿任务的关键名字 —— `aiReply`（实体）、`ai_reply`（列）、`generateReplyForPost`、`attachAiReply`、`PostAiReply`，前后一致。

无占位符。
