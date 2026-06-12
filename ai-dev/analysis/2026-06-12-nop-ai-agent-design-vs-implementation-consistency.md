# nop-ai-agent 设计文档与实现代码一致性深度分析

> Status: open
> Date: 2026-06-12
> Scope: nop-ai-agent 全模块（nop-ai-agent, nop-ai-toolkit, nop-ai-core, nop-ai-shell）+ 27 篇设计文档
> Conclusion: —（open 状态，见分析正文）

## Context

nop-ai-agent 的 27 篇设计文档描述了一个四层接口架构的 Agent 执行引擎，包含 ReAct 循环、Hook/Skill 系统、会话管理、安全权限、LLM 层、工具调用、可靠性增强、多 Agent 协同等子系统。

本文对比设计文档与实际 Java 实现之间的一致性，从三个维度分析：
1. **设计描述未实现** — 设计中规划但代码中不存在
2. **实现存在但设计未提及** — 代码中的好模式或坏模式，设计应吸纳或修正
3. **设计与实现不一致** — 同一概念的命名、语义、结构分歧

数据来源：对 nop-ai-agent（97 主文件 9,305 行 + 20 测试文件 3,961 行）、nop-ai-toolkit（19 工具 + XDSL 系统）、nop-ai-core（4 Dialect + 10 Provider 配置）、nop-ai-shell（41 文件）的全面代码审计，与 10 篇核心设计文档的逐条交叉验证。

---

## A. 设计文档可从实现中学习的改进点（实现优于设计）

### A1. 实现中的好模式，设计应吸纳

| # | 模式 | 实现位置 | 设计应如何改进 |
|---|------|---------|--------------|
| A1.1 | **不可变值类型 + 静态工厂** | `Permission`, `PathAccessResult`, `ToolAccessResult` — 均为不可变对象，`allow()/deny(reason)` 静态工厂 | 设计文档 §3（security-and-permissions.md）仅提到"三层接口"，未定义结果类型的数据契约。应补充 `DenyResult` / `AllowResult` 值类型规范，与实现对齐 |
| A1.2 | **AllowAll 透传默认实现** | `AllowAllPermissionProvider`, `AllowAllToolAccessChecker`, `AllowAllPathAccessChecker` — 各 9-11 行，极简 | 设计提到"pass-through 默认"但未命名。应在 glossary.md 中补充命名约定：`AllowAll{InterfaceName}` 或 `NoOp{InterfaceName}` |
| A1.3 | **伸缩构造函数链** | `DefaultAgentEngine` 和 `ReActAgentExecutor` 各 5 个构造函数重载，自动填充默认实现 | 设计未描述构造模式。应在架构基线文档中记录："引擎层核心类采用 telescoping constructor，最简构造函数只需必要参数" |
| A1.4 | **三层安全顺序执行** | `ReActAgentExecutor` 按 `IToolAccessChecker → IPermissionProvider → IPathAccessChecker` 顺序检查，任何一层 deny 即中止 | 设计中三个接口位于同一 Layer 1，但未明确执行顺序。应明确：IToolAccessChecker（硬编码 deny）→ IPermissionProvider（配置规则）→ IPathAccessChecker（文件系统沙箱），由窄到宽 |
| A1.5 | **模块级异常类** | `NopAiAgentException extends RuntimeException`，英文错误消息 | 设计未定义异常类。应符合 AGENTS.md 的两层异常策略（核心框架用 ErrorCode，模块内部用模块异常类），在安全文档或 glossary 中补充 |
| A1.6 | **EventPublisher 的 CopyOnWriteArrayList** | `DefaultAgentEventPublisher` 用 `CopyOnWriteArrayList` 管理订阅者，异常隔离 | 设计未描述事件发布器的线程安全策略。应补充 |
| A1.7 | **ToolSchemaConverter** | 将 XNode（tool.xdef 的 schema 节点）转换为 LLM 可理解的 `Map<String,Object>` JSON Schema | 设计中 tool-invocation.md §2 提到"Schema 翻译"但未描述实现策略。应记录这个桥接层 |
| A1.8 | **工具发现的 VFS 扫描机制** | `ToolManagerImpl.listTools()` 扫描 `/nop/ai/tools/*.tool.xml`，自动发现所有工具定义 | 设计未描述运行时工具发现机制。应在 tool-invocation.md 中补充注册-发现协议 |

