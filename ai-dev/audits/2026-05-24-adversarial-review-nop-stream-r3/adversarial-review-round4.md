# nop-stream 对抗性审查 — Round 4（Plan 47 分布式执行审查）

> 审查日期：2026-05-24
> 审查范围：nop-stream 全模块（10 个子模块），聚焦 Plan 47 分布式执行框架新代码 + 历史问题验证
> 审查方法：开放式发现导向，从新代码质量 + 集成正确性出发，验证历史问题
> 去重：已阅读以下已有报告，本报告不重复其中内容：
> - `ai-dev/audits/2026-05-20-adversarial-review-nop-stream/`（Round 1+2，N1-N41）
> - `ai-dev/audits/2026-05-21-adversarial-review-nop-stream-design/`（D1-D13）
> - `ai-dev/audits/2026-05-22-adversarial-review-nop-stream/`（Round 1，N42-N72）
> - `ai-dev/audits/2026-05-22-adversarial-review-nop-stream-r2/`（Round 3，N73-N93）
> 发现来源视角：异常路径侦探 + 10x 规模运维者 + 代码生成受害者

---

## 修复确认清单

以下为 N73-N93 的逐一验证结果：

| # | 描述 | 状态 | 验证详情 |
|---|------|------|----------|
| **N73** | HeapInternalTimerService CME | **已修复** ✅ | `advanceWatermark()` 现在用 `new ArrayList<>(entry.getValue())` 复制 timer entries 后再遍历（L116），解决了回调中删除 timer 导致的 CME |
| **N74** | HeapInternalTimer.getKey() 返回 null | **已修复** ✅ | `TimerEntry` 现在存储 `key` 字段（L129），`HeapInternalTimer` 构造器接收 key（L160），`getKey()` 返回实际 key 值 |
| **N75** | Graph Model 路径缺少 KeyExtractingOutput | **已修复** ✅ | `StreamTaskInvokable.wireOperators()` 现在检查 `keySelectors.get(i+1)` 并包装 `KeyExtractingOutput`（L107-108），headInput 也正确处理（L119-120） |
| **N76** | StreamReduceOperator transient HashMap 状态丢失 | **已修复** ✅ | 添加了 `snapshotState()`/`restoreState()` 方法（L77-110），手动序列化/反序列化 values map |
| **N77** | WindowAggregationOperator trigger state key 碰撞 | **仍存在** | 未改动，`#` 分隔符碰撞风险依然存在 |
| **N78** | WindowAggregationOperator 双 timer 系统不协调 | **仍存在** | 未改动，独立 timer 系统与 TimerServiceManager 仍不协调 |
| **N79** | CepOperator 每事件创建/关闭 SharedBufferAccessor | **仍存在** | 未改动 |
| **N80** | SharedBuffer.advanceTime EventId 碰撞风险 | **仍存在** | 未改动 |
| **N81** | NFACompiler 静默丢弃 NOT_FOLLOW | **仍存在** | 未改动 |
| **N82** | SkipToFirst/SkipToLast 静默退化 | **仍存在** | 未改动 |
| **N83** | invokeMiddle/invokeSink 不发 MAX_WATERMARK | **已修复** ✅ | `invokeMiddle()` 和 `invokeSink()` 现在在 `processInputGate()` 后调用 `headInput.processWatermark(Watermark.MAX_WATERMARK)`（L214, L226） |
| **N84** | processInputGate 不处理 WatermarkStatus | **已修复** ✅ | 添加了 `else if (element.isWatermarkStatus())` 分支（L258-259） |
| **N85** | BatchConsumerSinkFunction IBatchConsumer 从不关闭 | **仍存在** | 未改动 |
| **N86** | MessageSourceFunction unchecked cast | **仍存在** | 未改动 |
| **N87** | TestE2ECheckpointAndRecovery 不是真正恢复测试 | **仍存在** | 未改动 |
| **N88** | TestCepOperatorStateRecovery 复用同一实例 | **仍存在** | 未改动 |
| **N89** | TestEndToEndPipeline 的 stub 不处理数据 | **仍存在** | 未改动 |
| **N90** | CEP 模块零测试覆盖的关键功能 | **仍存在** | 未改动 |
| **N91** | 三套独立 timer 实现 | **仍存在** | 未改动（虽然 N73/N74 修复了 HeapInternalTimerService 的具体 bug，三套并存的架构问题未变） |
| **N92** | CepPatternBuilder 匿名类无生命周期管理 | **仍存在** | 未改动 |
| **N93** | SharedBufferCacheConfig 不验证参数 | **仍存在** | 未改动 |

