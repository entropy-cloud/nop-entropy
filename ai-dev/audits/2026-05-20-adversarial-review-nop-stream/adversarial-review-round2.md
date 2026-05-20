# nop-stream 对抗性审查 — Round 2

> 审查日期：2026-05-20
> 方法：基于 Round 1 暴露的线索继续深挖，重点关注 DataStreamImpl 执行路径、状态管理、Watermark 传播
> 新发现编号延续 Round 1（N17 起）

---

## N17: MemoryInternalAppendingState.add() 累加器不重置 — 聚合结果指数膨胀

**在哪里：** `nop-stream-core/.../MemoryKeyedStateBackend.java` L426-436（MemoryInternalAppendingState 内部类）

**是什么：**

```java
public void add(IN value) throws IOException {
    TypedNamespaceAndKey key = getStorageKey();
    ACC current = storage.get(key);
    if (current != null) {
        accumulator.add((IN) current);  // 往 accumulator 加入了旧值
    }
    accumulator.add(value);             // 再加入新值
    storage.put(key, (ACC) accumulator.getLocalValue());
}
```

注释说"重置 accumulator"，但**从未调用任何 reset**。连续调用 `add()` 时，accumulator 内部状态在第二次调用时会**保留上次 add 后的脏状态**。

例如对 `SumAccumulator` 连续 add(1), add(1)：
- 第 1 次：accumulator 内部=1, storage=1 ✅
- 第 2 次：accumulator 内部仍为 1（未重置），add(旧值1)→内部=2，add(新值1)→内部=3, storage=3 ❌（预期 2）

**为什么值得关心：** 所有使用窗口聚合的计算结果都会错误。与 Round 1 发现 1（addWindowElement 类型腐蚀）叠加，WindowOperator 的聚合路径从底层状态管理到上层元素添加都有 bug。

**信心水平：** 确定

---

## N18: KeyedStreamImpl(parentStream, keySelector) 构造器 environment=null/transformation=null — key 语义丢失

**在哪里：** `nop-stream-core/.../KeyedStreamImpl.java` L64-68

**是什么：**

```java
public KeyedStreamImpl(DataStream<T> parentStream, KeySelector<T, KEY> keySelector) {
    super(null, null);  // environment 和 transformation 都为 null
    this.keySelector = keySelector;
    this.parentStream = parentStream;
}
```

通过此构造器创建的 KeyedStream，`map()`/`filter()` 等委托到 `parentStream`，**完全跳过 key 分区**。`keyBy()` 本身产生的 `PartitionTransformation` 从未注册到 environment。

**为什么值得关心：** 对已 keyed 的流再次 keyBy 会彻底丢失 key 分区信息。虽然 `DataStream.keyBy()` 走另一个正确的构造器，但 `KeyedStreamImpl(parentStream, keySelector)` 这个构造器的存在本身就是一个陷阱。

**信心水平：** 很可能

---

## N19: execute() 路径中 TimestampsAndWatermarksTransformation 完全未处理

**在哪里：** `nop-stream-core/.../StreamExecutionEnvironment.java` L362-437（executePipeline 方法）

**是什么：**

`executePipeline()` 的 `instantiateOperators()` 只处理三种 transformation 类型：SourceTransformation、OneInputTransformation、SinkTransformation。**TimestampsAndWatermarksTransformation 不在其中**，被静默跳过。

结果：
1. 调用 `assignTimestampsAndWatermarks()` 后的 watermark operator 永远不会在 fast path 实例化
2. 链中跳过此节点导致上下游直接连接，watermark 策略完全不生效

**为什么值得关心：** 在 fast path（`execute()`）中，event-time 语义完全不工作。用户调用了 `assignTimestampsAndWatermarks()` 却没有效果，且没有任何错误提示。

**信心水平：** 确定

---

## N20: checkpointExecutorFactory 是 static 全局字段 — 跨实例共享且无线程安全

**在哪里：** `nop-stream-core/.../StreamExecutionEnvironment.java` L64

**是什么：**

```java
private static ICheckpointExecutorFactory checkpointExecutorFactory;
```

全局可变静态字段：任何调用 `setCheckpointExecutorFactory()` 的代码会**影响所有已存在和新创建的** environment 实例。

**为什么值得关心：** 多个测试并行运行时互相干扰。不同 job 可能需要不同的 checkpoint 策略，但无法独立配置。

**信心水平：** 确定

---

