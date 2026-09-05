# runtime checkpoint 协调器结构治理（retention I/O 出 monitor + 执行/终止路径克隆收敛）

> Plan Status: completed
> Mission: nop-stream-productization
> Work Item: roadmap item 26
> Last Reviewed: 2026-09-03
> Source: runtime 审计报告 `ai-dev/analysis/2026-09/2026-09-01-nop-stream-runtime-module-audit.md` §2.2 F-A/F-B（审计内部 Follow-up 编号 item 25 = 本 roadmap item 26）；行为级漂移 R-7/R-12 已由 item 8 plan `2026-09-01-0938-3` 修复，本计划只收结构。F16 处置：审计报告无 F16 独立定义（仅 §3.2 行内提及「counter 家族克隆合并（F-B/F16 结构性部分）」），counter 双份克隆已随 R-12 行为修复收口、无剩余结构项——本计划不承载 counter 家族
> Related: `2026-09-03-0830-3-state-serde-dedup-convergence.md`（克隆收敛 + 行为零变更回归面方法论先例）；`2026-09-03-1723-3-checkpoint-manifest-versioning-checksum.md`（同域最近变更，行号基线）

## Purpose

把 runtime 审计确认的两处结构性缺陷收口：

1. retention GC 的存储 I/O（getAllCheckpoints/deleteCheckpoint）在持有 coordinator monitor 时同步执行，违背 async-persist「I/O 出 monitor」设计目标，拖慢 checkpoint completion 关键路径。
2. `GraphModelCheckpointExecutor.executeWithCheckpoint` 三 overload 与 `JobCoordinator` terminate 三联的高比例克隆——R-7 的指纹缺失即历史克隆漂移产物，克隆不收敛则漂移会复发。

主线是**行为零变更的结构治理**；retention I/O 出 monitor 的时序变化是审计既定方向，为本计划声明内的唯一行为增量。

## Current Baseline

（live 核对 2026-09-03，行号为当前代码）

- retention I/O 在 monitor 内执行：`cleanupOldCheckpoints()`（CheckpointCoordinator.java:1197-1214，`getAllCheckpoints` + 逐 checkpoint `deleteCheckpoint`）由 `onCompletePersistSuccess`（段3a，:817-869）在 :852 持 monitor 调用。段3a 有三条进入路径：sync-fallback（`completePersistSynchronously` :562——`asyncSnapshotEnabled=false` 时全部 I/O 有意留在 monitor 内，:536-540 注释钉定 pre-async 行为）、async 完成回调（:615-617 重入 monitor）、incremental（:672-676）。
- segment discard 已正确 offload 到 persist executor（`gcSegmentsForCheckpoint` :1223-1259，经 :193 懒创建 executor / `getOrCreatePersistExecutor` :899；其内存部分 :1227-1234 registry unregister 随 cleanup 在 monitor 内），但 checkpoint 行删除未随行。
- `GraphModelCheckpointExecutor` 三个公开 overload（GraphModelCheckpointExecutor.java:104-152 JobGraph 形态 / :159-221 PartitionedPlan 默认配置 / :223-297 PartitionedPlan 用户配置）~85-90% 克隆：build → coordinator → register → scheduler → restore → submit → finally(shutdown + closeBufferPool) 脚手架逐字重复。**真实差异清单（收敛必须参数化的全部维度）**：① config 来源/解析（overload 3 的 userConfig 合并；jobId/pipelineId/jobName 解析不对称——overload 1 走 `resolveJobId`/`resolvePipelineId` 无默认值且 jobName 为显式参数，overload 2/3 从 partitionedPlan 取值并默认 `"job-0"`/`"pipeline-0"` 且多一步 `buildJobGraphFromStreamModel`）；② fingerprint 设置（overload 1 无）；③ 执行计划构建分叉——overload 1 走 4-arg `GraphExecutionPlan.build`（**不透传 unaligned checkpoint 配置**），overload 2/3 走 6-arg build（透传 `isUnalignedCheckpointEnabled()`/`getUnalignedThreshold()`）；④ restore 参数（overload 1 传 null，2/3 传 streamModel）；⑤ `validateUnalignedConfig` 与 `resolveBarrierAlignment` 调用顺序相反（行为不可观测的纯顺序差异，统一顺序不视为行为变更）。另 triggerSavepoint/executeWithSavepoint（:316-375/:377-426）同脚手架（审计 F-B 计 5 处逐字 finally 块）。
- `JobCoordinator.terminateDrain/terminateSuspend/terminateExportSavepoint`（JobCoordinator.java:1875-1953）~90% 三联克隆：tryTrigger → 构造 barrier → `sendBarrierToAllTaskManagers` → future.get(terminationCheckpointTimeoutMs) → 事件 → 收尾。差异：CheckpointType、日志文案、事件负载、health 转换与终止动作（Drain/Suspend 有 `health.onFinished` + JOB_FINISHED 事件 + stop()；Export 三者皆无，作业继续运行）。
- R-7（savepoint 指纹缺失）/ R-12 行为漂移已修复（item 8 plan），不在本计划范围。
- 全量回归基线绿（最近全量 3386/0/0/25，item 25 closure 留档）。

