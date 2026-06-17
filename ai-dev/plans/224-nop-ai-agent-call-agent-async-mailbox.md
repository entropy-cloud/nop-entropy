# 224 nop-ai-agent call-agent 异步 mailbox 请求-响应模型（Actor Runtime Phase 2）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-8-call-agent-async
> Last Reviewed: 2026-06-17
> Source: carry-over from `ai-dev/plans/218-nop-ai-agent-actor-runtime.md`（Non-Blocking Follow-ups：`call-agent 异步 mailbox 模型（vision §10 Phase 2 剩余）：将 call-agent 从 fork+exec 迁移到 Actor mailbox REQUEST/RESPONSE。Classification: successor plan required`）+ `ai-dev/plans/220-nop-ai-agent-steering-injection.md`（Non-Blocking Follow-ups：`call-agent 异步`）+ `ai-dev/plans/223-nop-ai-agent-team-manager.md`（Non-Goals：`call-agent 异步 mailbox 模型... 独立 successor plan required`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` §3.3（Actor 间通信：同步请求-响应）+ §10 Phase 2；`ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md` §5.1（父子通信）+ §6（Phase 2 目标）
> Related: `168`（交付 `call-agent` fork+exec MVP + `send-message` fire-and-forget）、`166`（交付 `IAgentMessenger` request-response + `LocalAgentMessenger`）、`216`（交付 `IMailbox` deferred-ack 邮箱）、`218`（交付 Actor Runtime 基础层）、`220`（交付 steering 注入）

## Purpose

把 nop-ai-agent 的 `call-agent` 工具从"直接 `IAgentEngine.execute()` fork+exec 同步执行子 Agent"扩展为"可选经 `IAgentMessenger.request()` 路由的异步请求-响应模型"。本计划只负责这一件事：当引擎装配了功能性 messenger 时，`call-agent` 的子 Agent 执行请求经 messenger request-response 通路投递与回复，使调用路径可观测、解耦 Caller 对 Callee 引擎的直接依赖、并为跨进程路由（DBMessageService 部署）做好准备。shipped 默认（NoOpAgentMessenger）保留既有 fork+exec 行为，零回归。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-17）：

- **`CallAgentExecutor`（fork+exec 模型）已交付**（plan 168 / L4-1b ✅）：`io.nop.ai.agent.tool.CallAgentExecutor` 直接调用 `IAgentEngine.execute(AgentMessageRequest)` 同步执行子 Agent，经 `.orTimeout(timeoutMs)` 超时保护，结果转 `AiAgentCallResult`。支持三种子 session 模式：continue existing（sessionId 非空）/ fork from parent（agentId="self" + inheritContext）/ create new（默认）。路径注入防护（finding [13-16]）+ 父权限约束传播（plan 169）已内嵌。`executeAsync` 返回 `CompletionStage<AiToolCallResult>`，fail-fast 返回 error result 而非抛异常。
- **`IAgentMessenger` request-response 已交付**（plan 166 / L4-1 ✅）：`request(AgentMessageEnvelope, Duration) → CompletableFuture<Object>`——发送 REQUEST 信封到 target topic，在共享 reply topic（`agent.{senderId}.reply`）按 `correlationId` demux 接收 RESPONSE。`registerHandler(topic, handler)` 注册 `IAgentMessageHandler`；当 handler 对 REQUEST 信封返回非 null 值时，messenger 将返回值路由为 RESPONSE 到 requester 的 reply topic。`LocalAgentMessenger` 功能实现基于 `LocalMessageService`；`NoOpAgentMessenger` shipped 默认（`request()` 抛 `UnsupportedOperationException`，`send()` debug-log no-op，`registerHandler()` 返回 inert subscription）。
- **`AgentMessageEnvelope` + `AgentMessageKind` 已交付**（plan 166）：不可变信封（senderId, targetTopic, correlationId, kind, payload, timestamp）；kind 枚举 REQUEST / RESPONSE / ASYNC。
- **`AgentMessageTopics` 命名约定已交付**：`inboxTopic(sessionId)` = `agent.{sessionId}.inbox`；`replyTopic(sessionId)` = `agent.{sessionId}.reply`。
- **Actor Runtime 基础层已落地**（plan 218 / L4-8 ✅）：`IActorRuntime`（`isEnabled()` gate + createActor/destroyActor）+ `NoOpActorRuntime` shipped 默认（`isEnabled()=false`）+ `InMemoryActorRuntime` 功能实现。`DefaultAgentEngine` 三入口点（doExecute/resumeSession/restoreSession）经 `isEnabled()` guard opt-in 注册/注销 Actor。
- **Steering 注入已落地**（plan 220 / L4-8-steering ✅）：Actor 消费循环 poll mailbox → 转 ChatMessage → enqueue 到 ctx steering queue；ReAct 循环 round 边界 drain。
- **`IMailbox` deferred-ack 邮箱已落地**（plan 216 / L4-5 ✅）：`ensureSessionMailbox`（`DefaultAgentEngine:2401`）经 `mailboxFactory` 为每个 session 创建 `IMailbox` + 在 `agent.{sessionId}.inbox` 注册 `MailboxMessageHandler`（offer ASYNC 消息到 mailbox）。
- **零 async call-agent 代码存在**：grep `AsyncCallAgent|mailbox.*request.*response|call-agent.*async|agent\.call-agent` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 实现命中。`CallAgentExecutor` 当前是唯一的 `call-agent` 实现，且为纯 fork+exec。
- **vision §3.3 + §10 Phase 2**：Phase 2 标注"call-agent 异步模式"为 Actor Runtime Phase 2 目标——"Caller 发 REQUEST 到 Callee inbox、等待 actor 响应"。Phase 2 的 steering 注入部分（plan 220）已交付，call-agent 异步是 Phase 2 唯一剩余项。
- **multi-agent.md §6 Phase 2 目标**：明确写"基于 mailbox 的 call-agent 模型（发 REQUEST 到目标 inbox、等待 actor 响应）是 Actor Runtime (Phase 2+) 的目标"。

## Goals

- `CallAgentExecutor` 在功能性 messenger 可用时，可选经 `IAgentMessenger.request()` 投递子 Agent 执行请求（REQUEST 信封），而非直接调用 `engine.execute()`。Caller 挂起等待 RESPONSE（CompletableFuture），超时保护不变。
- 引擎层提供 request handler：接收 call-agent REQUEST 信封 → 创建/继续子 Agent session → 执行子 Agent ReAct 循环 → 返回执行结果作为 RESPONSE。
- shipped 默认（`NoOpAgentMessenger`）保留既有 fork+exec 行为——`CallAgentExecutor` 无行为变化，全量测试零回归。
- REQUEST/RESPONSE 载荷契约定义清晰（见 §Design Decisions）：REQUEST payload 携带 `(targetAgentId, input, resolvedSessionId(nullable), parentConstraintMetadata, timeoutMs)`——其中 `resolvedSessionId` 由 CallAgentExecutor 侧完成全部 session-mode 分支解析（continue / fork-from-parent / create-new）后填充；RESPONSE payload 携带 `(status, sessionId, finalMessage, error)`。
- 调用路径经 messenger 可观测（LOG + 信封 correlationId 追踪），解耦 Caller 对 Callee 引擎实例的直接引用。
- 端到端验证：功能性 messenger 配置下，parent Agent 经 call-agent 工具 → messenger request-response → handler 执行子 Agent → RESPONSE 返回 → tool result 回灌 ReAct 循环，完整路径跑通。
- vision §10 Phase 2 "call-agent 异步模式"标注已落地。

## Non-Goals

- **Per-session inbox 路由**（vision §3.3 "发 REQUEST 到 Callee inbox"）：将 REQUEST 投递到 `agent.{calleeSessionId}.inbox` 而非引擎级 topic。foundational slice 使用引擎级统一 topic（如 `agent.call-agent`），per-session 路由是 successor（需 callee Actor 已存在 + 多 handler per topic 支持裁定）。
- **异步非阻塞 handler**：handler 同步阻塞等待 `engine.execute()` 完成（`.join()`）。非阻塞 handler（返回 CompletableFuture、不阻塞 messenger dispatch 线程）是 successor。
- **跨进程 call-agent 路由**：经 `DBMessageService` 跨进程投递 REQUEST/RESPONSE。需 DBMessageService 部署 + 载荷序列化加固。是 successor（依赖部署配置）。
- **call-agent 工具 schema / LLM 契约变更**：工具参数（agentId, sessionId, input, inheritContext, timeoutMs）与返回格式不变。异步路由是引擎内部实现细节，LLM 不可见。
- **`send-message` 异步迁移**：`send-message` 已是 fire-and-forget（ASYNC），无需迁移。
- **团队通信工具**（`team-task-create` / `team-send-message` / `team-status`）：plan 223 successor，依赖 TeamManager + 本计划交付的 async 通路。
- **call-agent 执行期间对 callee 的 steering**：plan 220 已交付 steering 注入（Actor 消费循环 poll → steering queue）。本计划不改变 steering 行为。
- **XDSL 配置化**：`agent.xdef` 增加 async call-agent 开关。是 optimization candidate。
- **nop-task / nop-stream 集成**：Agent 编排接入 nop-task DAG。是 out-of-scope improvement。

## Scope

### In Scope

- `DefaultAgentEngine`——新增引擎级 call-agent request handler 注册（功能性 messenger 可用时在 call-agent topic 注册 handler，handler 内部调用 `engine.execute()` 执行子 Agent 并返回结果）
- `CallAgentExecutor`——新增 async pathway（messenger 功能性可用时走 `messenger.request()`，否则 fall back 既有 fork+exec）；**CallAgentExecutor 保留全部 session-mode 分支解析**（continue / fork-from-parent / create-new），fork 模式下先同步调用 `engine.forkSession()` 获得 child sessionId 再将其填入 REQUEST payload
- `AgentMessageTopics`——新增 `callAgentTopic()` helper（或 `CALL_AGENT_TOPIC` 常量），作为 call-agent topic 名称的唯一来源（禁止在 `AgentMessageTopics` 之外出现字符串字面量）
- call-agent REQUEST/RESPONSE 载荷数据对象（不可变，携带执行参数 / 执行结果）
- 测试文件（async pathway focused tests + 端到端 test：功能性 messenger + CallAgentExecutor + handler 完整路径）

### Out Of Scope

- per-session inbox 路由（Non-Goal）
- 异步非阻塞 handler（Non-Goal）
- 跨进程路由（Non-Goal）
- 工具 schema 变更（Non-Goal）
- 团队通信工具（Non-Goal）

## Execution Plan

### Design Decisions (Pre-Adjudicated)

以下裁定在 plan 撰写阶段已确定，执行时直接遵循，不再作为 in-flight Decision。

1. **Session-mode 分支解析归属**：`CallAgentExecutor` 保留全部三种子 session 模式分支（continue existing / fork-from-parent / create-new），与现有 fork+exec 逻辑一致。fork 模式下 CallAgentExecutor 先同步调用 `engine.forkSession()` 获得 child sessionId，再将已解析的 `resolvedSessionId` 填入 REQUEST payload。**handler 不做分支**——handler 只调用 `engine.execute(AgentMessageRequest)`，与现有 fork+exec 的 `executeSubAgent` 路径一致。理由：handler 保持简单，避免在 messenger 侧复制 CallAgentExecutor 的分支逻辑 + 父 session 上下文获取。

2. **Async 激活条件**：`CallAgentExecutor` 通过 `agentCtx.getMessenger()` 获取 messenger，使用 `instanceof NoOpAgentMessenger` 判定功能性（遵循 `SendMessageExecutor:93-104` 既有模块约定）。非 NoOp → async pathway；NoOp → fork+exec fallback。不额外要求 `actorRuntime.isEnabled()`（async call-agent 的路由不依赖 Actor 生命周期）。

3. **REQUEST/RESPONSE 载荷契约**：
   - REQUEST payload（不可变 POJO）：`targetAgentId: String`, `input: String`, `resolvedSessionId: String(nullable)`, `parentConstraintMetadata: Map<String,Object>(nullable)`, `timeoutMs: long`
   - RESPONSE payload（不可变 POJO）：`status: String("success"|"failure")`, `sessionId: String(nullable)`, `finalMessage: String`, `error: String(nullable)`
   - `finalMessage` 提取逻辑 = `AgentExecutionResult.getMessages()` 中最后一个 `ChatAssistantMessage` 的 content（复用 `CallAgentExecutor.extractFinalMessage` 逻辑，或提取为共享 helper）
   - 载荷字段均为 String/Map/long，可经 messenger 序列化传输

4. **REQUEST 信封 senderId / correlationId**：
   - `senderId` = parent session 的 sessionId（`agentCtx.getSessionId()`），null 时 fallback `"unknown-sender"`（遵循 `SendMessageExecutor:81-89` 既有约定）
   - `correlationId` = 每个 REQUEST 生成 fresh UUID（避免 `LocalAgentMessenger` pendingRequests demux 冲突）
   - `targetTopic` = `AgentMessageTopics.callAgentTopic()`

5. **Handler 异常处理**：handler 内部 try/catch 包裹 `engine.execute().orTimeout(payload.timeoutMs, TimeUnit.MILLISECONDS).join()`——`engine.execute()` 无内建超时（与 fork+exec 在 `CallAgentExecutor:216` 显式 `.orTimeout()` 一致），handler 须自行施加超时。任何异常（含子 Agent 执行失败、fork 失败、`CompletionException(TimeoutException)` 超时）均捕获并返回 `RESPONSE(status="failure", error=<message>)`。**不**让异常传播到 messenger dispatch 层（`LocalAgentMessenger` 会吞异常返回 null → requester 挂起至超时）。超时后 handler 线程释放（不无限阻塞）。

6. **Handler 注册生命周期**：注册发生在 `DefaultAgentEngine.setMessenger` 内部。idempotent：先 cancel 既有 call-agent subscription（如有），再注册新 handler。engine 持有 `IMessageSubscription callAgentSubscription` 字段跟踪。NoOp messenger 时不注册（零回归）。`setMessenger(null)` 或 `setMessenger(NoOp)` 时 cancel 既有 subscription。

7. **Handler 同步阻塞语义**：handler 调用 `engine.execute()` 返回 `CompletableFuture`，handler 内部 `.join()` 阻塞等待。`LocalAgentMessenger` 的 dispatch 在 sender 线程上同步执行 handler——handler 阻塞期间 sender 线程（通常是 parent ReAct 的 ForkJoinPool 线程）被占用。

8. **已知限制——嵌套 call-agent 死锁风险**：parent Agent 经 ForkJoinPool.commonPool 线程执行 → async call-agent → `LocalAgentMessenger` 同步 dispatch → handler `.join()` 阻塞该线程等待子 Agent `engine.execute()`（也提交到 commonPool）。单层嵌套安全（commonPool 有多个线程）；**多层嵌套 + commonPool 饱和**可能死锁。foundational slice 接受此限制（非阻塞 handler successor 解决），plan 须在 Non-Blocking Follow-ups 显式记录。

### Phase 1 - 引擎级 request handler + CallAgentExecutor async gate + 载荷对象

Status: completed
Targets: `DefaultAgentEngine.java`（handler 注册）、`CallAgentExecutor.java`（async gate + fallback）、新载荷数据对象

- Item Types: `Fix | Decision | Proof`

- [x] 定义 REQUEST/RESPONSE 载荷不可变数据对象（字段见 Design Decisions §3），全参构造 + getter，无 setter
- [x] `AgentMessageTopics` 新增 `callAgentTopic()` helper（返回 `agent.call-agent`），作为 call-agent topic 名称唯一来源
- [x] `DefaultAgentEngine.setMessenger` 新增 call-agent handler 注册（Design Decisions §6）：idempotent——先 cancel 既有 subscription 再注册新 handler，持有 `IMessageSubscription callAgentSubscription` 字段。handler 内部 try/catch 包裹 `engine.execute().join()`（Design Decisions §5），异常时返回 `RESPONSE(status="failure")`，不让异常传播到 messenger dispatch 层。NoOp messenger 下不注册。
- [x] `CallAgentExecutor` 新增 async gate（Design Decisions §2）：`instanceof NoOpAgentMessenger` 判定。async pathway：保留全部 session-mode 分支解析（continue/fork/create-new，与 fork+exec 一致）→ 构建 REQUEST 信封（senderId/correlationId 见 Design Decisions §4）→ `messenger.request(envelope, timeout)` → RESPONSE 载荷转 `AiToolCallResult`。fallback pathway：既有 fork+exec 逻辑不变。
- [x] 编写 focused 测试：NoOp messenger 下 CallAgentExecutor 走 fork+exec（行为不变，既有断言通过）；功能性 messenger 下 handler 注册验证（`AgentMessageTopics.callAgentTopic()` 上有 handler）；载荷数据对象不可变性测试
- [x] 编写 Phase 1 集成测试：`CallAgentExecutor.executeAsync` + 功能性 `LocalAgentMessenger` + engine-registered handler → 断言 handler.onMessage 被触发 + RESPONSE 载荷正确返回。此测试验证 CallAgentExecutor↔messenger↔handler 完整链路（不依赖 parent ReAct 循环）。

Exit Criteria:

- [x] REQUEST/RESPONSE 载荷数据对象存在且不可变（无 setter，全参构造 + getter，字段匹配 Design Decisions §3）
- [x] `AgentMessageTopics.callAgentTopic()` helper 存在；grep 确认 `agent.call-agent` 字面量仅出现在 `AgentMessageTopics`（路由字面量唯一源于 `CALL_AGENT_TOPIC` 常量；其他匹配为 LOG metric-key 与测试断言，非路由字面量）
- [x] `DefaultAgentEngine.setMessenger` 在功能性 messenger 时 idempotent 注册 call-agent handler；NoOp messenger 时不注册；重复 `setMessenger` 不 double-register（测试断言 subscription cancel + re-register）
- [x] `CallAgentExecutor` async gate 正确判定：非 NoOp messenger → async pathway；NoOp → fork+exec fallback
- [x] handler try/catch 包裹 `engine.execute()`：异常返回 `RESPONSE(status="failure")`，不让异常传播到 messenger dispatch（测试注入失败子 Agent 断言 handler 返回 error RESPONSE 而非抛异常）
- [x] **接线验证**（Minimum Rules #23）：Phase 1 集成测试断言 handler.onMessage 在 `CallAgentExecutor.executeAsync` 调用后被触发（完整链路 CallAgentExecutor→messenger→handler 连通）
- [x] **无静默跳过**（Minimum Rules #24）：async pathway 中各分支有真实实现（非空方法体）；NoOp fallback 是显式 instanceof 分支（不是 catch-and-ignore）
- [x] No owner-doc update required（vision §10 Phase 2 标注在 Phase 2 更新）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 功能实现端到端验证 + 设计文档同步

Status: completed
Targets: 端到端测试、`nop-ai-agent-actor-runtime-vision.md` §10、`nop-ai-agent-multi-agent.md` §6

- Item Types: `Proof`

- [x] 编写端到端测试：配置 `DefaultAgentEngine`（功能性 `LocalAgentMessenger` + mock LLM）→ parent Agent ReAct 循环调用 `call-agent` 工具 → CallAgentExecutor async pathway 发 REQUEST → handler 接收 → `engine.execute()` 执行子 Agent → RESPONSE 返回 → tool result 回灌 parent ReAct → parent 继续。断言：REQUEST 经 messenger 路由（非直接 engine.execute）、RESPONSE payload 正确、parent 拿到子 Agent 结果。
- [x] 编写三种子 session 模式 async pathway 测试：(a) continue-existing（sessionId 非空）、(b) fork-from-parent（agentId="self" + inheritContext=true，CallAgentExecutor 先同步 forkSession 再 async REQUEST）、(c) create-new（sessionId=null）。每种模式断言结果形态与等效 fork+exec 一致。
- [x] 编写 handler 异常测试：注入失败子 Agent（mock LLM 抛异常）→ handler try/catch 捕获 → 返回 `RESPONSE(status="failure")` → CallAgentExecutor 收到 fast error RESPONSE（断言在远小于 timeoutMs 的时间内返回，非超时）
- [x] 编写超时测试：async pathway 下 REQUEST 超时（handler 执行超过 timeoutMs）→ CallAgentExecutor 返回 timeout error result（与 fork+exec 的 orTimeout 行为一致）
- [x] 编写 fallback 回退测试：messenger 为 NoOp 时 CallAgentExecutor 走 fork+exec（既有 TestCallAgentExecutor 断言全绿，零回归）
- [x] 验证全量测试零回归：`./mvnw test -pl nop-ai/nop-ai-agent -am`（默认 NoOp messenger 配置下既有 2153+ tests 全绿）
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` §10 Phase 2：call-agent 异步模式从"successor"标注为"🟡 部分落地"（引擎级 topic 路由已交付，per-session inbox 路由为 successor）
- [x] 更新 `nop-ai-agent-multi-agent.md` §6 Phase 2：call-agent async mailbox 模型标注 foundational 已落地

