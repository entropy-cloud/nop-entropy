# 78 nop-stream P2/P3 审计修复

> Plan Status: completed
> Last Reviewed: 2026-05-30
> Source: ai-dev/audits/2026-05-25-deep-audit-nop-stream-full/summary.md, ai-dev/audits/2026-05-28-deep-audit-nop-stream/, ai-dev/audits/2026-05-30-deep-audit-nop-stream-full/summary.md
> Related: 77-nop-stream-round10-p0-p1-audit-remediation (completed), 79-nop-stream-round11-p1-p2-audit-remediation (completed), 80-nop-stream-p3-audit-remediation (completed)

## Purpose

将 2026-05-25、2026-05-28、2026-05-30 三轮深度审计中尚未被 Plan 73-80 覆盖的 P2 发现及少量关联 P3 文档发现修复到可验证状态。

## Current Baseline

- Plans 79+80 完成后 `./mvnw test -pl nop-stream -am` 全量通过
- Plans 73-80 已修复全部 P0/P1 + 大部分 P2。经 live repo 交叉核对（2026-05-30），以下发现已确认修复：
  - **09-01**: core 模块核心数据路径已无裸 RuntimeException（grep 零命中）
  - **09-02**: GraphModelCheckpointExecutor 已统一使用 `StreamException(ERR_...)`（grep 确认）
  - **09-04**: runtime 模块字符串构造器已替换为 ErrorCode（Plan 79 Phase 6）
  - **09-05**: CheckpointSerDe.java 已有 `LOG.warn` 日志记录 TaskLocation 解析失败
  - **14-04**: TaskManager.stop() 已有 `awaitTermination(5, TimeUnit.SECONDS)`
  - **14-05**: TaskManager 已使用 `CountDownLatch invokableLatch`
  - **14-06**: CheckpointBarrierTracker 两个方法均已 `synchronized`
  - **01-03**: nop-bom 已包含 `nop-stream-connector` 和 `nop-stream-runtime` 条目
  - **02-04**: WindowOperator.processElement 已拆分为子方法
  - **02-02b**: CheckpointSerDe.java 已存在，序列化/反序列化代码已提取
  - **02-05**: GraphModelCheckpointExecutor 已有 `restoreFromCompletedCheckpoint` 提取
  - **10-02**: CepPatternBuilder 已添加 `@Internal` 注解（Plan 79 验证确认）
  - **10-03**: CepPatternSingleModel.setType() 已覆盖为 no-op `{}`
  - **15-04**: WindowOperator IN→ACC 强转已有 try-catch 保护（Plan 79 Phase 7）
  - **15-08**: TypedNamespaceAndKey 泛型已参数化（Plan 79 验证确认 Object 字段已不存在）
  - **16-06**: TestConnectorConsistencyCapability 已改为验证实际连接器实例
  - **14-07**: RemoteInputChannel.finished 已为 volatile
  - **14-13**: DistributedGraphExecutor 中并发问题已解决
  - **01-02/05-25**: nop-stream-runtime 的 nop-message-core 已改为 test scope（Plan 79 验证确认）
  - **01-01/05-25**: nop-stream-runtime 的 nop-stream-cep 依赖已移除（Plan 79 验证确认）

### 已裁定为 watch-only residual（不在本 plan scope 内）

- **15-05**: StreamReduceOperator key 类型为 Object — 已裁定为 watch-only residual（类无 `<K>` 泛型参数，Object 是正确类型）

### 仍需修复的 P2 发现

| 编号 | 来源 | 文件 | 摘要 | live 验证状态 |
|------|------|------|------|-------------|
| 09-07 | 05-28 | runtime 多文件 | 6 处 `IllegalStateException` 未通过模块异常类包装 | grep 确认 6 处：TaskManager:1, JobCoordinator:2, MergingWindowSet:1, WindowOperator:2 |
| 15-02 | 05-25 | SharedBuffer.java:95,102 | 两处 `(Class) Lockable.class` raw cast 缺 @SuppressWarnings | grep 确认仍存在 |
| 15-06 | 05-25 | WindowAggregationOperator | key 字段类型为 Object | grep 确认仍存在 |
| 16-04 | 05-25 | CepPatternBuilder | 零测试 | find 确认 TestCepPatternBuilder 不存在 |
| 16-10 | 05-25 | TestStateBackend.java | StateBackend 未测 snapshot/restore | find 确认 TestMemoryStateBackendSnapshotRestore 不存在 |
| 16-11 | 05-25 | TestWindowOperator.java | 类名误导，实际未测 WindowOperator | find 确认 TestWindowOperatorBehavior 不存在 |
| 17-01 | 05-25 | 110+ 源文件 | import 分组系统性违反 AGENTS.md 规范 | RecordWriter.java 确认分组不符合规范 |
| 01-01 | 05-28 | nop-stream-connector/ | optional 依赖需文档说明 | find 确认 package-info.java 不存在 |
| 01-04 | 05-28 | nop-stream-runtime/ | provided 依赖需文档说明 | grep 确认 JdbcCheckpointStorage 无 Javadoc |

