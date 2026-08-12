# 2 Checkpoint 与恢复路径修复（区域重启重接管线 + manifest 恢复 ID 推进 + 窗口状态恢复 + alignment 竞态）

> Plan Status: active
> Last Reviewed: 2026-08-13
> Draft Review: 2 轮独立子 agent 对抗性审查通过（round 1：1 Blocker（Phase 4 单一发射丢 barrier N+1）+ 4 Major（E2E 公共入口 / replace 语义 / 条件式 deferred / 测试构造）全部修复；round 2：1 Major（Phase 3 E2E 重启路径防 RocksDB 假绿）+ 6 Minor 全部修复，verdict 可转 active）
> Source: `ai-dev/audits/2026-08-12-1217-open-audit-nop-stream-invariant-loop.md` P0-02 / P0-03 / P1-01 / P1-05
> Related: `2026-08-13-0132-1-...`（运行时服务接线，独立面）；`2026-08-13-0132-3-...`（算子/部署契约，独立面）
> Mission: nop-stream-invariant-loop

## Purpose

修复 checkpoint 与恢复路径的四处确定性缺陷：(1) `SupervisionLoop.rebuildTask` 区域级重启后重建任务不接 checkpoint 管线 → 重启后 checkpoint 永久超时、作业必死（P0-02）；(2) EpochManifest 恢复路径不推进 checkpoint ID 计数器 → 新 checkpoint 落影子窗口、崩溃回退丢数据（P0-03）；(3) 窗口 aggregate/reduce 状态经无参反射重建 → checkpoint 后重启必然恢复失败（P1-01，Memory + RocksDB 双后端）；(4) `InputGate.markFinishedChannel` 桶序取"首个完成" alignment → 并发下泄漏兄弟 alignment、永久屏蔽超时/降级门（P1-05）。

## Current Baseline

> 已核对 live repo（2026-08-13）。

