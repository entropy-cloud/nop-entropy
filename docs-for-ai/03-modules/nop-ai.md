# nop-ai — AI 集成模块

## 功能概览

全面的 AI 集成子系统，覆盖 LLM 交互到 AI 辅助开发。

- **LLM Chat**：多模型聊天接口
- **Prompt 模板管理**：版本化 Prompt 模板
- **AI Agent**：Agent 框架
- **AI 网关 / 透明账号切换（failover）**：`nop-ai-gateway`——LLM failover + channel 消息网关 + 扫码登录编排，详见 `nop-ai-gateway.md`
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
| NopAiProjectConfig | `nop_ai_project_config` | 项目配置（模型/Agent 绑定） |
| NopAiModel | `nop_ai_model` | AI 模型注册（provider, modelName, baseUrl, apiKey, credentialId） |
| NopAiRequirement | `nop_ai_requirement` | 需求管理 |
| NopAiRequirementHistory | `nop_ai_requirement_history` | 需求历史版本 |
| NopAiKnowledge | `nop_ai_knowledge` | 知识库 |
| NopAiPromptTemplate | `nop_ai_prompt_template` | Prompt 模板 |
| NopAiPromptTemplateHistory | `nop_ai_prompt_template_history` | Prompt 模板历史版本 |
| NopAiChatRequest | `nop_ai_chat_request` | 聊天请求 |
| NopAiChatResponse | `nop_ai_chat_response` | 聊天响应（含评分） |
| NopAiGenFile | `nop_ai_gen_file` | AI 生成文件 |
| NopAiGenFileHistory | `nop_ai_gen_file_history` | 生成文件历史版本 |
| NopAiTestCase | `nop_ai_test_case` | 测试用例 |
| NopAiTestResult | `nop_ai_test_result` | 测试执行结果 |
| NopAiSession | `nop_ai_session` | 聊天会话 |
| NopAiSessionMessage | `nop_ai_session_message` | 会话消息（8 种类型） |
| NopAiSessionInput | `nop_ai_session_input` | 会话输入队列 |
| NopAiSessionContext | `nop_ai_session_context` | 上下文压缩快照 |
| NopAiTodo | `nop_ai_todo` | Agent 待办与依赖 |
| NopAiEvent | `nop_ai_event` | Agent 审计事件日志（append-only） |
| NopAiChannelSession | `nop_ai_channel_session` | 信道会话映射（渠道 ↔ Agent 会话） |

> 22 实体与 `nop-ai-dao` 一一对应（2026-09-14 复核：`nop-ai/model/nop-ai.orm.xml` 共 22 个 `<entity>`，`nop-ai/nop-ai-dao` 实体 Java 类 22 个）。

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-ai-api` | 公开 API 契约（`IChatService`/`ChatOptions` 等，新 AI API）。**WI14/plan 2446（2026-10-12）推翻 P2 round-4 保留裁定并删除实体 CRUD 生成面**：`io.nop.ai.api.crud/` 22 接口 + `io.nop.ai.api.beans/` 44 Bean 全仓零消费者（五形态普查：import/FQN/反射/短形态/类名字符串）确证为死代码，66 文件删除 + 22 实体 xmeta 打 `tagSet="no-api"`（gen-crud-api.xgen 空转保留为再启用开关）。wire 名对齐的 `NopAiXxxBizModel`（nop-ai-service）不受影响；客户端强类型契约需求经 git 历史（本提交）或重开 no-api 标签再生成满足 |
| `nop-ai-core` | AI 核心接口（含 LLM 集成） |
| `nop-ai-agent` | Agent 框架 |
| `nop-ai-rag` | RAG 实现落点模块（K2 起非空）：`IVectorStore` 双实现（`PgVectorStore` pgvector JDBC 驱动 + `InMemoryVectorStore` 内存参考实现，均不注册 default bean）；`IVectorStore` 为 nop-ai-core SPI 契约（P1-MA5-003），生产实现落点在本模块（04-rag-module-position §四/§八） |
| `nop-ai-gateway` | AI 网关（三块能力）：LLM failover（路由格式转换 + 透明账号切换——两种形态 + 流式重订阅 + 并发限流 + 模型类路由 + 选择策略 + 指标）+ channel 消息网关（渠道 ↔ agent 桥接 + 业务消息层 + 会话映射）+ 扫码登录编排。**使用文档见 `nop-ai-gateway.md`** |
| ~~`nop-ai-skills`~~ | WI17/plan 2454 解散：code-analyzer 升 nop-ai 直属；deepwiki/translate prompt 资源迁 nop-ai-core VFS（`_vfs/nop/ai/prompts/{deepwiki,translate,...}`） |
| `nop-ai-tools` | AI 工具 |
| `nop-ai-toolkit` | 工具包 |
| `nop-ai-coder` | AI 辅助编码 |
| `nop-ai-shell` | 命令行交互 |
| `nop-ai-mcp-server` / `nop-spring-mcp-server-support` | WI15/plan 2452 解散/并入：`nop-ai-mcp-server` 的 AiFileTool 迁入 nop-ai-tools（`io.nop.ai.tools.mcp` + VFS bean），Simplifier 迁 nop-ai-core；support 并入 nop-spring-mcp-server |
| `nop-ai-maven` | WI16/plan 2453 解散：mavenvfs 能力（DeltaVirtualFileSystem/VfsMavenCli 等 6 文件）迁入 nop-ai-toolkit `io.nop.ai.toolkit.mavenvfs`，模块删除 |
| `nop-ai-dao` | ORM 实体与 DAO |
| `nop-ai-service` | 业务逻辑 |
| `nop-ai-web` | Web 层与 AMIS 页面 |

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-ai/model/nop-ai.orm.xml` |
| 引擎可靠性/超时 | `AIREL-001`（见 `../04-reference/source-anchors.md`）：`nop-ai-agent` 的 `DefaultAgentEngine` |
| 会话生命周期/编排并发契约 | `AGENT-CC-001`（见 `../04-reference/source-anchors.md`）：注入登记/终态清理/team 取消/kill 通道/方言流式/DB 消息治理/接管锁 abort |
| RAG 向量库/合成/收集并发契约 | `RAG-CC-001`（见 `../04-reference/source-anchors.md`）：`PgVectorStore` per-collection 守卫 + `InMemoryVectorStore` 原子 upsert + `RagSynthesizeService` 错误传播 + `JavaSourceFileFinder` 函数式收集 |

## LLM 方言请求序列化契约（P1-6.7-001..002，plan 2399）

- **Anthropic Messages API 请求侧 role**：仅 `user`/`assistant`（`model` 仅出现在响应侧）——`assistant` 原样保留，映射为 `model` 会使多轮对话第二轮起 400。
- **工具调用轮**：`ChatToolCallMessage` 的调用参数由 `tool_calls`（OpenAI/Ollama）或 `functionCall`（Gemini）字段承载——不把 arguments JSON 重复置入 `content`/text part（请求体污染消除）。
- **Reasoning 消息**：只发 thinking 载荷（OpenAI/Ollama `thinking` 字段、Gemini `<thinking>` text part、Anthropic thinking block）——不双发裸文本 content。

## 后台任务状态机与 pgvector 回读（P1-7.1-001/002，plan 2398）

- **后台任务状态机（DefaultBackgroundTaskRegistry）**：`NOTIFICATION_FAILED` 是簿记态（桥通知失败标记），不承载业务语义——Entry 保留 `originalStatus`（首个业务终态 FAILED/TIMEOUT/COMPLETED）；懒重投递成功后桥回调携带原始终态且任务状态**恢复为原始终态**（不会把 FAILED/TIMEOUT 任务误报为 COMPLETED）。
- **pgvector search 回读**：`PgVectorStore.search` 回读 embedding/content/metadata 三列并填充 VectorData（content 键 + metadata jsonb 的 docId/chunkIndex/indexId 各键）——检索 hits 携带完整来源信息供合成上下文使用。

