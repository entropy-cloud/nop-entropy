# nop-stream 对比 Beam 与 Hazelcast Jet 的进一步设计改进分析

> Status: resolved
> Date: 2026-05-23
> Scope: `nop-stream` 分布式执行、checkpoint、source/sink exactly-once、状态与调度设计
> Conclusion: 现有 `distributed-exactly-once-design-amendment.md` 的主方向正确，但仍应吸收 Beam 的可移植模型/动态 source work-unit 语义，以及 Hazelcast Jet 的 processor lifecycle、两阶段 snapshot 和流控细节；建议新增一轮设计修订，重点补齐 work unit、requirements、flow control、processor callback、drain/suspend/savepoint 等契约。

## Context

本分析对比本地源码：

| 项目 | 路径 | 参考定位 |
|---|---|---|
| Apache Beam | `C:/can/sources/beam` | runner-independent model、Fn API、SDF、window/state/timer、bundle finalization |
| Hazelcast Jet | `C:/can/sources/hazelcast` | 嵌入式分布式 DAG runtime、cooperative processor、barrier snapshot、flow control |
| nop-stream 当前修订 | `ai-dev/design/nop-stream/distributed-exactly-once-design-amendment.md` | Nop 风格分布式 exactly-once 目标设计 |

约束：本文件只作为 analysis，指出进一步需要修改的设计点，不直接修改已有设计文件。

## Executive Summary

`nop-stream` 当前修订已经抓住正确主轴：`PartitionedPlan + StateShard + Epoch Manifest + Source/Sink Capability Contract + Fencing`。对比 Beam 和 Hazelcast Jet 后，不需要推翻这个主轴，也不应该复制任一项目的完整实现。

仍建议进一步改进的地方：

| 优先级 | 需要进一步修改的设计 | 来源启发 | 原因 |
|---|---|---|---|
| P0 | 引入 `StreamModel` 的 component registry 与 requirement/capability 校验 | Beam Runner API | 当前设计有模型优先，但缺少 transforms/streams/windowing/coders/environments/requirements 的统一 registry |
| P0 | 引入 source work-unit/restriction/progress/dynamic split 协议 | Beam SDF/Fn API | 当前只有 split/enumerator/cursor，无法表达动态拆分、残余 work、drain truncate、watermark estimator state |
| P0 | 把 sink 2PC 扩展为 processor-level snapshot callback lifecycle | Hazelcast Jet Processor | 当前 sink lifecycle 正确，但应推广到所有 transactional processor，并明确 `prepare/finish(success)/restore` 调用顺序 |
| P0 | 补齐 edge flow-control 与 queue/window 配置 | Hazelcast Jet EdgeConfig/Sender/Receiver | 当前 backpressure 只有原则，分布式 runtime 需要 receive window、ack、packet size、queue capacity 的模型契约 |
| P1 | 完整建模 windowing strategy、trigger、pane、accumulation/retraction | Beam WindowingStrategy | 当前窗口/CEP 要求正确，但窗口语义仍不够可序列化、可 fingerprint、可迁移 |
| P1 | 定义 bundle/work unit finalization，覆盖 source ack 与外部副作用 | Beam bundle finalization | 当前以 epoch 为一致性中心，但缺少“output durable 后回调 source/DoFn finalize”的细粒度协议 |
| P1 | 明确 `AT_LEAST_ONCE` 与 `STRICT_EXACTLY_ONCE` 的运行时差异 | Hazelcast ProcessingGuarantee | 当前有 semantic mode，但未定义 at-least-once 是否允许 barrier 后输入继续处理 |
| P1 | 增加 drain/suspend/export savepoint/initial snapshot 语义 | Beam drain + Hazelcast initial snapshot | 当前只有 bounded final epoch，未完整覆盖运维终止与 savepoint 恢复模式 |
| P2 | 增加 cooperative/non-cooperative processor 调度契约 | Hazelcast Jet Processor | 可避免 blocking UDF 拖慢 checkpoint，但不应过早绑定实现线程模型 |
| P2 | 增加 protocol-level metrics、sampled data、trace context | Beam Fn API + Hazelcast metrics | 当前可观测性列表偏指标名，缺少协议化采样和 per-channel progress |

## Beam 可参考内容

### 1. Runner-independent model 更彻底

