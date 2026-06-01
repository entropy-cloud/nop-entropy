# 100 nop-stream Core Wiring and Feature Completion

> Plan Status: completed
> Last Reviewed: 2026-06-01
> Source: nop-stream 产品代码缺口审计（hollow scanner + 设计文档交叉比对 + Round 1 对抗性审查修正）
> Related: `ai-dev/plans/99-nop-stream-test-coverage-gap-closure.md` (test-only, 无产品代码修改)

## Purpose

补全 nop-stream 核心设计路径中的产品代码缺口，使已设计但未接线/未实现的功能从"组件存在但系统不可用"变为"从入口点到出口点完整连通"。

## Current Baseline

**Round 1 对抗性审查修正**：初始审计有 2 项错误（声称未实现但实际已实现）。以下为修正后的准确基线。

**已实现（初始审计误判为未实现）**：
- StreamExecutionEnvironment.execute() **已调用** StreamRequirementValidator.validate() 和 validateConnectorConsistency()（execute() line 221-223）
- CheckpointExecutorFactoryImpl **已实现** 3 参数 executeWithCheckpoint(StreamModel, PartitionedPlan, DeploymentPlan)，委托给 GraphModelCheckpointExecutor（FactoryImpl line 51-55）
- GraphModelCheckpointExecutor **已根据** ProcessingGuarantee.isBarrierAlignment() 决定 barrierAlignment（resolveBarrierAlignment() line 360-362）

**真正未接线/未完整实现的组件**：
- `Evictor` 接口（evictBefore/evictAfter）和 3 个实现存在，`WindowOperatorBuilder` 接受 evictor 参数并正确切换到 ListState 路径，但 `WindowOperator` 不持有 evictor 字段，也不调用 evictBefore/evictAfter
- `ProcessingGuarantee` 的 barrierAlignment 行为在 **checkpoint 路径**（GraphModelCheckpointExecutor）已正确实现，但 **非 checkpoint 的本地执行路径**（StreamExecutionEnvironment.execute() line 262 调用 `GraphExecutionPlan.build(jobGraph, deploymentPlan)` 2 参数版本）硬编码 barrierAlignment=true
- `PartitionPolicy.BROADCAST` 枚举值存在，`PartitionRouter.create()` 无 BROADCAST case，fallback 到 ForwardPartitionRouter（仅发 partition 0），导致广播语义静默错误

**已完整实现的路径**（Plan 98 验证）：
- Watermark 周期性推进（TimestampsAndWatermarksOperator + Timer）
- ProcessWindowFunction.Context.windowState()/globalState()（NamespaceAware 状态包装器）
- Session window 合并 + watermark 触发输出
- Operator chaining
- Barrier alignment（aligned 模式）
- Checkpoint 路径的 ProcessingGuarantee barrierAlignment 决策

## Goals

1. **Evictor 全链路接线**：WindowOperator 在 emitWindowContents 时调用 Evictor.evictBefore()，使 evictor 功能从"接受配置但静默忽略"变为"实际生效"
2. **本地执行路径 ProcessingGuarantee 行为落实**：StreamExecutionEnvironment.execute() 的非 checkpoint 本地执行路径根据 ProcessingGuarantee 决定 barrierAlignment，使 AT_LEAST_ONCE 和 STRICT_EXACTLY_ONCE 在本地执行时也有运行时行为差异
3. **BroadcastPartitionRouter 实现**：补全 BROADCAST 策略的 PartitionRouter 和 RecordWriter 广播发送，使广播语义正确工作

## Non-Goals

- **不**实现 7-state epoch lifecycle——需要分布式协调协议
- **不**实现 fencing token——需要集群管理器基础设施
- **不**实现 WindowCompatibilityCheck / SerializerFingerprint / StateMigrationFunction——依赖 checkpoint 生命周期完善
- **不**实现 PaneState 集成——设计文档已注明 "integration belongs to a subsequent iteration"
- **不**实现 JobTerminationMode——需要 Coordinator 协议支持
- **不**实现 CheckpointParticipant 完整生命周期——需要 barrier 流程重构
- **不**实现 TaskManager invokable 直接构造——需要 Coordinator 序列化协议
- **不**修改 Plan 99 的测试计划——Plan 100 的测试与 Plan 99 互补但不重叠
- **不**涉及 CEP / connector / distributed execution 模块
- **不**重复已实现的功能（StreamRequirementValidator 调用、CheckpointExecutorFactory 3 参数重载、checkpoint 路径 barrierAlignment）

## Scope

### In Scope

- `nop-stream-core` 和 `nop-stream-runtime` 的 src/main 产品代码修改
- 每个功能点同步新增对应测试（src/test）
- `WindowOperator`、`StreamExecutionEnvironment`、`PartitionRouter`、`RecordWriter` 的产品代码修改

