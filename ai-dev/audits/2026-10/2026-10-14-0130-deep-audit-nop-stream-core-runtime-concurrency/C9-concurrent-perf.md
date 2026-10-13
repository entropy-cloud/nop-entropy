# C9 并发高性能 — nop-stream-core-runtime（第 1 轮初审）

- **审计维度**: C9（并发高性能 / 热路径）
- **审计对象**: `nop-stream/nop-stream-core/src/main/java`（439 files / 57,135 lines）、`nop-stream/nop-stream-runtime/src/main/java`（132 files / 29,922 lines）；`_` 前缀生成文件不在发现对象之列
- **盲审声明**: 本轮未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；只审计当前 live code
- **依据文档**: `AGENTS.md`、`docs-for-ai/INDEX.md`、owner doc `docs-for-ai/03-modules/nop-stream.md`、基线 `_tmp/cra-baseline-nop-stream-core-runtime.txt`
- **判级口径**: 本维度发现默认 P2/P3（除非掩盖正确性问题）；每条发现附量化口径（调用频率 × 单次代价估算链）；冷路径不在本维度
- **复核状态**: 已复核（见文末「维度复核结论」节）

## 检查范围说明

**热路径定位**（对照 owner doc：连续流模型、per-record 指标集 WI16、DB 通道背压语义）：

1. **任务主循环（每记录）**：`StreamTaskInvokable.processInputGate` → `classifyAndDispatch` → `headInput.processElement`；`markActivity/markProgress`（volatile 写）、`taskMetrics.*`（已核查：`MicrometerStreamTaskMetrics` 全部走预注册 meter + `record(long,TimeUnit)` 无 Duration 分配，`TaskMetricsHandle` 为 volatile 委托句柄——无发现）。
2. **算子链内转发（每记录每跳）**：`ChainingOutput.collect`（try/catch 成功路径零分配——无发现）、`KeyExtractingOutput`（每记录 keySelector.getKey + setCurrentKey——无分配）、`TimestampedCollector`/`StreamRecord.copy`（每发射一次拷贝，属跨队列别名正确性要求，不报）。
3. **跨任务发射路径（每记录）**：LOCAL = `RecordWriter.emit` → `ResultPartition.write`（LinkedBlockingQueue + IBufferPool permit——无发现）；REMOTE = `RemoteResultPartition.write` → `StreamElementCodec.encode` → `DataPlaneMessageServiceAdapter` → `AbstractStringWireCodec/SysDaoWireCodec.toWire` → `IMessageService.send`。
4. **跨任务接收路径（每记录）**：消息后端派发线程 → `EnvelopeConsumer.onMessage`（epoch 过滤 + decode + 有界 offer）→ `RemoteInputChannel` 本地队列 → `InputGate.readSingleChannel/readMultiChannel`（round-robin + barrier/watermark 合并）。
5. **有状态算子路径（每记录）**：`WindowOperator.processElement`（regular + merging 两路）、`MemoryKeyedStateBackend`（cachedNamespaceAndKey 单槽缓存——已优化，无发现）、`MemoryAggregatingState/MemoryReducingState`（首键反射建 accumulator，稳态无反射——无发现）、`HeapInternalTimerService`（每记录 cleanup/maxDelay 双注册，TimerEntry hashCode 已手写去装箱——无发现）、`EquiJoinOperator.processElement`。
6. **维表查找路径（每记录，缓存命中/未命中）**：`CachingTableLookup.lookup`（WI11 声明的同步 per-record 路径）。
7. **源发射路径（每记录）**：`StreamSourceOperator` SourceContext.collect（drain mails + markProgress + counter——无分配问题）；FLIP-27 = `SourceReaderOperator.run` pull 循环。
8. **2PC sink 每记录路径**：`StreamSinkOperator.processElement` → `TwoPhaseCommitSinkFunction.invoke`（基类无锁，`pendingCommits` 同步块全部在 checkpoint/commit 冷路径——无发现）。
9. **DB 数据面通道（每记录，gating 启用时）**：`RemoteResultPartition.gateOnBacklog`（TTL 缓存 + 100ms 分片等待，锁外契约——无发现）。

**明确不做**（冷路径）：checkpoint 持久化（`CheckpointSerDe`/`LocalFileCheckpointStorage`/`JdbcCheckpointStorage`）、控制面 RPC/协调器（`JobCoordinator`/`TaskManager`/`JdbcClusterRegistry`）、REST/运维端点（`StreamOpsHttpServer`/`OpsJobManager`）、指标抓取（`ChannelQueueGauges`/`StreamMetricsReporter`）、图构建（`StreamGraphGenerator`/`RegionDecomposer`）。

**未发现问题的定向核查点**（防复检重查）：`WindowedStreamImpl.defaultFactory` 双检（volatile + 构建期一次，且 default factory 在图构建期取用，非每记录）；全模块 `Pattern.compile` 均为 static final；`JsonToolSerializer`/`ContainerValueCodec`/`MemoryStateSerDe` 仅 checkpoint/restore 冷路径；`StreamMetricsRegistries` 注册表 scrape 期读取。

## 热路径清单（含判定）

| # | 热路径 | 入口 → 出口 | 频率 | 判定 |
|---|---|---|---|---|
| H1 | 任务输入循环 | `processInputGate` → `classifyAndDispatch` | 每记录 | 无发现（metrics/volatile 写均可控） |
| H2 | 算子链转发 | `ChainingOutput`/`KeyExtractingOutput` | 每记录每跳 | 无发现 |
| H3 | 本地发射 | `RecordWriter.emit` → `ResultPartition.write` | 每记录 | 无发现（双锁队列 + permit） |
| H4 | 远端发射 | `RemoteResultPartition.write` → codec → send | 每记录 | **C9-005 / C9-006** |
| H5 | 远端接收 | backend dispatch → `EnvelopeConsumer` → `InputGate.read` | 每记录 | **C9-008**（decode 侧） |
| H6 | 窗口算子 | `WindowOperator.processElement`（merging 路） | 每记录 | **C9-002** |
| H7 | 字段聚合 reduce | `FieldAggregationReducer.reduce` | 每记录 | **C9-001** |
| H8 | 维表查找 | `CachingTableLookup.lookup` | 每记录 | **C9-003** |
| H9 | FLIP-27 源循环 | `SourceReaderOperator.run` | 每轮询（空闲持续） | **C9-004** |
| H10 | 物化旁路写 | `ResultPartition.write` → `InMemoryMaterializationPoint.write` | 每记录（物化边） | **C9-007**（P3，含已声明边界） |
| H11 | 2PC sink | `StreamSinkOperator.processElement` → `invoke` | 每记录 | 无发现（锁在冷路径） |
| H12 | keyed 状态访问 | `MemoryKeyedStateBackend`/各 Memory state | 每状态访问 | 无发现（单槽 key 缓存在位） |

---

## 发现条目

