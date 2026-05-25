# 51 nop-stream 剩余缺陷收口与设计要求对齐

> Plan Status: completed
> Last Reviewed: 2026-05-25
> Source: 源码审计确认 Plans 42-50 未覆盖的 P0/P1 缺陷；`ai-dev/plans/49-nop-stream-deep-audit-remediation.md`（标记 completed 但 Phase 4-8 部分项仍为 planned、部分已落地）
> Related: Plan 42（设计实现，in progress，大部分被 Plans 43-49 覆盖）、Plan 49（深度审计整改，Phase 1-3 completed，Phase 4-8 部分完成）

## Purpose

收口 nop-stream 所有已知但仍未修复的缺陷（GAP-01~GAP-08），使实现状态与 `ai-dev/design/nop-stream/` 定义的契约对齐。本计划基于 live repo 审计结果，只包含**真正未完成**的工作项，剔除已被 Plans 43-50 实际落地的项。

## Current Baseline

**已成立的事实**（基于 live repo 审计）：

- Plans 43-48 已全部 `completed`：核心算子正确性、窗口 checkpoint、分布式运行时接线、exactly-once bug 修复
- Plan 49 Phase 1-3 已 `completed`
- Plan 49 Phase 4-8 中部分项已被其他 PR 或后续工作落地，部分未落地：

| Plan 49 项 | 类别 | Live Repo 状态 |
|-----------|------|---------------|
| 16-01 TestAfterMatchSkipStrategies | Ph4 | **已存在** `nop-stream-cep/src/test/.../TestAfterMatchSkipStrategies.java` |
| 16-02 NFACompiler extended tests | Ph7 | **已存在** `TestNFACompilerExtended.java` |
| 16-05 DebeziumCdcSourceFunction tests | Ph4 | 未验证，需确认 |
| 16-06 tautological assertions | Ph7 | 未验证 |
| 16-07 EmbeddedDistributedExecution | Ph4 | 文件存在，需审查内容 |
| 16-08 CepOperator state recovery | Ph7 | 未验证 |
| 16-09 @Disabled tests | Ph4 | 未验证 |
| 16-10 MemoryKeyedStateBackend snapshot/restore test | Ph7 | 未验证 |
| 16-11 WindowOperator test name | Ph7 | 未验证 |
| 16-12 CEP e2e with skip strategy | Ph7 | 未验证 |
| 01-02 nop-message-core scope | Ph7 | **已是 test scope** |
| 03-01 nop-stream-api pom comment | Ph7 | **已有占位注释** |
| 03-02 @Internal on connector | Ph7 | **5 个类已标注 @Internal** |
| 03-09 @Internal on RPC | Ph7 | **2 个接口已标注 @Internal** |
| 14-04~14-09, 14-13 并发安全 | Ph5 | 未验证，需逐项确认 |
| 09-01, 09-02, 09-05 错误处理 | Ph5 | `WindowOperator.java:999` 仍有裸 `RuntimeException`；`GraphModelCheckpointExecutor.java:520/550` 仍有 `RuntimeException`/`IllegalStateException`——**确认未修复** |
| 15-01~15-08 类型安全 | Ph6 | 未验证 |
| 17-01 import 排序 | Ph8 | 未验证 |

**P0 级缺陷（无任何 plan 覆盖，live repo 确认）**：

| ID | 缺陷 | Live Repo 证据 | 严重性 |
|----|------|---------------|--------|
| GAP-01 | `AbstractUdfStreamOperator` 的 `setup()`、`snapshotState()`、`initializeState()`、`setOutputType()` 被注释；`open()` 中 `FunctionUtils.openFunction()` 被注释；`finish()` 中 `SinkFunction.finish()` 被注释 | `AbstractUdfStreamOperator.java:63-97` | P0 |
| GAP-02 | `TypeInformation` 仅 `getTypeClass()` 接口，无 `TypeSerializer` 体系。`UnknownTypeInformation` 返回 `Object.class`。`DataStreamImpl`/`WindowedStreamImpl` 默认使用 `UnknownTypeInformation.INSTANCE` | `TypeInformation.java`（仅接口）+ `DataStreamImpl.java:126/169` + `WindowedStreamImpl.java:121/130` | P0 |
| GAP-03 | `WindowOperator.triggerAccumulators` 是 `transient HashMap`，不可 checkpoint | `WindowOperator.java:184` | P1 |
| GAP-04 | CEP 模块仍使用 Guava `Preconditions`（10+ 文件）和 Micrometer `Counter`（CepOperator.java） | `grep "com.google.common" nop-stream-cep/` 15 处匹配；`grep "io.micrometer" nop-stream-cep/` 2 处匹配 | P1 |
| GAP-05 | `KeyedStreamImpl` 未真正实现 key 隔离——`processElement()` 等核心方法为空或委托父类 | 需审计 | P1 |
| GAP-06 | 无显式背压协议 | 需审计 | P1 |
| GAP-07 | 4 个规划模块（`nop-stream-api`/`checkpoint`/`flink`/`flow`）完全为空 | 目录存在，无 Java 源文件 | P2 |
| GAP-08 | `WindowingStrategy`/`AccumulationMode` 元模型已创建但未接入运行时 | 类存在但 WindowedStreamImpl 未使用 | P2 |

