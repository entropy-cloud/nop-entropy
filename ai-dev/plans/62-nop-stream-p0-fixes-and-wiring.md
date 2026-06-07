# 62 nop-stream P0 Fixes and Critical Wiring Issues

> Plan Status: completed
> Last Reviewed: 2026-05-27
> Source: nop-stream 模块审计（接口契约 + 组件接线 + 测试覆盖三轮审计）
> Related: 51-nop-stream-window-operator-fixes.md, 53-nop-stream-cep-connector-correctness.md

## Purpose

修复 nop-stream 审计发现的 P0 正确性缺陷和关键 P1 接线问题，使模块在分布式执行和 checkpoint 恢复场景下行为正确。

## Current Baseline

- 构建通过：`./mvnw clean install -pl nop-stream -am -T 1C` ✅
- 全量测试通过：300 tests, 0 failures ✅
- JdbcCheckpointStorage 使用 IJdbcTemplate ✅
- Barrier 注入使用 source-pull 模式 ✅
- Keyed state 按 (namespace, key) 复合键隔离 ✅
- 已知技术债：4 个空壳模块、runtime 零代码引用 cep

## Goals

- 修复 `JobGraphGenerator.createOperatorFromFactory()` 返回共享模板实例的 bug（每个 subtask 必须获得独立 operator 副本）
- 修复 `CepOperator` 在无 `IKeyedStateBackend` 时回退到无 namespace 隔离的 `SimpleKeyedStateStore`
- 修复 `StreamSinkOperator.processWatermark()` 空方法体导致 watermark 跟踪失效
- 实现 `MemoryKeyedStateBackend.getReducingState()` 以完整支持状态后端 API
- 修复 `CheckpointType.fromName()` 返回 null 而非抛异常

## Non-Goals

- 不实现 `WindowOperatorFactory` 路由（当前两条 window 路径独立工作）
- 不添加 @Nullable 注解（防御性改进，非 bug）
- 不修改空壳模块（api/checkpoint/flink/flow）
- 不处理 fraud-example 的质量改进

## Scope

### In Scope

- `JobGraphGenerator` operator 实例化逻辑
- `CepOperator` 状态存储选择逻辑
- `StreamSinkOperator` watermark 处理
- `MemoryKeyedStateBackend` ReducingState 实现
- `CheckpointType.fromName()` 错误处理

### Out Of Scope

- 新增测试覆盖（归 Plan 63）
- WindowOperatorFactory 路由
- @Nullable 注解
- 文档更新（无 API 变更）

## Execution Plan

### Phase 1 - Fix JobGraphGenerator Shared Instance Bug

Status: completed
Targets: `nop-stream-core/.../jobgraph/JobGraphGenerator.java`

- Item Types: Fix

- [x] 将 `createOperatorFromFactory()` 中 `SimpleStreamOperatorFactory` 分支从 `getRawOperator()` 改为 `factory.createStreamOperator(outputType)`，确保每次调用返回独立深拷贝实例

Exit Criteria:

- [x] `JobGraphGenerator.createOperatorFromFactory()` 对 `SimpleStreamOperatorFactory` 调用 `createStreamOperator` 而非 `getRawOperator`
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过
- **端到端验证**：并行执行（parallelism=2）的测试仍然通过，确认算子链创建正确
- **无静默跳过**：确认无新增空方法体或吞异常
- No owner-doc update required（内部实现修复，无 API 变更）
- `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Fix CepOperator State Store Fallback

Status: completed
Targets: `nop-stream-cep/.../operator/CepOperator.java`

- Item Types: Fix

- [x] 当 `IKeyedStateBackend` 不可用时，抛出明确异常而非静默回退到 `SimpleKeyedStateStore`，或在无 backend 时创建一个基于 HashMap 的 `MemoryKeyedStateBackend` 作为 fallback

Exit Criteria:

- [x] CepOperator 不再使用 `SimpleKeyedStateStore`，或 `SimpleKeyedStateStore` 增加了 namespace 隔离支持
- [x] CEP 测试全部通过
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过
- **接线验证**：CepOperator 使用 IKeyedStateBackend 时状态隔离正确
- No owner-doc update required
- `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Fix Sink Watermark and State Backend Gaps

Status: completed
Targets: `nop-stream-core/.../operators/StreamSinkOperator.java`, `nop-stream-core/.../state/backend/memory/MemoryKeyedStateBackend.java`, `nop-stream-core/.../checkpoint/CheckpointType.java`

- Item Types: Fix

- [x] `StreamSinkOperator.processWatermark()` 改为调用 `super.processWatermark()` 或更新 watermark 跟踪
- [x] `MemoryKeyedStateBackend.getReducingState()` 实现完整，基于 `getInternalAppendingState`
- [x] `CheckpointType.fromName()` 在未找到时抛 `IllegalArgumentException` 而非返回 null

Exit Criteria:

- [x] StreamSinkOperator 不再静默丢弃 watermark
- [x] MemoryKeyedStateBackend.getReducingState() 可正常返回 ReducingState 实例
- [x] CheckpointType.fromName() 未知名称抛异常
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过
- **无静默跳过**：无新增空方法体或吞异常
- No owner-doc update required
- `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 P0 缺陷已修复（JobGraphGenerator 共享实例 bug）
- [x] 所有 in-scope P1 接线问题已修复
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [x] `./mvnw compile -pl nop-stream -am -T 1C` 通过
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] No owner-doc update required（无 API 变更）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：组件间调用链在运行时确实连通，无空方法体/静默跳过
- [x] `ai-dev/logs/` 已更新

## Deferred But Adjudicated

### MergingWindowSet.persist() state==null 静默丢弃

- Classification: watch-only residual
- Why Not Blocking Closure: 仅在 WindowOperator 使用非 IInternalStateBackend 的后端时触发，当前所有 production 路径使用 MemoryKeyedStateBackend（implements IInternalStateBackend），不会命中此路径
- Successor Required: no

### WindowedStreamImpl 无 WindowOperatorFactory 路由

- Classification: optimization candidate
- Why Not Blocking Closure: 两条 window 实现路径独立工作，当前无生产场景需要路由切换
- Successor Required: no

## Non-Blocking Follow-ups

- 为关键 null-return 方法添加 @Nullable 注解
- PendingCheckpoint.acknowledgePrecedingCheckpoint() 未实现但无调用方（清理或实现）
- ReduceAggregationFunction.createAccumulator() 返回 null（已由调用方正确处理）

## Closure

Status Note: All P0/P1 fixes completed. 300+ tests pass. CepOperator uses MemoryKeyedStateBackend, StreamSinkOperator processes watermark timers, MemoryKeyedStateBackend supports ReducingState, CheckpointType.fromName() throws on unknown.
Closure Audit Evidence: Build + tests verified: `./mvnw test -pl nop-stream -am -T 1C` → 300 tests, 0 failures, BUILD SUCCESS.
Follow-up: Plan 63 补齐关键测试覆盖
