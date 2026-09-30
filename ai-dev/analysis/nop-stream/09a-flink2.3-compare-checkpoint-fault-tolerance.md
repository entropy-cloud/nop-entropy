# nop-stream vs Flink 2.3.0 — Checkpoint 与容错子系统架构对比

> Status: open
> Date: 2026-09-30
> Scope: `nop-stream/nop-stream-runtime/.../checkpoint/`（CheckpointCoordinator、PendingCheckpoint、RetentionCleaner、storage/）、`nop-stream/nop-stream-core/.../checkpoint/`（CheckpointBarrier、TaskEpochSnapshot、ChannelState、CompletedCheckpoint、EpochManifest）、`.../execution/{CheckpointBarrierTracker,InputGate,GraphModelCheckpointExecutor}`、`.../coordinator/JobCoordinator`（恢复/终止面）；对照 Flink `release-2.3.0`（`~/sources/flink`，commit `c0f8d1a1e09`）的 `flink-runtime` `org.apache.flink.runtime.checkpoint.*`、`runtime/checkpoint/channel/*`、`org.apache.flink.streaming.runtime.io.checkpointing.*`（2.x 起并入 flink-runtime）
> Conclusion: epoch 中心化协议骨架与 Flink 的 coordinator 语义**结构性趋同**（两侧在 task 侧都是"单对齐 + ACK 流水线"，coordinator 侧都是多 pending），nop 的 aligned→unaligned 运行时切换在消费端阻塞模型下与 Flink 的 FORCED_ALIGNED→UNALIGNED 翻转语义等价且实现更简单。真正的差距集中在三个**生命周期端点**：savepoint 无对齐豁免（C1）、终止/保存点触发无队列化（C2）、任务完成后 ACK 集合不收缩（C3）——三者都是 Flink 早已用标准机制（`CheckpointOptions.forConfig`、`CheckpointRequestDecider`、`DefaultCheckpointPlanCalculator`）解决的标准场景。定位内质量评分 **4/5**。

## Context

- 本系列 03 号文档（`03-checkpoint-comparison.md`）对照的是 Flink 1.20 时代布局且早于 Stage 43/45/47 多轮演进。本文按 Flink 2.3.0（2.x 布局）重做 checkpoint 与容错维度的结构性对比，并纳入 nop-stream R4 轮 30+ 缺陷修复后的 HEAD 现状。
- 校准基线：`ai-dev/design/nop-stream/00-vision.md`（嵌入式/中小规模、最小控制面、single-in-flight 为有记录决策 + §六裁决"unaligned 不构成协议变更"、Non-Goal：不复制 Flink Runtime 结构）；`checkpoint-design.md`（§2 epoch 协议、§2.11 unaligned、§8 恢复、§13 容错契约）与 `failover-design.md`（Stage 27 NO-GO → Stage 44 go + 5 successor 已交付）。
- 已确认缺陷不重复立项，只引现状：R4（`ai-dev/audits/2026-09/2026-09-30-0530-deep-audit-nop-stream-quality-r4/`，125 项）中与本子系统直接相关的 ST-01（PendingCheckpoint abort future）、REG-01（no-mat 内部边丢失窗口 fail-fast）、CC-01/02/03（恢复并发/回放缝隙/孤儿任务）、AR-01（keyBy 路由双轨）**均已修复**；R4-04 的 monitor 持 RPC fan-out、R5-ST 系列 open 项按 R4 归档引用不重复报。本文报告的是**对比视野下的结构性结论**。
- Flink 2.3.0 关键布局事实（源码核验）：barrier handler 自 flink-streaming-java 并入 `flink-runtime`（`streaming/runtime/io/checkpointing/`）；`CheckpointRequestDecider`/`CheckpointScheduling` 承担触发排队与节流；`InflightDataRescalingDescriptor` + `MappingBasedRepartitioner` 使 **unaligned checkpoint 可跨并行度恢复**（channel state 与 output subpartition state 均重分布）；`CheckpointOptions.AlignmentType` 四态（AT_LEAST_ONCE/ALIGNED/UNALIGNED/FORCED_ALIGNED）；savepoint 类型恒为 `alignedNoTimeout` 且 `unaligned()` 对 savepoint `checkArgument` 拒绝。

## 0. 机制对照表

