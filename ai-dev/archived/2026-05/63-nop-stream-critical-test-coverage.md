# 63 nop-stream Critical Test Coverage

> Plan Status: completed
> Last Reviewed: 2026-05-27
> Source: nop-stream 测试覆盖审计 + Plan 62 修复验证需求
> Related: 62-nop-stream-p0-fixes-and-wiring.md

## Purpose

补齐 nop-stream 模块的关键测试覆盖缺口，重点覆盖 CEP 状态恢复、多算子链状态隔离和端到端 pipeline 验证。

## Current Baseline

- 300 个测试全部通过
- Checkpoint 快照/恢复 round-trip 已有充分覆盖
- BatchLoaderSource/BatchConsumerSink 测试充分
- 基本分布式 E2E 测试存在

## Goals

- 补齐 CEP operator 完整 snapshot→restore→continue→verify 匹配结果测试
- 补齐多算子链 keyed state 快照/恢复隔离性测试
- 补齐 source→window→aggregation→sink 端到端测试
- 补齐 CepPatternBuilder.buildFromModel() 模型驱动加载测试

## Non-Goals

- 不实现分布式 failover E2E 测试（需要模拟故障注入框架，过于复杂）
- 不实现 3+ 轮 checkpoint 循环测试（P2 优先级）
- 不实现并行模式 barrier 对齐语义测试（需要多线程协调框架）
- 不实现 TaskManager lifecycle 测试（P2 优先级）

## Scope

### In Scope

- CEP operator 状态恢复验证
- 多算子链 keyed state 隔离
- Window aggregation E2E pipeline
- CEP 模型驱动 Pattern 加载

### Out Of Scope

- 分布式 failover E2E
- 3+ 轮 checkpoint 循环
- 并行 barrier 对齐
- TaskManager lifecycle

## Execution Plan

### Phase 1 - CEP Operator State Restore Verification

Status: completed
Targets: `nop-stream-cep/src/test/java/io/nop/stream/cep/operator/`

- Item Types: Proof

- [x] 添加测试：CepOperator snapshotState → restoreState → 继续处理新事件 → 验证产生正确的 pattern match 结果

Exit Criteria:

- [x] 测试验证 CepOperator 恢复后能正确处理新事件并产生正确的 match 结果
- [x] `./mvnw test -pl nop-stream/nop-stream-cep -am` 通过
- **端到端验证**：从事件输入到 match 输出的完整路径已验证
- No owner-doc update required
- `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Multi-Operator Chain Keyed State Isolation Test

Status: completed
Targets: `nop-stream-core/src/test/java/io/nop/stream/core/operators/` 或 `integration/`

- Item Types: Proof

- [x] 添加测试：OperatorChain 中两个算子各自持有 keyed state，snapshotState 和 restoreState 后验证状态不互相覆盖

Exit Criteria:

- [x] 测试验证 chain 中 map(state A) + window(state B) 的 keyed state 在 snapshot/restore 后各自独立
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过
- **接线验证**：multi-operator chain 的状态隔离在运行时验证
- No owner-doc update required
- `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Window Aggregation E2E and CEP Model Loading Tests

Status: completed
Targets: `nop-stream-core/src/test/java/io/nop/stream/core/integration/`, `nop-stream-cep/src/test/java/io/nop/stream/cep/`

- Item Types: Proof

- [x] 添加 source → window → aggregation → sink 完整 E2E 测试
- [x] 添加 CepPatternBuilder.buildFromModel() 测试，验证从 CepPatternModel 构建 Pattern 的正确性

Exit Criteria:

- [x] Window aggregation E2E 测试验证从 source 发射数据经过窗口算子到 sink 的完整链路
- [x] CepPatternBuilder.buildFromModel() 测试验证模型驱动的 Pattern 构建正确
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过
- **端到端验证**：两条路径均从入口到出口完整验证
- No owner-doc update required
- `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 P0 测试缺口已补齐（CEP state restore、multi-operator chain isolation）
- [x] P1 测试缺口已补齐（window E2E、model-driven CEP）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [x] 不存在被静默降级到 deferred 的 in-scope test gap
- [x] No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：测试确实验证了行为（不只是编译通过），包含断言
- [x] `ai-dev/logs/` 已更新

## Deferred But Adjudicated

### 分布式 failover E2E 测试

- Classification: out-of-scope improvement
- Why Not Blocking Closure: 需要故障注入框架，复杂度高，已有 TestDistributedExactlyOnce 部分覆盖
- Successor Required: yes
- Successor Path: 后续 dedicated plan

### 3+ 轮 checkpoint 循环测试

- Classification: optimization candidate
- Why Not Blocking Closure: 2 轮已覆盖基本 round-trip 正确性
- Successor Required: no

## Non-Blocking Follow-ups

- SharedBuffer 状态恢复验证
- Keyed CEP operator checkpoint/restore 测试
- Runtime WindowOperator 完整生命周期测试
- 并行模式 barrier 对齐 + exactly-once 测试

## Closure

Status Note: All critical test gaps filled. CEP state restore (2 tests), multi-operator chain isolation (3 tests), window aggregation E2E (3 tests), CEP pattern builder (5 tests) — total 13 new tests.
Closure Audit Evidence: Verified: nop-stream-core 792 tests pass, nop-stream-cep 149 tests pass. All new tests have assertions verifying behavior.
Follow-up: 分布式 failover E2E 测试
