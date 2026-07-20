# nop-stream vs Apache Flink 架构对比与低成本设计迁移分析

> 分析日期: 2026-07-20
> 范围: ~/sources/flink (master) vs nop-entropy/nop-stream
> 目标: 从 Flink 中识别可用极低实现成本移植到 nop-stream 的设计

---

## 1. 整体架构对比

### Flink 架构（五层管线）

```
DataStream API / Table API / SQL
        ↓
  StreamGraph（逻辑算子 DAG）
        ↓
  JobGraph（物理优化：算子链化 + 中间数据集）
        ↓
  ExecutionGraph（可调度执行图 + 并行度展开）
        ↓
  Runtime（TaskManager / JobManager / Dispatcher）
```

### nop-stream 架构（五层管线 + 声明式入口）

```
XDSL 声明式模型  ←→  Java DataStream API  ←→  Delta 定制
        ↓
  StreamModel（canonical 模型 + 组件注册表）
        ↓
  StreamGraph（逻辑算子 DAG）
        ↓
  JobGraph（物理优化：算子链化 + 中间数据集）
        ↓
  PartitionedPlan → DeploymentPlan（分区策略与部署规划）
        ↓
  GraphExecutionPlan（可调度执行计划）
        ↓
  TaskExecutor（本地执行，分布式调度规划中）
```

### 核心差异

| 维度 | Flink | nop-stream | 评估 |
|------|-------|------------|------|
| 声明式入口 | 无（仅编程 API + Table/SQL） | **XDSL 声明式模型** + DataStream API + Delta | nop-stream 独有优势 |
| 管线层数 | StreamGraph → JobGraph → ExecutionGraph → Runtime | StreamModel → StreamGraph → JobGraph → PartitionedPlan/DeploymentPlan → GraphExecutionPlan | nop-stream 多一层声明式 canonical 模型 |
| 调度能力 | 分布式生产级 | LOCAL 可执行，分布式规划中 | Flink 领先 |
| 弹性伸缩 | SlotManager + ResourceManager | ClusterRegistry + LeaseInfo | Flink 领先 |
| 状态后端 | Memory / RocksDB / HeapKeyed | Memory only | Flink 领先 |
| 算子丰富度 | 全面 | 核心算子完备（map/filter/flatMap/keyBy/window/aggregate/process/CEP/union/sideOutput/custom） | 差距不大 |

---

## 2. 各子系统深度对比与低成本迁移建议

### 2.1 图模型管线（核心架构 — 不推荐迁移）

| 对比项 | Flink | nop-stream |
|--------|-------|------------|
| StreamGraph | 1710 行，含 ExecutionConfig / CheckpointConfig 全局配置 | 253 行，轻量级，配置已上移到 StreamModel |
| JobGraph | StreamingJobGraphGenerator 含 OperatorChain + 中间数据集分配 | JobGraphGenerator 清晰简洁 |
| ExecutionGraph | DefaultExecutionGraph ~2300 行，复杂调度状态机 | GraphExecutionPlan 479 行，直接可执行 |
| 拓扑排序 | Kahn 算法 | Kahn 算法（一致） |

**结论**: nop-stream 图模型管线已成熟。GraphExecutionPlan 在 LOCAL 模式下比 Flink ExecutionGraph 更简洁高效。**不推荐迁移**。

---

### 2.2 Operator Chain（适度参考 Flink 的链化策略检查）

| 对比项 | Flink | nop-stream |
|--------|-------|------------|
| ChainingStrategy | ALWAYS / NEVER / HEAD + 上下游并行度/分区器检查 | 相同枚举 + ChainingOutput |
| 链化实现 | OperatorChain 内 RecordWriterOutput + ChainingOutput | OperatorChain + ChainingOutput / RecordWriterOutput / BroadcastingRecordWriterOutput |
| Keyed 链化 | KeyGroupStreamInput + KeyContext 切换 | KeyExtractingOutput + KeyContext |
| 链化条件检查 | 完整（并行度、分区器、算子语义） | 基础 |

**低成本建议**:

1. ✅ **链化条件规则完善**（极低成本, < 1天）
   - **现状**: nop-stream 链化策略相对宽松，未严格检查上下游并行度匹配和分区器类型。
   - **建议**: 在 JobGraphGenerator 中增加链化约束检查：
     - 下游并行度 != 上游并行度 → 强制拆链
     - 分区器非 ForwardPartitioner → 强制拆链
     - 参考 Flink 的 `StreamingJobGraphGenerator.isChainable()` 实现。

