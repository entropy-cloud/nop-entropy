# Checkpoint 模块提取可行性分析

## 1. 问题

`nop-stream-checkpoint` 模块目前是空壳，checkpoint 代码分散在 `nop-stream-core` 和 `nop-stream-runtime` 中。本文分析是否应该将 checkpoint 逻辑提取到独立模块。

## 2. 当前 Checkpoint 代码分布

### 2.1 nop-stream-core（25 个 checkpoint 相关文件）

**包路径**: `io.nop.stream.core.checkpoint`

| 类 | 性质 | 被谁引用 |
|---|---|---|
| `CheckpointBarrier` | 值对象（继承 `StreamElement`） | core 算子、execution 层、runtime |
| `CheckpointType` | 枚举 | core 算子、execution 层、runtime |
| `CheckpointConfig` | 配置对象 | `StreamExecutionEnvironment`、runtime |
| `CheckpointPlan` | 不可变数据对象 | runtime `CheckpointPlanBuilder` |
| `CheckpointIDCounter` | 接口 | runtime `CheckpointCoordinator` |
| `CompletedCheckpoint` | 不可变数据对象 | runtime `CheckpointCoordinator`、storage |
| `TaskStateSnapshot` | 数据对象（含 ConcurrentHashMap） | core 算子、runtime |
| `TaskLocation` | 值对象 | core 执行层、runtime |
| `OperatorSnapshotResult` | 数据对象 | core 所有算子 |
| `OperatorStateMapping` | 数据对象 | core `CheckpointBarrierTracker` |
| `StateSnapshotContext` | 上下文对象 | core 所有算子的 `snapshotState()` |
| `FunctionSnapshotContext` | 上下文对象 | `AbstractUdfStreamOperator` |
| `FunctionInitializationContext` | 上下文对象 | `AbstractUdfStreamOperator` |
| `EpochManifest` | 不可变数据对象 | runtime storage |
| `EpochState` | 枚举 | runtime `CheckpointCoordinator` |
| `SavepointMetadata` | 数据对象 | runtime storage |
| `StateSegmentDescriptor` | 数据对象 | runtime `CheckpointSerDe` |
| `ProcessingGuarantee` | 枚举 | core `StreamExecutionEnvironment`、runtime |
| `JobTerminationMode` | 枚举 | core `CheckpointConfig`、runtime |
| `JobTerminationContext` | 上下文对象 | 未被引用（预留） |
| `SourceEnumeratorState` | 数据对象 | runtime `SourceEnumerator` |
| `TaskEpochSnapshot` | 数据对象 | runtime |
| `ICheckpointStorage` | 接口 | runtime storage 实现 |
| `CheckpointStorageException` | 异常类 | runtime storage 实现 |
| `CheckpointParticipant` | 接口 | core `AbstractStreamOperator`、runtime |

**包路径**: `io.nop.stream.core.checkpoint.participant`

| 类 | 性质 |
|---|---|
| `CheckpointParticipant` | 接口（4 个方法） |

**包路径**: `io.nop.stream.core.checkpoint.storage`

| 类 | 性质 |
|---|---|
| `ICheckpointStorage` | 接口 |
| `CheckpointStorageException` | 异常类 |

### 2.2 nop-stream-runtime（8 个 checkpoint 主源码文件）

**包路径**: `io.nop.stream.runtime.checkpoint`

| 类 | 行数 | 依赖 core checkpoint 类数 |
|---|---|---|
| `CheckpointCoordinator` | 583 | 12 |
| `PendingCheckpoint` | 193 | 4 |
| `CheckpointPlanBuilder` | 244 | 5 |
| `BarrierAligner` | 220 | 1 |
| `AlignedBarrier` | 71 | 0 |
| `CheckpointMetrics` | 105 | 0 |
| `CheckpointMetricsSnapshot` | 84 | 0 |

**包路径**: `io.nop.stream.runtime.checkpoint.storage`