**修复总结**：N73-N93 中 6 个已修复（N73、N74、N75、N76、N83、N84），14 个仍存在。

另外确认之前审查中的关键问题：
- **N42** InputGate 递归 CME → **仍存在**（`handleBarrier` L275 和 `handleWatermark` L290 仍然递归调用 `readMultiChannel`）
- **N43** barriersRemaining 下溢 → **仍存在**（L220 `barriersRemaining--` 仍然在 barrier 到达前触发）
- **N44** topologicalSort 不检测环 → **仍存在**（GraphExecutionPlan.topologicalSort() 仍无 `sorted.size() == totalVertices` 检查；RemoteGraphExecutionPlanBuilder 也复制了同样的有缺陷的实现）
- **N50** RecordWriter.emitElement 忽略 partitioner → **仍存在**（L184-186：有 partitioner 时广播到所有分区而非路由，`selectChannel()` 只在 `emit()` 中使用）
- **N58** TaskExecutor 不关闭 → **仍存在**（StreamExecutionEnvironment.execute() L260-272：创建了 TaskExecutor 但从不调用 shutdown()）

---

## 新发现

### N94：EmbeddedDistributedExecutor 中 TaskManager.RunningTask 立即提交但 invokable 为 null — 竞态条件导致空转

**在哪里：** `EmbeddedDistributedExecutor.execute()` L219 + TaskManager L386-426

**是什么：**

在 `EmbeddedDistributedExecutor.execute()` 中，task 的分配和启动分两步：

```java
// 步骤1：receiveAssignment → 创建 RunningTask(null invokable) → submit 到线程池 → 开始运行
targetTm.receiveAssignment(assignment);  // L126

// 步骤2：installInvokable → 设置 invokable
targetTm.installInvokable(jobId, vertexId, subtask.getTaskIndex(), subtask.getInvokable());  // L128
```

但 `receiveAssignment()` 内部立即将 RunningTask 提交到 `taskExecutor` 线程池（L219）：

```java
Future<?> future = taskExecutor.submit(runningTask);  // TaskManager L219
runningTask.setFuture(future);  // TaskManager L220
```

RunningTask.run() 会调用 `waitForInvokable()`，以 100ms 轮询等待 invokable 被安装（最多 30 秒）。在正常情况下，`installInvokable()` 紧随 `receiveAssignment()` 之后在调用线程中执行，而 `waitForInvokable()` 在工作线程中执行。

**竞态窗口**：如果 `receiveAssignment` 提交后线程池中恰好有空闲线程立即执行 RunningTask.run()，而 `installInvokable` 尚未被调用，RunningTask 会进入 100ms 轮询循环。在单线程 executor 或 embedded 场景中，这通常是安全的（线程池会被占满）。但在多节点、高并发场景下，轮询开销会显著增加。

**为什么值得关心：** 这是一个 "two-phase initialization" 反模式。Task 被提交执行但其核心工作负载（invokable）尚未就绪。在高并行度场景下，大量 RunningTask 同时进入轮询等待会造成不必要的 CPU 浪费。如果 `installInvokable` 因异常未被调用（例如循环中间抛出异常），RunningTask 会轮询 30 秒后超时退出，错误被吞掉。

**信心水平：** 很可能

---

### N95：EmbeddedDistributedExecutor 不检查 SubtaskTask 实际执行结果 — 任务失败被静默忽略

**在哪里：** `EmbeddedDistributedExecutor.waitForCompletion()` L162-176

**是什么：**

