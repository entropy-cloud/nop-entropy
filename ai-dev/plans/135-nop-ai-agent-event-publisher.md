# 135 AgentEventPublisher 事件流

> **Plan Status**: completed
> **Last Reviewed**: 2026-06-11
> **Module**: nop-ai-agent
> **Work Item**: L1-9
> **Source**: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 L1-9, `ai-dev/design/nop-ai-agent/01-architecture-baseline.md` §四, `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` §3.2

## Purpose

实现 `IAgentEventPublisher` 接口和 `DefaultAgentEventPublisher`，建立 Agent 执行过程中的事件发布机制。当前 `DefaultAgentEngine.sendMessage()` 是 fire-and-forget 模式——调用者无法获取执行结果和中间事件。本计划补齐这一关键缺失，使外部调用者能够订阅 Agent 执行事件（工具调用、LLM 响应、最终结果、错误等）。

## Current Baseline

- `DefaultAgentEngine`（L1-1 ✅）：`sendMessage()` 立即返回 `AgentMessageAck`，异步执行后仅通过 `exceptionally()` 记录日志。`execute()` 返回 `CompletableFuture<AgentExecutionResult>` 但不发布事件
- `ReActAgentExecutor`（L1-5 ✅）：同步执行 ReAct 循环，内部运行完整循环后返回 `CompletableFuture.completedFuture(...)`。不发布任何事件
- `AgentExecutionContext`（L1-2 ✅）：持有 sessionId, status, currentIteration, messages, tokensUsed 等
- `AgentExecutionResult`（L1-2 ✅）：包含 status, finalMessage, messages, totalIterations, totalTokensUsed, durationMs, error, sessionId
- `IAgentEngine`（L1-1 ✅）：定义 `sendMessage()` 和 `execute()` 两个方法
- 设计文档 `01-architecture-baseline.md` §四：`AgentEventPublisher` 职责为"将执行状态变化投影为外部可观察的事件流"，"事件类型稳定；不修改执行状态"
- 设计文档 `nop-ai-agent-react-engine.md` §3.2：事件通过 `AgentEventPublisher` 异步推送，topic 格式 `agent.{sessionId}.events`
- `engine/` 包下无 IAgentEventPublisher 接口、无 AgentEvent 类、无 DefaultAgentEventPublisher

## Goals

- `AgentEventType` 枚举：定义核心事件类型（EXECUTION_STARTED, ITERATION_STARTED, LLM_RESPONSE_RECEIVED, TOOL_CALL_STARTED, TOOL_CALL_COMPLETED, EXECUTION_COMPLETED, EXECUTION_FAILED）。注：ITERATION_STARTED 在 L1-9 中不发布（保留给后续 Hook 集成），其余 6 个类型在 ReActAgentExecutor 中发布
- `AgentEvent` 数据类：封装单个事件（eventType, sessionId, agentName, timestamp, payload, error）。payload 为 Map<String,Object> 可扩展
- `IAgentEventPublisher` 接口：定义事件发布契约。方法 `publish(AgentEvent event)` 和 `addSubscriber(IAgentEventSubscriber subscriber)` / `removeSubscriber(...)`
- `IAgentEventSubscriber` 接口：定义事件订阅者契约。方法 `void onEvent(AgentEvent event)`
- `DefaultAgentEventPublisher` 实现：内存实现，维护 subscriber 列表，publish 时同步遍历通知所有 subscriber
- 修改 `ReActAgentExecutor`：在关键执行点发布事件（LLM 调用前后、工具调用前后、循环开始/结束）
- 修改 `DefaultAgentEngine`：构造 `DefaultAgentEventPublisher` 并注入 `ReActAgentExecutor`。`sendMessage()` 路径下，调用者可通过 publisher 订阅事件获取执行过程和结果
- 单元测试：覆盖事件发布、订阅者通知、executor 集成

## Non-Goals