| 类 | 行数 | 依赖 core checkpoint 类数 |
|---|---|---|
| `CheckpointSerDe` | 311 | 7 |
| `LocalFileCheckpointStorage` | 555 | 7 + `ICheckpointStorage` |
| `JdbcCheckpointStorage` | 681 | 7 + `ICheckpointStorage` |

### 2.3 nop-stream-core 中使用 checkpoint 类的非 checkpoint 文件

**算子层（11 个文件）**:
- `AbstractStreamOperator` — `CheckpointBarrier`、`OperatorSnapshotResult`、`CheckpointParticipant`、`StateSnapshotContext`、`TaskStateSnapshot`
- `StreamSourceOperator` — `CheckpointBarrier`、`OperatorSnapshotResult`、`StateSnapshotContext`、`TaskLocation`、`TaskStateSnapshot`
- `StreamSinkOperator` — `CheckpointBarrier`、`OperatorSnapshotResult`、`CheckpointParticipant`、`StateSnapshotContext`、`TaskStateSnapshot`
- `StreamReduceOperator` — `OperatorSnapshotResult`、`StateSnapshotContext`
- `WindowAggregationOperator` — `OperatorSnapshotResult`、`StateSnapshotContext`
- `AbstractUdfStreamOperator` — `FunctionInitializationContext`、`FunctionSnapshotContext`、`OperatorSnapshotResult`、`StateSnapshotContext`、`TaskStateSnapshot`
- `StreamOperator` 接口 — `OperatorSnapshotResult`、`TaskStateSnapshot`
- `Output`、`ChainingOutput`、`KeyExtractingOutput`、`Input`、`TimestampedCollector` — `CheckpointBarrier`

**执行层（6 个文件）**:
- `CheckpointBarrierTracker` — `CheckpointBarrier`、`CheckpointType`、`OperatorSnapshotResult`、`OperatorStateMapping`、`TaskLocation`、`TaskStateSnapshot`
- `StreamTaskInvokable` — `CheckpointBarrier`
- `RecordWriter` — `CheckpointBarrier`
- `InputGate` — `CheckpointBarrier`
- `StreamElementCodec` — `CheckpointBarrier`、`CheckpointType`
- `GraphExecutionPlan` — `TaskLocation`

**环境层（2 个文件）**:
- `ICheckpointExecutorFactory` — `CheckpointConfig`
- `StreamExecutionEnvironment` — `CheckpointConfig`、`ProcessingGuarantee`

## 3. 依赖图分析

```
nop-stream-core.checkpoint (25 个类型)
    ↑ ↑ ↑ ↑
    | | | └── runtime.checkpoint.storage (3 个实现)
    | | └──── runtime.checkpoint (5 个协调器)
    | └────── core.execution (6 个文件)
    └──────── core.operators (11 个文件)
```

**关键发现**: core 的算子和执行层**直接依赖** checkpoint 类型。`CheckpointBarrier` 继承自 `StreamElement`（core 的流记录基类），`OperatorSnapshotResult` 是算子 `snapshotState()` 的返回类型，`TaskStateSnapshot` 是算子状态的容器。

## 4. 可行性评估

### 4.1 方案 A：不提取（保持现状）

**优点**:
- 零改动，无风险
- 当前分层已经合理：core 提供类型定义，runtime 提供实现

**缺点**:
- `nop-stream-checkpoint` 空壳模块造成困惑
- core 中 checkpoint 类（25 个）与非 checkpoint 代码混在一起

### 4.2 方案 B：提取 runtime checkpoint 到 nop-stream-checkpoint

将 runtime 中 8 个 checkpoint 实现文件移到 `nop-stream-checkpoint`。

**优点**:
- 清理空壳模块
- checkpoint 协调/存储逻辑独立

