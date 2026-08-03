# 53. CDC 深化 + 文件 sink（exactly-once connectors）

> Plan Status: completed
> Last Reviewed: 2026-08-04
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 53; `ai-dev/design/nop-stream/connector-design.md` §5.4（新增）; `ai-dev/design/nop-stream/checkpoint-design.md` §6.4/§12
> Mission: nop-stream-production
> Work Item: 53. CDC 深化 + 文件 sink
> Related: Stage 48（Kafka IMessageService）; Stage 52（`JdbcTwoPhaseCommitSink` 2PC 模式参考）; Stage 49（FLIP-27 Source split 体系）

## Purpose

为 nop-stream 的 CDC source 和文件 sink 补齐 exactly-once 支持：使 `DebeziumCdcSourceFunction` 参与 checkpoint（offset 持久化 + 恢复），并新增一个 exactly-once 文件 sink（temp file + atomic rename + manifest commit）。两个特性共同把 nop-stream 的连接器生态从「at-least-once / 无 checkpoint」推进到「exactly-once 端到端」。

## Current Baseline

经 live 仓库核对（2026-08-04，含独立子 agent 对抗性审查验证）：

- **`DebeziumCdcSourceFunction`**（`nop-stream-connector-debezium/.../DebeziumCdcSourceFunction.java:30`，152 行）：
  - 实现 `DrainableSource<ChangeEvent>`（`DrainableSource<T> extends SourceFunction<T>`），`getSourceConsistency()` 返回 `REPLAYABLE`。
  - `run()`（`:70`）启动 `DebeziumMessageSource` → `DebeziumEngineWrapper`（embedded Debezium engine，`JsonByteArray` 格式）。
  - **不实现 `CheckpointedSourceFunction`**——无 `snapshotState`/`initializeState`，不参与 checkpoint。
  - **`config` 字段为 `transient`**（`:34`：`private transient DebeziumConfig config;`），`readObject`（`:51-57`）恢复为 `new DebeziumConfig()`（空默认值）。跨 JVM kill/恢复后连接信息丢失——**必须修复**。
  - `truncateForDrain()`（`:134`）停止引擎（region failover drain 支持）。
- **`ChangeEventMetadata`**（`nop-message-debezium/.../ChangeEventMetadata.java:17`，166 行）：**只有标量位置字段**（`binlogPosition: Long`、`binlogFile: String`、`lsn: Long`、`commitLsn: Long`），`source` 字段为 `String`（"data"/"ddl"）。**无 Debezium source partition / offset map**——`DebeziumEventConverter` 转换时丢弃了原始 source partition + offset map。
- **`DebeziumMessageSource`**（`:25`，212 行）：纯消息源，`subscribe(Consumer<ChangeEvent>)` + `stop()`，**无 offset 暴露 API**。
- **`DebeziumEngineWrapper`**（`:27`，161 行）：`DebeziumEngine.create(JsonByteArray.class).using(props).notifying(...).build()`，offset storage 由 Debezium Properties 配置，**无自定义 OffsetBackingStore**。
- **`DebeziumConfig`**（`:19`，276 行）：POJO，含 `offsetStoragePath: String`（`:78`）、`offsetFlushInterval: Duration`（`:83`）。**未实现 `Serializable`**。`DebeziumEngineConfig.buildProperties` 将 `offsetStoragePath` 映射为 Kafka Connect 属性 `offset.storage=org.apache.kafka.connect.storage.FileOffsetBackingStore` + `offset.storage.file.filename=<path>`（FQCN，非 `file`）。
- **`CheckpointedSourceFunction<T>`**（`nop-stream-core/.../source/CheckpointedSourceFunction.java:21`）：`snapshotState(long checkpointId) → OperatorSnapshotResult` + `initializeState(TaskStateSnapshot state)`。**注意类型不对称**：snapshot 写入 `OperatorSnapshotResult`（经 `putOperatorState(String name, Object state)`），restore 从 `TaskStateSnapshot`（经 `getOperatorState(String name)`）读取。Javadoc 标注「API 预留，当前未被使用」但 `StreamSourceOperator.snapshotState:308-314` + `restoreState:333-344` 实际调用（P2-4 audit finding）。
- **文件 sink 不存在**：全仓库零 `*FileSink*` 命中。已有 sink：`MessageSinkFunction`（at-least-once）、`JdbcTwoPhaseCommitSink`（exactly-once，Stage 52）。
- **`TwoPhaseCommitSinkFunction<IN>`**（`nop-stream-core/.../sink/TwoPhaseCommitSinkFunction.java`）：抽象类，`pendingCommits: Map<Long, Object>`（值须 `Serializable`）。`JdbcTwoPhaseCommitSink`（Stage 52）证明 2PC 接线模式可行（`StreamSinkOperator.processBarrier` → `prepareCommit` → `CheckpointCoordinator.notifyParticipantsFinishCommit` 逆拓扑序 `finishCommit`）。
- **`FileSource`**（`nop-stream-connector/.../file/FileSource.java:42`）：Stage 49 落地的 bounded source。本 plan 的文件 sink 是独立 sink 端，不依赖 FileSource。`nop-stream-connector/pom.xml` 仅依赖 `nop-stream-core`（无 `nop-dao`），已有 `file/` 子包。