### A2. 实现中的额外能力，设计应纳入

| # | 能力 | 实现位置 | 说明 |
|---|------|---------|------|
| A2.1 | **10 个 LLM Provider 配置** | nop-ai-core: default, claude, gemini, ollama, deepseek, azure, volcengine, bailian, lm-studio, free | 设计仅列出 5 个（OpenAI, Anthropic, Gemini, DashScope, Ollama）。应更新为完整的 10 个 |
| A2.2 | **4 个 LLM Dialect 实现** | `OpenAiDialect`, `AnthropicDialect`, `GeminiDialect`, `OllamaDialect` | 设计 §3.3 提到 Dialect 模式但未列举具体实现。应补充 |
| A2.3 | **per-Provider 令牌桶限流** | `ChatServiceImpl` 中的 `DefaultRateLimiter`，按 provider 配置 `rateLimit` | 设计未描述限流机制。应在 LLM 层设计中补充 |
| A2.4 | **Streaming 支持（Java 9 Flow API）** | `IChatService.callStream()` → SSE → `SubmissionPublisher<ChatStreamChunk>` | 设计提到 streaming 但未描述流式 API 设计。应补充 |
| A2.5 | **Anthropic Extended Thinking / Prompt Caching** | `AnthropicDialect` 处理 thinking blocks + cache tokens | 设计 §3.3.1 讨论了 Extended Thinking 但未映射到实现。应记录实现策略 |
| A2.6 | **完整的 Bash 语法解析器** | nop-ai-shell: `BashLexer` (22 token types) + `BashSyntaxParser` (recursive descent) + AST visitor | nop-ai-shell-design.md 存在但与 nop-ai-agent 设计文档无交叉引用。应建立正式关联 |
| A2.7 | **IToolCallInterceptor** | nop-ai-toolkit 的 beforeCall/afterCall 拦截器，通过 IoC collect-beans 自动收集 | 这与设计的 PRE_ACTING/POST_ACTING Hook 有重叠，但在不同的抽象层。设计应明确两者的关系 |
| A2.8 | **IToolFileSystem 沙箱抽象** | nop-ai-toolkit 的 `IToolFileSystem` + `LocalToolFileSystem`（路径验证 + 工作目录限制） | 设计中 IPathAccessChecker 做路径检查，但 IToolFileSystem 提供了更完整的沙箱。两者应协调 |

### A3. 实现中的事件类型比设计更丰富

设计的 `runtime-semantics.md` 和 `glossary.md` 定义了 7 个核心事件 + 9 个扩展事件。实现中有 11 个事件类型：

| 实现事件 | 设计对应 | 差异 |
|---------|---------|------|
| `EXECUTION_STARTED` | — | 设计未定义，应纳入 glossary |
| `ITERATION_STARTED` | — | 设计未定义，应纳入 |
| `LLM_RESPONSE_RECEIVED` | `TextChunk` / `ThinkingChunk` | 语义不同：设计区分流式块，实现用单个事件表示整个响应 |
| `TOOL_CALL_STARTED` | `ToolCallStart` | 命名不同但语义一致 |
| `TOOL_CALL_COMPLETED` | `ToolCallComplete` | 命名不同但语义一致 |
| `TOOL_CALL_DENIED` | — | **安全事件**，设计未定义。建议加入 Layer 1 事件 |
| `PATH_ACCESS_DENIED` | — | **安全事件**，设计未定义。建议加入 Layer 1 事件 |
| `EXECUTION_COMPLETED` | `AgentResult` | 命名不同但语义一致 |
| `EXECUTION_FAILED` | `AgentError` | 命名不同但语义一致 |
| `SESSION_CREATED` | — | 生命周期事件，设计未定义 |
| `SESSION_LOADED` | — | 生命周期事件，设计未定义 |

**建议**：设计应采用实现的命名风格（UPPER_SNAKE_CASE 事件枚举），并将 `TOOL_CALL_DENIED`、`PATH_ACCESS_DENIED` 纳入 glossary.md 的事件表。

