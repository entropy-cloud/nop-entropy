# 窗口机制设计

> Status: active
> Created: 2026-05-19
> Updated: 2026-05-23
> Parent: `core-design.md` §4（算子模型）、`architecture.md` §3（执行模型）

## 1. 定位

nop-stream 窗口机制在无界数据流上定义有界的计算单元。核心架构采用 Flink 风格的窗口四要素模型（WindowAssigner + Trigger + Evictor + WindowFunction），同时引入 Beam 风格的 `WindowingStrategy` 作为可序列化模型对象，参与 fingerprint 计算和 savepoint 兼容性检查。

**设计要点**：

- 事件时间（event time）窗口为主，保留处理时间接口
- 状态存储使用 `MapState<String, ACC>` + `SimpleAccumulator`，不依赖 Flink 的 `AppendingState` 类型系统
- `MergingWindowAssigner` 抽象和合并窗口路径已实现，但无具体 Session Window Assigner
- Watermark 当前仅在 Source 结束时发送 `MAX_WATERMARK`

## 2. 窗口模型四要素

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
  │     ├─ Evictor.evict(元素列表) [可选]
  │     └─ WindowFunction.apply / AggregateFunction.getResult → 输出结果
  │
  └─► 注册清理定时器 → 定时器触发后清除窗口状态
