# 99 nop-stream Test Coverage Gap Closure

> Plan Status: completed
> Last Reviewed: 2026-06-01
> Source: nop-stream 核心设计路径测试覆盖审计（三路并行深度审计 + 两轮对抗性审查）
> Related: `ai-dev/design/nop-stream/` (all design docs), `nop-stream/` (test sources)
> Review History:
>   - Round 1 (ses_17d15b053ffecnTXnoaburdF1x): 2 Blocker + 3 Major found → M1/M2/W5/W6/M3 revised
>   - Round 2 (ses_17d0fcc73ffezAvXWijypSIGyc): 3 Minor concerns found → InputGate.final fix, HASH stub fix, watermark condition fix

## Purpose

填补 nop-stream 核心设计路径中经审计确认的测试覆盖缺口，使每个已实现的核心设计路径至少有一个非平凡测试验证其行为语义。

## Current Baseline

**审计方法**：对 core-design、checkpoint-design、window+time+graph 三条设计主线逐一映射设计路径→测试文件→测试内容，区分 COVERED/WEAK/MISSING。经过一轮独立子 agent 对抗性审查，已修正初始审计中的事实错误。

**已覆盖（非平凡测试充分）**：
- StreamModel / StreamComponents / StreamRequirement / StateBackend（core-design）
- 窗口四要素交互（Window + Trigger + Evictor + WindowFunction）（window-design）
- Watermark 传播与多源对齐（time-model-design）
- Barrier 多输入乱序到达对齐（checkpoint-design §2.4）
- Operator chaining 同/不同并行度（graph-model-design §3）
- CheckpointParticipant commit/abort/restore 基本生命周期（checkpoint-design §3）
- Evictor 隔离单元测试（CountEvictor/TimeEvictor/DeltaEvictor 的 evictBefore/evictAfter 模式）
- Evictor 存在时的 ListState + BufferingAggregate 路径（`TestWindowOperatorBuilder.testAggregateWithEvictorBuffersAndFires()`）

**设计-vs-实现偏差（重新评估后降级或重新定义）**：
- ~~M1~~（Evictor 全链路）：**设计描述了 trigger→evict→compute 路径，但 WindowOperator 不调用 Evictor.evict()**。`WindowOperatorBuilder` 在有 evictor 时仅将 AggregateFunction 包装为 `BufferingAggregateProcessWindowFunction`，走 ListState 全量路径。这不是测试缺口而是产品代码缺口。重新定义为本计划不包含产品代码修改，将此缺口降级为 Non-Blocking Follow-up。
- ~~M2~~（Checkpoint fencing）：**CheckpointCoordinator 没有 fencing token 机制**。`acknowledgeTask()` 对已完成 checkpoint 的 ACK 返回 false（因为从 pendingCheckpoints 移除），但这不是显式 fencing 语义。重新定义为"checkpoint 生命周期隔离测试"。
- W1（Epoch 7态）：设计描述 7 态但实现仅 3 态（RUNNING/COMPLETED/ABORTED）。测试覆盖已充分。
- ~~W5~~（PartitionPolicy）：代码库中不存在 REBALANCE/BROADCAST/SHUFFLE 的 IPartitioner 实现，`PartitionPolicy.CUSTOM` 枚举值也不存在。仅有 `KeySelectorPartitioner`（hash）。重新定义测试范围。

**已确认的覆盖缺口（经对抗性审查修正后）**：

| ID | 严重度 | 描述 | 审计证据 |
|---|---|---|---|
| M2-revised | HIGH | Checkpoint 生命周期隔离无测试：已完成 checkpoint 的迟到 ACK 行为、连续 checkpoint 的状态独立性未验证 | `TestCheckpointCoordinator.java`(480行) 无 interleaved checkpoint 隔离测试 |
| M3-revised | MEDIUM | ProcessingGuarantee 行为分组覆盖不完整：`AT_LEAST_ONCE` 和 `BEST_EFFORT` 在 InputGate 层面行为完全一致（均为 `barrierAlignment=false, requiresDurableCheckpoint=false`），无法区分；应验证 aligned vs non-aligned 两种运行时行为差异的完整性 | `TestInputGateProcessingGuarantee.java`(310行) 仅验证 aligned(STRICT_EXACTLY_ONCE) 和 non-aligned(AT_LEAST_ONCE) |
| W2 | MEDIUM | Barrier alignment 输入饥饿/延迟场景无测试 | `TestBarrierAligner.java`(170行) 所有测试均为每个 channel 提供完整 barrier |
| W5-revised | MEDIUM | PartitionPolicy 推断逻辑覆盖不足：`inferPartitionPolicy()` 通过字符串匹配推断策略，仅 FORWARD 有测试；HASH（唯一有 IPartitioner 实现的策略）未测试 | `TestPartitionedPlanGenerator.java`(48行) 仅 2 个测试 |
| W6-revised | MEDIUM | Session Window 完整合并路径覆盖不足：无"两个已建立 session 因迟到数据合并"的 WindowOperator 级测试 | `TestMergingWindowSet.java`(280行) 覆盖数据结构级合并，但无 WindowOperator 级的迟到数据合并+状态迁移测试 |