### [nop-stream-core-runtime-C9-001] FieldAggregationReducer 每记录全量反射拷贝 accumulator

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/KeyedStreamImpl.java:294-335`
- **证据片段**:
  ```java
  public T reduce(T acc, T value) throws Exception {
      initField(acc.getClass());
      Number accVal = (Number) fieldAccessor.get(acc);
      ...
      T copy = copyBean(acc);          // 每条记录一次
      fieldAccessor.set(copy, convert(accVal, result));
      return copy;
  }
  private static <T> T copyBean(T source) throws Exception {
      copy = (T) clazz.getDeclaredConstructor().newInstance();  // 每条记录查一次构造器
      ...
      for (Field f : c.getDeclaredFields()) {                    // 每条记录重建 Field 数组
          if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
          f.setAccessible(true);                                 // 每字段每记录 setAccessible
          f.set(copy, f.get(source));
      }
  ```
- **严重程度**: P2
- **并发场景**: 单任务线程（reduce 算子在 keyed 流主循环内逐记录执行；`copyForSubtask` 后每 subtask 独立实例，无共享，故纯为热路径分配/反射开销，非正确性问题）
- **触发时序**: 不需要交错时序——稳态每条进入 `sum(String)/min(String)/max(String)` 聚合的记录都完整执行一次 `copyBean`：`getDeclaredConstructor()`（JDK 每调用重扫 declaredConstructors + 数组拷贝）→ 反射实例化 → 逐 superclass `getDeclaredFields()`（每次返回全新 Field 拷贝数组）→ N 次 `setAccessible` + N 次反射 get/set。字段只被 `initField` 缓存了聚合目标那一个 Field，`copyBean` 零缓存。
- **现状**: per-record 分配放大：对 10 字段 bean，每条记录额外产生 ~1 个 Constructor 查找 + ~10 个 Field 拷贝对象 + 数组 + 装箱（fieldAccessor.get 返回 Number 装箱）+ 新实例，全部短命对象进 TLAB。
- **风险**: 量化口径：调用频率 = 该 reduce 算子的记录速率（设 10^4–10^5 rec/s/subtask）；单次代价 = getDeclaredFields()(≈0.3–1µs，10 字段) + getDeclaredConstructor()(≈0.1–0.3µs) + N×(setAccessible+get+set)(≈30–100ns/字段) ≈ **1–3µs/记录**。10^4 rec/s 时占单核 1–3%，10^5 rec/s 时 10–30%，且 GC 分配速率放大 ~10×。宽 bean（20+ 字段）线性恶化。窗口聚合（`aggregate(AggregateFunction)`）不受影响，仅字符串字段 reduce 形态命中。
- **建议**: 在 `initField` 同点一次性解析并缓存 `Constructor`（`clazz.getDeclaredConstructor()` 句柄）与 `Field[]` 快照（含已 setAccessible 的实例字段数组），`copyBean` 只做 `constructor.newInstance()` + 遍历缓存 Field 数组；key/类型不变（单 reduce 实例只服务一种 accumulator 类型）。可对照 `WindowOperator.windowNamespace` 的单槽缓存形态。
- **失效面枚举**: 使用 `FieldAggregationReducer` 的全部兄弟入口：`KeyedStreamImpl.sum(String):234`、`min(String):255`、`max(String):276`——三者共用同一 `copyBean`，**全部命中**；`sum(int)/min(int)/max(int)`（:217/:239/:260）走 Comparable/lambda 路径，无反射，不受影响；窗口路径 `WindowedStreamImpl.aggregate:352` 用用户 AggregateFunction，无反射，不受影响；模块内其余 `getDeclaredConstructor().newInstance()` 调用点（`MemoryReducingState.java:89`、`MemoryInternalAppendingState.java:56`、`MemoryStateSerDe.java:700/743`、`SimpleKeyedStateStore.java:113`、`WindowOperator.java:2731`、`WindowOperatorFactoryImpl.java:193`）均只在**首键创建/checkpoint 恢复**时执行一次，非每记录，不命中。
- **信心水平**: 确定
- **误报排除**: 不是"原语使用即问题"——缺陷点在热路径上对**每条记录**重复执行本应一次解析的反射元数据查找，且类内已有 `initField` 缓存先例，证明该缓存意图存在但 `copyBean` 漏掉了。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C9-002] Merging window 路径每记录重建 MergingWindowSet 并全量重写映射状态

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/windowing/WindowOperator.java:977,984,1077,1807-1812`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/windowing/MergingWindowSet.java:80-100,162-167,105-115`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryListState.java:116-125`
- **证据片段**:
  ```java
  // WindowOperator.getMergingWindowSet() —— 每元素调用
  protected MergingWindowSet<W> getMergingWindowSet() throws Exception {
      ...
      return new MergingWindowSet<>(mergingAssigner, mergingSetsState);   // 每记录 new
  }
  // MergingWindowSet 构造器：每记录读全量映射
  if (state != null) {
      Iterable<Tuple2<W, W>> windowState = state.get();
      for (Tuple2<W, W> window : windowState) mapping.put(window.f0, window.f1);  // O(n)
  }
  initialMapping.putAll(mapping);
  // persist()：mapping 因 addWindow 必然 != initialMapping → 每记录全量重写
  public void persist() throws Exception {
      if (!mapping.equals(initialMapping)) {          // O(n) equals，每记录
          List<Tuple2<W, W>> newState = new ArrayList<>();
          for (Map.Entry<W, W> entry : mapping.entrySet()) newState.add(new Tuple2<>(...));
          state.update(newState);                     // MemoryListState.update 再 new ArrayList 全量拷贝
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 单任务线程（merging 窗口算子主循环）；`mergingSetsState` 为 keyed list state，仅本线程读写，非正确性问题
- **触发时序**: 不需要交错时序——session 窗口管线的每条记录：`processElementForMergingWindow:977` 调 `getMergingWindowSet()` → 新建 set 并 O(n) 重建映射 → `addWindow:164-167` 再 O(n) 拷贝 keySet 到新 ArrayList → `:1077 persist()` 因新窗口加入 mapping 必然变化 → O(n) equals + O(n) Tuple2 重建 + `MemoryListState.update` 再 O(n) ArrayList 拷贝 + HashMap 替换。另 `:984` 每记录分配一个匿名 `MergeFunction`。
- **现状**: per-record 三重 O(n)（读重建、equals、全量状态重写），n = 当前 key 的在飞窗口数。
- **风险**: 量化口径：调用频率 = session 窗口管线记录速率（10^4–10^5 rec/s/subtask）；单次代价 = O(n) HashMap 重建 + O(n) equals + 2×O(n) 列表分配，n=50 在飞窗口时 ≈ **2–5µs/记录**（约 100–250 次 Tuple2/HashMap/ArrayList 分配），10^5 rec/s 时占单核 20–50%。同 key 窗口数随会话密度增长，n=200 时进一步恶化。`initialMapping` 只在构造时快照而 persist 后不收敛，导致**首条记录之后每条记录都走全量重写分支**。参照实现（Flink 同源结构）按 key 缓存 `MergingWindowSet` 正是为消除此开销；`nop-benchmark-stream` 的 `WindowOperatorProcessElementBench` 已含 SESSION 模式，可直接量化验证。
- **建议**: 按当前 key 缓存 `MergingWindowSet`（`Map<K, MergingWindowSet<W>>` 或单槽 lastKey 缓存，同 `MemoryKeyedStateBackend.cachedNamespaceAndKey` 形态），key 切换/窗口清理/`snapshotState` 时失效；persist 后同步 `initialMapping`（或改为脏标记），把每记录重写降为"有合并/退役才写"。
- **失效面枚举**: 全部命中点：`WindowOperator.processElementForMergingWindow:977`（每记录）；`WindowOperator` 定时器触发路径 `:1186-1189`（每 timer fire，频率低于记录速率，同缺陷同修）；regular window 路径 `processElementForRegularWindow:1133` 不用 MergingWindowSet，不命中；`MergingWindowSet` 的其他消费方仅 `WindowOperator` 一处（grep 全模块确认），无更多兄弟。
- **信心水平**: 确定
- **误报排除**: 不是"循环内 new ArrayList 一律报问题"——此处是结构性每记录全量状态重写（读-建-比-写四段 O(n)），且与同源参照实现已修正的形态可对照，收益有现成 SESSION 基准可量化，符合本维度"可量化"门槛。
- **复核状态**: 已驳回

### [nop-stream-core-runtime-C9-003] CachingTableLookup 驱逐路径在同步查找热路径上做 O(maxEntries) 全表扫描

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/lookup/CachingTableLookup.java:132-172`
- **证据片段**:
  ```java
  public Object lookup(Object key) {            // 每记录（维表 join 同步路径，WI11 声明）
      Entry entry = cache().get(key);
      if (entry != null && now - entry.loadNanos < ...ttl...) return unwrap(entry.value);
      Object value = delegate.lookup(key);      // miss → DB 查询
      putEntry(key, value, now);                // → evictOldestIfNeeded
  }
  private void evictOldestIfNeeded(long now) {  // 每个 miss 触发
      while (map.size() > maxEntries) {
          for (Map.Entry<Object, Entry> e : map.entrySet()) {   // O(maxEntries) 扫描找最老
              if (e.getValue().loadNanos < oldest) { oldest = ...; oldestKey = ...; }
          }
          map.remove(oldestKey);
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 任务线程（per-record `lookup`，WI11 owner 语义"per-record 路径完全同步"）与后台 refresh 线程共用一个 `ConcurrentHashMap`（弱一致迭代，正确性可接受；本条只量化扫描成本）
- **触发时序**: 不需要交错时序——当活跃 key 基数 > `maxEntries`（工作集超缓存）时稳态每条记录都是 miss：`putEntry` 必触发 `evictOldestIfNeeded`，每次驱逐做一次完整 O(maxEntries) 迭代比较；TTL 过期场景下每 key 每 TTL 周期也各触发一次。
- **现状**: 驱逐采用"每次 miss 全表扫描找最老"而非堆/近似 LRU 结构，扫描发生在每记录同步路径上。
- **风险**: 量化口径：调用频率 = miss 频率。基数超限场景（维表 key 基数 10^5、`maxEntries` 10^3，记录速率 10^4 rec/s）→ 每记录 1 次 O(10^3) 扫描 ≈ 10^7 次 Entry 比较/秒 ≈ **5–15% 单核**，且与 DB 回源查询叠加；若 delegate 被 `CachingTableLookup` 包装二层（scan/execute 分离场景），miss 频率进一步抬升。`refreshIntervalMs` 内重复 miss 不去重（无 in-flight 装载合并），同一热 key 的并发 miss 各自回源 + 各自全表扫描。
- **建议**: 驱逐结构改为 `LinkedHashMap`（access/insert 序）或按 `loadNanos` 的延迟堆，驱逐摊还 O(log n)；或最小改动——`evictOldestIfNeeded` 改为每次只驱逐一个最老项的近似选择（复用后台 scan 线程做精确驱逐），把 O(n) 扫描移出同步路径。平台侧可对照 `io.nop.commons.cache.CacheConfig` 的容量淘汰设施（本类为 WI11 自建，未复用平台缓存设施）。
- **失效面枚举**: 全部 miss 入口：`lookup:144`（同步 miss，每记录）——命中；`refreshOne:239`（后台刷新成功路径）写回时**不触发驱逐**（直接改 Entry 字段，不走 `putEntry`），不命中；`ensureInit`/`cache()` 懒初始化路径不涉及驱逐。同族"维表查找"实现仅此一个类（`connector/lookup` 包内仅 `ITableLookup`/`CachingTableLookup`），无更多兄弟。
- **信心水平**: 确定
- **误报排除**: 不是"有界缓存（配置上限）即豁免"——误报校准排除的是上限配置与规模声明矛盾的情形；此处是**上限存在但驱逐算法在同步热路径上退化**，扫描成本可量化且随 maxEntries 线性放大，属于 C9 定向检查项。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C9-004] FLIP-27 源循环空闲时 Thread.yield() 自旋烧核

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/SourceReaderOperator.java:349-378`
- **证据片段**:
  ```java
  while (isRunning) {
      drainControlMails();
      next = reader.pollNext();
      if (next != null && next.isPresent()) { ... }
      else {
          if (!isRunning) break;
          if (reader.isFinished()) { ...break; }
          // Cooperative yield: avoid busy-loop when reader is idle but not finished.
          Thread.yield();          // 空闲路径：无 park，纯自旋
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: SOURCE 角色任务线程独占自旋；与同进程其他任务线程/心跳线程竞争 CPU（无锁问题，纯 CPU 资源浪费 + 调度干扰）
- **触发时序**: 不需要交错时序——任意实现 `SourceReader` 的非阻塞源（消息源 `MessageSourceReader`、文件目录扫描源等连接器实现，轮询语义天然存在空档）在"无数据且未 finish"期间进入 else 分支：`pollNext` 空转 + `Thread.yield()`，循环速率仅受 yield 调度约束（每秒 10^5–10^7 次），每轮还附带 2 次 CLQ poll（mailbox）+ `isFinished` volatile 读。
- **现状**: 注释声称"avoid busy-loop"，但 `Thread.yield()` 不阻塞只让步，空闲源子任务持续吃满一个核。
- **风险**: 量化口径：频率 = 空闲期间持续（占空比 100%）；代价 = 每 SOURCE 子任务 1 个核。对照：同模块 MIDDLE/SINK 的 `InputGate.readMultiChannel` 空闲路径用 `LockSupport.parkNanos(IDLE_PARK_NANOS=10ms)`（`InputGate.java:805`），空闲 CPU ≈ 0。一个 8 核节点部署 4 个空闲 FLIP-27 源子任务即烧掉半机 CPU，并挤占数据面任务与 GC 线程——负载上升时表现为吞吐无端下降。
- **建议**: 空闲分支改 `LockSupport.parkNanos(...)`（对齐 `InputGate.IDLE_PARK_NANOS` 形态，1–10ms 档，保住 checkpoint 邮件 ~50ms 内被排干的时延预算）；或在 `SourceReader` 契约上增加可选 availability future（FLIP-27 正统形态），无 future 时回落 park。
- **失效面枚举**: 同族 yield 自旋点全量清单：`SourceReaderOperator.java:377`（生产主循环，**命中**）；`MailboxExecutor.runLoop:111`（文档声明"standalone…for future use"，当前无生产调用方——grep 确认仅测试可达，**不命中但登记为同族**）；`InputGate` 读循环已用 parkNanos，不命中；`StreamTaskInvokable.processInputGate` 空闲路径经由 `InputGate.read(50ms)` 有界阻塞，不命中。
- **信心水平**: 很可能（核心循环语义确定自旋；唯一不确定度在个别连接器 `pollNext` 内部可能自带阻塞，但那属于连接器实现选择，核心循环的空闲策略仍无界自旋）
- **误报排除**: 不是"测试代码简化并发模型"——`run()` 是 SOURCE 角色的生产主循环（`StreamTaskInvokable.invokeSource:860-865` 直达）；也不是与 InputGate 一致的有界阻塞（两者策略确实不一致，InputGate 用 park，此处用 yield）。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C9-005] RemoteResultPartition 每记录重复解析固定 valueType 并做两次同步门控调用

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteResultPartition.java:240-254,199-226`
- **证据片段**:
  ```java
  public void write(StreamElement element) throws InterruptedException {
      ...
      String valueType = typeRegistry != null ? typeRegistry.getOutputTypeClassName(edgeId) : null; // 每记录
      StreamMessageEnvelope envelope = StreamElementCodec.encode(element, valueType, epochId);
      gateOnBacklog();          // → peekBacklogCached() + bumpBacklogCached()：两次 synchronized(this)
      sendUnderLock(envelope);  // synchronized(sendLock)
  }
  private synchronized long peekBacklogCached() { ... }   // monitor #1（this）
  private synchronized void bumpBacklogCached() { ... }   // monitor #1 再次获取
  ```
- **严重程度**: P3
- **并发场景**: 单生产者任务线程（每记录）；`sendLock`（独立 monitor）只串行化后端 send；gating 关闭时 `gateOnBacklog` 直接返回，命中点只剩 valueType 查询
- **触发时序**: 不需要交错时序——每条跨任务记录固定执行 1 次 `ConcurrentHashMap.get(edgeId)`（key 恒定）+ gating 启用时 2 次 `synchronized(this)` 获取/释放；分区构造后 edge→valueType 映射不可变。
- **现状**: 不变量被逐记录重算；两个可合并的同步调用各自承担 monitor 进入/退出成本，且锁对象是分区实例 `this`（与继承自 `ResultPartition` 的公开方法共享 monitor 面，虽当前父类无 synchronized 方法，锁范围偏宽）。
- **风险**: 量化口径：频率 = 跨任务边记录速率（10^4–10^5 rec/s/边）；单次代价 = CHM.get ≈ 20–50ns + 2×monitor ≈ 40–80ns ≈ **~0.1µs/记录**，10^5 rec/s 时 <1% 单核。属可测量但低收益项；独立成条是因修复为零风险构造期缓存（valueType 是分区不变量）。
- **建议**: 构造函数内解析 `String outputValueType = typeRegistry != null ? typeRegistry.getOutputTypeClassName(edgeId) : null` 存 final 字段，`write` 直接用；`peekBacklogCached`+`bumpBacklogCached` 合并为单个返回"准入后缓存值"的 synchronized 方法（一次 monitor 往返），或改 `this` 锁为 private 锁对象与父类 monitor 面隔离。
- **失效面枚举**: `typeRegistry.getOutputTypeClassName` 的全部调用点（grep 全模块）：`RemoteResultPartition.write:249`（每记录，**命中**）——同文件无第二调用；`TypeRegistry` 无其他每记录消费方（`RemoteGraphExecutionPlanBuilder` 为构建期）。gating 双 synchronized 兄弟：`peekBacklogCached/bumpBacklogCached/forceBacklog/currentBacklog` 四方法同锁（`readBacklogCached`/`currentBacklog` 供 ops gauge，抓取频率低，不命中）。
- **信心水平**: 确定（代码路径确定；收益量级小故 P3）
- **误报排除**: 不是"看到共享容器就报"——TypeRegistry 是 ConcurrentHashMap 本身使用无误；报告点仅为不变量的每记录重算与可合并锁往返，按量化口径登记。
- **复核状态**: 已驳回

### [nop-stream-core-runtime-C9-006] 字符串后端数据面对同一记录值做双重 JSON 编码/解码

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/transport/StreamElementCodec.java:88-103`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/AbstractStringWireCodec.java:36-39`（`SysDaoWireCodec.java:40-48` 同构）
- **证据片段**:
  ```java
  // 第 1 层：StreamElementCodec.encode —— 记录值 → JSON 字符串
  Object serializedPayload = record.getValue() != null ? JsonTool.stringify(record.getValue()) : null;
  // 第 2 层：AbstractStringWireCodec.toWire —— 整个 envelope（含已字符串化的 payload）再转 map + 再 stringify
  public Object toWire(StreamMessageEnvelope envelope) {
      return JsonTool.stringify(DataPlaneWireSupport.toWireMap(envelope));  // payload 作为转义 JSON 嵌入
  }
  // 接收侧对称双重解析：fromWire 解析外层 → StreamElementCodec.decode 再 parseBeanFromText(payload)
  ```
- **严重程度**: P3
- **并发场景**: 发送侧任务线程 + 接收侧消息后端派发线程（每记录各一）；无锁问题，纯序列化放大
- **触发时序**: 不需要交错时序——Kafka/Pulsar/DB（SysDao）三类字符串 wire 后端的每条数据记录：值 JSON 序列化 → envelope flatten 成 8 项 LinkedHashMap（epoch/timestamp 装箱）→ 整 map 再 JSON 序列化（第 1 层产物作为转义字符串内嵌）→ 后端 → 外层 JSON 解析 → `StreamElementCodec.decode` 对内嵌 payload 字符串**再** `JsonTool.parseBeanFromText`。
- **现状**: 记录值经历 2 次序列化 + 2 次解析；转义内嵌使第 2 层体积大于第 1 层（引号/反斜杠转义膨胀）。
- **风险**: 量化口径：频率 = 字符串后端边上的记录速率（10^4–10^5 rec/s/边）；单次代价 = 对 500B 记录值，2 次 stringify + 2 次 parse ≈ 2×(1–3µs)，相比单次编码多付出 **~1.5–4µs/记录** 与 ~1.3× wire 体积；10^5 rec/s 时占派发/任务线程各 15–40%。注：`.stream.xml`/远程链路选择 JSON wire 属 Stage 40 已声明设计（Non-Goal 载明 envelope 序列化格式不变），故仅登记 P3 优化候选，不判缺陷级；`StreamElementCodecRoundTripBench` 已存在，收益可直接复测。
- **建议**: 若 wire 格式可演进：`toWireMap` 对 `TYPE_STREAM_RECORD` 走"payload 直通"形态（外层只包 envelope 元字段，payload 用后端结构化 JSON 值而非转义字符串），decode 侧按 type 分派一次解析；短期零格式变更选项：`RemoteResultPartition.write` 内合并 encode+toWire 的一次 map 中转（省 flatten 分配，序列化次数不变）。
- **失效面枚举**: 双重编码命中面 = 三个字符串 wire 编解码器：`AbstractStringWireCodec.toWire`（`KafkaStringWireCodec`/`PulsarStringWireCodec` 共用父实现，**命中**）、`SysDaoWireCodec.toWire`（**命中**）；`IdentityWireCodec`（LocalMessageService 按引用传 envelope）不做字符串化，不命中；`StreamElementCodec.encode` 本身是两层编码的第 1 层公共点，仅被字符串后端二次包装时命中。接收侧对称点：`AdaptingConsumer.fromWire` + `StreamElementCodec.decode:161`。
- **信心水平**: 确定（调用链静态可证；量级估算基于 JsonTool 典型吞吐）
- **误报排除**: 不是"用 JSON 即缺陷"——wire 格式为已声明设计；报告点限定为**同一记录值的重复序列化/解析**这一冗余层，且注明设计边界、只登记 P3。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C9-007] InMemoryMaterializationPoint 每记录同步写与恢复 replay 互斥（含已声明边界）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/materialization/InMemoryMaterializationPoint.java:61-93`
- **证据片段**:
  ```java
  public synchronized void write(StreamElement element, long epoch) {  // 每记录，物化边
      store.add(new MaterializedElement(element, epoch));              // 无界增长（类文档声明）
  }
  public synchronized List<MaterializedElement> replay(long fromEpoch) { // 恢复路径，同一 monitor
      for (MaterializedElement me : store) { ... out.add(me); }          // O(n) 全量拷贝，持锁
  }
  ```
- **严重程度**: P3
- **并发场景**: 生产者任务线程（每记录 `ResultPartition.write` 双写）与 supervision 恢复线程（region 重启 replay）；单 monitor 串行化
- **触发时序**: T1（生产者）持续在 `write` 上获取 monitor（每记录一次）→ T2（恢复线程）进入 `replay` 持锁做 O(n) 全量 ArrayList 拷贝（n = 自 region 起点累计物化记录数）→ T1 在每次 `write` 入口被阻塞至拷贝完成 → 上游 `emit` 计时抬升（WI16 backpressure 代理），背压逐级上溯。恢复窗口虽短，n 大时单次停顿可达百 ms 级。
- **现状**: 写路径单次成本极低（无争用时 monitor ≈ 20–50ns + MaterializedElement 分配），实质风险集中在"replay 持锁拷贝期间生产者整体停摆"；类 Javadoc 已声明"correctness-first 实现，生产级 RocksDB/磁盘物化为后续优化候选"。
- **风险**: 量化口径：写路径稳态代价 = 10^5 rec/s × ~70ns ≈ <1% 单核（可忽略）；争用路径 = replay 拷贝 n=10^6 条时 ≈ 30–100ms 持锁 → 生产者同窗停顿，10^5 rec/s 时积压 ~3k–10k 条（未超 1024 队列容量的边会先 offer 溢出到物化库，形成自愈；无池无物化队满的边阻塞）。因 Javadoc 已显式声明该实现为过渡形态，按误报校准第 6 条登记 P3 而非 P2。
- **建议**: 优化候选（与已声明的生产级实现合并处理）：`replay` 改为持锁取 `store` 不可变快照引用（写时复制段/按 epoch 分段 List），拷贝移出锁外；或按 owner 声明方向直接落地分片/外部存储实现时消除该 monitor。
- **失效面枚举**: `IMaterializationPoint` 的生产实现仅 `InMemoryMaterializationPoint` 一个（grep 确认）；其全部 synchronized 方法：`write`（热，命中）、`replay/replayAll`（恢复冷路径，持锁根源）、`seal/size/getLastEpoch`（低频，不命中）。挂接点唯一：`ResultPartition.setMaterializationPoint` → 仅物化启用边（`JobEdge` 显式标记）生效，非物化边零成本，不命中。
- **信心水平**: 确定
- **误报排除**: 不是"低频路径粗锁"误报的重演——write 是每记录热路径，但类文档已显式声明 correctness-first 与优化 deferred，且单次成本低于本维度报告阈值，故按已声明边界降 P3 登记，不消失。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C9-008] StreamElementCodec 解码路径每记录重跑类名白名单校验

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/transport/StreamElementCodec.java:44-73`
- **证据片段**:
  ```java
  // 注释自述缓存动机："decode previously ran validateClassName + Class.forName for EVERY record"
  private static Class<?> resolveValueType(String valueType) {
      ClassNameValidator.validateClassName(valueType);   // 每条记录仍全量执行（23 个前缀 startsWith）
      Class<?> clazz = DECODED_VALUE_TYPES.get(valueType); // 缓存只覆盖了 Class.forName 一半
      if (clazz == null) { ... computeIfAbsent(Class.forName) ... }
      return clazz;
  }
  ```