Beam 的核心优势不是 runtime，而是把 pipeline 表达为可移植、可引用、可校验的组件图。

参考：

| 路径 | 关键点 |
|---|---|
| `model/pipeline/src/main/proto/org/apache/beam/model/pipeline/v1/beam_runner_api.proto:57-78` | `Components` 用 ID 映射 transforms、PCollections、windowing strategies、coders、environments |
| `beam_runner_api.proto:80-112` | `Pipeline` 包含 components、root transform IDs、requirements，runner 遇到未知 requirement 应拒绝 |
| `beam_runner_api.proto:131-198` | `PTransform` 通过 stable `unique_name`、URN/payload、inputs/outputs、environment 表达语义 |

对 `nop-stream` 的影响：

| 当前设计 | 进一步修改建议 |
|---|---|
| 已有 `StreamModel -> StreamGraph -> JobGraph -> PartitionedPlan` | 在 `StreamModel` 中增加 canonical `components` registry，而不是让 windowing、codec、environment、requirements 分散在节点属性中 |
| 已有 `operatorId` 稳定规则 | 增加 `streamId`/`collectionId` 稳定身份，输出流也参与 fingerprint、metrics、state routing 和 sampled data |
| 已有 exactly-once 编译校验 | 增加 `requirements` 列表，未知 requirement 或 backend 不支持的 requirement 必须构建失败 |

建议新增的 Nop 概念：

| 概念 | 用途 |
|---|---|
| `StreamComponents` | transforms、streams、windowingStrategies、coders、schemas、environments、sideInputs、requirements 的统一 registry |
| `StreamRequirement` | `STATEFUL_PROCESSING`、`SPLITTABLE_SOURCE`、`BUNDLE_FINALIZATION`、`STABLE_INPUT`、`TIME_SORTED_INPUT`、`STRICT_EXACTLY_ONCE` 等能力要求 |
| `StreamEnvironment` | 本地 Java、远程 worker、外部 engine adapter 的执行环境描述；不复制 Beam SDK harness，但保留后端可替换边界 |

### 2. Splittable DoFn 对 source 设计有直接参考价值

Beam 对 source 的抽象比“split + cursor”更细：一个 work item 可以被动态拆成 primary/residual，并且能报告 progress、watermark estimator state、drain truncate。

参考：

| 路径 | 关键点 |
|---|---|
| `beam_runner_api.proto:412-445` | SDF 分解为 pair restriction、split and size、process restriction、truncate restriction |
| `beam_runner_api.proto:537-545` | `ParDoPayload` 标记 restriction coder 与 finalization request |
| `beam_fn_api.proto:541-705` | active bundle 可被 runner split，返回 primary/residual work，且二者不重叠、覆盖完整 work |

当前 `nop-stream` 修订已有 source split、enumerator state、exclusive offset cut，但仍缺：

| 缺口 | 为什么重要 | 建议修改 |
|---|---|---|
| dynamic split | 长 split 会造成负载倾斜，rescale 只能在 checkpoint 边界粗粒度调整 | `SourceSplit` 扩展为 `SourceWorkUnit`，包含 `restriction`、`restrictionCoder`、`sizeEstimate`、`progress` |
| primary/residual | task failure、drain、rebalance 时需要证明 work 不重叠不漏读 | 增加 `split(workUnit, fraction)` 返回 `primary + residual`，二者必须 disjoint and complete |
| watermark estimator | source watermark 不是单纯 cursor，Kafka/CDC/file discovery 都需要恢复估计器状态 | `TaskEpochSnapshot` 增加 `watermarkEstimatorState` 或并入 `sourceEnumeratorSnapshots` |
| truncate on drain | 运维 drain 不能无限等待 unbounded source，也不能丢失已承诺 work | 增加 `truncateForDrain(workUnit)`，drain 后 residual 由后续恢复或新 lineage 处理 |

### 3. Bundle finalization 补足 source ack 与外部确认

Beam 的 Fn API 明确区分 bundle processing 与 output commit 后的 finalization callback。

参考：

| 路径 | 关键点 |
|---|---|
| `beam_fn_api.proto:429-447` | `ProcessBundleResponse.requires_finalization` 表示 runner 在 bundle output committed 后必须回调 |
| `beam_fn_api.proto:708-716` | `FinalizeBundleRequest/Response` 是 output commit 后的回调协议 |