## Agent 循环契约（P1-6.7-005 / P1-6.7-006，plan 2400）

- **POST_REASONING BAIL 的 tool_call 配对不变量**：`ReActAgentExecutor` 在 BAIL 判定之前已把 assistant 消息与其 `ChatToolCallMessage` 写入 `ctx` 并经 LLM_TURN checkpoint 持久化到 session。BAIL 丢弃该轮时，会为每个 tool_call 注入配对的 `ChatToolResponseMessage.error`（reason 携带 bail 原因）并重同步 persisted session——下一轮请求不携带无 response 的 tool_call（配对严格 provider，如 Anthropic Messages API，不会因 tool_call_id 失配返回 400），崩溃/恢复也不会复现坏配对。空 tool_calls 的 BAIL 行为不变（仅计数/日志/continue）。
- **上下文窗口独立于生成上限**：压缩触发与 forced-stop 判定的上下文窗口取自独立声明 `ChatOptions.maxContextTokens`（agent 模型侧 `ChatOptionsModel.contextLength` 经 `ChatOptionsHelper.toChatOptions` 映射），缺省回落 `ReActAgentExecutor.DEFAULT_MAX_CONTEXT_TOKENS`（128000）。**不得**用 `ChatOptions.maxTokens`（最大生成 token 数，仅约束输出）充当上下文窗口——否则常见配置（如 4096）会使首轮即触达水位而误触发 forced-stop。单一解析入口 `ReActAgentExecutor.resolveMaxContextTokens(ChatOptions)`，由 `AgentCompactionCoordinator`、`PipelineCompactor`、`Layer3FullSummaryStrategy` 共用。

## NopAiModel 凭证库迁移（credentialId）

`NopAiModel` 增加了可选普通列 `credentialId`（`credential_id VARCHAR(50)`，propId=17），作为指向加密凭证库 `nop_credential` 的**逻辑外键**。运行时消费读取路径**已接通**（W7-successor，2026-08-13；2026-08-17 A1-audit D6-01 修复装配缺位——此前 resolver 实现类从未在 beans.xml 注册（NopIoC 无注解扫描），`ChatServiceImpl.credentialResolver` 恒 null，credentialId 在运行时静默回退 config apiKey）。

**设计要点**：

- `credentialId` 是**普通可选列**，ORM 模型中**不声明 `refEntityName`/`to-one` 关系**——这避免 `nop-ai-dao` 引入对 `nop-credential-dao` 的跨模块 DAO 依赖。逻辑外键关系仅在文档中描述。
- 运行时消费经 `ICredentialProvider`（接口在 `nop-credential-api`，实现 `CredentialProviderImpl` 在 `nop-credential-service`，是平台唯一凭证解密点），而非直接 DAO 引用。
- `apiKey` 列**保留兼容**（已是 `tagSet="enc"` 列级加密，非明文）。未迁移的存量行继续用 `apiKey` 列，迁移是增量可选。

**运行时消费集成点（W7-successor 已落地）**：

- **SPI**：`IAiModelCredentialResolver`（`nop-ai-api`，纯加法接口），impl `AiModelCredentialResolverImpl`（`nop-ai-service`，注入 `IDaoProvider` + `ICredentialProvider`）。`ChatServiceImpl.buildHttpRequest` 经 `@Inject @Nullable` 引用——未装配时（部署不含 nop-credential）字段为 null → 跳过 credential 解析 → **零回归**。
- **apiKey 优先级链**：`accountKey > credentialId > resolveApiKey(config-var/secret-file)`。accountKey（coordinator FALLBACK 下沉的纠正账号）优先；credentialId（DB 凭证）次之；config 变量最低。配了 credentialId 仍可被 account-chain 覆盖（fallback 是显式纠正动作）。
- **查找键/粒度**：按 `provider + modelName` 精确匹配 `NopAiModel` 行取该模型的 credentialId（每模型自有行/自有凭证）。`.llm.xml` modelName ↔ `NopAiModel.modelName` 不对应（模型仅在 `.llm.xml` 存在）= 显式回退到 resolveApiKey + WARN 审计（非报错）。
- **fail-closed**：credentialId 非空但凭证缺失/软删/解密失败/字段空 → 抛 `NopException` 中止调用（强 fail-closed，不静默用错配 key）。credentialId 为空 → 回退 resolveApiKey（正常兼容路径，非异常）。
- **引用计数**：`NopAiModelBizModel.save` 按 credentialId 新旧差异调用 `registerUsage`/`unregisterUsage`，consumerRef 约定 `ai:NopAiModel:<modelId>`（bind/换绑/解绑）。凭证删除时由 `NopCredentialBizModel.delete` 的引用计数拦截。
- **删除动作注销引用（A1-audit D6-02）**：`delete`/`batchDelete`（基类逐 id 虚分派到 `delete`）/`deleteByQuery`（先收集命中行再删除后注销）三动作在删除后自动 `unregisterUsage`（同事务；provider 未部署或 credentialId 空白时跳过，保持可选装配语义；`unregisterUsage` 按 (credentialId, consumerRef) 删行、天然幂等）——模型删除后凭证不再被残留 usage 行永久拦截（运维死锁闭合：模型删除 → usage 注销 → 凭证可删）。

**迁移路径**（建凭证 → 设 credentialId（save 自动登记引用） → apiKey 冗余）：

1. 建凭证：`NopCredential__saveCredential(typeName="openai-api-key", name="<模型名>", fields={apiKey:"<明文>"})`，得到 `credentialId`。明文经 `CredentialCipher` 加密为 `cv1:` 密文落库，不跨出服务进程。
2. 设 `NopAiModel.credentialId`：`NopAiModel__save(data={id, credentialId})`——BizModel.save 自动 `registerUsage(credentialId, "ai:NopAiModel:<modelId>")`，无需手动登记引用。换绑/解绑自动 reconcile。
3. `apiKey` 列冗余：迁移后 `apiKey` 列成为冗余备份，可在确认运行时消费稳定后清理（保留不破坏兼容）。

> **运行时消费装配**（A1-audit D6-01 修复后语义）：`AiModelCredentialResolverImpl` 由 `nop-ai-service` 自身的 `app-service.beans.xml` 注册（bean id `nopAiModelCredentialResolver`，`ioc:default` + 模块 `_module` 自动装载）——**消费 app 含 nop-ai-service 即完成 resolver 接线**（`ChatServiceImpl` 按类型注入）。再含 `nop-credential-service`（提供 `nopCredentialProvider` bean，装配链 import `credential-defaults.beans.xml`）时凭证解析完整生效；`ICredentialProvider`/`IDaoProvider` 在 resolver 中为 `@Nullable` 可选注入——部署不含 nop-credential 时 resolver bean 仍创建（credentialId 空→回退、credentialId 非空→fail-closed）。装配回归由 `TestAiModelCredentialResolverWiring`（完整 app 容器）守护。凭证库与 `@sec:` 的边界详见 `nop-credential.md`。

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

**工具分发执行器缺省池契约（3.1-C4-011，plan 2498）**：`AgentDispatchExecutor.INSTANCE`（`AgentToolDispatcher` 装配点的缺省工具执行载体）为模块专用有界命名池 `nop-ai-agent-dispatch`（32 线程/队列 1024/daemon，经平台池工厂校验契约构造）——阻塞工具体（shell `waitFor`/阻塞 HTTP/文件 I/O）不再落 `ForkJoinPool.commonPool`；饱和走 fail-loud 拒绝（无界提交面同步收敛）；`toolTimeoutMs<=0`（禁超时逃生舱）配置触发一次性 WARN——cap≤0 时阻塞工具体可永久占线，池宽 32 为占用封顶边界。测试锚点：`TestAgentDispatchExecutor_C4_011`。

