# nop-stream 对抗性审查 — Round 1

> 审查日期：2026-05-22
> 审查范围：nop-stream 全模块（10 个子模块），聚焦之前审查未覆盖的区域
> 审查方法：开放式发现导向，4 个并行探索 agent 分别聚焦 execution 核心、CEP NFA 内部、watermark/event-time 子系统、connector + 测试质量
> 去重：已阅读以下已有报告，本报告不重复其中内容：
> - `ai-dev/audits/2026-05-20-adversarial-review-nop-stream/`（Round 1+2，41 个 bug 级发现 + 已知问题确认）
> - `ai-dev/audits/2026-05-21-adversarial-review-nop-stream-design/`（13 个设计级发现 D1-D13）
> - `ai-dev/analysis/2026-04-02-nop-stream-review.md`（557 行综合分析，25 个已知问题）
> - `ai-dev/analysis/2026-05-20-nop-stream-duplicate-code-audit.md`（705 行重复代码审计）
> 发现来源视角：异常路径侦探 + 10x 规模运维者 + IoC 侦探 + 代码生成受害者

---

## 发现分类

本次审查发现的问题分为以下几类：

| 类别 | 数量 | 最严重 |
|------|------|--------|
| 执行引擎正确性 | 6 | P0 |
| Watermark/Event-time 系统缺陷 | 4 | P0 |
| CEP NFA 正确性 | 5 | P0 |
| Connector 资源管理 | 4 | P1 |
| 测试质量 | 5 | P1 |
| 代码质量/一致性 | 5 | P2 |

---

## P0：运行时正确性问题

### N42：InputGate.readMultiChannel() 递归调用可导致 StackOverflowError

**在哪里：** `nop-stream-core/.../execution/InputGate.java` L196-229

**是什么：**

`handleBarrier()` 和 `handleWatermark()` 在需要继续读取时递归调用 `readMultiChannel()`：

```java
private Optional<StreamElement> handleBarrier(int channelIndex, CheckpointBarrier barrier) {
    // ...
    return readMultiChannel();  // 递归
}

private Optional<StreamElement> handleWatermark(int channelIndex, Watermark watermark) {
    // ...
    return readMultiChannel();  // 递归
}
```

多通道场景下，如果连续收到大量不推进 min 的 watermark 或 barrier 未对齐，递归深度无上限增长，最终 `StackOverflowError`。

**为什么值得关心：** 多通道 InputGate 在持续高吞吐数据流中必然崩溃。这不是边界情况——任何 barrier 对齐等待期间的累积元素都会触发。

**信心水平：** 确定

---

### N43：InputGate barriersRemaining 在通道先于 barrier 完成时下溢为负数

**在哪里：** `nop-stream-core/.../execution/InputGate.java` L165-168

**是什么：**

```java
if (channel.isFinished()) {
    barrierReceived[channelIndex] = true;
    barriersRemaining--;    // barriersRemaining 初始为 0，减到负数
    checkBarrierAlignmentComplete();
}
```

`barriersRemaining` 初始为 0，只在第一个 barrier 到达时设为 `channels.size()`。如果通道在 barrier 到达之前完成（上游 producer 关闭），`barriersRemaining` 从 0 递减到 -1, -2 等。后续真正 barrier 到达时 `barriersRemaining <= 0` 提前触发 barrier 对齐，导致 checkpoint 数据不完整。

**为什么值得关心：** 当上游 task 在不同时间完成时（分布式场景常见），barrier 对齐逻辑完全崩溃。

**信心水平：** 确定

---

### N44：GraphExecutionPlan.topologicalSort() 不检测循环——有环 DAG 静默丢弃节点

**在哪里：** `nop-stream-core/.../execution/GraphExecutionPlan.java` L133-168

**是什么：**

Kahn 算法实现中，`sorted` 列表的大小可能小于总节点数（有环时），但方法**不检查** `sorted.size() == totalVertices`。调用方使用不完整的排序列表提交 task，环中的节点被静默丢弃。

