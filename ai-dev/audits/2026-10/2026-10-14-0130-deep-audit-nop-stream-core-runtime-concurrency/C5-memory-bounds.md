# C5 内存有界性与大数据量 — nop-stream-core-runtime（第 1 轮初审）

- **审计维度**: C5（内存有界性与大数据量），对照缺陷族 F7（OOM 同族）与 F11（序列化断链/中间体积）定向检查
- **审计对象（live code，`_` 前缀生成文件不在发现对象内）**:
  - `nop-stream/nop-stream-core/src/main/java`（439 files / 57,135 lines）
  - `nop-stream/nop-stream-runtime/src/main/java`（132 files / 29,922 lines）
- **盲审纪律**: 本报告为第 1 轮初审，未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件（含同目录其他维度报告）；现状标注留待复核阶段。
- **前置阅读**: AGENTS.md、`docs-for-ai/INDEX.md`、owner doc `docs-for-ai/03-modules/nop-stream.md`、设计文档 `ai-dev/design/nop-stream/state-management-design.md`（规模包络唯一数字声明所在）、`checkpoint-design.md` 相关节。

## 检查范围说明

**规模包络基线（规模口径纪律）**：owner doc `nop-stream.md` 未声明数值化规模包络；可辩护的包络声明只有一处——`ai-dev/design/nop-stream/state-management-design.md` §序列化权衡：JSON 反射 + JsonTool 序列化「适合**几十 GB 级状态**」（语境 = RocksDB off-heap keyed 状态后端）。本报告以此为唯一数字参照系；owner doc 内的**有界声明**逐项列出：checkpoint 观测历史 100 条 + retention 1440min（`CheckpointHistory`/`StreamGovernanceConfig`）、终态作业记录 retention、2PC commit 执行器有界队列 + CallerRuns（plan 369 R5-CC-10）、webhook 告警有界队列（容量常量 + 满丢弃 WARN）、DB 数据面 backlog 门控（WI6，opt-in）、`RemoteInputChannel` 队列 1024 + 有界 enqueue 等待（WI16）。

**执行方式**：按 C5 正文六步执行——(1) 盘点全部随输入增长容器（数据面队列/回放段/物化 store/邮箱/算子缓冲/定时器表/keyed 状态）；(2) 无界 API 使用点（`readAllBytes` 全量读 6 处逐一核对语境）；(3) 物化 vs 流式（checkpoint 单文档 JSON、join/window 工作视图、reduce 全量 map、reshard 离线工具）；(4) 缓存淘汰（静态注册表、类名缓存、实例标签指标）；(5) 峰值叠加验算（checkpoint 序列化链峰值倍数、背压停顿期邮箱累积速率）；(6) F7/F11 定向核对。重点通读文件：`ResultPartition`、`InputChannel`、`InMemoryMaterializationPoint`、`BufferPool`、`TaskMailbox`、`ProcessingTimeServiceDriver`、`EquiJoinOperator`、`OverWindowOperator`、`StreamReduceOperator`、`WindowOperator`（缓冲/清理面）、`PerKeyOrderedBuffer`、`SnapshotCopyUtil`、`MemoryKeyedStateBackend`、`CheckpointSerDe`、`LocalFileCheckpointStorage`、`JdbcCheckpointStorage`、`CheckpointCoordinator`、`CheckpointHistory`、`OpsJobManager`、`JobCoordinator`、`TaskManager`、`RemoteInputChannel`、`RemoteResultPartition`、`StreamOpsHttpServer`、`WebhookAlertChannel`、`SideOutputRegistry`、`TypeRegistry`、`StreamElementCodec`、`StreamMetricsRegistries`、`SharedStateRegistryImpl`、`TwoPhaseCommitSinkFunction`、`SourceReaderOperator`。

**已核实为有界/有声明、不作发现的项**（防复核误判，列要点）：`ResultPartition.queue`（LinkedBlockingQueue 有界，默认 1024，写阻塞/物化旁路语义见 C5-001 关联说明）；`BufferPool`（公平信号量，全局默认 65536 元素）；`RemoteInputChannel.queue`（有界 1024 + 10s 零进展 typed overflow，**不消费** per-job BufferPool——属文档声明的跨 JVM 边界由消息后端负责）；`RemoteResultPartition`（无本地缓冲，直发消息后端；DB 通道 opt-in backlog 门控）；`CheckpointHistory`（100 条 + retention，实现与声明一致）；`CheckpointCoordinator.pendingCheckpoints`（maxConcurrent 上限 + timeout abort 兜底）；`checkpointSegments`（随 retention GC 重建）；`OpsJobManager.jobs/terminalAt`（治理清扫按 retention 删除）；`JobCoordinator` 的 subtaskLiveness/deployIssuedAtMs/completedSubtaskKeys（liveness 迁移时 remove + stop 时 clear）；`TaskManager` commitExecutor（有界队列 + CallerRuns + 饱和计数指标，declared）与 completedTasks（MAX_COMPLETED_TASKS 上界）；`WebhookAlertChannel`（ArrayBlockingQueue 满丢弃 WARN，declared）；`TwoPhaseCommitSinkFunction.pendingCommits`（commit/abort 成功即 remove，restore 失败保留属「subsuming commit」有界重试语义）；`EquiJoinOperator/WindowOperator` 的 triggerAccumulators/paneTracking/activeWindows（purge/cleanup/merge 路径均有 remove，unbounded-growth 缺陷已由 `removeTriggerAccumulators` 前缀删除闭合）；`HeapInternalTimerService`（keyed 语义，TreeMap 按 (timestamp, key) 组织）；`MemoryKeyedStateBackend`（堆后端即声明包络；TTL 60s 周期清扫 plan 369）；`SharedStateRegistryImpl`（引用计数 register/unregister 成对，段锁内零计数即移除）；`MaxParallelismReshardMigration`/`StreamMaintenanceMain`（离线低频维护工具，readAllBytes 全量载入属工具语义）；`StreamElementCodec.DECODED_VALUE_TYPES`（见 C5-009，有界于 classpath 类数）；`TypeRegistry`（边数上界）。

## 无界风险清单（路径 × 增长源 × 声明上界 × 判定）

| # | 路径 | 增长源 | 声明上界 | 判定 |
|---|---|---|---|---|
| 1 | `InMemoryMaterializationPoint.store`（materialization 边每分区） | 每条数据记录 dual-write，只 add 无 remove/truncate | **无任何声明**（接口无清理方法，全仓无 seal/truncate 调用方） | **无界** → C5-001（P1，超出声明包络：线性于吞吐×时长） |
| 2 | `ResultPartition.pendingReplay`（ConcurrentLinkedQueue） | 恢复路径 attach 的回放集 | javadoc 明文「replay set can exceed the queue capacity」，上界 = 捕获集/物化 store 体积 | 声明性无界（继承 #1 上界；恢复期瞬时，随 #1 修复收敛）→ 并入 C5-001 |
| 3 | checkpoint 序列化链（`PerKeyOrderedBuffer.freeze` → `CheckpointSerDe` 单文档 JSON + canonical checksum 往返） | 每次 checkpoint 全状态深拷贝 ×2 轮 + 全文档 String/byte[]/树多份 | 包络 = keyed 状态体积（声明「几十 GB 级」），但峰值放大 4–8× | 有界但峰值倍数缺陷 → C5-002（P2）；工作视图驻堆致包络不可达 → C5-003（P2，超出声明包络） |
| 4 | `EquiJoinOperator.buffer` / `OverWindowOperator.buffer`（PerKeyOrderedBuffer 工作视图）/ `StreamReduceOperator.values` | 每键状态全量驻堆（RocksDB 后端不分流） | 声明包络几十 GB（RocksDB），工作视图实际包络 = 堆 | **超出声明包络** → C5-003（P2） |
| 5 | `StreamOpsHttpServer.handleSubmit` 请求体 `readAllBytes` | 单请求体大小（调用方可控） | 无上限（无 Content-Length 校验） | 无界输入 → C5-004（P2） |
| 6 | `TaskMailbox.controlQueue/normalQueue`（ConcurrentLinkedQueue） | 背压停顿期 ProcessingTimeServiceDriver 每 tick(100ms) 一封 timer mail | 无容量、无溢出策略（平台 QueueOverflowPolicy 未用） | 有界速率无总量上界 → C5-005（P2） |
| 7 | `CheckpointCoordinator.failedCommitParticipants` + `checkpointSuccessMap` | 参与者 finishCommit 持续失败时每 checkpoint 一个 epoch 条目 | 仅「重试成功或参与者注销」时清除；无条目数上限 | 条件性无界 → C5-006（P2） |
| 8 | `SideOutputRegistry.CONSUMERS`（static ConcurrentHashMap） | 每次作业构建注册，主代码无 remove 调用方 | 无（仅测试 clear()） | 多作业 JVM churn 下按 submission 累积 → C5-007（P2） |
| 9 | `StreamMetricsRegistries` 进程级组合注册表 × 实例身份标签（`operator@identityHashCode`，3 处） | 每次 task 构建/重启新 meter | micrometer 弱引用缓解；Prometheus 陈旧序列存续至进程重启 | P3 → C5-008 |
| 10 | `StreamElementCodec.DECODED_VALUE_TYPES`（static） | wire 上合法类名 → Class 永久驻留 | 有界于 classpath 类总数 | 有界，记 P3 → C5-009 |
| 11 | `LocalFileCheckpointStorage`/`JdbcCheckpointStorage` body（`readAllBytes`/`getBytes` 单体载入） | 单 checkpoint body = 全作业状态 JSON | 行数受 maxRetainedCheckpoints 声明约束；单体体积继承 #3 口径 | 格式固有，峰值缺陷归并 C5-002 |
| 12 | restore 路径（`CheckpointSerDe.deserializeCheckpoint`：byte[]→String→tree→canonical 再树→normalize 深拷贝） | 全状态解析峰值 | 同 #3 | 归并 C5-002 |

---

## 发现条目

### [nop-stream-core-runtime-C5-001] InMemoryMaterializationPoint 全量驻留堆内存：materialization 边将 100% 数据流记录永久保留，无任何截断/淘汰路径（F7）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/materialization/InMemoryMaterializationPoint.java:41,61-72,75-93`；关联 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:296,346-376`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java:392-402`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputChannel.java:224-240`
- **证据片段**:
  ```java
  // InMemoryMaterializationPoint.java:41,61-72
  private final List<MaterializedElement> store = new ArrayList<>();
  @Override
  public synchronized void write(StreamElement element, long epoch) throws InterruptedException {
      if (sealed) { throw new StreamException(ERR_STREAM_MATERIALIZE_POINT_SEALED)...; }
      // MaterializedElement constructor rejects null element.
      store.add(new MaterializedElement(element, epoch));   // 只 add，无任何 remove/trim
  ```
  ```java
  // ResultPartition.java:296,351-355 —— 每条记录无条件 dual-write；队列满时释放池许可继续生产
  dualWriteToMaterialization(point, element);      // point != null 时每条记录都进 store
  ...
  bufferPool.acquire();
  if (!queue.offer(element)) {
      bufferPool.release();                        // 池背压被旁路：许可放回，生产不减速
  }
  ```