## Goals

- retention GC 的 getAllCheckpoints/deleteCheckpoint 不再在持有 coordinator monitor 的调用栈内同步执行（指 async 完成路径；sync-fallback 路径按 D1(d) 裁定——「保持同步」为该模式声明语义的可接受裁定态）；retention 语义（maxRetained 约束）最终保持。
- checkpoint completion 关键路径不再被慢 retention I/O 阻塞（可观测：注入慢存储时后续 checkpoint 触发/推进不受阻）。
- GMCE 三 overload 公开签名保持不变（调用方零改动），内部收敛为单一骨架。
- JC terminate 三联收敛为单一参数化实现，Drain/Suspend/Export 三模式行为语义逐项钉定不变。

## Non-Goals

- 不改变 checkpoint 触发/节流/abort/commit 语义与配置面。
- 不做 cancelTask fencing（plan `2026-09-03-1951-2`）与 remote-deploy 数据面（plan `2026-09-03-1951-3`）。
- 不做 GC 调度策略优化（保留策略本身、触发时机不变）。

## Scope

### In Scope

- `CheckpointCoordinator`（retention I/O 出 monitor；completion 路径时序）
- `GraphModelCheckpointExecutor`（三 overload 骨架收敛；savepoint 族同脚手架归属裁定）
- `JobCoordinator`（terminate 三联收敛）
- 上述变更的 focused 测试 + 全量回归面 + 工具门禁

### Out Of Scope

- savepoint 族（triggerSavepoint/executeWithSavepoint）的任何行为变更——仅裁定结构收敛归属
- LOCAL/DISTRIBUTED 模式语义差异调整
- retention 策略/配置项新增

## Execution Plan

### Phase 1 - 裁定与回归面固化

Status: completed
Targets: 本 plan（必要时 `ai-dev/design/nop-stream/checkpoint-design.md` 增补节）

- Item Types: `Decision | Proof`

- [x] D1 retention I/O 出 monitor 的机制裁定。候选：① 沿用既有 persist executor 异步提交（与 segment discard 同通道）；② completion 路径 monitor 外执行。裁定必须回答：(a) 越界清单计算（纯内存读）与 I/O 执行的切分点；(b) 异步化后 retention 最终一致的可观测判据；(c) I/O 失败的重试/告警语义（不得静默吞）；(d) **sync-fallback 路径的 retention 处置**——`asyncSnapshotEnabled=false` 模式设计上把全部 I/O 留在 monitor（pre-async 行为钉定），retention 在该模式保持同步还是一并异步化，二态裁定（一并异步化 = 行为变更，须显式声明）；(e) cleanup 相对段3a 后续步骤（`retryFailedCommits` :855 / `notifyParticipantsFinishCommit` :859 / `notifyCheckpointCompleted` :861）的顺序依赖论证（retention 只删最旧、不触及当前 epoch 的论证须落档）；(f) 异步化后多次 completion 触发并发 retention 的互斥/串行化机制，及 `gcSegmentsForCheckpoint` 内存部分（:1227-1234）留 monitor 还是随 I/O 移出的归属裁定。
- [x] D2 savepoint 族（triggerSavepoint/executeWithSavepoint 同脚手架、审计 F-B 提及 5 处逐字 finally 块）归属裁定：纳入本计划收敛，或移入 Deferred But Adjudicated 附分类与理由。二态，不允许「如有时间」。
- [x] D3 行为零变更回归面清单固化：既有 terminate/checkpoint 相关测试清单（逐文件引用，rg 可验证）+ 需新增 focused 测试清单。

#### D1 裁定（2026-09-03，live 核对后）

**机制 = 候选①：沿用既有 persist executor 异步提交**（与 segment discard 同通道）。completion 路径 monitor 内仅执行「调度动作」（原子标志 CAS + `executor.submit`，两者均无 I/O）；retention 全量（`getAllCheckpoints` + 逐 checkpoint `deleteCheckpoint` + `gcSegmentsForCheckpoint`）在 persist executor 线程无 monitor 执行。

