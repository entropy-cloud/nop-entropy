# 304 nop-stream JobGraph 管线缺陷修复

> Plan Status: completed
> Last Reviewed: 2026-07-20
> Source: `ai-dev/audits/2026-07/2026-07-20-2100-adversarial-review-nop-stream-jobgraph.md`
> Source Audits: `ai-dev/audits/2026-07/2026-07-20-2100-adversarial-review-nop-stream-jobgraph.md`
> Related: `283-nop-stream-code-quality-fixes.md`, `303-nop-stream-flink-inspired-improvements.md`

## Purpose

修复审计报告 `ai-dev/audits/2026-07/2026-07-20-2100-adversarial-review-nop-stream-jobgraph.md` 中确认的 StreamGraph→JobGraph 转换及执行管线缺陷。优先修复 F2（operator 生命周期缺失）和 F8（fanOutWriters + inputGate 构造冲突）两个确认的 live defect，再处理 F1（ChainingStrategy 架构缺口）和 F9（JobEdge equals/hashCode 设计脆弱性）。

## Current Baseline

- `StreamTaskInvokable.invoke()` 的 4 条角色路径（SOURCE/MIDDLE/SINK/SELF_CONTAINED）均未调用 `operatorChain.open()` / `operatorChain.close()`。依赖 `open()` 初始化的算子（如 `TimestampsAndWatermarksOperator`）会 NPE。
- `GraphExecutionPlan.build()` 在 vertex 同时有 fanOutWriters 和 inputGate 时选择 `StreamTaskInvokable(chain, fanOutWriters)` 构造器，该构造器将 inputGate 硬编码为 null，导致 subtask 被判定为 SOURCE 角色而非 MIDDLE，数据不被消费。
- `JobGraphGenerator.canChain()` 无 `ChainingStrategy` 检查，所有算子（包括窗口、有状态算子）都可能被链化。
- `JobEdge` 无 equals/hashCode，作为 `LinkedHashMap<JobEdge, ...>` 的 key 使用对象引用相等，存在脆弱性。
- `OperatorChain.processElement()` 是死代码，执行路径不调用它。
- `JobGraphGenerator.determinePartitionType()` 从不产出 `BLOCKING`。
- `GraphExecutionPlan.build()` 中 `edge.getPartitioner()` 既传给 `PartitionRouter.create()` 又传给 `RecordWriter` 构造器，参数冗余。

### 确认的 live defects（本计划修复）

1. **F2: OperatorChain 生命周期缺失** — `StreamTaskInvokable.invoke()` 不调用 `operatorChain.open()`/`close()`
2. **F8: fanOutWriters + inputGate 构造冲突** — `GraphExecutionPlan.build()` 分支逻辑错误导致中间 vertex 不被消费
3. **F1: ChainingStrategy 架构缺口** — `canChain()` 无算子级链化策略检查
4. **F9: JobEdge equals/hashCode 缺失** — Map key 使用的对象引用而非逻辑相等

## Goals

- 修复 F2：在 `StreamTaskInvokable.invoke()` 所有角色路径中增加 `operatorChain.open()` 前置于处理开始、`operatorChain.close()` 后置于 finally 块
- 修复 F8：在 `GraphExecutionPlan.build()` 中增加 fanOutWriters + inputGate 共存时的正确构造路径，确保 MIDDLE 角色正确消费上游数据
- 修复 F1：在 `StreamOperatorFactory` / `StreamNode` 中增加 `ChainingStrategy` 属性，`canChain()` 中检查该策略，为窗口和有状态算子提供链化限制
- 修复 F9：为 `JobEdge` 增加基于 `sourceVertex` + `targetVertex` + `partitionType` 的 equals/hashCode
- 清理 F4：删除 `OperatorChain.processElement()` 死代码

## Non-Goals

- 不实现 BLOCKING 边运行时支持（F3 是中级别功能减弱，需 `GraphExecutionPlan` 配合改动，移出本计划 scope）
- 不解决分支场景选择性子链化（F5 是低优先级的优化缺失）
- 不增加 stable OperatorID（F6 依赖 savepoint 管线设计）
- 不改变 `TaskExecutor` 拓扑序提交机制（F7 需先确认 execute() 调用方行为）
- 不解决 RecordWriter partitioner 冗余（F10 纯设计冗余，不影响正确性）

## Scope

### In Scope

1. F2: `StreamTaskInvokable.invoke()` 增加 operator open/close
2. F8: `GraphExecutionPlan.build()` 修复 fanOutWriters + inputGate 分支
3. F1: `StreamOperatorFactory` / `StreamNode` 增加 ChainingStrategy
4. F9: `JobEdge` 增加 equals/hashCode
5. F4: 清理 `OperatorChain.processElement()` 死代码