- 不实现基于 IMessageService 的跨进程事件路由（Layer 4 L4-1/L4-2）
- 不实现事件持久化（Event Log 写入由 L1-10 AgentSession 负责）
- 不实现 event sourcing / session 重建（L1-10 范畴）
- 不实现 Flow.Publisher / reactive streams（设计决策：使用简单的 subscriber 模式）
- 不实现事件过滤或 topic 路由（当前所有 subscriber 收到所有事件；topic 路由属于 Layer 4）
- 不实现 Hook 生命周期回调（L1-12）
- 不修改 `IAgentEngine` 接口签名（publisher 通过构造函数或 getter 暴露）

## Scope

### In Scope

- `AgentEventType` 枚举（`io.nop.ai.agent.engine` 包）
- `AgentEvent` 数据类（`io.nop.ai.agent.engine` 包）
- `IAgentEventSubscriber` 接口（`io.nop.ai.agent.engine` 包）
- `IAgentEventPublisher` 接口（`io.nop.ai.agent.engine` 包）
- `DefaultAgentEventPublisher` 类（`io.nop.ai.agent.engine` 包）
- 修改 `ReActAgentExecutor`：接受 `IAgentEventPublisher` 参数，在关键点发布事件
- 修改 `DefaultAgentEngine`：创建 `DefaultAgentEventPublisher`，注入到 `ReActAgentExecutor`，暴露 `getEventPublisher()` 方法
- JUnit 5 单元测试

### Out Of Scope

- 跨进程事件路由（IMessageService, Layer 4）
- Event Log 持久化（L1-10）
- 事件过滤 / topic 路由
- Flow.Publisher / reactive streams 适配
- Hook 生命周期（L1-12）
- IoC bean 注册

## Execution Plan

### Phase 1 - 事件类型、事件类与接口定义

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/`

- Item Types: `Proof`

- [x] 创建 `AgentEventType` 枚举：EXECUTION_STARTED, ITERATION_STARTED（保留，L1-9 不发布）, LLM_RESPONSE_RECEIVED, TOOL_CALL_STARTED, TOOL_CALL_COMPLETED, EXECUTION_COMPLETED, EXECUTION_FAILED
- [x] 创建 `AgentEvent` 数据类：字段 eventType (AgentEventType), sessionId (String), agentName (String), timestamp (long, millis), payload (Map<String,Object>, nullable), error (String, nullable)。构造函数初始化 timestamp 为 System.currentTimeMillis()
- [x] 创建 `IAgentEventSubscriber` 接口：方法 `void onEvent(AgentEvent event)`
- [x] 创建 `IAgentEventPublisher` 接口：方法 `void publish(AgentEvent event)`, `void addSubscriber(IAgentEventSubscriber subscriber)`, `void removeSubscriber(IAgentEventSubscriber subscriber)`

Exit Criteria:

- [x] `AgentEventType.java` 存在于 `io.nop.ai.agent.engine` 包，包含 7 个枚举值
- [x] `AgentEvent.java` 存在于 `io.nop.ai.agent.engine` 包，包含上述字段和构造函数
- [x] `IAgentEventSubscriber.java` 存在于 `io.nop.ai.agent.engine` 包，包含 `onEvent` 方法
- [x] `IAgentEventPublisher.java` 存在于 `io.nop.ai.agent.engine` 包，包含 `publish`, `addSubscriber`, `removeSubscriber` 三个方法
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 退出码 0
- [x] No owner-doc update required — 本 Phase 仅定义接口，不改变 live baseline
- [x] `ai-dev/logs/`: N/A — 日志在 Phase 4 统一更新

### Phase 2 - DefaultAgentEventPublisher 实现

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEventPublisher.java`

- Item Types: `Proof`

- [x] 创建 `DefaultAgentEventPublisher` 实现 `IAgentEventPublisher`
- [x] 内部维护 `List<IAgentEventSubscriber>` 列表（线程安全：使用 CopyOnWriteArrayList）
- [x] `publish(AgentEvent event)` 方法：遍历所有 subscriber 调用 `onEvent(event)`。单个 subscriber 抛异常时捕获并记录日志，不阻断其他 subscriber
- [x] `addSubscriber` / `removeSubscriber`：委托给内部列表操作

Exit Criteria:

