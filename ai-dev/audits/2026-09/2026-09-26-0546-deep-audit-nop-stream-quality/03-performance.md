# 03 性能热点（core / runtime / cep / rocksdb）

> 审计代理：独立只读子代理；16 条 P0/P1/P2 逐条经独立复核代理确认（1 条量级修正、1 条修复约束补充）。路径相对 `nop-stream/`。

## 发现清单（复核后）

### A. 数据面主循环
- [P1] core/execution/task/StreamTaskInvokable.java:904,908,998,1000 — 每条记录 4 次 System.nanoTime + 2 次 Micrometer `Timer.record(Duration.ofNanos())`（每记录分配 2 个 Duration；MicrometerStreamTaskMetrics.java:85-96）。修复方向：`Timer.record(long,TimeUnit.NANOSECONDS)` 免 Duration 分配；打点采样化。
- [P1] core/execution/InputGate.java:618,680 — round-robin 对每个空 channel 固定阻塞 read(50ms)，空转后 parkNanos(10ms)；middle/sink 唯一读路径（processInputGate:870）。多通道倾斜/空闲时单记录延迟放大至百毫秒级。修复：非阻塞探测+就绪集/自适应回退。【复核限定：单通道有数据时立即返回，主要伤延迟与空转而非稳态吞吐】
- [P2] core/execution/buffer/BufferPool.java:56,60-94 — 全局公平信号量每记录 acquire/release（公平模式队列锁竞争）。方向：非公平或批量许可。
- [P3] core/streamrecord/StreamRecord.java:118-126 — replace() 的 isInstance 防御检查（可接受，保留）。

### B. RocksDB 状态后端
- [P0] rocksdb/RocksDBAggregatingState.java:131-151 — add() 每记录 get+JSON 反序列化+aggFn.add+序列化+put（RMW 双 JSON）+storageKey 2 次 JSON key 编码。方向：merge operator 或每 key 前向缓存。
- [P0] rocksdb/RocksDBListState.java:116-128 — add() 读整列表改后写回（O(n)/次，evictor 窗口 O(n²)）。方向：分片存储/追加 log。
- [P1] rocksdb/RocksDBKeyEncoder.java:91-103,242-264 — 每次状态访问重新 JSON 编码 namespace+key（nsBytes :247/keyBytes :263），全后端无 (key,namespace)→byte[] 缓存。方向：缓存前缀，key/namespace 变更失效。
- [P2] rocksdb/RocksDBValueState.java:55-99 — 每访问 JSON 值编解码（与上叠加）。
- [P1] core/common/typeutils/JavaStreamSerializer.java:106 + StreamDeserializationFilter.java:69-85 — 每次 Java 反序列化新建 JEP290 filter lambda + System.getProperty。CEP SharedBuffer 热路径（RocksDB 下每事件多次）。方向：缓存 filter 实例。

### C. Memory 状态后端
- [P1] core/common/state/backend/memory/MemoryKeyedStateBackend.java:414-416 + TypedNamespaceAndKey.java:42-46 — 每次状态操作 new TypedNamespaceAndKey + 2 次 Objects.hash 装箱数组（MemoryValueState:58/71/87、MemoryAggregatingState:40 等）。方向：复用 key 对象+手写 hash。

### D. 窗口算子（runtime）
- [P1] runtime/operators/windowing/WindowOperator.java:671,827,904 → :1294-1298 + MergingWindowSet.java:80-100 — 会话窗口每记录 new MergingWindowSet（构造器 state.get() 全量读 ListState 重建 HashMap+复制 initialMapping）。方向：按 key 复用实例、persist 后增量维护。
- [P1] runtime/operators/windowing/WindowOperator.java:1489-1502 — storeElementTimestamp 每记录 get 整列表+put 整列表（evictor 场景，与 RocksDBListState 叠加成双重二次方）。
- [P2] runtime/operators/windowing/WindowOperator.java:983-989 — removeTriggerAccumulators keySet().removeIf(startsWith) 全表扫描（堆内 Map<String,SimpleAccumulator>，:258）。purge 路径调用。方向：(key,window) 一级键嵌套 Map。
- [P2] runtime/operators/windowing/WindowOperator.java:1141-1143,1764-1773,2212 — paneKey/windowNamespace/trigger_ stateKey 每记录字符串拼接（fallback get+put 双次；Context.getSimpleAccumulator 每元素）。
- [P2] runtime/operators/windowing/WindowOperator.java:1020-1105 — evictor fire 路径一次分配 4 个新 List。
- [P2] core/operators/HeapInternalTimerService.java:423-426 — TimerEntry.hashCode 用 Objects.hash（装箱数组）；EventTimeTrigger.java:36-46 每元素注册 maxTimestamp 定时器（HashSet 已去重，成本仅 hash+equals）。方向：手写 hash。