---

## B. 实现应向设计看齐的缺陷（设计优于实现）

### B1. P0 级：核心架构偏差

| # | 问题 | 设计描述 | 实现现状 | 影响 |
|---|------|---------|---------|------|
| B1.1 | **无双循环模型** | 02-execution-model.md 定义外层 followUp 循环 + 内层 ReAct 循环 | `ReActAgentExecutor` 只有单个 while 循环，无 followUp 队列 | 无法处理多轮对话和外部消息注入 |
| B1.2 | **工具顺序执行** | 设计要求 parallel=true 时并行执行工具 | `ReActAgentExecutor` 用 for 循环逐个执行，即使 `ToolManagerImpl` 已支持并行 | 性能瓶颈，LLM 返回多个工具调用时串行等待 |
| B1.3 | **无 Hook 系统** | 设计定义 10 个生命周期点，是所有扩展的基础 | 零 Hook 实现，无 `before_reasoning`/`after_acting` 等回调 | Skill、内容护栏、审批门等扩展全部无法挂载 |
| B1.4 | **无错误分类** | reliability.md 定义 RETRYABLE/NON_RETRYABLE/RECOVERABLE | 单一 try-catch，任何异常直接终止执行 | 无重试、无降级、无恢复 |
| B1.5 | **deny 列表与实际工具名不匹配** | 设计要求 deny 规则匹配实际工具名 | `DefaultToolAccessChecker` 检查 `shell_exec`/`file_write`，但实际工具名是 `bash`/`write-file` | **安全漏洞**：硬编码 deny 列表对实际工具无效 |

### B2. P1 级：重要功能缺失

| # | 问题 | 设计描述 | 实现现状 |
|---|------|---------|---------|
| B2.1 | **无 Token 预算管理** | 设计要求 loop 内检查 token 预算并触发压缩 | `tokensUsed` 累加但从未检查阈值，5 层压缩管道全部未实现 |
| B2.2 | **无会话持久化** | session-and-storage.md §2 明确 Phase 1 使用 JSONL 文件 | `InMemorySessionStore` 用 ConcurrentHashMap，JVM 退出数据丢失 |
| B2.3 | **无 ICancelToken 集成** | 设计要求 context 包含 cancel token | `AgentExecutionContext` 无 cancelToken，`SimpleToolExecuteContext` 的 cancelToken 始终为 null |
| B2.4 | **无 steering 机制** | 02-execution-model.md §4 定义外部消息注入 | 不存在 steering 队列 |
| B2.5 | **无 Agent 状态机** | actor-runtime-vision.md 定义 7 状态 | `AgentExecStatus` 仅 4 状态（pending/running/completed/failed），无 idle/recovering/stopped |
| B2.6 | **无路径规范化安全** | security-and-permissions.md 要求 `..` 遍历、symlink、绝对路径逃逸防护 | `DefaultPathAccessChecker` 检查敏感目录前缀，但路径规范化依赖 Java File.getCanonicalPath()，未显式处理 symlink 和 case folding |
| B2.7 | **SimpleToolExecuteContext 使用硬编码 `new File(".")`** | 设计要求 VFS 集成和工作目录隔离 | 每次循环内创建 `new SimpleToolExecuteContext(new File("."), null, null, null)` |
| B2.8 | **Plan 字段从未使用** | 设计要求 AgentPlan 参与 ReAct 循环（exit criteria 检查等） | `AgentExecutionContext` 有 `plan` 字段但 `ReActAgentExecutor` 从不读取 |

### B3. P2 级：设计成熟但实现空白

