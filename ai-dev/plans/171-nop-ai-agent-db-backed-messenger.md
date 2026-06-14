# 171 nop-ai-agent DB-Backed Messenger (L4-2)

> **Plan Status**: planned
> **Module**: nop-ai-agent
> **Work Item**: L4-2

> Last Reviewed: 2026-06-14
> Source: Carry-over from plan 166 (`ai-dev/plans/166-nop-ai-agent-inter-agent-messenger.md`, Deferred "DB-backed Messenger (DBMessageService)", Successor Required: yes); roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4 L4-2; design `ai-dev/design/nop-ai-agent/01-architecture-baseline.md` §六 通信模型
> Related: Plan 166 (messenger infrastructure — `IAgentMessenger` + `LocalAgentMessenger` + `NoOpAgentMessenger` delivered), Plan 168 (call-agent / send-message tools — consumers of the messenger), Plan 134 (L1-1 `IAgentEngine`)

## Purpose

为 nop-ai-agent 提供基于数据库的 `IMessageService` 实现（`DBMessageService`），使 Agent 间通信从单进程内存（`LocalMessageService`）透明升级为跨进程数据库路由，满足设计文档 §六"多实例"部署模型。`DBMessageService` 是平台 `IMessageService` 接口的兄弟实现——与 `LocalMessageService`（同步内存分发）和 `PulsarMessageService`（Pulsar broker 跨进程）同级。`LocalAgentMessenger` 的所有 Agent 域逻辑（ack-topic 抑制、reply 解复用、correlation 匹配）保持不变，因为它是传输无关的（构造器接受任何 `IMessageService`）。

## Current Baseline

- **L4-1 ✅ delivered**: `IAgentMessenger` 接口 + `LocalAgentMessenger`（基于平台 `LocalMessageService`）+ `NoOpAgentMessenger`（默认 fail-fast）已交付（plan 166，43 个测试，721 总模块测试全通过）
- **Transport-agnostic messenger**: `LocalAgentMessenger` 构造器接受任何 `IMessageService`（`new LocalAgentMessenger(messageService)`）；切换传输不需要修改 Agent 域代码——`send`/`request`/`registerHandler` 全部委托底层 `IMessageService`
- **Platform `IMessageService` contract**: 继承 `IMessageSender` + `IMessageSubscriber`；必须实现的方法仅 `sendAsync(String topic, Object message, MessageSendOptions options)` 与 `subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options)`；其余方法（`send`、`sendAsync(topic,msg)`、`subscribe(topic,listener)`、`getAckTopic`）均有 default 实现
- **Platform `IMessageConsumer` contract**: `onMessage(topic, message, context)` 返回值语义——null = 已消费；`CompletionStage` = 异步处理中；`ConsumeLater` = 延迟重试；其他非 null = 响应消息
- **Platform precedents**: `LocalMessageService`（`nop-message-core`，同步内存 `ConcurrentHashMap<topic, CopyOnWriteArrayList<Subscription>>` 分发）和 `PulsarMessageService`（`nop-message-pulsar`，跨进程 Pulsar broker + `PulsarConsumeTask` 后台消费线程）是同一接口的兄弟实现——`DBMessageService` 是第三个
- **`LocalMessageService` ack-topic 行为**: `handleMessageResult` 将 consumer 非 null 返回值自动路由到 `ack-{topic}`；`LocalAgentMessenger.HandlerAdapter` 已显式抑制此行为（返回 null 给平台，另经 `messageService.send` 将 RESPONSE 发到 reply topic）；`DBMessageService` 不复制 ack-topic 路由（它是 `LocalMessageService` 特有行为）
- **AgentMessageEnvelope**: 不可变值对象，字段 `senderId`、`targetTopic`、`correlationId`、`kind`（REQUEST/RESPONSE/ASYNC）、`payload`（Object）、`timestamp`；内存传输时 payload 按引用传递（任意 Java 对象）
- **No DB-backed transport exists**: grep 确认 nop-ai-agent 中无 `DBMessageService` 或任何 DB-backed `IMessageService` 实现
- **nop-ai-agent 无 DB 依赖**: pom.xml 依赖 `nop-message-core`、`nop-ai-core`、`nop-ai-toolkit`（main）；`nop-autotest-junit`、`nop-record-mapping`（test）；不含 `nop-dao` / `nop-orm`；测试 resources 仅 `_vfs`
- **Platform DAO/ORM 位置**: `IOrmSession` 在 `nop-persistence/nop-orm`；DAO API 在 `nop-persistence/nop-dao`；roadmap 中的 "nop-dao" 指这些模块
- **Design contract**: `01-architecture-baseline.md` §六——"多实例：DB-backed `MessageService`，请求/结果通过数据库传递，支持跨进程路由"；"不同 `IMessageService` 实现的故障语义不同（内存实现崩溃丢消息，DB-backed 不丢）。这是预期行为——调用方通过超时+重试+幂等处理应对。"
- **Roadmap**: L4-2 = `IMessageService` `DBMessageService`（跨进程路由），deps = L4-1 ✅ + nop-dao