2. ❌ **KeyGroup 链化**（中等成本，暂不推荐）
   - Flink 在链化 KeyedStream 时使用 KeyGroup 隔离状态。
   - nop-stream 的 KeyExtractingOutput 功能等价，但缺少 KeyGroup 分区。当前不影响功能正确性。

---

### 2.3 Window Operator（关键差异区域 — 最具迁移价值）

这是 nop-stream 中复杂度最高的子系统，也是 Flink 最具参考价值的部分。

| 对比项 | Flink | nop-stream |
|--------|-------|------------|
| WindowOperator | ~2300 行，InternalTimerService + KeyedStateBackend | 新版 ~1765 行（runtime） + 旧版 ~887 行（已 deprecated） |
| Timer 存储 | KeyGroupedPriorityQueue | TreeMap<Long, Set<TimerEntry>> |
| State 集成 | 全状态类型通过 KeyedStateBackend | 新版通过 KeyedStateStore；旧版 LinkedHashMap 自行管理 |
| Evictor | 完整（Count / Time / Delta） | Evictor 接口已定义，新版已集成 |
| MergingWindow | MergingWindowSet + session window | MergingWindowSet + runtime 层支持 |

**低成本建议**:

1. ✅ **TimerService PriorityQueue 重构**（极低成本, 半天）
   - **文件**: `HeapInternalTimerService.java`
   - **现状**: `TreeMap<Long, Set<TimerEntry<N>>>` — 每次 advanceWatermark 需要 `headMap(newWatermark, true)` 创建视图并迭代，创建视图是 O(log n)，但 full iteration 后续还是 O(m)。
   - **建议**: 替换为 `PriorityQueue<TimerEntry<N>>`，按 timestamp 排序。`peek()` O(1), `poll()` O(log n)。Timer 插入时按 timestamp 入队，触发时 peek + poll 直到第一个未到期 timer。
   - **成本**: 修改 `HeapInternalTimerService` 内部数据结构，调整约 80 行代码。已有测试 `TestHeapInternalTimerService` 可验证正确性。

2. ✅ **ProcessingTimeTimer 实现**（极低成本, 半天）
   - **文件**: `HeapInternalTimerService.java`
   - **现状**: `registerProcessingTimeTimer()` 和 `deleteProcessingTimeTimer()` 为 no-op
   - **建议**: 复用 `ProcessingTimeService.registerTimer()` 注册处理时间回调。在 `TimerServiceManager` 中统一调度。nop-stream 的 `TimestampsAndWatermarksOperator` 已经展示了如何通过 `ProcessingTimeService` 调度定时任务。
   - **影响**: 当前 processing-time window（`TumblingProcessingTimeWindows` / `SlidingProcessingTimeWindows`）依赖此功能，实现后 processing time 窗口才真正可用。

3. ✅ **WindowAggregationOperator 退役**（低成本, 1天）
   - **现状**: `WindowAggregationOperator` 标注了 `@Deprecated`，但仍在代码库中存在，且有测试依赖它。
   - **建议**: 
     - 将 `WindowAggregationOperator` 的测试迁移到 `WindowOperator`（已通过 `TestWindowOperatorBasic` 等覆盖）
     - 彻底删除 `WindowAggregationOperator.java`（~887行）
     - 确保 `IWindowOperatorFactory` 是唯一的窗口算子工厂入口

4. ✅ **MergingWindowSet 增量合并缓存**（中等成本, 1-2天）
   - **建议**: 参考 Flink 的 `MergingWindowSet.persistMergedWindow()` 方法，增加合并结果缓存以避免 session window 频繁合并时的重复计算。

---

### 2.4 Timer Service 深入比较（成本最低、收益最高）

这是架构差异最集中、且迁移成本最低的区域。

```
Flink InternalTimerServiceImpl (~600行):
  - InternalTimerService<TimerSnapshot> 接口
  - KeyGroupedPriorityQueue<TimerSnapshot> 内部存储
  - 每个 KeyGroup 一个优先队列
  - 快照时序列化整个队列 → StateSnapshot
  - Processing time 通过 internal ScheduledThreadPool 触发
  - Timer 删除需 key + namespace + timestamp 精确匹配

nop-stream HeapInternalTimerService (~187行):
  - InternalTimerService<N> 接口 (从 Flink 移植)
  - TreeMap<Long, Set<TimerEntry<N>>> 内部存储
  - 全局一个 TreeMap，无 KeyGroup 划分
  - 快照时 forEachEventTimeTimer 遍历 → 手动序列化
  - Processing time 为 no-op
  - Timer 删除通过 key + namespace + timestamp equals 匹配
```

