# C4 线程池与执行器治理 — nop-stream-state-connectors 初审报告

- **审计维度**: C4（线程池与执行器治理）
- **审计对象**: nop-stream-state-connectors（8 个模块，src/main/java，140 files / 25,458 lines）
  - `nop-stream/nop-stream-cep`（74 files）、`nop-stream/nop-stream-rocksdb`（19 files）
  - connector 族：`nop-stream-connector`（19）、`nop-stream-connector-batch`（9）、`nop-stream-connector-jdbc`（4）、`nop-stream-connector-debezium`（8）、`nop-stream-connector-kafka`（4）
  - `nop-stream/nop-stream-format-avro`（3 files）
- **审计日期**: 2026-10-14
- **审计方法**: 盲审（未读 ai-dev/audits/、plans/、bugs/、lessons/ 任何文件）；以当前 live code 为准。基线 `_tmp/cra-baseline-nop-stream-state-connectors.txt` C4 节圈定候选点，随后对全部线程/池创建点做全文件级通读（不是仅看 grep 行）；每个池核对参数、队列、拒绝路径、关闭路径、生命周期对称性（F8）与任务隔离。
- **Owner doc**: `docs-for-ai/03-modules/nop-stream.md`、`docs-for-ai/03-modules/nop-stream-connectors.md`

---

## 检查范围说明

**扫描手段**：对 8 个模块 src/main/java 全量执行基线 C4 grep 族（`new Thread(` / `ExecutorService|Executors\.|ThreadPool` / `scheduleAtFixedRate|scheduleWithFixedDelay|Timer` / `commonPool|parallelStream|*Async|CompletableFuture` / `interrupt|shutdown|awaitTermination|setDaemon` / 平台设施 `GlobalExecutors|IThreadPoolExecutor|NamedThreadFactory|ForkJoin`），全部命中点逐文件通读上下文；CEP（74 files）与 RocksDB（19 files）无任何线程/池/定时器创建点，按其线程交互面（CEP 缓存统计定时器、RocksDB 后台线程配置、metrics recorder）做了定向核对。

**边界声明（平台基础设施不重审，只审使用方式）**：
- Debezium 嵌入引擎自身的线程（`DebeziumEngineWrapper` / `DebeziumMessageSource`，位于 `nop-message/nop-message-debezium`）不在 8 模块范围内；本报告只审 in-scope 代码对它的启动/停止/监听器注册解绑使用。核对结果：`DebeziumCdcSourceFunction.run()` finally 中 `unregisterFailureListener + subscription.cancel + source.stop()` 成对（wrapper 侧 `notifyEngineFailure` 对 listener 异常有 catch 守护，不会杀引擎 worker 线程）。
- `ProcessingTimeService` / `InternalTimerService` 的调度线程（`nop-stream-core` `TaskProcessingTimeService` + `ProcessingTimeServiceDriver`）属 core 基础设施；其 mailbox 串行契约（回调在 task 线程执行）已用于本报告对 CEP 定时器的判定。
- JDBC 连接池本体（`nop-dao` `IJdbcTemplate` 内部池）不重审；只审借还配对。
- KafkaProducer/KafkaConsumer 内部线程（kafka-clients 4.3.1）不重审实现，只审其生命周期归宿；Sender 线程 daemon 标志经 4.3.1 jar 反编译确认（`SenderThread.<init>(..., iconst_1)` = daemon=true）。
- `_` 前缀生成文件不在发现对象内（本批模块无命中）。

**结论概览**：8 个模块在 C4 面上整体治理良好——两个嵌入宿主 reader 线程均有界（每 subtask 恰 1 个、具名、daemon、有界队列桥接、close 走 cancel+join(5s)+typed 超时上抛）；CEP 唯一定时任务（缓存统计）是一次性注册 + fire 后重臂、close 取消，且因 mailbox 串行契约无 close/re-arm 竞态；RocksDB 后台线程经 `setMaxBackgroundJobs` 配置有界、close 链完整；批/文件连接器生命周期对称。**发现 1 个 P1（kafka-2pc sink 的 KafkaProducer 无任何关闭路径，normal stop 即泄漏 daemon sender 线程）与 2 个 P2（Avro registry HttpClient 无关闭路径；registry HTTP 等待点无请求级超时）。**

---

## 线程池清单表

判定口径：有界 = 线程数/队列/内存任一失控面均有约束；关闭 = 持有者生命周期终点是否有释放路径；判定 = OK / 泄漏 / 有界缺陷。

| # | 线程/池 | 创建点 | 参数/线程模型 | 队列/缓冲 | 拒绝策略 | 关闭路径 | 判定 |
|---|---|---|---|---|---|---|---|
| 1 | `message-source-reader-<subtask>` | `nop-stream-connector/.../MessageSourceReader.java:87` | 每 reader 恰 1 个专用 daemon 线程（具名，可观测），host legacy `MessageSourceFunction.run()` | `ArrayBlockingQueue(256)` 有界桥接队列，queue-full = runner 阻塞 `put`（=legacy 同步 collect 背压语义） | N/A（阻塞即背压，非丢弃） | `close()`: `running=false` + `embeddedFunction.cancel()` + `join(5000ms)`，超时 typed 上抛，`queue.clear()`（顺带释放 put 阻塞的投递线程——`ArrayBlockingQueue.clear` 会 signal notFull） | OK |
| 2 | `debezium-source-reader-<subtask>` | `nop-stream-connector-debezium/.../DebeziumCdcSourceReader.java:132` | 每 reader 恰 1 个专用 daemon 线程（具名），host 嵌入引擎宿主函数；引擎自身线程在 nop-message-debezium（边界外），由 `cancel()/source.stop()` 请求停止 | `ArrayBlockingQueue(256)` 同上 | N/A 同上 | `close()` 同 #1（cancel → join(5s) → typed 超时 → clear） | OK |
| 3 | KafkaProducer sender 线程（`kafka-producer-network-thread \| ...`） | `nop-stream-connector-kafka/.../KafkaTwoPhaseCommitSink.java:155`（`ensureTransactionOpen` → `newProducer()`） | 每 sink task 实例 1 个（kafka-clients 4.3.1，**daemon=true**，反编译确认）；`buffer.memory` 有界（默认 32MB），`send` 超限阻塞至 `max.block.ms`（默认 60s）后超时 | 生产者内部 RecordAccumulator（`buffer.memory` 上界） | `max.block.ms` 超时后 `send` 抛 typed 超时（非静默丢弃） | **无**。类未实现 `AutoCloseable`；`StreamSinkOperator.close()`（core:173-177）只对 `instanceof AutoCloseable` 的 userFunction 调 close → 永远到不了 producer。`finish()/rollback()/abort()/recover()` 均不 close | **泄漏（C4-001，P1）** |
| 4 | KafkaConsumer | `nop-stream-connector-kafka/.../KafkaSourceFunction.java:246`（`run()`）与 `:377`（`checkConnection()`） | 消费在 task 线程执行（不建线程）；`poll(200ms)` 有界轮询，`cancel()` 后 ≤200ms + 批内退出 | fetch 缓冲（consumer 内部有界） | N/A | try-with-resources，cancel/正常/drain 全退出路径关闭；`enable.auto.commit=false` typed 守卫 | OK |
| 5 | JDBC 连接（连接池借用） | `nop-stream-connector-jdbc/.../JdbcTwoPhaseCommitSink.java:673`（commit）、`:819`（ledger init） | 每 epoch commit 1 个独立连接（借自 nop-dao 池，池有界）；并行 subtask 各自借还 | N/A | 池满行为由池配置承载（边界外） | `finally`: 失败 `rollback` + 恢复 autoCommit + `close()`；异常路径全覆盖（SQLException/Runtime 分开包装，回滚失败/关闭失败降级日志不吞主异常） | OK |
| 6 | JDK HttpClient（selector 线程 + 默认 executor） | `nop-stream-format-avro/.../AvroSchemaRegistryClient.java:48` | 每 `AvroWireFormat`/client 实例 1 个；`connectTimeout(10s)` 仅覆盖建连 | HttpClient 内部连接池（有 keep-alive） | N/A | **无**。类无 close 方法；JDK 21 起 HttpClient 是 `AutoCloseable` 但无人关闭；持有者 AvroWireFormat/工厂产物（`avroSerializer/avroDeserializer` Function）随 job 销毁时 selector 线程与连接池残留 | **泄漏（C4-002，P2）** |
| 7 | Debezium 嵌入引擎线程 | （边界外 nop-message-debezium；in-scope 使用点 `DebeziumCdcSourceFunction.run():258-281`） | 引擎自管；in-scope 侧 1 runner 线程 await latch（1s 轮询） | 引擎侧自管 | in-scope 侧：collect 失败经 R5-CON-02 捕获置 `pendingError` → run() 重抛（typed，非吞） | `finally`: `unregisterFailureListener`（null 安全，已核对 wrapper）+ `subscription.cancel()` + `source.stop()`，异常降级 WARN 不掩盖主结果 | OK（使用面） |
| 8 | CEP 缓存统计定时器 | `nop-stream-cep/.../CepOperator.java:626-647`（`registerCacheStatisticsTimer`） | 一次性注册 + 回调内锚定 fire time 重臂（等效 fixed-delay，无重叠——回调在 task 线程 mailbox 串行执行，core 契约）；interval ≤0 显式 WARN 关闭 | N/A | N/A | `close():683-691` → `releaseCacheStatisticsTimer()` 先于 partialMatches 释放；cancel 与 re-arm 同在 task 线程，无 post-close 重臂竞态；`logCacheStatistics()` 仅 cache stats + INFO 日志，无可抛路径（不会断链亦不会杀调度） | OK |
| 9 | RocksDB 后台线程（flush/compaction） | `nop-stream-rocksdb/.../RocksDBKeyedStateBackend.java:218` | `setMaxBackgroundJobs(optionConfig.getMaxBackgroundThreads())`——后台线程数显式配置有界 | 原生 memtable/WAL（`writeBufferManagerCapacity` 可配上限） | N/A | `close():979-1055` 完整链：cf handles → db → options → dbOptions（先 handles 后 db 的正确顺序，注释钉定）；metricsRecorder.close() | OK |
| 10 | manifest 文件锁（JVM 内 ReentrantLock + 跨 JVM FileLock） | `nop-stream-connector/.../FileTwoPhaseCommitSink.java:390-395` | static `ConcurrentHashMap<String,ReentrantLock>`（按输出目录键控）+ FileLock | N/A | N/A（锁等待无超时，属 C2/F5 审查面，此处只记录不动判） | `try (FileChannel/FileLock)` TWR；锁 map 条目常驻（每目录 1 个 ReentrantLock，量级=目录数，有界） | OK（C2 维度复核项） |