| 机制 | Flink 2.3.0 | nop-stream（HEAD） | 语义等价性 |
|---|---|---|---|
| 一致性中心对象 | checkpointId 编号；一致性以"job 从 checkpoint 重启"为单位；per-checkpoint 元数据不含 fencing/fingerprint/participant manifest（`CheckpointProperties`） | epochId 为中心：offset/state/timer/watermark/sink txn/plan fingerprint/participant states/fencing token 全绑进 `EpochManifest`（设计 D70 有意差异） | 目标等价、组织相反：nop 的 epoch 是自描述恢复单元，Flink 依赖 JobGraph + OperatorCoordinator 交叉重建。nop 形态对"从单一 manifest 恢复"更直接 |
| 协调器并发 | 多 pending 并存；`CheckpointRequestDecider` 决策触发/排队/拒绝（`chooseRequestToExecute`/`chooseQueuedRequestToExecute` + `isTriggering` + `lastCheckpointCompletionRelativeTime`=minPause），被拒请求**入队待触发** | `tryTriggerCheckpointWithReason`：maxConcurrent → minPause(last-completed) → tasks-to-ack 三级门，被拒返回 `TriggerOutcome.reason`（THROTTLED_MIN_PAUSE/REJECTED_MAX_CONCURRENT/NO_TASKS_TO_ACK），**无队列**，等下个周期 | 节流语义等价；**拒绝后处置不等价**（Flink 排队保证 savepoint/terminal 最终执行，nop 调用方自担——见 C2） |
| task 侧对齐并发 | `SingleCheckpointBarrierHandler`："can handle/track just single checkpoint at a time"（单对齐；快照/完成经 `SubtaskCheckpointCoordinator` 流水线） | aligned 多 in-flight（Stage 45）：channel blocking 序列化对齐，多 epoch ACK/snapshot 经 `CheckpointBarrierTracker.inFlight` 流水线 | **等价**（nop 文档 D1 与 Flink 实现形态一致：单对齐 + ACK 流水线；"多 in-flight"实为 coordinator 级流水线化，不是并行对齐） |
| aligned→unaligned 翻转 | 触发期定档（`CheckpointOptions.forConfig`）+ 对齐超时翻转（`AlternatingCollectingBarriers.alignedCheckpointTimeout` → `barrier.asUnaligned()` + `prioritizeAllAnnouncements()` barrier 抢占 + upstream `CancelCheckpointMarker` unclog） | 运行期切换：`InputGate.checkAlignmentElapsed()`（每次 read 入口评估，与数据解耦）超 `unalignedThreshold`(1s) → `switchToUnalignedAndEmit`：per-channel `captureInFlightData` drain 式捕获 → emit barrier → resume 本进程 blockedChannels | **语义等价、机制适配各自传输模型**：nop 的对齐阻塞在消费端（by-reference 队列），无需跨 JVM unclog/优先级抢占；Flink 的 CancelCheckpointMarker 是 credit 网络栈的必需品，nop 不需要（合理裁剪，非缺陷） |
| unaligned 捕获面 | **输入+输出**：`ChannelStateCheckpointWriter.writeInput`（input channel buffers）+ `writeOutput`（ResultSubpartition 缓冲） | **仅输入**：`InputGate.captureInFlightData` drain input channel；输出侧 `RemoteResultPartition.write()` 无内部缓冲、在途数据在 transport 后端（设计 §2.11.5 显式裁定，非持久 backend 丢在途数据为已知限制） | 目标等价的前提是 transport 持久；**校验面缺口**：编译期 `STRICT_EXACTLY_ONCE` 校验表（§11）不含"transport 非持久 → 不允许声明 STRICT"条目（见 C4） |
| 超时/abort | `alignedCheckpointTimeout` → 翻转 unaligned；checkpoint 超时 → coordinator abort（`CheckpointFailureReason` ~40 个类型化原因 + `CheckpointFailureManager` 计数与 job-fail 决策） | `unalignedThreshold`(1s) → 切换；`barrierAlignmentTimeout`(30s) → task FAILED（`ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT`）；`checkpointTimeout`(600s) → abort handler（local: cancel tasks + job 失败；distributed: `cancelTask` RPC 携带 fencing epoch） | 分层等价（本地快检测 + coordinator 兜底）；nop abort 原因为自由字符串，Flink 类型化（见低成本清单） |
| savepoint | 恒 aligned（`alignedNoTimeout`；`unaligned()` 拒绝 savepoint）；`SavepointType{savepoint,terminate,suspend}` + drain 语义（`shouldDrain` 推进到 end-of-time）；restore 支持 `allowNonRestoredState` + CLAIM/NO_CLAIM/LEGACY 三种回收模式 | `CheckpointType{SAVEPOINT,TERMINAL_SAVEPOINT,EXPORTED_SAVEPOINT,COMPLETED_POINT_TYPE}` + `JobTerminationMode{CANCEL,DRAIN,SUSPEND,EXPORT_SAVEPOINT}`；restore 三层查找 miss fail-fast（R5-ST-07）；显式路径 + fingerprint 守卫 | 类型集等价（DRAIN≈terminate、SUSPEND≈suspend）；**对齐豁免缺失**：savepoint barrier 可在背压下降级为 unaligned（见 C1）；回收模式单一是定位内裁剪 |
| 增量 checkpoint | RocksDB per-key-group SST handle + `SharedStateRegistry` 引用计数 + 2.x CLAIM/FORWARD 共享策略 + `CheckpointsCleaner` 异步清理；savepoint 强制全量 | whole-SST SHA-256 内容寻址 + `SharedStateRegistryImpl` 引用计数 + `RetentionCleaner` 双平面同裁 + GC map + restart orphan 扫描；跨 JVM 需 Stage 40（未落地）；savepoint 全量 | 引用计数/内容寻址语义等价；粒度粗（整 SST vs key-group）与单 JVM 边界为已登记裁剪 |
| 恢复入口 | `restoreLatestCheckpointedStateInternal` / `restoreSavepoint`（savepoint 加载进 `CompletedCheckpointStore` + 计数器重置为 spId+1）→ `StateAssignmentOperation.assignStates()` | manifest 优先 → `CompletedCheckpoint` 回退（双平面）；`restoreTaskStatesFromSource`（keyed rescale 按 KeyGroupRange 交集；2PC sink 跨并行度 typed 拒绝；channel state rescale fail-fast；reverse-vertex differential 校验；channel state 重注入） | 入口结构等价；**恢复前检查面** nop 覆盖 fingerprint/operatorId/2PC/channel-state-rescale，Flink 覆盖 serializer snapshot 四态 + `VertexFinishedStateChecker`（部分完成算子校验）+ `allowNonRestoredState` |
| rescale + in-flight data | **支持**：`InflightDataRescalingDescriptor` + `RescaleMappings` + `MappingBasedRepartitioner` 对 input channel state 与 output subpartition state 双侧重分布（`StateAssignmentOperation:419-522`） | fail-fast（`ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED`，检测点在 rescale 循环前，§2.11.8 D1/D2） | nop 为显式裁定 successor；Flink 的机制可低成本映射到 nop 的静态 channel→producer-subtask 矩阵（见低成本清单 C-A3） |
| 完成后任务恢复语义 | `enableCheckpointsAfterTasksFinish` + `DefaultCheckpointPlanCalculator` 每 checkpoint **排除 finished tasks**；`VertexFinishedStateChecker` 恢复期校验"部分完成算子"；`FinishedOperatorState`/`FullyFinishedOperatorState` 状态类 | ACK 集合 job 生命周期静态（`registerTask` 仅接线时调用，`unregisterTask` 零生产调用方）；finished source 经 injector 线程 `injectBarrier()` 直执路径可 ACK；**完成的 middle/sink 永不再 ACK** | **结构性缺口**（见 C3）：Flink 把"作业部分完成"作为一等状态类管理，nop 只在 InputGate 对齐层处理 finished channel |
| region failover | `RestartPipelinedRegionFailoverStrategy` 默认自动划分 pipelined region；region 内全体从 checkpoint 重启（无"已完成 producer 跳过"窗口） | Stage 44：opt-in 物化点 + region 分解 + SupervisionLoop + drain/reconnect；no-mat 内部边×COMPLETED producer×checkpoint 回滚 fail-fast（REG-01 已修） | 方向等价、默认面不同：Flink 自动 region、按 region 重启；nop 默认单 region（=global recovery），region 是显式 opt-in（定位内裁剪合理）。nop 的 store+live-queue 双源拼接是 Flink 不存在的**缝类**（CC-02 已修至指令级残余）——换来的收益是存活 region 不必重启 |
| fencing | JobMaster leader session（独立于 checkpoint 元数据）；HA 下 `CheckpointIDCounter`/`CompletedCheckpointStore` 落 ZK/K8s | 单调 long fencing epoch（`leaderEpoch*EPOCH_SCALE+recoveryGen`）编入 epoch；JDBC lease + `activateAsLeader` → `rotateFencingEpochAndRestore`；counter restore 单调推进（不变式 #3） | 等价且 nop 更内聚（fencing 与 epoch 同生命周期）；JDBC-lease 零外部基建符合定位 |
| 存储/发布原子性 | 单 metadata 文件（`_metadata`）= 唯一提交点；`CheckpointScheduling`/`CheckpointsCleaner` 异步删除 | LocalFile：temp+ATOMIC_MOVE（单文件原子）；**双平面**（`{id}.checkpoint` + `{epochId}.epoch`）两次独立调用，无跨平面事务；JDBC 方言感知 upsert | 单文件提交点强于双平面；nop 以"manifest 优先 + checkpoint 回退"补一致性——正确性由 sink 幂等台账兜住，但 durable 边界比不变量 #5/#6 的表述模糊一档（见 §3） |
| watermark 状态 | **不持久化**（checkpoint 包零 watermark 载体，源码核验） | spec-only 未实现（设计 §2.5 明示 `TaskEpochSnapshot` 无 watermark 字段，恢复后水位归零重爬，登记 backlog P2-INV-7） | **两侧同病**：Flink 2.3 同样不持久化 watermark。这不是 nop 独有缺口；nop 若按 §2.5 落地将反超 |
| 可观测 | `CheckpointStatsTracker`（counts/history/每 task 对齐时长/字节精确） | `CheckpointMetrics` + `CheckpointHistory`（有界）+ estimateSize + 事件总线/engineMetrics | 目标等价，粒度 nop 粗（估算 size、无 per-task 对齐时长）——定位内可接受 |