**三个具体迁移方案**:

**方案 A — PriorityQueue 重构**（极低成本, 半天）

| 操作 | 当前 TreeMap 复杂度 | PriorityQueue 复杂度 |
|------|-------------------|---------------------|
| 注册 timer | O(log n) | O(log n) |
| 触发最小 timer | O(log n) (headMap) | O(1) peek + O(log n) poll |
| 删除 timer | O(log n + m) | O(n) scan (需要额外 Map 索引) |

**注意**: PriorityQueue 不支持 O(1) 删除，需要引入 `HashMap<TimerKey, TimerEntry>` 辅助索引。Flink 使用 `KeyGroupedPriorityQueue` 加 `TimerSnapshot` 解决了这个问题。

**建议方案**: 保留 TreeMap 但优化触发方式 — 使用 `TreeMap.firstKey()` 检查最小时间，用 `pollFirstEntry()` 取出。当前 `headMap` 视图不是必须的，可直接用 `while (!eventTimeTimers.isEmpty() && eventTimeTimers.firstKey() <= newWatermark)` 替换。

**方案 B — ProcessingTimeTimer**（极低成本, 半天）

```
当前:
  registerProcessingTimeTimer() = no-op
  forEachProcessingTimeTimer() = no-op

目标:
  registerProcessingTimeTimer(N namespace, long time):
    processingTimeTimers.computeIfAbsent(time, k -> new HashSet<>()).add(new TimerEntry<>(key, namespace, time))
    processingTimeService.registerTimer(time, this::onProcessingTimeCallback)

  onProcessingTimeCallback(long timestamp):
    Set<TimerEntry<N>> timers = processingTimeTimers.remove(timestamp)
    if (timers != null) {
      for (TimerEntry<N> timer : timers) {
        triggerable.onProcessingTime(new HeapInternalTimer<>(timer.key, timer.timestamp, timer.namespace))
      }
    }
```

**方案 C — 差量 Timer 快照**（低成本, 1天）

当前 checkpoint 时完整序列化所有 timer。在大量 timer（百万级）场景下性能差。建议增加增量快照支持：
- 记录自上次 checkpoint 以来的 timer 变更（注册/删除）
- checkpoint 时只写入变更日志 + 全量快照的引用

---

### 2.5 Checkpoint 机制（功能完备，稳定性加固）

| 对比项 | Flink | nop-stream |
|--------|-------|------------|
| Barrier 对齐 | 严格一次 / 至少一次 | 完全支持 |
| CheckpointCoordinator | ~1600 行，含完整状态机 | ~583 行，简化版 |
| 状态机 | PAUSED / IN_PROGRESS / COMPLETED / FAILED / ABORTED / CANCELED | Pending → Completed / Failed（简化） |
| 存储后端 | HDFS / S3 / JDBC / Local | LocalFile / Jdbc |
| Savepoint | 全量快照 + 可恢复 | 基础支持（有测试覆盖） |
| 对齐超时 | 超时后自动切至少一次 | alignmentTimeoutMs 参数已定义 |
| 两阶段提交 | TwoPhaseCommitSinkFunction | 已有实现 + 测试 |

**低成本建议**:

1. ✅ **PendingCheckpoint 状态机增强**（低成本, 1天）
   - **现状**: nop-stream 的 `PendingCheckpoint` 状态转换较简单。
   - **建议**: 引入 `PendingCheckpointState` 枚举（`PENDING / IN_PROGRESS / COMPLETED / FAILED / ABORTED`），参考 Flink 的 `PendingCheckpoint` 实现状态转换验证（如: IN_PROGRESS 不能直接转 COMPLETED，必须等待所有 ACK）。

2. ✅ **Checkpoint 失败原因追踪**（极低成本, < 1天）
   - 增加 `CheckpointMetrics.failureCause` 字段，记录失败异常类型和消息。

---

### 2.6 Source/Sink 连接器

| 对比项 | Flink | nop-stream |
|--------|-------|------------|
| Source API | SourceFunction + 新 Source 接口 | SourceFunction + WatermarkEstimator + DrainableSource |
| Sink API | SinkFunction + 两阶段提交 | SinkFunction + TwoPhaseCommitSinkFunction |
| Kafka | FlinkKafkaConsumer | 无（可通过 MessageSourceFunction 封装） |
| CDC | Debezium connector | DebeziumCdcSourceFunction（已有） |
| Batch | InputFormat source | BatchLoaderSourceFunction / BatchConsumerSinkFunction |
| 一致性 | AT_LEAST_ONCE / EXACTLY_ONCE | SourceConsistencyCapability / SinkConsistencyCapability 已定义 |

