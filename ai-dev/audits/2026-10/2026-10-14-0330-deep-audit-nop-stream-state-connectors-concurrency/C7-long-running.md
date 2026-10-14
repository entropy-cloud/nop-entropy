# C7 长时运行稳定性初审报告 — nop-stream-state-connectors

- **审计编号**: nop-stream-state-connectors-C7
- **维度**: C7（长时运行稳定性）——单调增长 / 挂死可检测性 / 重试风暴 / 自愈语义 / 时钟与期限 / 热路径外慢性病
- **定向缺陷族**: F4（无界账本只增不删）、F5（挂死生产者对健康检测不可见），旁照 F7/F8/F9
- **审计日期**: 2026-10-14
- **审计者**: 并发审计子代理（盲审轮——仅读 live code + owner doc，未读 ai-dev/audits、plans、bugs、lessons 下任何文件）
- **基线**: `_tmp/cra-baseline-nop-stream-state-connectors.txt`
- **Owner docs**: `docs-for-ai/03-modules/nop-stream.md`、`docs-for-ai/03-modules/nop-stream-connectors.md`

## 检查范围说明

审计对象为 8 个模块的 `src/main/java`（合计 140 files / 25,458 lines，与基线一致）：

| 模块 | 审读重点 | 覆盖方式 |
|---|---|---|
| `nop-stream/nop-stream-cep` | NFA 匹配状态长时累积、过期匹配清理、timer ledger | 全量精读 CepOperator(1510 行)/NFA/SharedBuffer/SharedBufferAccessor/SharedBufferCacheConfig/NopCepConfigs；其余 68 个文件为模式/条件/模型纯配置类，经 grep 全量扫过 |
| `nop-stream/nop-stream-rocksdb` | 增量快照 cp-N 目录、恢复临时目录、TTL 清扫、metrics gauge 生命周期 | 全量精读 RocksDBKeyedStateBackend/RocksDBIncrementalSnapshotStrategy/RocksDBIncrementalRestore/RocksDBMetricsRecorder；state 类文件经 grep 扫描（无阻塞/无累积结构） |
| `nop-stream/nop-stream-connector` | 文件 2PC manifest 锁表与增长、FileSourceReader 游标与 finishedSplits、MessageSource 队列桥接 | 精读 FileTwoPhaseCommitSink/FileSourceReader/FileSplitEnumerator/MessageSourceReader/MessageSourceFunction/MessageSinkFunction |
| `nop-stream/nop-stream-connector-jdbc` | JDBC 2PC 台账（F4 定向）、连接生命周期、重试 | 精读 JdbcTwoPhaseCommitSink（1162 行全量）+ builder/工厂 grep |
| `nop-stream/nop-stream-connector-kafka` | 重试风暴、2PC producer 生命周期、offset map | 精读 KafkaSourceFunction/KafkaTwoPhaseCommitSink 全量 |
| `nop-stream/nop-stream-connector-debezium` | offset 历史累积、引擎挂死可见性、schema history | 精读 DebeziumCdcSourceReader/DebeziumCdcSourceFunction 全量；split/enumerator/support 精读签名 + grep |
| `nop-stream/nop-stream-connector-batch` | 缓冲失败保留语义、loader 生命周期、offset 跳过 | 精读 BatchConsumerSinkFunction/BatchLoaderSourceReader；其余 grep |
| `nop-stream/nop-stream-format-avro` | schema 缓存只增不删、registry 调用超时/重试 | 精读 AvroSchemaRegistryClient/AvroWireFormat 全量 |

方法：以 C6 维附录 C grep 基线圈定重点文件后逐文件精读；对每个候选增长源回答"上界是什么、由谁保证"；对每个阻塞等待点回答"超时在哪、挂死后从哪条观测面可见"。`_` 前缀生成文件未作为发现对象。跨模块引用（`TwoPhaseCommitSinkFunction`/`StreamSinkOperator`，属 nop-stream-core）仅作契约证据核对，不立项。

**判级口径**：P1 = 长 时运行必然触发的无界增长 / 错误取消路径资源泄漏；P2 = 失败路径可具体化的残留或可观测性损失；P3 = 慢性病/防御性候选。

## 单调增长源清单

| # | 增长源 | 位置 | 上界/清理路径 | 判定 |
|---|---|---|---|---|
| G1 | JDBC 2PC 台账表 `stream_epoch_ledger_v2` 行数 | `JdbcTwoPhaseCommitSink.writeLedgerEntry` | **无任何删除/TTL/归档路径**（全模块 grep 证实只有 INSERT+SELECT） | P1 → C7-001 |
| G2 | KafkaProducer 实例（每次 task 生命周期结束泄漏一个） | `KafkaTwoPhaseCommitSink` | **无 close 路径**（类非 AutoCloseable、无 close override） | P1 → C7-002 |
| G3 | 增量恢复临时目录 `rocksdb-restore-range-{id}`（失败/kill 残留，全量状态尺寸） | `RocksDBIncrementalRestore.restoreRangeInto` | 仅同 id 重试时清理；无全局清扫 | P2 → C7-003 |
| G4 | RocksDB state 层 gauge（首注册后僵死，重建后永久 NaN） | `RocksDBMetricsRecorder.gauge` | 无注销/重绑路径 | P2 → C7-004 |
| G5 | CEP `reconciledTimerKeys`（每 distinct key 一条，永不移除） | `CepOperator:190,1274` | 无（每 run 内随 key 基数单调增长） | P2 → C7-005 |
| G6 | Avro registry HTTP 调用失败态不缓存（registry 故障期每记录重试） | `AvroWireFormat.readerSchemaFor` + `AvroSchemaRegistryClient` | 无失败缓存/退避/请求超时 | P2 → C7-006 |
| G7 | 文件 2PC `manifest.properties`（每 epoch 一键）+ `MANIFEST_JVM_LOCKS`（每 distinct 输出目录一把锁） | `FileTwoPhaseCommitSink:98,560` | manifest 无压缩/轮转；锁表无移除 | P3 → C7-007 |
| G8 | Avro `schemaByIdCache`（每个见过的 schema id 一条） | `AvroSchemaRegistryClient:36` | 无上限/TTL（增速 = schema 版本演进速率） | P3 → C7-008 |
| G9 | CEP 降格计数器 instance-identity 标签 meter（embedded 实例每个注册一个） | `CepOperator:379-383,438` | 无移除（生产部署用稳定 TaskLocation 标签，幂等） | P3 → C7-009 |
| G10 | Debezium 静态 offset registry 条目（任务停止后残留于 JVM） | `DebeziumCdcSourceFunction`（仅 first-run clear，close 不 clear） | 有界于 distinct connector 名 × offset map 尺寸（KB 级）；AR-03 clear 保证正确性不受影响 | 判 P3 情报项，不立项（store 本体在 nop-message-debezium，越界） |
| G11 | CEP timer ledger `registeredEventTimeTimersByKey` | `CepOperator:984-1002,1087-1107` | 消费后 `removeIf` + 空集移除 key；checkpoint 全量但只含 pending | **有界（已正确清理）** |
| G12 | CEP `elementQueueState`/`computationStates`/SharedBuffer events+entries | CepOperator/NFA/SharedBufferAccessor | drain 移除桶；idle 复位；refcount releaseNode/releaseEvent；advanceTime 清 eventsCount | **有界（过期匹配清理链完整）** |
| G13 | Kafka 2PC `pendingCommits` 标记 | `KafkaTwoPhaseCommitSink.saveState` | 基类 `finishCommit` 提交后 `pending.remove(eid)`；失败 checkpoint 的标记由下一个成功 checkpoint 收敛 | **有界（已验证基类契约）** |
| G14 | RocksDB 增量快照 cp-N 目录 | `RocksDBIncrementalSnapshotStrategy` | F-02 滚动保留（默认 2，删除失败 WARN + 下轮重试） | **有界（已自愈）** |
| G15 | FileSourceReader `finishedSplits` / FileSplitEnumerator `finishedFiles` | 两个文件 | source 为 deploy-time discovery 有界（javadoc 显式声明连续发现属 successor scope） | **有界（已声明边界）** |

## 挂死点清单

| # | 等待点 | 超时 | 中断响应 | 挂死后可观测路径 | 判定 |
|---|---|---|---|---|---|
| H1 | `FileTwoPhaseCommitSink.withManifestLock` → `FileChannel.lock()`（无限期阻塞） | **无锁超时**（tryLock+timeout 未用） | 可中断（AsynchronousCloseException） | checkpoint 超时 → abort → 恢复循环 → restart cap → FAILED；OS 对死进程释放文件锁 | P3 情报项：慢而非不可见；跨 JVM 对端 NFS stall 时恢复循环逐次阻塞直至 cap，最终可见 |
| H2 | `JdbcTwoPhaseCommitSink.commit` JDBC 调用（openConnection/execute/commit） | **sink 层无 setQueryTimeout**（grep 证实），依赖 datasource 驱动/socket 超时 | JDBC 不响应中断 | checkpoint 超时 → 恢复循环 → cap → FAILED | P3 情报项：与 H1 同理，最终可见；建议 sink 层显式 queryTimeout |
| H3 | `AvroSchemaRegistryClient.readJsonResponse` → `HttpClient.send`（**无 request timeout**，connectTimeout 只管建连） | 建连 10s；已连接后读挂起无限期 | 可中断（send 抛 InterruptedException） | 数据面停流、无报错；仅 checkpoint 超时可见 | P2，并入 C7-006 |
| H4 | `MessageSourceReader/DebeziumCdcSourceReader` `queue.put` 背压阻塞 | 无直接超时，但消费侧 `poll(100ms)` 有界持续放空 | 中断响应（put 抛 InterruptedException → typed） | 队列桥接自限；close 时 join(5s)+typed 失败 | 可接受（有界、响亮） |
| H5 | `MessageSourceReader/DebeziumCdcSourceReader.close` → `thread.join` | 5s + typed 失败 | — | 关闭失败显式上抛 | 可接受 |
| H6 | `KafkaSourceFunction.run` → `consumer.poll(200ms)`/`partitionsFor` | poll 有界；partitionsFor 受 client `default.api.timeout.ms`（默认 60s）约束 | 消费循环每 200ms 查 `running` | cancel 后 ≤200ms 退出；try-with-resources 关闭 consumer | 可接受 |
| H7 | `BatchLoaderSourceReader.nextRecord` → `loader.load` | 依赖 IBatchLoader 后端 | 依赖后端 | 任务线程阻塞时 checkpoint 超时可见 | 后端契约，不在本模块裁权内 |
| H8 | `MessageSinkFunction.consume` → `messageService.send`（阻塞 send future） | 依赖后端 | 依赖后端 | 同上 | 后端契约（owner doc 已声明同步阻塞语义），不立项 |
| H9 | CEP 全模块 | 无任何 wait/sleep/lock（grep 证实，task 线程单线程模型） | — | — | **无挂死点** |
| H10 | Debezium 引擎静默挂死（不产出也不报错） | 等待循环 1s 周期检查 `pendingError` | 引擎 failure listener → pendingError → typed task failure（R5-CON-03） | 任务 FAILED，非静默 | **F5 面已覆盖** |

## 发现条目

### [nop-stream-state-connectors-C7-001] JDBC 2PC epoch 台账表只增不删（F4 实证复发）

- **文件**: `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:852-861`（写入点）；全模块无删除点
- **证据片段**:
  ```java
  private void writeLedgerEntry(Connection connection, String namespace, long epochId,
                                int subtaskIndex) throws SQLException {
      try (PreparedStatement ps = connection.prepareStatement(insertLedgerSql)) {
          ps.setString(1, namespace);
          ps.setLong(2, epochId);
          ps.setInt(3, subtaskIndex);
          ps.setTimestamp(4, CoreMetrics.currentTimestamp());
          ps.executeUpdate();
      }
  }
  ```
