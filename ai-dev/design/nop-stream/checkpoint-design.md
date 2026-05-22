# Checkpoint 与 Exactly-Once 处理设计

> Status: active
> Created: 2026-05-19
> Updated: 2026-05-22
> Parent: `architecture.md` §3（执行模型）、`core-design.md` §3（状态管理）
> See also: `component-roadmap.md` §3 C5（Checkpoint 生产化计划）
## 1. 定位与目标

nop-stream 的 checkpoint 子系统为流处理管线提供**容错和状态一致性**保障。核心目标是实现端到端 exactly-once 语义：即使发生故障，每条记录的效果也**恰好出现一次**。

本文档描述 checkpoint 子系统的完整架构、端到端 exactly-once 的实现机制，以及当前的集成状态。

### 1.1 设计决策

**选了什么**：采用 Chandy-Lamport 分布式快照算法的 barrier 对齐模式，与 Flink 的 checkpoint 机制保持概念一致。

**为什么不用其他方案**：
- WAL（Write-Ahead Log）：需要重放整个日志，恢复慢，不适合长时间运行的流任务
- 事务日志（Kafka-style offset commit）：仅保证 source 位移一致，无法覆盖 sink 端外部副作用
- Barrier 模式可以在单进程和多进程场景下使用同一套抽象，且天然支持增量快照

## 2. 组件总览

checkpoint 子系统由以下组件构成，分布在 core 和 runtime 两个模块中：

```
                          ┌──────────────────┐
     定时触发 ───────────►│ CheckpointCoordinator │
                          │   (runtime)        │
                          └───────┬───────────┘
                                  │ trigger
                                  ▼
                          ┌──────────────────┐
     数据流中注入 ────────►│ CheckpointBarrier  │
                          │   (core)          │
                          └───────┬───────────┘
                                  │ 传播
                                  ▼
                          ┌──────────────────┐
     多输入对齐 ──────────►│  BarrierAligner    │
                          │   (runtime)        │
                          └───────┬───────────┘
                                  │ aligned
                                  ▼
                          ┌──────────────────┐
     状态快照 ────────────►│  PendingCheckpoint │◄─── ACK from tasks
                          │   (runtime)        │
                          └───────┬───────────┘
                                  │ complete
                                  ▼
                    ┌─────────────────────────────┐
                    │                             │
                    ▼                             ▼
          ┌──────────────┐             ┌──────────────────────┐
          │ICheckpointStorage│          │ CheckpointListener    │
          │  (core 抽象)     │          │  (core 抽象)          │
          └──────┬───────┘             └──────────────────────┘
                 │                               │
         ┌───────┴───────┐               ┌───────┴───────────────┐
         ▼               ▼               ▼                       ▼
 LocalFileStorage  JdbcStorage    AbstractUdfStream     TwoPhaseCommit
                                Operator.notify()     SinkFunction
```

### 2.1 模块分层

| 层 | 组件 | 模块 | 职责 |
|---|---|---|---|
| 抽象层 | `CheckpointBarrier` / `CheckpointConfig` / `CheckpointIDCounter` / `ICheckpointStorage` / `CheckpointListener` / `TwoPhaseCommitSinkFunction` / `CheckpointedSourceFunction` / `TaskStateSnapshot` / `OperatorSnapshotResult` / `CompletedCheckpoint` / **`CheckpointPlan`** / **`TaskLocation`** | core | 定义 checkpoint 相关的数据结构、配置和接口契约 |
| 计划层 | **`CheckpointPlanBuilder`** | runtime | 从 GraphExecutionPlan 生成 CheckpointPlan（依赖 runtime 的 GraphExecutionPlan 类） |
| 实现层 | `CheckpointCoordinator` / `PendingCheckpoint` / `BarrierAligner` / `AlignedBarrier` / `CheckpointMetrics` / `LocalFileCheckpointStorage` / `JdbcCheckpointStorage` | runtime | 提供 checkpoint 的协调、状态跟踪、barrier 对齐、指标统计和持久化实现 |

### 2.2 CheckpointPlan：执行计划与 Checkpoint 的桥梁

CheckpointPlan 是 checkpoint 子系统对管线拓扑的**只读视图**。它在执行计划生成阶段一次性构建，将 checkpoint 需要的拓扑信息从执行引擎中解耦出来。

**为什么需要 CheckpointPlan**（设计决策）：

Coordinator 需要 three 类拓扑信息才能工作：barrier 注入点（source task 在哪）、ACK 跟踪集合（哪些 task 必须全部 ACK）、状态恢复路由（恢复时哪块状态分给哪个 task）。如果这些信息隐式地从执行计划中推导（当前做法），checkpoint 逻辑与执行引擎耦合——执行引擎改了，checkpoint 就坏了。CheckpointPlan 把这个依赖显式化、稳定化。

**参考**：Apache SeaTunnel 的 `CheckpointPlan` 在 `PhysicalPlanGenerator.generate()` 阶段生成，nop-stream 采纳同一模式。

**拒绝了什么**：
- 让 Coordinator 直接遍历 GraphExecutionPlan——违反关注点分离，执行引擎变更会破坏 checkpoint
- 不做 CheckpointPlan、只在 Coordinator 里硬编码拓扑假设——只适用于单链管线，不可扩展
- 只修 BUG 1/4 的 key names 不引入 CheckpointPlan——可以修 bug 但不解决架构耦合问题，且无法支持分布式执行

