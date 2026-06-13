# 43 nop-stream 核心算子正确性修复与 Timer/状态 Checkpoint

> Plan Status: completed
> Last Reviewed: 2026-05-24
> Source: gap analysis 确认 Plan 44+45 不覆盖核心算子正确性；`component-roadmap.md` §4 阶段 1-2；源码审计发现 WindowAggregationOperator 无 snapshot/restore、WindowOperator 累加器腐蚀、Timer 不 checkpoint
> Related: Plan 44 (本地 runtime 模型集成), Plan 45 (分布式 runtime), Plan 43 必须在 Plan 44/45 之前或并行完成

## Purpose

修复 nop-stream 核心算子的正确性 bug，实现 Timer state 和窗口状态的 checkpoint/restore，使得 Plan 44+45 执行完后引擎的计算结果可信。当前状态：窗口聚合累加器正确但 checkpoint 后全部丢失、Timer 不被快照、WindowOperator 有多处正确性 bug。本计划完成后，从 `env.addSource()` 到窗口聚合输出到 checkpoint 恢复的完整路径上，数据和状态不丢失。

## Current Baseline

**两套窗口算子并存**：

| 算子 | 模块 | 标准 API 使用 | 行数 | 累加器正确 | Timer snapshot | 状态 snapshot | 测试 |
|------|------|-------------|------|----------|---------------|-------------|------|
| `WindowAggregationOperator` | core | **是**（WindowedStreamImpl.apply/aggregate/reduce 使用此算子） | 398 | 正确 | **无** | **无** | 无集成测试 |
| `WindowOperator` | runtime | 否（仅通过 transform() 手动使用） | 1015 | **错误** | **无** | 依赖父类（只快照 keyed state） | 3 个测试文件，无累加器/merge 测试 |

**WindowAggregationOperator 是用户实际使用的窗口算子**。它通过 WindowedStreamImpl 的 apply/aggregate/reduce 方法创建，累加器逻辑正确（`createAccumulator() → add() → put`），但所有状态都是 `transient` 内存 Map，checkpoint 后全部丢失。

**WindowOperator 的已知 bug**：
- `addWindowElement()` 第一个元素不通过累加器（直接强转 `(ACC)value`，IN≠ACC 时 ClassCastException）
- `getSimpleAccumulator()` 始终抛 UnsupportedOperationException（CountTrigger/ContinuousEventTimeTrigger 等不可用）
- `mergeWindowContents()` 吞 ClassCastException，静默丢弃 target 已累积数据
- `mergeWindowContents()` 合并两个累加器窗口时类型错误

**Timer checkpoint 缺失**：
- `WindowAggregationOperator` 的 TreeMap timers 不被快照
- `WindowOperator` 的 PriorityQueue timers 不被快照
- `WindowOperatorTimerService` 无 snapshot/restore 逻辑
- **不变量 #9（timer state 是窗口和 CEP exactly-once 的必要状态）被违反**

**其他正确性问题**：
- `WindowAggregationOperator` 不处理 late data（元素时间戳 < 当前 watermark），导致迟到元素创建新空窗口，内存泄漏
- `WindowAggregationOperator.purgeWindow()` 有复制粘贴死代码
- `AbstractStreamOperator.restoreState()` 的 `break` 只恢复第一个 keyed state entry
- `MemoryKeyedStateBackend.getListState()` 始终抛异常
- `TimestampsAndWatermarksOperator` 的 watermark 发射依赖 wall-clock time，批量数据可能不触发周期性 emit（但 `finish()` 会补发）

## Goals

1. **WindowAggregationOperator checkpoint/restore 可用**：所有窗口状态（windowState、timer、triggerState、currentWatermark）在 checkpoint 时完整快照，恢复后正确重建
2. **Timer state checkpoint 落地**：满足不变量 #9
3. **WindowOperator 核心正确性修复**：累加器腐蚀、getSimpleAccumulator、mergeWindowContents 三个 bug 修复
4. **Late data 处理**：WindowAggregationOperator 丢弃低于当前 watermark 的迟到元素
5. **端到端验证**：`env.addSource() → keyBy → window → aggregate → sink` + checkpoint 恢复完整跑通
6. **WatermarkInterval 可配置**：从 StreamExecutionEnvironment 传递到 TimestampsAndWatermarksOperator

