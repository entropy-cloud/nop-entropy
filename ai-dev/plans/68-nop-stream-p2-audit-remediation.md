# 68 nop-stream P2 审计修复

> Plan Status: completed
> Last Reviewed: 2026-05-28
> Source: ai-dev/audits/2026-05-28-deep-audit-nop-stream-full/summary.md
> Related: 67-nop-stream-critical-correctness-fixes (P0/P1 修复，已完成)

## Purpose

将 2026-05-28 21 维度系统审计中保留的全部 P2 缺陷修复到可验证状态。P0/P1 问题已在 Plan 67 中修复完毕，本计划处理剩余的 P2 问题。

## Current Baseline

- nop-stream 含 9 个子模块，87,195 行生产代码，1,309 个测试方法
- Plan 67 已修复全部 P0/P1 问题（N106-N120, 13-01 路径遍历, 14-01 线程池泄漏, 14-02/03/04 竞态, 16-01, 20-01），测试全量通过
- 21 维度系统审计保留了 13 个 P2 发现，其中 Plan 67 已修复了 13-01（路径遍历）和 14-01（线程池泄漏），但以下 2 个 P2 在 Plan 67 中被遗漏：
  - **13-01 安全风险**（Class.forName + newInstance 从 checkpoint/网络数据加载类）— Plan 67 修复的 13-01 是路径遍历漏洞，不是此安全风险
  - **14-01 计数器泄漏**（completePendingCheckpoint 存储失败后 pending checkpoint 计数器泄漏）— Plan 67 修复的 14-01 是 GraphModelCheckpointExecutor 线程池泄漏，不是此计数器泄漏
- 现存未修复的 P2 发现清单（13 个，其中 2 个来自上述编号重叠）：

| 编号 | 维度 | 文件 | 摘要 |
|------|------|------|------|
| 02-01 | 模块职责 | JdbcCheckpointStorage + LocalFileCheckpointStorage | ~200-250行序列化逻辑完全重复 |
| 03-01 | API 表面积 | TwoPhaseCommitSinkFunction.java | interface 持有 Logger + 50行默认逻辑，应为 abstract class |
| 04-01 | ORM/数据 | JdbcCheckpointStorage.java | checkpoint/epoch 表缺少 natural key 唯一约束 |
| 04-03 | ORM/数据 | JdbcClusterRegistry.java | registerNode() SELECT+INSERT 不在事务中，TOCTOU |
| 09-02 | 错误处理 | GraphModelCheckpointExecutor + ChainingOutput + NFA + StreamElementCodec + WindowAggregationOperator | ~22处字符串异常（跨模块公共 API + 模块内部一致性） |
| 13-01 | 安全 | StreamElementCodec + WindowAggregationOperator | Class.forName() + newInstance() 从 checkpoint/网络数据加载类（深度防御：类名白名单） |
| 14-01 | 异步/事务 | CheckpointCoordinator.java:196-280 | 存储失败后 pending checkpoint 计数器泄漏 |
| 16-03 | 测试覆盖 | NFA.java:279-284 | 仅 windowTimes 按状态超时路径未测试 |
| 16-04 | 测试覆盖 | SharedBuffer.java | 缓存淘汰压力测试缺失 |
| 16-05 | 测试覆盖 | StreamRecordComparator + NFAStateNameHandler | 零测试引用 |
| 19-01 | 命名一致 | NFAState.java:111 | setNewStartParti**ai**lMatch 拼写错误 |
| 20-01 | 跨模块契约 | StreamExecutionEnvironment.java | ICheckpointExecutorFactory 无 SPI 注册 |
| 21-01 | 测试有效性 | TestSharedBuffer.java | 仅覆盖基本注册，无检索/锁定/边界测试 |

## Goals

- 修复全部 13 个 P2 发现
- 每个修复附带可验证的单元测试（适用时）
- 修复完成后 `./mvnw test -pl nop-stream -am` 全部通过

## Non-Goals

- 全部 21 个 P3 问题留给后续计划（见 Non-Blocking Follow-ups）
- 架构级重构（如模块拆分 02-02、MemoryKeyedStateBackend 瘦身 02-02）
- 性能优化（如 exists() 默认实现 04-02、sidSequence 碰撞 04-04）
- 低价值测试清理（21-02）

## Risks And Rollback

