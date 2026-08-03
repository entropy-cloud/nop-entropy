# Checkpoint 与 Exactly-Once 处理设计

> Status: active
> Created: 2026-05-19
> Updated: 2026-07-25（timer state checkpoint/restore 已实现，G2）；2026-08-03（§2.11 unaligned checkpoint 背压逃生，Stage 43）
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
| timer state | event-time 和 processing-time timer 的待触发集合（**已实现**：`WindowOperator` 通过 `HeapInternalTimerService.snapshotTimers()` 持久化；`CepOperator` 通过自有 bypass 机制持久化 `registeredEventTimeTimers`） |
| watermark state | 输入 watermark 和 idle 状态 |
| sink transaction | 每个 sink subtask 的 pending transaction |
| plan fingerprint | 生成该 epoch 时的 PartitionedPlan 指纹 |
| participant states | 所有 CheckpointParticipant 的快照和 transaction handle |
| fencing token | 允许提交该 epoch 的 coordinator token（Stage 39 起为单调 long fencing epoch，见 §2.1.2） |

Exactly-once 的含义是：系统恢复到 epoch N 后，对外可见副作用等价于所有 epoch ≤ N 已提交，所有 epoch > N 未提交。

#### 2.1.2 Fencing token String→long epoch 统一（Stage 39，Decision 1/2/3）

**选了什么**：Stage 39 把原复合 String fencing token（`leaderId@epoch#recoveryGen`）统一为单一单调 `long` epoch，编码方案 `fencing_epoch = leaderEpochValue * EPOCH_SCALE + recoveryGen`（`EPOCH_SCALE = 1_000_000`，见 `JobCoordinator.deriveHaFencingEpoch`）。数据面 `StreamMessageEnvelope` 过滤从「String 等值 + long epochId 等值」双键收敛为**单一 long epoch 比较**。

**为什么（两不变量在单一 long 比较下同时成立）**：

- **stale-leader 拒绝**：leadership 切换使 `leaderEpochValue` cluster-wide 单调递增。新 leader epoch = `newLeaderEpoch * EPOCH_SCALE` 严格大于旧 leader 经任意次 recovery 的最大 epoch（`oldLeaderEpoch * EPOCH_SCALE + (EPOCH_SCALE-1)`），故单一 long 比较即拒绝 stale leader。
- **同 leader 上一轮 recovery 拒绝**：同 leader 内 `globalRecovery()` 仅递增 `recoveryGen`（`< EPOCH_SCALE`），epoch 严格单调递增，故上一轮 recovery 的 stale task 被拒。

**Decision 2（持久化边界）**：`ClusterRegistry` 接口签名为 `long fencingEpoch`；`JdbcClusterRegistry` 在 SQL 边界以 `String.valueOf(long)` 单值写入既有 `fencing_token VARCHAR(255)` 列（**不迁移 DDL**），读回经 `Long.parseLong`。持久化边界 String 是单值，非历史复合串。

**Decision 3（非 HA 模式 epoch 派生）**：非 HA 模式 `leaderEpochValue = 0`，`recoveryGen` 在 `start()` 时 seed 为 1（初始 epoch = 1，区别于 0「未初始化」哨兵），`globalRecovery()` 递增。fencing 有效（零回归）。

**拒绝了什么**：(a) 双 long 复合（`leaderEpoch` + `recoveryGen` 分两字段）——需要数据面维护双键过滤，复杂度高且 Stage 38 已验证单一 String 可承载两不变量，long 化后单一 long 更简单；(b) 迁移 `fencing_token VARCHAR(255)→BIGINT` DDL——影响已部署库，且持久化层仅为记录快照，运行时比较在内存 long 上进行，Option B 边界转换足够。

**验证**：`TestFencingEpochUnification` 显式断言 (a) 数据面 stale long-epoch envelope 被弃、current 被收；(b) leadership 切换推进 epoch 后旧 epoch 控制被拒；(c) 同 leader `globalRecovery()` 推进 epoch 后 prior-recovery task 被拒；(d) 非 HA 模式零回归。

#### 2.1.1 Epoch-centered vs Flink checkpoint-centered — vs Flink 的有意设计差异（D70）

**选了什么**：nop-stream 以 `epochId`（= `checkpointId`）为**一致性的中心对象**，把 source offset、operator state、timer state、watermark state、sink transaction、plan fingerprint、participant states、fencing token 全部绑定到同一个 epoch 切点（见上表）。Recovery 的语义以 epoch 为单位：恢复到 epoch N 等价于"所有 epoch ≤ N 已提交、所有 epoch > N 未提交"。

**与 Flink 的差异**：Flink 以 `CheckpointID` 为编号，但**一致性语义以"job 恢复后从 checkpointed state 重启"为单位**——coordinator 触发 checkpoint → task snapshot → JobManager 持久化 metadata → 恢复时从 metadata 反序列化。Flink 不把 fencing token、plan fingerprint、participant states 显式绑到每个 checkpoint 元数据里（fencing 由 `JobMaster` leader session 管理，不在 per-checkpoint 元数据中；participant states 通过算子级 `OperatorSnapshotFinisher` 隐式完成，没有统一的 per-checkpoint participant manifest）。nop-stream 把它们**全部聚合成 epoch 对象**，使 recovery 决策可以"从单一 epoch 完整重放一致性上下文"。

**为什么如此设计**：

- fencing 与一致性绑定：nop-stream 的 fencing token 在 **coordinator 切换**时刷新（§8.2），每个 epoch 显式记录允许提交它的 fencing token；旧 coordinator 在 durable 之前被 fence，对应的 epoch 永不 commit。Flink 的 leader session 与 checkpoint 元数据解耦，recovery 时需要交叉验证，nop-stream 选择**把 fencing 直接编入 epoch**简化 cross-epoch 不变式。
- plan fingerprint 内嵌：`PartitionedPlan` 指纹随 epoch 持久化（§2.6 manifest），恢复时直接从 epoch 校验 backend 能力，不需要单独的 savepoint 元数据与 plan 元数据交叉。
- participant 显式化：所有 transactional operator 显式注册为 `CheckpointParticipant`（§3），其快照和 transaction handle 进入 epoch manifest；Flink 的 2PC sink 通过 `TwoPhaseCommitSinkFunction` 隐式处理，没有统一的 participant manifest 概念。
- 全局 epoch recovery（§8.1）的简洁性来源：因为一个 epoch 自带全部一致性上下文，"恢复到 epoch N"是一个原子语义；region/local failover 只是优化，不是 exactly-once 前置条件。

### 2.2 Epoch 生命周期

```
CREATED → INJECTING → ALIGNING → SNAPSHOTTING → PRECOMMITTED → DURABLE → COMMITTED

任意阶段 → ABORTED
```

| 状态 | 含义 |
|---|---|
| `CREATED` | Coordinator 分配 epochId，建立待 ACK 集合 |
| `INJECTING` | source subtask 在读取线程中注入 barrier |
| `ALIGNING` | 多输入 task 等待所有输入 channel barrier 到齐。**实现**：`InputGate.handleBarrierNonRecursive()`（`InputGate.java:347`）— 首 barrier 到达调用 `blockConsumption(channelIndex)`（line 220）阻塞该 channel，所有 channel 到齐调用 `resumeConsumptionAll()`（line 245）并输出单一对齐 barrier；累计超 `barrierAlignmentTimeout`（默认 30s）抛 `ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT`（`readMultiChannel():335`）；重叠 barrier 抛 `ERR_STREAM_CHECKPOINT_ABORTED`（line 381）。`barrierAlignment` 标志由 `ProcessingGuarantee.isBarrierAlignment()` 派生（STRICT_EXACTLY_ONCE=true / AT_LEAST_ONCE=false）。注：原 `BarrierAligner`/`AlignedBarrier` 类（runtime/checkpoint/barrier/）已于 Stage 23 代码清理删除（`@Deprecated` reference code，零生产调用者，对齐一律走 `InputGate`） |
| `SNAPSHOTTING` | task 生成本地 state snapshot |
| `PRECOMMITTED` | sink 已完成 epoch 对应 transaction 的 preCommit |
| `DURABLE` | epoch manifest 和 state segment 已持久化 |
| `COMMITTED` | sink commit 通知已完成或可重试完成 |
| `ABORTED` | epoch 未 durable。若作业继续运行，已 preCommit 的 sink transaction 保留等待后续 durable epoch subsuming commit；若进入全局恢复，回滚最新 durable epoch 之后的 non-durable transaction |

