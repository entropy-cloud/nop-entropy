# 维度 20：跨模块契约一致性

## 第 1 轮（初审）

### [维度20-01] nop-stream-connector 直接实例化 nop-batch-core 实现类，超出 optional 依赖隔离边界

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/BatchConsumerSinkFunction.java:16-17,53-54` 及 `BatchLoaderSourceFunction.java:15,56`
- **证据片段**:
  ```java
  // BatchConsumerSinkFunction.java:16-17
  import io.nop.batch.core.impl.BatchTaskContextImpl;
  import io.nop.batch.core.impl.BatchChunkContextImpl;
  // BatchConsumerSinkFunction.java:53
  this.taskContext = new BatchTaskContextImpl();
  ```
- **严重程度**: P2
- **现状**: nop-batch-core 声明为 optional，但 connector 直接实例化其 impl 包下的类。如果消费者只引入 connector 未引入 batch-core，会得到 NoClassDefFoundError。
- **风险**: 类加载失败。
- **建议**: 通过工厂接口或拆分子模块解决。
- **信心水平**: 确定
- **误报排除**: 不是误报——直接 new 实现类确实绕过了 optional 隔离。
- **复核状态**: 未复核

### [维度20-02] nop-stream-runtime 的 nop-dao provided 依赖导致类加载脆弱性

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:20,48` 及 `JdbcClusterRegistry.java:20,35-36`
- **证据片段**:
  ```java
  import io.nop.dao.jdbc.IJdbcTemplate;
  private final IJdbcTemplate jdbcTemplate;
  ```
- **严重程度**: P2
- **现状**: nop-dao 为 provided scope，但 JDBC 类位于 src/main/java 且 import IJdbcTemplate 作为字段。当 nop-dao 不在 classpath 时，加载这些类本身就会触发 NoClassDefFoundError。
- **风险**: 比预期更早的类加载失败（不使用也会失败）。
- **建议**: 将 JDBC 类移到独立子模块（如 nop-stream-jdbc），使 nop-dao 成为 compile 依赖。
- **信心水平**: 确定
- **误报排除**: 不是误报——Java 类加载机制决定了 import 的类不存在时会立即失败。
- **复核状态**: 未复核

### [维度20-03] ICheckpointExecutorFactory SPI 默认方法抛出 UnsupportedOperationException

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/ICheckpointExecutorFactory.java:77-83`
- **证据片段**:
  ```java
  default StreamExecutionResult executeWithCheckpoint(
          StreamModel streamModel, PartitionedPlan partitionedPlan,
          DeploymentPlan deploymentPlan) throws Exception {
      throw new UnsupportedOperationException("not implemented");
  }
  ```
- **严重程度**: P3
- **现状**: SPI 接口的 default 方法直接抛异常，第三方实现可能未注意到需要覆写。
- **建议**: 添加 @implSpec Javadoc 说明。
- **信心水平**: 确定
- **误报排除**: 不是误报但影响有限（当前只有 runtime 一个实现）。
- **复核状态**: 未复核

### [维度20-04] 使用 System.getProperty("java.io.tmpdir") 而非项目约定的 _tmp/

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/EmbeddedDistributedExecutor.java:91-92` 及 `GraphModelCheckpointExecutor.java:581-583`
- **证据片段**:
  ```java
  LocalFileCheckpointStorage checkpointStorage = new LocalFileCheckpointStorage(
          System.getProperty("java.io.tmpdir") + "/nop-stream-checkpoint/" + jobId);
  ```
- **严重程度**: P3
- **现状**: 项目约定临时文件应使用 _tmp/，但 runtime 使用系统临时目录。
- **建议**: 改为使用项目根目录下的 _tmp/ 或通过配置属性注入。
- **信心水平**: 确定
- **误报排除**: 不是误报——AGENTS.md 明确禁止使用系统级 /tmp/。
- **复核状态**: 未复核

## 跨模块合规亮点

- SPI 接口稳定性良好，core 不依赖 runtime
- SPI 注册文件完整且正确
- nop-stream-cep 依赖隔离正确
