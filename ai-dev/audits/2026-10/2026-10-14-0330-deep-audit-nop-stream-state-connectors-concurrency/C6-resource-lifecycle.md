# C6 资源生命周期与泄漏（初审）— nop-stream-state-connectors

- **审计维度**: C6（资源生命周期与泄漏），对照缺陷族 F8（资源清理不对称）
- **审计对象**: 8 个模块 `src/main/java`，140 files / 25,458 lines
  - `nop-stream/nop-stream-rocksdb`（19 files，重点：JNI 句柄配对）
  - `nop-stream/nop-stream-connector`（19 files）、`-batch`（9）、`-jdbc`（4）、`-debezium`（8）、`-kafka`（4）
  - `nop-stream/nop-stream-cep`（74 files）、`nop-stream/nop-stream-format-avro`（3）
- **盲审纪律**: 未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；只审计当前 live code。`_` 前缀生成文件（如 `nfa/model/_gen/*`）不作为发现对象。
- **依据文档**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + C6 正文）、`docs-for-ai/03-modules/nop-stream.md`、`docs-for-ai/03-modules/nop-stream-connectors.md`、基线 `_tmp/cra-baseline-nop-stream-state-connectors.txt`（C6 acquire 节——基线 grep 只命中 rateLimiter.acquire 两处，覆盖面不足；本轮以全量资源类型盘点替代）。
- **日期**: 2026-10-14；**复核状态**: 全部条目=未复核（初审）

## 检查范围与方法

1. 全量盘点 8 模块的资源获取点：native 句柄（RocksDB/ColumnFamilyHandle/DBOptions/ColumnFamilyOptions/Cache/WriteBufferManager/BloomFilter/Checkpoint/RocksIterator）、Kafka consumer/producer、JDBC Connection/PreparedStatement、Debezium 引擎宿主（source/subscription/failure-listener/file-history）、自建线程（message-source-reader / debezium-source-reader）、文件流（FileInputStream/FileChannel/FileLock/BufferedWriter）、临时文件（`.epoch-N.tmp`、`cp-N` 目录、reconstructed 恢复目录）、JDK HttpClient、CEP SharedBuffer 引用计数锁（Lockable lock/release）、静态注册表（MANIFEST_JVM_LOCKS、DebeziumEngineWrapper failure-listener、NopStreamOffsetBackingStore）。
2. 对每个获取点核对配对释放：try-with-resources / finally / close 钩子；异常路径（获取后抛错）、取消路径（future cancel / close / interrupt）、超时路径逐一核对。
3. 框架生命周期钩子核实（非猜测）：`StreamFunction` 不继承 `RichFunction`；`AbstractUdfStreamOperator.close()` 只对 `RichFunction` 调 `FunctionUtils.closeFunction`；`StreamSinkOperator.close()` 只对 `AutoCloseable` userFunction 调 close——两个分支对普通 `SinkFunction`/`TwoPhaseCommitSinkFunction` 子类均为 no-op，这是判定 C6-001 的框架事实基础。
4. 基线补充：基线 C6 acquire 节仅 2 条 rateLimiter.acquire；本轮实际盘点出 20+ 类获取点（见配对表），基线仅用于圈定、未用于计数结论。

## 资源获取点配对表（获取点 × 释放路径 × 异常/取消/超时路径判定）

| # | 资源 | 获取点（file:line） | 正常释放 | 异常/取消/超时路径 | 判定 |
|---|---|---|---|---|---|
| 1 | RocksDB 实例 + DBOptions/CFOptions + Cache/WBM/BloomFilter | RocksDBKeyedStateBackend.openDB:211-280 | close():979-1067（顺序：metrics→CF handles→defaultCF→db→WBM→cache→filter→options，失败收集 suppressed） | 构造器抛错路径无清理 → C6-005 | **P2** |
| 2 | ColumnFamilyHandle（含 restore 重建的 src 句柄） | openDB:257-270 / getOrCreateColumnFamily:339-354 | close():991-1002（handles 先于 db，顺序正确） | open 部分失败句柄泄漏（并入 C6-005） | P2（并入） |
| 3 | RocksIterator | AbstractRocksDBState:73、RocksDBListState:62、RocksDBMapState:61/220/269、RocksDBSnapshotSerDe:209/311/448、RocksDBKeyedStateBackend:387/748 | 全部 try-with-resources | TWR 覆盖异常路径 | ✓ 配对 |
| 4 | Checkpoint（native） | RocksDBIncrementalSnapshotStrategy.doSnapshot:75 | try-with-resources | ✓ | ✓ |
| 5 | restore 重建的 src RocksDB | RocksDBIncrementalRestore.restoreRangeInto:209 | TWR + finally 关 handles | **db 先关、handles 后关——顺序颠倒** → C6-006 | **P2** |
| 6 | reconstructed 临时目录 | restoreRangeInto:172-173,231-233 | finally deleteRecursively（失败 WARN 可观测） | ✓ | ✓ |
| 7 | task-local cp-N 快照目录 | doSnapshot:120（F-02 retention） | pruneOldCheckpoints 滚动保留 2 代 | 删除失败 WARN + 下轮重试（可观测残留） | ✓ |
| 8 | KafkaConsumer | KafkaSourceFunction.run:246 / checkConnection:377 | try-with-resources | ✓ | ✓ |
| 9 | KafkaProducer（长驻） | KafkaTwoPhaseCommitSink.ensureTransactionOpen:154-155 | **无任何释放路径** | 任务 stop/cancel/fail 全部泄漏 → C6-001 | **P0** |
| 10 | KafkaProducer（探测） | KafkaTwoPhaseCommitSink.checkConnection:307 | try-with-resources（Producer 是 Closeable） | ✓ | ✓ |
| 11 | JDBC Connection | JdbcTwoPhaseCommitSink.commit:673 / initializeLedgerTable:819 | finally: rollback→restoreAutoCommit→close（717-735 / 829-835） | ✓（guard 命中分支同样关闭） | ✓ |
| 12 | PreparedStatement | writeInserts:925（TWR）/ writeKeyedOps:959-1012（finally 双关）/ ledger helpers:842/854（TWR） | ✓ | ✓ | ✓ |
| 13 | IBatchLoader | BatchLoaderSourceReader.adoptSplit:117 / BatchLoaderSourceFunction.run:105 / checkConnection:165 | reader.close():221-232 / run finally:144-148 / probe close | ✓ | ✓ |
| 14 | FileChannel + FileLock（manifest 锁） | FileTwoPhaseCommitSink.withManifestLock:393-399 | TWR；release 在 finally、先 release 后 close；jvmLock finally unlock | lock 抛错时 channel TWR 关闭 + jvmLock 释放 | ✓ |
| 15 | split InputStream（FileInputStream） | FileSourceReader.openSplit:278 | EOF 路径 closeActiveReader:241-249 / close():466-470 / restoreState:451 / openSplit 三个错误分支显式 close（290/295/302） | ✓ | ✓ |
| 16 | epoch 临时文件 `.epoch-N.tmp` | FileTwoPhaseCommitSink.saveState:253-261 | commit rename（ATOMIC_MOVE）/ abort delete:474-481 | checkpoint 未 durable 即失败 → 临时文件无人认领 → C6-007 | **P2** |
| 17 | 自建 runner 线程 + 有界队列 | MessageSourceReader.launchEngine:81-92 / DebeziumCdcSourceReader.launchEngine:92-138 | close() join(5s)（210/302） | 线程阻塞在 queue.put 时 join 超时抛错，线程+订阅/引擎泄漏 → C6-002/003 | **P1** |
| 18 | IMessageSubscription | MessageSourceFunction.run:139 | finally localSubscription.cancel():196-205（R5-CON-04 本地引用防 cancel/subscribe 竞态） | ✓（含 P1-9 失败退出路径） | ✓ |
| 19 | Debezium source/subscription/failure-listener | DebeziumCdcSourceFunction.run:248-277 | finally: unregister listener → subscription.cancel → source.stop（297-318）；subscribe 失败补 stop:279 | ✓ | ✓ |
| 20 | JDK HttpClient | AvroSchemaRegistryClient ctor:48 | 无 close（长驻 per-format 实例；JDK 线程 daemon，单实例代价有限） | — | P3 附注（并入 C6-008 说明） |
| 21 | HTTP 请求-响应 | readJsonResponse:113-129 | BodyHandlers.ofString 自动消费 | **无响应超时**（仅 connectTimeout） → C6-008 | **P2** |
| 22 | MANIFEST_JVM_LOCKS（static ReentrantLock 注册表） | FileTwoPhaseCommitSink:390-391 | 无 remove | 只增不减（按输出目录数有界） → C6-009 | P3 |
| 23 | CEP SharedBuffer 事件/节点引用计数锁 | NFA.EventWrapper（registerEvent→releaseEvent，TWR:244）/ SharedBufferAccessor.lockNode:265-276 / releaseNode:293-338 | EventWrapper.close / releaseOrDetach（over-release 抛 typed、repeat-visit 注释论证终止性） | 异常路径经 TWR 释放；mid-path 缺失 fail-fast | ✓ |
| 24 | DebeziumEngineWrapper failure-listener（static 注册表） | DebeziumCdcSourceFunction.run:248 | finally unregister:298 | resolveConnectorName 先于 register（未注册不会误 unregister 非 null 键） | ✓ |

---

## 发现条目

### [nop-stream-state-connectors-C6-001] kafka-2pc sink 的 KafkaProducer 在所有任务终止路径上永不关闭（每次任务运行确定性泄漏 producer 线程/连接/缓冲池）

- **文件**: `nop-stream/nop-stream-connector-kafka/src/main/java/io/nop/stream/connector/kafka/KafkaTwoPhaseCommitSink.java:89,150-173`（全文无 `close()` override）
- **证据片段**:
  ```java
  private transient volatile Producer<String, byte[]> producer;   // L89，懒创建后永不置 null/关闭
  private void ensureTransactionOpen() throws Exception {
      ...
      if (producer == null) {
          producer = newProducer();          // L155：唯一创建点
      }
      ...
      producer.beginTransaction();
  ```
  框架侧两个释放钩子均对该类失效（`StreamSinkOperator.java:173-177` 只关 `AutoCloseable` userFunction；`FunctionUtils.closeFunction:44-49` 只关 `RichFunction`；`StreamFunction` 仅 extends `Serializable`，本类两者皆未实现）。
