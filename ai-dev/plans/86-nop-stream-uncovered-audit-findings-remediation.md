# 86 nop-stream 未覆盖审计发现修复

> Plan Status: planned
> Last Reviewed: 2026-05-31
> Source: ai-dev/audits/2026-05-31-deep-audit-nop-stream/summary.md（维度 09/14/16/02）+ live repo 交叉验证
> Related: 85-nop-stream-21-dim-deep-audit-remediation (completed), 84-nop-stream-remaining-audit-findings-remediation (completed)

## Purpose

将 2026-05-31 深度审计中未被 Plans 81-85 覆盖的 P1 发现修复到可验证状态。Plans 81-85 覆盖了 R12/R13 对抗性审查和深度审计的大部分发现，但审计维度编号与 plan 编号的错位导致 12 个 P1 发现被遗漏（特别是 CEP 错误处理 09-01~09-06 和并发安全 14-02/14-03）。

## Current Baseline

- Plans 81-85 完成后 `./mvnw test -pl nop-stream -am` 全量通过（360 tests）
- 经独立子 agent 对 Plans 81-85 与 2026-05-31 深度审计的逐条交叉验证，确认以下 12 个 P1 发现仍存在于 live code 中且未被任何 plan 覆盖
- 16-01（P0 WindowAggregationOperator session merge 未测试）经 live repo 验证已有部分覆盖（TestSessionWindowIntegration），降级为 P2
- 02-01（双窗口算子实现）为架构级决策，明确排除在本次 scope 外

### 待修复发现

| 编号 | 严重程度 | 维度 | 文件 | 摘要 |
|------|---------|------|------|------|
| 09-01 | P1 | 错误处理 | SharedBuffer.java（7 处） | NopException.adapt() 丢失模块异常类型，ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED 已定义但从未使用 |
| 09-02 | P1 | 错误处理 | CepOperator.java（3 处） | CEP 核心热路径 NopException.adapt() 丢失异常类型 |
| 09-03 | P1 | 错误处理 | SharedBufferAccessor.java:220 | match 物化路径 NopException.adapt() 丢失异常类型 |
| 09-04 | P1 | 错误处理 | CepPatternBuilder.java:160 | 类加载失败被 adapt() 包装为通用异常 |
| 09-05 | P1 | 错误处理 | NFACompiler.java:108 | bare IllegalStateException 在编译器公共入口点 |
| 09-06 | P1 | 错误处理 | NoSkipStrategy.java:43,48 | "should never happen" 分支用 bare IllegalStateException |
| 14-02 | P1 | 异步事务 | CheckpointCoordinator.java:520 | currentFingerprint 缺少 volatile，跨线程可见性问题 |
| 14-03 | P1 | 异步事务 | PendingCheckpoint.java:113-183 | 混合同步模型：acknowledgeTask 是 synchronized 但 abort/dispose 在 synchronized 外修改 isDisposed |
| 02-06 | P1 | 模块职责 | JobCoordinator.java:415 | instanceof TaskManager 绕过 RPC 接口，分布式部署时 fencing token 更新被跳过 |
| 16-02 | P1 | 测试覆盖 | WindowAggregationFunction.java:15 | merge() 默认抛异常且从未被测试 |
| 16-04 | P1 | 测试覆盖 | CheckpointCoordinator.java:239 | EpochManifest 存储失败路径未测试 |
| 16-05 | P1 | 测试覆盖 | JobCoordinator.java:470 | EXPORT_SAVEPOINT 终止模式无测试 |

## Goals

- 修复全部 6 个 CEP 错误处理发现（09-01~09-06）：将 NopException.adapt() 替换为 StreamException(ErrorCode, cause)，将 bare IllegalStateException 替换为 StreamException(ErrorCode)
- 修复 2 个并发安全发现（14-02/14-03）：volatile 修复 + 同步模型统一
- 修复 1 个模块职责发现（02-06）：将 updateFencingToken 添加到 IStreamTaskRpcService 接口
- 填补 3 个测试覆盖盲区（16-02/16-04/16-05）
- 每个修复完成后 `./mvnw test -pl nop-stream -am` 全部通过

