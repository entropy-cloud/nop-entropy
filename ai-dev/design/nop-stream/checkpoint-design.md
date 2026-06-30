# Checkpoint 与 Exactly-Once 处理设计

> Status: active
> Created: 2026-05-19
> Updated: 2026-05-23
> Parent: `01-architecture-baseline.md` §4（执行模型）、`state-management-design.md`（状态管理）
> See also: `component-roadmap.md` §3 C5（Checkpoint 生产化计划）

## 1. 定位与目标

nop-stream 的 checkpoint 子系统为流处理管线提供**容错和状态一致性**保障。核心目标是实现端到端 exactly-once 语义：即使发生故障，每条记录的效果也**恰好出现一次**。

本文档描述以下内容：

- **Epoch Checkpoint 协议**：以 `checkpointId` 提升为 `epochId` 为中心，绑定 source offset、operator state、sink transaction 到同一个一致切点
- **CheckpointParticipant**：泛化的事务参与接口，统一 source、sink、外部状态 operator 的 checkpoint 生命周期
- **ProcessingGuarantee**：四种处理保证级别及其 barrier 行为差异
- **Source / Sink Exactly-Once 协议**：能力分级、offset cut、transaction identity
- **JobTerminationMode**：四种运维终止语义
- **故障恢复模型**：全局 epoch 恢复、fencing、coordinator HA
- **存储、可观测性与校验**

设计采用 Chandy-Lamport 分布式快照算法的 barrier 对齐模式，以 Nop 平台的模型驱动和可逆计算思想表达分布式流处理的不变量。

## 2. Epoch Checkpoint 协议

### 2.1 Epoch 是一致性的中心

`checkpointId` 在分布式语义中提升为 `epochId`。一个 epoch 绑定以下内容：

| 内容 | 说明 |
|---|---|
| source offset | 每个 source split 在 epoch 切点的读取位置 |
| operator state | 每个 operator/subtask/state shard 的状态快照 |
| timer state | event-time 和 processing-time timer 的待触发集合 |
| watermark state | 输入 watermark 和 idle 状态 |
| sink transaction | 每个 sink subtask 的 pending transaction |
| plan fingerprint | 生成该 epoch 时的 PartitionedPlan 指纹 |
| participant states | 所有 CheckpointParticipant 的快照和 transaction handle |
| fencing token | 允许提交该 epoch 的 coordinator token |

Exactly-once 的含义是：系统恢复到 epoch N 后，对外可见副作用等价于所有 epoch ≤ N 已提交，所有 epoch > N 未提交。

### 2.2 Epoch 生命周期

```
CREATED → INJECTING → ALIGNING → SNAPSHOTTING → PRECOMMITTED → DURABLE → COMMITTED

任意阶段 → ABORTED
```

| 状态 | 含义 |
|---|---|
| `CREATED` | Coordinator 分配 epochId，建立待 ACK 集合 |
| `INJECTING` | source subtask 在读取线程中注入 barrier |
| `ALIGNING` | 多输入 task 等待所有输入 channel barrier 到齐 |
| `SNAPSHOTTING` | task 生成本地 state snapshot |
| `PRECOMMITTED` | sink 已完成 epoch 对应 transaction 的 preCommit |
| `DURABLE` | epoch manifest 和 state segment 已持久化 |
| `COMMITTED` | sink commit 通知已完成或可重试完成 |
| `ABORTED` | epoch 未 durable。若作业继续运行，已 preCommit 的 sink transaction 保留等待后续 durable epoch subsuming commit；若进入全局恢复，回滚最新 durable epoch 之后的 non-durable transaction |

### 2.3 Barrier 注入规则

Barrier 只能从 source subtask 注入，且必须由 source 读取线程注入。

禁止行为：

| 禁止行为 | 原因 |
|---|---|
| 从 scheduler 线程直接调用 operator 注入 barrier | 会与 source collect 并发交错，破坏切点 |
| 对非 source task 主动注入 barrier | 会绕过真实数据流，破坏 Chandy-Lamport 快照语义 |
| barrier 不进入 transport channel | 下游无法按 channel 对齐 |

Source 注入规则：

```
source reader observes pending epoch N
    stop emitting records after current safe point
    snapshot split offset for records before N
    emit CheckpointBarrier(N) to all output channels
    resume emitting records after N
```

Safe point 由 source connector 定义。文件、批量加载、消息队列、CDC 的 safe point 不同，但都必须能给出可恢复 offset。

### 2.4 Barrier 对齐规则

单输入 task 收到 barrier 后立即 snapshot。

多输入 task 的规则（STRICT_EXACTLY_ONCE 模式）：

```
on barrier N from channel C:
    mark C aligned for N
    block records after barrier N from C
    continue processing records from unaligned channels
    when all input channels aligned for N:
        snapshot local state
        emit barrier N to all output channels
        unblock aligned channels
```

AT_LEAST_ONCE 模式的差异：已收到 barrier 的 channel **不阻塞** barrier 后 records，允许继续处理。代价是恢复后可能重复处理。详见 §4。

Aligned checkpoint 是基线能力。Unaligned checkpoint 是性能优化，不是 exactly-once 正确性的前置条件。Aligned 对齐**必须有累计超时上限**（`barrierAlignmentTimeout`，默认 30s，可通过 `CheckpointConfig` 配置），超时后 InputGate 抛出 `ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT` 使 task FAILED → 触发恢复。Coordinator abort（`checkpointTimeout`，默认 10min）作为兜底（见 §8.7 abort 接线）。

### 2.5 Snapshot 内容

每个 task 对 epoch N 上报 `TaskEpochSnapshot`。

| 内容 | 说明 |
|---|---|
| task identity | 稳定 task 身份（jobId / pipelineId / vertexId / subtaskIndex），不含 attemptId |
| operator snapshots | 按 operatorId 分组 |
| keyed state shards | 每个 shard 独立引用（shard 路由规则见 `state-management-design.md` §3） |
| timer state | 事件时间和处理时间 timer |
| watermark state | 输入 channel watermark 和 idle 标记 |
| source split state | source offset 或 split cursor |
| sink transaction state | pending transaction handle |
| participant states | CheckpointParticipant 快照 |
| metrics | snapshot size、duration、alignment duration |

### 2.6 Epoch Manifest

Coordinator 收齐所有 task snapshot 后生成 epoch manifest。manifest 是恢复的唯一入口，必须持久化。