**为什么值得关心：** 如果用户构建了有环的算子图（如错误反馈环路），部分算子永远不执行，无错误、无警告。

**信心水平：** 确定

---

### N45：WindowOperator 的 internalTimerService 不接收 watermark 推进——event-time 窗口永不触发

**在哪里：** `nop-stream-runtime/.../WindowOperator.java` L220-221 + 继承链

**是什么：**

`WindowOperator` 在 `open()` 中创建了自己的 `WindowOperatorTimerService`，但它**不注册**到 `AbstractStreamOperator.timeServiceManager`。`processWatermark()` 使用继承的默认实现，只在 `timeServiceManager != null` 时推进 timer，但 `timeServiceManager` 从未被设置。

结果：event-time timer（如 `EventTimeTrigger` 等待窗口 maxTimestamp）永不触发，纯 event-time 窗口化产出零结果。

**为什么值得关心：** 这是 event-time 语义的核心功能。与 Round 2 N19（TimestampsAndWatermarksTransformation 未处理）叠加——即使 watermark 正确生成，也无法到达 WindowOperator 的 timer 服务。

**信心水平：** 确定

---

### N46：TimestampsAndWatermarksOperator.markIdle()/markActive() 是空操作——withIdleness() 功能完全失效

**在哪里：** `nop-stream-core/.../operators/TimestampsAndWatermarksOperator.java` L112-121

**是什么：**

```java
@Override
public void markIdle() {
    // Idle detection not implemented
}
@Override
public void markActive() {
    // Idle detection not implemented
}
```

`WatermarkStrategy.withIdleness()` 在 source 静默一段时间后调用 `output.markIdle()` 以允许 watermark 继续推进。这两个方法是空操作，意味着一个停止产出数据的 partition 会无限阻塞整个 pipeline 的 watermark。

**为什么值得关心：** `withIdleness()` 是 Flink watermark API 的重要特性，用于多分区场景下的 watermark 推进。nop-stream 的 API 层声称为支持此特性（`WatermarksWithIdleness` 类存在），但 operator 层静默丢弃。

**信心水平：** 确定

---

### N47：SharedBufferAccessor.extractPatterns 在 getEntry 返回 null 时 NPE

**在哪里：** `nop-stream-cep/.../sharedbuffer/SharedBufferAccessor.java` L185-190

**是什么：**

```java
sharedBuffer.getEntry(target).getElement()  // getEntry 可返回 null
```

当 edge 的 target node 已被 release 或从 cache 驱逐且未持久化时，`getEntry()` 返回 null，`.getElement()` 立即 NPE。这是 match 提取阶段——匹配结果产出的关键路径。

**为什么值得关心：** 在 SharedBuffer 有并发释放或 cache 驱逐的场景下，pattern 匹配结果会在产出时崩溃。

**信心水平：** 确定

---

### N48：NFA.processMatchesAccordingToSkipStrategy 无 size 检查直接 get(0)——IndexOutOfBoundsException

**在哪里：** `nop-stream-cep/.../nfa/NFA.java` L460

**是什么：**

```java
result.add(sharedBufferAccessor.materializeMatch(matchedResult.get(0)));
```

`matchedResult` 来自 `extractPatterns()`，在所有 edge 已释放时返回空 list。`get(0)` 抛 `IndexOutOfBoundsException`。另一处调用点（`extractCurrentMatches` L869）有 `Preconditions.checkState(paths.size() == 1)` 保护，但此路径没有。

**为什么值得关心：** pattern 匹配在特定状态下（SharedBuffer 被过早清理）会崩溃。

**信心水平：** 很可能

---

### N49：NFA.createDecisionGraph 和 findFinalStateAfterProceed 无环检测——PROCEED 循环导致无限循环

**在哪里：** `nop-stream-cep/.../nfa/NFA.java` L769-836

**是什么：**

两个方法用 Stack (DFS) 遍历 NFA state graph 的 PROCEED 边，但都不跟踪 visited states。如果 NFA 图通过 PROCEED 边形成环，两个方法将无限循环。