当前 `nop-stream` 修订已经定义 sink transaction commit，但对 source ack 的描述不足。消息队列、外部 CDC、HTTP pull source 常见语义是：读取时不能立即 ack，必须等处理结果 durable 或 sink commit 后再确认外部 offset。

建议修改：

| 新设计点 | 说明 |
|---|---|
| `BundleFinalizer` 或 `EpochFinalizer` | 在 epoch durable 且相关 sink commit 可重试后，回调 source/processor 的外部确认动作 |
| `SourceAckPolicy` | `ACK_ON_READ`、`ACK_ON_EPOCH_DURABLE`、`ACK_ON_SINK_COMMIT`、`NO_ACK_REPLAYABLE` |
| `FinalizationState` | finalizer 本身必须幂等，并进入 epoch log，防止 coordinator failover 后重复 ack 或漏 ack |

注意：这不是把 Beam 的 bundle 作为 nop-stream 的 checkpoint 单位。Nop 仍应以 epoch 为一致性中心，但可以在 epoch 内定义更小的 work-unit/bundle，finalization 绑定到 epoch durable/commit 之后。

### 4. WindowingStrategy 应成为模型级对象

Beam 的 windowing strategy 把 window fn、merge status、window coder、trigger、accumulation、allowed lateness、output timestamp、closing/on-time behavior 作为模型组件。

参考：

| 路径 | 关键点 |
|---|---|
| `beam_runner_api.proto:1112-1169` | `WindowingStrategy` 完整记录 window fn、merge status、coder、trigger、accumulation、lateness 等 |
| `beam_runner_api.proto:1194-1277` | accumulation、closing、on-time、output time、time domain 均为模型枚举 |
| `beam_runner_api.proto:1279+` | trigger 是可序列化 DSL |

当前 `nop-stream` 修订要求“只能有一套窗口语义”，但还没有把窗口语义上升为 canonical model。

建议修改：

| 当前设计 | 进一步修改建议 |
|---|---|
| Window/CEP 必须接入统一 state/timer | 增加 `WindowingStrategy` 模型，参与 `PartitionedPlan` fingerprint |
| allowed lateness、cleanup timer 有明确恢复语义 | 增加 pane identity、trigger state、accumulation mode、retraction mode、output timestamp policy |
| merging windows 必须持久化 merge set | 增加 `mergeStatus`，禁止在已 merge 的 stream 上重复执行不兼容 group/window 操作 |

## Hazelcast Jet 可参考内容

### 1. Processor lifecycle 对 transactional exactly-once 很有价值

Hazelcast Jet 把 processor snapshot 与事务提交做成 lifecycle，而不是只在 sink 层处理。

参考：

| 路径 | 关键点 |
|---|---|
| `hazelcast/src/main/java/com/hazelcast/jet/core/Processor.java:363-380` | `saveToSnapshot()` 可多次调用直到完成 |
| `Processor.java:384-423` | `snapshotCommitPrepare()` 是 2PC 第一阶段，保存 pending transaction IDs 到 snapshot |
| `Processor.java:426-476` | `snapshotCommitFinish(success)` 是第二阶段；失败时不要处理 prepared transactions，成功时 commit，commit 必须 eventually succeed |
| `Processor.java:480-528` | `restoreFromSnapshot()` 恢复 snapshot entries；如恢复 transaction ID，应完成 commit 或处理 already committed/expired 情况 |

当前 `nop-stream` 修订的 sink transaction 设计已经修正了 checkpoint abort 语义，但可以进一步抽象：

| 当前设计 | 进一步修改建议 |
|---|---|
| sink 有 begin/preCommit/commit/abort/recover | 把 transaction callback 抽成 `CheckpointParticipant`，sink/source/外部状态 processor 都可参与 |
| `notifyCheckpointAborted(N)` 保留 precommitted transaction 等待 subsume | 对应 `snapshotCommitFinish(false)` 不应 abort prepared transaction；写入 processor lifecycle，避免只在 sink 章节出现 |
| recovery inspect pending transactions | 在 `restoreFromSnapshot` 阶段明确：durable manifest 中的 transaction 必须 commit；missing/already committed/expired 都必须由 connector 幂等处理 |

建议新增的 Nop lifecycle：

