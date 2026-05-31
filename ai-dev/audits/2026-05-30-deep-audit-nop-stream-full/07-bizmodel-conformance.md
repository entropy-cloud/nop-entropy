# 维度 07：核心服务接口与实现规范

> 注：nop-stream 是流处理引擎框架模块，没有 BizModel/XMeta/GraphQL 层。本维度调整为检查核心服务接口设计和 IoC 集成。

## 第 1 轮（初审）

### [维度07-01] ICheckpointExecutorFactory SPI 文件存在但从未被 ServiceLoader 加载（死代码）

- **文件**: `nop-stream/nop-stream-runtime/src/main/resources/META-INF/services/io.nop.stream.core.execution.ICheckpointExecutorFactory`，`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:63-65,91-93,129-131`
- **证据片段**:
```java
// StreamExecutionEnvironment.java 第 63-65 行
private static ICheckpointExecutorFactory defaultCheckpointExecutorFactory;

// 第 129-131 行
public static void setCheckpointExecutorFactory(ICheckpointExecutorFactory factory) {
    defaultCheckpointExecutorFactory = factory;
}

// 第 91-93 行
public StreamExecutionEnvironment() {
    this.checkpointExecutorFactory = defaultCheckpointExecutorFactory;
}
```
- **严重程度**: P2
- **现状**: nop-stream 中有两个 SPI 接口。`IDeploymentPlanProvider` 正确使用 `ServiceLoader.load()` 自动发现，但 `ICheckpointExecutorFactory` 的 SPI 文件存在却无人读取，需要手动调用 `StreamExecutionEnvironment.setCheckpointExecutorFactory()` 注册。
- **风险**: 如果集成者看到 SPI 文件存在，会假设自动发现已工作。实际上运行时不会自动加载 `CheckpointExecutorFactoryImpl`，导致 checkpoint 功能静默失效。
- **建议**: 要么在 `ICheckpointExecutorFactory` 中增加 ServiceLoader 自动发现方法并在 `StreamExecutionEnvironment` 构造时调用；要么删除 SPI 文件并文档化手动注册路径。
- **信心水平**: 确定
- **误报排除**: 同模块中 `IDeploymentPlanProvider` 已正确实现 SPI 自动发现，此处不一致是真实的实现遗漏。
- **复核状态**: 未复核

---

### [维度07-02] Fraud Example 中 UnusualAmountPattern 使用硬编码 DEMO STUB，UserTransactionHistory 未被引用

- **文件**: `nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/pattern/UnusualAmountPattern.java:116-119`，`nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/state/UserTransactionHistory.java`
- **证据片段**:
```java
// UnusualAmountPattern.java 第 116-119 行
private static BigDecimal getAverageForUser(String userId) {
    // DEMO STUB: return a fixed average for demo
    return new BigDecimal("100");
}
```
- **严重程度**: P3
- **现状**: `UnusualAmountPattern.getAverageForUser()` 始终返回硬编码值。`UserTransactionHistory` 类存在但从未被任何代码引用。
- **风险**: 作为示例代码，降低了参考价值。
- **建议**: 要么集成 `UserTransactionHistory` 展示状态管理与 CEP 的交互模式，要么删除未使用的类。
- **信心水平**: 确定
- **误报排除**: 示例代码质量问题不影响框架运行时行为，但降低示例的参考价值。
- **复核状态**: 未复核

---

### [维度07-03] @Internal 注解在核心接口上使用不一致

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/StreamFunction.java:19`，`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/source/SourceFunction.java`
- **证据片段**:
```java
// StreamFunction.java 第 19 行 — 标注了 @Internal
@Internal
public interface StreamFunction extends Serializable {}

// SourceFunction.java — 未标注 @Internal（继承自 StreamFunction）
public interface SourceFunction<T> extends StreamFunction, Serializable {}
```
- **严重程度**: P3
- **现状**: `StreamFunction`（基接口）标注了 `@Internal`，但其子接口 `SourceFunction`、`SinkFunction`、`RichFunction` 没有。同时 SPI 接口（`ICheckpointExecutorFactory`、`IDeploymentPlanProvider`）标注了 `@Internal`，而 `ICheckpointedFunction` 没有。
- **风险**: API 稳定性契约模糊，用户无法确定哪些接口是稳定 API。
- **建议**: 统一 `@Internal` 标注策略。如果 SourceFunction/SinkFunction 是公开 API，则 StreamFunction 不应是 internal。
- **信心水平**: 很可能
- **误报排除**: 基接口标记 internal 但子接口不标记，在语义上是矛盾的。
- **复核状态**: 未复核

---

## 维度复核结论

（待复核）

## 子项复核结论

（待复核）

## 最终保留项

（待复核后填写）
