# nop-stream 分布式 Exactly-Once 设计修订说明

> Status: active amendment
> Created: 2026-05-22
> Scope: 对 `nop-stream` 分布式执行与端到端 exactly-once 语义的补充修订

## 1. 定位

本文是 `ai-dev/design/nop-stream/` 现有设计的增量修订说明。它不修改既有设计文件，但在分布式执行、状态路由、checkpoint、source/sink 一致性、故障恢复等主题上给出更强的目标约束。

本文的核心结论是：`nop-stream` 的成熟目标不是复制 Flink 的实现结构，而是以 Nop 平台的模型驱动和可逆计算思想表达分布式流处理的不变量。

### 1.1 设计目标

`nop-stream` 的目标状态是：

> 基于声明式执行模型的分布式流处理内核，提供确定性分区、可恢复状态、epoch checkpoint、source/sink 协议化 exactly-once，以及可由 Java API、XDSL 和 Delta 共同驱动的执行计划。

必须达成的能力：

| 能力 | 设计要求 |
|---|---|
| 分布式执行 | 一个作业可拆分为多个 task fragment，部署到多个 runtime node 上执行 |
| 端到端 exactly-once | source offset、operator state、sink transaction 必须绑定到同一个 checkpoint epoch |
| 确定性分区 | key、state shard、subtask、edge channel 的映射必须由模型决定，不能由运行时对象顺序推导 |
| 稳定状态路由 | 恢复时必须按 `operatorId + subtaskIndex + stateShard + stateName` 路由状态 |
| 可声明和可定制 | Java API 和 XDSL 都必须落到同一套 canonical StreamModel，Delta 只作用于模型层 |
| 可替换后端 | 本地线程、远程进程、未来外部引擎适配都必须遵守同一语义契约 |

### 1.2 不是 Flink Clone

本文拒绝把“分布式 + exactly-once”简单等同于完整复制 Flink 的 `JobManager / TaskManager / ExecutionGraph / Slot / Netty` 体系。

选择 Nop 风格设计的原因：

| 维度 | Nop 设计取向 |
|---|---|
| 架构核心 | 以模型不变量为中心，而不是以运行时类层次为中心 |
| 执行计划 | 用可序列化、可对比、可 Delta 定制的 plan 表达分布式语义 |
| 状态恢复 | 用稳定 ID 和 epoch manifest 路由，不依赖对象实例和链内下标 |
| 扩展方式 | 通过 XDSL、Delta、后端 SPI 扩展，而不是复制某个引擎的内部调度结构 |
| Flink 关系 | 学习成熟语义边界，保持 API 概念兼容，避免实现结构耦合 |

拒绝的方案：

| 方案 | 拒绝原因 |
|---|---|
| 完整复制 Flink Runtime | 会引入超出 Nop 目标的复杂调度、slot、网络栈和二进制序列化体系 |
| 继续只保留单 JVM TaskExecutor | 不能满足分布式执行、故障隔离和跨进程恢复 |
| 只在现有 GraphExecutionPlan 上打补丁 | parallelism、partitioner、checkpoint、state route 仍会散落在运行时代码中 |
| 只声明 exactly-once 而不约束 source/sink | 没有 replayable source 和严格提交能力的 sink 时，端到端 exactly-once 不成立 |

## 2. 总体架构修订

### 2.1 模型优先的执行管线

成熟的 `nop-stream` 执行管线分为五层：

```text
StreamModel
    -> StreamGraph
    -> JobGraph
    -> PartitionedPlan
    -> DeploymentPlan
    -> RuntimeTopology
```

各层职责如下：

| 层 | 职责 | 是否持久化 |
|---|---|---|
| `StreamModel` | 用户意图的规范模型，来自 Java API、XDSL 或 Delta 合成 | 是 |
| `StreamGraph` | 逻辑 DAG，表达 source、operator、sink 和边语义 | 可持久化 |
| `JobGraph` | 算子链化和逻辑优化后的作业图 | 可持久化 |
| `PartitionedPlan` | 并行展开、state shard、subtask、edge channel、checkpoint route 的语义计划 | 必须持久化 |
| `DeploymentPlan` | 将 partitioned task 映射到 runtime node、transport、资源组 | 必须持久化 |
| `RuntimeTopology` | 运行时实例视图，包含 attempt、心跳、通道、状态句柄 | 可重建 |

关键决策：`PartitionedPlan` 是分布式 exactly-once 的中心模型。它承载并行度、分区、状态路由和 checkpoint ACK 集合。运行时只能执行它，不能重新发明拓扑语义。

### 2.2 为什么引入 PartitionedPlan

现有两层图模型可以表达逻辑拓扑和算子链，但不足以表达分布式 exactly-once 所需的稳定身份和路由关系。

`PartitionedPlan` 必须显式记录：

| 信息 | 原因 |
|---|---|
| 每个 vertex 的 parallelism | 运行时必须创建所有 subtask，而不是只创建 taskIndex=0 |
| 每个 operator 的稳定 operatorId | 状态恢复不能依赖链内 index 或对象顺序 |
| 每个 key 分配到哪个 state shard | keyed state 必须可以跨节点定位和恢复 |
| 每个 state shard 由哪个 subtask 拥有 | failure/restart/rescale 需要确定状态归属 |
| 每条边的 partition policy | keyBy、forward、broadcast、rebalance 必须进入执行计划 |
| 每个 subtask 的输入/输出 channel | barrier 对齐和数据传输需要通道级身份 |
| checkpoint ACK 集合 | Coordinator 必须知道哪些 task 必须 ACK |

拒绝让 `GraphExecutionPlan` 临时推导这些信息，因为这样会把分布式语义绑定到本地运行时实现，无法支持跨进程部署和稳定恢复。

### 2.3 DeploymentPlan 的职责边界

`PartitionedPlan` 只回答“应该有多少 task、状态和边如何分区”。`DeploymentPlan` 回答“这些 task 部署到哪里”。

`DeploymentPlan` 记录：

