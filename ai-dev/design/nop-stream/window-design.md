# 窗口机制设计

> Status: active
> Created: 2026-05-19
> Updated: 2026-06-01（重写为最优设计，经对抗性审查修订）
> Parent: `core-design.md` §4（算子模型）、`01-architecture-baseline.md` §4（执行模型）

## 1. 设计结论

1. **统一算子**：单一 `WindowOperator` 处理所有窗口类型（tumbling、sliding、session、global）、所有函数类型（aggregate、reduce、apply/process）和所有触发模式。无 evictor 时走增量状态路径，有 evictor 时走列表全量路径
2. **平台状态后端**：窗口状态通过 `IInternalStateBackend` 的 namespace 机制管理（key=key, namespace=window），不自管 HashMap
3. **平台定时器服务**：定时器通过 `InternalTimerService` 管理（key=key, namespace=window），不自管 TreeSet
4. **函数适配层**：`InternalWindowFunction` 统一适配三种用户函数（AggregateFunction、ReduceFunction、ProcessWindowFunction），算子只看到 `InternalWindowFunction`
5. **构建器模式**：`WindowOperatorBuilder` 根据函数类型和 evictor 选择状态描述符和内部函数包装，`WindowedStream` API 委托给构建器
6. **平台扩展已实现**：`IInternalStateBackend` 已增加接受 `AggregatingStateDescriptor` 的 `getInternalAppendingState` 重载，以支持 AggregateFunction 的 namespace-based 增量聚合（见 §8.3.1）

## 2. 背景与动机

窗口是无界流上定义有界计算单元的基础抽象。nop-stream 采用 Flink 风格的窗口四要素模型（WindowAssigner + Trigger + Evictor + WindowFunction），同时引入 Beam 风格的 `WindowingStrategy` 作为可序列化模型对象，参与 fingerprint 计算和 savepoint 兼容性检查。

事件时间窗口为主，保留处理时间接口。状态存储使用平台 `IKeyedStateBackend` 的 namespace 分区能力，定时器使用 `InternalTimerService`。

## 3. 窗口模型四要素

| 组件 | 职责 |
|---|---|
| **WindowAssigner** | 将每个元素分配到零或多个窗口实例 |
| **Trigger** | 决定窗口何时触发计算（FIRE / PURGE / FIRE_AND_PURGE / CONTINUE） |
| **Evictor** | 窗口触发后、计算前移除元素（可选） |
| **WindowFunction / AggregateFunction** | 窗口触发时的计算逻辑 |

交互流程：

```
元素到达
  │
  ▼
WindowAssigner.assignWindows(element, timestamp) → 窗口集合 W[]
  │
  对每个窗口 W:
  │
  ├─► 将元素加入窗口状态（namespace = W）
  │
  ├─► Trigger.onElement(element, timestamp, W) → TriggerResult
  │     ├─ CONTINUE → 不触发，等待更多元素
  │     ├─ FIRE → 触发计算
  │     ├─ PURGE → 清除窗口状态
  │     └─ FIRE_AND_PURGE → 触发计算 + 清除
  │
  ├─► 如果 FIRE:
  │     ├─ Evictor.evictBefore(元素列表) [可选，计算前]
  │     ├─ InternalWindowFunction.apply() → 输出结果
  │     └─ Evictor.evictAfter(元素列表) [可选，计算后]
  │
  └─► 注册清理定时器 → 定时器触发后清除窗口状态
```

## 4. 窗口类型

### 4.1 Window 抽象

| 类型 | 实现类 | 特征 |
|---|---|---|
| 时间窗口 | `TimeWindow` | 有 start/end 时间戳，`maxTimestamp() = end - 1` |
| 全局窗口 | `GlobalWindow` | 单例，所有元素同一窗口，需配合自定义 Trigger |

所有 Window 子类必须满足 `JsonTool` round-trip 要求（见 `state-management-design.md` §6.4）。

### 4.2 WindowAssigner 实现

| Assigner | 窗口类型 | 语义 | 合并支持 |
|---|---|---|---|
| `TumblingEventTimeWindows` | TimeWindow | 滚动：固定大小，不重叠 | 否 |
| `SlidingEventTimeWindows` | TimeWindow | 滑动：固定大小，有重叠 | 否 |
| `SessionEventTimeWindows` | TimeWindow | 会话：按间隔动态合并 | 是（`MergingWindowAssigner`） |
| `GlobalWindows` | GlobalWindow | 全局：所有元素同一窗口 | 否 |

