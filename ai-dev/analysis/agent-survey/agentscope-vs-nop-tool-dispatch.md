# AgentScope vs Nop AI Agent: Tool Dispatch Pipeline 对比

## 1. 整体 Tool Dispatch 流程

### AgentScope Java

`LLM response(ToolUseBlock)` → `ReActAgent.acting()` → `PermissionEngine.evaluatePermissions()` (ALLOW/DENY/ASK 门禁) → `MiddlewareChain.onActing()` 包裹实际 dispatch → `Toolkit.callTools()` → `ToolExecutor.executeAll()` → 按 tool 分别 `executeCore()` (lookup + schema validation + context merge) → `AgentTool.callAsync()` → `ToolResultBlock` → 经 `ToolResultMessageBuilder` 加入上下文。

- **Tool 注册**：编程式。`Toolkit.registerTool(object)` 扫描 `@Tool` 注解，或直接用 `Toolkit.registerAgentTool(AgentTool)`。tools 存放在 `ToolRegistry`。
- **Tool 发现**：`Toolkit.getToolSchemas()` 从注册的 tools 生成 JSON Schema，供 LLM 作为 tool definitions。
- **执行引擎**：Reactor (`Mono`/`Flux`)。`ToolExecutor.executeCore()` 处理 lookup、group 激活检查、schema 验证、预设参数合并、runtime context 合并，最后 `tool.callAsync()`。
- **并行/串行**：由 `ToolkitConfig.isParallel()` 控制。`ToolExecutor.executeAll()` 按 `ToolBase.isConcurrencySafe()` 分区，安全 tools 并发（`Flux.mergeSequential`），不安全 tools 串行。
- **基础设施层**：`ToolExecutor.executeWithInfrastructure()` 包含 `applyScheduling()` (boundedElastic)、`applyTimeout()` (Reactor timeout)、`applyRetry()` (Reactor retryWhen with backoff)、`applyShutdownGuard()`。

### Nop AI Agent

`LLM response(ChatAssistantMessage with tool_calls)` → `ReActAgentExecutor.reactLoop()` dispatch loop → `toolCallRepairer.repair()` → **4 安全层** (L1: access+permission+path, L2: security matrix, L3: approval+denial, L2-13a: conflict) → `toolManager.callTool()` → `IToolCallInterceptor.beforeCall()` → `IToolExecutor.executeAsync()` → `IToolCallInterceptor.afterCall()` → `AiToolCallResult` → `ChatToolResponseMessage` → 加入 `ctx.getMessages()`。

- **Tool 注册**：声明式。tools 定义在 `*.tool.xml` 文件中，位于 `/nop/ai/tools/` VFS 路径。`ToolManagerImpl.listTools()`/`loadTool()` 通过 `ResourceComponentManager` 加载。运行时 executor 由 `IToolExecutorProvider` 解析。
- **Tool 发现**：`ToolManagerImpl.listTools()` 扫描 VFS 下所有 `*.tool.xml`，返回 `AiToolModel` 实例。
- **执行引擎**：`CompletableFuture` 异步。`ToolManagerImpl.callTool()` 运行 interceptors → 查 `IToolExecutor` → `executeAsync()` → interceptors after。
- **并行/串行**：由 `AiToolCalls.getParallel()` 控制。`ToolManagerImpl.callTools()` 用 `CompletableFuture` 组合 fan-out 或 chain。
- **基础设施层**：超时在 ReAct loop 层（`toolTimeoutMs` via `CompletableFuture.orTimeout()`），重试在 LLM call 层（非 per-tool），circuit breaker 在外层。

---

## 2. Middleware/Hook 的 Tool 拦截方式

### AgentScope

- **Middleware onActing**：`onActing` hook 包裹整个 tool 执行。Middlewares 接收 `ActingInput(List<ToolUseBlock>)`，可以**修改 tool 列表**、**短路**（返回 stop event）、或**观察**结果（通过 Flux stream）。
- **Hook**（独立于 middleware）：`Hook` 接口有 `PreCallEvent`、`PostCallEvent`、`PostActingEvent`。Hooks 是 observer，不修改流程。
- **PermissionEngine**：在 `onActing` middleware chain 内部执行，但属于 `actingStream()` 核心逻辑的一部分。

### Nop

- **Middleware**：`IAgentMiddleware` 定义 `execute(HookContext, MiddlewareChain next)`。Middlewares 可以 `next.proceed(ctx)` 继续，或返回 Veto/Reenter 短路/重启。
- **Dual-track**：`IAgentMiddleware` (flow control) + `IAgentLifecycleHook` (observer)。Hooks **运行在 middleware chain 核心内部**。
- **8+ 生命周期点**：PRE_CALL/PRE_REASONING/POST_REASONING/PRE_ACTING/POST_ACTING/BEFORE_TOOL_RESULT_PROCESSED/AFTER_TOOL_RESULT_PROCESSED/POST_CALL/PRE_COMPACT/ON_ERROR。
- **安全层独立于 middleware**：4 层安全 pipeline 在 dispatch loop 中，不在 middleware 中。每层有可插拔接口（IToolAccessChecker/IPermissionMatrix/IApprovalGate/IConflictStrategy 等）。

