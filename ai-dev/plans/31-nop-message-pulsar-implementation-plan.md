# 31 nop-message-pulsar Implementation Plan

> Plan Status: completed
> Last Reviewed: 2026-05-21
> Source: `ai-dev/audits/2026-05-20-adversarial-review-nop-message-pulsar/adversarial-review-round1.md`
> Related: none
> Review History:
>   - Round 1: 后羿(architect) approve-with-comments + 盘古(implementer) approve-with-comments → 2 blocking + 5 significant issues
>   - Round 2: 后羿(consensus) **APPROVE** — 所有 blocking/significant issues 已解决，4 minor observations 供实施参考

## Purpose

将 nop-message-pulsar 从不可运行的代码骨架推进到最小可用的 Pulsar 消息服务实现，同时修复 nop-message-core 中已确认的运行时 bug，并建立基本的 IoC 集成和测试覆盖。

## Current Baseline

- nop-message-pulsar 有 7 个 Java 文件，但全部处于骨架/占位符状态：
  - `PulsarMessageService`：实现了 `IMessageService` 接口，但 `PulsarClient`、`topicSchemas`、`defaultProducer` 均未初始化，无生命周期管理，无 IoC 配置
  - `PulsarConsumeTask`：消费循环因 `active` 永远为 false 只跑一次；`seekToPosition()` 空方法体
  - `PulsarHelper`：`buildApiMessage` 返回 null，`encodeValue` 返回 null
  - `PulsarProducerConfig` / `PulsarConsumerConfig`：完全空类，无 `@DataBean` 注解
  - `subscriptionConfigs` 字段为死代码（声明但从未使用）
  - 无 beans.xml、无 autoconfig 入口、无 `_module` 文件、无测试
- `pom.xml` 依赖 `pulsar-client-api`（仅接口），缺少 `pulsar-client-original`（实现类），`nop-dependencies` 仅管理 `pulsar-client-api:2.8.0`
- nop-message-core 的 `LocalMessageService` 基本可用，但 `invokeMessageListener` 异步路径传递 CompletionStage 对象而非解析后的值（审计发现 10），消费循环无异常隔离（审计发现 11）
- `MultiMessageSubscription` 的 cancel/suspend/resume 只保留最后一个异常（审计发现 12）
- nop-message-kafka、nop-message-codec、nop-message-model 为空壳模块
- 审计共发现 17 个问题，其中 P0 级（运行时必然失败）7 个，设计级 2 个，空壳级 3 个

## Goals

- PulsarMessageService 能通过 NopIoC 自动装配，支持基本的同步发送和消费
- PulsarHelper 正确完成 ApiMessage ↔ Pulsar Message 的双向转换
- PulsarConsumeTask 能持续消费，支持优雅停止
- Config 类具备最基本的 Pulsar 配置属性（serviceUrl、topic schema 等）
- 修复 nop-message-core 中已确认的异步处理 bug
- 建立 nop-message-pulsar 的基本单元测试结构

## Non-Goals

- 不实现 Kafka 模块（nop-message-kafka 保持空壳）
- 不重构 API 层的 SubscriptionType 抽象（审计发现 16，属于跨模块设计决策，需单独讨论）
- 不实现完整的事务支持（事务框架已搭好骨架，但验证需要 Pulsar 集群环境，本次仅确保编译通过和基本结构正确）
- 不实现 SeekMode/seekTo 相关功能（空壳方法保留，添加 TODO 注释）
- 不增加 MessageSendOptions 的 message ID 返回能力（审计发现 17，接口变更需跨模块协调）
- 不清理空壳模块（nop-message-model、nop-message-codec、nop-message-kafka 保持现状）

## Scope

### In Scope

- nop-message-pulsar 的核心功能补全（client 初始化、IoC 配置、消息收发）
- nop-message-pulsar 的 pom.xml 依赖修正（`pulsar-client-api` → `pulsar-client-original`）
- nop-message-core 的异步 bug 修复
- nop-api-core 的 MultiMessageSubscription 异常处理改进
- nop-message-pulsar 的 NopIoC autoconfig 链路（beans.xml + autoconfig 入口 + `_module`）
- nop-message-pulsar 的基本测试

### Out Of Scope

- API 层 SubscriptionType 重构
- Kafka 模块实现
- 事务功能的端到端验证
- 空壳模块清理
- 分布式追踪和 message ID 返回

## Execution Plan

### Phase 1 - PulsarHelper 补全与消息转换

Status: completed
Depends on: none
Targets: `nop-message-pulsar/.../PulsarHelper.java`

- Item Types: `Fix`