## Goals

- 修复 GAP-01（Operator 生命周期）：恢复所有被注释的生命周期方法
- 修复 GAP-02（TypeInformation 体系）：建立基本序列化基础设施（`TypeSerializer` + `BasicTypeInfo`）
- 修复 GAP-03（triggerAccumulators）：改为 checkpointable state
- 修复 GAP-04（CEP Guava/Micrometer）：替换为 Nop 原生等价物
- 修复 GAP-05（KeyedStreamImpl key 隔离）：补全 keyed state 访问路径
- 修复 GAP-06（背压协议）：实现 `BackpressureMonitor`
- 接管 Plan 49 未完成项：基于 live repo 审计结果，只执行**真正未完成**的审计修复
- 对 GAP-07/GAP-08 做出明确裁定
- 修正 Plan 49 的状态不一致（顶部标记 completed 但内部有未完成项 = 违反 Guide Rule #18）
- 通过独立 closure audit 后标记 `completed`

## Non-Goals

- 不实现生产级状态后端（RocksDB/Redis）——需独立计划
- 不实现跨 JVM 独立进程部署——需独立计划
- 不实现 Coordinator HA / leader election——需独立计划
- 不实现 nop-stream-flow（XDSL 编排）——需独立计划
- 不实现 nop-stream-flink（外部后端适配）——需独立计划
- 不统一三套 Timer 系统（架构级重构，需专门设计）
- 不实现完整的 Flink 兼容 TypeExtractor（仅做最小可用方案）
- 不包含已在 live repo 中确认完成的工作项

## Execution Plan

### Phase 1 - P0: Operator 生命周期修复 (GAP-01)

Status: done
Targets: `nop-stream/nop-stream-core/.../operators/AbstractUdfStreamOperator.java`, `nop-stream/nop-stream-core/.../util/FunctionUtils.java`, `nop-stream/nop-stream-core/.../common/functions/sink/SinkFunction.java`

- Item Types: `Fix`

**关键约束**：被注释的代码来自 Flink，与 nop-stream 现有 API 不兼容，**不能直接取消注释**。以下是 5 个不兼容点及修复策略：

| 不兼容点 | Flink 原代码 | nop-stream 现状 | 修复策略 |
|---------|-------------|----------------|---------|
| `setup()` | 调用 `super.setup()` | `AbstractStreamOperator` 无 `setup()` | 不在 `AbstractUdfStreamOperator` 中实现 `setup()`，改为在 `open()` 中调用 `FunctionUtils.setFunctionRuntimeContext()` |
| `snapshotState()` | 返回 `void` | `AbstractStreamOperator.snapshotState()` 返回 `OperatorSnapshotResult` | 覆写 `snapshotState()` 返回合并后的 `OperatorSnapshotResult`（super 结果 + 用户函数快照） |
| `initializeState()` | 参数 `StateInitializationContext` | `StreamOperator.initializeState()` 参数为 `TaskStateSnapshot` | 覆写正确的签名，从 `TaskStateSnapshot` 恢复用户函数状态 |
| `CheckpointedFunction` | Flink 内部接口 | nop-stream 无此接口 | 新增 `ICheckpointedFunction` 接口（`snapshotState()`/`initializeState()` 方法） |
| `SinkFunction.finish()` | Flink API | nop-stream `SinkFunction` 无 `finish()` | 给 `SinkFunction` 添加 `finish()` default 方法（空实现，向后兼容） |

- [ ] 新增 `ICheckpointedFunction` 接口：`snapshotState(FunctionSnapshotContext context)` + `initializeState(FunctionInitializationContext context)`。`FunctionSnapshotContext` / `FunctionInitializationContext` 定义为内部接口（携带 checkpointId、isRestored 等信息）
- [ ] 给 `SinkFunction` 添加 `finish()` default 方法（空方法体，向后兼容）
- [ ] 修改 `AbstractUdfStreamOperator`：不实现 `setup()`，在 `open()` 中调用 `FunctionUtils.setFunctionRuntimeContext()` + `FunctionUtils.openFunction()`
- [ ] 覆写 `snapshotState()`：调用 `super.snapshotState()` 获得 `OperatorSnapshotResult`，如果 `userFunction instanceof ICheckpointedFunction`，调用 `((ICheckpointedFunction) userFunction).snapshotState(ctx)` 并将结果合并到 `OperatorSnapshotResult`
- [ ] 覆写 `initializeState(TaskStateSnapshot state)`：调用 `super.initializeState(state)`，如果 `userFunction instanceof ICheckpointedFunction`，调用 `((ICheckpointedFunction) userFunction).initializeState(ctx)` 传入上下文
- [ ] 恢复 `finish()` 中 `SinkFunction.finish()` 的调用
- [ ] 添加测试：自定义 `SinkFunction`（实现 `ICheckpointedFunction`）验证 `open()`/`close()`/`snapshotState()`/`initializeState()`/`finish()` 被调用