| # | 子系统 | 设计文档 | 实现状态 |
|---|--------|---------|---------|
| B3.1 | IModelRouter（智能路由） | llm-layer.md §6 | ❌ 未实现 |
| B3.2 | ITalent（动态准入） | llm-layer.md §5 | ❌ 未实现 |
| B3.3 | IRetryPolicy（重试策略） | llm-layer.md §7 | ❌ 未实现（ChatServiceImpl 无任何重试） |
| B3.4 | ICircuitBreaker（熔断） | reliability.md §5 | ❌ 未实现 |
| B3.5 | IGoalTracker（目标跟踪） | reliability.md §6 | ❌ 未实现 |
| B3.6 | ICheckpointManager（检查点） | reliability.md §7 | ❌ 未实现 |
| B3.7 | IAuditLogger（审计日志） | security-and-permissions.md §3 | ❌ 未实现 |
| B3.8 | IApprovalGate（审批门） | security-and-permissions.md §4 | ❌ 未实现 |
| B3.9 | IContentGuardrail（内容护栏） | security-and-permissions.md §5 | ❌ 未实现 |
| B3.10 | 会话分叉 | session-and-storage.md §4 | ❌ 未实现 |
| B3.11 | 会话压缩 | session-and-storage.md §5 | ❌ 未实现 |
| B3.12 | ISkillProvider / Skill 匹配 | skill-system-design.md | ❌ 未实现（nop-ai-toolkit 的 SkillExecutor 是占位） |
| B3.13 | IToolCallRepairer（工具修复链） | context-model.md §5 | ❌ 未实现 |
| B3.14 | IContextCompactor（上下文压缩） | context-model.md §6 | ❌ 未实现 |
| B3.15 | IAiMemoryStore 实现 | 01-architecture-baseline.md §三 | ❌ 接口存在但零实现 |

---

## C. 设计与实现的不一致（双向修正）

### C1. 命名分歧

| 概念 | 设计名称 | 实现名称 | 位置 | 建议 |
|------|---------|---------|------|------|
| call-agent 参数 | `agentName` | `agentId` | call-agent.tool.xml vs call-agent-dsl.md | 统一为 `agentName`（匹配 agent.xdef 的 `name` 属性） |
| 工具并行标志 | `parallel` | `paralllel` | call-tools.xdef | 修正 xdef 拼写错误（已有修正计划） |
| 压缩阈值 | `tokenCompactionThreshold` | `tokenCompressionThreashold` | agent.xdef 字段名 | 修正为 `tokenCompactionThreshold`（同时修正 Compression→Compaction 和 Threashold→Threshold） |
| LLM 上下文长度 | `contextLength` | `contextLenth` | llm.xdef 字段名 | 修正拼写 |
| 可用技能 | `availableSkills` | `avaliable-skills.xdef` / `skil` | xdef 文件名和元素名 | 修正拼写 |
| 工具 deny 列表 | `bash`, `write-file`, `delete-file` | `shell_exec`, `file_write`, `file_delete` | DefaultToolAccessChecker | **严重**：必须修正为实际工具名 |
| 事件类型 | PascalCase（TextChunk） | SCREAMING_SNAKE_CASE（TOOL_CALL_STARTED） | AgentEventType 枚举 | 统一为 SCREAMING_SNAKE_CASE（符合 Java 枚举约定） |
| 会话存储接口 | `ISessionManager` | `ISessionStore` | session-and-storage.md vs session 包 | 统一为 `ISessionStore`（实现更简洁）或升级实现为设计描述的 `ISessionManager` |

### C2. 语义分歧