- **(a) 切分点**：题设「越界清单计算 = 纯内存读」不成立——清单计算本身依赖 `getAllCheckpoints(jobId)`（磁盘目录枚举 + 逐文件反序列化，正是本次要卸载的 I/O 主体），coordinator 内存中无可替代的已完成 checkpoint 全集（`latestCompletedCheckpoint` 仅最新一条；另建内存全集需启动期 seed 读，引入双真值漂移面）。因此切分点裁定为「**整个 cleanup 出 monitor**」：monitor 内零 I/O、零清单计算；唯一留在 monitor 内的是非阻塞调度本身。
- **(b) 最终一致可观测判据**：完成 N > maxRetained 个 checkpoint 后，有界超时内轮询谓词 `storage.getAllCheckpoints(jobId).size() <= maxRetained` 成立（focused 测试 A 钉定，超时即 fail、禁止固定 sleep）；生产观测面 = DEBUG 日志 `Deleted old checkpoint {id}` + 存储目录 checkpoint 行数。
- **(c) I/O 失败语义**：保留既有聚合 try/catch + `LOG.warn("Failed to cleanup old checkpoints", e)`（不静默吞）；**不新增同步重试机制**——retention 在每个后续 checkpoint completion 自然重触发，transient 失败由 next-completion 天然重试自愈；持久失败经 WARN 日志可观测（与现状语义一致，仅执行线程变化）。
- **(d) sync-fallback 二态裁定：保持同步**。`asyncSnapshotEnabled=false` 的声明语义即「全部 I/O 留在 ACK 线程 monitor 内」（CheckpointCoordinator :536-540 注释钉定 pre-async 行为）；将该模式 retention 异步化 = 未声明的行为变更，驳回。实现：`onCompletePersistSuccess` 增加 `asyncRetention` 布尔参数——`completePersistSynchronously` 传 false（`cleanupOldCheckpoints()` 内联执行，行为逐字不变）；`executePersistAsync` / `executeIncrementalPersistAsync` 传 true（异步调度）。
- **(e) 顺序依赖论证**：删除集 = `getAllCheckpoints` 按 checkpointId **降序**排列（LocalFileCheckpointStorage :183 `result.sort((a,b) -> Long.compare(b.getCheckpointId(), a.getCheckpointId()))`）后 index ≥ maxRetained 的**最旧**行；当前 epoch 恒为全集最新（`CheckpointIDCounter` 单调递增 + `advanceCheckpointIdCounterAfterRestore` 保证 restore 后新 id 严格大于存量 durable epoch，P0-03 修复钉定），**永不在删除集**。段3a 后续步骤（`retryFailedCommits` / `notifyParticipantsFinishCommit` / `notifyCheckpointCompleted`）只触及当前 epoch id、participants/listeners 注册表、`failedCommitParticipants`——与删除集触及的状态（旧 epoch 存储行 + 旧 epoch 的 GC-map segments + segment 文件）**不相交**，双向均无顺序依赖；异步化仅改变相对时序，不改变可达状态集。
- **(f) 串行化 + 内存部分归属**：串行化 = per-coordinator `AtomicBoolean` in-flight 守卫（CAS）+ **trailing re-run 合并**：触发时已有 retention 在跑 → 置 `retentionRecheckNeeded` 标志；运行任务的 finally 复查该标志并再调度一轮。保证 (i) 同一时刻至多一个 retention 任务执行（消除并发重复 `deleteCheckpoint` 竞争——池大小 >1 时两个 retention 可能对同一行重复删除），(ii) **每个完成事件之后至少存在一个更晚启动的 retention 轮次**（trailing 语义：最后一个 completion 的 retention 不因合并而丢失，最终一致成立；证明：触发要么直接 submit（启动时刻 ≥ 触发时刻），要么置 recheck 标志，而 recheck 只会被一个已运行任务的 finally 消费并转化为再一次 submit，该 submit 启动时刻 > 该 finally 时刻 ≥ 触发时刻；completion 在段2 durable 之后才触发调度，故 trailing 轮次必然可见该行）。`gcSegmentsForCheckpoint` 内存部分（registry unregister + GC-map remove）**随 I/O 一并移出 monitor**：`checkpointSegments` 为 ConcurrentHashMap；`SharedStateRegistryImpl` 为 ConcurrentHashMap per-key 原子实现（`registry.compute` 单键原子）；且增量路径已在无 monitor 的 persist executor 线程上调用 `registry.register`/`unregister`（`executeIncrementalPersistAsync`→`buildAndMaterializeSegments`、`releaseIncrementalSegments`）——无 monitor 线程安全性为既定设计，非新增假设；内存部分与 I/O 部分同批执行也避免了拆分两段的中间状态。**已知残余竞态（诚实记录，watch-only）**：checkpoint X 的存储行已落、其 GC-map 条目（段3a monitor 块首个动作 `checkpointSegments.put`）尚未落地的窗口内，并发 retention 可能删 X 行而跳过 X 的 segment GC → 引用计数/SST 文件残留至下次重启（`restoreSharedStateRegistry` + `cleanupOrphanSegments` 回收）。窗口 = X 段2 完成到 X 段3a put 的微秒级间隙，且需 ≥ maxRetained 个更新 epoch 在该窗口内 durable 才会把 X 挤入删除集；`restoreSharedStateRegistry` 无 main-scope 调用点（rg 验证，仅测试直接调用），重建期与 retention 并发的路径在当前接线下面不存在。