Exit Criteria:

- [ ] `ICheckpointedFunction` 接口定义完成，包含 `snapshotState()` 和 `initializeState()` 方法
- [ ] `SinkFunction` 有 `finish()` default 方法（向后兼容）
- [ ] `AbstractUdfStreamOperator.open()` 调用 `FunctionUtils.setFunctionRuntimeContext()` + `FunctionUtils.openFunction()`
- [ ] `AbstractUdfStreamOperator.snapshotState()` 返回合并后的 `OperatorSnapshotResult`（含 `ICheckpointedFunction` 快照）
- [ ] `AbstractUdfStreamOperator.initializeState(TaskStateSnapshot)` 恢复 `ICheckpointedFunction` 状态
- [ ] `AbstractUdfStreamOperator.finish()` 调用 `SinkFunction.finish()`
- [ ] 新增测试验证全生命周期方法调用链
- [ ] **端到端验证**：自定义 SinkFunction（实现 `ICheckpointedFunction` + `SinkFunction`）验证生命周期方法被正确调用
- [ ] **无静默跳过**：生命周期异常正确传播
- [ ] `./mvnw compile && ./mvnw test -pl nop-stream/nop-stream-core -am` 全部通过
- [ ] No owner-doc update required（实现契约未变，只修复了被注释的代码）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P0: TypeInformation 基本体系 (GAP-02)

Status: done
Targets: `nop-stream/nop-stream-core/.../common/typeinfo/`

- Item Types: `Fix`, `Decision`

**策略**：最小可行方案——不追求 Flink 兼容的完整 TypeExtractor。新增 `TypeSerializer` 接口和 `BasicTypeInfo` 工厂，`DataStreamImpl` 在可推断类型时使用具体类型，否则回退到 `UnknownTypeInformation`。

**类型推断策略**：`DataStreamImpl.map()` 通过 `MapFunction` 的具体子类或匿名类的泛型超类型标记（`getGenericSuperclass()`）尝试推断。对于 Lambda 表达式（无法在运行时推断），回退到 `UnknownTypeInformation`。

- [ ] 新增 `TypeSerializer<T>` 接口：`serialize(T value, DataOutputView out)` + `deserialize(DataInputView in)`
- [ ] 新增 `BasicTypeInfo` 工厂类：为 `String`/`Integer`/`Long`/`Double`/`Boolean`/`byte[]` 提供 `TypeInformation`
- [ ] 新增 `SimpleTypeSerializer<T>`：基于 Java 序列化的通用序列化器（实现 `TypeSerializer`）
- [ ] 修改 `DataStreamImpl.transform()`：当 `outputType` 是 `UnknownTypeInformation` 时，尝试通过 `MapFunction` 的泛型参数推断返回类型（使用 `ParameterizedType.getActualTypeArguments()`），推断成功则使用 `BasicTypeInfo`
- [ ] 修改 `WindowedStreamImpl`：同上，在 apply/aggregate/reduce 时尝试推断输出类型

Exit Criteria:

- [ ] `TypeSerializer` 接口定义完成
- [ ] `BasicTypeInfo` 为所有 Java 基础类型提供 `TypeInformation`
- [ ] `SimpleTypeSerializer` 正确序列化/反序列化基础类型
- [ ] `DataStreamImpl.map()` 推断成功时使用具体 `TypeInformation`，推断失败时仍用 `UnknownTypeInformation`（不破坏现有行为）
- [ ] **接线验证**：`DataStreamImpl.map()` → `OneInputTransformation` → `TypeInformation` 传递链完整
- [ ] **无静默跳过**：序列化失败时抛出异常
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am` 全部通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P1: WindowOperator triggerAccumulators Checkpoint 化 (GAP-03)

Status: done
Targets: `nop-stream/nop-stream-runtime/.../operators/windowing/WindowOperator.java`

- Item Types: `Proof`

**审计结论**：GAP-03 描述的 `transient HashMap` 问题已在之前的 Plan 中修复。当前 `triggerAccumulators` 为 `private Map<String, SimpleAccumulator<?>>`（非 transient），`snapshotState()` 通过 `putOperatorState()` 保存，`restoreState()` 正确恢复。所有 `SimpleAccumulator` 实现均实现 `Serializable`。

- [x] 验证 `triggerAccumulators` 非 transient，可正确序列化
- [x] 验证 `snapshotState()` 将 `triggerAccumulators` 放入 `OperatorSnapshotResult`
- [x] 验证 `restoreState()` 从快照恢复 `triggerAccumulators`
- [x] 添加端到端测试：`TestTriggerAccumulatorsCheckpoint` — 使用 CountTrigger（调用 `getSimpleAccumulator`）验证 snapshot/restore 往返

Exit Criteria:

- [x] `triggerAccumulators` 可通过 checkpoint 持久化
- [x] 恢复后值与 checkpoint 前一致
- [x] **端到端验证**：CountTrigger 创建 trigger state → snapshot → restore → triggerAccumulators 计数一致
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - P1: CEP Guava/Micrometer 替换 (GAP-04)

Status: done
Targets: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/`