### 真正剩余的 gap

- CDC source 无 checkpoint 集成——kill/恢复后消费位点丢失。
- `ChangeEventMetadata` 无 raw Debezium offset 数据——CDC checkpoint 集成的前置。
- `DebeziumCdcSourceFunction.config` 为 transient——跨恢复连接信息丢失。
- 无 exactly-once 文件 sink。

## Goals

- **CDC checkpoint 集成**：`DebeziumCdcSourceFunction` 实现 `CheckpointedSourceFunction<ChangeEvent>`，在 checkpoint 时持久化 Debezium 消费位点，恢复时从 checkpoint 位点重建 Debezium 引擎。
- **exactly-once 文件 sink**：新增 `FileTwoPhaseCommitSink`，通过 temp file + atomic rename + per-epoch manifest 实现 exactly-once 文件输出。kill/恢复后无重复无丢失。
- 两个特性各有独立 E2E 验证。

## Non-Goals

- **CDC schema evolution / DDL 变更传播**——属 Debezium 自身能力。
- **文件 sink 的滚动策略（rolling policy）**——v1 为 per-checkpoint-epoch 单文件；滚动为 successor。
- **文件 sink 的格式插件体系（format SPI）**——v1 固定 text-line。
- **Kafka exactly-once sink（Kafka txn producer）**——successor。
- **BroadcastState（Item 36）**——blocked by vision §七 决策。

## Scope

### In Scope

**CDC deepening（Phase 1）：**
- `nop-message-debezium`：新增 `NopStreamOffsetBackingStore`（implements Debezium `OffsetBackingStore`）；`DebeziumMessageSource`/`DebeziumEngineWrapper` 构造器 overload 接受 custom store；`DebeziumConfig` 实现 `Serializable`。
- `DebeziumCdcSourceFunction` 实现 `CheckpointedSourceFunction<ChangeEvent>`；修复 `transient config`；持有 + 注入 `NopStreamOffsetBackingStore`。

**File sink（Phase 2）：**
- 新增 `FileTwoPhaseCommitSink`（extends `TwoPhaseCommitSinkFunction<IN>`），放入 `nop-stream-connector/file/`（v1 NIO-only，无额外依赖）。

### Out Of Scope

- Debezium schema evolution / DDL。
- 文件 sink 滚动策略、format 插件体系。
- Kafka txn producer / Pulsar txn sink。
- BroadcastState（vision gated）。

## Execution Plan

### Phase 1 - CDC checkpoint offset 集成

