# nop-stream 性能候选与基准缺口深度审计（R4 轮 · 07-performance）

- 审计日期：2026-09-30
- 审计范围：`nop-stream/` 全模块 + `nop-benchmark/nop-benchmark-stream/`（静态审查，未运行任何基准）
- 前置收敛裁定（维持有效）：plan 360/2279/366 已多轮收敛，2026-09-29 裁定"触碰路径无 ≥2% 低风险可收割项"。
- 审计输入：`git log --since=2026-09-20 -- nop-stream`（plan 366 Phase 2/3/4 + plan 01）涉及的 60+ 文件，重点为每记录/每水位/每 checkpoint 热路径。
- 明确排除（已 Deferred，勿重复立项）：file sink invoke 每记录 `toString()` 物化（aliasing 契约）、JDBC sink invoke 每记录 Map 拷贝（门控风险）、双槽 storage-key 缓存（实测无收益已 revert）。本轮核查确认：MessageSinkFunction.invoke 直发不拷贝、BatchConsumerSinkFunction 为批冲刷形态，**deferred 同名模式未在其他 sink 出现范围扩大**。

## 发现总览

| 编号 | 严重程度 | 标题 | 热路径频次 |
|------|---------|------|-----------|
| R5-PF-01 | **P2** | FileSourceReader 逐行 BAoS 分配 + 逐行 FileSplit 拷贝（plan 366 已登记候选的精确定位） | 每记录 |
| R5-PF-02 | **P2** | TaskDispatchLoop 每记录无条件时钟/计时开销（markActivity + markProgress + 2×nanoTime） | 每记录 |
| R5-PF-03 | P3 | FileSourceReader.pollNext 监视器跨磁盘读持有（锁内 I/O） | 每记录持锁 |
| R5-PF-04 | P3 | ResultPartition 回放耗尽后每记录空 CLQ.poll 永久残留 | 每记录 |
| R5-PF-05 | P3 | NFA 每事件分配 java.util.Stack/HashSet + doProcess 临时容器 | 每事件 × 部分匹配 |
| R5-PF-06 | P3 | SharedBufferAccessor.put 每条 TAKE 边执行 String.split | 每事件 × TAKE 边 |
| R5-PF-07 | P3 | WindowOperator 合并窗口路径每记录分配匿名 MergeFunction | 每记录（session 窗口） |
| R5-PF-08 | P3 | InputGate 读路径每记录 Optional 装载 | 每记录 |
| R5-PF-09 | P3 | RemoteInputChannel 每次读无条件心跳时钟读 | 每记录（远程边） |
| R5-PF-10 | P3 | TypedNamespaceAndKey 单槽缓存在 namespace 交替下失效（观察项） | 每状态访问 |
| R5-PF-11 | P3 | 微候选束：RemoteResultPartition 每记录类型查表等 3 项 | 每记录/每清扫 |

**分布：P0 × 0，P1 × 0，P2 × 2，P3 × 9，合计 11 项。** 无热路径 O(n²)、无无界分配达到 P1 档。

---

### [R5-PF-01] FileSourceReader 逐行 BAoS 分配 + 逐行 FileSplit 拷贝

- **文件**：`nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSourceReader.java`
- **严重程度**：P2
- **证据片段**（行 236-246，readNextLine 每行新建 ByteArrayOutputStream）：
  ```java
  private String readNextLine() throws IOException {
      long cap = activeSplit.getEndOffset() - activeSplit.getStartOffset();
      ByteArrayOutputStream out = new ByteArrayOutputStream();   // ← 每行一次分配
      while (activeBytesConsumed < cap) {
          int b = readBuffered();
          if (b == -1) { break; }
          activeBytesConsumed++;
          if (b == '\n') {
              return out.toString(StandardCharsets.UTF_8);       // ← 二次拷贝（内部数组→String）
          }
  ```
- **证据片段**（行 151-152，pollNext 每行物化一个新 FileSplit）：
  ```java
  long newOffset = activeSplit.getStartOffset() + activeBytesConsumed;
  activeSplit = activeSplit.withCurrentOffset(newOffset);        // ← 每行一个不可变 FileSplit 拷贝
  ```
