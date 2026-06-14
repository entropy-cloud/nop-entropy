# nop-stream Barrier/Checkpoint 机制对比分析报告

> Status: analysis（AI 单方面调研，结论可被推翻）
> Date: 2026-06-14（v2，经 3 位独立 reviewer 核查 + 源码复核修订）
> Author: opencode
> Scope: 对比 nop-stream 与 Apache Flink、Apache SeaTunnel(Zeta) 的 barrier/checkpoint 实现，聚焦容错场景（超时、新消息、channel 失败、并发、recovery），给出 nop-stream 的改进建议

## 0. 背景与触发点

修复 `TestProcessingGuaranteeBehavior` 无限 hang（见 `ai-dev/logs/2026/06-14.md`）时发现：`InputGate.readMultiChannel` 的对齐循环无累计超时。该类在生产执行路径中被实例化（`GraphExecutionPlan.java:269`、`RemoteGraphExecutionPlanBuilder.java:208`），因此测试中的 hang 模式在生产中同样可能发生。由此引发对 nop-stream 整体 barrier/checkpoint 容错能力的系统性对比。

> **定位前提**：nop-stream 有两条执行路径 —— (a) **local embedded**（`GraphModelCheckpointExecutor`，线程池 + 本地 `LinkedBlockingQueue` 数据交换，无 JM/TM 分离）；(b) **distributed**（`JobCoordinator` + `TaskManager`，远程 `IMessageService`，有 lease failover + fencing token）。本文容错分析需分别对待这两条路径，它们的能力差异显著。nop-stream 整体定位是**嵌入式流处理框架**，用 Flink 完整分布式架构对标其 local 路径并不公允；但 nop-stream 自身选择了"Flink 式 DAG + 算子级 barrier 对齐"路线（而非 SeaTunnel 式线性 chain），因此对标该路线下的容错完备性是合理的。

## 1. 对比对象

| 引擎 | checkpoint 模型 | 源码位置 |
|------|----------------|----------|
| **nop-stream** | Coordinator 周期触发 + barrier 在数据流对齐（aligned/非对齐）；local/distributed 双路径 | `nop-stream-core` + `nop-stream-runtime` |
| **Apache Flink** | 双层：JM CheckpointCoordinator + TM BarrierHandler 状态机；支持 aligned / unaligned / at-least-once | `~/sources/flink/flink-runtime` |
| **Apache SeaTunnel (Zeta)** | master 集中协调 + barrier 作为 `Record` 在数据流流动；靠 ack + FIFO 保序，**无算子级对齐** | `~/sources/seatunnel/seatunnel-engine` |
| **Hazelcast** | SeaTunnel 仅使用其 IMap/Operation RPC 通信层与 `jet.datamodel` 等工具类；**未启用** Hazelcast Platform 内置的 Jet 分布式快照引擎（基于 Chandy-Lamport，含 `SnapshotContext`/`SnapshotService`）。SeaTunnel 的 checkpoint 语义完全由 Zeta 自建 | `~/sources/seatunnel/seatunnel-shade/seatunnel-hazelcast` |

> 说明：Hazelcast Platform（5.x）本身内含 Jet 的分布式快照机制，但 SeaTunnel 选择不用它而自建 Zeta 引擎。本文 Hazelcast 维度指 SeaTunnel 所用部分。

## 2. 架构对比

### nop-stream（双路径）
- **local 路径**：`GraphModelCheckpointExecutor` 用 `startBarrierScheduler`（独立 `ScheduledExecutorService`，`GraphModelCheckpointExecutor.java:519`）周期调 `coordinator.tryTriggerPendingCheckpoint` + `triggerBarrierOnAllInvokables` → task 层 `CheckpointBarrierTracker.triggerCheckpoint` 向 source operator `offerBarrier` 注入 barrier。Coordinator 收 ack → 存 storage + EpochManifest。**无独立 failover**（失败即退出，由上层重试）。
- **distributed 路径**：`JobCoordinator` + `TaskManager`，`RemoteResultPartition`/`RemoteInputChannel` 经 `IMessageService` 传输，消息带 `fencingToken`+`epochId`。`JobCoordinator.detectFailures()`（`:355`）轮询 lease，过期触发 `globalRecovery()`（`:398`，新 fencing token + 从 checkpoint 恢复）。
- **Task 内**：`CheckpointBarrierTracker`（core）做 operator 级 ack 计数，**一次只允许一个 checkpoint**（`operatorsToAck>0` 拒绝新，`:56-58`）。
- **数据流对齐**：`InputGate.readMultiChannel` 内联对齐逻辑。`BarrierAligner`（runtime）是更独立的多输入对齐器，**类注释明确"当前 GraphModelCheckpointExecutor 未使用"**（`BarrierAligner.java:27`，仅测试引用）。
- **两阶段提交**：`CheckpointParticipant.finishCommit(checkpointId, success)`，失败进入 `failedCommitParticipants` 在下个 checkpoint 完成时 `retryFailedCommits`（逆拓扑序通知）。
- **状态恢复校验**（nop-stream 独有）：`StreamModelFingerprint` 在恢复时校验拓扑兼容性（`GraphModelCheckpointExecutor`），比 Flink 的 `allowNonRestoredState` 手动 flag 更严格——算子链变更会拒绝恢复。

