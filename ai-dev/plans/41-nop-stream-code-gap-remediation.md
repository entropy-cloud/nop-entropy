# 41 nop-stream 代码缺口修复

> Plan Status: completed
> Last Reviewed: 2026-05-23
> Source: nop-stream 源码 vs 设计文档全量对照审查（2026-05-23），经两轮对抗性审查后确认 10 个合法代码缺口
> Related: Plan 40 (design-docs-gap-fill), Plan 37 (round3-critical-fixes)

## Purpose

将 nop-stream 源码中已确认的代码缺口（Bug、缺失实现、空壳代码）按优先级分阶段修复，使源码行为与设计文档契约一致。

## Current Baseline

- Plan 26-29 已完成执行路径统一、checkpoint 集成、多链管线数据交换、savepoint 实现
- Plan 40 已完成设计文档缺口填补，13 个设计文档与源码结构一致
- 经两轮对抗性审查，排除了多个误报并确认以下事实：
  - `WindowAggregationOperator.processElement()` 已正确实现累加器逻辑（createAccumulator → add → put），**不是 Bug**
  - `WindowOperator`（runtime 模块）无生产调用路径——`WindowedStreamImpl` 使用的是 `WindowAggregationOperator`（core 模块）
  - `MergingWindowSet.persist()` 已有完整实现（line 100-110）
  - `WindowedStreamImpl.apply/aggregate/reduce` 已有完整实现（line 116-140）
  - `HeapInternalTimerService` 仅在测试代码中使用，运行时使用 `WindowOperatorTimerService`（已有完整处理时间实现）

## Goals

- 修复已确认的数据正确性 Bug（RecordWriter 分区、checkpoint 存储）
- 实现 Processing Time Timer（WindowAggregationOperator 的缺失功能）
- 实现 AggregatingState API
- 清理空壳/桩代码

## Non-Goals

- 不新增或修改设计文档
- 不处理 Deferred 设计限制
- 不修改无生产调用路径的 WindowOperator（runtime 模块）

## Scope

### In Scope

- 10 个已确认代码缺口

### Out Of Scope

- 设计文档修改
- WindowOperator（runtime）的累加器/指标修复（无生产调用路径）
- EvictingWindowOperator 死代码清理（无调用路径）
- Barrier 线程安全、JdbcCheckpointStorage 多数据库、watermarkInterval、HeapInternalTimerService

## Execution Plan

### Phase 1 - RecordWriter 分区 Bug 修复

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/RecordWriter.java`, `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/JobEdge.java`, `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/JobGraphGenerator.java`, `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java`

- Item Types: `Fix`

修复导致 keyBy 后所有记录发送到 partition 0 的两个 Bug：

- [x] **Fix #1**: `GraphExecutionPlan.build()` 传递 null partitioner 给 RecordWriter — 根因：JobEdge 不携带 partitioner 信息（只有 ResultPartitionType 枚举）。修复：`JobEdge` 新增 `IPartitioner partitioner` 字段；`JobGraphGenerator` 构建 JobEdge 时从 `StreamEdge.getPartitioner()` 提取并设置；`GraphExecutionPlan.build()` 从 JobEdge 读取传给 RecordWriter。传递链路：`StreamEdge.partitioner → JobEdge.partitioner → RecordWriter(partitions, partitioner)`。

- [x] **Fix #2**: `RecordWriter.emitElement()` 即使 partitioner 不为 null 仍硬编码 channel=0（line 117-119）。修复：当 partitioner 不为 null 时，对非 StreamRecord 类型的 element（如 WatermarkStatus）应广播或保持 channel=0（因为这些元素没有可提取的 key），对 StreamRecord 类型应使用 partitioner 分区。但因 emitElement 只处理非 record 类型的 StreamElement（Watermark、WatermarkStatus、CheckpointBarrier），这些应广播到所有分区而非用 partitioner 选择单分区。

Exit Criteria:

- [x] `JobEdge` 新增 `IPartitioner partitioner` 字段及 getter
- [x] `JobGraphGenerator` 构建 JobEdge 时从 StreamEdge 提取 partitioner 并设置
- [x] `GraphExecutionPlan.build()` 从 JobEdge 读取 partitioner 传给 RecordWriter（不再传 null）
- [x] `RecordWriter.emitElement()` 当 partitioner 不为 null 时广播到所有分区（与非 record 类型语义一致）
- [x] 单测验证：keyBy 后的流经 RecordWriter 发送时，记录按 key hash 分布到不同分区
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 通过
- [x] **接线验证**：`GraphExecutionPlan.build()` → `RecordWriter` → `IPartitioner.partition()` 调用链在运行时连通
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Checkpoint 存储 Bug 修复

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/`

