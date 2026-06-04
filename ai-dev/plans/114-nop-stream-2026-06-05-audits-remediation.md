# 114 nop-stream 2026-06-05 Audits Remediation

> Plan Status: in progress
> Last Reviewed: 2026-06-05
> Source: `ai-dev/audits/2026-06-05-adversarial-review-nop-stream/01-open-findings.md` (18 findings) + `ai-dev/audits/2026-06-05-deep-audit-nop-stream-full/summary.md` (5 P1, ~27 P2, ~55 P3)
> Related: 113-nop-stream-2026-06-04-audits-remediation (completed), 112-nop-stream-r12-r13-residual-audit-remediation (completed), 103-nop-stream-2026-06-02-audits-remediation (completed)

## Purpose

修复 2026-06-05 两份审计（对抗性审查 18 项 + 深度审计 87 项）中经 live repo 验证仍然存在的 P1 发现，以及与 P1 直接相关的高影响 P2 发现。将 nop-stream 审计发现从"持续积累"状态收口到"关键问题已处理"。

## Current Baseline

### 已完成的相关计划

| Plan | 范围 | 状态 |
|------|------|------|
| Plan 113 | 2026-06-04 三份审计 31 phases | completed |
| Plan 112 | R12-AR-7 + R13-AR-15（2 项） | completed |
| Plan 103 | 2026-06-02 审计 18 phases | completed |

经独立子 agent 对 live repo 验证（ses_16b427745ffeDlyIZaKuLBg4PT），以下 2026-06-05 审计发现均为 **STILL OPEN**，未被任何已完成计划修复。

### 对抗性审查发现（18 项，全部经 live repo 验证）

| ID | Severity | 文件 | 问题摘要 | Live 状态 |
|----|----------|------|---------|----------|
| AR-1 | P1 | PatternStreamBuilder.java:139 | `inputSerializer=null` 硬编码，CEP 在持久化后端崩溃 | OPEN |
| AR-2 | P1 | TaskExecutor.java:331 | `awaitCompletion` 吞掉 TimeoutException 返回 true | OPEN |
| AR-3 | P1 | TimestampsAndWatermarksOperator.java:60 | Timer 线程并发访问非线程安全状态 | OPEN |
| AR-4 | P1 | StreamSourceOperator.java:195 | 从不调用 `sourceFunction.cancel()`，资源泄漏 | OPEN |
| AR-5 | P1 | AbstractStreamOperator.java:264 | 快照失败后仍传播 barrier，部分检查点不一致 | OPEN |
| AR-6 | P1 | CheckpointSerDe.java:39 | 序列化路径不一致，raw TaskStateSnapshot vs 显式 Map | OPEN |
| AR-7 | P2 | SourceEnumerator.java:61 | `splitMetadata` 不在 snapshot/restore 中，恢复后丢失 | OPEN |
| AR-8 | P1 | MessageSourceFunction.java:117 | 回调在消息服务线程调用非同步 SourceContext | OPEN |
| AR-9 | P2 | OperatorChain.java:127 | `open()` 反向遍历，注释说"正向" | OPEN |
| AR-10 | P2 | RecordWriter.java:144 | 广播部分投递导致下游不一致 | OPEN |
| AR-11 | P2 | WindowAggregationFunction.java:20 | merge() 默认抛异常，session 窗口运行时才发现 | OPEN |
| AR-12 | P2 | SharedBuffer.java:210 | hasEventInBuffer 吞异常导致重复 EventId | OPEN |
| AR-13 | P2 | JdbcCheckpointStorage.java:88 | catch-all INSERT→UPDATE 掩盖真实错误 | OPEN |
| AR-14 | P2 | JdbcClusterRegistry.java:95 | TOCTOU 竞态，并发注册失败 | OPEN |
| AR-15 | P2 | GraphModelCheckpointExecutor.java:659 | savepoint 恢复用当前 jobId，查不到旧 savepoint | OPEN |
| AR-16 | P3 | TimestampsAndWatermarksOperator.java:96 | 空的 batch-data 处理块 | OPEN |
| AR-17 | P3 | NFAState.java:128 | DeweyNumber String→split→parseInt 低效且脆弱 | OPEN |
| AR-18 | P3 | EvalFunctionCondition.java:16 | 硬编码 Object 类型参数 | OPEN |

### 深度审计发现（P1 5 条，P2 ~27 条）

