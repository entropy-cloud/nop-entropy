# 06 WI6 能力声明机制与选项校验器

> Plan Status: active
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI6，deps WI3✓ WI4✓；对应 MSG-F-51/F-53；Protected Area 变更见下）
> Related: `ai-dev/design/nop-message/message-contract-design.md`（D1/D2/D8 裁定）、`ai-dev/design/nop-message/backend-semantics-design.md`（D5/D9/D11 裁定）

## Purpose

落地 D1 能力声明机制：`MessageCapability` 枚举 + `IMessageService.getCapabilities()`（default 收窄式新增）+ 挂在**每个 `subscribe` 与 `sendAsync` 入口**的 `MessageOptionsValidator`；同时按 D5(b') 落终止拒绝（TerminateConsume）与部分提交（PartialCommit）两个 SPI 信号并让主源码实现显式分派。全部 11+2 主源码实现编译与回归通过。

## Protected Area 声明

本 plan 触发 `nop-api-core` Protected Area 变更（`IMessageService` 新增 default 方法、`message` 包新增类型）。plan-first 前置已满足：roadmap Hard constraint 预告该触发、D1/D5 裁定已落档（design docs）、本 plan 经对抗审查。**不改变既有方法签名与骨架极简形态**（roadmap Hard constraint 允许 default 收窄式新增）。

## Current Baseline

- WI1-WI5 done，M1 done；D1/D2/D5/D8/D9/D11 裁定已落 `ai-dev/design/nop-message/`。
- 契约测试骨架在位（`AbstractMessageServiceContractTest`），是本 plan 的必跑回归面之一。
- 实测实现面：`grep "implements IMessageService"` src/main 11 文件（8 个需接线 validator 的实现：Local/SysDao/Lettuce/MqttServer/Td/DB(agent)/Kafka/Pulsar；3 个装饰器/桩不接线：Delegate/DataPlane 透传、bench 桩豁免）+ src/test 23 文件（不受影响：validator 调用发生在各实现入口内部）。
- 现有 warn 均为逐次 WARN（F-53）：Kafka delay、subscriptionType、未知 seek mode。
- 错误码新前缀按 D9：`nop.err.message.*`（新码直接用新前缀；存量迁移归 WI26）。

## Goals

- **SPI 新增（nop-api-core）**：
  1. `MessageCapability` 枚举：SEND/SUBSCRIBE/BATCH_CONSUME/DELAYED_SEND/TRANSACTION/SEEK/ACK_REPLY/ENVELOPE_METADATA/DLQ。
  2. `IMessageService.getCapabilities()` default 方法（默认最小集 SEND+SUBSCRIBE+ACK_REPLY；不 override 的实现不经过 validator，行为不变）。
  3. `MessageOptionsValidator`：字段→能力映射（delay→DELAYED_SEND；transactional/transactionTimeout→TRANSACTION；batchReceiveCount/batchReceiveTimeout→BATCH_CONSUME；seekMode/seekToMessage/seekToTime→SEEK；其余→SEND/SUBSCRIBE）；`validateSend`/`validateSubscribe` 两个入口；缺失能力默认**类型化拒绝**（`nop.err.message.option-not-supported`，参数 field/capability/backend），后端可选传 warn-once 能力集改为一次性 WARN 后按声明行为执行（warn-once 用类级 ConcurrentHashMap 实现 JVM 内一次）。
  4. `TerminateConsume`（终止拒绝）与 `PartialCommit(int committedCount)`（批量提交边界）两个返回值信号类型。
  5. `MessageErrors` 错误码接口（前缀 `nop.err.message.`）。
- **实现接线**：
  - 8 个实现入口调用 validator：Local/SysDao/Lettuce/MqttServer/Td（空集全拒）/DB(agent)/Kafka/Pulsar；Delegate/DataPlane/bench 不接线（透传/豁免）。
  - **仅非默认值触发校验**（防默认对象误拒/误警）：delay>0、sendTimeout>0（WI15 前全后端 SEND 放行）、cancelToken!=null（同上）、transactional==true、transactionTimeout>0、batchReceiveCount>0、batchReceiveTimeout>0（仅当 count>0 时才校验）、concurrency>1、subscriptionType!=null、seekMode!=null（DEFAULT 视为 null）、seekToMessage!=null、seekToTime>0。
  - **warn-once 能力集按 design D1 表**：Local＝{DELAYED_SEND,TRANSACTION,BATCH_CONSUME,SEEK}；SysDao＝{TRANSACTION,BATCH_CONSUME,SEEK}；Lettuce/MqttServer＝{DELAYED_SEND,TRANSACTION,BATCH_CONSUME,SEEK}；DB(agent)＝{DELAYED_SEND,TRANSACTION,SEEK}；Kafka＝{DELAYED_SEND}；Pulsar/Td＝空集。
  - F-53 消除：Kafka 三处逐次 WARN 改 warn-once。
  - TerminateConsume 分派：Local/Kafka/Pulsar 单条+批量路径 → ack、不重投、无 reply；SysDao（invokeConsumer 与 NonBroadcastEventProcessor.handleResult 两个分派点）/DB(agent)/Lettuce/Mqtt → 按完成处理；**nop-rpc-core 的 RpcMessageSubscriber.handleResponse 显式分支**（信号不进 reply 载荷）。
  - PartialCommit 分派（下标约定）：作为批量响应列表中**第 k 条（0-based）的返回值**，语义＝仅提交 [0..k-1]、k 起不提交不 reply 且停止处理其后响应；Kafka 用 commitSync(OffsetAndMetadata) 按分区收敛到「最后已提交+1」，Pulsar ack [0..k-1]、**k（含携带信号的那条）及其后全部 negativeAck**；Local 无批量消费路径，单条路径收到 PartialCommit 按完成处理并 javadoc 声明。
- **D2 边界声明**：信封契约裁定与能力集声明在本 plan 生效；两后端信封元数据对称性接线归 WI24。

## Non-Goals

- 不做 D9 存量前缀迁移（WI26）、不删 transactional 字段（WI14）、不改批量 arity 契约（WI25）、不修 seek 行为（WI7/WI8）。
- 不改 Delegate/DataPlane/bench/test 实现。
- 不引入新依赖。

## Scope

### In Scope

- `nop-kernel/nop-api-core`：message 包新增 5 类型 + getCapabilities default。
- `nop-message-core`、`nop-sys-dao`、`nop-nosql-lettuce`、`nop-vertx-mqtt-server`、`nop-orm-tdengine`、`nop-ai-agent`、`nop-message-kafka`、`nop-message-pulsar`、`nop-rpc-core` 的入口接线与信号分派。
- 测试：validator 单测、信号分派测试（contract 骨架扩展 + Kafka/Pulsar 单测 + RpcMessageSubscriber 分支单测）。

### Out Of Scope

- 信封元数据对称接线（WI24）、错误码存量迁移（WI26）、seek 行为修复（WI7/WI8）、批量 arity 重构（WI25/WI11）。

## Execution Plan

### Phase 1 - SPI 新增与单测

Status: completed
Targets: `nop-kernel/nop-api-core`

- Item Types: `Feature`

- [x] MessageCapability / MessageErrors / TerminateConsume / PartialCommit / MessageOptionsValidator 五件新增 + IMessageService.getCapabilities default
- [x] validator 单测：字段映射、类型化拒绝、warn-once 一次性语义、null options 直通

Exit Criteria:

- [x] `./mvnw test -pl nop-kernel/nop-api-core -am` 退出 0
- [x] 新类型有 Javadoc 契约语义（TerminateConsume/PartialCommit 的 ack/提交边界语义与 design D5 一致）
- [x] `ai-dev/logs/` 条目

### Phase 2 - 实现接线与 warn-once

Status: completed
Targets: 8 个实现模块

- Item Types: `Feature`

- [x] 8 实现入口接线 validator（Td 空集全拒；Kafka DELAYED_SEND warn-once）
- [x] F-53：Kafka 三处逐次 WARN 改一次性
- [x] 各实现 getCapabilities 按 design D1 表 override（偏差：Pulsar SEEK 待 WI8 与实现同批声明，代码注释与日志已登记）

Exit Criteria:

- [x] SPI 回归面构建+测试退出 0
- [x] 契约测试（Local extend）全绿
- [x] **接线验证**：validator 在运行时被 8 实现入口调用（测试断言非法选项被拒/被 WARN，而非仅类型存在）
- [x] `ai-dev/logs/` 条目

### Phase 3 - D5(b') 信号分派

Status: completed
Targets: Local/Kafka/Pulsar + 其余实现完成语义

- Item Types: `Feature`

- [x] TerminateConsume 分派：Local/Kafka/Pulsar 单条+批量（ack 不重投无 reply）；SysDao/DB/Lettuce/Mqtt 按完成处理；RpcMessageSubscriber 显式分支
- [x] PartialCommit 分派：Kafka/Pulsar 批量路径提交边界（Pulsar 第 k 条消息本身 negativeAck——closure audit Major-1 修复）；Local javadoc 声明视为完成
- [x] 分派测试：contract 骨架扩展两用例 + Kafka/Pulsar 单测（含 Pulsar 第 k 条 negativeAck 断言）+ RpcMessageSubscriber 分支单测

Exit Criteria:

- [x] 全量消息回归：mission test 命令退出 0（两个 nop-stream-runtime 失败经基线复现甄别为既有失败与并行 flaky，与改动无关）
- [x] 请求-响应必答方（nop-rpc-core / nop-graphql-message）测试零回归
- [x] **无静默跳过**：新信号在所有分派点显式处理，无实现把信号当普通 reply
- [x] `ai-dev/logs/` 条目

## Closure Gates

- [x] SPI 五件新增完成且契约语义与 design 一致
- [x] 8 实现入口 validator 接线并经运行时断言验证
- [x] TerminateConsume/PartialCommit 全分派点显式处理
- [x] F-53 三处 warn-once 化
- [x] mission test 命令 + SPI 回归面命令退出 0
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/06-wi6-capability-validator.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- Kafka transactional=true 的类型化拒绝码在 WI14 按 D11 字段删除时一并收敛。
- warn-once 记录的并发 map 无上界（键空间为 backend×field，有界）——WI18 门禁化时复核。

## Closure

Status Note: WI6 经独立子代理 closure audit：SPI 五件、8 实现接线、F-53 消除、信号全分派点、测试命令运行全部 PASS；audit 抓获 1 个 Major（Pulsar 批量 PartialCommit 漏 nack 第 k 条消息的 off-by-one）已修复并补断言测试；4 个 Minor（plan 文件重复拷贝→重写、design Pulsar SEEK 行漂移→加注、import 重复→清理、死变量→清理）已处置。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_8b0349a1-1383-4bb9-a561-8bd3fc7211e8（fresh session，自行执行测试命令）
- Evidence:
  - SPI 新增 PASS：5 文件 + getCapabilities default，骨架极简形态未破坏
  - 8 实现接线 PASS：入口×后端名×warn-once 逐一核对，与 design D1 表一致（Pulsar SEEK withheld 偏差已声明）
  - F-53 PASS：三处逐次 WARN 0 残留，warn-once 在位
  - 信号分派 PASS（Major-1 修复后）：全部 reply 发送分支核对，无信号当 reply
  - 运行验证 PASS：api-core -am 退出 0（validator 6/6）；message+rpc -am -T 1C 退出 0（Kafka 9/9、契约 14/1skip/0 fail、Rpc 信号 3/3）
  - git 范围 PASS：无超范围产品代码
  - Anti-Hollow PASS：scan-hollow-implementations --severity high 对涉及模块全 0 发现
  - Major-1 修复证据：PulsarConsumeTask 批量 PartialCommit 分支 nack 第 k 条本身 + TestPulsarConsumeTask 新增断言「信号携带消息被 negativeAck」
  - Deferred 分类检查：无 deferred