- **严重程度**: P1
- **并发场景**: commit 线程（checkpoint 完成通知路径）每个成功 epoch 写入一行 `(sink_namespace, epoch_id, subtask_id)`；与并发无关，是纯持久化 append-only 生命周期问题
- **触发时序**: 非竞态缺陷，时序即"时间本身"：每个 checkpoint 间隔（典型 30s–1min）每 subtask 插入一行台账 → 无任何代码路径执行 `DELETE FROM stream_epoch_ledger_v2`（全模块 grep `delete|cleanup|purge|retention|ttl|truncate` 仅命中数据表 d 事件 DELETE，台账表零命中）→ 行数随运行时长线性增长。1 分钟间隔 × 1 subtask ≈ 52.6 万行/年；`maxRetainedCheckpoints` 与治理清扫（`StreamGovernanceConfig`，仅裁观测历史）均不覆盖台账
- **现状**: 台账行的唯一用途是幂等提交守卫——恢复期只需重放**最近几个** durable-but-uncommitted epoch，历史行在越过恢复窗口后即成死重。owner doc 的 dry-run 副作用红线只声明"台账行绝不产生"（探测路径），未声明任何运行期清理契约；`StreamStateResetTool` 只清 checkpoint 目录
- **风险**: 长时运行必然触发的无界持久化增长（F4 族）：磁盘/备份体积线性膨胀、PK 索引膨胀、`ledgerExists` 的 PK probe 常驻缓存压力上升；DBA 侧无官方清理手段（手工删行有踩坏幂等守卫的风险——若删掉仍在恢复窗口内的行会造成重复提交）
- **建议**: 增加基于 epoch 保留窗的台账裁剪（如 commit 成功后异步 `DELETE ... WHERE sink_namespace=? AND epoch_id < ? AND epoch_id <= committedHighWater - N`，N 取恢复窗口安全余量），或提供 `StreamMaintenanceMain` 维护子命令按 namespace+水位批量清理；优先复用引擎已有的 finishCommit 高水位信息做 fencing（不删除超过待恢复窗口的行即可）
- **失效面枚举**（同族 append-only 持久化逐一核对）: ① 文件 2PC `manifest.properties` —— 同族（每 epoch 一键，无压缩）→ C7-007 单独立项；② Kafka 2PC `pendingCommits` —— 内存态，基类 finishCommit 提交后 remove（已核对 `TwoPhaseCommitSinkFunction.java:216`），有界 → 无缺陷；③ RocksDB cp-N 目录 —— F-02 滚动保留存在 → 有界；④ CEP timer ledger —— 消费后 removeIf+空集移除 → 有界；⑤ 本体（JDBC 台账）→ 本发现
- **信心水平**: 确定
- **误报排除**: 不属于"有界声明存在的缓存/队列"误报——owner doc 与代码均无台账保留策略声明；也不是低频路径（每 checkpoint 必写）
- **复核状态**: 已保留（P1 终裁维持）——live 复核证实全模块台账仅 INSERT+SELECT、无任何删除/TTL/归档路径，runtime 维护工具（StreamStateResetTool 等）均不覆盖台账表；同一缺陷 C5-006 曾以 P2 立项，本维度按「长时运行必然触发的无界增长」判级口径维持 P1，修复并轨处理；二轮复核：已保留（P1 维持，归并 C3-002〔merge-adjudication 裁定 7 主条目，C5-006/C8-007 同批归并〕）——live 重验 writeLedgerEntry(:852-861) 仅 INSERT、ledgerExists(:841-851) 仅 SELECT、全文件唯一 `DELETE FROM`(:1120) 为数据表 d 事件、StreamStateResetTool 仅递归删 checkpoint 目录；现状标注补全：历史登记除 C5-006 外另有 **r6 AR-06**（2026-10-06-0522-adversarial-review-nop-stream，同缺陷 P3 终裁「新发现·无重复」），当前 HEAD（2115b450e6）未修复

### [nop-stream-state-connectors-C7-002] Kafka 2PC sink 的 KafkaProducer 永不关闭（正常/失败/取消路径均泄漏）

- **文件**: `nop-stream/nop-stream-connector-kafka/src/main/java/io/nop/stream/connector/kafka/KafkaTwoPhaseCommitSink.java:134-165`（producer 创建）；类声明 77 行（未实现 AutoCloseable，无 close override）
- **证据片段**:
  ```java
  private void ensureTransactionOpen() throws Exception {
      if (transactionOpen) { return; }
      if (producer == null) {
          producer = newProducer();          // 唯一创建点
      }
      ...
      producer.initTransactions();
      ...
  }
  // 全类无 producer.close()；TwoPhaseCommitSinkFunction 亦无 close；
  // StreamSinkOperator.close 仅在 userFunction instanceof AutoCloseable 时关闭
  ```
- **严重程度**: P1
- **并发场景**: task 线程（invoke/saveState）惰性创建 producer；task 关闭路径（StreamSinkOperator.close → `((AutoCloseable) userFunction).close()` 仅对 AutoCloseable 生效，本类不满足）永远不会关闭它
- **触发时序**: T1 作业运行 → invoke 触发 `ensureTransactionOpen` 创建 KafkaProducer（持有 1 条非 daemon 网络线程 `kafka-producer-network-timeout`、broker socket 连接、RecordAccumulator buffer.pool、metrics）→ T2 任务因 CANCEL/DRAIN 正常结束或失败重启，operator close 链走到 `StreamSinkOperator.close()` → `instanceof AutoCloseable` 为 false → producer 泄漏 → T3 恢复/重提交经 `copyForSubtask` 新建实例再泄漏一个。长驻 TM 进程内：每作业生命周期 × 每 sink subtask 泄漏一个 producer；线程与连接随作业翻台次数单调累积；非 daemon 线程还会阻止 JVM 优雅退出（SIGTERM 后需要 SIGKILL）
- **现状**: 资源获取（producer 创建）与释放（close）完全不对称（F8）；连最后一次 `beginTransaction(next)` 打开的事务也无 rollback（作业收尾后悬空至 broker `transaction.timeout.ms` 默认 60s 才 abort，期间该分区 read_committed 消费者被 LSO 拖住）
- **风险**: 多作业长驻 TM（OpsJobManager 模式）下线程数/socket/内存随作业翻台线性增长；进程退出挂死；悬空事务拖慢下游 read_committed 消费
- **建议**: `KafkaTwoPhaseCommitSink` 实现 `AutoCloseable`（close 内 `rollback()` + `producer.close(Duration)`），即可被 `StreamSinkOperator.close()` 既有 instanceof 分支自动接管，零核心改动；顺带在 close 前对 open 事务 abort，消除收尾悬空窗口
- **失效面枚举**（同族长生命周期资源逐一核对）: ① `KafkaSourceFunction.run` 的 KafkaConsumer —— try-with-resources（`run()` 退出即关闭）→ 成对；② `JdbcTwoPhaseCommitSink` 每 commit 连接 —— finally 中 rollback/setAutoCommit/close → 成对；③ `MessageSourceReader` runner 线程 —— close join(5s)+typed → 成对；④ `DebeziumCdcSourceFunction` —— finally 中 `subscription.cancel()+source.stop()` → 成对；⑤ `BatchConsumerSinkFunction` —— 实现 AutoCloseable，close 关闭 consumer → 成对；⑥ 本体（KafkaTwoPhaseCommitSink producer）→ 本发现
- **信心水平**: 确定（close 契约三点均已逐一核对：基类无 close、operator 侧 instanceof 守卫、本类无 override）
- **误报排除**: 不是"平台基础设施自身并发实现不重审"的范畴——producer 生命周期归本模块；也不是低频管理路径——作业启停是长驻运行的主循环
- **复核状态**: 已保留（归并 C6-001，终裁随其 P0）——live 复核证实基类 TwoPhaseCommitSinkFunction 仅 implements SinkFunction+CheckpointParticipant（无 AutoCloseable）、StreamSinkOperator.close:175 instanceof 守卫对本类恒 false、本类无 close override；缺陷本体已由 C6-001 以 P0 立项并通过独立复核终裁，本条为同缺陷重复确证，修复并轨；二轮复核：已保留（终裁随 C6-001 P0）——live 重验基类 :28 类声明、StreamSinkOperator.close:175-177 instanceof 分支、本类全文件唯一 close 命中在 javadoc；C6-001 已完成独立复核+二轮复核维持 P0（主路径泄漏口径精确命中，反驳尝试均无 close 语义），修复并轨不变，随其进入 P0 即时修复通道

### [nop-stream-state-connectors-C7-003] RocksDB 增量恢复临时目录在失败/kill 路径残留，无全局清扫

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalRestore.java:172-175,231-233`；`RocksDBKeyedStateBackend.java:952-956`
- **证据片段**:
  ```java
  Path reconstructed = tempDir.resolve("rocksdb-restore-range-" + result.getCheckpointId());
  deleteRecursively(reconstructed);                       // 仅清理“同 id”旧残留
  Path nonSstDir = Path.of(result.getNonSstDir());
  reconstructRocksdbDir(segmentStore, nonSstDir, reconstructed);  // ← 此处抛出 = 无任何清理
  ...
  int copied;
  try (ColumnFamilyOptions cfOpts = ... ) {               // try/finally 从这里才开始
  ...
  } finally {
      deleteRecursively(reconstructed);                   // 成功路径之前的异常才有兜底
  }
  ```
- **严重程度**: P2
- **并发场景**: 恢复在 task 重建路径单线程执行；残留发生在 `reconstructRocksdbDir` 中途失败（segment 缺失/校验不符/复制 IO 失败，110-147 行任一抛点）或进程在恢复期间被 kill -9（kill/recover 是 owner doc 声明的支持路径）
- **触发时序**: T1 task 崩溃 → T2 globalRecovery 在新实例上执行 `restoreIncremental` → `reconstructRocksdbDir` 将共享 SST 全量拷贝到 `{checkpointBaseDir|dbPath}-restore/rocksdb-restore-range-{id}`（目录尺寸 ≈ 全量状态）→ T3 恢复中途失败（如 F-03 segment 校验拒绝）→ 异常上抛，try/finally 尚未进入，部分重建目录残留 → T4 下次恢复若 durable checkpoint 已前移（新 id），旧 `rocksdb-restore-range-{旧id}` 永不再被同名清理命中，且没有任何保留策略扫描 `*-restore*` 目录（`pruneOldCheckpoints` 只匹配 `cp-{数字}`）
- **现状**: 清理路径不对称——成功/同 id 重试可清，跨 id 失败残留无清扫者（F8）
- **风险**: 每次恢复失败/恢复期 crash 在磁盘上遗留一份全量状态尺寸的目录；反复故障循环的节点磁盘线性耗尽；因 `deleteIfExists` 内部 `Files.walk` 对大目录的删除也会拖慢下一次恢复
- **建议**: 在 `RocksDBKeyedStateBackend` open（或 restoreIncremental 入口）加一次陈旧目录清扫：列举 tempDir 下 `rocksdb-restore-range-*` 且 id 小于当前恢复 id 的目录删除；或将 `reconstructRocksdbDir` 调用移入现有 try/finally 并在 catch 中补删
- **失效面枚举**: ① 快照侧 cp-N 目录 —— F-02 retention 每次快照清扫（删除失败 WARN+重试）→ 成对；② CEP file sink `.tmp` —— abort/commit 删除、fresh-run 冲突时显式保留并 typed 报错 → 成对（语义明确）；③ `deleteIfExists(nativeDir)` 快照前清理 → 成对；④ 本体（恢复临时目录跨 id 残留）→ 本发现
- **信心水平**: 确定（抛点与 finally 边界按行号核实）
- **误报排除**: 不是"best-effort 清理已存在"的误判——best-effort 仅覆盖成功路径 finally 与同 id 前置清理；跨 id 残留确实无清理者
- **复核状态**: 已保留（P2 维持）——live 复核证实 reconstructRocksdbDir 先于 try/finally 执行（其内部任一抛点即残留）、跨 id 残留无任何清扫（pruneOldCheckpoints 仅匹配 cp-{数字}）；本条修正 C6 第 1 轮对该路径「finally deleteRecursively ✓」的不完整判定（C6 仅验证 finally 存在，未覆盖 finally 边界之前的抛点窗口），属本维度首次立项；二轮复核：已保留（P2 维持）——live 重验 reconstructed(:172)/同 id 前置清理(:174)/reconstructRocksdbDir(:177) 先于 try(:183+) 边界、restoreRangeInto 恢复临时目录父目录为 checkpointBaseDir 或 `{dbPath}-restore`（RocksDBKeyedStateBackend:949-952）、pruneOldCheckpoints（RocksDBIncrementalSnapshotStrategy:154-168）仅匹配 `cp-{数字}` 目录；C6 配对表两处「✓」判定（C6-resource-lifecycle.md:28、:238⑥）经核确为不完整判定，本条维持该残留面首次立项，与 C6-006（同方法 close 顺序缺陷）正交

### [nop-stream-state-connectors-C7-004] RocksDB state 层 gauge 在 backend 重建后永久 NaN（监控盲区，F5 邻接）

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:73-84`；`RocksDBKeyedStateBackend.java:278-283,988-990`
- **证据片段**:
  ```java
  private void gauge(MeterRegistry registry, String meterName, String property) {
      registry.gauge(meterName, Tags.empty(), dbRef, ref -> readProperty(ref.get(), property));
  }   // 六个 gauge 全部无标签（Tags.empty()）
  public synchronized void close() {
      dbRef.set(null);   // detach 后 gauge 读数 = NaN
  }
  ```