| # | 问题 | 设计描述 | 实现行为 | 建议 |
|---|------|---------|---------|------|
| C2.1 | **系统 prompt 插入位置** | react-engine.md 说 "system prompt 只在 created→ready 时构建一次" | `DefaultAgentEngine` 每次调用都重新插入 system prompt，且插在 session 历史消息之后 | 修正：system prompt 应在首次构建时插入到消息列表头部 |
| C2.2 | **异步执行模型** | baseline.md 说 Actor 在 Virtual Thread 上执行，挂起时释放 | `ReActAgentExecutor` 同步执行后包装为 `CompletableFuture.completedFuture()`，`DefaultAgentEngine` 再包 `supplyAsync(commonPool)` | 移除冗余包装，未来迁移到 Virtual Thread |
| C2.3 | **PermissionProvider 状态管理** | 设计说三源合并应是"无状态解析" | `DefaultPermissionProvider.configure()` 修改实例状态（agentPermissions, sessionPermissions） | 改为构造函数注入或 `resolve()` 方法接收上下文参数 |
| C2.4 | **Agent 与 AgentModel 的关系** | baseline.md §四 明确区分 Agent（无状态执行体）和 AgentModel（配置对象），并记录了"为什么 Agent 是配置对象而非执行体"的设计决策 | 实现中 `ReActAgentExecutor` 既是执行逻辑又持有服务引用，本质上是"Agent as Executor"模式 | 可接受（设计决策中的 rejected alternative 恰好是当前实现方式），但应在设计中承认实现的务实选择 |
| C2.5 | **两套 Plan 模型** | 设计中 AgentPlan（agent-plan.xdef）是唯一的计划模型 | 实现有两套：`io.nop.ai.agent.plan.model`（21 类，847 行根类）和 `io.nop.ai.agent.model`（4 类，277 行根类），均生成自同一 xdef | 设计应解释两套模型的分工：rich model 用于持久化/展示，simple model 用于运行时上下文 |
| C2.6 | **AgentExecStatus 枚举值域** | actor-runtime-vision.md 定义 7 状态（created/ready/running/idle/failed/recovering/stopped） | 实现仅 4 状态（pending/running/completed/failed），且 nop-ai-coder 的 ai-plan.xdef 使用 5 值枚举（not_started/in_progress/completed/failed/skipped） | 应在设计中明确：Layer 1 用 4 值，Actor 层扩展到 7 值，ai-plan 用独立枚举 |
| C2.7 | **nop-ai-shell 与 agent 的关系** | nop-ai-shell-design.md 存在但无交叉引用 | nop-ai-shell 完全独立，零依赖 nop-ai-toolkit | 设计应明确 shell 在 agent 生态中的角色（潜在的工具桥接） |

### C3. 结构分歧

| # | 问题 | 设计结构 | 实现结构 |
|---|------|---------|---------|
| C3.1 | **事件类型设计** | glossary.md 分"核心事件 Layer 1（7 个）+ 扩展事件 Layer 2-4（9 个）" | `AgentEventType` 枚举包含 11 个值，混合了核心、安全和生命周期事件 |
| C3.2 | **Session 持久化层次** | session-and-storage.md 设计了 JSONL → 快照 → DB 三阶段 | `InMemorySessionStore` 仅 ConcurrentHashMap |
| C3.3 | **安全层** | security-and-permissions.md 设计 4 层（Core → Policy → Governance → Platform），IAuditLogger 在 Layer 1 | 实现仅 Layer 1 的 3 个接口（IPermissionProvider, IToolAccessChecker, IPathAccessChecker），无 IAuditLogger |
| C3.4 | **工具并行执行位置** | 设计要求 agent 引擎层处理并行 | `ToolManagerImpl` 已实现并行，但 `ReActAgentExecutor` 逐个调用 `callTool()` 绕过了并行能力 |

---

## D. XDEF Schema 拼写错误清单

以下拼写错误同时存在于 schema 和生成的代码中，需要 xdef 层面修正：

| Schema | 字段 | 当前 | 正确 | 影响范围 |
|--------|------|------|------|---------|
| `agent.xdef` | constraints 属性 | `tokenCompressionThreashold` | `tokenCompactionThreshold` | `_AgentConstraintsModel.java`, 所有 `.agent.xml` |
| `call-tools.xdef` | 根属性 | `paralllel` | `parallel` | `_AiToolCalls.java`, `ToolManagerImpl.java` |
| `llm.xdef` | model 子元素属性 | `contextLenth` | `contextLength` | `_LlmModelModel.java`, 所有 `.llm.xml` |
| `avaliable-skills.xdef` | 文件名 + 元素名 | `avaliable` / `skil` | `available` / `skill` | 整个文件 |

---

## E. 实现质量评估

### E1. 实现中的好模式（推荐保留并在设计中推广）

1. **不可变值类型**：`Permission`, `PathAccessResult`, `ToolAccessResult`, `AgentEvent`, `AgentExecutionResult` 均为不可变对象 + 静态工厂方法。清晰、安全、易测试。

2. **伸缩构造函数 + AllowAll 默认**：引擎类提供 5 级构造函数重载，最简形式只需核心依赖。`AllowAll*` 类作为 null object 模式的标准实现。

3. **三层安全检查顺序**：硬编码 deny → 配置规则 → 路径沙箱，由窄到宽。设计应将此顺序明确为规范。

