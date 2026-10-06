# 2026-10-06 nop-stream 对抗性审查（开放发现，第 1 轮）

- **审查人**: 独立盲审 subagent（未读取 ai-dev/audits、ai-dev/plans、ai-dev/logs、ai-dev/analysis 下的任何历史文件）
- **方法**: 开放式发现导向审查。第 1 轮从 2PC sink 契约、checkpoint 协调面（CheckpointCoordinator/JobCoordinator/TaskManager）、数据面（InputGate/RecordWriter/RemoteInputChannel/RemoteResultPartition）、定时器服务、状态快照 serde、连接器（file/JDBC 2PC）、source 协调器注册表等正确性关键路径入手，对每个候选发现做了第二层验证（含对 `ai-dev/bugs/2026-10/2026-10-02-stream-2pc-commit-key-single-subtask.md` 的重开比对——本报告不重复该已修复缺陷）。
- **轮次记录**: 第 1 轮开放探索产出下述 AR-01～AR-09；第 2 轮基于线索复核（RescaleStateAssembler restoreFromEpoch 是否吞异常——实为 rethrow，放弃；chained operator 是否共享 keyed backend 导致 N 份快照——实为每 operator 独立 backend，放弃；TwoPhaseCommitSinkFunction.finishCommit 空 pending 回落 commit(epochId)——两个内建 sink 均安全跳过，放弃）。第 2 轮无新增发现，按连续一轮无新发现规则收敛。

---

### [AR-01] StreamSinkOperator 快照成功后的 saveState/prepareCommit 失败被当作成功 ACK 上报（错误优先级颠倒，exactly-once 潜在破口）
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSinkOperator.java:108-148`（关键：116-128 失败源，136-146 错误交付优先级）
- **证据片段**:
  ```java
  try {
      snapshotResult = snapshotState(context);            // ① 先成功
      if (userFunction instanceof CheckpointParticipant) {
          TaskStateSnapshot participantState = ((CheckpointParticipant) userFunction).saveState(barrier.getId());
          ...
          ((CheckpointParticipant) userFunction).prepareCommit(barrier.getId());   // ② 后失败
      }
  } catch (Exception e) {
      snapshotError = e;                                   // 只记录日志
  }
  ...
  if (snapshotResult != null) {
      snapshotCallback.accept(snapshotResult);             // ③ ②失败时仍按成功 ACK 交付
  } else if (snapshotError != null) { ...failureResult... }
  ```
- **严重程度**: P1
- **现状**: `snapshotState` 成功后，同一 try 块内的 `saveState`（把 sink 批次写入 pendingCommits，含 JDBC sink 的 `ensureInitialized()` DB 调用、file sink 的 temp 文件写入+fsync）或 `prepareCommit` 抛异常时，交付给 CheckpointBarrierTracker 的是**不含 participant 状态的成功快照**，错误被降级为 LOG.error 后吞掉。`CheckpointBarrierTracker` 注释里"关闭快照失败被当作成功 ACK 的静默损坏路径"的承诺在这条路径上不成立。
- **风险**: 该 epoch 的 durable checkpoint 缺失 sink 的 pendingCommits，但 source cursor 已推进到 epoch N。作业**继续运行时无害**（批次随下一 epoch 提交）；一旦之后从该 epoch **恢复**，N-1→N 之间的记录既未提交也不在任何 pending 中——恰好在"磁盘满/DB 抖动/首次 ensureInitialized 失败"这类该机制本来要防护的场景下，把 exactly-once 静默降级为丢数据。同类历史缺陷（2026-10-02 的 DmlOp 序列化失败导致 checkpoint 存储失败）走的是"整体失败"路径，而本缺陷把同类失败改写为"半个成功"。
- **建议**: 交付判定改为先看 `snapshotError`（非空即交付 tagged failureResult，绝不交付成功结果）；或将 ② 的失败合入 `snapshotResult.setError(...)` 走 tracker 的 fail-fast abort 通道。补一个"saveState 抛异常后 ACK 必须为 failure"的回归测试。
- **信心水平**: 确定（代码路径无歧义；数据丢失面在恢复场景下成立）
- **发现来源视角**: 异常路径侦探

---

### [AR-02] CheckpointCoordinator 持有 monitor 执行 sink finishCommit（外部 JDBC/RPC I/O），异步持久化的解耦被 commit 侧效应抵消
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:721-723（synchronized 块）、938-999（onCompletePersistSuccess→notifyParticipantsFinishCommit@989）、1466-1478（finishCommit 循环）`
- **证据片段**:
  ```java
  synchronized (this) {
      onCompletePersistSuccess(completed, pending, true);   // async persist 路径重新持锁
  }
  // onCompletePersistSuccess 内：
  notifyParticipantsFinishCommit(checkpointId, true);
  //   → participant.finishCommit(epochId, true) → TwoPhaseCommitSinkFunction.commit(eid)
  //     → JdbcTwoPhaseCommitSink: openConnection + executeBatch + commit  （阻塞外部 I/O）
  ```
