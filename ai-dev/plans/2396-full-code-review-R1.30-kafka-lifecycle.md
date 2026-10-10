# 2396 full-code-review R1.30 — nop-message kafka 消费生命周期修复批（P1-4.4-M01..M03）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Completed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.30；findings `P1-4.4-M01..M03`（check/msg-cluster-cred.md P1-5..P1-7，4.4 §2.3 标定维持），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/4.4-format-search-message-cluster-audit.md` §2.3（M-01..M-03）与 §5 裁决移交④②
> Related: P2-6.1-001 同点位唯一承载勿双立（R1.0 裁决：本行吸收）；7.7 移交①②（suspended 脱钩并入 M-01、ALWAYS/INIT 区分必须随 M-03）；P3-4.4-008（runTask onException 未防护——P3 台账不混入）
> Owner Doc: `docs-for-ai/03-modules/nop-network.md`

## Purpose

收口 kafka 消费生命周期三条 P1：①suspend/resume 在调用线程直操 KafkaConsumer 且 suspended 标志与 poll 循环脱钩——rebalance 后 pause 静默失效；②cancel 先 consumer.close 后等线程退出——消费线程使用中 close 的 acquire 等待抛 CME 被吞 → consumer 不关闭泄漏，cancel 线程阻塞最长 60s；③seekToPosition 在分区分配前执行全部 no-op（真实 KafkaConsumer 首次 poll 前 assignment 恒空）——且既有测试以 mock 注入非空 assignment 掩盖缺陷（测试重写义务）。

## Current Baseline

- **M-01**：`KafkaMessageService$KafkaMessageSubscription.suspend/resume:360-381`——调用线程直操 consumer.pause(consumer.assignment())，suspended 标志消费线程零引用（KafkaConsumeTask.runTask 只看 active）；rebalance 新分配分区不受 pause 约束（7.7 移交①并入）。
- **M-02**：`cancel():324-351`——task.stop()（wakeup 异步生效）→ 立即 consumer.close() → shutdown+awaitTermination(5s)+shutdownNow；消费线程在 poll/onMessage/commitSync 中时 close 抛 CME 被 catch 吞 → 泄漏。
- **M-03**：`doSubscribe:273-288` subscribe(topics) 无 ConsumerRebalanceListener；task.start() 先 seekToPosition() 再 poll——assignment 恒空 → seek 全 no-op。**7.7 裁决②**：迁移到 onPartitionsAssigned 时必须区分 ALWAYS（每次 assigned 执行）与 INIT_*（仅首次）。**测试掩盖**：TestKafkaConsumeTask#seekToBeginHonoredNotStubbed:153-169 stub `when(consumer.assignment()).thenReturn(assignment)` 注入非空——重写义务（dimension 21 移交）。
- **测试基建**：nop-message-kafka 测试树成熟（mockito 在 classpath：TestKafkaSubscription/TestKafkaConsumeTask 等 5 文件）；KafkaMessageSubscription 为 public inner class 可直构（TestKafkaSubscription 先例）。

## Goals

- M-01：suspend/resume 迁移为期望态标志（volatile，消费线程应用）——poll 循环每轮按标志 pause/resume 当前 assignment；rebalance listener onPartitionsAssigned 按标志对新增分区补 pause（rebalance 后不再失效）。
- M-02：cancel 顺序重排——task.stop()（wakeup）→ executor.shutdown + awaitTermination（消费线程退出，consumer 不再被使用）→ consumer.close()（末位）。
- M-03：seek 语义迁移 ConsumerRebalanceListener.onPartitionsAssigned——ALWAYS_SEEK_TO_END 每次 assigned 执行；INIT_*/seekToTime 仅首次 assigned 执行（7.7 裁决②）；seekToMessage fail-loud 保留。
- 掩盖测试重写（seekToBeginHonoredNotStubbed → listener 语义直调形态，assignment 作为方法参数传入不 stub）；判别四腿 stash 实证；nop-message-kafka 测试全绿。

## Non-Goals

- P3-4.4-008（runTask onException 防护）与停机观察（P3 台账/低风险）；非 ApiMessage 载荷编码（7.7 裁决③独立）；pull 消费重构；TestKafkaSubscription 的既有断言面重构。

## Scope

### In Scope

- `KafkaConsumeTask`：期望暂停态字段 + poll 循环应用 + `seekOnAssigned(partitions)`（ALWAYS/INIT 区分 + 首次标记）+ start 不再直调 seek
- `KafkaMessageService.doSubscribe`：subscribe(topics, rebalanceListener)（onPartitionsAssigned → seekOnAssigned + 按 suspended 补 pause）；cancel 顺序重排
- `KafkaMessageSubscription.suspend/resume`：改期望态标志
- 测试：TestKafkaConsumeTask 掩盖测试重写 + 新增 M-01/M-02 判别；TestKafkaSubscription 适配
- owner doc：network.md kafka 消费语义注记

### Out Of Scope

- Non-Goals 全部；producer 停机观察；P2-6.1-001（已由本行承载）；Pulsar 侧 seek stub

## Execution Plan

### Phase 1 - 三修 + 判别测试

Status: completed
Targets: 上列文件

- Item Types: `Fix`

