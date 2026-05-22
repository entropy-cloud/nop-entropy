# 时间模型与 Watermark 设计

> Status: active（**已对接执行路径**）
> Created: 2026-05-19
> Updated: 2026-05-22（统一执行路径，去除快速路径相关描述）
> Parent: `architecture.md` §3（执行模型）、`window-design.md` §6（Timer Service）

## 1. 定位

nop-stream 的时间模型定义了事件时间（event time）语义的基础：如何为记录分配时间戳、如何推进 watermark、以及 watermark 如何驱动窗口触发和定时器。这套子系统直接从 Flink 移植，接口完整，已与统一的图模型执行路径集成。

### 1.1 设计决策

**选了什么**：Flink 风格的 WatermarkStrategy 统一抽象，将时间戳分配和 watermark 生成合并为一个策略接口。

**为什么不用其他方案**：
- Source 自带 watermark（老式 AssignerWithPeriodicWatermarks）—— 耦合度高，不支持多 Source 场景
- 全局时钟（Processing Time only）—— 无法处理乱序事件，窗口语义不精确

## 2. 时间语义

nop-stream 支持两种时间语义：

| 语义 | 含义 | 驱动方式 | 当前状态 |
|---|---|---|---|
| **事件时间** | 事件实际发生的时间（记录中的 timestamp） | Watermark 推进 | 抽象完整，未集成 |
| **处理时间** | 处理端收到事件的时间（系统时钟） | 系统时钟 | CountTrigger、ProcessingTimeTrigger 可用 |

事件时间是窗口和 CEP 的核心语义。Watermark 是事件时间推进的标记——它告诉下游算子"不会再有 timestamp < watermark 的事件到达"。

## 3. 核心抽象

### 3.1 WatermarkStrategy

`WatermarkStrategy<T>` 是时间模型的统一入口，同时承担时间戳分配和 watermark 生成的职责：

```
WatermarkStrategy<T>
  ├── extends TimestampAssignerSupplier<T>    // 时间戳分配
  ├── extends WatermarkGeneratorSupplier<T>   // watermark 生成
  │
  ├── createTimestampAssigner(context) → TimestampAssigner<T>
  ├── createWatermarkGenerator(context) → WatermarkGenerator<T>
  │
  ├── withTimestampAssigner(assigner) → WatermarkStrategy<T>   // 装饰器
  ├── withIdleness(timeout) → WatermarkStrategy<T>            // 空闲检测
  └── withWatermarkAlignment(group, drift) → WatermarkStrategy<T>  // 对齐
```

**工厂方法**（生成预定义策略）：

| 方法 | 生成策略 | 适用场景 |
|---|---|---|
| `forMonotonousTimestamps()` | `AscendingTimestampsWatermarks` | 时间戳严格递增 |
| `forBoundedOutOfOrderness(Duration)` | `BoundedOutOfOrdernessWatermarks` | 乱序但有界 |
| `forGenerator(WatermarkGeneratorSupplier)` | 自定义 WatermarkGenerator | 完全自定义 |
| `noWatermarks()` | `NoWatermarksGenerator` | 不需要事件时间 |

### 3.2 TimestampAssigner

| 接口 | 方法 | 含义 |
|---|---|---|
| `TimestampAssigner<T>` | `extractTimestamp(element, recordTimestamp)` | 从事件中提取时间戳 |
| `RecordTimestampAssigner<T>` | 直接返回上游已分配的 recordTimestamp（无则返回 Long.MIN_VALUE） | 默认实现（如 Kafka 记录自带时间戳） |

**关键约定**：`NO_TIMESTAMP = Long.MIN_VALUE`，表示记录没有时间戳。

### 3.3 WatermarkGenerator

WatermarkGenerator 是 watermark 生成的核心接口，有两种回调：

| 方法 | 调用时机 | 用途 |
|---|---|---|
| `onEvent(event, timestamp, output)` | 每条记录到达时 | 跟踪最大时间戳、可能立即发射 watermark |
| `onPeriodicEmit(output)` | 周期性调用 | 根据积累的信息发射 watermark |