- **P0-02（实测）**：`SupervisionLoop.java:679-701`（`rebuildTask`）构建新 `StreamTaskInvokable` 全程无 `setBarrierTracker` / coordinator `registerTask` / participant 注册；`StreamTaskInvokable.java:255-259` `setBarrierTracker` 是唯一 `setupSnapshotCallbacks()` 入口（tracker != null 才接线，否则算子 `snapshotCallback` 为 null，`AbstractStreamOperator.java:400` 静默跳过 ACK）；`GraphModelCheckpointExecutor.java:744-756` 周期 barrier 只注入启动时的 `allInvokables`（:631-697 `registerTasksAndTrackers` 构造）。`SupervisionLoop.java:699` 重启复用同一 taskLocation → coordinator `tasksToAck` 仍含该位置 → 新 checkpoint 永远无法 full-ACK → `CheckpointCoordinator.java:997` timeout abort → `registerLocalAbortHandler`（GraphModelCheckpointExecutor:834-894）`abortMarked` → `checkAbortMarker`（:888-894）抛 `ERR_STREAM_CHECKPOINT_ABORTED` → 作业失败。现有 E2E `TestSupervisionLoopReconnectE2E.java:161-163` 以 `null` coordinator/checkpointPlan 运行——checkpoint 组合零测试覆盖。**注意**：`registerTasksAndTrackers` / `startBarrierScheduler` / `registerLocalAbortHandler` / `checkAbortMarker` 均为 `GraphModelCheckpointExecutor` 的 **private static**——区域重启+checkpoint 组合 E2E 无法经 `SupervisionLoop.run` 直调接线，须走公共入口（见 Phase 2）。
- **P0-03（实测）**：`GraphModelCheckpointExecutor.java:946-970` manifest 恢复成功即 return，不触碰计数器；`CheckpointCoordinator.java:896-900` 计数器推进只在 `restoreFromCheckpoint`（CompletedCheckpoint 路径）；`restoreLatestEpochManifest`（:1269-1271）仅加载。每个 checkpoint 完成都写 manifest（`storeEpochManifest` 调用点在 **CheckpointCoordinator.java:529/:560/:612** 三路径）→ manifest 优先于 checkpoint 恢复 → 计数器从 0 重新开始 → 新 ID 全 < 恢复 epoch R → `loadLatestEpochManifest`/`getLatestCheckpoint` 按 max-ID 选择（LocalFileCheckpointStorage:525-533；JDBC `ORDER BY ... DESC LIMIT 1`）→ 恒返回过期 epoch；ID==R 时 ATOMIC_MOVE REPLACE（LocalFileCheckpointStorage:500）覆写。崩溃于影子窗口（0..R-1）→ 回退到 R，期间已"完成"checkpoint 不可恢复。`EpochManifest.getEpochId()` 已存在（executor :949/:956 已在用）；**缺口 = `checkpointIdCounter` 为 private（CheckpointCoordinator:123），需新增公共推进方法**（如 `advanceCheckpointIdCounter(restoredId)`，与 :896-900 同逻辑）。
- **P1-01（实测）**：`MemoryStateSerDe.java:430-435`（`aggregateFunctionType` 类名 → `getDeclaredConstructor().newInstance()` 无参重建）；`RocksDBSnapshotSerDe.java:695-700` 同病；`WindowOperatorBuilder.java:205-206` `reduceFunctionAsAggregate` 把用户 `ReduceFunction` 包成捕获型匿名类（构造器带参）→ 恢复时 `NoSuchMethodException` → `WindowOperator.open()` 恢复失败。全仓无 descriptor 路径 snapshot→restore 测试（`TestTriggerAccumulatorsCheckpoint.java:107-127` 走 9 参 null-descriptor 构造器；`TestCheckpointRecovery` 仅 ValueState）。descriptor 路径是 `WindowedStreamImpl` 全部 API 默认路径（`WindowOperatorBuilder.buildWindowOperator` 恒传 stateDesc）。**恢复链（executor → op.restoreState → keyedStateBackend.restoreState → serde）不携带算子上下文**——live 函数注入需跨模块（core serde / runtime operator）设计新 API，属接线工程。
- **P1-05（实测）**：`InputGate.java:690-710` `markFinishedChannel` 遍历 `new ArrayList<>(inFlightAlignments.values())`（`ConcurrentHashMap` :99，桶序）——两 alignment 同轮完成时只 remove 第一个访问到的（:698），后访问者 `completed` 已非 null → 泄漏 fullyReceived alignment；泄漏项成为 `oldestAligning()`（:722-730 取 min id）→ timeout 检查（:491-504）要求 `oldest.receivedChannels.size() < channels.size()` 恒 false → `barrierAlignmentTimeout` 与 `switchToUnalignedAndEmit` 永久失效 + barrier 被静默丢弃 → pending N 永不 full-ACK → 超时 abort。**InputGate 不读 `maxConcurrentCheckpoints`（那是 coordinator 侧 gating）**——确定性复现应走 InputGate 单元测试（先例 `TestInputGateMultiEpochBarrier` 已存在）：向 N 个 channel 注入 barrier N 与 N+1（两者都缺同一个 channel），再 finish 该 channel → 同轮完成。默认 `maxConcurrentCheckpoints=1` 不可达（P1 不升 P0 依据）。
- **门禁基线（实测）**：JUnit 门禁 10 类 / 102 tests / 0 failures；mjs `all` exit 0（pin 0）；全量 2833 tests / 0 failures（2026-08-12 Cycle 2 / I5 基线，working tree clean）。
- **真正剩余的 gap**：四缺陷均 live；checkpoint+重启组合、descriptor 路径恢复、多 alignment 并发竞态三类路径均无生产 E2E / 参数化测试覆盖。

## Goals

- `rebuildTask` 重建任务完整重接 checkpoint 管线（`setBarrierTracker` + `setupSnapshotCallbacks` + coordinator `registerTask`/participant/listener 重注册 + **替换** barrier 注入列表中的旧 invokable），区域重启后 checkpoint 正常 full-ACK（P0-02 关闭）。
- manifest 恢复成功后 `checkpointIdCounter` 单调推进（`max(restoredId+1, current)`，与 `restoreFromCheckpoint` :896-900 同逻辑），新 checkpoint ID > 恢复 epoch（P0-03 关闭）。
- 窗口 aggregate/reduce 状态恢复**裁定方案 1：恢复时优先复用 live descriptor 持有的 aggregate 函数实例**（旧快照亦恢复——方案 2/3 只救新快照），Memory + RocksDB 双后端 descriptor 路径 snapshot→restore 往返测试绿（P1-01 关闭）。
- `markFinishedChannel` 收集完整后**按 checkpointId 顺序逐个发射全部完成项、发射后移除**，多 epoch 并发在途 + channel finish 场景不再泄漏 alignment、不丢 barrier、不屏蔽超时门（P1-05 关闭）。
- 全量回归绿 + 既有门禁零命中（只增不弱化）。

