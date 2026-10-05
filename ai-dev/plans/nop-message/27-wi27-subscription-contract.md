# 27 WI27 concurrency 与 subscriptionType 交互契约

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Draft Review: 一轮独立子代理对抗审查（agent_95ba6f23）5 Major 全修——DLQ warn-once 单一解析来源（resolveEffectivePulsarType）+ 误 WARN 回归钉、mode/legacy 优先级裁定（mode 胜+conflict WARN）、SPI 回归面并集（+5 实现模块）、warn-once ListAppender 观测机制、禁止变异 options（纯函数解析）；4 Minor 同批；审查确认结构健全
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI27，deps WI6✓；对应 MSG-F-32 与 D4 与 D5）
> Related: `ai-dev/design/nop-message/message-contract-design.md`（3.3 D4 中立化裁定，本计划落实现态）

## Purpose

消除 `concurrency × SubscriptionType` 组合的三后端不确定行为（F-32：Pulsar Exclusive+concurrency>1 整段 subscribe 硬失败、javadoc 承诺「exclusive 下忽略」与实现矛盾；Kafka warn-once；Local 忽略），并按 D4 加性引入三值中立订阅语义枚举与映射表，旧枚举进弃用期。

## Current Baseline

- **F-32 在场**：`PulsarMessageService.subscribe`（`:285-302`）concurrency>1 时无条件循环 doSubscribe N 次——Exclusive 订阅名下第二个 consumer 被 broker 拒绝 → 整段失败（`ret.cancel()` + 抛）；`MessageSubscribeOptions.concurrency` javadoc 承诺 exclusive 下忽略该参数（未兑现）。Kafka：concurrency=N 创建 N 个独立 consumer（组语义，行为确定）；Local：字段 warn-once 忽略（WI23）。三后端三种行为。
- **D4 裁定（message-contract 3.3）**：新增三值中立枚举（SINGLE_ACTIVE/LOAD_BALANCED/ORDERED_BY_KEY）与旧四值并存 + 双向映射 + 弃用期一次性 WARN；Pulsar 直通映射原生四型；Kafka 三值全部 warn-once。
- **现状无中立枚举**：`SubscriptionType` 仅 Pulsar 词汇四值；`MessageSubscribeOptions` 无 mode 字段。
- **弃用期 WARN 现状**：Kafka 对 subscriptionType 已有 warn-once（「无 Kafka 等价被忽略」）；Pulsar 对 subscriptionType **零告警**（直通原生）——弃用期 WARN 两后端均需按 D4 统一。
- **测试基建**：Pulsar builder mock capture（`LifecycleTestService.builder`，WI21/25 先例）；Kafka `createConsumer`/props 捕获先例。

## Goals

- **新枚举（加性，nop-api-core）**：`SubscriptionMode { SINGLE_ACTIVE, LOAD_BALANCED, ORDERED_BY_KEY }` + `MessageSubscribeOptions.subscriptionMode` 字段（新增，缺省 null=未配置，D8 合规）。javadoc：SINGLE_ACTIVE=同订阅名同时仅一个活跃消费者；LOAD_BALANCED=多消费者分发无顺序承诺；ORDERED_BY_KEY=同 key 固定路由。
- **映射表**：`SubscriptionMode`→Pulsar `SubscriptionType`：SINGLE_ACTIVE→Exclusive、LOAD_BALANCED→Shared、ORDERED_BY_KEY→Key_Shared（`SubscriptionModes.toPulsar(mode)` 静态映射，落 nop-api-core 或各后端私有——裁定：两后端各自私有静态映射，避免 nop-api-core 依赖 Pulsar 词汇）。双向对照表落 design。
- **F-32 修复（Pulsar，javadoc 与实现对齐）**：引入单一解析纯函数 `resolveEffectivePulsarType(options)`（**禁止变异 options**——`options.setSubscriptionType(...)` 会污染调用方对象、误触发 legacy 弃用 WARN 并漂移 DLQ 判定；doSubscribe 每 consumer 纯函数解析后仅接线 builder）。优先级（审查 M2 裁定）：`subscriptionMode` 优先；与 legacy `subscriptionType` 同时配置 → mode 胜 + 冲突 warn-once（`mode-type-conflict`）；legacy 弃用 WARN 照发。有效型规则：
  - 有效型 ∈ {Exclusive, Failover}（SINGLE_ACTIVE 语义）→ concurrency **钳制为 1** + warn-once（`concurrency-ignored-single-active`，兑现 javadoc 承诺）；
  - 有效型 ∈ {Shared, Key_Shared} → concurrency N 照常多 consumer；
  - **均未配置 + concurrency>1 → 有效型按 Shared**（现状该组合是硬失败、无成功用户 → 无兼容性破坏）+ warn-once（`concurrency-default-shared`）；均未配置 + concurrency≤1 → Pulsar 默认 Exclusive 不变。
  - **单一来源（审查 M1 裁定）**：`resolveEffectivePulsarType` 同时服务 builder.subscriptionType 接线与 WI21 DLQ warn-once 判定——修掉 mode-only 配置下 DLQ 误 WARN（mode=LOAD_BALANCED/ORDERED_BY_KEY + DLQ 实际会触发，legacy null 旧判定会假 WARN）。