`MergingWindowAssigner`（抽象类）：支持窗口合并的 Assigner 基类，提供 `mergeWindows(Collection<W>, MergeCallback<W>)` 方法。

## 5. Trigger 体系

### 5.1 TriggerResult

| 结果 | 含义 |
|---|---|
| `CONTINUE` | 不做任何操作 |
| `FIRE` | 触发计算，保留窗口状态 |
| `PURGE` | 清除窗口状态 |
| `FIRE_AND_PURGE` | 触发计算并清除窗口状态 |

### 5.2 Trigger 实现

| Trigger | 触发条件 | 时间语义 |
|---|---|---|
| `EventTimeTrigger` | watermark ≥ window.maxTimestamp() | 事件时间 |
| `ProcessingTimeTrigger` | 处理时间 ≥ window.maxTimestamp() | 处理时间 |
| `CountTrigger` | 元素数量 ≥ 阈值 | 不依赖时间 |
| `ContinuousEventTimeTrigger` | 每隔指定事件时间间隔触发 | 事件时间 |
| `ContinuousProcessingTimeTrigger` | 每隔指定处理时间间隔触发 | 处理时间 |
| `DeltaTrigger` | delta 值 ≥ 阈值 | 不依赖时间 |
| `PurgingTrigger` | 包装其他 Trigger，将 FIRE 转为 FIRE_AND_PURGE | 包装 |
| `ProcessingTimeoutTrigger` | 处理超时触发 | 包装（取决于内层 Trigger） |

### 5.3 Trigger 回调接口

| 方法 | 调用时机 | 用途 |
|---|---|---|
| `onElement(element, timestamp, window, ctx)` | 每条记录到达 | 检查是否触发 |
| `onEventTime(timestamp, window, ctx)` | 事件时间定时器触发 | 检查窗口是否到期 |
| `onProcessingTime(timestamp, window, ctx)` | 处理时间定时器触发 | 检查窗口是否到期 |
| `onMerge(window, ctx)` | 窗口合并时 | 合并两个 Trigger 的状态 |
| `clear(window, ctx)` | 窗口清除时 | 清理 Trigger 内部状态 |

Trigger 的 `ctx` 提供注册/删除定时器和获取当前 watermark 的能力。

## 6. AccumulationMode

| 模式 | 语义 | 状态 |
|---|---|---|
| `ACCUMULATING` | 窗口触发时输出累积结果，不清除状态，下次触发包含之前所有数据 | 已实现 |
| `DISCARDING` | 窗口触发时输出结果，清除状态，下次触发只包含新数据 | 已实现 |
| `ACCUMULATING_AND_RETRACTING` | 窗口触发时输出新结果并回撤之前的输出（需要下游支持 retraction） | **spec-only**（未实现，`WindowOperator.open()` 快速失败抛 `UnsupportedOperationException`） |

> **注**：枚举值名与代码一致（`AccumulationMode.ACCUMULATING_AND_RETRACTING`）。nop-stream 无 retract 下游消费者，故该模式标 spec-only 并在未实现分支快速失败，而非静默当作 ACCUMULATING。

## 7. WindowingStrategy

`WindowingStrategy` 是窗口策略的可序列化模型对象，注册到 `StreamComponents.windowingStrategies`，参与 fingerprint 计算和 savepoint 兼容性检查。

| 字段 | 含义 |
|---|---|
| `windowFn` | 窗口分配函数 |
| `mergeStatus` | 窗口是否支持合并（`NEVER` / `MERGE_IF_NEEDED` / `MERGE_ALWAYS`） |
| `windowCoder` | 窗口实例的序列化器 |
| `trigger` | 触发策略 |
| `accumulationMode` | 累积模式（见 §6） |
| `allowedLateness` | 允许迟到数据的时长，watermark 超过 `window.maxTimestamp + allowedLateness` 后窗口被彻底清除 |
| `outputTime` | 输出记录的时间戳策略 |
| `closingBehavior` | 窗口关闭时是否强制触发（`FIRE_IF_NON_EMPTY` / `FIRE_ALWAYS`） |
| `onTimeBehavior` | watermark 到达窗口结束时间时是否强制触发（`FIRE_IF_NON_EMPTY` / `FIRE_ALWAYS`） |

