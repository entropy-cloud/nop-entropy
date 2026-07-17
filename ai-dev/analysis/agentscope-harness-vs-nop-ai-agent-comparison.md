# AgentScope Harness vs Nop AI Agent — 修正对比分析

> 分析日期: 2026-07-17
> 范围: `agentscope-java/agentscope-harness` (HarnessAgent) vs `nop-entropy/nop-ai/nop-ai-agent` (DefaultAgentEngine)

---

## 0. 纠正：上一版的关键错误

| 错误 | 正确 |
|------|------|
| 说 `HarnessAgent` 通过"composition"包装 `ReActAgent` | 实际是 **继承**：`HarnessAgent extends ReActAgent`。`ReActAgent` 是 ~7700 行的核心类，包含完整的 ReAct 循环实现 |
| 说 AgentScope middleware 是"外层拦截" | 实际是 **onion 模型嵌入 ReAct 循环内部**：`onAgent` 包裹整个 call，`onReasoning` 包裹每次迭代的消息准备，`onActing` 包裹工具执行，`onModelCall` 包裹 LLM API 调用。这与 Nop 的 `IAgentMiddleware` 洋葱模型高度相似 |
| 把 `HarnessAgent` 和 `DefaultAgentEngine` 直接对标 | 两者根本不是同一层东西。`HarnessAgent` 是一个 **builder 组合出的最终 Agent 实例**（～5800行，含 builder），`DefaultAgentEngine` 是一个 **Runtime Engine 容器**（～3600行），按需加载 `AgentModel` 并动态装配组件 |

---

## 1. 核心架构差异：Builder 组合 vs 运行时容器

### AgentScope Harness — Builder 组合模式

```
HarnessAgent.Builder (Java fluent builder, ~15 配置域)
  │
  │  编程式配置所有组件（编译期决定）
  │
  ├── model / system prompt / agentId / maxIters
  ├── workspace(Path) + filesystem(Spec)
  ├── sandboxSpec / sandboxManager
  ├── subagent declarations + factories
  ├── skill repositories + curator config
  ├── plan mode
  ├── tools / MCP / tools.json
  ├── compaction / memory config
  ├── messageBus / asyncToolRegistry
  ├── middleware list (用户自定义 + 17 个内置)
  └── hooks list
       │
       └── build()
              │
              ├── 1. resolveFilesystem → AbstractFilesystem
              ├── 2. build WorkspaceManager
              ├── 3. build WorkspaceMessageBus + AsyncToolRegistry
              ├── 4. build SandboxManager + SandboxContext
              ├── 5. build MemoryFlushManager + MemoryConsolidator
              ├── 6. load subagent declarations
              ├── 7. build TaskRepository + DefaultAgentManager
              ├── 8. configure skill repositories (4-layer compose)
              ├── 9. install 17 harness middlewares (ordered list)
              ├── 10. new HarnessAgent(delegate=inner ReActAgent, ...)
              └── 11. agent.initAgent()   ← post-construction init
```

### Nop AI Agent — 运行时容器模式

```
agent.xml (XDSL + XDEF schema)
  │
  │  声明式定义（运行时加载）
  │
  ├── name / description / tagSet
  ├── chatOptions (provider, model, temperature)
  ├── tools / activeTags / denyTags / denyTools
  ├── availableSkills / requiredSkills
  ├── permissions / constraints
  ├── path-rules
  ├── prompt (prompt-syntax 模板)
  ├── hooks (event → XPL function)
  ├── middlewares
  └── team / team-member
       │
       └── ResourceComponentManager.loadComponentModel("...agent.xml")
              │
              └── AgentModel (Java bean)
                     │
                     v
              DefaultAgentEngine.doExecute(request, sessionId)
                     │
                     ├── loadAgentModel(agentName) → AgentModel
                     ├── sessionStore.getOrCreate(sessionId) → AgentSession
                     ├── buildBaseExecutionContext(agentModel, session)
                     ├── resolveExecutor(agentModel, checker...) → IAgentExecutor
                     │     └── ReActAgentExecutor.Builder
                     │           .hookRegistry(fromAgentModel)  ← 动态从 XML 加载 hooks
                     │           .toolManager(...)
                     │           .allSecurityComponents(...)
                     │           .build()
                     ├── 获取跨进程锁
                     ├── supplyAsync(agentExecutor.execute(ctx))
                     └── return AgentExecutionResult
```