- **严重程度**: P2
- **并发场景**: metrics scrape 线程（readProperty，synchronized）与 task 生命周期线程（close/register）；每次 task 重建会 open 新 backend → new recorder → 再次 register 同名**无标签** gauge
- **触发时序**: T1 首个 backend open，六个无标签 gauge 以旧 recorder 的 `dbRef` 注册进组合 registry → T2 task 失败/重建，旧 backend `close()`（dbRef=null，弱引用对象随 backend 不可达）→ T3 新 backend open 再调 `register()`，Micrometer 对同名同标签 meter 去重：**返回既有死 gauge，新 valueFunction 被忽略** → T4 此后 `nop.stream.state.rocksdb.*` 六个指标在 Prometheus 面永久输出 NaN。多 subtask 同 JVM 各持 backend 时同理——只有第一个 backend 的读数曾可见
- **现状**: gauge 注册无去重重绑机制，也无 close 时 registry 移除；owner doc 声称这些 gauge 挂在"RocksDBKeyedStateBackend 打开路径"可观测 compaction/cache 水位，但一次恢复后即永久失明（F5 邻接：状态后端压如 compaction pending 恶化时监控面恰好不可见）
- **风险**: 恢复后 state 层观测（`compaction.pending.bytes`、`memtable.usage.total` 等全部六个）静默失效，运维据此判断"无积压"；无告警无报错
- **建议**: gauge 加 per-backend 标签（如 jobId/vertexId/subtask，与 operator 层指标同法），或 close 时 `registry.remove(meterId)` + 注册前先移除同名旧 meter；参考本仓库 R5-CEP-03 的 per-scope 标签形态
- **失效面枚举**: ① `numLateRecordsDropped`（CepOperator）—— 稳定标签幂等去重，生产语义正确（embedded 实例哈希标签的累积另见 C7-009）；② `StreamMetricsRegistries` 引擎/task 层指标 —— 属 nop-stream-runtime/core，越界不审；③ CEP cache-stats 定时器 —— close 时 cancel（releaseCacheStatisticsTimer），成对；④ 本体（RocksDB 六 gauge）→ 本发现
- **信心水平**: 很可能（Micrometer "同名同标签注册去重、新弱引用不生效"为标准语义；未在本环境运行双实例复现）
- **误报排除**: 不是"有界声明缓存"误报——gauge 数量固定六个，缺陷是**值**失明不是 meter 数量爆炸；也不是 scrape 侧问题（组合 registry 与暴露配置无关）
- **复核状态**: 已保留（P2 维持，归并 C6-010）——live 复核证实 StreamMetricsRegistries 为进程级 static composite（永不重建）+ 六 gauge Tags.empty() + close 仅 dbRef.set(null) 无 registry.remove，Micrometer 同名同标签 first-wins 去重语义成立；缺陷本体已由 C6-010 以 P2 立项并通过独立复核终裁，本条为同缺陷重复确证（C6-004 SIGSEGV 竞态为同点位另一缺陷，不与本条混淆）；二轮复核：已保留（P2 维持，归并 C6-010）——live 重验六 gauge `Tags.empty()`(:82-84)、close 仅 `dbRef.set(null)`(:65-67)、StreamMetricsRegistries :32-34 static CompositeMeterRegistry（附 SimpleMeterRegistry 成员）；C6-010 已完成独立复核+二轮复核维持 P2；同点位异缺陷区分维持（C1-002/C6-004 SIGSEGV 竞态为本条之外的独立立案）。不确定项保留：Micrometer first-wins 语义按标准行为与 C6-010 两轮复核一致判定，未在本环境运行双实例复现（残余风险低）