集成流程：

1. `WindowingStrategy` 注册到 `StreamComponents.windowingStrategies`（按 strategyId）
2. 参与 `StreamModel.fingerprint()` 计算
3. 参与 savepoint 兼容性检查（见 §14）
4. PaneState 和 TriggerState 进入 checkpoint（operator state）

## 8. 统一算子架构

### 8.1 设计决策

`WindowOperator` 是唯一的窗口算子实现，处理所有组合场景。关键设计点：

- **状态选择由构建器决定**：无 evictor 时使用 `InternalAppendingState`（增量聚合），有 evictor 时使用 `InternalListState`（全量列表）。算子通过统一接口访问
- **函数选择由构建器决定**：所有用户函数被包装为 `InternalWindowFunction`，算子只调用此接口
- **合并窗口是算子内部路径**：`MergingWindowAssigner` 的合并逻辑通过 `MergingWindowSet` 在算子内部处理，不是外部策略

### 8.2 核心状态

| 状态 | 用途 | 存储方式 |
|---|---|---|
| `windowState` | 窗口内容，按 (key, namespace=W) 存储 | `IInternalStateBackend` 提供的 `InternalAppendingState`（增量）或 `InternalListState`（全量） |
| `mergingWindowSet` | 合并窗口的元数据（仅 `MergingWindowAssigner` 路径） | `InternalListState` 存储 `(W, W)` 映射对 |
| `internalTimerService` | 事件时间/处理时间定时器管理 | `InternalTimerService`（key=key, namespace=W） |

状态后端由 `IInternalStateBackend` 提供，通过 `StateDescriptor` 和 `namespace = window` 管理。算子不持有自管 Map 或 TreeSet。

### 8.3 状态描述符策略

| 函数类型 | Evictor | 状态接口 | 存储 |
|---|---|---|---|
| `AggregateFunction` | 无 | `InternalAppendingState`（AggregatingStateDescriptor） | 单个 accumulator |
| `ReduceFunction` | 无 | `InternalAppendingState`（ReducingStateDescriptor） | 单个归约值 |
| `ProcessWindowFunction` / `WindowFunction` | 无 | `InternalListState` | 元素列表 |
| 任意 | 有 | `InternalListState` | 元素列表（evictor 需要遍历） |

当 evictor 存在时，无论函数类型如何，都使用 `InternalListState` 存储原始元素。触发时先执行 evictor，再在内部执行聚合后将结果传给 `InternalWindowFunction`。

#### 8.3.1 平台扩展（已实现）

Phase 1 已为 `IInternalStateBackend` 增加 `getInternalAppendingState(AggregatingStateDescriptor<IN, ACC, OUT>)` 重载，返回 `InternalAppendingState<K, N, IN, ACC, OUT>`。`MemoryKeyedStateBackend` 对应新增 `MemoryInternalAggregatingState` 实现，内部使用 `HashMap<TypedNamespaceAndKey, ACC>` 存储 accumulator。

与 `ReducingStateDescriptor` 路径的关键差异：AggregateFunction 的 `OUT` 可以不等于 `ACC`（如 accumulator 是 `StringBuilder`，输出是 `String`），因此 `InternalAppendingState` 的泛型签名为 `<K, N, IN, ACC, OUT>` 而非 reducing 路径的 `<K, N, IN, ACC, ACC>`。

### 8.4 迟到数据处理

**迟到判断条件**：`window.maxTimestamp() + allowedLateness ≤ currentWatermark`

满足此条件的窗口视为过期（late），新到达的元素不会被加入该窗口。

**迟到数据路由**：

- 默认：丢弃
- 配置 `lateDataOutputTag` 时：输出到 side output

**Cleanup time 计算**：`window.maxTimestamp() + allowedLateness`。注册为事件时间定时器，触发时清除窗口全部状态（窗口内容 + trigger 状态）。

**Late firing**：在 `allowedLateness` 窗口内（即 `window.maxTimestamp() < currentWatermark ≤ cleanupTime`），迟到数据仍可加入窗口。Trigger 在此阶段触发的 firing 的 PaneTiming 为 `LATE`。

### 8.5 非合并窗口处理流程

适用于 `TumblingEventTimeWindows`、`SlidingEventTimeWindows`、`GlobalWindows` 等。

