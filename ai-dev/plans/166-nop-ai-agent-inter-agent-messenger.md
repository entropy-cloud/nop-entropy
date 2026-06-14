# 166 nop-ai-agent Inter-Agent Messenger (L4-1)

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-1

> Last Reviewed: 2026-06-14
> Source: Carry-over from plan 161 (`ai-dev/plans/161-nop-ai-agent-session-fork.md`, Deferred "call-agent 工具 fork+exec 集成 — Successor Path: 未来 Layer 4 plan（L4-1+ IMessageService / call-agent 工具）", Successor Required: yes); roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4 L4-1; design `ai-dev/design/nop-ai-agent/01-architecture-baseline.md` §六 通信模型, `nop-ai-agent-actor-runtime-vision.md` §3.3 / §4.2
> Related: Plan 161 (session fork — successor path explicitly names "L4-1+ IMessageService"), Plan 134 (L1-1 IAgentEngine Actor 消息入口)

## Purpose

为 nop-ai-agent 建立 Agent 间通信的消息基础设施：一个 Agent 域 messenger 抽象，叠加在已有的 Nop 平台 `IMessageService` / `LocalMessageService` 之上，提供可工作的内存实现并接入 `DefaultAgentEngine`。这解除 call-agent / send-message 工具（plan 161 deferred）与 Actor Runtime（L4-8）的阻塞，并落地设计文档中"Agent 间通信统一通过 IMessageService 抽象层"的契约。

## Current Baseline

- 平台 `io.nop.api.core.message.IMessageService`（extends `IMessageSender` + `IMessageSubscriber`）已存在于 `nop-kernel/nop-api-core` —— topic pub/sub，提供 `send`/`sendAsync`/`subscribe`
- 平台 `io.nop.message.core.local.LocalMessageService` 已存在于 `nop-message/nop-message-core` —— 内存 `ConcurrentHashMap<topic, CopyOnWriteArrayList<Subscription>>` 分发，支持 CompletionStage
- `nop-ai-agent` 的 pom.xml **未**依赖 `nop-message-core`（已核实）；`nop-api-core` 经 `nop-ai-core` → `nop-api-core` 传递可用
- `nop-ai-agent` 中对 `IMessageService`/`IMessageSender`/`IMessageSubscriber`/`io.nop.message` 的引用为 **零**（grep 已确认）
- `DefaultAgentEngine` 对可选扩展使用 setter 注入（`setTalents`/`setSkillProvider`/`setToolCallRepairer`）；默认值不统一（空集合 / NoOp 实例 / null）—— 本计划遵循 `setSkillProvider` 的 NoOp-instance 模式（非 null 默认）
- `IAgentEventPublisher`（进程内事件 pub/sub）已存在，但服务于不同关注点（Agent 执行事件推送给外部订阅者）；Agent 间消息通信是独立信道
- `AgentMessageRequest`/`AgentMessageAck`/`AgentEvent` 是现有引擎 I/O 类型（单 session、请求-响应）；不存在 Agent 间消息信封
- 设计文档定义了契约：`01-architecture-baseline.md` §六（LocalMessageService = 单进程，DB-backed = 多实例），`nop-ai-agent-actor-runtime-vision.md` §3.3（topic 命名 `agent.{actorId}.inbox`，call-agent 同步，send-message 异步），§4.2（MessageRouter = LocalMessageService + topic 命名约定）
- Plan 161 Deferred "call-agent 工具 fork+exec 集成" 将 "L4-1+ IMessageService" 列为 successor path

## Goals

- 一个 Agent 域 messenger 抽象（接口），在平台 `IMessageService` 之上适配 Agent 间通信，提供 fire-and-forget 发送 **以及** 请求-响应（返回 `CompletableFuture`）两种语义
- 一个可工作的内存实现，底层由平台 `LocalMessageService` 承载
- 一个 pass-through NoOp 默认（与本模块每个扩展点一致），使引擎在零配置下保持向后兼容
- Topic 命名约定 helper（`agent.{sessionId}.inbox`、`agent.{sessionId}.reply`）
- Messenger 通过 setter 接入 `DefaultAgentEngine`（默认 NoOp）
- 端到端验证：从一个 Agent 端点发出的消息到达另一端点的已注册消费者；且一个请求通过 `CompletableFuture` 收到其响应
- 记录决策：复用平台 `IMessageService`/`LocalMessageService`，**不**重复造轮子