**关键区别**：AgentScope 的中间件链在 **build()** 时全部确定并固定；Nop 的 hook/middleware 在 **运行时** 从 `agent.xml` 加载并通过 `IHookRegistry` 动态注册。

---

## 2. 中间件系统对比：两个 Onion

### AgentScope: `MiddlewareBase`（5 个 hook 点）

```java
interface MiddlewareBase {
    // Onion layer 1: wraps the ENTIRE agent call
    default Function<Mono<Msg>, Mono<Msg>> onAgent() { return next -> next; }

    // Onion layer 2: wraps each REASONING step (before LLM)
    default Function<Flux<Msg>, Flux<Msg>> onReasoning() { return next -> next; }

    // Onion layer 3: wraps each ACTING step (tool execution)
    default Function<Flux<Msg>, Flux<Msg>> onActing() { return next -> next; }

    // Onion layer 4: wraps the raw MODEL API call
    default Function<Mono<Msg>, Mono<Msg>> onModelCall() { return next -> next; }

    // TRANSFORMER: pipeline-style string transformation of system prompt
    default String onSystemPrompt(String prompt) { return prompt; }
}
```

执行顺序（`ReActAgent.doCallInner`）：
```
onAgent (outermost)
  └── for each iteration:
        onReasoning → inject context, expand @paths, inject subagent specs
          └── onModelCall → trace, actual LLM call
        parse response
        for each tool_use:
          onActing → plan mode intercept, async offload, eviction
  └── (post-call ops: memory flush, maintenance, curator scheduling)
```

### Nop: `IAgentMiddleware` + `IAgentLifecycleHook`（13+ hook 点）

```java
interface IAgentMiddleware {
    HookResult execute(HookContext ctx, MiddlewareChain next);
}

interface IAgentLifecycleHook {
    HookResult onEvent(HookContext ctx);
}
// HookResult: PassResult | VetoResult | ReenterResult

enum AgentLifecyclePoint {
    PRE_CALL, POST_CALL,
    PRE_REASONING, POST_REASONING,
    PRE_ACTING, POST_ACTING,
    ON_ERROR, PRE_COMPACT, POST_COMPACT,
    BEFORE_TOOL_RESULT_PROCESSED, AFTER_TOOL_RESULT_PROCESSED,
    REASONING_CHUNK
}
```

Nop 的 `MiddlewareChain` 实现：
```
MiddlewareChain.proceed(ctx):
  if index < middlewares.size():
    middlewares.get(index).execute(ctx, chain.next())
  else:
    hooks.forEach(hook.onEvent(ctx))
```

| 维度 | AgentScope | Nop |
|------|-----------|-----|
| Hook 点数量 | 5（onAgent/Reasoning/Acting/ModelCall + onSystemPrompt） | 13（PRE/POST 配对 + 工具粒度 + compact + error） |
| 粒度 | 较粗（iteration 级、tool 级） | 更细（reasoning 前后、acting 前后、工具结果处理前后） |
| Onion 机制 | 函数式 chain + 手动 compose（`Function<T, T>` 链） | 显式 `MiddlewareChain` + `next.proceed()` |
| Veto | 无（需要 throw 异常中止） | 有（`VetoResult` 可优雅中止当前点） |
| Reenter | 无 | 有（`ReenterResult` 请求从外层重新进入） |
| 数据共享 | 通过 AgentState | 通过 HookContext.data Map |
| 声明式加载 | builder 编程式添加 | XDSL `<hooks>` + `<middlewares>` 元素 |

---

## 3. 执行流程对比

### AgentScope Harness 完整调用链