## Non-Goals

- 架构级重构（WindowAggregationOperator 与 WindowOperator 统一、RuntimeContext 完整 API）
- P2/P3 发现修复（包括 16-01 降级后、16-06 并发 checkpoint 测试、02-01 双窗口实现）
- CEP ClosureCleaner 等效机制
- GraphExecutionPlan OperatorChain 隔离（Plan 82 Deferred R13-AR-12）
- fraud-example 端到端验证

## Scope

### In Scope

- nop-stream-cep: SharedBuffer、SharedBufferAccessor、CepOperator、CepPatternBuilder、NFACompiler、NoSkipStrategy
- nop-stream-runtime: CheckpointCoordinator（volatile）、PendingCheckpoint（同步模型）、JobCoordinator（fencing token + EXPORT_SAVEPOINT 测试）、IStreamTaskRpcService（接口扩展）
- nop-stream-core: WindowAggregationFunction（merge 测试）
- 对应新增/修改测试

### Out Of Scope

- 架构级重构
- P2/P3 发现
- Deferred items from Plans 82/83/84/85

## Execution Plan

### Phase 1 - CEP 错误处理统一（09-01~09-06）

Status: planned
Targets: `nop-stream-cep` 模块

- Item Types: `Fix`

- [ ] **09-01**: `SharedBuffer.java` 7 处 `NopException.adapt(exception)` 替换为 `throw StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, exception).param(ARG_DETAIL, "<operation>")` 或对应的已有 ErrorCode
- [ ] **09-02**: `CepOperator.java` 3 处 `NopException.adapt(e)` 替换为 `throw StreamException(ERR_STREAM_STATE_ERROR, e).param(...)` 或对应 ErrorCode
- [ ] **09-03**: `SharedBufferAccessor.java:220` `NopException.adapt(ex)` 替换为 `throw StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, ex).param(ARG_DETAIL, "match materialization failed")`
- [ ] **09-04**: `CepPatternBuilder.java:160` `NopException.adapt(e)` 替换为 `throw new MalformedPatternException(ERR_CEP_MALFORMED_PATTERN, e).param(ARG_DETAIL, "class loading failed")`。使用已有 `ERR_CEP_MALFORMED_PATTERN`（NopCepErrors:48）
- [ ] **09-05**: `NFACompiler.java:108` `new IllegalStateException(...)` 替换为 `throw new MalformedPatternException(ERR_CEP_MALFORMED_PATTERN).param(ARG_DETAIL, "Compiler produced no start state. This is a bug in NFAFactoryCompiler.")`。使用已有 `ERR_CEP_MALFORMED_PATTERN`
- [ ] **09-06**: `NoSkipStrategy.java:43,48` `new IllegalStateException(...)` 替换为 `throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_DETAIL, "This should never happen. Please file a bug.")`。使用已有 `ERR_STREAM_UNSUPPORTED`

Exit Criteria:

- [ ] SharedBuffer 7 处 NopException.adapt() 全部替换为 StreamException + ErrorCode
- [ ] CepOperator 3 处 NopException.adapt() 全部替换为 StreamException + ErrorCode
- [ ] SharedBufferAccessor、CepPatternBuilder 各 1 处 NopException.adapt() 全部替换
- [ ] NFACompiler、NoSkipStrategy bare IllegalStateException 全部替换为模块类型异常（MalformedPatternException / StreamException + 已有 ErrorCode）
- [ ] grep `NopException.adapt` 在 nop-stream-cep 模块返回 0 结果（不含 import 行和注释）
- [ ] grep `new IllegalStateException` 在 NoSkipStrategy.java 和 NFACompiler.java 返回 0 结果（不含注释）
- [ ] 新增测试：验证 SharedBuffer/CepOperator 异常被包装为 StreamException 而非 NopException
- [ ] `./mvnw test -pl nop-stream -am` 全部通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 并发安全修复（14-02, 14-03）

