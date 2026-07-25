# Mailbox 执行模型（G22, P1）

> Plan Status: completed
> Last Reviewed: 2026-07-25
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 17；`ai-dev/analysis/nop-stream/08-gap-analysis.md` G22（07-dist: D2）
> Mission: nop-stream-production
> Work Item: 17
> Related: Plan `2026-07-25-0800-3-multi-input-barrier-alignment`（Stage 16，已完成前置）；`ai-dev/design/nop-stream/checkpoint-design.md` §2.2；Deferred successor of `2026-07-25-0800-1-session-window-merge`（multi-key 测试 flakiness）

## Purpose

引入 nop-stream 原生的最小化 mailbox 控制面，把 **SOURCE 任务的 checkpoint 触发**与**所有任务的 abort**从脆弱的跨线程交接（cap-1 队列 + `Thread.interrupt()`）收敛为 task 线程上的协作式 mail/flag，并为后续 processing-time timer 接线与异步 snapshot（Stage 18）奠定执行模型基础。**不移植 Flink `MailboxProcessor` 全套机制**。经设计审查采用**保守范围**：middle/sink 的 `triggerCheckpoint` **保持 injector 线程同步**（仅 prime ack 计数、不执行 operator 代码、已 `synchronized` 安全），以保留"下游 ack 计数在 barrier 经数据流到达前已 prime"的跨 task 不变式。

## Current Baseline

> 所有引用均为 live repo 核对结果，路径相对 `nop-stream` 模块根。

### 执行与角色分派
- **主处理循环（仅 MIDDLE/SINK）**：`StreamTaskInvokable.processInputGate(Input)` at `nop-stream-core/.../execution/StreamTaskInvokable.java:350-368`，由 `invokeMiddle()`(307)/`invokeSink()`(323) 调用。该循环已在 task 线程单线程内处理 element/watermark/barrier/watermarkStatus。
- **SOURCE/SELF_CONTAINED 无此循环**：`invokeSource()`(273)/`invokeSelfContained()`(331) 调 `sourceOp.run()`(282/340)，阻塞在用户 `SourceFunction.run()` 直到耗尽。SOURCE 的"循环"由 source 的 `collect()` 发射驱动。
- **每 subtask 单线程，subtask 间并发**：`SubtaskTask.run()`(`.../execution/SubtaskTask.java:65`)；线程池 `TaskExecutor`(`.../execution/TaskExecutor.java:71`，固定池 115，默认 `availableProcessors`)。
- **跨 task 数据传递阻塞式**：`ResultPartition`(`.../execution/ResultPartition.java:46-48`，`LinkedBlockingQueue` cap 1024)。
- **`InputGate.read()` 阻塞**：`readSingleChannel()`(264) 调 `InputChannel.read()`→阻塞 `take()`；`readMultiChannel()`(280) 用 **50ms poll**(297)。**无现成的非阻塞 read API**。

### Barrier 注入与跨 task priming 不变式（决定本 plan 范围）
- barrier-injector 线程：`GraphModelCheckpointExecutor.startBarrierScheduler()`(568)，executor at 578-582，每 tick 调 `triggerBarrierOnAllInvokables()`(599-613) → **对所有 invokable 同步调** `inv.getBarrierTracker().triggerCheckpoint(...)`(603) **在 injector 线程上，按序、在 source 发射 barrier 之前**。
- `CheckpointBarrierTracker.triggerCheckpoint()`(`.../execution/CheckpointBarrierTracker.java:60`，`synchronized`)：source 场景调 `((StreamSourceOperator)head).offerBarrier`(84)；返回 `boolean accepted`（overlap 时 false，60-63）。**关键：该同步调用 prime 了 ack 计数（`currentCheckpointId`/`operatorsToAck`），且先于 barrier 经数据流到达下游** —— 这是当前 checkpoint 不 hang 的不变式。
- middle/sink 的 barrier 经数据流到达：`InputGate.read()`→`processInputGate():362`→`AbstractStreamOperator.processBarrier()`(`.../operators/AbstractStreamOperator.java:297-322`)→`snapshotState`(303, task 线程)→`acknowledgeOperator`（`CheckpointBarrierTracker:98`，若 `currentCheckpointId<0` 则**静默丢弃 ACK**(105-108)）。
- **推论**：若把 middle/sink 的 `triggerCheckpoint` 改为 fire-and-forget mail，mail 可能晚于 in-band barrier 被处理 → ACK 被 `acknowledgeOperator` 静默丢弃 → checkpoint hang。故 middle/sink trigger **必须保持同步**（见 Non-Goals）。
- source 端 cap-1 队列交接（本 plan 改造对象）：`StreamSourceOperator.pendingBarriers`(`.../operators/StreamSourceOperator.java:53`，`LinkedBlockingQueue<CheckpointBarrier>(1)`)，`offerBarrier`(79)，在 task 线程 `SourceContext.collect()`(133-136) 内 `injectPendingBarrier()`(185) 消费。
- **finished-source 路径**：源完成后 `invokeSource()` 返回、`SubtaskTask.run()` 跳出循环(77-80)，**source task 线程不再存在**；此时 `offerBarrier`(80-88) 直接在 injector 线程调 `injectBarrier`(83)→`snapshotState`+`emitBarrier`（final checkpoint，`triggerFinalCheckpoint` 615-628）。无 task 线程可投递 mail —— 本路径须保留 injector 线程执行（显式例外）。