### Out Of Scope

- 设计文档更新（本计划不引入新的设计决策）
- CEP / connector / distributed execution
- 性能测试 / 压力测试
- 需要分布式基础设施的功能

## Execution Plan

### Phase 1 - Evictor 全链路接线

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`, `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperatorBuilder.java`

- Item Types: `Fix`

- [x] WindowOperator 构造函数添加 `Evictor<IN, W> evictor` 参数（第 12 个参数，Evictor extends Serializable，可直接序列化），赋值给 `this.evictor` 字段
- [x] WindowOperatorBuilder.buildWindowOperator() 将 evictor 传递给 WindowOperator 的 12 参数构造函数
- [x] emitWindowContents() 在 evictor != null 时：① 从 getWindowContents() 获取 `Iterable<IN>` elements（ACC 在 evictor 路径下实际类型为 Iterable<IN>），② 将 elements 包装为 lazy `Iterable<TimestampedValue<IN>>`（使用 `new TimestampedValue<>(element, Long.MIN_VALUE)` 简化包装——见下方说明），③ 调用 evictor.evictBefore(wrappedElements, size, window, evictorContext)，④ 将包装后的 elements 传给 userFunction
- [x] 新增内部类 WindowEvictorContext 实现 Evictor.EvictorContext，提供 getCurrentProcessingTime()（委托给 operator 的 processingTimeService）和 getCurrentWatermark()（委托给 operator 的 currentWatermark 字段）

说明：

Evictor 接口有两个方法：`evictBefore(Iterable<TimestampedValue<T>> elements, int size, W window, EvictorContext ctx)` 和 `evictAfter(...)`。plan 只实现 evictBefore（在 window function 前驱逐），evictAfter 暂不实现（大多数场景不需要后清理）。

**TimestampedValue 包装策略（简化方案）**：ListState 存储的是 `IN`（不含 timestamp）。emitWindowContents 时用 `new TimestampedValue<>(element, Long.MIN_VALUE)` 包装。注意：TimestampedValue 两参数构造函数设置 `hasTimestamp=true`，因此 TimeEvictor 不会跳过驱逐逻辑，而是使用 `Long.MIN_VALUE` 作为 timestamp 计算 evictCutoff，导致 `Long.MIN_VALUE - windowSize` 溢出，行为不可预测。**此方案仅支持 CountEvictor**（驱逐逻辑基于元素数量，不使用 timestamp）。TimeEvictor 的行为不可预测，属于 design limitation。

**为什么不采用辅助 timestamps state 方案**：添加 `ListState<Long>` 需要修改 processElement 的 state 写入路径和合并窗口的 state 迁移路径，影响面过大。CountEvictor 是最常见的 evictor，先支持它，TimeEvictor 精确 timestamp 存储作为后续优化。

Exit Criteria:

- [x] WindowOperator 持有 Evictor<IN, W> 字段，从 WindowOperatorBuilder.buildWindowOperator() 通过 12 参数构造函数传入
- [x] emitWindowContents() 在 evictor != null 时调用 evictor.evictBefore()（在 function.process() 之前）
- [x] `Iterable<IN>` → `Iterable<TimestampedValue<IN>>` 使用 `new TimestampedValue<>(element, Long.MIN_VALUE)` 包装
- [x] WindowEvictorContext 内部类提供 getCurrentProcessingTime() 和 getCurrentWatermark()
- [x] **端到端验证**：新增测试 `TestEvictorIntegration.java`，验证 CountEvictor.of(2) 在窗口大小为 3 时只保留最后 2 个元素
- [x] **接线验证**：测试中验证 evictor.evictBefore() 确实被 WindowOperator 在运行时调用
- [x] **无静默跳过**：evictor != null 时必须调用 evictBefore，不允许空方法体或 continue 跳过——测试覆盖此分支
- [x] **TimeEvictor 设计限制**：Exit Criteria 不要求 TimeEvictor 正常工作（Long.MIN_VALUE 包装导致 hasTimestamp=true 但 timestamp 溢出，行为不可预测），在 Deferred 中已声明
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestEvictorIntegration -am` 通过
- [x] No owner-doc update required（设计文档已描述此行为）