```text
onEpochBarrier(N):
    saveState(N) until complete
    prepareCommit(N) until complete
    emit barrier N downstream

onEpochDecision(N, success):
    finishCommit(N, success) until complete

onRestore(epoch N):
    restoreStateSegments(N)
    restoreAndFinalizeDurableTransactions(N)
```

这个 lifecycle 不要求照搬 Jet 的 processor API，但要把“transactional participant 不只等于 sink”写入设计。

### 2. Exactly-once 与 at-least-once 的 barrier 行为应区分

Hazelcast Jet 的 `ProcessingGuarantee` 直接说明：exactly-once 需要等待所有输入 barrier；at-least-once 可以继续处理已收到 barrier 的输入，代价是恢复后可能重复。

参考：

| 路径 | 关键点 |
|---|---|
| `hazelcast/src/main/java/com/hazelcast/jet/config/ProcessingGuarantee.java:19-37` | barrier snapshot；多输入 processor 必须收到所有 barrier 才 snapshot；at-least-once 可继续处理已收到 barrier 的输入 |
| `ProcessingGuarantee.java:55-77` | `AT_LEAST_ONCE` 与 `EXACTLY_ONCE` 的恢复语义差异 |
| `hazelcast/src/main/java/com/hazelcast/jet/impl/execution/ProcessorTasklet.java:150-154` | `waitForAllBarriers` 决定是否在 barrier 后继续 drain 输入 |
| `ProcessorTasklet.java:211` | exactly-once 下初始化为 wait for all barriers |

当前 `nop-stream` 修订已有 semantic mode，但只详细定义了 strict exactly-once 的 aligned checkpoint。

建议修改：

| 模式 | 建议补充的运行时行为 |
|---|---|
| `STRICT_EXACTLY_ONCE` | 已收到 barrier 的 input channel 阻塞 barrier 后 records，直到所有 channel 对齐 |
| `AT_LEAST_ONCE` | 允许继续处理已收到 barrier channel 的 barrier 后 records；checkpoint 只保证恢复到某个 snapshot，可能重复处理 |
| `EFFECTIVELY_ONCE` | 数据处理层可按 exactly-once 或 at-least-once checkpoint 执行，但外部效果依赖幂等/upsert/去重键 |
| `BEST_EFFORT` | 可禁用 checkpoint，但必须在 metrics 和 job metadata 中显式暴露 |

### 3. 分布式 flow control 需要从原则变成模型参数

Hazelcast Jet 的 edge 不只描述 partition policy，还建模 local queue size、distributed receive window、ack、packet size。

参考：

| 路径 | 关键点 |
|---|---|
| `hazelcast/src/main/java/com/hazelcast/jet/config/EdgeConfig.java:56-84` | local SPSC queue capacity，实际队列数量与 sender/receiver parallelism 成乘积 |
| `EdgeConfig.java:87-123` | receive window multiplier，receiver 定期 ack，sender 只能领先 last ack 一个 window |
| `EdgeConfig.java:126-153` | distributed edge packet size limit |

当前 `nop-stream` 修订的 `Backpressure` 章节是正确方向，但对分布式实现仍不够可执行。

建议新增：

| 新模型字段 | 位置 | 说明 |
|---|---|---|
| `edge.queueCapacity` | `PartitionedPlan` 或 `DeploymentPlan` | 本地 channel 队列大小，参与内存预算 |
| `edge.flowControlPolicy` | `DeploymentPlan` | `BLOCKING`、`CREDIT_BASED`、`ACK_WINDOW` |
| `edge.receiveWindowBytes` 或 multiplier | `DeploymentPlan` | 控制远程 sender 可领先数据量 |
| `edge.packetSizeLimit` | `DeploymentPlan` | 控制远程传输批大小 |
| `channel.sequence/ack` | `RuntimeTopology` | 诊断、fencing、backpressure metrics |

这部分应作为 distributed backend 的语义约束，而不是 local queue 实现细节。

### 4. Snapshot phase 与 validation record 值得吸收

Hazelcast Jet 的 snapshot 有 phase 1/phase 2 participant counters，并在 master 侧处理 snapshot record 更新不确定时强制 restart。

参考：

