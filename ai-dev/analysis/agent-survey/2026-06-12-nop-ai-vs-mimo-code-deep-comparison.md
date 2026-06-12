# Nop AI Agent vs MiMoCode: 深度架构对比

> Status: open
> Date: 2026-06-12
> Conclusion:

## 一、总览

本报告从架构哲学、执行模型、Agent 定义、工具系统、会话记忆、多 Agent 编排、扩展性、安全、部署等 10 个维度，对 Nop AI Agent（nop-ai-agent，Java，Phase 1）和 MiMoCode（TypeScript，Phase 2+ 成熟度）进行深度对比。

| 维度 | Nop AI Agent | MiMoCode |
|------|-------------|----------|
| **语言/框架** | Java 21, Nop IoC, XDEF/XML DSL | TypeScript, Bun, Effect |
| **当前阶段** | Phase 1（ReAct 引擎基础版可用） | 生产级（10M+ 安装量） |
| **设计哲学** | DSL-First, 配置与执行分离, IoC/Delta | Fork from OpenCode, Effect 函数式, Actor 模式 |
| **定位** | 面向无人值守自动化的 Java Agent 引擎 | 交互式 AI 编程助手 + 工作流编排 |
| **源码规模** | ~19 modules, agent 核心 ~70 Java files | ~18 packages, single opencode ~88K TS lines |

## 二、架构哲学对比

### 2.1 配置 vs 代码

| | Nop AI Agent | MiMoCode |
|---|---|---|
| **Agent 定义** | XML DSL (`agent.xdef` → `.agent.xml`)，声明式 | TypeScript 程序式 (`createAgent()` / config object) |
| **Tool 定义** | XML (`tool.xdef` → `.tool.xml`)，VFS 加载 | TypeScript class + Zod schema / Plugin |
| **Prompt** | `<prompt>` CDATA 区 | TypeScript template literals / `.txt` imports |
| **Schema 驱动** | XDEF → Java `_gen` 代码生成 (32+ classes) | Zod / JSON Schema 运行时推导 |
| **Delta 定制** | 原生支持：Delta 层可覆盖任意 `.agent.xml` | 无 Delta 系统，通过 Plugin 覆盖 |

**分析**: Nop 秉承 DSL-First 原则，所有 agent/tool/plan 定义都是 XML DSL，通过 XDEF codegen 生成 Java 模型类。MiMoCode 遵循 TypeScript 生态的代码即配置风格。Nop 的优势在于 Delta 定制——不改源码即可覆盖任意 agent 配置；劣势是 XML 生态对开发者心智负担更高。

### 2.2 执行与状态分离

两者都采用了**配置-执行-状态三层分离**设计：

```
Nop:     AgentModel(配置) → Agent(执行体) → AgentSession(状态)
MiMoCode: Agent.Info(配置) → spawn(执行) → Session(状态)
```

Nop 明确区分 `AgentModel`（配置，从 `.agent.xml` 装载）、`Agent`（无状态执行体）、`AgentSession`（持久化状态对象）。MiMoCode 等效：`Agent.Info` 来自 `config.ts` 的 Zod schema，`actor.spawn()` 创建执行体，`Session` 管理持久化消息。

**差异**: Nop 的设计文档明确拒绝了"Agent 即执行器"模式（如 agentscope-java），而 MiMoCode 的 `Agent.Info` + `Actor.spawn` + `Session` 实际上是相同分离模式的变体。两者在此达成一致。

### 2.3 IoC/DI 风格

| | Nop AI Agent | MiMoCode |
|---|---|---|
| **容器** | Nop IoC (`@Inject`) | Effect 的 `Context.Service` + `Layer` |
| **生命周期** | IoC 容器管理 | Effect `Scope` + `Layer.fork` |
| **层叠** | Layer 叠加 (`Layer.provide` 链) | 类似，但 DSL 不同 |
| **隐式注入** | `@Inject private` 支持（避免 private field） | 显式 `yield* dep` 无隐式注入 |

两者都避免 Spring 风格的 `@Autowired private`——Nop IoC 文档明确禁用 private 字段注入，Effect 则在类型系统层面杜绝此类模式。

## 三、执行模型对比

