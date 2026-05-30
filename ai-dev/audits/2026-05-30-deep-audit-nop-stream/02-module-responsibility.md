# 维度 02：模块职责与文件边界

### [维度02-01] 四个空占位模块无源码但参与 Maven Reactor 构建

- **文件**: `nop-stream/nop-stream-api/pom.xml`, `nop-stream/nop-stream-checkpoint/pom.xml`, `nop-stream/nop-stream-flink/pom.xml`, `nop-stream/nop-stream-flow/pom.xml`
- **证据片段**:
  ```xml
  <artifactId>nop-stream-api</artifactId>
  <!-- placeholder, planned but not implemented -->
  <!-- interfaces are in nop-stream-core; this module is reserved for future API extraction -->
  ```
- **严重程度**: P3
- **现状**: 4个空模块在父 pom 的 `<modules>` 中注册，每次 `mvn install` 都会被 Reactor 遍历。
- **风险**: 在 IDE 中呈现空项目、在 Reactor 输出中产生误导。
- **建议**: 维持现状可接受。若想清理，可在 pom 注释中明确标注预留原因和预计引入时间。
- **信心水平**: 确定
- **误报排除**: 四个模块有独立 pom.xml 和 placeholder 注释，是设计意图。
- **复核状态**: 未复核

### [维度02-02] WindowOperator 的 onEventTime/onProcessingTime 存在结构重复

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:483-595`
- **证据片段**:
  ```java
  // onEventTime (lines 483-538):
  @Override
  public void onEventTime(InternalTimer<K, W> timer) throws Exception {
      triggerContext.key = timer.getKey();
      triggerContext.window = timer.getNamespace();
      // ... 几乎与 onProcessingTime 逐行相同
      TriggerResult triggerResult = triggerContext.onEventTime(timer.getTimestamp());
      // ...
  }

  // onProcessingTime (lines 540-595):
  @Override
  public void onProcessingTime(InternalTimer<K, W> timer) throws Exception {
      // ... 几乎与 onEventTime 逐行相同
      TriggerResult triggerResult = triggerContext.onProcessingTime(timer.getTimestamp());
      // ...
  }
  ```
- **严重程度**: P2
- **现状**: WindowOperator.java 共 1090 行，其中 onEventTime + onProcessingTime 约 110 行是几乎完全重复的代码。
- **风险**: 如果修改其中一个方法的逻辑而遗漏另一个，会引入不一致行为。
- **建议**: 提取一个 `onTimer(InternalTimer<K,W> timer, boolean isEventTime)` 私有方法统一处理。
- **信心水平**: 确定
- **误报排除**: 真正的逻辑重复，不是风格偏好。
- **复核状态**: 未复核

### [维度02-03] WindowAggregationOperator (core) 与 WindowOperator (runtime) 职责重叠

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java` (676行) vs `nop-stream-runtime/.../operators/windowing/WindowOperator.java` (1090行)
- **证据片段**:
  ```java
  // WindowAggregationOperator (core):
  public class WindowAggregationOperator<IN, ACC, OUT, K, W extends Window>
          extends AbstractStreamOperator<OUT>
          implements OneInputStreamOperator<IN, OUT>, KeyContext {

  // WindowOperator (runtime):
  public class WindowOperator<K, IN, ACC, OUT, W extends Window>
          extends AbstractUdfStreamOperator<OUT, InternalWindowFunction<ACC, OUT, K, W>>
          implements OneInputStreamOperator<IN, OUT>, Triggerable<K, W> {
  ```
- **严重程度**: P2
- **现状**: 两者都实现窗口运算符核心逻辑。core 版使用内置 LinkedHashMap 管理窗口状态，runtime 版使用 IKeyedStateBackend + MapState。两者目前无继承关系。
- **风险**: 新开发者不清楚何时用哪个算子。修改窗口逻辑时需要在两处同步。
- **建议**: 在类级别 Javadoc 中明确说明两者关系和各自适用场景。长期可考虑提取公共基类。
- **信心水平**: 很可能
- **误报排除**: 两者都在生产代码中被引用，不是死代码。
- **复核状态**: 未复核

### [维度02-04] nop-stream-core 承担了约 56% 的代码量，职责边界过宽