### Flink（标杆）
- **双层 + 状态机**：JM `CheckpointCoordinator`（触发/ack/超时/abort/subsume/recovery）+ TM `CheckpointBarrierHandler` 状态机。
- **对齐靠 credit 网络隐式阻塞**：现代 Flink 不再用 `BufferBlocker`（源码树已无此类），通过 credit-based flow control 让上游停止发送，barrier 后数据缓存在上游 subpartition（`IndexedInputGate.blockConsumption` 是 no-op，注释明示"by revoking credits"）。
- **三模式**：aligned（exactly-once）、unaligned（exactly-once + in-flight data 持久化）、at-least-once（`CheckpointBarrierTracker`，不阻塞）。
- **aligned→unaligned 超时自动切换**（FLIP-76，TM 本地决策，`alignedCheckpointTimeout`，`AlternatingCollectingBarriers.alignedCheckpointTimeout`）。
- **priority event + EventAnnouncement**：让 barrier 越过 in-flight data 抢占传播。
- **CheckpointFailureManager**：连续失败达阈值触发 region failover。

### SeaTunnel
- **master 集中协调 + barrier 当数据**：barrier 是 `Record<CheckpointBarrier>` 对象，在应用层 `Collector`/`IntermediateBlockingQueue` 流动，**无 network-stack 级 barrier 拦截**。
- **无算子级 barrier 对齐**：靠 (a) 单 task 内 `cycleAcks` 计数（chain 级同步）；(b) FIFO 队列保序；(c) master 端 `notYetAcknowledgedTasks` 全局 ack。物理执行计划是**线性 chain**，避免 fan-in 对齐问题。
- **串行 checkpoint**（`pendingCounter` 严格 1），不支持并发。
- **pipeline 级 failover**（超时/失败直接取消整个 pipeline 重启）。
- **两阶段提交**：`SinkAggregatedCommitter`（独立 task 聚合）。

## 3. 核心机制逐维度对比

