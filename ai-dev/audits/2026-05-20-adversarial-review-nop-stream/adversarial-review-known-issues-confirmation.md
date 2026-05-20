# nop-stream 对抗性审查 — 补充：已知问题确认

> 审查日期：2026-05-20
> 目的：验证之前报告的 25 个已知问题是否仍然存在，将未修复的问题纳入本次审计记录
> 方法：逐一读取源文件相关行验证

---

## 状态总览

| 状态 | 数量 | 说明 |
|------|------|------|
| **已修复** | 2 | operator vs operators 双包消除；EvictingWindowOperator 删除 |
| **部分修复** | 5 | 有改善但未完全解决 |
| **未修复** | 12 | 仍然存在 |
| **跳过** | 6 | 在 nop-stream-cep 中 |

---

## 已修复（不再报告）

### K1: operator vs operators 包双重定义 — 已修复

`operator/`（单数）包已消除，只保留 `operators/`（复数）包。

### K5: EvictingWindowOperator — 已修复（类已删除）

文件已不存在。CepWindowOperator + CepWindowAssigner + CepWindowTrigger 同样已删除。

---

## 部分修复（仍需关注）

### K8: CheckpointCoordinator 硬编码 tasksToAcknowledge — 部分修复

**之前：** 构造函数硬编码 `tasksToAcknowledge.add(1L); tasksToAcknowledge.add(2L);`
**现在：** 构造函数初始化为空集合，提供了 `registerTask()`/`unregisterTask()` 方法。
**残留问题：** 如果调用者不手动注册 task，`getTasksToAcknowledge()` 返回空集，所有 checkpoint 都会被跳过。当前无自动注册机制。
**文件：** `CheckpointCoordinator.java`
**标注：** 设计原型，未接入执行路径

### K9: TimestampsAndWatermarksOperator — 部分修复

**之前：** `super.open()` 注释掉 + `watermarkInterval = 0`
**现在：** `super.open()` 已恢复；`watermarkInterval` 从 0 改为硬编码 200ms
**残留问题：** 200ms 不可配置，高速/低速场景无法调优。本次 Round 2 也发现了此问题（N33）。
**文件：** `TimestampsAndWatermarksOperator.java` L75

### K12: BarrierAligner 低效轮询 — 部分修复

**之前：** `Thread.sleep(10)` 轮询
**现在：** 改为 `Condition.awaitNanos()` 模式
**残留问题：** `findCompletedCheckpointId()` 仍 O(N*M) 全量遍历，无生产引用。

### K13: LocalFileCheckpointStorage — 部分修复

**之前：** 手动拼接 JSON + 多处 `catch (Exception ignored)` 无日志
**现在：** 序列化改用 `JsonTool.serialize()`，反序列化改为显式字段提取
**残留问题：** 仍有 2 处 `catch (Exception ignored)`（`ensureDirectoryExists`、`deleteIfExists`），文件系统错误时静默失败。

### K11: TimerService key=null — 部分修复

`SimpleInternalTimerService` 独立文件已删除，但其逻辑作为内部类合并到 `WindowOperatorTimerService`。timer key 仍为 null（L54/61）。

---

## 未修复（仍然存在，需报告）

### K4: WindowedStreamImpl 核心 API 不可用

**文件：** `nop-stream-core/.../WindowedStreamImpl.java`
**现状：** `apply()`, `aggregate()`, `reduce()` 全部抛 `UnsupportedOperationException`，注释说"requires nop-stream-runtime module's WindowOperator"。
**信心水平：** 确定

### K6: WindowOperator 大量 state 管理代码注释残留

**文件：** `nop-stream-runtime/.../WindowOperator.java`
**现状：** 改为使用 `windowContentsState` (MapState) 的简化实现，但大量原始 state 管理代码仍被注释掉（lines 100, 145-151, 200-203, 209, 214, 250-254, 404-405, 503, 506, 561, 564, 603-611, 620-631, 849-861, 866-878, 881-903, 909-916, 937-940, 1002-1078, 1212-1215）。约 200+ 行注释代码残留。
**信心水平：** 确定

### K7: JobGraphGenerator 链节点映射 bug

**文件：** `nop-stream-core/.../JobGraphGenerator.java`
**现状：** 代码头部标注"设计原型"。`buildNodeToVertexMap()` 只将链头映射到 JobVertex，链内后续节点丢失。被 `executeWithGraphModel()` 路径使用，但主流 `execute()` 绕过。
**信心水平：** 确定

