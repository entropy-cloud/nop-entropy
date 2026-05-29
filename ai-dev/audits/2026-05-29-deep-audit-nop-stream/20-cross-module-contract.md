# 维度20：跨模块契约一致性

## 第 1 轮（初审）

### [维度20-01] SinkFunction.finish() 是死契约 — 生产生命周期从未调用

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/SubtaskTask.java:58-81`
- **证据片段**:
  ```java
  // SubtaskTask.java:58-81 — 生命周期：open → invoke → close，无 finish()
  @Override
  public void run() {
      try {
          openOperatorChains();
          subtask.getInvokable().invoke();
          state.set(State.COMPLETED);
      } finally {
          closeOperatorChains();  // ← 只调 close()，从不调 finish()
      }
  }
  ```
  ```java
  // SinkFunction.java:44 — 契约定义："flush any buffered data"
  default void finish() throws Exception {}
  ```
- **严重程度**: P2
- **现状**: `StreamOperator` 和 `SinkFunction` 均定义了 `finish()` 作为刷写缓冲数据的标准生命周期方法，但 `SubtaskTask.run()` 从未调用。按契约正确实现 `finish()` 的 `SinkFunction` 会在流结束时静默丢失缓冲数据。
- **风险**: 全局影响。未来任何按 `SinkFunction.finish()` 契约实现缓冲刷写的开发者都会踩入此陷阱。
- **建议**: 在 `SubtaskTask.run()` 的 invoke 完成后、close 之前调用 `finish()`。
- **信心水平**: 高
- **误报排除**: 不是"看起来不优雅"。接口契约与实际生命周期不匹配是结构性缺陷。
- **复核状态**: 未复核

---

### [维度20-02] CheckpointConfig.checkpointEnabled 默认 true，但无 factory 时静默跳过

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:242-254`
- **证据片段**:
  ```java
  // CheckpointConfig.java:27 — 默认启用
  private boolean checkpointEnabled = true;
  
  // StreamExecutionEnvironment.java:242-254 — factory 为 null 时静默跳过
  if (checkpointConfig.isCheckpointEnabled() && checkpointExecutorFactory != null) {
      // checkpoint 路径
  }
  // ← factory 为 null 时直接落到这里，无 checkpoint 无警告
  ```
- **严重程度**: P2
- **现状**: 用户以为 checkpoint 已启用（默认值），实际上可能从未生效。无任何警告或日志。
- **风险**: 用户配置意图被静默忽略，可能导致生产数据丢失时才发现 checkpoint 未生效。
- **建议**: 在 checkpoint 被静默跳过时输出 WARN 日志，或在 `checkpointEnabled=true` 但无 factory 时抛出异常。
- **信心水平**: 高
- **误报排除**: 默认配置与实际行为不匹配是可量化的语义偏差。
- **复核状态**: 未复核

---

### [维度20-03] CheckpointConfig.storageType="jdbc" 配置已公布但运行时必定抛异常

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:568-581`
- **证据片段**:
  ```java
  static ICheckpointStorage createStorage(CheckpointConfig config) {
      String storageType = config.getStorageType();
      if ("jdbc".equalsIgnoreCase(storageType)) {
          throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_FAILED)
                  .param(ARG_DETAIL, "JdbcCheckpointStorage requires IJdbcTemplate configuration...");
      }
      return new LocalFileCheckpointStorage(basePath);  // ← 硬编码，无 SPI
  }
  ```
- **严重程度**: P2
- **现状**: 文档/测试中明确支持 `"jdbc"` 值，但 `createStorage()` 在 `"jdbc"` 分支直接抛异常。`JdbcCheckpointStorage` 类已实现但无法被工厂方法实例化。同时 `ICheckpointStorage` 没有 SPI 或工厂模式。
- **风险**: 需要 JDBC 持久化 checkpoint 的生产部署场景完全不可用。消费者必须自行实例化绕过框架。
- **建议**: (1) 在 `createStorage()` 中接受 `IJdbcTemplate` 参数；(2) 或引入 `ICheckpointStorageFactory` SPI。
- **信心水平**: 高
- **误报排除**: `JdbcCheckpointStorage` 已实现且可用，但工厂方法无法创建它，是明确的实现缺口。
- **复核状态**: 未复核

---

### [维度20-04] nop-stream-connector 直接导入 nop-batch-core 实现类

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/BatchConsumerSinkFunction.java:10-17`
- **证据片段**:
  ```java
  import io.nop.batch.core.impl.BatchTaskContextImpl;
  import io.nop.batch.core.impl.BatchChunkContextImpl;
  
  // 直接构造实现类
  IBatchChunkContext chunkContext = new BatchChunkContextImpl(taskContext);
  ```
- **严重程度**: P3
- **现状**: 直接导入 impl 包的实现类而非接口，将 connector 耦合到 batch 模块内部实现。
- **风险**: `nop-batch-core` 重构内部实现类时会受到破坏性影响。
- **建议**: 通过工厂方法或依赖注入获取 `IBatchTaskContext` 和 `IBatchChunkContext`。
- **信心水平**: 高
- **误报排除**: 不是"多模块 pom.xml 中存在必要的传递依赖声明"。是代码层面直接耦合实现类。
- **复核状态**: 未复核

---

### [维度20-05] nop-stream-runtime 依赖 nop-dao 为 provided scope 但生产代码使用其接口

- **文件**: `nop-stream/nop-stream-runtime/pom.xml:33-37`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-dao</artifactId>
      <scope>provided</scope>
  </dependency>
  ```
  ```java
  // JdbcCheckpointStorage.java:18 — 生产代码使用 nop-dao 接口
  import io.nop.dao.jdbc.IJdbcTemplate;
  ```
- **严重程度**: P3
- **现状**: `provided` scope 不会传递给消费者。消费者 classpath 上没有 `nop-dao` 时使用 JDBC 功能会触发 `NoClassDefFoundError`。
- **风险**: 运行时才发现缺失依赖。
- **建议**: 考虑改为 `compile`（可选）或在文档中明确标注此隐式依赖。
- **信心水平**: 高
- **误报排除**: `provided` 语义是"编译期需要但运行时由容器提供"。此处消费者无法预知需要提供 `nop-dao`。
- **复核状态**: 未复核
