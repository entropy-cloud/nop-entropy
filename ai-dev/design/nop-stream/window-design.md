# 窗口机制设计

> Status: active
> Created: 2026-05-19
> Parent: `core-design.md` §4（算子模型）、`architecture.md` §3（执行模型）

## 1. 定位

nop-stream 的窗口机制是流处理的核心能力之一，用于在无界数据流上定义有界的计算单元。设计上忠实简化了 Flink 的窗口模型，保留四要素架构（WindowAssigner + Trigger + Evictor + WindowFunction），但在状态存储和聚合语义上做了简化。

### 1.1 设计决策

**选了什么**：Flink 风格的窗口四要素模型。

**简化了什么**（对比 Flink）：
- Flink 使用 `AppendingState<IN, ACC>` 做窗口状态，支持增量聚合 → nop-stream 使用 `MapState<String, ACC>`，通过 `SimpleAccumulator` 实现有限的增量聚合
- Flink 支持会话窗口（Session Windows）的 MergingWindowAssigner → nop-stream 源码中有 `MergingWindowAssigner` 抽象和 WindowOperator 中的合并窗口路径，但没有具体的 Session Window Assigner 实现
- Flink 的 `WindowOperator` 支持 `allowedLateness` 延迟数据处理 → nop-stream 源码中有 `isWindowLate` 检查和迟到数据输出（side output），但当前 watermark 仅在 Source 结束时发送 `MAX_WATERMARK`，延迟数据在正常运行中不会出现

## 2. 窗口模型四要素

| 组件 | 职责 | 对应 Flink 概念 |
|---|---|---|
| **WindowAssigner** | 将每个元素分配到零或多个窗口实例 | 相同 |
| **Trigger** | 决定窗口何时触发计算（FIRE / PURGE / FIRE_AND_PURGE / CONTINUE） | 相同 |
| **Evictor** | 窗口触发后、计算前移除元素（可选） | 相同 |
| **WindowFunction / AggregateFunction** | 窗口触发时的计算逻辑 | 相同 |

四要素的交互流程：

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
  │     ├─ Evictor.evict(元素列表) [可选] → 移除部分元素
  │     └─ WindowFunction.apply(或 AggregateFunction.getResult) → 输出结果
  │
  └─► 注册清理定时器 → 定时器触发后清除窗口状态