## N21: DataStreamImpl.map()/flatMap() 输出类型硬编码为 UnknownTypeInformation

**在哪里：** `nop-stream-core/.../DataStreamImpl.java` L124-130, L155-161

**是什么：**

```java
public <R> SingleOutputStreamOperator<R> map(MapFunction<T, R> mapper) {
    return transform("Map",
        (TypeInformation<R>) UnknownTypeInformation.INSTANCE,
        new StreamMap<>(mapper));
}
```

Java 泛型擦除导致无法获取 `R` 类型，且缺少 `map(mapper, TypeInformation)` 重载让用户传入显式类型。下游 `getType()` 只返回 `Object.class`。

**为什么值得关心：** 类型信息在 map/flatMap 操作后完全丢失，影响序列化选择、sink 类型检查等。

**信心水平：** 确定

---

## N22: ValueStateDescriptor(name, TypeInformation) 构造器丢弃 typeInfo 参数

**在哪里：** `nop-stream-core/.../ValueStateDescriptor.java` L57-59

**是什么：**

```java
public ValueStateDescriptor(String name, TypeInformation<T> typeInfo) {
    super(name, null);  // typeInfo 被丢弃
}
```

用户通过此构造器传入 TypeInformation，但实际传给 `StateDescriptor` 的是 `null`。后续 `getValueType()` 返回 null。

**为什么值得关心：** API 合同违背。使用此构造器的代码可能在运行时因 NPE 而失败。

**信心水平：** 确定

---

## N23: StreamSourceOperator.run() 正常完成后调用 cancel() — 违反生命周期契约

**在哪里：** `nop-stream-core/.../StreamSourceOperator.java` L89-90

**是什么：**

```java
sourceFunction.run(ctx);
sourceFunction.cancel();  // run 正常完成后调用 cancel
```

`cancel()` 的语义是"中断正在运行的 source"。在 `run()` 正常完成后调用 `cancel()` 可能导致对有副作用的 source（如通知外部系统停止）在正常结束时错误触发。

**为什么值得关心：** BatchLoaderSourceFunction 的 `cancel()` 只设 `running=false`（无害），但 DebeziumCdcSourceFunction 的 `cancel()` 会调用 `source.stop()`（有害——正常完成后不该 stop 已完成的 source）。

**信心水平：** 确定

---

## N24: extractKeySelectors 的索引映射在 PartitionTransformation 非标准位置时错乱

**在哪里：** `nop-stream-core/.../StreamExecutionEnvironment.java` L394-411

**是什么：**

`extractKeySelectors` 记录 PartitionTransformation 在**原始 chain** 中的索引，然后从 chain 中删除。但后续 `wireOperatorChain` 中 `keySelectors.containsKey(i + 1)` 基于的是**删除后**的 operators 列表。

当只有一个 Partition 且在特定位置时恰好正确。如果连续两个 Partition 或 Partition 在非标准位置，索引映射完全错乱。

**为什么值得关心：** 当前使用场景可能碰巧正确（单 Partition），但代码正确性依赖于无文档保护的隐含假设。

**信心水平：** 很可能

---

## N25: MemoryMapState 构造器丢弃 descriptor 参数

**在哪里：** `nop-stream-core/.../MemoryKeyedStateBackend.java` L291-293

**是什么：**

```java
MemoryMapState(MemoryKeyedStateBackend<?> backend, MapStateDescriptor<UK, UV> descriptor) {
    this.backend = backend;
    // descriptor 未保存到任何字段
}
```

对比 `MemoryValueState` 保留了 descriptor 并使用 `descriptor.getDefaultValue()`。

**为什么值得关心：** MapState 无法支持默认值或 descriptor 级别的配置。与同 backend 的 ValueState 实现不一致。

**信心水平：** 确定

---

## N26: MemoryKeyedStateBackend snapshot/restore 后 accumulator 带脏状态 + transient namespace 为 null

**在哪里：** `nop-stream-core/.../MemoryKeyedStateBackend.java` L196-234, L379-393

**是什么：**

1. `MemoryInternalAppendingState.accumulator` 未标记 `transient`，序列化后恢复时保留脏状态（与 N17 叠加）
2. `currentNamespace` 标记为 `transient`，反序列化后为 null，首个操作如果不 `setCurrentNamespace()` 就调用 `get()` 会使用 null namespace

**为什么值得关心：** Checkpoint 恢复后状态管理的正确性无法保证。

**信心水平：** 很可能

---