- **严重程度**: P3
- **并发场景**: 消息后端派发线程（每条远端记录 decode）；`DECODED_VALUE_TYPES` 为 ConcurrentHashMap，纯读命中，无正确性问题
- **触发时序**: 不需要交错时序——每条 `TYPE_STREAM_RECORD`/`TYPE_SIDE_OUTPUT_RECORD` 信封解码固定执行：23 次 `String.startsWith`（校验器无缓存）→ CHM get →（命中）返回。同一 valueType 名在同一边上恒定，校验是纯函数（"once pass always pass"，类注释自证）。
- **现状**: 缓存修复只做了一半：`Class.forName` 已缓存，校验器每记录重跑。
- **风险**: 量化口径：频率 = 远端边记录速率（10^4–10^5 rec/s/边，派发线程侧）；单次代价 = 23 × startsWith ≈ **100–200ns**；10^5 rec/s 时占派发线程 1–2%。低收益、零风险项，与 C9-006 同处 decode 链，合并修复成本最低（CHM 命中后再校验或校验结果随 Class 一并入缓存值）。
- **建议**: 将 `validateClassName` 并入 `DECODED_VALUE_TYPES` 缓存语义（首次通过校验的 name 才入缓存；未入缓存的新 name 才走校验 + forName + computeIfAbsent 完整链），与类注释声称的"behavior-equivalent"缓存意图对齐。
- **失效面枚举**: `resolveValueType` 调用点全部清单：`StreamElementCodec.decode:161`（TYPE_STREAM_RECORD，每记录，**命中**）、`:202`（TYPE_SIDE_OUTPUT_RECORD，每 side-output 记录，**命中**）；encode 侧不经校验器，不命中；`ClassNameValidator.validateAccumulatorClass` 无每记录调用方（grep 仅 checkpoint 侧），不命中；checkpoint/CEP 侧校验调用在冷路径，不命中。
- **信心水平**: 确定
- **误报排除**: 不是"防御性校验冗余一律报"的误报扩大——该缓存类注释已自证"每记录校验"是被修复目标，修复遗漏了校验半边；量化在 10^5 rec/s 口径下可测（1–2% 派发线程），达 P3 登记门槛。
- **复核状态**: 已保留