- **03-01 (interface → abstract class)**: 当前仅 2 个测试内部类 implements 此接口（`TestTwoPhaseCommitSinkFunction.TestSink` 和 `TestCheckpointParticipant.TestTwoPhaseSink`），无生产代码实现者。改为 abstract class 只需测试中 `implements` → `extends`，零生产风险。
- **09-02 (ErrorCode 迁移)**: 迁移范围覆盖 5 个文件共约 22 处字符串异常：GraphModelCheckpointExecutor ~8处 `StreamException`、ChainingOutput 5处 `StreamRuntimeException`、NFA ~5处 `StreamException`、StreamElementCodec 1处 `StreamException`、WindowAggregationOperator 1处 `StreamException` + 3处 `IllegalStateException`。带 cause 的异常需用 `new StreamException(ERR_XXX, e).param(...)` 模式，不带 cause 的用 `new StreamException(ERR_XXX).param(...)`。
- **02-01 (序列化去重)**: JdbcCheckpointStorage 和 LocalFileCheckpointStorage 的序列化方法存在签名差异（如 `deserializeCheckpoint(Path)` 重载仅 LocalFile 有），CheckpointSerDe 需提供公共 `byte[]` 版本，Path 版本保留在各自 Storage 中。
- **04-01 (upsert)**: `INSERT ... ON DUPLICATE KEY UPDATE` 是 MySQL 特有语法。项目使用 `IJdbcTemplate` 抽象层（测试用 H2）。应使用 IJdbcTemplate 事务包裹 `try { INSERT } catch (DuplicateKeyException) { UPDATE }` 模式，或使用标准 SQL MERGE。
- **13-01 (类名白名单)**: 需设计类名验证机制但不破坏现有序列化/反序列化流程。

## Scope

### In Scope

全部 13 个 P2 发现（见 Current Baseline 表格）

### Out Of Scope

- 全部 P3 问题（01-01, 01-02, 02-02, 03-02, 03-03, 03-04, 04-02, 04-04, 09-03, 09-04, 10-02, 10-03, 14-02, 15-01~04, 18-01, 19-02, 21-02）
- 架构级重构
- Flink 兼容性评估

## Execution Plan

### Phase 1 - 快速修复：命名拼写 + SPI 注册

Status: completed
Targets: `nop-stream-cep/.../nfa/NFAState.java`, `nop-stream-runtime/src/main/resources/META-INF/services/`

- Item Types: `Fix`

- [x] 19-01: 将 `NFAState.setNewStartPartiailMatch`（第111行）重命名为 `setNewStartPartialMatch`，更新 NFA.java 中所有调用点
- [x] 20-01: 在 `nop-stream-runtime/src/main/resources/META-INF/services/` 下创建文件 `io.nop.stream.core.execution.ICheckpointExecutorFactory`，内容为 `io.nop.stream.runtime.execution.CheckpointExecutorFactoryImpl`（注意：不是 GraphModelCheckpointExecutor，后者不实现此接口。实现者是 `CheckpointExecutorFactoryImpl`，确认于该文件第25行 `implements ICheckpointExecutorFactory`）

Exit Criteria:

- [x] `NFAState.java` 中不存在 `Partiail` 拼写（`grep -r "Partiail" nop-stream/ --include="*.java"` 返回零结果）
- [x] NFA.java 中所有调用使用新方法名 `setNewStartPartialMatch`
- [x] `META-INF/services/io.nop.stream.core.execution.ICheckpointExecutorFactory` 文件存在，内容为 `io.nop.stream.runtime.execution.CheckpointExecutorFactoryImpl`
- [x] `./mvnw compile -pl nop-stream -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 数据完整性：唯一约束 + TOCTOU 修复 + 计数器泄漏

Status: completed
Targets: `nop-stream-runtime/.../checkpoint/storage/JdbcCheckpointStorage.java`, `nop-stream-runtime/.../cluster/JdbcClusterRegistry.java`, `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java`

- Item Types: `Fix`

- [x] 04-01: 在 JdbcCheckpointStorage 的 DDL 中为 `stream_checkpoint` 表添加 `UNIQUE (job_id, pipeline_id, checkpoint_id)` 约束；将 `storeCheckPoint()` 改为事务内 try-INSERT-catch-UPDATE 模式（避免 MySQL 特有的 ON DUPLICATE KEY UPDATE，因 IJdbcTemplate 抽象层需兼容 H2）；为 `stream_epoch_manifest` 表添加 `UNIQUE (job_id, pipeline_id, epoch_id)` 约束，同样改 `storeEpochManifest()` 为 upsert 模式；同时检查 `storeSavepoint()`（第202行）是否有同样问题，如有则一并修复；在 `ensureTable()` 中添加 `ALTER TABLE ADD CONSTRAINT IF NOT EXISTS` 迁移逻辑（确保已有部署也能获得唯一约束保护——先检查约束是否存在，不存在则添加）
- [x] 04-03: 将 JdbcClusterRegistry.registerNode() 的 SELECT+INSERT/UPDATE 包裹在事务中（参照同文件中 registerCoordinator() 和 assignTask() 的事务模式）
- [x] 14-01: 修复 CheckpointCoordinator.completePendingCheckpoint() 中存储失败后计数器泄漏：在 `abortPendingCheckpoint()` 中增加 `COMPLETED→ABORTED` 的 CAS 转换（当前只处理 `RUNNING→ABORTED`），或在 `completePendingCheckpoint` 存储失败路径中直接执行 `pendingCheckpoints.remove()` + `decrementPendingCheckpointCount()` 而非委托给 abortPendingCheckpoint

Exit Criteria:

- [x] stream_checkpoint DDL 包含 `UNIQUE (job_id, pipeline_id, checkpoint_id)`
- [x] stream_epoch_manifest DDL 包含 `UNIQUE (job_id, pipeline_id, epoch_id)`
- [x] storeCheckPoint() 使用事务内 upsert 模式，纯 INSERT 在唯一约束冲突时能正确处理
- [x] storeSavepoint() 如有重复记录风险也已修复
- [x] ensureTable() 包含 ALTER TABLE ADD CONSTRAINT 迁移逻辑（`grep "ALTER TABLE" JdbcCheckpointStorage.java` 非空）
- [x] registerNode() 在事务中执行，不存在 TOCTOU 窗口
- [x] CheckpointCoordinator 存储失败后 pendingCheckpoints 被正确移除且计数器正确递减
- [x] 新增测试验证重复 checkpoint 写入不产生重复记录
- [x] 新增测试验证 CheckpointCoordinator 存储失败后计数器不泄漏（模拟 storage 抛异常，验证 pendingCheckpoints 为空且 numPendingCheckpoints 归零）
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 代码去重：提取 CheckpointSerDe

Status: completed
Targets: `nop-stream-runtime/.../checkpoint/storage/`

- Item Types: `Fix`

- [x] 02-01: 将 JdbcCheckpointStorage 和 LocalFileCheckpointStorage 中共享的序列化/反序列化逻辑提取到新的 CheckpointSerDe 工具类。注意事项：
  - 两个实现存在签名差异：LocalFileCheckpointStorage 的 `deserializeCheckpoint` 有额外的 `Path` 重载，`deserializeEpochManifest` 接受 `Path` 而非 `byte[]`
  - JdbcCheckpointStorage 的 `serializeEpochManifest` 中序列化逻辑是内联的，LocalFileCheckpointStorage 调用了独立的 `serializeTaskStateSnapshot`
  - CheckpointSerDe 提供公共 `byte[]` 版本方法；`Path` 重载保留在 LocalFileCheckpointStorage 中（调用 CheckpointSerDe 的 byte[] 版本 + 文件读写）
  - 需提取的方法（公共 byte[] 版本）：serializeCheckpoint(CompletedCheckpoint) → byte[]、deserializeCheckpoint(byte[]) → CompletedCheckpoint、serializeEpochManifest(EpochManifest) → byte[]、deserializeEpochManifest(byte[]) → EpochManifest、taskLocationToString(TaskLocation)、stringToTaskLocation(String)、serializeTaskStateSnapshot(Map, TaskLocation) → Map、deserializeTaskStateSnapshot(Map, TaskLocation) → Map
- [x] 两个 Storage 实现委托给 CheckpointSerDe

Exit Criteria:

- [x] CheckpointSerDe 类存在于 `io.nop.stream.runtime.checkpoint.storage` 包
- [x] CheckpointSerDe 包含上述公共 byte[] 版本方法
- [x] JdbcCheckpointStorage 和 LocalFileCheckpointStorage 不再包含直接的序列化/反序列化逻辑（均委托 CheckpointSerDe）
- [x] LocalFileCheckpointStorage 的 Path 重载方法保留，内部调用 CheckpointSerDe 的 byte[] 版本
- [x] `./mvnw test -pl nop-stream -am` 全部通过（序列化行为不变）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - API 设计：TwoPhaseCommitSinkFunction 重构

Status: completed
Targets: `nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java`, 测试文件

- Item Types: `Fix`

- [x] 03-01: 将 TwoPhaseCommitSinkFunction 从 interface 改为 abstract class。声明为 `public abstract class TwoPhaseCommitSinkFunction<IN> implements SinkFunction<IN>, CheckpointParticipant`。Logger 和 ~50行默认方法逻辑保持不变。将 getPendingCommits/setPendingCommits 从接口抽象方法改为 protected 字段 + getter/setter。
- [x] 更新测试中的实现者：`TestTwoPhaseCommitSinkFunction.TestSink` 和 `TestCheckpointParticipant.TestTwoPhaseSink` 从 `implements` 改为 `extends`

Exit Criteria:

- [x] TwoPhaseCommitSinkFunction 声明为 `abstract class` 而非 `interface`
- [x] 声明为 `implements SinkFunction<IN>, CheckpointParticipant`（保留多接口实现）
- [x] Logger 作为 `static final` 字段保留
- [x] `grep -rn "implements TwoPhaseCommitSinkFunction" nop-stream/` 返回零结果（全部改为 extends）
- [x] `./mvnw compile -pl nop-stream -am` 通过
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 错误码迁移 + 安全加固

Status: completed
Targets: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java`, `nop-stream-core/.../operators/ChainingOutput.java`, `nop-stream-cep/.../nfa/NFA.java`, `nop-stream-core/.../transport/StreamElementCodec.java`, `nop-stream-core/.../operators/WindowAggregationOperator.java`, `NopStreamErrors.java`, `NopCepErrors.java`