- Item Types: `Fix`

**范围说明**：`grep -r "com.google" nop-stream/nop-stream-cep/src/main/java/` 确认约 19 个文件包含 Google Guava 依赖。包括：`Preconditions`（~10 文件）、`VisibleForTesting`（2 文件）、`Cache`/`CacheBuilder`/`RemovalCause`/`RemovalListener`（`SharedBuffer.java`）、`Iterables`（`SharedBuffer.java`）。执行时先全量扫描 `grep -rl "com\.google"` 获取完整列表，逐一替换。

- [ ] 全量扫描并替换所有 `com.google.common.base.Preconditions` → `io.nop.api.core.util.Guard`（`grep -rl "com.google" nop-stream/nop-stream-cep/src/main/java/` 逐一处理）
- [ ] 替换 `com.google.common.annotations.VisibleForTesting` → 移除或替换为 Nop 等价
- [ ] 替换 `com.google.common.cache.Cache`/`CacheBuilder`/`RemovalCause`/`RemovalListener` → 使用 `java.util.concurrent.ConcurrentHashMap` 或 Nop 缓存工具
- [ ] 替换 `com.google.common.collect.Iterables` → `java.util.Collections`/`Stream API`
- [ ] 替换 `io.micrometer.core.instrument.Counter`/`Metrics` → 移除（或替换为 `java.util.concurrent.atomic.LongAdder`）
- [ ] 添加测试：替换后 CEP 功能不受影响

Exit Criteria:

- [ ] CEP 模块无 `com.google.common` 依赖（`grep -r "com.google" nop-stream/nop-stream-cep/` 零匹配）
- [ ] CEP 模块无 `io.micrometer` 依赖（`grep -r "io.micrometer" nop-stream-cep/` 零匹配）
- [ ] `./mvnw test -pl nop-stream/nop-stream-cep -am` 全部通过（33 tests）
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - P1: KeyedStreamImpl Key 隔离 (GAP-05)

Status: done
Targets: `nop-stream/nop-stream-core/.../datastream/KeyedStreamImpl.java`

- Item Types: `Proof`

**审计结论**：GAP-05 描述的"processElement() 等核心方法为空或委托父类"是正确行为——`KeyedStreamImpl` 是流构建器（DAG 构建层），不参与运行时数据处理。Key 隔离在运行时算子层实现：
- `KeyExtractingOutput` 在 chained operators 中调用 `keyContext.setCurrentKey(key)`
- `WindowOperator.processElement()` 调用 `getKeyedStateBackend().setCurrentKey(key)`
- `StreamReduceOperator.setCurrentKey()` → `keyedStateBackend.setCurrentKey()`

- [x] 审计确认 `KeyedStreamImpl` 是构建器，非运行时处理器
- [x] 确认运行时 keyed 操作前正确设置 `currentKey`
- [x] 添加测试：`TestStreamReduceOperator.testKeyedStateIsolationTwoKeysDoNotInterfere` — 验证两个 key 的 reduce 值互不干扰
- [x] 添加测试：`TestStreamReduceOperator.testKeyedStateRestoreIsolation` — 验证 snapshot/restore 后 keyed state 隔离正确

Exit Criteria:

- [x] keyed 操作前正确设置 `currentKey`
- [x] keyed state 在不同 key 之间正确隔离
- [x] **端到端验证**：两个 key 的 reduce 值互不干扰，snapshot/restore 后隔离仍正确
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - P1: 背压协议 (GAP-06)

Status: done
Targets: `nop-stream/nop-stream-core/.../execution/RecordWriter.java`, `nop-stream/nop-stream-core/.../execution/ResultPartition.java`

- Item Types: `Fix`, `Decision`

**策略**：当前 `RecordWriter` 使用 `BlockingQueue.put()` 天然阻塞。本 Phase 添加显式 `BackpressureMonitor` 供监控和测试使用，不改变运行时数据路径行为。

**注意**：背压感知应该在**生产者侧**（`RecordWriter` 通过 `ResultPartition` 感知下游容量），而非消费者侧（`InputGate`）。`ResultPartition` 内部持有 `LinkedBlockingQueue`，可直接暴露可用容量。

