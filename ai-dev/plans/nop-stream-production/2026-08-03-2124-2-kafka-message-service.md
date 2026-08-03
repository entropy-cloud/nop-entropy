# 48. Kafka IMessageService

> Plan Status: active
> Last Reviewed: 2026-08-03
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Item 48; `nop-message/nop-message-pulsar/`（参考实现）; `ai-dev/design/nop-stream/checkpoint-design.md` §数据面 IMessageService; Stage 40 `IDataPlaneWireCodec` SPI
> Mission: nop-stream-production
> Work Item: 48. Kafka IMessageService
> Related: Stage 40（`2026-08-02-2141-2-cross-jvm-data-plane-message-service.md` — 数据面跨 JVM + `IDataPlaneWireCodec` SPI，completed）; `nop-message/nop-message-pulsar/PulsarMessageService.java`（参考实现）

## Purpose

实现 `nop-message-kafka` 模块（当前空壳 placeholder），提供 `IMessageService` 的 Kafka 后端实现。Kafka 是生产流处理场景中最常用的消息队列，此实现使 nop-stream 数据面跨 JVM 传输可选择 Kafka 作为消息后端，与已有的 `PulsarMessageService`/`SysDaoMessageService` 并列。

## Current Baseline

经 live 仓库核对（2026-08-03，含独立子 agent 对抗性审查验证）：

- **`nop-message-kafka` 模块存在但为空壳**：`nop-message/nop-message-kafka/pom.xml` 仅有 `<artifactId>nop-message-kafka</artifactId>`，无 dependencies、无 src 目录。已在 `nop-message/pom.xml` modules 列表中注册。
- **kafka-clients 版本未在仓库管理**：全仓库搜索 `kafka-clients` 无结果。`nop-kernel/nop-dependencies/pom.xml` 中无 `<kafka.version>` 属性或 dependencyManagement 条目（对照 `pulsar-client-original` 在 `:264` 经 `${pulsar.version}`=2.8.0 管理）。**必须在 nop-dependencies/pom.xml 新增版本管理。**
- **`IMessageService` 接口**：extends `IMessageSender`（核心 `sendAsync(String topic, Object message, MessageSendOptions options)` → `CompletionStage<Void>`）+ `IMessageSubscriber`（核心 `subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options)` → `IMessageSubscription`）。
- **`MessageSendOptions`** 只有 3 字段：`delay`、`sendTimeout`、`cancelToken`。**无 key/partition/timestamp**——消息元数据从 `ApiMessage` headers 提取（参考 `PulsarHelper.buildPulsarMessage`）。
- **`MessageSubscribeOptions`** 有：`subscribeName`/`subscriptionType`/`concurrency`/`seekMode`/`seekToMessage`/`seekToTime`/`batchReceiveCount`/`batchReceiveTimeout`/`transactional`。Kafka 映射需逐项裁定（见 Phase 1 items）。
- **`IMessageConsumer.onMessage`** 返回值有 5 种语义：null→ack、CompletionStage→异步等待、ConsumeLater→延迟重消费、Acknowledge→ack+响应消息、其他非 null→响应消息。参考 `PulsarConsumeTask.consume()`（`:154-184`，80+ 行处理这些分支）。
- **`IMessageSubscription` 接口** 有 5 方法：`cancel()`/`isSuspended()`/`isCancelled()`/`suspend()`/`resume()`。参考 `PulsarMessageSubscription`（`:268-321`）全实现，suspend 用 `consumer.pause()`，resume 用 `consumer.resume()`。
- **`PulsarMessageService` 为完整参考**（322 行）：配置类 `PulsarClientConfig`/`PulsarProducerConfig`/`PulsarConsumerConfig`，独立 `PulsarConsumeTask`（238 行，poll 循环 + 返回值处理 + ConsumeContext），`PulsarHelper`，`PulsarErrors`，生命周期 `init()`/`destroy()`（**注意：方法名是 destroy 不是 close**），`pulsar-defaults.beans.xml`（`destroy-method="destroy"`）。
- **`IDataPlaneWireCodec` 接口**（`nop-stream-runtime/transport/`）：只有 `Object toWire(StreamMessageEnvelope)` + `StreamMessageEnvelope fromWire(Object)` 两方法。**无类型标识字段/方法/注解**。codec 选择通过 beans.xml class 属性或 `DataPlaneMessageServiceAdapter` 构造时显式传 INSTANCE（如 `new DataPlaneMessageServiceAdapter(backend, PulsarStringWireCodec.INSTANCE)`）。三个实现（`PulsarStringWireCodec`/`SysDaoWireCodec`/`IdentityWireCodec`）全部在 `nop-stream-runtime/transport/`，设计原则是 **codec 不依赖后端类**，保持 nop-stream-runtime 无后端硬依赖。
- **`stream-data-plane.beans.xml`**（Stage 40）：只有两个默认 bean（`LocalMessageService` + `IdentityWireCodec`）。SysDao/Pulsar 的 codec bean **不在该文件中**——注释（`:36-53`）说明由应用层 beans.xml 覆盖。
- **Pulsar E2E 测试位置**：`TestDataPlanePulsarBackendE2E` 在 **`nop-stream-runtime/src/test`**（不在 nop-message-pulsar），因需 `DataPlaneMessageServiceAdapter` + `RemoteResultPartition` + `RemoteInputChannel` 完整链路。`nop-message-pulsar` 的测试只有 IoC/consume-task/helper 组件级测试。