**诚实评估**：BUG 1（keyed state 碰撞）和 BUG 4（状态恢复路由错误）确实可以通过直接修改 `CheckpointBarrierTracker.acknowledgeOperator()` 和 `buildSnapshotFromTaskState()` 来修复，无需引入 CheckpointPlan。但这样修只是打补丁——checkpoint 逻辑仍然与执行引擎耦合，无法支持分布式执行。CheckpointPlan 的引入是为了长期架构正确性，而非最小 bug fix。

#### CheckpointPlan 数据结构

```
CheckpointPlan {
    int version = 1;                                // 序列化格式版本
    String jobId;                                   // 作业标识
    String pipelineId;                              // 管线标识
    List<TaskLocation> allTasks;                    // 所有 task 实例（ACK 跟踪）
    List<TaskLocation> sourceTasks;                 // barrier 注入点（source 顶点的所有并行实例）
    Map<TaskLocation, List<OperatorStateMapping>> stateMappings;  // 每 task 的算子状态映射
}

TaskLocation {
    String jobId;            // 作业标识（为分布式执行预留路由信息）
    String pipelineId;       // 管线标识
    String vertexId;         // 顶点 ID，如 "vertex-1"
    int taskIndex;           // 0 到 parallelism-1
}

OperatorStateMapping {
    int operatorIndex;            // 算子在链中的位置（从 0 开始）
    String operatorStateKey;      // 该算子 operator state 的存储键名，如 "operator-0"
    String keyedStateStorageKey;  // 该算子 keyed state 的存储键名，如 "operator-0-keyed"（null 表示无 keyed state）
    boolean isTwoPhaseCommit;     // 该算子是否为 TwoPhaseCommitSink（决定 preCommit/commit/rollback 生命周期）
}
```

**设计要点说明**：

1. **`TaskLocation` 包含 `jobId` + `pipelineId`**：为分布式执行预留路由信息。当前单进程执行时，Coordinator 通过 `TaskLocation` 的 `vertexId` + `taskIndex` 定位本地 task；分布式执行时，通过 `jobId` + `pipelineId` 路由到正确的节点。参考 SeaTunnel 的 `TaskLocation` 包含 `TaskGroupLocation`（jobId + pipelineId + taskGroupId）。

2. **`TaskLocation` 不含 task 角色，角色在 `OperatorStateMapping` 级别**：一个 task 包含算子链，链内可以有多个角色（如 `[SourceOperator, MapOperator, TwoPhaseCommitSinkOperator]`）。角色不是 task 级别的属性，而是算子级别的属性。Coordinator 通过 `sourceTasks.contains(taskLocation)` 判断是否为 source task，通过 `OperatorStateMapping.isTwoPhaseCommit` 判断哪个算子需要 2PC 生命周期。不需要 `TaskCheckpointRole` 枚举。

3. **`OperatorStateMapping` 用 `keyedStateStorageKey` 显式命名 keyed state**：如果为 null 表示该算子无 keyed state。快照时，算子的 operator state 存储在 `operatorStateKey` 下，keyed state 存储在 `keyedStateStorageKey` 下（如 `"operator-0-keyed"`）。恢复时，按 `keyedStateStorageKey` 精确取出对应算子的 keyed state blob，反序列化到 `IKeyedStateBackend`。dual-state 路由（同一算子同时有 operator state 和 keyed state）通过两个不同的键名自然解决。

4. **去掉了 `vertexParallelism`**：并行度信息已隐含在 `allTasks` 的基数中（同一 `vertexId` 的 TaskLocation 数量就是并行度）。`CheckpointPlanBuilder` 在构建时验证一致性，不单独存储以避免冗余不一致的风险。

5. **`sourceTasks` 包含所有并行实例**：如果 source 顶点的 parallelism=3，则 `sourceTasks` 包含 3 个 TaskLocation（taskIndex 0, 1, 2）。每个 source 实例独立注入 barrier。

6. **`version` 语义**：`version=1` 表示本文档描述的格式。恢复逻辑检查 `version <= CURRENT_VERSION`，拒绝未知版本。

#### 快照侧：OperatorStateMapping 如何传入 CheckpointBarrierTracker

`CheckpointBarrierTracker`（core 模块）在构建时接收 `List<OperatorStateMapping>`（由 `CheckpointPlanBuilder` 生成，经 `GraphModelCheckpointExecutor` 传入）。`acknowledgeOperator(operatorIndex, snapshotResult)` 使用 `mappings[operatorIndex]` 确定存储键名：

```
acknowledgeOperator(operatorIndex, snapshotResult):
    mapping = operatorStateMappings[operatorIndex]
    if snapshotResult has operator states:
        for each (name, bytes) in snapshotResult.operatorStates:
            currentSnapshot.operatorStates.put(mapping.operatorStateKey, bytes)
    if snapshotResult has keyed states:
        if mapping.keyedStateStorageKey != null:
            currentSnapshot.keyedStates.put(mapping.keyedStateStorageKey, bytes)
```

#### CheckpointPlan 如何修复 BUG 1 和 BUG 4

**BUG 1（keyed state 碰撞）**：当前所有算子用 `"keyed-state"` 作为存储键名。引入 CheckpointPlan 后，`OperatorStateMapping` 为每个算子分配唯一的 `keyedStateStorageKey`（如 `"operator-0-keyed"`, `"operator-1-keyed"`）——不再碰撞。