**任务隔离核对（C4 步骤 4）**：8 个模块无跨业务共享池；每个连接器实例自建 client（kafka producer/consumer 每实例独立）、每 reader 独立 runner 线程，互不饿死。无 caller-runs 策略使用；无池内任务再提交同池任务形态。`GlobalExecutors`/平台 `IThreadPoolExecutor` 在 8 模块内零使用（grep 确认）——不存在"平台设施未用手写轮子"的 C4 发现。

**定时任务核对（C4 步骤 5）**：唯一周期形态是 CEP 缓存统计定时器（#8）——重臂发生在回调末尾，任务比周期慢时不堆积（天然 fixed-delay）；无 `scheduleAtFixedRate` 的"异常后静默停摆"面（JDK 语义不适用）。

---

## 发现条目

### [nop-stream-state-connectors-C4-001] KafkaTwoPhaseCommitSink 的 KafkaProducer 全路径无关闭，job 停止/重启即泄漏 daemon sender 线程与 broker 连接

- **文件**: `nop-stream/nop-stream-connector-kafka/src/main/java/io/nop/stream/connector/kafka/KafkaTwoPhaseCommitSink.java:77,89,134-165`
- **证据片段**:
  ```java
  // :77 —— 类声明：只 implements ConnectivityCheckable，无 AutoCloseable
  public class KafkaTwoPhaseCommitSink<T> extends TwoPhaseCommitSinkFunction<T> implements ConnectivityCheckable {
      // :89
      private transient volatile Producer<String, byte[]> producer;
      // :150-165 ensureTransactionOpen() —— producer 惰性创建，全文件唯一创建点
      private void ensureTransactionOpen() throws Exception {
          if (transactionOpen) { return; }
          if (producer == null) {
              producer = newProducer();          // new KafkaProducer(props)：启动 daemon sender 线程 + NIO selector
          }
          ...
      }
      // 全文件 grep ".close()" 零命中（仅 checkConnection 的 probe producer 有 TWR close :307）
  ```
- **严重程度**: P1
- **并发场景**: 每 kafka-2pc sink task 的生命周期线程归宿问题。`KafkaProducer` 由 task 线程在首条 `invoke()` 时创建（内部启动 1 条 daemon `kafka-producer-network-thread` + NIO selector + broker 连接）；job 的 stop/cancel/restart/正常完成是常态运维路径（owner doc：kafka-2pc 为 EXPERIMENTAL 但为内建 10 端点之一，gated E2E `TestKafkaConnectorExactlyOnceE2E` 即 kill-recover 场景）。
- **触发时序**: T1（task 线程）首条记录进入 `invoke():168` → `ensureTransactionOpen():155` 创建 producer，daemon sender 线程启动。T2（runtime 停止路径）job stop/cancel → `StreamSinkOperator.close():173-177` 执行 `if (userFunction instanceof AutoCloseable) ((AutoCloseable) userFunction).close()` —— 本 sink 未实现 AutoCloseable，close 通道空转；`TwoPhaseCommitSinkFunction` 基类（core，`implements SinkFunction, CheckpointParticipant`）与 `StreamFunction` 接口均无 close/destroy。后果：producer 对象随 sink 实例本应可回收，但 daemon sender 线程在 `sender.run()` 死循环中持有对它的强引用且无任何线程调用 `producer.close()` → 线程永不退出、NIO selector 与 `transactional.id` 的 producer 状态常驻。每次 job 提交-停止循环泄漏 1 组；长驻引擎宿主（owner doc：流引擎可独立部署为长驻运行时）周/月级运行 + 作业滚动提交 → 线程数与句柄单调增长。
- **现状**: 正常成功路径即产生资源滞留：producer 在主路径创建、在包括正常完成在内的所有路径上都不释放（泄漏判定为 P1 而非 P0 的校准理由：泄漏粒度 = 每 sink task 生命周期 1 个实例而非每请求；sender 线程 daemon=true（4.3.1 jar 反编译 `SenderThread.<init>(..., iconst_1)` 确认）不阻塞 JVM 退出；单实例残留 footprint = 1 线程 + selector + 若干空闲连接，broker 侧 idle 连接 9 分钟后由 NetworkClient 回收，但线程与对象本身永不回收）。
- **风险**: 长时运行必然触发的无界增长（P1 判据）：每轮 job 滚动 +1 daemon 线程（约 1MB 栈）+ 文件句柄 + GC 压力；数百次滚动后线程数可观。泄漏的 producer 仍持有该 `transactional.id` 的元数据；虽然新 producer 的 `initTransactions()` 会 fence 旧代（语义不受损），但旧 sender 线程的 EOF/异常只写自身日志，运维面只见线程数上涨不可见归因。
- **建议**: 让 `KafkaTwoPhaseCommitSink` 实现 `AutoCloseable`，`close()` 中幂等执行 `rollback()`（中止悬挂事务）+ `producer.close(Duration.ofSeconds(5))` + 置空——`StreamSinkOperator.close():176` 的 AutoCloseable 通道已存在，无需改 core。可顺带在 `TwoPhaseCommitSinkFunction` 基类声明 `default void close()` 空实现并在 Javadoc 钉定"持有线程/连接资源的 sink 必须实现 AutoCloseable"契约（与 `BatchConsumerSinkFunction` 已实现的先例一致）。
- **失效面枚举**（F3：函数/资源生命周期对称性全族清点，逐一核对）:
  - `KafkaTwoPhaseCommitSink.producer` — **无 close（本发现）**
  - `KafkaSourceFunction` 的 `KafkaConsumer`（`run():246` / `checkConnection():377`）— TWR 全路径关闭，OK
  - `KafkaTwoPhaseCommitSink.checkConnection():307` probe producer — TWR 关闭，OK（恰说明作者知道要 close，主路径漏了）
  - `MessageSourceFunction.subscription`（run() finally `:196-205`）— 全退出路径 unsubscribe，OK
  - `MessageSourceReader.runnerThread`（close():195-212）— cancel+join+clear，OK
  - `DebeziumCdcSourceReader.runnerThread`（close():295-312）— 同上，OK
  - `DebeziumCdcSourceFunction.subscription/source`（run() finally :297-318）— 成对 stop/cancel，OK
  - `JdbcTwoPhaseCommitSink` 连接（:717-735 / :829-835）— finally close，OK；无实例级持有资源，无需 close，OK
  - `BatchConsumerSinkFunction`（implements AutoCloseable，close():141-171 flush+consumer close）— OK，且是本族唯一实现 close 通道的 sink
  - `BatchLoaderSourceFunction` loader（:145-146 `instanceof AutoCloseable` 则 close）— OK
  - `FileTwoPhaseCommitSink` 文件通道（:393/:604/:616/:673）— 全 TWR，OK
  - `AvroSchemaRegistryClient.http` — 无 close（见 C4-002，同族第二处）
- **信心水平**: 确定（"无关闭路径"为全文件级 grep + 接口链通读结论；泄漏后果链每步均有代码锚点）
- **误报排除**: 不属于"平台基础设施自身不重审"——缺陷主体在 connector 模块内（该实现 AutoCloseable 而未实现）；不属于"有界声明存在"豁免——无任何配置可关停该线程；不属于测试专用简化——kafka-2pc 是 owner doc 内建端点。
- **复核状态**: 已保留（P1）——独立复核确认，见文末「维度复核结论」R-C4-001；二轮复核：已保留（P1），见文末「维度复核结论（二轮）」R2-C4-001

### [nop-stream-state-connectors-C4-002] AvroSchemaRegistryClient 的 JDK HttpClient 无关闭路径，每份 Avro wire-format 装配在 job 结束后残留 selector 线程与连接池

- **文件**: `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java:34-49`
- **证据片段**:
  ```java
  public class AvroSchemaRegistryClient {          // :32 —— 无 close 方法、未实现 AutoCloseable
      private final HttpClient http;
      ...
      public AvroSchemaRegistryClient(String baseUrl) {
          ...
          this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();  // :48
      }                                            // http 从不关闭；类内无任何生命周期终点
  ```