| 字段 | 说明 |
|---|---|
| `epochId` | checkpoint epoch |
| `jobId` / `pipelineId` | 作业身份 |
| `planFingerprint` | PartitionedPlan 指纹（含 StreamComponents fingerprint） |
| `requirements` | 恢复时校验 backend 能力 |
| `taskSnapshots` | task 到 state segment 的映射 |
| `sourceOffsets` | source split offset 汇总 |
| `sourceEnumeratorSnapshots` | source split registry、assignment、finished split、discovery cursor |
| `sinkTransactions` | sink pending transaction 汇总 |
| `participantStates` | operatorId → CheckpointParticipantState |
| `stateFormatVersion` | 状态格式版本 |
| `createdTime` / `durableTime` | 时间戳 |
| `checksum` | manifest 完整性校验 |

Manifest 必须先于 `notifyCheckpointComplete` 持久化完成。Sink commit 只能发生在 manifest durable 之后。

### 2.7 Commit 与 Subsuming

Checkpoint complete 通知遵守 **subsuming contract**：收到 epoch N 完成通知时，sink 可以提交所有 `epoch ≤ N` 且未提交的 pending transaction。

Sink commit 必须幂等。即使 coordinator 在 durable 后、通知过程中失败，恢复后的 coordinator 也可以重新通知 commit，不得产生重复外部副作用。

Epoch log 必须持久化 `DURABLE` 和 `COMMITTED` 的状态变化。`COMMITTED` 是优化状态，不是恢复前提；恢复逻辑必须能够从 `DURABLE` epoch 重试 sink commit，并依赖 sink 幂等 commit 保证不会重复外部副作用。

### 2.8 Checkpoint 并发策略

基线设计只允许一个 checkpoint epoch in-flight。原因：

| 原因 | 说明 |
|---|---|
| 简化 sink pending transaction | 每个 subtask 最多只有一个正在对齐或快照的 epoch |
| 简化 barrier 对齐 | 不需要同时维护多个 epoch 的 channel 阻塞状态 |
| 简化恢复 | 最新 durable epoch 之后的状态全部 abort 或重试 commit |
| 满足首版语义 | exactly-once 正确性优先于 checkpoint 吞吐 |

**当前实现约束**：`CheckpointCoordinator` 强制 `maxConcurrentCheckpoints=1`（配置 >1 会被警告并降级到 1），`CheckpointBarrierTracker` 和 `InputGate` 也只支持单 checkpoint 对齐。配置 >1 不会崩溃，但不会生效。task 层多 checkpoint 支持（CheckpointBarrierTracker/InputGate 重构为多 checkpoint 追踪）作为 Deferred。

### 2.9 Bounded Source 与 Final Epoch

有限输入作业必须以 final epoch 收尾。

| 场景 | 规则 |
|---|---|
| 单 source 完成 | source 发出 final barrier 后再发出 finished 标记 |
| 多 source 部分完成 | 已完成 source 的 input channel 标记为 finished，不再阻塞后续 epoch 对齐 |
| 所有 source 完成 | Coordinator 触发 final epoch，manifest durable 后通知 sink commit 并结束作业 |
| final epoch 失败 | 按普通 epoch failure 恢复 |

Final epoch 的语义是：所有对外可见 sink 副作用都已绑定到某个 durable epoch，作业结束不是绕过 checkpoint 的特殊路径。

### 2.10 CheckpointPlan：执行计划与 Checkpoint 的桥梁

CheckpointPlan 是 checkpoint 子系统对管线拓扑的**只读视图**，从 PartitionedPlan 派生，将 checkpoint 需要的拓扑信息从执行引擎中解耦出来。

```
CheckpointPlan {
    int version = 1;
    String jobId;
    String pipelineId;
    List<TaskLocation> allTasks;                    // ACK 跟踪
    List<TaskLocation> sourceTasks;                 // barrier 注入点
    Set<String> checkpointParticipants;             // participant operatorId 集合
    Map<TaskLocation, List<OperatorStateMapping>> stateMappings;
}

TaskLocation {
    String jobId;
    String pipelineId;
    String vertexId;
    int taskIndex;
}

OperatorStateMapping {
    int operatorIndex;
    String operatorStateKey;       // 如 "operator-0"
    String keyedStateStorageKey;   // 如 "operator-0-keyed"（null 表示无 keyed state）
    boolean isTwoPhaseCommit;
}
```

**设计要点**：

1. `TaskLocation` 包含 `jobId` + `pipelineId`：为分布式执行预留路由信息
2. `sourceTasks` 包含所有并行实例：每个 source 实例独立注入 barrier
3. `checkpointParticipants` 从 `StreamComponents.checkpointParticipants` 获取
4. `OperatorStateMapping` 用 `keyedStateStorageKey` 显式命名 keyed state，解决同一链中多算子 keyed state 碰撞

**CheckpointPlan 与 Savepoint 的交互**：

- CheckpointPlan 随 savepoint 序列化存储（`checkpoint-plan.json`）
- 恢复时检查兼容性：算子数量、`operatorStateKey`、`keyedStateStorageKey` 是否匹配
- 恢复模式：`STRICT`（默认，不匹配则拒绝）和 `LENIENT`（忽略不匹配，记录警告）
- 不支持自动 schema 迁移

## 3. CheckpointParticipant：泛化事务参与

### 3.1 设计动机

`TwoPhaseCommitSinkFunction` 的 2PC lifecycle 是 sink 专用的。将 2PC 泛化为 `CheckpointParticipant`，使所有 transactional operator（source、sink、外部状态 operator）拥有统一的 checkpoint 生命周期。

### 3.2 CheckpointParticipant 接口

```java
interface CheckpointParticipant {
    /** 阶段 1：保存状态到快照。可多次调用直到完成。 */
    void saveState(long checkpointId) throws Exception;

    /** 阶段 2：准备提交（2PC 第一阶段）。Pending transaction handle 必须写入 snapshot。 */
    void prepareCommit(long checkpointId) throws Exception;

    /**
     * 阶段 3：完成提交（2PC 第二阶段）。
     * success=true 时必须 commit。
     * success=false 时不应 abort prepared transaction，而是保留等待后续 durable epoch subsuming commit。
     */
    void finishCommit(long checkpointId, boolean success) throws Exception;

    /** 阶段 4：从 epoch 恢复。Durable transaction 必须 commit 或证明 already committed。 */
    void restoreFromEpoch(long checkpointId) throws Exception;
}
```

### 3.3 注册机制

1. Operator 构造时，如果实现了 `CheckpointParticipant`，将其 `operatorId` 注册到 `StreamComponents.checkpointParticipants`
2. `CheckpointPlan` 从 `PartitionedPlan` 派生时，从 `StreamComponents.checkpointParticipants` 获取所有 participant 的 operatorId
3. `CheckpointCoordinator` 在触发 checkpoint 时，按 `CheckpointPlan` 中的 participant 列表依次调用