`NFACompiler.canProduceEmptyMatches()` 正确使用了 `visitedStates` set，说明这个模式是已知的，但没有一致性应用。

**为什么值得关心：** NFACompiler 正常情况下产生无环 PROCEED 图，但无验证强制保证。手动构建 NFA 或 compiler bug 都可能引入环。

**信心水平：** 很可能

---

### N50：RecordWriter.emitElement() 忽略 partitioner——所有元素写入 channel 0

**在哪里：** `nop-stream-core/.../execution/RecordWriter.java` L116-127

**是什么：**

```java
int channel = 0;
if (partitioner != null) {
    channel = 0; // BUG：与 else 分支完全相同
}
```

即使有 partitioner，也总是写入 channel 0。这是 copy-paste 错误——开发者意图使用 partitioner 路由元素但忘记实现。

**为什么值得关心：** 多分区场景下，只有第一个下游 task 收到数据。

**信心水平：** 确定

---

## P1：API 契约违背 / 功能缺陷

### N51：NFAState.equals() 使用 PriorityQueue.toArray() 比较——堆序不稳定导致 equals 不可靠

**在哪里：** `nop-stream-cep/.../nfa/NFAState.java` L116-126

**是什么：**

```java
return Arrays.equals(partialMatches.toArray(), nfaState.partialMatches.toArray())
        && Arrays.equals(completedMatches.toArray(), nfaState.completedMatches.toArray());
```

`PriorityQueue.toArray()` 返回内部堆序（heap order），不是 comparator 排序。两个包含相同元素但插入顺序不同的 PriorityQueue 的 `toArray()` 结果不同。因此 `NFAState.equals()` 对语义相同的状态可能返回 `false`。

**为什么值得关心：** 影响 NFA 状态快照比较、测试断言等。

**信心水平：** 确定

---

### N52：NFA.processMatchesAccordingToSkipStrategy 中 PriorityQueue.contains O(n) 导致 O(n*m) 性能问题

**在哪里：** `nop-stream-cep/.../nfa/NFA.java` L465-467

**是什么：**

```java
nfaState.getPartialMatches()
    .removeIf(pm -> pm.getStartEventID() != null && !partialMatches.contains(pm));
```

`partialMatches` 是 `PriorityQueue`，`contains()` 是 O(n) 线性扫描。对每个元素调用，总体 O(n*m)。此代码在**每个事件处理时**执行（`doProcess` 和 `advanceTime` 都调用）。

**为什么值得关心：** `followedByAny` + looping pattern 下 partial matches 数量可达数千，每个事件的 O(n*m) 操作会成为严重瓶颈。

**信心水平：** 确定

---

### N53：BatchConsumerSinkFunction.flush() 在 consumer.consume() 失败时导致重复处理

**在哪里：** `nop-stream-connector/.../BatchConsumerSinkFunction.java` L58-64

**是什么：**

```java
private void flush() {
    if (buffer.isEmpty()) return;
    consumer.consume(buffer, null);  // 如果抛异常
    buffer.clear();                   // 永远执行不到
}
```

如果 `consumer.consume()` 抛异常，`buffer.clear()` 不执行。下次 `consume()` 再填满 buffer 后，`flush()` 会重新发送已失败的数据 + 新数据，导致重复处理。

**为什么值得关心：** 生产环境中 consumer 间歇性失败会导致数据重复写入。

**信心水平：** 确定

---

### N54：DebeziumCdcSourceFunction 的 CountDownLatch 只在 cancel() 时 count down——正常完成无法终止

**在哪里：** `nop-stream-connector/.../DebeziumCdcSourceFunction.java` L32, L48-52

**是什么：**

```java
private final CountDownLatch completionLatch = new CountDownLatch(1);
```

latch 只在 `cancel()` 中 count down。如果 Debezium 引擎在 finite snapshot 模式下自行完成，`run()` 方法会永远在 `await()` 中轮询。对比之前的报告（Thread.sleep(1000) 轮询），这个问题已被 CountDownLatch 改善，但正常完成路径仍然缺失。