#### D2 裁定（2026-09-03）

**移入 Deferred But Adjudicated**（分类 `out-of-scope improvement`，详见该节「savepoint 族脚手架收敛」）。理由：triggerSavepoint / executeWithSavepoint 的 try-body 本质异构（savepoint 触发 + KeyGroup 物化 + 显式存储返回 path vs savepoint 恢复 + final checkpoint），共享面仅 setup + finally 脚手架；纳入全骨架收敛需引入 body-strategy 参数化，其行为零变更验证面与主线三联收敛（差异清单①—⑤封闭、参数矩阵清晰）不对称，风险收益比不利；R-7 已修复该族唯一已发生的行为级漂移（fingerprint 缺失），剩余为纯结构 residual。Successor：无需独立 successor plan，登记为 GMCE 结构观察项（Non-Blocking Follow-ups），克隆复发（下一次该族行为修改引入不一致）时再立项。

#### D3 回归面清单（2026-09-03，rg 复核）

**既有 terminate/checkpoint 相关测试（文件均存在，rg 复核）**：

- terminate 三联行为面：
  - `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/coordinator/TestJobCoordinator.java`：testTerminateCancel / testTerminateDrainTriggersTerminalCheckpoint / testTerminateSuspendTriggersSavepoint / testTerminateExportSavepointContinuesRunning / testFullCheckpointFlow
  - `.../coordinator/TestCoordinatorRpcControlPlane.java`：terminateCancelSetsCanceledStatus / terminateDrainUsesTerminalSavepointCheckpointType / terminateSuspendUsesTerminalSavepointCheckpointType
  - `.../health/TestJobCoordinatorHealthWiring.java`：terminateCancelTransitionsToCanceled / terminateDrainTransitionsToFinished / exportSavepointKeepsRunning
  - `.../coordinator/TestJobCoordinatorAuditFixes.java`（standby terminate 门控）、`.../event/TestJobCoordinatorLifecycleEvents.java`（CANCEL 生命周期事件）、`.../rpc/TestStreamControlRpc.java`（terminate RPC 透传）、`.../checkpoint/TestFingerprintAndTerminationMode.java`（termination mode 配置面 + LOCAL e2e）
- GMCE 三 overload 既有覆盖：
  - overload 1（JobGraph 形态）：TestMailboxE2ECheckpoint / TestParallelCheckpoint / TestE2EMultiVertexCheckpoint / TestCheckpointAbortWiring / TestE2EManifestRestoreIdAdvance / TestObservabilityWiringE2E / TestStreamStateResetTool / TestSupervisionLoopCheckpointReconnectE2E / TestIdlePeriodProcessingTimeWindowE2E（均直接调用 JobGraph overload）
  - overload 3（StreamModel + userConfig）：`env.execute()` 生产路径（StreamExecutionEnvironment → factory 4-arg → overload 3）= TestE2EWindowAggregateRestore 等 env-based E2E + fraud-example S1/S2 LOCAL 套件
  - overload 2（StreamModel 默认配置）：**无既有直接覆盖**（rg 验证：main 唯一调用方 CheckpointExecutorFactoryImpl 3-arg 委托被其 4-arg override 遮蔽，测试零直接调用）→ 由新增 focused 测试 C 补
- retention / async persist / 段3a 面：TestAsyncSnapshotPipeline（9 用例）、TestCheckpointCoordinator、TestCheckpointCoordinatorIncrementalIntegration（segment GC + restoreSharedStateRegistry）、TestCheckpointEndToEnd / TestE2ECheckpointAndRecovery / TestE2EMultipleCheckpoints 等 E2E
- savepoint 族（D2 移出项的行为钉定，回归面保留）：TestSavepointApi / TestSavepointEndToEnd

**需新增 focused 测试清单**：

