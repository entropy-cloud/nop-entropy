# 84 nop-stream 遗留审计发现修复

> Plan Status: completed
> Last Reviewed: 2026-05-31
> Source: ai-dev/audits/2026-05-31-deep-audit-nop-stream-full/summary.md + Plan 77 Non-Blocking Follow-ups (R10 AR-12/AR-13)
> Related: 83-nop-stream-deep-audit-2026-05-31-remediation (completed), 82-nop-stream-round13-audit-remediation (completed), 78-nop-stream-p2-p3-audit-remediation (completed)

## Purpose

将两批遗留审计发现修复到可验证状态：(1) 2026-05-31 全量深度审计（6 维度）的 2×P1 + 13×P2 有效发现，(2) Round 10 遗留的 2×P2 Pattern API 验证缺失（AR-14 经 live repo 验证为不准确——`Quantifier.combinations()` 已含完整验证）。共计 2×P1 + 15×P2 待修复。

## Current Baseline

- Plan 83 完成后 `./mvnw test -pl nop-stream -am` 全量通过（354 tests）
- Plans 73-83 覆盖了 Round 1-13 对抗性审查 + 2026-05-25/28/30/31 深度审计的全部 P0/P1 生产代码缺陷
- 2026-05-31-deep-audit-nop-stream-full（6 维度 36 发现）无对应 remediation plan
- Round 10 的 AR-12/AR-13（Pattern API 验证）从 Plan 77 Non-Blocking Follow-ups 起未被任何后续 plan 拾取
- 经 live repo 验证，17/18 个发现仍然存在，1 个（AR-14）不准确（验证见下）

### 审计发现更正

- **R10-AR-14**（`allowCombinations()` 无验证）经 live repo 验证**不准确**。`Quantifier.combinations()`（Quantifier.java:89-101）已含 3 项验证：检查 SINGLE 量词、STRICT 策略、SKIP_TILL_ANY 策略。从本 plan scope 中移除

### 待修复发现

| 编号 | 严重程度 | 维度 | 文件 | 摘要 |
|------|---------|------|------|------|
| 21-01 | P1 | 测试有效性 | TestNFAExtended.java:626 | 永真断言 `assertTrue(matches.isEmpty() \|\| !matches.isEmpty())` |
| 21-02 | P1 | 测试有效性 | TestGreedy.java | 全部 6 个测试仅 `>= 1` 断言，greedy 语义零有效覆盖 |
| 01-01 | P2 | 依赖 | nop-stream-connector/pom.xml | nop-message-core optional 但仅 test 使用 |
| 01-02 | P2 | 依赖 | connector/package-info.java | 文档声称需要 nop-message-core，实际只需 nop-api-core |
| 09-01 | P2 | 错误处理 | WindowOperator.java:377,390 | 合并窗口校验裸 UnsupportedOperationException |
| 09-02 | P2 | 错误处理 | KeyedStreamImpl.java:181-237 | 6 处 API 参数校验裸 UnsupportedOperationException |
| 09-03 | P2 | 错误处理 | Pattern.java/NFACompiler.java 等 | MalformedPatternException 全用 String 构造器，ERR_CEP_MALFORMED_PATTERN 零引用 |
| 09-07 | P2 | 错误处理 | Output.java:74 | emit() 对未知 StreamElement 用裸 UnsupportedOperationException |
| 15-01 | P2 | 类型安全 | NFA.java:188,201 | IterativeCondition 原始类型（零成本修复） |
| 15-03 | P2 | 类型安全 | WindowOperator.java:719-757 | ACC 类型参数运行时无约束 |
| 15-04 | P2 | 类型安全 | WindowOperator.java:317-327 | restoreState instanceof Map 无 value 类型验证 |
| 16-01 | P2 | 测试覆盖 | TestGraphModelCheckpointExecutor.java | 3/8 测试仅验证 CheckpointConfig getter |
| 16-04 | P2 | 测试覆盖 | CheckpointCoordinator.java:361-391 | scheduleTimeout 和 cleanupOldCheckpoints 无测试 |
| 21-04 | P2 | 测试有效性 | TestCheckpointCoordinator.java:219 | testSchedulerStartStop 零断言 |
| 21-05 | P2 | 测试有效性 | 多个测试文件 | 22 处 `size() >= 1` 弱断言 |
| R10-AR-12 | P2 | API 验证 | Pattern.java:474-484 | `times(from, to)` 无 `from <= to` 校验 |
| R10-AR-13 | P2 | API 验证 | Pattern.java:510-516 | `timesOrMore(int)` 无 `times > 0` 校验 |