| 路径 | 关键点 |
|---|---|
| `hazelcast/src/main/java/com/hazelcast/jet/impl/execution/SnapshotContext.java:66-94` | phase 1/phase 2 分别有 participant counters 和 active snapshot id |
| `SnapshotContext.java:135-149` | snapshot context 记录 guarantee 与是否 requireSnapshotBeforeProcessing |
| `hazelcast/src/main/java/com/hazelcast/jet/impl/MasterSnapshotContext.java:231-329` | master 启动 phase 1 |
| `MasterSnapshotContext.java:416-552` | validation record、JobExecutionRecord 更新、phase 2 decision；不确定时 forceful restart |

当前 `nop-stream` 修订有 `CREATED -> INJECTING -> ALIGNING -> SNAPSHOTTING -> PRECOMMITTED -> DURABLE -> COMMITTED`，但 coordinator 侧的 phase participant 与 indeterminate publish 处理可以更明确。

建议修改：

| 当前设计 | 进一步修改建议 |
|---|---|
| epoch lifecycle 是单线状态机 | 拆出 coordinator phase：`SNAPSHOT_PHASE1_COLLECTING`、`MANIFEST_PUBLISHING`、`COMMIT_PHASE2_NOTIFYING` |
| atomic publish 只描述 storage 行为 | 增加 `EpochDecisionRecord` 或 `ValidationRecord`，记录本 epoch 是否进入 phase2 commit |
| coordinator failover 依赖 durable epoch log | 明确 indeterminate 状态处理：如果 manifest publish 或 decision record 更新不确定，新 coordinator 必须 fence old run 并从 latest known durable epoch 全局恢复 |
| bounded final epoch | 增加 terminal barrier/snapshot flag，终止作业必须走 terminal epoch，而不是普通 checkpoint 的隐式变体 |

### 5. Cooperative processor 可作为运行时优化契约

Hazelcast Jet 的 cooperative processor 让多个 processor 共享 worker 线程，但要求每次调用短、非阻塞，阻塞 processor 使用专用线程。

参考：

| 路径 | 关键点 |
|---|---|
| `hazelcast/src/main/java/com/hazelcast/jet/core/Processor.java:103-145` | cooperative processor 必须短调用、非阻塞；non-cooperative 可以阻塞但必须周期性返回 |
| `hazelcast/src/main/java/com/hazelcast/jet/impl/execution/TaskletExecutionService.java:143-155` | cooperative 与 blocking tasklet 分组调度 |

对 `nop-stream` 的建议：

| 建议 | 说明 |
|---|---|
| 不把 cooperative API 暴露为用户主模型 | Nop 用户 API 应保持函数式/模型式，不要求用户理解 tasklet |
| 在 runtime SPI 增加 `OperatorSchedulingMode` | `COOPERATIVE`、`BLOCKING`、`ASYNC_IO`，由 connector/operator 声明 |
| checkpoint 约束 blocking operator | blocking operator 必须能在 bounded time 内响应 barrier、cancel、snapshot，否则不能声明 strict exactly-once |

## 不建议照搬的内容

| 来源 | 不建议照搬 | 原因 |
|---|---|---|
| Beam | 不照搬 protobuf/gRPC Fn API 作为内部唯一协议 | Nop 需要 XLang/Delta/Java 模型优先；可以借鉴 component registry 和 requirements，不必引入完整 SDK harness |
| Beam | 不把 bundle 作为 exactly-once 的唯一一致性单位 | `nop-stream` 已选择 epoch manifest，bundle 更适合作为 epoch 内 work unit 和 finalization 粒度 |
| Hazelcast Jet | 不照搬 in-memory replicated snapshot 作为 durable checkpoint | Nop 目标需要 LocalFile/JDBC/ObjectStorage/MessageLog 等 durable manifest；内存副本可作为 cache，不是恢复 source of truth |
| Hazelcast Jet | 不照搬 cluster member quorum 作为唯一 HA 模型 | Nop 可能部署在 DB lock、外部 coordinator、K8s lease 等环境；应抽象为 `ClusterRegistry`/lease/quorum policy |
| Hazelcast Jet | 不把 cooperative processor 作为用户编程模型 | 它适合 runtime SPI，不适合 Nop 的模型驱动用户层 |

## 建议进一步更改的设计清单

### P0: StreamModel Component Registry 和 Requirements

需要在下一版设计中新增：