- A. 新文件 `TestCheckpointRetentionAsync`：测试 A（retention 最终一致：N > maxRetained 完成后异步谓词收敛 ≤ maxRetained）+ 测试 B（completion 不被慢 retention I/O 阻塞，`asyncSnapshotEnabled=true` 路径，慢 storage stub + 超时 fail）+ 串行化守卫（in-flight CAS + trailing re-run）+ 失败不静默（stub 抛异常 → 不阻断、后续 completion 自愈）+ sync-fallback 保持内联（D1(d) 裁定态核验）
- B. 新文件 `TestJobCoordinatorTerminationMatrix`：Drain/Suspend/Export 参数化矩阵（各自断言 CheckpointType、事件类型与负载、health 转换、终态 jobStatus、stop 与否、barrier 发送时序）——先于 terminate 重构落地并绿
- C. GMCE 钉定（新文件 `TestGraphModelCheckpointExecutorEntryPinning`）：③ `GraphExecutionPlan.build` 4-arg vs 6-arg 的 InputGate unaligned 缺省差异（plan 层单测断言 gate 缺省，JobGraph 入口不透传的现状行为）+ ②④ fingerprint/restore 差异（overload 1 无 fingerprint / restore 传 null；overload 2/3 有 fingerprint + streamModel restore）+ overload 2 端到端路径（补既有覆盖缺口）

Exit Criteria:

- [x] D1—D3 裁定落入本 plan，无未裁定项
- [x] D3 清单引用的测试文件全部存在（rg 复核）

