# 134 IAgentEngine Actor 消息入口 + DefaultAgentEngine

> Plan Status: completed
> Last Reviewed: 2026-06-11
> Module: nop-ai-agent
> Work Item: L1-1
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 L1-1, `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` §3.2, `ai-dev/design/nop-ai-agent/01-architecture-baseline.md` §四
> Related: 131-nop-ai-agent-execution-context.md (L1-2 ✅), 132-nop-ai-agent-executor-interface.md (L1-3 ✅), 133-nop-ai-agent-react-executor.md (L1-5 ✅)

## Purpose

实现 `IAgentEngine` 接口和 `DefaultAgentEngine`，建立 Agent 引擎的 Actor 消息入口。这是 Layer 1 主链路的最后一公里——将已有的 AgentModel 加载、AgentExecutionContext、IAgentExecutor、ReActAgentExecutor 连接为一个可用的端到端引擎。

## Current Baseline

- `AgentModel`（L0-1 ✅）：可通过 VFS 组件管理器加载，字段含 name, chatOptions, tools, prompt, permissions, constraints, hooks 等
- `AgentExecutionContext`（L1-2 ✅）：`io.nop.ai.agent.engine.AgentExecutionContext`，持有 agentModel, messages, sessionId, status, currentIteration, maxIterations, chatOptionsModel, tokensUsed, metadata, lastError, startTimeMs。工厂方法 `create(AgentModel, String sessionId)`
- `AgentExecutionResult`（L1-2 ✅）：`io.nop.ai.agent.engine.AgentExecutionResult`，字段含 status, finalMessage, messages, totalIterations, totalTokensUsed, durationMs, error。**不含 sessionId 字段** — 本计划将扩展此类（见 Goals）
- `IAgentExecutor`（L1-3 ✅）：`io.nop.ai.agent.engine.IAgentExecutor`，方法 `CompletionStage<AgentExecutionResult> execute(AgentExecutionContext ctx)`
- `ReActAgentExecutor`（L1-5 ✅）：`io.nop.ai.agent.engine.ReActAgentExecutor`，**同步执行** — 内部运行完整 ReAct 循环后返回 `CompletableFuture.completedFuture(...)`。构造函数接受 `IChatService` 和 `IToolManager`
- `IChatService`（nop-ai-api）：`io.nop.ai.api.chat.IChatService`，含 `call(ChatRequest, ICancelToken)` 和 `callAsync(ChatRequest, ICancelToken)`
- `IToolManager`（nop-ai-toolkit）：`io.nop.ai.toolkit.api.IToolManager`，含 `callTool(name, AiToolCall, ctx)` 和 `loadTool(name)`
- `agent.register-model.xml`（L0-1 ✅）：已注册，`.agent.xml` 文件可被加载
- 测试 agent 配置：`/test-agent.agent.xml`（含 name="test-agent", description, prompt="You are a helpful assistant."）
- 测试初始化模式：`CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT)`（见 `TestAgentModelLoading`）
- `engine/` 包下无 IAgentEngine 接口、无 AgentMessageRequest/AgentMessageAck、无 DefaultAgentEngine
- `session/` 包为空（无任何 Java 文件）

## Goals

- `IAgentEngine` 接口存在于 `io.nop.ai.agent.engine` 包，定义 Actor 消息入口
- `AgentMessageRequest` 数据类：封装请求参数（agentName, userMessage, sessionId, metadata）
- `AgentMessageAck` 数据类：封装确认响应（sessionId, status）
- 扩展 `AgentExecutionResult`（L1-2 类）：添加 `sessionId` 字段，更新 `fromContext()` 从 context 复制 sessionId — 这是引擎使用者获取执行结果对应 session 的必要信息
- `DefaultAgentEngine` 实现 `IAgentEngine`：
  - 构造函数接受 `IChatService` 和 `IToolManager`
  - `sendMessage(request)` 方法：加载 AgentModel，创建 context，**异步启动执行**（因 ReActAgentExecutor 是同步的，需在 `execute()` 内部包装异步边界），立即返回 ack（与设计文档 §3.2 一致）
  - `execute(request)` 便利方法：返回 `CompletableFuture<AgentExecutionResult>`，内部将同步的 `ReActAgentExecutor.execute()` 包装在 `CompletableFuture.supplyAsync()` 中异步执行。供测试和简单场景使用（设计文档 §3.2 之外的扩展，用于弥补 L1-9 AgentEventPublisher 尚未实现时的结果获取需求）