- **严重程度**: P0（主路径资源泄漏——正常成功路径即泄漏：每个 kafka-2pc 子任务每跑一次作业，终止时（CANCEL/DRAIN/FAILED 均然）必泄漏一个 producer）
- **并发场景**: 任务线程（invoke/saveState）创建 producer；TaskManager 停止/作业取消/任务失败重建时的 teardown 线程本应释放。
- **触发时序**: T1 任务线程首次 invoke → ensureTransactionOpen 创建 KafkaProducer（持有 `kafka-producer-network-thread`、broker 连接、RecordAccumulator 缓冲池、transaction manager）→ T2 作业 DRAIN/CANCEL，`StreamSinkOperator.close()` 执行 → `userFunction instanceof AutoCloseable` = false → 什么都不关 → producer 对象仍被其存活的 Sender 线程强引用（永不 GC）→ 下一run重建 subtask 拷贝再创建新 producer → 长驻 TM 进程按「作业运行次数 × kafka-2pc 子任务数」单调累积。
- **现状**: producer 的获取（ensureTransactionOpen / newProducer）与释放完全不对称，F8 本族最直接实例；进程内无任何路径（正常/异常/取消/超时）到达 `producer.close()`。
- **风险**: 每个泄漏 producer = 1 条常驻 daemon 线程 + 到 broker 的 TCP 连接 + 最高 `buffer.memory`（缺省 32MB）的缓冲池 + 事务协调器侧悬挂 producer 代。反复 stop/start 或反复失败重启的集群线性恶化：句柄/连接数膨胀、broker 端事务状态堆积；与 C7 长时运行劣化叠加。JVM 退出不被阻塞（线程 daemon），故非进程级致命，但长驻进程内无界。
- **建议**: `KafkaTwoPhaseCommitSink` 实现 `AutoCloseable`（`StreamSinkOperator.close():175` 现成钩子即可触达）并在 `close()` 中幂等关闭：`transactionOpen → abortTransaction()` 后 `producer.close(Duration)`，`producer=null`；或让 `TwoPhaseCommitSinkFunction` 声明 `AutoCloseable` 并给 `rollback()` 后追加模板 `close()`。注意与 `rollback()` 的先后：close 内先 abort 后 close，且 close 幂等（重复调用安全）。
- **失效面枚举**（F3 同族清扫——全部持有长驻资源的 sink/source/格式类逐一核对）: ① `KafkaTwoPhaseCommitSink`（本条，**未修**）；② `KafkaSourceFunction`——consumer 在 run/checkConnection 内 TWR（已修面，✓）；③ `FileTwoPhaseCommitSink`——无长驻句柄（NIO per-call，✓）；④ `JdbcTwoPhaseCommitSink`——per-commit 连接 finally 关闭（✓）；⑤ `MessageSinkFunction`——无自有资源，messageService 外部所有（✓）；⑥ `BatchConsumerSinkFunction`——consumer 在 close() finally 关闭 + flush 失败优先（✓）；⑦ `MessageSourceFunction`——subscription finally cancel（✓）；⑧ `DebeziumCdcSourceFunction`——run finally 三连释放（✓）；⑨ `BatchLoaderSourceReader/LegacyFunction`——loader close/finally（✓）；⑩ 两个 `*ConnectorFactory.checkConnection` 探测——均 TWR（✓）。仅 ① 一处破面。
- **信心水平**: 确定（代码路径无歧义：全类无 close/destroy override；框架双钩子均不触达该类）
- **误报排除**: 不属于「有界声明存在的资源」（无 owner doc 声明 producer 随任务泄漏可接受）；不属于「平台基础设施自身不重审」——泄漏点在本模块连接器代码；也非「无状态单例设计意图」。kafka-clients 的 producer 无 finalizer/GC 回收路径（Sender 线程强引用），不能以「GC 会兜底」驳回。
- **复核状态**: 保留 P0（独立复核四点全部证实，终裁维持 P0；理由见文末「维度复核结论」）；二轮复核：保留 P0（独立重证四点：全类无 close/destroy、不实现 AutoCloseable/RichFunction；基类 TwoPhaseCommitSinkFunction=SinkFunction+CheckpointParticipant 无任何关闭钩子；StreamSinkOperator.close:175 的 instanceof AutoCloseable 恒 false，Task.run finally→closeOperatorChains→OperatorChain.close 与 StreamTaskInvokable.closeChainAndGate 两条 teardown 链均只达该 no-op；checkConnection probe producer 为 TWR 干净面；量级复核支持 P0——每作业每 kafka-2pc 子任务泄漏 1 条 daemon network 线程+broker 连接+至多 buffer.memory 缓冲池，正常 DRAIN 即确定性泄漏）