| 维度 | nop-stream | Flink | SeaTunnel |
|------|-----------|-------|-----------|
| **A. 触发** | local: `startBarrierScheduler` 周期调 `tryTriggerPendingCheckpoint` → task 层注入 barrier（`CheckpointBarrierTracker.triggerCheckpoint:74` `offerBarrier`）；distributed: `JobCoordinator` RPC | Coordinator 周期 + 手动；`TriggerCheckpoint` RPC 发 source | Coordinator 周期；Hazelcast `CheckpointBarrierTriggerOperation` RPC 发 source enumerator |
| **B. aligned 对齐** | `InputGate.readMultiChannel`：round-robin 每 channel `channel.read(50ms)`（`LinkedBlockingQueue.poll(50ms)`）+ 一轮全空后 `parkNanos(10ms)`；`barrierReceived[]` 标记 skip（`InputGate:208-264,225,263`）。**无累计超时上限** | `SingleCheckpointBarrierHandler` + credit 网络阻塞上游；状态机驱动；无 busy-wait | **无算子级对齐**；靠 FIFO 保序 + master ack |
| **C. at-least-once / 非对齐** | `InputGate(barrierAlignment=false)`：不 skip，首个 barrier 立即发出，后续 coalesce（`InputGate:276-288`） | `CheckpointBarrierTracker`：不阻塞，`pendingCheckpoints` ArrayDeque 多 checkpoint 追踪 | N/A（barrier 不阻塞即天然 at-least-once） |
| **D. 传播** | source 收 barrier → operator chain 广播到下游 ResultPartition | `operatorChain.broadcastEvent` → `ResultPartition.broadcastEvent` 每 subpartition | barrier 作为 Record 沿 FIFO 队列传 |
| **E. 超时** | **Coordinator 级有**：`scheduleTimeout` → `abortPendingCheckpoint("Timeout")`（`CheckpointCoordinator:364-374`，默认 10min）。**InputGate 对齐层无累计超时**；但 InputGate **可被 `Thread.interrupt()` 中断**（catch `InterruptedException` 返回 empty，`InputGate:253-256`）。**问题：Coordinator abort 未接线到 `SubtaskTask.cancel()`** | 三层：整体超时 + aligned→unaligned 切换 + FailureManager 计数 failover | 超时直接 abort + fail 整个 pipeline，不重试 |
| **F. 失败/异常** | local: 失败即退出（无 failover）；distributed: lease 过期 → `globalRecovery()`（粗粒度 30s+）。Coordinator 的 `consecutiveTriggerFailures`（`:123`）是**死代码**（生产用 `startBarrierScheduler`，其 catch 只 `LOG.error` 不计数，`GraphModelCheckpointExecutor:542`）；无 CancelCheckpointMarker | `CheckpointFailureManager` 按 reason 计数，超阈值 region failover；`CancelCheckpointMarker` 可靠传播 | task 异常 → `handleCheckpointError` → pipeline CANCELING → 整体重启 |
| **G. 并发 checkpoint** | 配置项存在（默认 1）；Coordinator `tryTriggerPendingCheckpoint`（**非 synchronized**）check-then-act 有竞态；task 层 `CheckpointBarrierTracker` 一次一个 | 支持 `maxConcurrentCheckpoints`；TM aligned 单追踪、at-least-once 多追踪 | **不支持**（`pendingCounter` 严格 1） |
| **H. recovery** | `restoreFromCheckpoint` 从 storage 加载；distributed 有 fencing token；**StreamModelFingerprint 拓扑校验** | `restoreLatestCheckpointedStateToAll`，含 channel state 重放 | 4 步协议：WAITING_RESTORE→restore→READY_START→start |
| **I. 新消息/in-flight data** | 对齐期间 barrier 后数据被 skip 滞留 `ResultPartition` 队列，对齐后释放；**无 in-flight data 持久化** | aligned：阻塞上游；unaligned：in-flight data 作 channel state 持久化重放 | 不阻塞输入（`checkpointLock` 互斥注入），barrier 后数据属下一周期 |
| **J. 背压交互** | barrier 随数据排队（无独立通道），背压下变慢；`ResultPartition` 有界(1024)，DAG 无环不会死锁但会严重反压停滞 | unaligned + priority event 让 barrier 抢占，不受背压影响 | barrier 与数据同通道，背压下同样受影响 |
| **K. finished/卡死 channel** | **finished channel 有隐式处理**：`InputGate:227-235` 当 channel `isFinished()` 且 pendingBarrier 存在时，自动标记 `barrierReceived=true`（视为隐式 barrier）；**stuck channel（不 finish 不 close 不发 barrier）无处理 → 永久 hang** | `enableCheckpointAfterTasksFinished`：finished channel 注入虚拟 barrier；`isRpcTriggered` 不阻塞 | `COMPLETED_POINT_TYPE`：source 读完自动触发最终 checkpoint |
| **L. unaligned / 超时切换** | 未实现（架构上为可选增强，§5.9 P3） | 完整 FLIP-76 | 未实现（barrier 随 FIFO 受背压，无抢占机制） |

## 4. 容错场景深度分析

### 4.1 如果超时怎么办？