## Goals

- 一个 `DBMessageService implements IMessageService`，用数据库表持久化消息，后台轮询投递给已注册消费者——与 `LocalMessageService` / `PulsarMessageService` 同级的兄弟实现
- `LocalAgentMessenger` 用 `DBMessageService` 作为传输层时，所有 Agent 域语义（send-async、request-response、reply demux、correlation matching、ack-topic 抑制）与内存传输行为一致——无需修改 `LocalAgentMessenger` 代码
- 跨实例验证：两个独立的 `DBMessageService` 实例（独立轮询线程、独立 consumer 映射、无共享内存状态）共享同一 DB → 一个实例发送的消息被另一个实例的消费者接收——证明跨进程路由
- 至少一次投递语义：消息持久化到 DB 后不因 JVM 崩溃丢失（设计 §六 契约："DB-backed 不丢"）
- 设计文档更新：§六 DB-backed 从"设计目标"变为"已落地"

## Non-Goals

- Actor Runtime / AgentActor / Mailbox 生命周期（L4-8 — 独立平台层，建立在 messenger 之上，deps L4-1~L4-6）
- Deferred-ack mailbox / 3-phase reservation（L4-5 — 在 messenger 之上的高级语义）
- 多租户消息隔离（actor runtime Phase 2+）
- Dead-letter queue / 失败消息人工干预（optimization candidate；基本重试在 scope 内）
- 消息表分区 / 批量 claim 性能调优（optimization candidate）
- 将 `DBMessageService` 提升到 `nop-message-dao` 通用模块（future; 当前 Agent 域是唯一消费者，roadmap 将其归在 nop-ai-agent Layer 4）
- CDC（Change Data Capture）替代轮询（`nop-message-debezium` 已存在，是独立关注点）
- WebSocket / SSE 推送通知（Gateway / frontend concern）
- call-agent / send-message 工具的行为变更（已由 plan 168 交付，本计划不修改工具代码）

## Scope

### In Scope

- nop-dao / nop-orm 依赖加入 nop-ai-agent pom.xml
- 消息队列表的 ORM 模型（新表，plan-first per Protected Areas: ORM 模型结构）
- `DBMessageService implements IMessageService`：send 持久化、subscribe 注册 + 后台轮询、consumer 返回值处理
- AgentMessageEnvelope ↔ JSON 序列化辅助（payload JSON 序列化约束）
- 跨实例端到端测试（两个独立实例共享 H2 DB）
- `LocalAgentMessenger` + `DBMessageService` 组合的 request-response 端到端
- 向后兼容验证（NoOp 默认不变）
- 设计文档更新（architecture-baseline §六、roadmap L4-2、actor-runtime-vision §4.2）

### Out Of Scope

- Actor Runtime（L4-8）
- 通用化提升到 nop-message 模块
- 高级消息语义（deferred-ack、dead-letter、partitioning、多租户）
- 工具代码变更（call-agent / send-message 由 plan 168 交付，不修改）

## Execution Plan

### Phase 1 - nop-dao 依赖 + 消息队列表 ORM 模型 + 序列化辅助

Status: planned
Targets: `nop-ai/nop-ai-agent/pom.xml`, message queue table ORM model, serialization helper class, unit tests

- Item Types: `Decision | Fix | Proof`

