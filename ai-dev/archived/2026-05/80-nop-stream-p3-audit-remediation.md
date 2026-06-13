# 80 nop-stream P3 审计修复（05-28 遗留）

> Plan Status: completed
> Last Reviewed: 2026-05-30
> Source: ai-dev/audits/2026-05-28-deep-audit-nop-stream/ (维度 01/03/09)
> Related: 78-nop-stream-p2-p3-audit-remediation (proposed), 79-nop-stream-round11-p1-p2-audit-remediation (completed)

## Purpose

将 2026-05-28 深度审计中未被 Plan 78 覆盖的 3 个 P3 发现修复到可验证状态。

## Current Baseline

- Plan 79 完成后 `./mvnw test -pl nop-stream -am` 全量通过
- Plans 73-79 修复了全部 P0/P1 + 大部分 P2
- Plan 78（proposed）覆盖了 05-25/27/28 审计的 P2 遗留（13 项），但以下 P3 发现不在其 scope 内
- 经 live repo 验证（2026-05-30），以下 3 个 P3 发现仍存在：

| 编号 | 来源 | 严重程度 | 文件 | 摘要 | live 验证 |
|------|------|---------|------|------|----------|
| 03-02 | 05-28 | P3 | ICheckpointStorage.java | 全部 13 个方法声明 `throws Exception`，无具体异常类型 | 确认仍存在：13 个方法均 throws Exception |
| 09-01 | 05-28 | P3 | MalformedPatternException.java | 仅有 `(String)` 构造器，缺少 `(ErrorCode)` 构造器 | 确认仍存在：仅 1 个 String 构造器 |
| 09-10 | 05-28 | P3 | TaskExecutor.java:290-297 | awaitCompletion() 异常仅 LOG.debug，缺少 Javadoc 说明调用方检查模式 | 确认仍存在：无 Javadoc 解释异常处理模式 |

### 已确认修复（Plan 78/79 已覆盖或已修复）

- 01-02: fraud-example 版本硬编码 → 已修复
- 02-02: CheckpointStorage 序列化重复 → 已提取为 CheckpointSerDe.java
- 09-04: runtime 模块字符串构造器 → Plan 79 Phase 6 已修复（grep 零命中）
- 09-05: connector 字符串构造器 → 已修复（grep 零命中）
- 17-01: import 排序 → ChainingOutput.java 已修复
- 01-03: nop-bom 缺失模块 → 已修复

### Plan 78 scope 内（proposed，未执行）

- 09-07: runtime IllegalStateException 替换（Phase 1）
- 01-01: connector package-info.java 依赖说明（Phase 5）
- 01-04: JdbcCheckpointStorage Javadoc 依赖说明（Phase 5）

## Goals

- 修复全部 3 个 P3 发现：ICheckpointStorage 异常声明、MalformedPatternException ErrorCode 构造器、TaskExecutor Javadoc
- 每个修复完成后 `./mvnw test -pl nop-stream -am` 全部通过

## Non-Goals

- Plan 78 scope 内的发现（09-07 IllegalStateException、01-01/01-04 依赖文档）
- 架构级重构（api 模块填充 03-01、core 模块拆分 02-01）
- 05-25 全量审计的其余 P3/P4 发现（~15 项：03-03/03-04/03-10、09-03/09-07、10-07、14-03/14-10/14-11、15-03、17-02/17-04）

## Scope

### In Scope

- nop-stream-core: ICheckpointStorage 异常声明改进、TaskExecutor Javadoc
- nop-stream-cep: MalformedPatternException 构造器补充

### Out Of Scope

- Plan 78 scope 内的全部发现
- 架构级重构（03-01 api 模块、02-01 大文件拆分）
- 05-25 审计其余 P3/P4 发现
- 05-27/05-28 审计维度 02（MemoryKeyedStateBackend 拆分已在 Plan 78 Deferred）

## Execution Plan

### Phase 1 - ICheckpointStorage 异常声明改进（P3: 03-02）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/storage/ICheckpointStorage.java`, `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java`, `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/LocalFileCheckpointStorage.java`, `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestCheckpointCoordinator.java`, `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestCheckpointRecovery.java`, `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestFingerprintAndTerminationMode.java`

- Item Types: `Fix`

- [x] 定义 `CheckpointStorageException extends StreamException`，复用已有的 `NopStreamErrors.ERR_STREAM_CHECKPOINT_ERROR`（已存在于 NopStreamErrors:52-53）。提供 `(String message, Throwable cause)` 和 `(ErrorCode, Throwable)` 构造器
- [x] 将 ICheckpointStorage 的全部 13 个方法从 `throws Exception` 改为 `throws CheckpointStorageException`
- [x] 更新实现类 JdbcCheckpointStorage、LocalFileCheckpointStorage：方法签名改为 `throws CheckpointStorageException`，内部 checked exception（IOException、SQLException 等）用 try-catch 包裹为 `throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL, "...")`
- [x] 更新测试文件中匿名 ICheckpointStorage 实现的 throws 声明（TestCheckpointCoordinator、TestCheckpointRecovery、TestFingerprintAndTerminationMode 等）