> **Async persist（SNAPSHOTTING → DURABLE 间的异步阶段）**：当 `CheckpointConfig.asyncSnapshotEnabled=true`（默认）时，coordinator 的 `completePendingCheckpoint` 在 ACK 到齐后分三段执行。**段 1**（ACK 线程，持有 coordinator monitor）：CAS(RUNNING→COMPLETED)，构建不可变 `CompletedCheckpoint` + `EpochManifest` 快照，提交 persist task 到专用 `checkpoint-persist-<jobId>-<n>` executor，释放 monitor，ACK 线程立即返回。**段 2**（persist executor 线程，不持锁）：`storeCheckPoint` + `storeEpochManifest`（I/O 卸载）。**段 3a/3b**（persist executor 线程，重新获取 monitor）：成功则 `forceComplete`（DURABLE）→ `decrementPendingCheckpointCount` → `notifyParticipantsFinishCommit(true)`（commit，在 DURABLE 之后，§12 不变量 5）；失败则 status=FAILED + `notifyParticipantsFinishCommit(false)`。
>
> **线程上下文变更（observable）**：`forceComplete`/`notifyCheckpointCompleted`/`notifyParticipantsFinishCommit` 在 async 模式下由 `checkpoint-persist-*` 线程执行（原本在 ACK 线程）。消费方语义不变：savepoint `.get()` 仍阻塞至 DURABLE；`CheckpointListener` 回调仍按 §12 不变量 5 顺序触发。`asyncSnapshotEnabled=false` 时保留改造前同步行为（段 1+2+3a 全在 ACK 线程的 synchronized 方法内），用于回退。详细并发模型与不变量见 Plan `2026-07-25-2200-1`。
>
> **Stage 31 增量 timing 变更**：当 `incrementalCheckpointEnabled=true`（要求 `asyncSnapshotEnabled=true`，互斥校验在 `CheckpointCoordinator.validateIncrementalConfig`）时，`EpochManifest` 的构建从段 1 移到段 2——因为 segments 计算涉及 `SharedStateRegistry.register`（内存去重）+ `ISegmentStore.storeSegment`（SST 文件内容寻址拷贝，I/O）+ 非内容寻址文件复制，不能在段 1 的 monitor 下执行。**段 1**（ACK 线程，持 monitor）仅捕获 `currentFingerprint` 到局部变量（保持 fingerprint-observation ordering），CAS COMPLETED，提交 `executeIncrementalPersistAsync`。**段 2**（persist executor）：从 ACK 携带的 `IncrementalSnapshotResult` 提取 SST handles → `registry.register` 去重 → `segmentStore.storeSegment` 物化 → 构建 `EpochManifest.segments` → `storeCheckPoint` + `storeEpochManifest`。**段 3a**（持 monitor）：更新 GC map（`checkpointSegments.put(epochId, segments)`）→ 标准成功副作用 + `cleanupOldCheckpoints`（含 subsumption segment GC）。非增量路径（memory backend 或 `incrementalCheckpointEnabled=false`）保持段 1 构建 manifest 的原 timing（向后兼容）。

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

**实现接线（mailbox 控制面）**：barrier-injector 线程对 source task 的 `CheckpointBarrierTracker.triggerCheckpoint()` 同步 prime ack 计数后，经 `StreamSourceOperator.offerBarrier()` 向 source task 的 `TaskMailbox` 投递 trigger-checkpoint mail（CONTROL 优先级）。source task 线程在 `SourceContext.collect()` 发射点 drain 该 mail，执行 `snapshotState`→`emitBarrier`（在 task 线程，与切点序一致）。详见 `mailbox-design.md` §3.1。

**middle/sink trigger 保持 injector 线程同步**：middle/sink 的 `triggerCheckpoint()` 仅同步 prime ack 计数（不执行 operator 代码、已 `synchronized` 安全），不改 mail。理由：middle/sink 的 barrier 经数据流 in-band 到达，"下游 ack 计数先于 barrier prime"是 checkpoint 不 hang 的跨 task 不变式。详见 `mailbox-design.md` §3.2。

**finished-source 例外**：源完成后 task 线程不存在，`offerBarrier()` 的 `finished` 分支直接在 injector 线程调 `injectBarrier()`（final checkpoint）。无 mailbox 消费者，保留 injector 线程执行。详见 `mailbox-design.md` §3.3。

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

**Aligned→Unaligned 回退（背压逃生，详见 §2.11）**：当 `unalignedCheckpointEnabled=true`（默认）时，对齐等待超过 `unalignedThreshold`（默认 1000ms，必须 < `barrierAlignmentTimeout`）后 checkpoint 切换为 unaligned 模式——捕获在途数据（§2.11.2）、立即完成 barrier、取消对齐超时计时。`unalignedCheckpointEnabled=false` 时保留纯对齐超时→FAILED 行为。

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
| channel state（unaligned） | per-channel 在途记录（`ChannelState`，nullable；仅 unaligned checkpoint 携带，见 §2.11）。aligned checkpoint 缺省为空 |
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
| `segments` | 增量 checkpoint 引用的内容寻址 SST 片段列表（`List<StateSegmentDescriptor>`）。非增量 checkpoint / memory backend 时为空列表；激活于 Stage 31 |

Manifest 必须先于 `notifyCheckpointComplete` 持久化完成。Sink commit 只能发生在 manifest durable 之后。

**`segments` 与 `codec` 值集（Stage 31）**：增量 checkpoint 激活 `EpochManifest.segments`。每个 `StateSegmentDescriptor` 携带 `segmentType` / `path` / `codec` / `checksum` / `schemaVersion`：

- `segmentType`：`rocksdb-sst`（内容寻址的 RocksDB SST 文件）。
- `codec` 取值集为 **`json`**（默认；内容是 JSON 可序列化的状态 blob，restore 端用 `JsonTool` 反序列化）或 **`identity`**（内容是不透明原始字节，即 SST 文件；`path` 即其 SHA-256 content hash，restore 端按 hash 从 `ISegmentStore` 取回原始文件，不做反序列化）。
- **restore 端按 `codec` 分支处理；未知 `codec` 值 fail-fast**（`StateSegmentDescriptor.validateCodec()` 抛 `IllegalStateException`），不静默猜测。
- 对增量 checkpoint，每个 segment 的 `codec=identity`、`path=contentHash`、`checksum=contentHash (SHA-256)`、`schemaVersion=1`、`segmentType=rocksdb-sst`。

`CheckpointSerDe` 的 segments 序列化路径（`segments` 非空时序列化为 `segments` 数组，反序列化回 `List<StateSegmentDescriptor>`）保证增量 manifest 持久化后完整 round-trip。

### 2.7 Commit 与 Subsuming

Checkpoint complete 通知遵守 **subsuming contract**：收到 epoch N 完成通知时，sink 可以提交所有 `epoch ≤ N` 且未提交的 pending transaction。

Sink commit 必须幂等。即使 coordinator 在 durable 后、通知过程中失败，恢复后的 coordinator 也可以重新通知 commit，不得产生重复外部副作用。

Epoch log 必须持久化 `DURABLE` 和 `COMMITTED` 的状态变化。`COMMITTED` 是优化状态，不是恢复前提；恢复逻辑必须能够从 `DURABLE` epoch 重试 sink commit，并依赖 sink 幂等 commit 保证不会重复外部副作用。

### 2.8 Checkpoint 并发策略

Coordinator 层并发模型：`CheckpointCoordinator` 完整尊重 `CheckpointConfig.maxConcurrentCheckpoints` 配置值（不再 clamp 到 1）与 `minPause`（last-completed 节流）。多个 pending checkpoint 可在 coordinator 的 `pendingCheckpoints` map 中安全共存，各自独立 ACK / complete / abort / timeout（经 coordinator 路径，由 `TestCheckpointCoexistenceViaCoordinator` 与 `TestCheckpointMinPauseAndFailureCounter` 覆盖；Anti-Hollow：禁止 `new PendingCheckpoint` + `forceComplete()` 绕过 coordinator 的 hollow 模式）。

**共存 pending 独立流转的正确性来源**（live 设计，非新引入）：`tryTriggerPendingCheckpoint` 为每次触发调用 `getTasksToAcknowledge()` 返回 `new HashSet` 独立快照；每个 `PendingCheckpoint` 持有独立的 `notYetAcknowledgedTasks` / `taskStates` 内部 map；`scheduleTimeout(pending)` 为每个 pending 在共享 `timeoutScheduler` 上独立调度（lambda 捕获特定 pending 引用，abort 仅作用于该 pending）；`acknowledgeTask` / `completePendingCheckpoint` / `abortPendingCheckpoint` 均 `synchronized` 且按 `checkpointId` 索引 `pendingCheckpoints` map（`remove(checkpointId, pending)` 条件删除，不会误删在途的其它 pending）；`cleanupOldCheckpoints` 仅作用于 storage 中已 durable 的 completed checkpoint（不会触碰仍在途的 pending）。Phase 2 在 sync fallback 路径下证明了上述不变量（async persist 路径的 pending 重叠验证属 Stage 18 集成验证项）。

