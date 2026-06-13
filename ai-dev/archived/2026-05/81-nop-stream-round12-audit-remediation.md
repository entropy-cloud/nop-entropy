# 81 nop-stream Round 12 审计修复

> Plan Status: completed
> Last Reviewed: 2026-05-30
> Source: ai-dev/audits/2026-05-30-adversarial-review-nop-stream-r12/01-open-findings.md
> Related: 79-nop-stream-round11-p1-p2-audit-remediation (completed), 80-nop-stream-p3-audit-remediation (completed)

## Purpose

将 2026-05-30 Round 12 对抗性审查的 8 个发现（1×P0 + 3×P1 + 4×P2）修复到可验证状态。核心问题是 TwoPhaseCommitSinkFunction 的 2PC 状态持久化链路双重断裂（saveState 返回 null + restoreState 不恢复 pendingCommits），以及 3 个 P1 正确性/安全缺陷。

## Current Baseline

- Plan 79/80 完成后 `./mvnw test -pl nop-stream -am` 全量通过
- Plans 62-80 修复了 Round 1-11 + 4 轮深度审计的全部 P0/P1 发现及大部分 P2/P3
- Round 12 发现 8 个新问题（AR-1~AR-8），其中 AR-1 和 AR-8 形成双重断裂
- 经 live repo 验证，全部 8 个发现仍存在于当前代码中

### 待修复发现

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| AR-1 | P0 | TwoPhaseCommitSinkFunction.java:65 | saveState() 返回 null，pendingCommits 永不持久化 |
| AR-2 | P1 | ClassNameValidator.java:23 | `[L` 前缀允许任意对象数组类实例化（RCE 向量） |
| AR-3 | P1 | RecordWriter.java:195 | emitElement() 只写 partitions[0]，watermark 不广播 |
| AR-4 | P2 | RecordWriter.java:234 | Math.abs(Integer.MIN_VALUE) 返回负数 |
| AR-5 | P1 | CountTrigger.java:71-78 | canMerge()=true 但 onMerge() 是 no-op |
| AR-6 | P2 | GraphExecutionPlan.java:374-408 | topologicalSort 不检测环 |
| AR-7 | P2 | CepOperator.java:158 | transient currentWatermark 反序列化后为 0L |
| AR-8 | P2 | StreamSinkOperator.java:117-129 | restoreState 不恢复 pendingCommits（与 AR-1 双重断裂） |

### 关键结构约束（Phase 1 依赖）

- `TwoPhaseCommitSinkFunction` 实现了 `CheckpointParticipant` 接口（:24），因此 `processBarrier()` 中 `userFunction instanceof CheckpointParticipant`（:60）**总是**先于 `else if (userFunction instanceof TwoPhaseCommitSinkFunction)`（:73）匹配，后者是死代码
- `processBarrier()` 通过 `CheckpointParticipant` 路径调用 `saveState()`，将结果用 `"participant-"` 前缀合并到 `snapshotResult`（:64、:67）。因此 `pendingCommits` 的存储 key 为 `"participant-" + PENDING_COMMITS_KEY` = `"participant-pending-commits"`
- `TaskStateSnapshot` 构造器需要 `TaskLocation` 参数（无 nullary constructor）。`TaskLocation` 有无参构造器 `new TaskLocation()` 返回 `("", "", "", 0)`，可用于中间状态容器
- `OnMergeContext` 是空接口（`Trigger.java:167-169`），`mergePartitionedState()` 方法在代码库中不存在（仅出现在 `CountTrigger.java:77` 的注释行）。因此 `canMerge()` 必须改为 `false`

## Goals

- 修复全部 1 个 P0 发现（2PC 状态持久化）
- 修复全部 3 个 P1 发现（安全验证、watermark 广播、trigger 合并）
- 修复全部 4 个 P2 发现（整数溢出、DAG 验证、反序列化初始化、2PC 恢复）
- 每个修复完成后 `./mvnw test -pl nop-stream -am` 全部通过
- 端到端验证 2PC save/restore 链路完整性

## Non-Goals

- 架构级重构（WindowAggregationOperator 与 WindowOperator 统一、core 模块拆分）
- Plan 78 scope 内的遗留发现
- P3/P4 级别发现（~28 项）
- 清理 `StreamSinkOperator` 中 `instanceof TwoPhaseCommitSinkFunction` 死代码分支（:73、:97、:109）— 属于代码清理，不在本 plan scope 内