- **严重程度**: P2
- **现状**: async persist 重构把 `storeCheckPoint`/`storeEpochManifest` 的存储 I/O 移出了 monitor，但紧随其后的 sink 提交侧效应（每个 sink 打开独立 JDBC 连接、整 epoch 批量写+fsync；分布式模式下是 commit forwarder 对全部节点的 RPC fan-out）仍在 `synchronized(this)` 内执行。abort 路径（`abortPendingCheckpoint`→`notifyParticipantsFinishCommit(false)`→rollback/abort I/O）与超时线程同理。
- **风险**: 大 epoch（如百万行批次）或慢库时，每次 checkpoint 完成 host monitor 数秒：`acknowledgeTask`（ACK 处理）、`tryTriggerCheckpointWithReason`（周期触发）、`abortPendingCheckpoint`（超时裁决）全部排队；ACK 延迟又推高下一 epoch 撞上 checkpointTimeout 的概率，形成"慢提交→checkpoint 超时 abort→重试"的自激。10x 数据量下最先崩的就是这条 monitor。
- **建议**: 把 `notifyParticipantsFinishCommit` 移出 monitor（snapshot 侧的 `pendingCheckpoints.remove`/`forceComplete` 已在锁内完成，participant 通知只依赖已发布的不可变状态）；或引入独立的 commit executor（TM 侧已有 `tm-commit-*` 同款先例，TaskManager.java:224-228）。
- **信心水平**: 很可能（持锁事实确定；实际停顿幅度取决于 sink 延迟分布）

---

### [AR-03] MemoryStateSerDe ListState payload 不冻结元素引用——冻结协议在 list 族上不对称，evictor 窗口内容路径直接暴露
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:230-237（writeListPayload）`
- **证据片段**:
  ```java
  private static void writeListPayload(Map<String, Object> entry, Object storedValue,
                                       IStreamSerializer<Object> valueSer) {
      List<Object> serializedList = new ArrayList<>();
      for (Object v : (List<?>) storedValue) {
          serializedList.add(serializeWithSerializer(v, valueSer));  // valueSer==null 时返回活引用
      }
      entry.put("listValue", serializedList);
  }
  ```
- **严重程度**: P2
- **现状**: R5 ST-01 为 Value/Map/Reducing/Appending 四族补齐了"快照在任务线程、持久化在异步线程"的防御性冻结（`freezeRawValue`/`copyAppendingPayload`/map key 的 `defensiveCopy`），唯独 ListState payload 在无自定义 serializer 时保留**活元素引用**（注释给出的理由是保住 ContainerValueCodec 的运行时类型——但 list 路径根本没有调用 `ContainerValueCodec.encode`，而 map 路径有）。快照里的 `serializedList` 是新 List、旧元素：追加不影响快照，**就地修改已存在元素**会泄漏进异步持久化的 JSON。
- **风险**: 快照→落盘窗口内任务线程改写元素 → durable checkpoint 内容撕裂（半新半旧），恢复后窗口/列表状态不确定。具体暴露面：① evictor 路径的窗口内容状态本身就是 ListStateDescriptor（WindowOperator，注释自证"the evictor path uses a list descriptor"）；② 用户在 KeyedProcessFunction 里声明的 ListState（可变 bean 元素）。这是同一冻结协议内部的缺口，而非理论威胁。
- **建议**: 对无 serializer 的 list 元素套用与 map key 一致的 `SnapshotCopyUtil.defensiveCopy`（类型信息经既有 restore 端 `JsonTool.parseBeanFromText` 回水合已可覆盖）；或与 map payload 对齐引入 list 版 ContainerValueCodec 包装。补"快照后突变元素、断言持久化内容不变"的回归测试。
- **信心水平**: 确定（代码不对称是事实）；触发概率评估为很可能
- **发现来源视角**: 组合爆炸测试者（异步持久化 × 可变元素 × list 族）

---

### [AR-04] FLIP-27 enumerator 状态持久化只在 LOCAL 路径接线：分布式 manifest 的 sourceEnumeratorSnapshots 恒空；注册表按裸 vertexId 全局键存在跨作业串线面
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:648-700（仅此路径调用 registerSourceApiVertices）；ops/OpsJobManager.java:151-152（直建 CheckpointCoordinator，无注册）；core/.../source/coordinator/SourceCoordinatorRegistry.java:27-31（v1 限制自述）；runtime/checkpoint/CheckpointCoordinator.java:1631-1654（snapshotSourceEnumerators 消费全局注册表）`
- **证据片段**:
  ```java
  // SourceCoordinatorRegistry javadoc（v1 limitation）:
  // keyed by vertex id only (not jobId+vertexId) ... fine for LOCAL-mode test execution
  private static final ConcurrentMap<Integer, LocalSourceCoordinator<? extends SourceSplit, ?>> COORDINATORS =
          new ConcurrentHashMap<>();
  // CheckpointCoordinator（作业级组件）直接读全局表：
  LocalSourceCoordinator<...> coord = SourceCoordinatorRegistry.get(vertexId);
  ```
