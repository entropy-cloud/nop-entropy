# C8 背压·流控·过载行为 初审报告 — nop-stream-state-connectors

- **审计维度**: C8（背压、流控与过载行为）
- **审计对象**: `nop-stream/nop-stream-cep`、`nop-stream/nop-stream-connector`、`nop-stream-connector-batch`、`nop-stream-connector-debezium`、`nop-stream-connector-jdbc`、`nop-stream-connector-kafka`、`nop-stream/nop-stream-rocksdb`、`nop-stream/nop-stream-format-avro`（8 模块 `src/main/java`，合计 140 files / 25,458 lines）
- **定向缺陷族**: F2（过载拒绝「半个成功」）+ F5（挂死不可见）+ F7（OOM 同族）
- **盲审纪律**: 本报告为初审（blind review），未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何既有文件；全部结论仅基于当前 live code + owner 文档（`docs-for-ai/03-modules/nop-stream.md`、`nop-stream-connectors.md`、`nop-stream-user-guide.md` 检索面）。
- **日期**: 2026-10-14
- **复核状态**: 已复核（2026-10-14 维度复核 + 2026-10-14 二轮独立复核，逐条判定见文末「维度复核结论（独立复核子代理 · 二轮复核）」）

---

## 1. 检查范围与方法

逐文件通读了 8 模块中全部承载生产-消费边界、批缓冲、过载语义的类（其余 model/builder/NFA 数学/序列化辅助类按 C8 相关性抽查）：

- **CEP**: `CepOperator`（全 1510 行）、`SharedBuffer`、`SharedBufferAccessor`（抽查）、`SharedBufferCacheConfig`、`NopCepConfigs`、`NFA`（advanceTime/释放路径定向）。
- **connector 族**: `MessageSourceReader`、`MessageSourceFunction`、`MessageSinkFunction`、`MessageTopicSplitEnumerator`、`FileSourceReader`、`FileSource`（参数面）、`FileSplitEnumerator`、`FileTwoPhaseCommitSink`（数据路径）、`BatchLoaderSource`/`BatchLoaderSourceReader`/`BatchLoaderSourceFunction`（run 循环）、`BatchConsumerSinkFunction`、`DebeziumCdcSourceReader`、`DebeziumCdcSourceFunction`、`DebeziumTableSplitEnumerator`、`JdbcTwoPhaseCommitSink`（全 1161 行）、`KafkaSourceFunction`、`KafkaTwoPhaseCommitSink`。
- **RocksDB**: `RocksDBKeyedStateBackend`（全 1068 行）、`RocksDBOptionConfig`、`RocksDBSnapshotSerDe`（snapshot 路径）、`RocksDBIncrementalSnapshotStrategy`（全）。
- **format-avro**: `AvroSchemaRegistryClient`、`AvroWireFormat`（全）。
- **边界接口上下文**（仅作交互对照，不作为发现对象）: `nop-stream-core` `TwoPhaseCommitSinkFunction`（pendingCommits 契约）、`SourceRateLimiter` 使用点；`nop-message-debezium` `NopStreamOffsetBackingStore`/`DebeziumEngineWrapper`（offset 写入时机）。
- `_` 前缀生成文件（`_gen/*Model.java` 等）未作为发现对象。
- 平台设施对照：8 模块内 **未使用** `QueueOverflowPolicy`/`ISemaphore`/`HighWatermarkSemaphore`/`IBlockingQueue`/commons `IRateLimiter`；有界队列全部为原生 `ArrayBlockingQueue`（阻塞 put），限速统一走 nop-stream-core `SourceRateLimiter`（token bucket，monitor 外有界分片睡眠）——使用方式本身无许可泄漏面（无 acquire/release 配对语义）。

## 2. 生产-消费边界清单

| # | 边界 | 生产者 | 消费者 | 有界性 | 满时行为 | 判定 |
|---|---|---|---|---|---|---|
| B1 | message push→pull 桥 | 订阅线程 `queue.put`（`MessageSourceReader.QueueSourceContext`） | 任务线程 `pollNext` | `ArrayBlockingQueue(256)` | 阻塞 put = 背压（声明语义，与 legacy 同步 collect 等价） | 合格 |
| B2 | debezium 引擎→reader 桥 | 引擎线程 `queue.put`（`DebeziumCdcSourceReader.QueueSourceContext`） | 任务线程 `pollNext` | `ArrayBlockingQueue(256)` | 阻塞 put → 引擎内部队列（Debezium 自身 max.queue.size/max.batch.size）反压连接器线程 | 合格（但见 C8-001：offset 与 emission 的偏斜） |
| B3 | kafka poll→管线 | `KafkaConsumer.poll(200ms)` → `ctx.collectWithTimestamp` | 下游算子（同步 collect 阻塞） | poll 批 ≤ `max.poll.records`（client 默认 500，consumerProps 可覆盖、无上限校验=已声明配置面）；offset 在 collect 返回后才 `markConsumed` | collect 阻塞 = 天然背压；可选 `SourceRateLimiter` 节流（bounded-slice 睡眠） | 合格 |
| B4 | file 读→管线 | `FileSourceReader.pollNext` 单行发射 | 下游算子 | 行聚合 `maxLineBytes`（默认 16MB，超限 typed fail-fast，R5-CON-07）；split 字节封顶（AR-15-③）；`SourceRateLimiter` 限速即背压 | 超限 typed fail-fast，不静默截断 | 合格（教科书级） |
| B5 | batch-loader→管线 | `loader.load(batchSize)` 增量批 | `pollNext` 单条发射 | 每次 load ≤ batchSize；cursor 无全量物化 | EOS typed skip-shortfall（AR-13） | 合格 |
| B6 | 管线→batch-consumer sink | `consume()` | `flush()` 批写 | 缓冲 ≤ batchSize（默认 100，满即 flush） | flush 失败 → 保留缓冲 + typed 抛出（任务失败重试）；close 失败显式「data may be lost」 | 合格 |
| B7 | 管线→JDBC 2PC sink | `invoke()` 缓冲 | barrier `saveState`→`commit`（分段 executeBatch ≤ maxBatchSize=1000） | 提交侧有界分段；**缓冲侧（currentBuffer）无上界**（见 C8-003） | commit 失败整体回滚（单事务，无半个成功） | 提交侧合格 / 缓冲侧缺陷 |
| B8 | 管线→file 2PC sink | `invoke()` 缓冲 | barrier `saveState`（写 tmp+fsync）→`commit`（ATOMIC_MOVE） | **currentBuffer 无上界**（同 C8-003）；saveState 后数据落盘有持久化屏障（R5 CON-04） | 恢复幂等（provenance 判别） | 缓冲侧缺陷（同族） |
| B9 | 管线→kafka 2PC sink | `invoke()` → `producer.send` | barrier `preCommit` flush + `saveState` commitTransaction | producer 内部缓冲 `buffer.memory`（默认 32MB）+ `max.block.ms`（默认 60s）超时 typed 失败 | send 阻塞→超时失败 = 有界拒绝（非静默） | 合格（对照组） |
| B10 | CEP 入口→匹配缓冲 | `processElement.bufferEvent`（per-key MapState<Long,List<IN>>） | watermark/processing-time 驱动 drain | **无上界、无过载行为、无深度观测**（见 C8-002） | watermark 停滞 = 无界积压直至 OOM | 缺陷 |
| B11 | CEP NFA→SharedBuffer | `registerEvent`（per-key events/entries MapState + 1024-slot Guava LRU cache） | match 剪枝 / window 超时释放 | cache 有界（1024/1024）；**backing 状态无上界**——无 `within` 的 pattern 部分匹配永不释放（Flink 同款固有语义） | — | 边界（并入 C8-002 注记） |
| B12 | RocksDB 写路径 | 状态 put（任务线程） | memtable flush/compaction | 每 CF writeBufferSize=64MB；**总量默认无 cap**（memory-control preset 默认全 0 = opt-in）；写 stall（L0 阈值/memtable 满）时 put 阻塞 → 任务线程阻塞 → 背压自然传播 | stall 阻塞即背压（RocksDB 默认语义） | 合格（preset 默认关闭见 C8-005 注记） |
| B13 | RocksDB checkpoint | `snapshotState()`（任务线程同步） | checkpoint 存储 | 全量 JSON 路径**整库物化进堆**（见 C8-005）；增量路径 opt-in 且有 F-02 滚动保留（K=2） | — | 缺陷（C5 交叠） |
| B14 | Avro 编解码→registry | `encode`/`decode`（每记录热路径） | Schema Registry HTTP | per-schema-id/per-subject 缓存**无上限**（键空间=registry schema 数，实际有限；负查询不缓存）；HTTP **无 request timeout**（见 C8-004） | registry 挂起 → 编解码无限阻塞 | 缺陷 |
| B15 | enumerators（file/debezium/message/batch） | 目录扫描/表清单/token | reader 分配 | file=部署期一次性发现（有界）；debezium/table、message token、batch 单 split 均有界 | — | 合格 |

F2（过载拒绝「半个成功」）定向结论：JDBC 2PC commit 为单事务（数据+台账原子，失败整体回滚）无半个成功；file 2PC saveState 先 fsync 再 durable + 恢复 provenance 判别；kafka-2pc 残余重复窗口已文档化钉定（EXPERIMENTAL 豁免）；batch-consumer flush 失败保留缓冲整体重试。**唯一实质半个成功形态 = C8-001（source 侧 offset 先于 emission 持久化，恢复语义静默丢数据）**。

---

## 3. 发现条目

### [nop-stream-state-connectors-C8-001] Debezium FLIP-27 reader 的 checkpoint offset 可领先于已发射记录（队列偏斜），崩溃恢复静默丢失 ≤256 条/检查点

- **文件**: `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceReader.java:56-57, 255-279`；关联 `DebeziumCdcSourceFunction.java:376-391`
- **证据片段**:
  ```java
  // DebeziumCdcSourceReader.java
  static final int QUEUE_CAPACITY = 256;                     // L57：引擎→reader 有界队列
  ...
  public List<DebeziumTableSplit> snapshotState(long checkpointId) throws Exception {
      ...
      if (embeddedFunction != null) {
          var snapshot = embeddedFunction.snapshotState(checkpointId);   // L261：直接抬升引擎侧 offset
          Object raw = snapshot.getOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY);
          if (raw instanceof Map) {
              Map<String, String> map = (Map<String, String>) raw;
              offsets = map;                              // L266：offset 真相 = 引擎 store 当前值
          }
      }
      ...
      for (DebeziumTableSplit split : adoptedSplits.values()) {
          splits.add(split.withOffsetMap(offsets));       // L273：无任何「已发射位点」回退/台账
      }
  ```
- **严重程度**: P1
- **并发场景**: Debezium 引擎线程（runEmbedded 守护线程，负责向 B2 有界队列 `queue.put` 并按引擎 offset commit 策略把 offset 刷入 `NopStreamOffsetBackingStore`）与任务线程（`pollNext` 逐条消费队列、`snapshotState` 在 barrier 处理中读取 store）。
- **触发时序**: T1（引擎线程）向队列 put 记录 r(N)…r(N+255)（下游慢/背压，`pollNext` 消化不及，队列近满）；引擎的 offset commit（periodic/batch，处理程序返回即视为已投递）把 r(N+255) 的 offset 刷入 store。T2（任务线程）barrier 到达，`snapshotState` 原样抬升 store → checkpoint 记录 offset = N+256，而此刻实际已向下游发射的记录止于 r(N-k)。T3：checkpoint durable 后进程崩溃/任务失败，恢复端以 offset N+256 重建引擎（`launchEngine` → `initializeState` 播种），队列（transient）连同其中 ≤256 条未发射记录一起消失 → 这些记录被跳过且无任何错误——REPLAYABLE 声明下的静默丢失。
- **现状**: `snapshotState` 把「引擎已投递进队列」的 offset 当作「已安全发射」的 offset 快照，二者之间横着 256 容量的 volatile 缓冲，无 ledger、无 last-emitted 回退、无恢复期重叠重放。legacy 直连形态（collect 同步到下游）偏斜窗口 ≈1 条，FLIP-27 队列桥把它放大到 QUEUE_CAPACITY。
- **风险**: 下游背压（恰是本维度主题）越大，每个检查点暴露在丢失窗口内的记录越多；声明 REPLAYABLE 的 CDC 管线在 kill-recover 场景静默丢变更（上游为数据库 binlog 时 = 数据面语义违约），且完全无观测信号。
- **建议**: 在 reader 侧维护 last-emitted 位点并在 `snapshotState` 时将 engine offset 回退/截断到已发射边界（参照 Kafka 侧「`markConsumed` 在 collect 返回之后」的正确顺序——但 ChangeEvent 不携 per-record offset，需经 `DebeziumSourceSupport`/engine wrapper 暴露 per-record offset 或在恢复期显式 rewind 一个安全重叠量并依赖下游幂等）；至少在文档把该窗口声明为残余边界并加队列深度 gauge。修复落点优先复用 core 侧 source 位点协议，避免改 nop-message-debezium（越界模块）。
- **失效面枚举**: ① `DebeziumCdcSourceReader.snapshotState`——本条主缺陷；② `DebeziumCdcSourceFunction.snapshotState`（legacy 程序化 API 路径）——collect 直连下游，偏斜 ≈1 条，同机制小窗口，随①一并修复；③ `KafkaSourceFunction.run`——`markConsumed` 在 `ctx.collectWithTimestamp` 返回后执行（:279-285），offset 严格跟随 emission，**无此缺陷（对照正例）**；④ `FileSourceReader.pollNext`——光标推进与发射同 monitor（AR-15-②），**无此缺陷**；⑤ `BatchLoaderSourceReader.pollNext`——`currentOffset++` 在返回前、skip 语义显式，**无此缺陷**；⑥ `MessageSourceReader`——recovery=NONE 无 offset 参与，**不适用**。
- **信心水平**: 很可能（reader 侧结构性偏斜确凿；engine 侧 offset 刷新的确切时点在 nop-message-debezium/Debezium 引擎内，本次未深挖到逐行证据——若引擎 offset 仅在下游 ack 后推进则降级，但 Debezium 嵌入式引擎的 commit 策略以「处理程序返回」为投递完成语义，与队列化 emission 不对齐）。
- **误报排除**: 非「已声明边界」——owner doc 对 debezium-cdc 声明的是 REPLAYABLE/kill-recover 可重放（`nop-stream-connectors.md` 能力矩阵），未声明该丢失窗口；非「有界缓存即合格」——问题不是队列有界，而是 offset 真相越过队列边界。也非通用 Flink 语义：Flink Debezium source 以 barrier 对齐 + emitted-ledger 回退 offset，正是本条缺失的机制。
- **复核状态**: 已保留（P1 维持，2026-10-14 复核）——reader 侧 `snapshotState` 直抬引擎 offset、无 last-emitted 回退逐行证实（DebeziumCdcSourceReader.java:255-279）；engine 侧「很可能」疑点已裁决：`DebeziumEngineWrapper.java:154` 以 `notifying(Consumer)` 形态建引擎，Debezium EmbeddedEngine 对该形态在 handler 返回（即 `queue.put` 成功）后即 markProcessed/批末 markBatchFinished 推进 offset store——offset 推进先于下游发射，偏斜窗口=队列在途（≤256）结构性成立，信心升「确定」。触发时序具体（背压→checkpoint 抬升→崩溃→静默跳过）。现状：新发现，无历史先例（plan 2343 M-2/M-4 未考虑队列桥偏斜）；二轮复核：已保留（P1 维持，2026-10-14 二轮独立复核）——丢失上界独立复推成立：引擎 handler 返回（queue.put 成功）即推进 offset store（DebeziumEngineWrapper.java:154 `.notifying(Consumer)` + DebeziumMessageSource.java:143 `dispatchEvent` 接线逐行复核），队列在途 ≤256 + handler 在途 1 条；恢复播种链（DebeziumCdcSourceReader.java:107-123 `initializeState`）复核成立；git log 8 模块无修复提交、历史审计「偏斜」关键词零命中 = 新发现未修复