- AgentModel 加载：通过 VFS 组件管理器按 `{agentName}.agent.xml` 路径加载
- Session ID 管理：新请求生成 UUID 作为 sessionId，已有 sessionId 直接复用（不持久化）
- sendMessage 的异步异常处理：对 `execute()` 返回的 CompletableFuture 附加 `exceptionally()` 处理器，记录异常日志（不静默吞掉）。直到 L1-9 实现后才有正式的事件通知机制
- 单元测试覆盖：sendMessage 返回 ack、execute 返回 result、sessionId 生成与复用、AgentModel 加载成功与失败、异步异常处理

## Non-Goals

- 不实现完整 Actor 模型（AgentActor、Mailbox、ActorSystem）——使用 CompletableFuture 简化异步
- 不实现 AgentEventPublisher 事件发布（L1-9）
- 不实现完整 AgentSession 持久化（L1-10）——仅内存 sessionId 生成/复用
- 不实现 followUp 外层循环（后续工作项）
- 不实现 steering 消息注入
- 不实现 IoC bean 注册（后续工作项统一注册）
- 不实现 Hook 生命周期回调（L1-12）
- 不实现权限检查（L1-6, L1-7, L1-8）

## Scope

### In Scope

- `IAgentEngine` 接口（`io.nop.ai.agent.engine` 包）
- `AgentMessageRequest` 数据类（`io.nop.ai.agent.engine` 包）
- `AgentMessageAck` 数据类（`io.nop.ai.agent.engine` 包）
- `DefaultAgentEngine` 类（`io.nop.ai.agent.engine` 包）
- `AgentExecutionResult` 扩展：添加 `sessionId` 字段并更新 `fromContext()`（L1-2 类的小幅扩展）
- AgentModel 加载逻辑（通过 VFS 组件管理器）
- Session ID 生成（UUID）与复用（不持久化）
- 异步执行边界（将同步 ReActAgentExecutor 包装为异步）
- JUnit 5 单元测试

### Out Of Scope

- 完整 Actor 运行时（AgentActor, Mailbox, ActorSystem）
- AgentEventPublisher（L1-9）
- AgentSession 持久化（L1-10）
- followUp 循环
- steering 注入
- Hook 生命周期
- 权限检查
- IoC bean 注册

## Execution Plan

### Phase 1 - IAgentEngine 接口与数据类

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/`, `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestAgentExecutionResult.java`

- Item Types: `Proof`

- [x] 创建 `AgentMessageRequest` 数据类：字段 `agentName` (String, 必填), `userMessage` (String, 必填), `sessionId` (String, nullable — 空=新建), `metadata` (Map<String,Object>, nullable)
- [x] 创建 `AgentMessageAck` 数据类：字段 `sessionId` (String, 必填), `status` (String, 默认 "accepted")
- [x] 扩展 `AgentExecutionResult`：添加 `sessionId` 字段（构造函数参数 + getter），更新 `fromContext()` 从 `ctx.getSessionId()` 复制
- [x] 更新 `TestAgentExecutionResult` 中已有测试以适应新增的 sessionId 参数（确保已有测试仍通过）
- [x] 创建 `IAgentEngine` 接口：
  - `AgentMessageAck sendMessage(AgentMessageRequest request)` — Actor 消息入口，立即返回 ack
  - `CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request)` — 便利方法，返回执行结果的 future

Exit Criteria:

- [x] `AgentMessageRequest.java` 存在于 `io.nop.ai.agent.engine` 包，含 agentName, userMessage, sessionId, metadata 四个字段
- [x] `AgentMessageAck.java` 存在于 `io.nop.ai.agent.engine` 包，含 sessionId, status 两个字段
- [x] `AgentExecutionResult.java` 已扩展：新增 `sessionId` 字段（final, getter），`fromContext()` 从 ctx 复制 sessionId
- [x] `TestAgentExecutionResult.java` 已有测试仍通过
- [x] `IAgentEngine.java` 存在于 `io.nop.ai.agent.engine` 包，含 sendMessage 和 execute 两个方法
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 退出码 0
- [x] No owner-doc update required（设计文档 `nop-ai-agent-react-engine.md` §3.2 已定义接口签名）
- [x] `ai-dev/logs/`: N/A — 日志在 Phase 3 统一更新

### Phase 2 - DefaultAgentEngine 实现

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`

