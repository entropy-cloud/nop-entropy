# 维度02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] GraphModelCheckpointExecutor 混合了作业构建、执行调度、checkpoint 恢复和 savepoint 管理四类职责

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:55-815`
- **证据片段**:
  ```java
  // 作业图构建 (line 158)
  private static JobGraph buildJobGraphFromStreamModel(StreamModel streamModel) {

  // 执行计划构建 + 任务提交 (line 355, 528)
  private static GraphExecutionPlan buildExecutionPlan(JobGraph jobGraph, ...) {

  // Checkpoint 恢复 + Fingerprint 校验 (line 583, 655)
  private static void restoreFromCheckpoint(...) throws Exception {

  // Savepoint 管理 (line 175, 686)
  public static String triggerSavepoint(...) throws Exception {

  // 任务终止处理
  private static void handleJobTermination(...) {
  ```
- **严重程度**: P2
- **现状**: 815 行的静态工具类同时负责：(1) StreamModel → JobGraph 构建；(2) JobGraph → ExecutionPlan 构建；(3) checkpoint/barrier 调度启动；(4) 任务提交和运行；(5) savepoint 触发和恢复；(6) fingerprint 兼容性校验；(7) 任务终止处理；(8) 资源 shutdown。3 个公开入口方法和 15+ 私有方法，全部是 static 方法。
- **风险**: 随着功能扩展，修改任一职责都可能影响其他职责。方法间通过参数传递临时状态，增加认知复杂度。
- **建议**: 可考虑拆分为：(1) `CheckpointExecutionLifecycle`；(2) `CheckpointRestoreService`；(3) `SavepointManager`。`GraphModelCheckpointExecutor` 保留为编排入口。当前 static 方法模式使拆分风险较低。
- **信心水平**: 很可能
- **误报排除**: 815 行中至少存在 4 个可明确区分的职责域，且互相独立。后续 feature work 会迫使修改该文件，增加 regression 风险。
- **复核状态**: 未复核

---

### [维度02-02] MemoryKeyedStateBackend 内嵌 7 个私有状态实现类导致文件膨胀至 1398 行

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:69-1398`
- **证据片段**:
  ```java
  // 外层类 (line 69)
  public class MemoryKeyedStateBackend<K> implements IInternalStateBackend<K>, Serializable {

  // 7 个内部类, 行号范围:
  // line 865:  private static class MemoryListState<T>            (~53 行)
  // line 918:  private static class MemoryValueState<T>           (~40 行)
  // line 958:  private static class MemoryMapState<UK, UV>        (~93 行)
  // line 1051: private static class MemoryReducingState<T>        (~45 行)
  // line 1096: private static class MemoryAggregatingState<IN,ACC,OUT> (~49 行)
  // line 1145: private static class MemoryInternalAppendingState<K,N,IN,ACC> (~90 行)
  // line 1235: private static class MemoryInternalListState<K,N,T>      (~116 行)
  // line 1351: protected static class TypedNamespaceAndKey              (~47 行)
  ```
- **严重程度**: P3
- **现状**: 1398 行文件承载两类职责：(1) 状态后端生命周期管理；(2) 7 种具体状态类型的内存实现。内部类合计约 533 行（38%）。外部类 snapshot/restore 序列化逻辑约 550 行。
- **风险**: 每种状态类型的序列化/反序列化逻辑分散在主类中，修改某一种状态类型的序列化格式需要在该 1398 行文件中定位。新增状态类型必须同时修改主类和添加内部类。
- **建议**: 可考虑将内部类提取为同包下的独立文件（`MemoryListState.java` 等），序列化/反序列化逻辑可通过策略模式外提。当前不影响功能正确性。
- **信心水平**: 很可能
- **误报排除**: 1398 行在核心引擎中是最大单文件，内嵌 7 个独立类各有独立泛型签名。结构性原因明确：状态类型数与文件行数线性增长。
- **复核状态**: 未复核

---

## 检查范围说明

**已检查的模块**：
- nop-stream-api: 空占位模块，合规
- nop-stream-core: 核心引擎逻辑，职责明确，合规
- nop-stream-cep: 纯 CEP 逻辑，合规
- nop-stream-runtime: 运行时执行，职责明确，合规
- nop-stream-connector: 外部连接器，合规
- nop-stream-fraud-example: 演示应用，合规
- checkpoint/flink/flow: 空占位模块

**跨模块 import 方向**：无反向依赖 ✓ 无循环依赖 ✓

**_gen/ 目录**：4 个生成文件均以 `_` 前缀命名，无手写文件混入 ✓

**Core 中 WindowAggregationOperator vs Runtime 中 WindowOperator**：两者不是重复实现，面向不同使用场景，不存在职责越权 ✓
