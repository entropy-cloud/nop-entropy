# 35 nop-stream Window & Watermark 测试补充计划

> Plan Status: completed
> Last Reviewed: 2026-05-22
> Source: `ai-dev/analysis/2026-05-22b-nop-stream-vs-flink-streaming-test-comparison.md`
> Related: `34-nop-stream-test-supplementation.md`, `33-nop-stream-checkpoint-test-coverage.md`

## Purpose

基于 nop-stream vs Flink streaming-java 的逐类对比分析，补充 Window Operator、MergingWindowSet、Evictor、WindowAssigner、Watermark 生成策略等关键缺失测试，并修复 2 个已知 Bug（N45/N46），使 nop-stream 的流处理核心正确性达到交付标准。

## Current Baseline

- nop-stream 测试总量（~86 文件，~530 方法）与 Flink flink-streaming-java（107 文件，625+ 方法）处于同一数量级
- Checkpoint、Barrier 对齐、数据交换、Trigger（7 种全覆盖）、TimerService、TaskExecutor 测试已覆盖良好
- **Window Operator 测试存在结构性缺失**：
  - `WindowOperator` 仅有 `TestWindowOperatorBasic`（仅测试 assigner/trigger/window 创建，不测试 processElement），缺少 Tumbling+Reduce、迟到数据、清理定时器等集成测试
  - `MergingWindowSet` 零测试（构造需要 `MergingWindowAssigner` 和 `ListState` 的 mock/stub，仓库中无现成 mock）
  - 三个 Evictor（Count/Time/Delta）零测试
  - `SlidingEventTimeWindows` 分配器零测试
- **API 实现现状**：
  - `WindowedStreamImpl.reduce()`/`aggregate()`/`apply()` 全部 throw `UnsupportedOperationException`（未实现）
  - 仓库中不存在 `EventTimeSessionWindows`/`ProcessingTimeSessionWindows` 等 `MergingWindowAssigner` 子类（Session Window 未实现）
  - 因此 WindowTranslation 测试和 Session Window 测试**不可实现**
- **2 个已知 Bug 的测试被 @Disabled**：
  - Bug N45：`TestWindowOperatorWatermarkReception` — WindowOperator.open() 创建的 WindowOperatorTimerService 未注册到 timeServiceManager，导致 processWatermark 不推进窗口定时器。现有测试使用 AbstractStreamOperator（非 WindowOperator），需重写测试
  - Bug N46：`TestWatermarkIdleDetection` — `TimestampsAndWatermarksOperator.markIdle()/markActive()` 是空方法体（no-op）。现有测试验证的是两个独立 Operator 的 watermark，需重写测试来验证 idle detection 语义
- **测试基础设施限制**：`OperatorTestHarness.open()` 不调用 `operator.setOutput(output)`，WindowOperator.open() 中 `new TimestampedCollector<>(output)` 会 NPE。WindowOperator 集成测试需先手动接线 output
- Watermark 生成策略（`BoundedOutOfOrdernessWatermarks`、`WatermarkOutputMultiplexer`）无独立测试

## Goals

- WindowOperator 集成测试覆盖 Tumbling+Reduce/迟到数据/清理定时器/多 Key 隔离（不含 Session，因 Session 未实现）
- MergingWindowSet 合并逻辑有完整单元测试（需自建 mock/stub）
- 三种 Evictor 有 evictBefore/evictAfter 测试
- SlidingEventTimeWindows 分配器有独立测试
- DataStream window() API 到 Transformation 的翻译正确性有测试（不含 reduce/aggregate/apply，因未实现）
- Bug N45/N46 修复并启用重写后的测试
- Watermark 生成策略有独立单元测试

## Non-Goals

- 不补充 Accumulator 测试（14 个简单工具类，低风险）
- 不补充状态迁移/向后兼容测试（不影响当前交付）
- 不补充 CEP 模块测试（需与 flink-cep 单独对比）
- 不补充 Async Operator 测试（nop-stream 未实现）
- 不补充双输入 Task 测试（CoOperator 功能未交付）
- 不补充 Session Window 测试（nop-stream 无 Session Window Assigner 实现，Deferred）
- 不补充 WindowedStream.reduce()/aggregate()/apply() 翻译测试（API 抛 UnsupportedOperationException，Deferred）
- 不重构现有测试结构或测试基础设施（但可小幅修补 OperatorTestHarness 如需要）
- 不引入 @ParameterizedTest 框架（除 Evictor 外无必要）