## Non-Goals

- `DBMessageService`（跨进程路由，L4-2 —— 需要 nop-dao）
- `call-agent` / `send-message` 工具（消费本 messenger 的独立 Layer 4 工作项）
- Actor Runtime / AgentActor / Mailbox 生命周期（L4-8 —— 建立在 L4-1~L4-6 之上）
- Team mode、协调信道、scope_claim（nop-ai-agent-multi-agent.md §4）
- Deferred-ack mailbox（L4-5）
- 多租户消息隔离（actor runtime Phase 2+）
- 在途消息的恢复 / 持久化（内存实现崩溃即丢消息 —— 符合设计 §六 注记的预期行为）
- 请求-响应失败传播（responder 抛异常时，MVP 仅向 requester 暴露 `TimeoutException`；信封不含 error/exception 字段；error envelope 为后续 plan）

## Scope

### In Scope

- Decision：复用平台 `IMessageService`/`LocalMessageService`（不重复）
- `nop-message-core` 依赖加入 `nop-ai-agent` pom.xml
- Agent 域消息信封类型（sender id、target topic、correlationId、payload、message kind）
- Topic 命名约定 helper
- Agent 域 messenger 接口（send-async + request-response + register-handler）
- 基于 `LocalMessageService` 的内存实现
- NoOp pass-through 默认（send-request fail-fast，非静默）
- `DefaultAgentEngine` setter 接线
- 单元 + 集成测试（端到端 send/receive + request/response）
- 设计文档更新（标记 messenger 层已接线）

### Out Of Scope

- DB-backed 实现（L4-2）
- call-agent / send-message 工具
- Actor Runtime 平台层（L4-8）
- 跨进程 / 分布式消息

## Execution Plan

### Phase 1 - 依赖 + Agent 消息信封 + Topic 约定

Status: completed
Targets: `nop-ai/nop-ai-agent/pom.xml`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/`（新包）

- Item Types: `Decision | Fix | Proof`

- [x] **Decision**：复用平台 `io.nop.api.core.message.IMessageService` + `io.nop.message.core.local.LocalMessageService`；记录理由（设计 §六 / actor-runtime §2.2 明确复用 Nop 平台，避免重复）。**不**新建通用 message-service 接口
- [x] 在 `nop-ai/nop-ai-agent/pom.xml` 增加 `nop-message-core` 依赖
- [x] 定义 Agent 消息信封（不可变值对象）：sender session/agent id、target topic、correlationId、message kind（REQUEST/RESPONSE/ASYNC）、payload 对象、timestamp
- [x] **Decision（topic 命名空间与平台常量的关系，M4）**：Agent 域 topic 命名有意采用独立的 `agent.*` 命名空间，**不复用**平台 `MessageCoreConstants` 的 `bro-`/`reply-`/`bat-` 前缀。理由：(1) 平台 `bro-` 前缀被 `LocalMessageService.getBroadcastTopics()` 识别后会触发特殊的多路分发逻辑，而 Agent 域消息是点对点投递（inbox）+ 共享 reply topic，不依赖平台 broadcast 语义；(2) `agent.*` 前缀避免与平台 broadcast/reply/batch topic 在同一 `ConcurrentHashMap<topic, ...>` 命名空间中混淆，便于直达投递与 introspection 独立统计；(3) `01-architecture-baseline.md` §六 与 actor-runtime §3.3 已规定 `agent.{actorId}.inbox` 命名约定，本计划延续该约定。Agent 域若需多订阅者广播，自行在多个 inbox topic 上 fan-out，不经平台 `bro-` 前缀
- [x] 定义 topic 命名约定 helper：inbox topic `agent.{sessionId}.inbox`、reply topic `agent.{sessionId}.reply`（**共享 reply topic**，单个 session 一个，由信封 correlationId 区分不同在途请求；**不**在 topic 名中嵌入 correlationId）、broadcast topic（`agent.broadcast.{scope}`，Agent 域自行 fan-out）
- [x] 单元测试：信封构造/访问；topic helper 产出预期格式

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-message-core` 出现在 `nop-ai-agent/pom.xml` 且 `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] Agent 消息信封类型存在于 `io.nop.ai.agent.message` 包，含上述行为字段
- [x] Topic helper 产出 `agent.{id}.inbox` 与 `agent.{id}.reply`（共享 reply topic，无 correlationId 嵌入 topic 名）格式（单元测试验证）
- [x] **新增功能测试**：信封 + topic helper 单元测试通过
- [x] No owner-doc update required（Phase 3 统一更新 design doc）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Messenger 接口 + 内存实现 + NoOp 默认

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/`（接口、Local 实现、NoOp 实现），`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/message/`（新测试）