| 信息 | 说明 |
|---|---|
| runtime node | task 所在节点或进程 |
| resource group | 资源隔离和调度分组 |
| transport backend | local queue、remote RPC、message bus 等传输实现 |
| state backend binding | task 使用的状态后端实例或命名空间 |
| checkpoint storage | epoch manifest 和 state segment 的持久化位置 |
| fencing token | 防止旧 attempt 写入新 epoch |

设计决策：本地线程执行只是 `DeploymentPlan` 的一种 backend。分布式语义不能依赖本地线程模型。

### 2.4 RuntimeTopology 的职责边界

`RuntimeTopology` 是运行时状态，不是语义来源。它记录当前 attempt、心跳、通道状态、checkpoint 进度、失败原因等动态信息。

不允许从 `RuntimeTopology` 反向生成状态路径、operatorId 或分区规则。恢复后可以重新构建 `RuntimeTopology`，但不能改变 `PartitionedPlan` 的语义。

### 2.5 分布式控制面契约

不复制 Flink 的控制面结构，并不意味着没有控制面。nop-stream 需要最小分布式控制面契约，保证 `DeploymentPlan` 能被可靠下发、执行、监控和恢复。

控制面角色：

| 角色 | 职责 |
|---|---|
| `JobCoordinator` | 持有 canonical plan、生成 DeploymentPlan、分配 task、触发 epoch、维护 fencing token |
| `RuntimeNode` | 注册到集群、汇报心跳、承载 task attempt、暴露本节点资源和 transport endpoint |
| `TaskAttempt` | 某个 stable task 的一次执行尝试，绑定 attemptId 和 fencing token |
| `NodeLease` | RuntimeNode 的存活租约，超时后其 task attempt 被视为失效 |
| `TaskAssignment` | stable task 到 RuntimeNode 的部署映射，是 DeploymentPlan 的运行期分配结果 |
| `ClusterRegistry` | 记录 active coordinator、runtime nodes、node lease 和 task assignment 的一致视图 |

职责边界：

| 主题 | 契约 |
|---|---|
| plan 所有权 | `JobCoordinator` 是 `PartitionedPlan` 和 `DeploymentPlan` 的唯一写入者 |
| task 下发 | `JobCoordinator` 将 `TaskAssignment` 下发到 `RuntimeNode`，RuntimeNode 只执行不改写语义 |
| 心跳 | RuntimeNode 周期性报告 node 状态、task attempt 状态、checkpoint progress 和资源占用 |
| 失败检测 | `NodeLease` 超时、task attempt 失败、transport channel 关闭都会触发 coordinator 进入恢复流程 |
| task 撤销 | 新 attempt 创建前必须 fence 旧 attempt；旧 attempt 的输出、ACK、commit 全部被拒绝 |
| checkpoint 触发 | 只有 active `JobCoordinator` 可以创建 epoch；source task 只响应带有效 fencing token 的 epoch |
| 调度策略 | 初始版本采用全局恢复和重新部署；局部恢复是后续优化，不改变语义契约 |

设计决策：控制面使用 Nop 的模型和租约抽象表达，不引入 Flink 的 SlotSharingGroup、ExecutionAttempt 层级和网络栈。但 task attempt、lease、assignment、fencing 这些分布式正确性概念不能省略。

## 3. 稳定身份体系

### 3.1 作业身份

| 身份 | 语义 |
|---|---|
| `jobId` | 逻辑作业 ID，跨重启稳定 |
| `pipelineId` | 稳定 pipeline lineage ID，跨重启和同一 lineage 的重新部署保持不变 |
| `checkpointNamespace` | checkpoint/savepoint 的状态命名空间，默认由 `jobId + pipelineId` 决定 |
| `planVersion` | canonical plan 的版本号，用于 savepoint 兼容性检查，不作为状态路径身份 |
| `deploymentId` | 一次部署实例 ID，重新部署后变化，不进入状态路径 |
| `runId` | 一次运行的 ID，重启后变化 |
| `attemptId` | 某个 task 的一次执行尝试，失败重启后变化 |
| `fencingToken` | Coordinator 发放的写入令牌，用于拒绝旧 attempt 的输出和 commit |

状态路径不能包含 `deploymentId`、`runId` 或 `attemptId`，否则失败恢复无法找到旧状态。外部副作用和日志可以包含 attempt 信息，用于诊断和 fencing。

`pipelineId` 必须表示稳定 lineage，而不是每次部署都变化的实例 ID。如果用户希望从 savepoint 派生一个新的 lineage，必须显式声明新的 `checkpointNamespace` 或 savepoint restore mapping；运行时不能用新的部署 ID 隐式替换旧状态路径。

### 3.2 OperatorId

`operatorId` 是状态和 checkpoint 的核心路由键，必须稳定。

生成规则：

| 优先级 | 来源 | 说明 |
|---|---|---|
| 1 | 用户显式 uid | 用户通过 API 或 XDSL 指定，最高稳定性 |
| 2 | 模型路径 | 由 source/operator/sink 在 StreamModel 中的 canonical path 生成 |
| 3 | 结构 hash | 对无 uid 的简单链路按规范化拓扑生成 hash |

不允许使用 Java 对象 hash、链内 index、Transformation 自增 ID 作为持久状态的唯一身份。这些值可以用于运行时调试，但不能用于 checkpoint 和 savepoint 兼容性。

### 3.3 TaskId 与 Subtask

`PartitionedPlan` 将每个 job vertex 展开为多个 subtask。

稳定 task 身份由以下信息组成：

```text
jobId / pipelineId / vertexId / subtaskIndex
```

运行时 attempt 身份在稳定 task 身份后追加：

```text
jobId / pipelineId / vertexId / subtaskIndex / attemptId
```

checkpoint ACK 使用稳定 task 身份。transport fencing 和诊断使用 attempt 身份。

### 3.4 StateShard

为避免复制 Flink 的 key-group 术语，同时保留分布式状态恢复需要的逻辑分片，nop-stream 引入 `StateShard`。

`StateShard` 的语义：

| 属性 | 说明 |
|---|---|
| `stateShardCount` | 一个 keyed vertex 的逻辑状态分片总数，作业生命周期内稳定 |
| `stateShardId` | `0 <= id < stateShardCount` 的逻辑分片编号 |
| `ownerSubtask` | 当前 plan 中拥有该 shard 的 subtask |
| `hashPolicy` | key 到 shard 的确定性 hash 规则 |

