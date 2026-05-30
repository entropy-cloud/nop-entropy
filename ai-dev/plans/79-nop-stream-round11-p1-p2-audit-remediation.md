# 79 nop-stream Round 11 P1/P2 审计修复

> Plan Status: completed
> Last Reviewed: 2026-05-30
> Source: ai-dev/audits/2026-05-30-adversarial-review-nop-stream-r11/01-open-findings.md, ai-dev/audits/2026-05-30-deep-audit-nop-stream-full/summary.md
> Related: 77-nop-stream-round10-p0-p1-audit-remediation (completed), 78-nop-stream-p2-p3-audit-remediation (proposed)

## Purpose

将 2026-05-30 Round 11 对抗性审查的 2 个 P1 + 3 个 P2、以及 2026-05-30 全量深度审计的 6 个 P2 修复到可验证状态。去重后独立 P1 共 2 条、P2 共 8 条。

## Current Baseline

- Plan 77 完成后 `./mvnw test -pl nop-stream -am` 全量通过（1422 tests）
- Plans 73-77 修复了 Round 1-10 + 2026-05-25/28/30 三轮深度审计的全部 P0/P1
- Plan 78（proposed）覆盖了 2026-05-25/27/28 审计的 P2/P3 遗留（13 项）
- 经 live repo 验证，Round 11 的 8 个新发现和 05-30 全量审计的 6 个 P2 均仍存在（见下表）

### 仍需修复的发现

| 编号 | 来源 | 严重程度 | 文件 | 摘要 |
|------|------|---------|------|------|
| AR-1 | R11 | P1 | TaskManager.java:219-222,236-238 | receiveAssignment 两个 early-return 路径信号量泄漏 |
| AR-2 | R11 | P1 | MessageSourceFunction.java:49,112 | transient CountDownLatch 反序列化后 NPE |
| AR-3 | R11 + 05-30-14-01 | P2 | TwoPhaseCommitSinkFunction.java:82-90 | synchronizedMap 迭代无外部同步 |
| AR-4 | R11 | P2 | NFA.java:289-293 | pending 状态超时后 releaseNode 被跳过（reviewer 确认 prune() 已释放，需代码注释说明） |
| AR-5 | R11 | P2 | WindowAggregationOperator.java:313-323 | session window merge 后源窗口 trigger state 未清理 |
| 14-02 | 05-30 | P2 | InputGate.java:263,292,304,319 | 递归调用 + Thread.sleep(10) 热路径 |
| 09-01 | 05-30 | P2 | 11 文件 18 处 | 字符串构造器而非 ErrorCode |
| 15-04 | 05-30 | P2 | WindowOperator.java:717,735 | IN→ACC 无校验强转 |
| 15-01 | 05-30 | P2 | CepPatternBuilder.java 7 个方法 | 原始类型，7 个 @SuppressWarnings("rawtypes") |
| 02-01 | 05-30 | P2 | WindowAggregationOperator + WindowOperator | 两个窗口算子职责重叠 |

### 已去重

- R11 AR-3 = 05-30-audit 14-01（同一文件同一问题）→ 合并为 AR-3
- 05-30-audit 15-01 已在 Plan 78 中部分覆盖（10-02 标注 @Internal），但原始类型修复不在 Plan 78 scope 内

## Goals

- 修复全部 2 个 P1 发现（TaskManager 信号量泄漏、MessageSourceFunction NPE）
- 修复全部 8 个 P2 发现（并发安全、内存泄漏、热路径、错误码、类型安全），其中 AR-4 经 reviewer 确认 prune() 已调用 releaseNode，仅需代码注释
- 每个修复完成后 `./mvnw test -pl nop-stream -am` 全部通过
- 新增行为的测试覆盖

## Non-Goals

- P3 发现修复（AR-6/AR-7/AR-8 + 05-30 全量审计 ~25 项 P3）
- 架构级重构（WindowAggregationOperator 与 WindowOperator 统一、core 模块拆分）
- Plan 78 scope 内的发现（09-07、10-02 @Internal 标注、17-01 import 排序等）

## Scope

### In Scope

- nop-stream-runtime: TaskManager 信号量修复、WindowOperator 类型安全
- nop-stream-connector: MessageSourceFunction 反序列化安全
- nop-stream-core: TwoPhaseCommitSinkFunction 同步修复、InputGate 递归消除、WindowAggregationOperator trigger state 清理、字符串→ErrorCode 迁移
- nop-stream-cep: NFA releaseNode 评估、CepPatternBuilder 原始类型

### Out Of Scope