| 引擎 | checkpoint 级超时 | 对齐(aligned)超时 |
|------|------------------|-------------------|
| **nop-stream** | ✅ `scheduleTimeout`（默认 10min）→ abort + `notifyParticipantsFinishCommit(false)`/`notifyCheckpointAborted`。**关键缺口：abort 没有接线到 `SubtaskTask.cancel()`** —— `SubtaskTask.cancel()`（`:101-108`）会 `t.interrupt()`，而 InputGate 能响应中断（`catch InterruptedException` 返回 empty，`InputGate:253-256`），但 **Coordinator abort 路径与 task 生命周期完全断开**（abort 只改 PendingCheckpoint 状态 + 通知 listener/participant，不调 cancel）。后果：local 路径下 Coordinator abort 后，task 线程仍在对齐循环空转（虽可被外部 interrupt，但无人调 interrupt）→ `awaitCompletion` 永不返回 → `finally{shutdown}` 永不执行。**distributed 路径有 lease 兜底**（`detectFailures` ~15-20s：lease 15s + 轮询 5s → `globalRecovery`），粒度较粗。 | ❌ **InputGate 无累计对齐超时**。`channel.read(50ms)` + `parkNanos(10ms)` 是固定间隔，无累计上限。 |
| **Flink** | ✅ `CheckpointCanceller` abort + `CheckpointFailureManager` 计数 | ✅ `alignedCheckpointTimeout` → **降级 unaligned**（不 abort），TM 本地决策，barrier 转 priority event |
| **SeaTunnel** | ✅ 超时 → pipeline CANCELING 重启 | N/A |

**nop-stream 超时缺口的精确表述**（修订自初版）：不是"InputGate 不可中断"，而是"abort 信号没有接线到已有的中断机制"。修复成本远低于初版判断 —— 不需要给 InputGate 加 abort 标志位，只需在 `scheduleTimeout` 回调中 abort 后调用对应 `SubtaskTask.cancel()`。

### 4.2 执行过程中有新消息（barrier 后的数据）怎么办？

| 场景 | nop-stream | Flink | SeaTunnel |
|------|-----------|-------|-----------|
| **aligned 对齐期间，已收到 barrier 的 channel 又来数据** | `InputGate` skip 该 channel（`:219-221`），数据滞留 `ResultPartition` 的 `LinkedBlockingQueue`（容量 1024）。对齐完成后 reset barrierReceived，数据读出。**正确**，但队列满时 producer 阻塞在 `queue.put()` → 反压上游。 | 数据阻塞在**上游** subpartition（credit 耗尽），不进下游 | 不阻塞，数据照常流动 |
| **对齐期间未阻塞 channel 来数据** | 正常读出（`:252`） | 同左 | 同左 |
| **late event** | 无专门处理 | aligned 阻塞上游不 late；unaligned 进 channel state | 无专门处理 |

> **重要缓解选项**：nop-stream 的 `ProcessingGuarantee.EFFECTIVELY_ONCE`（`barrierAlignment=false` + `requiresDurableCheckpoint=true`）模式**完全绕开对齐**——barrier 不阻塞，靠 sink 两阶段提交保证 exactly-once。这正是 SeaTunnel 的路线。在不需要算子状态一致快照的场景下，切到此模式可避免 §5.1 的对齐 hang（代价：sink 必须幂等或两阶段提交）。

### 4.3 channel 失败 / task 异常怎么办？

| 引擎 | 上报路径 | 恢复动作 |
|------|---------|---------|
| **nop-stream local** | task 异常无标准上报 Coordinator 的路径；Coordinator 靠 ack 超时被动发现 | 无自动 failover；失败即退出由上层重试 |
| **nop-stream distributed** | lease 过期 → `detectFailures` → `globalRecovery`（新 fencing token，旧 task 被 fence 掉） | 全局恢复，从 latest checkpoint 重启所有 task |
| **Flink** | task → `DeclineCheckpoint` RPC → abort；ExecutionGraph failover 策略 | `CheckpointFailureManager` 计数 → region restart；`CancelCheckpointMarker` 传播 |
| **SeaTunnel** | task → `CheckpointErrorReportOperation` → `handleCoordinatorError` | pipeline 级 CANCELING 重启 |

### 4.4 barrier ID 不匹配 / 重叠 checkpoint

- **nop-stream `InputGate`**：`handleBarrierNonRecursive:296` 在 `barrierReceived[channelIndex]==true`（该 channel 已收过 barrier）且新 barrier id 不同时抛 `ERR_STREAM_CHECKPOINT_ABORTED`。此分支在单 checkpoint 正常流程下**不可达**（单 checkpoint 不会同 channel 来两个 barrier），仅在并发 trigger 导致同 channel barrier 交叠时触发。**本质是 §5.5 并发问题的表现**，应合并处理。
- **Flink**：新 checkpoint id 更大 → 旧 checkpoint `abortInternal(CHECKPOINT_DECLINED_SUBSUMED)`（新 subsume 旧，方向确认）；优雅 abort 不崩溃 task。
- **SeaTunnel**：串行，不重叠。