注意：这只是存储层的修复。`IKeyedStateBackend` 本身也需要按算子隔离——同一链中的多个算子如果都使用 keyed state，需要各自独立的 state backend 实例，或在 state name 中加入算子 ID 前缀。这是一个独立于 CheckpointPlan 的 state backend 层修复。

**BUG 4（状态恢复路由错误）**：当前 `buildSnapshotFromTaskState()` 把所有状态发给每个算子。引入 CheckpointPlan 后，恢复路由如下：

```
对每个 TaskLocation:
  mappings = CheckpointPlan.stateMappings[taskLocation]
  对每个 OperatorStateMapping mapping:
    operatorState = taskSnapshot.operatorStates[mapping.operatorStateKey]
    if (mapping.keyedStateStorageKey != null):
      keyedState = taskSnapshot.keyedStates[mapping.keyedStateStorageKey]
    将 (operatorState, keyedState) 路由到第 mapping.operatorIndex 个算子
```

Dual-state 路由（同一算子同时有 operator state 和 keyed state）通过 `operatorStateKey` + `keyedStateStorageKey` 两个独立键名自然解决——恢复时按这两个键名分别从 `operatorStates` 和 `keyedStates` Map 中取出对应的数据。

#### CheckpointPlan 与 Savepoint 的交互

Savepoint 是手动触发的 checkpoint，可以跨管线版本保留和恢复。交互规则：

1. **CheckpointPlan 随 savepoint 序列化存储**：savepoint 目录中包含 `checkpoint-plan.json`（`JsonTool` 序列化），记录生成时的管线拓扑
2. **恢复时检查兼容性**：新的 `CheckpointPlan` 与 savepoint 中的旧 plan 对比，验证算子数量和状态键名是否匹配。不匹配的具体含义：算子数量不同、`operatorStateKey` 找不到、或 `keyedStateStorageKey` 不一致
3. **恢复模式**：`STRICT`（默认）—— 任何不匹配都拒绝恢复并报告具体不兼容项；`LENIENT` —— 忽略不匹配的算子（记录警告），恢复可以匹配的部分。`LENIENT` 模式允许管线小幅修改后恢复，避免全部状态丢失
4. **不支持自动 schema 迁移**：当前不支持算子增删、并行度变更后的自动状态重分布。这是一个显式限制，未来可通过 `pipelineActions` 扩展

#### CheckpointPlan 与 BarrierAligner 的关系

`BarrierAligner` 的配置（多输入算子的输入数量）不从 CheckpointPlan 中获取——它从执行引擎的 `GraphExecutionPlan` 中获取。CheckpointPlan 不记录边（edge）信息，只记录顶点（task）和状态映射。恢复时，执行引擎先从 `GraphExecutionPlan` 重建算子拓扑（包括 `BarrierAligner` 的输入数量），然后从 CheckpointPlan 恢复状态。

**跨执行引擎的 Savepoint 恢复假设**：如果 savepoint 是在引擎 A 上生成的，恢复到引擎 B（如从嵌入式切换到分布式），CheckpointPlan 假设两个引擎产生**相同的算子链拓扑**（相同的 chain 配置、相同的并行度）。如果链拓扑不同（如引擎 B 将某两个算子拆分为不同 chain），恢复可能失败。这是一个显式限制——跨引擎恢复要求管线拓扑一致。

#### CheckpointPlan 的生成时机

```
JobGraph
  ↓ GraphExecutionPlan 构建
GraphExecutionPlan
  ↓ CheckpointPlanBuilder.build(graphExecutionPlan)
CheckpointPlan
  ↓ 传入 CheckpointCoordinator 构造器
CheckpointCoordinator 使用 CheckpointPlan 驱动 barrier 注入和 ACK 跟踪
```

#### Coordinator 内部类型对齐

引入 `TaskLocation` 后，`CheckpointCoordinator` 及相关类的以下字段需要类型变更：

| 类 | 字段 | 当前类型 | 目标类型 |
|---|---|---|---|
| `CheckpointCoordinator` | `tasksToAcknowledge` | `Set<Long>` | `Set<TaskLocation>` |
| `CheckpointCoordinator` | `acknowledgeTask()` 参数 | `long taskId` | `TaskLocation` |
| `PendingCheckpoint` | `notYetAcknowledgedTasks` | `Set<Long>` | `Set<TaskLocation>` |
| `PendingCheckpoint` | `taskStates` key | `Long` | `TaskLocation` |
| `CompletedCheckpoint` | `taskStates` key | `Map<Long, TaskStateSnapshot>` | `Map<TaskLocation, TaskStateSnapshot>` |
| `CompletedCheckpoint` | `getTaskState` / `addTaskState` 参数 | `long taskId` | `TaskLocation` |
| `CompletedCheckpoint` | `jobId` / `pipelineId` | `long` / `int` | `String`（与 `TaskLocation` 对齐） |
| `CheckpointBarrierTracker` | `taskId` 字段 + 构造器参数 | `long` | `TaskLocation` |
| `CheckpointBarrierTracker` | `currentSnapshot` 构建 | `new TaskStateSnapshot(long, long)` | `new TaskStateSnapshot(TaskLocation, long)` |
| `TaskStateSnapshot` | `taskId` | `long` | `TaskLocation` |