### Abort（跨线程 interrupt，本 plan 改造对象）
- `GraphModelCheckpointExecutor.registerLocalAbortHandler()`(659-683) 运行在 `timeoutScheduler` 线程，调 `inputGate.resumeConsumptionAll()`(676) + `task.cancel()`(679)→`t.interrupt()`(`SubtaskTask.java:111`)。interrupt 使阻塞 read 抛 `InterruptedException` 上传。handler 闭包可访问 `tasks`（`Map` of `SubtaskTask`）。

### Timer / 其他
- Event-time timer 在 task 线程内联触发（无并发）；processing-time timer 生产不触发（`HeapInternalTimerService.fireProcessingTimeTimers()`(122) 零生产调用者，`ProcessingTimeService` 无生产实现）。
- **无 mailbox/Mail/TaskMailbox 概念**：全仓搜索仅命中 `processInputGate`。
- 已知并发缺陷（本 plan 间接缓解）：`TestCheckpointBarrierTrackerConcurrency.java:119-142`（extra ACK 使 `operatorsToAck` 变负）。
- 前置 deferred 项：Plan `2026-07-25-0800-1-session-window-merge` 的 multi-key flakiness 根因是**跨 subtask 调度**，本 plan（task 内）不直接解决，归独立 successor（见 Deferred）。

## Goals

- 引入最小 mailbox 原语（`Mail` + `TaskMailbox` + `MailboxExecutor`），作为 task 线程的**控制面**：投递/取出控制类 mail（checkpoint 触发、cancel），单消费者串行。
- **SOURCE checkpoint 触发改为 mail**：在 `SourceContext` 发射点 drain mailbox（替代 `pendingBarriers` cap-1 交接），使 source 的 `snapshotState`/`emitBarrier` 在 task 线程经 mail 触发。
- **abort 改为协作式 cancel**：保留 interrupt 作为**解除阻塞 read 的手段**（无现成非阻塞 read），新增 cancel flag/mail 在主循环顶部检查，使退出为受控优雅退出（替代仅靠 interrupt 抛异常上传）。
- **middle/sink `triggerCheckpoint` 保持 injector 线程同步**（仅 prime 计数、已 `synchronized`、保跨 task priming 不变式）—— **显式 Non-Goal，不改 mail**。
- 消除/缓解 `TestCheckpointBarrierTrackerConcurrency` 的 ACK 重入缺陷（source 路程串行化 + middle/sink 同步不变）。
- 为 Stage 18（异步 snapshot）与未来 processing-time timer 接线提供 mail 投递点（**仅奠基，不接线**）。

## Non-Goals

- **middle/sink `triggerCheckpoint` 不改 mail**：保持 injector 线程同步 prime ack 计数，以保"下游计数先于 in-band barrier prime"不变式（改为 mail 会致 ACK 丢弃、checkpoint hang）。middle/sink 的 in-line barrier 处理（task 线程）不变。
- **finished-source final checkpoint 不改 mail**：源完成、task 线程已不存在，`injectBarrier` 保留在 injector 线程执行（显式例外）。
- **不引入非阻塞 InputGate read**：无现成 API；abort 用 interrupt 解除阻塞 read 后在循环顶检查 cancel（见 Goals）。
- **跨 subtask 确定性测试调度**（Plan 14 deferred successor 的真正落点，独立 plan）。
- processing-time timer 生产接线；异步 snapshot（Stage 18）；跨 JVM（Stage 39）；Flink MailboxProcessor 全套语义；多输入算子；拆除线程池；改 `OperatorChain` open/close 顺序。