- Item Types: `Proof`

- [x] 创建 `DefaultAgentEngine` 类实现 `IAgentEngine`
- [x] 构造函数接受 `IChatService chatService` 和 `IToolManager toolManager`，并保存为 final 字段
- [x] 实现 `execute(AgentMessageRequest request)` 方法：
  - 通过 VFS 组件管理器加载 `AgentModel`（路径格式 `/{agentName}.agent.xml`）
  - 确定 sessionId：若 request.sessionId 为空则生成 UUID，否则使用请求提供的 sessionId
  - 通过 `AgentExecutionContext.create(agentModel, sessionId)` 创建执行上下文
  - 将用户消息（request.userMessage）追加到 context 的 messages 列表
  - 创建 `ReActAgentExecutor` 实例（注入 chatService 和 toolManager）
  - **异步边界**：因 ReActAgentExecutor.execute() 是同步的（返回 completedFuture），用 `CompletableFuture.supplyAsync()` 包装其调用，确保不阻塞调用线程
  - 返回 `CompletableFuture<AgentExecutionResult>`
- [x] 实现 `sendMessage(AgentMessageRequest request)` 方法：
  - 委托 `execute(request)` 启动异步执行（不等待完成）
  - 对返回的 future 附加 `exceptionally()` 处理器：记录异常日志（至少 log error message），不静默吞掉异步失败
  - 立即返回 `AgentMessageAck(sessionId, "accepted")`
  - 注：异步执行的结果目前仅通过 `execute()` 返回的 CompletableFuture 可获取。`sendMessage` 调用方无法获取结果（直到 L1-9 AgentEventPublisher 实现后才有事件通知机制）
- [x] 异常处理：AgentModel 加载失败时抛出带清晰消息的 NopException（含 agentName）

Exit Criteria:

- [x] `DefaultAgentEngine.java` 存在于 `io.nop.ai.agent.engine` 包，实现 `IAgentEngine`
- [x] 构造函数接受 `IChatService` 和 `IToolManager`
- [x] `execute()` 方法通过 VFS 组件管理器加载 AgentModel，创建 context，将同步的 ReActAgentExecutor 包装在 `CompletableFuture.supplyAsync()` 中异步执行
- [x] `sendMessage()` 方法委托 execute()，附加 `exceptionally()` 异常处理器记录日志，立即返回 AgentMessageAck(sessionId, "accepted")
- [x] AgentModel 加载路径格式为 `/{agentName}.agent.xml`（与 `test-agent.agent.xml` 测试配置一致）
- [x] sessionId 生成使用 UUID（当 request.sessionId 为空时），非空时直接复用
- [x] 测试类含 `@BeforeAll` 初始化 `CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT)`
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 退出码 0
- [x] **端到端验证**: N/A — Phase 3 测试覆盖端到端路径
- [x] **接线验证**: N/A — Phase 3 测试验证 DefaultAgentEngine → ReActAgentExecutor → IChatService 调用链
- [x] **无静默跳过**: AgentModel 加载失败抛异常，不返回 null
- [x] No owner-doc update required
- [x] `ai-dev/logs/`: N/A — 日志在 Phase 3 统一更新