Status: completed
Targets: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/NopStreamOffsetBackingStore.java`（新建）; `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumConfig.java`（implements Serializable）; `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java`（构造器 overload）; `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java`（构造器 overload）; `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java`; `nop-stream/nop-stream-connector-debezium/src/test/`; `ai-dev/design/nop-stream/connector-design.md` §5.4

- Item Types: `Decision`、`Fix`、`Proof`

- [x] **D1（CDC offset 持久化策略裁定）**：裁定使用**自定义 `NopStreamOffsetBackingStore`**（implements Kafka Connect `OffsetBackingStore` SPI），由 in-memory `ConcurrentHashMap<ByteBuffer, ByteBuffer>` 支撑。理由：
  - `FileOffsetBackingStore` 格式是 Kafka Connect 内部序列化（schema envelope + ByteBuffer），手工写文件易出错且格式可能随版本变化。
  - 自定义 `OffsetBackingStore` 直接被 Debezium Engine 使用，source function 可在 restore 时 pre-populate、snapshot 时读取——无需文件格式兼容。
  - **`ChangeEventMetadata` 不扩展**：`io.debezium.engine.ChangeEvent<byte[],byte[]>` 只暴露 `key()`/`value()`，不含 partition/offset map。获取 raw offset 需迁移到 `ChangeEventWithMetadata` + `ChangeConsumer` API（非 trivial），超出 v1 scope。offset 持久化**完全由 `NopStreamOffsetBackingStore` 承担**（Debezium engine 内部 commit offset → store），不从事件元数据提取。`ChangeEventMetadata` 扩展为 successor（Deferred）。
  - `NopStreamOffsetBackingStore` SPI 契约：实现 `start()`/`stop()`/`configure(WorkerConfig)`/`get(Collection<ByteBuffer>) → Future<Map<ByteBuffer,ByteBuffer>>`/`set(Map<ByteBuffer,ByteBuffer>, Callback<Void>) → Future<Void>`。额外提供 `setOffsets(Map)`（restore pre-populate）+ `getOffsets()`（snapshot 读取）helper API。
  - **接线链路**（3 层）：`DebeziumCdcSourceFunction` 创建/持有 `NopStreamOffsetBackingStore`（经 `forConnector(name)`）→ 经 `DebeziumMessageSource` 构造器 overload 传入 → `DebeziumMessageSource.startEngineIfNeeded()` 传给 `new DebeziumEngineWrapper(config, consumer, offsetStore)` → `DebeziumEngineWrapper` 设 `offset.storage = NopStreamOffsetBackingStore.class.getName()` 属性，engine 经反射实例化 + connector-name registry 桥接两个实例共享同一份 data map。**`DebeziumMessageSource` 和 `DebeziumEngineWrapper` 均已修改**（均在 Targets 中）。
  - **Debezium 2.4.0 约束适配**：`DebeziumEngine.Builder`（2.4.0）不暴露 `using(OffsetBackingStore)` 方法，故无法直接注入。实际机制为 engine 从 `offset.storage` FQCN 经反射实例化 store + `configure(WorkerConfig)`，由 `NopStreamOffsetBackingStore` 的静态 connector-name registry 桥接 source function 实例与 engine 实例。裁定写入 `connector-design.md` §5.4。
- [x] 新建 `NopStreamOffsetBackingStore implements OffsetBackingStore`（`nop-message-debezium/engine/`）：`ConcurrentHashMap<ByteBuffer, ByteBuffer>` + SPI 方法（`start/stop/configure(WorkerConfig)/get→Future/set→Future`）+ helper `setOffsets/getOffsets` + 静态 connector-name registry + 序列化辅助 `toSerializable/fromSerializable`（base64 编码，因 `ByteBuffer` 不可序列化）。
- [x] `DebeziumConfig` 实现 `Serializable`（POJO 字段全部 Serializable）。
- [x] 扩展 `DebeziumMessageSource`：新增构造器 overload `DebeziumMessageSource(DebeziumConfig, OffsetBackingStore)` 接受自定义 store，传给 `DebeziumEngineWrapper`。
- [x] 扩展 `DebeziumEngineWrapper`：新增构造器 overload `DebeziumEngineWrapper(DebeziumConfig, Consumer<ChangeEvent>, OffsetBackingStore)`，当 store 非 null 时经 `DebeziumEngineConfig.buildProperties(config, true)` 设 `offset.storage = NopStreamOffsetBackingStore.class.getName()`（不设 `offset.storage.file.filename`，避免与 custom store 冲突）。
- [x] `DebeziumCdcSourceFunction`：
  - 移除 `transient` 修饰符（`config` 改为 `private DebeziumConfig config;`，`DebeziumConfig` 现已 `Serializable`）。
  - 新增实例字段 `private transient NopStreamOffsetBackingStore offsetStore;`（transient——由 checkpoint 恢复重建，非序列化）。
  - 实现 `CheckpointedSourceFunction<ChangeEvent>`。
  - `snapshotState(checkpointId)`：从 `offsetStore.getOffsets()` 读取当前 offset map，经 `NopStreamOffsetBackingStore.toSerializable` 转为 `TreeMap<String,String>`（base64），经 `result.putOperatorState("cdc-offsets", offsetMap)` 存入 `OperatorSnapshotResult`。state key = `"cdc-offsets"`。首次运行（无 store）时存空 map。
  - `initializeState(TaskStateSnapshot state)`：经 `state.getOperatorState("cdc-offsets")` 读取 offset map（首次运行 state 为 null → 创建空 store）。创建 `NopStreamOffsetBackingStore.forConnector(config.getName())` 并 `setOffsets(restoredMap)`，存入实例字段供 `run()` 使用。
  - `run()` 修改：用 `createMessageSource(config, offsetStore)`（protected factory，默认 `new DebeziumMessageSource(config, offsetStore)`）替代 `new DebeziumMessageSource(config)`。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ChangeEventMetadata` **不扩展**（v1 裁定：offset 持久化完全由 `NopStreamOffsetBackingStore` 承担，不从事件元数据提取——见 D1 Deferred）。