- **严重程度**: P1
- **并发场景**: 任务生产者线程（`write()` 双写路径）与恢复线程（`SupervisionLoop.rebuildConsumerInputGateWithReplay` / `InputChannel.activateMaterializationReplay` 读路径）。增长本身与并发无关——单线程生产即可触发；`replayAll()`/`replay()` 在 `synchronized(this)` 内对全量 store 做整表拷贝，恢复线程持锁期间生产者阻塞在 monitor 上。
- **触发时序**: T0 作业以任一 `materializationEnabled` 边启动（`GraphExecutionPlan.buildPartitionMatrix` 对该边矩阵内每个分区 attach 一个 `InMemoryMaterializationPoint`）→ T1 起生产者每条数据记录经 `ResultPartition.write()` → `dualWriteToMaterialization()` → `store.add()`，控制事件除外；T2 消费者正常消费主队列，**store 永不收缩**（`IMaterializationPoint` SPI 无 truncate/remove 方法，全仓 grep 无 `.seal()` 调用方、无任何清理调用）→ Tn 长时运行后 store = 自作业启动以来 100% 数据记录，线性于 吞吐 × 时长，堆耗尽 OOM。叠加因子：region restart 恢复路径 `activateMaterializationReplay` 再把整个匹配段 `new ArrayList<>(materialized.size())` 物化一份并 attach 为 `pendingReplay`（又一个声明性无界容器），瞬时峰值再翻倍。
- **现状**: `InMemoryMaterializationPoint` javadoc 自述「in-memory list grows unboundedly」并声称「global memory pressure is expected to be controlled upstream by the IBufferPool on the main queue」——该声明与代码事实矛盾：pool+materialization 象限中队列满时 `offer` 失败即 `bufferPool.release()` 放回许可（`ResultPartition.enqueueWithBackpressure` 351-355 行），生产速率不再受池约束，全部记录继续进入无界 store；no-pool+materialization 象限同样 `offer` 丢弃式旁路。BufferPool 对 materialization 边**不构成**任何内存上界。设计文档自认 RocksDB/磁盘物化为 deferred 优化候选，当前唯一实现即无界内存版。
- **风险**: 启用 materialization 边（region 边界重启/failover 机制的基础，`RegionDecomposer` 只在 materialization 边切分区域）的任何长时运行作业，堆内存随吞吐线性增长直至 OOM：1K rec/s × 1KB 载荷 ≈ 86GB/天。OOM 同时击穿 checkpoint（全状态快照更慢）与恢复路径（replay 全量物化放大 2×）。「超出声明包络」标注：owner doc 与设计文档均未为 materialization store 声明任何规模包络；该增长无任何参数可配置上限，超出任何可辩护包络。附带跨维度风险（归 C3/C8 复核）：溢出旁路记录只存在于 store、主队列消费者在无 region restart 时永远看不到该记录——「no data is lost」的说法仅对重启重放语义成立。
- **建议**: (1) 短期：给 `IMaterializationPoint` 增加容量/字节上界 + 淘汰策略（复用平台 `HighWatermarkSemaphore` 或 `CacheConfig` 语义），达到上界时按声明语义 fail-fast（typed `ERR_STREAM_*`）而非静默增长；(2) 按 epoch/checkpoint 完成点截断：已随 durable checkpoint 固化的 segment（epoch < 最新完成 checkpoint id 且已无 region 依赖）可安全丢弃；(3) 中期按设计文档兑现 RocksDB/磁盘物化后端（`ISegmentStore`/`LocalFileSegmentStore` 已有落盘基建可复用）；(4) 修正 `InMemoryMaterializationPoint`/`ResultPartition` javadoc 中「由 IBufferPool 控制上游内存压力」的不实声明。
- **失效面枚举**（F3 同族清扫：随输入增长的数据面容器全集）: `InMemoryMaterializationPoint.store`（本发现，无界）；`ResultPartition.queue`（有界 1024，put 阻塞语义——无发现）；`ResultPartition.pendingReplay`（声明性无界，上界继承本发现，恢复期瞬时——并入本发现）；`RemoteInputChannel.queue`（有界 1024 + typed overflow——无发现）；`TaskMailbox` 双队列（无界，独立成 C5-005）；`PerKeyOrderedBuffer`（join/over-window 工作视图，keyed 包络错配——C5-003）；`StreamReduceOperator.values`（同 C5-003）；`WindowOperator.triggerAccumulators/paneTracking/activeWindows`（purge/cleanup 路径已有前缀删除/条目删除——无发现）；`HeapInternalTimerService` 双 TreeMap（keyed 语义——无发现）；`SharedStateRegistryImpl.registry`（引用计数配对——无发现）；`checkpointSegments`（retention GC——无发现）。
- **信心水平**: 确定
- **误报排除**: 不属于「有界声明存在的缓存/队列」误报——增长无界且无任何声明上界（javadoc 声明与代码矛盾恰恰是缺陷本体）；不是低频路径——materialization 边的 write 在数据面每记录热路径上；「理论上限 = 磁盘/堆」不构成声明边界。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C5-002] checkpoint 序列化中间体积放大链：全状态深拷贝 ×2 轮 + 单文档 JSON + canonical checksum 多次全量往返，峰值 ≈ 状态体积 4–8×（F7/F11）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java:86-88,96-127`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/util/SnapshotCopyUtil.java:20-27`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java:98-120,154-169,374-388,396-451`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/LocalFileCheckpointStorage.java:359-362`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:120-123`
- **证据片段**:
  ```java
  // PerKeyOrderedBuffer.java:86-88（每次 checkpoint 对整个工作视图深拷贝）
  public PerKeyOrderedBuffer<K, V> freeze() {
      return (PerKeyOrderedBuffer<K, V>) fromSerializableForm(toSerializableForm());
  }
  // toSerializableForm 内部每条目：SnapshotCopyUtil.defensiveCopy(...)
  // SnapshotCopyUtil.java:26 —— 每条目一次完整 JSON 序列化+解析往返
  return JsonTool.parse(JsonTool.serialize(value, false));
  // CheckpointSerDe.java:384-387 —— checksum 对全文档再 serialize→parse→serialize + 全树 normalize 深拷贝
  Map<String, Object> reparsed = JsonTool.parseMap(JsonTool.serialize(canonical, false));
  Map<String, Object> normalized = normalizeNumbersDeep(reparsed);
  String normalizedText = JsonTool.serialize(normalized, false);
  ```
- **严重程度**: P2
- **并发场景**: 任务线程（`snapshotState` 在 barrier 对齐点同步执行 freeze）与 checkpoint 持久化执行器线程（异步序列化 frozen form）；恢复时 coordinator 线程单线程解析。freeze 的全量拷贝发生在**任务线程**，直接拉长 barrier 对齐停顿。
- **触发时序**: 每 checkpoint（默认周期触发）：T1 任务线程 `EquiJoinOperator.snapshotState:198` / `OverWindowOperator.snapshotState:143` 调 `buffer.freeze()`——对 N 条缓冲逐条 `defensiveCopy`（每条一次 JSON serialize+parse，产出 JSON-native 树）+ 重建 PerKeyOrderedBuffer（第 1 份全量副本，O(N) 次小对象分配）；T2 持久化线程 `CheckpointSerDe.serializeOperatorStates:872-878` 对 frozen buffer **再调一次** `toSerializableForm()`（第 2 份全量表单树，条目再次 defensiveCopy）；T3 `serializeCheckpoint:119` 把整树 `JsonTool.serialize` 成单个 String → `getBytes`（第 3 份：全文档字符序列 + byte[]）；T4 `computeCanonicalChecksumHex:384-387` 再 serialize→parseMap→serialize + `normalizeNumbersDeep` 全树重建（第 4/5 份中间树）。峰值驻留 ≈ 活跃状态 N + 冻结副本 N + 表单树 ≈2N + JSON String ≈2N + byte[] ≈N，**4–8×状态体积**。restore 侧同构（`deserializeCheckpoint` checksum 先验路径再走一遍 serialize→parse→serialize + normalize）。
- **现状**: 序列化格式与防撕裂拷贝都是正确性设计（R6 ST-02 快照撕裂修复），但每轮 checkpoint 的中间体积放大从未被核算；三处拷贝（freeze、serializeOperatorStates 再转 form、checksum canonical 往返）存在功能重叠——frozen buffer 传入 `serializeOperatorStates` 后立即又被 `toSerializableForm()`，freeze 产出的 PerKeyOrderedBuffer 副本在此路径上是纯浪费。
- **风险**: 规模口径——声明包络「几十 GB 级状态」（state-management-design.md，RocksDB off-heap 语境）下，operator-state 工作视图路径峰值内存 = 状态体积 × 4–8，几十 GB 状态意味着数百 GB 堆峰值，**声明包络在该路径上不可达**；检查点周期性尖峰与 C5-001（若并存）叠加。task 线程 freeze O(N) JSON 往返还线性拉长 checkpoint 对齐停顿（背压放大）。
- **建议**: (1) 消除 freeze→serializeOperatorStates 的重复 form 转换：`snapshotState` 直接 put `toSerializableForm()` 的产物（已满足防撕裂——form 是深拷贝），跳过 fromSerializableForm 重建；(2) checksum 计算改为流式/单遍：对序列化文本直接做规范化哈希，避免 parse→serialize 全量往返与 `normalizeNumbersDeep` 全树复制（以「写入侧预规范化后落盘」替代读取侧重规范化）；(3) 长期按段（per-task）分片序列化，替代单文档 String/byte[]（`StateSegmentDescriptor`/`ISegmentStore` 段基建已在）。
- **失效面枚举**（F3 同族：所有 per-checkpoint 全量深拷贝点）: `EquiJoinOperator.snapshotState:198`（freeze——受影响）；`OverWindowOperator.snapshotState:143`（freeze——受影响）；`StreamReduceOperator.snapshotState:129,134`（逐条 `typedCopy` JSON 往返——受影响，同族）；`WindowOperator` 的 PaneTracking/ActiveWindow/TimerSnapshot 表单快照（条目级浅表单，量级小——无独立发现）；`TwoPhaseCommitSinkFunction.saveState:162-164`（synchronized 内 TreeMap 全量复制——量级小，无发现）；`CheckpointBarrierTracker`/`ChannelState` in-flight 捕获（上界 = 队列容量——无发现）。
- **信心水平**: 确定（拷贝链路逐点核实；峰值倍数为量级估算，标注为估算口径）
- **误报排除**: 不属于「热路径分配浪费不报」——checkpoint 是声明支持大数据量的周期性主路径且 owner doc 明确暴露 `checkpoint.size.bytes` 运维指标；不是「原语使用误报」——缺陷在三轮重叠拷贝的复合结构，非单个 API 用法。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C5-003] join/over-window/reduce 工作视图全量驻堆，RocksDB 声明包络（几十 GB 级状态）对这些算子不可达 —— 超出声明包络

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java:112-113,172`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/windowing/OverWindowOperator.java:77,118`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamReduceOperator.java:51,93,106-114`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java:41`
- **证据片段**:
  ```java
  // EquiJoinOperator.java:112-113 —— 工作视图与 keyed 后端并存，前者是纯堆对象
  /** working view: bufferKey → ordered entries (restored through the operator-state transport). */
  private transient PerKeyOrderedBuffer<String, JoinSideRecord<Object>> buffer;
  // StreamReduceOperator.java:51 —— reduce 状态完全不经过状态后端
  private transient Map<Object, T> values;
  ```
- **严重程度**: P2
- **并发场景**: 单任务线程读写；与 checkpoint 持久化线程的交互见 C5-002。非竞态发现，属规模包络错配。
- **触发时序**: 配置 RocksDB 状态后端 + 大基数 key 的 join/over-window/reduce 作业 → T1 每条记录 `buffer.add()` / `values.put()` 落**堆内**结构（EquiJoin/OverWindow 虽有 keyed MapState durable 副本，`PerKeyOrderedBuffer` 工作视图始终全量驻堆；StreamReduceOperator 连 durable 副本都走 operator-state，无 keyed 后端参与）→ T2 状态增长只受堆上限约束，RocksDB off-heap 包络完全不生效 → 达到堆上限即 OOM，而同一作业的纯 keyed 状态算子（window contents 等）本可在几十 GB 级继续工作。
- **现状**: 设计文档声明「JSON 反射序列化……适合几十 GB 级状态」是就 RocksDB 后端整体而言；join/over-window 的 durable 副本可进 RocksDB，但设计同时把 `PerKeyOrderedBuffer` 定为「in-memory working view」强制全量驻堆（restore 也以整块 operator-state 传输）；`StreamReduceOperator.values` 则完全绕过状态后端。owner doc 未对这些算子声明独立包络——按审计口径标注**超出声明包络**（声明包络存在但对这些路径不可达）。
- **风险**: 用户按声明包络配置 RocksDB + 大堆外状态预算后，join/reduce 算子仍在堆内 OOM；且 checkpoint 时叠加 C5-002 的 4–8× 峰值，实际可用状态规模进一步缩小。容量规划无法从任何文档推导出真实上界。
- **建议**: (1) 文档侧先行：在 owner doc 明确 join/over-window/reduce 的实际包络 = 堆（给出工作视图驻堆事实与 checkpoint 峰值倍数），这是最小修复；(2) 工程侧：StreamReduceOperator 的 per-key 状态迁移到 keyed 后端（`getValueState` 即可，恢复语义不变）；join/over-window 工作视图引入分段驻留（仅当前 watermark 前缘附近的段驻堆，其余走 keyed 后端读路径）。
- **失效面枚举**（F3 同族：声明走 RocksDB 但实际堆驻的状态容器）: `EquiJoinOperator.buffer`（驻堆——受影响）；`OverWindowOperator.buffer`（驻堆——受影响）；`StreamReduceOperator.values`（纯堆、绕过后端——受影响，最重）；`WindowOperator.windowContentsState/elementTimestampsState`（走 keyed 后端——RocksDB 下不驻堆，无发现；`elementTimestampsState` 每元素一个 boxed Long ≈3× 放大属 keyed 包络内浪费，P3 不立项）；`KeyedStreamImpl.FieldAggregationReducer`（经 StreamReduceOperator 承载——同上）；`triggerAccumulators`（per-key+window 条目，有清理——无发现）。
- **信心水平**: 确定（驻堆事实逐文件核实；「包络不可达」为对照设计文档声明的推论）
- **误报排除**: 不是「keyed 状态天然随 key 基数增长」的语义性无界——发现点是**实现（堆驻留）与声明包络（off-heap 几十 GB）的矛盾**，属规模口径错配而非流语义；WindowOperator 主状态走后端证明平台有能力分流，这三个算子是未分流点。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C5-004] StreamOpsHttpServer 提交端点无大小上限 readAllBytes：单个请求即可全量物化任意大 body 致 coordinator OOM

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamOpsHttpServer.java:389-418`（关键行 395）
- **证据片段**:
  ```java
  // StreamOpsHttpServer.java:395
  String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
  JobSubmissionSpec spec;
  try {
      spec = io.nop.core.lang.json.JsonTool.parseBeanFromText(requestBody, JobSubmissionSpec.class);
  ```