### 真正剩余的 gap

- `nop-message-kafka` 零实现——需从零构建 `KafkaMessageService implements IMessageService`。
- kafka-clients 版本未管理——需新增 `nop-dependencies/pom.xml` 版本声明。
- 数据面 wire codec 需放在 nop-stream-runtime（与其他三个 codec 一致），不是 nop-message-kafka。

## Goals

- **`KafkaMessageService` 完整实现 `IMessageService`**：`sendAsync`（`KafkaProducer`）+ `subscribe`（`KafkaConsumer` poll 循环 + `IMessageConsumer` 回调 + 5 种返回值语义处理）。
- **配置类 + 生命周期**：`KafkaClientConfig`/`KafkaProducerConfig`/`KafkaConsumerConfig`；`init()`/`destroy()` 生命周期（遵循 PulsarMessageService 命名）。
- **`KafkaMessageSubscription` 完整实现 `IMessageSubscription`** 5 方法（cancel/suspend/resume/isSuspended/isCancelled），suspend/resume 映射到 `KafkaConsumer.pause()`/`resume()`。
- **独立 `KafkaConsumeTask`**：poll 循环 + 返回值处理（null→commit offset、CompletionStage→异步等待后判断、ConsumeLater→seek 不 commit、Acknowledge/其他→响应消息到 ack topic），遵循 `PulsarConsumeTask` 模式。
- **kafka-clients 版本管理**：`nop-dependencies/pom.xml` 新增 `<kafka.version>` + dependencyManagement。
- **数据面 wire codec**：在 `nop-stream-runtime/transport/` 新增 `KafkaStringWireCodec`（或评估复用 `PulsarStringWireCodec`/`IdentityWireCodec`），与其他三个 codec 一致放置。
- **测试覆盖**：组件级测试（nop-message-kafka）+ 数据面 E2E（nop-stream-runtime，`@EnabledIfSystemProperty` 门控）。

## Non-Goals

- **事务型 Kafka producer（exactly-once Kafka transactions）**：Kafka 事务 API 是独立大特性。初版 at-least-once（与 PulsarMessageService 初版对等）。
- **Kafka Streams / Kafka Connect / Schema Registry**。
- **nop-stream Kafka source/sink connector**（FLIP-27 风格 SourceFunction/SinkFunction）：属 Stage 49/53。

## Scope

### In Scope

- `KafkaMessageService implements IMessageService`（`sendAsync` + `subscribe`）。
- 配置类：`KafkaClientConfig`/`KafkaProducerConfig`/`KafkaConsumerConfig`（`@DataBean`）。
- `KafkaErrors`（错误码，遵循 `PulsarErrors` 模式）。
- 独立 `KafkaConsumeTask`（poll 循环 + 返回值处理 + ConsumeContext）。
- `KafkaMessageSubscription implements IMessageSubscription`（5 方法完整实现）。
- 生命周期：`init()`/`destroy()`（遵循 PulsarMessageService 命名）。
- `kafka-defaults.beans.xml`（`destroy-method="destroy"`）。
- `nop-dependencies/pom.xml` 新增 `<kafka.version>` + dependencyManagement。
- `KafkaStringWireCodec`（在 `nop-stream-runtime/transport/`，与其他 codec 一致），或复用已有 codec（Phase 2 裁定）。
- 组件级测试（nop-message-kafka）+ 数据面 E2E（nop-stream-runtime，gated）。

### Out Of Scope