## Non-Goals

- **不做 P1-02 / P0-01 运行时服务接线**（plan 1）；**不做 CEP timer 注册表 / fan-out / beans.xml / merge fail-fast 测试**（plan 3）。
- **不改 checkpoint 存储格式 / 线协议**（manifest 结构、CompletedCheckpoint 序列化格式保持不变——P0-03 只推进计数器，不重设计存储）。
- **不新增/不改既有不变式门禁语义**。
- **不做 P2 批次修复**（triggerAccumulators 裁剪 / checkpointSuccessMap 裁剪 / getNodeLease 防御等 → Follow-up Backlog）。

## Scope

### In Scope

- `SupervisionLoop.rebuildTask` checkpoint 管线重接（含 barrier 注入列表替换语义）+ 区域重启×checkpoint 全链路 E2E（先红后绿，走 `GraphModelCheckpointExecutor.executeWithCheckpoint` 公共入口）。
- manifest 恢复路径计数器单调推进（新增 coordinator 公共推进方法）+ "manifest 恢复 → 再触发 checkpoint → 新 ID > R" 测试。
- 窗口 descriptor 路径恢复修复（方案 1：live 函数复用；Memory + RocksDB 双后端）+ snapshot→restore 往返测试。
- `InputGate.markFinishedChannel` 按序发射全部完成项修复 + "两 alignment 同轮完成"确定性单元测试（对齐 `TestInputGateMultiEpochBarrier`）。
- 文档收口：`docs-for-ai/04-reference/source-anchors.md`（STRM 锚点如有漂移则同步）+ `ai-dev/logs/`。

### Out Of Scope

- 恢复路径之外的 checkpoint 机制改造（存储格式、线协议、`HG-01` 等人工确认门事项）。
- P2 批次修复（Follow-up Backlog）。
- 运行时服务接线（plan 1）、算子/部署契约（plan 3）。

## Execution Plan

### Phase 1 - P0-03：manifest 恢复后 checkpoint ID 计数器单调推进

Status: planned
Targets: `nop-stream/nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java`（新增公共推进方法，:896-900 逻辑）；`nop-stream/nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java`（:946-970 manifest 恢复分支调用推进）

- Item Types: `Fix | Proof`
- [ ] **修复（Fix）**：`CheckpointCoordinator` 新增公共方法推进 `checkpointIdCounter`（`max(restoredId+1, current)`，与 :896-900 同逻辑）；`GraphModelCheckpointExecutor` manifest 恢复成功路径（:946-970）调用该方法（`epochManifest.getEpochId()` 已可消费，无需扩展返回结构）。**边界声明（Anti-Slacking）**：manifest 恢复不装配 `latestCompletedCheckpoint`（`restoreLatestEpochManifest` 仅 load，:1269-1271）属已知行为——崩溃恢复后、首个新 checkpoint 完成前若发生区域重启，`rebuildTask`:565 走"epoch 0 + empty state + full replay"回退（重放覆盖崩溃前已处理数据，有日志非静默）——该边界本 plan 明确 out-of-scope（本 plan 修复面 = 计数器单调推进；full-replay 回退边界登记 Non-Blocking Follow-ups 复探评估）。
- [ ] **测试（Proof，先红后绿）**：新增测试——manifest 恢复（epoch R）→ 再触发 checkpoint → 新 checkpoint ID > R；且多次重启不重入影子窗口。修复前红（新 ID ≤ R）、修复后绿。
- [ ] **无静默跳过（Proof，Rule #24）**：恢复路径不静默跳过计数器推进；manifest 加载与计数推进不吞异常。
- [ ] 回归：既有 checkpoint 相关测试全绿（`TestCheckpointRecovery` 等）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] manifest 恢复后新 checkpoint ID > 恢复 epoch 的测试绿（先红后绿证据在案）
- [ ] 计数器推进逻辑与 `restoreFromCheckpoint` 路径一致（`max(restoredId+1, current)` 语义）
- [ ] 既有 checkpoint 测试全绿
- [ ] No owner-doc update required（如无锚点漂移；有则同步 `source-anchors.md`）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P0-02：SupervisionLoop 区域重启重接 checkpoint 管线