```
processElement(element)
  ├── windowAssigner.assignWindows(element) → 窗口集合
  ├── keySelector.getKey(element) → 提取当前 key
  ├── keyedStateBackend.setCurrentKey(key)
  │
  ├── 对每个窗口 W:
  │     ├── isWindowLate(W) → 迟到则跳过
  │     ├── windowState.setCurrentNamespace(W)
  │     ├── windowState.add(element) → 增量聚合或追加到列表
  │     ├── trigger.onElement(element, timestamp, W) → TriggerResult
  │     ├── FIRE → emitWindowContents(key, W)
  │     ├── PURGE → clearWindow(key, W)
  │     └── registerCleanupTimer(W)
  │
  └── 所有窗口都跳过 → 迟到数据处理（见 §8.4）
```

### 8.6 合并窗口处理流程

适用于 `MergingWindowAssigner`（如 Session Windows）。

```
processElement(element)
  ├── windowAssigner.assignWindows(element) → 新窗口
  ├── keySelector.getKey(element)
  │
  ├── 对每个新窗口:
  │     ├── mergingWindowSet.addWindow(window, mergeFunction)
  │     │     ├── 如果需要合并:
  │     │     │     ├── 迁移被合并窗口的状态到目标窗口
  │     │     │     ├── 合并 trigger 状态（trigger.onMerge）
  │     │     │     └── 删除被合并窗口的定时器，注册新窗口的定时器
  │     │     └── 不需要合并: actualWindow == window
  │     │
  │     ├── windowState.setCurrentNamespace(actualWindow)
  │     ├── windowState.add(element)
  │     ├── trigger.onElement(element, timestamp, actualWindow) → TriggerResult
  │     └── 同上处理 FIRE/PURGE
  │
  └── mergingWindowSet.persist()
```

`MergingWindowSet` 维护逻辑窗口 → 状态窗口的映射。合并时多个逻辑窗口的状态被聚合到一个状态窗口中，trigger 状态通过 `onMerge` 合并，定时器重新注册到合并后的窗口。

#### 8.6.1 合并窗口约束

- **存储格式**：`MergingWindowSet` 的映射使用 `InternalListState`（namespace=VoidNamespace）存储 `(W, W)` 对列表。每次 `persist()` 时全量覆盖
- **累加器写回（AggregatingState 路径）**：`mergeWindowContents()` 合并多个 namespace 的 accumulator 后，必须通过 `InternalAppendingState.setAccumulator()` 将合并后的 ACC 直接写入目标窗口的 namespace，**不可**使用 `clear()` + `add()`——后者会对已经合并的 ACC 再施加一次 `AggregateFunction.add()` 变换，导致结果错误或 ACC/IN 类型不匹配异常。`setAccumulator()` 是 `getAccumulator()` 的逆操作，绕过累加变换直接存储原始 ACC
- **定时器迁移顺序**：先删除所有被合并窗口的 cleanup timer，再注册合并后窗口的 cleanup timer。如果被合并窗口的定时器已触发（即定时器已在队列中但尚未处理），合并逻辑仍需正确处理——合并后的窗口以新的 cleanup time 为准
- **合并失败处理**：如果 `trigger.onMerge` 抛异常，已迁移的状态不回滚。合并操作发生在 `addWindow` 内部，此时元素尚未加入，窗口状态处于一致状态。异常向上传播导致该元素处理失败

### 8.7 输出逻辑

**无 Evictor 路径**：

```
emitWindowContents(key, window)
  ├── 从 windowState 读取内容（增量路径：accumulator 单值；列表路径：元素集合）
  ├── internalWindowFunction.apply(key, window, contents, collector)
  └── collector.collect(result) → TimestampedCollector 保留窗口 timestamp
```

**有 Evictor 路径**：

```
emitWindowContents(key, window)
  ├── 从 windowState 读取元素列表到局部瞬态副本 wrapped (ArrayList)
  ├── evictor.evictBefore(wrapped, size, window, EvictorContext)   [计算前裁剪]
  ├── 将 wrapped 转换为 evictedElements
  ├── 增量函数（AggregateFunction/ReduceFunction）:
  │     └── 在内部遍历剩余元素执行聚合 → 得到 ACC
  │         → internalWindowFunction.apply(key, window, ACC, collector)
  ├── 全量函数（ProcessWindowFunction/WindowFunction）:
  │     └── internalWindowFunction.apply(key, window, 剩余元素集合, collector)
  ├── evictor.evictAfter(wrapped, size, window, EvictorContext)    [计算后裁剪]
  └── collector.collect(result)
```

