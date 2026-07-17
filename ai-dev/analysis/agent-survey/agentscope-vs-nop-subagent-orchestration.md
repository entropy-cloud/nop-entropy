# AgentScope vs Nop AI Agent: Subagent Orchestration 对比

## 1. 子代理/层级能力

### AgentScope Java

**子代理支持成熟，工具驱动**。父代理将子代理作为 tool 调用。

**Core 层** (`agentscope-core`)：
- `SubAgentTool`：`AgentTool` 实现，包装子代理用于多轮对话。参数：`session_id` (optional, 省略→新会话) + `message` (required)。用 `SubAgentProvider` (每调用创建新 Agent 实例的工厂，因 ReActAgent 非线程安全)。

**Harness 层** (`agentscope-harness`)：
- `SubagentsMiddleware` / `DynamicSubagentsMiddleware`：在 ReActAgent 执行 pipeline 内运行，管理子代理生命周期，在 system prompt 中注入子代理使用指南。
- `AgentSpawnTool`：三个方法：
  - `agent_spawn`：生成子代理，运行任务，返回结果 (sync/async)
  - `agent_send`：向先前生成的子代理发后续消息
  - `agent_list`：列出活跃子代理
- `DefaultAgentManager`：按 `agentId` 注册 `SubagentFactory`。支持 `createAgentIfPresent` + `invokeAgent`/`invokeAgentStream`。
- **Event propagation**：`SubagentEventBus` (Reactor Context key) 允许同步调用的子代理将 Event 推送到父级 `Flux<Event>` stream。事件携带 `EventSource` (完整路径 `main/researcher/...`/depth/agentKey/sessionId)。
- **MAX_SPAWN_DEPTH = 3** 硬编码。

### Nop AI Agent

**子代理成熟，消息驱动，会话分叉**。

**Tool 层** (`CallAgentExecutor`)：
- `call-agent` tool。解析 `agentId` (经 `AgentNames.isValidIdentifier()` 路径注入保护)。
- 会话模式：**continue** (同一 sessionId) / **fork** (`forkSession`, 从父级创建子会话, 可选择继承消息) / **create new**。
- **双路径执行**：
  - *异步邮箱路径* (首选, 当有 `IAgentMessenger` 时)：发 REQUEST 信封到 `AgentMessageTopics.callAgentTopic()` → 引擎处理 → 返回 RESPONSE。
  - *分叉+执行路径* (后备)：直接 `IAgentEngine.execute()` + `orTimeout`。
- 委托深度通过 `__nopAiAgent.delegationDepth` 元数据传播，默认 MAX_DEPTH = 3。

**引擎层** (`DefaultAgentEngine`)：
- `handleCallAgentRequest`：在 `callAgentTopic` 上注册，处理 REQUEST 信封，含超时处理 + 子会话取消 (反僵尸)。

**会话分叉**：
- `ISessionStore.forkSession(parentSessionId, inheritContext, props)`：在 InMemory/FileBacked/DBSessionStore 中实现。子会话得到独立 ID + `parentSessionId`。
- 可选择继承父级消息、planId、metadata。

**Team 系统 (DAG 编配)**：
- `Team` / `ITeamManager` / `TeamSpec`：代理的逻辑组。Team 有主代理、成员、规范。
- `TeamTaskFlowOrchestrator`：在 **nop-task 运行时** (DAG 调度程序) 上运行 team 的任务 DAG。`TeamTaskGraphBuilder` 将 team 任务转换为 `GraphTaskStepModel`，含拓扑排序、循环检测、依赖排序执行。
- 任务可以有 `blockedBy` 依赖项。任务类型：`MemberAgentTaskStep` (用已绑定的 member) 或 `SpawnMemberAgentTaskStep` (运行时通过 `IMemberSpawner` 生成)。

---

## 2. 代理间通信

### AgentScope

- **Reactive Streams** (Reactor)：Agent 有 `Flux<Event> stream()` + `Mono<Msg> call()`。子代理事件经 `SubagentEventBus` 推送到父级流。
- **Observer 模式**：`ObservableAgent` 允许代理在不生成回复时接收消息。`AgentBase` 通过 `hubSubscribers` (pub-sub style MsgHub) 广播。
- **MessageBus**：三种消费模式 — drain (单 consumer ack), replay (多 consumer, 外部分区), broadcast (即发即弃 pub/sub)。领域 helper: inboxPush/inboxDrain/enqueueWakeup/sessionPublishEvent。
- **Gateway** (`HarnessGateway`)：多代理注册表 + 会话序列化 + 暴露的子代理路由。`SubagentRegistry` 持久化暴露记录用于跨节点/跨重启恢复。

### Nop

- **基于主题的信使** (`IAgentMessenger` / `LocalAgentMessenger`)：构建在平台 `IMessageService` 上。
  - 主题：`agent.{sessionId}.inbox` / `agent.{sessionId}.reply` / `agent.broadcast.{scope}` / `agent.call-agent`。
  - Request-Response 模式：`request(envelope, timeout)` -> 发送 REQUEST, 等待 `correlationId` 匹配的 RESPONSE。
- **IMailbox/DelayedAckMailbox**：可选 per-session 邮箱，异步缓冲消息。
- **上下文传播**：`ParentPermissionConstraint` + 委托深度 + metadata 通过 `AgentMessageRequest` 传播到子代理。

---

## 3. 注册表/发现

### AgentScope

两种发现方式：

