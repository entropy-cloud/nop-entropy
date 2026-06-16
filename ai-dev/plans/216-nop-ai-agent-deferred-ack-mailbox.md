# 216 nop-ai-agent Deferred-Ack 邮箱契约（IMailbox: DeferredAckMailbox 3-phase reservation）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-5
> Last Reviewed: 2026-06-16
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4（L4-5 ❌ 未实现，line 244）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` §3.1（AgentActor Mailbox 概念）+ §2.1（Deferred-ack 异步通信洞察，来自 oh-my-opencode Team Mode）+ §11 Open Question（有界/无界邮箱）；`ai-dev/design/nop-ai-agent/01-architecture-baseline.md` §通信模型（IMessageService Mailbox 抽象层，line 142-159、line 242）
> Related: `168`（交付 `IAgentMessenger` + `LocalAgentMessenger` + `NoOpAgentMessenger`，L4-1 ✅）、`185`（交付 `DBMessageService` 跨进程路由，L4-2 ✅）

## Purpose

把 nop-ai-agent 的消息消费从"开箱即同步 handler 调用（fire-and-forget，无显式确认）"扩展为"可通过 deferred-ack 邮箱缓冲消息、消费者显式确认（ack）/拒绝（nack）后消息才从队列移除"。本计划交付**邮箱契约表面 + NoOp 默认 + 功能性 DeferredAckMailbox 实现（3-phase reservation）+ IAgentMessenger 集成适配器 + 引擎接线 + 端到端验证**，闭合 L4-5 roadmap gap。Actor Runtime 执行循环（L4-8）消费邮箱、DB-backed 持久化邮箱、跨进程邮箱复制均为显式 Non-Goals（独立 successor），本计划只交付让它们可以被消费的契约和接线。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **`IAgentMessenger`（3 方法）✅**（plan 168 / L4-1）：`send`（fire-and-forget ASYNC）、`request`（REQUEST → `CompletableFuture` 含超时）、`registerHandler`（topic → `IAgentMessageHandler` 订阅，返回 `IMessageSubscription`）。接口不含邮箱/缓冲/deferred-ack 概念。
- **`LocalAgentMessenger` ✅**（plan 168）：基于平台 `IMessageService`/`LocalMessageService` 的内存实现。`send` 经 `messageService.send(topic, message)` 投递；`registerHandler` 经 `messageService.registerConsumer` 订阅，handler 在消息到达时被**同步调用**——无缓冲、无 deferred-ack、无重投递。
- **`NoOpAgentMessenger` ✅**（plan 168）：shipped 默认。`send` = debug-log no-op（fire-and-forget 无可观测返回）；`request` = fail-fast `UnsupportedOperationException`；`registerHandler` = no-op subscription。向后兼容零行为回归。
- **`AgentMessageEnvelope`** ✅：不可变信封（senderId / targetTopic / correlationId / kind / payload / timestamp），位于 `io.nop.ai.agent.message` 包。
- **`AgentMessageKind`** ✅：`REQUEST` / `RESPONSE` / `ASYNC` 三值枚举。
- **`IAgentMessageHandler`** ✅：`@FunctionalInterface`，`onMessage(envelope) → Object`（非 null = REQUEST 的响应 payload，null = 无响应）。
- **`AgentMessageTopics`** ✅：定义 topic 命名约定（inbox topic = `agent.{sessionId}.inbox`，reply topic = `agent.{sessionId}.reply`）。
- **`DBMessageService` ✅**（plan 185 / L4-2）：跨进程 DB-backed 实现，消息持久化到 `ai_agent_message` 表，至少一次语义（JVM 崩溃后 pending 消息重新投递）。
- **`SendMessageExecutor` ✅**（plan 168）：`send-message` 工具，经 `IAgentMessenger.send()` 投递 `AgentMessageKind.ASYNC` 信封到目标 inbox topic。
- **`DefaultAgentEngine` 接线 ✅**：`messenger` 字段（默认 `NoOpAgentMessenger`）+ `setMessenger` setter + `resolveExecutor` Builder 链透传 `.messenger(this.messenger)`。
- **`IMailbox` / `DeferredAckMailbox` 零实现**：grep `IMailbox|DeferredAckMailbox|MailboxEntry` 在 `nop-ai-agent/src/main` 返回 0 命中（仅 design doc + javadoc 注释引用 Actor mailbox 概念）。
- **Actor Runtime（L4-8）未实现**：邮箱的最终消费者（Actor 执行循环 poll → process → ack）尚未存在。本计划交付的邮箱是 L4-8 可消费的独立原语。
- **actor-runtime-vision.md §11 Open Question**："[ ] Actor 邮箱是无界队列还是有界队列？有界时背压策略如何？（倾向：有界 + 背压拒绝，默认 1000 条）"——本计划 Phase 1 裁定此项。
- **roadmap §4**：`L4-5 | IMailbox DeferredAckMailbox (3-phase reservation) | L1-1 | ❌`（line 244）。本计划关闭这一行。

## Goals

- **`IMailbox` 接口定义**：3-phase reservation 邮箱契约——`offer`（投递消息到邮箱，预占容量）→ `poll`（消费者取出消息并标记 in-flight）→ `ack`/`nack`（确认处理完成移除 / 拒绝并可选重投递）。
- **`MailboxEntry` 数据对象**：包装 `AgentMessageEnvelope`，附带 deliveryId（唯一投递标识）、deliveryCount（投递次数）、state（PENDING/IN_FLIGHT/ACKED/NACKED/DEAD_LETTERED）、入队/出队时间戳。
- **`DeferredAckMailbox` 功能实现**：线程安全（offer 线程与 poll/ack/nack 线程可不同）、可配置容量（默认无界，有界时 offer 满返回 false 作背压信号）、in-flight 跟踪、nack 重投递、超过 maxDeliveryAttempts 后 dead-letter。
- **`NoOpMailbox` 默认**：offer 返回 false（拒绝投递）、poll 返回 null（空）、ack/nack 返回 false——显式 no-op，非静默成功。
- **`MailboxMessageHandler` 集成适配器**：实现 `IAgentMessageHandler`，消息到达时 offer 到关联 `IMailbox`（使 deferred-ack 邮箱可经现有 `IAgentMessenger.registerHandler` 接入消息流，无需改 messenger 接口）。
- **引擎接线**：`DefaultAgentEngine` 可选邮箱工厂（null = 不使用邮箱，零行为回归；非 null = 为 session 创建邮箱并注册 `MailboxMessageHandler` 到 inbox topic）。
- **focused 测试**：邮箱契约（offer/poll/ack/nack/redelivery/dead-letter/capacity）、适配器、NoOp 默认各有测试覆盖。
- **端到端验证**：从 `send-message` 工具 → `IAgentMessenger.send(ASYNC)` → `MailboxMessageHandler` → `mailbox.offer()` → 消费者 `poll()` → `ack()` 的完整 deferred-ack 路径跑通；另验证 `nack(requeue=true)` → 重投递路径。
- **roadmap §4**：`L4-5` 行从 ❌ → ✅ 并标注本 plan。

## Non-Goals

- **Actor Runtime 执行循环（L4-8）**：邮箱的最终消费者——Actor 在单个 virtual thread 上 poll → process → ack 的执行循环。本计划交付邮箱原语，不交付 Actor 执行循环。Classification: successor plan required。
- **DB-backed 持久化邮箱**：raw JDBC 写邮箱消息到 DB 表使进程崩溃后不丢失。本计划只交付 in-memory 实现（参照 L4-1 `LocalMessageService` → L4-2 `DBMessageService` 的接口→DB 拆分模式）。Classification: successor plan required。
- **跨进程邮箱复制**：多实例部署下邮箱状态跨进程同步。依赖 L4-8 Actor Runtime。Classification: successor plan required。
- **`take()` 阻塞接收**：阻塞式 `take()` / `take(timeout)` 方法（Actor 在 virtual thread 上阻塞等待消息）。本计划只交付非阻塞 `poll()`；阻塞接收是 Actor Runtime 的执行语义裁定。Classification: successor plan required。
- **Dead-letter queue 存储/查询 API**：dead-lettered 消息的持久化存储与查询接口。本计划只标记 dead-letter 状态（超过 maxDeliveryAttempts 后不再重投递），不提供 dead-letter queue 管理。Classification: optimization candidate。
- **背压通知机制**：有界邮箱满时主动通知生产者（如回调、事件），而非仅 offer 返回 false。Classification: optimization candidate。
- **XDSL 配置化**：`agent.xdef` 增加 `<mailbox>` 元素绑定邮箱配置（容量/maxDeliveryAttempts）。当前通过构造器参数编程配置。
- **邮箱消息 TTL / 过期清理**：消息在邮箱中停留超过 TTL 后自动过期。Classification: optimization candidate。
- **优先级队列**：消息按优先级排序而非 FIFO。Classification: optimization candidate。

## Scope

### In Scope

- `io.nop.ai.agent.message` 包下新增 `IMailbox` 接口 + `MailboxEntry` 数据对象 + `MailboxDeliveryState` 枚举 + `NoOpMailbox` 默认 + `DeferredAckMailbox` 功能实现 + `MailboxMessageHandler` 适配器
- `DefaultAgentEngine` + `ReActAgentExecutor.Builder` 接线（可选邮箱工厂/setter）
- `nop-ai-agent-actor-runtime-vision.md`（§3.1 Mailbox + §11 Open Question 裁定）+ `01-architecture-baseline.md` §通信模型 设计文档更新
- roadmap §4 L4-5 ❌→✅
- focused 测试 + 端到端测试

### Out Of Scope

- 见 Non-Goals（Actor Runtime / DB-backed / 跨进程 / take() 阻塞 / dead-letter queue 存储 / 背压通知 / XDSL / TTL / 优先级队列 均为显式 successor）

### 设计裁定

**裁定 1：3-phase reservation 语义定义**
- Phase 1 (Reserve/Offer)：生产者调用 `offer(envelope)`，邮箱接受消息并分配 deliveryId，消息进入 PENDING 状态。有界邮箱满时返回 false（背压信号，非异常）。
- Phase 2 (Poll/In-flight)：消费者调用 `poll()` 取出最早 PENDING 消息，标记为 IN_FLIGHT。`poll()` 非阻塞，无消息时返回 null。
- Phase 3 (Ack/Nack)：消费者调用 `ack(deliveryId)` → 消息标记 ACKED 并永久移除；或 `nack(deliveryId, requeue)` → requeue=true 时消息回到 PENDING（deliveryCount +1）等待重投递，requeue=false 时标记 NACKED 移除。
- 超过 maxDeliveryAttempts 的消息在 nack(requeue=true) 时标记 DEAD_LETTERED 而非重投递（防止无限重试）。

**裁定 2：有界 vs 无界容量（关闭 §11 Open Question）**
- `DeferredAckMailbox` 构造器接受 `capacity` 参数（`<= 0` 表示无界，默认无界）。有界邮箱满时 `offer()` 返回 false（生产者据此决策重试/降级/丢弃）。
- Actor Runtime（L4-8）将按 actor-runtime-vision.md §11 的倾向配置有界容量（如默认 1000）。本计划不强制默认值。

**裁定 3：线程安全模型**
- Actor 模型中 offer（messenger 投递线程）与 poll/ack/nack（Actor 执行线程）并发。`DeferredAckMailbox` 必须线程安全（内部 `ReentrantLock` 或等价同步原语保护 pending/in-flight/dead-letter 三个集合的复合操作）。
- 同一 deliveryId 的 ack/nack 不会并发（Actor 串行处理），但 poll 可能与 offer 并发。

**裁定 4：deliveryId 生成策略**
- deliveryId 为 per-mailbox 单调递增 `long`（`AtomicLong`），每次 offer 分配。同一消息重投递时分配**新** deliveryId（deliveryCount 递增但 deliveryId 不同），使 ack/nack 精确匹配当前投递实例。

**裁定 5：MailboxMessageHandler 适配器——不改 IAgentMessenger 接口**
- `MailboxMessageHandler implements IAgentMessageHandler`：`onMessage(envelope)` 调用 `mailbox.offer(envelope)` 并返回 null（ASYNC 消息无需响应）。
- 经现有 `IAgentMessenger.registerHandler(topic, new MailboxMessageHandler(mailbox))` 接入消息流——无需改 `IAgentMessenger` 接口，零接口侵入。
- 消费者（Actor 循环或测试消费者）经 `mailbox.poll()` 取出消息，处理完成后 `ack()`/`nack()`。
- offer 失败（邮箱满/NoOp）时：log WARN 并返回 null（fire-and-forget ASYNC 语义下消息丢弃是可接受的降级；WARN 使降级可见，非静默吞没）。

**裁定 6：shipped 默认不变**
- `DefaultAgentEngine` 邮箱工厂字段默认 null（不使用邮箱，消息走现有 handler 同步调用路径，零行为回归）。
- 集成商显式 `engine.setMailboxFactory(sessionId -> new DeferredAckMailbox(...))` 启用邮箱缓冲。
- 不引入 `warnIfInsecureDefaults` WARN——邮箱缺失不构成安全风险（与 IUsageRecorder / IMemoryAdapter 裁定一致：NoOp 下系统正常运行，只是不缓冲/不 deferred-ack）。

## Execution Plan

### Phase 1 - 邮箱契约表面 + 数据对象 + NoOp 默认 + 设计裁定落档

Status: completed
Targets: `io.nop.ai.agent.message` 包（新增接口 + 数据对象 + 枚举 + NoOp 默认）、`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md`（§3.1 + §11）、`ai-dev/design/nop-ai-agent/01-architecture-baseline.md`（§通信模型）

- Item Types: `Decision`、`Proof`

- [x] **裁定并落档** 3-phase reservation 语义 + 容量策略 + 线程安全模型 + deliveryId 策略 + 适配器集成方案 + shipped 默认策略（设计裁定 1-6 写入设计文档）
- [x] 定义 `IMailbox` 接口行为契约：`offer(AgentMessageEnvelope) → boolean`（投递消息，满返回 false）、`poll() → MailboxEntry`（取出最早 PENDING 消息标记 IN_FLIGHT，空返回 null）、`ack(long deliveryId) → boolean`（确认完成移除，未知 id 返回 false）、`nack(long deliveryId, boolean requeue) → boolean`（拒绝；requeue=true 回 PENDING 或达 maxDeliveryAttempts 转 DEAD_LETTERED，requeue=false 标记 NACKED 移除）、`pendingCount() → int`、`inFlightCount() → int`。Javadoc 须明确 3-phase 状态转换与线程安全契约
- [x] 定义 `MailboxEntry` 不可变数据对象：包装 `AgentMessageEnvelope` + `deliveryId`（long）+ `deliveryCount`（int，首次投递=1）+ `state`（MailboxDeliveryState）+ `offeredAt` / `polledAt`（long timestamp，0=未发生）
- [x] 定义 `MailboxDeliveryState` 枚举：`PENDING`（已入队待消费）、`IN_FLIGHT`（已 poll 待 ack/nack）、`ACKED`（已确认，将移除）、`NACKED`（已拒绝不重投，将移除）、`DEAD_LETTERED`（达 maxDeliveryAttempts，不再重投）
- [x] 实现 `NoOpMailbox`（singleton，`offer` 返回 false、`poll` 返回 null、`ack`/`nack` 返回 false、count 查询返回 0）——显式 no-op，非静默成功（Minimum Rules #24：返回值明确表达"无操作发生"）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `IMailbox` 接口文件存在于 `io.nop.ai.agent.message` 包，有清晰的 3-phase reservation 行为契约 Javadoc
- [x] `MailboxEntry` + `MailboxDeliveryState` 数据对象/枚举文件存在，字段完整
- [x] `NoOpMailbox` 默认文件存在，所有方法返回明确的 no-op 结果（offer=false / poll=null / ack,nack=false / count=0），无静默成功伪装
- [x] `nop-ai-agent-actor-runtime-vision.md` §3.1 + §11 已更新：3-phase reservation 语义落档、有界/无界 Open Question 裁定关闭
- [x] `01-architecture-baseline.md` §通信模型 已更新：IMailbox 契约 + MailboxMessageHandler 适配器描述
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DeferredAckMailbox 功能实现 + 适配器 + 引擎接线 + 测试

Status: completed
Targets: `io.nop.ai.agent.message` 包（DeferredAckMailbox + MailboxMessageHandler）、`io.nop.ai.agent.engine.DefaultAgentEngine`（接线）、`io.nop.ai.agent.engine.ReActAgentExecutor.Builder`（接线）、`src/test/`（测试）

- Item Types: `Proof`、`Follow-up`

- [x] 实现 `DeferredAckMailbox`（线程安全 3-phase reservation 邮箱）：
  - 内部维护 PENDING 队列（FIFO）、IN_FLIGHT map（deliveryId → MailboxEntry）、DEAD_LETTER 集合
  - 构造器接受 `capacity`（`<= 0` 无界）+ `maxDeliveryAttempts`（默认 5）
  - `offer`：容量未满时分配新 deliveryId（`AtomicLong`）、创建 `MailboxEntry`（state=PENDING, deliveryCount=1）、入 PENDING 队列、返回 true；满时返回 false
  - `poll`：从 PENDING 队列取出最早消息、标记 IN_FLIGHT、移入 IN_FLIGHT map、记录 polledAt、返回 entry；空时返回 null
  - `ack`：从 IN_FLIGHT map 移除指定 deliveryId、标记 ACKED、返回 true；未知 id 返回 false
  - `nack`：从 IN_FLIGHT map 取出 entry；requeue=true 且 deliveryCount < maxDeliveryAttempts → 分配新 deliveryId、deliveryCount+1、state=PENDING、回 PENDING 队列；requeue=true 且 deliveryCount >= maxDeliveryAttempts → state=DEAD_LETTERED、移入 DEAD_LETTER；requeue=false → state=NACKED、移除。返回 true；未知 id 返回 false
  - 全程 `ReentrantLock` 保护三个集合的复合操作（offer/poll/ack/nack 互斥）（实现注：用单一 `synchronized(lock)` 对象提供等价的复合操作互斥）
- [x] 实现 `MailboxMessageHandler`（`IAgentMessageHandler` 适配器）：构造器接受 `IMailbox`；`onMessage(envelope)` 调用 `mailbox.offer(envelope)`，offer 成功返回 null（ASYNC 无需响应）；offer 失败时 log WARN 并返回 null（fire-and-forget 降级可见，非静默吞没）
- [x] 引擎接线：`DefaultAgentEngine` 新增可选 `mailboxFactory` 字段（`Function<String, IMailbox>`，null = 不使用邮箱）+ `setMailboxFactory` setter。**邮箱创建 + handler 注册发生在 session 执行启动时**（`sessionId` 可用的位置，如 `doExecute` / `resumeSession` / `restoreSession` 入口点，**非** `resolveExecutor`——后者签名不含 sessionId）。per-session 去重：engine 内部维护 `ConcurrentHashMap<String, IMailbox>` 缓存已创建邮箱 + 对应 `IMessageSubscription`，同一 sessionId 仅首次执行时创建邮箱并经 `IAgentMessenger.registerHandler` 注册 `MailboxMessageHandler` 到 inbox topic `agent.{sessionId}.inbox`（参照 `AgentMessageTopics` 命名约定），后续执行复用已有邮箱（`computeIfAbsent` 原子操作，不重复注册 handler）。shipped 默认 mailboxFactory=null（零行为回归）
- [x] **接线验证**（Minimum Rules #23）：测试通过注入非 null mailboxFactory 到 `DefaultAgentEngine`，运行 ReAct 循环发送 ASYNC 消息，断言消息被 offer 到邮箱（`mailbox.pendingCount() > 0` 或 `inFlightCount() > 0`），证明 messenger → handler → mailbox 运行时调用连通
- [x] 编写 focused 测试：
  - `DeferredAckMailbox` 契约测试：offer→poll→ack 正常路径、offer 满容量返回 false、poll 空返回 null、ack 未知 id 返回 false、nack(requeue=true) 重投递（deliveryCount 递增 + 新 deliveryId）、nack(requeue=true) 达 maxDeliveryAttempts 转 DEAD_LETTERED、nack(requeue=false) 标记 NACKED 移除、FIFO 顺序保证、pendingCount/inFlightCount 准确
  - `NoOpMailbox` 默认测试：offer 返回 false、poll 返回 null、ack/nack 返回 false、count=0（显式 no-op，非静默成功）
  - `MailboxMessageHandler` 适配器测试：onMessage 调用 mailbox.offer、返回 null、offer 失败时 WARN-log 不抛异常
- [x] **端到端验证**（Minimum Rules #22）：一个测试从 `DefaultAgentEngine`（注入非 null mailboxFactory + `LocalAgentMessenger`）出发，经 `send-message` 工具发送 ASYNC 消息 → messenger 投递到 inbox topic → `MailboxMessageHandler.onMessage` → `mailbox.offer()` → 测试消费者 `poll()` 取出消息 → `ack()` 确认，完整 deferred-ack 路径跑通；另验证 `nack(requeue=true)` → `poll()` 重投递路径

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DeferredAckMailbox` 实现存在，覆盖全部 6 接口方法，无空方法体或静默跳过（Minimum Rules #24）
- [x] 3-phase reservation 语义正确：offer→PENDING、poll→IN_FLIGHT、ack→ACKED 移除、nack(requeue=true)→PENDING 重投递（deliveryCount 递增）、nack(requeue=false)→NACKED 移除、达 maxDeliveryAttempts→DEAD_LETTERED
- [x] 有界容量 offer 满返回 false（背压信号）
- [x] `NoOpMailbox` 全方法返回明确 no-op 结果，有 focused 测试覆盖
- [x] `MailboxMessageHandler` 适配器正确 offer 到邮箱，有 focused 测试覆盖
- [x] **接线验证**：测试断言 mailboxFactory 注入 engine 后，ASYNC 消息经 messenger → handler → mailbox offer 路径连通（mailbox count > 0）
- [x] **端到端验证**：从 send-message 工具 → messenger → mailbox offer → poll → ack 完整路径测试通过；nack(requeue=true) → 重投递路径测试通过
- [x] 线程安全：focused 测试验证 offer 与 poll 并发不破坏不变量（如使用多线程 offer + 单线程 poll/ack 验证消息不丢失/不重复）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全量通过（含新增测试 + 既有测试零回归）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IMailbox` 接口 + `MailboxEntry` + `MailboxDeliveryState` + `NoOpMailbox` + `DeferredAckMailbox` + `MailboxMessageHandler` 全部落地，无空壳
- [x] DeferredAckMailbox 覆盖全 6 接口方法，3-phase reservation 行为正确
- [x] 引擎接线完成（`DefaultAgentEngine` 可注入 mailboxFactory，shipped 默认 null 不变）
- [x] focused 测试覆盖邮箱契约（offer/poll/ack/nack/redelivery/dead-letter/capacity）+ 适配器 + NoOp
- [x] 端到端测试从 send-message → messenger → mailbox offer → poll → ack 完整跑通
- [x] shipped 默认行为零回归（mailboxFactory=null 默认不变，既有测试全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（Actor Runtime / DB-backed / 跨进程 / take() / dead-letter queue 存储 均显式在 Non-Goals 切出）
- [x] 受影响的 owner docs 已同步到 live baseline（actor-runtime-vision.md §3.1+§11 + 01-architecture-baseline.md §通信模型 + roadmap §4 L4-5 ❌→✅）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）MailboxMessageHandler 在运行时确实被 messenger 调用并 offer 到邮箱（不只是类型存在），（b）DeferredAckMailbox 的 offer→poll→ack/nack 链完整连通，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；Actor Runtime / DB-backed / 跨进程 / take() 阻塞 / dead-letter queue 存储 / 背压通知 / XDSL / TTL / 优先级队列 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **DB-backed 持久化邮箱**：raw JDBC 写邮箱消息到 DB 表（参照 `DBMessageService` / `DbUsageRecorder` 模式），使进程崩溃后 in-flight 消息可恢复重投递。Classification: successor plan required。
- **Actor Runtime 执行循环（L4-8）**：Actor 在单个 virtual thread 上 poll → process → ack 的执行循环，消费 `IMailbox`。Classification: successor plan required。
- **`take()` 阻塞接收**：阻塞式 `take()` / `take(timeout)` 方法。Classification: successor plan required。
- **Dead-letter queue 存储/查询 API**：dead-lettered 消息的持久化存储与查询。Classification: optimization candidate。
- **背压通知机制**：有界邮箱满时主动通知生产者。Classification: optimization candidate。
- **XDSL 配置化**：`agent.xdef` 增加 `<mailbox>` 元素。Classification: optimization candidate。
- **邮箱消息 TTL / 过期清理**。Classification: optimization candidate。
- **优先级队列**。Classification: optimization candidate。

## Closure

Status Note: 本计划交付了 nop-ai-agent 的 deferred-ack 邮箱契约（IMailbox 3-phase reservation）+ 功能性 DeferredAckMailbox 实现 + NoOp 显式默认 + MailboxMessageHandler 适配器 + DefaultAgentEngine 接线（可选 mailboxFactory，shipped 默认 null 零回归）+ 35 个 focused/端到端测试。所有 in-scope 项已落地，Non-Goals（Actor Runtime L4-8 / DB-backed / 跨进程 / take() / dead-letter queue 存储）均为显式 successor。mailbox 的最终消费者（Actor 执行循环）是 L4-8 的独立 successor——本计划只交付让它们可被消费的契约和接线。
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（session `ses_130a91049ffelUoid30RYogDEd`，fresh session，非执行阶段复用）
- Audit Session: ses_130a91049ffelUoid30RYogDEd
- Evidence:
  - **Phase 1 Exit Criteria**: 全部 PASS — IMailbox.java:52-121（6 方法 + 3-phase Javadoc）/ MailboxEntry.java / MailboxDeliveryState.java:33-39（5 状态）/ NoOpMailbox.java:29-58（显式 no-op offer=false refusal）/ actor-runtime-vision.md §3.1+§11（§11 open question 标 [x] resolved）/ 01-architecture-baseline.md §通信模型（Deferred-ack mailbox 段 + Mailbox 行）
  - **Phase 2 Exit Criteria**: 全部 PASS — DeferredAckMailbox.java:84-178（6 方法均有真实逻辑，synchronized(lock) 互斥；3-phase 语义：offer→PENDING / poll→IN_FLIGHT / ack→移除 / nack requeue→新 deliveryId+count+1 / nack no-requeue→移除 / dead-letter 达 max）；bounded capacity offer 满返回 false；TestDeferredAckMailbox（20 tests）+ TestNoOpMailbox（7）+ TestMailboxMessageHandler（5）+ TestMailboxEndToEnd（3）
  - **接线验证（#23）**: PASS — DefaultAgentEngine mailboxFactory 字段（默认 null）+ setMailboxFactory + ensureSessionMailbox（computeIfAbsent 原子去重 + registerHandler 到 inbox topic），在 doExecute:1170 / resumeSession:1398 / restoreSession:1528 三入口调用；TestMailboxEndToEnd.sendMessageToolDeliversToMailboxThenPollAck 断言 pendingCount+inFlightCount>0 证明 messenger→handler→mailbox 运行时调用连通
  - **端到端验证（#22）**: PASS — TestMailboxEndToEnd 真实 DefaultAgentEngine.execute() + LLM emit send-message 工具 → messenger → inbox topic → MailboxMessageHandler.onMessage → mailbox.offer → 测试 poll → ack 完整路径；另 nackRequeueRedeliversViaRegisteredHandler 验证 requeue 重投递路径
  - **Anti-Hollow Check**: PASS — (a) MailboxMessageHandler 在运行时被 messenger 调用并 offer（TestMailboxEndToEnd 断言 count>0，仅当 handler→offer 被调用才可能）；(b) offer→poll→ack/nack 链完整连通（同测试 poll 断言 IN_FLIGHT + payload + ack 成功 count 归 0；nack requeue 测试断言 count 1→2 + 新 deliveryId）；(c) 无空方法体/静默跳过——DeferredAckMailbox 6 方法均有真实逻辑，NoOpMailbox 是文档化的显式 no-op 默认（offer=false 拒绝，非静默成功）
  - **线程安全**: PASS — TestDeferredAckMailbox.concurrentOfferAndPollNoLossNoDuplication（4 producer + 1 consumer 500 条消息，distinct deliveryIds，无丢失无重复）
  - **shipped 默认零回归**: PASS — TestMailboxEndToEnd.defaultNullMailboxFactoryCreatesNoMailbox 断言 null factory 不创建邮箱；全量 1927 tests 0 failures（+35 新增，既有 1892 零回归）
  - **owner docs 同步**: PASS — actor-runtime-vision.md §3.1+§11 + 01-architecture-baseline.md §通信模型 + roadmap §4 L4-5 ❌→✅
  - **Deferred 项分类检查**: PASS — 全部 Non-Goals（Actor Runtime L4-8 / DB-backed / 跨进程 / take() / dead-letter queue 存储 / 背压通知 / XDSL / TTL / 优先级队列）为显式 successor，无 in-scope live defect 被降级
  - `scan-hollow-implementations.mjs --module nop-ai-agent --severity high` → 退出码 0（0 critical / 0 high）
  - `check-plan-checklist.mjs …216… --strict` → 退出码 0（全 checklist 已勾选 + Closure Evidence 已写入）

Follow-up:

- 无 plan-owned 剩余工作。显式 successor（Non-Blocking Follow-ups）：DB-backed 持久化邮箱、Actor Runtime 执行循环（L4-8）消费 IMailbox、take() 阻塞接收、dead-letter queue 存储/查询、背压通知、XDSL 配置化、TTL 过期清理、优先级队列——均为独立 successor plan，不影响本计划 closure。