**Evictor 瞬态语义（transient-per-fire，与 Flink 一致）**：eviction 作用于**局部瞬态副本** `wrapped`，**不写回 state**。在 `ACCUMULATING` 模式下，窗口触发后不清除状态（`emitWindowContents` 仅在 `DISCARDING` 模式调用 `clearWindowContents`），故**全量元素跨 firing 持久化，eviction 每次 firing 从 state 重新读取并重算**。这与 Flink `EvictingWindowOperator` 的语义一致（Flink 同样从 `ListState` 读副本、eviction 不写回、每次重算）。

> **决策（G46）**：nop-stream 收口为"核对 + 文档"，**不引入 evictAfter 持久化裁剪**。持久化会永久丢弃 Flink 在每次 firing 会重新淘汰的元素，构成回归（破坏 ACCUMULATING 模式的全量累积契约）。live 实现：`WindowOperator.emitWindowContents()` evictor 分支构造 `wrapped`（局部 `ArrayList`，从 state 读取），`evictBefore` → `userFunction.process` → `evictAfter`，全程不写回 state。

**Evictor 路径的类型桥接**：有 Evictor 时，状态存储原始元素（`IN`），但 `InternalWindowFunction` 接收的可能是聚合后的 `ACC`（增量函数）或 `Iterable<IN>`（全量函数）。这个转换由算子在 `emitWindowContents` 内部完成，不在适配器中。

### 8.8 定时器触发与 Watermark 链路

Watermark 推进到窗口触发的完整调用链：

```
processWatermark(watermark)
  → InternalTimerService.advanceWatermark(watermark)
  → 对所有 timestamp ≤ watermark 的事件时间定时器:
      → WindowOperator.onEventTime(timer)
          ├── 如果是 MergingWindowAssigner: 查找 stateWindow 映射
          ├── trigger.onEventTime(timestamp, window, ctx) → TriggerResult
          ├── FIRE → emitWindowContents
          ├── PURGE → clearWindow
          ├── 清理时间 (timestamp == cleanupTime):
          │     → clearWindow（清除窗口内容状态 + trigger.clear）
          │     → MergingWindowAssigner → 合并窗口元数据清理
          └── 注册下一次触发定时器（ContinuousTrigger 场景）
```

## 9. InternalWindowFunction 适配层

`InternalWindowFunction` 是算子看到的唯一函数接口。构建器根据用户函数类型创建对应的内部适配器：

### 9.1 无 Evictor 路径

| 用户函数 | 内部适配器 | 传入内容 | 说明 |
|---|---|---|---|
| `AggregateFunction` | `InternalSingleValueWindowFunction` | ACC 单值 | 状态中保存 accumulator，直接输出 getResult |
| `AggregateFunction` + `ProcessWindowFunction` | `InternalSingleValueProcessWindowFunction` | ACC 单值 | 状态中保存 accumulator，传给 ProcessWindowFunction |
| `AggregateFunction` + `WindowFunction` | `InternalSingleValueWindowFunction` | ACC 单值 | 同上，适配旧版 WindowFunction |
| `ReduceFunction` | `InternalSingleValueWindowFunction` | T 单值 | 状态中保存归约值，直接输出 |
| `ReduceFunction` + `ProcessWindowFunction` | `InternalSingleValueProcessWindowFunction` | T 单值 | 状态中保存归约值，传给 ProcessWindowFunction |
| `ProcessWindowFunction` | `InternalIterableProcessWindowFunction` | `Iterable<T>` | 全量元素列表 |
| `WindowFunction` | `InternalIterableWindowFunction` | `Iterable<T>` | 全量元素列表 |

### 9.2 有 Evictor 路径

有 Evictor 时，状态存储原始元素。适配器不变，但算子在调用适配器前先在内部执行聚合（增量函数）或直接传递列表（全量函数）。

