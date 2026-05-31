# 维度 20：跨模块契约一致性

## 发现

### [20-01] StreamConnectors 便利门面破坏 optional 依赖隔离

- **文件**: `nop-stream-connector/.../StreamConnectors.java:10-11`
- **证据片段**:
  ```java
  import io.nop.batch.core.IBatchConsumerProvider;
  import io.nop.batch.core.IBatchLoaderProvider;
  ```
- **严重程度**: P3
- **现状**: 类级别 import nop-batch-core 类型，当该依赖不在 classpath 时 JVM 加载类本身即抛 NoClassDefFoundError
- **风险**: 用户仅引入 nop-message-core 时无法使用 StreamConnectors
- **建议**: 将 batch 方法拆到 BatchStreamConnectors 或使用 Class.forName 延迟加载

### [20-02] ICheckpointExecutorFactory 的 ServiceLoader 注册文件未被消费

- **文件**: `nop-stream-runtime/src/main/resources/META-INF/services/io.nop.stream.core.execution.ICheckpointExecutorFactory`
- **严重程度**: P2
- **现状**: runtime 提供了 ServiceLoader 注册文件，但 core 的 StreamExecutionEnvironment 不使用 ServiceLoader 发现，依赖手动 setCheckpointExecutorFactory()
- **风险**: ServiceLoader 死文件误导开发者以为注册已自动完成
- **建议**: 增加 ServiceLoader.load() fallback 或删除死文件

## 正面确认

- JdbcCheckpointStorage/JdbcClusterRegistry 对 nop-dao 的 provided 依赖使用正确 ✓
- ErrorCode 跨子模块命名空间隔离清晰 ✓
- ICheckpointStorage 两个实现类均完整实现全部接口方法 ✓
- CepOperator 正确使用 core 的所有抽象接口 ✓
- 配置项无冲突 ✓

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 20-01 | P3 | StreamConnectors.java | optional 依赖隔离被类级 import 打破 |
| 20-02 | P2 | META-INF/services | ServiceLoader 注册文件未被消费 |
