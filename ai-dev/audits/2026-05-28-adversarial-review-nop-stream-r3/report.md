# nop-stream 对抗性审查 — Round 6

> 审查日期：2026-05-28
> 审查范围：nop-stream 全模块（10 个子模块），聚焦先前审查覆盖不足的区域
> 审查方法：开放式发现导向，4 个并行 agent 深入代码 + 手动交叉验证
> 去重：已阅读以下已有报告，本报告不重复其中内容：
> - `2026-05-20-adversarial-review-nop-stream/`（Round 1+2，N1-N41）
> - `2026-05-22-adversarial-review-nop-stream/`（Round 1，N42-N72）
> - `2026-05-22-adversarial-review-nop-stream-r2/`（Round 3，N73-N93）
> - `2026-05-24-adversarial-review-nop-stream-r3/`（Round 4，N94-N105）
> - `2026-05-28-adversarial-review-nop-stream/report.md`（12 个发现，Checkpoint/WindowOperator/CEP SharedBuffer/TaskManager/BarrierTracker 等）
> - `2026-05-28-adversarial-review-nop-stream-r2/report.md`（Round 5，N106-N120）
> - `2026-05-28-deep-audit-nop-stream-full/summary.md`（21 维度全量审计）
> 发现来源视角：死代码清道夫 + 异常路径侦探 + 新人开发者

---

## 发现分类

| 类别 | 数量 | 最严重 |
|------|------|--------|
| 执行引擎正确性 | 2 | P0 |
| 状态后端/序列化 | 2 | P1 |
| 窗口算子正确性 | 2 | P1 |
| Connector 资源管理 | 1 | P1 |
| 算子工厂/初始化 | 2 | P1 |
| 代码质量/死代码 | 2 | P2 |

---

## P0：执行引擎根本性缺陷

### N121：SimpleStreamOperatorFactory 静默回退共享实例 — parallelism > 1 时算子状态污染

**在哪里：** `nop-stream-core/.../operators/SimpleStreamOperatorFactory.java` L54-57

**是什么：**

当算子不可序列化（`NotSerializableException`），`createStreamOperator()` 静默返回同一个共享模板实例：

```java
} catch (java.io.NotSerializableException e) {
    // Operator contains non-serializable fields (e.g. lambdas).
    // Return the shared template instance instead of failing.
    return operator;   // <-- 所有并行 subtask 共享同一个可变对象
}
```

`GraphExecutionPlan` 的 deep copy 路径调用此工厂为每个 subtask 创建独立算子。但任何包含 lambda 或非序列化字段的算子（如使用 `KeySelector` lambda 的 `WindowAggregationOperator`、使用匿名 `SourceContext` 的 `StreamSourceOperator`）都会触发此回退。

当 `parallelism > 1` 时，多个 subtask 共享同一个可变算子对象，导致：
- `windowState`、`triggerState`、`currentKeyField` 等 `transient` 字段被并发读写
- 状态完全交叉污染——一个 subtask 处理的元素修改了另一个 subtask 的窗口状态

**为什么不修会怎样：** 这不是边缘 case——大多数实际算子（`WindowAggregationOperator`、`StreamReduceOperator`、`StreamSourceOperator`）都包含 lambda 或非序列化字段。parallelism > 1 时，所有这些算子的行为都不可预测。

**信心水平：** 确定

**与先前报告的关系：** N109 报告了 `SubtaskTask.openOperatorChains()` 打开原始 chain 而非 deep copy。本发现是同一问题的另一面：即使 `deepCopy()` 被调用，`createStreamOperator()` 也会静默回退到共享实例，使 deep copy 无效。

---

### N122：HeapInternalTimerService 的 processing time timer 是空操作 — 依赖 processing time 的算子完全失效

**在哪里：** `nop-stream-core/.../operators/HeapInternalTimerService.java` L55-57, L60-62

**是什么：**