**为什么值得关心：** Debezium snapshot-only 模式下 source 永远不会自行终止。

**信心水平：** 确定

---

### N55：TimestampsAndWatermarksOperator 每次 processElement 创建新 OperatorWatermarkOutput 对象——GC 压力

**在哪里：** `nop-stream-core/.../operators/TimestampsAndWatermarksOperator.java` L74, 81

**是什么：**

```java
watermarkGenerator.onEvent(element.getValue(), extractedTs, new OperatorWatermarkOutput());
// ...
watermarkGenerator.onPeriodicEmit(new OperatorWatermarkOutput());
```

每个元素至少创建一个 `OperatorWatermarkOutput` 短生命周期对象。Flink 的实现复用单个对象。

**为什么值得关心：** 高吞吐场景下（每秒百万级元素）产生显著的 GC 压力。

**信心水平：** 确定

---

### N56：AbstractStreamOperator.processWatermark() 不做单调性检查——可能转发非递增 watermark

**在哪里：** `nop-stream-core/.../operators/AbstractStreamOperator.java` L205-210

**是什么：**

```java
public void processWatermark(Watermark mark) throws Exception {
    if (timeServiceManager != null) {
        timeServiceManager.advanceWatermark(mark);
    }
    output.emitWatermark(mark);  // 无条件转发，不检查是否递增
}
```

Flink 的实现跟踪当前 watermark 只转发递增的。这里无条件转发，下游可能收到相同或倒退的 watermark。`HeapInternalTimerService` 有 `if (newWatermark <= currentWatermark) return` 保护，但其他下游逻辑不一定。

**为什么值得关心：** 不符合 watermark 单调递增的语义契约。

**信心水平：** 确定

---

### N57：TimestampsAndWatermarksOperator 在元素转发前可能发出 watermark——违反 watermark 不变量

**在哪里：** `nop-stream-core/.../operators/TimestampsAndWatermarksOperator.java` L74-76

**是什么：**

```java
watermarkGenerator.onEvent(element.getValue(), extractedTs, new OperatorWatermarkOutput());
// ↑ 此调用可能通过 OperatorWatermarkOutput.emitWatermark() 转发 punctuated watermark
output.collect(element);  // 元素在 watermark 之后转发
```

punctuated watermark generator 可以在 `onEvent()` 中直接调用 `output.emitWatermark()`，此时元素还未转发。下游在收到元素之前先收到该元素触发的 watermark，违反 "watermark 之后不会有更早时间戳的元素" 的不变量。

**为什么值得关心：** 影响依赖严格 watermark 顺序的算子（如窗口分配器、CEP 引擎）。

**信心水平：** 很可能

---

### N58：StreamExecutionEnvironment.executeWithGraphModel() 不关闭 TaskExecutor——线程池泄漏

**在哪里：** `nop-stream-core/.../environment/StreamExecutionEnvironment.java` L245-268

**是什么：**

```java
TaskExecutor executor = new TaskExecutor();
// ... use executor ...
executor.awaitCompletion();
// executor.shutdown() 从未调用
```

`TaskExecutor` 内部创建 `Executors.newFixedThreadPool()`，执行完毕后不关闭。线程池线程保持活跃，阻止 JVM 正常退出。

**为什么值得关心：** 每次调用 `executeWithGraphModel()` 都泄漏一个线程池。

**信心水平：** 确定

---

### N59：GraphExecutionPlan 每个 JobEdge 创建单个 ResultPartition——并行度 > 1 时数据分布错误

**在哪里：** `nop-stream-core/.../execution/GraphExecutionPlan.java` L73-76

**是什么：**

```java
for (JobEdge edge : jobGraph.getEdges()) {
    edgePartitions.put(edge, new ResultPartition()); // 每个 edge 一个 partition
}
```

