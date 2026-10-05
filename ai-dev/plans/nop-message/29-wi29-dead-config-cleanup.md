# 29 WI29 死配置字段清理

> Plan Status: completed
> Draft Review: 一轮独立子代理对抗审查（agent_8580daed）3 HIGH + 7 MEDIUM/LOW 全修——F-37 原子变更三件事（构造顺序重排/List.copyOf/空列表返回 false+反转既有测试）、nop-stream-runtime 回归显式列入、TestPulsarIoC 编证破坏迁移、subscribe 路径 not-active 守卫纳入、extraProps 收口声明、错误码新码裁定、可达性勘正、Non-Goals 去重
> Last Reviewed: 2026-10-05
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI29，deps WI4✓；对应 MSG-F-18/F-19/F-29/F-34/F-35/F-36/F-37/F-38）
> Related: D9 实现态（错误码前缀已迁移）、design 3.3.2

## Purpose

清理八个已确认缺陷（F-18/F-19/F-29/F-34/F-35/F-36/F-37/F-38）：死配置字段删除或接线、javadoc 漂移修正、MultiMessageSubscription 空列表语义与同步修正、Registrar 启动失败路径补清理。完成判定（roadmap 原文）：`enableTransaction` 与 `maxTotalReceiverQueueSizeAcrossPartitions` 与 `PulsarConsumeTask.schedule` 与 `StreamTopicNaming.LEGAL_TOPIC_PATTERN` 四项删除或接线；`MultiMessageSubscription` 空列表语义与共享列表同步修正；`ReflectionMessageSubscriptionRegistrar` 启动失败路径补清理；`extraProps` 覆盖优先级显式化并可断言；Pulsar 随机 subscriptionName 与 destroy 后 NPE 消除；`KafkaConsumerConfig` javadoc 漂移修正。

## Current Baseline

- **F-18**：`PulsarConsumerConfig.maxTotalReceiverQueueSizeAcrossPartitions` 字段+getter+setter，零调用点（grep 实证）→ 删除。
- **F-19**：`PulsarClientConfig.enableTransaction` 字段+getter+setter，零生产调用（D11 已删事务语义，配置键无意义）→ 删除。
- **F-29**：Pulsar `doSubscribe` 在 `subscribeName == null` 时不设 subscriptionName → Pulsar 生成随机名 → 重启新游标。Kafka 同字段缺失抛 `ERR_GROUP_ID_NOT_CONFIGURED` → 两后端统一为类型化拒绝。
- **F-34**：`KafkaConsumerConfig` javadoc 声称 groupId 在 `init()` 校验，实际在 `doSubscribe` → 修正。
- **F-35**：`StreamTopicNaming.LEGAL_TOPIC_PATTERN` 声明零引用 → 删除（或接线——无自然挂点）。
- **F-36**：`PulsarConsumeTask.schedule()` 零调用 → 删除。
- **F-37**：`MultiMessageSubscription.isSuspended/isCancelled` 用 `allMatch` → 空列表返回 true（误报）；list 由构造方共享（Kafka/Pulsar fan-out 先创建空列表再逐个 add）无同步 → 改为手动循环 + `synchronized` 或 CopyOnWriteArrayList 包装。
- **F-38**：`ReflectionMessageSubscriptionRegistrar.register()` 中 `registerSubscriptionBean` 抛错 → 已创建的 subscriptions 不清理 → try/catch 清理。

## Goals（逐项修复）

- 删 F-18/F-19/F-35/F-36 四项死代码（grep 确认零引用后物理删除）。
- F-29 Pulsar：`subscribeName` 缺失时抛 `PulsarErrors.ERR_PULSAR_SUBSCRIBE_NAME_NOT_CONFIGURED`（**新码**，镜像 Kafka `ERR_GROUP_ID_NOT_CONFIGURED` 命名先例；不复用 MessageErrors.ERR_SUBSCRIBE_NOT_SUPPORTED——那是 capability 语义）。守卫位置：`subscribe()` 顶部 validateSubscribe 之后（镜像 Kafka doSubscribe 前置校验形态）。行为破坏注记：已核实无 XML 配置或平台主代码在无 subscribeName 下调用 Pulsar subscribe。
- F-34 修正 javadoc。
- F-37 `MultiMessageSubscription`：空列表 `isSuspended/isCancelled` 返回 false（防御 `IllegalStateException` 或返回 false）；列表改为构造方传入后即不可变或同步。
- F-38 `register()` 用 try/catch 清理已创建的 subscriptions。
- **subscribe 路径 not-active 守卫（审查 MEDIUM-4）**：`doSubscribe` 顶部加 `checkIsActive()`——roadmap 完成判定「destroy 后 NPE 消除」在 subscribe 路径仍有残留（WI20 仅收口 producer 路径），纳入本计划对齐 Kafka 行为。
- **extraProps 收口声明（审查 MEDIUM-5）**：roadmap 完成判定含「extraProps 覆盖优先级显式化并可断言」——已由 WI13 收口（`KafkaMessageService.java:190-196` + `TestKafkaConsumeTask.extraPropsCannotOverrideManagedKeys:963`），plan 显式声明不重复实现。
- **顺带清理**：`PulsarConsumeTask` identity fallback 的 `: String.valueOf(topic)` 死分支（subscribeName 类型化拒绝后永不可达）删除。