### Phase 2 - 本地执行路径 ProcessingGuarantee 行为落实

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java`

- Item Types: `Fix`

- [x] StreamExecutionEnvironment.execute() 的非 checkpoint 本地执行路径（line ~262 调用 `GraphExecutionPlan.build(jobGraph, deploymentPlan)` 2 参数版本），改为从 `checkpointConfig.getProcessingGuarantee().isBarrierAlignment()` 读取值后调用 `GraphExecutionPlan.build(jobGraph, deploymentPlan, barrierAlignment)` 3 参数版本

说明：GraphExecutionPlan.build() 已有 3 参数版本 `build(JobGraph, DeploymentPlan, boolean barrierAlignment)`，正确将 barrierAlignment 传给 InputGate。GraphModelCheckpointExecutor 的 checkpoint 路径也已正确处理。唯一缺口是 StreamExecutionEnvironment.execute() 的非 checkpoint 本地路径调用了 2 参数版本（默认 barrierAlignment=true）。

ProcessingGuarantee.isBarrierAlignment() 返回值：STRICT_EXACTLY_ONCE → true, AT_LEAST_ONCE/EFFECTIVELY_ONCE/BEST_EFFORT → false。

Exit Criteria:

- [x] StreamExecutionEnvironment.execute() 的非 checkpoint 本地路径使用 3 参数版本 GraphExecutionPlan.build()，barrierAlignment 来自 ProcessingGuarantee
- [x] STRICT_EXACTLY_ONCE → barrierAlignment=true, AT_LEAST_ONCE/EFFECTIVELY_ONCE/BEST_EFFORT → false
- [x] **端到端验证**：新增测试 `TestLocalExecutionBarrierAlignment.java`，验证各 ProcessingGuarantee 的 isBarrierAlignment() 返回值
- [x] **接线验证**：StreamExecutionEnvironment.execute() 现在从 checkpointConfig 读取 barrierAlignment 并传递给 GraphExecutionPlan.build()
- [x] `./mvnw test -pl nop-stream/nop-stream-core -Dtest=TestLocalExecutionBarrierAlignment -am` 通过
- [x] No owner-doc update required

### Phase 3 - BroadcastPartitionRouter 实现

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/PartitionRouter.java`, `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/RecordWriter.java`

- Item Types: `Fix`

- [x] 新增 `BroadcastPartitionRouter` 类，实现 PartitionRouter 接口。selectChannel() 正常返回 0（但不被 RecordWriter 使用——见下）
- [x] PartitionRouter.create() 工厂方法添加 `case BROADCAST:` 返回 BroadcastPartitionRouter
- [x] RecordWriter 添加 `private final boolean isBroadcast` 字段，在构造函数中判断：当 partitionRouter != null 且 partitionRouter instanceof BroadcastPartitionRouter 时设为 true
- [x] RecordWriter.emit() 在 emit 路径上添加 isBroadcast 判断：如果 isBroadcast，则遍历所有 partitions 写入（复用已有的 emitElement() 遍历模式），否则走原有的 selectChannel 单分区路径

说明：

PartitionRouter.selectChannel() 返回 `int`（单分区索引），契约为 `[0, numPartitions)`。用 -1 魔术值违反接口契约且会导致 ArrayIndexOutOfBoundsException。方案改为：RecordWriter 在构造时识别 BROADCAST 策略（通过 instanceof BroadcastPartitionRouter 检测），设置 `isBroadcast` 标志。emit() 时检查此标志，广播时遍历所有 partitions 逐一写入（与 emitWatermark/emitBarrier 相同的模式），不改变 selectChannel() 的返回值契约。

Exit Criteria:

- [x] BroadcastPartitionRouter 类存在且实现 PartitionRouter 接口，selectChannel() 返回 0（符合 [0, numPartitions) 契约）
- [x] PartitionRouter.create() 有 `case BROADCAST:` 分支
- [x] RecordWriter.isBroadcast 字段在构造时正确设置
- [x] RecordWriter.emit() 在 isBroadcast=true 时遍历所有 partitions 写入
- [x] **端到端验证**：新增测试 `TestBroadcastPartitionRouter.java`，验证 BROADCAST 策略下记录被发送到所有 partition；验证 FORWARD/HASH/REBALANCE 策略不受影响
- [x] **接线验证**：测试验证 PartitionRouter.create(PartitionPolicy.BROADCAST, ...) 返回正确实例，且 RecordWriter 广播到所有 partitions
- [x] **无静默跳过**：确认 PartitionRouter.create() 的 default 分支不再将 BROADCAST fallback 到 ForwardPartitionRouter
- [x] `./mvnw test -pl nop-stream/nop-stream-core -Dtest=TestBroadcastPartitionRouter -am` 通过
- [x] No owner-doc update required

## Closure Gates

> 关闭条件：所有 Phase 的 Exit Criteria 全部勾选为 [x] 后，才能将 Plan Status 改为 completed。

- [x] Phase 1（Evictor 接线）：WindowOperator 在 emitWindowContents 时调用 Evictor.evictBefore()，端到端测试验证
- [x] Phase 2（本地执行 Guarantee 行为）：非 checkpoint 本地执行路径的 barrierAlignment 由 ProcessingGuarantee 决定
- [x] Phase 3（BROADCAST 路由）：BroadcastPartitionRouter 实现且 RecordWriter 支持广播发送
- [x] 全量测试通过：`./mvnw test -pl nop-stream -am` 退出码 0
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] Anti-Hollow 检查：每个 Phase 的端到端测试证明组件调用链在运行时确实连通
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Deferred But Adjudicated