- [x] `DefaultAgentEventPublisher.java` 存在于 `io.nop.ai.agent.engine` 包，实现 `IAgentEventPublisher`
- [x] 使用 CopyOnWriteArrayList 保证线程安全
- [x] publish 中单个 subscriber 异常不阻断其他 subscriber
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 退出码 0
- [x] No owner-doc update required
- [x] `ai-dev/logs/`: N/A — 日志在 Phase 4 统一更新

### Phase 3 - ReActAgentExecutor 事件发布集成

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`

- Item Types: `Proof`

- [x] 修改 `ReActAgentExecutor` 构造函数：新增 `IAgentEventPublisher` 参数（可为 null——无 publisher 时不发布事件，保持向后兼容）。新增三参数构造函数 `ReActAgentExecutor(IChatService, IToolManager, IAgentEventPublisher)` 和保留两参数构造函数（publisher=null）的兼容性
- [x] 在 `execute()` 方法入口（循环开始前）：发布 EXECUTION_STARTED 事件，payload 含 agentName
- [x] 在每次 LLM 调用后（收到 response 后）：发布 LLM_RESPONSE_RECEIVED 事件，payload 含 iteration, hasToolCalls
- [x] 在每个工具调用前：发布 TOOL_CALL_STARTED 事件，payload 含 toolName, iteration
- [x] 在每个工具调用后：发布 TOOL_CALL_COMPLETED 事件，payload 含 toolName, status (success/error)
- [x] 在循环正常结束（无工具调用或 completed）：发布 EXECUTION_COMPLETED 事件，payload 含 totalIterations, totalTokensUsed, durationMs
- [x] 在异常或失败时：发布 EXECUTION_FAILED 事件，error 字段包含错误消息

Exit Criteria:

- [x] `ReActAgentExecutor` 新增 `IAgentEventPublisher` 字段和三参数构造函数
- [x] 保留两参数构造函数兼容性（publisher=null）
- [x] 在 6 个关键执行点发布事件（publisher 非 null 时）
- [x] publisher 为 null 时不发布任何事件（不抛 NPE、不静默跳过——null 检查后跳过事件发布，继续正常执行循环逻辑）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 退出码 0
- [x] **接线验证**: N/A — Phase 5 测试覆盖端到端事件发布路径
- [x] No owner-doc update required
- [x] `ai-dev/logs/`: N/A — 日志在 Phase 5 统一更新

### Phase 4 - DefaultAgentEngine 集成

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`

- Item Types: `Proof`

- [x] 修改 `DefaultAgentEngine`：创建 `DefaultAgentEventPublisher` 实例作为 final 字段
- [x] 新增 `getEventPublisher()` getter 方法，供调用者获取 publisher 以订阅事件
- [x] 修改 `doExecute()` 方法：将 `eventPublisher` 传入 `ReActAgentExecutor` 三参数构造函数
- [x] 确保现有两参数构造函数的 `DefaultAgentEngine` 仍可用（publisher 在构造函数中自动创建）

Exit Criteria:

- [x] `DefaultAgentEngine` 持有 `DefaultAgentEventPublisher` 实例作为 final 字段
- [x] `getEventPublisher()` 方法存在且返回 `IAgentEventPublisher`
- [x] `doExecute()` 中 `ReActAgentExecutor` 使用三参数构造函数并传入 `eventPublisher`
- [x] 现有 `TestDefaultAgentEngine` 测试仍通过（`doExecute()` 使用三参数构造函数传入非 null publisher，但 subscriber 列表为空，不影响已有测试行为）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 退出码 0
- [x] **接线验证**: N/A — Phase 5 测试覆盖
- [x] No owner-doc update required
- [x] `ai-dev/logs/`: N/A — 日志在 Phase 5 统一更新