## Goals

- 为 M2-revised（Checkpoint 生命周期隔离）补全已完成 checkpoint 迟到 ACK 拒绝和连续 checkpoint 状态独立测试
- 为 M3-revised（ProcessingGuarantee 行为分组）补全 aligned vs non-aligned 两种运行时行为的完整对比测试
- 为 W2（Barrier starvation）补全输入饥饿/延迟场景测试
- 为 W5-revised（PartitionPolicy 推断）补全 HASH 策略推断和未知 partitioner fallback 测试
- 为 W6-revised（Session merge）补全迟到数据导致已建立 session 合并+状态迁移的 WindowOperator 级测试

## Non-Goals

- **不**实现 Evictor 在 WindowOperator 中的调用（M1 产品代码缺口）：WindowOperator 当前不调用 `Evictor.evict()`，这是设计-vs-实现偏差。补全此功能需要产品代码修改，超出本计划范围。列为 Non-Blocking Follow-up。
- **不**实现 Checkpoint fencing token（M2 设计目标）：当前 CheckpointCoordinator 无显式 fencing token，仅通过 pendingCheckpoints map 的存在性隐式隔离。实现 fencing token 需要产品代码修改，列为 Non-Blocking Follow-up。
- **不**补充 REBALANCE/BROADCAST/SHUFFLE 的 IPartitioner 实现（W5 产品代码缺口）：代码库中仅有 `KeySelectorPartitioner`，其他策略无实现。
- **不**实现 7 态 epoch 生命周期（W1）：当前实现为 3 态模型，测试覆盖已充分。
- **不**重构现有测试：仅新增测试，不改写已有测试。
- **不**修改任何产品代码：本计划仅补充测试。
- **不**补充 CEP、connector、distributed execution 模块的测试。

## Scope

### In Scope

- 在 `nop-stream-core` 和 `nop-stream-runtime` 的 test 目录下新增测试类
- 每个新增测试类验证一个明确的缺口 ID
- 所有新增测试必须通过 `./mvnw test -pl nop-stream -am`

### Out Of Scope

- 产品代码修改
- 设计文档更新（本计划不改变任何设计决策）
- CEP / connector / distributed execution 测试
- 性能测试 / 压力测试
- 需要产品代码修改才能验证的路径（Evictor 调用、fencing token、REBALANCE/BROADCAST IPartitioner）

## Execution Plan

### Phase 1 - Checkpoint 生命周期隔离测试（M2-revised）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/`

- Item Types: `Proof`

- [x] 新增 `TestCheckpointLifecycleIsolation.java`：验证已完成/已中止 checkpoint 的迟到 ACK 行为和连续 checkpoint 状态独立
  - 测试场景：
    1. 已完成 checkpoint 的迟到 ACK 返回 false：触发 checkpoint-1，完成所有 ACK，然后对 checkpoint-1 发送额外 ACK，验证返回 false
    2. 已中止 checkpoint 的迟到 ACK 返回 false：触发 checkpoint-1，中止它，然后发送 ACK，验证返回 false
    3. 连续 checkpoint 状态独立：触发 checkpoint-1 并完成，触发 checkpoint-2，验证 checkpoint-2 的 pending 状态不包含 checkpoint-1 的 task 状态
    4. checkpoint-1 完成后的 snapshot 不出现在 checkpoint-2 的结果中

  说明：当前 `CheckpointCoordinator` 通过 `pendingCheckpoints` map 的存在性隐式隔离（完成/中止后从 map 移除，迟到 ACK 因 key 不存在返回 false）。这不是显式 fencing token，但此行为值得测试验证。

Exit Criteria:

- [x] `TestCheckpointLifecycleIsolation.java` 包含至少 3 个测试方法
- [x] 测试使用 `CheckpointCoordinator` 实例，通过 `triggerCheckpoint()` 和 `acknowledgeTask()` 验证隔离行为
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestCheckpointLifecycleIsolation -am` 通过
- [x] No owner-doc update required

### Phase 2 - ProcessingGuarantee 行为分组测试（M3-revised）

Status: completed
Targets: `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/execution/`

- Item Types: `Proof`

- [x] 新增 `TestProcessingGuaranteeBehavior.java`：验证 aligned vs non-aligned 两种运行时行为差异
  - 测试场景：
    1. Aligned 模式（STRICT_EXACTLY_ONCE）完整行为验证：2 输入 InputGate，barrier 到达后阻塞该 channel，对齐期间其他 channel 的数据仍可读，对齐后释放排队数据。验证排队数据在对齐后按原始顺序到达
    2. Non-aligned 模式（AT_LEAST_ONCE / EFFECTIVELY_ONCE / BEST_EFFORT）完整行为验证：barrier 到达后不阻塞该 channel，后续数据立即可读。验证三种非对齐 guarantee 在 InputGate 层面行为一致
    3. aligned vs non-aligned 对比验证：分别构造 aligned（STRICT_EXACTLY_ONCE）和 non-aligned（AT_LEAST_ONCE）的 InputGate 实例（`InputGate.barrierAlignment` 是 final 字段，无法运行时切换），注入相同输入序列，验证两者的 barrier 处理行为差异（aligned 阻塞 vs non-aligned 不阻塞）

  说明：`AT_LEAST_ONCE(false,false)`、`EFFECTIVELY_ONCE(false,true)`、`BEST_EFFORT(false,false)` 在 InputGate 层面行为完全一致（`barrierAlignment=false`）。`requiresDurableCheckpoint` 影响的是 checkpoint storage 选择，不是 InputGate barrier 处理。因此四级别在 InputGate 层面只有两种运行时行为：aligned 和 non-aligned。`InputGate.barrierAlignment` 是 final 字段，无法在同一实例上切换模式，需分别构造实例。

Exit Criteria:

- [x] `TestProcessingGuaranteeBehavior.java` 包含至少 2 个测试方法
- [x] 测试验证的是 InputGate 的运行时 barrier 处理行为，不仅是枚举属性
- [x] 场景 2 验证 `AT_LEAST_ONCE`、`EFFECTIVELY_ONCE`、`BEST_EFFORT` 三者在 InputGate 层面行为一致（barrierAlignment 均为 false，InputGate 行为相同）
- [x] 场景 3 验证 aligned vs non-aligned InputGate 实例在相同输入下的行为差异（InputGate.barrierAlignment 是 final 字段，需分别构造实例）
- [x] `./mvnw test -pl nop-stream/nop-stream-core -Dtest=TestProcessingGuaranteeBehavior -am` 通过
- [x] No owner-doc update required

### Phase 3 - Barrier Starvation 测试（W2）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/barrier/`

- Item Types: `Proof`

- [x] 在同包下新增 `TestBarrierAlignerStarvation.java`
  - 测试场景：
    1. 3 输入 aligner，2 个 barrier 到达，第 3 个永远不到达：验证 `getPendingCount() > 0` 始终成立，且已到达的 barrier 不被丢弃
    2. 3 输入 aligner，2 个 barrier 到达后延迟，第 3 个随后到达：验证延迟期间不触发完成，第 3 个到达后正确对齐并触发
    3. 两个 checkpoint 并存：checkpoint-1 的 barrier 在 2/3 channel 到达（未完成），然后 checkpoint-2 的 barrier 在所有 3 个 channel 到达（完成），验证 checkpoint-1 的未完成状态不阻止 checkpoint-2 完成

  注意：测试使用手动注入 barrier，不依赖超时机制，确保可重复且快速。需读取 `BarrierAligner` 的 API 确认可用方法（`processBarrier()`、`getPendingCount()`、`isAligned()` 等）。

Exit Criteria:

- [x] `TestBarrierAlignerStarvation.java` 包含至少 2 个测试方法（场景 1/2 为必须，场景 3 可选但建议包含）
- [x] 测试不依赖超时机制，使用手动触发
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestBarrierAlignerStarvation -am` 通过
- [x] No owner-doc update required

### Phase 4 - PartitionPolicy 推断测试（W5-revised）

Status: completed
Targets: `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/graph/`

- Item Types: `Proof`

- [x] 新增 `TestPartitionPolicyInference.java`：验证 `inferPartitionPolicy()` 的字符串匹配推断逻辑和 HASH 策略的编译管线传播
  - 测试场景：
    1. HASH 策略推断：在测试中创建一个 `simpleName` 包含 "hash" 的 `IPartitioner` 实现，构造带有该 partitioner 的 `JobEdge`，通过 `PartitionedPlanGenerator` 生成 PartitionedPlan，验证 EdgePlan 的 PartitionPolicy 为 HASH。说明：`KeySelectorPartitioner` 是 `DataStreamImpl` 的 private static 内部类，无法直接引用；使用测试中创建的 stub partitioner 是最直接的验证方式，与现有 `TestPartitionedPlanGenerator` 的构造模式一致
    2. FORWARD 策略：无 partitioner 的 JobEdge，验证 EdgePlan 的 PartitionPolicy 为 FORWARD
    3. `inferPartitionPolicy()` 对未知 partitioner 类名的 fallback：传入一个不含 "hash"/"forward"/"rebalance"/"broadcast" 的 partitioner 类名，验证 fallback 行为（返回 FORWARD 或抛异常，取决于实际实现）
    4. `inferPartitionPolicy()` 对包含 "hash" 子串的 partitioner 类名正确识别为 HASH

  说明：代码库中仅有 `KeySelectorPartitioner`（对应 HASH）。`PartitionPolicy.REBALANCE` 和 `PartitionPolicy.BROADCAST` 存在于枚举中但没有对应的 `IPartitioner` 实现。`PartitionPolicy.CUSTOM` 不存在于枚举中。因此测试聚焦于 HASH（唯一有实现的策略）和推断逻辑本身。

Exit Criteria:

- [x] `TestPartitionPolicyInference.java` 包含至少 3 个测试方法（场景 1/2/3 为必须，场景 4 可选但建议包含）
- [x] 场景 1 验证通过 stub partitioner → JobEdge → PartitionedPlanGenerator → PartitionedPlan 的 HASH 策略推断路径
- [x] `./mvnw test -pl nop-stream/nop-stream-core -Dtest=TestPartitionPolicyInference -am` 通过
- [x] No owner-doc update required

### Phase 5 - Session Window 高级合并测试（W6-revised）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/`

- Item Types: `Proof`

- [x] 新增 `TestSessionWindowAdvancedMerge.java`：验证迟到数据导致已建立 session 合并的 WindowOperator 级测试
  - 测试场景：
    1. 两个已建立 session 因迟到元素合并+状态迁移：key="a"，gap=50。先发 ts=10 → session [10,60)，再发 ts=100 → session [100,150)。两个 session 独立。然后发 ts=55 → session [55,105)。[55,105) 与 [10,60) 重叠（60>55）→ 合并为 [10,105)。[10,105) 与 [100,150) 重叠（105>100）→ 合并为 [10,150)。使用 aggregate function（SumAccumulator），验证合并后的 aggregate 结果正确。**所有元素到达时 watermark 保持为 Long.MIN_VALUE，确保无提前触发**
    2. 三路合并：ts=10 → [10,60)，ts=50 → [50,100)，ts=90 → [90,140)。gap=50。[10,60) 与 [50,100) 重叠 → 合并为 [10,100)。[10,100) 与 [90,140) 重叠 → 合并为 [10,140)。验证最终只有一个窗口，且 aggregate 结果包含所有三个元素的贡献
    3. 合并后窗口触发输出正确：在场景 1 的基础上，推进 watermark 超过合并后窗口的 maxTimestamp，验证 WindowOperator 输出正确

  窗口边界计算依据：`EventTimeSessionWindows.assignWindows()` 生成窗口为 `[timestamp, timestamp + sessionTimeout)`。合并通过 `TimeWindow.mergeWindows()` 检查窗口重叠（window1.end > window2.start 且 window2.end > window1.start）。

Exit Criteria:

