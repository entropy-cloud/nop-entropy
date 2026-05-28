# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] MemoryKeyedStateBackend.java -- 7 个内部状态类可拆出

- **文件**: `nop-stream-core/.../common/state/backend/memory/MemoryKeyedStateBackend.java` (1254 行)
- **证据片段**:
  ```java
  // L68: public class MemoryKeyedStateBackend<K> implements IInternalStateBackend<K>, Serializable {
  // L713-1254: 7个private static内部状态类 + 2个辅助类
  private static class MemoryListState<T> ... { }
  private static class MemoryValueState<T> ... { }
  private static class MemoryMapState<UK, UV> ... { }
  private static class MemoryReducingState<T> ... { }
  private static class MemoryAggregatingState<IN,ACC,OUT> ... { }
  private static class MemoryInternalAppendingState<K,N,IN,ACC> ... { }
  private static class MemoryInternalListState<K,N,T> ... { }
  ```
- **严重程度**: P2
- **现状**: 文件包含 3 个职责区块：(1)状态后端核心逻辑约 220 行 (2)序列化/反序列化约 423 行 (3)内部状态实现类约 542 行。7 个内部类各自独立实现标准接口，不依赖外部后端的内部细节。
- **风险**: 文件大但职责集中于"内存状态后端"。维护时需要滚动大量代码。
- **建议**: 低优先级。可将 7 个内部状态类提取为同包下的独立文件（如 MemoryListState.java），预计缩减至约 500 行。
- **误报排除**: 不构成 P0/P1。1254 行在状态后端实现中是常见体量。
- **复核状态**: 未复核

### [维度02-02] GraphModelCheckpointExecutor.java -- 上帝方法类，4 个 execute 方法大量重复

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java` (805 行)
- **证据片段**:
  ```java
  // L57: public static StreamExecutionResult executeWithCheckpoint(JobGraph, ...)  // 入口1
  // L102: public static StreamExecutionResult executeWithCheckpoint(StreamModel, ...)  // 入口2
  // L173: public static String triggerSavepoint(...)  // 入口3
  // L220: public static StreamExecutionResult executeWithSavepoint(...)  // 入口4
  // 4个方法都包含几乎相同的编排流程
  ```
- **严重程度**: P2
- **现状**: 全 static 方法的工具类，承担 6+ 种职责。4 个 execute 方法之间有大量重复代码。
- **风险**: 每新增一种执行模式都需要复制整段流程。
- **建议**: 提取公共编排流程为 CheckpointExecutionTemplate（模板方法）。restore 逻辑可提取为独立的 CheckpointRestorer 类。
- **误报排除**: 不构成 P0/P1。当前代码可正常工作。
- **复核状态**: 未复核

### [维度02-03] core 和 runtime 之间存在两个功能重叠的窗口算子

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java` (658 行) vs `nop-stream-runtime/.../operators/windowing/WindowOperator.java` (1088 行)
- **证据片段**:
  ```java
  // WindowAggregationOperator (core) - 自包含，不依赖 IKeyedStateBackend
  public class WindowAggregationOperator<IN, ACC, OUT, K, W extends Window>
          extends AbstractStreamOperator<OUT> implements OneInputStreamOperator<IN, OUT>

  // WindowOperator (runtime) - 依赖 IKeyedStateBackend，支持 MergingWindow
  public class WindowOperator<K, IN, ACC, OUT, W extends Window>
          extends AbstractUdfStreamOperator<OUT, ...> implements OneInputStreamOperator<IN, OUT>
  ```
- **严重程度**: P2
- **现状**: 两个算子都处理窗口分配+触发+聚合。实现策略不同：core 的自包含（JSON 序列化），runtime 的依赖状态后端。
- **风险**: 使用者可能困惑该使用哪个算子。长期维护两个功能重叠的窗口算子增加理解成本。
- **建议**: 在 WindowAggregationOperator 的 Javadoc 中明确其定位（轻量级，不依赖状态后端）。如 WindowOperator 已完全覆盖其功能，考虑标记为 @Deprecated。
- **误报排除**: 两者实现策略确实不同，可能有共存的技术理由。
- **复核状态**: 未复核

### [维度02-04] GraphModelCheckpointExecutor restore 逻辑与 CheckpointCoordinator 职责重叠

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:573-734` vs `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:269-278`
- **证据片段**:
  ```java
  // GraphModelCheckpointExecutor.java L573
  private static void restoreFromCheckpoint(GraphExecutionPlan execPlan,
          CheckpointCoordinator coordinator, CheckpointPlan checkpointPlan, ...) {
      // ~90行 restore 逻辑
  // CheckpointCoordinator.java L269
  public CompletedCheckpoint restoreFromCheckpoint() throws Exception {
      // 仅从 storage 读取 CompletedCheckpoint，实际 restore 全在 GraphModelCheckpointExecutor
  ```
- **严重程度**: P2
- **现状**: CheckpointCoordinator 的 restoreFromCheckpoint 只负责从存储读取，实际状态恢复逻辑全在 GraphModelCheckpointExecutor 中。
- **风险**: checkpoint 的"触发-收集-恢复"生命周期被割裂在两个类中。
- **建议**: 将 restore 相关方法提取为独立的 CheckpointRestorer 类。
- **误报排除**: 当前功能正确，不影响运行时行为。
- **复核状态**: 未复核

## 信息性确认

- NFACompiler.java (1090 行): 算法复杂度驱动的文件体量，职责单一（Pattern → NFA 编译），合理
- NFA.java (969 行): 核心状态机算法，合理体量
- _gen 文件管理规范，无手写修改痕迹
- core/runtime checkpoint 分层合理（core=契约，runtime=实现）
- fraud-example 设计自洽（仅演示 CEP，不需 runtime）