key 到 state shard 的语义规则：

```text
stateShardId = stableHash(normalizedKey) mod stateShardCount
```

`StateShard` 不是 Flink key-group 的照搬。它只承担 nop-stream 的稳定状态路由职责，不引入 Flink 的序列化器、KeyGroupRangeAssignment 或 ExecutionGraph 结构。

### 3.5 StatePath

状态持久化路径必须可由模型确定。

推荐状态路径：

```text
checkpoint/{checkpointNamespace}/{epochId}/{operatorId}/{subtaskIndex}/{stateShardId}/{stateName}
```

非 keyed operator state 使用特殊 shard：

```text
checkpoint/{checkpointNamespace}/{epochId}/{operatorId}/{subtaskIndex}/operator/{stateName}
```

source split state 使用：

```text
checkpoint/{checkpointNamespace}/{epochId}/{operatorId}/{subtaskIndex}/source/{splitId}
```

sink transaction state 使用：

```text
checkpoint/{checkpointNamespace}/{epochId}/{operatorId}/{subtaskIndex}/sink/{transactionId}
```

设计约束：状态路径必须只由稳定身份、epoch 和 state name 构成，不能包含对象内存地址、临时 index 或本地文件路径。

## 4. 分区与数据交换

### 4.1 Edge Partition Policy

每条边必须在 `PartitionedPlan` 中记录 partition policy。

| Policy | 语义 | 使用场景 |
|---|---|---|
| `FORWARD` | 上下游 subtask 一一对应 | chain 边界、同并行度直连 |
| `HASH` | 按 key hash 到 state shard，再映射到 owner subtask | keyBy、keyed window、keyed CEP |
| `REBALANCE` | round-robin 或负载均衡分发 | 无 key 的并行扩散 |
| `BROADCAST` | 复制到所有下游 subtask | 配置流、规则流 |
| `UNION` | 多上游合并到下游输入集合 | 多 source 合并 |
| `SINGLETON` | 所有数据汇聚到 subtask 0 | 全局 sink、全局聚合 |

`HASH` policy 必须包含 key selector、hash policy、stateShardCount 和 shard owner 映射。只记录 `ResultPartitionType` 不足以表达分布式语义。

### 4.2 Channel Identity

分布式 transport 的 channel 必须有稳定身份。

```text
edgeId / upstreamSubtaskIndex / downstreamSubtaskIndex / channelIndex
```

barrier 对齐、watermark 合并、输入阻塞和 failure fencing 都以 channel identity 为单位，而不是以 Java 队列对象为单位。

### 4.3 Transport Backend

transport 是可替换后端。

| Backend | 适用场景 | 语义要求 |
|---|---|---|
| local queue | 单进程开发和测试 | 保序、可关闭、barrier 可传播 |
| remote RPC | 多进程执行 | 每 channel 保序、支持 fencing、支持 backpressure |
| message bus | 跨节点缓冲 | 需要记录 offset，恢复时可重放或丢弃旧 attempt 数据 |

transport 必须保证同一 channel 内 record、watermark、barrier 的顺序一致。跨 channel 不要求全局顺序。

### 4.4 Backpressure

成熟分布式执行必须定义 backpressure 行为。

| 场景 | 规则 |
|---|---|
| 下游缓冲满 | 上游对应 channel 阻塞或异步挂起 |
| barrier 对齐中 | 已收到 barrier 的输入 channel 暂停处理后续 data，直到对齐完成 |
| task 失败 | 相关 channel 关闭，旧 attempt 输出被 fencing token 拒绝 |
| checkpoint 超时 | 对齐状态释放；若作业继续运行则保留已 preCommit sink transaction 等待后续 epoch subsume，若进入全局恢复则回滚到最新 durable epoch |

## 5. Epoch Checkpoint 协议

### 5.1 Epoch 是一致性的中心

`checkpointId` 在分布式语义中提升为 `epochId`。一个 epoch 绑定以下内容：

| 内容 | 说明 |
|---|---|
| source offset | 每个 source split 在 epoch 切点的读取位置 |
| operator state | 每个 operator/subtask/state shard 的状态快照 |
| timer state | event-time 和 processing-time timer 的待触发集合 |
| watermark state | 输入 watermark 和 idle 状态 |
| sink transaction | 每个 sink subtask 的 pending transaction |
| plan fingerprint | 生成该 epoch 时的 PartitionedPlan 指纹 |
| fencing token | 允许提交该 epoch 的 coordinator token |

exactly-once 的含义是：系统恢复到 epoch N 后，对外可见副作用等价于所有 epoch <= N 已提交，所有 epoch > N 未提交。

本文后续称这种语义为 `STRICT_EXACTLY_ONCE`。如果 sink 在 epoch durable 前已经产生不可回滚的外部可见副作用，即使写入是幂等的，也只能称为 `EFFECTIVELY_ONCE` 或 `IDEMPOTENT_ON_RETRY`，不能声明为严格端到端 exactly-once。

### 5.2 Epoch 生命周期

Epoch 生命周期如下：

```text
CREATED
  -> INJECTING
  -> ALIGNING
  -> SNAPSHOTTING
  -> PRECOMMITTED
  -> DURABLE
  -> COMMITTED

任意阶段 -> ABORTED
```

状态含义：

| 状态 | 含义 |
|---|---|
| `CREATED` | Coordinator 分配 epochId，建立待 ACK 集合 |
| `INJECTING` | source subtask 在读取线程中注入 barrier |
| `ALIGNING` | 多输入 task 等待所有输入 channel barrier 到齐 |
| `SNAPSHOTTING` | task 生成本地 state snapshot |
| `PRECOMMITTED` | sink 已完成 epoch 对应 transaction 的 preCommit |
| `DURABLE` | epoch manifest 和 state segment 已持久化 |
| `COMMITTED` | sink commit 通知已完成或可重试完成 |
| `ABORTED` | epoch 未 durable。若作业继续运行，已 preCommit 的 sink transaction 不能直接 rollback，必须保留到后续 durable epoch subsuming commit；若进入全局恢复，则回滚最新 durable epoch 之后的 non-durable transaction |