## 1. 协议语义

**结论：对齐/翻转/并发三件套在各自传输模型下语义等价；差距全部集中在"非周期触发的显式动作"（savepoint/terminal）与"作业生命周期末态"（任务完成）两个端点。**

- **single vs multi in-flight——差异比宣传的小**。nop 文档反复强调"single-in-flight 是有记录的设计决策"，但 Stage 45 之后的事实形态是：**aligned 多 in-flight 已支持**（coordinator 尊重 `maxConcurrentCheckpoints`，task 侧 per-epoch ACK 追踪），且 task 侧"单对齐"恰好就是 Flink `SingleCheckpointBarrierHandler` 的实现形态（javadoc 明言 single at a time）。unaligned 保持 single-in-flight 并在 `switchToUnalignedAndEmit` 对 size>1 fail-fast——由于 aligned channel-blocking 天然序列化对齐，size>1 在生产路径几乎不可达（防御性守卫），该 fail-fast 不会咬人。vision §六决策点 #4（single→multi 需人审批）对应的"并发模型变更"实际已被 aligned 多 in-flight 消化大半，文档口径（§2.8.1）与代码一致，无漂移。
- **aligned→unaligned 切换**。两侧都是"先 aligned、超阈值转 unaligned、快照在途数据"。nop 的三个实现选择经得起对照：(1) 捕获语义 drain（非 copy）与 Flink 的 buffer-steal 语义一致；(2) 恢复重放先于新数据（`restoreChannelState` 注入 channel buffer）对应 Flink 的 `SequentialChannelStateReader` 先行；(3) elapsed 评估与数据返回解耦（P1-INV-2）对应 Flink 的 timer 驱动。**nop 缺的是输出侧**：Flink 持久化 output subpartition 缓冲，nop 依赖 transport 持久性（§2.11.5）——这在持久 backend 下正确，但 `STRICT_EXACTLY_ONCE` 编译校验（§11 表）没有对应条目把"非持久 transport + STRICT"组合拦下来（C4）。
- **savepoint 与 checkpoint 的差异面（C1，定位内缺陷）**。Flink 用类型系统保证 savepoint 三件事：恒 aligned、不可 unaligned（构造器 checkArgument）、超时不可翻 转（`isTimeoutable` 对 savepoint 恒 false）。nop 的 `CheckpointBarrier` 携带 `checkpointType`，但 `InputGate.checkAlignmentElapsed()`/`switchToUnalignedAndEmit()` **不查类型**：默认 `unalignedCheckpointEnabled=true` 下，一个背压 >1s 的 savepoint 会静默捕获 channel state 并降级为 unaligned。后果链：(a) savepoint 不再是纯一致切点（含在途数据）；(b) 从该 savepoint 做 rescale 恢复会撞 `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED` fail-fast（用户拿到的是"自己的 savepoint 拒绝了自己的恢复"）；(c) 跨拓扑移植时 channelIndex 语义无定义。aligned savepoint 在背压下本可等待（`barrierAlignmentTimeout` 30s 兜底），降级纯属无豁免。
- **超时/abort 分层**。nop 的三层（1s 切换 / 30s 对齐失败 / 600s coordinator 兜底）与 Flink（alignedCheckpointTimeout 翻转 / checkpoint timeout abort）同构，且 nop 的 abort 控制通道（mailbox signalCancel + interrupt / distributed cancelTask RPC 携 fencing epoch）满足"独立于数据流"硬契约——这是 Flink 用 `CancelCheckpointMarker`（数据通道内）+ RPC 双通道承担的职责，nop 把数据通道内 abort 显式裁定为 Decision-only（无消费方不造空壳），对照 Flink 的实践（marker 仅用于 aligned 期间 unclog）该裁定成立。
- **增量 checkpoint**。引用计数 + 内容寻址 + savepoint 强制全量三语义与 Flink 等价。结构性差距两条，均为已登记裁剪：粒度（whole-SST vs per-key-group，小状态增量放大不明显、定位内可接受）；跨 JVM（non-sst 伴生目录 task-local，DISTRIBUTED+RocksDB+incremental 组合恢复必败，Stage 40 open）。另有一处时序差异值得记录：Flink 2.x 的 CLAIM/FORWARD 共享策略 + `CheckpointsCleaner` 把"本地文件是否可删"的裁决推迟到持久层确认后；nop 用 F-02 的 task 侧 K 目录滚动清理 + 恢复以最新 durable 为锚论证闭合了该问题，代价是"连续 ≥K 次 persist 失败 → 更旧 durable 的恢复目录可能已被剪"（已裁定接受，边界登记在案）。