- **严重程度**: P2
- **现状**: enumerator 状态（已发现/已分配/已完成 split）进入 epoch manifest 的唯一接线是 `GraphModelCheckpointExecutor.createCoordinator(jobGraph)`（LOCAL 执行路径）。分布式路径（`OpsJobManager.submit` 直建 coordinator、JobCoordinator 接管）从不注册 source vertex → manifest 该段恒空；且 LocalSourceCoordinator 本就注册在 **TM JVM** 的全局表里，JC 进程读同名表必为空——该机制在分布式形态下结构性失效。恢复后果由代码注释自证："enumerator state is lost on restore, and the source would re-read already-finished splits"。另外多作业共存于一个 JVM（OpsJobManager 形态、或 LOCAL 顺序作业重叠窗口）时，两个作业的 vertex id 都从同一编号空间起步，`registerIfAbsent(vertexId)` 会让后到作业的 SourceReaderOperator 复用**前一个作业的** enumerator。
- **风险**: 分布式 file-source/FLIP-27 作业每次恢复都从 split 全集重扫（幂等 sink 下是重复处理，非幂等 sink 下是重复副作用）；多作业同 JVM 时是跨作业数据串线的正确性风险，且失败形态是静默复用（无 typed 错误）。
- **建议**: 短期——在 `OpsJobManager.submit`/分布式装配路径显式拒绝或 WARN source-api 顶点（no-silent）；中期——把 enumerator 状态经 ACK 通道上收（TM 侧快照→manifest）或按 jobId+vertexId 重建注册表键。至少要把 SourceCoordinatorRegistry 的 v1 限制从 javadoc 提升到 owner doc 的"分布式支持边界"。
- **信心水平**: 很可能（机制链路已核实；"分布式+source-api 是否属于受支持组合"待维护者裁定）

---

### [AR-05] SOURCE/SELF_CONTAINED 角色心跳上报 TM 墙钟——挂死的 source 线程对 stall 检测结构性不可见
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:436-443（livenessValue）`
- **证据片段**:
  ```java
  private long livenessValue(StreamTaskInvokable inv) {
      StreamTaskInvokable.TaskRole role = inv.getRole();
      if (role == StreamTaskInvokable.TaskRole.SOURCE
              || role == StreamTaskInvokable.TaskRole.SELF_CONTAINED) {
          return CoreMetrics.currentTimeMillis();   // 墙钟：永远新鲜
      }
      return inv.getLastActivityTime();
  }
  ```
- **严重程度**: P3
- **现状**: 注释明确这是为了"空闲 source 不被误杀"的裁定（source run loop 可能整生命周期阻塞）。代价是：一个真挂死的 source（无超时的外部拉取、死循环用户代码、connector 线程卡死）的 liveness 永远等于当前墙钟，`detectFailures` 的 taskStall 分支对它永不触发；只要节点进程活着（心跳线程独立），作业就表现为健康的 RUNNING，数据面零推进。
- **风险**: 故障排查依赖用户感知"没数据出来"再人肉介入；健康状态机/告警链路全程无信号。已知 `lastProgressTime`（每次发射都刷新）与 `lastActivityTime` 双信号俱存，本可支撑一个宽松得多的 source 级 stall 阈值而不引入误杀。
- **建议**: 为 SOURCE/SELF_CONTAINED 增加独立的长窗口 stall 检测（如 `max(lastProgress, lastActivity)` 超过可配的 sourceStallTimeoutMs 才触发，默认关闭或小时级），保住"空闲 source 不误杀"的同时给挂死一个最终观测面。
- **信心水平**: 确定（行为即代码）；影响评估为真实但被注释承认的取舍
- **发现来源视角**: 10x 规模运维者

---

### [AR-06] JDBC 2PC 提交台账（ledger）无任何保留/清理策略——按 (namespace, epoch, subtask) 永久增长
- **文件**: `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:820-829（writeLedgerEntry）；全仓 grep 确认无 prune/clean/retention 调用点`
- **证据片段**:
  ```java
  private void writeLedgerEntry(Connection connection, String namespace, long epochId,
                                int subtaskIndex) throws SQLException {
      ...
      ps.setTimestamp(4, CoreMetrics.currentTimestamp());
      ps.executeUpdate();     // 每个 (namespace, epoch, subtask) 一行，从不删除
  }
  ```
- **严重程度**: P3
- **现状**: ledger 行只服务幂等提交守卫，语义上只要求覆盖"仍在可能被重提交窗口内的 epoch"。10s checkpoint 间隔 + parallelism P 的作业每天新增 8640×P 行/分支，无 TTL、无 compaction、运维工具族（`StreamMaintenanceMain` reset-state/reshard）也不触及。
- **风险**: 长期运行作业的用户库被只增不减的台账拖慢（主键查询尚可，但表膨胀影响备份/容量）；运维第一次发现通常是在 DBA 侧而非引擎侧。
- **建议**: 在 sink 或 `StreamMaintenanceMain` 增加保留策略（如删除 epoch < 当前 durable epoch - 安全余量 N 的行），或在 owner doc 明示"台账需 DBA 侧自行清理 + 清理上界公式"。
- **信心水平**: 确定（无清理代码是可穷举验证的事实）

---

### [AR-07] LocalFileCheckpointStorage 原子 rename 但无 fsync——checkpoint 权威存储的崩溃纪律弱于它所保护的 file 2PC sink
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/LocalFileCheckpointStorage.java:93-105（storeCheckPoint）、394-416、489-510（storeEpochManifest）`
- **证据片段**:
  ```java
  Files.write(tempPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  Files.move(tempPath, checkpointPath, StandardCopyOption.ATOMIC_MOVE, ...);
  // 无 FileChannel.force —— 与 FileTwoPhaseCommitSink 的 forcePath/fsync 纪律（R5 CON-04）不对称
  ```