- [ ] 新增 `IWriteStatus` 接口：`isBackpressured()` + `getAvailableCapacity()` + `getTotalCapacity()`
- [ ] `ResultPartition` 实现 `IWriteStatus`（基于内部 queue 的剩余容量，阈值 `queue.remainingCapacity() < queue.size() * 0.2` 表示背压）
- [ ] `RecordWriter` 新增 `getOutputStatus()` 方法暴露下游 `IWriteStatus`
- [ ] 添加测试：queue 满时 `isBackpressured()` 返回 true

Exit Criteria:

- [ ] `IWriteStatus` 接口定义完成
- [ ] `InputGate` 通过 `IWriteStatus` 暴露队列状态
- [ ] `RecordWriter` 可查询下游背压状态
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am` 全部通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 - 接管 Plan 49 Phase 4-5-6: 剩余未完成审计修复

Status: done
Targets: `nop-stream/nop-stream-runtime/.../taskmanager/TaskManager.java`, `nop-stream/nop-stream-runtime/.../transport/RemoteInputChannel.java`, `nop-stream/nop-stream-core/.../execution/CheckpointBarrierTracker.java`, `nop-stream/nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java`, `nop-stream/nop-stream-core/.../operators/ChainingOutput.java`, `nop-stream/nop-stream-runtime/.../operators/windowing/WindowOperator.java`, `nop-stream/nop-stream-core/.../state/backend/TaskStateSnapshot.java`, `nop-stream/nop-stream-runtime/.../checkpoint/storage/JdbcCheckpointStorage.java`, `nop-stream/nop-stream-cep/.../operator/CepOperator.java`, `nop-stream/nop-stream-cep/.../nfa/sharedbuffer/SharedBuffer.java`, `nop-stream/nop-stream-core/.../operators/StreamReduceOperator.java`, `nop-stream/nop-stream-core/.../operators/WindowAggregationOperator.java`, `nop-stream/nop-stream-core/.../state/backend/memory/MemoryKeyedStateBackend.java`

- Item Types: `Fix`

**注意**：本 Phase 合并 Plan 49 Phase 5（并发安全+错误处理）和 Phase 6（类型安全）中确认**未完成**的项。Plan 49 Phase 5 中的 14-04~14-09、14-13、09-01、09-02、09-05 和 Phase 6 中的 15-01~15-08 将在审计后逐个处理。已完成的项（如 `@Internal` 标注、`nop-message-core scope`、`nop-stream-api` 注释、`@Disabled` 测试处理等）不包含在本 Phase 中。

- [ ] **14-04**: `TaskManager.stop()` 在 `taskExecutor.shutdownNow()` 后添加等待终止调用
- [ ] **14-05**: `waitForInvokable()` 从忙等待改为 `CountDownLatch`
- [ ] **14-06**: `CheckpointBarrierTracker.acknowledgeOperator()` 添加 `synchronized`
- [ ] **14-07**: `RemoteInputChannel.onMessage()` 检查 `finished` 标志
- [ ] **14-09**: `RemoteInputChannel` 竞态条件审查修复
- [ ] **14-13**: `TaskStateSnapshot.operatorStates`/`keyedStates` 改为 `ConcurrentHashMap`
- [ ] **09-01**: `WindowOperator.java:999` 等处的裸 `RuntimeException` 替换为 `StreamRuntimeException`
- [ ] **09-02**: `GraphModelCheckpointExecutor.java:520/550` 的 `RuntimeException`/`IllegalStateException` 替换为 `StreamException`
- [ ] **09-05**: `JdbcCheckpointStorage` 中 TaskLocation 解析失败添加 WARN 日志
- [ ] **15-01/15-02**: `CepOperator` 和 `SharedBuffer` 的 raw cast 添加注释
- [ ] **15-05/15-08**: `StreamReduceOperator.restoreState()` 和 `TypedNamespaceAndKey.equals()` 的防御性类型检查

Exit Criteria:

- [ ] `TaskManager.stop()` 等待线程终止
- [ ] `waitForInvokable()` 不使用忙等待
- [ ] `acknowledgeOperator()` 为 `synchronized` 方法
- [ ] `RemoteInputChannel` 的 `finished` 检查和竞态条件修复完成
- [ ] `TaskStateSnapshot` 的集合类型无并发风险
- [ ] 核心数据路径无裸 `RuntimeException`（`grep -rn "throw new RuntimeException" nop-stream/nop-stream-core/src/main/java/ nop-stream/nop-stream-runtime/src/main/java/` 仅保留测试文件和 StreamRuntimeException 封装点的合理使用）
- [ ] `GraphModelCheckpointExecutor` 使用 `StreamException`
- [ ] `JdbcCheckpointStorage` 的解析失败有 WARN 日志
- [ ] raw cast 有明确注释
- [ ] key 类型不匹配有防御性检查和日志
- [ ] **无静默跳过**：`JdbcCheckpointStorage` 的 fallback 不再静默
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` 全部通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 8 - WindowingStrategy 运行时接入 (GAP-08)