```java
private void waitForCompletion(List<TaskManager> taskManagers, long timeoutSeconds) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;
    while (System.currentTimeMillis() < deadline) {
        int totalRunning = 0;
        for (TaskManager tm : taskManagers) {
            totalRunning += tm.getRunningTaskCount();
        }
        if (totalRunning == 0) {
            return;  // 所有 task 退出就返回
        }
        Thread.sleep(100);
    }
    throw new RuntimeException("Timed out waiting for tasks to complete.");
}
```

`waitForCompletion` 只检查 `getRunningTaskCount() == 0`，不检查 task 是否成功完成。如果所有 task 都失败了（异常后 RunningTask 从 runningTasks 移除），方法返回成功，`execute()` 返回正常的 `StreamExecutionResult`。

对比 LOCAL 模式的 `StreamExecutionEnvironment.execute()`（L274-278）：

```java
for (SubtaskTask task : subtaskTasks) {
    if (task.getState() == SubtaskTask.State.FAILED) {
        throw new RuntimeException("Task failed", task.getError());
    }
}
```

LOCAL 模式正确检查失败状态，DISTRIBUTED 模式不检查。

**为什么值得关心：** 分布式执行中如果所有 task 都失败，用户得到的返回值看起来像是成功。数据完全丢失但无异常抛出。

**信心水平：** 确定

---

### N96：InMemoryClusterRegistry.getActiveNodes() 返回所有已注册节点，不检查 lease 是否过期 — 故障检测失效

**在哪里：** `InMemoryClusterRegistry.getActiveNodes()` L66-68

**是什么：**

```java
@Override
public List<NodeInfo> getActiveNodes() {
    return new ArrayList<>(nodes.values());
}
```

方法返回**所有曾经注册过的节点**，不管其 lease 是否过期。对比 `ClusterRegistry` 接口的 Javadoc 注释："Get all active nodes (nodes with valid leases)"。

`JobCoordinator.detectFailures()` 依赖 `getActiveNodes()` 来检测节点故障：

```java
List<NodeInfo> activeNodes = clusterRegistry.getActiveNodes();
Set<String> activeNodeIds = new HashSet<>();
for (NodeInfo node : activeNodes) {
    activeNodeIds.add(node.getNodeId());
}
// 如果 assigned node 不在 activeNodeIds 中 → 触发恢复
```

因为 `getActiveNodes()` 永远返回所有节点，故障检测永远找不到失效节点 → 全局恢复永远不会被触发。在真正的分布式部署中，如果一个 TaskManager 崩溃，JobCoordinator 永远不会发现。

`JdbcClusterRegistry` 是否正确实现了 lease 过期检查尚未验证（接口注释暗示应该如此），但 `InMemoryClusterRegistry` 的实现与接口契约不一致。

**为什么值得关心：** 这是分布式执行中故障检测的核心功能。如果故障检测不工作，单个节点崩溃就会导致整个 job 挂起（永远等待崩溃节点的 ACK）。

**信心水平：** 确定

---

### N97：RemoteGraphExecutionPlanBuilder.buildRemoteOnly() 所有 Subtask 共享同一个 OperatorChain 实例 — 并行度 > 1 时状态污染

**在哪里：** `RemoteGraphExecutionPlanBuilder.buildRemoteOnly()` L149-150

**是什么：**

```java
for (int taskIndex = 0; taskIndex < parallelism; taskIndex++) {
    OperatorChain chain = original.getOperatorChains().get(0);  // 所有 subtask 共享同一个 chain

    RecordWriter<Object> recordWriter = null;
    InputGate inputGate = null;
    // ... 使用 chain 创建 invokable ...
    StreamTaskInvokable invokable = new StreamTaskInvokable(chain, recordWriter, inputGate);
}
```

`original.getOperatorChains().get(0)` 每次返回的是**同一个 OperatorChain 对象**。当 parallelism > 1 时，N 个 Subtask 的 StreamTaskInvokable 引用同一个 OperatorChain，而 OperatorChain 包含的 Operator 实例是有状态的（如 StreamReduceOperator 的 HashMap、WindowAggregationOperator 的 timer 等）。

这个问题在 `GraphExecutionPlan.build()` 中也存在（L186），是同一个 bug 的复制。

**为什么值得关心：** 并行度 > 1 时，所有并行实例的 keyed state 共享同一个 HashMap/TreeMap，数据互相覆盖。这不是"近似正确"而是"完全错误"。

