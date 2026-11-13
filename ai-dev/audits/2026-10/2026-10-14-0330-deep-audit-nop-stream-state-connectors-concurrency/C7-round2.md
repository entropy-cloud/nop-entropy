# C7 长时运行稳定性 — 深挖第 2 轮（完整轮次交付物）

- **审计编号**: nop-stream-state-connectors-C7（第 2 轮）
- **前序**: `C7-long-running.md`（第 1 轮：15 项单调增长源 G1–G15 + 10 项挂死点 H1–H10 → C7-001..009）
- **合并续写说明**: 本文件目标路径在先前轮次不存在，但前轮文件 `C7-long-running.md` 末尾已含一次**先前中断的第 2 轮追加段**（发现 C7-010、C7-011 + 四轴盲区核查①metrics 基数/②自愈旧状态/③重试退避/④时钟依赖 + 终止信号）。按合并续写纪律：该内容保留于前轮文件原文不动（本文件不复制、不覆盖），编号沿用，**本轮新发现从 C7-012 起**。本轮派发指令的定向方向（日志放大专项、挂死可检测性矩阵、句柄/连接收敛、重试叠加、未读段抽查）与该追加段的四轴不重叠，为本轮新增覆盖面。
- **审计日期**: 2026-10-14
- **盲审边界**: 本轮仅读取前轮文件（唯一豁免的审计目录文件）+ live code + owner docs（`docs-for-ai/03-modules/nop-stream.md`、`nop-stream-connectors.md`）+ grep 基线 `_tmp/cra-baseline-nop-stream-state-connectors.txt`；未读 ai-dev/plans、bugs、lessons 及审计目录中其他维度文件
- **审计单元**: 8 模块 `src/main/java`（140 files / 25,458 lines，与基线一致）；`_` 前缀生成文件未作为发现对象

## 本轮结论（终止信号）

新增发现 2 条（C7-012 P2、C7-013 P3）。派发的六个深挖方向全部完成定向核查：日志放大专项产出 C7-012（唯一热路径/采集路径日志缺陷，其余全部有界）；avro 资源生命周期复检产出 C7-013；其余四方向（挂死矩阵补充、自愈句柄/连接收敛、重试叠加、metrics 基数复核）+ 第 1 轮 grep-only 未读段抽查均为干净排除（证据见第 2 节）。第 2 轮（含先前中断段）之后本维度剩余未覆盖面为零散低价值项，**建议终止本维度深挖迭代**。

---

## 1. 本轮发现

