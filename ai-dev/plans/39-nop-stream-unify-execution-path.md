# 39 nop-stream 统一执行路径：去除快速路径，仅保留图模型

> Plan Status: in progress
> Last Reviewed: 2026-05-22
> Source: `ai-dev/design/nop-stream/architecture.md` §3（执行模型统一决策）、`ai-dev/audits/2026-05-21-adversarial-review-nop-stream-design/adversarial-review-design-round1.md` D1（双执行模型缺陷）
> Related: Plan 30（审计修复，将双执行模型归入 deferred）、Plan 26-27（图模型对接）

## Purpose

去除 nop-stream 的双执行路径（快速路径 + 图模型路径），将 `execute()` 统一为仅走图模型路径（Transformation → StreamGraph → JobGraph → Task → TaskExecutor），消除架构分裂和功能不一致问题。

## Current Baseline

- `StreamExecutionEnvironment.execute()` 走快速路径：`executePipeline → buildTransformationChain → instantiateOperators → wireOperatorChain → runSource`（单线程同步，不支持 checkpoint/watermark/savepoint）
- `StreamExecutionEnvironment.executeWithGraphModel()` 走图模型路径（支持 checkpoint、watermark、多 Task 并行）
- 图模型路径存在两个关键缺口（Phase 1 审查发现）：
  - **KeySelector 传播断裂**：`PartitionTransformation` 携带 `KeySelector`，但 `StreamGraphGenerator.transformPartition()` 创建的 StreamNode 不保留 KeySelector；下游 `OneInputTransformation` 的 keySelector 为 null（`KeyedStreamImpl.transform()` 创建 `OneInputTransformation` 时不传 keySelector）。因此 `StreamNode.keySelector` 始终为 null，`StreamTaskInvokable.wireOperators()` 无法获取 KeySelector 信息来插入 `KeyExtractingOutput`
  - **Operator lifecycle 顺序相反**：快速路径 open tail-to-head、close head-to-tail；图模型路径 `OperatorChain` open head-to-tail、close tail-to-head
- 设计文档已更新（`ai-dev/design/nop-stream/` 下 9 个文档）以反映统一执行路径的决策
- Plan 30 已将双执行模型统一归入 deferred

## Goals

- `execute()` 内部统一走图模型路径，删除 `executeWithGraphModel()` 方法
- 删除快速路径的 6 个私有方法及其专属 import
- 修复图模型路径的 KeySelector 传播机制，使 `KeyExtractingOutput` 正确插入
- 统一 Operator lifecycle 顺序
- 所有现有测试通过（含 keyed state 测试和窗口测试）
- 更新比较测试（移除 fast-path leg，保留 graph-model 断言）

## Non-Goals

- 不改变算子的内部逻辑或状态管理实现
- 不增加新的流处理功能（如双流 Join、Side Output）
- 不修改 CEP 独立执行路径（NFA + SharedBuffer 直接使用，不经过 `StreamExecutionEnvironment`）
- 不修改 connector 或 fraud-example 的非执行路径代码

## Scope

### In Scope

- 修复 KeySelector 从 PartitionTransformation → StreamNode → OperatorChain → StreamTaskInvokable 的完整传播链路
- 修复 OperatorChain lifecycle 顺序与快速路径对齐
- 重写 `execute()` 为委托图模型路径
- 删除快速路径代码和 `executeWithGraphModel()`
- 更新所有受影响的测试
- 验证 `TimestampsAndWatermarksTransformation` 在图模型路径中正确处理

### Out Of Scope

- 图模型路径本身的增强（如并行度 > 1、rescaling）— 属于 Plan 30 后续工作
- CEP 模块测试补充 — 属于 Plan 36/37 scope
- WindowOperator 聚合正确性修复 — 属于 Plan 30 scope

## Risks And Rollback