恢复时的 participant 发现：

- 从 Epoch Manifest 读取 `participantStates`
- 从 `StreamComponents.checkpointParticipants` 读取当前 participant 列表
- 当前列表是 manifest 的超集（新增 participant）→ 兼容
- 当前列表是 manifest 的子集（删除 participant）→ 需要迁移 action
- participant 类型变化 → 需要迁移 action

### 3.4 调用顺序与失败处理

**触发 checkpoint 时**（按 DAG 拓扑顺序：source → operator → sink）：

```
对每个 participant（拓扑序）:
    saveState(epochId)
    prepareCommit(epochId)

所有 participant 成功后:
    emitBarrier(epochId)
```

**Checkpoint 完成时**（按相反拓扑顺序：sink → operator → source）：

```
对每个 participant（逆拓扑序）:
    finishCommit(epochId, true)
```

**失败处理规则**：

| 阶段 | 失败处理 |
|---|---|
| `saveState()` 失败 | Checkpoint abort，不触发 barrier，不传播到其他 task |
| `prepareCommit()` 失败 | Checkpoint abort，已保存的状态丢弃，不触发 barrier |
| `finishCommit(true)` 失败 | 记录日志，不中止 checkpoint（manifest 已 durable），恢复时重试 |
| `finishCommit(false)` 失败 | 记录日志，不中止 abort 流程，恢复时处理 |
| `restoreFromEpoch()` 失败 | 恢复失败，需要人工干预 |

### 3.5 Lifecycle 完整流程

```
onEpochBarrier(N):
    saveState(N) until complete
    prepareCommit(N) until complete
    emit barrier N to all output channels

onEpochDecision(N, success):
    finishCommit(N, success) until complete

onRestore(epoch N):
    restoreStateSegments(N)
    restoreFromEpoch(N)
```

关键语义：

1. `saveState()` 和 `prepareCommit()` 可以分步执行，避免阻塞所有 task
2. `finishCommit(false)` 不 abort prepared transaction——保留等待后续 durable epoch subsuming commit
3. `restoreFromEpoch()` 必须幂等——coordinator failover 后可能重复调用

### 3.6 TwoPhaseCommitSinkFunction 作为实现

`TwoPhaseCommitSinkFunction` 实现 `CheckpointParticipant`：

| CheckpointParticipant 方法 | TwoPhaseCommitSink 实现 |
|---|---|
| `saveState(N)` | `snapshotState(operatorStateBackend)` |
| `prepareCommit(N)` | `currentTransaction.preCommit()` |
| `finishCommit(N, true)` | `currentTransaction.commit()` |
| `finishCommit(N, false)` | 不 abort，保留 prepared transaction |
| `restoreFromEpoch(N)` | `rollback() + beginTransaction() + restoreState()` |

### 3.7 Source 作为 CheckpointParticipant

消息队列 source 和 CDC source 可以实现 `CheckpointParticipant`，在 sink commit 成功后才 ack offset：

| CheckpointParticipant 方法 | MessageQueueSource 实现 |
|---|---|
| `saveState(N)` | 快照当前 offset |
| `prepareCommit(N)` | 无操作 |
| `finishCommit(N, true)` | `subscription.ack(offset)` |
| `restoreFromEpoch(N)` | 从最新 durable offset 恢复订阅 |

## 4. ProcessingGuarantee

### 4.1 四种保证级别

| 保证 | 语义 | 要求 |
|---|---|---|
| `STRICT_EXACTLY_ONCE` | 恢复后从 durable epoch 重放，不重复外部副作用 | source REPLAYABLE，sink 两阶段提交 |
| `AT_LEAST_ONCE` | 恢复后从 durable epoch 重放，可能重复处理 | source REPLAYABLE，sink 幂等 |
| `EFFECTIVELY_ONCE` | 数据层可按 exactly-once 或 at-least-once 执行，外部效果依赖幂等/upsert/去重键 | sink 至少 IDEMPOTENT |
| `BEST_EFFORT` | 可禁用 checkpoint，不保证状态一致性 | 无要求 |

### 4.2 Barrier 行为差异

| 行为 | STRICT_EXACTLY_ONCE | AT_LEAST_ONCE |
|---|---|---|
| 已收到 barrier 的 channel | 阻塞 barrier 后 records | 继续处理 barrier 后 records |
| Snapshot 时机 | 所有 channel barrier 到齐后 | 所有 channel barrier 到齐后 |
| 恢复后行为 | 从 durable epoch 重放，不重复副作用 | 从 durable epoch 重放，可能重复处理 |
| 对齐延迟 | 高（等待最慢 channel） | 低（不阻塞已收到 barrier 的 channel） |
| 状态大小 | 对齐期间缓冲 barrier 后 records | 不缓冲，直接处理 |

### 4.3 配置映射

| 用户配置 | ProcessingGuarantee | 要求 |
|---|---|---|
| `semanticMode=STRICT_EXACTLY_ONCE` | `STRICT_EXACTLY_ONCE` | source REPLAYABLE，sink 两阶段提交 |
| `semanticMode=EFFECTIVELY_ONCE` | `EFFECTIVELY_ONCE` | sink 至少 IDEMPOTENT |
| `semanticMode=AT_LEAST_ONCE` | `AT_LEAST_ONCE` | source REPLAYABLE |
| `semanticMode=BEST_EFFORT` | `BEST_EFFORT` | 无要求 |

如果 source 不可重放或 sink 不具备严格提交能力，不允许声明 `STRICT_EXACTLY_ONCE`。运行时和指标必须暴露当前语义等级。

## 5. Source Exactly-Once 协议

### 5.1 Source 能力分级

Source 必须声明一致性能力。

| 能力 | 语义 | exactly-once 可用性 |
|---|---|---|
| `REPLAYABLE` | 可从 checkpoint offset 重放 | 可参与 exactly-once |
| `TRANSACTIONAL_READ` | 外部系统支持事务读或一致快照 | 可参与 exactly-once |
| `AT_LEAST_ONCE` | 可恢复但可能重复 | 不能单独提供 exactly-once |
| `BEST_EFFORT` | 无可靠 offset | 禁止声明 exactly-once |

如果作业声明 `semanticMode=STRICT_EXACTLY_ONCE`，所有 source 必须满足 `REPLAYABLE` 或 `TRANSACTIONAL_READ`，否则作业构建失败。