- [ ] **Decision（DBMessageService 定位）**：`DBMessageService` 实现平台 `IMessageService` 接口，放在 `nop-ai-agent` 的 `io.nop.ai.agent.message` 包中，与 `LocalAgentMessenger` / `NoOpAgentMessenger` 同包。理由：(1) roadmap L4-2 将其归在 nop-ai-agent Layer 4，deps 为 L4-1 + nop-dao；(2) `LocalAgentMessenger` 是传输无关的（接受任何 `IMessageService`），`DBMessageService` 是与 `LocalMessageService` 同级的兄弟实现（同为 `IMessageService`，非 `IAgentMessenger` 层）；(3) 当前唯一消费者是 Agent 运行时；如果未来非 Agent 消费者需要 DB 传输，可提升到独立 `nop-message-dao` 模块（同 `PulsarMessageService` → `nop-message-pulsar` 模式）。**不修改 `IAgentMessenger` 接口或 `LocalAgentMessenger`**——DBMessageService 是传输层的兄弟实现，不是 Agent 域 messenger 的子类。
- [ ] **Decision（消息持久化方案）**：新建 ORM 模型定义一张消息队列表，每行存储一条序列化后的消息。消息的完整 `AgentMessageEnvelope`（含 payload）序列化为 JSON 文本存储。Topic 列单独提取用于 SQL 查询过滤。Status 列跟踪消息生命周期（pending → claimed → consumed）。payload 序列化为 JSON——这是一个有文档约束的设计选择：DB-backed 传输要求 payload 可 JSON 序列化（内存传输无此约束，因为 payload 按引用传递）。
- [ ] 在 `nop-ai/nop-ai-agent/pom.xml` 增加 nop-dao / nop-orm 依赖（main scope）；确保 H2 驱动经 AutoTest 框架传递可用（test scope，如果已有则不重复添加）
- [ ] 定义消息队列表的 ORM 模型（表名遵循 Nop 数据库设计规范，如 `ai_agent_message`；列至少包含：主键、topic、序列化消息体、status、consumer 实例标识、created_at、consumed_at）
- [ ] 实现 AgentMessageEnvelope ↔ JSON 序列化辅助（使用平台 JSON 工具），payload 作为嵌套 JSON 对象处理，支持往返还原
- [ ] 单元测试：ORM 模型加载验证（经 AutoTest 骨架初始化 H2 + 自动建表）
- [ ] 单元测试：序列化 round-trip（envelope 含非平凡 payload 的完整往返——所有字段一致，payload 类型正确还原）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] nop-dao / nop-orm 依赖出现在 `nop-ai/nop-ai-agent/pom.xml` 且 `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [ ] 消息队列表 ORM 模型存在且经 AutoTest 框架在 H2 中自动建表成功
- [ ] 序列化辅助 round-trip 测试通过（envelope 含非平凡 payload 的完整往返）
- [ ] **新增功能测试**：ORM 模型加载 + 序列化 round-trip 各有对应通过的测试
- [ ] **无静默跳过**：序列化失败时抛异常（不返回 null / 空字符串 / 静默忽略）
- [ ] No owner-doc update required（Phase 3 统一更新 design doc）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DBMessageService 实现 + 单元测试

Status: planned
Targets: `io.nop.ai.agent.message` 包（新增 `DBMessageService` 类），`nop-ai/nop-ai-agent/src/test/`（新测试）

- Item Types: `Decision | Fix | Proof`

- [ ] **Decision（投递模型——后台轮询 + 至少一次语义）**：`DBMessageService` 使用后台调度线程定期轮询消息表中 pending 状态的消息，反序列化后投递给已注册消费者，投递成功后标记为 consumed。多个 `DBMessageService` 实例（模拟多进程）通过原子状态更新竞争消息（claim → process → consume），确保每条消息至少投递一次、不重复投递。消息持久化到 DB 后不因 JVM 崩溃丢失（pending 状态的消息在下次轮询恢复后被重新投递）。这与 `LocalMessageService`（同步内存分发，崩溃丢消息）和 `PulsarMessageService`（broker 持久化）的故障语义不同，符合设计 §六 契约。
- [ ] **Decision（consumer 返回值处理）**：`DBMessageService` 遵循 `IMessageConsumer.onMessage` 契约处理 consumer 返回值：null → 标记消息已消费；`ConsumeLater` → 保持 pending，下次轮询重试；`CompletionStage` → 等待完成后按上述规则处理；其他非 null → 标记已消费（返回值**不触发** ack-topic 路由——ack-topic 路由是 `LocalMessageService` 特有行为，`DBMessageService` 不复制）。Agent 域 reply 路由由 `LocalAgentMessenger.HandlerAdapter` 通过 `IMessageConsumeContext.send()` / `IMessageService.send()` 经同一 `DBMessageService` 处理（response envelope 持久化到 DB，requester 端的 reply topic poller 拾取）。
- [ ] **Decision（后台轮询的生命周期管理）**：`DBMessageService` 实现的平台 `IMessageService` 接口（= `IMessageSender` + `IMessageSubscriber`）不含任何生命周期方法——无 start/stop/close，也没有 `@PreDestroy` 等价物；`IMessageSubscription` 仅有 cancel/suspend/resume，管理单条订阅而非整个后台轮询线程。因此 `DBMessageService` 必须自行提供显式的后台轮询生命周期管理：(1) 启动入口使已注册 topics 的 pending 消息开始被投递（订阅生效）；(2) 停止入口干净终止轮询并释放线程资源（无泄漏、无残留消费）。这是 Phase 2 "消息持久化跨重启" 测试（关旧实例 → 建新实例）和 Phase 3 "两个独立实例" 测试（各自独立启停）的硬前置——没有显式停止入口，无法在不杀 JVM 的情况下关闭旧实例的轮询。候选行为提示：`AutoCloseable`（try-with-resources）或显式 `start()` / `close()` 配对——具体签名属实现细节，本 plan 不规定。
- [ ] 实现 `DBMessageService implements IMessageService`：
  - `sendAsync(topic, message, options)`：序列化 message 为 JSON，INSERT 到消息表（status=pending），返回已完成的 `CompletionStage`
  - `subscribe(topic, consumer, options)`：注册 consumer 到内存 topic→consumer 映射；确保后台轮询覆盖该 topic；返回可取消 `IMessageSubscription`（cancel 后该 topic 不再被轮询，已 pending 的消息保留在 DB 等待未来 subscriber）
  - 后台轮询：周期性查询消息表中已注册 topics 的 pending 消息，原子 claim（防竞争重复），反序列化，调用 `consumer.onMessage`，按返回值标记 consumed 或保持 pending
  - `IMessageConsumeContext` 实现：提供 `send` / `sendAsync`，路由回同一 `DBMessageService`（使 consumer 可通过 context 发送响应消息到 DB）
  - 生命周期：按上方 Decision 提供显式的后台轮询启动 / 停止入口；停止后该实例不再消费任何 topic、轮询线程被释放
- [ ] 单元测试：send → 消息出现在 DB 表中（topic + 序列化 payload + status=pending）
- [ ] 单元测试：subscribe + poll → consumer 收到反序列化后的消息（payload 类型 + 内容正确还原）
- [ ] 单元测试：competing consumers → 两个 `DBMessageService` 实例共享同一 DB，各自 subscribe 同一 topic，一条消息只被一个实例消费（不重复投递）
- [ ] 单元测试：消息持久化跨重启 → send 后关闭 DBMessageService（停止轮询），新建 DBMessageService 恢复轮询 → pending 消息被投递（证明 JVM 重启不丢消息）
- [ ] 单元测试：`ConsumeLater` → consumer 返回 `ConsumeLater` → 消息保持 pending，下次轮询再次投递
- [ ] 单元测试：subscription cancel → cancel 后该 topic 的 pending 消息不再被投递给该 consumer

Exit Criteria:

- [ ] `DBMessageService` 存在于 `io.nop.ai.agent.message` 包，implements `IMessageService`
- [ ] `sendAsync` 将消息持久化到 DB 表（非内存队列；通过查询 DB 表验证）
- [ ] `subscribe` 注册 consumer 并启动后台轮询（非空方法体 / no-op）
- [ ] 后台轮询生命周期可独立启停：显式启动入口使订阅生效（pending 消息被投递），显式停止入口终止轮询且不再消费任何 topic、释放线程资源（支撑"消息持久化跨重启"测试的关旧 / 建新模式与 Phase 3 的两独立实例）
- [ ] **端到端验证**（send → poll → consume）：send 消息 → 轮询 → `consumer.onMessage` 收到反序列化的完整消息（payload 正确还原）——完整路径在一条测试中验证
- [ ] **接线验证**：`consumer.onMessage` 确实被后台轮询调用（通过测试断言 consumer 收到消息证明，非仅注册成功）
- [ ] **无静默跳过**：send 失败抛异常（不吞掉）；consumer 未注册时 send 的消息保持 pending 在 DB（不静默丢弃）；轮询异常记录日志并继续轮询（不静默停止）
- [ ] **新增功能测试**：send 持久化、poll 投递、competing consumers、消息持久化跨重启、`ConsumeLater` 重试、subscription cancel —— 各有对应通过的测试
- [ ] No owner-doc update required（Phase 3）
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` Phase 2 新增测试通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 跨实例端到端 + 引擎接线验证 + 设计文档更新