```java
@Override
public void registerProcessingTimeTimer(N namespace, long time) {
    // Processing time semantics not implemented per task constraints
}

@Override
public void deleteProcessingTimeTimer(N namespace, long time) {
    // Processing time semantics not implemented per task constraints
}
```

`HeapInternalTimerService` 是 `AbstractStreamOperator` 使用的默认 timer service。`registerProcessingTimeTimer` 和 `deleteProcessingTimeTimer` 都是空操作——不报错、不警告、不记录。

对比 `WindowOperatorTimerService`（L83-88）正确实现了 processing time timer 的注册和触发。

影响范围：
1. `ProcessingTimeTrigger`（使用 `registerProcessingTimeTimer`）注册的 timer 永远不会触发
2. `ContinuousProcessingTimeTrigger` 同样失效
3. 使用 `SlidingProcessingTimeWindows` 或 `TumblingProcessingTimeWindows` 的窗口，如果底层 timer service 是 `HeapInternalTimerService`，窗口永远不会触发

**为什么不修会怎样：** 任何依赖 processing time 语义的窗口操作静默不工作。无错误、无日志。与 `WindowOperatorTimerService` 的正确实现形成对比，说明这是一个遗漏而非设计决定。

**信心水平：** 确定

**与先前报告的关系：** 2026-05-28-deep-audit 提及了此问题作为 P3 发现（"缺失注解"级别），但未标记为正确性 bug。实际上这是一个 P0 级别的功能性缺陷——processing time 窗口完全不工作且无任何错误提示。

---

## P1：正确性问题

### N123：SharedBufferAccessor.releaseNode 遇到 null entry 时 break 整个循环 — 悬挂引用未清理

**在哪里：** `nop-stream-cep/.../nfa/sharedbuffer/SharedBufferAccessor.java` L267-268

**是什么：**

```java
while (!nodesToExamine.isEmpty()) {
    NodeId curNode = nodesToExamine.pop();
    Lockable<SharedBufferNode> curBufferNode = sharedBuffer.getEntry(curNode);

    if (curBufferNode == null) {
        break;   // <-- 终止整个 while 循环
    }
    // ... process edges, push more nodes to examine
}
```

当 `getEntry(curNode)` 返回 null（节点已被其他 computation state 释放），`break` 终止整个循环。此时 `nodesToExamine` 栈中可能还有其他待处理的节点引用，它们的 refCount 永远不会被减少。

**为什么不修会怎样：** 当多个 partial match 共享 buffer 节点时，一个 match 的提取可能触发另一个 match 的节点释放。此时第一个 match 的 `releaseNode` 遇到 null entry 后停止，导致：
1. 剩余节点的 refCount 永远不为 0 → SharedBuffer 内存泄漏
2. 后续的 `extractPatterns` 可能引用已被释放的事件 → NPE

应该是 `continue` 而非 `break`。

**信心水平：** 很可能

**与先前报告的关系：** N120（Round 5）报告了 `SharedBuffer` 创建无用 Timer 的问题，N114 报告了 `extractPatterns` 中间节点 NPE。本发现是 SharedBuffer 生命周期管理的第三个独立缺陷——`releaseNode` 的错误退出策略。

---

### N124：WindowAggregationOperator.processWatermark 发射非单调 watermark — 下游 watermark 语义被破坏

**在哪里：** `nop-stream-core/.../operators/WindowAggregationOperator.java` L234-237

**是什么：**

```java
public void processWatermark(Watermark mark) throws Exception {
    long newWatermark = mark.getTimestamp();
    if (newWatermark <= currentWatermark) {
        output.emitWatermark(mark);   // <-- 向下游转发旧 watermark！
        return;
    }
```

当收到小于或等于当前 watermark 的值时，方法跳过 timer 处理（正确），但仍然向下游转发这个旧 watermark。Watermark 的语义保证是单调递增。向下游发射非递增 watermark 违反了这一契约。

对比 `HeapInternalTimerService.advanceWatermark`（L103-105）在 watermark 未前进时直接 return，不转发——正确行为。