**信心水平：** 确定

---

### N98：EmbeddedDistributedExecutor 在同一 JobVertex 的 Subtask 间不保证执行顺序 — 下游 InputGate 永久阻塞

**在哪里：** `EmbeddedDistributedExecutor.execute()` L109-132

**是什么：**

```java
for (String vertexId : plan.getSortedVertexIds()) {
    List<Subtask> subtasks = plan.getSubtasks(vertexId);
    for (Subtask subtask : subtasks) {
        int nodeIndex = subtask.getTaskIndex() % nodeCount;
        TaskManager targetTm = taskManagers.get(nodeIndex);
        targetTm.receiveAssignment(assignment);  // 立即开始执行
        targetTm.installInvokable(...);
    }
}
```

代码按拓扑序遍历 vertex，但**不等待上游 vertex 的所有 subtask 完成后再提交下游 vertex**。如果 source vertex 和 sink vertex 被分配到不同 TaskManager，sink 的 InputGate 会立即开始尝试读取。

在 `buildRemoteOnly` 中，每个 subtask 都有自己独立的 RemoteInputChannel 订阅上游 topic。如果下游 subtask 的 RemoteInputChannel 在上游 subtask 的 RemoteResultPartition 开始写入之前就订阅了 topic（LocalMessageService 场景），消息不会丢失（订阅在先）。但如果使用非持久化的 message service 实现，或者上游 topic 的某些消息在下游订阅之前已经发送完毕，下游可能永远收不到 END_OF_STREAM，导致 InputGate.read() 永久阻塞。

在当前的 `InProcessMessageService`（测试中使用）中，`send()` 是同步立即投递到 consumer 的。如果下游还没有订阅，消息会被丢弃 → 下游永远收不到 END_OF_STREAM → `waitForCompletion` 超时 60 秒。

**为什么值得关心：** 这是一个竞态条件，在嵌入式测试中可能导致偶发超时失败（flaky test）。在真正的分布式部署中，如果 message service 不支持 topic 热订阅（consumer 后上线可以消费历史消息），下游 subtask 将永远阻塞。

**信心水平：** 很可能（取决于 IMessageService 的实现语义——是否保证消费者后上线也能收到消息）

---

### N99：StreamElementCodec 使用 JsonTool.stringify/parseBeanFromText 进行跨 TaskManager 序列化 — 丢失 timestamp 和自定义类型信息

**在哪里：** `StreamElementCodec.encode()` L47 + `decode()` L91-103

**是什么：**

编码：

```java
Object serializedPayload = record.getValue() != null ? JsonTool.stringify(record.getValue()) : null;
```

解码：

```java
Object value = payload;
if (payload instanceof String && envelope.getValueType() != null) {
    Class<?> clazz = Class.forName(envelope.getValueType());
    value = JsonTool.parseBeanFromText((String) payload, clazz);
}
return new StreamRecord<>(value);  // 丢失 timestamp
```

两个问题：

1. **StreamRecord 的 timestamp 丢失**：编码时只序列化了 `record.getValue()`，没有序列化 `record.getTimestamp()`。解码时 `new StreamRecord<>(value)` 使用默认构造器，timestamp 未设置。下游算子看到的 timestamp 始终是默认值（通常是 `Long.MIN_VALUE` 或 0），影响所有 event-time 语义。

2. **类型系统脆弱**：`Class.forName(envelope.getValueType())` 要求目标类型在 classpath 上且有无参构造器。对于泛型类型、嵌套集合、枚举等，`parseBeanFromText` 可能反序列化失败或返回错误类型。

**为什么值得关心：** 分布式执行路径中所有 StreamRecord 的 timestamp 都丢失了。这意味着 event-time 窗口、watermark 传播、CEP timeout 等所有依赖 timestamp 的功能在分布式模式下完全失效。

**信心水平：** 确定（timestamp 丢失），很可能（类型系统脆弱性）

---

### N100：EmbeddedDistributedExecutor 的 TaskExecutor 线程池从不关闭 — 资源泄漏（与 N58 同根）