- **严重程度**: P3
- **现状**: 进程崩溃安全由 temp+ATOMIC_MOVE 保证（与 file sink 相同）；但 OS/断电崩溃时，`Files.write` 的页缓存内容可能未落盘。file 2PC sink 专门补了"数据 fsync 先于 checkpoint ACK（R5 CON-04）+ manifest fsync + 目录 fsync"，而作为一切恢复起点、语义上更权威的 checkpoint 存储（含 sink pendingCommits 的 JSON 载体）反而不具备同等级别。
- **风险**: 断电场景下出现"file sink 数据文件已 fsync 持久、checkpoint manifest 丢失/回退到旧 epoch"的组合——恢复会重放旧 epoch（file sink 幂等守卫可挡住重复提交，但 JDBC sink 守卫依赖 pendingCommits 里恢复出的台账批次，撕裂的 JSON 还会触发 checksum/format-version typed 错误，把一次可恢复的崩溃升级为需人工 reset-state）。
- **建议**: 对 checkpoint 与 manifest 的 temp 文件在 move 前补 `FileChannel.force`（成本为每 checkpoint 一次，与 file sink 同量级），把"checkpoint 是 durable 锚点"的语义落到与 sink 相同的崩溃纪律上。
- **信心水平**: 确定（代码无 fsync）；影响评估为有趣的猜测（需真实 OS 崩溃窗口验证）

---

### [AR-08] reportTaskStatus 的 FAILED 分支在 livenessLock 之外写 subtaskLiveness——同一映射的锁纪律不一致
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1111（FAILED 分支）；对照 1081-1087（COMPLETED 分支持锁）、1195-1211（心跳持锁 + tombstone 检查）`
- **证据片段**:
  ```java
  } else {
      // Any other terminal report (FAILED) ...
      subtaskLiveness.put(livenessKey, now);        // 不持 livenessLock
      deployIssuedAtMs.remove(livenessKey);
  }
  ```
- **严重程度**: P3
- **现状**: `subtaskLiveness` 的其余两个写入方（COMPLETED 移除+ tombstone、心跳 merge）都在 `livenessLock` 内并以 tombstone 挡"完成任务的冻结时间戳复活"（REG-02）；FAILED 分支的裸 `put` 在锁外，可穿插进 COMPLETED 分支的临界区。fencing epoch 校验挡住了跨代僵尸报告，因此实际窗口是同代内的窄竞态（如 FAILED 报告与该任务最后一批心跳、或与 COMPLETED 报告的三方交错），后果是冻结/过期时间戳复活 → `taskTimeoutMs` 后对已完成子任务产生一次假 TASK_STALL 恢复。
- **风险**: 低概率的伪恢复；更重要的是锁纪律的"局部正确"会误导后续改动（新增分支照抄裸 put 就扩大了竞态面）。
- **建议**: FAILED 分支纳入 `synchronized (livenessLock)` 并复用 tombstone 检查；或为该映射收敛一个单一的 liveness 更新入口。
- **信心水平**: 确定（不一致是事实）；触发评估为低概率

---

### [AR-09] AbstractStreamOperator.snapshotState 声称 null 安全，实际两处 context 解引用会 NPE
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java:284-296、307-321`
- **证据片段**:
  ```java
  public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
      OperatorSnapshotResult result = new OperatorSnapshotResult();
      // Null-safe: some legacy callers/tests invoke snapshotState(null).
      if (context != null) {
          result.setCheckpointId(context.getCheckpointId());
      }
      if (this instanceof CheckpointParticipant) {
          TaskStateSnapshot participantState =
                  ((CheckpointParticipant) this).saveState(context.getCheckpointId()); // context==null → NPE
      }
      ...
      if (operatorStateBackend != null) {
          OperatorSnapshotResult opResult = operatorStateBackend.snapshotState(context.getCheckpointId()); // 同上
      }
  ```