## 2. 恢复语义

**结论：恢复路径的静态检查面（fingerprint/operatorId/2PC/channel-state-rescale/reverse-vertex）已覆盖 Flink 的对应检查的定位内子集；已知雷区（REG-01/回放缝隙/savepoint miss）修复后与 Flink 的处理形成"两种哲学"：Flink 用状态类与重分区机制吸收复杂性，nop 用 fail-fast 把不可证安全的组合推出边界——对嵌入式定位是正确取舍，但有三个 Flink 已标准化的生命周期场景 nop 尚未覆盖。**

- **恢复路径对照**。nop：最新 durable manifest →（缺失回退）最新 CompletedCheckpoint → fingerprint 校验 → per-vertex 恢复（keyed 按 KeyGroupRange 交集、operator state 按 policy）→ channel state 重注入 → task 带新 fencing epoch 重启。Flink：`CompletedCheckpointStore.getLatestCheckpoint` → `StateAssignmentOperation.assignStates`（重分区 + 重分配）→ `JobManagerTaskRestore` 下发 → task `initializeState`。结构对位良好。差异点：
  1. **savepoint 恢复**：Flink 把 savepoint 装进 CompletedCheckpointStore 并把计数器重置为 spId+1（savepoint 从此参与 subsumption/回退）；nop 的 savepoint 恢复是独立路径（`restoreFromSavepointPath`），不进 retention/subsumption 体系——定位内合理（savepoint 只读），但意味着 nop 没有等价于 Flink "恢复后立即从 savepoint 继续做 checkpoint" 的单一路径，恢复后首个 epoch 依赖 counter 推进（`advanceCheckpointIdCounterAfterRestore`，不变式 #3 已钉）。已收口，无缺口。
  2. **部分完成作业的恢复**：Flink 的 `VertexFinishedStateChecker` 在恢复期校验"算子部分完成"组合（bounded 作业从 savepoint 恢复时，有算子已完成而上游未完成的组合被拒绝），`FinishedOperatorState` 把"完成态"持久化。nop 没有等价物：`COMPLETED_POINT_TYPE` final epoch 依赖全源完成，`validateReverseVertexDifferential` 只查拓扑差集不查完成态。与 C3 同根。
  3. **已确认雷区 vs Flink 对应处理**：
     - *no-mat 内部边丢失窗口*（REG-01 已修，fail-fast）：Flink 无此类窗口——region failover 会连同"已完成 producer"一起重启（无 F1 跳过概念）；Flink 在**恢复期**用 `VertexFinishedStateChecker` 拦同类"完成态×状态回滚"组合。两者殊途同归：**"producer 完成 + consumer 回滚"是必须显式裁决的组合**，Flink 把裁决放恢复期状态类，nop 放重启期 fail-fast。结论：nop 的修复方向与 Flink 语义等价，且 fail-fast 对定位更廉价。
     - *回放缝隙*（CC-02 已修至指令级残余）：Flink 不存在这类缝——unaligned 恢复是纯快照重放（`SequentialChannelStateReader`），region failover 是整 region 从 checkpoint 重启，**从不"对活着的 producer 做增量拼接"**。nop 的 SupervisionLoop 回放（store 快照 + 两次 drain + 实例同一性去重 + 单活跃段契约）是 Flink 没有的机制类，收益是存活 region 不重启（Flink 必须整 region 重启），代价是一条永久存在的正确性接缝（R4 后残余窗口为单指令级挂起）。这是**用复杂度换可用性的合理定位内选择**，但意味着 nop 的恢复正确性论证必须永远绑定这条缝的时序证明——建议把 T1/T3/T3'/T4 四步协议提升为 `failover-design.md` 的一等不变量（目前散在 successor plan 实现注记里）。
     - *explicit savepoint miss*（R5-ST-07 已修 fail-fast）：Flink `resolveCheckpoint` 路径缺失同样 fail-fast；nop 的三层查找 + 全枚举错误信息达到同等级。
  4. **rescale + channel state**：Flink 2.3 **已支持** unaligned checkpoint 跨并行度恢复（`InflightDataRescalingDescriptor` + `MappingBasedRepartitioner`，input/output 双侧）。nop 裁定 fail-fast（D1），D3 开放问题"records 可能无 key 如何重映射"在 Flink 的方案里不存在——Flink 按 **channel→上游 subtask 归属**重分布（`RescaleMappings` 描述旧新 channel 集合的映射），不需要 record key。nop 的分区矩阵 `[srcP][tgtP]` 使 channelIndex→producer subtask 是静态知识，该机制可映射（见低成本清单，中成本）。