### Phase 3 - 单元测试

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestDefaultAgentEngine.java`

- Item Types: `Proof`

- [x] 创建 `TestDefaultAgentEngine` 测试类，使用 mock IChatService 和 IToolManager
- [x] 测试 1（testSendMessageReturnsAck）：调用 sendMessage（agentName="test-agent", userMessage="hello"）→ 返回 ack，sessionId 非空，status="accepted"。**验证不阻塞**：使用含 `Thread.sleep()` 延迟的 mock IChatService，断言 sendMessage 在延迟完成前返回 ack
- [x] 测试 2（testExecuteReturnsResult）：mock IChatService 返回成功 ChatResponse（isSuccess=true, 无 tool calls）→ execute 的 future 完成 → AgentExecutionResult.status == completed, result.sessionId 非空
- [x] 测试 3（testNewSessionGeneration）：不提供 sessionId → execute 返回的 result.sessionId 为新生成的 UUID（非 null 且非空字符串）
- [x] 测试 4（testExistingSessionReuse）：提供 sessionId="my-session" → execute 返回的 result.sessionId == "my-session"
- [x] 测试 5（testAgentModelLoadingFromDsl）：使用 test-agent（mock IChatService 返回成功 ChatResponse），验证 result 中 agentModel.name == "test-agent"（通过 messages 包含 system prompt 验证 prompt 非空）
- [x] 测试 6（testAgentModelNotFound）：使用不存在的 agentName="non-existent-agent" → execute 抛出异常（验证错误消息含 "non-existent-agent"）
- [x] 测试 7（testSendMessageAsyncExecution）：调用 execute() 获取 future → future.get() 成功返回 AgentExecutionResult → 验证 mock IChatService.call 被调用（证明异步执行确实发生且委托到 ReActAgentExecutor）

Exit Criteria:

- [x] `TestDefaultAgentEngine.java` 存在于 `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/`
- [x] 测试类含 `@BeforeAll` 初始化 `CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT)` 和 `@AfterAll` 销毁
- [x] 7 个测试方法全部通过
- [x] **端到端验证**: 测试 2, 5, 7 覆盖从 IAgentEngine.execute()/sendMessage() → AgentModel 加载 → ReActAgentExecutor → AgentExecutionResult 的完整路径
- [x] **接线验证**: 测试 2 和 5 中 mock IChatService.call 被调用，证明 DefaultAgentEngine 正确委托给 ReActAgentExecutor
- [x] **无静默跳过**: 测试 6 验证 AgentModel 加载失败时抛异常而非静默返回
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 退出码 0
- [x] No owner-doc update required
- [x] `ai-dev/logs/`: N/A — 日志在 Phase 4 统一更新

### Phase 4 - 收尾：Roadmap 更新与日志

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`, `ai-dev/logs/`

- Item Types: `Follow-up`

- [x] 更新 roadmap L1-1 状态从 ❌ 改为 ✅
- [x] 更新设计文档 `nop-ai-agent-react-engine.md` §3.2：记录 `execute()` 便利方法（L1-1 扩展，含设计理由）
- [x] 更新 `ai-dev/logs/` 对应日期条目

Exit Criteria:

- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` §3.2 已记录 `execute()` 便利方法
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` L1-1 行状态已更新
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] **端到端验证**: N/A — 纯文档更新
- [x] **接线验证**: N/A — 纯文档更新
- [x] **无静默跳过**: N/A — 纯文档更新
- [x] Owner-doc update: roadmap 已更新

## Closure Gates

