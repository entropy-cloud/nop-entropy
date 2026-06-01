# 97 Window Operator Unification

> Plan Status: completed
> Last Reviewed: 2026-06-01
> Source: `ai-dev/design/nop-stream/window-design.md`（最优设计）、`ai-dev/plans/96-stale-plan-disposition-and-successor-adjudication.md`（Window 算子裁定）
> Related: `ai-dev/design/nop-stream/window-design.md`

## Purpose

将 nop-stream 的两个窗口算子（core 模块的 `WindowAggregationOperator` 和 runtime 模块的 `WindowOperator`）统一为单一 `WindowOperator`，按照 `window-design.md` 描述的最优架构实现。

## Current Baseline

- **core 模块**：`WindowAggregationOperator`（834 行）—— 自管 `MapState<String, ACC>` 状态，自管 `TreeMap` 定时器，支持 `AggregateFunction`/`ReduceFunction`/`WindowFunction`，增量聚合模式。`MemoryInternalAppendingState` 使用共享 `SimpleAccumulator` 实例 + `resetLocal()/add()/getLocalValue()` 模式做累积，由于 accumulator 实例被所有 (key, namespace) 共享，连续对不同 namespace 执行 `add()` 时 `resetLocal()` 可能清除前一个 namespace 的中间状态
- **runtime 模块**：`WindowOperator`（1099 行）—— 使用平台 `IInternalStateBackend` + `InternalTimerService`，支持全量 `WindowFunction`、`MergingWindowAssigner` 路径。状态管理使用 `MapState<String, ACC>` + 手动字符串 namespace（`windowNamespace(W)` 方法将 Window 转成 String 如 `"TW:100,200"`），包含 `addWindowElement`/`getWindowContents`/`setWindowContents`/`clearWindowContents`/`mergeWindowContents` 五个方法依赖 `windowContentsState`（MapState 字段）。`EvictingWindowOperator` 不存在
- **API 层**：`WindowedStream` 接口定义了 `aggregate`/`reduce`/`apply`/`trigger`/`evictor` 方法（无 `process()` 方法）。`WindowedStreamImpl`（core 模块）全部委托给 `WindowAggregationOperator`。`WindowedStreamImpl` 保存了 `evictor` 字段但从未传递给算子
- **缺失接口**：`ProcessWindowFunction` 接口在整个代码库中不存在（只有注释中提到）
- **函数适配层**：runtime 模块有 `InternalWindowFunction` 接口（带 `process` 和 `clear` 方法及 `InternalWindowContext` 内部接口），但没有具体适配器实现（`InternalSingleValueWindowFunction` 等不存在）
- **状态接口**：`IInternalStateBackend` 的 `getInternalAppendingState` 只接受 `ReducingStateDescriptor`，不支持 `AggregatingStateDescriptor`
- **模块依赖**：runtime 依赖 core（`nop-stream-runtime → nop-stream-core`），core 不能依赖 runtime。`WindowedStreamImpl` 在 core 中无法直接引用 runtime 中的类
- **Window namespace**：当前 `WindowOperator.windowNamespace()` 将 Window 转成 String。新方案直接使用 Window 对象作为 namespace（通过 `TypedNamespaceAndKey` 存储，依赖 Window 的 equals/hashCode），需确保 `TypedNamespaceAndKey` 正确处理 Window 对象的 equals/hashCode（TimeWindow/GlobalWindow 均已正确实现）
- **合并窗口状态迁移**：当前 `mergeWindowContents()` 区分三种情况：target/source 都是 SimpleAccumulator → merge()；否则 last-write-wins 或抛异常。新算子使用 `InternalAppendingState` 后需要从 source namespace 读取 accumulator，在新 namespace 下合并。Builder 需将 merge 函数（`AggregateFunction.merge` 或 `ReduceFunction.reduce`）传入 WindowOperator
- **WindowContext.windowState()**：当前依赖 `windowNamespace()` 方法设置 namespace 后返回 `IKeyedStateBackend`。删除 `windowNamespace()` 后此方法需要重新实现。本计划中改为抛出 `UnsupportedOperationException`（最小 Context 实现）
- **测试**：core 模块有 `TestWindowAggregationOperatorSnapshotRestore`、`TestWindowAggregationFunction`、`TestWindowAggregationOperatorLateData`、`TestWindowAggregationOperatorProcessingTimeTimer`、`TestWindowedStreamAggregation`、`TestWindowAggregationE2E`、`TestEventTimeWindowE2E`、`TestSessionWindowE2E`、`TestWindowEndToEnd`、`TestProcessingTimeWindowIntegration`、`TestSessionWindowIntegration`、`TestWindowTranslation`、`TestWindowingModel`、`TestWindowOperatorWatermarkReception`。runtime 模块缺少独立测试