- Item Types: `Fix | Proof`

- [x] **Decision（handler 契约形状，M5）**：messenger 接口接受的是 **Agent 域 handler**（行为契约：接收 Agent 消息信封，可返回一个响应对象；返回非 null 表示这是对该请求的响应）。**不**直接暴露平台 `IMessageConsumer`（其 `onMessage` 返回 Object 会被平台自动路由到 ack-topic）。Agent 域 handler 是一个面向 Agent 语义的新函数式契约，与平台消息类型解耦
- [x] **Decision（ack-topic 自动路由冲突规避，M2）**：平台 `LocalMessageService.handleMessageResult` 会将 consumer 的非 null 返回值自动路由到 `getAckTopic(topic)`（即 `ack-{topic}`）。内存实现将 Agent 域 handler 适配为平台 `IMessageConsumer` 时，适配层**捕获** handler 的返回值，**显式发送**到 reply topic（`agent.{sessionId}.reply`），然后向 `IMessageService` 返回 **null** 以抑制平台的自动 ack-topic 路由。行为规格：responder handler 的非 null 返回值必须出现在 requester 订阅的 reply topic 上，而不是 responder 自己的 ack-topic 上；否则 requester 的 `CompletableFuture` 会永远超时
- [x] 定义 Agent 域 messenger 接口（行为契约）：fire-and-forget 发送到 topic；请求-响应返回 `CompletableFuture`（含超时）；为 topic 注册 Agent 域 handler 返回可取消 subscription
- [x] 实现基于平台 `LocalMessageService` 的内存 messenger：send-async 委托 `IMessageService.send`；请求-响应经共享 reply topic 订阅 + `CompletableFuture.orTimeout` + correlationId 匹配实现（多个在途请求共享同一 reply topic subscription，按信封 correlationId 分发到对应 future）；register-handler 委托 `IMessageService.subscribe`，并在适配层执行上述 ack-topic 抑制逻辑
- [x] 实现 NoOp pass-through 默认：send-async 为 no-op（debug 日志）；register-handler 返回 no-op subscription；**请求-响应 fail-fast**（抛 `UnsupportedOperationException("not yet implemented: no message service configured")`）—— **非**静默 null
- [x] 单元测试：send-async → 已注册 handler 收到信封
- [x] 单元测试：请求-响应 → handler 返回响应 → `CompletableFuture` 以响应 payload 完成（**验证响应到达 reply topic 而非 responder 的 ack-topic**）
- [x] 单元测试：请求-响应超时 → 无回复时 `CompletableFuture` 以 TimeoutException 异常完成
- [x] 单元测试：回复 correlation —— 共享 reply topic 上延迟/不匹配的 correlationId **不会**完成另一个请求的 future
- [x] 单元测试：NoOp 默认 —— 请求-响应抛异常（fail-fast），send-async 不抛

Exit Criteria:

- [x] Messenger 接口存在于 `io.nop.ai.agent.message`，含三种行为（send-async、request-response、register-handler）；handler 契约为 Agent 域类型（非平台 `IMessageConsumer`）
- [x] 内存实现经由平台 `LocalMessageService` 路由消息（非自建重复队列）；handler→`IMessageConsumer` 适配层显式抑制 ack-topic 自动路由
- [x] **端到端验证**：send → handler 收到确切信封 payload（完整路径经平台 IMessageService）
- [x] **端到端验证**：request → handler 返回响应 → 响应经共享 reply topic 到达 → CompletableFuture 完成（完整请求-响应往返经平台 IMessageService，响应**未**误投到 responder ack-topic）
- [x] **无静默跳过**：NoOp 请求-响应抛 `UnsupportedOperationException`，非静默 null/空 future
- [x] **新增功能测试**：send-async 投递、请求-响应成功（含 ack-topic 规避验证）、请求-响应超时、共享 reply topic correlation 匹配/不匹配、NoOp fail-fast —— 各有对应通过测试
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` messenger 测试通过
- [x] No owner-doc update required（Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 引擎接线 + 端到端 + 设计文档更新

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`, `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/`（新接线测试），`ai-dev/design/nop-ai-agent/01-architecture-baseline.md`, `ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md`, `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Fix | Follow-up`

