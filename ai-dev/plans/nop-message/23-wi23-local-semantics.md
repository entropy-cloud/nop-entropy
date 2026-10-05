# 23 WI23 LocalMessageService 语义对齐

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Draft Review: 两轮独立子代理对抗审查通过（R1：agent_e3d0753b，1 Blocker——cancel 按身份移除漏改致 F-15 换形态复发，+3 Major+M4+4 Minor 全修；R2：agent_fd42a0fe，N1/N2 文字残留修正后判定通过）
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI23，deps WI4✓；对应 MSG-F-14 残面验证 / MSG-F-15 / MSG-F-56 残面）
> Related: `ai-dev/design/nop-message/backend-semantics-design.md`（本计划增补 3.9 Local 语义节）、`message-contract-design.md` D1 矩阵（Local 行）

## Purpose

对齐 `LocalMessageService` 与 broker 后端的语义边界。完成判定（roadmap 原文）：`ConsumeLater` 不再被发到 ack topic；重复订阅的取消不再交叉影响另一持有者；`subscribe` 对每个选项字段给出显式处置；与两后端语义差异在设计文档中显式登记。

## Current Baseline

- **F-14 已修复且有直接测试（WI4）**：`invokeSubscription` 在分支链拦截 `ConsumeLater` → `scheduleRedelivery`；契约测试 `AbstractMessageServiceContractTest.consumeLaterDefersRedeliveryWithoutAckReply`（经 `LocalMessageServiceContractTest` 对 Local 运行）已断言 ack-topic 无投递 + 延迟重投到达 + payload 一致，对照面 Acknowledge→ack topic 同在。**本计划无 F-14 代码增量**，仅引用该既有测试为证明（审查 M2 勘误）。
- **F-15 在场**：`subscribe` 按 listener 身份去重（`findSubscription` 相同 listener 返回**同一个** Subscription 实例）；`cancel` 按身份移除该唯一实例 → 第二个 subscribe 持有者被静默取消（roadmap 原缺陷形态完整存在）。
- **F-56 大部分已修复（WI6）**：`MessageOptionsValidator.validateSubscribe`（WI6 落地）对全部选项字段给出三态处置——transactional/transactionTimeout warn-once（D11）、batch/seek 族能力门（Local 声明于 WARN_ONCE → warn-once 忽略）、subscribeName/concurrency/subscriptionType 需 SUBSCRIBE 能力（Local 已声明）。**残面**：Local 实际**忽略** subscribeName/concurrency/subscriptionType（in-JVM 广播无这些概念），但三者因 SUBSCRIBE 能力已声明而**静默通过**——D1 矩阵 Local 行「其余逐字段 warn-once」未完全兑现（对齐 Kafka subscriptionType 的 in-place warn-once 先例即可）。
- **契约测试**：`LocalMessageServiceContractTest extends AbstractMessageServiceContractTest`（WI4 骨架）在位且绿。
- **触碰面**：`nop-message-core` 单模块；SPI 零变更；其他后端零改动。

## Goals

- **F-15：订阅独立化（审查 B1 全链裁定）**。三处同改，缺一即换形态复发：
  1. `subscribe` 每次调用创建**独立的** `Subscription` 实例（删除按 listener 身份去重）——同一 listener 重复订阅 = 两条独立订阅（各收各的投递）；取消其一不影响另一持有者。语义注记（3.9 节）：与 broker「每次 subscribe 一个独立订阅」对齐，重复 listener 的重复投递是显式订阅的诚实结果。
  2. `cancel` 改为**按订阅实例身份**移除（`subscriptions.remove(this)`），删除 `removeConsumer`/`findSubscription` 的 consumer 身份查找——原形态在订阅序 [A,B] 先取消 B 时会误删 A（B 留在 list 里继续收消息、A 被静默退订），是 F-15 换形态复发。
  3. `scheduleRedelivery` 的 fire-time map 检查从 `findSubscription(...)` 改为 `subscriptions.contains(subscription)`（实例身份），保住 WI4 per-subscription 重投裁定（兄弟实例不得满足本订阅的存在性检查）。