- **双平面 durable 边界的模糊带（结构性观察，非缺陷立案）**。Flink 的 `_metadata` 单文件是唯一提交点；nop 的 `storeCheckPoint` + `storeEpochManifest` 是两次独立存储调用。失败窗口（checkpoint 行已落、manifest 未落）下：coordinator 走 `onCompletePersistFailure`（epoch 判 FAILED、`finishCommit(false)` 保留 sink 事务），但 restore 的 checkpoint 平面回退会把这个"被 coordinator 判死"的 epoch 当恢复锚——状态完整（ACK 齐了才落行）、sink 事务经 `restoreFromEpoch(N)` 幂等重提交，exactly-once 由台账幂等兜住。**结论：正确性成立，但"该 epoch 到底 durable 与否"存在两平面各执一词的窗口，依赖 sink 幂等收口**。这与不变量 #5/#6 的表述（"manifest durable 之前不 commit"成立；"恢复从最新 durable manifest 开始"在此窗口不成立——恢复从非 durable checkpoint 平面开始）存在文档级张力，建议在 §9.1 明示"checkpoint 平面回退 = 二等恢复源，其 epoch 视同 durable 处理"。

## 3. 实现质量（以 Flink 同名组件为参照系）

**结论：状态机完备性与失败清理对称性经 R4 修复后已接近 Flink 的骨架水准；剩余成熟度差距在"类型化失败原因"与"拒绝请求的后续处置"，不在核心路径。**