### 5.2 Source Split

分布式 source 由 split 构成。

| 概念 | 说明 |
|---|---|
| source split | 可独立读取和恢复的输入分片 |
| split owner | 当前负责该 split 的 source subtask |
| split cursor | 该 split 的可恢复读取位置 |
| split assignment | split 到 source subtask 的分配模型 |

Split assignment 必须进入 `PartitionedPlan` 或其运行时可持久化扩展中。恢复时 split owner 可以变化，但 split cursor 必须从最新 durable epoch 恢复。

### 5.3 Source Enumerator State

分布式 source 除 reader cursor 外，还必须 checkpoint 全局 split registry 和 assignment state。

| 状态 | 说明 |
|---|---|
| discovered splits | 已发现的 split 集合 |
| unassigned splits | 尚未分配给 reader 的 split |
| assigned splits | 已分配但尚未完成的 split 及 ownerSubtask |
| finished splits | 已完成且不应重复分配的 split |
| pending acknowledgements | 已下发但 reader 尚未确认接管的 split |
| discovery cursor | 文件发现、partition discovery、CDC snapshot 阶段等枚举进度 |

恢复规则：先从 epoch manifest 恢复 enumerator state，再恢复 reader split cursor。ownerSubtask 可以重新计算，但 split 不能因为 owner 改变而重复分配或漏分配。

### 5.4 Source Offset Cut

Source 在 barrier 注入前必须定义 offset cut。

| Source 类型 | Offset Cut |
|---|---|
| 文件/批量加载 | 下一条允许发出的文件路径、行号、页游标或主键游标 |
| 消息队列 | 下一条允许发出的 topic/partition/offset 或 message id |
| CDC | 下一条允许发出的 binlog/LSN/SCN 和表快照阶段 |
| 数据库分页 | 下一条允许发出的 query identity、last key、page token |

统一语义是：**恢复后第一条允许重新发出的记录位置**（exclusive cut）。Cut 之前的记录已纳入 epoch N 的状态，恢复到 epoch N 后不得再次发出；cut 位置及之后的记录可以重新发出。

## 6. Sink Exactly-Once 协议

### 6.1 Sink 能力分级

Sink 必须声明一致性能力。

| 能力 | 语义 | `STRICT_EXACTLY_ONCE` 可用性 |
|---|---|---|
| `TWO_PHASE_COMMIT` | 支持 begin/preCommit/commit/abort/recover | 首选 |
| `STAGED_ATOMIC_COMMIT` | 先写 staging，checkpoint durable 后原子发布 | 可用 |
| `OUTBOX_EPOCH_LOG` | 外部可见性由 epoch log 控制 | 可用 |
| `IDEMPOTENT` | 写入带确定性业务键或去重键 | 仅可声明 `EFFECTIVELY_ONCE` |
| `UPSERT_BY_KEY` | 最终效果由 key 覆盖决定 | 仅可声明 `EFFECTIVELY_ONCE` |
| `AT_LEAST_ONCE` | 可能重复写 | 禁止声明 exactly-once |
| `BEST_EFFORT` | 不保证成功或幂等 | 禁止声明 exactly-once |

`IDEMPOTENT` 和 `UPSERT_BY_KEY` 只有在外部可见性同样由 epoch commit 控制时才能升级为严格 exactly-once；否则必须降级为 `EFFECTIVELY_ONCE`。

### 6.2 Transaction Identity

严格提交型 sink 的 transaction id 必须由稳定身份和 epoch 决定。

推荐格式：`{jobId}:{pipelineId}:{operatorId}:{subtaskIndex}:{epochId}`

不允许 transaction id 包含随机数作为唯一身份。可以附加 attemptId 作为诊断字段，但外部可见事务身份必须以 epoch 为中心，以便恢复后幂等 commit/abort。

### 6.3 Sink Lifecycle

```
begin epoch N transaction
write records before barrier N into transaction N
on barrier N:
    preCommit transaction N
    snapshot transaction handle
    begin epoch N+1 transaction immediately
on notifyCheckpointComplete(N):
    commit all transactions ≤ N
on notifyCheckpointAborted(N):
    if job continues:
        keep precommitted transaction N for later subsuming commit
    if global recovery starts:
        abort non-durable transactions after latest durable epoch
on recovery:
    inspect pending transactions
    commit durable epochs
    abort non-durable epochs
```

Barrier N 之后的数据必须写入 epoch N+1 或更高 epoch 的 transaction，不能继续写入 epoch N。`notifyCheckpointAborted(N)` 不等价于"丢弃 N 之前已经处理的数据"——transaction N 必须作为 precommitted pending transaction 保留，由后续 durable epoch 通过 subsuming commit 提交。

### 6.4 Sink Abort 与 Orphan 清理

| 资源 | 清理规则 |
|---|---|
| non-durable sink transaction | 作业继续运行时保留等待后续 subsuming commit；全局恢复时 abort 最新 durable epoch 之后且未被后续 durable manifest subsume 的 transaction |
| durable but not committed transaction | 不得 abort，恢复后必须重试 commit |
| state segment orphan | manifest 未引用的 segment 可异步清理 |
| source assignment transient state | 未进入 durable manifest 的临时 assignment 可丢弃 |
| commit uncertainty | 依赖 transaction id 幂等查询或重复 commit 解决 |

### 6.5 外部系统约束

| 外部系统 | exactly-once 条件 |
|---|---|
| JDBC | 使用事务表、唯一键、epoch transaction log 或 outbox pattern |
| 消息队列 | 支持事务 producer，或业务幂等 key 并降级为 effectively-once |
| 文件 | 使用临时文件 + atomic rename + manifest commit |
| HTTP/RPC | 需要外部事务或 epoch outbox；只有幂等键时不能声明 strict exactly-once |
| CDC 输出 | 需要目标端事务、staging publish 或 epoch outbox |

## 7. JobTerminationMode

### 7.1 四种模式

| 模式 | 语义 | 适用场景 |
|---|---|---|
| `CANCEL` | 尽快停止，可 abort non-durable work，不保证输出完整 | 强制停止、开发调试 |
| `DRAIN` | Source truncate 成有限 work，terminal epoch durable 后结束 | 优雅关闭、版本升级 |
| `SUSPEND` | 停止新输入，导出可恢复 savepoint | 暂停作业、状态迁移 |
| `EXPORT_SAVEPOINT` | 生成 protected checkpointNamespace，不停止作业 | 定期备份、状态快照 |

### 7.2 各模式流程

**CANCEL**：