### 5.3 Barrier 注入规则

barrier 只能从 source subtask 注入，且必须由 source 读取线程注入。

禁止行为：

| 禁止行为 | 原因 |
|---|---|
| 从 scheduler 线程直接调用 operator 注入 barrier | 会与 source collect 并发交错，破坏切点 |
| 对非 source task 主动注入 barrier | 会绕过真实数据流，破坏 Chandy-Lamport 快照语义 |
| barrier 不进入 transport channel | 下游无法按 channel 对齐 |

source 注入规则：

```text
source reader observes pending epoch N
    stop emitting records after current safe point
    snapshot split offset for records before N
    emit CheckpointBarrier(N) to all output channels
    resume emitting records after N
```

safe point 由 source connector 定义。文件、批量加载、消息队列、CDC 的 safe point 不同，但都必须能给出可恢复 offset。

### 5.4 Barrier 对齐规则

单输入 task 收到 barrier 后立即 snapshot。

多输入 task 的规则：

```text
on barrier N from channel C:
    mark C aligned for N
    block records after barrier N from C
    continue processing records from unaligned channels
    when all input channels aligned for N:
        snapshot local state
        emit barrier N to all output channels
        unblock aligned channels
```

设计决策：aligned checkpoint 是基线能力。unaligned checkpoint 是性能优化，不是 exactly-once 正确性的前置条件。

### 5.5 Snapshot 内容

每个 task 对 epoch N 上报 `TaskEpochSnapshot`。

必须包含：

| 内容 | 说明 |
|---|---|
| task identity | 稳定 task 身份，不含 attemptId |
| operator snapshots | 按 operatorId 分组 |
| keyed state shards | 每个 shard 独立引用 |
| timer state | 事件时间和处理时间 timer |
| watermark state | 输入 channel watermark 和 idle 标记 |
| source split state | source offset 或 split cursor |
| sink transaction state | pending transaction handle |
| metrics | snapshot size、duration、alignment duration |

### 5.6 Epoch Manifest

Coordinator 收齐所有 task snapshot 后生成 epoch manifest。

manifest 是恢复的唯一入口，必须持久化。

manifest 包含：

| 字段 | 说明 |
|---|---|
| epochId | checkpoint epoch |
| jobId / pipelineId | 作业身份 |
| planFingerprint | PartitionedPlan 指纹 |
| taskSnapshots | task 到 state segment 的映射 |
| sourceOffsets | source split offset 汇总 |
| sourceEnumeratorSnapshots | source split registry、assignment、finished split、discovery cursor 等全局 source 枚举状态 |
| sinkTransactions | sink pending transaction 汇总 |
| stateFormatVersion | 状态格式版本 |
| createdTime / durableTime | 时间戳 |
| checksum | manifest 完整性校验 |

manifest 必须先于 `notifyCheckpointComplete` 持久化完成。sink commit 只能发生在 manifest durable 之后。

### 5.7 Commit 与 Subsuming

checkpoint complete 通知遵守 subsuming contract：收到 epoch N 完成通知时，sink 可以提交所有 `epoch <= N` 且未提交的 pending transaction。

sink commit 必须幂等。即使 coordinator 在 durable 后、通知过程中失败，恢复后的 coordinator 也可以重新通知 commit，不得产生重复外部副作用。

epoch log 必须持久化 `DURABLE` 和 `COMMITTED` 的状态变化。`COMMITTED` 是优化状态，不是恢复前提；恢复逻辑必须能够从 `DURABLE` epoch 重试 sink commit，并依赖 sink 幂等 commit 保证不会重复外部副作用。

### 5.8 Checkpoint 并发策略

基线设计只允许一个 checkpoint epoch in-flight。

选择单 in-flight 的原因：

| 原因 | 说明 |
|---|---|
| 简化 sink pending transaction | 每个 subtask 最多只有一个正在对齐或快照的 epoch，减少事务交错 |
| 简化 barrier 对齐 | 不需要同时维护多个 epoch 的 channel 阻塞状态 |
| 简化恢复 | 最新 durable epoch 之后的状态全部 abort 或重试 commit |
| 满足首版成熟语义 | exactly-once 正确性优先于 checkpoint 吞吐 |

后续可以扩展多个 in-flight checkpoint，但必须同时定义 barrier N/N+1 的对齐顺序、sink pending transaction 队列、subsuming 和 abort 规则。

### 5.9 Bounded Source 与 Final Epoch

有限输入作业必须以 final epoch 收尾。

规则：

| 场景 | 规则 |
|---|---|
| 单 source 完成 | source 发出 final barrier 后再发出 finished 标记 |
| 多 source 部分完成 | 已完成 source 的 input channel 标记为 finished，不再阻塞后续 epoch 对齐 |
| 所有 source 完成 | Coordinator 触发 final epoch，manifest durable 后通知 sink commit 并结束作业 |
| final epoch 失败 | 按普通 epoch failure 恢复，source 从 durable offset 继续或确认 finished 状态 |

final epoch 的语义是：所有对外可见 sink 副作用都已经绑定到某个 durable epoch，作业结束不是绕过 checkpoint 的特殊路径。

## 6. Source Exactly-Once 协议

### 6.1 Source 能力分级

source 必须声明一致性能力。

| 能力 | 语义 | exactly-once 可用性 |
|---|---|---|
| `REPLAYABLE` | 可从 checkpoint offset 重放 | 可参与 exactly-once |
| `TRANSACTIONAL_READ` | 外部系统支持事务读或一致快照 | 可参与 exactly-once |
| `AT_LEAST_ONCE` | 可恢复但可能重复 | 不能单独提供 exactly-once |
| `BEST_EFFORT` | 无可靠 offset | 禁止声明 exactly-once |

如果作业声明 `exactlyOnce=true`，所有 source 必须满足 `REPLAYABLE` 或 `TRANSACTIONAL_READ`。否则作业构建失败。

### 6.2 Source Split

分布式 source 由 split 构成。

| 概念 | 说明 |
|---|---|
| source split | 可独立读取和恢复的输入分片 |
| split owner | 当前负责该 split 的 source subtask |
| split cursor | 该 split 的可恢复读取位置 |
| split assignment | split 到 source subtask 的分配模型 |