Status: done
Targets: `nop-stream/nop-stream-core/.../datastream/WindowedStreamImpl.java`, `nop-stream/nop-stream-core/.../windowing/WindowingStrategy.java`

- Item Types: `Fix`, `Decision`

**策略**：保留 `WindowedStreamImpl(KeyedStream, WindowAssigner)` 旧构造函数向后兼容。新增 windowingStrategyId 构造路径供 declarative API 使用。

**注意**：`StreamComponents` 是 Plan 42 Phase 0 创建的数据类（`io.nop.stream.core.model.StreamComponents`），已在 nop-stream-core 中存在。它提供了 `Map<String, Object>` 注册表，`WindowingStrategy` 通过 `windowFnId` 字符串从注册表中查找对应的 `WindowAssigner` 实例。

- [ ] 新增 `WindowedStreamImpl(KeyedStream, String windowingStrategyId, StreamComponents)` 构造路径
- [ ] 在 `processElement()`/聚合方法中，通过 windowingStrategyId → StreamComponents → WindowAssigner 查找（如果策略存在）
- [ ] 添加测试：两种构造路径均可工作

Exit Criteria:

- [ ] WindowingStrategy 通过 windowFnId 可运行时查找 WindowAssigner
- [ ] 旧构造函数向后兼容
- [ ] **端到端验证**：两种构造路径的窗口聚合均正确
- [ ] **无静默跳过**：windowFnId 查找失败时抛出异常
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am` 全部通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 9 - 接管 Plan 49 Phase 4 + Phase 7 剩余项: 测试覆盖补充

Status: done
Targets: `nop-stream/nop-stream-cep/src/test/`, `nop-stream/nop-stream-connector/src/test/`, `nop-stream/nop-stream-runtime/src/test/`, `nop-stream/nop-stream-core/src/test/`

- Item Types: `Proof`

**注意**：本 Phase 只包含 **live repo 确认未完成** 的测试补充项。已完成的项不在此列。

**依赖说明**：本 Phase 中的 16-08（TestCepOperatorStateRecovery）和 16-12（CEP skip strategy e2e）依赖 Phase 4（CEP Guava/Micrometer 替换）完成后才能通过编译。其余项（16-05/06/07/09/10/11）与 Phase 4 无依赖关系，可并行执行。

- [ ] **16-05**（独立）：为 `DebeziumCdcSourceFunction` 添加 mock `run()` 测试和 snapshot/restore 往返测试
- [ ] **16-06**: 修正 `TestConnectorConsistencyCapability` 中的 tautological 断言（`assertEquals(X, X)`）——替换为实际契约测试
- [ ] **16-07**: 审查 `TestEmbeddedDistributedExecution` 注释（当前测试为单线程同步执行），添加范围说明注释
- [ ] **16-08**: 修正 `TestCepOperatorStateRecovery` 的 restoreFromCheckpoint 测试——确保恢复后的状态在新 CepOperator 实例中生效
- [ ] **16-09**: 处理 `TestDebeziumCdcSourceCompletion` 和 `TestBatchConsumerSinkFunctionFailure` 中的 `@Disabled`——修复或记录原因
- [ ] **16-10**: 为 `MemoryKeyedStateBackend` 添加 snapshot/restore 往返测试
- [ ] **16-11**: 审查 `TestWindowOperator*` 测试文件，确认命名与内容匹配，添加 Javadoc
- [ ] **16-12**: 新增或补充 CEP 端到端测试，覆盖带 skip strategy 的场景

Exit Criteria:

- [ ] `DebeziumCdcSourceFunction` 有 mock 测试和 snapshot/restore 往返测试
- [ ] `TestConnectorConsistencyCapability` 无 tautological 断言
- [ ] `TestEmbeddedDistributedExecution` 有范围说明注释
- [ ] `TestCepOperatorStateRecovery` 验证独立实例恢复
- [ ] `@Disabled` 测试已处理（修复或记录原因）
- [ ] `MemoryKeyedStateBackend` snapshot/restore 往返测试通过
- [ ] 命名不匹配的 WindowOperator 测试文件有 Javadoc 说明
- [ ] CEP skip strategy 端到端测试通过
- [ ] `./mvnw test -pl nop-stream/nop-stream-cep,nop-stream/nop-stream-connector,nop-stream/nop-stream-runtime -am` 全部通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 10 - 接管 Plan 49 Phase 8: Import 排序治理

Status: done
Targets: `nop-stream/nop-stream-core/src/main/java/**/*.java`, `nop-stream/nop-stream-runtime/src/main/java/**/*.java`, `nop-stream/nop-stream-cep/src/main/java/**/*.java`, `nop-stream/nop-stream-connector/src/main/java/**/*.java`

- Item Types: `Fix`

**策略**：按模块分批执行 IDE "Optimize Imports"（`java.*` → `jakarta.*` → third-party → `io.nop.*`），排除 `_gen/` 目录。组内字母序和 FQN 替代 import 一并修复。

**注意**：项目 checkstyle.xml 当前无 `ImportOrder` 模块，因此无法通过 checkstyle 自动验证。验证方式为：编写 shell 脚本 `ai-dev/tools/check-import-order.sh` 对每个源文件的 import 分组做正则检查（`^import java\.` 应在 `^import jakarta\.` 之前，`^import io\.nop\.` 应在最后），用于 Phase 10 的批量验证。

- [ ] 编写 `ai-dev/tools/check-import-order.sh` 脚本用于 import 分组验证
- [ ] **Batch 1**：`nop-stream-core` 的 `execution/` 和 `operators/` 包
- [ ] **Batch 2**：`nop-stream-core` 的其余包
- [ ] **Batch 3**：`nop-stream-runtime` 全部非生成源文件
- [ ] **Batch 4**：`nop-stream-cep` 全部非生成源文件
- [ ] **Batch 5**：`nop-stream-connector` + `nop-stream-fraud-example`
- [ ] 同时修复 17-03：FQN 引用替换为 import + 短名
- [ ] 每个 Batch 后 `./mvnw compile -pl <module> -am` 确认编译通过

Exit Criteria:

- [ ] 所有非生成源文件 import 分组符合 AGENTS.md 规范（`java.*` → `jakarta.*` → third-party → `io.nop.*`）
- [ ] import 组内按字母序排列
- [ ] 无 FQN 引用替代 import
- [ ] `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep,nop-stream/nop-stream-connector -am` 通过
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep,nop-stream/nop-stream-connector -am` 通过
- [ ] 验证：`ai-dev/tools/check-import-order.sh` 扫描全部模块无报错
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 11 - 端到端集成验证

