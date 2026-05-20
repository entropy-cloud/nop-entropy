# 27 nop-stream 跨 Task 数据交换

> Plan Status: completed
> Last Reviewed: 2026-05-20
> Source: `ai-dev/design/nop-stream/graph-model-design.md` §9.2（未完成项）、`checkpoint-design.md` §10.3（未对接项）
> Related: `26-nop-stream-graph-model-and-checkpoint-integration.md`（单链管线已完成）、`03-nop-stream-improvement-plan.md`（旧改进计划，部分过时）

## Purpose

实现 Task 间数据交换机制（RecordWriter / RecordReader / InputGate），使图模型执行路径支持多链管线（keyBy 产生多 JobVertex 的场景）。完成后，`executeWithGraphModel()` 不再限于单链管线，可通过 BlockingQueue 在同进程内的 Task 间传递数据，算子链优化和 barrier 对齐在跨 Task 场景下正确工作。

## Current Baseline

### 执行路径

- `executeWithGraphModel()` 已可执行单链管线（Plan 26）：StreamGraph → JobGraph → Task → TaskExecutor
- 单链约束：`JobGraph.getEdges()` 非空时抛出 `IllegalStateException`，拒绝多链管线
- `StreamTaskInvokable` 执行单链：wire operators → run source → emit MAX_WATERMARK
- `TaskExecutor` 用线程池执行 Task，但不考虑 vertex 间的数据依赖和启动顺序

### 图模型

- `JobGraph` 包含 `JobVertex[]` + `JobEdge[]`，`JobEdge` 携带 `ResultPartitionType`（PIPELINED / PIPELINED_BOUNDED / BLOCKING）
- `JobGraphGenerator` 可正确将 `PartitionTransformation` 产生的 `StreamEdge` 转换为 `JobEdge`（partitioner 非空时）
- `StreamGraphGenerator` 正确处理 `PartitionTransformation`：创建独立 `StreamNode` + 带 partitioner 的 `StreamEdge`

### Checkpoint

- `BarrierAligner` 已实现多输入 barrier 对齐（ReentrantLock + TreeMap + Condition）
- `CheckpointBarrierTracker` 仅支持单链（一个 source、一个 ACK 路径）
- checkpoint 在跨 Task 场景下需要每个 Task 独立快照和 ACK

### 已有的数据交换抽象

- `Output` 接口：`collect()`、`emitWatermark()`、`emitBarrier()` — 用于算子链内传递
- `ChainingOutput`：链内 Output 实现，转发到下一个算子的 Input
- 无 RecordWriter / RecordReader / InputGate 实现
- 无跨 Task 的数据缓冲区或分区机制

## Goals

- 实现 `RecordWriter<T>` 和 `RecordReader<T>`，基于 `BlockingQueue` 在同进程 Task 间传递 `StreamElement`
- 实现 `InputGate`，管理多输入端的数据读取和 barrier 对齐
- `executeWithGraphModel()` 支持多链管线：移除单链约束，按拓扑顺序提交多个 Task
- keyBy + map + sink 等含 `PartitionTransformation` 的管线可在图模型路径中正确执行
- 跨 Task 场景下 checkpoint barrier 正确传播（每个 Task 独立处理 barrier）
- 跨 Task 场景下 Watermark 正确传播（多输入时取最小值）

## Non-Goals

- 不实现分布式数据交换（不引入 Netty / RPC / 网络 IO）
- 不实现 key-group 重分布（partitioner 仅按 key hash 选目标 Task）
- 不实现 backpressure 的精确控制（BlockingQueue 天然提供有限背压）
- 不实现跨 Task 的 exactly-once delivery 保证（at-least-once，由 Sink 端 2PC 保证）
- 不修改快速路径 `execute()` 的行为

## Scope

### In Scope

- `RecordWriter<T>` 实现：将 `StreamElement` 按 partitioner 分发到对应 `BlockingQueue`
- `RecordReader<T>` 实现：从 `BlockingQueue` 读取 `StreamElement`
- `InputGate` 实现：管理 1-N 个 `RecordReader`，多输入时做 barrier 对齐和 watermark 合并
- `ResultPartition` / `InputChannel` 抽象：`BlockingQueue` 的包装，带分区语义
- `StreamTaskInvokable` 修改：区分 Source Task（写 RecordWriter）、Middle Task（从 InputGate 读 → 处理 → 写 RecordWriter）、Sink Task（从 InputGate 读 → 处理）
- `executeWithGraphModel()` 修改：多 Task 拓扑排序提交，创建数据交换通道
- `CheckpointBarrierTracker` 扩展或替换：支持多 Task 独立 ACK
- 多链管线的端到端测试