**Agent 沙箱后端清理契约（3.1-MA-17，plan 2501，B1-04 回升 P1）**：nop-ai-agent `security` 的 `DockerSandboxBackend`/`NoOpSandboxBackend` 与 toolkit 侧沙箱族（见「bash 工具 sandbox 接线」节）两套实现同病——本批以共享工具类 `SandboxProcessCleanup`（包私有静态工具，非共享基类）收敛双实现分叉，清理三口径与 toolkit 侧同口径不各走一套：①超时路径对本地 `docker run` CLI 进程树 `reapTree`（descendants+root `destroyForcibly`）兜底——容器 kill 失败/挂死不再泄漏 CLI 进程；②reader 退出补偿：`awaitReaderExit` 宽限 join 后显式关流强制 EOF（interrupt 对阻塞原生 pipe read 无效），drainStream 终态一次性发布不可变快照并释放 fd；③cleanup/kill 失败显式 WARN（异常与非零退出双形态，容器名+步骤可见）不静默。success 路径 `awaitReaderSettled`：补偿关流未收敛即收割持管道孙进程（`cmd &` 孪生持 stdout 写端）后短 rejoin；仍未收敛 ⇒ `SandboxResult.outputIncomplete=true`（缺省 false 零破坏）——空输出不静默交付；「孙进程滞留/输出截断」为声明边界（收割仅在补偿不能收敛时触发）。容器侧残留面运维口径同 toolkit：容器名为唯一可寻址面。测试锚点：`TestSandboxCleanupContract_MA17`/`TestDockerKillVisibility_MA17`/`TestDockerSandboxCleanup_MA17`；docker 门内 `TestDockerSandboxBackend.conditionalIntegrationTimeoutKillsContainerWithoutResidue`。

**Heartbeat wakeup 预算与 WAIT 碎片生命周期（3.1-C7-014，plan 2501）**：`DefaultAgentEngineConfig.maxHeartbeatTurns`（默认 `20`，`heartBeatIntervalMs` 邻域）为每个等待期 wakeup turn 数量预算——预算耗尽（`heartbeatTurns >= maxHeartbeatTurns`）后等待循环降级纯机械等待（deadline/cancel/pause/forceStop/queued steering 全保持活性），降级转场 INFO 恰记一次；`<= 0` 完全禁用 wakeup 轮（纯机械等待——预算/禁用语义非校验失败，同 `heartBeatIntervalMs` 约定；禁 cap 时 escape hatch = cancel/steer/forceStop，`AgentToolDispatcher` cap≤0 → deadline=MAX_VALUE 即预算耗尽后的机械终结面）。WAIT 裁决的 heartbeat 证据消息沿 WI3 invariant 1 既有请求副本机制携带、**不写入会话终史**——仅终局裁决（派生并行工作非空或 ABANDON 前缀，`isTerminalWakeupReply`）才 commit hb/reply/tool_call 入 ctx；WAIT/空文本/guardrail 拦截回复零入史，**ctx 终史 hb=0 为正面契约**。长工具调用期间 LLM 成本不再随等待时长线性放大。测试锚点：`TestHeartbeatWakeupBudget_C7_014`；`TestHeartbeatWaitPhase` 反向钉改写面 =「请求副本携带 hb + ctx 终史 hb=0」正面保护。

## Agent 会话生命周期与编排并发契约（nop-ai-agent / nop-ai-core）

> 本节为规范性契约摘要；裁定权威 = AI 开发记录区 concurrency 设计集 agent-orchestration-session-lifecycle-concurrency.md（按边界规则此处不直接链接），行号锚点见 `../04-reference/source-anchors.md` `AGENT-CC-001`。

**会话注入登记与终态摘除序**：注入可达性以引擎 `runningInjectionTargets` 注册表为唯一 SSOT（「注册在窗 = 注入可达」）——`doExecute` 与 `resumeSession`/`wakeSession`/`restoreSession` 三恢复路径在进入执行窗时对称 `put`、退出 finally 对称 `remove`；`injectInput` 的「查注册表→入 pendingInjections 队列」收敛入 per-session 注入锁（复合原子），`pendingInjections` 为同步载体且溢出判定与 `add`/`clear` 同锁。会话终态摘除序写死为四步：(1) 锁内置 terminal 标记 + drain 残余注入；(2) 锁外 persist（终态 save 含 drain 所得，persist 失败不阻塞后续步骤）；(3) 锁外注册表 remove 与终态清理；(4) 标记置位后任何注入持锁即被显式类型化拒绝（WARN + 计数 `nop.ai.agent.inject-after-terminal`，非静默）。**`completed` 是执行终态而非会话生命周期终态**：多轮原地继续执行依赖 store 驻留与注入通道，生命周期终态摘除（store 逐出/terminal 标记/drain/journal purge，判定谓词 `AgentSessionLifecycle.isSessionLifecycleTerminal`）仅在 failed/cancelled/forced_stopped/escalated/truncated 触发，不含 `completed`。

**终态清理接线与 restore 补偿**：引擎终态路径统一接线终态清理清单（sessionStore 三实现 remove、checkpoint 五容器清理 `FileBackedCheckpointManager.remove`/`purgeTerminal`、注册表/账本/mailbox 等承载容器），清理为 remove 型幂等；save 先于 remove（先落盘后释放）。终态语义矩阵：内存缓存即时释放；DB/FileBacked 持久层行**不物理删除**（复用 `AgentExecStatus` 终态值即终态标记），restore 按持久层终态标记走补偿加载（内存重建、终态只读）；纯内存实现的终态会话不可 restore（显式拒绝）。journal 活跃段受条数/尺寸双 cap 约束，超界环形截断最旧 checkpoint + WARN（`nop.ai.agent.checkpoint-journal-rotate`），归档不受 cap 约束。

**team 编排取消与 spawn 池生命周期**：成员/spawn 超时的取消一律经既有 `IAgentEngine.cancelSession`（消费平台 bindCancel/ICancelToken 原语，禁新造并行取消机制）——`DefaultMemberSpawner` spawn 超时分支在返回失败前取消 spawnedSessionId（取消失败显式 WARN + 计数）；`TeamTaskFlowOrchestrator` overall deadline 到期在既有图取消（taskRt cancel，停 DAG 推进）之上，遍历 per-flow 成员会话登记表对未 settle 成员逐个会话级 `cancelSession(forced=true)`（单点失败不中断汇聚）。spawn 编排池由 `TeamExecuteFlowExecutor` 单点持有（executor 与持有者生命周期对称，经既有 `setSpawnStepExecutor` 注入缝传入 orchestrator/coordinator），停机一律有界 drain 三段式：`shutdown()` → 有界 `awaitTermination` → 超时 `shutdownNow()` + 残余任务显式 fail/settle（dispatch future `completeExceptionally`，WARN `nop.ai.agent.spawn-drain-discard`，禁静默丢弃）。`inFlightDispatches` 不变式对 queued/running 两态均成立：running 态由 awaitTermination 预算内自然落定，queued 态由 drain 点显式 settle。

