# nop-stream vs Flink 2.3.0 — 数据面（network/shuffle）、任务执行与分布式编排对比

> Status: open
> Date: 2026-09-30
> Scope: `nop-stream/nop-stream-core/.../execution/`（ResultPartition、InputGate、InputChannel、TaskMailbox、GraphExecutionPlan）、`nop-stream/nop-stream-runtime/.../transport|coordinator|taskmanager|execution|cluster|rpc/`；对照 Flink `release-2.3.0`（`~/sources/flink`，commit `c0f8d1a1e09`）的 `flink-runtime` io/network（partition/consumer/netty/buffer）、streaming/runtime/tasks mailbox、runtime/taskmanager Task、runtime/scheduler（DefaultScheduler + failover）、runtime/highavailability
> Conclusion: 数据面「统一数据通道 + IMessageService + 有界队列」在定位内成立——正确性语义（fencing/EOS/对齐/unaligned）与 Flink 对齐且有真实多 JVM 演练证据，被 vision 豁免的 Netty/credit/二进制序列化不计缺陷，但 remote 边「失败制背压 + 无 barrier 插队」是真实语义代价；mailbox 声明与实现一致、属最小可用面，与 Flink 全量 mailbox 线程模型的差距被 Non-Goal 覆盖一半、另一半（full-mailbox 化）已由 owner 文档登记为 successor；编排面 global-recovery-only 是 failover-design 的显式 NO-GO 裁定而非缺陷，剩余结构性差距集中在 JDBC-lease HA 成熟度与控制面并发长尾（CC-06/07/10/15/17 open）。定位内质量评分 **4/5**。

## Context

- 本系列 07 号文档（`07-distributed-comparison.md`）对照的是 Flink 1.20 结构。本文按 Flink 2.3.0 重做**数据面、任务执行、分布式编排**三个维度的结构性对比，并纳入 nop-stream R4 深度审计（`ai-dev/audits/2026-09/2026-09-30-0530-deep-audit-nop-stream-quality-r4/`，R5-CC 系列）→ plan 368 修复后的 HEAD 现状。
- 校准基线：`00-vision.md` 约束 6（统一数据通道：Record/Barrier/Watermark 同管线，Barrier 不需要独立 RPC 通道）、约束 7（最小控制面：不引入 SlotSharingGroup、Netty 网络栈）、Non-Goal（复制 Flink Runtime 结构 / Netty / 二进制序列化体系 / PB 级吞吐，几十 GB 状态级）、§六决策点 #5（分布式通信模型变更需人决策）。**被 vision 豁免的不算缺陷；vision 内能力严格比。**
- 已确认缺陷不重复立项，只引现状：`04-concurrency-resource.md`（R5-CC-01..17）。经 HEAD 逐项核验，plan 368 Phase 3 已修复 CC-01/02/03/04/05/11（+REG-02），裁定表判定 CC-08/09/12/13/14/16 为 watch-only residual（不修），**仍 open 并归入 successor 的是 CC-06/07/10/15/17**。本文差异分析引用这些编号，不重新立项。
- Flink 2.3.0 相对 1.x 的布局变化（本文核对）：barrier handler 迁至 `streaming/runtime/io/checkpointing`（`SingleCheckpointBarrierHandler`/`CheckpointedInputGate`）；mailbox 落在 `streaming/runtime/tasks/mailbox/`；`ResultPartitionType` 新增 `HYBRID_FULL/HYBRID_SELECTIVE`（hybrid shuffle）与 `PIPELINED_APPROXIMATE`；新增 buffer debloating（`throughput/BufferDebloater`）。credit-based 流控、ZooKeeper/K8s HA、pipelined region failover 语义与 1.x 一致。

## 0. 机制对照表