- Kafka 事务型 producer（exactly-once）。
- Kafka Streams / Kafka Connect / Schema Registry。
- nop-stream Kafka SourceFunction/SinkFunction（Stage 49/53）。

## Execution Plan

### Phase 1 — KafkaMessageService 核心实现 + 版本管理

Status: planned
Targets: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/`（新建）; `nop-kernel/nop-dependencies/pom.xml`; `nop-message/nop-message-kafka/pom.xml`; `nop-message/nop-message-kafka/src/main/resources/_vfs/nop/message/kafka/beans/kafka-defaults.beans.xml`

- Item Types: `Fix | Proof`

- [ ] **kafka-clients 版本管理**：`nop-kernel/nop-dependencies/pom.xml` 新增 `<kafka.version>` 属性（如 3.5.0，与 JDK 11+ 兼容）+ `<dependencyManagement>` 条目 `org.apache.kafka:kafka-clients:${kafka.version}`——`Fix`
- [ ] **pom.xml 依赖声明**：`nop-message-kafka/pom.xml` 添加 compile 依赖 `kafka-clients`、`nop-core`（**不是 nop-api-core**——ApiMessage/ApiRequest/ApiHeaders 在 nop-core）、`slf4j-api`；test 依赖 `mockito-core`（mock KafkaProducer/Consumer）、`nop-autotest-junit`（与 Pulsar 一致）、`junit-jupiter`——`Fix`
- [ ] **`KafkaHelper`**：将 `ConsumerRecord`/`ApiMessage` 互转的辅助方法集中在独立类（参考 `PulsarHelper.buildPulsarMessage`/`buildApiMessage` 模式），使 `KafkaConsumeTask` 和 `sendAsync` 共享转换逻辑——`Fix`
- [ ] **配置类**：`KafkaClientConfig`（bootstrapServers/clientId 等）、`KafkaProducerConfig`（acks/retries/batchSize/lingerMs 等）、`KafkaConsumerConfig`（groupId/autoOffsetReset/enableAutoCommit/maxPollRecords 等），`@DataBean`——`Fix`
- [ ] **`KafkaErrors`**：错误码（`ERR_BOOTSTRAP_SERVERS_NOT_CONFIGURED`/`ERR_GROUP_ID_NOT_CONFIGURED` 等）——`Fix`
- [ ] **`sendAsync` 实现**：创建/复用 `KafkaProducer<String, byte[]>`，message 序列化为 `ProducerRecord`。消息 key 从 `ApiMessage` bizKey header 提取（参考 `PulsarHelper.buildPulsarMessage`），topic 映射到 Kafka topic。返回 `CompletableFuture<Void>`（`producer.send` callback → `CompletableFuture`）——`Fix`
- [ ] **`subscribe` + `KafkaConsumeTask`**：创建 `KafkaConsumer`，`subscribe(topic)`。`KafkaConsumeTask` 独立类（遵循 `PulsarConsumeTask` 模式），启动 poll 循环线程（`ExecutorService`）。每条 `ConsumerRecord` 经 `KafkaHelper.buildApiMessage(record)`（参考 `PulsarHelper.buildApiMessage` 模式，将 record key/value/headers/topic/partition/offset/timestamp 映射到 `ApiMessage`/`ApiRequest`，**不经 codec**——codec 是 DataPlaneMessageServiceAdapter 层职责，不在 IMessageService 后端实现层）后回调 `IMessageConsumer.onMessage`。返回值处理：null→`consumer.commitSync()`；CompletionStage→异步等待后判断；ConsumeLater→`consumer.seek()` 不 commit；Acknowledge/其他→ack topic 发送响应。持有 `ConsumeContext`（`IMessageConsumeContext` 实现）——`Fix`
- [ ] **`KafkaMessageSubscription`** 实现 5 方法：`cancel()`（停止 poll 线程 + `consumer.close()`）、`suspend()`（`consumer.pause(topicPartitions)`）、`resume()`（`consumer.resume(topicPartitions)`）、`isSuspended()`/`isCancelled()`（volatile 标志位）——`Fix`
- [ ] **`MessageSubscribeOptions` → Kafka 映射**：`subscribeName`→Kafka `group.id`（若 KafkaConsumerConfig.groupId 未设则用 subscribeName）；`concurrency`>1→多个 `KafkaConsumer` + `MultiMessageSubscription` 包装（参考 PulsarMessageService `:192-209`）；`subscriptionType`→Kafka 无直接对应，日志 warn 后忽略；`seekMode`/`seekToTime`→若设了则 `consumer.seek(...)` 实现，未设则跳过（注意 `PulsarConsumeTask.seekToPosition()` 当前是 TODO stub——Kafka 版**不许复制 stub**，要么实现 `consumer.seek()`，要么设了 seekMode 时抛 `UnsupportedOperationException`，不可静默跳过）；`batchReceiveCount`/`batchReceiveTimeout`→`poll(Duration)` 参数——`Fix`
- [ ] **生命周期**：`init()`（校验 bootstrapServers/groupId 非空 + 初始化 producer）、`destroy()`（**注意：方法名是 destroy 不是 close**，与 PulsarMessageService 一致），graceful shutdown producer + 所有 subscription poll 线程——`Fix`
- [ ] **`kafka-defaults.beans.xml`**：`destroy-method="destroy"`，遵循 `pulsar-defaults.beans.xml` 模式——`Fix`
- [ ] **组件级测试**：`sendAsync` 单测（mock `KafkaProducer`，验证 `ProducerRecord` 构造 + callback → CompletableFuture）；`subscribe` 单测（mock `KafkaConsumer` poll 返回测试记录，验证 `IMessageConsumer.onMessage` 被调用 + 返回值分支处理）；`KafkaMessageSubscription` suspend/resume/cancel 测试——`Proof`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `KafkaMessageService implements IMessageService`，`sendAsync` 和 `subscribe` 均为真实实现（非空方法体/非 stub）
- [ ] `KafkaConsumeTask` 独立类处理 poll 循环 + 5 种 `IMessageConsumer.onMessage` 返回值语义（非简化为"null 就 commit"）
- [ ] `KafkaMessageSubscription` 实现 `IMessageSubscription` 全部 5 方法（suspend/resume 映射到 `KafkaConsumer.pause/resume`）
- [ ] 生命周期方法名为 `init()`/`destroy()`（不是 close）
- [ ] **无静默跳过**：bootstrapServers/groupId 为 null 抛 `NopException`（见 Minimum Rules #24）；未支持的 `MessageSubscribeOptions` 字段（如 subscriptionType）日志 warn 而非静默忽略
- [ ] **新增功能测试覆盖**：sendAsync 1 test + subscribe（含返回值分支）1+ test + subscription 生命周期 1 test
- [ ] kafka-clients 版本经 `nop-dependencies/pom.xml` 统一管理
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 数据面 wire codec + E2E 集成测试

Status: planned
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/KafkaStringWireCodec.java`（与其他 codec 一致放置）; `nop-stream/nop-stream-runtime/src/test/java/.../TestDataPlaneKafkaBackendE2E.java`; owner-docs