**在哪里：** `EmbeddedDistributedExecutor.execute()` L72-84（TaskManager 创建）

**是什么：**

每个 TaskManager 在构造器中创建 `Executors.newFixedThreadPool(capacity)`（L91）和 `Executors.newSingleThreadScheduledExecutor`（L92-96）。在 `execute()` 方法的 finally 块中（L147-151），调用了 `tm.stop()` 来关闭这些线程池。

但是 `stop()` 方法中的关闭逻辑有问题：

```java
public void stop() {
    if (!running) {
        return;
    }
    running = false;
    heartbeatExecutor.shutdownNow();
    taskExecutor.shutdownNow();  // 立即关闭，不等待
    // ...
}
```

`shutdownNow()` 不等待任务完成。如果 source task 还在运行（如无限 source），中断可能传播失败。更重要的是，与 `StreamExecutionEnvironment.execute()` 中 TaskExecutor 不被关闭（N58）不同，这里的 TaskManager 至少尝试了关闭，但 `shutdownNow()` 的语义是"立即停止"而非"优雅等待完成"。

**为什么值得关心：** 这是一个较小的问题（至少有 finally 块），但 `shutdownNow()` 的"不等待完成"语义与 `waitForCompletion` 的"等待完成"语义矛盾。如果 `waitForCompletion` 成功返回（所有任务完成），`shutdownNow()` 是安全的。如果 `waitForCompletion` 超时抛异常，`shutdownNow()` 会中断还在运行的任务——这是正确的行为。

**信心水平：** 有趣的猜测（问题存在但实际影响较小）

---

### N101：RemoteResultPartition.write() 和 RemoteInputChannel 的 queue.put() 无背压 — 高吞吐下 OOM

**在哪里：**
- `RemoteResultPartition.write()` L80-92
- `RemoteInputChannel.EnvelopeConsumer.onMessage()` L208

**是什么：**

`RemoteResultPartition.write()` 调用 `messageService.send(topic, envelope)` — 如果 message service 是同步的（如 InProcessMessageService），send 立即投递到 consumer。

`RemoteInputChannel.EnvelopeConsumer.onMessage()` 调用 `queue.put(element)` — 这是一个无超时的阻塞 put。如果下游消费速度慢于上游生产速度（如下游是 windowed aggregation），队列满后 producer 线程会在 `queue.put()` 上阻塞。但在 InProcessMessageService 中，`send()` 在 producer 线程上同步调用 `consumer.onMessage()`，所以 producer 线程会阻塞在 `queue.put()` 上。

**问题**：queue 容量硬编码为 1024（DEFAULT_QUEUE_CAPACITY）。如果 producer 快速发送 1024+ 元素而 consumer 处理较慢（如 window 触发计算），producer 线程阻塞。如果 producer 是 source task 的运行线程，source 被阻塞后无法发出 END_OF_STREAM，下游的 `waitForInvokable()` 或 `waitForCompletion()` 可能超时。

更重要的是，如果使用了非阻塞的 message service（如基于 Kafka 的实现），`send()` 立即返回但 consumer 处理不过来 → queue 持续增长 → OOM。

**为什么值得关心：** 分布式执行在高吞吐场景下可能死锁（同步 message service）或 OOM（异步 message service）。

**信心水平：** 很可能

---

### N102：RecordWriter.emitElement() 有 partitioner 时广播到所有分区 — 语义错误（N50 变种）

**在哪里：** `RecordWriter.emitElement()` L182-195

**是什么：**

```java
public void emitElement(StreamElement element) {
    try {
        if (partitioner != null) {
            for (ResultPartition partition : partitions) {
                partition.write(element);  // 广播
            }
        } else {
            partitions[0].write(element);  // 总是 channel 0
        }
    }
}
```

虽然 N50 的原始问题（`emit()` 忽略 partitioner）已通过 `PartitionRouter` 修复，但 `emitElement()` 仍有问题：

1. **有 partitioner 时广播**：Watermark、WatermarkStatus 等非 Record 元素在 `emitElement()` 中被广播到所有分区。这在语义上对 Watermark 是正确的（watermark 应该广播），但代码逻辑是"有 partitioner 就广播"，而不是"watermark 应该广播"。如果 emitElement 将来用于其他非广播元素类型，语义就会错误。