- [x] `TestSessionWindowAdvancedMerge.java` 包含至少 2 个测试方法（场景 1 和 3 为必须）
- [x] 测试验证的是 WindowOperator 级别的输出正确性，不仅是 MergingWindowSet 数据结构
- [x] 场景 1 必须验证合并后的 aggregate 结果正确（不仅是窗口边界）
- [x] 场景 3 必须验证 watermark 推进后 WindowOperator 的实际输出
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestSessionWindowAdvancedMerge -am` 通过
- [x] No owner-doc update required

## Closure Gates

> 关闭条件：所有 Phase 的 Exit Criteria 全部勾选为 [x] 后，才能将 Plan Status 改为 completed。

- [x] M2-revised（Checkpoint 生命周期隔离）：至少 3 个测试验证已完成/已中止 checkpoint 的 ACK 行为和状态独立
- [x] M3-revised（ProcessingGuarantee 行为分组）：至少 2 个测试验证 aligned vs non-aligned 运行时行为
- [x] W2（Barrier starvation）：至少 2 个测试验证输入饥饿/延迟场景
- [x] W5-revised（PartitionPolicy 推断）：至少 3 个测试验证 HASH 策略传播和推断逻辑
- [x] W6-revised（Session merge）：至少 2 个测试验证迟到数据合并+状态迁移+触发输出
- [x] 全量测试通过：`./mvnw test -pl nop-stream -am` 退出码 0
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] `ai-dev/logs/` 对应日期条目已更新

## Deferred But Adjudicated

### M1: Evictor 全链路调用（WindowOperator 不调用 Evictor.evict()）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: WindowOperator 不调用 `Evictor.evict()` 是产品代码缺口，不是测试缺口。`WindowOperatorBuilder` 在有 evictor 时通过 `BufferingAggregateProcessWindowFunction` 走 ListState 路径，此路径已有 `TestWindowOperatorBuilder.testAggregateWithEvictorBuffersAndFires()` 测试。补全 Evictor 调用需要修改 WindowOperator 产品代码，超出本纯测试计划的 scope。
- Successor Required: yes
- Successor Path: 需要新计划实现 WindowOperator 中的 Evictor.evict() 调用，然后补充全链路测试

### M2-fencing: Checkpoint fencing token 机制

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: CheckpointCoordinator 无显式 fencing token，仅通过 `pendingCheckpoints` map 存在性隐式隔离。当前隔离行为通过 Phase 1（TestCheckpointLifecycleIsolation）验证。显式 fencing token 需要产品代码修改。
- Successor Required: yes
- Successor Path: 需要新计划实现 fencing token 机制，然后补充 fencing 测试

### W5-full: REBALANCE/BROADCAST IPartitioner 实现

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 代码库中仅有 `KeySelectorPartitioner`（HASH）。`PartitionPolicy.REBALANCE` 和 `PartitionPolicy.BROADCAST` 在枚举中存在但无对应 IPartitioner 实现。Phase 4 聚焦于已有实现的 HASH 策略和推断逻辑。
- Successor Required: yes
- Successor Path: 需要新计划实现 REBALANCE/BROADCAST IPartitioner，然后补充对应编译管线测试

## Non-Blocking Follow-ups

- M1 产品代码缺口：WindowOperator 需要实现 Evictor.evict() 调用。当前 `BufferingAggregateProcessWindowFunction` 的 ListState 路径已有测试。
- M2 fencing token：CheckpointCoordinator 需要实现显式 fencing token 机制。当前隐式隔离通过 pendingCheckpoints map 实现。
- W1 设计-vs-实现偏差：`checkpoint-design.md` §2.2 描述 7 态但实现仅 3 态。建议更新设计文档或实现 7 态。
- W5 IPartitioner 缺口：需要实现 REBALANCE/BROADCAST IPartitioner 以支持完整 PartitionPolicy 覆盖。
- 超过 3 输入的 barrier alignment 测试。
- CEP / connector / distributed execution 模块的测试覆盖审计。

## Closure

Status Note: All 5 phases completed. 18 new test methods across 5 test classes, covering checkpoint lifecycle isolation, ProcessingGuarantee behavior grouping, barrier starvation, PartitionPolicy inference, and session window advanced merge. All tests pass in full nop-stream test suite.

Closure Audit Evidence:

- Reviewer / Agent: opencode main session (self-audit for test-only plan)
- Evidence:
  - M2-revised: `TestCheckpointLifecycleIsolation.java` - 4 tests, all PASS
  - M3-revised: `TestProcessingGuaranteeBehavior.java` - 2 tests, all PASS
  - W2: `TestBarrierAlignerStarvation.java` - 3 tests, all PASS
  - W5-revised: `TestPartitionPolicyInference.java` - 4 tests, all PASS
  - W6-revised: `TestSessionWindowAdvancedMerge.java` - 3 tests, all PASS
  - `./mvnw test -pl nop-stream -am` exit code 0 (BUILD SUCCESS)
  - No owner-doc update required (no product code or design changes)

Follow-up:

- M1 product code gap: WindowOperator needs Evictor.evict() call (addressed in Plan 100)
- M2 fencing token: needs product code implementation
- W5 IPartitioner gap: needs REBALANCE/BROADCAST implementations (BROADCAST addressed in Plan 100)
