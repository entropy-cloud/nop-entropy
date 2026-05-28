# 维度 02: 模块职责与文件边界

## 适用性
适用

## 检查范围
- nop-stream 所有子模块下的 src/main/java 目录
- 统计了 407 个主代码文件的行数分布

## 发现

### [维度02-01] MemoryKeyedStateBackend 1254 行，承担 7 种内部状态实现
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:1-1254`
- **证据片段**:
  ```java
  // L68: 主类定义
  public class MemoryKeyedStateBackend<K> implements IInternalStateBackend<K>, Serializable {
      // L721: 内部类 MemoryListState
      private static class MemoryListState<T> implements ListState<T>, Serializable { ... }
      // L774: 内部类 MemoryValueState
      private static class MemoryValueState<T> implements ValueState<T>, Serializable { ... }
      // L814: 内部类 MemoryMapState
      private static class MemoryMapState<UK, UV> implements MapState<UK, UV>, Serializable { ... }
      // L907: 内部类 MemoryReducingState
      private static class MemoryReducingState<T> implements ReducingState<T>, Serializable { ... }
      // L952: 内部类 MemoryAggregatingState
      private static class MemoryAggregatingState<IN, ACC, OUT> implements AggregatingState<IN, OUT>, Serializable { ... }
      // L1001: 内部类 MemoryInternalAppendingState
      private static class MemoryInternalAppendingState<K, N, IN, ACC> ... { ... }
      // L1091: 内部类 MemoryInternalListState
      private static class MemoryInternalListState<K, N, T> ... { ... }
      // L1168: 辅助类 ShardPrefixedKey
      // L1207: 辅助类 TypedNamespaceAndKey
  }
  ```
- **严重程度**: P2
- **现状**: 单个文件包含 7 个内部状态实现类 + snapshot/restore 逻辑 + 序列化/反序列化。这 7 个内部类（MemoryListState、MemoryValueState、MemoryMapState、MemoryReducingState、MemoryAggregatingState、MemoryInternalAppendingState、MemoryInternalListState）各自都是独立的 Serializable 对象，但被压缩在一个文件中。
- **风险**: 修改任何一种状态实现都需要审查 1254 行文件。snapshot/restore 逻辑的 5 个 restoreXxxState 方法和 5 个 snapshotXxxState 方法与状态实现混在一起，增加了变更风险。
- **建议**: 将 7 个内部状态类提取为独立文件（在 `state/backend/memory/` 包下），同时将 snapshot/restore 逻辑提取到 `MemoryStateSerializer` 辅助类。主类控制在 200 行以内。
- **误报排除**: 这不是简单的"太长"问题。该文件将状态定义、状态实现、序列化/反序列化三个不同关注点混合在一起，每个关注点都有独立变化的理由。
- **复核状态**: 未复核

### [维度02-02] JdbcCheckpointStorage 和 LocalFileCheckpointStorage 存在大量重复的序列化/反序列化代码
- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:333-694` 和 `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/LocalFileCheckpointStorage.java:255-651`
- **证据片段**:
  ```java
  // JdbcCheckpointStorage.java L333-351 和 LocalFileCheckpointStorage.java L255-273
  // 两处几乎完全相同的 serializeCheckpoint() 方法
  private byte[] serializeCheckpoint(CompletedCheckpoint checkpoint) {
      Map<String, Object> serializable = new LinkedHashMap<>();
      serializable.put("jobId", checkpoint.getJobId());
      serializable.put("pipelineId", checkpoint.getPipelineId());
      serializable.put("checkpointId", checkpoint.getCheckpointId());
      serializable.put("triggerTimestamp", checkpoint.getTriggerTimestamp());
      serializable.put("completedTimestamp", checkpoint.getCompletedTimestamp());
      serializable.put("checkpointType", checkpoint.getCheckpointType().name());
      serializable.put("restored", checkpoint.isRestored());
      Map<String, Object> taskStatesMap = new LinkedHashMap<>();
      for (Map.Entry<TaskLocation, TaskStateSnapshot> entry : checkpoint.getTaskStates().entrySet()) {
          String key = taskLocationToString(entry.getKey());
          taskStatesMap.put(key, entry.getValue());
      }
      serializable.put("taskStates", taskStatesMap);
      return JsonTool.serialize(serializable, false).getBytes(StandardCharsets.UTF_8);
  }
  
  // 同样重复的还有：deserializeCheckpoint(), serializeEpochManifest(), 
  // deserializeEpochManifest(), deserializeTaskStateSnapshot(), 
  // taskLocationToString(), stringToTaskLocation()
  ```
- **严重程度**: P2
- **现状**: JdbcCheckpointStorage（695行）和 LocalFileCheckpointStorage（652行）之间有约 400 行几乎逐行相同的序列化/反序列化代码。6 个方法（serializeCheckpoint、deserializeCheckpoint、serializeEpochManifest、deserializeEpochManifest、deserializeTaskStateSnapshot、taskLocationToString/stringToTaskLocation）被完整复制。
- **风险**: 序列化格式变更时需要同步修改两处，遗漏一处会导致 JDBC 存储和文件存储的 checkpoint 不兼容。已有证据表明两处的 fallback 策略不同（Jdbc 版本在解析失败时使用 `new TaskLocation(jobId, pipelineId, entry.getKey(), 0)` 而 File 版本相同），说明维护者需要手动保持一致。
- **建议**: 提取公共的 `CheckpointSerializer` 类到 nop-stream-core 的 checkpoint 包中，封装所有序列化/反序列化逻辑。JdbcCheckpointStorage 和 LocalFileCheckpointStorage 仅负责存储/读取字节。
- **误报排除**: 不是"看起来不优雅"。两份代码的 fallback 行为已经出现细微差异（Jdbc 版本有 `LOG.warn` 并 fallback，File 版本直接 fallback 无日志），说明维护一致性已经出现问题。
- **复核状态**: 未复核

## 维度总结
模块职责划分整体合理：core 负责引擎抽象、runtime 负责执行时、cep 负责复杂事件处理。最大的结构性问题是 MemoryKeyedStateBackend（1254行，7种内部状态）和两个 CheckpointStorage 实现之间的代码重复（~400行）。无 P0/P1 级别问题。