4. **模块级异常**：`NopAiAgentException` 符合 AGENTS.md 的"模块内部用模块异常类"策略。

5. **CopyOnWriteArrayList 事件发布**：线程安全、异常隔离。

6. **XDSL 工具发现**：`ToolManagerImpl` 扫描 VFS 自动发现工具，IoC collect-beans 自动收集拦截器。这是 Nop 平台特性的正确利用。

### E2. 实现中的坏模式（需要修正）

1. **deny 列表名称不匹配**（P0 安全问题）：`DefaultToolAccessChecker` 的 `Set.of("shell_exec", "file_write", ...)` 与实际工具名 `bash`, `write-file` 完全不匹配。**这是有效的安全漏洞**。

2. **系统 prompt 插入位置错误**：`DefaultAgentEngine.doExecute()` 先复制 session 历史，后插入 system prompt，导致 system prompt 出现在用户消息之后。LLM 通常期望 system prompt 在消息列表头部。

3. **冗余的异步包装**：`ReActAgentExecutor.execute()` 返回 `CompletableFuture.completedFuture()`，`DefaultAgentEngine` 再包 `supplyAsync(commonPool)`。要么真正异步，要么简化为同步。

4. **SimpleToolExecuteContext 使用硬编码 `new File(".")`**：每次循环迭代创建新的 context，工作目录是 JVM 当前目录而非 agent 配置的工作目录。

5. **PermissionProvider 可变状态**：`configure()` 方法修改实例状态，同一个 provider 实例无法安全地被多个 agent 共享。

6. **Plan 字段完全未使用**：`AgentExecutionContext.plan` 被设置但 `ReActAgentExecutor` 从不读取。这是死代码。

### E3. nop-ai-core 实现亮点

1. **Dialect 模式**：`ILlmDialect` 接口 + 4 实现是设计中的 ILlmDialect 概念的忠实实现。每个 Provider 的格式差异封装在独立的 Dialect 类中。

2. **10 个 Provider 配置**：超出设计预期。通过 XDSL 的 Delta 继承（如 deepseek extends default, azure extends default）实现配置复用。

3. **Streaming 完整实现**：Java 9 Flow API + SSE 解析 + 4 种 Dialect 的流式协议处理。

4. **per-Provider 令牌桶限流**：虽然设计未提及，但这是生产必备。

### E4. nop-ai-toolkit 实现亮点

1. **19 个工具执行器**：完整的文件系统操作 + bash + HTTP + GraphQL + search + agent 调用工具。

2. **IToolFileSystem 沙箱抽象**：`normalizePath()` + `isPathAllowed()` 提供了设计描述的路径沙箱能力。

3. **IToolCallInterceptor**：beforeCall/afterCall 拦截器，通过 IoC 自动收集。这是设计中 Hook 系统在工具层的轻量实现。

4. **XDSL 工具定义**：每个工具有 `.tool.xml` 定义（schema + description + examples），作为 AI 可见的契约。这是设计未充分描述的好模式。

### E5. nop-ai-shell 实现亮点

1. **完整的 Bash 语法解析器**：22 种 token 类型 + 递归下降解析器 + AST visitor。独立于 agent 系统但可桥接。

2. **并发管道执行**：`BlockingQueueShellOutput` 连接管道阶段，自然背压。

3. **ICommandChecker 预检**：AST 级别的安全检查。设计的安全系统可借鉴这个层次。

---

## F. 统计摘要

### F1. 设计 vs 实现覆盖矩阵

| 设计文档 | 设计特性未实现 | 实现额外能力 | 严重分歧 |
|---------|-------------|------------|---------|
| 01-architecture-baseline.md | 12 | 5 | 无 Actor 系统、无 VFS、无 Memory |
| 02-execution-model.md | 7 | 3 | 无双循环、无 Hook、无并行工具 |
| nop-ai-agent-react-engine.md | 6 | 0 | 无 cancel token、无 steering |
| nop-ai-agent-security-and-permissions.md | 10 | 3 | deny 列表不匹配实际工具名 |
| nop-ai-agent-session-and-storage.md | 10 | 2 | 无持久化、无 fork、无压缩 |
| nop-ai-agent-llm-layer.md | 7 | 3 | 无重试、无路由、无 token 估算 |
| 04-tool-invocation.md | 4 | 4 | 无并行执行（引擎层绕过） |
| nop-ai-agent-context-model.md | 5 | 1 | 无上下文继承 |
| nop-ai-agent-reliability.md | 11 | 0 | 零可靠性特性 |
| nop-ai-agent-runtime-semantics.md | 5 | 3 | Hook 未实现、Plan 未使用 |
| **合计** | **77** | **27** | — |

