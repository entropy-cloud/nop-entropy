# nop-stream JobGraph 管线审计报告

> Status: reviewed
> Audit Status: closed
> Date: 2026-07-20
> Scope: nop-stream-core `JobGraphGenerator.java`, `OperatorChain.java`, `StreamTaskInvokable.java`, `TaskExecutor.java`, `GraphExecutionPlan.java` 与 Flink `StreamingJobGraphGenerator.java`, `StreamTask.java` 对比
> Auditor: manual code review
> Plan: `ai-dev/plans/304-nop-stream-jobgraph-defect-fix.md`

## Context

在完成 `ai-dev/analysis/nop-stream-flink-comparison-deep-dive.md` 后，用户要求深入审查 nop-stream 的 StreamGraph→JobGraph 转换和 JobGraph 执行两个关键管线的正确性。本报告聚焦于**功能正确性问题**和**结构化缺陷**，而非代码风格或性能优化。

对照基准：Flink master 分支 `StreamingJobGraphGenerator.java`（2230 行）、`StreamTask.java`（2161 行）。

## 审计发现

---

### Finding 1: 链化条件缺失 ChainingStrategy（严重）

**文件**: `JobGraphGenerator.java` L258-295

**现状**: `canChain()` 检查 6 个结构性条件（并行度、分区器、source/sink 边界、单出边、单入边），但**没有任何算子级别的链化策略**。

**Flink 对比**: `StreamingJobGraphGenerator.isChainable()` 增加了 `ChainingStrategy` 枚举检查：
- `ALWAYS` — 可链化
- `NEVER` — 不可链化
- `HEAD` — 只能作为链头，不可被链到上游
- `HEAD_WITH_SOURCES` — 只能与 source 链化

**影响**: 窗口算子（`WindowOperator`）、有状态算子通常应标记为 `NEVER` 或 `HEAD`，nop-stream 没有这个机制，它们会被随意链化到上下游。这导致：
- 窗口算子的 state backend 与上下游算子共享同一个 task 内的 key context，可能触发 state key 冲突
- checkpoint barrier 对齐粒度过粗（应该以 window 为边界）

**复现方式**: 构造 `source → window → sink` 管线，观察 JobGraph 是否将三者链化为一个 vertex。

**建议**: 在 `StreamOperatorFactory` 或 `StreamNode` 中增加 `ChainingStrategy` 属性，`canChain()` 中检查该策略。

---

### Finding 2: OperatorChain 生命周期缺失 open/close 调用（严重）

**文件**: `StreamTaskInvokable.java` L244-321

**现状**: `StreamTaskInvokable.invoke()` 有 4 种角色路径（SOURCE/MIDDLE/SINK/SELF_CONTAINED），但**没有任何一条路径调用 `operatorChain.open()` 或 `operatorChain.close()`**。

Operator 的 `open()` 仅在 `OperatorChain.open()` 中被定义（L136-161），但从未被调用。同样 `close()` 也从未被调用。

**Flink 对比**: `StreamTask.invoke()` 有完整的生命周期：
1. `beforeInvoke()` → 创建 mailbox
2. `init()` → 初始化 operator chain
3. `openAllOperators()` → 调用每个 operator 的 `open()`
4. 处理循环 → mailbox 驱动的处理
5. `afterInvoke()` / `cleanUp()` → `close()` 所有 operator

**影响**: 依赖 `open()` 初始化的算子（如 `TimestampsAndWatermarksOperator` 中创建 timestampAssigner/watermarkGenerator）可能在构造函数中已经初始化了，但**依赖 operator chain 上下文**（如 `setProcessingTimeService()`、`setKeyedStateBackend()`）的初始化不会发生——因为这些 setter 在 `open()` 之前调用。如果某个 operator 在 `open()` 中访问 `processingTimeService` 或 `keyedStateBackend`，就会遇到 null 指针。

**实际案例**: `AbstractStreamOperator.open()` 是空方法（L60），但子类 `TimestampsAndWatermarksOperator.open()` 覆盖了它。`AbstractStreamOperator.processingTimeService` 声明于 L32（默认 null），`open()` 中 `scheduleNextWatermarkTimer()` 访问 `processingTimeService`（L69），如果 `open()` 没被调用就会 NPE。