| 机制 | Flink 2.3.0 | nop-stream（HEAD） | 语义等价性 |
|---|---|---|---|
| 端到端管线 | `BufferWritingResultPartition` → subpartition（buffer 粒度，32KB–1MB）→ Netty TCP 连接池 → `SingleInputGate` → `InputChannel`；Record/Event 同管线，barrier 为 in-band 事件 + priority buffer 插队 | `ResultPartition`（LinkedBlockingQueue 元素粒度，默认 1024）→ LOCAL 同 JVM by-reference；DISTRIBUTED 经 `RemoteResultPartition`（`IMessageService` topic = `{jobId}.{edgeId}.{srcSub}.{tgtSub}`）→ `RemoteInputChannel` 本地 1024 队列；Record/Barrier/Watermark 同管线（vision 约束 6 逐字落地） | **拓扑形态等价**（partition→channel 镜像；nop 的订阅收敛三态保证 TM 只订 assigned 输入）；载体粒度差一个数量级 |
| 流控 | credit-based：initial credit + floating buffer + sender backlog 声明（`RemoteInputChannel.onSenderBacklog` → `requestFloatingBuffers(backlog+initialCredit)` → `AddCredit` 反向通告）+ TCP 窗口 + buffer debloating | LOCAL：per-partition 有界队列阻塞 put + per-job `IBufferPool`（fair semaphore，元素粒度全局配额）；REMOTE：依赖消息后端背压；通道满 + 10s 零消费进展 → typed `ERR_STREAM_CHANNEL_OVERFLOW` 失败（D2 裁定「有界等待 + typed 可恢复失败」） | LOCAL **等价**（自然背压）；REMOTE **不同制**：Flink 是「减速制」（持续节流），nop 是「缓冲 + 失败制」（重放闭环兜底） |
| barrier 传播 | in-band + priority buffer（`addPriorityBuffer`/`announce`，barrier 插队到数据前）+ `SingleCheckpointBarrierHandler` 对齐，`aligned-checkpoint-timeout` 后转 unaligned（ChannelStateWriter 持久化在途 buffer） | in-band FIFO（与数据同队列，无插队）+ `InputGate.BarrierAlignment` 多 epoch 对齐 + 1s 阈值 aligned→unaligned fallback（`captureInFlightData` 捕 ChannelState） | 对齐/unaligned 语义**等价**；nop 的 unaligned escape 在持续积压下必然先于 aligned 完成（1s ≪ 30s），aligned 档位在积压作业上事实上不可达 |
| watermark | `StatusWatermarkValve`（per-channel min + idle 排除 + WatermarkStatus IDLE/ACTIVE 转发） | `InputGate.handleWatermark*`：per-channel min + idle 排除 + WatermarkStatus 转发（javadoc 明示镜像 Flink 语义） | **等价** |
| 任务线程模型 | `Task`（executingThread）+ `MailboxProcessor`：**mailbox 循环即主循环**（default action = `StreamTask.processInput`），operator 代码只在 mailbox 线程跑；`StreamTaskActionExecutor` 与 cancel/快照互斥；suspend/resume、per-mail priority、batch take | `SubtaskTask`/`RunningTask` 裸线程跑主循环；mailbox = 控制信号旁路（`TaskMailbox` 2 级优先级队列，CONTROL 先于 NORMAL），在安全点 drain（`SourceContext.collect()` / `processInputGate` 循环顶 / 空闲 250ms idle-return）；middle/sink 的 checkpoint trigger 保持 injector 线程同步（prime-before-barrier 不变量，mailbox-design §3.2） | 控制动作收敛方向相同；**完成度差距**：nop 是「最小 mailbox 控制面」（mailbox-design §1.1 显式 Non-Goal 不移植 Flink 全套），数据/控制无双线程互斥锁 |
| 任务状态机 | `ExecutionState`：CREATED→DEPLOYING→RUNNING→FINISHED/CANCELED/FAILED（attempt 级）+ Task 主线程终态回调 | `SubtaskTask.State` 9 态 + `TaskStateTransition` 校验（CREATED→SCHEDULED→DEPLOYING→RUNNING→COMPLETED/FAILED/CANCELED/RECOVERING/CANCELING）；TM 侧 `RunningTask` volatile canceled 终态 | **形态等价**；nop 终态写非 CAS（R5-CC-08，watch-only：后果=一次多余 region restart） |
| 取消/中断 | cancel → `cancelOrFailAndCancelInvokable`（状态 CAS + mailbox runAsync(cancel) + interrupt 链 + invokable 硬停兜底） | `MailboxExecutor.signalCancel()`（flag + CONTROL marker mail）+ `task.cancel()` CAS→CANCELING + interrupt 解阻塞 read；`InputLoopExitReason` 三态区分 EOS/CANCELLED/INTERRUPTED，取消不 finalize（不 finish、不发 MAX_WATERMARK/EOS） | **语义等价且 nop 显式**（截断流不冒充 bounded-complete）；机制上 nop 依赖 interrupt + 循环顶 flag 协同，无 mailbox 互斥锁 |
| 失败检测 | RM↔TM、JM↔TM 双向心跳（`HeartbeatManager`，超时→TaskManager 失败→slot 回收→failover）；RPC ask 全部带超时 | 节点 lease（15s/5s tick，`JdbcClusterRegistry`）+ per-subtask 双信号 liveness（data progress + task-loop activity，`lastProgressTime`/`lastActivityTime` volatile）+ COMPLETED 移除/REG-02 tombstone；stall 与真实故障双预算（`maxRestarts`/`maxStallRestarts`=3 + 30s cooldown） | 检测粒度 **nop 更细**（per-subtask vs per-TM）；信号通道不对称：Flink RPC 有超时契约，nop 控制面 RPC 无显式发送超时（CC-17） |
| 恢复模型 | `DefaultScheduler` + `ExecutionFailureHandler` + `RestartPipelinedRegionFailoverStrategy`（SCC 算 pipelined region，只重启受影响 region，blocking result 复用）+ `RestartBackoffTimeStrategy`（fixed/failure-rate/exponential） | DISTRIBUTED：唯一恢复入口 = `JobCoordinator.globalRecovery()`（fencing 轮转→全量重部署，`maxRestarts=3`）——failover-design §2 的**显式裁定**：all-pipelined → 单 region → targeted failover 零收益；LOCAL：`SupervisionLoop` 100ms 轮询 + 物化边 opt-in region restart（region 计数器 3 次/region + zombie fail-loud） | 定位内**等价于设计意图**；分布式面恢复粒度粗于 Flink（全作业重启 vs region），为已裁定边界 |
| checkpoint 编排 | `CheckpointCoordinator`（JM 侧）+ ACK + CompletedCheckpointStore（HA 时 ZK/K8s 持久）+ `CheckpointIDCounter`（ZK persistent sequential / standalone atomiclong） | `CheckpointCoordinator`（JC 侧）+ ACK + EpochManifest durable（LocalFile/JDBC 双存储，retained 双平面同裁）+ checkpoint id counter 由 JC 启动时从最新 durable epoch 推进（P0-03 shadow-window 修复） | 协议等价（single-in-flight epoch + barrier 对齐 + fencing）；**HA 计数器差距**：Flink 计数器在 ZK 持久，nop JC 重启从存储推断（等价闭包，但依赖「manifest 先于计数器重置」时序正确） |
| HA/选主 | `HighAvailabilityServices`：ZooKeeper/K8s leader election（Curator LeaderLatch/watch，毫秒级）+ LeaderRetrieval + 持久 checkpoint store + JobResultStore | `JdbcLeaderElector`（`nop_stream_leader` 租约表，乐观 epoch 递增，poll 轮询）+ `ClusterRegistry`（H2/MySQL）；HA 模式 standby JC 经租约激活 | 功能面等价（standby 选主 + fencing 单调）；**成熟度差距**：poll 轮询 vs watch、租约表删除后 epoch 回卷（R5-CC-15 open）、无 session 重连语义 |
| 任务部署 | `TaskExecutor`（slot 表）+ `TaskDeploymentDescriptor`（含 shuffle 描述符）+ ResultPartition 生命周期由调度器管理 | `TaskManager.deployTask`（capacity semaphore + 线程池 + invokable 安装 30s 超时）+ `TaskDeploymentDescriptor`（携带 XDSL spec/JobGraph + restore path）；TM 无全局 slot 表（per-node capacity 信号量） | 定位内等价；无 slot 共享/跨作业复用（Flink 有）——vision 约束 7 明示不引入 SlotSharingGroup |