1. **编程注册**：在 Builder 上通过 `SubagentDeclaration.builder()` 注册。`DefaultAgentManager` 有 `ConcurrentHashMap<String, SubagentFactory>`。
2. **文件/工作区发现** (`subagents/*.md`)：`AgentSpecLoader` 扫描 `subagents/*.md`。每个 Markdown 文件有 YAML frontmatter (description/workspace/model/maxIters/tools)。`DynamicSubagentsMiddleware` 每次推理重新解析子代理集。LLM 还可以用 `agent_generate` 工具创作新 subagent spec。
3. **Gateway 级注册表**：`SubagentRegistry` (InMemory / StoreBacked) + `SubagentMaterializer` 用于跨节点/跨重启恢复。

### Nop

**模型驱动 XML 配置**：
1. `*.agent.xml` 文件：由 `agent.xdef` XDEF schema 生成。`DefaultAgentEngine.loadAgentModel(agentName)` 通过 VFS 加载。Agent 名经 `AgentNames.requireValidIdentifier()` 校验 (路径注入保护)。
2. **无运行时中心注册表**：引擎不知道所有可用代理，按需通过 VFS 路径加载。无可查询的 agent directory。
3. **Team 管理** (`ITeamManager`)：InMemory/DbTeamManager。Team 可通过 `.agent.xml` 声明或编程式 `createTeam(TeamSpec)` 创建。`IMemberSpawner` 为 team 任务生成成员。

---

## 4. 关键架构差异

| 维度 | AgentScope | Nop |
|------|-----------|-----|
| **编配模式** | **工具驱动**：子代理作为 tool 调用，瞬时的 LLM-driven 委派 | **会话驱动 + 消息传递**：`call-agent` 是 tool，但执行通过 IAgentEngine.execute() + 显式会话分叉 |
| **上下文共享** | 克隆 AgentState 到子代理的 session store。子代理无父级消息历史可见性 | 分叉会话可选继承父级消息 (inheritContext=true)。ParentPermissionConstraint 用于安全继承 |
| **通信** | Reactive streams (Flux<Event>) + MessageBus (drain/replay/broadcast) | 基于主题的 messaging (inbox/reply/broadcast/call-agent)，correlationId 解复用 |
| **子代理规范** | 编程式 (Java Builder) + 声明式 (subagents/*.md YAML frontmatter) | 声明式 (*.agent.xml 由 xdef schema 驱动) |
| **状态隔离** | 每个子调用独立 AgentStateStore 槽位。IsolationScope (USER/SESSION/AGENT/GLOBAL) | 子会话是独立 AgentSession 实例。parentSessionId 仅用于审计 |
| **执行模型** | 同步 (Mono/Flux 反应式管道)，子代理在父级 Reactor 链内 | 异步 (CompletableFuture<AgentExecutionResult>)。支持同步 sendMessage + 异步 execute |
| **安全性** | 每个子代理有 ToolFilter/PermissionContextState | ParentPermissionConstraint 传播给子代理，ParentConstrainedToolAccessChecker/PathAccessChecker 强制执行 |
| **DAG/工作流** | 无。子代理是扁平的、独立的 | **真正的 DAG 编配**：TeamTaskFlowOrchestrator + nop-task 运行时。blockedBy 依赖、拓扑排序、失败传播 |
| **生命周期** | 每调用生成和销毁。MAX_SPAWN_DEPTH = 3。超时晋升 (sync→async) | 完整会话生命周期 (create/fork/continue/cancel/resume)。MAX_DELEGATION_DEPTH = 3 |
| **恢复/弹性** | 暴露子代理通过 SubagentRegistry 跨节点恢复。后台任务在 TaskRepository | 全面的：检查点、断路器、重试、目标跟踪、恢复 |

---

## 5. 各自亮点

### AgentScope

1. **Markdown 子代理规范**：`subagents/*.md` YAML frontmatter 定义子代理。非技术成员可创作/修改。LLM 甚至可用 `agent_generate` 工具写自己的规范。
2. **EventSource 分层路径**：子代理事件携带完整层次路径 (`main/researcher/sub-executor`)，用于 UI 可折叠 trace 面板。
3. **SubagentEventBus**：在非流式父调用期间将子代理事件汇入父级 Reactive stream 的轻量模式。
4. **后台任务模型**：`timeout_seconds=0` 生成子代理 → fire-and-forget 并行。完成通过 `<system-reminder>` 自动推送。超时晋升优雅。
5. **IsolationScope 状态隔离**：USER/SESSION/AGENT/GLOBAL 灵活隔离，适合多租户。

### Nop

1. **XDEF 模型驱动定义**：`.agent.xml` 由 `agent.xdef` schema 驱动，自动生成 AgentModel/TeamModel/PermissionModel/MiddlewareModel 等。
2. **TeamTaskFlowOrchestrator — 真实 DAG 编配**：与 nop-task 平台运行时集成。拓扑排序 + 循环检测 + 自动成员生成。AgentScope 的子代理是扁平的，Nop 支持真实 DAG。
3. **ParentPermissionConstraint — 安全继承**：父级约束 (允许工具、路径规则、安全级别) 通过 ParentConstrainedToolAccessChecker/PathAccessChecker 传播到子代理并强制执行。
4. **全面可靠性层**：ICheckpointManager/ICircuitBreaker/IRetryPolicy/IGoalTracker/ISustainer — 所有参与子代理恢复和执行可靠性。显式 `cancelSession` + ShutdownManager 防僵尸。
5. **Budget + Model router**：IBudgetProvider + IModelRouter 使子代理可根据预算降级到更便宜的模型——企业级功能。
6. **日志和审计**：Slf4jAuditLogger (含工具和安全决策审计)。IUsageRecorder 跟踪 token 使用。IDenialLedger 实现 "sticky pause" (重复权限拒绝 → 暂停直到人工恢复)。