- [x] `NopStreamOffsetBackingStore` 编译通过且实现 `OffsetBackingStore` 接口全部 SPI 方法（`start/stop/configure(WorkerConfig)/get→Future/set→Future`）+ helper API（`setOffsets/getOffsets`）（有测试验证 SPI 语义 + helper round-trip——`testNopStreamOffsetBackingStoreSpiRoundTrip`、`testNopStreamOffsetBackingStoreHelperGetSetOffsets`）。
- [x] `DebeziumConfig implements Serializable`（编译期验证 + 有测试验证序列化 round-trip——`testDebeziumConfigSerializable`）。
- [x] `DebeziumCdcSourceFunction` 实现 `CheckpointedSourceFunction<ChangeEvent>` 且 `config` 字段非 `transient`（`instanceof CheckpointedSourceFunction` 为 true + `config` 序列化 round-trip 测试——`testCdcSourceImplementsCheckpointedSourceFunction`、`testConfigSurvivesSerialization`）。
- [x] `snapshotState` 经 `putOperatorState("cdc-offsets", offsetMap)` 写入 `NopStreamOffsetBackingStore.getOffsets()` 的 offset map，`initializeState` 经 `getOperatorState("cdc-offsets")` 读取并 `setOffsets` 到新 store（有 round-trip 测试：snapshot → serialize → deserialize → initializeState → assert `offsetStore.getOffsets()` equals snapshot——`testSnapshotStateStoresOffsetsFromStore`、`testInitializeStateRestoresOffsetsToStore`、`testSnapshotRestoreRoundTrip`）。
- [x] **注入链路连通**：`DebeziumCdcSourceFunction.run()` → `createMessageSource(config, offsetStore)` → `new DebeziumMessageSource(config, offsetStore)` → `new DebeziumEngineWrapper(config, consumer, offsetStore)` → `offset.storage = NopStreamOffsetBackingStore.class.getName()`（有测试验证 3 层注入链路 + registry 桥接——`testOffsetStoreInjectionChain`。Debezium 2.4.0 无 `using(OffsetBackingStore)` API，接线经 FQCN 反射 + connector-name registry，见 D1 适配说明）。
- [x] **端到端验证**：CDC source（mock `DebeziumMessageSource` 经 protected factory method `createMessageSource(config, offsetStore)` 注入 test double）→ collector → checkpoint（验证 `offsetStore.getOffsets()` 非空 → snapshot 写入）→ kill → restore（验证 `initializeState` 从 `"cdc-offsets"` 恢复 offset → `offsetStore.setOffsets` 被调用）→ 源从 checkpoint offset 继续，无重复事件（`testCdcCheckpointKillRecoverNoDuplicates`）。断言：恢复后 `offsetStore.getOffsets()` 与 snapshot 时的 offset map 一致；mock source 在恢复后从恢复 offset 发送事件（证明 offset 被消费方使用）。
- [x] **无静默跳过**：`snapshotState`/`initializeState` 中 null/empty state guard 显式处理（null state → 创建空 store，非 null 但类型不符 → 抛 `StreamException`）。
- [x] 新增功能均有对应测试（Rule #25）：`testNopStreamOffsetBackingStoreSpiRoundTrip`、`testNopStreamOffsetBackingStoreHelperGetSetOffsets`、`testDebeziumConfigSerializable`、`testCdcSourceImplementsCheckpointedSourceFunction`、`testSnapshotStateStoresOffsetsFromStore`、`testInitializeStateRestoresOffsetsToStore`、`testConfigSurvivesSerialization`、`testOffsetStoreInjectionChain`、`testCdcCheckpointKillRecoverNoDuplicates`（+ `testSnapshotRestoreRoundTrip`、`testConnectorNameRegistrySharesDataAcrossInstances`、`testSerializeDeserializeRoundTrip`、`testConfigureWithWorkerConfigBindsToRegistry` 辅助）。
- [x] **Javadoc drift 修复**：`CheckpointedSourceFunction` Javadoc 从「API 预留，当前未被使用」修正为实际被 `StreamSourceOperator.snapshotState/restoreState` 调用 + 类型不对称 API 说明。
- [x] `connector-design.md` §5.4 已记录 D1 裁定（NopStreamOffsetBackingStore 策略 + state key + 类型不对称 API 使用说明 + Debezium 2.4.0 接线约束适配）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - exactly-once 文件 sink

