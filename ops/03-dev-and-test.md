# 03 开发与测试

## 本地依赖

- 后端：Java 17、Maven、MySQL。
- 前端：Node.js、npm、Vite、Vue3。
- 数据库：优先使用 `sql/xinyu.sql` 初始化本地库。

## 后端常用命令

```powershell
cd xinyu-backend
mvn test
mvn spring-boot:run
```

启动前可按本地环境设置：

```powershell
$env:DB_PASSWORD="你的本地 MySQL 密码"
```

如果 Maven Wrapper 在本机环境异常，优先用本地 `mvn` 验证。

## 前端常用命令

```powershell
cd xinyu-vue
npm install
npm run dev
npm run build
```

如需检查风格：

```powershell
npm run lint
```

## 手动自测主链路

- 注册、登录、刷新页面后登录态仍可用。
- 新增心情记录，确认列表、详情、AI 回复。
- 修改或删除心情记录，确认权限和页面刷新。
- 发布社区帖子，查看详情、评论、点赞、收藏。
- 管理员进入后台，检查用户、帖子、评论、话题、AI 记录。
- AI API 失败时，主业务不能被完全阻断，要有兜底或错误提示。

## 注意事项

- 不在文档或代码中写真实密码、密钥、服务器地址。
- 不直接执行清库、删表、批量删除文件等命令。
- 测试结论要写明“已执行什么命令”和“没有执行什么”。
