# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] WindowAggregationOperator merging window 路径（session windows）无专门单元测试

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java:256-365`
- **证据片段**:
  ```java
  private void processElementWithMerging(IN value, long timestamp, K key, Collection<W> newWindows) throws Exception {
      MergingWindowAssigner<Object, W> mergingAssigner = (MergingWindowAssigner<Object, W>) windowAssigner;
      // ... 80 lines of merge logic including trigger.clear(), triggerState migration,
      // accumulator merge via aggregationFunction.merge(), unregisterEventTimeTimersForWindow
  }
  ```
- **严重程度**: P0
- **现状**: 所有现有测试使用 TumblingEventTimeWindows。processElementWithMerging 路径完全未在算子级别测试。默认 merge() 抛 UnsupportedOperationException，无测试捕获。
- **风险**: Session window merging 是流引擎中最易出错的部分。accumulator merging、trigger state migration 或 timer cleanup 中的 bug 可导致静默数据丢失或状态损坏。
- **建议**: 添加 TestWindowAggregationOperatorMerging 测试类。
- **信心水平**: 确定
- **误报排除**: 验证所有 WindowAggregationOperator 测试文件——无 import MergingWindowAssigner 或 EventTimeSessionWindows。
- **复核状态**: 未复核

### [维度16-02] WindowAggregationFunction.merge() 从未被测试

- **文件**: `nop-stream-core/.../operators/WindowAggregationFunction.java:15-17`
- **严重程度**: P1
- **现状**: merge() 默认抛 UnsupportedOperationException。无测试调用此方法或提供自定义实现验证 merge 路径。
- **建议**: 添加测试验证自定义 merge() 行为和默认异常抛出。
- **信心水平**: 确定
- **误报排除**: grep 确认无测试调用 WindowAggregationFunction.merge。
- **复核状态**: 未复核

### [维度16-03] WindowOperator.mergeWindowContents accumulator 合并逻辑未测试

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:789-837`
- **严重程度**: P1
- **现状**: mergeWindowContents 处理三个分支（accumulator+accumulator、accumulator+raw、raw+raw），但测试使用 ToStringWindowFunction 存储原始值，触发错误路径而非成功合并路径。
- **建议**: 添加使用 accumulator-based InternalWindowFunction 的 session window 测试。
- **信心水平**: 很可能
- **误报排除**: 现有 session window 测试用 ToStringWindowFunction 存储最后原始值，不是 accumulator。
- **复核状态**: 未复核

### [维度16-04] CheckpointCoordinator EpochManifest 存储失败路径未测试

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:239-254`
- **严重程度**: P1
- **现状**: 现有 testStorageFailureNoCounterLeak 仅测试 storeCheckPoint 失败。EpochManifest 存储失败（checkpoint 数据已成功存储后）是不同的代码路径，未测试。
- **建议**: 添加 mock 测试：storeCheckPoint 成功但 storeEpochManifest 抛异常。
- **信心水平**: 很可能
- **误报排除**: 现有测试明确只测试 checkpoint storage failure。
- **复核状态**: 未复核

### [维度16-05] JobCoordinator EXPORT_SAVEPOINT 模式无测试

- **文件**: `nop-stream-runtime/.../coordinator/JobCoordinator.java:530-551`
- **严重程度**: P1
- **现状**: DRAIN/SUSPEND 测试仅验证 isRunning() 为 false。无 EXPORT_SAVEPOINT 测试（唯一作业继续运行的终止模式）。
- **建议**: 添加 EXPORT_SAVEPOINT 测试验证 isRunning() 为 true。
- **信心水平**: 确定
- **误报排除**: TestJobCoordinator 无 EXPORT_SAVEPOINT 相关测试方法。
- **复核状态**: 未复核

### [维度16-06] GraphModelCheckpointExecutor 缺少并发 checkpoint + 元素处理测试

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:59-97`
- **严重程度**: P1
- **现状**: barrier scheduler 在独立线程注入 barrier，task executor 在自己的线程处理元素。无测试验证并发行为。
- **建议**: 添加快速 barrier interval 的多算子管道测试。
- **信心水平**: 很可能
- **误报排除**: E2E 测试使用简单管道和慢 barrier interval，不压测并发路径。
- **复核状态**: 未复核