---

## 基准缺口登记（P3，对照 nop-benchmark 惯例）

**已有覆盖**（`nop-benchmark/nop-benchmark-stream/src/main/java/io/nop/benchmark/stream/`，本轮不重复立项）：`StreamElementCodecRoundTripBench`（C9-006 收益可直接复测）、`WindowOperatorProcessElementBench`（含 SESSION 合并路径，C9-002 修复前基线现成）、`MemoryKeyedStateBench`、`InputGateReadLoopBench`、`RemoteTransportWriteBench`、`BufferPoolPermitBench`、`TimerServiceBench`、`CheckpointSerDeBench`、`ProcessingTimeDriverLatencyBench`、`CepOperatorBench`/`NfaProcessBench`、`ConnectorInvokeBench`。

**缺口**（建议 JMH 形态均挂 `nop-benchmark-stream`，Mode.AvgTime + per-op 吞吐双量纲）：

| # | 缺口 | 建议基准形态 | 关联发现 |
|---|---|---|---|
| BG-1 | `TaskMailbox` put/poll/drainAndRun 无基准（control mail lambda 分配 + CLQ + notifyAll 往返未量化；barrier 高频注入场景无数据） | 单线程 put+poll 往返；双线程（1 生产者 put × N + 消费者 drain）吞吐 | H1/H9 控制面 |
| BG-2 | `CachingTableLookup` 无基准（TTL 命中/miss/驱逐三态；基数 > maxEntries 的驱逐退化无数据） | hit-only 稳态 × miss-with-eviction（keySet 10^5, maxEntries 10^3）× 后台 refresh 并发 | C9-003 |
| BG-3 | `FieldAggregationReducer`（sum/min/max(String)）无基准；`StreamReduceOperator` 亦无 | 3 聚合形态 × 宽 bean（10/20 字段）× 修复前后对照 | C9-001 |
| BG-4 | `SourceReaderOperator.run` 空闲策略无量化（yield 自旋 vs park 的 CPU/唤醒时延对照） | 空闲 reader CPU 占用（非 JMH，`ThreadMXBean` 采样 harness）+ 有数据时首记录时延 JMH | C9-004 |