**两种生成模式**：
- **Punctuated**（基于事件）：在 `onEvent` 中直接发射 watermark
- **Periodic**（基于周期）：在 `onPeriodicEmit` 中发射 watermark，间隔由 `autoWatermarkInterval` 控制

### 3.4 WatermarkOutput

WatermarkOutput 是 watermark 发射的输出接口：

| 方法 | 含义 |
|---|---|
| `emitWatermark(watermark)` | 发射一个 watermark（也隐式标记为 active） |
| `markIdle()` | 标记为空闲（下游不再等待此输出的 watermark） |
| `markActive()` | 标记为活跃 |

**空闲检测**：如果一个 Source 长时间没有数据，可以 `markIdle()` 让下游不等待它。通过 `withIdleness(Duration)` 自动实现。

## 4. Watermark 生成策略

### 4.1 AscendingTimestampsWatermarks

```
onEvent(event, timestamp, output):
    // 跟踪最大时间戳（继承自 BoundedOutOfOrdernessWatermarks，outOfOrderness = 0）

onPeriodicEmit(output):
    output.emitWatermark(maxTimestamp - 1)  // maxTimestamp - outOfOrderness - 1, outOfOrderness = 0
```

假设时间戳严格递增，watermark 紧跟最新时间戳（实际为 `maxTimestamp - 1`，因为同一时间戳的事件可能仍然到达）。延迟仅为周期性发射间隔。

### 4.2 BoundedOutOfOrdernessWatermarks

```
maxTimestamp = Long.MIN_VALUE + outOfOrderness + 1

onEvent(event, timestamp, output):
    maxTimestamp = Math.max(maxTimestamp, timestamp)

onPeriodicEmit(output):
    output.emitWatermark(maxTimestamp - outOfOrderness - 1)
```

假设事件最多乱序 `outOfOrderness` 毫秒。watermark = 当前最大时间戳 - 乱序界。这保证了在 watermark 之后不会再有 timestamp > watermark + outOfOrderness 的迟到事件。

**AscendingTimestampsWatermarks 是 BoundedOutOfOrdernessWatermarks 的子类**，`outOfOrderness = 0`。

### 4.3 NoWatermarksGenerator

不生成任何 watermark。用于不需要事件时间的场景。

## 5. Watermark 传播

### 5.1 Watermark 对象

| 属性 | 类型 | 含义 |
|---|---|---|
| `timestamp` | long | watermark 的时间戳 |
| `MAX_WATERMARK` | 常量 | `Long.MAX_VALUE`，表示流结束 |

Watermark 单调递增——一旦发出 timestamp 为 T 的 watermark，后续的 watermark 必须 > T。

### 5.2 传播机制

Watermark 通过算子的 `Output` 接口传播：

```
Source → [TimestampsAndWatermarksOperator] → 算子链
              │
              ├── 提取时间戳 → 设置到 StreamRecord
              ├── 生成 watermark → 通过 output.emitWatermark() 发射
              └── 下游算子的 processWatermark() 接收
```

**WatermarkStatus** 传播：除了 watermark 本身，还有 `WatermarkStatus`（ACTIVE / IDLE）随流传播，告诉下游是否需要等待此输入。

### 5.3 WatermarkOutputMultiplexer

用于多分区/多 split 场景的 watermark 合并。将多个输入的 watermark 取最小值后输出（木桶效应——最慢的输入决定全局 watermark）。

支持即时（immediate）和延迟（deferred）两种输出模式。延迟模式在 `onPeriodicEmit` 时才合并更新。

### 5.4 CombinedWatermarkStatus

跟踪多个输入的 watermark + idle 状态，计算组合 watermark：

- 所有输入 active → 取最小 watermark
- 部分输入 idle → 只从 active 输入取最小
- 所有输入 idle → 输出也 idle

## 6. TimestampsAndWatermarksOperator

runtime 模块中的 `TimestampsAndWatermarksOperator` 是时间模型与算子链的桥梁。

### 6.1 职责