**修复**: 在 `StreamTaskInvokable.invoke()` 的每个角色分支中，在开始处理前调用 `operatorChain.open()`，在 finally 块中调用 `operatorChain.close()`。

---

### Finding 3: GraphExecutionPlan 不产出 BLOCKING 边（中）

**文件**: `JobGraphGenerator.java` L523-531

**现状**: `determinePartitionType()` 只有两个输出：
- `partitioner == null` → `PIPELINED`
- `partitioner != null` → `PIPELINED_BOUNDED`

`ResultPartitionType.BLOCKING` 枚举存在但从不产出。

**Flink 对比**: Flink 的 `StreamExchangeMode` 支持 `BATCH` 模式，在 `StreamingJobGraphGenerator` 中对应 `ResultPartitionType.BLOCKING`。当 `ExecutionMode` 为 BATCH 或算子的 `ExchangeMode` 为 BATCH 时产出 BLOCKING 边。

**影响**: 当前 nop-stream 的所有 JobGraph 边都是 PIPELINED 模式。BLOCKING 模式需要 producer 完成后 consumer 才能读取（先物化再消费），支持 batch/批处理语义。nop-stream 的 `GraphExecutionPlan.build()` 不从 JobGraph 中读取 `ResultPartitionType` 来决定 ResultPartition 的行为（ResultPartition 本身没有类型区分），所以即使产出 BLOCKING 也不会正确执行。

**建议**: 要么移除未使用的 `BLOCKING` 枚举避免误导，要么在 `GraphExecutionPlan` 中增加 BLOCKING 边的物化物支持。

---

### Finding 4: OperatorChain.processElement 逐一遍历算子（中）

**文件**: `OperatorChain.java` L101-121

**现状**: `processElement()` 遍历所有 operator，依次调用 `Input.processElement(record)`，用同一 record 喂给每个算子。这意味着在 `StreamTaskInvokable` 的执行模型中，chain 内的算子接收同一个 record。

**正确性分析**: 实际上 `StreamTaskInvokable.wireOperators()`（L110-146）做了真正的链化接线：相邻算子之间通过 `ChainingOutput` 连接（前一个的输出是后一个的输入），`processElement()` 本身**在运行时并未被实际调用**——处理记录的是 `processInputGate()` → `headInput.processElement()` → head operator 处理 → 通过 ChainingOutput 输出到下一个 operator。

所以 `processElement()` 是**死代码（dead code）**，不会被实际执行路径调用。这不仅不会导致错误，反而是一个独立的正确性验证：operator 链的运行时行为完全由 `wireOperators()` 中的 ChainingOutput 接线决定，不依赖 `processElement()`。

**建议**: 删除 `processElement()` 以避免混淆，或将其改为内部调试用途。

---

### Finding 5: 分支场景链化过于保守（低）

**文件**: `JobGraphGenerator.java` L221-231

**现状**: `buildChain()` 检查 `outgoingEdges.size() == 1`，当节点有多条出边时立即中断链化。

```
source → map (并行度2)
         ├── filter1 → sink1
         └── filter2 → sink2
```

在这个场景中，map 会因为它有两条出边而不与 filter1 链化。Flink 的 `createChain()` 则会将出边分为 chainableOutputs/nonChainableOutputs，map→filter1 链化，map→filter2 走边。

**影响**: 功能正确（不会产生错误结果），但错过链化优化机会。这不是 defect，而是优化缺失。

**建议**: P2 级别优化。重构 `buildChain()` 以支持选择性子链化。

---

### Finding 6: JobGraph 中 Operator ID 缺失（低）

**文件**: `JobVertex.java` L56-172, `StreamNode.java`

**现状**: `JobVertex` 只包含 `OperatorChain`（operator 实例列表），没有任何 `OperatorID` 或 `OperatorIDPair`。`StreamNode` 使用的是 Integer 类型的节点 ID。

**Flink 对比**: Flink 的 `JobVertex` 包含 `List<OperatorIDPair>`，记录了链中每个 operator 的可追溯 ID。这对于 savepoint 恢复、状态映射、跨版本兼容性至关重要。

**影响**: 当前 nop-stream 的 savepoint/checkpoint 使用 `OperatorIndex`（链中位置）来定位 operator 状态。如果 chain 结构变化（如增加一个 filter），operator 索引移位会导致状态映射错误。

**建议**: P2 级别。在 `OperatorChain` 中增加 stable `OperatorID` 支持。

