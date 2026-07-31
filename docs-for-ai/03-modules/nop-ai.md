# nop-ai — AI 集成模块

## 功能概览

全面的 AI 集成子系统，覆盖 LLM 交互到 AI 辅助开发。

- **LLM Chat**：多模型聊天接口
- **Prompt 模板管理**：版本化 Prompt 模板
- **AI Agent**：Agent 框架
- **RAG**：检索增强生成
- **AI Coder**：AI 辅助编码
- **MCP Server**：Model Context Protocol 服务端
- **AI Shell**：命令行 AI 交互
- **AI Skills**：技能/工具包
- **多模型测试与评分**：对比不同模型输出质量

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopAiProject | `nop_ai_project` | AI 项目 |
| NopAiProjectRule | `nop_ai_project_rule` | 项目规则 |
| NopAiModel | `nop_ai_model` | AI 模型注册（provider, modelName, baseUrl, apiKey） |
| NopAiRequirement | `nop_ai_requirement` | 需求管理 |
| NopAiKnowledge | `nop_ai_knowledge` | 知识库 |
| NopAiPromptTemplate | `nop_ai_prompt_template` | Prompt 模板 |
| NopAiChatRequest | `nop_ai_chat_request` | 聊天请求 |
| NopAiChatResponse | `nop_ai_chat_response` | 聊天响应（含评分） |
| NopAiSession | `nop_ai_session` | 聊天会话 |
| NopAiGenFile | `nop_ai_gen_file` | AI 生成文件 |
| NopAiTestCase | `nop_ai_test_case` | 测试用例 |

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-ai-core` | AI 核心接口（含 LLM 集成） |
| `nop-ai-agent` | Agent 框架 |
| `nop-ai-rag` | RAG 检索增强 |
| `nop-ai-skills` | AI 技能 |
| `nop-ai-tools` | AI 工具 |
| `nop-ai-toolkit` | 工具包 |
| `nop-ai-coder` | AI 辅助编码 |
| `nop-ai-shell` | 命令行交互 |
| `nop-ai-mcp-server` | MCP Server |
| `nop-ai-dao` | ORM 实体与 DAO |
| `nop-ai-service` | 业务逻辑 |
| `nop-ai-web` | Web 层与 AMIS 页面 |

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-ai/model/nop-ai.orm.xml` |
| 引擎可靠性/超时 | `AIREL-001`（见 `../04-reference/source-anchors.md`）：`nop-ai-agent` 的 `DefaultAgentEngine` |

## Agent 引擎可靠性配置（nop-ai-agent）

`DefaultAgentEngine` 通过 setter 暴露可靠性/超时配置，保证 agent 会话、worker 线程、takeover lock 不被永久阻塞，且并发 agent 不互相饿死。默认值保证开箱即用（均为正数）：

| 配置项 | 默认值 | 语义 |
|--------|--------|------|
| `agentExecutor` | 专用 cached 守护线程池（线程名 `nop-ai-agent-exec-*`） | 三个入口点（`execute`/`resumeSession`/`restoreSession`）的 `supplyAsync` executor，替代 `ForkJoinPool.commonPool()`（默认仅 3-7 线程，多并发 agent 易互相饿死）。可通过 `setAgentExecutor` 覆盖（建议用 cached/virtual-thread 池，固定大小池在 ReAct LLM 超时回派到同一池时有自死锁风险） |
| `callAgentTimeoutMs` | `120000`（120s） | call-agent 子 agent 执行的 wall-clock 超时。超时后调用 `engine.cancelSession(childSessionId, forced=true)` 取消子 agent，释放 LLM/DB 资源（非僵尸执行）。必须为正数 |
| `llmTimeoutMs` | `120000`（120s） | ReAct 主循环单次 LLM 调用的 wall-clock 超时（经 `callChatWithTimeout` 用可中断的 `f.get(timeout)` 包裹）。`<= 0` 禁用（向后兼容逃生舱） |
| `toolTimeoutMs` | `300000`（300s） | ReAct dispatch fanout 中单次工具调用的 per-tool `.orTimeout`。超时转为 LLM 可见的工具错误响应。`<= 0` 禁用（向后兼容逃生舱） |
| `lockLeaseMs` | `1800000`（30min） | 跨进程 takeover lock 的租约时长（ms）。持有者崩溃时租约被动过期，另一实例可抢占。仅在功能性 lock（如 `DbSessionTakeoverLock`）接入时生效；`NoOpSessionTakeoverLock` 默认下无行为 |
| `lockRenewIntervalMs` | `600000`（10min） | takeover lock 心跳续期间隔（ms，租约 30min 的安全 1/3 分数）。执行期间引擎周期调用 `tryRenew` 把 `LOCK_EXPIRES_AT` 推到 `now + lockLeaseMs`，使长时执行（>30min）的租约不会中途过期被另一实例抢占（double-execution 防护，plan 273）。`tryRenew` 返回 false（租约丢失/被抢占）时中止本侧执行并把 session 置 `failed`。`<= 0` 禁用续期（纯被动 TTL，向后兼容逃生舱）。仅在功能性 lock 接入时生效；`NoOpSessionTakeoverLock` 默认下续期为无害 no-op |