Status: completed
Targets: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java`（新建）; `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FilePendingCommit.java`（新建）; `nop-stream/nop-stream-connector/src/test/java/io/nop/stream/connector/file/`; `ai-dev/design/nop-stream/connector-design.md` §5.5

- Item Types: `Decision`、`Fix`、`Proof`

- [x] **D2（模块放置裁定）**：裁定放入 `nop-stream-connector/file/`（基模块 `nop-stream-connector`，仅依赖 `nop-stream-core`，已有 `FileSource` 在此子包）。v1 仅 text-line + NIO，无额外依赖。当 format SPI 引入时再考虑拆分 `nop-stream-connector-file`。裁定写入 `connector-design.md` §5.5.1。
- [x] 实现 `FileTwoPhaseCommitSink<IN>`（extends `TwoPhaseCommitSinkFunction<IN>`）：
  - `getSinkConsistency()` 返回 `TWO_PHASE_COMMIT`。
  - `invoke(value)`：追加到当前 epoch 的内存缓冲（`List<String>`）。
  - 覆盖 `saveState(epochId)`：**先于** `super.saveState`——把内存缓冲写入 temp file（`{outputDir}/.{epochId}.tmp`，`BufferedWriter`），记录 `pendingCommits[epochId] = new FilePendingCommit(tempPath, recordCount)`，清空内存缓冲，再调 `super.saveState`（参考 Stage 52 JDBC sink 的 saveState-first 模式）。
  - `preCommit(epochId)`：no-op（temp file 已在 saveState 写入）。
  - `commit(epochId)`：从 `pendingCommits[epochId]` 读 `FilePendingCommit`。幂等守卫：先查 manifest，若该 epoch 已记录则跳过（recover-safe 重提交不重复 rename）。否则 `Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE)` + 更新 manifest（**manifest 原子更新**：写 `manifest.properties.tmp` → `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` 到 `manifest.properties`）。成功后从 `pendingCommits` 移除。
  - **final-exists 但 manifest-missing 边缘**：若 `Files.exists(finalPath)` 且 manifest 无记录——说明上次 crash 在 rename 后/manifest 写入前。裁定：**修复 manifest**（补写 entry，跳过 rename），而非报错。理由：rename 已成功，数据已 durable，只需同步 manifest。
  - `abort(epochId)`：delete `.{epochId}.tmp`（commit 前的 temp file 安全删除）。若 temp file 不存在（已被 rename），no-op。
  - `rollback()`：丢弃当前内存缓冲。
- [x] 实现 `FilePendingCommit implements Serializable`：`String tempPath`（**非 `Path`**——`Path` 不可序列化）+ `int recordCount`。
- [x] manifest 持久化：`manifest.properties`（`Properties` 格式，因 `nop-stream-connector` 无 Jackson databind 依赖），`Map<Long, String>`（epochId → finalPath）。原子更新：写 `manifest.properties.tmp` → `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`。（设计 doc §5.5 记录为 manifest 原子更新；JSON→Properties 为依赖约束下的实现选择，INTENT 一致。）
- [x] 构造器 + 配置（`outputDir: String`、`charset: Charset` 默认 UTF-8）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `FileTwoPhaseCommitSink` 编译通过且 `getSinkConsistency()` 返回 `TWO_PHASE_COMMIT`，被 `CheckpointPlanBuilder` 自动识别为 participant（`testSinkIsCheckpointParticipant` + instanceof `CheckpointParticipant`——`testSinkIsCheckpointParticipant`、`testGetSinkConsistencyReturnsTwoPhaseCommit`）。
- [x] `FilePendingCommit implements Serializable`（`testFilePendingCommitSerializableRoundTrip`——`Path` 不可序列化，确认用 `String`）。
- [x] `commit(epochId)` 执行 `Files.move(ATOMIC_MOVE)` + manifest 原子更新（`testCommitAtomicRenamesAndUpdatesManifest`——断言 final file 存在 + manifest 含 epoch 条目 + manifest 更新经 `.tmp` → `ATOMIC_MOVE`）。
- [x] 幂等 commit：重复 `commit(同一epoch)` 不重复 rename、不报错（`testIdempotentCommitNoDuplicateRename`——manifest 已记录则跳过）。
- [x] **final-exists 但 manifest-missing 边缘**：`testCommitFinalExistsManifestMissingRepairsManifest`——final file 存在但 manifest 无记录时，commit 修复 manifest 而非报错。
- [x] `abort(epochId)`：delete temp file（`testAbortDeletesTempFile`）；temp 不存在时 no-op（`testAbortOnNonExistentTempIsSafe`）。
- [x] **端到端验证**（Anti-Hollow 强制项）：source → `FileTwoPhaseCommitSink`，多 checkpoint，**kill 在 saveState 后/commit 前**（durable-but-uncommitted 窗口），recover 后断言：final 目录中文件总行数 = 源记录数，**无重复无丢失**（`testFileSinkKillRecoverExactlyOnce`——3+2=5 行，无 temp 残留）。
- [x] **接线验证**：sink 在运行时确被 coordinator `finishCommit` 调用（`testCoordinatorFinishCommitDrivesFileCommit`——finishCommit(2,true) → commit epoch 1+2 → atomic rename，all pending cleared）。
- [x] **无静默跳过**：所有 commit/abort 分支显式行为；未实现路径抛异常（constructor null guards `outputDir`、invoke null guard `value`、pendingCommits 类型不匹配抛 `StreamException`）。
- [x] 新增功能均有对应测试（Rule #25）：`testGetSinkConsistencyReturnsTwoPhaseCommit`、`testInvokeBuffersInMemory`、`testSaveStateWritesTempFileAndClearsBuffer`、`testCommitAtomicRenamesAndUpdatesManifest`、`testIdempotentCommitNoDuplicateRename`、`testCommitFinalExistsManifestMissingRepairsManifest`、`testAbortDeletesTempFile`、`testAbortOnNonExistentTempIsSafe`、`testFilePendingCommitSerializableRoundTrip`、`testFileSinkKillRecoverExactlyOnce`、`testCoordinatorFinishCommitDrivesFileCommit`（+ `testSinkIsCheckpointParticipant` 辅助）。
- [x] `connector-design.md` §5.5 已记录 D2（模块放置）+ 行为模型 + 边缘处理（最终设计状态）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] CDC source 参与 checkpoint 且 kill/恢复后无重复消费（E2E 验证——恢复后首条事件 offset > checkpoint offset）。
- [x] 文件 sink exactly-once 输出经 E2E 验证（kill/恢复后无重复无丢失）。
- [x] 幂等 commit 守卫（manifest / offset）经测试验证 recover-safe。
- [x] `DebeziumConfig` 可序列化，`config` 非 transient（跨恢复不丢连接信息）。
- [x] 受影响 owner docs（`connector-design.md` §5.4/§5.5、`source-anchors.md` 新增 CDC checkpoint + file sink 锚点）已同步到 live baseline。
- [x] 不存在被静默降级到 deferred 的 in-scope live defect。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：CDC source 的 `snapshotState`/`initializeState` 在运行时被 `StreamSourceOperator` 调用且 offset 确实恢复到 offset store（`testSnapshotRestoreRoundTrip` + E2E `testCdcCheckpointKillRecoverNoDuplicates` 验证恢复后从 checkpoint offset 继续）；文件 sink 在运行时被 coordinator `finishCommit` 调用且 atomic rename 生效（`testCoordinatorFinishCommitDrivesFileCommit`）；无空方法体/静默跳过（`scan-hollow-implementations.mjs` 三个模块均 0 findings）。
- [x] `./mvnw compile`
- [x] `./mvnw test -pl nop-stream -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### ChangeEventMetadata raw offset 扩展

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: offset 持久化完全由 `NopStreamOffsetBackingStore` 承担（Debezium engine 内部 commit path），不依赖事件元数据。`ChangeEventMetadata.sourcePartition/sourceOffset` 扩展需迁移 `DebeziumEngineWrapper` 到 `ChangeEventWithMetadata` + `ChangeConsumer` API（非 trivial），v1 无需此扩展即可实现 checkpoint/restore。
- Successor Required: `yes`
- Successor Path: 独立 successor plan（当需要 per-event offset 可观测性或测试断言时）