- **现状**：plan 366 Phase 4 文件源缓冲化（-33% 保留）消除了逐字节同步流读，但保留了 per-line 的 BAoS 分配、逐字节 `out.write(b)` 累积循环与 `toString` 二次拷贝；`withCurrentOffset` 每行物化不可变 FileSplit 是缓冲化引入 AR-15-② 游标契约时的伴生成本。plan 366 Non-Blocking Follow-ups 已登记"file source 行累积重构候选"（JFR 归因：残余 97% 为 BAOS 逐字节累积 + UTF-8 解码基本成本）——**本发现是该已登记候选的精确化**，非重复立项：新增 `withCurrentOffset` 逐行拷贝这一独立可消除项。
- **风险**：中。行跨 chunk 边界 / CR 边界语义重构属 plan 366 已标注的中风险项；`withCurrentOffset` 去除需保证 `snapshotState`（持同一监视器）改为从 `activeBytesConsumed` 现算游标，AR-15-② 不撕裂游标契约必须在同一临界区内成立。
- **建议**：值得进 JMH 验证——口径已存在（`ConnectorInvokeBench.fileSourceReadLine`，plan 366 Phase 1）。两个子项可分档收割：(a) 低风险档——`activeSplit` 游标惰性化（snapshot 时现算），行为保持；(b) 中风险档——可增长 byte[] 复用 + 单次 `new String(bytes, UTF_8)`，需独立设计（与 plan 366 登记一致）。
- **信心水平**：高（分配模式确证；收益档位依 plan 366 JFR 归因推算）。
- **误报排除**：`readBuffered()` 返回 `int` 无装箱；`reportFinishedToCoordinator` 的 ArrayList 分配仅每 split 一次，非热路径。

---

### [R5-PF-02] TaskDispatchLoop 每记录无条件时钟/计时开销