所有变更统一使用 `TaskLocation`，避免 `long` ↔ `TaskLocation` 转换的维护负担。

## 3. 核心数据结构

### 3.1 CheckpointBarrier

Barrier 是注入到数据流中的特殊标记，随数据一起在算子间传播。它的作用是"切割"数据流——barrier 之前的数据属于上一个快照，barrier 之后的数据属于下一个快照。

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | long | checkpoint 的唯一递增 ID |
| `timestamp` | long | 触发时间戳 |
| `checkpointType` | `CheckpointType` | CHECKPOINT / SAVEPOINT / COMPLETED_POINT_TYPE |

`snapshot()` 始终返回 true（当前所有 barrier 都触发快照）。`prepareClose()` 对 `COMPLETED_POINT_TYPE` 和 `SAVEPOINT` 类型返回 true（通过 `isFinalCheckpoint()` 判断），用于作业关闭前的最终快照。

### 3.2 CheckpointConfig

| 参数 | 默认值 | 含义 |
|---|---|---|
| `checkpointEnabled` | true | 是否启用 checkpoint |
| `checkpointInterval` | 60000ms（1 分钟） | 触发间隔 |
| `checkpointTimeout` | 600000ms（10 分钟） | 单次 checkpoint 超时 |
| `minPause` | 500ms | 两次 checkpoint 之间的最小间隔 |
| `maxConcurrentCheckpoints` | 1 | 最大并发 checkpoint 数 |
| `maxRetainedCheckpoints` | 5 | 保留的已完成 checkpoint 数 |
| `storageType` | "local" | 存储类型（"local" / "jdbc"） |

### 3.3 TaskStateSnapshot

单个 task 的状态快照，包含 operator state 和 keyed state 两部分：

| 字段 | 类型 | 含义 |
|---|---|---|
| `taskId` | long | task 标识 |
| `operatorStates` | `Map<String, byte[]>` | 算子级状态（名称 → 序列化值） |
| `keyedStates` | `Map<String, byte[]>` | 键控状态（名称 → 序列化值） |

这是一个简化的设计：Flink 使用 `OperatorSubtaskState` + `KeyGroupRange` 支持分布式状态重分布，nop-stream 去除了 key-group 概念，直接用 Map 存储。

## 4. CheckpointCoordinator：生命周期管理

`CheckpointCoordinator` 是整个 checkpoint 子系统的中枢，负责 checkpoint 的触发、跟踪、完成、中止和恢复。

### 4.1 核心状态

```
checkpointIdCounter: CheckpointIDCounter         // 生成单调递增的 checkpoint ID
pendingCheckpoints: ConcurrentHashMap<Long, PendingCheckpoint>  // checkpointId → 进行中的快照
latestCompletedCheckpoint: CompletedCheckpoint                  // 最近完成的快照
tasksToAcknowledge: Set<Long>                                   // 需要 ACK 的 task 集合
scheduler: ScheduledExecutorService                             // 定时触发器
timeoutScheduler: ScheduledExecutorService                      // 超时检查器
listeners: List<CheckpointListener>                             // 完成通知监听器
```

`CheckpointIDCounter`（core 模块）使用 `AtomicLong.getAndIncrement()` 生成单调递增的 checkpoint ID，是整个生命周期的基础。

`OperatorSnapshotResult`（core 模块）是单个算子的快照结果，包含 `operatorStates`、`keyedStates`、`rawKeyedStates` 三个 Map。`CheckpointedSourceFunction.snapshotState()` 返回此类型。多个算子的快照聚合为 `TaskStateSnapshot`。

### 4.2 完整生命周期

一个 checkpoint 从触发到结束经历以下阶段：

```
trigger ──► pending ──► (all ACKed) ──► store ──► notify ──► completed
                │
                └──► (timeout/error) ──► abort ──► notify aborted
```

#### 阶段 1：触发（trigger）

```
CheckpointCoordinator.tryTriggerPendingCheckpoint()
    ├─ 检查并发限制: numPending < maxConcurrent
    ├─ 递增 checkpointIdCounter 获取新 ID
    ├─ 创建 PendingCheckpoint（包含所有待 ACK 的 task）
    ├─ 注册到 pendingCheckpoints
    ├─ 注册 CompletableFuture 回调
    │   ├─ 成功 → completePendingCheckpoint()
    │   └─ 异常 → abortPendingCheckpoint()
    └─ 注册超时调度（超时后自动 abort）
```

触发后，Coordinator 应将 `CheckpointBarrier` 注入数据流。当前实现中，触发和 barrier 注入是分离的——Coordinator 负责"决定要做什么"，barrier 的实际注入需要由执行引擎完成。

#### 阶段 2：ACK 收集（acknowledge）

```
CheckpointCoordinator.acknowledgeTask(taskId, checkpointId, state)
    └─ PendingCheckpoint.acknowledgeTask(taskId, state)
        ├─ 从 notYetAcknowledgedTasks 中移除 taskId
        ├─ 将 state 记录到 taskStates
        └─ 如果 notYetAcknowledgedTasks 为空
            └─ completableFuture.complete(toCompletedCheckpoint())
```

每个 task 在处理完 barrier 后，将自身的状态快照上报给 Coordinator。当所有 task 都上报完毕，PendingCheckpoint 自动通过 CompletableFuture 触发完成回调。

#### 阶段 3：完成（complete）