## 1. 数据面架构

**结论：vision 约束 6（统一数据通道）以「每 subtask 对一个 topic 的元素粒度管线」实现，正确性语义与 Flink 对齐；LOCAL 面背压语义与 Flink 等价，REMOTE 面是「缓冲 + 失败制」而非「credit 减速制」，在定位内成立但有两条真实代价（aligned 档位退化、吞吐天花板由 JSON+消息后端决定）。**

### 1.1 结构与裁剪核对

- **同构面**。`ResultPartition → InputChannel → InputGate` 三件套与 Flink `ResultPartition → InputChannel → SingleInputGate` 同构：per- (src,tgt) subtask 通道、watermark min 合并 + idle 排除（`handleWatermarkNonRecursive`/`handleWatermarkStatusNonRecursive` 镜像 `StatusWatermarkValve`）、多 epoch 对齐 + finished-channel 补齐（`markFinishedChannel` 处理 finished channel 视为已交付全部 in-flight barrier——Flink 在 `processBarrier`/barrier handler 有同语义）。nop 缺失的 Flink 件全部有明确出处：`LocalInputChannel`（本地直读）在 LOCAL 模式由共享 `ResultPartition` 队列天然承担；`UnionInputGate`（多 gate 合并）无对应物——但 nop 无 input 复用/iteration 场景，未破定位。
- **vision 豁免核对（不算缺陷）**：Netty 栈、零拷贝二进制序列化、credit-based 流控（G27 裁决从 `FlowControlPolicy` 永久移除，枚举只剩 `BLOCKING_QUEUE`）、hybrid/blocking shuffle（batch 场景）、buffer debloating、压缩——全部命中 Non-Goal「不引入 Netty 网络栈/二进制序列化体系」或属于「PB 级吞吐」裁剪。Flink 的 credit 机制解决的问题是「TCP 上多路复用流的自适应缓冲分配」，nop 的跨 JVM 承载是 `IMessageService`（Kafka/Pulsar/JDBC），缓冲分配责任移交后端——**结构对应物存在，只是换了承载层**，vision 豁免论证成立。
- **订阅收敛三态（dataplane-transport-design D1）**是 Flink 没有的问题也没有的解：Flink 每 Task 只建自己的 InputGate（由调度器按 ExecutionGraph 装配），不存在「全图构建 + 全对订阅」形态；nop 因「TM 侧从完整 JobGraph 镜像重建 plan」而引入该问题，修复用「构建全图 + 订阅谁解耦」三态收敛。这是 nop 结构性选择（模型优先、可序列化 plan 为核）付出的独有复杂度——不算缺陷（已修复且有设计文档），但记入「结构性代价」。

### 1.2 背压语义：等价、减速制与失败制

- **LOCAL（单 JVM）**：per-partition 有界队列（1024）阻塞 put + per-job `IBufferPool`（fair semaphore，元素粒度）双级背压，上游 emit 阻塞即自然背压，与 Flink「subpartition 满 → writer 阻塞」的语义等价；fair semaphore 防 fan-out 场景单分区垄断，对应 Flink 的 per-channel 缓冲配额思想。BP-1 三档节流演练（50/200/500ms）证明健康慢消费者不被误伤（items 28+31 修复后复验）。
- **REMOTE**：Flink 的背压是**连续减速**——credit 耗尽 → 停止从 TCP 读 → TCP 窗口收窄 → 上游写阻塞，全程无失败；nop 是**缓冲 + 失败**——Kafka/Pulsar 后端积压在 broker（retention 内不丢），JDBC 后端积压在消息表；通道本地队列满且 10s 零消费进展 → typed `ERR_STREAM_CHANNEL_OVERFLOW` → task FAILED → recovery（JDBC cursor-0 重放 + 新 fencing epoch 过滤陈旧消息，exactly-once 闭环）。**语义差距**：Flink 的慢下游产生「整链同步减速 + aligned checkpoint 自然推进」；nop 的慢下游先积压、超窗后产生「重启 + 重放」——延迟尾部分布不同（一个连续膨胀，一个锯齿恢复）。定位内（几十 GB 状态、中小规模、重放源便宜）该取舍成立，且有界等待同时封死了 JDBC 2 线程 dispatch 池被死通道 jam 的停摆族（原 item 28/31 缺陷，SOAK-3 复验 36 倍流量收敛）。
- **注意一个不对称**：物化边 overflow-bypass（`enqueueWithBackpressure` offer 失败只落 store）是「永不背压」边——它换来 producer-region restart 的死锁释放，但意味着物化拓扑下 channel gauge 饱和不构成背压信号（runbook 已如实记录 OBS-1 判据形态）。

### 1.3 吞吐/延迟位置

