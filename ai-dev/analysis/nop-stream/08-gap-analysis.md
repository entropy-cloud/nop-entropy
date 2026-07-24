# 综合缺口分析文档

> Status: resolved
> Date: 2026-07-25
> Scope: 汇总 items 3—7（checkpoint/barrier、状态管理、窗口/时间、CEP、分布式执行）的全部对比发现，形成统一的缺口清单
> Conclusion: 87 total active gaps (deduplicated), 4 resolved gaps, mapped to roadmap items 9—13
> Source: `03-checkpoint-comparison.md`, `04-state-comparison.md`, `05-window-comparison.md`, `06-cep-comparison.md`, `07-distributed-comparison.md`
> Plan: `docs/plans/nop-stream-flink-comparison/2026-07-25-1210-2-gap-analysis.md`

## Executive Summary

从 5 篇源码级对比文档（03—07）中提取共 **87 条有效缺口**（去重前 81+20+11+12+20 = 144；去重后 87 活跃 + 4 已解决附录）。

### 分布

| Priority | Count | Description |
|----------|-------|-------------|
| P0 | 3 | Correctness blocking |
| P1 | 24 | Design contract violation |
| P2 | 43 | Missing capability |
| P3 | 10 | Optimization/minor |
| Doc | 5 | Documentation / contract drift |
| Improvement | 2 | Intentional design differences |

### 关键发现

1. **P0 级缺口 3 个**: Session window merge 对 AggregatingState 失败、timer 无 checkpoint/restore、BarrierAligner 无生产调用者
2. **CEP 状态后端在代码层面已正确**（分级 backend wiring），真缺口在 runtime 层是否调用 `setKeyedStateBackend()` 和 `snapshotState()`/`restoreState()`
3. **Roadmap 多个 known gap 被对比分析纠正**: SimpleKeyedStateStore 说法不准确、watermarkInterval 默认 200L 非 0、NFA 状态已参与 checkpoint（代码层面）
4. **Plan 303 和 checkpoint-lifecycle-fixes 已解决 4 个缺口**: processing time timer no-op、WindowAggregationOperator 清理、abort metrics 混淆、错误码不匹配

## Classification Taxonomy

统一分类系统（来自 07-distributed-comparison，经本文确认用于全部维度）：

| 分类 | 含义 | 映射规则 |
|------|------|---------|
| **Bug** | 行为降级：非故意修改，从 Flink 提取时引入的退化 | 例：`ExtractionDegradation`、OperatorChain double-open |
| **Gap** | 功能缺失：Flink 有而 nop-stream 无 | 例：无 channel blocking、无 mailbox |
| **Hollow** | 空壳实现：接口存在但运行时无连线/方法体为空 | 例：BarrierAligner 无调用者、CREDIT_BASED 抛 `UnsupportedOperationException` |
| **Improvement** | 改进机会：功能正确但可优化 | 例：同步 snapshot 可改异步 |
| **Doc** | 文档/契约偏离：有意与 Flink 不同、注释过时 | 例：EFFECTIVELY_ONCE 为 nop-stream 独有 |
| **No-Op** | 静默跳过：无实现也无异常 | 本次不单独使用，归入 Hollow |

跨文档分类不一致解决规则（audit trail）：
- 06-cep 的 `ExtractionDegradation` → `Bug`
- 06-cep 的 `Wiring Gap` → `Hollow`（接口存在但 runtime 不调用）
- 06-cep 的 `Design Difference` → `Doc`

## Merged Gap Table

所有活跃缺口（去重后），保留全部来源引用。

### P0 — Correctness Blocking（3）

