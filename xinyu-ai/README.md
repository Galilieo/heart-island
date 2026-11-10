# 心屿 Python 单次回应服务 · Task 1

内部服务只负责认证、输入校验、有限风险分支与一次模型生成；Java 管用户身份、来源版本、任务状态和结果持久化。此目录独立运行，不引用 agent-lab，不连接业务数据库。Java已接入内部HTTP客户端、单次执行及条件启用的自动worker，事务和三槽调度已用隔离MySQL验证；完整1a发布验收尚待后续任务。

## 安装与运行

需要 Python 3.12 与 uv。`uv.lock` 锁定实际安装版本；后续安装不重新解析升级：

```sh
uv sync --directory xinyu-ai --locked
```

按 `.env.example` 将以下五项配置注入进程环境：`AI_INTERNAL_TOKEN`、`OPENAI_BASE_URL`、`OPENAI_API_KEY`、`OPENAI_MODEL`、`DEEPSEEK_THINKING_MODE`。不自动查找／加载 `.env`，也不读取 Java 或 lab 的真实配置。内部 token 与供应商 key 必须为非空 ASCII；启动错误不回显变量值。

另支持可选配置 `OPENAI_MAX_TOKENS`，缺省为 4096，必须是 1～8192 的整数。8192 是本服务当前预算上限，不是供应商的模型上限；非法值阻止启动。

`OPENAI_*` 沿用 agent-lab 的兼容接口配置名称，实际供应商仍由地址和模型决定；`AI_INTERNAL_TOKEN` 专供 Java→Python 内部认证，`DEEPSEEK_THINKING_MODE` 明确标识供应商扩展参数。不再兼容原 `XY_` 前缀配置。

```sh
uv run --directory xinyu-ai --locked uvicorn app.main:app_from_env --factory \
  --host 127.0.0.1 --port 8001 --no-access-log --h11-max-incomplete-event-size 8192
```

保持回环／私网可达，不向公网暴露此端口。健康检查 `GET /health` 只返回 `{"ready":true}`，表示配置已通过启动校验，不代表供应商在线；API 文档端点关闭。

`OPENAI_BASE_URL` 为供应商 API 前缀，实际调用追加 `/chat/completions`。当前适配发送 `max_tokens=OPENAI_MAX_TOKENS`、`stream=false` 和 `thinking.type=DEEPSEEK_THINKING_MODE`，模式仅支持 `disabled`／`enabled`，样例关闭 thinking。供应商不支持该协议时应显式更换适配并补测试，不能默默忽略参数。禁止自动重试／自动跳转，不使用环境代理转发请求。