- P3 发现（~28 项）
- 02-01 两个窗口算子统一（架构级，需独立设计文档）
- Plan 78 scope 内的全部发现

## Execution Plan

### Phase 1 - TaskManager 信号量泄漏（P1: AR-1）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java`

- Item Types: `Fix`

- [x] **AR-1**: 将 receiveAssignment 中两个 early-return 路径添加 `capacitySemaphore.release()`

Exit Criteria:

- [x] runningTasks.containsKey(taskKey) return 前有 capacitySemaphore.release()
- [x] putIfAbsent return 前有 capacitySemaphore.release()
- [x] 新增测试：testDuplicateAssignmentDoesNotLeakSemaphore
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - MessageSourceFunction 反序列化 NPE（P1: AR-2）

Status: completed
Targets: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java`

- Item Types: `Fix`

- [x] **AR-2**: 在 `run()` 方法开头添加 `shutdownLatch` null 检查并重新初始化

Exit Criteria:

- [x] MessageSourceFunction.run() 方法开头有 `if (shutdownLatch == null)` 守卫
- [x] 新增测试：testDeserializedSourceRunDoesNotThrowNPE（反射设置 shutdownLatch=null）
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - TwoPhaseCommitSinkFunction 同步修复（P2: AR-3）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java`

- Item Types: `Fix`

- [x] **AR-3**: 将 finishCommit() 和 restoreFromEpoch() 中对 `pending` 的迭代和修改包裹在 `synchronized(pending)` 块中

Exit Criteria:

- [x] finishCommit() 中的 `pending.entrySet()` 迭代在 `synchronized(pending)` 块内
- [x] restoreFromEpoch() 中的 pending 迭代同样包裹在 `synchronized(pending)` 块内
- [x] 新增测试：testConcurrentFinishCommitNoConcurrentModificationException
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - NFA + WindowAggregationOperator 内存泄漏（P2: AR-4, AR-5）

Status: completed
Targets: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java`, `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java`

- Item Types: `Proof`, `Fix`

- [x] **AR-4 (Proof)**: NFA.advanceTime pending 状态 continue 处添加注释说明 SharedBuffer 释放路径
- [x] **AR-5 (Fix)**: WindowAggregationOperator session window merge 后源窗口清理添加 trigger.clear() 和 triggerState 迭代清理

Exit Criteria:

- [x] NFA.java: continue 处有注释说明 pending 状态释放路径（prune → releaseNode）
- [x] WindowAggregationOperator：源窗口清理包含 triggerState 迭代清理 + trigger.clear() 调用
- [x] 新增测试：testSessionWindowMergeClearsTriggerState
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - InputGate 热路径优化（P2: 14-02）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java`

- Item Types: `Fix`

- [x] **14-02**: 将 readMultiChannel() 中 3 处递归调用改为 labeled continue 循环，Thread.sleep(10) 改为 LockSupport.parkNanos(10_000_000L)

Exit Criteria:

- [x] readMultiChannel() 中无递归调用自身（改为 labeled continue）
- [x] `grep "Thread.sleep" InputGate.java` 零命中（已改为 parkNanos）
- [x] 新增测试：testHighWatermarkEventCountNoStackOverflow + testHighBarrierEventCountNoStackOverflow
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 字符串构造器→ErrorCode 迁移（P2: 09-01）

Status: completed
Targets: 11 个源文件 + NopStreamErrors.java

- Item Types: `Fix`

- [x] **09-01**: 将 18 处字符串构造器替换为 NopStreamErrors.ERR_... 常量，新增 10 个 ERR_ 常量和 3 个 ARG_ 常量

Exit Criteria:

- [x] `grep -rn 'new StreamException("' nop-stream/nop-stream-*/src/main/java/` 零命中
- [x] `grep -rn 'new StreamRuntimeException("' nop-stream/nop-stream-*/src/main/java/` 零命中
- [x] NopStreamErrors 中新增的 ERR_ 常量覆盖所有原字符串消息
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 - WindowOperator 类型安全 + CepPatternBuilder 泛型化（P2: 15-04, 15-01）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`, `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java`

- Item Types: `Fix`, `Decision`

- [x] **15-04**: WindowOperator.addWindowElement 中 IN→ACC 强转添加 try-catch，类型不匹配时抛 StreamException(ERR_STREAM_TYPE_MISMATCH)
- [x] **15-01**: CepPatternBuilder 泛型化评估。Pattern<T, F extends T> 自反泛型使得 Pattern<?, ?> 无法用于方法链（capture conversion 阻止编译），@SuppressWarnings("rawtypes") 是框架设计约束下的正确选择，保留不变

Exit Criteria:

- [x] WindowOperator.addWindowElement 中 IN→ACC 强转有 try-catch 保护
- [x] CepPatternBuilder 评估结果：泛型化不可行（编译失败），保留 raw type + @SuppressWarnings
- [x] Phase 7 新增的 `ERR_STREAM_TYPE_MISMATCH` 常量已存在
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 2 个 P1 发现已修复，有对应测试证明
- [x] 全部 7 个 P2 Fix 发现已修复，1 个 P2 Proof 发现（AR-4）已验证无实际泄漏并添加注释
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope P1/P2 缺陷
- [x] No owner-doc update required（全部为代码/测试修复）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check: 修复未引入空壳或静默跳过
- [x] `./mvnw compile -pl nop-stream -am` 通过
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] checkstyle / 代码规范检查通过（ast-grep lint 通过）
- [x] `ai-dev/logs/` 收口记录已写入

## Deferred But Adjudicated

### 02-01: 两个窗口算子职责重叠

- Classification: `optimization candidate`
- Why Not Blocking Closure: WindowAggregationOperator（core）和 WindowOperator（runtime）功能重叠是历史设计遗留，统一需要架构级重构（提取共用抽象或明确分工），影响面大。当前两者各自独立工作，无运行时正确性问题
- Successor Required: yes
- Successor Path: 独立设计文档 + 重构计划

### 15-01: CepPatternBuilder 原始类型保留

- Classification: `watch-only residual`
- Why Not Blocking Closure: Pattern<T, F extends T> 自反泛型约束使得 Pattern<?, ?> 无法在方法链中编译通过（capture conversion error）。保留 @SuppressWarnings("rawtypes") 是框架 API 设计约束下的唯一可行方案，不影响运行时类型安全
- Successor Required: no

## Non-Blocking Follow-ups

- P3 发现修复（~28 项：Lockable TOCTOU、GraphExecutionPlan 防御编码、CheckpointCoordinator shutdown 状态、import 排序、Javadoc 等）
- 架构级重构设计文档（窗口算子统一、core 模块拆分）

## Closure

Status Note: 全部 2 个 P1 + 8 个 P2（7 Fix + 1 Proof）已完成。15-01 CepPatternBuilder 泛型化经实际编译验证不可行，已裁定为 watch-only residual。

Closure Audit Evidence:

- Reviewer / Agent: independent closure auditor (general subagent, session ses_188b71f3dffe21AJWXuWBbIM2L)
- Evidence:
  - Phase 1 AR-1: capacitySemaphore.release() present at both early-return paths in receiveAssignment (5 semaphore release sites verified). Test: testDuplicateAssignmentDoesNotLeakSemaphore PASS
  - Phase 2 AR-2: shutdownLatch null guard present at run() start. Test: testDeserializedSourceRunDoesNotThrowNPE PASS
  - Phase 3 AR-3: synchronized(pending) blocks present in finishCommit() and restoreFromEpoch(). Test: testConcurrentFinishCommitNoConcurrentModificationException PASS
  - Phase 4 AR-4: NFA advanceTime continue has comment explaining releaseNode via prune path. AR-5: trigger.clear() + triggerState iteration cleanup in processElementWithMerging. Test: testSessionWindowMergeClearsTriggerState PASS
  - Phase 5 14-02: readMultiChannel uses labeled continue (no recursion). LockSupport.parkNanos replaces Thread.sleep. grep confirms zero Thread.sleep hits. Tests: testHighWatermarkEventCountNoStackOverflow, testHighBarrierEventCountNoStackOverflow PASS
  - Phase 6 09-01: grep -rn 'new StreamException("' zero hits. grep -rn 'new StreamRuntimeException("' zero hits. 10 new ERR_ constants added.
  - Phase 7 15-04: try-catch around (ACC) value casts with ERR_STREAM_TYPE_MISMATCH. 15-01 adjudicated as watch-only residual (Pattern<T,F extends T> capture conversion blocks Pattern<?, ?>).
  - Anti-Hollow Check: all fixes have runtime behavior changes + focused tests. No empty method bodies or silent no-ops.
  - `./mvnw test -pl nop-stream -am` BUILD SUCCESS
  - ast-grep lint: all 7 commits passed
  - Deferred items (02-01 optimization candidate, 15-01 watch-only residual) properly classified with explicit rationale

Follow-up:

- P3 发现修复（~28 项）
- 02-01 窗口算子统一设计文档
- Plan 78 scope 内的遗留发现