**缺点**:
- `nop-stream-checkpoint` 需要依赖 `nop-stream-core`（使用 12+ 个 core checkpoint 类型）
- `nop-stream-runtime` 的其他文件（`JobCoordinator`、`TaskManager`、`GraphModelCheckpointExecutor`）需要改为依赖 `nop-stream-checkpoint`
- 增加一个模块但核心问题（core 中 checkpoint 类型混杂）未解决

**依赖链变化**:
```
当前:  core → runtime（checkpoint 实现在 runtime 中）
方案B: core → checkpoint-module → runtime（runtime 依赖 checkpoint-module）
       或 core ← checkpoint-module, runtime → checkpoint-module
```

### 4.3 方案 C：将 core checkpoint 类型提取到 nop-stream-checkpoint

将 core 中 25 个 checkpoint 类型移到 `nop-stream-checkpoint`。

**优点**:
- checkpoint 类型完全独立

**缺点**:
- **不可行**。core 的 11 个算子文件和 6 个执行层文件直接引用 checkpoint 类型
- `CheckpointBarrier` 继承 `StreamElement`，形成循环依赖（checkpoint → core → checkpoint）
- `OperatorSnapshotResult` 是 `StreamOperator.snapshotState()` 的返回类型，算子接口必须可见
- `TaskStateSnapshot` 是算子状态容器，算子必须可见
- 要实现此方案，需要将 `StreamElement`、`StreamOperator` 接口等核心抽象也移到 checkpoint 模块，这会破坏整个 core 的架构

### 4.4 方案 D：混合提取

将 core checkpoint 中的**纯数据类型**（无 core 依赖的）提取到 `nop-stream-checkpoint`，其余保留在 core。

**可提取的类型**（不依赖 core 其他类）:
- `CheckpointConfig` — 仅依赖标准库
- `ProcessingGuarantee` — 枚举
- `JobTerminationMode` — 枚举
- `EpochState` — 枚举
- `CheckpointType` — 枚举
- `SavepointMetadata` — 数据对象
- `StateSegmentDescriptor` — 数据对象
- `CheckpointStorageException` — 异常类

**不可提取的类型**（依赖 core）:
- `CheckpointBarrier` — 继承 `StreamElement`
- `OperatorSnapshotResult` — 算子快照结果
- `TaskStateSnapshot` — 算子状态容器
- `TaskLocation` — 被执行层引用
- `OperatorStateMapping` — 被执行层引用
- `StateSnapshotContext` — 算子上下文
- `FunctionSnapshotContext` — 算子上下文
- `FunctionInitializationContext` — 算子上下文
- `CompletedCheckpoint` — 引用 `TaskStateSnapshot`
- `CheckpointPlan` — 引用 `TaskLocation`、`OperatorStateMapping`
- `EpochManifest` — 引用 `StreamModelFingerprint`
- `ICheckpointStorage` — 引用 `CompletedCheckpoint`
- `CheckpointParticipant` — 引用 `TaskStateSnapshot`
- `SourceEnumeratorState` — 数据对象（但被 runtime 引用）
- `TaskEpochSnapshot` — 数据对象
- `CheckpointIDCounter` — 接口

**结论**: 只有 8 个完全独立的类型可以提取，且它们主要是枚举和配置类。提取收益极低。

## 5. 根因分析：为什么 checkpoint 没有放到独立模块

### 5.1 架构原因

checkpoint 不是一个独立的"功能"，而是**贯穿整个流处理引擎的横切关注点**：

1. **算子层**: 每个算子都必须实现 `snapshotState()`，返回 `OperatorSnapshotResult`
2. **执行层**: `CheckpointBarrier` 作为 `StreamElement` 的子类在数据流中传播
3. **传输层**: `RecordWriter`、`InputGate`、`StreamElementCodec` 都需要处理 `CheckpointBarrier`
4. **协调层**: `CheckpointCoordinator` 协调整个 checkpoint 生命周期
5. **存储层**: `ICheckpointStorage` 持久化 checkpoint 数据