## Non-Blocking Follow-ups

- 文件 sink 滚动策略（按大小/时间切分输出文件）。
- 文件 sink format 插件体系（CSV/JSON/Parquet）。
- Kafka exactly-once sink（txn producer）。
- Pulsar txn sink。
- CDC source offset 经事件元数据追踪的辅助路径（当前由 NopStreamOffsetBackingStore 主路径承担）。

## Closure

Status Note: Stage 53 完成。CDC source 经 `NopStreamOffsetBackingStore`（Debezium 2.4.0 约束适配：`offset.storage` FQCN 反射实例化 + connector-name registry 桥接实例）实现 checkpoint offset round-trip（`"cdc-offsets"` key），`config` 非 transient + `DebeziumConfig implements Serializable` 修复跨恢复连接信息丢失。文件 sink 经 temp file + `Files.move(ATOMIC_MOVE)` + manifest 原子更新实现 exactly-once，幂等 commit 守卫 + final-exists/manifest-missing 边缘修复。两个特性各有 E2E 验证（kill/recover 无重复无丢失）。Debezium 2.4.0 API 与 plan 原假设（`using(OffsetBackingStore)`）不符，已适配为 FQCN 反射 + registry，设计 doc §5.4 记录。
Completed: 2026-08-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（task ses_0367b5c65ffeFdZSYHD1QHLBAB，general 类型，fresh session）
- Audit Session: ses_0367b5c65ffeFdZSYHD1QHLBAB
- Evidence:
  - 每条 Exit Criterion 验证结果（全 PASS，含 file:line 证据）：
    - Phase 1: `NopStreamOffsetBackingStore` 全 SPI + helper（:47/:118/:97/:138/:153/:169/:177/:193 + base64 序列化 :215/:230）；`DebeziumConfig implements Serializable`（:24）；`DebeziumCdcSourceFunction` `CheckpointedSourceFunction` + config 非 transient（:62）+ `"cdc-offsets"` key（:56）+ 非空 snapshotState/initializeState；Javadoc drift 修复（:18-25，live 验证 StreamSourceOperator :308/:333 调用）；injection chain 经 registry 桥接；E2E `testCdcCheckpointKillRecoverNoDuplicates` 验证恢复后无重复。
    - Phase 2: `FileTwoPhaseCommitSink` 2PC（:64/:110）+ saveState-first（:131-142）+ ATOMIC_MOVE（:185）+ manifest 原子更新（:194）+ 幂等守卫（:169-172）+ final-exists/manifest-missing 修复（:174-181）；`FilePendingCommit implements Serializable`（:19，String tempPath）；E2E `testFileSinkKillRecoverExactlyOnce`（3+2=5 行无重复无丢失）；接线 `testCoordinatorFinishCommitDrivesFileCommit`。
  - 每条 Closure Gate 验证结果（全 PASS）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` → 退出码 0（无未勾选项 + Closure Evidence 已写入）。
  - Anti-Hollow 检查结果：snapshotState/initializeState 非空方法体（real logic）；commit 无 silent no-op（type-mismatch 抛异常、ATOMIC_MOVE 失败抛异常）；无 TODO/FIXME；`scan-hollow-implementations.mjs` 三个模块（nop-stream-connector/nop-message-debezium/nop-stream-connector-debezium）均 0 findings。
  - Deferred 项分类检查：`ChangeEventMetadata raw offset 扩展` 为 `out-of-scope improvement`（successor required: yes），无 in-scope live defect 被降级。
  - 测试结果：`./mvnw test -pl nop-message/nop-message-debezium,nop-stream/nop-stream-connector,nop-stream/nop-stream-connector-debezium -am -T 1C` → 27 新增 tests 全绿；`./mvnw test -pl nop-stream -am -T 1C` → 全模块 BUILD SUCCESS。

Follow-up:

- `ChangeEventMetadata` 携带 raw Debezium source partition/offset map（successor plan，需迁移 `ChangeEventWithMetadata` + `ChangeConsumer` API）。
- 文件 sink 滚动策略（按大小/时间切分）。
- 文件 sink format SPI（CSV/JSON/Parquet）。
- Kafka exactly-once sink（txn producer）/ Pulsar txn sink。
- 当 Debezium 版本升级暴露 `using(OffsetBackingStore)` API 时，简化 `NopStreamOffsetBackingStore` 的 registry 桥接。