split assignment 必须进入 `PartitionedPlan` 或其运行时可持久化扩展中。恢复时，split owner 可以变化，但 split cursor 必须从最新 durable epoch 恢复。

### 6.3 Source Enumerator State

分布式 source 除 reader cursor 外，还必须 checkpoint 全局 split registry 和 assignment state。

source enumerator state 包含：

| 状态 | 说明 |
|---|---|
| discovered splits | 已发现的 split 集合 |
| unassigned splits | 尚未分配给 reader 的 split |
| assigned splits | 已分配但尚未完成的 split 及 ownerSubtask |
| finished splits | 已完成且不应重复分配的 split |
| pending acknowledgements | 已下发但 reader 尚未确认接管的 split |
| discovery cursor | 文件发现、partition discovery、CDC snapshot 阶段等枚举进度 |

恢复规则：先从 epoch manifest 恢复 enumerator state，再恢复 reader split cursor。ownerSubtask 可以重新计算，但 split 不能因为 owner 改变而重复分配或漏分配。

### 6.4 Source Offset Cut

source 在 barrier 注入前必须定义 offset cut。

不同 source 的 cut 语义：

| Source 类型 | Offset Cut |
|---|---|
| 文件/批量加载 | 下一条允许发出的文件路径、行号、页游标或主键游标 |
| 消息队列 | 下一条允许发出的 topic/partition/offset 或 message id |
| CDC | 下一条允许发出的 binlog/LSN/SCN 和表快照阶段 |
| 数据库分页 | 下一条允许发出的 query identity、last key、page token |

offset cut 的统一语义是：**恢复后第一条允许重新发出的记录位置**，也就是 checkpointed records 之后的 next-to-read 位置。

这一定义是 exclusive cut：cut 之前的记录已经纳入 epoch N 的状态，恢复到 epoch N 后不得再次发出；cut 位置及之后的记录可以重新发出。每种 source connector 必须在自己的 offset 类型中明确 inclusive/exclusive 规则，并转换为该统一语义。

## 7. Sink Exactly-Once 协议

### 7.1 Sink 能力分级

sink 必须声明一致性能力。

| 能力 | 语义 | `STRICT_EXACTLY_ONCE` 可用性 |
|---|---|---|
| `TWO_PHASE_COMMIT` | 支持 begin/preCommit/commit/abort/recover | 首选 |
| `STAGED_ATOMIC_COMMIT` | 先写 staging，checkpoint durable 后原子发布 | 可用 |
| `OUTBOX_EPOCH_LOG` | 外部可见性由 epoch log 控制 | 可用 |
| `IDEMPOTENT` | 写入带确定性业务键或去重键 | 仅可声明 `EFFECTIVELY_ONCE`，除非外部可见性受 epoch commit 控制 |
| `UPSERT_BY_KEY` | 最终效果由 key 覆盖决定 | 仅可声明 `EFFECTIVELY_ONCE`，除非外部可见性受 epoch commit 控制 |
| `AT_LEAST_ONCE` | 可能重复写 | 禁止声明 exactly-once |
| `BEST_EFFORT` | 不保证成功或幂等 | 禁止声明 exactly-once |

如果作业声明 `semanticMode=STRICT_EXACTLY_ONCE`，所有 sink 必须满足 `TWO_PHASE_COMMIT`、`STAGED_ATOMIC_COMMIT` 或 `OUTBOX_EPOCH_LOG`。`IDEMPOTENT` 和 `UPSERT_BY_KEY` 只有在外部可见性同样由 epoch commit 控制时才能升级为严格 exactly-once；否则必须降级为 `EFFECTIVELY_ONCE` 或 `IDEMPOTENT_ON_RETRY`。

### 7.2 Transaction Identity

严格提交型 sink 的 transaction id 必须由稳定身份和 epoch 决定。

推荐格式：

```text
{jobId}:{pipelineId}:{operatorId}:{subtaskIndex}:{epochId}
```

不允许 transaction id 包含随机数作为唯一身份。可以附加 attemptId 作为诊断字段，但外部可见事务身份必须以 epoch 为中心，以便恢复后幂等 commit/abort。

### 7.3 Sink 生命周期

sink 的 epoch 生命周期：