### 3.1 核心循环

```
Nop (ReActAgentExecutor):
  while (iteration < maxIterations):
    1. build ChatRequest(messages + tools)
    2. IChatService.call(request) → ChatResponse
    3. Extract tool calls from assistant message
    4. If no tool calls → break (completed)
    5. For each tool call: checkAccess → execute
    6. Append tool results, increment iteration

MiMoCode (runAgentLoop):
  while (true):
    1. sessionPrompt.prompt() → assistant response
    2. Plugin triggerActorPreStop → decision.continue?
    3. If continue → re-run with new task
    4. If stop → break
    5. TaskGate.decide() → needReentry?
    6. If need → runTurn() again (completion gate)
    7. Plugin triggerActorPostStop → decision.continue?
    8. If continue → runTurn() again
```

**关键差异**:

| 特征 | Nop | MiMoCode |
|---|---|---|
| **循环结构** | 单层 ReAct loop | 三层：ReAct → PreStop Hook → PostStop Hook |
| **工具执行** | 同步 `CompletableFuture.allOf().join()` | Effect 异步 fork |
| **结束判定** | 无 tool calls → break | Completion Gate (Judge) + Hook 决策 |
| **Hook/Plugin** | 10 生命周期点（设计），目前 5 引擎级点 | 22 ExtensionEvent 类型 + preStop/postStop ReAct |
| **重入机制** | 无（followUp 是外部消息，不重入当前循环） | PreStop/PostStop/Completion Gate 三重重入 |
| **流式** | `Flow.Publisher<ChatStreamChunk>` 响应式流 | `AssistantMessageEventStream` |
| **Steering** | 设计文档定义，尚未实现 | followUp Queue + steering 注入 |

**分析**: MiMoCode 的循环比 Nop 复杂两个数量级。差异的根本原因在于用例不同——MiMoCode 是交互式编码助手，需要弹性的"再想想"循环 + 自动 checkpoint + 任务 gate；Nop 定位于无人值守自动化，追求循环的可预测性和终止性。Nop 的设计文档（`02-execution-model.md`）明确 reference 了 pi-agent 风格的双循环，但 Phase 1 只实现了标准 ReAct 单循环。

### 3.2 Actor 模型与 Fork 语义

两者都有子 Agent（subagent）概念，但 fork 的语义层次完全不同：

| | Nop AI Agent (设计) | MiMoCode (实现) |
|---|---|---|
| **入口** | `IAgentEngine.sendMessage(request)` → ack | `Actor.spawn(input)` → `SpawnResult` |
| **子 Agent 机制** | `call-agent` 工具 + `inheritContext` DSL | `Actor.spawn()` (mode: peer/subagent, context: none/full) |
| **Fork 语义层次** | **Session 级**——`forkSession()` 创建 child session，深拷贝消息+Plan | **运行时级**——`ForkContext` 快照父 agent 的 system prompt+tools+permission |
| **Fork 目的** | 子 Agent 获得独立上下文环境，类似 OS fork/exec | Checkpoint-writer 复用父 agent 的 LLM prefix-cache |
| **上下文继承** | `inheritContext` 明确声明消息/Plan/工具/约束的继承行为 | `contextMode`: "full"(继承消息) / "none"(不继承) |
| **父子独立** | Fork 后完全独立——修改互不影响 | ForkContext 是只读快照，父子共享 session |
| **通信** | EventPublisher + IMessageService (设计分布式) | `Deferred<AgentOutcome>` + Inbox + Bus (进程内) |
| **并行** | 设计: Coordination Bus + `IConflictStrategy` | 并行 subagent + 并行工具 |

**核心差异澄清**: Nop 的 fork 是 **Session 级 fork** (OS fork/exec 隐喻)——`inheritContext=true` + `agentName="self"` 等价于 fork，创建全新 session、深拷贝消息历史、独立 Plan 状态。MiMoCode 的 `ForkContext` 是 **运行时 prefix-cache 快照**——专为 checkpoint-writer 设计，通过快照父 agent 的 system prompt/tools/permission 实现 LLM 前缀缓存复用。两者是不同的抽象层次，解决不同的问题，不直接可比。Nop 的设计文档（`nop-ai-agent-context-model.md` §5.3）明确拒绝了 CoW、选择了深拷贝；MiMoCode 的 ForkContext 本质上是引用式快照。