## Goals

- 修复全部 2 个 P1 发现（永真断言 + greedy 测试零覆盖）
- 修复全部 15 个 P2 发现（错误处理一致性 + 类型安全 + 测试有效性 + 依赖声明 + API 验证）
- 每个修复完成后 `./mvnw test -pl nop-stream -am` 全部通过

## Non-Goals

- P3 发现修复（14 项：placeholder 清理、文件体量、测试基础设施、demo 代码等）
- 架构级重构（WindowOperator ACC 类型参数重新设计、core 模块拆分、CEP ClosureCleaner）
- fraud-example 端到端验证
- GraphModelCheckpointExecutor 803 行类拆分（Plan 83 Deferred 02-02）
- GraphExecutionPlan taskIndex==0 共享 OperatorChain（Plan 82 Deferred R13-AR-12）
- 全模块 import 风格统一

## Scope

### In Scope

- nop-stream-core: KeyedStreamImpl、Output、NFA 泛型
- nop-stream-cep: Pattern API 验证、TestNFAExtended、TestGreedy、NFA 泛型、MalformedPatternException 调用点
- nop-stream-runtime: WindowOperator 错误处理/类型验证、CheckpointCoordinator 测试、TestGraphModelCheckpointExecutor
- nop-stream-connector: pom.xml scope 修正、package-info.java 文档修正
- 对应新增/修改测试

### Out Of Scope

- P3 发现
- 架构级重构
- Deferred items from Plans 78/82/83

## Execution Plan

### Phase 1 - P1 测试有效性修复（21-01, 21-02）

Status: completed
Targets: `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/TestNFAExtended.java`, `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/TestGreedy.java`

- Item Types: `Fix`

- [x] **21-01**: 修复 `TestNFAExtended.testPendingStateTimeoutHandling` 的永真断言。替换 `assertTrue(matches.isEmpty() || !matches.isEmpty())` 为对 timeout matches 具体内容的精确断言
- [x] **21-02**: 修复 `TestGreedy` 全部 6 个测试的弱断言。每个测试添加精确断言（匹配数量 `assertEquals`、匹配内容验证、greedy vs non-greedy 差异验证）

Exit Criteria:

- [x] TestNFAExtended 无永真断言，timeout 测试验证具体 match 内容
- [x] TestGreedy 所有测试使用精确匹配数量断言（`assertEquals`），至少 2 个测试验证 greedy vs non-greedy 行为差异
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 错误处理一致性（09-01, 09-02, 09-03, 09-07）

Status: completed
Targets: `nop-stream/nop-stream-runtime/.../operators/windowing/WindowOperator.java`, `nop-stream/nop-stream-core/.../datastream/KeyedStreamImpl.java`, `nop-stream/nop-stream-cep/.../pattern/Pattern.java`, `nop-stream/nop-stream-cep/.../nfa/NFACompiler.java`, `nop-stream/nop-stream-cep/.../pattern/conditions/Quantifier.java`, `nop-stream/nop-stream-cep/.../nfa/NFAStateNameHandler.java`, `nop-stream/nop-stream-core/.../operators/Output.java`, `nop-stream/nop-stream-cep/.../NopCepErrors.java`, `nop-stream/nop-stream-core/.../exceptions/NopStreamErrors.java`

- Item Types: `Fix`

- [x] **09-01**: `WindowOperator.java:377,390` 两处 `UnsupportedOperationException` 替换为 `StreamException(ERR_STREAM_WINDOW_MERGE_INVALID_WATERMARK)` 和 `StreamException(ERR_STREAM_WINDOW_MERGE_INVALID_PROCESSING_TIME)`。需在 `NopStreamErrors` 中新增对应错误码
- [x] **09-02**: `KeyedStreamImpl.java:181-237` 6 处 `UnsupportedOperationException` 替换为 `StreamException(ERR_STREAM_*)`.param(...)。注意：部分为无效参数校验（需 ERR_STREAM_INVALID_ARG 类），部分为不支持的操作（需 ERR_STREAM_UNSUPPORTED_AGGREGATION 类）。执行时应按语义逐一分类选择错误码。需在 `NopStreamErrors` 中新增对应错误码
- [x] **09-03**: 将生产代码中的 `new MalformedPatternException("...")` 调用替换为使用 `ERR_CEP_MALFORMED_PATTERN`（执行时需根据 live repo 校准实际调用点数量，约 11 处 MalformedPatternException + 2 处 UnsupportedOperationException）。涉及文件：Pattern.java、NFACompiler.java、Quantifier.java、NFAStateNameHandler.java
- [x] **09-07**: `Output.java:74` `UnsupportedOperationException` 替换为 `StreamException(ERR_STREAM_UNSUPPORTED_ELEMENT_TYPE).param(...)`