- 逐记录 `JsonTool.stringify` 编码（`StreamElementCodec.encode`）+ 每分区 `sendLock` 串行 send + JDBC 后端逐条 INSERT，vs Flink buffer 粒度攒批（数百至数千记录/网络 IO）+ 二进制 + 零拷贝 + LZ4——**量级差距真实存在**（粗估 1–2 个数量级，取决于后端）。但演练证据显示定位内可接受：SOAK-3 源 ~20 行/s、释放后吞吐 82 行/s 的场景离任何后端极限都远；瓶颈先到状态后端（RocksDB JSON serde）与 sink（2PC JDBC），再到数据面。**定位豁免判据**：Non-Goal「PB 级吞吐」明示接受；「几十 GB 状态」定位下数据面不是第一瓶颈。
- 延迟面：nop 每 topic 每条消息独立投递，无攒批延迟（Flink buffer-timeout 反而引入最多数百 ms 攒批窗）；低吞吐小集群上 nop 的逐条路径**延迟不差**。真正吃亏的是 CPU/IO 成本单记录化（JDBC 后端每记录一行 INSERT 是消息表增长与 COUNT 代理失真的共同根因，runbook 已记录）。

### 1.4 barrier 通道：正确性等价，档位可达性退化

- 正确性：统一数据通道下 barrier 经数据 FIFO 传播 + 注入点在 source 读取线程（不变量 4）、对齐阻塞/恢复、abort 后 straggler 丢弃（`abortedBarriers` + per-channel `lastAcceptedBarrierIds` 去重）、多 epoch 重叠支持——与 Flink barrier handler 的语义面一一对应，且 R4 后收口了大部分并发缝（CC-09 仍为 watch-only 残留）。
- **档位可达性（定位内缺陷，P3）**：Flink 用 priority buffer 让 barrier 插队到数据之前，持续积压下 aligned checkpoint 仍可达（插队不破坏 record/barrier 相对序的语义要求，因为对齐只关心 barrier 先于本 channel 后续 record 到达快照）；nop 的 barrier 与数据同队列严格 FIFO，`checkAlignmentElapsed` 在 1s（unaligned threshold）必然先于 30s（alignment timeout）触发——即**任何持续积压的 remote 作业，aligned 模式事实上自动退化为 unaligned 模式**。正确性无损（unaligned 是更高成本但等价安全的档位），但「aligned 优先、超时降级」的配置语义在积压下名存实亡，且 unaligned 要求每次捕获 ChannelState（成本随在途量走）。低成本修法见 §5.3-1。

## 2. 任务执行模型

**结论：mailbox-design.md 声明的就是实现的最小面，无声明-实现落差；与 Flink 的差距一半被 §1.1 Non-Goal（不移植 MailboxProcessor 全套）豁免，另一半（full-mailbox 化、数据/控制互斥）是 owner 文档已登记的 successor，当前形态的净风险集中在 middle/sink 双控制线程的 prime 不变量上——该不变量目前由 `synchronized` 保护且经测试钉死，属「复杂度负债」而非缺陷。**

### 2.1 声明 vs 实现

mailbox-design.md 逐条核对 HEAD：Mail 2 级优先级 + CONTROL 先取（`TaskMailbox.poll` 先 control 后 normal）、put 非阻塞/close 幂等/take 唤醒（`TaskMailboxImpl` 同名实现）、`processAvailableMails` 返回 cancel flag、SOURCE trigger-checkpoint 经 mail 在发射点 drain（`StreamSourceOperator.setMailboxExecutor` 接线）、middle/sink trigger 保持 injector 线程同步（§3.2 裁定原样在位：prime 必须先于 in-band barrier 到达，否则 ACK 被丢、checkpoint hang——这是**不做 full-mailbox 化的正确理由**，Flink 不需要该补丁因为它的 trigger 本来就在 mailbox 线程内）、finished-source 例外（injector 线程直接执行 final checkpoint）、PT timer 已接线且空闲期 drain 闭环（AR-02：`InputGate` 250ms idle-return + `processInputGate` 空闲/EOS 区分）。**反空壳约束兑现**（§6）：无空方法体；`MailboxExecutor.runLoop()` 未用于生产但文档如实标注。

### 2.2 与 Flink mailbox 语义的差距

| 差距点 | Flink 2.3.0 | nop-stream | 裁定 |
|---|---|---|---|
| 循环形态 | mailbox 循环即主循环（`MailboxProcessor.run` 驱动 default action `processInput`），数据处理也在 mailbox 线程队列化 | 数据主循环裸跑 task 线程，mailbox 只承载控制信号，安全点 drain | Non-Goal §1.1 豁免；代价=控制 mail 只能在安全点被看到（最坏 250ms idle-return 或一条记录处理时长），对 PT timer 语义已被 AR-02 闭合 |
| 数据/控制互斥 | `StreamTaskActionExecutor`（synchronized）+ mailbox 锁：cancel/快照回调与 operator 代码硬互斥 | 无互斥锁：cancel 靠 interrupt + 循环顶 flag；快照在 barrier 处理点内联 | 部分 Non-Goal；残余即 CC-08 类三态竞态（watch-only，自愈型） |
| suspend/resume | `MailboxProcessor.suspend()`（SourceReader 空闲让出、async wait） | 无 | Non-Goal（异步算子 Non-Goal 的执行层对应物） |
| mail 优先级/批处理 | per-mail priority + batch take + Deferrable | 2 级 FIFO | Non-Goal |
| 控制线程数 | 1（mailbox 线程统一） | 2（task 线程 + injector 线程同步 trigger）+ abort 线程投 mail | **结构性差距**：`CheckpointBarrierTracker` 三处 `synchronized` 的存在正是双线程并发的补偿（mailbox-design §4 已裁定保留并写明 full-mailbox 化的解锁条件）。复杂度真实存在但被测试覆盖（`TestCheckpointBarrierTrackerConcurrency`），且 §5 的 ACK 越界缺陷已转正向断言 |

### 2.3 生命周期与 exactly-once 终态

