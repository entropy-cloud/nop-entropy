# 28 nop-stream 时间模型与执行引擎集成

> Plan Status: proposed
> Last Reviewed: 2026-05-20
> Source: `ai-dev/design/nop-stream/time-model-design.md`（status "active（未对接）"）、`checkpoint-design.md` §10.3（watermark propagation 未对接）
> Related: `27-nop-stream-cross-task-data-exchange.md`（跨 Task 数据交换，watermark 传播依赖此计划）

## Purpose

将时间模型（TimestampAssigner / WatermarkGenerator / WatermarkStrategy）集成到执行引擎，使事件时间窗口（EventTime Window）在运行时正确触发。完成后，`DataStream.assignTimestampsAndWatermarks()` 可在 API 层声明时间策略，运行时自动提取时间戳、生成 Watermark、驱动窗口触发。

## Current Baseline

### 时间模型 API（已存在，未对接）

- `WatermarkStrategy<T>` 接口：`createTimestampAssigner()` + `createWatermarkGenerator()`，已有多个实现（`BoundedOutOfOrdernessWatermarks`、`AscendingTimestampsWatermarks`）
- `TimestampAssigner<T>` 接口：`extractTimestamp(element, recordTimestamp)` → long
- `WatermarkGenerator<T>` 接口：`onEvent()` + `onPeriodicEmit()` → 通过 `WatermarkOutput` 发射 watermark
- `WatermarkOutput` 接口：`emitWatermark()`，已有 `WatermarkOutputMultiplexer` 实现多路复用

### DataStream API

- `DataStream` 无 `assignTimestampsAndWatermarks()` 方法 — 无法在 API 层声明时间策略
- `SingleOutputStreamOperator` 无 ` WatermarkStrategy` 存储字段
- `StreamGraphGenerator` 不处理时间策略转换

### 执行引擎

- `StreamTaskInvokable` 当前在 source 完成后手动发射 `MAX_WATERMARK`，无周期性 watermark 生成
- `AbstractStreamOperator` 无 timestamp 提取逻辑
- `TimestampsAndWatermarksOperator` 不存在
- `WindowOperator` 已有完整窗口逻辑（触发器、驱逐器、状态管理），但依赖 `Watermark` 驱动触发
- `InternalTimerService<N>` 接口已定义（`registerEventTimeTimer` / `currentWatermark`），但无具体实现类（如 `HeapInternalTimerService`）
- `AbstractStreamOperator.processWatermark()` 中 `timeServiceManager.advanceWatermark(mark)` 被注释掉 — watermark 无法推进定时器
- 无 `TimerServiceManager` 或等效的定时器管理组件

### Checkpoint

- `StreamElement` 包含 `Watermark` 类型，`Output.emitWatermark()` 已实现
- 跨 Task 场景下 watermark 传播由 Plan 27 的 InputGate 处理

## Goals

- `DataStream` API 新增 `assignTimestampsAndWatermarks(WatermarkStrategy)` 方法
- 运行时自动从数据元素提取事件时间戳并设置到 `StreamRecord`
- 运行时周期性调用 `WatermarkGenerator.onPeriodicEmit()` 生成 Watermark
- Watermark 沿算子链正确传播（递减），驱动 `WindowOperator` 触发窗口计算
- 事件时间窗口（TumblingEventTimeWindows / SlidingEventTimeWindows）在端到端场景中正确触发

## Non-Goals

- 不实现 watermark alignment（`WatermarkAlignmentParams`）— 优化特性，后续跟进
- 不实现 idle source 检测（`WatermarksWithIdleness`）— 优化特性，后续跟进
- 不实现 processing time 语义的窗口（仅事件时间）
- 不实现自定义 Timer Service（仅使用 `WindowOperator` 内建的 timer）

## Scope

### In Scope

- `DataStream.assignTimestampsAndWatermarks()` API
- `TimestampsAndWatermarksOperator` 实现（提取时间戳 + 调用 WatermarkGenerator）
- 周期性 watermark 生成（集成到 `StreamTaskInvokable` 的主循环）
- Watermark 在算子链中的传播（通过 `Output.emitWatermark()`）
- 事件时间窗口端到端测试

### Out Of Scope

- Watermark alignment / idleness
- Processing time 语义
- 自定义 Timer Service
- Watermark 传播的跨 Task 优化（Plan 27 范围）

## Risks And Rollback

- **风险**：周期性 watermark 生成需要一个定时器或心跳机制。当前 `StreamTaskInvokable` 的 source 循环是阻塞的。缓解：在 source 的 `run()` 循环中周期调用 `WatermarkGenerator.onPeriodicEmit()`，或在 source 完成后一次性发射（对 bounded source 足够）
- **风险**：`HeapInternalTimerService` 是全新实现，需要正确处理 key namespace 和定时器去重。缓解：接口已定义（`InternalTimerService<N>`），实现遵循接口契约，通过单元测试验证
- **依赖风险**：Phase 4 的多链端到端测试依赖 Plan 27（跨 Task 数据交换）。缓解：Phase 1-3 和 Phase 4 的单链测试可独立执行，多链测试在 Plan 27 完成后补充
- **回滚策略**：时间模型集成通过新增算子实现，不影响现有执行路径。`assignTimestampsAndWatermarks()` 是可选调用

## Execution Plan

### Phase 1 - API 层与 StreamGraph 集成

Status: planned
Targets: `nop-stream-core`（datastream 包、transformation 包、streamgraph 包）

- Item Types: `Proof`

在 DataStream API 层添加时间策略声明能力，并在 StreamGraph 中表示。