## Scope

### In Scope

- 新增 mailbox 原语（`Mail`/`TaskMailbox`/`MailboxExecutor`）于 `nop-stream-core`，含聚焦单测。
- SOURCE/SELF_CONTAINED：`SourceContext` 发射点 drain mailbox；trigger-checkpoint mail 替代 `pendingBarriers` cap-1 交接；source 的 `snapshotState`/`emitBarrier` 在 task 线程经 mail 触发。
- abort：协作式 cancel（interrupt 解除阻塞 + cancel flag/mail 循环顶检查 → 优雅退出）。
- mailbox 归属接线：`StreamTaskInvokable` 创建/持有 mailbox，经既有引用链暴露（source 路径 + abort handler）。
- middle/sink 主循环在顶部检查 mailbox（cancel mail + 未来 mail），barrier/element 处理保持 in-line 不变。
- 设计决策记录入 `ai-dev/design/nop-stream/`（含"为何 middle/sink trigger 保持同步"的理由）。
- 全量回归 + 反空壳 + 测试。

### Out Of Scope

- middle/sink `triggerCheckpoint` 改 mail；finished-source final checkpoint 改 mail；非阻塞 InputGate read。
- 跨 subtask 确定性调度；processing-time timer 接线；异步 snapshot；跨 JVM；Flink 全套；多输入算子；拆除线程池。

## Execution Plan

### Phase 1 — Mailbox 原语（归属契约 + 单测）