### Phase 2 - retention I/O 出 monitor

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java`

- Item Types: `Fix | Proof`

- [x] 按 D1 实现：`cleanupOldCheckpoints` 的存储 I/O 不再在 monitor 持有栈内同步执行
- [x] focused 测试 A（retention 最终一致）：完成 N > maxRetained 个 checkpoint 后，存储保留集收敛到 ≤ maxRetained（异步等待谓词，超时 fail，不得固定 sleep 偶然通过）
- [x] focused 测试 B（completion 不阻塞，async 路径）：注入慢 getAllCheckpoints/deleteCheckpoint 的 storage stub，在 `asyncSnapshotEnabled=true` 路径下断言后续 checkpoint 触发/完成不被 retention I/O 阻塞（超出门限即 fail）；sync-fallback 路径按 D1(d) 裁定态处理（保持同步 = 不适用本测试）

Exit Criteria:

- [x] 代码审查验证：async 完成路径上 getAllCheckpoints/deleteCheckpoint 调用点不在 synchronized 调用栈内同步执行；sync-fallback 路径按 D1(d) 裁定结果核验（裁定「保持同步」= 该模式声明语义，通过；裁定「异步化」= 须声明为行为增量并有对应测试）
- [x] 测试 A/B 绿，分别钉定 retention 最终一致与 completion 不被慢 I/O 阻塞
- [x] **无静默跳过**：retention I/O 异步失败有日志（及如 D1 裁定的重试），不静默吞掉
- [x] owner-doc 裁定：`checkpoint-design.md` async-persist 相关节如需同步则更新；否则显式 `No owner-doc update required`
- [x] `ai-dev/logs/` 当日条目更新

### Phase 3 - GMCE 三 overload 与 terminate 三联收敛

Status: completed
Targets: `GraphModelCheckpointExecutor.java`、`JobCoordinator.java`

- Item Types: `Fix | Proof`

- [x] 先钉后拆：依据现实现固化参数化矩阵测试（Drain/Suspend/Export 各自断言 CheckpointType、事件类型与负载、health 转换、终态/jobStatus、stop 与否），测试先于重构落地并绿
- [x] 先钉后拆（GMCE）：三入口差异维度钉定（含 JobGraph 入口 unaligned 不透传的**现状行为**——收敛保留该不对称；观测面提示：unaligned 标志落在 InputGate 内部、GMCE 无公开暴露，钉定可在 `GraphExecutionPlan.build` 4-arg/6-arg 层面单测断言 gate 缺省，GMCE 层面钉 fingerprint/restore 差异②④），断言先于重构落地并绿
- [x] 三 overload 收敛为单一私有骨架 + 三个薄公开入口（签名不变），差异面以 Current Baseline 差异清单①—⑤为完整参数集
- [x] **差异收敛纪律**：默认保留现状不对称（参数化，行为零变更）；任何「统一」分叉（如 JobGraph 入口开始透传 unaligned）= 行为变更，必须显式裁定并纳入行为增量声明，默认不做。唯一豁免：差异⑤（行为不可观测的纯顺序差异）统一顺序不视为行为变更
- [x] terminate 三联收敛为单一参数化私有实现（差异：CheckpointType、日志文案、事件负载、health 转换、终止动作）
- [x] D2 裁定执行（savepoint 族纳入收敛，或按裁定移出并记录）
- [x] GMCE 路径回归：三个 overload 各至少一条既有测试路径覆盖（清单来自 Phase 1 D3）

**结构审查记录（2026-09-03）**：

- 先钉后拆证据：`TestJobCoordinatorTerminationMatrix`（3 用例：Drain/SUSPEND/EXPORT 参数化矩阵——CheckpointType/事件+负载/health 转换/jobStatus 停留 RUNNING（现状钉定）/stop 与否）与 `TestGraphModelCheckpointExecutorEntryPinning`（4 用例：③ build 层 4-arg gate unaligned 缺省 vs 6-arg 透传、② overload 1 manifest 无 fingerprint、②④ overload 3 manifest 有 fingerprint + 输出、overload 2 端到端（补既有覆盖缺口））均在重构前落地并绿；重构后**同一套断言**复跑全绿（3+4/0/0）。
- 收敛形态：GMCE = `executeWithCheckpointSkeleton`（单一私有骨架，参数集 = 差异①—⑤：① 入口解析 ids/config、② `streamModel != null` 键控 fingerprint、③ `threadUnalignedConfig` 键控 4-arg/6-arg build（保留 JobGraph 入口不透传 unaligned 的现状不对称）、④ restore 参数 = `streamModel`（JobGraph 入口 null）、⑤ validate→resolve 顺序统一（唯一豁免项））+ 三个薄公开入口（各只做 ① 的 id/config 解析）；terminate = `terminateWithTerminalSavepoint(mode, checkpointType, snapshotNoun, finishedPayload, terminal)`（JobCoordinator.java:1910）+ 三个 3—4 行薄委托。逐字 finally 块：`execPlan.closeBufferPool()` 调用点 5 → 3（骨架 1 + savepoint 族 2，后者为 D2 裁定移出项）。
- 公开 API 签名零变更：`git diff` 生产面仅触及 3 个 in-scope 文件（CheckpointCoordinator/JobCoordinator/GraphModelCheckpointExecutor），零调用方适配改动；savepoint 族（triggerSavepoint/executeWithSavepoint/restoreFromSavepointPath）diff 零行（D2 裁定忠实执行）。
- 日志文案卫生声明（closure audit Minor #1 采纳）：EXPORT_SAVEPOINT 两条日志经参数化后措辞微调（完成日志 `"savepoint {} exported"` → `"export savepoint {} exported"`、失败日志 `"failed for job"` → `"failed to complete export savepoint for job"`，JobCoordinator.java:1929/:1934）——模式标签（`EXPORT_SAVEPOINT:`）与可观测性保留、无测试断言日志文本；这是与⑤同类的纯文案归一，closure audit 裁定为 log-only、零功能影响。
- GMCE 三路径回归：overload 1 = TestMailboxE2ECheckpoint / TestParallelCheckpoint / TestE2EMultiVertexCheckpoint / TestCheckpointAbortWiring / TestE2EManifestRestoreIdAdvance 等（全绿）；overload 3 = env.execute 生产路径（TestE2EWindowAggregateRestore 等 + fraud S1/S2 LOCAL）；overload 2 = TestGraphModelCheckpointExecutorEntryPinning.streamModelDefaultConfigEntryRunsEndToEnd（新补，输出/manifest/fingerprint 断言）。

Exit Criteria:

- [x] 除 D2 裁定移出的 savepoint 族外，逐字重复的脚手架/finally 块收敛到单点（结构审查记录）
- [x] 公开 API 签名零变更（git diff 无 GMCE/JC 调用方签名适配改动）
- [x] 参数化矩阵测试 + GMCE 三路径回归全绿（重构前后同一套断言）
- [x] **行为零变更证据**：除 Phase 2 声明的 retention 时序外无其他行为差异（D3 清单全绿）
- [x] owner-doc 裁定（同 Phase 2 规则，显式记录）
- [x] `ai-dev/logs/` 当日条目更新

### Phase 4 - 回归与门禁收口

Status: completed
Targets: nop-stream 全模块

- Item Types: `Proof`

- [x] **整计划端到端验证锚点**：本计划 Phase 2/3 为组件级，端到端由本 Phase 承载——checkpoint completion → retention → 恢复路径在全量回归 + gated 多 JVM 真实分布式场景中运行不回退
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] gated 多 JVM 既有套件回归绿（场景 gated 套件 + legacy 套件，命令与计数以 `distributed-runbook.md` §5 所列为权威）
- [x] invariants 注册表 pin 如行号漂移按工具指引 re-pin，`check-nop-stream-invariants.mjs` exit 0
- [x] `scan-hollow-implementations.mjs --module nop-stream-runtime --severity high` exit 0

**验证记录（2026-09-03）**：

- 默认态全量：`./mvnw test -pl nop-stream -am -T 1C` **3393/0/0/25 BUILD SUCCESS**（core 1554/1、runtime 975/10、cep 359/0、flow 102/0、connector 65/0、connector-batch 46/0、connector-jdbc 40/0、connector-debezium 40/1、rocksdb 108/0、fraud 110/13；skipped 全为既有 gated/@Disabled）。
- 场景 gated 套件（fraud-example，`-Dnop.stream.test.multi-jvm.enabled=true`）：`TestS1MultiJvmE2E,TestS2MultiJvmE2E,TestS2RestoreRescaleMultiJvmE2E,TestScenarioBackpressureMultiJvmE2E` **7/7 绿 0 skipped**（C0 基线 / C1 kill-recover-fencing / C2 restore-rescale / C3 backpressure；最慢 95.53s）。
- legacy gated 套件（runtime）：`TestMiniStreamClusterProcessSpawn,TestMultiJvmExactlyOnceRecovery,TestMultiJvmCoordinatorFailover,TestMultiJvmHealthStateAndAlerts` **8/8 绿**（3/1/3/1）。
- EX 稳定性演练矩阵（§5 EX 行）未纳入本轮的裁定：item 15 的 soak/chaos 专项装置由 plan 1951-3 的 SOAK-3 全参数复验承载（该计划的 D3 验收锚点），本计划行为零变更面由 C0—C3 + legacy 双绿钉定（与 0830-3/1723-3 closure 同口径）。
- invariants：GMCE 收敛使 wiring pin `setStateBackend` :751 → :712，按注册表惯例 re-pin + `updated` 留痕 → `check-nop-stream-invariants.mjs` **exit 0**。
- `scan-hollow-implementations.mjs --module nop-stream-runtime --severity high` **exit 0**（0 findings）；`check-doc-links.mjs --strict` **exit 0**；`echo 'typecheck not configured' && ./mvnw clean install -pl nop-stream -am -T 1C -DskipTests && echo 'lint not configured'` BUILD SUCCESS。

Exit Criteria:

- [x] 上述命令/套件全绿且退出码 0
- [x] `ai-dev/logs/` 收口条目更新

## Closure Gates

- [x] F-A 收敛：retention GC I/O 不再持 coordinator monitor（Phase 2 证据）
- [x] F-B/F16 结构收敛：GMCE 三 overload + terminate 三联单点化（Phase 3 证据）
- [x] 行为零变更（除声明的 retention 时序）由回归面钉定
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope 项
- [x] 受影响 owner docs 已同步或显式 `No owner-doc update required`
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：调用链运行时连通 + 无空方法体/静默跳过/no-op 正常实现
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-runtime --severity high` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（若本计划修改了 docs/ai-dev 文件）