- [x] 实现 `buildApiMessage`：从 Pulsar Message 构建完整的 ApiMessage（提取 key、value、messageId、topicName、eventTime、properties、publishTime、sequenceId），对 `Schema.STRING` 消息将 value 设置到 ApiMessage.setData()，对 `Schema.BYTES` 妥善处理
- [x] 实现 `encodeValue`：将 Object 值编码为 String（String 原样返回，Number/Boolean 调用 toString()，其他类型 JSON 序列化），null 输入返回 null（调用方 `_buildPulsarMessage` 跳过 null property）

Exit Criteria:

- [x] `buildApiMessage` 返回非 null 的 ApiMessage，包含 Pulsar Message 的所有关键字段
- [x] `encodeValue` 对 String/Number/Boolean/null 返回正确的 String 表示或 null，不会导致 Pulsar `TypedMessageBuilder.property()` 失败
- [x] `./mvnw compile -pl nop-message/nop-message-pulsar` 编译通过
- [x] No owner-doc update required

### Phase 2 - PulsarMessageService 生命周期、依赖与 IoC 集成

Status: completed
Depends on: Phase 1（PulsarHelper 被 PulsarMessageService 和 PulsarConsumeTask 使用）
Targets: `nop-message-pulsar/.../PulsarMessageService.java`, `PulsarClientConfig.java`, `PulsarProducerConfig.java`, `PulsarConsumerConfig.java`, `pom.xml`, 新增 autoconfig/beans.xml

- Item Types: `Fix`, `Decision`

- [x] `pom.xml`：将依赖从 `pulsar-client-api` 改为 `pulsar-client-original`（2.8.0），并在 `nop-dependencies/pom.xml` 中增加对应版本管理
- [x] `PulsarClientConfig`：确认 `serviceUrl` 和 `enableTransaction` 的注入路径（通过 beans.xml property 或 `@InjectValue`）；无显式配置时 `serviceUrl` 为 null 导致 fail-fast
- [x] `PulsarProducerConfig` 增加基本配置属性（batchingEnabled、batchMaxMessages、sendTimeout）并添加 `@DataBean` 注解
- [x] `PulsarConsumerConfig` 增加基本配置属性（ackTimeout、negativeAckRedeliveryDelay、maxTotalReceiverQueueSizeAcrossPartitions）并添加 `@DataBean` 注解
- [x] PulsarMessageService 增加生命周期方法：`init()`（`@PostConstruct` 或 beans.xml `ioc:alive-method`）创建 PulsarClient；`destroy()`（`@PreDestroy` 或 beans.xml `ioc:destroy-method`）关闭所有 Producer（清空 `producers` map）、Consumer 和 Client
- [x] 删除死代码字段 `subscriptionConfigs`
- [x] `topicSchemas` 初始化为 `ConcurrentHashMap<>`（空 map，通过 setter 注入或配置加载）
- [x] `defaultProducer` 在 `init()` 中用 `Schema.STRING` 创建（明确其作为未知 topic 的 fallback，仅支持 String 消息）
- [x] `doSubscribe` 传递 `subConfig.getTopic()` → `builder.topic()`、`options.getSubscribeName()` → `builder.subscriptionName()`、`options.getSubscriptionType()` → `builder.subscriptionType()`
- [x] 创建 NopIoC autoconfig 链路：
  - `src/main/resources/_vfs/nop/autoconfig/nop-message-pulsar.beans`（内容为 beans.xml 的路径引用）
  - `src/main/resources/_vfs/nop/message/pulsar/beans/pulsar-defaults.beans.xml`（注册 PulsarClientConfig、PulsarProducerConfig、PulsarConsumerConfig、PulsarMessageService，声明生命周期方法）

Exit Criteria:

- [x] `PulsarMessageService.init()` 能根据 `PulsarClientConfig.serviceUrl` 创建 PulsarClient；serviceUrl 为 null 时 fail-fast
- [x] `PulsarMessageService.destroy()` 按顺序关闭：所有 Producer → 清空 producers map → 所有 Consumer → PulsarClient
- [x] beans.xml 遵循 NopIoC autoconfig 约定（autoconfig 入口文件 + beans.xml），可通过 `AppBeanContainerLoader` 自动发现
- [x] `doSubscribe` 至少设置了 topic 和 subscriptionName
- [x] `subscriptionConfigs` 死代码已删除
- [x] `./mvnw compile -pl nop-message/nop-message-pulsar` 编译通过
- [x] No owner-doc update required

### Phase 3 - PulsarConsumeTask 消费循环修复与资源管理

Status: completed
Depends on: Phase 2（Consumer 创建依赖 doSubscribe 修复）
Targets: `nop-message-pulsar/.../PulsarConsumeTask.java`, `PulsarMessageService.java`

- Item Types: `Fix`