- **文件**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/StreamTaskInvokable.java`
- **严重程度**：P2
- **证据片段**（行 919-965，processInputGate 主循环）：
  ```java
  while (true) {
      markActivity();                                    // ← 每循环 System.currentTimeMillis（含每记录迭代）
      if (mailboxExecutor.processAvailableMails()) { ... }
      Optional<StreamElement> elementOpt = inputGate.read();
      ...
      markProgress();                                    // ← 每记录再一次 System.currentTimeMillis
      classifyAndDispatch(headInput, elementOpt.get());
  ```
- **证据片段**（行 976-985，classifyAndDispatch）：
  ```java
  if (element.isRecord()) {
      taskMetrics.recordsIn(1);
      long metricsStart = CoreMetrics.nanoTime();        // ← 每记录 nanoTime #1（NOOP 时也执行）
      try {
          headInput.processElement(...);
      } finally {
          taskMetrics.processingTime(CoreMetrics.nanoTime() - metricsStart);  // ← nanoTime #2
      }
  ```
  同型：`RecordWriterOutput.collect`（行 1081-1088）每发射记录 2×nanoTime + 2 次 metrics 调用。`CoreMetrics` 为虚分派直通 `System.nanoTime()`/`System.currentTimeMillis()`（`nop-kernel/nop-api-core/.../CoreMetrics.java` 行 23-28）。
- **现状**：每记录合计约 2×currentTimeMillis（markActivity + markProgress）+ 2×nanoTime（链处理计时）+ 2×nanoTime（发射计时，MIDDLE 角色）≈ 4 次系统时钟调用 + 多次 volatile 写。轻链（算子亚微秒级）下这是 TaskDispatchLoop 中间层的主要固定开销之一；`TaskMetricsHandle` 委托为 NOOP 时计时纯属浪费（仅测试/local 形态；生产两条路径均注入真实实现，故 NOOP 门控对生产无收益）。
- **风险**：低（行为保持档）。可收割子项：(a) `markActivity`/`markProgress` 合并为单次时钟读（两个 volatile 时间戳同值刷新，liveness 语义均保持"单调不降、持续刷新"）；(b) NOOP 门控仅优化测试路径。**不可**删除 per-record 计时——`processingTime`/`emitTime` 是契约指标（backpressure 代理）。
- **建议**：值得进 JMH——但**前提是先补 TaskDispatchLoop 中间层隔离口径**（见基准缺口结论：本层目前无隔离基准，InputGateReadLoopBench 止步于 gate 读环，WindowOperatorProcessElementBench 起步于 operator.processElement，中间层自身开销无数字）。先建口径、再实测 (a) 是否 ≥2%。
- **信心水平**：中高（开销确证；收益档位待测，估计 1-4%，随算子链轻重浮动）。
- **误报排除**：`markProgress`/`markActivity` 的 volatile 写本身非问题（心跳契约要求）；`taskMetrics.recordsIn(1)` 在 NOOP 下为虚调用，成本可忽略，非候选。

---

### [R5-PF-03] FileSourceReader.pollNext 监视器跨磁盘读持有

- **文件**：`nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSourceReader.java`
- **严重程度**：P3
- **证据片段**（行 127-158）：
  ```java
  synchronized (this) {
      if (activeReader == null) { ... openSplit(next); }   // ← 文件打开（I/O）在锁内
      line = readNextLine();                               // ← 8KB bulk 磁盘读在锁内
      if (line != null && activeSplit != null) {
          long newOffset = activeSplit.getStartOffset() + activeBytesConsumed;
          activeSplit = activeSplit.withCurrentOffset(newOffset);
      }
  }
  ```
- **现状**：每行读取全程持 `this` 监视器，而 `snapshotState`（checkpoint 线程，行 323-333）与 `isFinished()`（行 104-123）争同一锁。checkpoint 触发瞬间，快照线程可能等待一次 bulk 读（≤8KB 读 + 行解析）。无 checkpoint 时单线程无争用，锁开销可忽略。
- **风险**：中。AR-15-② 明确要求游标推进与 snapshotState 同监视器（防撕裂游标）；缩小临界区需证明"读在锁外、游标提交在锁内"仍满足不撕裂契约——本质是把 bulk read 移出临界区、仅游标写留在锁内，可行但属并发契约变更。
- **建议**：暂不立项。争用窗口 = checkpoint 时刻单次 bulk 读，对吞吐无稳态影响；仅当引入"checkpoint 线程高频 snapshot"特性时再评估。不值得单独进 JMH。
- **信心水平**：高（结构确证）；收益信心低（稳态近零）。
- **误报排除**：非"每记录锁争用"问题——生产读路径单任务线程，锁仅与 checkpoint 线程短暂争用。

---

### [R5-PF-04] ResultPartition 回放耗尽后每记录空 CLQ.poll 永久残留

- **文件**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java`
- **严重程度**：P3
- **证据片段**（行 106-118 + 行 368-372）：
  ```java
  private StreamElement pollPendingReplay() {
      java.util.concurrent.ConcurrentLinkedQueue<StreamElement> pending = this.pendingReplay;
      if (pending == null) { return null; }
      StreamElement element = pending.poll();   // ← 耗尽后仍然每次 read 到达这里
      ...
  }
  public StreamElement read() throws InterruptedException {
      StreamElement pending = pollPendingReplay();   // ← 每记录（行 369/398 两个读过载）
  ```
- **现状**：字段故意不置 null（行 98-105 注释：防并发 attach 被 orphan）。未曾 attach 的边（绝大多数）只付一次 volatile 读——零成本；但**经历过一次 region 重启回放的边**，此后每条记录永久多付一次空 `ConcurrentLinkedQueue.poll()`（head/item/next 约 3 次 volatile 读 + 偶发 CAS）。
- **风险**：中高（若尝试置 null/fast-path）。文档已载明置 null 的 orphan 竞态；安全的"耗尽快速路径"需要 attach 端以 volatile 序（先写 queue、后清标志）配对，且消费端顺序相反——可设计但易错。
- **建议**：不立项。成本约 2-5ns/记录且仅限回放过的边，远低于 2% 收敛线；强行优化引入的并发风险不对称。记录在案防止未来误判为回归。
- **信心水平**：高（成本模型确证）。
- **误报排除**：`drainBufferedElements` 的 poll 循环仅在 checkpoint 捕获路径执行，非热路径。

---

### [R5-PF-05] NFA 每事件 Stack/HashSet 与 doProcess 临时容器分配

- **文件**：`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java`
- **严重程度**：P3
- **证据片段**（行 940-942，findFinalStateAfterProceed，每 TAKE 边调用）：
  ```java
  final Stack<State<T>> statesToCheck = new Stack<>();   // ← java.util.Stack（Vector 同步方法）
  statesToCheck.push(state);
  final Set<State<T>> visited = new HashSet<>();
  ```
  行 981-983（createDecisionGraph，每 (事件 × 部分匹配) 一次）：`final Stack<State<T>> states = new Stack<>(); final Set<State<T>> visited = new HashSet<>();`
  行 411-429（doProcess，每事件）：两个 `new PriorityQueue<>(...)` + 每部分匹配 `new ArrayList<>()`（statesToRetain）+ 行 423 `newComputationStates.iterator().next()`（size==1 判等仍分配 iterator）。
- **现状**：浅模式（单状态、无边分支）下每事件固定 2×Stack + 2×HashSet + 2×PriorityQueue + n×ArrayList 分配；`Stack` 继承 Vector，push/pop 为 synchronized 方法（无争用 monitor 成本 + 抑制逃逸分析栈上分配）。这是 Flink 血统代码的同构问题。
- **风险**：低（ArrayDeque 直换为行为保持；PriorityQueue/ArrayList 复用为字段则引入跨事件状态保留，需谨慎——NFA 是 per-key 对象，字段化 scratch 会按 key 数放大内存，且 PriorityQueue 复用需 clear 语义校验）。
- **建议**：值得低成本验证——仅做 `Stack→ArrayDeque` + 容量预置（`new ArrayDeque<>(4)`、`new HashSet<>(4)`），在既有 `NfaProcessBench`（patternDepth=1/5/20 档）上实测；深模式档（每事件多部分匹配）预期可见个位数百分比。PriorityQueue/ArrayList 字段化不建议跟进。
- **信心水平**：高（分配确证）；收益信心中（浅模式下绝对值小）。
- **误报排除**：`LazyVerdictCache`（行 1039-1052）已是惰性分配（E3 记忆化成果），非问题；`ConditionContext.getEventsForPattern` 的匿名 Iterable（行 1132-1140）仅在迭代式条件求值时分配，浅模式不可达，并入本项观察不单列。

---

### [R5-PF-06] SharedBufferAccessor.put 每条 TAKE 边执行 String.split

- **文件**：`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferAccessor.java`（行 118）+ `nop-stream-cep/.../nfa/compiler/NFAStateNameHandler.java`
- **严重程度**：P3
- **证据片段**（SharedBufferAccessor.put，每次挂边调用）：
  ```java
  NodeId currentNodeId = new NodeId(eventId, NFAStateNameHandler.getOriginalNameFromInternal(stateName));
  ```
  （NFAStateNameHandler）：
  ```java
  public static String getOriginalNameFromInternal(String internalName) {
      Guard.notNull(internalName, "internalName");
      return internalName.split(STATE_NAME_DELIM)[0];   // ← 每次：分隔扫描 + String[] + substring 分配
  }
  ```
- **现状**：每事件每条 TAKE 边都把内部状态名（`base:counter`）重解码为原始名。状态名集合在编译期即封闭且极小（= 模式状态数），却每条边重算。
- **风险**：低。三选一：静态 `ConcurrentHashMap<String,String>` 记忆化（names 集合小，无界风险可忽略，仍建议容量上限意识）；或在 `State`/编译期预存原始名并让 put 直接传入；或 `indexOf(':')` + `substring` 免数组分配。语义均为行为保持（同一输入同一名）。
- **建议**：值得验证——`SharedBufferRegisterBench.registerEventAndPut` 每调用 3 次 put，直接覆盖该路径；配合 `NfaProcessBench` 看端到端份额。预估单次 30-80ns，CEP 浅模式端到端占比 1-3%。
- **信心水平**：高（代码路径确证）；收益信心中。
- **误报排除**：`Guard.notNull` 非热点（引用判空）；`new NodeId` 本身是路径必需分配，非候选。

---

### [R5-PF-07] WindowOperator 合并窗口路径每记录分配匿名 MergeFunction

- **文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`
- **严重程度**：P3
- **证据片段**（行 688-692，processElementForMergingWindow，每元素每窗口）：
  ```java
  for (W window : elementWindows) {
      W actualWindow =
              mergingWindows.addWindow(
                      window,
                      new MergingWindowSet.MergeFunction<W>() {   // ← 每记录分配，捕获 key/element 上下文
                          @Override
                          public void merge(W mergeResult, Collection<W> mergedWindows, ...) {
  ```
- **现状**：session/合并窗口 assigner 下每条记录（每候选窗口）构造一个捕获 `key` 的匿名 MergeFunction；多数记录实际不发生 merge，对象即弃。同路径 `getMergingWindowSet()`（行 1252-1257）每记录 `new MergingWindowSet<>(...)`。
- **风险**：低。字段化单个可复用 MergeFunction + `triggerContext.key` 同款可变 key 槽位（类内已有此模式，行 719-720）；`getMergingWindowSet` 字段化需校验 MergingWindowSet 的 per-key 状态（它包装 mergingSetsState，按当前 key 读写——复用前须确认无 per-instance 遗留状态）。
- **建议**：值得验证——`WindowOperatorProcessElementBench` SESSION 档即口径（SESSION 走合并路径），一次实测即可裁决。预估 session 档 1-3%，tumbling/sliding 档零影响。
- **信心水平**：高（分配确证）；收益信心中。
- **误报排除**：`processElementForRegularWindow`（行 801-814）无每记录分配；`isElementLate`/`isWindowLate` 为纯算术比较，非候选。

---

### [R5-PF-08] InputGate 读路径每记录 Optional 装载

- **文件**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java`
- **严重程度**：P3
- **证据片段**（行 637 readSingleChannel / 行 817 dispatchChannelElement）：
  ```java
  return Optional.of(element);        // ← 每条数据记录一个 Optional 分配（两个读路径同型）
  ```
  行 715：`if (barrierAlignment && blockedChannels.contains(channelIndex))` —— `blockedChannels` 为 `Set<Integer>`（行 179），每通道每轮扫描一次 int 装箱查表（≤127 命中 Integer 缓存，无分配）。
- **现状**：每条数据记录经 gate 返回时携带一个 16B Optional；`Optional.empty()` 为单例无成本。对齐稳态下 `blockedChannels` 为空集，contains 为廉价哈希探测。
- **风险**：中。`InputGate.read()` 返回 `Optional<StreamElement>` 是公共面形状，改哨兵/可变字段是签名级变更，牵动 `processInputGate`/`readSingleChannel`/`readMultiChannel` 三层调用。
- **建议**：不单独立项。与 R5-PF-02 合并考虑：若 TaskDispatchLoopBench 落地且显示 gate 层份额显著，再一并裁决（Optional 去除 + 时钟合并一次改）。单独收益 <1%，不符收割线。
- **信心水平**：高（分配确证）；收益信心低。
- **误报排除**：`checkAlignmentElapsed`/`emitPendingBarriers` 返回的 `Optional.empty()` 是静态单例，零分配；`emitCompletedAlignment`/`handleBarrier*` 仅 barrier 频次执行。

---

### [R5-PF-09] RemoteInputChannel 每次读无条件心跳时钟读

- **文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java`
- **严重程度**：P3
- **证据片段**（行 302-307，read(long, TimeUnit)；行 336-349）：
  ```java
  public StreamElement read(long timeout, TimeUnit unit) throws InterruptedException {
      checkReadable();
      checkChannelError();
      checkChannelTimeout();          // ← 每次读（含每条记录）执行
      StreamElement element = queue.poll(timeout, unit);
  ...
  public boolean isChannelTimedOut() {
      if (channelTimeoutMs <= 0 || finished || decodeError != null) { return false; }
      return (CoreMetrics.currentTimeMillis() - lastReceivedTime) > channelTimeoutMs;   // ← 每读一次时钟
  ```
- **现状**：心跳检测启用（channelTimeoutMs>0）的远程边，每条记录读前做一次 `System.currentTimeMillis()` + 2 次 volatile 读。数据流动时 `lastReceivedTime` 必然新鲜，检查不可能触发；检查只在空闲轮询（null 返回，50ms 周期）与**积压排水**场景有判别力。
- **风险**：中（若改为"仅 null 轮询时检查"）：生产者死亡但本地队列仍有积压时，排水期间的检测会推迟到积压排干，检测延迟语义变宽。更安全的收窄是时间阈值节流（距上次检查 < channelTimeoutMs/4 则跳过），语义近似保持。
- **建议**：不单独立项。远程边每记录成本由 `StreamElementCodec` JSON 编解码（µs 级，`RemoteTransportWriteBench`/`StreamElementCodecRoundTripBench` 已有口径）支配，本项 ~20ns/记录占比 <1%。仅当未来做远程边整链降耗批次时搭车处理。
- **信心水平**：高（路径确证）；收益信心低。
- **误报排除**：`EnvelopeConsumer.onMessage` 内 `lastReceivedTime = CoreMetrics.currentTimeMillis()`（行 641）是心跳契约的写入点，必须保留；行 646 `LOG.debug("Received heartbeat...")` 为单参数 slf4j 重载，禁用时不产生 varargs 分配，非候选。

---

### [R5-PF-10] TypedNamespaceAndKey 单槽缓存在 namespace 交替下失效（观察项）

- **文件**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java`
- **严重程度**：P3（测量优先的观察项）
- **证据片段**（行 445-456）：
  ```java
  TypedNamespaceAndKey cachedNamespaceAndKey(Object namespace, Object key) {
      if (cachedNamespaceAndKey != null
              && java.util.Objects.equals(key, cachedKey)
              && java.util.Objects.equals(namespace, cachedNamespace)) {
          return cachedNamespaceAndKey;                      // ← 单槽：(namespace, key) 任一变化即重建
      }
      TypedNamespaceAndKey built = new TypedNamespaceAndKey(namespace, routeKey(key));  // ← miss 时分配 + routeKey
  ```
- **现状**：plan 366 Phase 4 P4 保留的单槽惰性分配缓存。失效形态：同一记录处理中两个状态以**不同 namespace** 交替访问（如窗口内容 state 的 namespace=窗口 vs 另一 state 的 namespace=全局）时，槽位每访问抖动一次，退化为每访问分配 + `routeKey`（`ShardPrefixedKey` 装箱包装，maxParallelism>1 时）。当前算子实现（WindowOperator/CepOperator）同一 key 内 namespace 单一，不触发抖动。
- **风险**：若扩展为 2-4 槽，行为保持（不可变值，注释已载明安全论证），但属于对实测无收益方向的再投入风险（双槽 storage-key 缓存已因无收益 revert——**注意那是不同的缓存**，本项是 (namespace,key) 键构造缓存）。
- **建议**：不立项；仅登记**基准负载形态缺口**（见第五节）：`MemoryKeyedStateBench` 的 local/rotate 覆盖 key 维度，无 namespace 交替档。若未来出现多 namespace 交替的新算子，先补该档再评估。
- **信心水平**：中（抖动模式从代码推演，未实测；当前无触发算子）。
- **误报排除**：`verifySchemaCompatibility`/`applyTtl` 每次走 `getState` 才执行（open 期一次），非每记录路径，非候选。

---

### [R5-PF-11] 微候选束（3 项，均单独低于收割线）

- **严重程度**：P3（捆绑登记，防丢失）
- **子项 1 — RemoteResultPartition.write 每记录类型查表**（`nop-stream-runtime/.../transport/RemoteResultPartition.java` 行 127）：
  ```java
  String valueType = typeRegistry != null ? typeRegistry.getOutputTypeClassName(edgeId) : null;
  ```
  每条远程记录一次 Map 查表；edgeId 恒定，构造期缓存进 final 字段可消除。风险：注册晚于构造则缓存失效（当前注册发生在 plan 构建期，早于数据面）——需断言注册时序后才可改。收益 <1%（JSON 编码支配）。**建议**：随远程边批次搭车，不单独立项。
- **子项 2 — TtlContext.expiredKeys 防御性整表拷贝**（`nop-stream-core/.../common/state/TtlContext.java` 行 168）：
  ```java
  for (Map.Entry<K, Long> e : new HashMap<>(timestamps).entrySet()) {
  ```
  每次清扫 O(n) 拷贝 + 装箱；清扫频率低（后台 sweep），非每记录。**建议**：仅当 sweep 周期缩短特性立项时一并改（直接迭代 + 收集待删集合即可），现在不动。
- **子项 3 — NFA.doProcess size==1 判等经 iterator**（`NFA.java` 行 423）：`!newComputationStates.iterator().next().equals(computationState)` 为 size==1 集合分配 iterator；换 `List.get(0)` 形态需 `computeNextStates` 返回类型变更（现返回 `Collection`）。收益 ~1ns/事件，**建议**：随 R5-PF-05 的 ArrayDeque 批次顺手处理，不独立验收。

---

## 基准缺口复核（上轮登记 5 项 vs `nop-benchmark-stream` 现状）

| # | 上轮登记缺口 | 现状 | 证据 |
|---|-------------|------|------|
| 1 | connector 数据面（file source 行读、2PC sink invoke） | **已闭合** | `ConnectorInvokeBench`（plan 366 Phase 1 新建）：`fileSourceReadLine` / `fileSinkInvokeMap` / `jdbcSinkInvokeMap` 三口径，@Param 负载化（行 174-207） |
| 2 | TaskDispatchLoop 中间层隔离口径 | **仍无** | 现有 14 个基准覆盖 gate 读环（InputGateReadLoopBench：producer/consumer 两端）、operator.processElement（WindowOperatorProcessElementBench）、timer driver（ProcessingTimeDriverLatencyBench），但 `StreamTaskInvokable.processInputGate`→`classifyAndDispatch` 层（mailbox drain + markActivity/Progress + 2×nanoTime + Optional 解包 + 分派）无隔离口径。R5-PF-02 的裁决被此缺口阻塞 |
| 3 | 嵌入态 checkpoint 循环 | **仍无** | `CheckpointSerDeBench` 仅覆盖 EpochManifest serDe 三口径（serialize/deserialize/deserializeNoChecksum，行 128-150）；`GraphModelCheckpointExecutor` 的 trigger→snapshot→ack 嵌入循环无口径 |
| 4 | extractPatterns | **部分收敛（无隔离口径）** | `NfaProcessBench.processEvent` 端到端包含 extractPatterns（followedBy+within 模式下匹配必然完成、processMatchesAccordingToSkipStrategy 每次完成匹配调用 extractPatterns），但无隔离口径；`SharedBufferRegisterBench` 仅覆盖 register+put，不触 extract。plan 366 已登记为 Deferred（判定控制面/低频口径意义有限）——维持登记，建议口径形态：`SharedBufferExtractBench`（预置 N 级版本链后每 invocation extract 一次） |
| 5 | Memory shard 路由 | **仍无** | `MemoryKeyedStateBench.setup` 用 `new MemoryStateBackend().createKeyedStateBackend(Long.class)` 单参构造 → `maxParallelism=1` → `MemoryKeyedStateBackend.routeKey` 直通返回原 key（行 458-461），`KeyGroupAssignment.assignToKeyGroup` + `ShardPrefixedKey` 包装路径零覆盖。口径形态建议：bench 增加 `@Param maxParallelism {1, 64}` |

**本轮新增缺口（1 项）**：
- **namespace 交替访问档**：`MemoryKeyedStateBench` 的 accessPattern（local/rotate）只覆盖 key 维度，无"同 key 双状态不同 namespace 交替"档——R5-PF-10 单槽缓存抖动的唯一可观测口径（关联 Deferred 项，非阻塞）。

**plan 366 新改动是否制造新的无口径热路径**：是——R5-PF-02 所在的 dispatch 层在 plan 366 Phase 3 重构中被触碰（markActivity 逐迭代打点即本轮引入的语义），且该层无隔离口径；这是"新改动 + 既有缺口"的叠加，优先级应高于其余 Deferred 缺口。

---

## 与收敛裁定的关系

- 收敛裁定（2026-09-29）"触碰路径无 ≥2% 低风险可收割项"**维持有效**：本轮 11 项中，9 项 P3 低于收割线或不建议立即动；2 项 P2 中，R5-PF-01 是 plan 366 已登记候选的精确化（中风险档需独立设计），R5-PF-02 被 TaskDispatchLoop 口径缺口阻塞、行为保持档收益待实测。
- 本轮未发现热路径 O(n²) 或无界分配（唯一 O(n) 每记录形态是 InputGate 多通道整轮扫描与 NFA 部分匹配遍历，均为算法内在复杂度，非缺陷）。
- 明确排除的 Deferred 同名项均未发现范围扩大：file sink toString、JDBC Map 拷贝、双槽 storage-key 缓存，及 MessageSink/BatchSink 无同模式。

## 审计方法附注

- 静态审查基于 live code（working tree，HEAD=0e67dba845）；所有行号以当前工作区为准。
- 未运行 JMH/基准/测试（任务约束）；所有收益档位均为静态推算或引用 plan 366 已留档实测。
- 交叉引用：`ai-dev/plans/366-nop-stream-audit-r4-quality-perf.md`（Deferred 清单与收敛裁定）、`ai-dev/audits/2026-09/2026-09-29-0546-deep-audit-nop-stream-quality-r3/`（上轮基准缺口登记）。
