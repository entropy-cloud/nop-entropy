# C2 维度初审报告：锁与同步原语（nop-stream-state-connectors）

- **审计维度**: C2（锁与同步原语——互斥成立性、死锁、锁代价、等待点超时）
- **审计对象**: nop-stream/nop-stream-cep、nop-stream-rocksdb、nop-stream-connector{,-batch,-jdbc,-debezium,-kafka}、nop-stream-format-avro（8 模块 src/main/java，合计 140 files / 25,458 lines）
- **审计方式**: 盲审（未读 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 任何历史记录），仅以 live code 为准
- **依据文档**: `docs-for-ai/03-modules/nop-stream.md`、`docs-for-ai/03-modules/nop-stream-connectors.md`（owner docs）；基线 `_tmp/cra-baseline-nop-stream-state-connectors.txt`（仅用于圈定重点文件）
- **审计日期**: 2026-10-14
- **结果概要**: P0=0，P1=4，P2=1，P3=4；锁序图无环；CEP 模块零锁、任务线程封闭成立

---

## 检查范围说明

**锁载体全量盘点**（基线 grep + 补充 grep `wait/notify/CountDownLatch/Semaphore/Atomic*/volatile/LockSupport`，两轮交集覆盖 8 模块全部 140 个文件）：

| 模块 | 锁载体 | 判定摘要 |
|---|---|---|
| nop-stream-cep | **零显式锁**（无 synchronized/ReentrantLock）；仅 `Lockable` 的 AtomicInteger 引用计数；5 处 computeIfAbsent 全部位于任务线程封闭的普通集合或提取路径局部 map | 封闭假设成立（TaskMailbox 单线程模型），computeIfAbsent 无重逻辑/无递归；见「已排除项」E1 |
| nop-stream-rocksdb | `RocksDBMetricsRecorder`：synchronized close/readProperty + AtomicReference；`RocksDBKeyedStateBackend`：AtomicLong 快照 id 计数器，**JNI 原生句柄生命周期与 metrics 采集线程的交叉点** | 发现 C2-001（P1） |
| nop-stream-connector | `FileSourceReader`：10 处 synchronized(this)（cursor/快照互斥）；`FileTwoPhaseCommitSink`：currentBuffer 监视器 ×4 + 双重检查 charset() + 静态 `MANIFEST_JVM_LOCKS`（ReentrantLock→FileLock 固定顺序）；`MessageSourceFunction`：synchronized(ctx) + CountDownLatch；`MessageSourceReader`：volatile + 内嵌线程 + 有界队列 | 发现 C2-004（P1）、C2-006/007/009（P3） |
| nop-stream-connector-batch | 无锁（注释声明的非同步缓冲 + `ParallelismCheckable` 部署期 fail-fast 守卫） | 已声明边界 + 运行时守卫，非缺陷（规则 6） |
| nop-stream-connector-jdbc | `JdbcTwoPhaseCommitSink`：synchronized(this) DCL ×3（均配 volatile）+ synchronized(currentBuffer) ×3；pendingCommits 为基类 synchronizedMap | DCL 正确、无锁嵌套，未发现缺陷 |
| nop-stream-connector-debezium | `DebeziumCdcSourceFunction`：CountDownLatch（await 1s 有界）+ AtomicBoolean runEntered CAS 守卫（F10 正例）；`DebeziumCdcSourceReader`：内嵌线程 + volatile；`DebeziumTableSplitEnumerator`：**裸 LinkedHashMap 无自身锁** | 发现 C2-003（P1）、C2-004 同族 |
| nop-stream-connector-kafka | `KafkaTwoPhaseCommitSink`：synchronized(pending) 单点（与基类 RL-4 迭代纪律一致）；volatile 布尔 | 未发现缺陷 |
| nop-stream-format-avro | 两处 `ConcurrentHashMap.computeIfAbsent`，**mapping function 内嵌 HTTP I/O** | 发现 C2-002（P1） |

**核对方法**：对每个锁载体核对其全部迭代点是否在锁内（门禁先例口径）；对每个等待点核对超时与中断响应（F5）；对每个 check-then-act 守卫核对原子性（F10）；对每个跨线程调用边核对锁序（死锁排查）；对每个缺陷类发现 grep 同族兄弟（F3）。

**线程模型事实基线**（后续时序推演依据）：TaskManager 以 `taskExecutor.submit(runningTask)` 并发运行各 subtask（`nop-stream-runtime/.../taskmanager/TaskManager.java:594,780`）；LOCAL 模式 `LocalSourceCoordinator` 只在 `startEnumerator/snapshotState/close` 持 `enumeratorLock`，`registerReader→enumerator.addReader` 与 `ReaderChannel.requestSplits→enumerator.handleSplitRequest` 均在 reader 任务线程上**不持该锁**直接调用（`nop-stream-core/.../LocalSourceCoordinator.java:176-191,255-267`）；splits 交付回调链 `assignSplits→channel.deliver→drainPending→reader.addSplits` 在触发枚举的线程上同步执行（可为另一 subtask 的任务线程）。

---

## 锁序图（文本）

记号：`A → B` 表示线程持有锁 A 的同时获取锁 B。`[跨线程]` = 两端在不同线程可达；`(同线程重入)` = 仅同一线程重入，不构成死锁面。

```
FSR  = FileSourceReader.this                      JDBC-this = JdbcTwoPhaseCommitSink this（DCL）
CH   = ReaderChannel.pendingSplits 监视器         CB-J      = JdbcTwoPhaseCommitSink currentBuffer
EL   = LocalSourceCoordinator.enumeratorLock      PC        = TwoPhaseCommitSinkFunction.pendingCommits
CB   = FileTwoPhaseCommitSink currentBuffer       JL        = MANIFEST_JVM_LOCKS ReentrantLock（per outputDir）
FILE-this = FileTwoPhaseCommitSink this（DCL）    FL        = .manifest.lock OS FileLock
RMR  = RocksDBMetricsRecorder this                K-pending = KafkaTwoPhaseCommitSink synchronized(pending)
ENUM-STATE = DebeziumTableSplitEnumerator.shareBySubtask（无锁保护）

边清单：
 1. FSR → CH            [isFinished():182-199 → pendingCount():248-252]
 2. EL → FSR            [跨线程] startEnumerator:110-169 → enumerator.start → assignSplits
                        → channel.deliver → drainPending cb → reader.addSplits:171-175
 3. FSR → (无锁) → ENUM-STATE → CH → FSR
                        (同线程重入) requestSplits:409-421 不持 EL → handleSplitRequest
                        → assignSplits → deliver → cb → addSplits 重入 FSR
 4. FSR → (无锁) → CH → (无锁) → FSR
                        drainPending 在 pendingSplits 监视器【外】执行回调（LocalSourceCoordinator:229-245），
                        无 CH → FSR 边
 5. CB → FILE-this      saveState:250-264 → writeLines:675 → charset():211-217 DCL 慢路径
                        （charset 构造/readObject 已赋值，慢路径实际不可达；单向边，无回边）
 6. JL → FL             withManifestLock:389-404，固定顺序，注释明示「Lock ordering is always
                        jvmLock → fileLock (no inversion)」
 7. PC → ∅              基类 finishCommit/restoreFromEpoch 均「check(持PC) → 释放 → commit(eid)(锁外)
                        → 重取 PC remove」（TwoPhaseCommitSinkFunction:189-203,268-284）；
                        commit 实现内 getPendingCommits().get/remove 为原子单操作——
                        无 PC → JL/FL/K-pending 边
 8. K-pending → ∅       restoreFromEpoch:259-268 synchronized(pending) 块在 super 调用前闭合
 9. RMR → ∅             close/readProperty 持 RMR 期间无嵌套获取；但 gauge lambda 在锁【外】解引用
                        dbRef.get()（见 C2-001，属可见性/释放序缺陷而非锁序环）
10. JDBC-this → ∅ / CB-J → ∅
                        ensureInitialized:461-475 与 resolveKeyedColumns:1031-1049 在 this 内不取
                        CB-J；saveState:641-651 取 CB-J 前已释放 this——无嵌套
11. ENUM-STATE 相关边    addReader/remove(0|1)（各 subtask 任务线程）与 snapshotState 迭代
                        （checkpoint 线程，持 EL）对同一裸 LinkedHashMap 并发——**无锁纪律缺口**，
                        见 C2-003（非环，是单侧锁缺失）

环检测结论：**无环**。所有嵌套均为单向（5、6），互持对不存在；
PC 的「释放-行动-重取」纪律（边 7）是阻断 PC→JL→PC 环的关键正确设计。
```

---

## 发现条目

