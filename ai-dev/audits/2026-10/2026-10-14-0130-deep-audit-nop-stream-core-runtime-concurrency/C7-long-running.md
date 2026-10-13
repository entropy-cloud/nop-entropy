# C7 长时运行稳定性 — nop-stream-core-runtime（第 1 轮初审）

- 审计日期：2026-10-14
- 审计维度：C7（长时运行稳定性）——单调增长、挂死可检测性（F5）、重试风暴、自愈语义、时钟与期限、热路径外慢性病
- 审计对象：
  - `nop-stream/nop-stream-core/src/main/java`（439 files / 57,135 lines）
  - `nop-stream/nop-stream-runtime/src/main/java`（132 files / 29,922 lines）
  - `_` 前缀生成文件不作为发现对象。
- 盲审纪律：本轮为第 1 轮初审，未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；所有判定仅基于当前 live code。
- 前置阅读：`AGENTS.md`、`docs-for-ai/INDEX.md`、owner doc `docs-for-ai/03-modules/nop-stream.md`、审计提示词 `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + C7 正文）、基线 `_tmp/cra-baseline-nop-stream-core-runtime.txt`。
- 定向对照缺陷族：F4（无界账本只增不删）、F5（挂死生产者对健康检测不可见）；同时按 F3 对每个发现做同族失效面枚举。

## 检查范围说明

按 C7 正文六个步骤执行：

1. **单调增长盘点**：逐一核对了 runtime 侧持久化面（`nop_stream_task_assignment` / `nop_stream_node` / `nop_stream_coordinator` / `nop_stream_leader` / `nop_stream_msg_queue`、JDBC/local checkpoint 双平面 + epoch manifest）与内存面（`InMemoryClusterRegistry` 各 map、`JobCoordinator.subtaskLiveness/completedSubtaskKeys/deployIssuedAtMs/checkpointSuccessMap/failedCommitParticipants/barrierFanoutExecutors`、`CheckpointCoordinator.pendingCheckpoints/checkpointSegments/checkpointHistory`、`SupervisionLoop.regionRestartCounts`、`PollingJdbcMessageService.subscriptions`、metrics 注册表 `EngineMetrics.BY_JOB`/`TaskNodeMetrics.BY_NODE`/`MicrometerStreamTaskMetrics.TASK_HOLDERS`/`ChannelQueueGauges.HOLDERS`/`DbBacklogGauges.HOLDERS`/`WebhookAlertChannel.delivered`）；core 侧（`SideOutputRegistry.CONSUMERS` 静态注册表、`InMemoryMaterializationPoint.store`、`PerKeyOrderedBuffer`、`HeapInternalTimerService` 计时器表、`WindowOperator.activeWindows`、`TwoPhaseCommitSinkFunction.pendingCommits`、`SharedStateRegistryImpl` 引用计数、`CachingTableLookup` 缓存、`StreamElementCodec.DECODED_VALUE_TYPES`、`TaskMailbox` 队列）。日志放大逐点核对了周期循环内的 error/warn 调用点；metrics 标签基数按 jobId/vertexId/subtaskIndex/nodeId/topic 键空间单独验算。
2. **挂死可检测性**：盘点了全部阻塞/等待点（`TaskMailbox.take/wait`、`InputGate` 有界 poll、`RemoteInputChannel` 有界 offer + typed overflow、`BufferPool.acquire`（Semaphore，可中断）、`ResultPartition.read→queue.take`、barrier alignment 30s fail-fast、`ProcessingTimeServiceDriver` 100ms tick sleep、JDBC/elector 轮询、webhook HTTP 5s 超时、stop 路径各 `awaitTermination(5s)`），并核对每点是否被任务活性心跳（`markActivity`/`markProgress` → `subtaskLiveness` → `detectFailures` → stall recovery + cancel-interrupt）覆盖。F5 结论：任务级挂死（线程卡死不 tick）有完整检测-恢复-中断闭环；"SOURCE 挂着不出数据"按 owner doc 声明为活性-进度解耦的合法态（挂死点清单中单独标注）。
3. **重试风暴**：核对全部重试点的上限与节奏（`JdbcLeaderElector` 32 次有界预算、webhook 2 次 + 可配退避、checkpoint ACK 3 次有界重试、`SupervisionLoop` 每区域重启预算 + 响亮失败、stall 恢复独立预算 + 30s 冷却、`retryFailedCommits` 以 checkpoint 周期为节奏的有界速率重试）。未发现无速率上限的紧循环重试。
4. **自愈语义**：核对 `globalRecovery` 状态清理（liveness 三 map 全清、fencing epoch 轮转、zombie 心跳拒绝 CC-05、CC-01 空 assignment 楔死自检、R5-CC-07 部署宽限窗）、`TaskManager.stop` 资源与指标释放、`RetentionCleaner` 双平面保留收敛、恢复时 checkpoint id counter 前推防 shadow-window、`SharedStateRegistry` 孤儿文件重启对账（类注释声明）。旧租约/旧临时文件：内存 registry 有 `evictExpiredNodes`；JDBC `nop_stream_node` 死节点行无删除路径（见清单，按 nodeId 键空间有界）。
5. **时钟与期限**：全部超时/租约/停顿判定使用墙钟 `CoreMetrics.currentTimeMillis`（C7-010）；`CachingTableLookup`/WI16 时间片使用 `System.nanoTime`（单调，正确）。epoch/generation 均为 long 且增速极低，溢出不可达（清单记录，不立项）。
6. **热路径外慢性病**：listener/缓存只增不减点逐个核对，命中 `SideOutputRegistry`（C7-006）、`MicrometerStreamTaskMetrics`（C7-007）、`WebhookAlertChannel.delivered`（C7-008）。

**经验证的正面结论（不立项，供复核对照）**：durable checkpoint 与 epoch manifest 双平面有 `RetentionCleaner` 保留收敛（AR-06/F4 的 checkpoint 面已闭合）；`checkpointSuccessMap`/`failedCommitParticipants`/`pendingCheckpoints` 生命周期有完整 remove 路径；liveness 三 map 在 `globalRecovery` 全清；`EngineMetrics`/`TaskNodeMetrics`/`ChannelQueueGauges`/`DbBacklogGauges` 均有成对 release；`CachingTableLookup` 有 maxEntries 淘汰 + TTL + 自愈出口；`WebhookAlertChannel` 投递队列为有界 256 + 满丢弃 WARN；`RpcDistributedExecutor` 启动失败有 teardown；`OpsJobManager` 治理清扫有异常围栏。

---

## 单调增长源清单

| # | 增长源 | 位置 | 增长条件 | 上界/清理路径 | 判定 |
|---|---|---|---|---|---|
| 1 | `InMemoryMaterializationPoint.store`（物化 bypass 库） | core `execution/materialization/InMemoryMaterializationPoint.java:41` | materialization-enabled 边的每条数据记录 | **无任何删除/裁剪 API** | **缺陷 C7-001（P1）** |
| 2 | `nop_stream_task_assignment` 表行（每 attempt 一行） | runtime `cluster/JdbcClusterRegistry.java:219-226` | 每次 assignment/重启/恢复 | `removeTaskAssignment`（:275）**无生产调用方** | **缺陷 C7-002（P2）** |
| 2b | `InMemoryClusterRegistry.taskAssignmentHistory`（每 attempt 追加） | runtime `cluster/InMemoryClusterRegistry.java:44,149-166` | 同上（每 coordinator 生命周期内有重启预算约束） | `removeTaskAssignment`（:190）同样无调用方；进程重启即清 | C7-002 失效面 |
| 3 | `nop_stream_msg_queue` 表行（INSERT-only 消息后端） | runtime `launch/PollingJdbcMessageService.java:176-186` | launch 模式下每条控制面/数据面消息 | 仅测试清理口 `clearTopics`（:152）；无运行时保留 | **缺陷 C7-003（P2，含已声明边界分析）** |
| 4 | `TaskMailbox.controlQueue`（timer-fire mail 重复投递） | core `execution/TaskMailbox.java:22-27` + `ProcessingTimeServiceDriver.java:116-133` | 任务线程被背压/用户代码阻塞期间，每 100ms 一封 | 解除阻塞后排空；无在途去重 | **缺陷 C7-004（P2）** |
| 5 | 4 个周期循环的失败日志（无抑制） | C7-005 证据 | 持续故障/僵尸期间按调度节奏刷 | 无 | **缺陷 C7-005（P2）** |
| 6 | `SideOutputRegistry.CONSUMERS`（static，register-only） | core `datastream/SideOutputRegistry.java:32` | 每个 side-output tag 注册一次 | `remove` 无生产调用方；`clear` 仅测试 | **缺陷 C7-006（P2）** |
| 7 | `MicrometerStreamTaskMetrics.TASK_HOLDERS` 条目 + per-task meters | core `metrics/MicrometerStreamTaskMetrics.java:58,72-75` | 每个新 (jobId,vertexId,subtaskIndex) 注册约 11 个 meter | `releaseHolder` 仅置空值不移除条目/meter | **缺陷 C7-007（P2）** |
| 8 | `WebhookAlertChannel.delivered` | runtime `alert/WebhookAlertChannel.java:67,180` | 每次投递追加 | 无清理 | **缺陷 C7-008（P3）** |
| 9 | `InMemoryClusterRegistry.coordinators` | runtime `cluster/InMemoryClusterRegistry.java:31` | 每个曾注册的 jobId 一条 | 无删除 API；键空间=jobId 数 | 有界（作业数），不立项 |
| 10 | `JobCoordinator.barrierFanoutExecutors` | runtime `coordinator/JobCoordinator.java:159,2932-2935` | 每个 nodeId 一个单线程执行器 | stop 时全部 shutdown（:723-735）；键空间=节点数 | 有界，不立项 |
| 11 | `PollingJdbcMessageService.subscriptions` 键 | runtime `launch/PollingJdbcMessageService.java:84` | 每个 topic 键一次 | cancel 时从 list 移除（键本身留存）；键空间=edge/topic 数 | 有界，不立项 |
| 12 | `CheckpointCoordinator.checkpointSuccessMap` / `failedCommitParticipants` | runtime `checkpoint/CheckpointCoordinator.java:281,278` | 每 epoch 写入 | 成功路径 :1009 / 失败路径 :1066-1068 / 重试收敛 :1555-1557 成对移除 | 有界，不立项（重试滞留面归入 C7-005） |
| 13 | `CheckpointCoordinator.checkpointSegments` / `checkpointHistory` / `pendingCheckpoints` | 同上 :165,167,274 | 每 epoch | retention GC / 100 条上限+治理裁剪 / 完成-中止移除 | 有界，不立项 |
| 14 | `nop_stream_node` 死节点行（JDBC） | runtime `cluster/JdbcClusterRegistry.java` | 节点死亡后行留存（仅租约过期使其不可见） | 无删除；键空间=曾用 nodeId 数 | 有界（nodeId 稳定），不立项 |
| 15 | `JobCoordinator.subtaskLiveness` / `completedSubtaskKeys` / `deployIssuedAtMs` | runtime `coordinator/JobCoordinator.java:245,260,296` | 每任务键 | 完成移除（:1124,1128）/ `globalRecovery` 全清（:2023-2030） | 有界，不立项 |
| 16 | `SupervisionLoop.regionRestartCounts` | runtime `execution/SupervisionLoop.java:284` | 每 region 一个 AtomicInteger | 键空间=region 数；值受 `maxRestartsPerRegion` 预算约束 | 有界，不立项 |
| 17 | `CachingTableLookup.cache` 冷键常驻刷新 | core `connector/lookup/CachingTableLookup.java:211-227` | 刷新续 loadNanos → 冷键永不淘汰（上限 maxEntries 内） | maxEntries 硬上界 | 有界；冷键慢刷新为已声明语义，P3 候选不立项 |
| 18 | `StreamElementCodec.DECODED_VALUE_TYPES` | core `execution/transport/StreamElementCodec.java:63` | 每个新值类型名 | 键空间=类型数 | 有界，不立项 |
| 19 | fencing epoch / checkpoint id counter（long 单调递增） | runtime `coordinator/JobCoordinator.java:2132`、core `CheckpointIDCounter` | 每 epoch/checkpoint +1 | long 溢出需 9.2e18 次——按默认 1s checkpoint 间隔需 ~2.9e11 年 | 溢出不可达，不立项 |

## 挂死点清单（F5 定向）

| # | 等待点 | 超时/中断 | 健康检测可见性 | 判定 |
|---|---|---|---|---|
| 1 | 任务线程卡死（用户代码/算子），不 tick | — | `markActivity` 停止 → `subtaskLiveness` 过期 → `detectFailures` TASK_STALL → 恢复 → cancel 中断 | **已覆盖** |
| 2 | 任务线程阻塞在 emit/背压（`BufferPool.acquire`、remote sendLock） | Semaphore 可中断 | 不 tick → 同上；emit 耗时经 `nop.stream.io.emit.time` 直接可观测 | **已覆盖** |
| 3 | `InputGate.read`（本地通道） | 有界 poll（IDLE_RETURN_THRESHOLD），idle 返回循环顶部排 mailbox | idle 切片继续 tick | **已覆盖** |
| 4 | `RemoteInputChannel` 满队列入队 | 有界 offer 窗口超时 → typed `ERR_STREAM_CHANNEL_OVERFLOW` | typed 失败上抛任务失败 | **已覆盖** |
| 5 | barrier alignment | 30s fail-fast 超时抛错 | 失败可见 | **已覆盖** |
| 6 | SOURCE 任务阻塞（限流器/无数据轮询） | 无（设计如此） | SOURCE 按声明上报 TM 墙钟，**活性-进度解耦**：源侧"不出数据也不报错"对 stall 检测不可见——owner doc 明文声明（合法空闲期不得误杀），属已声明边界；用户 source 真挂死 = 边界内盲区 | 已声明边界，不立项 |
| 7 | enumerator 卡死（用户 split 分配代码） | 无 | 同上（活性解耦）；下游 idle 不 tick 但 idle 是合法态 | 已声明边界，不立项 |
| 8 | JDBC / elector / webhook 等 I/O | 平台 datasource socket 超时 / 32 次有界预算 / HTTP 5s | 失败按路径日志/计数可见 | **已覆盖** |
| 9 | 周期调度循环被 Error 类 throwable 杀死（failureDetector、barrier-injector、periodic-checkpoint driver） | 无守卫（catch Exception 不含 Error） | 死亡后检测/注入面**静默消失**（completed counter 走平需外部告警发现） | **缺陷 C7-009（P3，理论）** |
| 10 | `TaskMailbox.take`/`wait` | close 唤醒；主循环实际走 poll 路径 | — | **已覆盖** |
| 11 | 时钟回拨/跳变对 lease/stall 判定的扰动 | — | 见 C7-010 | **缺陷 C7-010（P3，理论）** |

---

## 发现条目

### [nop-stream-core-runtime-C7-001] 物化 bypass 库 InMemoryMaterializationPoint 只增不删，materialization-enabled 边的连续流必然无界增长

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/materialization/InMemoryMaterializationPoint.java:41,61-72`（连带 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java:392-402`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:293-328,346-376`）
- **证据片段**:
  ```java
  // InMemoryMaterializationPoint.java
  private final List<MaterializedElement> store = new ArrayList<>();
  ...
  public synchronized void write(StreamElement element, long epoch) throws InterruptedException {
      if (sealed) { throw new StreamException(ERR_STREAM_MATERIALIZE_POINT_SEALED)...; }
      store.add(new MaterializedElement(element, epoch));   // 追加，无任何删除路径
      ...
  }
  // 类注释（:27-36）："the bypass write does not block on capacity (in-memory list
  //  grows unboundedly); global memory pressure is expected to be controlled
  //  upstream by the IBufferPool on the main queue."
  ```
  `IMaterializationPoint` 接口仅有 `write/replay/replayAll/seal/isSealed/size/getLastEpoch`——**没有 ack/trim/discard**；`ResultPartition.write` 对每条数据记录双写（`dualWriteToMaterialization`，:293-297），且 queue-full 时 overflow-bypass 把记录**只**写入该库并释放池许可（:349-356）——恰好在该生效时绕过了本应约束内存的 bufferPool。
