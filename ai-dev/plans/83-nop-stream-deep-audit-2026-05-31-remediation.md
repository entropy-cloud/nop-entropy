# 83 nop-stream 2026-05-31 深度审计修复

> Plan Status: in progress
> Last Reviewed: 2026-05-31
> Source: ai-dev/audits/2026-05-31-deep-audit-nop-stream/summary.md + 8 个维度报告
> Related: 82-nop-stream-round13-audit-remediation (completed)

## Purpose

将 2026-05-31 深度审计的 10 个有效 P2 发现修复到可验证状态（共 11 个 P2，其中 01-01 经独立审查确认审计发现不准确）。审计覆盖 8 个维度（依赖图、模块职责、错误处理、安全、类型安全、测试覆盖、代码风格、测试有效性），共 36 个发现，无 P0/P1 级别问题。核心改进方向：(1) 安全白名单收窄（ClassNameValidator），(2) 类型安全加固（MemoryStateSerDe / MemoryInternalAppendingState / StreamReduceOperator），(3) 测试覆盖盲区填补（4 个核心组件）。

## Current Baseline

- Plan 82 完成后 Round 13 全部 16 个发现已修复，`./mvnw test -pl nop-stream -am` 全量通过
- 2026-05-31 深度审计新发现 36 个（11×P2 + 25×P3），无 P0/P1
- 审计结论：nop-stream 整体质量良好，所有 P2 均为维护成本或防御性加固，不影响当前功能正确运行
- 4 个复核降级项已确认（02-01、09-01、13-01、15-01 从 P2 降至 P3）

### 待修复 P2 发现

| 编号 | 维度 | 文件 | 摘要 |
|------|------|------|------|
| 02-02 | 模块 | GraphModelCheckpointExecutor.java:55-803 | 803 行 7 种职责的静态方法类 |
| 09-02 | 错误 | SubtaskTask.java:147-148 | 字符串构造 StreamException，同文件内不一致 |
| 13-02 | 安全 | ClassNameValidator.java:15-33 | 白名单含 "java." 前缀过宽，存在网络攻击面 |
| 15-02 | 类型 | MemoryStateSerDe.java:131-375 | 反序列化缺运行时类型验证 |
| 15-03 | 类型 | MemoryInternalAppendingState.java:82-92 | IN/ACC 类型混淆，add() 中的不安全强转 |
| 15-04 | 类型 | StreamReduceOperator.java:99-122 | restoreState 反序列化无类型验证 |
| 16-01 | 测试 | GraphModelCheckpointExecutor.java | 核心编排路径缺直接测试 |
| 16-02 | 测试 | TaskManager.java | RunningTask 执行循环和 checkpoint 触发缺测试 |
| 16-03 | 测试 | JobCoordinator.java | DRAIN/SUSPEND 终止模式缺测试 |
| 16-04 | 测试 | MemoryStateSerDe.java | 598 行核心文件无测试 |

### 审计发现更正

- **维度01-01**（nop-message-core scope）经独立审查确认：审计发现**不准确**。`MessageSourceFunction.java` 和 `MessageSinkFunction.java`（main 源码）直接 import 并使用 `IMessageService`（来自 nop-message-core）。`<optional>true</optional>` 是正确的 Maven 语义——connector 模块提供连接器，消费者需自行提供运行时实现。从本 plan scope 中移除

### P3 发现（不在本 plan scope 内）

25 个 P3 发现涉及代码风格（通配符 import、Logger 声明）、测试有效性（低价值测试、assertNotNull 过多）、模块职责（两套窗口实现、废弃代码）、依赖文档等，均归入 Non-Blocking Follow-ups。

## Goals

- 修复 10 个 P2 发现中的 9 个（02-02 延期，01-01 审计发现不准确已移除）
- 安全白名单收窄：ClassNameValidator 的 "java." 前缀改为实际需要的子包
- 类型安全加固：MemoryStateSerDe / MemoryInternalAppendingState / StreamReduceOperator 增加运行时类型检查
- 测试覆盖填补：为 GraphModelCheckpointExecutor / TaskManager / JobCoordinator / MemoryStateSerDe 添加核心路径测试
- 错误码规范化：SubtaskTask.openOperatorChains 改用 ErrorCode
- 依赖修正：~~nop-message-core scope 改为 test~~（已确认审计发现不准确，移除）
- 每个修复完成后 `./mvnw test -pl nop-stream -am` 全部通过