### E. CEP
- [P0] cep/operator/CepOperator.java:972-983 — NFAState value()+update() 全量 Java 序列化往返。【复核修正：事件时间模式发生在每次事件时间定时器触发批次（onEventTime :855/:879，按时间戳分批覆盖该批全部事件），非字面每元素一次；处理时间模式 comparator==null 才是每元素一次（:693/:697）。量级结论成立。】方向：增量持久化 partial matches 或脏标记批量写。
- [P0] cep/nfa/sharedbuffer/SharedBuffer.java:197-229(registerEvent :223 put 写穿),282-306,388-391(flushCache) — 每事件写穿+accessor close（CepOperator.java:994 processEvent 每事件 try-with-resources）即清空双缓存 → 下事件全部读落 MapState。【复核约束：:376-384 注释表明清空是跨 key 正确性必需（EventId/NodeId 不含 key）；修复须 key-scoped 缓存键，不可简单去掉 flush。】
- [P1] cep/operator/CepOperator.java:985-991 — getSortedTimestamps 每次 timer 触发全量扫描 elementQueueState.keys()（:854/:908 调用；RocksDB 为 range 扫描+key JSON 解码）。方向：堆式 pending-timestamps 索引。
- [P1] cep/operator/CepOperator.java:833-842 — bufferEvent 每事件 get 整桶 list+put 整桶（elementQueueState RMW）。
- [P2] cep/operator/CepOperator.java:799-808,410-414 — snapshotTimersByKey 每 watermark 全账本深拷贝（new TreeSet ×N）。
- [P2] cep/nfa/NFA.java:364-425,631-652,824-832,947-956 — 每事件每 partial match 2 个 PriorityQueue+ConditionContext/Stack/HashSet/ArrayList+匿名 Iterable。
- [P3] cep/nfa/sharedbuffer/EventId.java:77-79 / NodeId.java:75-77 — 热键 hashCode 用 Objects.hash 装箱。
- [P3] cep/nfa/NFA.java:283-288,317-321 — windowTimes containsKey+get 双查。

### F. 网络传输/序列化
- [P0] core/execution/transport/StreamElementCodec.java:58(JsonTool.stringify payload),121-125,164-168(validateClassName+Class.forName+parseBeanFromText 无缓存) + runtime/transport/KafkaStringWireCodec.java:48(再 stringify),61 — 远程边每记录 4 次 JSON 编解码（encode→toWire / fromWire→decode，经 RemoteResultPartition:153-166、DataPlaneMessageServiceAdapter:97-119）+ 每记录 Class.forName + ~24 前缀 startsWith 白名单扫描。方向：valueType→Class ConcurrentHashMap 缓存；同 JVM IdentityWireCodec 已有；二进制 envelope。
- [P1] runtime/transport/RemoteResultPartition.java:154-170 — write() 全程 synchronized 且 messageService.send 同步阻塞（IMessageSender 默认 syncGet sendAsync）在锁内，发送完全串行无法流水线化。方向：锁收窄到 epoch/心跳字段；send 移出锁或异步批量。
- [P2] core/execution/ResultPartition.java:377-390 — isBackpressured 双重队列锁（当前仅心跳/监控路径，P2）。

### G. 控制面
- [P2] runtime/checkpoint/storage/CheckpointSerDe.java:96-117,227-231 — 全量状态单线程 JSON 序列化（byte[] 走 base64 ×1.33）；大状态拉长 barrier 对齐窗口。方向：byte[] 免 base64 直写、分片流式。
- [P3] core/execution/CheckpointBarrierTracker.java:153-257 — synchronized 内状态深拷贝（冷路径）。
- [P3] core/execution/ProcessingTimeServiceDriver.java:117-134 — 固定 tick 轮询（可 sleep-to-deadline）。

## JMH 基准候选 Top-10（复核认可）

1. **RocksDBAggregatingState#add**（P0-1，每记录 RMW+双 JSON）— ops/s+alloc/op；对照 merge/前向缓存版。
2. **RocksDBKeyEncoder#encode**（P1，纯 CPU+分配独立可测）— ns/op+bytes/op；对照前缀缓存版。
3. **RocksDBListState#add**（P0-2，O(n) 曲线）— 列表 100/1k/10k 三档。
4. **StreamElementCodec#encode+KafkaStringWireCodec toWire/fromWire pipeline**（P0-5）— 端到端 ops/s+编码字节+decode 单项（隔离 Class.forName）；对照 Class 缓存版。
5. **MemoryKeyedStateBackend 状态访问原语**（getTypedNamespaceAndKey+value/update+hashCode）— ns/op+alloc/op。
6. **WindowOperator#processElement 端到端** — tumbling/sliding/session/sliding+evictor 四组，records/s+alloc/record。
7. **HeapInternalTimerService registerEventTimeTimer+advanceWatermark** — 10k (key,TimeWindow) 注册、watermark 1s 步进 fire。
8. **NFA#process**（CEP 主循环）— P=1/10/100 缩放，alloc 大户定位。
9. **SharedBuffer registerEvent+put/extractPatterns（flushCache 开销隔离）** — Memory 与 RocksDB 双后端对照。
10. **CheckpointSerDe serialize/deserialize**（含 base64 Java-stream 大状态放大系数）。

复核补充：#6、#7、#10 单独收益可能低于 2%，作为分配观测与回归护栏；#1/2/4/5/8/9 是预期 ≥2% 的主战场。

## 共性根因

①状态值统一走 JSON/Java 序列化无数值/二进制快路径；②RocksDB 复合键每次访问重新编码；③"每事件写穿+每事件清缓存"使缓存与增量优化全部失效；④主循环记录级 micrometer/nanoTime 打点过密。
