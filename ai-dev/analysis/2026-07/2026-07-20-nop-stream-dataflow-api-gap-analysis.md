# nop-stream DataStream API 完整性分析

> Status: resolved
> Date: 2026-07-20
> Scope: nop-stream-core datastream/ + common/functions/ 与 Flink DataStream API 对比
> Conclusion: ProcessFunction + SideOutput 为 P1 缺口，基础设施就绪，已纳入 plan 305
> Plan: `ai-dev/plans/305-nop-stream-datastream-api-completeness.md`

## Context

前序分析已覆盖 nop-stream 的图模型管线（`ai-dev/audits/2026-07/2026-07-20-2100-adversarial-review-nop-stream-jobgraph.md`）、Timer/Window/Checkpoint 子系统（`ai-dev/analysis/nop-stream-flink-comparison-deep-dive.md`）。本轮聚焦于 **DataStream API 表面的完整性**——用户直接接触的编程接口是否完备。

对照基准：Flink 1.20 `DataStream.java` / `KeyedStream.java` / `WindowedStream.java` / `ProcessFunction.java` / `ConnectedStreams.java`。

---

## 分析

### 1. Flink DataStream API 概览（作为基线）

Flink DataStream API 的关键方法分组：

| 分组 | Flink 方法 | 用途 |
|------|-----------|------|
| 基础转换 | `map`, `filter`, `flatMap` | 最常用算子 |
| 分区 | `keyBy`, `shuffle`, `rebalance`, `rescale`, `partitionCustom`, `broadcast` | 数据分发 |
| 时间 | `assignTimestampsAndWatermarks` | 事件时间分配 |
| 多流 | `union`, `connect`, `coGroup`, `join`, `intervalJoin` | 多流合并/关联 |
| 侧输出 | `sideOutput`, `getSideOutput` | 分流 |
| 拆分 | `split`, `select` | 历史 API，新版本用 side output |
| 迭代 | `iterate` | 迭代计算 |
| 扩展 | `process` (ProcessFunction), `transform` (低阶) | 自定义有状态处理 |
| 异步 | `AsyncDataStream.unorderedWait/orderedWait` | 非阻塞外部调用 |
| 广播 | `broadcast` (对 BroadcastStream) | 动态配置分发 |

### 2. nop-stream 当前 API 覆盖

| 方法 | nop-stream 状态 | Flink 等价 | 差距 |
|------|---------------|-----------|------|
| `map` | ✅ `DataStreamImpl.map()` | 一致 | 无 |
| `filter` | ✅ `DataStreamImpl.filter()` | 一致 | 无 |
| `flatMap` | ✅ `DataStreamImpl.flatMap()` | 一致 | 无 |
| `keyBy` | ✅ `DataStreamImpl.keyBy()` | 一致 | 无 |
| `assignTimestampsAndWatermarks` | ✅ `DataStreamImpl.assignTimestampsAndWatermarks()` | 一致 | 无 |
| `transform` | ✅ `DataStreamImpl.transform()` | 一致 | 无 |
| `reduce` | ✅ `KeyedStreamImpl.reduce()` | 一致 | 无 |
| `window` | ✅ `KeyedStreamImpl.window()` | 一致 | 无 |
| `sum/min/max` | ✅ `KeyedStreamImpl.sum/min/max()` | 一致 | 无 |
| `process` (window) | ✅ `WindowedStreamImpl.process()` | ProcessWindowFunction | 无 |
| `print` / `sink` / `collect` | ✅ | 一致 | 无 |
| **`process` (on DataStream)** | ❌ **缺失** | `DataStream.process(ProcessFunction)` | **核心缺口** |
| **`union`** | ❌ **缺失** | `DataStream.union(DataStream...)` | **缺失** |
| **`connect`** | ❌ **缺失** | `DataStream.connect(DataStream)` → `ConnectedStreams` | **缺失** |
| **`sideOutput`** | ❌ **缺失** | `SingleOutputStreamOperator.getSideOutput(OutputTag)` | **接口存在但未暴露** |
| **`broadcast`** | ❌ **缺失** | `DataStream.broadcast(MapStateDescriptor)` | 缺失 |
| **`shuffle` / `rebalance` / `rescale`** | ❌ **缺失** | 分区器直接调用 | 缺失（仅内部 PartitionPolicy 支持） |
| **`iterate`** | ❌ **缺失** | 迭代处理 | 缺失 |
| **`AsyncDataStream`** | ❌ **缺失** | 异步 I/O | 缺失 |
| `coGroup` / `join` / `intervalJoin` | ❌ **缺失** | 窗口连接 | 缺失 |
| `split` / `select` | ❌ **缺失** | 分流（旧 API） | 缺失 |