## Goals

- 统一为单一 `WindowOperator`（runtime 模块），支持所有窗口类型和函数类型
- 所有窗口状态通过 `IInternalStateBackend` 的 namespace 机制管理
- 所有定时器通过 `InternalTimerService` 管理
- 修复所有已知窗口 bug（共享 SimpleAccumulator、MapState 覆盖语义等）
- `WindowedStream` API 全部委托给统一算子（通过工厂模式桥接 core/runtime 模块边界）
- 保留所有现有测试场景的覆盖

## Non-Goals

- PaneState 完整集成（ACCUMULATING/RETRACTING 模式）—— 属于后续迭代
- Watermark 持续推进机制改进 —— 属于 `time-model-design.md` 范畴
- Session Window Assigner 实现（`SessionEventTimeWindows`）—— 合并窗口路径在本计划中通过测试验证，具体 Assigner 属于后续
- 新增 Evictor 实现 —— 保留现有 `CountEvictor`、`TimeEvictor`、`DeltaEvictor`
- 分布式场景验证 —— 本计划聚焦本地模式
- `ProcessWindowFunction` 的完整 Context 支持（windowState/globalState）—— 本计划只实现最小 Context（processingTime/watermark）

## Scope

### In Scope

- 定义 `ProcessWindowFunction` 接口（core 模块）
- 扩展 `IInternalStateBackend` 支持 `AggregatingStateDescriptor`
- 在 runtime 模块重构 `WindowOperator`（增量修改现有实现）
- 实现 `InternalWindowFunction` 适配器族
- 实现 `WindowOperatorBuilder`
- 定义 `IWindowOperatorFactory` 接口（core），实现（runtime），桥接 core/runtime 模块边界
- 修改 `WindowedStreamImpl` 通过工厂委托给新算子
- 新增 `WindowedStream.process()` 方法
- 传递 `evictor` 到算子
- 废弃 `WindowAggregationOperator`（core 模块）
- 迁移现有测试到新算子
- 端到端验证：`env.addSource() → keyBy → window → aggregate/reduce/apply → collect`

### Out Of Scope

- PaneState 集成（见 Non-Goals）
- Watermark 持续推进
- Session Window Assigner
- 新 Evictor 类型
- 分布式 exactly-once 验证

## Execution Plan

> Phase 1 和 Phase 2 之间没有数据依赖，可以并行执行。

### Phase 1 - 扩展状态后端 + 定义缺失接口

Status: completed
Targets: `nop-stream/nop-stream-core/`（`IInternalStateBackend`、`MemoryKeyedStateBackend`、`ProcessWindowFunction` 接口）

- Item Types: `Fix`