## Non-Goals

- P3 发现修复（25 项）
- GraphModelCheckpointExecutor 大规模重构（02-02 的完整修复需提取 2 个子类，属于架构变更）
- WindowAggregationOperator 与 WindowOperator 统一
- CEP ClosureCleaner 等效机制
- fraud-example 端到端验证
- 全模块 import 风格统一

## Scope

### In Scope

- nop-stream-core: ClassNameValidator、SubtaskTask、MemoryStateSerDe、MemoryInternalAppendingState、StreamReduceOperator
- nop-stream-runtime: GraphModelCheckpointExecutor、TaskManager、JobCoordinator
- 对应新增/修改测试

### Out Of Scope

- P3 发现
- GraphModelCheckpointExecutor 类拆分重构
- 架构级重构
- fraud-example 模块

## Execution Plan

### Phase 1 - 安全与错误处理修复（13-02, 09-02）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/util/ClassNameValidator.java`, `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/SubtaskTask.java`

- Item Types: `Fix`

- [x] **13-02**: 收窄 ClassNameValidator 的 "java." 前缀。已完成：(1) 替换为6个安全子包前缀；(2) 新增 validateAccumulatorClass 方法；(3) MemoryStateSerDe 和 WindowAggregationOperator 已更新调用点
- [x] **09-02**: SubtaskTask.openOperatorChains 已改用 ERR_STREAM_INIT_ERROR 错误码

Exit Criteria:

- [x] ClassNameValidator 不再包含裸 `"java."` 前缀，替换为六个子包前缀；新增 `validateAccumulatorClass()`；新增5个测试覆盖安全验证
- [x] SubtaskTask.openOperatorChains 使用 ErrorCode 而非字符串构造 StreamException
- [x] `./mvnw test -pl nop-stream -am` 全部通过（858 tests passed）
- [x] No owner-doc update required（内部正确性修复）
- [x] `ai-dev/logs/` — Phase 1 无需单独 log entry，plan 完成后统一更新

### Phase 2 - 类型安全加固（15-02, 15-03, 15-04）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java`, `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryInternalAppendingState.java`, `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamReduceOperator.java`

- Item Types: `Fix`

- [x] **15-02**: MemoryStateSerDe restoreAppendingState/restoreReducingState 增加 valueClass.isInstance 检查
- [x] **15-03**: MemoryInternalAppendingState.add() 增加 descriptor.getValueType().isInstance 检查
- [x] **15-04**: StreamReduceOperator snapshotState 记录 valueTypeName, restoreState 验证类型

Exit Criteria:

- [x] MemoryStateSerDe 的 restoreAppendingState/restoreReducingState 在 storage.put 前有 `valueClass.isInstance(value)` 检查，使用 `ERR_STREAM_TYPE_MISMATCH`
- [x] MemoryInternalAppendingState.add() 在 `accumulator.add((IN) current)` 前有 `descriptor.getValueType().isInstance(current)` 检查，使用 `ERR_STREAM_TYPE_MISMATCH`
- [x] StreamReduceOperator.restoreState() 有类型验证（valueTypeName 记录与校验），使用 `ERR_STREAM_TYPE_MISMATCH`
- [x] 新增测试：`TestMemoryStateSerDe.java` — snapshot/restore 往返测试
- [x] 新增测试：`TestMemoryInternalAppendingState.java` — 类型不匹配 add() 抛异常
- [x] 新增测试：`testReduceOperatorRestoreTypeMismatch`（`TestStreamReduceOperator.java`）— 类型不匹配 restore 抛异常
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` — Phase 2 无需单独 log entry，plan 完成后统一更新

### Phase 3 - 测试覆盖填补（16-01, 16-02, 16-03, 16-04）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/execution/TestGraphModelCheckpointExecutor.java`(新建), `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/taskmanager/TestTaskManager.java`(扩展), `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/coordinator/TestJobCoordinator.java`(扩展), `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/common/state/backend/memory/TestMemoryStateSerDe.java`(扩展)

- Item Types: `Proof`