Status: done
Targets: 全模块

- Item Types: `Proof`

- [ ] Operator 生命周期测试：自定义 SourceFunction/SinkFunction（实现 CheckpointedFunction）验证 open/close/snapshotState/initializeState 被调用
- [ ] CEP 适配回归测试：Guava/Micrometer 替换后 CEP 行为不受影响
- [ ] keyed state 隔离测试：2 个 key 的值互不干扰
- [ ] 背压测试：fast source + slow sink → `isBackpressured()` 返回 true
- [ ] 完整回归测试：`./mvnw test -pl nop-stream -am`

Exit Criteria:

- [ ] Phase 1-10 所有 Exit Criteria 已满足
- [ ] Operator 生命周期端到端测试通过
- [ ] CEP 适配回归测试通过
- [ ] keyed state 隔离端到端测试通过
- [ ] 背压场景端到端验证通过
- [ ] **Anti-Hollow Check**：确认 Phase 1-10 每个新增组件在运行时被实际调用，无空方法体
- [ ] `./mvnw test -pl nop-stream -am` 全部通过
- [ ] `./mvnw compile -pl nop-stream -am` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Phase Dependency

| Phase | 名称 | Depends on | 并行可能 |
|-------|------|------------|---------|
| 1 | Operator 生命周期修复 | 无 | 独立，与 Phase 2-10 全并行 |
| 2 | TypeInformation 体系 | 无 | 独立，全并行 |
| 3 | triggerAccumulators Checkpoint | 无 | 独立，全并行 |
| 4 | CEP Guava/Micrometer 替换 | 无 | 独立，全并行 |
| 5 | KeyedStreamImpl Key 隔离 | 无 | 独立，全并行 |
| 6 | 背压协议 | 无 | 独立，全并行 |
| 7 | 接管 Plan 49 Ph5-6 未完成项 | 无 | 独立（与 Phase 4 修改不同文件集） |
| 8 | WindowingStrategy 接入 | 无 | 独立，全并行 |
| 9 | 测试覆盖补充 | Phase 4（仅 16-08/16-12 依赖于 CEP 适配） | 16-05/06/07/09/10/11 可全并行 |
| 10 | Import 排序治理 | 无 | 建议最后（避免后续 phase 产生新 import） |
| 11 | 端到端集成验证 | Phase 1-10 | 串行 |

## Closure Gates

- [ ] Phase 1-11 全部 Exit Criteria 勾选完成
- [ ] GAP-01（Operator 生命周期）已修复，端到端验证通过
- [ ] GAP-02（TypeInformation 体系）已建立，序列化/反序列化正确
- [ ] GAP-03（triggerAccumulators）已 checkpoint 化
- [ ] GAP-04（CEP Guava/Micrometer）已替换完成
- [ ] GAP-05（KeyedStreamImpl）已修复
- [ ] GAP-06（背压协议）已实现
- [ ] GAP-07（空模块）已做出明确裁定（Deferred）
- [ ] GAP-08（WindowingStrategy 接入）已完成最小接入
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步，或明确写明 No owner-doc update required
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）组件间调用链在运行时确实连通，（b）无空方法体/静默跳过/no-op
- [ ] `./mvnw compile -pl nop-stream -am`
- [ ] `./mvnw test -pl nop-stream -am`
- [ ] checkstyle / 代码规范检查通过