- [x] 在 `IInternalStateBackend` 中增加 `getInternalAppendingState(AggregatingStateDescriptor)` 重载
- [x] 在 `MemoryKeyedStateBackend` 中实现 `MemoryInternalAggregatingState`：组合 `InternalAppendingState` 的 namespace 接口 + `AggregateFunction` 累积模式（参照现有 `MemoryAggregatingState` 的累积逻辑 + `MemoryInternalAppendingState` 的 namespace 管理）
- [x] 在 `MemoryStateSerDe` 中新增 `MemoryInternalAggregatingState` 的 snapshot/restore 分支（`snapshotState()` 和 `restoreState()` 的 instanceof 链）。在 `MemoryKeyedStateBackend.rebindStateBackends()` 中新增对应的 instanceof 分支
- [x] 为新增状态实现编写单元测试：创建 → add 多次 → get → 验证聚合结果正确
- [x] 快照/恢复 round-trip 测试：创建状态 → add 多次 → snapshot → restore → 验证聚合结果与快照前一致
- [x] 在 `io.nop.stream.core.common.functions` 包下定义 `ProcessWindowFunction<IN, OUT, KEY, W>` 接口，包含 `process(KEY key, W window, Iterable<IN> input, Context context, Collector<OUT> out)` 方法和 `Context` 内部接口（最小实现：`currentProcessingTime()`、`currentWatermark()`）
- [x] 在 `WindowedStream` 接口中增加 `<R> SingleOutputStreamOperator<R> process(ProcessWindowFunction<T, R, K, W> function)` 方法
- [x] 在 core 模块定义 `IWindowOperatorFactory` 接口：接受窗口配置参数，返回 `OneInputStreamOperator` 实例。用于桥接 core（`WindowedStreamImpl`）和 runtime（统一算子）的模块边界

Exit Criteria:

- [x] `IInternalStateBackend` 有 `getInternalAppendingState(AggregatingStateDescriptor)` 方法
- [x] `MemoryKeyedStateBackend` 返回的 `InternalAppendingState` 正确执行 `AggregateFunction.add` 累积
- [x] Namespace 隔离正确：不同 Window 对象作为 namespace 时 accumulator 互不干扰（验证 `TypedNamespaceAndKey` 对 Window 对象的 equals/hashCode 正确处理）
- [x] 快照/恢复 round-trip：`MemoryInternalAggregatingState` 的 snapshot → restore 路径正确（`MemoryStateSerDe` 分发链覆盖新类型）
- [x] `rebindStateBackends()` 正确处理 `MemoryInternalAggregatingState`
- [x] `ProcessWindowFunction` 接口存在且有 `process` 方法 + `Context` 内部接口
- [x] `WindowedStream` 接口有 `process()` 方法
- [x] `IWindowOperatorFactory` 接口存在
- [x] 新增测试通过
- [x] 现有状态后端测试全部通过（`./mvnw test -pl nop-stream/nop-stream-core`）
- [x] **接线验证**：新状态接口被 `MemoryKeyedStateBackend` 在运行时正确创建和返回
- [x] No owner-doc update required（`state-management-design.md` 在 Phase 6 同步更新）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 实现 InternalWindowFunction 适配器族

Status: completed
Targets: `nop-stream/nop-stream-runtime/`（`io.nop.stream.runtime.operators.windowing.functions` 包下新增适配器）

- Item Types: `Fix`

- [x] 实现 `InternalSingleValueWindowFunction<IN, OUT, KEY, W>`：接受 `BiFunction<ACC, ACC, OUT>` resultExtractor 参数（由 Builder 传入）。对 AggregateFunction 传入 `(acc, ignored) -> aggFn.getResult(acc)`；对 ReduceFunction 传入 `(acc, ignored) -> acc`。`process()` 中 `InternalWindowContext` 参数忽略（增量函数不需要 context）
- [x] 实现 `InternalSingleValueProcessWindowFunction<IN, OUT, KEY, W>`：包装增量函数 + `ProcessWindowFunction`，将 accumulator 传给 PWF 的 `process()` 方法。`InternalWindowContext` 透传给 PWF 的 Context
- [x] 实现 `InternalIterableWindowFunction<T, OUT, KEY, W>`：包装 `WindowFunction`，传入 `Iterable<T>`。`InternalWindowContext` 按 `WindowFunction.apply` 签名决定是否透传
- [x] 实现 `InternalIterableProcessWindowFunction<T, OUT, KEY, W>`：包装 `ProcessWindowFunction`，传入 `Iterable<T>`。`InternalWindowContext` 透传给 PWF
- [x] 为每个适配器编写单元测试：验证正确的底层函数被调用、正确的参数被传入、`InternalWindowContext` 被正确处理