（C9-002/C9-006 已有基准，登记为"修复对照基线"而非缺口。）

## 覆盖度自述

- 逐文件精读：`StreamTaskInvokable`、`InputGate`、`ResultPartition`、`RecordWriter`、`TaskMailbox`、`MailboxExecutor`、`StreamElementCodec`、`TypeRegistry`、`ClassNameValidator`、`RemoteResultPartition`、`RemoteInputChannel`、`DataPlaneMessageServiceAdapter`、`SysDaoWireCodec`、`AbstractStringWireCodec`+`DataPlaneWireSupport`、`PollingJdbcMessageService`、`CachingTableLookup`、`MergingWindowSet`、`MemoryListState`、`MemoryAggregatingState`、`MemoryReducingState`、`MemoryKeyedStateBackend`（key 缓存段）、`StreamSinkOperator`、`TwoPhaseCommitSinkFunction`、`KeyExtractingOutput`、`ChainingOutput`、`StreamSourceOperator`（collect 路径）、`SourceReaderOperator`（run/split 路径）、`MicrometerStreamTaskMetrics`+`TaskMetricsHandle`、`ContainerValueCodec`、`HeapInternalTimerService`（结构+TimerEntry）、`EquiJoinOperator`（processElement 段）、`WindowOperator`（processElement/merging/状态访问段）、`KeyedStreamImpl`（聚合段）、`InMemoryMaterializationPoint`、`CheckpointBarrierTracker`（同步段）。
- 结构性扫描：全模块 synchronized/computeIfAbsent/反射/Pattern/JsonTool/ThreadLocal 静态可变点（基线 C1–C6 grep 复现），冷路径命中项逐条排除理由见检查范围说明。

# C9 并发高性能 — nop-stream-core-runtime（深挖第 2 轮追加）

## 深挖第 2 轮追加

- **审计维度**: C9（并发高性能 / 热路径），第 1 轮文件 `C9-concurrent-perf.md` 的深挖追加轮
- **审计对象**: `nop-stream/nop-stream-core/src/main/java`、`nop-stream/nop-stream-runtime/src/main/java`（同第 1 轮；`_` 前缀生成文件不作为发现对象）
- **盲审边界执行声明**: 本轮只读取了共享提示词 `ai-dev/skills/concurrency-resource-audit-prompt.md`、本目录第 1 轮 `C9-concurrent-perf.md` 与 live code；未读取 `ai-dev/audits/` 下其他审计目录、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`
- **本轮深挖的盲区方向**: (1) 第 1 轮未精读的 regular window 路径（`processElementForRegularWindow`/`processWindowElement`/active-window registry）；(2) 触发器每记录路径（round 1 仅审计了 `FieldAggregationReducer` 与 merging 窗口，未审计 trigger 状态访问）；(3) watermark 生成/合并路径（`TimestampsAndWatermarksOperator`/`WatermarkOutputMultiplexer`/`CombinedWatermarkStatus`）；(4) 发送侧 per-send 分配与锁作用域（`sendLock`/`MessageSendOptions`/topic）；(5) 逐条 I/O 无批量路径（DB/JDBC 消息后端）；(6) 热路径日志急切求值；(7) 定时器注册的锁交互（`HeapInternalTimerService` → `ProcessingTimeServiceDriver`）；(8) 本应并行被串行化的点

### 本轮盲区排除记录（未立案理由，防复检重查）

- **`TimestampsAndWatermarksOperator` 每记录路径**（`operators/TimestampsAndWatermarksOperator.java:97-131`）：每记录 1 次小对象分配（`new OperatorWatermarkOutput()`，未逃逸、JIT 可标量替换）+ 1 次 `CoreMetrics.currentTimeMillis()`（发射节流门，~20ns）+ 2 次 nanoTime（处理耗时度量，属 per-record 可观测性的设计代价）。单次 ~50-80ns，低于第 1 轮 P3 报告门槛（C9-008 为 100-200ns），不立案。
- **watermark 合并路径**：`WatermarkOutputMultiplexer`/`CombinedWatermarkStatus` 在 nop-stream-core 主源码内无任何算子调用方（仅互相引用，为连接器预留设施）；生产 watermark 路径 = `TimestampsAndWatermarksOperator`（已核，干净）+ `InputGate` 合并（第 1 轮已覆盖）。
- **`HeapInternalTimerService` 每记录双注册的锁交互**：`registerProcessingTimeTimer:96-103`/`registerEventTimeTimer:119-123` 只做 `computeIfAbsent` + `TimerEntry` 去装箱 add + `nextProcessingTimeTimer` volatile 写，调度线程只读该 volatile（类注释 :56-60 自证），无锁、无 notify——第 1 轮判定维持，无新发现。
- **`RemoteResultPartition.sendLock` 作用域**：实例级 `private final Object sendLock`（:70），单生产者任务线程 + close() 竞争面，锁范围正确；topic 为构造期不变量字段，无 per-send 重建；`IMessageService.send(topic, envelope)` 不传 options（无 per-send options 分配）。第 1 轮 C9-005 已登记的部分之外无新增。
- **逐条 I/O 无批量路径**：`PollingJdbcMessageService`（send = 每消息单条 INSERT，`launch/PollingJdbcMessageService.java:177-186`）Javadoc 显式声明为 **test-infrastructure**（"production deployments use SysDaoMessageService / Pulsar"），按误报校准第 6 条不立案；生产 DB 后端 `SysDaoMessageService` 位于 `nop-sys/nop-sys-dao`（scope 外模块），其批量能力缺口不属本轮对象，建议在该模块审计轮覆盖。Kafka/Pulsar 消息服务实现在平台消息模块，scope 外。
- **`OverWindowOperator`/`PerKeyOrderedBuffer`**：每元素字符串拼接 + durable MapState 镜像存在（`OverWindowOperator.java:191`），但全仓 grep 确认该算子**仅测试可达**（无任何生产 graph/factory 接线，WI12 evidence 组件），无法建立生产调用频率口径，不立案；若未来接入生产管线，按 C9-009 同族口径补报。
- **热路径日志急切求值**：operators/execution/windowing/transport 热路径包内 LOG 调用全部在失败/丢弃/生命周期分支，slf4j 占位符形态无急切拼接；`MergingWindowSet.java:176` 的 debug 两参为既有引用非新建（其外层成本已并入 C9-002）。无发现。
- **`StreamReduceOperator`**（`operators/StreamReduceOperator.java:98-116`）：每记录 HashMap get/put + 1 StreamRecord 分配，无反射/无锁/无冗余；`values` 无界增长属 C5 维度不属 C9。无发现。
- **`StreamTaskInvokable.classifyAndDispatch`**（`execution/task/StreamTaskInvokable.java:1162-1206`）：instanceof 链 + 2 次 nanoTime（per-record 处理耗时度量，第 1 轮已在 H1 判定 metrics 可控），无分配。无发现。
- **维护/采样设施**：`StreamDataSampler`/`StreamConnectivityProber`/`WatermarkEstimator` 仅 `runtime/maintain` CLI（`StreamSampleCommand` 等）与连接器 probe 路径可达，冷路径。`AlertService`/`StreamJobEventBus` 为 ops/生命周期事件，冷路径。
- **`StreamDataSampler` 等已排除**；`MessageSourceReader` 位于 `nop-stream-connector`（scope 外）。

---

### [nop-stream-core-runtime-C9-009] WindowOperator active-window registry 每记录重建 paneKey 复合字符串并分配 ActiveWindow 探针对象

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/windowing/WindowOperator.java:1094,1478-1480,1905-1908,1620-1647`
- **证据片段**:
  ```java
  // processWindowElement —— regular 与 merging 两路共用的每记录入口
  if (registerActiveWindow(key, actualWindow)) {          // :1094 每记录每窗口无条件执行
      emitLifecycleEvent(WindowLifecycleEvent.Type.OPEN, key, actualWindow);
  }
  private boolean registerActiveWindow(K key, W window) { // :1905
      return activeWindows.computeIfAbsent(paneKey(key, window), k -> new LinkedHashSet<>())
              .add(new ActiveWindow<>(key, window));      // 每记录分配探针对象，add 返回 false 即丢弃
  }
  private String paneKey(K key, W window) {               // :1478 每记录字符串拼接
      return key + STATE_KEY_SEPARATOR + windowNamespace(window);
  }
  ```