当 vertex 并行度 > 1 时，所有并行 task 实例共享同一个 `ResultPartition`（同一个 `LinkedBlockingQueue`）。数据分布错误 + 第一个 producer 关闭时 END_OF_STREAM sentinel 会错误通知所有 consumer。

**为什么值得关心：** 虽然当前并行度固定为 1（另一个 bug），但如果并行度修复后此问题立即暴露。

**信心水平：** 确定

---

### N60：StreamExecutionEnvironment static defaultCheckpointExecutorFactory 无 volatile——多线程可见性

**在哪里：** `nop-stream-core/.../environment/StreamExecutionEnvironment.java` L67

**是什么：**

```java
private static ICheckpointExecutorFactory defaultCheckpointExecutorFactory;
```

此字段由 `setCheckpointExecutorFactory()` 写入，由构造函数读取。无 `volatile`，Java 内存模型不保证跨线程可见性。

**为什么值得关心：** 启动线程设置 factory 后提交 job 的线程可能看到 null。

**信心水平：** 确定

---

### N61：CepPatternBuilder.addQualifier 不校验互斥的量词组合——运行时 MalformedPatternException

**在哪里：** `nop-stream-cep/.../model/builder/CepPatternBuilder.java` L143-181

**是什么：**

多个独立的 `if` 检查允许冲突组合：
- `oneOrMore()` 设置 LOOPING 量词后 `times()` 抛 MalformedPatternException
- `consecutive()` 设置 STRICT 后 `allowCombinations()` 抛 MalformedPatternException

应在模型层面验证互斥。

**信心水平：** 很可能

---

### N62：CepPatternBuilder.buildFollow 缺少 default 分支——新增 FollowKind 枚举值时静默跳过

**在哪里：** `nop-stream-cep/.../model/builder/CepPatternBuilder.java` L101-122

**是什么：**

`buildFollowGroup` 有 `default:` 分支抛明确异常，但 `buildFollow` 没有。新增枚举值时静默跳过，pattern 缺少 follow step。

**信心水平：** 很可能

---

## P2：代码质量 / 一致性

### N63：NFAState.setNewStartPartiailMatch 拼写错误

**在哪里：** `nop-stream-cep/.../nfa/NFAState.java` L111

`Partiail` → `Partial`。字段名 `isNewStartPartialMatch` 和其他方法名拼写正确。被 `NFA.java` L379 调用。

**信心水平：** 确定

---

### N64：StreamNode Javadoc 声称不可变但有 public setter

**在哪里：** `nop-stream-core/.../graph/StreamNode.java` L43-44 vs L177-215

Javadoc 写 "This class is designed to be immutable after construction"，但有 `setKeySelector()`、`setWindowAssigner()`、`setTrigger()` 等 public setter。

**信心水平：** 确定

---

### N65：StreamGraphGenerator.generate() 不可复用——processedTransformations 跨调用累积

**在哪里：** `nop-stream-core/.../graph/StreamGraphGenerator.java` L73-104

`processedTransformations` set 在 `generate()` 调用间不清除。第二次调用跳过与第一次相同 ID 的 transformation，且 `streamGraph` 累积旧节点/边。

**信心水平：** 确定

---

### N66：DeweyNumber.fromString 有不可达分支和缺失 null 检查

**在哪里：** `nop-stream-cep/.../nfa/DeweyNumber.java` L165-181

`String.split()` 对非空串总返回 length >= 1，所以 `else` 分支（throw IllegalArgumentException）不可达。输入为 null 时 NPE。

**信心水平：** 确定

---

### N67：nop-stream-fraud-example 使用 Java 17，其他模块使用 Java 21

**在哪里：** `nop-stream-fraud-example/pom.xml` L14-15

```xml
<maven.compiler.source>17</maven.compiler.source>
<maven.compiler.target>17</maven.compiler.target>
```

与项目其他部分的 Java 21 目标不一致。

**信心水平：** 确定

---

## 测试质量问题

### N68：TestPattern.testNFA 用 System.out.println 替代断言——测试永远通过

**在哪里：** `nop-stream-cep/src/test/.../TestPattern.java` L50