```text
begin epoch N transaction
write records before barrier N into transaction N
on barrier N:
    preCommit transaction N
    snapshot transaction handle
    begin epoch N+1 transaction immediately
on notifyCheckpointComplete(N):
    commit all transactions <= N
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

barrier N 之后的数据必须写入 epoch N+1 或更高 epoch 的 transaction，不能继续写入 epoch N。checkpoint N 可能长时间 pending，因此 sink 必须允许多个 pending transaction 并存。`notifyCheckpointComplete(N)` 只提交已经 durable 的 pending transaction，不负责创建下一个 transaction。

`notifyCheckpointAborted(N)` 不等价于“丢弃 N 之前已经处理的数据”。如果作业继续运行，transaction N 中包含 barrier N 之前的输出，这些输出不会自动重新进入 transaction N+1；因此 transaction N 必须作为 precommitted pending transaction 保留，并由后续 durable epoch 通过 subsuming commit 提交。只有当系统决定全局恢复到最新 durable epoch 时，才允许 abort 最新 durable epoch 之后的 non-durable transactions，因为 source 和 operator 会从该 durable epoch 重新处理这些记录。

### 7.4 Sink Abort 与 Orphan 清理

epoch abort 后必须处理外部事务和内部 segment 的孤儿资源。

| 资源 | 清理规则 |
|---|---|
| non-durable sink transaction | 作业继续运行时保留等待后续 subsuming commit；全局恢复时同步 abort 或标记为 recovery-time abort，但仅限最新 durable epoch 之后且未被后续 durable manifest subsume 的 transaction |
| durable but not committed transaction | 不得 abort，恢复后必须重试 commit |
| state segment orphan | manifest 未引用的 segment 可异步清理 |
| source assignment transient state | 未进入 durable manifest 的临时 assignment 可丢弃 |
| commit uncertainty | 依赖 transaction id 幂等查询或重复 commit 解决 |

### 7.5 外部系统约束

| 外部系统 | exactly-once 条件 |
|---|---|
| JDBC | 使用事务表、唯一键、epoch transaction log 或 outbox pattern |
| 消息队列 | 支持事务 producer，或业务幂等 key 并降级为 effectively-once |
| 文件 | 使用临时文件 + atomic rename + manifest commit |
| HTTP/RPC | 需要外部事务或 epoch outbox；只有幂等键时不能声明 strict exactly-once |
| CDC 输出 | 需要目标端事务、staging publish 或 epoch outbox；普通 upsert 只能声明 effectively-once |

## 8. 状态管理修订

### 8.1 状态后端职责

状态后端必须支持三个层次：

| 层次 | 说明 |
|---|---|
| runtime state | task 处理时的本地状态，可在内存、Redis、Rocks-like backend 中 |
| snapshot state | epoch 切点的不可变状态片段 |
| durable state | checkpoint storage 中可恢复的 state segment |

runtime state 可以是对象存取模型。snapshot 和 durable state 必须有版本、类型、校验和、所属 state path。

### 8.2 State Segment

state snapshot 不应只是一张大 Map。分布式恢复需要 segment 化。

segment 粒度：

| Segment | 粒度 |
|---|---|
| operator state segment | operatorId + subtaskIndex + stateName |
| keyed state segment | operatorId + subtaskIndex + stateShardId + stateName |
| timer segment | operatorId + subtaskIndex + stateShardId + timer domain |
| source segment | operatorId + subtaskIndex + splitId |
| sink segment | operatorId + subtaskIndex + transactionId |

segment 可以由 LocalFile、JDBC、对象存储或其他 backend 持久化。epoch manifest 只记录 segment 引用和校验信息。

### 8.3 序列化策略

控制面元数据必须可 JSON round-trip。

状态 payload 的默认格式仍使用 Nop `JsonTool`，但成熟分布式场景允许通过状态后端声明可替换 payload codec。

约束：

| 约束 | 说明 |
|---|---|
| metadata JSON | plan、manifest、state segment descriptor 必须 JSON 可读 |
| payload pluggable | 大状态可选择二进制 payload，但 descriptor 必须说明 codec |
| schema version | 每个 state name 必须记录 value schema version |
| checksum | 每个 segment 必须有 checksum |
| compatibility | savepoint 恢复必须检查 codec 和 schema version |

### 8.4 Timer State

窗口和 CEP 的 timer 必须进入 checkpoint。

timer state 包含：

| 字段 | 说明 |
|---|---|
| timer domain | event-time 或 processing-time |
| timestamp | 触发时间 |
| key/stateShard | keyed timer 的归属 |
| namespace | window 或 CEP namespace |
| callback owner | operatorId 和 timer service identity |

不 checkpoint timer 的窗口实现不能声明支持 exactly-once 恢复，因为恢复后窗口可能永不触发或重复触发。

processing-time timer 不提供确定性重放语义，因为它依赖 wall clock。恢复时，如果 checkpoint 中的 processing-time timer 已经过期，可以立即触发或按 connector/operator 策略延迟触发；但无论触发时机如何，operator state 和 sink epoch commit 不能产生重复外部副作用。需要确定性结果的窗口和 CEP 逻辑应优先使用 event-time timer。

### 8.5 Watermark State

恢复后 watermark 不能倒退到破坏窗口语义的状态。

每个 input channel 需要记录：

| 状态 | 说明 |
|---|---|
| last watermark | 最近处理到的 watermark |
| idle status | 输入是否 idle |
| alignment status | checkpoint barrier 对齐状态 |

恢复时允许 watermark 保守恢复，但不能导致已完成窗口重新产生对外副作用。sink exactly-once 通过 epoch transaction 屏蔽重复输出。

## 9. CEP 与 Window 的统一运行时要求

### 9.1 只能有一套窗口语义

公共 `WindowedStream` API、runtime `WindowOperator`、checkpoint state、timer service 必须收敛到同一套窗口语义。

设计约束：

| 约束 | 说明 |
|---|---|
| 单一窗口 runtime | `apply/aggregate/reduce` 必须使用同一个 window operator family |
| state backend 接入 | window contents、accumulator、merge metadata 必须进入统一 state backend |
| timer checkpoint | event-time 和 processing-time timer 必须快照 |
| merging windows | session/merge window 的 merge set 必须持久化 |
| allowed lateness | late data、cleanup timer、side output 必须有明确恢复语义 |

### 9.2 CEP 必须接入统一状态

CEP 可以保留独立 NFA 和 Pattern API，但作为流算子运行时必须接入统一 state backend。

约束：

| CEP 状态 | 存储要求 |
|---|---|
| NFA state | keyed state，按 state shard 路由 |
| SharedBuffer | keyed state，支持 segment snapshot |
| event queue | keyed map/list state，checkpoint 可恢复 |
| timers | timer state，纳入 epoch snapshot |
| pattern model | operator metadata，参与 plan fingerprint |

`SimpleKeyedStateStore` 只能用于独立内存模式的 CEP API，不允许作为分布式 exactly-once 作业的 CEP operator state backend。

## 10. 故障恢复模型

### 10.1 基线恢复策略

成熟 exactly-once 的正确性基线采用全局 epoch 恢复。

流程：

```text
detect failure
    fence failed runId/attempts
    stop or isolate all tasks of the pipeline
    load latest durable epoch manifest
    rebuild DeploymentPlan if node assignment changed
    restore source offsets, operator state, timers, sink transactions
    restart tasks with new attemptId and fencingToken
    resume from epoch + 1