Exit Criteria:

- [x] 4 个适配器类存在于 `io.nop.stream.runtime.operators.windowing.functions` 包下
- [x] 每个适配器有对应单元测试验证行为正确
- [x] **无静默跳过**：适配器中无空方法体或 placeholder 返回值
- [x] 现有测试通过（`./mvnw test -pl nop-stream/nop-stream-runtime`）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 实现 WindowOperatorBuilder + 重构 WindowOperator（原子单元）

Status: completed
Targets: `nop-stream/nop-stream-runtime/`（`io.nop.stream.runtime.operators.windowing` 包）

- Item Types: `Fix`

> **Phase 3 和原 Phase 4 合并为一个原子单元**：Builder 构造 WindowOperator 实例，WindowOperator 的构造函数签名变化直接影响 Builder 的调用。两者必须同步设计和实现。

**Part A — WindowOperatorBuilder**

- [x] 实现 `WindowOperatorBuilder`：持有窗口配置（WindowAssigner、Trigger、Evictor、allowedLateness、lateDataOutputTag）
- [x] 无 Evictor 构建：`aggregate` → `AggregatingStateDescriptor` + `InternalSingleValueWindowFunction`（或 ProcessWindowFunction 变体）；`reduce` → `ReducingStateDescriptor` + 对应适配器；`apply` → `ListStateDescriptor` + `InternalIterableWindowFunction`；`process` → `ListStateDescriptor` + `InternalIterableProcessWindowFunction`
- [x] 有 Evictor 构建：所有方法统一使用 `ListStateDescriptor`
- [x] Builder 将 merge 函数传递给 WindowOperator：对 AggregateFunction 通过 `AggregatingStateDescriptor.getAggregateFunction()` 获取 `merge(ACC, ACC)` 方法；对 ReduceFunction 通过 `ReduceFunction.reduce(T, T)` 获取归约逻辑。WindowOperator 保存为 `BiFunction<ACC, ACC, ACC>` 字段用于合并窗口状态迁移
- [x] 新 `WindowOperator` 构造函数不要求 `TypeSerializer` 参数（内存模式不需要序列化，namespace 使用 Window 对象而非序列化后的 byte[]）

**Part B — WindowOperator 重构**

**策略**：增量修改现有 `WindowOperator`，保留已正确的逻辑（`InternalTimerService` 集成、`MergingWindowSet` 合并窗口逻辑、`onEventTime`/`onProcessingTime` 回调、迟到数据处理、cleanup timer），只替换状态管理层和函数调用层。

- [x] 将 `windowContentsState`（MapState）及其全部辅助方法（`addWindowElement`、`getWindowContents`、`setWindowContents`、`clearWindowContents`）替换为 `InternalAppendingState`/`InternalListState` 的 namespace-based 操作。删除 `windowNamespace()` 方法（直接使用 Window 对象作为 namespace）。`WindowOperator` 持有两个可选字段（`InternalAppendingState appendingState` 和 `InternalListState listState`），由构造时传入的描述符决定哪个非 null，`processElement` 和 `emitWindowContents` 通过 `instanceof` 判断当前路径
- [x] `WindowContext.windowState()` 改为抛出 `UnsupportedOperationException("windowState() not yet supported in minimal Context implementation")`（标注为最小 Context 实现，Non-Goal 中已声明）
- [x] 集成 `InternalWindowFunction`：`emitWindowContents` 通过 `internalWindowFunction.process()` 调用用户函数，替代当前直接调用 `windowFunction.apply()` 的方式
- [x] 实现 Evictor 路径（§8.7）：`emitWindowContents` 中有 Evictor 时，从 `InternalListState` 读取元素列表 → `evictor.evict()` → 增量函数则在内部聚合后传 ACC 给适配器，全量函数直接传 `Iterable`
- [x] 替换 `mergeWindowContents()` 的实现：合并窗口的状态迁移使用 Builder 传入的 `BiFunction<ACC, ACC, ACC>` merge 函数，从 source namespace 读取 accumulator，执行 merge，写入目标窗口的 namespace。对全量路径（ListState）则迁移元素列表
- [x] 在测试中验证：两个不同 TimeWindow 的状态互不干扰（验证 Window 对象作为 namespace 的 equals/hashCode 正确性）