### [nop-stream-state-connectors-C6-002] MessageSourceReader.close() 无法终止阻塞在 queue.put 的 runner 线程——join 超时抛错后线程连同活跃订阅永久泄漏

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceReader.java:194-212,221-231`
- **证据片段**:
  ```java
  public void close() throws Exception {
      running = false;
      embeddedFunction.cancel();
      Thread thread = runnerThread;
      if (thread != null) {
          thread.join(CLOSE_JOIN_TIMEOUT_MILLIS);      // L200：无 interrupt 兜底
          if (thread.isAlive()) {
              throw ... "message source runner did not terminate within ..."   // 抛错后线程仍在
          }
      }
  ...
  public void collect(T element) {
      queue.put(element == null ? NullValueMarker.INSTANCE : element);   // L225：阻塞点，不检查 running
  ```
- **严重程度**: P1（取消路径资源泄漏：线程 + 内嵌 `IMessageService` 活跃订阅（Kafka 消费组成员/KafkaConsumer/SysDao 连接））
- **并发场景**: 订阅回调线程（embeddedFunction 的推送线程）阻塞在 `queue.put`；任务线程执行 close()（mailbox 停机路径）。
- **触发时序**: T1 下游停轮询（任务进入 close/cancel 或上游失败）→ 队列（容量 256）填满 → T2 订阅线程在 collect→`queue.put` 永久阻塞 → T3 任务线程 close()：`running=false` + `embeddedFunction.cancel()` 置位循环标志，但 T2 卡在 put 不回到循环 → `join(5000)` 超时 → close() 抛 typed 异常（响亮但为时已晚）→ T2 线程永不退出 → run() 的 finally（`localSubscription.cancel()`，MessageSourceFunction.java:196-205）永不执行 → 消费组成员/连接泄漏。
- **现状**: 阻塞等待点（put）与停机信号（running flag + cancel）之间无中断桥；`put` 的 InterruptedException 分支（L226-229）证明中断本是设计好的退出通道，但 close() 从不调用 `thread.interrupt()`。
- **风险**: 每次「关闭时恰有积压」的停机泄漏一个线程 + 一份订阅（含后端连接）；`isFinished()=!running` 假象下任务已终止，泄漏物不可自愈，长驻 TM 上累积。close 抛错还可能吞掉/污染停机主流程的原始异常。
- **建议**: join 超时且 `thread.isAlive()` 时先 `thread.interrupt()` 再二次短 join（put 的 InterruptedException 分支已就绪）；或将 `queue.put` 改为 `offer(timeout)` 轮询 `isCancelled()`（QueueSourceContext 已有该入口）。
- **失效面枚举**: ① `MessageSourceReader`（本条，未修）；② `DebeziumCdcSourceReader`——同一模板复刻，同缺陷（→ C6-003，未修）；③ `FileSourceReader`——无自建线程，poll 即任务线程（✓）；④ `BatchLoaderSourceReader`——无自建线程（✓）；⑤ `KafkaSourceFunction`——自轮询于任务线程（✓）；⑥ `MessageSourceFunction` 直跑（legacy 算子线程内 await 循环，latch 响应 cancel，✓）。同族 2 处，全部登记。
- **信心水平**: 确定（阻塞链与不可达的 finally 均为静态可证；「队列恰好满」触发条件在背压/停机窗口下常见）
- **误报排除**: 不是「join 超时抛错已响亮」的可接受设计——响亮报告不等于资源回收，泄漏本体（线程+订阅）依然存在；也不是 owner doc 声明的能力边界（connectors owner doc 声明的是「限速即背压」边界，非停机泄漏边界）。
- **复核状态**: 驳回（复核证伪泄漏主链路：close() join 的是 runner 线程而非 put 阻塞线程，queue.clear() 可达并解救阻塞 put；理由见文末「维度复核结论」）；二轮复核：维持驳回（重证结构：runner=shutdownLatch.await(1s) 循环线程（MessageSourceFunction.java:182-184），queue.put 阻塞发生在 message-service 投递线程（onMessage→ctx.collect:158-176）；cancel() countDown 后 runner ≤1s 退出→join(5000) 不超时→queue.clear()（signalAll notFull）解救 put；KafkaMessageService.cancel 5s 超时后 shutdownNow 中断兜底、PollingJdbcMessageService.cancel 纯置位非阻塞——泄漏链不成立）

### [nop-stream-state-connectors-C6-003] DebeziumCdcSourceReader.close() 同族：阻塞在 queue.put 的引擎线程泄漏，连带嵌入 Debezium 引擎与 CDC 源库连接

- **文件**: `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceReader.java:294-312,326-335`
- **证据片段**:
  ```java
  public void close() throws Exception {
      running = false;
      if (embeddedFunction != null) {
          embeddedFunction.cancel();
      }
      Thread thread = runnerThread;
      if (thread != null) {
          thread.join(CLOSE_JOIN_TIMEOUT_MILLIS);      // L302
          if (thread.isAlive()) { throw ...; }         // 引擎线程留在原地
      }
      if (queue != null) { queue.clear(); }            // L309：join 抛错后永远走不到
  ...
  public void collect(ChangeEvent element) {
      queue.put(element);                              // L329：阻塞点
  ```
- **严重程度**: P1（取消路径资源泄漏：runner 线程 + 嵌入 Debezium 引擎实例 + 到 CDC 源数据库的连接 + offset backing store）
- **并发场景**: Debezium 引擎投递线程（调 collect 的线程）阻塞在 `queue.put`；任务线程执行 close()。
- **触发时序**: T1 CDC 突发（binlog 批量回放/快照阶段）产量 > 256 且任务线程已停止 pollNext（cancel/上游失败/作业停机）→ 队列满 → T2 引擎投递线程阻塞在 put → T3 close()：cancel() 置位 running、countDown latch，但 T2 卡在 put 不经 latched 循环 → join(5s) 超时抛错 → `queue.clear()`（本可解救 put）在抛错后不可达 → 引擎线程、嵌入 DebeziumCdcSourceFunction（其 run() finally 的 source.stop/unsubscribe 亦不可达）、源库连接全部滞留。
- **现状**: 与 C6-002 同根因（F3 同族第二处）；额外放大：Debezium 引擎持有对源库（MySQL/PG）的长连接与 binlog 位点，泄漏代价更高。
- **风险**: 反复停机/失败重启的 CDC 作业在 TM 进程内累积引擎线程与源库连接；源库侧连接数被耗尽会影响业务库；残留线程持有已取消订阅的引擎，行为未定义。
- **建议**: 同 C6-002：join 超时先 `thread.interrupt()`（put 分支已处理中断并抛 typed）再二次 join；并把 `queue.clear()` 提到 join 之前/finally 中，给阻塞 put 一个排水路径。
- **失效面枚举**: 同族清单 = C6-002 的失效面枚举（6 处逐一核对）；本条为其中 ②。引擎宿主 `DebeziumCdcSourceFunction` 自身的 run() finally 释放链完整（✓），缺陷仅在 reader 包装层的停机桥接。
- **信心水平**: 很可能（代码链路静态可证；「队列满时 close」的现实概率取决于 CDC 流量形态——快照/批量回放窗口内必然满足）
- **误报排除**: 非「join+typed 抛错=有守卫」的误判——守卫只报不收；非 owner doc 边界（CDC cookbook/能力矩阵未声明停机期引擎泄漏为接受行为）。
- **复核状态**: 驳回（同 C6-002 根因证伪：engine.stop() 为非阻塞信号，runner 经 latch ≤1s 退出，三连释放与 queue.clear() 均可达；理由见文末「维度复核结论」）；二轮复核：维持驳回（重证：DebeziumCdcSourceFunction.run 主循环为 completionLatch.await(1s)（:283-290），cancel()（:331-344）countDown+sub.cancel+source.stop；DebeziumMessageSource.stop→DebeziumEngineWrapper.stop→engine.close() 为非阻塞信号（wrapper:194-213）；runner ≤1s 退出→join 不超时→finally 三连释放（:297-318）与 queue.clear()（reader:309-311）均可达——引擎线程+源库连接泄漏链不成立）

### [nop-stream-state-connectors-C6-004] RocksDBMetricsRecorder gauge 抓取与后端 close 的竞态：db 引用在 synchronized 之外求值，可对已关闭句柄读 native 属性（SIGSEGV 窗口）

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:82-101`（配套 `RocksDBKeyedStateBackend.close():988-990`）
- **证据片段**:
  ```java
  private void gauge(MeterRegistry registry, String meterName, String property) {
      registry.gauge(meterName, Tags.empty(), dbRef, ref -> readProperty(ref.get(), property));
  }                                            // ref.get() 在锁外求值
  public synchronized double readProperty(RocksDB db, String property) {
      if (db == null) { return Double.NaN; }
      try {
          return db.getAggregatedLongProperty(property);   // 对已 close 的 db = native use-after-free 窗口
  ```
- **严重程度**: P1（罕见但可具体化的竞态，后果为进程级 JVM crash；类注释自称「Synchronized with readProperty so an in-flight scrape cannot race the detach」，该守卫被锁外取参击穿）
- **并发场景**: ops HTTP 抓取线程（scrape /metrics 时调用 gauge valueFunction）与任务线程（后端 close()：先 `metricsRecorder.close()` 再 `db.close()`）。
- **触发时序**: T1 scrape 线程求值 `ref.get()` → 拿到非 null db（此刻尚未进 synchronized 方法）→ T2 close 线程进入 `synchronized close()`（不与 T1 互斥，因 T1 还未拿监视器）→ 置 dbRef=null → 继续关 CF handles 并 `db.close()`（native 释放）→ T1 进入 `readProperty(已捕获的旧 db)` → null 检查失效（参数非 null）→ `getAggregatedLongProperty` 命中已释放/释放中句柄 → `IllegalStateException`（nativeHandle_==0 时）或 native use-after-free（nativeClose 进行中时 SIGSEGV）。反向时序（T1 已持锁后 T2 才 close）才是安全的——这正是设计意图，但取参在锁外使其不成立。
- **现状**: 守卫意图正确、实现差一行：参数求值应移入临界区。
- **风险**: 状态后端关闭/作业重启与 /metrics 抓取并发的窗口内 JVM 崩溃（native 崩溃无法被 catch），多作业共享 TM 进程时一崩全崩。
- **建议**: `readProperty` 改为无参并在内部 `synchronized(this){ RocksDB db = dbRef.get(); if (db==null) return NaN; ... }`，gauge lambda 改传 `this`/recorder；或 close 顺序上先从 registry remove gauge 再放 dbRef。
- **失效面枚举**: 本模块 native 句柄的全部跨线程读点逐一核对：① recorder gauges（本条）；② 后端 close 自身（单线程 mailbox 模型 + 幂等 null 守卫，✓）；③ SnapshotSerDe/State 类迭代器（任务线程内 TWR，✓）；④ `RocksDBIncrementalRestore` 重建 src（restore 生命周期内自持自关，✓）。仅 ① 存在跨线程共享 native 句柄读。
- **信心水平**: 确定（窗口存在性可静态证明；最终表现是 IllegalStateException 还是 SIGSEGV 取决于与 nativeClose 的微观交错，两者均非可接受行为）
- **误报排除**: 不是「看到共享字段就报线程不安全」——此处有明确的两线程交错链与 native 后果；也不是「原语使用不是问题」——synchronized 的用途恰是防该竞态而失效。
- **复核状态**: 保留 P1（锁外取参窗口经 live code 证实，竞态链成立；理由见文末「维度复核结论」）；二轮复核：保留 P1（L83 valueFunction 的 `ref.get()` 确在进入 synchronized readProperty 之前求值；后端 close 的 recorder.close 先于全部 native 关闭的顺序本身无误——缺陷恰在锁外取参使该顺序失去保护作用）

### [nop-stream-state-connectors-C6-005] RocksDBKeyedStateBackend 构造失败路径泄漏全部 native 句柄（open 抛错后对象不可达但 GC 兜底不可靠）

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:186-205,211-280`
- **证据片段**:
  ```java
  private void openDB() {
      dbOptions = new DBOptions()...                 // L215：先分配
      cfOptions = new ColumnFamilyOptions()...
      if (optionConfig.isMemoryControlEnabled()) { applyMemoryControlPreset(); }  // L229：Cache/WBM/Bloom
      ...
      try {
          db = RocksDB.open(dbOptions, dbPath, cfDescriptors, handles);
      } catch (RocksDBException e) {
          throw new StreamException(ERR_STREAM_STATE_ERROR, e)...   // L260-263：直接抛，前面全漏
      }
  ```
- **严重程度**: P2（错误路径 native 资源泄漏；rocksdbjni RocksObject finalizer 是唯一兜底，native 内存不受堆 GC 压力驱动，回收时机不可控）
- **并发场景**: 单线程构造路径；典型触发 = 作业重启窗口新旧进程重叠导致 `LOCK:` 抢锁失败、或目录损坏 open 失败。
- **触发时序**: T1 构造器分配 dbOptions/cfOptions（+内存控制预置的 LRUCache/WriteBufferManager/BloomFilter）→ T2 `RocksDB.open` 因锁占用/目录损坏抛 RocksDBException → 构造器抛 StreamException → 调用方从未拿到对象引用，close() 永不可达 → 全部句柄等待 finalizer；`handles` 列表中已部分创建的 ColumnFamilyHandle 同样无人关闭。
- **现状**: 获取与释放不对称于「获取后任一语句抛错」路径（同类纪律在 `RocksDBIncrementalRestore` 已用 try-with-resources 兑现，注释自称「mirrors the try-with-resources discipline」——openDB 自身未 mirror）。
- **风险**: 失败重启场景下泄漏量与重试次数线性相关；WriteBufferManager/Cache 的 native 内存 + 后台线程（setMaxBackgroundJobs）驻留；锁竞争失败重试风暴时叠加。
- **建议**: openDB 内 try/catch：`RocksDB.open` 失败时先逆序关闭 `handles` 中非 null 项，再依次关闭 dbOptions/cfOptions/WBM/cache/filter（复用 close() 的收集-suppressed 形态），然后重抛。
- **失效面枚举**: 同类「获取后可能抛错」的 native 获取点：① openDB（本条，未修）；② `getOrCreateColumnFamily:344-353`（createColumnFamily 失败时无句柄半成品，✓）；③ `restoreRangeInto`（TWR 全配对，✓）；④ `doSnapshot` Checkpoint（TWR，✓）；⑤ 模块内其余 `new Iterator/Options` 点（TWR，✓）。仅 ①。
- **信心水平**: 确定（路径静态可证；影响等级受 finalizer 存在缓解，故 P2 非 P1）
- **误报排除**: 不是「失败即进程终态、泄漏无意义」——open 失败（锁被占）正是可重试场景，重试叠加泄漏；不是低频启动路径粗粒度问题的误报校准项（本条是资源泄漏非性能）。
- **复核状态**: 保留 P2（openDB 失败路径无清理经 live code 证实；理由见文末「维度复核结论」）；二轮复核：保留 P2（dbOptions/cfOptions/memory-control 预设句柄与 handles 半成品在 RocksDB.open 抛错时确无任何清理；同方法内 listColumnFamilies 的 Options 反而有 TWR——注释自称 mirror 纪律而 open 自身未 mirror 的双标准成立）

### [nop-stream-state-connectors-C6-006] RocksDBIncrementalRestore.restoreRangeInto 先关 src RocksDB 后关 ColumnFamilyHandles——违反 rocksdbjni 关闭顺序，native use-after-free 风险

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalRestore.java:208-230`
- **证据片段**:
  ```java
  List<ColumnFamilyHandle> handles = new ArrayList<>();
  try (RocksDB src = openReadOnlyTyped(dbOptions, reconstructed, descriptors, handles)) {
      copied = 0;
      for (ColumnFamilyHandle srcCf : handles) { ... backend.copyColumnFamilyRange(src, srcCf, ...); }
  } finally {
      for (ColumnFamilyHandle h : handles) {      // L227：TWR 已先关 src，这里再关句柄
          if (h != null) h.close();
      }
  }
  ```
- **严重程度**: P2（native 关闭顺序违约；rocksdbjni 官方契约 = 先关全部 ColumnFamilyHandle 再关 DB，逆序对已随 DB 释放的句柄调 dispose 是 use-after-free；触发需 restore 路径 + native 行为，非必现崩溃）
- **并发场景**: restore 路径单线程执行（同 JVM 重建时与既有后端 db 并存，但 src 实例私有）；风险是顺序性而非并发性。
- **触发时序**: T1 try-with-resources 正常/异常退出 → T2 自动 `src.close()`（DBImpl 析构，旗下 CF 句柄原生对象随之失效）→ T3 finally 再对 `handles` 逐个 `h.close()`（dispose 已悬空指针）→ UB（多数构建表现为无害 no-op，部分平台/版本 SIGSEGV/堆损坏）。
- **现状**: 与同模块 `RocksDBKeyedStateBackend.close():991-1017` 的正确顺序（CF handles → defaultCF → db）形成同仓双标准；restore 路径是 F-02 注释声明的「same-JVM restore resolves the newest」常规路径，非冷门分支。
- **风险**: 增量恢复（作业重启恢复路径）末尾的原生句柄 double-dispose；一旦命中崩溃形态，恢复流程以 SIGSEGV 终结（typed 错误体系失效）。
- **建议**: 调整为显式两段：先 `for handle: h.close()`，再 `src.close()`（放弃 TWR 对 src 的自动顺序，或 TWR 仅包 handles 集合后手工关 src）；与 backend.close() 的顺序对齐。
- **失效面枚举**: 模块内全部「RocksDB 实例 + CF handles 共同关闭」点：① backend.close()（顺序正确，✓）；② restoreRangeInto（本条，未修）；③ openReadOnlyTyped 失败路径（open 抛错时 handles 至多部分创建且无人关——并入 C6-005 同族形态，此处 openReadOnly 对象由调用方 TWR 承接，失败时 handles 亦漏，登记为 C6-005 的同族兄弟）。两处主点 + 一处兄弟。
- **信心水平**: 很可能（顺序违约确定；实际崩溃依赖 rocksdbjni 版本行为，非每触发必崩）
- **误报排除**: 不是「原语使用不是问题」的误报校准项——这是复合顺序误用；也不是理论风险未具体化（关闭顺序契约与交错后果明确，降为 P2 仅因非必现）。
- **复核状态**: 保留 P2（TWR 先关 src DB 后关 handles 的顺序违约经 live code 证实，与 backend.close() 同仓双标准；理由见文末「维度复核结论」）；二轮复核：保留 P2（`try (RocksDB src = openReadOnlyTyped(...))` 先关 DB、finally 后关 handles 的逆序复证成立；实际崩溃形态依赖 rocksdbjni 版本行为，非必现——P2 恰当）

### [nop-stream-state-connectors-C6-007] FileTwoPhaseCommitSink：saveState 落盘的 epoch 临时文件在 checkpoint 未 durable 即失败时成为永久孤儿（无清理路径、无启动清扫）

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:249-266,474-481`
- **证据片段**:
  ```java
  public TaskStateSnapshot saveState(long epochId) throws Exception {
      synchronized (currentBuffer) {
          if (count > 0) {
              Path tempPath = tempPath(epochId);
              writeLines(tempPath, currentBuffer);          // L254：temp 已落盘
              forcePath(tempPath);
              getPendingCommits().put(epochId, new FilePendingCommit(...));
  ...
  public void abort(long epochId) throws Exception {
      Object raw = getPendingCommits().remove(epochId);     // 只有 pendingCommits 里的条目才会删 temp
  ```
- **严重程度**: P2（失败/取消路径的磁盘残留，单调累积；不损数据正确性——孤儿 temp 永不进入输出）
- **并发场景**: 任务线程 saveState 与 checkpoint 协调器的 durable/abort 决策跨线程/跨进程。
- **触发时序**: T1 任务线程 saveState(epoch N+1) 写下 `.epoch-(N+1).tmp` 并登记 pendingCommits → T2 checkpoint N+1 超时/协调器 abort/进程崩溃，checkpoint 未 durable → 恢复时 `restoreFromEpoch` 只能 abort「恢复快照 pendingCommits 里存在」的条目（基类 TwoPhaseCommitSinkFunction.java:234-277 的 toAbort 集合来自恢复快照），N+1 的登记只存在于已死进程内存 → 孤儿 temp 无人删除 → 输出目录 `.epoch-*.tmp` 按失败 checkpoint 数线性累积；无任何启动扫描/过期清扫。
- **现状**: abort 路径覆盖「pendingCommits 已登记且被恢复快照继承」的 temp；覆盖不到「登记后即死」的窗口；R6 CON-01 的 fresh-run 碰撞守卫只针对 manifest/final 文件，不清理旧 temp。
- **风险**: 长期运行 + 偶发 checkpoint 失败的作业，输出目录残留数据量等于全部失败 epoch 的批量（每个 temp = 一个完整 epoch 批），磁盘单调增长并可能误导运维（看似未提交数据）。持久化侧无泄漏（JDBC sink 无对应物），纯磁盘面。
- **建议**: ① 恢复入口（setPendingCommits/首次 commit 前）扫描输出目录中 `.epoch-*.tmp` 且不在当前 pendingCommits 的文件删除（或按 manifest+pending 双白名单判断）；② 或在 beginTransaction 时清扫 mtime 早于本次 run 启动时间的 temp。优先方案 ①（与 R6 CON-01 的 provenance 判别同址）。
- **失效面枚举**: 2PC sink 临时产物族：① file sink `.epoch-N.tmp`（本条，未修）；② file sink `manifest.properties[.sK].tmp`（写后 ATOMIC_MOVE，move 失败残留一份 manifest temp——同族微缩版，登记同修）；③ JDBC sink（数据+台账同事务，无 temp，✓）；④ Kafka sink（无本地 temp，悬挂事务由 initTransactions abort，✓）；⑤ rocksdb 增量 cp-N 目录（F-02 retention 有界，✓）；⑥ restore reconstructed 目录（finally 删除，✓）。
- **信心水平**: 确定（窗口为「登记与 durable 之间」的固有窗口，无争议）
- **误报排除**: 非 F2「半个成功」语义问题（孤儿 temp 不影响 exactly-once，输出不受污染）；非「幂等豁免对象」（红线豁免的是目录与台账表，不是 per-epoch temp 文件）；磁盘残留属 C6 步骤 6「临时文件清理钩子」的正审范围。
- **复核状态**: 保留 P2（孤儿窗口与无清扫路径经 live code 证实；理由见文末「维度复核结论」）；二轮复核：保留 P2（saveState 的登记仅在内存 pendingCommits、abort 仅覆盖恢复快照继承的条目、全文件唯一 temp 删除点在 abort 且无启动清扫，均复证成立）

### [nop-stream-state-connectors-C6-008] AvroSchemaRegistryClient HTTP 请求无响应超时——registry 半死时首个 miss 记录的 encode/decode 无限期挂起任务线程

- **文件**: `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java:48,113-119`（消费面 `AvroWireFormat.encode:55-60 / decode:92 / readerSchemaFor:111-117`）
- **证据片段**:
  ```java
  this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();  // 仅连接超时
  ...
  private Map<?, ?> readJsonResponse(HttpRequest request) {
      HttpResponse<String> resp;
      try {
          resp = http.send(request, HttpResponse.BodyHandlers.ofString());   // 无 request.timeout
  ```
- **严重程度**: P2（无超时等待点；挂起发生于热路径首次 miss——新 subject/schemaId 的首条记录）
- **并发场景**: Kafka 任务线程（KafkaSourceFunction.valueDeserializer / KafkaTwoPhaseCommitSink.valueSerializer 的 per-record 编解码）同步调用 registry；无独立超时守护。
- **触发时序**: T1 记录到达，decode 遇到未缓存的 schemaId（或 encode 遇到新 subject）→ T2 `http.send` 建连成功（registry 端口 accept）但对端不响应（半死/Full GC/网络黑洞）→ connectTimeout 已过、无响应超时 → T3 任务线程无限期阻塞在 send → 该子任务 watermark/heartbeat 全停，且「不产出也不报错」（F5 形态在本维度的资源占用面）。
- **现状**: 只有 10s 连接超时；`HttpRequest.timeout()` 未设置；缓存命中路径无网络 I/O，故挂起点集中在 miss——但 miss 在「新 schema 上线」「registry 重启换 id」时必然出现。
- **风险**: 单 registry 抖动 → kafka 作业子任务静默挂死，只能靠外部 checkpoint 超时/人工干预回收；挂死期间任务线程持有的资源（consumer、事务 producer、network buffer）全部滞留。
- **建议**: 每请求 `.timeout(Duration.ofSeconds(10))`（或配置化）；fail-fast 后由现有 typed 异常路径（StreamAvroException）转任务失败重启。
- **失效面枚举**: 模块全部同步外部 I/O 等待点：① registry HTTP（本条，未修）；② Kafka consumer poll（200ms 上限，✓）；③ Kafka producer send/flush/commit（客户端 max.block.ms/默认超时面，✓）；④ JDBC commit（驱动 socketTimeout 未在本 sink 配置——由 jdbcUrl 侧配置承接，owner doc 未声明，登记为观察项不立案）；⑤ Debezium engine（自带内部超时面，✓）；⑥ file I/O（本地盘，✓）。
- **信心水平**: 确定（缺失超时为静态事实；触发依赖 registry 半死形态）
- **误报排除**: 不是 C7 维度重复计数——本条以「挂起点持有任务线程与其资源」的资源视角立案，修复建议同 F5 家族共享；非「有界声明存在」（connectTimeout 不覆盖响应阶段，声称的有界是残缺的）。
- **复核状态**: 保留 P2（仅 connectTimeout、send 无 request.timeout 经 live code 证实；理由见文末「维度复核结论」）；二轮复核：保留 P2（ctor L48 仅 connectTimeout(10s)、readJsonResponse 的 http.send 无 HttpRequest.timeout 复证成立；cache-miss 点即挂起点，Registry 半死形态下任务线程无界等待）

### [nop-stream-state-connectors-C6-009] MANIFEST_JVM_LOCKS 静态锁注册表只增不删（register 无 unregister）

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:96-99,389-392`
- **证据片段**:
  ```java
  private static final ConcurrentHashMap<String, ReentrantLock> MANIFEST_JVM_LOCKS =
          new ConcurrentHashMap<>();
  ...
  ReentrantLock jvmLock = MANIFEST_JVM_LOCKS.computeIfAbsent(
          outputDirPath.toAbsolutePath().normalize().toString(), k -> new ReentrantLock());
  ```
- **严重程度**: P3（原则上无界的静态注册表；实际上界 = 进程生命周期内出现过的不同 file sink 输出目录数，单条目代价约几十字节 + 一把 ReentrantLock）
- **并发场景**: 多作业/多子任务线程共享进程级注册表；条目按输出目录路径去重。
- **触发时序**: 每个新的 `outputDir` 首次 commit → computeIfAbsent 插入一条 → sink 关闭/作业结束后条目永不清除 → 长驻 TM 上随作业配置多样性缓慢增长；无删除调用点（全仓 grep 确认仅 computeIfAbsent 一个写点）。
- **现状**: 与 F8「register/unregister 配对」形态吻合但量级无害；与文件 sink 的 `.manifest.lock` 文件（`Files.createDirectories` 目录内固定单文件，跨 JVM 复用）不同，Java 侧注册表无生命周期钩子。
- **风险**: 实际可忽略；仅在「同一 TM 进程承载海量短命作业且输出目录高度多样」的部署形态下成为慢性增长项。
- **建议**: 不强制修；若统一治理，可在 close() 时 `MANIFEST_JVM_LOCKS` 引用计数化，或接受并文档声明上界 = 目录基数。
- **失效面枚举**: 本审计对象内全部 static 注册表逐一核对：① MANIFEST_JVM_LOCKS（本条，P3）；② `DebeziumEngineWrapper` failure-listener（register/unregister 在 run/finally 配对，✓）；③ `NopStreamOffsetBackingStore` 静态 offset 注册表（AR-03 首跑 clearConnector 清理 + 实例名隔离，✓）；④ `StreamMetricsRegistries` 进程组合注册表（gauge 随后端 close 摘除，✓）。
- **信心水平**: 确定（无删除点为静态事实）；**误报排除**: 按「有界声明缺失但实际有界」的自校准降 P3，不按 P1 无界账本口径放大；**复核状态**: 保留 P3（computeIfAbsent 唯一写点、无 remove 经全仓 grep 复证，P3 恰当；理由见文末「维度复核结论」）；**二轮复核**: 保留 P3（MANIFEST_JVM_LOCKS 全模块仅 L98-99 声明 + L390 computeIfAbsent 一个写点、无任何 remove 复证成立；上界=目录基数，P3 恰当）

---

## 汇总

| 严重度 | 数量 | 条目 |
|---|---|---|
| P0 | 1 | C6-001 kafka producer 永不关闭 |
| P1 | 3 | C6-002 MessageSourceReader 阻塞 put 泄漏；C6-003 DebeziumCdcSourceReader 同族；C6-004 metrics 抓取/close 竞态（native 崩溃窗口） |
| P2 | 4 | C6-005 openDB 失败路径 native 泄漏；C6-006 restore 关闭顺序颠倒；C6-007 file sink 孤儿 temp；C6-008 registry HTTP 无响应超时 |
| P3 | 1 | C6-009 manifest 锁注册表只增不删 |

**配对健全面（无发现声明）**: RocksIterator 全部 10 处、Checkpoint、JDBC Connection/PreparedStatement 全部、KafkaConsumer 全部、探测路径 producer/consumer、FileChannel+FileLock、split InputStream、IMessageSubscription、Debezium 引擎三连释放、batch loader、CEP SharedBuffer 引用计数锁、Debezium failure-listener 注册表、cp-N 目录 retention、reconstructed 目录清理——均配对完整。

# C6 资源生命周期与泄漏（深挖第 2 轮追加）— nop-stream-state-connectors

## 深挖第 2 轮追加

- **审计维度**: C6 第 2 轮（盲区深挖：第 1 轮未盘点的资源获取点；注册表对称性 register/remove 配对；临时文件清理钩子）
- **基础**: 第 1 轮 `C6-resource-lifecycle.md`（C6-001~009，P0×1 / P1×3 / P2×4 / P3×1），本轮只输出新发现，序号接续
- **盲审纪律**: 本轮仅读取本维度文件与 live code；未读取 `ai-dev/audits/` 下其他审计目录、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`
- **日期**: 2026-10-14；**复核状态**: 全部条目=未复核（第 2 轮初审）

### 第 2 轮盲区覆盖面（盘点补全，非发现）

1. **第 1 轮未盘点的获取点补扫**：`Files.walk/Files.list/Files.newDirectoryStream` 返回的 `Stream<Path>`/DirectoryStream（新发现 → C6-011）；Micrometer gauge 注册（新发现 → C6-010）；rocksdbjni 二级对象（WriteOptions/ReadOptions/FlushOptions/Statistics/SstFileManager/IngestExternalFileOptions/SstFileWriter——全模块 grep 零命中，均不使用，无发现）；Guava Cache 淘汰回调（见下 4，非缺陷）。
2. **注册表对称性复核**：静态可变容器全量 grep 复扫——8 模块内仅 MANIFEST_JVM_LOCKS（C6-009 已登记）、DebeziumEngineWrapper failure-listener（第 1 轮 ✓）两处 static 注册表，无第 3 处；**修正第 1 轮一处误判**：第 1 轮配对表脚注「④ StreamMetricsRegistries（gauge 随后端 close 摘除，✓）」与 live code 不符——`RocksDBMetricsRecorder.close()` 仅 `dbRef.set(null)`，从不摘除 meter，实为 register 无 remove + first-wins（→ C6-010，以本轮为准）。
3. **临时文件清理钩子复扫**：`createTempFile`/`.tmp` 全模块 grep 仅命中 file sink 族（C6-007 已登记）与 manifest tmp（C6-007 失效面②已并入）；`Files.writeString(nonSstDir/...)` 为单次写自动关闭；无新发现。
4. **判定为非缺陷的盲区**（记录排除理由，防止第 3 轮重复）：① `SharedBuffer` Guava LRU 淘汰——write-through（`upsertEvent/upsertEntry` 同写 MapState，state 权威），SIZE 淘汰只丢缓存副本不丢 Lockable 锁（SharedBuffer.java:248-251,340-346），锁生命周期不被缓存淘汰破坏；② TTL 后台清扫——`cleanupExpiredEntries()` 是 `snapshotState()` 内的同步调用（RocksDBKeyedStateBackend.java:782-830），无独立线程/调度器，无线程生命周期问题，且与读写在同一任务线程（无跨线程 native 句柄共享）；③ `checkConnection` 探针全族逐一复核（Kafka source/sink TWR ✓、File sink begin+rollback 纯内存 ✓、JDBC sink begin=内存 init + ledger 连接 finally 关闭 ✓、Debezium=纯配置校验 ✓、BatchLoader loader AutoCloseable 即关 ✓、BatchConsumer=null 检查 ✓）——第 1 轮 ✓ 判定全部成立；④ `BatchConsumerSinkFunction` 构造失败路径——setup() 前置校验先抛、setup() 后无抛点，无构造半程泄漏；⑤ 8 模块无自建线程池/调度器（runner 线程族第 1 轮 C6-002/003 已尽）。

---

### [nop-stream-state-connectors-C6-010] RocksDB gauge 注册到进程级共享 registry 后永不摘除且同名空 tags first-wins——第二个后端起指标静默失效，recorder 随 meter 永久驻留

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:65-67,73-84`；调用点 `RocksDBKeyedStateBackend.java:277-279,988-990`；注册表本体 `nop-stream/nop-stream-core/.../metrics/StreamMetricsRegistries.java:32-41`（进程级 static composite，模块外设施，本轮只审 rocksdb 侧使用方式）
- **证据片段**:
  ```java
  // RocksDBKeyedStateBackend.openDB —— 每个后端 open 都执行：
  metricsRecorder = new io.nop.stream.rocksdb.metrics.RocksDBMetricsRecorder(db);
  metricsRecorder.register(io.nop.stream.core.metrics.StreamMetricsRegistries.registry());  // 进程级单例 registry
  // RocksDBMetricsRecorder —— 6 个 gauge 全部 Tags.empty()，valueFunction 强捕获 this：
  private void gauge(MeterRegistry registry, String meterName, String property) {
      registry.gauge(meterName, Tags.empty(), dbRef, ref -> readProperty(ref.get(), property));
  }
  public synchronized void close() {   // 后端 close 时唯一调用：只置空 dbRef，无任何 meter 摘除
      dbRef.set(null);
  }
  ```
- **严重程度**: P2（注册表 register/remove 不对称的确定性后果：首个后端 close 后全进程 RocksDB 指标永久 NaN、并存后端互相不可见；微量对象永久驻留。非数据正确性/资源耗尽级，故 P2）
- **并发场景**: 任务线程（后端 openDB 注册 / close 摘除引用）与 ops HTTP 抓取线程（scrape 读 gauge）；多作业共享同一 TM 进程时多个后端线程竞争同一 registry 的同名 meter。
- **触发时序**: T1 作业 A 后端 open → 6 个 gauge 以 `nop.stream.state.rocksdb.*` + `Tags.empty()` 注册进 static composite，meter 归 A 的 recorder/dbRefA（Micrometer 按 id first-wins）→ T2 作业 A 失败重启：`close()` 置 `dbRefA=null`，**meter 不摘除** → T3 新后端 open（同进程重启或作业 B）→ `registry.gauge(...)` 命中同名同 tags 的既有 meter → **新 dbRef/新 recorder 被静默丢弃**（Micrometer 既有 meter 直接返回，无日志无异常）→ 此后 scrape 永远读 `dbRefA.get()==null` → NaN，直到进程重启。并存场景：A、B 同时运行时 B 完全不可见且 gauge 显示 A 的数值，观测归因错误。
- **现状**: register 与 remove 完全不对称（F8）：无 `registry.remove(meter)`/`RemoveByTag` 路径；`close()` 语义只有「断开 native 引用」。第 1 轮配对表脚注④的「gauge 随后端 close 摘除，✓」经本轮核实不成立，予以修正。附带驻留面：`DefaultGauge` 对 obj 是 WeakReference，但 valueFunction lambda 强捕获 `this`（recorder）且被 meter map 强持有 → 每次后端 open 泄漏一个 recorder+dbRef+lambda 对象链（约百字节级），进程生命周期内只增不减。
- **风险**: 长驻 TM 上「首次作业重启后 RocksDB 观测永久失效」是确定性事件——容量水位（block cache/memtable usage）、key 规模（estimate-num-keys）、compaction 积压全部失去可见性，且无任何报错；C7 长时运行维度依赖这些 gauge 做劣化判断的口径同时失效。对象驻留本身量级小，但与 C6-009 同属「注册表只增不删」族。
- **建议**: ① `RocksDBMetricsRecorder` 记录 `register()` 返回的 6 个 `Meter.Id`（或 gauge 句柄），`close()` 内 `registry.remove(id)` 先于 `dbRef.set(null)`（顺序保证抓取线程在摘除后读到 NaN 而非已关句柄——与 C6-004 的锁序结论一致）；② 或给 gauge 增加 per-backend 标识 tags（jobId/vertexId/backendId），使并存后端各自有 meter 且重启后 id 唯一——但此路径仍需 remove 否则 meter 按 tag 基数累积，①②应同时做。
- **失效面枚举**（F3——本模块全部 MeterRegistry 注册点逐一核对）: ① `RocksDBMetricsRecorder.register` 6 gauge（本条，未修）；② rocksdb 模块内其余 gauge/counter 注册点——grep `registry\.(gauge|counter|timer)` 仅此一处（✓ 其余为零）；③ nop-stream-core 的 `MicrometerStreamTaskMetrics`/`WindowOperator` 等 metrics 注册（模块外平台设施，不在本审计对象内，登记为相邻疑点待 core 专项审计核对同族形态）。
- **信心水平**: 确定（first-wins 语义是 Micrometer `registerMeterIfNecessary` 的核心契约；`DefaultGauge` 弱引用+强 lambda 的双重语义已对本地 micrometer-core 1.12.8 字节码核实；close 无摘除为静态事实）
- **误报排除**: 不是「平台基础设施自身不重审」——缺陷锚点在 rocksdb 模块的使用方式（空 tags + 每 open 注册 + 不摘除），StreamMetricsRegistries 本体未立案；不是「有界声明存在的缓存」——无任何 owner 文档声明 gauge 与进程共享 registry 的 first-wins 行为；也不是 C6-004 的重复计数——004 报的是抓取与 close 的竞态崩溃窗口，本条报的是注册/摘除不对称导致的静默失效与驻留，机制与修复点不同。
- **复核状态**: 保留 P2（first-wins + close 无摘除经 live code 与 Micrometer 语义复证；理由见文末「维度复核结论」）；二轮复核：保留 P2（6 gauge 全 Tags.empty() 注册进 StreamMetricsRegistries 进程级 static composite、recorder.close 仅 dbRef.set(null) 无 remove、后端 openDB 每 open 重新 register 三点全部复证；first-wins 丢弃新 dbRef 的确定性后果成立）

### [nop-stream-state-connectors-C6-011] Files.walk 返回的 Stream 未关闭（FileSplitEnumerator.discoverSplits / FileSource.sample）——遍历异常中断时目录 fd 永久泄漏（UnixDirectoryStream 无 finalizer/Cleaner 兜底）

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSplitEnumerator.java:142-155`；`nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSource.java:397-410`
- **证据片段**:
  ```java
  // FileSplitEnumerator.discoverSplits（任务启动时一次性枚举）
  List<Path> files = Files.walk(dir)          // L147：Stream<Path> 未在 TWR/显式 close 中
          .filter(Files::isRegularFile)
          .collect(Collectors.toList());
  // FileSource.sample（ops 只读采样，每次调用执行）
  try {
      files = Files.walk(dir).filter(Files::isRegularFile).collect(Collectors.toList());  // L407：同形态，try 不覆盖 Stream 本体
  ```
- **严重程度**: P2（错误路径资源泄漏：happy path 由 JDK FileTreeWalker 穷尽自关兜底，仅遍历中途异常/中断路径泄漏；每次泄漏 1 个目录 fd 且无任何 GC 兜底，不可自愈）
- **并发场景**: 单线程顺序性缺陷（enumerator 线程启动期发现 / ops 调用线程采样），风险面是泄漏而非竞态；泄漏物 = `sun.nio.fs.UnixDirectoryStream` 持有的 native 目录 fd。
- **触发时序**: T1 `discoverSplits()` 执行 `Files.walk` → 打开源目录的 DirectoryStream（native fd）→ T2 遍历中途命中不可读子目录/遍历 IO 错误 → `FileTreeWalker.next()` 抛出（`Files.walk` 包装为 UncheckedIOException）→ `collect()` 异常终止 → 该 Stream 注册的 `onClose(walker::close)` 清理路径因调用方未使用 TWR/显式 `close()` 而**永不执行** → walker 栈内 ≥1 个 DirectoryStream 不可达 → 经 JDK 25 源码核实 `UnixDirectoryStream` 无 finalizer 亦无 Cleaner → fd 泄漏至进程退出。T2'（happy path 反证）：遍历正常穷尽时 `FileTreeWalker.next()` 在「目录耗尽」分支自关 stream（FileTreeWalker.java:337-341），故成功路径无泄漏——缺陷面收敛于异常路径。失败作业的重启循环（T3：重试 discoverSplits 再抛再漏）使泄漏线性累积。
- **现状**: 与本模块其余全部目录遍历点（SnapshotStrategy/Restore 的 6 处 `Files.list/Files.walk` 均为 try-with-resources）形成同仓双标准；两处缺失点分属 job 启动主路径（discoverSplits 抛错直接导致作业起不来，即「每失败重启一次漏一个 fd」）与 ops 采样路径（sample 可被反复调用）。
- **风险**: 目录权限故障+重启风暴的长驻进程场景下 fd 单调累积直至 `ulimit -n`，此时后续任意 socket/file 打开失败（Too many open files），故障表现远离根因难以定位；单次代价小、不可回收是其隐蔽性来源。
- **建议**: 两处改为 `try (Stream<Path> s = Files.walk(dir)) { files = s.filter(...).collect(...); }`（与 SnapshotStrategy:189 现成写法对齐）；顺带统一 sample 的 try 块作用域。
- **失效面枚举**（F3——8 模块全部目录遍历/目录流获取点逐一核对）: ① `FileSplitEnumerator.discoverSplits:147`（本条，未修）；② `FileSource.sample:407`（本条第二点，未修）；③ `RocksDBIncrementalSnapshotStrategy:88,156,189`（TWR，✓）；④ `RocksDBIncrementalRestore:136,191,277`（TWR，✓）；⑤ `FileTwoPhaseCommitSink` 目录写路径（BufferedWriter TWR，无目录流，✓）；⑥ batch/jdbc/debezium/kafka/cep/avro 六模块 grep `Files.(list|walk|find|newDirectoryStream)` 零命中（✓）。2 处破面 + 6 处已修面 + 6 模块无此形态。
- **信心水平**: 确定（缺失 close 为静态事实；happy-path 自关与无-finalizer 两点均对本地 JDK 25 src.zip 逐行核实——FileTreeWalker.java:337-341 穷尽自关、UnixDirectoryStream 无 Cleaner/finalizer 注册）
- **误报排除**: 不是「collect 终端操作会关流」的误报——终端操作不触发 onClose，此为 Stream 契约明文；也不是把 happy path 夸大为必然泄漏——本条已明确把缺陷面收敛到异常路径并据此定 P2 而非 P1；不属「低频启动路径粗粒度问题」的性能误报校准项——这是不可回收的 fd 泄漏面，非性能问题。
- **复核状态**: 保留 P2（两处 Files.walk 均未 TWR/显式 close 经 live code 复证；理由见文末「维度复核结论」）；二轮复核：保留 P2（discoverSplits 与 sample 两处裸用复证成立；happy path 由 FileTreeWalker 穷尽自关、异常中断路径 UnixDirectoryStream 无 finalizer/Cleaner 兜底——缺陷面收敛于异常路径，P2 恰当）

---

## 汇总（第 2 轮追加）

| 严重度 | 数量 | 条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 2 | C6-010 RocksDB gauge 注册无摘除 + first-wins 指标静默失效（修正第 1 轮配对表脚注④）；C6-011 Files.walk Stream 未关闭（异常路径目录 fd 永久泄漏，2 处破面） |
| P3 | 0 | — |

**第 2 轮终止判定**: 补扫后各盲区（rocksdbjni 二级对象、静态注册表第 3 处、临时文件钩子、checkConnection 全族、SharedBuffer 缓存-锁交互、TTL 清扫线程化、模块调度器）均已盘点并有明确结论（非缺陷或已有条目），无剩余未盘点获取点——C6 维度深挖可以收敛。累计（第 1+2 轮）：P0×1 / P1×3 / P2×6 / P3×1。

---

# C6 资源生命周期与泄漏（独立复核结论）— nop-stream-state-connectors

- **复核角色**: 独立复核子代理（与初审/深挖不同任务实例），本轮解除盲审
- **复核方法**: 逐条重开 live code + 调用方独立验证——`KafkaTwoPhaseCommitSink` 全文、`StreamSinkOperator`/`AbstractUdfStreamOperator`/`FunctionUtils`/`StreamFunction`/`SinkFunction`/`TwoPhaseCommitSinkFunction`/`CheckpointParticipant` 继承链、`Task.closeOperatorChains`/`OperatorChain.close` 停机链、`MessageSourceReader`/`MessageSourceFunction`、`DebeziumCdcSourceReader`/`DebeziumCdcSourceFunction`/`DebeziumEngineWrapper`、全部生产 `IMessageService` 实现的 cancel 语义、RocksDB 三文件（backend/recorder/restore）、`FileTwoPhaseCommitSink`、`AvroSchemaRegistryClient`、`StreamMetricsRegistries` 均重新打开核对，未沿用初审证据。解除盲审后用 `ai-dev/audits/` 历史审计与 git log 仅做现状归属标注，不替代技术判定。
- **三态判定汇总**: 保留 9 / 降级 0 / 驳回 2（C6-002、C6-003）。终判严重度分布：P0×1 / P1×1 / P2×6 / P3×1。
- **P0 终裁**: C6-001 维持 P0。

## 维度复核结论

### 保留（9 条）

**C6-001 — 保留，P0 终裁维持**。四点独立证实：(a) 全类无 `close()`/`destroy()`，不实现 `AutoCloseable`/`RichFunction`；模块 main 源 grep `producer.close` 零命中；`rollback()/abort()/recover()` 均只动事务状态不动 producer。(b) 继承链核实 `KafkaTwoPhaseCommitSink → TwoPhaseCommitSinkFunction → SinkFunction → StreamFunction → Serializable`，`CheckpointParticipant` 无 close 钩子——`StreamSinkOperator.close():175` 的 `instanceof AutoCloseable` 分支对该类恒 false，`AbstractUdfStreamOperator.close():139 → FunctionUtils.closeFunction()` 只处理 `RichFunction`，双钩子皆 no-op；core/runtime 全部 `instanceof TwoPhaseCommitSinkFunction` 站点（copyForSubtask/restoreState/checkpoint/probe）无一释放 producer。(c) 正常成功路径证实泄漏：`Task.run()` 全部终态（COMPLETED/CANCELED/FAILED）的 finally → `closeOperatorChains()` → `OperatorChain.close()` → 上述 no-op；producer 的 kafka-producer-network-thread（daemon）强引用 producer，永不 GC。(d) 判级：producer 在首次 invoke（主路径）创建，作业正常 DRAIN 即确定性泄漏「每作业运行 × 每 kafka-2pc 子任务」一个 producer（1 常驻线程 + broker 连接 + 最高 `buffer.memory` 缺省 32MB 缓冲池），精确命中 P0 判级标准「主路径资源泄漏（正常成功路径即泄漏）」；JVM 退出不被阻塞（daemon）只影响进程级致命性，不构成降 P1 依据。

**C6-004 — 保留 P1**。live code 证实 `gauge()` 的 valueFunction 为 `ref -> readProperty(ref.get(), property)`：`ref.get()` 在进入 `synchronized readProperty` 之前求值，「get 到非 null db → close 线程完成 recorder.close+db.close → 持旧引用进 native 读」的交错窗口真实存在，类 Javadoc 自称的「in-flight scrape cannot race the detach」被锁外取参击穿；后果为 `IllegalStateException`（nativeHandle_==0）或 SIGSEGV。解除盲审核对：R6 曾核对「gauge 先于 native handle 分离」的顺序面并判 ✓（r6 04-concurrency-resource.md 头部核对清单），未覆盖本竞态——非重复计数。窗口窄但后果为共享 TM 进程级崩溃，P1 恰当。

**C6-005 — 保留 P2**。`openDB()` 失败路径证实：`dbOptions/cfOptions` 与 memory-control 预设的 Cache/WBM/BloomFilter（`RocksDB.open` 之前分配）、`handles` 半成品在 open 抛错时均无清理，构造器直抛后 close() 不可达，唯一兜底为 rocksdbjni finalizer。现状归属：R6 `02-state-backend-serialization.md` ST-11 已登记（P3），本轮重证成立并将定级从重为 P2（本轮补充了 handles 半成品与重试风暴叠加论证）；live code 未修。

**C6-006 — 保留 P2**。`restoreRangeInto:209-229` 证实 TWR 先关 `src`（DB）后 finally 关 `handles`，违反 rocksdbjni「先关全部 ColumnFamilyHandle 再关 DB」契约，且与同模块 `RocksDBKeyedStateBackend.close()`（handles → defaultCF → db）同仓双标准；restore 为 F-02 声明的常规路径。非必现崩溃故 P2 恰当。现状归属：新发现（历史审计无该锚点）。

**C6-007 — 保留 P2**。孤儿窗口证实：`saveState` 写 `.epoch-N.tmp` 后至 checkpoint durable 之间，`finishCommit` 无 success=false 分支（coordinator abort 不触达 `abort()`），进程崩溃则登记随内存丢失，restore 只能 abort 恢复快照内的条目；全文件仅 `deleteIfExistsQuiet` 一处（位于 abort），无启动清扫。现状归属：R6 `03-connectors-security.md` CON-04 已登记（P3），本轮重证成立并将定级从重为 P2（本轮明确「登记与 durable 之间固有窗口」的触发时序）；live code 未修。

**C6-008 — 保留 P2**。ctor 仅 `connectTimeout(10s)`，`readJsonResponse` 的 `http.send` 未设 `HttpRequest.timeout`，响应阶段无界等待为静态事实；cache miss 点（新 subject/schemaId 首条记录）遇 registry 半死即无限挂起任务线程并滞留其全部资源。现状归属：新发现。

**C6-009 — 保留 P3**。全仓 grep 复证 `MANIFEST_JVM_LOCKS` 仅 `computeIfAbsent` 一个写点、无 remove；上界 = 进程生命周期内不同输出目录基数。现状归属：2026-09-26 `deep-audit-nop-stream-quality/04-concurrency-resources-connectors.md` 已以同锚点登记 [P3]，本轮同级维持；live code 未修。

**C6-010 — 保留 P2**。三点复证：① 6 个 gauge 全部 `Tags.empty()` 且注册进 `StreamMetricsRegistries` 进程级 static composite（同名同 tags 的 meter id 恒等）；② Micrometer 同 id first-wins，第二个后端的 dbRef/recorder 被静默丢弃；③ `recorder.close()` 仅 `dbRef.set(null)`，全类无 `registry.remove`——首个后端 close 后全进程 RocksDB gauge 永久 NaN 为确定性事件。现状归属：同族问题 R6 04-concurrency-resource CC-06（runtime `EngineMetrics` GAUGE_STATE_REFS，P3）已登记，但本条锚点（rocksdb recorder 空 tags + 每 open 注册 + 无摘除）为首次立案，非重复计数；本轮还修正了初审配对表脚注④的误判（recorder close 不摘除 meter）。

**C6-011 — 保留 P2**。`FileSplitEnumerator.discoverSplits:147` 与 `FileSource.sample:407` 两处 `Files.walk` 均未用 TWR/显式 close（grep 复证为模块内仅有的两处裸用；rocksdb snapshot/restore 6 处均 TWR，同仓双标准成立）；Stream 终端操作不触发 onClose、异常中断时 `UnixDirectoryStream` 无 finalizer/Cleaner，happy path 由 FileTreeWalker 穷尽自关——缺陷面收敛于异常路径，P2 恰当。现状归属：新发现。

### 驳回（2 条）

**C6-002 — 驳回（泄漏主链路被证伪）**。结构核实：`queue.put` 的阻塞者是 message-service 投递线程（`MessageSourceFunction.onMessage` 回调线程），而 `close()` join 的是 runner 线程——runner 执行的 `embeddedFunction.run()` 主体是 `while (running && !failed) shutdownLatch.await(1s)` 循环，`cancel()` 置位 + countDown 后 runner ≤1s 退出：`run()` 的 finally（`localSubscription.cancel()`）照常执行、`join(5000)` 不会超时、`queue.clear()` 可达并确定性解救阻塞的 put（腾出空位令 put 返回，投递线程随即回到取消感知的投递循环）。已核全部生产实现：`PollingJdbcMessageService.cancel` 纯置位（`task.cancel(false)` 不阻塞）、`KafkaMessageService.cancel` 阻塞等待但超时即 `shutdownNow()` 中断 put（QueueSourceContext 的 InterruptedException 分支正是为该通道设计）、`DebeziumEngineWrapper.stop()` 为非阻塞 `engine.close()` 信号。原发现「订阅线程卡 put → runner join 超时 → finally 永不执行 → 消费组成员/连接永久泄漏」的链条在现有代码下不成立；残余仅为健壮性附注（join 超时抛错会跳过 clear、无 interrupt 桥），不构成 P1 立案面。现状归属：同日 core-runtime C8 轮（2026-10-14-0130）已将这两个 reader 队列显式移交本模块审计轮，本轮首次立案、技术驳回。

**C6-003 — 驳回（同根因，随 C6-002 一并证伪）**。`DebeziumCdcSourceReader.close()` join 的 runner 线程执行 `DebeziumCdcSourceFunction.run()`，主体为 `completionLatch.await(1s)` 循环；`cancel()`（countDown + `sub.cancel()` + `msgSource.stop()` → `DebeziumEngineWrapper.stop()` = `engine.close()` 非阻塞信号）令 runner ≤1s 退出，finally 三连释放（unregister listener → subscription.cancel → source.stop）照常执行、`queue.clear()` 可达并解救引擎投递线程的阻塞 put；投递线程返回后引擎循环感知 close 信号优雅退出并释放 binlog 连接。「引擎线程 + 嵌入引擎 + 源库连接泄漏」不成立。interrupt 桥 + clear 提前至 finally 可留作健壮性改进建议，不作 C6 发现保留。

## 最终保留项

| 编号 | 终裁严重度 | 三态 | 现状归属 | 锚点（复核确认） |
|---|---|---|---|---|
| C6-001 | P0 | 保留 | 新发现（本轮首次；kafka-2pc 为 plan 2335 WI3 新连接器） | `nop-stream-connector-kafka/.../KafkaTwoPhaseCommitSink.java`（全文无 close）+ `StreamSinkOperator.java:175`/`FunctionUtils.closeFunction` 双钩子不触达 |
| C6-004 | P1 | 保留 | 新发现（R6 曾核对 recorder close 顺序面判 ✓，未覆盖锁外取参窗口） | `nop-stream-rocksdb/.../metrics/RocksDBMetricsRecorder.java:82-101` |
| C6-005 | P2 | 保留 | 历史已登记：R6 02-state-backend ST-11（P3）→ 本轮 P2 从重；未修 | `nop-stream-rocksdb/.../RocksDBKeyedStateBackend.java` openDB 失败路径 |
| C6-006 | P2 | 保留 | 新发现 | `nop-stream-rocksdb/.../incremental/RocksDBIncrementalRestore.java:209-229` |
| C6-007 | P2 | 保留 | 历史已登记：R6 03-connectors-security CON-04（P3）→ 本轮 P2 从重；未修 | `nop-stream-connector/.../file/FileTwoPhaseCommitSink.java` saveState/abort |
| C6-008 | P2 | 保留 | 新发现 | `nop-stream-format-avro/.../AvroSchemaRegistryClient.java:48,113-119` |
| C6-009 | P3 | 保留 | 历史已登记：2026-09-26 deep-audit-nop-stream-quality 04-concurrency-resources-connectors（P3 同级）；未修 | `nop-stream-connector/.../file/FileTwoPhaseCommitSink.java:96-99,389-392` |
| C6-010 | P2 | 保留 | 同族已登记、锚点新：R6 04-concurrency-resource CC-06（runtime EngineMetrics，P3）；rocksdb recorder 锚点首次 | `nop-stream-rocksdb/.../metrics/RocksDBMetricsRecorder.java` + core `StreamMetricsRegistries` |
| C6-011 | P2 | 保留 | 新发现 | `nop-stream-connector/.../file/FileSplitEnumerator.java:147` + `FileSource.java:407` |

**现状归属统计**：新发现 5（C6-001/004/006/008/011）；同族已登记、锚点新 1（C6-010 ← R6 CC-06）；历史已登记 3（C6-005 ← R6 ST-11、C6-007 ← R6 CON-04、C6-009 ← 2026-09-26 审计）。历史登记项经 live code 复核均未修复（缺陷形态仍在），其中 C6-005/C6-007 本轮较历史 P3 从重定级为 P2；驳回 2 条（C6-002/003）的残余健壮性观察（close 的 join 超时抛错跳过 clear、无 interrupt 桥）随本节记录，不立案。

---

# C6 资源生命周期与泄漏（二轮独立复核）— nop-stream-state-connectors

- **复核角色**: 二轮独立复核子代理（与一审/深挖/一轮复核均不同任务实例），审计单元=nop-stream-state-connectors，覆盖全部 14 条发现（C6-001~011 主文件 + C6-012~014 C6-round2.md）
- **复核方法**: 逐条重开 live code（HEAD=concurrency-audit-remediation 分支）独立验证，未沿用前轮证据：`KafkaTwoPhaseCommitSink` 全文、`TwoPhaseCommitSinkFunction`/`SinkFunction`/`StreamFunction`/`CheckpointParticipant`/`FunctionUtils`/`StreamSinkOperator`/`AbstractStreamOperator`/`AbstractUdfStreamOperator` 继承链与关闭钩子、`Task.run` finally→`closeOperatorChains`→`OperatorChain.close` 与 `StreamTaskInvokable.closeChainAndGate` 两条 teardown 链、`MessageSourceReader`/`MessageSourceFunction`/`KafkaMessageService`/`PollingJdbcMessageService`、`DebeziumCdcSourceReader`/`DebeziumCdcSourceFunction`/`DebeziumMessageSource`/`DebeziumEngineWrapper`、`RocksDBKeyedStateBackend`（构造/openDB/close）、`RocksDBMetricsRecorder`、`RocksDBIncrementalRestore`、`FileTwoPhaseCommitSink`、`AvroSchemaRegistryClient`、`FileSplitEnumerator`/`FileSource`、`StreamMetricsRegistries`、`CepOperator`/`NFA` 均重新打开核对。解除盲审后历史审计（R3/R4/R6、core-runtime 轮）与 git log 仅用于现状归属标注，不替代技术判定。
- **三态判定汇总（二轮终判）**: 保留 12 / 降级 0 / 驳回 2（C6-002、C6-003，维持一轮驳回）。终判严重度分布：**P0×1 / P1×1 / P2×6 / P3×4**（与一轮复核结论一致；二轮新增对 C6-012/013/014 的首轮判定，全部保留原判级）。
- **P0 终裁（二轮）**: C6-001 维持 P0，成立。

## 维度复核结论（独立复核子代理）

### 逐条判定表（14 条全覆盖）

| 编号 | 原判级 | 二轮判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C6-001 | P0 | 保留 P0 | 关闭路径四点独立重证全部成立（见下专项段），量级支持 P0 | 新发现（kafka-2pc 为 ae0217fc14/plan 2335 WI3 新连接器）；未修 |
| C6-002 | P1 | 驳回（维持） | runner=await 循环线程≠put 阻塞线程，join 不超时、clear 可达，泄漏链证伪 | 一轮驳回；二轮维持（残余健壮性附注不立案） |
| C6-003 | P1 | 驳回（维持） | engine.stop=非阻塞 engine.close()，runner ≤1s 退出，三连释放与 clear 均可达 | 一轮驳回；二轮维持 |
| C6-004 | P1 | 保留 P1 | `ref.get()` 锁外求值击穿 synchronized 守卫，L83 复证 | 新发现（R6 曾核对 close 顺序面判 ✓，未覆盖本窗口）；未修 |
| C6-005 | P2 | 保留 P2 | openDB 失败路径无清理复证，同方法内 Options 有 TWR 双标准 | 历史已登记：R6 ST-11（P3）→ 本单元 P2 从重；未修 |
| C6-006 | P2 | 保留 P2 | TWR 先关 src 后关 handles 逆序复证，与 backend.close() 双标准 | 新发现；未修 |
| C6-007 | P2 | 保留 P2 | 孤儿窗口（登记后即死）与无清扫复证 | 历史已登记：R6 CON-04（P3）→ 本单元 P2 从重；未修 |
| C6-008 | P2 | 保留 P2 | 仅 connectTimeout、send 无 request.timeout 复证 | 新发现；未修 |
| C6-009 | P3 | 保留 P3 | computeIfAbsent 唯一写点、无 remove 复证 | 历史已登记：2026-09-26 04-concurrency-resources-connectors（P3 同级）；未修 |
| C6-010 | P2 | 保留 P2 | 空 tags first-wins + close 无摘除三点复证 | 同族已登记、锚点新：R6 CC-06（runtime EngineMetrics，P3）；rocksdb recorder 锚点首次；未修 |
| C6-011 | P2 | 保留 P2 | 两处 Files.walk 裸用复证，缺陷面收敛于异常路径 | 新发现；未修 |
| C6-012 | P3 | 保留 P3（本轮首判） | 每 open 注册 counter、close 无 remove、identityHashCode 回退三点复证 | 同机制面已登记（core-runtime C7-012 P2/C5-008 P3）；本条=R4 R5-CEP-03（P3）经 2bb7c013a7 修复打 tags 后的残留面，cep 锚点首次；未修 |
| C6-013 | P3 | 保留 P3（本轮首判） | close 顺序洞 + NFA 首抛中断复证；定时器假说驳论复核成立 | 新发现（R4 登记过共享条件实例并发 close 的另一面）；未修 |
| C6-014 | P3 | 保留 P3（本轮首判） | 注释-only 空 catch 零观测复证 | 新发现（R6 CON-04 仅提及清理点存在；R6 卫生扫描「空 catch=0」与本点相左）；未修 |

**不确定项**：无阻断性不确定项。两点固有不确定性已被原条目自行声明且不影响判级：C6-006 的实际崩溃形态依赖 rocksdbjni 版本行为（P2 仅按顺序违约立案）；C6-004 的触发依赖 ops 抓取与后端关闭并发（P1 按窗口存在性 + 进程级崩溃后果立案）。

### P0（C6-001）验证过程专段

二轮按指定五点独立、彻底重证：

1. **创建点与全部关闭点**：producer 唯一长驻创建点为 `ensureTransactionOpen()`（KafkaTwoPhaseCommitSink.java:154-156，`producer = newProducer()`），由 `invoke()`（:168-169，主路径首条记录）与 `beginTransaction()`（:176-178）触达。全类无 `close()`/`destroy()` override（全文 323 行核对），不实现 `AutoCloseable`/`RichFunction`（类声明 :77 仅 `extends TwoPhaseCommitSinkFunction implements ConnectivityCheckable`）；模块 main 源 grep `producer.close` 零命中。`rollback()`（:234-239）/`abort()`（:247-249）/`recover()`（:276-280）均只动事务状态不动 producer。probe 路径（`checkConnection`:306-312）producer 为 TWR，确认干净、非第二泄漏点。
2. **基类关闭钩子**：`TwoPhaseCommitSinkFunction`（nop-stream-core）= `implements SinkFunction, CheckpointParticipant`（:28），无任何 close/生命周期释放钩子；`SinkFunction.finish()` 为 default no-op 且本类未 override；`CheckpointParticipant` 四方法（saveState/prepareCommit/finishCommit/restoreFromEpoch）均非释放钩子；`StreamFunction` 仅 extends `Serializable`。
3. **运行时调用链**：`StreamSinkOperator.close()`（:172-178）仅对 `instanceof AutoCloseable` 的 userFunction 调 close——本类恒 false；`AbstractUdfStreamOperator.close()`→`FunctionUtils.closeFunction` 只处理 `RichFunction`——no-op；`AbstractStreamOperator.close()` 只关 keyedStateBackend。运行时全部 `instanceof TwoPhaseCommitSinkFunction` 站点（StreamSinkOperator copyForSubtask/restoreState、StreamGraphGenerator、StreamConnectorCatalog、StreamConnectivityProber、CheckpointPlanBuilder、GraphModelCheckpointExecutor）无一释放 producer。两条任务 teardown 链——`Task.run()` 全终态（COMPLETED/CANCELED/FAILED）finally→`closeOperatorChains()`→`OperatorChain.close()`，及运行时 `StreamTaskInvokable.closeChainAndGate`→`operatorChain.close()`——均只到达上述 no-op；runtime 侧 grep 无其他 producer 关闭点。
4. **裁定**：全部关闭路径缺位证实，P0 成立。正常成功路径即泄漏：producer 在主路径首次 invoke 创建，作业 DRAIN/CANCEL/FAILED 任一终态均不关闭。
5. **量级评估（支持 P0）**：每个泄漏 producer = 1 条常驻 `kafka-producer-network-thread`（daemon，KafkaProducer 构造器启动、仅 close() 可终止，线程强引用 NetworkClient/RecordAccumulator/TransactionManager，相关资源永不 GC）+ 到 broker 的 TCP 连接 + 至多 `buffer.memory`（缺省 32MB）的缓冲池上限（barrier flush 后常驻量小，但池与连接常驻）+ 进程内 producer 对象链。同 jobId 重启时新一代 `initTransactions()` 仅在 broker 侧 fence 旧代（事务状态不堆积），但进程内旧 producer 的线程/连接不受影响——长驻 TM 进程内按「作业运行 × kafka-2pc 子任务」单调累积成立。JVM 退出不被阻塞（daemon 线程）只影响进程级致命性，不构成降级依据：P0 判级口径为「主路径资源泄漏（正常成功路径即泄漏）」，精确命中。反驳尝试（coordinator 终态 abort/rollback、SUSPEND 停源、runtime 侧兜底）均无 close 语义，无法推翻。

### 驳回项（2 条，维持一轮）

**C6-002/C6-003 — 二轮维持驳回**。独立重证一轮复核的证伪链：(a) 结构事实——两个 reader 的 runner 线程执行的是嵌入函数的 `run()` 主体等待循环（`MessageSourceFunction`:182-184 `shutdownLatch.await(1s)`；`DebeziumCdcSourceFunction`:283-290 `completionLatch.await(1s)`），而 `queue.put` 阻塞发生在 message-service/Debezium 引擎的**投递线程**（`onMessage`→`ctx.collect` / 引擎 dispatcher→collect），runner 从不进入 put；(b) `cancel()` 为 countDown+置位+订阅/引擎非阻塞停止信号（`DebeziumEngineWrapper.stop()`=`engine.close()` 非阻塞，wrapper:194-213），runner ≤1s 退出；(c) 故 `close()` 的 `join(5000)` 不超时、`queue.clear()`（ArrayBlockingQueue clear→notFull.signalAll）可达并解救阻塞 put，投递线程随即经取消感知路径退出——`PollingJdbcMessageService.cancel` 纯置位（PollingJdbcMessageService.java:341-344）、`KafkaMessageService.cancel` 5s 超时后 `shutdownNow()` 中断兜底（KafkaMessageService.java:578-593）——原发现「线程+订阅/引擎+源库连接永久泄漏」的链条不成立。残余仅为健壮性附注（join 超时抛错会跳过 clear、无 interrupt 桥、KafkaMessageService.cancel 最长 ~6s 的 close 延迟），随一轮复核节记录，不立案。

## 最终保留项

| 编号 | 终判严重度 | 文件路径 | 一句话摘要 | 现状 |
|---|---|---|---|---|
| C6-001 | **P0** | `nop-stream/nop-stream-connector-kafka/src/main/java/io/nop/stream/connector/kafka/KafkaTwoPhaseCommitSink.java`（全文，创建点 :154-156）+ 框架钩子 `nop-stream-core/.../operators/StreamSinkOperator.java:172-178` | kafka-2pc sink 的 KafkaProducer 在所有任务终止路径永不关闭（正常成功路径即泄漏：每作业每子任务 1 daemon 线程+连接+缓冲池） | 新发现（plan 2335 WI3 连接器）；未修——P0 即时通道修复对象 |
| C6-004 | P1 | `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:82-101` | gauge valueFunction 锁外取 `ref.get()`，scrape 与后端 close 竞态可对已关 native 句柄读属性（SIGSEGV 窗口） | 新发现；未修 |
| C6-005 | P2 | `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java`（openDB 失败路径） | RocksDB.open 抛错时 dbOptions/cfOptions/Cache/WBM/BloomFilter/handles 半成品全部无清理，仅 finalizer 兜底 | R6 ST-11（P3）→ 从重 P2；未修 |
| C6-006 | P2 | `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalRestore.java:209-230` | restoreRangeInto TWR 先关 src DB 后 finally 关 ColumnFamilyHandles——违反 rocksdbjni 关闭顺序（use-after-free 风险） | 新发现；未修 |
| C6-007 | P2 | `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java`（saveState :245-264 / abort :473-481） | checkpoint 未 durable 即失败时 epoch temp 文件成永久孤儿（登记只在内存、恢复只 abort 快照内条目、无启动清扫） | R6 CON-04（P3）→ 从重 P2；未修 |
| C6-008 | P2 | `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java:48,113-129` | HTTP 请求无响应超时（仅 connectTimeout），registry 半死时 cache-miss 首条记录无限期挂起任务线程 | 新发现；未修 |
| C6-009 | P3 | `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:96-99,389-392` | MANIFEST_JVM_LOCKS 静态注册表只增不删（上界=输出目录基数，量级无害） | 2026-09-26 审计同锚点 P3；未修 |
| C6-010 | P2 | `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:65-84` + core `StreamMetricsRegistries` | 6 gauge 空 tags 注册进程级 registry 后永不摘除——首后端 close 后全进程 RocksDB 指标永久 NaN、并存后端互不可见 | 同族已登记（R6 CC-06）、rocksdb 锚点首次；未修 |
| C6-011 | P2 | `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSplitEnumerator.java:146-148` + `FileSource.java:407` | 两处 Files.walk 返回的 Stream 未关闭——遍历异常中断时目录 fd 永久泄漏（无 finalizer/Cleaner 兜底） | 新发现；未修 |
| C6-012 | P3 | `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:379-387,438-441,683-691` | late-drop counter 注册后无摘除：meter 按 (operator,subtask) 标签基数缓增、同身份重启跨代计数合并、无位置实例按 identityHashCode 每实例一枚 | R5-CEP-03 修复（2bb7c013a7）后的残留面，cep 锚点首次；未修 |
| C6-013 | P3 | `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:683-691` + `nfa/NFA.java:206-214` | close 链中间异常无 suppressed 收集：super.close()（可重抛 native 错误）最先执行，异常时定时器取消与 NFA 条件函数关闭被跳过；NFA.close 首抛中断后续条件 | 新发现；未修 |
| C6-014 | P3 | `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:683-689,473-481` | deleteIfExistsQuiet 空 catch 静默吞 IOException——abort 删除失败零观测（同仓清理路径均有 WARN） | 新发现；未修 |

**二轮现状归属统计**（保留 12 项，全部经 live code 复证为未修复）：新发现 7（C6-001/004/006/008/011/013/014）；同族/残留面已登记、锚点新 2（C6-010 ← R6 CC-06；C6-012 ← core-runtime C7-012/C5-008 + R4 R5-CEP-03 修复残留）；历史已登记 3（C6-005 ← R6 ST-11、C6-007 ← R6 CON-04、C6-009 ← 2026-09-26 审计），其中 C6-005/C6-007 较历史 P3 从重为 P2。驳回 2（C6-002/003，一轮驳回二轮维持）。

**需子项复核的高风险项**：无。C6-001 已终裁成立，直接进入 P0 即时通道修复流程（修复后按惯例由独立 closure audit 验证关闭路径与幂等性）；其余保留项均为 P1-P3 常规整改面，无需额外子项复核。
