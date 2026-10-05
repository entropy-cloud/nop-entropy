# 08 性能候选与基准缺口审计（nop-stream-core / nop-stream-runtime 数据面热路径）

- **审计日期**: 2026-10-05
- **HEAD SHA**: `bd089444f5c2b1fcd2c9a964ce909bac774a83d9`（2026-10-05 00:07:55 +0800）
- **范围**: `nop-stream/nop-stream-core` 与 `nop-stream/nop-stream-runtime` 的数据面热路径——streamrecord、buffer（PerKeyOrderedBuffer）、transport（StreamElementCodec / envelope / wire codec）、execution/task（StreamTaskInvokable、RecordWriter/Reader、InputGate、ResultPartition、BufferPool、mailbox）、operators（chaining、timer service、join）、windowing（WindowOperator、MergingWindowSet、OverWindowOperator、triggers）、common/state 读写路径、serialization（JSON canonical 使用点）。基准对照面：`nop-benchmark/nop-benchmark-stream` 全部 14 个基准类（任务口径写 13，实测 14，见「基准缺口清单」注）。
- **方法**: 静态只读审计。逐文件通读上述热路径全部 main 源码（约 60 个文件中与逐记录路径相关的 25 个），追踪逐记录调用链（processInputGate → InputGate.read → classifyAndDispatch → operator chain → RecordWriterOutput → RecordWriter → ResultPartition / RemoteResultPartition → wire codec），并对齐 `nop-benchmark-stream` 14 个基准类的被测路径。未运行任何构建/基准。基于 2025 常用 CPU 的时钟/分配成本常识给出档位估计，均标注为静态估计。

- **上一轮裁定衔接**: 上一轮性能裁定为「无 ≥2% 低风险可收割项」。本轮结论与其一致：**未发现可确证的 ≥2% 低风险可收割项**；PF-01 是唯一有潜力达到 2-10% 档的候选，但其收益未被任何基准覆盖（属基准缺口 PF-08），且建议的收割方式（NOOP 短路时钟读取）需先补基准实证，本轮如实标注为「很可能、需测量后收割」。

---

## 发现条目