## Scope

### In Scope

- nop-stream-core: TwoPhaseCommitSinkFunction、RecordWriter、CountTrigger、GraphExecutionPlan、StreamSinkOperator、ClassNameValidator、NopStreamErrors
- nop-stream-cep: CepOperator transient watermark 初始化
- 对应新增/修改测试

### Out Of Scope

- P3/P4 发现修复
- 架构级重构
- Plan 78 遗留
- `StreamSinkOperator` 死代码分支清理

## Execution Plan

### Phase 1 - TwoPhaseCommitSinkFunction 2PC 状态持久化（P0: AR-1 + P2: AR-8）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java`, `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSinkOperator.java`

- Item Types: `Fix`

- [x] **AR-1**: 在 `saveState(long epochId)` 中创建 `new TaskStateSnapshot(new TaskLocation())`，将 `new TreeMap<>(pendingCommits)` 作为 operator state 写入（key = `PENDING_COMMITS_KEY` = `"pending-commits"`），然后返回该 snapshot。当 `pendingCommits` 为空 map 时仍返回非 null snapshot（内容为空 map），确保 `processBarrier()` 行 62 的 `participantState != null` 检查为 true
- [x] **AR-8**: 在 `StreamSinkOperator.restoreState()` 中，当 `userFunction instanceof TwoPhaseCommitSinkFunction` 时（注意：该分支在 `instanceof CheckpointParticipant` 之后的 else-if 中是死代码，但当前 restoreState 只检查 `TwoPhaseCommitSinkFunction`），改为：
  1. 从 `snapshotResult` 的 operator states 中提取 key `"participant-pending-commits"` 对应的 `Map<Long, Object>`
  2. 若非 null，通过 `tpcSink.setPendingCommits()` 恢复到 sink function
  3. 调用 `tpcSink.restoreFromEpoch(-1, null)` 让 `restoreFromEpoch` 正确 rollback 恢复的 pendingCommits 中的每个事务并开始新事务
  4. 移除原来的 `tpcSink.rollback()` + `tpcSink.beginTransaction()`（由 `restoreFromEpoch` 内部的 `recover()` 统一处理）

Exit Criteria:

- [x] `TwoPhaseCommitSinkFunction.saveState()` 返回非 null `TaskStateSnapshot`，其 operatorStates 包含 key `"pending-commits"` 映射到 `pendingCommits` 的副本
- [x] `StreamSinkOperator.restoreState()` 从快照中用 key `"participant-pending-commits"` 提取 pendingCommits，恢复到 sink function，并调用 `restoreFromEpoch()` 进行 rollback
- [x] 新增测试：`testSaveStatePersistsPendingCommits` — preCommit → saveState 返回非 null 且包含 pendingCommits 数据
- [x] 新增测试：`testRestoreStateRecoversPendingCommitsAndRollbacks` — 模拟 snapshot 中有 pendingCommits → restoreState 提取并恢复 → rollback 被调用
- [x] **端到端验证**：新增测试 `testTwoPhaseCommitSaveRestoreRoundTrip`：preCommit(epoch=1) → saveState(1) → 构造包含 participant state 的 snapshotResult → restoreState → 验证 pendingCommits 中的事务被 rollback → 新 transaction 开始
- [x] **接线验证**：验证 `processBarrier()` 调用 `saveState()` 得到非 null 结果，且通过 `"participant-" + PENDING_COMMITS_KEY` key 合并到 `snapshotResult` 的 operatorStates 中
- [x] **无静默跳过**：saveState 不再返回 null；restoreState 不再跳过 pendingCommits 恢复
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ClassNameValidator 安全加固（P1: AR-2）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/util/ClassNameValidator.java`

- Item Types: `Fix`

- [x] **AR-2**: 从 `ALLOWED_PREFIXES` 中移除 `"[L"` 条目，替换为 `"[Lio.nop."` 和 `"[Ljava."` 两个前缀。这样 `[Lio.nop.stream.core.SomeClass[]` 和 `[Ljava.lang.String;` 仍能通过，但 `[Lcom.evil.Malicious;` 被拒绝

Exit Criteria:

- [x] `ClassNameValidator` 中不再有裸 `"[L"` 前缀
- [x] `"[Lio.nop.stream.core.SomeClass;"` 通过验证
- [x] `"[Ljava.lang.String;"` 通过验证
- [x] `"[Lcom.evil.Malicious;"` 被拒绝
- [x] 新增测试：`testArrayClassValidationRejectsMaliciousPrefix` — 验证 `[Lcom.evil.Class;` 被拒绝
- [x] 新增测试：`testArrayClassValidationAcceptsAllowedPrefixes` — 验证 `[Lio.nop.xxx` 和 `[Ljava.xxx` 通过
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - RecordWriter emitElement 广播修复（P1: AR-3）+ selectChannel 整数溢出修复（P2: AR-4）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/RecordWriter.java`

- Item Types: `Fix`

- [x] **AR-3**: 将 `emitElement()` 方法体简化为始终遍历所有 `partitions` 写入（移除 `if (partitioner != null)` 分支判断），与 `emitWatermark()` 和 `emitBarrier()` 行为一致。注意：`emitElement` 用于 watermark/watermark-status 等控制元素，应始终广播；数据记录的分区路由由 `emit()` 方法通过 `selectChannel()` 处理，不受此变更影响
- [x] **AR-4**: 将 `selectChannel()` 中 `Math.abs(channel % partitions.length)` 改为 `Math.floorMod(channel, partitions.length)`（与 `HashPartitionRouter:33` 保持一致）

Exit Criteria:

- [x] `emitElement()` 始终遍历所有 partitions 写入（方法体内无 `if (partitioner != null)` 条件分支）
- [x] `selectChannel()` 使用 `Math.floorMod` 而非 `Math.abs(channel % ...)`
- [x] 新增测试：`testEmitElementBroadcastsToAllPartitions` — 构造多 partition RecordWriter，调用 emitElement，验证所有 partition 收到元素
- [x] 新增测试：`testSelectChannelHandlesIntegerMinValue` — mock IPartitioner 返回 Integer.MIN_VALUE，验证不抛 ArrayIndexOutOfBoundsException
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - CountTrigger canMerge 修正（P1: AR-5）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/triggers/CountTrigger.java`

- Item Types: `Fix`

- [x] **AR-5**: 将 `canMerge()` 改为 `return false`，移除 `onMerge()` 中注释掉的代码（`// ctx.mergePartitionedState(stateDesc);`）。`OnMergeContext` 接口是空的（`Trigger.java:167-169`），`mergePartitionedState()` 方法在代码库中不存在。当前 `canMerge()=true` 会误导调用方认为 CountTrigger 支持状态合并，实际 `onMerge()` 是 no-op 导致 Session Window 合并后计数器归零

Exit Criteria:

- [x] `CountTrigger.canMerge()` 返回 `false`
- [x] `onMerge()` 方法体为空（无注释掉的代码残留）
- [x] 新增测试：`testCountTriggerCannotMerge` — 验证 `canMerge()` 返回 false
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - GraphExecutionPlan 环检测（P2: AR-6）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java`, `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/exceptions/NopStreamErrors.java`

- Item Types: `Fix`

- [x] 在 `NopStreamErrors.java` 中新增 `ERR_STREAM_CYCLIC_JOB_GRAPH` 错误码定义
- [x] **AR-6**: 在 `topologicalSort()` 的 `return sorted;` 前添加环检测：`if (sorted.size() != jobGraph.getVertices().size())` 时，计算 `jobGraph.getVertices().keySet() - sorted` 得到缺失顶点，抛出 `StreamException(ERR_STREAM_CYCLIC_JOB_GRAPH).param(ARG_DETAIL, "Cycle detected involving: " + missing)`

Exit Criteria:

- [x] `NopStreamErrors` 包含 `ERR_STREAM_CYCLIC_JOB_GRAPH` 定义
- [x] `topologicalSort()` 对有环图抛出 `StreamException`（非静默丢弃）
- [x] 异常消息包含环中涉及的顶点 ID（通过 `ARG_DETAIL` 参数）
- [x] 新增测试：`testTopologicalSortDetectsCycle` — 构建包含环的 JobGraph（A→B→C→A），验证抛出 StreamException 且消息包含环中顶点
- [x] 新增测试：`testTopologicalSortAcceptsDAG` — 构建无环 JobGraph，验证正常返回排序结果
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - CepOperator transient watermark 初始化（P2: AR-7）

Status: completed
Targets: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java`

- Item Types: `Fix`

- [x] **AR-7**: 在 `open()` 方法（:186）开头添加 `currentWatermark = Long.MIN_VALUE;`。`currentWatermark` 是 `transient long` 字段（:158），Java 反序列化后初始化为 `0L`（字段初始化器 `= Long.MIN_VALUE` 对 transient 不执行），导致反序列化后 watermark 偏移

Exit Criteria:

- [x] `CepOperator.open()` 方法开头显式设置 `currentWatermark = Long.MIN_VALUE`
- [x] 新增测试：`testWatermarkInitializedAfterDeserialization` — 验证 near Long.MIN_VALUE 时间戳元素不被误判为 late
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 1 个 P0 发现已修复（2PC 状态持久化链路完整）
- [x] 全部 3 个 P1 发现已修复（安全验证、watermark 广播、trigger 合并）
- [x] 全部 4 个 P2 发现已修复（整数溢出、DAG 验证、反序列化、2PC 恢复）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 发现
- [x] No owner-doc update required（全部为代码/测试修复）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: 2PC saveState/restoreState 链路从 StreamSinkOperator.processBarrier() 到 TwoPhaseCommitSinkFunction 完整连通；无空方法体或静默跳过
- [x] `./mvnw compile -pl nop-stream -am` 通过
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] checkstyle / 代码规范检查通过
- [x] `ai-dev/logs/` 收口记录已写入

## Deferred But Adjudicated

### StreamSinkOperator instanceof TwoPhaseCommitSinkFunction 死代码分支

- Classification: `optimization candidate`
- Why Not Blocking Closure: `TwoPhaseCommitSinkFunction` 实现了 `CheckpointParticipant`，所以 `instanceof CheckpointParticipant` 分支（:60、:94、:106）总是先匹配。`else if (instanceof TwoPhaseCommitSinkFunction)` 分支（:73、:97、:109）是死代码，不影响运行时行为。清理需要评估是否有非 2PC 的 SinkFunction 场景，属于代码卫生
- Successor Required: no
- Successor Path: 后续代码清理时一并处理

## Non-Blocking Follow-ups

- P3/P4 发现修复（~28 项）
- 架构级重构设计文档（窗口算子统一、core 模块拆分）
- Plan 78 scope 内的遗留发现
- StreamSinkOperator 死代码分支清理

## Closure

Status Note: 全部 8 个发现（1×P0 + 3×P1 + 4×P2）已修复并通过测试验证。2PC saveState/restoreState 链路完整连通，无空壳实现或静默跳过。

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent (houyi, task ses_187c68d7effeH0e3JN82zS5OuN)
- Evidence:
  - AR-1 (P0) PASS: TwoPhaseCommitSinkFunction.java:66-71 saveState() 返回非 null TaskStateSnapshot 含 pendingCommits
  - AR-8 (P2) PASS: StreamSinkOperator.java:122-133 restoreState() 提取 participant-pending-commits 并调用 restoreFromEpoch
  - AR-2 (P1) PASS: ClassNameValidator.java:23-24 [L 替换为 [Lio.nop. 和 [Ljava.
  - AR-3 (P1) PASS: RecordWriter.java:188-197 emitElement() 无条件广播到所有分区
  - AR-4 (P2) PASS: RecordWriter.java:230 使用 Math.floorMod 替代 Math.abs
  - AR-5 (P1) PASS: CountTrigger.java:71-72 canMerge() 返回 false，onMerge() 方法体为空
  - AR-6 (P2) PASS: GraphExecutionPlan.java:414-421 环检测抛出 StreamException，NopStreamErrors.java:167-168 含 ERR_STREAM_CYCLIC_JOB_GRAPH
  - AR-7 (P2) PASS: CepOperator.java:188 open() 设置 currentWatermark = Long.MIN_VALUE
  - Anti-Hollow Check PASS: processBarrier → saveState → restoreState → restoreFromEpoch 完整链路连通
  - No Silent No-Op Check PASS: 无空方法体/静默跳过
  - `./mvnw test -pl nop-stream -am` BUILD SUCCESS
  - Deferred 项分类检查 PASS: 唯一 deferred 项为 optimization candidate，无 in-scope live defect 被降级

Follow-up:

- P3/P4 发现修复（~28 项）
- 架构级重构设计文档
- Plan 78 scope 内遗留
- StreamSinkOperator 死代码分支清理