---

### Finding 7: TaskExecutor 提交顺序依赖调用方保证拓扑序（低）

**文件**: `TaskExecutor.java` L140-172, `GraphExecutionPlan.java` L152-321

**现状**: `TaskExecutor.submitJobVertex()` 是独立的提交操作。`GraphExecutionPlan` 返回按拓扑排序的 vertex 列表，但**调用方需要在外部按序提交**。若调用方不按序提交，source 和 sink 可能乱序执行。

**当前调用方**: 在 `StreamExecutionEnvironment.execute()` 中，如果正确遍历 `GraphExecutionPlan.getSortedVertexIds()` 并按序提交，则不会出现问题。需确认 `execute()` 的实现在运行时的提交顺序。

**Flink 对比**: Flink 的 `ScheduleMode` 有 `LAZY_FROM_SOURCES`（source 就绪即调度，下游被上游数据触发）和 `EAGER`（所有一起调度）。nop-stream 当前是主动调度，依赖调用方。

**建议**: 在 `TaskExecutor` 中增加拓扑序内部保证，或显式文档化调用方责任。

---

### Finding 8: GraphExecutionPlan 中 fanOutWriters 与 inputGate 构造函数选择冲突（严重）

**文件**: `GraphExecutionPlan.java` L289-296, `StreamTaskInvokable.java` L75-83

**现状**: 当一个 vertex 同时有 **多条出边**（fanOutWriters != null）**和** 入边（inputGate != null）时——即 MIDDLE 角色且 >1 条出边——代码走 L290-291: `new StreamTaskInvokable(chain, fanOutWriters)`。但此构造器将 `inputGate` 硬编码为 null（`StreamTaskInvokable.java:81`），且调用的 `wireOperators(fanOutWriters)`（L148-197）完全不处理 inputGate。

```
GraphExecutionPlan.java L289-296:
    if (fanOutWriters != null && !fanOutWriters.isEmpty()) {
        invokable = new StreamTaskInvokable(chain, fanOutWriters);  // ← inputGate = null
    } else if (recordWriter != null || inputGate != null) {
        invokable = new StreamTaskInvokable(chain, recordWriter, inputGate);
    } else {
        invokable = new StreamTaskInvokable(chain);
    }
```

**影响**: 这个 subtask 的 role 被判定为 `SOURCE`（`getRole()` L99-100: `outputWriter != null && inputGate == null → SOURCE`），但实际它应该消费上游数据（有 inputGate）。SOURCE 路径 `invokeSource()` 不会调用 `processInputGate()` → **上游数据永远不会被消费**，管线死锁或数据丢失。

**触发条件**: parallelism > 1 且某个中间 vertex 的 outgoing edges > 1（fan-out 分支）。当前版本 nop-stream 的 `GraphExecutionPlan` 在 parallelism=1 时走 `fanOutWriters.size() > 1` 吗？需要验证：`GraphExecutionPlan` L226-267 中 `fanOutWriters` 的数量等于 outgoing edges 数量。在 `parallelism=1` 场景下仍可能触发 `fanOutWriters != null && !fanOutWriters.isEmpty()`。所以**单并行度分支场景也会触发**。

**建议**: 在 fanOutWriters + inputGate 同时存在时增加第四个构造器或重构现有的分支逻辑：

```java
if (fanOutWriters != null && !fanOutWriters.isEmpty()) {
    if (inputGate != null) {
        invokable = new StreamTaskInvokable(chain, fanOutWriters, inputGate); // 新增构造器
    } else {
        invokable = new StreamTaskInvokable(chain, fanOutWriters);
    }
}
```

---

### Finding 9: JobEdge 缺失 equals/hashCode 导致 Map key 脆弱性（中）

**文件**: `JobEdge.java` L42-139, `GraphExecutionPlan.java` L182

**现状**: `GraphExecutionPlan.build()` 使用 `LinkedHashMap<JobEdge, ResultPartition[][]>`（L182）作为 edge→partition 矩阵的映射。但 `JobEdge` **没有实现 `equals()`/`hashCode()`**，使用默认的 Object 引用相等。

当前实现中，`edgePartitionMatrix` 的 `put` 和 `get` 使用同一 `jobGraph.getEdges()` 迭代产生的相同 `JobEdge` 对象引用，因此能正确工作。但若任何代码创建**逻辑等价但对象不同**的 `JobEdge`（如反序列化、深度复制、重建 graph），则 `edgePartitionMatrix.get(newEdge)` 返回 null，导致索引丢失。

