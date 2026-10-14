# C3 异步链路正确性审计 — nop-stream-state-connectors

- **审计维度**: C3（异步链路正确性）——异步组合的异常传播、取消/超时语义、时序假设；定向对照缺陷族 F1（异步快照撕裂）与 F2（半个成功）
- **审计对象**: 8 个模块 `src/main/java`，140 files / 25,458 lines（nop-stream-cep、nop-stream-rocksdb、nop-stream-connector、nop-stream-connector-batch、nop-stream-connector-jdbc、nop-stream-connector-debezium、nop-stream-connector-kafka、nop-stream-format-avro）
- **审计日期**: 2026-10-14
- **审计方式**: 盲审（live code only；未读取 ai-dev/audits/plans/bugs/lessons 任何历史记录）。`_` 前缀生成文件不作为发现对象。
- **Owner doc 基准**: `docs-for-ai/03-modules/nop-stream.md`、`docs-for-ai/03-modules/nop-stream-connectors.md`
- **基线**: `_tmp/cra-baseline-nop-stream-state-connectors.txt`（C3 async 节为空——8 模块 `src/main/java` 中 **零 CompletableFuture/thenApply/supplyAsync/orTimeout 使用**，异步面全部由嵌入线程 + 平台 timer service + 引擎回调承载，见下节清单）

---

## 检查范围说明

### 异步入口清单（C3 步骤 1 盘点结果）

| # | 异步入口 | 载体 | 线程模型 | 判定 |
|---|---|---|---|---|
| A1 | `CepOperator.createTimerService().registerProcessingTimeTimer`（cep/operator/CepOperator.java:556-571） | `ProcessingTimeService.registerTimer` 回调 | 生产路径 = `TaskProcessingTimeService`（core/execution/TaskProcessingTimeService.java）：scheduler 线程只投递 mail，回调在 **task 线程 mailbox** 执行，与 `processElement` 串行；异常 fail-fast 上抛任务循环（owner doc「定时器异常语义」节钉定） | 干净 |
| A2 | `CepOperator.onCacheStatisticsTimer`（:658-667）周期 cache 统计日志 + 自重排 | 同 A1 载体，独立 callback 面（不触发 CEP 事件处理） | task 线程；close() 先 cancel（:683-691），mailbox 串行化使 cancel 与 fire 不交错 | 干净 |
| A3 | `MessageSourceReader` 嵌入 legacy source 线程（connector/MessageSourceReader.java:87-105） | `new Thread(daemon)` + `ArrayBlockingQueue(256)` push→pull 桥 | runner 线程跑 `SourceFunction.run`，失败 stash 到 `volatile runnerFailure` 从 `pollNext` typed 重抛；close 经 cancel-latch ≤1s 退出 + join(5s) | 干净 |
| A4 | `DebeziumCdcSourceReader` 嵌入引擎线程（connector-debezium/DebeziumCdcSourceReader.java:132-158） | 同 A3 形态 | 同 A3；引擎终态失败经 `DebeziumEngineWrapper` 静态 listener → `pendingError` → run() 重抛（R5-CON-03） | 干净（但静态 listener 注册表本身见 C3-006） |
| A5 | `DebeziumCdcSourceFunction.run` 订阅回调 `source.subscribe(event -> ctx.collect(...))`（DebeziumCdcSourceFunction.java:260-277） | Debezium 引擎分发线程 → 引擎宿主 | collector 异常 capture-and-rethrow（R5-CON-02）；offset store 为 CHM，task 线程 `snapshotState` 读得弱一致快照 + 防御拷贝 | 干净 |
| A6 | Kafka consumer poll 循环（connector-kafka/KafkaSourceFunction.java:269-292） | legacy source 同步 run() 占 task 线程；poll(200ms) 有界 | `restoredOffsets` 与 `snapshotState`（经 `StreamSourceOperator.drainControlMails` 在 collect 点排水 mailbox）**同线程串行**——无撕裂 | 取消语义见 C3-005 |
| A7 | 2PC sink commit 路径（file/jdbc/kafka 三实现） | checkpoint 完成通知经 commit 执行器（owner doc plan 369 `nop.stream.task.commitQueueDepth` 钉定）→ `finishCommit` → `commit(epoch)` 在 **commit 线程**执行，与 task 线程 invoke/saveState 跨线程 | `pendingCommits` = synchronizedMap（安全发布）；JDBC sink 懒初始化 volatile+DCL（:246-255, :461-471）；file sink buffer monitor；kafka 高水位 volatile | 干净（账本增长见 C3-002，producer 泄漏见 C3-001） |
| A8 | `AvroSchemaRegistryClient` JDK HttpClient 同步调用（format-avro/AvroSchemaRegistryClient.java:113-129） | task 线程阻塞 RPC | **无请求级超时** → 见 C3-003 | 缺陷 |
| A9 | RocksDB 增量快照 `RocksDBIncrementalSnapshotStrategy.doSnapshot`（rocksdb/incremental/RocksDBIncrementalSnapshotStrategy.java:63-124） | task 线程同步执行（native checkpoint 返回即一致，SST hash/copy 读 hard-link 集合，免于后续写入撕裂） | cp-N 目录滚动保留（F-02）；`dbPath` 定长不按 subtask 限定——同 JVM 多 subtask 同 dbPath 时 RocksDB 目录 LOCK fail-fast（响亮失败，非静默） | 干净 |
| A10 | `FileTwoPhaseCommitSink.withManifestLock`（connector/file/FileTwoPhaseCommitSink.java:389-404）跨 JVM FileLock | commit 线程阻塞等锁 | JVM 内 ReentrantLock + OS FileLock，锁序恒定 jvmLock→fileLock；等待无超时 → 见 C3-008 | 理论风险 |

### F1（异步快照撕裂）定向检查结果：零发现

逐一路径核对「快照构建 → 异步持久化」之间是否外泄活引用：

- `CepOperator.snapshotState`（:696-730）：timer ledger 逐 key 构建 fresh `ArrayList` + `SnapshotCopyUtil.typedCopy(ledgerKey)`（core/util/SnapshotCopyUtil.java:41-49，JSON freeze 语义，框架注释明确「快照 task 线程构建、异步持久化」契约）；watermark/keyClass 为值/不可变。**安全**。
- `TwoPhaseCommitSinkFunction.saveState`（core 基类 :156-167）：`synchronized(pendingCommits)` 内 fresh `TreeMap` 拷贝——file/jdbc/kafka 三个子类共享该咽喉。**安全**。
- `KafkaSourceFunction.snapshotState`（:338-348）：fresh `TreeMap<String,Long>`（JSON-safe 键），offset 值为装箱不可变。**安全**。
- `DebeziumCdcSourceReader.snapshotState`（:255-279）→ `NopStreamOffsetBackingStore.getOffsets`（CHM 弱一致遍历 + `copy()` 防御拷贝）：撕裂窗口只产生「部分分区位点偏旧」——按分区单调 + REPLAYABLE 重放语义下无害（at-least-once 收敛）。**安全**。
- `RocksDBSnapshotSerDe.snapshotState` / `RocksDBIncrementalSnapshotStrategy`：task 线程同步扫描构建 fresh map / native checkpoint（返回即物理一致，SST 经 hard-link 免疫后续 compaction 写）。**安全**。

### F2（半个成功）定向检查结果

- kafka-2pc 残余重复窗口 `[barrier commitTransaction, checkpoint durable)`：owner doc 能力矩阵 **已显式声明**（EXPERIMENTAL 成熟度豁免 + watch-only residual）——按「已声明的边界不是缺陷」不立项。
- JDBC 2PC：ledger 守卫幂等 + 失败回滚 + durable/非 durable 恢复分叉（re-commit / abort）——闭环；但台账只增不删 → C3-002。
- file 2PC：temp 先落盘 fsync → rename → manifest 原子更新 → 目录 fsync；rename/manifest 间崩溃经 `recoveredEntryKeys` 来源判别修复或 typed 拒绝（R6 CON-01）——闭环。
- batch-consumer：flush 失败保留缓冲重试 + close 失败显式报「data may be lost」——语义已声明。
- debezium 恢复面发现一个真实的「重复投递」缺陷（半个成功的对偶面：重复而非丢失）→ C3-004。

---

## 发现条目

### [nop-stream-state-connectors-C3-001] Kafka 2PC sink 的 transactional producer 从不关闭——每次任务停止/重建泄漏一个 producer