**低成本建议**:

1. ✅ **新 Source API 核心抽象**（中等成本, 2-3天）
   - Flink 1.12+ 新 Source API 的核心思想是 **SplitEnumerator + SourceReader** 分离。
   - nop-stream 的 `DrainableSource` / `DynamicSplitRequest/Response` / `SourceEnumerator` 已经走上了类似路线。
   - 将当前`SourceFunction` 概念升级为 `Source<OUT, SplitT, CheckpointT>` 三元组:
     - `SourceReader<OUT, SplitT>` — 实际读取数据
     - `SplitEnumerator<SplitT, CheckpointT>` — 管理分片分配
     - `Source<OUT, SplitT, CheckpointT>` — 工厂

2. ✅ **Kafka 连接器**（中等成本, 1-2天）
   - 依赖 `kafka-clients`，实现 KafkaSourceFunction。
   - 可利用现有的 `MessageSourceFunction` 作为基类。

---

### 2.7 分布式执行框架

| 对比项 | Flink | nop-stream |
|--------|-------|------------|
| 资源管理 | SlotManager + ResourceManager | ClusterRegistry + LeaseInfo + NodeInfo |
| 部署 | JobManager → TaskManager 调度 | EmbeddedDistributedExecutor（进程内） |
| RPC | Akka / Netty | IStreamCoordinatorRpcService + IStreamTaskRpcService 接口 |
| 远程 shuffle | Netty-based | RemoteInputChannel + RemoteResultPartition（骨架） |
| 高可用 | ZooKeeper / Kubernetes | 无（单机模式） |

**低成本建议**:

1. ✅ **RPC 轻量级实现**（低成本, 1-2天）
   - 当前 `IStreamCoordinatorRpcService` 和 `IStreamTaskRpcService` 只是接口定义。
   - 建议实现一个基于 Vert.x 的简单 RPC（nop-entropy 已有 `nop-vertx` 模块可用，见 `nop-cluster-vertx`）。
   - **重用路径**: 检查 `nop-cluster-vertx` 是否已有通用的 RPC 基础设施，直接适配。

2. ✅ **远程 shuffle 背压支持**（中等成本, 2-3天）
   - 当前 `RemoteInputChannel` / `RemoteResultPartition` 是基本骨架。
   - 参考 Flink 的 `RemoteInputChannel.onBuffer()` 背压 + `BufferListener` 模式。
   - Flink 使用 `BufferListener` 接口实现: `notifyBufferAvailable()` 回调 → 信用通告。

---

### 2.8 分区策略与部署计划（nop-stream 独有的优势层）

nop-stream 的 `PartitionedPlan` 和 `DeploymentPlan` 将分区策略从执行管线中分离，这是 Flink 没有的：

| 组件 | 角色 |
|------|------|
| PartitionedPlan | 定义每个顶点的并行度和每个边的分区策略 |
| DeploymentPlan | 定义 edge 配置（背压策略、队列容量、内存预算） |
| PartitionRouter | FORWARD / HASH / REBALANCE / BROADCAST 路由策略 |
| FlowControlPolicy | 流量控制策略 |

**建议**: 这是 nop-stream 的差异化亮点。持续强化部署计划的动态重分区能力。

---

### 2.9 XDSL 声明式模型（nop-stream 核心竞争力）

nop-stream 的 `StreamModel` 通过 `stream.xdef` XDSL schema 定义了完整的声明式流处理模型，这是 **Flink 完全没有的能力**：

| 特性 | 支持程度 |
|------|---------|
| 组件注册表 | windowingStrategies / coders / schemas 通过稳定 ID 引用 |
| 可逆计算 | x:extends 继承 + Delta 定制 |
| Xpl 模板 | map/filter/flatMap 逻辑直接嵌入 Xpl 模板函数 |
| 多环境 | environments 支持不同 deploymentMode 配置 |
| 生命周期 | onStart / onEnd / onError 回调 |
| CEP 声明式 | pattern.xdef 中定义复杂事件模式 |
| 侧输入 | sideInputs 流间引用 |
| Checkpoint 声明式 | checkpoint config + participants 声明 |