2. **无 partitioner 时写 channel 0**：当 `partitionRouter != null` 但 `partitioner == null` 时（FORWARD 模式），非 Record 元素总是写到 channel 0，而不是广播。如果 parallelism > 1 且使用 FORWARD 策略，下游只有一个 subtask 收到 watermark——其他 subtask 的 watermark 永远不推进。

**为什么值得关心：** Watermark 在非 HASH 分区 + parallelism > 1 的场景下不会正确传播到所有下游 subtask。

**信心水平：** 确定

---

### N103：JobCoordinator.start() 生成新 fencing token，但 EmbeddedDistributedExecutor 在 start() 之前已设置了 fencing token — token 不一致

**在哪里：**
- `EmbeddedDistributedExecutor.execute()` L61, L77, L98
- `JobCoordinator.start()` L131-132

**是什么：**

在 `EmbeddedDistributedExecutor.execute()` 中：

```java
String fencingToken = UUID.randomUUID().toString();  // L61 生成 token-A
// ...
tm.updateFencingToken(fencingToken);  // L77 设置 TaskManager 的 token 为 token-A
// ...
JobCoordinator coordinator = new JobCoordinator(...);
coordinator.setFencingToken(fencingToken);  // L98 设置 coordinator 的 token 为 token-A

coordinator.start();  // L134 → start() 内部生成新 token-B！
```

`JobCoordinator.start()` 内部：

```java
public void start() {
    String token = UUID.randomUUID().toString();  // 生成 token-B，覆盖 token-A
    fencingToken.set(token);  // L132
    clusterRegistry.registerCoordinator(jobId, coordinatorId, token);  // 用 token-B 注册
}
```

**结果**：TaskManager 持有 token-A，JobCoordinator 持有 token-B。后续 checkpoint barrier 从 coordinator 发出时携带 token-B，TaskManager 的 fencing token 检查会拒绝 token-B（TaskManager 的 `currentFencingToken` 是 token-A）。

所有 checkpoint barrier 都被 TaskManager 拒绝 → checkpoint 永远不会完成。

**为什么值得关心：** 这是 Plan 47 新代码中最严重的问题之一。分布式执行的 checkpoint 功能完全不可用——不是偶发失败，而是每次执行都必然失败。

**信心水平：** 确定

---

### N104：RemoteGraphExecutionPlanBuilder 和 GraphExecutionPlan 的 topologicalSort 复制了相同的有缺陷的实现 — DRY 违反 + 环检测缺失

**在哪里：**
- `GraphExecutionPlan.topologicalSort()` L352-387
- `RemoteGraphExecutionPlanBuilder.topologicalSort()` L293-328

**是什么：**

两处实现了完全相同的 Kahn 算法，都缺少环检测（N44 已报告）。但更重要的是，这是一个 DRY 违反——293-328 和 352-387 是逐行相同的代码。

`RemoteGraphExecutionPlanBuilder` 还复制了 `resolveParallelism()`、`resolvePartitionPolicy()`、`resolveEdgeConfig()` 三个静态方法。这些方法与 `GraphExecutionPlan` 中的同名方法完全相同。

**为什么值得关心：** 代码复制导致维护负担加倍。如果修复了 `GraphExecutionPlan` 中的环检测（N44），`RemoteGraphExecutionPlanBuilder` 中仍存在未修复的副本。已有的修复被复制而非共享，是"bug 传播"的典型案例。

**信心水平：** 确定

---

### N105：EmbeddedDistributedExecutor 的 InProcessMessageService 实现不完整 — 缺少 send() 方法

**在哪里：** `TestEmbeddedDistributedExecution.InProcessMessageService` L36-63

**是什么：**

测试中的 `InProcessMessageService` 只实现了 `subscribe()` 和 `sendAsync()`，但 `IMessageService` 接口还声明了 `send()` 方法。`send()` 的默认实现通常调用 `sendAsync().toCompletableFuture().get()`，这会阻塞当前线程。

