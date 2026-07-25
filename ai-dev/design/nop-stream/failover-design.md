# Targeted Failover 可行性裁定

> Status: final（no-go 裁定）
> Created: 2026-07-26
> Source: Plan `ai-dev/plans/nop-stream-production/2026-07-26-0433-2-targeted-failover.md`（Stage 27）
> Related: `checkpoint-design.md` §8.1.1（全局 epoch recovery baseline）；`01-architecture-baseline.md` §五 Restart Strategy；`00-vision.md` §四/§七（流式连续执行定位）

---

## 一、裁定结论

**NO-GO**：在 nop-stream 当前 all-pipelined + by-reference-queue 架构下，targeted failover（region 级或 subtask 级的局部故障恢复）**不可行**，且所需的前置变更超出本 Stage 范围，需 vision 级决策或 Stage 44（跨 JVM region failover）引入。

本裁定是 Stage 27 的核心交付物。裁定为 no-go 后，G57（targeted failover 实现）、G28（续，partial/region 恢复）、per-region restart 计数器保持 deferred，归属记录到 successor（Stage 44 或新 vision 决策 plan）。

---

## 二、架构事实确认（裁定依据）

以下事实经 live 仓库核对确认。

### 2.1 所有 edge 均为 pipelined → 单 region

`JobGraphGenerator.determinePartitionType()`（`JobGraphGenerator.java:546-554`）的逻辑：

- partitioner == null（forward）→ `ResultPartitionType.PIPELINED`
- partitioner != null（keyBy/hash 等）→ `ResultPartitionType.PIPELINED_BOUNDED`

两种类型均 `pipelined=true`（`ResultPartitionType.java:49,59`）。唯一 `pipelined=false` 的 `BLOCKING`（`:70`）**从不被产生**。

**推论**：每个 JobGraph = 单个 pipelined connected component = **单个 region**。vertex 级 targeted failover 等同于 global failover，**零收益**。

### 2.2 数据交换 = by-reference 阻塞队列（drain/reconnect 不可达的根因）

- 生产侧：`ResultPartition`（`ResultPartition.java:39,49`）持有 `LinkedBlockingQueue<StreamElement>`，`write()`（`:108-128`）在队列满时 `queue.put()` **阻塞调用线程**。
- 消费侧：`InputChannel`（`InputChannel.java:20-25`）**直接持有** `ResultPartition` 引用，`read()` 委托 `partition.read()`（`queue.take()`）。
- 分区矩阵：`GraphExecutionPlan.build()`（`:249-262`）为每条 edge 创建 `[srcP][tgtP]` 的 `ResultPartition` 矩阵，所有 subtask 共享这些 by-reference 实例。

**推论**：生产者线程与消费者线程通过同一个 `LinkedBlockingQueue` 实例直接耦合。不存在"换 channel"的间接层——要替换 partition 必须停止持有它引用的线程。

### 2.3 唯一恢复入口 = globalRecovery()，无 mid-execution 重启

- `JobCoordinator.globalRecovery()`（`JobCoordinator.java:647-699`）：新 fencing token → 清空内存 working set → `assignTasks()` 全量重分配。所有失败信号（节点 lease 丢失 `:626`、per-task stall `detectFailures():603-627`、per-task FAILED 上报 `reportTaskStatus():526`）汇聚于此。
- `restartCount`（`:155`）仅 `globalRecovery()` 递增；`maxRestarts=3`（`:158`）。无 per-region 计数器。
- local 执行路径中，`GraphModelCheckpointExecutor.submitAndRun()`（`:678-683`）先 `submitTask` 再 `awaitCompletion()`（阻塞至全部完成）；`checkTaskFailures()`（`:685-691`）仅在 `awaitCompletion()` **返回后**运行。**不存在 mid-execution 重启入口**。

### 2.4 restore 为 whole-job

`CheckpointCoordinator.restoreFromCheckpoint()`（`CheckpointCoordinator.java:644`）无 partial variant；`GraphModelCheckpointExecutor.restoreTaskStatesFromSource()` 遍历全部 vertex/subtask。入口为 whole-job，无 per-region / per-subtask 恢复路径。

### 2.5 partitioner 分布

生产代码中仅存在两种 partitioner：