- **严重程度**: P2
- **并发场景**: ops HTTP 线程（JDK `com.sun.net.httpserver` 每连接一线程）vs coordinator 进程内其他组件共享堆；无并发竞态，属无界输入处理。
- **触发时序**: T1 调用方（loopback 零认证默认形态下的本机进程；或非 loopback + token 形态下的 token 持有者/被攻陷凭据）向 `POST /jobs` 发送任意大 body（如分块传输数十 GB）；T2 `readAllBytes` 将全部字节读入堆并复制为 String（≈2×body 字节数）→ T3 `JsonTool.parseBeanFromText` 再构建解析树（第 3 份）→ 峰值 ≈3×body 大小，单请求即 OOM coordinator 进程（连带其承载的全部作业协调面）。
- **现状**: 请求体无 `Content-Length` 校验、无流式/限量读取；同文件其余端点（stop/list/detail）不读 body 无此问题。认证边界：默认 loopback 零认证（back-compat 声明），非 loopback 强制 token（F-09b fail-fast）——攻击面已收敛但未闭合「合法调用方单请求打爆堆」。
- **风险**: 运维面单点：误用脚本/被攻陷凭据/失控重试循环一次即可 OOM 整个 coordinator；对多作业模式（OpsJobManager 宿主多协调器）爆炸半径最大。P2 定级依据：管理面（非数据面）、有认证/loopback 前置条件、单请求击穿而非渐进增长。
- **建议**: 读取前校验 `Content-Length` 上限（`JobSubmissionSpec` 实际只有 jobId/pipelineFactoryClass/params 三字段，1MB 量级足够）并在超限时 413；或按限额流式读取（复用平台 IO 工具的 bounded copy）；超限 typed 400/413 而非 OOM。
- **失效面枚举**（F3 同族：全模块 readAllBytes/全量读入点）: `StreamOpsHttpServer.handleSubmit:395`（外部输入无上限——本发现）；`LocalFileCheckpointStorage.deserializeCheckpoint:360 / deserializeEpochManifest:652 / loadSavepointMetadata:472`（读自家存储的受 retention 约束文件，语义固有——无发现，峰值问题归 C5-002）；`JdbcCheckpointStorage` `row.getBytes`（同前）；`MaxParallelismReshardMigration:556-558`（离线维护工具读 checkpoint 文件，低频声明语义——无发现）；`StreamMaintenanceMain` 族（CLI 工具同前）。
- **信心水平**: 确定
- **误报排除**: 不是「低频管理操作粗粒度实现」误报——缺陷是无输入上界而非实现风格；同端点已有结构化 400/409 错误面，超限拒绝与既有契约一致、不改变合法路径行为。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C5-005] TaskMailbox 无界且背压停顿期间 ProcessingTimeServiceDriver 每 tick(100ms) 投递一封 timer mail，慢速无上界累积

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/TaskMailbox.java:40-41,54-72`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ProcessingTimeServiceDriver.java:44,115-132`
- **证据片段**:
  ```java
  // ProcessingTimeServiceDriver.java:117-124（while 循环无邮箱深度感知/退避）
  while (!stopped) {
      long now = CoreMetrics.currentTimeMillis();
      boolean due = processingTimeService.isTimerDue(now)
              || (timeServiceManager != null && timeServiceManager.hasProcessingTimeTimersDue(now));
      if (due) {
          mailbox.put(Mail.control(this::fireDueTimersOnTaskThread, "processing-time-timer-fire"));
      }
  // TaskMailbox.java:40-41 —— 双无界 ConcurrentLinkedQueue，put 无任何容量/溢出策略
  private final ConcurrentLinkedQueue<Mail> controlQueue = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<Mail> normalQueue = new ConcurrentLinkedQueue<>();
  ```