## Non-Goals

- 不实现 WindowOperator 的 timer snapshot（WindowAggregationOperator 是标准 API 使用的算子，优先修复）
- 不实现 WindowOperatorFactory 接口（WindowedStreamImpl 已通过 WindowAggregationOperator 正常工作）
- 不实现 Evictor（设计文档标注为可选，当前 EvictingWindowOperator 是死代码）
- 不修复 CepOperator 的状态对接（component-roadmap 阶段 4）
- 不修复 fraud-example（component-roadmap 阶段 5）
- 不实现 State Segment 模型（Plan 45 的分布式优化）
- 不实现 metrics/observability

## Execution Plan

### Phase 1 - WindowAggregationOperator snapshot/restore

Status: completed
Targets: `nop-stream-core` (WindowAggregationOperator), 新增 WindowAggregationState 数据类

- Item Types: `Fix`, `Proof`

**目标**：WindowAggregationOperator 的所有 transient 状态（windowState、eventTimeTimers、processingTimeTimers、triggerState、windowTimerLookup、processingTimeTimerLookup、currentWatermark）在 checkpoint 时完整快照，恢复后正确重建。

**序列化约束**（解决泛型 K/W 和 SimpleAccumulator<?> 的类型擦除问题）：
- WindowAggregationState 必须携带 `keyClassName`（K 的具体类名）和 `windowClassName`（W 的具体类名），反序列化时通过 `Class.forName()` 恢复类型
- WindowKey<K, W> 序列化为 `{key: JSON_value, window: JSON_value}`，其中 key 和 window 使用上述类型信息反序列化
- triggerState 中的 SimpleAccumulator<?> 序列化时记录 `accumulatorClassName`，反序列化时通过反射重建实例并恢复值
- windowState 中的 ACC（累加器）如果是 SimpleAccumulator 类型，同样记录类名；如果是普通值，直接 JSON 序列化
- WindowAggregationState 包含 `version` 字段（初始值 1），为后续格式演进预留

**工作项**：

- [x] 新增 `WindowAggregationState` 可序列化数据类：包含 version（int）、keyClassName（String）、windowClassName（String）、windowState（序列化后的 Map）、eventTimeTimers（序列化后的 Map）、processingTimeTimers（序列化后的 Map）、triggerState（序列化后的 Map，含 accumulatorClassName）、currentWatermark（long）
- [x] 修改 `WindowAggregationOperator.snapshotState(StateSnapshotContext)`（覆写 AbstractStreamOperator 的同名方法）：**不调用 super.snapshotState()**（WindowAggregationOperator 无 keyedStateBackend）。收集所有状态到 WindowAggregationState，序列化后作为 operator state 存入 `OperatorSnapshotResult.putOperatorState("window-aggregation-state", snapshot)`（使用 operatorStates 而非 keyedStates）
- [x] 修改 `WindowAggregationOperator.restoreState(OperatorSnapshotResult)`（覆写 AbstractStreamOperator 的同名方法）：**不调用 super.restoreState()**。从 `OperatorSnapshotResult.getOperatorState("window-aggregation-state")` 提取条目，反序列化 WindowAggregationState，使用 keyClassName/windowClassName 恢复类型信息，重建所有 TreeMap/Map 容器
- [x] 修改 `open()` 方法：如果 restoreState 已恢复状态，使用恢复的状态；否则创建空容器
- [x] 测试：单窗口聚合 → checkpoint → 模拟恢复 → 继续聚合 → 验证结果包含恢复前后的所有数据
- [x] 测试：多 key 多窗口 → checkpoint → 恢复 → 验证每个 key 的每个窗口状态正确
- [x] 测试：timer 恢复正确性——注册事件时间 timer → checkpoint → 恢复 → watermark 推进 → timer 正确触发
- [x] 测试：自定义 key 类型（非 String/Integer）的序列化 round-trip
- [x] 测试：triggerState 中 SimpleAccumulator 的序列化 round-trip
- [x] 测试：恢复后 watermark 远超 timer 时间戳时，所有过期 timer 正确触发