| ID | Severity | 维度 | 文件 | 问题摘要 | 与对抗性审查重叠 |
|----|----------|------|------|---------|--------------|
| DA-P1-1 | P1 | 14 | CheckpointCoordinator.java:194 | acknowledgeTask 与 completePendingCheckpoint 竞态 | 与 AR-6/AR-5 相关但独立 |
| DA-P1-2 | P1 | 14 | JdbcCheckpointStorage.java:88 | catch-all INSERT→UPDATE（同 AR-13） | **= AR-13** |
| DA-P1-3 | P1 | 20 | ICheckpointExecutorFactory.java:77 | 三参数 executeWithCheckpoint 丢弃用户 CheckpointConfig | 新发现 |
| DA-P1-4 | P1 | 16 | StreamSourceOperator.java | 管道头部算子完全无测试 | 与 AR-4 相关（同一文件） |
| DA-P1-5 | P1 | 21 | TestFingerprintAndTerminationMode.java:107 | 假测试：手动 throw 再 assertThrows | 新发现 |

### 去重合并

对抗性审查 6 个 P1 + 深度审计 5 个 P1，去重后 **9 个独立 P1**：

1. AR-1: PatternStreamBuilder inputSerializer=null
2. AR-2: TaskExecutor awaitCompletion 吞 TimeoutException
3. AR-3: TimestampsAndWatermarksOperator Timer 线程安全
4. AR-4: StreamSourceOperator 不调用 cancel()
5. AR-5: AbstractStreamOperator processBarrier 快照失败仍传播
6. AR-6: CheckpointSerDe 序列化不一致
7. AR-8: MessageSourceFunction 回调线程安全
8. DA-P1-1: CheckpointCoordinator acknowledgeTask 竞态
9. DA-P1-3: ICheckpointExecutorFactory 丢弃 CheckpointConfig

加上深度审计特有的 2 个 P1（测试有效性）：
10. DA-P1-4: StreamSourceOperator 无测试
11. DA-P1-5: TestFingerprintAndTerminationMode 假测试

## Goals

1. 修复全部 11 个 P1 发现（对抗性审查 7 个 + 深度审计特有 4 个）
2. 修复与 P1 直接相关的高影响 P2 发现（AR-7 SourceEnumerator splitMetadata、AR-12 SharedBuffer 异常吞咽、AR-13 JdbcCheckpointStorage catch-all、AR-15 savepoint jobId）
3. 修复测试有效性问题（DA-P1-5 假测试、DA-P2-16 Java assert）
4. 每个修复有对应的单元测试
5. `./mvnw test -pl nop-stream -am` 通过

## Non-Goals

- 不做分布式执行路径端到端重构（需要架构设计文档）
- 不做 CEP 声明式模型层系统性验证改造
- 不处理 P3 级别发现（AR-16 空代码块、AR-17 DeweyNumber、AR-18 类型安全）
- 不做代码风格清理（深度审计维度 17 全部 P3）
- 不做 OperatorChain 正向遍历修复（AR-9，需评估影响范围，单独决策）
- 不做 RecordWriter 广播部分投递修复（AR-10，需设计决策：原子广播 vs 容错）
- 不做 WindowAggregationFunction merge() 构建时校验（AR-11，需设计决策）
- 不做 JdbcClusterRegistry TOCTOU 修复（AR-14，需确认部署场景是否单节点）

## Scope

### In Scope

- 对抗性审查 7 个 P1 修复（AR-1, AR-2, AR-3, AR-4, AR-5, AR-6, AR-8）
- 深度审计 4 个特有 P1 修复（DA-P1-1, DA-P1-3, DA-P1-4, DA-P1-5）
- 高影响 P2 修复（AR-7, AR-12, AR-13, AR-15）
- 测试有效性修复（DA-P1-5 假测试重写、DA-P2-16 Java assert → JUnit）

### Out Of Scope

- P3 级别发现（18 条）
- 需要架构设计决策的 P2（AR-9, AR-10, AR-11, AR-14）
- 分布式执行路径端到端重构
- CEP 声明式模型层改造

## Execution Plan

### Phase 1 - Source 生命周期与线程安全（AR-4 + AR-8）

Status: completed
Targets: `nop-stream/nop-stream-core/.../operators/StreamSourceOperator.java`, `nop-stream/nop-stream-connector/.../connector/MessageSourceFunction.java`