- **严重程度**: P1
- **并发场景**: 生产者任务线程（`ResultPartition.write`）与恢复期消费者/监督线程（`SupervisionLoop.restartRegion` 的 `replayAll()`）并发访问同一 synchronized store；增长本体是单线程数据面持续写入。
- **触发时序**: T1 = 作业启用 region 边界（`JobEdge.setMaterializationEnabled(true)`，这是获得 region-scoped restart 的唯一途径）且数据为连续流；T2 = 每条记录经 `ResultPartition.write:296` 双写进 `store`，无消费后裁剪；T3 = region 重启后 `SupervisionLoop` 复用**同一批** ResultPartition + materialization point（`SupervisionLoop.java:438-458` 注释明示），旧元素继续驻留并继续累积；T4 = 长时间运行后堆随吞吐×运行时长线性增长直至 OOM（下游变慢时 overflow-bypass 还会把本应受池约束的记录全部改道进该库，加速增长）。
- **现状**: 全 append-only，唯一的"清理"是 seal（拒绝写入），无按 watermark/epoch/确认点的裁剪。
- **风险**: 声明包络内（连续流作业、region 重启特性按设计意图用于长时运行）必然触发内存耗尽；region 重启特性因此无法用于生产连续流。类注释声称由 IBufferPool 控制上游压力，但 overflow-bypass 路径恰恰绕过该池，声明与实现矛盾。
- **建议**: 为 `IMaterializationPoint` 增加 `discardUpTo(epoch/seq)` 契约，在消费者确认（或 durable checkpoint 完成、replay 完成后）裁剪 `store.head`；中期按类注释声明的方向落 RocksDB/磁盘物化（复用 `nop-stream-rocksdb` 状态后端）；短期至少在 `replay` 消费完成与 checkpoint 完成两个点上对已耗元素做 head-trim。
- **失效面枚举**（F3）: `IMaterializationPoint` 实现全仓唯一 = `InMemoryMaterializationPoint`（grep 证实），无兄弟实现可漏修；同族"append-only 账本"= C7-002（task assignment 账本）、C7-003（msg_queue 表），均已分别立项；`PerKeyOrderedBuffer` 有 `trimToWatermark/trimToCount` 成对裁剪（正面对照，无需修）。
- **信心水平**: 确定
- **误报排除**: 类注释把无界声明为"correctness-first + 生产级物化延后"的已声明边界，但 (1) owner doc 未声明该限制，(2) 所声明的上游内存控制（IBufferPool）被 overflow-bypass 路径自身绕过，声明与实现不一致，(3) 触发条件是特性（materialization/region restart）的正常使用而非误用，不符合"文档明文声明 + fail-fast 守卫"的误报标准。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C7-002] 任务尝试账本只增不删：`nop_stream_task_assignment` 行与内存 `taskAssignmentHistory` 均为 append-only，`removeTaskAssignment` 无任何生产调用方

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcClusterRegistry.java:214-230,274-288`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/InMemoryClusterRegistry.java:44,149-166,190-192`
- **证据片段**:
  ```java
  // JdbcClusterRegistry.assignTask —— G56 注释："preserve attempt history. Do NOT
  // delete prior rows ... INSERT a new row with an incremented attempt_number."
  SQL insertSql = SQL.begin().name("insertTaskAssignment").querySpace(querySpace)
          .sql("INSERT INTO " + TASK_ASSIGNMENT_TABLE +
                          " (job_id, vertex_id, subtask_index, node_id, attempt_id, fencing_token, assigned_at, attempt_number)" +
                          " VALUES (?,?,?,?,?,?,?,?)", ...)
          .end();
  // JdbcClusterRegistry.removeTaskAssignment（:275-288）："used by globalRecovery clear"
  ```
  全仓 grep（core+runtime main sources）证实 `removeTaskAssignment` **零生产调用方**——仅测试桩实现/覆写它；`InMemoryClusterRegistry.removeTaskAssignment`（:190）同样无调用方。G56 注释声称的 "used by globalRecovery clear" 与 live code 不符（`JobCoordinator.globalRecovery` 不清理 registry 账本）。
- **严重程度**: P2
- **并发场景**: `JobCoordinator.assignTasks`/恢复重派（coordinator 控制线程）在每次 attempt 时追加行；JDBC 侧多 JVM 并发 INSERT 受 PK `(job_id,vertex_id,subtask_index,attempt_number)` 约束；内存侧 append 在 `synchronized(taskAssignmentHistory)` 内（本身无竞态）。
- **触发时序**: T1 = 任务反复失败/恢复（flapping 任务、毒记录循环、演练期反复 kill-recover）；T2 = 每次 attempt 追加一行/一个列表元素，永不删除；T3 = 账本跨 coordinator 重启持续存在（JDBC 平面持久），随运维周期（月级）无界累积；T4 = `getAttemptHistory` 的 `SELECT ... ORDER BY attempt_number ASC` 无 LIMIT，行数增长后单次读取成本随之上升。单作业生命周期内受 `maxRestarts=3`（默认）约束——增长主要来自跨代际/跨作业的长期累积。
- **现状**: 清理 API 存在但未接线；注释宣称的清理路径（globalRecovery clear）不存在。
- **风险**: 长运行集群（flapping 作业 + 定期重提交）下账本无界增长：DB 行累积、历史查询变慢；内存 registry 在多作业宿主中随作业churn累积；账本与真实状态漂移还可能误导按历史诊断的运维动作。
- **建议**: 接线 `removeTaskAssignment` 到 `globalRecovery`/作业终态清理路径（与注释声明对齐），或为 JDBC 平面加保留策略（如仅保留最近 N 个 attempt 或按 `assigned_at` TTL，复用 `OpsJobManager` 治理清扫节奏）；至少修正 G56 注释使声明与实现一致。
- **失效面枚举**（F3）: 同族账本全清单——① `JdbcClusterRegistry` 表（本条）；② `InMemoryClusterRegistry.taskAssignmentHistory`（本条，同 API 同因）；③ `InMemoryClusterRegistry.coordinators`（键空间=jobId 数，有界，不立项）；④ `nop_stream_node` 死行（键空间=nodeId 数，有界，不立项）；⑤ checkpoint/epoch manifest 平面（`RetentionCleaner` 已闭合，正面）；⑥ `nop_stream_msg_queue`（独立成因，C7-003）。
- **信心水平**: 确定（"无调用方"为全仓 grep 实证）
- **误报排除**: append-only 本身是 G56 特性决策（保留尝试史供 fencing/审计），缺陷不在"保留历史"而在"清理路径声明存在但未接线 + 无任何保留上限"——符合 F4"只增不删"画像而非设计意图误报。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C7-003] `PollingJdbcMessageService` 队列表 INSERT-only 无运行时保留路径，而它被生产 launch 入口（JobCoordinatorMain/TaskManagerMain）装配

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java:151-156,176-186`（装配点 `launch/JobCoordinatorMain.java:134`、`launch/TaskManagerMain.java:101-104`）
- **证据片段**:
  ```java
  /** Drops all rows for topics starting with {@code prefix}. Test-cleanup helper. */
  public void clearTopics(String prefix) {           // 唯一删除入口，注释明示 test-cleanup
      jdbcTemplate.executeUpdate(SQL.begin()
              .sql("DELETE FROM " + queueTable + " WHERE topic LIKE ?", prefix + "%")
              .end());
  }
  @Override
  public void send(String topic, Object message, MessageSendOptions options) {
      ...
      jdbcTemplate.executeUpdate(SQL.begin()
              .sql("INSERT INTO " + queueTable
                      + " (topic, payload, created_at_ms) VALUES(?,?,?)", topic, payload, System.currentTimeMillis())
              .end());
  }
  ```
- **严重程度**: P2
- **并发场景**: 多个 JVM（JC/TM 进程）的发送线程并发 INSERT；各订阅的 50ms 轮询线程 SELECT（`pollIntervalMs` 默认 50，`TaskManagerMain.java:101`）；无删除方。
- **触发时序**: T1 = 按 owner doc「运维手册」以 launch 模式拉起跨 JVM 集群（`java -cp ... JobCoordinatorMain jdbcUrl=...`）；T2 = 控制面 RPC 与数据面记录全部经 `send()` INSERT（数据面经 `DataPlaneMessageServiceAdapter` 走同一 IMessageService）；T3 = 消费只推进订阅方内存 cursor（`lastSeenId`），行永不删除；T4 = 表随吞吐×运行时长线性增长（BLOB payload），演练/试运行数天即达 GB 级，查询 `WHERE topic=? AND id>?` 虽有 (topic,id) 索引但表膨胀拖慢扫描与磁盘。
- **现状**: 仅测试清理口；无 TTL/归档/消费后删除。
- **风险**: launch 模式长时运行的磁盘耗尽与查询退化；同时该表是 owner doc 声明的积压观察代理，无界膨胀使 COUNT 代理失真。
- **建议**: 若维持 launch 模式为演练定位：在 owner doc/类注释明示"表需外部清理"并给保留 SQL；若要支撑生产：按订阅方确认位点做批量删除（`DELETE ... WHERE topic=? AND id<=min(lastSeenId)`，注意 fan-out 多订阅取 min），或复用治理清扫周期执行。
- **失效面枚举**（F3）: append-only 队列/账本族 = C7-001（物化库）、C7-002（task assignment）、本条；SysDao 数据面（`SysDaoMessageService`，WAITING/CLAIMED 行模型，含 `ChannelBacklogProbe`）不在本模块范围（transport 适配层 `DataPlaneMessageServiceAdapter` 仅探测，无行清理职责）；`subscriptions` 键表有界不立项。
- **信心水平**: 确定
- **误报排除**: 类 javadoc（:42-44,66）与 owner doc 均声明其"test-infrastructure / INSERT-only 对照代理"定位——属部分已声明边界；但 ship 的唯一跨 JVM 路径（launch 入口，owner doc 称 main-scope 生产入口）在无替代装配的情况下默认落到它，"声明测试定位"与"生产入口装配"互相矛盾，故按 P2 登记为部署卫生缺陷而非纯误报；若复核裁定 launch 模式整体为非生产面，可降 P3。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C7-004] `ProcessingTimeServiceDriver` 在任务线程被阻塞期间每 100ms tick 重复投递 timer-fire mail，`TaskMailbox` 无界积压

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ProcessingTimeServiceDriver.java:116-133`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/TaskProcessingTimeService.java:60,101-103`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/TaskMailbox.java:22-27`
- **证据片段**:
  ```java
  // ProcessingTimeServiceDriver.run() —— 无在途 mail 去重
  while (!stopped) {
      long now = CoreMetrics.currentTimeMillis();
      boolean due = processingTimeService.isTimerDue(now)
              || (timeServiceManager != null && timeServiceManager.hasProcessingTimeTimersDue(now));
      if (due) {
          mailbox.put(Mail.control(this::fireDueTimersOnTaskThread, "processing-time-timer-fire"));
      }
      try { Thread.sleep(tickMs); } ...          // tickMs 默认 100
  }
  // TaskProcessingTimeService：nextTimerTimestamp 仅由任务线程在 fireDueTimers/
  // cancelRegistration 中重算（volatile long，初值 Long.MAX_VALUE）
  public boolean isTimerDue(long now) { return nextTimerTimestamp <= now; }
  // TaskMailbox：controlQueue = new ConcurrentLinkedQueue<>()（无界）
  ```
- **严重程度**: P2
- **并发场景**: `processing-time-driver` 调度线程（投递方）× 任务主循环线程（消费方）。任务线程被下游背压阻塞（`BufferPool.acquire`/remote sendLock）或卡在慢用户函数（如 JDBC sink 写）时，mailbox 无人在安全点排空。
- **触发时序**: T1 = 任务注册了 processing-time timer（如 `TimestampsAndWatermarksOperator` 周期 watermark timer，`scheduleNextWatermarkTimer` 持续再注册）；T2 = 下游故障，任务线程阻塞在 emit，`fireDueTimers` 无法执行，`nextTimerTimestamp` 保持过期值；T3 = driver 每 100ms 判定 `isTimerDue()==true` 且 `due` 分支**无"已投递未消费"标记**，再投一封 fire mail；T4 = 阻塞持续 D 毫秒即积压 D/100 封 mail（10 封/秒 ≈ 86 万封/天，每封 Mail+Runnable 约 100B 量级 → 约 100MB/天量级堆占用）；T5 = 阻塞解除后任务线程一次性排空数千至数十万封 mail，除首封外全为 no-op fire（`fireDueTimers` 对空表直通），造成无谓的恢复抖动。
- **现状**: driver 只有"不到期不投递"的空闲抑制，没有"已投递待消费"的在途去重；`TaskProcessingTimeService` 类注释自己也写明 stale volatile 读的后果上限是"an extra no-op fire mail at most"——该论证只覆盖正常消费节奏，未覆盖任务线程长时间不消费的场景。
- **风险**: 长时间背压（小时/天级 sink 故障是流系统常态）下的堆膨胀与解除后的 mail 风暴；内存无界属 F7 家族在控制面的形态。
- **建议**: driver 增加在途标记（投递后置位，`fireDueTimersOnTaskThread` 完成时经 volatile/AtomicBoolean 复位）实现至多一封在途；或给 `TaskMailbox` 每优先级加软上限 + 重复 mail 合并（复用平台 `IBlockingQueue`/`QueueOverflowPolicy` 语义）。
- **失效面枚举**（F3）: 同一投递形态的兄弟路径 = ① `TaskProcessingTimeService.isTimerDue` 直读路径（本条主证据）；② `TimerServiceManager.hasProcessingTimeTimersDue`（HeapInternalTimerService 路径，`ProcessingTimeServiceDriver.java:120-121` 同一 `due` 判定的右操作数，同样每 tick 重复投递）；③ SOURCE `collect()` 安全点的 mailbox 排空（`StreamSourceOperator.java:335`）与 `SourceReaderOperator.java:389`——消费侧无积压，非缺陷点。`fireDueTimers` 异常传播（fail-fast）已有 owner doc 契约背书，不在本条范围。
- **信心水平**: 确定（机制实证；量级为估算链）
- **误报排除**: 不是 C8 背压语义问题（数据面有界队列/overflow 属 C8），本条是控制面 mail 的**无界重复投递**；也不是"volatile 陈读多一封 mail"的已声明无害态——声明的前提（任务线程随后即消费）在长阻塞下不成立。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C7-005] 持续失败路径的周期性错误日志无抑制，故障期日志放大可占满日志面