### [nop-stream-state-connectors-C7-012] RocksDB state 层 gauge 读数失败的 WARN 无一次性守卫，按 scrape 频率无限期放大——owner doc 声明「告警一次」与实现不符（日志放大 + 文档-代码分歧）

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:26-28`（javadoc 声明）、`:82-84`（gauge 注册，value function 绑定 readProperty）、`:91-101`（WARN 无守卫）；owner doc 声明点 `docs-for-ai/03-modules/nop-stream.md:90`（「个别属性在特定 RocksDB 构建下不可用时该 gauge 值为 NaN 并**告警一次**」）
- **证据片段**:
  ```java
  // javadoc（:26-28）：自认失败是持久性的（"availability varies by version"），
  // 且声称 one-shot —— 但类中不存在任何一次性守卫字段：
  // A read failure of a property surfaces as an absent gauge value (NaN) plus a
  // one-shot warn log — RocksDB property availability varies by version, ...

  private void gauge(MeterRegistry registry, String meterName, String property) {
      registry.gauge(meterName, Tags.empty(), dbRef, ref -> readProperty(ref.get(), property));
  }                                                            // value function = 每次 scrape 执行

  public synchronized double readProperty(RocksDB db, String property) {
      if (db == null) { return Double.NaN; }
      try {
          return db.getAggregatedLongProperty(property);
      } catch (RocksDBException e) {
          LOG.warn("Failed to read RocksDB property {} for metrics gauge: {}", property, e.toString());
          return Double.NaN;                                   // 无 once-flag、无限频、无去重
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: Prometheus/运维端点 scrape 线程（或周期 metrics log sink）每次抓取调用 gauge value function → `readProperty`；与 task 生命周期线程仅经 `synchronized`/`dbRef` 交互（该面本身正确）。缺陷面是**日志生命周期**：属性读取失败是 RocksDB 构建特征（确定性、持久性），非瞬态故障，故 WARN 频率 = scrape 频率 × 失败属性数，无任何衰减
- **触发时序/增长链**: T1 作业使用 RocksDB 后端打开（`RocksDBKeyedStateBackend.openDB:278-279` 注册六 gauge）→ T2 所用 RocksDB 构建缺少某个 aggregated property（javadoc 自认「varies by version」的持久性现象）→ T3 每次 scrape（典型 15–60s 周期）该 gauge 的 value function 抛 RocksDBException → 一条 WARN。**时间尺度 × 增长源 × 后果**：15s scrape 间隔 × 1 个失败属性 ≈ 5,760 条 WARN/小时、13.8 万条/天，自 backend open 起持续到该 backend close（天/周级 run 内即无限期）；6 个属性全部不可用时 ×6。日志被采集路径噪声淹没，真实 ERROR/WARN 信号被稀释；磁盘/日志管道按纯噪声速率消耗
- **现状**: 类内无 `warnOnce` 标志、无比特集、无窗口限频——javadoc 的「one-shot-style warn」是声明而非实现；owner doc 指标名表（state 层）同样声明「告警一次」。与 C7-004 叠加后行为更糟：backend 重建后 Micrometer 去重返回旧 recorder 的死 gauge（dbRef=null）→ `readProperty` 走 `db == null` 短路返回 NaN——WARN 反而**停止**，六个指标从此静默 NaN（C7-004 的失明态），即「先刷屏、后失明」的组合
- **风险**: 长时运行监控面上：① 失明前窗口的日志放大（纯噪声、无抑制）；② 文档-代码分歧使运维按「告警一次」预期配置告警/日志告警规则而实际行为不符；③ 与 C7-004 组合后整个 state 层观测面在一次重建后归零且无信号
- **建议**: `readProperty` 加一次性守卫（如 `Set<String> warnOnceProps`（ConcurrentHashMap.newKeySet）按 property 名去重，或 per-recorder `AtomicBoolean`），命中后降为 debug；同时把「一次性告警」承诺与 C7-004 的 gauge 失明面一并修复（per-backend 标签或 remove+重注册）
- **失效面枚举**（8 模块全部日志调用逐一分类，见第 2 节第 1 项的完整盘点）: ① 本体（RocksDBMetricsRecorder，唯一绑定 scrape 频率的 WARN）→ 本发现；② `SharedBuffer.logCacheStatistics`（INFO per 定时器 tick，默认 30 分钟，`NopCepConfigs.CEP_CACHE_STATISTICS_INTERVAL`）→ 设计即周期观测、有界 → 非缺陷；③ `CepOperator:803` legacy 台账键 WARN → 仅恢复路径、仅 legacy checkpoint、每恢复一次有界 → 非缺陷；④ file/jdbc 恢复重提交 WARN（`FileTwoPhaseCommitSink:314,343`、`JdbcTwoPhaseCommitSink:685`）→ 每恢复 × pending 数有界 × 引擎 restart cap 封顶 → 非缺陷；⑤ `BatchConsumerSinkFunction:149` close 失败 ERROR → 每 close 一次 → 非缺陷；⑥ 其余全部为 open/close/restore 启停一次性日志 → 非缺陷
- **信心水平**: 确定（value function 每次 scrape 执行为 Micrometer 标准语义；代码无一次性守卫逐行核实；owner doc 声明原文引用）
- **误报排除**: 不属于「信息性日志」豁免——缺陷不在日志本身而在**频率与文档承诺不符**且失败源是持久性构建特征（非偶发瞬态）；不与 C7-004 重复计数——该条是 meter 失明（值 NaN），本条是失败路径日志放大与文档分歧，修复点不同（一个在标签/注册，一个在 WARN 守卫）
- **复核状态**: 已保留（P2 维持）——二轮独立复核：live 重验 javadoc（:26-28）声明「one-shot warn」而 readProperty（:91-101）无 once-flag/无限频/无去重（gauge value function 每 scrape 执行）、owner doc `nop-stream.md:90`「告警一次」声明在案；全审计目录（本目录 C1–C8/C10 各轮 + core-runtime 审计）无重复立项（C1-002/C6-004 为同点位 SIGSEGV 异缺陷，不混淆）。修正一处算术：15s scrape × 1 个失败属性 ≈ 240 条/小时、5,760 条/天（原条目「5,760 条/小时、13.8 万条/天」高估 24×），「随运行时长无界放大」结论不变；WARN 分支可达性依赖所用 RocksDB 构建的属性可用性（javadoc 自认 varies by version），文档-代码分歧面不受影响

### [nop-stream-state-connectors-C7-013] Avro Schema Registry 客户端的 JDK HttpClient 构造即建、永不通close——每任务实例泄漏一条 selector 线程（C7-002 同族，量级更小）

- **文件**: `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java:34,43-49`（构造即建 HttpClient；类未实现 AutoCloseable、全类无 close）；`AvroWireFormat.java:38-48`（强引用持有）与 `:121-129`（工厂方法闭包注入连接器）
- **证据片段**:
  ```java
  public class AvroSchemaRegistryClient {          // 非 AutoCloseable，无 close()/shutdown
      private final HttpClient http;               // JDK 21+ HttpClient 本身是 AutoCloseable

      public AvroSchemaRegistryClient(String baseUrl) {
          ...
          this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
          // 构造即启动 client 内部 selector 线程；全模块无任何 close 调用点
      }

  // AvroWireFormat —— 工厂方法以 lambda 闭包注入 Kafka 连接器：
  public java.util.function.Function<byte[], GenericRecord> avroDeserializer(String subject) {
      return bytes -> decode(bytes, subject);      // 捕获 this（含 client+HttpClient）
  }
  ```
- **严重程度**: P3
- **并发场景**: 无并发缺陷面（client 自身线程安全）；缺陷为生命周期——每个使用 avro 格式的任务实例（KafkaSourceFunction 的 valueDeserializer / KafkaTwoPhaseCommitSink 的 valueSerializer 背后的 AvroWireFormat → AvroSchemaRegistryClient）在构造时创建一个 JDK HttpClient，其内部 selector 线程随构造启动
- **触发时序/增长链**: T1 作业在 TM 进程启动/失败重启/翻台 → 每个使用 avro 格式的端点实例重建 AvroWireFormat → 新 AvroSchemaRegistryClient → 新 HttpClient（eager 构造，`:48`）→ T2 任务结束/重建时，旧实例经 lambda 强引用链随任务函数一起不可达，但**模块内没有任何 close 调用**（类非 AutoCloseable、AvroWireFormat 无生命周期钩子、Kafka 两端点亦无格式层释放契约）→ JDK HttpClient 的内部 selector 线程在未 close 时持续存活（Java 21 起 `HttpClient implements AutoCloseable` 的契约正为此设，本代码未使用）→ 长驻 TM 进程内：每「作业生命周期 × 每 avro 端点」泄漏一条 daemon selector 线程 + 关联 selector/fd 状态，随翻台次数线性累积
- **现状**: 资源获取（构造即建）与释放（无 close）不对称（F8），与 C7-002 的 KafkaProducer 同族同形；区别在量级——HttpClient 无 broker socket 池、无 RecordAccumulator buffer、selector 线程为 daemon（不阻塞 JVM 退出），且空闲时不产生流量
- **风险**: 多作业长驻 TM、avro 格式高频翻台的部署下线程数缓慢爬升（量级：每实例一线程 ≈ 1MB 栈 + 少量 fd）；观测盲区（无线程名归因到本模块，泄漏源难定位）；owner doc 格式层章节未声明任何客户端生命周期契约
- **建议**: `AvroSchemaRegistryClient` 实现 `AutoCloseable`（close 内 `http.close()`，JDK 21+ 直support）；生命周期挂接面两选一：① `AvroWireFormat` 传递 close 责任给承载它的连接器函数 close 链（与 C7-002 建议的 `KafkaTwoPhaseCommitSink implements AutoCloseable` 同一批修复）；② 模块内提供共享 client 工厂（按 baseUrl 键的进程级单例），使实例数与任务翻台解耦——由于 client 线程安全且纯读缓存，共享是安全的
- **失效面枚举**（同族长生命周期资源逐一核对，接续第 1 轮 C7-002 的枚举）: ① KafkaProducer → C7-002（P1）；② JDBC 每提交连接 → commit finally 中 rollback/setAutoCommit/close（本轮重验 `JdbcTwoPhaseCommitSink.java:660-737`）→ 成对；③ KafkaConsumer → `KafkaSourceFunction.run` try-with-resources → 成对；④ Message/Debezium reader 线程 → close join(5s)+typed → 成对；⑤ RocksDB 全部 native 句柄 → backend close() 全覆盖（本轮逐行核实，见第 2 节第 3 项）→ 成对；⑥ 本体（Avro HttpClient）→ 本发现
- **信心水平**: 代码事实（eager 构造、无 close 点、非 AutoCloseable、每任务实例新建）为**确定**；「selector 线程在未 close 时存活」为**很可能**（JDK HttpClient 无 close 时的 selector 线程存续行为按 JDK 21+ AutoCloseable 契约与公开实现判断，未在本环境做泄漏复现；即使按最乐观口径存在 Cleaner 兜底回收，模块内仍无显式释放点，close 纪律与同类资源不对称）
- **误报排除**: 不按「测试专用代码」豁免——AvroWireFormat 工厂是 owner doc 声明的连接器注入面（生产路径）；不与 C7-002 重复——不同类、不同模块、不同量级，且第 1 轮 C7-002 的失效面枚举未覆盖格式层；「低频路径」豁免不适用——作业启停/翻台是长驻运行主循环（与 C7-002 同判）
- **复核状态**: 已保留（跨维重复确证：归并 C4-002，终裁随其 P2）——二轮独立复核：live 重验 `:34`/`:48` eager 构造、全类无 close、类非 AutoCloseable、`AvroWireFormat`（:38）强引用、工厂方法（:122-129）闭包捕获 this。同一缺陷（AvroSchemaRegistryClient 的 JDK HttpClient 无关闭路径）已由 **C4-002 以 P2 立项**，并经 C4 维度独立复核+二轮复核维持 P2、merge-adjudication 裁定 20 终裁独立保留——C4-002 二轮复核进一步证实 selector 线程被强引用使 GC 不可达（强于本条「很可能」的对冲表述，该不确定项随归并消解）。round2 盲审边界（未读他维文件）致重复立项；本条与 C4-002 修复并轨，P3→P2 为归并对齐上修（非降级）

---

## 2. 排除项/定向核查

### 2.1 日志放大专项（8 模块全部日志调用盘点）

全模块 grep `.warn|.error|.info|.trace|.debug(` 得全集 42 处，逐处归类：

| 路径形态 | 处数 | 判定 |
|---|---|---|
| 绑定 scrape/采集频率的 value function | 1（`RocksDBMetricsRecorder.readProperty:98`） | **C7-012** |
| 周期观测 timer（CEP cache statistics INFO） | 1（`SharedBuffer.logCacheStatistics:388`） | 有界：默认 30 分钟/条（`CEP_CACHE_STATISTICS_INTERVAL` = 30 MIN），间隔可配、≤0 可关 → 非缺陷 |
| 恢复路径（每恢复一次 × 有界条目数） | 4（CepOperator:803 legacy 键；FileTwoPhaseCommitSink:314/343；JdbcTwoPhaseCommitSink:685） | 有界 × 引擎 restart cap 封顶 → 非缺陷 |
| 启停/生命周期一次性（open/close/restore/启动枚举） | 34 | 一次性 → 非缺陷 |
| 热路径 trace（`SharedBuffer.onCacheRemoval:237`） | 1 | `isTraceEnabled()` 守卫 + trace 级 → 非缺陷（注释自证曾因热路径日志降级） |
| 热路径每记录 WARN/ERROR | **0** | 无发现 |

循环内 error 无抑制：全模块未发现。`AvroWireFormat.readerSchemaFor` 的 registry 故障路径**无日志**（静默回落）——反向问题（沉默而非放大），已由 C7-006 覆盖，不重复计数。

### 2.2 挂死可检测性矩阵补充（不产出也不报错路径的观测面）

阻塞原语横扫（`Thread.sleep|LockSupport|.await(|CountDownLatch|Semaphore|.join(`）全模块结果与第 1 轮 H1–H10 清单完全重合，无新等待点：`MessageSourceFunction:183`（shutdownLatch.await 1s 循环，响应 running 标志）、`DebeziumCdcSourceFunction:287`（completionLatch.await 1s 循环 + pendingError 检查，H10 已覆盖）、`MessageSourceReader:200`/`DebeziumCdcSourceReader:302`（close join 5s，H5）、`FileTwoPhaseCommitSink` 的 FileChannel.lock（H1）。RocksDB/CEP/Avro/JDBC/batch 模块零自建等待原语。

「不产出也不报错」路径的可见性链条核对：7 个连接器模块**零自定义指标注册**（grep 证实，见 2.5），停摆可见性由引擎/算子层兜底——任务线程阻塞（sink send、JDBC commit、loader.load）表现为 operator 层 busy ratio 升高 + records.in/out 停滞 + `nop.stream.engine.checkpoint.duration` 抬升 → checkpoint 超时 → abort → 恢复 → restart cap → FAILED（健康状态机 DEGRADED/FAILED + JOB_FAILED 告警事件）；source 端静默空转（Kafka poll 空批、message 后端静默）表现为 idle ratio → 1 + records.consumed 停滞 + watermark 停滞。owner doc「分层指标标准集」与「逻辑健康状态机」两节为该链条的声明面，与 live 一致。**结论：无新发现**；连接器自身无专属 stall 指标属可接受设计（引擎级任务线程指标已覆盖同一线程），不立项。

### 2.3 自愈清理面——句柄/连接对象收敛（本轮新覆盖，第 1 轮覆盖的是临时目录）

- **RocksDB 后端 native 句柄**：`RocksDBKeyedStateBackend.close()`（:978-1067）逐项释放 metricsRecorder（detach）→ 全部 cfHandles → defaultCF → db → writeBufferManager → blockCache → bloomFilter → cfOptions → dbOptions，首错重抛 + 余者 suppressed，域全部置 null——**全覆盖**。close 的调用链证据：`nop-stream-core/.../operators/AbstractStreamOperator.java:136-146`——operator close 链无条件调用 `keyedStateBackend.close()`，注释明示防「job-restart 重开同目录自持 LOCK」，即**每次任务重建路径必经** → 旧实例句柄收敛，无泄漏（与 C7-004 的时序叙述互证）。
- **增量恢复的只读源 DB**：`RocksDBIncrementalRestore.restoreRangeInto`（:209-230）——`try (RocksDB src = openReadOnlyTyped(...))` try-with-resources + finally 中逐个 close 全部 src cfHandles → 恢复源实例与句柄全部收敛（残留目录问题已由 C7-003 覆盖，此处仅句柄面）。
- **JDBC 每提交连接**：`JdbcTwoPhaseCommitSink.commit`（:660-737）finally 三连 rollback/setAutoCommit(true)/close，各失败分支 WARN 但不中断 → 每提交收敛（第 1 轮结论重验成立）。
- **Kafka 两端**：consumer try-with-resources（run 退出即关）；producer 无 close → 已由 C7-002 立项，不重复。
- **后端内存面随重建**：`states`/`stateTypes`/`restoredDescriptors` 均实例字段随旧 backend 整体不可达；`TtlContext` sidecar 随 backend；`RocksDBSnapshotSerDe` restore 段（:356-500 抽读）全为恢复期瞬态结构 + `clearAllStates` 先清后写 → 无跨恢复累积。

**结论：句柄/连接收敛面干净，无新发现**（存储残留面此前已由 C7-003/C7-010 立项）。

### 2.4 重试叠加（checkpoint 失败重试 × 2PC 恢复重试 × connector 重连）

模块内重试循环 grep（`retry|backoff|reconnect`）全 8 模块仅 2 处命中且均非循环：`BatchConsumerSinkFunction:128`（错误消息文案「data retained for retry」——重试委托引擎重启语义）、`BatchConsumerSinkConnectorFactory:39`（描述符声明 `BUFFERED_RETRY`）。引擎侧存在 sink commit 重试（`JobCoordinator.java:1366,1426-1428`，`retryFailedCommits`），其注释明示安全性前提「sink idempotency guards make the retry safe」——三兄弟守卫（JDBC 台账三元键 / file manifest 键 / kafka 高水位幂等）第 1 轮已逐一核实。组合放大面逐项：commit 重试 → 每次 = 一次幂等探测/一事务，有界；恢复重试 → 每次 restart 一个 pendingCommits 重放，有界 × restart cap；connector 重连 → 模块内无自建重连循环（Kafka client 内部重连受 `default.api.timeout.ms`/`delivery.timeout.ms` 上限，H6/H8 已核）。**结论：无自放大组合，无新发现**。

### 2.5 metrics 标签基数专项复核（对先前中断段结论的独立复验）

grep `StreamMetricsRegistries|MeterRegistry|.counter(|.gauge(|.timer(` 全 8 模块（去 import）仅命中 2 个注册点：`CepOperator:438-439`（counter，C7-009）与 `RocksDBMetricsRecorder:82-84`（六 gauge，C7-004/C7-012）。无第三形态、无含无界值（epoch/key/topic+partition）标签的注册点。先前中断段「两类病理已立项、无第三种」的结论**复验成立**。

### 2.6 第 1 轮 grep-only 未读段抽查（本轮精读补齐）

| 文件 | 第 1 轮覆盖 | 本轮结论 |
|---|---|---|
| `DebeziumTableSplitEnumerator`（全量精读） | grep | 有界：tables 静态表分片，shareBySubtask 交付即 remove；快照=未分配集；close 无外部资源 → 干净 |
| `DebeziumSourceSupport`（全量精读） | 签名+grep | 纯配置变换（实例限定/凭据解密副本/序列化拷贝），每实例构建一次 → 干净 |
| `MessageTopicSplitEnumerator`（全量精读） | 未列 | 无状态 per-registration token，restore 仅信息性 → 干净 |
| `BatchLoaderSource` + `BatchLoaderSplitEnumerator`（全量精读） | grep | 单 token + assigned 标志，restore 感知；序列化器纯文本有界 → 干净 |
| `FileSource`（全量精读，含 sample 路径） | grep | 纯配置 + 有界序列化（fail-fast 校验字符集）；sample 为一次性只读 → 干净 |
| `RocksDBSnapshotSerDe`（前 360 行精读 + 后段结构扫描） | grep | 全量快照路径 TTL 过滤存在；全量内存物化 = 全量快照固有设计（增量路径为 opt-in），非独立于状态规模的单调增长 → 非缺陷；restore 段瞬态结构 + clearAllStates → 干净 |
| `RocksDBKeyEncoder`（热路径 grep 核查） | grep | 无 Pattern.compile/replaceAll；backend 侧 `cachedStorageKeyFor` 单槽缓存已摊销编码成本 → 干净 |
| CEP 68 个 pattern/condition/model 配置类 | grep | 维持第 1 轮判定：纯字段持有配置，无运行时累积/阻塞面（本轮阻塞原语横扫零命中佐证）→ 不逐文件升级精读 |

---

## 3. 终止声明

### 累计发现清单（第 1 轮 + 第 2 轮，含先前中断段）

| ID | 标题（简） | 严重度 | 位置 |
|---|---|---|---|
| C7-001 | JDBC 2PC epoch 台账表只增不删（F4） | P1 | 第 1 轮文件 |
| C7-002 | KafkaProducer 永不关闭（与 C6-001 疑似同缺陷，合并裁定阶段处理） | P1 | 第 1 轮文件 |
| C7-003 | RocksDB 增量恢复临时目录失败/kill 残留 | P2 | 第 1 轮文件 |
| C7-004 | RocksDB 六 gauge 重建后永久 NaN | P2 | 第 1 轮文件 |
| C7-005 | CEP reconciledTimerKeys 永不回收 | P2 | 第 1 轮文件 |
| C7-006 | Avro registry 每记录重试无退避 + HTTP 无请求超时 | P2 | 第 1 轮文件 |
| C7-007 | file manifest 线性增长 + 全量重写；JVM 锁表只增不删 | P3 | 第 1 轮文件 |
| C7-008 | Avro per-id schema 缓存无上限 | P3 | 第 1 轮文件 |
| C7-009 | CEP 降格计数器 identity 标签 meter 累积（embedded 形态） | P3 | 第 1 轮文件 |
| C7-010 | RocksDB TTL sweep 与 checkpoint 成败耦合（60s 周期 sweep 仅 Memory 后端存在） | P2 | 第 1 轮文件末尾追加段 |
| C7-011 | kafka-2pc 探测以 initTransactions 为之，owner doc 红线表缺行 | P3 | 第 1 轮文件末尾追加段 |
| **C7-012** | RocksDB gauge 读数失败 WARN 按 scrape 频率放大，「告警一次」声明未实现 | **P2** | 本文件 |
| **C7-013** | Avro HttpClient 构造即建永不 close，每任务实例泄漏 selector 线程 | **P3** | 本文件 |

另有第 1/2 轮未立项情报项 I1–I4（Debezium schema-history 孤儿文件、JDBC schema-history append-only、废弃 dbPath 无 owner 清理、lazyEviction=false no-op 槽），见前轮文件。

### 终止依据

1. **六个定向方向全部闭合**：日志放大（全量盘点，唯一采集路径缺陷已立项 C7-012）；挂死矩阵（阻塞原语横扫与 H1–H10 完全重合，停摆可见性链条有引擎级指标 + checkpoint 超时 + 健康/告警事件兜底）；自愈句柄/连接收敛（全部成对，逐项证据在 2.3）；重试叠加（模块零重试循环 + 引擎 retryFailedCommits 受幂等守卫与 restart cap 双约束）；metrics 基数（独立复验仅 2 注册点）；未读段抽查（7 个 grep-only 文件精读 + 2 类文件维持判定，全部干净）。
2. **增长源/挂死点清单已两轮饱和**：G1–G15 + H1–H10（第 1 轮）经第 2 轮六个方向再验证无新增形态；唯一新增的慢性病（C7-013）来自对已读文件生命周期面的换角度复查，而非新文件。
3. **剩余未覆盖面为最低价值**：CEP 68 个纯配置 POJO（无运行时行为）与 RocksDB 各状态类的同构 CRUD 面（抽样 3/8 + SerDe + encoder 干净，其余同模板）；两者即便精读，产出预期不高于已记录的 I1–I4 情报项。
4. 第 1 轮已核对面（2PC 恢复链、CEP 处理时间恢复、时钟回拨敏感面、epoch 溢出）与先前中断段四轴（metrics 基数/自愈旧状态/重试退避/时钟）本轮未发现翻案证据。

**结论：未发现新的高价值问题面。C7 维度深挖迭代建议于本轮（累计 13 项发现）终止。**

---

## 4. 覆盖声明

- **本轮精读文件**（第 1 轮未覆盖或仅 grep 的）：`RocksDBMetricsRecorder`、`RocksDBIncrementalRestore`（句柄面复检）、`RocksDBKeyedStateBackend`（close/open/restore 全文复检）、`RocksDBSnapshotSerDe`（:1-360 精读 + :360-500 结构扫描）、`DebeziumTableSplitEnumerator`、`DebeziumSourceSupport`、`MessageTopicSplitEnumerator`、`BatchLoaderSource`、`BatchLoaderSplitEnumerator`、`FileSource`、`AvroSchemaRegistryClient`、`AvroWireFormat`（全文）；跨模块契约证据核对（只读）：`AbstractStreamOperator`（close 链）、`JobCoordinator`（retryFailedCommits 注释面）、`IKeyedStateBackend`（close 契约）、`NopCepConfigs`（cache-stats 间隔默认值）。
- **本轮 grep 定向核查面**：8 模块日志调用全集（42 处归类）、阻塞原语横扫、重试循环横扫、metrics 注册点全集、JDBC commit finally 重验。
- **未覆盖（有意不覆盖）**：CEP 68 个纯配置 POJO 逐文件精读（维持第 1 轮 grep 判定）；RocksDB 8 个状态类中未抽样的 5 个同构 CRUD（RocksDBMap/List/Reducing/InternalList/InternalAggregating 的 per-type 文件——模板同构，抽样 3/8 + SerDe/encoder 干净）；`_` 前缀生成文件（纪律豁免）；nop-stream-core/runtime/runtime-main 的自身实现（越界，仅作契约证据核对）。
- **盲审纪律遵守声明**：本轮未读取 ai-dev/audits（除指定前序文件）、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件；未读取其他维度（C1–C6、C8）的审计输出；未修改前轮文件。