- [x] AR-4: `StreamSourceOperator.close()` 添加 `sourceFunction.cancel()` 调用（wrap 在 try-catch 中防止 cancel() 自身抛异常导致后续清理中断），确保所有 SourceFunction 在算子关闭时被正确清理
- [x] AR-8: `MessageSourceFunction.onMessage()` 回调添加 `synchronized` 保护，确保 `ctx.collect()` 不会被消息服务线程和算子线程并发调用。注意：需验证 `collect()` 内部不会持锁等待算子线程（避免 deadlock）。如果存在 deadlock 风险，改用 `LinkedBlockingQueue` 方案

Exit Criteria:

- [x] `StreamSourceOperator.close()` 确实调用了 `sourceFunction.cancel()`（try-catch 包裹），通过新增单元测试 `TestStreamSourceOperator.testCloseCancelsSourceFunction()` 验证
- [x] `MessageSourceFunction.onMessage()` 不再允许并发调用 `ctx.collect()`，通过新增测试验证线程安全
- [x] 无 deadlock 风险：验证 `synchronized collect()` 不会与算子线程的 `run()` 循环产生循环等待
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Watermark Timer 线程模型（AR-3）

Status: completed
Targets: `nop-stream/nop-stream-core/.../operators/TimestampsAndWatermarksOperator.java`

- Item Types: `Fix`

- [x] AR-3: 移除 `java.util.Timer` 方式，改为使用算子的处理时间定时器。具体方案：在 `open()` 中通过 `getTimerService().registerProcessingTimeTimer(currentProcessingTime + watermarkInterval)` 注册定时器；在 `onProcessingTime()` 回调中调用 `watermarkGenerator.onPeriodicEmit()` 并重新注册下一个定时器。确保 watermark 生成只在算子线程上执行。注意：即使没有元素到达，定时器也必须持续触发

Exit Criteria:

- [x] `TimestampsAndWatermarksOperator` 不再使用 `java.util.Timer`，watermark 生成通过算子线程的 timer service 回调完成
- [x] 无元素到达时 watermark 定时器仍正常触发
- [x] 新增测试验证 watermark 生成不会从独立线程触发
- [x] 现有 watermark 相关测试全部通过
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Checkpoint 语义正确性（AR-5 + AR-6 + DA-P1-1）

Status: completed
Targets: `nop-stream/nop-stream-core/.../operators/AbstractStreamOperator.java`, `nop-stream/nop-stream-runtime/.../checkpoint/storage/CheckpointSerDe.java`, `nop-stream/nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java`

- Item Types: `Fix`

- [x] AR-5: `AbstractStreamOperator.processBarrier()` 快照失败时不再传播 barrier。方案：快照失败时跳过 `output.emitBarrier()` 并记录错误，让 CheckpointCoordinator 通过回调检测到失败。注意：对单输入算子直接跳过；多输入算子（如 join）的 BarrierAligner 需确认不会因缺少 barrier 而死锁——检查 `BarrierAligner` 是否有超时或 abort 机制
- [x] AR-6: `CheckpointSerDe.serializeCheckpoint()` 统一使用与 `serializeEpochManifest()` 相同的显式 Map 构造方式（提取 operatorStates/keyedStates）。注意向后兼容：`deserializeCheckpoint()` 需同时支持旧格式（raw TaskStateSnapshot 字段名）和新格式（operatorStates/keyedStates 键名）
- [x] DA-P1-1: `CheckpointCoordinator.acknowledgeTask()` 添加 `synchronized` 保护（注意：此方法在 Phase 中应先于 AR-5 修改应用，因为 AR-5 依赖回调路径正确工作）。消除 acknowledgeTask 和 completePendingCheckpoint 之间的竞态窗口

Exit Criteria:

- [x] 快照失败时 barrier 不传播到下游，通过新增测试 `TestAbstractStreamOperatorProcessBarrier.testProcessBarrierSnapshotFailureDoesNotPropagate()` 验证
- [x] `serializeCheckpoint()` 和 `serializeEpochManifest()` 使用相同的序列化格式，`deserializeCheckpoint()` 兼容新旧格式。通过新增测试验证两条序列化路径输出一致，且旧格式数据可正确反序列化
- [x] `CheckpointCoordinator.acknowledgeTask()` 无竞态窗口，通过并发测试验证
- [x] BarrierAligner 在 barrier 缺失时不会死锁（确认有超时或 abort 机制）
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - CEP 序列化与 API 契约（AR-1 + DA-P1-3）