- **严重程度**: P3
- **并发场景**: 单任务线程（window 算子主循环）；`activeWindows` 仅任务线程读写（checkpoint 快照走 DTO），非正确性问题，纯热路径分配/哈希开销
- **触发时序**: 不需要交错时序——regular 与 merging 窗口管线的每条被接受记录（`processElementForRegularWindow:1143` 与 `processElementForMergingWindow:1074` 均转入 `processWindowElement`）固定执行：`paneKey` 拼接 `key + "\u0000" + "TW:start,end"`（新建 StringBuilder + char[] + String）→ `HashMap.computeIfAbsent` 以全新 String 计算 hash → `new ActiveWindow<>(key, window)` 分配 → `LinkedHashSet.add` 值比较（`ActiveWindow.equals:1632` 走 `Objects.equals` ×2）返回 false 丢弃。同窗口连续记录每次都完整重做（registry 内已存在该窗口）。
- **现状**: 每记录为"确认窗口已在 registry"这一个布尔判断，重建本可按 (key, window) 记忆化的复合 key 并分配一个一次性比较对象。
- **风险**: 量化口径：频率 = 窗口管线记录速率（10^4–10^5 rec/s/subtask，与第 1 轮同基线）。单次代价 = paneKey 拼接（40–80 chars ≈ 60–120ns）+ 新 String key 重算 hash 的 computeIfAbsent（≈30–60ns）+ ActiveWindow 分配 + Set.add（≈30–60ns）≈ **0.12–0.24µs/记录**。10^5 rec/s 时占单核 1.2–2.4%，附加分配 ~100–250B/记录（≈10–25MB/s GC 压力）；长字符串 key（URL/订单号）与多窗口记录（sliding 每记录 n 个窗口）线性放大。同文件 `windowNamespace` 已为同类问题建了单槽 memo（:2550-2571，注释自证"rebuilt once per window switch instead of once per state operation"），paneKey 漏在同型修复之外。
- **建议**: 仿 `windowNamespace` 单槽 memo 形态增加 `lastPaneKeyOf(lastKey, lastWindow)`（`processWindowElement` 内 key/window 切换时失效）；或 registry 结构改嵌套 `Map<K, LinkedHashSet<W>>` 消除字符串键与探针对象（`registerActiveWindow` 改 `Set.add(window)` 值语义判断）。注意 `removeActiveWindow:1924,1928` 同方法内拼两次 paneKey，可顺带复用一次拼接。
- **失效面枚举**: `paneKey` 全部调用点（grep 全文件）：`registerActiveWindow:1906`（每记录，**命中**）；`transferActiveWindow:1916`（merge 路径，频率=合并事件）；`removeActiveWindow:1924+1928`（purge/清理，双拼接，频率=窗口结束）；`computePaneInfo:1451`（每次窗口 FIRE，`:1335` 经 emitWindowContents 触发）；`clearWindowContents:2261`（purge）；merging MergeFunction 内 `paneTracking.remove:1030`（合并事件）。仅 `registerActiveWindow` 为每记录级命中，其余为窗口速率级同族（同修）。兄弟算子核查：`OverWindowOperator` 仅测试可达（见盲区排除）；`EquiJoinOperator`/`StreamReduceOperator` 无字符串复合键，不命中。
- **信心水平**: 确定
- **误报排除**: 不是"循环内分配一律报"——报告点限定为**不变量（key+window）每记录重派生**，且同类问题在同文件已有被承认并修复的先例（windowNamespace 单槽 memo 的注释即为此动机），本条是该修复漏掉的同型兄弟（F3 口径）；量化达第 1 轮 P3 门槛（对照 C9-008 的 100–200ns）。
- **复核状态**: 已保留