### [维度16-07] WindowOperator trigger accumulators snapshot/restore 无完整 round-trip 测试

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:309-335`
- **严重程度**: P1
- **现状**: TestTriggerAccumulatorsCheckpoint 通过测试工具测试，未通过 WindowOperator 的实际 snapshot/restore 方法。
- **建议**: 添加通过 WindowOperator snapshot/restore 的 trigger accumulator 测试。
- **信心水平**: 很可能
- **误报排除**: 现有测试通过 harness 而非 WindowOperator 本身。
- **复核状态**: 未复核

### [维度16-08] PendingCheckpoint 无并发 acknowledgeTask 测试

- **文件**: `nop-stream-runtime/.../checkpoint/PendingCheckpoint.java:113-129`
- **严重程度**: P2
- **现状**: acknowledgeTask 是 synchronized 但无测试验证多线程并发 ACK 行为。
- **建议**: 添加 N 线程并发 acknowledgeTask 测试。
- **信心水平**: 很可能
- **误报排除**: TestCheckpointConcurrencySafety 聚焦 coordinator 层级，非 PendingCheckpoint 内部线程安全。
- **复核状态**: 未复核

### [维度16-09] CheckpointPlanBuilder 多 subtask（parallelism > 1）路径未测试

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointPlanBuilder.java:95-143`
- **严重程度**: P2
- **现状**: 有两种代码路径：multi-subtask 和 legacy single-task。测试可能仅覆盖 single-task。
- **建议**: 添加 parallelism > 1 的测试。
- **信心水平**: 很可能
- **误报排除**: 大多数测试使用 single-parallelism 管道。
- **复核状态**: 未复核

### [维度16-10] JdbcCheckpointStorage 错误恢复和并发访问路径测试不足

- **文件**: `nop-stream-runtime/.../checkpoint/storage/JdbcCheckpointStorage.java:68-120`
- **严重程度**: P2
- **现状**: 647 行的 JDBC 存储仅有基础 CRUD 测试，缺少 DDL 失败、并发写入、损坏数据反序列化等路径测试。
- **建议**: 添加错误恢复路径测试。
- **信心水平**: 很可能
- **误报排除**: 现有测试仅覆盖 happy path。
- **复核状态**: 未复核

### [维度16-11] WindowAggregationOperator restoreState 版本不匹配和损坏数据路径无覆盖

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java:156-228`
- **严重程度**: P2
- **现状**: 仅测试 happy path。未测试版本不匹配、损坏 JSON、无效类名等错误路径。
- **建议**: 添加错误路径测试。
- **信心水平**: 确定
- **误报排除**: TestWindowAggregationOperatorSnapshotRestore 仅测试成功 round-trip。
- **复核状态**: 未复核

### [维度16-12] BarrierAligner 并发 barrier 处理未测试

- **文件**: `nop-stream-runtime/.../checkpoint/barrier/BarrierAligner.java:47-63`
- **严重程度**: P2
- **现状**: TestBarrierAligner 仅测试单线程场景。生产中多输入通道并发传递 barrier。
- **建议**: 添加 N 输入并发 processBarrier 测试。
- **信心水平**: 很可能
- **误报排除**: 测试中无 "Concurrent"/"Thread"/"Parallel" 命名的方法。
- **复核状态**: 未复核

### [维度16-13] TestWindowOperatorBasic 测试基础设施原语而非 WindowOperator 行为

- **文件**: `nop-stream-runtime/.../operators/windowing/TestWindowOperatorBasic.java:1-72`
- **严重程度**: P3
- **现状**: 类名误导——测试 TumblingEventTimeWindows 创建和 TimeWindow 属性，从不实例化 WindowOperator。
- **建议**: 重命名为 TestWindowingPrimitives 或添加实际 WindowOperator 基础测试。
- **信心水平**: 确定
- **误报排除**: 类的 Javadoc 承认 "does not directly test WindowOperator"。
- **复核状态**: 未复核

### [维度16-14] TestWindowOperatorCorrectness session window 测试使用 raw-value 而非 accumulator

- **文件**: `nop-stream-runtime/.../operators/windowing/TestWindowOperatorCorrectness.java:337-505`
- **严重程度**: P2
- **现状**: session window 测试使用存储原始值的函数，mergeWindowContents 的 accumulator 合并主路径从未被测试。
- **建议**: 添加使用 accumulator-based 函数的 session window 测试。
- **信心水平**: 很可能
- **误报排除**: 现有测试验证窗口分配和触发，但不验证 accumulator 内容合并。
- **复核状态**: 未复核
