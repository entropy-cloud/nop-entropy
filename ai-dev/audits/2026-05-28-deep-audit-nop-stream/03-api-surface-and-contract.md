# 维度 03: API 表面积与契约一致性

## 适用性
适用

## 检查范围
- nop-stream-core 中所有 public interface（80+ 个）
- nop-stream-cep 中公开 API
- nop-stream-runtime 中公开 API
- 检查接口契约的一致性

## 发现

### [维度03-01] nop-stream-api 空壳模块导致 API 表面积散落在 core 中
- **文件**: `nop-stream/nop-stream-api/pom.xml` 和 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/`
- **证据片段**:
  ```xml
  <!-- nop-stream-api/pom.xml -->
  <artifactId>nop-stream-api</artifactId>
  <!-- placeholder, planned but not implemented -->
  <!-- interfaces are in nop-stream-core; this module is reserved for future API extraction -->
  ```
  
  ```java
  // 在 core 中找到的关键公开接口（无实现，纯 API 契约）:
  // io.nop.stream.core.common.functions.SourceFunction
  // io.nop.stream.core.common.functions.SinkFunction
  // io.nop.stream.core.datastream.DataStream
  // io.nop.stream.core.operators.StreamOperator
  // io.nop.stream.core.checkpoint.storage.ICheckpointStorage
  // io.nop.stream.core.common.state.State
  ```
- **严重程度**: P3
- **现状**: nop-stream-api 模块为空壳，所有 80+ 个公开接口（SourceFunction、SinkFunction、DataStream、KeyedStream、WindowedStream、StreamOperator 等）直接定义在 nop-stream-core 中。下游模块（cep、connector、runtime）依赖 core 时同时获取了 API 和实现。
- **风险**: 第三方若想实现自定义 operator 或 source function，必须依赖整个 core 模块（包含 state backend、windowing 等不相关的实现）。API 层的稳定性无法独立于实现层管理。
- **建议**: 与维度 01-02 同一问题。当模块 API 足够稳定后，将纯接口抽取到 api 模块。
- **误报排除**: nop-stream-api 的注释明确说明这是计划中的架构决策。
- **复核状态**: 未复核

### [维度03-02] ICheckpointStorage 接口方法签名统一抛 Exception，缺少具体异常类型
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/storage/ICheckpointStorage.java`
- **证据片段**:
  ```java
  public interface ICheckpointStorage {
      String storeCheckPoint(CompletedCheckpoint checkpoint) throws Exception;
      CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) throws Exception;
      List<CompletedCheckpoint> getAllCheckpoints(String jobId) throws Exception;
      List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) throws Exception;
      void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) throws Exception;
      void deleteAllCheckpoints(String jobId) throws Exception;
      int getCheckpointCount(String jobId) throws Exception;
      String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) throws Exception;
      CompletedCheckpoint loadSavepoint(String savepointPath) throws Exception;
      SavepointMetadata loadSavepointMetadata(String savepointPath) throws Exception;
      void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) throws Exception;
      EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) throws Exception;
  }
  ```
- **严重程度**: P3
- **现状**: ICheckpointStorage 的全部 12 个方法都声明 `throws Exception`，没有使用更具描述性的异常类型（如 IOException 或自定义的 CheckpointStorageException）。调用者无法区分 IO 错误、序列化错误和参数错误。
- **风险**: 调用者（如 CheckpointCoordinator）捕获 Exception 后只能统一处理，无法针对不同故障类型采取不同恢复策略。例如，连接超时可以重试，但序列化格式错误应立即失败。
- **建议**: 定义 `CheckpointStorageException` 并在接口中使用。这是一个可排期改进，不阻塞当前功能。
- **误报排除**: 在引擎模块中，存储层异常的区分对于故障恢复策略至关重要。Apache Flink 的 StateBackend 也使用专门的异常层次。
- **复核状态**: 未复核

## 维度总结
API 表面积广泛（80+ 公开接口），契约基本一致。核心问题是 API 未独立抽取（P3，已知设计决策）。ICheckpointStorage 的 `throws Exception` 签名是可改进的契约问题。