**建议**:
1. ✅ **XDSL 图模型 → 执行管线的编译器**（规划中但关键）
   - 将 `StreamModel` → `StreamGraph` 的转换管线完整化（当前 nop-stream-flow 模块标注为"规划中"）。
   - 实现 `StreamModelCompiler` 将声明式模型编译为 `StreamGraph`。

2. ✅ **Xpl 函数与算子绑定**（中等成本）
   - XDSL 中的 `<source>xpl-fn:(event)=>any</source>` 函数需要与算子实例绑定。
   - 建议实现 `XplFunctionAdapter` 将 Xpl 函数包装为 `MapFunction` / `FilterFunction` 等接口。

3. ❌ **声明式窗口策略 DSL**（低优先）
   - 当前窗口策略通过 `strategyId` 引用 `windowingStrategies` 注册表，已经足够。

---

### 2.10 CEP 引擎（nop-stream 独有完整实现）

| 对比项 | Flink | nop-stream |
|--------|-------|------------|
| 模式定义 | Pattern API (Java/Scala) | CepPatternModel（声明式）+ Pattern API |
| NFA 状态机 | NFA 类 | NfaState 状态机（已有） |
| 共享缓冲区 | SharedBuffer | 待确认 |
| 声明式约束 | 编程方式 | pattern.xdef 中声明式定义 |
| 时间窗口 | within() / times() | 声明式 within / times |

**建议**: **不推荐迁移**。nop-stream 的 CEP 模块已经完整，声明式模式定义是亮点。只需确保 NFA 引擎与 WindowOperator 中的 timer 服务集成一致。

---

## 3. Flink 可低成本移植特性总结表

| 优先级 | 特性 | 文件 | 成本 | 收益 | 风险 |
|--------|------|------|------|------|------|
| **P0** | TimerService PriorityQueue 重构 | `HeapInternalTimerService.java` | 半天 | 中等 | 低 |
| **P0** | ProcessingTimeTimer 实现 | `HeapInternalTimerService.java` | 半天 | 高 | 低（无 timer 依赖者不受影响） |
| **P1** | WindowAggregationOperator 退役 | `WindowAggregationOperator.java` | 1天 | 中（清理代码） | 中（需要迁移测试） |
| **P1** | Chainable 条件检查 | `JobGraphGenerator.java` | 1天 | 中 | 低 |
| **P1** | PendingCheckpoint 状态机增强 | `CheckpointCoordinator.java` / `PendingCheckpoint.java` | 1天 | 中（稳定性） | 低 |
| **P2** | 差量 Timer 快照 | `HeapInternalTimerService.java` | 1-2天 | 高（大规模 timer） | 中 |
| **P2** | RocksDB 状态后端 | 新文件 | 2-3天 | 高（生产化） | 中 |
| **P2** | MergingWindowSet 缓存 | `MergingWindowSet.java` | 1天 | 中 | 低 |
| **P3** | Kafka 连接器 | 新文件 | 1-2天 | 高 | 低 |
| **P3** | RPC 轻量级实现 | `IStreamCoordinatorRpcService.java` 实现 | 2天 | 高（分布式） | 中 |
| **P3** | 新 Source/Sink API | 新文件 | 2-3天 | 高 | 中 |

### 优先级说明

- **P0**: 1天以内，高风险收益比，无副作用，立即可以实施
- **P1**: 1-2天，中风险收益比，需注意兼容性
- **P2**: 2-3天，较高收益但需要更多验证
- **P3**: 3天以上，功能增强性需求

---

## 4. 综合评估

### nop-stream 相对 Flink 的优势

1. **声明式模型（XDSL）**: 这是 nop-stream 最核心的差异化竞争力。Flink 只能用编程方式（Java/Scala DataStream API, Table API, SQL）定义流处理任务。nop-stream 通过 `stream.xdef` 提供了可逆计算、组件注册表、声明式窗口策略等能力。

2. **分区策略与部署计划分离**: `PartitionedPlan` / `DeploymentPlan` 将执行时的路由策略和执行配置从图模型中分离，使得运行时可以动态调整分区策略。

3. **深度 Nop 平台集成**: 可以利用 `nop-batch`、`nop-dao`、`IoGuess`、`ICache` 等 nop 生态组件。

4. **可逆计算**: `x:extends` 继承 + Delta 定制意味着可以通过 delta 目录修改已有流处理作业的行为，无需修改原始定义。

5. **统一的组件注册表**: `coders` / `schemas` / `windowingStrategies` 通过稳定 ID 引用，便于跨应用重用。