- **严重程度**: P3
- **现状**: 注释承诺对 `snapshotState(null)` 兼容（"legacy callers/tests"），但仅在"非 participant 且 operatorStateBackend == null"的窄组合下成立；participant 算子或挂了 operator state backend 的算子传 null 直接 NPE，且裸 NPE 违反项目两级错误处理约定（模块内部也应用 typed StreamException 携带上下文）。
- **风险**: 误导后续调用者按注释传 null；真触发时 NPE 无 checkpoint 上下文，排障成本高。属于"文档/契约与代码不符"类小缺陷。
- **建议**: 要么在入口对 null 抛 `StreamException(ERR_STREAM_NULL_ARG)`（显式拒绝 legacy 口径），要么全程 `context == null ? -1 : context.getCheckpointId()` 兑现注释；二选一，删除虚假承诺。
- **信心水平**: 确定

---

## 总评

最值得关注的三个方向：

1. **"半个成功"路径（AR-01）**：本模块对快照失败的处理下了重注（Stage 45 tagged failure、abort 通道、fail-fast），但 sink 侧 barrier 处理的错误优先级让"snapshot 成功之后、participant 状态写入之前"的失败整类漏网。这是错误处理拓扑上最后一块不对称拼图，且直接命中 exactly-once 的恢复语义。
2. **协调面 monitor 上的外部 I/O（AR-02）**：async persist 重构完成了一半——存储 I/O 出了锁，commit 侧效应还在锁里。这是 10x 规模下第一个排队点，也是 TM 侧已经解决（`tm-commit-*` 专用执行器）而 JC 侧没有对齐的姊妹问题。
3. **LOCAL/分布式能力面漂移（AR-04，含 AR-05 的观测盲区）**：enumerator 状态持久化、source 级 stall 检测这类能力在 LOCAL 演练路径上齐备、在分布式主路径上静默缺位。模块的测试矩阵以 LOCAL/embedded 为主，这类"演练环境全绿、生产拓扑缺功能"的漂移靠现有 gated e2e 抓不住，值得作为一类问题系统性盘点（哪些契约只有 LOCAL 接线）。

## 盲区自评

- 未深读：WindowOperator 全文（2073 行，只验证了 snapshot/restore/evictor-list 分支）、CEP NFA/SharedBuffer 的匹配语义与 GC 细节（只确认了缓存结构与白名单反序列化）、nop-stream-flow 的 XDSL 编译全链、nop-stream-sql、RocksDB 后端、MaxParallelismReshardMigration、StreamControlRpc* 线协议、SupervisionLoop。
- 未验证：真实 OS 崩溃窗口（AR-07）、多作业同 JVM 的实际部署形态是否被官方支持（AR-04 的裁定依赖）、大 epoch 下 AR-02 的实际停顿幅度。
- 重开比对：已对照 `ai-dev/bugs/2026-10/2026-10-02-stream-2pc-commit-key-single-subtask.md`（stableHash/DmlOp 序列化）与 `2026-08-12-nop-stream-concurrent-recovery-dedup-race`（recovery 去重竞态）——本报告未重复两者；AR-01 与 2026-10-02 缺陷 1 的失败源同族（JSON/DB 拒绝），但失效形态不同（整体失败 vs 半个成功），判定为残留的新缺口而非旧问题重开。
- 刻意不报：`@Inject` 可见性、`_` 前缀生成物、防御性注释堆叠本身（本模块防御密度显著高于均值，多数候选问题在深读后被代码自证已修复而放弃，见轮次记录）。

## 严重程度分布

| 严重程度 | 数量 | 编号 |
|---|---|---|
| P0 | 0 | — |
| P1 | 1 | AR-01 |
| P2 | 3 | AR-02, AR-03, AR-04 |
| P3 | 5 | AR-05, AR-06, AR-07, AR-08, AR-09 |
| 合计 | 9 | — |

---

## 复核结论