- Item Types: `Fix`

- [x] **Fix #3**: `JdbcCheckpointStorage.loadSavepoint()` 硬编码 `getLatestCheckpoint("1", "1")`（line 207）— 忽略 savepointPath 参数。修复：在 `storeSavepoint()` 中将 savepointPath 写入 checkpoint 记录的元数据字段（或单独的 savepoint 表），在 `loadSavepoint()` 中按 savepointPath 查询而非硬编码 jobId/pipelineId。

- [x] **Fix #4**: `LocalFileCheckpointStorage` 吞异常（line 368-380）— `ensureDirectoryExists` 和 `deleteIfExists` 的 `catch (Exception ignored) {}` 静默忽略 IO 错误。修复：改为记录 WARN 级别日志，影响数据完整性的操作（storeCheckPoint 后的 cleanup）抛出 `NopException`。

Exit Criteria:

- [x] `loadSavepoint()` 根据 savepointPath 参数查询，不依赖硬编码值
- [x] `storeSavepoint()` 将 savepointPath 持久化到数据库
- [x] `LocalFileCheckpointStorage` IO 异常至少记录 WARN 日志，影响数据完整性的操作抛出 NopException
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 通过
- [x] **端到端验证**：savepoint 存储→加载→恢复路径完整跑通
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - WindowAggregationOperator Processing Time Timer

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java`

- Item Types: `Fix`

实现 WindowAggregationOperator 中缺失的 processing time timer 功能：

- [x] **Fix #5**: `WindowAggregationOperator` 缺少 `processingTimeTimers` 数据结构 — 当前只有 `eventTimeTimers`（line 29），需要新增对称的 `TreeMap<Long, Set<WindowKey<K, W>>> processingTimeTimers` 和对应的 `processingTimeTimerLookup`。

- [x] **Fix #6**: `WindowAggregationOperator` 缺少 processing time 触发机制 — 新增 `advanceProcessingTime(long timestamp)` 方法（与 `processWatermark` 对称），遍历 processingTimeTimers 触发到期定时器。驱动方式：在 `processElement` 结束时基于 `System.currentTimeMillis()` 检查是否有到期定时器（简化方案，无需外部调度器）。

- [x] **Fix #7**: `TriggerContextImpl.registerProcessingTimeTimer` 和 `deleteProcessingTimeTimer` 为空（line 316-321）— 委托给外层 WindowAggregationOperator 新增的 processingTimeTimers 数据结构。

Exit Criteria:

- [x] `WindowAggregationOperator` 新增 `processingTimeTimers` 和 `processingTimeTimerLookup` 字段
- [x] `TriggerContextImpl.registerProcessingTimeTimer()` 将定时器注册到 processingTimeTimers
- [x] `TriggerContextImpl.deleteProcessingTimeTimer()` 从 processingTimeTimers 删除
- [x] `advanceProcessingTime()` 触发到期定时器，调用 `trigger.onProcessingTime()`
- [x] 单测验证：注册处理时间定时器 → advanceProcessingTime → Trigger.onProcessingTime 被调用
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 通过
- [x] **无静默跳过**：两个方法不再有空方法体
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - AggregatingState 缺失实现

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java`

- Item Types: `Fix`

- [x] **Fix #8**: `getAggregatingState()` 抛 UnsupportedOperationException（line 168-172）。修复：参照已有的 `MemoryInternalAppendingState` 创建 `MemoryAggregatingState<IN, ACC, OUT>` 内部类。`add(IN)` 调用 `aggregateFunction.add(accumulator, IN)`，`get()` 调用 `aggregateFunction.getResult(accumulator)`。状态存储复用 HashMap。

Exit Criteria:

- [x] `getAggregatingState()` 返回可用 AggregatingState 实例
- [x] `add(element)` 正确累加，`get()` 返回聚合结果，单测验证
- [x] 不再抛 UnsupportedOperationException
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 空壳代码清理