这与 Flink 的设计一致——Flink 的 checkpoint 代码也是分布在 `flink-streaming-java`（算子+barrier）和 `flink-runtime`（coordinator+storage）中，没有独立的 `flink-checkpoint` 模块。

### 5.2 技术原因

`CheckpointBarrier` 继承 `StreamElement` 是根本原因。如果 checkpoint 是独立模块，`StreamElement` 也要在那个模块中，但 `StreamElement` 是 core 的基础类型（所有算子都依赖它），这会导致循环依赖。

### 5.3 设计原因

当前分层实际上是合理的：

| 层 | 位置 | 职责 |
|---|---|---|
| 类型定义 | `core.checkpoint` | 数据对象、接口、枚举、值类型 |
| 算子集成 | `core.operators` | barrier 传播、snapshot 回调 |
| 执行集成 | `core.execution` | barrier tracker、codec |
| 协调逻辑 | `runtime.checkpoint` | coordinator、pending checkpoint、plan builder |
| 存储实现 | `runtime.checkpoint.storage` | LocalFile、JDBC、SerDe |
| 指标 | `runtime.checkpoint.metrics` | metrics、snapshot |

这是一个**合理的分层**，而不是"应该提取但没提取"。

## 6. 建议

### 6.1 短期（推荐）

**清理空壳模块，不创建新模块**:
- 从 `nop-stream` 的 `pom.xml` 中移除 `nop-stream-checkpoint` 模块声明
- 保留 `core.checkpoint` 包中的类型定义（当前位置正确）
- 保留 `runtime.checkpoint` 包中的实现（当前位置正确）
- 更新 README.md 中的模块表，删除"规划中"的 checkpoint 模块

### 6.2 中期（可选）

如果未来 checkpoint 存储需要支持更多后端（Redis、S3、HDFS），可以考虑：
- 在 `nop-stream-checkpoint` 中只放**存储实现**（`ICheckpointStorage` 的各种实现）
- 这样可以将 JDBC 依赖、H2 测试依赖从 runtime 中解耦
- 但当前只有 2 个实现（LocalFile + JDBC），不值得为此增加模块

### 6.3 不推荐

- ❌ 将 core checkpoint 类型移到独立模块（循环依赖）
- ❌ 将 runtime checkpoint 协调器移到独立模块（收益低、改动大）
- ❌ 将算子的 checkpoint 集成代码移到独立模块（破坏当前分层）

## 7. 结论（初版）

**checkpoint 没有放到独立模块是正确的设计选择，不是遗漏。**

checkpoint 是流处理引擎的横切关注点，与算子、执行层、传输层深度耦合。强行提取会导致循环依赖或增加不必要的间接层。当前的 `core.checkpoint`（类型）+ `runtime.checkpoint`（实现）分层与 Apache Flink 的做法一致，是经过验证的架构模式。

`nop-stream-checkpoint` 空壳模块应该被清理，而不是被填充。

---

## 8. 补充分析：将 runtime checkpoint 实现提取到 nop-stream-checkpoint

用户提出：core 保留类型/接口，具体实现类写到 `nop-stream-checkpoint` 中。

### 8.1 当前 runtime checkpoint 实现清单

| 文件 | 行数 | 非 core 依赖 |
|------|------|-------------|
| `CheckpointCoordinator` | 583 | `StreamModelFingerprint`（core.model）, `CheckpointMetrics`（同包） |
| `PendingCheckpoint` | 193 | 无（纯 core.checkpoint 类型） |
| `CheckpointPlanBuilder` | 244 | **`GraphExecutionPlan`、`Subtask`、`JobVertex`、`OperatorChain`、`StreamComponents`、`AbstractStreamOperator`、`AbstractUdfStreamOperator`、`StreamOperator`、`StreamSourceOperator`、`TwoPhaseCommitSinkFunction`**（共 10 个 core 执行/算子类） |
| `BarrierAligner` | 220 | 无（纯 `CheckpointBarrier`） |
| `AlignedBarrier` | 71 | 无 |
| `CheckpointMetrics` | 105 | 无 |
| `CheckpointMetricsSnapshot` | 84 | 无 |
| `CheckpointSerDe` | 311 | `StreamModelFingerprint`（core.model）, `JsonTool`（nop-core） |
| `LocalFileCheckpointStorage` | 555 | `JsonTool`（nop-core）, `NopException`（nop-api） |
| `JdbcCheckpointStorage` | 681 | `IJdbcTemplate`（nop-dao）, `IDataRow`（nop-dataset）, `SQL`（nop-core）, `NopException`（nop-api） |