超时发生时执行显式失败（`AgentExecStatus.failed` 或工具错误响应），不静默跳过。接线锚点见 `AIREL-001`。

## 服务级配置（nop-ai-core）

| 配置键 | 默认值 | 语义 |
|--------|--------|------|
| `nop.ai.service.cache-ttl` | `0` | AiChat 响应缓存的过期时间（秒）。`0`=永不过期（兼容默认）。读取时惰性过期：缓存条目文件 mtime 超过 TTL 视为 miss 并删除，不主动清扫、不改缓存文件格式。接线于 `nopAiChatResponseCache` bean（`ai-defaults.beans.xml`） |
| `nop.ai.service.log-message` | `false` | LLM 引擎执行时是否自动打印全部请求/响应消息。全局默认关闭（安全）；生效条件为「全局开启 且 单模型未显式关闭」——单模型可在 llm 配置中经 `logMessage="false"`（`setLogMessage`）覆盖关闭。凭据脱敏由 `DefaultChatLogger` 保证 |
| `nop.ai.service.rate-limit-acquire-timeout` | `1000` | LLM 调用本地限流（llm.xml 配 `rateLimit` 时）的许可获取超时（毫秒）。超时未获许可抛 `ERR_AI_RATE_LIMITED`（携带 `httpStatus=429`，`LlmErrorClassifier` 判为 RATE_LIMITED 可重试）——替代旧的无限阻塞 `acquire()`，消除挂起风险（MA6.3-AR-6）。`0`=立即失败（fail-fast） |

**限流扩展点（MA6.3-AR-6 裁定）**：`ChatServiceImpl` 每 provider 一个 in-memory token bucket（`DefaultRateLimiter`），无 tenant 身份来源。per-tenant 配额 = **文档化扩展点**：子类覆盖 `createRateLimiter(double)`（按需 per-tenant key 建 limiter）或 `checkRateLimit(...)`；跨 JVM 分布式限流 = **文档化扩展点**：替换 `IRateLimiter` 实现（接口已抽象 `tryAcquire` 语义）。`DefaultAiChatService`（废弃类）同款限时 tryAcquire 处理已对齐。

## 业务权限模型（nop-ai-service）

nop-ai 为框架模块组：42 个 xbiz 文件全部继承 CRUD 声明式 action（自定义 action 面为 0），**声明式 CRUD 权限归属调用方应用层**（与 nop-code/nop-auth 的 DataAuth 应用层配置模式一致），框架基线不声明 `rights=`/`roles=`（裁定 2026-07-31，P2-MA3-026 路线 B）。自定义方法面基线：MR2 已在自定义 BizModel 落 `@Auth(permissions="<BizObjName>:<action>")`（如 `NopAiChatResponse:query`）。应用层如需收紧 CRUD 权限，可在自己的 Delta xbiz 中声明 `rights`。

## 相关文档

- `../reusable-modules-overview.md`