```
CheckpointCoordinator.completePendingCheckpoint(completed)
    ├─ checkpointStorage.storeCheckPoint(completed)    // 持久化
    │   └─ 失败 → abortPendingCheckpoint("Failed to store")
    ├─ pendingCheckpoints.remove(checkpointId, pending)
    ├─ latestCompletedCheckpoint = completed
    ├─ decrementPendingCheckpointCount
    ├─ cleanupOldCheckpoints()                         // 保留 maxRetained 个
    └─ notifyCheckpointCompleted(checkpointId)          // 通知所有 listener
```

#### 阶段 4：中止（abort）

```
CheckpointCoordinator.abortPendingCheckpoint(pending, reason)
    ├─ pendingCheckpoints.remove(checkpointId)
    ├─ removed.abort(reason)
    ├─ decrementPendingCheckpointCount
    └─ notifyCheckpointAborted(checkpointId)
```

中止原因包括：超时（timeoutScheduler 触发）、存储失败、主动取消。

#### 阶段 5：恢复（restore）

```
CheckpointCoordinator.restoreFromCheckpoint()
    ├─ checkpointStorage.getLatestCheckpoint(jobId, pipelineId)
    ├─ checkpoint.setRestored(true)
    └─ latestCompletedCheckpoint = checkpoint
```

恢复逻辑从存储中读取最近完成的 checkpoint，标记为已恢复。下游算子可以据此重建状态。

### 4.3 定时调度

`startCheckpointScheduler()` 启动一个 `SingleThreadScheduledExecutor`，按 `checkpointInterval` 间隔定时调用 `tryTriggerPendingCheckpoint(CHECKPOINT)`。调度器是 daemon 线程，随 JVM 退出。

### 4.4 超时机制

每次触发 checkpoint 后，通过 `timeoutScheduler` 注册一个延迟任务。如果在 `checkpointTimeout`（默认 10 分钟）内未收到所有 ACK，自动中止该 checkpoint。

## 5. CheckpointedSourceFunction：Source 端状态管理

`CheckpointedSourceFunction` 是 `SourceFunction` 的扩展接口，支持 source 端的 checkpoint：

| 方法 | 含义 |
|---|---|
| `snapshotState(checkpointId)` | 将 source 当前的消费位置（如 Kafka offset）序列化为 `OperatorSnapshotResult` |
| `initializeState(state)` | 从 checkpoint 恢复 source 的消费位置 |

Source 端的 exactly-once 依赖这个接口：source 在 barrier 到达时记录当前 offset，恢复时从记录的 offset 重新开始消费。如果 source 没有实现这个接口，checkpoint 无法保证 source 端不丢不重。

## 6. BarrierAligner：多输入屏障对齐

当算子有多个输入（如 join、union 后的算子）时，需要等待所有输入通道都收到同一 checkpoint 的 barrier 后，才能认为该 checkpoint 的 barrier 已经完全到达。这就是 barrier 对齐。

### 6.1 对齐算法

```
BarrierAligner(numberOfInputs)
    ├─ 每个输入通道维护一个 TreeMap<checkpointId, CheckpointBarrier>
    ├─ 输出队列: Queue<AlignedBarrier>

processBarrier(barrier, inputIndex)
    ├─ lock.lock()
    ├─ 将 barrier 放入 inputBarriers[inputIndex]
    └─ checkComplete()
        ├─ 统计每个 checkpointId 在所有输入中出现的次数
        ├─ 如果 count == numberOfInputs → 对齐完成
        │   ├─ 创建 AlignedBarrier（包含对齐耗时）
        │   ├─ 放入 alignedBarriers 队列
        │   ├─ signalAll() 唤醒等待者
        │   └─ 从所有输入的 TreeMap 中移除该 barrier
        └─ 否则 → 等待更多输入

findCompletedCheckpointId()
    ├─ 遍历所有输入的 TreeMap，统计每个 checkpointId 的出现次数
    ├─ 找到 count == numberOfInputs 的最小 checkpointId
    └─ 返回（保证按序完成）
```

### 6.2 关键设计点

- **TreeMap 保序**：每个输入通道的 barrier 按 checkpointId 排序，保证按序对齐
- **ReentrantLock + Condition**：支持阻塞等待（`pollAlignedBarrier(timeout, unit)`），用于消费端同步
- **最小 ID 优先**：对齐时选择所有输入中都已到达的最小 checkpointId，保证 checkpoint 按序完成
- **线程安全**：所有操作在 lock 保护下进行，支持多线程并发调用 `processBarrier()`

### 6.3 AlignedBarrier

对齐结果，记录：

| 字段 | 含义 |
|---|---|
| `checkpointId` | 对齐的 checkpoint ID |
| `checkpointType` | checkpoint 类型 |
| `triggerTimestamp` | 触发时间 |
| `alignedTimestamp` | 对齐完成时间 |
| `inputCount` | 参与对齐的输入数量 |
| `alignmentDuration` | 对齐耗时（alignedTimestamp - triggerTimestamp） |

## 7. Exactly-Once 语义实现

### 7.1 Exactly-Once 的含义

在流处理中，exactly-once 语义意味着：**每条记录对系统状态和外部副作用的影响恰好发生一次**。这需要三个层面的保证：