- **严重程度**: P2
- **并发场景**: scheduler daemon 线程（driver）生产 / 任务线程消费。任务线程在数据面背压点（本地 `ResultPartition.put` 阻塞或远端 send 阻塞）长时间无法到达 mailbox 安全点时，driver 持续投递。
- **触发时序**: T1 任务持有 processing-time 触发器（如 `ContinuousProcessingTimeTrigger`），定时器持续到期；T2 下游持续背压（慢 sink 是流的**正常**运行态，非故障），任务线程阻塞在 `queue.put`/send 上数小时；T3 driver 每 100ms tick 检查 `hasProcessingTimeTimersDue`——timer 因任务线程不消费而持续 due → 每 tick `mailbox.put` 一封新 Mail（同一 runnable，但 Mail 对象 + CLQ 节点每次新建）→ 864,000 封/天 × ≈100B ≈ 80–100MB/天/任务，多任务 TM 进程线性放大，数日级持续背压下可观测堆增长，无任何回收路径。
- **现状**: 邮箱无容量、无平台 `QueueOverflowPolicy` 接入；driver 无「邮箱已积压则不再投递」的去抖（同 tick 已有待执行 fire mail 时重复投递在语义上也是冗余的——fire 是幂等读 TreeMap）。分布式模式下持续 stall 会触发 liveness/lease 失败转移，但**本地/嵌入式模式慢 sink 背压可无限期持续**，恰是该场景无兜底。
- **风险**: 长时运行慢消费作业的慢性堆增长；同时巨量重复 fire mail 排队本身无意义（每封执行时 TreeMap 多为空）。量级低于 C5-001 故定 P2（有界速率、无总量上界、正常负载可触发）。
- **建议**: driver 投递前检查「已有一封 pending 的 timer-fire mail」即跳过（driver 持有 mailbox 引用，可查 `size()` 或自持 AtomicBoolean 在任务线程执行完毕时清除）；或给 TaskMailbox 接入平台 `QueueOverflowPolicy`（CONTROL 类满时丢重复 mail + 计数指标）。
- **失效面枚举**（F3 同族：所有向该邮箱投递的线程）: `ProcessingTimeServiceDriver.run:123`（每 tick 一封——本发现）；checkpoint barrier 注入/触发路径（每 checkpoint 周期一封，30s 量级——速率可忽略）；abort/cancel handler（单次——无发现）；`TaskMailbox.put` 关闭后静默丢弃（close 后任务已终止，无累积——无发现）。
- **信心水平**: 很可能（投递循环与无界容器逐行核实；「任务线程长时间不到达安全点」在本地背压路径成立——`ResultPartition.put` 为无限期阻塞；具体 OOM 时长依赖载荷与 tick 常量，故不标确定）
- **误报排除**: 不是「控制面低频消息无界队列属合理」的误报校准项——driver 的投递速率是固定 10Hz 且与邮箱深度无关，正常负载（持续背压）即可触发；不是 C7 域——本发现只算内存账，挂死可检测性归 C7 复核。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C5-006] CheckpointCoordinator failedCommitParticipants/checkpointSuccessMap 在参与者 finishCommit 持续失败时按 checkpoint 频率累积，无条目上界

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:278-281,1518-1530,1532-1565`
- **证据片段**:
  ```java
  // CheckpointCoordinator.java:1527 —— 失败即入账，无条目上限
  failedCommitParticipants.computeIfAbsent(checkpointId, k -> ConcurrentHashMap.newKeySet()).add(participant);
  // 1555-1562 —— 仅当重试全成功（或参与者已注销）才整条移除
  if (stillFailing.isEmpty()) {
      it.remove();
      checkpointSuccessMap.remove(failedEpoch);
  } else {
      failedCommitParticipants.put(failedEpoch, stillFailing);
  }
  ```
- **严重程度**: P2
- **并发场景**: checkpoint 完成回调线程（`notifyParticipantsFinishCommit`）与 `retryFailedCommits`（完成路径 line 1004 调用）在同/不同线程交错；`ConcurrentSkipListMap` 保证结构一致，非竞态发现。
- **触发时序**: T1 某 `CheckpointParticipant`（2PC sink 任务）进入「finishCommit 通知持续抛异常但任务本体未失败」状态 → T2 每次新 checkpoint 完成：`checkpointSuccessMap.put(ckptId)` + `failedCommitParticipants[ckptId] = {participant}`；T3 下次完成时 `retryFailedCommits` 重试旧 epoch 仍失败 → 条目按 epoch 保留，新 epoch 再入账 → 条目数随 checkpoint 次数单调增长（30s 周期 ≈ 2880 条/天），且对应 `checkpointSuccessMap` 条目被 abort 门控（1066/1147 行 `containsKey` 判断）同步钉住。移除条件仅两个：重试成功，或参与者已从 `participants` 注销（1544 行 contains 检查失败→ stillFailing 空→移除）。
- **现状**: 重试机制本身是有意设计（deferred retry），缺的是**上限与升级语义**：持续失败的参与者没有「重试 N 轮后 failJob/驱逐」的出口，账本随时间线性增长且每条目在每次 `retryFailedCommits` 全表扫描中被反复重试（本身还是重试放大）。
- **风险**: 长时运行 + 一个半失效参与者的组合下内存慢涨（P2 量级：条目小）；更重的是 `retryFailedCommits` 每次完成都全量重试所有历史失败 epoch——失败参与者的重试负载随时间线性放大。触发链依赖「通知持续失败但任务不失败」这一窄条件，写不出必然时序，按纪律不标 P1。
- **建议**: 给 `failedCommitParticipants` 加 epoch 数上限 + 重试轮次计数：同一参与者连续 N 轮失败后从 `participants` 注销并 failJob（typed 错误），使 1544 行注销路径成为必然出口；或按「仅重试最近 K 个 epoch」截断扫描面。
- **失效面枚举**（F3 同族：coordinator 内按 checkpointId 键控的账本 Map）: `failedCommitParticipants`（条件性无界——本发现）；`checkpointSuccessMap`（正常路径完成/abort 即 remove——仅在 failedCommitParticipants 钉住时滞留，随本发现收敛）；`pendingCheckpoints`（maxConcurrent 上限 + timeout abort——无发现）；`checkpointSegments`（retention GC 重建——无发现）；`registeredSourceVertexIds`（顶点数上界——无发现）。
- **信心水平**: 很可能（代码路径确定；「任务不失败而通知持续失败」的实际可达性取决于参与者实现，未构造完整端到端复现）
- **误报排除**: 不是「有界声明存在的缓存」——无任何上限声明；不是纯理论竞态——存在明确的逐 checkpoint 入账单调路径，只是触发前提较窄（故 P2 而非 P1）。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C5-007] SideOutputRegistry 静态注册表主代码无 remove 调用方：多作业 JVM 长期 churn 按作业提交次数累积并钉住已停作业的计划图

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SideOutputRegistry.java:32-48`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SideOutputDataStream.java:87-107`
- **证据片段**:
  ```java
  // SideOutputRegistry.java:32 —— 进程级 static，注册即驻留
  private static final Map<String, Consumer<StreamRecord<?>>> CONSUMERS = new ConcurrentHashMap<>();
  // SideOutputDataStream.java:90 —— 注册闭包捕获 outputTag 与 sinkFunction（含其捕获的计划对象）
  SideOutputRegistry.register(outputTag.getId(), record -> {
      ...
      sinkFunction.consume(typed.getValue());
  ```
- **严重程度**: P2
- **并发场景**: 作业构建线程（plan build 时注册）与运行中生产任务线程（读取消费方）。写入只在构建期，非热路径竞态；问题在生命周期不对称。
- **触发时序**: T1 多作业模式（`OpsJobManager` + REST submit）进程内首次提交含 side output 的作业 A → `register("tagA", consumer)` 入表；T2 作业 A 停止/FINISHED——全仓主代码 grep 无任何 `SideOutputRegistry.remove(...)` 调用方（仅测试调 `clear()`），条目与闭包捕获的 sinkFunction（进而其捕获的 SinkFunction/配置/上游计划引用）永久驻留；T3 反复提交/停止不同 tag 的作业 → 表条目数与被钉住对象随提交次数单调增长，治理清扫只清 `OpsJobManager.jobs`，触及不到本表。
- **现状**: javadoc 声明「Entries are plain function references and small; tests can clear the registry between jobs via clear()」——「small」与「测试可清」仅对单作业嵌入式形态成立；同 id 重复注册会替换（replace 语义使同 tag 重提交不泄漏），泄漏条件是 tag id 随作业不同（用户自命名的 tag 或 `<operatorUid>:window-events` 在不同 uid 下）。附带跨维度注记（归 C1 复核）：同 JVM 两个作业使用相同 tag id 时后注册者静默顶替前者，属跨作业错路由语义。
- **风险**: 长期运行的 REST 多作业宿主进程内存慢涨；每个泄漏条目钉住的不只是 Consumer，还有整条 side-output sink 链的对象图。单作业嵌入式部署不受影响（进程退出即回收），故 P2。
- **建议**: (1) `OpsJobManager.stop`/coordinator 终态路径按该作业构建期登记的 tag id 集合调 `SideOutputRegistry.remove`（构建期收集 tag id 到 job 元数据即可，改动小）；(2) 或把注册表键升级为 `(jobId, tagId)` 复合键 + 作业终态批量清除（同时消除同 tag 跨作业顶替歧义）。
- **失效面枚举**（F3 同族：core/runtime 内 static/进程级注册容器）: `SideOutputRegistry.CONSUMERS`（无 remove 调用方——本发现）；`StreamMetricsRegistries.COMPOSITE`（进程级但 meter 弱引用 + ops server 关闭 removeMember——P3 归 C5-008）；`StreamElementCodec.DECODED_VALUE_TYPES`（类名缓存——P3 归 C5-009）；`TypeRegistry.edgeToOutputType`（实例级、边数上界——无发现）；`WindowedStreamImpl.defaultFactory`（单槽 volatile——无发现）。
- **信心水平**: 确定（「主代码无 remove 调用方」为全量 grep 事实；泄漏速率依赖多作业 churn 模式）
- **误报排除**: 不按「低频路径粗粒度」豁免——多作业宿主是 owner doc 声明的运维形态（REST 生命周期 + OpsJobManager），churn 是其正常用法；「entries are small」的自述不覆盖闭包钉住的对象图。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C5-008] 算子实例身份标签指标（operator@identityHashCode）进程级注册表基数 churn

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java:183-185`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/windowing/OverWindowOperator.java:128-131`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/windowing/WindowOperator.java:585-588`
- **证据片段**:
  ```java
  // EquiJoinOperator.java:183-185
  joinOutputsCounter = StreamMetricsRegistries.registry()
          .counter(JOIN_OUTPUTS_METRIC_NAME,
                  "operator", getClass().getSimpleName() + "@" + System.identityHashCode(this));
  ```
- **严重程度**: P3
- **并发场景**: 任务构建/重启路径注册；无竞态。
- **触发时序**: 每 subtask 每次 open 注册一个新 tag 组合的 meter；任务重启/恢复重建即产生新 identityHashCode 标签，旧 series 在 Prometheus 成员输出中存续至 meter 被回收/进程重启。
- **现状**: micrometer 弱引用 meterMap 使未被引用的 meter 可被 GC，堆泄漏有限；残余影响是 scrape 输出中的高基数 churn 与重启风暴下的标签噪声。
- **风险**: 重启循环下指标面基数膨胀（运维误读），非内存风险主体。
- **建议**: 标签改用稳定维度（jobId/vertexId/subtaskIndex——owner doc operator 层标准标签集已定义），仅在无部署位置的回退分支保留实例标签。
- **失效面枚举**: `EquiJoinOperator:185`、`OverWindowOperator:130`、`WindowOperator:587`——3 处同族，全部同一模式；其余指标注册点（StreamTaskInvokable 数据面、io 层）使用 owner doc 标准标签集，无发现。
- **信心水平**: 确定
- **误报排除**: 不按 P1/P2 报——micrometer 弱引用已缓解堆风险，剩运维噪声；无量化 OOM 链。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C5-009] StreamElementCodec 静态类名缓存按 wire 合法类名累积（有界于 classpath，含类初始化副作用）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/transport/StreamElementCodec.java:44-63`
- **证据片段**:
  ```java
  // StreamElementCodec.java:44-46 —— 键为 wire 上声明的、能通过校验并成功加载的任意类名
  private static final java.util.concurrent.ConcurrentHashMap<String, Class<?>> DECODED_VALUE_TYPES =
          new java.util.concurrent.ConcurrentHashMap<>();
  ...
  ClassNameValidator.validateClassName(valueType);   // 仅拒绝非法形式，不设白名单上限
  clazz = DECODED_VALUE_TYPES.computeIfAbsent(valueType, name -> { ... Class.forName(name) ... });
  ```
- **严重程度**: P3
- **并发场景**: 消息分发线程（decode）并发写缓存；`computeIfAbsent` 原子，无竞态。
- **触发时序**: 上游（跨 JVM，envelope.valueType 字段为其可控输入）发送大量**互不相同但合法**的类名 → 每个通过校验且可加载的名字永久入缓存并 `Class.forName` 初始化该类。上界 = classpath 可加载类总数（数千至数万），非无界；类经初始化后终生驻留（无卸载）。
- **现状**: 缓存动机正当（消除每记录校验+forName），拒绝名不缓存的设计正确；残余面是「合法即可钉住」——与 F-10a 反序列化白名单的收紧方向相反（白名单管反序列化，本缓存管加载）。
- **风险**: 被攻陷/失控上游可钉住 classpath 内大量类（内存与元空间压力有限但可测）；属防御纵深缺口而非可达 OOM。
- **建议**: 复用 F-10a 的 `nop.stream.state.deserialize.allowed-prefixes` 前缀声明机制约束可缓存类名；或给缓存加容量上限（有界 ConcurrentHashMap + 淘汰）。
- **失效面枚举**: `StreamElementCodec.DECODED_VALUE_TYPES` 一处；`TypeRegistry`（计划侧注册，非外部输入键控）、accumulator 类名重建（`CheckpointSerDe.accumulatorFromForm:978-994`，经 `ClassNameValidator.validateAccumulatorClass` 且 checkpoint 载荷受 checksum 保护）——同族但防护已就位，无发现。
- **信心水平**: 确定（行为事实）；风险评级依赖部署信任模型（标注为有趣边界）
- **误报排除**: 未按「缓存有界即豁免」直接跳过——因其键空间来自外部输入；但上界确凿（classpath 类数）故仅 P3 登记。
- **复核状态**: 已降级（P3→备案注记）

---

## 汇总

| 严重程度 | 数量 | 发现 |
|---|---|---|
| P0 | 0 | — |
| P1 | 1 | C5-001 |
| P2 | 6 | C5-002 / C5-003（超出声明包络）/ C5-004 / C5-005 / C5-006 / C5-007 |
| P3 | 2 | C5-008 / C5-009 |

**超出声明包络专项标注**: C5-001（materialization store 无声明包络、线性无界）、C5-003（声明「几十 GB 级状态」对 join/over-window/reduce 工作视图不可达，实际包络 = 堆）、C5-002（checkpoint 峰值 4–8× 使声明包络在该路径不可达）。owner doc `nop-stream.md` 本身无数值化规模包络——建议作为文档缺陷单独立项补声明。

# C5 内存有界性与大数据量 — nop-stream-core-runtime（深挖第 2 轮追加）

- **审计维度**: C5（内存有界性与大数据量），第 1 轮（`C5-memory-bounds.md`，C5-001…C5-009）之后的定向深挖追加轮
- **盲审边界（第 2 轮）**: 按追加轮纪律，仅允许读取本维度第 1 轮文件（`C5-memory-bounds.md`）与本轮新读的 live code；未读取 `ai-dev/audits/` 下其他审计目录、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`。现状标注留待复核阶段。
- **本轮深挖方向**（第 1 轮盲区）: (1) 随输入增长的容器漏盘点——执行/任务/协调/集群/source-coordinator 各层的 Session 级 Map、去重集合、批缓冲；(2) 无 LIMIT 查询/全量读入（runtime 内全部 `executeQuery` 点逐一核对）；(3) 缓存键空间用户可控性（lookup 缓存、类名缓存之外的第 1 轮未盘点缓存）；(4) 峰值叠加验算（ops server 执行器并发上界、多作业宿主叠加）；(5) off-heap/直接内存（`allocateDirect`/`MemorySegment`/`Unsafe`/RocksDB 实现类）。

## 本轮已核实为有界/无发现的排查面（防复核误判）

| 排查面 | 结论 | 依据 |
|---|---|---|
| `TaskExecutor.submittedTasks/submittedSubtaskTasks/taskFutures`（三 Map 只 put 无 remove） | **有界** | 类内全量 grep 无 remove，但实例仅在 `StreamExecutionEnvironment.runLocal:543` 每次执行新建（局部变量）+ `finally shutdownLocalExecution`，随执行对象图整体消亡；分布式路径 TaskManager 用自己的 `taskExecutor` 字段，不经此类 |
| `CheckpointBarrierTracker.inFlight`（LinkedHashMap，trigger 即 put） | **有界（声明成立）** | javadoc:104-106 明文声明上界 = coordinator 端 `maxConcurrentCheckpoints` 门控 + timeout abort 兜底；remove 点覆盖 ack 完成（286）、snapshot error（196）、显式 abort（383）、source 拒绝（133/143） |
| `InputGate.abortedBarriers` / `inFlightAlignments` | **有界** | `abortedBarriers` 按 `completedId` prune（1176-1182 `removeIf(id <= completedId)`）；`inFlightAlignments` 在 emission/abort 全路径 remove |
| `TaskProcessingTimeService.timers`（TreeMap） | **有界** | fire 前移除（120）、cancel 删除（85-95）、空桶 remove（91）；寄存者即触发器重臂路径 |
| `CachingTableLookup.cache`（lookup join 本地物化，键 = 流数据 join key，用户可控键空间） | **有界（声明成立）+ 不立项注记** | `maxEntries` + 最旧 loadNanos 淘汰（156-172），构造器 fail-fast 校验上限 ≥1——有界声明完整。注记：主代码 grep 无任何 `new CachingTableLookup` 构造点（WI11 储备能力，仅测试接线）——不满足"live code 可达"纪律故不立项；但接线时须注意其 `refreshPool`（`newFixedThreadPool`，无界 LinkedBlockingQueue）+ `scanAndDispatch` 每轮对全部 aged key 重复派发且无 pending 去重（218-226），delegate 挂起/慢查询时任务队列随轮次线性累积（C5-005 同族形态），接线评审应一并处理 |
| `InMemoryClusterRegistry.taskAssignmentHistory`（append-only）+ `coordinators`（无删除）+ `evictExpiredNodes`（主代码无调用方） | **C5 有界；持久化孪生归 C7** | 实例仅 `EmbeddedDistributedExecutor:139` / `RpcDistributedExecutor:192` 每作业执行新建，作业终态随 handle 消亡；单作业内 attempt 追加受 restart cap（RECOVERING→DEGRADED）约束。JDBC 实现（`JobCoordinatorMain/TaskManagerMain` 生产接线）的 task_assignment 历史表 append-only 且 `removeTaskAssignment` 主代码零调用方——属持久化无界账本（F4），内存审计不覆盖 DB 增长，**移交 C7 维度复核** |
| `JobCoordinator.barrierFanoutExecutors` / `attemptCounters`（第 1 轮未盘点的两容器） | **有界** | 前者按 nodeId 键控 + stop 时全部 shutdown（723-726）；后者按 (vertex,subtask) 键控 + 从 attempt history 播种（2295-2297），上界 = 任务数 |
| `StreamJobEventBus` / `JobHealthStateMachine` / `AlertService` | **无发现** | 均为同步分发，无队列/历史缓冲容器 |
| `CheckpointCoordinator.completedTasks`（第 1 轮未盘点） | **有界** | 1372 行 clear，条目 = 任务位置数 |
| `SourceCoordinatorRegistry`（static COORDINATORS / PENDING_ENUMERATOR_RESTORES，C5-007 同族候选） | **有界（声明成立）** | 清理责任在 javadoc 明文声明且有双调用点：LOCAL `StreamExecutionEnvironment:597-605`（finally 内）+ 分布式 `RunningTask:369-385`（R6 CC-04 终态清扫）；stash 条目随 unregister 一并移除。同 JVM 并发同 vertexId 为已声明 v1 limitation（javadoc:27-30 + RunningTask javadoc）——非缺陷 |
| `StreamOpsHttpServer` 并发叠加（第 1 轮 C5-004 只算了单体） | **并发有界** | `setExecutor(Executors.newFixedThreadPool(4))`（127 行）——同时处理请求 ≤4，无叠加放大 |
| off-heap / 直接内存 | **无发现** | 两模块主代码 grep 无 `allocateDirect`/`MemorySegment`/`Unsafe`/`DirectBuffer`；"RocksDB" 仅出现在配置项字符串/测试，无实现类（设计文档的 off-heap 后端为 deferred，与第 1 轮 C5-003 口径一致） |
| 累加器容器（`ListAccumulator`/`Histogram` 等 14 个类） | **无发现** | 主代码数据面零使用（仅 accumulators 包内定义 + 测试） |
| `common/eventtime`（WatermarkOutputMultiplexer/CombinedWatermarkStatus）、`windowing/evictors`/`delta`、`TypeRegistry`、`credentials`、`connector/registry` | **无发现** | channel/顶点索引有界，或 keyed/实例级包络内 |
| runtime 其余 `executeQuery` 点（`JdbcCheckpointStorage`×7、`JdbcClusterRegistry`×3、`JdbcLeaderElector`×2） | **无新发现** | checkpoint 行数受 maxRetainedCheckpoints/retention 声明约束（第 1 轮已核）；registry/elector 行数受节点数/任务数/单值约束。同族唯一无界点即 C5-010 |

---

## 发现条目

### [nop-stream-core-runtime-C5-010] PollingJdbcMessageService 轮询无 LIMIT 全量物化 topic backlog：单次 poll 峰值内存线性于 DB 积压，且该后端无 ChannelBacklogProbe 能力、WI6 门控对其不可达（F7）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java:305-338`（关键 311-314、383-390）、关联 `:66-75`（Limitations 未声明此项）、`:283-291`（数据面 topic cursor=0 引导）、`:151-156`（清理仅测试助手）；接线点 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/TaskManagerMain.java:104`、`JobCoordinatorMain.java:134`；门控缺失佐证 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteResultPartition.java:122-127`、owner doc `docs-for-ai/03-modules/nop-stream.md:298`
- **证据片段**:
  ```java
  // PollingJdbcMessageService.java:311-314 —— 无 LIMIT/FETCH，subject 积压全量进结果集
  List<Row> rows = jdbcTemplate.executeQuery(SQL.begin()
          .sql("SELECT id, payload FROM " + queueTable
                  + " WHERE topic = ? AND id > ? ORDER BY id ASC", topic, cursor)
          .end(), PollingJdbcMessageService::readRows);
  // PollingJdbcMessageService.java:383-390 —— 全部行（含完整 byte[] payload）先物化进 ArrayList，再逐条反序列化分发
  private static List<Row> readRows(IDataSet dataSet) {
      List<Row> rows = new ArrayList<>();
      while (dataSet.hasNext()) { ... rows.add(new Row(r.getLong(0), r.getBytes(1))); }
  ```
- **严重程度**: P1
- **并发场景**: `poller` 单线程调度器的 poll 线程（每订阅一个固定延迟任务）承载 SELECT + 全量物化 + 逐条同步分发；与生产端 INSERT（任务数据面发送线程）、共享 DB 无锁竞争——非竞态发现，属无界查询输入处理。分发目标 `RemoteInputChannel.queue`（有界 1024）的入队等待会反压到 poll 线程，使 rows 列表驻留时长被进一步拉长。
- **触发时序**: T1 下游子任务停滞/慢消费（流的正常运行态）→ 数据面记录持续经 `RemoteResultPartition.send` → `messageService.send` INSERT 进 `nop_stream_msg_queue`；生产端**无任何门控**——WI6 backlog limit（`nop.stream.data.db.backlog.limit`）为 opt-in 且要求裸后端实现 `ChannelBacklogProbe`（owner doc :298），`PollingJdbcMessageService` 未实现该接口（grep 零命中）→ 配了 limit 也按"无能力诚实禁用"，DB 积压无界增长；T2 消费恢复（或 Item 14 声明的 cursor=0 引导：数据面 topic 订阅从头重放，替换 TaskManager 首次订阅即命中）→ T3 单次 `poll()` 将 `id > cursor` 的**全部**积压行（每行完整 payload byte[]）物化进一个 ArrayList（JDBC 驱动结果集另有整份缓冲）→ 峰值内存 ≈ Σpayload ≈ 积压字节数 × ~2，积压达堆量级即单次 poll OOM 协调器/任务节点进程。稳态慢消费（速率 r < p）下每轮 poll 批量 ≈ p × pollIntervalMs，stall 时长 Δt 内累积 p × Δt，无任何上限。
- **现状**: 类 javadoc 自称 "test-infrastructure alternative; production deployments use SysDaoMessageService / Pulsar"，但其 Limitations 清单（:66-75）只声明 at-least-once、无通配订阅、fan-out 三项，**未声明无界 poll 批量**；且 owner doc :270 明文确认 `JobCoordinatorMain`/`TaskManagerMain` 及 `PollingJdbcMessageService` 自 2026-10 起在 main sources、随 runtime jar 发布、启动脚本 `nop-stream-cluster.sh` 直接可用（"生产数据库驱动须自带"暗示真实 DB 部署形态）——即该服务是本仓库发布的分布式集群模式的**唯一内置消息后端**，不是测试专用代码路径。表行只增不删（poll 无 ack 删除，`clearTopics` 为测试助手），积压只单向增长。
- **风险**: JDBC 集群模式（发布形态）下，一次足够长的下游 stall + 恢复，或一次替换节点重放，即可以单个查询击穿进程堆，爆炸半径 = 该 JVM 上全部任务/协调面。「超出声明包络」标注：owner doc 无数值化规模包络；唯一相关的声明上限（WI6 backlog limit）对该后端结构性不可达（缺能力接口），故本路径的积压无任何可配置或声明上界。附带跨维度注记（归 C3/C8 复核，不重复计数）：分发时 `consumer.onMessage` 抛异常（如 channel overflow typed 错误）被 330-332 行 catch 仅记日志、**cursor 仍然前移**（333 行）——DB-poll 通道存在"半个成功"丢消息语义。
- **建议**: (1) 最小修复：给 poll 查询加批量上限（`AND id <= ?`（cursor + batch）或 `LIMIT <batch>`，batch 大小可配，如 256），逐批推进 cursor——语义不变（cursor 本就逐条推进）；(2) 为 `PollingJdbcMessageService` 实现 `ChannelBacklogProbe`（对 `nop_stream_msg_queue` 按 topic COUNT WAITING 等价物 = `id > cursor` 行数），使 WI6 门控在该后端可达；(3) 增加已分发行的清理路径（`DELETE ... WHERE id <= minCursor`，JDBC 后端无 Kafka 的 retention 可依赖，现为永久累积）；(4) 若维持 test-infra 定位，则按 owner doc 口径在 `nop-stream-cluster.sh`/文档明示"仅限验证环境，数据面吞吐上限 = 单批 poll 内存预算"，并同步修正类 Limitations 清单。
- **失效面枚举**（F3 同族：runtime 内全部"无 LIMIT 查询/全量载入"点复核状态）: `PollingJdbcMessageService.poll:311`（外部数据面积压，无上界——本发现）；`StreamOpsHttpServer.handleSubmit:395` readAllBytes（第 1 轮 C5-004 已立项）；`LocalFileCheckpointStorage` deserialize/manifest/loadSavepoint（读自家存储、受 retention 约束——第 1 轮已裁定无发现）；`JdbcCheckpointStorage`×7 executeQuery（行数受 maxRetainedCheckpoints/retention 声明约束——无发现）；`JdbcClusterRegistry:193/265`（节点/任务数上界——无发现）；`JdbcLeaderElector:382/507`（单值——无发现）；`MaxParallelismReshardMigration`/`StreamMaintenanceMain`（离线工具声明语义——第 1 轮已裁定）。同族内新发现仅本处。
- **信心水平**: 确定（代码路径与接线点逐行核实；峰值倍数为量级估算口径）
- **误报排除**: 不属于"为压测/测试写的简化并发模型不按生产标准报"的误报校准——该类在 main sources、被两个发布入口 main 硬接线、owner doc 将其列为 runtime jar 的集群启动组件；"低频路径"豁免也不适用——poll 在数据面每条记录的传输路径上。无界批量亦不在其自述 Limitations 之内，非已声明边界。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C5-011] LocalSourceCoordinator.finishedSplits 为 write-only 无界去重集合：只 addAll 永不移除、主代码零消费者，随 FLIP-27 source 的 split 完成数单调增长

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java:69,280-285,329-331`
- **证据片段**:
  ```java
  // LocalSourceCoordinator.java:69 —— 每个完成的 split 一个 String，无删除路径
  private final Set<String> finishedSplits = ConcurrentHashMap.newKeySet();
  // :284 —— reader 每报告一批完成 split 即并入
  owner.finishedSplits.addAll(finishedSplitIds);
  // :329-331 —— 唯一读取方，全仓主代码 grep 无任何调用方
  public Set<String> getFinishedSplits() {
      return Collections.unmodifiableSet(new LinkedHashSet<>(finishedSplits));
  }
  ```
- **严重程度**: P3
- **并发场景**: reader 任务线程经 `ReaderChannel.reportFinishedSplits` 写入（`ConcurrentHashMap.newKeySet` 原子，无线程安全问题）；coordinator 线程经 `getFinishedSplits` 读——实际主代码无读取方。纯内存增长发现，非竞态。
- **触发时序**: 用户实现 FLIP-27 `Source` SPI（模块公开扩展点，`SourceReaderOperator`/`LocalSourceCoordinator` 即其运行时承载）且采用连续发现语义（如目录监视类 source，每完成一个文件 split 报告一次）→ 作业长时运行期间每完成一个 split，`finishedSplits` 永久新增一个 String 条目 → 无任何 remove/TTL/上限，增长线性于 split 完成速率 × 作业时长；条目仅在协调器 unregister（作业终态）时随对象图消亡。仓库自带 runtime source（`CollectionReplayableSource`/`TimeRangeReplayableSource`）走旧 `SourceFunction` 接口不经过该路径，故 shipped 代码内不可触发——按纪律降 P3。
- **现状**: 容器存在（为 restore 往返预留的语义），但当前是纯 write-only：无消费者、无 checkpoint 参与、无清理调用。`getFinishedSplits` 的快照实现还会在读取时做一次全量 `LinkedHashSet` 拷贝（若未来被大集合调用，读取峰值再翻倍）。
- **风险**: 条目小（String），增长慢性；主要风险是语义性的——无界去重集合为未来接入 checkpoint 状态时会把全量历史带进 C5-002 的序列化放大链。当前无可达 OOM 链。
- **建议**: 三选一：(1) 删除该容器（YAGNI，待真实消费者出现再加回）；(2) 消费侧落地（enumerator 在 `snapshotState` 时把 finished 集合纳入可恢复状态并按 checkpoint 截断）；(3) 至少加上限（`maxTrackedFinishedSplits` + 淘汰）并在 javadoc 声明包络。
- **失效面枚举**（F3 同族：作业生命周期级 write-only/低消费容器全集）: `LocalSourceCoordinator.finishedSplits`（零消费者——本发现）；`TaskExecutor.submittedTasks/submittedSubtaskTasks/taskFutures`（无 remove 但实例每执行新建，随执行消亡——无发现）；`JobCoordinator.deployTransportFailures`（任务数上界——无发现）；`SideOutputRegistry.CONSUMERS`（第 1 轮 C5-007 已立项）；`SourceCoordinatorRegistry` 双 static 表（unregister 双调用点覆盖——本轮已核，声明成立）。
- **信心水平**: 确定（"零消费者、零删除"为全量 grep 事实）；可达性依赖用户自定义连续发现 source（标注为边界条件）
- **误报排除**: 不属于"keyed 状态天然随键增长"语义豁免——发现点是容器无消费者也无清理，属实现残缺而非流语义；未按"低频"豁免——split 完成报告在连续发现 source 下是数据面持续路径，只是 shipped source 恰好不触发。
- **复核状态**: 已保留

---

## 汇总（第 2 轮追加）

| 严重程度 | 数量 | 发现 |
|---|---|---|
| P0 | 0 | — |
| P1 | 1 | C5-010（超出声明包络：积压无任何可配置/声明上界，WI6 门控对该后端结构性不可达） |
| P2 | 0 | — |
| P3 | 1 | C5-011 |

**与第 1 轮合并后本维度累计**: P1×2（C5-001、C5-010）、P2×6（C5-002…C5-007）、P3×3（C5-008、C5-009、C5-011）。

**移交注记（非 C5 立项）**: ① `JdbcClusterRegistry` task_assignment 历史表 append-only、`removeTaskAssignment` 主代码零调用方——持久化无界账本（F4），移交 C7 复核；② `PollingJdbcMessageService` 分发失败仍前移 cursor——"半个成功"语义，移交 C3/C8 复核；③ `CachingTableLookup` 接线时的 refreshPool 无界队列 + 重复派发（WI11 储备能力，当前无构造点）——接线评审前置注意项。

# C5 内存有界性与大数据量 — nop-stream-core-runtime（深挖第 3 轮追加）

- **审计维度**: C5（内存有界性与大数据量），第 2 轮（`C5-round2.md`，C5-010…C5-011）之后的定向深挖追加轮
- **盲审边界（第 3 轮）**: 按追加轮纪律，仅允许读取本维度第 1、2 轮文件（`C5-memory-bounds.md`、`C5-round2.md`）与本轮新读的 live code；未读取 `ai-dev/audits/` 下其他审计目录、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`。现状标注留待复核阶段。
- **本轮深挖方向**（前两轮盲区）: (1) 前两轮未盘点的文件/包——runtime `rpc`/`protocol`/`alert`/`metrics`/`deploy`/`execution`（SupervisionLoop、分布式执行器）、core `connector` 全包、core `source` 全包、core `checkpoint/incremental|participant|storage`、`execution/flow`、`windowing` assigners/triggers/utils、`common/state` 子包（shard/simple/backend）、`S3CheckpointStorage`（第 1 轮存储后端盘点遗漏）；(2) 缓存键空间可控性补扫（`BasicTypeInfo.INSTANCES`、`StreamConnectorRegistry`、`StateMigrationRegistry`、`StreamControlRpcProxyFactory`）；(3) 峰值叠加验算补漏（多作业宿主 checkpoint 峰值叠加、OpsJobManager.rpcTargets、per-execution 实例容器）。