```
1. 发送 CANCEL 信号到所有 task
2. Task 停止处理新数据
3. 如果 abortTransactions=true，abort 所有 pending transactions
4. Coordinator 等待 task 停止（或超时）
5. 作业结束，不保证输出完整
```

**DRAIN**：

```
1. 如果 source 实现了 DrainableSource，调用 truncateForDrain()
2. Source 继续处理 primary work，residual work 暂停
3. Coordinator 触发 terminal epoch（TERMINAL_SAVEPOINT）
4. Task 完成 primary work 后，terminal epoch durable
5. 如果 waitForSinkCommit=true，等待所有 sink commit
6. 作业结束
```

**SUSPEND**：

```
1. Coordinator 停止 source 发送新数据
2. Coordinator 触发 savepoint
3. Savepoint durable 后，task 停止
4. Sink 不要求 final commit 到作业完成状态
5. 作业暂停，状态保存在 savepoint 中
```

**EXPORT_SAVEPOINT**：

```
1. Coordinator 触发 savepoint（不停止作业）
2. Savepoint 写入 protected namespace
3. 作业继续运行
```

### 7.3 CheckpointType 扩展

| 类型 | 说明 |
|---|---|
| `CHECKPOINT` | 定时 checkpoint |
| `SAVEPOINT` | 手动 savepoint |
| `TERMINAL_SAVEPOINT` | DRAIN/SUSPEND 模式的 terminal savepoint |
| `EXPORTED_SAVEPOINT` | EXPORT_SAVEPOINT 模式的 savepoint |
| `COMPLETED_POINT_TYPE` | bounded source 的最终 checkpoint |

### 7.4 JobTerminationContext

```
JobTerminationContext {
    JobTerminationMode mode;
    Duration timeout;                    // 默认 10 分钟
    boolean waitForSinkCommit;           // DRAIN 专用，默认 true
    String savepointNamespace;           // SUSPEND/EXPORT_SAVEPOINT 专用
    boolean abortTransactions;           // CANCEL 专用，默认 false
}
```

## 8. 故障恢复模型

### 8.1 基线恢复策略

成熟 exactly-once 的正确性基线采用**全局 epoch 恢复**。

```
detect failure
    fence failed runId/attempts
    stop or isolate all tasks of the pipeline
    load latest durable epoch manifest
    rebuild DeploymentPlan if node assignment changed
    restore source offsets, operator state, timers, sink transactions
    restart tasks with new attemptId and fencingToken
    resume from epoch + 1
```

全局恢复比局部恢复更简单，但语义完整。Region/local failover 是后续优化，不是 exactly-once 的前置条件。

### 8.2 Fencing

分布式 exactly-once 必须防止旧 attempt 继续输出。

| 场景 | Fencing 规则 |
|---|---|
| task restart | 新 attempt 获得新 token，旧 token 输出被拒绝 |
| coordinator failover | 新 coordinator 获得集群 lease，旧 coordinator commit 被拒绝 |
| sink commit | external transaction 带 epoch identity，重复 commit 幂等 |
| transport write | channel 校验 attempt token，旧 attempt channel 关闭 |

### 8.3 Coordinator HA

Coordinator 是逻辑单点，但不能成为 exactly-once 的单点故障。

| 能力 | 说明 |
|---|---|
| durable epoch log | CREATED、DURABLE、COMMITTED 等关键状态必须持久化 |
| cluster lease | 同一 pipeline 同时只能有一个 active coordinator |
| fencing token | coordinator 切换后旧 token 全部失效 |
| idempotent recovery | 新 coordinator 可重复 commit durable epoch，重复 abort non-durable epoch |

Nop 平台可以通过已有集群锁、数据库锁或外部协调服务提供 lease。具体实现是 runtime backend 决策，语义必须一致。

### 8.4 恢复兼容性

恢复时必须检查：

| 检查项 | 失败处理 |
|---|---|
| plan fingerprint | 不兼容则拒绝自动恢复 |
| operatorId 集合 | 缺失状态的 operator 按策略拒绝或使用初始状态 |
| state schema version | 不兼容则要求显式迁移 |
| stateShardCount | 不一致则要求 rescale manifest 或拒绝 |
| sink transaction protocol | 不兼容则拒绝 exactly-once 恢复 |
| StreamComponents fingerprint | 不匹配则拒绝恢复或要求迁移 action |
| checkpointParticipants 列表 | 新增兼容，删除或类型变化需迁移 action |

Savepoint 可以支持显式迁移，但迁移必须通过模型级 action 描述，不能由运行时猜测。

### 8.4.1 Serializer Fingerprint 策略

恢复兼容性检查需要判断持久化的状态是否能被当前版本的代码正确读取。nop-stream 采用**指纹比对 + 快速失败**策略，不实现 Flink 的四态兼容性模型（COMPATIBLE_AS_IS / COMPATIBLE_AFTER_MIGRATION / COMPATIBLE_WITH_RECONFIGURED_SERIALIZER / INCOMPATIBLE），以降低复杂度。

**Fingerprint 结构**：

每个 `StateDescriptor` 在注册时生成一个 `SerializerFingerprint`，随 TaskEpochSnapshot 一起持久化到 Epoch Manifest：

```java
class SerializerFingerprint {
    String serializerClass;     // 状态序列化器类名（如 "io.nop.stream.core.common.typeinfo.SimpleTypeSerializer"）
    int version;                // 序列化器自声明的格式版本（默认 1）
    String configChecksum;      // 配置参数校验和（如 TypeInformation 的 JSON 序列化后 MD5）
}
```

**生成规则**：

| 状态类型 | serializerClass | version | configChecksum |
|---------|----------------|---------|---------------|
| ValueState\<T> | JsonTool 序列化的类名 | 1 | TypeInformation 的 fingerprint |
| ListState\<T> | JsonTool 序列化的类名 | 1 | 元素 TypeInformation 的 fingerprint |
| MapState\<K,V> | JsonTool 序列化的类名 | 1 | key + value TypeInformation 的联合 fingerprint |
| Timer State | 内部 TimerSerializer | 1 | 空 |
| Source Split State | Connector 定义的 SplitSerializer | Connector 自定义 | Connector 自定义 |

**Manifest 中的存储**：

```
EpochManifest {
    ...
    taskSnapshots: Map<TaskLocation, TaskEpochSnapshot>
}

TaskEpochSnapshot {
    ...
    operatorSnapshots: Map<String, OperatorSnapshot>  // operatorId → snapshot
}

OperatorSnapshot {
    ...
    stateFingerprints: Map<String, SerializerFingerprint>  // stateName → fingerprint
}
```

