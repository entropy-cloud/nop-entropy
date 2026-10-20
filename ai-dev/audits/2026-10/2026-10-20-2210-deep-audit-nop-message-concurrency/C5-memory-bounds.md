# C5 内存有界性与大数据量 — nop-message（WI 4.1）

## 审计范围与口径

**单批次 4 源码根覆盖声明**（逐文件审读，非抽查；`_` 前缀生成文件按纪律排除，本模块 main 源码内无 `_` 文件）：

| 源码根 | 审读文件 |
|---|---|
| `nop-message/nop-message-core/src/main/java` | `LocalMessageService`、`MessageMetrics`、`MessageConsumeFailureBridge`、`MessageCoreConstants`、`MessageCoreErrors`、`channel/ChannelBacklogProbe`、`reflection/ReflectionMessageSubscriptionRegistrar` |
| `nop-message/nop-message-kafka/src/main/java` | `KafkaMessageService`、`KafkaConsumeTask`、`KafkaHelper`、`KafkaClientConfig`、`KafkaProducerConfig`、`KafkaConsumerConfig`、`KafkaErrors` |
| `nop-message/nop-message-pulsar/src/main/java` | `PulsarMessageService`、`PulsarConsumeTask`、`PulsarHelper`、`PulsarClientConfig`、`PulsarProducerConfig`、`PulsarConsumerConfig`、`PulsarErrors` |
| `nop-message/nop-message-debezium/src/main/java` | `DebeziumMessageSource`、`DebeziumConfig`、`ChangeEvent`、`ChangeEventMetadata`、`DebeziumConstants`、`DebeziumErrors`、`engine/DebeziumEngineWrapper`、`engine/DebeziumEngineConfig`、`engine/DebeziumEventConverter`、`engine/NopStreamOffsetBackingStore` |

**与 C1-002 的边界声明**：`NopStreamOffsetBackingStore.REGISTRY` 的并发访问面（computeIfAbsent 竞态等）归 C1-002 所有权，本文件不重复立案；本文件只审其**内存/生命周期演化面**（单调增长、清理缺位、驻留倍率），关系显式声明如 C5-001。

**口径纪律声明**：基线 `_tmp/cra-baseline-nop-message.txt` 仅用于圈定重点站点（C5b 节 prefetch/buffer 配置点全部人工复核）；基线 C5a 节 grep 本身失败（对目录执行 wc），全部无界集合判定按口径纪律以逐文件审读人工复现，不引用机器计数。本模块无 owner 文档声明的 GB 级规模包络，验算口径为「单消息有界 × 并发/订阅数 × key 基数 × 时间」。「降档口径：内存有界是行为性缺陷——单线程不豁免」已代入：consume 线程私有的 `failureAttempts` Map 不因线程封闭而豁免无界判定（判定结果见清单第 5/6 行，均为有界，非豁免）。误报校准已代入：客户端默认值有界的项不按无界缺陷报告。

**无界风险清单（路径 × 增长源 × 声明上界 × 判定）**：