### [nop-stream-state-connectors-C2-001] RocksDB 指标 gauge 在锁外解引用 dbRef，与 backend close 构成 JNI use-after-free 竞态

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:82-101`（gauge 注册 82-84；close 65-67；readProperty 91-101）；关联 `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:979-1016`
- **证据片段**:
  ```java
  // RocksDBMetricsRecorder.java:82-84 —— 解引用发生在 scrape 线程、进入 synchronized 之前
  private void gauge(MeterRegistry registry, String meterName, String property) {
      registry.gauge(meterName, Tags.empty(), dbRef, ref -> readProperty(ref.get(), property));
  }
  // :91-95 —— synchronized 只保护方法体，保护不了已在锁外完成的参数求值
  public synchronized double readProperty(RocksDB db, String property) {
      if (db == null) { return Double.NaN; }
      try { return db.getAggregatedLongProperty(property); ...
  // RocksDBKeyedStateBackend.java:979-1016 —— close() 先 detach 后 db.close()（顺序正确）
  //   但 close() 释放 RMR 监视器之后、db.close() 释放原生句柄之后，
  //   已拿到 stale 引用的 scrape 线程仍会进入 readProperty
  ```
- **严重程度**: P1
- **并发场景**: Prometheus/micrometer scrape 线程（ops HTTP 端点 `/metrics`，owner doc 声明的进程级 pull 端点）vs 任务/作业关闭线程（`RocksDBKeyedStateBackend.close()`，作业 stop/失败重启/恢复重建路径）
- **触发时序**: T_scrape（采集线程）执行 `RocksDBMetricsRecorder.java:83` 的 `ref.get()`，得到非 null 的 RocksDB 引用（此刻原生句柄存活）→ 被调度让出；T_close（关闭线程）执行 `recorder.close():989`（synchronized，置 dbRef=null 后**立即释放监视器**），继续执行 `db.close():1013` 释放 JNI 原生句柄 → T_scrape 恢复执行，携带 **stale 非 null 引用**进入 `synchronized readProperty:91`（此刻无人持锁，直接进入），对已释放句柄调用 `getAggregatedLongProperty:96` → 原生层 use-after-free → SIGSEGV / JVM 整进程崩溃（节点上全部作业陪葬）。javadoc:59-63 自己声明了这一后果（"reading aggregated properties on a closed handle crashes the JVM in native code"），但 synchronized 修不到锁外求值。
- **现状**: close 与 readProperty 互相 synchronized 的意图是互斥 detach 与 scrape，但互斥对「参数求值」这一步失效——解引用点（`ref.get()`）在临界区外，`db == null` 检查形同虚设（拿到的是 stale 非 null 引用而非 null）。
- **风险**: 任何一次 backend close 与指标 scrape 的交叠即整进程崩溃；`/metrics` 抓取是周期性的（典型 15-30s），作业的停止/失败重启是常态运维动作，长时运行下交叠概率不可忽略。崩溃前无任何 Java 层异常可捕获。
- **建议**: 把解引用移入临界区——`readProperty` 改为无参读 `dbRef`：`private synchronized double readProperty(String property) { RocksDB db = dbRef.get(); if (db == null) return NaN; ... }`，gauge lambda 改为 `ref -> readProperty(property)`（dbRef 字段被 lambda 捕获，安全）。或按平台惯例改用 `AtomicReference` 的 get-acquire + 句柄代际校验；首选前者，改动 3 行。
- **失效面枚举**: 同一族 = 该 recorder 注册的全部 6 个 gauge lambda（`RocksDBMetricsRecorder.java:74-79`，block-cache-usage/pinned/memtable/estimate-num-keys/compaction-pending/compactions-running——共用同一 `ref -> readProperty(ref.get(), property)` 形态，逐一登记：6 处全部带同一缺陷，修一处 lambda 形态即全量修复）。grep 全模块其余 RocksDB 跨线程访问点：`RocksDBKeyedStateBackend` 的 db/cfHandles 仅任务线程使用（open/close/状态读写同线程），`AbstractRocksDBState`/`RocksDBSnapshotSerDe` 均任务线程封闭——无其他兄弟。nop-kernel 平台设施不重审。
- **信心水平**: 确定（交错序列为代码级必然；触发需 close 与 scrape 交叠这一外部条件）
- **误报排除**: 不属于「synchronized 本身的使用不是问题」——问题恰在 synchronized 与解引用点的**相对位置**；也不是低频粗粒度锁问题——后果是进程崩溃，非优雅性问题。
- **复核状态**: 已保留（P1）—— 2026-10-14 独立复核确认，判定理由与现状归属见文末「维度复核结论」；二轮复核：已保留（P1）

### [nop-stream-state-connectors-C2-002] Avro Schema Registry 客户端在 computeIfAbsent 内做无超时 HTTP I/O——registry 挂死时永久阻塞并毒化共享 bin 锁

- **文件**: `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java:68-70,101-129`；`nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroWireFormat.java:41,54-60`
- **证据片段**:
  ```java
  // AvroSchemaRegistryClient.java:68-70 —— CHM bin 锁内执行网络调用
  public Schema getSchemaById(int id) {
      return schemaByIdCache.computeIfAbsent(id, this::fetchSchemaById);
  }
  // :113-123 —— http.send 无请求级超时（仅构造器的 connectTimeout(10s)，:48）
  resp = http.send(request, HttpResponse.BodyHandlers.ofString());
  ```
  ```java
  // AvroWireFormat.java:55-60 —— encode 路径同形态：POST + GET 都在 mapping function 内
  int schemaId = writerSchemaIds.computeIfAbsent(subject, s -> {
      int id = client.registerSchema(s, record.getSchema());
      client.getSchemaById(id);
      return id;
  });
  ```
- **严重程度**: P1
- **并发场景**: Kafka source decode 线程 / kafka-2pc sink encode 线程（每 subtask 一条任务线程；LOCAL 并行或同 JVM 多作业共享同一 `AvroWireFormat`/`AvroSchemaRegistryClient` 实例——`avroSerializer/avroDeserializer` 工厂产物按引用共享）vs 外部 Schema Registry 服务
- **触发时序**: Registry 端 accept 连接后不响应（挂死/故障切换中）→ T1（某 subtask 任务线程）decode 首条携带新 schemaId 的记录 → `getSchemaById:69` cache miss → 进入 `computeIfAbsent`，**持 CHM bin 锁**执行 `fetchSchemaById` → `http.send:116` 无 request timeout，连接建立后读阶段**无限期阻塞** → T2（另一 subtask 线程）decode 落到同一 bin 的任意 id → 阻塞在 bin 锁上 → 两个任务线程的 mailbox 均停摆：无异常、无日志、checkpoint 全停，作业表面 RUNNING 实际冻结（F5「不产出也不报错」型挂死，健康检测不可见，除非配置了 checkpoint 超时兜底——而 checkpoint 超时取决于 barrier 能否发出，源头卡死的 decode 使 barrier 永不产生）。同 bin 毒化随 map 条目数（schema id 数）覆盖大多数活跃 key。
- **现状**: 缓存命中后不再有 I/O，故稳态吞吐不受影响；缺陷集中于「cache miss + registry 不健康」的组合：I/O 被放进 CHM 的 bin 临界区，且该 I/O 自身无上界。
- **风险**: 长时运行中 registry 一次网络故障（LB 静默丢包、连接半开）即把并行 source/sink 任务组整体挂死，需人工重启；无 typed 错误、无自动恢复。owner doc「失败面全 typed：registry 不可达……均抛 StreamAvroException（不静默降级）」的声明在「挂死（不失败也不返回）」这一形态下不成立。
- **建议**: ① 每个 `HttpRequest` 加 `.timeout(Duration)`（如 30s），一行覆盖 getJson/postJson 全部路径（根治 F5）；② mapping function 移出临界区——`get`→miss→请求→`put`（幂等，最多少数重复请求），或先在锁外 fetch 再 `putIfAbsent`；若保留 computeIfAbsent，须以「请求超时已存在」为前提。
- **失效面枚举**: 同族 HTTP 无超时点逐一登记——`readJsonResponse:116`（getSchemaById→fetchSchemaById、getLatestSchema:78、registerSchema:59 三条路径共用，全部无 request timeout）；computeIfAbsent-I/O 两处——`AvroWireFormat.encode:55`（POST+GET）、`AvroSchemaRegistryClient.getSchemaById:69`（GET）。`getLatestSchema:73-86` 为 get-then-put 非原子但幂等（双线程重复 fetch 后 put 覆盖，值等价），不另立案。grep 模块内其余网络点：无（该模块仅此 HttpClient）。
- **信心水平**: 确定（挂死行为由 JDK HttpClient 语义保证；共享实例前提见并发场景，跨 subtask 复制链路中 Function 字段按引用共享）
- **误报排除**: 不属于「有界声明存在的缓存」误报——问题不是缓存无界，而是临界区内无界等待；不适用「原语使用不是问题」——computeIfAbsent 内嵌阻塞 I/O 正是提示词 C2 第 2 步点名的复合误用形态。
- **复核状态**: 已保留（P1）—— 2026-10-14 独立复核确认，判定理由与现状归属见文末「维度复核结论」；二轮复核：已保留（P1）

### [nop-stream-state-connectors-C2-003] DebeziumTableSplitEnumerator 裸 LinkedHashMap 被并发 reader 线程读写——addReader/handleSplitRequest 绕过 enumeratorLock

- **文件**: `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumTableSplitEnumerator.java:47,88-105,113-119,131`；分发侧证据 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java:176-191,255-267`
- **证据片段**:
  ```java
  // DebeziumTableSplitEnumerator.java:47 —— 无任何同步保护的普通 LinkedHashMap
  private final Map<Integer, List<String>> shareBySubtask = new LinkedHashMap<>();
  // :88-89 —— 各 reader 任务线程并发执行的结构性删除
  private void deliverShare(int subtaskIndex) {
      List<String> share = shareBySubtask.remove(subtaskIndex);
  // :113-118 —— checkpoint 线程持 enumeratorLock 迭代，而上面的 mutator 不持该锁
  public DebeziumCdcEnumeratorState snapshotState(long checkpointId) {
      List<String> unassigned = new ArrayList<>();
      for (List<String> share : shareBySubtask.values()) { ...
  ```
  ```java
  // LocalSourceCoordinator.java:183-186 —— addReader 在 enumeratorLock【外】调用
  // （startEnumerator 已返回、锁已释放），registerReader 运行在各 subtask 自己的任务线程
  startEnumerator(null);
  try { enumerator.addReader(subtaskIndex); ...
  ```
- **严重程度**: P1
- **并发场景**: parallelism>1 的 CDC source：N 个 subtask 任务线程并发 `registerReader→addReader`（TaskManager 以 taskExecutor.submit 并发启动 subtask）；checkpoint 线程并发 `snapshotState`；恢复线程 `restoreState`
- **触发时序**: 启动期：T1（subtask 0 任务线程）执行 `addReader(0)→deliverShare(0)→shareBySubtask.remove(0)`，同时 T2（subtask 1 任务线程）执行 `remove(1)`——两线程对同一 LinkedHashMap 并发结构性修改（modCount/hash 桶内部状态），轻则内部链表错链、重则后续迭代死循环或条目丢失；交叠后果 A：T_cp（checkpoint 线程，持 enumeratorLock）在 `snapshotState:115` 迭代 `shareBySubtask.values()` 与 T1/T2 的 remove 并发 → `ConcurrentModificationException`（iter 检查 modCount）或撕裂快照——mutator 不持 EL，锁对迭代者不构成保护；交叠后果 B：某 share 条目在并发 remove 中丢失 → 该 reader 永远收不到表份额 → 该 CDC 引擎实例空转，且恢复快照同样缺表（静默丢路由）。
- **现状**: 类的线程安全完全寄托于 core 侧 javadoc「enumerator methods are invoked from the coordinator thread」（LocalSourceCoordinator:46-47），但同一 coordinator 的 `registerReader`/`requestSplits` 分发路径明示「safe for the reader task thread to call」并直接穿透到 enumerator 方法——契约与实现矛盾，enumerator 侧无任何自身防线（对比同族 `FileSplitEnumerator.java:55-58` 全部用 ConcurrentHashMap + 按 subtask 确定性分片，天然免疫）。
- **风险**: CDC 作业并行启动是标准路径（所有 subtask 同时 open），非罕见路径；启动竞态失败表现为作业启动失败重启（响亮）或份额静默丢失（表永不消费——后者违反 owner doc「每 reader 非空 share」部署门禁的意图）。
- **建议**: 双层修复取其一：① 8 模块内自守——`shareBySubtask` 改 `ConcurrentHashMap` + `deliverShare`/`restoreState`/`snapshotState` 语义核对（remove/iterate 原子性足够，快照撕裂可容忍为「下轮重分配」）；② 根治于 core——`registerReader`/`requestSplits` 的 enumerator 调用纳入 `enumeratorLock`（core 侧改动，跨出本报告对象但为根因层，建议合并裁定时报送持有 core 范围的维度）。优先 ①（受害者自守）+ 报送 ②。
- **失效面枚举**: 同族 enumerator 逐一登记——`FileSplitEnumerator.java`（安全：CHM ×4 + round-robin 按 `i % parallelism` 确定性分片使各 subtask 线程只写不相交子集，size-aware 路径同理，见 :172-225 推演）；`BatchLoaderSplitEnumerator.java`（安全：无可变共享状态，start 即一次性投递单 split :57）；`DebeziumTableSplitEnumerator.java`（**唯一缺陷实例**：裸 LinkedHashMap + remove/iterate/computeIfAbsent 三面并发）。core 分发侧兄弟：`handleSplitRequest` 直通路径对本 enumerator 为空实现（:108-110 no-op），实际暴露面收敛于 `addReader` 与 `snapshotState`。
- **信心水平**: 很可能（LinkedHashMap 并发结构破坏的精确后果随 JVM 实现而异，但并发修改本身与 CME 触发链为确定性）
- **误报排除**: 不属于「无状态单例被并发调用是设计意图」——该对象持跨线程可变状态；不适用「平台基础设施自身不重审」——缺陷实体（enumerator）在本审计对象内，core 仅作分发证据引用。
- **复核状态**: 已保留（P1，受害者侧登记）—— 2026-10-14 独立复核确认；派发根因已被 WI1.1 立案（cra-index C1-001），见文末「维度复核结论」；二轮复核：已保留（P1，受害者侧登记，挂靠 WI1.1-C1-001 不重复修复）

### [nop-stream-state-connectors-C2-004] MessageSourceReader/DebeziumCdcSourceReader 内嵌线程 launch 检查-后-行动无守卫——双线程可达双启动（重复订阅/队列孤儿化/双引擎）

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceReader.java:81-92,108-127,142-144`；同族 `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceReader.java:92-138,161-193,221-223`
- **证据片段**:
  ```java
  // MessageSourceReader.java:81-92 —— check-then-act 无锁/CAS 守卫
  private void launchRunner() {
      if (runnerThread != null) { return; }          // 82: 检查
      queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY); // 85
      Thread thread = new Thread(this::runEmbedded, ...); // 87
      thread.setDaemon(true);
      thread.start();                                 // 90
      runnerThread = thread;                          // 91: 生效点在最后
  }
  // :142-144 任务线程入口 / :113-117 交付线程入口 —— 两个线程可达同一无守卫方法
  if (queue == null) { launchRunner(); }              // pollNext, 任务线程
  if (this.token == null) { this.token = newToken; if (running) { launchRunner(); } ...
  ```
- **严重程度**: P1
- **并发场景**: reader 任务线程（pollNext 100ms 轮询，`queue == null` 触发）vs 分配交付线程（另一 subtask 的枚举链 `assignSplits→channel.deliver→drainPending cb→addSplits`，见检查范围说明线程模型）；checkpoint 线程（`snapshotState` 迭代 `adoptedSplits`）
- **触发时序**: T_deliver（交付线程）执行 `MessageSourceReader.java:113-114` 置 `this.token = newToken` → 进入 launchRunner，通过 ：82 空检查后正在创建线程（尚未到 ：85/:91）→ T_task（任务线程）下一轮 pollNext 读到 `token != null`（:134）、`queue == null`（:142，T_deliver 尚未执行 ：85）→ T_task 也通过 ：82 空检查（runnerThread 两边都还是 null）→ **两个 runner 线程**先后执行 `embeddedFunction.run(...)`：各自 `messageService.subscribe`（重复订阅→消息双份消费）；第二次 launch 的 ：85 覆写 `this.queue` → runner-1 持有孤儿队列，记录 put 进无人 poll 的队列 → 任务线程侧既重复又停滞。Debezium 同族窗口更宽：`DebeziumCdcSourceReader.launchEngine:96-128` 在 runnerThread 生效（:135）前完成 engine 配置构建 + `initializeState`（毫秒级），双启动 → **两台同实例名 Debezium 引擎**同时连库，offset store 双写互踩。另同根 facets（同把锁缺失的全部症状）：`token`/`queue`（MessageSourceReader:59,62）非 volatile、`adoptedSplits` LinkedHashMap（DebeziumCdcSourceReader:68）被交付线程 put 与 checkpoint 线程 `snapshotState:272` 迭代并发 → CME 使 checkpoint 失败。
- **现状**: 守卫只有一次裸字段读（:82/:93），无 synchronized/无 CAS/无 volatile 写序屏障；`runnerThread` 虽为 volatile，但检查与生效点之间的窗口不因此闭合。
- **风险**: 双订阅 = at-least-once 声明被突破为「同周期双份」；队列孤儿化 = 管线静默 stall（记录已从订阅侧消失、下游永不收到）；双引擎 = 数据库双倍快照负载 + offset 互覆（REPLAYABLE 位点失真）。触发窗口微秒级但 pollNext 以 100ms 周期无限重试，长时运行可积累命中机会。
- **建议**: launch 方法加单守卫即可收敛全部症状：`synchronized (this)` 包裹 addSplits/pollNext-launch 段（该锁无嵌套面， cheapest），或 `AtomicBoolean launched = new AtomicBoolean(); if (!launched.compareAndSet(false,true)) return;`（对齐同模块 `DebeziumCdcSourceFunction.runEntered:92-94` 的既有 F10 正例模式）；`token`/`queue` 补 volatile；`adoptedSplits` 迭代处做防御性拷贝或改并发容器。修复一处 `launchRunner` 形态 + 复制到 Debezium 兄弟。
- **失效面枚举**: 同族逐一登记——`MessageSourceReader.launchRunner:81`（缺陷）；`DebeziumCdcSourceReader.launchEngine:92`（缺陷，窗口更宽）；`BatchLoaderSourceReader`（安全：bounded source，run 仅任务线程单次调用，无交付线程 launch 路径）；`FileSourceReader`（安全：pollNext 内联拉取无内嵌线程）；`KafkaSourceFunction`（安全：legacy 单线程 run 模型无 launch 二入口）。交付回调链上的兄弟消费者 `SourceReaderOperator.bridgeChannelToReaderFromWire`（core）对 `preOpenSplits` 的追加与本缺陷正交（open 前单线程）。
- **信心水平**: 很可能（交错链完整具体；实际命中率依赖交付线程与任务线程的调度相位）
- **误报排除**: 不属于「FutureTask/CHM 原语本身」误报——这里没有任何原语，是裸 check-then-act；不适用「测试专用简化并发模型」——两条入口均为生产路径。
- **复核状态**: 已保留（P1）—— 2026-10-14 独立复核确认，判定理由与现状归属见文末「维度复核结论」；二轮复核：已降级至 P2（当前 LOCAL 生产接线下同一 reader 的全部入口均在自身任务线程串行、双线程交错不可达；理由见二轮复核结论）

### [nop-stream-state-connectors-C2-005] FileTwoPhaseCommitSink manifest 锁获取无超时且不可中断——对端 JVM 挂死时 commit 无限阻塞

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:389-404`
- **证据片段**:
  ```java
  // FileTwoPhaseCommitSink.java:389-404 —— 两级锁均为无限期获取
  private void withManifestLock(FileTwoPhaseCommitAction action) throws Exception {
      ReentrantLock jvmLock = MANIFEST_JVM_LOCKS.computeIfAbsent(...);
      jvmLock.lock();                                   // 392: 非 lockInterruptibly
      try (FileChannel channel = FileChannel.open(...)) {
          FileLock fileLock = channel.lock();           // 395: 阻塞式 OS 锁，无超时变体
          try { action.run(); } finally { fileLock.release(); }
      } finally { jvmLock.unlock(); }
  }
  ```
- **严重程度**: P2
- **并发场景**: 本 JVM commit 线程 vs 共享存储上另一 JVM 的 sink subtask commit 线程（AR-14 跨 JVM manifest 互斥设计的对偶面）；阻塞点位于 checkpoint commit 路径（`commit:274-293 → withManifestLock`）
- **触发时序**: T_peer（对端 JVM）进入 commit 临界区持有 `.manifest.lock` OS 文件锁，随后在 `forcePath:356`（fsync）上因存储故障/进程挂死而**长期不返回也不退出**（进程活着、线程僵死）→ T_self（本 JVM）`channel.lock():395` 无限阻塞；并行地本 JVM 内若另一 subtask 也等待，`jvmLock.lock():392` 的不可中断性使 cancel/interrupt 信号无法传导 → checkpoint 提交停滞，任务表现为持续 in-flight（无异常无日志）。线程 dump 可见（可检测性靠人工，无看门狗）。
- **现状**: 锁顺序纪律正确（JL→FL 固定，无反转）；缺陷仅在等待点上界缺失——同 owner doc 对 webhook/背压门等其他等待点均声明了超时语义（如 send-gate 10s typed 失败），此点无对齐。
- **风险**: 对端僵死时本端 checkpoint 永久卡住 → checkpoint 超时 → 作业失败重启 → 重启后 re-commit 再次进入同一临界区再次卡住 → 循环；需人工介入清理锁持有者。触发前提（对端线程僵死而非退出）罕见，故 P2 而非 P1。
- **建议**: JVM 内 `jvmLock.tryLock(timeout)`（带中断，超时 typed 失败并入 checkpoint 失败路径）；跨 JVM `channel.tryLock()` 自旋 + 期限（FileLock 无带超时 API，轮询 tryLock 是标准替代），超时 typed `ERR_STREAM_CHECKPOINT_ERROR` 注明锁竞争细节；同时把 `lock()` 换 `lockInterruptibly()` 保证取消响应。
- **失效面枚举**: 同类无超时等待点逐一登记（8 模块全量）——`MessageSourceFunction:183` await(1s) 有界；`DebeziumCdcSourceFunction:272` await(1s) 有界；`MessageSourceReader:145,200` poll(100ms)/join(5s)+typed 超时；`DebeziumCdcSourceReader:224,302` 同构有界；`AvroSchemaRegistryClient:116`（已立案 C2-002）；`withManifestLock:392,395`（本条）；JDBC 路径 socket 超时由连接池配置承载（平台面，不在本维度重审）；Kafka client 自带 delivery/request 超时。**仅本条与 C2-002 两处无上界**。
- **信心水平**: 确定（等待行为语义确定；触发依赖对端僵死这一外部故障形态）
- **误报排除**: 不属于「低频管理路径粗粒度锁」——commit 是 checkpoint 主路径；不是 FileLock「进程退出自动释放」可豁免——挂死进程不退出，正是 F5 点名的静默挂死形态。
- **复核状态**: 已保留（P2）—— 2026-10-14 独立复核确认，判定理由与现状归属见文末「维度复核结论」；二轮复核：已保留（P2）

### [nop-stream-state-connectors-C2-006] FileTwoPhaseCommitSink.saveState 持 currentBuffer 监视器执行整 epoch 写盘 + fsync——invoke 逐条路径被 checkpoint 写放大阻塞（JDBC 兄弟已示范更轻模式）

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:249-266`（对照兄弟实现 `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:641-651`）
- **证据片段**:
  ```java
  // FileTwoPhaseCommitSink.java:250-264 —— 监视器内做 I/O + fsync
  synchronized (currentBuffer) {
      int count = currentBuffer.size();
      if (count > 0) {
          Path tempPath = tempPath(epochId);
          writeLines(tempPath, currentBuffer);   // 整 epoch 缓冲写盘
          forcePath(tempPath);                    // fsync
          getPendingCommits().put(epochId, ...); currentBuffer.clear();
      }
  }
  // JdbcTwoPhaseCommitSink.java:644-649 —— 兄弟实现：锁内仅拷贝+引用转移，零 I/O
  synchronized (currentBuffer) {
      if (!currentBuffer.isEmpty()) {
          List<DmlOp> batch = new ArrayList<>(currentBuffer);
          getPendingCommits().put(epochId, batch); currentBuffer.clear();
  ```
- **严重程度**: P3
- **并发场景**: 任务线程（invoke 逐条 `synchronized(currentBuffer)` :238-240）vs checkpoint 线程/屏障路径（saveState）
- **触发时序**: 非竞态缺陷，为可量化的锁持有时长问题：大缓冲 epoch（如 100 万行）saveState 时，invoke 在 :238 阻塞至 writeLines+fsync 完成——每 checkpoint 间隔一次、时长 = 缓冲大小 × 磁盘带宽 + fsync；逐条路径的记录延迟出现周期性尖刺。无正确性后果（互斥本身正确且必要）。
- **现状**: JDBC sink 证明「锁内拷贝、锁外写」语义等价（写的是不可变副本，缓冲清空照常）；file sink 把 I/O 留在锁内属实现选择而非约束。
- **风险**: 不影响正确性；checkpoint 间隔内的一次性延迟尖刺，速率敏感场景（配了 rateLimit 的下游）被放大为可观测抖动。
- **建议**: 对齐 JDBC 模式：锁内 `List<String> batch = new ArrayList<>(currentBuffer)` + put pending + clear，锁外 writeLines(batch)+fsync+回填 tempPath 到 pending 条目（注意保持「durable 先于 checkpoint ack」的 R5 CON-04 顺序——fsync 完成后再返回 saveState 即可）。
- **失效面枚举**: 同族逐一登记——`FileTwoPhaseCommitSink.saveState:250`（锁内 I/O，本条）；`FileTwoPhaseCommitSink.invoke:238`（锁内仅 add，安全）；`JdbcTwoPhaseCommitSink.saveState:642`（锁内零 I/O，正例）；`JdbcTwoPhaseCommitSink.invoke:491`（同）；`KafkaTwoPhaseCommitSink.saveState:198-214`（无缓冲锁，事务 RPC 在锁外语义下无监视器，安全）；`FileSourceReader.snapshotState:434`（同族锁内 I/O 见 C2-007，形态不同单列）。
- **信心水平**: 确定
- **误报排除**: 不属于「每次调用都拿锁的粗粒度 synchronized 合理」豁免——豁免针对正确性无关的优雅性，本条给出可量化收益（每 checkpoint 一次 × 缓冲序列化时长）与已存在的同族正例。
- **复核状态**: 已保留（P3）—— 2026-10-14 独立复核确认，判定理由与现状归属见文末「维度复核结论」；二轮复核：已保留（P3）

### [nop-stream-state-connectors-C2-007] FileSourceReader 持 this 监视器执行文件 I/O（openSplit 存在性探测 + 整行聚合读取）——checkpoint 快照线程被单行读取阻塞

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSourceReader.java:202-251,253-323,432-443`
- **证据片段**:
  ```java
  // FileSourceReader.java:206-217 —— this 监视器内：openSplit（Files.exists/channel.size/
  // channel.position）+ readNextLine（至多 8KB/次、整行聚合至 maxLineBytes=16MB）
  synchronized (this) {
      if (activeReader == null) {
          FileSplit next = assignedSplits.poll();
          if (next == null) { requestMoreSplitsIfNeeded(); return Optional.empty(); }
          openSplit(next);                       // 215: I/O in monitor
      }
      line = readNextLine();                     // 217: I/O in monitor
  // :219-227 注释明示这是 AR-15-② 的有意设计（cursor 推进与快照同监视器防撕裂）
  ```
- **严重程度**: P3
- **并发场景**: 任务线程（pollNext 数据路径）vs checkpoint 线程（snapshotState:434 同一 this 监视器）；`addSplits`（交付线程 :171-175）
- **触发时序**: 非竞态缺陷，为 AR-15-② 一致性设计的已知代价面：T_task 在监视器内聚合一条超长行（无终止符，至多 16MB，R5-CON-07 上界）→ T_cp `snapshotState:434` 阻塞至该行读完 → checkpoint barrier 对齐延迟 = 单行读取时长。有界（单行上界 16MB）、周期性（每行一次锁获取但仅 checkpoint 到来时才有争用方）。
- **现状**: 监视器语义承载三重职责（split 队列、cursor 一致性、reader 生命周期），设计注释已声明意图；限速睡眠已正确置于锁外（:144-159 WI5 注释），团队对「锁内不放慢路径」有明确意识，本条为残留面登记。
- **风险**: checkpoint 时延抖动（最坏单行 16MB 的磁盘读取时长）；无正确性影响。
- **建议**: 可选优化——readNextLine 在锁外读入临时行缓冲，仅「cursor 推进 + 行返回」的复合步进入监视器（保持 AR-15-② 的快照原子性不变式：快照只能看到「行已发射」或「行未读」两态之一）。收益有限，接受现状亦合理。
- **失效面枚举**: 同族逐一登记——`FileSourceReader` 的 10 处 synchronized(this)（:171,182,206,241,434,450,467,475,481,488）：仅 :206-249（pollNext）与经由 ：215 的 openSplit 持锁做 I/O，其余 8 处为纯内存操作（安全）；兄弟类 `DebeziumCdcSourceReader`/`MessageSourceReader` 无此形态（队列 poll 均在锁外）。
- **信心水平**: 确定
- **误报排除**: 不报 P2 的理由——设计意图已注释声明（AR-15-②）、后果有界且与 owner doc 已声明的「限速即背压，checkpoint/cancel 时延随速率退化」同一能力边界族；按「缺陷 vs 已声明边界」口径归入 P3 登记而非缺陷。
- **复核状态**: 已保留（P3，已声明边界残留登记，非缺陷主张）—— 2026-10-14 独立复核确认，见文末「维度复核结论」；二轮复核：已保留（P3，触发面精度修正见二轮复核结论）

### [nop-stream-state-connectors-C2-008] RocksDBMetricsRecorder 属性读取失败「一次性告警」声明与实现不符——每次 scrape 重复 WARN

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:87-100`
- **证据片段**:
  ```java
  // :87 注释声明 one-shot，:97-99 实现为每次失败都打
  /**
   * Reads an aggregated long property; returns {@code Double.NaN} (absent
   * gauge) on failure after a one-shot-style warn — ...
   */
  } catch (RocksDBException e) {
      LOG.warn("Failed to read RocksDB property {} for metrics gauge: {}", property, e.toString());
      return Double.NaN;
  }
  ```
- **严重程度**: P3
- **并发场景**: scrape 线程周期性触发（每指标每次抓取一次）
- **触发时序**: 某 RocksDB 构建缺某属性（javadoc 声明的合法形态）→ 每次 `/metrics` 抓取（典型 15-30s）对 6 个 gauge 各打一条 WARN → 恒定低频日志噪声，无限期持续（无抑制标志位）。
- **现状**: 实现与注释意图相悖；影响为日志放大（C7 口径的「循环内 error 日志无抑制」的低频兄弟），有 scrape 频率上界。
- **风险**: 观测噪声掩蔽真实告警；无功能风险。
- **建议**: 增加 `Set<String> warnedProperties`（或 volatile boolean）实现真 one-shot；或把注释改实事求是。
- **失效面枚举**: 同族逐一登记——同方法仅 1 处 catch（:97）；6 个 gauge 共用该方法（同一抑制标志即全覆盖）；模块内无其他「catch 后周期性 WARN」形态（grep `LOG.warn` 于 rocksdb src/main 仅此一处 + `AbstractRocksDBState` 迁移路径非周期性）。
- **信心水平**: 确定
- **误报排除**: 非错误处理风格问题（error-handling 维度不涉）——立案点是「周期性触发路径的日志无抑制」这一长时运行稳定性形态，属 C2/C7 交叉的可观测性代价。
- **复核状态**: 已保留（P3）—— 2026-10-14 独立复核确认，判定理由与现状归属见文末「维度复核结论」；二轮复核：已保留（P3）

### [nop-stream-state-connectors-C2-009] MANIFEST_JVM_LOCKS 静态锁表只增不删——长驻多作业 JVM 缓慢累积

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:96-99,389-391`
- **证据片段**:
  ```java
  // :98-99 —— per outputDir 一把 ReentrantLock，进程生命周期内无任何移除路径
  private static final ConcurrentHashMap<String, ReentrantLock> MANIFEST_JVM_LOCKS =
          new ConcurrentHashMap<>();
  // :390-391 —— computeIfAbsent 只进不出
  ReentrantLock jvmLock = MANIFEST_JVM_LOCKS.computeIfAbsent(
          outputDirPath.toAbsolutePath().normalize().toString(), k -> new ReentrantLock());
  ```
- **严重程度**: P3
- **并发场景**: 长驻 JVM（OpsJobManager 多作业协调进程/TaskManager 长驻进程）内随作业提交不断创建不同 outputDir 的 file sink
- **触发时序**: 非竞态缺陷：每新增一个从未使用过的输出目录 → 静态 map 永久增加一个 ReentrantLock 条目（约 50 字节级）；作业停止后条目不回收。万级历史作业（同进程）≈ MB 级驻留。
- **现状**: F4「无界账本」族的内存内最小实例；增长率 = 不同输出目录数，实际部署下可忽略。
- **风险**: 理论性；仅当同进程长期高频使用一次性临时目录作为 outputDir 时可感知。
- **建议**: 可接受现状；若治理，按「活跃 sink 引用计数在最后一个 copyForSubtask 实例 close 时移除其目录锁」回收（需防移除-再获取竞态，复杂度大于收益，倾向保留）。
- **失效面枚举**: 同族逐一登记——8 模块 static 只增容器 grep 复查：`MANIFEST_JVM_LOCKS`（本条）为唯一 static 缓存型容器；`AvroWireFormat.writerSchemaIds:41`/`AvroSchemaRegistryClient` 双缓存（:36-37）为 per-subject/per-id 有界键空间（subject/id 可枚举、单实例生命周期与 sink/source 绑定），非全局只增；其余模块无 static 集合载体（基线 C1 节交叉印证）。
- **信心水平**: 确定
- **误报排除**: 不按 P1/P2 报——「长时运行必然触发的无界增长」判据要求增长量在实际包络内不可忽略，本条明确不满足，故按校准降为 P3 防御性登记。
- **复核状态**: 已保留（P3）—— 2026-10-14 独立复核确认，判定理由与现状归属见文末「维度复核结论」；二轮复核：已保留（P3）

---

## 已排除项（误报校准对照）

| # | 检查对象 | 排除理由 |
|---|---|---|
| E1 | `CepOperator` 4 处 computeIfAbsent（:772,:781,:988,:1288）与 `SharedBufferAccessor:176` | 全部位于任务线程封闭的普通 LinkedHashMap/局部 map（`CepOperator.java:1011` 注释明示 "All timer bookkeeping is task-thread confined"；TaskMailbox 单线程模型）；mapping function 均为 `new TreeSet<>()`/`new ArrayList<>()`，无 I/O、无递归 |
| E2 | `Lockable.refCounter`（AtomicInteger CAS 环） | lock/release/releaseOrDetach 的 CAS 循环含 over-release 防护，原子性正确；`equals/hashCode` 含可变 refCounter 值是异味，但 Lockable 仅作 MapState **value**（`SharedBuffer.java:136,142`）从不作哈希键，无查找语义依赖 |
| E3 | `SharedBuffer` 双 Guava Cache | Guava Cache 原生线程安全 + 原子 LRU 淘汰（:170-181 注释：替换了旧手写非原子 LruCache——历史缺陷已修）；访问限任务线程 |
| E4 | 2PC 三兄弟的 pendingCommits 访问 | synchronizedMap + 全原子单操作；基类所有迭代点持 PC 监视器（RL-4，`TwoPhaseCommitSinkFunction:158-163,189-203,239-284`），commit(eid) 刻意在锁外调用（阻断 PC→JL/FL 环）；`KafkaTwoPhaseCommitSink:261` 迭代在锁内，纪律一致 |
| E5 | JDBC sink 三处 synchronized(this) DCL | 均配 volatile 发布字段（initialized/keyedColumns/upsertSql/deleteSql），检查-赋值全在临界区内，安全发布成立（:455-475,1031-1049） |
| E6 | `BatchConsumerSinkFunction` 非同步共享缓冲 | 已声明边界：owner doc 能力矩阵 `SINGLE_INSTANCE` + `ParallelismCheckable` 部署期 fail-fast typed 拒绝（:45-80 注释 + 守卫存在）——守卫在、文档有，按规则 6 非缺陷 |
| E7 | `MessageSourceFunction.synchronized(ctx)` 持锁阻塞 put | ctx 监视器专用（序列化 collect 调用），put 可中断且有界队列背压 = 声明语义（owner doc「queue-full = the subscription thread blocks on put」）；等待有界（pollNext 100ms 节拍消费） |
| E8 | `DebeziumCdcSourceFunction` 双 run 防护 | `runEntered.compareAndSet`（:92-94,211-213）是 F10 的正例；latch 快照读模式（:252,:272 先取局部再 countDown）正确处理 initCompletionLatch 换锁竞态 |
| E9 | `FileSplitEnumerator` 并发访问 | 全 CHM 状态 + round-robin/size-aware 均为按 subtask 确定性不相交分片，各 reader 线程只写自己的子集（:172-225 推演）；`addReader`/`handleSplitRequest` 同 subtask 串行于该 subtask 自己的任务线程 |
| E10 | `FileTwoPhaseCommitSink` check-then-act 提交守卫（:312-349） | 撞键判别依赖 `recoveredEntryKeys`（ConcurrentHashMap.newKeySet），整个判别在 manifest 双锁临界区内执行，无窗口 |

## 覆盖清单与缺口

- **逐文件精读**（锁载体 + 全部迭代点/等待点核对）：FileSourceReader、FileTwoPhaseCommitSink、FileSplitEnumerator、MessageSourceFunction、MessageSourceReader、JdbcTwoPhaseCommitSink（锁相关段落全覆盖）、KafkaTwoPhaseCommitSink（全文）、DebeziumCdcSourceReader（全文）、DebeziumCdcSourceFunction（run/cancel/latch 段）、DebeziumTableSplitEnumerator（全文）、RocksDBMetricsRecorder（全文）、RocksDBKeyedStateBackend（结构 + open/close/快照路径）、CepOperator（computeIfAbsent 四站点 + timer ledger）、SharedBuffer/SharedBufferAccessor/Lockable、AvroWireFormat/AvroSchemaRegistryClient（全文）
- **grep 全量盘点**：140 文件的锁/原子/volatile/等待原语两轮扫描（方法见检查范围说明），无遗漏锁载体
- **跨模块证据引用**（不作为发现对象，仅用于时序成立）：LocalSourceCoordinator、TwoPhaseCommitSinkFunction、SourceReaderOperator、TaskManager、DebeziumEngineWrapper（nop-message，用法已核对称）
- **缺口**：① core 侧 enumeratorLock 分发缺口的根治属 core 审计范围，本报告仅报受害者侧；② 并发测试保护力（变异推演）属 C10 维度，本报告不重复；③ JDBC 连接超时配置面归 C6/C7。

# C2 维度深挖第 2 轮追加：锁与同步原语（nop-stream-state-connectors）

- **审计维度**: C2（锁与同步原语）——第 2 轮深挖追加
- **与第 1 轮的关系**: 接续 `C2-locks-sync.md`（11 边锁序图无环、18 文件精读、C2-001~009）。本轮只输出**新发现**，序号自 C2-010 起续编；对第 1 轮已立案项零重复。
- **盲审边界**: 本轮仅读取本维度第 1 轮文件与 live code；未读 ai-dev/audits/ 其他目录、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/
- **审计日期**: 2026-10-14
- **结果概要**: 新发现 P1=1（C2-010），无 P0；其余深挖面全部收敛为排除项 E11–E17 → **达到终止信号**（无更多可具体化发现）

---

## 深挖第 2 轮追加

## 本轮深挖范围（第 1 轮盲区清单）

| 盲区 | 本轮动作 | 结论 |
|---|---|---|
| `RocksDBKeyedStateBackend` 全文 1068 行（第 1 轮仅结构 + open/close/快照路径） | 全文逐行锁面复查 | 新发现 C2-010（REST 面跨线程快照）；其余面见 E17 |
| `nop-stream-rocksdb` 其余文件（IncrementalSnapshotStrategy/Restore、AbstractRocksDBState、KeyEncoder、StateBackend 工厂） | 全读/静态扫描 | 无锁载体新增，E16/E17 |
| `MessageTopicSplitEnumerator`、`MessageSinkFunction`（第 1 轮未盘点） | 全读 | 无共享可变状态，E12/E13 |
| `KafkaSourceFunction`（第 1 轮仅标注 safe 未给推演） | 全读 + 快照线程模型核实 | E11（mailbox 串行化成立） |
| `BatchLoaderSourceReader`/`BatchLoaderSplitEnumerator`（第 1 轮仅 launch 家族排除） | 全读 + 投递判别推演 | E15（parallelism==1 双层门禁下任务线程封闭） |
| synchronized 集合迭代点补查 | 全 8 模块重 grep `Collections.synchronized` | 零命中（synchronizedMap 仅基类 pendingCommits，第 1 轮 RL-4 已全迭代点核对） |
| `computeIfAbsent` 站点补查 | 全 8 模块重 grep | 10 站点全部对账入册（E1×5、C2-002×2、C2-009×1、C2-003×2），无遗漏 |

**新增线程模型事实**（本轮推演依据）：legacy `CheckpointedSourceFunction.snapshotState` 经 `StreamSourceOperator.offerBarrier` 的 **mailbox handoff 在任务线程执行**（`nop-stream-core/.../StreamSourceOperator.java:195-197`："the mail runs snapshotState + emitBarrier on the task thread"）；而 WI14 queryable-state REST 面把**同一个** `backend.snapshotState()` 派发到 ops HTTP 固定 4 线程池（`StreamOpsHttpServer.java:126-127`）——同一方法两个入口线程，一个有 mailbox 串行化、一个没有任何同步。这一不对称是 C2-010 的成立基础。

---

## 发现条目

### [nop-stream-state-connectors-C2-010] WI14 keyed-state REST 在 HTTP 线程执行 RocksDBKeyedStateBackend.snapshotState——与任务线程并发违反单线程契约，TTL sidecar 裸 HashMap 构成三点竞态面

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:797-836`（cleanupExpiredEntries :797-824 + snapshotState :826-836）、`:90-92`（单线程契约 javadoc）、`:146,603-617`（states LinkedHashMap 及任务线程 lazy put）；竞态对侧证据 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/TtlContext.java:57,112-133,175-192`、`nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBValueState.java:67,69,93`；派发链证据 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/StreamTaskInvokable.java:607-636`（:623 无锁调用 backend.snapshotState）← `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:149-161` ← `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamOpsHttpServer.java:126-127,234-283,329-338`
- **证据片段**:
  ```java
  // RocksDBKeyedStateBackend.java:90-92 —— 契约声明
  // The backend assumes single-threaded access (mailbox model), matching the
  // memory backend. RocksDB native handles are not thread-safe and do not need
  // synchronization under this assumption.
  // :799/:808 —— REST 线程可抵达的迭代 + sidecar 变异（snapshotState 两条路径都先执行）
  for (Object stateObj : states.values()) {            // 799: 裸 LinkedHashMap 迭代
      ...
      total += ctx.sweepExpired(baseKeyBuf -> { ... db.delete(cf, baseBytes); ...}); // 808
  // :826-835 —— snapshotState 两个入口共享此方法，无任何线程守卫
  public StateSnapshot snapshotState() throws Exception {
      if (incrementalCheckpointEnabled) { cleanupExpiredEntries(); return snapshotIncremental(); }
      cleanupExpiredEntries();                           // 834
      return RocksDBSnapshotSerDe.snapshotState(this);   // 835（SerDe :85/:107 迭代 states）
  ```
  ```java
  // TtlContext.java:57 —— 任务线程与 REST 线程共享的裸 HashMap sidecar
  private final Map<K, Long> timestamps = new HashMap<>();
  // :175-192 sweepExpired = iterator-remove 变异；:112-133 grantFreshWindow/recordWrite = 任务线程 put
  // RocksDBValueState.java:67,69,93 —— 每条记录的 TTL 访问都在任务线程写 sidecar
  ```
  ```java
  // StreamOpsHttpServer.java:126-127 + 254 —— HTTP 固定 4 线程池直达后端，无 mailbox 编组
  httpServer.setExecutor(Executors.newFixedThreadPool(4, ...));
  ... taskManager.keyedStateSnapshot(jobId, vertexId, subtaskIndex);   // 254
  ```
- **严重程度**: P1
- **并发场景**: 三个互不排斥的并发源——① ops HTTP worker（固定池 4 线程，`GET /jobs/{id}/vertices/{id}/subtasks/{n}/state`）vs 该 subtask 的任务线程（每条记录的 TTL put + lazy 状态注册）；② 两个并行 HTTP 请求彼此之间（同一 backend 的 snapshotState 重入）；③ HTTP 线程 vs 任务收尾线程的 close()（:979-1067 置 db=null）。对照：checkpoint 路径的同一方法经 mailbox 在任务线程串行（StreamSourceOperator:195-197），REST 路径是唯一无串行化的入口。
- **触发时序**: 面①（TTL 状态）：T_http 执行 `snapshotState:834 → cleanupExpiredEntries:808 → TtlContext.sweepExpired:184` 正在 iterator-remove 遍历 `timestamps` HashMap → T_task 处理下一条记录，`RocksDBValueState:67 ttl.grantFreshWindow(keyBuf)` 对同一 HashMap put → CME（抛在 HTTP 线程，查询 500）或 HashMap 桶结构破坏（JDK21 无死循环但有条目丢失/错链）→ TTL 时间戳丢失的条目**永不过期**（状态无界增长，C7 族）或被误删（未过期数据被 TTL 提前清除——状态语义违约）。面②（lazy 注册交叠）：T_http 在 `:799`（或 SerDe `:107`）迭代 `states` LinkedHashMap → T_task 首次 `getXxxState` 走 `getOrCreateState:612 states.put` 结构性插入 → HTTP 线程 CME；更坏分支：插入恰逢迭代中 rehash，LinkedHashMap 内部链破坏 → 后续**任务线程**的 getState 在损坏桶上异常/挂死（任务崩溃，非仅查询失败）。面③（自对偶）：两个 HTTP 请求并发进入同一 backend → 两个 sweepExpired 并发 iterator-remove 同一 HashMap → CME/丢失，**无需任务线程参与**（固定池 4 线程使两次并发查询是常态能力而非构造）。
- **现状**: 后端对跨线程访问零防线——契约只写在 javadoc（:90-92）与缓存注释（:514,:529 "single task-thread access"），无运行时守卫；派发链（TaskManager:158 → invokable:623）直通无 mailbox 编组、无 busy 拒绝。语义层附加问题：端点声明 "read-only"（StreamTaskInvokable:609 javadoc、StreamOpsHttpServer:247 "read-only keyed-state snapshot"），但 `cleanupExpiredEntries` 使每次 GET **删除** RocksDB 条目（TTL 过期项）——读端点携带写副作用。
- **风险**: 运维轮询 keyed-state（该端点的存在目的即"查询运行中 subtask"）× TTL 状态或状态懒注册交叠 → 静默状态损坏（永不过期/提前清除/任务挂死）。增量路径下面③叠加违反 F-02 retention 的 `maxConcurrentCheckpoints=1` 前提（RocksDBIncrementalSnapshotStrategy:113-120 注释明示 K=2 覆盖"last durable + one in-flight"）：并发第二快照的 pruneOldCheckpoints 可删掉在途快照结果已引用的 cp-N 目录 → 同 JVM restore 解析 nonSstDir 失败。
- **建议**: 三层取其二：① 根治（core 派发侧）——`TaskManager.keyedStateSnapshot` 改经 mailbox 编组（对齐 `offerBarrier` 的 `exec.getMailbox().put(Mail.control(...))` 既有模式，StreamSourceOperator:197），快照与数据面天然串行，同时消除面①②③；② 受害者自守（本模块）——`TtlContext.timestamps` 改 `ConcurrentHashMap` 且 sweepExpired 对 CHM 迭代安全（javadoc :163-167 "iterator-remove 不会 CME" 的论断仅在单线程下成立），`states` 改 `ConcurrentHashMap`（迭代弱一致，快照撕裂可容忍为缺一条目）；③ 守卫（可选项）——backend 加 `AtomicBoolean snapshotInProgress` CAS，二次进入 typed 拒绝（消除面③与 retention 违约）。建议 ① + ② 组合，②的 TTL 语义面需同时修正 sweepExpired 注释。
- **失效面枚举**: 同一入口的同族调用面逐一登记——`RocksDBKeyedStateBackend.snapshotState` 两个入口：checkpoint 路径（`AbstractStreamOperator.java:308`，mailbox 任务线程，**安全**）与 REST 路径（`StreamTaskInvokable.java:623`，**缺陷本体**）。REST 路径触及的可变面全清单：`states`（:146，缺陷面②）、`TtlContext.timestamps` × 全部 TTL 状态实现（RocksDBValueState/MapState/ListState/ReducingState/AggregatingState/Internal* 均经 `applyTtl:722-739` 绑定同一 TtlContext 类，缺陷面①）、`restoreAggregateFunctions`/`stateTypes`/`restoredDescriptors`（仅 restore 时段单线程访问，安全）、`cfHandles`（REST 快照只读迭代 SerDe:447，任务线程 getOrCreateColumnFamily 写——与面②同构的次级窗口，随 ② 的 CHM 化一并消除）。跨模块兄弟：`MemoryKeyedStateBackend.snapshotState/cleanupExpiredEntries`（nop-stream-core:243,257）经同一 REST 入口暴露同一形态——不在本报告对象内，建议合并裁定时报送持有 nop-stream-core 范围的维度。
- **信心水平**: 确定（交错序列为代码级必然；触发条件是 REST 查询与任务处理的常态交叠，无需罕见故障形态）
- **误报排除**: 不属于「无状态单例被并发调用是设计意图」——缺陷实体是后端内部可变集合与 sidecar map，非注入依赖；不适用「平台基础设施自身不重审」——缺陷实体（RocksDBKeyedStateBackend 的无守卫快照路径）在本审计对象内，core/runtime 仅作派发链证据引用；也不属于「低频管理路径粗粒度锁合理」——后果是状态语义损坏与任务挂死，非优雅性问题，且触发源是周期性运维轮询。
- **复核状态**: 已保留（P1，受害者侧登记）—— 2026-10-14 独立复核确认；派发根因已被 WI1.1 保留（C3-009/P2），见文末「维度复核结论」；二轮复核：已保留（P1，全链派发/受害两侧逐点复验成立）

---

## 本轮新增排除项（误报校准对照，接第 1 轮 E1–E10 编号）

| # | 检查对象 | 排除理由 |
|---|---|---|
| E11 | `KafkaSourceFunction.restoredOffsets`（volatile TreeMap :115） | 第 1 轮仅标注 safe，本轮补全推演：`markConsumed:320-325`（任务线程 put）与 `snapshotState:338-348`（迭代序列化）经 StreamSourceOperator mailbox handoff 同在任务线程串行（:195-197）；`initializeState:352-369` 在 run 前单线程；`currentOffsets:388-391` 全仓无生产调用方（仅测试可见性钩子）。volatile 布尔（running/drainRequested/rateLimit*）为单向配置传播，安全。 |
| E12 | `MessageTopicSplitEnumerator`（第 1 轮未盘点） | 无共享可变状态：仅 final `topic` + transient `context`；每次 `deliverToken` 新建 `MessageTopicSplit`，无集合字段。**非 C2-003 同族缺陷实例**（C2-003 的失效面枚举可补注此条）。 |
| E13 | `MessageSinkFunction` | 仅 final 字段（messageService/topic），`consume` 无状态。 |
| E14 | `NopStreamOffsetBackingStore`（nop-message-debezium，C2-003/C2-004 时序的证据组件，本轮补核线程安全） | 底层 `data` 为 ConcurrentHashMap（:63），`getOffsets:211-218` 弱一致迭代对引擎线程 `set:165-178` 的并发 put 安全（无 CME）；`setOffsets:189-205` 的 REPLACE（clear+put）仅发生在 initializeState（引擎启动前，任务线程）。静态 REGISTRY 为 CHM，computeIfAbsent 无重逻辑。快照可能取到弱一致瞬时集（个别分区旧 offset）→ REPLAYABLE 重放语义可容忍，非缺陷。 |
| E15 | `BatchLoaderSourceReader` 投递判别 + `BatchLoaderSplitEnumerator.assigned`（第 1 轮仅排除 launch 家族） | `assigned:28` 为裸布尔且被 addReader/handleSplitRequest 双入口写，但 BatchLoader 强制 parallelism==1（部署期 `ParallelismCheckable` + reader 侧 `start():87-90` belt-and-braces typed 拒绝，双层守卫）→ 单 reader 单任务线程，竞态被声明边界+守卫阻断（规则 6）。附带登记一处潜在设计张力（不立案）：`addSplits:106-120` 以 `started` 判别首/二次投递，post-start 到达的首次投递会 typed 抛 secondAssignmentError，与 `pollNext:127-133` 的"late arrival must still be adopted"防御注释相矛盾；在声明部署（parallelism==1、单线程投递序）下该路径不可达，且 `ReaderChannel.requestSplits:255-267` 对枚举器异常 LOG.warn 吞掉（F9 形态，实体在 core）——仅当未来放开并行度或投递时序改变时升级，本轮按「理论风险，未具体化」不立项。 |
| E16 | `RocksDBIncrementalSnapshotStrategy`/`RocksDBIncrementalRestore` | 零共享 Java 可变状态（每次快照新建实例 + cp-{id} 隔离目录）；Restore 为静态工具、restore 时段单线程。retention 剪枝（:154-185）在快照串行化前提下正确（K=2 注释自证）；其前提被 C2-010 面③破坏的后果已并入 C2-010 风险字段，不重复计数。 |
| E17 | `RocksDBKeyedStateBackend` 全文锁面复查（1068 行逐行） | 除 C2-010 外无新锁面缺陷：`buildStorageKeyForCurrent` 单槽缓存（:515-542）任务线程封闭（:514/:529 注释自证）；`close():979-1067` 释放序正确（recorder→cfHandles→defaultCF→db→WBM→cache→filter→options）且容错收敛（suppressed）；`getOrCreateColumnFamily:339-354` check-then-act 仅任务线程 + restore 时段可达；`incrementalSnapshotIdCounter` AtomicLong 正确；无 synchronized/无嵌套锁 → 锁序图无边可增，第 1 轮 11 边无环结论维持。 |

## 覆盖清单与缺口（第 2 轮）

- **本轮新增精读**：RocksDBKeyedStateBackend（全文 1068 行）、RocksDBIncrementalSnapshotStrategy（全文）、RocksDBIncrementalRestore（锁面扫描）、MessageTopicSplitEnumerator（全文）、MessageSinkFunction（全文）、KafkaSourceFunction（全文）、BatchLoaderSourceReader（全文）、BatchLoaderSplitEnumerator（全文）、NopStreamOffsetBackingStore（全文）、TtlContext（sweep/grant 段）、AbstractRocksDBState/KeyEncoder（静态面扫描）
- **本轮新增跨模块证据引用**（不作为发现对象）：StreamTaskInvokable.keyedStateSnapshot、TaskManager.keyedStateSnapshot、StreamOpsHttpServer（executor/路由/handler）、StreamSourceOperator.offerBarrier（mailbox 串行化事实）、CheckpointBarrierTracker.triggerCheckpoint、LocalSourceCoordinator（ReaderChannel.requestSplits）
- **grep 复核**：`Collections.synchronized*` 全 8 模块零新增；`computeIfAbsent` 10 站点全部对账入册无遗漏
- **缺口**：① C2-010 的根治层（mailbox 编组派发）属 nop-stream-core/nop-stream-runtime 审计范围，本报告仅报受害者侧并建议报送；② MemoryKeyedStateBackend 同形 exposure 属 core 范围，同上报送；③ 并发测试保护力（变异推演）属 C10 维度。
- **终止信号判定**：本轮 1 条新发现（C2-010）已完整立案；其余深挖面全部收敛为排除项或按规则 6 /「理论风险未具体化」不立项，无可继续深挖的具体化疑点 → **C2 维度深挖终止**。

# C2 维度深挖第 3 轮追加：锁与同步原语（nop-stream-state-connectors）

- **审计维度**: C2（锁与同步原语）——第 3 轮深挖追加
- **与第 1/2 轮的关系**: 接续 `C2-locks-sync.md`（C2-001~009、锁序图 11 边无环）与 `C2-round2.md`（C2-010 + E11–E17）。本轮只输出**新发现**，序号自 C2-011 起续编；对前两轮已立案项零重复。
- **盲审边界**: 本轮仅读取本维度第 1/2 轮文件与 live code；未读 ai-dev/audits/ 其他目录、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/
- **审计日期**: 2026-10-14
- **结果概要**: 新发现 P2=1（C2-011，/metrics ops 面 gauge 注册身份缺陷——C2-001 同代码的非竞态兄弟面）；无 P0/P1；C2-010 同族 ops 面全量对账收敛 + 前两轮未盘点角落全部闭合为排除项 E18–E24 → **维持终止信号**

---

## 深挖第 3 轮追加

## 本轮深挖范围

**方向一：C2-010 同族锁面——全部经 HTTP/运维面触达共享状态的入口对账。** 以 `StreamOpsHttpServer.java`（nop-stream-runtime，:131-153 路由注册）为入口清单做穷举，逐族判定其触达的 8 模块内共享状态：

| ops 端点族 | 触达的 8 模块状态（受害者侧） | 判定 |
|---|---|---|
| `GET /metrics`（scrape 线程，周期性） | `RocksDBMetricsRecorder` 6 个 gauge valueFunction（:83，scrape 线程经 micrometer 回调执行）+ `CepOperator.numLateRecordsDropped` Counter（:438） | 竞态面 C2-001 已立案；**本轮新立确定性注册面 C2-011**；CepOperator Counter 安全（E19） |
| `GET /jobs/{id}/vertices/{v}/subtasks/{n}/state`（HTTP 固定 4 线程池直通） | `RocksDBKeyedStateBackend.snapshotState` + `RocksDBSnapshotSerDe` 全链 | C2-010 已立案；本轮全文精读 SerDe（746 行）确认无 C2-010 之外的新面（E20） |
| `GET/POST /jobs`、`GET /jobs/{id}`、`/{id}/checkpoints`、`POST /{id}/stop`、`GET /{id}/threaddump` | 仅 runtime 侧实体（JobRegistry/JobCoordinator/OpsJobManager/CheckpointCoordinator metrics/ThreadMXBean），不触达 8 模块内状态 | 实体在 nop-stream-runtime，非本报告对象；无受害者侧可立案 |

同族结论：**经 HTTP/ops 面可达的 8 模块内共享状态恰为两个（metrics gauge 面与 keyed-state 面），全部已入册**；keyed-state 执行体 SerDe 为无状态静态工具，不产生第三个面。

**方向二：排除项之外的前两轮未盘点角落。** 逐文件对账（文件清单 vs 前两轮覆盖清单差集）：CEP 模块 nfa/time/pattern/model 包及 `CepOperator` 定时器面、rocksdb 模块全部 serde/encoder/factory 小文件、connector 各工厂/support 类、`BatchLoaderSourceFunction`、`NFAStateNameHandler`、`SharedBufferCacheConfig`——结论见发现条目与 E18–E24。

**新增线程模型事实**（本轮推演依据）：`TaskProcessingTimeService`（nop-stream-core，:27-47 javadoc + :113-132 实现）的定时器回调**从不**在 scheduler 线程执行——driver 线程仅判定到期并向任务线程 `TaskMailbox` 投递 fire mail，回调在任务线程的 `fireDueTimers` 中执行；driver 线程唯一的跨线程读是 volatile `nextTimerTimestamp`（:101-103）。这意味着 CepOperator 的 cacheStats 定时器（第 1 轮未显式清查的 ScheduledFuture.cancel 面）整个生命周期都在任务线程上串行。

---

## 发现条目

### [nop-stream-state-connectors-C2-011] RocksDB gauge 以 Tags.empty() 注册进进程级共享 registry 且 close 无移除——多 subtask 静默去重吞掉 N-1 份注册，首个 backend 关闭后指标永久 NaN（C2-001 同代码的非竞态兄弟面）

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:34-39`（6 个固定 meter 名，无任何 subtask/job 维度）、`:73-84`（register + Tags.empty()）、`:65-67`（close 仅置 dbRef=null，无 meter 移除）、`:52`（dbRef AtomicReference）；注册/关闭调用点 `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:278-279,988-989`；共享载体 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/metrics/StreamMetricsRegistries.java:23-26`（进程级 static CompositeMeterRegistry，全仓唯一移除路径是 server stop 的 removeMember）
- **证据片段**:
  ```java
  // RocksDBMetricsRecorder.java:82-84 —— Meter.Id = 固定名 + Tags.empty()，
  // 每个 per-subtask backend 的注册产生完全相同的 Id
  private void gauge(MeterRegistry registry, String meterName, String property) {
      registry.gauge(meterName, Tags.empty(), dbRef, ref -> readProperty(ref.get(), property));
  }
  // :65-67 —— close 只摘引用，不摘 meter；gauge 与 valueFunction 永久留在 registry
  public synchronized void close() { dbRef.set(null); }
  // StreamMetricsRegistries.java:23-25 —— 全进程单一静态 registry，无 per-job/per-subtask 作用域
  private static final CompositeMeterRegistry COMPOSITE = new CompositeMeterRegistry(...);
  ```
- **严重程度**: P2
- **并发场景**: 三个写方共享同一进程级 registry——① LOCAL 模式同 JVM 的 N 个 subtask 任务线程在各自 `backend.open`（`RocksDBKeyedStateBackend.java:278-279`）并发注册同一批 Meter.Id；② 作业停止/失败重启线程执行 `backend.close → recorder.close`（:988-989）；③ `/metrics` scrape 线程周期读取。注册路径本身是 micrometer `getOrCreateMeter` 的原子 check-then-put，无数据竞争——缺陷是**注册身份恒等 + 移除缺失**，使共享 registry 的语义在该部署形态下静默失效。
- **触发时序**: 面①（多 subtask）：T1（subtask 0 任务线程）`backend.open` 注册 6 个 gauge（Id = `nop.stream.state.rocksdb.*` + 空标签，valueFunction 绑定 recorder-0.dbRef）→ T2（subtask 1 任务线程）`register()` 传入相同 Id 与绑定 recorder-1 的 valueFunction → micrometer 按 Id 命中已有 meter，**新 valueFunction 静默丢弃**（标准去重语义，无日志无异常）→ subtask 1..N-1 的 RocksDB 状态从此不可观测。面②（重启，确定性，无需并发交叠）：任一作业周期结束 → recorder-0.close() 置 dbRef=null → 幸存 gauge 每次 scrape 读 null → NaN（:92-94）→ 后续重启的新 backend register 再次同 Id 去重 → gauge 永久 NaN，进程存活期内不可自愈。
- **现状**: 注册面无 per-subtask/job 标签（对照同仓库正例：`CepOperator.java:434-440` 的 drop counter 带 `operator`/`subtask` 双标签 + 实例身份 fallback，R5-CEP-03 修复时已确立 tag 纪律）；recorder close 无 `registry.remove`；`StreamMetricsRegistries` 无按 job 移除的 API。C2-001 修的是同一批 gauge 的锁外求值竞态，与本条（注册身份/生命周期）互不覆盖——两缺陷独立成立。
- **风险**: owner doc 声明的 `/metrics` 观测面在两种**常见**部署形态下静默失效：parallelism>1 只暴露 1/N subtask；长驻协调进程内任何一次作业停止/重启后 RocksDB 族全部 NaN。无错误码、无日志（F9 邻族的静默形态——不是异常被吞，而是去重语义本身不可见），监控侧只会看到"指标恒 NaN"，误判为无状态数据。
- **建议**: ① gauge 名补 per-subtask/job 标签（对齐 `CepOperator` R5-CEP-03 既有模式，tag 取自 TaskLocation，fallback 实例身份）——使各 backend 注册 Id 唯一，去重不再吞注册；② `recorder.close()` 持有 6 个 `Meter.Id`（或 Meter 引用）调用 `registry.remove(...)`，阻断"close 后幸存 gauge 读 null"路径；①+② 组合后 C2-001 建议的锁内求值改造仍独立适用。改动全部限于本模块 recorder + backend 传参。
- **失效面枚举**: 8 模块全部 meter 注册点逐一登记——`RocksDBMetricsRecorder.register:73-79` 6 个 gauge（**缺陷本体**，Tags.empty() + 无移除）；`CepOperator.java:438-440` drop counter（**正例**：Counter 原语线程安全 + operator/subtask 标签使 Id 唯一，去重不吞注册；跨重启同 Id 计数持续累积属 C7 口径且为 R5-CEP-03 声明设计，不立案）；grep 全 8 模块（cep/connector×5/format-avro/rocksdb src/main）无其他 `MeterRegistry` 使用点。runtime 侧 `StreamMetricsReporter` 的注册面不在本报告对象内，建议合并裁定时报送持有 nop-stream-runtime 范围的维度核对同族形态。
- **信心水平**: 确定（micrometer 同 Id 注册返回既有 meter 并丢弃新 valueFunction 是文档化标准行为；静态单一 registry、per-subtask backend、无移除路径均为代码级事实；keyed-state 端点按 subtaskIndex 寻址印证 backend per-subtask）
- **误报排除**: 不属于「有界声明的缓存/registry 不是问题」——问题不是容量而是 Id 恒等导致的静默去重与生命周期不对称（F8：register 无配对 remove）；不是 C2-001 的重复——C2-001 需要 close 与 scrape 的时序交叠（竞态），本条在零并发下即可确定性复现（面②）且修复互不覆盖；不适用「平台基础设施自身不重审」——缺陷实体（recorder 的空标签与无移除）在 8 模块内，core 的 registry 仅作共享载体证据；也不是「低频管理路径」——/metrics 是 owner doc 声明的进程级 pull 端点，触发源是其设计目的本身。
- **复核状态**: 已保留（P2）—— 2026-10-14 独立复核确认，判定理由与现状归属见文末「维度复核结论」；二轮复核：已保留（P2）

---

## 本轮新增排除项（误报校准对照，接第 1/2 轮 E1–E17 编号）

| # | 检查对象 | 排除理由 |
|---|---|---|
| E18 | `CepOperator.cacheStatsTimerFuture`（:267,:626-680）——自旋重挂定时器的 cancel/re-arm 跨线程面 | 第 1 轮「CEP 任务线程封闭」未显式清查此面，本轮补全：`registerCacheStatisticsTimer`（open，任务线程）、`onCacheStatisticsTimer` 的 re-arm（:664，经 TaskProcessingTimeService fire mail 在任务线程执行）、`releaseCacheStatisticsTimer` 的 cancel（:675-680，close，同任务线程）三者经 mailbox 全序串行，无交错；driver 线程仅读 volatile `nextTimerTimestamp`。cancel(false) 后不可能再有 re-arm 写入（回调与 close 同线程）。非竞态。 |
| E19 | `CepOperator.numLateRecordsDropped`（:434-440） | micrometer Counter 原语线程安全；`operator`/`subtask` 双标签（TaskLocation 来源 + 实例身份 fallback）使 Meter.Id 唯一——与 C2-011 同族的正例对照。scrape 读与任务线程 increment 经 Counter 内部原子性安全。 |
| E20 | `RocksDBSnapshotSerDe` 全文精读（746 行，C2-010 执行体，前两轮仅引用行号） | 无状态静态工具类（私有构造 + 全静态方法，全部上下文经参数传入）：snapshot 路径的 `states` 迭代（:85,:107）、cfHandles 迭代（:447）、`backend.getDb()` 读均已并入 C2-010 失效面；restore 路径（:356-428）时段单线程（任务初始化）；局部 Map 均方法内新建。无锁载体、无静态可变状态、无新面。 |
| E21 | `RocksDBValueSerDe`/`RocksDBKeyEncoder`/`RocksDBStateBackend`/`RocksDBOptionConfig`/`RocksDbTtlAware` | 全部为常量 + 静态无状态工具（KeyEncoder 仅 `static final` 字节数组/版本号；ValueSerDe 全静态方法；StateBackend 工厂仅 serialVersionUID；OptionConfig/TtlAware 为配置/接口）。无锁载体、无共享可变状态。 |
| E22 | `BatchLoaderSourceFunction`（前两轮仅覆盖同族 Reader/Enumerator/BatchConsumerSink） | `running`/`currentOffset` 均 volatile 且 javadoc 声明线程模型（:76-81：seek/getCurrentOffset 与 run 分属 operator/checkpoint 线程与任务线程，volatile 防无限期 stale）；checkpoint 快照经 mailbox 在任务线程串行（与 E11 同构）；parallelism==1 双层守卫（E15 同族已声明边界）。`run` 开头 `running=true` 重置为区域重启语义（注释声明）。无新面。 |
| E23 | CEP/connector 剩余未点名文件：`NFAStateNameHandler`、`CdcEventOps`、`DebeziumSourceSupport`、Source 工厂类（`MessageSource`/`FileSource`/`BatchLoaderSource`/`DebeziumCdcSource`）、`SharedBufferCacheConfig`、各 ConnectorFactory | `NFAStateNameHandler`：实例级 `usedNames` HashSet（:40，非 static），仅在 pattern 编译期单线程使用；`CdcEventOps`/`DebeziumSourceSupport`：静态 facade/常量，无状态；Source 工厂类无可变字段，唯一例外 `DebeziumCdcSource.initialOffsets`（:44）为部署前 setter 写入、`createReader` 部署装配单线程读取，部署后只读——非运行时并发面。 |
| E24 | 6 个 connector/format 模块锁载体与 F5 等待点的独立复核 grep（第 3 轮自证，不依赖前两轮基线） | 锁载体清单与前两轮完全一致：`MessageSourceFunction:158` synchronized(ctx)（E7）、`FileSourceReader` ×10 synchronized(this)（C2-007 已逐点枚举）、`FileTwoPhaseCommitSink` :211 FILE-this DCL + currentBuffer ×4（:238,:250,:414,:695）+ JL/FL（C2-005/006/009）、`JdbcTwoPhaseCommitSink` DCL ×3（:461,:1031,:1039，E5）+ CB-J ×3（:491,:642,:742）、`KafkaTwoPhaseCommitSink:261`（E4）。等待点全对账：poll(100ms)/join(5s)×2、await(1s)×2、Kafka consumer.poll(200ms)、`AvroWireFormat:70,97` EncoderFactory/DecoderFactory（线程安全工厂 + per-call 编解码器）——无新增无上界等待点（C2-002/C2-005 仍为仅有的两处，前轮已立案）。 |

## 覆盖清单与缺口（第 3 轮）

- **本轮新增精读**：StreamOpsHttpServer（全文 549 行，ops 端点穷举）、RocksDBSnapshotSerDe（全文 746 行）、RocksDBMetricsRecorder（register/close 段复读）、TaskProcessingTimeService（全文，线程模型事实）、BatchLoaderSourceFunction（全文）、NFAStateNameHandler（全文）、DebeziumCdcSource（initialOffsets 面）、StreamMetricsRegistries（全文）
- **本轮新增 grep 自证**：8 模块锁载体全量（6 connector/format 模块 + cep nfa/time/pattern/model + rocksdb 全部）与 F5 等待点全量（E24 对账表）；`MeterRegistry|gauge\(` 全 8 模块（C2-011 失效面枚举依据）
- **终止信号判定**：C2-010 同族 ops 面穷举完毕（8 端点族 → 2 个在范围内受害者面，全部入册）；前两轮未盘点文件差集全部闭合为 E18–E24；本轮唯一新发现 C2-011 已完整立案且为 P2 观测面缺陷（非锁面新竞态）。此后无可继续具体化的 C2 疑点 → **C2 维度深挖终止（维持第 2 轮判定，以本条新发现为最终追加）**。
- **缺口**：① C2-011 修复建议中 per-subtask tag 的 TaskLocation 取值链在 runtime 侧（`copyForSubtask` 传参），实施时需与持有 runtime 范围的报告对齐；② `StreamMetricsReporter`（runtime）与 `MemoryKeyedStateBackend` 的 ops 面同族核对属 core/runtime 范围，沿用第 2 轮报送建议。

---

# C2 维度复核结论（独立复核子代理，2026-10-14）

- **复核性质**: 本轮为**解除盲审**的独立复核——可读历史审计（WI1.1 core-runtime 并发审计、R5/R6 quality 审计、productization 验收）与 git log，为每条保留发现标注现状归属。
- **复核方法**: 11/11 条逐条重读 live code（缺陷文件全文或锁/等待相关段落）+ 关键调用方验证（`LocalSourceCoordinator`、`SourceReaderOperator.wireCoordinatorChannel`、`KafkaSourceFunction` 子任务复制链、`StreamOpsHttpServer→TaskManager→StreamTaskInvokable` 派发链）；判定口径：并发发现须能写出具体触发时序，已声明边界+运行时守卫不立案。
- **判定结果**: **已保留 11 / 降级 0 / 驳回 0**（P1×4、P2×2、P3×5 全部维持初审/深挖定级）。
- **非阻塞观察**: 第 3 轮头部引用的 `C2-round2.md` 实际不存在独立文件（第 2/3 轮追加已合并入本文件），属文本引用瑕疵，不影响内容，未改动其他正文。

## 逐条判定

- **C2-001 —— 已保留 P1**。复验成立：`gauge:83` 的 `ref.get()` 确在 `synchronized readProperty` 之外求值；`RocksDBKeyedStateBackend.close`（:988-995）先 recorder.close 置 null、随后 `db.close()` 释放原生句柄，scrape 线程持 stale 非 null 引用进入临界区读已释放句柄——交错序列代码级必然，javadoc 与 backend 注释（"SIGSEGVs in native code"）自证后果，3 行修复建议成立。**现状归属**: plan-2216-1（roadmap item 16，commit c6dbc769fe）引入后零修改，历史审计未覆盖该 facet（R6 仅核对指标名与文档一致）；本单元 C1-002、C6-004 为同一缺陷实例的跨维度重复立案，7.1 合并裁定应归并。

- **C2-002 —— 已保留 P1**。复验成立：全部 `HttpRequest` 无 `.timeout()`（仅 client `connectTimeout(10s)`，:48——只限 TCP 连接阶段，不覆盖读阶段）；`getSchemaById:69` 与 `AvroWireFormat.encode:55` 两处 `computeIfAbsent` 内嵌 POST/GET 属实；共享前提成立——`KafkaSourceFunction:166` 子任务复制按引用传递 `valueDeserializer`，各 subtask 共享同一 registry client/CHM，bin 锁毒化成立；「挂死不产出不报错」的 F5 形态成立。**现状归属**: WI18（plan 2341，commit e7c0f8c774）引入；本单元 C6-008（挂起面）、C7-006（无超时+重试风暴面）为同族不同侧面，合并裁定归并。

- **C2-003 —— 已保留 P1（受害者侧登记）**。复验成立：`shareBySubtask:47` 裸 LinkedHashMap；`registerReader:186` 的 `enumerator.addReader` 确在 `enumeratorLock` 外、经 `SourceReaderOperator.wireCoordinatorChannel:205` 在各 subtask 任务线程并发可达；`snapshotState` 持 EL 迭代而 mutator 无锁——并发结构性 remove 与受锁迭代并存，启动期常态触发。**现状归属**: 根因层已被 WI1.1（core-runtime 单元）立案——WI1.1-C1-001/C2-001 均点名本 enumerator 为 in-library 易损受害者，已并入 cra-index P1 台账（C1-001，open 待 7.1）；本条为受害者侧落账，core 侧修复（addReader/requestSplits 收敛入锁）落地后自动闭合，不重复修复。

- **C2-004 —— 已保留 P1**。复验成立：`launchRunner:81-92`/`launchEngine:92-138` 均为「检查（:82/:93）→ 构建生效点置后（:91/:135）」的无守卫 check-then-act；交付线程入口经 `drainPending` 回调在另一 subtask 任务线程同步执行（线程模型基线复核一致），与 pollNext 任务线程构成双入口；Debezium 窗口含 engine config 构建 + `initializeState`（毫秒级）属实；`adoptedSplits:68` 裸 LinkedHashMap 被交付线程 put 与本任务线程 `snapshotState:272` 迭代并发，CME 使 checkpoint 失败成立。双引擎后果支撑 P1 定级。**现状归属**: WI2（plan 2343，commit 53be7ab83c）引入；本单元 C1-003 覆盖同族 reader 的可见性侧面（同根不同 facet），合并裁定归并；建议中的 CAS 正例模式（`DebeziumCdcSourceFunction.runEntered`）经 E8 复核确在库。

- **C2-005 —— 已保留 P2**。复验成立：`jvmLock.lock():392`（非 `lockInterruptibly`、无 tryLock 超时）与 `channel.lock():395`（阻塞式 OS 锁）两级无限期获取。触发依赖对端 JVM 线程僵死（进程退出会释放 FileLock）这一外部故障形态——等待无上界为代码级确定，仅触发为外部故障，P2 恰当（非「理论风险」降级项）。**现状归属**: plan-2026-08-04-2107（AR-14）引入；R6 quality 审计（2026-10-06 `03-connectors-security.md`）已核对该临界区的 exactly-once 语义并列为「无问题」，但未审等待点上界——本条为新增 facet 非已知复发；F5 全量对账结论（8 模块仅本条与 C2-002 两处无上界）复核无异议。

- **C2-006 —— 已保留 P3**。复验成立：`saveState:250-264` 监视器内 `writeLines`+`forcePath`（fsync）、`invoke:238-240` 锁内仅 add；JDBC 兄弟 `saveState:641-651` 锁内拷贝+引用转移零 I/O 的正例属实。非竞态、正确性无损、量化收益明确，P3 优化登记恰当。**现状归属**: 同 C2-005 引入点；历史审计未立案、无跨维度重复。

- **C2-007 —— 已保留 P3（登记类，非缺陷主张）**。复验成立：AR-15-② 设计注释（:219-227）与限速睡眠锁外（:144-159、:233-236）均在；pollNext 持 this 监视器执行 `openSplit`/`readNextLine`（单行 ≤ maxLineBytes 上界）为已声明一致性设计的已知代价面。按「已声明边界不是缺陷」口径本条不构成缺陷主张，维持 P3 残留面登记（与初审自我定位一致，接受现状亦合理）。**现状归属**: AR-15-② 修复批次的刻意设计残留；无跨维度重复。

- **C2-008 —— 已保留 P3**。复验成立：:87-89 javadoc 声明 "one-shot-style warn" 与 :97-99 每次失败都 WARN 相悖，无抑制标志位。纯观测噪声、有 scrape 频率上界，P3 恰当。**现状归属**: plan-2216-1 引入即存在；历史审计未覆盖（R6 仅做名称一致性核对）；无跨维度重复。

- **C2-009 —— 已保留 P3**。复验成立：`MANIFEST_JVM_LOCKS` 全文件仅 2 处引用（:98 声明、:390 `computeIfAbsent`），无任何移除路径。增长率（~50B/不同输出目录）在实际部署包络内不可忽略性不成立，P3 防御性登记恰当。**现状归属**: plan-2026-08-04-2107 引入；本单元 C1-004、C6-009、C7-007 为同一容器的跨维度重复立案（R6 曾报的是磁盘锁**文件**无删除路径，属不同对象），7.1 合并裁定归并。

- **C2-010 —— 已保留 P1（受害者侧登记）**。全链复验成立：`states:146` 裸 LinkedHashMap（任务线程 `getOrCreateState` 懒注册 put）；`TtlContext.timestamps:57` 裸 HashMap，`sweepExpired` iterator-remove（HTTP 线程可达）vs 任务线程 `recordWrite/grantFreshWindow` put；REST 派发链逐点属实——`StreamOpsHttpServer` 固定 4 线程池（:126-127）→ `TaskManager.keyedStateSnapshot:158` 直呼 → `StreamTaskInvokable:623` 无 mailbox 编组直调 `backend.snapshotState`，与 checkpoint 路径（经 mailbox 在任务线程串行，`AbstractStreamOperator:308`）不对称；读端点经 `cleanupExpiredEntries` 携带 `db.delete` 写副作用确认。面③（4 线程池使两次并发查询为常态能力）成立；增量 retention 前提被破坏的叠加风险成立。后果面（native 句柄跨线程误用、TTL 状态静默损坏、任务侧容器损坏、非仅查询面 500）显著重于内存后端兄弟，P1 区别于 WI1.1-C3-009 的 P2 恰当。**现状归属**: 派发根因已被 WI1.1 保留为 C3-009（P2，其失效面枚举 ⑤ 明示「RocksDB 后端……需在 rocksdb 维度复核同族」）——本条即该委托项的受害者侧落账，修复（mailbox 编组）应与 WI1.1-C3-009 同批次裁定，rocksdb 受害侧按本条建议 ② 加固；TTL 面引入点 plan-2026-08-02-0955（Stage 32）、REST 面 WI14（plan 2340）。

- **C2-011 —— 已保留 P2**。复验成立：6 个 gauge 固定名 + `Tags.empty()`（:34-39,:82-83）、进程级 static CompositeMeterRegistry（`StreamMetricsRegistries:23-26`）、close 仅置 dbRef 无 `registry.remove`（:65-67）；micrometer 同 Id first-wins 丢弃新 valueFunction 为文档化标准行为——面①（多 subtask 仅暴露 1/N）与面②（首个 backend close 后永久 NaN，零并发可复现）均为确定性；`CepOperator` R5-CEP-03 双标签正例属实，tag 纪律缺失为可归因回归面。观测面静默失效、无正确性影响，P2 恰当。**现状归属**: plan-2216-1（item 16）引入；本单元 C6-010（注册/生命周期面）、C7-004（NaN 观测面）为同一缺陷实例的跨维度重复立案，合并裁定归并。

## 最终保留项

| 编号 | 严重度 | 一句话 | 现状归属 | 跨维度重复 / 修复挂靠 |
|---|---|---|---|---|
| C2-001 | P1 | RocksDB gauge 锁外解引用 dbRef，与 backend close 构成 JNI use-after-free 竞态 | 本单元首立（plan-2216-1 引入） | 同实例：C1-002、C6-004；7.1 批量修复 |
| C2-002 | P1 | Avro registry 客户端 computeIfAbsent 内无超时 HTTP——registry 挂死永久阻塞并毒化共享 bin 锁 | 本单元首立（plan 2341 引入） | 同族侧面：C6-008、C7-006；7.1 批量修复 |
| C2-003 | P1 | DebeziumTableSplitEnumerator 裸 LinkedHashMap 被 addReader（锁外）与 snapshotState（锁内）并发读写 | 受害者侧登记；根因已被 WI1.1 立案 | 挂靠 cra-index C1-001（open）；随 core 修复闭合 |
| C2-004 | P1 | MessageSourceReader/DebeziumCdcSourceReader 内嵌线程 launch check-then-act 无守卫——双启动（双订阅/双引擎） | 本单元首立（plan 2343 引入） | 同根 facet：C1-003；7.1 批量修复 |
| C2-005 | P2 | manifest 双锁获取无超时且不可中断——对端 JVM 挂死时 commit 无限阻塞 | 本单元首立（AR-14 引入；R6 只审过 exactly-once 面） | 无重复；7.1 批量修复 |
| C2-006 | P3 | saveState 持 currentBuffer 监视器执行整 epoch 写盘+fsync（JDBC 兄弟已示范锁内零 I/O） | 本单元首立 | 无重复；优化项 |
| C2-007 | P3 | FileSourceReader 持 this 监视器执行文件 I/O——checkpoint 快照被单行读取阻塞 | 已声明边界残留登记（AR-15-② 设计代价） | 无重复；可接受现状 |
| C2-008 | P3 | 「一次性告警」声明与每次 scrape 重复 WARN 的实现不符 | 本单元首立（plan-2216-1 引入） | 无重复；一行修复 |
| C2-009 | P3 | MANIFEST_JVM_LOCKS 静态锁表只增不删——长驻 JVM 缓慢累积 | 本单元首立 | 同实例：C1-004、C6-009、C7-007；合并归并 |
| C2-010 | P1 | WI14 keyed-state REST 在 HTTP 线程执行 backend.snapshotState——违反单线程契约，TTL sidecar 裸 HashMap 三点竞态 | 受害者侧登记；派发根因已被 WI1.1 保留（C3-009/P2） | 挂靠 WI1.1-C3-009 批次；受害侧按建议 ② 加固 |
| C2-011 | P2 | RocksDB gauge 空 tags 注册进程级共享 registry 且 close 无移除——多 subtask 静默去重 + 重启后永久 NaN | 本单元首立（plan-2216-1 引入） | 同实例：C6-010、C7-004；7.1 批量修复 |

**现状标注统计**: 首立新发现 8 条（C2-001/002/004/005/006/008/009/011）；根因已被 WI1.1 覆盖的受害者侧落账 2 条（C2-003 ← cra-index C1-001、C2-010 ← WI1.1-C3-009）；已声明边界残留登记 1 条（C2-007）。同单元跨维度重复立案 5 条涉及（C2-001×2、C2-002×2、C2-004×1、C2-009×3、C2-011×2），提请 7.1 合并裁定归并编号。

---

# C2 维度二轮复核结论（独立复核子代理·二轮，2026-10-14）

- **复核性质**: 第二轮独立复核（与初审者、一轮复核者均为不同代理）。纪律：逐条重读 live code 验证文件/行号/证据/推演/判级；三态判定（保留/降级/驳回）；仅对保留/降级项做现状标注（历史审计 summary/结论节 + git log）；除各条目「复核状态」行与本节追加外未改动任何前文。
- **本轮增量工作**: 一轮复核已逐条确认代码级事实，本轮不重复其结论，而将火力集中在**线程模型前提的全链重追踪**——对 4 条 P1 与 2 条 P3 逐一回答「陈述的两条线程在当前生产接线下是否真的存在」：`Task.run`（单任务线程：open→invoke→close，Task.java:161-245）、`SourceReaderOperator.wireCoordinatorChannel/open`（open 内 :252，任务线程）、`LocalSourceCoordinator.registerReader→addReader→deliverShare→assignSplits→channel.deliver→drainPending cb→reader.addSplits`（投递目标 subtask == 执行线程自身 subtask，四个枚举器逐一核对 `getDeliveryService` 全部 5 个调用点）、checkpoint 路径（`CheckpointBarrierTracker.triggerCheckpoint:137-147` → `offerBarrier:523-554` 经 mailbox 在任务线程执行 `injectBarrier→reader.snapshotState`；mailbox 在 `StreamTaskInvokable.wireMailboxToHeadSource:431-450` 对 head 为 SourceReaderOperator 的任务恒接线）、REST 路径（`StreamOpsHttpServer` 固定 4 线程池 → `TaskManager.keyedStateSnapshot:149-159` → `StreamTaskInvokable:623` 直调，无 mailbox）、`SourceCoordinatorRegistry`（v1 键=vertexId、声明单活跃执行限制）。历史对照：WI1.1 core-runtime 单元（C1-001/C3-009）、r6 quality（2026-10-06）、cra-index.md、git log（各缺陷文件在引入提交后零修改 commit——**无任何「已修复」项**）。
- **判定结果**: **保留 10 / 降级 1（C2-004：P1→P2）/ 驳回 0**。
- **与一轮复核的两处分歧**（均给出反证链）：
  1. **C2-004 降级**：其双启动交错要求「同一 reader 的 addSplits（交付入口）与 pollNext（任务入口）分属两线程」。全链追踪结论：当前 LOCAL 生产接线下该前提不成立——①四个枚举器的 `assignSplits(subtaskIndex,…)` 全部只向**发起调用的 subtask 自己**投递（Message deliverToken:54-62、Debezium deliverShare:88-100、File :193/:221、BatchLoader 单 split 且 parallelism==1），不存在向「别的 subtask 的 reader」投递的路径；addReader(k) 恰在 reader k 自己的任务线程上执行（registerReader 由 open() 在该 subtask 任务线程调用）；②reader 的 checkpoint 快照（含 adoptedSplits 迭代、MessageSourceReader.snapshotState 的 token 读）经 `offerBarrier` 的 mailbox 控制邮件在**同一任务线程**执行（mailbox 恒接线，见上）；③`SourceCoordinatorRegistry` 的 v1 单活跃执行声明排除了跨 job 交错。故「T_deliver vs T_task」交错在本仓库当前代码上写不出具体触发时序——按口径属「理论竞态无具体时序」= P2。**不驳回的理由**：无守卫 check-then-act、token/queue 非 volatile、adoptedSplits 裸 LinkedHashMap 全部属实；`LocalSourceCoordinator` javadoc:46-48 声明的设计意图是「enumerator 方法在 coordinator 线程执行」——当前实现（任务线程直调）本就偏离该声明，一旦接线迁至文档模型或 DISTRIBUTED RPC（v1 未实现），双线程入口即成活竞态；单守卫（CAS/synchronized）修复廉价，应随 7.1 批次实施。
  2. **C2-007 触发面修正（判级不变）**：「checkpoint 快照线程被单行读取阻塞」在当前生产接线下不成立——reader 快照经 mailbox 与 pollNext 同线程串行，监视器运行期无争用；争用仅存于两个残余窗口：`offerBarrier` 的 `finished` 早分支（:524-531）在 checkpoint 触发线程（JobCoordinator RPC 侧）直接执行 `injectBarrier`（FileSourceReader 为可耗尽有限源，该窗口真实可达）与未接 mailbox 的回退（测试/装配边缘）。I/O 位于监视器内的事实与「已声明边界残留登记」定位不变，维持 P3。
- **不确定项（显式列出）**: ① C2-004 降级依赖「reader 入口仅 SourceReaderOperator 两处桥接回调（:228/:327）+ mailbox 恒接线」这一全量 grep 结论；若 7.1 实施时发现本审计未覆盖的自建接线（如未来 SQL 层 direct wire），应回升 P1。② C2-010 面③的增量 retention 叠加风险（并发第二快照触发 pruneOldCheckpoints 删在途 cp 目录）依据 `RocksDBIncrementalSnapshotStrategy:113-120` 的 K=2 注释推演，未实机复现。③ C2-002 的「共享实例跨 subtask 复制」前提经 `KafkaSourceFunction.copyForSubtask:161-170`（valueDeserializer 按引用传递）证实，但仅覆盖 Kafka 注入面；若其他 connector 以序列化方式复制 Function，则退化为单实例挂死（仍成立，仅毒化面收窄）。

## 逐条判定表（编号 × 原判级 × 判定 × 理由 × 现状标注）

| 编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C2-001 | P1 | 保留 | `gauge:83` 解引用确在 synchronized 外、`close:979-1017` 先 detach 后 `db.close():1013`、backend 注释（:985-987）自证 SIGSEGV 后果——交错代码级必然 | **新发现（未修复）**：引入 c6dbc769fe（plan-2216-1 item 16）后零修改；r6 仅做指标名一致性核对（09-doc-contract-consistency.md:20）未覆盖此 facet；本单元 C1-002/C6-004 为同实例跨维度重复，7.1 归并 |
| C2-002 | P1 | 保留 | 全部 HttpRequest 无 `.timeout()`（仅 :48 connectTimeout 限 TCP 阶段）、两处 computeIfAbsent 内嵌 POST/GET、`KafkaSourceFunction.copyForSubtask:161-170` 按引用共享 deserializer——bin 锁毒化成立 | **新发现（未修复）**：引入 e7c0f8c774（WI18/plan 2341）后零修改；本单元 C6-008/C7-006 为同族不同侧面，7.1 归并 |
| C2-003 | P1 | 保留 | `addReader:186` 确在 EL 外、各 subtask 任务线程并发 `remove` 与 CheckpointCoordinator 线程（:1712）持 EL 迭代并存——多线程前提由 taskExecutor.submit 并发启动独立成立 | **已知未修复（根因=WI1.1-C1-001，P1，cra-index #30 open 待 7.1）**：其失效面枚举⑤已点名本 enumerator「实际易损」；本条为受害者侧落账，core 侧修复（分发收敛入锁）落地后自动闭合，不重复修复 |
| C2-004 | P1 | **降级至 P2** | 模式与代码证据全部属实，但当前 LOCAL 接线下同一 reader 全入口（start/addSplits/pollNext/mailbox 化 snapshot/close）单任务线程串行，双线程交错不可达（分歧点 1 详述） | **新发现（未修复）**：引入 53be7ab83c（WI2/plan 2343）后零修改；本单元 C1-003 覆盖同根 facet，7.1 归并；随 7.1 补单守卫 |
| C2-005 | P2 | 保留 | `jvmLock.lock():392`（非 lockInterruptibly/无 tryLock）与 `channel.lock():395`（阻塞式 OS 锁）两级无上界属实；触发依赖对端僵死外部故障，P2 恰当 | **新发现（未修复）**：引入 plan-2026-08-04-2107（AR-14）后零修改；r6（03-connectors-security.md:16,112）仅核对 exactly-once 语义与锁文件删除路径，未审等待上界——本条为新增 facet 非复发 |
| C2-006 | P3 | 保留 | `saveState:250-264` 监视器内 writeLines+forcePath、`invoke:238-240` 锁内仅 add、JDBC 兄弟 `:641-651` 锁内零 I/O 正例——非竞态优化项，P3 恰当 | **新发现（未修复）**：同 AR-14 引入点后零修改 |
| C2-007 | P3 | 保留（触发面修正） | I/O 在 this 监视器内（:206-231，openSplit:215/readNextLine:217）与 AR-15-② 注释（:219-227）属实；但运行期快照经 mailbox 同线程，争用仅存于 finished 早分支直接注入窗口与无 mailbox 回退（分歧点 2 详述）——残留面登记维持 P3 | **新发现（未修复）**：AR-15-② 修复批次的刻意设计残留（引入 2bb7c013a7，plan 368 Phase 7；其后仅 WI5 限速 1995aa4eaa 触及且在监视器外）零修改；无跨维度重复 |
| C2-008 | P3 | 保留 | :86-89 javadoc「one-shot-style warn」与 :97-99 每失败必 WARN、无抑制标志——声明与实现相悖属实 | **新发现（未修复）**：引入 c6dbc769fe 后零修改；r6 仅名称一致性核对未覆盖 |
| C2-009 | P3 | 保留 | `MANIFEST_JVM_LOCKS` 全文件仅 :98 声明与 :390 computeIfAbsent 两处引用、无移除路径属实；增长量在部署包络内不可忽略性不成立，P3 恰当 | **新发现（未修复）**：同 AR-14 引入点；r6 报的是磁盘**锁文件**无删除路径（03-connectors-security.md:112，不同对象）；本单元 C1-004/C6-009/C7-007 为同一容器跨维度重复，7.1 归并 |
| C2-010 | P1 | 保留 | 全链逐点复验成立：固定 4 线程池（StreamOpsHttpServer:126-127）→ keyedStateSnapshot 直调（TaskManager:158 → StreamTaskInvokable:623，无 mailbox）→ `cleanupExpiredEntries:797-824` 迭代裸 `states:146` + `sweepExpired` iterator-remove 裸 `timestamps:57`（TtlContext）+ `db.delete:815` 写副作用；任务线程对侧 `RocksDBValueState:67-93`/`getOrCreateState:603-617` 逐条 put；面③（4 线程池使并发查询为常态能力）与面②（lazy 注册交叠）独立于任务线程成立 | **已知未修复（根因=WI1.1-C3-009，P2）**：其第 2 轮表（C3:297-298）明示 REST 读面命中并委托 rocksdb 侧复核——本条即该委托项的受害者侧落账，P1 区别于派发侧 P2 的理由（native 面/TTL 语义损坏/任务侧容器损坏）复核无异议；修复挂靠 WI1.1-C3-009 批次（mailbox 编组）+ 受害侧 CHM 化加固；TTL 面 plan-2026-08-02-0955、REST 面 WI14（plan 2340）引入后零修改 |
| C2-011 | P2 | 保留 | 6 gauge 固定名 + `Tags.empty()`（:34-39,:83）、进程级 static CompositeMeterRegistry（StreamMetricsRegistries:32-34）、close（:65-67）无 registry.remove、micrometer 同 Id first-wins 丢弃 valueFunction——面①/面②确定性成立；`CepOperator:438-441` 双标签正例（R5-CEP-03）确认 tag 纪律缺失可归因 | **新发现（未修复）**：引入 c6dbc769fe 后零修改；本单元 C6-010/C7-004 为同实例跨维度重复，7.1 归并 |

## 最终保留项（二轮复核后）

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|---|---|---|---|---|
| C2-001 | P1 | nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:82-101 | RocksDB gauge 在锁外解引用 dbRef，与 backend close 构成 JNI use-after-free 竞态（进程级 SIGSEGV） | 新发现，未修复；7.1 批量修复（同实例 C1-002/C6-004 归并） |
| C2-002 | P1 | nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java:68-129 | Avro registry 客户端 computeIfAbsent 内做无请求超时 HTTP I/O——registry 挂死时永久阻塞并毒化共享 bin 锁 | 新发现，未修复；7.1 批量修复（同族 C6-008/C7-006 归并） |
| C2-003 | P1 | nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumTableSplitEnumerator.java:47-135 | 裸 LinkedHashMap 被 addReader（EL 外、并发任务线程）与 snapshotState（EL 内、checkpoint 线程）并发读写 | 已知未修复；受害者侧落账，挂靠 WI1.1-C1-001（open），随 core 修复闭合 |
| C2-004 | **P2（降级）** | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceReader.java:81-92（+ DebeziumCdcSourceReader.java:92-138） | 内嵌线程 launch 无守卫 check-then-act——当前接线单线程串行不可达，属理论竞态（接线模型迁移即成活）；仍应随 7.1 补单守卫 | 新发现，未修复；7.1 批量修复（同根 C1-003 归并） |
| C2-005 | P2 | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:389-404 | manifest 双锁获取无超时且不可中断——对端 JVM 挂死时 checkpoint commit 无限阻塞 | 新发现，未修复；7.1 批量修复 |
| C2-006 | P3 | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:249-266 | saveState 持 currentBuffer 监视器执行整 epoch 写盘+fsync（JDBC 兄弟已示范锁内零 I/O） | 新发现，未修复；优化项 |
| C2-007 | P3 | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSourceReader.java:202-251 | pollNext 持 this 监视器执行文件 I/O——运行期已被 mailbox 串行化掩蔽，争用仅存于 finished 直接注入窗口（触发面经二轮修正） | 新发现（已声明边界残留登记），未修复；可接受现状 |
| C2-008 | P3 | nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:86-100 | 「一次性告警」javadoc 声明与每次 scrape 重复 WARN 的实现不符 | 新发现，未修复；一行修复 |
| C2-009 | P3 | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:96-99,389-391 | MANIFEST_JVM_LOCKS 静态锁表只增不删——长驻 JVM 缓慢累积 | 新发现，未修复；同实例 C1-004/C6-009/C7-007 归并 |
| C2-010 | P1 | nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:797-836 | WI14 keyed-state REST 在 HTTP 4 线程池直调 backend.snapshotState（无 mailbox）——违反单线程契约，TTL sidecar 裸 HashMap 三点竞态 + 读端点携带 db.delete 写副作用 | 已知未修复；受害者侧落账，挂靠 WI1.1-C3-009 批次（mailbox 编组）+ 受害侧 CHM 加固 |
| C2-011 | P2 | nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:34-84 | gauge 空 tags 注册进程级共享 registry 且 close 无移除——多 subtask 静默去重吞 1/N、首个 backend close 后永久 NaN | 新发现，未修复；7.1 批量修复（同实例 C6-010/C7-004 归并） |

**二轮判定统计**: 保留 10（P1×3、P2×2、P3×5）/ 降级 1（C2-004 P1→P2）/ 驳回 0。最终口径：P1×3（C2-001/002/010）、P2×3（C2-004/005/011）、P3×5。全部 11 条在 live code 均未修复（缺陷文件于引入提交后零修改 commit），无「已修复」项。

**需子项复核的高风险项**:
1. **C2-010（P1）**：修复横跨 core 派发侧（mailbox 编组，WI1.1-C3-009 批次）与本模块受害侧（states/TtlContext CHM 化 + sweepExpired 注释修正），双处改动需独立验收；且现无「HTTP 轮询 state 端点 × 任务线程并发写」并发测试会红（与 WI1.1 C10 移交口径一致），修复必须配并发回归测试。
2. **C2-001（P1）**：修复仅 3 行但涉及 native 句柄生命周期与 micrometer 弱引用 gauge 语义（backend:275-279 注释自证团队已知弱引用约束），需防「移入临界区后被后续 C2-011 tag 重构回退」的回归，建议与 C2-011 同批实施并配 scrape×close 交叠测试。
3. **C2-004（降级 P2，附回升条件）**：降级依据为当前接线的全量追踪；若 7.1 实施时发现本审计未覆盖的生产 addSplits 接线（当前全仓仅 SourceReaderOperator:228/:327 两处桥接回调）或 DISTRIBUTED 接线落地，应回升 P1。