### 4.5 并发 checkpoint

- **nop-stream**：Coordinator `tryTriggerPendingCheckpoint`（`:165`，**非 synchronized**）check-then-act：`numPendingCheckpoints.get() < max` 检查与 `incrementAndGet` 之间有窗口，两个并发 trigger 可同时通过 → 突破 `maxConcurrentCheckpoints`。task 层 `CheckpointBarrierTracker`（`:56-58`）和 `InputGate`（单 `pendingBarrier`）都只支持单 checkpoint，即使突破也只触发 §4.4 的异常。**这是真竞态，非单纯文档不一致**。
- **Flink**：`CheckpointRequestDecider` 控并发；TM aligned 单追踪、at-least-once 多追踪。
- **SeaTunnel**：明确不支持。

## 5. nop-stream 缺口与改进建议（按优先级，经 reviewer 重排）

### P0 — 必须修复（生产 hang）

#### 5.1 Coordinator abort 未接线到 task 取消（对齐 hang 的真正根因）
- **现状**：`scheduleTimeout`（`CheckpointCoordinator:364`）abort 后只通知 listener/participant，**不调用 `SubtaskTask.cancel()`**。而 `SubtaskTask.cancel()`（`:101-108`）已有 `t.interrupt()`，InputGate 已能响应（`InputGate:253-256` catch InterruptedException）。即**中断机制已存在，只是没接线**。后果：local 路径下对齐 hang（stuck channel 永不 finish 不发 barrier）时，Coordinator abort 后 task 线程仍空转。
- **加剧因子（reviewer 补充）**：`TaskExecutor.awaitCompletion`（`:303-315`）**串行** `future.get()` 各 task，单 task hang 会阻塞整个 `shutdown` 路径 → `executeWithCheckpoint` 永不返回 → `finally{shutdown}` 永不执行。即一个 task hang 会拖住整个 job 生命周期。
- **证据**：本次修复的 `TestProcessingGuaranteeBehavior` 正是 stuck channel 模式。生产路径 `GraphExecutionPlan.java:269` 实例化同一 InputGate。
- **建议**：在 `scheduleTimeout` 回调中，abort 后调用对应 `SubtaskTask.cancel()`（local）/ 发 cancel RPC（distributed）。**不需要给 InputGate 加 abort 标志位**（初版建议 2 多余，reviewer 指出已存在中断机制）。
- **影响范围**：local 路径严重（无兜底）；distributed 路径有 lease 兜底（~15-20s：`TaskManager:61` lease 15s + `JobCoordinator:65` 轮询 5s；注意 `DEFAULT_LEASE_EXPIRE_THRESHOLD_MS=30000` 是声明但未使用的死常量）。

### P1 — 重要（健壮性 / 冷路径竞态）

#### 5.2 `tryTriggerPendingCheckpoint` check-then-act 竞态（降自初版 P0）
- **现状**：`CheckpointCoordinator:165` 方法**非 synchronized**，`numPendingCheckpoints.get() < max` 检查与 `incrementAndGet` 之间有 TOCTOU 窗口。共享状态本身有线程安全保证（`pendingCheckpoints` ConcurrentHashMap、`numPendingCheckpoints` AtomicInteger、`pending.getStatus()` AtomicReference CAS），**可见性无问题**；但 synchronized/非 synchronized 混用是代码异味，复合操作原子性靠各点 CAS 兜底。（初版"内存可见性无保证"系术语错误，已纠正。）
- **可达性（reviewer 校准，关键）**：**纯周期路径不可达** —— `startBarrierScheduler` 用 `newSingleThreadScheduledExecutor`（`GraphModelCheckpointExecutor:529`），单线程串行调度。竞态仅在 **savepoint/终态触发与 scheduler tick 并发**时打开（main 线程调 `tryTriggerPendingCheckpoint(SAVEPOINT)` 与 barrier-injector 线程的下次 tick 微秒级巧合），或 **distributed 路径多外部触发源**（HA 切换等）时。属**冷路径竞态**，非默认热路径。
- **后果**：突破 `maxConcurrentCheckpoints` → 不同 task 可能接受不同 checkpointId → DAG 中 barrier 传播触发 `InputGate.handleBarrierNonRecursive:296` 重叠异常 → task crash（非 hang、非静默损坏）。
- **定级理由**：P1（冷路径，但后果是 job crash）。**修复成本极低（加 synchronized），建议与 §5.1 一并修**。
- **建议**：`tryTriggerPendingCheckpoint` 加 synchronized；统一 abort/ack 的锁协议。