- **严重程度**: P2
- **并发场景**: 每 `AvroWireFormat` 实例（经 `avroSerializer/avroDeserializer` 工厂注入 Kafka source/sink 的 value 序列化函数，owner doc WI18）持有一个 JDK HttpClient——每个 client 实例启动 1 条 daemon selector 线程 + 默认 executor（短命连接线程，空闲自灭）+ keep-alive 连接池。client 生命周期 = 注入它的 job 装配生命周期。
- **触发时序**: T1（部署/装配线程）构造 `AvroWireFormat` → `AvroSchemaRegistryClient` → `HttpClient`（selector 线程启动）。T2（job 停止）KafkaSourceFunction/KafkaTwoPhaseCommitSink 生命周期终结，`avroSerializer/avroDeserializer` 返回的 Function 与其捕获的 wire-format/client 一起失去调用方，但无任何代码路径关闭 HttpClient（JDK 21 起 HttpClient 实现 AutoCloseable，本工程 Java 21 可直接 close；类未暴露）。后果：daemon selector 线程与注册的 keep-alive 连接常驻至进程退出；每轮携带 Avro 格式的 job 滚动 +1 组。
- **现状**: 与 C4-001 同族（持有线程资源的对象缺关闭路径），单实例 footprint 更小（1 daemon selector 线程；默认 executor 的空闲线程会超时自灭），故降一档 P2；但无界累积形态相同。
- **风险**: 长驻宿主 + 作业滚动下 selector 线程数单调增长（每线程约 1MB 栈 + selector 留存的连接表）；测试/采样进程（`StreamDataSampler` 不经 job 生命周期）中反复构造 wire-format 时同样累积。功能语义无损（线程空转），纯资源治理问题。
- **建议**: `AvroSchemaRegistryClient` 实现 `AutoCloseable`（JDK 21 `HttpClient` 直接 `close()`/`closeAsync`），`AvroWireFormat` 透传 close 或在连接器宿主 close 链上接线；工厂方法 Javadoc 声明生命周期归宿。
- **失效面枚举**（F3）: format-avro 全部 3 文件核对——`AvroWireFormat`（无自有线程资源，encode/decode 每次新建 writer/encoder/`ByteArrayOutputStream`，无池化残留）OK；`StreamAvroException`（纯异常类）OK；`AvroSchemaRegistryClient.http`（本发现）。同族兄弟 = C4-001 清单，此处不重复。
- **信心水平**: 确定（无 close 为全类通读结论；HttpClient 线程模型为 JDK 公开行为）
- **误报排除**: 不属于"有界缓存/池豁免"——这里没有任何 close 机制存在，不是"配了上限"的形态；不属于低频路径豁免——selector 线程是长驻残留而非短暂开销。
- **复核状态**: 已保留（P2）——独立复核确认，见文末「维度复核结论」R-C4-002；二轮复核：已保留（P2），见文末「维度复核结论（二轮）」R2-C4-002

### [nop-stream-state-connectors-C4-003] Avro registry HTTP 调用只有 connectTimeout 无请求级超时：registry 建连后挂起时首条编解码线程无限期阻塞，且首次调用在 ConcurrentHashMap 锁内做 HTTP

- **文件**: `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java:113-123`（无请求超时）、`nop-stream-format-avro/.../AvroWireFormat.java:55-60`（computeIfAbsent 内 HTTP）
- **证据片段**:
  ```java
  // AvroSchemaRegistryClient.java
  this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();  // :48 仅建连超时
  private Map<?, ?> readJsonResponse(HttpRequest request) {                            // :113
      HttpResponse<String> resp;
      try {
          resp = http.send(request, HttpResponse.BodyHandlers.ofString());             // :116 无 request.timeout()
  // AvroWireFormat.java
  public byte[] encode(GenericRecord record, String subject) {
      int schemaId = writerSchemaIds.computeIfAbsent(subject, s -> {                   // :55
          int id = client.registerSchema(s, record.getSchema());                       // :56 首条记录 = 阻塞 HTTP
  ```
- **严重程度**: P2
- **并发场景**: Kafka 连接器的 sink `invoke`（task 线程）与 source `pollNext` 反序列化（task 线程）在每记录热路径上调用 encode/decode；每 subject 首条记录走冷路径 HTTP（注册 schema / 按 id 取 schema）。多个 task 线程可能并发对同一 client 做首次 encode。
- **触发时序**: 前置：registry 端 TCP 可建连但应用层挂起（LB 半开、服务 GC 停顿、网络黑洞）。T1（某 sink task 线程）首条记录进入 `encode():55` → `computeIfAbsent` 拿到该 subject 桶锁 → `registerSchema` → `http.send():116`。`connectTimeout(10s)` 只覆盖建连；`HttpRequest` 未设 `.timeout()`，`send` 无请求级超时 → task 线程在 socket read 上无限期阻塞。T2（另一 task 线程）对 hash 到同一桶的**不同** subject 做首次 encode → 阻塞在 `computeIfAbsent` 桶锁上排队。交叠后果：受影响 stream task 无限期停滞（既不产出也不报错），无 typed 失败；可见性只能靠引擎级 checkpoint 超时间接暴露（F5 挂死形态）。桶锁排队使停滞面从单 subject 扩大到同桶 subject 集合（热身完成后 `computeIfAbsent` 退化为无锁读，不放大）。
- **现状**: 等待点缺超时（F5 定向检查项）：连接被拒/HTTP 非 200 均有 typed `StreamAvroException`（owner doc"失败面全 typed"），唯独"建连成功后对端不应答"这一失败形态无界。owner doc 已声明的边界只有"同 subject 换版 encoder 不感知"，未覆盖挂起形态。
- **风险**: registry 半挂起时 CDC/流管线静默停滞且无错误可归因；恢复依赖对端最终应答或连接被 OS 断开，无模块级自愈。与 kafka-2pc 组合时 sink task 停滞叠加 checkpoint 超时触发无谓的恢复循环。
- **建议**: `readJsonResponse` 统一 `request.timeout(Duration.ofSeconds(N))`（请求级），超时转 typed `StreamAvroException`；`AvroWireFormat.encode` 的注册移出 `computeIfAbsent`（先 get、miss 后同步注册再 put——重复注册幂等，服务端语义已声明），消除锁内 I/O 形态。两改均不影响公开签名。
- **失效面枚举**（F3）: 本 client 全部阻塞点清点——`registerSchema:59`（经 readJsonResponse，本发现）、`fetchSchemaById:89`（同）、`getLatestSchema:78`（同，且经 `readerSchemaFor:111-117` 被 decode 每条 miss 路径调用）——三处同一修复面；`AvroWireFormat.encode:55` 与 `decode:92` 是全部消费入口（decode 的 `client.getSchemaById` 同样走无超时 send）。
- **信心水平**: 很可能（挂起形态在真实网络上必然可复现，但需要异常 registry 行为；未做实网复现）
- **误报排除**: 不属于"原语使用不是问题"豁免——缺陷是等待点无界 + 锁内 I/O 的复合形态；不属于误报校准的"有界声明"——此处无任何超时上界声明。
- **复核状态**: 已保留（P2）——独立复核确认，见文末「维度复核结论」R-C4-003；二轮复核：已保留（P2），见文末「维度复核结论（二轮）」R2-C4-003

---

## 零发现面声明（定向核对后不立项的点）

- **CEP close/re-arm 竞态假设**：`CepOperator.onCacheStatisticsTimer` 在回调末尾重臂，理论上存在"cancel(false) 后回调仍执行并重臂 → 僵尸定时器"的形态；核对 core `TaskProcessingTimeService` 契约——定时回调经 mailbox 在 task 线程执行，与 `close()` 串行，cancel 与 re-arm 不可能交错。**不立项**（依赖 core 已文档化的串行契约）。
- **reader 线程 close 时序**（MessageSourceReader/DebeziumCdcSourceReader）：`join(5s)` 先于 `queue.clear()` 的顺序曾在初检中怀疑会让 put 阻塞的投递线程泄漏——实际 runner 线程只 await latch 不做 collect，join 必然成功，`clear()`（signal notFull）随后释放所有 put 阻塞者。**不立项**。
- **KafkaSourceFunction `restoredOffsets` TreeMap** 的跨线程读写原子性属 C1 维度（且 runtime 单线程 mailbox 模型下 check-then-act 成立，owner doc AR-10 以 volatile 处理可见性），不在 C4 立项。
- **FileTwoPhaseCommitSink `MANIFEST_JVM_LOCKS`** 的锁语义/迭代点属 C2 维度（baseline C2 节已圈定），C4 只确认其线程承载为调用线程、无新增线程。

## 统计

- **P0**: 0
- **P1**: 1（C4-001）
- **P2**: 2（C4-002、C4-003）
- **P3**: 0
- **不立项（定向核对后排除）**: 4（见上节）

# C4 线程池与执行器治理 — nop-stream-state-connectors 深挖第 2 轮追加

## 深挖第 2 轮追加

- **审计维度**: C4（线程池与执行器治理），第 1 轮报告 `C4-thread-pools.md`（清单 10 行 + 4 项排除 + C4-001/002/003）之后的定向深挖
- **深挖盲区（本轮指派）**: ① debezium 嵌入引擎线程的治理（连接器自建线程与引擎线程的宿主关系）；② 定时器任务异常遏制的其他点
- **审计日期**: 2026-10-14
- **盲审边界**: 仅读取本维度第 1 轮文件与本轮新核对的 live code；未读 ai-dev/audits/ 其他目录、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/
- **范围声明（对第 1 轮边界的一处显式扩展）**: 第 1 轮将 `DebeziumEngineWrapper`/`DebeziumMessageSource`（位于 `nop-message/nop-message-debezium`）自身实现列为边界外、只审使用面；本轮按指派对「嵌入引擎线程的治理」做深挖，发现 C4-004/005/006 的缺陷定位横跨 wrapper（nop-message-debezium）与 in-scope 使用链（`DebeziumCdcSourceReader`/`DebeziumCdcSourceFunction`），每条均给出 in-scope 锚点。第 1 轮已核对并保留的 R5-CON-02（startRequested gate）/R5-CON-03（failure listener）修复不在本轮重复。
- **核对口径**: 全部行号与池参数在本审计 worktree（branch `concurrency-audit-remediation`）live code 上以 grep/sed 复核；`nop-global-worker` 池参数（core=4 / max=30 / 队列 10000）核对 `ThreadPoolConfig.java:17,19,25` + `CommonConfigs.java:27`；平台共用面按 `GlobalExecutors.globalWorker()` 全仓 grep（22 处非测试使用点）计。

---