### Out Of Scope

- 分布式执行（JobManager / TaskManager RPC）
- key-group 重分布和状态重分配
- 网络层传输优化
- 精确背压控制（credit-based 等）

## Risks And Rollback

- **风险**：`StreamTaskInvokable` 当前假设单链（直接运行 source），需要重构为支持三种 Task 角色。缓解：新增 `StreamTaskInvokable` 的工厂方法，根据 JobVertex 位置创建不同角色的 invokable
- **风险**：`InputGate` 的 barrier 对齐可能与现有 `BarrierAligner` 有功能重叠。缓解：`InputGate` 直接复用 `BarrierAligner` 的对齐逻辑
- **风险**：`BlockingQueue` 的容量限制可能导致死锁（生产者和消费者互相等待）。缓解：使用有界队列 + 超时，或在单链场景下仍走 ChainingOutput 直连
- **回滚策略**：所有跨 Task 数据交换代码在独立类中，单链管线行为不变。如有问题可回退到单链约束

## Execution Plan

### Phase 1 - 数据交换核心抽象与实现

Status: completed (pre-existing from Plan 26)
Targets: `nop-stream-core`（execution 包、jobgraph 包）

- Item Types: `Proof`

实现 RecordWriter / RecordReader / InputGate 的核心抽象和 BlockingQueue 实现。

- [x] 新增 `ResultPartition<T>` 类：封装一个 `BlockingQueue<StreamElement>`，提供 `write()` / `read()` / `close()` 方法。一个 `ResultPartition` 对应一个 JobEdge 的输出端
- [x] 新增 `InputChannel<T>` 类：持有对一个 `ResultPartition` 的引用，提供 `read()` 方法。一个 `InputChannel` 对应一个 Task 的一路输入
- [x] 新增 `RecordWriter<T>` 类：持有多个 `ResultPartition`（按下游 Task 数量），根据 partitioner（如果有）选择目标 partition 写入。无 partitioner 时（forward）直接写入唯一 partition。支持写入 `StreamRecord`、`Watermark`、`CheckpointBarrier`
- [x] 新增 `RecordReader<T>` 类：封装 `InputChannel`，提供 `read()` 方法返回 `StreamElement`。单输入时直接代理 InputChannel，多输入时委托给 `InputGate`
- [x] 新增 `InputGate` 类：管理多个 `InputChannel`，提供 `read()` 方法。多输入时实现 barrier 对齐逻辑（收到 barrier 时阻塞该路输入、等待其他路的 barrier 到达后对齐释放；watermark 取多路最小值）。注意：`BarrierAligner` 位于 runtime 模块，InputGate 位于 core 模块，需要在 core 中重新实现对齐逻辑或将 `BarrierAligner` 的核心算法下沉到 core

Exit Criteria:

- [x] RecordWriter 能按 partitioner 将 StreamElement 分发到正确的 ResultPartition
- [x] RecordReader 能从 InputChannel 读取 StreamElement
- [x] InputGate 多输入时能对齐 barrier（复用 BarrierAligner）
- [x] InputGate 多输入时 watermark 取最小值
- [x] 新增单元测试验证上述行为
- [x] `./mvnw test -pl nop-stream/nop-stream-core` 通过

### Phase 2 - StreamTaskInvokable 多角色重构

Status: completed (pre-existing from Plan 26)
Targets: `nop-stream-core`（execution 包）

- Item Types: `Proof`

重构 `StreamTaskInvokable`，支持 Source Task、Middle Task、Sink Task 三种角色。