| 用户函数 | 内部适配器 | 算子内部预处理 | 传入适配器的内容 |
|---|---|---|---|
| `AggregateFunction` | `InternalSingleValueWindowFunction` | 遍历元素执行 `add` → ACC | ACC 单值 |
| `AggregateFunction` + `ProcessWindowFunction` | `InternalSingleValueProcessWindowFunction` | 遍历元素执行 `add` → ACC | ACC 单值 |
| `ReduceFunction` | `InternalSingleValueWindowFunction` | 遍历元素执行 `reduce` → T | T 单值 |
| `ReduceFunction` + `ProcessWindowFunction` | `InternalSingleValueProcessWindowFunction` | 遍历元素执行 `reduce` → T | T 单值 |
| `ProcessWindowFunction` | `InternalIterableProcessWindowFunction` | 无 | `Iterable<T>` |
| `WindowFunction` | `InternalIterableWindowFunction` | 无 | `Iterable<T>` |

### 9.3 设计约束

- **无 Evictor**：适配器的传入内容类型与状态类型一致（增量路径：单值；列表路径：`Iterable`）
- **有 Evictor**：状态类型始终为列表，但适配器接收的内容可能是单值（由算子内部聚合转换）。这个转换发生在 `emitWindowContents` 中，不在适配器中
- 增量函数（AggregateFunction / ReduceFunction）不配 WindowFunction 时，适配器直接输出最终结果

## 10. WindowOperatorBuilder

构建器封装算子构造的复杂度，`WindowedStream` API 委托给构建器。

构建器的职责：

1. 持有窗口配置（WindowAssigner、Trigger、Evictor、allowedLateness、lateDataOutputTag）
2. 根据函数类型选择状态描述符（见 §8.3）
3. 根据是否有 Evictor 决定状态路径（有 Evictor 时一律 `InternalListState`）
4. 包装用户函数为 `InternalWindowFunction`（见 §9）
5. 构造 `WindowOperator` 实例

### 10.1 工厂注入

`WindowedStream` API 通过 `IWindowOperatorFactory` 获取 `WindowOperatorBuilder`，而非直接实例化。工厂在 runtime 模块实现，通过 SPI 注册。`WindowedStreamImpl` 从 `StreamComponents` 获取工厂（见 `core-design.md` §2.2.1）。

**设计约束**：工厂缺失时窗口操作必须快速失败，不允许静默回退到不支持 checkpoint 的简化路径。

**core-only 场景**：`WindowOperator` 依赖 `IInternalStateBackend` 和 `InternalTimerService`（runtime 模块）。core 模块不提供窗口算子的运行时实现，只定义接口和 API。core-only 测试如需验证窗口逻辑，使用 `OperatorTestHarness` 直接构造算子，不走 `WindowedStream` API 路径。

### 10.2 无 Evictor 构建

| 构建方法 | 状态描述符 | 内部函数 |
|---|---|---|
| `aggregate(AggregateFunction)` | `AggregatingStateDescriptor` | `InternalSingleValueWindowFunction`（直接输出 getResult） |
| `aggregate(AggregateFunction, ProcessWindowFunction)` | `AggregatingStateDescriptor` | `InternalSingleValueProcessWindowFunction` |
| `reduce(ReduceFunction)` | `ReducingStateDescriptor` | `InternalSingleValueWindowFunction`（直接输出归约值） |
| `reduce(ReduceFunction, ProcessWindowFunction)` | `ReducingStateDescriptor` | `InternalSingleValueProcessWindowFunction` |
| `apply(WindowFunction)` | `ListStateDescriptor` | `InternalIterableWindowFunction` |
| `process(ProcessWindowFunction)` | `ListStateDescriptor` | `InternalIterableProcessWindowFunction` |

### 10.3 有 Evictor 构建

所有方法统一使用 `ListStateDescriptor`。函数包装不变（同 §10.2 对应行），算子在 `emitWindowContents` 中处理中间聚合。

## 11. Evictor

Evictor 是可选组件，在窗口触发后、计算前执行，用于移除窗口中的部分元素。

| Evictor | 移除规则 |
|---|---|
| `CountEvictor` | 保留最多 N 个元素 |
| `TimeEvictor` | 只保留 `(watermark - keepDuration)` 之后的元素 |
| `DeltaEvictor` | 基于相邻元素的 delta 值移除 |

当 Evictor 存在时，状态存储始终使用 `InternalListState`（存储原始元素），因为 Evictor 需要逐个检查元素。这与 §8.3 的状态描述符策略一致——Evictor 优先级高于函数类型选择。

Evictor 在 `emitWindowContents` 中执行，不是独立的算子或外部策略。