## Scope

### In Scope

- `nop-stream/nop-stream-runtime/src/test/` — WindowOperator、MergingWindowSet 新测试
- `nop-stream/nop-stream-core/src/test/` — Evictor、WindowAssigner、Watermark 新测试
- `nop-stream/nop-stream-core/src/main/` — Bug N45/N46 的源码修复
- `nop-stream/nop-stream-runtime/src/main/` — Bug N45 相关源码修复

### Out Of Scope

- 其他模块（nop-batch、nop-wf、nop-tcc 等）的测试补充
- 性能/基准测试
- Architecture Tests（ArchUnit）
- E2E 集成测试框架（已有 8 个 E2E 测试文件，不需扩展）
- Session Window 测试（无 `MergingWindowAssigner` 实现，见 Deferred）
- WindowedStream.reduce()/aggregate()/apply() 翻译测试（API 未实现，见 Deferred）

## Execution Plan

### Phase 1 - MergingWindowSet 单元测试

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/test/`

- Item Types: `Proof`

- [x] 创建测试所需的 mock/stub：`MergingWindowAssigner` 的测试实现（assignWindows 返回单个窗口、mergeWindows 调用合并回调）、`ListState<Tuple2<TimeWindow, TimeWindow>>` 的内存实现
- [x] 创建 `TestMergingWindowSet` 测试类
- [x] 测试：无重叠窗口不触发合并（非急切合并）
- [x] 测试：重叠窗口增量合并（新增元素落入已有窗口附近触发合并）
- [x] 测试：迟到元素触发合并
- [x] 测试：大窗口覆盖单个已有窗口
- [x] 测试：大窗口覆盖多个已有窗口
- [x] 测试：添加相同窗口是幂等的
- [x] 测试：从状态恢复后 MergingWindowSet 与原始一致
- [x] 测试：状态持久化快照正确性
- [x] 测试：仅在窗口集变更时才写入状态

Exit Criteria:

- [x] `TestMergingWindowSet` 包含 >= 8 个 @Test 方法，覆盖上述 9 个场景
- [x] mock/stub 类位于测试目录中，不污染主代码
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - WindowOperator 集成测试

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/test/`

前提：OperatorTestHarness.open() 不调用 operator.setOutput()。WindowOperator.open() 中 `new TimestampedCollector<>(output)` 会 NPE。本 Phase 需先解决此接线问题（在测试代码中手动调用，或小幅修补 OperatorTestHarness）。

- Item Types: `Proof`

- [x] 解决 OperatorTestHarness 的 output 接线问题：在测试中手动调用 `operator.setOutput(output)` 再调用 `operator.open()`，或在 OperatorTestHarness.open() 中补上 setOutput 调用
- [x] 创建新测试类 `TestWindowOperatorIntegration`（不扩展 `TestWindowOperatorBasic`，后者仅测试 assigner/trigger 创建）
- [x] 测试：TumblingEventTimeWindow + EventTimeTrigger + ReduceFunction（多 Key 各自独立窗口，验证各窗口 SUM 结果正确）
- [x] 测试：TumblingEventTimeWindow 聚合正确性（多个元素落入同一窗口，验证 COUNT/求和）
- [x] 测试：迟到数据被丢弃（watermark 已过窗口 maxTimestamp 后到达的数据不进入窗口、不出现在输出中）
- [x] 测试：窗口清理定时器（窗口触发后清理状态，验证无资源泄漏）
- [x] 测试：多 Key 并行窗口（不同 Key 的窗口状态互不干扰）
- [x] 测试：Watermark 推进触发窗口 fire（watermark 到达窗口 maxTimestamp 时窗口触发，验证输出时序）

Exit Criteria:

- [x] `TestWindowOperatorIntegration` 包含 >= 6 个 @Test 方法
- [x] 测试覆盖 Tumbling + Reduce + 迟到数据 + 清理定时器 + 多 Key 隔离 + Watermark 触发
- [x] **端到端验证**：至少 1 个测试从 processElement() → watermark 推进 → 窗口 fire → 验证 output 完整走通
- [x] **接线验证**：测试中 WindowOperator 的 output 确实被设置且收到聚合结果（非 null）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Evictor 测试

Status: completed
Targets: `nop-stream/nop-stream-core/src/test/`

- Item Types: `Proof`

