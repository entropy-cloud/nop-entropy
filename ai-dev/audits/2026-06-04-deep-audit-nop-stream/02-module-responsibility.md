# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] WindowOperator.java 职责混合（God Class, 1668行）

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java`
- **行数**: 1668
- **证据片段**:
  ```java
  // 14 个内部类/接口，至少 5 种职责
  :1233  private static class NamespaceAwareValueState<T> implements ValueState<T> {
  :1263  private static class NamespaceAwareListState<T> implements ListState<T> {
  :1305  private static class NamespaceAwareReducingState<T> implements ReducingState<T> {
  :1335  private static class NamespaceAwareAggregatingState<IN, OUT> implements AggregatingState<IN, OUT> {
  :1365  private static class NamespaceAwareMapState<UK, UV> implements MapState<UK, UV> {
  ```
- **严重程度**: P2
- **现状**: 1668 行，含 14 个内部类/接口，承担窗口主逻辑 + State Store 适配 + Namespace State 包装 + Trigger 上下文 + Timer 管理。
- **风险**: `NamespaceAware*` 系列（5个类，~230行）是通用状态适配代码，与窗口逻辑正交，维护耦合。
- **建议**: 提取 `NamespaceAwareStateAdapters` 为独立文件。提取 State Store 适配。主文件可降至 ~1200 行。
- **信心水平**: 很可能 (85%)
- **误报排除**: Flink 原版也类似，但 Nop 不必须跟随设计缺陷。
- **复核状态**: 未复核

### [维度02-02] NFACompiler.java 内含巨型内部类（1099行）

- **文件**: `nop-stream-cep/.../nfa/compiler/NFACompiler.java`
- **证据片段**:
  ```java
  :58   public class NFACompiler {
  :142    static class NFAFactoryCompiler<T> {  // ~900 lines
  :1061   public interface NFAFactory<T> extends Serializable {
  :1073   private static class NFAFactoryImpl<T> implements NFAFactory<T> {
  ```
- **严重程度**: P3
- **现状**: 外层仅 2 个 static 方法，内部类 `NFAFactoryCompiler` 占 ~900 行。
- **风险**: 低。内部类内聚（Pattern → NFA 编译），结构大但可理解。
- **建议**: 将 `NFAFactoryCompiler` 提升为包级类，不紧急。
- **信心水平**: 很可能 (90%)
- **误报排除**: 编译器内部类模式在编译器领域常见。
- **复核状态**: 未复核

### [维度02-04] GraphModelCheckpointExecutor.java 职责过多 + 代码重复（807行）

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java`
- **证据片段**:
  ```java
  :59   public static StreamExecutionResult executeWithCheckpoint(...)
  :104  public static StreamExecutionResult executeWithCheckpoint(StreamModel, PartitionedPlan, DeploymentPlan)
  :175  public static String triggerSavepoint(...)
  :222  public static StreamExecutionResult executeWithSavepoint(...)
  :587  private static void restoreFromCheckpoint(...)
  :659  private static void restoreFromSavepointPath(...)
  ```
- **严重程度**: P2
- **现状**: 纯 static 方法类，承担编排 + Savepoint 管理 + Checkpoint 恢复 + 辅助构建。两个 `executeWithCheckpoint` 重载代码高度重复。
- **风险**: 807 行全 static 方法暗示缺乏对象建模。恢复逻辑与编排逻辑耦合。
- **建议**: 提取 `CheckpointRestoreHelper` 和 `SavepointManager`。消除重复。
- **信心水平**: 很可能 (80%)
- **误报排除**: 作为框架入口有一定编排合理性，但 static 过度使用是结构性问题。
- **复核状态**: 未复核

### [维度02-06] Checkpoint 职责跨 core/runtime 分散 + placeholder 空壳

- **文件**: 多文件
- **严重程度**: P3
- **现状**: core 包含 checkpoint 接口/DTO，runtime 包含实现，checkpoint 模块是空壳。
- **风险**: 如果 checkpoint 功能增长，缺少清晰模块边界。
- **建议**: 明确 placeholder 定位——废弃或作为实现迁移目标。
- **信心水平**: 很可能 (75%)
- **误报排除**: core(接口) + runtime(实现) 分层本身合理。
- **复核状态**: 未复核

### [维度02-07] Placeholder 模块有 IDE 配置文件泄漏

- **文件**: `nop-stream-api/.classpath`, `.project`, `.settings/*`
- **严重程度**: P3
- **现状**: Eclipse IDE 配置文件被提交到版本控制。
- **风险**: 低。不影响构建，但违反代码仓库清洁度标准。
- **建议**: 删除并添加 .gitignore 规则。
- **信心水平**: 确定 (95%)
- **误报排除**: 不是功能性缺陷，是工程卫生问题。
- **复核状态**: 未复核

### [维度02-09] WindowAggregationOperator(core) vs WindowOperator(runtime) 边界模糊

- **文件**: `core/operators/WindowAggregationOperator.java`(871行) vs `runtime/operators/windowing/WindowOperator.java`(1668行)
- **严重程度**: P3
- **现状**: 两者功能重叠——都处理窗口分配、触发器、状态管理。`WindowAggregationOperator` 在 core 中含运行时实现。
- **风险**: 两个窗口算子维护需同时修改 core 和 runtime。
- **建议**: 明确 core/runtime 窗口算子分界。
- **信心水平**: 有趣的猜测 (70%)
- **误报排除**: 可能是轻量级 vs 通用实现路径的刻意分离。
- **复核状态**: 未复核

## 已排除项

- NFA.java (980行)：大但内聚，状态机核心实现属正常范围。
- MemoryStateSerDe.java (685行)：大但结构对称。
- _gen 文件（4个 CepPattern*Model）：合规，无手写修改痕迹。
- Core 包含 connector 接口：SPI 模式合理。
- Placeholder 模块无 src 代码：api, checkpoint, flink, flow 均为空壳。

## 维度复核结论

待复核。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 02-01 | P2 | WindowOperator.java | 1668行 God Class，14内部类 |
| 02-02 | P3 | NFACompiler.java | 内部类占 900 行 |
| 02-04 | P2 | GraphModelCheckpointExecutor.java | 职责过多 + 代码重复 |
| 02-06 | P3 | 跨 core/runtime | Checkpoint placeholder 空壳 |
| 02-07 | P3 | nop-stream-api | IDE 配置文件泄漏 |
| 02-09 | P3 | core/runtime 窗口算子 | 边界模糊 |