**两次调用（evictBefore / evictAfter）**：`Evictor` 接口定义 `evictBefore`（计算前裁剪）与 `evictAfter`（计算后裁剪）两个回调，`emitWindowContents` 按序调用：先 `evictBefore` → 再 `userFunction.process` → 最后 `evictAfter`。两者均作用于局部瞬态副本，**不持久化**。

**瞬态淘汰语义（transient-per-fire）**：eviction 每次窗口触发从 state 读取全量元素到局部副本后裁剪，裁剪结果**不写回 state**。因此：
- `DISCARDING` 模式：触发后 `clearWindowContents` 清空 state，下次触发从空开始。
- `ACCUMULATING` 模式：触发后不清空 state，全量元素跨 firing 持久化，eviction 每次 firing 重算。

此语义与 Flink `EvictingWindowOperator` 一致。**明确不引入"evictAfter 持久化裁剪"**——后者会永久丢弃 ACCUMULATING 模式下本应跨 firing 保留的元素，构成回归。

## 12. Timer Service

### 12.1 InternalTimerService

`InternalTimerService` 管理定时器，按 (key, namespace=window) 存储（见 `state-management-design.md` §8）。

| 操作 | 含义 |
|---|---|
| `registerEventTimeTimer(namespace, time)` | 注册事件时间定时器 |
| `registerProcessingTimeTimer(namespace, time)` | 注册处理时间定时器 |
| `deleteEventTimeTimer(namespace, time)` | 删除事件时间定时器 |
| `deleteProcessingTimeTimer(namespace, time)` | 删除处理时间定时器 |
| `currentWatermark()` | 当前 watermark 值 |
| `currentProcessingTime()` | 当前处理时间 |

### 12.2 Timer 与 Checkpoint

窗口定时器进入 checkpoint（见 `state-management-design.md` §8）。不 checkpoint 定时器的窗口实现不能声明支持 exactly-once 恢复。

## 13. PaneState / Pane 跟踪

Pane 跟踪记录窗口多次触发（early/on-time/late firing）的元信息，用于在 `WindowOperator.computePaneInfo` 中分类 firing 并分配 pane index。

### 13.1 live 实现

`WindowOperator` 通过 `paneTracking`（`Map<String, PaneTrackingInfo>`）跟踪 per-(key,window) 的 pane 状态：

| 字段 | 说明 |
|---|---|
| `paneIndex` | 窗口内第几次触发（0-based），每次 firing 递增 |
| `onTimeEmitted` | 是否已发射过 ON_TIME firing（首次跨 watermark 为 ON_TIME 并置 true，其后为 LATE） |

`PaneInfo`（不可变，每次 firing 构造）字段：`index`、`isFirst`（`paneIndex==0`）、`isLast`（**见 §13.3 裁定**）、`timing`（EARLY/ON_TIME/LATE）。

PaneTiming 判定（`computePaneInfo`）：
- `watermark < window.maxTimestamp()` → **EARLY**
- `watermark >= window.maxTimestamp()` 且首次跨过 → **ON_TIME**（置 `onTimeEmitted=true`）
- `watermark >= window.maxTimestamp()` 且已发射过 ON_TIME → **LATE**

### 13.2 Checkpoint / Restore（G48）

`paneTracking` 参与 `snapshotState` / `restoreState`，以 `PaneTrackingSnapshot` DTO（可序列化）持久化：

- **snapshot**：`snapshotState` 将 TimeWindow-scoped 的 pane 条目写入 operator state `"pane-tracking"`。
- **restore**：`restoreState`（在 `open()` 之前调用）捕获 snapshot；`open()` 在初始化 `paneTracking` map 后延迟应用，恢复 `paneIndex` / `onTimeEmitted`。
- **TimeWindow 限制**：仅 `TimeWindow`-scoped pane 参与持久化（`windowNamespace` 为可逆的 `TW:start,end`）。非 TimeWindow 窗口的 `windowNamespace` 为 `className#toString`（不可逆），不参与持久化以避免 restore 错配。未来可扩展可逆 namespace 编码。

恢复后窗口首次触发不再被误判为 ON_TIME / isFirst —— pane index/onTimeEmitted 续接。

### 13.3 isLast 裁定