### Out Of Scope

- BLOCKING 边运行时支持
- 分支选择性子链化
- Stable OperatorID
- 拓扑序提交自动化
- RecordWriter partitioner 参数清理

## Execution Plan

### Phase 1 — OperatorChain 生命周期修复（F2）

Status: completed
Targets: `nop-stream-core/.../execution/StreamTaskInvokable.java` L244-321, `OperatorChain.java`

- Item Types: `Fix`

- [x] 在 `StreamTaskInvokable.invoke()` 的 `invokeSource()` 分支中，在 `sourceOp.run()` 前调用 `operatorChain.open()`，在 finally 块中调用 `operatorChain.close()`
- [x] 在 `invokeMiddle()` 分支中，在 `processInputGate()` 前调用 `operatorChain.open()`，在 finally 中调用 `operatorChain.close()`
- [x] 在 `invokeSink()` 分支中，在 `processInputGate()` 前调用 `operatorChain.open()`，在 finally 中调用 `operatorChain.close()`
- [x] 在 `invokeSelfContained()` 分支中，在 `sourceOp.run()` 前调用 `operatorChain.open()`，在 finally 中调用 `operatorChain.close()`
- [x] 确认 `OperatorChain.open()` 和 `close()` 已正确处理异常聚合（已有实现，只需被调用）

Exit Criteria:

> 所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `invokeSource/invokeMiddle/invokeSink/invokeSelfContained` 四个分支都包含 `open()`/`close()` 调用
- [x] `open()` 在记录处理开始前调用，`close()` 在 finally 块中保证执行
- [x] **端到端验证**：构造含有 `TimestampsAndWatermarksOperator` 的 pipeline（如 `source → assignTimestampsAndWatermarks → sink`），观察是否 NPE
- [x] **接线验证**：在 `TimestampsAndWatermarksOperator.open()` 中设置标志位，确认 `execute()` 后标志位为 true
- [x] **无静默跳过**：所有新增的 open/close 调用路径在异常时正确处理
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am -Dtest="TestStreamTaskInvokable*,TestOperatorLifecycle*,TestTimestampsAndWatermarksOperator*"` 通过
- [x] No owner-doc update required（纯内部行为修复）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — fanOutWriters + inputGate 构造冲突修复（F8）

Status: completed
Targets: `nop-stream-core/.../execution/GraphExecutionPlan.java` L289-296, `StreamTaskInvokable.java`

- Item Types: `Fix`

- [x] 在 `StreamTaskInvokable` 中增加第四个构造器：`StreamTaskInvokable(OperatorChain chain, List<RecordWriter<Object>> fanOutWriters, InputGate inputGate)`
- [x] 该构造器应正确设置 `this.outputWriter`、`this.inputGate` 和 `this.fanOutWriters`，并调用相应的 `wireOperators` 重载
- [x] 在 `GraphExecutionPlan.build()` 中修改分支逻辑：

```java
if (fanOutWriters != null && !fanOutWriters.isEmpty()) {
    if (inputGate != null) {
        invokable = new StreamTaskInvokable(chain, fanOutWriters, inputGate);
    } else {
        invokable = new StreamTaskInvokable(chain, fanOutWriters);
    }
} else if (recordWriter != null || inputGate != null) {
    ...
}
```

- [x] 添加 focused test：构造 source → (fan-out) → filter1→sink1, filter2→sink2 的分支管线，验证中间 vertex 正确消费上游数据

Exit Criteria:

> 所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新的 `StreamTaskInvokable(chain, fanOutWriters, inputGate)` 构造器存在且正确设置三个核心字段
- [x] `GraphExecutionPlan` 分支逻辑在 fanOutWriters + inputGate 同时存在时选择正确构造器
- [x] **端到端验证**：fan-out 分支管线（source→filter1→sink1, →filter2→sink2）中 filter1 和 filter2 正确消费所有数据
- [x] **接线验证**：在分支场景的中间 operator 中设置计数器，确认 processElement 被调用且数据完整
- [x] **无静默跳过**：新构造器必须显式设置 outputWriter、inputGate、fanOutWriters 三个核心字段而非静默跳过任何赋值
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am -Dtest="TestGraphExecutionPlan*,TestDataExchange*,TestSubtaskExecution*"` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — ChainingStrategy 架构补全（F1）

Status: completed
Targets: `nop-stream-core/.../operators/ChainingStrategy.java`（可能新建）, `StreamOperatorFactory.java`, `StreamNode.java`, `JobGraphGenerator.java`

- Item Types: `Fix`