- **KeySelector 传播修复涉及多类修改**：需修改 `StreamGraphGenerator`、`StreamNode`/`StreamEdge`、`JobGraphGenerator`、`OperatorChain`、`StreamTaskInvokable` 多个类。修改面较广，但每步可独立验证。
- **Operator lifecycle 顺序变更**：修改 `OperatorChain.open()/close()` 顺序可能影响依赖 head-to-tail open 顺序的算子（如 WindowOperator 的 state backend 初始化）。需在修改后运行全量测试验证。
- **多 Sink 管线行为变化**：快速路径对多 Sink 有隐含 bug（共享上游算子每个 Sink 运行一次），图模型路径通过 DAG 去重正确处理。这是 bug 修复，不是回归。
- 回退策略：每个 Phase 独立提交，可按 Phase 粒度 revert。

## Execution Plan

### Phase 1 - 修复 KeySelector 传播机制（前置条件）

Status: completed
Targets: `StreamGraphGenerator.java`（`io.nop.stream.core.graph`）、`StreamNode.java`（`io.nop.stream.core.graph`）、`StreamEdge.java`（`io.nop.stream.core.graph`）、`JobGraphGenerator.java`（`io.nop.stream.core.jobgraph`）、`OperatorChain.java`（`io.nop.stream.core.jobgraph`）、`StreamTaskInvokable.java`（`io.nop.stream.core.execution`）

- Item Types: `Fix`

**问题分析**：在 `keyBy().map()` 链中，`PartitionTransformation` 携带 `KeySelector`，但 `OneInputTransformation.keySelector` 为 null。`StreamGraphGenerator.transformPartition()` 创建的 StreamNode 不保留 KeySelector。信息传播链完全断裂。

**选定方案**：后处理 + StreamNode 存储方案。不修改 StreamEdge，在 `StreamGraphGenerator.generate()` 完成后做一次后处理，将 KeySelector 写入下游 StreamNode。`JobGraphGenerator` 从 StreamNode 列表收集 KeySelector（跳过 Partition 节点），按 chain 内算子索引对齐。

**KeySelector 映射逻辑**（处理 Partition 节点被过滤的情况）：

```
StreamGraph 中的链: [Source] → [Map] → [Partition(keySelector=ks)] → [Reduce] → [Sink]
                                         ↑ Partition 的 createStreamOperator() 返回 null

后处理: 将 Partition 节点上的 keySelector 传递给其**出边的 target 节点**（即 Reduce 节点）

JobGraph chain 中的 StreamNode 列表（Partition 被过滤）:
  [Source, Map, Reduce, Sink]  ← KeySelector 列表索引 0=Source, 1=Map, 2=Reduce, 3=Sink
  KeySelector 列表:            [null,  null,  ks,     null]

StreamTaskInvokable.wireOperators():
  算子 2 (Reduce) 实现了 KeyContext → KeySelector[2] = ks → 插入 KeyExtractingOutput
```

- [ ] **StreamGraphGenerator 后处理**：在 `generate()` 方法末尾新增后处理步骤——遍历所有 StreamEdge，如果 source 节点的 operatorFactory 是 PartitionOperatorFactory（或等价判断），从该节点对应的 PartitionTransformation 中提取 KeySelector，设置到 target 节点的 `keySelector` 字段
- [ ] **JobGraphGenerator 层**：修改 `createJobVertex()` 方法，在构建 `OperatorChain` 时，从 chain 中各 StreamNode（Partition 节点已被过滤，不会出现在 chain 中）的 keySelector 收集为 `List<KeySelector<?,?>>`（等长，null 表示无 key selector），传入 `OperatorChain` 构造函数
- [ ] **OperatorChain 层**：修改构造函数，新增 `List<KeySelector<?,?>>` 参数并存储，提供 getter
- [ ] **StreamTaskInvokable 层**：修改 `wireOperators()` 方法，从 `OperatorChain` 获取 KeySelector 列表，当算子实现 `KeyContext` 且对应位置 KeySelector 非 null 时，在 `ChainingOutput` 前插入 `KeyExtractingOutput`
- [ ] 编写/更新单元测试：验证 KeySelector 从 PartitionTransformation 正确传播到 StreamTaskInvokable 的 wireOperators 逻辑

Exit Criteria:

- [ ] `StreamGraphGenerator` 生成的 StreamGraph 中，keyBy 下游的 StreamNode.keySelector 非 null
- [ ] `OperatorChain` 持有 KeySelector 列表，可通过 getter 访问
- [ ] `StreamTaskInvokable.wireOperators()` 正确插入 `KeyExtractingOutput`
- [ ] `TestGraphModelExecution` 中使用 `executeWithGraphModel()` 的 keyed 操作测试通过（如 `testKeyByMapSinkGraphModel`）
- [ ] **接线验证**：在图模型路径运行 keyed state 测试时，`KeyExtractingOutput` 被实际调用（通过 keyed state 测试的输出正确性间接验证）
- [ ] No owner-doc update required（此 Phase 不改变公开行为，仅修复图模型路径内部正确性）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 修复 OperatorChain lifecycle 顺序

Status: completed
Targets: `OperatorChain.java`（`io.nop.stream.core.jobgraph`）

- Item Types: `Fix`

**问题分析**：快速路径 open tail-to-head（从 sink 到 source），close head-to-tail（从 source 到 sink）。图模型路径 `OperatorChain` 顺序相反。需统一为快速路径的顺序（与 Flink 语义一致：先 open 下游再 open 上游，先 close 上游再 close 下游）。

- [ ] 修改 `OperatorChain.open()` 遍历顺序为 tail-to-head（`for (i = operators.size()-1; i >= 0; i--)`）
- [ ] 修改 `OperatorChain.close()` 遍历顺序为 head-to-tail（`for (operator : operators)`）
- [ ] 验证 `TimestampsAndWatermarksTransformation` 在图模型路径中通过 `StreamGraphGenerator.transformTimestampsAndWatermarks()` 正确处理（此 Transformation 生成的 StreamNode 在 chain 中的位置是否影响 lifecycle）

Exit Criteria:

- [ ] `OperatorChain.open()` 按 tail-to-head 顺序调用各算子 open
- [ ] `OperatorChain.close()` 按 head-to-tail 顺序调用各算子 close
- [ ] 图模型路径已有的所有测试通过（含 runtime 模块的 checkpoint 测试）
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 重写 execute() 并更新测试（原子提交）

Status: completed
Targets: `StreamExecutionEnvironment.java`（`io.nop.stream.core.environment`）、`TestGraphModelExecution.java`、`TestE2ESimplePipeline.java`、`TestDataStreamPipeline.java`、`TestKeyedStreamAggregation.java`、`TestWindowedStreamAggregation.java`、`TestSessionWindowIntegration.java`、`TestEventTimeWindowE2E.java`、`TestAssignTimestampsAndWatermarks.java`

- Item Types: `Fix`

**原子性要求**：删除 `executeWithGraphModel()` 和更新测试调用必须在同一次提交中完成，否则编译断档。

- [ ] 将 `executeWithGraphModel(String jobName)` 的逻辑移入 `execute(String jobName)`，删除对 `executePipeline` 的调用
- [ ] 删除 `executeWithGraphModel()` 和 `executeWithGraphModel(String)` 两个方法
- [ ] 删除快速路径的 6 个私有方法：`executePipeline`、`buildTransformationChain`、`extractKeySelectors`、`instantiateOperators`、`wireOperatorChain`、`runSource`
- [ ] 删除仅被快速路径使用的 import（逐一确认无其他引用后删除：`ChainingOutput`、`Input`、`KeyContext`、`KeyExtractingOutput`、`StreamSinkOperator`、`StreamSourceOperator`、`TimestampsAndWatermarksOperator`、`TimestampsAndWatermarksTransformation`、`PartitionTransformation`、`OneInputTransformation`、`HashMap`）
- [ ] `TestGraphModelExecution`：将所有 `executeWithGraphModel()` 调用改为 `execute()`；移除 `testGraphModelMatchesFastPath` 和 `testMultiChainMatchesFastPath` 中的 fast-path 比较逻辑，改为纯 graph-model 断言
- [ ] `TestE2ESimplePipeline`：移除 fast-path 测试分支，仅保留 graph-model 路径断言
- [ ] 确认以下原 fast-path 测试在统一路径下通过：`TestDataStreamPipeline`、`TestKeyedStreamAggregation`、`TestWindowedStreamAggregation`、`TestSessionWindowIntegration`、`TestEventTimeWindowE2E`、`TestAssignTimestampsAndWatermarks`
- [ ] 确认 runtime 模块所有测试通过（`TestCheckpointEndToEnd`、`TestCheckpointRecovery` 等）
- [ ] 确认 cep 模块所有测试通过
- [ ] 确认 connector 模块所有测试通过