- Item Types: `Fix`

- [x] 09-02: 为以下文件中的字符串异常添加 ErrorCode 定义：
  - **GraphModelCheckpointExecutor.java**（~8处 `new StreamException`）：新增到 NopStreamErrors。注意区分带 cause 的（用 `new StreamException(ERR_XXX, e).param(...)`）和不带 cause 的
  - **ChainingOutput.java**（5处 `new StreamRuntimeException("msg", e)`）：注意这些是 `StreamRuntimeException` 而非 `StreamException`，且都已正确传递 cause。新增到 NopStreamErrors
  - **NFA.java**（~5处 `new StreamException`）：位于 private 方法（isStartState/isStopState/isFinalState）和内部 filter function 执行中。虽然是模块内部路径，但为一致性迁移至 NopCepErrors
  - **StreamElementCodec.java**（第105行 `new StreamException("Failed to load valueType class: ...", e)`）：catch ClassNotFoundException 中的字符串异常，需迁移到 NopStreamErrors
  - **WindowAggregationOperator.java**（第651行 `new StreamException("Failed to create trigger state accumulator", e)` + 第135/144/148行 3处 `new IllegalStateException`）：StreamException 迁移到 NopStreamErrors；IllegalStateException 改为 StreamException(ERR_XXX).param(...)（从 checkpoint 恢复的公共路径应使用结构化异常）
- [x] 13-01: 添加类名白名单机制作为深度防御：
  - 在 `StreamElementCodec` 的 `Class.forName(envelope.getValueType())` 前添加类名验证（允许 io.nop.stream.* 和 java.* 前缀的类，或提供可配置的白名单）
  - 在 `WindowAggregationOperator` 的 `Class.forName(accType).newInstance()` 处添加类似验证
  - 在 `MemoryKeyedStateBackend` 的多处 `Class.forName` 处添加类似验证（此处类名来自 checkpoint 数据，受白名单保护尤为重要）

Exit Criteria:

- [x] GraphModelCheckpointExecutor 中不存在 `new StreamException("`（`grep` 验证）
- [x] ChainingOutput 中不存在 `new StreamRuntimeException("`（`grep` 验证）
- [x] NFA.java 中不存在 `new StreamException("`（`grep` 验证）
- [x] StreamElementCodec.java 中不存在 `new StreamException("`（`grep` 验证）
- [x] WindowAggregationOperator.java 中不存在 `new StreamException("` 且不存在 `new IllegalStateException("`（`grep` 验证）
- [x] NopStreamErrors 新增约 17 个 ErrorCode 常量（GraphModelCheckpointExecutor ~8 + ChainingOutput ~5 + StreamElementCodec ~1 + WindowAggregationOperator ~4）
- [x] NopCepErrors 新增约 5 个 ErrorCode 常量（NFA）
- [x] TestErrorCodeMessagesEnglish 已扩展覆盖 NopCepErrors（当前仅覆盖 NopStreamErrors）
- [x] StreamElementCodec 和 WindowAggregationOperator 中 Class.forName 前有类名白名单验证
- [x] MemoryKeyedStateBackend 中 Class.forName 前有类名白名单验证
- [x] 新增测试验证非法类名被拒绝
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 测试补充：CEP 测试盲区 + 有效性提升