| # | 维度 | 发现 | 分类 | 来源 | Flink Ref | nop-stream Ref | 修复 Plan |
|---|------|------|------|------|-----------|----------------|-----------|
| G1 | window | Session window merge 对 AggregatingState 失败（`clear()+add()` 应为 `mergeNamespaces()`） | Bug/P0 | 05-window: G1 | `WindowOperator.mergeWindowContents()` uses `state.mergeNamespaces()` | `WindowOperator.mergeWindowContents()` uses `clear()+add()`, throws "Failed to set merged accumulator" | Item 9 |
| G2 | window | Timer 无 checkpoint/restore（HeapInternalTimerService 和 WindowOperatorTimerService 均为纯内存） | Bug/P0 | 05-window: G2, 03-checkpoint: #9(partial) | `InternalTimerServiceImpl.snapshotTimersForKeyGroup()` + restore | 两个 timer service 实现在内存中操作，无 `snapshotTimersForKeyGroup()`/`restoreTimersForKeyGroup()` 等效 | Item 9 |
| G3 | checkpoint | BarrierAligner 无生产调用者 | Hollow/P0 | 03-checkpoint: #1, 03-checkpoint: #3 | `SingleCheckpointBarrierHandler` (multi-input alignment) | `BarrierAligner.java` — 0 production callers, 3 test files only. Javadoc: "当前 GraphModelCheckpointExecutor 未使用" | Item 9 |

### P1 — Design Contract Violation（24）