Exit Criteria:

- [x] **端到端验证**（Minimum Rules #22）：从 parent Agent `engine.execute()` 入口 → ReAct 循环 → call-agent 工具 async pathway → messenger request → handler → 子 Agent execute → RESPONSE → tool result → parent ReAct 继续，完整路径跑通且有测试覆盖
- [x] 三种子 session 模式（continue-existing / fork-from-parent / create-new）经 async pathway 测试覆盖，结果形态与 fork+exec 一致
- [x] handler 异常 → fast error RESPONSE（非静默超时）测试通过
- [x] 超时行为与 fork+exec 一致（handler 施加 `orTimeout` 后 `.join()`，线程在 timeoutMs 后释放；requester 侧 `messenger.request` 超时一致）
- [x] NoOp messenger 默认配置下既有 TestCallAgentExecutor + 全量测试零回归
- [x] vision §10 Phase 2 + multi-agent.md §6 已更新（call-agent 异步模式 foundational 落地标注）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] async call-agent pathway 经 messenger request-response 通路可运行（功能性 messenger 配置下）
- [x] shipped 默认（NoOpAgentMessenger）保留 fork+exec 行为，全量测试零回归
- [x] REQUEST/RESPONSE 载荷契约定义清晰且可序列化
- [x] 引擎级 handler 正确注册与注销（NoOp 下不注册）；`setMessenger` idempotent（重复调用不 double-register）
- [x] handler 异常返回 error RESPONSE（非静默超时）
- [x] 三种子 session 模式经 async pathway 覆盖
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs（vision §10 / multi-agent.md §6）已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：handler 在运行时确实被 messenger 调用（不只是注册存在）；async pathway 端到端路径连通
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；per-session inbox 路由 / 异步非阻塞 handler / 跨进程路由 / XDSL 配置化 / 团队通信工具 / nop-task 集成均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **Per-session inbox 路由**（vision §3.3）：REQUEST 投递到 `agent.{calleeSessionId}.inbox` 而非引擎级 topic。需 callee Actor 已存在 + 多 handler per topic 支持（当前 `MailboxMessageHandler` 已占 inbox topic）。Classification: successor plan required。
- **异步非阻塞 handler**：handler 返回 CompletableFuture 而非阻塞 messenger dispatch 线程。**解决嵌套 call-agent 死锁风险**（Design Decisions §8：多层嵌套 + ForkJoinPool.commonPool 饱和可能死锁）。Classification: successor plan required。
- **跨进程 call-agent 路由**：经 `DBMessageService` 跨进程投递。需载荷序列化加固 + DBMessageService 部署。Classification: successor plan required。
- **XDSL 配置化**：`agent.xdef` 增加 async call-agent 开关。Classification: optimization candidate。
- **团队通信工具**（plan 223 successor）：`team-task-create` / `team-send-message` / `team-status` IToolExecutor 经本计划交付的 async 通路。Classification: successor plan required。