Status: planned
Targets: `nop-ai/nop-ai-agent/src/test/`（跨实例端到端测试），`ai-dev/design/nop-ai-agent/01-architecture-baseline.md`, `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`, `ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md`

- Item Types: `Proof | Follow-up`

- [ ] 端到端测试（跨实例 send-async）：两个独立的 `DBMessageService` 实例（独立轮询线程、独立 consumer 映射、无共享内存状态）共享同一 H2 DB —— 实例 A subscribe topic `agent.B.inbox` 的 handler；实例 B `send` 一条 `AgentMessageEnvelope`（kind=ASYNC）到 `agent.B.inbox`；实例 A 的 handler 收到消息（payload 正确还原）。**这证明跨进程/跨实例投递：两个实例无共享内存状态，仅通过 DB 通信。如果 `DBMessageService` 内部偷偷用了内存队列，两个独立实例无法互相投递。**
- [ ] 端到端测试（跨实例 request-response via LocalAgentMessenger）：两个 `LocalAgentMessenger`（各自包裹一个独立的 `DBMessageService` 实例，共享同一 H2 DB）—— messenger A `request`（发 REQUEST envelope 到 B 的 inbox topic）→ messenger B 的 handler 返回响应 → 响应经 DB 到达 A 的 reply topic → A 的 `CompletableFuture` 以响应 payload 完成（含超时设置）。**这证明完整 Agent 域 request-response 往返经 DB 传输端到端可用——包括 ack-topic 抑制（HandlerAdapter 返回 null 给 DBMessageService）+ correlation demux（ReplyDemultiplexer 从 reply topic poller 拾取 RESPONSE）在 DB 传输上的正确行为。**
- [ ] 端到端测试（向后兼容）：构造 `DefaultAgentEngine` 时**不**设 messenger → 默认 NoOp → 其上 request-response fail-fast（既有引擎构造路径不变，不受 `DBMessageService` 引入影响）
- [ ] 更新 `01-architecture-baseline.md` §六：标记 DB-backed MessageService 已落地（L4-2 已实现）；记录 DB-backed 故障语义（至少一次投递、JVM 崩溃不丢消息、payload JSON 序列化约束）；保持既有设计契约文本不变
- [ ] 更新 `nop-ai-agent-roadmap.md` §4 L4-2 行状态 ❌ → ✅
- [ ] 更新 `nop-ai-agent-actor-runtime-vision.md` §4.2（MessageRouter 行）：注明 DB-backed 传输已可作为多实例路由基底