Status: planned
Targets: `nop-stream/nop-stream-runtime/.../execution/SupervisionLoop.java`（rebuildTask :679-701 + 可变注入列表访问）；`nop-stream/nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java`（registerTasksAndTrackers :625-697 复用 / barrier 注入列表）；`nop-stream/nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java`（registerTask / participant / listener）

- Item Types: `Fix | Proof`
- [ ] **修复（Fix）**：`rebuildTask` 重建 invokable 后按原任务 wiring 补全：(a) `setBarrierTracker`（触发 `setupSnapshotCallbacks`）；(b) coordinator `registerTask`（taskLocation 复用正确注册）+ participant/CheckpointListener 重注册（**含对旧算子实例的 `removeListener`/对偶移除**——`CheckpointCoordinator.java:246` 存在移除 API；多次重启后 stale listener 不得累积；P0-02 审计风险段的 listener 通知缺口属本项 in-scope，不得降级）；(c) **替换** barrier 注入列表中的旧 invokable（remove old + add new，或显式停用旧 tracker）——append 会留下 phantom in-flight epoch 无界增长 + 死 mailbox 堆积（`SupervisionLoop.run` 需能访问**可变的** `allInvokables` 列表，签名/共享结构变更在本项内裁定；**列表须为线程安全容器**——barrier scheduler 线程 `triggerBarrierOnAllInvokables`（:744）for-each 迭代与 supervision 线程 remove/add 并发会 CME 被 :733-736 catch 吞掉→该 tick barrier 静默丢失，裁定 CopyOnWriteArrayList 或同步包装）。
- [ ] **端到端验证（Proof，Rule #22）**：新增"区域重启 + checkpoint 全链路"E2E——**走 `GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, jobId, config)` 公共入口**（先例：`TestCheckpointAbortWiring`（:124 用该入口断言 abort 抛错）/ `TestMailboxE2ECheckpoint` / `TestE2EMultiVertexCheckpoint`——`SupervisionLoop.run` 直调无法接线，因为 `registerTasksAndTrackers` 等均为 private static）。**时序约束**：慢/无限源 + 具体 `checkpointInterval`/`checkpointTimeout` 配置，确保"重启后第一个 checkpoint"在作业结束前超时 abort 可复现（有界源跑太快会真空通过）；绿态断言 = **可观察的 full-ACK 证据**（完成 checkpoint 数 ≥ 2 或明确 ACK 计数），而非"无异常"。修复前红（重启后 checkpoint 超时 abort）、修复后绿。
- [ ] **接线验证（Proof，Rule #23）**：重建任务在运行时确实收到 barrier（barrier 注入列表包含重建 invokable 且旧 invokable 已移除）、其 `snapshotCallback` 非 null（`setupSnapshotCallbacks` 被调用）——断言 / 计数器在案。
- [ ] **无静默跳过（Proof，Rule #24）**：重建任务接线不静默跳过；`snapshotCallback == null` 的静默 ACK 跳过路径（`AbstractStreamOperator.java:400`）在重建路径不再可达（或显式失败）。
- [ ] 回归：`TestSupervisionLoopReconnectE2E` 既有用例 + checkpoint 相关测试全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **端到端验证**：区域重启 + checkpoint 全链路 E2E 绿（走 `executeWithCheckpoint` 公共入口；慢源 + 具体 interval/timeout；重启后 checkpoint 正常 full-ACK、不超时 abort；可观察 full-ACK 证据在案）
- [ ] **接线验证**：重建 invokable 在 barrier 注入列表内（旧项已替换）+ `snapshotCallback` 非 null（运行时连通证明）
- [ ] participant/CheckpointListener 重注册落地（notifyCheckpointCompleted/Aborted 可达，测试断言在案）
- [ ] 先红后绿证据在案（修复前重启后 checkpoint 超时 abort 可复现——时序约束下）
- [ ] 既有 `TestSupervisionLoopReconnectE2E` 用例全绿
- [ ] `docs-for-ai/` 锚点（STRM 监督重启 / checkpoint 相关）如漂移已同步
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P1-01：窗口 aggregate/reduce 状态恢复（方案 1：live 函数复用，双后端）

