# 305 nop-stream DataStream API 完善（设计审计修订版）

> Plan Status: active
> Last Reviewed: 2026-07-20
> Source: `ai-dev/analysis/2026-07/2026-07-20-nop-stream-dataflow-api-gap-analysis.md`
> Design Alignment: `00-vision.md`（定位）§三、§四、§七；`component-roadmap.md`（组件路线）C1；`core-design.md` §2（DataStream API 定位）
> Related: `304-nop-stream-jobgraph-defect-fix.md`

## Purpose

为 nop-stream 的 StreamModel 编程构造器（Java DataStream API）补齐 ProcessFunction 支持，使其能构造包含有状态处理和定时器的 Transformation DAG。ProcessFunction 是 StreamModel 编译路径的关键基础设施——它让用户定义的复杂有状态逻辑可以表达为 Transformation 节点，经五层管线编译执行。

## Design Alignment

### 本计划与 vision 文档的对齐

| vision 约束 | 对齐情况 | 说明 |
|-------------|---------|------|
| §七「保留（非核心路径）：DataStream API——作为 StreamModel 的编程构造器，不是最终用户的主入口」 | ✅ **对齐** | ProcessFunction 定位为 StreamModel 编程构造器的基础设施，不是独立用户入口 |
| §三#1「图模型为核」 | ✅ **对齐** | ProcessFunction 是通过 `transform()` 注册为 Transformation 节点进入编译管线的 |
| §三#2「模型优先」 | ✅ **对齐** | ProcessOperator 可序列化，纳入 StreamGraph/JobGraph 管线 |
| §四「Non-Goals：不做异步算子」 | ✅ **对齐** | AsyncDataStream 已排除 |
| §四「Non-Goals：不做双流 Join」 | ✅ **对齐** | ConnectedStreams/CoGroup/Join 已排除 |
| §七「聚焦：单流窗口聚合 + CEP + Checkpoint」 | ✅ **对齐** | ProcessFunction 是单流上有状态处理的核心工具 |
| `component-roadmap.md` C1「已完成度：高」 | ⚠️ **改进** | ProcessFunction 填补了 C1 的唯一实质性缺口（定时器+有状态处理入口） |

### 偏离 Design Alignment 的调整

**SideOutput 从本计划移除**（原 Phase 3）。理由：
1. SideOutput 的完整实现需要 StreamGraph/JobGraph/GraphExecutionPlan 三层管线改造——成本远超一个 DataStream API 方法的价值
2. vision §七明确 DataStream API 是「非核心路径」，不应驱动管线层变更
3. `component-roadmap.md` C2（编译管线）的待完善项中没有 SideOutput，说明它不是当前管线能力的缺口
4. 如果 XDSL 声明式模型未来需要侧输出，应先在 StreamModel 层设计，再从 XDSL 和 DataStream API 两个入口同时实现

## Current Baseline

- `ProcessFunction` 不存在。用户无法通过 DataStream API 使用定时器、侧输出、有状态处理。
- `RuntimeContext` 只有 3 个方法（subtask 索引/总数/task 名），无 `getKeyedStateStore()`/`getTimerService()`。
- `StreamingRuntimeContext` 实现同样精简，未持有 keyedStateBackend 或 timerServiceManager 的引用。
- `OneInputStreamOperator` 是唯一的有状态处理入口——但它是框架级 SPI 而非用户级 API。
- `AbstractUdfStreamOperator` 已持有 `userFunction`，`FunctionUtils` 已支持 `RichFunction` 的生命周期回调。

### 确认的 gap

- DataStream API 无法表达有状态处理 + 定时器 + 时间上下文驱动的处理逻辑（ProcessFunction）。

## Goals

- 在 `RuntimeContext` 中增加 `getKeyedStateStore()` 和 `getTimerService()` 方法
- 实现 `ProcessFunction<IN, OUT>` + `KeyedProcessFunction<K, IN, OUT>` 类
- 实现 `ProcessOperator`（包装 ProcessFunction 的 OneInputStreamOperator）
- 在 `DataStream`/`KeyedStream` 中增加 `process()` API 方法
- 端到端验证：ProcessFunction 的 processElement + onTimer 从入口到执行完整路径

## Non-Goals

- SideOutput DataStream API（已裁定为偏离设计对齐，移出 scope）
- ConnectedStreams / TwoInputStreamOperator（vision §四 Non-Goals）
- AsyncDataStream（vision §四 Non-Goals）
- Broadcast State（vision §四 Non-Goals）
- union / coGroup / join / intervalJoin（vision §四 Non-Goals）
- 管线层改造（StreamGraph/JobGraph 结构变更——非 DataStream API 自身职责）

## Scope

### In Scope

1. `RuntimeContext` 扩展（`getKeyedStateStore()`, `getTimerService()`）
2. `ProcessFunction` + `KeyedProcessFunction` 类定义
3. `ProcessOperator`（包装 ProcessFunction 为 OneInputStreamOperator）
4. `DataStream.process()` + `KeyedStream.process()` API

### Out Of Scope

- SideOutput 读取端（`getSideOutput()` + 管线层路由）
- ConnectedStreams / TwoInputStreamOperator（依赖非现有组件）
- 任何管线层（StreamGraph/JobGraph/GraphExecutionPlan）的结构变更

## Execution Plan

### Phase 1 — ProcessFunction 核心实现（合并原 Ph1+Ph2）