- [x] IAgentEngine 接口包含 `sendMessage()`（与设计文档 §3.2 一致）和 `execute()` 便利方法（L1-1 扩展，弥补 L1-9 尚未实现时的结果获取需求）
- [x] DefaultAgentEngine 能从 DSL 加载 AgentModel 并执行 ReAct 循环
- [x] AgentMessageRequest 和 AgentMessageAck 数据类正确封装请求/响应
- [x] sendMessage 立即返回 ack（不阻塞等待执行完成）
- [x] execute 返回 CompletableFuture（可等待执行完成）
- [x] sessionId 正确生成（UUID）或复用
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] **Anti-Hollow Check**: closure audit 已验证（a）DefaultAgentEngine.sendMessage 确实触发了 ReActAgentExecutor.execute（通过异步边界）（b）AgentModel 通过 VFS 组件管理器被实际加载（c）sendMessage 附加了 exceptionally() 处理器不静默吞掉异步异常（d）无空方法体/静默跳过/no-op 作为正常实现
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] checkstyle / 代码规范检查通过（或确认 checkstyle 未配置于此模块时注明 N/A）

## Deferred But Adjudicated

### 完整 Actor 运行时（Mailbox, AgentActor, ActorSystem）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: L1-1 的核心目标是建立消息入口和执行连接。CompletableFuture 简化异步已足够支撑端到端流程。完整 Actor 模型属于 Layer 4（L4-1 ~ L4-8）的工作
- Successor Required: yes
- Successor Path: Layer 4 plans

### AgentEventPublisher 事件发布

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: L1-9 是独立工作项，无依赖。DefaultAgentEngine L1-1 版本不发布事件，但不影响执行正确性
- Successor Required: yes
- Successor Path: L1-9 plan

### AgentSession 持久化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: L1-10 是独立工作项。L1-1 仅做 sessionId 生成/复用，不做持久化。持久化是生产需求，不影响功能正确性
- Successor Required: yes
- Successor Path: L1-10 plan

### followUp 外层循环

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: followUp 循环依赖 session 持久化和事件发布，属于后续增强
- Successor Required: yes
- Successor Path: followUp 循环专用 plan

## Non-Blocking Follow-ups

- Hook 生命周期回调（L1-12）将增强引擎的扩展性
- steering 消息注入将在内层循环中支持外部干预
- 取消令牌（ICancelToken）传递和检查
- IoC bean 注册将在统一阶段处理
- 异步执行使用 `CompletableFuture.supplyAsync()` 默认的 ForkJoinPool.commonPool()；生产环境应切换为专用 Executor（Layer 4 concern）

## Closure

Status Note: All 4 phases completed. IAgentEngine interface with sendMessage/execute, DefaultAgentEngine implementation with VFS-based AgentModel loading and async ReAct loop execution, 7 unit tests covering e2e/wiring/error paths, roadmap and design docs updated. Closure audit PASSED by independent sub-agent.

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (task ses_148fdf5fbffecNZ8JRTxlNoLX0)
- Audit Session: ses_148fdf5fbffecNZ8JRTxlNoLX0
- Evidence:
  - Phase 1 Exit Criteria: all PASS — AgentMessageRequest (4 fields), AgentMessageAck (2 fields), AgentExecutionResult extended with sessionId, IAgentEngine (2 methods), existing tests pass
  - Phase 2 Exit Criteria: all PASS — DefaultAgentEngine implements IAgentEngine, constructor IChatService+IToolManager, execute() uses CompletableFuture.supplyAsync(), sendMessage() delegates+logs+returns ack, loadAgentModel throws NopAiAgentException
  - Phase 3 Exit Criteria: all PASS — TestDefaultAgentEngine 7/7 tests pass, e2e tests (2,5,7) cover full chain, wiring verified via AtomicBoolean, error test covers missing agent
  - Phase 4 Exit Criteria: all PASS — design doc §3.2 updated, roadmap L1-1 ✅, daily log updated
  - Closure Gates: all PASS — Anti-Hollow verified via call chain trace (execute→loadAgentModel→createContext→ReActAgentExecutor→chatService.call), no silent no-ops, no swallowed exceptions
  - Anti-Hollow: call chain complete and real; `node ai-dev/tools/check-plan-checklist.mjs` only unchecked item was closure audit itself
  - Deferred items: all 4 correctly classified as out-of-scope improvement with legitimate successor paths

Follow-up:

- no remaining plan-owned work