| 操作 | 实现 |
|---|---|
| 时间戳分配 | `processElement` 中调用 `timestampAssigner.extractTimestamp()`，设置到 StreamRecord |
| Watermark 生成 | `processElement` 中调用 `watermarkGenerator.onEvent()` |
| 周期性 watermark | `onProcessingTime` 中调用 `watermarkGenerator.onPeriodicEmit()`，并注册下一个定时器 |
| 结束时最终 watermark | `finish()` 中调用 `onPeriodicEmit()` |
| 上游 watermark 透传 | `processWatermark` 只透传 `MAX_WATERMARK`，其他忽略（因为本算子自己生成 watermark） |

### 6.2 构造参数

| 参数 | 含义 |
|---|---|
| `watermarkStrategy` | WatermarkStrategy 实例 |
| `emitProgressiveWatermarks` | 是否发射渐进 watermark。false 时使用 NoWatermarksGenerator |

### 6.3 WatermarkEmitter（内部类）

`WatermarkEmitter` 实现 `WatermarkOutput`，确保 watermark 单调递增：

- `emitWatermark(watermark)` — 如果 timestamp <= currentWatermark，忽略；否则更新并发送到下游
- `markIdle()` / `markActive()` — 向下游发送 WatermarkStatus

### 6.4 周期性发射

`open()` 中注册定时器，按 `watermarkInterval` 间隔周期调用 `onProcessingTime` → `onPeriodicEmit`。**当前 `watermarkInterval = 0L`**（硬编码），因此周期性发射不生效。

## 7. 与执行路径的集成

> **Updated: 2026-05-22** — 统一执行路径后，TimestampsAndWatermarksOperator 在 `execute()` 中自动插入。

`execute()` 通过图模型路径执行时，`TimestampsAndWatermarksTransformation` 在 StreamGraph 生成阶段被正确处理，`TimestampsAndWatermarksOperator` 作为 Source 和下游算子之间的独立节点插入算子链：

```
Source → TimestampsAndWatermarksOperator → Map → Window → Sink
           ↑ 提取时间戳 + 生成 watermark
           ↑ 周期性调用 onPeriodicEmit
```

**Watermark 推进机制**：
1. `TimestampsAndWatermarksOperator.open()` 注册定时器，按 `watermarkInterval` 间隔周期调用 `onPeriodicEmit`
2. 每条记录到达时，`onEvent()` 跟踪最大时间戳
3. Watermark 单调递增，通过 `Output.emitWatermark()` 向下游传播
4. 下游算子的 `processWatermark()` 接收并处理 watermark
5. 流结束时 `finish()` 发射最终 watermark

## 8. 与 Flink 的差异

| 维度 | Flink | nop-stream |
|---|---|---|
| WatermarkStrategy | 完整实现，4 个工厂方法 + 对齐 + 空闲 | 完整移植（接口和策略类一致） |
| TimestampsAndWatermarksOperator | 自动插入，watermarkInterval 可配置 | 存在但未插入，watermarkInterval 硬编码为 0 |
| Watermark 对齐 | 分布式对齐（Source → Coordinator → Tasks） | 接口存在（`withWatermarkAlignment`），但无 Coordinator |
| 多 Source watermark 合并 | 通过 InputGate + StatusWatermarkValve | WatermarkOutputMultiplexer 存在，但未接入执行路径 |
| 自定义 WatermarkGenerator | 完整支持 | 接口完整，可使用 |

## 9. 已知限制

1. **未与执行路径集成** — TimestampsAndWatermarksOperator 不在 execute() 的算子链中。当前只有 Source 结束时的 MAX_WATERMARK
2. **watermarkInterval 硬编码为 0** — 周期性 watermark 发射不生效。需要从配置中读取（如 Flink 的 `ExecutionConfig.autoWatermarkInterval`）
3. **Watermark 对齐未实现** — `withWatermarkAlignment()` 接口存在，但 `WatermarksWithWatermarkAlignment` 内部需要 Coordinator 通信，nop-stream 无此基础设施
4. **CEP 的 currentWatermark() 返回 Long.MIN_VALUE** — CEP 使用独立执行路径，不感知 watermark 推进。FraudDetectionDemo 通过 `advanceTime()` 直接使用传入的 timestamp 绕过
5. **WatermarkOutputMultiplexer 未使用** — 单 Source 场景不需要多路合并，但多 Source 场景会需要