**Part C — 测试**

- [x] 为 `WindowOperatorBuilder` 编写单元测试：验证不同函数类型 + Evictor 组合产生正确的状态描述符和内部函数
- [x] 直接构造 `WindowOperator`（使用新构造函数）→ 注入数据 → 通过 `InternalWindowFunction` 输出结果的端到端测试
- [x] 合并窗口路径的 merge 函数传递和状态迁移测试

Exit Criteria:

- [x] `WindowOperatorBuilder` 存在于 `io.nop.stream.runtime.operators.windowing` 包，有 6 个构建方法
- [x] 构建器测试覆盖无 Evictor 和有 Evictor 的所有状态描述符选择路径
- [x] `WindowOperator` 使用 `InternalAppendingState` 或 `InternalListState`（无 MapState 字段）
- [x] `WindowOperator` 使用 `InternalTimerService`（保留现有逻辑）
- [x] 无 `windowNamespace()` 方法（Window 对象直接作为 namespace）
- [x] `WindowContext.windowState()` 抛出 `UnsupportedOperationException`（最小 Context 实现）
- [x] 非合并窗口路径：`aggregate`/`reduce`/`apply`/`process` 均有对应测试通过
- [x] 合并窗口路径：`MergingWindowSet` 合并语义有测试通过
- [x] 合并窗口状态迁移正确：merge 函数被正确传递和使用
- [x] 迟到数据处理：late data 被正确丢弃或输出到 side output
- [x] Evictor 路径：有 Evictor 时元素被正确驱逐后再传给函数
- [x] Namespace 隔离测试通过：不同 Window 对象的状态互不干扰
- [x] **端到端验证**：直接构造 `WindowOperator` → 注入数据 → 通过 `InternalWindowFunction` 输出结果的完整路径已验证
- [x] **接线验证**：`WindowOperatorBuilder` 构建的 `WindowOperator` 与直接构造的行为一致
- [x] **无静默跳过**：无空方法体、无 `continue` 跳过应处理的逻辑分支
- [x] 现有 runtime 模块测试通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 迁移 API 和测试，桥接模块边界

Status: completed
Targets: `nop-stream/nop-stream-core/`（`WindowedStreamImpl`）、`nop-stream/nop-stream-runtime/`（`IWindowOperatorFactory` 实现）、core 模块测试迁移、core 模块旧算子废弃

- Item Types: `Fix`