```
User: agent.call(List<Msg>, RuntimeContext)
  │
  ├── HarnessAgent.ensureSessionDefaults(ctx)
  │     └── 注入 sessionId / SandboxContext / Filesystem / WorkspaceManager
  │
  ├── HarnessAgent.wrappedCall(msgs, effective, supplier)
  │     └── Mono.using(
  │           acquire: sandboxLifecycleMw?.acquireForCall()
  │           body:    delegate.call(msgs, effective)
  │           release: sandboxLifecycleMw?.releaseForCall()
  │         )
  │
  └── ReActAgent.call(List<Msg>, RuntimeContext)
        │
        ├── create ReActScope (sessionId, eventSink, structured output tool, ...)
        │
        ├── ReActScope.doCallInner(msgs)
        │     │
        │     ├── [onAgent onion] : outermost wrap
        │     │     ├── SandboxLifecycleMiddleware : acquire sandbox
        │     │     ├── AgentTraceMiddleware : log start
        │     │     └── (post-call: MemoryFlush + MemoryMaintenance)
        │     │
        │     ├── REPEAT (up to maxIters):
        │     │     │
        │     │     ├── [onReasoning onion] : prepare messages for model
        │     │     │     ├── AtPathExpansion: resolve @path refs
        │     │     │     ├── WorkspaceContext: inject AGENTS.md/MEMORY.md/knowledge
        │     │     │     ├── SubagentsMiddleware: inject subagent YAML specs
        │     │     │     ├── DynamicSubagents: inject dynamic context
        │     │     │     ├── InboxMiddleware: drain async results from MessageBus
        │     │     │     ├── CompactionMiddleware: compact if over token limit
        │     │     │     ├── HarnessSkillMiddleware: inject skill catalog
        │     │     │     └── AgentTraceMiddleware: log input
        │     │     │
        │     │     ├── [onModelCall onion] : call LLM
        │     │     │     ├── AgentTraceMiddleware: log model call
        │     │     │     └── [HTTP/model API call]
        │     │     │
        │     │     ├── parse response → ContentBlocks (text, tool_use...)
        │     │     │
        │     │     ├── FOR each tool_use:
        │     │     │     [onActing onion] : execute tool
        │     │     │       ├── PlanModeMiddleware: intercept plan tools
        │     │     │       ├── AsyncToolMiddleware: offload long-running
        │     │     │       ├── ToolResultEviction: evict oversized results
        │     │     │       ├── SkillUsageMiddleware: track usage
        │     │     │       ├── AgentTraceMiddleware: log tool call
        │     │     │       └── [actual tool execution]
        │     │     │
        │     │     └── append tool results, continue loop
        │     │
        │     ├── publish AgentResultEvent + AgentEndEvent
        │     ├── publish to message bus (subagent results)
        │     └── return final Msg
        │
        └── return Mono<Msg> to caller
```

### Nop DefaultAgentEngine 完整调用链