| # | 容器/路径 | 增长源 | 声明上界 | 判定 |
|---|---|---|---|---|
| 1 | `NopStreamOffsetBackingStore.REGISTRY`（静态） | JVM 生命期内出现过的 distinct connector 名 × 每连接器分区数 × offset 字节 | 无（无容量、无 TTL、stop 不清） | **发现 C5-001（P2）** |
| 2 | `DebeziumEngineWrapper.FAILURE_LISTENERS`（静态） | distinct connector 名，值是对 owner 的强引用 | 无自动清理，靠调用方手工 unregister | **发现 C5-002（P3）** |
| 3 | `LocalMessageService.consumers` | 订阅过的 distinct topic（cancel 只删列表元素，topic 键与空列表永不移除） | 主题空间（通常配置界） | 清单登记：应用侧有界；动态 topic 场景慢增长 |
| 4 | `LocalMessageService` redelivery 调度队列（`DelayedWorkQueue` 无界） | 返回 `ConsumeLater` 后未决的消息对象 | 无（无重投上限、无 DLQ、无 pending 指标） | **发现 C5-003（P2）** |
| 5 | `KafkaConsumeTask.failureAttempts` | distinct 失败 location | 失败重试指针钉住同一 offset → 实际 ≤ 分区数；`maxConsumeAttempts=0`（文档化选项）时条目仅在成功/endpoint 移除，仍受分区数约束 | 有界（单线程封闭不豁免口径下仍有限） |
| 6 | `PulsarConsumeTask.failureAttempts` | distinct messageId | ≤ nack 在途重投窗口（受客户端 receiver 队列约束） | 有界 |
| 7 | Kafka `poll→batchConsume` 批量物化（`list`/`recordList`/responses） | 单轮 poll 记录数 | `maxPollRecords` 默认 500（可配），× 单消息大小；`fetch.max.bytes` 客户端默认兜底 | 有界（配置声明） |
| 8 | Pulsar `batchReceive → List<TopicMessage>` 物化 | 单批消息数 | `batchReceiveCount>0` 时 `maxNumMessages`+默认 maxNumBytes+100ms；否则客户端默认 policy | 有界，但 receiverQueueSize/批上限无模块配置面 → **C5-004（P3）** |
| 9 | `PulsarMessageService.producers` / `topicSchemas` | distinct topic | 主题空间（应用侧） | 有界（应用侧） |
| 10 | `MessageMetrics`（观测集合） | 计数器名按 backend 固定（`nop.message.<backend>.*`），不按 topic 分片 | backend 数 | 有界 |
| 11 | `ChannelBacklogProbe`（观测集合） | 接口无状态；实现方（nop-sys-dao `nop_sys_event` 表）在模块外 | — | 本模块无观测集合累积面 |
| 12 | Debezium 引擎内部事件队列 | 引擎预取 | Debezium 默认 `max.queue.size=8192` / `max.batch.size=2048`（`extraProperties` 可透传调整） | 有界（第三方默认声明） |
| 13 | `DebeziumEventConverter.convert` 单事件 JSON 全量 Map 化（before+after+source） | 瞬态，单事件 ~2–3x 放大，不跨事件驻留 | 单事件大小 | 有界（流式逐事件，非批量物化） |
| 14 | Kafka producer `buffer.memory=32MB` + `max.block.ms`；Pulsar producer `batchingMaxMessages=1000` | 发送积压 | 配置声明 | 有界 |
| 15 | Kafka/Pulsar service `subscriptions` 队列 | 订阅数 | 应用侧；cancel 移除 | 有界 |
| 16 | `MessageOptionsValidator.WARNED`（平台设施，只审使用） | backend+option 键空间 | 枚举级 | 有界 |

## 发现