**minPause（last-completed）**：上一个 checkpoint **完成**（`onCompletePersistSuccess` 写入 storage 后）后须经过 ≥ `config.getMinPause()` 才允许触发下一个。首次触发（无前序完成）不受限。minPause == 0 关闭节流。节流仅作用于周期性 `CheckpointType.CHECKPOINT`；savepoint 与 terminal 类型（`SAVEPOINT` / `COMPLETED_POINT_TYPE` / `TERMINAL_SAVEPOINT` / `EXPORTED_SAVEPOINT`）是显式动作，**绕过** minPause，仍受 `maxConcurrentCheckpoints` 串行化约束。minPause 节流命中时返回 `TriggerOutcome.reason == THROTTLED_MIN_PAUSE` 并打 DEBUG 日志（非静默跳过），与 `REJECTED_MAX_CONCURRENT` 在 reason / 日志层面可区分。节流与拒绝都不计入 `consecutiveTriggerFailures`——只有「真失败」（如无 task 可 ACK、触发异常）才计数。

**Task 层多 epoch 约束（Stage 45 已满足）**：`CheckpointBarrierTracker` 与 `InputGate` 现已支持多 in-flight epoch 同时追踪（一次可追踪 ≥ `maxConcurrentCheckpoints` 个 barrier），ACK 路由、对齐、abort 按 epoch 独立。Stage 45 的设计裁定见 §2.8.1。配置 `maxConcurrentCheckpoints > 1` 时，Coordinator 与 task 各层一致：Coordinator 发出 N+1 barrier 时，task 侧按 epoch 独立 ACK/对齐/abort，互不污染。

| 原因 | 说明 |
|---|---|
| 简化 sink pending transaction | 每个 subtask 最多只有一个正在对齐或快照的 epoch |
| 简化 barrier 对齐 | 不需要同时维护多个 epoch 的 channel 阻塞状态 |
| 简化恢复 | 最新 durable epoch 之后的状态全部 abort 或重试 commit |
| 满足首版语义 | exactly-once 正确性优先于 checkpoint 吞吐 |

### 2.8.1 Task 层多 epoch 设计裁定（Stage 45）

Stage 45 把 task 层从「单 in-flight」推进到「多 in-flight」。下列四个设计问题在编码前裁定，记录最终决策与拒绝的替代方案。

#### D1 多 barrier 对齐状态机模型

**决策**：aligned checkpoint 采用「per-channel 有序投递 + 序列化对齐」模型。

- pipelined streaming 中，同一 channel 上的 barrier 严格有序（N 先于 N+1 到达）。Coordinator 可在 N 未完成 ACK 时注入 N+1。
- aligned 模式下 channel blocking 天然序列化对齐：channel C 交付 barrier N 后即阻塞（消费暂停），直到 N 在全 channel 对齐完成（emit + resume）。因此 N+1 的 barrier 无法在被 N 阻塞的 channel 上被读出——aligned barrier 在 InputGate 层逐个对齐，不存在同一时刻两个 barrier 并行对齐。
- 多 in-flight 的并发收益来自「Coordinator 在 N 的 snapshot/ACK 窗口内注入 N+1」（ACK/snapshot 流水线化），而非 barrier 并行对齐。
- InputGate 按 in-flight barrier id 维护对齐状态（小的有序集合），使某个迟到/被 abort 的 barrier N 不污染 N+1 的对齐状态。收到更高 id 的 barrier 时按 channel 有序性处理，不再抛 `ERR_STREAM_CHECKPOINT_ABORTED`。
- channel blocking/resume 按 barrier 边界管理：交付 N 时 block，N 对齐完成时 resume。

**拒绝的替代**：per-channel per-barrier-id 真正并行对齐状态机（允许同一时刻多 barrier 在不同 channel 集合上并行对齐）。拒绝原因：aligned 语义下 channel blocking 已序列化对齐，真正并行对齐不产生收益却显著增加状态机复杂度；真正的并发收益在 unaligned 多 in-flight（见 D4 successor）。

#### D2 ACK 路由 checkpointId 传播

**决策**：在 `OperatorSnapshotResult` 上携带 `checkpointId`（snapshot 时刻由 barrier id / `StateSnapshotContext.getCheckpointId()` 写入），`CheckpointBarrierTracker.acknowledgeOperator` 按 `snapshot.getCheckpointId()` 路由到对应 epoch 的 ACK 条目。**不改变 `acknowledgeOperator(int, OperatorSnapshotResult)` 签名**——42 处 call-site 零签名变更。

- 生产 snapshot 产出点（`AbstractStreamOperator.processBarrier`、`StreamSourceOperator.injectBarrier`、`StreamSinkOperator` snapshot 路径）在 snapshot 时写入 checkpointId。
- 向后兼容：当 result 上 checkpointId 未设置（≤0，遗留测试/直接构造），tracker 回退路由到最近一个 in-flight epoch（保留单 in-flight 行为）。

**拒绝的替代**：给 `acknowledgeOperator` 加 `long checkpointId` 参数（option a）。拒绝原因：会改 `Consumer<OperatorSnapshotResult>` 回调类型与 42 处 call-site 签名，回归面大；checkpointId 已天然存在于 snapshot 产出上下文（barrier / StateSnapshotContext），由 result 携带更内聚。

#### D3 abort 精准化路径

**决策**：option (C) — task 侧 epoch→abort-state 过滤，不改跨模块公共 RPC 契约。

- `CheckpointBarrierTracker` 维护 per-epoch ACK 状态；`notifyCheckpointAborted(N)` **只**移除 epoch N 的追踪条目（非全局 reset），不影响其它在途 epoch。
- local abort handler（`GraphModelCheckpointExecutor.registerLocalAbortHandler`）改为 epoch 感知：对每个 task 调 `tracker.notifyCheckpointAborted(N)` 并释放 N 的对齐；**仅当该 task 无其它在途 epoch 时**才 cancel task 线程；否则只释放该 epoch 的对齐，让其它 epoch 继续 ACK 完成。
- abort N 不误杀在途的 N±1。

**拒绝的替代**：
- option (A) 扩展 `cancelTask` RPC 携带 epoch 参数。拒绝原因：`IStreamTaskRpcService` 是跨模块公共 API（AGENTS.md Protected Area `plan-first`），且 distributed abort 当前驱动 full recovery，RPC 边界 sweep-all 在 recovery 语义下可接受；精准化的核心收益在 task/tracker 层，option (C) 已覆盖。distributed epoch-precise RPC 留作 successor（需独立 plan-first 升级）。
- option (B) 复活 `CancelCheckpointMarker` 作为 in-data-flow 精准 abort 信号。拒绝原因：§13.2.1 裁定为 Decision-only；当前无 in-data-flow cancel marker 消费方，引入空壳违反 plan guide #24。

#### D4 aligned vs unaligned 多 epoch 首版方向

**决策**：首版支持 aligned 多 in-flight；unaligned 保持 single-in-flight 限制（Stage 43 假设保留）。

- aligned 多 in-flight：并发收益来自 Coordinator 在 N 的 snapshot/ACK 窗口注入 N+1（ACK 流水线化），已由 D1/D2/D3 支持。
- unaligned 多 in-flight：释放真正并发，但要求 `ChannelState` 按 epoch 独立追踪（Stage 43 `switchToUnalignedAndEmit` 假设单 in-flight），属更大改动。记录为 successor（邻近 Stage 47 unaligned+rescale）。
- 当 unaligned 模式启用且有第二个 checkpoint 试图对齐时，fail-fast 抛明确异常（不静默跳过）。

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

### 2.11 Unaligned Checkpoint（背压逃生）

Aligned checkpoint（§2.4）是 exactly-once 基线。但当某条 input channel 持续背压时，barrier 无法在对齐窗口内通过，`barrierAlignmentTimeout`（默认 30s）耗尽后 task FAILED → 触发整作业恢复。健康的慢管线（只是慢、不是坏）因此被反复重启。**Unaligned checkpoint 是背压逃生通道**：对齐超时阈值（`unalignedThreshold`）到达后，checkpoint 切换为 unaligned 模式——不再阻塞等待对齐，而是把在途数据（in-flight data）快照进 checkpoint 并立即完成 barrier，使 checkpoint 在背压下也能完成。

本节定义 unaligned 的行为语义（"应该发生什么"），不定义代码层签名（源码是唯一事实）。

#### 2.11.1 不改变 single-in-flight 约束（unaligned 视角）

Unaligned checkpoint **保持** single-in-flight 限制（一次只追踪一个 in-flight unaligned epoch）。它只改变两件事：