## N27: WindowedStreamImpl 构造器传入 null WindowAssignerContext

**在哪里：** `nop-stream-core/.../WindowedStreamImpl.java` L41

**是什么：**

```java
this.trigger = windowAssigner.getDefaultTrigger(null);
```

如果 `getDefaultTrigger` 使用 context，NPE。

**信心水平：** 很可能（当前内置实现碰巧不使用 context）

---

## N28: SimpleStreamOperatorFactory.createStreamOperator() 返回同一个对象 — 并行度>1 时线程不安全

**在哪里：** `nop-stream-core/.../SimpleStreamOperatorFactory.java` L33-35

**为什么值得关心：** 如果 parallelism > 1 或执行路径被调用两次，多个实例共享同一个 operator 对象的可变状态。

**信心水平：** 确定

---

## N29: KeySelectorPartitioner.partition() 对 null key 抛 NPE + Math.abs(Integer.MIN_VALUE) 返回负数

**在哪里：** `nop-stream-core/.../DataStreamImpl.java` L281-288

**是什么：**

```java
Object key = keySelector.getKey(value);
return Math.abs(key.hashCode()) % numPartitions;
```

1. key 为 null → `key.hashCode()` NPE
2. `Math.abs(Integer.MIN_VALUE)` 返回负数 → `负数 % numPartitions` 为负 → ArrayIndexOutOfBoundsException

**信心水平：** 确定

---

## N30-N41: 其他发现

| # | 发现 | 文件 | 信心 |
|---|------|------|------|
| N30 | Transformation.id 使用全局 AtomicInteger，序列化后计数器不重置 | Transformation.java L27-28 | 有趣的猜测 |
| N31 | wireOperatorChain 索引映射基于不同列表，无断言保护 | StreamExecutionEnvironment.java L445-464 | 很可能 |
| N32 | StreamSinkOperator 被 KeyExtractingOutput 包装但 sink 不使用 key context | StreamSinkOperator.java | 有趣的猜测 |
| N33 | TimestampsAndWatermarksOperator 周期性 watermark 间隔硬编码 200ms | L76 | 确定 |
| N34 | UnknownTypeInformation 未实现 Serializable | UnknownTypeInformation.java L21 | 确定 |
| N35 | MemoryInternalAppendingState.accumulator 非 transient — 序列化带脏状态 | MemoryKeyedStateBackend.java L379 | 确定 |
| N36 | execute() 和 executeWithGraphModel() 两条路径 DAG 解释逻辑完全不同 | StreamExecutionEnvironment.java | 确定 |
| N37 | TestEndToEndPipeline 的 operator 不处理数据，只测试图结构 | TestEndToEndPipeline.java | 确定 |
| N38 | TestE2ESimplePipeline 比较两个实现而非比较预期结果 | TestE2ESimplePipeline.java | 确定 |
| N39 | MemoryKeyedStateBackend 序列化无容量管理，状态大时 OOM | MemoryKeyedStateBackend.java | 有趣的猜测 |
| N40 | DataStreamImpl 声称 Serializable 但 environment 不可序列化 | DataStreamImpl.java L46 | 确定 |
| N41 | StreamSinkOperator.restoreState 无条件 rollback — savepoint 恢复会误回滚 | StreamSinkOperator.java L87-95 | 很可能 |

---

## Round 2 总评

Round 2 深挖执行路径和状态管理后，发现了比 Round 1 更严重的问题：

### 最关键的发现

1. **N17（累加器不重置）**：这是与 Round 1 发现 1 叠加的底层 bug。`addWindowElement` 的类型腐蚀（R1-1）和 `MemoryInternalAppendingState.add()` 的累加器膨胀（N17）形成 bug 链：窗口聚合从状态管理层到算子层都存在正确性问题。

2. **N19（TimestampsAndWatermarks 未处理）**：这意味着 `execute()` fast path 中 event-time 语义完全不工作。这是功能性缺失，不是质量问题。

3. **N22（ValueStateDescriptor 丢弃 typeInfo）**：API 合同违背，影响所有使用此构造器的代码。

### Round 2 盲区自评

1. **Watermark 传播细节**：没有追踪 watermark 从 source 到 sink 的完整传播路径（需要 event-time 工作，而 fast path 中不工作）。
2. **Trigger 生命周期**：没有验证 Trigger 的 `onMerge`/`clear` 在合并窗口场景下的完整调用链。
3. **序列化兼容性**：只抽样检查了几个类的 Serializable 声明。