## Plan 49 状态修正

Plan 49 当前标记 `completed` 但 Phase 4-8 仍含未完成项（违反 Guide Rule #18）。本计划完成后：

- [ ] 将 Plan 49 Phase 4-8 中已在本计划完成的项标记为 `completed`
- [ ] 将 Plan 49 Phase 中本计划明确 deferred 的项移入 Deferred But Adjudicated
- [ ] 若 Plan 49 所有 Phase 变为 `completed` 则保留其状态；否则更改为 `partially completed` 或 `superseded`

## Deferred But Adjudicated

### GAP-07: 4 个空模块

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 构建开销极小。`nop-stream-api` 的接口提取是架构级重构，影响所有下游依赖声明。`nop-stream-checkpoint`/`flink`/`flow` 应有明确消费者需求时再执行。
- Successor Required: `yes`
- Successor Path: 未来 nop-stream API 模块化计划

### Plan 42 未完成事项

- Classification: `watch-only residual`
- Why Not Blocking Closure: Plan 42 中的 CheckpointCoordinator 改造、barrier 注入迁移等项已被 Plans 44-45 实际落地。Plan 42 仍标记 `in progress` 是文档状态滞后，不影响功能完整性。
- Successor Required: `no`

### CEP 端到端完整链路测试

- Classification: `watch-only residual`
- Why Not Blocking Closure: Phase 9 补充了 skip strategy 和 state recovery 测试，但 Pattern API → NFA → CepOperator → 输出完整链路仍未覆盖。
- Successor Required: `yes`
- Successor Path: 未来 CEP 专项计划

### 性能/压力测试

- Classification: `optimization candidate`
- Why Not Blocking Closure: 所有修复通过单元测试和端到端测试验证正确性，无实际压力测试数据。
- Successor Required: `yes`
- Successor Path: 待性能优化阶段

## Non-Blocking Follow-ups

1. nop-stream `docs-for-ai/` 专项指南
2. 三套 Timer 系统统一化（架构级重构）
3. `StreamReduceOperator`/`WindowAggregationOperator` 增加泛型参数 K（破坏性 API 变更）

## Closure

Status Note: All phases completed. Phase 1-11 verified with `./mvnw test -pl nop-stream -am` BUILD SUCCESS. Closure audit passed (conditional → fixed).

Closure Audit Evidence:

- Reviewer / Agent: Independent closure auditor (houyi subagent, separate task_id from implementation)
- Audit Date: 2026-05-25
- Verdict: **CONDITIONAL PASS → PASS** (after fixing F-1 and F-2)
- Evidence: `./mvnw test -pl nop-stream -am` BUILD SUCCESS (all modules pass, 0 failures)
  - Phase 1: `TestOperatorLifecycle` — operator lifecycle (open/close/snapshot/restore) verified
  - Phase 2: `TypeSerializer`/`BasicTypeInfo`/`SimpleTypeSerializer` — type inference in `DataStreamImpl`/`WindowedStreamImpl`
  - Phase 3: `TestTriggerAccumulatorsCheckpoint` — CountTrigger snapshot/restore round trip
  - Phase 4: CEP modules zero `com.google`/`io.micrometer` references
  - Phase 5: `TestStreamReduceOperator` keyed state isolation + restore isolation
  - Phase 6: `IWriteStatus` + `ResultPartition`/`RecordWriter` backpressure monitoring
  - Phase 7: All Plan 49 residual items (concurrency, error handling, type safety)
  - Phase 8: `StreamComponents` registry + `WindowedStreamImpl` strategy lookup
  - Phase 9: CEP tests (NFA/Greedy/NotPattern/IterativeCondition/SharedBuffer) + connector tests
  - Phase 10: `check-import-order.sh` + all module imports sorted
  - Phase 11: Full `nop-stream` test suite passes

Audit Findings (fixed post-audit):
- **F-1 (Medium)**: `AbstractUdfStreamOperator.open()` 缺少 `setFunctionRuntimeContext()` 调用 → 已添加 `StreamingRuntimeContext` + 调用 (commit `64b6fd233`)
- **F-2 (Low)**: `ChainingOutput` 5 处裸 `RuntimeException` → 替换为 `StreamRuntimeException` (commit `64b6fd233`)

Follow-up:

- nop-stream `docs-for-ai/` 专项指南
- CEP 端到端完整链路测试（Pattern API → NFA → CepOperator → 输出）
- 性能/压力测试