- Item Types: `Fix | Decision | Proof`

- [ ] **codec 设计决策**：评估 `PulsarStringWireCodec`（`toWire`/`fromWire` 逻辑处理 ApiMessage/ApiRequest data 字段，JSON String 互转）是否可直接复用于 Kafka（Kafka 用 `StringSerializer`/`StringDeserializer` 时 wire format 与 Pulsar 完全一致）。注意 `PulsarStringWireCodec` 是 `final class`（不可继承）。若可复用 → Decision-only（直接使用 `PulsarStringWireCodec.INSTANCE`，接受命名脱节，或新建 `KafkaStringWireCodec` 委托 `PulsarStringWireCodec.INSTANCE`——薄 wrapper 非继承）；若需差异 → 新建 `KafkaStringWireCodec implements IDataPlaneWireCodec`（在 nop-stream-runtime/transport/，与其他三个 codec 一致放置，**不放 nop-message-kafka**）——`Decision | Fix`
- [ ] **wire codec 实现**（若新建）：`KafkaStringWireCodec implements IDataPlaneWireCodec`，encode 将数据面 envelope（barrier/watermark/record payload）序列化为 String，decode 反向。**无类型标识机制**（IDataPlaneWireCodec 无此设计）——codec 通过 `DataPlaneMessageServiceAdapter` 构造时显式传入或 beans.xml class 属性选择——`Fix`
- [ ] **E2E 集成测试**（`nop-stream-runtime/src/test`，`@EnabledIfSystemProperty("test.kafka.brokers")` 门控）：需真实 Kafka broker，验证 `KafkaMessageService` + `KafkaStringWireCodec` + `DataPlaneMessageServiceAdapter` 完整数据面路径：producer 发送 → consumer poll → `RemoteInputChannel` 收到消息。参考 `TestDataPlanePulsarBackendE2E` 结构。需在 nop-stream-runtime test scope 添加 nop-message-kafka 依赖——`Fix`
- [ ] **接线验证**：断言 `KafkaStringWireCodec` 确实被 `DataPlaneMessageServiceAdapter` 消费（codec INSTANCE 经构造函数传入或 beans.xml 注入）——`Proof`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `KafkaStringWireCodec`（或复用 `PulsarStringWireCodec`）实现 `IDataPlaneWireCodec`，放置在 `nop-stream-runtime/transport/`（与其他 codec 一致）
- [ ] **端到端验证**：`@EnabledIfSystemProperty` 门控的 E2E 测试覆盖 `KafkaMessageService` + codec + `DataPlaneMessageServiceAdapter` + `RemoteResultPartition`/`RemoteInputChannel` 完整数据面路径（broker 不可用时默认跳过，不 fail CI——见 Minimum Rules #22）
- [ ] **接线验证**：`KafkaStringWireCodec` 确实被 `DataPlaneMessageServiceAdapter` 消费（见 Minimum Rules #23）
- [ ] **无静默跳过**：未实现的方法/分支抛异常或 warn 日志而非返回 null（见 Minimum Rules #24）
- [ ] **新增功能测试覆盖**：WireCodec round-trip 1 test + E2E 1 test（gated）
- [ ] nop-stream-runtime 无后端硬依赖（codec 不依赖 kafka-clients，与 Pulsar codec 不依赖 pulsar-client 一致）
- [ ] `ai-dev/design/nop-stream/checkpoint-design.md` §数据面 IMessageService 更新：Kafka 后端列入已实现后端
- [ ] `ai-dev/design/nop-stream/01-architecture-baseline.md` 模块表更新 `nop-message-kafka` 状态
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] `KafkaMessageService` 完整实现 `IMessageService`（sendAsync + subscribe），`KafkaConsumeTask` 处理 5 种返回值语义，`KafkaMessageSubscription` 实现 5 方法
- [ ] 配置类 + `init()`/`destroy()` 生命周期完整
- [ ] kafka-clients 版本经 `nop-dependencies/pom.xml` 统一管理
- [ ] `KafkaStringWireCodec`（或复用）放置在 nop-stream-runtime，round-trip 语义完整，被 `DataPlaneMessageServiceAdapter` 消费
- [ ] `kafka-defaults.beans.xml` 部署脚手架可用
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）`sendAsync` 确实调用 `KafkaProducer.send`，（b）`subscribe` poll 循环确实回调 `IMessageConsumer` + 处理返回值，（c）`KafkaStringWireCodec` 确实被 `DataPlaneMessageServiceAdapter` 消费
- [ ] `./mvnw test -pl nop-message/nop-message-kafka -am`
- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime -am`（E2E gated，默认跳过）
- [ ] checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-message-kafka --severity high` 退出码为 0