**工具超时取消传播（kill 通道判据）**：判据写死——`IToolProgressTracker` 已装配 ⇒ 机械超时与 `cancelCall` 必达进程级 kill：`AgentToolDispatcher` 的等待循环超时/取消点在 `future.cancel(true)` 后必经 `tracker.cancelExecution(callId)`（复用 heartbeat abandon 与 background stop 同一通道；bash 沙箱侧为进程树终止）；kill 失败或 tracker 未装配显式 WARN + 计数（`nop.ai.agent.tool-kill-unreachable`），非静默。HTTP/LLM 面超时取消经平台 bindCancel 请求级取消传播。

**方言流式消费契约（nop-ai-core）**：五方言流式解析统一经 `AbstractLlmDialect.parseStreamChunks` 多 chunk 通道（`ILlmDialect.parseStreamChunk` 单 chunk 方法保留为兼容委托，取首 chunk）——单个 SSE 事件携带多个 tool_call/part 时必须全枚举返回，禁止取首丢弃。usage 帧为增强信号非正确性信号：OpenAI 终止 usage 帧（`include_usage`）`choices` 为空数组，解析须容 null/空 choices（只产 usage chunk 不产内容 chunk）；Anthropic/Gemini/Ollama 各自 usage 字段（cache token/usageMetadata/eval_count）接入解析；provider 未回 usage 容忍为 null。provider 错误事件走**类型化带内错误通道**（error chunk），禁止把错误渲染为 text DELTA 污染 assistant 内容；消费侧对 error chunk 显式终止 + 类型化留痕。Ollama 空 content 帧不抢占 text 分支（thinking/tool_calls 分支可达）。

**DB 消息传输治理契约（DBMessageService）**：poll 与投递分离——单线程 poller 只负责取消息与 claim，消费回调由独立有界投递 executor 承载（拒绝策略显式：队列满类型化拒绝 + 消息回 claim 释放 + WARN 计数，禁静默丢弃；拒绝计入消息重投计数），长回调（如 call-agent）不再冻结其他 topic 投递。毒消息处置：per-message 重投计数上界（缺省 3），超界落 DLQ 死信表行（含原始载荷/失败原因/计数）并 ack 跳过——拒绝风暴被 DLQ 上界终结，无「无限拒绝-重投循环」。consumers 注册治理：同 topic 同 consumerId 重复注册为显式类型化拒绝（顶替/身份窃取反转；合法换装须先 cancel 后 subscribe），取消与注册对称。claim 携世代号（fencing token，条件 UPDATE 承载）：旧世代消费写被拒绝（`nop.ai.message.claim-fenced-reject`），suspend 状态由 claim 行世代+状态承载。

**接管锁续期异常升级 abort**：`SessionLockRenewal.renewOnceSafe` 保留 Throwable 绝缘护栏（异常不抛穿续期线程），但其上新增升级分支——异常按 **cause 链 unwrap 至根因**分类（禁按外层包装类型短路：`DbSessionTakeoverLock.tryRenew` 将 SQLException 包装为模块异常，外层类型恒「不可分类」）：根因为连接类 SQLException（不可达/断连）即 lease 未知，立即 `handleLeaseLost` 同型 abort（fail-safe：非对称分区下本节点自 abort 胜过双执行）；可判定瞬时类异常入计数豁免窗（per-handle 计数，连续 N 次缺省 3 超界必 abort，禁无限 WARN）；Error 轮入豁免窗计数不触发即时 abort。成功续期复位计数。

## Agent store 层边界（nop-ai-agent，审计 2026-09-12 AI-2 裁定）

nop-ai-agent 是可嵌入运行时引擎，**不能依赖 nop-ai-dao**。其运行时持久化（13 个 raw-JDBC 类：会话状态快照/检查点/消息传输/团队任务/锁/账本/用量，自建表如 `ai_agent_session`）保留独立 store 层，不注册 ORM——引擎内部状态非业务数据，不参与业务多租户/逻辑删除管道，生命周期由 runtime 管理（表清单与四项能力复用裁定记录于 agent store 层契约设计文档）。`ai_agent_session`（运行时状态 JSON）与 `NopAiSession`（业务会话记录）为两个概念。

**例外（双写已修复）**：`nop_ai_session_message` 是 ORM 实体 `NopAiSessionMessage` 管理的表——生产装配必须使用 `OrmModelSwitchedMessageWriter`（nop-ai-service，bean `nopOrmModelSwitchedMessageWriter`，IEntityDao 管道）；raw-JDBC 的 `DbModelSwitchedMessageWriter` 已弃用（其 initSchema DDL 与 ORM schema 管理冲突），仅限 embedded 无 ORM 部署。

## 服务级配置（nop-ai-core）

| 配置键 | 默认值 | 语义 |
|--------|--------|------|
| `nop.ai.service.cache-ttl` | `0` | AiChat 响应缓存的过期时间（秒）。`0`=永不过期（兼容默认）。读取时惰性过期：缓存条目文件 mtime 超过 TTL 视为 miss 并删除，不主动清扫、不改缓存文件格式。接线于 `nopAiChatResponseCache` bean（`ai-defaults.beans.xml`） |
| `nop.ai.chat-log.retention-days` | `0` | 聊天日志保留期（天）。`0`=不清理（兼容默认，磁盘包络由运维自行管理）。`>0` 时 `DefaultChatLogger` 在写入路径上惰性删除早于 今天-保留期 的日期分区目录（布局 `logDir/yyyy/MM-dd/`，目录级删除），每日期至多全量扫描一次（once-per-date 节流，避免每请求目录 IO）。清理 best-effort，失败仅 WARN（`nop.ai.chat-log.sweep-fail`），不影响日志写入主路径（3.1-MA-21，plan 2555 裁定 I） |
| `nop.ai.service.log-message` | `false` | LLM 引擎执行时是否自动打印全部请求/响应消息。全局默认关闭（安全）；生效条件为「全局开启 且 单模型未显式关闭」——单模型可在 llm 配置中经 `logMessage="false"`（`setLogMessage`）覆盖关闭。凭据脱敏由 `DefaultChatLogger` 保证 |
| `nop.ai.service.rate-limit-acquire-timeout` | `1000` | LLM 调用本地限流（llm.xml 配 `rateLimit` 时）的许可获取超时（毫秒）。超时未获许可抛 `ERR_AI_RATE_LIMITED`（携带 `httpStatus=429`，`LlmErrorClassifier` 判为 RATE_LIMITED 可重试）——替代旧的无限阻塞 `acquire()`，消除挂起风险（MA6.3-AR-6）。`0`=立即失败（fail-fast） |
| `nop.ai.service.stream-total-timeout` | `600000`（10min） | LLM 流式调用的全程总期限（毫秒）。独立于 `read-timeout`（后者仅约束响应头到达）：provider 返回 200 后连接挂死时，聚合 future 在本期限内以 `ERR_AI_STREAM_TOTAL_TIMEOUT` 显式异常终态。`0` = 不启用总期限（不推荐）。watchdog 为 globalTimer 短任务（非自旋） |
| `nop.ai.service.stream-idle-timeout` | `120000`（2min） | LLM 流式调用的 chunk 间 inactivity 上界（毫秒）。服务端静默（hang/代理黑洞/进程假死）超过本预算时聚合 future 以 `ERR_AI_STREAM_IDLE_TIMEOUT` 显式异常终态。`0` = 不启用（不推荐）。与传输层 body idle 读超时（见 `nop-network.md`）构成双层预算 |
| `nop.ai.service.stream-max-accumulated-chars` | `2000000` | LLM 流式聚合单次调用的累积字符上界（text+reasoning+tool-call arguments 总和）。超界 fail-fast `ERR_AI_STREAM_ACCUMULATION_EXCEEDED` 终态化流（防恶意端点单请求 OOM）。`0` = 不设上界（不推荐） |
| `nop.ai.service.stream-max-tool-calls` | `100` | LLM 流式聚合单次调用的 tool-call 条目数上界（按线上 itemIndex 键控的累加器）。超界 fail-fast `ERR_AI_STREAM_ACCUMULATION_EXCEEDED`。`0` = 不设上界（不推荐） |