Exit Criteria:

- [x] WindowAggregationOperator 覆写 snapshotState() 和 restoreState()，**不调用 super**
- [x] WindowAggregationState 携带 keyClassName、windowClassName、version
- [x] triggerState 的 SimpleAccumulator 序列化记录具体类名
- [x] 所有 transient 状态在 snapshot/restore 后完整重建
- [x] **端到端验证**：source → keyBy → window(3 elements) → aggregate → sink，中途 checkpoint → 恢复 → 继续处理 → 结果包含恢复前后所有聚合值
- [x] **端到端验证**：事件时间窗口，watermark 推进过程中 checkpoint → 恢复 → timer 正确触发窗口计算
- [x] **接线验证**：processBarrier() → snapshotState(StateSnapshotContext) → 返回包含 window-aggregation-state 的 OperatorSnapshotResult；restoreState(OperatorSnapshotResult) → 读取 operatorState 条目并重建状态
- [x] **无静默跳过**：恢复时状态为空或格式不匹配或版本不兼容时抛出异常
- [x] 现有 WindowAggregationOperator 测试通过
- [x] 相关 `ai-dev/design/nop-stream/window-design.md` 已更新（标注 WindowAggregationOperator 支持 checkpoint）
- [x] `ai-dev/logs/` 对应日期条目已更新

---

### Phase 2 - Late data 处理与 purgeWindow 清理

Status: completed
Targets: `nop-stream-core` (WindowAggregationOperator)

- Item Types: `Fix`, `Proof`

**目标**：WindowAggregationOperator 丢弃低于当前 watermark 的迟到元素（避免内存泄漏），清理 purgeWindow 死代码。

**Late data 安全约束**：检查必须只在元素有有效事件时间戳时执行。`StreamRecord.getTimestamp()` 在无时间戳时返回 `Long.MIN_VALUE`。检查条件必须为 `element.hasTimestamp() && element.getTimestamp() < currentWatermark`，避免在 processing time 模式下误杀所有元素。

**工作项**：

- [x] 修改 `processElement()`：在 `windowAssigner.assignWindows()` 之前检查：`if (element.hasTimestamp() && element.getTimestamp() < currentWatermark)` 则丢弃（不分配到窗口）
- [x] 清理 `purgeWindow()` 中第二次 `windowTimerLookup.remove(wk)` 的死代码
- [x] 修复 `processWatermark()` 触发定时器后 `windowTimerLookup.remove(wk)` 一次删除整个 key 的问题（改为只删除当前 timestamp，保留同一 WindowKey 的其他 timer）
- [x] 修复 `advanceProcessingTime()` 触发定时器后 `processingTimeTimerLookup.remove(wk)` 一次删除整个 key 的问题（与 processWatermark 修复对称，改为只删除当前 timestamp）
- [x] 测试：发送有事件时间戳且 timestamp < currentWatermark 的元素 → 验证被丢弃
- [x] 测试：watermark 推进后发送迟到的有事件时间戳元素 → 验证不创建新窗口
- [x] 测试：无时间戳元素（processing time 模式）在 watermark 推进后仍然正常处理
- [x] 测试：正常有事件时间戳的元素在 late data 丢弃后仍能正确处理

Exit Criteria:

- [x] 低于当前 watermark 且有事件时间戳的元素被丢弃（不分配到窗口，不创建空窗口）
- [x] 无事件时间戳的元素（processing time 模式）不被丢弃
- [x] purgeWindow 死代码已清理
- [x] windowTimerLookup 在多 timer 场景下保持一致性
- [x] processingTimeTimerLookup 在多 timer 场景下保持一致性（advanceProcessingTime 修复后）
- [x] **端到端验证**：source 发送有序数据 → watermark 推进 → 发送迟到有事件时间戳元素 → 迟到元素不影响输出
- [x] **端到端验证**：无时间戳的 processing time 管线（fromElements → keyBy → WindowAggregationOperator(processing time 窗口) → sink）在无 watermark 推进时正常处理所有元素
- [x] **无静默跳过**：late data 丢弃时记录 DEBUG 日志（不静默忽略）
- [x] 现有测试通过
- [x] No owner-doc update required（late data 处理是设计文档已定义的行为）
- [x] `ai-dev/logs/` 对应日期条目已更新