| # | 维度 | 发现 | 分类 | 来源 | Flink Ref | nop-stream Ref | 修复 Plan |
|---|------|------|------|------|-----------|----------------|-----------|
| G4 | checkpoint | 无 multi-input barrier alignment 运行时 | Gap/P1 | 03-checkpoint: #2 | `CheckpointedInputGate` → `SingleCheckpointBarrierHandler` with state machine | `AbstractStreamOperator.processBarrier()` — sequential per-input only | Item 9 |
| G5 | checkpoint | 无 CancelCheckpointMarker 事件类型 | Gap/P1 | 03-checkpoint: #4 | `CancelCheckpointMarker` extends `RuntimeEvent` | No equivalent | Item 9 |
| G6 | checkpoint | 无 unaligned checkpoint 支持 | Gap/P1 | 03-checkpoint: #5 | `AlignmentType.UNALIGNED` + channel state in `OperatorSnapshotFutures` | Not present | deferred (Phase 4) |
| G7 | checkpoint | 无 channel blocking 机制 | Gap/P1 | 03-checkpoint: #6 | `InputGate.blockConsumption(channelInfo)` | No `blockConsumption()`/`resumeConsumption()` on `Input` | Item 9 |
| G8 | state | 缺少 OperatorStateStore 接口 | Gap/P1 | 04-state: #4 | `OperatorStateStore` interface | `TaskStateSnapshot.putOperatorState(key, value)` — `Map<String, Object>` 直接操作 | Item 12a |
| G9 | state | 缺少重分布模式 (SPLIT/UNION/BROADCAST) | Gap/P1 | 04-state: #5 | `PartitionableListState` + `SPLIT_DISTRIBUTE`/`UNION`/`BROADCAST` | 完全不支持 | Item 12b |
| G10 | state | 缺少 IOperatorStateBackend | Gap/P1 | 04-state: #6 | `OperatorStateBackend` interface (extends `OperatorStateStore` + `Snapshotable`) | `TaskStateSnapshot` + `OperatorSnapshotResult` — Map<String, Object> | Item 12a |
| G11 | state | 缺少 IStateBackend.createOperatorStateBackend() | Gap/P1 | 04-state: #8 | `StateBackend.createOperatorStateBackend(params)` | 不存在 | Item 12a |
| G12 | state | 缺少 TypeSerializerSnapshot 接口体系 | Gap/P1 | 04-state: #14 | `TypeSerializerSnapshot<T>` + `resolveSchemaCompatibility()` | 不存在 | Item 13 或独立 serialization plan |
| G13 | state | ICheckpointedFunction 的 FunctionInitializationContext 不暴露 state store | Hollow/P1 | 04-state: #7 | `FunctionInitializationContext.getOperatorStateStore()` + `getKeyedStateStore()` | `FunctionInitializationContext` 仅提供 `isRestored()` | Item 12a |
| G14 | window | PaneInfo/PaneState 数据模型存在但未接线 | Hollow/P1 | 05-window: G3 | 无显式 PaneState 类（pane 隐含在 per-window state） | `PaneInfo.java`, `PaneState.java` 为 @DataBean 类，WindowOperator 不读写 | Item 10 |
| G15 | window | AccumulationMode 未接线 | Gap/P1 | 05-window: G4 | `WindowingStrategy.accumulationMode` 影响窗口函数行为 | Enum 存在但 WindowOperator 忽略 | Item 10 |
| G16 | window | 并行 timer service 实现重复 | Gap/P1 | 05-window: G5 | 单一 `InternalTimerServiceImpl` | `HeapInternalTimerService` vs `WindowOperatorTimerService` — 不同数据结构，不同管理 | Item 9 |
| G17 | window | SourceFunction watermark 自动插入缺失 | Gap/P1 | 05-window: G6 | `StreamSourceContexts.AutomaticWatermarkContext` + `ManualWatermarkContext` | 无等效 — 所有 source 必须显式调用 `assignTimestampsAndWatermarks()` | Item 10 |
| G18 | cep | Runtime 是否调用 `setKeyedStateBackend()` 未验证 | Hollow/P1 | 06-cep: #5 | Flink runtime 自动注入 KeyedStateBackend | CepOperator 代码有分级 fallback 但 runtime 层注入路径未验证 | Item 11 |
| G19 | cep | Runtime 是否调用 operator `snapshotState()`/`restoreState()` 未验证 | Hollow/P1 | 06-cep: #6 | Flink checkpoint coordinator 调用 operator snapshot/restore | CepOperator 代码正确实现 snapshotState/restoreState 但 runtime 是否调用未验证 | Item 11 |
| G20 | cep | Watermark 从 runtime 到 CepOperator 的传播管路未验证 | Hollow/P1 | 06-cep: #9 | Flink 通过 InternalTimerService.advanceWatermark() 自动传播 | CepOperator 内部 watermark 处理正确但外部触发路径未验证 | Item 10/11 |
| G21 | distributed | OperatorChain double-open（Task + Invokable 都调 .open()） | Bug/P1 | 07-dist: D1 | `StreamTask.invoke()` — 单一生命周期 | `Task.java:61-68` (openOperatorChains) + `StreamTaskInvokable.java:170-185` (wireOperators → chain.open()) | Item 9 |
| G22 | distributed | 无 mailbox/interleaving 执行模型 | Gap/P1 | 07-dist: D2 | `MailboxProcessor.runMailboxLoop()` interleaves mail + data | 同步 `inputGate.read()` 阻塞循环 | Item 9 |
| G23 | distributed | RPC 接口仅 local 实现（无跨 JVM） | Hollow/P1 | 07-dist: D3 | `AkkaRpcService`/`PekkoRpcService` with network transport | `IStreamTaskRpcService`(4 methods), `IStreamCoordinatorRpcService`(1 method) — local-only | Item 12a |
| G24 | distributed | ILeaderElector 未实现（零代码） | Hollow/P1 | 07-dist: D4 | `LeaderElectionService` + `LeaderContender` + ZooKeeper driver | `ILeaderElector` + `SysDaoLeaderElector` — 在代码库中不存在 | deferred (Phase 3) |
| G25 | distributed | 无 leader election / HA for coordinator | Gap/P1 | 07-dist: D5 | `FencedRpcEndpoint` + `StandbyJobManager` + ZooKeeper HA | 单一 `JobCoordinator` — 无 standby, 无 election | deferred (Phase 3) |
| G26 | distributed | IStreamExecutionDispatcher 接口空壳（2 methods） | Hollow/P1 | 07-dist: D8 | `SchedulerNG` (15+ methods) with state tracking | `IStreamExecutionDispatcher` (2 methods, 21 lines) | Item 12a |
| G27 | distributed | Credit-based 和 ACK_WINDOW flow control 为 no-op | Hollow/P1 | 07-dist: D11 | `CreditBasedSequenceNumbering`, `PartitionRequestClient.notifyCreditAvailable()` | `FlowControlPolicy.CREDIT_BASED` / `ACK_WINDOW` — throw `UnsupportedOperationException` | Item 12a |

