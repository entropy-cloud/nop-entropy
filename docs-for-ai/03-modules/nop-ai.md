# nop-ai — AI 集成模块

## 功能概览

全面的 AI 集成子系统，覆盖 LLM 交互到 AI 辅助开发。

- **LLM Chat**：多模型聊天接口
- **Prompt 模板管理**：版本化 Prompt 模板
- **AI Agent**：Agent 框架
- **AI 网关 / 透明账号切换（failover）**：`nop-ai-gateway`——详见 `nop-ai-gateway.md`
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
| NopAiModel | `nop_ai_model` | AI 模型注册（provider, modelName, baseUrl, apiKey, credentialId） |
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
| `nop-ai-rag` | RAG 实现落点模块——空占位（P3-MA3-003 裁定保留）：`IVectorStore` / `IEmbeddingModel` 为 nop-ai-core 的 SPI 扩展点契约（P1-MA5-003），无生产实现属设计意图；未来实现放本模块 |
| `nop-ai-gateway` | AI 网关：路由格式转换 + 透明账号切换（failover）——两种形态（网关拦截器 / 本地 `IChatService` 适配器）+ 流式重订阅 + 并发限流 + 模型类路由 + 选择策略 + 指标。**使用文档见 `nop-ai-gateway.md`** |
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

## Agent store 层边界（nop-ai-agent，审计 2026-09-12 AI-2 裁定）

nop-ai-agent 是可嵌入运行时引擎，**不能依赖 nop-ai-dao**。其运行时持久化（13 个 raw-JDBC 类：会话状态快照/检查点/消息传输/团队任务/锁/账本/用量，自建表如 `ai_agent_session`）保留独立 store 层，不注册 ORM——引擎内部状态非业务数据，不参与业务多租户/逻辑删除管道，生命周期由 runtime 管理（完整表清单与裁定见 `ai-dev/design/nop-ai-agent/store-layer-contract.md`）。`ai_agent_session`（运行时状态 JSON）与 `NopAiSession`（业务会话记录）为两个概念。

**例外（双写已修复）**：`nop_ai_session_message` 是 ORM 实体 `NopAiSessionMessage` 管理的表——生产装配必须使用 `OrmModelSwitchedMessageWriter`（nop-ai-service，bean `nopOrmModelSwitchedMessageWriter`，IEntityDao 管道）；raw-JDBC 的 `DbModelSwitchedMessageWriter` 已弃用（其 initSchema DDL 与 ORM schema 管理冲突），仅限 embedded 无 ORM 部署。

## 服务级配置（nop-ai-core）

| 配置键 | 默认值 | 语义 |
|--------|--------|------|
| `nop.ai.service.cache-ttl` | `0` | AiChat 响应缓存的过期时间（秒）。`0`=永不过期（兼容默认）。读取时惰性过期：缓存条目文件 mtime 超过 TTL 视为 miss 并删除，不主动清扫、不改缓存文件格式。接线于 `nopAiChatResponseCache` bean（`ai-defaults.beans.xml`） |
| `nop.ai.service.log-message` | `false` | LLM 引擎执行时是否自动打印全部请求/响应消息。全局默认关闭（安全）；生效条件为「全局开启 且 单模型未显式关闭」——单模型可在 llm 配置中经 `logMessage="false"`（`setLogMessage`）覆盖关闭。凭据脱敏由 `DefaultChatLogger` 保证 |
| `nop.ai.service.rate-limit-acquire-timeout` | `1000` | LLM 调用本地限流（llm.xml 配 `rateLimit` 时）的许可获取超时（毫秒）。超时未获许可抛 `ERR_AI_RATE_LIMITED`（携带 `httpStatus=429`，`LlmErrorClassifier` 判为 RATE_LIMITED 可重试）——替代旧的无限阻塞 `acquire()`，消除挂起风险（MA6.3-AR-6）。`0`=立即失败（fail-fast） |

**限流扩展点（MA6.3-AR-6 裁定）**：`ChatServiceImpl` 每 provider 一个 in-memory token bucket（`DefaultRateLimiter`），无 tenant 身份来源。per-tenant 配额 = **文档化扩展点**：子类覆盖 `createRateLimiter(double)`（按需 per-tenant key 建 limiter）或 `checkRateLimit(...)`；跨 JVM 分布式限流 = **文档化扩展点**：替换 `IRateLimiter` 实现（接口已抽象 `tryAcquire` 语义）。`DefaultAiChatService`（废弃类）同款限时 tryAcquire 处理已对齐。

## 工具配置（nop-ai-tools）

| 配置键 | 默认值 | 语义 |
|--------|--------|------|
| `nop.ai.sequential-thinking-tool.storage-dir-path` | `./_tmp/ai/sequential-thinking/store` | SequentialThinking 会话 thought 的 JSON 文件存储目录。默认值相对 JVM 工作目录解析（`FileHelper.resolveFile`：`/` 开头为绝对路径，其余相对 `user.dir`）；配置为空回退 `~/.mcp_sequential_thinking`。裁定（P3-MA1-013）：保持文件持久化（会话级工具），多实例部署需配置共享路径或迁移 ORM |
| `nop.ai.tools.graphql-tool-names` | （空） | `nopGraphQLToolSet` 暴露的 GraphQL 操作名集合（csv）。为空时 tool set 无函数。`nopGraphQLToolSet` bean 仅在存在 `nopGraphQLEngine` bean 时注册（`<ioc:condition><on-bean>`，P3-MA1-016） |

## 业务权限模型（nop-ai-service）

nop-ai 为框架模块组：42 个 xbiz 文件全部继承 CRUD 声明式 action（自定义 action 面为 0），**声明式 CRUD 权限归属调用方应用层**（与 nop-code/nop-auth 的 DataAuth 应用层配置模式一致），框架基线不声明 `rights=`/`roles=`（裁定 2026-07-31，P2-MA3-026 路线 B）。自定义方法面基线：MR2 已在自定义 BizModel 落 `@Auth(permissions="<BizObjName>:<action>")`（如 `NopAiChatResponse:query`）。应用层如需收紧 CRUD 权限，可在自己的 Delta xbiz 中声明 `rights`。

## 相关文档

- `../reusable-modules-overview.md`