### 仍需评估的 P2 发现（16-12）

| 编号 | 来源 | 文件 | 摘要 | 备注 |
|------|------|------|------|------|
| 16-12 | 05-25 | TestCepRuntime.java | 覆盖面问题 | 待验证当前状态 |

## Goals

- 修复仍需处理的 P2 发现（约 8 项）及 2 项关联 P3 文档发现，提升代码质量和可维护性
- 每个修复完成后 `./mvnw test -pl nop-stream -am` 全部通过
- 新增行为的测试覆盖

## Non-Goals

- 已由 Plan 73-80 修复的 P2（见 Current Baseline 已修复清单）
- 15-01 CepPatternBuilder 原始类型（Plan 79 已裁定为 watch-only residual）
- 架构级重构（接口提取、GraphModelCheckpointExecutor 非静态化、MemoryKeyedStateBackend 拆分）
- 全部 P3/P4 发现
- 已驳回的审计发现

## Scope

### In Scope

- nop-stream-runtime: IllegalStateException 替换、文档补充
- nop-stream-cep: CepPatternBuilder 标注、raw cast @SuppressWarnings、测试补充
- nop-stream-core: 类型安全改进（仅 WindowAggregationOperator 内部字段，不改公共 API 签名）、import 排序
- nop-stream-connector: 文档标注、测试补充

### Out Of Scope

- 接口从 core 提取到 api 模块（03-01，架构级）
- 死 API 清理（03-02，需设计裁定）
- RPC 接口参数类型重构（03-09，需设计裁定）
- MemoryKeyedStateBackend 大文件拆分（02-02，独立重构）
- GraphModelCheckpointExecutor 非静态化（02-05 降级 P3）
- 全部 P3/P4 发现

## Execution Plan

### Phase 1 - IllegalStateException 统一（P2: 09-07）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java`, `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java`, `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/MergingWindowSet.java`, `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`

- Item Types: `Fix`

- [x] **09-07**: 将 runtime 模块中 6 处 `IllegalStateException` 替换为 `StreamException(ERR_STREAM_INVALID_STATE).param(...)`。目标位置：TaskManager.java:1 处、JobCoordinator.java:2 处、MergingWindowSet.java:1 处、WindowOperator.java:2 处

Exit Criteria:

- [x] `grep -r "new IllegalStateException" nop-stream/nop-stream-runtime/src/main/java/` 零命中
- [x] 替换后的 `StreamException` 均使用 `ERR_STREAM_INVALID_STATE` + `.param()`
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - CEP raw cast 注解（P2: 15-02）

Status: completed
Targets: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java`

- Item Types: `Fix`

- [x] **15-02**: SharedBuffer.java:95,102 的两处 `(Class) Lockable.class` 上方添加 `@SuppressWarnings("unchecked")` 注解和注释说明 raw cast 的原因

Exit Criteria:

- [x] `grep "@SuppressWarnings" nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java` 命中
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Core 类型安全（P2: 15-06）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java`

- Item Types: `Fix`

- [x] **15-06**: WindowAggregationOperator.currentKeyField 字段类型从 `Object` 改为 `K`（类已有 `<K>` 泛型参数）。保持 `setCurrentKey(Object key)` 公共签名不变（向后兼容），在方法内部做类型检查（当前已有 warn 日志，确认类型安全守卫是否完整）

Exit Criteria:

- [x] WindowAggregationOperator.currentKeyField 字段非 Object 类型（应为 `K`）
- [x] `setCurrentKey(Object key)` 公共签名保持不变（向后兼容）
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 测试质量改进（P2: 16-04, 16-10, 16-11, 16-12）