> 复核人：独立审计复核员（与初审者无关）。方法：逐条实读被引文件与行号核对证据；对 P1 级 AR-01 从代码独立推导完整链路（调用时序、异常传播、ACK 路径、tracker 计数误导、恢复期后果），并反向搜寻反证（异常处理器、重试、失败上报通道、协调面校验）；对 AR-02/03/04 与同批 R6 维度报告（2026-10-06-0522-deep-audit-nop-stream-quality-r6 的 dim-04 CC-01/CC-04、dim-02 ST-01）做同根因/同失效面比对。现状归属仅引 R5 summary 与 R6 同批维度报告。未修改本报告正文；未运行构建或测试。

| 发现 | 判定 | 现状归属 | 复核要点 | 与维度报告的疑似重复关系 |
|---|---|---|---|---|
| AR-01 | 保留 P1 | 新发现（R6 九份维度报告均未登记；dim-03 通读 StreamSinkOperator 全文仍未立案） | 证据与行号全部属实；独立推导链成立（见下节）；反证通道逐一排除；一处证据表述修正（JDBC ensureInitialized 非 DB 往返）不影响判定 | 无重复（独有发现） |
| AR-02 | 保留（建议合并裁定） | 新发现 | 持锁事实逐行核实：executePersistAsync:721-723 synchronized → onCompletePersistSuccess:989 → notifyParticipantsFinishCommit:1466-1478 同步循环 participant.finishCommit；abort 侧 :1033/:1091/:1572 同理（onCompletePersistFailure 注释自证 "Caller MUST hold the coordinator monitor"） | **同族同根因**：与同批 R6 dim-04 CC-01 为同一 monitor、同一调用链（CC-01 引锚点 721-723/1466-1478 完全重合）。CC-01 面孔=分布式 commit forwarder/abort RPC fan-out + recoveryLock 放大；AR-02 面孔=LOCAL sink JDBC/file 提交 I/O 在锁内 + tm-commit-* 先例。建议合并为一条，两面互补 |
| AR-03 | 保留（确认重复，建议合并） | 已知未修复（同批 R6 dim-02 ST-01 已登记同一缺陷且更全面；R5 ST-01 修复 plan 2311 的残余） | 代码不对称核实成立：writeListPayload:230-237 经 serializeWithSerializer:823-828 在 valueSer==null 时返回活元素引用；对照 freezeRawValue:223-228、copyAppendingPayload:198-207、map key :247 均已冻结 | **确认重复（子集）**：R6 ST-01（P1）覆盖 List 元素 + Map value 两面并给出生产触发面（EquiJoinOperator.bufferState matched-flag），AR-03 仅 List 面且未发现 EquiJoin 触发点。建议合并进 ST-01，定级以 P1 为准 |
| AR-04 | 保留 P2（后半建议并入 CC-04） | 前半=新发现；后半=R6 CC-04 已登记 | 前半核实：registerSourceApiVertices 唯一调用点 createCoordinator:648-657（LOCAL 路径）；OpsJobManager.submit:151-152 直建 coordinator 无注册；静态注册表在 TM JVM、JC 进程读同名表必空 → 分布式结构性失效；GraphModelCheckpointExecutor:644-646 注释自证恢复后果（重扫已完成 split）。后半核实：SourceCoordinatorRegistry:35-36 裸 Integer 键 + registerIfAbsent（SourceReaderOperator:176）、unregister 仅 LOCAL（StreamExecutionEnvironment） | 后半**确认重复**：与 R6 dim-04 CC-04 同根因（进程级静态 map 裸 vertexId 键 + 无分布式注销）同失效面（同 JVM 跨作业串台），建议并入 CC-04。前半（分布式 manifest sourceEnumeratorSnapshots 恒空 → 恢复重扫）为 CC-04 未覆盖的独立失效面（R6 全批无 enumerator manifest 相关登记），支撑本条独立保留 |
| AR-05 | 保留 P3 | 新发现 | livenessValue:436-443 属实；heartbeat javadoc:354-362 自证取舍；detectFailures:1459-1471 stall 分支对该类任务结构性不可见属实；node-lease 为唯一兜底且仅覆盖节点级死亡；lastProgressTime 存在（StreamTaskInvokable:428-441）但 TaskProgress 只携带 livenessValue、不上报协调面 | 无重复 |
| AR-06 | 保留 P3 | 新发现 | writeLedgerEntry:820-829 属实；全仓 grep 无 ledger 删除/保留代码（connector 内无 prune/clean；StreamMaintenanceMain 无任何 ledger 处理）；8640×P/天/分支算术正确 | 无重复 |
| AR-07 | 保留 P3 | 新发现 | storeCheckPoint:98-100 Files.write + ATOMIC_MOVE 无 fsync（全文件无 FileChannel.force）；对照 FileTwoPhaseCommitSink saveState fsync:248、commit fsync:322、manifest+目录 fsync:344——不对称属实。报告自评"影响为猜测"与 P3 定位一致 | 无重复 |
| AR-08 | 保留 P3 | 新发现 | FAILED 分支:1111 裸 put 属实（reportTaskStatus 全程不持 livenessLock）；对照 COMPLETED:1081-1087 持锁+tombstone、心跳:1195-1211 持锁+tombstone+merge。subtaskLiveness 为 ConcurrentHashMap:228 → 无内存损坏，纯锁纪律/原子性不一致；fencing:1047-1054 挡跨代——报告"同代窄竞态+误导后续改动"定性准确 | 无重复 |
| AR-09 | 保留 P3 | 新发现 | 注释 :288 的 null 承诺仅覆盖 setCheckpointId；:296（participant 分支）与 :315（operatorStateBackend 分支）在 context==null 时 NPE 属实。实际 snapshotState(null) 调用方仅两个测试（TestMemoryKeyedStateBackendFix:228/308，恰好落在窄安全组合内）——"契约与代码不符"定性准确 | 无重复 |