**恢复时检查**：

```
对每个 operator 的每个 state：
    manifestFingerprint = manifest 中的 SerializerFingerprint
    currentFingerprint = 当前 StateDescriptor 生成的 SerializerFingerprint
    
    if manifestFingerprint == currentFingerprint:
        → 兼容，直接恢复
    if manifestFingerprint.version == currentFingerprint.version 
       && manifestFingerprint.configChecksum != currentFingerprint.configChecksum:
        → 不兼容，拒绝恢复（配置变化，如字段类型从 Integer 变为 Long）
    if manifestFingerprint.version < currentFingerprint.version:
        → 要求显式 migration action（提供 StateMigrationFunction）
    if manifestFingerprint.version > currentFingerprint.version:
        → 不兼容，拒绝恢复（代码降级不支持）
```

**与 Flink 的对比**：

| 维度 | Flink | nop-stream |
|------|-------|------------|
| 兼容性检查 | `TypeSerializerSnapshot.resolveSchemaCompatibility()` 返回四态 | `SerializerFingerprint` 比对，仅两态（兼容 / 不兼容） |
| 状态迁移 | 内置全量读-写迁移（读旧写新） | 不内置自动迁移，要求显式 `StateMigrationFunction` |
| 复杂度 | `CompositeTypeSerializerSnapshot` 递归检查嵌套序列化器 | 单层指纹比对，不递归 |
| 适用场景 | 长期运行的生产作业需要零停机升级 | 中小规模，允许停机迁移 |

**StateMigrationFunction**（当需要迁移时）：

```java
interface StateMigrationFunction<Old, New> {
    New migrate(Old oldValue);
    SerializerFingerprint sourceFingerprint();  // 源指纹
    SerializerFingerprint targetFingerprint();  // 目标指纹
}
```

Migration function 通过 `StreamComponents` 注册，恢复时 Coordinator 查找匹配的 migration function 并执行。迁移是全量扫描（读所有旧值、转换、写回），仅在显式声明时触发。

### 8.5 Rescale 与状态重分配

Parallelism 变化必须通过显式 rescale manifest 或 migration action 描述。

| 状态类型 | Rescale 规则 |
|---|---|
| keyed state | `stateShardCount` 不变时，按 `StateShard.ownerSubtask` 重新归属 |
| non-keyed operator state | operator 必须声明 redistribution policy，否则拒绝自动 rescale |
| union/list operator state | 可声明 union redistribution，所有新 subtask 读取同一集合后自行过滤 |
| broadcast state | 所有 subtask 获取完整副本，必须校验版本一致 |
| source split state | 按 split registry 重新分配 owner，split cursor 不随 subtask 下标绑定 |
| sink pending transaction | 不允许跨 subtask 静默迁移；必须先完成、abort，或由 connector 声明显式 takeover 协议 |

`stateShardCount` 默认不可改变。改变 `stateShardCount` 等价于 keyed state 重分片，必须提供显式 migration action 和校验报告。

### 8.6 模型演化边界

| 变化 | 默认策略 |
|---|---|
| 新增无状态 operator | 可兼容 |
| 删除无状态 operator | 可兼容，前提是不改变状态 operator 的输入语义 |
| 新增有状态 operator | 默认使用初始状态，必须显式确认 |
| 删除有状态 operator | 默认拒绝，除非 migration action 丢弃其状态 |
| 修改 operatorId | 默认拒绝，除非提供 old→new 映射 |
| 修改 key selector/hash policy | 默认拒绝 |
| 修改 state schema/codec | 默认拒绝，除非提供 schema migration |
| 修改 stateShardCount | 默认拒绝，除非提供 reshard migration |
| 修改 sink protocol | 默认拒绝 strict exactly-once 恢复 |

### 8.7 Checkpoint 超时 Abort 与 Job 失败

**选了什么**：checkpoint 超时 abort 后，job 明确进入失败态（`executeWithCheckpoint` 抛 `StreamException`），由上层重试（local 路径）或 lease failover → `globalRecovery`（distributed 路径）。

**为什么**：
- checkpoint 超时意味着某些 task 无法在限定时间内完成 snapshot（通常是 stuck channel 导致 barrier 对齐 hang）。继续运行无法自愈，必须触发恢复。
- abort 后不进入不确定状态：所有因 checkpoint 阻塞的 task 线程在 `checkpointTimeout + 限定宽限`内退出，job 明确失败而非永久 hang。
- 与对齐超时（Phase 3，§2.4）形成一致语义：对齐超时是更快的本地检测（`barrierAlignmentTimeout` < `checkpointTimeout`），abort 接线是 `checkpointTimeout` 级别的兜底。

**机制（local 路径）**：
- `CheckpointCoordinator` 提供 `setAbortHandler(Consumer<Long>)`，在 `abortPendingCheckpoint` 中调用。
- `GraphModelCheckpointExecutor.executeWithCheckpoint()` 构建 tasks 后，注册 abort handler：置位 `AtomicBoolean abortMarked` + 遍历 tasks 调 `SubtaskTask.cancel()`（中断阻塞线程）。
- abort 标记插在 `submitAndRun` 返回后、`handleJobTermination` 之前：`if (abortMarked) throw StreamException`，同时实现"抛异常使 job 失败"和"跳过 handleJobTermination 的 final checkpoint"（task 已取消，final barrier 无人处理）。

**为什么不能靠 task FAILED 传播**：
- `SubtaskTask.cancel()` 的状态机先 CAS `RUNNING→CANCELING` 再 `t.interrupt()`，被取消的 task 中断后进入 `CANCELED`（非 `FAILED`），而 `checkTaskFailures` 只检 `FAILED`。
- 故必须用 abort 标记让 `executeWithCheckpoint` 直接判定失败，不依赖 task 终止状态。

**拒绝了什么**：
- (a) 改 `checkTaskFailures` 检查 `CANCELED`——混淆正常 cancel 与 abort cancel。
- (b) 不走 `cancel()` 直接 `interrupt()`——绕过 `SubtaskTask` 状态机，破坏正常取消流程。
- (c) 靠 `CheckpointListener.notifyCheckpointAborted` 抛异常传播——`notifyCheckpointAborted` 的调用方 catch-and-log，异常无法传播。

**distributed 路径**：abort 接线的 distributed 部分（`IStreamTaskRpcService` 新增 `cancelTask` RPC + `JobCoordinator` 注册 abort listener）作为 Deferred（见 §13.2 abort 接线契约的 distributed 部分）。distributed 已有 lease failover 兜底。

