# 21 WI21 分层 DLQ 与失败终点

> Plan Status: completed
> Draft Review: 两轮独立子代理对抗审查通过（R1：agent_16ebe6c1，3 Major——Pulsar 2.8 ack 触发语义证伪重述/桥契约写死/deadLetterTopic 取数路径，9 Minor 全修；R2：agent_f466416a，2 Minor+1 Info 文字修正后判定可实施）
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI21，deps WI16✓；对应 MSG-F-22 与 MSG-F-53；D6 三层裁定）
> Related: `ai-dev/design/nop-message/backend-semantics-design.md`（3.3 节 D6，本计划增补 3.3.1 实现口径）、`nop-job/nop-job-retry-adapter`（平台桥先例）、`nop-retry-api`（IRetryEngine/IRetryTask）

## Purpose

按 D6 三层裁定交付 DLQ 与失败终点机制（F-22：当前无 DLQ、无重试上限、无失败终点语义）：第二层后端原生能力接线（Pulsar `DeadLetterPolicy`、Kafka 应用级重发布按需启用）+ 第三层 `nop-retry` 桥消费 WI16 失败回调。不新增无法实现的后端 DLQ 义务（Local/Redis/MQTT/TDengine/DB 仅第一层回调，roadmap Hard constraint）。重试上限语义与跨重启 attempt 归 WI22（本计划交付其所需 substrate）。

## Current Baseline