### [C5-001] REGISTRY 静态 offset 账本单调增长：stop 不回收、无容量、独立嵌入路径无任何清理入口

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/NopStreamOffsetBackingStore.java:59-60, 101-103, 130-139`；关联 `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java:168-180`
- **证据片段**:
  ```java
  // NopStreamOffsetBackingStore.java:59
  private static final ConcurrentHashMap<String, ConcurrentHashMap<ByteBuffer, ByteBuffer>> REGISTRY =
          new ConcurrentHashMap<>();
  // :101 唯一的移除入口
  public static void clearConnector(String connectorName) {
      REGISTRY.remove(connectorName);
  }
  // :136 stop 只翻转布尔，账本条目原样驻留
  public void stop() {
      started = false;
  ```
- **严重程度**: P2
- **并发场景**: 嵌入式 Debezium 引擎 worker 线程（`GlobalExecutors.globalWorker()`）执行 offset `set()` 写入；nop-stream checkpoint 线程经 `getOffsets()` 快照读取；作业（re）部署线程执行 `forConnector`/`clearConnector`。增长本身是跨时序的账本演化，非单点竞态。
- **触发时序**: T1：CDC 作业 A 以连接器名 `cdc-1` 运行后停止（`DebeziumCdcSourceFunction` 仅在 first-run 路径调 `clearConnector`，见 `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:442`；`stop()` 永不清）→ `REGISTRY["cdc-1"]` 整体驻留（分区 offset map + ByteBuffer 拷贝）。T2：长运行 JVM 内按部署轮换连接器名（`cdc-2…cdc-N`，或独立使用 `DebeziumMessageSource(config, offsetStore)` 的嵌入方——该路径从构造到 `stop()` 没有任何一行代码触及 `clearConnector`）→ 条目只增不删，堆驻留随 distinct 连接器名线性增长，无上限、无观测指标。
- **现状**: 每条目负载 = 该连接器源分区数 × offset 值字节数（通常数百字节级），单条目小；缺陷在**账本无界**：无容量声明、无 TTL/淘汰、stop 语义（`started=false`）与回收语义完全脱钩、且 `clearConnector` 的调用契约只存在于 nop-stream source function 的 first-run 分支——模块自身的另一条使用路径（`DebeziumMessageSource`）没有等价清理。
- **风险**: 长运行应用内反复（重）部署 CDC 作业且命名含时间戳/版本后缀时，静态账本单调增长（F4 族轻量形态）；与 C1-002 立案的并发面叠加时，同一张表既是竞态现场又是泄漏现场。不会在固定名单场景 OOM——判 P2 而非 P1 的依据：增长源是 distinct 连接器名，键空间通常由部署纪律决定。
- **建议**: `DebeziumEngineWrapper.stop()`/`DebeziumMessageSource.stop()` 收尾时对非恢复场景调用 `clearConnector(name)`（或引入 `NopStreamOffsetBackingStore.evictIdle(ttl)` 由 stop 路径显式声明「本连接器不再恢复」）；复用平台 `io.nop.commons.cache.CacheConfig` 有上限缓存承载 connector→data 映射；为 REGISTRY 增加驻留条目数的诊断指标。
- **失效面枚举**（F3 同族清扫）: ① `REGISTRY`——本条立案；② `DebeziumEngineWrapper.FAILURE_LISTENERS`——C5-002 立案；③ `LocalMessageService.consumers`（静态性弱于前两者，实例级，topic 键不移除）——清单第 3 行登记，未达立案阈值；④ `MessageOptionsValidator.WARNED`——键空间枚举级，有界，不立案；⑤ `GlobalExecutors.g_executors`——平台设施，按口径不重审。
- **信心水平**: 确定（无界性与清理缺位由代码直接可证）
- **误报排除**: 不属于「有界声明存在的缓存/队列」误报项——REGISTRY 没有任何上界声明或配置；也不与 C1-002 重复：本条证据与后果均为内存/生命周期面。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（REGISTRY :59-60/唯一 remove :101-103/stop 仅翻 started :136-139 逐点回码确认；nop-stream 清理仅 first-run 路径实测（DebeziumCdcSourceFunction.java:400/:413→:440-444），restore 分支与 DebeziumMessageSource 路径零 clearConnector 复现；三面分层成立——本条承接 C1-002 收窄移交的「无界账本」内存演化面，与 C6-004 清理路径面同根同修，须同 diff 落地）

### [C5-002] FAILURE_LISTENERS 静态注册表与 stop() 生命周期不对称：stop 不注销，泄漏 face 依赖调用方纪律

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:44, 52-62, 67-71, 195-215`
- **证据片段**:
  ```java
  // :44 值是对 owner（lambda 捕获 this/字段）的强引用
  private static final Map<String, Consumer<Throwable>> FAILURE_LISTENERS = new ConcurrentHashMap<>();
  // :200-214 stop() 关闭 engine，但不触碰 FAILURE_LISTENERS
  if (!startRequested.get() || stopped.getAndSet(true)) {
      return;
  }
  ...
  engine.close();
  ...
  running.set(false);
  ```
- **严重程度**: P3
- **并发场景**: 引擎 worker 线程 `notifyEngineFailure` 读取；owner 任务线程 register/unregister；静态 Map 跨引擎生命周期共享。
- **触发时序**: T1：owner 调 `registerFailureListener(name, listener)`（listener 捕获 owner 任务对象）→ 若 owner 的清理路径未调 `unregisterFailureListener`（模块的 `stop()` 不会代劳，javadoc 仅以「owners must deregister」文字约定）→ T2：owner 任务已结束/重部署，静态表仍持有其强引用，连同捕获的 `pendingError`/latch 等字段永久驻留；换名重注册场景下旧条目不可达且不可枚举清理。
- **现状**: 当前唯一生产调用方 `DebeziumCdcSourceFunction` 在 `finally` 中注销（`DebeziumCdcSourceFunction.java:248/298`），现状无活泄漏；缺陷形态是 **register/stop 生命周期不对称**（F8）：start 的自然配对面 stop 不承担注销，契约仅存在于 javadoc 文字，无守卫、无泄漏检测、无 `stop()` 兜底注销。
- **风险**: 任何后续嵌入方按「start/stop」直觉使用该 API 即复现静态强引用泄漏；同 key 重复注册会静默顶替旧 listener，换 key 注册则累积。因现状无活泄漏、增长依赖未来误用，判 P3（API 形状/防御缺位），非 P2 活缺陷。
- **建议**: `stop()` 内补 `unregisterFailureListener(config.getName())` 兜底（或提供 `registerFailureListener` 返回 auto-close 句柄）；与 C5-001 一并纳入「静态表必须有 stop 配对回收」的模块约定并写入 owner 文档。
- **失效面枚举**: ① `FAILURE_LISTENERS`——本条；② `REGISTRY`——C5-001（其清理入口 `clearConnector` 同样不在 stop 配对面内）；③ nop-stream 侧 `DebeziumCdcSourceFunction` 注册点——已核查 finally 注销合规（`DebeziumCdcSourceFunction.java:298`），无漏修兄弟。
- **信心水平**: 确定（不对称是事实）；泄漏后果为条件性（依赖调用方违约）
- **误报排除**: 不按「已声明边界」豁免——javadoc 的文字要求是施加给调用方的义务而非 fail-fast 守卫；守卫缺失、依赖自觉正是本领域缺陷定义（F10/F8 口径）。因现状调用方合规，降档 P3 而非 P2。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜现状:新发现（现状无活泄漏——唯一生产调用方 finally 注销（DebeziumCdcSourceFunction.java:248/:298）实测确认；stop() :195-215 不触 FAILURE_LISTENERS、javadoc :50「owners must deregister」为义务性文字非守卫，均成立。与 C1-001（同表的替换/无条件 remove 竞态面，已裁 P2 与 WI 1.2 C3-006 双轨）为同表两面：本条=泄漏/生命周期不对称面，修复同批落地）

### [C5-003] LocalMessageService ConsumeLater 重投：未决消息堆驻留无上界（无重投上限/无 DLQ/无 pending 观测），delay=0 热循环独占共享重投线程

- **文件**: `nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java:216-219, 250-270, 280-295`
- **证据片段**:
  ```java
  // :218-219 ConsumeLater → 延迟重投，无任何计数上限
  if (ret instanceof ConsumeLater) {
      scheduleRedelivery(subscription, message, ((ConsumeLater) ret).getDelay());
  // :250-263 消息对象被调度任务持有直至触发；delay 下限 0
  private void scheduleRedelivery(Subscription subscription, Object message, long delayMs) {
      long delay = Math.max(delayMs, 0L);
      try {
          redeliveryScheduler().schedule(() -> {
  // :285 全服务共享单线程调度器，DelayedWorkQueue 无界
  redeliveryExecutor = Executors.newSingleThreadScheduledExecutor(...)
  ```
- **严重程度**: P2
- **并发场景**: 业务线程调 `send` 同步分发 → listener 返回 `ConsumeLater` → 单线程 daemon 调度器（`nop-local-message-redelivery`）持有消息对象并按延迟触发重投；所有 topic 的重投共享同一线程。
- **触发时序**: T1：消费端 handler 对持续到达的消息流返回 `ConsumeLater(delay)`（API 首类返回值语义，delay≥0 合法）且条件长期不满足 → 每条消息以 `ScheduledFutureTask` 形式驻留无界 `DelayedWorkQueue`，消息体（含完整 payload）在堆上按「到达率 − 解决率」随时间线性累积，无上限、无重投次数上限、无 DLQ、无 pending 深度指标。T2（放大形态）：handler 返回 `ConsumeLater(0)` → `schedule(0)` 即刻触发 → `invokeSubscription` → 再次 `ConsumeLater(0)` → 单线程调度器进入零延迟自旋，该 topic 的毒消息独占线程，**全部其他 topic 的延迟重投饿死**，同时消息永久不离开堆。
- **现状**: Kafka/Pulsar 的同语义路径把延迟消息还给了 broker（`seek`+`deferAfterBatch` 睡眠 / `reconsumeLaterAsync`），堆驻留有界；Local 是唯一把未决积压落在 JVM 堆上的后端，且是三后端中唯一没有失败上限/死信/积压观测的路径。失败（异常）路径同样无上限，但失败消息由 broker（K/P）或调用方（Local 的同步 send 链）持有，不在本条范围。
- **风险**: 慢消费者语义下内存放大无上界（F4/F7 族）；delay=0 形态附加 CPU 自旋与跨 topic 重投饥饿。触发依赖 handler 使用 ConsumeLater 语义（平台不能排除的一等用法），增长速率=持续到达流 → 判 P2（条件触发的长时运行无界增长），不到 P0/P1。
- **建议**: 为 Local 增加每 subscription 重投上限（对齐 Kafka `maxConsumeAttempts`）+ 上限到达后的 typed WARN/丢弃证明；`ConsumeLater` 待决深度接入 `MessageMetrics`（如 `nop.message.local.redelivery.pending` Gauge）；delay=0 时按最小间隔（如 100ms）下限节流，防止自旋；评估复用平台 `IBlockingQueue`+`QueueOverflowPolicy` 承载待决队列以获得显式上界。
- **失效面枚举**（ConsumeLater 未决消息堆积的同族后端逐一登记）: ① Local `scheduleRedelivery`——本条（堆驻留无界）；② Kafka `pollAndConsume` ConsumeLater 分支（`KafkaConsumeTask.java:360-369`，seek+`deferAfterBatch`）——堆有界，但 delay=0 同样构成消费线程自旋（CPU 面，属其他维度，登记不立案）；③ Kafka `batchConsume` ConsumeLater 分支（`:529-535`）——同 ②；④ Pulsar `consume`/`batchConsume` 的 `reconsumeLater`（`PulsarConsumeTask.java:336-338, 272-275`）——broker 侧持有，堆有界；无堆面缺陷。
- **信心水平**: 确定（驻留机制由代码直接可证；增长率依赖负载画像）
- **误报排除**: 非通用「队列/缓存有上限」误报项——该队列（`DelayedWorkQueue`）无任何上界或拒绝策略；也非「单线程即安全」可豁免的行为——降档口径明示单线程不豁免内存有界性判定。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜现状:新发现（:218-219 无计数上限、:250-263 任务持消息强引用、:280-295 单线程 DelayedWorkQueue 无界、delay 下限 0 全部回码确认；Kafka/Pulsar 同语义路径还消息于 broker（:367-368 seek+defer / :338 reconsumeLater）实测成立。与 C8-003（同一 executor 的执行拓扑/全局串行面）、C6-001（同一 executor 的生命周期面）三面同体，修复须一个 diff 批次；delay=0 自旋面归本条（C8-005 失效面 ⑤ 的「无纯自旋」判定偏宽，见 C8 卷复核注记））

### [C5-004] Pulsar 预取上界无模块配置面：receiverQueueSize/batchReceive 批上限仅靠客户端缺省值，堆峰值验算不可控

- **文件**: `nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarConsumerConfig.java:13-119`（全类无 receiverQueueSize 字段）；`nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarMessageService.java:426-492`（builder 装配全程未调 `receiverQueueSize`/`maxTotalReceiverQueueSizeAcrossPartitions`）、`451-457`（batchReceivePolicy 仅在 `batchReceiveCount>0` 时设置）
- **证据片段**:
  ```java
  // PulsarMessageService.doSubscribe：仅 subscriptionName/type/seek/batchReceivePolicy/
  // ackTimeout/negativeAckRedeliveryDelay/deadLetterPolicy 有装配，prefetch 无装配点
  builder.topic(subConfig.getTopic());
  ...
  if (options != null && options.getBatchReceiveCount() > 0) {
      builder.batchReceivePolicy(BatchReceivePolicy.builder()
              .maxNumMessages(options.getBatchReceiveCount()) ...
  ```
- **严重程度**: P3
- **并发场景**: 每订阅单消费线程（`nop-msg-pulsar-consume-*`）+ Pulsar 客户端内部 IO 线程向 receiver 队列预取；堆峰值 = receiverQueueSize × 单消息大小 × (订阅数 × concurrency)。
- **触发时序**: T1：部署按 Kafka 经验配置消费端——`PulsarConsumerConfig` 找不到与 `maxPollRecords` 对称的预取字段，客户端按缺省 `receiverQueueSize=1000` 每消费者预取 → T2：大消息（Pulsar 单消息上限默认 5MB）或高 concurrency（`LOAD_BALANCED` N 消费者各持独立队列）场景，单订阅堆峰值最坏 1000×5MB≈5GB 量级、常规 64KB 消息亦达 64MB/消费者 × N，且应用无任何配置点收窄该上界；批路径 `batchReceiveCount=0` 时批大小同样完全由客户端默认 policy 决定。
- **现状**: 上界**存在**（客户端缺省值兜底，故非无界缺陷、非 P2），但模块配置面缺对肿瘤旋钮：Kafka 侧 `maxPollRecords` 可配（`KafkaConsumerConfig.java:33`），Pulsar 侧无对称字段；design 3.10 仅声明了 batchReceiveCount 语义的不对称，未声明 prefetch 旋钮缺位。
- **风险**: 峰值叠加（C5 步骤 5：单消费者有界 × concurrency × 订阅数）超出堆预算时无调优入口，只能整体改客户端缺省或换部署形态；与 C5-003 对照形成「三后端预取可配置性不一致」的运维盲区。
- **建议**: `PulsarConsumerConfig` 增加 `receiverQueueSize`（映射 `ConsumerBuilder.receiverQueueSize`），并在批路径缺省时显式设置 `BatchReceivePolicy`（maxNumBytes/timeout 已有缺省声明可复用）；文档补记「Pulsar 预取缺省 1000/消费者」的堆验算公式。
- **失效面枚举**: ① Pulsar 消费预取——本条；② Pulsar 生产端 `batchingMaxMessages=1000`（`PulsarProducerConfig`）——有界且可配，不立案；③ Kafka `maxPollRecords`——有界可配，不立案；④ Debezium 引擎队列（`max.queue.size` 等可经 `extraProperties` 透传）——有界可配，不立案。
- **信心水平**: 确定（配置面缺位直接可证；最坏堆值依赖消息大小画像）
- **误报排除**: 客户端缺省值构成事实上界，按误报校准不报「无界」；本条以「有界但边界配置缺陷/配置面缺位」立项，与校准条款的例外分支（上限配置与使用方规模声明矛盾/不可控）一致。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜现状:新发现（全模块 grep `receiverQueueSize|maxTotalReceiverQueueSizeAcrossPartitions` = 0 命中独立复现；doSubscribe :426-492 装配面无 prefetch 旋钮、batchReceivePolicy 仅 `batchReceiveCount>0` 分支（:451-457）确认；对照 KafkaConsumerConfig.maxPollRecords 在位的非对称成立。校准例外分支（有界但配置面缺位）适用正确，P3 与 C5-005 定级逻辑对齐）

## 维度集外线索（overlay C2/C4/C7/C9）

### [nop-message-CL-009] [C4] LocalMessageService 重投调度器只建不关，无线程生命周期终点

`LocalMessageService.java:280-295`：懒创建的单线程 daemon 调度器无任何 shutdown 路径——`LocalMessageService` 无 stop/destroy 方法（对比 Kafka/Pulsar service 的 LifeCycleSupport），每个服务实例永久占用一条 `nop-local-message-redelivery` 线程；IoC 重复创建/销毁容器场景（热部署、多容器测试）逐实例累积。建议随 C5-003 一并补 `destroy()`（`shutdownNow` + RejectedExecutionException 已有 WI26 兜底）。≥5 行成立。

### [nop-message-CL-010] [C4] Debezium 引擎以「终身阻塞」任务占用全局 worker 池，缺省池容 30

`DebeziumEngineWrapper.java:159-173`：`GlobalExecutors.globalWorker().execute(() -> engine.run())`——`run()` 阻塞至引擎关闭，即每个引擎实例终身占满 1 个全局 worker；`GlobalExecutors.java:85-93` + `CommonConfigs.java:26`：`nop-global-worker` `maxPoolSize` 缺省 30。并发引擎数逼近 30 时，共享池的其他全局任务（平台各处 globalWorker 用户）被 CDC 引擎挤占饿死。建议引擎改用独立 `IThreadPoolExecutor`（`ThreadPoolConfig` 可配）或专用线程工厂。≥5 行成立。

### [nop-message-CL-011] [C9] DebeziumMessageSource 路径无引擎终结失败监听：引擎死亡后 started 仍为 true，CDC 流静默死亡

`DebeziumMessageSource.java:137-153` 创建 `DebeziumEngineWrapper` 后从不调 `registerFailureListener`；引擎 `run()` 抛异常时仅 `DebeziumEngineWrapper.java:165` ERROR 日志 + `notifyEngineFailure` 空转（无监听者即 return）。后果：`DebeziumMessageSource.started` 保持 true、`isStarted()` 报运行中、subscriptions 仍挂接，事件从此不再派发——恰是 wrapper javadoc（`:34-43`）描述并已为 nop-stream 路径修复（`DebeziumCdcSourceFunction.java:248`）的 R5-CON-03 失效面，但独立嵌入路径缺同款修复（F3 同族漏修现场）。≥5 行成立。

### [nop-message-CL-012] [C9] DebeziumEventConverter.parseKey 对 null key 无守卫：单个 keyless 事件可击穿引擎 worker

`DebeziumEventConverter.java:112-113`：`new String(keyBytes, UTF_8)` 在 `event.key()==null` 时 NPE；`convert()` 由 `DebeziumEngineWrapper.handleDebeziumEvent`（`:185-190`）在引擎 worker 内调用且不捕获 → 异常穿出 `engine.run()`（`:163`）→ 引擎终结。结合 CL-011，独立嵌入路径该死亡是静默的。触发面较窄（事务元数据等 keyless 事件、默认关闭），信心水平：有趣的猜测（未实测 Debezium 2.4 是否保证非空 key）；建议 convert 前置 null 守卫并走 typed 错误。≥5 行成立。

### [nop-message-CL-013] [C9] DebeziumEngineWrapper 一次性生命周期：stop 后 start 静默 no-op 且日志误导

`DebeziumEngineWrapper.java:135-143`（start 门禁 `startRequested`，置位不复位）+ `:200`（stop 置 `stopped` 永不复位）：对同一 wrapper 实例 stop→start 时，`start()` 命中 `startRequested.get()` 走 `:137` 仅 WARN "already running" 后正常返回——调用方拿到「启动成功」的假象而引擎并未运行。`DebeziumMessageSource` 因每次重建 wrapper 实例而幸免，但 wrapper 是 public API，stop/start 循环复用是自然用法。≥5 行成立。

---
（本维度 C5 共 4 条发现：P0×0 / P1×0 / P2×2 / P3×2；集外线索 5 条：CL-009…CL-013。所有 file:line 以 2026-10-20 live code 为准，相对路径根为仓库根。）

## 维度复核结论（WI 4.1 独立复核，2026-10-20）

复核人（独立子代理，未参与初审/深挖，与 C1/C3 复核人互异）对 C5 全部 6 条（第 1 轮 4 条 + 第 2 轮 2 条）逐条回码验证：证据行号逐一比对（`NopStreamOffsetBackingStore.java` 全文、`DebeziumEngineWrapper.java` 全文、`DebeziumEngineConfig.java:45-177`、`LocalMessageService.java` 全文、`PulsarMessageService.java:210-245/:426-492`、`KafkaProducerConfig/KafkaConsumerConfig` 全文、`DebeziumConfig.java:90-120`），配置面缺位类主张以全模块 grep 独立复现（`receiverQueueSize|maxPendingMessages|blockIfQueueFull` = 0 命中），跨源核验 `DebeziumCdcSourceFunction.java:220-222/:248/:298/:395-444`；严重度对照 `ai-dev/skills/concurrency-resource-audit-prompt.md` 校准表，并对照 `ai-dev/audits/cra-index.md`（WI 1.1–3.4 台账）与 WI 1.2/WI 2.5 历史审计做同案查重。

### 逐条判定表

| 条目 | 初判 | 判定 | 最终严重度 | 现状标注 | 关键复核依据 |
|---|---|---|---|---|---|
| C5-001 | P2 | 保留 | P2 | 新发现 | 无界账本+清理缺位逐点回码；三面分层成立（承接 C1-002 收窄移交的内存演化面；清理调用点面归 C6-004、并发面归 C1-002、消费面归 WI 2.5） |
| C5-002 | P3 | 保留 | P3 | 新发现（现状无活泄漏） | stop 不触表+javadoc 义务性文字成立；唯一调用方 finally 注销实测，条件性泄漏判 P3 正确 |
| C5-003 | P2 | 保留 | P2 | 新发现 | 无界 DelayedWorkQueue+消息强引用+delay 下限 0 回码；三后端对照成立；与 C6-001/C8-003 同体三面、一个 diff 批次 |
| C5-004 | P3 | 保留 | P3 | 新发现 | 配置面缺位 grep 独立复现；校准例外分支适用正确 |
| C5-005 | P3 | 保留 | P3 | 新发现 | 与 C5-004 的 F3 对称成立；PulsarProducerConfig 仅 3 字段（:14-16，finding 引 :17-41 行号偏移不影响实质） |
| C5-006 | P3 | 保留 | P3 | 新发现 | extraProperties 仅钉 schema.history.internal.*/offset.storage.* 前缀（:165-175）回码；max.queue.size 族可上调、无字节上界缺省成立；「很可能」信心自评恰当（字节驻留倍数依赖第三方实现） |

**判定统计**：保留 6、降级 0、驳回 0。
**最终严重度分布**：P2×2 / P3×4（与初审+深挖合计一致，无校准变动）。
**现状标注分布**：新发现×6（本单元无前序 CRA 覆盖；WI 2.5 CL-010 为台账 watch 非工单，不构成前案）。

### 跨单元对照（cra-index P1 台账逐条比对）

1. **REGISTRY 三面分层表态（C5/C6 侧）**：成立。C1-002（并发摘除竞态，已裁）/ C5-001（内存演化，本条）/ C6-004（清理调用点+stale-resume 语义）+ WI 2.5 消费面（CL-010 watch/C3-008 键错位）四方互斥。**附加纪律注记**：C5-001 与 C6-004 共享同一根因（生命周期路径零 clearConnector）与同一主修复（stop 侧配对清理）——两单必须同 diff 落地，且修复时须一次性裁决「stop 清理 vs 同名重启续传」的契约取舍（C5-001 的 evictIdle/TTL 方案与 C6-004 的 stop-clear 方案对该契约影响相反，不得两单各修一半）。
2. **WI 1.2 同族**：C4-004/MA-06（引擎常驻 globalWorker）与 C3-014/MA-07（停止契约）为 wrapper 的 nop-stream 使用面，本卷未立案对应条目（CL-010/CL-013 集外线索已分导 C4/C6），无重复。
3. **清单行 5/6（failureAttempts 有界判定）精度注记**：行 5/6 的「有界」结论在缺省 `maxConsumeAttempts=10` 下成立（endpoint 触发即删，`KafkaConsumeTask.java:673`/`PulsarConsumeTask.java:423`）；文档化选项 cap=0（unlimited）下 Pulsar 侧退化为只增（C6 卷 CL-017 立案面），Kafka 侧因 seek 钉住同 offset 键仍有界——两行应与 CL-017 合读，不影响本卷任何立案。
4. **其余各条**在 WI 1.1–3.4 台账无同站点/同案命中：三后端预取/批缓冲配置面、Local 重投队列均为本单元独有审计面。

## 最终保留项

- **保留工单 6 条**（零驳回、零降级）：C5-001…C5-006 全部成立，P2×2（C5-001/C5-003）/ P3×4。
- **同 diff 修复批建议**：① REGISTRY 生命周期族——C5-001 + C6-004（+ C1-002 条件移除面），一次性裁决 stop-clear vs TTL 与续传契约；② Local 重投 executor 族——C5-003 + C6-001 + C8-003（同一 executor 的内存/生命周期/拓扑三面），上限+destroy+有界工作队列一并落地；③ 配置面对称族——C5-004 + C5-005（receiverQueueSize/maxPendingMessages/blockIfQueueFull 字段补齐与验算公式同批）；④ C5-006（引擎队列字节上界缺省+钉定族扩展）独立小 diff。
