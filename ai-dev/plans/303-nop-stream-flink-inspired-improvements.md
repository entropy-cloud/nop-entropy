# 303 nop-stream Flink 对标改进

> Plan Status: active
> Last Reviewed: 2026-07-20
> Source: `ai-dev/analysis/nop-stream-flink-comparison-deep-dive.md`
> Related: `283-nop-stream-code-quality-fixes.md`（代码质量修复已在 Phase 1）

## Purpose

从 Flink 源码分析报告中识别的高性价比改进项中，选取 P0/P1 级别任务在本计划内完成实施，使 nop-stream 的 TimeService、Window 算子、算子链化、Checkpoint 等核心子系统消除已知功能缺口和静默跳过问题。

## Current Baseline

- `HeapInternalTimerService.registerProcessingTimeTimer()` 为空方法体（静默跳过，违反 No Silent No-Op Rule）
- `HeapInternalTimerService.advanceWatermark()` 使用 `TreeMap.headMap()` 视图然后迭代删除，方式笨重但功能正确
- `WindowAggregationOperator` 标注 `@Deprecated` 但仍在代码库中存在并被人引用
- `JobGraphGenerator.canChain()` 已包含并行度和分区器类型检查（null 视为 forward），但缺少 `ForwardPartitioner` 显式类——当前用 null 隐式表示 forward，导致无法区分"无分区器"与"显式 forward 分区器"；同时缺少算子链化开关检查（`StreamOperatorFactory.isChainable()`）
- `PendingCheckpoint` 状态机只有 `RUNNING / COMPLETED / ABORTED` 三个状态，缺少 `FAILED` 和状态转换合法性校验
- `CheckpointMetrics` 缺少失败原因追踪字段

### 确认的 live defects（本计划修复）

1. **`HeapInternalTimerService` 空方法体** — `registerProcessingTimeTimer()` 和 `deleteProcessingTimeTimer()` 为空（no-op）。虽然当前没有算子调用这两个方法，但 Silent No-Op 可能导致将来误以为功能已实现。
2. **`WindowAggregationOperator` Code Debt** — 887 行的 deprecated 代码与新版 `WindowOperator` 并存，造成维护负担。

## Goals

- 消灭 `HeapInternalTimerService` 中所有静默跳过（ProcessingTimeTimer no-op）
- 优化 `HeapInternalTimerService.advanceWatermark()` 触发方式：从 `headMap+iteration` 改为 `while+pollFirstEntry`
- 退役 `WindowAggregationOperator`，将测试全量迁移到 runtime 层的新版 `WindowOperator`
- 增强 `JobGraphGenerator` 链化条件：新增 `ForwardPartitioner` 显式类 + `StreamOperatorFactory.isChainable()` 算子链化开关检查
- 增强 `PendingCheckpoint` 状态机：引入完整状态枚举 + 状态转换合法性校验
- 在 `CheckpointMetrics` 中增加失败原因追踪

## Non-Goals

- 不引入 KeyGroup 概念（需要 `IKeyedStateBackend` 架构修改，中等成本）
- 不实现差量 Timer 快照（P2 级别，依赖 checkpont 增量快照管线）
- 不实现 RocksDB 状态后端（P2 级别，可选依赖）
- 不涉及分布式 RPC/Shuffle（P3 级别）
- 不涉及 Source/Sink 新 API 迁移（P3 级别）
- 不涉及 XDSL 声明式模型编译器（属于 nop-stream-flow 模块规划）

## Scope

### In Scope

1. `HeapInternalTimerService` 优化：ProcessingTimeTimer 实现 + advanceWatermark 算法改进
2. `WindowAggregationOperator` 退役
3. `JobGraphGenerator` 链化条件增强
4. `PendingCheckpoint` 状态机增强 + `CheckpointMetrics` 失败追踪

### Out Of Scope

- 分布式执行框架改造
- RocksDB 状态后端
- 新 Source/Sink API
- CEP 引擎修改

## Execution Plan

### Phase 1 — TimerService 功能完备与性能优化

Status: planned
Targets: `nop-stream-core/.../operators/HeapInternalTimerService.java` + `TimerServiceManager.java`

- Item Types: `Fix`