### [nop-stream-state-connectors-C4-004] Debezium 嵌入引擎以无限阻塞任务常驻平台共享 nop-global-worker 池：每 CDC subtask 永久钉死 1 条共享线程，任务隔离失效且影响半径为全平台异步面

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:159-173`（提交点）；in-scope 使用链锚点：`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceReader.java:132-138`（reader 线程 → 嵌入函数）→ `DebeziumCdcSourceFunction.java:258-281`（`source.subscribe` → 引擎启动）
- **证据片段**:
  ```java
  // DebeziumEngineWrapper.start() :159-173 —— 引擎主循环（无限阻塞直到 close()）提交到平台全局共享池
  GlobalExecutors.globalWorker().execute(() -> {      // :159
      try {
          running.set(true);
          LOG.info("Starting Debezium engine: {}", config.getName());
          engine.run();                                // :163 阻塞至引擎终结（天/月级）
      } catch (Exception e) { ...; notifyEngineFailure(config.getName(), e); }
      finally { running.set(false); ... }
  });
  // 池参数：GlobalExecutors.globalWorker() :85-93 → ThreadPoolConfig corePoolSize=4(:17)
  //   maxPoolSize 被 CommonConfigs.java:27 覆盖为默认 30、queueCapacity=10000(:25)、daemon
  // 平台共用面：globalWorker() 非测试使用点 22 处（nop-web 页面渲染/导出、nop-datav 导出与交付、
  //   nop-batch 导入导出、nop-ai、nop-retry 引擎与扫描器、nop-cluster 选主/服务注册定时器
  //   executeOn(globalWorker)、nop-sys 消息、nop-job 批扫描器、nop-ioc 容器启动图等）
  ```
- **严重程度**: P1
- **并发场景**: 每个 CDC subtask 的嵌入引擎主循环（`DebeziumSourceSupport.buildInstanceConfig` 每 subtask 一引擎实例；B-layer 表分片显式支持 parallelism>1，`DebeziumCdcSourceFunction.validateParallelism:487-516`）与全平台 22 处异步功能共享同一个 core=4/max=30/queue=10000 的 `nop-global-worker` 池。引擎任务是「永不结束的阻塞任务」——从 start 到 stop 占满整条线程的整个生命周期（天/月级），不是可排队结束的短任务。
- **触发时序**: T1..Tn（部署/装配线程）：n 个 CDC subtask 陆续 `subscribe → startEngineIfNeeded → wrapper.start`，每个 `engine.run()` 永久占 1 条 worker 线程（job 运行期不释放）。Tn+1（平台其他功能线程）：datav 导出提交、retry 引擎派发、leader 选举 tick、页面异步渲染等任意 `globalWorker` 任务在 30 线程全被引擎钉死后只能入 10000 队列积压；队列满后 `execute` 走拒绝路径。交叠后果：引擎未超限但平台繁忙时，引擎启动 lambda 也可被长时间排队（这正是 C4-006 的触发前置）；超限后平台级异步面整体饿星。单实例核查：每 job 每 subtask 恰 1 条，随 job 停止释放（stop 生效前提下，见 C4-005）——泄漏形态是「驻留挤压」而非「累积泄漏」，故 P1 而非 P0。
- **现状**: 引擎没有专用线程，直接复用平台全局共享池；线程名统一为 `nop-global-worker-N`，线程 dump 中引擎线程与其他平台任务不可区分（可观测性次生缺陷）；reader 桥接队列（256）反压时引擎线程阻塞在 `queue.put`，同样消耗共享池线程。
- **风险**: 长驻运行时 + 多 CDC 管线 consolidated 部署（owner doc 未限制引擎数量）下，约 30 个引擎实例即挤占全部 worker：平台异步功能（导出/交付/重试/选举）集体降级；且该形态与 C4-005/006 复合——池饱和越严重，引擎启动延迟与 stop 后僵尸窗口越大。C4 步骤 4 的任务隔离检查项（「一个慢业务饿死其他业务」）在此为「一个常驻业务钉死其他业务」。
- **建议**: `DebeziumEngineWrapper` 为每个引擎持有专用单线程执行器（`Executors.newSingleThreadExecutor(new NamedThreadFactory("debezium-engine-" + config.getName(), true))`，平台设施 `io.nop.commons.concurrent.thread.NamedThreadFactory`），`stop()` 中 `shutdown()` + `awaitTermination(有界超时)`（与 C4-005 的终结等待合并实现）；平台先例：nop-stream-runtime 各组件的 `NopStreamThreadFactory.named("tm-task-<id>")` 单线程执行器形态。若维持共享池，则至少要求引擎任务声明为长任务并隔离到独立池。
- **失效面枚举**（F3：全模块线程/池创建点同族清点，逐一核对）:
  - `DebeziumEngineWrapper.start:159` globalWorker 提交 — **本发现**（8 模块关联面内唯一一处共享池常驻提交，grep 全量确认）
  - `MessageSourceReader.java:87` `message-source-reader-<subtask>` — 专用具名 daemon 线程、有界队列、close cancel+join(5s)+typed 超时+clear，OK
  - `DebeziumCdcSourceReader.java:132` `debezium-source-reader-<subtask>` — 同上，OK（本轮全文复核维持第 1 轮判定）
  - `CepOperator` 缓存统计定时器 — 经 task 线程 mailbox 串行执行，不占额外线程，OK
  - `RocksDBKeyedStateBackend:218` `setMaxBackgroundJobs` — 显式配置有界，OK
  - `KafkaTwoPhaseCommitSink` sender 线程 — 第 1 轮 C4-001（P1，已知未修复），非本轮范围
  - `AvroSchemaRegistryClient` HttpClient — 第 1 轮 C4-002/003，非本轮范围
  - `ReplayableCdcSourceFunction`（nop-stream-fraud-example 示例模块）— 范围外，同类共享池用法存在，登记供该模块审计引用
- **信心水平**: 确定（池参数、提交点、22 处共用面均为 live code grep/read 直接证据；饱和算术为确定性推导）
- **误报排除**: 不属于「有界声明存在的池」豁免——池确有界（30 线程/10000 队列），缺陷是使用方式违反任务隔离，恰为 C4 步骤 4 的定向检查形态；不属于「平台基础设施自身不重审」——`GlobalExecutors` 本身不审，审的是 connector 链路对它的使用决策；owner doc（`docs-for-ai/03-modules/nop-stream-connectors.md`）未声明「引擎线程模型 = 共享池」为已接受边界。
- **复核状态**: 已保留（P1）——独立复核确认，见文末「维度复核结论」R-C4-004；二轮复核：已保留（P1），见文末「维度复核结论（二轮）」R2-C4-004

### [nop-stream-state-connectors-C4-005] 引擎 stop 为 fire-and-forget：close() 发出异步信号后无任何终结等待，job 快速重启时新旧引擎同名交叠——新引擎按模块自证结论卡死在 MBean 注册重试循环，任务 RUNNING 假活且挂死对失败监听器不可见

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:195-215`（stop 无等待）；`nop-message/nop-message-debezium/.../DebeziumMessageSource.java:168-180`（stop 置空 wrapper，同样无等待）；in-scope 锚点：`nop-stream/nop-stream-connector-debezium/.../DebeziumCdcSourceReader.java:295-312`（close 的 join 只覆盖 runner 线程）、`DebeziumCdcSourceFunction.java:656-658`（模块自证的同 serverName MBean 冲突注释）、`DebeziumSourceSupport.java:77-79`（实例名确定性 → 重启必然同名）
- **证据片段**:
  ```java
  // DebeziumEngineWrapper.stop() :195-215 —— close() 是协作式异步信号，返回不等于引擎已停
  public synchronized void stop() {
      if (!startRequested.get() || stopped.getAndSet(true)) { return; }   // :200
      LOG.info("Stopping Debezium engine: {}", config.getName());
      try {
          if (engine != null) { engine.close(); }                         // :208 异步请求停止
      } catch (Exception e) {
          LOG.error("Error closing Debezium engine: {}", ...);            // :210-212 吞掉，无 fallback
      }
      running.set(false);                                                 // :214 无任何 join/await
  }
  // DebeziumCdcSourceFunction.java:656-658（模块对同 JVM 同名双实例的已文档化行为）
  // serverName 同步实例化：它决定 Debezium 指标 MBean 的 ObjectName 与 topic 前缀，
  // 同 JVM 双实例共享 serverName 会让后注册者卡死在 JmxUtils.registerMXBean 重试循环
  // DebeziumSourceSupport.instanceName :77-79 —— baseName+subtaskIndex 确定性命名：重启同名
  ```