Status: planned
Targets: `nop-stream/nop-stream-core/.../state/backend/memory/MemoryStateSerDe.java`（:426-454 / :638）；`nop-stream/nop-stream-rocksdb/.../RocksDBSnapshotSerDe.java`（:695-700）；恢复接线（executor → 算子 open 前注入 live descriptor 的 aggregate 函数；跨模块新 API 属本项设计裁决）

- Item Types: `Decision | Fix | Proof`
- [ ] **裁定（Decision）**：**方案 1 = 恢复时优先使用 live 算子持有的 descriptor 的 aggregate 函数实例**（恢复前由 executor 传入，不得仅依赖类名无参反射）——方案 1 的额外收益：**旧快照（修复前写入）也能恢复**；方案 2/3（序列化函数实例 / 可序列化数据类包装）只救新快照，且改变快照格式。跨模块接线（core serde ↔ runtime operator 传递 live 函数）的 API 形态在本项内裁定（最小侵入：descriptor 在恢复路径携带函数实例或等价传递通道）。裁定写入执行记录。
- [ ] **修复（Fix）**：按裁定实现恢复逻辑——live descriptor 的函数实例在恢复路径被使用；`reduce()`（匿名包装类）+ `aggregate()`（lambda/内部类）在 Memory + RocksDB 双后端都能恢复。
- [ ] **测试（Proof，先红后绿）**：新增 descriptor 路径 snapshot→restore 往返测试——`reduce()`（匿名包装类）+ `aggregate()`（lambda/内部类）各至少一例，双后端（Memory + RocksDB）跑通；修复前红（NoSuchMethodException / 恢复失败）、修复后绿。**"重启"路径裁定（防 RocksDB 假绿）**：本项验证的"重启" = **作业级重启**（同一 checkpoint storage 二次 `executeWithCheckpoint` → manifest/checkpoint 恢复，后端经 config 正确配置）——**不得**用 supervision 区域重启（重建链走 `OperatorChain.deepCopy` → `WindowOperator` copy 构造器 :355-370，**不复制 stateBackend** → open() :418-420 静默回退 `new MemoryStateBackend()`，RocksDB 配置的作业区域重启后实际走 Memory serde 恢复，断言全绿但验证的不是 RocksDB 路径）。若执行中发现区域重启场景确需重建链 state backend 重新供给，该工作显式并入 Phase 2 修复项 (c) 并在 Phase 2/3 边界记录。
- [ ] **接线验证（Proof，Rule #23）**：恢复路径确实把 live descriptor / 函数实例传入恢复逻辑（非仅依赖反射类名）；验证方式 = 恢复测试断言函数实例被复用（如用户字段状态保留断言）。
- [ ] **无静默跳过（Proof，Rule #24）**：恢复失败不静默降级（不吞异常返回空状态）；无参反射路径在不可用时显式失败并给出明确错误。
- [ ] 回归：既有 `TestTriggerAccumulatorsCheckpoint` / `TestCheckpointRecovery` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **端到端验证**：窗口作业（reduce/aggregate）带状态 checkpoint → 重启 → 状态恢复 → 继续处理完整走通（双后端）
- [ ] **接线验证**：live aggregate 函数实例在恢复路径被实际使用（断言在案）
- [ ] 方案 1 裁定记录在案 + 旧快照兼容行为已声明（旧快照可恢复或显式记录行为边界）
- [ ] 先红后绿证据在案（修复前恢复抛 NoSuchMethodException / 函数状态丢失）
- [ ] 既有 checkpoint / 窗口测试全绿
- [ ] No owner-doc update required 或相关设计/锚点文档已同步（跨模块 API 变更需记录）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - P1-05：InputGate.markFinishedChannel 竞态修复

Status: planned
Targets: `nop-stream/nop-stream-core/.../execution/InputGate.java`（:690-710 markFinishedChannel / :722-730 oldestAligning / :491-504 超时门）