---

### Phase 3 - WindowOperator 核心正确性修复

Status: completed
Targets: `nop-stream-runtime` (WindowOperator)

- Item Types: `Fix`, `Proof`

**目标**：修复 WindowOperator 的三个高严重性 bug，使其作为高级用户通过 transform() 手动使用时也能正确工作。

**工作项**：

- [x] 修复 `addWindowElement()` 累加器腐蚀：第一个元素应通过 `createAccumulator()` 创建初始累加器，然后 `add()`，而非直接强转 `(ACC)value`
- [x] 修复 `getSimpleAccumulator()`：实现从 triggerState 中获取或创建 SimpleAccumulator，而非抛 UnsupportedOperationException
- [x] 修复 `mergeWindowContents()` 吞 ClassCastException：移除 try-catch，让类型错误快速失败；修复累加器合并逻辑（检查 source 和 target 的类型，两个都是 SimpleAccumulator 时调用合并方法）
- [x] 修复 `mergeWindowContents()` 累加器合并逻辑：当 source 和 target 都是 SimpleAccumulator 时，应先获取 source 的当前值再 add 到 target
- [x] 测试：单 key AggregateFunction（IN≠ACC 类型）聚合正确性——首元素不丢失、连续累加正确
- [x] 测试：getSimpleAccumulator 在 Trigger.onElement 中可用（CountTrigger 正确计数）
- [x] 测试：合并窗口（Session Window）累加器合并正确性——两个窗口的累加器正确合并，不丢数据
- [x] 测试：mergeWindowContents 的类型不兼容场景抛出异常（不静默丢弃）

Exit Criteria:

- [x] `addWindowElement()` 第一个元素通过 createAccumulator → add 路径
- [x] `getSimpleAccumulator()` 返回有效累加器（不抛异常）
- [x] `mergeWindowContents()` 不吞 ClassCastException，类型不兼容时快速失败
- [x] `mergeWindowContents()` 正确合并两个 SimpleAccumulator 窗口
- [x] **端到端验证**：source → keyBy → transform(WindowOperator) → sink，AggregateFunction(IN≠ACC) 聚合结果正确
- [x] **端到端验证**：CountTrigger 通过 getSimpleAccumulator 正确计数并触发
- [x] **接线验证**：CountTrigger.onElement() 调用 getSimpleAccumulator() 获取的累加器可正确 add 和 get
- [x] **无静默跳过**：类型不兼容时 mergeWindowContents 抛出异常
- [x] 现有 WindowOperator 测试通过
- [x] 相关 `ai-dev/design/nop-stream/window-design.md` 已更新（标注 WindowOperator 已修复）
- [x] `ai-dev/logs/` 对应日期条目已更新

---

### Phase 4 - WatermarkInterval 可配置

Status: completed
Targets: `nop-stream-core` (StreamExecutionEnvironment, TimestampsAndWatermarksOperator, TimestampsAndWatermarksTransformation, DataStreamImpl)

- Item Types: `Fix`, `Proof`

**目标**：watermarkInterval 从 StreamExecutionEnvironment 配置传递到 TimestampsAndWatermarksOperator，而非硬编码 200ms。同时修复批量数据场景下的 watermark 发射逻辑。

**watermarkInterval=0 的语义**：每个 processElement 调用都触发 periodic emit。但因为 `BoundedOutOfOrdernessWatermarks.onPeriodicEmit()` 输出的 watermark 值取决于 maxTimestamp，同一 ms 内的多条数据会产生相同的 watermark 值，下游的 `processWatermark()` 的 `newWatermark <= currentWatermark` 检查会跳过重复 watermark。因此 watermarkInterval=0 + 同 ms 数据 = 下游只收到一个 watermark。窗口触发仍然依赖 finish() 时的 MAX_WATERMARK 作为兜底。