1. **barrier 处理模式**：aligned（阻塞等待对齐）→ unaligned（快照在途数据 + 立即完成，不阻塞任何 channel）
2. **snapshot 内容**：在 `TaskEpochSnapshot` 上增加 `ChannelState`（per-channel 在途记录）

aligned 多 in-flight（解开 aligned 路径的 `maxConcurrentCheckpoints=1`）已由 Stage 45 满足（见 §2.8.1 D1/D4）。unaligned 多 in-flight 仍是 successor（Stage 47），要求 `ChannelState` 按 epoch 独立追踪。当 unaligned 模式启用且有第二个 checkpoint 试图对齐时，fail-fast（不静默跳过）。vision §六 决策点 #4（"Checkpoint 协议的变更（如从单 in-flight 扩展为多 in-flight）"）针对的是并发模型变更；aligned 多 in-flight 决策记录于 §2.8.1，unaligned 保持 single-in-flight，其决策记录见 `00-vision.md` §六裁决。

#### 2.11.2 在途数据语义（per-channel）

这是 unaligned 的正确性核心。一个 channel 在 unaligned 切换时刻的状态只有两类：

| channel 状态 | 在途数据 = | 为什么 |
|---|---|---|
| **已交付 barrier**（aligned channel） | barrier **之后**已缓冲的记录 | 这些是新 epoch 的记录，在对齐等待期间先于 barrier 完成而到达；恢复时必须在处理新数据前重放 |
| **未交付 barrier**（non-aligned channel） | 该 channel **全部**当前缓冲记录 | 这些是 pre-barrier 记录，属于上一个 epoch 的尾部，必须保留以保证 exactly-once（恢复后重放，避免丢数据） |

判据：`barrierReceived[channelIndex]`（`InputGate` 已有状态）。**capture 错集合会破坏 exactly-once**：把 aligned channel 的 pre-barrier 记录也 capture 会重复；漏掉 non-aligned channel 的记录会丢数据。

capture 语义是 **drain**（记录从 channel 缓冲移入 `ChannelState`，不是 copy）——因为 barrier 已从这些 channel 读出，缓冲里的记录是"barrier 之后的"，必须从正常数据流移除并以恢复重放的方式重新进入。

#### 2.11.3 触发与捕获路径

- **WHO**：`InputGate`（它持有 `barrierReceived[]` 与 channel 列表）。
- **WHEN**：
  - 对齐模式下，`unalignedThreshold`（默认 1000ms）耗尽且仍未完成对齐 → 切换 unaligned 模式：对所有 **non-aligned** channel 调 `captureInFlightData(barrierReceived=false)`，对已 aligned channel 调 `captureInFlightData(barrierReceived=true)`。
  - 切换后立即取消该 checkpoint 的 `barrierAlignmentTimeout` 计时（不再抛对齐超时）。
- **HOW 到达 snapshot**：channel state 不能走 `triggerCheckpoint()`（它在 checkpoint 发起时运行，早于 barrier 流经数据面）。`InputGate` 在 emit unaligned barrier 时把 `ChannelState` 暂存于自身；task 线程从 `read()` 收到该 barrier 后，从 `InputGate` 取出 `ChannelState`，经 `CheckpointBarrierTracker.setChannelState(...)` 附加到当前 `TaskStateSnapshot`（与 operator state 并列）。channel state 走 **barrier ACK 路径**，不走 trigger 路径。

#### 2.11.4 恢复重放

- **WHERE**：task 生命周期中，operator state restore **之后**、task 开始从 `InputGate` 读取**之前**。新增生命周期步骤 `restoreChannelState(ChannelState)`。
- **WHAT ORDER**：replay 的在途记录**先于**任何新 upstream 记录 / barrier 处理。实现方式：把 `ChannelState` 记录按 channelIndex 预注入对应 `InputChannel` 的缓冲（local channel 注入 `ResultPartition` 队列；`RemoteInputChannel` 注入其 `LinkedBlockingQueue`），再启动订阅/读取。
- **顺序保证**：恢复后的 task 先消费完所有 replay 的在途记录，再处理新数据。pre-barrier 记录（来自 non-aligned channel）与新 epoch 记录（来自 aligned channel 的 post-barrier）都因此被正确重放，state 与 checkpoint 一致。

#### 2.11.5 输出侧安全性（output channel state 不持久化的理由）

`RemoteResultPartition.write()` 立即委托 `IMessageService.send()`，**无内部缓冲**（见源码）。因此 output 侧的在途数据不在 nop-stream 进程内，而存在于传输后端：

- 持久后端（`SysDaoMessageService` / DB / Pulsar）：output 在途数据由后端持久化，task 恢复后订阅同一 topic 继续消费，自然安全。
- 非持久后端：output 在途数据在 producer 崩溃后会丢——这是**已知限制**，不在 Stage 43 解决范围（output channel state 持久化是 follow-up）。本设计仅持久化 **input** channel state。

#### 2.11.6 超时关系

| 配置 | 含义 | 默认 |
|---|---|---|
| `unalignedThreshold` | aligned→unaligned 模式切换阈值（触发条件，不是失败） | 1000ms |
| `barrierAlignmentTimeout` | 绝对对齐失败上限（unaligned 关闭时仍生效） | 30000ms |

不变量：`unalignedCheckpointEnabled=true` 时 **`unalignedThreshold` 必须 < `barrierAlignmentTimeout`**。配置加载时 fail-fast（`CheckpointConfig` 校验）。当 unaligned 模式激活，该 checkpoint 的 `barrierAlignmentTimeout` 计时被取消（切换即完成，不再计时）。`unalignedCheckpointEnabled=false` 时保留原行为（对齐超时 → task FAILED）。

#### 2.11.7 单输入与多输入

- **单输入 task**：无跨 channel 对齐，`ChannelState` 为空（trivially correct）。
- **多输入 task**：`ChannelState` 含 non-aligned channel 的在途记录（§2.11.2）。这是 unaligned 的价值场景。

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

    /** 阶段 4：从 epoch 恢复。Durable transaction 必须 commit 或证明 already committed。
     *  epochId 从 EpochManifest.epochId / CompletedCheckpoint.checkpointId / savepoint.checkpointId
     *  透传（由 GraphModelCheckpointExecutor.restoreOperatorsFromState 注入），使 participant
     *  恢复时能感知真实 durable epoch 而非硬编码值。state 为该 subtask 自身的 TaskStateSnapshot。 */
    void restoreFromEpoch(long epochId, TaskStateSnapshot state) throws Exception;
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

#### 4.1.1 `EFFECTIVELY_ONCE` — vs Flink 的有意设计差异（D69）

**选了什么**：nop-stream 把 `EFFECTIVELY_ONCE` 提升为 **ProcessingGuarantee 一级保证级别**（与 `STRICT_EXACTLY_ONCE`/`AT_LEAST_ONCE`/`BEST_EFFORT` 并列），通过 `barrierAlignment=false` + `requiresDurableCheckpoint=true` 表达"数据层不强对齐、外部效果靠 sink 幂等/upsert/去重键收敛"。

**与 Flink 的差异**：Flink 在 `CheckpointingMode` 之外用 **`ProcessingMode` + side-effect sink 能力分级（`SinkV2`/`GenericWriteAheadSink`/两阶段提交）** 表达同一意图，没有把"数据层不对齐 + 外部效果幂等收敛"列为独立保证级别——它通过 sink 的 exactly-once/at-least-once 修饰符来表达，语义层在 sink 而不在 ProcessingGuarantee。nop-stream 选择把它**显式列为 ProcessingGuarantee**，使运行时指标（§10 semantic mode）能直接暴露当前模式，且配置映射（§4.3 `semanticMode=EFFECTIVELY_ONCE`）在编译期即可校验 sink 至少 IDEMPOTENT。

**为什么如此设计**：

- 运维与可观测：semantic mode 是用户最关心的对外语义契约，把它压到 sink 修饰符会导致"为什么 checkpoint 看起来对齐了但效果仍然 effectively-once"难以解释。
- 容错契约（§13）的逃生通道：当算子状态一致快照的对齐代价不可接受时（背压、对齐超时频繁），`EFFECTIVELY_ONCE` 是合规逃生路径——barrier 不阻塞，靠 sink 两阶段提交保证 exactly-once。这条逃生通道需要是一个**保证级别**才能在 `ProcessingGuarantee.isBarrierAlignment()` 等运行时判定中被消费。
- 与 source 能力（§5.1）解耦：source 只需声明是否 REPLAYABLE，无需关心下游 sink 是否严格 2PC；EFFECTIVELY_ONCE 把"数据层一致性 vs 外部效果一致性"的取舍从 source/sink 组合判定中提出来，作为顶层模式。

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