Status: planned
Targets: `nop-stream/nop-stream-cep/.../PatternStreamBuilder.java`, `nop-stream/nop-stream-core/.../execution/ICheckpointExecutorFactory.java`, `nop-stream/nop-stream-core/.../environment/StreamExecutionEnvironment.java`, `nop-stream/nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java`

- Item Types: `Fix`

- [ ] AR-1: `PatternStreamBuilder.build()` 恢复注释掉的 `inputStream.getType().createSerializer(inputStream.getExecutionConfig())`。备选：在 `CepOperator.open()` 中通过 `getRuntimeContext()` 获取 serializer 并延迟初始化（需同时修改 CepOperator）
- [ ] DA-P1-3: 修复 `StreamExecutionEnvironment.execute()` 到 `executeWithCheckpoint` 的调用路径，使用户的 `CheckpointConfig` 被传递而非丢弃。方案：在 `StreamExecutionEnvironment` 中将用户的 `CheckpointConfig` 传递到 `GraphModelCheckpointExecutor`，替换 `new CheckpointConfig()` 默认值

Exit Criteria:

- [ ] `PatternStreamBuilder.build()` 不再传入 `null` serializer，CEP 算子在序列化路径不崩溃。新增测试验证 CepOperator 可正确序列化/反序列化
- [ ] 用户通过 `StreamExecutionEnvironment` 设置的 `CheckpointConfig`（interval, storageType, guarantee 等）在 execute 路径中被正确传递到 executor。新增测试验证
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - TaskExecutor 语义与 JDBC 存储（AR-2 + AR-13 + AR-15）

Status: planned
Targets: `nop-stream/nop-stream-core/.../execution/TaskExecutor.java`, `nop-stream/nop-stream-runtime/.../checkpoint/storage/JdbcCheckpointStorage.java`, `nop-stream/nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java`, `nop-stream/nop-stream-runtime/.../checkpoint/storage/LocalFileCheckpointStorage.java`

- Item Types: `Fix`

- [ ] AR-2: `TaskExecutor.awaitCompletion()` 单独捕获 `TimeoutException`，设置 `timedOut` 标志，确保超时后返回 `false` 而非 `true`
- [ ] AR-13: `JdbcCheckpointStorage` 的 INSERT-then-UPDATE 只捕获 duplicate-key 异常（`SQLIntegrityConstraintViolationException` 或检查 SQL state '23505'），其他异常重新抛出。适用于 `storeCheckPoint()`, `storeSavepoint()`, `storeEpochManifest()` 三处
- [ ] AR-15: `GraphModelCheckpointExecutor.restoreFromSavepointPath()` 在 savepoint 路径下不使用 jobId/pipelineId 过滤。方案：为 `LocalFileCheckpointStorage` 添加 `getLatestCheckpoint()` 无参重载，返回目录下最新的 checkpoint

Exit Criteria:

- [ ] `awaitCompletion()` 在 TimeoutException 后返回 `false`，新增测试验证
- [ ] `JdbcCheckpointStorage` 只在 duplicate-key 时走 UPDATE，其他异常正确传播，新增测试验证
- [ ] savepoint 恢复不依赖 jobId 匹配，新增测试验证
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - SourceEnumerator 与 SharedBuffer（AR-7 + AR-12）

Status: planned
Targets: `nop-stream/nop-stream-runtime/.../source/SourceEnumerator.java`, `nop-stream/nop-stream-core/.../checkpoint/SourceEnumeratorState.java`, `nop-stream/nop-stream-cep/.../nfa/sharedbuffer/SharedBuffer.java`

- Item Types: `Fix`

- [ ] AR-7: `SourceEnumerator.snapshotState()` 包含 `splitMetadata`，`restoreState()` 恢复它。**跨模块依赖解决方案**：`SourceSplit` 在 `nop-stream-runtime` 而 `SourceEnumeratorState` 在 `nop-stream-core`。方案：将 splitMetadata 序列化为 `Map<String, Map<String, Object>>`（JSON 兼容格式），避免 core 依赖 runtime 的 `SourceSplit` 类。`SourceEnumeratorState` 新增 `Map<String, Map<String, Object>> splitMetadataMap` 字段
- [ ] AR-12: `SharedBuffer.hasEventInBuffer()` 不再吞异常，改为传播异常。**注意**：此为 Plan 113 Phase 19 的加强修复——Plan 113 仅添加了 `LOG.error`，本次改为 rethrow 以彻底消除重复 EventId 风险

Exit Criteria:

- [ ] `splitMetadata` 在 checkpoint 恢复后完整保留（通过 `Map<String, Map<String, Object>>` 序列化），新增测试 `TestSourceEnumerator.testSplitMetadataPersistedOnSnapshot()` 验证
- [ ] `SourceEnumeratorState` 不引入对 `nop-stream-runtime` 的依赖（编译验证）
- [ ] `hasEventInBuffer` 异常不再静默返回 false 而是向上传播，新增测试验证异常传播
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 - 测试有效性修复（DA-P1-5 + DA-P1-4 + DA-P2-16）

Status: planned
Targets: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestFingerprintAndTerminationMode.java`, `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/operators/TestStreamSourceOperator.java`（新增）, `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/sharedbuffer/TestSharedBuffer.java`

- Item Types: `Fix | Proof`

- [ ] DA-P1-5: 重写 `TestFingerprintAndTerminationMode.testFingerprintMismatchOnRestoreThrowsException()`，实际构建不匹配的 fingerprint 并触发 restore 路径验证抛出 `StreamException`，而非在 lambda 中手动 throw
- [ ] DA-P1-4: 新增 `TestStreamSourceOperator` 测试类（包 `io.nop.stream.core.operators`），覆盖 barrier 注入、source offset 快照/恢复、source function cancel 调用
- [ ] DA-P2-16: `TestSharedBuffer.java` 中 4 处 Java `assert`（行 230, 261, 289, 314）替换为 JUnit `assertTrue()` / `assertNotEquals()`

Exit Criteria:

- [ ] 重写后的假测试确实触发了生产代码路径，通过检查测试不再包含手动 `throw` 语句验证
- [ ] `TestStreamSourceOperator` 包含至少 3 个测试方法覆盖 barrier 注入和 source 生命周期
- [ ] `TestSharedBuffer` 不包含任何 Java `assert` 语句（grep 验证）
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 全部 11 个 P1 发现已修复并经 live repo 验证
- [ ] 高影响 P2 发现已修复（AR-7, AR-12, AR-13, AR-15）
- [ ] 测试有效性问题已修复（假测试重写、Java assert 替换、新增测试）
- [ ] 不存在被静默降级到 deferred 的 in-scope P1 或 P2 发现
- [ ] `./mvnw compile -pl nop-stream -am` 通过
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] checkstyle / 代码规范检查通过
- [ ] 受影响 owner docs 已同步或明确写明 No owner-doc update required
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：修复的组件间调用链在运行时确实连通，无空方法体/静默跳过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

### AR-9 OperatorChain open() 反向遍历

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 反向遍历可能是历史遗留的设计决策。当前所有算子的 open() 方法不依赖上游算子已初始化。修复需要逐个验证所有算子的 open() 行为，风险不确定
- Successor Required: yes
- Successor Path: 待定，需设计决策确认正向遍历是否正确

### AR-10 RecordWriter 广播部分投递

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前内存传输层 `ResultPartition.write()` 不抛 InterruptedException，因此该路径在实际使用中不会触发。修复需要设计原子广播或容错机制
- Successor Required: yes
- Successor Path: 待定

### AR-11 WindowAggregationFunction merge() 构建时校验

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前 session window 未被生产使用，merge() 的 UnsupportedOperationException 是默认行为的显式失败（非静默跳过）。运行时失败比静默数据错误更好
- Successor Required: no

### AR-14 JdbcClusterRegistry TOCTOU

- Classification: `watch-only residual`
- Why Not Blocking Closure: 当前部署场景为单节点，不会出现并发注册。多节点部署需重新评估
- Successor Required: no

### P3 级别发现（18 条）

- Classification: `optimization candidate` / `out-of-scope improvement`
- Why Not Blocking Closure: P3 为低风险技术债，不影响功能正确性
- Successor Required: no

## Non-Blocking Follow-ups

- AR-16: TimestampsAndWatermarksOperator 空 batch-data 处理块（P3）
- AR-17: NFAState DeweyNumber String→split→parseInt 优化（P3）
- AR-18: EvalFunctionCondition 类型参数泛型化（P3）
- 深度审计维度 09 错误处理统一（P2 ~27 条中的非关键项）
- 深度审计维度 02/05 模块职责和代码重复（P2 P3 混合）

## Closure

Status Note: <<执行完成后填写>>

Closure Audit Evidence:

- Reviewer / Agent: <<待 closure audit 时填写>>
- Evidence: <<待 closure audit 时填写>>

Follow-up:

- <<待执行完成后填写>>
