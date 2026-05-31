# 维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] AbstractStreamOperator 13+ 个 public 方法应为 protected

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java`
- **证据片段**:
```java
// 以下方法应为 protected 而非 public
public Output<StreamRecord<OUT>> getOutput() { ... }
public void setOutput(Output<StreamRecord<OUT>> output) { ... }
public IStateBackend getStateBackend() { ... }
public void setStateBackend(IStateBackend stateBackend) { ... }
public OperatorSnapshotResult restoreState(OperatorSnapshotResult snapshotResult) { ... }
public void snapshotState(StateSnapshotContext context) { ... }
public void processBarrier(CheckpointBarrier barrier) { ... }
// ... 共 13+ 个方法
```
- **严重程度**: P2
- **现状**: 框架内部的生命周期/基础设施方法暴露为 public，但外部不应直接调用。
- **风险**: 外部使用者可能误以为这些是稳定 API，增加未来重构难度。
- **建议**: 将 `getOutput/setOutput`、`setStateBackend/getStateBackend`、`restoreState/snapshotState`、`processBarrier` 等方法改为 protected。
- **信心水平**: 确定
- **误报排除**: 这些是框架内部方法，对比 Flink 原版中这些方法也在基类或 internal 接口中。
- **复核状态**: 未复核

---

### [维度03-02] ReduceFunction/AggregateFunction/WindowFunction 标为 @Internal 但需用户实现

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/ReduceFunction.java` 等
- **证据片段**:
```java
@Internal
public interface ReduceFunction<T> extends StreamFunction {
    T reduce(T value1, T value2) throws Exception;
}

// 对比：无 @Internal 的 MapFunction
public interface MapFunction<T, R> extends StreamFunction, java.io.Serializable {
    R map(T value) throws Exception;
}
```
- **严重程度**: P2
- **现状**: `ReduceFunction`、`AggregateFunction`、`WindowFunction` 标注 `@Internal`，但用户需要实现这些接口来使用 `KeyedStream.reduce()`、`WindowedStream.aggregate()` 等公共 API。而 `MapFunction`、`FilterFunction` 等同类接口未标注 `@Internal`。
- **风险**: `@Internal` 意味着"不保证兼容性"，如果用户依赖了被标为 @Internal 的接口，可能在版本升级时遇到破坏性变更。
- **建议**: 移除这些用户面向接口的 @Internal 标注，与 MapFunction/FilterFunction 保持一致。
- **信心水平**: 确定
- **误报排除**: 同类用户面向接口（MapFunction、FilterFunction）未标 @Internal，此处不一致是真实矛盾。
- **复核状态**: 未复核

---

### [维度03-03] Function 接口 Serializable 冗余声明不一致

- **文件**: `SourceFunction.java`、`MapFunction.java`、`FilterFunction.java` 等 vs `SinkFunction.java`、`ReduceFunction.java`
- **证据片段**:
```java
// SourceFunction — 冗余 Serializable（StreamFunction 已 extends Serializable）
public interface SourceFunction<T> extends StreamFunction, Serializable { ... }
// MapFunction — 使用全限定 java.io.Serializable
public interface MapFunction<T, R> extends StreamFunction, java.io.Serializable { ... }
// SinkFunction — 无冗余
public interface SinkFunction<T> extends StreamFunction { ... }
```
- **严重程度**: P3
- **现状**: 5 个接口重复声明 `extends Serializable`（其中 MapFunction/FlatMapFunction 用全限定名），但 SinkFunction/ReduceFunction 没有冗余声明。
- **风险**: 声明风格不一致，让阅读者困惑是否有特殊意图。
- **建议**: 统一移除冗余 `extends Serializable`，StreamFunction 已包含。
- **信心水平**: 确定
- **误报排除**: 功能无影响，但 5/7 接口的冗余声明不一致是可验证的代码风格问题。
- **复核状态**: 未复核

---

## 维度 12：GraphQL 与 API 层

**不适用**。nop-stream 是流处理引擎框架模块，不包含 GraphQL 层。

## 维度复核结论

| 发现 | 判定 | 说明 |
|------|------|------|
| [维度03-01] AbstractStreamOperator 方法可见性 | **保留 P2** | 实际有 23 个非接口覆盖的 public 方法（超过初审声称的 13+）；改为 protected 会在 3 个生产文件（GraphModelCheckpointExecutor、CheckpointPlanBuilder、StreamTaskInvokable，跨 2 模块）引起编译失败 |
| [维度03-02] 函数接口 @Internal 矛盾 | **保留 P2** | 确认。AggregateFunction 的 Javadoc 甚至包含用户实现示例代码，与 @Internal 含义直接矛盾 |
| [维度03-03] Serializable 冗余声明 | 保留 P3 | 确认 |

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 03-01 | P2 | AbstractStreamOperator.java | 23 个框架内部方法应为 protected 而非 public，修复需跨模块重构 |
| 03-02 | P2 | ReduceFunction/AggregateFunction/WindowFunction | 用户面向接口标注 @Internal 与实际用途矛盾 |
| 03-03 | P3 | SourceFunction/MapFunction 等 | Serializable 冗余声明不一致 |