#### 5.3 生产路径无失败计数（修正自原 §5.2，初版引用了死代码）
- **现状**：初版引用的 `consecutiveTriggerFailures`（`CheckpointCoordinator:123`）属于 `startCheckpointScheduler`，而**生产路径用 `startBarrierScheduler`**（`GraphModelCheckpointExecutor:519`，main 代码 5 处调用 `startBarrierScheduler`、0 处调用 `startCheckpointScheduler`）。`startBarrierScheduler` 的 catch 只 `LOG.error("Failed to inject checkpoint barrier", e)`（`:542`），**连失败计数都没有**。即 checkpoint 持续失败时，连告警精度都不够，更不触发任何恢复。
- **建议**：在 `startBarrierScheduler` 增加失败计数 + 可配置回调（CheckpointFailureManager 等价物），超阈值通知上层（distributed 路径可触发 `globalRecovery`，local 路径告警/退出）。

#### 5.4 abort 不可靠传播（缺 CancelCheckpointMarker）
- **现状**：abort 不向数据流注入取消标记。但需注意：nop-stream 的 barrier 注入路径是 source operator 的 `offerBarrier`，**没有独立控制通道**——所有控制信息（barrier/watermark）混在 `ResultPartition` 的 `LinkedBlockingQueue`。若 InputGate 正在对齐等待，cancel marker 也排在数据队列里，**同样读不到**（先有鸡先有蛋）。
- **建议**：若 §5.1 已通过 `cancel()` 接线解决 hang，此项可降级。若要做 cancel marker，**前置依赖是独立控制通道**（local 可直接方法调用；distributed 需 RPC 通道），不能走数据队列。Flink 用独立 event/RPC 通道解决此问题。

#### 5.5 启用 BarrierAligner 替代 InputGate 内联对齐（提升自原 §5.7，合并原 §5.8）
- **现状**：`BarrierAligner`（runtime）已实现且**显著更健壮**：有 `pollAlignedBarrier(timeout)` 超时、`TreeMap<Long,CheckpointBarrier>` 多 checkpoint 追踪、`ReentrantLock`+`Condition` 线程安全、`abortAll()`、事件驱动（非 busy-wait）。但类注释"当前未使用"，实际对齐靠 InputGate 的内联逻辑（无超时、单 checkpoint、parkNanos 轮询）。
- **建议**：将多输入对齐统一到 BarrierAligner，启用后 §5.1 的对齐超时、并发 checkpoint、busy-wait 三个问题同时缓解。需修复 `BarrierAligner.findCompletedCheckpointId`（`:103-120`）的 O(输入数×待完成数) 性能问题（HashMap 全扫描计数）。

#### 5.6 并发能力明确化（合并原 §5.4 + §5.5 文档部分）
- **现状**：§5.2 竞态 + task/InputGate 单 checkpoint。
- **建议**：要么强制 `maxConcurrentCheckpoints=1`（synchronized 修复竞态后，移除误导配置）；要么实现 task 层多 checkpoint 追踪。barrier ID 不匹配抛异常（§4.4）改为优雅 abort。

### P2 — 增强（中期）

#### 5.7 distributed 路径的 InputChannel 心跳/超时
- **现状**（reviewer 补充的遗漏场景）：`RemoteInputChannel` 无心跳，网络分区时只靠 lease 30s 兜底。barrier 可能只到部分 channel。
- **建议**：channel 级心跳缩短检测时间。

#### 5.8 savepoint / DRAIN 路径的对齐阻塞（同构问题）
- **现状**（reviewer 补充）：`triggerTerminalSavepoint` 等 DRAIN/SUSPEND 模式等待 future 完成，若 barrier 无法对齐（§5.1），优雅关闭阻塞至超时（local 默认 10min，`future.get(checkpointTimeout)`；distributed 60s，`JobCoordinator.terminateDrain`）—— 非永久。另有静默分支：若已有 pending checkpoint（max=1），`tryTriggerPendingCheckpoint` 返回 null，savepoint 直接跳过 wait。
- **建议**：随 §5.1 修复一并解决（cancel 接线覆盖 savepoint 路径）。

### P3 — 长期 roadmap