## Deferred But Adjudicated

### Kafka 事务型 producer（exactly-once Kafka transactions）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Kafka 事务 API（`initTransactions`/`beginTransaction`/`sendOffsetsToTransaction`/`commitTransaction`）是独立大特性，需配合 nop-stream checkpoint 两阶段提交协议设计。初版 at-least-once 语义与 `PulsarMessageService` 初版对等。exactly-once 需求由 sink 端幂等或 `TwoPhaseCommitSinkFunction`（Stage 52）覆盖。
- Successor Required: yes
- Successor Path: 未来增强 plan 或绑定 Stage 52（事务型 JDBC sink 2PC）的事务框架抽象

## Non-Blocking Follow-ups

- nop-stream Kafka SourceFunction/SinkFunction connector → Stage 49（Source split）/ Stage 53（CDC + file sink）
- Kafka Schema Registry 集成 → 远期
- Kafka Streams 集成 → 远期

## Closure

Status Note: (pending)
Completed: (pending)

Closure Audit Evidence:

- Reviewer / Agent: (pending)
- Audit Session: (pending)
- Evidence:
  - 每条 Exit Criterion 验证结果: (pending)
  - 每条 Closure Gate 验证结果: (pending)
  - `check-plan-checklist.mjs --strict` 退出码: (pending)
  - `scan-hollow-implementations.mjs --severity high` 退出码: (pending)
  - Anti-Hollow 检查结果: (pending)
  - Deferred 项分类检查: (pending)

Follow-up:

- (pending)