```

## 3. WindowingStrategy

`WindowingStrategy<W>` 是窗口策略的可序列化模型对象，注册到 `StreamComponents.windowingStrategies`，参与 fingerprint 计算和 savepoint 兼容性检查。

```java
class WindowingStrategy<W extends Window> {
    WindowAssigner<? super W, ?> windowFn;
    MergeStatus mergeStatus;       // NEVER / MERGE_IF_NEEDED / MERGE_ALWAYS
    Coder<W> windowCoder;
    Trigger<W, ?> trigger;
    AccumulationMode accumulationMode;
    Duration allowedLateness;
    OutputTimeFn outputTime;       // END_OF_WINDOW / EARLIEST / LATEST
    ClosingBehavior closingBehavior; // FIRE_IF_NON_EMPTY / FIRE_ALWAYS
    OnTimeBehavior onTimeBehavior;   // FIRE_IF_NON_EMPTY / FIRE_ALWAYS
}
```

| 字段 | 含义 |
|---|---|
| `windowFn` | 窗口分配函数 |
| `mergeStatus` | 窗口是否支持合并（`NEVER` = Tumbling/Sliding, `MERGE_IF_NEEDED` = Session） |
| `windowCoder` | 窗口实例的序列化器 |
| `trigger` | 触发策略 |
| `accumulationMode` | 累积模式（见 §6） |
| `allowedLateness` | 允许迟到数据的时长，watermark 超过 `window.maxTimestamp + allowedLateness` 后窗口被彻底清除 |
| `outputTime` | 输出记录的时间戳策略 |
| `closingBehavior` | 窗口关闭时是否强制触发 |
| `onTimeBehavior` | watermark 到达窗口结束时间时是否强制触发 |

**集成流程**：

1. `WindowingStrategy` 注册到 `StreamComponents.windowingStrategies`（按 strategyId）
2. 参与 `StreamModel.fingerprint()` 计算
3. 参与 savepoint 兼容性检查（`WindowCompatibilityCheck`，见 §11）
4. `PaneState` 和 `TriggerState` 进入 checkpoint（operator state）

## 4. 窗口类型

### 4.1 Window 抽象

| 类型 | 实现类 | 特征 |
|---|---|---|
| 时间窗口 | `TimeWindow` | 有 start/end 时间戳，`maxTimestamp() = end - 1` |
| 全局窗口 | `GlobalWindow` | 单例，所有元素同一窗口，需配合自定义 Trigger |

### 4.2 WindowAssigner 实现

| Assigner | 窗口类型 | 语义 | 合并支持 |
|---|---|---|---|
| `TumblingEventTimeWindows` | TimeWindow | 滚动：固定大小，不重叠 | 否 |
| `SlidingEventTimeWindows` | TimeWindow | 滑动：固定大小，有重叠 | 否 |
| `GlobalWindows` | GlobalWindow | 全局：所有元素同一窗口 | 否 |

`MergingWindowAssigner`（抽象类）：支持窗口合并的 Assigner 基类。`WindowOperator` 中有完整的合并窗口处理路径，但当前无具体 Session Window Assigner 实现。

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
| `ProcessingTimeoutTrigger` | 处理超时触发 | 处理时间 |

### 5.3 Trigger 回调接口

| 方法 | 调用时机 | 用途 |
|---|---|---|
| `onElement(element, timestamp, window, ctx)` | 每条记录到达 | 检查是否触发 |
| `onEventTime(timestamp, window, ctx)` | 事件时间定时器触发 | 检查窗口是否到期 |
| `onProcessingTime(timestamp, window, ctx)` | 处理时间定时器触发 | 检查窗口是否到期 |
| `onMerge(window, ctx)` | 窗口合并时 | 合并两个 Trigger 的状态 |
| `clear(window, ctx)` | 窗口清除时 | 清理 Trigger 内部状态 |

Trigger 的 `ctx` 提供注册/删除定时器和获取当前 watermark 的能力。

### 5.4 TriggerState

```java
class TriggerState {
    String triggerId;                // 对应 WindowingStrategy 中的 trigger ID
    Map<String, Object> state;       // trigger 内部状态（可序列化）
    long lastFiringTimestamp;
}
```

`TriggerState` 进入 checkpoint，恢复后 trigger 可从断点继续判断。

## 6. AccumulationMode

| 模式 | 语义 |
|---|---|
| `ACCUMULATING` | 窗口触发时输出累积结果，不清除状态，下次触发包含之前所有数据 |
| `DISCARDING` | 窗口触发时输出结果，清除状态，下次触发只包含新数据 |
| `RETRACTING` | 窗口触发时输出新结果并回撤之前的输出（需要下游支持 retraction） |

## 7. PaneState

每次窗口触发产生一个 Pane。`PaneState` 记录当前 pane 的元信息和累积器状态，进入 checkpoint。

```java
class PaneState {
    int paneIndex;            // 窗口内第几次触发（0-based）
    PaneTiming paneTiming;    // EARLY / ON_TIME / LATE
    Object accumulator;       // 当前累积器状态
    Object retracted;         // 之前已回撤的输出（RETRACTING 模式）
    long lastFiringTimestamp; // 上次触发时间
}
```

| PaneTiming | 含义 |
|---|---|
| `EARLY` | 在 watermark 到达窗口结束时间之前触发 |
| `ON_TIME` | 在 watermark 到达窗口结束时间时触发 |
| `LATE` | 在 watermark 超过窗口结束时间 + allowedLateness 后触发 |

**与 AccumulationMode 的交互**：

- `DISCARDING`：每次触发后清除 accumulator，paneIndex 递增
- `ACCUMULATING`：accumulator 持续累积，paneIndex 递增，每次输出包含全部累积数据
- `RETRACTING`：输出新结果 + 回撤上一 pane 的输出，下游合并后得到正确结果

## 8. WindowOperator 核心逻辑

### 8.1 关键状态

| 状态 | 类型 | 用途 |
|---|---|---|
| `windowContentsState` | `MapState<String, ACC>` | 窗口内容，按 (key, namespace=window) 存储 |
| `mergingWindowSet` | `MergingWindowSet<W>` | 合并窗口的元数据（仅 MergingWindowAssigner 路径使用） |
| `internalTimerService` | `InternalTimerService<W>` | 事件时间/处理时间定时器管理 |

### 8.2 非合并窗口路径

适用于 `TumblingEventTimeWindows`、`SlidingEventTimeWindows` 等。

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

### 8.3 合并窗口路径

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
  │     ├── addWindowElement(key, actualWindow, value)
  │     ├── trigger.onElement(element) → TriggerResult
  │     └── 同上处理 FIRE/PURGE
  │
  └── mergingWindowSet.persist()
```

`MergingWindowSet` 维护 `Map<W, W>` 的 stateWindow 映射（逻辑窗口 → 状态窗口）。合并时多个逻辑窗口的状态被聚合到一个状态窗口中，trigger 状态通过 `onMerge` 合并，定时器重新注册到合并后的窗口。

### 8.4 聚合语义：三路分支

`addWindowElement` 的核心逻辑：

| 条件 | 行为 | 适用场景 |
|---|---|---|
| 当前值为 null | 直接存入 value | 窗口首条记录 |
| 当前值 instanceof SimpleAccumulator | `accumulator.add(value)` | AggregateFunction |
| 其他 | last-write-wins（覆盖） | ReduceFunction / WindowFunction |

**与 Flink 的差异**：nop-stream 使用 `MapState<String, ACC>` + `SimpleAccumulator` 检查，而非 Flink 的 `AppendingState<IN, ACC>` 类型系统。

### 8.5 emitWindowContents：输出逻辑

**基础 WindowOperator**（无 Evictor）：直接将 contents 传给 `internalWindowFunction.apply()`，输出通过 `TimestampedCollector`（保留窗口的 timestamp）。

