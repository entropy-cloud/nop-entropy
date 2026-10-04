# 07 WI7 Kafka seek 起点解析与失败可达调用方

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI7，deps WI4✓；对应 MSG-F-01 与 MSG-F-02）
> Related: `ai-dev/design/nop-message/backend-semantics-design.md`（D3 裁定）、`ai-dev/plans/nop-message/06-wi6-capability-validator.md`

## Purpose

按 D3 裁定重构 Kafka seek 起点解析：从「poll 循环前对空 assignment 的一次性 no-op」（MSG-F-01）改为 `ConsumerRebalanceListener.onPartitionsAssigned` 逐次重解析；`seekToMessage` 的类型化拒绝从消费线程改到 `subscribe` 调用方同步抛出（MSG-F-02 的 seek 切面）；删除/重写把 MSG-F-01 认证为正确行为的 `seekToBeginHonoredNotStubbed` 测试。

## Current Baseline

- WI6 done：validator 门禁在位，Kafka 能力集已声明 SEEK，测试设施（TestKafkaConsumeTask mock 设备）在位。
- `KafkaConsumeTask.seekToPosition()`（`:106-149`）在 `start()` 中先于 `runTask()` 执行，此时 `consumer.assignment()` 为空集 → 四种 SeekMode 与 seekToTime 全部 no-op；唯一生效分支是 `seekToMessage` 抛错（`:115-117`），但该抛错发生在 executor 线程、被 `start()` 的 catch 吞掉（`:89-95`），调用方拿到 `isCancelled()==false` 的死订阅（MSG-F-02）。
- `KafkaMessageService.doSubscribe`（`:275-293`）用 `kConsumer.subscribe(topics)`（无 listener）。
- 既有测试 `seekToBeginHonoredNotStubbed`（`:154-168`）用 mock 造非空 assignment 直调 `seekToPosition()`，把 no-op 认证为正确（roadmap 点名必须删除或重写）。

## Goals

- **D3 落地**：`doSubscribe` 改用 `subscribe(topics, ConsumerRebalanceListener)`；`onPartitionsAssigned` 内对新分配分区应用 seek 策略，INIT 系按 design 裁定**以消费组位移状态门控**：对该分区查 `consumer.committed(...)`，committed == null（消费组从未提交过该分区）才执行 INIT_SEEK_TO_BEGIN→seekToBeginning / INIT_SEEK_TO_END→seekToEnd——与 SeekMode javadoc「再次连接从上次消费后的位置开始」一致，避免服务重启后整条 log 重投；committed != null 跳过（从 committed 恢复）。ALWAYS_SEEK_TO_END 每次分配无条件 seekToEnd。seekToTime>0（含 DEFAULT 模式）仅任务实例首次分配时执行（每次 rebalance 重放会反复回卷同一时间戳，故仅首次；此为 design 静默处的补全，理由登记）。committed-gating 顺带使 INIT seek 失败后下次分配自然重试。
- **失败可达**：`seekToMessage` 校验移到 `doSubscribe` **最顶部**（groupId 校验旁、`new KafkaConsumer` 之前——consumer 构造会起网络线程，校验后置会泄漏），类型化拒绝 `ERR_KAFKA_SEEK_NOT_SUPPORTED` 直接抛给 `subscribe` 调用方。
- **consumer 注入缝隙**：`KafkaMessageService` 新增 protected 工厂 `createConsumer(props)`（默认 `new KafkaConsumer<>(...)`），测试子类覆盖注入——captor 断言 listener 传入真实 `subscribe` 调用。
- **消费循环不再有启动期 seek**：删除 `start()` 中的 `seekToPosition()` 前置调用与该方法本体，poll 循环只负责消费。
- **测试重写**：`seekToBeginHonoredNotStubbed` 删除。主断言改为 **kafka-clients 自带 `MockConsumer` 的 poll-first 形态**（非 Mockito mock、无新依赖）：`schedulePollTask(() -> mc.rebalance(partitions))` 驱动一次真实 `poll()`，在 poll 内触发 rebalance→listener→seek，断言 `mc.position(tp)`——字面满足「非 mock + poll-first」。committed-gating 用例以 MockConsumer 子类覆写 `committed(Set)` 实现（MockConsumer 无 committed setter）。辅助断言：captor 接线（listener 真实传入 subscribe）+ `subscribe` 级 `ERR_KAFKA_SEEK_NOT_SUPPORTED` 同步拒绝（校验在 consumer 构造前，无需 consumer 实例）；`seekToMessageThrowsNotSilentlySkipped` 随 `seekToPosition()` 删除一并迁移到 service 级。真 broker rebalance 时序 Deferred 归 WI33。

## Non-Goals

- 不修 MSG-F-02 的完整面（非 seek 类消费错误仍走现有 catch 退避；失败回调归 WI16）。
- 不动 Pulsar seek（WI8）。
- 不改 `auto.offset.reset` 默认（earliest 语义保持，与 INIT_SEEK_TO_BEGIN 叠加关系在测试中锁定）。

## Scope