下游算子如果也是基于 watermark 驱动的（如另一个 `WindowAggregationOperator`），收到旧 watermark 后 `advanceWatermark` 返回，不产生错误。但如果下游是 `TimestampsAndWatermarksOperator` 或用户自定义算子，非单调 watermark 可能导致意外行为。

**信心水平：** 很可能

---

### N125：WindowAggregationOperator 序列化键用 `#` 分隔 — 键/window 中含 `#` 时反序列化错误

**在哪里：** `nop-stream-core/.../operators/WindowAggregationOperator.java` L399-401, L434, L467, L483

**是什么：**

```java
private String serializeWindowKey(WindowKey<K, W> wk) {
    return JsonTool.stringify(wk.key) + "#" + JsonTool.stringify(wk.window);
}
```

反序列化时用 `split("#", 2)` 解析。如果 key 或 window 的 JSON 表示中包含 `#` 字符（例如 key 是字符串 `"a#b"`，JSON 为 `"\"a#b\""`），split 会在 JSON 内部的 `#` 处断裂，导致 `JsonTool.parseBeanFromText` 收到不完整的 JSON。

类似问题存在于 trigger state 的序列化（L419），用 `:` 作为第二级分隔符。

**为什么不修会怎样：** 使用字符串 key 且包含 `#` 或 `:` 的 CEP/窗口作业，checkpoint 恢复后反序列化失败，作业无法恢复。

**信心水平：** 很可能

---

### N126：StateShard.computeShardId 中 `Math.abs(Integer.MIN_VALUE)` 返回负数 — 负分片 ID 导致状态路由错误

**在哪里：** `nop-stream-core/.../common/state/shard/StateShard.java` L71

**是什么：**

```java
public int computeShardId(Object key) {
    if (stateShardCount == 1) {
        return 0;
    }
    return Math.abs(stableHash(key)) % stateShardCount;
}
```

当 `key.hashCode()` 返回 `Integer.MIN_VALUE`（-2147483648）时，`Math.abs(Integer.MIN_VALUE)` 仍然是 `Integer.MIN_VALUE`（负数）。取模后仍为负数。负的分片 ID 会导致状态查找失败或路由到错误的分片。

这是 Java 中经典的 `Math.abs` 边界 bug。应使用 `(stableHash(key) & Integer.MAX_VALUE) % stateShardCount` 或 `Math.floorMod()`。

**为什么不修会怎样：** 哈希值恰好为 `Integer.MIN_VALUE` 的 key 被路由到负数分片 ID，其状态无法被正确存储或检索。虽然概率低（约 1/2^32），但在高基数 key 场景下最终会发生。

**信心水平：** 确定

---

### N127：GraphModelCheckpointExecutor.createStorage 对 jdbc 类型直接抛异常 — 无法使用 JDBC checkpoint 存储

**在哪里：** `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java` L566-569

**是什么：**

```java
if ("jdbc".equalsIgnoreCase(storageType)) {
    throw new StreamException(
            "JdbcCheckpointStorage requires IJdbcTemplate configuration. " +
            "Use storageType='local' or provide JDBC configuration.");
}
```

Graph Model 执行路径（通过 `StreamModel` 定义的流作业）永远无法使用 JDBC checkpoint 存储。`JdbcCheckpointStorage` 类存在且有完整的实现，但 `createStorage` 工厂方法直接抛异常阻止了它的使用。

目前没有替代入口可以配置 JDBC 存储并通过 Graph Model 路径执行。这是一个功能缺失，而非配置问题。

**为什么不修会怎样：** Graph Model 执行路径（`StreamModel` → `GraphModelCheckpointExecutor`）只能使用本地文件 checkpoint，无法使用数据库存储。对于需要共享 checkpoint 状态的分布式部署场景，这是硬性限制。

**信心水平：** 确定

---

### N128：BatchConsumerSinkFunction.flush() 消费失败仍清空 buffer — 数据静默丢失