Exit Criteria:

- [ ] `StreamExecutionEnvironment` 不再包含 `executeWithGraphModel`、`executePipeline`、`buildTransformationChain`、`instantiateOperators`、`wireOperatorChain`、`runSource` 方法
- [ ] `execute()` 内部走图模型路径（StreamGraph → JobGraph → TaskExecutor）
- [ ] 代码中不再存在对 `executeWithGraphModel` 的调用（grep 验证）
- [ ] 所有 `nop-stream-core` 测试通过（`./mvnw test -pl nop-stream/nop-stream-core -am`）
- [ ] 所有 `nop-stream-runtime` 测试通过
- [ ] 所有 `nop-stream-cep` 测试通过
- [ ] 所有 `nop-stream-connector` 测试通过
- [ ] **端到端验证**：`env.addSource().map().keyBy().window().aggregate().sink()` 通过 `execute()` 完整跑通
- [ ] **端到端验证**：checkpoint 管线通过 `execute()` 完整跑通（barrier 注入→快照→恢复）
- [ ] No owner-doc update required（设计文档已在 Plan 拟制前更新）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 独立 Closure Audit

Status: planned
Targets: 全部变更文件

- Item Types: `Proof`

- [ ] 独立子 agent 执行 closure audit（不同 task_id），验证所有 Phase 的 Exit Criteria

Exit Criteria:

- [ ] 独立子 agent 确认：`execute()` 走图模型路径，快速路径代码已完全删除
- [ ] 独立子 agent 确认：KeySelector 传播链路完整（从 PartitionTransformation 到 KeyExtractingOutput）
- [ ] 独立子 agent 确认：全量测试通过（`./mvnw test -pl nop-stream -am`）
- [ ] 独立子 agent 确认：无空壳代码、无静默跳过
- [ ] 独立子 agent 确认：代码中不存在对已删除方法的引用（grep 验证）
- [ ] Closure audit 证据已记录到本 plan 的 Closure section
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 快速路径代码已完全删除（6 个方法 + `executeWithGraphModel` + 专属 import）
- [ ] KeySelector 传播链路完整修复（StreamGraphGenerator → OperatorChain → StreamTaskInvokable）
- [ ] OperatorChain lifecycle 顺序与快速路径一致
- [ ] 所有测试通过：`./mvnw test -pl nop-stream -am`
- [ ] `./mvnw compile -pl nop-stream -am` 通过
- [ ] 不存在对已删除方法的引用（grep 验证 `executeWithGraphModel`、`executePipeline`、`buildTransformationChain`、`wireOperatorChain`、`runSource`）
- [ ] 端到端验证：source→map→keyBy→window→aggregate→sink 通过 `execute()` 完整跑通
- [ ] 端到端验证：checkpoint 管线通过 `execute()` 完整跑通
- [ ] Anti-Hollow Check：closure audit 验证调用链从 `execute()` 到算子到 sink 连通
- [ ] 独立子 agent closure audit 已完成并记录证据
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Deferred But Adjudicated

### 图模型路径并行度 > 1 支持

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前所有 Transformation parallelism=1，并行度增强是独立功能开发，不影响统一执行路径的正确性
- Successor Required: yes
- Successor Path: `ai-dev/plans/` 后续计划（component-roadmap.md 阶段 6）

### 算子链精细控制（disableChaining / startNewChain）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前自动链化规则足够，精细控制是增强功能
- Successor Required: no

## Non-Blocking Follow-ups

- runtime 模块中 `GraphModelCheckpointExecutor` 的代码清理（去除重复逻辑）

## Closure

Status Note: <<执行完成后填写>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent task id>>
- Evidence: <<审查发现摘要>>