从实现状态看：Nop Phase 1 只有 `CompletableFuture.supplyAsync()` 包装，fork 尚无实现；MiMoCode 的 actor 系统已深度实现（cancel/reclaim、ForkContext prefix-cache、生命周期管理），是更成熟的生产级实现。

## 四、Agent 定义 DSL 对比

### 4.1 Nop agent.xdef

```xml
<agent name="coder" tagSet="code,default">
    <description>General coding agent</description>
    <meta>{...}</meta>
    <chatOptions provider="openai" model="gpt-4.1" temperature="0.2"/>
    <tools>read-file,write-file,patch-file,call-agent</tools>
    <availableSkills>plan,review,compression</availableSkills>
    <permissions>
        <permission id="file:read" resource="file:read" action="allow"/>
    </permissions>
    <constraints maxIterations="10" toolTimeoutSeconds="300"
                 maxParallelTools="5" tokenCompactionThreshold="0.78"/>
    <prompt><![CDATA[You are a coding agent...]]></prompt>
    <hooks>
        <on id="plan-hook" event="before_reasoning">xpl-fn:(event,agentRt)=>null</on>
    </hooks>
</agent>
```

### 4.2 MiMoCode Agent.Info (Zod)

```typescript
const Info = z.object({
  name: z.string(),
  description: z.string().optional(),
  mode: z.enum(["subagent", "primary", "all"]),
  native: z.boolean().optional(),
  hidden: z.boolean().optional(),
  color: z.string().optional(),
  permission: Permission.Ruleset.zod,
  model: z.object({ modelID: ModelID.zod, providerID: ProviderID.zod }).optional(),
  modelRef: z.string().optional(),
  variant: z.string().optional(),
  prompt: z.string().optional(),
  options: z.record(z.string(), z.any()),
  steps: z.number().int().positive().optional(),
  toolAllowlist: z.array(z.string()).optional(),
})
```

### 4.3 对比

| 维度 | Nop agent.xdef | MiMoCode Agent.Info |
|------|----------------|---------------------|
| **模式** | 声明式 XML + XDEF schema | Zod schema → 运行时推导 |
| **权限** | 基于 resource+action 的显式声明 | Permission.Ruleset (通配符) |
| **工具声明** | `<tools>csv-set</tools>` 名字列表 | `toolAllowlist` 白名单数组 |
| **约束** | constraints 块 (maxIterations, timeout, parallel) | `steps` + agentType 的 permission 隐式控制 |
| **Hook** | DSL 级 `<hooks>` + XPL 表达式 | 通过 Plugin 注册 ExtensionEvent |
| **Skills** | availableSkills/requiredSkills | 通过 Plugin/Skill 系统 |
| **Prompt** | `<prompt>` CDATA 区 | `prompt` 字符串 + `.txt` import |
| **ChatOptions** | 独立的 `chat-options.xdef` 引用 | `model` + `modelRef` + `temperature` 等 |
| **模型生成** | XDEF → `_gen` Java 类 (32+) | 无代码生成 |
| **Delta 定制** | 原生支持 | 无 |

## 五、工具系统对比

| | Nop AI Agent (nop-ai-toolkit) | MiMoCode (tool/) |
|---|---|---|
| **定义格式** | XML (`tool.xdef` → `.tool.xml`), VFS 发现 | TypeScript (`Tool.InferParameters` + Zod schema) |
| **注册方式** | `IToolExecutor` SPI + interceptor 链 | Plugin 注册 + `ToolRegistry` |
| **发现路径** | VFS `/nop/ai/tools/*.tool.xml` | Plugin 静态注册 + MCP `.json` |
| **管理接口** | `IToolManager` (loadTool, listTools, callTool, callTools) | `Tool` (全局工具注册表) |
| **执行** | `IToolExecutor.executeAsync()` → `CompletableFuture` | Effect 异步 |
| **拦截器** | `IToolCallInterceptor` chain | Plugin HookEvent |
| **安全** | 3 层检查: Permission → ToolAccess → PathAccess | Permission.Ruleset + 可配置 |
| **MCP** | `nop-ai-mcp-server` | 内置 MCP client + server |
| **并行** | `CompletableFuture.allOf()` + maxParallelTools | 并行 subagent + 并行工具 |
| **内置工具** | 18+ (read/write/patch/bash/search/ask-oracle/call-agent 等) | 60+ (继承 OpenCode 全部 + actor/skill 等) |