#### 8.1.1 全局 epoch recovery — vs Flink 的有意设计差异（D70 续）

**选了什么**：nop-stream 采用**全局 epoch recovery**——任意 task 失败/lease 过期 → fence 所有 attempt → 停止整个 pipeline 的所有 task → 从最新 durable epoch manifest 重建 DeploymentPlan、source offset、operator state、timer、sink transaction → 用新 attemptId + fencingToken 重启所有 task → 从 epoch+1 继续。

**与 Flink 的差异**：

| 维度 | Flink | nop-stream |
|---|---|---|
| 恢复粒度 | 默认 `RestartPipelinedRegionFailoverStrategy`（region 级）+ 可选全局恢复 | 全局 epoch recovery（region/local 为后续优化） |
| 状态来源 | per-job `CompletedCheckpointStore` + `ExecutionGraph` 的 `JobManagerTaskRestore` | 单一 durable epoch manifest（含全部一致性上下文，§2.1.1） |
| fencing | `JobMaster` leader session（独立于 checkpoint） | fencing token 编入 epoch（§8.2） |
| coordinator HA | ZooKeeper + Standby JobManager | 持久化 epoch log + cluster lease（§8.3） |

**为什么如此设计**：

- 全局 recovery 语义完整且简单：因为 epoch 自带全部一致性上下文（§2.1.1），"恢复到 epoch N"是单一原子操作，无需 region 边界识别、partial state restore 等复杂语义。
- 单一恢复路径降低正确性风险：region/local failover 的实现复杂度极高（需精确识别哪些 vertex 可独立恢复、哪些 state shard 跨 region 共享、barrier 在 region 边界如何重对齐），首版选择全局 recovery 把正确性风险降到最低。
- nop-stream 的分布式粒度天然较粗：当前 `ClusterRegistry` + lease 是节点级故障检测（§13），不存在 region 级独立故障域；region failover 的收益要在 Stage 44 引入 region 概念后才浮现（Stage 27 已正式裁定 no-go，见 §8.1.2）。
- 拒绝 Flink `ExecutionGraph` 三层调度（D71 一致）：恢复直接基于 epoch manifest + DeploymentPlan，不需要 ExecutionVertex/ExecutionAttempt 三层抽象。

#### 8.1.2 Region failover 可行性裁定（Stage 27 — NO-GO）

**裁定**：Stage 27（targeted failover）经 live 仓库核对正式裁定为 **NO-GO**——在 nop-stream 当前 all-pipelined + by-reference-queue 架构下，region/subtask 级局部恢复**不可行**。

**裁定依据**（详见 `failover-design.md`）：

- `JobGraphGenerator.determinePartitionType()`（`JobGraphGenerator.java:546-554`）从不返回 `BLOCKING`——所有 edge 为 `PIPELINED`/`PIPELINED_BOUNDED`（`pipelined=true`），因此每个 JobGraph = 单 pipelined connected component = **单 region**。vertex 级 targeted = global，零收益。
- 数据交换为 by-reference `LinkedBlockingQueue`（`ResultPartition` ↔ `InputChannel` 直连），scoped 重启存在三个结构死锁（上游 `queue.put()` 永久阻塞 / 下游 channel 不 close 永挂 / 无 mid-execution 重启入口），**drain/reconnect 不可设计**（关键路径）。
- 解除 no-go 需五项架构前置（blocking edge + region 概念 + supervision loop + drain/reconnect + per-region 计数器），全部超出 in-process scope，归属 Stage 44 / vision 决策。

**对 baseline 的影响**：无。`globalRecovery()` 仍是唯一恢复入口，语义完整。targeted failover 从始至终是优化项，不是 exactly-once 正确性前置——本裁定确认该立场成立。G57 / G28（续）/ per-region 计数器保持 deferred → Stage 44。

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

每个 `StateDescriptor` 在注册时由 store **内部自动**生成 `SerializerFingerprint`，随 TaskEpochSnapshot 一起持久化到 Epoch Manifest。算子和用户不接触此过程。

```java
class SerializerFingerprint {
    String stateName;           // 状态名
    int schemaVersion;          // store 内部自动管理（schema 变更时自动递增）
    String schemaChecksum;      // JSON schema 结构 checksum（store 内部自动生成）
}
```

nop-stream **不暴露序列化接口**，`SerializerFingerprint` 不是序列化器指纹，而是 checkpoint manifest 内部记录的 **JSON schema 结构指纹**。它是 storage 实现的内部元数据，算子和用户不直接接触。序列化实现固定为 `JsonTool`（见 `state-management-design.md` §6）。内存 store 不经过任何序列化。具体 schema 描述技术（如平台 `record-object.xdef` 对象描述机制）是 storage 实现的内部决策，不在接口上暴露。

**生成规则**：

| 状态类型 | schemaVersion | schemaChecksum |
|---------|---------------|----------------|
| ValueState\<T> | store 自动管理 | T 的 JSON schema checksum（自动生成） |
| ListState\<T> | store 自动管理 | 元素 T 的 JSON schema checksum（自动生成） |
| MapState\<K,V> | store 自动管理 | K + V 的联合 JSON schema checksum（自动生成） |
| Timer State | store 自动管理 | Timer 结构 checksum（自动生成） |
| Source Split State | store 自动管理 | Connector 结构 checksum（自动生成） |

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

**StateMigrationFunction**（Stage 33 已落地，最终接口契约）：

```java
@Internal
interface StateMigrationFunction<Old, New> {
    New migrate(Old oldValue);
    SerializerFingerprint sourceFingerprint();  // 源指纹（restored）
    SerializerFingerprint targetFingerprint();  // 目标指纹（current descriptor）
}
```

**Stage 33 落地的最终状态**：

- **注册载体**：`StreamComponents`（`io.nop.stream.core.model.StreamComponents` 实现 `StateMigrationRegistry`）。`StreamComponents.registerStateMigrationFunction(stateName, fn)` 按 stateName 注册，同一 state 允许多个迁移函数（不同 source→target pair）。
- **匹配规则**：恢复时 state backend 调用 `StateSchemaResolver.findMigration(registry, stateName, restoredFp, currentFp)`，匹配 = `fn.sourceFingerprint().schemaChecksum == restoredFp.schemaChecksum && fn.targetFingerprint().schemaChecksum == currentFp.schemaChecksum`。匹配命中即执行迁移，未命中返回 `null` 让调用方 fail-fast（**无静默默认**）。
- **执行点**：state backend 的 `getState()`（`MemoryKeyedStateBackend.verifySchemaCompatibility` / `RocksDBKeyedStateBackend.verifySchemaCompatibility`），**不是设计原文描述的 Coordinator**。该偏差与 Stage 29「比对时机下沉到 getState()」一致——Stage 29 把 fail-fast 比对从 storage 层移到 backend `getState()`，Stage 33 在同一执行点先查迁移函数再 fail-fast，使首次 getState() 即触发迁移并更新该 state 持有的 descriptor。
- **全量扫描语义**：命中迁移函数后，遍历该 state 的所有 entry（memory: `Map<TypedNamespaceAndKey, value>`；rocksdb: column-family iterator），读旧值→`migrate`→写回新值；迁移完成后更新该 state 对象的 descriptor 为新 schema（使下次 getState() 校验时 checksum 已匹配，幂等）。
- **迁移时机**：算子 `initializeState` 阶段首次 `getState()` 同步执行（处理任何 element 之前）。**不支持** element 处理中途懒触发 getState() 的迁移——会与 element 处理交错，违反 all-or-nothing 语义。
- **崩溃恢复**：迁移中途崩溃 → checkpoint 不可用，从上一个成功 checkpoint 重跑（迁移全量扫描前不持久化"迁移中"标记；nop-stream 无迁移事务日志）。
- **schemaVersion 四分支**：`schemaVersion` 当前恒为 1（`SerializerFingerprint.DEFAULT_SCHEMA_VERSION`），故 §8.4.1 伪代码中 version<current→migrate / version>current→reject 分支无真实触发条件；version-based branching 作为框架预留，待 schemaVersion 获得递增来源时激活。
- **accumulator 迁移风险**：`verifySchemaCompatibility` 对 Reducing/Aggregating/InternalAppending 等类型的迁移路径同样接线，但存储值是 opaque ACC（`SimpleAccumulator`/用户 ACC 类型），迁移正确性由用户 `StateMigrationFunction` + `AggregateFunction`/`ReduceFunction` 契约决定——错误迁移产出**静默 corrupt**（非 no-op）。本平台不验证 accumulator 迁移语义，仅 surface 该风险。
- **验证 demo**：Integer→Long ValueState 迁移（`TestStateMigration` core 单测 + `TestRocksDBStateMigration` rocksdb 单测 + `TestStateMigrationEndToEnd` 经 `CheckpointSerDe` + `LocalFileCheckpointStorage` 全链路 E2E，memory + rocksdb 两后端各跑一次）覆盖完整链路：`getState(Integer)` 产 checkpoint → 改 `Long` + 注册迁移 → restore → `getState` 返回正确 Long 值；对照测试（未注册迁移）确认 fail-fast（非静默降级）。