**限流扩展点（MA6.3-AR-6 裁定）**：`ChatServiceImpl` 每 provider 一个 in-memory token bucket（`DefaultRateLimiter`），无 tenant 身份来源。per-tenant 配额 = **文档化扩展点**：子类覆盖 `createRateLimiter(double)`（按需 per-tenant key 建 limiter）或 `checkRateLimit(...)`；跨 JVM 分布式限流 = **文档化扩展点**：替换 `IRateLimiter` 实现（接口已抽象 `tryAcquire` 语义）。旧 `DefaultAiChatService`（已随 plan 2414 删除）的同款限时 tryAcquire 对齐约定随之失效。

**LocalFileOperator 读取限额（3.1-MA-20，plan 2555 裁定 H）**：`LocalFileOperator` 的读取面全部有界——readText 按 offset/limit 经模块内 `BoundedFileReader` helper 有界读取（不整文件装载）；分页 readLines 行数上界下沉；grepFiles 每文件扫描至多 10,000 行；globGrep 文件数内部缺省上界 1000（原传 0 无界自弃），命中即精确截断 + WARN（`nop.ai.file.find-truncated`）。`IFileOperator` 接口签名未变（接口化传递 maxFileCount 登记 successor 触发条款）。

## 流式调用取消/期限/交付语义契约（nop-ai-core，3.1-C3-001/C7-001/C7-002，plan 2505）

`ChatServiceImpl.callStream`/`aggregateStreamToResponse`（`callAsync(stream=true)` 汇聚路径同此语义）五条契约：

1. **取消闭合下游（不再单向悬挂）**：cancelToken 取消同时触发上游订阅 `cancel()` 与桥接 publisher `closeExceptionally(CancellationException)`——下游 Flow 订阅与聚合 future 在有界观察窗内收到可区分的取消终态（修复前 cancel 只向上游传播，下游悬挂、future 永不完成、订阅注册泄漏）。onNext 解析/提交异常经 closeExceptionally 兜底终态化，publisher 不存在永不关闭形态。
2. **背压窗口有界**：两级订阅（桥接对上游、聚合对桥接）均为有限 request 窗口（`STREAM_REQUEST_WINDOW=64` 初始 + 滑动补给），不再 `request(MAX_VALUE)` 全速拉取；桥接缓冲满载 fail-loud `ERR_AI_STREAM_BUFFER_OVERFLOW`（替代 uninterruptible 阻塞发射线程）。
3. **受管承载**：桥接 publisher 构造点显式 `GlobalExecutors.cachedThreadPool()`（平台治理面 daemon 池）——缺省不再是 `ForkJoinPool.commonPool`；响应日志盘写（logResponse）不指向 globalTimer。
4. **日志盘写异步化**：终态回调先 `future.complete` 再异步 `logResponseAsync`（cached 池承载）——序列化/脱敏/写盘失败仅 WARN（`nop.ai.chat-log-write-fail`）可见，不阻断 future 完成（修复前日志 I/O 抛异常 future 永不完成）。落点在 `ChatServiceImpl` 终态回调侧而非 `DefaultChatLogger` 内部——`IChatLogger` 同步契约保持不变。
5. **R11 交付纪律（终止信号校验）**：`onComplete` 校验聚合器终止信号（finishReason 非空 ∨ `StreamItemPhase.DONE`）；**分支裁定 = completeExceptionally**——收到过内容 chunk 但无终止信号（干净半关闭截断流）按传输级错误 `ERR_AI_STREAM_TERMINATION_SIGNAL_MISSING`（带 llmName + 截断字符数诊断）显式失败，不以成功响应交付（截断内容不经 `hasStreamedContent` 守卫漏入重试体系）。**零 chunk 空流保持既有成功契约**（空流成功为既有行为，不受 R11 校验影响）。

**双层预算与端到端锚点**：聚合层期限（上表 `stream-total-timeout`/`stream-idle-timeout`）与传输层 body idle 读超时（`nop.http.client.jdk.body-idle-timeout`，见 `nop-network.md`）构成两侧同批双层预算——缺省装配（JdkHttpClient）下取消/挂死/半关闭三场景全链有界终止，端到端回归锚点 `TestStreamFullChainEndToEnd_C3_001`（nop-ai-core）；组件级回归锚点 `TestStreamCancelDeadlineAndDelivery_C3_001`（nop-ai-core 13 条）与消费面钉 `TestFailoverStreamCancelPin_C3_001`（nop-ai-gateway——FailoverStreamFlow 作为 callStream 直接订阅者，挂死传输上调用方取消有界终止、订阅注册不泄漏；其「调用方取消不向输出发终态」的适配器既有语义归 R2-06，不在本契约内）。

## Embedding 客户端（nop-ai-core，K1）

`EmbeddingServiceImpl`（bean `nopAiEmbeddingModel`，`io.nop.ai.core.service`）是 `IEmbeddingModel` 的平台生产实现（K1，2026-09-28）：OpenAI 兼容 `/embeddings` 协议，经 `/nop/ai/llm/{provider}.llm.xml` 配置驱动——llm 配置需声明 `<embedUrl>`（如 `/embeddings`）且 `apiStyle="openai"`（其他 apiStyle fail-loud `ERR_AI_EMBEDDING_UNSUPPORTED_API_STYLE`）。provider 路由：`EmbeddingOptions.provider` > 配置 `nop.ai.embedding.default-llm` > fail-loud（`ERR_AI_EMBEDDING_NO_PROVIDER`）。可靠性面与 chat 同构：`rateLimit` 限流、`StandardRetryPolicy` 有界重试、`<accounts>` 账号链 failover（QUOTA/AUTH 分类经 `<errorMappings>` 配置面到达）、`IAiModelCredentialResolver` 可选凭证注入。

**重试调度器生命周期闭合（3.1-C4-004，plan 2498）**：内建重试调度器（`nop-ai-embedding-retry` 线程）有显式生命周期——bean 注册 `destroy-method="stop"`（`ai-defaults.beans.xml`，仅终结自建池、外部注入调度器不触碰）；`setRetryScheduler` 换装/置 null 恢复默认时对旧内部池有界终结（`shutdown` → 1s 宽限 → 超时 `shutdownNow` → 有界再等待，失败路径显式日志不吞异常）。无常驻孤儿线程。测试锚点：`TestEmbeddingRetrySchedulerLifecycle_C4_004`（换装孤儿终结/destroy 钩子/注入不触碰）。

**ITextEmbedding 桥接（N4.2）**：`AiModelTextEmbedding`（bean `nopAiTextEmbedding`，现位于 nop-ai-rag——plan 2421/WI4 随 vectorstore SPI 自 nop-ai-core 迁入，包名保留 `io.nop.ai.core.search`）实现 nop-search 的 `ITextEmbedding` SPI、委托 `nopAiEmbeddingModel`。classpath 含 nop-ai-rag 时经 `LuceneSearchEngine.setTextEmbedding`（`@Inject`+`@Nullable`）自动注入，Lucene 向量字段索引与 VECTOR/HYBRID 查询即用真实嵌入；失败语义为异常上抛（不静默降级）。**纯 core+search-lucene（无 rag）部署回退 hash 模拟**（bean 定义随类迁 rag，plan 2421 行为登记）。`IEmbeddingModel`/`EmbeddingOptions` 留守 nop-ai-core（LLM 内核输出 SPI——`EmbeddingServiceImpl` implements 它且依赖内核，接口外迁即 core→rag 倒置，plan 2421 排除性裁定）。