**工作项**：

- [x] 修改 `StreamExecutionEnvironment`：新增 `watermarkInterval` 字段（默认 200ms）、getter/setter 方法
- [x] 修改 `DataStreamImpl.assignTimestampsAndWatermarks()`：从 `environment.getWatermarkInterval()` 获取 interval，传递给 `TimestampsAndWatermarksTransformation`
- [x] 修改 `TimestampsAndWatermarksTransformation`：新增 `watermarkInterval` 字段
- [x] 修改 `StreamGraphGenerator`：将 transformation 的 watermarkInterval 传递给 TimestampsAndWatermarksOperator 构造函数。**注意**：StreamGraphGenerator 当前通过 `new TimestampsAndWatermarksOperator<>(strategy)` 直接创建实例再包装到 `SimpleStreamOperatorFactory`。`SimpleStreamOperatorFactory.createStreamOperator()` 使用 Java 序列化深拷贝。`watermarkInterval` 是基本类型 long，可被序列化保留。但如果改为更复杂的配置对象，需要确保序列化兼容性
- [x] 修改 `TimestampsAndWatermarksOperator`：构造函数接受 watermarkInterval 参数（而非硬编码），并实现 `Serializable` 以支持 SimpleStreamOperatorFactory 深拷贝
- [x] 修复 watermark 发射逻辑：在 `processElement` 中同时检查 wall-clock time **和元素计数**。如果自上次 emit 后已处理 N 个元素（N = watermarkInterval == 0 ? 1 : Integer.MAX_VALUE，即 watermarkInterval=0 时每个元素都触发），也触发周期性 emit。解决批量数据 wall-clock 间隔过短的问题
- [x] 测试：配置 watermarkInterval=50ms → 验证 watermark 每 50ms 发射一次
- [x] 测试：批量数据（所有数据在同一 ms 内到达）→ 配置 watermarkInterval=0 → 验证 watermark 仍然推进（基于元素计数触发），窗口在 finish() 时通过 MAX_WATERMARK 正确触发
- [x] 测试：watermarkInterval < 0 时抛出 IllegalArgumentException

Exit Criteria:

- [x] StreamExecutionEnvironment 支持配置 watermarkInterval
- [x] watermarkInterval 传递链完整：Environment → DataStreamImpl → Transformation → StreamGraph → TimestampsAndWatermarksOperator
- [x] 批量数据场景下 watermark 正确推进（基于元素计数触发）
- [x] **端到端验证**：env 配置 watermarkInterval=0 → source 发送 100 条数据（同一 ms 内）→ keyBy → window(TumblingEventTimeWindows) → aggregate → sink → finish() 时 MAX_WATERMARK 触发所有窗口正确计算
- [x] **端到端验证**：env 配置 watermarkInterval=50ms → source 持续发送 1s → watermark 每 50ms 推进 → 事件时间窗口在运行中正确触发
- [x] **接线验证**：StreamExecutionEnvironment.watermarkInterval → DataStreamImpl.assignTimestampsAndWatermarks → TimestampsAndWatermarksTransformation → StreamGraphGenerator → TimestampsAndWatermarksOperator 传递链完整
- [x] **接线验证**：TimestampsAndWatermarksOperator 的 watermarkInterval 在 SimpleStreamOperatorFactory 深拷贝后保留（Serializable 兼容）
- [x] **无静默跳过**：watermarkInterval < 0 时抛出 IllegalArgumentException
- [x] 现有 TimestampsAndWatermarksOperator 测试通过
- [x] 相关 `ai-dev/design/nop-stream/core-design.md` 已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

---

### Phase 5 - MemoryKeyedStateBackend 修复

Status: completed
Targets: `nop-stream-core` (MemoryKeyedStateBackend, AbstractStreamOperator)

- Item Types: `Fix`, `Proof`

**目标**：修复 MemoryKeyedStateBackend 的 API 缺陷和 AbstractStreamOperator 的恢复 bug。