1. **Source 端**：记录不丢失、不重复读取（通过 checkpoint 记录 offset）
2. **处理端**：算子状态可以通过 checkpoint 恢复（通过 TaskStateSnapshot）
3. **Sink 端**：外部写入不重复（通过 TwoPhaseCommitSinkFunction）

### 7.2 端到端 Exactly-Once 流程

以下是 nop-stream 中端到端 exactly-once 的完整流程（假设各组件均已正确对接）：

```
时间线 ─────────────────────────────────────────────────────────────►

[Source]          [Operator-1]      [Operator-N]      [Sink(2PC)]
   │                   │                 │                 │
   │── data ──────────►│── data ────────►│── data ────────►│ invoke()
   │                   │                 │                 │
   │── barrier ───────►│── barrier ─────►│── barrier ─────►│ preCommit(id)
   │  [snapshot]       │  [snapshot]     │  [snapshot]     │
   │  ACK(id,state)    │  ACK(id,state)  │  ACK(id,state)  │
   │       │           │       │         │       │         │
   └───────┴───────────┴───────┴─────────┴───────┘         │
                           │                                │
                    CheckpointCoordinator                   │
                    store + notifyComplete ──────────────────│ commit(id)
                                                            │ beginTransaction()
```

**注意**：Barrier 从 Source 注入，随数据流向下游传播。Source 最先做快照，Sink 最后收到 barrier 并执行 preCommit。所有 ACK 收齐后 Coordinator 持久化并通知完成，Sink 才执行 commit。

**步骤详解**：

1. **触发**：CheckpointCoordinator 定时触发，生成 CheckpointBarrier（id=N）
2. **注入**：Barrier 注入 Source 的数据流，随数据一起向下游传播
3. **处理**：
   - 算子收到 barrier 时，暂停处理新数据，对当前状态做快照
   - 多输入算子通过 BarrierAligner 等待所有输入的 barrier 到齐后才做快照
4. **ACK**：每个算子完成快照后，将 TaskStateSnapshot 上报给 Coordinator
5. **完成**：Coordinator 收到所有 ACK → 持久化到 ICheckpointStorage → 通知所有 CheckpointListener
6. **提交**：
   - 普通算子：收到 `notifyCheckpointComplete(id)` 后确认状态已持久化
   - 2PC Sink：收到 `notifyCheckpointComplete(id)` 后调用 `commit(id)` 提交外部事务，然后 `beginTransaction()` 开启新事务
7. **中止**：如果 checkpoint 超时或失败，2PC Sink 调用 `rollback()` 回滚未提交的事务

### 7.3 TwoPhaseCommitSinkFunction：Sink 端一致性

2PC（两阶段提交）是端到端 exactly-once 的关键。nop-stream 的 `TwoPhaseCommitSinkFunction` 定义了五个阶段：

| 阶段 | 方法 | 调用时机 | 作用 |
|---|---|---|---|
| 开始事务 | `beginTransaction()` | 作业启动 / 上一次 commit 后 | 开启外部事务（如 DB transaction、Kafka transaction） |
| 写入数据 | `invoke(value)` | 每条记录到达时 | 在当前事务中写入 |
| 预提交 | `preCommit(checkpointId)` | checkpoint barrier 到达时 | 准备提交，不再接受新数据到该事务 |
| 提交 | `commit(checkpointId)` | 收到 `notifyCheckpointComplete` 时 | 外部提交事务，数据对外可见 |
| 回滚 | `rollback()` | checkpoint 失败 / 恢复时 | 回滚当前事务，丢弃未提交数据 |

**恢复语义**：`recover(checkpointId)` 默认实现为 `rollback() + beginTransaction()`。即恢复时先回滚任何残留事务，再开始新事务。这保证了故障恢复后的状态干净。

### 7.4 CheckpointListener：完成通知契约

`CheckpointListener` 定义了两个回调：

- `notifyCheckpointComplete(checkpointId)` — checkpoint 成功持久化后调用
- `notifyCheckpointAborted(checkpointId)` — checkpoint 中止后调用

**Checkpoint Subsuming Contract**（关键契约）：

> Checkpoint ID 严格递增。收到 `notifyCheckpointComplete(N)` 时，可以安全假设所有 ID < N 的 checkpoint 都已完成（无论是否收到过通知）。实现者应一次性提交所有 ≤ N 的未提交事务。

这意味着即使中间某些 checkpoint 的通知丢失，只要收到更大的 ID，就可以安全地提交所有挂起的工作。这个契约简化了实现，不需要严格依赖每个通知都到达。

### 7.5 传播路径

Checkpoint 完成通知的传播路径：

```
CheckpointCoordinator.notifyCheckpointCompleted(id)
    └─ 遍历 listeners
        └─ 直接注册的 listener.notifyCheckpointComplete(id)

算子内部的传播：
AbstractUdfStreamOperator.notifyCheckpointComplete(id)
    └─ if (userFunction instanceof CheckpointListener)
        └─ userFunction.notifyCheckpointComplete(id)
```

即：算子的 UDF 如果实现了 `CheckpointListener`（如 `TwoPhaseCommitSinkFunction`），会自动收到通知。

## 8. ICheckpointStorage：持久化

### 8.1 接口契约