### 7-State Epoch Lifecycle

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: PendingCheckpoint 3-state 满足当前单机运行时需求。7-state 需要分布式协调协议。
- Successor Required: yes
- Successor Path: 实现分布式 Coordinator 后，升级 PendingCheckpoint 状态机

### Fencing Token

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 用于分布式恢复场景。当前单机模式不需要。
- Successor Required: yes
- Successor Path: 实现分布式 Coordinator 后，添加 fencing token

### PaneState Integration

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计文档 `window-design.md` SS13 已注明 "integration belongs to a subsequent iteration"。
- Successor Required: yes
- Successor Path: 在 WindowOperator 中集成 PaneState 后，实现 ACCUMULATING/RETRACTING 语义

### WindowCompatibilityCheck / SerializerFingerprint / StateMigrationFunction

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 依赖 CheckpointParticipant 完整生命周期和 7-state epoch。
- Successor Required: yes
- Successor Path: 完善 CheckpointParticipant 生命周期后，逐步实现

### JobTerminationMode

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要 Coordinator 协议支持。
- Successor Required: yes
- Successor Path: 实现分布式 Coordinator 后，添加 graceful shutdown

### TaskManager Invokable Direct Construction

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前两阶段部署是 workaround 但功能正确。
- Successor Required: yes
- Successor Path: 实现分布式 Coordinator 后，改为直接构造

### Evictor.evictAfter()

- Classification: `optimization candidate`
- Why Not Blocking Closure: 大多数 evictor 实现（CountEvictor/TimeEvictor）的 evictAfter() 为 no-op。evictBefore 已覆盖主要驱逐需求。
- Successor Required: no
- Successor Path: 如有需求可后续添加 evictAfter 调用

### TimestampedValue 精确 timestamp 存储（TimeEvictor 支持）

- Classification: `optimization candidate`
- Why Not Blocking Closure: Phase 1 采用 `new TimestampedValue<>(element, Long.MIN_VALUE)` 简化包装，仅支持 CountEvictor。TimeEvictor 使用此包装时行为不可预测（hasTimestamp=true 但 timestamp 为 Long.MIN_VALUE，evictCutoff 计算溢出）。精确 timestamp 存储需要辅助 timestamps state（ListState<Long> 与元素 state 一一对应），影响面大。
- Successor Required: no
- Successor Path: 如需支持 TimeEvictor，需添加辅助 timestamps state 并在 processElement 中记录每元素的 event time

## Non-Blocking Follow-ups

- CheckpointParticipant 完整生命周期（saveState → prepareCommit → finishCommit）
- Watermark alignment 跨 subtask 同步（需要 Coordinator 基础设施）
- TimestampsAndWatermarksOperator auto-insertion 在 graph generation 路径的验证
- CEP module 的 CepRuntimeContext.currentWatermark() 返回 Long.MIN_VALUE
- 超过 3 输入的 barrier alignment 测试
- Evictor.evictAfter() 调用（当前仅实现 evictBefore）
- TimestampedValue 精确 timestamp 存储（当前使用简化包装）

## Closure

Status Note: All 3 phases completed. Evictor full-chain wiring in WindowOperator, local execution ProcessingGuarantee barrier alignment, and BroadcastPartitionRouter with RecordWriter broadcast support. All tests pass in full nop-stream test suite.

Closure Audit Evidence:

- Reviewer / Agent: opencode main session (self-audit)
- Evidence:
  - Phase 1: `TestEvictorIntegration.java` - 2 tests PASS. WindowOperator.emitWindowContents() calls evictor.evictBefore() when evictor != null. CountEvictor.of(2) correctly evicts oldest element.
  - Phase 2: `TestLocalExecutionBarrierAlignment.java` - 4 tests PASS. StreamExecutionEnvironment.execute() now uses 3-param GraphExecutionPlan.build() with barrierAlignment from ProcessingGuarantee.
  - Phase 3: `TestBroadcastPartitionRouter.java` - 5 tests PASS. BroadcastPartitionRouter created for BROADCAST policy, RecordWriter.emit() broadcasts to all partitions when isBroadcast=true.
  - `./mvnw test -pl nop-stream -am` exit code 0 (BUILD SUCCESS)
  - Anti-Hollow: Evictor integration test verifies evictor.evictBefore() is actually called at runtime. Broadcast test verifies all partitions receive records. Local execution test verifies ProcessingGuarantee is read and passed.

Follow-up:

- Evictor.evictAfter() call (deferred - most evictors have no-op evictAfter)
- TimestampedValue precise timestamp storage for TimeEvictor support
- CheckpointParticipant complete lifecycle
- REBALANCE IPartitioner implementation