- **严重程度**: P1
- **并发场景**: job 重启（checkpoint 恢复 / region restart / rescale——owner doc 钉定的常态运维路径，`TestDebeziumCdcCheckpoint` 的 kill-recover 场景即此）期间的引擎生命周期交叠：旧引擎线程（`nop-global-worker` 上的 `engine.run()`）与新引擎线程（重启后另一条 worker）在同一 JVM 同时存在，共享确定性同名（instanceName = `baseName[-subtaskIndex]`）派生的同一 serverName、同一 `NopStreamOffsetBackingStore.REGISTRY` offset 桶。reader `close():300-308` 的 `join(5000)` 只等待 runner 线程——runner 在 fire-and-forget 的 `source.stop()` 返回后即退出（其 wait loop 仅 1s latch 轮询），所以 join 通过对「引擎是否已终结」零保证。
- **触发时序**: T1（task 线程）：cancel/stop → `DebeziumCdcSourceReader.close()` → `embeddedFunction.cancel()` → `source.stop()` → `wrapper.stop()` → `engine.close()`（异步信号，立即返回）→ runner 退出 wait loop → finally 完成清理 → `join(5s)` 通过 → reader 生命周期终结。此刻旧引擎大概率仍在收尾（connector stop hook、当前 poll/事务边界）。T2（重启驱动）：region restart 立即重建 reader → `launchEngine` → 新 `DebeziumCdcSourceFunction` → 新 `DebeziumMessageSource.subscribe` → 新 `wrapper.start` → 新 `engine.run()` 于另一条 worker → connector 启动阶段注册与旧引擎相同的 serverName MBean ObjectName → 按模块自身注释（:656-658）：后注册者卡死在 `JmxUtils.registerMXBean` 重试循环。交叠后果：① 新引擎永不产出且永不死亡——R5-CON-03 的 failure listener 依赖「引擎终态死亡」触发，卡死不触发 → 任务 RUNNING 假活、CDC 断流无错误可归因（F5：挂死生产者对健康检测不可见）；② 该条 worker 线程被无进展的自旋永久占用（与 C4-004 复合）；③ 交叠窗口内新旧两条 binlog 连接并存、两引擎并发写同一 offset REGISTRY 桶（checkpoint 快照可能采到旧引擎位点）。
- **现状**: stop 语义为「请求即返回」：无终结等待、无超时、`engine.close()` 抛异常被 catch 后仅记日志（此时引擎可能永不退出而 stop 已视为完成）；reader 侧 join 契约与引擎终结完全脱节。
- **风险**: 每次快速重启（恢复/重部署测试、故障切换）都有概率楔死新引擎；楔死形态恰是该模块 R5-CON-03 立项要消灭的「RUNNING 假活断流」，但以更隐蔽的方式复发（无失败通知）；每楔死一次永久少一条共享池线程；offset 注册表双写可污染后续 checkpoint。
- **建议**: `DebeziumEngineWrapper.stop()` 增加有界终结等待：利用已存在的 `EngineCompletionCallback`（:227-241）武装 completion latch，stop 内 `close()` 后 `latch.await(timeout)`，超时 typed 上抛（此时 reader close 的 typed 超时通道已存在，可层层上浮）；close() 异常不再静默吞（升级为 typed 或 fallback 中断 worker Future）。in-scope 加固：`DebeziumCdcSourceReader.close()` 的 join 契约注释明确「引擎终结由 wrapper.stop 的等待保证」，或在 `launchEngine` 前对同名旧引擎存活做防御检测（`!wrapper.isRunning()` 不可靠，见 006，建议以终结 latch 状态为准）。
- **失效面枚举**（F3：引擎 stop/清理全路径清点，全部汇聚到同一 fire-and-forget stop，逐一核对）:
  - `DebeziumMessageSource.stop:168-180` — 同族（wrapper.stop 后置空，无等待），同一修复面
  - `DebeziumMessageSource.Subscription.cancel:219-229` — 订阅清空触发 `stop()`，同族，同修复面
  - `DebeziumCdcSourceFunction.run() finally:297-318` — `unregisterFailureListener + subscription.cancel + source.stop`，同族，同修复面
  - `DebeziumCdcSourceFunction.cancel:330-344` / `truncateForDrain:352-367` — 同上两条路径，同族
  - `DebeziumCdcSourceReader.close:295-312` — 本发现证据点（join 不覆盖引擎）
  - `MessageSourceReader.close:195-212` — 无引擎宿主，仅 runner join，不受影响，OK
  - `DebeziumEngineWrapper.EngineCompletionCallback:227-241` — 已有引擎终结回调钩子，修复可直接挂接，OK（作为挂接点登记）
  - `ReplayableCdcSourceFunction`（fraud-example）— 范围外同类用法，登记供该模块审计引用
- **信心水平**: 很可能（fire-and-forget 与 join 脱节为代码直读的确定事实；交叠楔死需要旧引擎退出慢于重启间隔这一运行时条件，MBean 重试行为引自模块自身注释、属第三方内部语义，未实机复现）
- **误报排除**: 与第 1 轮已修项不重复——R5 CON-02 修的是「启动窗口内 stop 早退导致引擎泄漏」（stop 改为 gate 在 startRequested 上）；本发现是「stop 返回后引擎终结无保证」的正交面，:200 的 gate 对本问题无作用。不属于「有界声明」豁免：无任何等待/超时机制存在，也无线程模型边界声明。时序具体可化（同名确定性 + 重启路径 + 模块自证的冲突行为），满足 P1 证据标准。
- **复核状态**: 已保留（P1）——独立复核确认，见文末「维度复核结论」R-C4-005；二轮复核：已保留（P1），见文末「维度复核结论（二轮）」R2-C4-005