| 方法 | 含义 |
|---|---|
| `storeCheckPoint(completed)` | 存储完成的 checkpoint |
| `getLatestCheckpoint(jobId, pipelineId)` | 获取最近完成的 checkpoint |
| `getAllCheckpoints(jobId)` | 获取所有 checkpoint（按 ID 降序） |
| `getLatestCheckpoints(jobId, count)` | 获取最近 N 个 |
| `deleteCheckpoint(jobId, pipelineId, checkpointId)` | 删除指定 checkpoint |
| `deleteAllCheckpoints(jobId)` | 删除作业的所有 checkpoint |

### 8.2 实现

| 实现 | 存储方式 | 适用场景 |
|---|---|---|
| `LocalFileCheckpointStorage` | JSON 文件（目录结构：`{baseDir}/checkpoint-{jobId}-{pipelineId}-{checkpointId}.json`） | 单机开发测试 |
| `JdbcCheckpointStorage` | JDBC 数据库表（通过 `IJdbcTemplate` 多数据库适配） | 生产环境、需要跨进程共享 |

两个实现都是 nop-stream-runtime 中的类，core 只定义接口。

### 8.3 JdbcCheckpointStorage 多数据库设计

#### 设计决策

**选了什么**：`JdbcCheckpointStorage` 通过 Nop 平台的 `IJdbcTemplate` + `IDialect` 访问数据库，而非直接使用 `java.sql.Connection`。

**为什么**：
- `IJdbcTemplate` 封装了连接管理、事务管理、方言适配、分页——Nop 平台的 18 种方言（MySQL/PostgreSQL/Oracle/H2/DM/DB2/MariaDB/DuckDB 等）已通过 `.dialect.xml` 定义完毕
- 同一存储实现自动适配所有数据库，无需为每种数据库写适配代码
- `nop-batch-jdbc` 的 `JdbcBatchConsumerProvider` 已验证了这一模式

**拒绝了什么**：
- 直接 `DataSource.getConnection()` + 手写 SQL——违反 Nop 平台规范，维护成本高，只能支持 MySQL
- `IOrmTemplate`（ORM）——checkpoint 存储只有一张简单表，不需要 ORM 的 session/cache 开销

#### 依赖关系

```
JdbcCheckpointStorage
  ├── IJdbcTemplate (NopIoC 注入)
  ├── String querySpace (可配置，默认 "default")
  └── 通过 IJdbcTemplate 获取 IDialect，自动适配数据库
```

#### 实现要求

| 维度 | 当前（需替换） | 目标（正确） |
|------|------------|------------|
| 数据库访问 | `DataSource.getConnection()` | `IJdbcTemplate` 注入 |
| SQL 构建 | 字符串拼接 | `SQL.begin()` 构建器 |
| DDL | MySQL 专用（`AUTO_INCREMENT` 等） | `IDialect` 类型映射 + 方言感知 |
| 分页 | `LIMIT 1` | `IJdbcTemplate.findPage()` |
| 事务管理 | 手动 `commit/rollback` | `ITransactionTemplate.runInTransaction()` |
| 表存在检查 | `INFORMATION_SCHEMA` | `IJdbcTemplate.existsTable()` |
| ID 生成 | `AUTO_INCREMENT` | 应用侧生成（checkpointId 已有 AtomicLong） |

参考实现模式：`nop-batch-jdbc` 的 `JdbcBatchConsumerProvider`（注入 `IJdbcTemplate`，通过 `getDialectForQuerySpace()` 获取方言，用 `SQL.begin()` 构建 SQL）。

## 9. 恢复流程

故障恢复时，完整的恢复流程为：

```
1. 新的 CheckpointCoordinator 创建
2. restoreFromCheckpoint()
   └─ storage.getLatestCheckpoint(jobId, pipelineId)
3. 从 CompletedCheckpoint 中提取各 task 的 TaskStateSnapshot
4. 将状态分发给对应的算子
5. 算子用恢复的状态初始化
6. TwoPhaseCommitSinkFunction.recover(checkpointId)
   └─ rollback() + beginTransaction()  // 回滚残留事务，开始新事务
7. Source 从 checkpoint 中记录的 offset 恢复读取
8. 继续正常的数据处理和 checkpoint 流程
```

## 10. 当前集成状态

> **Updated: 2026-05-20** — Checkpoint 子系统已通过 Plan 26 与图模型执行路径端到端对接。

### 10.1 已实现

checkpoint 子系统的核心组件已**完整实现**并通过测试：

- CheckpointCoordinator 完整生命周期（trigger → ACK → complete/abort → restore）
- PendingCheckpoint 的 ACK 跟踪和 CompletableFuture 自动完成
- BarrierAligner 多输入对齐（ReentrantLock + TreeMap + Condition）
- TwoPhaseCommitSinkFunction 接口定义（5 个阶段）
- CheckpointListener 通知契约（含 Subsuming Contract 文档）
- ICheckpointStorage 接口 + LocalFileCheckpointStorage + JdbcCheckpointStorage
- CheckpointConfig 可配置参数
- TaskStateSnapshot 的 operator/keyed state 分离
- CompletedCheckpoint 持久化和恢复
- CheckpointBarrier 继承 StreamElement，随数据流在算子间传播
- AbstractStreamOperator.snapshotState() / restoreState() 算子级状态快照与恢复
- IKeyedStateBackend.snapshotState() / restoreState() 状态后端序列化
- StreamSourceOperator.injectBarrier() barrier 注入点
- StreamSourceOperator / StreamSinkOperator / WindowOperator 各自的快照/恢复实现
- CheckpointBarrierTracker 单链管线 barrier 传播与 ACK 跟踪
- GraphModelCheckpointExecutor (runtime) 将 Coordinator 与执行引擎对接
- 集成测试覆盖：TestCheckpointCoordinator、TestCheckpointRecovery、TestBarrierAligner、TestCheckpointEndToEnd、TestBarrierPropagation、TestOperatorSnapshot