**关键差异**:
1. **Nop 的 3 层安全检查**是独特优势：`IPermissionProvider` (功能级)、`IToolAccessChecker` (工具级)、`IPathAccessChecker` (路径级)，且每个都有 AllowAll/Default 两个实现。MiMoCode 使用简单的 Permission.Ruleset 通配符系统。
2. **MiMoCode 的工具系统**更丰富（60+ vs 18+），继承 OpenCode 生态的全部工具。
3. **Nop 的工具定义**通过 VFS 发现 + Delta 可定制，适合 Java 企业级部署；MiMoCode 的插件化注册更灵活。
4. **call-agent**: Nop 设计了 `CallAgentExecutor` + `Agent-as-Subprocess` 模式，MiMoCode 有等效的 `ActorTool` + `spawn()`。

## 六、会话与记忆系统对比

| | Nop AI Agent | MiMoCode |
|---|---|---|
| **会话持久化** | `InMemorySessionStore` (Phase 1), 设计 VFS Event Log | SQLite via Drizzle ORM |
| **消息模型** | `ChatMessage` 5 种子类 | `MessageV2.WithParts` projector 架构 |
| **上下文压缩** | 设计文档定义 5 层渐进 (Layer 0-4)，未实现 | 生产级 `compaction.ts` (543 行) |
| **记忆存储** | `IAiMemoryStore` (getAll/getLastN/search/add) | SQLite FTS5 全文索引 (root/memory/) |
| **记忆类型** | 设计文档定义 3 层：短期/工作/长期 | `MEMORY.md` + `checkpoint.md` + `notes.md` |
| **Budgeted 注入** | 无 | `readBudgeted()` + 重要性排序 |
| **Fork/Subagent** | Session 级 fork (`forkSession` + `inheritContext`), 设计未实现 | ForkContext (运行时 prefix-cache 快照), 生产级实现 |
| **Checkpoint** | 设计文档定义 (snapshot/compaction), 未实现 | 生产级 `checkpoint.ts` (1478 行) |
| **导入/导出** | 无 | Claude Code 会话导入 |

**分析**: 这是 Nop 和 MiMoCode 差距最大的领域。MiMoCode 的记忆系统是全项目最创新的部分——SQLite FTS5 索引、budgeted injection、自动 checkpoint compaction 都是生产级实现。Nop Phase 1 的 `InMemorySessionStore` 只是占位实现，真正的 VFS Event Log 持久化和 5 层渐进压缩管线在设计中但未实现。

## 七、多 Agent 编排对比

| | Nop AI Agent | MiMoCode |
|---|---|---|
| **子 Agent** | `call-agent` 工具 + `CallAgentExecutor` | `Actor.spawn()` + `ActorTool` |
| **并行** | design docs: Coordination Bus | Parallel subagent + workflow `parallel()` |
| **冲突检测** | `IConflictStrategy` + `scope_claim`/`operation_intent` | 无（写冲突由 OS file lock 兜底） |
| **工作流** | 设计：Flow 编排 (Solon Flow 风格) | QuickJS 沙箱 + TypeScript 子集 |
| **通信协议** | IMessageService (设计分布式) | Inbox + Bus (进程内) |
| **团队模式** | 设计文档定义，未实现 | team 模块 (Min 级别) |

**分析**:
1. **Nop 的 Coordination Bus** (`scope_claim`/`operation_intent`/`conflict_alert`) 是一个独创设计——通过 LLM 可见的协调消息实现主动避让 + 引擎级预警 + fail-fast 三层的冲突处理。MiMoCode 没有等效机制。
2. **MiMoCode 的 QuickJS Workflow** 是另一个创新——TypeScript 子集在沙箱中执行工作流脚本，支持 `parallel()`/`pipeline()`/`agent()`，且有重放/恢复能力。Nop 的 Flow 编排仍在设计阶段。
3. 两者的多 Agent 编排都处于不对称状态：Nop 有更清晰的协调理论模型（Coordination Bus + IConflictStrategy），但无实现；MiMoCode 有可用的子 agent + workflow，但无高级冲突处理。