| 设计项 | 更改说明 |
|---|---|
| `StreamComponents` | 统一保存 transform、stream、windowing strategy、coder/schema、environment、side input、requirement |
| `StreamRequirement` | 编译器和 backend 必须理解的能力要求；未知或不支持则 fail-fast |
| `StreamBackendCapability` | runtime/backend 声明支持哪些 requirement，例如 dynamic split、strict exactly-once、remote state service、bundle finalization |
| fingerprint 输入 | component registry、requirements、coder/schema/windowing strategy 都必须参与 plan fingerprint |

原因：现有设计强调 canonical model，但没有明确 components/requirements 的可序列化结构，后续 XDSL/Delta/外部 backend 难以统一校验。

### P0: SourceWorkUnit、Restriction 和 Dynamic Split

需要新增或修改：

| 设计项 | 更改说明 |
|---|---|
| `SourceWorkUnit` | 替代只描述 split/cursor 的薄模型，包含 sourceId、splitId、restriction、owner、sizeEstimate、watermarkEstimatorState |
| `RestrictionTracker` | connector 负责 claim position、report progress、snapshot restriction cursor |
| `DynamicSplitRequest/Response` | 返回 primary/residual，必须 disjoint and complete |
| `DrainTruncate` | drain/suspend 时 source 可以把无限 restriction 截断成 finite primary 和 residual |
| `WatermarkEstimatorState` | 进入 `TaskEpochSnapshot` 或 source enumerator snapshot，恢复后不能靠默认值重算 |

原因：没有动态 work-unit，分布式 source 只能静态 split，难以支持长文件、CDC snapshot、大 partition、自动扩缩容和 drain。

### P0: CheckpointParticipant Lifecycle

需要把 sink lifecycle 泛化：

| 设计项 | 更改说明 |
|---|---|
| `CheckpointParticipant` | source、sink、外部状态 processor 都可以参与 epoch snapshot/commit |
| `saveState(epoch)` | 可多次调用直到完成，不能长时间阻塞 cooperative runtime |
| `prepareCommit(epoch)` | 2PC 第一阶段，pending external transaction handle 必须写入 snapshot |
| `finishCommit(epoch, success)` | 成功时 commit，失败时保留 prepared transaction 等待 subsume 或 recovery；不能在 continue-running abort 中直接丢弃 |
| `restoreFromEpoch(epoch)` | 恢复 state segment 和 transaction handle，durable transaction 必须 commit 或证明 already committed |

原因：当前修订的 sink 章节已经正确，但如果只写在 sink，会漏掉外部 source ack、transactional operator、outbox processor 等同类问题。

### P0: Distributed Edge Flow Control

需要把 backpressure 从原则提升为模型：

| 设计项 | 更改说明 |
|---|---|
| `FlowControlPolicy` | `BLOCKING_QUEUE`、`CREDIT_BASED`、`ACK_WINDOW` |
| `queueCapacity` | 每 edge/channel 的容量，参与 DeploymentPlan 内存预算 |
| `receiveWindow` | 远程 channel sender 可领先 receiver 的字节数或 item 数 |
| `packetSizeLimit` | 远程批发送大小上限 |
| `channelSequence` | channel 级 sequence/ack，用于 flow-control、metrics、fencing diagnostics |

原因：没有 flow-control 参数，分布式 transport 无法在模型层证明不会无限积压，也无法解释 checkpoint alignment latency。

### P1: WindowingStrategy 完整模型化

需要新增：

| 设计项 | 更改说明 |
|---|---|
| `WindowingStrategy` | windowFn、mergeStatus、windowCoder、trigger、accumulationMode、allowedLateness、outputTime、closingBehavior、onTimeBehavior |
| `PaneState` | pane index/timing、accumulated/retracted state、last firing timestamp |
| `TriggerState` | trigger DSL 状态进入 checkpoint，而不是散落在 operator 内部 |
| `WindowCompatibilityCheck` | savepoint 恢复时检查 window strategy fingerprint |

原因：窗口状态是 exactly-once 的核心。当前设计说“统一窗口语义”，但还没有足够模型字段来保证跨版本恢复和 Delta 审计。

### P1: Bundle/WorkUnit Finalization

需要新增：

| 设计项 | 更改说明 |
|---|---|
| `WorkUnitFinalizer` | output durable 后回调 external ack、offset commit、cleanup |
| `FinalizationHandle` | finalizer handle 写入 epoch manifest 或 epoch log |
| `FinalizationRetryPolicy` | coordinator failover 后可重试，必须幂等 |
| `SourceAckPolicy` | 明确 ack 发生在 read、epoch durable、sink commit 或 never ack |