- **取消不冒充完成**：`InputLoopExitReason.{END_OF_STREAM, CANCELLED, INTERRUPTED}` 三态把「success 终态（finish → MAX_WATERMARK → EOS 下游）」严格限制在真 EOS——2PC sink 的 flush/commit 窗口不会在 cancelled task 上运行；`invokeSource/invokeMiddle` 的 finally 在失败时**保留输出分区打开**（producer-region restart 契约）。对照 Flink：cancel 路径同样不触发 final checkpoint/finish（`StreamTask.shutdown` 区分），语义一致。
- **interrupt 保留为解阻塞手段**与 Flink 相同（Flink 的 `Task.cancelExecution` 同样 interrupt executingThread + mailbox cancel 双通道）；nop 单次 read 内最长阻塞 = 50ms 有界 poll（`CHANNEL_POLL_TIMEOUT_MS`）或 idle-return，实际中断响应性不劣于 Flink。
- **区域重启纪律**：`SupervisionLoop` 的 zombie fail-loud（`ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT`，不再静默重建双写同一 partition）、不可回放内部边 fail-fast（REG-01，`ERR_STREAM_RESTART_UNREPLAYABLE_INTERNAL_EDGE`——防「consumer 重建 + 状态回滚 × producer 已 EOS」静默截断）、CC-02 修复后的四步回放协议（drain→快照→二次 drain→单次 attach），这三条使 LOCAL region restart 的 exactly-once 论证完整度接近 Flink region failover + unaligned 恢复的组合。残余窗口如实登记在 failover-design（producer 线程恰在 store 写入后 enqueue 前被暂停跨 T3→T3'——单条指令级）。

## 3. 编排与容错

**结论：JobCoordinator 集中式编排 + fencing 单 long + 双预算恢复的骨架与 Flink JobMaster/DefaultScheduler 的职责划分等价（控制在 JC、执行在 TM、共享注册表），且 per-subtask liveness 粒度比 Flink per-TM 心跳更细；结构性差距按影响排序为 HA 成熟度（JDBC 租约 vs ZK watch）> 控制面并发长尾（CC-06/07/10/15/17）> 恢复粒度（global-only，已裁定）。**

### 3.1 职责对照

Flink 把「调度（slot 分配/region 计算/restart 策略）」「作业管理（JobMaster）」「资源管理（RM/SlotPool）」「失败检测（双向心跳）」「HA（leader election/存储）」拆成五个组件群；nop 全部收敛进 `JobCoordinator`（2595 行）+ `AssignmentPlanner` + `ClusterRegistry` + `CheckpointCoordinator` 四件。RD-05 已登记拆分线（checkpoint 编排/终止/Status/fencing 四条）。**收敛本身在定位内不是缺陷**（Flink 的拆分服务于万级 slot 的资源市场，nop 的 assignment 是静态规划 + 全量重分配），但两个具体后果值得记：单体内 monitor 纪律难维持（CC-06 的 monitor 内 RPC fan-out 即后果之一）；assignment 无资源弹性（Flink slot 共享让多 vertex 挤一个 TM，nop 用 capacity 信号量静态分割——小规模下等价）。

### 3.2 失败检测与恢复

- nop 的双信号 liveness 设计（`lastProgressTime` 数据进度 + `lastActivityTime` 线程活性，空闲迭代也 tick）与 Flink「心跳超时 + partition producer 状态检查」相比**检测粒度更细**（subtask 级 vs TM 级），且 COMPLETED 移除 + REG-02 tombstone + R4-N3 finished 过滤把「健康完成任务误报 stall」封死。stall/真实故障双预算（D3 裁定）是 Flink `FailureHandlingResult`/restart-budget 思想的子集实现：Flink 有 failure-rate/fixed/exponential 三种 backoff 策略，nop 只有 stall cooldown（30s）+ 绝对上限（3+3 后 failJob）。定位内够用——nop 的恢复单位是全作业重启，高频故障场景本来就不该用 nop。
- 恢复临界区纪律在 plan 368 后达标：recoveryLock 互斥 + 锁外 fan-out + CC-01 哨兵（`everAssigned && taskAssignmentMap.isEmpty()` → 重触发恢复，消除「active 但零 assignment」永久 wedge）+ CC-04 liveness 随代清理。对照 Flink 的恢复路径（ExecutionFailureHandler 单线程 + restart 策略节流），nop 的剩余风险是**分布式面触发源更多**（RPC FAILED 报告线程池 + 检测器 + lease），靠 CAS/锁收敛——CC-07（deploy 传输失败 → assignment 永久 benefit-of-the-doubt 豁免）正是「多触发源 + 无 RPC 超时契约」组合下的长尾。
- **region failover**：分布式面 global-only 是 failover-design §2 的显式 NO-GO（all-pipelined → 单 region → targeted 零收益），LOCAL 面由物化边 opt-in 提供 region 级重启。Flink 的 pipelined region failover 之所以值钱，是因为 blocking result（batch/bounded 边）让失败影响面天然受限；nop 无 blocking 边（物化点是 opt-in 旁路），**该差距是架构前提的推论而非遗漏**。定位内裁定：不缺陷；scale-up 触发条件（更高可用性要求/更大并行度）出现时才需要 vision 级重开（Stage 44 跨 JVM region failover 已预留）。

### 3.3 HA 面：JDBC 租约 vs ZooKeeper

功能面等价（standby JC + 租约选主 + fencing 单调 + attempt 历史种子化跨 leader 接管——item 34 修复后 CHAOS-2 两轮 kill 收敛）；成熟度差距具体化为三点：