- **PendingCheckpoint 状态机**：nop 四态（RUNNING/COMPLETED/ABORTED/FAILED）+ `forceFail`（COMPLETED→FAILED 显式强制，F-01）+ abort/fail/forceFail 三条终态路径全部完成 future（ST-01 已修）。Flink 的 PendingCheckpoint 状态面更窄（无独立 FAILED 态，abort 携 `CheckpointFailureReason`），**nop 的状态机反而更显式**。差距：Flink 的 ~40 个类型化 `CheckpointFailureReason` vs nop 的自由字符串 reason——ops 侧无法按原因分类统计（低成本清单）。
- **ACK 验证**：nop 已有 unknown-ACK warn、非 RUNNING 拒绝、per-operator 去重、错误快照 fail-fast 路由（abortCallback）。Flink 另有 `ExecutionAttemptID` 维度的 attempt 校验（旧 attempt ACK 天然失效）——nop 的 fencing 在数据面 envelope 过滤层承担等价职责，ACK 面不校验 attempt，但 ACK 携带 checkpointId 且 epoch 单调，旧 attempt 的 ACK 只可能落在同 id 的 pending 上（coordinator 已轮转则 pending 不存在）→ 等价收口。无缺口。
- **失败清理对称性**：成功/失败/abort 三路径均做 pendingCheckpoints.remove + decrement + metrics/event/history + participant 通知 + terminal 标记清理（A2' 语义）；`abortAllPendingCheckpoints` 在 recovery 时清队；shutdown 对 pending 做 finishCommit(false)+dispose。与 Flink 的 `abortPendingCheckpoint`（discard 快照 + failure manager + stats + coordinator 通知）对称。R4-04 已登记的"monitor 持 RPC fan-out"（`onCompletePersistSuccess` 段 3a 内 participant 通知是 N 节点同步 RPC）按 R4 归档引用，不重复立案。
- **资源生命周期**：persist/retention 双 executor 懒创建 + 终态 shutdown 宽限窗口 + retention 合并/trailing re-run + GC map/ref-count 回滚（`releaseIncrementalSegments`）+ restart orphan 扫描。对照 Flink 的 `CheckpointsCleaner`（async per-checkpoint 删除）+ `SharedStateRegistry` offload，nop 的清理闭环更复杂（双平面 + 段 GC + 本地 K 目录三层），复杂度来源是单 JVM 内自管文件生命周期——Flink 把这事交给了文件系统语义（handle 即引用，物理删除由 filesystem 版本化兜底）。结构判断：**nop 的清理面复杂度是其"无外部依赖"定位的必要成本，不是过度设计**，但三层清理（retention 平面裁剪 / segment GC / local dir 滚动）各自独立容错语义，回归面大，值得在 C5 roadmap 里合并为一个"checkpoint 生命周期清理"测试簇。
- **线程模型**：Flink 全异步 CompletableFuture 流水线（executor/timer 分离、isTriggering 防重入、请求队列）；nop 单 monitor + 三专用线程池。嵌入定位下 nop 模型更易推理（R4-04 的 monitor-fan-out 是唯一结构性代价，已立案）。`isTriggering` 防重入 nop 无显式等价物（maxConcurrent=1 时由 pending 计数天然互斥；>1 时同一 tick 双触发由周期调度单线程保证）——当前安全，但依赖"触发单线程"隐含前提，建议在 §13.2 契约表补一行显式声明。

## 4. 最优化评估

**在 vision（嵌入式/中小规模/最小控制面）约束下，nop-stream 的主体设计选择（epoch 中心化 manifest、消费端对齐、drain 式捕获、JDBC lease HA、JSON manifest、opt-in 物化点 region failover）均为该定位下的近优解——对照 Flink 逐一检视后，没有发现"为复制 Flink 而引入"的结构；发现的是三类可修正项与少量可瘦身项。**