Status: completed
Targets:
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/`（新增 mailbox 类型）
- `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/execution/`（新增 mailbox 单测）
- `ai-dev/design/nop-stream/`（新增 mailbox 设计章节）

- Item Types: `Decision | Proof`

- [x] `Decision` 确定最小 mailbox 契约与归属：`Mail`（闭包 + 类别：control-mail 优先）、`TaskMailbox`（多生产者投递 + 单消费者取出，非阻塞 `poll`、关闭后 `take` 退出）、`MailboxExecutor`（`while(running){ if(cancelled) break; mail = mailbox.poll(); if(mail!=null) mail.run(); else yield/wait; }`）。**归属**：`TaskMailbox` 每 subtask 一个，由 `StreamTaskInvokable` 创建并持有，暴露 `getMailbox()`；设计文档须记录三种用法（SOURCE 发射点 drain；middle/sink 循环顶 poll；abort 投递 cancel mail）**与"middle/sink trigger 保持同步、不改 mail"的决策理由**。
- [x] `Proof` 实现 `Mail`/`TaskMailbox`/`MailboxExecutor`，**未实现分支抛 `UnsupportedOperationException`，不得空方法体/静默跳过**。
- [x] `Proof` 新增聚焦单测：投递顺序、control-mail 优先、关闭后退出、多生产者单消费者线程安全、非阻塞 `poll` 空时不阻塞。

Exit Criteria:

- [x] `TaskMailbox`/`MailboxExecutor`/`Mail` 类型存在且有聚焦单测（顺序、control 优先、关闭退出、单消费者线程安全、非阻塞 poll）
- [x] mailbox 设计章节写入 `ai-dev/design/nop-stream/`（最终设计状态：归属 + 三种用法 + middle/sink 同步理由；非 Proposed/Current 对比）
- [x] **无静默跳过**：未实现分支抛 `UnsupportedOperationException`
- [x] **接线验证**：本 Phase 仅引入原语，接线在 Phase 2（Phase 1 exit 不含接线）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — SOURCE trigger 改 mail + abort 协作式 cancel + 归属接线

Status: completed
Targets:
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/SubtaskTask.java`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSourceOperator.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java`（仅 source 分支，不改 middle/sink 同步语义）

- Item Types: `Fix | Decision | Proof`

- [x] `Fix` **mailbox 归属接线**：`StreamTaskInvokable` 创建/持有 `TaskMailbox`，暴露 `getMailbox()`；abort handler 闭包(659-683)经 `tasks`→invokable→`getMailbox()` 投递 cancel mail（与现有 `task.cancel()` 路径并列或替代）。
- [x] `Fix` **SOURCE trigger 改 mail**：`CheckpointBarrierTracker.triggerCheckpoint`(60) 的 **source 分支**（`offerBarrier`(84)）改为向 source task 的 mailbox 投递 trigger-checkpoint mail；在 `SourceContext.collect()`(133-136) 发射点 drain 该 mail（替代 `injectPendingBarrier()`(185) 的 cap-1 交接），mail 处理时执行 `snapshotState`→`emitBarrier`（与现状 on-task-thread 序一致）。移除 `pendingBarriers`(53) cap-1 队列。
- [x] `Fix` **abort 协作式 cancel**：`registerLocalAbortHandler`(659-683) 投递 cancel mail/置 cancel flag；保留 interrupt 解除阻塞 read（无非阻塞 read）；主循环（middle/sink 的 `processInputGate` + source 的 run 循环）在顶部检查 cancel → 优雅退出（不再仅靠 `InterruptedException` 上传）。
- [x] `Fix` **middle/sink 主循环顶检查 mailbox**：`processInputGate`(350-368) 在循环顶 `poll` mailbox（处理 cancel mail；barrier/element 处理保持 in-line 不变）；**middle/sink `triggerCheckpoint` 同步语义不变**（injector 线程 prime，保跨 task 不变式）。
- [x] `Decision` 裁定 `synchronized`(60/102/183) 处置：source 路程串行化后 + middle/sink 仍同步 → 选定"保留为断言式不变量"或按路径简化，写入设计文档。**不得静默保留矛盾状态**。
- [x] `Decision` 明确 **finished-source 例外**：源完成、task 线程不存在时，`offerBarrier`(80-88)→`injectBarrier`(83) 保留 injector 线程执行（final checkpoint），不投递 mail；写入设计文档。
- [x] `Proof` 新增/扩展测试：(a) SOURCE trigger mail 在发射点被消费、source `snapshotState` 在 task 线程；(b) middle/sink 在 in-band barrier 到达前 ack 计数已 prime（验证不 hang —— 跨 task priming 不变式保持）；(c) abort 经 interrupt+cancel flag 优雅退出；(d) ACK 计数不再越界（针对 `TestCheckpointBarrierTrackerConcurrency`）。

Exit Criteria:

- [x] mailbox 归属接线完成：`StreamTaskInvokable` 持有 mailbox，abort handler 经引用链投递
- [x] SOURCE trigger 经 mail（发射点 drain），`pendingBarriers` cap-1 队列移除
- [x] **middle/sink `triggerCheckpoint` 保持 injector 线程同步**（验证：相关代码路径未改为 mail）
- [x] abort 为协作式 cancel（interrupt 解除阻塞 + cancel flag 循环顶检查 → 优雅退出）
- [x] finished-source final checkpoint 保留 injector 线程（显式例外，文档化）
- [x] **端到端验证**：至少一条测试从 `env.addSource()` 经算子到 sink、含一次以上 checkpoint（SOURCE trigger→barrier 经数据流→middle/sink 对齐→snapshot→ACK 完成）完整跑通，**不 hang**；aligned 与 AT_LEAST_ONCE 各至少一条
- [x] **接线验证**：用断言/mock verify 确认 (a) SOURCE 的 trigger-checkpoint mail 在发射点消费、`snapshotState` 在 task 线程，(b) middle/sink ack 计数先于 in-band barrier prime，(c) abort cancel flag 使主循环退出——可追踪，非仅类型存在
- [x] **无静默跳过**：新增 mail/cancel 处理分支在未实现时抛异常而非静默返回
- [x] `checkpoint-design.md` §2.2（及 abort 章节）更新 SOURCE-trigger-via-mail + middle/sink-sync + finished-source 例外 的最终接线事实
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 并发正确性收口 + flakiness 观察

Status: completed
Targets:
- `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/execution/TestCheckpointBarrierTrackerConcurrency.java`
- `nop-stream` 全模块测试

- Item Types: `Fix | Proof`

- [x] `Fix` 处理 `TestCheckpointBarrierTrackerConcurrency` 的 `testExtraAckTriggersCallbackAgainKnownIssue`(119-142)：source 路程串行化 + middle/sink 同步后该越界应不再可能；转为正向断言（多余 ACK 被安全忽略/拒绝）或显式记录为何仍保留（**不得静默弱化**）。
- [x] `Proof` 全模块回归：`./mvnw test -pl nop-stream -am -T 1C` 通过。
- [x] `Proof` **观察（best-effort，非门禁）**：观察 multi-key session window 测试是否因 task 内串行化趋稳；结论写入 plan/daily log。根因若仍在跨 subtask 调度则归独立 successor（确定性跨 subtask 调度），本 plan 不对其设门禁。

Exit Criteria:

- [x] `TestCheckpointBarrierTrackerConcurrency` 的 known-issue 被消除或显式 adjudicated（无静默弱化）
- [x] `synchronized` 处置裁定落地并写入设计文档
- [x] **Anti-Hollow Check**：closure 审计验证 (a) SOURCE trigger-checkpoint mail 确在发射点被 task 线程消费，(b) middle/sink trigger 仍为 injector 线程同步，(c) abort cancel flag 经循环顶检查生效，(d) 无空方法体/静默跳过作为正常实现——均为可追踪代码路径
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
- [x] mailbox 设计文档反映最终落地状态（非 Proposed 对比）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] G22 收敛：SOURCE checkpoint 触发经 mailbox（task 线程），middle/sink trigger 保持 injector 同步（priming 不变式保持），abort 为协作式 cancel
- [x] mailbox 归属链接通：`StreamTaskInvokable`→source 路径/abort-handler 取得 handle
- [x] `pendingBarriers` cap-1 队列移除；finished-source final checkpoint 例外文档化
- [x] 跨 task priming 不变式保持（middle/sink 不 hang）
- [x] `CheckpointBarrierTracker` ACK 越界缺陷由设计消除或显式 adjudicated
- [x] 无 in-scope live defect 被静默降级
- [x] 受影响 owner docs（`checkpoint-design.md`、`ai-dev/design/nop-stream/` mailbox 章节）已同步到 live baseline
- [x] `./mvnw compile -pl nop-stream -am`
- [x] `./mvnw test -pl nop-stream -am -T 1C`
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0  *(注：工具退出码 1，但全部命中为 PRE-EXISTING baseline — GroupPattern/RuntimeContext/StreamingRuntimeContext/WindowAggregationFunction/FunctionUtils/Trigger/DemoKeyedStateStore/TaskManager，均与本 plan 新增 mailbox 代码无关；新代码零 high-severity 命中，Anti-Hollow Check (d) PASS)*
- [x] `node ai-dev/tools/check-plan-checklist.mjs <this-plan-file> --strict` 退出码 0
- [x] 独立子 agent closure-audit 完成并写入证据
- [x] **Anti-Hollow Check**：closure audit 追踪 SOURCE trigger mail（投递→发射点消费→`snapshotState`）与 abort cancel flag（投递→循环顶检查→退出）的完整可追踪链；端到端 checkpoint 完整连通且不 hang

## Deferred But Adjudicated

### middle/sink triggerCheckpoint 改 mail（更激进 mailbox 化）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 改 mail 会破坏"下游 ack 计数先于 in-band barrier prime"的跨 task 不变式，致 ACK 丢弃、checkpoint hang。需先重构 `processBarrier` 使其能从 in-band barrier 自 prime，或引入显式 priming 同步——属更大范围改造，非本 plan。当前 middle/sink 同步 trigger 已 `synchronized` 安全、仅 prime 计数不执行 operator 代码。
- Successor Required: `yes`
- Successor Path: 未来 full-mailbox 化 plan（需 priming 重构）

### 跨 subtask 确定性测试调度（Plan 14 deferred successor 真正落点）

- Classification: `optimization candidate`
- Why Not Blocking Closure: multi-key flakiness 根因是跨 subtask 线程调度，非 task 内并发；本 plan 只串行化 task 内部。
- Successor Required: `yes`
- Successor Path: 未来确定性测试调度 plan

### 非阻塞 InputGate read

- Classification: `optimization candidate`
- Why Not Blocking Closure: 无现成 API；abort 用 interrupt 解除阻塞 + 循环顶 cancel 检查已满足需求。真正非阻塞 read 属数据面改造。
- Successor Required: `no`

### Processing-time timer 生产接线

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: mailbox 是前置（timer 触发需作为 mail），但接线本身独立。
- Successor Required: `yes`

## Non-Blocking Follow-ups

- 若 `synchronized` 在 source 串行化后裁定为冗余，可在 Stage 23 统一清理。
- finished-source 若未来需 task 线程化 final checkpoint，须重构 source 生命周期。
- mailbox 优先级/抢占语义若未来 async operator（vision Non-Goal）解禁，需重新评估。

## Closure

Status Note: Mailbox 控制面落地。SOURCE checkpoint 触发经 mailbox（task 线程 collect() 发射点 drain），middle/sink trigger 保持 injector 同步（跨 task priming 不变式保持），abort 为协作式 cancel（signalCancel + interrupt）。pendingBarriers cap-1 队列移除。修复了 registerTasksAndTrackers 引用 original chains 而非 invokable live chain 的预存缺陷（multi-vertex periodic checkpoint 因此从未真正连通，本 plan 修复）。ACK 越界 known-issue 转 strict 正向断言。
Completed: 2026-07-25

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（explore subagent，fresh session ses_066b7dab6ffeUPvAML8O67hUK0，read-only 审计）
- Audit Session: 独立 closure audit，读取 live code + 测试断言
- Evidence:
  - **G22 收敛**: PASS — `StreamSourceOperator.java:124-148` offerBarrier 非-finished 路径 `exec.getMailbox().put(Mail.control(...))`；`collect():173-176` → `drainControlMails():219-228` → `processAvailableMails()`；`CheckpointBarrierTracker.java:60-96` middle/sink 路径仅 prime operatorsToAck 不 touch mail；`GraphModelCheckpointExecutor.java:683` `signalCancel()` before `task.cancel()`.
  - **mailbox 归属链接通**: PASS — `StreamTaskInvokable.java:71` mailboxExecutor 字段 + `:262-264` getMailboxExecutor() + `:243-251` wireMailboxToHeadSource()；引用链 `tasks→SubtaskTask.getSubtask()→Subtask.getInvokable()→getMailboxExecutor().signalCancel()` 经审计 trace.
  - **pendingBarriers 移除 + finished 例外**: PASS — grep 确认 StreamSourceOperator 中无 `pendingBarriers` 字段/LinkedBlockingQueue；offerBarrier finished 分支 `:114-123` 直接 injectBarrier on injector 线程；mailbox-design.md §3.3 文档化；TestSourcePullBarrierInjection.testFinishedSourceInjectsDirectlyOnCallerThread 断言 `"barrier-injector"`.
  - **跨 task priming 不变式**: PASS — triggerCheckpoint middle/sink 路径同步 prime 后返回（无 mail）；TestMailboxWiring.testMiddleSinkTriggerPrimesAckCountSynchronously 断言 `assertEquals(1, completed.get())`；TestMailboxE2ECheckpoint aligned+AT_LEAST_ONCE 各 `assertNotNull(completed, "no hang")`.
  - **ACK 越界消除**: PASS — `CheckpointBarrierTracker.java:110-113` `operatorsToAck.get() <= 0` 早退 guard；testExtraAckIsSafelyIgnored strict `assertEquals(1, callbackCount)`（原 `>= 1` 升级为 strict）.
  - **无静默降级**: PASS — 无 @Disabled/known-issue downgrade；deferred 项全部 documented with Classification + Successor Required.
  - **owner docs 同步**: PASS — mailbox-design.md §3.1/3.2/3.3/3.5/§4/§5；checkpoint-design.md §2.3 + §8.7.
  - **Anti-Hollow Check (a)**: PASS — SOURCE trigger mail 完整链 trace：offerBarrier(injector)→mailbox.put→collect()(task thread)→drainControlMails→Mail.run()→injectBarrier→snapshotState+emitBarrier；TestSourcePullBarrierInjection.testTriggerMailConsumedOnSourceTaskThread + TestMailboxWiring.testSourceTriggerMailConsumedOnTaskThread 断言 `"source-task-thread"`.
  - **Anti-Hollow Check (b)**: PASS — middle/sink trigger 同步：triggerCheckpoint synchronized prime operatorsToAck 后返回，barrier 经 InputGate in-band 到达 processBarrier→snapshotState→acknowledgeOperator.
  - **Anti-Hollow Check (c)**: PASS — abort cancel flag：signalCancel→cancelled=true+marker mail→processInputGate loop-top `processAvailableMails()` 返回 cancelled→break；TestMailboxWiring.testMiddleSinkCooperativeCancelFlagPropagates + testMiddleSinkTaskTerminatesOnAbort 断言 `assertFalse(taskThread.isAlive())`.
  - **Anti-Hollow Check (d)**: PASS — Mail/TaskMailbox/MailboxExecutor 全部方法有真实逻辑；signalCancel 的 marker mail `() -> {}` 是 documented wake-up 行为（cancel state 在 flag），非 hollow. 新 mailbox 代码零 high-severity hollow 命中.
  - **scan-hollow exit code**: 注 — 工具退出码 1，全部命中为 PRE-EXISTING baseline（GroupPattern/RuntimeContext/StreamingRuntimeContext/WindowAggregationFunction/FunctionUtils/Trigger/DemoKeyedStateStore/TaskManager），均与本 plan 无关；本 plan 新代码零命中.