- [x] **16-01**: 为 `GraphModelCheckpointExecutor` 添加核心路径测试：(1) `createStorage` 默认/JDBC/自定义路径验证，(2) `handleJobTermination` DRAIN/SUSPEND/CANCEL 模式配置分发验证，(3) `restoreFromSavepointPath` 无 savepoint fallback 逻辑。新建 `TestGraphModelCheckpointExecutor.java`（7 个测试）
- [x] **16-02**: 为 `TaskManager` 添加核心路径测试：(1) `sendCheckpointAck` 无 coordinatorRpcService 不崩溃，(2) `sendCheckpointAck` 有 coordinatorRpcService 正确发送，(3) `installInvokable` 对不存在任务不崩溃，(4) `triggerCheckpoint` 无运行任务不崩溃，(5) `triggerCheckpoint` stale token 忽略。扩展 `TestTaskManager.java`（+6 个测试）
- [x] **16-03**: 为 `JobCoordinator` 添加终止模式测试：(1) `testTerminateDrainTriggersTerminalCheckpoint`，(2) `testTerminateSuspendTriggersSavepoint`，(3) `testDetectFailuresTriggersRecoveryWhenNodeLost`，(4) `testDetectFailuresNoRecoveryWhenAllNodesHealthy`。扩展 `TestJobCoordinator.java`（+4 个测试）
- [x] **16-04**: 为 `MemoryStateSerDe` 添加专项测试：(1) `testAggregatingStateRoundTrip` 使用 SumAggregateFunction，(2) `testReducingStateRoundTripMultipleKeys` 多 key 验证，(3) `testMapStateRoundTrip` 含 entries 迭代验证。扩展 `TestMemoryStateSerDe.java`（+3 个测试）

Exit Criteria:

- [x] GraphModelCheckpointExecutor 有 `restoreFromSavepointPath` 和 `handleJobTermination(DRAIN/SUSPEND)` 的直接测试（新文件 `TestGraphModelCheckpointExecutor.java`）
- [x] TaskManager 有 `triggerCheckpoint` / `sendCheckpointAck` / `installInvokable` 的测试
- [x] JobCoordinator 有 DRAIN / SUSPEND / `detectFailures` 的测试
- [x] MemoryStateSerDe 有 AggregatingState / ReducingState / MapState 的序列化往返测试（扩展 `TestMemoryStateSerDe.java`）
- [x] `./mvnw test -pl nop-stream -am` 全部通过（354 tests passed, BUILD SUCCESS）
- [x] **端到端验证**（Phase 2 类型安全修复）：checkpoint 恢复管线的端到端路径（snapshot → serialize → deserialize → restore → type check）在正常和类型不匹配场景下均行为正确，由 Phase 2 + Phase 3 的测试联合覆盖
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 9/10 个 P2 发现已修复（02-02 延期见 Deferred，01-01 审计发现不准确已移除）
- [ ] 安全白名单收窄完成
- [ ] 类型安全加固完成
- [ ] 测试覆盖盲区填补完成
- [ ] 不存在被静默降级到 deferred 的 in-scope P2 live defect
- [ ] No owner-doc update required（均为代码/测试修复）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：新增类型检查确有运行时验证逻辑，新增测试确有断言
- [ ] `./mvnw compile -pl nop-stream -am` 通过
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 02-02: GraphModelCheckpointExecutor 803 行 7 种职责

- Classification: `optimization candidate`
- Why Not Blocking Closure: 完整修复需要提取 `CheckpointRestoreManager`（~145行）和 `CheckpointExecutionLifecycle`（~296行）两个新类。属于架构级重构，影响面大（803 行全静态方法类的拆分涉及 ~30 个调用点），回归风险高。当前功能正确运行，审计也未报告由此导致的 bug。适合作为后续专项重构计划
- Successor Required: yes
- Successor Path: 待测试覆盖完善后（Phase 3 完成），可启动专项重构计划

## Non-Blocking Follow-ups

- P3 发现修复（25 项：代码风格 4 + 测试有效性 3 + 类型安全 3 + 错误处理 3 + 安全 1 + 模块职责 2 + 依赖 1 + 测试覆盖 1 + 测试有效性 2）
- GraphModelCheckpointExecutor 类拆分重构（02-02 完整修复）
- WindowAggregationOperator 与 WindowOperator 统一（明确定位 + 文档说明）
- CEP ClosureCleaner 等效机制
- fraud-example 端到端验证
- 全模块 import 风格统一

## Closure

Status Note: <<完成或关闭时填写>>

Closure Audit Evidence:

<<独立子 agent closure audit 完成后填写>>

Follow-up:

- P3 发现修复（25 项）
- GraphModelCheckpointExecutor 重构
- WindowAggregationOperator 与 WindowOperator 统一
- CEP ClosureCleaner 等效机制