### F2. 实现成熟度评分

| 维度 | 设计完整度 | 实现完整度 | 一致性 |
|------|-----------|-----------|--------|
| 核心 ReAct 循环 | 10/10 | 7/10 | 高 |
| 安全权限 | 10/10 | 3/10 | 中（但 deny 列表有 bug） |
| 会话管理 | 10/10 | 2/10 | 低 |
| Hook/Skill 系统 | 10/10 | 0/10 | 零 |
| LLM 层集成 | 10/10 | 6/10 | 中高 |
| 工具调用 | 10/10 | 7/10 | 中高 |
| 可靠性增强 | 10/10 | 0/10 | 零 |
| 多 Agent 协同 | 10/10 | 0/10 | 零 |
| DSL 定义 | 10/10 | 9/10 | 高（有拼写错误） |

---

## Conclusion

### 核心发现

1. **实现是一棵强壮的树苗，设计是一片茂密的森林规划图。** 实现正确完成了 Layer 1 的核心骨架（ReAct 循环、三层安全、事件发布、模型加载），但设计的四层架构中 Layer 2-4 完全空白。

2. **最严重的问题不是"未实现"，而是"实现与设计的关键语义不一致"。** deny 列表名称不匹配是安全问题；系统 prompt 插入位置错误影响 LLM 行为；Plan 字段完全不使用是死代码。

3. **实现中的好模式应反向影响设计。** 不可变值类型、AllowAll 透传、三层安全顺序、模块级异常、事件类型命名等，都是设计应吸纳的模式。

4. **nop-ai-core 和 nop-ai-toolkit 的成熟度远高于 nop-ai-agent。** LLM Dialect 系统（4 Dialect + 10 Provider + Streaming）和工具系统（19 工具 + XDSL 发现 + 并行执行）的实现质量值得设计文档学习。

### 建议的下一步

1. **P0（立即修复）**：修正 deny 列表工具名、修正 system prompt 插入位置、修正 XDEF 拼写错误
2. **P1（近期改进）**：设计吸纳实现的好模式（值类型、事件命名、构造模式）；实现补充 cancel token 和 token 预算检查
3. **P2（持续对齐）**：按 Layer 2 → Layer 3 → Layer 4 顺序逐步实现设计特性，每层完成后双向验证

## Open Questions

- [ ] `Agent` 概念是否需要独立的类（设计说需要），还是 `ReActAgentExecutor` 就够了（实现做法）？
- [ ] `IToolCallInterceptor`（toolkit 层）与 PRE_ACTING/POST_ACTING Hook（agent 层）的关系如何定义？
- [ ] 两套 Plan 模型（rich vs simple）的长期策略：合并还是保持分工？
- [ ] nop-ai-shell 是否应作为 nop-ai-agent 的正式工具桥接层？
- [ ] 事件类型命名应统一为 PascalCase（设计）还是 SCREAMING_SNAKE_CASE（实现）？

## References

- `ai-dev/design/nop-ai-agent/01-architecture-baseline.md` — 架构基线
- `ai-dev/design/nop-ai-agent/02-execution-model.md` — 执行模型
- `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` — ReAct 引擎
- `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` — 安全权限
- `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md` — 会话存储
- `ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md` — LLM 层
- `ai-dev/design/nop-ai-agent/04-tool-invocation.md` — 工具调用
- `ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md` — 上下文模型
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` — 可靠性
- `ai-dev/design/nop-ai-agent/nop-ai-agent-runtime-semantics.md` — 运行时语义
- `ai-dev/design/nop-ai-agent/glossary.md` — 术语表
- `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` — 路线图