**Stage 29 实现分歧**（与上方 pseudo-code 的差异）：

- **checksum 嵌入位置**：伪代码把 fingerprint 描述为存在 `OperatorSnapshot.stateFingerprints` 这种外层 wrapper 中。实际实现把 `schemaChecksum` + `schemaVersion` 直接嵌入 `MemoryStateSerDe` 写出的 per-state JSON info map（与 `stateType` / `valueType` 同层），随 `StateSnapshot.stateData` 透传到 `CheckpointSerDe` → JSON。**不引入** `OperatorSnapshot` wrapper 类，避免对 `TaskStateSnapshot` / `StateSnapshot` / `CheckpointSerDe` 的数据结构改动。
- **比对时机**：伪代码描述比对发生在 storage 层的 manifest 恢复时。实际实现把比对移到 `MemoryKeyedStateBackend.getState()` 时 —— 即算子真正消费恢复出的 state 的入口点。这样 fail-fast 时机更精确（首次 `getState()` 调用），且复用了恢复出的 descriptor（`MemoryStateSerDe.restoreState` 时已从持久化的 type 字符串重建 descriptor 对象）。
- **比对数据源**：不比较"持久化的 checksum 字段"与"当前 descriptor 的 checksum"，而是比较"恢复出的 state 对象上的 descriptor 算出的 checksum"与"当前算子 `getState(descriptor)` 入参 descriptor 算出的 checksum"。两边都从代码侧 type 信息独立计算 checksum，因此旧 checkpoint（无 `schemaChecksum` 字段）也能做检查 —— 持久化的 checksum 字段仅用于人工 inspect 和 Stage 33 的 migration 决策。
- **schemaVersion 恒为 1**：Stage 29 不激活 version-based branching（lower→migrate / higher→reject 的四分支逻辑）。仅当 checksum 不同即 fail-fast。`schemaVersion=1` 作为前向兼容元数据持久化，version-based 分支需要 Stage 33 的 `StateMigrationFunction` 基础设施。
- **checksum 算法**：type-signature 级 SHA-256（`stateType` + class FQN），不采用 deep POJO field-level introspection。具体 canonical 字符串格式见 `StateSchemaResolver.java`。

### 8.5 Rescale 与状态重分配

Parallelism 变化必须通过显式 rescale manifest 或 migration action 描述。

| 状态类型 | Rescale 规则 |
|---|---|
| keyed state | `maxParallelism` 不变、`parallelism` 变化时，按 `KeyGroupRange` 交集局部恢复：新 subtask `i` 只恢复落在 `KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(maxParallelism, parallelism, i)` 区间内的 group 的 key |
| non-keyed operator state | operator 必须声明 redistribution policy，否则拒绝自动 rescale；未声明时 scale-up 新 subtask 从空状态启动 |
| union/list operator state | 可声明 union redistribution，所有新 subtask 读取同一集合后自行过滤 |
| broadcast state | 所有 subtask 获取完整副本，必须校验版本一致 |
| source split state | 按 split registry 重新分配 owner，split cursor 不随 subtask 下标绑定 |
| sink pending transaction | 不允许跨 subtask 静默迁移；必须先完成、abort，或由 connector 声明显式 takeover 协议 |

**选了什么（Stage 35）**：keyed rescale 采用 KeyGroupRange 区间路由，而非全量加载后丢弃。

- **executor dispatch（承重）**：`GraphModelCheckpointExecutor.restoreTaskStatesFromSource` 不再严格 1:1 `TaskLocation` 查找。当检出 `oldParallelism != newParallelism` 且 vertex 持有 keyed state 时，按区间路由：新 subtask `i` 的 `KeyGroupRange` 与旧 plan 各 subtask 的 `KeyGroupRange` 求交，从所有相交旧 `TaskStateSnapshot` 合并 keyed entries 并按新区间过滤。
- **scale-up（4→16）**：新 subtask `i` 从旧 4 subtask 的 keyed snapshot 收集其新区间内的 group。
- **scale-down（16→4）**：新 subtask `i` 从多个旧 snapshot 合并其新区间（跨多个旧 subtask 区间）的 group，非单 snapshot 内过滤。
- **区间归属物化**：`TaskEpochSnapshot` 记录每个 keyed subtask 的 `KeyGroupRange`（`keyGroupRangeStart/End` + `parallelism` + `maxParallelism`），`CheckpointSerDe` 持久化，restore 可消费（缺失时 executor 从 parallelism 计数派生，不静默跳过）。
- **不变量**：key→group 映射仅依赖 `maxParallelism`（job-global 常量），`parallelism` 变化只改 group→subtask 归属，不改 key→group。

**为什么**：局部恢复避免 rescale 时全量状态加载；区间路由使 scale-up/scale-down 对称且可验证（每个新 subtask backend 持有的 key 数 = 其 `KeyGroupRange` 内的 key 数）。

`maxParallelism` 默认不可改变。改变 `maxParallelism` 等价于 keyed state 重分片（key→group 映射变化），必须提供显式 migration action 和校验报告。`parallelism` 变化在 `maxParallelism` 上界内是合法 rescale。

#### 8.5.1 `maxParallelism` Reshard Migration Action（Stage 37 已交付）

改变 `maxParallelism` 的唯一 supported 路径是**离线 reshard migration 工具**——独立工具读旧 savepoint 文件、按新 `maxParallelism` 重映射所有 keyed state 的 key→group、**写出新 savepoint** + 校验报告。**不**采用「restore 路径触发 descriptor 运行时重算」范式（运行时重算不落新文件，与「可复核的离线迁移」目标冲突；`maxParallelism` 变化是低频重操作，离线落盘更安全且可复核）。范式裁定见 `2026-08-02-0955-7-shard-to-keygroup-migration-and-vision-update.md` Phase 2。

**工具入参**：

| 入参 | 含义 |
|------|------|
| 旧 savepoint 路径 | 本地可达的 savepoint 目录或 `.checkpoint` 文件（由 `CheckpointSerDe` 反序列化为 `CompletedCheckpoint`） |
| old `maxParallelism` | 旧 savepoint 写入时的 job-global 上界（用于一致性校验） |
| new `maxParallelism` | 迁移目标上界（`!= old`，否则 fail-fast） |
| newParallelism（可选） | 新并行度，默认 = 旧 savepoint 每 vertex 的并行度（pure reshard：subtask 数不变，仅 key→group→subtask 归属重算） |
| 输出路径 | 新 savepoint 落盘目录 |

**物理重写流程（算法规格）**：

1. 反序列化旧 savepoint → `CompletedCheckpoint`。按 `vertexId` 分组所有 subtask。
2. 对每个 vertex 的每个 keyed state name（跨所有旧 subtask），把全部 entry 汇聚成**全局池**（旧 savepoint 是按 subtask 分片的，reshard 需要全局 key 视图）。
3. 对全局池中**每个 entry**，用**新** `maxParallelism` 经 `KeyGroupAssignment.assignToKeyGroup(key, newMaxParallelism)` 重算该 key 的 group（这是与 §8.5 `parallelism`-only rescale 的本质区别——后者 key→group 不变，只 group→subtask 变；reshard 两者都变）。
4. 按新 group 经 `KeyGroupAssignment.assignKeyGroupToSubtask(groupId, newMaxParallelism, newParallelism)` 把 entry 归入新 subtask；每个新 subtask 的 `KeyGroupRange = computeKeyGroupRangeForSubtaskIndex(newMaxParallelism, newParallelism, i)`。
5. 构建新 `TaskStateSnapshot`（每新 subtask 一个），物化新 `KeyGroupRange` 归属（`TaskEpochSnapshot.setKeyGroupOwnership(newParallelism, newMaxParallelism, range)`，由 `CheckpointSerDe` 持久化）。
6. **operator state（非 keyed）原样搬运**，不受 reshard 影响（operator state rescale 由 §8.5 `SPLIT_DISTRIBUTE/UNION/BROADCAST` 独立策略承载，与本 keyed reshard 正交）。
7. 写出新 `CompletedCheckpoint` → 新 savepoint 目录（原子写 `.tmp` 后 rename，与 `LocalFileCheckpointStorage.storeSavepoint` 一致）。

**校验报告字段**（`ReshardMigrationResult`）：