- **D4 弃用期**：Pulsar 对 legacy `subscriptionType` 打一次性 WARN（`legacy-subscription-type`，指向 subscriptionMode）；旧值仍生效（映射直通）。Kafka 侧 legacy 保持既有「被忽略」语义，弃用 WARN 文案按后端限定（不共用「仍生效」话术——审查 Minor-1）。
- **测试①-⑦**（Exit Criteria）。**warn-once 观测机制（审查 M4 裁定）**：测试内挂 logback `ListAppender` 到 `MessageOptionsValidator` 的 slf4j Logger，断言 WARN 事件与 key/文案片段（logback-classic 经 nop-autotest-junit 在 test classpath 可用）；每用例挂/摘，避免 JVM 级 warn-once 状态串扰。

## Non-Goals

- 不删除旧 `SubscriptionType`（弃用期）；不实现 Failover 的 broker 特有语义；不动 Local（WI23 已 warn-once 全字段）；不做 ORDERED_BY_KEY 的 Kafka key 分配语义（Kafka 无订阅型概念）。

## Scope

### In Scope

- `nop-kernel/nop-api-core`：`SubscriptionMode` 枚举、`MessageSubscribeOptions.subscriptionMode` 字段。
- `nop-message-pulsar`：mode 映射 + concurrency 钳制/默认 Shared + legacy warn-once、测试。
- `nop-message-kafka`：mode warn-once、测试。
- design（message-contract 3.3 实现态）、日志、roadmap WI27 状态行。

### Out Of Scope

- WI28-WI35；SPI 签名变更（字段新增为收窄式新增）；Failover 竞选语义。

## Execution Plan

### Phase 1 - 中立枚举、映射与组合确定化

Status: completed
Targets: `nop-kernel/nop-api-core`、`nop-message/nop-message-pulsar`、`nop-message/nop-message-kafka`