```

## 3. 窗口类型

### 3.1 Window 抽象

| 类型 | 实现类 | 特征 |
|---|---|---|
| 时间窗口 | `TimeWindow` | 有 start/end 时间戳，maxTimestamp() = end - 1 |
| 全局窗口 | `GlobalWindow` | 单例模式，所有元素同一窗口，需配合自定义 Trigger |

### 3.2 WindowAssigner 实现

| Assigner | 窗口类型 | 语义 | 合并支持 |
|---|---|---|---|
| `TumblingEventTimeWindows` | TimeWindow | 滚动：固定大小，不重叠 | 否 |
| `SlidingEventTimeWindows` | TimeWindow | 滑动：固定大小，有重叠 | 否 |
| `GlobalWindows` | GlobalWindow | 全局：所有元素同一窗口 | 否 |

**MergingWindowAssigner**（抽象类）：支持窗口合并的 Assigner 基类（用于会话窗口场景）。WindowOperator 中有完整的合并窗口处理路径，但当前没有具体的 Session Window Assigner 实现。

### 3.3 设计决策

**选了什么**：事件时间（event time）窗口为主，保留处理时间（processing time）接口。

**简化了什么**：
- Flink 有 TumblingProcessingTimeWindows / SlidingProcessingTimeWindows → nop-stream 未实现
- Flink 有 SessionWindows（基于事件间隔动态合并）→ nop-stream 无具体实现
- 当前 watermark 仅在 Source 结束时发送 MAX_WATERMARK → 事件时间窗口实际上只在数据流结束时才触发

## 4. Trigger 体系

### 4.1 TriggerResult

| 结果 | 含义 |
|---|---|
| `CONTINUE` | 不做任何操作 |
| `FIRE` | 触发计算，保留窗口状态 |
| `PURGE` | 清除窗口状态 |
| `FIRE_AND_PURGE` | 触发计算并清除窗口状态 |

### 4.2 Trigger 实现

| Trigger | 触发条件 | 事件时间/处理时间 |
|---|---|---|
| `EventTimeTrigger` | watermark ≥ window.maxTimestamp() | 事件时间 |
| `ProcessingTimeTrigger` | 处理时间 ≥ window.maxTimestamp() | 处理时间 |
| `CountTrigger` | 元素数量 ≥ 阈值 | 不依赖时间 |
| `ContinuousEventTimeTrigger` | 每隔指定事件时间间隔触发 | 事件时间 |
| `ContinuousProcessingTimeTrigger` | 每隔指定处理时间间隔触发 | 处理时间 |
| `DeltaTrigger` | delta 值 ≥ 阈值 | 不依赖时间 |
| `PurgingTrigger` | 包装其他 Trigger，将 FIRE 转为 FIRE_AND_PURGE | 包装 |
| `ProcessingTimeoutTrigger` | 处理超时触发 | 处理时间 |

### 4.3 Trigger 回调接口

| 方法 | 调用时机 | 用途 |
|---|---|---|
| `onElement(element, timestamp, window, ctx)` | 每条记录到达 | 检查是否触发 |
| `onEventTime(timestamp, window, ctx)` | 事件时间定时器触发 | 检查窗口是否到期 |
| `onProcessingTime(timestamp, window, ctx)` | 处理时间定时器触发 | 检查窗口是否到期 |
| `onMerge(window, ctx)` | 窗口合并时 | 合并两个 Trigger 的状态 |
| `clear(window, ctx)` | 窗口清除时 | 清理 Trigger 内部状态 |

Trigger 的 `ctx` 提供注册/删除定时器和获取当前 watermark 的能力。

## 5. WindowOperator 核心逻辑

WindowOperator 是 nop-stream 中最复杂的算子，完整实现了 Flink 风格的窗口处理。

### 5.1 关键状态

| 状态 | 类型 | 用途 |
|---|---|---|
| `windowContentsState` | `MapState<String, ACC>` | 窗口内容，按 (key, namespace=window) 存储 |
| `mergingWindowSet` | `MergingWindowSet<W>` | 合并窗口的元数据（仅 MergingWindowAssigner 路径使用） |
| `internalTimerService` | `InternalTimerService<W>` | 事件时间/处理时间定时器管理 |

### 5.2 非合并窗口路径

适用于 TumblingEventTimeWindows、SlidingEventTimeWindows 等。

```
processElement(element)
  ├── windowAssigner.assignWindows(element) → 窗口集合
  ├── keySelector.getKey(element) → 提取当前 key
  ├── keyedStateBackend.setCurrentKey(key)
  │
  ├── 对每个窗口:
  │     ├── isWindowLate(window) → 迟到则跳过
  │     ├── addWindowElement(key, window, value)
  │     │     ├── namespace = windowNamespace(window)
  │     │     ├── 当前值 = windowContentsState.get(WINDOW_VALUE_KEY)
  │     │     ├── null → 直接存入 value
  │     │     ├── SimpleAccumulator → accumulator.add(value)
  │     │     └── 其他 → 新值覆盖旧值（last-write-wins）
  │     │
  │     ├── trigger.onElement(element) → TriggerResult
  │     ├── FIRE → emitWindowContents → 读取累积值 → windowFunction.apply()
  │     ├── PURGE → clearWindowContents → 删除窗口状态
  │     └── registerCleanupTimer(window)
  │
  └── 所有窗口都跳过 → 迟到数据处理（丢弃或 side output）
```

### 5.3 合并窗口路径

适用于 MergingWindowAssigner（如 Session Windows）。源码中有完整实现。

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
  │     ├── addWindowElement(key, actualWindow, value)
  │     ├── trigger.onElement(element) → TriggerResult
  │     └── 同上处理 FIRE/PURGE
  │
  └── mergingWindowSet.persist()
```

**合并窗口的关键点**：
- `MergingWindowSet` 维护一个 `Map<W, W>` 的 stateWindow 映射（逻辑窗口 → 状态窗口）
- 合并时，多个逻辑窗口的状态被聚合到一个状态窗口中
- trigger 状态也需要合并（调用 `onMerge`）
- 定时器需要重新注册到合并后的窗口

### 5.4 定时器触发