### P1 重点复核详情（AR-01 独立推导链与裁定）

**推导链（全部从 live code 独立重推）**：

1. **触发点与调用时序**：`StreamSinkOperator.processBarrier`（:108-148）在**同一 try 块**内顺序执行 `snapshotState`(:114) → `saveState`(:117) → `prepareCommit`(:127)。`snapshotResult` 在 :114 赋值后即非空；`saveState`/`prepareCommit` 是其后的独立失败源。
2. **异常传播**：② 抛出 → :131-134 catch → 仅 `LOG.error` + `snapshotError` 置位，不 return、不重抛（方法签名声明 `throws Exception` 但异常被吞）。
3. **"当成功 ACK"的确切路径**：:136-138 **先判 `snapshotResult != null`** → `snapshotCallback.accept(snapshotResult)`（快照不含 `participant-pending-commits`）→ `StreamTaskInvokable.setupSnapshotCallbacks`:496-506 → `CheckpointBarrierTracker.acknowledgeOperator`:197-206 **成功分支**（per-operator 去重 :182 → 计数 :202 → 末个 ACK 时 fire completionCallback）→ `TaskCheckpointWiring`:154 `coordinator.acknowledgeTask(...)` → durable checkpoint 落盘（source cursor=N，无 sink pendingCommits）。tagged failure 分支 :139-146 仅 `snapshotState` 本身失败（result==null）时可达——错误优先级颠倒属实。对照 `AbstractStreamOperator.processBarrier`:407-435 同构交付逻辑但安全：其 try 块内只有 `snapshotState` 一个失败源，失败时 result 必为 null——缺陷精确锁定在 sink 覆写引入的"成功后的第二步失败"。
4. **下游如何被误导**：tracker 把该 epoch 记为该 operator 已确认（`acknowledgedOperators`）；协调面按成功 epoch 推进（`forceComplete` → §12 invariant 5 → `notifyParticipantsFinishCommit`:989）。tracker javadoc :84-89 明文承诺"当 abortCallback 非空时，携带 error 的 ACK 走 abort 通道而**不**作为成功快照交付……关闭快照失败被当作成功 ACK 的静默损坏路径"——该承诺在本路径不成立。
5. **反证搜索（均不成立）**：(a) 无重试——processBarrier 不重试 saveState；(b) 失败上报通道存在但不可达——abort 通道已在生产接线（`TaskCheckpointWiring`:155-156 `reportTaskCheckpointFailure`、`RemoteTaskDeploySupport`:198 `remoteAbortListener`），但仅由 tracker:189-196 的 `hasError()` 分支触发，本路径交付的结果永无 error；(c) 协调面无校验——`acknowledgeTask`/`storeCheckPoint` 不要求 participant 键存在；(d) `lastSnapshotResult` 不更新（:130 跳过）只影响 abort 清理，不改变 ACK。
6. **恢复期丢数据推导**：两个内建 sink 均为 saveState-first——`FileTwoPhaseCommitSink.saveState`:238-255 先 `writeLines`+`forcePath`(fsync) :243/:248、**之后**才 `pendingCommits.put(epochId)` :249；`JdbcTwoPhaseCommitSink.saveState`:608-617 同构（先 `ensureInitialized` :609 后 move batch :610-614）；两者 `preCommit` 均为 no-op（File:258-260 / Jdbc:621-623，javadoc 自证）。磁盘满/IO 错误场景：异常发生在批次入 pending **之前** → 批次留 `currentBuffer`、pending 无 N、durable 快照无 participant 态。**继续运行无害**：批次随 epoch N+1 的 saveState 入 pending → 随 N+1 提交（与报告一致）。**从 epoch N 恢复**：`StreamSinkOperator.restoreState`:189-199 从 durable 快照重建 pendingCommits（无 N）→ `restoreFromEpoch`（TwoPhaseCommitSinkFunction:234-277）只重提交 eid≤N 的 pending → 批次 N 不在 pending、缓冲随进程死亡消失、source cursor=N 不重放 barrier N 之前记录 → **N-1→N 之间记录静默丢失**。变体：若失败发生在批次已入 pending 之后（如 super.saveState 阶段），恢复时 restoreFromEpoch 会重提交 N、幂等 guard 挡重复——损失面集中在"失败先于 pending 入账"窗口，但这恰是 file sink 的 writeLines/fsync 主失败点。
7. **证据表述修正（不改判定）**：`JdbcTwoPhaseCommitSink.ensureInitialized`(:435-452) 是本地方言解析 + SQL 拼接（无 DB 往返），报告"ensureInitialized() DB 调用"表述偏重；真实外部 I/O 失败源是 file sink 的 writeLines+forcePath（磁盘满即报告点名的场景，成立）+ 自定义 sink 可覆写的 `preCommit`/`saveState`。操作者级缺陷与具体 sink 无关，内置 file sink 一类即足以支撑定级。
8. **独有性核实**：R6 批次九份维度报告未登记此缺陷（dim-03 复核自述通读 StreamSinkOperator 全文，仅立案 copyForSubtask 共享问题 CON-03）；与本模块 2026-10-02 已修缺陷（DmlOp 序列化失败 → 整体 checkpoint 失败）失效形态不同（整体失败 vs 半个成功），非旧问题重开。