- [x] 审阅当前 `HeapInternalTimerService` 源码（已完成，共 187 行）
- [ ] **实现 ProcessingTimeTimer**：添加 `processingTimeTimers` 字段（`TreeMap<Long, Set<TimerEntry<N>>>`），在 `registerProcessingTimeTimer()` 中写入，在 `deleteProcessingTimeTimer()` 中删除。新增 `fireProcessingTimeTimers(long timestamp)` 方法，由 `TimerServiceManager` 定时调用。
- [ ] **优化 advanceWatermark 算法**：将 `headMap(newWatermark, true)` + 收集到 `toFire` 列表 + 迭代删除 的模式替换为 `while (firstKey <= newWatermark) { pollFirstEntry(); fire; }`，消除中间列表和视图开销。
- [ ] **验证 No Silent No-Op Rule**：确认新实现中每个公共方法均无空方法体或无 `continue` 跳过分支。`forEachProcessingTimeTimer` 在无 timer 时应遍历空集合（安全），不抛出异常也不静默忽略。

Exit Criteria:

> 所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `HeapInternalTimerService` 不再包含任何空方法体或 `continue` 跳过
- [ ] `registerProcessingTimeTimer()` 和 `deleteProcessingTimeTimer()` 有实际实现
- [ ] `advanceWatermark()` 使用 `while + pollFirstEntry` 模式，不再使用 `headMap` 创建视图
- [ ] 新增验证：ProcessingTimeTimer 确实被触发（单元测试 `TestHeapInternalTimerService` 应包含 processing time 用例）
- [ ] 新增验证：`fireProcessingTimeTimers` 测试覆盖
- [ ] **端到端验证**：`TestProcessingTimeWindowIntegration` 中 processing time 窗口语义正确
- [ ] **无静默跳过**：`HeapInternalTimerService` 中无空方法体
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am -Dtest="TestHeapInternalTimerService*,TestTimerServiceManager*,TestProcessingTimeWindowIntegration"` 通过
- [ ] No owner-doc update required（纯内部实现重构，对外接口 `InternalTimerService` 不变）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — WindowAggregationOperator 退役

Status: planned
Targets: `nop-stream-core/.../operators/WindowAggregationOperator.java` + 关联测试

- Item Types: `Fix`

- [ ] 审计所有引用 `WindowAggregationOperator` 的测试文件，确认哪些已在 `nop-stream-core/src/test/.../operators/` 下有对应的 `TestWindow*` 测试（新版 `WindowOperator` 的测试主要在 `nop-stream-runtime` 模块）
- [ ] 将缺失的测试覆盖（如窗口聚合语义、late data 处理、trigger 状态）从旧测试迁移到新版 `WindowOperator` 对应的测试类
- [ ] 删除 `WindowAggregationOperator.java`
- [ ] 删除 `WindowAggregationState.java`（仅被 WindowAggregationOperator 使用）
- [ ] 清理所有不再使用的 import 引用

Exit Criteria:

> 所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `WindowAggregationOperator.java` 已从仓库中删除
- [ ] `WindowAggregationState.java` 已从仓库中删除
- [ ] 旧 `WindowAggregationOperator` 的所有行为语义在新版 `WindowOperator` 测试中有等价覆盖
- [ ] **接线验证**：`IWindowOperatorFactory` 是唯一窗口算子工厂入口，确认 `WindowOperatorBuilder` 和 `WindowOperatorFactoryImpl` 被端到端路径调用
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` 通过
- [ ] No owner-doc update required（纯代码清理，对外 API 不变）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 链化条件显式化（ForwardPartitioner + 算子链化开关）

Status: planned
Targets: `nop-stream-core/.../jobgraph/` + `nop-stream-core/.../graph/`

- Item Types: `Fix`

- [ ] 审阅当前 `canChain()` 逻辑（并行度和分区器null检查已存在）
- [ ] 新增 `ForwardPartitioner` 类：作为 `IPartitioner` 的 marker 实现，构造后为 no-op
- [ ] 修改 `canChain()` 分区器检查：从 `edge.getPartitioner() != null` 改为 `edge.getPartitioner() != null && !(edge.getPartitioner() instanceof ForwardPartitioner)`，使 null 和显式 ForwardPartitioner 均视为 forward
- [ ] 在 `StreamOperatorFactory` 接口中增加 `isChainable()` 默认方法（返回 true），允许算子声明自身不可链化
- [ ] 修改 `canChain()` 增加算子链化开关检查：调用 `node2.getOperatorFactory().isChainable()`
- [ ] 新增 focused test：分区器非 Forward/不可链化算子导致拆链