Status: planned
Targets: `nop-stream-runtime/.../checkpoint/`

- Item Types: `Fix`

- [ ] **14-02**: `CheckpointCoordinator.java:520` `currentFingerprint` 字段添加 `volatile` 修饰符。验证：该字段由 `setCurrentFingerprint()` 写入（可能在不同线程），由 checkpoint 调度线程读取
- [ ] **14-03**: `PendingCheckpoint.java` 统一同步模型：(a) `abort()` 方法添加 `synchronized` 修饰符（或改用与 `acknowledgeTask()` 一致的锁策略），(b) `dispose()` 方法添加 `synchronized` 修饰符，确保 `isDisposed` 的检查-修改在同一个锁内完成。注意：`isDisposed` 已是 volatile，ConcurrentHashMap 已防止结构性损坏，但逻辑竞态（dispose 后 acknowledgeTask 仍添加状态）需要 synchronized 保护

Exit Criteria:

- [ ] `currentFingerprint` 声明为 `private volatile StreamModelFingerprint`
- [ ] `abort()` 和 `dispose()` 都在 `synchronized` 块内修改 `isDisposed`
- [ ] `acknowledgeTask()` 的 `isDisposed` 检查和后续操作在同一 `synchronized` 块内，与 `abort()/dispose()` 互斥
- [ ] 新增测试：TestPendingCheckpoint 验证并发 abort + acknowledgeTask 的竞态安全
- [ ] 新增测试：TestCheckpointCoordinator 验证 currentFingerprint 跨线程可见性（volatile write-read）
- [ ] **接线验证**：abort/dispose 的 synchronized 不与 CheckpointCoordinator 其他 synchronized 方法形成死锁（检查锁获取顺序）
- [ ] `./mvnw test -pl nop-stream -am` 全部通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 模块职责修复（02-06）

Status: planned
Targets: `nop-stream-runtime/.../coordinator/`

- Item Types: `Fix`

- [ ] **02-06**: 在 `IStreamTaskRpcService` 接口中添加 `void updateFencingToken(String newToken)` 方法（参数类型为 `String`，与 `TaskManager.updateFencingToken(String)` 行 346 签名一致）。`TaskManager` 已有实现（行 346-349），无需修改。将 `JobCoordinator.globalRecovery()` 行 415-416 的 `if (rpc instanceof TaskManager) { ((TaskManager) rpc).updateFencingToken(newToken); }` 改为直接调用 `rpc.updateFencingToken(newToken)`，消除 instanceof 下转型。注意：`TestJobCoordinator.java:448` 的 `MockTaskRpcService` 也需要添加该方法的空实现

Exit Criteria:

- [ ] `IStreamTaskRpcService` 接口包含 `void updateFencingToken(String newToken)` 方法声明
- [ ] `TaskManager` 已有实现无需修改（签名一致）
- [ ] `TestJobCoordinator` 中的 `MockTaskRpcService` 添加空实现
- [ ] `JobCoordinator.globalRecovery()` 不再使用 `instanceof TaskManager` 下转型
- [ ] 新增测试：验证 fencing token 更新通过接口调用（非 instanceof 路径）
- [ ] **接线验证**：所有 IStreamTaskRpcService 实现（TaskManager 及任何 RPC proxy stub）都实现了新方法
- [ ] `./mvnw test -pl nop-stream -am` 全部通过
- [ ] 若改变了 live baseline（接口签名变更）：检查是否需要更新 `docs-for-ai/` 相关文档
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 测试覆盖填补（16-02, 16-04, 16-05）

Status: planned
Targets: `nop-stream-core/.../operators/`, `nop-stream-runtime/`

- Item Types: `Proof`