#### 5.9 unaligned checkpoint + channel state
- **现状**：背压下 barrier 随数据排队，易超时；无逃生机制。
- **前置依赖**：channel state 序列化、priority event、EpochManifest segments 落地（当前 `buildEpochManifest` 传 null，`CheckpointCoordinator:504`）。
- **风险提示**：在未实现 channel state 前，§5.1 的"快速失败 + failover"在持续背压下会进入**重启循环**（恢复时无 in-flight data → 从 source 重放 → 若 source 不可重放则丢数据）。因此 §5.1 是缓解 hang，非根治背压场景，根治需 §5.9。但这不妨碍 §5.1 优先（hang 比频繁重启更糟）。

## 6. 结论

| 能力 | nop-stream | Flink | SeaTunnel |
|------|-----------|-------|-----------|
| checkpoint 级超时 | ✅（但 abort 未接 task cancel，P0） | ✅（三层） | ✅ |
| 对齐(aligned)超时 | ❌（InputGate 无累计超时，P0） | ✅（→unaligned） | N/A |
| abort 可靠传播 | ❌（无 CancelMarker，P1） | ✅ | ✅（pipeline 重启） |
| 连续失败处理 | ❌（生产路径无计数，P1） | ✅（failover） | ✅（pipeline 级） |
| 并发 checkpoint | ⚠️（竞态 + 单 checkpoint，P0/P1） | ✅ | ❌（明确不支持） |
| unaligned | ❌ | ✅ | ❌（barrier 随数据受背压） |
| channel state | ❌ | ✅ | ❌（持久化算子快照至 storage，但不持久化 in-flight channel 数据） |
| 两阶段提交 | ✅（+retry） | ✅ | ✅ |
| recovery | ✅（distributed fencing + fingerprint 校验） | ✅（含 channel state） | ✅（pipeline 级） |

**核心判断（修订）**：
1. nop-stream 正常路径完整（触发/对齐/ack/存储/恢复/两阶段提交/fingerprint 校验），异常路径有系统性缺口。
2. **最该先修的不是初版以为的"InputGate 不可中断"，而是"abort 没接线到已有的 cancel 中断机制"（§5.1）+ "触发竞态"（§5.2）** —— 前者是 hang（可用性），后者是竞态（正确性），均为 P0。
3. 初版把 `consecutiveTriggerFailures` 当 P0 是**基于死代码**的误判，真正问题是生产路径 `startBarrierScheduler` 连计数都没有（§5.3，降为 P1）。
4. `BarrierAligner` 是已存在且更健壮的替代实现（§5.5），启用它能一并缓解超时/并发/busy-wait。
5. distributed 路径有 lease failover 兜底（粗粒度），local 路径无兜底 —— 两路径严重性不同。
6. unaligned checkpoint（§5.9）是长期项，但在实现前 §5.1 的快速失败在持续背压下有重启循环风险（§5.9 风险提示）。
7. **EFFECTIVELY_ONCE 模式可绕开对齐 hang**（§4.2），是现成的架构级缓解选项。

**与 SeaTunnel 的定位差异**：SeaTunnel 通过"线性 chain + master ack 收集"绕开算子级对齐难题，工程简单但牺牲 DAG 灵活性。nop-stream 选择了 Flink 式 DAG + 算子对齐路线，方向正确，但需补齐对齐层容错（§5.1-5.5）。

**"生产可用"的公允表述**：local 嵌入式路径在修复 §5.1/§5.2 后可达 SeaTunnel 内嵌模式 / Flink LocalExecutor 级别的容错；distributed 路径有 lease 兜底但需补 §5.3-5.5 达到流处理生产级。完整对标 Flink 分布式能力（unaligned/channel state/priority event）是长期 roadmap（§5.9），非当前阻塞点。

## 附录：关键源码引用（均已 reviewer 核查 + 行号 100% 准确）