- **文件**: `nop-stream/nop-stream-connector-kafka/src/main/java/io/nop/stream/connector/kafka/KafkaTwoPhaseCommitSink.java:89,150-165`（配合 core 关闭路径 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSinkOperator.java:172-178`）
- **证据片段**:
  ```java
  // KafkaTwoPhaseCommitSink.java
  private transient volatile Producer<String, byte[]> producer;
  ...
  private void ensureTransactionOpen() throws Exception {
      if (transactionOpen) { return; }
      if (producer == null) {
          producer = newProducer();          // 创建 KafkaProducer（自带 network 线程）
      }
      ...
  }
  // 全文件无 producer.close()；类未实现 AutoCloseable/finish() 资源释放
  ```
  ```java
  // StreamSinkOperator.java（唯一会关闭 userFunction 的钩子）
  if (userFunction instanceof AutoCloseable) {
      ((AutoCloseable) userFunction).close();   // kafka-2pc sink 不是 AutoCloseable → 不触发
  }
  ```
- **严重程度**: P1
- **并发场景**: task 线程（invoke/saveState 创建并使用 producer）× 任务停机/恢复路径（operator.close() 与任务重建）。KafkaProducer 自持一条 `kafka-producer-network-thread` + accumulator 缓冲。
- **触发时序**: T1（正常停止）：作业 DRAIN/CANCEL → `StreamSinkOperator.close()` → `userFunction instanceof AutoCloseable` = false → producer 保留。T2（恢复重建）：任务失败 → `SupervisionLoop.rebuildTask` 经 `copyForSubtask(TaskLocation)` 构造 **新 sink 拷贝**（新 producer），旧拷贝连同其 producer 线程被丢弃但从未关闭。反复恢复/启停作业（OpsJobManager 多作业长驻进程）→ 每周期净增一条阻塞在 producer 上的网络线程与缓冲内存。
- **现状**: 生产者生命周期只创建不释放；`rollback()/abort()/recover()` 均只操作事务状态，`checkConnection()` 的探针 producer 用 try-with-resources 关闭，唯独运行期 producer 无任何关闭路径。
- **风险**: 长驻 JVM（本地模式/单进程多作业）随作业启停次数线性累积线程与直接内存；broker 侧靠下一次 `initTransactions()` fencing 兜底，但泄漏线程本身永不回收。恢复风暴场景（反复失败-重启）放大为线程耗尽。
- **建议**: `KafkaTwoPhaseCommitSink` 实现 `AutoCloseable`（`close()` 内 `rollback()` + `producer.close(Duration)`），复用 `StreamSinkOperator.close()` 既有钩子即可生效；或在 `finish()`（`SinkFunction` 已有该 default 方法）中关闭。对齐 `BatchConsumerSinkFunction`（构造 consumer 在 `close()` 关闭）的既有族内形态。
- **失效面枚举**（F3 同族清扫——8 模块全部资源持有者的关闭配对）: `FileTwoPhaseCommitSink`（per-call channel，无常驻资源——干净）；`JdbcTwoPhaseCommitSink`（commit 内连接 finally close——干净）；`BatchConsumerSinkFunction`（close() 关闭 consumer——干净）；`MessageSinkFunction`（不持有 service 生命周期——干净）；`MessageSourceReader`/`DebeziumCdcSourceReader`（close: cancel+join+清队列——干净）；`KafkaSourceFunction`（consumer 在 `run()` try-with-resources——干净）；`AvroSchemaRegistryClient`（HttpClient 无需显式关闭——干净）。**仅 kafka-2pc sink 一个漏修点**。
- **信心水平**: 确定
- **误报排除**: 不属于「有界声明的缓存/池」豁免——producer 无任何上限声明与关闭钩子；也不是 Flink 语义必需（KafkaProducer 明确要求 close 释放线程）。
- **复核状态**: 已保留（P1，2026-10-14 复核）——live code 证实全文件无 producer.close、类仅 implements ConnectivityCheckable，`StreamSinkOperator.close` 的 `instanceof AutoCloseable` 钩子（:172-178）对其不生效，copyForSubtask 重建即弃置旧 producer；启停/恢复周期线性累积时序成立。归属：同 campaign 四向重复 ←C4-001、C6-001、C7-002（7.1 合并裁定归并）；无历史先例。

### [nop-stream-state-connectors-C3-002] JDBC 2PC 台账 `stream_epoch_ledger_v2` 只增不删——无任何清理/TTL 路径

- **文件**: `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:852-861`（`writeLedgerEntry`），全模块 grep 无 ledger DELETE
- **证据片段**:
  ```java
  private void writeLedgerEntry(Connection connection, String namespace, long epochId,
                                int subtaskIndex) throws SQLException {
      try (PreparedStatement ps = connection.prepareStatement(insertLedgerSql)) {
          ps.setString(1, namespace);
          ps.setLong(2, epochId);
          ps.setInt(3, subtaskIndex);
          ps.setTimestamp(4, CoreMetrics.currentTimestamp());
          ps.executeUpdate();               // 唯一台账写路径：INSERT
      }
  }
  // abort(epochId)（:747-752）：仅 getPendingCommits().remove —— 不触碰台账行
  ```
- **严重程度**: P1
- **并发场景**: commit 线程每 epoch 每子任务插入一行 `(sink_namespace, epoch_id, subtask_id)`；台账守卫查询（`ledgerExists`）随行数增长线性变慢；无任何后台/周期清理。
- **触发时序**: 30s checkpoint 间隔 × 单 subtask = 2,880 行/天/命名空间；运行一年 ≈ 105 万行/subtask。守卫探测 `SELECT 1 ... WHERE namespace=? AND epoch_id=? AND subtask_id=?` 走主键本可常数命中，但表本身无界膨胀：① 每次提交多一次 INSERT 写放大；② 运维面（表空间/备份/跨作业共享库）随行数无界增长；③ epoch id 重置/多作业共享 ledger 表时行数按命名空间乘积累积。
- **现状**: 守卫只需覆盖「可被重提交的 epoch」（最近 durable 窗口），但实现为永久 append-only；模块内无 delete/retention/TTL 代码路径（`abort` 只删内存 pending 条目）。
- **风险**: 长时运行必然触发的无界持久化增长（F4 族）；与声明规模包络无关——任何长期作业都会命中。
- **建议**: 在 `commit()` 成功路径顺带按 `committed_at < now() - retention`（或 `epoch_id < durableHighWater - N`）删除本命名空间的过期台账行（同一事务内，保持守卫语义）；或提供 `StreamMaintenanceMain` 维护子命令做离线裁剪。保留窗口 ≥ `maxRetainedCheckpoints × checkpoint 间隔` 即可维持恢复幂等。
- **失效面枚举**: ① 本模块台账表（本条）；② **file 2PC sink 的 `manifest.properties`**（`FileTwoPhaseCommitSink.updateManifestAtomically` :560-595——每 epoch 追加一个键、从不移除，同族「只增不删」，量级为每 epoch 一行 properties 文本，无界增长但单文件、增速低——同族登记，建议同批修：commit 成功后裁剪早于保留窗口的键）；③ RocksDB cp-N 任务本地目录（F-02 滚动保留 K=2——已闭环，干净）；④ RocksDB JSON 全量快照（每 checkpoint 全量重写，非 append-only——干净）。
- **信心水平**: 确定
- **误报排除**: 不是「幂等 DDL 豁免的台账表」误报——豁免的是探测红线中的表对象，本条针对运行期行积累；owner doc（nop-stream.md P-REQ-11 治理节）只治理观测历史，不覆盖数据面台账。
- **复核状态**: 已保留（P1，2026-10-14 复核）——`writeLedgerEntry` INSERT 为唯一台账写路径证实；模块内 grep DELETE 命中均为 CDC 数据面 `deleteKeyMapper`，无任何台账清理；`abort` 仅删内存 pending。增速量级（30s 间隔单 subtask ≈2880 行/天）成立。归属：同 campaign 四向重复 ←C5-006、C7-001、C8-007（7.1 归并）；2026-09-01 连接器分析仅裁「表名硬编码非缺陷」，无先例。

### [nop-stream-state-connectors-C3-003] Schema Registry HTTP 调用无请求级超时——registry 挂起时 task 线程无限期阻塞且持有 CHM bin 锁

- **文件**: `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java:48,55-58,101-107,113-123`；`AvroWireFormat.java:55-60`
- **证据片段**:
  ```java
  // AvroSchemaRegistryClient.java:48 —— 只有连接超时，无请求/响应超时
  this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  ...
  private Map<?, ?> readJsonResponse(HttpRequest request) {
      HttpResponse<String> resp;
      try {
          resp = http.send(request, HttpResponse.BodyHandlers.ofString());  // 无 request.timeout()
  ```
  ```java
  // AvroWireFormat.java:55 —— computeIfAbsent 内做网络 RPC（热路径 encode）
  int schemaId = writerSchemaIds.computeIfAbsent(subject, s -> {
      int id = client.registerSchema(s, record.getSchema());
      client.getSchemaById(id);
      return id;
  });
  ```
- **严重程度**: P1
- **并发场景**: 多 subtask task 线程共享同一 `AvroWireFormat`/`AvroSchemaRegistryClient` 实例（valueSerializer/valueDeserializer 经 `copyForSubtask` 共享同一 Function 闭包）。`computeIfAbsent` 在 ConcurrentHashMap bin 锁内执行 `registerSchema`/`fetchSchemaById` 的阻塞 HTTP 调用。
- **触发时序**: registry 端 TCP accept 后不响应（挂起的 server/网络黑洞）。T1：subtask-0 的 encode 走 `computeIfAbsent(subject)` → bin 锁内 `registerSchema` → `http.send` **无限期阻塞**（connect 已成功，读响应无超时）。T2：subtask-1 encode 同一 subject → `computeIfAbsent` 同 bin → 阻塞等锁。T3：decode 路径 `getSchemaById`（cache miss 时）同样阻塞。全管线停摆：任务状态 RUNNING、无输出、无异常——对健康检测不可见（F5「不抛异常也不产出」）。
- **现状**: `HttpClient` 仅配 `connectTimeout(10s)`；三处请求构造（`registerSchema` :55-58、`getJson` :101-107、`postJson`→`readJsonResponse`）均未设 `HttpRequest.timeout(...)`。
- **风险**: 上游 Kafka（源头有 poll 200ms 心跳面）与下游 sink 全部卡死在 registry 单点挂起上；coordinator 只能靠 checkpoint 超时间接触发恢复，且恢复重建后第一个 encode/decode 再次卡在同一无超时调用。
- **建议**: 每个 `HttpRequest.newBuilder()` 补 `.timeout(Duration.ofSeconds(N))`（对齐 webhook 渠道 `nop.stream.alert.webhook.timeout-ms` 的既有超时纪律）；并把 `writerSchemaIds.computeIfAbsent` 的 RPC 移出锁外（先查缓存，miss 时锁外注册、成功后 `putIfAbsent` 回填），避免阻塞面从单线程放大为同 subject 全部 subtask。
- **失效面枚举**: `AvroSchemaRegistryClient` 全部 3 个 RPC 调用点（`registerSchema`/`getLatestSchema`/`fetchSchemaById`）同族未修；模块内无其他 HTTP 客户端（webhook 渠道属 nop-stream-runtime，不在本审计对象内，其已有 timeout-ms 配置可作族内对齐样板）；`getLatestSchema` 的 check-then-put（:74-85）并发首查会重复 GET 但结果幂等——非缺陷。
- **信心水平**: 确定
- **误报排除**: 不是「原语使用不是问题」豁免——缺陷在复合形态（无超时的阻塞 I/O × computeIfAbsent 锁内执行 × 共享热路径），不是 ConcurrentHashMap 本身；也非压测专用代码。
- **复核状态**: 已保留（P1，2026-10-14 复核）——两文件 grep timeout 零命中（仅 connectTimeout 10s 连接超时）证实；encode 热路径 `computeIfAbsent` 锁内 `registerSchema`+`getSchemaById` RPC 逐行证实；registry 建连后挂起→同 subject 全 subtask 阻塞且无异常无输出（F5）时序成立。归属：同 campaign 五向重复 ←C2-002、C4-003、C6-008、C8-004、C7-006（后者另含重试风暴面；7.1 归并）。

### [nop-stream-state-connectors-C3-004] Debezium 枚举器恢复路径把未分配表与新份额双重登记——同一表被两个引擎重复消费

- **文件**: `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumTableSplitEnumerator.java:56-70,113-135`（配合 core 恢复时序 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java:160-163`）
- **证据片段**:
  ```java
  // DebeziumTableSplitEnumerator.start() —— 恢复后仍按全表重新计算 position 份额（不清空、不去重）
  for (int i = 0; i < tables.size(); i++) {
      int subtask = i % parallelism;
      shareBySubtask.computeIfAbsent(subtask, k -> new ArrayList<>()).add(tables.get(i));
  }
  // restoreState() —— 把全部未分配表塞进"单个"桶 nsi（注释声称 one table per registration）
  for (String table : unassigned) {
      shareBySubtask.computeIfAbsent(state.getNextSubtaskIndex(), k -> new ArrayList<>())
              .add(table);
  }
  ```
- **严重程度**: P1
- **并发场景**: coordinator 枚举器线程（restoreState→start 顺序执行）× 各 reader 注册事件（addReader）。非竞态——给定崩溃形态即确定性复现。
- **触发时序**: 4 表 T0-T3、parallelism=2，position 份额 s0=[T0,T2]、s1=[T1,T3]。① 首轮 reader-0 注册（取走 s0），reader-1 未注册即崩溃 → 快照 unassigned=[T1,T3]、nextSubtaskIndex=shareBySubtask.size()=1... 取一般形：剩余桶 {2,3} 时 nsi=2。② 恢复：新枚举器 `restoreState` 先把 [T_a,T_b] 全塞进桶 2；`start()`（LocalSourceCoordinator.java:160-163 恒在 restoreState 之后调用）再按 position 追加：T_a→桶2（与恢复条目共存）、T_b→桶3。③ reader-2 注册 → 得 [T_a,T_b]；reader-3 注册 → 得 [T_b]。④ 两个 reader 各自 `launchEngine`：reader-3 的 `adoptedSplits` 为空 → D-4 守卫（`DebeziumCdcSourceReader.addSplits` :172 的 `|| adoptedSplits.isEmpty()` 分支）放行 → **T_b 被两台引擎同时消费**，下游每个变更事件收到两份。
- **现状**: `restoreState` 的注释语义是「re-offered to registering readers (one table per registration)」，实现却是单桶倾倒；`start()` 又对全表重算份额且 `computeIfAbsent` 只增不清——恢复态与新鲜份额求并集而非求差集。reader 侧仅按 splitId 去重 **单 reader 内** 的重复，跨 reader 无去重。
- **风险**: 恢复后 CDC 流对部分表双份投递——jdbc-2pc INSERT 模式产生重复行（原子性保住、唯一性破坏），file sink 重复行，仅 keyed upsert 模式自然收敛；违反 REPLAYABLE 声明的「恢复即续读」语义。
- **建议**: 恢复路径二选一：① `restoreState` 时不预置份额，`start()` 检测恢复态后把 unassigned 表 **并入其 position 份额桶**（`i % parallelism` 的桶，而非 nsi 单桶），注册即得「fresh ∪ restored」且每表恰一桶；② 或 `start()` 在 `restoredState != null` 时跳过全表重算、只按恢复态分配（枚举器需感知 restore 标志）。对齐 `FileSplitEnumerator` 的恢复形态（恢复 `assignedFiles` 集合防止重复分配——同族已闭环样板）。
- **失效面枚举**: ① `DebeziumTableSplitEnumerator`（本条）；② `FileSplitEnumerator` 恢复（restore `discovered/assigned/finished` 三集合，防重复分配——干净）；③ `MessageTopicSplitEnumerator` 恢复（token 按 reader 重发、splitId 幂等——干净）；④ `BatchLoaderSplitEnumerator`（单 split 有界源——干净）；⑤ reader 侧 D-4 守卫（只挡「已启动后新表」，放行首查——本条的放行面，非独立缺陷）。
- **信心水平**: 很可能（core 侧 `restoreState`→`start` 调用顺序已在 `LocalSourceCoordinator` 直接读码证实；「崩溃时部分 reader 未注册」的前置条件频率取决于部署窗口）
- **误报排除**: 不属于「已声明边界」——owner doc 恢复语义承诺「恢复经嵌入引擎宿主的 initializeState 合约重放」「启动后新表分配 typed 拒绝」，未声明跨 reader 重复投递；`TestDebeziumCdcSource` 锚点覆盖份额分配但未覆盖「部分注册崩溃恢复」形态。
- **复核状态**: 已保留（P1，2026-10-14 复核）——`restoreState` 单桶 computeIfAbsent 倾倒 + `start()` 全表 `i%parallelism` 重算追加（不清不去重）逐行证实；恢复态来源证实（`SourceCoordinatorRegistry.consumeEnumeratorRestore` stash）且 restoreState 先于 start（LocalSourceCoordinator:160-163）；跨 reader 双登记时序成立——剩余桶 index > nsi 的表同时落入恢复桶与 position 桶（例：剩余桶 {2:[T2],3:[T3]}、nsi=2，T3 双桶→两 reader 各自 launchEngine，reader-3 `adoptedSplits` 为空 D-4 放行）。归属：相邻非重复——WI1.1 C1-001/C2-001 指向同一枚举器的线程安全面（裸 LinkedHashMap CME），本条为恢复分配逻辑缺陷，7.1 cross-ref 不归并。

### [nop-stream-state-connectors-C3-005] KafkaSourceFunction.cancel 不调用 consumer.wakeup——启动期元数据拉取可阻塞取消最长 max.block.ms

- **文件**: `nop-stream/nop-stream-connector-kafka/src/main/java/io/nop/stream/connector/kafka/KafkaSourceFunction.java:247,269-292,328-330`
- **证据片段**:
  ```java
  public void run(SourceContext<T> ctx) throws Exception {
      this.running = true;
      try (KafkaConsumer<String, byte[]> consumer = newConsumer()) {
          List<PartitionInfo> partitions = consumer.partitionsFor(topic);   // 阻塞至 max.block.ms（默认 60s）
          ...
          while (running) {
              var records = consumer.poll(Duration.ofMillis(200L));          // 稳态取消延迟有界 ≤200ms
  ...
  @Override
  public void cancel() {
      running = false;                    // 仅置位；无 consumer.wakeup()
  }
  ```
- **严重程度**: P2
- **并发场景**: task 线程执行 run()（legacy source 占线程模型）；cancel 由任务控制路径置位 `volatile running`，依赖 run 循环自身回到检查点。
- **触发时序**: 作业启动时 broker 不可达/元数据慢 → run() 卡在 `partitionsFor(topic)`（KafkaConsumer 默认 `max.block.ms=60s`，且 `consumerProps` 可被用户放大）→ 用户取消作业 → `cancel()` 置 running=false 但无 `wakeup()` 打断阻塞的 consumer 调用 → 取消挂起至多 max.block.ms 才生效；期间 task 无法响应停机/恢复编排。
- **现状**: 稳态循环取消语义良好（poll 200ms 有界 + 逐记录检查 `!running`）；缺口仅在启动期元数据调用与 `rateLimiter.acquire`（后者为有界分片睡眠，已声明）。`checkConnection()` 探针路径同样无超时风险（probe 亦走 `partitionsFor`）。
- **风险**: 取消/恢复编排延迟被放大到分钟级；恢复窗口内旧任务不退场，可能拖长 fencing 窗口。
- **建议**: `cancel()` 中对持有的 consumer 引用调用 `wakeup()`（字段化保存 run() 内的 consumer 引用，`volatile KafkaConsumer`），对齐 Flink Kafka 连接器的标准取消形态；并为 `partitionsFor` 前置一次 `wakeup()` 保护（consumer 复用 wakeup 语义一次一击）。
- **失效面枚举**: ① `KafkaSourceFunction.cancel`（本条）；② `MessageSourceFunction.cancel`（latch countdown ≤1s 退出——干净）；③ `DebeziumCdcSourceFunction.cancel`（latch + `source.stop()`——干净）；④ `BatchLoaderSourceReader`（同步 pull，无嵌入线程——不适用）；⑤ `KafkaTwoPhaseCommitSink`（无独立线程——不适用）。同族仅 kafka source 一处缺 wakeup。
- **信心水平**: 确定（机制确定；触发需启动期 broker 异常窗口）
- **误报排除**: 非「有界声明」——owner doc 仅声明限速退化代价（checkpoint/cancel 时延随速率退化，WI5），未声明元数据拉取期的取消挂起；200ms poll 的有界性不覆盖 `partitionsFor`。
- **复核状态**: 已保留（P2，2026-10-14 复核）——`cancel()` 仅置位 running、无 wakeup 证实；KafkaConsumer 对线程中断不响应（R3 证实的 SubtaskTask interrupt 不解除 poll/partitionsFor 阻塞），取消延迟上界 = max.block.ms/default.api.timeout.ms（默认 60s、用户可放大），时序具体。归属：本 campaign 唯一登记（C7-H6 仅从 run 侧资源配对视角判「可接受」，未评 cancel 响应性）；无历史先例。

### [nop-stream-state-connectors-C3-006] Debezium 失败监听静态注册表按键覆盖——同名连接器实例的引擎失败信号串号

- **文件**: `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:248-256,568-570`（载体 `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:44,52-69`）
- **证据片段**:
  ```java
  // DebeziumEngineWrapper（载体，out-of-scope 只作事实引用）
  private static final Map<String, Consumer<Throwable>> FAILURE_LISTENERS = new ConcurrentHashMap<>();
  public static void registerFailureListener(String connectorName, Consumer<Throwable> listener) {
      ...
      FAILURE_LISTENERS.put(connectorName, listener);        // 同名直接覆盖
  ```
  ```java
  // DebeziumCdcSourceFunction.run —— 单实例模式用裸名注册
  DebeziumEngineWrapper.registerFailureListener(connectorName, error -> {
      if (pendingError == null) { pendingError = error; }    // 错误归属由注册表键决定
      ...
  ```
- **严重程度**: P2
- **并发场景**: 同一 JVM 内两条嵌入管线各自运行 `DebeziumCdcSourceFunction.run()`；`resolveConnectorName()` 在 `totalParallelism<=1` 时返回 **裸 base 名**（实例名限定仅在 B 层并行模式 `>1` 时加 `-<subtaskIndex>` 后缀，:568-570）。
- **触发时序**: 管线 A、B 同 JVM 部署且 `DebeziumConfig.name` 相同。T1：A 的 run() 注册 listener-A（键=name）；T2：B 的 run() 注册 listener-B → **覆盖** listener-A。T3：A 的引擎终态失败 → `notifyEngineFailure(name)` 命中 listener-B → 错误写进 B 的 `pendingError`（B 因假失败重启，A 的 task RUNNING 且 CDC 流已死——恰是 R5-CON-03 要消灭的「静默死流」形态）。T4：A 退出时 finally `unregisterFailureListener(name)` 移除的是 B 的注册 → B 失去失败可见性。
- **现状**: register/unregister 在单 run 内成对（F8 对称），但注册表无同名冲突守卫；offset 侧同风险由 AR-03 首跑 clear 缓解（不同问题面），失败监听侧无任何缓解。
- **风险**: 多管线共驻 JVM（嵌入式编排/data-migration 场景）时引擎故障路由错乱：故障管线假活、健康管线假死，恢复语义双向失真。
- **建议**: ① 注册时检测键已存在即 typed 拒绝（对齐 AR-03 对同名桶的 fail-fast 口径）；② 或把注册表键改为实例级唯一句柄（run() 持有注册 token，unregister 按 token 而非名字）；载体改动落在 nop-message-debezium（本次 out-of-scope，跨模块修复建议移交）。
- **失效面枚举**: ① `DebeziumEngineWrapper.FAILURE_LISTENERS`（载体）+ `DebeziumCdcSourceFunction.run` 注册点（in-scope 调用面，本条）；② `NopStreamOffsetBackingStore.REGISTRY` 同名桶（同键覆盖族——AR-03 clear 只护首跑，双活同名仍互覆 offset，相邻 face 一并登记）；③ B 层并行模式实例名（`instanceName` 加后缀——该模式天然免疫，干净）。
- **信心水平**: 很可能（覆盖语义读码确定；触发需同名双实例部署这一配置错误前提，且该前提未被任何守卫拦截）
- **误报排除**: 非「低频启动路径粗粒度 synchronized」类豁免——这是跨管线故障信号错投，直接违反 owner doc R5-CON-03 的承诺（「引擎终态失败必须 fail THIS task」）。
- **复核状态**: 已保留（P2，2026-10-14 复核）——`FAILURE_LISTENERS.put` 同名覆盖证实（载体 javadoc 自述「replaces any previous listener」是机制文档而非边界声明，且无同名冲突守卫）；`totalParallelism<=1` 裸名注册证实；同名双管线串号 T1-T4 时序成立且无任何组件拦截。归属：本 campaign 唯一登记；与 C3-014 风险③共享 `NopStreamOffsetBackingStore` 同名互覆相邻面。

### [nop-stream-state-connectors-C3-007] Avro decode 的 reader schema 兜底 catch 面宽于声明理由——registry 不可达时静默关闭 schema 演进

- **文件**: `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroWireFormat.java:111-117`
- **证据片段**:
  ```java
  private Schema readerSchemaFor(String subject, Schema writerSchema) {
      try {
          return client.getLatestSchema(subject).schema();
      } catch (StreamAvroException e) {
          return writerSchema;      // 注册表宕机同样落这里——演进静默失效
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 单 task 线程 decode 热路径；触发面是 registry 可用性状态机（缓存已预热后 registry 宕机），与并发交叠无关但属于「异常被吞后语义漂移」（F9）。
- **触发时序**: T1：subject=v 的 v1/v2 schemaId 均已进 `schemaByIdCache`（前期正常解码预热）。T2：registry 宕机。T3：携带新增字段的 v2 记录到达 → `getSchemaById(v2Id)` 命中缓存 → `getLatestSchema(v)` 抛 `StreamAvroException`（registry 不可达）→ 兜底返回 **writer schema** → v2 记录按「writer=v2 / reader=v2」解码，latest reader schema 的 default/alias 规则全部跳过 → 下游读到 null 而非声明的 default。T4：registry 恢复后同样记录读出 default——同一条记录两种解码结果，无任何日志/指标。
- **现状**: 代码注释声明的兜底理由仅是「first-write-before-register race in some producers」（subject 尚无 latest 可查）；实际 catch 覆盖了 `getLatestSchema` 的全部失败面（网络不可达/HTTP 非 200/解析失败），与 owner doc「失败面全 typed：registry 不可达……均抛 StreamAvroException（**不静默降级**）」（nop-stream-connectors.md WI18 节）直接冲突。
- **风险**: registry 故障窗口内的静默语义降级不可观测；下游基于 default 值的业务逻辑在故障前后行为不一致。
- **建议**: 区分两类失败：`getLatestSchema` 增加「subject 无 latest」（HTTP 404）与「registry 不可达」的可判别错误（404 保留兜底，注释理由成立；不可达/5xx 改为 typed 上抛，与 owner doc 对齐）；最低成本方案是兜底分支补一条 WARN 日志使降级可观测。
- **失效面枚举**: `AvroWireFormat` 解码路径共 2 个 registry 依赖点：`getSchemaById`（cache miss 时异常上抛——干净）；`readerSchemaFor`（catch 兜底——本条，唯一漏修点）。同族 catch-and-fallback 扫描：模块内仅此一处 `catch (StreamAvroException)`。
- **信心水平**: 确定（行为确定；是否算缺陷取决于 owner doc「不静默降级」承诺的覆盖范围——本审计判定冲突成立）
- **误报排除**: 非「已声明边界」——声明过的边界是「encoder 侧 schemaId 缓存不感知换版」（WI18 已知边界），与本条 decode 侧兜底是两个面；兜底本身有注释但注释理由不能覆盖 registry 宕机形态。
- **复核状态**: 已保留（P2，2026-10-14 复核）——`catch(StreamAvroException)→writerSchema` 兜底证实；`getLatestSchema` 失败面经 `readJsonResponse` 全部折进 StreamAvroException（IO 异常/非 200），兜底覆盖 registry 不可达证实；与 owner doc `nop-stream-connectors.md:114`「失败面全 typed……（不静默降级）」直接冲突，文档冲突成立。归属：同 campaign C7-006 相邻（其登记重试无退避面，本条为兜底语义面），7.1 cross-ref。

### [nop-stream-state-connectors-C3-008] file 2PC sink 跨 JVM manifest 文件锁等待无超时——对端 JVM 挂死时 commit 无限阻塞（理论风险）

- **文件**: `nop-stream/nop-stream-connector/file/FileTwoPhaseCommitSink.java:389-404`
- **证据片段**:
  ```java
  private void withManifestLock(FileTwoPhaseCommitSinkAction action) throws Exception {
      ReentrantLock jvmLock = MANIFEST_JVM_LOCKS.computeIfAbsent(...);
      jvmLock.lock();                                    // 无超时
      try (FileChannel channel = FileChannel.open(outputDirPath.resolve(MANIFEST_LOCK_FILE),
              StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
          FileLock fileLock = channel.lock();            // 跨 JVM 阻塞等锁，无超时
  ```
- **严重程度**: P2（理论风险，未具体化到生产可复现时序）
- **并发场景**: 并行 subtask 的 commit 线程（commit 执行器）跨 JVM 竞争共享存储上的 `.manifest.lock`；JVM 内层由 ReentrantLock 串行。
- **触发时序**: 理论链：JVM-A 的 commit 在 `updateManifestAtomically` 中间因存储层挂起（NFS staleness 等）长期持锁不返 → JVM-B 的同目录 commit 在 `channel.lock()` 无限等待 → B 的 checkpoint commit 超时 → 任务失败。对端进程 **崩溃** 时 OS 释放锁（常见路径无害），故需要「对端活但挂死」这一罕见前提，无法给出可复现的生产时序——按证据标准降级并标注。
- **现状**: 所有等待点中唯一除 C3-003 外的无超时阻塞；JVM 内 ReentrantLock 亦无 `tryLock` 超时，但同 JVM 对端线程挂死可经线程栈诊断（`/jobs/{jobId}/threaddump` 可见）。
- **风险**: 罕见前提下的 commit 停摆叠加 checkpoint 超时连锁；可观测性依赖运维手动查线程栈。
- **建议**: `channel.lock()` 换 `tryLock(wait)` 有界等待（超时 typed 失败，等待期间持锁者信息可通过锁文件内嵌 pid/host 行辅助诊断）；JVM 内 `jvmLock.tryLock(timeout)` 同步改造。优先级低于 C3-001/002/003。
- **失效面枚举**: 8 模块全部阻塞等待点：`MessageSourceReader.queue.poll(100ms)/join(5s)`（有界——干净）、`DebeziumCdcSourceReader` 同形（有界——干净）、`DebeziumCdcSourceFunction` completionLatch `await(1s)`（有界——干净）、`KafkaSourceFunction.poll(200ms)`（有界；启动期例外见 C3-005）、`AvroSchemaRegistryClient.http.send`（无界——C3-003）、`FileTwoPhaseCommitSink` jvmLock+fileLock（无界——本条）。同族仅此一处文件锁无界等待。
- **信心水平**: 有趣的猜测（机制存在、生产触发前提罕见）
- **误报排除**: 不按「低频路径粗粒度锁」豁免，因为它位于 exactly-once 提交主路径且跨 JVM；但因写不出具体可复现时序，按提示词纪律降 P2 标注理论风险。
- **复核状态**: 已保留（P2，2026-10-14 复核）——`jvmLock.lock()`/`channel.lock()` 无超时逐行证实；触发前提=对端 JVM「活而挂死」持锁不返，写不出生产可复现时序——维持 P2 理论风险档（不降 P3：位于 exactly-once 提交主路径且跨 JVM）。归属：同 campaign 双向重复 ←C2-005（7.1 归并）。

---

## 覆盖度与残留边界

- **已覆盖**: CepOperator 全部 timer/callback 面（A1/A2）、两个嵌入线程桥（A3/A4）、Kafka 双端（A6 + C3-005）、三实现 2PC sink 的 commit 跨线程面（A7）、RocksDB 增量/全量快照（A9）、avro 格式层（A8/C3-003/C3-007）、全部 4 个 enumerator 的恢复/分配时序、F1 全路径核对（零发现）、F2 全路径核对（1 个对偶面缺陷 C3-004 + 2 个已声明边界）。
- **边界声明**: ① core/runtime 侧契约（`TaskProcessingTimeService`/`LocalSourceCoordinator`/`StreamSinkOperator`/`TwoPhaseCommitSinkFunction` 基类）按使用方式审，不重审其内部实现（平台设施豁免）；② `NopStreamOffsetBackingStore`/`DebeziumEngineWrapper` 载体代码属 nop-message-debezium（不在 8 模块清单），仅作事实引用，修复建议需跨模块移交；③ baseline C3 grep（CompletableFuture 族）为空与人工全量核对结论一致——8 模块确无 future 链，异步面即上表 A1-A10。
- **计数**: P0 = 0；P1 = 4（C3-001/002/003/004）；P2 = 3（C3-005/006/007）；P3 = 0（C3-008 按证据标准落在 P2 理论风险档）。

# C3 异步链路正确性审计 — nop-stream-state-connectors · 深挖第 2 轮追加

## 深挖第 2 轮追加

- **审计维度**: C3（异步链路正确性），第 2 轮深挖追加轮
- **审计对象**: 与第 1 轮相同的 8 模块 `src/main/java`（nop-stream-cep、nop-stream-rocksdb、nop-stream-connector、nop-stream-connector-batch、nop-stream-connector-jdbc、nop-stream-connector-debezium、nop-stream-connector-kafka、nop-stream-format-avro）
- **审计日期**: 2026-10-14
- **盲审边界遵守**: 本轮仅读取本维度第 1 轮文件（`C3-async-chains.md`）与 live code；未读取 ai-dev/audits/ 其他目录、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/。
- **第 1 轮已覆盖面（不再重复）**: F1 快照零发现；F2 对偶面 C3-004；异步入口 A1–A10 清单；C3-001（kafka producer 泄漏）、C3-002（JDBC 台账无界）、C3-003（registry 无超时）、C3-005（kafka cancel 无 wakeup）、C3-006（失败监听串号）、C3-007（avro 兜底吞降级）、C3-008（manifest 锁无界等待）。

### 第 2 轮盲区盘点（第 1 轮未覆盖的回调/线程入口清单）

| # | 入口 | 位置 | 判定 |
|---|---|---|---|
| B1 | `MessageSourceFunction.onMessage`（IMessageConsumer 回调，消息后端分发线程） | connector/MessageSourceFunction.java:139-179 | 类型不匹配/collect 失败 capture-and-rethrow（P1-9）闭环；R5-CON-04 本地引用防 subscribe/cancel 竞态闭环；`synchronized(ctx)` 串行化 collect——干净 |
| B2 | `MessageSourceReader`/`DebeziumCdcSourceReader` 的 `QueueSourceContext`（桥接 collect 全部 6 方法） | MessageSourceReader.java:219-258、DebeziumCdcSourceReader.java:324-361 | emitWatermark/markAsTemporarilyIdle/getProcessingTime/isCancelled 均无共享态——干净；**collect 的阻塞 put 执行线程归属 → C3-009** |
| B3 | 桥 runner 线程异常传播（batch/debezium 深挖指令面） | MessageSourceReader.java:94-105、DebeziumCdcSourceReader.java:148-158 | `runnerFailure` stash → pollNext 空闲/非空闲两条分支都 typed 重抛；`launchEngine` 中 `queue` 先于 `thread.start()` 赋值，pollNext 不会 NPE——干净；**close 路径 → C3-012** |
| B4 | `runEntered` 重入守卫 | DebeziumCdcSourceFunction.java:92,227-229,317 | 守卫存在但行为是静默 return → **C3-011** |
| B5 | `BatchLoaderSourceFunction` run/cancel（legacy 占线程模型） | connector-batch/BatchLoaderSourceFunction.java:97-154 | **取消无中断响应点 → C3-010**（第 1 轮 C3-005 失效面枚举只列了 `BatchLoaderSourceReader`，legacy function 漏盘） |
| B6 | `BatchLoaderSourceReader`（FLIP-27 同步 pull，无嵌入线程） | connector-batch/BatchLoaderSourceReader.java 全文 | loader 生命周期 adopt/close 配对；`volatile currentOffset` 弱一致快照 + at-least-once 回放——干净（close 吞 loader close 异常见 C3-012 失效面登记） |
| B7 | `DebeziumEngineWrapper` 引擎回调（CompletionCallback/ConnectorCallback/worker catch） | 载体 nop-message-debezium/DebeziumEngineWrapper.java:159-173,227-275 | `notifyEngineFailure` 对 listener 异常 catch 防杀 worker——干净（载体代码按事实引用，修复建议跨模块移交） |
| B8 | RocksDB metrics/file source/enumerator 面 | RocksDBMetricsRecorder.java:73（同步 meter 注册）、FileSourceReader/FileSplitEnumerator/MessageTopicSplitEnumerator | 全同步 pull 模型，无回调线程面——干净 |

---

## 发现条目

### [nop-stream-state-connectors-C3-009] Debezium 嵌入引擎的事件分发与阻塞背压绑定在平台全局 worker 池（max 30）——管线停顿/池耗尽时 CDC 静默死流且拖饿平台共租方

- **文件**: `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceReader.java:260-277,324-335`（桥接 collect 阻塞 put）；`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:260-277`（legacy 直采路径同耦合）；载体事实引用 `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:159-173,185-190` + `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:85-95`、`CommonConfigs.java:26-28`（`global-worker.maxPoolSize` 默认 30）、`ThreadPoolConfig.java:25`（queueCapacity 默认 10000）
- **证据片段**:
  ```java
  // DebeziumCdcSourceReader.java —— 桥接 collect：阻塞 put（满队列上无限期挂起）
  @Override
  public void collect(ChangeEvent element) {
      try {
          queue.put(element);              // 执行线程 = 引擎分发线程（见下）
      } catch (InterruptedException e) { ...
  ```
  ```java
  // DebeziumEngineWrapper.java（载体）—— 引擎 run() 与事件回调都内联在 globalWorker 线程
  GlobalExecutors.globalWorker().execute(() -> {
      try { running.set(true); engine.run(); ...
  private void handleDebeziumEvent(...) { ... changeEventConsumer.accept(changeEvent); }  // 同步内联
  ```
- **严重程度**: P1
- **并发场景**: 每条嵌入 Debezium 管线的 `engine.run()` **全生命周期占住一个 `GlobalExecutors.globalWorker()` 线程**（该池 maxPoolSize 默认 30、队列 10000，为平台共享池，共租方含 nop-datav 导出/仪表盘、nop-batch 导入导出、nop-ai-shell 等）；事件经 `handleDebeziumEvent` 同步内联回调到 in-scope 的 subscribe lambda → `ctx.collect`。reader 桥的背压阻塞点 = 该全局 worker 线程上的 `queue.put`；legacy function 路径则直接在全局 worker 线程上阻塞在算子 collect。
- **触发时序**: T1：管线 P 下游变慢 → task 线程 pollNext 消费慢于产出 → queue(256) 满 → P 的全局 worker 线程在 `queue.put` 上阻塞，阻塞时长 = 下游恢复时长（无上界）。T2（耗尽面）：同 JVM 并发引擎数（多条 CDC 管线 / B 层并行模式的 per-subtask 实例）+ 被停顿管线占住 + 共租任务占用的 worker 数 > 30 → 第 31 条管线的 `wrapper.start()` 把 `engine.run()` 提交进 10000 深队列后**永不执行**：`DebeziumMessageSource.started=true`、subscribe 正常返回、reader runner 阻塞在 `completionLatch.await` 等事件——任务 RUNNING、无事件、无异常、offset 不变使 checkpoint 照常成功（F5「不抛异常也不产出」的完全体）。T2'（泄漏放大面）：停顿管线因 checkpoint 超时失败 → supervisor 重建 → 新引擎提交 globalWorker；旧引擎 worker 仍阻塞在废弃队列的 put 上（若 Debezium `engine.close()` 不中断 run 线程——未证实）→ 每轮重建净耗 1 个全局 worker，单管线多轮重建即可耗尽全局池。
- **现状**: 桥接设计注释钉定「queue-full = 订阅线程阻塞（背压 parity）」，但未审视订阅线程的身份是平台全局共享池线程；`InterruptedException` 分支存在（作者预期过中断），但整条链路上没有任何组件会中断该线程。
- **风险**: ① 平台全局 30 线程预算被长驻 CDC 引擎与管线停顿侵蚀，共租功能（导出/批处理/AI-shell）随之排队劣化；② 池耗尽后新 CDC 管线静默死流（无错误、checkpoint 正常），违背 owner doc R5-CON-03「引擎终态失败必须可见」的精神——这里是「引擎从未启动」的不可见形态；③ 若 close 不中断，泄漏按重建次数单调增长（F4 内存/线程账本族）。
- **建议**: ① 为 Debezium 引擎建立**专用 executor**（平台设施 `IThreadPoolExecutor` + `ThreadPoolConfig` 命名池，容量与声明管线数匹配、有界队列 + `QueueOverflowPolicy` 显式拒绝），替换 `DebeziumEngineWrapper` 中的 `GlobalExecutors.globalWorker()`（载体改动，跨模块移交 nop-message-debezium）；② in-scope 侧将 `QueueSourceContext.collect` 的 `queue.put` 改为 `offer(timeout)` + 检查 `running`/`isCancelled()` 的有界等待，超时 typed 抛错使引擎终态可见，消除无限期占线；③ 为「引擎提交后 N 秒未观察到首事件/connectorStarted」加启动看门狗（对齐 F5）。
- **失效面枚举**（F3 同族清扫——8 模块全部「异步回调阻塞点绑定的线程池」）: ① `DebeziumCdcSourceReader.QueueSourceContext.collect`（本条，globalWorker——缺陷）；② `DebeziumCdcSourceFunction.run` legacy 直采路径（同耦合且无 256 缓冲，同一根因，同批修）；③ `MessageSourceReader.QueueSourceContext.collect`（分发线程 = 消息后端线程：LocalMessageService 为**发送方线程**同步分发——背压传导给发送方属声明语义，干净；Kafka/Pulsar 后端线程模型属各自后端模块，不在本审计对象）；④ 两个桥 runner 线程（`completionLatch.await(1s)` 有界——干净）；⑤ `CepOperator` timer 回调（task 线程 mailbox 串行——干净）。
- **信心水平**: 很可能（线程绑定与内联分发从 in-scope + 载体代码直接证实；T2' 的「close 是否中断 run 线程」依赖 Debezium 内部实现未证实，已标注为条件分支）
- **误报排除**: 不是「有界声明的池」豁免——缺陷不在池本身而在**使用方式**：长驻阻塞任务 + 无限阻塞回调绑定到全局共享池，且无任何容量守卫/看门狗；也不是「背压语义已声明」豁免——声明的是 queue-full 阻塞，未声明阻塞发生在平台全局池线程上。
- **复核状态**: 已保留（P1，2026-10-14 复核，按 R3 修正口径）——载体 `GlobalExecutors.globalWorker().execute(() -> engine.run())`、池默认 maxPoolSize=30（CommonConfigs.java:27）、事件同步内联分发、in-scope collect 阻塞 put 逐项证实；池耗尽→新引擎提交 10000 深队列永不执行→静默死流时序具体。R3 自我裁决的 T2' 泄漏分支推翻成立（close 协作式释放），核心占线结论不受影响——复核采纳修正后口径。归属：同 campaign 双向重复 ←C4-004（7.1 归并）。

### [nop-stream-state-connectors-C3-010] BatchLoaderSourceFunction.cancel 无中断响应点——在途 loader.load 阻塞期间取消/停机挂起无上界（C3-005 同族漏盘点）

- **文件**: `nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceFunction.java:71,115-116,151-154`（同族 `BatchLoaderSourceReader.java:170` pollNext 内联 `loader.load` 同缺）
- **证据片段**:
  ```java
  while (running) {
      List<S> batch = loader.load(batchSize, chunkContext);   // 唯一阻塞点，无中断响应
      ...
  @Override
  public void cancel() {
      running = false;            // 仅 volatile 置位
  }                               // 全类无 Thread.interrupt / loader 取消通道
  ```
- **严重程度**: P2
- **并发场景**: task 线程执行 run()（legacy source 占线程模型）；cancel 由任务控制路径置位 `volatile running`，生效点仅限批间/记录间检查。已核实 `IBatchTaskContext`（nop-batch-core，接口全文）**没有任何取消钩子**（无 isCancelled/cancel），适配器也无通道把 stream 取消传导进 loader。
- **触发时序**: loader 为 owner doc 点名的 JDBC 形态（`checkConnection` 注释自证「a JDBC loader opens its connection」）。T1：`loader.load(batchSize, ctx)` 在途执行长查询/大批次拉取。T2：用户取消作业 → `cancel()` 置 running=false，但 load 不响应 → 取消生效时刻被推迟到 load 自然返回（查询时长无上界）；期间任务不响应停机/恢复编排，恢复窗口内旧任务不退场。
- **现状**: run 循环的批间/记录间 `running` 检查是良好的（细粒度响应点）；缺口仅在单次 `load()` 在途期间无任何解除手段。第 1 轮 C3-005 的失效面枚举第 ④ 项把 batch 系标为「不适用」，但所列对象是 `BatchLoaderSourceReader`（同步 pull 无嵌入线程），legacy `BatchLoaderSourceFunction` 的占线程模型与 KafkaSourceFunction 同形，属漏盘点。
- **风险**: 取消/恢复编排延迟被 loader 查询时长放大（分钟级可复现）；与 C3-005 相比缺少 `wakeup()` 这类现成原语，属设计缺口而非漏调用。
- **建议**: `cancel()` 捕获并中断 run 线程（字段化 `volatile Thread runThread`，run 入口记录、finally 清空——对 `loader.load` 内的 JDBC 调用多数驱动会响应 `Statement.cancel`/interrupt）；或在 `IBatchTaskContext` 上增加取消钩子并让适配器在 cancel() 时置位（跨模块改动）；最低成本先在 owner doc 声明「取消时延上界 = 单批 load 时长」。
- **失效面枚举**: ① `BatchLoaderSourceFunction.cancel`（本条）；② `BatchLoaderSourceReader.pollNext` 的 `loader.load`（task 线程内联阻塞，mailbox 取消邮件被同一阻塞点推迟——同族未修，一并登记）；③ `KafkaSourceFunction.cancel`（C3-005 已登记，wakeup 缺失）；④ `MessageSourceFunction`（1s latch 有界——干净）；⑤ `DebeziumCdcSourceFunction`（1s latch + sub.cancel + source.stop——干净）；⑥ `BatchConsumerSinkFunction`（同步 flush，无长阻塞挂起点——干净）。
- **信心水平**: 确定（机制确定；触发需要长查询窗口）
- **误报排除**: 非「低频路径粗粒度等待」豁免——cancel 响应性是 owner doc WI5 声明面（取消时延随限速退化已声明，但 loader 查询时长从未声明）；也不是「已声明边界」——无任何文档声明该取消挂起形态。
- **复核状态**: 已保留（P2，2026-10-14 复核）——`cancel()` 仅置位、`loader.load` 唯一阻塞点、`IBatchTaskContext` 无取消钩子证实；R3 精化（SubtaskTask interrupt 已递送，JDBC load 是否响应取决于驱动）不改变函数级取消通道缺失。触发=长查询窗口，时序具体。归属：本 campaign 唯一登记（C4:348 仅核 loader close 配对，未评 cancel 响应性）；无历史先例。

### [nop-stream-state-connectors-C3-011] Debezium run() 重入守卫对并发双进入静默 return——检测到异常即制造「正常 EOS」，与本模块已消灭的静默死流家族同型

- **文件**: `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:92,226-229,317`
- **证据片段**:
  ```java
  private final AtomicBoolean runEntered = new AtomicBoolean(false);
  ...
  public void run(SourceContext<ChangeEvent> ctx) throws Exception {
      if (!runEntered.compareAndSet(false, true)) {
          return;                        // CAS 失败 = 检测到并发双进入 → 静默 return
      }                                  // 任务立即被判定"正常完成"(EOS)
  ...
  } finally {
      ...
      runEntered.set(false);
  }
  ```
- **严重程度**: P2
- **并发场景**: 旧任务线程的 run() 尚未走完 finally（`source.stop()` 引擎关闭耗时长于新任务启动间隔）时，region 重启在同一实例上的新任务线程进入 run()。前置条件是 core 侧存在「旧 run 未返回即启动新 run」的同实例重启形态——该形态正是守卫注释与同类生命周期重置注释（`run()` 内 running/draining/pendingError 重置、「a region restart reuses this instance」）自己预期并防御的异常路径。
- **触发时序**: T1：旧 run 在 finally 的 `source.stop()`（Debezium engine close，耗时无界）中阻塞，`runEntered` 仍为 true。T2：core 重启同实例新任务线程 → `run()` CAS 失败 → 静默 return → 新任务零事件、无异常地「正常完成」→ 作业被标记 FINISHED 而非 FAILED，CDC 流实际已死且无任何 typed 信号。对比：守卫若 typed 抛错，作业进入 FAILED → 恢复语义接管。
- **现状**: 守卫的目的（F10 fail-fast 拦截违约复用）与行为（静默成功）相反——它把「检测到的异常」转译为「最恶性的静默 EOS」，恰是 P1-9/R5-CON-02 审计史认定的「run 循环静默退出被误报为成功 EOS」同族形态。同族守卫缺失面见失效面枚举。
- **风险**: 守卫唯一会被触发的场景恰是异常场景，其静默 return 使该场景下作业假完成、无重试、无告警；数据流死亡且被标记为成功，比抛错更难发现。
- **建议**: CAS 失败分支改为 typed 拒绝：`throw new StreamException(ERR_STREAM_STATE_ERROR).param(ARG_DETAIL, "run() re-entered while a previous run is still active")`（一行修复，语义与 R5-CON-02/03 的 no-silent-EOS 纪律对齐）。
- **失效面枚举**（F3 同族清扫——8 模块全部 run() 重入防御形态）: ① `DebeziumCdcSourceFunction.runEntered`（有守卫但静默——本条）；② `MessageSourceFunction.run`（**无守卫**——同异常形态下会双订阅，同一消息双份投递，退化更重，建议同批补 typed 守卫）；③ `BatchLoaderSourceFunction.run`（无守卫——双 run 会双份发射全量数据集；parallelism>1 已有 validateParallelism 挡位但挡不住同实例双线程）；④ `KafkaSourceFunction.run`（无守卫——双 run 会双 consumer 同 group 分区重复分配，靠 broker 侧 rebalance 部分兜底）；⑤ `MessageSourceReader`/`DebeziumCdcSourceReader`（reader 形态由 `launchEngine` 的 `runnerThread != null` 守卫，且为同线程 mailbox 串行调用，无并发双进入面——干净）。
- **信心水平**: 确定（行为确定；触发依赖 core 同实例并发重启这一守卫自身防御的前置形态）
- **误报排除**: 非「已声明边界」——无文档声明双进入时静默完成是预期行为；也不是误报校准中的「原语使用」类——缺陷在守卫的响应语义，不在 AtomicBoolean 本身。
- **复核状态**: 已保留（P2，2026-10-14 复核）——CAS 失败静默 return 证实；R3 触发面收窄裁决成立（SupervisionLoop fail-loud zombie timeout + SubtaskTask:166 interrupt 结构性阻断运行时同实例双进入），收窄后触发面=程序化直构路径（owner doc 声明的合法用法）——守卫把检测到的异常转译为假 EOS 的响应语义缺陷成立。归属：历史先例=2026-06-05 r2 审计建议③（runEntered CAS 化）已落地，本条为该修复的响应语义残留新面，非重复。

### [nop-stream-state-connectors-C3-012] 桥 reader close() 的 join 超时分支先抛后 clear 且全程无 interrupt 兜底——超时路径上阻塞在废弃队列 put 的分发线程永久挂起

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceReader.java:195-212,221-231`（`DebeziumCdcSourceReader.java:294-312,326-335` 同形）
- **证据片段**:
  ```java
  public void close() throws Exception {
      running = false;
      embeddedFunction.cancel();
      Thread thread = runnerThread;
      if (thread != null) {
          thread.join(CLOSE_JOIN_TIMEOUT_MILLIS);
          if (thread.isAlive()) {
              throw ...;                       // 先抛 → 下方 queue.clear() 永不执行
          }
      }
      if (queue != null) { queue.clear(); }    // 唯一能解除阻塞 put 的语句
  }
  ```
- **严重程度**: P2
- **并发场景**: 消息/引擎分发线程阻塞在 `queue.put`（满队列）× task 线程执行 close()。runner 线程本身经 `completionLatch` countdown ≤1s 退出（干净）；风险面在 join 超时分支与「无任何 Thread.interrupt 兜底」这两个组合缺口。
- **触发时序**: T1：分发线程（Local 总线=发送方业务线程；Debezium=globalWorker，见 C3-009）阻塞在 `queue.put`。T2：close() → runner 卡在 finally 的 `source.stop()`（引擎关闭耗时 >5s，无界）→ join 超时 → typed 异常先抛 → `queue.clear()` 被跳过 → T1 的 put **永久阻塞**：发送方业务线程（Local 总线形态）被单条流任务的关闭路径无限期拖死，且无中断会到达（`collect` 的 InterruptedException 分支在整条链路上不可达）。
- **现状**: 正常关闭路径是闭环的（runner ≤1s 退出 → join 成功 → clear 解除在途 put）；缺口仅两个：① join 超时分支不执行 clear、不 interrupt；② close 全程无 `thread.interrupt()`，join 超时只「检测」不「解除」，与类注释宣称的「joins the daemon thread with a typed-failure timeout」的有界关闭承诺不符。
- **风险**: 一条流任务的停机故障放大为无关业务线程（消息发送方）永久挂起——故障面跨出流模块；挂死不可见（无超时、无日志），仅能靠线程栈诊断（F5）。
- **建议**: close() 用 try/finally 保证 `queue.clear()` 在 join 超时抛错前执行（解除在途 put 是无副作用的，队列随即废弃）；join 超时分支补一次 `thread.interrupt()` 给 runner 一个强制出口（collect 的 InterruptedException→typed throw 分支即可生效）；`BatchLoaderSourceReader.close`（:221-232）对 loader close 异常 `LOG.warn` 吞掉，与族内 `BatchConsumerSinkFunction.close` 的 typed 上抛纪律不一致——同批对齐。
- **失效面枚举**: ① `MessageSourceReader.close`（本条）；② `DebeziumCdcSourceReader.close`（同形同修）；③ `BatchLoaderSourceReader.close`（无线程可 join，但存在吞异常面，同批登记）；④ `KafkaSourceFunction`（consumer try-with-resources——干净）；⑤ `BatchConsumerSinkFunction.close`（flush 失败 typed 上抛——干净）。
- **信心水平**: 很可能（控制流确定；触发需要「引擎 stop >5s」或等价的 runner 迟退窗口）
- **误报排除**: 非「已声明边界」——类注释声明的契约是有界关闭 + typed 失败，实际超时路径既未解除阻塞也未中断；正常路径闭环不免除超时路径的清理不对称（F8）。
- **复核状态**: 已保留（P2，2026-10-14 复核）——join 超时分支 throw 先于 `queue.clear()`、close 全程无 interrupt 逐行证实；R3 收窄 Debezium 面（需引擎关闭预算>5s）后主触发面=Local 总线发送方业务线程成立。复核补充精化：clear 仅解除当前在途单条 put，持续泵送的发送方仍会阻塞在下一 put——缺陷面与「interrupt 兜底」建议均成立。归属：同 campaign 双向重复 ←C6-002、C6-003（C6 泄漏视角/本维度清理不对称视角，同一缺陷，7.1 归并）。

---

## 覆盖度与残留边界（第 2 轮）

- **已覆盖（本轮新增盘点）**: 第 1 轮未盘点的回调入口 B1–B8 全清单（MessageSourceFunction.onMessage、两个 QueueSourceContext 全方法、BatchLoaderSourceFunction/Reader、DebeziumEngineWrapper 引擎回调、RocksDBMetrics/file/enumerator 同步面）；batch/debezium 桥接线程异常传播闭环核对（runnerFailure stash → pollNext 双分支 typed 重抛——干净，无新发现）；取消/interrupt 语义逐入口核对（有界 latch/poll 全部确认；缺口 = C3-009/C3-010/C3-012）。
- **零发现声明面**: batch 桥接线程异常传播（batch 模块无嵌入线程，同步 pull——异常经 pollNext/consume 直接上抛，闭环）；Debezium 桥 runnerFailure 传播（第 1 轮 R5-CON-02/03 修复已覆盖，本轮验证空闲分支 `value==null && runnerFailure!=null` 也重抛——闭环）；`unregisterFailureListener(null)` 已核实 null 安全（排除了 config-error 路径 NPE 掩蔽的候选，不立项）。
- **边界声明**: ① `DebeziumEngineWrapper`/`NopStreamOffsetBackingStore`/消息后端实现（Local/Kafka/Pulsar）为载体/外部模块，仅作事实引用，C3-009 的首选修复（专用 executor）落在 nop-message-debezium，需跨模块移交；② `GlobalExecutors`/`ThreadPoolConfig` 为平台设施（nop-commons），只审使用方式；③ core 侧「同实例并发重启」形态未在 in-scope 内证实，C3-011 按守卫行为缺陷立项并标注前置条件；④ Debezium `engine.close()` 是否中断 run 线程未从依赖源码证实，C3-009 的 T2' 分支与 C3-012 的触发窗口均以此为条件标注。
- **计数（第 2 轮新增）**: P0 = 0；P1 = 1（C3-009）；P2 = 3（C3-010/011/012）。
- **终止判定**: 本轮 4 条新发现均落在第 1 轮明确未盘点的盲区（回调入口 B1–B8、close 超时路径、legacy function 取消面），其中 C3-009 为新机制（平台全局池耦合），非零散低价值；第 1/2 轮合计异步入口面 A1–A10 + B1–B8 已穷尽 8 模块全部线程/回调载体（grep 复核：`new Thread`/Executor/Timer/interrupt 全量命中均已归位）。**建议第 3 轮终止**。

# C3 异步链路正确性审计 — nop-stream-state-connectors · 深挖第 3 轮追加

## 深挖第 3 轮追加

- **审计维度**: C3（异步链路正确性），第 3 轮深挖追加轮
- **审计对象**: 与前两轮相同的 8 模块 `src/main/java`（nop-stream-cep、nop-stream-rocksdb、nop-stream-connector、nop-stream-connector-batch、nop-stream-connector-jdbc、nop-stream-connector-debezium、nop-stream-connector-kafka、nop-stream-format-avro）
- **审计日期**: 2026-10-14
- **盲审边界遵守**: 本轮仅读取本维度第 1/2 轮文件（`C3-async-chains.md`、`C3-round2.md`）与 live code；未读取 ai-dev/audits/ 其他目录、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/。core 侧文件（`MicrometerStreamTaskMetrics`/`StreamMetricsRegistries`）仅作同族事实引用（平台设施/边界声明口径），不作为独立发现对象。
- **本轮任务口径**: ① 第 2 轮新发现（C3-009 全局池占用、C3-011 重入守卫）的同族清扫；② 前两轮载体清单 A1–A10 + B1–B8 之外的残余面。
- **第 3 轮·续（同文件追加，收口第 2 轮遗留）**: 深挖指令四方向——①第 2 轮两项未证实依赖裁决（Debezium `engine.close()` 语义 / core 同实例并发重启形态）；②C3-009 GlobalExecutors 失效面独立复核；③定时器取消/重挂窗口；④线程中断响应点独立 grep 复核。发现编号说明：续轮派发指令以 C3-013 起编，但 **C3-013 已被本文件前半部分（D8 面，RocksDB gauge 注册缺陷）占用**——为保持维度 ID 空间唯一（append-only），续轮发现自 **C3-014** 起编。依赖裁决新手段：本地 Maven 依赖工件（`~/.m2/repository/io/debezium/debezium-{embedded,api}/3.7.0.Final`，与仓库 `nop-kernel/nop-dependencies` 钉定的 3.7.0.Final 构建基线一致）javap 反编译取证——第 2 轮的「依赖源码未证实」由此升级为依赖字节码直接证据。

### 第 3 轮盲区盘点（同族清扫 + 残余面核对结果）

| # | 检查面 | 方法与结果 | 判定 |
|---|---|---|---|
| D1 | C3-009 同族（8 模块内绑定全局共享池的阻塞/长驻点） | grep `GlobalExecutors|commonPool|ForkJoin` 全 8 模块 `src/main/java` = **零命中**；C3-009 的池耦合全部在载体 `DebeziumEngineWrapper`（跨模块），in-scope 无同族漏修 | 同族穷尽 |
| D2 | C3-011 同族（重入/生命周期守卫的静默响应） | grep `compareAndSet` 全 8 模块：仅 `Lockable.release/releaseUntil`（cep，task 线程独占使用，CAS 为防御性——无共享即无竞态，干净）+ `runEntered`（C3-011 本体）；grep 静默生命周期守卫形态 `if (started|initialized|running|closed...) return` = 零命中 | 同族穷尽 |
| D3 | 上下文传播轴（C3 步骤 4） | grep `ThreadLocal|ContextProvider|IContext` 全 8 模块 = **零命中**——本审计对象无任何跨线程上下文依赖，该轴为空 | 空 |
| D4 | 定时器/调度面 | grep `registerTimer|scheduleAtFixedRate|ScheduledExecutor|new Timer` = 仅 CepOperator（A1/A2 已审）；无新增调度面 | 已覆盖 |
| D5 | static 可变集合残余 | grep 全 8 模块：仅 `FileTwoPhaseCommitSink.MANIFEST_JVM_LOCKS`（静态 CHM 按 outputDir 缓存 ReentrantLock，条目不删——上界 = 去重目录数，进程生命周期内有界；其并发正确性面已由 A10/C3-008 覆盖；低于立项阈值，不单独立项） | 低于阈值 |
| D6 | JDBC 2PC 跨线程复合操作 | 逐行核对 `commit`/`saveState`/`abort`/`rollback`：数据行 + 台账行同一事务 + v2 主键 `(namespace,epoch,subtask)` 使并发双 commit 必然一方 PK 冲突整事务回滚（check-then-act 竞态被事务原子性兜住）；`ensureInitialized`/`resolveKeyedColumns` 的 `synchronized(this)` 与 `currentBuffer` 锁无嵌套（saveState 先 release `this` 再取 currentBuffer，commit 线程不触碰 currentBuffer）——无死锁环 | 干净 |
| D7 | Kafka 2PC 事务状态机跨线程面 | 复核 `KafkaTwoPhaseCommitSink`：`commit(epoch)` 为纯高水位比较（不触碰 producer）；`saveState/preCommit/invoke` 全在 task 线程 barrier 串行；`recover()` 仅恢复构建期调用（run 之前）——无 task/commit 线程并发进入 `ensureTransactionOpen` 的具体交错；`producer/transactionOpen/initDone/committedHighWater` 全 volatile。维持第 1 轮 A7「干净」结论 | 干净 |
| D8 | RocksDB 状态后端 open/close/metrics 面（B8 判定复查） | `RocksDBKeyedStateBackend.close` 的 `metricsRecorder.close()` 先于全部 native close（:979-1013，use-after-free 面闭环）；但 **gauge 注册面发现 B8「干净」判定遗漏的共享注册表重注册语义缺陷** → C3-013 | 缺陷 |
| D9 | 其余未点名文件抽查 | `MessageSinkFunction`（纯同步 send——干净）、`DebeziumSourceSupport`（无线程/回调面——干净）、`CepOperator.TimerServiceImpl`（只暴露 currentProcessingTime，无用户 timer 注册面——干净）、`FileTwoPhaseCommitSink.withManifestLock`（try/finally 锁释放配对完整——干净）、`BatchConsumerSinkFunction.flush`（失败 typed 上抛保留缓冲，无静默重试循环——干净） | 干净 |

---

## 发现条目

### [nop-stream-state-connectors-C3-013] RocksDB 状态指标 gauge 以无 tag 固定名注册进进程级共享注册表且无 rebind——任意一次任务重建后 6 个 `nop.stream.state.rocksdb.*` gauge 静默失效（NaN 后消失），与 core 侧已钉定的 holder-rebind 惯例直接背离

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:52,65-66,73-84`（注册面）；调用点 `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:278-279`（每次后端 open 都注册）；共享注册表 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/metrics/StreamMetricsRegistries.java:32-39`（进程级静态 Composite）；同族正确形态事实引用 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/metrics/MicrometerStreamTaskMetrics.java:54-59,85-90`（WI16 holder-rebind 惯例）
- **证据片段**:
  ```java
  // RocksDBMetricsRecorder.java —— 每个后端实例一个 recorder，gauge 值函数绑定实例态 dbRef
  private final AtomicReference<RocksDB> dbRef;
  public synchronized void close() { dbRef.set(null); }          // 只摘引用，不摘 meter
  public void register(MeterRegistry registry) { gauge(registry, METRIC_BLOCK_CACHE_USAGE, ...); ... }
  private void gauge(MeterRegistry registry, String meterName, String property) {
      registry.gauge(meterName, Tags.empty(), dbRef, ref -> readProperty(ref.get(), property));  // 无任何 tag、固定名
  }
  ```
  ```java
  // MicrometerStreamTaskMetrics.java（core 同族惯例，WI16 注释自证该陷阱已被平台认知）
  // Micrometer gauges hold a WEAK reference to their state object and silently drop
  // same-id re-registrations. Every WI16 gauge therefore reads through a STATIC strong
  // holder keyed by the meter identity; a task instance registers into it and recovery
  // rebuilds rebind.
  private static final ConcurrentMap<String, AtomicReference<MicrometerStreamTaskMetrics>> TASK_HOLDERS = ...
  ```
- **严重程度**: P2
- **并发场景**: gauge 值函数 `readProperty(ref.get(), ...)` 由 micrometer 抓取线程（ops server HTTP 抓取/成员 registry 轮询）在 task 线程之外异步执行——该跨线程面已被 `synchronized readProperty` + close 先序正确防护（非缺陷面）。缺陷面在**注册生命周期与异步重建的交互**：后端由 supervisor 在恢复/重启时于同一 JVM 重建（core 重建路径），新旧两个 recorder 实例先后向进程级静态 `StreamMetricsRegistries.registry()` 注册同名同空 tag 的 6 个 gauge。
- **触发时序**: T1：带 RocksDB 状态的作业运行中，recorder-0 的 6 个 gauge 已按固定名 + `Tags.empty()` 注册进静态 composite（micrometer 弱引用持有状态对象 dbRef-0）。T2：任务失败 → supervisor 重建任务 → 新后端 open → 创建 recorder-1 并 `register()` → micrometer 对同名同 tag 的 gauge **静默丢弃重注册**（返回既有 gauge-0，recorder-1 的值函数从未安装）。T3：旧后端 `close()` → `dbRef-0 = null` → gauge-0 读数变 NaN（无任何错误）。T4：旧后端对象整体不可达 → micrometer 弱引用被 GC 清除 → gauge-0 从注册表移除 → `nop.stream.state.rocksdb.block.cache.usage / memtable.usage.total / compactions.running` 等 6 项指标**永久消失**。终态取决于 GC 时序的三选一（NaN / 消失 / 偶然存活到下次重建），全程无 error/warn。
- **现状**: scrape-vs-close 竞态防护正确（close 先序 + synchronized）；`StreamMetricsRegistries` 的类注释明示其设计契约是「meters are registered once」的进程级一次注册点；而 recorder 以**每后端实例一次**的节奏向该一次注册点注册**实例态**值函数——两套契约互斥。core 侧 WI16 已为完全相同的陷阱钉定解法（per-identity tag + 静态强 holder + 重建 rebind），rocksdb metrics 未遵循。
- **风险**: 任何一次恢复/重启之后， RocksDB 状态后端的全部内部指标（块缓存/内存表/compaction 积压/键数——owner doc P-REQ-8 声明的观测面）静默失效且不可自愈；运维对 OOM 前兆（memtable/block cache 增长）与 compaction 风暴的观测在**最需要它的时刻（故障恢复后）**恰好失明。多作业共存 JVM 且 dbPath 可区分时，后注册作业的 gauge 同样被静默丢弃（只观测到先注册作业）。
- **建议**: 对齐 core 侧 WI16 既有惯例：① 把 6 个 gauge 改挂 `StreamMetricsRegistries` 外的静态强 holder（`ConcurrentMap<holderKey, AtomicReference<RocksDB>>`，holderKey 至少含 jobId|vertexId|subtask 或后端 dbPath），后端 open 时 `holder.set(db)` rebind、close 时 `holder.set(null)`（读 NaN）；② 或最低成本方案：meter 名/tag 加入后端实例身份（`Tags.of("jobId",..., "subtask",...)`）使重建各自拥有独立 meter 序列（代价是指标基数随重建增长，需配合 `registry.remove` 或接受前缀清理策略）。优先方案 ①——与 `MicrometerStreamTaskMetrics.TASK_HOLDERS` 形态逐点对齐。
- **失效面枚举**（F3 同族清扫——8 模块全部「向共享注册表注册带实例态回调的观测面」）: ① `RocksDBMetricsRecorder.register`（本条，唯一命中）；② core 侧 `MicrometerStreamTaskMetrics` WI16 gauges（holder-rebind 已闭环——同族正确样板，事实引用）；③ core 侧 `ChannelQueueGauges`（D2c 同批 holder 惯例——闭环）；④ `CepOperator` cache 统计（纯日志输出，不注册 meter——不适用）；⑤ 其余 7 模块无任何 MeterRegistry 注册点（grep 证实）。同族仅 rocksdb metrics 一处漏修。
- **信心水平**: 确定（micrometer「同名同 tag 重注册静默丢弃 + 状态对象弱引用 GC 后移除」语义由 core 侧 WI16 注释在本仓库内自证，且 `StreamMetricsRegistries` 静态单例与 `Tags.empty()` 从读码直接证实；触发条件「任意一次后端重建」为主路径恢复语义的必然事件）
- **误报排除**: 不属于「有界声明的缓存/池」或「平台设施自身实现」豁免——`StreamMetricsRegistries` 是平台设施（使用正确），缺陷在 rocksdb 使用方以实例态回调 + 无 tag 固定名向一次注册点重复注册；也非「低频路径」豁免——恢复/重启是 owner doc 声明的常规运维路径（R5/R6 恢复语义整个围绕它设计）；第 2 轮 B8 对本文件的「干净」判定仅覆盖了「同步 meter 注册」的调用线程面，未覆盖共享注册表的重注册语义，属盲区而非已知已驳回项。
- **复核状态**: 已保留（P2，2026-10-14 复核）——6 gauge 固定名 + `Tags.empty()` 绑定实例 dbRef、close 只摘引用、注册进进程级静态 `StreamMetricsRegistries`（RocksDBKeyedStateBackend:278-279）逐项证实；core `TASK_HOLDERS` 注释在仓内自证 micrometer 弱引用 + 同 id 重注册静默丢弃陷阱；任意一次后端重建即触发。归属：同 campaign 四向重复 ←C2-011、C6-010、C7-004（C2-001/C6-004 为同代码竞态兄弟面，非本条；7.1 归并）。

---

## 发现条目（第 3 轮·续 —— 第 2 轮遗留依赖收口）

### [nop-stream-state-connectors-C3-014] Debezium 引擎 STARTING_TASKS 窗口内 stop 被依赖方 IllegalStateException 按契约拒绝且被载体双层吞掉——取消/关闭路径产生永久幽灵引擎（活 binlog 连接 + globalWorker + 内部线程组），无任何组件重试关闭，同名重部署时进一步互覆 offset

- **文件**:
  - in-scope 调用面：`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceReader.java:294-312`（close）；`DebeziumCdcSourceFunction.java:297-318`（run finally）、`:330-344`（cancel）、`:346-367`（truncateForDrain）
  - 载体事实引用：`nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:204-215`（stop 吞 ISE + stopped 守卫永久 no-op）；`nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java:168-180`（stop 二次吞 + engineWrapper=null + subscriptions.clear）
  - 依赖契约证据：`io.debezium.embedded.async.AsyncEmbeddedEngine#close()`（debezium-embedded-3.7.0.Final.jar 反编译，见证据片段 3）
- **证据片段**:
  ```java
  // DebeziumEngineWrapper.stop()（载体）—— engine.close() 抛出的 IllegalStateException 被吞
  try {
      if (engine != null) {
          engine.close();
      }
  } catch (Exception e) {
      LOG.error("Error closing Debezium engine: {}", config.getName(), e);   // 仅记日志
  }
  running.set(false);   // stopped 已由 getAndSet(true) 置位——此后一切 stop() 永久 no-op
  ```
  ```java
  // DebeziumCdcSourceReader.close()（in-scope）—— 对「引擎是否真正终止」零验证
  running = false;
  if (embeddedFunction != null) {
      embeddedFunction.cancel();      // → DebeziumMessageSource.stop() → wrapper.stop() → engine.close()
  }
  Thread thread = runnerThread;
  if (thread != null) {
      thread.join(CLOSE_JOIN_TIMEOUT_MILLIS);   // runner ≤1s 经 latch 退出——join 正常成功
  ...
  if (queue != null) { queue.clear(); }         // close() 正常返回，无 typed 信号
  ```
  ```java
  // AsyncEmbeddedEngine.close()（依赖 3.7.0.Final 反编译控制流；该引擎即
  // DebeziumEngine.create(JsonByteArray) 经 ServiceLoader 唯一工厂
  // ConvertingAsyncEngineBuilderFactory 构建的实现——3.x 无经典 EmbeddedEngine）
  16: getEngineState()
  17: == STARTING_TASKS → throw IllegalStateException("Cannot stop engine while tasks are
          starting, this may lead to leaked resource. Wait for the tasks to be fully started.")
  34: == STOPPING       → throw IllegalStateException("Engine is already being shutting down.")
  52: == STOPPED        → throw IllegalStateException("Engine has been already shut down.")
  // 全类零 java/lang/Thread.interrupt 调用；关闭 = 状态机迁移 STOPPING
  //   + 内部 ExecutorService.shutdown/shutdownNow + 有界 awaitTermination/CountDownLatch.await(timeout)
  ```
- **严重程度**: P1
- **并发场景**: 任务控制路径（reader close / function cancel / truncateForDrain / run finally，task 线程或控制线程）× globalWorker 线程上的引擎启动状态机（CREATING→INITIALIZING→CREATING_TASKS→STARTING_TASKS→POLLING_TASKS）。竞态窗口 = 引擎处于 STARTING_TASKS 的时段（connector.connect + SourceTask.start：建立 DB 连接、读 binlog 位点；网络慢时达连接超时量级，秒~数十秒）。
- **触发时序**: T0：管线启动，`launchEngine` → `subscribe` → `wrapper.start()` → globalWorker 执行 `engine.run()`，引擎推进至 STARTING_TASKS。T1：任务在该窗口内被取消/失败/DRAIN（典型：用户 stop 一个刚部署的作业；部署期其他算子失败触发 region 重启）→ `DebeziumCdcSourceReader.close()` → `embeddedFunction.cancel()` → `DebeziumMessageSource.stop()` → `wrapper.stop()` → `engine.close()`。T2：引擎状态 == STARTING_TASKS → Debezium 按自身契约抛 `IllegalStateException`（其报文自证「this may lead to leaked resource」）→ `wrapper.stop()` 的 `catch (Exception)` 吞掉 → `stopped=true`。T3：runner 线程 ≤1s 经 latch 退出 → join 成功 → `queue.clear()` → reader.close() 正常返回，任务被判定干净下线，无任何 typed 信号。T4：引擎继续完成启动进入 POLLING_TASKS——活的 binlog 连接、1 个 globalWorker 线程、引擎内部 record-processing 线程池、offset store 全部存活；`dispatchEvent` 遍历已被 `subscriptions.clear()` 清空的 map → 事件静默丢弃；offset 周期提交持续推进。此后无任何组件会再次调用 `engine.close()`（wrapper 的 `stopped` 守卫 + `msgSource.engineWrapper=null` 使四个停止面全部永久 no-op）。
- **现状**: 四个 in-scope 停止面全部把「stop 调用已发出」当「引擎已终止」，无终止性验证、无重试；载体的双层 catch（wrapper + message source）把依赖方按契约抛出的 ISE 降级为 ERROR 日志。第 2 轮 C3-009/C3-012 的条件分支正以此为未证实前提；本轮从依赖工件字节码证实后，该分支从「未证实的泄漏放大面」翻转为「确定性可复现的幽灵引擎面」。
- **风险**: ① 每次命中净泄漏一个活引擎：DB 连接 + 引擎线程组 + 一个 globalWorker 名额；反复部署/取消周期线性累积（F4 账本族）；② 幽灵不可见（无异常、无事件、任务已下线——F5 完全体）；③ 同 JVM 内同名重部署时，ghost 与新引擎共享同名 offset registry 桶（C3-006 已登记的 `NopStreamOffsetBackingStore` 同名互覆面在此具体化）——ghost 持续覆写 offset，新引擎恢复位点被推进，恢复后跳数（数据面后果，超出纯资源泄漏）。
- **建议**: ① 载体修复（跨模块移交 nop-message-debezium）：`wrapper.stop()` 不得吞 `IllegalStateException`——按 Debezium 报文语义对 STARTING_TASKS 拒绝做**有预算重试**（等待状态离开 STARTING_TASKS 后重试 close 直至 STOPPED），或把 ISE 上抛让调用方面对；② in-scope 侧：`DebeziumCdcSourceReader.close` 与 `DebeziumCdcSourceFunction.cancel/truncateForDrain/finally` 在 `source.stop()` 后增加终止性验证（有限预算内轮询引擎状态，未终止即 typed 失败或至少 ERROR 显式声明 ghost 风险）——把停止契约从「已发出」升级为「已终止」；③ 最低成本：载体 catch 分支加延迟重试（如 100ms × N 次）。
- **失效面枚举**（F3 同族清扫——8 模块全部「引擎宿主停止面」）: ① `DebeziumCdcSourceReader.close`（本条，经 cancel → msgSource.stop）；② `DebeziumCdcSourceFunction.cancel`（:331-344，同根因）；③ `DebeziumCdcSourceFunction.truncateForDrain`（:346-367，DRAIN 路径同根因——「刚启动即 DRAIN」是窗口的直接命中形态）；④ `DebeziumCdcSourceFunction.run` finally（:308-316，第二道停止也因 stopped 守卫 no-op）；⑤ `MessageSourceFunction` 停止面（Local 后端无引擎状态机，latch + 引用交换——干净）；⑥ `KafkaSourceFunction`（consumer try-with-resources——干净）；⑦ `BatchLoaderSourceFunction/Reader`（无引擎宿主——不适用）。同族仅 Debezium 一个引擎宿主，四个停止面全部中招。
- **信心水平**: 很可能（机制确定：ISE 契约从实际解析依赖的 jar 字节码验证、双层吞异常从载体源码验证、四个 in-scope 停止面控制流逐一验证；触发窗口宽度取决于 DB 启动时延——正常亚秒~秒级，连接超时劣化时达数十秒）
- **误报排除**: 非「平台基础设施自身实现」豁免——该豁免限定 nop-kernel/nop-commons；nop-message-debezium 是产品载体模块，第 1/2 轮已按同一口径将其作为事实引用立项（C3-006 先例）；非「已声明边界」——owner doc Debezium 节声明 REPLAYABLE/typed 失败/R5-CON-02/03，未声明 cancel-during-startup 的 ghost 形态；也不是「低频启动路径粗粒度等待」类——后果是跨生命周期资源泄漏与 offset 干扰，非一次性延迟。
- **复核状态**: 已保留（P1，2026-10-14 复核）——本轮独立 javap 反编译 debezium-embedded-3.7.0.Final 证实 `close()` 在 STARTING_TASKS 抛 ISE（报文与发现引用逐字一致）；载体双层吞异常（wrapper catch→LOG.error + stopped 守卫永久 no-op；msgSource 二次 catch + engineWrapper=null + subscriptions.clear()）证实；四个 in-scope 停止面均无终止性验证证实。幽灵引擎时序完整可复现。归属：同族相邻 ←C4-005（fire-and-forget 无终结等待）、C4-006（启动 lambda 无 stopped 守卫）——同一停止契约族三面，7.1 应合并为一个修复项；本条提供依赖契约根因。

---

## 覆盖度与残留边界（第 3 轮，前半 + 续合并）

### 前半（D1–D9 同族清扫 + 残余面，产出 C3-013）

- **已覆盖（前半新增）**: C3-009 同族（全局池绑定——8 模块零命中，载体穷尽）、C3-011 同族（重入/静默守卫——`compareAndSet` 与生命周期守卫全量清扫，仅 Lockable 防御性 CAS 与 C3-011 本体）、上下文传播轴（零使用，轴为空）、JDBC 2PC 跨线程复合操作的事务原子性验证（双 commit 被 PK + 单事务兜住——干净）、Kafka 2PC 事务状态机线程归属复核（维持 A7 干净结论）、RocksDB 后端 open/close/metrics 全链（发现 C3-013）、残余文件抽查（MessageSinkFunction/DebeziumSourceSupport/TimerServiceImpl/withManifestLock/BatchConsumer flush——全部干净）。
- **低于立项阈值的残余（记录不立项）**: ① `FileTwoPhaseCommitSink.MANIFEST_JVM_LOCKS` 静态 CHM 条目只增不删——上界 = 进程内去重 outputDir 数，有界（P3）；② `readProperty` synchronized 使 backend close 可能等待一次在途 native 抓取（RocksDB mutex 病态阻塞时才可感知，防护方向正确，理论风险 P3）——两处均并入 C3-013 失效面/边界记录，不单独计数。
- **前半边界声明**: ① core 侧 `MicrometerStreamTaskMetrics`/`StreamMetricsRegistries` 为 nop-stream-core 平台文件，仅作同族事实引用与惯例证据，修复建议全部落在 in-scope 的 `RocksDBMetricsRecorder`；② micrometer 弱引用/重注册语义的仓库内证据链依赖 core WI16 注释自证，未另行引入外部文档；③「多作业共存 JVM」触发面依赖 dbPath 按作业可区分的配置前提（A9 已证 dbPath 定长，同路径双作业会 LOCK fail-fast），主触发面不依赖该前提。

### 续（第 2 轮遗留依赖收口 + 四方向定向核查，产出 C3-014）

**遗留依赖①裁决：Debezium `engine.close()` 语义（第 2 轮边界声明第 ④ 项）——已证实，并推翻 C3-009 的 T2' 泄漏放大分支。** 验证方法：javap 反编译仓库钉定基线的依赖工件（debezium-embedded/api 3.7.0.Final）。已证实事实：

1. `DebeziumEngine.create(JsonByteArray)` 经 ServiceLoader（META-INF/services 唯一注册 `ConvertingAsyncEngineBuilderFactory`）构建 **`AsyncEmbeddedEngine`**——3.x 已移除经典 `EmbeddedEngine`（jar 内无该类）。
2. **`AsyncEmbeddedEngine` 全类零 `java/lang/Thread.interrupt` 调用**（对 run() 调用者线程即 globalWorker 线程从不中断）。关闭是协作式的：`close()` → 状态迁移 STOPPING → `stopPollingIfNeeded` / `stopRecordService`（内部 `ExecutorService.shutdown` + `shutdownNow` + 有界 `awaitTermination`）→ 跑 poll 循环的 run() 调用者线程观察到状态变化后返回 → CompletionCallback → run() 返回 → globalWorker 线程**被释放归还池**。
3. 引擎内部 record-processing 线程经 `shutdownNow` 被中断 → 阻塞在 `queue.put` 的 collect 抛 `InterruptedException` → `QueueSourceContext.collect`（DebeziumCdcSourceReader.java:330-334）`Thread.currentThread().interrupt()` + typed 重抛 → 线程退出。
4. `close()` 在 STARTING_TASKS / STOPPING / STOPPED 三态抛 `IllegalStateException`（STARTING_TASKS 报文自证泄漏风险）→ 直接产出本轮续轮新发现 **C3-014**。

对既有发现的影响：
- **C3-009 的 T2' 条件分支（「若 close 不中断 run 线程 → 每轮重建净耗 1 个全局 worker」）被推翻**：close 后 globalWorker 协作释放、内部线程被 shutdownNow 中断，无按重建次数的单调线程泄漏。C3-009 的核心结论（长驻 `engine.run()` + 阻塞背压回调绑定全局 30 线程共享池；>30 条管线/停顿管线耗尽池后新引擎提交进 10000 深队列永不执行 → 静默死流）**不受影响**——那是运行期占线问题，不是关闭后泄漏问题。C3-009 维持 P1。
- **C3-012 的 Debezium 面（「runner 卡在 finally source.stop() >5s」触发窗口）收窄**：引擎关闭 = 有界 await 序列，>5s 需要引擎关闭预算本身超 5s（慢速 binlog 断连等，罕见但可能）——C3-012 维持 P2。其主触发面（`MessageSourceReader` 的 Local 总线**发送方业务线程**阻塞在废弃队列 put、无任何中断会到达）不受影响，维持原判。

**遗留依赖②裁决：core「同实例并发重启」形态（第 2 轮边界声明第 ③ 项）——已证实为被 fail-loud 守卫阻断，C3-011 触发面收窄。** 验证方法（事实引用，runtime/core 非审计对象）：`nop-stream-runtime/.../execution/SupervisionLoop.java:512-530,711-759` + `nop-stream-core/.../task/SubtaskTask.java:148-174`。

- region 重启序 = Phase 1 `cancelTaskWithMailbox`（mailbox 取消信号 + **`task.cancel()` 内 `executingThread.interrupt()`**，SubtaskTask.java:166）→ Phase 2 `waitForTerminal`（有界轮询）→ **超时即 typed 失败 `ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT`（fail-loud，:752-758），不再静默放行第二实例**（javadoc 自证旧 LOG.warn 放行行为已被移除）→ 只有旧任务达终态才 `rebuildTask`。旧任务终态 = 任务线程已退出（legacy source run() 占任务线程模型）⇒ run() 已返回。
- **结论：运行时重启路径下「旧 run 未返回即新 run 进入同一实例」被结构性阻断**。C3-011（`runEntered` CAS 失败静默 return）的触发面收窄为**程序化直构路径**（数据迁移编排器/测试直接并发调用 run()——owner doc「三个 legacy 类降级为程序化 API（测试/数据迁移编排器直构）」正是该用法）。C3-011 的守卫行为缺陷判定不变（P2），触发前提按本轮证据修正。
- 附带精化（对 C3-005/C3-010 的中断结论）：`SubtaskTask.cancel()` **确实中断任务线程**——即运行时层已向 `KafkaSourceFunction.poll/partitionsFor` 与 `BatchLoaderSourceFunction.loader.load` 递送 interrupt。C3-005 维持原判（KafkaConsumer 对线程中断不响应，`wakeup()` 仍是唯一解除原语）；C3-010 维持原判但表述精化（JDBC load 是否响应中断取决于驱动，函数级取消通道缺失对嵌入/直构路径仍是完整缺口）。

**深挖指令其余两方向**（均确认无新发现，证据见续轮 grep 记录）：
- **C3-009 GlobalExecutors 失效面独立复核**：`grep -rn "GlobalExecutors|globalWorker|CommonConfigs"` 对 8 模块 `src/main/java` 全量 = **零命中**——平台全局池耦合唯一入口 = connector-debezium 经载体 `DebeziumEngineWrapper.start()`。前半 D1 的穷尽性结论独立复核成立。
- **定时器取消/重挂窗口**：`CepOperator.onCacheStatisticsTimer`（:658-667）的 close 后复活窗口**不存在**——全部 timer 注册/取消/触发在任务线程（`TaskProcessingTimeService` javadoc + `SubtaskTask.run` finally `closeOperatorChains()` 证实 close 同线程）；`fireDueTimers`（:113-132）先移除注册再跑回调、重挂发生在同一次 fireDueTimers 栈上，`TimerRegistrationFuture.cancel` 只作用于未触发注册；fire mail 唯一执行器是任务线程 drain 点，严格先于 closeOperatorChains——「mail 在 close 后执行」不可能（先执行则重挂 future 被 close 取消，否则永不执行，两分支皆干净）。`TimerServiceImpl`（:1382-1388）为纯时钟读取器，非异步面。R5 CEP-7 恢复重挂（:413-425）open 在任务线程先于处理——干净。
- **线程中断响应点**：8 模块 `Thread.interrupt` 主动调用**零**（仅 3 处 `Thread.currentThread().interrupt()` 恢复标志：MessageSourceReader.java:227、DebeziumCdcSourceReader.java:331、AvroSchemaRegistryClient.java:121，均伴随 typed 重抛/上抛非吞点）；阻塞站点全清单（queue.poll/put ×4、join ×2、completionLatch.await ×2、consumer.poll+partitionsFor、rateLimiter.acquire ×2、jvmLock+channel.lock、http.send）与前两轮登记一一对应，无未登记站点。
- **上下文传播**：8 模块 `IContext|ContextProvider|ThreadLocal` 零命中——该轴为空（与前半 D3 独立复核一致）。

### 续轮逐文件读取清单

| 文件 | 方式 | 目的 |
|---|---|---|
| `nop-stream/nop-stream-cep/.../operator/CepOperator.java`（:380-500、:540-780、:1355-1465） | 定向读取 | timer 三面取消/重挂窗口 |
| `nop-stream/nop-stream-core/.../execution/TaskProcessingTimeService.java`、`TimerRegistrationFuture.java` | 全文 | fireDueTimers 移除先行语义、cancel 语义 |
| `nop-stream/nop-stream-core/.../task/SubtaskTask.java`（:140-200） | 定向读取 | cancel 的 interrupt 递送 |
| `nop-stream/nop-stream-runtime/.../execution/SupervisionLoop.java`（:700-820 + grep） | 定向读取 | 遗留依赖②裁决 |
| `nop-stream/nop-stream-connector-debezium/.../DebeziumCdcSourceReader.java`（:100-340）、`DebeziumCdcSourceFunction.java`（:220-375） | 定向读取 | C3-014 in-scope 停止面 |
| `nop-message/nop-message-debezium/.../engine/DebeziumEngineWrapper.java`、`.../DebeziumMessageSource.java` | 全文（载体事实引用） | 双层吞 ISE 链 |
| `nop-stream/nop-stream-connector-jdbc/.../JdbcTwoPhaseCommitSink.java`（:1020-1100） | 定向读取 | commit 线程面复核 |
| `~/.m2/.../debezium-embedded-3.7.0.Final.jar` + `debezium-api-3.7.0.Final.jar` | javap -c -p 反编译 | 遗留依赖①裁决 |

续轮 grep 记录：①`GlobalExecutors|globalWorker|CommonConfigs`（零命中）；②`Thread.sleep|.wait(|.join(|.take()|interrupt()`；③`.poll(|.put(|.offer(|await(|.acquire(|.lock()`（排除集合写 put）；④`CompletableFuture|new Thread|Executors.|ExecutorService|ScheduledExecutor|new Timer|TimerTask|ForkJoin|parallelStream|.execute(|.submit(`（2 个已知 new Thread + 1 个 TimerServiceImpl，零 future/exec）；⑤`IContext|ContextProvider|ThreadLocal`（零命中）；⑥`DebeziumEngineWrapper` 全仓调用方（2 in-scope + 2 载体）；⑦`source.stop(|.cancel()` @connector-debezium（4 停止面）。

### 累计发现清单（第 1-3 轮，含续）

| ID | 标题（缩略） | 严重度 | 轮次 |
|---|---|---|---|
| C3-001 | kafka-2pc producer 从不关闭（启停泄漏网络线程） | P1 | R1 |
| C3-002 | JDBC 台账只增不删（无界持久化增长） | P1 | R1 |
| C3-003 | registry HTTP 无请求超时（computeIfAbsent 锁内阻塞） | P1 | R1 |
| C3-004 | Debezium 枚举器恢复双登记（同表双引擎重复消费） | P1 | R1 |
| C3-005 | Kafka cancel 无 wakeup（启动期取消挂起 max.block.ms） | P2 | R1 |
| C3-006 | 失败监听静态表按键覆盖（故障信号串号） | P2 | R1 |
| C3-007 | avro reader schema 兜底吞 registry 不可达（演进静默失效） | P2 | R1 |
| C3-008 | manifest 跨 JVM 文件锁无界等待（理论风险） | P2 | R1 |
| C3-009 | Debezium 引擎分发/背压绑定平台全局 worker 池（停顿占线 + 耗尽静默死流） | P1 | R2 |
| C3-010 | BatchLoaderSourceFunction.cancel 无中断响应点 | P2 | R2 |
| C3-011 | Debezium run 重入守卫静默 return（异常→假 EOS；触发面已收窄至程序化直构路径） | P2 | R2 |
| C3-012 | 桥 reader close 超时分支先抛后 clear、无 interrupt 兜底（Debezium 面窗口已收窄） | P2 | R2 |
| C3-013 | RocksDB gauge 无 tag 固定名注册进进程级共享注册表且无 rebind（重建后 6 指标静默失效/消失） | P2 | R3 前半 |
| C3-014 | Debezium STARTING_TASKS 窗口 stop 被依赖方 ISE 拒绝且被载体双层吞掉 → 永久幽灵引擎（活 binlog 连接 + 线程组 + offset 互覆） | P1 | R3 续 |

**累计：P0 = 0；P1 = 6（001/002/003/004/009/014）；P2 = 8（005/006/007/008/010/011/012/013）；P3 = 0（两项低于阈值残余已记录不立项）。**

### 终止判定（前半 + 续合并，维持终止）

1. **异步入口面多重穷尽**：第 1 轮 A1–A10 + 第 2 轮 B1–B8 + 第 3 轮前半 D1–D9 + 续轮四方向定向 grep 交叉复核（`CompletableFuture|new Thread|Executors|Timer|ForkJoin|GlobalExecutors|ThreadLocal|compareAndSet|sleep|wait|IO 等待点` 全量命中均归位；零新载体）。四套独立盘点闭合。
2. **第 2 轮两项未证实依赖全部裁决完毕**（本轮续的核心产出）：依赖①从依赖工件字节码证实（并产出 C3-014、推翻 C3-009 T2'、收窄 C3-012 Debezium 面）；依赖②从守卫源码证实（收窄 C3-011 触发面）。未证实存量清零，同类「依赖翻转出新缺陷面」的机制在后续轮次不再存在。
3. 前半终止判定（「后续轮次已无未盘点载体，剩余候选均低于立项阈值」）与续轮判定一致；跨模块移交项两项明确（C3-009 专用 executor、C3-014 close 重试/终止性验证，均落 nop-message-debezium；C3-006 载体注册表键改造同批）。

**C3 维度深挖终止（建议不再派发第 4 轮）。**

---

# C3 异步链路正确性审计 — nop-stream-state-connectors · 维度复核结论

- **复核人**: 独立复核子代理（与初审/深挖不同任务实例）
- **复核日期**: 2026-10-14
- **复核方式**: 本轮解除盲审（可读 ai-dev/audits/ 历史审计与 git log，为保留发现标注现状归属）；逐条重读 live code + 调用方三态判定（已保留/降级/驳回）。判定纪律：写不出具体触发时序的并发发现降 P2；已声明边界+守卫不是缺陷。C3-014 的依赖契约经本轮独立 javap 反编译 `~/.m2/repository/io/debezium/debezium-embedded/3.7.0.Final` 复证。
- **总体结论**: **14 条全部已保留（P1×6 / P2×8），0 降级，0 驳回。** 所有发现机制面均经 live code 逐行复核成立，触发时序均可具体写出；定级全部维持初审/深挖值。

## 维度复核结论（独立复核子代理，逐条判定）

| ID | 初判 | 复核判定 | 复核要点 | 现状归属 |
|---|---|---|---|---|
| C3-001 | P1 | **已保留 P1** | 全文件无 producer.close 证实；类仅 implements ConnectivityCheckable，`StreamSinkOperator.close` 的 `instanceof AutoCloseable` 钩子（:172-178）对其不生效；copyForSubtask 重建即弃置旧 producer（网络线程+缓冲）。启停/恢复周期线性累积时序成立。 | 同 campaign 四向重复 ←C4-001、C6-001、C7-002（7.1 归并）；无历史先例 |
| C3-002 | P1 | **已保留 P1** | `writeLedgerEntry` INSERT 为唯一台账写路径证实；模块内 grep DELETE 命中均为 CDC 数据面 `deleteKeyMapper`，无台账清理；`abort` 仅删内存 pending。增速量级（30s 间隔单 subtask ≈2880 行/天）成立。 | 同 campaign 四向重复 ←C5-006、C7-001、C8-007（归并）；2026-09-01 连接器分析仅裁「表名硬编码非缺陷」，无先例 |
| C3-003 | P1 | **已保留 P1** | 两文件 grep timeout 零命中（仅 connectTimeout 10s）证实；encode 热路径 `computeIfAbsent` 锁内 `registerSchema`+`getSchemaById` RPC 逐行证实；registry 建连后挂起→同 subject 全 subtask 阻塞且无异常无输出（F5）时序成立。 | 同 campaign 五向重复 ←C2-002、C4-003、C6-008、C8-004、C7-006（后者另含重试风暴面；归并） |
| C3-004 | P1 | **已保留 P1** | `restoreState` 单桶 computeIfAbsent 倾倒 + `start()` 全表 `i%parallelism` 重算追加（不清不去重）逐行证实；恢复态来源证实（`SourceCoordinatorRegistry.consumeEnumeratorRestore` stash→restoreState 先于 start，LocalSourceCoordinator:160-163）；跨 reader 双登记时序成立——剩余桶 index > nsi 的表同时落入恢复桶与 position 桶（例：剩余桶 {2:[T2],3:[T3]}、nsi=2，T3 双桶→两 reader 各自 launchEngine，reader-3 `adoptedSplits` 为空 D-4 放行）。 | 相邻非重复：WI1.1 C1-001/C2-001 指向同一枚举器的线程安全面（裸 LinkedHashMap CME），本条为恢复分配逻辑缺陷——7.1 cross-ref 不归并 |
| C3-005 | P2 | **已保留 P2** | `cancel()` 仅置位、无 wakeup 证实；KafkaConsumer 对线程中断不响应（R3 证实的 SubtaskTask interrupt 不解除 poll/partitionsFor 阻塞），取消延迟上界 = max.block.ms/default.api.timeout.ms（默认 60s、用户可放大），时序具体。 | 本 campaign 唯一登记（C7-H6 仅从 run 侧资源配对视角判「可接受」，未评 cancel 响应性）；无历史先例 |
| C3-006 | P2 | **已保留 P2** | `FAILURE_LISTENERS.put` 同名覆盖证实（载体 javadoc「replaces any previous listener」是机制文档非边界声明，且无同名冲突守卫）；`totalParallelism<=1` 裸名注册证实；同名双管线串号 T1-T4 时序成立。 | 本 campaign 唯一登记；与 C3-014 风险③共享 `NopStreamOffsetBackingStore` 同名互覆相邻面 |
| C3-007 | P2 | **已保留 P2** | `catch(StreamAvroException)→writerSchema` 兜底证实；`getLatestSchema` 失败面经 `readJsonResponse` 全部折进 StreamAvroException，兜底覆盖 registry 不可达证实；与 owner doc `nop-stream-connectors.md:114`「失败面全 typed……（不静默降级）」直接冲突，文档冲突成立。 | 同 campaign C7-006 相邻（其登记重试无退避面，本条为兜底语义面），7.1 cross-ref |
| C3-008 | P2 | **已保留 P2** | `jvmLock.lock()`/`channel.lock()` 无超时逐行证实；触发前提=对端 JVM「活而挂死」持锁不返，写不出生产可复现时序——维持 P2 理论风险档（不降 P3：位于 exactly-once 提交主路径且跨 JVM）。 | 同 campaign 双向重复 ←C2-005（归并） |
| C3-009 | P1 | **已保留 P1（按 R3 修正口径）** | 载体 `GlobalExecutors.globalWorker().execute(() -> engine.run())`、池默认 maxPoolSize=30（CommonConfigs.java:27）、事件同步内联分发、in-scope collect 阻塞 put 逐项证实；池耗尽→新引擎提交 10000 深队列永不执行→静默死流时序具体。R3 自我裁决的 T2' 泄漏分支推翻成立（close 协作式释放，经字节码证实），核心占线结论不受影响——复核采纳修正后口径。 | 同 campaign 双向重复 ←C4-004（归并） |
| C3-010 | P2 | **已保留 P2** | `cancel()` 仅置位、`loader.load` 唯一阻塞点、`IBatchTaskContext` 无取消钩子证实；R3 精化（SubtaskTask interrupt 已递送，JDBC load 是否响应取决于驱动）不改变函数级取消通道缺失。触发=长查询窗口，时序具体。 | 本 campaign 唯一登记（C4:348 仅核 loader close 配对，未评 cancel 响应性）；无历史先例 |
| C3-011 | P2 | **已保留 P2** | CAS 失败静默 return 证实；R3 触发面收窄裁决成立（SupervisionLoop fail-loud zombie timeout + SubtaskTask:166 interrupt 结构性阻断运行时同实例双进入），收窄后触发面=程序化直构路径（owner doc 声明的合法用法）——守卫把检测到的异常转译为假 EOS 的响应语义缺陷成立。 | 历史先例=2026-06-05 r2 审计建议③（runEntered CAS 化）已落地，本条为该修复的响应语义残留新面，非重复 |
| C3-012 | P2 | **已保留 P2** | join 超时分支 throw 先于 `queue.clear()`、close 全程无 interrupt 逐行证实；R3 收窄 Debezium 面（需引擎关闭预算>5s）后主触发面=Local 总线发送方业务线程成立。复核补充精化：clear 仅解除当前在途单条 put，持续泵送的发送方仍阻塞在下一 put——缺陷面与「interrupt 兜底」建议均成立。 | 同 campaign 双向重复 ←C6-002、C6-003（C6 泄漏视角/本维度清理不对称视角，同一缺陷，归并） |
| C3-013 | P2 | **已保留 P2** | 6 gauge 固定名 + `Tags.empty()` 绑定实例 dbRef、close 只摘引用、注册进进程级静态 `StreamMetricsRegistries`（RocksDBKeyedStateBackend:278-279）逐项证实；core `TASK_HOLDERS` 注释在仓内自证 micrometer 弱引用 + 同 id 重注册静默丢弃陷阱；任意一次后端重建即触发。 | 同 campaign 四向重复 ←C2-011、C6-010、C7-004（C2-001/C6-004 为同代码竞态兄弟面，非本条；归并） |
| C3-014 | P1 | **已保留 P1** | 本轮独立 javap 反编译 debezium-embedded-3.7.0.Final 证实 `close()` 在 STARTING_TASKS 抛 ISE（报文与发现引用逐字一致）；载体双层吞异常（wrapper catch→LOG.error + stopped 守卫永久 no-op；msgSource 二次 catch + engineWrapper=null + subscriptions.clear()）证实；四个 in-scope 停止面均无终止性验证证实。幽灵引擎时序完整可复现。 | 同族相邻 ←C4-005（fire-and-forget 无终结等待）、C4-006（启动 lambda 无 stopped 守卫）——同一停止契约族三面，7.1 应合并为一个修复项；本条提供依赖契约根因 |

## 最终保留项

| ID | 标题（缩略） | 复核定级 | 轮次 | 现状归属 |
|---|---|---|---|---|
| C3-001 | kafka-2pc producer 从不关闭（启停泄漏网络线程） | P1 | R1 | 重复 ←C4-001/C6-001/C7-002（归并） |
| C3-002 | JDBC 台账只增不删（无界持久化增长） | P1 | R1 | 重复 ←C5-006/C7-001/C8-007（归并） |
| C3-003 | registry HTTP 无请求超时（computeIfAbsent 锁内阻塞） | P1 | R1 | 重复 ←C2-002/C4-003/C6-008/C8-004/C7-006（归并） |
| C3-004 | Debezium 枚举器恢复双登记（同表双引擎重复消费） | P1 | R1 | cross-ref WI1.1 C1-001/C2-001（不归并） |
| C3-009 | Debezium 引擎分发/背压绑定平台全局 worker 池 | P1 | R2 | 重复 ←C4-004（归并） |
| C3-014 | Debezium STARTING_TASKS 窗口 stop 被 ISE 拒绝且双层吞掉→永久幽灵引擎 | P1 | R3 续 | 合并 ←C4-005/C4-006（停止契约族三面并一项） |
| C3-005 | Kafka cancel 无 wakeup（启动期取消挂起至 max.block.ms） | P2 | R1 | 唯一登记 |
| C3-006 | 失败监听静态表按键覆盖（故障信号串号） | P2 | R1 | 唯一登记 |
| C3-007 | avro reader schema 兜底吞 registry 不可达（违反「不静默降级」） | P2 | R1 | cross-ref C7-006（不归并） |
| C3-008 | manifest 跨 JVM 文件锁无界等待（理论风险） | P2 | R1 | 重复 ←C2-005（归并） |
| C3-010 | BatchLoaderSourceFunction.cancel 无中断响应点 | P2 | R2 | 唯一登记 |
| C3-011 | Debezium run 重入守卫静默 return（假 EOS；触发面=程序化直构） | P2 | R2 | 先例=2026-06-05 r2 修复残留（非重复） |
| C3-012 | 桥 reader close 超时分支先抛后 clear、无 interrupt 兜底 | P2 | R2 | 重复 ←C6-002/C6-003（归并） |
| C3-013 | RocksDB gauge 无 tag 固定名注册共享 registry 无 rebind（重建后指标静默失效） | P2 | R3 前半 | 重复 ←C2-011/C6-010/C7-004（归并） |

**复核计数：保留 14 / 降级 0 / 驳回 0。P0=0；P1=6（001/002/003/004/009/014）；P2=8（005/006/007/008/010/011/012/013）。**

**现状标注统计**：跨维度重复（7.1 归并）7 条（001/002/003/008/009/012/013）；同族相邻 cross-ref（不归并）3 条（004/007/014）；本 campaign 唯一登记 3 条（005/006/010）；历史先例关联（非重复）1 条（011）。修复排批时 7 条归并项应与对应维度发现合并计一项，避免重复修复。