```
User: engine.execute(AgentMessageRequest)
  │
  ├── resolveTenantId(request)
  ├── loadAgentModel(request.getAgentName()) → AgentModel
  ├── precheckTeamDeclarations(agentModel)
  ├── sessionStore.getOrCreate(sessionId) → AgentSession
  ├── buildBaseExecutionContext(agentModel, session)
  │     ├── 构建 system prompt (from agentModel.prompt + budgeted memory)
  │     ├── replay session message history
  │     └── add user message
  │
  ├── resolveExecutor(agentModel, toolChecker, pathChecker) → ReActAgentExecutor
  │     └── Builder with all 40+ engine components
  │
  ├── pre-register CancelHandle (putIfAbsent dedup guard)
  ├── 获取跨进程 ISessionTakeoverLock
  ├── 启动 heartbeat lock renewal
  │
  ├── supplyAsync(agentExecutor.execute(ctx)) on dedicated thread pool:
  │     │
  │     ├── set tenant context on worker thread
  │     ├── session.setStatus(running)
  │     ├── bind CancelHandle to thread
  │     ├── create AgentActor (if IActorRuntime enabled)
  │     ├── autoBindTeam (if team declared)
  │     │
  │     └── ReActAgentExecutor.execute(ctx):
  │           │
  │           ├── resolve tool definitions (tag filtering)
  │           ├── consultTalents, consultSkills, consultPromptContributions
  │           ├── buildChatOptions
  │           │
  │           ├── [PRE_CALL middleware + hooks]
  │           │     └── VetoResult → return completed early
  │           │
  │           ├── sustainLoop:
  │           │     └── reactLoop (while iteration < maxIterations):
  │           │           │
  │           │           ├── check cancel, check denialLedger.isPaused
  │           │           ├── check shouldForceStop (context overflow)
  │           │           ├── goalTracker.assessGoal
  │           │           ├── check shouldTriggerCompaction
  │           │           │
  │           │           ├── [PRE_REASONING middleware + hooks]
  │           │           │
  │           │           ├── input guardrail check
  │           │           ├── budget refresh → modelRouter.route
  │           │           ├── resolve circuit-aware model
  │           │           │
  │           │           ├── LLM CALL (with retry loop):
  │           │           │     ├── circuitBreaker.allowCall
  │           │           │     ├── retry: callChatWithTimeout
  │           │           │     └── On failure: classify → retryPolicy
  │           │           │
  │           │           ├── [POST_REASONING middleware + hooks]
  │           │           ├── output guardrail check
  │           │           │
  │           │           ├── CompletionJudge.decide
  │           │           │     └── COMPLETE / CONTINUE / ESCALATE
  │           │           │
  │           │           ├── if HAS tool calls:
  │           │           │     dispatchLoop for each tool:
  │           │           │       ├── toolCallRepairer.repair
  │           │           │       ├── [L3] postDenialGuard
  │           │           │       ├── [L1] toolAccessChecker
  │           │           │       ├── [L1] permissionProvider
  │           │           │       ├── [L1] pathAccessChecker
  │           │           │       ├── [L2] securityLevel + permissionMatrix
  │           │           │       ├── [L3] approvalGate
  │           │           │       ├── [L2] writeIntentRegistry
  │           │           │       └── if denied: handleDenialAndCheckThreshold
  │           │           │             └── threshold exceeded → session PAUSED
  │           │           │     execute all allowedCalls (parallel)
  │           │           │     for each result:
  │           │           │       ├── [PRE_ACTING hooks]
  │           │           │       ├── commit tool response
  │           │           │       ├── [BEFORE_TOOL_RESULT_PROCESSED hooks]
  │           │           │       ├── checkpoint save
  │           │           │       └── [POST_ACTING + AFTER_TOOL_RESULT hooks]
  │           │           │
  │           │           └── drain steering queue (actor messages)
  │           │
  │           ├── if max iterations && still running:
  │           │     sustainer.onStop → CONTINUE (extend) or STOP
  │           │
  │           ├── [POST_CALL middleware + hooks]
  │           └── return AgentExecutionResult
  │
  ├── FINALLY:
  │     ├── remove CancelHandle
  │     ├── session.setStatus(failed if lease lost)
  │     ├── release write intents
  │     ├── clean checkpoints
  │     ├── destroy Actor
  │     ├── release takeover lock
  │     └── cancel lock renewal
  │
  └── sessionStore.save(session)
       └── return AgentExecutionResult
```

---

## 4. 关键架构差异汇总

| 维度 | AgentScope Harness | Nop AI Agent |
|------|-----------|--------------|
| **本质** | 面向代码助手场景的 **Agent 子类**，通过 Builder 组合17个中间件 | 通用 **Agent 执行引擎**，运行时加载 AgentModel 并装配 ~35 个可插拔组件 |
| **Agent 定义** | Java Builder 编程式（编译期） | XDSL XML 声明式（运行时加载） |
| **继承关系** | `HarnessAgent extends ReActAgent` | `DefaultAgentEngine implements IAgentEngine`（独立，不继承 Agent） |
| **组件注入** | Builder 方法链 + 构造函数参数 | IoC setter 注入 + 构造函数（NopIoC） |
| **中间件** | 5 个 onion 点的 `Function<T,T>` 链 | 13 个生命周期点的 `MiddlewareChain` + `HookResult` |
| **Veto/中止** | throw 异常 | `VetoResult` 优雅且可恢复的 `ReenterResult` |
| **安全模型** | 基础路径/工具过滤 + PermissionMode | 4 层防御纵深（含 approval gate + denial ledger + post-denial guard） |
| **会话** | `AgentState` per-slot JSON 文件 | `AgentSession` + `ISessionStore`（InMemory/File/DB 三种） |
| **崩溃恢复** | 无显式机制 | Checkpoint + `restoreSession` + `restorePendingSessions` |
| **跨进程锁** | `SessionTurnGate`（仅单进程互斥） | `ISessionTakeoverLock`（跨进程 + heartbeat 续租） |
| **异步工具** | `AsyncToolMiddleware` + `MessageBus` 投递 | 工具执行超时（`toolTimeoutMs`） |
| **多通道** | `Gateway` + `Channel` + `ChannelRouter` 路由 | 无 |
| **团队** | 子 agent 任务系统（subagent factories + task repository） | `ITeamManager` + `ITeamTaskStore` + DAG 任务流 |
| **技能** | `SkillCurator` + `SkillPromoter`（自动学习/策展） | `availableSkills` + `requiredSkills`（声明式） |
| **文件系统抽象** | 5 种实现（Local/Remote/Sandbox/Overlay/Composite） | 无（工具直接操作路径 + `IPathAccessChecker`） |
| **沙箱** | DockerSandbox（完整生命周期 + 快照） | `ISandboxBackend` 仅接口 |