- [ ] `DataStream<T>` 新增 `assignTimestampsAndWatermarks(WatermarkStrategy<T>)` 方法
- [ ] 方法内部创建 `TimestampsAndWatermarksTransformation`，插入到 transformation 链中
- [ ] `StreamGraphGenerator` 处理 `TimestampsAndWatermarksTransformation`：创建对应 `StreamNode`，在 `StreamGraph` 中连接到上下游
- [ ] `StreamGraph` 的 `StreamNode` 携带 `WatermarkStrategy` 配置

Exit Criteria:

- [ ] `dataStream.assignTimestampsAndWatermarks(strategy)` 不报错，返回新的 DataStream
- [ ] 生成的 StreamGraph 包含时间戳提取算子的 StreamNode
- [ ] 新增单元测试验证 StreamGraph 生成
- [ ] `./mvnw test -pl nop-stream/nop-stream-core` 通过

### Phase 2 - TimestampsAndWatermarksOperator 实现

Status: planned
Targets: `nop-stream-core`（operator 包、streamrecord 包）

- Item Types: `Proof`

实现时间戳提取和 watermark 生成的算子。

- [ ] 新增 `TimestampsAndWatermarksOperator<T>` extends `AbstractStreamOperator<T>` implements `OneInputStreamOperator<T, T>`
- [ ] `processElement()`：调用 `TimestampAssigner.extractTimestamp()` → 将时间戳设置到 `StreamRecord.setTimestamp()` → 调用 `WatermarkGenerator.onEvent()`
- [ ] `processWatermark()`：直接转发到下游（watermark 递减语义由下游处理）
- [ ] 周期性 watermark 生成：在 `processElement()` 中检查是否到达发射周期（基于元素计数或系统时间），到达时调用 `WatermarkGenerator.onPeriodicEmit()`
- [ ] source 完成时：调用 `onPeriodicEmit()` 最后一次，然后发射 `MAX_WATERMARK`

Exit Criteria:

- [ ] `TimestampsAndWatermarksOperator` 能从 StreamRecord 提取时间戳
- [ ] 能根据 WatermarkStrategy 周期性生成 Watermark
- [ ] 生成的 Watermark 通过 `output.emitWatermark()` 传播到下游
- [ ] 新增单元测试验证时间戳提取和 watermark 生成
- [ ] `./mvnw test -pl nop-stream/nop-stream-core` 通过

### Phase 3 - Timer Service 实现与 WindowOperator 对接

Status: planned
Targets: `nop-stream-core`（windowing 包、operator 包）

- Item Types: `Proof`

实现 `HeapInternalTimerService` 并将其集成到 `AbstractStreamOperator`，使 Watermark 能驱动 WindowOperator 的窗口触发。

- [ ] 新增 `HeapInternalTimerService<N>` implements `InternalTimerService<N>`：基于 `TreeMap<Long, Set<N>>` 管理事件时间定时器，`advanceWatermark()` 时触发所有 timestamp ≤ watermark 的定时器
- [ ] 新增 `TimerServiceManager`：管理每个算子的 `HeapInternalTimerService` 实例，`advanceWatermark()` 推进所有注册的 timer service
- [ ] 取消注释 `AbstractStreamOperator.processWatermark()` 中的 `timeServiceManager.advanceWatermark(mark)`
- [ ] 验证 `WindowOperator.processWatermark()` 通过 timer service 触发到期窗口的 `onEventTime()` 回调
- [ ] 窗口触发后正确调用 WindowFunction 产出结果

Exit Criteria:

- [ ] Watermark 推进时，`WindowOperator` 能触发到期窗口的 `onEventTime()` 回调
- [ ] 窗口触发后正确调用 WindowFunction 产出结果
- [ ] 新增单元测试：注入 watermark → 验证窗口触发
- [ ] `./mvnw test -pl nop-stream/nop-stream-core` 通过

### Phase 4 - 端到端测试与文档更新

Status: planned
Targets: `nop-stream-runtime`（test）、`ai-dev/design/nop-stream/`

- Item Types: `Proof`、`Follow-up`

- [ ] 端到端测试（单链，可独立执行）：`env.fromCollection(data).assignTimestampsAndWatermarks(strategy).map(fn).addSink(collector)` — 验证时间戳提取和 watermark 传播在单链场景下正确
- [ ] 端到端测试（多链，依赖 Plan 27）：`env.fromCollection(data).assignTimestampsAndWatermarks(strategy).keyBy(k -> k).window(TumblingEventTimeWindows.of(Time.milliseconds(100))).reduce(fn)` — 验证窗口按事件时间正确触发
- [ ] 端到端测试：SlidingEventTimeWindows 场景（依赖 Plan 27）
- [ ] 端到端测试：迟到数据处理（allowedLateness）（依赖 Plan 27）
- [ ] 更新 `time-model-design.md`：status 从 "active（未对接）" 改为 "active（已对接）"
- [ ] 更新 `graph-model-design.md` §9：时间模型集成状态
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] 事件时间窗口端到端测试通过
- [ ] 设计文档已更新
- [ ] `./mvnw test -pl nop-stream` 全通过

## Closure Gates

- [ ] `DataStream.assignTimestampsAndWatermarks()` API 可用
- [ ] 事件时间窗口（Tumbling / Sliding）在端到端场景中正确触发
- [ ] Watermark 沿算子链正确传播
- [ ] 不存在被静默降级的 in-scope live defect
- [ ] 独立子 agent closure-audit 已完成
- [ ] `./mvnw test -pl nop-stream`
- [ ] checkstyle / 代码规范检查通过

## Non-Blocking Follow-ups

- Watermark alignment 实现
- Idle source 检测
- Processing time 语义
- 自定义 Timer Service（事件时间 / 处理时间）
- 周期性 watermark 的精确定时（ ScheduledExecutorService）