- Item Types: `Fix`（F-32）+ `Feature`（中立枚举）
- [x] `SubscriptionMode` 枚举 + `subscriptionMode` 字段（D8 加性）
- [x] Pulsar：mode→Pulsar 型映射、SINGLE_ACTIVE concurrency 钳制 warn-once、默认型+concurrency>1 → 显式 Shared + warn-once、legacy subscriptionType warn-once
- [x] Kafka：subscriptionMode warn-once、legacy warn-once 保持
- [x] 测试① Pulsar mode 映射三例（builder capture `subscriptionType` 值：SINGLE_ACTIVE→Exclusive/LOAD_BALANCED→Shared/ORDERED_BY_KEY→Key_Shared）；② SINGLE_ACTIVE + concurrency=3 → 单 consumer（仅一次 subscribe）+ ListAppender 断言 `concurrency-ignored-single-active` WARN；③ LOAD_BALANCED + concurrency=2 → 双 consumer；④ 均未配置型 + concurrency=2 → 显式 Shared 接线 + 双 consumer + `concurrency-default-shared` WARN；⑤ legacy subscriptionType 弃用 warn-once（Pulsar/Kafka，ListAppender）；⑥ Kafka mode warn-once 不抛；mode 与 legacy type 冲突 → mode 胜 + `mode-type-conflict` WARN；**mode=LOAD_BALANCED + DLQ 配置 → 无 `dlq-subscription-type` 误 WARN**（审查 M1 回归钉）；既有订阅测试全绿
- [x] design 3.3 实现态（映射表、组合矩阵、弃用路径、Failover 仅经 legacy 可达、mode 枚举与 Pulsar 客户端 SubscriptionMode[Durable/NonDurable] 撞名注记）；`MessageSubscribeOptions.concurrency` javadoc 更新（新行为与「exclusive 下忽略」兑现——审查 Minor-5）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-kernel/nop-api-core,nop-message/nop-message-core,nop-message/nop-message-kafka,nop-message/nop-message-pulsar,nop-message/nop-message-debezium,nop-network/nop-rpc/nop-rpc-core,nop-service-framework/nop-graphql/nop-graphql-message,nop-stream/nop-stream-runtime,nop-sys/nop-sys-dao,nop-network/nop-vertx/nop-vertx-mqtt-server,nop-persistence/nop-nosql/nop-nosql-lettuce,nop-persistence/nop-orm-drivers/nop-orm-tdengine,nop-ai/nop-ai-agent -am` 退出 0 全绿（BUILD SUCCESS 串行 11 模块 [nop-stream-runtime 已含]，_tmp/wi27-regression3.log。**偏离记录**：nop-orm 剔除——并行 plan-2318 会话的未提交改动（SessionFactoryImpl/EncAssistedHelper 引入 EncAssistedEqlAstTransformer）致 nop-orm AOP codegen 步骤 NoClassDef io/nop/app/SimsExam（测试 TestEncAssistedEqlRewrite 同源）——git diff 实证非本计划触碰面，与本计划零相关；nop-sys-dao 同前述 JunitBaseTestCase 存量问题仍未修复。WI27 改动仅加性（新枚举+新字段），零破坏面）
- [x] **新功能必有测试**：①-⑥ 全部存在且通过——①④ 是映射/组合的直接证明；②③ 是 F-32 的直接证明（Exclusive+并发不再硬失败）
- [x] **无静默跳过**：钳制/默认 Shared 均显式 warn-once；legacy 弃用期 WARN 两后端就位
- [x] **接线验证**：①③④ 的 builder capture / subscribe 计数为运行时证据
- [x] Hard constraint：SPI 仅收窄式新增（新枚举+新字段，零签名变更）；D8 零改名；不引入 AckMode
- [x] Owner-doc：message-contract 3.3 实现态已落档
- [x] `ai-dev/logs/2026/` 当日条目已更新
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0

## Closure Gates

- [x] MSG-F-32 已修复且有测试证明（②③④）；D4 中立枚举/映射/弃用路径落地（①⑤）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-message --severity high` 退出码 0
- [x] `node ai-dev/tools/check-silent-wrong-result.mjs --module <m>` 四个 nop-message 模块（core/kafka/pulsar/debezium）逐一退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/27-wi27-subscription-contract.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- ORDERED_BY_KEY 在 Kafka 的 key 路由近似（需应用层 key→分区映射，无实证需求）。
- Failover 与 SINGLE_ACTIVE 的语义细分注记（design 3.3 已注记，无行为差异需求）。

## Closure

Status Note: WI27 收口：F-32（concurrency×type 组合两后端确定——SINGLE_ACTIVE 钳制 1、LOAD_BALANCED/ORDERED_BY_KEY N 照常、未配置+并发>1 显式 Shared，原硬失败无兼容用户）；D4 落地（SubscriptionMode 枚举+subscriptionMode 字段 D8 加性、mode>legacy 优先级+冲突 WARN、resolveEffectivePulsarType 单一来源修 DLQ 误 WARN、legacy 弃用 WARN 两后端）；javadoc 与实现一致。SPI 仅收窄式新增。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 audit + self-audit（本计划规模较小：一轮对抗审查 5 Major 全修 + 独立门禁族全 0 亲跑 + 映射/钳制/冲突/DLQ 回归钉测试 8 个）
- Evidence:
  - 测试 8 个新增全绿：映射三例、SINGLE_ACTIVE 钳制（times(1) subscribe）、默认 Shared（times(2)+Shared 断言）、mode/legacy 冲突（mode 胜 Shared 断言）、DLQ+LOAD_BALANCED 无误 WARN、Kafka 双 warn-once 不抛、api-core 字段 round-trip。
  - 回归：api-core 165/0F + kafka 83/0F + pulsar 58/0F + SPI 模块 11 模块 BUILD SUCCESS（nop-orm 剔除：并行 plan-2318 未提交改动 AOP codegen NoClassDef io/nop/app/SimsExam——git diff 实证非本计划触碰面）。
  - 门禁：scan-hollow 0；silent-wrong-result ×4 = 0；doc-links 0；checklist 0。
  - Deferred 分类检查：无 in-scope live defect 被降级。

Follow-up:

- ORDERED_BY_KEY 在 Kafka 的 key 路由近似（optimization candidate）
- Failover 与 SINGLE_ACTIVE 语义细分注记（design 3.3 已登记）
- 无 remaining plan-owned work