Status: completed
Targets: `nop-stream/nop-stream-runtime/`, `nop-stream/nop-stream-core/`

- Item Types: `Fix`

- [x] **Fix #9**: `PendingCheckpoint.acknowledgePrecedingCheckpoint(long)` 为空方法体 — 改为 `throw UnsupportedOperationException("not yet implemented")`

- [x] **Fix #10**: `CollectionReplayableSource.cancel()` 为空方法体 — 添加 `volatile boolean running = true` 标志位，`run()` 循环加入 `!running` 退出条件

Exit Criteria:

- [x] `PendingCheckpoint.acknowledgePrecedingCheckpoint()` 不再是空方法体
- [x] `CollectionReplayableSource.cancel()` 设置 running=false，run() 循环检查并退出
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] **无静默跳过**：无新增空方法体
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 10 个 Fix 已通过单测验证
- [x] `./mvnw test -pl nop-stream -am` 全通过
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] **Anti-Hollow Check**：(a) RecordWriter partitioner 在运行时被调用且返回非零分区值，(b) savepoint 存储→加载→恢复路径完整跑通，(c) processing time timer 注册后确实被触发，(d) AggregatingState add/get 正确工作
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `ai-dev/logs/` 收口记录已更新

## Deferred But Adjudicated

### #D1 WindowOperator（runtime）累加器 Bug

- Classification: `watch-only residual`
- Why Not Blocking Closure: WindowOperator 无生产调用路径。WindowedStreamImpl 使用 WindowAggregationOperator（core），其 processElement 已正确实现累加器逻辑。WindowOperator 只在测试代码中直接实例化。
- Successor Required: yes
- Successor Path: 随 ProcessWindowFunction 支持或 runtime 算子完善统一处理

### #D2 WindowOperator numLateRecordsDropped 为 null

- Classification: `watch-only residual`
- Why Not Blocking Closure: 同 #D1，WindowOperator 无生产调用路径
- Successor Required: yes
- Successor Path: 同 #D1

### #D3 WindowOperator 空内部类

- Classification: `watch-only residual`
- Why Not Blocking Closure: 同 #D1
- Successor Required: yes
- Successor Path: 同 #D1

### #D4 HeapInternalTimerService processing time 空方法

- Classification: `watch-only residual`
- Why Not Blocking Closure: 仅在测试代码中使用。运行时 timer service 是 WindowOperatorTimerService（已有完整实现）
- Successor Required: no
- Successor Path: 可作为测试代码质量改善项

### #D5 Barrier 注入线程安全

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 已文档化为已知限制，属架构级变更
- Successor Required: yes
- Successor Path: 需独立架构计划

### #D6 JdbcCheckpointStorage 仅支持 MySQL

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需迁移到 IJdbcTemplate，属基础设施适配层变更
- Successor Required: yes
- Successor Path: 需独立计划

### #D7 watermarkInterval 硬编码为 0

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需调度机制支持，已文档化
- Successor Required: yes
- Successor Path: 需 Plan 28 后续计划

### #D8 EvictingWindowOperator 死代码

- Classification: `watch-only residual`
- Why Not Blocking Closure: 无调用路径
- Successor Required: yes
- Successor Path: 随窗口算子完善统一处理

## Non-Blocking Follow-ups

- WindowOperator（runtime）累加器/指标/内部类修复（随 ProcessWindowFunction 支持）
- HeapInternalTimerService 处理时间方法实现（测试代码质量）
- EvictingWindowOperator 实现或移除
- watermarkInterval 配置化
- Barrier 注入重构
- JdbcCheckpointStorage 迁移到 IJdbcTemplate

## Closure

Status Note: 所有 10 个 Fix 已实现并通过单测验证。独立子 agent closure audit 通过，无 anti-hollow 问题。8 项 deferred 正确分类为 out-of-scope/watch-only。

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (task ses_1af597ca7ffe1K8CKXisJznaWV)
- Evidence: 所有 10 个 Fix 逐项验证 PASS，无空方法体/静默跳过/no-op，partitioner 接线链完整，savepoint 存储→加载路径完整，processing time timer 注册→触发链完整，AggregatingState add/get 使用 AggregateFunction 方法

Follow-up:

- #D1-#D8 的 8 项设计限制/残余需独立后续计划