Exit Criteria:

- [x] `CheckpointStorageException.java` 存在于 `io.nop.stream.core.checkpoint.storage` 包中
- [x] `grep "throws Exception" ICheckpointStorage.java` 零命中（全部改为 `throws CheckpointStorageException`）
- [x] JdbcCheckpointStorage 和 LocalFileCheckpointStorage 的 ICheckpointStorage 方法签名一致（`throws CheckpointStorageException`），内部 checked exception 已正确包装
- [x] 测试文件中 ICheckpointStorage 匿名实现的 throws 声明已更新
- [x] No new test required: CheckpointStorageException 是 unchecked exception 的简单包装，行为与替换前相同，原有测试覆盖不变
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - MalformedPatternException ErrorCode 构造器（P3: 09-01）

Status: completed
Targets: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/pattern/MalformedPatternException.java`

- Item Types: `Fix`

- [x] 为 MalformedPatternException 新增 `MalformedPatternException(ErrorCode errorCode)` 和 `MalformedPatternException(ErrorCode errorCode, Throwable cause)` 构造器，调用 `super(errorCode)` 和 `super(errorCode, cause)`（StreamRuntimeException 已有这些父类构造器，直接委托以保留 ErrorCode 元数据）
- [x] 在 NopCepErrors 中新增 `ERR_CEP_MALFORMED_PATTERN`（如尚未存在）

Exit Criteria:

- [x] `grep "ErrorCode" MalformedPatternException.java` 命中（至少 2 个构造器）
- [x] NopCepErrors 包含 `ERR_CEP_MALFORMED_PATTERN`（或确认已有等价定义）
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - TaskExecutor Javadoc 补充（P3: 09-10）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/TaskExecutor.java`

- Item Types: `Fix`

- [x] 为 `awaitCompletion()` 和 `awaitCompletion(long, TimeUnit)` 添加 Javadoc，说明异常仅以 DEBUG 级别记录（因为调用方会在后续遍历 SubtaskTask 列表时通过 `task.getError()` 统一检查并抛出异常）

Exit Criteria:

- [x] `TaskExecutor.java` 中两个 awaitCompletion 方法有 Javadoc 说明异常处理模式
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 3 个 P3 发现已修复
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 发现
- [x] No owner-doc update required（全部为代码/文档修复）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check: 修复未引入空壳或静默跳过
- [x] `./mvnw compile -pl nop-stream -am` 通过
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] checkstyle / 代码规范检查通过
- [x] `ai-dev/logs/` 收口记录已写入

## Deferred But Adjudicated

（无——全部 3 个 P3 均在 scope 内）

## Non-Blocking Follow-ups

- Plan 78 scope 内的 P2 发现修复（13 项：09-07、10-02、15-01/02/05/06/08、16-04/10/11/12、17-01、01-01、01-04）
- 05-25 审计其余 P3/P4 发现（~15 项）
- 架构级重构设计文档（api 模块填充 03-01、core 模块拆分 02-01）

## Closure

Status Note: All 3 P3 findings fixed and verified. Phase 1: CheckpointStorageException replaces throws Exception. Phase 2: MalformedPatternException gains ErrorCode constructors. Phase 3: TaskExecutor awaitCompletion Javadoc added.

Closure Audit Evidence:

- Reviewer / Agent: Independent subagent (closure-audit task)
- Audit Session: ses_1886652e5ffe8zb9n4hBlgPkJC (independent closure-audit subagent)
- Evidence:
  - Phase 1 Exit Criteria: PASS - CheckpointStorageException exists, ICheckpointStorage has zero `throws Exception`, JdbcCheckpointStorage/LocalFileCheckpointStorage updated, test file updated, BUILD SUCCESS
  - Phase 2 Exit Criteria: PASS - MalformedPatternException has ErrorCode constructors, NopCepErrors has ERR_CEP_MALFORMED_PATTERN, BUILD SUCCESS
  - Phase 3 Exit Criteria: PASS - Both awaitCompletion methods have Javadoc explaining exception handling pattern, BUILD SUCCESS
  - Closure Gates: All 9 gates checked
  - Anti-Hollow: No hollow implementations introduced; all changes are concrete
  - Deferred items: None; all 3 P3 findings in scope

Follow-up:

- no remaining plan-owned work