**裁定**：**保留 P1**。链路每一环均在 live code 验证；恢复期静默丢数据破口成立，且命中该机制本要防护的故障场景（磁盘满/IO 抖动）；修复建议（交付判定先看 snapshotError，或将 ② 失败合入 tagged failure 通道）方向正确，应补"saveState 抛异常后 ACK 必须为 failure"回归测试。

## 最终保留项（复核后）

| 编号 | 复核后严重程度 | 文件路径 | 一句话摘要 | 现状归属 |
|---|---|---|---|---|
| AR-01 | P1 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSinkOperator.java | sink 快照成功后 saveState/prepareCommit 失败被降级为日志并按成功 ACK 交付，durable checkpoint 缺 sink pendingCommits，恢复期 N-1→N 记录静默丢失 | 新发现（本报告独有） |
| AR-02 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java | coordinator monitor 内同步执行 sink finishCommit/rollback 外部 I/O（LOCAL JDBC/file；分布式为 RPC fan-out），大 epoch 时 ACK/触发/abort 全面排队 | 新发现；与 R6 dim-04 CC-01 同根因同失效层，建议合并裁定（两面互补） |
| AR-03 | P2（并入 ST-01 后以 P1 为准） | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java | ListState payload 无 serializer 时保留活元素引用，快照→落盘窗口内就地改元素泄漏进持久化 JSON | 已知未修复：R6 dim-02 ST-01 同一缺陷（子集面），建议合并进 ST-01 |
| AR-04 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java | enumerator 状态持久化仅 LOCAL 接线（分布式 manifest 恒空 → 恢复重扫已 finish split）+ 注册表裸 vertexId 跨作业串台 | 前半新发现；后半与 R6 dim-04 CC-04 重复，建议并入 CC-04 |
| AR-05 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java | SOURCE/SELF_CONTAINED 心跳上报墙钟，挂死 source 对 stall 检测结构性不可见（被注释承认的取舍） | 新发现 |
| AR-06 | P3 | nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java | 2PC 提交台账按 (namespace, epoch, subtask) 只增不删，无任何保留/清理策略 | 新发现 |
| AR-07 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/LocalFileCheckpointStorage.java | checkpoint 权威存储原子 rename 但无 fsync，崩溃纪律弱于其所保护的 file 2PC sink | 新发现 |
| AR-08 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java | reportTaskStatus FAILED 分支在 livenessLock 之外裸写 subtaskLiveness，同映射锁纪律不一致 | 新发现 |
| AR-09 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java | snapshotState 声称 null 安全，participant/backend 两分支实际 NPE，契约与代码不符 | 新发现 |

**复核统计**：9/9 保留（0 降级、0 驳回）；初审证据与行号全部核实为真，无虚引；一处证据表述修正（AR-01 的 JDBC ensureInitialized 性质）不改变判定。重复关系：AR-03 确认为 R6 ST-01 的子集（建议合并，P1 为准）；AR-02 与 R6 CC-01 同根因同失效层（建议合并裁定）；AR-04 后半与 R6 CC-04 重复（建议并入）。AR-01、AR-05..09 为无重复的独立发现。