## Deferred But Adjudicated

### savepoint 族脚手架收敛（triggerSavepoint / executeWithSavepoint）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Phase 1 D2 二态裁定（2026-09-03）：该族 try-body 本质异构（savepoint 触发+物化+显式存储 vs savepoint 恢复+final checkpoint），共享面仅 setup+finally；全骨架收敛需 body-strategy 参数化，验证面与主线三联收敛不对称。R-7 已修复其唯一已发生的行为级漂移（fingerprint 缺失），剩余为纯结构 residual，不影响本计划「retention I/O 出 monitor + 三联单点化」closure 成立；本计划行为零变更回归面（TestSavepointApi / TestSavepointEndToEnd）持续钉定其行为。
- Successor Required: `no`
- Successor Path: 无独立 successor——登记为 Non-Blocking Follow-ups 的 GMCE 结构观察项，克隆复发时再立项

## Non-Blocking Follow-ups

- **GMCE/savepoint 族结构观察项（watch-only residual，源自 D2 裁定）**：triggerSavepoint / executeWithSavepoint 保留各自 setup+finally 脚手架（finally 块 2 处逐字克隆为 Deferred 项）；行为由 TestSavepointApi / TestSavepointEndToEnd 持续钉定。触发再立项条件 = 该族下一次行为修改引入克隆间不一致（R-7 模式复发）。
- **GMCE 行数观察**：收敛后 GraphModelCheckpointExecutor.java 1956 → 1917 行（-39）；若后续增长回 overload 时代量级（>2100）或新增第 4 个入口未走 skeleton，视为结构回归信号。
- **retention watch-only 残余（D1(f) 落档）**：checkpoint 行已落、GC-map 条目未落的微秒级窗口内并发 retention 可能跳过该 epoch 的 segment GC（磁盘残留由 restart orphan 清扫回收）；若未来出现无法解释的 SST 残留投诉，优先核验此窗口。

## Closure