原固定 300-token 预算已提高到默认 4096，作为本地初值，尚未通过真实模型校准。启用 thinking 时，预算还要容纳思考开销，不能只按最终短回复估算；可按实际样例调整至 8192，但预算增大不保证完成，也不保证时延或费用。40 秒总 deadline、64 KiB 上游正文上限和 2000 UTF-16 回复上限仍独立生效。`finish_reason=length` 仍判失败，不自动补发或展示截断回复；仅返回 `content`，不回传 `reasoning_content`。参数依据见[官方接口说明](https://api-docs.deepseek.com/api/create-chat-completion/)和[思考模式说明](https://api-docs.deepseek.com/guides/thinking_mode/)。

当前选用 DeepSeek 的 OpenAI 兼容 Chat Completions 协议，沿用 agent-lab 的直接 HTTP 接入方式。环境样例设置 `OPENAI_BASE_URL=https://api.deepseek.com`、`OPENAI_MODEL=deepseek-flash`、`DEEPSEEK_THINKING_MODE=disabled`；实际请求发送到 `https://api.deepseek.com/chat/completions`，密钥仍留空并由进程环境注入，不从 lab 复制真实配置。

2026-11-07 核对[官方调用说明](https://api-docs.deepseek.com/quick_start/agent_integrations/)：`deepseek-flash` 当前由 DeepSeek-V4.1-Flash-1017 提供服务；lab 示例中的旧别名 `deepseek-v4-flash` 仍被兼容路由。该别名不是不可变版本锁，后续采样应记录日期及当时的模型映射。关闭 thinking 的请求格式见[官方思考模式说明](https://api-docs.deepseek.com/guides/thinking_mode/)。此前只有文档与离线协议证据；2026-11-09经用户授权，用两条合成心情走Java17→Python→真实供应商，均通过，单次generate耗时2.305秒／2.582秒。仅验证此次配置下的真实链路，不证明稳定延迟、总体质量或生产验收；也不据此确认别名当前底层版本。

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

ID／版本必须是正数的十进制字符串且在 Java signed long 范围内；attempt 为 1—3 的整数，budget_ms 为 1—40000。心情类型与前端、Java统一为13项：开心、满足、期待、感动、平静、焦虑、难过、生气、委屈、孤独、迷茫、疲惫、其他；目前仍为单选字符串，旧六项继续合法。content 非空，最多 500 个 UTF-16 单位；250 个 emoji 合法，251 个越界；孤立代理字符拒绝。未知字段、错误类型和非法 JSON 均拒绝。关联字段仅回传，不代表服务替 Java 校验了来源归属。

200 响应回传 request_id／job_id／attempt／source_version，并包含 reply_content、result_kind、model_called、template_version 和 model_name。回复去首尾空白后非空、最多 2000 个 UTF-16 单位。MODEL 必须 model_called=true 且模型名非空；SUPPORT 为 false／null。模板版本固定 `mood-reply-v1`。model_called=true 只说明已发起本地供应商请求，不证明供应商执行或计费。

错误体只有 `request_id`（未校验时为 null）与 `code`，不附日记、prompt、上游正文或异常文本：

| HTTP | code | 含义 |
| --- | --- | --- |
| 400 | invalid_request | 结构、字段、Unicode 或大小不合法 |
| 401 | unauthorized | 无／错误／重复认证头 |
| 502 | provider_error | 供应商非 200、连接错误等 |
| 502 | invalid_model_response | 非 JSON、非法结构、空白／超长回复、非正常结束 |
| 504 | provider_timeout | 本地阶段等待或总 deadline 到期 |
| 503 | overloaded | 生成名额已满，默认3个，不额外排队 |

请求头合计上限 8 KiB，正文上限 16 KiB（包括流式接收的累计字节）；供应商响应最多读取 64 KiB。应用限制不能替代网关／HTTP 服务器的接入层限制。错误日志只记录安全追踪 ID 与分类，不输出原文或 traceback。

请求处理总时限默认 40 秒；验证请求后按 budget_ms 缩短，同样从请求开始计时。供应商连接最多 2 秒、单次读等待最多 35 秒，均受剩余总时限约束。`asyncio.timeout_at` 包住可取消的异步网络 I/O，超时退出上下文关闭连接；不承诺远端停止计算／计费。单进程生成并发默认 3，可通过 `AI_SERVICE_CONCURRENCY` 调整；超出名额立即返回 overloaded，不在 Python 内存排队。Java 数据库 worker 通过 `AI_WORKER_ENABLED=true` 条件接入，默认 3 个执行槽位，由 `AI_WORKER_CONCURRENCY` 配置；数据库仍是唯一持久待办队列。

## 有限风险回应

风险规则在普通模型之前执行。命中时直接生成内置 SUPPORT，提示联系可信任的人、寻求陪伴，并在有立即危险时联系当地急救或警方；不推测地区，不生成具体号码。回应仍经过非空和 2000 UTF-16 单位校验，结果为 model_called=false、model_name=null。

此分支不依赖外部资源文件、地区选择或核验日期，也没有定期维护门禁。旧环境变量 `XY_HELP_RESOURCES_FILE` 不再读取，可从部署环境移除；遗留值即使指向无效路径，也不影响应用启动和普通模型调用。内部认证及模型必需配置仍需合法。

当前仅有限中英文第一人称／省略主语表达规则，已覆盖“我想死”“我想去死”“不想活了”“活不下去了”和句首／分句边界的“想自杀”。保留明确否定、第三人称转述及“累死了”“笑死我了”“想死你了”“不想活在过去”“不想活成别人期待的样子”“想死磕这道题”等对照测试。省略主语仅在句首或中文常见分句边界匹配，避免直接把“我朋友说他想自杀”当作本人意图；这不是完整的否定、引用或人称分析，仍可能漏判或误判，不代表完整危机识别能力。

普通模型的 SYSTEM_PROMPT 与固定 SUPPORT 文案分别维护：更改 system 提示词不会自动改动固定分支。未来确需提供地区联系方式时，另行明确适用范围与信息来源，不自动引入 LLM 定位、IP 查询或启动门禁。

## 验证与证据范围

在仓库根目录运行：

```sh
uv run --directory xinyu-ai --locked pytest -q
```

测试替换外部供应商，保留真实认证、风险、校验与 HTTP 适配；另以真实回环 TCP 验证挂起响应在总 deadline 后关闭连接。运行测试需要允许绑定 127.0.0.1 临时端口。协议测试及有限样例不证明真实模型质量、危机覆盖率或生产可用性。

本地 RED/GREEN、依赖版本与后续接续见 `AI_WORKLOG/2026-11-07-ai-reply-1a-progress.md`。3A已完成Java内部HTTP联调与两条合成输入真实供应商验收；3D／3E自动worker和数据库提交闭环已实现验证，新增主体已带读；冷却、社区耗时校准、前端联调和完整发布验收尚待后续阶段。


### Java→Python 回环契约验收（3A）

后端 `AiServiceClientContractIT` 会用本目录 `.venv/bin/python -m tests.java_contract_server` 自动启动随机回环端口，并在结束后停止进程。测试子进程清空继承环境，使用字面量测试配置与合成 Provider，不读取真实模型密钥、不访问数据库。共享 `test-contracts/mood-types.json` 的13种心情、SUPPORT零供应商调用与错误token均走实际Python HTTP接口。

在后端目录显式运行 `sh ./mvnw '-Dtest=AiServiceClientContractIT' test`；需要已准备本服务锁定依赖的虚拟环境和本地端口权限。该IT不由默认 `*Tests` 匹配自动运行，不能只跑默认测试就声称跨服务已验。它只验证内部协议与客户端，worker／数据库／页面闭环在后续批次验收。