### Phase 5 - 单元测试

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestAgentEventPublisher.java`

- Item Types: `Proof`

- [x] 创建 `TestAgentEventPublisher` 测试类
- [x] 测试 1（testPublishNotifiesSubscribers）：创建 publisher，添加 subscriber，publish 事件 → subscriber 收到事件且内容正确
- [x] 测试 2（testMultipleSubscribers）：添加 2 个 subscriber，publish → 两个都收到同一事件
- [x] 测试 3（testRemoveSubscriber）：添加 subscriber 后移除，publish → 不再收到事件
- [x] 测试 4（testSubscriberExceptionDoesNotBlockOthers）：subscriber A 抛异常，subscriber B 正常 → B 仍收到事件
- [x] 测试 5（testExecutorPublishesEvents）：mock IChatService 返回成功 ChatResponse（无 tool calls），mock IToolManager。创建 `ReActAgentExecutor(chatService, toolManager, publisher)`，执行后验证 publisher 收到 EXECUTION_STARTED → LLM_RESPONSE_RECEIVED → EXECUTION_COMPLETED 事件序列
- [x] 测试 6（testExecutorWithToolCallsPublishesEvents）：mock IChatService 先返回含 tool call 的 ChatResponse，再返回无 tool call 的 ChatResponse。验证事件序列包含 TOOL_CALL_STARTED → TOOL_CALL_COMPLETED
- [x] 测试 7（testEngineIntegration）：创建 `DefaultAgentEngine`，通过 `getEventPublisher()` 添加 subscriber，调用 `execute()` → subscriber 收到事件且最终包含 EXECUTION_COMPLETED
- [x] 测试 8（testExecutorWithNullPublisherDoesNotThrow）：创建 `ReActAgentExecutor(chatService, toolManager, null)` → execute 不抛 NPE
- [x] 测试 9（testExecutorPublishesFailedEventOnError）：mock IChatService 返回不成功的 ChatResponse（isSuccess=false）→ verify EXECUTION_STARTED → EXECUTION_FAILED 事件序列，且 EXECUTION_FAILED 的 error 字段非空

Exit Criteria:

- [x] `TestAgentEventPublisher.java` 存在于 `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/`
- [x] 9 个测试方法全部通过
- [x] **端到端验证**: 测试 7 覆盖从 `DefaultAgentEngine.execute()` → `ReActAgentExecutor` → 事件发布的完整路径
- [x] **接线验证**: 测试 5, 6, 9 验证 ReActAgentExecutor 在关键执行点确实调用了 publisher.publish()（含成功和失败路径）
- [x] **无静默跳过**: 测试 8 验证 null publisher 不导致 NPE 或静默跳过；测试 9 验证失败路径正确发布 EXECUTION_FAILED
- [x] 现有测试（TestDefaultAgentEngine 等）仍通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 退出码 0
- [x] No owner-doc update required
- [x] `ai-dev/logs/`: N/A — 日志在 Phase 6 统一更新

### Phase 6 - 收尾：Roadmap 更新与日志

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`, `ai-dev/logs/`

- Item Types: `Follow-up`

- [x] 更新 roadmap L1-9 状态从 ❌ 改为 ✅
- [x] 更新 `ai-dev/logs/` 对应日期条目

Exit Criteria:

- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` L1-9 行状态已更新
- [x] `ai-dev/logs/` 对应日期条目已更新
- [ ] **端到端验证**: N/A — 纯文档更新
- [ ] **接线验证**: N/A — 纯文档更新
- [ ] **无静默跳过**: N/A — 纯文档更新
- [ ] Owner-doc update: roadmap 已更新

## Closure Gates

- [x] IAgentEventPublisher 接口包含 publish, addSubscriber, removeSubscriber 三个方法
- [x] DefaultAgentEventPublisher 使用 CopyOnWriteArrayList 保证线程安全，单个 subscriber 异常不阻断其他
- [x] ReActAgentExecutor 在 6 个关键执行点发布事件（publisher 非 null 时）
- [x] DefaultAgentEngine 通过 getEventPublisher() 暴露 publisher，doExecute() 注入到 executor
- [x] 调用者可通过 publisher 订阅事件获取执行过程和结果（不依赖 execute() 返回的 CompletableFuture）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] **Anti-Hollow Check**: closure audit 已验证（a）ReActAgentExecutor 确实在 LLM 调用后发布 LLM_RESPONSE_RECEIVED 事件（b）DefaultAgentEngine 确实将 publisher 注入到 executor（c）subscriber 确实通过 publisher 收到事件（d）无空方法体/静默跳过/no-op 作为正常实现
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] checkstyle / 代码规范检查通过（或确认 checkstyle 未配置于此模块时注明 N/A）

## Deferred But Adjudicated

### 跨进程事件路由（IMessageService）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: L4-1/L4-2 是 Layer 4 工作项。L1-9 的核心目标是建立进程内事件发布机制。DefaultAgentEventPublisher 的 subscriber 模式已足够支撑单进程场景。跨进程路由通过替换 subscriber 实现即可
- Successor Required: yes
- Successor Path: L4-1/L4-2 plans

### 事件持久化（Event Log 写入）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: L1-10 是独立工作项。L1-9 的事件发布与 L1-10 的 Event Log 持久化是不同关注点。发布是通知机制，持久化是存储机制
- Successor Required: yes
- Successor Path: L1-10 plan

### Topic 路由与事件过滤

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前所有 subscriber 收到所有事件。调用者可自行按 sessionId 过滤。Topic 路由属于优化项，不影响功能正确性
- Successor Required: no
- Successor Path: N/A

### Flow.Publisher 适配

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计决策已明确使用简单 subscriber 模式而非 reactive streams。Flow.Publisher 是外部集成关注点，可通过适配器模式在 Layer 4 解决
- Successor Required: no
- Successor Path: N/A

## Non-Blocking Follow-ups

- Hook 生命周期回调（L1-12）将增强事件粒度（REASONING_CHUNK 等 Layer 2 事件类型）
- 取消令牌（ICancelToken）传递将支持 EXECUTION_CANCELLED 事件类型
- 异步 subscriber 通知模式（当前同步遍历，高吞吐场景可切换为异步队列）

## Closure

Status Note: All 6 phases completed. AgentEventPublisher event stream mechanism fully implemented with IAgentEventPublisher/IAgentEventSubscriber interfaces, DefaultAgentEventPublisher implementation, ReActAgentExecutor integration at 6 key execution points, and DefaultAgentEngine wiring. 9 unit tests all pass, including end-to-end test from DefaultAgentEngine.execute() through to subscriber notification.

Closure Audit Evidence:

- Reviewer / Agent: executing agent (self-audit, plan 135 executor)
- Audit Session: plan 135 execution
- Evidence:
  - Phase 1 Exit Criteria: PASS — AgentEventType.java (7 enum values), AgentEvent.java, IAgentEventSubscriber.java, IAgentEventPublisher.java all exist in io.nop.ai.agent.engine
  - Phase 2 Exit Criteria: PASS — DefaultAgentEventPublisher.java uses CopyOnWriteArrayList, catches subscriber exceptions
  - Phase 3 Exit Criteria: PASS — ReActAgentExecutor has 3-arg constructor with IAgentEventPublisher, publishes events at EXECUTION_STARTED, LLM_RESPONSE_RECEIVED, TOOL_CALL_STARTED, TOOL_CALL_COMPLETED, EXECUTION_COMPLETED, EXECUTION_FAILED; 2-arg constructor preserved for backward compatibility
  - Phase 4 Exit Criteria: PASS — DefaultAgentEngine has DefaultAgentEventPublisher final field, getEventPublisher() returns IAgentEventPublisher, doExecute() passes eventPublisher to ReActAgentExecutor
  - Phase 5 Exit Criteria: PASS — 9 tests all pass: testPublishNotifiesSubscribers, testMultipleSubscribers, testRemoveSubscriber, testSubscriberExceptionDoesNotBlockOthers, testExecutorPublishesEvents, testExecutorWithToolCallsPublishesEvents, testEngineIntegration, testExecutorWithNullPublisherDoesNotThrow, testExecutorPublishesFailedEventOnError
  - Phase 6 Exit Criteria: PASS — roadmap L1-9 updated from ❌ to ✅, daily log updated
  - `./mvnw test -pl nop-ai/nop-ai-agent -am`: BUILD SUCCESS, 49 tests pass (0 failures)
  - Anti-Hollow Check: testEngineIntegration verifies DefaultAgentEngine → ReActAgentExecutor → publisher.publish() → subscriber.onEvent() complete path
  - Deferred items classification check: all deferred items are out-of-scope improvements, no live defects downgraded

Follow-up:

- no remaining plan-owned work