### 4.1 低成本可采纳的 Flink 机制

| # | 机制（Flink 锚点） | 对应 nop-stream 改动 | 预估成本 |
|---|---|---|---|
| C-A1 | savepoint 恒 aligned：`CheckpointOptions.forConfig`（savepoint→`alignedNoTimeout`，`unaligned()` checkArgument 拒绝） | `InputGate.checkAlignmentElapsed()`/`switchToUnalignedAndEmit()` 增加 barrier 类型豁免（SAVEPOINT/TERMINAL/EXPORTED/COMPLETED 不翻转、不超时翻转，仅对齐超时→FAILED）+ 单测 | 小（~15 行 + 2 测试）。**收益最高**：关闭 C1 |
| C-A2 | `CheckpointRequestDecider` 请求排队：savepoint/terminal 请求在 pending 满时入队、可触发时执行 | `terminateWithTerminalSavepoint`/`triggerSavepoint` 对 `TriggerOutcome` 为 REJECTED_MAX_CONCURRENT 时轮询重试（≤timeout），null/失败时**阻止终止**（DRAIN/SUSPEND 不得绕过 terminal savepoint 完成作业） | 小-中。关闭 C2 的一半（另一半是失败不终止的语义修正） |
| C-A3 | `InflightDataRescalingDescriptor` + `MappingBasedRepartitioner`：按 channel→上游 subtask 归属重分布 channel state（不需要 record key） | `ChannelState` 增加 per-channel 上游 subtask 元数据（nop 的 `[srcP][tgtP]` 矩阵使这是静态知识）；restore rescale 路径按旧 channel 归属把记录迁入新 subtask 的新 channel 替代 fail-fast | 中（元数据 + 恢复重分布 + 测试）。消除"unaligned 作业永不 rescale"的死组合 |
| C-A4 | `DefaultCheckpointPlanCalculator` 每 checkpoint 排除 finished tasks | coordinator 触发时从 `tasksToAcknowledge` 过滤已 COMPLETED 的 TaskLocation（JobCoordinator 已维护 completed tombstone；LOCAL 路径在 `SupervisionLoop` 终态扫描处同步收缩） | 中。关闭 C3 |
| C-A5 | 类型化 `CheckpointFailureReason` 枚举 | `abortPendingCheckpoint`/`onCompletePersistFailure` 的 reason 从 String 收敛为枚举 + 自由 detail 附参 | 小。ops 可按原因聚合告警 |
| C-A6 | `allowNonRestoredState` 语义已在 nop 以 STRICT/LENIENT 覆盖；补的是**校验面**：`STRICT_EXACTLY_ONCE` 编译校验表增加"非持久 transport × 严格语义"拒绝条目（§11 表补一行） | 小。关闭 C4 |

### 4.2 过度设计（超出定位必要，建议瘦身而非删除）

1. **sync-fallback 全路径保留**（`asyncSnapshotEnabled=false`）：为回退保留一条"全部 I/O 在 ACK 线程 monitor 内"的平行语义，导致设计文档几乎每个裁定都要写一遍 sync-fallback 分支（§2.2 段时序、§2.6 checksum 计算位置、§9.1 发布时机、retention D1(d)）。Flink 只有 async 一条路。建议：sync fallback 降级为测试逃生门并在文档标注"非生产支持面"，停止为它做新语义论证。
2. **failedCommitParticipants 重试机制**：Flink 不在 coordinator 内重试 `finishCommit`——靠 subsuming contract（下个 durable epoch 的完成通知携带 ≤N 的提交义务）自然收敛；nop 额外维护 per-epoch 失败集合 + `checkpointSuccessMap` 终态标记 + A2' 保留判定，三条交互规则（abort 时留、失败时留、成功时清）是纯增量簿记。收益（更早重试 commit）对定位不关键。建议评估退化为"只依赖下一 epoch subsuming"，删掉一层簿记。
3. **ACK 路由 legacy 回退**（`routeAckToEpoch` 的 cpId<0 → most-recent 分支）：生产路径已全部 tag checkpointId（42 处 call-site），该分支只剩测试流量且多 epoch 下是歧义源（warn 里自己都说 ambiguous）。建议给出迁移截止后删除。

### 4.3 不算过度设计的"重"

- 三专用线程池（persist/retention/timeout）每 job：嵌入定位下 3 线程/job 可接受，且职责分离有明确的 head-of-line-blocking 论证（§2.2）。保留。
- 双平面 manifest（checkpoint + epoch）：Flink 单文件更优，但 nop 双平面是 JDBC 存储自然形态 + `StorageJobIds` 命名空间隔离的支撑结构；其张力点已在 §2 用"二等恢复源"表述收口。保留 + 文档补注。
- Stage 44 全套 region failover（物化点/分解/监督循环/drain-reconnect）：看着超出"最小控制面"，但其 opt-in 默认关闭（默认单 region = 既有 global recovery 零回归），复杂度只在启用者身上兑现。保留。

