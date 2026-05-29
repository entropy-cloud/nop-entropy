# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] Duplicated checkpoint serialization logic across storage implementations

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java` 和 `LocalFileCheckpointStorage.java`
- **证据片段**:
  ```java
  // Both files contain nearly identical:
  // serializeCheckpoint(CompletedCheckpoint)
  // deserializeCheckpoint(byte[])
  // taskLocationToString(TaskLocation)
  // stringToTaskLocation(String)
  // deserializeTaskStateSnapshot(Map, TaskLocation)
  // serializeEpochManifest(EpochManifest)
  // deserializeEpochManifest(byte[])
  ```
- **严重程度**: P2
- **现状**: JdbcCheckpointStorage (695行) 和 LocalFileCheckpointStorage (685行) 包含约 200-250 行几乎逐字复制的序列化/反序列化代码（7个方法）。
- **风险**: Bug修复或 schema 变更必须在两处同步修改。两文件体积过大也部分源于此重复。
- **建议**: 将共享序列化逻辑提取到工具类（如 CheckpointSerDe），两个实现委托给它。
- **误报排除**: 不是微小的代码重复。7个方法约200-250行的完整逻辑重复，是结构性设计问题。
- **复核状态**: 未复核

### [维度02-02] MemoryKeyedStateBackend is oversized (1383 lines) with 7 inner state classes

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java`
- **证据片段**:
  ```java
  // Contains:
  // - Main class with state management (~280 lines)
  // - Snapshot/restore serialization (~320 lines)
  // - 7 private static inner classes: MemoryValueState, MemoryMapState, 
  //   MemoryListState, MemoryReducingState, MemoryAggregatingState, etc. (~580 lines)
  // - 2 helper classes: ShardPrefixedKey, TypedNamespaceAndKey (~80 lines)
  ```
- **严重程度**: P3
- **现状**: 文件包含1383行代码，承载了状态管理、序列化和7种状态类型的完整实现。snapshot/restore 有重复的 switch-on-type 模式。
- **风险**: 可读性和维护性下降。但这不是职责混合——所有代码都属于内存状态后端的单一职责。
- **建议**: 考虑将内部状态类提取为独立文件，将序列化逻辑提取为 MemoryStateSerDe 辅助类。低优先级。
- **误报排除**: 不是职责混合问题。所有代码围绕内存状态后端的单一职责，但文件体积确实过大。
- **复核状态**: 未复核

### 零发现区域

1. **生成文件未手写修改**: _gen/ 下4个文件均由 XDef 自动生成，无手写痕迹。
2. **无跨模块错位代码**: 依赖图单向，无反向依赖。
3. **子模块职责清晰**: core（引擎核心）、runtime（运行时执行）、cep（复杂事件处理）、connector（连接器）、fraud-example（示例）。
4. **算法文件（NFACompiler、NFA、Pattern）体积合理**: 复杂算法实现的正常体量。