**向量库后端（K2，nop-ai-rag）**：`io.nop.ai.rag.vector.PgVectorStore`（pgvector JDBC 驱动，集成方注入 `javax.sql.DataSource`）与 `InMemoryVectorStore`（内存参考实现）实现 `IVectorStore` SPI（`IVectorStore`/`VectorQueryBean`/`VectorStoreOptions`/`VectorStoreResult` 与 `CosineSimilarity`/`RelevanceScore`、classifier 族已随 plan 2421/WI4 迁入 nop-ai-rag，**包名保留 `io.nop.ai.core.api.*`**——模块归属变更、包名不变，重启条件：major 版本 API 清理时改 io.nop.ai.rag.*，含消除 io.nop.ai.core.api.embedding 的跨模块 split package）。租户隔离 = UNIQUE(tenant_id,id) + 全语句谓词；search 要求 `query.vector` 必填（A1 裁定）。两实现均不注册 IoC default bean——装配归消费方（K3 管线/集成方）。owner doc 为 nop-ai 设计文档集的 vector-store.md（A1-A4 契约裁定）。并发契约（3.4-C6-001/C1-003）：PgVectorStore 的 DDL 就绪门禁为 **per-collection 作用域**——同实例逐 collection 惰性建表（ready set 承载；store/delete/search 三入口及经 store 委托的 update 同守卫面）；并发首建同 collection 的双跑 DDL 由 IF NOT EXISTS 幂等 + CREATE TABLE 单次重试承载（重试仍失败照常上抛，禁静默吞）。**不得回退为实例级单一就绪标志**——多 index 部署将从第二个 collection 起永久确定性失败。InMemoryVectorStore 的同 id upsert 为**单语句原子**（`put` 载体，并发同 docId 摄取不产生重复条目，与 PgVectorStore `ON CONFLICT(tenant_id,id) DO UPDATE` 范式对齐）；search 读 values 快照后按 score 显式排序（等分 tie 相对序未指定，fixture score 互异不依赖）。

**RAG 管线（K3，nop-ai-rag）**：`RagIngestService`（splitter→embed→store）、`RagSearchService`（embed query→search→重算 cosine score）、`RagSynthesizeService`（search→context→IChatService）三服务编排为完整摄取-检索-合成管线。经 `NopAiRagBizModel`（`@BizModel("/NopAiRag")`）暴露 `ingestDocument`/`search`/`synthesize` 三 action。v1 纯向量检索（hybrid 归 K8）；InMemoryVectorStore 缺省装配，生产 Delta 替换 PgVectorStore。synthesize 错误传播契约（3.4-C3-005，错误以成功交付族）：`RagSynthesizeService` 必检 `ChatResponse.isSuccess`——provider 错误响应 **fail-loud** 抛 `nop.err.ai.rag.synthesize-failed`（携 detail/errorCode/errorClassification/retryAfterMs 参数面，null 者不挂），禁折叠为空串成功交付；**判据只挂错误不挂空内容**——success 响应无 assistant 文本的合法空回答保留 `""` 返回（设计内行为，防过修）。code-analyzer 收集契约（3.4-C1-001）：`JavaSourceFileFinder.getAllJavaSourceFiles` 为函数式收集（per-module 局部 List + 终端合并）——禁止以共享可变 List 直连 `parallelStream` 副作用遍历（多模块仓并发 add 静默丢文件）。




## Agent 会话/取消/续期并发契约（nop-ai-agent，3.1-MA-10/14/15/16/18/C1-010，plan 2555）

- **会话存储锁纪律统一**：per-session 装载锁模板——装载期发布（persistedStates.put）收进 per-session 锁域；remove 不摘锁（锁身份不更换，会话复活面闭合；DB store 摘锁同约束），锁条目仅在空闲时机会性退休（按活跃 session 数缓增，watch-only）。`DBSessionStore` 租户复合键覆盖 sessions/persistedStates/sessionLocks 三映射（同 sessionId 跨租户互不串扰）。Checkpoint manager remove 为 per-session 条目移除并同步清 `loadedSessions` 负缓存。
- **装配窗取消哨兵**：cancelSession 对未注册会话以 null-ctx 哨兵 `putIfAbsent` 原子占位——装配中的执行在注册点发现哨兵即消费（两参条件移除）并立即进入取消流程；装配窗取消意图不再静默丢失；二次取消幂等；入口→getOrCreate 残余窗与既有声明等宽（watch-only）。
- **租户上下文 save-restore**：cancel/fork/fan-out 完成链捕获-恢复外层租户值（不再嵌套清除）；resumeSession 自带恢复语义（豁免）。
- **配额与账本原子化**：bindMemberSession 配额判定在 per-team 锁 + compute 复合原子域内（compute 域内禁 IO/发布）；contribution registry 注册/注销全局全序；EMA 估计器 RMW 串行化。
- **租约续期调度池**：`SessionLockRenewal` 使用小型专用多线程调度池（`nop.ai.agent.lock-renew-pool-size` 缺省 2）——阻塞 JDBC 续期不指向 globalTimer、无队头阻塞；executor 换装为启动期操作（运行中续期句柄不跨换装迁移）；接管锁续期语句带 queryTimeout（`nop.ai.agent.takeover-renew-query-timeout` 缺省 5s）。
- **恢复链路预算**：orphan resume 与 localCancelled 重试带可配置 attempt 上界 + 指数退避 + 结果观测，预算耗尽类型化升级（`nop.ai.agent.resume-max-attempts`/`resume-backoff-base-ms`/`cancel-max-attempts`/`cancel-backoff-base-ms`）。
- **运行期换装 volatile 纪律**：agentExecutor/lockRenewExecutor 及 C1-022 七组配置字段全部 volatile（换装可见性；旧池生命周期归 close() own 语义）。


## AI 可靠性资源包络契约（nop-ai，3.1-C3/C5/C7 束 b，plan 2556）

- **异步入口单异常通道**：`AiCommand.callChat` 前置段（限流/baseUrl/凭证）同步异常收敛为 failed future；缓存写失败不使成功响应 exceptionally（读降级/写隔离对称）。
- **重试为异常感知形态**：重试单位=callChat（每 attempt 天然复查入口限流器），异常完成经 LlmErrorClassifier 分类可重试，延迟经 StandardRetryPolicy full-jitter（GlobalTimer 轻量 hop，无阻塞体指向 globalTimer）；invalid 响应按 TRANSIENT 取 floor 延迟；adjustTemperature per-attempt、响应后处理循环外一次（语义由载体钉定）。
- **Retry-After 上限**：`StandardRetryPolicy` shouldRetry 入口单点 normalize（`MAX_RETRY_AFTER_MS`=10min，超限 WARN 记原值）——解析端保持原值。
- **熔断器 probe 世代回执**：ThresholdBreaker per-key probeGeneration——陈旧逃逸回执 WARN 丢弃不翻转状态（gen=-1 兼容回落档显式声明）；probe timeout 缺省 90s 可配；失败记忆条目带 lastTransition TTL 惰性清除（pristine 即时清理保持）。
- **LLM 热路径 DB 副作用隔离**：usage 记账/model-switched 写入失败 WARN+计数+阈值升级，不中止主流程；resolveModelId 进程级缓存。
- **响应缓存/邮箱包络**：响应缓存 max-entries LRU（逐出删文件）+ 单条字节上界 + promptName 路径穿越守卫（TTL 缺省 0 语义保持）；DeferredAckMailbox deadLetter 有界淘汰最旧 + inFlight 惰性 stale 回放（对齐 DBMessageService 5min 语义；生产缺省 off，显式启用即有界）。
- **restore 锁序**：restoreSession 状态变更与事件发布在执行槽获取成功之后（对齐 resumeSession 模式；装配失败对称清理）。