1. **探测时延**：JDBC poll（leader 检测间隔）+ 租约过期 vs ZK watch 毫秒级推送——nop 的 failover RTO 下限 = poll 周期 + 激活路径（读 attempt 历史 + 重部署），秒级~十秒级，Flink 是亚秒~秒级。定位内可接受（演练判据未约束 RTO 上限），但这是部署形态从 H2 单机库迁向真实 MySQL 集群时要写入 SLA 的参数。
2. **单调性根基**：Flink 的 epoch 单调性由 ZK persistent sequential 节点保证，删除运行数据 ≠ 单调性破坏；nop 的 `JdbcLeaderElector` 在租约行丢失时 `tryBecomeLeader` 硬编码 epoch=1（**R5-CC-15，open**），运维清表即可触发 fencing 回卷 → 全 TM 拒绝 → 与无隔离推送叠加成接管半途失败（CC-15×CC-01 组合——CC-01 哨兵已缓解 wedge 面，但 epoch 回卷本身仍会派生小于现值的 fencing token）。
3. **故障面独立性**：nop 控制面与数据面共享同一消息后端（topic 分离）——JDBC 后端下控制 RPC 与数据投递竞争 2 线程 dispatch 池（D2 设计已论证等待窗与租约线程隔离，但 dispatch 线程本身仍是共享资源）。Flink 控制面（RPC/akka→puloer）与数据面（Netty）物理隔离。定位内（控制消息频率低：心跳/ACK/部署）可接受，记入边界而非缺陷。

### 3.4 控制面并发长尾（open 清单对照 Flink）

| open 项 | 内容 | Flink 同域机制 |
|---|---|---|
| R5-CC-06 | `CheckpointCoordinator` monitor 内执行 participant 通知/abortHandler（N×阻塞 RPC fan-out），ACK/触发/超时全停摆，可放大为误 recovery | `CheckpointCoordinator` 的通知走线程池 + RPC 异步化；monitor 不跨网络 IO |
| R5-CC-07 | deployTask 传输失败后无 liveness 记录 → 永久 benefit-of-the-doubt 盲区（物化拓扑静默半死） | Execution deploy 失败走 Execution 状态机 FAILED → failover；无「无记录即豁免」面 |
| R5-CC-10 | TM commitExecutor 单线程无界队列，慢提交无限积压 | Flink sink commit 有超时 + 有界重试 + mailbox 上界 |
| R5-CC-15 | elector epoch=1 硬编码回卷（见 §3.3） | ZK sequential 无此面 |
| R5-CC-17 | taskExecutor 无界队列 + 终端 RPC 在任务线程 finally 同步发送，协调端卡顿可放大为节点雪崩 | Task 终态上报经 RPC 网关（超时/重试契约明确），不占任务执行线程池 |

共性根因：**nop 控制面 RPC 无显式发送/响应超时契约**（CC-17 建议写入 `IStreamTaskRpcService` javadoc），而 Flink 的每一跳 RPC 都有超时。这五项均已登记 successor，修复成本都低（per-node try/catch、宽限期、有界队列、单调初值、超时契约），不构成架构返工。

## 4. 最优化评估（定位内最优性）

**结论：在「几十 GB 状态、中小规模、模型优先、Nop 平台基建复用」的定位内，当前数据面/执行模型/编排面是接近最优的实现选择：每一处与 Flink 的简化都有明确出处（vision Non-Goal、G27 裁决、failover NO-GO、D2/D3 裁定）且用可观测性 + 演练证据换回了信心。主要非最优点是 aligned 档位可达性与 JSON 逐记录成本（部分豁免、部分低成本可修），过度设计面很小。**

- **豁免核对表**：无 Netty ✅、无 credit 流控 ✅（G27）、无二进制序列化 ✅、无 SlotSharingGroup ✅、无 PB 吞吐诉求 ✅、无在线 reshard ✅（离线 action 已交付）、BroadcastState ✅（G36）。均不列为缺陷。
- **定位内必要且已做到**：fencing 单 long 双不变量（leadership + recovery 单比较）；EOS typed 失败（防静默截断）；订阅收敛三态；恢复预算分池；retention 双平面同裁；运维观测面（TM 本地 pull 端点 + per-channel gauge + COUNT 对照）。
- **Flink 2.3.0 的新东西 nop 不需要**：hybrid shuffle（batch/流混跑场景）、PIPELINED_APPROXIMATE（approximate recovery）、buffer debloating（credit 调优）、ForSt/async state v2（状态维度已由 09b 覆盖）。

## 5. 差距清单

### 5.1 定位内缺陷

| 编号 | 内容 | 级别（沿用 R4 口径） | 现状 |
|---|---|---|---|
| R5-CC-06 | checkpoint 持久化 monitor 内执行 N×阻塞 RPC（participant 通知 + abortHandler），慢节点放大为控制面停摆 + 误 recovery 级联 | P2 | open（successor：monitor 与 fan-out 分离） |
| R5-CC-07 | deployTask 传输失败 → assignment 无 liveness 记录永久豁免；物化拓扑下静默半死无恢复触发 | P2 | open（successor：部署宽限期；plan 368 已收敛更大 wedge 入口） |
| R5-CC-15 | `JdbcLeaderElector.tryBecomeLeader` 硬编码 epoch=1，租约行丢失后 fencing 单调性断裂 | P3 | open（successor：HA/fencing 专项） |
| R5-CC-17 | taskExecutor 无界队列 + 终端 RPC 任务线程 finally 同步发送；控制面无超时契约 | P3 | open（successor：同 CC-06 波次） |
| R5-CC-10 | TM commitExecutor 单线程无界队列，慢提交线性放大延迟 | P3 | open（successor：资源上界专项） |
| **NEW-C**（本次） | **remote 边 barrier 无插队机制，持续积压下 aligned checkpoint 档位事实上不可达**（1s unaligned 阈值必然先于 30s 对齐超时触发；`InputChannel` 数据/barrier 同队列 FIFO）。正确性无损（unaligned 恒等安全），但配置语义「aligned 优先」名存实亡，且 unaligned 的 ChannelState 捕获成本随在途量上升 | 建议 P3 | 本次新发现 |
| watch-only 残留 | CC-08（SubtaskTask 三态竞态）/CC-09（abortBarrierAlignment check-then-act 缝）/CC-12（rewire 非原子漏发 barrier）/CC-13（EOS sentinel 入队失败无人唤醒，生产路径免疫）/CC-14（close TOCTOU 重复 EOS，单线程调用方不可达）/CC-16（leaderEpoch=0 哨兵，Jdbc elector 不触发） | P3 | plan 368 裁定不修，有兜底（超时/幂等/预算），保持 watch |