### [nop-stream-core-runtime-C9-010] 有状态触发器每记录经 getSimpleAccumulator 重建 "trigger_key|window|name" stateKey 并装箱计数

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/windowing/WindowOperator.java:2713-2741`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/triggers/CountTrigger.java:44-53`；`ContinuousEventTimeTrigger.java:56`；`ContinuousProcessingTimeTrigger.java:49`；`DeltaTrigger.java:58`；`ProcessingTimeoutTrigger.java:77`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/KeyedStreamImpl.java:188,193`
- **证据片段**:
  ```java
  // WindowOperator.Context.getSimpleAccumulator —— 触发器 onElement 每记录调用
  String stateKey = "trigger_" + key + STATE_KEY_SEPARATOR + window + STATE_KEY_SEPARATOR
          + descriptor.getName();                       // :2720 每记录拼接（window.toString() 分配）
  SimpleAccumulator<T> existing = (SimpleAccumulator<T>) accums.get(stateKey);
  // CountTrigger.onElement —— 每记录
  SimpleAccumulator<Long> count = ctx.getSimpleAccumulator(stateDesc);  // 接口类型 → 装箱通道
  count.add(1L);                                        // 经 add(Long) 装箱；count>127 后分配
  if (count.get() >= maxCount) { ... }
  ```
- **严重程度**: P3
- **并发场景**: 单任务线程（window 算子主循环内触发器回调）；`triggerAccumulators` 仅任务线程读写，非正确性问题
- **触发时序**: 不需要交错时序——使用有状态触发器的窗口管线（`KeyedStreamImpl.countWindow:188` = `PurgingTrigger(CountTrigger)`、`countWindowAndEvictor/slide:193` = `CountTrigger`，及用户经 `.trigger(...)` 接入的 Continuous/Delta/ProcessingTimeout）每条记录 `triggerContext.onElement:1103` → `trigger.onElement` → `ctx.getSimpleAccumulator`：执行 `TimeWindow.toString()`（`TimeWindow.java:106-108`，新建 "TimeWindow{start=..., end=...}"）→ 三段拼接 stateKey（60–100 chars）→ `HashMap.get`（新 String 重算 hash）→ `LongCounter.add(Long)` 装箱（接口引用只能走 `add(Long):50`，`LongCounter:80` 的 `add(long)` 原语重载经接口不可达，其 Javadoc :47 自注 "Consider using add(long) instead"）。
- **现状**: stateKey 的三段组成（key、window、descriptor name）在单次 onElement 上下文内是不变量，却每记录重派生；计数器经 `SimpleAccumulator<Long>` 接口通道强制装箱。
- **风险**: 量化口径：频率 = 有状态触发器管线的记录速率（10^4–10^5 rec/s/subtask）。单次代价 = `TimeWindow.toString` ≈ 40–60ns + stateKey 拼接 ≈ 80–150ns + `HashMap.get` ≈ 30–50ns + 装箱 ≈ 10–20ns（count≤127 时 Long 缓存命中）≈ **0.16–0.28µs/记录**；10^5 rec/s 时占单核 1.6–2.8%，附加分配 ~150–300B/记录。无状态触发器（`EventTimeTrigger`/`ProcessingTimeTrigger`/`PurgingTrigger` 外壳）不进入该路径。
- **建议**: 在 `WindowOperator.Context` 内加单槽 memo（`lastStateKeyOf(key, window, descriptorName)`，key/window 字段为 protected 直接赋值，可在 `processWindowElement`/`onTimer` 设置点失效）；计数器侧为 `SimpleAccumulator` 增加 `long addAndGetLong(long)` 原语默认方法（或 CountTrigger 持有具体 `LongCounter` 类型引用走 `add(long):80`），消除装箱。与 C9-009 同批修复成本最低（同一文件、同一 memo 形态）。
- **失效面枚举**: `getSimpleAccumulator` 全部调用点（grep triggers 包）：`CountTrigger.onElement:46`（每记录，**命中**）、`CountTrigger.clear:67`（窗口清理）；`ContinuousEventTimeTrigger.onElement:56`（每记录，**命中**）+ `:72/:100/:116`（定时器/清理路径）；`ContinuousProcessingTimeTrigger.onElement:49`（每记录，**命中**）+ `:71/:84/:100`；`DeltaTrigger.onElement:58`（每记录，**命中**）；`ProcessingTimeoutTrigger.onElement:77`（每记录，**命中**）+ `:111`。不命中：`EventTimeTrigger`/`ProcessingTimeTrigger`/`PurgingTrigger`（无状态触发器，grep 确认无 getSimpleAccumulator 调用）；`WindowOperator.removeTriggerAccumulators:1318` 构造同型 prefix（前缀扫描删除），频率=窗口清理，同族冷路径。
- **信心水平**: 确定
- **误报排除**: 不是"反射元数据未缓存"的同型误报复制——本条缺陷点是**字符串键每记录重派生 + 接口通道强制装箱**，两者都有代码内自证意图（LongCounter 的原语重载与"Consider using add(long)"注释证明装箱是被认识但未接通的问题；windowNamespace memo 证明字符串重派生是同类已修问题）；量化口径独立成立，达第 1 轮 P3 门槛。
- **复核状态**: 已驳回

---

## 基准缺口追加（P3，接第 1 轮 BG 编号）

| # | 缺口 | 建议基准形态 | 关联发现 |
|---|---|---|---|
| BG-5 | 有状态触发器每记录路径无基准：`WindowOperatorProcessElementBench` 仅用无状态 `EventTimeTrigger`（`WindowOperatorProcessElementBench.java:96`），`CountTrigger`/`ContinuousEventTimeTrigger` 等 `getSimpleAccumulator` 路径与 `LongCounter` 装箱零数据；C9-009 的 paneKey 开销虽被现有 bench 测量但未单列归因 | 现有 bench 增加 `@Param TRIGGER={EVENT_TIME, COUNT}` 模式 + 记录态（`-prof gc` alloc.norm）对照修复前后；另建议为 `registerActiveWindow` 单列 @Group 操作 | C9-009 / C9-010 |

（BG-1 至 BG-4 见第 1 轮文件；本轮无重复立项。）

## 覆盖度自述（第 2 轮新增核查点）

- 逐文件精读：`WindowOperator`（regular 路径 `processElementForRegularWindow`/`processWindowElement`/`addWindowElement`/`storeElementTimestamp`/`getWindowContents`/`setWindowContents`/`mergeWindowContents`/active-window registry/`paneKey`/`getSimpleAccumulator`/`Context`/`WindowContext` 段）、`CountTrigger`、`LongCounter`、`TimestampsAndWatermarksOperator`（全文）、`WatermarkOutputMultiplexer`（全文）、`CombinedWatermarkStatus`（全文）、`OverWindowOperator`（全文）、`PerKeyOrderedBuffer`（全文）、`StreamReduceOperator`（全文）、`StreamTaskInvokable`（processInputGate/classifyAndDispatch/sideOutputConsumerFor 段）、`RemoteResultPartition`（write/sendUnderLock/close/gateOnBacklog 段复核）、`RemoteInputChannel`（EnvelopeConsumer/onWireDecodeFailure 段复核）、`DataPlaneMessageServiceAdapter`（全文）、`PollingJdbcMessageService`（全文）、`HeapInternalTimerService`（注册/删除/fire 段复核）。
- 结构性扫描：热路径包 synchronized 全量复核（operators/streamrecord/windowing/common state+buffer/execution transport+materialization——仅 SourceReaderOperator split 恢复、InMemoryMaterializationPoint（C9-007 已立）、StreamSinkOperator 2PC 冷路径命中）；热路径包 LOG 调用全量复核（无急切求值）；`TimeWindow.toString`/`STATE_KEY_SEPARATOR`/`LongCounter` 装箱通道逐点核实。
- scope 外登记：`SysDaoMessageService`（`nop-sys/nop-sys-dao`，生产 DB 消息后端的逐条 I/O 批量能力）、`MessageSourceReader`（`nop-stream-connector`）不在本轮对象内，建议随各自模块审计轮覆盖。

---

# C9 并发高性能 — 维度复核结论（独立复核子代理，2026-10-14）

- **复核人**: 独立复核子代理（task_id 区别于初审/深挖子代理；本节为追加内容，未改动上方任何发现正文）
- **盲审解除声明**: 本轮按复核阶段授权读取了历史审计（`ai-dev/audits/2026-06-02-deep-audit-nop-stream/07-bizmodel-conformance.md`、`2026-09/2026-09-26-0546-deep-audit-nop-stream-quality/03-performance.md`（下称 r1-perf）、`2026-10/2026-10-05-0523-…-r5/04-concurrency-resource.md`、`2026-10/2026-10-06-0522-…-r6/08-performance.md`（下称 r6-perf）、`2026-10/2026-10-06-0522-…-r6/05-keyed-routing-window.md`、`nop-stream-flink-comparison/2026-07-24-2227-…`）与 `git log`/`git show`；live code 全部 10 条逐条重读（含各失效面兄弟点抽查）
- **判定口径**: 三态（已保留/降级/驳回）；逐条核验量化链（调用频率 × 单次代价）；冷路径/测试专用代码排除；与本仓性能审计**既定校准**对齐——r6-perf:20 明文："与 Flink 参考实现同构的算法形态（如 `MergingWindowSet` 每记录重建……）不做'次优'指控，只对 nop-stream 在参考实现之上**额外引入**的结构性成本定罪"，且该排除经 r6 复核抽查确认（r6-perf:352）；以本文件第 2 轮自设的 P3 量化门槛（~100ns/记录，对照 ：307 对 50–80ns 项不立案、C9-008 以 100–200ns 达门槛）为底线校验

## 维度复核结论
> 注：原「逐条判定」节，2026-10-14 closure audit 要求标准化为本标题；内容未改动。

### [C9-001] 已保留（P2）
- live code 核实（`KeyedStreamImpl.java` copyBean/initField 现状与引文一致）：`copyBean` 每记录执行 `getDeclaredConstructor()` 重扫 + 逐 superclass `getDeclaredFields()` 重建 Field 数组 + 每字段 `setAccessible`，仅聚合目标 Field 有缓存——每记录重复解析本应一次解析的反射元数据，量化链（10^4–10^5 rec/s × 1–3µs + GC 放大）成立；非 Flink 同构形态（`sum(String)/min/max(String)` 为 nop DSL 自建便捷聚合），属"参考实现之上额外引入"的成本，P2 符合 CRA 校准"热路径可量化的分配浪费"。
- **现状标注**: 新发现。同位置历史发现为 2026-06-02 维度07-04（JPMS setAccessible + 就地突变面）；live code 的 copy-on-reduce 语义即 30f4ca7321（plan-2026-08-13-0132-2）为修复"就地突变"引入的产物，其反射元数据每记录重建成本此前从未立案。

### [C9-002] 驳回
- 结构与 Flink `WindowOperator` 参考实现逐行同构（每元素 `new MergingWindowSet` + `persist` 全量重写 + 匿名 `MergeFunction`，含 javadoc 措辞）；r6-perf 已**三次明示排除**同一定罪（:141 误报排除、:260 深挖复查#2、:320 汇总），且 r6 复核抽查确认"排除正确"（:352）；r5 `05-keyed-routing-window.md` 亦将该结构认证为"Flink 忠实移植"。
- 初审的核心参照前提失实：发现正文声称"Flink 同源结构按 key 缓存 `MergingWindowSet` 正是为消除此开销"，而 r6-perf:141 核实结论为"Flink **同样**每元素 new MergingWindowSet + persist"——不存在"已修正的参照形态"，本条实质是对既定校准已排除形态的重新开题。量化链形式上成立（且其中"每记录必走全量重写"的表述偏宽：新窗口被既有窗口完整吞并且 state window 不变时 mapping 未变、persist 跳过重写），但按本仓校准（只对参考实现之上额外引入的成本定罪）不构成发现。若未来决策放弃 merging window 的 Flink port 保真，可基于现有 SESSION bench 基线（`WindowOperatorProcessElementBench` 已含 SESSION 档）重新立项。
- **现状标注**: 已知且被既定校准排除（r6-perf :141/:260/:320/:352）。

### [C9-003] 已保留（P2）
- live code 核实（`CachingTableLookup.java` lookup/putEntry/evictOldestIfNeeded 与引文一致）：驱动驱逐扫描只发生在 miss 侧（`putEntry`），工作集基数 > `maxEntries` 时每条记录 1 次 O(maxEntries) 全表比较，发生在 WI11 声明的同步 per-record 路径上；`refreshOne` 写回直改 Entry 字段不触发驱逐（不命中面核实无误）。该类为 WI11 自建（52599aba5c，plan 2338），非平台缓存设施复用也非 Flink 同构——驱逐算法退化属"额外引入"的结构性成本，量化链（10^5 key 基数 × 10^3 maxEntries × 10^4 rec/s ≈ 10^7 比较/秒）成立。P2 维持。
- **现状标注**: 新发现。

### [C9-004] 已保留（P2）
- live code 核实（`SourceReaderOperator.run` 空闲分支 `Thread.yield()` 在位）：注释自称 "avoid busy-loop" 但 yield 只让步不阻塞，空闲 SOURCE 子任务持续烧满 1 核（占空比 100% 的量化成立）；与同模块 `InputGate` 空闲路径 `LockSupport.parkNanos(IDLE_PARK_NANOS=10ms)`（`InputGate.java:113,805`）策略不一致核实成立。9/10 月各轮审计（r5/r6 concurrency、2026-09-22 code-review、2026-10-06 adversarial）均未立过此案（CC-04/AR-04 仅涉 SourceCoordinatorRegistry 面）。
- **现状标注**: 新发现。

### [C9-005] 驳回
- 定量主体半边（每记录 `typeRegistry.getOutputTypeClassName(edgeId)` CHM.get）已被 r6-perf:249 明示排除："固定键，~10ns 量级，被 stringify 主导——排除"；该半边占初审估算（~0.1µs）的 20–50%。
- 残余半边（gating 启用时 `peekBacklogCached`+`bumpBacklogCached` 两次 synchronized 往返）仅在 DB-gated 边可达，无争用 monitor 往返 ~20–40ns，连同 CHM.get 合计落在本文件自设 P3 门槛（~100ns）之下（对照第 2 轮对 50–80ns 项不立案的先例）；锁面收窄（`this` → private 锁对象）属非量化卫生点。构造期缓存 valueType 本身无害，可随任意 `RemoteResultPartition` 后续改动顺带处理，不作为 tracked finding 保留。
- **现状标注**: 主体半边已知且被排除（r6-perf:249）；残余半边低于本轮自设门槛。

### [C9-006] 已保留（P3）
- live code 核实（`StreamElementCodec.encode` stringify payload → `AbstractStringWireCodec.toWire`/`SysDaoWireCodec.toWire` 再 stringify 整 map；decode 侧对称再 parse）：双重编码链完整在位。r1-perf:42 曾以 F 组 P0 合并立案（含 forName/startsWith 无缓存半边），plan 360 修复（c794cfce34）后编码半边未动；r6-perf PF-06 以同一形态独立立案 P3 并经复核维持（"未修复（Stage 40 Non-Goal，属 wire 格式演进候选）"，r6-perf:341）。本轮 P3 定级与 r6 终裁一致；本条对 `SysDaoWireCodec` 的同构命中面核实正确（r6-perf:262 同结论）。
- **现状标注**: 已知未修复（r1-perf F 组 P0 的编码半边；r6-perf PF-06 复核维持 P3）。

### [C9-007] 已保留（P3）
- live code 核实（`InMemoryMaterializationPoint` write/replay 同 monitor、replay 持锁 O(n) 全量拷贝；类 Javadoc 明文 "correctness-first implementation……deferred as an optimization candidate"）：按已声明边界降登记 P3（而非 P2）的处理正确，write 稳态成本可忽略、风险集中于恢复窗口生产者停摆的量化链成立。历史审计仅从正确性面处置过该类（r4 R5-CC-02 drain/快照缝隙竞态，已修复；r5 复核正面确认 "write/replay 全 synchronized，T3 快照是原子切面"，r5-perf:301）——正确性面闭合与性能面 P3 登记不冲突。
- **现状标注**: 新发现（性能面；正确性面历史发现 R5-CC-02 已修复）。

### [C9-008] 已保留（P3）
- live code 核实（`resolveValueType` 在 CHM 缓存查询**之前**每记录执行 `ClassNameValidator.validateClassName`，23×startsWith；类注释自证"每记录校验+forName"曾是修复目标）：缓存修复只覆盖 forName 半边、校验半边残留的定性成立；修复方向行为等价（未通过校验的名字本就不入缓存，后续命中 miss 路径仍会重新校验并抛出，失败语义不变）。r1-perf:42 将 "validateClassName+Class.forName 无缓存" 合并立案 P0，plan 360 修复提交 c794cfce34 仅落地 forName 缓存半边（`DECODED_VALUE_TYPES` + `StreamElementCodecRoundTripBench`）；r6-perf:263 将该位置正面确认为"热点优化已就位、同位置不需再改"，未单独分析校验半边——本条是对该正面确认的补充修正。量化（100–200ns/记录，10^5 rec/s 占派发线程 1–2%）达本文件 P3 门槛。
- **现状标注**: 已知未修复（残半）（r1-perf F 组 P0 校验半边；修复提交 c794cfce34 仅覆盖 forName 半边）。

### [C9-009] 已保留（P3）
- live code 核实（`registerActiveWindow:1905-1908` 每记录 `paneKey` 拼接 + `computeIfAbsent` + `new ActiveWindow` 探针分配 + 值比较丢弃；`windowNamespace` 单槽 memo 在位于 ：2550-2571，注释自证"rebuilt once per window switch instead of once per state operation"）：本条是同文件已承认并修复过的同型问题（字符串不变量每记录重派生）漏掉的第 2 处，属"参考实现之上额外引入"（WI8 自建 R-registry，bfc4c6eefa plan 2332，非 Flink 形态）；量化 0.12–0.24µs/记录达 P3 门槛。历史 paneKey 提及（2026-08 invariant-loop）为生命周期语义面，非性能面，无重复。
- **现状标注**: 新发现。

### [C9-010] 驳回
- 定量主体半边（`stateKey` 三段拼接 + `TimeWindow.toString()`，占自估总成本 160–280ns 的约 75%）与 r6-perf:174 的明示排除直接冲突——PF-04 误报排除："getSimpleAccumulator 每记录的键拼接（Flink 同构）……排除"，且 r6 复核进一步确认"被初审以 Flink 同构排除且未计入本条——**口径正确**"（r6-perf:339）。本条实质是对 r6 已裁定排除形态的重新开题。
- 扣除该半边后的残余（HashMap.get 30–50ns + Long 装箱 10–20ns ≈ 40–70ns）低于本文件自设 P3 门槛（~100ns），不满足独立立案的量化下限；对应基准缺口已由 r6-perf BG-9（:300）登记，本文件 BG-5 与之重复。若未来放弃 Flink 同构校准，可与 BG-9 合并处置。
- **现状标注**: 已知且被既定校准排除（r6-perf PF-04 误报排除 :174 + 复核确认 :339；基准缺口 r6 BG-9）。

## 基准缺口登记复核

- BG-1（TaskMailbox）、BG-2（CachingTableLookup）、BG-3（FieldAggregationReducer）、BG-4（SourceReaderOperator 空闲策略）：对照 `nop-benchmark/nop-benchmark-stream/src/main/java/io/nop/benchmark/stream/`（16 个基准类，含 plan 360 新增的 `StreamElementCodecRoundTripBench`、`SlidingWindowFanoutBench` 等）逐一确认无对应基准，缺口属实——**维持**。
- BG-5（有状态触发器路径）：与 r6-perf BG-9（:300，`getSimpleAccumulator` + `removeTriggerAccumulators` 基准缺口）**重复立项**——维持登记但处置时应与 r6 BG-9 合并，不重复实施。
- 附带更正：本文件第 1 轮"已有覆盖"称 `WindowOperatorProcessElementBench` 含 SESSION 模式——对当前 HEAD 核实为真（`@Param({TUMBLING, SLIDING, SESSION, EVICTOR})`），r6-perf BG-5 时点（"现 bench 仅 tumbling"）之后已补齐，引用无碍。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状标注 |
|---|---|---|---|---|
| C9-001 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/KeyedStreamImpl.java | FieldAggregationReducer 每记录全量反射拷贝 accumulator（构造器/Field 数组每记录重解析，仅聚合目标 Field 有缓存） | 新发现 |
| C9-003 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/lookup/CachingTableLookup.java | 驱逐路径在同步 per-record lookup 热路径上做 O(maxEntries) 全表扫描（工作集超缓存时每 miss 一次） | 新发现 |
| C9-004 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/SourceReaderOperator.java | FLIP-27 源循环空闲时 Thread.yield() 纯自旋烧核（与 InputGate parkNanos 形态不一致，空闲 SOURCE 子任务占满 1 核） | 新发现 |
| C9-006 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/transport/StreamElementCodec.java + nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/AbstractStringWireCodec.java | 字符串 wire 后端对同一记录值做双重 JSON 编码/解码（payload stringify 后整 envelope 再 stringify，接收侧对称双 parse） | 已知未修复（r1-perf F 组 P0 编码半边；r6-perf PF-06 复核维持 P3，Stage 40 Non-Goal） |
| C9-007 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/materialization/InMemoryMaterializationPoint.java | 物化边每记录同步 write 与恢复 replay（持锁 O(n) 拷贝）互斥，恢复窗口生产者整体停顿；类 Javadoc 已声明 correctness-first 边界 | 新发现（正确性面历史发现 R5-CC-02 已修复，性能面为本轮新登记） |
| C9-008 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/transport/StreamElementCodec.java | 解码路径每记录重跑 23 前缀 startsWith 类名白名单校验（DECODED_VALUE_TYPES 缓存只覆盖 Class.forName 半边） | 已知未修复（残半）（r1-perf F 组 P0 校验半边；修复提交 c794cfce34 仅覆盖 forName 半边） |
| C9-009 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/windowing/WindowOperator.java | active-window registry 每记录重建 paneKey 复合字符串+分配探针对象（windowNamespace 已有单槽 memo 先例，此为漏修同型兄弟） | 新发现 |

## 复核统计

- **三态判定**: 保留 6（P2×3：C9-001/003/004；P3×3：C9-006/007/008）；降级 0（保留项严重程度均与初审定级一致）；驳回 3（C9-002、C9-005、C9-010）。
- **现状标注统计**（按保留 6 条）: 新发现 4（C9-001、C9-003、C9-004、C9-007）；已知未修复 2（C9-006 = r1-perf F-P0 残余/r6 PF-06 维持；C9-008 = r1-perf F-P0 校验残半，部分修复提交 c794cfce34）。无"已修复"保留项（C9-002/005/010 驳回项的既有排除/修复归属已在逐条判定中注明：c794cfce34 修复 forName 半边、30f4ca7321 修复就地突变半边）。
- **基准缺口**: BG-1..4 维持（缺口核实属实）；BG-5 与 r6-perf BG-9 重复，处置时合并。
- **对初审/深挖的质量备注**: 10 条引文与行号逐点核对全部为真，失效面枚举（refreshOne 不触发驱逐、MailboxExecutor.runLoop 测试可达、OverWindowOperator 仅测试可达等）抽查无虚引；驳回 3 条均非事实错误而是**校准归属问题**（2 条重开 r6 已裁定排除的 Flink 同构形态，1 条主体半边已被 r6 明示排除且残余低于本文件自设门槛）。