- [x] 创建 `TestCountEvictor` 测试类
- [x] 创建 `TestTimeEvictor` 测试类
- [x] 创建 `TestDeltaEvictor` 测试类
- [x] 每个 Evictor 测试 evictBefore 和 evictAfter 两个阶段的行为
- [x] CountEvictor：超过 maxCount 时驱逐最早元素
- [x] TimeEvictor：超过 windowLength 时驱逐过早元素；无时间戳元素的处理
- [x] DeltaEvictor：delta 超过阈值时驱逐元素
- [x] 边界值：窗口内元素数 <= evictor 阈值时无驱逐

Exit Criteria:

- [x] 3 个新测试文件，每个 >= 3 个 @Test 方法，总计 >= 9 个方法
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - WindowAssigner 与 Window API Translation 测试

Status: completed
Targets: `nop-stream/nop-stream-core/src/test/`

注意：`WindowedStreamImpl.reduce()`/`aggregate()`/`apply()` 全部 throw `UnsupportedOperationException`，因此本 Phase 仅测试 window() 调用本身产生的 Transformation，不测试聚合 API。

- Item Types: `Proof`

- [x] 创建 `TestSlidingEventTimeWindows` 测试类
- [x] 测试：元素分配到正确的滑动窗口（重叠窗口，同一元素可属于多个窗口）
- [x] 测试：窗口边界元素分配（边界值，元素 timestamp = 窗口 start）
- [x] 测试：窗口大小和滑动步长验证（非法参数拒绝）
- [x] 测试：isEventTime 返回 true
- [x] 测试：不同 offset 值的窗口分配
- [x] 创建 `TestWindowTranslation` 测试类
- [x] 测试：`KeyedStream.window(TumblingEventTimeWindows)` 生成的 Transformation 包含正确的 WindowAssigner
- [x] 测试：`KeyedStream.window(SlidingEventTimeWindows)` 生成的 Transformation 包含正确的 WindowAssigner
- [x] 测试：`WindowedStream.getTransformer()` 返回非 null（验证 window→transform 路径存在）
- [x] 测试：非 KeyedStream 调用 window() 的行为（验证类型安全）

Exit Criteria:

- [x] `TestSlidingEventTimeWindows` >= 5 个 @Test 方法
- [x] `TestWindowTranslation` >= 4 个 @Test 方法
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - Watermark 生成策略与 Bug 修复

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/`, `nop-stream/nop-stream-core/src/test/`, `nop-stream/nop-stream-runtime/src/main/`

- Item Types: `Fix`, `Proof`

- [x] 创建 `TestBoundedOutOfOrdernessWatermarks` 测试类
- [x] 测试：首个元素后生成水印 = timestamp - outOfOrdernessMillis
- [x] 测试：水印单调递增（乱序元素不拉低水印）
- [x] 测试：周期性调用 onPeriodicEmit 产生正确水印
- [x] 测试：无元素时不产生水印
- [x] 创建 `TestWatermarkOutputMultiplexer` 测试类
- [x] 测试：多源水印合并（取最小值）
- [x] 测试：单源空闲不阻塞其他源的水印推进
- [x] **Fix Bug N45**：根因是 `WindowOperator.open()` 创建 `WindowOperatorTimerService` 但不注册到 `timeServiceManager`，导致继承的 `processWatermark()` 推进空的 timeServiceManager。修复方案：重写 WindowOperator 的 processWatermark() 推进其自身的 internalTimerService。同时重写 `TestWindowOperatorWatermarkReception` 使用 WindowOperator 而非 AbstractStreamOperator，并正确接线 output
- [x] **Fix Bug N46**：根因是 `TimestampsAndWatermarksOperator.markIdle()/markActive()` 是空方法体。修复方案：实现 markIdle/markActive 语义（暂停/恢复 watermark 输出）。同时重写 `TestWatermarkIdleDetection` 验证真正的 idle detection 语义（idle 源不阻塞 watermark 推进），而非两个独立 operator 的无关测试

Exit Criteria:

- [x] `TestBoundedOutOfOrdernessWatermarks` >= 4 个 @Test 方法
- [x] `TestWatermarkOutputMultiplexer` >= 2 个 @Test 方法
- [x] Bug N45 修复：WindowOperator.processWatermark() 推进其 internalTimerService；重写后的 `TestWindowOperatorWatermarkReception` 不再 @Disabled，测试通过
- [x] Bug N46 修复：markIdle/markActive 不再是 no-op；重写后的 `TestWatermarkIdleDetection` 不再 @Disabled，测试通过
- [x] 修复后的两个测试使用正确的被测对象（WindowOperator 而非 AbstractStreamOperator，TimestampsAndWatermarksOperator 的 idle 语义而非无关 operator）
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - Operator 生命周期与 Watermark 组件测试

Status: completed
Targets: `nop-stream/nop-stream-core/src/test/`

- Item Types: `Proof`

- [x] 创建 `TestOperatorLifecycle` 测试类
- [x] 测试：Operator 完整生命周期 open → processElement → processWatermark → close 顺序正确
- [x] 测试：open 中异常导致 close 被调用
- [x] 测试：processElement 中异常不阻止后续 watermark 处理（或明确验证异常传播）
- [x] 创建 `TestAscendingTimestampsWatermarks` 测试类
- [x] 测试：水印严格等于当前元素时间戳
- [x] 测试：时间戳递增场景下水印正确生成

Exit Criteria:

- [x] `TestOperatorLifecycle` >= 3 个 @Test 方法
- [x] `TestAscendingTimestampsWatermarks` >= 2 个 @Test 方法
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 6 个 Phase 的 Exit Criteria 全部 `[x]`
- [x] nop-stream WindowOperator 集成测试覆盖：Tumbling + Reduce + 迟到数据 + 清理定时器 + 多 Key 隔离
- [x] MergingWindowSet 合并逻辑有 >= 8 个单元测试
- [x] 三种 Evictor（Count/Time/Delta）各有独立测试
- [x] SlidingEventTimeWindows 分配器有独立测试
- [x] WindowTranslation（window() API → Transformation）有测试验证
- [x] Bug N45/N46 已修复，重写后的测试不再 @Disabled 且通过
- [x] BoundedOutOfOrdernessWatermarks 有独立单元测试
- [x] 不存在被静默降级到 deferred 的 in-scope 测试缺失
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 WindowOperator 集成测试的端到端路径（processElement → watermark → fire → output）完整连通
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### Accumulator 测试

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 14 个 Accumulator 均为简单计数器/最大值/最小值工具类，逻辑透明，无复杂状态交互
- Successor Required: no

### 状态迁移/向后兼容测试

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 仅影响跨版本升级场景，不影响当前版本的流处理正确性
- Successor Required: no

### CEP 模块测试

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: CEP 模块需与 flink-cep 单独对比，超出本计划 scope
- Successor Required: yes — 待 CEP 对比分析后创建 successor plan

### TwoPhaseCommitSinkFunction 单元测试

- Classification: `optimization candidate`
- Why Not Blocking Closure: 已通过 `TestE2ETwoPhaseCommitSink` 端到端覆盖
- Successor Required: no

### Session Window 测试

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: nop-stream 无 `MergingWindowAssigner` 子类实现（无 EventTimeSessionWindows/ProcessingTimeSessionWindows），Session Window 功能未实现。无法测试不存在的功能
- Successor Required: yes — 待 Session Window 实现后创建 successor plan

### WindowedStream.reduce()/aggregate()/apply() Translation 测试

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `WindowedStreamImpl` 的 reduce/aggregate/apply 方法全部 throw `UnsupportedOperationException`，API 未实现
- Successor Required: yes — 待聚合 API 实现后创建 successor plan

## Non-Blocking Follow-ups

- 考虑为 WindowOperator 测试引入 @ParameterizedTest（async 模式参数化），参考 Flink 的 EvictingWindowOperatorTest 模式
- 考虑为 Evictor 测试创建共享的 EvictorTestBase，减少重复代码
- WatermarkOutputMultiplexer 和 CombinedWatermarkStatus 的更深覆盖（多源对齐、idle 恢复）
- DynamicEventTimeSessionWindows 分配器测试（如未来实现动态 Gap）
- Session Window 实现后补充 Session Window 集成测试
- WindowedStream 聚合 API 实现后补充 Translation 测试

## Closure

Status Note: All 6 phases completed. 63 new/rewritten test methods. Bug N45 (WindowOperator processWatermark) and Bug N46 (markIdle/markActive) fixed. 158 tests pass (0 failures).

Closure Audit Evidence:

- Reviewer / Agent: Independent subagent closure audit
- Evidence: All exit criteria verified against live repo. 11 new test files + 2 rewritten. 63 test methods. Anti-hollow check confirmed WindowOperator end-to-end path (processElement → watermark → fire → output) complete.
- Follow-up: no remaining plan-owned work