### In Scope

- `nop-message-kafka`：KafkaMessageService（doSubscribe + listener）、KafkaConsumeTask（seek 策略重构）、TestKafkaConsumeTask（重写 seek 相关用例）、TestKafkaSubscription 如受影响则同步。
- `ai-dev/logs/2026/10-04.md`、roadmap WI7 状态行。

### Out Of Scope

- nop-stream-runtime 数据面回归之外的其他模块；`_gen/` 生成物。

## Execution Plan

### Phase 1 - D3 重构与测试重写

Status: completed
Targets: `nop-message/nop-message-kafka`

- Item Types: `Fix`

- [x] KafkaMessageService.doSubscribe：seekToMessage 顶部同步校验 + protected `createConsumer` 工厂 + subscribe 带 listener（onPartitionsRevoked 刻意空实现并注释理由：提交已由每批 poll 后 commitSync 完成，revoke 无需动作）
- [x] KafkaConsumeTask：新增 `onPartitionsAssigned(Collection<TopicPartition>)`（committed 门控 + seekToTime 首次标志，listener 仅 poll 线程回调故普通 boolean 足够），删除 `seekToPosition()` 与 start() 前置调用；同步重写类 javadoc 的旧 seek 策略自述
- [x] 测试：MockConsumer poll-first 主断言（INIT 无 committed→seekToBeginning、有 committed→不回卷【关键回归判别点】、ALWAYS 两次分配均 seekToEnd、seekToTime 仅首次、DEFAULT+seekToTime）+ captor 接线 + service 级 seekToMessage 同步拒绝（迁移旧 seekToMessageThrowsNotSilentlySkipped）+ 删除 seekToBeginHonoredNotStubbed

Exit Criteria:

- [x] `./mvnw test -pl nop-message/nop-message-kafka -am -T 1C` 退出 0（全绿）
- [x] 契约测试 Local extend 零回归
- [x] **接线验证**：captor 断言 listener 真实传入 `consumer.subscribe`
- [x] **无静默跳过**：空 mode 组合不产生 seek 调用（verify 精确性）；seekToMessage 拒绝在 subscribe 线程抛出
- [x] **新功能必有测试**：首/后续分配语义各有断言
- [x] mission 回归（message 模块 + rpc-core）退出 0
- [x] `ai-dev/logs/` 条目
- [x] No owner-doc update required（D3 行为即 design 既有裁定）

## Closure Gates

- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-message --severity high` 退出 0
- [x] MSG-F-01 形态消除：无 poll 前空 assignment seek 路径残留
- [x] MSG-F-02 seek 切面消除：seekToMessage 拒绝同步到达 subscribe 调用方
- [x] 旧反模式测试已删除且新断言就位
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-core -am -T 1C` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/07-wi7-kafka-seek-rebalance.md --strict` 退出码 0

## Deferred But Adjudicated

### 真实 broker rebalance 时序验证

- Classification: `watch-only residual`
- Why Not Blocking Closure: roadmap Cross-cutting 既有约束——外部 broker 的 rebalance 时序与分区分配结果无法在本仓库静态验证，凡依赖该口径的完成判定必须以 WI33 gated 测试为准；本 plan 的 MockConsumer poll-first 已覆盖 seek 策略逻辑本身。
- Successor Required: `yes`
- Successor Path: WI33 gated 真实 broker E2E

## Non-Blocking Follow-ups

- 真 broker rebalance 时序验证 → WI33 gated E2E（roadmap 既有约束，非本 plan 可验证）。

## Closure

Status Note: WI7 经独立子代理 closure audit：MSG-F-01/F-02（seek 切面）形态消除、D3 语义与 design 一致、36/36 全绿、范围干净。audit 抓获 1 个 Moderate（plan 声称的 seekToTime 仅首次测试缺失）——已补 `seekToTimeAppliesOnlyOnFirstAssignment`（listener 驱动断言两次分配仅一次 offsetsForTimes）后关闭。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_77885981-1927-4fe0-a2ce-e7e76b6c5c22（fresh session，自行重跑测试与工具）
- Evidence:
  - MSG-F-01 消除 PASS：无 seekToPosition 残留，onPartitionsAssigned 含 committed 门控/ALWAYS 每次分配/seekToTime 首次标志
  - MSG-F-02 seek 切面 PASS：seekToMessage 校验在 consumer 构造前同步抛出
  - 旧反模式测试删除 PASS（全仓 grep 0 命中）；MockConsumer poll-first 主断言 + captor 接线 + service 级拒绝在位
  - 运行 PASS：nop-message-kafka 36/36（audit 时 35，补 seekToTime 测试后 36）、nop-message-core 17/0fail/1skip
  - D3 一致性 PASS：design 3.1 各项对齐
  - git 范围 PASS：仅 nop-message-kafka 三文件 + roadmap/plan/日志
  - 工具 PASS：scan-hollow --severity high 0 发现；checklist --strict 退出 0
  - Deferred：真 broker rebalance 时序 → WI33（Deferred But Adjudicated 显式登记）