原因：source exactly-once 不只是不重复读取，还要避免过早 ack 外部系统导致数据丢失。

### P1: Job Termination Modes

当前 bounded source final epoch 不足以覆盖运维场景。

需要新增：

| 模式 | 语义 |
|---|---|
| `CANCEL` | 尽快停止，可 abort non-durable work，不保证输出完整 |
| `DRAIN` | source truncate 成有限 work，terminal epoch durable 后结束 |
| `SUSPEND` | 停止新输入，导出可恢复 savepoint，不要求 sink final commit 到作业完成状态 |
| `EXPORT_SAVEPOINT` | 生成 protected checkpointNamespace，可用于新 lineage restore |
| `RESTORE_FROM_SAVEPOINT` | 明确 initial snapshot、operator mapping、checkpointNamespace 派生规则 |

原因：Beam 的 drain/truncate 和 Hazelcast 的 initial snapshot 都说明，终止/恢复不是普通 failure recovery 的小变体，应成为设计契约。

### P2: Runtime Scheduling Mode

需要新增：

| 设计项 | 更改说明 |
|---|---|
| `OperatorSchedulingMode` | `COOPERATIVE`、`BLOCKING`、`ASYNC_IO` |
| `barrierResponsiveness` | blocking operator 必须声明最大响应时间或被运行时隔离 |
| `snapshotStepBudget` | 大状态 snapshot 必须分步执行，避免阻塞所有 task |

原因：这属于性能和可运维性，不阻塞 exactly-once 语义，但会影响成熟 runtime 的可用性。

### P2: Protocol-level Observability

需要新增：

| 设计项 | 更改说明 |
|---|---|
| `MonitoringInfo` | 统一指标 envelope，包含 metric URN、labels、payload、scope |
| `ProgressSnapshot` | source work-unit progress、bundle progress、checkpoint phase progress |
| `SampledData` | 按 streamId/operatorId 采样元素和异常上下文 |
| `TraceContext` | record/bundle/epoch 级 trace id，方便跨节点诊断 |

原因：当前设计已有指标名，但缺少协议化数据结构，后续多 backend 和远程 runtime 难以统一观测。

## 与当前修订文档的关系

| 当前修订结论 | 是否保留 | 进一步增强 |
|---|---|---|
| `PartitionedPlan` 是分布式 exactly-once 中心模型 | 保留 | `PartitionedPlan` 应引用 `StreamComponents` 和 `requirements`，而不是只承载 task/edge/state route |
| `StateShard` 替代复制 Flink key-group | 保留 | 增加 coder/schema/window namespace fingerprint，确保 state hash 与状态序列化稳定 |
| `Epoch Manifest` 是恢复入口 | 保留 | 增加 decision record、finalization handles、watermark estimator state、work-unit residuals |
| source split/enumerator/cursor | 保留 | 升级为 source work-unit/restriction/dynamic split/progress/drain truncate |
| sink transaction lifecycle | 保留 | 泛化为 `CheckpointParticipant` lifecycle，并明确 `finishCommit(false)` 不 abort prepared transaction |
| aligned checkpoint 是 strict exactly-once 基线 | 保留 | 增加 at-least-once 的非阻塞 barrier 行为定义 |
| backpressure 原则 | 保留 | 增加 flow-control 参数、ack/window/packet/queue 模型 |
| final epoch | 保留 | 扩展为 terminal snapshot、drain、suspend、export/restore savepoint |

## Conclusion

对 `nop-stream` 有可参考内容，而且原先的设计改进仍可进一步完善。

最终判断：

| 判断 | 结论 |
|---|---|
| 是否推翻现有修订 | 不推翻。现有主轴正确，尤其是 `PartitionedPlan`、`StateShard`、`Epoch Manifest`、source/sink capability、fencing |
| 是否继续改 | 需要。Beam 暴露了模型可移植性、requirements、dynamic source work-unit、bundle finalization 的缺口；Hazelcast 暴露了 processor lifecycle、snapshot phase、flow-control 的缺口 |
| 下一步文档 | 建议新增第二份 design amendment，专门覆盖 `StreamComponents + SourceWorkUnit + CheckpointParticipant + FlowControl + TerminationModes` |
| 实现优先级 | 先做模型和协议，再做 runtime 优化；不要先复制 Beam Fn API 或 Hazelcast tasklet runtime |