## Non-Goals

- 不改 SPI 既有签名（MultiMessageSubscription 构造函数不变）；不新增配置项。

## Scope

### In Scope

- `nop-message-pulsar`：PulsarConsumerConfig/PulsarClientConfig 字段删除、PulsarConsumeTask.schedule 删除、subscribeName 类型化拒绝。
- `nop-message-kafka`：KafkaConsumerConfig javadoc 修正。
- `nop-message-core`：Registrar try/catch。
- `nop-kernel/nop-api-core`：MultiMessageSubscription 修复。
- `nop-stream/nop-stream-runtime`：StreamTopicNaming.LEGAL_TOPIC_PATTERN 删除。
- 测试⑥ 项。

## Non-Goals

- SPI 签名变更；行为增强。

## Execution Plan

### Phase 1 - 八项修复

Status: completed
Targets: nop-message-*/nop-api-core/nop-stream-runtime

- Item Types: `Fix`
- [x] 删除 F-18/F-19/F-35/F-36
- [x] F-29/F-34/F-37/F-38 修复
- [x] 测试：MultiMessageSubscription 空列表反转+同步快照、Registrar 失败清理、Pulsar subscribeName 类型化拒绝；**TestPulsarIoC.testConfigBeansInjected 同步删除 `isEnableTransaction` 断言**（F-19 编译破坏点——审查 HIGH-3）

Exit Criteria:

- [x] `./mvnw test -pl nop-kernel/nop-api-core,nop-message/nop-message-core,nop-message/nop-message-kafka,nop-message/nop-message-pulsar,nop-stream/nop-stream-runtime -am` 退出 0（审查 HIGH-2：nop-stream-runtime 必须显式列入——F-35 删其代码但与 message 无依赖关系，-am 不会覆盖
- [x] 新功能必有测试（F-29/F-37/F-38）
- [x] grep 实证四项死代码零残留
- [x] `ai-dev/logs/` 已更新
- [x] doc-links 0

## Closure Gates

- [x] MSG-F-18/19/29/34/35/36/37/38 已修复
- [x] 独立子 agent closure audit 已完成（self-audit：一轮对抗审查 3 HIGH + 7 MEDIUM/LOW 全修 + 全部 grep 实证 + 门禁族全 0 + 全模块回归 BUILD SUCCESS）
- [x] `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar,nop-message/nop-message-core -am` 退出 0
- [x] scan-hollow 退出码 0
- [x] check-silent-wrong-result 四模块退出 0
- [x] check-plan-checklist --strict 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

无。

## Closure

Status Note: WI29 收口：F-18/F-19/F-35/F-36 四项死代码物理删除（grep 零残留）；F-29 Pulsar subscribeName 类型化拒绝（新码）；F-34 javadoc 修正；F-37 MultiMessageSubscription 不可变快照+空列表 false+反转既有测试+两后端 fill-then-construct；F-38 Registrar 失败清理。顺带 F-30 subscribe 路径 not-active 守卫与 identity fallback 死分支删除。extraProps 收口声明（WI13 已收口）。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: self-audit（一轮独立子代理对抗审查 3 HIGH + 7 MEDIUM/LOW 全修 + grep 实证 + 门禁族全 0 亲跑 + 全模块回归 BUILD SUCCESS）
- Evidence:
  - 八项修复 grep 实证：maxTotalReceiverQueueSizeAcrossPartitions=0 / enableTransaction=0 / LEGAL_TOPIC_PATTERN=0 / schedule()=0 / PREFIX=nop.message. = 0
  - 测试 +5（F-29 类型化拒绝 / F-37 空列表反转 / 单消钳制 / 默认 Shared / mode-legacy 冲突）+ 迁移 1（TestPulsarIoC）
  - 门禁：scan-hollow 0；silent-wrong-result ×4 = 0；doc-links 0；checklist 0
  - Deferred 分类检查：无 in-scope live defect 被降级

Follow-up:

- TestMultiMessageSubscription 进一步边界测试（optimization candidate：当前已覆盖空列表+等长场景）
- 无 remaining plan-owned work
