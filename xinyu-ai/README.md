# 心屿 Python 单次回应服务 · Task 1

内部服务只负责认证、输入校验、有限风险分支与一次模型生成；Java 管用户身份、来源版本、任务状态和结果持久化。此目录独立运行，不引用 agent-lab，不连接业务数据库。当前尚未接入 Java。

## 安装与运行

需要 Python 3.12 与 uv。`uv.lock` 锁定实际安装版本；后续安装不重新解析升级：

```sh
uv sync --directory xinyu-ai --locked
```

按 `.env.example` 将以下五项配置注入进程环境：`XY_AI_INTERNAL_TOKEN`、`XY_MODEL_BASE_URL`、`XY_MODEL_API_KEY`、`XY_MODEL_NAME`、`XY_MODEL_THINKING_MODE`。不自动查找／加载 `.env`，也不读取 Java 或 lab 的真实配置。内部 token 与供应商 key 必须为非空 ASCII；启动错误不回显变量值。

```sh
uv run --directory xinyu-ai --locked uvicorn app.main:app_from_env --factory \
  --host 127.0.0.1 --port 8001 --no-access-log --h11-max-incomplete-event-size 8192
```

保持回环／私网可达，不向公网暴露此端口。健康检查 `GET /health` 只返回 `{"ready":true}`，表示配置已通过启动校验，不代表供应商在线；API 文档端点关闭。

`XY_MODEL_BASE_URL` 为供应商 API 前缀，实际调用追加 `/chat/completions`。当前适配发送 `max_tokens=300`、`stream=false` 和 `thinking.type=XY_MODEL_THINKING_MODE`，模式仅支持 `disabled`／`enabled`，样例关闭 thinking。供应商不支持该协议时应显式更换适配并补测试，不能默默忽略参数。禁止自动重试／自动跳转，不使用环境代理转发请求。

## 请求与结果

`POST /internal/v1/mood-replies`，认证头 `Authorization: Bearer <internal token>`。请求体示例：

```json
{
  "request_id": "request-1",
  "job_id": "123",
  "attempt": 1,
  "source_version": "1",
  "mood_type": "平静",
  "content": "今天散步后平静了一些。",
  "budget_ms": 40000
}
```

ID／版本必须是正数的十进制字符串且在 Java signed long 范围内；attempt 为 1—3 的整数，budget_ms 为 1—40000。心情类型与现有页面六种枚举一致。content 非空，最多 500 个 UTF-16 单位；250 个 emoji 合法，251 个越界；孤立代理字符拒绝。未知字段、错误类型和非法 JSON 均拒绝。关联字段仅回传，不代表服务替 Java 校验了来源归属。

200 响应回传 request_id／job_id／attempt／source_version，并包含 reply_content、result_kind、model_called、template_version 和 model_name。回复去首尾空白后非空、最多 2000 个 UTF-16 单位。MODEL 必须 model_called=true 且模型名非空；SUPPORT 为 false／null。模板版本固定 `mood-reply-v1`。model_called=true 只说明已发起本地供应商请求，不证明供应商执行或计费。

错误体只有 `request_id`（未校验时为 null）与 `code`，不附日记、prompt、上游正文或异常文本：

| HTTP | code | 含义 |
| --- | --- | --- |
| 400 | invalid_request | 结构、字段、Unicode 或大小不合法 |
| 401 | unauthorized | 无／错误／重复认证头 |
| 502 | provider_error | 供应商非 200、连接错误等 |
| 502 | invalid_model_response | 非 JSON、非法结构、空白／超长回复、非正常结束 |
| 504 | provider_timeout | 本地阶段等待或总 deadline 到期 |
| 503 | overloaded | 已有一个生成在处理，不额外排队 |

请求头合计上限 8 KiB，正文上限 16 KiB（包括流式接收的累计字节）；供应商响应最多读取 64 KiB。应用限制不能替代网关／HTTP 服务器的接入层限制。错误日志只记录安全追踪 ID 与分类，不输出原文或 traceback。

请求处理总时限默认 40 秒；验证请求后按 budget_ms 缩短，同样从请求开始计时。供应商连接最多 2 秒、单次读等待最多 35 秒，均受剩余总时限约束。`asyncio.timeout_at` 包住可取消的异步网络 I/O，超时退出上下文关闭连接；不承诺远端停止计算／计费。单进程生成并发为 1，不提供持久化队列，Java 的数据库 worker 在 Task 3 接入。

## 有限风险回应

风险规则在普通模型之前执行。命中时直接生成内置 SUPPORT，提示联系可信任的人、寻求陪伴，并在有立即危险时联系当地急救或警方；不推测地区，不生成具体号码。回应仍经过非空和 2000 UTF-16 单位校验，结果为 model_called=false、model_name=null。

此分支不依赖外部资源文件、地区选择或核验日期，也没有定期维护门禁。旧环境变量 `XY_HELP_RESOURCES_FILE` 不再读取，可从部署环境移除；遗留值即使指向无效路径，也不影响应用启动和普通模型调用。内部认证及模型必需配置仍需合法。

当前仅有限中英文第一人称表达规则，可漏判或误判，不代表完整危机识别能力。普通模型的 SYSTEM_PROMPT 与固定 SUPPORT 文案分别维护：更改 system 提示词不会自动改动固定分支。未来确需提供地区联系方式时，另行明确适用范围与信息来源，不自动引入 LLM 定位、IP 查询或启动门禁。

## 验证与证据范围

在仓库根目录运行：

```sh
uv run --directory xinyu-ai --locked pytest -q
```

测试替换外部供应商，保留真实认证、风险、校验与 HTTP 适配；另以真实回环 TCP 验证挂起响应在总 deadline 后关闭连接。运行测试需要允许绑定 127.0.0.1 临时端口。协议测试及有限样例不证明真实模型质量、危机覆盖率或生产可用性。

本地 RED/GREEN、依赖版本与后续接续见 `AI_WORKLOG/2026-11-07-ai-reply-1a-progress.md`。尚未执行真实供应商采样、Java 联调、数据库验证或部署。