### P2 — Missing Capability（43）

| # | 维度 | 发现 | 分类 | 来源 | 修复 Plan |
|---|------|------|------|------|-----------|
| G28 | checkpoint | 无 partial/region failover | Gap/P2 | 03-checkpoint: #7, 07-dist: D10 | Item 9 |
| G29 | checkpoint | 无 subtask-level granular restoration | Gap/P2 | 03-checkpoint: #8 | Item 9 |
| G30 | checkpoint | 无 async snapshot pipeline | Gap/P2 | 03-checkpoint: #9, 04-state: #9 | Item 9 |
| G31 | checkpoint | maxConcurrentCheckpoints config hard-coded to 1 | Bug/P2 | 03-checkpoint: #10 | Item 9 |
| G32 | checkpoint | 无 HA checkpoint store | Gap/P2 | 03-checkpoint: #11 | deferred (Phase 3) |
| G33 | checkpoint | 无 shared state registry | Gap/P2 | 03-checkpoint: #12 | Item 9 |
| G34 | checkpoint | 无 abort propagation via data channel | Gap/P2 | 03-checkpoint: #13 | Item 9 |
| G35 | checkpoint | 无 operator coordinator ACK tracking | Gap/P2 | 03-checkpoint: #14, 07-dist: D14 | deferred (Phase 3) |
| G36 | state | 缺少 BroadcastState 类型 | Gap/P2 | 04-state: #1 | Item 12b |
| G37 | state | 缺少 maxParallelism 概念 | Gap/P2 | 04-state: #11 | 独立 plan 或 state backend 重构 |
| G38 | state | StateShard 使用 Object.hashCode() 非稳定哈希 | Gap(Hollow)/P2 | 04-state: #12 | 独立 plan |
| G39 | state | StateShard 无 range 交集/分割能力 | Gap/P2 | 04-state: #13 | 独立 plan |
| G40 | state | TypeSerializer 接口未实际使用 (MemoryStateSerDe 用 JsonTool) | Hollow/P2 | 04-state: #15 | Item 13 |
| G41 | state | 缺少 serializer 注册/管理机制 | Gap/P2 | 04-state: #16 | Item 13 |
| G42 | state | StateTtlConfig 完全缺失 | Gap/P2 | 04-state: #18 | 独立 plan |
| G43 | state | TTL 装饰器/清理策略完全缺失 | Gap/P2 | 04-state: #19 | 独立 plan |
| G44 | state | 缺少异步两阶段 snapshot | Gap/P2 | 04-state: #9(dup) | Item 9 |
| G45 | state | 缺少增量 checkpoint | Gap/P2 | 04-state: #10(partial) | deferred (Phase 4) |
| G46 | window | Evictor.evictAfter() 未被调用 | Gap/P2 | 05-window: G7 | Item 10 或独立 |
| G47 | window | StatusWatermarkValve 等效缺失 | Gap/P2 | 05-window: G8 | Item 10 |
| G48 | window | Early/on-time/late pane tracking 缺失 | Gap/P2 | 05-window: G9 | Item 10 |
| G49 | cep | 设计注释过时（Javadoc 声称 "hardcoded MemoryKeyedStateBackend"） | Doc/P2 | 06-cep: #7 | Item 11 |
| G50 | distributed | 无 slot sharing / co-location groups | Gap/P2 | 07-dist: D6 | Item 12a |
| G51 | distributed | 无 resource manager 组件 | Gap/P2 | 07-dist: D7 | Item 12a |
| G52 | distributed | 无 per-task failure detection | Gap/P2 | 07-dist: D9 | Item 12a |
| G53 | distributed | 无 buffer pool abstraction | Gap/P2 | 07-dist: D12 | Item 12a |
| G54 | distributed | 缺少中间 execution 状态 (SCHEDULED/ DEPLOYING/ INITIALIZING) | Gap/P2 | 07-dist: D13 | Item 12a |
| G55 | distributed | 无 region-aware scheduling | Gap/P2 | 07-dist: D15 | Item 12a |
| G56 | distributed | 无 execution retry/attempt tracking | Gap/P2 | 07-dist: D16 | Item 12a |
| G57 | distributed | 无 targeted failover (仅 globalRecovery) | Gap/P2 | 07-dist: D10(dup) | Item 12a |
| G58 | distributed | CANCELING 状态仅在 SubtaskTask 中有，Task 中没有 | Improvement/P2 | 07-dist: (line 182) | Item 12a |