## 本轮已核实为有界/无发现的排查面（防复核误判）

| 排查面 | 结论 | 依据 |
|---|---|---|
| runtime `rpc` 包（StreamControlRpcServer/StreamControlRpcProxyFactory/StreamControlRpcTransformer/RemotePipelineResolver） | **无发现** | 无本地缓冲/队列/历史容器：Server 是对平台 `MessageRpcServer`+`ReflectiveRpcService` 的无状态委托（订阅制，停机即退订）；ProxyFactory 实例字段仅配置 + 单 client/proxy/channelState。缓冲行为属平台 RPC 基础设施（审计口径不重审） |
| runtime transport codec 族（AbstractStringWireCodec/Kafka/Pulsar/SysDao/Identity/DataPlaneMessageServiceAdapter） | **无发现** | 全部无状态编解码/适配器，无实例级集合 |
| core `connector` 包（StreamDataSampler/StreamConnectivityProber/StreamConnectorCatalog/registry 族） | **无发现** | StreamDataSampler 为维护命令路径且 record 数受 `limit` 参数上界；registry 的 source/sink factory Map 实例级、上界 = 已注册 connector 数；WatermarkEstimator 为 `@Deprecated` 纯接口（Stage 49 defer，主代码零实现类，不可达） |
| core `source` 包（16 文件：Source/SourceReader/SplitEnumerator/AssignmentDeliveryService/RateLimitedSourceFunction 等） | **无发现** | SPI 接口 + 无状态包装；split 状态走 enumerator snapshot 进 checkpoint（受 retention 约束） |
| runtime `metrics` 包（EngineMetrics/TaskNodeMetrics/RuntimeMetrics） | **有界（remove 配对成立）** | `EngineMetrics.BY_JOB`/`TaskNodeMetrics.BY_NODE`/双 gauge REF 表均有显式 remove：`TaskManager.java:411 releaseNode`、`JobCoordinator.java:759 releaseJob`——与 C5-007（SideOutputRegistry 无 remove）同族但清理路径存在，无发现 |
| `S3CheckpointStorage`（第 1 轮存储后端盘点遗漏的第三个实现） | **无新发现（归并口径）** | body 读取 `getBytes→readAll` 单体载入，与 Local/Jdbc 同族（格式固有，峰值归并 C5-002）；`listKeys` 分页遍历（`isTruncated`/continuation token 循环 + 截断 WARN，196-199 行）；`S3Object` try-with-resources 关闭；key 数受 maxRetainedCheckpoints（默认 5）+ retention 约束 |
| `BasicTypeInfo.INSTANCES`（static HashMap） | **有界，C5 无发现** | 键源为 plan 期类（`DataStreamImpl:417/427` 类型实参、`WindowedStreamImpl:203` 固定类），数据面记录不可控，上界 = 计划引用的类集合；非并发容器属 C1 域（登记 C1 复核候选，非 C5） |
| `StateMigrationRegistry`/`StateSchemaResolver`/`TtlContext`/`SimpleKeyedStateStore` | **无发现** | 查找接口与 wrapper，无累积容器 |
| `SupervisionLoop.regionRestartCounts`（284 行） | **有界** | 方法内局部 Map，随单次 supervision 迭代消亡；重启次数受 region restart cap 约束 |
| `EmbeddedDistributedExecutor`/`RpcDistributedExecutor` 实例集合 | **有界** | 全部 per-execution 局部/实例集合（taskManagers/taskServers/taskProxies），随执行对象图消亡（与第 2 轮 TaskExecutor 口径一致） |
| `OpsJobManager.rpcTargets` | **有界** | per-coordinator `LinkedHashMap`（106 行构造后只读，176 行消费），随 coordinator 生命周期；`jobs`/`terminalAt` retention 清扫第 1 轮已核 |
| `windowing` assigners/triggers/utils、`common/functions`、`streamrecord`、`jobgraph`/`graph`/`model`/`transformation` | **无发现** | 纯函数/不可变模型/plan 期对象，上界 = 计划规模；per-window 状态清理路径第 1 轮已核 |
| `alert` 包其余（LoggingAlertChannel/WebhookAlertPayload） | **无发现** | Webhook 队列有界已声明（第 1 轮）；payload 仅 jobId/severity/eventType/message/timestamp 五字段，无数据面引用 |
| 全仓 `readAllBytes`/`readString`/`readLines`/`toByteArray` 复扫 | **无新命中** | 全部命中点已被前两轮裁定（storage body→C5-002 族、ops server→C5-004、离线维护工具声明语义、PollingJdbcMessageService→C5-010）；`AbstractStreamOperator:112`/`SimpleStreamOperatorFactory:63` 的 `toByteArray` 为 UDF 函数对象 Java 序列化（构建期路径，体积 = 函数对象闭包，非数据面） |
| `LocalFileSegmentStore`（core checkpoint/storage） | **无发现** | `discardSegment` 物理删除；段清单受 CheckpointCoordinator retention GC 约束（第 1 轮已核） |