## 八、安全模型对比

| | Nop AI Agent | MiMoCode |
|---|---|---|
| **权限模型** | 三层: PermissionProvider + ToolAccessChecker + PathAccessChecker | Permission.Ruleset (glob 模式) |
| **粒度** | resource + action 级别的显式声明 | `"*": "allow"` / `"write": "deny"` |
| **路径安全** | `IPathAccessChecker` 运行时路径参数扫描 | bash 工具级别的 `external_directory` 控制 |
| **工具拒绝** | `ToolAccessResult` (allowed/denied + reason) | Permission.disabled() |
| **审计** | `AgentEventPublisher` → 事件日志 | SQLite 消息持久化 |
| **沙箱** | 无（工具层面的路径检查） | QuickJS 沙箱 + permission ruleset |

**分析**: Nop 的 3 层安全检查比 MiMoCode 的 Permission.Ruleset 更结构化。特别是 `IPathAccessChecker` 在 `ReActAgentExecutor` 中的路径参数扫描（`PATH_ARG_KEYS` 检测工具参数中的 path/file/filePath/filename → 运行时检查），是 Nop 独有的安全设计。MiMoCode 没有等效的细粒度路径安全检查。

## 九、LLM Provider 抽象对比

| | Nop AI Agent (nop-ai-core) | MiMoCode (provider/) |
|---|---|---|
| **统一接口** | `IChatService` (callAsync + call + callStream) | `ai` SDK (Vercel AI SDK) |
| **Dialect/Provider** | `ILlmDialect` SPI + 4 实现 | 20+ `@ai-sdk/*` 包 |
| **Token 估算** | `AbstractLlmDialect` → jtokkit | Vercel SDK 内置 |
| **前缀缓存** | 设计文档: `prefixLength` + `prefixHash` | ForkContext + watermark |
| **重试** | 设计文档: `IRetryPolicy` + `ICircuitBreaker` | `retry.ts` 模块 |
| **OAuth** | 无 | GitHub Copilot, OpenAI Codex |
| **流式** | `Flow.Publisher<ChatStreamChunk>` | `AssistantMessageEventStream` |
| **模型配置** | `{provider}.llm.xml` via XDEF | `models.generated.ts` + provider imports |

**分析**: MiMoCode 借助 Vercel AI SDK 的 20+ `@ai-sdk/*` 包获得了远比 Nop 更广泛的 LLM provider 覆盖。Nop 的 `ILlmDialect` SPI 设计更干净（纯函数契约保证前缀缓存安全），但实现数量少。Nop 的 `prefixLength` + `prefixHash` 前缀缓存设计思路与 MiMoCode 的 forkContext + watermark 机制理念相似，但处于不同实现阶段。

## 十、可靠性设计对比

| | Nop AI Agent (设计文档) | MiMoCode (实现) |
|---|---|---|
| **重试** | `IRetryPolicy` + 指数退避 | `retry.ts` + 重试策略 |
| **熔断** | `ICircuitBreaker` (设计) | 无 |
| **Checkpoint** | `ISessionManager.createSnapshot()` (设计) | 生产级 checkpoint.ts (1478 行) |
| **Compaction** | 5 层渐进 (Layer 0-4, 设计) | 生产级 compaction.ts (543 行) |
| **超时** | toolTimeoutSeconds + maxIterations | scriptDeadlineMs + agentTimeoutMs |
| **恢复** | `RecoveryManager` (设计) | workflow `resume()` + journal |
| **预算控制** | tokenCompactionThreshold | Budgeted Injection + token 估算 |
| **中断** | `ICancelToken` | Fiber.interrupt + cancel("graceful"\|"forced") |

**分析**: MiMoCode 的可靠性基础设施远超 Nop Phase 1。Nop 的设计文档（`nop-ai-agent-reliability.md`）覆盖了完备的可靠性策略，但基本都未实现。MiMoCode 的 checkpoint + compaction + workflow resume 都是生产级实现。