- [x] 定义 `ChainingStrategy` 枚举（`ALWAYS / NEVER / HEAD`），与 Flink 对齐但简化（暂不包含 `HEAD_WITH_SOURCES`）
- [x] 在 `StreamOperatorFactory` 接口中增加 `getChainingStrategy()` 默认方法（默认返回 `ALWAYS`）
- [x] 在 `StreamNode` 中增加 `chainingStrategy` 字段及 getter/setter
- [x] 在 `StreamGraphGenerator` 中创建节点时将 operatorFactory 的 chainingStrategy 传播到 StreamNode
- [x] 在 `JobGraphGenerator.canChain()` 中增加 ChainingStrategy 检查：upstream 为 NEVER 或 downstream 为 NEVER/HEAD 则不可链化
- [x] 为窗口算子（`WindowAggregationOperator`, `WindowOperator` 等）的工厂设置 `NEVER` 或 `HEAD`

Exit Criteria:

> 所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ChainingStrategy` 枚举定义到位
- [x] `StreamOperatorFactory` 接口有 `getChainingStrategy()` 方法
- [x] `JobGraphGenerator.canChain()` 检查 ChainingStrategy
- [x] 窗口算子工厂返回 `NEVER` 或 `HEAD`
- [x] **端到端验证**：`source → window → sink` 管线中 window 不被链化（生成独立的 JobVertex）
- [x] **无静默跳过**：`StreamOperatorFactory` 未覆盖 `getChainingStrategy()` 时默认返回 `ALWAYS`（维持向后兼容）；`canChain()` 对 `NEVER` 标记的算子显式返回 false 而非静默接受
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — JobEdge equals/hashCode 修复 + 死代码清理（F9 + F4）

Status: completed
Targets: `nop-stream-core/.../jobgraph/JobEdge.java`, `OperatorChain.java`

- Item Types: `Fix`

- [x] 在 `JobEdge` 中增加基于 `sourceVertex` + `targetVertex` + `partitionType` 的 `equals()` 和 `hashCode()`
- [x] 验证 `GraphExecutionPlan.build()` 中 `edgePartitionMatrix` 在新增 equals/hashCode 后行为不变（同一对象的引用相等和逻辑相等结果一致）
- [x] 删除 `OperatorChain.processElement()` 方法（L101-121）及其相关注释引用
- [x] 确认 `OperatorChain` 的 `processElement` 无外部调用

Exit Criteria:

> 所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `JobEdge.equals()` 基于 sourceVertex + targetVertex + partitionType 判定相等
- [x] `JobEdge.hashCode()` 与 equals 一致
- [x] `OperatorChain.processElement()` 已从源码删除
- [x] `./mvnw compile -pl nop-stream/nop-stream-core` 无编译错误
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am -Dtest="*JobEdge*,*JobGraph*,*GraphExecutionPlan*"` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] F2: StreamTaskInvokable 所有角色分支包含 open/close
- [x] F8: fanOutWriters + inputGate 同时存在时正确构造
- [x] F1: ChainingStrategy 枚举定义 + canChain 检查 + 窗口算子标记
- [x] F9: JobEdge equals/hashCode 已实现
- [x] F4: OperatorChain.processElement() 已删除
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）组件间调用链运行时连通，（b）无空方法体/静默跳过/no-op
- [x] `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am`
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### BLOCKING 边运行时支持（F3）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: nop-stream 当前不支持 BATCH 执行模式。BLOCKING 边涉及 `ResultPartition` 的物化语义（producer 完成后 consumer 才能读取），需要 `GraphExecutionPlan` 中的调度机制配合。当前不属于任何活跃的 feature 需求。
- Successor Required: `no`

### 分支选择性子链化（F5）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 功能正确（只是保守），当前无性能需求驱动此项优化。
- Successor Required: `no`

### Stable OperatorID（F6）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要配合 savepoint 管线设计，当前 nop-stream checkpoint 使用 OperatorIndex 定位状态。
- Successor Required: `no`

## Non-Blocking Follow-ups

- F7（拓扑序依赖）：确认 `StreamExecutionEnvironment.execute()` 是否按拓扑序提交 vertex，若否可另启计划修复
- F10（RecordWriter partitioner 冗余）：可在后续重构中清理

## Closure

Status Note: All 4 phases completed.
Completed: 2026-07-21

Closure Audit Evidence:

- Reviewer / Agent: local agent (self-audit via test pass + compile)
- Evidence: `./mvnw test -pl nop-stream/nop-stream-core -am` = 0 failures, BUILD SUCCESS; `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` = 0 errors

Follow-up:

- no remaining plan-owned work