Exit Criteria:

- [x] WindowOperator.java:377,390 使用 StreamException + ErrorCode
- [x] KeyedStreamImpl.java 6 处使用 StreamException + ErrorCode
- [x] MalformedPatternException 全部调用点使用 ERR_CEP_MALFORMED_PATTERN（执行前校准实际数量）
- [x] Output.java:74 使用 StreamException + ErrorCode
- [x] NopStreamErrors/NopCepErrors 新增的错误码有对应定义
- [x] 新增测试：至少覆盖 KeyedStreamImpl 的参数校验抛异常场景和 Output 的未知 StreamElement 场景
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 类型安全（15-01, 15-03, 15-04）

Status: completed
Targets: `nop-stream/nop-stream-cep/.../nfa/NFA.java`, `nop-stream/nop-stream-runtime/.../operators/windowing/WindowOperator.java`

- Item Types: `Fix`

- [x] **15-01**: `NFA.java:188,201` 两处 `IterativeCondition condition` 添加泛型参数 `<T>`
- [x] **15-03**: `WindowOperator.java:719-757` ACC 类型添加运行时验证。在 `addWindowElement` 的累加器路径和非累加器路径的 `(ACC)` 强转前，增加 `SimpleAccumulator.class.isInstance()` 或 `valueClass.isInstance()` 检查。注意：这是行为变更——之前静默接受的值现在可能抛异常，需验证现有测试仍通过
- [x] **15-04**: `WindowOperator.java:317-327` `restoreState` 中 `instanceof Map` 后增加 value 类型验证：遍历 map values 检查 `SimpleAccumulator.class.isInstance()`，不匹配时抛出 `StreamException(ERR_STREAM_TYPE_MISMATCH)`

Exit Criteria:

- [x] NFA.java 无 raw type IterativeCondition（编译无 warning）
- [x] WindowOperator.addWindowElement 有运行时类型验证
- [x] WindowOperator.restoreState 有 value 类型验证
- [x] 新增测试：WindowOperator.restoreState 类型不匹配抛异常
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 测试覆盖与有效性（16-01, 16-04, 21-04, 21-05）

Status: completed
Targets: `nop-stream/nop-stream-runtime/.../execution/TestGraphModelCheckpointExecutor.java`, `nop-stream/nop-stream-runtime/.../checkpoint/TestCheckpointCoordinator.java`, 多个 CEP 测试文件

- Item Types: `Fix | Proof`

- [x] **16-01**: 改写 `TestGraphModelCheckpointExecutor` 中 3 个 getter-only 测试，使其验证 `GraphModelCheckpointExecutor` 的实际行为（如 `createStorage` 路径分发、`handleJobTermination` 模式分发）。执行前需先通读 GraphModelCheckpointExecutor 理解可测试行为
- [x] **16-04**: 为 `CheckpointCoordinator.scheduleTimeout` 和 `cleanupOldCheckpoints` 添加测试（通过公共方法 `triggerCheckpoint` 和配置驱动间接测试）。执行前需先通读 CheckpointCoordinator 理解可间接测试的路径
- [x] **21-04**: 修复 `TestCheckpointCoordinator.testSchedulerStartStop` 添加断言（验证 scheduler 启动后有 checkpoint 被触发，或验证内部状态变化）
- [x] **21-05**: 替换 ~21 处 `size() >= 1` 弱断言为精确 `assertEquals(N, size())` 断言。涉及 5 个测试文件（执行前校准实际数量）：TestNFAExtended、TestGreedy、TestCepSkipStrategyE2E、TestNFA、TestNotPattern

Exit Criteria:

- [x] TestGraphModelCheckpointExecutor 所有测试验证 GraphModelCheckpointExecutor 行为而非仅 getter
- [x] CheckpointCoordinator 有 timeout 和 cleanup 的间接测试
- [x] testSchedulerStartStop 有断言
- [x] 5 个测试文件中 `size() >= 1` 弱断言替换为精确断言（实际数量执行前校准）
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 依赖声明修正（01-01, 01-02）+ Pattern API 验证（R10-AR-12, R10-AR-13）

Status: completed
Targets: `nop-stream/nop-stream-connector/pom.xml`, `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/package-info.java`, `nop-stream/nop-stream-cep/.../pattern/Pattern.java`

- Item Types: `Fix`

- [x] **01-01**: `nop-stream-connector/pom.xml` 中 `nop-message-core` 依赖从 `<optional>true</optional>` 改为 `<scope>test</scope>`。需验证 test classpath 不受影响
- [x] **01-02**: `connector/package-info.java` 修正文档：`MessageSourceFunction` / `MessageSinkFunction` 依赖 `nop-api-core` 中的 `IMessageService`，运行时需提供 `IMessageService` 实现（如 `nop-message-core`）
- [x] **R10-AR-12**: `Pattern.java:474` `times(int from, int to, ...)` 添加 `Guard.checkArgument(from <= to, ...)` 前置校验
- [x] **R10-AR-13**: `Pattern.java:510` `timesOrMore(int times, ...)` 添加 `Guard.checkArgument(times > 0, ...)` 前置校验

Exit Criteria:

- [x] nop-stream-connector/pom.xml 中 nop-message-core 为 test scope
- [x] connector/package-info.java 文档准确反映依赖关系
- [x] Pattern.times(from, to) 对 from > to 抛出异常
- [x] Pattern.timesOrMore(times) 对 times <= 0 抛出异常
- [x] 新增测试：testTimesFromGreaterThanToThrows / testTimesOrMoreZeroOrNegativeThrows
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 2 个 P1 发现已修复
- [x] 全部 15 个 P2 发现已修复
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] No owner-doc update required（均为代码/测试修复）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 已验证修复有实际行为代码，无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-stream -am`
- [x] `./mvnw test -pl nop-stream -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（无——全部发现均在 scope 内）

## Non-Blocking Follow-ups

- P3 发现修复（14 项）
- 架构级重构设计文档（WindowOperator ACC 类型参数重新设计、CEP ClosureCleaner、core 模块拆分）
- GraphModelCheckpointExecutor 803 行类拆分（Plan 83 Deferred 02-02）
- GraphExecutionPlan taskIndex==0 OperatorChain 隔离（Plan 82 Deferred R13-AR-12）
- fraud-example 端到端验证
- 全模块 import 风格统一

## Closure

Status Note: 全部 2×P1 + 15×P2 发现已修复并验证。5 个 Phase 全部完成，`./mvnw test -pl nop-stream -am` 全量通过（354+23 tests）。

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (ses_185af1c70ffeuFARuzTsbl3rOx)
- Audit Session: ses_185af1c70ffeuFARuzTsbl3rOx
- Evidence:
  - Phase 1: TestNFAExtended.testPendingStateTimeoutHandling 永真断言已替换为具体 timeout match 验证; TestGreedy 6 个测试全部添加精确断言
  - Phase 2: WindowOperator 2 处、KeyedStreamImpl 6 处、Output 1 处、MalformedPatternException 11+2 处全部替换为 ErrorCode; NopStreamErrors 新增 6 个错误码
  - Phase 3: NFA.java IterativeCondition 添加 <T> 泛型; WindowOperator.restoreState 添加 Map value 类型验证
  - Phase 4: TestGraphModelCheckpointExecutor 增强 3 个 getter 测试; TestCheckpointCoordinator 新增 timeout/cleanup 测试; testSchedulerStartStop 添加调度触发断言; 21 处 >=1 弱断言替换为精确 assertEquals
  - Phase 5: nop-message-core 改为 test scope; package-info.java 文档修正; Pattern.times 添加 from<=to 校验; Pattern.timesOrMore 添加 times>0 校验; 新增 2 个测试
  - `./mvnw test -pl nop-stream -am` BUILD SUCCESS (all tests pass)
  - Commits: b0d61f6f9 (P1), cf22c52ab (P2 errors), ea7178247 (P3 types), 5a8deb13a (P4 tests), 1d941ad88 (P5 deps+API)

Follow-up:

- P3 发现修复（14 项）
- 架构级重构（WindowOperator ACC 类型参数、CEP ClosureCleaner、core 模块拆分）