```
onEventTime(timer)
  ├── 如果是 MergingWindowAssigner: 查找 stateWindow 映射
  ├── trigger.onEventTime(timestamp) → TriggerResult
  ├── FIRE → emitWindowContents
  ├── 清理时间 (time == cleanupTime) → 清除窗口所有状态
  └── MergingWindowAssigner → 合并窗口元数据清理
```

### 5.5 聚合语义：三路分支

`addWindowElement` 的核心逻辑：

| 条件 | 行为 | 适用场景 | 对比 Flink |
|---|---|---|---|
| 当前值为 null | 直接存入 value | 窗口首条记录 | 相同 |
| 当前值 instanceof SimpleAccumulator | `accumulator.add(value)` → 存 `getLocalValue()`（**bug：存储原始值而非 accumulator**） | AggregateFunction | Flink 使用 InternalAppendingState 自动增量聚合 |
| 其他 | last-write-wins（覆盖） | ReduceFunction | Flink 使用 ReduceFunction.apply(old, new) 合并 |

**关键差异**：nop-stream 使用 `MapState<String, ACC>` + `SimpleAccumulator` 检查，而非 Flink 的 `AppendingState<IN, ACC>` 类型系统。这意味着：
- AggregateFunction 路径有 bug：`addWindowElement` 在 `accumulator.add(value)` 后调用 `getLocalValue()` 存储原始结果值（如 `Long`），而非存储 accumulator 本身。下次进入时 `instanceof SimpleAccumulator` 检查失败，落入 last-write-wins 分支。因此 accumulator 最多只能正确累加一次
- ReduceFunction 路径语义有偏差（覆盖而非合并）
- WindowFunction（全量收集）路径也受覆盖影响（每次 addWindowElement 都覆盖，只保留最后一条）

### 5.6 emitWindowContents：输出逻辑

`WindowOperator` 的 `emitWindowContents(W window, ACC contents)` 负责窗口结果的输出。key 来自 `triggerContext`（当前 key）。

**基础 WindowOperator**（无 Evictor）：
```
emitWindowContents(window, contents)
  └── 直接将 contents 传给 internalWindowFunction.apply()
      └── 输出通过 TimestampedCollector（保留窗口的 timestamp）
```

**EvictingWindowOperator**（子类，有 Evictor）：
```
emitWindowContents(window, contents)  // contents 始终为 null（见下）
  ├── 从 evictingWindowState 读取元素列表
  ├── evictor.evict(元素列表) → 移除部分元素
  └── 对剩余元素调用 windowFunction
```

**注意**：`EvictingWindowOperator` 是 `WindowOperator` 的子类，覆盖了 `processElement` 和 `emitWindowContents`。但当前其状态存储代码被注释掉（`evictingWindowState.add(element)` 被注释），`contents` 被硬编码为 `null`，因此 `emitWindowContents` 实际上是死代码。

### 5.7 apply / aggregate / reduce 的行为

**当前状态**：`WindowedStreamImpl` 中的 `apply()`、`aggregate()`、`reduce()` 三个方法**全部抛出 `UnsupportedOperationException`**。这是因为 core 模块的 `WindowedStreamImpl` 不依赖 runtime 模块的 `WindowOperator`，需要通过 `transform()` 方法手动构建 `WindowOperator` 实例。

| API 方法 | 行为 | 根因 |
|---|---|---|
| `.apply(WindowFunction)` | 抛 `UnsupportedOperationException` | core 不引用 runtime |
| `.aggregate(AggregateFunction)` | 抛 `UnsupportedOperationException` | core 不引用 runtime |
| `.reduce(ReduceFunction)` | 抛 `UnsupportedOperationException` | core 不引用 runtime |
| `.transform(name, type, operator)` | **可用** | 需要手动创建 WindowOperator |

如果通过 `transform()` 直接构建 `WindowOperator`，则 `WindowOperator` 内部的聚合逻辑存在上述 `getLocalValue()` bug，导致 AggregateFunction 路径也只能正确累加第一个元素。

## 6. Timer Service

### 6.1 InternalTimerService

WindowOperator 使用 `InternalTimerService<W>` 管理定时器，定时器按 (key, namespace=window) 存储。