### K10: ChainingOutput side output 静默丢弃

**文件：** `nop-stream-core/.../operators/ChainingOutput.java` L65-67
**现状：** `collect(OutputTag<X>, StreamRecord<X>)` 方法体为空，注释"Side outputs not supported in simplified execution"。side output 记录被静默丢弃，无日志、无异常。
**信心水平：** 确定

### K14: JdbcCheckpointStorage MySQL 方言绑定

**文件：** `nop-stream-runtime/.../JdbcCheckpointStorage.java`
**现状：** `ensureTableExists()` 仍使用 `BIGINT AUTO_INCREMENT`、`TIMESTAMP DEFAULT CURRENT_TIMESTAMP`、`INDEX idx_xxx` 等 MySQL 特定 DDL。`loadSavepoint()` 硬编码 `getLatestCheckpoint(1L, 1)`。无生产代码引用。
**信心水平：** 确定

### K15: WindowOperator window.toString() 作为 namespace

**文件：** `nop-stream-runtime/.../WindowOperator.java` L834-836
**现状：** `windowNamespace()` 仍使用 `window.toString()` 作为 MapState key。`TimeWindow.toString()` 产生 `[10,20)` 格式，在边界相同的窗口间可能碰撞。
**连锁效应：** 与本次 Round 1 发现 N12（snapshotState 未按 key 分区）叠加——namespace 碰撞 + checkpoint 恢复不分区 = 数据错乱风险更高。
**信心水平：** 确定

### K17: TimerService 接口零引用 — 死代码

**文件：** `nop-stream-core/.../time/TimerService.java`
**现状：** 84 行完整接口（currentProcessingTime, currentWatermark, register*/delete* 6 个方法），**整个 nop-stream 无任何 import 引用**。与 `operators/InternalTimerService` 功能重叠。完全是死代码。
**信心水平：** 确定

### K18: 双执行模型不统一

**文件：** `nop-stream-core/.../StreamExecutionEnvironment.java`
**现状：** `execute()` → `executePipeline()`（chain push）vs `executeWithGraphModel()` → StreamGraph → JobGraph → TaskExecutor。两条路径的 DAG 解释逻辑不同（本次 Round 2 N36 也发现）。用户无法选择执行模式。
**信心水平：** 确定

### K19: 4 个空壳模块

**文件：** nop-stream-api, nop-stream-checkpoint, nop-stream-flink, nop-stream-flow 的 pom.xml
**现状：** 全部没有 src 目录，仅有 pom.xml 坐标声明。
**信心水平：** 确定

### K20: Configuration 接口为空且无实现

**文件：** `nop-stream-core/.../Configuration.java`
**现状：** 空接口，无方法、无字段、无实现类。NFA 的 `open(RuntimeContext, Configuration)` 接受此空接口，配置能力完全缺失。
**信心水平：** 确定

### K23: PriorityQueue.removeIf O(n) 操作

**文件：** `nop-stream-runtime/.../WindowOperatorTimerService.java` L68-69, 72-73
**现状：** `deleteEventTimeTimer()`/`deleteProcessingTimeTimer()` 仍使用 `PriorityQueue.removeIf()` 做 O(n) 扫描。
**信心水平：** 确定

### K24: 大量零引用/死代码

**现状确认（core 模块）：**
- 未使用的 Trigger：ProcessingTimeoutTrigger, ContinuousEventTimeTrigger, ContinuousProcessingTimeTrigger, DeltaTrigger
- 未使用的 Evictor：TimeEvictor, DeltaEvictor
- 未使用的 Accumulator：AverageAccumulator, DoubleMinimum, LongMaximum, IntMinimum, IntMaximum, DoubleMaximum, ListAccumulator（614 行）
- 未使用的 Function 接口：TwoPhaseCommitSinkFunction, CoMapFunction, CheckpointedSourceFunction
- `time.TimerService` 零引用（84 行）
- `Configuration` 零实现

**现状确认（runtime 模块）：**
- `BarrierAligner` 无生产引用
- `JdbcCheckpointStorage` 无生产引用
- `HeapInternalTimerService` — processing time timer 未实现（registerProcessingTimeTimer 空方法）
- `time.TimerService` 零引用

**信心水平：** 确定