- [x] `start()` 在调用 `runTask()` 前设置 `active = true`
- [x] `stop()` 实现：设置 `active = false`
- [x] 将 Executor 类型从 `Executor` 改为 `ExecutorService`，在 subscription 取消时 shutdown
- [x] 关闭顺序修正：`active = false` → `Consumer.close()`（打断 `receive()` 阻塞）→ `ExecutorService.shutdown()` + `awaitTermination(timeout)`。避免 PulsarClient.close() 与消费线程的潜在死锁
- [x] PulsarMessageSubscription.doOnCancel 按上述顺序关闭 Consumer 和 ExecutorService
- [x] `seekToPosition()` 添加 TODO 注释标记为未实现（保留空方法体）
- [x] `runTask` 增加消费失败时的退避策略：固定 sleep 1 秒（可配置），避免紧密空循环

Exit Criteria:

- [x] `start()` 后消费者能持续消费消息（`active = true`）
- [x] `stop()` 后消费者不再拉取新消息
- [x] subscription 取消时按正确顺序关闭 Consumer 和 ExecutorService
- [x] 消费失败不会导致紧密空循环（至少 1 秒间隔）
- [x] `./mvnw compile -pl nop-message/nop-message-pulsar` 编译通过
- [x] No owner-doc update required

### Phase 4 - nop-message-core Bug 修复

Status: completed
Depends on: none（可与 Phase 1-3 并行）
Targets: `nop-message-core/.../LocalMessageService.java`, `nop-api-core/.../MultiMessageSubscription.java`

- Item Types: `Fix`

- [x] `LocalMessageService.invokeMessageListener`：异步路径传递 `r`（CompletionStage 解析后的值）而非 `ret`（CompletionStage 对象本身），确保 `CompletionStage<Acknowledge>` 的 Acknowledge 对象能被 `handleMessageResult` 正确识别
- [x] `LocalMessageService.invokeMessageListener`：为每个消费者的 `onMessage` 调用添加 try-catch，单个消费者异常不阻断后续消费者，异常记入日志
- [x] `MultiMessageSubscription`：cancel/suspend/resume 使用 suppressed exceptions 保留所有异常

Exit Criteria:

- [x] 异步消费者的 Acknowledge 返回值能被正确识别和处理
- [x] 单个消费者抛异常时，后续消费者仍能收到消息
- [x] MultiMessageSubscription 部分失败时所有异常信息可获取
- [x] `./mvnw test -pl nop-message/nop-message-core` 全部通过
- [x] No owner-doc update required

### Phase 5 - 测试结构与验证

Status: completed
Depends on: Phase 1-4 全部完成
Targets: `nop-message-pulsar/src/test/`, `nop-message-core/src/test/`

- Item Types: `Proof`

- [x] PulsarHelper 单元测试：`buildApiMessage`（mock Pulsar Message，验证各字段映射）和 `encodeValue`（String/Number/Boolean/null/复杂对象）的正向/边界测试
- [x] PulsarConsumeTask 单元测试：验证 `start()` 设置 `active = true`；验证 `stop()` 设置 `active = false`；mock Consumer 验证消费循环行为
- [x] LocalMessageService 异步路径测试：验证 CompletionStage<Acknowledge> 返回值的正确处理
- [x] LocalMessageService 异常隔离测试：验证单个消费者异常不影响其他消费者
- [x] PulsarMessageService IoC 测试：验证 beans.xml 定义可被 NopIoC 解析、PulsarClientConfig 属性可注入（使用测试 beans.xml，mock 掉 PulsarClient 创建，不依赖真实 Pulsar 集群）

Exit Criteria:

- [x] `./mvnw test -pl nop-message/nop-message-pulsar,nop-message/nop-message-core` 全部通过
- [x] PulsarHelper 测试覆盖 buildApiMessage 和 encodeValue 的主要分支
- [x] PulsarConsumeTask 测试覆盖 start/stop 和基本循环行为
- [x] LocalMessageService 测试覆盖异步路径和异常隔离
- [x] IoC 测试验证 bean 定义可被 NopIoC 解析（无需真实 Pulsar 集群）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 审计发现 1-9（P0/P1 级 Pulsar 模块 bug）已全部修复
- [x] 审计发现 10-12（core 模块 bug）已全部修复
- [x] 审计发现 13（pulsar-client 依赖缺失）已修复（pom.xml 依赖已更新）
- [x] PulsarMessageService 可通过 NopIoC 装配、支持基本的同步发送和持续消费
- [x] 不存在被降级到 deferred 的 in-scope live defect
- [x] 受影响的 owner docs 已同步（或明确 No owner-doc update required）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw compile -pl nop-message` 编译通过
- [x] `./mvnw test -pl nop-message` 测试通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 审计发现 16 - SubscriptionType 暴露 Pulsar 概念到 API 层

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 这是 API 层设计决策，涉及 nop-api-core 的公共接口变更，影响所有 IMessageService 实现。需要独立的架构讨论，不应在单个模块实现计划中解决
- Successor Required: yes（需要独立的设计讨论 plan）

### 审计发现 17 - sendAsync 丢弃 MessageId

- Classification: `optimization candidate`
- Why Not Blocking Closure: IMessageSender.sendAsync 接口返回 CompletionStage<Void>，返回 MessageId 需要接口变更。当前 IMessageService 的所有实现（LocalMessageService、PulsarMessageService）都遵循此接口约定。修改接口是跨模块变更，不阻塞 Pulsar 模块的可用性
- Successor Required: yes（需要跨模块接口设计 plan）

### 审计发现 14 - 空壳模块

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 空壳模块不影响 Pulsar 模块的功能完整性。是否保留/删除/填充需要产品级决策
- Successor Required: no

### 审计发现 15 - Config 完整配置映射

- Classification: `optimization candidate`
- Why Not Blocking Closure: Phase 2 已处理最基本的配置属性，足以支持基本使用。完整 Pulsar Producer/Consumer 参数映射是优化项
- Successor Required: no

## Non-Blocking Follow-ups

- SeekMode / seekTo 功能实现（seekToPosition 当前为空方法体）
- 完整的 Pulsar Producer/Consumer 配置属性映射
- 事务功能的端到端验证
- 分布式追踪 / message ID 返回能力
- nop-message-debezium 模块的集成审查
- Pulsar 版本升级（当前 2.8.0 较旧，考虑升级到 2.10+ 或 3.x）

## Implementation Notes

> 以下来自 Round 2 审查的 minor observations，不阻塞执行，供实施者参考。

1. **`_module` 文件**：计划未显式提及 `_vfs/nop/message/pulsar/_module` 文件。实施时验证是否需要，参考 nop-message-core 的实际结构（它没有独立的 `_module` 文件，autoconfig 入口已足够）
2. **`PulsarClientConfig` 的 `@DataBean` 注解**：Producer/Consumer Config 已明确要求添加。`PulsarClientConfig` 已有该注解（审计确认），无需额外操作
3. **init()/destroy() 实现方式**：计划使用了"或"的表述。建议实施时选择 beans.xml 的 `ioc:alive-method` / `ioc:destroy-method` 方式（与 `ReflectionMessageSubscriptionRegistrar` 使用 `ioc:delay-method` 的模式一致）
4. **Phase 5 IoC 测试的 fail-fast 验证**：serviceUrl 为 null 时的 fail-fast 行为未在测试项中显式列出，但可通过 PulsarClient.create(null) 本身抛异常被测试间接覆盖

## Closure

Status Note: 所有 5 个 Phase 均已完成，exit criteria 全部满足。独立 closure audit (houyi sub-agent) 已验证全部 closure gates 通过。nop-message-pulsar 已从不可运行的代码骨架成功推进到最小可用的 Pulsar 消息服务实现，支持 NopIoC 自动装配、同步发送和持续消费。nop-message-core 的异步 bug 和异常隔离已修复。

Closure Audit Evidence:

- Reviewer / Agent: houyi (independent closure-audit sub-agent)
- Evidence: task_id=ses_1b9d2e8baffe3Ejw57PiDiaKNx, all 25+ exit criteria verified PASS against live source code

Follow-up:

- SeekMode / seekTo 功能实现（seekToPosition 当前为空方法体 + TODO）
- 完整的 Pulsar Producer/Consumer 配置属性映射
- 事务功能的端到端验证
- 分布式追踪 / message ID 返回能力
- SubscriptionType API 层重构（需独立设计讨论）
- sendAsync 丢弃 MessageId（需跨模块接口设计）
- nop-message-debezium 模块的集成审查
- Pulsar 版本升级（当前 2.8.0 较旧）
- PulsarConsumeTask 消费循环的完整集成测试（需 Pulsar mock 或 embedded instance）

## Implementation Notes

> 实施过程中的实际决策记录：

1. **init-method/destroy-method 替代 ioc:alive-method**：NopIoC beans.xdef 不支持 `ioc:alive-method` 属性，改用标准的 `init-method` + `destroy-method`
2. **`ioc:default="true"`**：autoconfig beans.xml 中所有 bean 使用 `ioc:default="true"` 以允许用户通过自定义 beans.xml 覆盖配置
3. **`getSequenceId()` 返回 long**：Pulsar Message.getSequenceId() 返回原始类型 long（非 Long），测试中不能 mock 为 null，已调整测试用例
4. **JSON.stringify 需要 JSON provider**：PulsarHelper.encodeValue 的 JSON 序列化依赖 `JSON.stringify()`，需要 NopIoC 初始化后的 JSON provider，测试需使用 `@NopTestConfig`
