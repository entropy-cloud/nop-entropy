# 维度 08：性能候选与基准缺口

- 审计对象：nop-stream（live code HEAD，仓库 `/Users/abc/app/nop-entropy-wt/nop-entropy-master`）
- 审计日期：2026-10-06
- 审计者：盲审 subagent（dimension 08）
- 复核状态：所有条目均为「未复核」

## 审计范围与方法

**范围（热路径清单）**：
1. StreamRecord 序列化/传输：`nop-stream-runtime` transport（`RemoteResultPartition`、`RemoteInputChannel`、`DataPlaneMessageServiceAdapter`、`KafkaStringWireCodec`/`PulsarStringWireCodec`/`SysDaoWireCodec`、`DataPlaneWireSupport`）、`nop-stream-core` 本地数据面（`RecordWriter`、`RecordReader`、`ResultPartition`、`InputGate`、`InputChannel`）。
2. `BufferPool`（per-job 许可池）。
3. 状态访问：`MemoryKeyedStateBackend`/`AbstractMemoryState` 家族、`TypedNamespaceAndKey`、`RocksDBKeyedStateBackend`/`RocksDBKeyEncoder`/`RocksDBValueState`（读写路径）。
4. 窗口触发：`WindowOperator`、`OverWindowOperator`、`MergingWindowSet`、`HeapInternalTimerService`、`TimestampsAndWatermarksOperator`、触发器（`ContinuousEventTimeTrigger` 等）。
5. NFA 转移：`NFA`（doProcess/advanceTime）、`SharedBuffer`/`SharedBufferAccessor`、`CepOperator`（buffer/drain/ledger）。
6. keyed 路由哈希：`HashPartitionRouter`、`KeyExtractingOutput`、`KeyGroupAssignment`。
7. checkpoint 序列化：`MemoryStateSerDe`、`CheckpointSerDe`（runtime）、`RocksDBSnapshotSerDe`（采样）。
8. 辅助算子：`EquiJoinOperator`、`PerKeyOrderedBuffer`、`ChainingOutput`、`StreamTaskInvokable` 主循环。

**方法**：静态通读热路径源码，逐候选论证触发频率（每记录 / 每窗口 / 每 barrier / 每 checkpoint）；对照 `nop-benchmark/nop-benchmark-stream` 现有基准类（源码级 14 个，见基准缺口一节的口径说明）识别"关键热路径无 JMH 基准"清单。遵守排除规则：不报"理论上可以用 X 更快"的猜测；与 Flink 参考实现同构的算法形态（如 `MergingWindowSet` 每记录重建、`SharedBuffer.advanceTime` 的 eventsCount 扫描本体、`HeapInternalTimerService` 的 TreeMap+Set 结构）不做"次优"指控，只对 nop-stream 在参考实现之上**额外引入**的结构性成本定罪。JDK/库已良好优化的路径（HashMap/CHM 良好散列、`LinkedBlockingQueue` 双锁分离）不报。

**盲审纪律**：未读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/、ai-dev/logs/ 下任何文件；仅审计 live code（HEAD）与 docs-for-ai/01-repo-map/module-groups.md 的流处理引擎一节。

---

## 第 1 轮（初审）

### [PF-01] BufferPool 采用全局公平信号量，构成每记录一次的作业级单点串行化

