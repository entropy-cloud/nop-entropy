# Checkpoint 与 Exactly-Once 处理设计

> Status: active
> Created: 2026-05-19
> Parent: `architecture.md` §3（执行模型）、`core-design.md` §3（状态管理）
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
| 抽象层 | `CheckpointBarrier` / `CheckpointConfig` / `CheckpointIDCounter` / `ICheckpointStorage` / `CheckpointListener` / `TwoPhaseCommitSinkFunction` / `CheckpointedSourceFunction` / `TaskStateSnapshot` / `OperatorSnapshotResult` / `CompletedCheckpoint` | core | 定义 checkpoint 相关的数据结构、配置和接口契约 |
| 实现层 | `CheckpointCoordinator` / `PendingCheckpoint` / `BarrierAligner` / `AlignedBarrier` / `CheckpointMetrics` / `LocalFileCheckpointStorage` / `JdbcCheckpointStorage` | runtime | 提供 checkpoint 的协调、状态跟踪、barrier 对齐、指标统计和持久化实现 |

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
| `JdbcCheckpointStorage` | JDBC 数据库表 | 生产环境、需要跨进程共享 |

两个实现都是 nop-stream-runtime 中的类，core 只定义接口。

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

checkpoint 子系统**已通过图模型执行路径（`executeWithGraphModel()`）与执行引擎对接**。对接范围：

1. **StreamExecutionEnvironment.executeWithGraphModel() 集成 CheckpointCoordinator**
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
   - 图模型路径仅支持**单链管线**（所有算子在一个 chain 内）
   - 多链管线（含 keyBy 产生 PartitionTransformation）被明确拒绝
   - 快速路径 `execute()` 不受影响，保持不变

### 10.3 未对接（后续工作）

以下功能仍为独立计划，不阻塞当前闭环：

1. **跨 Task 数据交换**（RecordWriter/RecordReader/InputGate）— 解锁多链管线
2. **多链管线图模型执行** — 依赖跨 Task 数据交换
3. **Savepoint 深度实现**（手动触发、schema 兼容）
4. **增量快照优化**
5. **Unaligned checkpoint 模式**
6. **Key-group 重分布**

## 11. 与 Flink 的差异

| 维度 | Flink | nop-stream |
|---|---|---|
| Barrier 对齐 | aligned / unaligned 两种模式 | 仅 aligned 模式 |
| State Backend | HeapStateBackend / RocksDBStateBackend | 仅 MemoryStateBackend |
| State 分区 | Key-Group + KeyGroupRange 支持重分布 | 无 key-group，直接 Map 存储 |
| Coordinator 通信 | RPC（Akka/Netty），JM ↔ TM | 方法调用，同一进程内 |
| 存储后端 | FileSystem / S3 / State Backend 集成 | LocalFile / JDBC |
| Savepoint | 完整支持（手动触发、恢复、schema 兼容） | 接口存在但未深度实现 |
| 增量快照 | RocksDB 支持增量 | 仅全量快照 |
| 对齐超时 | 可配置切换为 unaligned | 不支持 unaligned |

### 11.1 简化决策的理由

- **单进程执行**：nop-stream 当前是单线程同步执行模型，不需要 RPC 通信
- **无 key-group**：没有分布式重分布需求，状态按 Map 存储即可
- **仅 aligned 模式**：unaligned 模式增加复杂度且主要解决高延迟场景，对 nop-stream 的目标场景（单机、低延迟）价值不大
- **全量快照**：状态数据量小（内存中），全量快照足够快

## 12. 已知限制

1. **未与执行引擎集成** — 所有组件已实现但未接入 execute()（详见 §10.2）
2. **状态后端仅内存** — 故障恢复时如果进程重启，内存状态丢失，依赖持久化的 checkpoint 文件
3. **无增量快照** — 每次全量序列化所有状态，状态量大时开销高
4. **barrier 对齐可能阻塞** — 多输入场景下，如果一个输入的 barrier 延迟，其他输入的数据会被缓冲（当前实现中 BarrierAligner 仅记录 barrier 不缓冲数据，但实际的"对齐后处理"逻辑需要在集成时实现）
5. **无 exactly-once source 支持** — 没有可回放的 Source 实现（如 Kafka consumer），无法实现 source 端的 offset 管理和重放