**建议**: 在 `JobEdge` 中增加基于 `sourceVertex` + `targetVertex` + `partitionType` 的 `equals()`/`hashCode()`，消除此设计脆弱性。

---

### Finding 10: RecordWriter 构造器中 partitioner 参数冗余（低）

**文件**: `GraphExecutionPlan.java` L238-244

**现状**: 同一个 `edge.getPartitioner()` 既传给 `PartitionRouter.create()`（L241）又传给 `RecordWriter` 构造器（L243-244）。`PartitionRouter` 已经根据 `PartitionPolicy` 决定了路由策略，`RecordWriter` 似乎也通过 `router` 决定 emit 目标。两者并存可能导致控制流混淆。

**建议**: 审查 `RecordWriter` 是否真的需要独立 `partitioner` 参数，或可移除以消除冗余。当前不影响正确性，只是设计冗余。

---

| 编号 | 类型 | 文件 | 严重程度 | 当前是否构成 defect |
|------|------|------|---------|-------------------|
| F1 | 链化条件缺失 ChainingStrategy | JobGraphGenerator.java | 严重 | ⚠️ 可能 defect（取决于具体算子） |
| F2 | OperatorChain 生命周期缺失 open/close | StreamTaskInvokable.java | 严重 | ✅ 确认 defect（operator 依赖 processingTimeService 时会 NPE） |
| F8 | fanOutWriters + inputGate 构造冲突 | GraphExecutionPlan.java, StreamTaskInvokable.java | 严重 | ✅ 确认 defect（分支场景下数据不消费） |
| F3 | 不产出 BLOCKING 边 | JobGraphGenerator.java | 中 | ⚠️ 功能减弱（影响 batch 语义） |
| F4 | processElement 死代码 | OperatorChain.java | 中 | ⚠️ 代码混淆（不影响正确性） |
| F9 | JobEdge 缺失 equals/hashCode | JobEdge.java, GraphExecutionPlan.java | 中 | ⚠️ 设计脆弱（跨序列化场景） |
| F5 | 分支场景链化保守 | JobGraphGenerator.java | 低 | ❌ 优化缺失（不影响正确性） |
| F6 | Operator ID 缺失 | JobVertex.java | 低 | ❌ 优化缺失（savepoint 兼容性） |
| F7 | 拓扑序依赖 | TaskExecutor.java | 低 | ❌ 设计决策（需确认调用方正确） |
| F10 | RecordWriter partitioner 冗余 | GraphExecutionPlan.java | 低 | ❌ 设计冗余（不影响正确性） |

## Conclusion

在基本管线（source→map→filter→sink，无 window，无状态，无分支且 parallelism=1）的端到端场景中，nop-stream 的 StreamGraph→JobGraph→GraphExecutionPlan 管线**功能正确**。存在三个需要优先修复的 defect：

1. **F2（生命期缺失）** 是确认的 live defect，可能在 `TimestampsAndWatermarksOperator` 等依赖 `open()` 的算子中引发 NPE
2. **F8（fanOutWriters + inputGate 构造冲突）** 是确认的 live defect，在分支场景 + 有入边的中间 vertex 中导致数据不被消费
3. **F1（ChainingStrategy）** 是架构缺口，虽当前未触发（窗口算子路径走新版 WindowOperator），但中长期需要补齐

另外，**F9（JobEdge equals/hashCode）** 是设计层面的脆弱性，虽当前未触发，但在涉及 JobEdge 序列化或复制时可能引发难以追踪的 bug。

## References

- `nop-stream-core/.../jobgraph/JobGraphGenerator.java` — 链化逻辑
- `nop-stream-core/.../execution/StreamTaskInvokable.java` — 执行生命周期
- `nop-stream-core/.../jobgraph/OperatorChain.java` — 算子链实现
- `nop-stream-core/.../execution/TaskExecutor.java` — 执行器
- `nop-stream-core/.../execution/GraphExecutionPlan.java` — 执行计划
- `~/sources/flink/.../streaming/api/graph/StreamingJobGraphGenerator.java` — Flink 对照
- `~/sources/flink/.../streaming/runtime/tasks/StreamTask.java` — Flink 对照