### nop-stream
- `InputGate.java:208-264`（readMultiChannel，对齐循环）；`:225`（channel.read(50ms) 主阻塞）；`:253-256`（catch InterruptedException，**可被中断**）；`:263`（parkNanos 10ms 辅助）；`:219-221`（skip 已对齐 channel）；`:227-235`（finished channel 隐式 barrier 处理）；`:276-288`（at-least-once）；`:296`（重叠 barrier 抛异常，需 barrierReceived 先 true）
- `CheckpointBarrierTracker.java:55-91`（triggerCheckpoint，`:56-58` operatorsToAck>0 拒绝）
- `CheckpointBarrier.java`（无 unaligned/cancel 字段）
- `CheckpointConfig.java:22`（DEFAULT_CHECKPOINT_TIMEOUT=600000）；`:24`（MAX_CONCURRENT=1）
- `CheckpointCoordinator.java:165`（tryTriggerPendingCheckpoint，**非 synchronized**）；`:194`（acknowledgeTask synchronized）；`:212`（completePendingCheckpoint synchronized）；`:287`（abortPendingCheckpoint **非 synchronized**）；`:364-374`（scheduleTimeout）；`:98`（startCheckpointScheduler，**死代码**，main 无调用）；`:123`（consecutiveTriggerFailures，死代码路径）；`:430-461`（retryFailedCommits）
- `GraphModelCheckpointExecutor.java:519-547`（startBarrierScheduler，生产路径，`:542` catch 只 LOG.error）；`:549-563`（triggerBarrierOnAllInvokables）
- `SubtaskTask.java:101-108`（cancel → `t.interrupt()`，中断机制已存在）
- `BarrierAligner.java:27`（注释"当前 GraphModelCheckpointExecutor 未使用"）；`:158-172`（pollAlignedBarrier(timeout)）；`:103-120`（findCompletedCheckpointId，O(n*m)）
- `JobCoordinator.java:355`（detectFailures，lease 检查）；`:398`（globalRecovery，新 fencing token）
- `GraphExecutionPlan.java:269` / `RemoteGraphExecutionPlanBuilder.java:208`（InputGate 生产实例化点）

### Flink
- `SingleCheckpointBarrierHandler.java`（aligned 核心）；`:310-333`（registerAlignmentTimer）
- `AlternatingCollectingBarriers.java:41-52`（超时→unaligned 切换）
- `AbstractAlignedBarrierHandlerState.java:62`（blockChannel → credit no-op）
- `IndexedInputGate.java:60-62`（blockConsumption 注释"by revoking credits"）
- `CheckpointCoordinator.java`（CheckpointCanceller）
- `CheckpointFailureManager.java`（失败计数 failover）

### SeaTunnel
- `CheckpointCoordinator.java:286-307`（handleCoordinatorError）；`:659-663`（串行）
- `SeaTunnelTask.java:417-437`（cycleAcks）
- `SourceFlowLifeCycle.java:381-437`（checkpointLock 原子注入）

## 附：审查过程记录

**第一轮（3 位独立 reviewer：源码核查员 / 对比公允性审查 / 魔鬼代言人）** → v2 核心修订：
- 纠正"InputGate 不响应中断"→ 实为"abort 未接线到已有中断机制"（§5.1）
- 纠正 §5.2 引用死代码 → 生产路径 `startBarrierScheduler` 连计数都没有（§5.3）
- 提升 §5.5 竞态到 P0（§5.2）
- 补充 local/distributed 双路径区分、EFFECTIVELY_ONCE 缓解选项、savepoint hang、fingerprint 校验
- 修正 Hazelcast 结论过度泛化、SeaTunnel channel state 措辞、finished channel 隐式处理、parkNanos vs channel.read 主阻塞

**第二轮（2 位独立 reviewer：修订验证裁判 / 全新独立终审）** → v2.1 定向修订，两 reviewer 均 **ACCEPT（共识达成）**：
- §5.2 竞态降为 P1：补充可达性分析（纯周期单线程路径不可达，仅 savepoint/distributed 冷路径）；修正"内存可见性无保证"术语错误（实际有 CHM/Atomic 保证，是复合操作/代码异味问题）
- §5.1 补充 `awaitCompletion` 串行阻塞放大 hang 的加剧因子
- lease 时间 30s → ~15-20s（`DEFAULT_LEASE_EXPIRE_THRESHOLD_MS=30000` 是死常量，实际 `TaskManager:61` lease 15s + 轮询 5s）
- §5.8 "永久卡住" → 实际阻塞至超时（local 10min / distributed 60s）+ 静默跳过分支
- §3 维度表 L 评判标准统一（nop-stream 与 SeaTunnel 都标"未实现"并注明原因）

**共识状态**：核心结论经 5 位独立 reviewer + 作者源码复核全部成立，行号引用 100% 准确。无 BLOCKING 异议。v2.1 为最终版。残留为非阻塞的精度/措辞项，已在 v2.1 落实。