---

## 与本次新发现的连锁效应

本次新发现的多个问题与已知未修复问题形成连锁效应：

1. **窗口聚合正确性链**（最高优先级）：
   - K15（window.toString namespace 碰撞）+ N1（addWindowElement 类型腐蚀）+ N17（累加器不重置）+ N12（snapshotState 未分区）→ 窗口聚合从 namespace 设计到元素添加到状态管理到 checkpoint 全链路都有问题

2. **Event-time 不工作链**：
   - K9（watermarkInterval 硬编码 200ms）+ N19（execute() 不处理 TimestampsAndWatermarksTransformation）→ fast path 中 event-time 完全不工作

3. **Side output 数据丢失**：
   - K10（ChainingOutput 丢弃）→ 任何使用 side output 的窗口操作（如迟到数据输出）数据静默丢失

---

## 全部问题优先级重排（已知 + 新发现合并）

### P0：运行时数据正确性问题

| # | 问题 | 来源 |
|---|------|------|
| N1 | WindowOperator.addWindowElement 破坏 SimpleAccumulator 类型 | Round 1 |
| N17 | MemoryInternalAppendingState.add() 累加器不重置 | Round 2 |
| N3 | MergingWindowSet.persist() 空操作 | Round 1 |
| N19 | execute() 不处理 TimestampsAndWatermarksTransformation | Round 2 |
| N2 | mergeWindowContents 静默吞 ClassCastException | Round 1 |
| N12 | WindowOperator.snapshotState 未按 key 分区 | Round 1 |

### P1：API 契约违背 / 功能缺失

| # | 问题 | 来源 |
|---|------|------|
| N22 | ValueStateDescriptor 丢弃 typeInfo 参数 | Round 2 |
| K10 | ChainingOutput side output 静默丢弃 | 已知未修复 |
| K4 | WindowedStreamImpl 核心 API 不可用 | 已知未修复 |
| N23 | StreamSourceOperator 正常完成后调用 cancel() | Round 2 |
| N29 | KeySelectorPartitioner null key NPE + MIN_VALUE 负数 | Round 2 |
| K6 | WindowOperator 大量注释残留 (~200 行) | 已知未修复 |

### P2：设计缺陷 / 资源管理

| # | 问题 | 来源 |
|---|------|------|
| K18 | 双执行模型不统一 | 已知未修复 |
| K14 | JdbcCheckpointStorage MySQL 方言绑定 | 已知未修复 |
| K15 | WindowOperator window.toString() namespace | 已知未修复 |
| K11 | TimerService key=null | 已知部分修复 |
| N9 | BatchLoaderSourceFunction loader 资源未关闭 | Round 1 |
| N10 | DebeziumCdcSourceFunction Thread.sleep 轮询 | Round 1 |
| N20 | checkpointExecutorFactory static 全局字段 | Round 2 |
| N28 | SimpleStreamOperatorFactory 返回同一对象 | Round 2 |

### P3：代码质量 / 示例问题

| # | 问题 | 来源 |
|---|------|------|
| K17 | TimerService 接口零引用 | 已知未修复 |
| K20 | Configuration 接口为空 | 已知未修复 |
| K23 | PriorityQueue.removeIf O(n) | 已知未修复 |
| K24 | 大量死代码/零引用文件 | 已知未修复 |
| K19 | 4 个空壳模块 | 已知未修复 |
| K7 | JobGraphGenerator 链节点映射（设计原型） | 已知未修复 |
| N4 | GeographicAnomalyPattern 条件恒真 | Round 1 |
| N5 | MockTransactionGenerator 事件类型不匹配 | Round 1 |
| N6 | UnusualAmountPattern 硬编码 + UserTransactionHistory 未使用 | Round 1 |
| N14 | RapidTransactionPattern 无同一用户检查 | Round 1 |
| N8 | WindowOperator.getSimpleAccumulator 返回 null | Round 1 |
| N21 | DataStreamImpl.map()/flatMap() 类型信息丢失 | Round 2 |
| N25 | MemoryMapState 丢弃 descriptor | Round 2 |
| N27 | WindowedStreamImpl 传入 null WindowAssignerContext | Round 2 |
| N34 | UnknownTypeInformation 未实现 Serializable | Round 2 |
| N40 | DataStreamImpl 声称 Serializable 但不可序列化 | Round 2 |