### 8.2 提取后的依赖关系

```
nop-stream-core
    ├── checkpoint/ (25 个类型/接口) ← 保留
    ├── operators/ ← 使用 core.checkpoint 类型
    └── execution/ ← 使用 core.checkpoint 类型

nop-stream-checkpoint (新)
    ├── depends on: nop-stream-core
    ├── depends on: nop-core (JsonTool)
    ├── depends on: nop-dao (IJdbcTemplate) ← 仅 JdbcCheckpointStorage
    ├── depends on: nop-dataset (IDataRow) ← 仅 JdbcCheckpointStorage
    ├── checkpoint/CheckpointCoordinator
    ├── checkpoint/PendingCheckpoint
    ├── checkpoint/CheckpointPlanBuilder ← 问题点
    ├── checkpoint/BarrierAligner
    ├── checkpoint/AlignedBarrier
    ├── checkpoint/metrics/CheckpointMetrics
    ├── checkpoint/metrics/CheckpointMetricsSnapshot
    ├── checkpoint/storage/CheckpointSerDe
    ├── checkpoint/storage/LocalFileCheckpointStorage
    └── checkpoint/storage/JdbcCheckpointStorage

nop-stream-runtime
    ├── depends on: nop-stream-core
    ├── depends on: nop-stream-checkpoint ← 新依赖
    ├── coordinator/JobCoordinator → uses CheckpointCoordinator
    ├── execution/GraphModelCheckpointExecutor → uses CheckpointCoordinator, CheckpointPlanBuilder
    └── execution/EmbeddedDistributedExecutor → uses CheckpointCoordinator
```

**依赖方向**: `core ← checkpoint ← runtime`（无循环）

### 8.3 利分析

#### 利

1. **存储实现可独立扩展**: 新增 Redis/S3/HDFS 存储只需在 `nop-stream-checkpoint` 中添加类，不修改 runtime
2. **关注点分离**: coordinator + storage + barrier aligner 作为 checkpoint 的"实现层"独立存在，与 runtime 的 coordinator/taskmanager/transport 逻辑解耦
3. **测试独立性**: checkpoint 模块的测试不依赖 runtime 的分布式执行框架
4. **模块职责清晰**: core=类型定义, checkpoint=实现, runtime=分布式调度

#### 弊

1. **CheckpointPlanBuilder 是阻塞点**: 它依赖 core 的 10 个执行/算子类（`GraphExecutionPlan`、`Subtask`、`JobVertex`、`OperatorChain`、`AbstractStreamOperator` 等），这些是 core 执行层的核心类。如果移到 checkpoint 模块，checkpoint 模块就变成了"core 执行层的消费者"，而不是纯粹的 checkpoint 实现。

2. **JdbcCheckpointStorage 引入 nop-dao 依赖**: checkpoint 模块需要 `nop-dao`（`IJdbcTemplate`）、`nop-dataset`（`IDataRow`）、`nop-core`（`JsonTool`、`SQL`）。这使得 checkpoint 模块不再是轻量级的。

3. **模块数量增加但收益有限**: 从 9 个模块变成 10 个（或保持 9 个但替换空壳），但 checkpoint 实现代码总共只有 ~3000 行，不值得为此增加构建复杂度。