**核心结论**：基础算子完备，但所有高级 API 均缺失。

---

### 3. ProcessFunction：最大的单一缺口

#### Flink 的 ProcessFunction

`ProcessFunction<IN, OUT>` 是 Flink 最强大的通用处理接口：

```java
// Flink ProcessFunction
public abstract class ProcessFunction<I, O> extends AbstractRichFunction {
    // 每条记录的处理入口
    public abstract void processElement(I value, Context ctx, Collector<O> out);
    
    // 处理时间定时器触发
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<O> out) {}
    
    // Context 提供：TimerService、sideOutput、当前处理时间/watermark
    public abstract class Context {
        public abstract Long timestamp();
        public abstract TimerService timerService();
        public abstract <X> void output(OutputTag<X> outputTag, X value);
    }
}
```

#### nop-stream 的等价物

nop-stream **没有 `ProcessFunction`**。当前最接近的是 `OneInputStreamOperator` 接口——但它是**算子级接口**（面向框架扩展开发者），不是**用户级 API**（面向业务逻辑开发者）。区别在于：

| 维度 | ProcessFunction | OneInputStreamOperator |
|------|----------------|----------------------|
| 身份 | 用户级 API | 框架级 SPI |
| 签名 | 简单（value, context, collector） | 复杂（StreamRecord, Output） |
| Timer 访问 | 通过 Context 自动注入 | 需手动实现 Triggerable + InternalTimerService |
| State 访问 | 通过 RuntimeContext 自动获取 | 需手动注入 keyedStateBackend |
| Side output | 通过 Context.output() | 需手动调用 output.collect(tag, record) |

#### 影响评估

**严重**。ProcessFunction 是 Flink 中使用最广的核心 API，几乎所有非简单 ETL 的 Flink 作业都依赖它。在 nop-stream 中，用户只能通过两种方式编写有状态逻辑：

1. **MapFunction/FilterFunction + 状态变量**（通过 RichFunction 的 RuntimeContext.getState()）——可以访问 ValueState/ListState 等，但无法访问定时器、无法侧输出
2. **自定义 OneInputStreamOperator**——功能完整但需要理解 StreamRecord/Output/CheckpointBarrier 等框架内部类型，开发成本高

**具体缺失能力**：
- 业务逻辑中按 key 注册 event time / processing time 定时器
- 定时器触发时执行业务逻辑（如超时判断、会话合并）
- 主输出以外的 side output 分流
- 获取当前 processing time / watermark

---

### 4. ConnectedStreams / CoGroup / Join：多流操作缺失

#### Flink 的多流机制

```java
// ConnectedStreams — 两条同/异类型流的合并
ConnectedStreams<IN1, IN2> connected = stream1.connect(stream2);
connected.map(new CoMapFunction<IN1, IN2, OUT>() { ... });
connected.flatMap(new CoFlatMapFunction<IN1, IN2, OUT>() { ... });
connected.process(new CoProcessFunction<IN1, IN2, OUT>() { ... });

// Windowed CoGroup / Join — 按窗口关联
stream1.coGroup(stream2).where(...).equalTo(...).window(TumblingEventTimeWindows.of(...));
stream1.join(stream2).where(...).equalTo(...).window(TumblingEventTimeWindows.of(...));
```

#### nop-stream 的现状

`CoMapFunction` 和 `CoFlatMapFunction` 接口**存在**（`common/functions/co/` 下），但**没有任何 `connect()` API 暴露给用户**，也没有 `ConnectedStreams`、`CoProcessFunction`、`CoGroupedStreams`、`JoinedStreams` 等类。

同样，`union` 操作用于合并多条同类型流，在 nop-stream 的 XDSL 声明式模型中通过 `<union>` 节点支持，但 **DataStream API 中没有 `union()` 方法**。

#### 影响评估

**中**。对 nop-stream 当前定位（声明式流处理为主、DataStream API 为编程辅助）影响较小，但如果有用户选择 DataStream API 构建复杂管线，多流操作是关键缺失。