- `ForwardPartitioner`（point-to-point，但经 `determinePartitionType` 映射为 `PIPELINED`）。
- `KeySelectorPartitioner`（hash，all-to-all，经 `determinePartitionType` 映射为 `PIPELINED_BOUNDED`）。

无生产 `RebalancePartitioner` / `RescalePartitioner`（仅测试 stub）。

---

## 三、可行性分析（go/no-go 关键路径）

### 3.1 vertex 级 targeted failover

**裁定：零收益。** §2.1 已证明 all-pipelined → 单 region。vertex 级重启 = 整个 region 重启 = global failover。

### 3.2 subtask 级 blast-radius

**裁定：理论上有更小的 blast-radius，但不可实现。**

在 forward 边（parallelism 相等）下，partition 矩阵 `[s][s]` 为对角使用，单 subtask 失败理论上仅影响配对的对端 subtask——blast-radius 确实小于全局。但在 all-to-all（keyBy/hash）边下，每个 source subtask 连接全部 target subtask，blast-radius = 整个 vertex。

然而 blast-radius 分析的结论是** moot**（无实际意义），因为 §3.3 的 drain/reconnect 不可达，使 subtask 级重启本身结构上不可能。

### 3.3 drain/reconnect 可行性（关键路径）

**裁定：不可设计（无 blocking edge 引入的前提下）。** 这是 no-go 的决定性因素。

pipelined 队列按引用直连（§2.2）下，scoped 重启面临三个死锁/结构障碍：

1. **下游死 → 上游 `queue.put()` 永久阻塞**：若仅重启下游 subtask，其消费的 `ResultPartition` 队列积满后，上游生产者线程阻塞在 `queue.put()` 上。上游线程不会被重启（"scoped"语义要求仅重启受影响子集），因此永久阻塞，无超时、无中断（队列 `put` 仅响应 `InterruptedException`，但 scoped 重启不会中断未失败的上游线程）。
2. **上游死 → 下游 channel 不 close 永挂**：若仅重启上游 subtask，其旧的 `ResultPartition` 不会收到 `END_OF_STREAM`（`finish()` 未被调用，因为线程已死），下游消费者 `queue.take()` 永久阻塞等待数据。
3. **无法 mid-execution 重启**：`checkTaskFailures` 在 `awaitCompletion()` 后运行（§2.3），线程已全部退出，不存在"重启单个运行中 task"的入口。

要解决上述死锁，必须引入**物化点 / blocking edge**：在 region 边界将中间结果物化到可独立寻址的存储（内存或磁盘），使 producer 与 consumer 的生命周期解耦。producer 完成后 consumer 可独立重启，反之亦然。这正是 `BLOCKING` partition type 的语义。

但引入 blocking edge 与 nop-stream 的流式连续执行定位存在张力（§3.4），且属于超出本 Stage scope 的架构变更。

### 3.4 region 边界引入可行性（blocking edge）

**裁定：需 vision 级决策，超出本 Stage scope。**

`graph-model-design.md:143` 明确：`BLOCKING` = 批式传输，生产者全部完成后消费者才开始——批处理场景（**当前 nop-stream 未使用**）。`00-vision.md` 将 nop-stream 定位为声明式图模型驱动的**流处理引擎**（连续、低延迟执行）。

引入 blocking edge / 物化点意味着：

- 流式低延迟连续执行模型需要扩展为支持"批式物化边界"的混合模型。
- 物化点的语义（何时刷盘、物化数据生命周期、恢复时如何消费物化结果）需要独立设计。
- 这改变了 nop-stream 的核心执行模型假设（`graph-model-design.md:204`："当前假设所有 vertex 可以同时启动"）。

此类变更属 vision 决策范畴，不在本 Stage（in-process targeted failover）的 scope 内。

### 3.5 supervision loop 可行性

**裁定：需执行模型变更，属 Stage 44 scope。**

mid-execution 重启要求一个 supervision loop 能够：(a) 在 task 运行中检测单 task 失败；(b) 终止并重启单个 task 而不影响其余。当前 `GraphModelCheckpointExecutor` 的 `submitAndRun` → `awaitCompletion` 模型不支持——它一次性提交所有 task 并阻塞等待全部完成（§2.3）。