### [PF-01] 任务驱动循环逐记录无条件时钟读取：4×nanoTime + 2×currentTimeMillis 每记录，NOOP 委托下照付
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/StreamTaskInvokable.java:925,963,978-985,1089-1096`
- **证据片段**: ```java
// processInputGate 循环顶（每次迭代，含空闲迭代）：
925:  markActivity();                       // CoreMetrics.currentTimeMillis() + volatile 写
// 每条记录：
963:  markProgress();                       // 第二次 currentTimeMillis + volatile 写
978:  taskMetrics.recordsIn(1);
980:  long metricsStart = CoreMetrics.nanoTime();       // 第 1 次 nanoTime
984:  taskMetrics.processingTime(CoreMetrics.nanoTime() - metricsStart);  // 第 2 次
// RecordWriterOutput.collect（每条跨任务输出记录）：
1089: taskMetrics.recordsOut(1);
1091: taskMetrics.recordsEmitted(1);
1094: long emitStart = CoreMetrics.nanoTime();           // 第 3 次
1096: taskMetrics.emitTime(CoreMetrics.nanoTime() - emitStart);           // 第 4 次
```
- **严重程度**: P2
- **现状**: `classifyAndDispatch` 与 `RecordWriterOutput.collect` 在**每条记录**上无条件调用 `CoreMetrics.nanoTime()` 两次（链处理计时 + 发射计时），`processInputGate` 每次迭代再调用 `CoreMetrics.currentTimeMillis()` 一到两次（markActivity/markProgress）。`CoreMetrics.nanoTime()` 委托 `s_clock` volatile 读 + `System.nanoTime()`（vDSO 下约 20-25ns），`currentTimeMillis()` 类似。四个指标方法经 `TaskMetricsHandle` 双跳（handle → delegate）后落到 `StreamTaskMetrics.NOOP` 时**全部为空操作**——但 nanoTime/clock 读取发生在调用委托之前，无法被 JIT 消除（nanoTime 是本征调用）。一个 MIDDLE 任务处理并转发一条记录的固定开销 ≈ 4×nanoTime + 2×currentTimeMillis ≈ 120-150ns，外加 4 次虚调用。若注入 Micrometer 委托，`Timer.record` ×2/记录（约 100-200ns）是按设计的可观测性成本，不在"无谓"之列。
- **风险/收益**: 预估档位 **2-10%**（仅对轻量 pass-through 链成立：跨任务 exchange 本身 queue put+take 约 400-600ns、Optional 分配、mailbox 探测，循环总成本约 1-1.5µs/记录，时钟开销占比可达 ~10%；有 JSON 编解码或实际算子计算的链路占比 <2%）。收益面：`StreamTaskMetrics.NOOP` 短路（如 `TaskMetricsHandle` 增加 `isNoop()` 快速路径跳过 nanoTime）对 NOOP 任务是纯减法、对注入委托的任务零变化，行为等价；风险低。**但**：该路径无任何 JMH 覆盖（见 PF-08 第一条），收益档位目前纯属静态估计，不建议在补基准前收割——这与上一轮"无 ≥2% 低风险可收割项"的裁定衔接：本候选是那个裁定的最强潜在反例，证据链就差一个基准。
- **建议**: 先补一个 task-loop 级 JMH（processInputGate 或 classifyAndDispatch 粒度，NOOP 指标），实测档位后再决定是否加 NOOP 短路。不要在无基准的情况下直接改。
- **信心水平**: 很可能（机制确定——nanoTime 无条件调用可从源码直接确证；档位不确定——取决于链路负载，未测量）
- **误报排除**: 这不是"metrics by design"误报：NOOP 委托下计时结果被丢弃，时钟读取是纯浪费；Micrometer 场景的 Timer.record 成本本审计明确不计入。也不是上一轮已否决项的复读：上一轮否决的是"状态读写/codec"路径的候选，本条位于 task 驱动层，属未覆盖的新区域（并作为基准缺口登记）。
- **复核状态**: 未复核

### [PF-02] 远程边 decode 逐记录白名单校验：validateClassName 在缓存命中前执行
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/transport/StreamElementCodec.java:59-73`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/util/ClassNameValidator.java:44-54`
- **证据片段**: ```java
private static Class<?> resolveValueType(String valueType) {
    ClassNameValidator.validateClassName(valueType);   // 每次 decode 都跑：23 个前缀 startsWith 线性扫描
    Class<?> clazz = DECODED_VALUE_TYPES.get(valueType);
    if (clazz == null) {
        clazz = DECODED_VALUE_TYPES.computeIfAbsent(valueType, name -> { ... });
    }
    return clazz;
}
```
- **严重程度**: P3
- **现状**: 每条远程边记录 decode 都执行 `validateClassName`（23 个 `ALLOWED_PREFIXES` 的 `startsWith` 线性扫描，约 100-250ns），然后才查 `DECODED_VALUE_TYPES` 缓存。类内 javadoc 自己已论证"校验是类名字符串的纯函数，过一次恒过"，但校验本身没有被缓存短路。
- **风险/收益**: 预估档位 **<2%**：decode 总成本由 `JsonTool.parseBeanFromText`（约 1-3µs，StreamElementCodecRoundTripBench 的 decodeOnly 口径）主导，白名单扫描占比 ~5% 以下、对端到端远程管线 <2%。把校验移入 `computeIfAbsent` 的 miss 分支（无效名不缓存、每次照常抛错，可观测行为不变）是低风险微改，但收益低于收割门槛——登记备查，不推荐单独实施。
- **建议**: 与其他 codec 改动搭车实施即可；若未来 wire 格式改为二进制（decode 成本大降），此项占比会上升，届时再收割。
- **信心水平**: 确定（机制与占比结构均可静态确证）
- **误报排除**: 不是安全-性能冲突误报：校验移入 miss 分支后无效类名仍然每次抛 `ERR_STREAM_CLASS_NOT_ALLOWED`（不缓存失败项），安全语义与现行为逐点等价；caches 恰好已在同一方法内以同一论证存在。
- **复核状态**: 未复核

### [PF-03] RemoteResultPartition.write 逐记录 ConcurrentHashMap 查询边 valueType
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteResultPartition.java:127`
- **证据片段**: ```java
String valueType = typeRegistry != null ? typeRegistry.getOutputTypeClassName(edgeId) : null;
StreamMessageEnvelope envelope = StreamElementCodec.encode(element, valueType, epochId);
```
- **严重程度**: P3
- **现状**: `edgeId` 在分区构造时固定，但 `getOutputTypeClassName(edgeId)`（CHM.get + String hash）每条记录执行一次（RemoteTransportWriteBench writeSingle/writeBroadcast 口径内）。valueType 在注册后不会随记录变化。
- **风险/收益**: 预估档位 **<2%**：CHM.get 约 20-40ns，同路径上的 `JsonTool.stringify`（约 0.5-2µs/记录）与锁内 send 完全主导。缓存到分区实例字段是低风险微改，但需处理"注册晚于首条 write"的 wiring 顺序（lazy memo 可解）。低于收割门槛，登记备查。
- **建议**: 搭车实施；无单独收割价值。
- **信心水平**: 确定
- **误报排除**: 不是并发误报：`TypeRegistry.register` 理论上可运行期重注册，lazy memo 必须保留 miss 时回源查询的路径，否则会改变"运行期重注册生效"的行为——建议实现为 volatile 字段 + miss 回源，而非构造期固化。
- **复核状态**: 未复核