Status: planned
Targets: `common/functions/RuntimeContext.java`, `common/functions/ProcessFunction.java`(新建), `common/functions/KeyedProcessFunction.java`(新建), `operators/StreamingRuntimeContext.java`, `operators/ProcessOperator.java`(新建), `datastream/DataStream.java`, `datastream/DataStreamImpl.java`, `datastream/KeyedStream.java`, `datastream/KeyedStreamImpl.java`

- Item Types: `Fix`

- [ ] 在 `RuntimeContext` 中增加 `getKeyedStateStore()` 默认方法（抛 `UnsupportedOperationException`）和 `getTimerService()` 默认方法（抛 `UnsupportedOperationException`）
- [ ] 在 `StreamingRuntimeContext` 中增加 `keyedStateStore` 和 `timerService` 字段及 setter，实现新增方法
- [ ] 创建 `ProcessFunction<IN, OUT>` 抽象类：`processElement(IN, Context, Collector<OUT>)` + `onTimer(long, OnTimerContext, Collector<OUT>)` + 内部类 `Context`（提供 `timestamp()`, `timerService()`, `output(OutputTag, value)`） + `OnTimerContext`
- [ ] 创建 `KeyedProcessFunction<K, IN, OUT>` 继承 `ProcessFunction`：`processElement` 调用前注入当前 key 到 `KeyContext.setCurrentKey()`
- [ ] 创建 `ProcessOperator<IN, OUT>`（继承 `AbstractUdfStreamOperator`，实现 `OneInputStreamOperator` 和 `Triggerable`）：在 `processElement()` 中调用 `userFunction.processElement()`，在 `onEventTime()`/`onProcessingTime()` 中调用 `userFunction.onTimer()`
- [ ] 在 `DataStream<T>` 接口中增加 `process(ProcessFunction<T, R>)` 方法
- [ ] 在 `DataStreamImpl` 中实现 `process()`：创建 `ProcessOperator` 并通过 `transform()` 注册为 Transformation
- [ ] 在 `KeyedStream<T, K>` 接口中增加 `process(KeyedProcessFunction<K, T, R>)` 方法
- [ ] 在 `KeyedStreamImpl` 中实现 `process()`：创建 `ProcessOperator`，确保每次 `processElement` 前调用 `setCurrentKey()`
- [ ] **接线**：`ProcessOperator.open()` 中将 `StreamingRuntimeContext` 的 `keyedStateStore` 和 `timerService` 注入到 `RuntimeContext`（依赖 `operatorChain.open()` 被调用——见 plan 304 Phase 1 的 F2 修复）

Exit Criteria:

> 所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `RuntimeContext` 包含 `getKeyedStateStore()` 和 `getTimerService()`（默认抛 UnsupportedOperationException）
- [ ] `ProcessFunction` 已被 DataStream API 用户正确使用（`DataStream.process()` + `KeyedStream.process()` 编译通过）
- [ ] `ProcessOperator` 正确包装 ProcessFunction 为 OneInputStreamOperator
- [ ] **端到端验证（Anti-Hollow）**：构造 `source → keyBy → process(KeyedProcessFunction with ValueState + timer)` → sink 管线，验证：
  - `processElement` 被每条记录调用
  - `Context.timerService().currentWatermark()` 返回正确值
  - `Context.timerService().registerEventTimeTimer()` 注册的定时器被 `onTimer` 在 watermark 推进后触发
  - `RuntimeContext.getKeyedStateStore().getState()` 返回的 ValueState 可读写
- [ ] **无静默跳过**：`RuntimeContext` 新增默认方法抛 `UnsupportedOperationException`，非空方法体
- [ ] **接线验证**：`ProcessFunction` 确实通过 `ProcessOperator` → `OneInputStreamOperator` → `transform()` → `Transformation` → `StreamGraph` 路径进入编译管线，不绕过管线直接执行
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am` 通过
- [ ] No owner-doc update required（纯新增 API，不影响现有行为）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] ProcessFunction + KeyedProcessFunction 已创建且可运行
- [ ] DataStream.process() + KeyedStream.process() API 已暴露
- [ ] 端到端管线（source → keyBy → process → sink）通过 processElement + onTimer + state 访问的完整验证
- [ ] ProcessFunction 通过 transform() 注册为 Transformation，不绕过五层管线
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：onTimer 和 state 访问在端到端管线上确实工作（不只接口存在，不只在单元测试中存在）
- [ ] `./mvnw compile -pl nop-stream/nop-stream-core -am`
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### SideOutput DataStream API（原 Phase 3）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要 StreamGraph/JobGraph/GraphExecutionPlan 三层管线改造，但 DataStream API 定位为「非核心路径」（vision §七），不应驱动管线层变更。SideOutput 若未来需要，应在 StreamModel 层设计后从 XDSL 和 API 两个入口同时实现。
- Successor Required: `no`

### ConnectedStreams / TwoInputStreamOperator

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: vision §四 Non-Goals 明确排除。需要 TwoInputStreamOperator 组件且 nop-stream 当前无多输入算子运行时基础。
- Successor Required: `no`

### AsyncDataStream / Broadcast State

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: vision §四 Non-Goals 明确排除。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 无。本计划专注于 vision 文档对齐的 ProcessFunction 单一缺口。

## Closure

Status Note: （完成时填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent）
- Evidence: （task id / findings 摘要）

Follow-up:

- （明确写 no remaining plan-owned work）