**工作项**：

- [x] 修复 `MemoryKeyedStateBackend.getListState()`：实现 InternalListState，而非抛异常
- [x] 修复 `AbstractStreamOperator.restoreState()` 中 `break` 只恢复第一个 keyed state entry 的问题
- [x] 测试：getListState → add → get → 值正确
- [x] 测试：多个 keyed state entry 的恢复全部完成（不只是第一个）
- [x] 测试：getListState 在实际算子场景中可用（如自定义算子使用 ListState 存储窗口元数据）

Exit Criteria:

- [x] `getListState()` 返回有效的 InternalListState
- [x] `restoreState()` 恢复所有 keyed state entry
- [x] **端到端验证**：使用 getListState 的自定义算子在 checkpoint 恢复后正确工作
- [x] **无静默跳过**：getListState 不再抛 UnsupportedOperationException
- [x] 现有测试通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

---

### Phase 6 - 端到端验证：窗口聚合 + checkpoint 恢复 + watermark

Status: completed
Targets: 全模块

- Item Types: `Proof`

**目标**：验证 Phase 1-5 的修复后，从用户入口到最终输出的完整路径正确，包含 checkpoint 恢复场景。验证不变量 #9（timer state 进入 checkpoint）。

**工作项**：

- [x] 编写端到端测试：`env.addSource(有序数据).assignTimestampsAndWatermarks(BoundedOutOfOrderness(100ms)).keyBy().window(TumblingEventTimeWindows(500ms)).aggregate(SumAggregate).sink()`，完整跑通，验证每个窗口的聚合值
- [x] 编写 checkpoint 恢复测试：上述管线中途 checkpoint → 清空状态 → 恢复 → 继续处理 → 结果与不中断执行一致
- [x] 编写 timer state 验证测试：checkpoint 时注册了 timer → 恢复后 watermark 推进 → timer 正确触发窗口计算
- [x] 编写 timer 跨越恢复测试：恢复后第一个 watermark 跳过多个 timer 时间戳 → 所有过期 timer 都正确触发（无遗漏）
- [x] 编写 late data 测试：watermark 推进后发送迟到元素 → 不影响输出
- [x] 编写 WindowOperator 正确性回归测试：使用 transform(WindowOperator) + AggregateFunction(IN≠ACC) + CountTrigger
- [x] 完整回归测试：`./mvnw test -pl nop-stream -am`

Exit Criteria:

- [x] 不变量 #9：timer state 进入 checkpoint，恢复后 timer 正确触发（timer state 验证测试通过）
- [x] **端到端验证**：source → timestamp/watermark → keyBy → window → aggregate → sink 完整路径跑通（使用 WindowAggregationOperator）
- [x] **端到端验证**：checkpoint 恢复后结果与不中断执行一致
- [x] **端到端验证**：WindowOperator + AggregateFunction(IN≠ACC) + CountTrigger 正确
- [x] **端到端验证**：late data 不影响正常输出
- [x] **接线验证**：Anti-Hollow 清单：
  - [x] WindowAggregationOperator.snapshotState() 返回非空 OperatorSnapshotResult
  - [x] WindowAggregationOperator.restoreState() 读取状态并重建所有 TreeMap/Map
  - [x] Timer 在 checkpoint 前注册、恢复后触发
  - [x] Late data 在 processElement 中被丢弃
  - [x] WindowAggregationOperator 的累加器在 snapshot/restore 后正确
  - [x] watermarkInterval 从 StreamExecutionEnvironment 传递到 TimestampsAndWatermarksOperator
- [x] **无静默跳过**：无空方法体、无吞异常、无 no-op
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] `ai-dev/logs/` 对应日期条目已更新

---

## Phase Status

| Phase | 名称 | Depends on | Status |
|-------|------|------------|--------|
| 1 | WindowAggregationOperator snapshot/restore | 无 | completed |
| 2 | Late data 处理与 purgeWindow 清理 | 无 | completed |
| 3 | WindowOperator 核心正确性修复 | 无 | completed |
| 4 | WatermarkInterval 可配置 | 无 | completed |
| 5 | MemoryKeyedStateBackend 修复 | 无 | completed |
| 6 | 端到端验证 | Phase 1-5 | completed |

