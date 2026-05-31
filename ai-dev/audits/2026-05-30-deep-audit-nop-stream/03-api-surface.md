# 维度 03：API 表面积与契约一致性

## P1 发现

### [03-01] AggregatingState/ReducingState 未扩展 AppendingState — 打破类型层次

- **文件**: `nop-stream-core/.../state/AggregatingState.java:3`, `ReducingState.java:3`
- **证据片段**:
  ```java
  public interface AggregatingState<IN, OUT> extends State {  // 应 extends AppendingState
      OUT get() throws Exception;
      void add(IN value) throws Exception;
  }
  ```
- **严重程度**: P1
- **现状**: 两个接口具有与 AppendingState 相同的方法签名却直接 extends State，破坏了 Flink 建立的类型层次
- **风险**: 针对 AppendingState 编写的客户端代码静默忽略这些实例
- **建议**: 修改为 `extends AppendingState<IN, OUT>`
- **信心水平**: 高

### [03-02] 静态可变 defaultCheckpointExecutorFactory — 跨作业污染

- **文件**: `nop-stream-core/.../StreamExecutionEnvironment.java:63,129`
- **证据片段**:
  ```java
  private static ICheckpointExecutorFactory defaultCheckpointExecutorFactory;
  public static void setCheckpointExecutorFactory(ICheckpointExecutorFactory factory) {
      defaultCheckpointExecutorFactory = factory;
  }
  ```
- **严重程度**: P1
- **现状**: 无 volatile/AtomicReference，无同步，多测试/作业在同一个 JVM 中运行时互相干扰
- **风险**: 竞态条件 + 跨作业状态泄漏
- **建议**: 改为实例字段 + volatile/AtomicReference，或 ServiceLoader 自动发现
- **信心水平**: 高

## P2 发现

### [03-03] WindowedStream extends DataStream — 语义不正确的继承

- **文件**: `nop-stream-core/.../WindowedStream.java:33`
- **严重程度**: P2 — WindowedStream.print() 打印未聚合元素，误导用户

### [03-04] setKeyContextElement2 是死 API，引用不存在的 TwoInputStreamOperator

- **文件**: `nop-stream-core/.../StreamOperator.java:146`
- **严重程度**: P2 — 强制所有实现者提供无用方法

### [03-05] StreamComponents 暴露 7 个 Map<String, Object> getter 无外部调用者

- **文件**: `nop-stream-core/.../StreamComponents.java:67-91`
- **严重程度**: P2

### [03-06] ICheckpointExecutorFactory 默认方法抛 UnsupportedOperationException

- **文件**: `nop-stream-core/.../ICheckpointExecutorFactory.java:77-83`
- **严重程度**: P2 — execute() 调用此重载，任何未覆盖的实现都会运行时失败

### [03-07] RuntimeContext/IterationRuntimeContext 空接口

- **文件**: `nop-stream-core/.../RuntimeContext.java:16`, `IterationRuntimeContext.java:16`
- **严重程度**: P2 — 强制 RichFunction 实现无意义的方法

### [03-08] StateSnapshot 暴露 Map<String, Object>

- **文件**: `nop-stream-core/.../StateSnapshot.java:15,25`
- **严重程度**: P2

## P3 发现（摘要）

| 编号 | 文件 | 摘要 |
|------|------|------|
| 03-09 | Input.java:28-31 | Javadoc 引用 4 个不存在的类 |
| 03-10 | nop-stream-api/pom.xml | 空占位模块 |
| 03-11 | CoFlatMapFunction.java:43 | 缺少 @Internal 注解 |
| 03-12 | DataStream.java:109-118 | collect()/print(SinkFunction) 是 sink() 的无语义别名 |
| 03-13 | StreamExecutionEnvironment.java:303-344 | 重叠的 savepoint 方法不一致降级 |

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 03-01 | P1 | AggregatingState/ReducingState | 未 extends AppendingState |
| 03-02 | P1 | StreamExecutionEnvironment.java:63 | 静态可变字段无同步 |
| 03-03~03-08 | P2 | 多文件 | API 设计/死代码/类型安全缺口 |
| 03-09~03-13 | P3 | 多文件 | 文档漂移/命名不一致 |