### [nop-stream-state-connectors-C7-005] CEP reconciledTimerKeys 每个历史 key 一条永不回收（F4 内存面）

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:190,1267-1274`
- **证据片段**:
  ```java
  private transient Set<Object> reconciledTimerKeys;      // 190

  private void reconcileTimerLedgerIfNeeded(Object key) throws Exception {
      if (reconciledTimerKeys != null && reconciledTimerKeys.contains(key)) { return; }
      if (reconciledTimerKeys == null) { reconciledTimerKeys = new HashSet<>(); }
      reconciledTimerKeys.add(key);                        // 1274: 唯一写操作，无任何移除
      ...
  }
  ```
- **严重程度**: P2
- **并发场景**: task 线程单线程访问（无并发缺陷面）；问题纯为生命周期——该集合在 run 内只增不删
- **触发时序**: T1 高基数键流（如按 userId keyBy）持续流入 → T2 每个 key 首次 drain 时进入 `reconcileTimerLedgerIfNeeded`，key 对象被永久持有 → T3 该 key 的 timer ledger 与队列随后全部消费完毕（`registeredEventTimeTimersByKey.remove(key)`、`elementQueueState.remove`、idle 时 `computationStates.clear()`）→ T4 但 `reconciledTimerKeys` 中的 key 引用仍在：run 存续期内，每个"曾经活跃过"的 key 占一条，从不回收。同期 per-key 状态全部遵循"消费即删"，唯独此集合例外
- **现状**: 一次性对账标记（plan 2279 Q1）本可用"每 key 一个布尔位 + 空集即弃"表达，却用了无淘汰的全量集合；与 checkpoint 恢复也不对齐（transient，restore 后重置，故仅单 run 内单调）
- **风险**: 高基数（百万级 key）数周级 run 中累积与全量 key 等量的引用集合（每条 = key 引用 + HashSet 槽位），叠加恒在的 keyed state 构成额外常驻；属"长时运行必然触发、随 key 基数放大"的慢性增长，非 OOM 级但违背本模块其余状态的清扫不变式
- **建议**: 与 `registeredEventTimeTimersByKey` 同生命周期管理——`onEventTime/onProcessingTime` 的 ledger 清空点同步 `reconciledTimerKeys.remove(key)`；或把对账标记折叠进 ledger 本身（空 ledger + 已观察过 = 对账完成态），复用现有 remove 点
- **失效面枚举**: ① `registeredEventTimeTimersByKey` —— 空集移除 key（1099-1105, 1144-1152）→ 成对；② `elementQueueState` 桶 —— drain 后 remove（1256）→ 成对；③ `computationStates` —— idle 复位 clear（1183）→ 成对；④ SharedBuffer entries/events —— refcount 0 时 removeEntry/removeEvent（SharedBufferAccessor:331-333,369-371）→ 成对；⑤ SharedBuffer `eventsCount` —— advanceTime 迭代删除（SharedBuffer:278-290）→ 成对；⑥ Guava 双缓存 —— maximumSize 1024 LRU → 有界；⑦ 本体 → 本发现
- **信心水平**: 确定（全文件仅 1274 一处 add，无 remove/removeIf/clear 调用——grep 已证）
- **误报排除**: 不是"无状态单例被并发调用"类误报；也不是 C5 口径的"声明包络内"误判——该集合的增长与事件量无关、与 key 基数×运行时长单调相关，且无 owner doc 边界声明
- **复核状态**: 已保留（P2 维持，归并 C5-001）——live 复核证实全文件仅 1274 一处 add、无任何 remove/removeIf/clear；同一缺陷已由 C5-001 以 P2 立项（含 resetNfaStateIfFullyTimedOut 全清空后 key 引用仍存留的更强论据），本条为同缺陷重复确证，修复并轨；二轮复核：已保留（P2 维持，归并 C5-001）——live 重验全文件 reconciledTimerKeys 仅 :190/:1268/:1271-1274 三类引用、add 为唯一写操作、同文件 registeredEventTimeTimersByKey 族（:999/:1104/:1151）与 elementQueueState(:1256) 均有 remove 对照；merge-adjudication 裁定 18 确认并入 C5-001（P2 主条目）；C5-001 注记的「r4 正确性面先例」为相邻正确性缺陷的历史记录，非本增长缺陷前次登记

### [nop-stream-state-connectors-C7-006] Avro registry 客户端：故障期每记录重试无退避/失败缓存 + HTTP 无请求超时（F5/F 重试风暴双面）

- **文件**: `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java:48,113-129`；`AvroWireFormat.java:111-117`
- **证据片段**:
  ```java
  // AvroSchemaRegistryClient
  this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();  // 仅建连超时
  ...
  resp = http.send(request, HttpResponse.BodyHandlers.ofString());   // 无 .timeout()，读挂起无限期

  // AvroWireFormat —— decode 每条记录调用
  private Schema readerSchemaFor(String subject, Schema writerSchema) {
      try {
          return client.getLatestSchema(subject).schema();   // registry 故障期：每条记录一次 HTTP 尝试
      } catch (StreamAvroException e) {
          return writerSchema;                                // 失败不缓存、不退避、不上抛
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: decode 运行在 Kafka source 任务线程（`valueDeserializer`），是每记录热路径；registry 故障期间每条记录串行发起一次 `GET /subjects/{subject}/versions/latest`
- **触发时序**: T1 Schema Registry 网络黑洞（能建连/或建连慢）→ T2 每条消息 decode 走 `getLatestSchema` → 缓存 miss（失败态不缓存）→ `http.send` 无请求级超时：若对端挂起不回包，任务线程在 HTTP 读上无限期阻塞（仅可中断），数据面静默停流——"不产出也不报错"，唯一可见性是 checkpoint 超时（H3）；若对端快速拒连，则每条记录一次失败调用 + 回落 writer schema，吞吐塌陷且无任何 error 级信号
- **现状**: ① 无请求超时（connectTimeout 只约束 TCP 建连）；② 无失败缓存/负缓存/指数退避——重试频率与记录速率相等（每记录一次），放大下游（registry）故障；③ `readerSchemaFor` 吞掉 StreamAvroException 静默回落 writer schema，与 owner doc "失败面全 typed（registry 不可达…不静默降级）"的声明相悖（该声明只对 encode 侧与 magic/schema 解析成立）
- **风险**: registry 单点故障直接转化为管道停流或吞吐崩塌；schema 演进读取在故障窗口被静默跳过（writer/reader schema 不匹配的兼容性风险下移到消费侧）
- **建议**: ① request 级 `.timeout(Duration.ofSeconds(5))`；② 负缓存（失败态记 TTL，如 5–30s 内直接回落，不重复打 registry）；③ 回落路径至少 WARN 一次/窗口限频，或改为 typed 失败交给任务重启语义（与 owner doc "不静默降级"对齐）
- **失效面枚举**: ① `registerSchema`（encode 侧）—— 失败 typed 上抛 → 正确；② `getSchemaById`（decode writer schema）—— 成功后缓存，故障期仅首次 fetch 阻塞 → 较好（同受无请求超时影响）；③ `getLatestSchema`（decode reader schema）→ 本发现主载体；④ `AvroWireFormat.writerSchemaIds` —— 按 subject 有界 + owner doc 声明的换版边界 → 已声明，不计；⑤ Kafka/JDBC/Message 各连接器的 I/O 超时 —— 见挂死点清单 H2/H6/H8，各自独立
- **信心水平**: 确定（JDK HttpClient 语义：未设 request timeout 时响应等待无限期，connectTimeout 仅建连）
- **误报排除**: 不落入"有界声明存在"豁免——`latestBySubject` 有界但本发现针对的是调用行为而非缓存容量；"低频路径粗粒度"豁免不适用——decode 是每记录热路径
- **复核状态**: 已保留（P2 维持，拆分归属）——① 无 request timeout 面与 C6-008（P2，已独立复核保留）/C8-004 同一缺陷，归并不重复计数；② 负缓存/退避缺失 + readerSchemaFor 静默吞 StreamAvroException 回落 writer schema（违背 owner doc「不静默降级」声明，且成功后永不重试意味着故障窗口内 schema 演进读取被静默跳过）为本维度首次立项，live 复核证实；二轮复核：已保留（P2 维持，拆分归属不变）——live 重验 :48 仅 connectTimeout(10s)、getJson(:102-105)/postJson 构造的 request 均无 `.timeout()`（readJsonResponse:116 直接 send）、readerSchemaFor(:111-117) 捕获 StreamAvroException 静默回落且无负缓存无 WARN 日志；反驳尝试均不成立：owner doc「失败面全 typed：registry 不可达…不静默降级」声明（connectors doc 格式层节）与该方法 javadoc 自述「falls back …when the subject has no registered latest」均不能覆盖 registry 不可达窗口的静默回落（代码 catch 的是全部 StreamAvroException，比 javadoc 自述更宽）。现状：超时面已知未修复（C6-008 已二轮复核维持 P2）；回落面新发现（本维度首次立项）

### [nop-stream-state-connectors-C7-007] 文件 2PC manifest 每 epoch 一键线性增长 + 每次提交全量重写；JVM 锁表只增不删

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:96-99,540-595`；锁获取 `withManifestLock:389-404`
- **证据片段**:
  ```java
  private static final ConcurrentHashMap<String, ReentrantLock> MANIFEST_JVM_LOCKS =
          new ConcurrentHashMap<>();                       // 按 outputDir 键，永不移除
  ...
  private Properties loadManifest() throws IOException {   // 每次提交全量读
      ...
  }
  // updateManifestAtomically: 每次提交全量序列化 + ATOMIC_MOVE 覆写整个 manifest
  ```
- **严重程度**: P3
- **并发场景**: commit 线程持目录级锁（JVM 内 ReentrantLock + 跨 JVM FileLock）做 load→改→全量重写；多 subtask/多作业在同一 JVM 共享 `MANIFEST_JVM_LOCKS`
- **触发时序**: 数周级 run、30s checkpoint 间隔 → 每 epoch 在 manifest 增加一键（86400 键/月），且每次 commit 重读+重写整个 manifest（O(#epochs)）→ 单文件与单次提交成本随作业运行时长线性劣化；同时 JVM 内每个 distinct 输出目录在 `MANIFEST_JVM_LOCKS` 留一把永不回收的锁（多作业运维面下随作业数累积）。附带观察：`channel.lock()` 无超时（挂死点清单 H1——engine 侧 checkpoint 超时兜底，最终可见，不升级）
- **现状**: manifest 既是提交记录又是幂等守卫，全量重写保证原子性但无压缩/分段/轮转设计；锁表为进程级静态结构，无 dispose 钩子
- **风险**: 长期运行的提交延迟缓慢上升（每次 commit 的 IO 与锁持有时间随键数增长，放大 H1 的持锁窗口）；锁表为小对象泄漏（每目录约百字节级，量级无害）
- **建议**: manifest 按 epoch 段分片（如 `manifest-<epochBucket>.properties`）或定期归档历史键；`MANIFEST_JVM_LOCKS` 可换 Caffeine 弱引用/上限缓存，或在作业 close 时按引用计数释放
- **失效面枚举**: ① JDBC 台账 —— 同族持久化账本（P1，见 C7-001）；② Kafka 2PC pendingCommits —— 基类提交后移除，有界；③ per-epoch 终文件（`epoch-N.txt`）—— 即输出数据本体，属交付物非账本，不计；④ 恢复条目键集 `recoveredEntryKeys` —— 有界于 pending commits 数 → 成对；⑤ 本体 → 本发现
- **信心水平**: 确定
- **误报排除**: 不按 P1 报——owner doc 已把 manifest 钉定为 exactly-once 设计的一部分（幂等/修复语义依赖全量键集），增长速率（每 checkpoint 一键）与单键尺寸使数月内风险有限，属可量化慢性病
- **复核状态**: 已保留（P3 维持，归并）——live 复核证实 manifest 每 epoch 一键全量重写 + MANIFEST_JVM_LOCKS 无移除；两个构成面均已他维立项（manifest 增长面 = C5-007 P2，含 O(E) 每 commit 放大论证；锁表面 = C6-009 P3，已独立复核保留），本条为两前项合并复述，按其中较温和口径维持 P3，修复并轨；二轮复核：已保留（P3 维持，归并不变）——live 重验 manifest.setProperty 每 epoch 一键(:345/:373)、loadManifest(:540-552)+updateManifestAtomically(:555-589) 全量重写、MANIFEST_JVM_LOCKS 全文件仅 :98/:390 两处引用无 remove；锁表面历史登记核实至 2026-09-26 审计 dim-04（P3，与 C6-009 同锚点）、manifest 面归并 C5-007（P2），修复随持久化账本保留策略族批次（C5 终裁排批③）；H1（channel.lock 无超时）维持情报级不升级

### [nop-stream-state-connectors-C7-008] Avro per-id schema 缓存无上限、无淘汰（F7 族慢速候选）

- **文件**: `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java:36,68-70`
- **证据片段**:
  ```java
  private final Map<Integer, Schema> schemaByIdCache = new ConcurrentHashMap<>();
  ...
  public Schema getSchemaById(int id) {
      return schemaByIdCache.computeIfAbsent(id, this::fetchSchemaById);  // 见一个 id 收一个，永不淘汰
  }
  ```
- **严重程度**: P3
- **并发场景**: decode 热路径（任务线程）读、首次 miss 时 HTTP 拉取并放入；无容量/TTL 约束
- **触发时序**: 长时运行 + schema 持续演进（每版本新 id）→ 缓存随 registry id 空间单调增长；每个条目为一个已解析 Avro `Schema` 对象。增速 = schema 版本演进速率（正常部署下每年数十至数百条，KB 级/条）。病理输入（broker 上流入伪造/随机 schemaId 的消息）时每条新 id 触发一次 registry 往返 + 一条永久驻留，可被外部输入驱动放大
- **现状**: 与 `latestBySubject`（按 subject 有界）不同，per-id 缓存的键空间由 registry 侧 id 分配决定，模块内无上限声明；owner doc 的"已知边界"只覆盖 encoder 侧 subject 缓存不感知换版，未覆盖本缓存的容量面
- **风险**: 常规部署增长缓慢（不构成近期风险）；异常/恶意 id 流下与 C7-006 的无失败缓存叠加为"每 id 一次 HTTP + 永久驻留"的双重复利
- **建议**: 换平台 `io.nop.commons.cache.ICache` + `CacheConfig`（容量上限 + LRU），或 Guava `maximumSize`（同模块 SharedBuffer 已有先例）；顺手与 C7-006 的负缓存一并落
- **失效面枚举**: ① `latestBySubject` —— 按 subject 有界 → 无缺陷；② `AvroWireFormat.writerSchemaIds` —— 按 subject 有界 → 无缺陷（换版边界已声明）；③ 本体（schemaByIdCache）→ 本发现
- **信心水平**: 确定（代码事实）；实际危害评级依赖部署的 schema 演进频率
- **误报排除**: 不落入"键空间可枚举豁免"——schema id 随演进单调递增且可被消息输入驱动；也不与 C7-006 重复计数（该条针对调用行为，本条针对驻留容量）
- **复核状态**: 已保留（P3 维持，修正触发时序）——live 复核证实 schemaByIdCache 无上界无淘汰；修正原触发时序一处过强表述：fetchSchemaById 抛出时 computeIfAbsent 不记录映射，伪造 id 投毒只放大 HTTP 调用（归 C7-006 面）不放大驻留，实际增长面 = 真实 schema 演进速率，P3 恰当；C5 第 1 轮盘点 #25 以「键空间本质可枚举且小」判 OK 不成立（id 空间由 registry 单调分配且无界）；二轮复核：已保留（P3 维持）——live 重验 :36 无上界 ConcurrentHashMap、:68-70 computeIfAbsent 无淘汰；C5 盘点 #25 原文核实（「键空间本质可枚举且小，OK（已知边界声明）」+「坏 id fetch 失败不入缓存，不可投毒」）——后者与原审修正一致成立，前者判 OK 不成立，owner doc 已知边界仅覆盖 encoder 侧 subject 缓存，decoder 侧容量面未声明；现状：新发现（本维度首次立项），当前 HEAD 未修复

### [nop-stream-state-connectors-C7-009] CEP 降格计数器 instance-identity 兜底标签使 meter 随实例累积（embedded 形态）

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:379-387,438-441`
- **证据片段**:
  ```java
  private String effectiveLateDropOperatorTag() {
      return lateDropMetricOperator != null
              ? lateDropMetricOperator
              : "cep@" + Integer.toHexString(System.identityHashCode(this));  // 每实例唯一标签
  }
  ...
  this.numLateRecordsDropped = StreamMetricsRegistries.registry()
          .counter(LATE_ELEMENTS_DROPPED_METRIC_NAME,
                  "operator", effectiveLateDropOperatorTag(),
                  "subtask", effectiveLateDropSubtaskTag());        // close() 无注销
  ```
- **严重程度**: P3
- **并发场景**: open 时注册进进程级组合 registry；无 TaskLocation 的实例（embedded/测试/非部署装配形态）以 identityHashCode 兜底，每个实例一个新 meter
- **触发时序**: 同一 JVM 内反复构建/打开 CepOperator 实例且未走 `copyForSubtask(TaskLocation)`（embedded 多轮作业、框架测试宿主）→ 每实例注册一个唯一标签的 `numLateRecordsDropped` meter → 实例关闭不注销 → meter（及其标签序列）在 registry 中单调累积，`/metrics` 输出面随之膨胀
- **现状**: 生产部署路径（TaskLocation 标签）语义正确——同 job/vertex/subtask 重复注册被 Micrometer 去重，幂等；缺陷面只在"无位置实例反复创建"的形态
- **风险**: 指标基数（label cardinality）慢性膨胀拖慢 scrape 与存储；与 owner doc 指标名表的稳定标签契约偏离
- **建议**: 兜底标签改为稳定的静态值（如 `"cep-unlocated"`）——同标签去重即天然幂等；或 open 时记录 meter 引用、close 时 `registry.remove`
- **失效面枚举**: ① 生产部署标签形态 —— 稳定 TaskLocation，幂等 → 无缺陷；② RocksDB 六 gauge —— 见 C7-004（另一种标签缺失病理：无标签去重过度）；③ 本体 —— 兜底标签唯一化导致去重失效 → 本发现；④ cache-stats 定时器 —— close cancel 成对 → 无缺陷
- **信心水平**: 确定（代码事实）；触发形态限定于非部署装配
- **误报排除**: 不按"测试专用代码"豁免——CepOperator 是生产类，兜底标签是生产代码分支，embedded 形态是 fraud-example 等场景的真实用法；危害评级（P3）已如实反映低频性
- **复核状态**: 已保留（P3 维持，归并 C6-012）——live 复核证实 identityHashCode 兜底标签 + close 无注销；同一缺陷已由 C6-012 以 P3 立项（C6 第 2 轮，C6-round2 条目尚未独立复核），本条为同缺陷重复确证，修复并轨；二轮复核：已保留（P3 维持，归并 C6-012）——live 重验 :379-383 identityHashCode 兜底、:438-441 注册、close 链无 registry 摘除（全文件 `.remove` 命中均为 timer/queue 结构）；C6-012 其后已完成自身二轮独立复核维持 P3（merge-adjudication 裁定 19 以本条为锚点，双锚点同缺陷成立）；现状：已知未修复（campaign C6-012 P3 终裁；R5-CEP-03 修复 commit 2bb7c013a7 之后的残留面，生产 TaskLocation 标签路径语义正确，缺陷面限于无位置实例形态）

## Owner doc 已声明边界（核对后不计缺陷）

| 声明 | 位置 | 核对结论 |
|---|---|---|
| FileSource 为 deploy-time discovery 有界源（连续发现 deferred） | connectors doc + `FileSplitEnumerator` javadoc | `finishedSplits/finishedFiles` 增长属声明边界 |
| kafka-2pc 残余重复窗口 [barrier commit, checkpoint durable) | connectors doc 成熟度豁免 | 语义窗口非本维度对象；`committedHighWater`/pendingCommits 记账已验证有界 |
| Avro encoder 侧 subject 缓存不感知换版 | connectors doc「已知边界」 | `writerSchemaIds` 增长与换版不拾取均为声明边界（decoder 侧 per-id 缓存容量未声明 → C7-008 立项） |
| message source/sink 语义由后端承载（EXPERIMENTAL） | connectors doc 能力矩阵 | H7/H8 的后端阻塞等待不立项 |
| CEP 仅支持 Memory backend（R5 CEP-1 fail-fast） | owner doc + `CepOperator.resolveKeyedBackend` | RocksDB×CEP 组合已 fail-fast，无长时累积混合面 |
| kafka-2pc EXPERIMENTAL + jdbc/file/kafka source PRODUCTION 判据 | 能力矩阵 | 恢复语义自愈链（pendingCommits 重放/abort、initTransactions fencing、manifest 修复、台账幂等）逐条与 live 一致 |

## 自愈与 fencing 语义核对（无发现项，记录结论）

- **2PC 三兄弟恢复链**: file（manifest 幂等 + recoveredEntryKeys 来源判别 + final-exists 修复）、jdbc（台账三元键守卫 + guard 命中 WARN + pendingCommits 同步清除）、kafka（initTransactions fencing + 高水位幂等 + restoreFromEpoch 先于基类 re-commit）——三链均有 typed fail-fast 兜底，无"半个成功"静默面（F2）。
- **CEP 处理时间恢复**: R5 CEP-7 open 时从 ledger 重挂墙钟 timer、R6 CEP-01 fire 侧链式 re-arm——恢复后 timer 不悬空（挂死防"永不触发"面已闭合）。
- **时钟**: 台账时间戳仅为信息列（`CoreMetrics.currentTimestamp`）；CEP 窗口/水位全部走事件时间或引擎 monotonic processing time；未发现依赖系统时钟单调性的超时计算（回拨敏感面在 nop-stream-core 的 ProcessingTimeService，越界）。
- **epoch 溢出**: 各处 epochId 均为 long，checkpoint id/快照 id 计数器（`incrementalSnapshotIdCounter`）为 AtomicLong——天/周级运行无溢出面。

# C7 长时运行稳定性 — 深挖第 2 轮追加

- **审计编号**: nop-stream-state-connectors-C7（第 2 轮）
- **前序**: `C7-long-running.md`（第 1 轮：15 项单调增长源 G1–G15 + 10 项挂死点 H1–H10 → C7-001..009）
- **本轮盲区**: 按派发指令定向四轴——① metrics 标签基数爆炸；② 自愈后旧状态清理；③ 重试风暴退避；④ 时钟依赖
- **审计日期**: 2026-10-14
- **盲审边界**: 本轮仅读取本维度第 1 轮文件 + live code + owner docs（`docs-for-ai/03-modules/nop-stream.md`、`nop-stream-connectors.md`）+ `ai-dev/design/nop-stream/state-management-design.md`（TTL/processing-time 声明核对）；未读其他审计目录/plans/bugs/lessons

## 本轮结论（终止信号）

新增发现 2 条（C7-010 P2、C7-011 P3）。四轴盲区中，metrics 基数与重试退避两轴**无新发现**（核查结论见文末清单）；时钟轴除 C7-010 关联的 TTL 时钟语义外全部为行业对齐/已声明边界。第 2 轮之后本维度剩余未覆盖面均为零散低价值情报项（见「未立项情报项」），**建议终止本维度深挖迭代**。

---

### [nop-stream-state-connectors-C7-010] RocksDB 后端 TTL 清理与 checkpoint 成败耦合：声明的 60s 周期 sweep 仅存在于 Memory 后端，checkpoint 关闭/长间隔时过期状态无任何回收触发点

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:443-445`（setCurrentKey 无 sweep 挂钩）、`:797-824`（cleanupExpiredEntries 唯一自动调用点在 snapshotState）、`:827-836`（唯一调用方）；对照 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:222-244`（Memory 后端的 nanotime 门控周期 sweep）
- **证据片段**:
  ```java
  // RocksDBKeyedStateBackend —— setCurrentKey 无任何 sweep 钩子：
  @Override
  public void setCurrentKey(K key) {
      this.currentKey = key;
  }
  // cleanupExpiredEntries() 的全部调用点（全仓 grep 证实仅 2 处，均在 checkpoint 路径）：
  public StateSnapshot snapshotState() throws Exception {
      if (incrementalCheckpointEnabled) { cleanupExpiredEntries(); return snapshotIncremental(); }
      cleanupExpiredEntries();
      return RocksDBSnapshotSerDe.snapshotState(this);
  }

  // MemoryKeyedStateBackend —— 同名机制存在（设计文档声明的形态）：
  private void maybeSweepExpiredEntries() {
      if (!anyTtlBound || ttlSweepIntervalMillis <= 0) { return; }
      long now = System.nanoTime();                        // 门控用 monotonic 时钟，正确
      if (lastTtlSweepNanos != 0 && now - lastTtlSweepNanos < ttlSweepIntervalMillis * 1_000_000L) { return; }
      lastTtlSweepNanos = now;
      cleanupExpiredEntries();
  }
  ```
- **严重程度**: P2
- **并发场景**: 单任务线程（mailbox 模型，无并发缺陷面）；问题为生命周期拓扑——设计文档声明的清理机制「TTL 清理挂 setCurrentKey 周期 sweep（默认 60s，`setTtlSweepIntervalMillis(≤0)` 关闭）」（`ai-dev/design/nop-stream/state-management-design.md:420`）在生产大状态后端上不存在
- **触发时序**: T1 作业使用 RocksDB 后端 + TTL-enabled state（dedup/会话/幂等窗口类典型用法：key 写入后不再被触碰），checkpoint 间隔配置为小时级或显式 `checkpointEnabled=false`（`CheckpointConfig.java:48-49,149-150` 为公开可配项）→ T2 key 过期后流上再无该 key 的访问 → T3 无 barrier（checkpoint 关闭）或 barrier 长期不到达 → `snapshotState()` 不执行 → `cleanupExpiredEntries()` 零次运行 → T4 过期 entry 永久驻留 RocksDB 列族，且 `TtlContext.timestamps` sidecar（`TtlContext.java:57` 的 HashMap，每 touched key 一条 ByteBuffer+Long）同步只增不删。访问路径惰性逐出（`RocksDBValueState.java:59-69`）只覆盖"会再次被读写的 key"，对 TTL 的主用途（永不回访的 key）无效
- **现状**: RocksDB 后端的 TTL 清理实际为两个触发点：checkpoint 前置 sweep + 访问惰性逐出；与 checkpoint 成败耦合。设计文档 §11.1 声明「TTL 启用的 state 会按配置过期并被清理」、§12 声明「状态条目在指定 TTL 后自动失效」，`TtlCleanupStrategy` javadoc（`TtlCleanupStrategy.java:16-21`）声明「caller-driven、无自动后台清理线程」但未声明唯一自动 caller 就是 checkpoint；文档 420 行的 60s 周期 sweep 未标注「仅 Memory 后端」，与实现不一致
- **风险**: checkpoint 关闭或长间隔的长期运行中，TTL 配置对"不再回访的 key"静默失效——RocksDB 存储与 sidecar 双面单调增长，恰好击穿 TTL 被配置要去防止的增长面；运维按文档相信「60s 周期 sweep 在清理」，无任何指标/日志暴露 sweep 从未发生（checkpoint 关闭时甚至无观测面）。设计文档虽把「RocksDB sidecar 无界增长」挂给资源上界专项，但该豁免只覆盖 sidecar 内存面，不覆盖**存储行回收机制缺失**
- **建议**: 把 Memory 后端的 `maybeSweepExpiredEntries` 钩子移植到 `RocksDBKeyedStateBackend.setCurrentKey`（同 nanotime 门控 + `setTtlSweepIntervalMillis`，逐字复用，成本同为一布尔一长比较）；或在 owner 文档/设计文档显式声明「RocksDB 后端 TTL 回收 = checkpoint 耦合 + 访问惰性」并给出 checkpoint 关闭场景的替代运维手段（定期调用 `cleanupExpiredEntries()` 的维护入口）
- **失效面枚举**（同族 TTL 清理触发点逐一核对）: ① Memory 后端 setCurrentKey 周期 sweep（60s，nanotime 门控）→ 存在且正确；② RocksDB 后端 snapshotState 前置 sweep → 存在但 checkpoint 耦合 → 本发现；③ RocksDB 状态类访问惰性逐出（ValueState:59-69，其余 7 个 RocksDB*State 同构 bindTtl/isExpired 路径）→ 存在，仅覆盖回访 key；④ `TtlCleanupStrategy.lazyEviction=false`（后台清理档位）→ 已声明 no-op 保留槽，非缺陷；⑤ `TtlContext.sweepExpired` 迭代删除实现（storage 先删、sidecar 后删、失败保留重试）→ 语义正确，非缺陷
- **信心水平**: 确定（调用点全仓 grep 证实；两后端实现逐行对照；设计文档三处声明逐条核对）
- **误报排除**: 不落入「有界声明存在」豁免——设计文档声明的机制（60s 周期 sweep）在 RocksDB 后端不存在，属 doc-code 分歧而非已声明边界；也不落入「平台基础设施不重审」——缺陷载体 RocksDBKeyedStateBackend 属本模块审计范围（Memory 后端仅作同族对照，其所在 nop-stream-core 未立项）
- **复核状态**: 已保留（P2 维持）——live 复核证实 RocksDB 后端 setCurrentKey（:443-445）无任何 sweep 挂钩、cleanupExpiredEntries 全仓调用点仅 snapshotState 两处（:831/:834）、Memory 后端同机制（maybeSweepExpiredEntries 挂 setCurrentKey，60s nanotime 门控）存在，设计文档 :420 声明确未限定后端；全审计目录无重复立项，本维度首次立项；二轮复核：已保留（P2 维持）——live 重验 setCurrentKey(:443-446) 仅赋值、cleanupExpiredEntries(:797) 调用点全仓仅 :831/:834、Memory 后端 setCurrentKey(:183-187)→maybeSweepExpiredEntries(:233-243) 对照成立、CheckpointConfig.checkpointEnabled 为公开可配项（默认 true，setter 公开）；反驳尝试均不成立：① checkpoint 关闭/小时级间隔非病理配置（公开旋钮）；② 设计文档 :420「TTL 清理挂 setCurrentKey 周期 sweep」未限定后端而 TtlCleanupStrategy javadoc 仅诚实声明「caller-driven、无后台线程」未披露唯一自动 caller 是 checkpoint，doc-code 分歧成立；③ 访问惰性逐出（RocksDBValueState.value 的 isExpired→delete）对永不回访 key 无效。精确化补充：snapshotState 另有 ops 诊断端点手动触发面（StreamTaskInvokable:623 keyed-state snapshot GET），属人工调用非自动周期 sweep，不影响「TTL 回收与 checkpoint 成败耦合」主判定。现状：新发现（全审计目录唯一），当前 HEAD 未修复

### [nop-stream-state-connectors-C7-011] kafka-2pc 的 checkConnection 以 initTransactions（fencing 原语）作连通性探测：副作用未入 owner doc 探测红线表，安全性依赖「生产路径恒为 TaskLocation 限定 id」这一偶然事实

- **文件**: `nop-stream/nop-stream-connector-kafka/src/main/java/io/nop/stream/connector/kafka/KafkaTwoPhaseCommitSink.java:306-312`（checkConnection）；`:134-148`（newProducer 以实例 transactional.id 建连）；对照 owner doc `docs-for-ai/03-modules/nop-stream-connectors.md:95-103`（探测逐族红线表，缺 kafka-2pc 行）
- **证据片段**:
  ```java
  @Override
  public void checkConnection() throws Exception {
      try (Producer<String, byte[]> probe = newProducer()) {   // transactional.id = 本实例 id
          probe.initTransactions();      // fencing 原语：bump 该 id 的 producer epoch，
          probe.beginTransaction();      // 并 abort 该 id 在 broker 侧的进行中事务
          probe.abortTransaction();
      }
  }
  /** Test/ops visibility. */
  public String getTransactionalId() { return transactionalId; }
  ```
- **严重程度**: P3
- **并发场景**: 提交前校验路径（`StreamConfValidator.java:186` / `StreamConnectorCatalog.java:141-151` 经 `StreamConnectivityProber` 调用）在 ops 时机执行；与运行中任务的 producer 共享 broker 侧 transactional.id 状态空间
- **触发时序**: 平台自身路径下无害——全部三个生产部署点（`GraphExecutionPlan.java:454`、`RemoteGraphExecutionPlanBuilder.java:313`、`SupervisionLoop.java:847`）均走 `deepCopy(TaskLocation)`，任务 id 恒为 `prefix|jobId|vertexId|taskIndex`，而探测用的是模板的裸 `prefix` id（`KafkaTwoPhaseCommitSink.java:104-108` 构造器），二者不同 id，initTransactions 的 epoch bump 只作用于无人使用的裸 id。**潜在时序**（依赖偶然守卫）：T1 任一未来部署路径改用无身份 `deepCopy()`（`OperatorChain.java:284-290` → `copyForSubtask(-1)` → 基类 `copyForSubtask(int)` 对 index 0 返回 `this`，`TwoPhaseCommitSinkFunction.java:129-137`，运行实例即持裸 prefix id）→ T2 运维对同一 conf 执行 conf-validate --connect / dry-run → T3 probe 的 `initTransactions()` bump 裸 id epoch 并 abort 其开放事务 → T4 运行中 producer 下一次 send/commit 抛 ProducerFenced → 健康作业被探测动作打挂（fail-loud 可恢复，但属探测引入的伪故障）
- **现状**: owner doc 的探测红线表对 file/jdbc/batch-loader/batch-consumer/debezium/message 六族逐一声明了探测语义与副作用红线，唯独 kafka-2pc 缺行——而它是唯一改变 broker 侧分布式事务状态（epoch bump + abort 标记）的探测实现；「平台自身调用安全」依赖「生产恒走 TaskLocation 限定」这一未在任何文档显式声明的耦合
- **风险**: 当前生产路径无实际触发面（故 P3）；文档缺口使后续改动（新增部署路径、ops 工具直接以 `getTransactionalId()` 揭示的运行 id 构造探测实例）可能在无感知下引入「校验动作打挂健康作业」的故障模式
- **建议**: ① owner doc 探测红线表补 kafka-2pc 行：声明 initTransactions 的 fencing 副作用、探测 id = 裸 prefix（与运行 id 恒异的前置条件）、红线 = 不得以运行中 subtask 的限定 id 构造探测；② 代码侧防御：checkConnection 内断言「本实例 transactional.id 不含 `|` 分隔的限定段时才允许探测」或直接以 `prefix + "|probe"` 专用 id 探测，使守卫从偶然变为显式
- **失效面枚举**（ConnectivityCheckable 全部 7 个实现逐一核对）: ① `FileTwoPhaseCommitSink.checkConnection:496-503` begin+rollback，无文件写入 → 已声明，安全；② `JdbcTwoPhaseCommitSink.checkConnection:778-782` begin+幂等台账 DDL+rollback → 已声明（台账表豁免），安全；③ `BatchConsumerSinkFunction.checkConnection:207-213` 构造级 null 断言 → 无副作用，安全；④ `BatchLoaderSourceReader` 侧 loaderProvider.setup+关闭 → 已声明，安全；⑤ `DebeziumCdcSourceFunction.checkConnection:603-609` 参数级（不拉引擎不连库）→ 已声明，安全；⑥ `MessageSource/SinkFunction` 显式 SKIP → 已声明，安全；⑦ 本体（KafkaTwoPhaseCommitSink）→ 本发现
- **信心水平**: 确定（代码事实与文档缺行均已逐行核对）；潜在危害评级依赖未来部署路径是否引入裸身份运行实例
- **误报排除**: 不属于「低频管理路径粗粒度合理」豁免——问题不是探测慢/粗，而是探测语义与文档红线契约的缺口 + 隐式安全前提；也未与第 1 轮 C7-002（producer 泄漏）重复——该条针对 close 契约，本条针对探测契约与文档完备性
- **复核状态**: 已保留（P3 维持）——live 复核证实探测 producer 以裸 prefix id 执行 initTransactions、三处生产部署点（GraphExecutionPlan:454 / RemoteGraphExecutionPlanBuilder:313 / SupervisionLoop:847）均走 TaskLocation 限定 id、owner doc 探测红线表确缺 kafka-2pc 行；当前生产路径无实际触发面，P3 文档契约缺口定级恰当，全审计目录无重复立项；二轮复核：已保留（P3 维持）——live 重验 checkConnection(:306-312) 以实例裸 id 执行 initTransactions+beginTransaction+abortTransaction、三部署点 deepCopy(TaskLocation) 逐点核实、copyForSubtask(int)(TwoPhaseCommitSinkFunction:129-137) 对 idx≤0 返回 this（OperatorChain.deepCopy() 无参→deepCopy(-1) 的潜在裸 id 面真实）、owner doc 红线表（connectors doc「提交前探测能力」节）六族齐备唯缺 kafka-2pc；C6 第 2 轮对探针族的「TWR ✓」判定为资源生命周期面，与本条 fencing 副作用契约缺口正交，无翻案。现状：新发现（全审计目录唯一），当前 HEAD 未修复

---

## 四轴盲区核查结论（无发现项，记录核查面）

### ① metrics 标签基数爆炸

全 8 模块 grep `counter(|gauge(|timer(|MeterRegistry|Tags` 证实指标注册点全集为两处：CepOperator 的 `numLateRecordsDropped` counter 与 RocksDBMetricsRecorder 的六个无标签 gauge——两类病理（identityHashCode 兜底标签累积 / 无标签去重致失明）第 1 轮已分别立项 C7-009、C7-004，本轮无第三种形态。`StreamMetricsRegistries.registry()` 本体属 nop-stream-core，沿用第 1 轮「越界不审」边界。Kafka/JDBC/File/Message/Batch/Debezium/Avro 七个连接器模块零指标注册点。

### ② 自愈后旧状态清理

- 文件 2PC `.tmp`：崩溃残留要么被同 epoch 重放覆盖（tempPath 由 epochId+subtask 确定性派生），要么在 fresh-run 冲突路径显式保留并 typed 报错（R6 CON-01）→ 生命周期闭合，非缺陷。
- RocksDB 主 DB 目录：`close()` 只释放 native 句柄从不删目录（恢复语义要求目录跨重启存活）；dbPath 由使用方传入，8 模块内无按 job 派生 dbPath 的代码，`StreamStateResetTool` 只清 `{checkpointBaseDir}/{jobId}`——废弃作业 dbPath 目录的清理责任在部署层，模块内无可立项代码路径（情报项见文末）。
- Debezium：`registerFailureListener/unregisterFailureListener` 在 run 的 finally 成对（R5-CON-03 链）；`AR-03` first-run stale-registry clear + WI9 seed 的 REPLACE 语义经 `seedOffsetStoreIfPresent` 核对一致。
- Kafka 2PC 自愈链（pendingCommits 重放 / 高水位幂等 / initTransactions fence）第 1 轮已核对，本轮仅补充 C7-011 的探测面。

### ③ 重试风暴退避

全 8 模块 grep `retry|backoff|attempt` + 逐文件核对证实：**模块内不存在任何内建重试循环**。BatchConsumerSinkFunction flush 失败 typed 上抛保留缓冲（`BatchConsumerSinkFunction.java:126-129`），重试委托引擎任务重启语义（有 restart cap 兜底）；Kafka producer 依赖 client `delivery.timeout.ms`（默认 120s）上限封顶 send/flush 等待；Avro registry 的无退避重试已由第 1 轮 C7-006 立项；Message sink 为后端契约（owner doc 已声明）。无新发现。

### ④ 时钟依赖

- TTL 过期判定走 wall clock（`SystemTtlTimeProvider` = `System.currentTimeMillis()`，`TtlContext.java:79,82-84`）：回拨使过期延迟（幅度=回拨量，有界）、前跳使新 entry 批量过期。与 Flink processing-time TTL 同为 wall-clock 行业对齐实现（跨重启的时间戳可比性要求 nanoTime 不可持久化），且设计文档 §8（:318）已显式声明 processing-time 语义依赖 wall clock → 不立项。
- Memory 后端 sweep 门控正确使用 `System.nanoTime()`（monotonic）——实现层无回拨敏感面。
- CEP：窗口/水位全走事件时间或引擎 processing time（`internalTimerService.currentProcessingTime()`），恢复重挂 wall-clock timer 的非确定性已由设计文档 §8 声明；CepOperator 内无 `System.currentTimeMillis` 直接调用。
- Debezium/Message reader 的 `getProcessingTime()` 桥接桩（`MessageSourceReader.java:250`、`DebeziumCdcSourceReader.java:353`）：仅实现接口，其 legacy function 只走 `collect`，无调用方 → 死代码级，无时钟暴露面。
- epoch/代际溢出：第 1 轮已核对（long/AtomicLong），无新面。

## 未立项情报项（零散低价值，累计即终止信号依据）

| # | 情报 | 位置 | 不立项理由 |
|---|---|---|---|
| I1 | Debezium 并行模式 schema-history 文件按实例限定（`{name}-{subtask}` 前缀，`DebeziumSourceSupport.java:83-96`）：并行度缩容后旧实例历史文件成为磁盘孤儿，无清扫 | nop-stream-connector-debezium | 量级 KB–MB/文件、增速=并行度变更次数；store 本体语义归 nop-message-debezium（越界） |
| I2 | JDBC schema-history 表按实例后缀（`_nop_cdc_schema_history_{instance}`）：DDL 事件 append-only 增长 | 同上 | Debezium 引擎自有存储语义，模块不持有清理责任；增长速率=DDL 频率 |
| I3 | 废弃 dbPath 的 RocksDB 本地目录无 owner 清理（close 不删、reset 工具不覆盖 checkpointBaseDir 之外路径） | RocksDBKeyedStateBackend.close:979+ / StreamStateResetTool | 目录生命周期属部署层契约，模块内无按 job 派生路径的代码可挂清理 |
| I4 | `TtlCleanupStrategy.lazyEviction=false` 为已声明 no-op 保留槽 | TtlCleanupStrategy.java:23-31 | 文档已显式声明无行为，非缺陷 |

# C7 长时运行稳定性 — 维度复核结论

- **复核编号**: nop-stream-state-connectors-C7（复核轮）
- **复核日期**: 2026-10-14
- **复核者**: 独立复核子代理（解除盲审——可读 ai-dev/audits 历史审计与 git log，逐条重读 live code + 调用方）
- **复核方法**: 对 11 条初审+深挖发现逐条重读 live code（含 C5/C6/C8 各维度已立项条目交叉核对），三态判定；判级规则——写不出具体触发时序的并发发现降 P2、已声明边界+守卫不算缺陷、跨维度同缺陷合并归属并统一终裁。
- **live code 状态**: 全部 11 条缺陷形态在当前 HEAD（2115b450e6）均未修复，无一条因上游修复而失效。

## 逐条判定

**C7-001 — 保留，P1 终裁维持（归并 C5-006）**。三重证实：① 全模块 grep `delete|cleanup|purge|retention|ttl|truncate` 对台账表零命中，`writeLedgerEntry` 仅 INSERT，`ledgerExistsSql` 仅 PK SELECT；② runtime 维护面（StreamStateResetTool 等）均不触达台账表，owner doc 无保留策略声明；③ 触发时序为确定性（每成功 checkpoint 每 subtask 一行），非竞态面。现状归属：同一缺陷已由 C5-006 以 P2 立项。判级差异裁定：C7 维度判级口径「P1 = 长时运行必然触发的无界增长」为字面命中（每 checkpoint 必写、无任何清理路径、手工删行有踩坏幂等守卫风险、无官方运维手段），终裁维持 P1 并在修复时与 C5-006 并轨（一条修复同时关闭两个编号）。

**C7-002 — 保留（归并 C6-001，终裁随其 P0）**。live 复核证实三点 close 契约缺口：基类 `TwoPhaseCommitSinkFunction` 仅 implements `SinkFunction, CheckpointParticipant`（无 AutoCloseable）；`StreamSinkOperator.close():175` 的 instanceof 分支对本类恒 false；本类无 close override，`ensureTransactionOpen` 的 producer 为唯一创建点、全类唯一 `close` 命中在 javadoc 注释。现状归属：同一缺陷已由 C6-001 以 P0 立项并通过独立复核终裁 P0（C6 复核以四点独立证实维持），本条为同缺陷重复确证，非重复修复面——修复并轨。

**C7-003 — 保留，P2 维持（新锚点，修正 C6 误判）**。live 复核证实：`reconstructRocksdbDir` 在 `restoreRangeInto` 的 try/finally 边界之前执行，其内部 SST 缺失/校验拒绝/复制 IO 失败任一抛点即残留部分重建目录（尺寸 ≈ 全量状态）；`pruneOldCheckpoints` 仅匹配 `cp-{数字}`，`rocksdb-restore-range-*` 跨 id 残留无任何清扫者；同名前置清理只命中同 id。现状归属：C6 第 1 轮配对表与本轮 C6-round2 自愈核对均将该路径判「finally deleteRecursively ✓」——该判定不完整（只验证了 finally 存在，未覆盖 finally 边界之前的抛点窗口与跨 id 场景），C6-006（同方法 close 顺序缺陷）为另一独立缺陷，与本条不重复。本条为该残留面首次立项。

**C7-004 — 保留，P2 维持（归并 C6-010）**。live 复核证实：`StreamMetricsRegistries` 为进程级 static CompositeMeterRegistry（附 SimpleMeterRegistry 累积成员，永不重建）；六 gauge 全部 `Tags.empty()`；close 仅 `dbRef.set(null)` 且全类无 `registry.remove`；Micrometer 同名同标签 first-wins 去重语义成立（原审自评「很可能」经语义核对升级为成立）。现状归属：同一缺陷已由 C6-010 以 P2 立项并通过独立复核终裁。注意区分同点位异缺陷：C6-004（gauge 抓取锁外取参 SIGSEGV 竞态，P1）与本条（静默失效 NaN）机制不同，均独立保留。

**C7-005 — 保留，P2 维持（归并 C5-001）**。live 复核证实全文件 `reconciledTimerKeys` 仅 ：190 声明、:1268 contains、:1271-1274 add，无任何移除点；同文件 `registeredEventTimeTimersByKey` 有完整空集移除语义，清扫不变式例外成立。现状归属：同一缺陷已由 C5-001 以 P2 立项（其触发时序论证更强：key 的 per-key 状态被 `resetNfaStateIfFullyTimedOut` 全清空后引用仍存留），本条为同缺陷重复确证，修复并轨。

**C7-006 — 保留，P2 维持（拆分归属：超时面归并 / 回落面新立）**。live 复核证实两面：① `HttpClient` 仅 `connectTimeout(10s)`，`http.send` 无 request 级 timeout——该面已由 C6-008（P2，已独立复核保留）与 C8-004 双维立项，归并不重复计数；② `readerSchemaFor` 静默吞 `StreamAvroException` 回落 writer schema、失败态不入 `latestBySubject`（重试频率=记录速率、无负缓存无退避），故障窗口内 reader schema 演进读取被静默跳过，与 owner doc「registry 不可达…不静默降级」声明相悖（该声明仅对 encode 侧与解析侧成立）——该面为本维度首次立项，P2 恰当（故障路径可具体化：registry 快速拒连时每记录一次失败调用吞吐塌陷且零 error 信号）。修复时三编号并轨。

**C7-007 — 保留，P3 维持（归并：C5-007 + C6-009）**。live 复核证实两个构成面均为他维已立项缺陷的合并复述：manifest 每 epoch 一键 + 每 commit 全量重写（= C5-007 P2，其论证含 O(E) 每 commit 放大）；`MANIFEST_JVM_LOCKS` computeIfAbsent 无 remove（= C6-009 P3，已独立复核保留，历史登记可溯至 2026-09-26 审计）。本条无独立新增面（H1 channel.lock 无超时的附带观察已被 engine 侧 checkpoint 超时→恢复循环→restart cap 链兜底，维持情报级）。终裁按构成面中较温和口径维持 P3，修复并轨。

**C7-008 — 保留，P3 维持（新锚点，修正触发时序一处过强表述）**。live 复核证实 `schemaByIdCache` 为无上界无淘汰 ConcurrentHashMap；同时证实 `getSchemaById` 走 `computeIfAbsent(id, this::fetchSchemaById)`——fetch 抛出时不记录映射，故原发现触发时序 T3「病理输入 → 一条永久驻留」不成立：伪造 id 投毒只放大 HTTP 调用（归 C7-006 面），不放大驻留。实际增长面 = registry 真实 id 分配速率（schema 演进），KB 级/条，P3 恰当。现状归属：C5 第 1 轮盘点 #25 以「键空间本质可枚举且小」+「owner doc 已知边界」判 OK——复核裁定该判断不成立：id 空间由 registry 单调分配且无界，owner doc 已知边界仅覆盖 encoder 侧 subject 缓存，decoder 侧容量面未声明。本条为该锚点首次立项。

**C7-009 — 保留，P3 维持（归并 C6-012）**。live 复核证实 `effectiveLateDropOperatorTag` 的 identityHashCode 兜底 + open 注册 + close 无注销；生产部署路径（稳定 TaskLocation 标签）语义正确，缺陷面限于无位置实例反复创建形态。现状归属：同一缺陷已由 C6-012 以 P3 立项（C6 第 2 轮 C6-round2，该批条目尚未独立复核），本条为同缺陷重复确证，修复并轨。

**C7-010 — 保留，P2 维持（新发现，全目录唯一）**。live 复核三点证实：① RocksDB 后端 `setCurrentKey`（:443-445）无任何 sweep 挂钩；② `cleanupExpiredEntries` 全仓调用点仅 `snapshotState` 两处（:831/:834）——checkpoint 关闭或长间隔时零触发；③ Memory 后端同机制存在（`maybeSweepExpiredEntries` 挂 setCurrentKey，60s nanotime 门控），设计文档 :420 声明「TTL 清理挂 setCurrentKey 周期 sweep」未限定后端，doc-code 分歧属实。触发时序具体（TTL key 写入后不再回访 + checkpoint 关闭/小时级间隔 → 过期行与 TtlContext sidecar 永久驻留，访问惰性逐出对永不回访 key 无效），P2 恰当。全审计目录（C1–C8 各轮）无重复立项。

**C7-011 — 保留，P3 维持（新发现，全目录唯一）**。live 复核三点证实：① `checkConnection` 以实例裸 prefix id 构造 producer 并执行 `initTransactions`（fencing 原语：epoch bump + abort 该 id 进行中事务）；② 三处生产部署点（GraphExecutionPlan:454 / RemoteGraphExecutionPlanBuilder:313 / SupervisionLoop:847）均走 `deepCopy(TaskLocation)`，运行 id 恒为 `prefix|jobId|vertexId|taskIndex`，与探测 id 异——当前生产路径无实际触发面；③ owner doc 探测红线表六族齐备、唯独缺 kafka-2pc 行（唯一改变 broker 侧事务状态的探测实现）。潜在时序依赖「未来部署路径引入裸身份运行实例」这一未发生前提，不构成现势缺陷；安全前提属未声明的偶然耦合而非已声明边界+守卫，文档契约缺口成立，P3 恰当。

## 最终保留项

| 编号 | 终裁严重度 | 三态 | 现状归属 | 锚点（复核确认） |
|---|---|---|---|---|
| C7-001 | P1 | 保留 | 跨维重复确证：C5-006（P2）同缺陷；本维度判级口径维持 P1，修复并轨 | `JdbcTwoPhaseCommitSink.java` writeLedgerEntry（全模块无台账删除路径） |
| C7-002 | P0（随 C6-001） | 保留 | 跨维重复确证：C6-001 已 P0 立项并通过独立复核终裁 | `KafkaTwoPhaseCommitSink.java`（无 close）+ `TwoPhaseCommitSinkFunction`/`StreamSinkOperator:175` 双钩子不触达 |
| C7-003 | P2 | 保留 | 本维度首次立项；修正 C6 第 1 轮「finally ✓」不完整判定（C6-006 为同方法另一缺陷，不重复） | `RocksDBIncrementalRestore.java:172-175,231-233` + `pruneOldCheckpoints` 仅匹配 cp-N |
| C7-004 | P2 | 保留 | 跨维重复确证：C6-010 已 P2 立项并通过独立复核终裁 | `RocksDBMetricsRecorder.java:73-84` + core `StreamMetricsRegistries` static composite |
| C7-005 | P2 | 保留 | 跨维重复确证：C5-001（P2）同缺陷 | `CepOperator.java:190,1267-1274`（唯一 add 无移除） |
| C7-006 | P2 | 保留 | 拆分：超时面归并 C6-008（P2，已复核）/C8-004；负缓存+静默回落面本维度首次立项 | `AvroSchemaRegistryClient.java:48,113-129` + `AvroWireFormat.readerSchemaFor:110-117` |
| C7-007 | P3 | 保留 | 合并复述：manifest 面 = C5-007（P2）；锁表面 = C6-009（P3，已复核）；无新增面 | `FileTwoPhaseCommitSink.java:96-99,389-404,540-595` |
| C7-008 | P3 | 保留 | 本维度首次立项；修正 C5 盘点 #25「判 OK」与原触发时序投毒驻留过强表述 | `AvroSchemaRegistryClient.java:36,68-70`（无上界无淘汰；fetch 失败不入缓存） |
| C7-009 | P3 | 保留 | 跨维重复确证：C6-012（P3，C6 第 2 轮未复核项）同缺陷 | `CepOperator.java:379-387,438-441`（identityHashCode 兜底 + 无注销） |
| C7-010 | P2 | 保留 | 新发现（全审计目录唯一；doc-code 分歧实证） | `RocksDBKeyedStateBackend.java:443-445,797-836` 对照 Memory 后端 ：186,233-243 + 设计文档 ：420 |
| C7-011 | P3 | 保留 | 新发现（全审计目录唯一；当前生产路径无触发面的文档契约缺口） | `KafkaTwoPhaseCommitSink.java:306-312` + owner doc 探测红线表缺行 + 三处部署点 TaskLocation 限定 |

**现状归属统计**：11 条全部保留（降级 0、驳回 0）。其中跨维度重复确证 6 条（C7-001←C5-006 P2、C7-002←C6-001 P0、C7-004←C6-010 P2、C7-005←C5-001 P2、C7-007←C5-007 P2+C6-009 P3、C7-009←C6-012 P3；另有 C7-006 超时面←C6-008 P2/C8-004）、本维度首次立项新锚点 5 条（C7-003、C7-006 回落面、C7-008、C7-010、C7-011，其中 C7-003/C7-008 分别修正 C6/C5 前轮的两处误判）。live code 全部未修，历史登记条目经复核缺陷形态均在。修复并轨组：{C7-001, C5-006}、{C7-002 → C6-001}、{C7-004 → C6-010}、{C7-005 → C5-001}、{C7-006② + C6-008/C8-004 同点位}、{C7-007 → C5-007 + C6-009}、{C7-009 → C6-012}。

# C7 长时运行稳定性 — 二轮复核结论（独立复核子代理）

- **复核编号**: nop-stream-state-connectors-C7（二轮复核；一轮复核见上文「维度复核结论」节）
- **复核日期**: 2026-10-14
- **复核者**: 独立复核子代理（二轮；与初审者、一轮复核者均非同一角色）
- **复核范围**: 全部 13 条发现——主文件 C7-001~011 + `C7-round2.md` C7-012/013（后者原为「未复核」状态，本轮首次复核）
- **live code 状态**: HEAD 2115b450e6（与一轮复核同点，无新提交）；工作区仅审计产物与 plans 文件改动，涉事 8 模块 src/main/java 零代码改动；涉事文件 git log 末次提交均为特性/历史修复提交（如 JdbcTwoPhaseCommitSink←8b8fb69f25 WI4、KafkaTwoPhaseCommitSink←ae0217fc14 WI3、RocksDBMetricsRecorder←c6dbc769fe plan-2216-1、avro←e7c0f8c774 WI18），**13 条缺陷形态全部未修复**
- **复核方法**: 逐条重读 live code 核对文件/行号/证据与增长链（时间尺度×增长源×后果）；对抗性反驳（增长有无 TTL/清理/裁剪配置、挂死/失明有无既有指标日志可诊断、判级口径比对）；交叉核对本 campaign C1–C8/C10 各轮终裁与 merge-adjudication 裁定表、历史审计（r6 AR 2026-10-06、2026-09-26 dim-04 审计、core-runtime 审计 2026-10-14-0130）summary/结论节与 git log

## 维度复核结论（独立复核子代理）

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C7-001 | P1 | 保留（P1 维持） | 台账仅 INSERT(:852-861)+SELECT(:841-851)、全文件唯一 DELETE FROM(:1120) 为数据表 d 事件、维护工具不触达——每 checkpoint 确定性一行、无任何清理路径，判级口径字面命中 | 已知未修复：历史登记 r6 AR-06（2026-10-06，P3 终裁「新发现·无重复」，一轮复核未引）+ campaign C3-002（P1 主条目，merge 裁定 7）+ C5-006 归并 |
| C7-002 | P1（随 C6-001 P0） | 保留（归并 C6-001，终裁 P0） | 基类无 AutoCloseable(:28)+operator close instanceof 恒 false(:175-177)+本类无 close 三点重验；C6-001 已过独立+二轮复核维持 P0 | 已知未修复（campaign C6-001 P0 终裁，主路径泄漏，随其走 P0 即时修复通道） |
| C7-003 | P2 | 保留（P2 维持） | reconstructRocksdbDir(:177) 先于 try(:183+) 边界、跨 id 残留无清扫（pruneOldCheckpoints 仅 cp-{数字}，strategy:154-168）；C6 配对表两处「✓」（C6:28/:238⑥）确为不完整判定 | 新发现（本维度首次立项；修正 C6 第 1 轮不完整判定，C6-006 为同方法异缺陷不重复） |
| C7-004 | P2 | 保留（归并 C6-010，P2 维持） | 六 gauge Tags.empty()+static composite+close 仅 dbRef.set(null)，Micrometer first-wins 失明链成立；C6-010 已两轮复核维持 P2 | 已知未修复（campaign C6-010 P2 终裁；同族历史 R6 CC-06 为 engine 侧异锚点）；不确定项：未在本环境复现 first-wins（按标准语义+两轮复核一致判定） |
| C7-005 | P2 | 保留（归并 C5-001，P2 维持） | 全文件唯一 add(:1274) 无移除（重验）；merge 裁定 18 并入 C5-001（P2 主条目） | 已知未修复（campaign C5-001 P2；r4 相邻正确性面先例非本缺陷前次登记） |
| C7-006 | P2 | 保留（P2 维持，拆分归属） | 超时面归并 C6-008/C8-004；回落面（readerSchemaFor 静默吞 StreamAvroException+无负缓存，catch 全部异常比 javadoc 自述更宽）违背 owner doc「失败面全 typed…不静默降级」，反驳不成立 | 拆分——超时面已知未修复（C6-008 P2 已二轮复核/C8-004）；负缓存+静默回落面新发现（本维度首次立项） |
| C7-007 | P3 | 保留（P3 维持，归并） | 两构成面均为已立项缺陷合并复述：manifest 面=C5-007（P2）、锁表面=C6-009（P3←2026-09-26 审计 dim-04 同锚点）；无新增面 | 已知未修复（锁表面 2026-09-26 审计 P3 登记；manifest 面 campaign C5-007 P2；修复随账本保留策略族批次） |
| C7-008 | P3 | 保留（P3 维持） | 无上界无淘汰（重验）；fetch 失败不入缓存使投毒驻留面不成立（维持一轮修正），C5 盘点 #25「键空间可枚举且小，OK」不成立（id 空间单调分配无界） | 新发现（本维度首次立项；修正 C5 盘点 #25 判 OK） |
| C7-009 | P3 | 保留（归并 C6-012，P3 维持） | identityHashCode 兜底(:379-383)+注册无注销(:438-441)重验；C6-012 其后自身二轮复核维持 P3，双锚点同缺陷（merge 裁定 19） | 已知未修复（campaign C6-012 P3 终裁；R5-CEP-03 修复 2bb7c013a7 后的残留面） |
| C7-010 | P2 | 保留（P2 维持） | setCurrentKey 无挂钩+清理点仅 checkpoint 路径+Memory 后端对照+设计文档 :420 未限定后端，doc-code 分歧实证；checkpoint 关闭/长间隔为公开旋钮非病理配置，反驳不成立 | 新发现（全审计目录唯一）；复核补充：ops 诊断端点（StreamTaskInvokable:623）为 snapshotState 的额外手动触发面，不改变「无自动周期 sweep」主判定 |
| C7-011 | P3 | 保留（P3 维持） | 探测以裸 id 执行 initTransactions(:306-312)+红线表缺行+三部署点 TaskLocation 限定重验+潜在裸 id 面（copyForSubtask(int) idx≤0 返回 this）真实；当前生产路径无触发面，P3 恰当；C6 探针族「TWR ✓」为资源面与本条正交 | 新发现（全审计目录唯一） |
| C7-012 | P2 | 保留（P2 维持） | 「告警一次」声明（javadoc:26-28+owner doc nop-stream.md:90）无实现守卫（readProperty:91-101 无 once-flag，value function 每 scrape 执行）——文档-代码分歧+无界日志放大成立；全目录唯一（C1-002/C6-004 为同点位 SIGSEGV 异缺陷） | 新发现（round2 立项，本轮首次复核；修正条目内算术：15s scrape≈240 条/小时、5,760 条/天，原文高估 24×，无界放大结论不变）；WARN 分支可达性依赖 RocksDB 构建属性可用性（javadoc 自认 varies by version），分歧面不受影响 |
| C7-013 | P3 | 保留（跨维重复确证：归并 C4-002，终裁随其 **P2**） | eager 构造无 close（重验）；与 **C4-002（P2，已过独立+二轮复核，merge 裁定 20 终裁独立保留）** 同缺陷——round2 盲审边界未覆盖他维文件致重复立项；P3→P2 为归并对齐上修（非降级） | 已知未修复（campaign C4-002 P2 终裁；C4-002 复核证实 selector 线程强引用使 GC 不可达，强于本条「很可能」表述，该不确定项随归并消解） |

**不确定项显式列出**：① C7-004 的 Micrometer first-wins 去重按标准语义+C6-010 两轮复核一致判定，未运行双实例复现（残余风险低）；② C7-012 的 WARN 分支实际触发依赖所用 RocksDB 构建的属性可用性（环境相关；文档-代码分歧面与之无关），且条目内增长率算术高估 24× 已修正；③ C7-010 存在 ops 端点手动触发面（见上表补充），「唯一自动调用点在 checkpoint 路径」表述精确化但主判定不变；④ C7-013 selector 线程存续机制未做泄漏复现——随归并 C4-002 由其复核结论覆盖。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|---|---|---|---|---|
| C7-001 | P1 | `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java` | JDBC 2PC epoch 台账表只增不删（每成功 checkpoint 每 subtask 一行，无删除/TTL/维护工具触达） | 已知未修复（r6 AR-06 P3 2026-10-06 + campaign C3-002 P1 主条目归并） |
| C7-002 | P0（随 C6-001） | `nop-stream/nop-stream-connector-kafka/src/main/java/io/nop/stream/connector/kafka/KafkaTwoPhaseCommitSink.java`（+core `TwoPhaseCommitSinkFunction`:28/`StreamSinkOperator`:175 契约面） | KafkaProducer 在所有任务终止路径永不关闭（正常成功路径即泄漏） | 已知未修复（campaign C6-001 P0 终裁，P0 即时修复通道） |
| C7-003 | P2 | `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalRestore.java:172-233` | 增量恢复临时目录在 reconstructRocksdbDir 抛点/kill 路径残留，跨 id 无任何清扫 | 新发现（首次立项；修正 C6 配对表两处「✓」不完整判定） |
| C7-004 | P2 | `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:65-84` | 六 gauge 空 tags 注册进程级 registry，backend 重建后永久 NaN（first-wins 去重） | 已知未修复（campaign C6-010 P2 终裁归并） |
| C7-005 | P2 | `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:190,1267-1274` | reconciledTimerKeys 每历史 key 一条永不回收（唯一 add 无移除） | 已知未修复（campaign C5-001 P2 归并） |
| C7-006 | P2 | `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java:48,113-129` + `AvroWireFormat.java:111-117` | registry 客户端无请求级超时（归并 C6-008/C8-004）+ 故障期每记录重试无负缓存/退避、readerSchemaFor 静默回落违背「不静默降级」声明 | 拆分：超时面已知未修复；回落面新发现（本维度首次立项） |
| C7-007 | P3 | `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:96-99,389-404,540-595` | manifest 每 epoch 一键全量重写 + MANIFEST_JVM_LOCKS 只增不删（两构成面归并复述） | 已知未修复（锁表面←2026-09-26 审计 P3；manifest 面←campaign C5-007 P2） |
| C7-008 | P3 | `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java:36,68-70` | per-id schema 缓存无上界无淘汰（增速=schema 演进速率） | 新发现（修正 C5 盘点 #25 判 OK） |
| C7-009 | P3 | `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:379-387,438-441` | 降格计数器 identityHashCode 兜底标签使 meter 随实例累积（embedded 形态，close 无注销） | 已知未修复（campaign C6-012 P3 终裁归并；R5-CEP-03 修复残留面） |
| C7-010 | P2 | `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:443-446,797-836` | RocksDB 后端 TTL 清理与 checkpoint 成败耦合：60s 周期 sweep 仅 Memory 后端存在，与设计文档声明相悖 | 新发现（全审计目录唯一；ops 端点为额外手动触发面，主判定不变） |
| C7-011 | P3 | `nop-stream/nop-stream-connector-kafka/src/main/java/io/nop/stream/connector/kafka/KafkaTwoPhaseCommitSink.java:306-312` | checkConnection 以 initTransactions（fencing 原语）作探测，owner doc 红线表缺 kafka-2pc 行；当前生产路径无触发面 | 新发现（全审计目录唯一） |
| C7-012 | P2 | `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:26-28,91-101` | gauge 读数失败 WARN 无一次性守卫按 scrape 频率放大，「告警一次」声明未实现 | 新发现（round2 立项，全审计目录唯一） |
| C7-013 | P2（随 C4-002，自 P3 对齐上修） | `nop-stream/nop-stream-format-avro/src/main/java/io/nop/stream/format/avro/AvroSchemaRegistryClient.java:34,43-49`（+`AvroWireFormat.java`） | JDK HttpClient 构造即建永不 close，每任务实例泄漏 selector 线程 | 已知未修复（campaign C4-002 P2 终裁归并；round2 盲审边界致重复立项） |

**二轮统计**：保留 13 / 降级 0 / 驳回 0（13/13 条经对抗性反驳后证据链全部成立）。判级变动 1 条：C7-013 P3→P2（归并 C4-002 的对齐上修，非降级）。其中跨维重复确证（修复并轨）8 条——C7-001→C3-002（P1 主条目）、C7-002→C6-001（P0）、C7-004→C6-010、C7-005→C5-001、C7-006①→C6-008/C8-004、C7-007→C5-007+C6-009、C7-009→C6-012、C7-013→C4-002；本维度首次立项新锚点 6 条——C7-003、C7-006②、C7-008、C7-010、C7-011、C7-012。最终判级分布：P0×1（并轨）、P1×1、P2×7、P3×4。现状标注对一轮复核的两处补正：C7-001 历史登记补引 r6 AR-06（2026-10-06，一轮仅引 C5-006）；C7-013 为 round2 重复立项（归并 C4-002）。

**需子项复核的高风险项**：无新增。C7-002 随 C6-001 已终裁进入 P0 即时修复通道；C7-001 随 C3-002（P1 主条目，merge 裁定 7）进入修复批次；其余 P2/P3 均为常规整改面，修复时按上文并轨组归并，不重复开工。