- **文件**: 主证据 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java:305-338`；同族 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:447-456`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1230-1235`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1543-1551`
- **证据片段**:
  ```java
  // PollingJdbcMessageService.Subscription.poll() —— 每 50ms/订阅 执行一次
  } catch (Exception e) {
      LOG.error("Poll failed for topic={}", topic, e);   // 带完整堆栈，无抑制/去重
  }
  // TaskManager.heartbeat() —— 每 5s 一次（DEFAULT_HEARTBEAT_INTERVAL_MS）
  } catch (Exception e) {
      LOG.error("Heartbeat lease renewal failed for node {}", nodeId, e);
  }
  ```
- **严重程度**: P2
- **并发场景**: 各订阅的轮询线程（每 topic 一个 schedule 任务）× TM 心跳线程 × coordinator 检测/重试线程——故障期间多线程并行刷日志。
- **触发时序**: T1 = 共享 DB 计划内重启/网络抖动（分钟级维护窗）；T2 = 每个订阅的 `poll()` 每 50ms 抛异常并 LOG.error 一条**含完整堆栈**（每条约 1-2KB）；T3 = 分布式作业订阅数 = 控制面 topic（每 TM 1-2 个）+ 数据面通道（每入边×subtask，数十个量级）；以 20 个订阅计 = 20 × 20 条/s = 400 条/s ≈ 140 万条/小时（数百 MB 日志）；T4 = 日志后端磁盘/采集管道被故障噪声占满，真实故障信号被淹没，极端下磁盘写满反噬 JVM。
- **现状**: 四处周期失败日志均为无条件全量输出，无 first-N/退避/去重抑制；异常围栏本身正确（不会杀调度线程，不立项 F9）。
- **风险**: 故障可观测性目标（owner doc 的日志检索锚点体系）在最需要它的故障窗口失效；日志面资源耗尽。
- **建议**: 为周期失败日志加抑制（首条全量 + 后续按 30s/计数窗口聚合摘要，恢复时输出一条 cleared），或引入带键的 rate-limited logger（平台无现成设施时可局部实现 AtomicLong lastLoggedAt 门控）。
- **失效面枚举**（F3）: 全部同族周期失败日志点——① `PollingJdbcMessageService.poll`（50ms 节奏，最重）；② `TaskManager.heartbeat` 租约续期失败 ERROR（5s 节奏）；③ `JobCoordinator.reportNodeTaskLiveness` 的 stale-attempt 心跳拒绝 WARN（`JobCoordinator.java:1230-1235`，僵尸任务存活期间按心跳节奏每子任务每 5s 一条，僵尸存活数小时 = 3600×subtask 条）；④ `CheckpointCoordinator.retryFailedCommits` 仍失败 WARN（`CheckpointCoordinator.java:1548-1550`，按 checkpoint 完成节奏，默认 1s 间隔）；⑤ `PollingJdbcMessageService.poll` 内消费失败 `LOG.error("Consumer onMessage failed...")`（:331，同 poll 节奏）。①②③④⑤ 逐一登记，均无抑制。
- **信心水平**: 确定（机制实证；速率为按默认参数的估算链）
- **误报排除**: 不是"错误被吞"（F9 不适用——异常都被正确围栏且状态一致）；owner doc 的"监听器异常捕获记录"契约针对的是监听器主路径保护，不豁免日志量治理；日志放大是 C7 正文第 1 步明文列出的检查形态。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C7-006] `SideOutputRegistry` JVM 级静态注册表 register-only：`remove` 无生产调用方，长期存活 JVM 中跨作业累积并强引用已停作业的算子链

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SideOutputRegistry.java:32,40-48,64-75`（注册点 `datastream/SideOutputDataStream.java:90`）
- **证据片段**:
  ```java
  private static final Map<String, Consumer<StreamRecord<?>>> CONSUMERS = new ConcurrentHashMap<>();
  ...
  public static void register(String tagId, Consumer<StreamRecord<?>> consumer) {
      ...
      CONSUMERS.put(tagId, consumer);        // 注册或替换，永不自动清除
  }
  // remove(tagId)/clear() 仅测试调用；类注释："tests can clear the registry between jobs"
  ```
- **严重程度**: P2
- **并发场景**: 作业构建/启动线程注册；运行期 `ChainingOutput`/`StreamTaskInvokable` 读取（并发安全本身无问题）。增长是跨作业生命周期的慢性累积，非单作业内竞态。
- **触发时序**: T1 = 宿主 JVM（嵌入式多作业宿主/长存活应用进程）中第 N 个作业执行 `getSideOutput(tag).sink(...)` → `SideOutputDataStream:90` 注册 consumer；T2 = 作业停止，无任何路径调用 `remove(tagId)`；T3 = 条目连同 consumer 闭包强引用该作业的算子链/用户函数驻留 static map；T4 = 作业反复（重新）提交（jobId 不同但 tagId 形如用户语义名，或同 tagId 被新一代 consumer 替换——替换释放旧引用，新 tagId 则净增）→ 条目数随作业执行次数单调增长。
- **现状**: 生命周期与作业未对齐（F8 清理不对称：register 无成对 unregister）。
- **风险**: 长存活宿主中内存随作业churn缓慢累积（每条目引用整条算子链，非小对象）；残留 consumer 还构成跨作业语义污染面（tagId 命名冲突时新作业可能读到旧作业注册的 consumer——javadoc 声明 JVM-local 作用域即默认该场景存在）。
- **建议**: 在任务/作业 teardown 路径（`StreamTaskInvokable` close 或 `JobCoordinator.stop`）对本次执行注册的 tagId 调用 `SideOutputRegistry.remove`（注册点已有 `SideOutputDataStream`，可在构建期收集 tagId 集合统一注销）；或在注册值上包一层与作业实例关联的弱句柄。
- **失效面枚举**（F3）: register/remove 配对面全查——`register` 唯一生产调用点 `SideOutputDataStream:90`；`remove` 生产调用点 = 0（全仓 grep）；`clear` = 测试专用。对照正面样本：`MicrometerStreamTaskMetrics` 有 releaseHolder（虽不完整，见 C7-007）、`EngineMetrics`/`TaskNodeMetrics` 有成对 release——本条是同族中唯一完全没有生产清理路径的。
- **信心水平**: 确定（调用面穷举）
- **误报排除**: javadoc 声明 "Registering again for the same tag id replaces the previous consumer" 只说明替换语义，未声明"条目允许跨作业滞留"；`clear()` 被明确定位为 test isolation helper，即生产无清理属遗漏而非设计。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C7-007] `MicrometerStreamTaskMetrics` 释放不对称：`releaseHolder` 只置空不删条目，per-task meters（约 11 个/任务）永驻进程注册表——同族 `EngineMetrics`/`TaskNodeMetrics` 均有完整释放

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/metrics/MicrometerStreamTaskMetrics.java:58,68-89,121-131`（对照 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/metrics/EngineMetrics.java:100-127`、`TaskNodeMetrics.java:179-205`、调用点 `execution/task/StreamTaskInvokable.java:824`）
- **证据片段**:
  ```java
  private static final ConcurrentMap<String, AtomicReference<MicrometerStreamTaskMetrics>> TASK_HOLDERS = ...;
  // 构造器：每任务注册 counters×4 + timers×2 + gauges×5（按 jobId|vertexId|subtask 标签）
  this.recordsIn = registry.counter(METRIC_OPERATOR_RECORDS_IN, tags); ...
  public void releaseHolder() {                       // 任务 close 时调用
      AtomicReference<...> holder = TASK_HOLDERS.get(myHolderKey());
      if (holder != null) { holder.compareAndSet(this, null); }   // 仅置空值
  }                                                    // 不移除 TASK_HOLDERS 条目，不 registry.remove 任何 meter
  ```
- **严重程度**: P2
- **并发场景**: 任务关闭线程（`StreamTaskInvokable` invoke finally）执行 release；Prometheus 抓取线程并发读取注册表。非竞态缺陷，是生命周期不对称（F8）。
- **触发时序**: T1 = 进程内作业以**不同 jobId** 反复执行（OpsJobManager 多作业宿主的作业churn、治理清扫删除终态记录后重提交、嵌入式宿主顺序跑多作业）；T2 = 每个新 (jobId, vertexId, subtaskIndex) 三元组净增约 11 个 meter + 1 个 `TASK_HOLDERS` 条目；T3 = `releaseHolder` 后 gauge 读 NaN 但 meter 全部留存；T4 = `/metrics` 抓取体与注册表随作业churn线性膨胀（单 jobId 复用场景键空间封闭，不增长）。
- **现状**: 值面释放（防弱引用 gauge 陷阱的 holder 置空）与面清理（meter 移除）被混为一谈。
- **风险**: 长存活多作业进程中指标基数无界增长（C7 明列的 label 基数爆炸形态）；抓取延迟与内存缓涨；Prometheus 面残留大量恒 NaN 序列污染查询。
- **建议**: 对齐 `EngineMetrics.releaseJob` 的做法：release 时 `registry.remove(each meter id)` 并 `TASK_HOLDERS.remove(key)`（同 jobId 重启场景由后续构造自然重建）；或在 owner doc 明示"任务面 meter 随进程存续"的声明边界并说明理由。
- **失效面枚举**（F3）: 指标注册/释放配对全查——① `EngineMetrics` BY_JOB：`releaseJob` 完整移除（`JobCoordinator.stop:759` 接线，正面）；② `TaskNodeMetrics` BY_NODE/RUNNING_GAUGE_REFS/COMMIT_QUEUE_GAUGE_REFS：`releaseNode` 完整移除（`TaskManager.stop:411` 接线，正面）；③ `ChannelQueueGauges.HOLDERS`：通道 close 释放绑定（owner doc D2c，正面）；④ `DbBacklogGauges.HOLDERS`：同 holder 模式（正面）；⑤ `MicrometerStreamTaskMetrics.TASK_HOLDERS` + per-task meters：**不完整**（本条）；⑥ `WebhookAlertChannel.delivered`（C7-008，同族不对称的轻量形态）。
- **信心水平**: 确定
- **误报排除**: `releaseHolder` 的 NaN 语义是 owner doc 声明的观测契约（"任务结束后 NaN"），本条不否定它；缺陷在于"值置空"被当作"资源释放"而 meter 面从未清理——与同族 ①-④ 的完整释放相比构成明确的不对称，非设计意图误报。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C7-008] `WebhookAlertChannel.delivered` 历史列表只增不减（低速慢性累积）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/alert/WebhookAlertChannel.java:67,131,180`
- **证据片段**:
  ```java
  private final List<AlertEvent> delivered = new CopyOnWriteArrayList<>();
  ...
  delivered.add(event);          // 每次投递（含重试投递）追加
  ...
  public List<AlertEvent> getDelivered() { return delivered; }   // 对外暴露全量
  ```