### 10.2 已对接（Plan 26 完成）

checkpoint 子系统**已通过图模型执行路径（`execute()`）与执行引擎对接**。对接范围：

1. **StreamExecutionEnvironment.execute() 集成 CheckpointCoordinator**
   - `enableCheckpointing(long interval)` 配置 checkpoint 参数
   - `GraphModelCheckpointExecutor`（runtime 模块）创建 Coordinator、注册 task、启动调度器
   - 定时触发 barrier 注入到 Source 算子

2. **Barrier 注入与传播**
   - `CheckpointBarrier` 继承 `StreamElement`，成为流元素类型体系的一部分
   - `Output.emitBarrier()` / `Input.processBarrier()` 统一分发模型
   - `ChainingOutput`、`TimestampedCollector`、`KeyExtractingOutput` 均已适配
   - Source 端通过 `injectBarrier()` 注入 barrier

3. **算子快照与 ACK**
   - `AbstractStreamOperator.processBarrier()` 调用 `snapshotState()` 后传播 barrier
   - `OperatorSnapshotResult` 通过回调上报给 `CheckpointBarrierTracker`
   - Tracker 收齐后调用 `coordinator.acknowledgeTask()`

4. **Sink 端 2PC 集成**
   - `TwoPhaseCommitSinkFunction.preCommit()` 在 barrier 到达时调用
   - `commit()` 在 checkpoint 完成通知时调用
   - `rollback()` 在 checkpoint 中止或恢复时调用

5. **恢复流程**
   - `GraphModelCheckpointExecutor` 启动时检查可恢复的 checkpoint
   - 各算子 `restoreState()` 从快照反序列化恢复状态
   - Source 恢复消费 offset（通过 `CheckpointedSourceFunction`）
   - Sink 恢复时 rollback 残留事务并 beginTransaction

6. **执行约束**
   - 图模型路径支持单链和多链管线（Plan 27 移除了单链约束）

### 10.3 已对接

以下功能已在 Plan 27-29 中实现：

1. **跨 Task 数据交换**（RecordWriter/RecordReader/InputGate）— Plan 27 已实现
2. **多链管线图模型执行** — Plan 27 已实现
3. **Savepoint 深度实现**（手动触发、恢复）— Plan 29 已实现
4. **时间模型对接**（TimestampAssigner/WatermarkGenerator/TimerService）— Plan 28 已实现

### 10.4 未对接（后续工作）

以下功能仍为独立计划，不阻塞当前闭环：

1. **增量快照优化**
2. **Unaligned checkpoint 模式**
3. **Key-group 重分布**

## 11. 与 Flink 的差异

| 维度 | Flink | nop-stream |
|---|---|---|
| Barrier 对齐 | aligned / unaligned 两种模式 | 仅 aligned 模式 |
| State Backend | HeapStateBackend / RocksDBStateBackend | 仅 MemoryStateBackend |
| State 分区 | Key-Group + KeyGroupRange 支持重分布 | 无 key-group，直接 Map 存储 |
| Coordinator 通信 | RPC（Akka/Netty），JM ↔ TM | 方法调用，同一进程内 |
| 存储后端 | FileSystem / S3 / State Backend 集成 | LocalFile / JDBC |
| Savepoint | 完整支持（手动触发、恢复、schema 兼容） | 手动触发 + 恢复已实现（Plan 29），schema 兼容为后续工作 |
| 增量快照 | RocksDB 支持增量 | 仅全量快照 |
| 对齐超时 | 可配置切换为 unaligned | 不支持 unaligned |

### 11.1 简化决策的理由

- **Coordinator 通信**：当前使用方法调用（同一进程内），但 Coordinator 接口设计为可扩展为 RPC
- **无 key-group**：简化版不实现分布式状态重分布，状态按 Map 存储即可。如果未来需要，可通过 key-group 扩展
- **仅 aligned 模式**：unaligned 模式增加复杂度且主要解决高延迟场景，对当前目标场景价值不大
- **全量快照**：状态数据量小（内存中），全量快照足够快。增量快照为后续优化方向

## 12. 已知限制

1. **JdbcCheckpointStorage 仅支持 MySQL** — 当前实现直接使用 `java.sql.Connection` + MySQL 专用 DDL，需改为基于 `IJdbcTemplate` 的多数据库实现（详见 §8.3）
2. **Barrier 注入线程安全问题** — 当前从独立 `ScheduledExecutorService` 线程注入 barrier，与 source 算子线程存在竞争。正确做法是让 barrier 作为数据流内的特殊元素注入
3. **状态后端仅内存** — 故障恢复时如果进程重启，内存状态丢失，依赖持久化的 checkpoint 文件
4. **无增量快照** — 每次全量序列化所有状态，状态量大时开销高
5. **barrier 对齐可能阻塞** — 多输入场景下，如果一个输入的 barrier 延迟，其他输入的数据会被缓冲
6. **无 exactly-once source 支持** — 没有可回放的 Source 实现（如 Kafka consumer），无法实现 source 端的 offset 管理和重放