| 操作 | 含义 |
|---|---|
| `registerEventTimeTimer(namespace, time)` | 注册事件时间定时器 |
| `registerProcessingTimeTimer(namespace, time)` | 注册处理时间定时器 |
| `deleteEventTimeTimer(namespace, time)` | 删除事件时间定时器 |
| `deleteProcessingTimeTimer(namespace, time)` | 删除处理时间定时器 |
| `currentWatermark()` | 当前 watermark 值 |
| `currentProcessingTime()` | 当前处理时间 |

### 6.2 Timer 触发流程

当 watermark 推进时（当前只在 Source 结束时发送 MAX_WATERMARK）：

1. 检查所有已注册的事件时间定时器
2. 对于 timestamp ≤ currentWatermark 的定时器，触发 `Triggerable.onEventTime(timer)`
3. WindowOperator 的 `onEventTime` 调用 trigger 的 `onEventTime`，判断是否 FIRE

### 6.3 WindowOperatorTimerService

runtime 模块中的 `WindowOperatorTimerService` 是 `InternalTimerService` 的实现，使用 `TreeSet` 存储定时器（按 timestamp 排序），支持按 (key, namespace) 精确查找和按时间范围触发。

## 7. Evictor

Evictor 是可选组件，在窗口触发后、计算前执行，用于移除窗口中的部分元素。

| Evictor | 移除规则 |
|---|---|
| `CountEvictor` | 保留最多 N 个元素 |
| `TimeEvictor` | 只保留 (watermark - keepDuration) 之后的元素 |

**当前状态**：Evictor 接口和两个实现已存在。但 `EvictingWindowOperator`（`WindowOperator` 的子类）中的状态存储代码被注释掉（`evictingWindowState.add(element)` 被注释），`contents` 被硬编码为 `null`，使得 `emitWindowContents` 成为死代码。因此 Evictor 实际上无法工作——直接原因是状态未存储，间接原因是 `apply()` 本身也抛异常。

## 8. 与 Flink 的窗口差异

| 维度 | Flink | nop-stream |
|---|---|---|
| 窗口状态 | `AppendingState<IN, ACC>`（类型安全的增量聚合） | `MapState<String, ACC>` + instanceof 检查 |
| Session Windows | 完整实现 | MergingWindowAssigner 抽象和合并路径已实现，无具体 Assigner |
| Processing Time Windows | 完整实现 | ProcessingTimeTrigger 存在，但无对应的 ProcessingTime WindowAssigner |
| 延迟数据 | `allowedLateness` + side output | 代码中有 isWindowLate 检查，但 watermark 机制不完整，实际运行中无延迟数据 |
| Evictor | 与 WindowFunction 完整配合 | 接口存在，但因 apply 抛异常无法使用 |
| 多种输出 | WindowedStream.apply/aggregate/reduce/process 全部可用 | 全部抛 UnsupportedOperationException（需通过 transform() 手动构建 WindowOperator） |

## 9. 已知限制

1. **API 层三个方法全部抛 UnsupportedOperationException** — `WindowedStreamImpl.apply()`、`aggregate()`、`reduce()` 全部抛异常（core 模块不依赖 runtime）。需通过 `transform()` 手动构建 `WindowOperator`
2. **SimpleAccumulator.getLocalValue() bug** — `addWindowElement` 在 accumulator.add 后存储 `getLocalValue()` 的返回值（如 `Long`），而非 accumulator 本身。导致第二个元素进入时 `instanceof SimpleAccumulator` 失败，落入 last-write-wins。AggregateFunction 最多只能正确累加一次
3. **MapState 覆盖语义** — last-write-wins 导致 ReduceFunction 和 WindowFunction 无法正确累积多值
4. **EvictingWindowOperator 是死代码** — 状态存储代码被注释（`evictingWindowState.add(element)`），`contents` 硬编码为 `null`，Evictor 无法工作
5. **无 Session Window 实现** — MergingWindowAssigner 和合并窗口路径已实现，但缺少具体的 Session Window Assigner
6. **watermark 仅在 Source 结束时发送** — 事件时间窗口实际上只在数据流结束时才触发（MAX_WATERMARK 触发所有窗口）。没有持续的 watermark 推进，ContinuousEventTimeTrigger 和定时注册的事件时间定时器不会在运行中触发
7. **迟到数据处理无实际意义** — `isWindowLate` 检查依赖 watermark 推进，当前 watermark 只有 MAX_WATERMARK 一个值，所有元素都在 watermark 之前到达