**并行可能性**：Phase 1-5 互相独立可并行。Phase 6 依赖全部。注意：Phase 1 和 Phase 2 修改同一文件 `WindowAggregationOperator.java`（不同方法），并行执行后合并可能需手动解决冲突。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 6 个 Phase 的 Exit Criteria 全部满足
- [x] 不变量 #9（timer state 进入 checkpoint）有端到端测试验证
- [x] 窗口聚合端到端测试通过（source → keyBy → window → aggregate → sink）
- [x] 窗口聚合 checkpoint 恢复测试通过（恢复后结果与不中断执行一致）
- [x] WindowOperator 三个核心 bug 修复（累加器腐蚀、getSimpleAccumulator、mergeWindowContents）
- [x] WatermarkInterval 从 StreamExecutionEnvironment 可配置
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响的 owner docs 已同步：`window-design.md`、`core-design.md`
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证 WindowAggregationOperator 的 snapshot/restore 路径在运行时被调用
- [x] `./mvnw compile -pl nop-stream -am`
- [x] `./mvnw test -pl nop-stream -am`
- [x] `ai-dev/logs/` 记录每个 Phase 的执行日志

## Deferred But Adjudicated

### WindowOperator timer snapshot

- Classification: `optimization candidate`
- Why Not Blocking Closure: WindowAggregationOperator 是标准 API 使用的窗口算子，已实现 timer snapshot（Phase 1）。WindowOperator 是高级用户通过 transform() 手动使用的算子，其 timer snapshot 优先级低于标准 API 路径
- Successor Required: `yes`
- Successor Path: 可在 Plan 44/45 的分布式场景中补充，或在独立 PR 中实现

### WindowOperatorFactory 接口

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: WindowedStreamImpl 的 apply/aggregate/reduce 已经通过 WindowAggregationOperator 正常工作，不需要 Factory 接口
- Successor Required: `no`

### Evictor（EvictingWindowOperator）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计文档标注为可选功能，EvictingWindowOperator 当前是死代码
- Successor Required: `no`

### CepOperator 对接 IKeyedStateBackend

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: CepOperator 对接标准状态后端是 component-roadmap 阶段 4，不在本计划范围
- Successor Required: `yes`
- Successor Path: 待 Plan 44 完成后，CepOperator 接入标准状态后端

### State Segment 模型

- Classification: `optimization candidate`
- Why Not Blocking Closure: 整块 Map 快照满足单进程和嵌入式分布式场景的正确性。按 operatorId + stateShard 分段的优化是 Plan 45 的分布式场景需求
- Successor Required: `yes`
- Successor Path: Plan 45 或后续计划

## Non-Blocking Follow-ups

- Metrics/observability（11 个核心指标，需新计划）
- Fraud-example 重写（component-roadmap 阶段 5）
- BoundedOutOfOrdernessWatermarks 的内部状态持久化（当前从 Long.MIN_VALUE 重新开始，恢复后 watermark 可能回退）
- SimpleStreamOperatorFactory 改为构造器创建而非 Java 序列化深拷贝
- StreamGraphGenerator 的 PartitionOperatorFactory.createStreamOperator() 返回 null 修复

## Closure

Status Note: All 6 phases completed. Independent closure audit verified all exit criteria met, anti-hollow checks passed, 736+ tests pass.

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (GLM-5.1, separate task)
- Evidence: Full source code audit confirmed all changes exist and are substantive. Test suite passes.

Follow-up:

- Metrics/observability（11 个核心指标，需新计划）
- Fraud-example 重写（component-roadmap 阶段 5）
- BoundedOutOfOrdernessWatermarks 的内部状态持久化
- SimpleStreamOperatorFactory 改为构造器创建而非 Java 序列化深拷贝
- StreamGraphGenerator 的 PartitionOperatorFactory.createStreamOperator() 返回 null 修复