### [PF-04] EquiJoinOperator.emitMatches 每记录全量扫描对侧缓冲（语义要求的 O(n²)，非浪费，但零基准覆盖）
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java:269-285`
- **证据片段**: ```java
private void emitMatches(JoinSideRecord<Object> rec, long ts) throws Exception {
    String otherKey = bufferKeyOf(rec.getEquiKey(), !rec.isLeft(), ts);
    for (long[] tsKey : buffer.sortedTimestamps(otherKey)) {   // 对侧全量排序快照，每记录 O(n)
        JoinSideRecord<Object> other = buffer.valueAt(otherKey, tsKey);
        ...
        output.collect(new StreamRecord<>(matchOf(rec, other), ts));
        rec.setMatched(true);
        other.setMatched(true);
```
- **严重程度**: P3
- **现状**: hash join 每条到达记录对对侧缓冲做一次 `sortedTimestamps`（TreeMap keySet 全量拷贝 + 再排序）并逐条匹配。总匹配次数是语义决定的全配对（一条左记录可与多条未完成右记录配对，`matched` 标志只影响 outer completion、不剪枝匹配），所以**不能用 matched 索引剪枝而不改变输出**——这是"正确性优先"的形状，不是无谓开销。但每记录 O(对侧缓冲长度) 的排序快照（TimSort 对已序数据 O(n)，拷贝 O(n)）在高扇形 key 下使 join 总成本逼近 O(记录数 × 缓冲深度)。
- **风险/收益**: 预估档位 **依赖负载**（key 缓冲深度 ≥ 数百时可达 ≥10%；一般负载 <2%）——但**不列为可收割项**：任何"预剪枝"都会改变 JoinMatch 输出集合，属行为变更而非优化。真正的无害改进只有把 `sortedTimestamps` 的多余再排序去掉（TreeMap keySet 本就按 `TsSeqComparator` 有序，拷贝即保序），约省 O(n log n) 中的比较项，仍 <2%。同时该算子完全没有 JMH 覆盖（见 PF-08 第三条），任何负载结论都无法实证。
- **建议**: 登记为基准缺口驱动项：先建 JoinBench 测出真实缓冲深度-吞吐曲线，再决定是否值得做语义等价的快照免排序微改。
- **信心水平**: 确定（形状）/ 有趣的猜测（档位）
- **误报排除**: 本条刻意**不**申报为 O(n²) 缺陷：全配对语义下每记录扫对侧是定义要求；与审计要点 4"O(n²) 的窗口合并"同类但此处剪枝会破坏正确性，因此按"语义要求的保守实现"归类并只登记基准缺口。
- **复核状态**: 未复核

### [PF-05] MergingWindowSet 每记录重建：状态读 + 双 HashMap 拷贝 + persist 全量比较（Flink 同构保守实现）
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:746,830,1338-1343`；`MergingWindowSet.java:80-100,105-115`
- **证据片段**: ```java
// WindowOperator.processElementForMergingWindow（session 窗口每记录）：
746:  MergingWindowSet<W> mergingWindows = getMergingWindowSet();   // new MergingWindowSet<>(...)
830:  mergingWindows.persist();
// MergingWindowSet 构造器：
mapping = new HashMap<>();
... for (Tuple2<W, W> window : windowState) { mapping.put(window.f0, window.f1); }   // O(n) 重建
initialMapping = new HashMap<>(); initialMapping.putAll(mapping);                    // 第二份 O(n) 拷贝
```
- **严重程度**: P3
- **现状**: session/merging 窗口每条记录：新构造 `MergingWindowSet`（从 `mergingSetsState.get()` 全量重建 mapping + `initialMapping` 双份拷贝）、`addWindow` O(n) key 集合并归、`persist()` O(n) equals 比较。Memory 后端 `MemoryListState.get()` 返回活引用（O(n) 拷贝进 HashMap，无序列化）；RocksDB 后端每记录全量反序列化 merging-sets 列表。该形状与 Apache Flink `WindowOperator` 逐字同构（Flink 亦每记录重建 `MergingWindowSet`），属可逆计算移植的保守实现而非本仓自创开销。`WindowOperatorProcessElementBench` 的 SESSION 档已直接覆盖此路径（memory 与 rocksdb 双后端），成本已被基准面观测。
- **风险/收益**: 预估档位 **<2%**（memory 后端，n = 每 key 在飞窗口数通常个位数）到 **2-10%**（RocksDB + 长 session + 深窗口集）。可缓存 per-key 的 mapping（key 切换时失效）是 Flink 之外的自有优化，但会引入 key 上下文切换失效逻辑，属中风险改动且 SESSION 档基准已可测量——按上一轮口径，RocksDB 深窗口档以下的收益不构成 ≥2% 低风险收割项。
- **建议**: 维持现状；若未来 RocksDB session 窗口业务报性能问题，优先用现有 SESSION+ROCKSDB 基准档定位，再评估 per-key mapping 缓存。
- **信心水平**: 确定（现状描述）/ 有趣的猜测（RocksDB 档位）
- **误报排除**: 不是"每记录 new 对象"误报的同类项：这里的分配被 SESSION 基准直接测量、且与 Flink 参考实现逐字对齐，属移植保真而非疏漏——按审计口径明确归类为"正确性优先的保守实现"。
- **复核状态**: 未复核

### [PF-06] WindowOperator 逐记录清理定时器重注册与 trigger 状态键字符串拼接
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:870,2010`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/HeapInternalTimerService.java:119-123,355-423`
- **证据片段**: ```java
// processWindowElement 每记录：
870:  registerCleanupTimer(actualWindow);      // → registerEventTimeTimer
// HeapInternalTimerService.registerEventTimeTimer：
eventTimeTimers.computeIfAbsent(time, k -> new HashSet<>())
        .add(new TimerEntry<>(key, namespace, time));   // 每记录新分配 TimerEntry（重复注册靠 Set 去重）
// Context.getSimpleAccumulator（CountTrigger 每记录）：
2010: String stateKey = "trigger_" + key + STATE_KEY_SEPARATOR + window + STATE_KEY_SEPARATOR + descriptor.getName();
```
- **严重程度**: P3
- **现状**: 同一 (key, window) 的连续记录反复注册同一清理定时器：每次分配一个 `TimerEntry`（hash 已有手工混合防装箱分配的修复，见 hashCode 注释）、做一次 TreeMap O(log n) 下探 + HashSet add（equals 碰撞后失败）。CountTrigger 类触发器每记录拼接 `"trigger_" + key + SEP + window + SEP + name` 状态键（一次 StringBuilder 链 + 装箱段）。两者均为 Flink 同构模式（Flink `InternalTimerServiceImpl` 同样以 Set 去重、同样每记录重注册）。
- **风险/收益**: 预估档位 **<2%**：TimerEntry 分配 + TimeWindow hash + TreeMap 下探合计约 30-60ns，相对窗口记录路径上 `windowNamespace` 已缓存、`TypedNamespaceAndKey` 已缓存（backend 单槽缓存）后的总成本占比小。单槽"上次注册 (key,namespace,time) 短路"可消 TimerEntry 分配，行为等价（同参重注册本就是幂等去重），低风险但收益低于门槛。
- **建议**: 登记备查；若做 PF-01 的 task-loop 基准，可顺带在 WINDOW 档对照实测该分配的 GC 影响。
- **信心水平**: 确定（机制）/ 有趣的猜测（占比）
- **误报排除**: hashCode 注释表明本仓已裁决过此处的装箱分配问题（Objects.hash→手工混合）；本条指出的是**剩余的** TimerEntry 实例分配，与已修复项不同层，不构成对既往裁定的重复申报。
- **复核状态**: 未复核

### [PF-07] PerKeyOrderedBuffer.trimToWatermarkWithKeys 全树线性扫描（同文件 trimToWatermark 已用 headMap O(k+log n)）
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java:161-179`（对照 `:114-125`）
- **证据片段**: ```java
public List<long[]> trimToWatermarkWithKeys(K key, long watermark) {
    ...
    List<long[]> dropped = new ArrayList<>();
    for (long[] tsKey : tree.keySet()) {          // O(n) 全树扫描
        if (tsKey[0] <= watermark) { dropped.add(tsKey); }
    }
    for (long[] tsKey : dropped) { tree.remove(tsKey); }   // 再 O(k log n) 逐个删
    ...  // 对照 trimToWatermark（:120）：tree.headMap(new long[]{watermark, Long.MAX_VALUE}).clear();
```
- **严重程度**: P3
- **现状**: 带 key 返回的 trim 变体放弃 headMap 视图，改为全树扫描 + 逐个 remove；同一文件的无 key 变体已示范 headMap 一次清割的写法（headMap 迭代 + 转移到 dropped 列表即可两全）。调用方：`OverWindowOperator.emitRowNumbers/emitSlidingAgg`（每 key 每 watermark）与 `EquiJoinOperator.trimToWatermark/dropAll`（watermark 路径）。
- **风险/收益**: 预估档位 **<2%**：触发频率为每 watermark 每 key（非逐记录），深缓冲（数千条/key）下单次 O(n) 扫描才可感。改为 headMap 迭代收集是纯机械等价改写（比较器同构，`headMap(new long[]{watermark, Long.MAX_VALUE})` 语义与 `tsKey[0] <= watermark` 完全一致），低风险；但收益小于门槛，登记备查。
- **建议**: 搭车实施（同文件已有范式，改动 ~6 行）；不建议单独立项。
- **信心水平**: 确定
- **误报排除**: 不是"序列化快照成本"误报：buffer 是工作视图、树内键即 `long[]` 实例，headMap 收集的是同一批引用，无拷贝语义差异。
- **复核状态**: 未复核

### [PF-08] 基准缺口：任务驱动循环 / 多通道 InputGate / RemoteInputChannel 接收路径 / Join 与 OVER 算子 / CountTrigger 档
- **文件**:
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/StreamTaskInvokable.java:917-1019,1083-1097`（任务循环逐记录路径，无任何基准）
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:683-767`（readMultiChannel 仅单通道基准覆盖）
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:598-690`（EnvelopeConsumer 解码+入队路径，无基准）
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java:170-183`（join 算子，无基准）
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/OverWindowOperator.java:150-250` + `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java:71-145`（OVER/ordered buffer，无基准）
  - `nop-benchmark/nop-benchmark-stream/src/main/java/io/nop/benchmark/stream/WindowOperatorProcessElementBench.java:96`（仅 EventTimeTrigger 档，无 CountTrigger/连续触发档）
- **严重程度**: P3（基准缺口登记）
- **证据片段**: ```java
// InputGateReadLoopBench.setup：单通道
gate = new InputGate(channel, null);              // readMultiChannel 的轮询/对齐/水位合并分支未被任何基准执行
// WindowOperatorProcessElementBench.setup：
builder.trigger(EventTimeTrigger.create())        // 无 CountTrigger → Context.getSimpleAccumulator 逐记录状态键路径无基准
```
- **严重程度说明**: 每条缺口单独立项均不足 P2（属测量盲区而非已证退化），合并登记。
- **现状与风险/收益**: 现有 14 个基准类覆盖：codec 往返、Memory/RocksDB keyed state、窗口算子四形状双后端、timer 注册/水位推进、NFA、SharedBuffer、CheckpointSerDe、远程写（编码+锁内 send）、CepOperator、InputGate **单通道**读循环、BufferPool 许可、处理时间驱动延迟、连接器 invoke。缺口按优先级：(1) **任务循环层**（PF-01 的计时开销、mailbox 探测、RecordWriterOutput 的 record.copy 分配、Optional 装载）——这是唯一可能藏着 ≥2% 项的盲区，建议优先补齐；(2) 多通道 gate（`readMultiChannel` 的轮询节奏、`Optional`/`emitPendingBarriers` 分配、水位 O(channels) 合并）；(3) RemoteInputChannel 接收侧（fencing 过滤、`StreamElementCodec.decode`+入队、`lastReceivedTime` 更新）——写侧已有 RemoteTransportWriteBench、codec 级已有 RoundTripBench，通道级 dispatch 缺位；(4) EquiJoin / OverWindow 算子（PF-04/PF-07 无法定档的直接原因）；(5) 触发器档扩展（CountTrigger/ContinuousEventTimeTrigger）。均为静态评估，不要求本轮运行。
- **建议**: 按 (1)→(4) 顺序补基准；补齐后再复核 PF-01 的档位与 PF-04 的负载曲线。
- **信心水平**: 确定（覆盖面由基准源码逐一核对得出）
- **误报排除**: 逐一核对了 14 个基准类的被测入口与上述文件的调用关系，上述文件确无任何 JMH 入口可达（例：`StreamTaskInvokable` 在整个 `nop-benchmark-stream` 源码树中零引用）。
- **复核状态**: 未复核

---

## 检查范围

已逐行通读并分析（main 源码，均在审计范围内）：

- **streamrecord/serialization**: `StreamRecord`、`StreamElement`、`SideOutputElement`、`Watermark`、`WatermarkStatus`、`LatencyMarker`、`StreamElementCodec`、`StreamMessageEnvelope`、`TypeRegistry`、`ClassNameValidator`
- **execution**: `RecordWriter`、`RecordReader`、`ResultPartition`、`InputChannel`、`InputGate`、`BufferPool`/`IBufferPool`、`TaskMailbox`、`MailboxExecutor`、`Mail`、`ProcessingTimeServiceDriver`、`TaskProcessingTimeService`、`StreamTaskInvokable`（含 RecordWriterOutput/BroadcastingRecordWriterOutput）、`EdgeConfig`/`FlowControlPolicy`、materialization 三件套（`InMemoryMaterializationPoint` 等，旁路启用时才在热路径）
- **operators**: `AbstractStreamOperator`、`AbstractUdfStreamOperator`、`ChainingOutput`、`KeyExtractingOutput`、`TimestampedCollector`、`HeapInternalTimerService`、`TimerServiceManager`、`StreamMap/Filter/FlatMap/Reduce/Sink/Union`、`StreamSourceOperator`（collect 路径）、`StreamReduceOperator`、`join/EquiJoinOperator`
- **windowing**: `WindowOperator`（全文 2074 行）、`MergingWindowSet`、`OverWindowOperator`、`PerKeyOrderedBuffer`、`EventTimeTrigger`、`CountTrigger`、`PurgingTrigger`（抽查）、window assigners（抽查 Tumbling/Sliding/Session）
- **common/state 读写路径**: `MemoryKeyedStateBackend`（含 `cachedNamespaceAndKey` 单槽缓存、`routeKey`、TTL sweep 门）、`AbstractMemoryState`、`MemoryValueState`、`MemoryListState`/`MemoryInternalListState`、`MemoryAggregatingState`/`MemoryInternalAggregatingState`、`MemoryMapState`（抽查）、`StateSchemaResolver`（指纹计算）、`TypedNamespaceAndKey`、`ShardPrefixedKey`/`KeyGroupAssignment`（抽查）、`TtlContext`（抽查）
- **runtime transport/execution**: `RemoteResultPartition`、`RemoteInputChannel`（含 EnvelopeConsumer）、`KafkaStringWireCodec`、`PulsarStringWireCodec`（核对平行复制声明）、`SysDaoWireCodec`（抽查）、`IdentityWireCodec`（抽查）、`DataPlaneWireSupport`、`DataPlaneMessageServiceAdapter`（抽查）、`ChannelQueueGauges`（抽查）、`TaskManager`/`SubtaskTask`/`SupervisionLoop`（控制面抽查，确认非数据面）
- **metrics**: `TaskMetricsHandle`、`StreamTaskMetrics`（NOOP）、`MicrometerStreamTaskMetrics`、`CoreMetrics` 时钟委托（nop-api-core）
- **基准对照**: `nop-benchmark/nop-benchmark-stream` 全部 14 个基准类源码通读（BufferPoolPermit、CepOperator、CheckpointSerDe、ConnectorInvoke、InputGateReadLoop、MemoryKeyedState、NfaProcess、ProcessingTimeDriverLatency、RemoteTransportWrite、RocksDbKeyedState、SharedBufferRegister、StreamElementCodecRoundTrip、TimerService、WindowOperatorProcessElement）
- **范围澄清**: nop-stream-cep / nop-stream-rocksdb / 连接器模块不在本轮审计对象内（本报告仅在基准对照与调用链衔接处引用）；scope 内的 CepOperatorBench/SharedBufferRegisterBench/RocksDbKeyedStateBench/NfaProcessBench 仅作覆盖面核对。

未发现问题的热路径点（明确归类为"合理实现"，非遗漏）：
- `ChainingOutput.collect`：try/catch 无异常零成本，直接虚调用转发。
- `MemoryKeyedStateBackend.cachedNamespaceAndKey` / `WindowOperator.windowNamespace`：单槽缓存已消逐记录键分配。
- `TimerEntry.hashCode` 手工混合：装箱分配已修复（注释在案）。
- `MicrometerStreamTaskMetrics.emitTime/processingTime`：已用 `record(long, TimeUnit)` 消除 Duration 分配（注释在案）。
- `StreamElementCodec.DECODED_VALUE_TYPES`：Class.forName 已缓存（注释在案）；余下 validateClassName 开销见 PF-02。
- `getOrCreateState` 的 SHA-256 schema 校验：仅在 `open()` 时经 `getXxxState` 调用（WindowOperator 等均缓存 state 句柄为字段），不在逐记录路径。
- `ResultPartition` 队列语义：pool acquire/release 与 queue put/poll 为背压设计本体；`isBackpressured` 的浮点表达式仅观测路径。
- `RemoteInputChannel` 逐消息 `currentTimeMillis` 活性刷新：producer-death 快失败设计（150ms 契约），正确性优先。
- `InputGate` 屏障/对齐路径的 ConcurrentHashMap 复合操作：已有明确的跨线程 abort 协议注释（add-before-remove），正确性优先，非性能问题。

## 基准缺口清单

| # | 缺口 | 被测对象（文件:行） | 现有最近基准 | 建议基准形态 |
|---|------|--------------------|--------------|--------------|
| G1 | 任务驱动循环逐记录路径（计时仪表、mailbox 探测、record.copy、Optional） | `StreamTaskInvokable.java:917-1019,1083-1097` | 无 | TaskLoopBench：HeadInput + 哑 InputGate/RecordWriter，对照 NOOP/Micrometer 委托两档（直接服务 PF-01 定档） |
| G2 | 多通道 InputGate（readMultiChannel 轮询、屏障对齐、水位合并） | `InputGate.java:683-767,978-1053,1181-1203` | InputGateReadLoopBench（仅 1 通道 readSingleChannel） | 现 bench 增 @Param channels={1,4,16}；水位合并可单测 `handleWatermarkNonRecursive` |
| G3 | RemoteInputChannel 接收侧（fencing 过滤 + decode + 入队 + 活性时钟） | `RemoteInputChannel.java:606-689` | RemoteTransportWriteBench（仅发送侧）/ StreamElementCodecRoundTripBench（codec 级） | EnvelopeConsumer 级 bench：预编码 envelope 流注入 onMessage，测 decode+offer 吞吐 |
| G4 | EquiJoin 算子（emitMatches 对侧扫描、trim 路径） | `EquiJoinOperator.java:170-183,269-285` | 无 | JoinBench：@Param 缓冲深度 {10,100,1000}，hash/window 两形态 |
| G5 | OverWindow / PerKeyOrderedBuffer（sortedView、trimTo*WithKeys） | `OverWindowOperator.java:174-250`、`PerKeyOrderedBuffer.java:71-198` | 无 | OrderedBufferBench：add/trimToWatermarkWithKeys/trimToCountWithKeys 微基准 |
| G6 | 触发器档（CountTrigger 逐记录 getSimpleAccumulator 状态键；连续触发器） | `WindowOperator.java:2003-2031`、`CountTrigger.java:44-53` | WindowOperatorProcessElementBench（仅 EventTimeTrigger） | 现 bench 增 windowType/trigger 档或新 trigger 维度 |
| G7 | MemoryMapState 读写 | `MemoryMapState.java`（put/get/entries） | MemoryKeyedStateBench（value/agg/list 三态，无 map） | 现 bench 增 mapPutGet 档（OverWindow/Join 的 fallback 路径依赖它） |

注：任务口径称 13 个基准类，实测 `nop-benchmark-stream` 源码树为 14 个（多出 `ConnectorInvokeBench`）；以 14 为准登记覆盖面。

---

## 维度复核结论

**复核方法**：独立子代理只读重走源码。对 PF-01..08 逐条重新定位证据行号、重走逐记录调用链、独立核对基准覆盖面（`nop-benchmark-stream` 源码树 grep + 目录清单），并对关键档位估算做独立算术复核。未运行任何构建/基准；按复核纪律未读取其他审计/计划/bug/lessons 文件，PF-01 中"上一轮否决范围"的衔接表述以本报告自身已验证的覆盖面分析（PF-08）为支撑独立成立。

### 逐条裁决

**PF-01 — 保留（P2）**。独立验证全部成立：(1) 行号 925/963/978-985/1089-1096 与源码逐字对上；(2) 热路径判定成立——`processInputGate` 是 MIDDLE/SINK 任务唯一的逐记录驱动循环，`classifyAndDispatch` 每记录执行（980/984 两次 `CoreMetrics.nanoTime()`），`RecordWriterOutput.collect` 在 266/270/492 处接线到所有跨任务 fan-out 输出（chained 下游走 `ChainingOutput` 无仪表，与本报告"未发现问题"清单一致），`markActivity` 每迭代、`markProgress` 每元素各一次 `currentTimeMillis` + volatile 写（字段 114/131 确为 volatile）；(3) NOOP 委托下照付成立——`TaskMetricsHandle` 各方法为 `delegate.xxx(n)` 单跳转发（volatile delegate），`StreamTaskMetrics.NOOP` 六方法全空，而时钟读取（`s_clock` volatile 读 + 接口分派 → `System.nanoTime()` 本征调用）发生在委托调用之前，结果被丢弃也无法被 JIT 消除；(4) 档位评估：绝对开销 4×nanoTime(~20-25ns) + 2×currentTimeMillis(~10-20ns) + 4 次虚调用 ≈ 100-150ns 成立；"2-10%"上限仅在 ~1-1.5µs/记录的 pass-through 链算术自洽（120ns/1.2µs≈10%），且原文已明确限定"仅对轻量 pass-through 链成立、其余 <2%、未测量不收割"——**不算高估**；对极简饱和链（总成本更低时）该占比甚至可能更高，上限偏保守而非虚高。建议的"先补基准后收割"顺序正确。

**PF-02 — 保留（P3）**。行号 59-73 / 44-54 核实，`resolveValueType` 确在缓存查询前每次执行 `validateClassName`，decode 记录路径（TYPE_STREAM_RECORD / TYPE_SIDE_OUTPUT_RECORD，154/191）均命中。**微小偏差**：`ALLOWED_PREFIXES` 实为 **25** 个（审计写 23），线性扫描常数量级结论不变，<2% 评估不受影响。miss 分支校验方案行为等价论证成立（无效名在 `computeIfAbsent` lambda 内抛出、不缓存，逐次照抛 `ERR_STREAM_CLASS_NOT_ALLOWED`）。

**PF-03 — 保留（P3）**。行号 127 核实；`TypeRegistry.getOutputTypeClassName` 确为 `ConcurrentHashMap.get`；`write()` 是 RecordWriter 逐记录入口，`edgeId` 构造期固定属实。误报排除论证核实：`register` 用 `put` 覆写、`unregister` 存在，运行期重注册语义真实存在，"volatile 字段 + miss 回源、非构造期固化"的实现要求正确。

**PF-04 — 保留（P3）**。`emitMatches`（269-285）与逐记录入口 `processElement`（170-183，179 行先匹配后入缓冲）核实；`sortedTimestamps` 确为 keySet 全量拷贝 + 冗余重排序（TreeMap 迭代本就按比较器有序，TimSort 对已序输入 O(n)）——"多余再排序"这一唯一无害微改的判断成立。语义判定核实：循环内不检查 `isMatched()`，matched 标志仅 `completeUnmatched` 用于 outer completion，**剪枝确实会改变 JoinMatch 输出集合**，"语义要求的保守实现、不列为可收割项"归类正确。

**PF-05 — 保留（P3）**。746（`getMergingWindowSet()`）/830（`persist()`）/1338-1343（`new MergingWindowSet<>`）核实；MergingWindowSet 构造器确为 mapping O(n) 重建 + `initialMapping.putAll` 第二份 O(n) 拷贝，`persist()` 为 O(n) equals；`MemoryListState.get` 走 `storage.get` 无序列化（memory 后端）核实；SESSION 档基准（memory/rocksdb 双后端，bench 含 SESSION case 与 dbDir）已覆盖该路径核实。与 Flink `WindowOperator`/`MergingWindowSet` 同构的判断与参考实现一致，"移植保真、维持现状"裁定恰当。

**PF-06 — 保留（P3）**。870 → `registerCleanupTimer`（1371）→ `triggerContext.registerEventTimeTimer` 链核实；`HeapInternalTimerService` 119-123 确为 `computeIfAbsent(time, k -> new HashSet<>()).add(new TimerEntry<>(...))`（同参重注册靠 Set 去重、每记录分配 TimerEntry）；`TimerEntry.hashCode` 手工混合修复注释核实（355-423 区间内，与已修复项分层不重复的排除论证成立）；2010 状态键 `"trigger_" + key + SEP + window + SEP + name` 拼接核实，CountTrigger onElement（44-53）逐记录 `ctx.getSimpleAccumulator` 核实。30-60ns/注册的量级与 <2% 档合理。

**PF-07 — 保留（P3）**。161-179（全树扫描 + 逐个 remove）对照 114-125（headMap 一次清割）核实；调用方核实：`OverWindowOperator:197`（emitRowNumbers）/`:238`、`EquiJoinOperator:329/333`（trimToWatermark/dropAll 包装）。**微小表述偏差**：`emitSlidingAgg` 实际调用的是 `trimToCountWithKeys`（182-198，同为全量扫描+排序形状），非 `trimToWatermarkWithKeys`，不影响结论与档位。触发频率"每 watermark 每 key（非逐记录）"核实（`processWatermark` 遍历 `buffer.keys()`）；headMap 改写等价性成立（唯一差异为 ts==watermark 且 seq==Long.MAX_VALUE 的病态情形，insertionSeq 实际不可达）。

**PF-08 — 保留（P3）**。全部零覆盖声明以 grep 独立复核成立：`StreamTaskInvokable`、`RemoteInputChannel`、`OverWindowOperator`、`EquiJoinOperator`、`CountTrigger` 在 `nop-benchmark-stream` 源码树**零引用**；`InputGateReadLoopBench:70` 确为单通道 `new InputGate(channel, null)`；`WindowOperatorProcessElementBench:96` 确为仅 `EventTimeTrigger.create()`；目录实测 14 个基准类与本报告清单逐一对上。G1-G7 缺口分级与"任务循环层是唯一可能藏 ≥2% 项的盲区"的优先级判断与本复核对 PF-01 的独立结论互相印证。

### 复核总体

8/8 保留，无驳回、无降级。两处微小事实偏差（PF-02 前缀计数 23→实际 25；PF-07 emitSlidingAgg 实调 `trimToCountWithKeys`）均为不影响等级与结论的表述级误差，按"不修改已有发现"纪律原文保留、在此登记备查。PF-01 的"2-10% 档"经独立算术与机制复核**不构成高估**，其"先基准后收割"的门槛设定是本轮结论"无 ≥2% 低风险可收割项"成立的关键，予以确认。

## 最终保留项

| 编号 | 裁决 | 等级 | 一句话理由 |
|------|------|------|-----------|
| PF-01 | 保留 | P2 | 逐记录 4×nanoTime + 2×currentTimeMillis 机制与 NOOP 照付全部源码确证，2-10% 档算术自洽且已限定范围，收割被基准缺口（PF-08/G1）正确门槛化 |
| PF-02 | 保留 | P3 | 校验先于缓存逐记录执行确证；前缀数实为 25（非 23）不影响 <2% 结论 |
| PF-03 | 保留 | P3 | 逐记录 CHM.get 确证；运行期重注册语义成立，lazy memo + miss 回源实现要求正确 |
| PF-04 | 保留 | P3 | 全配对语义确证（matched 不剪枝），不可优化判定正确；多余重排序是唯一无害微改，已正确降为基准缺口驱动项 |
| PF-05 | 保留 | P3 | 每记录重建/双拷贝/persist 全部确证，Flink 同构、SESSION 基准已覆盖，维持现状恰当 |
| PF-06 | 保留 | P3 | 定时器重注册与状态键拼接确证，Flink 同构、<2% 合理，登记备查恰当 |
| PF-07 | 保留 | P3 | 全树扫描 vs headMap 对照确证，每 watermark 每 key 触发核实；emitSlidingAgg 实调 trimToCountWithKeys（同形状）不影响结论 |
| PF-08 | 保留 | P3 | 六项零覆盖声明全部 grep 独立复核成立，14 基准类清单与实测一致，G1-G7 缺口登记有效 |