## 工具执行与输出包络契约（nop-ai-toolkit/tools/shell，3.2 束，plan 2557）

- **pathQueues 生命周期**：`ToolManagerImpl` 路径队列尾完成即两参条件驱逐（ABA 安全）；per-path 深度计数（`nop.ai.tool.path-queue-limit` 缺省 64）超限 fail-loud（QueueOverflowPolicy ERROR 语义），驱逐不影响同 path 串行化语义。
- **update-todos 原子化**：todoLists clear/write 收敛 compute 单次原子决策（同会话并行不丢更新）；会话条目清理面待 session-lifecycle seam（watch）。
- **工具文件写原子化**：DeltaVFS newOutputStream/copyToVirtual 与 DslToolImpl/AiFileTool 保存路径均 temp+ATOMIC_MOVE（降级 REPLACE_EXISTING）——失败保旧、读者不见撕裂；VFS virtual-resource 分支保持原样。
- **后台任务回滚**：BashExecutor registry.run 抛 UOE → taskId 回滚 FAILED（RUNNING 残留消除）。
- **cancel 可重试**：BashExecutionTracker 销毁段 UOE/SecurityException 守卫；销毁失败注册项保留（putIfAbsent 回填+rethrow），二次 cancel 可重试；remove 顺序外部契约零变化。
- **thought 存储生命周期**：clearHistory 删文件；TTL 清扫（`nop.ai.tools.thought-retention-days` 缺省 0=off）once-per-date 节流。
- **工具输出上界族**：http/graphql 响应体截断-续排空（`nop.ai.tool.http-max-response-chars` 缺省 100K+truncated 标记）；七失效面服务端 clamp（grep 命中 ≤1万/read-file 行帽 100K/patch 10M/readFiles 兜底 100K）；shell `>` 重定向阈值分块落盘（append 语义首刷截断保持）+ 字节帽 2MiB 超限命令失败；shell IO readLine 超帽分片可拼接、readAllText 超帽 fail-loud。
## 工具配置（nop-ai-tools）

| 配置键 | 默认值 | 语义 |
|--------|--------|------|
| `nop.ai.sequential-thinking-tool.storage-dir-path` | `./_tmp/ai/sequential-thinking/store` | SequentialThinking 会话 thought 的 JSON 文件存储目录。默认值相对 JVM 工作目录解析（`FileHelper.resolveFile`：`/` 开头为绝对路径，其余相对 `user.dir`）；配置为空回退 `~/.mcp_sequential_thinking`。裁定（P3-MA1-013）：保持文件持久化（会话级工具），多实例部署需配置共享路径或迁移 ORM |

### bash 工具 sandbox 接线（nop-ai-toolkit）

`ai-tools:bash`（`io.nop.ai.toolkit.tools.BashExecutor`）默认 **fail-closed**：未显式装配 `IBashSandbox` 后端时拒绝一切调用（错误消息为 `"BashExecutor has no IBashSandbox backend wired (fail-closed): ..."`），绝不回退到宿主 shell 执行。命令失败（exitCode ≠ 0）经正常结果路径返回真实退出码与输出（见 `bash.tool.xml` 错误契约）；沙箱/基础设施故障（Docker 不可达、容器启动失败、超时、资源超限）以 `"Sandbox refused execution [REASON]"` 错误返回。

**opt-in 装配**：在 `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/beans/ai-toolkit-defaults.beans.xml` 的 `ai-tools:bash` bean 上方有注释装配示例（M8-P1 登记）。两种后端：

- **HostBashSandbox**（宿主执行，显式 opt-in，**无隔离**）：仅强制 wall-time 超时、输出截断与工作目录 jail；`cpuCores`/`memoryMb`/`networkMode` 不生效。不可信输入请勿使用。
- **DockerBashSandbox**（Docker 隔离，推荐）：资源限制（`--cpus`/`--memory`）、默认 `--network none`、工作目录 jail、wall-time 超时。需 Docker daemon 与镜像（示例用 `alpine:3.19`）。

装配形态：`<property name="sandbox" ref="..."/>` 注入 `BashExecutor`。默认 fail-closed 语义是硬约束——任何接线不得改变"未显式 opt-in 时拒绝调用"的行为；全仓生产装配面无默认静默启用 bash 的路径（`TestBashToolDefaultWiring` 守护该文件）。

**沙箱清理契约（3.2-C6-001/C6-002/C6-003，plan 2501）**：三口径在 Host/Docker 双实现统一——①reader 退出补偿：宽限耗尽后显式关流强制 EOF（interrupt 对阻塞原生 read 无效），reader 线程唯一命名（callId 后缀）可观测，stdin 恒指向 NUL 设备（`cat` 类命令不悬挂）；`cmd &` 孪生子进程的输出在主进程结束后丢弃（声明边界，孪生进程本身由宿主回收）。②Docker 清理失败可见化：`docker kill`/`docker rm -f` 失败（异常或非零退出）记 WARN 携容器名——容器名是残留的唯一可寻址面，运维按名清扫；**容器侧兜底裁定 = 声明运维边界 + 本包络**（不内置 `--stop-timeout`/后台 reaper——后者为 Deferred 增强项）；超时路径对本地 `docker run` CLI 进程 `destroyForcibly()` 兜底（CLI 不随容器清理失败而滞留）。③清理辅助命令（`docker kill`/`rm -f`）有界化：限时 drain（3s）+ 有界 waitFor（1s）+ 超时必 destroy——daemon 挂死不再钉死清理线程（wallSeconds 契约不被绕过）。取消路径（`cancelExecution`）带进程树收割 + 关流补偿。回归测试：`TestSandboxReaderExitCompensation_C6_001`/`TestDockerKillVisibility_C6_002`/`TestDockerTimeoutCliBackstop_C6_002`/`TestRunShortCommandBounded_C6_003`。

### shell 命令执行池与 pipeline 包络（nop-ai-shell，3.2-C4-004，plan 2498）

`ShellCommandExecutor` 缺省装配（未注入 executor）不回落平台 `globalWorker`：构造模块专用有界命名池 `nop-ai-shell-pipeline`（32 线程/队列 1024/daemon，平台池工厂校验契约构造）——pipeline 生产/消费阶段与前台 bash 工具池、平台 worker 三者隔离，阶段任务不与全平台任务共池。**池参数包络** =「最大并发会话 × 最大管道段数」：有效并发行 ≤32、待发段深度 ≤1024。显式注入 executor 保持优先且永不被实例 `close()` 触碰（所有权边界=仅关闭自建池，`close()` 有界关闭）。测试锚点：`TestShellPipelinePoolIsolation_C4_004_3_2`（缺省链隔离钉/注入优先钉/close 终结钉）。

**pipeline 失败回滚契约（3.2-C4-003，plan 2501）**：阶段提交 REE 与管道早退即回滚——取消已提交阶段（FutureTask 载体下真实中断）并关闭全部已建队列（写方下一写显式失败），调用方得异常终态且池无线程滞留；任一阶段异常向上游传播取消（下游死亡不再钉死上游生产者）且非末位阶段异常 WARN 可观测；末级收集与末级生产并发启动（>1024 chunk 输出不再自锁，单命令路径收集同样并行化）；`0<&1` 自引用及其余 FD_INPUT 组合显式拒绝（不再静默忽略/挂死）；输出收集失败显式 WARN + 异常传播（不静默空串）。溢出语义声明：阶段队列 1024 chunk 有界阻塞写（背压由并发收集器消费），池饱和 fail-loud + 回滚。cancelToken 在本执行器链仅透传、无自身检查点——该覆盖面**裁定归 R4-23 批次承载**（3.2 工具输出/路径束；非 G56 dormant 面、不 Deferred）。回归测试：`TestShellPipelineDeadlockFamily_C4_003`（两自锁拓扑钉/REE 回滚/阶段异常解锁/`0<&1` 拒绝/收集可观测/端到端）。