- **严重程度**: P3
- **并发场景**: webhook 投递单线程执行器追加；观测方经 `getDelivered()` 并发读（CopyOnWriteArrayList 保证安全，无竞态）。
- **触发时序**: 理论风险，未具体化到故障级——长存活进程中每次告警投递追加一条 AlertEvent；告警由故障事件驱动（JOB_FAILED/RECOVERY/JOB_DEGRADED），flapping 作业的长期故障循环下按事件节奏累积，进程生命周期内无清理。
- **现状**: append-only 观测列表，无上限、无快照截断。
- **风险**: 单条目小、事件节奏低，主要风险是数月级进程中的缓慢内存驻留与 `getDelivered()` 全量快照的瞬时放大；不构成独立故障源。
- **建议**: 改为有界环形缓冲（如保留最近 1000 条）或按时间窗裁剪；`getDelivered()` 返回只读视图。
- **失效面枚举**（F3）: 告警子系统增长面 = ① `pending` 投递队列：有界 256 + 满丢弃 WARN（正面）；② `delivered` 历史列表：无界（本条）；③ `AlertService` 路由表/渠道注册：静态配置，有界（正面）。同族"只增观测面"另见 C7-006/C7-007。
- **信心水平**: 确定（代码事实）；严重度按"低速慢性病"校准
- **误报排除**: 非测试代码；`getDelivered()` 是 public 对外面（非死代码），增长面真实存在。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C7-009] 周期调度循环对 Error 类 throwable 无守卫：failureDetector / barrier-injector / periodic-checkpoint driver 可静默死亡（F5 残留面）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:692-698,1583-1585`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/TaskCheckpointWiring.java:273-287`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1307-1325`
- **证据片段**:
  ```java
  // startFailureDetector()
  failureDetector.scheduleAtFixedRate(this::detectFailures, ...);
  // detectFailures() 的整方法围栏：
  } catch (Exception e) {
      LOG.error("Error during failure detection for job {}", jobId, e);
  }
  // barrier-injector（TaskCheckpointWiring.startBarrierScheduler）同形态：
  } catch (Exception e) {
      LOG.error("Failed to inject checkpoint barrier for job {}", jobId, e);
      coordinator.incrementTriggerFailures();
  }
  ```
- **严重程度**: P3
- **并发场景**: 单线程 `ScheduledExecutorService`（failureDetector / barrier-injector-\<jobId\> / jc-periodic-checkpoint-\<jobId\>）；任何逃逸出 catch(Exception) 的 Throwable（Error 族：OOM、StackOverflowError）按 JDK `scheduleAtFixedRate` 语义**永久终止**后续执行。
- **触发时序**: 理论风险，未具体化——T1 = 检测/注入循环执行中抛出 Error（如线程栈已深的 barrier tracker 递归触发 StackOverflowError，或临近堆极限时的 OOM）；T2 = catch(Exception) 不匹配，异常逃逸；T3 = 调度器静默停止：liveness/stall 检测面或 checkpoint 注入面永久消失，作业"不产出也不报错"（引擎内无 watchdog 检测"检测器自身死亡"；`isFailureDetectorAlive` 仅查询线程存活，无人周期调用告警）。
- **现状**: Exception 面已有每轮围栏（`OpsJobManager.governanceSweep` 注释亦确认该约定），Error 面无守卫且死亡不可自愈不可告警。
- **风险**: 监控的监控缺失——恰好是 F5"挂死对健康检测不可见"在检测器自身的投影；触发前提是 JVM 已处于深度退化态，故 P3。
- **建议**: 三处循环改为 catch(Throwable)（Error 分支记录后按团队裁定决定是否 rethrow OOM 类），或增加独立的"检测器活性"看门狗（复用 `nop.stream.engine.*` 指标面：检测器每轮心跳一个 gauge，外部告警走平即响）。
- **失效面枚举**（F3）: 引擎自建周期循环全清单——① `JobCoordinator` failureDetector（本条）；② `TaskCheckpointWiring.startBarrierScheduler`（本条，LOCAL 路径）；③ `JobCoordinator.startPeriodicCheckpoints` 驱动（:1307-1325，同形态）；④ `TaskManager.heartbeat`（:442-447 注释明示按 JDK 语义围栏，catch 同为 Exception——同族同缺口）；⑤ `OpsJobManager.governanceSweep`（:358-369，Exception 围栏，同缺口）；⑥ `StreamMetricsReporter.report`（同形态）；⑦ `PollingJdbcMessageService.poll`（:335 同形态）。①-⑦ 全部只捕 Exception；本条以 ①②③（检测/注入面，死亡后果最重）立项，④-⑦ 并入同族清单。
- **信心水平**: 很可能（机制确定；触发依赖 Error 类事件，未构造具体可复现序列）
- **误报排除**: "catch Exception 是代码库通行围栏约定"成立，故不按 Exception 漏捕报缺陷；本条仅针对 Error 逃逸后**调度静默死亡且无活性自检**的组合——这是 F5 明文的定向检查形态（"不产出也不报错路径是否有看门狗"），非原语使用挑剔。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C7-010] 租约/停顿/冷却全部基于墙钟 `CoreMetrics.currentTimeMillis`，时钟跳变下批量误判或漏检

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:320,1503-1504,1637-1644`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/InMemoryClusterRegistry.java:106-107`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcClusterRegistry.java:152-162`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java`（心跳时间戳）
- **证据片段**:
  ```java
  // JobCoordinator
  private volatile java.util.function.LongSupplier clock = CoreMetrics::currentTimeMillis;  // 墙钟
  ...
  long now = clock.getAsLong();
  long cutoff = now - taskTimeoutMs;               // DEFAULT_TASK_TIMEOUT_MS = 60s
  // InMemoryClusterRegistry.getNodeLease
  boolean active = expireAt > now;                 // expireAt = 续租时刻墙钟 + TTL(15s)
  ```
- **严重程度**: P3
- **并发场景**: TM 心跳线程写入时间戳、coordinator 检测线程读取比较——跨 JVM 墙钟（多 JVM 模式下各进程时钟独立，依赖 NTP 对齐）。
- **触发时序**: 理论风险，未具体化到可复现故障——（a）向前跳变 > TTL/taskTimeout（NTP 步进矫正、VM 迁移恢复）：全部活跃节点/任务瞬间过期 → 一次全网 global recovery（fencing 保护下可收敛，代价是集群级无谓重启）；（b）向后跳变 > TTL：`lastProgress` 与 `expireAt` 同用旧钟记录、cutoff 随 now 下移，检测被推迟直至墙钟追上，期间真挂死漏检（≤ 跳变量）。两向均自限（恢复机制本身兜底），无数据损坏路径。
- **现状**: 活性/租约/冷却（`stallRecoveryCooldownMs`）统一墙钟；WI16 时间片与 `CachingTableLookup` 已正确使用 `nanoTime`（单进程单调语义）。
- **风险**: 时钟运维事件（跳变/大步进同步）引发一次集群级恢复风暴或短窗漏检；多 JVM 部署对 NTP 的隐性强依赖未在任何 owner doc 声明。
- **建议**: 跨进程语义保留墙钟（租约本就需要可比对的公共钟），但 (1) 在 owner doc 部署要求中显式声明 NTP 对齐 + 跳变影响；(2) 单进程内可混入 `System.nanoTime` 做二次确认（跳变时以单调钟差分复核再触发恢复，避免单点跳变直接引爆全局恢复）。
- **失效面枚举**（F3）: 墙钟依赖点全清单——① `JobCoordinator.detectFailures` cutoff（本条）；② `JobCoordinator.requestRecovery` stall 冷却（:1637-1644，跳变→冷却窗口失真，后果轻）；③ `InMemoryClusterRegistry` 租约判定（本条同族）；④ `JdbcClusterRegistry.renewLease`（:152-162，JDBC 平面同族）；⑤ `PendingCheckpoint`/checkpoint timeout（`timeoutScheduler`，超时窗口在跳变下伸缩一次，自限）；⑥ `deployIssuedAtMs` 宽限窗（同 ①）；⑦ 单进程 nanoTime 使用点（`CachingTableLookup`、WI16 时间片、`MicrometerStreamTaskMetrics` 分母）——正确，不在缺陷面。
- **信心水平**: 很可能（机制确定；触发依赖具体时钟运维事件，未具体化为固定序列）
- **误报排除**: 不按"用了墙钟"泛报：租约/跨进程活性用墙钟是分布式系统常规且大体必要；立项点是多 JVM 隐性 NTP 依赖未声明 + 跳变可直接引爆集群级恢复且无复核机制，属 C7 第 5 步明文检查项。
- **复核状态**: 已保留

---

## 覆盖重点文件/包

- runtime：`coordinator/JobCoordinator`（活性/失败检测/恢复/周期任务/清理路径全查）、`checkpoint/CheckpointCoordinator` + `RetentionCleaner` + `PendingCheckpoint`、`checkpoint/storage/JdbcCheckpointStorage` + `LocalFileCheckpointStorage`（保留/删除面）、`cluster/InMemoryClusterRegistry` + `JdbcClusterRegistry` + `JdbcLeaderElector` + `StreamNodeAutoRegistration`、`execution/SupervisionLoop` + `TaskCheckpointWiring` + `RpcDistributedExecutor`、`taskmanager/TaskManager`（心跳/stop/执行器治理）、`launch/PollingJdbcMessageService` + `JobCoordinatorMain` + `TaskManagerMain`、`ops/OpsJobManager` + `StreamOpsHttpServer` + `StreamMetricsReporter`、`alert/WebhookAlertChannel`、`metrics/EngineMetrics` + `TaskNodeMetrics` + `transport/RemoteInputChannel`。
- core：`execution/materialization/InMemoryMaterializationPoint` + `ResultPartition` + `GraphExecutionPlan`、`execution/ProcessingTimeServiceDriver` + `TaskProcessingTimeService` + `TaskMailbox` + `MailboxExecutor` + `task/StreamTaskInvokable`（主循环/挂死可见性）、`common/functions/sink/TwoPhaseCommitSinkFunction`、`common/buffer/PerKeyOrderedBuffer`、`connector/lookup/CachingTableLookup`、`datastream/SideOutputRegistry`、`metrics/MicrometerStreamTaskMetrics`、`checkpoint/incremental/SharedStateRegistryImpl`、`operators/windowing/WindowOperator`（窗口 GC）、`operators/HeapInternalTimerService`、`execution/buffer/BufferPool`。

统计：P0 × 0；P1 × 1（C7-001）；P2 × 6（C7-002…C7-007）；P3 × 3（C7-008…C7-010）。

# C7 长时运行稳定性 — nop-stream-core-runtime（第 2 轮深挖追加）

- 审计日期：2026-10-14
- 审计维度：C7 第 2 轮深挖——第 1 轮已覆盖单调增长源（19 项）与挂死点（11 项）清单（见同目录 `C7-long-running.md`），本轮只补盲区：metrics 标签基数、重试风暴残留、自愈后旧状态/旧临时文件（F2/F10）、fencing 旧实例复活隔离、时钟与期限残留、日志放大漏查点。
- 盲审边界：本轮仅读取本维度第 1 轮文件与审计提示词，未读取 `ai-dev/audits/` 其他目录、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`；所有判定基于当前 live code。
- 定向排查结论（无新发现的方向，供复核对照，不重复立项）：
  - **重试风暴**：全部重试点复核均有界——elector 轮询内 20 次循环上限 + epoch 分配 32 次预算 + `restartElection` 10 次 CAS（`JdbcLeaderElector.java:117-180,306-349`）；webhook 2 次 + 可配退避；checkpoint ACK 3 次；region 重启预算 + stall 冷却；retention 单飞 + trailing re-run 合并（`RetentionCleaner.java:111-142`，非递归风暴）。未发现无速率上限的紧循环。
  - **时钟/epoch**：C7-010 已覆盖墙钟面；本轮补查 elector epoch（long，每次接管 +1，接管速率受租约约束）、`leader_epoch` 计数器行持久单调——溢出不可达，无新点。
  - **TwoPhaseCommitSinkFunction.pendingCommits**：aborted epoch 的 pending 项由后续成功 epoch 的 subsumption（`commit(eid<=N)` 全量前推，:227-253）收敛，且作业在持续失败下受重启预算约束响亮失败——有界，不立项。
  - **PollingJdbcMessageService 毒消息**：反序列化失败跳过并推进 cursor（:317-321）；消费失败 at-most-once（cursor 仍推进，:327-330），丢消息由 checkpoint 超时/CC-01 楔死自检兜底自愈——语义自洽，不立项。
  - **StreamNodeAutoRegistration**：一次性注册（`@PostConstruct`/`@PreDestroy` 成对），无重试循环、无累积面。
  - **第 1 轮 C7-011 候选（增量 checkpoint 中止后 segment 引用滞留）经查不成立**：`checkpointSegments.put`（`CheckpointCoordinator.java:790`）先于 `onCompletePersistSuccess` 的 skip 检查执行，孤儿 epoch 的引用会被后续 retention GC 延迟回收（`RetentionCleaner.gcSegmentsForCheckpoint:250-257`），不构成永久泄漏，不立项。

---

## 发现条目（新发现，序号接续第 1 轮 C7-010）

### [nop-stream-core-runtime-C7-011] standby 降级不取消在途 checkpoint persist：完成/提交扇出路径无 active/fencing 复核，僵尸 standby 成为 checkpoint 持久平面的第二写者

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:2329-2351`（deactivateToStandby，:2346-2348 声明注释）；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:948-1017`（onCompletePersistSuccess 无任何 active/epoch 复核；:1004-1013 commit 扇出）、:707-733（executePersistAsync）、:742-792（executeIncrementalPersistAsync）；对照门控点 `JobCoordinator.java:1021-1030`（collectAck）
- **证据片段**:
  ```java
  // JobCoordinator.deactivateToStandby —— 仅翻转标志，不触 checkpoint 面：
  this.active = false; this.currentLeadership = epoch;
  // In-flight checkpoints will not commit: collectAck is gated on active,
  // so further ACKs are rejected and the pending checkpoint times out /
  // is aborted by the new leader. ...
  // CheckpointCoordinator.onCompletePersistSuccess —— 在途 persist 落地后的完成路径：
  if (!pendingCheckpoints.remove(checkpointId, pending)) { LOG.debug("Skip completing..."); return; }
  pending.forceComplete(); ...
  getOrCreateCommitFanoutExecutor().execute(() -> {
      retryFailedCommits();
      notifyParticipantsFinishCommit(fanoutCheckpointId, true);   // :1007 提交扇出
  ```