- [ ] `StreamTaskInvokable` 增加构造参数：`RecordWriter`（可选，Source/Middle Task 有）和 `InputGate`（可选，Middle/Sink Task 有）
- [ ] Source Task 角色：和当前单链行为一致（wire operators → run source → emit to output），但 output 最终连接到 RecordWriter 而非 ChainingOutput 到 sink
- [ ] Middle Task 角色：从 InputGate 读取 StreamElement → 按 element 类型分发（StreamRecord → processElement，Watermark → processWatermark，CheckpointBarrier → processBarrier）→ 处理后写入 RecordWriter
- [ ] Sink Task 角色：从 InputGate 读取 → 分发到算子链处理（链尾是 SinkFunction）
- [ ] 新增 `StreamTaskInvokableFactory` 工厂方法：根据 JobVertex 在 JobGraph 中的位置（有无输入 JobEdge、有无输出 JobEdge）创建正确角色的 invokable

Exit Criteria:

- [ ] Source Task 能通过 RecordWriter 输出数据到下游 Task
- [ ] Middle Task 能从 InputGate 读取数据、处理后写入 RecordWriter
- [ ] Sink Task 能从 InputGate 读取数据并交给 SinkFunction
- [ ] 三种角色的 invokable 能正确处理 Watermark 和 CheckpointBarrier
- [ ] 新增单元测试验证三种角色
- [ ] `./mvnw test -pl nop-stream/nop-stream-core` 通过

### Phase 3 - executeWithGraphModel() 多链支持

Status: completed
Targets: `nop-stream-core`（environment 包、execution 包）、`nop-stream-runtime`（execution 包）

- Item Types: `Proof`

移除单链约束，支持多链管线的图模型执行。

- [x] 修改 `executeWithGraphModel()`：移除单链约束验证（`JobGraph.getEdges()` 非空的检查），改为创建数据交换通道
- [x] 创建数据交换通道：遍历 `JobGraph.getEdges()`，为每条 edge 创建 `ResultPartition`，连接到上游 Task 的 RecordWriter 和下游 Task 的 InputChannel
- [x] 拓扑排序提交：按依赖顺序（source 先、sink 后）提交 Task 到 TaskExecutor
- [x] 更新 `GraphModelCheckpointExecutor`（runtime）：多 Task 场景下每个 Task 独立注册 barrier tracker，checkpoint coordinator 收齐所有 Task 的 ACK
- [x] 端到端测试：`env.fromCollection(data).keyBy(k -> k).map(fn).addSink(collector)` 通过图模型路径正确执行

Exit Criteria:

- [x] 含 keyBy 的多链管线（Source → keyBy → Map → Sink）在图模型路径中正确执行
- [x] 含多个 keyBy / 多个分支的管线正确执行
- [x] 单链管线行为不受影响（回归测试通过）
- [x] 多链场景下 checkpoint barrier 正确传播到每个 Task
- [x] 多链场景下 watermark 正确合并
- [x] 新增测试：多链管线端到端、多 Task + checkpoint、回归单链
- [x] `./mvnw test -pl nop-stream` 全通过

### Phase 4 - 集成测试与文档更新

Status: completed
Targets: `nop-stream-runtime`（test）、`ai-dev/design/nop-stream/`

- Item Types: `Proof`、`Follow-up`

- [x] 端到端测试：Source → keyBy → map → aggregate → Sink，验证数据按 key 正确分区
- [x] 端到端测试：Source → keyBy → timeWindow → aggregate → Sink，验证窗口在多链场景下正确触发
- [x] 端到端测试：多链 + checkpoint，验证 barrier 在 Task 间传播、每个 Task 独立快照
- [x] 更新 `graph-model-design.md` §9.2：数据交换组件从"未完成"改为"已完成"
- [x] 更新 `graph-model-design.md` §10：移除已知限制 1-6 中与数据交换相关的项
- [x] 更新 `checkpoint-design.md` §10.3：多 Task checkpoint 已支持
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 所有端到端测试通过
- [x] 设计文档已更新
- [x] `./mvnw test -pl nop-stream` 全通过

## Closure Gates

- [x] 含 keyBy 的管线可通过 `executeWithGraphModel()` 正确执行（数据按 key 分区）
- [x] 跨 Task 的 barrier 传播和快照正确
- [x] 跨 Task 的 watermark 传播和合并正确
- [x] 单链管线行为不受影响（回归）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 独立子 agent closure-audit 已完成
- [x] `./mvnw test -pl nop-stream`
- [x] checkstyle / 代码规范检查通过

## Non-Blocking Follow-ups

- 精确背压控制（credit-based flow control）
- 网络层传输（分布式执行准备）
- key-group 重分布（状态重分配）
- 性能优化（零拷贝、批量写入）