- **F-22 现状**：两后端无任何 DLQ 实现（grep 全模块无 deadLetter/dead-letter 命中）；`TerminateConsume`（D5(b')，WI6 落地）当前语义 = ack 后丢弃（Kafka 单条 `KafkaConsumeTask.java:334-338`/批量 `:466-468`，Pulsar 单条 `PulsarConsumeTask.java:282-286`/批量 `:226-230`，均 WARN 留证后 ack/commit）——失败终点只有「丢弃」一种去向。
- **D6 裁定（design 3.3）**：SPI 配置不出现 `deadLetterTopic`/`maxRetries` 字段；Pulsar 接线原生 `deadLetterPolicy`+`maxDeliveries` 进 consumer 配置；Kafka 无原生 DLQ，提供应用级重发布按需启用；`nop-retry` 桥消费第一层失败回调，禁止消息层自建重试机制。
- **WI16 substrate**：`IMessageConsumer.onConsumeFailure(ConsumeFailure)`（topic/location/message/attempt/throwable）已是框架唯一失败派发点；attempt 计数（同 location 连续失败、成功清零）已在两后端 runTask 落地——WI22 重试上限的直接输入。
- **Pulsar 2.8 原生 API（javap + 反编译实测）**：`DeadLetterPolicy.builder().maxRedeliverCount(n).deadLetterTopic(t).retryLetterTopic(t).build()`；`ConsumerBuilder.deadLetterPolicy(...)` 存在；`deadLetterTopic` 留空时客户端派生 `%s-%s-DLQ`。**关键语义（2.8.0 ConsumerImpl 反编译证实）**：DLQ 分流由 **ack 触发**（`doAcknowledge` 查追踪表改道），重投计数仅在接收侧追踪；`redeliverUnacknowledgedMessages` 仅 Shared/Key_Shared 携带计数、非 Shared 恒 0。**因此当前失败路径只 nack 的消费任务下，「持续抛异常的毒消息」不会触发 DLQ（生效依赖 WI22 的 attempt 上限/终结 ack 语义）**；默认 Exclusive 订阅下计数恒 0 亦不触发。本计划交付「接线 + 准确语义文档 + 配置面 warn-once」，真实投递效果归 WI33 gated E2E。`PulsarConsumerConfig` 现有 3 字段。
- **Kafka deadLetterTopic 取数路径**：`KafkaMessageService.defaultConsumerConfig` 为 private——新增包私有访问器 `resolveDeadLetterTopic(String topic)`（读配置，null=off），task 经持有的 `service` 引用调用；`TestKafkaConsumeTask` 的 mock service 默认返回 null → DLQ off → 既有 terminate 测试零回退。
- **存量交互（记录不修）**：2.8 `doReconsumeLater` 无条件解引用 `deadLetterPolicy.getRetryLetterTopic()`——未配 DLQ 时 ConsumeLater 每次触发客户端内部 NPE→ERROR 噪音→原地重投（存量缺陷，登记 roadmap Follow-up；本计划接线后噪音以 topic:null 形态延续，直至 retryLetterTopic 语义或 Pulsar 升级处理）。
- **平台桥先例**：`NopRetryJobRetryBridge`（nop-job-retry-adapter）——子系统事件 → `IRetryEngine.newRetryTask(serviceName, serviceMethod).withPolicyId/withIdempotentId/withNamespaceId/withGroupId` → `task.callAsync(request, null)`；engine 缺失时 WARN 不抛。消息桥按同形态落 `nop-message-core`。
- **模块图**：`nop-message-core` 现不依赖 `nop-retry-api`。本计划显式声明新增 `nop-retry-api` 依赖（api-only：IRetryEngine/IRetryTask/IRpcCall 均落 `nop-api-core` 与 nop-retry-api 自身，无 engine 传递重量）——roadmap Hard constraint「模块图变更须显式声明」的声明项。
- **测试基建**：`TestKafkaConsumeTask` 的任务用 `mock(KafkaMessageService.class)`（`service.sendAsync` 可 stub）；`TestPulsarServiceLifecycle.LifecycleTestService` 有 `newConsumerBuilder` seam（builder mock 可捕获 `deadLetterPolicy` 调用）。

## Goals

- **第二层 Pulsar 原生接线（交付面 = 接线 + 准确语义文档 + 配置面 warn-once）**：`PulsarConsumerConfig` 新增 `deadLetterMaxRedeliverCount`（int，默认 0=off）与 `deadLetterTopic`（String，默认 null=客户端 `%s-%s-DLQ` 派生）。`doSubscribe` 在 `deadLetterMaxRedeliverCount > 0` 时 `builder.deadLetterPolicy(DeadLetterPolicy.builder().maxRedeliverCount(n)[.deadLetterTopic(t)].build())`；且订阅类型非 Shared/Key_Shared 时打一次性 WARN（2.8 非 Shared 重投计数恒 0，DLQ 不触发——D-2 裁定）。**语义文档如实声明（D-1 裁定，反编译证据）**：2.8 的 DLQ 分流由 ack 触发、计数在接收侧追踪；当前消费任务失败路径只 nack，故「持续抛异常的毒消息」不触发 DLQ——Pulsar DLQ 的实际生效依赖 WI22 的 attempt 上限与终结 ack 语义；真机投递验证归 WI33 gated E2E（其 charter 已含 DLQ 投递场景）。
- **第二层 Kafka 应用级重发布（按需启用）**：`KafkaConsumerConfig` 新增 `deadLetterTopic`（String，默认 null=off）；`KafkaMessageService` 新增包私有 `resolveDeadLetterTopic(String topic)` 访问器（null=off），task 经 `service` 引用调用。配置后，`TerminateConsume` 失败终点从「ack 后丢弃」变为「重发布到 DLQ topic 后 ack」——单条与批量两路径同语义；重发布经 `service.sendAsync(dlqTopic, apiMessage, null)` + `FutureHelper.syncGet`（DLQ 写入成功先于 commit，保 at-least-once）。**裁定（审查 A-1/A-2/A-3）**：发送失败透传 adapted 异常（不加新错误码，避免膨胀），由既有 catch 走失败路径重投；逐条 sync 发送计入 `max.poll.interval.ms` 预算（javadoc 注明）；`dlqTopic.equals(record.topic())` 自环守卫——命中则跳过重发布按丢弃留证处理（防无限自环）。未配置时行为不变（WARN 留证后丢弃）。
- **第三层 nop-retry 桥（契约写死——审查 F 裁定）**：`nop-message-core` 新增 `MessageConsumeFailureBridge`（形态对齐 `NopRetryJobRetryBridge`）。**桥 = 「失败事件 → 部署声明的重放入口」的转发器，不是消息重放机制**：`IRetryEngine` 与重放目标（serviceName/serviceMethod）均为注入（setter），任一缺失 → WARN 不抛；`submit(ConsumeFailure)` 构造 `IRetryTask`：`ApiRequest.data` = failure.getMessage()（ApiMessage 原样），topic/location/attempt/throwable 经 request headers 携带（ApiRequest 元数据字段），`withIdempotentId(location)`、`withGroupId(topic)`；policyId 仅在部署显式提供时设置（缺省不调用 withPolicyId → 引擎默认策略）。callAsync 结果 WARN 级留证。对未声明重放目标的部署，第三层零行为。桥不实现任何重试策略——满足「禁止消息层自建重试机制」。部署用法：消费者覆写 `onConsumeFailure` 调 `bridge.submit(failure)`（仅对具备消息重放 service 的部署有意义，owner doc WI34 给模板与前提）。
- **无 DLQ substrate 的后端义务不变**：Local/Redis/MQTT/TDengine/DB 不新增任何 DLQ 字段或行为（Hard constraint「不新增无法实现的后端 DLQ 义务」）。
- **顺手微修（审查 J-1，失败回调同域）**：`dispatchConsumeFailure` 读取并清空 `lastFailureTopic` 与 location/message 一致——消除框架级失败带出上一条旧 topic 的串味。
- **测试**（Exit Criteria ①-⑤）。

## Non-Goals

- 不做重试上限/attempt 跨重启语义（WI22）；Pulsar 的 `TerminateConsume` 保持 ack 后丢弃（其 DLQ 走原生 deadLetterPolicy 路径：nack 重投循环在接收侧累积计数、终结 ack 时分流——与 terminate 直接 ack 的语义分轨，design 3.3.1 记录该裁定）。
- 不改 SPI（`nop-api-core` 零变更）；不引入 AckMode；不在 SPI 选项出现 DLQ 字段。
- 不实现 retryLetterTopic 高阶语义（2.8 的 retry letter 主题流）——仅透传 `deadLetterTopic` 覆盖；retryLetterTopic 保持 Pulsar 默认。
- 不创建独立 adapter 模块（nop-job-retry-adapter 形态的模块化留给后续需要时；桥类落 nop-message-core 足够，BOM 归 WI31）。

## Scope

### In Scope

- `nop-message-pulsar`：`PulsarConsumerConfig` 两字段 + `doSubscribe` 接线 + wiring 测试 + `dispatchConsumeFailure` topic 清理微修。
- `nop-message-kafka`：`KafkaConsumerConfig.deadLetterTopic` + 单条/批量 TerminateConsume 重发布 + `dispatchConsumeFailure` topic 清理微修 + 测试。
- `nop-message-core`：`MessageConsumeFailureBridge` + pom 增 `nop-retry-api` + 单测。
- design 3.3.1、日志、roadmap WI21 状态行。

### Out Of Scope

- WI22（attempt 上限）、WI25（批量契约）、SPI 变更、Pulsar TerminateConsume→DLQ 重发布、retryLetterTopic 高阶语义、gated 真机 DLQ E2E（WI33）。

## Execution Plan

### Phase 1 - 三层 DLQ substrate

Status: completed
Targets: `nop-message/nop-message-core`、`nop-message/nop-message-kafka`、`nop-message/nop-message-pulsar`

- Item Types: `Feature`（F-22 的 DLQ 机制面新增）；其中 lastFailureTopic 微修为 `Fix`（已确认 live defect）；测试归类 `Proof`

- [x] （`Fix` 子项，两后端）`dispatchConsumeFailure` 的 `lastFailureTopic` 随 location/message 一并读取清空——消除框架级失败带出上一条旧 topic
- [x] Pulsar：`PulsarConsumerConfig` 增 `deadLetterMaxRedeliverCount`/`deadLetterTopic` + `doSubscribe` 接线 `DeadLetterPolicy` + 非 Shared 订阅一次性 WARN
- [x] Kafka：`KafkaConsumerConfig` 增 `deadLetterTopic` + `resolveDeadLetterTopic` 包私有访问器 + 单条/批量 TerminateConsume 重发布（syncGet、失败透传走既有失败路径、自环守卫）
- [x] `nop-message-core`：pom 增 `nop-retry-api`（`<version>${project.version}</version>`，BOM 登记归 WI31）；`MessageConsumeFailureBridge`（engine/重放目标缺失 WARN 不抛；idempotentId=location、groupId=topic、policyId 仅显式提供时设置）
- [x] 测试① Pulsar wiring：配置后 `builder.deadLetterPolicy` 被调用且 maxRedeliverCount/topic 正确；未配置不调用
- [x] 测试② Kafka 单条 TerminateConsume + DLQ 配置 → sendAsync(dlqTopic, 同值消息) 且 commit 继续；DLQ 发送失败 → 透传异常走失败路径（重投）；DLQ 自环（dlqTopic==record.topic）→ 不重发布按丢弃处理
- [x] 测试③ Kafka 批量 TerminateConsume + DLQ → 同②；未配置 DLQ → 行为与现状一致（无 sendAsync 调用）
- [x] 测试④ 桥：mock IRetryEngine 捕获 IRetryTask 断言 serviceName/serviceMethod/idempotentId/groupId；engine 为 null 时 WARN 不抛
- [x] 测试⑤ 既有全量回归（terminate 现状语义测试不回退）
- [x] design 3.3.1 落档（三层 substrate 实现口径、Pulsar 2.8 ack 触发语义与毒消息不触发事实、非 Shared warn-once、桥转发器契约、DLQ 发送失败受 WI22 上限治理声明、commitSync 失败 DLQ 重复的 at-least-once 残留、reconsumeLater retryLetterTopic 存量噪音记录）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-message/nop-message-core,nop-message/nop-message-kafka,nop-message/nop-message-pulsar,nop-message/nop-message-debezium -am -T 1C` 退出 0 全绿（BUILD SUCCESS：kafka 67/0F、pulsar 42/0F、core 21(1skip)，_tmp/wi21-regression.log）
- [x] **新功能必有测试**：①-④ 全部存在且通过（②③ 是 Kafka 重发布的直接证明；① 是 Pulsar 接线的直接证明；④ 是桥的直接证明）
- [x] **无静默跳过**：DLQ 发送失败显式抛出走既有失败路径（保 at-least-once），不吞异常；桥 engine 缺失 WARN 留证（对齐 job 桥先例），非静默 no-op；Pulsar 未配置时零行为变化
- [x] **端到端验证（机制面）**：Kafka 路径从 handler 返回 TerminateConsume → DLQ 重发布 → commit 的完整链路有测试覆盖（mock broker 面内的端到端；真机 DLQ 投递归 WI33 gated）
- [x] **接线验证**：Pulsar `DeadLetterPolicy` 经 `ConsumerBuilder.deadLetterPolicy` 真实传给 builder（mock 捕获断言）；桥经 `IRetryEngine.newRetryTask` 真实产出任务（mock 捕获断言）
- [x] Hard constraint 核对：SPI（nop-api-core）零变更；SPI 选项无 DLQ 字段（DLQ 配置全部在模块级 config bean）；Local/Redis/MQTT/TDengine/DB 零改动
- [x] Owner-doc：design 3.3.1 已落档
- [x] `ai-dev/logs/2026/` 当日条目已更新
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0（当前存量 0 errors、4 个无关 warnings；如回归以「errors 不增加」为判据）

## Closure Gates

- [x] MSG-F-22 的机制面（D6 三层 substrate）已交付且有测试证明（①-④）；WI22 所需 substrate（attempt 计数已在 WI16 + DLQ 去向）就位
- [x] 不新增无法实现的后端 DLQ 义务（grep 证明五个无 substrate 后端零改动；git status 双证）
- [x] 独立子 agent closure audit 已完成并记录证据（agent_09c3159e，PASS 附 F1/F2 条件——已修复，见 Evidence）
- [x] `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am` 退出 0（审计亲跑超集命令 EXIT=0）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-message --severity high` 退出码 0
- [x] `node ai-dev/tools/check-silent-wrong-result.mjs --module <m>` 四个 nop-message 模块逐一退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/21-wi21-layered-dlq.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- Pulsar retryLetterTopic 高阶语义与 ConsumeLater 重试主题流（optimization candidate：2.8 语义复杂，无实证需求）。
- 存量缺陷登记：2.8 `doReconsumeLater` 无条件解引用 retryLetterTopic → 未配 DLQ 时 ConsumeLater 每次触发客户端内部 NPE ERROR 噪音（`PulsarConsumeTask` 的 reconsumeLater 路径）——登记 roadmap Follow-up 或随 Pulsar 升级处理（本计划不修，3.3.1 记录交互）。
- 独立 `nop-message-retry-adapter` 模块化（out-of-scope improvement：桥类在 nop-message-core 足够，模块化等第二个消费场景出现）。

## Closure

Status Note: WI21 收口：D6 三层 substrate 全部落地——Pulsar 原生 DeadLetterPolicy 接线（含 2.8 ack 触发语义的如实声明与非 Shared warn-once）、Kafka 应用级重发布按需启用（自环守卫 + syncGet 保 at-least-once）、nop-retry 桥转发器（契约写死、双缺失零行为）。SPI 零变更、五个无 substrate 后端零改动。独立 closure audit PASS（附条件 F1/F2 均已修复：自环守卫直接单测补齐、design 3.3.1 补 reconsumeLater 噪音记录行）。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_09c3159e-aeeb-4c0d-a706-c592ced36994（fresh session，亲跑全部验证命令）
- Evidence:
  - 9 条 Exit Criteria：8 PASS + ②条件性 PASS——审计条件 F1（自环测试缺失 vs 勾选声明）已修复：新增 resolveDeadLetterTopicDisablesSelfLoopAndUnconfiguredIsNull（null-config→null、配置→dlq、自环→null 三断言）；F2（design 3.3.1 缺 reconsumeLater 噪音记录行）已补。
  - Hard constraint：nop-api-core 零文件变更（git diff 实证）；SPI 无 DLQ 字段；五个无 substrate 后端不在触碰面（git status 双证）。
  - Anti-Hollow：Kafka terminate→resolve→sendAsync→syncGet→commit 链路连通且有失败注入测试（never commitSync + seek 断言）；Pulsar captor 捕获真实 policy；桥 mock 捕获 newRetryTask/callAsync 全参数。
  - 语义如实性 PASS：javadoc/design 无「DLQ 已可用」夸大，2.8 三推论 + 分轨裁定如实。
  - 门禁：四模块回归 EXIT=0（kafka 68/0F 补测后、pulsar 42/0F、core 21）；scan-hollow 0；silent-wrong-result ×4 = 0；doc-links 0 errors。
  - Deferred 分类检查：无 in-scope live defect 被降级（retryLetterTopic 噪音为存量缺陷，已登记 Follow-up 与 design 3.3.1）。

Follow-up:

- 存量缺陷：Pulsar 2.8 doReconsumeLater 无条件解引用 retryLetterTopic 的 NPE 噪音（已登记 design 3.3.1 与 roadmap Follow-up 区）
- roadmap Hard constraint 中 kafka.version=3.5.0 过时（实际 3.9.2）——docs bug 归 WI34 核对
- Pulsar retryLetterTopic 高阶语义、独立 adapter 模块化（optimization candidates）