```

全局恢复比局部恢复更简单，但语义完整。region/local failover 是后续优化，不是 exactly-once 的前置条件。

### 10.2 Fencing

分布式 exactly-once 必须防止旧 attempt 继续输出。

fencing 场景：

| 场景 | Fencing 规则 |
|---|---|
| task restart | 新 attempt 获得新 token，旧 token 输出被拒绝 |
| coordinator failover | 新 coordinator 获得集群 lease，旧 coordinator commit 被拒绝 |
| sink commit | external transaction 带 epoch identity，重复 commit 幂等 |
| transport write | channel 校验 attempt token，旧 attempt channel 关闭 |

### 10.3 Coordinator HA

Coordinator 是逻辑单点，但不能成为 exactly-once 的单点故障。

要求：

| 能力 | 说明 |
|---|---|
| durable epoch log | CREATED、DURABLE、COMMITTED 等关键状态必须持久化 |
| cluster lease | 同一 pipeline 同时只能有一个 active coordinator |
| fencing token | coordinator 切换后旧 token 全部失效 |
| idempotent recovery | 新 coordinator 可重复 commit durable epoch，重复 abort non-durable epoch |

Nop 平台可以通过已有集群锁、数据库锁或外部协调服务提供 lease。具体实现是 runtime backend 决策，语义必须一致。

### 10.4 恢复兼容性

恢复时必须检查：

| 检查项 | 失败处理 |
|---|---|
| plan fingerprint | 不兼容则拒绝自动恢复 |
| operatorId 集合 | 缺失状态的 operator 按策略拒绝或使用初始状态 |
| state schema version | 不兼容则要求显式迁移 |
| stateShardCount | 不一致则要求 rescale manifest 或拒绝 |
| sink transaction protocol | 不兼容则拒绝 exactly-once 恢复 |

savepoint 可以支持显式迁移，但迁移必须通过模型级 action 描述，不能由运行时猜测。

### 10.5 Rescale 与状态重分配

parallelism 变化必须通过显式 rescale manifest 或 migration action 描述。

重分配规则：

| 状态类型 | Rescale 规则 |
|---|---|
| keyed state | `stateShardCount` 不变时，按 `StateShard.ownerSubtask` 重新归属；payload 不按 key 逐条重写 |
| non-keyed operator state | operator 必须声明 redistribution policy，否则拒绝自动 rescale |
| union/list operator state | 可声明 union redistribution，所有新 subtask 读取同一集合后自行过滤 |
| broadcast state | 所有 subtask 获取完整副本，必须校验版本一致 |
| source split state | 按 split registry 重新分配 owner，split cursor 不随 subtask 下标绑定 |
| sink pending transaction | 不允许跨 subtask 静默迁移；必须先完成、abort，或由 connector 声明显式 takeover 协议 |

`stateShardCount` 默认不可改变。改变 `stateShardCount` 等价于 keyed state 重分片，必须提供显式 migration action 和校验报告。

### 10.6 模型演化边界

模型演化必须在恢复前完成兼容性判断。

| 变化 | 默认策略 |
|---|---|
| 新增无状态 operator | 可兼容，前提是不改变上下游 operatorId 和 state route |
| 删除无状态 operator | 可兼容，前提是不改变状态 operator 的输入语义 |
| 新增有状态 operator | 默认使用初始状态，必须显式确认 |
| 删除有状态 operator | 默认拒绝，除非 migration action 丢弃其状态 |
| 修改 operatorId | 默认拒绝，除非提供 old->new 映射 |
| 修改 key selector/hash policy | 默认拒绝，因为 state shard 路由变化 |
| 修改 state schema/codec | 默认拒绝，除非提供 schema migration |
| 修改 stateShardCount | 默认拒绝，除非提供 reshard migration |
| 修改 sink protocol | 默认拒绝 strict exactly-once 恢复 |

## 11. 模块职责修订

### 11.1 模块边界

| 模块 | 修订后职责 |
|---|---|
| `nop-stream-api` | 用户可见 API、function contract、source/sink 一致性能力声明、公共模型接口 |
| `nop-stream-core` | StreamModel、StreamGraph、JobGraph、PartitionedPlan、DeploymentPlan、优化和校验 |
| `nop-stream-runtime` | RuntimeTopology、本地/分布式 task 执行、transport backend、fencing、node lifecycle |
| `nop-stream-checkpoint` | Epoch coordinator、manifest、state segment descriptor、checkpoint/savepoint storage contract |
| `nop-stream-connector` | replayable source、transactional/idempotent sink、split 和 offset 协议适配 |
| `nop-stream-cep` | Pattern/NFA/SharedBuffer，以及接入统一状态后端的 CEP operator |
| `nop-stream-flow` | XDSL StreamModel 编排，支持 Delta 定制 |
| `nop-stream-flink` | 可选外部后端适配，不作为 nop-stream 内核设计来源 |
| `nop-stream-fraud-example` | 展示 StreamModel/CEP/source/sink/checkpoint 的端到端示例 |

### 11.2 依赖方向

依赖只能从右向左：运行时和集成模块依赖 core，core 依赖 api，api 不依赖任何实现模块。

```text
runtime/checkpoint/connector/cep/flow -> core -> api
```

细化规则：

| 规则 | 说明 |
|---|---|
| core 不依赖 runtime | core 只定义模型和编译结果 |
| checkpoint 不依赖具体 transport | checkpoint 通过 plan 和 task identity 工作 |
| connector 不依赖具体 runtime | connector 声明 source/sink 能力和状态协议 |
| cep 不依赖 runtime checkpoint 实现 | CEP operator 通过标准 state/timer 接口接入 |
| runtime 不依赖 fraud-example | 示例不能反向影响内核 |

`runtime` 与 `checkpoint` 的关系通过 epoch coordinator 和 storage SPI 解耦。runtime 可以调用 checkpoint 抽象，checkpoint 不能依赖具体 transport backend。

## 12. XDSL 与 Delta 契约

### 12.1 StreamModel 是唯一入口

Java DataStream API、XDSL 和测试构造器都必须生成同一类 canonical StreamModel。

设计理由：

| 理由 | 说明 |
|---|---|
| 可逆计算 | Delta 应作用于模型，不能 patch 运行时对象 |
| 稳定 ID | operatorId 从 canonical model path 生成 |
| 可审计 | StreamModel、PartitionedPlan、DeploymentPlan 都可序列化和对比 |
| 多后端 | 同一模型可编译到本地 runtime、分布式 runtime 或外部 backend |

### 12.2 Delta 允许修改的内容

| 内容 | 是否允许 |
|---|---|
| source/sink 配置 | 允许 |
| operator 参数 | 允许 |
| parallelism | 允许，但会触发 PartitionedPlan 重新生成和兼容性检查 |
| stateShardCount | 默认不允许，除非提供 rescale action |
| operatorId | 不允许静默修改，必须显式迁移 |
| checkpoint storage | 允许，但必须兼容 manifest 读写 |
| exactly-once 降级 | 不允许静默降级，必须显式声明语义变化 |

## 13. Exactly-Once 作业校验

当作业声明 exactly-once 时，编译阶段必须执行静态校验。

校验项：

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

当 `semanticMode=STRICT_EXACTLY_ONCE` 时，sink 能力校验要求所有 sink 具备严格提交能力：`TWO_PHASE_COMMIT`、`STAGED_ATOMIC_COMMIT`、`OUTBOX_EPOCH_LOG`，或具备等价的 epoch-controlled visibility。只有幂等或 upsert 能力时，必须降级为 `EFFECTIVELY_ONCE` 或 `IDEMPOTENT_ON_RETRY`。

校验失败时，作业不能以声明的语义模式启动。允许用户显式选择 `AT_LEAST_ONCE`、`EFFECTIVELY_ONCE` 或 `BEST_EFFORT`，但运行时和指标必须暴露语义等级。

## 14. 存储与 Manifest 发布

### 14.1 Atomic Publish

epoch manifest 的发布必须是原子的。

不同存储的规则：

| 存储 | Atomic Publish 规则 |
|---|---|
| LocalFile | 先写临时文件，fsync 后 rename 到 final manifest |
| JDBC | 在事务中写入 manifest、segments index 和 epoch status |
| Object Storage | 写 segment 后写 manifest，manifest key 作为唯一提交点 |
| Message Log | manifest 作为 compacted key 的最后记录 |

如果 state segment 已写入但 manifest 未发布，该 epoch 不可恢复，后续 cleanup 可删除孤儿 segment。

### 14.2 Checkpoint Retention

retention 必须以解析后的 `checkpointNamespace` 为范围，不能跨 namespace 计数后删除当前 namespace 的 checkpoint。默认 `checkpointNamespace` 由 `jobId + pipelineId` 决定，但显式 savepoint 派生或 lineage 迁移可以使用不同 namespace。

保留策略：

| 策略 | 说明 |
|---|---|
| latest N | 每个 checkpointNamespace 保留最近 N 个 durable epoch |
| savepoint protected | savepoint 不受普通 retention 删除 |
| referenced segments | 被 manifest 引用的 segment 才可保留 |
| orphan cleanup | 未被 durable manifest 引用的 segment 可异步清理 |

## 15. 可观测性契约

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

## 16. 与现有设计的关系

本文不删除既有设计，而是补充更强约束。

关系说明：

| 既有主题 | 本文修订 |
|---|---|
| `StreamGraph -> JobGraph -> Task` | 增加 `PartitionedPlan` 和 `DeploymentPlan`，避免 JobGraph 直接执行 |
| `CheckpointPlan` | 提升为 epoch checkpoint 的拓扑视图，必须由 PartitionedPlan 派生 |
| 无 key-group | 保持不复制 Flink key-group，但引入 Nop `StateShard` 作为稳定状态分片 |
| 本地 BlockingQueue transport | 保留为 local backend，但不作为分布式语义来源 |
| JSON 状态快照 | metadata 必须 JSON，payload 默认 JSON 且允许后端声明 codec |
| CEP 独立可用 | 独立 API 保留，分布式 CEP operator 必须接入统一 state/timer |
| WindowOperator 多实现 | 成熟目标要求窗口语义收敛到单一 runtime family |
| Flink 后端 | 作为可选适配，不主导内核模型 |

当本文与既有设计在分布式 exactly-once 主题上出现冲突时，以本文的不变量为准；既有文档可在后续文档同步任务中逐步调整。

## 17. 设计不变量

以下不变量不可违反：

1. 所有持久状态必须有稳定 `operatorId`。
2. 所有 keyed state 必须有确定性 `StateShard` 路由。
3. `PartitionedPlan` 是 parallelism、edge partition、state route、checkpoint route 的唯一语义来源。
4. barrier 只能由 source 读取线程注入，并随数据 channel 传播。
5. epoch manifest durable 之前，sink transaction 不得 commit。
6. 恢复必须从最新 durable epoch manifest 开始。
7. source 不可重放或 sink 不具备严格提交能力时，不允许声明 `STRICT_EXACTLY_ONCE`。
8. 旧 attempt 和旧 coordinator 必须被 fencing。
9. timer state 是窗口和 CEP exactly-once 的必要状态。
10. Delta 只能修改模型，不能 patch runtime object 来改变语义。

## 18. 成熟度验收门槛

一个 `nop-stream` 分布式 exactly-once 实现必须满足以下验收门槛：

| 门槛 | 说明 |
|---|---|
| plan 完整性 | StreamModel、PartitionedPlan、DeploymentPlan 可序列化、可 diff、可 fingerprint |
| 并行执行 | parallelism > 1 时创建全部 subtask，并按 partition policy 路由 |
| source 恢复 | source split offset 在故障后可恢复且不跳读 |
| state 恢复 | operator/keyed/timer/watermark state 按 StatePath 精确恢复 |
| sink 一致性 | 故障发生在 preCommit、durable、commit 任意阶段都不重复外部副作用 |
| coordinator failover | active coordinator 切换后旧 token 被拒绝，新 coordinator 可恢复 epoch |
| barrier 对齐 | 多输入 channel 在 checkpoint 时不混入 barrier 后数据 |
| savepoint 兼容 | plan fingerprint 和 state schema 不兼容时明确拒绝或要求迁移 action |
| 语义暴露 | runtime 明确暴露当前作业 semantic mode |
| 回归测试 | 覆盖 source failure、task failure、sink commit failure、coordinator failover、node restart |

## 19. 结论

`nop-stream` 的分布式 exactly-once 成熟设计应以 `PartitionedPlan + StateShard + Epoch Manifest + Source/Sink Capability Contract + Fencing` 为核心，而不是复制 Flink 的运行时结构。

这个设计保留 Flink 已验证的流处理语义边界，但把语义收束到 Nop 的模型层。这样既能支撑分布式和 exactly-once，也能保持 Nop 平台的模型优先、可序列化、可 Delta 定制和多后端适配能力。