## Closure

Status Note: plan 224 交付 call-agent 异步 mailbox 请求-响应模型的 foundational slice。`CallAgentExecutor` 在功能性 `IAgentMessenger` 可用时经 `IAgentMessenger.request()` 投递 REQUEST 到引擎级 `agent.call-agent` topic，引擎在 `setMessenger` 时 idempotent 注册 handler 执行子 Agent 并返回 RESPONSE；shipped 默认（NoOpAgentMessenger）保留 fork+exec 零回归。per-session inbox 路由 / 异步非阻塞 handler / 跨进程路由为显式 successor（Non-Blocking Follow-ups）。
Completed: 2026-06-17

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (task_id: ses_12e6de604ffeM5BRaQaw2gL8pL)
- Audit Session: ses_12e6de604ffeM5BRaQaw2gL8pL
- Evidence:
  - **A (payload immutability) PASS**: `CallAgentRequestPayload.java` final class + all-args ctor + getters + NO setters + defensive Map copy (L51-53); `CallAgentResponsePayload.java` same shape. Fields match Design Decisions §3.
  - **B (callAgentTopic single-source) PASS**: `AgentMessageTopics.java:45` `CALL_AGENT_TOPIC="agent.call-agent"` + `callAgentTopic()` L103-105; grep confirms routing literal single-sourced (其余匹配为 LOG metric-key 与测试断言，非路由字面量).
  - **C (handler registration) PASS**: `DefaultAgentEngine.registerCallAgentHandler` idempotent (cancel-then-register L657-674), NoOp opt-out (L667-669), try/catch wraps `engine.execute().orTimeout().join()` returns failure RESPONSE non-propagating (L703-728), `callAgentSubscription` field L154.
  - **D (async gate) PASS**: `CallAgentExecutor.dispatch` L196-207 `instanceof NoOpAgentMessenger` gate; `executeViaMessenger` L222-264 builds REQUEST envelope (UUID correlationId) + `messenger.request()`; session-mode resolution preserved BEFORE payload; fork mode calls `engine.forkSession()` L175; no empty bodies/silent no-ops.
  - **E (tests real) PASS**: `TestCallAgentAsyncMailbox` 13 @Test + `TestCallAgentAsyncEndToEnd` 7 @Test — real assertions, integration test asserts sub-agent actual LLM output, E2E asserts sentinel string flows back, 3 session modes + fast-failure + timeout + fallback covered.
  - **F (NoOp zero-regression) PASS**: `TestCallAgentExecutor.java` intact (11 @Test, uses `NoOpAgentMessenger.noOp()`).
  - **G (owner docs) PASS**: vision §10 Phase 2 (L431 + note L433) + multi-agent.md §5.1 (L151) + §6 (L178 + note L182) mark plan 224 foundational landing.
  - **H (Anti-Hollow) PASS**: runtime call chain `CallAgentExecutor.executeViaMessenger`→`messenger.request`→`LocalMessageService.send`→`HandlerAdapter.onMessage`→`handleCallAgentRequest`→`engine.execute` 全部真实代码；E2E 测试 sentinel string `"ASYNC_E2E_SUB_RESULT_123"` 仅在子 Agent chat round 产生，其出现在 parent tool-response message 证明 handler 运行时确实执行了子 Agent。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（所有 checklist 已勾选 + Closure Evidence 已写入）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（0 critical/high findings）
  - `./mvnw compile -pl nop-ai/nop-ai-agent -am` → BUILD SUCCESS
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS, **2173 tests, 0 failures, 0 errors**（既有 2153 + 13 Phase 1 + 7 Phase 2，零回归）
  - Deferred 项分类检查：per-session inbox 路由 / 异步非阻塞 handler / 跨进程路由 / XDSL 配置化 / 团队通信工具均为显式 Non-Goals successor（Non-Blocking Follow-ups 已记录 Why Not Blocking），无 in-scope live defect 被降级

Follow-up:

- per-session inbox 路由（successor plan required）
- 异步非阻塞 handler / 解决嵌套 call-agent 死锁风险（successor plan required）
- 跨进程 call-agent 路由 via DBMessageService（successor plan required）
- XDSL 配置化（optimization candidate）
- 团队通信工具 team-task-create/team-send-message/team-status（plan 223 successor）