- Item Types: `Fix | Proof`
- [ ] **修复（Fix）**：`markFinishedChannel` 收集**全部** fullyReceived 项后，**按 checkpointId 顺序逐个发射**（一次 read() 返回一个，其余进入 pending-emission 队列），**发射后才 remove**——不允许"只发射 min 一个 + remove 全部"，否则 barrier N+1 被 remove 但从未发射 → 下游对 N+1 的 snapshot 永不发生 → tracker 不 ACK N+1 → 与原始缺陷同构的失败（泄漏 N 变成丢 N+1）。
- [ ] **测试（Proof，先红后绿）**：**确定性单元测试**（对齐 `TestInputGateMultiEpochBarrier` 先例，不搭 E2E——InputGate 不读 `maxConcurrentCheckpoints`）——向 N 个 channel 注入 barrier N 与 N+1（两者都缺同一个 channel），再 finish 该 channel → 同轮完成；断言：**两个 barrier 均被 read() 按 checkpointId 顺序返回**、`inFlightAlignments` 无泄漏项、超时门（:491-504）不被屏蔽、**已 fullyReceived 但未发射的 pending alignment 在 `abortBarrierAlignment` 后被丢弃且不阻塞后续发射**。修复前红（泄漏 / 桶序选取 / 丢 barrier 可复现）、修复后绿。
- [ ] **无静默跳过（Proof，Rule #24）**：泄漏路径不静默保留 fullyReceived alignment；超时门不因泄漏项被永久屏蔽。
- [ ] 回归：既有 InputGate / barrier 对齐测试全绿（`TestInputGate*` 系列 + checkpoint 相关）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] "两 alignment 同轮完成"确定性单元测试绿（**两个 barrier 均按序返回** + 无泄漏 + 超时门可用）
- [ ] 先红后绿证据在案（修复前泄漏 / 丢 barrier 可复现）
- [ ] 既有 InputGate / checkpoint 测试全绿
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] P0-02 已修复：区域重启 + checkpoint 全链路 E2E 绿（`executeWithCheckpoint` 公共入口；重启后 full-ACK、不 abort）
- [ ] P0-03 已修复：manifest 恢复后新 checkpoint ID > 恢复 epoch（测试绿）
- [ ] P1-01 已修复：窗口 aggregate/reduce 双后端恢复 E2E 绿（方案 1 裁定落地）
- [ ] P1-05 已修复：双 alignment 同轮完成确定性测试绿（两 barrier 按序发射、无泄漏）
- [ ] 无被静默降级到 deferred / follow-up 的 in-scope live defect（四个 P0/P1 均以 Fix 落地；listener 重注册已 in-scope，无条件式 deferred 项）
- [ ] 接线完整性：重建任务 / 恢复函数实例在运行时调用连通（非仅类型存在）
- [ ] 无静默跳过：恢复路径 / 重启路径无空实现、无静默 ACK 跳过、无静默降级
- [ ] 必要 focused verification 完成（先红后绿证据全部在案）
- [ ] 受影响 owner docs 已同步或明确 No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（含 Anti-Hollow 检查）
- [ ] `./mvnw compile` (`-pl nop-stream -am`)
- [ ] `./mvnw test -pl nop-stream -am -T 1C`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` exit 0（closure 时）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0（closure 时）
- [ ] `node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0（既有门禁零命中）
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（无——本 plan 无 in-scope 延期项；listener 重注册已含于 Phase 2 修复项 (b)，不存在条件式 deferred。）

## Non-Blocking Follow-ups

- P2-01 checkpointSuccessMap abort/fail 路径无界增长（open-audit P2-01）——已确认 live defect（P2 级），Non-Goals 已将其移出本 plan scope，successor = roadmap Follow-up Backlog 对应条目（`2026-08-13` P2 批次登记，条目 ID = open-audit P2-01；触发条件：abort 高频场景复探或类别清扫时）。
- manifest 恢复后 `latestCompletedCheckpoint` 不装配的回退边界（崩溃恢复后首个 checkpoint 完成前的区域重启走 full replay）——本 plan out-of-scope（Anti-Slacking 裁定已记录），复探时评估是否需要"从 manifest 装配 latestCompletedCheckpoint"。

## Closure

Status Note: （完成时填写）
Completed: （完成时填写）

Closure Audit Evidence:

- Reviewer / Agent: （closure audit 时填写）
- Evidence: （closure audit 时填写）

Follow-up:

- （closure audit 时填写）