## References

Beam：

| 路径 | 内容 |
|---|---|
| `C:/can/sources/beam/model/pipeline/src/main/proto/org/apache/beam/model/pipeline/v1/beam_runner_api.proto:57-112` | Components、Pipeline、requirements |
| `C:/can/sources/beam/model/pipeline/src/main/proto/org/apache/beam/model/pipeline/v1/beam_runner_api.proto:131-198` | PTransform stable name、URN/payload、inputs/outputs、environment |
| `C:/can/sources/beam/model/pipeline/src/main/proto/org/apache/beam/model/pipeline/v1/beam_runner_api.proto:412-445` | Splittable DoFn primitive decomposition |
| `C:/can/sources/beam/model/pipeline/src/main/proto/org/apache/beam/model/pipeline/v1/beam_runner_api.proto:518-626` | ParDo state specs、timer specs、restriction coder、finalization、stable/time sorted input |
| `C:/can/sources/beam/model/pipeline/src/main/proto/org/apache/beam/model/pipeline/v1/beam_runner_api.proto:1112-1277` | WindowingStrategy、accumulation、lateness、time domain |
| `C:/can/sources/beam/model/fn-execution/src/main/proto/org/apache/beam/model/fn_execution/v1/beam_fn_api.proto:118-160` | Fn control instruction lifecycle |
| `C:/can/sources/beam/model/fn-execution/src/main/proto/org/apache/beam/model/fn_execution/v1/beam_fn_api.proto:287-318` | ProcessBundleDescriptor state/timer/data endpoints |
| `C:/can/sources/beam/model/fn-execution/src/main/proto/org/apache/beam/model/fn_execution/v1/beam_fn_api.proto:429-447` | ProcessBundleResponse finalization |
| `C:/can/sources/beam/model/fn-execution/src/main/proto/org/apache/beam/model/fn_execution/v1/beam_fn_api.proto:541-705` | ProcessBundleSplit primary/residual semantics |
| `C:/can/sources/beam/model/fn-execution/src/main/proto/org/apache/beam/model/fn_execution/v1/beam_fn_api.proto:708-716` | FinalizeBundle request/response |

Hazelcast Jet：

| 路径 | 内容 |
|---|---|
| `C:/can/sources/hazelcast/hazelcast/src/main/java/com/hazelcast/jet/config/ProcessingGuarantee.java:19-37` | barrier snapshot、at-least-once 与 exactly-once 对齐差异 |
| `C:/can/sources/hazelcast/hazelcast/src/main/java/com/hazelcast/jet/config/ProcessingGuarantee.java:55-77` | NONE、AT_LEAST_ONCE、EXACTLY_ONCE 语义 |
| `C:/can/sources/hazelcast/hazelcast/src/main/java/com/hazelcast/jet/core/Processor.java:103-145` | cooperative processor contract |
| `C:/can/sources/hazelcast/hazelcast/src/main/java/com/hazelcast/jet/core/Processor.java:363-476` | save snapshot、prepare commit、finish commit lifecycle |
| `C:/can/sources/hazelcast/hazelcast/src/main/java/com/hazelcast/jet/core/Processor.java:480-528` | restore from snapshot lifecycle |
| `C:/can/sources/hazelcast/hazelcast/src/main/java/com/hazelcast/jet/impl/execution/ProcessorTasklet.java:150-154` | exactly-once waits for all barriers |
| `C:/can/sources/hazelcast/hazelcast/src/main/java/com/hazelcast/jet/impl/execution/SnapshotContext.java:66-94` | snapshot phase 1/phase 2 counters |
| `C:/can/sources/hazelcast/hazelcast/src/main/java/com/hazelcast/jet/config/EdgeConfig.java:56-153` | queue capacity、receive window、packet size |
| `C:/can/sources/hazelcast/hazelcast/src/main/java/com/hazelcast/jet/impl/execution/init/ExecutionPlan.java:185-339` | member-side tasklet、conveyor、sender/receiver topology |
| `C:/can/sources/hazelcast/hazelcast/src/main/java/com/hazelcast/jet/impl/MasterSnapshotContext.java:231-552` | master snapshot phase、validation record、phase 2 decision |