**文件+行号**：
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/buffer/BufferPool.java:39,56,60-73,91-94`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java:259`（每作业一个共享池）
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:346-376`（每记录 write 路径 acquire）、`397-399,430-432`（每记录 read 路径 release）

**证据片段**（BufferPool.java:38-57, 91-94）：
```java
/** Fair semaphore so cross-partition permit acquisition is FIFO (no starvation). */
private final Semaphore permits;
...
public BufferPool(int totalCapacity) {
    ...
    this.permits = new Semaphore(totalCapacity, true);   // fair=true
}
...
@Override
public void release() {
    permits.release();
}
```

**严重程度**：P2

**现状**：`GraphExecutionPlan.build()` 为每个本地执行计划创建一个 `BufferPool(DEFAULT_GLOBAL_BUFFER_CAPACITY)`，该池被计划内**所有**本地 `ResultPartition` 共享（类 javadoc 明示 "shared by all local ResultPartition instances"）。池实现为 `new Semaphore(totalCapacity, true)` —— 公平（FIFO）信号量。生产侧 `ResultPartition.enqueueWithBackpressure` 对**每条记录**执行 `bufferPool.acquire()`，消费侧 `ResultPartition.read(...)` 对**每条记录**执行 `bufferPool.release()`。

**风险（含触发频率论证）**：触发频率 = 每记录两次全局同步原语操作（写路径 acquire、读路径 release）。公平信号量的结构性代价：(a) 即使许可空闲，每次 `acquire()` 也要先走 `hasQueuedPredecessors()` 检查（volatile 读 AQS 队列头尾），无竞争基线比非公平模式多一次跨核 volatile 读序列；(b) 有竞争时严格 FIFO 交接——每次 release 只唤醒队头一个 park 线程，且许可必须经"唤醒→上下文切换→重新 CAS"交接，作业整体吞吐上界被压到 ≈ 1/交接延迟（每次交接约 1-10µs，对应全作业 <0.1M-1M rec/s 的池路径天花板），N 个 task 线程全部串行通过这一个信号量。这是"全局/粗粒度锁被每记录高频获取"的典型锁竞争面（维度 2 交叉命中）。公平性是文档化的有意选择（防止单分区饿死其他分区），但公平策略把"背压语义"与"逐记录 FIFO 交接"绑定了：背压只需要"池满时阻塞"，不需要"每次许可移交都排队"——批量记账（一次 acquire/release 携带多个许可）或非公平信号量 + 独立的防饿调度都能保住跨分区公平而不逐记录付 AQS 握手税。注意 `BufferPoolPermitBench` 的 javadoc 已明确承认该 AQS 握手成本并把"批量许可记账"列为候选，说明这是已知且待量化的结构性热点，而非误报。

**建议**：以 `BufferPoolPermitBench` 为口径先补多写者竞争档基准（见 BG-1），再实施批量许可记账（per-partition 局部记账 + 批量 acquire/release）或改非公平信号量 + 分片池（per-vertex 或 per-edge 池）消除全局单点；无论哪种方案，保持"池满阻塞"的背压契约与既有 `getGlobalUsage` 观测口径不变。

**信心水平**：高（结构性证据充分；公平语义的 trade-off 已被代码注释与基准类注释双重确认）。

**误报排除**：`ResultPartition` 内部队列是 `LinkedBlockingQueue`（putLock/takeLock 双锁分离），不是竞争点，已排除；`TaskMailbox`/`LocalSourceCoordinator` 的 synchronized 均在控制面低频路径，已排除。

**复核状态**：未复核

---

### [PF-02] PerKeyOrderedBuffer.sortedTimestamps 对已排序 TreeMap keySet 复制并重排，EquiJoinOperator 每记录调用一次

**文件+行号**：
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java:216-225`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java:287-303`（emitMatches，每记录）、`305-313`（completeUnmatched，每水印）

**证据片段**（PerKeyOrderedBuffer.java:217-225 与 EquiJoinOperator.java:289-291）：
```java
public List<long[]> sortedTimestamps(K key) {
    TreeMap<long[], V> tree = buffers.get(key);
    if (tree == null) {
        return Collections.emptyList();
    }
    List<long[]> keys = new ArrayList<>(tree.keySet());
    keys.sort(TsSeqComparator.INSTANCE);      // TreeMap.keySet() 本就按 comparator 序迭代
    return keys;
}
```
```java
for (long[] tsKey : buffer.sortedTimestamps(otherKey)) {
    JoinSideRecord<Object> other = buffer.valueAt(otherKey, tsKey);   // 再逐 key O(log n) 查值
```

**严重程度**：P2

**现状**：`sortedTimestamps` 先把 `TreeMap.keySet()` 整体复制为 `ArrayList`（O(n) 分配），再对该已按 `TsSeqComparator` 排好序的集合调用 `keys.sort(...)`（O(n log n) 比较 + TimSort 一次完整运行）。`EquiJoinOperator.emitMatches` 对**每条到达记录**调用一次（对侧 buffer 全量），随后又用 `valueAt(key, tsKey)` 逐 key 回查（每次 O(log n)），而 `keySet()` 复制出的就是排序后的 key，若直接迭代 `entrySet()` 可同时拿到 key 和 value。

**风险（含触发频率论证）**：触发频率 = 每记录一次；n = 对侧 key 下已缓冲记录数。equiv-join 的 O(n·m) 匹配扫描本身是语义必需的，但其中"复制 + 冗余重排 + 逐条回查"三段是纯浪费：排序一个本已有序的集合（TimSort 对有序输入仍要 O(n) 次比较确认 + 完整运行检测，无序输入最坏 O(n log n)），加上每记录一次 O(n) 数组分配。对高吞吐 join 边（每秒 10⁴-10⁶ 记录 × 对侧缓冲 10³-10⁵ 条），冗余部分（复制+排序）即达每记录 10⁴-10⁶ 量级比较，是匹配循环内**可整体消除**的最大单项成本。同类的 `OverWindowOperator`（每水印）与 `completeUnmatched`（每水印）调用频率低一档，随 PF-02 修复一并受益。

**建议**：`sortedTimestamps` 删除 `keys.sort(...)`（TreeMap 迭代序即 comparator 序）；在 `PerKeyOrderedBuffer` 增加 `sortedEntries(key)` 直接返回迭代 `entrySet()` 的视图（或惰性视图），`emitMatches` 改用它，去掉逐 `valueAt` 回查。`sortedTimestamps` 的 javadoc 需同步改写（现实现注释未声明"输入已有序"这一事实）。

**信心水平**：高（冗余排序是可机械证明的：`TreeMap.keySet().iterator()` 按 comparator 升序迭代，JDK 契约保证）。

**误报排除**：`sortedView`（`new ArrayList<>(tree.values())`，无重排）不在此列；`trimToCount*` 的"复制+排序"发生在每水印路径（低频），归入 PF-03 一并评估，不单独立项。

**复核状态**：未复核

---

### [PF-03] PerKeyOrderedBuffer.trimToWatermarkWithKeys 全量扫描 + 逐条 remove，同类 trimToWatermark 已有 headMap().clear() 的 O(d·log n) 模式

**文件+行号**：
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java:286-304`（WithKeys 变体）、`239-250`（高效对照实现）
- 调用方：`EquiJoinOperator.java:346-352`（每水印，hash 形态对每个 buffer key）、`OverWindowOperator.java:216,257`（每水印每 key）

**证据片段**（PerKeyOrderedBuffer.java:286-299 与同文件 245 行对照）：
```java
public List<long[]> trimToWatermarkWithKeys(K key, long watermark) {
    ...
    List<long[]> dropped = new ArrayList<>();
    for (long[] tsKey : tree.keySet()) {          // O(n) 全量扫描（含存活元素）
        if (tsKey[0] <= watermark) {
            dropped.add(tsKey);
        }
    }
    for (long[] tsKey : dropped) {
        tree.remove(tsKey);                       // O(d·log n) 逐条删除
    }
```
```java
// 同类 trimToWatermark（245 行）已是高效形态：
tree.headMap(new long[]{watermark, Long.MAX_VALUE}).clear();   // O(d·log n)，无全量扫描
```

**严重程度**：P3

**现状**：`trimToWatermarkWithKeys`（返回被删条目 exact key 的变体，供调用方同步清理 keyed-state 镜像）对整棵树做全量 keySet 扫描，再逐条 `remove`。同一类中不带 keys 返回值的 `trimToWatermark` 已经使用 `headMap(...).clear()`，二者行为等价（headMap 边界即 `ts <= watermark`），只是是否需要回传被删 key 列表的差别。

**风险（含触发频率论证）**：触发频率 = 每水印每 key（`EquiJoinOperator` hash 形态每水印对**所有** buffer key 各一次；`OverWindowOperator` 每水印每 key 一次）。单次成本 O(n)（n=该 key 缓冲条数）+ O(d·log n)。相对每记录路径（PF-02）低一档频率，故定 P3；但 `EquiJoinOperator` 的 hash 形态把它乘以 K（buffer key 数），水印高峰时为 O(K·n)。同类内两种实现并存，说明高效模式在该数据结构上已被验证可行，WithKeys 变体是漏改而非设计约束。

**建议**：`trimToWatermarkWithKeys` 改为：取 `headMap(bound)`，在其 `keySet()` 上收集（被删的 d 个 key，headMap 视图只含待删元素，收集即 O(d)），随后 `headMap(bound).clear()`（O(d·log n)，单次结构修复）。`trimToCountWithKeys` 同理可用 `keySet()` 前缀迭代（TreeMap 有序，前 `drop` 个即待删集合）替代全量复制+排序+逐删。

**信心水平**：高。

**误报排除**：`MergingWindowSet.persist` 每记录全量重写 mapping 列表——与 Flink `WindowOperator` 参考实现同构（Flink 同样每元素 new MergingWindowSet + persist），按口径排除；`MemoryKeyedStateBackend.cleanupExpiredEntries` 全表扫描有 60s nanoTime 门控（低频），排除。

**复核状态**：未复核

---

### [PF-04] WindowOperator.removeTriggerAccumulators 每窗口清理对全表做 O(W) 前缀扫描，W 个活窗口时代际成本 O(W²)

**文件+行号**：
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:1020-1026`
- 相关：`2003-2031`（`Context.getSimpleAccumulator`，状态键 `"trigger_" + key + "\u0000" + window + "\u0000" + name`）

**证据片段**（WindowOperator.java:1020-1026）：
```java
private void removeTriggerAccumulators(K key, W window) {
    if (triggerAccumulators == null || triggerAccumulators.isEmpty()) {
        return;
    }
    String prefix = "trigger_" + key + STATE_KEY_SEPARATOR + window + STATE_KEY_SEPARATOR;
    triggerAccumulators.keySet().removeIf(stateKey -> stateKey.startsWith(prefix));
}
```

**严重程度**：P3

**现状**：`triggerAccumulators` 是 `Map<String, SimpleAccumulator<?>>`（键为 `key + 分隔符 + window + 分隔符 + descriptorName` 复合字符串）。清理某 `(key, window)` 的全部条目时，对**整个 map** 做 `removeIf(startsWith(prefix))` —— 无任何 (key,window)→条目 反向索引。该方法在每个窗口的 purge、cleanup timer、merge 三个生命周期终点各被调用（含 merge 回调内对每个 merged window 一次）。

**风险（含触发频率论证）**：触发频率 = 每窗口一次（每 key 每窗口生命周期 1-3 次），非每记录。单次成本 O(W)（W = 当前活窗口的 accumulator 条目数，`startsWith` 逐条构造前缀比较），一个窗口代际（全部窗口走一遍生命周期）合计 O(W²) 次字符串前缀比较。W 的量级 = 活跃 key 数 × 每 key 并发窗口数（sliding 窗口每 key 可达 slide-count 量级）；高基数 key 场景（10⁴-10⁶ 活窗口）下单次清理即为毫秒-十毫秒级，且发生在 watermark 驱动的 timer 热循环内（`onTimer` 的 purge/cleanup 分支），会拉长单次 watermark 推进延迟。另注意条目键本身的构造（`getSimpleAccumulator` 每记录字符串拼接 + HashMap 查找）与 Flink 的 per-record ValueState 访问同构，不在本条指控范围——本条只针对**清理路径缺反向索引**这一结构性缺口。

**建议**：将复合键结构改为二级结构 `Map<WindowKey(key,window), Map<String, SimpleAccumulator<?>>>`（外层键用 `TypedNamespaceAndKey` 式不可变复合对象，hash 已有 memoize 先例），清理退化为外层 map 单键 `remove`，O(1)。或保留扁平 map 但另维护 `(key,window) → List<stateKey>` 反向索引，清理时按索引删除。

**信心水平**：中高（结构性论证成立；实际影响幅度取决于 W 的部署规模，未见现场数据，故不升 P2）。

**误报排除**：`getSimpleAccumulator` 每记录的键拼接（Flink 同构）；`paneTracking.remove(paneKey)` 单键 remove（O(1)）；CepOperator ledger 的 `timers.removeIf(timer -> timer <= time)` 作用在**单 key 自己的** TreeSet 上（非全表），排除。

**复核状态**：未复核

---

### [PF-05] SharedBuffer.advanceTime 每次 NFA.advanceTime 全量扫描 eventsCount 与全量 eventsBufferCache keySet，且 drainDueBuckets 每个 due timestamp 各调一次 → O(due × 存量) / 水印 / key

**文件+行号**：
- `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:278-290`
- 调用链：`CepOperator.java:1216-1235`（drainDueBuckets 的 per-timestamp 循环内 `advanceTime(nfaState, timestamp)`，1223 行）→ `NFA.java:393`（`sharedBufferAccessor.advanceTime(timestamp)`）→ `SharedBufferAccessor.java:78-80`
- 缓存槽配置：`SharedBufferCacheConfig.java`（默认槽位来自 `NopCepConfigs.CEP_SHARED_BUFFER_*_CACHE_SLOTS`）

**证据片段**（SharedBuffer.java:278-290）：
```java
void advanceTime(long timestamp, Object scope) {
    Iterator<Long> iterator = eventsCount.keys().iterator();   // O(该 key 存量时间戳数)
    while (iterator.hasNext()) {
        Long next = iterator.next();
        if (next < timestamp) {
            iterator.remove();
        }
    }
    eventsBufferCache.asMap().keySet().removeIf(cacheKey -> {  // O(全 cache 存量，跨所有 key)
        EventId eventId = matchingId(cacheKey, scope);
        return eventId != null && eventId.getTimestamp() < timestamp;
    });
}
```

**严重程度**：P3

**现状**：`SharedBuffer.advanceTime` 做两段全量扫描：(1) `eventsCount`（keyed `MapState<Long,Integer>`，每 key 每个未过期事件时间戳一个条目）全迭代删除过期项；(2) `eventsBufferCache` 的**全部**缓存键做 `removeIf`，且谓词里对每个 `ScopedId` 做 `scope.equals` 比较——scoped 模式下 cache 由该算子**所有 key 共享**（key-scoped 键、close 不清），因此每 key 的 drain 都要横扫其他 key 的缓存条目。第一段扫描与 Flink `SharedBuffer.advanceTime` 参考实现同构（按口径不单独定罪）；第二段 cache 横扫是 nop-stream 在参考实现之上**额外引入**的（Flink 无此缓存层）。放大系数来自调用点：`CepOperator.drainDueBuckets` 在 due 时间戳循环内**每个时间戳**调用一次 `NFA.advanceTime`，而每次 NFA.advanceTime 都触发上述两段全量扫描。

**风险（含触发频率论证）**：触发频率 = 每 (key × due timestamp) 一次 drain，即每水印每带待处理 timer 的 key 乘以其 due 桶数 d。单次成本 O(eventsCount 存量 + cache 全局存量)；合计 O(d × (E_k + C))，其中 E_k 为该 key 未清时间戳条目数（≈ 窗口跨度 × 事件率的去重时间戳数），C 为全算子缓存条目数（默认槽位量级，跨 key 汇总）。高事件率 + 长窗口 + 多 key 时，每次 watermark 推进的 CEP 侧成本呈乘性放大——这是"每事件线性扫描 × 每事件调用"的 O(n·m) 形态。`SharedBufferRegisterBench`/`NfaProcessBench` 只覆盖 register/process，未覆盖 advanceTime 扫描，该成本目前不可见。

**建议**：两层改法（可独立落地）：(1) `drainDueBuckets` 把 per-timestamp 的 `advanceTime` 上提为循环外一次 `advanceTime(limitTimestamp)`（watermark 语义本就允许一次清到上限；语义差异仅在 NFA 内部的中间 advanceTime 触发，需按 Flink 语义核对每桶先 advance 再 process 的次序——若必须保序，至少把 SharedBuffer 层的清理合并为每 drain 一次）；(2) `eventsCount` 的过期清理利用 CepOperator 已有的有序 due 集（TreeSet headSet）定向删除，替代全表迭代；cache 的 `removeIf` 按 scope 先过滤（scoped 模式下 `matchingId` 已具备判断，可在进入谓词前用 `ScopedId.hasScope` 短路非本 scope 的键，避免无谓比较——收益有限，主收益在前两点）。

**信心水平**：中（结构性乘性放大成立；eventsCount 扫描本体与 Flink 同构，故不升 P2；实际幅度依赖窗口长度/事件率部署参数）。

**误报排除**：`registerEvent` 的碰撞循环 `containsKey + hasEventInBuffer` 是防 EventId 冲突的守卫（碰撞即异常场景，稳态一次 HashMap get），排除；`processWatermark` 的 ledger 全迭代已被前轮优化为"live 迭代无快照拷贝"（代码注释明确记录），非新增问题，排除。

**复核状态**：未复核

---

### [PF-06] 远程数据面每记录双重 JSON 序列化：payload 先 stringify 进 envelope，toWire 再把整个 envelope（含转义后的 payload 字符串）重新 stringify

**文件+行号**：
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/transport/StreamElementCodec.java:88-96`（encode：`JsonTool.stringify(record.getValue())`）
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/DataPlaneWireSupport.java:35-47`（toWireMap 把 payload 字符串原样放入 map）
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/KafkaStringWireCodec.java:48-50`（`JsonTool.stringify(DataPlaneWireSupport.toWireMap(envelope))`；`PulsarStringWireCodec` 同构）
- 接入点：`RemoteResultPartition.write`（118-131 行，每记录 encode + send）

**证据片段**（StreamElementCodec.java:90 与 KafkaStringWireCodec.java:48-49）：
```java
// 第一层：payload → JSON 字符串
Object serializedPayload = record.getValue() != null ? JsonTool.stringify(record.getValue()) : null;
...
// 第二层：整个 envelope（payload 字符串作为一个字段）→ 再次 JSON 字符串（含转义）
public Object toWire(StreamMessageEnvelope envelope) {
    return JsonTool.stringify(DataPlaneWireSupport.toWireMap(envelope));
}
```

**严重程度**：P3

**现状**：对 Kafka/Pulsar 这类 String-schema 后端，每条远程记录的发送侧做两次完整 JSON 序列化：先 `stringify(payload)` 得到 payload 字符串，随后 `toWireMap` 把该字符串作为 `"payload"` 字段放进 map、`stringify(map)` 时对 payload 的引号/特殊字符做全量转义（wire 体积随之膨胀）；接收侧对称地 `parseBeanFromText` 解析外层 envelope，再在 `StreamElementCodec.decode` 里对 payload 字符串做第二次 parse。往返共 2 次 stringify + 2 次 parse，其中外层 envelope 的 stringify/parse 纯粹是信封框架成本。该行为是文档化的有意设计（envelope 统一信封 + backend 无关；`DataPlaneWireSupport` javadoc 声明"不改变 envelope 自身序列化格式"为 Stage 40 Non-Goal），且 `StreamElementCodecRoundTripBench.fullRoundTrip` 已对全链路计时——成本是已知、可测的。

**风险（含触发频率论证）**：触发频率 = 每记录每远程边。发送侧多出的 envelope stringify 是 O(envelope 总长) 的字符转义扫描，payload 占比越高浪费越大；对以远程 transport 为主干的多 JVM 部署，这占每记录固定 CPU 的一个可测份额（具体份额可从 `StreamElementCodecRoundTripBench.fullRoundTrip` 减 `decodeOnly` 差值估出）。定 P3 而非 P2 的原因：成本已被基准显式量化管理、设计取舍已文档化、且修改 wire 格式属跨兼容性变更。

**建议**：不动信封语义的前提下有两个低风险选项：(1) 为 String-schema 后端定义"payload 即 Kafka/Pulsar 消息体、信封字段进消息 header"的 wire 变体，消除外层包裹（一次 stringify/一次 parse）；(2) 至少为 `toWire` 的 map 构造做 payload 旁路（payload 字符串直接拼进目标 JSON，不做 map 中转），减少一次中间 LinkedHashMap 分配。任一选项实施前后以 `StreamElementCodecRoundTripBench` 同口径对比。

**信心水平**：中（结构性事实确凿；但"明显次优"的判定受制于该设计的有意性——作为候选提出，交由复核裁定是否接受 wire 格式演进）。

**误报排除**：`RemoteResultPartition.write` 每记录的 `typeRegistry.getOutputTypeClassName(edgeId)`（CHM.get 固定键，~10ns 量级，被 stringify 主导）——排除，见误报排除汇总；`StreamElementCodec.decode` 已缓存 `Class.forName`（DECODED_VALUE_TYPES），非问题。

**复核状态**：未复核

---

## 深挖第 2 轮追加

深挖第 2 轮基于初审发现的四类模式（每记录全局锁、已排序集合的冗余复制/重排、O(n) 全表扫描 + 逐条删、per-event 双重序列化）做了同族复查，**追加 0 条新发现**。复查记录（用于证明覆盖，不构成发现）：

1. **全局锁模式**（自 PF-01）：全仓 `new Semaphore(` 仅 BufferPool 一处；`synchronized` 命中集中在控制面（`TaskMailbox`、`LocalSourceCoordinator`、`TwoPhaseCommitSinkFunction.pendingCommits`、`CheckpointBarrierTracker`），均为每 barrier/每 mail/每 checkpoint 频率，非每记录。`ResultPartition` 的 `LinkedBlockingQueue` 为 put/take 双锁分离，非竞争点。
2. **冗余排序/复制模式**（自 PF-02）：`sortedView` 无重排；`trimToCount*` 的复制+排序在每水印路径（已并入 PF-03 建议）；`MergingWindowSet.persist` 每记录全量重写与 Flink 参考同构，按排除规则不立项。
3. **O(n) 扫描模式**（自 PF-03/04/05）：`CepOperator` ledger 清理的 `removeIf` 作用在单 key 自己的 TreeSet 上（O(k)，k=该 key 待清条目），非全表；`OverWindowOperator.processWatermark` 遍历 `buffer.keys()` 后本就要逐 key emit，属语义必需；`AbstractMemoryState.applyWholeStorageMigration`、`MemoryStateSerDe` 全表循环均在 restore/snapshot（每 checkpoint）频率，已有 `CheckpointSerDeBench` 邻近覆盖。
4. **双重序列化模式**（自 PF-06）：`SysDaoWireCodec`/`PulsarStringWireCodec` 与 Kafka 版同构（同一定罪）；本地进程内路径（`IdentityWireCodec`/`LocalMessageService`）零序列化，无此问题。`MemoryStateSerDe.freezeRawValue` 的防御性拷贝是 R5 ST-01 正确性修复（barrier 后异步持久化的竞态防护），非冗余。
5. **正面确认（非发现，避免误改回归）**：`MemoryKeyedStateBackend.cachedNamespaceAndKey` 单槽缓存、`TypedNamespaceAndKey` hash memoize、`RocksDBKeyedStateBackend.cachedStorageKeyFor` 单槽编码缓存、`WindowOperator.windowNamespace` 单槽 memo、`SharedBuffer.ScopedId` 预计算 hash、`TimerEntry` 手工混 hash、`StreamElementCodec.DECODED_VALUE_TYPES` 类缓存——这些热点优化均已就位，同位置不需再改。

---

## 基准缺口清单

**现有基准盘点（口径说明）**：`nop-benchmark/nop-benchmark-stream/src/main/java` 下实际存在 **14** 个基准类；`docs-for-ai/01-repo-map/module-groups.md` 的"13 基准类"按其列举的热路径口径（codec、RocksDB/Memory 状态、窗口、timer、NFA、SharedBuffer、CheckpointSerDe、远程传输写、CepOperator、InputGate 读取循环、BufferPool 许可、处理时间触发延迟）计数，未含 `ConnectorInvokeBench`。建议 docs 侧把计数口径改为"13+1"或直接写 14，消除歧义。

| # | 现有基准类 | 已覆盖 |
|---|---|---|
| 1 | StreamElementCodecRoundTripBench | encode→toWire→fromWire→decode 全链路 + decodeOnly（KafkaStringWireCodec） |
| 2 | RemoteTransportWriteBench | RemoteResultPartition 锁内 send（sendDelay × fanout 参数），encode+send 真实路径 |
| 3 | MemoryKeyedStateBench | valueGetUpdate / internalListAdd / aggregatingAdd（Memory 后端，无 TTL、无 MapState） |
| 4 | RocksDbKeyedStateBench | valueGetUpdate / listAdd / aggregatingAdd（无 MapState） |
| 5 | WindowOperatorProcessElementBench | tumbling + EventTimeTrigger + aggregate + Memory 后端的 processElement |
| 6 | TimerServiceBench | registerTimer / advanceWatermark（HeapInternalTimerService） |
| 7 | NfaProcessBench | NFA.process（事件转移） |
| 8 | SharedBufferRegisterBench | registerEventAndPut |
| 9 | CepOperatorBench | 算子层 eventTime buffer / drain |
| 10 | CheckpointSerDeBench | runtime EpochManifest/TaskStateSnapshot serialize / deserialize / no-checksum 档 |
| 11 | InputGateReadLoopBench | **单通道** readLoop 组（producer/consumer 各 1 线程，gap 参数） |
| 12 | BufferPoolPermitBench | 无竞争 acquire/release 对 + 1-1 pingPong 组（capacity 1/64 参数） |
| 13 | ProcessingTimeDriverLatencyBench | registerAndAwaitFire |
| 14 | ConnectorInvokeBench | file/jdbc sink、file source invoke（在 13 类口径之外） |

**缺口清单（关键热路径无 JMH 基准，按优先级排序）**：

| # | 缺口 | 热路径与触发频率 | 现状 | 建议基准形态 |
|---|---|---|---|---|
| BG-1 | BufferPool 多写者竞争档 | 每记录 acquire / 每记录 release；N 个 task 线程共享一池（PF-01 的量化前提） | 仅有 1 生产-1 消费 pingPong 与无竞争对 | @Group 多 @GroupThreads(4/8) 生产者 + 消费者，对比 fair/非 fair/批量记账实施前后 |
| BG-2 | InputGate 多通道读取循环 | 每记录：round-robin 轮询 + `checkAlignmentElapsed`（每 read 入口）+ barrier 对齐 blockedChannels 集合操作 + watermark 合并（`getCurrentWatermark`/`minWatermarkExcluding`） | 现基准仅单通道直通，`readMultiChannel` 全路径零覆盖 | 4/16 通道 gate + 常压记录流；另加"对齐中"档（一通道持 barrier）测对齐开销与超时门成本 |
| BG-3 | keyBy 路由哈希 | 每记录：`RecordWriter.emit` → `HashPartitionRouter.selectChannel`（`record.getValue().hashCode()` + floorMod）或 `IPartitioner.partition` | 无任何基准 | RecordWriter.emit（1/N 分区）微基准，含 String key 与 Long key 两档 |
| BG-4 | MemoryStateSerDe snapshot/restore | 每 checkpoint：每条目 namespace/key 序列化 + `freezeRawValue` 防御拷贝 + schema fingerprint + TTL 过滤（CheckpointSerDeBench 只测 runtime manifest 层） | 无直接基准 | 构造 MemoryKeyedStateBackend（Value/Map/InternalList 三族 × 10k 条目）跑 snapshotState/restoreState |
| BG-5 | 窗口分配变体与 evictor | 每记录：SlidingEventTimeWindows.assignWindows（每记录生成 slide-count 个窗口对象）、session/merging 路径（MergingWindowSet.addWindow + persist 全量重写）、CountEvictor/TimeEvictor 路径 | 现 bench 仅 tumbling + EventTimeTrigger + aggregate | WindowOperatorProcessElementBench 加 @Param(assigner=tumbling/sliding/session) + evictor 档 |
| BG-6 | EquiJoin / OverWindow / PerKeyOrderedBuffer | 每记录（join 匹配循环 sortedTimestamps + valueAt，PF-02/03 量化前提）与每水印（trim 路径） | 无基准 | PerKeyOrderedBuffer 微基准（add/sortedTimestamps/trimToWatermarkWithKeys，n=1k/10k/100k）+ EquiJoinOperator.emitMatches 端到端 |
| BG-7 | MapState get/put 与 TTL 启用状态访问 | 每记录：window-contents 回退路径（`MapState.get+put`）、CepOperator `elementQueueState`/SharedBuffer 三个 MapState、TTL sidecar（`TtlContext.recordRead/writeEviction`）附加成本 | state bench 仅 value/list/aggregating 且无 TTL | MemoryKeyedStateBench/RocksDbKeyedStateBench 增加 mapGetPut 与 @Param(ttl=on/off) 档 |
| BG-8 | SharedBuffer.advanceTime 清理扫描 | 每 drain 每 due timestamp：eventsCount 全迭代 + cache keySet removeIf（PF-05 量化前提） | 无基准 | 预置 E=1k/10k 时间戳条目 + cache 满，测单次 advanceTime 成本随存量的曲线 |
| BG-9 | trigger accumulator 状态键路径 | 每记录（ContinuousEventTimeTrigger.onElement → getSimpleAccumulator 字符串拼接 + HashMap 查找）与每窗口清理（removeTriggerAccumulators 前缀扫描，PF-04 量化前提） | 无基准（窗口 bench 未单列 trigger state 成本） | W=1k/10k 活窗口下 getSimpleAccumulator + removeTriggerAccumulators 微基准 |

---

## 最终保留项（表格索引）

| ID | 标题（摘要） | 位置 | 触发频率 | 严重程度 | 信心 | 复核状态 |
|---|---|---|---|---|---|---|
| PF-01 | BufferPool 全局公平信号量：每记录 acquire/release 的作业级单点串行化 | nop-stream-core/execution/buffer/BufferPool.java:56 + GraphExecutionPlan.java:259 | 每记录 ×2 | P2 | 高 | 未复核 |
| PF-02 | sortedTimestamps 对已排序 keySet 复制+重排，EquiJoin 每记录调用 | nop-stream-core/common/buffer/PerKeyOrderedBuffer.java:217-225 + operators/join/EquiJoinOperator.java:289 | 每记录 | P2 | 高 | 未复核 |
| PF-03 | trimToWatermarkWithKeys 全量扫描+逐条删（同类已有 headMap().clear() 模式） | PerKeyOrderedBuffer.java:286-304 | 每水印每 key | P3 | 高 | 未复核 |
| PF-04 | removeTriggerAccumulators 每窗口清理 O(W) 前缀扫描，代际 O(W²) | nop-stream-runtime/operators/windowing/WindowOperator.java:1020-1026 | 每窗口 | P3 | 中高 | 未复核 |
| PF-05 | SharedBuffer.advanceTime 全量扫描 × drainDueBuckets 每 due 时间戳一次 → O(due×存量) | nop-stream-cep/nfa/sharedbuffer/SharedBuffer.java:278-290 + operator/CepOperator.java:1223 | 每水印每 key × due 桶数 | P3 | 中 | 未复核 |
| PF-06 | 远程数据面每记录双重 JSON 序列化（payload stringify + envelope stringify 转义包裹） | nop-stream-core/.../transport/StreamElementCodec.java:90 + nop-stream-runtime/transport/KafkaStringWireCodec.java:49 | 每记录每远程边 | P3 | 中 | 未复核 |

**严重程度分布**：P2 × 2（PF-01、PF-02），P3 × 4（PF-03 ~ PF-06）。P0/P1：无。

**误报排除汇总**（已论证并主动排除，供复核对照）：
- `RecordReader.read()`/`InputGate.read()` 每记录 `Optional` 分配——量级 ~16B，JIT/EA 影响下非结构性次优，排除。
- `RemoteResultPartition.write` 每记录 `TypeRegistry` CHM.get（固定键）——被 stringify 主导，排除。
- `MergingWindowSet` 每记录重建 + `persist` 全量重写——与 Flink `WindowOperator` 参考实现同构，按口径排除。
- `HeapInternalTimerService` 每次注册 cleanup timer 分配 `TimerEntry` + HashSet 去重——Flink 同构形态，排除。
- `CepOperator.processWatermark` ledger 全迭代——已优化为 live 迭代无快照拷贝（代码注释记录），排除。
- `MemoryKeyedStateBackend`/`RocksDBKeyedStateBackend` 的 (key,namespace) 单槽缓存与 hash memoize——已优化到位，正面确认。
- `MemoryStateSerDe.freezeRawValue` 防御性拷贝——正确性修复（异步持久化竞态防护），非冗余，排除。

**方法自检**：6 条发现全部满足"结构性论证 + 触发频率 + ≥9 行完整格式"；无"理论上可用 X 更快"型条目（PF-06 已显式标注设计有意为 P3 候选）；与 Flink 同构形态一律未定罪；基准缺口 9 条均可落到具体基准形态。全部结论仅基于 live code（HEAD）与 module-groups.md，未读取 ai-dev/ 任何审计记录。

---

## 维度复核结论（独立复核，2026-10-06）

复核者：独立审计复核员（与初审者无关）。方法：逐条亲读被引代码、追调用链核实触发频率、反证搜索遗漏的缓存/短路/批量机制、抽查 Flink 同构排除规则、对 `nop-benchmark/nop-benchmark-stream` 实际基准源码逐类核对 BG 缺口。现状归属仅参考 r5 summary.md（唯一允许的历史文件）。

| 发现 | 判定 | 现状归属 | 复核要点 |
|---|---|---|---|
| PF-01 | 保留（P2） | 未修复；R5 08-performance 已有同域条目（r5 summary 盲区自评"PF-01 档位为估算"），未进 2310-2317 任何修复批次 | 结构链完整验证：`BufferPool.java:56` 公平信号量；`GraphExecutionPlan.java:259` 每作业单池、`:395` 同一池注入全部本地 ResultPartition、`:301` build 强制池非空（无池旁路不存在于计划路径）；`enqueueWithBackpressure:350/357` 每记录 acquire、`read:398/431` 每记录 release。反证搜索：全仓 `new Semaphore(` 仅此一处（TaskManager 的为控制面容量信号量），无批量记账/缓存短路。缓和备注：`BufferPoolPermitBench` javadoc 自评"近畅通档（capacity=64）收益 <2%"——税负集中于背压挤压档，但背压正是池的存在场景，且公平 FIFO 交接在挤压下把全作业串行过单点，P2 成立不降 |
| PF-02 | 保留（P2） | 未修复（PerKeyOrderedBuffer 经 R5 KR-7/CC-11 批次只动了序列化面，sortedTimestamps 原样） | `PerKeyOrderedBuffer.java:217-225` 复制+`keys.sort(...)` 与引文一致；TreeMap keySet 迭代序即 comparator 序（JDK 契约），sort 冗余可机械证明；`EquiJoinOperator.processElement:197 → emitMatches:289` 每记录调用 + `valueAt` 逐条 O(log n) 回查均确认。反证：无缓存/短路。同文件 `trimToCountWithKeys:312-313` 存在同款冗余 sort（初审归入 PF-03 建议一并处理，归属合理） |
| PF-03 | 保留（P3） | 未修复 | `trimToWatermarkWithKeys:286-304` 全量 keySet 扫描+逐条 remove；同文件 `trimToWatermark:245` 已是 `headMap().clear()` 高效形态，"漏改而非设计约束"论证成立。调用频率核实：`EquiJoinOperator.completeAndTrimHash:224-233` 每水印对每 buffer key 各一次、`OverWindowOperator:216/257` 每水印每 key——每水印每 key 定级 P3 恰当 |
| PF-04 | 保留（P3） | 未修复 | `removeTriggerAccumulators:1020-1026` 对整 map `removeIf(startsWith)` 无反向索引确认；调用点 794（merge 回调每 merged window）/865/964/978 均为窗口生命周期终点（每窗口 1-3 次，非每记录）；键构造 `:2010` 与引文一致。`getSimpleAccumulator` 每记录键拼接被初审以 Flink 同构排除且未计入本条——口径正确。P3（依赖 W 规模）恰当 |
| PF-05 | 保留（P3） | 未修复（HEAD 仍为 per-timestamp advanceTime + cache 全局横扫） | 调用时序逐环验证：`CepOperator.drainDueBuckets:1216-1235` due 时间戳循环内 `:1223` 每 timestamp 调 advanceTime → `NFA.java:393` 无条件传导 → `SharedBufferAccessor:78-80` → `SharedBuffer.advanceTime:278-290` 两段全量扫描。`eventsBufferCache`（`:157` 单实例 Guava Cache，ScopedId 键、close 不清）跨 key 共享、每 key drain 横扫他 key 条目确认属实。Flink 同构口径应用正确：eventsCount 扫描本体未定罪、只定罪 nop 额外的 cache 层与 per-timestamp 放大。P3/中信心恰当 |
| PF-06 | 保留（P3） | 未修复（设计有意为 Stage 40 Non-Goal，属 wire 格式演进候选） | 双重序列化链路逐点确认：`StreamElementCodec.encode:90` stringify payload → `DataPlaneWireSupport.toWireMap:43` payload 字符串原样入 map → `KafkaStringWireCodec.toWire:48-50` 再 stringify 整 map（含转义膨胀）；`fromWire:62` parseBeanFromText 对称。`RemoteResultPartition.write:118-131` 每记录 encode+send。初审已因设计有意+成本被 `StreamElementCodecRoundTripBench` 显式管理而自定 P3——定级过程克制，维持 P3 |
| BG-1 | 保留 | 缺口属实 | BufferPoolPermitBench 实测仅 `acquireReleaseUncontended`（单线程）+ `pingPong` 组（@GroupThreads(1)×2），无多写者竞争档（4/8 生产者） |
| BG-2 | 保留 | 缺口属实 | InputGateReadLoopBench setup `:69-70` 单 InputChannel 单通道直通；无多通道/对齐中档。`InputGate.checkAlignmentElapsed:702/828` 每 read 入口机制存在，零基准覆盖 |
| BG-3 | 保留 | 缺口属实 | 全 bench 目录无 RecordWriter.emit/HashPartitionRouter/IPartitioner 基准（仅注释提及）；`HashPartitionRouter:33-35` 每记录 hashCode+floorMod 路径无任何测量 |
| BG-4 | 保留 | 缺口属实 | CheckpointSerDeBench 仅覆盖 runtime `EpochManifest/TaskStateSnapshot` manifest 层；MemoryStateSerDe 每条目 snapshot/restore（含 freezeRawValue/TTL 过滤）无基准 |
| BG-5 | **驳回** | 缺口不存在——现基准已覆盖 | 初审"现 bench 仅 tumbling + EventTimeTrigger + aggregate"与 HEAD 相反：`WindowOperatorProcessElementBench` 已有 `@Param({"TUMBLING","SLIDING","SESSION","EVICTOR"})`、`@Param({"MEMORY","ROCKSDB"})`、evictorSize `@Param({"100","1000"})`，setup `:96-116` 显式配置 `SlidingEventTimeWindows`/`EventTimeSessionWindows`（merging 路径）/`CountEvictor`。所提缺口内容已在 live 基准中实现 |
| BG-6 | 保留 | 缺口属实 | EquiJoin/OverWindow/PerKeyOrderedBuffer/sortedTimestamps 在 bench 目录零命中；PF-02/03 的量化前提无基准 |
| BG-7 | 保留 | 缺口属实 | 状态基准仅 value/list/aggregating 三族（MemoryKeyedStateBench:64-83、RocksDbKeyedStateBench 3 个 @Benchmark），无 MapState 档、无 TTL on/off 档 |
| BG-8 | 保留（表述修正） | 缺口部分属实 | **修正**：初审"NfaProcessBench 只覆盖 register/process，未覆盖 advanceTime 扫描"不准确——该 bench 每 invocation 显式调 `nfa.advanceTime`（:119），javadoc 明示"让 advanceTime 的修剪路径进入被测负载"，扫描路径已被小存量覆盖。缺口本体仍成立：单 key、within 窗口有界小存量（百条目级），缺 E=1k/10k 规模曲线与 drainDueBuckets 每 due 时间戳放大形态的覆盖——应表述为"规模/放大档缺失"而非"零覆盖" |
| BG-9 | 保留 | 缺口属实 | getSimpleAccumulator/removeTriggerAccumulators 在 bench 目录零命中；窗口 bench 未单列 trigger state 成本 |

**误报排除抽查（3 条独立验证，全部正确）**：(1) MergingWindowSet 每元素 new + persist——`WindowOperator.java:746/830/1338-1342` 确认每元素重建实例并 persist，与 Flink WindowOperator 参考同构，排除正确；(2) HeapInternalTimerService TimerEntry + HashSet 去重——`HeapInternalTimerService.java:42-43/98-99/121-122` 确认 TreeMap<Long,Set<TimerEntry>> 结构与 Flink 同构，排除正确；(3) `cachedNamespaceAndKey` 单槽缓存正面确认——`MemoryKeyedStateBackend.java:589-617` 属实。另核 `CepOperator:1091` ledger 清理确在单 key 自有 TreeSet 上（非全表），与初审排除口径一致。

**基准类盘点核对**：`nop-benchmark-stream/src/main/java` 实际 14 个基准类 + 2 个数据辅助类（BenchCepEvent/BenchPayload），与初审 14 类口径一致；`module-groups.md:23` 确写"13 基准类"且其列举口径不含 ConnectorInvokeBench——初审的计数澄清建议（改 13+1 或 14）成立，属文档微漂移。

**复核统计**：PF 6 条——保留 6（P2×2、P3×4，严重程度全部维持，无虚高降级）；BG 9 条——保留 8（其中 BG-8 表述修正）、驳回 1（BG-5，现基准已覆盖）。初审的排除规则应用、触发频率论证与定级克制度总体为高质量，主要瑕疵是 BG-5 未核对现基准的 @Param 演进、BG-8 低估了 NfaProcessBench 的既有覆盖。

## 最终保留项（复核后）

| 编号 | 复核后严重程度 | 文件路径 | 一句话摘要 | 现状归属 |
|---|---|---|---|---|
| PF-01 | P2 | /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/buffer/BufferPool.java（:56；装配 GraphExecutionPlan.java:259/395，每记录路径 ResultPartition.java:350/357/398/431） | 全作业共享的公平信号量被每记录 acquire/release，背压挤压时全作业串行过单点 AQS FIFO 交接 | 未修复；R5 同域条目（档位估算）未进修复批次 |
| PF-02 | P2 | /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java（:217-225；调用方 EquiJoinOperator.java:289） | sortedTimestamps 对本已有序的 TreeMap keySet 复制后再 sort，EquiJoin 每记录一次纯浪费 | 未修复 |
| PF-03 | P3 | /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java（:286-304，对照 :245） | trimToWatermarkWithKeys 全量扫描+逐条删，同类已有 headMap().clear() 高效形态未复用 | 未修复 |
| PF-04 | P3 | /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java（:1020-1026） | removeTriggerAccumulators 每窗口清理对全 accumulator map 做 O(W) 前缀扫描，无 (key,window) 反向索引 | 未修复 |
| PF-05 | P3 | /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java（:278-290；放大点 CepOperator.java:1223） | SharedBuffer.advanceTime 两段全量扫描（含跨 key 共享 cache 横扫）被 drainDueBuckets 每 due 时间戳调用一次，乘性放大 | 未修复 |
| PF-06 | P3 | /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/transport/StreamElementCodec.java（:90）+ /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/KafkaStringWireCodec.java（:48-50） | 远程 String-schema 数据面每记录双重 JSON 序列化（payload stringify + envelope stringify 转义包裹），设计有意的演进候选 | 未修复（Stage 40 Non-Goal，候选演进） |
| BG-1 | 缺口（P3 级治理项） | /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-benchmark/nop-benchmark-stream/src/main/java/io/nop/benchmark/stream/BufferPoolPermitBench.java | BufferPool 缺多写者竞争档基准（现仅 uncontended + 1-1 pingPong），PF-01 量化前提 | 缺口存在 |
| BG-2 | 缺口（P3 级治理项） | /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-benchmark/nop-benchmark-stream/src/main/java/io/nop/benchmark/stream/InputGateReadLoopBench.java | InputGate 缺多通道读取循环与 barrier 对齐档基准（现仅单通道直通） | 缺口存在 |
| BG-3 | 缺口（P3 级治理项） | /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/HashPartitionRouter.java（被测面） | keyBy 路由哈希（每记录 hashCode+floorMod）零基准 | 缺口存在 |
| BG-4 | 缺口（P3 级治理项） | /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-benchmark/nop-benchmark-stream/src/main/java/io/nop/benchmark/stream/CheckpointSerDeBench.java（对照面） | MemoryStateSerDe 每条目 snapshot/restore（freezeRawValue/TTL 过滤）无直接基准 | 缺口存在 |
| BG-6 | 缺口（P3 级治理项） | /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java（被测面） | EquiJoin/OverWindow/PerKeyOrderedBuffer 匹配与 trim 路径零基准，PF-02/03 量化前提 | 缺口存在 |
| BG-7 | 缺口（P3 级治理项） | /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-benchmark/nop-benchmark-stream/src/main/java/io/nop/benchmark/stream/MemoryKeyedStateBench.java（对照面） | MapState get/put 与 TTL on/off 状态访问档缺失（现仅 value/list/aggregating） | 缺口存在 |
| BG-8 | 缺口（P3 级治理项，表述修正） | /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-benchmark/nop-benchmark-stream/src/main/java/io/nop/benchmark/stream/NfaProcessBench.java（对照面） | advanceTime 扫描已有小存量路径覆盖（初审表述"零覆盖"不准），缺 E=1k/10k 规模曲线与 drainDueBuckets 放大档 | 缺口存在（规模/放大档） |
| BG-9 | 缺口（P3 级治理项） | /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java（:2010 被测面） | trigger accumulator 状态键路径（每记录拼接+查找、每窗口前缀清理）零基准，PF-04 量化前提 | 缺口存在 |

（BG-5 已驳回，不列入保留表。）