### Flink 相对 nop-stream 的优势

1. **分布式调度**: 生产级分布式执行、资源管理和弹性伸缩。
2. **状态后端**: RocksDB 状态后端支持超大规模状态。
3. **连接器生态**: 30+ 连接器（Kafka / Pulsar / JDBC / HDFS / S3 / ES / Redis 等）。
4. **社区与成熟度**: Flink 已在数千家公司投入生产。
5. **Tiered State**: 分层存储（内存 → 磁盘 → 远程）。
6. **SQL / Table API**: 完整的关系代数查询优化器。
7. **多语言支持**: Python (PyFlink) / SQL / Java / Scala。

### 关键结论

nop-stream 在**架构设计上已完全对齐 Flink 的核心抽象**。Flink 绝大多数核心概念（StreamGraph → JobGraph、OperatorChain、CheckpointBarrier、Trigger/Evictor/WindowAssigner、TwoPhaseCommitSink、StateBackend、TimerService 等）都能在 nop-stream 中找到等价实现。

**最优先实施的三项**:

1. **TimerService ProcessingTimeTimer**: 填补当前空实现，开启`TumblingProcessingTimeWindows` / `SlidingProcessingTimeWindows` 等所有 processing time 窗口的功能。**半天**即可完成。

2. **HeapInternalTimerService 优化**: 将 TreeMap 触发改为更高效的 while + pollFirstEntry 模式，提升 watermark 推进时的 timer 触发性能。

3. **WindowAggregationOperator 退役**: 清理已 deprecated 的旧版窗口算子，降低维护成本。

这三个 P0 级别任务总计约 **1-2 天** 可实现，收益明确，风险极低。

---

## 附录 A: 关键源码对照表

| 概念 | Flink 路径 | nop-stream 路径 |
|------|-----------|----------------|
| StreamGraph | flink-runtime/.../graph/StreamGraph.java | core/.../graph/StreamGraph.java |
| JobGraph | flink-runtime/.../jobgraph/JobGraph.java | core/.../jobgraph/JobGraph.java |
| StreamConfig | flink-runtime/.../graph/StreamConfig.java | core/.../jobgraph/StreamConfig.java |
| CheckpointCoordinator | flink-runtime/.../checkpoint/CheckpointCoordinator.java | runtime/.../checkpoint/CheckpointCoordinator.java |
| InternalTimerService | flink-runtime/.../operators/InternalTimerServiceImpl.java | core/.../operators/HeapInternalTimerService.java |
| WindowOperator | flink-runtime/.../operators/windowing/WindowOperator.java | runtime/.../operators/windowing/WindowOperator.java |
| AbstractStreamOperator | flink-runtime/.../operators/AbstractStreamOperator.java | core/.../operators/AbstractStreamOperator.java |
| Trigger | flink-streaming-java/.../windowing/triggers/Trigger.java | core/.../windowing/triggers/Trigger.java |
| WatermarkStrategy | flink-core/.../eventtime/WatermarkStrategy.java | core/.../common/eventtime/WatermarkStrategy.java |
| OperatorChain | flink-runtime/.../graph/OperatorChain.java | core/.../jobgraph/OperatorChain.java |

## 附录 B: nop-stream 验证清单

执行上述 P0/P1 任务后，需验证：

```bash
# 图模型管线
mvn test -pl nop-stream/nop-stream-core -am -Dtest="TestStreamGraphGenerator,TestJobGraphGenerator,TestGraphExecutionPlan"

# Checkpoint
mvn test -pl nop-stream/nop-stream-core -am -Dtest="TestCheckpointBarrier,TestCheckpointConfig"
mvn test -pl nop-stream/nop-stream-runtime -am -Dtest="TestCheckpointCoordinator,TestE2ECheckpointAndRecovery"

# Window
mvn test -pl nop-stream/nop-stream-core -am -Dtest="TestWindowAggregationOperator*,TestWindowOperator*"
mvn test -pl nop-stream/nop-stream-runtime -am -Dtest="TestWindowOperator*,TestSessionWindow*"

# Timer
mvn test -pl nop-stream/nop-stream-core -am -Dtest="TestHeapInternalTimerService*,TestTimerServiceManager*"

# E2E
mvn test -pl nop-stream/nop-stream-core -am -Dtest="TestEndToEndPipeline,TestEventTimeWindowE2E"
mvn test -pl nop-stream/nop-stream-runtime -am -Dtest="TestE2ECheckpointAndRecovery,TestDistributedExactlyOnce"
```