---

## 3. 关键架构差异

| 维度 | AgentScope | Nop |
|------|-----------|-----|
| **Reactive 框架** | Project Reactor (Mono/Flux) | CompletableFuture |
| **Tool 定义** | 编程式 Java (@Tool, AgentTool) | 声明式 XML (*.tool.xml on VFS) |
| **Tool 注册** | ToolRegistry (内存 ConcurrentHashMap) | IToolExecutorProvider (按名解析) + VFS 模型加载 |
| **Identity** | AgentTool instance | IToolExecutor instance |
| **Middleware vs Security** | Permissions 在 onActing middleware chain 内 | Security 是独立的多层 pre-dispatch pipeline |
| **Agent 结构** | ReActAgent 是单体类 (4661 行), 集 builder/state/reasoning/acting/middleware/permissions | ReActAgentExecutor 是 focused executor, 通过 DI 注入协作者 |
| **结果类型** | ToolResultBlock (content blocks) | AiToolCallResult (status/output/error/exitCode) |
| **Tool 分组** | ToolGroup + ToolGroupManager, 动态激活/禁用 | AgentModel.activeTags 按 tag 过滤 |
| **Tool 修复** | 无 | IToolCallRepairer (4 阶段链: name normalization → argument cleanup → coercion → structure repair) |
| **Tool 上下文** | ToolCallParam (toolUseBlock, input, agent, RuntimeContext, ToolEmitter) | AgentToolExecuteContext implements IToolExecuteContext (workDir, envs, cancelToken, fileSystem, + engine/messenger/session/memory/team info) |

---

## 4. 各自独特之处

### AgentScope 亮点

- **Permission-as-tool-check**：每个 `ToolBase` 有 `checkPermissions()` 可覆盖。Tools 可以自评估危险程度。结合 `PermissionEngine` 的 ALLOW/DENY/ASK, 支持 HITL 审批流。
- **External tool pattern**：`SchemaOnlyTool` + `ToolSuspendException` 允许 schema 在框架内但执行在外部。LLM 调用后返回 TOOL_SUSPENDED, 由上层执行。
- **Concurrency-safe partitioning**：不盲目并行——按 `isConcurrencySafe()` 分区，写文件串行、读文件并行，`Flux.mergeSequential` 保证输出顺序。
- **Chunk callbacks**：Tool 执行可通过 `ToolEmitter` + chunk callbacks 流式返回部分结果。
- **Reactor middleware chain**：整个 middleware 基于 `Flux<AgentEvent>`, middlewares 可注入事件、修改事件流、或短路。

### Nop 亮点

- **多层次安全 pipeline**：4 层独立安全层 (L1-L4) + 冲突检测 (L2-13a)，每层有专门的可插拔接口。远超 AgentScope 的 permission 系统，接近企业安全框架。
- **Dual-track middleware + hooks**：Middlewares 控制流程 (可 Veto/Reenter), hooks 观察。8+ 细粒度生命周期点。Re-entrant hooks (BEFORE/AFTER_TOOL_RESULT_PROCESSED) 可使 ReAct loop 重新处理 tool 结果。
- **Tool call repair**：`ChainRepairer` 4 阶段修复 (name normalization → argument cleanup → coercion → structure repair)，主动修正 LLM 的 malformed tool calls。
- **Rich tool execution context**：`AgentToolExecuteContext` 携带 engine/messenger/session/memory/team 引用，tools 可完整参与多 Agent 生态。
- **Checkpoint + session 持久化**：每次 tool 执行后记录 checkpoint + 持久化 session，支持崩溃恢复。
- **Completion judge + Goal tracker + Sustainer**：ReAct loop 有复杂的退出决策逻辑。

| | AgentScope | Nop |
|---|---|---|
| **哲学** | Agent app SDK; tools as Java objects | 企业级 Agent 框架; tools as XML models |
| **Tool 调度控制** | Middleware 包裹整个 tool batch | Per-tool lifecycle hooks + 多层安全门禁 |
| **错误恢复** | Retry per tool (Reactor retryWhen) | Checkpoint/restart + circuit breaker per LLM call |
| **HITL** | ToolBase.checkPermissions() ASK + pause/resume | IApprovalGate + IDenialLedger + IPostDenialGuard |
| **并发模型** | Safe vs unsafe 分区 + Flux merge | Sequential/parallel flag on AiToolCalls |