---

### 5. Side Output：写入端存在，读取端缺失

nop-stream 有完整的 `OutputTag` 类和 `Output.collect(OutputTag, record)` 方法（用于**写入**侧输出），`ProcessWindowFunction` 的 `Context` 也提供了 `output()` 方法。但 `SingleOutputStreamOperator` 上**没有 `getSideOutput()` 方法**（无法**读取**侧输出），使得用户无法在 DataStream API 中消费侧输出。

两层缺口：
1. `SingleOutputStreamOperator` 接口只有 `forceNonParallel()`，没有 `getSideOutput(OutputTag<X>) -> DataStream<X>`
2. 算子执行时侧输出数据需要通过 `Output.collect(tag, record)` 写入，但下游如何区分主流 vs 侧流并路由到正确出口的逻辑尚未实现

#### 影响评估

**中**。侧输出在 Flink 中常用于分流异常数据、迟到数据、监控事件等场景。OutputTag 和 Output.collect() 等基础设施存在，但两侧（写入端 Consumer→OutputTag → 读取端 SingleOutputStreamOperator.getSideOutput）**都不完整**。

---

### 6. 异步 I/O（AsyncDataStream）

#### Flink 的异步 I/O

```java
AsyncDataStream.unorderedWait(
    stream, new AsyncFunction<IN, OUT>() { ... },
    timeout, TimeUnit.SECONDS, capacity);
```

用于对外部系统（数据库、Redis、HTTP API）的非阻塞异步调用，是 Flink 中处理 enrichment 场景的标准模式。

#### nop-stream 现状

**完全缺失**。没有 `AsyncFunction` 接口，没有 `AsyncDataStream` 工具类，没有 `AsyncWaitOperator`。

#### 影响评估

**低-中**。异步 I/O 是常用的 enrichment 模式，但可以通过自定义 `OneInputStreamOperator` + Java `CompletableFuture` 实现。不过如果目标是 DataStream API 完整性，这是一个可见缺口。

---

### 7. Broadcast State

#### Flink 的 Broadcast State

```java
MapStateDescriptor<String, Rule> ruleState = new MapStateDescriptor<>("rules", ...);
BroadcastStream<Rule> ruleStream = env.fromCollection(rules).broadcast(ruleState);
dataStream.connect(ruleStream).process(new BroadcastProcessFunction<>() { ... });
```

用于将动态配置/规则分发给所有并行实例。

#### nop-stream 现状

**完全缺失**。无 `BroadcastStream`，无 `BroadcastProcessFunction`，无 `BroadcastStateDescriptor`。

#### 影响评估

**低**。这是特定场景需求（动态规则更新），不影响核心管线。

---

### 8. 分区器 API 暴露

Flink 提供了 `shuffle()` / `rebalance()` / `rescale()` / `broadcast()` / `partitionCustom()` 等高阶分区 API。nop-stream 的 `PartitionRouter` 内部支持 `FORWARD / HASH / REBALANCE / BROADCAST`，但 DataStream 层只暴露了 `keyBy()`（对应 HASH 分区）。

#### 影响评估

**低**。分区策略多通过 `keyBy()` 隐式确定，显式分区器使用场景有限。

---

## Conclusion

### 核心缺口（影响最大的三项）

| 缺口 | 已有基础 | 实现成本 | 影响 | 优先级 |
|------|---------|---------|------|--------|
| **ProcessFunction** | OneInputStreamOperator, RichFunction, KeyedStateStore, OutputTag, InternalTimerService | **中（2-3天）** | 高 | **P1** |
| **SideOutput API** | OutputTag, Output.collect(tag, record) 完全存在 | **极低（<半天）** | 高 | **P1** |
| **ConnectedStreams** | CoMapFunction, CoFlatMapFunction 接口存在 | **中（1-2天）** | 中 | **P2** |

### ProcessFunction 的实现路径

nop-stream 实现 `ProcessFunction` 的**基础设施已经完备**，这是实现成本低的核心原因：