## 5. 差距清单

### 定位内缺陷（vision 承诺的能力未达 Flink 同级语义）

| # | 差距 | 一句话 | Flink 对照 |
|---|---|---|---|
| C1 | savepoint 无对齐豁免 | 背压 >1s 时 savepoint 静默降级 unaligned，破坏"纯一致切点"并使后续 rescale 恢复撞自己设的 fail-fast | `CheckpointOptions` 类型系统三重保证 savepoint 恒 aligned |
| C2 | 终止/保存点触发无队列化、失败仍终止 | DRAIN/SUSPEND 撞上 in-flight 周期 checkpoint（maxConcurrent=1 默认）时 `tryTriggerPendingCheckpoint` 返回 null → **跳过 terminal savepoint 直接宣布作业完成**；且 terminal 路径 catch(Exception) 后仍 stop()——违反 §2.9"作业结束不是绕过 checkpoint 的特殊路径"（R4-CC-11 只修了中断位，skip/失败两支未覆盖；本轮 grep R4 五份报告未命中该项，若既有归档已含请以归档为准） | `CheckpointRequestDecider` 排队保证 savepoint 最终执行；stop-with-drain 失败时 job 不终止 |
| C3 | 任务完成后 ACK 集合不收缩 | 多源有界/分支先排空场景下，已完成的 middle/sink 任务永不 ACK 后续 epoch → 每周期 checkpoint 撞满 600s 超时 abort 循环（设计 §2.9 第 2 行只解决了对齐层，没解决 ACK 层） | `DefaultCheckpointPlanCalculator` 每 checkpoint 排除 finished tasks + `enableCheckpointsAfterTasksFinish` |
| C4 | 输出侧在途数据依赖 transport 持久性但无校验门 | 非持久 transport + `STRICT_EXACTLY_ONCE` 组合在编译期不被拒绝，运行期 unaligned 在途数据丢失无告警 | Flink 持久化 output subpartition state，无此前提 |

### 定位外裁剪（合理，不当缺陷）

- 无 `CancelCheckpointMarker`（消费端阻塞模型不需要跨 JVM unclog）；
- region failover 为 opt-in 物化点（默认单 region = global recovery；Flink 自动 region 的收益要在多 region 大作业上才浮现）；
- 增量 checkpoint 单 JVM（Stage 40）、whole-SST 粒度；
- JDBC lease 替代 ZK HA；JSON manifest 替代二进制 metadata；
- serializer 兼容两态（vs Flink 四态）+ 显式 `StateMigrationFunction`；
- 无 watermark 持久化（**Flink 2.3 同样没有**，共同上游空白）；
- 单一恢复回收模式（无 CLAIM/NO_CLAIM 分级）。

### 低成本/中成本可采纳（详见 §4.1）

C-A1 savepoint 强制 aligned（小）；C-A2 终止触发排队 + 失败不终止（小-中）；C-A4 per-checkpoint 排除已完成任务（中）；C-A5 类型化失败原因（小）；C-A6 transport 持久性校验条目（小）；C-A3 channel-state rescale descriptor（中）。

### 过度设计（瘦身建议，详见 §4.2）

sync-fallback 平行语义面；failedCommitParticipants 重试簿记层；ACK 路由 legacy 回退分支。

## 6. 质量评分

**4 / 5（定位内）**

- **给到 4 的理由**：协议骨架（epoch 中心化、manifest-durable-before-commit、fencing 编入 epoch、aligned 多 in-flight + 单对齐序列化、aligned→unaligned 运行时切换、增量 + 引用计数 + 三层清理闭环、keyed rescale 区间路由、全局恢复 + opt-in region failover）在 vision 边界内语义完整，且关键不变量都有 fail-fast + 测试钉；与 Flink 逐机制对照后，task 侧对齐并发形态甚至与 Flink 完全一致（单对齐 + ACK 流水线），部分设计（fencing 内聚进 epoch、abort 分层超时）比 Flink 更贴合自身模型。R4 修复后的已知雷区（REG-01/CC-02/ST-01/R5-ST-07）处理方式与 Flink 的同类场景语义等价。
- **不给 5 的理由**：四个生命周期端点缺口（C1 savepoint 对齐豁免、C2 终止触发跳过、C3 完成任务 ACK 收缩、C4 输出侧校验门）都是 Flink 用标准机制长期解决、nop 定位内同样会遇到的场景（有界源、背压下 savepoint、DRAIN 停机是中小规模的日常操作）；C2 尤其直接抵触自家 §2.9 不变量。这些是"端点工程"而非核心协议问题，修复成本全部在 §4.1 的低成本档内，修完即可到 4.5+。
- **不因定位豁免而扣分的项**：单 JVM 增量、无 ZK、JSON manifest、opt-in region、无 watermark 持久化——均已在 vision/裁决链有记录且 Flink 对照下不构成语义错误。