### [nop-stream-state-connectors-C8-002] CEP 匹配缓冲（elementQueueState / SharedBuffer backing 状态）无上界、无过载行为、无深度观测——watermark 停滞即无界积压

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:876-915, 1041-1050`；`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:314-350`
- **证据片段**:
  ```java
  // CepOperator.java —— 入口只判「晚到」，不判「积压」
  if (timestamp > internalTimerService.currentWatermark()) {
      bufferEvent(value, timestamp);          // L908：无容量检查、无拒绝/降级分支
  } else if (lateDataOutputTag != null) {
      output.collect(lateDataOutputTag, element);
  } else {
      numLateRecordsDropped.increment();      // L912：只有「晚到」可观测
  }
  ...
  private void bufferEvent(IN event, long currentTime) throws Exception {
      List<IN> elementsForTimestamp = elementQueueState.get(currentTime);
      if (elementsForTimestamp == null) {
          elementsForTimestamp = new ArrayList<>();   // L1044：per-key per-timestamp 无界 List
          registerTimer(currentTime);
      }
      elementsForTimestamp.add(event);
  ```
- **严重程度**: P2（若按「声明包络内必然 OOM」口径且生产 pattern 常配 idle 源，可争 P1；按现行证据保守 P2）
- **并发场景**: 单任务线程内生产（processElement）与消费（processWatermark 驱动的 drain）解耦——消费推进完全依赖上游 watermark 前进；无第二个线程，积压是跨时间累积而非竞态。
- **触发时序**: T1：上游多分区 source 中一个分区 idle（或 watermark 生成器周期长/事件时间停滞）；T2：merged watermark 冻结在 W；T3：每个到达记录满足 `timestamp > W` 全数进 `elementQueueState`（per-key MapState<Long,List<IN>>，backing 为 keyed 状态）且 SharedBuffer 为每个事件 `registerEvent`（events/entries MapState 各增一条 + Lockable 图）；T4：`processWatermark` 无 due key，drain 不发生；持续 T2→T3 直至堆耗尽 OOM——期间唯一观测点是 30 分钟一次的 INFO 缓存统计日志（`onCacheStatisticsTimer`→`logCacheStatistics`），无积压深度 gauge、无拒绝/降级路径。
- **现状**: 事件时间 CEP 的 out-of-order 缓冲是语义必需，但该边界既无容量上界与过载行为声明（阻塞/拒绝/降级均不存在），也无深度可观测；lint R5 只覆盖「无水位供给」，不覆盖「水位慢/停」。附带：无 `within` 的 pattern（windowTime=0）在 `isStateTimedOut` 恒 false 下部分匹配（SharedBuffer entries）随事件只增不减，与 Flink 同为固有语义但同样无包络声明。
- **风险**: 「匹配缓冲过载」的直接形态：一个慢分区/一条长尾 key 把整任务拖入 GC 死亡螺旋直至 OOM；运维只能从堆转储反推，`nop.stream.*` 指标面（owner doc 分层指标标准集）中无任何 CEP 缓冲深度指标。共享缓存有界（1024 slots）会造成「缓存有界」错觉，backing 状态才是无界体。
- **建议**: ① 增加 per-key（或 subtask 级）缓冲深度 gauge（`nop.stream.operator.cep.queue.size` 候选）+ 积压告警锚点；② 提供可配置的过载策略（复用平台 `QueueOverflowPolicy` 语义：拒绝 typed / 降级 side-output / 仅告警），至少支持 per-key 事件数上限的 typed fail-fast；③ owner doc 声明事件时间 CEP 的包络前提（watermark 必须前进 + within 推荐），把「无 within pattern 的状态增长」写入用户指南边界节。
- **失效面枚举**: ① `CepOperator.bufferEvent`/`processElement`（事件时间路径）——主缺陷；② `CepOperator.bufferEvent`（processing-time + comparator 路径，:897-900）——processing time 单调前进使桶必然被 drain，**不积压**；③ `SharedBuffer.registerEvent`（backing MapState）——同根无界体，随①暴露；④ `SharedBuffer.eventsBufferCache`/`entryCache`（Guava maximumSize=1024）——有界，合格；⑤ `registeredEventTimeTimersByKey` ledger——消费即删（onEventTime/onProcessingTime removeIf），有界，合格。
- **信心水平**: 确定（无界性与观测缺失为代码直接事实；OOM 需要 watermark 停滞这一前提，触发链完整可复现）。
- **误报排除**: 非「有界声明存在的缓存」误报——1024 上限只作用 Guava 缓存层，backing keyed 状态无任何上界；非事件时间语义的合理固有成本——固有的是「需要缓冲」，不固有的是「无界 + 无观测 + 无过载行为三缺」；F7 缺陷族已把 nop-stream CEP 列为 OOM 同族实证，属定向复核项。
- **复核状态**: 已保留（P2 维持，2026-10-14 复核）——`bufferEvent` 无容量检查/无拒绝分支（CepOperator.java:1041-1050）与「仅晚到可观测」逐行证实；idle 分区→watermark 冻结→无界积压→OOM 时序具体。升 P1 驳回：watermark 停滞是部署前提而非负载必然，事件时间缓冲本体是语义必需（与同 campaign C5「声明包络内，用法学区」裁定一致）；本条立案核=「无过载行为+无深度观测+无包络声明」三缺，P2 恰当。现状：campaign 唯一登记（C5 对 elementQueueState 判 OK 系缓冲本体口径，与本条立案核不冲突）；二轮复核：已保留（P2 维持，2026-10-14 二轮独立复核）——bufferEvent 无容量/过载分支（:1041-1050）与 NopCepConfigs 仅缓存槽位+统计间隔两组旋钮复核成立；round2 §2.3 对失效面⑤的「条件有界」改判与 checkpoint 膨胀链采纳并入本条触发时序，不改变判级；git log 无修复提交

### [nop-stream-state-connectors-C8-003] JDBC / File 2PC sink 的 epoch 内存缓冲在 barrier 间无上界（无 cap、无提前 flush、无深度指标）

- **文件**: `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:233, 483-494, 639-650`；`nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:136, 233-241, 248-266`
- **证据片段**:
  ```java
  // JdbcTwoPhaseCommitSink.java
  private final List<DmlOp> currentBuffer = new ArrayList<>();   // L233：无容量上限字段
  ...
  public void invoke(IN value) throws Exception {
      ...
      synchronized (currentBuffer) {
          currentBuffer.add(op);                                  // L492：无条件追加，直到下一个 barrier
      }
  }
  ...
  public TaskStateSnapshot saveState(long epochId) throws Exception {
      ...
      synchronized (currentBuffer) {
          if (!currentBuffer.isEmpty()) {
              List<DmlOp> batch = new ArrayList<>(currentBuffer); // L644：整 epoch 复制一份进 pendingCommits
  ```
- **严重程度**: P2
- **并发场景**: 任务线程（invoke 持续追加）与 checkpoint/commit 路径（saveState 于 barrier、commit 于 checkpoint 完成通知线程）共享 `currentBuffer`；内存增长非竞态而是「barrier 间隔 × 输入速率」的时间累积（同步已正确，提交侧 maxBatchSize=1000 分段有界——本条只针对缓冲侧）。
- **触发时序**: T1：作业输入速率 R 条/秒，checkpoint 周期 C 秒；缓冲峰值 ≈ R×C 条（JDBC 为 DmlOp{kind,row(Map)}，File 为整行 String）。T2：checkpoint coordinator 故障/超时重试使 barrier 迟迟不到（或用户配置了过长 checkpoint 间隔）；T3：`currentBuffer` 与 saveState 后的 `pendingCommits` 双份持有（saveState L644 复制后 clear 之前瞬时 ×2）；T4：堆增长直至 OOM 或老年代长尾。全程无任何缓冲深度指标（owner doc task 层指标表无 sink buffer gauge）。
- **现状**: 提交侧对「单批超大」有防御（`maxBatchSize` 分段 executeBatch，javadoc 明言防 driver OOM/包限制），但缓冲侧对「单 epoch 超大」无对称防御；Kafka 2PC（producer `buffer.memory`+`max.block.ms` 有界拒绝）与 BatchConsumer（batchSize=100 满即 flush）是同族中的合格对照。
- **风险**: 过载（输入速率↑或 checkpoint 间隔↑）必然线性放大堆占用；OOM 发生在 barrier 前的 invoke 路径，任务失败重启后从头重放该 epoch（2PC 语义保住正确性，但可用性受损）；pendingCommits 在反复 checkpoint 失败-重试下按 epoch 数累积（commit 失败不删条目），叠加放大。
- **建议**: 为两个 sink 增加可配置的 epoch 缓冲上限（条数或字节数；达限 typed fail-fast 或提前 saveState spill——file sink 天然可 spill 到临时文件，JDBC 可提前写 pending 事务表）；同时在 task 层指标面补 `nop.stream.task.sink.buffer.size` gauge。复用平台口径优先（`CacheConfig`/容量守卫风格），不动 2PC 提交协议。
- **失效面枚举**: ① `JdbcTwoPhaseCommitSink.currentBuffer`——本条；② `FileTwoPhaseCommitSink.currentBuffer`——同族同修；③ `KafkaTwoPhaseCommitSink.invoke`——producer 缓冲有界 + max.block.ms 超时 typed 拒绝，**合格对照**；④ `BatchConsumerSinkFunction.buffer`——batchSize 满即 flush，flush 失败抛出终止增长，**合格**；⑤ `TwoPhaseCommitSinkFunction.pendingCommits`（core，越界不立案）——file/jdbc 恢复与守卫命中路径均会移除条目，kafka 恒为 Boolean.TRUE 标记；重复失败重试下的累积为上游 checkpoint 协议问题，建议 C4/C7 维度核对。
- **信心水平**: 确定（无界为代码直接事实；触发只需时间×速率，无需竞态）。
- **误报排除**: 非「有界队列配置上限与规模声明矛盾」的误报反例——这里根本没有上限；非低频路径——invoke 是每记录热路径；也不是 F2（提交原子性完好），纯 C8/C5 内存有界性。
- **复核状态**: 已保留（P2，同 campaign 重复归并，2026-10-14 复核）——currentBuffer 无界（Jdbc :233/:492、File :136/:239）与提交侧 maxBatchSize 有界对照逐行证实；触发=时间×速率，无竞态。现状归属：与 C5-005 为同一本体（Jdbc+File 双面均由 C5-005 先立案），7.1 归并不重复计数；本条独立残值=B7/B8 边界过载行为视角与「提前 flush/spill」建议，并入 C5-005 修复项执行；二轮复核：已保留（P2 归并 C5-005 维持，2026-10-14 二轮独立复核）——Jdbc :233/:492/:641-651、File :136/:233-241 无界与 maxBatchSize 仅提交侧（:91/:216）复核成立；归并主立案 C5-005 存在性核对（C5-memory-bounds.md:208）；git log 无修复提交

### [nop-stream-state-connectors-C8-004] Avro registry 客户端在每记录热路径上做无 request-timeout 的同步 HTTP，且 encode 持 ConcurrentHashMap 分段锁发起网络调用——registry 挂起即管线不可见挂死（F5）

- **文件**: `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java:43-49, 68-70, 113-129`；`nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroWireFormat.java:54-60`
- **证据片段**:
  ```java
  // AvroSchemaRegistryClient.java
  this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  // 只有连接超时；请求级无 timeout（见 readJsonResponse 的 request 构造无 .timeout()）
  ...
  // AvroWireFormat.java —— 每记录 encode 路径
  public byte[] encode(GenericRecord record, String subject) {
      int schemaId = writerSchemaIds.computeIfAbsent(subject, s -> {
          int id = client.registerSchema(s, record.getSchema());   // CHM 分段锁内做 HTTP POST
          client.getSchemaById(id);                                // 锁内再一次 HTTP GET
          return id;
      });
  ```
- **严重程度**: P2
- **并发场景**: Kafka sink/source 任务线程逐记录调用 `avroSerializer`/`avroDeserializer`（注入 `KafkaTwoPhaseCommitSink.valueSerializer`/`KafkaSourceFunction.valueDeserializer`）；registry 是进程外依赖；`writerSchemaIds`/`schemaByIdCache` 为 `ConcurrentHashMap`，多 subtask 同 JVM 时共享（若实例共享）或各自持有（每管线独立构造时）——两种形态下挂死面相同。
- **触发时序**: T1：Schema Registry 进程存活但停止响应（accept 后不回包——负载过高/GC/半开连接）；T2：首次 encode（冷缓存）或任一未缓存 schemaId 的 decode 进入 `http.send`；connect 已过（10s 超时只管 TCP 建连），读阶段无超时 → 任务线程无限期阻塞在 `readJsonResponse`；T3：若阻塞点在 `computeIfAbsent` lambda 内，同 bin 的其他 subject 的 encode 调用一并阻塞；T4：任务既不失败也不产出，checkpoint barrier 不再产生，无任何指标/日志可判别（F5：挂死生产者对健康检测不可见）。
- **现状**: connect timeout ≠ request timeout；decode 路径的 `getSchemaById`（每次未命中 schemaId 都打 registry）与 encode 路径的注册调用都落在每记录热路径上。负查询（404）抛 typed 异常失败任务（响亮），但「慢而不断」的 registry 是纯挂起形态。
- **风险**: 下游 sink 停摆 → 上游全链背压积压（B1/B2 队列满、Kafka consumer lag），故障根因（registry）与现象（流停）跨进程，无超时即无自愈入口；`computeIfAbsent` 锁内 I/O 还会把单 subject 故障放大为 bin 内互阻。
- **建议**: ① `HttpRequest.newBuilder().timeout(Duration...)`（复用 owner doc webhook 渠道的 5s 量级或缺省可配）覆盖全部 registry 调用；② encode 注册路径把 HTTP 移出 `computeIfAbsent`（先 get 后 put + 首写单飞语义，或改 per-subject `ConcurrentHashMap<String,Object>` 锁）；③ owner doc「格式层」节补 registry 超时契约。
- **失效面枚举**: ① `AvroSchemaRegistryClient.readJsonResponse`（get/post 共用）——本条主缺陷面；② `AvroWireFormat.encode` 的 `computeIfAbsent`——锁内 I/O 放大面；③ `AvroWireFormat.decode` → `getSchemaById`——热路径未命中同步 HTTP（换 schema 洪峰时逐记录 RPC）；④ `AvroWireFormat.readerSchemaFor`——catch StreamAvroException 回退 writer schema（已声明的兜底，合格）；⑤ nop-stream-webhook 渠道（runtime，越界）——同平台内已有 timeout+有界队列的正例对照。
- **信心水平**: 确定（JDK HttpClient 语义：未设 request timeout 则无读超时；代码直接事实）。
- **误报排除**: 非「有界缓存即合格」——本条不是缓存容量问题（schema-id 键空间受 registry 有限集约束，负查询不驻留）；非低频路径——decode/encode 是每记录执行；也不是原生类误用报告——是复合 misuse（无超时 + 锁内 I/O）。
- **复核状态**: 已保留（P2，同 campaign 重复归并，2026-10-14 复核）——connectTimeout-only（AvroSchemaRegistryClient.java:48）+ 全部 request 无 `.timeout()`（:55-58,:102-105,:113-129）+ encode `computeIfAbsent` 锁内双 HTTP（AvroWireFormat.java:54-60）逐行证实；JDK HttpClient 未设 request timeout 则读阶段无超时，挂死时序具体。现状归属：超时本体与 C6-008 为同一缺陷（C7 复核已裁定归并不重复计数；同族 C2-002/C4-003/C3-003 五向），7.1 归并；本条独立残值=锁内 I/O 的 bin 内互阻放大面，作为 C6-008 修复项执行注记；二轮复核：已保留（P2 归并 C6-008 维持，2026-10-14 二轮独立复核）——connectTimeout-only（:48）、全部 request 无 `.timeout()`（:55-58/:102-105/:113-116 readJsonResponse）、encode `computeIfAbsent` 锁内双 HTTP（AvroWireFormat.java:55-60）逐行复核成立；归并主立案 C6-008 存在性核对（C6-resource-lifecycle.md:243）；git log 无修复提交

### [nop-stream-state-connectors-C8-005] RocksDB 全量 JSON 快照把整库一次性物化进堆（默认路径），同步跑在任务线程——大状态作业的 checkpoint 即过载点

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBSnapshotSerDe.java:84-115, 209-245, 284-326`；`nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:119, 826-836, 845-865`
- **证据片段**:
  ```java
  // RocksDBSnapshotSerDe.snapshotState —— 逐 CF 全迭代，逐条反序列化进 List<Map>
  List<Map<String, Object>> entries = new ArrayList<>();          // L309：整 CF 条目全量驻留
  TtlContext<ByteBuffer> ttl = state.ttlContext();
  try (RocksIterator it = backend.getDb().newIterator(cfHandle)) {
      for (it.seekToFirst(); it.isValid(); it.next()) {
          ...
          RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(it.key(), backend.getKeyType());
          Map<String, Object> entry = new LinkedHashMap<>();
          ...
          entryWriter.write(entry, dk, it.key(), it.value(), descriptor); // L320：值反序列化成对象树
          entries.add(entry);
      }
  }
  info.put("entries", entries);                                    // L324：随 StateSnapshot 整体携带
  ```
- **严重程度**: P2（与 C5 交叠，本维度按「checkpoint 消费边界的过载形态」立项）
- **并发场景**: 任务线程在 barrier 处理中同步执行 `snapshotState()`（owner doc 明确同步快照语义）——快照时长内记录处理完全停顿，等效于一次与状态体积成正比的管线自背压；内存面为单线程物化，无竞态。
- **触发时序**: T1：作业选择 RocksDB 后端的原因正是大状态（多 key × 大值）；T2：checkpoint barrier 到达，`RocksDBKeyedStateBackend.snapshotState` 走默认 `incrementalCheckpointEnabled=false` 全量路径（:828-836）；T3：每个 CF 被 `RocksIterator` 全量迭代，每条 key+value 反序列化为 Java 对象挂进 `entries`/`grouped` 结构，峰值堆 ≈ 整库反序列化体积（外加 JSON 中间态放大）；T4：堆峰值超限 → checkpoint 期 OOM——发生在「 RocksDB 本来为了不把状态放堆里」的场景下，off-heap 状态在 checkpoint 瞬间整体回到堆上。
- **现状**: 增量路径（native checkpoint + SST content-addressing，含 F-02 滚动保留 K=2）已完整实现但默认关闭（字段 javadoc 自述 "Defaults to false so the full-scan path remains the default (backward compatible)"）；全量路径与 Memory 后端字节兼容是显式取舍，但「RocksDB 用户的默认 checkpoint 会整库进堆」未见 owner doc 包络声明。
- **风险**: 状态越接近声明包络上界，checkpoint 越接近必现 OOM；且快照同步阻塞任务线程，状态 GB 级时 barrier 时延可达秒-分钟级，反压沿 B3/B1/B2 逐级放大（ emit timer 会如实显示，但根因观测不到）。
- **建议**: ① owner doc（`nop-stream.md` state 层节）声明两路径的内存包络与建议（大状态启用 `setIncrementalCheckpointEnabled(true)`）；② 中期把全量路径改流式（分批序列化进 segment store，去掉 entries 全量 List），或把默认翻转的迁移另立 plan（Protected Area：生成/序列化管线 plan-first，不在本轮实施）。
- **失效面枚举**: ① `RocksDBSnapshotSerDe.snapshotKeyedState`/`snapshotMapState`——本条主面；② `RocksDBKeyedStateBackend.snapshotIncremental`——native 路径不物化，SST 直接落盘 + 哈希，**合格对照**；③ `RocksDBSnapshotSerDe.restoreState`（恢复侧逐条写回）——流式，合格；④ `RocksDBKeyedStateBackend.deleteByPrefix`（:746-767，TTL 逐 key List 物化后逐条删）——同「先物化后处理」形态但上界为单 key 的 map 条目数，降 P3 注记；⑤ CEP 侧全量 checkpoint（Memory 后端 JSON 快照）为 nop-stream-core 范围，不在本报告立案。
- **信心水平**: 确定（全量物化为代码直接事实；OOM 需状态规模前提，与「声明包络内必然 OOM」的判据边界留给复核裁定包络值）。
- **误报排除**: 非重复报告——C5 维度审「无界增长容器」，本条是「checkpoint 消费边界的峰值物化 + 同步自背压」，C8 视角为 barrier 处理路径的过载行为；亦非「为压测写的简化」豁免面——这是默认生产路径。
- **复核状态**: 已保留（P2，同 campaign 重复归并，2026-10-14 复核）——全量路径逐 CF 全迭代+逐条反序列化进 entries List（RocksDBSnapshotSerDe.java:309-324）、incrementalCheckpointEnabled 默认 false（RocksDBKeyedStateBackend.java:119）、任务线程同步执行（:826-836）逐项证实。现状归属：内存物化本体已由同 campaign C5-002 以 P1 先立案（同一文件同一行段），7.1 归并以 C5-002 为主；本条 C8 角度（barrier 同步自背压）与时长预算面由 C8-010 独立承接；二轮复核：已保留（P2 归并 C5-002 维持，2026-10-14 二轮独立复核）——entries 全量物化（RocksDBSnapshotSerDe.java:309-324）、incrementalCheckpointEnabled 默认 false 及自述 javadoc（RocksDBKeyedStateBackend.java:112-119）、任务线程同步快照（:825-836）复核成立；归并主立案 C5-002 存在性核对（C5-memory-bounds.md:91）；git log 无修复提交

---

## 4. 零发现维度说明与合格面记录

无零发现子模块；以下合格面对照供复核与 C10（测试保护力）引用：

- **B1/B2 有界队列桥**（`MessageSourceReader`/`DebeziumCdcSourceReader`）：容量 256 + 阻塞 put + 100ms 有界 poll + 首错 typed 重抛（runnerFailure 从 pollNext 声明性失败）——push→pull 桥的背压/过载语义是本轮最佳实现；其 offset 偏斜缺陷单列为 C8-001。
- **限速即背压**（`FileSourceReader.acquireRate`/`KafkaSourceFunction` poll 批节流）：monitor 外有界分片睡眠、负值 typed 拒绝、随分片拷贝携带——与 owner doc WI5 声明一致。
- **F2 全量核查结论**：JDBC 2PC（单事务 + 台账幂等守卫 + 守卫命中 WARN 带全键）、file 2PC（saveState 先 fsync、恢复 provenance 判别、manifest 目录锁串行化）、kafka-2pc（高水位幂等 no-op + 超界 typed 拒绝 + 文档化残余重复窗口）、batch-consumer（失败保留缓冲重试 + close 失败显式「data may be lost」）——sink 侧无未声明的半个成功；source 侧唯一实质形态为 C8-001。
- **挂死点扫描（F5）**：全部 `poll(timeout)`/`await(timeout)`/`join(timeout)` 均带超时（100ms poll ×3、5s close join ×2、1s latch ×2）；唯一无限期等待点 = C8-004 的 registry HTTP 读阶段。`lockInterruptibly`/裸 `wait` 无。
- **RocksDB 写 stall 背压传播**：默认选项下 memtable 满/L0 阈值触发 RocksDB 原生 write stall → put 阻塞在任务线程 → 背压自然传播（B12）；plan 369 memory-control preset（共享 block cache + WriteBufferManager 总量 cap）为 opt-in 加固，构造期对「WBM 无 cache」组合 typed fail-fast。总量 cap 默认关闭一事并入 C8-005 注记，不单独立案。

## 5. 计数与移交

| 严重程度 | 数量 | 条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 1 | C8-001 |
| P2 | 4 | C8-002 / C8-003 / C8-004 / C8-005 |
| P3 | 0（1 条 P3 注记并入 C8-005 失效面④） | — |

移交复核阶段关注点：① C8-001 的 engine 侧 offset 刷新时点（需读 nop-message-debezium 引擎路径裁定「确定/很可能」是否升级或降级）；② C8-002 是否按「负载上升必然触发的无界增长」升 P1（取决于 idle 源是否常见部署形态）；③ C8-003 与 C5 维度可能同根合并（根因 = epoch 缓冲无界，失效层 = sink 数据路径）。

# C8 背压·流控·过载行为 深挖第 2 轮追加报告 — nop-stream-state-connectors

- **审计维度**: C8（背压、流控与过载行为）— 深挖第 2 轮
- **基线**: 同目录 `C8-backpressure.md`（第 1 轮，B1–B15 边界 + C8-001…C8-005）
- **盲审边界**: 本轮仅读取本维度第 1 轮文件；未读取 `ai-dev/audits/` 下其他维度/其他审计目录、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`
- **日期**: 2026-10-14
- **复核状态**: 已复核（2026-10-14 维度复核 + 2026-10-14 二轮独立复核，逐条判定见文末「维度复核结论（独立复核子代理 · 二轮复核）」）

---

## 1. 深挖范围与方法

按第 2 轮指令对三个定向面逐一执行，全部结论基于当前 live code：

1. **信号量/限流许可泄漏**：8 模块全量 grep `Semaphore|RateLimiter|tryAcquire|acquire(|release(|permits`。事实：**无** `ISemaphore`/`HighWatermarkSemaphore`/commons `IRateLimiter` 使用（第 1 轮结论复核成立）。限速仅两处消费 core `SourceRateLimiter`：`FileSourceReader.java:155-158`（monitor 外有界分片睡眠）、`KafkaSourceFunction.java:264-277`（poll 批 acquire 后逐条发射，睡眠可中断、typed 失败）——均无 acquire/release 配对语义，**许可泄漏面为空，本轮零发现**。CEP `Lockable.release`（`Lockable.java:60-68`）是带 over-release 守卫的引用计数，非信号量。
2. **过载可观测缺口**：8 模块 grep `Gauge|Meter|Counter|registerMetric|micrometer`——除 CEP 一处 `StreamMetricsRegistries.registry()` 注册 `numLateRecordsDropped`（`CepOperator.java:438`）外**零指标 API 使用**；由此立案 C8-008。
3. **C8-001（offset 偏斜）同族面**：逐一核查全部携带位点的路径（见 §3 同族闭环）。核心裁定事实：legacy `SourceFunction` 的 `snapshotState` 经 mailbox drain 在**任务线程** collect 点执行（`StreamSourceOperator.java:195-207, 410-424`——`offerBarrier` 投递 control mail，`injectBarrier→snapshotState` 在 `SourceContext.collect` 的 `drainControlMails()` 处运行），与 `run()` 同线程 → Kafka `restoredOffsets`（普通 `TreeMap`）无跨线程竞态，`volatile` 仅为 restore 发布安全。

## 2. 证伪记录（深挖中被证据推翻的假设，供复核引用）

- **「B1/B2 桥 close 路径 runner 永久滞留 `queue.put` = 线程泄漏」——证伪**。JDK 21 实证探针（`_tmp/AbqClearProbe.java`）：`ArrayBlockingQueue.clear()` 会 `notFull.signalAll()`，卡死的 put 被唤醒并完成入队。两桥的 close 序列自洽：`cancel()` → embedded `run()` 的 watch-loop 经 latch 在 ≤1s 内退出 → `join(5s)` 成功 → `queue.clear()` 解救滞留在 `put` 的引擎/消息派发线程（`MessageSourceReader.java:194-212`、`DebeziumCdcSourceReader.java:294-312`）。背压阻塞 + 取消逃逸语义**合格**，不立案。
- **「Debezium `engine.close()` 与卡死 handler 互等 → 任务线程在 close 内无限挂死」——降级为理论风险**。`DebeziumEngineWrapper.stop()` 仅置位 + `engine.close()`（Debezium 3.7.0.Final `EmbeddedEngine.close` 为非阻塞置位语义，`awaitStop` 才等待），且即使短暂互等也被上述 `clear()` 信号收敛；未具体化触发时序，按纪律不立案。
- **「BatchLoaderSourceReader `currentOffset++` 在返回前 = C8-001 同族偏斜」——证伪**。offset 计数先于下游消费返回，但源快照与 barrier 发射之间已发射的记录在 barrier 到达 sink 前已进入 sink 缓冲 → 计入同一 barrier 轮的 `saveState`/preCommit；恢复时 skip 语义显式（`BatchLoaderSourceReader.java:150-151, 160-181`）——at-least-once + barrier 轮一致，无偏斜。
- **「`DebeziumTableSplitEnumerator` 二次持有 offset 真相」——证伪**。`snapshotState` 只快照未分配表（`DebeziumTableSplitEnumerator.java:111-116`），offset 真相唯一存在于 reader 侧（即 C8-001 已立案的面）。

## 3. C8-001 同族面闭环（F3 清扫）

| 同族成员 | 位点语义 | 状态 |
|---|---|---|
| `DebeziumCdcSourceReader.snapshotState` | 引擎 offset 直抬，越过 256 队列 | **C8-001 已立案** |
| `DebeziumCdcSourceFunction.snapshotState`（legacy） | collect 直连，偏斜 ≈1 条 | C8-001 失效面② 已并 |
| `KafkaSourceFunction.run/markConsumed/snapshotState` | `markConsumed` 严格跟随 collect 返回；snapshot 与 run 同任务线程（mailbox drain 事实） | **无此缺陷（正例）** |
| `FileSourceReader` | 光标推进与发射同 monitor（第 1 轮 AR-15-②） | 无此缺陷 |
| `BatchLoaderSourceReader` | `currentOffset++` 先于返回，skip 显式，barrier 轮一致 | 无此缺陷（本轮复核） |
| `MessageSourceReader` | recovery=NONE，无 offset 参与 | 不适用 |
| `DebeziumTableSplitEnumerator` | 只快照未分配表，无 offset | 无此缺陷（本轮复核） |

同族面闭环，无新成员。

---

## 4. 发现条目

### [nop-stream-state-connectors-C8-006] Kafka 2PC epoch 事务存活期与 checkpoint 间隔的耦合无守卫：core 默认间隔(60s) == broker 默认 transaction.timeout.ms(60s)，负载下提交必然触界，进入「typed 失败→重启→超限→job FAIL」循环

- **文件**: `nop-stream/nop-stream-connector-kafka/src/main/java/io/nop/stream/connector/kafka/KafkaTwoPhaseCommitSink.java:134-148, 167-173, 185-214`；关联 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/CheckpointConfig.java:27`
- **证据片段**:
  ```java
  // KafkaTwoPhaseCommitSink.java —— 事务自首个 invoke 开启，跨整个 checkpoint 间隔，
  // 直到下一个 barrier 的 saveState 内才 commitTransaction：
  public void invoke(T value) throws Exception {
      ensureTransactionOpen();                       // L169：首次 invoke 开启 epoch 事务
      ...
      producer.send(new ProducerRecord<>(topic, key, bytes));
  }
  public TaskStateSnapshot saveState(long epochId) throws Exception {
      if (transactionOpen && producer != null) {
          producer.commitTransaction();              // L203：下一 barrier 才提交
          ...
  // newProducer()（L134-148）不设 transaction.timeout.ms —— broker/producer 默认 60000ms
  // CheckpointConfig.java:27 —— DEFAULT_CHECKPOINT_INTERVAL = 60000L
  ```
- **严重程度**: P1
- **并发场景**: sink 任务线程（invoke 发送 / barrier 内 saveState 提交）与 Kafka 事务协调器（按 transaction.timeout.ms 从 beginTransaction 起算的惰性过期中止）跨进程交互；无平台内第二线程参与。
- **触发时序**: T1：barrier N 处理完成，`saveState` 提交 epoch N 并 `beginTransaction` 开启 epoch N+1。T2：`invoke` 持续发送（epoch N+1 事务保持 open，记录对 read_committed 消费者不可见——声明的可见性延迟语义）。T3：barrier N+1 在 t+60s+δ 到达（δ = barrier 等待 + 快照物化[C8-005 使 δ 随状态体积放大] + 持久化 + 提交 RPC）；事务年龄 = 60s+δ−d（d = barrier N 后首条记录的到达延迟）——持续负载下 d→0、δ>0，年龄稳定超过 60s；T4：协调器惰性中止超时事务，epoch N+1 被标记 abort；T5：`commitTransaction`（或后续 `send`）收到 ProducerFenced/InvalidProducerEpoch typed 失败 → saveState 抛出 → checkpoint 失败 → 任务失败重启（javadoc L201-203 自述该毒化路径「loud, never silent」）→ `initTransactions` fence+abort → 源重放。负载不消退则循环；`DEFAULT_MAX_RESTARTS_PER_REGION=3` 后 job FAIL。
- **现状**: 默认配置组合（core 默认 60s 间隔 × producer 默认 60s 事务超时）恰好使 epoch 事务的存活期预算等于其设计存活期，零裕度；`newProducer()` 不设默认 `transaction.timeout.ms`，部署/校验链路无「checkpointInterval < transaction.timeout.ms」守卫，owner doc 无该耦合声明。对照：Flink Kafka sink 同构场景将 transaction.timeout.ms 默认提升到 1h 正是为了给 checkpoint 间隔留裕度。
- **风险**: kafka-2pc（声明的 exactly-once 对的提交半）在默认配置 + 持续负载下进入可用性崩溃循环（数据不丢：abort → 不可见 → 源重放，F2 干净；提交点 fused-in-barrier 使失败响亮），但「负载上升必然触发的失败循环」正是本维度 P1 族；且 C8-005 的慢快照直接放大 δ，使两个已立案缺陷在过载场景复合。
- **建议**: ① `newProducer()` 显式默认 `transaction.timeout.ms`（≥ 数倍 checkpoint 间隔，如 15-60min，不超过 broker `transaction.max.timeout.ms`=900s 上限的取值需同步声明）；② 部署校验/`checkConnection` 增加「checkpointInterval × 安全系数 ≤ transaction.timeout.ms」的 typed fail-fast；③ owner doc 声明该耦合与推荐配置。
- **失效面枚举**: ① `KafkaTwoPhaseCommitSink` epoch 事务——本条主缺陷；② `JdbcTwoPhaseCommitSink`——commit 在 notify 通知线程独立连接执行，无外部超时中止，不适用；③ `FileTwoPhaseCommitSink`——无外部事务协议，不适用；④ `BatchConsumerSinkFunction`——无事务，不适用；⑤ `KafkaSourceFunction`——无事务，不适用；⑥ Avro registry HTTP——无事务（挂死面已由 C8-004 立案）。
- **信心水平**: 确定（守卫缺失与默认值冲突为代码直接事实；「正常负载即触界」依赖协调器惰性过期的粒度，标注很可能——但过载下 δ 增大使其趋于必然）。
- **误报排除**: 非「有界/响亮即合格」——失败虽响亮但构成负载触发的主路径可用性循环，且根因是默认配置自相矛盾而非用户误配；非 F2 半个成功——abort→重放无丢失，本条是纯过载可用性缺陷；非把「Kafka 正常语义」当缺陷——缺陷在框架未管理该语义与自身 checkpoint 协议的耦合。
- **复核状态**: 已保留（P1 维持，2026-10-14 复核）——`newProducer()` 不设 transaction.timeout.ms（:134-148，仅守卫 transactional.id）、事务自首个 invoke 开启至下一 barrier `saveState` 才提交（:167-214）、core 默认间隔 60s（CheckpointConfig.java:27）、DEFAULT_MAX_RESTARTS_PER_REGION=3（:35）逐项证实；裕度为算术零：事务年龄=interval+saveState 时长>60s 恒成立（快照时长 w>0 恒真），失败循环时序具体。EXPERIMENTAL 豁免不覆盖本条——owner doc 仅声明残余重复窗口，未声明该耦合。残余不确定性（协调器惰性过期粒度，reaper 周期同量级→持续负载下高概率命中）不构成降级理由：默认组合零裕度+无守卫+无声明为代码直接事实，Flink 同构场景默认 1h 超时佐证。现状：新发现（E2E `TestKafkaConnectorExactlyOnceE2E` 用默认 60s 间隔但运行窗口仅数十秒，从未触界，测试通过不构成反证）；二轮复核：已保留（P1 维持，2026-10-14 二轮独立复核）——零裕度算术复核成立且强于原推演：下一 epoch 事务在 saveState 内 commit 后立即 begin（KafkaTwoPhaseCommitSink.java:200-206），事务年龄自 barrier N 起算 = interval + 快照时长 > 60s 恒成立；newProducer() 仅守卫 transactional.id（:134-150）、core 60s/600s/3（CheckpointConfig.java:27-28,35）复核成立；owner doc 豁免仅声明残余重复窗口（nop-stream-connectors.md:35/132）不覆盖本条；「transaction.timeout」关键词历史审计仅本 campaign 命中 = 无先例；git log 无修复提交

### [nop-stream-state-connectors-C8-007] JDBC 2PC epoch 台账表只增不删（F4 在 live code 的现存实例）：每次成功 commit 永久留一行 (namespace, epoch_id, subtask_id)，epoch 单调递增，无任何 prune/TTL 路径

- **文件**: `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:705-707, 737, 842, 1146-1153`
- **证据片段**:
  ```java
  // commit 成功路径：数据 + 台账行同一事务写入，仅内存 pendingCommits 移除
  writeLedgerEntry(connection, effectiveLedgerNamespace(), checkpointId, subtaskIndex); // L705
  connection.commit();                                                                  // L707
  ...
  getPendingCommits().remove(checkpointId);            // L737：只删内存 map，台账行永驻
  ...
  private String buildInsertLedgerSql(IDialect d) {    // L1146-1153：仅有的台账写 SQL
      return "INSERT INTO " + d.escapeSQLName(ledgerTableName) + ...;
  ```
- **严重程度**: P2
- **并发场景**: commit 通知线程按 epoch 逐个写台账行；无竞态——本条是时间 × checkpoint 频率的单调持久化累积（非内存、非竞态面）。
- **触发时序**: T1：作业以 checkpoint 间隔 C 运行，每轮 barrier 每并行子任务 commit 一次 → 台账表新增 parallelism 行（epoch_id 单调递增）。T2：行内 SQL 仅有 INSERT（L1146-1153）与按全 PK 的 EXISTS 守卫读（L842），全文件 grep 无 DELETE/prune/purge/TTL；重启恢复的重放走 checkpoint 态内的 pendingCommits（`restoreFromEpoch` → `super.restoreFromEpoch`），**不依赖扫描台账历史** → 高水位以下的行语义上已可修剪而无人修剪。T3：天/周级运行后表按 rows = parallelism/C 持续增长（30s 间隔 × 并行度 4 ≈ 1.15 万行/天）。
- **现状**: 幂等守卫只需「未确认 epoch 可查」，按全 PK 点查性能不劣化，但存储只增不减；审计缺陷族画像 F4 的实证出处（「JDBC 2PC ledger 只增不删」）在 v2 表（`stream_epoch_ledger_v2`，namespace 化）中**仍以无清理路径的形态存在于 live code**。
- **风险**: 长时运行必然增长（速率低、无热路径劣化，故 P2 而非 P1）；运维侧表现为台账表无限膨胀需人工清理，且人工清理若误删未确认 epoch 行会破坏幂等守卫（无 TTL 归属说明加剧误操作面）。
- **建议**: commit 成功后按 `epoch_id < 已完成高水位`（或 `committed_at` TTL）批量 DELETE 旧行——守卫只查当前 epoch，删除高水位以下历史行不影响恢复重放（重放依据 checkpoint 态）；或在 owner doc 声明台账为审计保留 + 提供清理工具。与文件 sink 的孤儿 `.tmp` 清扫（同族弱形态，见失效面②）可并入同一清理 plan。
- **失效面枚举**: ① `JdbcTwoPhaseCommitSink` 台账表——本条主面；② `FileTwoPhaseCommitSink` 每 epoch `.tmp`——有 `abort(epochId)` 配套删除 + restore provenance 判别（合格），但进程 kill -9 于 saveState 后 commit/abort 前会留下孤儿 tmp，无启动清扫，同 F4 弱形态随①同修；③ 内存 `pendingCommits`——commit 成功即 remove、abort 即 remove，有界，合格；④ `KafkaTwoPhaseCommitSink` pendingCommits 恒为 TRUE 标记——随 checkpoint 态保留（第 1 轮 C8-003 失效面⑤已注记，归 core 协议）。
- **信心水平**: 确定（无 DELETE 为代码直接事实；恢复重放不依赖台账历史由 `restoreFromEpoch` 路径证实）。
- **误报排除**: 非「有界声明存在的缓存」——无任何上限或清理声明；非低频路径——每 checkpoint 每 subtask 必写一行；非 F2/F9——提交正确性完好，纯 F4 无界账本；也不与第 1 轮 C8-003（堆内缓冲无界）重复——失效层是持久化表而非堆。
- **复核状态**: 已保留（P2，同 campaign 重复归并，2026-10-14 复核）——台账 SQL 仅 INSERT（:1146-1153）与全 PK EXISTS 读（buildLedgerExistsSql），全文件无 DELETE/prune 证实（grep 命中均为 CDC 数据面 UPSERT_DELETE 语义）；恢复重放走 checkpoint 态 pendingCommits、不依赖台账历史成立。现状归属：同 campaign 三向重复（←C5-006 同审计先立案；C3-002 已由 C3 复核升 P1 为主立案并列本条为归并对象），7.1 归并不重复计数；本条无独立残值（file sink 孤儿 tmp 弱形态已由 C5-006/C3-002 覆盖）；二轮复核：已保留（P2 归并维持，2026-10-14 二轮独立复核）——台账仅 INSERT（:1146-1153）+全 PK EXISTS 读（:839-846）、全文件无 DELETE/prune/purge/TTL（grep 命中均为 CDC 数据面 UPSERT_DELETE）复核成立；归并主立案 C5-006（C5-memory-bounds.md:240）/C3-002（C3-async-chains.md:85）存在性核对；2026-09-27 quality-r2 审计的「台账」命中为 CEP timer 台账性能优化面，与本条 JDBC 台账无关，无历史先例冲突；git log 无修复提交

### [nop-stream-state-connectors-C8-008] 8 模块过载可观测面系统性缺失：15 条边界的积压深度/滞留时长/拒绝计数零指标，唯一积压相邻信号是 CEP 30 分钟一次的 INFO 缓存统计日志

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:438, 646, 658`（正例 + 唯一信号）；缺口面覆盖 `MessageSourceReader.java:53`、`DebeziumCdcSourceReader.java:57`、`JdbcTwoPhaseCommitSink.java:233`、`KafkaSourceFunction.java:388-391` 等（B1/B2/B3/B7/B8/B9/B12/B14）
- **证据片段**:
  ```java
  // CepOperator.java:438 —— 模块内唯一指标注册点（证明注册设施存在且可用）
  this.numLateRecordsDropped = StreamMetricsRegistries.registry()
      ...counter(...LATE_ELEMENTS_DROPPED_METRIC_NAME...);   // "numLateRecordsDropped"
  ...
  firstFire, this::onCacheStatisticsTimer);                  // L646：30 分钟一次
  // → SharedBuffer.logCacheStatistics()：积压/缓存状态的唯一信号是低频 INFO 日志
  // 对照缺口：B1/B2 桥（QUEUE_CAPACITY=256，无深度 gauge）、JDBC/File sink 缓冲（:233，无 gauge）、
  // Kafka 源位点仅手工钩子 currentOffsets()（:388-391，非指标）
  ```
- **严重程度**: P2
- **并发场景**: 覆盖全部生产-消费边界的观测面：跨线程积压（B1/B2 队列、B7/B8 sink 缓冲、B10 CEP 桶）与跨进程滞留（B9 Kafka 事务年龄、B14 registry 调用延迟）——非单一竞态，是遥测平面整体缺失。
- **触发时序**: T1：任一边界进入过载（源桥满、sink 缓冲增长、CEP 桶堆积、Kafka 事务年龄逼近超时[C8-006]、registry 变慢[C8-004]）。T2：任务线程继续阻塞/积压——无任何 gauge 暴露深度、滞留时长或拒绝计数（8 模块 grep 指标 API 仅命中 CEP 一处 counter）。T3：运维可见信号仅有 core 侧 emit timer/recordCounter（活性）与 30 分钟一次的 CEP INFO 日志；直到任务 typed 失败（可能已是 C8-006 循环的第 3 次重启）才有第一个响亮信号，且失败日志无法区分「checkpoint 慢」「事务过期」「registry 挂起」等根因。
- **现状**: 指标注册设施（`StreamMetricsRegistries`）在模块内已使用（`numLateRecordsDropped` 正例），缺口不是「无设施」而是「过载维度未接入」；第 1 轮 C8-001/002/003 各自在条目内提及配套 gauge 缺失，本条将**未被那些条目覆盖的边界**（B1/B2 深度、B3 lag/位点、B9 事务年龄、B12 write-stall、B14 registry 延迟）与系统性「无积压遥测平面」立为一等发现。
- **风险**: 第 1 轮 5 条发现 + 本轮 C8-006 的全部过载形态在演化为任务失败前不可见；C8-001 的静默丢失窗口（队列深、偏斜大）与 C8-006 的超时触界（事务年龄）恰恰是最需要先行指标的两处——无指标则容量规划与过载根因定位都只能靠日志考古/堆转储。
- **建议**: 复用 `StreamMetricsRegistries` 注册面向过载的最小 gauge 集：`source.queue.size`（B1/B2，桥内 `queue.size()` 直读）、`sink.buffer.size`/`sink.pendingCommits`（B7/B8）、`kafka.txn.open.age.millis`（B9，begin 时间戳差）、`source.lag`（B3，endOffset−position）、`cep.queue.size`（B10，并入 C8-002 建议①）；并给 C8-006 的守卫校验失败、C8-004 的 registry 调用失败/延迟加 counter。
- **失效面枚举**: ① B1/B2 桥深度（`QUEUE_CAPACITY=256` 无 gauge）——本条；② B3 Kafka 位点/lag（仅手工钩子 `currentOffsets()`）——本条；③ B7/B8 sink 缓冲/pendingCommits 深度——本条（C8-003 只立案无界性本体）；④ B9 Kafka 事务年龄——本条（C8-006 立案超时耦合本体）；⑤ B12 RocksDB write-stall/延迟计数——本条（RocksDB 原生统计未暴露）；⑥ B14 registry 调用延迟/失败——本条（C8-004 立案无超时本体）；⑦ B10 CEP 桶深度——已由 C8-002 建议① 覆盖，此处仅归并。
- **信心水平**: 确定（零指标为 grep 直接事实；「30 分钟 INFO 日志是唯一信号」由 `onCacheStatisticsTimer` 注册间隔证实）。
- **误报排除**: 非「功能缺失即缺陷」的泛化——C8 维度步骤 4 明确将「积压深度、拒绝数是否有指标」列为一等检查项；非与 C8-002/003 重复——那两条的失效层是无界增长本体，本条失效层是遥测平面，且范围限缩到前两条未覆盖的边界；非「活性指标可替代」——recordCounter/emit timer 只证「在动」，不证「在涨」。
- **复核状态**: 已保留（P2，证据面修正后维持，2026-10-14 复核）——证伪修正：失效面⑤（B12 RocksDB write-stall 未暴露）不成立，`RocksDBMetricsRecorder`（2026-09-03 plan-2216-1 item 16 引入，早于本审计）已在后端 open 路径注册 6 个 gauge（memtable.usage.total/compaction.pending.bytes/compactions.running/block.cache.usage/pinned/estimate.num.keys，RocksDBKeyedStateBackend.java:279），「8 模块仅 CEP 一处指标」表述错误；该 recorder 已由同 campaign C1-002/C7-004/C6-010 审及。复核后仍成立的核：B1/B2 队列深度、B3 lag/位点、B7/B8 sink 缓冲与 pendingCommits、B9 事务年龄、B14 registry 延迟、B10 CEP 桶深度确无任何 gauge（connector 四模块 grep 复证零命中）——「connector source/sink 边界积压深度零遥测」缺口真实，P2 维持但范围限缩于此；二轮复核：已保留（P2 修正后范围维持，2026-10-14 二轮独立复核）——失效面⑤证伪独立成立：RocksDBMetricsRecorder 在 open 路径实例化（RocksDBKeyedStateBackend.java:278）并经 micrometer 注册 gauge（metrics/RocksDBMetricsRecorder.java:83）；connector 六模块 StreamMetricsRegistries/Micrometer grep 零命中复证；CEP counter 正例 = AR-06（2026-08-13-1930 open-audit P2 死计数器）修复产物（plan 368 Phase 7 提交 2bb7c013a7）核实；git log 无修复提交

---

## 5. 合格面补充与边界注记

- **限速即背压复核（零发现面）**：`SourceRateLimiter` 两处消费（file/kafka 源）均为 monitor 外有界分片、可中断、负值 typed 拒绝、随分片拷贝携带——许可泄漏面不存在（无配对语义）；`Lockable` 引用计数带 over-release fail-fast。
- **边界注记（不立案，随 C8-002 观测）**：CEP 在 watermark 长期停滞恢复后的 drain 一次性处理全部到期桶 + NFA 计算，单次 `processWatermark` 时长与积压量成正比，期间 checkpoint barrier 邮箱排队（屏障时延放大）——这是 C8-002 同一无界体的「不 OOM 形态」后果，不重复计数。
- **桥 close 语义（本轮实证合格）**：push→pull 桥的取消逃逸依赖 `ArrayBlockingQueue.clear()` 的 `notFull` 信号（JDK 21 探针实证），close 序列有界收敛；建议复核阶段保留该探针作为回归测试形态（C10 可引用）。

## 6. 计数与移交

| 严重程度 | 数量 | 条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 1 | C8-006 |
| P2 | 2 | C8-007 / C8-008 |
| P3 | 0 | — |

两轮累计：P1×2（C8-001、C8-006）、P2×6（C8-002…C8-005、C8-007、C8-008）。

移交复核阶段关注点：① C8-006 的 P1 判级（默认配置冲突为代码事实，触界概率依赖协调器惰性过期粒度——若裁定降 P2 需同时给出「默认组合可用」的反证）；② C8-007 与 F4 族历史实例（AR-06）的合并口径（live code 现存 vs 已修复的裁定需读修复记录）；③ C8-008 与 C8-001/002/003 条目内观测建议的归并去重；④ 证伪记录（§2）中的 `clear()` 探针建议转 C10 回归测试立项。

# C8 背压·流控·过载行为 深挖第 3 轮追加

## 深挖第 3 轮追加

- **审计维度**: C8（背压、流控与过载行为）— 深挖第 3 轮
- **基线**: 同目录 `C8-backpressure.md`（第 1 轮，B1–B15 + C8-001…C8-005）、`C8-round2.md`（第 2 轮，C8-006…C8-008）
- **盲审边界**: 本轮仅读取本维度第 1/2 轮文件与共享提示词；未读取 `ai-dev/audits/` 下其他维度/其他审计目录、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`。`nop-stream-core`/`nop-stream-runtime` 相关类仅作边界上下文证据（同第 2 轮 C8-006 对 `CheckpointConfig` 的引用口径），不作为发现对象。
- **定向任务**: ① C8-006 同族——其他「超时裕度冲突面」全量排查；② 前两轮边界清单残余项闭环
- **日期**: 2026-10-14
- **复核状态**: 已复核（2026-10-14 维度复核 + 2026-10-14 二轮独立复核，逐条判定见文末「维度复核结论（独立复核子代理 · 二轮复核）」）

---

## 1. 深挖范围与方法

按第 3 轮指令对 C8-006 同族（超时裕度冲突）做全量面排查，并对前两轮边界清单残余逐项闭环，全部结论基于当前 live code：

1. **超时面全量盘点**：8 模块 grep `timeout|TimeUnit\.|Duration\.of|TIMEOUT|_MS\b`（剔除 CEP pattern-within 语义命中）。全部时间配置面收敛为：`MessageSourceReader`/`DebeziumCdcSourceReader`（poll 100ms + close join 5s）、`KafkaSourceFunction.poll(200ms)`、`KafkaTwoPhaseCommitSink`（producer 默认超时族：transaction.timeout.ms=60s / max.block.ms=60s / request.timeout.ms=30s / delivery.timeout.ms=120s，均未显式设置）、`AvroSchemaRegistryClient` connectTimeout=10s（无 request timeout，C8-004）、core `CheckpointConfig`（interval=60s / **timeout=600s** / barrierAlignment=30s / consecutiveFailures=3）。逐一面核对裕度，结论见 §2。
2. **checkpointTimeout 消费者核查**：`getCheckpointTimeout()` 在 `nop-stream-core` src/main 内**零消费**（仅 `InputGate.java:738` 注释提及）；强制执行点在 `nop-stream-runtime`：`CheckpointCoordinator.java:1476`（超时调度）与 `GraphModelCheckpointExecutor.java:546`（有界 `get(checkpointTimeout, MILLISECONDS)`）——超时真实生效，作用域为 barrier→snapshot→ACK。
3. **JDBC 超时专项 grep**：5 个 connector 模块 `setQueryTimeout|socketTimeout|connectTimeout|loginTimeout` **零命中**——JDBC 写路径无任何语句级超时。
4. **CEP/RocksDB 配置面**：`NopCepConfigs` 仅缓存槽位与统计日志间隔（无过载旋钮，C8-002 口径复核成立）；`RocksDBOptionConfig` 仅内存旋钮（writeBuffer/blockCache/WBM/bloom），**无任何快照时长/超时旋钮**。

## 2. 证伪与修正记录（供复核引用）

- **「Kafka 源在背压下受 `max.poll.interval.ms` 踢组影响 → commit 失败循环」——证伪**。`KafkaSourceFunction` 采用 `consumer.assign(shard)` 手动分区指派（`KafkaSourceFunction.java:229-239`），javadoc 明言 no group coordination/no rebalance，且 auto-commit 强制关闭并带二次覆盖 typed 守卫（用户 props 复 enable.auto.commit 即 ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH）。无组协调即无踢组面、无 CommitFailedException——`max.poll.interval` 裕度冲突**不存在**，该源设计合格。
- **「`producer.send` 无回调（`KafkaTwoPhaseCommitSink.java:172`）→ 记录级失败静默丢弃（F9）」——证伪**。事务性 producer 的批级永久失败进入 abortable 错误状态，后续 `commitTransaction`（barrier 内 `saveState`，:203）typed 抛出——失败响亮；超限记录（>max.request.size）在 `send` 同步抛 RecordTooLargeException。B9「合格（对照组）」判定复核成立。
- **修正第 1 轮 F5 扫描结论**：第 1 轮 §4 「唯一无限期等待点 = C8-004 的 registry HTTP 读阶段」**不完整**——JDBC sink 的 `executeBatch`/`commit` 套接字读是第二个无限期等待点（见 C8-009），第 1 轮扫描只覆盖了显式等待 API（poll/await/join），漏了外部 I/O 套接字读形态。

---

## 3. 发现条目

### [nop-stream-state-connectors-C8-009] JDBC 2PC 提交路径无语句级超时，且 commit 发生在 checkpointTimeout 覆盖之外的完成通知阶段——挂死 DB 对 checkpoint 协议与指标面双盲（F5 第二个无限期等待点）

- **文件**: `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:657-738, 910-995`（commit/writeOps/writeInserts 全路径无 `setQueryTimeout`）；关联 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:176-205`（`finishCommit` 于 checkpoint 完成通知路径驱动 `commit(eid)`）
- **证据片段**:
  ```java
  // JdbcTwoPhaseCommitSink.commit —— checkpoint 完成后执行，此后不再有任何超时机制覆盖
  Connection connection = jdbcTemplate.openConnection(querySpace);   // L673
  ...
  if (!batch.isEmpty()) {
      writeOps(connection, batch);              // L701 → writeInserts: ps.executeBatch() L935/940
  }
  writeLedgerEntry(connection, ...);            // L705
  connection.commit();                          // L707 —— 无 setQueryTimeout(模块 grep 零命中)
  ```
- **严重程度**: P2
- **并发场景**: JDBC sink 的 commit 执行于 checkpoint **完成通知**路径（`finishCommit` → `commit(eid)`，屏障外、独立连接），与任务线程（invoke 缓冲/saveState 内存拷贝）并发；对端为进程外 DB。挂死形态无平台内第二线程参与，是「通知调用方 × DB 套接字读」的单点无限期阻塞。
- **触发时序**: T1：checkpoint N ACK 完成，`finishCommit(N)` 打开连接执行 `executeBatch`（L701）。T2：DB 因 GC 长停/网络半开/未设锁等待超时的行锁冲突挂起——`queryTimeout` 驱动默认 0（无限），套接字读无限期阻塞。T3：checkpointTimeout（600s）**不再适用**——checkpoint N 已完成，runtime 的超时调度（`CheckpointCoordinator.java:1476`）只覆盖 barrier→snapshot→ACK 阶段；通知路径无任何超时 → 既无失败也无指标（C8-008）。T4：epoch N+1 barrier 照常 saveState（内存拷贝入 pendingCommits），其 commit 排队在挂死调用之后 → 未提交 epoch 持续堆积，JDBC 下游可见数据滞后多个 epoch 而无任何错误信号。T5：仅当 DB 自愈（套接字被内核/FW 断开方可抛出）或进程级重启（`restoreFromEpoch` 重放 commit）才收敛；半开连接可挂至小时级。
- **现状**: 模块对「单批超大」有防御（maxBatchSize 分段 executeBatch）但对「单次调用无界慢」零防御；第 1 轮 F5 扫描以显式等待 API 为对象，漏掉外部 I/O 套接字读形态，本条修正该结论并登记第二等待点。对照：C8-004（Avro registry）为同族第一等待点，但本条另有独立加重面——commit 位于 checkpointTimeout 覆盖之外，连「超时后响亮失败」的兜底都不存在。
- **风险**: exactly-once 管线的 JDBC 可见性在 DB 挂起时静默停滞（读方看到的是 N-1 时刻），故障根因（DB）与现象（下游数据滞后）跨进程且双盲（无超时、无指标）；pendingCommits/台账滞留叠加 C8-003 缓冲无界放大堆占用；恢复依赖重启而非自愈。
- **建议**: ① `writeOps`/`writeInserts`/`commit` 前统一 `statement.setQueryTimeout(可配, 默认与 checkpointTimeout 同量级)`；② owner doc 声明 commit 阶段的超时契约并接入 C8-008 的最小 gauge 集（`jdbc.commit.inflight.age`）；③ 连接侧建议部署校验提示 socketTimeout 配置（DataSource 层为用户配置面，模块至少 fail-loud 校验缺失）。
- **失效面枚举**: ① `JdbcTwoPhaseCommitSink.commit/writeOps/writeInserts`（无 queryTimeout）——本条主面；② `JdbcTwoPhaseCommitSink.saveState`（纯内存拷贝，无外部 I/O）——合格；③ `AvroSchemaRegistryClient` HTTP 读（同族第一等待点，C8-004 已立案）；④ `KafkaSourceFunction.poll(200ms)`/`KafkaTwoPhaseCommitSink.flush/commitTransaction`（request.timeout.ms=30s 有界）——合格；⑤ `FileTwoPhaseCommitSink` fsync（本地盘内核有界）——合格；⑥ `BatchLoaderSource.loader.load`（用户注入函数，框架不为用户代码设超时为标准契约）——不适用；⑦ Debezium 引擎内部等待（队列有界；offset 面归 C8-001）——不适用。
- **信心水平**: 确定（无 queryTimeout 为 grep 直接事实；commit 位于完成通知路径为 core `finishCommit` 代码直接事实；「挂死」需 DB 半开/长停前提，与 C8-004 同判级口径）。
- **误报排除**: 非把 DataSource 用户配置推责给平台——语句级 queryTimeout 是模块自身未设界，连接池超时无法覆盖已建连接上的 executeBatch；非低频路径——每 checkpoint 每 subtask 必经 commit；非 F2——提交原子性完好（单事务 + 台账同事务），本条是纯可观测/可用性缺陷；与 C8-004 不重复——不同模块、不同机制（语句超时 vs HTTP request timeout）、且本条独有「屏障外盲区」加重面。
- **复核状态**: 已保留（P2 维持，2026-10-14 复核）——模块 grep setQueryTimeout/socketTimeout 零命中证实；commit 位于 checkpoint 完成通知路径证实（RunningTask.notifyCheckpointComplete:308-341 → finishCommit → commit(eid)；TaskManager.java:263-280 专用单线程 commit executor），checkpointTimeout（覆盖 barrier→snapshot→ACK）不触及该阶段成立；无超时即无自愈入口、挂死期间下游可见数据静默滞后的时序具体。触发前提=DB 半开/长停（与 C8-004 同判级口径），P2 恰当。合并口径裁定：C8-004 已归并 C6-008，本条独立保留（不同模块、语句级超时机制、独有「屏障外盲区」加重面）。补充佐证：commit 队列有界（plan 369 R5-CC-10）+ caller-runs 兜底意味着持续 DB 挂死最终会阻塞派发线程——放大面与风险判断一致，非反证；二轮复核：已保留（P2 维持，2026-10-14 二轮独立复核）——五连接器模块 setQueryTimeout/socketTimeout/connectTimeout/loginTimeout grep 零命中复核成立；writeOps/writeInserts 仅 maxBatchSize 分段、无语句级超时（:910-995）；commit 位于 checkpoint 完成通知路径复核成立（RunningTask.java:321/326 → finishCommit；TwoPhaseCommitSinkFunction.java:176-206）；git log 无修复提交

### [nop-stream-state-connectors-C8-010] Barrier 内同步写放大路径（RocksDB 默认全量快照 / file sink epoch 落盘）的时长与 checkpointTimeout 无耦合守卫——状态增长越过时长预算后进入不可自愈的「checkpoint 超时→3 次失败→FAIL→重启→重演」循环

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:826-835`（默认全量路径入口）；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/CheckpointConfig.java:27-29, 34`（interval=60s / timeout=600s / consecutiveFailures=3）；对照守卫先例 `CheckpointConfig.java:271-286`（`validateUnalignedConfig`）；同族 `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:233-266`
- **证据片段**:
  ```java
  // RocksDBKeyedStateBackend.snapshotState —— barrier 内任务线程同步执行，时长 ∝ 状态总体积
  public StateSnapshot snapshotState() throws Exception {
      if (incrementalCheckpointEnabled) {        // 默认 false（L119），无任何配置守卫/校验
          ...
          return snapshotIncremental();
      }
      return RocksDBSnapshotSerDe.snapshotState(this);   // L835：全迭代 + 逐条反序列化 + JSON 写出
  }
  // CheckpointConfig: DEFAULT_CHECKPOINT_INTERVAL=60s, DEFAULT_CHECKPOINT_TIMEOUT=600s,
  // maxConsecutiveCheckpointFailures=3 —— 时长预算固定，与状态体积零耦合
  ```
- **严重程度**: P2
- **并发场景**: 单任务线程在 barrier 处理中同步执行快照（第 1 轮已证的同步语义）；runtime 侧 `CheckpointCoordinator.java:1476` 超时调度与 `GraphModelCheckpointExecutor.java:546` 有界 get 按 600s 判定——「任务线程的快照时长」与「runtime 的固定超时预算」之间无任何模块内校验、旋钮或声明耦合（`RocksDBOptionConfig` 无快照时长旋钮）。
- **触发时序**: T1：RocksDB 状态增长至 S（GB 级即声明包络内，`nop-stream.md` state 层定位）；T2：barrier N 到达，`snapshotState` 走默认全量路径，RocksDBSnapshotSerDe 全迭代反序列化 + JSON 中间态写出（第 1 轮 C8-005 证据 L309-324），时长 ∝ S（10-30MB/s 量级估算 → 6GB+ 即逼近 600s 预算）；T3：runtime 超时判定 checkpoint N 失败，而任务线程的快照仍在跑（幽灵快照，与 C8-003 的幽灵任务形态同构）；T4：连续 3 次失败（maxConsecutiveCheckpointFailures=3）→ job FAIL；T5：重启恢复自上一好 checkpoint（状态规模 ≈ S，不因重启缩小）→ 下一 barrier 重演超时 → 不可自愈循环，直至人工干预（缩状态/调大 timeout/改开增量）。δ 同时拉长 Kafka 事务年龄——与 C8-006 复合（同 C8-005 已登记的放大链）。
- **现状**: 默认配置组合（interval 60s / timeout 600s）对中小状态有 10× 裕度——这是本条判 P2 而非 C8-006 式 P1 的关键差异（C8-006 的默认裕度为 0）；但裕度是**常量**而快照时长是**数据规模函数**，仓库已有配置裕度 fail-fast 惯例（`validateUnalignedConfig` 强制 threshold < alignmentTimeout），同构的「快照时长包络 × checkpointTimeout」校验不存在，owner doc 亦无声明。file sink 的 `saveState`（写 tmp + fsync，时长 ∝ epoch 缓冲体积，而缓冲无界见 C8-003）为同族兄弟。
- **风险**: 「声明包络内、堆不 OOM（C8-005 的前提不满足时）仍必然失败」的可用性崩溃形态：小堆慢盘或纯 CPU 慢序列化即可在 OOM 之前先撞超时预算；失败循环响亮但无自愈入口，且每次循环伴随源端全量重放（放大上游读放大）。
- **建议**: ① 部署期校验（对照 `validateUnalignedConfig` 惯例）：声明/观测的状态体积 × 估算序列化速率 ≤ checkpointTimeout × 安全系数，越界 typed 提示开增量或调大 timeout；② 快照耗时 gauge 并入 C8-008 最小指标集（`checkpoint.snapshot.duration.millis`）；③ 与 C8-005 建议合流执行（增量路径默认化 / 全量路径流式化，另立 plan）；④ 复核阶段顺带裁定 runtime `JobCoordinatorMain.java:150-152` 的 `: 10_000L` fallback 语义——若该路径同样服务 barrier-checkpoint 预算，默认裕度将从 10× 收缩至 1/60×，本条判级需上调。
- **失效面枚举**: ① `RocksDBKeyedStateBackend.snapshotState` 默认全量路径——本条主面；② `FileTwoPhaseCommitSink.saveState`（写+fsync，时长 ∝ 无界 epoch 缓冲[C8-003]）——同族同修；③ `KafkaTwoPhaseCommitSink.saveState`（flush + commitTransaction RPC，request.timeout.ms=30s 有界响亮）——合格；④ `JdbcTwoPhaseCommitSink.saveState`（内存拷贝，毫秒级）——合格；⑤ `BatchConsumerSinkFunction.flush`（batchSize=100 有界）——合格；⑥ CEP Memory 后端全量 checkpoint（nop-stream-core，越界注记）；⑦ runtime 超时消费点（`CheckpointCoordinator`/`GraphModelCheckpointExecutor`，越界上下文非发现对象）。
- **信心水平**: 确定（无守卫/无旋钮/超时强制生效/同步语义均为代码直接事实；「循环触发」依赖状态体积越过时长预算——标注数据规模前提，包络内可达，精确阈值留复核按真实序列化速率裁定）。
- **误报排除**: 非与 C8-005 重复——C8-005 的失效层是内存物化（OOM + barrier 时延），本条失效层是**时长预算与固定超时的可用性裕度**（不 OOM 也失败：堆够但序列化慢即触发），修复方向不同（守卫/观测 vs 流式化）；非「runtime 已有超时即合格」——超时只让失败响亮，不能阻止进入失败循环，缺陷是守卫缺失而非失败不可见；非把「大数据量必然慢」当缺陷——固有的是「大状态快照慢」，不固有的是「慢无上界声明、无校验、无自愈出口三缺」。
- **复核状态**: 已保留（P2 维持，2026-10-14 复核）——默认全量快照路径（RocksDBKeyedStateBackend.java:826-836）、interval/timeout/failures=60s/600s/3（CheckpointConfig.java:27-29,35）、`validateUnalignedConfig` 裕度校验先例（:271-286）、RocksDBOptionConfig 无时长旋钮逐项证实；「常量预算 vs 数据规模函数时长无耦合守卫」成立。建议④裁定：JobCoordinatorMain.java:148-152 的 fallback 证实（pipeline artifacts 未声明时 interval=1000ms/timeout=10_000L，ClusterPipelineFactory 场景启动路径）——该路径裕度从 10× 收缩且 barrier 每 1s 触发快照，守卫缺失论点被加强；属 runtime 启动路径（越界），不改变本条 P2 判级，移交 runtime 侧知悉。与 C8-005 的失效层区分（时长预算 vs 内存物化）成立，不重复；二轮复核：已保留（P2 维持，2026-10-14 二轮独立复核）——默认全量路径（:825-836）、60s/600s/3 预算（CheckpointConfig.java:27-28,35）、validateUnalignedConfig 裕度校验先例（:272-286）、JobCoordinatorMain fallback interval=1000ms/timeout=10_000L（建议④）逐项复核成立；与 C8-005（内存物化）/C8-013（增量哈希时长）失效层三分不重复；git log 无修复提交

---

## 4. 边界清单残余闭环（前两轮遗留项逐项裁定）

| 残余项 | 来源 | 本轮裁定 |
|---|---|---|
| `max.poll.interval.ms` 踢组裕度 | 第 1 轮 B3 未显式裁定 | **证伪不存在**：assign 手动指派 + auto-commit typed 守卫（§2-1） |
| `producer.send` 无回调静默丢弃面 | 第 1 轮 B9 未展开 | **证伪**：事务 abortable 状态 → commitTransaction 响亮（§2-2） |
| F5「唯一无限期等待点 = registry」 | 第 1 轮 §4 结论 | **修正**：JDBC 套接字读为第二等待点（C8-009） |
| checkpointTimeout 是否覆盖 sink commit | C8-009 立项前提 | **裁定**：不覆盖（core `finishCommit` 于完成通知路径执行；runtime 超时仅及 barrier→ACK） |
| RocksDB 快照旋钮面 | 第 1 轮 C8-005 建议② | `RocksDBOptionConfig` 全量核查：仅内存旋钮，无时长旋钮 → 支撑 C8-010 |
| runtime `JobCoordinatorMain:150-152` 的 10s fallback | 本轮新发现线索 | 语义不明（runtime 越界），移交复核裁定（C8-010 建议④） |
| 第 2 轮 §2 各证伪（close 桥/Debezium close/BatchLoader offset/enumerator） | 第 2 轮 | 复核成立，无翻案 |
| pendingCommits 失败重试累积 / kafka 恒 TRUE 标记 | C8-003 ⑤ 移交 C4/C7 | 维持移交，不在 C8 立案 |

## 5. 计数与移交

| 严重程度 | 本轮数量 | 条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 2 | C8-009 / C8-010 |
| P3 | 0 | — |

三轮累计：P1×2（C8-001、C8-006）、P2×8（C8-002…C8-005、C8-007…C8-010）。

**终止建议**：C8-006 同族（超时裕度冲突面）经本轮全量盘点已**闭合**——8 模块 + core 的时间配置面逐一面核对完毕，两个未立案面均为证伪（§2-1/2-2），两个新立案面（C8-009/010）已尽；边界清单残余全部闭环（§4）。继续深挖第 4 轮的边际收益判断为低（剩余面均已证伪或越界），建议转入复核阶段。

移交复核阶段关注点：① C8-009 与 C8-004 的合并口径（同族两等待点：合并为「外部依赖等待点无界」一档，还是按模块分立）；② C8-010 建议④——runtime 10s fallback 语义裁定（可能上调判级）；③ C8-010 与 C8-005/C8-006 的三缺陷复合链（大状态 → 慢快照 → δ 拉长 → 事务超时）是否按「根因 + 失效层」合并立项；④ 第 1 轮 F5 扫描结论的修正需回写 C8-backpressure.md §4（复核阶段执行，本轮不改动已归档文件）。

---

# C8 背压·流控·过载行为 — nop-stream-state-connectors · 维度复核结论

## 维度复核结论

- **复核人**: 独立复核子代理（与初审/深挖不同任务实例）
- **复核日期**: 2026-10-14
- **复核方式**: 本轮解除盲审（可读 `ai-dev/audits/` 历史审计与 git log，为保留发现标注现状归属）；逐条重读 live code + 调用方三态判定（已保留/降级/驳回）。判定纪律：写不出具体触发时序的并发发现降 P2；已声明边界+守卫不是缺陷。
- **总体结论**: **10 条全部已保留（P1×2 / P2×8），0 降级，0 驳回**；其中 4 条为同 campaign 跨维度重复（7.1 归并不重复计数），1 条（C8-008）证据面经证伪修正后限缩范围维持 P2。两条 P1 复核后均增强：C8-001 的 engine 侧时点疑点已裁决（信心升「确定」），C8-006 的零裕度为算术事实。
- **前轮移交关注点裁定**:
  - R1-①（C8-001 engine 侧 offset 刷新时点）：已裁决——`DebeziumEngineWrapper.java:154` 以 `notifying(Consumer)` 形态构建引擎，Debezium EmbeddedEngine 对 Consumer 形态在 handler 返回（即 B2 桥 `queue.put` 成功）后即 markProcessed、批末 markBatchFinished 推进 offset store；offset 推进严格先于下游 emission，偏斜结构性成立，P1 维持。
  - R1-②（C8-002 升 P1）：驳回升级——watermark 停滞为部署前提而非负载必然；事件时间缓冲本体是语义必需；立案核为「过载行为/深度观测/包络声明三缺」，P2 恰当。
  - R1-③（C8-003 与 C5 合并）：确认归并 C5-005（同一 Jdbc+File currentBuffer 本体）。
  - R2 移交（C8-007 与 F4 族历史口径）：live code 证实台账现存无清理路径；同 campaign C5-006 先立案、C3-002 已由 C3 复核升 P1 为主立案并列本条为归并对象；历史审计（2026-08-13 invariant-loop 两轮）均未就台账清理立案（其 AR-06 为死计数器/吞异常面），无「已修复回退」情形。
  - R3-①（C8-009 与 C8-004 合并口径）：C8-004 归并 C6-008（超时本体唯一立案）；C8-009 独立保留（不同模块、语句级超时机制、独有「屏障外盲区」加重面）。
  - R3-②（runtime 10s fallback）：裁定成立——`JobCoordinatorMain.java:148-152` 在 pipeline artifacts 未声明时 fallback interval=1000ms / timeout=10_000L（ClusterPipelineFactory 场景启动路径）；该路径裕度远小于 600s 主路径，加强 C8-010 守卫缺失论点但不升判级（runtime 越界，移交 runtime 侧知悉）。
  - R3-③（三缺陷复合链合并立项）：不合并——C5-002（内存物化，P1 主立案）/ C8-010（时长预算与固定超时耦合）/ C8-006（Kafka 事务超时零裕度）根因与失效层各异，分立立案；7.1 修复排批建议同批评估。
  - R3-④（第 1 轮 §4「唯一无限期等待点」修正回写）：修正内容复核成立（JDBC 套接字读为第二等待点，见 C8-009）；依本轮「不改动归档正文」纪律不回写 §4 原文，以本裁定记录为准。
- **深挖 §2 证伪记录复核**: 4 项（桥 close `queue.clear()` 信号逃逸 / Debezium close 互等 / BatchLoaderSourceReader offset / DebeziumTableSplitEnumerator）全部复核成立，无翻案；`clear()` 探针转 C10 回归测试立项的建议维持移交。

### 逐条判定

| ID | 初判 | 复核判定 | 复核要点 | 现状归属 |
|---|---|---|---|---|
| C8-001 | P1 | **已保留 P1** | `snapshotState` 直抬引擎 offset、无 last-emitted 回退（DebeziumCdcSourceReader.java:255-279）；engine 侧经 `DebeziumEngineWrapper.java:154` Consumer 形态裁决：offset 于 handler 返回（queue.put 成功）后即推进，先于下游发射，偏斜窗口=队列在途 ≤256 结构性成立，信心升「确定」；触发时序具体。 | 新发现，无历史先例（plan 2343 M-2/M-4 未考虑队列桥偏斜；legacy 直连 ≈1 条偏斜为 WI2 前既有语义） |
| C8-002 | P2 | **已保留 P2** | `bufferEvent` 无容量/过载分支（CepOperator.java:1041-1050）、「仅晚到可观测」逐行证实；idle 分区→watermark 冻结→无界积压时序具体。升 P1 驳回：watermark 停滞为部署前提；缓冲本体是语义必需（与 C5「用法学区」一致），立案核=三缺。 | campaign 唯一登记（C5 判 elementQueueState OK 系缓冲本体口径，与本条立案核不冲突） |
| C8-003 | P2 | **已保留 P2（归并）** | currentBuffer 无界（Jdbc:233/492、File:136/239）、提交侧 maxBatchSize 有界对照成立；触发=时间×速率无竞态。 | 同 campaign 重复 ←C5-005（同一 Jdbc+File 本体，C5 先立案）；独立残值=B7/B8 过载行为视角+spill 建议，并入 C5-005 执行 |
| C8-004 | P2 | **已保留 P2（归并）** | connectTimeout-only + 全部 request 无 `.timeout()`（AvroSchemaRegistryClient.java:48,55-58,102-129）+ encode 锁内双 HTTP（AvroWireFormat.java:54-60）逐行证实；JDK HttpClient 读阶段无超时，挂死时序具体。 | 同 campaign 重复 ←C6-008（C7 复核已裁定同一缺陷归并；同族 C2-002/C4-003/C3-003 五向）；独立残值=锁内 I/O 互阻放大面，作 C6-008 执行注记 |
| C8-005 | P2 | **已保留 P2（归并）** | entries 全量物化（RocksDBSnapshotSerDe.java:309-324）、默认 false（RocksDBKeyedStateBackend.java:119）、任务线程同步（:826-836）逐项证实。 | 同 campaign 重复 ←C5-002（同一文件同一行段，C5 以 P1 先立案为主）；C8 角度（同步自背压）由 C8-010 承接 |
| C8-006 | P1 | **已保留 P1** | `newProducer()` 不设 transaction.timeout.ms（:134-148）、事务跨 checkpoint 间隔（:167-214）、core 默认 60s（CheckpointConfig.java:27）、重启上限 3（:35）逐项证实；裕度算术零（事务年龄=interval+快照时长>60s 恒成立），失败循环时序具体；EXPERIMENTAL 豁免仅覆盖重复窗口。残余不确定性（协调器惰性过期粒度）不构成降级理由。 | 新发现（E2E 用默认 60s 间隔但窗口仅数十秒从未触界，通过不构成反证；无历史先例） |
| C8-007 | P2 | **已保留 P2（归并）** | 台账仅 INSERT+EXISTS、无 DELETE/prune（:705-707,737,1146-1153）证实；恢复重放走 pendingCommits、不依赖台账历史成立。 | 同 campaign 三向重复 ←C5-006（先立案）/C3-002（C3 复核升 P1 主立案，已列本条为归并对象）；无独立残值 |
| C8-008 | P2 | **已保留 P2（证据面修正）** | 证伪：失效面⑤不成立——`RocksDBMetricsRecorder`（2026-09-03 plan-2216-1 引入，早于本审计）已在 open 路径注册 6 gauge（RocksDBKeyedStateBackend.java:279），「仅 CEP 一处指标」表述错误。修正后仍成立：B1/B2 队列深度、B3 lag、B7/B8 sink 缓冲、B9 事务年龄、B14 registry 延迟、B10 CEP 桶深度确零 gauge（connector grep 复证）。 | 新发现但范围限缩（RocksDB 面已由 C1-002/C7-004/C6-010 审及）；CEP counter「正例」现状=2026-08-13 AR-06（死计数器 P2）修复产物 |
| C8-009 | P2 | **已保留 P2** | setQueryTimeout/socketTimeout 零命中；commit 于完成通知路径（RunningTask.notifyCheckpointComplete:308-341 → finishCommit；TaskManager.java:263-280 专用 commit executor）在 checkpointTimeout 覆盖外证实；挂死→下游静默滞后时序具体，前提=DB 半开/长停。 | campaign 唯一登记（C8-004 归并后本条为 JDBC 等待点唯一立案）；有界 commit 队列 caller-runs 兜底为放大佐证非反证 |
| C8-010 | P2 | **已保留 P2** | 默认全量快照、60s/600s/3 预算、`validateUnalignedConfig` 先例（CheckpointConfig.java:271-286）、无时长旋钮逐项证实；「常量预算 vs 规模函数时长无耦合」成立。建议④已裁定（fallback 证实，论点加强、判级不变）。 | campaign 唯一登记；与 C8-005 失效层区分成立（不重复） |

## 最终保留项

| ID | 标题（缩略） | 复核定级 | 轮次 | 现状归属 |
|---|---|---|---|---|
| C8-001 | Debezium FLIP-27 reader checkpoint offset 领先已发射（队列偏斜）→恢复静默丢 ≤256 条 | P1 | R1 | 唯一登记 |
| C8-006 | Kafka 2PC epoch 事务存活期与 checkpoint 间隔零裕度（默认 60s==60s）→触界失败循环 | P1 | R2 | 唯一登记 |
| C8-002 | CEP 匹配缓冲无过载行为、无深度观测、无包络声明（watermark 停滞即无界积压） | P2 | R1 | 唯一登记 |
| C8-003 | JDBC/File 2PC epoch 缓冲无上界 | P2 | R1 | 重复 ←C5-005（归并） |
| C8-004 | Avro registry 无 request timeout + encode 锁内 I/O | P2 | R1 | 重复 ←C6-008（归并；残值=锁内互阻注记） |
| C8-005 | RocksDB 全量快照整库物化进堆 | P2 | R1 | 重复 ←C5-002（P1 主立案，归并） |
| C8-007 | JDBC 2PC 台账只增不删 | P2 | R2 | 重复 ←C3-002（P1 主）/C5-006（归并） |
| C8-008 | connector source/sink/CEP 边界积压深度零遥测（原「8 模块系统性零指标」经证伪修正限缩） | P2 | R2 | 唯一登记（修正后范围） |
| C8-009 | JDBC 2PC 提交无语句级超时且位于 checkpointTimeout 盲区（第二无限期等待点） | P2 | R3 | 唯一登记 |
| C8-010 | barrier 同步写放大时长与 checkpointTimeout 无耦合守卫 | P2 | R3 | 唯一登记 |

**复核计数：保留 10 / 降级 0 / 驳回 0。P0=0；P1=2（C8-001、C8-006）；P2=8（002/003/004/005/007/008/009/010，其中 008 为证据修正后限缩维持）。**

**现状标注统计**：campaign 唯一登记（新发现）5 条（001/002/006/009/010）；同 campaign 跨维度重复（7.1 归并不重复计数）4 条（003←C5-005、004←C6-008、005←C5-002、007←C3-002/C5-006）；证据面证伪修正后限缩维持 1 条（008）。修复排批提示：4 条归并项应与对应维度发现合并计一项；两条 P1 建议与 C5-002（P1）/C3-002（P1）同批评估。

---

# C8 背压·流控·过载行为 — nop-stream-state-connectors · 二轮独立复核追加（2026-10-14）

## 维度复核结论（独立复核子代理 · 二轮复核）

- **复核人标识**: nop-stream-state-connectors C8 维度二轮独立复核子代理（与初审者、一轮维度复核者不同任务实例）
- **复核日期**: 2026-10-14
- **复核输入**: 本文件全文（含第 1 轮 C8-001…005、已并入的深挖第 2/3 轮 C8-006…010、一轮维度复核结论）+ 同目录 `C8-round2.md` 全文（C8-011/012/013），合计 13 条
- **复核方式**: 解除盲审（历史审计 summary/结论节 + git log 仅用于现状标注；仍不读 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/）；对全部 13 条逐一重读 live code 验证文件/行号/证据真实性、积压与丢失链、判级口径（P0=主路径语义违约致数据损坏/包络内必然资源耗尽；P1=崩溃恢复静默丢数据、负载上升必然队列溢出/死锁；P2=无观测/边界配置缺陷/理论风险；P3=优化候选）；三态判定 + 现状标注
- **反驳自检（按复核指令定向执行）**:
  - ① C8-001 丢失量上界 ≤256 条/检查点——**成立**：引擎 handler 返回（`queue.put` 成功）即经 `markProcessed`/批末 flush 推进 offset store（`DebeziumEngineWrapper.java:154` Consumer 形态 + `DebeziumMessageSource.java:143` dispatchEvent 接线），任意时刻 store 领先已发射位的距离 = 队列在途（≤256）+ handler 在途（1）条；checkpoint 快照读 store 现值，恢复播种经 `initializeState`（DebeziumCdcSourceReader.java:107-123），队列（transient）随之蒸发。
  - ② 无界缓冲隐藏 cap 排查——JDBC/File `currentBuffer` 无 cap 字段（仅提交侧 `maxBatchSize=1000` 分段）；CEP `elementQueueState` 无 cap（`NopCepConfigs` 仅缓存槽位与统计间隔两组旋钮）；两桥 `ArrayBlockingQueue(256)` 仅条数维、无字节维（grep maxRecordBytes 零命中）；Avro 两缓存键空间受 registry 有限集约束（发现已自注，不计无界）。无隐藏 cap 反例。
  - ③ 热路径核实——Avro encode/decode 每记录（serializer/deserializer 注入 Kafka sink/source 逐记录调用）；message sink `consume` 每记录主路径；JDBC commit 每 checkpoint 每 subtask（barrier/通知路径，非每记录——C8-009 表述准确，不属虚报热路径）。
- **总体结论**: **13 条全部保留（P1×2 / P2×11），0 降级，0 驳回**。其中 4 条为同 campaign 跨维度重复（7.1 归并不重复计数），C8-008 维持一轮证据面修正后的限缩范围。两条 P1 经二轮复核均加强：C8-001 偏斜机制双侧逐行闭环；C8-006 的事务起点实为 barrier N 的 saveState 内 `beginTransaction`（比原「首个 invoke」推演更强），默认组合下事务年龄恒 >60s。
- **逐条判定表**:

| ID | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C8-001 | P1 | 保留 P1 | reader 直抬引擎 offset + 无 last-emitted 回退、engine Consumer 形态 handler 返回即推进 offset 双侧逐行复核；≤256 上界复推成立 | 新发现未修复（历史审计「偏斜」零命中、git log 无修复提交） |
| C8-002 | P2 | 保留 P2 | bufferEvent 无容量/过载分支 + 仅晚到可观测 + NopCepConfigs 无过载旋钮复核成立；round2 §2.3 timer ledger「条件有界」改判采纳并入，不改判级 | campaign 唯一登记未修复 |
| C8-003 | P2 | 保留 P2（归并 C5-005） | currentBuffer 无界 + maxBatchSize 仅提交侧复核成立；归并主立案存在性核对 | 同 campaign 重复（←C5-005）未修复 |
| C8-004 | P2 | 保留 P2（归并 C6-008） | connectTimeout-only + 全部 request 无 `.timeout()` + encode 锁内双 HTTP 复核成立 | 同 campaign 重复（←C6-008）未修复 |
| C8-005 | P2 | 保留 P2（归并 C5-002） | entries 全量物化 + 默认 false + 任务线程同步快照复核成立 | 同 campaign 重复（←C5-002，P1 主立案）未修复 |
| C8-006 | P1 | 保留 P1 | 零裕度算术复核成立且事务起点前移至 barrier N saveState（年龄恒>60s）；EXPERIMENTAL 豁免仅覆盖残余重复窗口不覆盖本条 | 新发现未修复（「transaction.timeout」历史审计仅本 campaign 命中） |
| C8-007 | P2 | 保留 P2（归并 C5-006/C3-002） | 台账仅 INSERT + EXISTS 读、无任何清理路径复核成立 | 同 campaign 重复（←C5-006/C3-002）未修复；2026-09-27 quality-r2 的「台账」命中为 CEP timer 性能面，与本条无关 |
| C8-008 | P2 | 保留 P2（证据修正限缩） | 一轮对失效面⑤的证伪独立成立（RocksDBMetricsRecorder 实存，:278/:83）；connector 六模块零指标复证；CEP counter = AR-06 修复产物（plan 368 Phase 7 提交 2bb7c013a7）核实 | 新发现未修复（RocksDB 面已由 C1-002/C7-004/C6-010 审及） |
| C8-009 | P2 | 保留 P2 | 超时 API 五模块零命中 + commit 位于 checkpoint 完成通知路径（RunningTask→finishCommit）在 checkpointTimeout 覆盖外复核成立 | campaign 唯一登记未修复 |
| C8-010 | P2 | 保留 P2 | 常量预算 vs 规模函数时长无守卫复核成立；JobCoordinatorMain fallback（建议④）证实 | campaign 唯一登记未修复 |
| C8-011 | P2 | 保留 P2 | 2-arg send→null options 链路 + sendTimeout 旋钮被 Kafka 后端真实消费 + SysDao 同步 DB 写逐行复核成立；与 C8-004/C8-009 维持三分立（独有「旋钮 opt-out」形态） | 新发现未修复 |
| C8-012 | P2 | 保留 P2 | 两桥仅条数界无字节界 + 族内对照（file 16MB 守卫 / Kafka fetch 字节界）复核成立 | 新发现未修复 |
| C8-013 | P2 | 保留 P2 | 全 SST 无条件 sha256Hex + 无跳过逻辑/旋钮复核成立；对 C8-005 失效面②的时长维修正采纳（内存维判定不变） | 新发现未修复 |

- **不确定项**: 无降级级不确定项。唯一残余不确定性为 C8-006 的 Kafka 协调器惰性过期粒度（影响触发概率而非机制成立性；零裕度 + 无守卫 + 无声明为代码直接事实，不足以降级）。
- **需子项复核的高风险项**: 无。

## 最终保留项（二轮复核）

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|---|---|---|---|---|
| C8-001 | P1 | nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceReader.java | FLIP-27 reader checkpoint offset 领先已发射记录（256 队列偏斜），崩溃恢复静默丢 ≤256 条/检查点 | 新发现未修复 |
| C8-006 | P1 | nop-stream/nop-stream-connector-kafka/src/main/java/io/nop/stream/connector/kafka/KafkaTwoPhaseCommitSink.java | epoch 事务存活期与默认 60s checkpoint 间隔零裕度、无守卫，持续负载下触界进入失败循环 | 新发现未修复 |
| C8-002 | P2 | nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java | CEP 匹配缓冲无上界/无过载行为/无深度观测，watermark 停滞即无界积压 | 唯一登记未修复 |
| C8-003 | P2 | nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java（同族 FileTwoPhaseCommitSink.java） | JDBC/File 2PC epoch 缓冲 barrier 间无上界 | 重复 ←C5-005（归并） |
| C8-004 | P2 | nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java | registry HTTP 无 request timeout + encode 锁内 I/O 互阻放大 | 重复 ←C6-008（归并） |
| C8-005 | P2 | nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBSnapshotSerDe.java | 默认全量 JSON 快照整库物化进堆（内存维由 C5-002 P1 主立案承接） | 重复 ←C5-002（归并） |
| C8-007 | P2 | nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java | JDBC 2PC 台账只增不删（无 prune/TTL/清理工具） | 重复 ←C5-006/C3-002（归并） |
| C8-008 | P2 | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceReader.java 等（connector 六模块 source/sink/CEP 边界） | 边界积压深度零遥测（B1/B2 深度、B3 lag、B7/B8 sink 缓冲、B9 事务年龄、B14 registry 延迟、B10 CEP 桶深；RocksDB 面除外） | 唯一登记未修复（修正后范围） |
| C8-009 | P2 | nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java | JDBC commit 无语句级超时且位于 checkpointTimeout 覆盖外（第二无限期等待点） | 唯一登记未修复 |
| C8-010 | P2 | nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java | barrier 内同步快照时长与固定 checkpointTimeout（60s/600s/3）无耦合守卫 | 唯一登记未修复 |
| C8-011 | P2 | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSinkFunction.java | message sink 每记录热路径同步 send opt-out 平台 sendTimeout 机制（第三无限期等待点） | 新发现未修复 |
| C8-012 | P2 | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceReader.java（同族 DebeziumCdcSourceReader.java） | push→pull 桥 256 槽位仅条数界无字节界，大 payload 场景堆失控 | 新发现未修复 |
| C8-013 | P2 | nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalSnapshotStrategy.java | 增量快照每 checkpoint 全 SST 重哈希，时长 ∝ 状态总体积（C8-005/C8-010 推荐逃生门自身的时长放大面） | 新发现未修复 |

**二轮复核计数：保留 13 / 降级 0 / 驳回 0（P1×2：C8-001、C8-006；P2×11：其余全部）。** 现状合计：新发现/唯一登记未修复 9 条（001/002/006/008/009/010/011/012/013，含 C8-008 修正后范围），同 campaign 归并 4 条（003←C5-005、004←C6-008、005←C5-002、007←C5-006/C3-002），无「已修复」项（分支 concurrency-audit-remediation 截至本轮复核尚无触及 8 模块的修复提交）。修复排批：4 条归并项与对应维度发现合并计一项；两条 P1 与 C5-002/C3-002（P1）同批评估；C8-011/C8-004/C8-009 三个外部依赖等待点可并入同一「外部依赖超时契约」修复项分模块执行。