**EvictingWindowOperator**（子类，有 Evictor）：从 `evictingWindowState` 读取元素列表 → `evictor.evict()` → 对剩余元素调用 windowFunction。

### 8.6 定时器触发

```
onEventTime(timer)
  ├── 如果是 MergingWindowAssigner: 查找 stateWindow 映射
  ├── trigger.onEventTime(timestamp) → TriggerResult
  ├── FIRE → emitWindowContents
  ├── 清理时间 (time == cleanupTime) → 清除窗口所有状态
  └── MergingWindowAssigner → 合并窗口元数据清理
```

## 9. Timer Service

### 9.1 InternalTimerService

`InternalTimerService<W>` 管理定时器，按 (key, namespace=window) 存储。

| 操作 | 含义 |
|---|---|
| `registerEventTimeTimer(namespace, time)` | 注册事件时间定时器 |
| `registerProcessingTimeTimer(namespace, time)` | 注册处理时间定时器 |
| `deleteEventTimeTimer(namespace, time)` | 删除事件时间定时器 |
| `deleteProcessingTimeTimer(namespace, time)` | 删除处理时间定时器 |
| `currentWatermark()` | 当前 watermark 值 |
| `currentProcessingTime()` | 当前处理时间 |

### 9.2 Timer 触发流程

Watermark 推进时（当前只在 Source 结束时发送 `MAX_WATERMARK`）：

1. 检查所有已注册的事件时间定时器
2. 对于 `timestamp ≤ currentWatermark` 的定时器，触发 `Triggerable.onEventTime(timer)`
3. `WindowOperator.onEventTime` 调用 trigger 的 `onEventTime`，判断是否 FIRE

### 9.3 WindowOperatorTimerService

runtime 模块中的 `WindowOperatorTimerService` 是 `InternalTimerService` 的实现，使用 `TreeSet` 存储定时器（按 timestamp 排序），支持按 (key, namespace) 精确查找和按时间范围触发。

## 10. Evictor

Evictor 是可选组件，在窗口触发后、计算前执行，用于移除窗口中的部分元素。

| Evictor | 移除规则 |
|---|---|
| `CountEvictor` | 保留最多 N 个元素 |
| `TimeEvictor` | 只保留 `(watermark - keepDuration)` 之后的元素 |

`EvictingWindowOperator` 是 `WindowOperator` 的子类，覆盖了 `processElement` 和 `emitWindowContents`。

## 11. WindowCompatibilityCheck

Savepoint 恢复时，比较当前 `WindowingStrategy` 与 savepoint 中的 `WindowingStrategy`，判断是否兼容。

**允许的变化**：

- `allowedLateness` 变大（更多延迟数据被接受）
- `closingBehavior` 从 `FIRE_IF_NON_EMPTY` 改为 `FIRE_ALWAYS`

**不允许的变化**（需要显式迁移 action）：

- `windowFn` 类型变化（如从 Tumbling 改为 Sliding）
- `accumulationMode` 变化
- `trigger` 类型变化

**检查流程**：

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

## 12. 已知限制

1. **API 层三个方法全部抛 UnsupportedOperationException** — `WindowedStreamImpl.apply()`、`aggregate()`、`reduce()` 全部抛异常（core 模块不依赖 runtime）。需通过 `transform()` 手动构建 `WindowOperator`
2. **SimpleAccumulator.getLocalValue() bug** — `addWindowElement` 在 `accumulator.add` 后存储 `getLocalValue()` 的返回值（如 `Long`），而非 accumulator 本身。导致第二个元素进入时 `instanceof SimpleAccumulator` 失败，落入 last-write-wins。AggregateFunction 最多只能正确累加一次
3. **MapState 覆盖语义** — last-write-wins 导致 ReduceFunction 和 WindowFunction 无法正确累积多值
4. **EvictingWindowOperator 是死代码** — 状态存储代码被注释（`evictingWindowState.add(element)`），`contents` 硬编码为 `null`，Evictor 无法工作
5. **无 Session Window 实现** — `MergingWindowAssigner` 和合并窗口路径已实现，但缺少具体的 Session Window Assigner
6. **Watermark 仅在 Source 结束时发送** — 事件时间窗口实际上只在数据流结束时才触发（`MAX_WATERMARK` 触发所有窗口）。没有持续的 watermark 推进，`ContinuousEventTimeTrigger` 和定时注册的事件时间定时器不会在运行中触发
7. **迟到数据处理无实际意义** — `isWindowLate` 检查依赖 watermark 推进，当前 watermark 只有 `MAX_WATERMARK` 一个值，所有元素都在 watermark 之前到达