### 5.2 定位外裁剪（vision/裁决排除，不算缺陷）

| 项 | 裁剪依据 |
|---|---|
| Netty 网络栈 + 零拷贝二进制序列化 + 压缩 + buffer debloating | vision §四 Non-Goal「不引入 Netty 网络栈/二进制序列化体系」 |
| credit-based 流控 | G27 裁决永久移除（`FlowControlPolicy` 仅 `BLOCKING_QUEUE`）；跨 JVM 背压责任移交 `IMessageService` 后端 |
| blocking partition / hybrid shuffle / PIPELINED_APPROXIMATE | batch 场景 Non-Goal；approximate recovery 超出 exactly-once 定位 |
| SlotSharingGroup / slot 共享 / 资源市场 | vision 约束 7 明示不引入；capacity 信号量在中小规模等价 |
| 分布式 pipelined region failover | failover-design §2 NO-GO（all-pipelined 单 region → targeted 零收益）；Stage 44 物化点为 opt-in 替代，跨 JVM region failover 留作 vision 级重开项 |
| ZK/K8s HA 组件群 | §六决策点 #5：通信模型变更须人决策；JDBC 租约是「零基建」裁定（JdbcLeaderElector javadoc 明示） |
| MailboxProcessor 全套（suspend/Deferrable/per-mail priority/批 take） | mailbox-design §1.1 显式 Non-Goal |
| RPC 框架级超时/重试/熔断 | 控制面复用 `IMessageService`，超时契约属传输层后端职责——但**超时契约的显式声明**仍应补（CC-17），此为契约缺失而非架构缺陷 |

### 5.3 低成本可采纳（Flink 已证可行、不破定位）

1. **barrier 优先通道**（对 NEW-C）：`InputChannel` 双队列（data FIFO + control FIFO，`InputGate` 读路径先排空 control）——等价于 Flink priority buffer 的队列版，改动集中在 `RemoteInputChannel.EnvelopeConsumer` 入队分流与 gate 读序，让 aligned 档位在积压下可达。需评估对「barrier 先于本 channel 后续 record」语义的保持（Flink 语义即如此，安全）。
2. **控制面 RPC 超时契约**（CC-17 一半）：`IStreamTaskRpcService`/`IStreamCoordinatorRpcService` javadoc 固化「send 须有界或由后端保证有界」+ 后端实现核查清单——文档级改动先行。
3. **部署宽限期**（CC-07 修复建议）：assignment 记录时间戳，`detectFailures` 对超宽限无 liveness 的 assignment 判定部署失败并重触发——半小时级改动。
4. **fan-out 移出 monitor**（CC-06 修复建议）：participant 通知/abortHandler 投独立 executor，`failedCommitParticipants` 重试结构已天然支持异步化。
5. **elector epoch 单调源**（CC-15 修复建议）：insert 失败/空表时改走 `changeLeader` 递增路径，或初值取墙钟秒——防线一行级。
6. **commit/执行队列上界**（CC-10/CC-17 一半）：有界队列 + CallerRuns/丢弃-由-subsuming-兜底 + 丢弃打点。
7. **restart backoff**：给 globalRecovery 加 exponential backoff（复用 stall cooldown 机制），把「3 次内连发」改善为「3 次内退避」——Flink `ExponentialDelayRestartBackoffTimeStrategy` 思想，几十行。
8. **failure-rate 恢复策略**（可选）：`maxRestarts=3` 绝对上限替换/叠加「时间窗内 N 次」语义，避免长生命周期作业早期故障耗尽预算——定位内低优先。

### 5.4 过度设计点（定位内可再精简）

1. **`EdgeConfig.receiveWindow`/`packetSize` 死旋钮**：XDSL `StreamEdgeModel` 与 `EdgeConfig` 都声明并序列化这两个字段，但全引擎**零运行时消费者**（grep 核实：仅 getter 与模型搬运）。它们是 Flink TCP 流控词汇（receive window/packet size）的残留，与 G27 裁决后的引擎无关——用户配置它们没有任何效果。应删除或在 schema 层 fail-fast 声明 unsupported（与 backlog「flow 声明面波次」AR-06/08/09 同族）。
2. **`FlowControlPolicy` 单值枚举**：G27 移除后只剩 `BLOCKING_QUEUE` 一个值，保留枚举 + `EdgeConfig` 构造校验是为 API 兼容；可在下一次 schema 演进时降级为 boolean 或直接删除（记录为低优先清理，非急迫）。
3. **分布式演练矩阵的判定依赖人工校准**：`ExerciseSampler` 的 jam 签名（增长 + 冻结耦合判定）、OBS 档位需接到 window assigner 等都依赖演练归因知识，沉淀在 runbook 而非工具内——这不是过度设计，而是**知识位置**问题；若后续出现第二套场景，建议把签名判定固化进 `ExerciseSampler`。此条不计入评分。

## 6. 质量评分

**4 / 5（定位内）**

理由：