## 十一、自改进与内部 Agent 化

| | Nop AI Agent | MiMoCode |
|---|---|---|
| **Dream** | 无 | 扫描历史会话 → 提取知识到 MEMORY.md |
| **Distill** | 无 | 发现重复工作流 → 打包为 skill/subagent |
| **Agent 生成** | 无 | `/generate` 根据描述生成新 agent 配置 |
| **内部 Agent 化** | §6 设计文档定义：压缩/修复/评审/Plan 调整可 Agent 实现 | Checkpoint-writer 是专用内部 agent，但无通用框架 |

**Dream/Distill**: MiMoCode 独有的自改进能力。Nop 目前没有对应设计。

**内部 Agent 化**: Nop 的设计文档（`nop-ai-agent-context-model.md` §6）描述了一个独特理念——引擎内部能力（上下文压缩、错误修复、结果评审、Plan 调整）可以通过统一的薄接口（结构化输入/输出，非自由文本 prompt）实现为 Agent。引擎不关心实现方式是硬编码逻辑还是 Agent 调用。这与 MiMoCode 的 checkpoint-writer 作为专用内部 agent 不同：Nop 的模型更抽象和通用，且明确要求接口是结构化的（不是自由文本 prompt）。这是 Phase 2 的设计重点。

## 十二、总结

### Nop AI Agent 的独特优势

1. **DSL-First + Delta 定制**: agent/tool/plan 都是 XML DSL，支持 Delta 层覆盖——这是 MiMoCode 完全不具有的能力
2. **3 层安全检查**: PermissionProvider + ToolAccessChecker + PathAccessChecker——企业级部署的关键能力
3. **Coordination Bus (设计)**: `scope_claim`/`operation_intent`/`conflict_alert` 三层冲突检测——多 Agent 编排的独到设计
4. **ILlmDialect SPI 纯函数契约**: 明确的 prefix-cache 安全保证
5. **Java / JVM 生态**: 可与 Nop IoC、Nop ORM、Nop GraphQL 深度集成——企业级应用的优势
6. **五层架构清晰**: Application → Platform → Agent Engine → LLM → Tool，边界严格

### MiMoCode 的独特优势

1. **持久记忆系统**: SQLite FTS5 + Budgeted Injection + 自动 compaction——跨会话记忆的生产级方案
2. **Actor/Subagent 系统**: 完整的子 agent 生命周期 + PreStop/PostStop ReAct 钩子
3. **Checkpoint 智能上下文**: Token pressure 检测 → 自动压缩 → 重建 → budgeted 注入
4. **QuickJS Workflow 引擎**: 沙箱隔离 + TypeScript 子集编排 + 重放/恢复
5. **Completion Gate**: Judge 模型验证任务完成，防止过早停止
6. **Dream/Distill 自改进**: 从历史自动提取知识和 skill
7. **Max Mode**: 并行 best-of-N 推理
8. **OpenCode 生态**: 20+ LLM provider、60+ 工具、MCP、LSP

### Nop AI Agent 的独特优势

1. **DSL-First + Delta 定制**: agent/tool/plan 都是 XML DSL，支持 Delta 层覆盖——这是 MiMoCode 完全不具有的能力
2. **3 层安全检查**: PermissionProvider + ToolAccessChecker + PathAccessChecker——企业级部署的关键能力
3. **Coordination Bus (设计)**: `scope_claim`/`operation_intent`/`conflict_alert` 三层冲突检测——多 Agent 编排的独到设计
4. **ILlmDialect SPI 纯函数契约**: 明确的 prefix-cache 安全保证
5. **Java / JVM 生态**: 可与 Nop IoC、Nop ORM、Nop GraphQL 深度集成——企业级应用的优势
6. **五层架构清晰**: Application → Platform → Agent Engine → LLM → Tool，边界严格
7. **内部 Agent 化 (设计)**: 薄接口结构化契约，引擎能力可 Agent 实现——独一无二的元架构设计
8. **Session 级 Fork (设计)**: OS fork/exec 隐喻 + `inheritContext` 协议——子 Agent 上下文继承语义清晰