## 9. 存储与 Manifest 发布

### 9.1 Atomic Publish

Epoch manifest 的发布必须是原子的。

| 存储 | Atomic Publish 规则 |
|---|---|
| LocalFile | 先写临时文件，fsync 后 rename 到 final manifest |
| JDBC | 在事务中写入 manifest、segments index 和 epoch status |
| Object Storage | 写 segment 后写 manifest，manifest key 作为唯一提交点 |
| Message Log | manifest 作为 compacted key 的最后记录 |

如果 state segment 已写入但 manifest 未发布，该 epoch 不可恢复，后续 cleanup 可删除孤儿 segment。

### 9.2 Checkpoint Retention

Retention 必须以解析后的 `checkpointNamespace` 为范围，不能跨 namespace 计数后删除当前 namespace 的 checkpoint。默认 `checkpointNamespace` 由 `jobId + pipelineId` 决定。

| 策略 | 说明 |
|---|---|
| latest N | 每个 checkpointNamespace 保留最近 N 个 durable epoch |
| savepoint protected | savepoint 不受普通 retention 删除 |
| referenced segments | 被 manifest 引用的 segment 才可保留 |
| orphan cleanup | 未被 durable manifest 引用的 segment 可异步清理 |

### 9.3 ICheckpointStorage 接口

| 方法 | 含义 |
|---|---|
| `storeCheckPoint(completed)` | 存储完成的 checkpoint |
| `getLatestCheckpoint(jobId, pipelineId)` | 获取最近完成的 checkpoint |
| `getAllCheckpoints(jobId)` | 获取所有 checkpoint（按 ID 降序） |
| `getLatestCheckpoints(jobId, count)` | 获取最近 N 个 |
| `deleteCheckpoint(jobId, pipelineId, checkpointId)` | 删除指定 checkpoint |
| `deleteAllCheckpoints(jobId)` | 删除作业的所有 checkpoint |

| 实现 | 适用场景 |
|---|---|
| `LocalFileCheckpointStorage` | JSON 文件，单机开发测试 |
| `JdbcCheckpointStorage` | JDBC 数据库（通过 `IJdbcTemplate` + `IDialect` 多数据库适配），生产环境 |

### 9.4 CheckpointConfig

| 参数 | 默认值 | 含义 |
|---|---|---|
| `checkpointEnabled` | true | 是否启用 checkpoint |
| `checkpointInterval` | 60000ms | 触发间隔 |
| `checkpointTimeout` | 600000ms | 单次 checkpoint 超时 |
| `barrierAlignmentTimeout` | 30000ms | 多输入 barrier 对齐累计超时（超时后 task 主动失败，不等 checkpointTimeout） |
| `minPause` | 500ms | 两次 checkpoint 之间的最小间隔 |
| `maxConcurrentCheckpoints` | 1 | 最大并发 checkpoint 数（**当前实现强制为 1，配置 >1 会被降级**） |
| `maxRetainedCheckpoints` | 5 | 保留的已完成 checkpoint 数 |
| `maxConsecutiveCheckpointFailures` | 3 | 连续 checkpoint 触发失败的告警阈值（超阈值触发 ERROR 日志） |
| `storageType` | "local" | 存储类型（"local" / "jdbc"） |

## 10. 可观测性契约

分布式 exactly-once 必须有可观测指标。

核心指标：

| 指标 | 说明 |
|---|---|
| checkpoint epoch id | 最新触发、durable、committed epoch |
| checkpoint duration | 端到端耗时 |
| alignment duration | barrier 对齐耗时 |
| snapshot size | state segment 总大小 |
| pending epochs | 未完成 epoch 数 |
| source lag | source split lag |
| sink pending transactions | 未提交事务数 |
| recovery count | 作业恢复次数 |
| fenced attempts | 被拒绝的旧 attempt 数 |
| semantic mode | strict-exactly-once / effectively-once / at-least-once / best-effort |

诊断信息必须能从 epochId 追溯到 source offset、operator state segment 和 sink transaction。

## 11. Exactly-Once 作业校验

当作业声明 exactly-once 时，编译阶段必须执行静态校验。

| 校验 | 失败条件 |
|---|---|
| source 能力 | 存在非 replayable/transactional source |
| sink 能力 | strict 模式下存在非严格提交能力 sink；effectively-once 模式下存在非幂等 sink |
| operatorId | 存在不稳定或冲突的 operatorId |
| state descriptor | 状态缺少名称、类型或 schema version |
| partition policy | keyBy 边缺少 hash policy 或 stateShardCount |
| timer support | 使用窗口/CEP 但 timer 不可 checkpoint |
| checkpoint storage | storage 不支持 manifest durable 和 atomic publish |
| plan persistence | PartitionedPlan 或 DeploymentPlan 不可序列化 |
| fencing | distributed backend 不支持 attempt fencing |
| StreamRequirement | 存在 backend 不支持的 requirement |
| checkpointParticipants | participant 列表与 manifest 不兼容 |

校验失败时，作业不能以声明的语义模式启动。允许用户显式选择更低级别的保证，但运行时和指标必须暴露实际语义等级。

## 12. 设计不变量

1. 所有持久状态必须有稳定 `operatorId`
2. 所有 keyed state 必须有确定性 `StateShard` 路由
3. `PartitionedPlan` 是 parallelism、edge partition、state route、checkpoint route 的唯一语义来源
4. Barrier 只能由 source 读取线程注入，并随数据 channel 传播
5. Epoch manifest durable 之前，sink transaction 不得 commit
6. 恢复必须从最新 durable epoch manifest 开始
7. Source 不可重放或 sink 不具备严格提交能力时，不允许声明 `STRICT_EXACTLY_ONCE`
8. 旧 attempt 和旧 coordinator 必须被 fencing
9. Timer state 是窗口和 CEP exactly-once 的必要状态
10. Delta 只能修改模型，不能 patch runtime object 来改变语义
11. 所有 `StreamModel` 必须包含 `StreamComponents` registry
12. 所有 `StreamRequirement` 必须在编译时和运行时校验
13. 所有 transactional operator 必须实现 `CheckpointParticipant`
14. 所有分布式 edge 必须配置 `EdgeConfig`
15. 所有作业终止必须明确 `JobTerminationMode`

> 完整不变量列表以 `00-vision.md` §八为权威来源。

## 13. 容错约束边界

checkpoint 子系统除 §12 的正确性不变量外，还必须满足以下容错健壮性约束。不变量定义正确性硬约束（违反=数据不一致），容错契约定义可用性/健壮性边界（违反=hang/崩溃/静默降级，但 checkpoint 正确性不变量仍兜底，不破坏数据一致性）。**当前实现状态与已知缺口见 `component-roadmap.md` §3 C5 与 §5。**