**在哪里：** `nop-stream-connector/.../connector/BatchConsumerSinkFunction.java` L63-75

**是什么：**

```java
public void flush() {
    if (!buffer.isEmpty()) {
        try {
            consumer.consume(buffer, null);   // null IBatchChunkContext → N118 已报告
        } catch (Exception e) {
            LOG.error("Error consuming batch", e);
            // 异常被吞掉，不重新抛出
        } finally {
            buffer.clear();   // <-- 无论成功失败都清空 buffer
        }
    }
}
```

如果 `consumer.consume()` 抛出异常，buffer 中的所有记录在 `finally` 块中被清空。这些记录既没有被成功消费，也没有被保留用于重试。异常仅被 LOG.error 记录，不重新抛出。

**为什么不修会怎样：** 在 sink consumer 暂时不可用时（网络抖动、数据库超时），每批数据都会被静默丢弃。流作业继续运行但数据永久丢失，无任何机制可以检测或恢复。

**信心水平：** 确定

---

### N129：ChainingOutput.collect(OutputTag, StreamRecord) 静默丢弃 side output — 迟到数据/分支流完全丢失

**在哪里：** `nop-stream-core/.../operators/ChainingOutput.java` L68-70

**是什么：**

```java
@Override
public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
    LOG.warn("Side output '{}' discarded in simplified chaining mode. ...", outputTag);
}
```

`ChainingOutput` 是算子链中最常用的 `Output` 实现（`StreamTaskInvokable` 构建算子链时使用 `ChainingOutput` 连接相邻算子）。side output（如迟到数据 `lateDataOutputTag`、分支流）只记录 WARN 日志，数据被完全丢弃。

影响路径：
1. `WindowOperator` 使用 `lateDataOutputTag` 输出迟到数据 → 在链式模式下全部丢失
2. `CepOperator` 使用 `lateDataOutputTag` → 同上
3. 任何使用 `ctx.output(outputTag, value)` 的用户函数 → 同上

**为什么不修会怎样：** 在当前架构下（所有算子默认链式执行），side output 机制完全不可用。对于迟到数据处理场景（金融、IoT），这意味着迟到事件被静默丢弃而只产生一条 WARN 日志。

**信心水平：** 确定

---

### N130：SourceEnumerator.restoreState 中 nextSubtaskIndex 重算公式错误 — 恢复后 split 分配不均匀

**在哪里：** `nop-stream-runtime/.../source/SourceEnumerator.java` L257

**是什么：**

```java
this.nextSubtaskIndex = discoveredSplits.size() - unassignedSplits.size();
```

恢复 checkpoint 后，`nextSubtaskIndex`（round-robin 分配游标）用 `discoveredSplits.size() - unassignedSplits.size()` 重算。但 `discoveredSplits` 包含三种状态的 split：
- 已分配但未完成（在 `assignedSplits` 中）
- 已完成（在 `finishedSplits` 中）
- 未分配（在 `unassignedSplits` 中）

所以 `discoveredSplits.size() - unassignedSplits.size()` = 已分配 + 已完成的数量，而不是"下一个要分配的 subtask index"。

正确公式应该是 `assignedSplits.size()`（已分配数量即 round-robin 已经转过的次数）。

**为什么不修会怎样：** checkpoint 恢复后，round-robin 游标偏移过大（因为包含了已完成的 split）。假设 10 个 split 中 5 个已完成、3 个已分配、2 个未分配。当前公式得到 `10 - 2 = 8`，而正确值应该是 `3`（只有 3 个当前已分配）。游标为 8 意味着前 2 个未分配的 split 全部分配给 subtask `8 % N`，而不是均匀分布。

**信心水平：** 确定

---

## P2：代码质量 / 死代码

### N131：TimestampsAndWatermarksOperator 批量模式下空的 if 块 — 死代码

**在哪里：** `nop-stream-core/.../operators/TimestampsAndWatermarksOperator.java` L81-86

**是什么：**