`PaneInfo.isLast` 在当前架构下**清理前不可知，恒为 false**。理由：`computePaneInfo` 在 `emitWindowContents` 内构造不可变 `PaneInfo` 并立即随 emit 输出；清理时（`clearWindowContents`）已无法回填已输出的 `isLast`。**不采用时间上不可行的回填方案**。若未来需要真实 `isLast` 语义，须改为清理时独立 emit 一次 cleanup pane（属未来增强，本设计不做）。

### 13.4 与 AccumulationMode 的交互（spec）

- `DISCARDING`：每次触发后清除窗口内容，paneIndex 递增
- `ACCUMULATING`：窗口内容持续累积，paneIndex 递增，每次输出包含全部累积数据
- `ACCUMULATING_AND_RETRACTING`：spec-only，输出新结果 + 回撤上一 pane 的输出（未实现，见 §6）

## 14. WindowCompatibilityCheck

Savepoint 恢复时，比较当前 `WindowingStrategy` 与 savepoint 中的 `WindowingStrategy`，判断是否兼容。

**允许的变化**：

- `allowedLateness` 变大（更多延迟数据被接受）
- `closingBehavior` 从 `FIRE_IF_NON_EMPTY` 改为 `FIRE_ALWAYS`

**不允许的变化**（需要显式迁移 action）：

- `windowFn` 类型变化（如从 Tumbling 改为 Sliding）
- `accumulationMode` 变化
- `trigger` 类型变化

检查流程：

```
savepoint 恢复时：
  1. 从 Epoch Manifest 读取 streamModelFingerprint
  2. 计算 StreamModel.fingerprint()
  3. 提取当前和 savepoint 中的 WindowingStrategy
  4. 逐字段比较：
     - windowFn 类型 → 必须一致
     - accumulationMode → 必须一致
     - trigger 类型 → 必须一致
     - allowedLateness → 只允许变大
     - closingBehavior → 只允许放宽
  5. 不兼容 → 拒绝恢复，或要求显式迁移 action
```

## 15. 拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| 多算子分离（Aggregate 用一个算子，Apply 用另一个算子） | 状态管理、定时器处理、合并窗口逻辑高度重复。增量聚合和全量处理的区别仅在于状态描述符类型和函数包装方式，不值得分裂为独立算子 |
| 自管 HashMap 状态 | 不参与平台的 keyed state 生命周期管理（checkpoint、恢复、分片路由）。namespace-based state 是平台的标准模式，窗口状态应遵守 |
| 自管 TreeMap 定时器 | 不参与平台的 timer checkpoint。`InternalTimerService` 已提供 (key, namespace) 精确定时器管理，重复实现是冗余的 |
| Evictor 作为独立算子 | Evictor 操作的是窗口内部元素列表，必须在窗口输出前执行。作为算子间操作会破坏窗口的原子性语义 |
| 在 core 模块中保留窗口算子实现 | core 模块定义接口和模型，runtime 模块提供实现。窗口算子的状态管理和定时器依赖 runtime 的 `IInternalStateBackend` 和 `InternalTimerService`，属于 runtime 层 |
| AggregateFunction 适配为 ReduceFunction（绕过 `AggregatingStateDescriptor` 缺失） | AggregateFunction 的 `createAccumulator` / `add` / `getResult` 三阶段语义与 ReduceFunction 的 `reduce` 语义不同。强行适配会丢失 accumulator 的中间状态类型信息（ACC ≠ T），在 Evictor 路径下无法正确恢复聚合中间态。正确做法是扩展 `IInternalStateBackend` |

## 16. 与已有设计的关系

| 设计文档 | 关系 |
|---|---|
| `core-design.md` §4（算子模型） | WindowOperator 是 `OneInputStreamOperator` 的实现，遵循算子生命周期 |
| `01-architecture-baseline.md` §4（执行模型） | 窗口算子在 StreamGraph → JobGraph 管线中被当作普通 keyed 算子处理 |
| `state-management-design.md` §2–§5 | 窗口状态使用 `IInternalStateBackend` 的 `InternalAppendingState` 和 `InternalListState`，通过 `StateDescriptor` 和 namespace 管理。§8.3.1 的平台扩展需同步更新 `state-management-design.md` |
| `checkpoint-design.md` | 窗口状态和定时器参与 epoch checkpoint，支持 exactly-once 恢复 |
| `time-model-design.md` §2–§3 | 事件时间窗口依赖 watermark 推进（§2 WatermarkStrategy）；Trigger 的 `onEventTime` 由 watermark 驱动（§3 WatermarkGenerator 传播机制） |