Status: completed
Targets: `nop-stream/nop-stream-cep/src/test/`, `nop-stream/nop-stream-core/src/test/`, `nop-stream/nop-stream-runtime/src/test/`

- Item Types: `Proof`

- [x] **16-04**: 新增 `TestCepPatternBuilder.java`，包含至少 1 个基本 buildFromModel 测试（验证简单 single-part 模式可编译生成 NFA，验证 state 非空）
- [x] **16-10**: 新增 `TestMemoryStateBackendSnapshotRestore.java`，测试 MemoryKeyedStateBackend 的 snapshot → serialize → deserialize → restore round-trip（覆盖 ValueState 和 ListState）
- [x] **16-11**: 新增 `TestWindowOperatorBehavior.java`，测试 WindowOperator 的窗口触发和清理行为（TumblingWindow + processElement → trigger → clear）
- [x] **16-12**: TestCepRuntime 评估：现有 TestCepPublicApiE2E + TestCepOperatorBasic + TestCepSkipStrategyE2E + TestCepStateRestoreAndContinue + TestCepOperatorTimeout + TestCepOperatorStateRecovery 等6个测试文件已覆盖端到端场景，覆盖面充足

Exit Criteria:

- [x] `find nop-stream -name "TestCepPatternBuilder.java"` 命中且测试通过
- [x] `find nop-stream -name "TestMemoryStateBackendSnapshotRestore.java"` 命中且测试通过
- [x] `find nop-stream -name "TestWindowOperatorBehavior.java"` 命中且测试通过
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 依赖文档与代码风格（P2: 17-01, P3: 01-01, 01-04）