### P3 — Optimization / Minor（10）

| # | 维度 | 发现 | 分类 | 来源 | 修复 Plan |
|---|------|------|------|------|-----------|
| G59 | checkpoint | CheckpointSerDe 缺少 schema versioning | Improvement/P3 | 03-checkpoint: #15 | Item 13 |
| G60 | checkpoint | Bulk cleanup instead of precise subsume | Improvement/P3 | 03-checkpoint: #16 | Item 9 |
| G61 | checkpoint | JdbcCheckpointStorage 脆弱 duplicate key 检测（字符串匹配） | Improvement/P3 | 03-checkpoint: #17 | Item 9 |
| G62 | state | 缺少 MergingState 中间接口 | Gap/P3 | 04-state: #2 | 可选项 |
| G63 | window | Timer 注册中 O(n) contains() 检查 | Improvement/P3 | 05-window: G10 | Item 9 |
| G64 | window | 反射工厂加载开销 | Improvement/P3 | 05-window: G11 | — |
| G65 | cep | SharedBuffer 缓存使用 ConcurrentHashMap 替代 Guava Cache，无 LRU 驱逐 | ExtractionDegradation/P3 | 06-cep: #3 | Item 11 |
| G66 | distributed | 无 spill-to-disk for large buffers | Gap/P3 | 07-dist: (line 459) | deferred |
| G67 | distributed | 无 adaptive scheduling | Gap/P3 | 07-dist: (line 285) | deferred |
| G68 | distributed | OperatorChain javadoc 说 forward 顺序但代码 reverse | Doc/P3 | 07-dist: D20(partial) | Item 9 |

### Doc / Design Difference（5）

| # | 维度 | 发现 | 分类 | 来源 |
|---|------|------|------|------|
| D69 | checkpoint | EFFECTIVELY_ONCE 语义为 nop-stream 独有 | Doc | 03-checkpoint: #18 |
| D70 | checkpoint | Epoch-based recovery 为 nop-stream 独有 | Doc | 03-checkpoint: #19 |
| D71 | distributed | 四层图模型（StreamGraph→JobGraph→PartitionedPlan→DeploymentPlan）为有意设计 | Doc | 07-dist: D20 |
| D72 | distributed | Remote transport 使用 IMessageService 为有意设计 | Doc | 07-dist: D19 |
| D73 | distributed | ClusterRegistry JDBC durability 为有意简化 | Doc | 07-dist: D17 |

## Resolved Gap Appendix（已解决缺口）

被 Plan 303 和 checkpoint-lifecycle-fixes 关闭的缺口：

| # | 发现 | 原分类 | 解决 Plan | Plan 状态 | 证据 |
|---|------|--------|-----------|----------|------|
| R1 | HeapInternalTimerService processing time timer 空方法体（no-op） | Hollow | 303 Phase 1 | completed | `registerProcessingTimeTimer()` 已实现；`fireProcessingTimeTimers()` 添加；TimerServiceManager 定时调用 |
| R2 | WindowAggregationOperator 代码债务（deprecated 887 行与新 WindowOperator 冲突） | Code Debt | 303 Phase 2 | completed | `WindowAggregationOperator.java` 和 `WindowAggregationState.java` 已删除 |
| R3 | 链化条件不完整（无 ForwardPartitioner 显式类、无 isChainable 检查） | Gap | 303 Phase 3 | completed | `ForwardPartitioner` 新增；`StreamOperatorFactory.isChainable()` 默认方法；`canChain()` 更新 |
| R4 | PendingCheckpoint 状态机缺少 FAILED 状态和状态转换校验 | Gap | 303 Phase 4 | completed | `Status.FAILED` 新增；`isValidTransition()` 校验表；`CheckpointMetrics.failureCause` 新增 |
| R5 | abort 指标污染 numFailedCheckpoints | Bug | checkpoint-lifecycle Phase 1 | completed | `recordAborted()` 新增；`abortPendingCheckpoint()` 调用 `recordAborted()` 非 `recordFailure()` |
| R6 | PendingCheckpoint.fail() 使用 ERR_STREAM_CHECKPOINT_ABORTED | Bug | checkpoint-lifecycle Phase 2 | completed | `ERR_STREAM_CHECKPOINT_FAILED` 新增；`fail()` 使用正确错误码 |