Exit Criteria:

> 所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `ForwardPartitioner` 类存在于 `io.nop.stream.core.graph` 包下
- [ ] `canChain()` 中 null 和 `ForwardPartitioner` 均视为 forward（可链化）；其他分区器及不可链化算子强制拆链
- [ ] `StreamOperatorFactory.isChainable()` 默认方法存在，返回 true；被覆盖返回 false 时强制拆链
- [ ] **端到端验证**：高并行度 source → 低并行度 map → sink 的图管线生成正确的非链化 JobGraph（并行度不匹配的拆链已有测试覆盖，增加端到端集成验证）
- [ ] 新增 focused test 验证「非 Forward 分区器 + 不可链化算子」两场景的拆链决策正确性
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am -Dtest="TestJobGraphGenerator*,Test*Chain*"` 通过
- [ ] No owner-doc update required（纯内部增强，对外接口 `ForwardPartitioner` 为新增类但属于内部框架，不改变用户 API）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — Checkpoint 状态机增强

Status: planned
Targets: `nop-stream-runtime/.../checkpoint/PendingCheckpoint.java` + `CheckpointMetrics.java`

- Item Types: `Fix`

- [ ] 在 `PendingCheckpoint.Status` 枚举中增加 `FAILED` 状态
- [ ] 增加状态转换合法性校验表：RUNNING → COMPLETED / ABORTED / FAILED（不允许 COMPLETED → ABORTED）；已 dispose 的 checkpoint 不允许继续 ack
- [ ] 在 `CheckpointMetrics` 中添加 `failureCause` 字段（`String` 类型，记录异常堆栈或错误消息）
- [ ] 在 `CheckpointCoordinator` 的失败路径中设置 `failureCause`
- [ ] 新增 focused test 验证非法状态转换被拒绝

Exit Criteria:

> 所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `PendingCheckpoint.Status` 包含 `FAILED`
- [ ] 非法状态转换（如 `COMPLETED → ABORTED`）抛出 `IllegalStateException` 或 `StreamException`
- [ ] `CheckpointMetrics` 包含 `failureCause` 字段且被 checkpoint 失败路径设置
- [ ] 新增测试覆盖非法状态转换拒绝和 `failureCause` 填充验证
- [ ] **端到端验证**：checkpoint 从 barrier 触发 → COMPLETED → 恢复重新处理 的端到端路径在修改后通过（`TestE2ECheckpointAndRecovery`），且 `FAILED`/`failureCause` 在新增的端到端失败场景测试中可通过
- [ ] **无静默跳过**：所有不应通过的状态转换显式抛异常，不允许静默忽略
- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime -am -Dtest="TestPendingCheckpoint*,TestCheckpointCoordinator*,TestCheckpointMetrics,TestE2ECheckpointAndRecovery*"` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] HeapInternalTimerService 无空方法体、advanceWatermark 使用 while+pollFirstEntry
- [ ] WindowAggregationOperator 已从仓库删除，所有语义由新版 WindowOperator 覆盖
- [ ] JobGraphGenerator 链化条件显式化：ForwardPartitioner 类 + 算子链化开关检查（`StreamOperatorFactory.isChainable()`）
- [ ] PendingCheckpoint 状态机含 FAILED 状态 + 合法转换校验
- [ ] CheckpointMetrics.failureCause 在失败时被填充
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）组件间调用链在运行时确实连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am`
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### WindowAggregationOperator 旧测试迁移遗漏风险

- Classification: `watch-only residual`
- Why Not Blocking Closure: 旧版所有行为在新版 `WindowOperator` 中已有等价实现，旧测试中的特殊场景（如触发状态 key collision）已在新版测试中覆盖（`TestTriggerStateKeyCollision`）。如果确实有遗漏，新版测试会通过常规运行暴露，不影响本计划关闭。
- Successor Required: `no`

### 差量 Timer 快照

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前 checkpoint 全量序列化 timer 在大规模（百万级场景）性能差，但功能正确。优化项不影响当前支持的功能 baseline。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 链化条件增强后，观察是否有用户 pipeline 因此拆链而产生行为变化（一般不会，因为之前只是宽松而非不正确）
- P2 任务的 RocksDB 状态后端可另启计划

## Closure

Status Note: （完成时填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent）
- Evidence: （task id / findings 摘要）

Follow-up:

- （明确写 no remaining plan-owned work）