- [x] 为 `DefaultAgentEngine` 增加 messenger 字段（默认 NoOp）+ setter（遵循 `setTalents`/`setSkillProvider` 模式）；无构造器链变更
- [x] 暴露 messenger 访问器，使未来工具 / actor runtime 可从引擎获取
- [x] 端到端集成测试：两个端点（在两个不同 inbox topic 上的两个 handler 注册）共享一个内存 messenger —— 端点 A 向端点 B 的 inbox 发请求 → B 的 handler 回复 → A 的 `CompletableFuture` 以 B 的响应完成；经由引擎配置的 messenger 验证（非仅 standalone 测试）
- [x] 端到端测试：构造引擎时**不**设 messenger → 默认 NoOp → 其上请求-响应 fail-fast（向后兼容：既有引擎构造路径不变）
- [x] 更新 `01-architecture-baseline.md` §六：标记 Agent 域 messenger 层已接线（平台 IMessageService 集成已落地），保持既有设计契约文本不变
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` §4.2（MessageRouter 行）：注明以 LocalMessageService 为底的 messenger 已可作为路由基底
- [x] 更新 `nop-ai-agent-roadmap.md` §4 L4-1 行状态 ❌ → ✅

Exit Criteria:

- [x] `DefaultAgentEngine` 持有 messenger 引用，可经访问器获取；默认为 NoOp（既有构造路径不变）
- [x] **接线验证**：设置到引擎上的 messenger 与经访问器取回的是同一实例（接线连通，非仅字段存在）
- [x] **端到端验证**：完整 A→B 请求-响应往返经引擎的 messenger 完成（两个端点，收到回复）
- [x] **端到端验证**：未显式设 messenger 的引擎默认 NoOp，既有测试仍通过（向后兼容）
- [x] **无静默跳过**：NoOp 默认的请求-响应在引擎上下文中抛异常（已验证）
- [x] **新增功能测试**：引擎接线 + 双端点往返 + NoOp 默认向后兼容测试通过
- [x] `01-architecture-baseline.md` §六 已更新；`nop-ai-agent-actor-runtime-vision.md` §4.2 已更新；roadmap L4-1 → ✅
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全部通过（含新增测试 + 现有测试不受影响）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] Agent 域 messenger 抽象 + 内存实现 + NoOp 默认均已落地
- [x] Messenger 经平台 `IMessageService`/`LocalMessageService` 路由（无重复队列）
- [x] 请求-响应语义经 `CompletableFuture`（含超时）端到端可用
- [x] NoOp 默认 fail-fast（无静默跳过）—— 向后兼容
- [x] Messenger 已接入 `DefaultAgentEngine`（可经访问器取回）
- [x] 必要 focused verification（单元 + 集成 + 端到端）已完成
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs 已同步（architecture-baseline §六、actor-runtime-vision §4.2、roadmap L4-1）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 (a) send → consumer.onMessage 调用链在运行时连通（经平台 IMessageService，非自建队列），(b) request→reply→CompletableFuture 端到端连通，(c) NoOp 无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/166-nop-ai-agent-inter-agent-messenger.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### DB-backed Messenger (DBMessageService)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: L4-1 以内存实现建立 Agent 域 messenger 抽象。DB-backed 跨进程实现（L4-2）是满足同一接口的独立传输；消费 messenger 的 nop-ai-agent 代码与传输无关。单进程内存消息完全满足当前单 JVM 内所有消费者（进程内 call-agent、事件路由）。
- Successor Required: yes
- Successor Path: 未来 L4-2 plan（DBMessageService，deps L4-1 + nop-dao）

### call-agent / send-message 工具

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 这些工具是 messenger 的 Layer 4 消费者，不属于消息基础设施。本计划交付的 messenger 抽象 + 请求-响应语义正是它们所需的基底。实现它们是独立的、单独有价值的工作项。
- Successor Required: yes
- Successor Path: 未来 call-agent 工具 plan（plan 161 deferred）

### Actor Runtime / Mailbox 生命周期

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Actor Runtime（L4-8）管理 AgentActor 生命周期、Virtual Thread 调度、TeamManager、RecoveryManager —— 全部建立在 messenger 之上，但不是 messenger 本身。L4-1 提供消息信道；L4-8 提供使用它的 actor。
- Successor Required: yes
- Successor Path: 未来 L4-8 plan（deps L4-1~L4-6）

## Non-Blocking Follow-ups

- Deferred-ack mailbox（L4-5）—— 在 messenger 之上的 3-phase reservation 语义
- 多租户消息隔离 —— tenantId 作用域的 topic 命名空间（actor runtime Phase 2+）
- 在途消息持久化 / 崩溃恢复 —— 内存实现按设计崩溃即丢消息（architecture-baseline §六 注记）

## Closure

Status Note: Plan 166 delivered the Agent-domain inter-agent messenger infrastructure for nop-ai-agent, layered on the platform `IMessageService`/`LocalMessageService` (no duplicate queue). All three Phases completed: (1) `nop-message-core` dependency + `AgentMessageEnvelope` + `AgentMessageTopics` helper with independent `agent.*` namespace; (2) `IAgentMessenger` interface + `LocalAgentMessenger` (LocalMessageService-backed, ack-topic suppression, shared reply topic demultiplexing) + `NoOpAgentMessenger` (fail-fast on request); (3) `DefaultAgentEngine` setter/ accessor wiring with NoOp default (backward-compatible) + design doc updates. 43 new tests (15 + 20 + 8), 721 total module tests, 0 failures. Independent closure audit PASSED.
Completed: 2026-06-14

Closure Audit Evidence:

- Reviewer / Agent: Independent closure-audit subagent (explore, task_id: ses_13d230bfcffe4o5mt9XqWQ5Av5)
- Evidence:
  - **Phase 1 Exit Criteria** — ALL PASS:
    - `nop-message-core` in `nop-ai/nop-ai-agent/pom.xml` (lines 27-30, version inherited from nop-bom)
    - `AgentMessageEnvelope.java` / `AgentMessageKind.java` / `AgentMessageTopics.java` exist in `io.nop.ai.agent.message`
    - `inboxTopic()` → `agent.{id}.inbox`; `replyTopic()` → `agent.{id}.reply` (shared, no correlationId embedded); verified by `TestAgentMessageTopics` (9 tests)
    - Topic namespace uses `agent.` prefix, NOT platform `bro-`/`reply-`/`bat-`; asserted by `topicDoesNotUsePlatformPrefixes`
  - **Phase 2 Exit Criteria** — ALL PASS:
    - `IAgentMessenger` interface (send/request/registerHandler); handler contract is Agent-domain `IAgentMessageHandler` (NOT platform `IMessageConsumer`)
    - `LocalAgentMessenger` routes all messages through platform `IMessageService` (holds `IMessageService` field, delegates send/subscribe); `HandlerAdapter` explicitly suppresses ack-topic by returning `null` to platform after sending RESPONSE to reply topic
    - **ack-topic suppression verified**: `responseGoesToReplyTopicNotResponderAckTopic` spies on `ack-agent.B.inbox` and asserts it receives NOTHING; response arrives only at requester's future via reply topic
    - NoOp `request()` throws `UnsupportedOperationException("not yet implemented: no message service configured")`; NoOp `send()` is safe no-op (debug log); verified by `TestNoOpAgentMessenger` (6 tests)
    - `TestLocalAgentMessenger` (14 tests): send delivery, request-response success + ack-topic suppression, timeout, correlation match/mismatch, handler exception → timeout, validation, cancellable subscription
  - **Phase 3 Exit Criteria** — ALL PASS:
    - `DefaultAgentEngine` line 66: `private IAgentMessenger messenger = NoOpAgentMessenger.noOp()`; `setMessenger()` (null→NoOp fallback); `getMessenger()`; no constructor chain change (8 constructors unchanged)
    - Wiring verified: `setMessengerAndGetMessengerReturnSameInstance` uses `assertSame`; `TestDefaultAgentEngineMessengerWiring` (8 tests)
    - End-to-end A→B round trip via engine-configured messenger: `twoEndpointsRoundTripViaEngineMessenger`
    - Backward-compat: default NoOp → request fail-fast in engine context; `existingEngineConstructionPathsAreUnchanged`
  - **Anti-Hollow Check** — ALL VERIFIED:
    - (a) `send()` → `messageService.send()` — CONNECTED (no guard, no early return)
    - (b) `request()` → reply subscription + correlationId→future map + `messageService.send()` + `future.orTimeout()` — CONNECTED (real implementation, no stub)
    - (c) `registerHandler()` → `messageService.subscribe(topic, adapter)` — CONNECTED (direct delegation)
    - (d) ack-topic suppression: adapter sends RESPONSE to reply topic, returns `null` to platform — VERIFIED (test spies on ack-topic, source confirms platform only routes on non-null)
    - (e) NoOp `request()` fail-fast — VERIFIED (`requestNeverReturnsSilentlyCompletingFuture` uses try/catch to assert no silent future)
  - **Automated checks**:
    - `./mvnw compile -pl nop-ai/nop-ai-agent -am` → BUILD SUCCESS
    - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 721 tests, 0 failures, 0 errors
    - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/166-nop-ai-agent-inter-agent-messenger.md --strict` → exit code 0
    - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` → exit code 0 (0 findings)
  - **Silent no-op scan**: CLEAN — no empty catch blocks, no TODO/FIXME in new code, all `return null` occurrences verified intentional (non-envelope ignore, handler exception MVP, ack-topic suppression, type guards)
  - **Design docs**: ALL UPDATED — `01-architecture-baseline.md` §六 (messenger wiring paragraph); `nop-ai-agent-actor-runtime-vision.md` §4.2 MessageRouter row (L4-1 已落地); `nop-ai-agent-roadmap.md` L4-1 ❌→✅
  - **Deferred 项分类检查**: 3 deferred items (DB-backed L4-2, call-agent/send-message tools, Actor Runtime L4-8) all correctly classified as `out-of-scope improvement` with explicit non-blocking rationale — no in-scope live defect downgraded

Follow-up:

- no remaining plan-owned work
- Deferred (already adjudicated in plan): DB-backed Messenger (L4-2), call-agent/send-message tools (plan 161 successor), Actor Runtime (L4-8), Deferred-ack mailbox (L4-5), multi-tenant isolation, in-flight message persistence

## Follow-up handled by 168-nop-ai-agent-call-agent-tool.md

Plan 168 picks up the "call-agent / send-message 工具" deferred item — the Layer 4 consumers of the messenger delivered in this plan. It replaces the hollow `CallAgentExecutor` mock in `nop-ai-toolkit` (which never invokes any agent engine) with a functional call-agent tool using the fork+exec model (`IAgentEngine.execute()` on a sub-session, design-aligned per `nop-ai-agent-multi-agent.md` §Phase 1), and adds a send-message tool that consumes this plan's `IAgentMessenger.send()` for fire-and-forget delivery. The call-agent tool is the synchronous subagent-invocation capability; send-message is the async inter-agent messaging capability — both defined by `nop-ai-agent-actor-runtime-vision.md` §3.3. The messenger-based call-agent model (send to inbox, actor processes) remains the L4-8 actor-runtime successor.