```java
if (now == lastEmitTime && watermarkInterval == 0) {
    // Element-count based trigger for batch data with interval=0
} else if (now == lastEmitTime && watermarkInterval > 0) {
    // Same millisecond batch data - still use element count as fallback
    // to ensure watermarks advance even when system clock doesn't
}
watermarkGenerator.onPeriodicEmit(new OperatorWatermarkOutput());
```

两个 if 分支的 body 都是空的（只有注释）。无论条件如何，执行路径都一样：直接到 `onPeriodicEmit()`。这些条件判断是无效代码。

从注释意图看，这里应该实现"同一毫秒内到达大量元素时的计数触发"逻辑，但实际未实现。

**信心水平：** 确定

---

### N132：SubtaskTask.cancel() 只在 CREATED 状态有效 — RUNNING 状态的 task 无法取消

**在哪里：** `nop-stream-core/.../execution/SubtaskTask.java` L84（cancel 方法）

**是什么：**

`cancel()` 只在 `state == State.CREATED` 时设置 `state = State.CANCELED`。一旦 task 进入 RUNNING 状态，cancel 调用没有任何效果。没有机制中断正在运行的 invokable。

虽然 `RunningTask` 类（在 TaskManager 中）使用 `CountDownLatch` 协调 cancel，但 `SubtaskTask` 是另一个独立的 task 实现类。如果 `SubtaskTask` 的 `run()` 方法中的 invokable 阻塞（如 source function 的 `run()` 无限循环），没有任何方式可以停止它。

**信心水平：** 很可能

---

## 总评

### 最值得关注的 3 个方向

1. **SimpleStreamOperatorFactory 的静默回退（N121）是本轮最实际的发现。** 之前的审查都聚焦在 `SubtaskTask.openOperatorChains` 打开原始 chain 的问题（N109），但更深层的根因是：即使 deep copy 被正确调用，`SimpleStreamOperatorFactory.createStreamOperator()` 对非序列化算子静默返回共享实例。这意味着 N109 的修复（改用 deep copy chain）对包含 lambda 的算子仍然无效。这两个发现必须一起修复。

2. **HeapInternalTimerService 的 processing time timer 空操作（N122）是一个被低估的功能缺失。** 之前的 deep-audit 将其归类为 P3（"缺失注解"），但实际影响是：使用 `HeapInternalTimerService` 的算子（通过 `AbstractStreamOperator` 创建的所有默认算子）中，processing time 窗口完全不工作。与 `WindowOperatorTimerService` 的完整实现形成鲜明对比，说明这确实是一个遗漏。

3. **Side output 和 checkpoint 恢复路径的静默数据丢失（N128, N129, N130）形成了一个"静默失败"模式。** BatchConsumerSinkFunction 在消费失败时清空 buffer，ChainingOutput 丢弃 side output，SourceEnumerator 恢复时游标偏移——这三者共同特点是：不报错、不抛异常、只记录日志或完全不记录。用户在正常运行中无法发现数据丢失。

### 本次审查的盲区自评

1. **没有验证 N121 的实际触发频率：** 需要确认 `GraphExecutionPlan.deepCopy()` 是否真的调用了 `createStreamOperator()`，还是直接序列化整个 chain。
2. **没有审查 `nop-stream-flow` 和 `nop-stream-flink` 模块：** 这两个模块没有 Java 源代码，只有 pom.xml。它们可能是空壳模块或仍在规划中。
3. **没有运行测试验证任何发现：** 特别是 N121（共享实例）和 N122（空操作 timer）应该可以通过简单的单元测试快速确认。
4. **没有深入审查 `StreamGraphGenerator` 的 `PartitionOperatorFactory` 返回 null 的问题。** 探索 agent 报告了这个问题，但我没有亲自验证其调用链。
5. **RPC 传输层（`StreamElementCodec`、`RemoteInputChannel`、`RemoteResultPartition`）没有审查。** 这些是分布式部署的关键路径。