CEP 模块唯一的 NFA 测试用 `System.out.println(matches)` 输出匹配结果，无任何 assert。NFA 算法正确性完全未验证。

**信心水平：** 确定

---

### N69：TestMessageAdapters.testMessageSinkSendsMessages 是假阳性测试

**在哪里：** `nop-stream-connector/src/test/.../TestMessageAdapters.java` L49-58

测试名声称"验证消息发送"，但 `assertNull(subs)` 只验证没有人订阅 topic。`send()` 到无订阅者的 topic 是 no-op，消息从未真正验证。

**信心水平：** 确定

---

### N70：TestCheckpointCoordinator 使用 Thread.sleep(100) 等待异步完成——脆弱测试

**在哪里：** `nop-stream-runtime/src/test/.../TestCheckpointCoordinator.java` L105

`Thread.sleep(100)` 在慢 CI 上可能不够，导致 flaky test。

**信心水平：** 确定

---

### N71：TestEndToEndPipeline 使用不产出数据的 dummy operator——从未验证数据正确性

**在哪里：** `nop-stream-core/src/test/.../TestEndToEndPipeline.java` L322-457

`TestSourceFunction.run()` 不发射任何元素。测试只验证 graph 结构和 task 生命周期，从未验证数据流正确性。

**信心水平：** 确定

---

### N72：TestMessageAdapters 吞掉测试线程中的异常

**在哪里：** `nop-stream-connector/src/test/.../TestMessageAdapters.java` L70, L97

```java
} catch (Exception ignored) {
}
```

source function 在 `run()` 中抛异常时测试不会检测到失败。

**信心水平：** 确定

---

## 总评

### 最值得关注的 3 个方向

1. **Event-time 管线完全不工作**（N42+N43+N45+N46）：从 watermark 生成（markIdle 空操作）、watermark 在 operator 链中传播（非递增转发）、到 watermark 到达 WindowOperator 的 timer 服务（timer 服务未注册），整个 event-time 管线从源头到消费者都有问题。即使修复了 Round 2 的 N19（TimestampsAndWatermarksTransformation 未处理），这些下游问题仍然会阻止 event-time 语义工作。这是一个系统性问题，不是孤立 bug。

2. **CEP NFA 的正确性保障薄弱**（N47+N48+N49+N51+N52+N68）：NFA 是 nop-stream 中完成度最高的模块，但本次审查发现其 match 提取路径有 NPE 风险、状态比较不可靠、性能瓶颈、无环检测。加上唯一的测试不验证结果（N68），NFA 的正确性实际上没有经过自动化验证。

3. **执行引擎的多通道/并行度支持有根本性缺陷**（N42+N43+N44+N50+N59）：InputGate 的递归 StackOverflow + barriersRemaining 下溢、RecordWriter 忽略 partitioner、GraphExecutionPlan 单 partition per edge——如果 nop-stream 未来需要支持并行度 > 1，整个 execution 层需要重写。

### 本次审查的盲区自评

1. **HeapInternalTimerService 的完整生命周期**：没有验证 `HeapInternalTimerService` 与 `TimerServiceManager` 的注册/注销流程，以及 timer 在 checkpoint 恢复后的正确性。
2. **Watermark 的端到端传播路径**：虽然发现了几处断裂，但没有完整追踪从 source 到 sink 的 watermark 传播全路径（特别通过 ChainingOutput 的 `emitWatermark` 路径）。
3. **CEP SharedBuffer 的并发安全**：`SharedBuffer` 使用 Guava Cache + MapState，但没有分析多线程访问时的线程安全性（当前单线程执行可能安全，但 Graph Model 路径中 task 可能在线程池中执行）。
4. **StreamTaskInvokable 的错误恢复**：当 `processElement` 抛异常时，`StreamTaskInvokable` 的错误处理和 task 状态转换没有分析。
5. **性能基准**：没有分析任何性能数据或建立性能基准，所有性能相关发现都是基于代码结构的理论分析。