### MiMoCode 的独特优势

1. **持久记忆系统**: SQLite FTS5 + Budgeted Injection + 自动 compaction——跨会话记忆的生产级方案
2. **Actor/Subagent 系统**: 完整的子 agent 生命周期 + PreStop/PostStop ReAct 钩子
3. **Checkpoint 智能上下文**: Token pressure 检测 → 自动压缩 → 重建 → budgeted 注入
4. **QuickJS Workflow 引擎**: 沙箱隔离 + TypeScript 子集编排 + 重放/恢复
5. **Completion Gate**: Judge 模型验证任务完成，防止过早停止
6. **Dream/Distill 自改进**: 从历史自动提取知识和 skill
7. **Max Mode**: 并行 best-of-N 推理
8. **ForkContext 实现**: 运行时 prefix-cache 快照，生产级——checkpoint-writer 复用的关键优化
9. **OpenCode 生态**: 20+ LLM provider、60+ 工具、MCP、LSP

### 核心哲学差异

| 维度 | Nop | MiMoCode |
|------|-----|----------|
| **重心** | 结构 + 安全 + 可定制 | 记忆 + 上下文 + 自改进 |
| **实现顺序** | DSL First → Engine → Persistence | Fork First → Memory → Workflow |
| **扩展性** | Delta 定制 + IoC 容器 | Plugin + Effect Layer |
| **安全性** | 内置 3 层检查 | Permission Ruleset |
| **Agent 关系** | Coordination Bus (LLM 可见) | 父子 subagent (引擎管理) |

### 对 Nop AI Agent 后续实现的启示

1. **优先实现 Session 持久化和 Checkpoint**: MiMoCode 证明这是长任务 Agent 的基石能力。Nop 已有 VFS Event Log 设计，但 `InMemorySessionStore` 需要替换
2. **Fork 语义优先实现**: Nop 的设计已是行业最完整的子 Agent 上下文继承模型（`inheritContext` 协议 + Session 级 fork），尽快实现可在这一维度建立优势
3. **Coordination Bus 是独特价值**: 其他框架（包括 MiMoCode）都没有类似设计，三层冲突检测完全是 Nop 的独创，值得作为差异化王牌
4. **Delta 定制是 Nop 王牌**: 在企业级 Agent 部署中，Delta 层覆盖 agent/tool 配置的能力是 TypeScript 框架做不到的
5. **内部 Agent 化做深**: 薄接口 + 结构化契约的理念 MiMoCode 没有，Phase 2 实现后可以成为独特竞争力
6. **3 层安全检查做深**: 企业级部署的安全诉求远高于交互式 IDE 插件
7. **不急于 Add Dream/Distill**: MiMoCode 的自改进建立在长时间运行的历史数据上，Nop Phase 1 先积累会话数据
8. **Nop 的 Fork ≠ MiMoCode 的 ForkContext**: 前者是 Session 级上下文继承（OS fork/exec），后者是运行时 prefix-cache 快照。两者解决的问题不同，Nop 不需要模仿 MiMoCode 的 ForkContext，但可以在实现 Session fork 时考虑 prefix-cache 优化

## References

- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/` — ReActAgentExecutor.java, DefaultAgentEngine.java, IAgentEngine.java
- `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/` — IChatService.java, ChatRequest.java, messages/
- `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/` — IToolManager.java, IToolExecutor.java, tools/
- `ai-dev/design/nop-ai-agent/` — 01-architecture-baseline.md, 02-execution-model.md, react-engine.md, context-model.md, multi-agent.md, session-engine.md
- `~/_tmp/ai/mimo-code/packages/opencode/src/actor/spawn.ts` — Actor system
- `~/_tmp/ai/mimo-code/packages/opencode/src/memory/service.ts` — Memory system
- `~/_tmp/ai/mimo-code/packages/opencode/src/session/checkpoint.ts` — Checkpoint
- `~/_tmp/ai/mimo-code/packages/opencode/src/workflow/runtime.ts` — Workflow engine
- `~/ai/mimo-code/packages/opencode/src/task/registry.ts` — Task system
- `ai-dev/analysis/agent-survey/2026-06-12-mimo-code-analysis.md` — MiMoCode survey