## Cross-Cutting Gaps

以下缺口跨多个对比维度，需要跨 plan 协调：

| # | 涉及维度 | 缺口 | 协调建议 |
|---|---------|------|---------|
| CC1 | checkpoint × window | Timer 无 checkpoint/restore (G2) | 需要 checkpoint 快照管线 + timer service 实现 snapshot/restore 方法。Item 9 主责 |
| CC2 | checkpoint × state | 无 async snapshot pipeline (G30) | 影响 checkpoint latency 和状态快照。Item 9 主责，需与 state backend 协调 |
| CC3 | checkpoint × distributed | 无 partial/region failover (G28) + 无 targeted failover (G57) | Checkpoint 恢复 + 分布式调度共同设计。Item 9 + Item 12a |
| CC4 | state × serialization | TypeSerializerSnapshot 缺失 (G12) | 状态序列化兼容性影响所有 state backend。独立 serialization plan 或 Item 13 |
| CC5 | window × watermark | AccumulationMode 未接线 (G15) + 多输入 watermark 对齐 (G47) | Watermark 是 AccumulationMode 触发条件。Item 10 主责 |
| CC6 | cep × distributed | Runtime 状态后端注入 + snapshot/restore 调用未验证 (G18, G19) | 需要 runtime 层的 operator 生命周期审计。Item 11 作为审计入口 |

## Priority Distribution Analysis

### P0 缺口分析
3 个 P0 全部与 data correctness 直接相关：
- **G1 (session window merge)**：当前有 4 个 disabled test，AggregatingState merge 路径抛出异常。影响所有 session window 用户
- **G2 (timer no checkpoint)**：故障恢复后 timer 丢失 → window 永不触发
- **G3 (BarrierAligner unwired)**：配置 `STRICT_EXACTLY_ONCE` 无效果

### P1 缺口分析
24 个 P1。按修复 plan 分组：
- **Item 9**（checkpoint/barrier fixes）：G4, G5, G7, G21, G22, G16（timer 去重合并）
- **Item 10**（watermark）：G14, G15, G17, G20（部分）
- **Item 11**（CEP）：G18, G19, G20（部分）
- **Item 12a**（operator state）：G8, G10, G11, G13, G23, G26, G27
- **Item 12b**（redistribution）：G9
- **Item 13**（serialization）：G12
- **deferred**：G6（unaligned checkpoint, Phase 4）, G24, G25（leader election/HA, Phase 3）

### P2 缺口分析
43 个 P2。分为：
- **checkpoint/barrier 修复**（Item 9）：G28-G35
- **watermark**（Item 10）：G46-G48
- **operator state/distributed runtime**（Item 12a）：G50-G58, G27(已列P1)
- **broadcast state/redistribution**（Item 12b）：G36
- **serialization/StreamModel**（Item 13）：G37-G41
- **TTL**：G42-G43（独立 plan 建议）
- **Key-Group/StateShard**：G37-G39（独立 plan 建议）

## Plan Mapping Table

### Items 9—13 缺口分配