### [nop-stream-state-connectors-C4-006] 引擎启动 lambda 缺 stopped 守卫：饱和池排队窗口内 stop() 先完成，出队的 lambda 仍无条件 engine.run()，产生无主引擎（最坏形态为永久僵尸）

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:159-173`（lambda 无守卫）对照 `:143`（startRequested 同步置位）与 `:200`（stopped gate 仅存在于 stop()）
- **证据片段**:
  ```java
  // start() :143 —— 请求同步落账，引擎任务异步入队（池满时在 10000 队列中排队）
  startRequested.set(true);
  ...
  GlobalExecutors.globalWorker().execute(() -> {      // :159 出队时机不受控
      try {
          running.set(true);                          // :161 无 stopped 检查
          engine.run();                               // :163 无条件执行
      } ...
  });
  // stop() :200 —— stopped 置位只 gate stop 自身，从不被 lambda 侧检查
  if (!startRequested.get() || stopped.getAndSet(true)) { return; }
  ```
- **严重程度**: P2
- **并发场景**: `start()` 的引擎任务在 `nop-global-worker` 饱和/积压时（恰为 C4-004 的运行形态）排队延迟执行；期间该 connector 被 cancel/stop。R5 CON-02 修复覆盖了「stop 早于 running=true 时早退泄漏」方向，反向窗口——「stop 完整执行后 lambda 才出队」——无任何守卫。
- **触发时序**: T0（task 线程）：`subscribe → startEngineIfNeeded → wrapper.start()`：`startRequested=true`，lambda 入队（30 worker 全忙）。T1（task 线程）：cancel → `Subscription.cancel`（订阅清空）→ `source.stop()` → `wrapper.stop()`：gate 通过（startRequested=true）→ 对从未 run 的引擎 `engine.close()` → `stopped=true` → 返回；reader close 完成、wrapper 字段置 null、订阅 map 清空。T2（某条空闲 worker）：出队 lambda → `running.set(true)` → `engine.run()` 无条件启动 → 无主引擎：事件派发到空订阅 map（无人消费）、binlog 连接与 MBean（同 serverName）被占用、`stopped=true` 使任何后续 `stop()` 直接 return——平台再无代码路径能停掉它。良性读法：EmbeddedEngine 对 close-before-run 即刻退出（仅短暂占线程）；恶性读法：永久僵尸 binlog 消费者，且其 MBean 使该 connector 下次启动楔死（与 C4-005 复合）。最终形态取决于 debezium EmbeddedEngine 对 close-before-run 的第三方语义。
- **现状**: 同一 wrapper 的两个入口守卫不对称——stop() 检查 startRequested/stopped，启动 lambda 什么都不检查；`running`（lambda 写）与 `stopped`（stop 写）是两个互不引用的 AtomicBoolean。
- **风险**: 稀有但触发链具体（池饱和排队 × 快速取消，两者在 C4-004 形态下同时概率上升）；后果上界为永久资源占用 + 重启楔死，下界为无主引擎短暂启动。属「罕见但可具体化的竞态」，因最终后果部分依赖第三方内部语义降一档为 P2。
- **建议**: lambda 头部加一行守卫：`if (stopped.get()) { LOG.warn("Debezium engine start skipped: already stopped: {}", config.getName()); return; }`；采用 C4-004 的专用执行器方案后，stop 走 `shutdownNow()` 可结构性关闭该窗口（排队任务直接移除）。
- **失效面枚举**（F3）: wrapper 内引擎执行入口全量清点——`start():135-180` 的 lambda（本发现，唯一提交点，无兄弟）；`stop():195-215`（gate 侧，对照面）；`EngineCompletionCallback.handle:228-240`（终结侧，仅置 running=false 并通知，不启动引擎，OK）。模块外无其他 `engine.run()` 调用点（grep 全仓唯一）。同族线程提交守卫先例：MessageSourceReader/DebeziumCdcSourceReader 的 runner 采用同步 `new Thread().start()`（无排队窗口），形态上不受此缺陷族影响，OK。
- **信心水平**: 很可能（守卫缺失为代码直读确定事实；排队窗口的最终后果依赖 debezium 内部语义，故未定级 P1）
- **误报排除**: 不属于「原语使用不是问题」豁免——缺陷是复合生命周期状态机（startRequested/stopped/running 三 flag）缺少交叉检查，非 ConcurrentHashMap 类原语误用；不与 C4-005 重复计数——005 是正常退出路径的终结等待缺失，006 是排队窗口的启动守卫缺失，根因与修复点不同（006 一行守卫即可独立修复）。
- **复核状态**: 已保留（P2）——独立复核确认，见文末「维度复核结论」R-C4-006；二轮复核：已保留（P2），见文末「维度复核结论（二轮）」R2-C4-006

---

## 深挖盲区②核对结论：定时器任务异常遏制的其他点 — 零新发现（定向核对后不立项）

第 1 轮只覆盖了 CEP 缓存统计定时器（清单 #8）。本轮对其余定时器/调度形态逐一核对：

- **CepOperator NFA 处理时间定时器回调包装**（`CepOperator.java:556-571`）：`registerProcessingTimeTimer` 捕获注册 key 后包装回调，`catch (Exception e)` → 包装为 `StreamException(ERR_STREAM_STATE_ERROR)` **重抛**（非吞）→ 经 `TaskProcessingTimeService.fireDueTimers:126-130` 包装为 `ProcessingTimeCallbackException` → 经 mailbox mail action 传播进 task 主循环 → 任务可见失败。fail-fast 语义成立，不立项。
- **TaskProcessingTimeService.fireDueTimers 的「已出队未回调」定时器**（`TaskProcessingTimeService.java:113-132`）：先出队全部到期定时器再逐个回调，回调中途抛异常时剩余已出队定时器随之丢弃——但异常同时使任务可见失败，丢弃面由恢复语义接管；类 javadoc :37-43 已将 fail-fast 钉定为 recorded decision。属「已声明的边界」，不立项。
- **ProcessingTimeServiceDriver 调度线程**（`ProcessingTimeServiceDriver.java:116-158`）：调度线程只做 volatile 读 + 可中断 sleep，从不执行回调（mailbox 串行契约）；异常经 mail 传播，线程 daemon、单发、shutdown 可中断。无「异常杀死调度线程」面，不立项。
- **CepOperator.onCacheStatisticsTimer 重臂顺序**（`CepOperator.java:658-667`）：若 `logCacheStatistics()` 抛异常则重臂被跳过（定时链断），但异常同样经 fireDueTimers 传播为任务可见失败（非静默停摆），且第 1 轮已核实该回调体仅 cache stats + INFO 日志、无可抛路径。不立项。
- **8 模块 src/main 的 JDK 调度器**：`scheduleAtFixedRate|scheduleWithFixedDelay|ScheduledExecutorService|new Timer` 全量 grep 零命中（仅测试代码的接口 stub）——JDK「任务异常后静默停摆」语义在 8 模块主代码无适用面。不立项。
- **TimerServiceManager 的 catch-and-log 契约**（javadoc 于 `TaskProcessingTimeService.java:42-43` 提及）：属 `nop-stream-core` 基础设施（第 1 轮边界声明范围外），且注明有 `TestTimerServiceManagerRobustness` 覆盖；8 模块内无操作子直接使用该路径。不立项（留待 nop-stream-core 审计口径）。
- **reader close 的 join 超时不 interrupt**（`MessageSourceReader.java:199-206` / `DebeziumCdcSourceReader.java:300-308`）：typed 超时抛出后 runner 线程不被中断——维持第 1 轮不立项结论并复核成立：runner 仅做 1s 周期的 latch await，无阻塞 collect（collect 阻塞面在引擎/订阅线程），join(5s) 实际有界。

## 统计

- **P0**: 0
- **P1**: 2（C4-004、C4-005）
- **P2**: 1（C4-006）
- **P3**: 0
- **不立项（定向核对后排除）**: 7（见上节）
- **与第 1 轮合计**: P1 ×3（C4-001/004/005）、P2 ×3（C4-002/003/006）

# C4 线程池与执行器治理 — nop-stream-state-connectors 深挖第 3 轮追加

## 深挖第 3 轮追加

- **审计维度**: C4（线程池与执行器治理），第 1 轮 `C4-thread-pools.md`（C4-001~003）、第 2 轮 `C4-round2.md`（C4-004~006）之后的定向深挖
- **深挖盲区（本轮指派）**: ① 第 2 轮新发现（Debezium 引擎线程治理）的同族面；② 其余嵌入宿主（message 桥、batch）的线程生命周期
- **审计日期**: 2026-10-14
- **盲审边界**: 仅读取本维度第 1/2 轮文件与本轮新核对的 live code；未读 ai-dev/audits/ 其他目录、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/
- **核对口径**: 对 8 个 in-scope 模块 src/main 全量复跑线程形态兜底 grep（`ForkJoin|parallelStream|.parallel(|CompletableFuture|ThreadLocal|ExecutorService|ScheduledExecutor|new Thread(|GlobalExecutors|scheduleAtFixedRate|new Timer(`）——除已盘点的 `MessageSourceReader.java:87` 与 `DebeziumCdcSourceReader.java:132` 两处 `new Thread` 外零命中，即 8 模块不存在任何未盘点的线程/池/异步调度形态；同族面核对扩展到嵌入宿主的底座实现（nop-message-kafka/pulsar/core、nop-message-debezium），与第 2 轮的范围扩展口径一致（每条结论给出 in-scope 锚点）。

---

## 结论：**终止信号——无新发现**

本轮对两个指派盲区完成定向核对，全部核查面或治理良好、或属已声明边界、或已被前两轮发现覆盖，无达到证据标准的新发现。C4 维度的发现集在前两轮后收敛：**P1 ×3（C4-001/004/005）、P2 ×3（C4-002/003/006），本轮不新增。**

---

## 定向核对记录（零发现面声明）

### 盲区①：Debezium 引擎线程治理的同族面

同族 = 「长驻阻塞任务的宿主线程治理」（fire-and-forget stop / 共享池常驻 / 启动守卫不对称——C4-004/005/006 三个根因形态）。逐一核对全部同族成员：

- **Kafka 消息订阅底座**（`nop-message/nop-message-kafka/.../KafkaMessageService.java:428` 起）：每订阅 1 条单线程 executor（`NamedThreadFactory("nop-msg-kafka-consume-<groupId>", true)` daemon 具名）；`KafkaMessageSubscription.cancel()`（:575-602）为完整终结链——`task.stop()`（consumer.wakeup）→ `executor.shutdown()` → `awaitTermination(5s)` → `shutdownNow()` → `awaitTermination(1s)` → 超时 WARN → **之后**才 `consumer.close()`（消除 close 与 poll/commit 的跨线程竞态）→ `subscriptions.remove(this)`；`AtomicBoolean cancelled` 保证并发 cancel 幂等。**恰为 C4-005 建议修复形态的既有先例**（该先例同时佐证 C4-005 的修复建议可直接复用本仓库模式），不立项。
- **Pulsar 消息订阅底座**（`nop-message/nop-message-pulsar/.../PulsarMessageService.java:494-544` 订阅、`:577-610` doOnCancel）：与 Kafka 同构的终结链（stop → shutdown → await 5s → shutdownNow → await 1s → consumer close），且订阅创建失败路径（:540-544）补齐 consumer close + executor shutdownNow（异常路径对称，F8 通过）。不立项。
- **LocalMessageService 重投递调度器**（`nop-message/nop-message-core/.../local/LocalMessageService.java:274-294`）：懒初始化单线程 ScheduledExecutor（daemon、具名 `nop-local-message-redelivery`）、**每 service 单例恰 1 条**（非每订阅），double-check 在 synchronized 内正确；调度拒绝走 typed WARN（F-40）。生命周期归宿为 service 单例，量级有界。不立项。
- **NopStreamOffsetBackingStore.REGISTRY 静态注册表**（`nop-message-debezium/.../NopStreamOffsetBackingStore.java:59`）：键 = 实例名（baseName[-subtaskIndex]），条目数上界 = 不同 CDC 实例基数（有界）；`clearConnector`（:101-103）在全部首跑路径被 `DebeziumCdcSourceFunction.newFreshOffsetStore():440-444` 调用（AR-03），`setOffsets` 为 REPLACE 语义（R5 CON-03）。属设计内的跨重启 offset 载体，非无界账本。不立项（账本增长语义属 C7 复核面，C4 无线程/池面）。
- **FAILURE_LISTENERS 静态注册表**（`DebeziumEngineWrapper.java:44-71`）：`registerFailureListener`（run 进入时 put，同名替换）与 `unregisterFailureListener`（run() finally :298 必达；`resolveConnectorName()` 抛错时 connectorName 为 null、unregister null 安全且此时也无已注册项）严格配对；条目数上界 = 存活 CDC 实例数。不立项。
- **DebeziumMessageSource 残留面**（全文通读）：`subscriptions` ConcurrentHashMap 随 `stop():179` clear、随 `Subscription.cancel:222` remove，配对完整；`dispatchEvent` 的 per-consumer catch 由 R5-CON-02 的 lambda 内捕获语义兜底（collect 失败在订阅 lambda 内记 pendingError，不依赖 dispatcher 传播）；`startEngineIfNeeded` synchronized + `started` flag 与第 2 轮 C4-005/006 已覆盖的 stop 时序面同根因，不重复计数。不立项。
- **ReplayableCdcSourceFunction**（nop-stream-fraud-example）：维持第 2 轮「范围外，登记供该模块审计引用」结论。

### 盲区②：其余嵌入宿主（message 桥、batch）的线程生命周期

- **message 桥宿主**（in-scope `nop-stream-connector`）：
  - `MessageSourceFunction`（全文复核）：订阅生命周期 R5-CON-04 成对（run() finally :196-205 全退出路径 `localSubscription.cancel()`，幂等）；`shutdownLatch` 每 run 重建（:130-133 区域重启重置契约）；pendingError 单通道上报。cancel 底座终结保证由上方 Kafka/Pulsar 核对承载。不立项。
  - `MessageSourceReader`（全文复核）：runner 线程模型维持第 1 轮 OK 判定；本轮补充确认 `close():195-212` 的 `join(5s)` 在 Kafka/Pulsar 底座下有界性更强——底座 cancel 自带 6s 级终结预算，极端叠加时 reader join 超时 typed 上抛（fail-fast 通道存在，非静默）。不立项。
  - `MessageSinkFunction`（全文读）：无状态、`messageService.send` 同步发送，无线程/池/缓冲面。不立项。
  - `FileSourceReader`：无线程创建（grep 证实）；`closeActiveReader():398-404` 与 poll/`close():451` 配对。不立项。
  - 枚举器（`MessageTopicSplitEnumerator`/`FileSplitEnumerator`/`BatchLoaderSplitEnumerator`）：无线程创建点（grep 证实），承载线程为 runtime driver。不立项。
- **batch 嵌入宿主**（in-scope `nop-stream-connector-batch`，全文读）：
  - `BatchLoaderSourceFunction`：无自有线程；loader 在 `run() finally:144-148` 按 `AutoCloseable` 关闭（异常/取消路径覆盖）；`checkConnection():163-174` probe loader 有 close。不立项。
  - `BatchConsumerSinkFunction`：无自有线程；constructor 建立的 consumer 在 `close():141-173` 关闭（flush 失败优先 + suppressed 语义）；单线程契约由 `validateParallelism:71-81` 部署期守卫钉定。不立项。
  - `BatchLoaderSourceReader`：`closed` flag + `close():221-229` 对 loader 的 AutoCloseable 关闭，幂等（:124 `finished || closed` 早退）。不立项。
- **checkConnection 探测 producer 的线程生灭频率**（`KafkaTwoPhaseCommitSink.checkConnection:307` probe producer 每次探测建 1 条 sender 线程再 TWR 关闭）：全部调用点为冷路径——`StreamConfValidator`（提交前校验）与 `StreamConnectorCatalog`（catalog 探测，探测后即 close），无周期性/热路径调用者（`StreamConnectivityProber` 仅被这两处调用）。线程生灭频率 = 人工操作频率，不构成治理缺陷。不立项。
- **8 模块兜底 grep**：`ForkJoin|parallelStream|CompletableFuture|ThreadLocal|ExecutorService|ScheduledExecutor` 零命中——不存在「CF 默认池/commonPool 间接使用」「池化 ThreadLocal 泄漏」「隐藏调度器」面。第 1 轮清单表的线程盘点在此确认完备。

## 统计

- **P0**: 0
- **P1**: 0
- **P2**: 0
- **P3**: 0
- **不立项（定向核对后排除）**: 12（见上两节核对记录）
- **终止信号**: C4 维度深挖收敛。三轮合计发现集不变：P1 ×3（C4-001 KafkaProducer 无关闭、C4-004 引擎常驻共享池、C4-005 stop fire-and-forget）、P2 ×3（C4-002 HttpClient 无关闭、C4-003 registry 无请求超时、C4-006 启动 lambda 缺守卫）。

## 维度复核结论

- **复核人**: 独立复核子代理（与本审计主 agent 不同会话）
- **复核日期**: 2026-10-14
- **复核方法**: 逐条重读 live code + 调用方（`KafkaTwoPhaseCommitSink` 全文、`AvroSchemaRegistryClient`/`AvroWireFormat` 全文、`DebeziumEngineWrapper` 全文、`DebeziumCdcSourceReader`/`DebeziumCdcSourceFunction`/`DebeziumSourceSupport`/`DebeziumMessageSource` 定向段、`StreamSinkOperator.close`、`ThreadPoolConfig`/`CommonConfigs` 池参数），三态判定（已保留/降级/驳回）。本轮解除盲审：已核对 ai-dev/audits/ 历史审计（r5/r6 quality 审计）与 git log，为保留项标注现状归属。
- **复核口径**: 写不出具体触发时序的并发发现降 P2；已声明边界 + 守卫不是缺陷。

### 逐条判定

- **R-C4-001 → 已保留（P1）**。live 复核：类声明（:77）只 implements `ConnectivityCheckable`，主 producer 全路径无 close（唯一 TWR close 在 `checkConnection` 的 probe producer :307）；`StreamSinkOperator.close():175` 的 `instanceof AutoCloseable` 通道到不了该 sink，基类 `TwoPhaseCommitSinkFunction` 无 close。触发为主路径确定性形态（每次 job 提交-停止循环泄漏 1 组 daemon sender 线程 + selector + broker 连接）。owner doc 的 kafka-2pc EXPERIMENTAL 豁免经核对仅覆盖「残余重复窗口」语义，不覆盖资源生命周期，不构成边界豁免。修复建议（实现 AutoCloseable 复用既有 close 通道）成立。
  - **现状归属**: 本审计首次立项，无历史跟踪（r5/r6 quality 审计未覆盖该泄漏面）；本 worktree 无修复提交，未修复。
- **R-C4-002 → 已保留（P2）**。live 复核：全类无 close 方法、:48 HttpClient 仅 connectTimeout；JDK 21 HttpClient 已是 AutoCloseable 但无任何调用方关闭。每份 wire-format 装配泄漏 1 条 daemon selector 线程，触发时序具体（job 装配-停止循环），footprint 小于 C4-001，P2 校准恰当。
  - **现状归属**: 本审计首次立项（WI18/plan 2341 引入面）；未修复。
- **R-C4-003 → 已保留（P2）**。live 复核：`readJsonResponse:116` 的 send 无 request.timeout（connectTimeout 只覆盖建连）；`AvroWireFormat.encode:55` 在 `computeIfAbsent` 桶锁内做 HTTP 核实；decode:92/`getLatestSchema:78` 同修复面。触发时序具体（registry 建连后应用层挂起 → task 线程 socket read 无界阻塞，仅能靠 checkpoint 超时间接暴露）；`copyForSubtask` 传同一 Function 实例，跨 subtask 并发与桶锁放大成立。等待点无界且无 typed 失败，P2 恰当。
  - **现状归属**: 本审计首次立项；未修复。
- **R-C4-004 → 已保留（P1）**。live 复核：`DebeziumEngineWrapper.start():159` 将无限阻塞的 `engine.run()` 提交 `GlobalExecutors.globalWorker()`；池参数核实 core=4（`ThreadPoolConfig.java:17`）/max=30（`CommonConfigs.java:27` 覆盖类默认 20）/queue=10000；B 层并行（`validateParallelism`）显式支持 parallelism>1，每 subtask 恰 1 条引擎线程占满至 job 停止。这是 C4 步骤 4 任务隔离检查的定向命中形态（常驻任务钉死共享池，一个业务挤占全部业务的线程预算），影响半径为全平台共用面；owner doc 未声明「引擎线程模型 = 共享池」为已接受边界。饱和算术确定性（30 引擎全占；低于饱和时启动排队构成 C4-006 前置）。
  - **现状归属**: 本审计首次立项；r5 审计 CON-02 证据片段中已可见同一 globalWorker 提交形态，但未立项线程治理面。缺陷主体在 nop-message-debezium（wrapper），经第 2 轮派发的范围扩展 + in-scope 使用链锚点（connector-debezium）立项成立；未修复。
- **R-C4-005 → 已保留（P1）**。live 复核：`stop():195-215` 为 fire-and-forget（`engine.close()` 异步信号、异常 catch 后仅记日志、无任何 join/await）核实；`DebeziumCdcSourceReader.close():300-308` 的 join 只覆盖 runner 线程（runner 在 stop 返回后 1s latch 轮询内退出），对引擎终结零保证核实；`DebeziumSourceSupport.instanceName` 确定性命名 → 重启必然同名核实；模块自证注释（`DebeziumCdcSourceFunction:656-658`：同 serverName 双实例后注册者卡死 `JmxUtils.registerMXBean` 重试循环）核实。触发时序具体（stop → 旧引擎未终结 → region restart → 新引擎同名 MBean 注册）。与历史已修项正交：R5-CON-02 修的是启动窗口早退泄漏（:200 gate），对本面无作用；R5-CON-03 的 failure listener 依赖引擎终态死亡触发，楔死形态恰好绕过该修复。P1 成立。
  - **现状归属**: 本审计首次立项；关联历史 R5-CON-02/03（修复均在位）但为正交面；未修复。
- **R-C4-006 → 已保留（P2）**。live 复核：启动 lambda（:161-163）无任何 stopped 检查，`stopped`（stop 写）与 `running`（lambda 写）互不引用核实。触发窗口具体（池饱和排队 × 快速取消——C4-004 形态下两前置概率同时上升），一行守卫可独立修复；最坏后果（永久僵尸引擎）依赖 debezium EmbeddedEngine 对 close-before-run 的第三方语义，P2 校准合理（属「后果上界依赖第三方」而非「缺时序」型降级）。与 R5-CON-02 为同一状态机（startRequested/stopped/running）的反向窗口，不重复计数。
  - **现状归属**: 本审计首次立项；R5-CON-02 已修 gate 对本窗口无作用；未修复。

**降级/驳回说明**: 无。6 条发现均有 live code 直接锚点与具体触发时序，无需按「缺时序降 P2」规则降级；无一条落在「已声明边界 + 守卫」豁免内——kafka-2pc EXPERIMENTAL 豁免仅覆盖语义残余窗口，CEP 定时器 mailbox 串行契约属边界声明且已有守卫（维持不立项）。

### 最终保留项

| 编号 | 判定 | 严重程度 | 一句话缺陷 | 现状归属 |
|---|---|---|---|---|
| C4-001 | 已保留 | P1 | KafkaTwoPhaseCommitSink 的 KafkaProducer 全路径无关闭，job 停止/重启即泄漏 daemon sender 线程与 broker 连接 | 首次立项（本审计）；未修复 |
| C4-002 | 已保留 | P2 | AvroSchemaRegistryClient 的 JDK HttpClient 无关闭路径，job 结束后残留 selector 线程与连接池 | 首次立项（本审计）；未修复 |
| C4-003 | 已保留 | P2 | Avro registry HTTP 只有 connectTimeout 无请求级超时，且首次调用在 computeIfAbsent 锁内做 HTTP | 首次立项（本审计）；未修复 |
| C4-004 | 已保留 | P1 | Debezium 引擎以无限阻塞任务常驻平台共享 nop-global-worker 池（core=4/max=30/queue=10000），每 CDC subtask 钉死 1 条共享线程 | 首次立项（本审计，r5 证据中可见形态但未立项）；未修复 |
| C4-005 | 已保留 | P1 | 引擎 stop 为 fire-and-forget，快速重启时新旧引擎同名交叠，新引擎卡死 MBean 注册循环（RUNNING 假活，绕过 R5-CON-03 修复） | 首次立项（本审计）；与已修项 R5-CON-02/03 正交；未修复 |
| C4-006 | 已保留 | P2 | 引擎启动 lambda 缺 stopped 守卫，饱和池排队窗口内 stop 先完成后 lambda 无条件 engine.run()（最坏永久僵尸） | 首次立项（本审计）；R5-CON-02 已修 gate 对本窗口无作用；未修复 |

**复核统计**: 保留 6（P1 ×3、P2 ×3）/ 降级 0 / 驳回 0；与初审 + 深挖合计口径一致，发现集收敛。

## 维度复核结论（独立复核子代理·二轮）

- **复核人**: 独立复核子代理·二轮（与初审 agent、一轮复核子代理均不同会话）
- **复核日期**: 2026-10-14
- **复核对象**: 本文件全部 6 条发现（C4-001~006；第 2/3 轮深挖内容内嵌于本文件，仓库中不存在独立 C4-round2/round3 文件，以主文件为准）
- **复核方法**: 逐条重读 live code 全文或定向段（`KafkaTwoPhaseCommitSink` 全文 + `StreamSinkOperator.close` + `TwoPhaseCommitSinkFunction`/`StreamFunction` 接口链、`AvroSchemaRegistryClient`/`AvroWireFormat` 全文、`DebeziumEngineWrapper` 全文、`DebeziumCdcSourceReader`/`DebeziumCdcSourceFunction`/`DebeziumSourceSupport`/`DebeziumMessageSource` 定向段、`GlobalExecutors`/`ThreadPoolConfig`/`CommonConfigs` 池参数与 daemon 标志），并做第三方依赖语义验证（kafka.version=4.3.1 钉定核对；debezium-embedded 3.7.0.Final `AsyncEmbeddedEngine` close/run/setEngineState 字节码反编译；debezium-core 2.4.0.Final `JmxUtils` sources 核对）；解除盲审核对历史审计（r5/r6 quality 审计 summary 与 connectors/concurrency 报告结论节、同日 nop-stream-core-runtime 并发审计 C4 报告）与 git log。三态判定（保留/降级/驳回），每条做反证检查（池队列有界性、关闭路径是否真无 awaitTermination、任务是否真无法取消）。
- **一轮复核结论**: 全部 6 条已保留（P1×3/P2×3）；本轮逐条独立重验，判定如下。

### 逐条判定表

| 编号 | 原判级 | 二轮判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C4-001 | P1 | 已保留（P1） | 类声明（:77 只 implements ConnectivityCheckable）、producer 字段（:89）、唯一创建点（:154-155）、唯一 TWR close 在 probe（:307）逐行核实；close 通道到不了该 sink（`StreamSinkOperator.close:175` instanceof AutoCloseable + 基类 `TwoPhaseCommitSinkFunction` 无 close + `StreamFunction` 仅 marker 接口）；反证均不成立（sender 线程无自灭路径、rollback/abort/recover 只管事务不管 producer、stop 后无二次补救 stop）——主路径每次 job 提交-停止循环泄漏 1 组 | 新发现（本审计首次立项；r5/r6 审计无此泄漏面，git log 该文件仅 ae0217fc14 特性提交）；未修复 |
| C4-002 | P2 | 已保留（P2） | 全类无 close、:48 仅 connectTimeout 核实；HttpClient（Java 21+ AutoCloseable，工程 Java 25）selector 线程无任何关闭调用方、线程自身强引用使 GC 不可达；持有链经 avroSerializer/avroDeserializer 工厂注入 kafka 连接器核实；footprint 小于 C4-001，P2 恰当 | 新发现（首次立项；该文件仅 e7c0f8c774 特性提交）；未修复 |
| C4-003 | P2 | 已保留（P2） | `readJsonResponse:116` send 无请求级超时（connectTimeout 只覆盖建连）、`encode:55` computeIfAbsent 桶锁内 HTTP、`decode:92`→`fetchSchemaById:89` 同修复面核实；`copyForSubtask`（KafkaTwoPhaseCommitSink:289-292）传递同一 Function 实例 → 跨 subtask 共享同一 client/锁面成立；JDK HttpClient 无默认响应超时，"建连成功后对端挂起"为唯一无界失败面（IOException/非 200 均 typed） | 新发现（首次立项）；未修复 |
| C4-004 | P1 | 已保留（P1） | `:159` globalWorker 提交、core=4（ThreadPoolConfig:17）/max=30（CommonConfigs:27 默认）/queue=10000（:25）/daemon（GlobalExecutors setThreadDaemon(true)）逐项核实；`validateParallelism:487` 支持 parallelism>1；引擎任务无限阻塞（3.7 `AsyncEmbeddedEngine.run` → runTasksPolling 至 close）钉死共享线程 = C4 任务隔离检查定向命中；非测试共用面 grep 实测 24 处（初审记 22，计数口径差异、平台级共用结论不变）；池确有界（30/10000）故为「驻留挤压」非「累积泄漏」，P1 非 P0 校准成立 | 新发现（首次立项；r5 CON-02 修复未立项线程治理面，同日 nop-stream-core-runtime 并发审计 C4 报告亦无 globalWorker 条目）；未修复 |
| C4-005 | P1 | 已保留（P1） | stop（:195-215）无任何 join/await/close 异常吞掉（:210-212）且 `stopped=true` 封死重试、reader join（:300-308）只覆盖 runner、instanceName 确定性命名（DebeziumSourceSupport）、run() 1s latch 轮询全部核实；二轮反编译补充强化：3.7 `AsyncEmbeddedEngine.close()` 在 STARTING_TASKS 窗口抛 IllegalStateException（"Cannot stop engine while tasks are starting"）恰被 stop() 吞掉 → 取消路径存在「不可停引擎」的永久泄漏形态，P1 独立于 MBean 机制成立（校准注记①见下） | 新发现（首次立项；R5-CON-02/03 修复在位〔c79be63506〕但为正交面——live code 注释与提交均确认）；未修复 |
| C4-006 | P2 | 已保留（P2） | 启动 lambda（:161-163）无 stopped 检查、`stopped`（stop 写）与 `running`（lambda 写）互不引用、stop() 被 stopped gate 封死后孤儿引擎无任何 API 可停——守卫缺失为代码直读事实；二轮反编译钉定第三方语义：3.7 引擎为 AtomicReference 状态机，close-before-run 将状态 CAS 为 STOPPING，后到的 `run()` 在首个 CAS（CREATING→INITIALIZING）处抛 IllegalStateException 立即失败——「永久僵尸 binlog 消费者」恶性读法在钉定版本不成立；缺陷实质为「安全性依赖未文档化的第三方状态机不变量（版本耦合）」，属 P2 口径档位（理论风险），非 P3 优化候选 | 新发现（首次立项；R5-CON-02 已修 gate 经核实对本窗口无作用）；未修复 |

### 不确定项（显式列出）

1. **C4-005 校准注记①（MBean「卡死」为有界停顿）**: 初审与模块自证注释（DebeziumCdcSourceFunction:656-658）称后注册者「卡死在 JmxUtils.registerMXBean 重试循环」。二轮核对 debezium-core 2.4.0.Final sources（本地缓存唯一 core 版本；3.7.0.Final core jar 未缓存）：`REGISTRATION_RETRIES=12 × REGISTRATION_RETRY_DELAY=5s` ≈ 最长约 60s **有界**重试后放弃并仅记 ERROR、引擎继续运行（metrics 缺失）——「楔死」实为约 60s 有界启动停顿，重叠后果收敛为「双引擎并存 + 60s 启动停顿」而非永久假活；3.7 是否保留该实现未验证。P1 判定不依赖该机制：fire-and-forget stop、close 异常吞掉 + stopped 封死重试（STARTING_TASKS 窗口为真实可触发竞态窗）、重叠期双 binlog 连接与 offset REGISTRY 同名桶双写均为代码直读事实。
2. **C4-001 daemon 标志**: sender 线程 daemon=true 引自初审对 kafka-clients 4.3.1 jar 的反编译；本轮核实 4.3.1 为 nop-dependencies 钉定版本（pom.xml:52）但未独立重复反编译。daemon 与否不改变泄漏判定（非 daemon 反而更糟——阻塞 JVM 退出）。
3. **globalWorker 非测试使用点计数**: 初审记 22 处，本轮按行 grep 计 24 处（多行调用展开差异），「平台级共用面」结论不受影响。

**降级/驳回说明**: 无。6 条发现的文件/行号、证据、触发时序与判级口径（P1=取消/错误路径资源泄漏或负载上升必然池耗尽；P2=边界配置缺陷/理论风险）经二轮独立重验全部成立；二轮新增的第三方语义反编译结果只收紧了 C4-005/006 的机制描述精度，不改变任何判定。

## 最终保留项（二轮复核后）

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|---|---|---|---|---|
| C4-001 | P1 | `nop-stream/nop-stream-connector-kafka/src/main/java/io/nop/stream/connector/kafka/KafkaTwoPhaseCommitSink.java` | KafkaProducer 全路径无关闭（未实现 AutoCloseable、基类无 close 通道），job 停止/重启即泄漏 daemon sender 线程与 broker 连接 | 新发现；未修复 |
| C4-002 | P2 | `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java` | JDK HttpClient 无关闭路径，每份 Avro wire-format 装配在 job 结束后残留 daemon selector 线程与连接池 | 新发现；未修复 |
| C4-003 | P2 | `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java`（+ `AvroWireFormat.java`） | registry HTTP 仅 connectTimeout 无请求级超时（挂起形态无界停滞），且首条记录在 computeIfAbsent 桶锁内做 HTTP | 新发现；未修复 |
| C4-004 | P1 | `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java`（in-scope 锚点 `nop-stream-connector-debezium/.../DebeziumCdcSourceReader.java:132`） | Debezium 引擎无限阻塞任务常驻平台共享 nop-global-worker 池（core=4/max=30/queue=10000），每 CDC subtask 钉死 1 条共享线程，任务隔离失效 | 新发现；未修复 |
| C4-005 | P1 | `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java`（in-scope 锚点 `DebeziumCdcSourceReader.close:295-312`） | 引擎 stop 为 fire-and-forget（无终结等待、close 异常吞掉且 stopped 封死重试），快速重启时新旧引擎确定性同名交叠：不可停引擎 / 双 binlog 连接 / offset REGISTRY 双写 | 新发现；未修复（R5-CON-02/03 在位但正交） |
| C4-006 | P2 | `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java` | 引擎启动 lambda 缺 stopped 守卫：饱和池排队窗口内 stop 先完成后 lambda 无条件 engine.run()；安全性依赖第三方状态机不变量（3.7 反编译验证良性，属版本耦合的理论风险） | 新发现；未修复（R5-CON-02 gate 对本窗口无作用） |

**二轮复核统计**: 保留 6（P1 ×3、P2 ×3）/ 降级 0 / 驳回 0；与一轮复核及初审+深挖合计口径一致，发现集收敛确认。需子项复核的高风险项：无（C4-004/005/006 的修复设计建议合并实施——专用单线程执行器 + 终结 latch 可同时消除 004 的常驻挤压、005 的无等待与 006 的排队窗口）。