- [x] M-01：KafkaConsumeTask 增 `desiredPaused`（volatile）+ poll 循环每轮（poll 返回后）`applyDesiredPause()`：desiredPaused ? consumer.pause(assignment) : consumer.resume(assignment)；KafkaMessageSubscription.suspend/resume 改设 task 期望态
- [x] M-03：seekToPosition 逻辑重构为 `seekOnAssigned(Collection<TopicPartition>)`（参数传入不再读 assignment）——ALWAYS_SEEK_TO_END 每次执行；INIT_SEEK_TO_BEGIN/INIT_SEEK_TO_END/seekToTime 仅首次（`initSeekDone` 标记）；seekToMessage fail-loud 保留；doSubscribe subscribe 挂 ConsumerRebalanceListener：onPartitionsAssigned → seekOnAssigned(partitions) + desiredPaused 时对 partitions 补 pause（M-01 rebalance 面闭合）；task.start() 移除直调
- [x] M-02：cancel 顺序重排 task.stop() → executor.shutdown/await → consumer.close()（末位；异常 catch 保留）
- [x] 测试：①掩盖测试重写——seekToBeginHonoredNotStubbed 删除，新增 `testSeekOnAssignedHonorsInitMode`（直调 seekOnAssigned(assignment) 断言 seekToBeginning；二调不再执行=INIT 语义）与 `testSeekOnAssignedAlwaysMode`（每次都 seekToEnd）；②M-01——suspend 后 task 循环应用：直调 applyDesiredPause 断言 consumer.pause(assignment)（不 stub assignment 之外行为）；resume 对称；③M-02——cancel 顺序：task.stop 后线程退出才 close（latch 或顺序记录断言 consumer.close 在 executor 终止后）
- [x] **判别验证（三腿 stash）**：①stash M-01→suspend 应用测试 FAIL（pause 未调用/调用线程外）；②stash M-02→顺序断言 FAIL（close 先于线程退出）；③stash M-03→INIT 用例 FAIL（seekOnAssigned 不再被 listener 驱动/首调不 seek）——各腿恢复后绿
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 三修全落地（期望态暂停/cancel 顺序/listener seek）
- [x] 判别测试三腿绿 + 既有测试零回归（掩盖测试重写后）
- [x] nop-message-kafka 测试全绿
- [x] owner doc 处置归 Phase 2

### Phase 2 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-network.md`

- Item Types: `Fix`

- [x] network.md：kafka 消费语义注记（suspend=期望态由消费循环应用且 rebalance 保持；seek=onPartitionsAssigned 驱动且 ALWAYS/INIT 区分；cancel=先停线程后 close）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.30 翻 done；index P1-4.4-M01..M03 → fixed；daily log 条目

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-4.4-M01..M03）
- [x] 行为/契约结果已达成（判别三腿 + 零回归）
- [x] 必要 focused verification 已完成（nop-message-kafka 测试全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-kafka` 全绿（34/34，执行轮+审计独立复跑双验证）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- P3-4.4-008 runTask onException 防护（P3 台账通道）
- PulsarConsumeTask.seekToPosition stub（同族面——Pulsar backend 独立计划）

## Closure

Status Note: 三修语义完整落地、判别三腿精确（含 M-01 首轮无效腿如实改腿且最终腿为行为载体）、34/34 审计独立复跑全绿、owner doc/index/roadmap 一致。audit「Can Close（有条件）」的两步机械回填（daily log 条目补写 + Closure 段证据落盘）已完成。P2-6.1-001 Pulsar 半边维持 open 待独立 Pulsar plan 承接（daily log 已注记，防「唯一承载+done」记账掩埋 live P2——audit 问题 2 建议）。

Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）agent_833dfb58-269e-4963-9a6b-1d4d9d65601a
- Evidence:
  - M-01 PASS：KafkaConsumeTask:73 desiredPaused/:218 轮首应用/:117-127 pause-resume；Service:296-301 listener 补 pause；Subscription:384-396 零 consumer 直操
  - M-03 PASS：seekOnAssigned:137 参数驱动；INIT 守卫 :152-155 + 置位 :169/:173；seekToMessage :146-149 fail-loud；start :97-103 无直调
  - M-02 PASS：cancel :347-371 顺序严格（stop→shutdown→await 5s→close 末位）
  - 测试证据：审计独立执行 34/34 EXIT=0（Send 5+Helper 9+IoC 5+Subscription 5+ConsumeTask 10）
  - 判别证据：_tmp/disc-m03.log（恰 INIT-once 1F）/ _tmp/disc-m02.log（恰 closeDuringRun 1F，消息逐字翻转）/ _tmp/disc-m01.log（首轮直调腿 0F 确证无效——如实记录）+ _tmp/disc-m01b.log（改腿后恰 suspendFlagAppliedByConsumeLoop 1F，Wanted pause 未调用+176,257 次 poll 证明循环在跑）
  - Anti-Hollow：运行时接线（代码追踪+行为级测试双证）；无静默 no-op（既存设计决策均有 javadoc）；hollow 扫描 0 findings
  - 收口完整性：roadmap :161 done；index :186-188 fixed；owner doc network.md:62 逐句吻合；doc-links 审计亲跑 EXIT=0
  - Follow-ups 诚实性：P3-4.4-008（从未入 scope，4.4 :527 P3 通道）/Pulsar stub（Out Of Scope 指名 successor）——诚实 non-blocking
  - audit 问题清单处置：问题 1（daily log 缺失）已补写；问题 2（P2-6.1-001 注记）已入 daily log；问题 3（doNothing 未用 import/用例名残留）为 cosmetic 留观；问题 4（38MB 判别日志）卫生注记

Follow-up:

- P3-4.4-008 runTask onException 防护（P3 台账通道）；PulsarConsumeTask.seekToPosition stub（独立 Pulsar plan，M-03 seekOnAssigned 为语义模板）