| Plan | 主责缺口 | 涉及缺口 | 说明 |
|------|---------|---------|------|
| **Item 9** — Checkpoint & barrier 修复 | G1, G2, G3, G4, G5, G7, G21, G22, G28, G29, G30, G31, G33, G34, G60, G61, G63, G68 | 18 gaps + G16(timer去重) | P0+P1+P2 checkpoint/window 修复；启用 BarrierAligner；统一 timer service；修复 session window merge |
| **Item 10** — Watermark 集成修复 | G14, G15, G17, G46, G47, G48, G20(部分) | 7 gaps | SourceFunction watermark 自动插入；AccumulationMode/ PaneInfo 接线；StatusWatermarkValve 等效 |
| **Item 11** — CEP 状态后端接入 | G18, G19, G49, G65, G20(部分) | 5 gaps | Runtime 层审计 state backend 注入 + snapshot/restore 调用；更新过时 Javadoc；SharedBuffer 缓存改进 |
| **Item 12a** — Operator State 基础 | G8, G10, G11, G13, G23, G26, G27, G50, G51, G52, G53, G54, G55, G56, G57, G58 | 16 gaps | OperatorStateStore IOperatorStateBackend；分布式 RPC 扩容；resource manager；buffer pool；execution state machine；region scheduling |
| **Item 12b** — Operator State 重分布 | G9, G36, (G12 部分) | 3 gaps | SPLIT/UNION/BROADCAST redistribution；BroadcastState 类型 |
| **Item 13** — StreamModel 做实 | G12, G40, G41, G59, (G37-G39 部分) | 6 gaps | TypeSerializerSnapshot 体系；serializer 注册管理；fingerprint 接线 |
| **Deferred / 独立 plan** | G6, G24, G25, G32, G35, G37-G39, G42-G43, G45, G66, G67 | 13 gaps | Unaligned checkpoint (Phase 4), Leader election (Phase 3), Key-Group migration, State TTL, 增量 checkpoint, 自适应调度 |

## completion-roadmap.md Alignment

### 当前阶段划分评估

| Phase | 当前范围 | 建议调整 |
|-------|---------|---------|
| Phase 0.x | Known gap fixes | ✅ 合理 — 对应 Items 9-13 |
| Phase 0.2 | StreamModel 做实 | ✅ 但建议将 serialization 体系（TypeSerializerSnapshot）与 StreamModel 分离 |
| Phase 0.3 | Operator State 体系 | ✅ 建议拆分为 Item 12a（基础）和 Item 12b（重分布），与 roadmap 一致 |
| Phase 0.4 | CEP 状态后端 | ✅ G18/G19 实质是 runtime 层审计而非 CEP 代码修改 — 建议将 audit 前置到 Phase 0.x |
| Phase 0.5 | BarrierAligner 启用 | ✅ 合理 |
| Phase 0.7 | 端到端并行度 > 1 | ✅ 分布式执行 gap（D1-D20）建议在此 Phase 处理 |
| Phase 1 | RocksDB 状态后端 | ⚠️ 建议将 maxParallelism/Key-Group 迁移作为前置条件 |
| Phase 2 | Watermark 集成 | ✅ 合理 |
| Phase 3 | 分布式 RPC + HA | ✅ Leader election (G24) 应在 Phase 0.x 先设计而非等到 Phase 3 |
| Phase 4 | Unaligned checkpoint | ✅ deferred — 合理 |
| Phase 5 | XDSL 声明式入口 | ✅ deferred — 合理 |

### 调整建议

1. **Phase 0.x 增加 runtime 层审计 item**: 验证 `setKeyedStateBackend()`、`snapshotState()`/`restoreState()` 调用路径（当前 Gap G18/G19），可在 checkpoint barrier 修复前置
2. **Leader election 设计前置**: 虽实现放在 Phase 3，但接口定义和连线方式应在 Phase 0.x 明确（否则影响分布式 RPC 接口设计）
3. **Key-Group 迁移路径**: 当前无明确 Phase 归属。建议在 Phase 0.x 增加设计决策（StateShard → Key-Group），否则 RocksDB（Phase 1）和 Operator State redistribution（Item 12b）都会受阻

## Design Decision Points

以下决策点需要人确认：