---

## 发现条目

### [nop-stream-core-runtime-C5-012] MemoryBudget 为 write-only 死配置：全仓零读取方，设计文档 §9 声明的 MemoryBudgetMonitor 与三档超预算策略零实现——模块唯一字节级内存上界声明无任何执行机制

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/flow/MemoryBudget.java:18-47`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/plan/DeploymentPlan.java:32,41,52,77`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/DefaultDeploymentPlanProvider.java:59`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/DeploymentPlanGenerator.java:45,79`；设计声明 `ai-dev/design/nop-stream/state-management-design.md:324-364`（§9.1/§9.2）；单位冲突自认 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/buffer/IBufferPool.java:28-30`
- **证据片段**:
  ```java
  // MemoryBudget.java:36-42 —— 预算对象随 DeploymentPlan 传播，默认 64MB 三分（50/30/20）
  public static MemoryBudget defaultLocalBudget(long totalBytes) {
      allocations.put("stateBackend", (long) (totalBytes * 0.5));
      allocations.put("edgeQueues", (long) (totalBytes * 0.3));
      allocations.put("networkBuffers", (long) (totalBytes * 0.2));
  // DeploymentPlan.java:77 —— getter 在全仓 main+test 代码中零调用方（唯一命中即定义行）
  public MemoryBudget getMemoryBudget() { return memoryBudget; }
  ```
  设计文档 state-management-design.md:344-364 声明 `class MemoryBudgetMonitor { void checkBudget() {...} }` 与超预算策略表（`TRIGGER_BACKPRESSURE` 标注为「默认」/`REJECT_NEW_DATA`/`SPILL_TO_DISK`）——`MemoryBudgetMonitor` 在主代码 grep 零命中，三档策略零实现。
- **严重程度**: P2
- **并发场景**: 无竞态——纯治理面缺失。所有执行线程（数据面生产者/消费者、checkpoint 线程、恢复线程）的内存行为均不受该预算任何形式的约束；预算也不参与提交期校验。
- **触发时序**: 非竞态发现，静态事实链：T0 作业构建 → `DefaultDeploymentPlanProvider:59` / `DeploymentPlanGenerator:45,79` 一律附加硬编码 `defaultLocalBudget(64MB)` 到 DeploymentPlan → T1..Tn 整个作业生命周期内无任何代码读取 `getMemoryBudget()`/`getAllocation()`（全仓 grep 零调用方，测试除外）→ 实际内存上界由互不相干的机制决定：`BufferPool` 65536 元素（元素制）、`EdgeConfig.queueCapacity`（元素制）、materialization store（无界，C5-001）、状态工作视图（驻堆，C5-003）、checkpoint 序列化峰值（4–8×，C5-002）→ 设计 §9.2 的运行时监控、checkBudget、超预算降级（含「默认」的 TRIGGER_BACKPRESSURE）不存在 → 任何按 plan.memoryBudget 做容量规划的运维路径都是假保证。
- **现状**: 设计文档 §9 是 owner doc 之外唯一的字节级内存治理声明（第 1 轮基线已确认 owner doc `nop-stream.md` 无数值化规模包络）；实现只落地了「预算数据对象」这一层且为 write-only 死配置，监控与强制执行层整体缺失。`IBufferPool` javadoc:28-30 明文自认「deliberately does not interact with `MemoryBudget.networkBuffers` (byte-based); the two units are incomparable and no conversion is performed」——即模块自身已声明元素制设施与字节制预算互不相通，但字节制一侧没有任何其他执行点承接。
- **风险**: 容量规划假保证是本发现的独立风险：部署计划展示 64MB 预算，而真实内存上界由元素计数与无界容器决定，偏差可达数量级（C5-001 的 materialization store 1K rec/s × 1KB ≈ 86GB/天 vs 预算 64MB）。本发现同时是 C5-001/C5-002/C5-003 的治理层注脚——「预算内不 OOM」的 C5 维度目标在声明层即无承载机制。审计判级口径对照：误报校准豁免「有界声明存在的缓存/队列」，本处恰为反例——**声明存在而守卫缺失**，属应报形态。
- **建议**: (1) 最小修复：从 `DeploymentPlan` 移除 MemoryBudget，或在类 javadoc + owner doc 明示「当前仅为记录性声明，未强制执行」，消除容量规划误导；(2) 提交期静态验算（无需运行时监控即可闭环）：`edgeQueues` 预算 = Σ(queueCapacity × parallelism × estimatedItemSize)、`networkBuffers` 对照 RemoteInputChannel 队列深度的字节换算，提交时超预算 fail-fast（typed `ERR_STREAM_*`）；(3) 中期按设计 §9.2 实现最小监控子集——复用平台 `HighWatermarkSemaphore` 语义做水位告警（触发时仅 WARN + 指标，不承诺自动降级），并给 `IBufferPool`/`CacheConfig` 增加可选字节化上限与预算联动。
- **失效面枚举**（F3 同族：模块内全部内存/流控上界声明点的执行状态盘点）: `MemoryBudget`（write-only——本发现）；`MemoryBudgetMonitor` + 超预算策略表（设计声明、零实现——本发现）；`IBufferPool` 65536 元素全局默认（元素制有界、javadoc 明示不与预算换算——声明边界成立，无发现）；`EdgeConfig.queueCapacity`（`LinkedBlockingQueue` 容量强制——声明成立）；`RemoteInputChannel` 1024 + typed overflow（第 1 轮已核，声明成立）；WI6 `nop.stream.data.db.backlog.limit`（opt-in 且对 PollingJdbcMessageService 结构性不可达——C5-010 已立项）；`CheckpointHistory` 100 条 + retention（第 1 轮已核，声明成立）；`FlowControlPolicy` 单值枚举 + `RecordWriter.validateFlowControlPolicy:137-140` fail-fast（声明成立）；`FlowControlPolicy` 中被永久移除的 credit-based 策略（enum 注释声明，无残留路径）。
- **信心水平**: 确定（「零调用方/零实现」为全仓 grep 事实；设计-实现差距逐文件核对）
- **误报排除**: 不属于「有界声明存在的缓存/队列」误报校准项——该豁免针对声明与实现一致的情形，本发现的本体恰是声明与实现的断裂；不属于「低频管理面粗粒度」豁免——预算声称约束的是数据面常驻内存，是 C5 维度的核心命题；也非纯文档瑕疵——设计文档给出了具体类名、方法签名与默认策略，属可验收承诺未兑现，P2 定级与「有界但边界配置缺陷」校准一致。
- **复核状态**: 已保留

---

## 峰值叠加验算补漏注记（非新立项）

- **多作业宿主 checkpoint 峰值叠加**：`OpsJobManager` 宿主 N 个 JobCoordinator 时，各作业 `CheckpointCoordinator` 独立周期触发、无全局节流，C5-002 的 4–8× 序列化峰值可在时间窗内并发叠加，宿主进程峰值上界 ≈ N × 单作业峰值；restore 路径同理（多作业同时恢复时 `CheckpointSerDe.deserializeCheckpoint` 全状态解析峰值线性叠加）。属 C5-002 已立项风险的线性乘数，不另立项，复核 C5-002 时应补充多作业叠加口径。
- **本地/嵌入式模式叠加**：`StreamExecutionEnvironment.runLocal` 每次执行新建 TaskExecutor + 执行对象图（第 2 轮已核随执行消亡）；同一 JVM 串行多次 runLocal 不叠加（前一对象图可回收），并发多次 runLocal 叠加 = 执行数 × 单执行峰值，属调用方并发语义，无可模块内修复的缺陷点。

---

## 汇总（第 3 轮追加）

| 严重程度 | 数量 | 发现 |
|---|---|---|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 1 | C5-012 |
| P3 | 0 | — |

**与前两轮合并后本维度累计**: P1×2（C5-001、C5-010）、P2×7（C5-002…C5-007、C5-012）、P3×3（C5-008、C5-009、C5-011）。

**移交注记（非 C5 立项）**: `BasicTypeInfo.INSTANCES` 为非并发 static HashMap（`of()` 路径 check-then-put）——键源为 plan 期类、内存有界，无 C5 内存问题；线程安全归 C1 维度复核。

---

# C5 内存有界性 — 维度复核结论（独立复核子代理，2026-10-14）

- **复核方式**: 本轮解除盲审——已读取 `ai-dev/audits/` 历史审计（`check/stream-core.md` 2026-08-20、`2026-05-31-adversarial-review-nop-stream-r16`、`2026-09-26…10-06 deep-audit-nop-stream-quality r1–r6`、对抗审查 r4/r5/r6、`nop-stream-production` 双轮 multi-audit）与 `git log`/`git log -S`；对 12 条发现逐条重读 live code（本 worktree 实测 nop-stream-core 439 files / runtime 132 files，与初审声明口径一致）。规模判断对照 owner doc `docs-for-ai/03-modules/nop-stream.md`（无数值化规模包络；有界声明 = checkpoint history 100 条 + retention、WI6 backlog limit、commit 执行器有界队列、webhook 有界队列等）与 `ai-dev/design/nop-stream/state-management-design.md` §序列化权衡（「适合几十 GB 级状态」:239）。
- **判定汇总**: 保留 11 / 降级 1 / 驳回 0。P1×2（C5-001、C5-010）全部维持；P2×6 维持；P3×2 维持、P3×1 降级。

## 维度复核结论
> 注：原「逐条判定」节，2026-10-14 closure audit 要求标准化为本标题；内容未改动。

- **C5-001 — 已保留（P1）**。live 重核成立：`InMemoryMaterializationPoint.store` 只 add 无 remove/truncate（`IMaterializationPoint` SPI 无清理方法），`ResultPartition.enqueueWithBackpressure` 的 pool+materialization 象限 offer 失败即 `bufferPool.release()` 放回许可，池背压被旁路，javadoc「由 IBufferPool 控制上游内存压力」声明与代码矛盾恰是缺陷本体；owner doc 与设计文档均未为 materialization store 声明任何包络，不符合「已声明边界+守卫」豁免。
  - **现状: 已知未修复** — `ai-dev/audits/check/stream-core.md`（2026-08-20）[P2]「InMemoryMaterializationPoint 无淘汰的无界内存增长」同一发现；`2026-09-29 deep-audit-nop-stream-quality-r3/summary.md` 引 plan-01 Deferred A5「已记录 store 无界」（owner 语义确认后单独立项，未实施）。该文件自创建提交 `53842e1480`（plan-2026-08-03-1600）后零改动。本轮维持 P1（历史 P2 定级早于回放阻塞族缺陷闭合语境；热路径线性无界 + 无任何可配上限成立）。
- **C5-002 — 已保留（P2）**。live 重核成立：`PerKeyOrderedBuffer.freeze()` = `fromSerializableForm(toSerializableForm())` 逐条目 `SnapshotCopyUtil.defensiveCopy`（JSON 往返）全量拷贝；`CheckpointSerDe.serializeOperatorStates` 的 `PerKeyOrderedBuffer` 分支对 frozen 副本**再调一次** `toSerializableForm()`（freeze 重建在该路径纯浪费）；`computeCanonicalChecksumHex` serialize→parseMap→normalize→serialize 全树多份往返。峰值 4–8× 为量级估算口径，标注恰当。
  - **现状: 新发现** — freeze 机制本体来自 R6 ST-02 快照撕裂修复（plan 2323），其内存峰值放大此前未立项；多作业宿主峰值线性叠加（第 3 轮补漏注记）一并归入本条复核口径。
- **C5-003 — 已保留（P2）**。live 重核成立：EquiJoin/OverWindow 的 `transient PerKeyOrderedBuffer` 工作视图为纯堆对象（durable 副本走 keyed 后端但工作视图全量驻堆）；`StreamReduceOperator.values` 为普通堆 Map、完全绕过状态后端。设计文档「几十 GB 级状态」声明对这些路径不可达，属实现与声明的包络错配，非流语义豁免项。
  - **现状: 新发现** — r1–r6 质量审计与对抗审查均未从包络角度立项（r6 ST 族关注撕裂/漂移正确性）。
- **C5-004 — 已保留（P2）**。live 重核成立：`StreamOpsHttpServer.handleSubmit` 的 `readAllBytes` 无 Content-Length 校验/限量读取；`JobSubmissionSpec` 仅 jobId/pipelineFactoryClass/params 三字段，1MB 量级上限即可闭合；第 2 轮已核 `newFixedThreadPool(4)` 只限并发不限单体。认证边界（loopback 零认证 / 非 loopback 强制 token，F-09b）不改变「合法调用方单请求打爆堆」。
  - **现状: 新发现** — 历史对抗审查（r4/r5/r6）只覆盖鉴权与类加载加固面（F-09a/F-09b），未覆盖请求体上限。
- **C5-005 — 已保留（P2）**。live 重核成立：`TaskMailbox` 双 `ConcurrentLinkedQueue` 无容量/无 `QueueOverflowPolicy` 接入；`ProcessingTimeServiceDriver.run()` 每 tick(100ms) 定时器 due 即无条件 `mailbox.put` 新 Mail，无「已 pending 即跳过」去抖。本地/嵌入式慢 sink 背压可无限期持续，该场景无 liveness 兜底。
  - **现状: 新发现** — r6 曾核「TaskMailbox 双优先级队列」但属并发正确性 PASS 口径（liveness/优先级），内存累积角度未立项。
- **C5-006 — 已保留（P2）**。live 重核成立：`notifyParticipantsFinishCommit` 对 finishCommit 抛异常的参与者按 checkpointId 入账 `failedCommitParticipants`；`retryFailedCommits` 仅在重试全成功或参与者已注销时移除；持续失败场景条目按 checkpoint 周期单调增长且钉住 `checkpointSuccessMap` 条目（abort 门控 1067/1148 行 containsKey 判断），无条目上限、无重试轮次升级出口。
  - **现状: 已知未修复（部分归属）** — `2026-05-31-adversarial-review-nop-stream-r16/01-open-findings.md` **AR-10**（P1）「checkpointSuccessMap 无限增长」，并点名 `failedCommitParticipants`「有类似问题」；AR-10 主泄漏（成功路径条目滞留）已由提交 `8355aea426`（plan 2326 R6 治理批次一）修复（成功 fan-out 任务 1009 行 remove + A2'/abort 路径 1067/1148 行 remove）；failedCommitParticipants 条件性无界残留即本条。触发条件窄（通知持续失败而任务不失败），维持 P2 而非 P1。
- **C5-007 — 已保留（P2）**。live 重核成立：`SideOutputRegistry.CONSUMERS` 为进程级 static，主代码唯一写点 `SideOutputDataStream:90`（闭包捕获 outputTag 与 sinkFunction），`remove`/`clear` 在主代码零调用方（仅测试）；同 tag replace 语义使泄漏条件为「不同 tag id 的多作业 churn」，多作业宿主（OpsJobManager + REST）是 owner doc 声明的运维形态。
  - **现状: 新发现** — 全部历史审计（含 2026-05/06 对抗审查系列、production multi-audit）无 SideOutputRegistry 泄漏立项。
- **C5-008 — 已保留（P3）**。live 重核成立：EquiJoin:185 / OverWindow:130 / WindowOperator:587 三处以 `getClass().getSimpleName() + "@" + identityHashCode` 作 metric tag，重启即换标签；owner doc operator 层标准标签集（jobId/vertexId/subtask）已定义，三处偏离。micrometer 弱引用限制堆风险，运维噪声为主体，P3 合理。
  - **现状: 新发现**。
- **C5-009 — 降级（P3 → 备案注记，不计入保留项）**。证据存在事实错误：初审称 `ClassNameValidator.validateClassName`「仅拒绝非法形式，不设白名单上限」，live 代码实为 **24 项前缀白名单**（`io.nop.stream/commons/core/dao.`、`java.lang/util/math/time/io/nio.`、`javax.sql/crypto/net.`、`jakarta.annotation/persistence.`、数组形式）——键空间被白名单收窄到可信前缀，「被攻陷上游可钉住 classpath 内大量类」不成立（至多钉住可信前缀下已存在的类，即引擎正常运行加载的类集）。`2026-09-27 deep-audit-nop-stream-quality-r2/05-regression-review-358-359-360.md` A1 已对该缓存裁定「条目有界」PASS（该缓存本身即 2026-09-26 P0-5 性能修复的产物）。符合「已声明边界+守卫」豁免，按误报校准降级。残余真实点仅一条，留作搭车项：`resolveValueType` 用 `Class.forName(name)`（触发静态初始化），与 F-09a 工厂加载加固惯例（`forName(name, false, loader)` 先接口检查后初始化）不一致。
- **C5-010 — 已保留（P1）**。live 重核成立：`poll()` 查询无 `LIMIT`/批量上限，`readRows` 将 `id > cursor` 全部行（含完整 payload byte[]）物化进 ArrayList 后逐条分发；数据面/task 控制 topic 订阅引导 cursor=0（替换 TaskManager 首次订阅即全量重放）；`PollingJdbcMessageService` 未实现 `ChannelBacklogProbe`（launch 包 grep 零命中），WI6 backlog limit 对该后端结构性不可达；表行无 ack 删除、积压单向增长；Limitations 清单未声明该点。测试基础设施豁免不适用：类在 main sources、经 `feb189859b`（WI1 plan 2328）main-scope 化、owner doc :270 明列为 runtime jar 集群启动组件、`nop-stream-cluster.sh` 直接可用。
  - **现状: 新发现** — 历史审计零立项。移交注记（cursor 在分发异常时仍前移的「半个成功」语义）经重核属实，归 C3/C8。
- **C5-011 — 已保留（P3）**。live 重核成立：`LocalSourceCoordinator.finishedSplits` 只 `addAll` 永不移除，`getFinishedSplits()` 主代码零调用方；shipped source 走旧 `SourceFunction` 接口不经过该路径，可达性依赖用户 FLIP-27 SPI，P3 定级恰当。
  - **现状: 新发现** — 容器由 `73d8eeb406`（WI2 plan 2343）引入，为 restore 往返预留，历史审计未立项。
- **C5-012 — 已保留（P2）**。live 重核成立：`DeploymentPlan.getMemoryBudget()` 全仓 main+test 零调用方（唯一命中即定义行）；`MemoryBudgetMonitor` 主代码零命中；`defaultLocalBudget(64MB)` 在 `DefaultDeploymentPlanProvider:59` 与 `DeploymentPlanGenerator:45,79` 硬编码附加；`IBufferPool` javadoc:28-30 明文自认元素制与字节制预算互不相通。设计文档 §9.1/§9.2（:324-366）的监控类与三档超预算策略（含「默认」TRIGGER_BACKPRESSURE）零实现——「声明存在而守卫缺失」正是应报形态，不适用「有界声明」误报豁免。
  - **现状: 新发现（部分已知）** — r3 `02-readability-structure.md` 曾以死代码角度登记 `MemoryBudget.getTotalBytes/getComponentAllocations`、r6 `07-readability-structure.md` 登记 `DeploymentPlan.getMemoryBudget` 死 getter；「治理声明零执行机制」的定性口径为本轮新立项。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状标注 |
|---|---|---|---|---|
| C5-001 | P1 | `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/materialization/InMemoryMaterializationPoint.java` | materialization 边每条记录永久驻留堆内 ArrayList，无截断/淘汰路径，池背压被溢出旁路架空，线性于吞吐×时长直至 OOM | 已知未修复（check/stream-core.md 2026-08-20 [P2]；plan-01 A5 deferred；创建后零改动） |
| C5-002 | P2 | `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java` | checkpoint 序列化链 freeze 深拷贝 + 重复 form 转换 + checksum 多轮全量往返，峰值 ≈ 状态体积 4–8×，声明包络在该路径不可达 | 新发现 |
| C5-003 | P2 | `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java`（同族 OverWindowOperator / StreamReduceOperator） | join/over-window/reduce 工作视图全量驻堆，RocksDB「几十 GB 级状态」声明包络对这些算子不可达 | 新发现 |
| C5-004 | P2 | `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamOpsHttpServer.java` | 提交端点无大小上限 readAllBytes，单请求即可 ≈3× body 峰值击穿 coordinator 堆 | 新发现 |
| C5-005 | P2 | `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/TaskMailbox.java`（同族 ProcessingTimeServiceDriver） | 无界邮箱 + 定时器 driver 每 100ms 无去抖投递，慢 sink 背压期 10Hz 慢性累积无回收路径 | 新发现 |
| C5-006 | P2 | `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java` | 参与者 finishCommit 持续失败时 failedCommitParticipants/checkpointSuccessMap 按 checkpoint 频率累积，无条目上限与升级出口 | 已知未修复·部分（AR-10 r16 点名；主泄漏已由 8355aea426 修复，本条残留） |
| C5-007 | P2 | `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SideOutputRegistry.java` | static 消费者注册表主代码无 remove，多作业 churn 按提交次数累积并钉住整条 side-output sink 对象图 | 新发现 |
| C5-008 | P3 | `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java:185`（同族 OverWindow:130 / WindowOperator:587） | 算子指标以 identityHashCode 作标签，重启换标签致 Prometheus 高基数 churn | 新发现 |
| C5-010 | P1 | `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java` | 轮询无 LIMIT 全量物化 topic backlog，cursor=0 引导 + 无 ChannelBacklogProbe 使 WI6 门控不可达，单次 poll 可击穿进程堆 | 新发现 |
| C5-011 | P3 | `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java` | finishedSplits 为 write-only 无界去重集合：只 addAll、零消费者、零删除 | 新发现 |
| C5-012 | P2 | `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/flow/MemoryBudget.java`（关联 DeploymentPlan.java:77） | MemoryBudget 为 write-only 死配置（getMemoryBudget 零调用方），设计 §9 的 MemoryBudgetMonitor 与三档超预算策略零实现，字节级内存上界声明无执行机制 | 新发现（部分已知：r3/r6 曾以死代码角度登记相关访问器） |

**降级项**: C5-009（P3 → 备案注记）——证据误述 `ClassNameValidator` 为「非白名单」，实为 24 项前缀白名单；缓存有界且受守卫，r2 回归评审已裁定 PASS；残余仅 `Class.forName` 带初始化与 F-09a 惯例不一致，留作加固搭车项。

**现状标注统计**: 新发现 8（C5-002/003/004/005/007/008/010/011）；已知未修复 3（C5-001 全额、C5-006 部分、C5-012 部分）；已修复 0（C5-006 关联的 AR-10 主泄漏已修但不构成本条闭合）。

**文档缺陷附议（维持初审建议）**: owner doc `nop-stream.md` 无数值化规模包络——join/over-window/reduce 工作视图实际包络 = 堆（C5-003）、checkpoint 峰值倍数（C5-002）、materialization store 无界（C5-001）建议在 owner doc 补「规模与内存包络」节，作为最小修复先行。