### 工具执行语义（nop-ai-toolkit，P2 round-4 收口）

- **update-todos 会话隔离**：todo 表按 `IToolExecuteContext.getSessionId()` 键控（agent 引擎 `AgentToolExecuteContext` 提供 sessionId；toolkit `ToolExecuteContext.builder().sessionId(...)` 可显式设置）。同会话共享列表，跨会话完全隔离；无会话标识时**显式失败**（不静默落进程级全局表）。回归测试：`UpdateTodosExecutorTest`（跨会话隔离 / 空列表只清自己 / 无会话 fail-closed）。
- **并行并发上限**：`ToolManagerImpl.executeParallel` 真正执行 `maxConcurrency`（`IToolManager.callTools`）——正数上限下同时在飞调用数 ≤ 上限，超限排队不丢弃；null/非正数 = 无上限。回归测试：`ToolManagerImplParallelTest`（在飞数 ≤ 上限 / 结果无丢失 / null 无上限 / 结果保序）。
- **返回 future 取消语义（3.2-C4-008，plan 2499）**：经 `ToolManagerImpl` 返回 future 的 `cancel(true)` 为**真取消**——排队任务被取消后不再派生执行（执行入口统一 isCancelled 短路），在途任务取消传播到源 future；排队窗口的 `cancelToken` 注册盲区由注册前已取消补触发闭合（平台单点 `FutureHelper.bindCancelToken`，见 `../04-reference/source-anchors.md` `CANCEL-002`）。执行器取消能力分档：`jq` 为唯一 cancelToken 消费档（可中断），fs/团队/技能面执行器登记为不可取消档（不做隐式承诺）。回归测试：`TestToolManagerCancelChain_C4_008`（排队取消联动 / 在途传播 / 已取消短路 / 零回归对照）。
- **路径串行排队链契约（3.2-C1-001，plan 2501）**：`ToolManagerImpl.callTool` 对非 concurrency-safe 且可提取 pathKey 的调用做 per-path 串行——`pathQueues.compute` 内只做 tail 哨兵原子取换，执行体在 compute 返回后挂链运行（同步执行器装配——nop-cli `CliCallToolsCommand` 注入 `SyncThreadPoolExecutor.INSTANCE`——下全执行不持 CHM bin 锁；同 path 嵌套派发不再触发 Recursive update）。调用方 result future **保证终态落定**：`executeNow` 同步抛（饱和池 REE）→ `completeExceptionally` 显式异常终态而非永久悬挂，失败调用不毒化队列；已装配 context 但缺 executor 属装配错误 → 显式 fail-loud 异常终态。回归测试：`TestToolManagerQueueChain_C1_001`（同 bin 锁外执行 / 嵌套重入 / REE 终态 / null-executor fail-loud / 队列不毒化 / 信号量恢复 / 端到端终态输出）。
- **skill load 真实内容**：`skill` 工具 `load` 动作返回 VFS `/nop/skills/<name>/` 的真实目录清单 + 首个描述文件内容（README.txt/README.md/SKILL.md/skill.md/description.txt，超 4000 字符截断标注）；目录缺失/不可读显式报错。回归测试：`SkillExecutorVfsTest`。
- **SSRF 解析时防护**：`http-request`/`graphql-query` 在 pre-flight 文本校验之外，发请求前经 `SsrfGuardDnsResolver`（默认 `IDnsResolver`，可 `setDnsResolver` 注入）解析目标主机名——解析到内网/元数据地址（含 DNS rebinding 多答案集）fail-closed，连接不建立。该防护对全部 HTTP client 生效；redirect-hop 级 client 内解析拦截仅 Apache HttpClient 支持（需装配 `HttpClientConfig.dnsResolver`）。回归测试：`HttpRequestExecutorTest`/`GraphqlQueryExecutorTest`（mock DNS 注入端到端）。

## 业务权限模型（nop-ai-service）

nop-ai 为框架模块组：44 个 xbiz 文件全部继承 CRUD 声明式 action（自定义 action 面为 0），**声明式 CRUD 权限归属调用方应用层**（与 nop-code/nop-auth 的 DataAuth 应用层配置模式一致），框架基线不声明 `rights=`/`roles=`（裁定 2026-07-31，P2-MA3-026 路线 B）。自定义方法面基线：MR2 已在自定义 BizModel 落 `@Auth(permissions="<BizObjName>:<action>")`（如 `NopAiChatResponse:query`）。应用层如需收紧 CRUD 权限，可在自己的 Delta xbiz 中声明 `rights`。

> xbiz 计数 44（2026-09-14 复核：`find nop-ai -path '*/src/main/resources/_vfs*' -name '*.xbiz' | wc -l` = 44，22 实体 × 基+保留）。

## 废弃 chat 栈删除迁移说明（plan 2414，roadmap WI1，2026-10-11）

nop-ai-core 的旧 chat API 已整体删除，公共 API 变更如下（Protected Areas 迁移说明）：

| 已删除 | 替代 |
|--------|------|
| `IAiChatService`/`IAiChatSession`/`IAiChatProgressListener`/`AiChatOptions`（`core/api/chat/`） | `IChatService`/`ChatRequest`/`ChatResponse`/`ChatOptions`（nop-ai-api，bean `nopChatService`） |
| `core/api/messages/` 全部（`AiChatExchange`/`Prompt`/`AiMessage` 族 14 文件） | `ChatRequest`/`ChatResponse` + `io.nop.ai.core.prompt.ChatExchange`（脚本门面：outputs/validate/resultValue） |
| `core/api/tool/`（`IAiChatToolSet`/`IAiChatFunctionTool`/`ToolSpecification` 族） | 无——工具调用不在 AiCommand 命令路径（plan 2419 退役）；agent 引擎 toolkit 运行时是唯一工具执行面 |
| `DefaultAiChatService`/`MockAiChatService`/`DefaultAiChatSession` 族 | `ChatServiceImpl`（bean `nopChatService`）/`MockChatService`（`core/mock` 新栈——plan 2434/WI7 已下沉 core **test scope**，无 main 装配；FileSystemResponseProvider record/replay 死能力随删） |
| persist 族 `IAiChatExchangePersister`/`DefaultAiChatExchangePersister` | `IChatExchangePersister`/`DefaultChatExchangePersister`（JSON 序列化 + AES；**旧 `/nop/ai/cache` 磁盘条目一次性失效**） |
| 错误码 8 个（result-invalid-end-line/result-invalid-number/get-session-deprecated/unknown-role/command.not-found/command.empty-tools-node/command.tools-node-parse-failed/command.file-path-empty） | —（随废弃栈退役） |

行为登记：①旧磁盘缓存条目格式失效（TTL 默认 0 时残留无害，读取走 load-fail→miss）；②`disableCache`/`streamListener`/`seed`/`contextLength` options 键不再有落点（`contextLength` task-xml attr 仍可解析但被忽略——新栈 dialect 本就不消费）；③`ai:toolSet` 死属性已从 deepwiki task-xml 移除；④`nopGraphQLToolSet` bean 已随 plan 2415（WI2 双路线裁定）退役——AiCommand 工具循环生产链不存在，`nop.err.ai.unknown-tool-call` 错误码同步删除。

## 相关文档

- `../reusable-modules-overview.md`