- **F-56 残面：Local 专属字段处置**。`LocalMessageService.subscribe` 在 validator 之后对 `subscribeName != null` / `concurrency > 1` / `subscriptionType != null` 逐一 `MessageOptionsValidator.warnOnce("local", field, ...)`（每 JVM 一次，对齐 Kafka subscriptionType 先例与 D1 矩阵 Local 行）。如实声明（审查 m1）：`WARNED` 表为 private，warn-once 是否发生不可程序化断言——测试③以「不抛（typed reject 会抛）」+ design 声明为证明面。
- **TerminateConsume 留证（审查 M4，Hard constraint 对齐）**：Local 的 TerminateConsume 分支补 WARN（`nop.message.local.terminate-consume:topic={},reason={}`，对齐两 broker 格式）——无 substrate 后端「丢弃（留证）」要求 WARN，纯 javadoc 留证不满足。
- **F-14 证明面（引用既有，不重复实现）**。既有契约测试钉定：handler 返回 ConsumeLater → ack topic **无**投递（`getConsumers`/send 监听断言）+ 延迟后重投到达该订阅；Acknowledge → ack topic 有投递（对照面）。
- **design 3.9（平级新节，非 3.3.x——审查 M3 节号裁定）：Local 与两 broker 后端的语义差异登记**：in-JVM 广播模型（逐订阅投递 vs Kafka 组内竞争/Pulsar 订阅模式）；ConsumeLater = 定时器 per-subscription 重投（非 broker 重投队列）；suspend/cancel = 内存标志/注销（无 broker 状态）；订阅独立化语义；DLQ/seek/batch 无 substrate（仅第一层回调）；选项字段三态表；`ReflectionMessageSubscriptionRegistrar` 同 listener 订多 topic 不受影响、同 topic 重复声明从静默去重变为重复投递的边缘注记（审查 m4）。

## Non-Goals

- 不改 `scheduleRedelivery` 的 per-subscription 语义（WI4 裁定保持）；不做 Local 的 DLQ/持久化。
- 不动 SPI、validator 框架本身、其他后端。
- 不处理 `redeliveryScheduler` 的关闭生命周期（daemon 线程 + 现状够用；登记 optimization candidate）。

## Scope

### In Scope

- `nop-message/nop-message-core`：`LocalMessageService`（订阅独立化 + 字段 warn-once）、测试（F-14 直接断言 / F-15 独立取消 / 字段处置不抛）、design 3.3.3、日志、roadmap WI23 状态行。

### Out Of Scope

- WI24-WI35；SPI 变更；broker 后端改动。

## Execution Plan

### Phase 1 - 订阅独立化与显式处置

Status: completed
Targets: `nop-message/nop-message-core`

- Item Types: `Fix`（F-15/F-56 残面为已确认缺陷）；测试归类 `Proof`

