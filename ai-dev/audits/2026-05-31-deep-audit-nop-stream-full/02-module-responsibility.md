# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] WindowOperator.onEventTime/onProcessingTime 近乎复制粘贴（仅 3 行差异，110 行冗余）

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:483-537,540-594`
- **证据片段**:
  ```java
  // onEventTime (483-494)
  public void onEventTime(InternalTimer<K, W> timer) throws Exception {
      triggerContext.key = timer.getKey();
      triggerContext.window = timer.getNamespace();
      MergingWindowSet<W> mergingWindows;
      if (windowAssigner instanceof MergingWindowAssigner) {
          mergingWindows = getMergingWindowSet();
          W stateWindow = mergingWindows.getStateWindow(triggerContext.window);
          if (stateWindow == null) { return; } else {}
      } else {
          mergingWindows = null;
      }
  // onProcessingTime (540-551) — 字符级相同
  ```
- **严重程度**: P3
- **现状**: 两个方法共 110 行，仅 3 行不同（方法名、调用目标、清理条件）。源自 Flink 移植。
- **风险**: 维护成本——修改一处需同步修改另一处。
- **建议**: 抽取 `processTimerEvent(InternalTimer, boolean isEventTime)` 消除冗余。
- **信心水平**: 确定
- **误报排除**: 不是误报——110 行中 107 行完全相同是事实。
- **复核状态**: 未复核

### [维度02-02] NFACompiler 内含 912 行内部类 NFAFactoryCompiler

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:142-1054`
- **证据片段**:
  ```java
  // line 142
  static class NFAFactoryCompiler<T> {
      private final NFAStateNameHandler stateNameHandler = new NFAStateNameHandler();
      private final Map<String, State<T>> stopStates = new HashMap<>();
      private final List<State<T>> states = new ArrayList<>();
      // ... 11 fields, 20+ methods, 912 lines
  ```
- **严重程度**: P3
- **现状**: 内部类占文件 83%，拥有 11 个字段、20+ 个方法，完全可以独立为文件。
- **风险**: 可读性和维护性降低。
- **建议**: 提取为独立的 `NFAFactoryCompiler.java`。
- **信心水平**: 确定
- **误报排除**: 不是误报——NFAStateNameHandler 已独立为同包文件，说明项目有此实践。
- **复核状态**: 未复核

### [维度02-03] WindowAggregationOperator 放在 core 但包含完整运行时逻辑，与 runtime 中 WindowOperator 功能重叠

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java:29-31`（834 行） vs `nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:91-93`（1099 行）
- **证据片段**:
  ```java
  // core 中的 WindowAggregationOperator — 834 行完整算子
  public class WindowAggregationOperator<IN, ACC, OUT, K, W extends Window>
          extends AbstractStreamOperator<OUT>
          implements OneInputStreamOperator<IN, OUT>, KeyContext {
  ```
  ```java
  // runtime 中的 WindowOperator — 1099 行完整算子
  public class WindowOperator<K, IN, ACC, OUT, W extends Window>
          extends AbstractUdfStreamOperator<OUT, InternalWindowFunction<ACC, OUT, K, W>>
          implements OneInputStreamOperator<IN, OUT>, Triggerable<K, W> {
  ```
- **严重程度**: P2
- **现状**: WindowAggregationOperator 包含完整运行时逻辑（状态序列化 131-228 行、水位线处理 384-426 行、Timer 管理 428-462 行），不是抽象基类。core 模块定位应为"抽象与接口"，但此文件是可独立运行的算子。
- **风险**: core 与 runtime 职责边界模糊，两个功能重叠的窗口算子增加维护成本和理解难度。
- **建议**: 将 WindowAggregationOperator 移至 runtime，或在 core 中只保留抽象层。
- **信心水平**: 很可能
- **误报排除**: 不是误报——设计文档明确 core 定义模型和编译结果，runtime 负责执行。WindowAggregationOperator 包含序列化、Timer 等运行时逻辑，违反了这一边界。
- **复核状态**: 未复核

### [维度02-04] GraphModelCheckpointExecutor 4 个公共入口方法共享相同编排模式（约 60-70% 代码重复）

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:59-97,104-156,175-220,222-261`
- **证据片段**:
  ```java
  // 第一个重载 (67-96)
  GraphExecutionPlan execPlan = buildExecutionPlan(jobGraph, barrierAlignment);
  CheckpointCoordinator coordinator = createCoordinator(jobId, pipelineId, idCounter, storage, checkpointConfig);
  List<StreamTaskInvokable> allInvokables = registerTasksAndTrackers(execPlan, checkpointPlan, coordinator);
  ScheduledExecutorService barrierScheduler = startBarrierScheduler(allInvokables, coordinator, checkpointConfig, jobId);
  restoreFromCheckpoint(execPlan, coordinator, checkpointPlan, null);
  // 第二个重载 (123-155) — 核心编排步骤逐行相同
  ```
- **严重程度**: P3
- **现状**: 4 个方法中约 60-70% 是公共编排逻辑的复制粘贴。
- **风险**: 修改一处逻辑需同步修改所有 4 处。
- **建议**: 抽取 `executeCommon(...)` 模板方法，将差异参数化。
- **信心水平**: 确定
- **误报排除**: 不是误报——这是本项目原创实现（非 Flink 移植），更应及时优化。
- **复核状态**: 未复核

### [维度02-05] NFA.java 内部类 ConditionContext 和 EventWrapper 可提取为独立文件

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:534-572,885-949`
- **严重程度**: P3
- **现状**: ConditionContext 65 行、EventWrapper 39 行，均可独立为包级类。
- **风险**: 信息级别，976 行在算法密集型类中可接受。
- **建议**: 可选提取，不强制。
- **信心水平**: 确定
- **误报排除**: 低优先级，记录在案。
- **复核状态**: 未复核

## 合规确认

- 4 个 placeholder 模块（api/checkpoint/flink/flow）确认清洁
- _gen 目录无手写代码
- 模块依赖关系清洁，core 不引用 runtime/cep
- Pattern.java (693 行) 和 CepOperator.java (609 行) 职责单一