- [ ] **16-02**: 为 `WindowAggregationFunction.merge()` 添加测试：(a) 验证默认 `merge()` 抛出 `UnsupportedOperationException`，(b) 验证自定义实现（如 sum merge）在 session window 合并场景下正确工作
- [ ] **16-04**: 为 `CheckpointCoordinator` EpochManifest 存储失败路径添加测试：mock `storeEpochManifest` 抛出异常，验证 (a) 已存储的 checkpoint 数据被清理或标记为 orphaned，(b) 异常正确传播
- [ ] **16-05**: 为 `JobCoordinator` EXPORT_SAVEPOINT 终止模式添加测试：验证 (a) savepoint 触发，(b) 数据导出，(c) 作业继续运行（不终止）

Exit Criteria:

- [ ] TestWindowAggregationFunction 包含 merge() 默认异常测试和自定义 merge 正确性测试
- [ ] TestCheckpointCoordinator 包含 EpochManifest 失败路径测试
- [ ] TestJobCoordinator 包含 EXPORT_SAVEPOINT 终止模式测试
- [ ] 新增测试至少覆盖正常路径和主要失败路径
- [ ] `./mvnw test -pl nop-stream -am` 全部通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 全部 12 个 in-scope P1 发现已修复（09-01~09-06、14-02、14-03、02-06、16-02、16-04、16-05）
- [ ] CEP 模块 NopException.adapt() 和 bare IllegalStateException 已清除
- [ ] CheckpointCoordinator/PendingCheckpoint 并发安全已修复
- [ ] JobCoordinator 不再使用 instanceof TaskManager 下转型
- [ ] 测试覆盖盲区已填补
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）组件间调用链在运行时确实连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile -pl nop-stream -am`
- [ ] `./mvnw test -pl nop-stream -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 14-01 CheckpointCoordinator registerTask/unregisterTask check-then-act 竞态

- Classification: `watch-only residual`
- Why Not Blocking Closure: Plan 85 Phase 2 已将 `tasksToAcknowledge` 改为 volatile + copy-on-write 模式，`registerTask()` 和 `unregisterTask()` 使用 copy-on-write 原子赋值。审计报告的 check-then-act 竞态已被 copy-on-write 模式消除（每次修改构建新 Set 后 volatile 赋值，读端无锁读取完整快照）。经 live repo 验证，`registerTask()`（行 354）和 `unregisterTask()`（行 358）已使用 copy-on-write
- Successor Required: no

### 16-01 WindowAggregationOperator session merge 测试补充

- Classification: `optimization candidate`
- Why Not Blocking Closure: 经 live repo 验证，TestSessionWindowIntegration 和 TestSessionWindowE2E 已提供 session window merge 路径的基本覆盖。边缘场景（3+ 窗口级联合并、timer 清理、trigger state 迁移）缺少专门测试，但核心路径已有保障
- Successor Required: no

### 16-06 GraphModelCheckpointExecutor 并发 checkpoint 测试

- Classification: `optimization candidate`
- Why Not Blocking Closure: 需要多线程测试基础设施（如 CountDownLatch + barrier 注入），实现成本高且该组件的并发正确性已由 ConcurrentHashMap 和 volatile 字段部分保障
- Successor Required: no

### 02-01 双窗口算子实现（WindowAggregationOperator vs WindowOperator）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 架构级重构，需要设计文档和影响评估。已被 Plans 83/85 明确排除
- Successor Required: yes
- Successor Path: 待 WindowOperator 统一设计文档完成后启动

## Non-Blocking Follow-ups

- P3 发现修复（20+ 项）
- CEP ClosureCleaner 等效机制
- GraphExecutionPlan OperatorChain 隔离完整修复（Plan 82 Deferred R13-AR-12）
- GraphModelCheckpointExecutor 类拆分（Plan 83 Deferred 02-02）
- fraud-example 端到端验证
- 全模块 import 风格统一

## Closure

Status Note: 待执行

Closure Audit Evidence:

- Reviewer / Agent: 待独立 closure audit
- Evidence: 待执行后填写