---

## 5. 值得参考的核心设计

### AgentScope → Nop

| 设计 | 为什么值得参考 |
|------|-------------|
| **`MiddlewareBase` 的 5 个 onion 点**（onAgent/Reasoning/Acting/ModelCall） | Nop 已有 13 个 hook 点但不冲突——AgentScope 证明少量但精准的拦截点足够覆盖大部分代码助手场景。Nop 可比较两者的 hook 点设计取舍 |
| **`AsyncToolMiddleware` + `MessageBus` 的异步工具执行模式** | 工具执行时若超时，注册 `AsyncToolRecord` 到 `WorkspaceAsyncToolRegistry`，返回 placeholder，后续 iteration 通过 `InboxMiddleware` 收取结果。Nop 目前只有同步工具超时 |
| **`FilesystemTool` 统一注册设计** | 一个 class 批量注册 read/write/glob/grep/edit 等所有文件操作工具。Nop 当前可能为每个文件操作定义独立工具 |
| **`InboxMiddleware` 的消息投递模式** | 异步结果通过 `MessageBus.inboxPush → inboxDrain` 在 reasoning 前注入。Nop 可参考实现 agent 间的延迟消息 |
| **`ToolResultEvictionMiddleware`** | 当 tool result 超长时写入文件系统替代内联，防止 context 膨胀。Nop 有 context compaction 但缺少 result-level 的按需驱逐 |
| **`WorkspaceContextMiddleware` 的模板化 system prompt 注入** | 将 AGENTS.md/MEMORY.md/knowledge 结构化注入到 prompt，带 token budget 截断。这是代码助手场景验证过的 prompt 工程实践 |

### Nop → AgentScope

| 设计 | 说明 |
|------|------|
| **`VetoResult` / `ReenterResult` 的 hook 结果模型** | AgentScope 的 middleware 无中断/重入语义（只能 throw），Nop 的设计更优雅 |
| **`HookContext.data` 共享机制** | Nop 的 hook 之间通过 `HookContext.data` Map 共享数据，AgentScope 依赖 `AgentState` 全局状态 |
| **`IHookRegistry` 的动态加载** | XML 声明的 hook 在运行时注册，AgentScope 的 middleware 全部在 build() 时固定 |
| **`ISessionStore` 的三种实现 + crash recovery** | AgentScope 只有 per-slot JSON 文件，无恢复语义 |
| **4 层安全纵深** | AgentScope 缺乏 `IApprovalGate`/`IDenialLedger`/`IPostDenialGuard` 等精细的安全控制 |
| **`CompletionJudge` + `GoalTracker` + `Sustainer` 的 loop 控制** | AgentScope 的 ReAct 循环终止判断较简单 |

---

## 6. 一句话总结

**AgentScope Harness 是一个"代码助手专用 Agent 子类 + Builder"**，面向文件操作密集型场景，有丰富但专一的中间件生态。**Nop AI Agent 是一个"通用 Agent 执行引擎"**，组件更可替换、安全体系更完整、运行时更健壮，但缺乏代码助手场景的专有抽象。

两者架构完全不在同一层面——**Harness 是 Agent 实例的构建器**，**DefaultAgentEngine 是 Agent 实例的运行时容器**。最直接的比较对象是 AgentScope 的 `ReActAgent`（核心 ReAct 循环）与 Nop 的 `DefaultAgentEngine` + `ReActAgentExecutor`，而 `HarnessAgent` 只是前者之上的场景化封装。