要引入 supervision loop 需要：将 `awaitCompletion` 的全量阻塞等待改为可观测的单 task 失败回调 + 单 task 重启调度。这是执行模型变更，其复杂度与 Stage 44（跨 JVM region failover）同级。

---

## 四、裁定汇总

| 维度 | 裁定 | 依据 |
|---|---|---|
| vertex 级 targeted failover | **零收益** | §2.1：all-pipelined → 单 region → vertex 重启 = global |
| subtask 级 blast-radius | 理论可行（forward）但 **moot** | §3.2：受 drain/reconnect 不可达阻断 |
| drain/reconnect（无 blocking edge） | **不可设计** | §3.3：by-reference 队列三个死锁障碍 |
| region 边界（blocking edge） | **需 vision 决策** | §3.4：改变流式连续执行模型假设 |
| supervision loop（mid-execution 重启） | **属 Stage 44** | §3.5：执行模型变更 |

**综合裁定：NO-GO。** targeted failover 在 nop-stream 当前架构下不可行，所需前置（blocking edge + supervision loop + drain/reconnect）超出本 Stage scope。

---

## 五、架构前置（解除 no-go 所需）

targeted failover 变为可行，需以下前置全部满足。这些构成 Stage 44（或新 vision 决策 plan）的入口条件：

1. **Blocking edge / 物化点支持**：在 JobGraph 中引入 `BLOCKING` partition type 的实际产生路径（当前 `determinePartitionType` 从不返回它），使 region 边界成立、producer/consumer 生命周期解耦。需 vision 决策确认流式引擎是否支持批式物化边界。
2. **Region 概念与识别**：在 runtime 引入 region 抽象（当前生产代码零 region 概念，`JobCoordinator.java:151,649` 仅为 forward-looking 注释），能从 JobGraph 的 blocking edge 切分出多个 pipelined connected component。
3. **Supervision loop 执行模型**：将 `submitAndRun` → `awaitCompletion` 全量阻塞模型改为可 mid-execution 检测单 task 失败并重启的 supervision 模型。
4. **Drain/reconnect 机制**：基于物化点实现 scoped 重启时 producer/consumer 的安全 drain（排空在途数据）与 reconnect（重新接线到新 partition），不破坏 exactly-once。
5. **Per-region restart 计数器**：scoped 重启不走 `globalRecovery()`，需独立的 per-region 计数器与上限（Stage 25 deferred 项）。

---

## 六、G57 / G28（续）/ per-region 计数器归属

| 项 | 来源 | 归属 | 理由 |
|---|---|---|---|
| G57（targeted failover 实现） | Stage 27 核心 | deferred → Stage 44 / vision 决策 | 需全部架构前置（§五） |
| G28（续，partial/region 恢复） | Stage 20 deferred | deferred → Stage 44 | drain/reconnect 需 blocking edge 前置 |
| per-region restart 计数器 | Stage 25 deferred | deferred → Stage 44 | scoped 重启入口不存在 |

以上三项保持 deferred，不降级为 non-blocking follow-up（它们是 Stage 27 的 in-scope 裁定对象，裁定结论为"需架构前置"而非"优化项"）。

---

## 七、与 global epoch recovery 的关系（不变）

no-go 裁定**不改变**现有恢复基线：`globalRecovery()`（`JobCoordinator.java:647-699`）仍是唯一恢复入口，语义完整（epoch manifest 自带全部一致性上下文）。targeted failover 从始至终是**优化项**，不是 exactly-once 的正确性前置条件（`checkpoint-design.md` §8.1.1 已确立此立场）。本裁定确认该立场成立。

---

## 八、拒绝的替代方案

| 替代方案 | 拒绝原因 |
|---|---|
| 仅对 forward 边实现 subtask 级重启（不引入 blocking edge） | drain/reconnect 不可达（§3.3）：by-reference 队列下 scoped 重启死锁无解 |
| 引入 blocking edge 但限定为"仅恢复时临时物化" | 改变执行模型假设（所有 vertex 同时启动），仍需 supervision loop + region 识别，复杂度等同 Stage 44，不属于 in-process scope |
| 用 checkpoint unaligned barrier 绕过 drain | unaligned checkpoint（Stage 43）解决的是 barrier 对齐延迟，不解决 producer/consumer 生命周期解耦；scoped 重启仍需 channel drain |