4. **runtime 仍然依赖 checkpoint**: `JobCoordinator`、`GraphModelCheckpointExecutor`、`EmbeddedDistributedExecutor` 都直接使用 `CheckpointCoordinator` 和 `PendingCheckpoint`。提取后 runtime 仍然需要依赖 checkpoint 模块，没有实现真正的解耦。

5. **CheckpointPlanBuilder 的尴尬位置**: 它既需要 core 的执行模型（`GraphExecutionPlan`、`Subtask`）又需要 checkpoint 类型（`CheckpointPlan`、`OperatorStateMapping`）。如果留在 runtime，checkpoint 逻辑就分散在两个模块中；如果移到 checkpoint，checkpoint 模块就依赖 core 的执行层，破坏了分层。

### 8.4 CheckpointPlanBuilder 问题详解

这是整个方案的关键阻塞点。`CheckpointPlanBuilder` 做的事情是：

```
GraphExecutionPlan (core.execution)
    └── 遍历 Subtask → OperatorChain → StreamOperator
        └── 检测 TwoPhaseCommitSinkFunction (core.common.functions.sink)
        └── 检测 StreamSourceOperator (core.operators)
        └── 构建 CheckpointPlan (core.checkpoint)
```

它本质上是 **core 执行模型和 checkpoint 语义之间的桥梁**。要移动它，有三种选择：

| 选择 | 做法 | 问题 |
|------|------|------|
| A. 移到 checkpoint | checkpoint 模块依赖 core.execution | checkpoint 不再是纯 checkpoint 实现 |
| B. 留在 runtime | runtime 保留此文件 | checkpoint 逻辑分散在两个模块 |
| C. 接口化 | 在 core 定义 `ICheckpointPlanBuilder` 接口，checkpoint 模块实现 | 增加间接层，且接口只有一个实现 |

### 8.5 量化评估

| 指标 | 当前 | 提取后 |
|------|------|--------|
| 模块数 | 9 | 10（或 9 替换空壳） |
| runtime checkpoint 代码 | ~3200 行 | 0 |
| checkpoint 模块代码 | 0 | ~3200 行 |
| runtime → checkpoint 依赖 | 无（同模块） | 有 |
| checkpoint → core 依赖 | - | 有（12 个 checkpoint 类型 + 执行/算子类） |
| 新增构建依赖 | 无 | nop-dao, nop-dataset（通过 JdbcCheckpointStorage） |
| 测试可独立运行 | 否（依赖 runtime） | 是 |

### 8.6 结论

**方案可行但不推荐，原因如下**:

1. **CheckpointPlanBuilder 是结构性障碍**: 它横跨 checkpoint 和 execution 两个领域，无论放哪边都会破坏分层纯度。这是 checkpoint 作为横切关注点的本质决定的。

2. **收益与成本不匹配**: 3200 行实现代码的提取，换来的是新增模块构建开销 + nop-dao 依赖引入 + CheckpointPlanBuilder 的位置尴尬。

3. **当前分层已经合理**: `core.checkpoint`（类型）+ `runtime.checkpoint`（实现）的分层与 Flink 一致，且 runtime 中 checkpoint 相关代码与其他 runtime 代码（coordinator、taskmanager、transport）本身就在同一个模块中，符合"runtime 是执行引擎"的定位。

4. **如果未来有强需求再提取**: 当 checkpoint 存储实现超过 5 个（Redis/S3/HDFS/etc.）时，提取的收益会超过成本。当前只有 2 个实现，不值得。

**如果一定要做**，推荐的最小代价方案是：只提取 `storage` 子包（`CheckpointSerDe` + `LocalFileCheckpointStorage` + `JdbcCheckpointStorage`），保留 coordinator 和 plan builder 在 runtime 中。这样 storage 可以独立扩展，而 checkpoint 协调逻辑和执行模型的桥梁留在它该在的地方。