Exit Criteria:

- [ ] **端到端验证**（跨实例 send-async）：两个独立 DBMessageService 实例（无共享内存）经共享 DB 完成消息投递（A 发 → B 收）
- [ ] **端到端验证**（跨实例 request-response）：两个 LocalAgentMessenger（各自包裹独立 DBMessageService）经共享 DB 完成完整 request-response 往返（A request → B handler response → A future completes with correct payload）
- [ ] **端到端验证**（向后兼容）：引擎默认 NoOp 不受影响（request fail-fast），既有测试全通过
- [ ] **接线验证**：`LocalAgentMessenger` + `DBMessageService` 组合时——HandlerAdapter 经 DBMessageService 的 IMessageConsumeContext.send() 将 RESPONSE 写入 DB → requester 端 ReplyDemultiplexer 从 reply topic poller 拾取 RESPONSE → CompletableFuture 完成（由 request-response 端到端测试覆盖）
- [ ] **Anti-Hollow Check**：跨实例端到端测试证明消息确实经过 DB（而非内存引用传递）—— 两个实例是独立 JVM 对象、独立轮询线程、独立 consumer 映射，仅共享 DB 连接。如果 DBMessageService 内部偷偷用了内存队列，两个独立实例无法互相投递，测试会失败。
- [ ] **无静默跳过**：跨实例 request-response 超时抛 `TimeoutException`（非静默 null future）；DB 不可用时 send 抛异常（非静默丢弃）
- [ ] **新增功能测试**：跨实例 send-async + 跨实例 request-response + 向后兼容 —— 各有对应通过的测试
- [ ] `01-architecture-baseline.md` §六 已更新；`nop-ai-agent-roadmap.md` L4-2 → ✅；`nop-ai-agent-actor-runtime-vision.md` §4.2 已更新
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全部通过（含新增测试 + 现有测试不受影响）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] `DBMessageService implements IMessageService` 已落地（send 持久化 + subscribe 轮询 + consumer 返回值处理）
- [ ] 消息经数据库表路由（非内存队列；跨实例端到端测试证明）
- [ ] 至少一次投递语义已落地（消息持久化跨重启测试证明 JVM 崩溃不丢消息）
- [ ] `LocalAgentMessenger` + `DBMessageService` 组合下 Agent 域 request-response 语义端到端可用
- [ ] NoOp 默认向后兼容（引擎默认不变，既有测试全通过）
- [ ] 必要 focused verification（单元 + 竞争消费者 + 跨重启 + 跨实例端到端）已完成
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [ ] 受影响 owner docs 已同步（architecture-baseline §六、roadmap L4-2、actor-runtime-vision §4.2）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证 (a) send → DB INSERT → poll → consumer.onMessage 调用链在运行时连通（经 DB 表，非自建内存队列），(b) 跨实例 request-response 经 DB 端到端连通，(c) 无空方法体 / 静默跳过 / no-op 作为正常实现
- [ ] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/171-nop-ai-agent-db-backed-messenger.md --strict` 退出码为 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 消息表清理 / 保留策略

- Classification: `optimization candidate`
- Why Not Blocking Closure: 已消费的消息在表中累积。保留策略（TTL 删除、定期清理）是维护优化。表增长率受消息量约束，清理可在不改变 `DBMessageService` 契约的情况下添加。
- Successor Required: no

### Dead-letter queue for poison messages

- Classification: `optimization candidate`
- Why Not Blocking Closure: Consumer 反复抛异常的消息会被无限重试（至少一次语义）。Dead-letter queue（N 次重试后移入 DLQ）是可靠性增强。基本重试在 scope 内；DLQ 不在。
- Successor Required: no

### Promotion to nop-message-dao generic module

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `DBMessageService` 当前放在 nop-ai-agent。如果非 Agent 平台消费者需要 DB-backed 消息，可提升到独立 `nop-message-dao` 模块（同 `PulsarMessageService` → `nop-message-pulsar` 模式）。当前 Agent-only scope 符合 roadmap 定位。
- Successor Required: no

### CDC (Change Data Capture) as alternative to polling

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 后台轮询引入延迟（vs 内存投递的即时性）。CDC（如经 `nop-message-debezium`）可提供近实时投递而无需轮询。轮询更简单且满足当前用例。
- Successor Required: no

### Multi-tenant message isolation

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 多租户 topic 命名空间隔离（tenantId 作用域）是 Actor Runtime Phase 2+ 关注点。当前 `agent.*` topic 命名无租户隔离——单租户场景完全满足。
- Successor Required: no

## Non-Blocking Follow-ups

- 消息表 TTL 自动清理 + 定期 purge
- DLQ for poison messages（N 次重试后移入死信表）
- 批量 claim 性能优化（一次 claim 多条消息）
- CDC 替代轮询（经 `nop-message-debezium`）
- 提升到 `nop-message-dao` 通用模块（当非 Agent 消费者出现时）