- [x] `subscribe` 独立化（新建 Subscription）+ `cancel` 按实例身份移除（`removeConsumer`/`findSubscription` 删除）+ `scheduleRedelivery` map 检查改 `contains(subscription)`；javadoc 声明重复 listener = 重复投递
- [x] `subscribe` 字段处置：subscribeName/concurrency/subscriptionType 逐一 warn-once（对齐 D1 矩阵 Local 行与 Kafka 先例）
- [x] TerminateConsume 分支补 WARN 留证（对齐 broker 格式）
- [x] 测试①（F-14 证明面）：引用既有契约测试 `consumeLaterDefersRedeliveryWithoutAckReply`（WI4 已断言 ack-topic 无投递 + 重投到达），本计划不重复实现
- [x] 测试② 同 listener 两次订阅 → assertNotSame 独立实例；取消 sub2 → `getConsumers` 含 sub1 不含 sub2 且 `sub1.isCancelled()==false`（身份断言，防 B1 形态复发）→ 投递仍达 sub1；两条都取消 → 无投递
- [x] 测试③ subscribe 带全部可选字段 → 不抛（warn-once 而非 typed reject；warn-once 发生本身因 WARNED private 不可断言，如实声明）；契约测试保持全绿
- [x] design 3.9 落档（Local 语义差异登记 + 选项三态表 + 订阅独立化 + registrar 边缘注记）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-message/nop-message-core,nop-message/nop-message-kafka,nop-message/nop-message-pulsar,nop-message/nop-message-debezium -am -T 1C` 退出 0 全绿（BUILD SUCCESS：core 23(1skip)/0F 含契约套件，_tmp/wi23-regression.log）
- [x] **新功能必有测试**：①（既有契约测试引用）+②③ 存在且通过——① 是 F-14 的直接证明（WI4 契约套件）；② 是 F-15 的直接证明（身份断言 + 取消独立性，防 B1 形态）；③ 是 F-56 残面的处置证明（不抛 + 声明）
- [x] **无静默跳过**：字段处置显式 warn-once（非静默忽略）；订阅独立化的重复投递语义在 javadoc + design 双声明
- [x] **接线验证**：② 经 `getConsumers`/投递计数运行时证明；契约测试（WI4 骨架）全绿
- [x] Hard constraint：SPI 零变更；broker 后端零改动
- [x] Owner-doc：design 3.9 已落档
- [x] `ai-dev/logs/2026/` 当日条目已更新
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0

## Closure Gates

- [x] MSG-F-14（验证面）/MSG-F-15/MSG-F-56 残面已修复且有测试证明（①-③）
- [x] 独立子 agent closure audit 已完成并记录证据（见 Closure Audit Evidence）
- [x] `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-message --severity high` 退出码 0
- [x] `node ai-dev/tools/check-silent-wrong-result.mjs --module <m>` 四个 nop-message 模块逐一退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/23-wi23-local-semantics.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- `redeliveryScheduler` 生命周期管理（stop 时 shutdown）（optimization candidate：daemon 线程现状无泄漏面）。

## Closure

Status Note: WI23 收口：F-15 三处同改（subscribe 独立实例 / cancel remove(this) / scheduleRedelivery contains——零 findSubscription 残留）、F-56 残面（subscribeName/concurrency/subscriptionType in-place warn-once，D1 矩阵 Local 行兑现）、TerminateConsume WARN 留证、F-14 以既有契约测试为证（无代码增量）。design 3.9 落档。SPI 零变更、broker 后端零改动。独立 closure audit 实质 PASS（流程整改：executor 曾在审计前预勾 Gate 2/6——已按审计要求以真实审计结论回填并复验 checklist）。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_4760378b-3ac7-4ae5-8bf6-ed85fd504338（fresh session，亲跑全部验证命令）
- Evidence:
  - 8 条 Exit Criteria 全 PASS（亲验）：core 23(1skip)/0F 含契约套件；kafka+pulsar -am exit 0；debezium BUILD SUCCESS。
  - F-15 三处同改逐 hunk 核对：findSubscription/removeConsumer 全仓零残留（grep exit 1）；测试②身份断言真实（assertSame/getConsumers/三段计数 2→3→3）。
  - F-14 引用面：AbstractMessageServiceContractTest.consumeLaterDefersRedeliveryWithoutAckReply 经 LocalMessageServiceContractTest 对 Local 实跑。
  - Anti-Hollow：warn-once 三处真实调用（与 Kafka 先例格式一致）；TerminateConsume WARN 真实且契约测试覆盖该分支；scan-hollow 0 findings；测试 +69/-0 无弱化。
  - Hard constraint：nop-api-core 零触碰（git status 实证）；触碰面=core 2 文件+docs。
  - 门禁：scan-hollow 0；四模块 check-silent-wrong-result 0；check-doc-links 0 errors；check-plan-checklist --strict 于 Closure 填写后复验退出 0。
  - 流程整改记录：executor 曾在审计前预填 Gate 2/6 与 Plan Status（审计 Minor-1/2/Major 抓出）——本 Closure 为按审计结论的真实回填，checklist 复验 0。
  - Deferred 分类检查：无 in-scope live defect 被降级。

Follow-up:

- redeliveryScheduler 生命周期管理（optimization candidate）
- 无 remaining plan-owned work