| 字段 | 含义 |
|------|------|
| old/new `maxParallelism` | 迁移上下界 |
| 每 state key 总数（迁移前/后） | **守恒校验**：per-state 迁移前后 key 总数必须相等（无丢失/重复/静默丢弃），不一致则 fail-fast |
| 每新 subtask 的 key 数分布 | `vertexId + subtaskIndex → keyCount`，可人工核对 |
| keyed/operator state 计数 | 与 `SavepointMetadata` 口径一致 |
| warnings | 例如「无 keyed state 作业（pure operator state）→ no-op 已记录」 |

**失败语义**：

- 迁移中途失败 → 新 savepoint 不完整即丢弃（原子 rename 保证只暴露完整文件），从原 savepoint 重跑（无「迁移中」持久标记，与 schema migration 崩溃语义一致 `checkpoint-design.md:809`）。
- **原 savepoint 只读不被破坏**（迁移工具不写输入路径）。
- 迁移产出的新 savepoint 在 restore 前用 Stage 29 schema fingerprint 一并校验（reshard 不改 value schema，fingerprint 守恒）。

**边界与 fail-fast**：

- 空 keyed state：迁移不报错、不静默丢数据（per-state 计数守恒于 0）。
- 无 keyed state 作业（纯 operator state）：迁移为 no-op 且在校验报告 warnings 中显式记录。
- `old == new maxParallelism`：**fail-fast**（无意义迁移拒绝），不静默写出与输入相同的 savepoint。
- 未知 state 类型 / 无 `entries` 结构 / entry 缺 `key` 字段：**抛异常**（非静默丢弃）。
- savepoint 格式不可识别（反序列化失败）：fail-fast。

**与 schema migration 的边界（orthogonal）**：

reshard migration 与 schema migration（§8.4.1 `StateMigrationFunction`）**正交**，不混用：schema migration 处理「同一 key、value schema 变化」（per-state、在 backend `getState()` 内触发）；reshard migration 处理「同一 key、group 归属变化（`maxParallelism` 变）」（job-global、savepoint 级跨 subtask）。两者**复用的是 read-rewrite 模式，非具体代码**——作用域不同，需独立实现。reshard 工具不触碰 value schema/codec；如同时需 schema 迁移，先 reshard 再在 restore 时由 schema migration 处理。

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
| 修改 maxParallelism | 默认拒绝，除非提供 reshard migration（key→group 映射变化） |
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
- **协作式 cancel（mailbox 控制面）**：abort handler 在 `task.cancel()` 之前经 `invokable.getMailboxExecutor().signalCancel()` 置 cancel flag + 投递 CONTROL marker mail。`Thread.interrupt()` 仍是解除阻塞 `InputGate.read()` / source I/O 的手段（无现成非阻塞 read API）；cancel flag 在 middle/sink 主循环（`processInputGate`）顶部和 source `SourceContext.collect()` 发射点检查，使退出为受控优雅退出。详见 `mailbox-design.md` §3.5。

**为什么不能靠 task FAILED 传播**：
- `SubtaskTask.cancel()` 的状态机先 CAS `RUNNING→CANCELING` 再 `t.interrupt()`，被取消的 task 中断后进入 `CANCELED`（非 `FAILED`），而 `checkTaskFailures` 只检 `FAILED`。
- 故必须用 abort 标记让 `executeWithCheckpoint` 直接判定失败，不依赖 task 终止状态。

**拒绝了什么**：
- (a) 改 `checkTaskFailures` 检查 `CANCELED`——混淆正常 cancel 与 abort cancel。
- (b) 不走 `cancel()` 直接 `interrupt()`——绕过 `SubtaskTask` 状态机，破坏正常取消流程。
- (c) 靠 `CheckpointListener.notifyCheckpointAborted` 抛异常传播——`notifyCheckpointAborted` 的调用方 catch-and-log，异常无法传播。

**distributed 路径（Stage 39 Phase 3 已落地）**：`JobCoordinator.registerDistributedAbortHandler()` 在 `CheckpointCoordinator.setAbortHandler` 上注册一个 handler，checkpoint 超时/abort 时对所有已分配的远程 task 经 `IStreamTaskRpcService.cancelTask` RPC 触发取消（`cancelTask` 已由 Stage 28 加入接口与 `TaskManager` 实现，Phase 3 只接通 coordinator 调用点）。`cancelTask` 是 abort 信号独立于数据流的控制通道（§13.2 line 1116 硬契约），远程 `TaskManager.cancelTask` → `RunningTask.cancel()`（mailbox `signalCancel` + `future.cancel(true)` 中断）解除阻塞的对齐读（§13.2 line 1113）。与 local 路径（`GraphModelCheckpointExecutor.registerLocalAbortHandler`，embedded fast-path）共存：RPC-distributed 形态（`RpcDistributedExecutor`）注册 distributed handler，embedded 形态注册 local handler。RPC 失败经 per-node 日志显式传播（非静默吞），下一轮 `globalRecovery` 的 epoch 轮转仍 fence 未取消的 task。

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

> **异步发布时机**：当 `CheckpointConfig.asyncSnapshotEnabled=true`（默认）时，`storeCheckPoint` + `storeEpochManifest` 在 coordinator 的专用 persist executor 线程执行（段 2，不持 coordinator monitor），ACK 线程提交后即返回。Atomic Publish 规则不变：两个 store 仍在段 2 顺序执行，manifest 写入成功（段 2 完成）后才进入段 3a 置 DURABLE 并 commit（§12 不变量 5）。persist 失败经段 3b 显式置 FAILED + `finishCommit(false)`，不静默降级。

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
| `minPause` | 500ms | 两次 checkpoint 之间的最小间隔（last-completed 语义：上一个 checkpoint 完成后须经过 ≥ minPause 才允许触发下一个；首次触发不受限；仅作用于 `CheckpointType.CHECKPOINT`，savepoint/terminal 绕过；minPause=0 关闭节流） |
| `maxConcurrentCheckpoints` | 1 | 最大并发 checkpoint 数（Coordinator 层完整尊重配置值，不再 clamp 到 1；task 层单 barrier 对齐属 Stage 45） |
| `maxRetainedCheckpoints` | 5 | 保留的已完成 checkpoint 数 |
| `maxConsecutiveCheckpointFailures` | 3 | 连续 checkpoint 触发失败的告警阈值（超阈值触发 ERROR 日志） |
| `asyncSnapshotEnabled` | true | 是否将 coordinator 侧持久化（storeCheckPoint + storeEpochManifest）卸载到专用 persist executor。true 时 ACK 线程提交后即返回，存储 I/O 不阻塞 abort/timeout/trigger bookkeeping；false 保留同步行为（段 1+2+3a 全在 ACK 线程 synchronized 方法内） |
| `asyncSnapshotThreadPoolSize` | 1 | persist executor 线程池大小。Stage 19 解禁并发时需重新评估 |
| `storageType` | "local" | 存储类型（"local" / "jdbc"） |

### 9.5 增量 Checkpoint 与 Segment 共享（Stage 31）

基于 RocksDB native checkpoint（`org.rocksdb.Checkpoint.createCheckpoint`）实现 SST 文件内容寻址与跨 checkpoint 共享，使增量 checkpoint 只物化新增/变更的 SST 文件。**仅 RocksDB backend 支持**；memory backend 无 SST 概念，走全量路径。

**组件契约（接口分离）**：

| 组件 | 职责 | 所在 |
|---|---|---|
| `SharedStateRegistry` | content-hash → 引用计数（in-memory）。`register` 去重返回 canonical handle + 计数+1；`unregister` 计数-1，计数归零时返回 handle 供调用方 discard | `nop-stream-core/checkpoint.incremental` |
| `SharedStateHandle` | 内容寻址 SST 句柄（`contentHash`=SHA-256 = `stateObjectId` / `filePath` / `size`） | `nop-stream-core/checkpoint.incremental` |
| `IncrementalSnapshotResult` | task 侧增量快照结果（SST handles + 非 SST companion 文件路径 + `sst-name-map`）。`@DataBean`，经 ACK 携带到 coordinator | `nop-stream-core/checkpoint.incremental` |
| `ISegmentStore` | content-addressed 文件存储 side-channel（`storeSegment`/`discardSegment`/`segmentExists`/`getSegmentPath`）。**不做引用计数**——由 registry 驱动 `discardSegment` | `nop-stream-core/checkpoint.storage` |
| `LocalFileSegmentStore` | 本地文件实现：`{baseDir}/shared-state/{hash前2字符}/{hash}.sst`，按 hash 前缀分片 | `nop-stream-core/checkpoint.storage` |
| `RocksDBIncrementalSnapshotStrategy` | task 侧：`Checkpoint.create(db)` → 物理快照 → 枚举 `.sst`/`.ldb` 算 SHA-256 → 复制非 SST 文件到 `{baseDir}/cp-{id}/non-sst/`（含 `sst-name-map.txt`） | `nop-stream-rocksdb` |
| `RocksDBIncrementalRestore` | restore 侧：按 `sst-name-map.txt` 将 segment store 的 `{hash}.sst` 重命名回原始 SST 文件名 + 复制非 SST 文件，重组可被 RocksDB 直接打开的目录 | `nop-stream-rocksdb` |

