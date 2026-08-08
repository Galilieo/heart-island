# AI 回复可选化 v1

## 功能目标

写心情和发/编社区帖子时，用户可以选择是否要 AI 回复；选择不要则不生成回复、不写 AI 调用日志，界面不出现 AI 回复卡片。

## 用户场景

- 只想纯记录、不想被 AI 打扰的用户。
- 节省 AI 调用额度 / 减少无效调用日志。

## 业务流程

1. 心情新增/编辑、帖子新增/编辑表单出现"让心屿回应我"开关，默认开。
2. 提交时携带 `needAiReply`。
3. 后端收到 `false`：跳过 AI 生成与日志写入；若是编辑场景，同时把已有 `ai_reply` 清空。
4. 后端收到 `true` 或未传（旧客户端）：行为与现状一致。

## 影响范围

- 心情记录主链路：是（新增/编辑）。
- 社区内容展示：是（无回复的帖子/心情不显示 AI 卡）。
- AI 调用记录：是（关闭时不写日志，属预期）。
- 登录态/权限：无影响。后台管理：无新入口。

## 涉及文件

- 后端：`dto/MoodRecordAddRequest.java`、`dto/MoodRecordUpdateRequest.java`、`dto/CommunityPostAddDTO.java`、`dto/CommunityPostUpdateDTO.java`、`service/MoodRecordService.java`、`service/CommunityPostService.java`
- 前端：`api/mood.js`、`stores/mood.js`、`views/Home.vue`、`components/mood/MoodForm.vue`、`components/mood/MoodDetailDialog.vue`、`components/community/PostPublishForm.vue`、`components/community/PostEditDialog.vue`
- 数据库：无变更（`ai_reply` 字段已存在且可空）。

## API 设计

四个现有接口的请求体新增可选字段：

```
needAiReply: Boolean，缺省 true（向后兼容）
```

- `POST /mood/add`、`PUT /mood/update/{id}`
- `POST /community/post/add`、`POST /community/post/update`

## 权限与安全

- 只能控制自己内容的回复生成，无越权面。

## 异常处理

- 字段缺省按 true 处理，老数据/老客户端行为不变。
- AI 服务不可用的现有兜底逻辑不变。

## 验收标准

- 关闭开关提交心情 → 记录保存成功，`mood_record.ai_reply` 为 NULL，`ai_reply_log` 无新行，前端该条无 AI 回复区。
- 编辑已有（带回复的）心情并关闭开关 → `ai_reply` 被清空。
- 开启开关提交 → 与现状一致（生成回复 + 写日志）。
- 不传 `needAiReply` 的旧请求 → 与现状一致。
- 发帖/编辑帖子同上。

## 自测清单

- 前端实际操作：开/关开关各提交一次心情、一次帖子。
- 数据库验证：`ai_reply` 字段与 `ai_reply_log` 行数。
- `npm run build`、后端 `mvn compile` 通过。

## 后续迭代

- 全局"默认不要 AI 回复"的个人偏好设置。
- 对已有无回复内容补发 AI 回复的入口。