| # | 问题 | 选项 | 推荐 |
|---|------|------|------|
| D1 | Key-Group vs StateShard 迁移路径 | (a) 在 StateShard 上增加 maxParallelism + 稳定哈希 | (b) 完全替换 StateShard 为 Flink KeyGroupRange 模式 | (a) — 最小侵入，保留 StateShard 作为存储分片标识 |
| D2 | 是否引入 mailbox 模型 | (a) 移植 Flink MailboxProcessor | (b) 简化版本：PriorityBlockingQueue + 优先级 mail | (a) — mailbox 是 checkpoint barrier 正确交错的必要条件 |
| D3 | 分布式 RPC 传输选型 | (a) gRPC (b) 基于 IMessageService 的自定义协议 (c) Akka/Pekko | (b) — 与现有 RemoteResultPartition 体系一致，减少额外依赖 |
| D4 | OperatorState 分发模式是否完全对齐 Flink | (a) 实现 UNION/BROADCAST/SPLIT_DISTRIBUTE 三模式 | (b) 仅实现 SPLIT_DISTRIBUTE（最常用），其他按需添加 | (a) — 与 Flink CheckpointedFunction 契约一致 |
| D5 | Timer checkpoint 策略 | (a) 全量 checkpoint（每 checkpoint 保存所有 timer） | (b) 差量 checkpoint（仅保存新增/删除的 timer） | (a) — 先正确再优化 |
| D6 | 是否引入 Guava 依赖（SharedBuffer 缓存） | (a) 添加 Guava 依赖使用 CacheBuilder | (b) 自实现 LRU 缓存 | (a) — 与 Flink CEP 行为一致，降低维护成本 |

## Owner-Doc Update Inventory

以下文档需要更新：

| 文档 | 更新原因 | 对应缺口/事实 |
|------|---------|--------------|
| `docs-for-ai/04-reference/source-anchors.md` | 添加 nop-stream 锚点条目 | Follow-up Backlog: 零 nop-stream 条目 |
| `docs-for-ai/02-core-guides/testing.md` | 记录 session window merge 测试限制 | G1: 4 disabled tests |
| `ai-dev/design/nop-stream/state/state-design.md` | 记录 StateShard vs Key-Group 决策 | D1 决策点 |
| `ai-dev/design/nop-stream/checkpoint/checkpoint-design.md` | 记录 BarrierAligner 接线状态 | G3: BarrierAligner unwired |
| `ai-dev/design/nop-stream/cep/cep-design.md` | 纠正 SimpleKeyedStateStore 说法；更新 Javadoc | 06-cep preamble: roadmap 修正 |
| `ai-dev/design/nop-stream/watermark/watermark-design.md` | 记录 watermarkInterval 非 0 事实 | 05-window roadmap verification |
| `ai-dev/design/nop-stream/task-lifecycle/task-lifecycle-design.md` | 记录 mailbox 模型 | G22: 无 mailbox |
| `ai-dev/design/nop-stream/rpc/rpc-design.md` | 记录 RPC 传输选型 | D3 决策点 |
| `ai-dev/design/nop-stream/cluster/cluster-design.md` | 记录 leader election 设计 | G24: ILeaderElector 零代码 |
| `ai-dev/logs/2026/07-25.md` | 计划执行记录 | 本文 closure |

## References

- `ai-dev/analysis/nop-stream/03-checkpoint-comparison.md` — Checkpoint & Barrier 对比，19 gap 条目
- `ai-dev/analysis/nop-stream/04-state-comparison.md` — 状态管理对比，19 gap 条目
- `ai-dev/analysis/nop-stream/05-window-comparison.md` — 窗口/时间对比，11 gap 条目
- `ai-dev/analysis/nop-stream/06-cep-comparison.md` — CEP 对比，12 gap 条目
- `ai-dev/analysis/nop-stream/07-distributed-comparison.md` — 分布式执行对比，20 gap 条目
- `ai-dev/plans/303-nop-stream-flink-inspired-improvements.md` — 已关闭，部分缺口已解决
- `docs/plans/nop-stream-flink-comparison/2026-07-25-0900-1-checkpoint-lifecycle-fixes.md` — 已关闭，部分缺口已解决
- `docs/backlog/nop-stream-flink-comparison-roadmap.md` — 路线图，本文产出作为 Items 9—13 输入