- **严重程度**: P1
- **并发场景**: 旧 leader JVM 的 `checkpoint-persist-<jobId>-<n>` 持久化线程（I/O 无锁无门控）× elector 轮询线程（执行 deactivateToStandby，recoveryLock 内翻标志）× 新 leader JVM 的 coordinator 线程（同 id 写同一持久平面）。降级动作与在途 persist 之间无任何同步。
- **触发时序**: T1 = leader L1 触发 checkpoint N+1，全部任务 ACK 已在 `collectAck`（active 门控之前）收集完毕，异步 persist 任务提交（增量路径含 SST 全量拷贝 + `storeCheckPoint`，秒~分钟级 I/O）；T2 = 网络分区/GC 停顿超租约安全间隙，L2 经 elector 接管（epoch E2），`globalRecovery` 从最近持久 checkpoint N 恢复，checkpoint id counter 前推至 N+1；T3 = L1 的 elector 轮询检出失去领导权 → `deactivateToStandby` 仅置 `active=false`，**不取消在途 persist**；T4 = L1 persist 落地：`storeCheckPoint(N+1)` + `storeEpochManifest(N+1)`（均为 ATOMIC_MOVE + REPLACE_EXISTING）→ `onCompletePersistSuccess` 中 `pendingCheckpoints.remove` 命中 **L1 自己的 pending 表**（降级不清空它）→ L1 以 standby 身份完成该 checkpoint：置 `latestCompletedCheckpoint`、fire CHECKPOINT_COMPLETED 事件、触发 retention、并以陈旧 fencing epoch 向参与任务扇出 `finishCommit(N+1, true)`；T5 = L2 触发它自己的 N+1 → 对同一 id 形成双写者：两平面（`.checkpoint` 行与 `.epoch` manifest）**各自独立 last-writer-wins**，交错落地可产生同一 id 的跨写者配对（如 L2 的 checkpoint 文件 + L1 的 manifest），增量恢复按 manifest 段重建 keyed state 时与 checkpoint 行内容来自不同快照。
- **现状**: `active` 门控完整覆盖 assignTasks(:872)/triggerCheckpoint(:943)/collectAck(:1021)/reportTaskStatus(:1083)/reportNodeTaskLiveness(:1210)/detectFailures(:1470)/terminate，唯独 persist 完成路径（含提交扇出）没有任何 `active` 或 fencingEpoch 复核；deactivateToStandby 的注释断言 "In-flight checkpoints will not commit"，与实现直接矛盾（只要 ACK 收集先于降级完成，在途 checkpoint 恰恰会 commit）。
- **风险**: HA 失换窗口内 checkpoint 持久平面出现双写者：轻则同 id 覆盖互踩、standby 发出虚假 CHECKPOINT_COMPLETED 事件/指标/历史记录并重复驱动 sink 提交扇出（subsume 语义可收敛）；重则 `.checkpoint` 与 `.epoch` 跨写者错配后，故障恢复按 manifest 段重建出与 checkpoint 行不一致的状态——持久化平面在声明的故障模型（leader 失换 + 慢持久化）下失去单写者假设。
- **建议**: (1) `onCompletePersistSuccess`/`onCompletePersistFailure` 入口（已在 monitor 内）复核 `active` 与提交时的 fencingEpoch 快照，不一致时走显式 discard：回滚 `releaseIncrementalSegments` 并删除/标记刚落地的 checkpoint 行与 manifest 供 retention 优先回收，禁止提交扇出；(2) 或 `deactivateToStandby` 中同步 abort 全部 `pendingCheckpoints` 并等待在途 persist 任务排空（复用 host 的 persist executor 生命周期管理）；(3) 修正 :2346 注释使声明与实现一致。
- **失效面枚举**（F3）: 控制面入口 active 门控全查——assignTasks/triggerCheckpoint/collectAck/reportTaskStatus/reportNodeTaskLiveness/detectFailures/terminate 七处均已门控（正面对照）；**未门控的副作用路径全清单** = ① persist 成功完成路径（本条）；② persist 失败路径 `onCompletePersistFailure`（同样无 active 复核，会以 standby 身份 fire CHECKPOINT_FAILED 事件 + abort 扇出——abort 扇出 `finishCommit(false)` 保留 prepared 事务属设计内 subsume 语义，事件噪声并入本条修复）；③ 僵尸 timeout scheduler 的 `abortPendingCheckpoint`（同 ② 的 abort 扇出面）；④ 单 JVM HA 拓扑共享同一 CheckpointCoordinator 实例（`JobCoordinator.java:2369-2372` 注释证实），该拓扑下 standby 守卫由 terminate 的 `!active` 检查部分缓解，但跨 JVM 部署（owner doc 声明的分布式模式）完整暴露本条。
- **信心水平**: 代码路径确定；跨写者错配后果很可能（需持久化落地与降级检测的具体交错，触发链完整可复现）
- **误报排除**: 不是"standby 不作为"的已声明边界——注释明示设计意图是在途 checkpoint **不** commit，实现允许其 commit，属声明与实现不一致；也不是 collectAck 门控已覆盖的场景（ACK 收集发生在降级前，门控天然管不到已在飞的 persist 任务）；后果触及 C7 明文的"fencing：旧实例复活后是否被隔离"定向检查项（checkpoint 写平面恰是隔离缺口）。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C7-012] 三个算子级 counter 以实例身份做标签且无任何移除路径：identityHashCode 标签使每次任务部署/重启净增 meter，永不复用永不清理（metrics 标签基数爆炸，C7-007 失效面遗漏点）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java:183-185`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/windowing/OverWindowOperator.java:128-131`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/windowing/WindowOperator.java:584-596,631-635`（全仓 `registry.remove` 仅存在于 runtime `EngineMetrics.java:111-117` 与 `TaskNodeMetrics.java:200-206`）
- **证据片段**:
  ```java
  // EquiJoinOperator.open() —— 标签含 System.identityHashCode(this)：
  joinOutputsCounter = StreamMetricsRegistries.registry()
          .counter(JOIN_OUTPUTS_METRIC_NAME,
                  "operator", getClass().getSimpleName() + "@" + System.identityHashCode(this));
  // OverWindowOperator.open() 同构（OVER_OUTPUTS_METRIC_NAME）。
  // WindowOperator（W-M1）：有 TaskLocation 时用 jobId.pipeline.vertex + taskIndex，
  // 无 location 回退 "window@" + Integer.toHexString(System.identityHashCode(this))。
  ```
  三个算子均无 close/release 路径（grep 证实 `registry.remove`/meter 移除为零调用）。
- **严重程度**: P2
- **并发场景**: 非竞态缺陷，是标签基数单调增长：任务部署线程（每次 `copyForSubtask` + `open()`）在新实例上注册新 meter；Micrometer `registry.counter(name, tags)` 对不同标签值恒新建、注册表永不淘汰；Prometheus 抓取线程并发读取。
- **触发时序**: T1 = `EquiJoinOperator`/`OverWindowOperator` 标签含 `identityHashCode`——同一作业内每次任务重启/重部署（region restart、stall recovery、重新 deploy）都构造全新算子实例 → 新 hash → **净增**一个 meter，旧的永不移除；T2 = `WindowOperator` 有 TaskLocation 路径按 `jobId.pipeline.vertex` 键——单次执行内有界，但 jobId churn（OpsJobManager 多作业宿主重新提交）下与 C7-007 同形态增长，其无 location 回退路径则按实例数增长；T3 = 长存活宿主中 `/metrics` 抓取体与注册表随重启次数 + 作业执行次数线性膨胀，含大量恒 0 死序列。
- **现状**: 有注册无释放（F8 清理不对称），且 `identityHashCode` 标签从根上排除了同键去重的可能；第 1 轮 C7-007 的失效面枚举 ①-⑥ 未覆盖这三个注册点，本轮补全为 ⑦⑧⑨。
- **风险**: 长时运行多重启/多作业宿主中指标基数无界增长——抓取延迟、内存缓涨、监控面死序列污染；`identityHashCode` 形态使增长速率与重启频率直接挂钩（故障频发的集群指标面退化最快，恰在最需要观测时劣化）。
- **建议**: 对齐 `EngineMetrics.releaseJob`/`TaskNodeMetrics.releaseNode` 的做法：标签改用部署位置键（jobId|vertexId|subtask，`TaskLocation` 全路径可用时），并在任务 close（`StreamTaskInvokable` finally，已调用 `MicrometerStreamTaskMetrics.releaseHolder` 的同一位置）对本次执行注册的 meter id 逐一 `registry.remove`；短期至少把 `identityHashCode` 标签替换为 (vertexId, subtaskIndex) 稳定键以恢复同键去重。
- **失效面枚举**（F3）: 指标注册/释放配对面全查（接第 1 轮 C7-007 的 ①-⑥ 编号）——⑦ `EquiJoinOperator` JOIN_OUTPUTS counter：identityHashCode 标签，无释放（本条）；⑧ `OverWindowOperator` OVER_OUTPUTS counter：同 ⑦（本条）；⑨ `WindowOperator` numLateRecordsDropped counter：location 标签有界 per 执行 / 回退 identityHashCode，无释放（本条）；core 侧其余 `registry.counter/gauge/timer` 调用点全查（`MicrometerStreamTaskMetrics` ⑤ 已立项、`StreamMetricsRegistries` 静态组合注册表无增长面）——无第四处遗漏。
- **信心水平**: 确定（注册点与"无移除"均为全仓 grep 实证）
- **误报排除**: 不属于"有界声明存在的缓存不报"——三处均无容量/生命周期声明，也无任何移除路径；`WindowOperator` 的 W-M1 注释声称"per-job/per-vertex/per-subtask scope"已解决 JVM 级共享计数问题，但只覆盖有 TaskLocation 的部署路径且未声明 meter 生命周期，回退路径与释放缺失均未被声明。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C7-013] `LocalFileCheckpointStorage` 崩溃残留 `.tmp` 文件无任何清理路径：所有列举/保留/剪枝过滤只认 `.checkpoint`/`.epoch` 后缀

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/LocalFileCheckpointStorage.java:51-53,91-107,391-411,492-506`（写路径）；:126-131,525-530,607-634（列举/剪枝全按后缀过滤）；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/RetentionCleaner.java:168-227`（双平面保留不含 `.tmp`）
- **证据片段**:
  ```java
  private static final String TEMP_SUFFIX = ".tmp";
  ...
  Files.write(tempPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  Files.move(tempPath, checkpointPath, StandardCopyOption.ATOMIC_MOVE, ...);   // write→move 非原子区间
  // finally { unlock; deleteIfExists(tempPath); }   // 仅覆盖进程存活路径
  // 所有读取/保留路径：filter(p -> p.toString().endsWith(CHECKPOINT_SUFFIX)) —— .tmp 永不命中
  ```
- **严重程度**: P3
- **并发场景**: persist 线程 write→move 区间内进程崩溃（kill -9/断电/OOM killer）遗留；无并发竞态，纯残留累积。
- **触发时序**: T1 = 每次崩溃若恰落在 `Files.write(tempPath)` 与 `ATOMIC_MOVE` 之间（含 savepoint 与 epoch manifest 的同构写法），遗留一个 `<id>.checkpoint.tmp` / `<id>.epoch.tmp`；T2 = `getLatestCheckpoint`/`getAllCheckpoints`/`pruneEpochManifests`/`RetentionCleaner` 的过滤全部不匹配 `.tmp`，`deleteAllCheckpoints` 仅在作业级 ops 删除时顺带清理；T3 = 长期运行的集群按崩溃次数累积残留，单文件为完整 checkpoint 序列化体（声明包络内可 MB~GB 级），磁盘占用随历史崩溃次数单调增长且不可被任何运行时机制回收。
- **现状**: 进程内存活路径的 finally 清理正确；崩溃路径的恢复清理（"旧临时文件"，C7 第 4 步明文检查项）缺失。
- **风险**: 频繁崩溃-恢复循环（演练、不稳定宿主）下 checkpoint 目录磁盘占用慢性增长；残留 `.tmp` 不参与任何一致性语义（不会被误读），纯粹是卫生问题，故 P3。
- **建议**: 在 `RetentionCleaner.cleanupOldCheckpoints`（或 coordinator 启动恢复路径）增加一轮 `.tmp` 清扫：列出 jobDir 下 `*.tmp` 且 mtime 早于当前时间的文件直接删除（崩溃残留不可能再有写者——同一 checkpointId 的重写会 TRUNCATE_EXISTING 复用同名 temp，删除无竞态）；或 `Files.list` 后按 `TEMP_SUFFIX` 过滤在 retention 每轮顺带删除。
- **失效面枚举**（F3）: 本地平面临时文件写点全查——① `storeCheckPoint`（.checkpoint.tmp，本条）；② `storeEpochManifest`（.epoch.tmp，本条）；③ `storeSavepoint`（checkpoint.tmp + metadata.tmp，同构，savepoint 目录通常手工管理，并入本条修复）；④ JDBC 平面（`JdbcCheckpointStorage`）行模型无 temp 概念（不适用）；⑤ core 侧 `SharedStateRegistryImpl` 孤儿段文件——类注释声明重启对账（:38-39），有恢复机制（正面对照）。
- **信心水平**: 确定（过滤面穷举）
- **误报排除**: 非"声明边界"——无任何文档/注释声明崩溃残留被接受；AR-07 类的 fsync 缺口属 F6（持久化屏障），本条不涉及写屏障，仅磁盘卫生，二者正交。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C7-014] C7-005 失效面补充：四个漏查的周期性失败日志点（elector 轮询 / barrier 注入 / persist 失败 / retention 清理）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcLeaderElector.java:140-144`（轮询驱动 `nop-cluster/.../AbstractPollingLeaderElector.java:147` scheduleCheck）；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/TaskCheckpointWiring.java:273-287` 与 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1307-1325`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1042-1043`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/RetentionCleaner.java:187-189`
- **证据片段**:
  ```java
  // JdbcLeaderElector.checkElection —— DB 不可达时每次轮询 INFO+完整堆栈：
  } catch (Exception e) {
      LOG.info("nop.stream.leader-elector.check-fail: clusterId={}", getClusterId(), e);
      onException(e); onBecomeFollower(null); onRestartElection();
  }
  // CheckpointCoordinator.onCompletePersistFailure —— 存储故障期每个 checkpoint 尝试一条 ERROR+堆栈：
  LOG.error("Failed checkpoint {} for job {}: {}", checkpointId, jobId, failMessage, cause);
  ```
- **严重程度**: P3
- **并发场景**: elector 轮询线程（平台 `scheduleCheck` 周期）× barrier-injector 调度线程（checkpoint 间隔节奏）× persist 线程 + retention 线程（checkpoint 完成/失败节奏）——存储/DB 故障期间多线程并行刷日志，与 C7-005 ①-⑤ 同一放大机制。
- **触发时序**: T1 = 共享 DB/存储计划内中断（分钟级）；T2 = elector 每次轮询输出一条 **INFO 级**含完整堆栈的 check-fail（级别低于 ERROR 更易被日志策略放行，堆积更隐蔽）；barrier 注入每 checkpoint 间隔一条 ERROR（`incrementTriggerFailures` 计数不动摇节奏）；persist 失败每触发尝试一条 ERROR+堆栈（默认 1s 间隔）；retention 每轮清理失败一条 WARN；T3 = 与 C7-005 ①-⑤ 叠加后，故障窗口日志放大速率高于第 1 轮估算。
- **现状**: 第 1 轮 C7-005 的失效面枚举声称"全部同族周期失败日志点 ①-⑤"，本轮复核发现该枚举不完整——上述四处均为周期性失败日志且无抑制，同族同因。
- **风险**: 与 C7-005 相同：故障窗口日志面被噪声占满，真实信号淹没；elector 点为 INFO 级，日志分级策略通常不限制 INFO 量，风险更隐蔽。
- **建议**: 并入 C7-005 的统一修复（首条全量 + 窗口聚合 + 恢复 cleared），四处同批处理；elector 点建议同时把 check-fail 从 INFO 升为 WARN（不可达是异常态）以便与正常轮询噪声区分。
- **失效面枚举**（F3）: 本条即 C7-005 枚举的补全——⑥ `JdbcLeaderElector.checkElection`（轮询节奏）；⑦ `TaskCheckpointWiring.startBarrierScheduler` 失败分支 + `JobCoordinator` LOCAL 同构点（checkpoint 间隔节奏，C7-009 引用过同一 catch 的 Error 死亡面，日志量未入 ⑤ 之外的清单）；⑧ `CheckpointCoordinator.onCompletePersistFailure`（触发节奏）；⑨ `RetentionCleaner.cleanupOldCheckpoints` 失败 WARN（完成节奏）。⑥⑦⑧⑨ 与 ①-⑤ 合并后该族枚举方为完备。
- **信心水平**: 确定（四个点均为逐处核实的无条件全量输出）
- **误报排除**: 非新缺陷类——明确登记为第 1 轮 C7-005 的 F3 失效面遗漏修正，合并裁定时应并入 C7-005 而非独立计数；`tryBecomeLeader` 的 become-leader-fail 为 DEBUG 级且有"另一个节点竞得"的正常语义，不列入。
- **复核状态**: 已并入 C7-005

---

统计（第 2 轮新增）：P0 × 0；P1 × 1（C7-011）；P2 × 1（C7-012）；P3 × 2（C7-013、C7-014，其中 C7-014 为 C7-005 失效面补全，合并裁定时应并入）。

未立项的候选与理由（防复核翻案）：增量 checkpoint 中止后 segment 引用滞留——`checkpointSegments.put` 先于 skip 检查，retention GC 延迟回收，自愈有界；standby 僵尸的 abort 扇出——`finishCommit(false)` keep-prepared 属声明 subsume 语义；elector/重试面——全部有界预算；TwoPhaseCommit pendingCommits——subsumption 收敛 + 响亮失败兜底。

# C7 长时运行稳定性 — nop-stream-core-runtime（第 3 轮深挖追加）

## 深挖第 3 轮追加

- 审计日期：2026-10-14
- 审计维度：C7 第 3 轮深挖——前两轮已覆盖 19 项单调增长源 + 11 项挂死点清单、metrics 标签基数、重试面、自愈/fencing、时钟主面（见同目录 `C7-long-running.md`、`C7-round2.md`）；本轮只盘前两轮未触及的文件/包：source/coordinator 全栈、runtime rpc/ops/event/health/metrics 新面、checkpoint/metrics 与 CheckpointHistory、taskmanager CheckpointAckSender/RunningTask 释放面、windowing/MergingWindowSet 合并清理、execution/flow、execution/CheckpointBarrierTracker、InputGate 对齐集合、core connector/registry、LocalFileSegmentStore、MemoryKeyedStateBackend TTL 面。
- 盲审边界：本轮仅读取本维度前两轮文件与审计提示词，未读取 `ai-dev/audits/` 其他目录、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`；所有判定基于当前 live code。
- 定向排查结论（无新发现的方向，供复核对照，不重复立项）：
  - **StreamControlRpcServer / StreamControlRpcProxyFactory**：委托平台 `MessageRpcServer`/`MessageRpcClient`（平台基础设施不重审）；`CorrelatingRpcService` 无状态包装，无自有累积面。
  - **CheckpointHistory（runtime）**：`ConcurrentLinkedDeque` 有 100 条默认上限 + governance sweep `pruneOldestCheckpointHistory`，双路收敛，正面。
  - **RuntimeMetrics**：纯 facade 无状态；`CheckpointMetrics`/`CheckpointMetricsSnapshot` 无注册表面。
  - **CheckpointAckSender**：3 次有界内联重试 + 线性退避 + 可中断，无队列无累积，失败计数可观测，正面。
  - **OpsJobManager**：`jobs`/`terminalAt` 均有显式 remove（:312-313）、sweep 移除（:407-408）、clear（:437），生命周期闭合，正面。
  - **StreamJobEventBus / JobHealthStateMachine**：均为 JobCoordinator 实例字段（构造器创建），随 coordinator GC，无跨作业静态累积；bus 有 add/removeListener 配对 API。
  - **TypeRegistry**：实例级（Embedded/Rpc executor 各自 new），随 plan 生命周期，非静态注册表（与 SideOutputRegistry 不同族），正面。
  - **DataPlaneMessageServiceAdapter**：无状态装饰器；解码失败转发 typed failure（fail-fast 契约），无累积。
  - **MergingWindowSet / WindowOperator 合并清理**：合并回调对每个 merged-away 窗口逐一执行 triggerContext.clear / removeTriggerAccumulators / paneTracking.remove / removeActiveWindow / clearPendingMaxDelay / clearUserWindowState / deleteCleanupTimer，且三条路径（元素 :1077、timer :1218、:1291）均调用 `mergingWindows.persist()`——为 Flink 语义的忠实且加固实现，正面。
  - **PerKeyOrderedBuffer**：trimToWatermark/trimToCount 空树即 `buffers.remove(key)`（:269,:323,:341），空 key 壳不驻留，正面。
  - **InputGate 对齐集合**（inFlightAlignments/abortedBarriers/blockedChannels）：alignment 完成发射即 remove（:949,:1072-1084,:1119,:1145），aborted 标记在后续 epoch 完成时 `removeIf(id <= completedId)` 机会性清扫（:1182），正面。
  - **CheckpointBarrierTracker.inFlight**：条目在本地完成/快照错误/epoch-precise abort 三路移除；unaligned 默认开启（`CheckpointConfig.unalignedCheckpointEnabled` 默认 true）使对齐不悬挂、本地 ACK 完成路径自清——未发现可具体化的永久滞留时序，不立项。
  - **NodeDiscoveryConsistencyChecker**：无自建循环的无状态诊断器（check() 由调用方驱动），不属周期循环死亡面。
  - **RunningTask 释放面**：R6 CC-04 的 `maybeReleaseSourceCoordinatorRegistryEntry` 在任务终态路径接线（:186），JVM 级 source coordinator 注册表有分布式释放路径，正面。
  - **TaskExecutor**：每 execute() 新建实例（StreamExecutionEnvironment:543），三 map 随实例 GC，finally 走 `shutdownLocalExecution`，正面。
- 第 1 轮正面结论修正（本轮实证，见 C7-019）：第 1 轮检查范围说明中"内存 registry 有 `evictExpiredNodes`"的自愈表述不成立——该方法全仓无生产调用方（仅测试调用）。
- C7-005 族清单补遗（合并裁定并入 C7-005，不单独计数）：`StreamMetricsReporter.report`（`ops/StreamMetricsReporter.java:118-119`）——metrics 目标不可达时每个报告周期一条 WARN（无堆栈，仅 `e.toString()`），无抑制；与 C7-014 ⑥-⑨ 同族同因，建议随 C7-005 统一修复一并处理。

---

## 发现条目（新发现，序号接续第 2 轮 C7-014）

### [nop-stream-core-runtime-C7-015] `LocalFileSegmentStore.storeSegment` 崩溃残留 `.tmp-<UUID>` 段文件不被任何清理路径命中——C7-013 失效面 ⑤ 的"正面对照"不成立

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/storage/LocalFileSegmentStore.java:59-73`（temp 写 + finally）；对照 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1873-1886`（孤儿清扫仅认 `.sst`）、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/RetentionCleaner.java:168-227`（双平面保留仅认 `.checkpoint`/`.epoch`）
- **证据片段**:
  ```java
  // LocalFileSegmentStore.storeSegment
  Path tmp = target.resolveSibling(target.getFileName() + ".tmp-" + java.util.UUID.randomUUID());
  try {
      Files.copy(sourceFile, tmp);
      Files.move(tmp, target, ATOMIC_MOVE);            // 崩溃窗口：copy 与 move 之间
      ...
  } finally {
      Files.deleteIfExists(tmp);                       // 仅覆盖进程存活路径
  }
  // CheckpointCoordinator.cleanupOrphanSegments —— 孤儿段清扫过滤：
  .filter(p -> p.getFileName().toString().endsWith(".sst"))   // ".sst.tmp-<uuid>" 不命中
  ```
- **严重程度**: P3
- **并发场景**: 增量 checkpoint persist 线程（拷贝 SST 段文件，秒~分钟级 I/O 窗口）与进程崩溃（kill -9/断电/OOM killer）交叠；无并发竞态，纯残留累积。
- **触发时序**: T1 = 增量 checkpoint 持久化执行 `storeSegment`，`Files.copy` 已写出 `<hash>.sst.tmp-<uuid>`、`ATOMIC_MOVE` 未执行时进程崩溃；T2 = 重启后 `cleanupOrphanSegments` 只对 `.endsWith(".sst")` 的文件做孤儿判定，`<hash>.sst.tmp-<uuid>` 不命中；T3 = `RetentionCleaner` 与 `LocalFileCheckpointStorage` 的列举/剪枝全部只匹配 `.checkpoint`/`.epoch` 后缀（第 2 轮 C7-013 已实证），同样不命中；T4 = 残留随历史崩溃次数单调累积，单文件为完整段体积（声明包络内可 MB 级），无任何运行时机制回收。
- **现状**: 进程内存活路径的 finally 清理正确（与 C7-013 同构）；崩溃路径的恢复清扫缺失。第 2 轮 C7-013 失效面枚举将 ⑤ `SharedStateRegistryImpl` 记为"有恢复机制（正面对照）"——本轮实证该对账只覆盖 `<hash>.sst` 本名文件，段存储自身的 temp 写路径同族缺口，⑤ 的正面判定不完整，应并入 C7-013 修复。
- **风险**: 频繁崩溃-恢复循环（演练、不稳定宿主）下 shared-state 目录磁盘占用慢性增长；残留不参与一致性语义（不会被误读），纯卫生问题，故 P3。
- **建议**: 并入 C7-013 的修复：在 `cleanupOrphanSegments` 的 walk 中增加对 `*.tmp-*` 前缀/后缀模式的清扫（崩溃残留不可能再有写者——同 hash 重写走新的 UUID temp 名，删除无竞态），或在 `restoreSharedStateRegistry` 启动对账时统一清扫段目录非 `.sst` 文件。
- **失效面枚举**（F3）: 本地平面 temp 写点全查（接 C7-013 编号）——① `storeCheckPoint`（.checkpoint.tmp，C7-013）；② `storeEpochManifest`（.epoch.tmp，C7-013）；③ `storeSavepoint`（同构，C7-013）；④ **`LocalFileSegmentStore.storeSegment`（.sst.tmp-<uuid>，本条，C7-013 枚举遗漏）**；⑤ JDBC 平面行模型无 temp（不适用）；⑥ `SharedStateRegistryImpl` 无文件 I/O（类注释声明，孤儿对账经 CheckpointCoordinator 实现）；`discardSegment` 直接删除无 temp（正面）。
- **信心水平**: 确定（过滤面穷举：孤儿清扫 `.sst` 过滤 + retention 后缀过滤 + C7-013 的列举过滤三面均不命中该命名模式）
- **误报排除**: 非"崩溃残留被声明接受"——无任何文档声明该残留可容忍；`finally` 的存在证明作者已识别 temp 清理需求，缺口仅在崩溃路径，属 F8"清理不对称"的磁盘面形态而非设计边界。
- **复核状态**: 已并入 C7-013

### [nop-stream-core-runtime-C7-016] `LocalSourceCoordinator.finishedSplits` 只增不删且模块内零生产读者：split 型源的完成 split id 随作业生命周期单调累积

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java:68-69,279-285,329-331,333-344`；写入入口 `source/SplitAssignmentProxy.java:45`（生产调用方在 connector 模块 `FileSourceReader`，模块外）
- **证据片段**:
  ```java
  /** Splits that have been reported finished by readers (for restore round-trip). */
  private final Set<String> finishedSplits = ConcurrentHashMap.newKeySet();
  ...
  @Override
  public void reportFinishedSplits(int subtaskIndex, List<String> finishedSplitIds) {
      ...
      owner.finishedSplits.addAll(finishedSplitIds);   // 追加，全仓无任何 remove/clear 路径
  }
  // close()（:333-344）：只关 enumerator，不清 readerChannels/finishedSplits
  ```
- **严重程度**: P3
- **并发场景**: 读者任务线程经 `ReaderChannel.reportFinishedSplits` 并发写入（ConcurrentHashMap.newKeySet 写安全，无竞态）；增长是单作业生命周期内随 split 完成节奏的慢性累积。
- **触发时序**: T1 = LOCAL 模式作业使用 split 型源（如文件发现源，每完成一个文件/split 上报一次）；T2 = 每个完成 split 的 id 净增一个 set 条目，类注释声明的用途"(for restore round-trip)"在模块内无任何生产读者（`getFinishedSplits` 全仓 grep 生产调用为零）；T3 = coordinator 存活整个作业执行（SourceCoordinatorRegistry 条目直至作业终态才 unregister），长时运行流式作业中 set 随 split 生产总量线性增长且永不回收；T4 = 数周级运行 + 高频小 split 源下驻留达十万级 id 字符串。
- **现状**: append-only 集合，无移除 API、无生产读者、close 不清理——声明的"restore round-trip"用途未实现，是只写不读的死增长面。
- **风险**: 长时运行 split 型源作业的缓慢内存驻留；因无读者，不构成正确性风险；主要代价是 set 本体与 id 字符串驻留。
- **建议**: 短期：若 restore round-trip 无实施计划，删除该面或在 javadoc 显式声明"仅诊断、允许无界"并给 `getFinishedSplits` 快照加上限；中期：若保留用途，在 enumerator `snapshotState` 消费后按 checkpoint 轮次裁剪已入 manifest 的 id。
- **失效面枚举**（F3）: source/coordinator 包增长面全查——① `finishedSplits`（本条）；② `ReaderChannel.pendingSplits`：reader 注册后由 `setOnSplitsDelivered` drain（回调未接线的窗口内积压，注册即接线，有界）；③ `readerChannels`：键空间=parallelism，有界；④ `SourceCoordinatorRegistry.PENDING_ENUMERATOR_RESTORES`：consume-once + unregister 清扫（正面）；⑤ `SplitEnumeratorContext`/`AssignmentDeliveryService`：转发层无账本（正面）。
- **信心水平**: 确定（"无生产读者"为模块内 grep 实证；增长速率取决于模块外 connector 的上报节奏）
- **误报排除**: 非测试代码——`reportFinishedSplits` 是 `SplitAssignmentProxy` 的生产接口方法且有生产实现方调用；与 C7-006 的差别在于本条是实例级（随作业 GC）故降 P3，C7-006 是 JVM 级静态（跨作业累积）故 P2。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C7-017] C7-009 失效面补全：core 侧 `ProcessingTimeServiceDriver.run()` 无任何逐轮守卫且 `isRunning()` 无调用方——驱动线程死亡后 processing-time 定时器面静默永久停止，processing-time 窗口从此永不触发也永不 GC

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ProcessingTimeServiceDriver.java:111-131`（run 循环 + isRunning）；后果链 `operators/windowing/WindowOperator.java`（processing-time 窗口的 trigger/cleanup timer 均注册于 processing-time 定时器服务）；对照 `TaskMailbox.java:54-61`（put 不抛）
- **证据片段**:
  ```java
  public boolean isRunning() { ... }        // :111 —— 全仓 grep 生产调用为零
  @Override
  public void run() {                        // :117 —— 循环体无 try/catch（仅 sleep 捕 InterruptedException）
      while (!stopped) {
          long now = CoreMetrics.currentTimeMillis();
          boolean due = processingTimeService.isTimerDue(now)
                  || (timeServiceManager != null && timeServiceManager.hasProcessingTimeTimersDue(now));
          if (due) {
              mailbox.put(Mail.control(this::fireDueTimersOnTaskThread, ...));
          }
          try { Thread.sleep(tickMs); } catch (InterruptedException e) { ... }
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: `processing-time-driver` 独立线程 × 任务主循环线程。驱动线程死亡与任务活性无关：任务线程继续 tick（`markActivity` 正常上报），stall 检测/liveness 心跳对驱动死亡完全不可见。
- **触发时序**: 理论风险，未具体化到非 Error 触发——T1 = 驱动循环内逃逸一个 Throwable（当前循环体各调用点实际不抛受检/运行时异常：`isTimerDue` 读 volatile、`mailbox.put` 对 closed 静默丢弃不抛、时钟读取不抛；现实触发面为 Error 族，或未来演进引入的运行时异常——同循环无逐轮 Exception 守卫，任何一次即死亡）；T2 = 驱动静默终止，fire mail 停发；T3 = processing-time 定时器全部停摆：processing-time 窗口的 trigger timer 与 cleanup timer 永不触发 → `WindowOperator.activeWindows`、trigger 累加器、keyed 窗口状态、pane tracking 从此只增不清（清理路径本身依赖 processing-time 定时器）；T4 = 作业"继续消费数据但不产出窗口结果也不报错"，聚合结果永久 stale，状态无界增长直至资源耗尽；引擎内无任何机制感知（`isRunning` 死代码）。
- **现状**: C7-009 已登记 runtime 侧 ①-⑦ 周期循环仅捕 Exception 的 Error 死亡面；本轮发现 core 侧驱动为同族更弱形态——连逐轮 Exception 守卫都没有（同族 ①-⑦ 至少能扛住单轮运行时异常），且后果最重（定时器面整体死亡 → 窗口 GC 依赖自身 → 无界增长 + 静默数据 stale）。
- **风险**: F5 在 core 控制线程的投影：死亡不可自愈、不可告警、后果沿"定时器停摆 → 窗口永不清理"放大为无界增长。触发前提是 JVM 已深度退化（Error），故 P3；但一旦触发，其后果比 ①-⑦ 的检测面消失更直接（数据面窗口结果错误 + 状态无界）。
- **建议**: 并入 C7-009 统一修复并按本条的更强形态处理：(1) 循环体加 catch(Throwable) 逐轮围栏（Error 分支记录后按团队裁定）；(2) 将 `isRunning()` 接入任务侧心跳或 ops 状态面（`StreamOpsHttpServer` 的 job detail 已有挂钩点），驱动死亡转为可告警；(3) 死亡后 fail-fast 使任务失败走 SupervisorLoop 恢复（比静默停摆更符合 F5 的可检测性要求）。
- **失效面枚举**（F3）: 周期/调度循环死亡面全清单（接 C7-009 ①-⑦）——⑧ **`ProcessingTimeServiceDriver.run`（core，无逐轮守卫 + isRunning 死代码，本条）**；runtime ①-⑦（failureDetector、barrier-injector、periodic-checkpoint、TM heartbeat、governanceSweep、metrics reporter、poll）维持 C7-009 登记；`NodeDiscoveryConsistencyChecker` 无自建循环（不适用）；平台侧 `AbstractPollingLeaderElector.scheduleCheck` 属 nop-cluster（模块外）；`SourceRateLimiter` 为任务线程内同步限速非循环（不适用）。
- **信心水平**: 很可能（机制与后果链确定；触发依赖 Error 类事件或未来演进，未构造非 Error 的可复现序列）
- **误报排除**: 不按"循环体当前不抛异常"驳回：C7-009 已裁定同族循环的 Error 死亡面成立，本条是同族在 core 侧的遗漏点且守卫更弱（无 Exception 围栏）、检测更差（isRunning 死代码）、后果更重（定时器面全停 → 窗口 GC 自依赖 → 无界增长），符合 C7 第 2 步"不产出也不报错路径是否有看门狗"的明文检查项。
- **复核状态**: 已并入 C7-009

### [nop-stream-core-runtime-C7-018] C7-010 失效面补全：状态 TTL 到期判定与 processing-time 窗口/定时器判定均基于墙钟，前两轮 ①-⑦ 清单未覆盖 core 数据面

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/SystemTtlTimeProvider.java:23-24`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:123-127,233-243,257-268`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ProcessingTimeServiceDriver.java:119`（`currentProcessingTime` 的唯一时间源）
- **证据片段**:
  ```java
  // SystemTtlTimeProvider —— TTL 到期判定的时间源：
  public long currentTimeMillis() { return CoreMetrics.currentTimeMillis(); }   // 墙钟
  // MemoryKeyedStateBackend —— 清扫门控用单调钟，到期判定用墙钟（两钟并存）：
  long now = System.nanoTime();                    // :237 仅门控（60s 节流）
  total += ctx.sweepExpired(storage::remove);      // :268 到期判定走 ttlClock（墙钟）
  // ProcessingTimeServiceDriver.run —— processing-time 窗口/定时器的 now：
  long now = CoreMetrics.currentTimeMillis();      // :119
  ```
- **严重程度**: P3
- **并发场景**: TTL/窗口判定在任务线程与驱动线程间共享墙钟读数；多 JVM 部署下各进程时钟独立（同 C7-010 的 NTP 隐性依赖）。
- **触发时序**: 理论风险，未具体化到可复现故障——（a）墙钟向前跳变 > TTL：下一次 60s 门控清扫将所有 TTL 条目按跳变后的 now 判定过期并批量物理删除（`storage::remove`）——**用户 keyed state 静默批量丢失**，且 checkpoint 重建亦无法恢复（已删除）；processing-time 窗口/定时器同刻批量到期（会话窗瞬间合并触发、聚合提前发射）；（b）向后跳变：到期判定整体推迟 ≤ 跳变量，state/窗口晚清理（有界延迟）。两向自限于"一次跳变一个效应窗"，但 (a) 的数据丢失方向不可逆。
- **现状**: C7-010 的墙钟清单 ①-⑦ 覆盖 runtime 控制面（lease/stall/冷却/checkpoint timeout/deploy 宽限），本轮补全 core 数据面两处——清扫门控本身已正确用 `nanoTime`（:237），缺口只在到期/触发判定。
- **风险**: 时钟运维事件（NTP 步进、VM 迁移恢复）下 TTL 状态静默批量丢失（超出"时钟回拨漏检"的 C7-010 原口径——这里是前跳数据丢失方向）；processing-time 窗口聚合时序失真。多 JVM 部署的 NTP 依赖未在任何 owner doc 声明（C7-010 已登记该声明缺口，本条共享）。
- **建议**: (1) TTL 到期判定可选单调钟基线（记录条目写入时的 `nanoTime`+墙钟对，跳变时按单调钟差分复核再判过期），或至少在 owner doc 部署要求中声明"启用 StateTtlConfig 的作业对时钟跳变敏感（前跳 = 批量过期）"；(2) processing-time 窗口面与 C7-010 建议一致（跳变二次确认）。
- **失效面枚举**（F3）: 墙钟依赖点全清单（接 C7-010 ①-⑦）——⑧ **状态 TTL 到期判定（SystemTtlTimeProvider，本条）**；⑨ **processing-time 窗口/定时器到期判定（ProcessingTimeServiceDriver:119 → TaskProcessingTimeService/HeapInternalTimerService，本条）**；⑦ 单进程 nanoTime 正确点补充：`MemoryKeyedStateBackend` 清扫门控（:237）、`SourceRateLimiter`（:63 refill 基准）——均正确，不在缺陷面。
- **信心水平**: 很可能（机制确定；触发依赖具体时钟运维事件，未具体化为固定序列）
- **误报排除**: 不按"用了墙钟"泛报（同 C7-010 口径）：立项点是 (1) C7-010 清单声称覆盖"全部超时/租约/停顿判定"但 core 数据面两处遗漏——失效面修正；(2) TTL 前跳方向是**数据丢失**而非漏检，后果等级高于 C7-010 原登记的自限扰动，值得独立枚举供合并裁定量级。
- **复核状态**: 已并入 C7-010

### [nop-stream-core-runtime-C7-019] `InMemoryClusterRegistry.evictExpiredNodes` 全仓无生产调用方：死节点清理 API 存在但从未接线（第 1 轮"内存 registry 有 evictExpiredNodes"正面判定修正，C7-002 同族）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/InMemoryClusterRegistry.java:111-124`；实例化点 `execution/EmbeddedDistributedExecutor.java:139`、`execution/RpcDistributedExecutor.java:192`（生产调用方为零——仅 `TestClusterRegistryConsistencyInvariant.java:159,203`、`TestInMemoryClusterRegistryFixes.java:31,41,117` 测试调用）
- **证据片段**:
  ```java
  public void evictExpiredNodes() {
      long now = CoreMetrics.currentTimeMillis();
      synchronized (nodes) {
          for (Map.Entry<String, Long> entry : leaseExpireTimes.entrySet()) {
              if (entry.getValue() <= now) {
                  nodes.remove(nodeId); leaseStartTimes.remove(nodeId);
                  leaseExpireTimes.remove(nodeId);          // 三 map 同步清理，实现正确
                  ...
  // 全仓 grep：main sources 无任何调用点；周期调度器/检测循环均未接线
  ```
- **严重程度**: P3
- **并发场景**: 非竞态缺陷：清理实现自身在 `synchronized(nodes)` 内正确；缺陷是"清理能力存在但没有任何调度路径调用它"——F4/C7-002 同族的死清理 API。
- **触发时序**: T1 = 长存活宿主（RpcDistributedExecutor 协调进程，registry 随 executor 实例存活跨作业重提交）；T2 = TM 节点反复上下线（nodeId 变化的滚动升级/伸缩）→ `nodes`/`leaseStartTimes`/`leaseExpireTimes` 为每个曾注册 nodeId 留存条目；T3 = 租约过期仅使节点在 `getNodeLease`/`getActiveNodes` 判定中不可见（inactive），三 map 条目本体永不回收；T4 = 与第 1 轮清单 row 14 的 JDBC 面 `nop_stream_node` 死行同形态——内存面同样无主动清理。
- **现状**: 清理 API 实现正确且被测试覆盖，但生产路径零调用；第 1 轮检查范围说明以"内存 registry 有 evictExpiredNodes"作为自愈正面对照，本轮 grep 实证该表述不成立（能力存在 ≠ 已接线）。
- **风险**: 有界（键空间=曾用 nodeId 数，稳定集群下有限），主要风险是自愈声明与实现不一致误导运维/后续审计；nodeId 高 churn 部署（容器化按 pod 重编号）下随实例寿命缓慢累积。
- **建议**: 将 `evictExpiredNodes` 接入既有周期面（`JobCoordinator.detectFailures` 每轮末尾或 `OpsJobManager.governanceSweep` 周期调用），或在类 javadoc/owner doc 显式声明"条目按 nodeId 键空间有界驻留、租约过期仅逻辑失效"为已声明边界；至少修正第 1 轮审计文件中的正面表述（由复核阶段并入 C7-002 修复批）。
- **失效面枚举**（F3）: cluster 注册/清理配对面全查——① `InMemoryClusterRegistry.evictExpiredNodes`（本条，死清理 API）；② `JdbcClusterRegistry` 死节点行无删除（第 1 轮清单 row 14，有界不立项，与本条同因）；③ `JdbcClusterRegistry.removeTaskAssignment`（C7-002，死清理 API 同族）；④ `InMemoryClusterRegistry.removeTaskAssignment`（C7-002 失效面，同族）；⑤ `StreamNodeAutoRegistration`：`@PostConstruct`/`@PreDestroy` 成对（正面）；⑥ discovery 面 `nop_sys_service_instance` 行属平台模块（范围外）。
- **信心水平**: 确定（"无生产调用方"为全仓 grep 实证，测试调用点逐一核对）
- **误报排除**: 不属"低频路径粗粒度同步合理"的误报校准——问题不是实现质量而是清理路径从未被调度；与 C7-002 的裁定先例一致（"清理 API 存在但未接线"登记为缺陷而非设计意图），本条为该族在节点租约面的第 5 个兄弟点。
- **复核状态**: 已并入 C7-002

---

## 本轮覆盖重点文件/包（前两轮未盘）

- runtime：`rpc/StreamControlRpcServer` + `StreamControlRpcProxyFactory`、`ops/OpsJobManager`（jobs/terminalAt 生命周期）、`ops/StreamOpsHttpServer`、`ops/StreamMetricsReporter`（日志量补遗）、`event/StreamJobEventBus` + `LoggingJobEventListener`、`health/JobHealthStateMachine`、`checkpoint/CheckpointHistory`、`checkpoint/metrics/*`、`taskmanager/CheckpointAckSender`、`taskmanager/RunningTask`（释放面）、`cluster/NodeDiscoveryConsistencyChecker`、`cluster/InMemoryClusterRegistry`（evict 接线）、`transport/DataPlaneMessageServiceAdapter`、`execution/GraphModelCheckpointExecutor`（状态面）。
- core：`source/coordinator/LocalSourceCoordinator` + `SourceCoordinatorRegistry`、`source/SourceRateLimiter` + `RateLimitedSourceFunction`、`common/state/backend/memory/MemoryKeyedStateBackend`（TTL 清扫面）+ `SystemTtlTimeProvider`、`checkpoint/storage/LocalFileSegmentStore`、`checkpoint/CheckpointType`（无 UNALIGNED 型，unaligned 经 config 布尔开启）、`execution/CheckpointBarrierTracker`（inFlight 生命周期）、`execution/InputGate`（对齐集合清理）、`execution/ProcessingTimeServiceDriver`（循环守卫 + isRunning）、`execution/transport/TypeRegistry`、`execution/flow/MemoryBudget` + `FlowControlPolicy`、`operators/windowing/MergingWindowSet` + `WindowOperator`（合并清理/persist 三路径）、`common/buffer/PerKeyOrderedBuffer`（空 key 回收）、`operators/StreamingRuntimeContext`（无 accumulator 注册面）、`util/SnapshotCopyUtil`（无文件 I/O）、`environment/StreamExecutionEnvironment`（execute finally 卸载链）。

统计（第 3 轮新增）：P0 × 0；P1 × 0；P2 × 0；P3 × 5（C7-015…C7-019，其中 C7-015 为 C7-013 失效面补全、C7-017 为 C7-009 失效面补全、C7-018 为 C7-010 失效面补全、C7-019 为 C7-002 同族补全——合并裁定时分别并入对应母条目）。

未立项的候选与理由（防复核翻案）：CheckpointBarrierTracker stale epoch——unaligned 默认开启使对齐不悬挂，本地 ACK 完成路径自清，未发现可具体化的永久滞留时序；distributed timeout abort 不通知任务侧（"tasks keep running"）——任务侧条目随后续 barrier 流经自清，协调侧条目随 abort 移除，两侧均有收敛路径；MemoryKeyedStateBackend TTL 仅记录驱动的清扫——零记录期间无新增条目，恢复记录即触发清扫，自限；LoggingJobEventListener 每事件一条 INFO——结构化可观测日志（owner doc 锚点体系），量级 = checkpoint 节奏，非放大缺陷。

---

# C7 长时运行稳定性 — 维度复核结论（独立复核子代理，2026-10-14）

- 复核人：独立复核子代理（与初审/深挖不同 task）；工作区 `nop-entropy-concurrency-audit`。
- 复核方法：逐条重读 live code（含调用方全仓 grep）；本轮解除盲审——对照 `ai-dev/audits/2026-05-31-deep-audit-nop-stream(-full)`、`2026-08-12/13-*-nop-stream-invariant-loop`、`2026-09/2026-09-22-2020-code-review-full`、`2026-09-26/27/29/30 nop-stream-quality r1-r4 + adversarial`、`2026-09-12 platform-conformance` 及 `git log`（含 `-S`  History 检索），为每条标注现状归属。
- 校准规则执行：P2 及以上条目全部核验过具体触发时序（T1-Tn）成立；纯理论条目（C7-008/009/010/017/018）初审均已落 P3 并自带「理论风险，未具体化」标注，无高挂理论项；"已声明边界 + fail-fast 守卫"驳回口径逐条检查——C7-003（javadoc 声明测试定位但无守卫、owner doc 反称生产入口）、C7-008（getDelivered 声明 test observability 但无上界）均为"部分声明、缺守卫"，不构成驳回。

## 维度复核结论
> 注：原「逐条判定」节，2026-10-14 closure audit 要求标准化为本标题；内容未改动。

| 编号 | 判定 | 理由（live 复核 + 现状） |
|---|---|---|
| C7-001 | **保留 P1** | live 复核成立：`store` append-only，`IMaterializationPoint` 接口确无 ack/trim/discard；`ResultPartition.write:296` 每条记录双写，`enqueueWithBackpressure:349-356` 队列满即释放池许可、记录仅存于该库——类注释声称的 IBufferPool 上游控制恰被 overflow-bypass 绕过，声明与实现矛盾属实。materialization-enabled 是 region restart 唯一途径，连续流下必然增长。现状：**新发现**（历史 R3-R4-N1 replay 阻塞已修、R5-CC-02 双投递在案，均为同特性不同失效面，store 无界增长无既有立项）。 |
| C7-002 | **保留 P2** | 全仓 grep 实证 `removeTaskAssignment` 仅测试调用；`JobCoordinator.globalRecovery` 无任何 registry 账本清理；G56 注释（:280 "used by globalRecovery clear"）与实现不符。现状：**新发现**。 |
| C7-003 | **保留 P2** | 类 javadoc 自我定位 test-infrastructure（"production deployments use SysDaoMessageService / Pulsar"），但 owner doc `nop-stream.md:270` 明文 JobCoordinatorMain/TaskManagerMain（默认装配 PollingJdbcMessageService）为 main-scope 生产入口；无 fail-fast 守卫、无保留缺口披露——矛盾成立。附注：若后续裁定 launch 分布式模式整体为演练面，可再降 P3（初审误报排除段已预留此口）。现状：**新发现**。 |
| C7-004 | **保留 P2** | run 循环无"已投递未消费"在途标记，`TaskMailbox.controlQueue` 为无界 ConcurrentLinkedQueue（live 复核）；`TaskProcessingTimeService:58` "an extra no-op fire mail at most" 的论证以任务线程持续消费为前提，长阻塞下前提不成立。现状：**新发现**（旁证：2026-09-27 r2 `03-performance-r2.md` §6 曾从延迟/唤醒视角登记 "task 忙时重复 fire mail"，未立项内存积压——不同失效面，不构成既有立项）。 |
| C7-005 | **保留 P2** | 五个周期失败日志点逐一 live 在位：poll fail ERROR（:336）、onMessage ERROR（:331）、heartbeat ERROR（TaskManager:455）、stale-attempt WARN（JobCoordinator:1230）、retryFailedCommits WARN。均无抑制，异常围栏本身正确（不涉 F9）。现状：**新发现**。 |
| C7-006 | **保留 P2** | 全仓 main sources 无 `SideOutputRegistry.remove/clear` 调用（唯一命中为测试）；register 唯一生产点 `SideOutputDataStream:90`；static map 强引用 consumer 闭包，生命周期与作业不对齐。现状：**新发现**。 |
| C7-007 | **保留 P2** | `releaseHolder` 仅 `holder.compareAndSet(this, null)`（:135-138），无 `TASK_HOLDERS.remove`、无 meter 移除（live 复核）；键空间 jobId\|vertexId\|subtask，jobId churn 下线性增长；与 EngineMetrics/TaskNodeMetrics 完整 release 的不对称属实。现状：**新发现**。 |
| C7-008 | **保留 P3** | `delivered` 仅 :180 一处 add、无清理（live 复核）；`getDelivered` javadoc "test observability" 属部分声明、无上界守卫；低速慢性，P3 + 理论风险标注与校准规则一致。现状：**新发现**。 |
| C7-009 | **保留 P3** | failureDetector/barrier-injector/periodic-checkpoint 均只捕 Exception、无 catch(Throwable)、无检测器活性看门狗（live 复核）。触发依赖 Error 族，初审已标理论风险且落 P3，合规。现状：**新发现**（同族关联：P1-6.7-007〔2026-09-22 code-review index，plan 2387，2026-10-10 修复〕已闭合 fire 链 Exception 吞点，Error-escape 调度死亡面未覆盖——非既有立项）。 |
| C7-010 | **保留 P3** | `clock = CoreMetrics::currentTimeMillis`（:320，可注入测试缝，生产为墙钟）在位；立项点收敛为 NTP 隐性依赖未声明 + 跳变无二次复核，自限无数据损坏路径，P3 恰当。现状：**新发现**（nop-stream 范围无既有立项；2026-09-22 3.5-sys 审计登记过 nop-sys 模块同方向问题，跨模块旁证）。 |
| C7-011 | **保留 P1** | live 复核全部成立：`deactivateToStandby:2329-2351` 仅翻 active/currentLeadership，注释断言 "In-flight checkpoints will not commit"；`CheckpointCoordinator` 全文 **零** active/fencingEpoch 引用（grep 实证）；persist 于 collectAck 完成时 `executor.submit`（:642/:665），降级不取消在途任务；`onCompletePersistSuccess:948` 以本实例 pendingCheckpoints 命中即完成并扇出 `finishCommit(true)`。跨 JVM 失换 + 慢持久化下双写者时序具体可复现，注释与实现直接矛盾。现状：**新发现**（R5 系列未覆盖 standby 残留 persist 面）。 |
| C7-012 | **保留 P2** | 三处 identityHashCode 标签 live 在位（EquiJoin:185、OverWindow:130、WindowOperator:587 回退路径）；core main 无任何 meter 移除调用。每次重部署新实例净增 meter，速率与重启频率挂钩。现状：**新发现**。 |
| C7-013 | **保留 P3** | 列举/剪枝全部 `.endsWith(CHECKPOINT/EPOCH_MANIFEST/METADATA_SUFFIX)`（:128,:165,:208,:298,:527,:572,:622），TEMP_SUFFIX 仅写路径 finally 使用；崩溃残留无恢复清扫。现状：**新发现**。 |
| C7-014 | **并入 C7-005** | 内容为 C7-005 ①-⑤ 枚举的失效面补全而非独立缺陷（elector check-fail INFO 带堆栈 :141、persist 失败 ERROR :1043、retention WARN :187-189、barrier 注入 ERROR :283，live 逐一在位）——按维度文件自身裁定并入 C7-005 修复批，不独立计数。现状：**新发现**（作为 C7-005 补全）。 |
| C7-015 | **并入 C7-013** | `.tmp-<UUID>` 写路径 + finally 在位，`cleanupOrphanSegments` 仅 `.endsWith(".sst")`（:1876）不命中——为 C7-013 失效面 ⑤ 正面判定的修正，同批修复（清扫模式加 `*.tmp-*`）。不独立计数。现状：**新发现**（作为 C7-013 补全）。 |
| C7-016 | **保留 P3** | `finishedSplits` 仅 addAll（:284）；`getFinishedSplits` 全仓（core+runtime+connector）零生产调用（grep 实证）——只写不读的声明用途（restore round-trip）未实现。实例级随 coordinator GC，P3 恰当。现状：**新发现**。 |
| C7-017 | **并入 C7-009** | run 循环无逐轮守卫在位；`isRunning()` 全仓 main 零调用（grep 命中的同名方法均属 JobCoordinator）——为 C7-009 在 core 侧的更弱形态（无 Exception 围栏 + 检测死代码 + 后果更重），并入 C7-009 统一修复，不独立计数。现状：**新发现**（作为 C7-009 补全）。 |
| C7-018 | **并入 C7-010** | SystemTtlTimeProvider 墙钟 + 清扫门控 nanoTime 双钟结构在位——为 C7-010 ①-⑦ 清单的 core 数据面补全。注意：前跳 → TTL 条目批量物理删除属**数据丢失方向**，重于 C7-010 原口径的自限扰动，并入时 C7-010 修复（owner doc 声明 + 单调钟二次确认）须显式覆盖 TTL 面。不独立计数。现状：**新发现**（作为 C7-010 补全）。 |
| C7-019 | **并入 C7-002** | `evictExpiredNodes` 全仓仅测试调用（TestClusterRegistryConsistencyInvariant / TestInMemoryClusterRegistryFixes），main 零接线（grep 实证）；第 1 轮"内存 registry 有 evictExpiredNodes"正面表述按本条修正。作为 C7-002 死清理 API 族的节点租约面兄弟点并入修复批，不独立计数。现状：**新发现**（作为 C7-002 补全）。 |

## 归并裁定与统计

- **保留（独立立项）**：14 条——P1×2（C7-001、C7-011）、P2×7（C7-002、003、004、005、006、007、012）、P3×5（C7-008、009、010、013、016）。
- **并入（保留价值并入母条目，不独立计数）**：5 条——C7-014→005、C7-015→013、C7-017→009、C7-018→010、C7-019→002。
- **降级**：0（P2 以上条目触发时序全部复核成立；理论性条目初审已自落 P3 并带标注）。
- **驳回**：0（未发现"已声明边界 + fail-fast 守卫"被漏看的条目；C7-003/C7-008 的部分声明不足以构成驳回，理由见逐条判定）。
- **现状标注统计**：新发现 ×19；已知未修复 ×0；已修复 ×0（历史审计五批 + git log/-S 检索均无既有立项；最近旁证为 r2 性能审计对 fire mail 重复的性能视角登记与 P1-6.7-007 的 Exception 吞点修复，均不构成本维度任一条目的既有编号）。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状标注 |
|---|---|---|---|---|
| C7-001 | P1 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/materialization/InMemoryMaterializationPoint.java | 物化 bypass 库 append-only 无任何裁剪契约，materialization-enabled 连续流必然无界增长，overflow-bypass 还绕过声明的 IBufferPool 上游控制 | 新发现 |
| C7-011 | P1 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java | standby 降级不取消在途 checkpoint persist，完成/提交扇出路径无 active/fencing 复核——僵尸 standby 成为持久平面第二写者，注释声明与实现相反 | 新发现 |
| C7-002 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcClusterRegistry.java | 任务尝试账本（JDBC 行 + 内存 history）append-only，removeTaskAssignment 零生产调用方，G56 注释宣称的清理路径不存在 | 新发现 |
| C7-003 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java | msg_queue 表 INSERT-only 无运行时保留路径，而 launch 生产入口默认装配该服务——声明测试定位与生产装配矛盾 | 新发现 |
| C7-004 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ProcessingTimeServiceDriver.java | 任务线程长阻塞期间每 100ms 重复投递 timer-fire mail 且无在途去重，控制面 mailbox 无界积压 | 新发现（旁证：2026-09-27 r2 perf §6 性能视角登记，非既有立项） |
| C7-005 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java | 持续失败路径周期性错误日志无抑制（poll/heartbeat/stale-ACK/commit-retry 等 5+4 点，含 C7-014 并入的 ⑥-⑨），故障期日志放大可占满日志面 | 新发现 |
| C7-006 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SideOutputRegistry.java | JVM 级静态注册表 register-only，remove 无生产调用方——长存活宿主跨作业累积并强引用已停作业算子链 | 新发现 |
| C7-007 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/metrics/MicrometerStreamTaskMetrics.java | releaseHolder 只置空不移除条目/meter——jobId churn 下每作业净增约 11 个 meter 永驻注册表，与同族完整释放不对称 | 新发现 |
| C7-012 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java | 三个算子 counter 以 identityHashCode 做标签且无移除路径——每次任务重部署净增 meter 永不复用（C7-007 失效面 ⑦⑧⑨） | 新发现 |
| C7-008 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/alert/WebhookAlertChannel.java | delivered 历史列表只增不减，低速慢性累积（理论风险，未具体化到故障级） | 新发现 |
| C7-009 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java | 周期调度循环仅捕 Exception：Error 逃逸致检测/注入面静默永久死亡且无活性看门狗（理论风险；含 C7-017 并入的 core 侧 ⑧） | 新发现 |
| C7-010 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java | 租约/停顿/冷却全用墙钟，跳变下批量误判/漏检 + NTP 隐性依赖未声明（理论风险；含 C7-018 并入的 TTL 前跳数据丢失方向 ⑧⑨） | 新发现 |
| C7-013 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/LocalFileCheckpointStorage.java | 崩溃残留 .tmp 文件不被任何列举/保留/剪枝过滤命中，随崩溃次数单调累积（含 C7-015 并入的 .sst.tmp-* 段文件面） | 新发现 |
| C7-016 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java | finishedSplits 只增不删且全仓零生产读者——声明用途（restore round-trip）未实现的死增长面 | 新发现 |

（并入条目 C7-014/C7-015/C7-017/C7-018/C7-019 不设独立行，随母条目 C7-005/C7-013/C7-009/C7-010/C7-002 修复批一并处理。）