**数据流（单 JVM 嵌入式模型；跨 JVM 传输属 Stage 40）**：

1. Task：`RocksDBKeyedStateBackend.snapshotState()`（`incrementalCheckpointEnabled=true`）→ 策略产出 `IncrementalSnapshotResult`（SST handles 指向 task 本地 native checkpoint 目录），嵌入 `StateSnapshot` 的 `__incremental_checkpoint__` 标记 key。
2. ACK：经现有 ACK 机制将带标记的 `StateSnapshot` 携带到 coordinator。
3. Coordinator（段 2）：从 ACK 提取 handles → `registry.register` 去重 → `segmentStore.storeSegment`（内容寻址，已存在则复用）→ 构建 `EpochManifest.segments`（`segmentType=rocksdb-sst`/`codec=identity`/`path=checksum=contentHash`/`schemaVersion=1`）→ 持久化。
4. **Coordinator 从不直接操作 RocksDB 实例**——只消费 ACK 携带的 raw handles。

**引用计数与 subsumption GC**：`SharedStateRegistry` 是引用计数唯一 source of truth（job 级生命周期，coordinator 持有）。`cleanupOldCheckpoints` subsumption 时，从 GC map（`checkpointId → segments`，段 2 持久化成功后于段 3a under monitor 写入）取旧 checkpoint 的 segments → `registry.unregister` → 零引用 handles off-load 到 persist executor 调用 `segmentStore.discardSegment`（物理删除，不在 monitor 下）。`ISegmentStore` 无独立引用计数，避免双重计数。

**Restart 恢复**：coordinator 启动/恢复时 `restoreSharedStateRegistry` 从 `ICheckpointStorage.loadRetainedEpochManifests` 加载 retained manifests → 逐 segment `registry.register` 重建 ref-count + GC map → 一次性 orphan 扫描（`LocalFileSegmentStore` 的 `shared-state/` 目录，删除 registry 中不存在的文件）。

**配置互斥（fail-fast）**：`incrementalCheckpointEnabled=true` 要求 `segmentStore != null`（否则抛 `UnsupportedOperationException`）且 `asyncSnapshotEnabled=true`（否则抛 `IllegalStateException`）——segments 计算涉及 RocksDB I/O + SHA-256，不能在 sync 路径的 monitor 下执行。校验在 `startCheckpointScheduler` 时执行。

**性能（基准）**：增量 checkpoint 在大状态/小 delta 下显著快于全量扫描。全量路径（Stage 30）逐 key 解码 + 逐值 JSON 反序列化；增量路径为 createCheckpoint 硬链接 + 单次顺序 SHA-256 + 小量非 SST 复制。基准测试（20000 keys × ~250B）实测增量/全量 ≈ 0.35（≈2.9× 加速），满足 ≥2× 目标。

**Out of scope（后续 Stage）**：Key-Group 级 SST range 读取（Stage 35）；跨 JVM SST 文件传输（Stage 40）；JDBC segment 存储（`JdbcSegmentStore` 优化项）。

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
2. 所有 keyed state 必须有确定性 `KeyGroup` 路由（`key → keyGroupId` 仅依赖 job-global `maxParallelism`）
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
| **abort 接线** | Coordinator 的 checkpoint abort 必须能终止已阻塞的对齐读，不得依赖外部被动干预。task cancel + 线程中断机制是接线基础，abort 路径必须使用它。**distributed 部分 Stage 39 Phase 3 已落地**：`JobCoordinator.registerDistributedAbortHandler` → `cancelTask` RPC → 远程 `RunningTask.cancel()` |
| **触发线程安全** | checkpoint 触发路径的复合操作（并发数检查 + 计数自增）必须原子，不得有 check-then-act 竞态 |
| **失败可观测** | 连续 checkpoint 失败必须计数，超阈值触发恢复或显式告警，不得静默降级（minPause 节流 / numPending 拒绝属正常背压，**不**计入 `consecutiveTriggerFailures`；仅「真失败」——无 task 可 ACK / 触发异常——才计数） |
| **abort 传播通道** | abort 信号必须有独立于数据流的控制通道传播到所有 task。不得仅靠数据队列内的 marker——对齐等待时数据队列读不到 marker。**distributed 部分 Stage 39 Phase 3 已落地**：`cancelTask` RPC 是独立控制通道（local 形态用 mailbox + interrupt） |
| **多输入对齐统一** | 多输入 barrier 对齐应使用统一、线程安全、带超时的对齐器实现，不得在不同执行路径存在双轨制 |
| **并发能力一致**（跨层契约，Stage 45 已满足） | 配置的 `maxConcurrentCheckpoints` 必须 Coordinator/task/对齐器各层一致，不得配置允许但实现拒绝。**各层当前状态**：Coordinator 层 ✅ 已满足（Stage 19 完整尊重配置值）；task 层 / 对齐器层 ✅ 已满足（Stage 45：`CheckpointBarrierTracker` per-epoch ACK 追踪 + `InputGate` 多 in-flight barrier 对齐 + epoch 精准 abort）。aligned 多 in-flight 端到端成立；unaligned 保持 single-in-flight（§2.8.1 D4，successor Stage 47） |
| **channel 心跳（distributed）** | 分布式 `RemoteInputChannel` 应有 channel 级心跳/超时检测，不得仅靠粗粒度 lease 兜底。**✅ Stage 43 Phase 1 已落地**：`RemoteResultPartition.sendHeartbeatIfIdle()`/`startHeartbeat(sharedScheduler)`（producer-sends-idle 模型）+ `RemoteInputChannel` `channelTimeoutMs` + `read()` 路径 piggyback 超时检查 → `ERR_STREAM_CHANNEL_TIMEOUT`；fencing 错误 epoch 的 heartbeat 不刷新 liveness |
| **背压逃生（unaligned）** | 持续背压场景需 barrier 抢占式传播通道（unaligned checkpoint），不得仅靠 aligned 对齐（背压下对齐时延无上限）。**✅ Stage 43 已落地**：`CheckpointConfig.unalignedCheckpointEnabled`（默认 true）+ `unalignedThreshold`（默认 1000ms，必须 < `barrierAlignmentTimeout`，`validateUnalignedConfig()` fail-fast）；`InputGate` 在对齐等待超 `unalignedThreshold` 后切换 unaligned 模式（`switchToUnalignedAndEmit`：per-channel `captureInFlightData` 捕获在途数据、emit barrier、resume 所有 channel）；`ChannelState` 走 barrier ACK 路径（`InputGate.consumePendingChannelState` → `CheckpointBarrierTracker.setChannelState` → `TaskEpochSnapshot`）。详见 §2.11 |

### 13.2.1 G5 `CancelCheckpointMarker` / G34 abort 数据 channel 传播 — Stage 39 Phase 3 裁定（Decision-only）

**裁定**：**Decision-only**，不引入 `CancelCheckpointMarker` 类，不为 abort 跨 JVM 数据 channel 传播新增独立机制。

**为什么**：主 abort 机制为控制通道 `cancelTask` RPC（§13.2 line 1133/§8.7 distributed 已落地）。`cancelTask` 已满足「abort 信号独立于数据流的控制通道」硬契约。`CancelCheckpointMarker` 的潜在价值是「已恢复 channel 的补充通知」（in-data-flow marker），但其价值依赖 future stage（如 Stage 43 unaligned / Stage 45 多并发）是否需要 in-data-flow marker。当前**无消费方**，引入空壳类违反 plan guide #24（禁止空壳实现）。

**Successor**：若 Stage 43（unaligned checkpoint）/ Stage 45（多并发 checkpoint）出现真实 in-data-flow cancel marker 消费方，则在该 stage plan 重新裁定并实现 `CancelCheckpointMarker` 事件类型。Stage 45 已裁定（§2.8.1 D3）：采用 option (C) task 侧 epoch→abort-state 过滤，**不**引入 `CancelCheckpointMarker`（仍无 in-data-flow cancel marker 消费方）。abort 经 `cancelTask` RPC 控制通道 + local mailbox `signalCancel` + task 侧 per-epoch `notifyCheckpointAborted` 精准清理。distributed 路径的 epoch 精准 RPC（option A）留作 successor（需独立 plan-first 升级 Protected Area）。

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