在 `RemoteResultPartition.write()` 中调用的是 `messageService.send(topic, envelope)`（默认方法），这会同步等待 `sendAsync()` 的 CompletionStage 完成。由于 `InProcessMessageService.sendAsync()` 直接返回 `CompletableFuture.completedFuture(null)`（L61），这实际上是安全的。

但 `InProcessMessageService` 没有 `subscribe(String topic, IMessageConsumer listener)` 的单参数重载——`RemoteInputChannel` 构造器调用的是 `messageService.subscribe(topic, new EnvelopeConsumer())`（L98），这是 `IMessageService` 的另一个重载。如果默认实现不匹配，编译可能通过但运行时行为不确定。

**为什么值得关心：** 测试的 message service 实现可能不是所有使用场景下的准确模拟。

**信心水平：** 有趣的猜测

---

## 总评

### 最值得关注的 3 个方向

1. **N103（Fencing token 不一致）是最严重的 Plan 47 问题**：这是唯一一个"每次执行都必然失败"的问题。TaskManager 和 JobCoordinator 持有不同的 fencing token，导致所有 checkpoint 操作被拒绝。这是一个集成 bug——两个组件各自正确，但组合时出错了。修复方案：要么 `JobCoordinator.start()` 不生成新 token（如果已经通过 `setFencingToken` 设置了），要么 `EmbeddedDistributedExecutor` 不调用 `setFencingToken` 而让 `start()` 自己生成。

2. **N97（所有 Subtask 共享 OperatorChain）是分布式并行度 > 1 的根本障碍**：当 parallelism > 1 时，多个线程同时操作同一个 OperatorChain 的状态（HashMap、TreeMap），不仅结果错误（数据互相覆盖），还存在线程安全问题（HashMap 并发修改）。这与 N28（SimpleStreamOperatorFactory 返回同一对象）是同一类问题——"需要复制但没有复制"。

3. **N99（StreamRecord timestamp 丢失）使分布式模式的 event-time 语义完全失效**：编码时丢弃了 timestamp，解码时没有恢复。这意味着分布式模式下所有依赖时间戳的功能（窗口、watermark、CEP timeout）都产出错误结果。这不是边界情况——是所有包含 event-time 语义的分布式 job 的必然失败。

### 与之前审查发现的系统性问题叠加

本次审查确认了之前审查发现的几个系统性问题的现状：

1. **三套 timer 系统问题（N91）依然存在**，但 HeapInternalTimerService 的具体 bug（N73 CME、N74 null key）已修复。timer 系统从"完全不可用"变为"可用但架构不统一"。

2. **双执行模型问题（D1）加剧了**——现在有三条执行路径：
   - Fast Path（execute()）：LOCAL 模式，无 checkpoint
   - Graph Model（execute()）：LOCAL 模式，有 checkpoint（通过 TaskExecutor + SubtaskTask）
   - Distributed（execute()）：DISTRIBUTED 模式，有 TaskManager + JobCoordinator（但 N103 导致 checkpoint 不可用）

3. **InputGate 的递归问题（N42）和 barriersRemaining 下溢（N43）依然存在**，并且被 `RemoteGraphExecutionPlanBuilder` 复制。分布式模式下多通道场景更常见，这些 bug 更容易触发。

### 本次审查的盲区自评

1. **JdbcClusterRegistry 的实现质量**：只审查了 InMemoryClusterRegistry，没有验证 JDBC 实现是否正确实现了 lease 过期检查。
2. **TaskManager 和 JobCoordinator 的完整生命周期**：没有验证 globalRecovery 的完整流程（从故障检测 → fencing token 更新 → 任务重新分配）。
3. **消息序列化的兼容性**：没有验证 `StreamMessageEnvelope` 通过真正的消息中间件（如 Kafka）传输时的序列化/反序列化行为。
4. **并行度 > 1 的端到端测试**：`TestEmbeddedDistributedExecution` 的 `testDistributed_sourceMapSink` 使用 parallelism=2，但 fromElements source 可能只产生单个 split（所有数据在同一个 subtask），因此测试可能没有真正测试并行执行的正确性。
5. **性能**：所有性能相关发现（N101 背压/Queue 满问题）都是理论分析，没有实际的吞吐量测试数据。