### 13.1 执行路径与容错分层

nop-stream 有两条执行路径，容错能力分层不同：

| 路径 | 入口 | 数据交换 | Failover |
|---|---|---|---|
| local embedded | `GraphModelCheckpointExecutor` | 本地内存队列（`ResultPartition`） | 无独立 failover（失败即退出，由上层重试） |
| distributed | `JobCoordinator` + `TaskManager` | 远程消息服务（`RemoteResultPartition`） | lease 过期 → `globalRecovery`（新 fencing token + 从 checkpoint 恢复） |

两条路径上 checkpoint 都由 `CheckpointCoordinator` 协调。barrier 在数据流中对齐（`InputGate`），不依赖 network-stack 级拦截。task 取消通过线程中断机制实现（`SubtaskTask.cancel` → `Thread.interrupt`），多输入对齐循环能响应中断退出。

### 13.2 容错契约

| 契约 | 要求 |
|---|---|
| **对齐超时** | multi-input barrier 对齐必须有累计超时上限。stuck channel（不 finish、不 close、不发 barrier）不得导致对齐永久阻塞 |
| **abort 接线** | Coordinator 的 checkpoint abort 必须能终止已阻塞的对齐读，不得依赖外部被动干预。task cancel + 线程中断机制是接线基础，abort 路径必须使用它 |
| **触发线程安全** | checkpoint 触发路径的复合操作（并发数检查 + 计数自增）必须原子，不得有 check-then-act 竞态 |
| **失败可观测** | 连续 checkpoint 失败必须计数，超阈值触发恢复或显式告警，不得静默降级 |
| **abort 传播通道** | abort 信号必须有独立于数据流的控制通道传播到所有 task。不得仅靠数据队列内的 marker——对齐等待时数据队列读不到 marker |
| **多输入对齐统一** | 多输入 barrier 对齐应使用统一、线程安全、带超时的对齐器实现，不得在不同执行路径存在双轨制 |
| **并发能力一致** | 配置的 `maxConcurrentCheckpoints` 必须 Coordinator/task/对齐器各层一致，不得配置允许但实现拒绝 |
| **channel 心跳（distributed）** | 分布式 `RemoteInputChannel` 应有 channel 级心跳/超时检测，不得仅靠粗粒度 lease 兜底 |
| **背压逃生（unaligned）** | 持续背压场景需 barrier 抢占式传播通道（unaligned checkpoint），不得仅靠 aligned 对齐（背压下对齐时延无上限） |

### 13.3 缓解选项

在上述契约完全满足前，对不需要算子状态一致快照的场景，可使用 `EFFECTIVELY_ONCE` 模式（`barrierAlignment=false` + `requiresDurableCheckpoint=true`）绕开对齐——barrier 不阻塞，靠 sink 两阶段提交保证 exactly-once。代价是 sink 必须幂等或两阶段提交。

source 必须声明可重放（§5.1）。若 source 声明可重放但实际 offset 被外部清理导致不可重放，恢复重放时丢数据——此风险由 source 实现负责，不在 checkpoint 契约范围。

### 13.4 不变量与容错契约的关系

- **不变量（§12）**：正确性硬约束。违反 → 数据不一致（不可接受）。
- **容错契约（§13.2）**：健壮性约束。违反 → 可用性损失（hang/崩溃/静默降级），但 fencing、manifest durable 前不 commit、恢复从 durable epoch 等不变量仍兜底，不破坏数据一致性。

容错契约当前部分未满足（实现状态见 `component-roadmap.md`），其未满足不影响 §12 不变量保证的 exactly-once 正确性，只影响故障场景下的可用性与恢复速度。

## 14. 与 SeaTunnel Checkpoint 的对比

SeaTunnel (Zeta Engine) 的 checkpoint 机制与 nop-stream 有共同的血统（都受 Flink 影响），但在实现上有几个关键差异：

### 14.1 Checkpoint 粒度

| 维度 | SeaTunnel | nop-stream |
|------|-----------|------------|
| 范围 | **per Pipeline**（Execution Plan 中按 shuffle 边界切分） | **per Job**（整个 JobGraph 一个 checkpoint） |
| 隔离性 | 不同 Pipeline 的 checkpoint 互不干扰，故障恢复仅影响对应 Pipeline | 整个作业统一 checkpoint |
| 复杂度 | 需要维护多 Pipeline 的 checkpoint 状态机 | 更简单，但故障爆炸半径更大 |

### 14.2 Barrier 结构

SeaTunnel 的 `CheckpointBarrier` 比 nop-stream 携带更多信息：

```java
// SeaTunnel
class CheckpointBarrier implements Barrier {
    long id;
    long timestamp;
    CheckpointType checkpointType;
    Set<Long> prepareCloseTasks;  // 此 checkpoint 后需要关闭的 task
    Set<Long> closedTasks;        // 已经关闭的 task
}

// nop-stream
class CheckpointBarrier {
    long id;
    long timestamp;
}
```

`prepareCloseTasks` / `closedTasks` 机制使 SeaTunnel 可以在 checkpoint 完成后精确控制哪些 task 应该关闭（对 bounded source 的优雅终止很重要）。nop-stream 当前通过 `JobTerminationMode.DRAIN` 处理有界输入终止，不如 SeaTunnel 的粒度精确。

### 14.3 存储模型

| 维度 | SeaTunnel | nop-stream |
|------|-----------|------------|
| 状态序列化 | Java 序列化（`byte[]`） | JSON（`JsonTool`） |
| 状态聚合 | `ActionSubtaskState` = `List<byte[]>` | `TaskEpochSnapshot` = 结构化数据 |
| Coordinator 状态 | Hazelcast IMap（分布式内存，自动恢复） | `EpochManifest` 持久化到 ICheckpointStorage |
| Checkpoint 触发 | 由 `CheckpointCoordinator.scheduleTriggerPendingCheckpoint()` 定时触发 | 同 |
| Pipeline 级隔离 | 每个 Pipeline 独立 checkpoint | 无 Pipeline 概念 |

### 14.4 Task 级恢复

SeaTunnel 的 `SourceSplitEnumeratorTask` 是独立的 coordinator task，在 `JobMaster` 侧运行。这意味著 Split Enumerator 可以独立 checkpoint/恢复。nop-stream 的 Split 管理当前在设计阶段（`connector-design.md` §4），还没有独立 Enumerator Task 的等价物。