- [x] 在 runtime 模块实现 `IWindowOperatorFactory`：使用 `WindowOperatorBuilder` 根据函数类型和 Evictor 配置创建统一 `WindowOperator`
- [x] 工厂发现机制：在 `StreamComponents` 中新增 `windowOperatorFactory` 字段（类型 `IWindowOperatorFactory`）。runtime 模块在构造 `StreamComponents` 时注册工厂实例。`WindowedStreamImpl` 通过 `components.getWindowOperatorFactory()` 获取工厂。如果工厂不可用（runtime 模块未加载），`WindowedStreamImpl` 的 `aggregate`/`reduce`/`apply`/`process` 方法抛出 `UnsupportedOperationException` 说明需要 nop-stream-runtime 模块
- [x] 修改 `WindowedStreamImpl`：`aggregate`/`reduce`/`apply`/`process` 方法通过 `IWindowOperatorFactory` 创建算子
- [x] 修复 Evictor 传递：`WindowedStreamImpl` 将 `evictor` 字段传递给 `WindowOperatorBuilder`
- [x] 实现 `WindowedStreamImpl.process()` 方法
- [x] 迁移 core 模块的窗口测试到使用新算子路径（通过工厂）：
  - `TestWindowAggregationOperatorSnapshotRestore`、`TestWindowAggregationFunction`、`TestWindowAggregationOperatorLateData`、`TestWindowAggregationOperatorProcessingTimeTimer`、`TestWindowedStreamAggregation`
  - `TestWindowAggregationE2E`、`TestEventTimeWindowE2E`、`TestSessionWindowE2E`、`TestWindowEndToEnd`
  - `TestProcessingTimeWindowIntegration`、`TestSessionWindowIntegration`、`TestWindowTranslation`、`TestWindowingModel`、`TestWindowOperatorWatermarkReception`
- [x] 废弃 `WindowAggregationOperator`：标记 `@Deprecated`，保留文件（避免破坏可能的直接引用）
- [x] 废弃 core 模块中的 `WindowAggregationFunction`、`AggregateAggregationFunction`、`ReduceAggregationFunction`、`ApplyAggregationFunction`、`WindowAggregationState`

Exit Criteria:

- [x] `WindowedStreamImpl.aggregate()` 通过工厂 + 新算子正确执行聚合并输出结果
- [x] `WindowedStreamImpl.reduce()` 通过工厂 + 新算子正确执行归约并输出结果
- [x] `WindowedStreamImpl.apply()` 通过工厂 + 新算子正确执行全量窗口函数并输出结果
- [x] `WindowedStreamImpl.process()` 通过工厂 + 新算子正确执行 ProcessWindowFunction 并输出结果
- [x] Evictor 被正确传递给算子并在运行时生效
- [x] 所有迁移后的测试通过
- [x] **端到端验证**：`env.fromElements() → keyBy() → window(TumblingEventTimeWindows.of(ms)) → aggregate(fn) → collect()` 完整路径已验证
- [x] **接线验证**：`IWindowOperatorFactory` 在 `WindowedStreamImpl` 运行时被调用并返回 runtime 模块的 `WindowOperator`
- [x] 工厂发现测试：验证 `StreamComponents.getWindowOperatorFactory()` 返回 runtime 注册的工厂实例
- [x] `WindowAggregationOperator` 标记 `@Deprecated`
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 通过
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 通过
- [x] No owner-doc update required（`state-management-design.md` 在 Phase 5 同步更新）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 文档同步

Status: completed
Targets: `ai-dev/design/nop-stream/state-management-design.md`、`ai-dev/design/nop-stream/window-design.md`

- Item Types: `Follow-up`

- [x] 更新 `state-management-design.md` §2.1（接口层次）和 §5.1（接口层次）：反映 `IInternalStateBackend` 新增的 `AggregatingStateDescriptor` 路径
- [x] 更新 `window-design.md`：如有实现中发现的设计偏差需记录

Exit Criteria:

- [x] `state-management-design.md` 描述了 `getInternalAppendingState(AggregatingStateDescriptor)` 路径
- [x] 文档内容与 live repo 代码一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 统一 `WindowOperator` 替换了两个旧算子的所有功能
- [x] 所有窗口状态通过 `IInternalStateBackend` namespace 机制管理（无自管 HashMap）
- [x] 所有定时器通过 `InternalTimerService` 管理（无自管 TreeMap）
- [x] 共享 SimpleAccumulator bug 已修复（新算子使用 namespace-isolated `InternalAppendingState`，每个 namespace 独立存储）
- [x] MapState 覆盖语义 bug 已修复（新算子使用 `InternalAppendingState.add()` 增量累积）
- [x] Evictor 被正确传递并在运行时生效
- [x] `ProcessWindowFunction` 接口已定义并可使用
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响的 owner docs 已同步（`state-management-design.md`）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 已验证端到端路径从 `WindowedStream` API → `IWindowOperatorFactory` → 统一 `WindowOperator` → 输出完整连通
- [x] `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` 通过
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### PaneState 完整集成

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: PaneState 模型已定义在 `window-design.md` §13 中，但 ACCUMULATING/RETRACTING 模式的完整集成需要 per-window pane 元数据存储。DISCARDING 模式（默认）不依赖 PaneState。当前所有现有测试场景均使用 DISCARDING 语义
- Successor Required: yes
- Successor Path: 后续 plan（窗口多次触发 + late firing 支持）

### Watermark 持续推进

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前 watermark 仅在 Source 结束时发送 MAX_WATERMARK，事件时间窗口只在数据流结束时触发。这是 watermark 机制的已知限制，不是窗口算子的问题。新算子已正确处理 watermark 推进逻辑，待 watermark 机制改进后即可受益
- Successor Required: yes
- Successor Path: 后续 plan（time-model 改进）

### Session Window Assigner

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 合并窗口路径（`MergingWindowSet` + `MergingWindowAssigner`）在本计划中实现并测试。具体 `SessionEventTimeWindows` Assigner 的实现属于独立功能
- Successor Required: yes
- Successor Path: 后续 plan

### ProcessWindowFunction 完整 Context

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `ProcessWindowFunction.Context` 的完整实现（windowState/globalState）需要 `KeyedStateStore` 的 per-window 实例化。本计划只实现最小 Context（processingTime/watermark），不影响 aggregate/reduce/apply 功能
- Successor Required: yes
- Successor Path: 后续 plan

## Non-Blocking Follow-ups

- 性能优化：大窗口场景下的状态访问模式优化
- 分布式 exactly-once 验证：checkpoint + 窗口状态恢复
- Evictor 路径的更多边界测试

## Closure

Status Note: Plan 97 全部 5 个 Phase 执行完毕。统一 WindowOperator 通过工厂模式桥接 core/runtime 模块边界，支持 aggregate/reduce/apply/process 四种函数类型。所有窗口状态通过 IInternalStateBackend namespace 机制管理，所有定时器通过 InternalTimerService 管理。共享 SimpleAccumulator bug 和 MapState 覆盖语义 bug 已修复。ProcessWindowFunction 接口已定义。旧 WindowAggregationOperator 标记 @Deprecated。E2E 测试验证所有路径。

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor (houyi agent, task_id: ses_17e16bf38ffeT32qXc4D75dVd0)
- Audit Session: 2026-06-01
- Evidence:
  - Phase 1-5 全部 Exit Criteria PASS
  - Closure Gates 全部 PASS
  - `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` BUILD SUCCESS
  - `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` BUILD SUCCESS (881 core + 382 runtime = 1263 tests)
  - Anti-Hollow 检查：调用链追踪 WindowedStreamImpl.aggregate() → IWindowOperatorFactory → WindowOperatorBuilder → WindowOperator → InternalAppendingState.add() → userFunction.process() → output 完整连通
  - E2E 测试覆盖：TestWindowOperatorUnificationE2E（5 tests），TestWindowOperatorBuilder（10 tests），TestInternalWindowFunctionAdapters（4 tests）
  - Deferred 项分类诚实，无 in-scope live defect 被降级

Follow-up:

- PaneState 完整集成 → Deferred But Adjudicated: successor plan（窗口多次触发 + late firing）
- Watermark 持续推进 → Deferred But Adjudicated: successor plan（time-model 改进）
- Session Window Assigner → Deferred But Adjudicated: successor plan
- ProcessWindowFunction 完整 Context → Deferred But Adjudicated: successor plan