Status: completed
Targets: `nop-stream-cep/src/test/`

- Item Types: `Proof`

- [x] 16-03: 添加 NFA 级别测试验证 windowTimes 按状态超时路径（使用 SharedBufferCacheConfig 设置小容量，创建带 .within() 条件的 NFA，advanceTime 触发特定状态超时，验证超时 ComputationState 被正确修剪）
- [x] 16-04: 添加 SharedBuffer 缓存淘汰压力测试（使用 SharedBufferCacheConfig 设置较小缓存槽位数如 capacity=2，填满缓存槽位后继续注册新条目，验证 LRU 淘汰后模式匹配仍正确）
- [x] 16-05: 添加 StreamRecordComparator 单元测试（`io.nop.stream.cep.operator.StreamRecordComparator`：验证事件按 timestamp 升序排序，相同 timestamp 的边界条件）和 NFAStateNameHandler 单元测试（`io.nop.stream.cep.nfa.compiler.NFAStateNameHandler`：验证状态名生成/解析一致性）
- [x] 21-01: 扩展 TestSharedBuffer（`io.nop.stream.cep.nfa.sharedbuffer.TestSharedBuffer`）添加：检索测试（注册后按条件检索）、锁定/解锁语义测试（锁定后条目不被淘汰，解锁后可淘汰）、边界条件测试（空缓冲区操作、重复注册）

Exit Criteria:

- [x] 存在 NFA 级别 windowTimes 按状态超时测试（测试方法名包含 windowTimes 或 windowTimeout）
- [x] TestSharedBuffer 或新测试文件包含缓存淘汰压力测试
- [x] StreamRecordComparator 和 NFAStateNameHandler 各有对应测试文件且测试通过
- [x] TestSharedBuffer 包含检索、锁定/解锁和边界条件测试方法
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 13 个 P2 发现已修复或已显式移入 Deferred But Adjudicated
- [x] `./mvnw test -pl nop-stream -am` 全量通过
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope P2 缺陷
- [x] No owner-doc update required（全部 P2 为代码/测试变更，不涉及平台约定变更）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `ai-dev/logs/` 收口记录已写入

## Deferred But Adjudicated

（无——全部 13 个 P2 均在 scope 内）

## Non-Blocking Follow-ups

- P3 问题集合（21个）：依赖优化(01-01, 01-02)、文件瘦身(02-02)、Javadoc 清理(03-02, 03-04)、死代码(03-03)、exists() 性能(04-02)、sidSequence 碰撞(04-04)、异常层次(09-03, 09-04)、XDef typo/namespace(10-02, 10-03)、Lockable 线程安全(14-02)、泛型注解(15-01~04)、README 不一致(18-01)、FollowKind 文档(19-02)、低价值测试(21-02)

## Closure

Status Note: 全部 13 个 P2 缺陷已修复，6 个 Phase 全部 completed，独立 closure audit PASS（25 项检查全部通过，0 failures）。

Closure Audit Evidence:

- Reviewer / Agent: Independent subagent (ses_19076f3f4ffec0n1ANTeiTddre)
- Evidence:
  - Phase 1: grep "Partiail" returns 0 results; SPI file exists with correct content
  - Phase 2: UNIQUE constraints in DDL + ALTER TABLE migration; registerNode in transaction; storage failure directly removes pendingCheckpoints
  - Phase 3: CheckpointSerDe.java exists; both Storage classes delegate (5+ and 7+ references)
  - Phase 4: abstract class declaration confirmed; 0 "implements TwoPhaseCommitSinkFunction" results
  - Phase 5: 0 string exceptions in all 5 target files; ClassNameValidator used in 3 files (14+ call sites); TestClassNameValidator exists
  - Phase 6: TestNFAWindowTimeout (4 tests), TestStreamRecordComparator (7 tests), TestNFAStateNameHandler (10 tests), TestSharedBuffer expanded (22 test methods)
  - `./mvnw test -pl nop-stream -am` BUILD SUCCESS, 0 failures, 0 errors
  - No silently deferred P2 defects; Deferred But Adjudicated section is empty

Follow-up:

- P3 问题集合留给后续计划