- **文件**: `nop-stream/nop-stream-core/src/main/java/` (288 文件, 27566 行)
- **证据片段**: core 模块包含 checkpoint/、execution/、operators/、graph/、jobgraph/、environment/、datastream/、windowing/、model/ 等包。
- **严重程度**: P2
- **现状**: core 模块既包含核心抽象接口，也包含完整的执行引擎实现。这导致 core 模块依赖 nop-core。
- **风险**: 任何上层模块只要需要核心接口就必须引入整个执行引擎的实现依赖。修改执行逻辑时，core 的变更会波及所有下游模块。
- **建议**: 未来可考虑将 execution/、operators/（具体实现类）、graph/、jobgraph/ 中的非抽象类提取到独立模块。但当前结构已稳定，不建议近期重构。
- **信心水平**: 很可能
- **误报排除**: 空模块 (api/checkpoint) 的存在本身就说明最初设计者曾计划更细的拆分。
- **复核状态**: 未复核

### [维度02-05] NFACompiler 内部类 NFAFactoryCompiler 约 900 行，职责集中但内聚

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:140-1053`
- **证据片段**:
  ```java
  static class NFAFactoryCompiler<T> {
      private final NFAStateNameHandler stateNameHandler = new NFAStateNameHandler();
      private final Map<String, State<T>> stopStates = new HashMap<>();
      // ... 约 30 个私有方法
  }
  ```
- **严重程度**: P3
- **现状**: 内部类负责将 Pattern 编译为 NFA 状态图，职责单一且内聚。所有方法操作同一组共享状态。
- **风险**: 维护成本可接受。
- **建议**: 维持现状。可将 NFAFactoryCompiler 提升为顶层类。
- **信心水平**: 确定
- **误报排除**: 内部类虽然大，但所有方法操作同一组共享状态，是高内聚的编译器实现。
- **复核状态**: 未复核

### [维度02-06] NFA.java 包含 4 个内部类

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:480-972`
- **证据片段**:
  ```java
  private static class OutgoingEdges<T> { ... }
  private class EventWrapper implements AutoCloseable { ... }
  private class ConditionContext implements IterativeCondition.Context<T> { ... }
  public static class MigratedNFA<T> { ... }
  ```
- **严重程度**: P3
- **现状**: 4 个内部类紧密依赖外部 NFA 实例状态。
- **风险**: 低。强行拆分不会带来架构改善。
- **建议**: 维持现状。
- **信心水平**: 确定
- **误报排除**: 内部类高度耦合外部类状态。
- **复核状态**: 未复核

### [维度02-07] GraphModelCheckpointExecutor 是一个 812 行的全静态方法编排类

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1-812`
- **证据片段**:
  ```java
  public class GraphModelCheckpointExecutor {
      public static StreamExecutionResult executeWithCheckpoint(
              JobGraph jobGraph, String jobName, CheckpointConfig checkpointConfig) { ... }
      // 后续约 20+ 个 private static 方法
  }
  ```
- **严重程度**: P2
- **现状**: 812 行全静态类承担 checkpoint 执行路径的完整编排。两个 executeWithCheckpoint 重载共享大量代码但无法利用多态。
- **风险**: 如果需要支持新的 checkpoint 存储后端或新的执行模式，需要在长文件中定位正确的插入点。
- **建议**: 可重构为实例类，将配置参数作为构造器注入。但这是一个风格建议，当前代码功能正确。
- **信心水平**: 很可能
- **误报排除**: 812 行全静态方法编排 + 两个重载共享逻辑 + 无法利用多态是可量化的结构特征。
- **复核状态**: 未复核

### [维度02-08] _gen 目录下的生成代码未发现手写修改痕迹

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/model/_gen/` (4 文件)
- **证据片段**:
  ```java
  // _CepPatternPartModel.java:10-17
  // tell cpd to start ignoring code - CPD-OFF
  @SuppressWarnings({"PMD.UselessOverridingMethod",...})
  public abstract class _CepPatternPartModel extends AbstractComponentModel {
  ```
- **严重程度**: N/A (未发现问题)
- **现状**: _gen 代码完全符合 Nop 平台代码生成规范，未发现手写修改痕迹。
- **风险**: 无
- **建议**: 维持现状。
- **信心水平**: 确定
- **误报排除**: 明确的审核步骤结果——确认无问题。
- **复核状态**: 未复核