```
已有: OneInputStreamOperator <-- 已有框架级 SPI
已有: AbstractUdfStreamOperator <-- 已有，可包装 UDF
已有: RichFunction + RuntimeContext <-- 已有，提供状态访问
已有: KeyedStateStore <-- 已有，提供 ValueState/ListState/MapState
已有: InternalTimerService <-- 已有，提供 timer 管理
已有: OutputTag + Output.collect(tag, record) <-- 已有，侧输出机制
已有: TimestampedCollector <-- 已有，带时间戳的 collector

缺失: ProcessFunction 类定义（约 80 行）
缺失: ProcessOperator（包装 ProcessFunction，约 100 行）  
缺失: DataStream.process() API 方法（约 15 行）
```

**关键设计决策**：`ProcessFunction` 的运行时需要将 `InternalTimerService` 和 `KeyedStateBackend` 注入到 operator 中。当前 `AbstractUdfStreamOperator` 已经通过 `StreamingRuntimeContext` 提供了 `getState()` 等操作，但 timer 服务尚未通过 `RuntimeContext` 暴露。需要：
1. 在 `RuntimeContext` 中增加 `TimerService timerService()` 方法
2. 实现 `ProcessOperator` 包装 `ProcessFunction`
3. 在 `DataStreamImpl` 中增加 `process()` 方法

### SideOutput API 的实现路径

```
已有: OutputTag 类 - 完全存在
已有: Output.collect(OutputTag, StreamRecord) - 完全存在（写入端）
已有: ProcessWindowFunction.Context.output(OutputTag, value) - 完全存在

缺失: SingleOutputStreamOperator.getSideOutput(OutputTag) -> DataStream - 不存在
缺失: 侧输出流在 JobGraph/GraphExecutionPlan 中的路由逻辑 - 不存在
```

实现需要两层工作：
1. API 层：在 `SingleOutputStreamOperator` 中增加 `getSideOutput(OutputTag<X>)` 方法，返回一个新的 `DataStream<X>`，内部创建一个 `SideOutputTransformation`
2. 管线层：`StreamGraphGenerator` 增加 `SideOutputTransformation` → `StreamNode` 的转换，以及 `JobGraphGenerator` 中的路由处理
3. 运行时层：`StreamTaskInvokable` 需要将 `Output.collect(tag, record)` 的调用路由到正确的下游 InputGate 或 RecordWriter

**非纯粹 API 封装**，涉及到管线改造（StreamGraph/JobGraph/GraphExecutionPlan 三层），成本约 1-2 天。

### 被否决的方案

- **不优先实现 AsyncDataStream**：需要 `AsyncWaitOperator` + 线程池管理 + 顺序/无序输出控制，实现成本较高（3-5天），且 nop-stream 当前定位是声明式流处理，enrichment 场景可通过 Xpl 函数调用实现。
- **不优先实现 Broadcast State**：需要 `BroadcastProcessFunction` + `BroadcastStateBackend`，实现成本约 2-3 天，场景较窄。

## Open Questions

- [ ] nop-stream 的 DataStream API 与 XDSL 声明式模型的定位关系应该如何划定？是 DataStream API 作为"所有功能的主入口"还是"声明式模型的编程辅助"？这个定位决定了 ProcessFunction 等 API 的实现优先级。
- [ ] `ConnectedStreams` 需要 `TwoInputStreamOperator`——nop-stream 当前只有 `OneInputStreamOperator`，`TwoInputStreamOperator` **不存在**（仅被 `StreamOperator.java` Javadoc 提及，类未定义）。实现 connect 前必须先创建 `TwoInputStreamOperator` 接口及对应的 `AbstractTwoInputStreamOperator`。
- [ ] Broadcast State 完全缺失：无 `BroadcastProcessFunction`、`BroadcastStream`、`BroadcastStateDescriptor`。实现成本约 2-3 天。

## References

- `nop-stream-core/.../datastream/DataStream.java` — API 接口定义
- `nop-stream-core/.../datastream/DataStreamImpl.java` — 已有实现
- `nop-stream-core/.../common/functions/co/CoMapFunction.java` — ConnectedStreams 接口
- `nop-stream-core/.../common/functions/co/CoFlatMapFunction.java` — ConnectedStreams 接口
- `nop-stream-core/.../common/functions/ProcessWindowFunction.java` — 窗口版 process 已存在
- `nop-stream-core/.../common/functions/RichFunction.java` — 富函数接口
- `nop-stream-core/.../util/OutputTag.java` — 侧输出标签
- `nop-stream-core/.../operators/Output.java` — 侧输出方法已存在