- **加分**：(1) 数据面正确性语义（fencing 单 long 双不变量、EOS typed 失败、对齐/unaligned、watermark idle 排除）与 Flink 对齐，且有真实多 JVM 演练证据链（SOAK-3 36 倍流量、CHAOS-1/2 kill 轮、BP-1 三档、HA takeover attempt 种子化）——这是很多结构细节无法替代的信心来源；(2) 与 Flink 的每一处简化都有显式裁决出处（vision Non-Goal、G27、failover NO-GO、D1–D4），**没有「忘了做」型差距**；(3) plan 368 后并发缺陷的修复质量高（CC-01 哨兵、CC-02 四步回放、CC-03 cancel fan-out、CC-04/05 liveness 过滤都有专项回归钉死）；(4) per-subtask 双信号 liveness + stall/真实故障双预算比 Flink 同域机制粒度更细。
- **扣分一**（-0.5）：控制面并发长尾仍 open（CC-06/07/10/15/17）——共性根因是「控制面 RPC 无超时契约 + monitor 纪律 + 无界队列」三个工程缺口，对照 Flink 的 RPC/调度器成熟度属于同代差距；任一项在故障场景叠加都可能放大为控制面停摆（CC-06→误 recovery、CC-17→节点雪崩）。
- **扣分二**（-0.5）：HA 面成熟度与 Flink ZK 级的代差（poll vs watch、租约行丢失即单调性断裂、控制/数据面共享后端）+ aligned 档位在积压下不可达（NEW-C）+ 恢复粒度 global-only（已裁定但客观存在）。这些不影响定位内的正确性，但决定了「遇到比演练矩阵更恶劣的环境时」的余量。

不评 5：open 的 P2（CC-06/07）+ HA 代差尚未收口；不评 3：所有 open 项均有兜底或登记，正确性不变量（§八 4–8：barrier 注入切点、manifest durable 先于 commit、恢复从最新 durable、旧 attempt/旧 coordinator 必须被 fencing）在 HEAD 上无已知违例路径。

## References

- `ai-dev/design/nop-stream/00-vision.md`（约束 6/7、§四 Non-Goals、§六决策点 #5、§八不变量 4–8）
- `ai-dev/design/nop-stream/dataplane-transport-design.md`（D1 订阅收敛三态、D2 有界等待、D3 恢复预算分池）
- `ai-dev/design/nop-stream/mailbox-design.md`（§1.1 Non-Goals、§3.2 prime 不变量、§4 synchronized 裁定、§7 PT timer 接线）
- `ai-dev/design/nop-stream/distributed-runbook.md`（演练矩阵、已知边界、观测面）
- `ai-dev/design/nop-stream/failover-design.md`（§2 NO-GO 裁定、§9 物化点/region/supervision 落地状态）
- `ai-dev/audits/2026-09/2026-09-30-0530-deep-audit-nop-stream-quality-r4/04-concurrency-resource.md`（R5-CC-01..17 现状）
- `ai-dev/analysis/nop-stream/07-distributed-comparison.md`（Flink 1.20 对照）、`09b-flink2.3-compare-state-backend.md`（同轮 2.3.0 状态维度）
- `ai-dev/plans/368-nop-stream-audit-r5-defects-governance.md`（修复裁定表：CC-01/02/03/04/05/11 修复，CC-08/09/12/13/14/16 watch-only）、`ai-dev/backlog/nop-stream-r5-successors.md`（open 项波次）
- nop-stream：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java`、`InputGate.java`、`InputChannel.java`、`RecordWriter.java`、`buffer/BufferPool.java`、`flow/{EdgeConfig,FlowControlPolicy}.java`、`execution/task/{StreamTaskInvokable,SubtaskTask,TaskExecutor}.java`、`{TaskMailbox,MailboxExecutor,Mail}.java`、`transport/StreamElementCodec.java`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/{RemoteInputChannel,RemoteResultPartition,DataPlaneMessageServiceAdapter,StreamTopicNaming}.java`、`coordinator/JobCoordinator.java`、`taskmanager/{TaskManager,RunningTask}.java`、`execution/SupervisionLoop.java`、`cluster/{JdbcLeaderElector,JdbcClusterRegistry}.java`
- Flink 2.3.0：`flink-runtime/src/main/java/org/apache/flink/runtime/io/network/partition/{ResultPartitionType,PipelinedSubpartition,BufferWritingResultPartition}.java`、`.../partition/consumer/{RemoteInputChannel,SingleInputGate}.java`、`.../netty/NettyMessage.java`、`.../throughput/BufferDebloater.java`、`.../streaming/runtime/tasks/mailbox/{MailboxProcessor,TaskMailboxImpl}.java`、`.../streaming/runtime/io/checkpointing/SingleCheckpointBarrierHandler.java`、`.../runtime/taskmanager/Task.java`、`.../runtime/scheduler/{DefaultScheduler,failover/*}.java`、`.../runtime/highavailability/{HighAvailabilityServices,zookeeper/ZooKeeperLeaderElectionHaServices}.java`、`.../runtime/checkpoint/{ZooKeeperCheckpointIDCounter,StandaloneCheckpointIDCounter}.java`

## Open Questions

- [ ] NEW-C（barrier 插队）的 owner 裁定：是补双队列优先通道，还是在 `checkpoint-design.md` 明示「remote 持续积压作业的 aligned 档位自动降级为 unaligned，属预期行为」？后者零代码成本，前者恢复配置语义。
- [ ] `EdgeConfig.receiveWindow/packetSize` 归属：并入 backlog「flow 声明面波次」（AR-06/08/09 同批 schema 清理）还是单独 fail-fast？
- [ ] CC-17 的「超时契约固化到 `IStreamTaskRpcService` javadoc」可否先于代码修复落地（文档先行，零风险）？