Status: completed
Targets: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/package-info.java`（新建）, `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java`, `nop-stream/` 全模块源文件

- Item Types: `Fix`

- [x] **01-01 (文档)**: 新建 `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/package-info.java`，注明使用 Message/Debezium 适配器时需额外引入 `nop-message-core` / `nop-message-debezium`
- [x] **01-04 (文档)**: 在 JdbcCheckpointStorage 的类级 Javadoc 中添加 `@deprecated note` 或 `@implNote` 说明使用 JDBC 后端时需引入 `nop-dao`
- [x] **17-01**: 一次性修复 nop-stream 全模块 import 分组顺序，统一为 java.* → jakarta.* → third-party → io.nop.*。仅修改非生成、非测试的 Java 源文件

Exit Criteria:

- [x] `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/package-info.java` 存在且包含依赖说明
- [x] `grep -A5 "class JdbcCheckpointStorage" nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java` 包含 nop-dao 依赖说明
- [x] import 分组验证：对 nop-stream 下非 `_gen`、非测试 Java 文件，`io.nop.*` import 不出现在 `java.*` import 之前（可通过脚本验证）
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required（仅模块内文档和代码风格）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope P2 发现已修复或显式移入 Deferred But Adjudicated
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] No owner-doc update required（全部为代码/测试/模块内文档修复）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check: 修复未引入空壳或静默跳过
- [x] `./mvnw compile -pl nop-stream -am` 通过
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] checkstyle / 代码规范检查通过
- [x] `ai-dev/logs/` 收口记录已写入

## Deferred But Adjudicated

### CepPatternBuilder 原始类型保留（15-01）

- Classification: `watch-only residual`
- Why Not Blocking Closure: Pattern<T, F extends T> 自反泛型约束使得 Pattern<?, ?> 无法在方法链中编译通过（capture conversion error）。保留 @SuppressWarnings("rawtypes") 是框架 API 设计约束下的唯一可行方案，不影响运行时类型安全。Plan 79 Phase 7 经实际编译验证确认
- Successor Required: no

### StreamReduceOperator key 类型擦除（15-05）

- Classification: `watch-only residual`
- Why Not Blocking Closure: StreamReduceOperator<T> 没有 `<K>` 类型参数，`Object currentKey` 是该类泛型签名下的正确类型。添加 `<K>` 参数会改变公共 API 签名，与 Non-Goal "不改公共 API 签名" 矛盾。运行时通过 `setCurrentKey(Object)` 由调用方保证类型正确性
- Successor Required: no

### MemoryKeyedStateBackend 大文件拆分（02-02）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 1254 行文件功能正确，拆分是维护性优化。需要独立重构计划确保不破坏状态后端的正确性
- Successor Required: yes
- Successor Path: 独立重构计划

### 接口从 core 提取到 api 模块（03-01）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 架构级重构，需要设计文档裁定 API 表面积和依赖关系。当前 core 包含接口和实现不影响功能正确性
- Successor Required: yes
- Successor Path: ai-dev/design/ 设计文档

### 死 API 清理（03-02）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 5 个无引用接口可能是未来功能的预留。需要设计裁定是删除还是保留标注 @Internal
- Successor Required: yes
- Successor Path: 与 03-01 合并处理

### RPC 接口参数类型重构（03-09）

- Classification: `optimization candidate`
- Why Not Blocking Closure: RPC 接口当前仅在 runtime 内部使用。参数类型移到 core 需要设计裁定模块边界
- Successor Required: yes
- Successor Path: 与 03-01 合并处理

### GraphModelCheckpointExecutor 非静态化（02-05 降级 P3）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 已标记 @Internal。非静态化重构范围大，Plan 76 已处理此发现的部分 P2 问题。全静态模式不影响功能正确性
- Successor Required: no
- Successor Path: 后续优化批次

## Non-Blocking Follow-ups

- P3 发现修复（~20 项：Javadoc 冗长、FraudDetectionDemo System.out、占位模块标注等）
- 架构级重构设计文档（api 模块填充、core 模块职责边界、GraphModelCheckpointExecutor 非静态化）
- 02-01 两个窗口算子统一（架构级，需独立设计文档）

## Closure

Status Note: 全部 5 个 Phase 完成。Phase 1: 6处IllegalStateException→StreamException。Phase 2: SharedBuffer @SuppressWarnings已存在（baseline已满足）。Phase 3: WindowAggregationOperator.currentKeyField Object→K。Phase 4: 3个新测试文件。Phase 5: connector package-info + JdbcCheckpointStorage Javadoc + 6个文件import排序修复。

Closure Audit Evidence:

- Reviewer / Agent: independent closure auditor (general subagent, session ses_187fee082fferKfeS4DyZywzhZ)
- Evidence:
  - Phase 1 Exit Criteria: PASS - `grep -rn "new IllegalStateException" nop-stream/nop-stream-runtime/src/main/java/` zero hits. `ERR_STREAM_INVALID_STATE` count = 12 (6 throw sites + 6 references). TestMergingWindowSet updated to assertThrows(StreamException). BUILD SUCCESS.
  - Phase 2 Exit Criteria: PASS - `@SuppressWarnings({"unchecked","rawtypes"})` present at line 84 of SharedBuffer.java. Both raw casts have `// raw cast intentional - type erased at runtime` comments. Already satisfied in baseline.
  - Phase 3 Exit Criteria: PASS - `private transient K currentKeyField` confirmed (not Object). `setCurrentKey(Object key)` signature preserved. `resolveKey()` no longer has `@SuppressWarnings("unchecked")` or `(K)` cast. BUILD SUCCESS.
  - Phase 4 Exit Criteria: PASS - TestCepPatternBuilder.java exists (2 tests). TestMemoryStateBackendSnapshotRestore.java exists (2 tests: ValueState + ListState round-trip). TestWindowOperatorBehavior.java exists (6 tests: trigger/cleanup/boundary). TestCepRuntime evaluation: 6 existing CEP test files provide adequate coverage. BUILD SUCCESS.
  - Phase 5 Exit Criteria: PASS - connector/package-info.java created with optional dependency docs. JdbcCheckpointStorage has class-level Javadoc mentioning nop-dao dependency. 6 source files import ordering fixed. BUILD SUCCESS.
  - Closure Gates: All 9 gates checked
  - Anti-Hollow: All changes are concrete code/test/docs. No hollow implementations.
  - Deferred items: 15-01 (watch-only residual, Plan 79 verified), 15-05 (watch-only residual), 02-02 (optimization candidate), 03-01 (optimization candidate), 03-02 (optimization candidate), 03-09 (optimization candidate), 02-05 (optimization candidate) - all properly classified

Follow-up:

- P3 发现修复（~20 项）
- 架构级重构设计文档（api 模块填充、core 模块职责边界、GraphModelCheckpointExecutor 非静态化）
- 02-01 两个窗口算子统一（架构级，需独立设计文档）