Status Note: 审计 F-A（retention I/O 在 monitor 内）与 F-B 结构性主体（GMCE 三 overload ~85-90% 克隆 + terminate 三联 ~90% 克隆）已收口：retention 存储 I/O 在 async 完成路径移至专用单线程 `checkpoint-retention-<jobId>` executor（monitor 内仅非阻塞调度；sync-fallback 按 D1(d) 保持内联），串行化 + trailing re-run 保证最终一致；GMCE 收敛为单一骨架 + 三薄入口（差异①—⑤全参数化、现状不对称保留、⑤顺序统一为唯一豁免）、terminate 收敛为单参数化实现 + 三薄委托；savepoint 族按 D2 二态裁定移出（out-of-scope improvement，行为回归面保留）。行为增量 = 声明的 retention 时序 + closure audit 裁定为 log-only 的 EXPORT 文案归一；全量 3393/0/0/25 + gated 场景 7/7 + legacy 8/8 + 四工具门禁 exit 0 钉定。四 Phase 全部 completed，独立 closure audit APPROVED（0 Blocker / 0 Major / 2 Minor 已采纳修复 + 2 Info），无 remaining plan-owned work。
Completed: 2026-09-03

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent fresh session `ses_f9819ebd2ffeejsI9INUEGMCU4`（2026-09-03，executor 之外的独立 closure audit pass）
- Evidence:
  - Phase 1 Exit Criteria：PASS——D1 六问裁定（含 (a)—(f) 全部落档：切分点/最终一致判据/失败语义/sync-fallback 二态「保持同步」/顺序无依赖论证/串行化+trailing+内存部分归属+watch-only 残余）+ D2 二态裁定（savepoint 族移出 Deferred）+ D3 回归面清单（逐文件 rg 复核存在；audit 抽点 7 个文件确认）。
  - Phase 2 Exit Criteria：PASS——audit live 追踪调用链：async 路径 `completePendingCheckpoint:530 → executePersistAsync:581/executeIncrementalPersistAsync:558 → synchronized 段3a:648/:707 → scheduleRetentionCleanup:904/:1271-1302`（仅 CAS+submit）→ retention executor 线程执行 `cleanupOldCheckpoints:1343/:1347`（无 monitor；测试运行日志栈帧佐证 `checkpoint-retention-*` 线程）；sync-fallback `:594 → :614 → :907` 内联（D1(d) 裁定态核验通过）；失败 WARN `:1355` + RejectedExecution WARN `:1300`（无静默吞）；guard 串行化 + trailing 语义 soundness 确认（含 set(false)/getAndSet(false) 与直接触发的再武装竞态论证）；retention executor 生命周期 `:1304-1319` / `:1506-1517`；TestCheckpointRetentionAsync 6/0/0/0 audit 现场 live 复跑绿。
  - Phase 3 Exit Criteria：PASS——三公开 overload 签名与 HEAD 逐字节一致（git show 对照）；单骨架 `executeWithCheckpointSkeleton:198` 消费三入口 `:113/:137/:170`；钉定不对称保留（4-arg build 无 unaligned 透传 `:213`、fingerprint 键控 `:222-224`、restore null `:232`）；terminate 三薄委托 `:1875-1890` → 单点 `:1910`（矩阵：TERMINAL/TERMINAL/EXPORTED + drain/suspend/null + true/true/false，`!terminal` 早返回于 health/event/stop 之前 `:1936-1944`）；savepoint 族 diff 零行（D2 忠实执行）；矩阵 + 钉定测试 3+4/0/0 audit 现场 live 复跑绿（重构前后同一套断言）。
  - Phase 4 Exit Criteria：PASS——3393/0/0/25 全量 + 场景 gated 7/7（C0—C3）+ legacy gated 8/8 留档（executor 侧执行，audit 基于 13/13 focused live 复跑 + 全 node 门禁 + invariant pin live 核验采信，Info #4 诚实记录）；invariants re-pin :751→:712（audit live 核验 :712 = setStateBackend 调用点）。
  - Closure Gates 逐条：上表全 [x]，对应 evidence 来源 = 上述 Phase 2/3/4 + 本节命令退出码。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 收口仪式执行，见下方复跑记录）。
  - Anti-Hollow 检查结果：三条新调用链运行时连通（scheduleRetentionCleanup 被 async 段3a真实调用、skeleton 被三入口调用、terminateWithTerminalSavepoint 被三方法调用——audit 逐点核验行号 + 测试线程日志佐证）；无空方法体/静默跳过；`scan-hollow-implementations.mjs` 退出码 0（0 findings）。
  - Deferred 项分类检查：唯一 deferred = savepoint 族（out-of-scope improvement，D2 为 Phase 1 二态裁定而非 closure 时降级，行为回归面保留 + watch 项登记）——无 in-scope live defect 被降级。
  - audit 发现处置：Minor #1（EXPORT 日志文案微调）→ Phase 3 结构审查记录补「日志文案卫生声明」；Minor #2（plan :91 路径缺前缀）→ 已修复（doc-links 复跑 0 errors 0 warnings）；Info #3/#4 → 本 Closure 段落填充即为处置 / 全量复跑记录已留档于 Phase 4。

Follow-up:

- 无 remaining plan-owned work（三个 watch-only 项已登记于 Non-Blocking Follow-ups：savepoint 族结构观察 / GMCE 行数观察 / retention 微秒窗残余）。
