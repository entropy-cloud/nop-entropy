# C8 背压·流控·过载行为 深挖追加报告（C8-round2.md）— nop-stream-state-connectors

- **审计维度**: C8（背压、流控与过载行为）— 深挖追加轮
- **基线**: 同目录 `C8-backpressure.md`（含第 1 轮 C8-001…C8-005 + 已并入该文件的深挖轮 C8-006…C8-010）
- **编号说明（重要）**: 派发指令假设基线止于 C8-005（本轮从 C8-006 起编号）。实际读取发现 `C8-backpressure.md` 已吸收两轮深挖内容至 C8-010（第 2 轮 C8-006/007/008 + 第 3 轮 C8-009/010，形式与派发指令描述的「先前中断轮次遗留」一致）。按「保留已有内容与编号，不得覆盖」规则，**本轮新发现自 C8-011 起编号**，不与既有 ID 冲突。
- **盲审边界**: 本轮仅读取本维度基线文件 `C8-backpressure.md` 与 owner docs（`docs-for-ai/03-modules/nop-stream.md`、`nop-stream-connectors.md`）、grep 基线 `_tmp/cra-baseline-nop-stream-state-connectors.txt`；未读取 `ai-dev/audits/` 下其他维度文件、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`。`nop-api-core`/`nop-message`/`nop-sys`/`nop-stream-core` 相关类仅作边界上下文证据（沿用第 2 轮对 `CheckpointConfig` 的引用口径），不作为发现对象。
- **日期**: 2026-10-14
- **复核状态**: 已复核（2026-10-14 二轮独立复核，C8-011/012/013 逐条判定回填于各条目；汇总见 C8-backpressure.md 文末「维度复核结论（独立复核子代理 · 二轮复核）」）

---

## 1. 本轮发现

### [nop-stream-state-connectors-C8-011] message sink 每记录热路径同步 send 无超时：连接器侧 opt-out 平台 sendTimeout 机制 + syncGet 无界等待——挂死后端对任务线程不可见阻塞（F5 第三无限期等待点）

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSinkFunction.java:43-51`；关联边界证据：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/message/IMessageSender.java:21-27`（2-arg `send` 默认 `options=null` → `FutureHelper.syncGet`）、`MessageSendOptions.java:24`（`sendTimeout` 旋钮存在）、`nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaMessageService.java:308-325`（options.sendTimeout 被真实消费，缺省回退 producer config 或 0=无界）、`nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:424-431`（DB 写在 future 创建前同步执行）
- **证据片段**:
  ```java
  // MessageSinkFunction.java —— 每记录热路径（SinkFunction.consume = 算子链主路径）
  @Override
  public void consume(T value) throws Exception {
      ...
      messageService.send(topic, value);      // L50：2-arg 重载 → send(topic, value, null)
  }
  // IMessageSender.java（平台 API）
  default void send(String topic, Object message) {
      send(topic, message, null);             // options=null → sendTimeout 旋钮全程未启用
  }
  // MessageSendOptions.java（平台已声明且被后端真实消费的旋钮）
  /** 发送超时时间，多长时间内未发送出去认为消息已超时。单位为毫秒 */
  private long sendTimeout;
  // KafkaMessageService.java:321-325（旋钮被后端消费；回退链末位 = 0 = 无配置上界）
  if (options != null && options.getSendTimeout() > 0) {
      return options.getSendTimeout();
  }
  if (defaultProducerConfig != null) { return defaultProducerConfig.getSendTimeoutMs(); }
  return 0L; // no configured bound
  // SysDaoMessageService.java:424-431（DB 后端：同步写发生在 future 存在之前）
  public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
      MessageOptionsValidator.validateSend(CAPABILITIES, options, "sysdao", WARN_ONCE);
      try {
          saveMessage(topic, message, options == null ? 0 : options.getDelay());  // 同步 DB 写
  ```
- **严重程度**: P2
- **并发场景**: sink 任务线程逐记录执行 `consume` → 同步 send；对端为进程外消息后端（Kafka broker / Pulsar / DB）。任务线程与后端之间的挂起形态无平台内第二线程参与，是「任务线程 × 后端套接字读/同步 DB 写」的单点无限期阻塞；syncGet 侧 `ContextTaskQueue.syncGet` 的等待同样无超时（future 不完成则永不返回）。
- **触发时序**: T1：管线以 message sink 收尾（EXPERIMENTAL，AT_LEAST_ONCE，B6 之外未被第 1 轮 B 表单独立边界的 sink）。T2：后端挂起——SysDao 后端时 DB GC 长停/网络半开/行锁未设超时，`saveMessage` 的同步 DB 写直接阻塞任务线程（此时 future 尚未创建，任何 future 级超时都不适用，与 C8-009 同一触发类）；Kafka/Pulsar 后端时若部署未配 backend 级 `sendTimeoutMs`/`sendTimeout`，回退链落到 `0 = no configured bound`，仅剩 producer 内部 delivery.timeout.ms（Kafka 120s，有界但属后端实现细节而非连接器契约）。T3：任务线程滞留 `consume`，管线停摆——源侧背压逐级积压（B1/B2 队列满、Kafka poll 停滞），checkpoint barrier 不再推进；全程无超时、无失败、无指标（C8-008 的 B-sink-message 面）。T4：仅当套接字被内核断开/DB 自愈或进程重启才收敛。
- **现状**: 平台的 `MessageSendOptions.sendTimeout` 是**已声明且被 Kafka/Pulsar 后端真实消费**的机制（非纸面旋钮），message sink 连接器经 2-arg 重载把它整体 opt-out；连接器自身参数面也无 sendTimeout 配置项。第 1 轮 F5 扫描以显式等待 API（poll/await/join）为对象，第 3 轮「超时面全量盘点」以 `timeout|TimeUnit.|Duration.of|TIMEOUT|_MS\b` grep 为手段——两者都**结构性地发现不了「调用点传 null options 导致旋钮未生效」的缺失形态**（该文件不含任何超时 token），本条修正第 3 轮 §1 盘点的完备性声明（同第 3 轮修正第 1 轮的先例）。
- **风险**: 每记录热路径上的无限期阻塞是三个已立案等待点（C8-004 registry、C8-009 JDBC commit）中暴露面最大的一个——registry/commit 都是低频或 barrier 级，message sink 的 send 是逐记录执行，后端变慢即刻逐级放大为管线停摆且不可判别根因；SysDao 后端形态与 C8-009 共享同一「无 queryTimeout 的 DB 写」触发类。
- **建议**: ① `MessageSinkFunction` 增加可配 `sendTimeoutMs`（builder/工厂参数，缺省取与 webhook 渠道同量级的 5-30s），`consume` 改传 `MessageSendOptions` 显式设值，超时 typed 失败（任务失败重启，响亮优于挂死）；② owner doc 能力矩阵 message sink 行补「发送超时契约」声明（当前「阻塞 send future」声明未含时长边界）；③ backend 缺省 0=无界的回退链建议至少 WARN 一次（后端侧，越界注记）。
- **失效面枚举**: ① `MessageSinkFunction.consume`（本条主面）；② C8-004 `AvroSchemaRegistryClient` HTTP 读——同族第一等待点（已立案）；③ C8-009 `JdbcTwoPhaseCommitSink.commit`——同族第二等待点（已立案），本条为第三点且唯一在逐记录热路径；④ `MessageSourceReader`/`MessageSourceFunction` 的 collect→put 路径——阻塞 put 是 B1 已声明背压语义，且 Kafka 后端消费线程为每订阅独立单线程 executor（`Executors.newSingleThreadExecutor`，KafkaMessageService:428），无跨订阅队头阻塞，不属本族；⑤ `BatchConsumerSinkFunction` 对 `IBatchConsumerProvider` 的 flush——provider 为用户注入且平台无对应超时旋钮，按第 3 轮 C8-009 失效面⑥ 的「不为用户代码设超时」口径不适用（与①的区别：IMessageService 有现成 sendTimeout 机制而连接器未用，IBatchConsumer 无机制可不用）。
- **信心水平**: 确定（调用点传 null options、旋钮被后端消费、syncGet 无界、SysDao 同步 DB 写均为代码直接事实；「挂死」需后端停/半开前提，判级与 C8-004/C8-009 同口径）。
- **误报排除**: 非「平台基础设施自身实现」——发现对象是连接器对平台 API 的使用方式（opt-out 现成超时机制），平台侧仅作上下文；非「低频路径」——consume 是每记录执行；非与 C8-009 重复——不同模块、不同机制（消息 sendTimeout vs 语句 queryTimeout）、且本条独有「旋钮存在但被调用侧 opt-out」形态；非 F2——at-least-once 语义未破坏，纯可用性/可观测缺陷。
- **复核状态**: 已保留（P2 维持，2026-10-14 二轮独立复核）——MessageSinkFunction.java:50 2-arg send → IMessageSender 默认链 `send(topic,message)`→`send(topic,message,null)`（IMessageSender.java:36-39）→ `syncGet(sendAsync(...,null))`（:26-28）逐行复核成立；sendTimeout 旋钮被 Kafka 后端真实消费（KafkaMessageService.java:311-313 `future.orTimeout`）证实「旋钮 opt-out」形态、回退链末位 0=无界（:327）、SysDao 同步 saveMessage 先于 future（SysDaoMessageService.java:427）均成立。合并口径裁定：与 C8-004/C8-009 维持三分立（不同模块、不同机制；本条独有「每记录热路径 + 现成旋钮被调用侧 opt-out」形态）。现状：新发现未修复（git log 8 模块无修复提交；历史审计无 sendTimeout opt-out 先例）

### [nop-stream-state-connectors-C8-012] push→pull 桥（message / debezium）的 256 槽位只有条数界、没有字节界：无 per-record 大小守卫、无包络声明，大 payload 场景 256×S 即堆失控——与 file source 的 maxLineBytes 守卫族内不一致

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceReader.java:52-54, 85, 222-231`；`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceReader.java:57, 130, 326-336`；对照正例 `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSourceReader.java:60, 335`（DEFAULT_MAX_LINE_BYTES=16MB typed 守卫）
- **证据片段**:
  ```java
  // MessageSourceReader.java —— 容量语义 = 条数
  static final int QUEUE_CAPACITY = 256;            // L53
  queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY); // L85
  ...
  public void collect(T element) {
      try {
          // blocking put = legacy synchronous-collect backpressure parity
          queue.put(element == null ? NullValueMarker.INSTANCE : element);  // L225：无大小检查
  // DebeziumCdcSourceReader.java —— 同构
  static final int QUEUE_CAPACITY = 256;            // L57
  ...
  queue.put(element);                               // L329：无大小检查
  // 对照正例 FileSourceReader.java（同族 source 边界已有的字节守卫）
  public static final long DEFAULT_MAX_LINE_BYTES = 16L * 1024 * 1024;   // L60（R5-CON-07）
  ```
- **严重程度**: P2
- **并发场景**: 引擎/订阅线程（put 生产者）与任务线程（poll 消费者）跨线程共享 256 槽位 `ArrayBlockingQueue`；积压深度受条数界约束，但**每槽位驻留字节数无界**——堆占用 = 256 × 单记录字节数 S，S 是用户/上游数据库完全控制且两个桥都不检查的自由变量。
- **触发时序**: T1：message source 订阅大 payload 主题（如文档/二进制事件，S=5-10MB；nop-message 载荷为任意 Object，无大小契约）或 Debezium 快照阶段流式读取含 LONGBLOB 宽表（单行事件 multi-MB）。T2：下游处理速率 < 生产速率（恰为本维度的背压场景），队列填满 256 槽并驻留。T3：桥内驻留堆 ≈ 256×S（S=10MB → 2.56GB/子任务），叠加任务线程正在处理的当前记录与下游缓冲 → 默认堆下 OOM 或 GC 死亡螺旋；put 端阻塞（背压语义正常）但内存边界已被字节维击穿。全程无 per-record 拒绝、无字节深度 gauge（C8-008 面）。
- **现状**: 两个桥的「有界」只在条数维成立；owner doc 能力矩阵对 message/debezium-cdc 无任何 payload 包络声明；同单元的兄弟边界全部具备字节维防线——file source `maxLineBytes`（16MB typed fail-fast，R5-CON-07）、kafka source 的 poll 批受客户端 `fetch.max.bytes`（默认 50MB）/`max.partition.fetch.bytes`（默认 1MB/分区）双重字节界。仅两个桥裸奔字节维。
- **风险**: 「有界队列」声明给出虚假安全感：条数界下运维按 256 估内存，实际内存随 payload 大小自由伸缩；OOM 发生在声明有界的缓冲上，与 C8-003（sink 缓冲条数无界）同属「缓冲的内存边界与规模声明脱节」缺陷族，但失效层不同（桥的字节维缺失 vs sink 的条数维缺失）。
- **建议**: ① 两桥增加可配 `maxRecordBytes` 守卫（对照 `maxLineBytes` 形态：超限 typed fail-fast 或降级 side-output/丢弃计数，缺省值对齐 16MB 量级）；构造期拒绝非正值；② 或改用字节水位队列（平台 `IBlockingQueue`+`QueueOverflowPolicy` 为条数/字节策略挂点，本单元当前零使用——见 §2 定向核查①）；③ owner doc 能力矩阵 message/debezium-cdc 行补 payload 包络声明。
- **失效面枚举**: ① `MessageSourceReader.QueueSourceContext.collect`→put（本条主面）；② `DebeziumCdcSourceReader` 引擎 handler→put——同族同修；③ `KafkaSourceFunction` poll 批——客户端字节界存在，**合格对照**；④ `FileSourceReader`——`maxLineBytes` typed 守卫，**合格对照（族内标杆）**；⑤ `BatchLoaderSourceReader`——`batchSize` 条数界 + loader 记录体积属用户契约（与 C8-009 失效面⑥ 同口径），不适用。
- **信心水平**: 确定（无大小检查为代码直接事实；OOM 链需大 payload 前提，S 为无上界自由变量——只要部署出现 multi-MB 记录即从「理论」变「必然」，与 C8-003 的「时间×速率」触发类同）。
- **误报排除**: 非「有界声明结构」误报——该排除针对「声明的界与规模声明自洽」的形态，此处恰恰是**界的维度不完整**（有界条数、无界字节）且无任何声明可依赖；非把「大消息」当缺陷——固有的是「缓冲记录」，不固有的是「兄弟边界都有字节防线而这两处没有 + 无包络声明」；非平台队列实现问题——桥是连接器自有代码；与 C8-008 不重复——本条失效层是内存边界本体，遥测缺失是叠加面。
- **复核状态**: 已保留（P2 维持，2026-10-14 二轮独立复核）——两桥 256 条数界、无任何字节维守卫逐行复核成立（MessageSourceReader.java:53/:85/:223-227、DebeziumCdcSourceReader.java:57/:130/:329；两文件 grep maxRecordBytes/serializedSize 零命中）；对照正例 FileSourceReader DEFAULT_MAX_LINE_BYTES=16MB（:60，R5-CON-07 javadoc :333-337）与 Kafka consumer fetch 字节界（§2.2）成立——「族内不一致」论点成立。现状：新发现未修复（git log 无修复提交；历史审计无桥字节界先例）

### [nop-stream-state-connectors-C8-013] RocksDB 增量快照路径每个 checkpoint 对全部 SST 做全量 SHA-256 重哈希，同步跑在 barrier 内任务线程——时长 ∝ 状态总体积（非增量），且是 C8-005/C8-010 推荐逃生门自身的时长放大面（F3 兄弟站点）

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalSnapshotStrategy.java:63-124, 88-107`；调用链 `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:828-832, 845-853`；预算上下文 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/CheckpointConfig.java:27-29`（interval=60s / timeout=600s，越界上下文引用同前轮口径）
- **证据片段**:
  ```java
  // RocksDBIncrementalSnapshotStrategy.doSnapshot —— checkpoint 内同步执行
  try (Checkpoint cp = Checkpoint.create(db)) {
      cp.createCheckpoint(nativeDir.toAbsolutePath().toString());   // L75-77：硬链接，廉价
  }
  ...
  try (Stream<Path> entries = Files.list(nativeDir)) {
      for (Path entry : (Iterable<Path>) entries::iterator) {
          ...
          if (lower.endsWith(".sst") || lower.endsWith(".ldb")) {
              String hash = SstFileChecksum.sha256Hex(entry);        // L96：每 SST 全文件读+哈希
              long size = Files.size(entry);
              sstHandles.add(new SharedStateHandle(hash, entry.toAbsolutePath().toString(), size));
  ```
- **严重程度**: P2
- **并发场景**: 单任务线程在 barrier 处理中同步执行 `snapshotState` → `snapshotIncremental`（:828-832 分支 → :853 `doSnapshot`，owner doc 已证同步快照语义）；与 runtime 侧固定 600s `checkpointTimeout` 之间无守卫/无旋钮/无声明耦合（C8-010 确立的缺陷类别）。无多线程竞态，纯「时长 ∝ 数据体积 × 固定预算」过载形态。
- **触发时序**: T1：作业因大状态选用 RocksDB 并按 C8-005/C8-010 的建议开启 `incrementalCheckpointEnabled`（escape hatch 路径）。T2：barrier 到达，`createCheckpoint` 硬链接 SST（廉价、增量语义成立），但随后 L88-107 对 native 目录下**每一个** SST（含与上一 checkpoint 完全相同、将被 content-addressing 共享的未变更文件）执行 `sha256Hex` 全文件读取+哈希。T3：时长 ≈ 总 SST 字节 / 哈希吞吐（~0.3-1GB/s，冷页缓存取下限）——100GB 状态 = 100-300s+/checkpoint，**每个** checkpoint 重复，期间记录处理完全停顿（屏障内同步）。T4：状态继续增长或慢盘 → 逼近 600s 预算 → 按 C8-010 的链进入「超时→3 次失败→FAIL→重启→重演」循环，且重启不缩小状态体积。
- **现状**: content-addressing 只省「durable 上传」，不省「本地哈希」——增量路径的时长复杂度仍是 O(总状态体积)/checkpoint 而非 O(增量)；（name,size）足以做不变性判据（SST 不可变、编号单调）时全量 SHA-256 是纯完整性加固选择，但其成本缩放无旋钮（无「仅哈希新增/变更 SST」或抽样校验配置）、无耗时 gauge（C8-008 面）。第 1 轮 C8-005 失效面② 曾把 native 路径标注为「合格对照」——该判定对**内存维**成立（不物化进堆），对**时长维不成立**，本条修正之。
- **风险**: 两条已立案发现（C8-005 内存维、C8-010 时长维）给用户的共同修复方向是「开增量」——本条证明该逃生门自身携带同类的未声明时长缩放，且在「状态大到需要 RocksDB」的声明包络内随状态增长必然恶化；与 C8-006 复合（δ 拉长 → Kafka 事务年龄增长）的放大链同 C8-010。
- **建议**: ① 哈希增量化：按（文件名, size, mtime/编号）判等跳过未变更 SST 的重哈希，仅对新增文件全哈希；或提供 `hashPolicy=full|new-only|sampled` 旋钮并声明完整性取舍；② 快照耗时 gauge 并入 C8-008 最小指标集（与 C8-010 建议②同一点位）；③ 与 C8-010 的部署期校验合并（校验对象应含增量路径的哈希时长包络，不能只按全量路径估算）。
- **失效面枚举**: ① `RocksDBIncrementalSnapshotStrategy.doSnapshot` 的逐 SST `sha256Hex`（本条主面）；② `RocksDBSnapshotSerDe.snapshotState` 全量 JSON 路径——内存维 C8-005、时长维 C8-010 已立案（本条的同类前置）；③ `createCheckpoint` 硬链接——廉价有界，合格；④ non-SST 拷贝（MANIFEST/WAL/OPTIONS，KB-MB 级）——有界，合格；⑤ `pruneOldCheckpoints`（K=2 滚动保留，失败 WARN 留痕不中止）——有界且已声明，合格；⑥ `FileTwoPhaseCommitSink.saveState`（tmp+fsync，时长 ∝ epoch 缓冲）——C8-010 失效面② 已并。
- **信心水平**: 确定（全量重哈希为代码直接事实；时长阈值依赖磁盘/页缓存，标注量级估算；「越过 600s 预算」前提同 C8-010 的包络内可达口径）。
- **误报排除**: 非与 C8-005/C8-010 重复——C8-005 失效层是内存物化、C8-010 主面是默认全量路径的时长预算，本条失效层是**增量路径（推荐逃生门）自身的全量重哈希时长**，且修正第 1 轮对 native 路径「合格对照」的误标；非把「完整性哈希」当缺陷——固有的是「需要校验和」，不固有的是「对未变更文件重复全量哈希且无旋钮、无观测、无预算耦合三缺」；非「增量语义不成立」——共享/上传侧语义成立，仅时长维未兑现增量承诺。
- **复核状态**: 已保留（P2 维持，2026-10-14 二轮独立复核）——doSnapshot 对 native 目录全部 SST 无条件 sha256Hex（RocksDBIncrementalSnapshotStrategy.java:88-107）、无（name,size,mtime）跳过逻辑、无 hashPolicy 旋钮逐行复核成立；策略无上一轮哈希缓存（每次新建 cp 目录全量列表）证实「时长 ∝ 总体积而非增量」；调用链 snapshotIncremental（RocksDBKeyedStateBackend.java:840-865）同步在 barrier 内成立；对第 1 轮 C8-005 失效面②「合格对照」的时长维修正采纳（该面内存维合格判定不受影响）。现状：新发现未修复（git log 无修复提交；历史审计无增量哈希时长先例）

---

## 2. 排除项/定向核查

### 2.1 定向核查①：QueueOverflowPolicy / IBlockingQueue 该用未用专项（全量枚举，第 1 轮结论复核 + 扩展）

平台设施确认存在：`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/IBlockingQueue.java`、`QueueOverflowPolicy.java`、`semaphore/ISemaphore.java`、`semaphore/HighWatermarkSemaphore.java`、`ratelimit/IRateLimiter.java`。8 模块全量 grep（BlockingQueue|ConcurrentLinkedQueue|LinkedBlockingDeque|PriorityBlockingQueue|ArrayDeque|LinkedList）+ 人工核对，**完整缓冲区清单**：

| 站点 | 结构 | 有界性/满策略 | 判定 |
|---|---|---|---|
| `MessageSourceReader.queue` | ArrayBlockingQueue(256) | 阻塞 put=背压（声明语义） | 有界合格（字节维缺失→C8-012） |
| `DebeziumCdcSourceReader.queue` | ArrayBlockingQueue(256) | 阻塞 put=背压（声明语义） | 有界合格（字节维缺失→C8-012） |
| `FileSourceReader.assignedSplits` | ArrayDeque | 条数 ≤ 静态发现集（B15） | 合格 |
| `NFA.startingStates`（LinkedList） | 算法 BFS 瞬态 | per-element 局部 | 非缓冲，不适用 |
| `JdbcTwoPhaseCommitSink.currentBuffer` / `FileTwoPhaseCommitSink.currentBuffer` | ArrayList | **无上界** | C8-003 已立案 |
| `TwoPhaseCommitSinkFunction.pendingCommits` | map | epoch 界/失败重试累积 | C8-003 失效面⑤ 已移交 |
| CEP `elementQueueState`+SharedBuffer+timer ledger | keyed state | watermark 停滞无界 | C8-002 已立案（本轮面⑤修正见 2.3） |
| `BatchConsumerSinkFunction.buffer` | ArrayList | batchSize 满即 flush | 合格 |
| `KafkaSourceFunction.restoredOffsets` | TreeMap | ≤ 分区数 | 合格 |
| `FileTwoPhaseCommitSink.MANIFEST_JVM_LOCKS` | CHM | ≤ 输出目录数 | 合格 |

**结论**：不存在「手写有界队列+手工满策略」站点（无任何自研满策略逻辑）；「无界队列当有界用」的站点已全部归并于 C8-002/C8-003；平台 `QueueOverflowPolicy`（DROP/降级策略族）在该用未用意义上的唯一实质落点是 C8-012 的字节界守卫（已并入其建议②）。ISemaphore/HighWatermarkSemaphore 全单元零使用场景（无许可配对语义的边界存在），第 1/2 轮结论复核成立。

### 2.2 定向核查②：Kafka consumer 侧（poll 批积压 / max.poll.records / pause-resume）

- **poll 循环**：`KafkaSourceFunction.run`（:269-292）单线程循环 `poll(200ms)` → 限速 acquire → 逐条 `collectWithTimestamp`（同步阻塞=背压）→ `markConsumed`。任意时刻至多一个 poll 批在本地内存；consumer 客户端预取受 `fetch.max.bytes`（默认 50MB）/`max.partition.fetch.bytes`（默认 1MB/分区）字节界——**有字节界的合格对照**（供 C8-012 引用）。
- **pause/resume 未使用——证伪必要性**：手动 `assign()` 无消费组协调 → 无 rebalance 暂停需求；poll 与 collect 同线程串行，背压期间根本不会再次 poll，客户端 TCP 流控天然反压 fetch。pause/resume 的两个标准动机在本形态均不存在，**不使用是正确的**，零发现。
- **max.poll.records 匹配声明**：client 默认 500、`consumerProps` 可覆盖且无上限校验——第 1 轮 B3「已声明配置面」判定复核成立；结合 fetch 字节界，单批内存双界（条数+字节），无新发现。
- **`markConsumed` 与 collect 的批内时序**：批内第 k 条的 `markConsumed` 在 `collectWithTimestamp` 返回后执行；barrier 邮箱 drain 发生在 collect 内部（第 2 轮 §3 mailbox 事实）→ snapshot 观察到的 offset 至多落后批内已发射位 1 条 = ≤1 条重放形态（at-least-once，无丢失）。第 2 轮同族闭环结论复核成立，无翻案。

### 2.3 定向核查③：CEP watermark 链（C8-002 延伸）——timer ledger 面⑤误判修正 + cache statistics 定性

- **timer ledger（`registeredEventTimeTimersByKey`）**：第 1 轮 C8-002 失效面⑤ 判「消费即删，有界，合格」。本轮核实该判定**仅在 watermark 前提下成立**：`bufferEvent`→`registerTimer` 对每个新缓冲 timestamp 调 `registerEventTimeTimerForKey(currentRegistrationKey(), timestamp)`（`CepOperator.java:917-934` PT 分支与 ET 分支均镜像入账；:984-989 入账），ledger 条目与缓冲的 (key, timestamp) 对 1:1 增长——**C8-002 的 watermark 停滞场景下 ledger 与 elementQueueState 同步无界**。加重面：`snapshotState` 把整个 ledger 逐键物化进 checkpoint（:709-728，`typedCopy` 冻结 + per-key List<Map> 形态），即停滞积压在 OOM 之前先表现为 **checkpoint 体积/时长随积压线性膨胀**（与 C8-010 的固定 600s 预算相撞）。处置：与 C8-002 同根同触发（watermark 停滞），按纪律不另立 ID——**移交复核将其并入 C8-002 失效面⑤（改判「条件有界」）并把 checkpoint 膨胀链补入其触发时序**。
- **cache statistics 队列**：`SharedBuffer` 的两个 Guava 缓存（1024 slots，`recordStats`）为有界本体；stats 消费 = 30 分钟一次 INFO 日志（`NopCepConfigs.CEP_SHARED_BUFFER_ENTRY/EVENT_CACHE_SLOTS` + `statistics-interval`，CepOperator:645-666）——**不存在 stats 队列结构**，唯一信号通道低频日志的定性（C8-002/C8-008）复核成立。
- **`NopCepConfigs` 全量复核**：仅缓存槽位与统计间隔两组旋钮，无任何积压深度/过载行为旋钮——C8-002「无过载旋钮」口径复核成立。

### 2.4 定向核查④：enumerator→reader split 投递积压（file/batch/message/debezium 全量）

| enumerator | 投递模型 | 积压上界 | 判定 |
|---|---|---|---|
| `MessageTopicSplitEnumerator` | 注册/请求即铸 token 直投（:54-62） | 1 token/reader | 合格 |
| `FileSplitEnumerator` | 部署期一次性 `Files.walk` 发现（:142-156，D4 声明持续轮询为 successor 范围）；assign 仅在投递时标记 `assignedFiles`（:189, :215——reader 未注册其份额不丢不重复） | ≤ 发现文件数（split 对象 KB 级） | 合格 |
| `BatchLoaderSplitEnumerator` | 单 split `singletonList` 直投（:56-58） | 1 | 合格 |
| `DebeziumTableSplitEnumerator` | 注册期整份额分配 + 迟到分配 typed 拒绝 | ≤ 表数 | 合格 |

无「enumerator 快于 reader 的无界 split 积压」站点；投递服务（core `DeliveryService`）为越界上下文，未发现模块侧悬空引用。

### 2.5 定向核查⑤：限流器 acquire/release 配对（第 2 轮零发现复核）

`SourceRateLimiter` 消费点仍仅 `FileSourceReader.java:158` 与 `KafkaSourceFunction.java:277`：均为 monitor 外有界分片睡眠、可中断、无 acquire/release 配对语义（token bucket 自回收）→ **许可泄漏面不存在**，第 2 轮结论复核成立。`Lockable`（SharedBuffer）为带 over-release fail-fast 的引用计数，非信号量。本轮零新增。

### 2.6 定向核查⑥：RocksDB 增量路径（C8-013 的伴生合格面）

`createCheckpoint`（JNI 硬链接）廉价有界；non-SST 拷贝 KB-MB 级有界；F-02 滚动保留 K=2 磁盘有界、失败 WARN 留痕（已声明）；共享上传经 SharedStateRegistry（core，越界）。增量路径的**磁盘与内存维**合格，仅时长维立案（C8-013）。

### 2.7 定向核查⑦：message 后端消费线程模型（C8-011 的边界核对）

Kafka 后端消费线程 = **每订阅独立单线程 executor**（`KafkaMessageService.java:424-432`，`Executors.newSingleThreadExecutor` + 命名 daemon factory）→ `onMessage` 内 `queue.put` 阻塞只反压本订阅，无跨订阅/跨 topic 队头阻塞——B1 桥的阻塞 put 语义在 Kafka 后端形态下**合格**（Local 后端的同步派发阻塞生产者线程属 EXPERIMENTAL best-effort 已声明边界）。C8-011 的失效面枚举④据此成立。

---

## 3. 终止声明

### 3.1 累计发现清单（第 1 轮 + 已并入基线的两轮深挖 + 本轮）

| ID | 标题（缩写） | 严重程度 | 来源轮 |
|---|---|---|---|
| C8-001 | Debezium FLIP-27 checkpoint offset 领先已发射记录，恢复静默丢 ≤256 条 | **P1** | 1 |
| C8-002 | CEP 匹配缓冲无上界无观测，watermark 停滞即无界积压 | P2 | 1 |
| C8-003 | JDBC/File 2PC epoch 缓冲 barrier 间无上界 | P2 | 1 |
| C8-004 | Avro registry 每记录热路径无超时 HTTP + 锁内网络调用 | P2 | 1 |
| C8-005 | RocksDB 全量 JSON 快照整库物化进堆 | P2 | 1 |
| C8-006 | Kafka 2PC epoch 事务存活期 == checkpoint 间隔（默认裕度 0） | **P1** | 基线深挖 A |
| C8-007 | JDBC 台账表只增不删（F4 live 实例） | P2 | 基线深挖 A |
| C8-008 | 8 模块过载遥测平面系统性缺失 | P2 | 基线深挖 A |
| C8-009 | JDBC commit 无语句超时且位于 checkpointTimeout 覆盖外（F5 第二等待点） | P2 | 基线深挖 B |
| C8-010 | barrier 内同步写放大时长与 checkpointTimeout 无守卫 | P2 | 基线深挖 B |
| C8-011 | message sink 热路径 send 无超时（F5 第三等待点，旋钮 opt-out 形态） | P2 | **本轮** |
| C8-012 | push→pull 桥只有条数界无字节界，无大小守卫无包络声明 | P2 | **本轮** |
| C8-013 | 增量快照全 SST 重哈希，时长 ∝ 总体积（C8-010 的 F3 兄弟 + 逃生门自噬） | P2 | **本轮** |

累计：**P1×2（C8-001、C8-006）、P2×11（C8-002…C8-005、C8-007…C8-013）**。

### 3.2 终止判定：**C8 维度深挖终止**

依据：
1. **派发指令的全部定向面已闭合**：① QueueOverflowPolicy/IBlockingQueue 该用未用专项——全量枚举完成（§2.1），唯一实质落点已并入 C8-012 建议；② Kafka consumer 侧——poll 批双界、pause/resume 必要性证伪、max.poll.records 复核成立（§2.2）；③ File/batch split 投递积压——四个 enumerator 全部有界（§2.4）；④ CEP watermark 链——timer ledger 修正归并 C8-002、cache statistics 确认无队列结构（§2.3）；⑤ 过载可观测矩阵——C8-008 维持，本轮三条新发现各自携带叠加观测面；⑥ 限流器配对——零发现复核成立（§2.5）。
2. **剩余未覆盖面均为越界或非缺陷**：nop-message 后端内部实现、core/runtime 协议、`_` 前缀生成物；连续三轮的证伪记录（桥 close、Debezium close、BatchLoader offset、max.poll.interval、producer.send 回调、pause/resume、 enumerator 积压）表明剩余候选形态已被逐一排除。
3. **新发现收益率趋势**：本轮 3 条新发现全部来自「结构性盲区」（调用量缺超时的 grep 盲点、字节维缺失、增量路径时长维），此类盲区经本轮补齐后，第 5 轮可审的增量面只剩越界模块与已证伪形态，边际收益判断为低。

移交复核阶段关注点：① **C8-011 与 C8-004/C8-009 的合并口径**——三个「外部依赖无限期等待点」是否按「同族一档（分模块立目）」归并（本轮维持分立，但 C8-011 是唯一热路径形态，判级取向请复核明示）；② **C8-002 失效面⑤ 改判**（§2.3：timer ledger 条件有界 + checkpoint 膨胀链并入）；③ **第 1 轮 C8-005 失效面② 的「合格对照」改判**（时长维不成立，由 C8-013 承接）；④ 第 3 轮 §1「超时面全量盘点」完备性声明需按本轮 §1-C8-011 的 grep 盲点修正回写基线文件（复核阶段执行，本轮不改动已归档文件）；⑤ C8-012 的守卫缺省值与策略（fail-fast vs 降级）需产品口径裁定。

---

## 4. 覆盖声明

- **本轮逐文件/逐面通读**：`MessageSinkFunction`（全）、`MessageSourceReader`（全）、`MessageSourceFunction`（全）、`KafkaSourceFunction`（全 419 行）、`MessageTopicSplitEnumerator`（全）、`FileSplitEnumerator`（全 287 行）、`RocksDBIncrementalSnapshotStrategy`（全 201 行）、`CepOperator` 定向段（L700-830 / L880-1016）、`DebeziumCdcSourceReader` 定向段（L100-145 / L245-285 / L326-336）、`BatchLoaderSplitEnumerator`（投递面）、`FileSourceReader`（守卫面 L60/335）、`AvroSchemaRegistryClient`/`AvroWireFormat`（第 1 轮已全读，本轮仅定性复核）。
- **边界上下文证据（非发现对象）**：`IMessageService`/`IMessageSender`/`MessageSendOptions`/`FutureHelper`/`ContextTaskQueue`/`ResolvedPromise`（nop-api-core）、`KafkaMessageService`/`KafkaConsumeTask`（nop-message-kafka）、`PulsarMessageService`（nop-message-pulsar）、`SysDaoMessageService`（nop-sys-dao）、`LocalMessageService`（定性引用）。
- **grep 手段**：队列/缓冲类型全量枚举、timeout 族、Semaphore/RateLimiter 族、指标 API 族（复核第 2 轮）、`assignSplits`/`requestSplits` 投递面。
- **未覆盖声明**：`nop-message-*`/`nop-sys` 后端实现内部（越界）；nop-stream-core/runtime 的 DeliveryService、SourceReaderOperator、TwoPhaseCommitSinkFunction（越界，沿用前轮引用口径）；8 模块 `_gen/` 与 `_` 前缀生成物（纪律排除）；CEP NFA 数学/模型类（非 C8 相关，第 1 轮已抽查）。
- **盲审纪律确认**：本轮未读取 `ai-dev/audits/` 下除 `C8-backpressure.md` 外的任何文件，未读取 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`，未读取其他维度审计文件；全部结论基于当前 live code + owner docs + 本维度前序轮次文件。
