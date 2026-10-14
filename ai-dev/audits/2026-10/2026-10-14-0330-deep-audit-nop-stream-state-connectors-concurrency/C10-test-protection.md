# C10 测试保护力 — nop-stream-state-connectors（并发正确性回归会不会红）

## 0. 检查范围与方法

- **审计对象**: nop-stream-state-connectors 单元 8 个模块的 `src/test`——`nop-stream-cep`、`nop-stream-rocksdb`、`nop-stream-connector`、`nop-stream-connector-batch`、`nop-stream-connector-jdbc`、`nop-stream-connector-debezium`、`nop-stream-connector-kafka`、`nop-stream-format-avro`（仓库根 `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`）。
- **审计问题**: 不是"有没有测试"，而是**回归会不会红**——对 Phase 4 复核后的 P0×1 + P1×16（跨维度重复归并后 15 行）逐一做变异推演："补上正确实现后，现有哪个测试会红？"无测试会红的按缺陷族登记「缺保护」（P2，与缺陷本体分开计数）。
- **方法**: (1) 盘点 8 模块全部 150 个测试 java 文件（143 个 `Test*` + 7 个测试支撑类）的并发/资源/规模覆盖面，live 计数并发原语使用（`new Thread|ExecutorService|CyclicBarrier|CountDownLatch|AtomicBoolean|CompletableFuture`）；(2) 精读与缺陷锚点相邻的 12 个关键测试文件全文，其余按锚点符号 grep 定向核对（全部「零命中」声明均经 live grep 复核）；(3) 变异推演三态判定——**会红**（现有测试钉死缺陷语义，修复必须同步反转测试）/ **假保护**（相邻测试覆盖功能面但断言与缺陷正交，修复或回归均不改变测试结果）/ **无测试**（无任何测试触及缺陷行为面）；凡"无测试会红"的登记缺保护。(4) 时序敏感测试审查（Thread.sleep 同步、未等待异步结果、测试自吞竞争）；(5) 资源泄漏钩子盘点；(6) 大规模/边界包络测试盘点（小规模参数化口径）。
- **输入**: 同目录 C1–C8 八个维度文件的「最终保留项」节（Phase 4 复核后清单，含二轮复核终裁级）；盲审纪律下未读 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`。
- **口径说明（与任务简报的一处对齐）**: 简报中 C2-003 标注为 "FileSplitEnumerator"，经核对权威维度文件 `C2-locks-sync.md` 最终保留项（二轮复核后），C2-003 的缺陷实体是 **DebeziumTableSplitEnumerator**（裸 LinkedHashMap，`DebeziumTableSplitEnumerator.java:47-135`）；`FileSplitEnumerator` 恰是该发现中逐点核实过的**安全对照**（CHM + 确定性分片，C2 文件失效面枚举明示"唯一缺陷实例"）。本文件按维度文件口径执行。
- **判定性质**: 静态推演（断言检视 + live grep），未实际执行测试或变异注入；docker 门控集成测试（`TestDebeziumReal*`）只盘点不执行。

---

## 1. 既有并发测试盘点（8 模块测试清单 × 保护范围）

### 1.1 模块 × 测试规模（live 计数）

| 模块 | 测试 java 文件 | 其中 Test* 类 | 并发原语使用文件 | 真正的多线程测试 |
|---|---|---|---|---|
| nop-stream-cep | 66（62 Test* + 4 helper） | 62 | 2（TestLockable、TestCepOperatorConditionLifecycle¹） | **1**：`TestLockable#testConcurrentReleaseDoesNotCauseNegativeRefCount`（20 线程并发 release 共享 `Lockable`，断言 refCounter 恒 ≥0） |
| nop-stream-rocksdb | 28 | 28 | **0** | 0（全模块零 `new Thread/ExecutorService/CountDownLatch`，live grep 复核） |
| nop-stream-connector | 18（17 + TestAwait helper） | 17 | 9 | 4：ThreadSafety / Lifecycle / Restart / ConnectorResourceManagement（+file 包 2PC 并行提交 2 项） |
| nop-stream-connector-batch | 10（9 + TestAwait helper） | 9 | 1 | 1：`TestBatchLoaderSourceFunction`（runner 线程 + gate 取消） |
| nop-stream-connector-jdbc | 9 | 9 | 1 | 1：`TestJdbcConcurrencyAndBatchSegments#concurrentFirstInitializationIsSafe`（CyclicBarrier 6 线程并发 beginTransaction，Fix-10 面） |
| nop-stream-connector-debezium | 16（15 + TestAwait helper） | 15 | 10 | 4：CdcSourceFunction 重入/重启、ResourceManagement 重入守卫、CdcCheckpoint kill/recover（+Real* docker 门控 3 项） |
| nop-stream-connector-kafka | 2 | 2 | 0 | 0 |
| nop-stream-format-avro | 1 | 1 | 0 | 0 |
| **合计** | **150** | **143** | **23** | **约 12** |

¹ `TestCepOperatorConditionLifecycle` 仅用 `AtomicBoolean` 做单线程观测旗标，非并发测试。

### 1.2 关键测试的保护范围（与缺陷锚点相邻者）

- **connector**: `TestMessageSourceFunctionThreadSafety`（10 线程 × 100 消息，断言 `maxConcurrent<=1`——collect 不并发；保护 **legacy `MessageSourceFunction`**，非 FLIP-27 reader）；`TestMessageSourceFunctionLifecycle`（`RecordingSubscription.cancelCount==1` 泄漏断言，:141/:177——**本单元最好的资源释放断言范本**）；`TestMessageSourceFunctionRestart`、`TestConnectorResourceManagement`（`awaitUntil` 30s deadline 正例）、`TestPreSubmitConnectivityProbe`。
- **file**: `TestFileTwoPhaseCommitSink`（555 行）：epoch 提交/abort 删 tmp（:254）、kill/recover 无 `*.tmp` 残留（:333）、并行 subtask 隔离（:351）、**AR-14 manifest 读-改-写互斥**（`testParallelSubtaskCommitsInterleaveWithoutManifestLoss:412`，单 JVM 内 2 线程 × 25 epoch）；`TestFileSourceCheckpointRestore`/`Recovery`/`AuditFixes`/`Boundaries`/`RateLimitAndSkew`。
- **batch**: `TestBatchLoaderSourceFunction`（取消/重启语义，含**无界自旋等待**——见 §4 与 C10-016）；`TestBatchLoaderParallelismGate`（parallelism>1 fail-fast 边界 + `testCurrentOffsetIsVolatile`）；`TestBatchConsumerSinkFunction{,CloseLogging,Failure}`。
- **jdbc**: `TestJdbcConcurrencyAndBatchSegments`（并发首初始化 + `countLedgerRows:159` 台账**存在性**断言 + `largeEpochIsCommittedInSegments:145` 提交分段）；`TestJdbcTwoPhaseCommitSink{Deep,Dml,InsertNoDebezium,ParallelIsolation,Skeleton}`、`TestJdbcTwoPhaseCommitLedgerNamespace`——台账**写入/幂等/命名空间**面，**无保留/清理面**。
- **debezium**: `TestDebeziumCdcCheckpoint`（630 行：offset store 快照/恢复 round-trip、kill/recover 无重复——全在 **legacy SourceFunction** 路径，5 处 `while(...<deadline) sleep(50)` 有界轮询；:288-291 **显式把快照移到 cancel 之后**，见 §4）；`TestDebeziumCdcSource`（269 行 FLIP-27 reader 装配：序列化 round-trip、枚举器首分配、`StubbedReader:228-268` **覆写 `createEmbeddedFunction`——真实 DebeziumEngineWrapper 从不启动**、lift-all offset 快照断言 :166-174）；`TestDebeziumCdcSourceFunction`（runEntered CAS 双进入 :308、cancel 后重启 :352、finally 清理 :274）；`TestDebeziumResourceManagement`（重入守卫，catch-all 吞差异——见 §4）；`TestDebeziumCdcSourceErrorPropagation`（R5-CON-02/03 失败回调**投递**面）；`TestDebeziumCdcSourceCompletion`、`OffsetSeed`、`Sharding`、`PreSubmitConnectivityProbe`；`TestDebeziumReal{MysqlCdc,MysqlParallel,PostgresCdc}` 为 docker `assumeTrue` 门控（SKIP 可见，不伪造通过）。
- **kafka**: `TestKafkaTwoPhaseCommitSinkLifecycle`（213 行，模块仅有的 sink 测试）：FakeProducer 记录 `init/begin/commit/abort/flush/send` 调用序列，覆盖 barrier 提交、recover fencing、事务 id 命名空间、`transactional.id` 覆写守卫；**全文无 close/teardown 词汇，FakeProducer 未覆写 `close()`**（类名含 "Lifecycle" 但只覆盖 epoch 生命周期）；`TestKafkaSourceFunctionSharding`（纯分片函数/守卫，无 consumer close 面）。
- **format-avro**: `TestAvroWireFormat`（274 行，模块**唯一**测试文件）：嵌入 JDK HttpServer 快速响应，register→encode→decode round-trip、演进、缓存省调用、typed 错误（`registryUnreachableFailsTyped:239` 用 `127.0.0.1:1` **快速拒连**）——全部快速成功路径。
- **cep**: 66 个文件中 operator 目录**零 `Thread/ExecutorService`**（live grep 复核）；`TestCepOperatorSubtaskCopy`（`assertSame(userFunction)` 实例同一性）；`TestCepOperatorTimeout`（单 operator 单 subtask 超时路径）；`TestCepCheckpointRestoreE2E`/`StateRecovery`（NFA/SharedBuffer/Timer/ElementQueue 快照恢复）；`TestCepOperatorLedgerDrain`/`DanglingCleanup`（ledger drain 与悬垂清理语义）；sharedbuffer 族含唯一真并发测试 `TestLockable`（并发 release 引用计数不减为负）。
- **rocksdb**: `TestRocksDBSnapshotRestore`（729 行 22 用例，小规模 round-trip）；`TestRocksDBStateTtl`（FakeClock + **显式调用 `cleanupExpiredEntries()`**:169）；`TestRocksDBIncrementalLifecycleAndIntegrity`（**有界性钩子存在**：`taskLocalCheckpointDirsStayBoundedAfterManySnapshots:108` cp-{N} 滚动保留、`segmentStoreNeverLeavesTempOrPartialFilesAtFinalName:286`、损坏 typed fail-fast ×3）；`TestRocksDBIncremental{RestoreFailFast,RangeRestore,SnapshotStrategy,BackendWiring}`；`TestRocksDBMemoryControl`；`metrics/TestRocksDBMetricsRecorder`（非空读数、6 gauge 注册、**close 后 NaN**——全单线程）。

### 1.3 测试基建评价

- `testsupport/TestAwait`（connector/batch/debezium 三份拷贝）：deadline + 确定性条件轮询、`untilThreadParked/untilThreadSettles`——**良好范式**；debezium/batch 重启测试系统性使用它，但 batch 的 `TestBatchLoaderSourceFunction:250` 存在未用它的一处无界自旋（C10-016）。
- 缺失的钩子形态：producer/consumer close 断言、线程/句柄泄漏断言、注册表清空断言（`MANIFEST_JVM_LOCKS`/`FAILURE_LISTENERS`/`StreamMetricsRegistries` meter 摘除）、挂死注入（慢/挂起 HTTP 与 JDBC 对端）、池耗尽/队列满/缓存上限边界参数化。

---

## 2. 保护力矩阵（P0/P1 缺陷 × 现有测试 × 变异结论）

判定：**会红** = 修复落地现有测试会失败（钉死缺陷语义，需随修复反转）；**假保护** = 相邻测试覆盖功能面但与缺陷正交；**无测试** = 无测试触及行为面。后两者（无测试会红）登记缺保护（§3）。

| # | 缺陷 ID（终裁级） | 现有最相邻测试 | 变异结论（补上正确实现后会红吗） | 缺保护登记 |
|---|---|---|---|---|
| 1 | C6-001（**P0**，=C3-001/C4-001/C7-002） | `TestKafkaTwoPhaseCommitSinkLifecycle`（FakeProducer 记录调用序列，未覆写 `close()`；全文无 teardown 断言） | **无测试**——producer.close() 加入任何终态路径（DRAIN/CANCEL/FAILED）均无断言会红；删除 close 的回归同样零报警 | C10-001 |
| 2 | C1-001（P1，已知未修复 r4 R5-CEP-05） | `TestCepOperatorSubtaskCopy`（唯一相邻测试） | **条件会红（仅 udf 面）+ NFA 面无测试**——该测试对 NFA states 图/条件对象零断言；`nfaFactory` round-trip 修复单独落地时 `assertSame(userFunction)` 仍绿。仅当与 C1-006 合并修复（一并深拷贝 getUserFunction）时才经 ：43 反转。共享 NFA 条件图的并发变异面独立看属无测试 | C10-003 |
| 3 | C1-006（P1，=r6 CON-03 族） | `TestCepOperatorSubtaskCopy.java:43-44` `assertSame(operator.getUserFunction(), copy.getUserFunction(), "...must be shared across subtasks")` | **会红（钉死缺陷）**——逐字钉死共享 udf 设计；`copyForSubtask` 对 udf round-trip 深拷贝的修复落地后该断言必失败。修复批次必须同步反转此断言 | 不登记（矩阵注记） |
| 4 | C1-002 = C2-001 = C6-004（P1 同实例族） | `TestRocksDBMetricsRecorder#testCloseDetachesGaugesFromNativeHandle:113-130` | **假保护**——完全单线程顺序断言（活句柄实值 → recorder.close() → NaN）；且用独立 recorder（`backend.getDbForTest()`），不测 backend 自身 close 顺序。把 `ref.get()` 移入 synchronized 的 3 行修复后照常绿；rocksdb 全模块测试零并发原语（live grep 复核） | C10-002 |
| 5 | C1-005（P1） | `TestCepOperatorTimeout#testTimeoutWithProcessingTime:95`（单 operator 单 subtask） | **无测试**——cep operator 测试目录零 `Thread/ExecutorService`（live grep 复核）；跨 subtask ctx 槽位错路由（requires ≥2 共享 adapter 的 operator 并发超时）在测试目录中构造不存在 | C10-004 |
| 6 | C2-003（P1，受害者侧，挂靠 WI1.1-C1-001） | （测试目录 grep `DebeziumTableSplitEnumerator` **零命中**，live 复核） | **无测试**——`TestDebeziumCdcSource#enumeratorDeliversWholeRoundRobinSharesAtRegistration:108` 只单线程顺序 `addReader(0)→addReader(1)→snapshotState`；并发注册 × 持锁迭代的 CME 面不可达 | C10-005 |
| 7 | C2-010（P1，受害者侧，挂靠 WI1.1-C3-009） | `TestRocksDBStateTtl`（323 行，单线程 + FakeClock + 显式 sweep 调用） | **无测试**——rocksdb 测试全模块零并发原语；HTTP 线程 × 任务线程跨线程 `snapshotState`/TTL sidecar 三点竞态全部不可达 | C10-006 |
| 8 | C3-002 = C5-006 = C7-001 = C8-007（P1 四向族） | `TestJdbcConcurrencyAndBatchSegments#countLedgerRows:159`、`TestJdbcTwoPhaseCommitLedgerNamespace` | **假保护**——台账**写入存在性**（`assertEquals(1, countLedgerRows(7L))`:156）与幂等/命名空间有断言；**保留/清理面零断言**（jdbc+connector 测试目录 grep `prune|retention` 零命中，live 复核）。补 retention 修复后全部用例照常绿 | C10-007 |
| 9 | C3-003 = C2-002 = C4-003 = C6-008 = C8-004（P1 超时面五向族） | `TestAvroWireFormat`（274 行，模块唯一测试） | **假保护**——全部快速成功路径；`registryUnreachableFailsTyped:239` 用 `127.0.0.1:1` 快速拒连而非挂死注入；无慢响应/挂起服务器形态，无多线程 encode 用例。补 `.timeout()` 后全部用例照常绿（挂死注入下现有测试反而会永久挂起——无超时保护本身就是测试盲区） | C10-008 |
| 10 | C3-004（P1） | `TestDebeziumCdcSource#enumeratorStateSerializerRoundTripsUnassignedTables:96`（仅序列化）；`restoredOffsetsReplayIntoTheEmbeddedStateContract:180` 是 **reader 侧** restore | **无测试**——枚举器 `restoreState → start()` 的恢复重分配（`i%parallelism` 重算追加、不清不去重、双登记面）零测试；对照 File 族有 `TestFileSourceCheckpointRestore`，Debezium 枚举器恢复零覆盖 | C10-009 |
| 11 | C3-009 = C4-004（P1 归并族） | （8 模块测试目录 grep `GlobalExecutors|globalWorker` **零命中**，live 复核） | **无测试**——`StubbedReader` 覆写引擎宿主后连真实提交路径都不可达；池归属/饱和静默死流面（F5）零覆盖 | C10-010 |
| 12 | C3-014 = C4-005/C4-006 停止契约族（P1） | `TestDebeziumCdcSource` 的 `StubbedReader:241-267` 覆写 `createEmbeddedFunction` | **无测试**——真实 `DebeziumEngineWrapper` 的 start/stop/close/MBean 从不在测试中运行；stop 终结性（join 有界 + 线程终态 + 同名重注册）零断言；第三方 ISE 契约零钉住 | C10-011 |
| 13 | C5-002 = C8-005（P1 归并族） | `TestRocksDBSnapshotRestore`（729 行 22 用例，全 ≤数条记录 round-trip） | **假保护**——全量物化行为被 round-trip 功能面依赖但规模零断言；`TestRocksDBIncrementalLifecycleAndIntegrity` 覆盖增量 segment 面（但增量缺省关，非缺陷路径）。流式化修复后全部 round-trip 照常绿 | C10-012 |
| 14 | C8-001（P1） | `TestDebeziumCdcSource#readerScopes...LiftsOffsetsIntoSplitSnapshots:166-174`；`TestDebeziumCdcCheckpoint:288-291` | **假保护 + 测试自吞竞争**——前者断言"offset 无条件抬入**每个** split 快照"（M-2 语义，恰为缺陷载体）；后者**显式把快照移到 kill 之后**（注释自认"Snapshotting before cancel races with the emitter thread"），把偏斜窗口整个排除在断言外。last-emitted 回退修复后照常绿 | C10-013 |
| 15 | C8-006（P1） | （kafka 测试目录 grep `transaction.timeout` **零命中**，live 复核） | **无测试**——lifecycle 测试仅断言调用序列与 `transactional.id` 覆写守卫（:203-212）；producer 属性面/超时耦合校验零断言 | C10-014 |

**矩阵计数**: 15 行 = 会红（钉死缺陷）1（C1-006）+ 条件会红 1（C1-001，随 C1-006 合并修复反转）+ 假保护 5（行 4/8/9/13/14）+ 无测试 8（行 1/5/6/7/10/11/12/15）。覆盖 P0×1、P1 输入清单 16 ID（归并后 15 行）全部推演完毕。

**P2 侧记（简报授权的"一并登记"）**: 复审中发现 1 条 P2 命中假保护形态——**C3-011**（Debezium run 重入守卫 CAS 失败静默 return）：`TestDebeziumResourceManagement` 的 catch-all 把"静默 return"与"抛错"折叠为同一断言结果（登记 C10-015）；另 1 条测试自身缺陷命中时序敏感审查（登记 C10-016）。其余 P2/P3 未逐条推演（简报口径），其中与 §1.2 盘点重合的旁证：C2-005（file 锁等待上界）、C2-011（fresh registry 回避碰撞）、C7-003（bounded 钩子不覆盖 restore-range 残留）、C7-010（测试手动调 sweep 钉住零自动触发）、C8-002（缓冲仅持久化面）在盘点中已见同类假保护形态，修复排批时应同步配测试。

---

## 3. 缺保护登记发现条目

### [nop-stream-state-connectors-C10-001] 缺保护：C6-001（P0）kafka-2pc producer 全路径无 close——零断言

- **关联缺陷**: C6-001（P0 终裁维持；跨维重复 C3-001/C4-001/C7-002 归并）——`KafkaTwoPhaseCommitSink` 的 KafkaProducer 在所有任务终止路径永不关闭，正常成功路径（DRAIN）即确定性泄漏（每作业每子任务 1 daemon network 线程 + broker 连接 + 缓冲池）。
- **文件:行号**: `nop-stream/nop-stream-connector-kafka/src/test/java/io/nop/stream/connector/kafka/TestKafkaTwoPhaseCommitSinkLifecycle.java:41-85`（FakeProducer）、:87-95（newSink 覆写 `newProducer()`）、:115（唯一调用序列断言）。
- **证据片段**:
  ```java
  // FakeProducer 只覆写记录这 6 个方法（:49-84）：
  public void initTransactions() { calls.add("init"); ... }
  public void beginTransaction() { calls.add("begin"); ... }
  public void commitTransaction() { calls.add("commit"); ... }
  public void abortTransaction() { calls.add("abort"); ... }
  public void flush() { calls.add("flush"); ... }
  public Future<RecordMetadata> send(...) { calls.add("send:" + record.key()); ... }
  // —— 没有 close() 覆写；全文 213 行无 close/teardown 词汇
  ```
- **严重程度**: P2（缺保护本体；缺陷本体 P0）
- **并发场景**: 资源生命周期——不依赖并发；但泄漏主体（daemon 线程 + TCP 连接）在长驻 TM 进程内随启停线性累积。
- **触发时序（变异场景）**: 为 sink 补上 AutoCloseable + close 传播（复用 `StreamSinkOperator.close:172-178` 的 `instanceof AutoCloseable` 通道）后，现有 6 个用例（barrier 提交序列、typed 越界、recover fencing、restore 回放、命名空间、transactional.id 守卫）**全部照常通过**——修复的正确性零验证；反向回归（有人删掉 close 或从未合入）也零报警。FakeProducer 连 close 调用都记录不到。
- **现状**: kafka 模块仅有的 sink 测试类名含 "Lifecycle"，实际只覆盖 epoch 生命周期；生产者生命周期零保护。
- **风险**: P0 修复批次若遗漏测试配套，close 语义可被后续重构无声移除；泄漏回归不可观测。
- **建议（测试形态）**: `TestKafkaTwoPhaseCommitSinkLifecycle` 增补——FakeProducer 覆写 `close()` 记录调用；新增用例 (a) 断言 sink 关闭链路传播 close（经 `StreamSinkOperator.close` 或任务 teardown 链），(b) close 恰好一次且幂等（二次 close 安全），(c) recover/abort 路径不误关 producer（后续 epoch 仍可 begin/commit）。先红后绿：对缺陷本体（现状无 close）用例 (a) 应红。
- **失效面枚举**: `TestKafkaTwoPhaseCommitSinkLifecycle` 全部 6 用例均不触达 close；`TestKafkaSourceFunctionSharding` 无 consumer close 断言；format-avro 侧 HttpClient close 面同形态缺口（P2 C4-002，见 §1.3）。
- **信心水平**: 确定（live 全文精读 + grep 复核）
- **误报排除**: 不属"测试存在即有保护"——调用序列断言 `assertEquals(List.of("init","begin",...), producer.calls)` 的**精确序列匹配**意味着若修复在终态路径插入 close，只要测试不同步扩展期望序列，测试本身会因 calls 列表增长而红——但这要求修复者**主动改测试**，现有断言对"缺 close"方向零敏感；判定不变。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：FakeProducer（:41-85）仅覆写 init/begin/commit/abort/flush/send 六方法、全文 grep close|teardown 零命中、:115 精确序列断言对「缺 close」方向零敏感均经核读/复跑证实；见文末「维度复核结论」

### [nop-stream-state-connectors-C10-002] 缺保护：C1-002/C2-001/C6-004（P1 同实例族）gauge 锁外解引用 dbRef 的 JNI UAF 竞态——仅顺序 detach 面

- **关联缺陷**: C1-002（=C2-001/C6-004）——gauge 闭包 `ref -> readProperty(ref.get(), property)` 在 synchronized 之外求值，与 backend close 构成 native use-after-free 窗口（SIGSEGV）。
- **文件:行号**: `nop-stream/nop-stream-rocksdb/src/test/java/io/nop/stream/rocksdb/metrics/TestRocksDBMetricsRecorder.java:113-130`。
- **证据片段**:
  ```java
  // :121-129 —— 完全单线程顺序执行
  RocksDBMetricsRecorder recorder = new RocksDBMetricsRecorder(backend.getDbForTest());
  recorder.register(registry);
  assertFalse(Double.isNaN(registry.get(...METRIC_BLOCK_CACHE_USAGE).gauge().value()), ...);
  recorder.close();                                    // 顺序 close
  double afterClose = registry.get(...).gauge().value();
  assertTrue(Double.isNaN(afterClose), "detached gauge reports NaN, ...");
  ```
- **严重程度**: P2
- **并发场景**: ops/metrics scrape 线程读取 gauge 值 × 任务线程 close backend 的窗口交叠。
- **触发时序（变异场景）**: 把 `ref.get()` 移入 `synchronized readProperty` 之内（3 行修复）后，该测试照常通过——它构造的是独立 recorder 且从不并发 scrape；同样，"回归回锁外取参"测试也照常通过——测试无法区分正确/缺陷实现。rocksdb 测试全模块零 `new Thread/ExecutorService/CountDownLatch`（live grep 复核），并发 scrape × close 交错完全不可达。
- **现状**: 唯一贴近缺陷的测试只验证"close 后 gauge 报 NaN"的顺序 detach 语义（该语义本身是修复成果，但保护的是 detach 而非取参窗口）。
- **风险**: SIGSEGV 级缺陷的修复无回归防护；且 `TestRocksDBStateTtl` 等全部 rocksdb 测试同形态（单线程），同族面整体裸奔。
- **建议（测试形态）**: 双线程压窗测试 `TestRocksDBMetricsRecorderConcurrency`——线程 A 循环 scrape gauge（`Gauge.value()`），线程 B 循环 open/close backend，`TestAwait.staysTrue` 断言窗口内 gauge 读数永不抛 `IllegalStateException`（nativeHandle_==0；SIGSEGV 面只能靠 JVM 存活间接断言）；另补白盒守卫测试断言 `ref.get()` 调用点位于锁内（将取参提取为可测的包级方法，断言其被 synchronized 调用）。
- **失效面枚举**: `TestRocksDBMetricsRecorder` 4 用例全部单线程；`testBackendOpenPathRegistersIntoProcessComposite:97-111` 用进程 composite 的唯一用例在 finally `backend.close()` 后不复查 meter 摘除（与 P2 C2-011 的 rebind 面共缺口）。
- **信心水平**: 确定
- **误报排除**: 不属"NaN 断言已覆盖该缺陷"——NaN 断言验证的是 close **之后**的读数，缺陷窗口是 close **期间**的读数，两者正交。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：testCloseDetachesGaugesFromNativeHandle（:113-130）单线程顺序断言 + 独立 recorder（backend.getDbForTest()）核读证实，4 用例全单线程；rocksdb 全模块测试 grep 并发原语零命中独立复跑证实；见文末「维度复核结论」

### [nop-stream-state-connectors-C10-003] 缺保护：C1-001（P1）共享 NFA 条件图 runtimeContext 错配——NFA 面无测试，唯一相邻测试仅条件性会红

- **关联缺陷**: C1-001（已知未修复，r4 R5-CEP-05）——并行 CEP 各 subtask 经引用拷贝共享 NFA 编译态 State 图，Rich 条件对象被每 subtask 的 `NFA.open()/close()` 原地变异，per-operator keyedStateStore last-writer-wins。
- **文件:行号**: `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepOperatorSubtaskCopy.java:40-44`。
- **证据片段**:
  ```java
  CepOperator<Event, Integer, String> copy = operator.copyForSubtask();
  assertNotSame(operator, copy, "CepOperator copy must be a fresh instance");
  assertSame(operator.getUserFunction(), copy.getUserFunction(),
          "CepOperator user function must be shared across subtasks");   // :43-44
  // —— 对 nfaFactory/NFA states/条件对象 runtimeContext 零断言
  ```
- **严重程度**: P2
- **并发场景**: parallelism ≥2 时 N 个 subtask 的 `NFA.open()` 各自把 `RichIterativeCondition.runtimeContext`（非 volatile）写为自身 per-operator keyedStateStore——last-writer-wins。
- **触发时序（变异场景）**: 最小修复（`nfaFactory` 序列化 round-trip 深拷贝）**不动 userFunction**，单独落地时 `assertSame(userFunction)` 仍绿——该测试钉死的是 C1-006 的 udf 共享面，不是 C1-001 的 NFA 面。NFA states 图的共享/变异行为在 66 个 cep 测试文件中无任何断言；`TestLockable` 的并发测试只覆盖 sharedbuffer `Lockable` 原语，与条件图无关。
- **现状**: 唯一相邻测试 `TestCepOperatorSubtaskCopy` 仅断言实例/udf 同一性；仅当 C1-001 与 C1-006 合并为同一修复（一并 round-trip `getUserFunction()`）时测试才反转。
- **风险**: NFA 面修复（含回归回退）无任何测试信号；`assertSame` 的存在反而可能误导修复者以为共享面"有测试钉着、是刻意的"。
- **建议（测试形态）**: `TestCepOperatorSubtaskSharedNfaState`——用 `copyForSubtask` 构造 2 个共享 factory 的 operator（或直接断言工厂产物），(a) 若修复为 round-trip：断言两 operator 的 NFA states 图实例不同（`assertNotSame`，对现状红）；(b) 带 Rich 条件的 pattern：两 subtask 分别 open 后，断言条件对象读到的 runtimeContext/keyedStateStore 是**本 subtask** 的（注入可区分的 keyedStateStore 探针）。
- **失效面枚举**: cep operator 目录零 `Thread/ExecutorService`（live grep 复核）；全部 62 个 cep 测试对 NFA states 图共享性零断言；`TestCepOperatorConditionLifecycle` 仅单线程 AtomicBoolean 旗标。
- **信心水平**: 确定
- **误报排除**: 不属"assertSame 已是保护"——它保护的是**共享设计的现状**（C1-006 载体），方向恰好相反；对 C1-001 的 NFA 面它是盲的。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：TestCepOperatorSubtaskCopy 全文仅 assertNotSame(operator)+assertSame(userFunction) 两断言、NFA/条件对象零断言核读证实；cep 测试仅 2 文件触 copyForSubtask、唯一身份断言即 :43-44（grep 证实）；「条件会红」推演成立（udf 与 nfaFactory 独立构造，:35-38）；见文末「维度复核结论」

### [nop-stream-state-connectors-C10-004] 缺保护：C1-005（P1）共享 adapter 的 SideCollector ctx 槽位跨 subtask 错路由——并行形态构造不存在

- **关联缺陷**: C1-005——共享 adapter 的 `sideCollector` 槽位被全部 subtask 复用，超时匹配记录可经别的 subtask 的 Context 发出（错路由 + 错时间戳）。
- **文件:行号**: `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepOperatorTimeout.java:95`（`testTimeoutWithProcessingTime`，单 operator 单 subtask）。
- **证据片段**: cep operator 测试目录 `grep -rl "new Thread\|ExecutorService"` **零命中**（live 复核）；`TestCepOperatorTimeout` 用共享的 `MockRuntimeContext` 单实例走超时路径，断言超时记录到达自身 ctx。
- **严重程度**: P2
- **并发场景**: ≥2 个共享同一 adapter 的 operator 实例（`copyForSubtask` 引用透传 sideCollector）的 `processTimedOutSequences` 在各自任务线程并发执行，`setCtx→用户回调→collect` 槽位无同步收敛。
- **触发时序（变异场景）**: 修复（per-subtask ctx 槽位或 per-copy adapter）后，单 subtask 超时测试全部照常通过——单实例上"槽位复用"与"槽位独占"行为不可区分；跨 subtask 串号的回归同样无测试可红。缺陷 requires 的双 subtask 并发超时构造在测试目录中不存在。
- **现状**: 超时发射面（`processTimedOutSequences` → SideCollector → ctx）仅有单 subtask 形态测试。
- **风险**: 错路由是静默数据面缺陷（记录发给别人的 collector、盖别人的时间戳），无测试则修复与回归都不可见。
- **建议（测试形态）**: `TestCepOperatorTimeoutCrossSubtask`——`copyForSubtask` 构造 2 个共享 adapter 的 operator，各自绑定**记录型 ctx**（记录发射者身份/时间戳/负载）；双线程（CountDownLatch 收敛）注入使同一 pattern 的超时窗口在两 subtask 交叠；断言每条超时记录的发射 ctx 身份 == 触发 subtask 身份、时间戳 == 该 subtask 时钟（对缺陷本体应红）。
- **失效面枚举**: `TestCepOperatorTimeout`、`TestCepOperatorBasic`、`TestCepSkipStrategyE2E` 等全部 operator 级测试单实例；E2E 测试（`TestCepProductionExecutionE2E`）亦单线程。
- **信心水平**: 确定
- **误报排除**: 不属"超时语义已有测试"——现有断言验证单 subtask 超时**是否发生**，不验证超时记录**从谁的 ctx 发出**。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：cep operator 测试目录 grep new Thread|ExecutorService 零命中独立复跑证实，跨 subtask 并发超时构造不存在；单 subtask 下槽位复用/独占不可区分推演成立；见文末「维度复核结论」

### [nop-stream-state-connectors-C10-005] 缺保护：C2-003（P1）DebeziumTableSplitEnumerator 裸 LinkedHashMap 并发读写——类级零测试

- **关联缺陷**: C2-003（受害者侧登记，挂靠 WI1.1-C1-001）——`shareBySubtask` 裸 LinkedHashMap，`registerReader→addReader`（ enumeratorLock 外、各 subtask 任务线程）与持锁 `snapshotState` 迭代并发（CME/撕裂快照/份额丢失）。
- **文件:行号**: 测试目录 grep `DebeziumTableSplitEnumerator` **零命中**（live 复核）；最相邻间接测试 `nop-stream/nop-stream-connector-debezium/src/test/java/io/nop/stream/connector/debezium/TestDebeziumCdcSource.java:108-125`。
- **证据片段**:
  ```java
  // TestDebeziumCdcSource#enumeratorDeliversWholeRoundRobinSharesAtRegistration —— 单线程顺序：
  enumerator.start(context(2, deliveries));
  enumerator.addReader(0);
  enumerator.addReader(1);          // 顺序注册，无并发
  assertEquals(2, deliveries.size());
  DebeziumCdcEnumeratorState state = enumerator.snapshotState(1L);  // 与 addReader 无交叠
  ```
- **严重程度**: P2
- **并发场景**: parallelism>1 的 CDC 作业启动期，N 个 subtask 任务线程并发 `addReader(i)→shareBySubtask.remove(i)` × checkpoint 线程持 enumeratorLock 迭代 `snapshotState`。
- **触发时序（变异场景）**: 修复（① CHM 化受害者自守，或 ② core 侧分发收敛入锁）后，单线程顺序用例照常通过；并发注册 × 快照的 CME 交错在测试目录中完全不可达——连间接测试都没有并发形态。该测试同时是保护 WI1.1 core 侧修复受害者面的唯一候选位置。
- **现状**: 该枚举器类名在 8 模块测试目录零出现（仅经 `DebeziumCdcSource.createEnumerator()` 间接触及首分配路径）。
- **风险**: core 修复落地后受害者面无守护，未来 enumerator 重构可无声重引入竞态；份额静默丢失（表永不消费）是 F5 形态不可观测缺陷。
- **建议（测试形态）**: `TestDebeziumEnumeratorConcurrentRegistration`——2+ 线程以 CyclicBarrier 收敛并发 `addReader(i)`，与 checkpoint 线程循环 `snapshotState` 交错多轮；断言无 CME、每 reader 恰好收到一次完整 round-robin 份额、快照 unassigned 集与已交付集一致（对缺陷本体应红）。
- **失效面枚举**: `TestDebeziumCdcSharding`（纯分片函数）、`TestDebeziumCdcSource` 两枚举器用例均单线程；安全对照 `FileSplitEnumerator` 的 CHM 分片有 `TestFileSourceCheckpointRestore` 等正确性测试但同样无并发形态（它不需要）。
- **信心水平**: 确定
- **误报排除**: 不属"Enumerator 已被间接测试覆盖"——间接测试覆盖的是**分配正确性**（单线程），不是**并发安全**（缺陷面）。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：DebeziumTableSplitEnumerator 在 8 模块测试目录 grep 零命中独立复跑证实；最相邻间接测试（TestDebeziumCdcSource:108-125）确为单线程顺序 addReader→snapshotState；见文末「维度复核结论」

### [nop-stream-state-connectors-C10-006] 缺保护：C2-010（P1）WI14 REST 绕过 mailbox 直调 backend.snapshotState + TTL sidecar 裸 HashMap——跨线程面零覆盖

- **关联缺陷**: C2-010（受害者侧登记，挂靠 WI1.1-C3-009）——HTTP 4 线程池直调 `backend.snapshotState`（无 mailbox），与任务线程并发；`TtlContext.timestamps` 裸 HashMap 三点竞态；`states` 裸 LinkedHashMap 迭代 × 任务线程 put；读端点携带 `db.delete` 写副作用。
- **文件:行号**: `nop-stream/nop-stream-rocksdb/src/test/java/io/nop/stream/rocksdb/TestRocksDBStateTtl.java:156-170`（`listStateAndSweep` 显式调 `cleanupExpiredEntries()`）。
- **证据片段**: rocksdb 测试目录 28 个文件 grep `new Thread|ExecutorService|CountDownLatch` **零命中**（live 复核）——全模块测试单线程；TTL sweep 与 snapshotState 全部由测试主线程顺序驱动。
- **严重程度**: P2
- **并发场景**: ops REST 线程（`StreamOpsHttpServer` 4 线程池）调 `keyedStateSnapshot` 直达 backend，与任务线程的 `setCurrentKey+put`、checkpoint 路径并发。
- **触发时序（变异场景）**: 修复（mailbox 编组 + sidecar/states 并发容器化）后，所有单线程 TTL/快照测试照常通过；HTTP 线程 × 任务线程交叠不可达。相关 REST 端到端测试不在本单元 8 模块内（nop-stream-runtime 侧且为单线程请求-响应形态）。
- **现状**: `TestRocksDBStateTtl` 验证 sweep **语义**（过期回收正确），不验证 sweep/snapshot 的**线程纪律**。
- **风险**: 双处改动（core 派发侧 + rocksdb 受害侧）需独立验收，受害侧 CHM 化若无并发回归测试，可在后续重构中回退为裸容器而零信号。
- **建议（测试形态）**: `TestRocksDBKeyedStateBackendCrossThreadOps`——线程 A 持续 `setCurrentKey+put`（TTL 状态），线程 B 模拟 REST 派发直调 `snapshotState`+`cleanupExpiredEntries`；断言无 CME/无 native 异常/快照键集 ⊆ 已写键集；修复后追加断言 B 的调用被编组进任务线程（mailbox 队列深度 hook 观察）（对缺陷本体应红）。
- **失效面枚举**: `TestRocksDBStateTtl` 全部 8 用例单线程 FakeClock；`TestRocksDBSnapshotRestore`/`MemoryControl` 同形态。
- **信心水平**: 确定
- **误报排除**: 不属"REST 端点已有测试"——runtime 单元的 REST 测试是单线程请求-响应，验证**功能可达**而非**线程纪律**；本单元内跨线程面确实为零。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：rocksdb 28 个测试文件 grep new Thread|ExecutorService|CountDownLatch 零命中独立复跑证实，TTL sweep/snapshotState 均测试主线程顺序驱动；见文末「维度复核结论」

### [nop-stream-state-connectors-C10-007] 缺保护：C3-002 四向族（P1）JDBC 台账只增不删——写入有断言、保留/清理面零断言

- **关联缺陷**: C3-002（=C5-006/C7-001/C8-007）——`writeLedgerEntry` 仅 INSERT，无台账 DELETE/prune/retention 路径，每成功 checkpoint 每 subtask 一行确定性增长。
- **文件:行号**: `nop-stream/nop-stream-connector-jdbc/src/test/java/io/nop/stream/connector/jdbc/TestJdbcConcurrencyAndBatchSegments.java:156-159`。
- **证据片段**:
  ```java
  assertEquals(1, countLedgerRows(7L), "ledger entry must be recorded for the segmented commit");
  ...
  private int countLedgerRows(long epochId) throws Exception {
      ... "SELECT COUNT(*) FROM stream_epoch_ledger_v2 WHERE epoch_id = ?" ...
  ```
  jdbc+connector 测试目录 grep `prune|retention` 零命中（live 复核）。
- **严重程度**: P2
- **并发场景**: 长驻作业 × 每 barrier 写台账——单线程确定性增长（资源面），并发正确性面（幂等守卫）已有 `concurrentFirstInitializationIsSafe` 保护。
- **触发时序（变异场景）**: 补上台账 retention/TTL/归档后，现有用例（断言"本次 epoch 恰一行"）照常通过——清理语义零验证。更危险的反向回归（清理误删活跃 epoch 行、踩坏幂等守卫导致 at-least-once 破坏）同样无测试可红。
- **现状**: 台账测试覆盖**存在性写入/幂等/命名空间**三面，保留策略零覆盖。
- **风险**: 无界持久化增长与"过度清理"两个方向都无防护；修复批次最易引入的恰是后者。
- **建议（测试形态）**: (a) `TestJdbcLedgerRetention`——写 N 个历史 epoch 后触发清理，断言活跃高水位 epoch 行保留、历史行删除、同历史 epoch 重放仍被幂等守卫 typed 拒绝；(b) 小规模参数化（1 万 epoch 循环，批量写入即可，不需真实长跑）断言台账行数 ≤ 保留窗口（对缺陷本体应红）。
- **失效面枚举**: `TestJdbcTwoPhaseCommitSinkDeep`/`LedgerNamespace`/`ParallelIsolation` 全部断言正向写入面。
- **信心水平**: 确定
- **误报排除**: 不属"台账已有测试"——`countLedgerRows` 断言的是**本 epoch 恰一行**（写入正确性），与**历史行是否被清理**正交。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：assertEquals(1, countLedgerRows(7L)):156 与 countLedgerRows:159 逐字核对属实，断言仅覆盖本 epoch 写入存在性；jdbc+connector 测试目录 grep prune|retention 零命中独立复跑证实；见文末「维度复核结论」

### [nop-stream-state-connectors-C10-008] 缺保护：C3-003 五向族（P1）registry HTTP 无请求级超时 + 锁内 RPC——全快速成功路径，挂死面零覆盖

- **关联缺陷**: C3-003（=C2-002/C4-003/C6-008/C8-004/C7-006①）——全部 HttpRequest 无 `.timeout()`（仅 connectTimeout），首条记录在 `computeIfAbsent` 桶锁内做 HTTP；registry 建连后应用层挂起 → 任务线程 socket read 无界阻塞 + 同 subject 全 subtask 连锁阻塞（F5）。
- **文件:行号**: `nop-stream/nop-stream-format-avro/src/test/java/io/nop/stream/format/avro/TestAvroWireFormat.java:239-246`、:53-99（嵌入 HttpServer 快速响应）。
- **证据片段**:
  ```java
  @Test
  void registryUnreachableFailsTyped() {
      AvroSchemaRegistryClient unreachable = new AvroSchemaRegistryClient("http://127.0.0.1:1");
      ...                                          // 快速拒连（connect 阶段），非挂起形态
  }
  ```
  全文件 6 用例无慢响应/挂起服务器、无多线程 encode。
- **严重程度**: P2
- **并发场景**: registry 半死（accept 后不响应）× 多线程同 subject 首 encode——桶锁毒化放大。
- **触发时序（变异场景）**: 给 HttpClient/request 补 `.timeout(...)` 后，全部现有用例照常通过——它们从不制造"建连成功但响应挂起"的形态；反而，若在**修复前**注入挂死 registry，现有测试会永久挂起（无超时保护本身就是测试盲区的直接证据）。
- **现状**: format-avro 模块唯一测试文件全部快速成功路径 + 一次快速拒连。
- **风险**: 超时契约（超时值存在、typed 失败、锁最终释放）零钉住；五向归并族的修复（分散在超时/锁/重试多处）无回归防线。
- **建议（测试形态）**: (a) `TestAvroWireFormatHungRegistry`——`ServerSocket` accept 后不读不写（挂死注入）+ 断言 encode/decode 在配置超时内 typed 失败（**先红后绿的正确顺序**：修复前该测试超时失败）；(b) N 线程同 subject 并发首 encode + 挂死 registry，断言全部线程在超时内释放而非永久阻塞（锁毒化面）。
- **失效面枚举**: `TestAvroWireFormat` 全部 6 用例；`TestDebeziumCredentialIntegration` 等不触达 registry HTTP。
- **信心水平**: 确定
- **误报排除**: 不属"unreachable 测试已覆盖失败面"——`127.0.0.1:1` 在 connect 阶段立即拒连，走的是与"挂起"完全不同的代码路径（connectTimeout 可救）；缺陷面是 connect 成功后的 read 无界。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：TestAvroWireFormat 全文 6 用例核读均为快速成功/快速拒连路径，registryUnreachableFailsTyped:239-246 确用 127.0.0.1:1 connect 阶段拒连，无挂死注入、无线程并发；见文末「维度复核结论」

### [nop-stream-state-connectors-C10-009] 缺保护：C3-004（P1）Debezium 枚举器恢复双登记——恢复重分配路径零测试

- **关联缺陷**: C3-004——`restoreState` 单桶 computeIfAbsent 倾倒 + `start()` 按 `i%parallelism` 重算追加，不清不去重，剩余桶 index ≤ nsi 的表可同时落入恢复桶与 position 桶，两 reader 各自 launchEngine 同表重复消费。
- **文件:行号**: `nop-stream/nop-stream-connector-debezium/src/test/java/io/nop/stream/connector/debezium/TestDebeziumCdcSource.java:96-103`（仅序列化 round-trip）、:180-198（**reader 侧** restore，非枚举器分配）。
- **证据片段**:
  ```java
  void enumeratorStateSerializerRoundTripsUnassignedTables() { ... }   // :96 序列化 only
  void restoredOffsetsReplayIntoTheEmbeddedStateContract() {
      ... reader.restoreState(List.of(new DebeziumTableSplit("db.a", offsets, true)));  // :188 reader 侧
  ```
- **严重程度**: P2
- **并发场景**: 崩溃恢复后枚举器 `restoreState`+`start` 分配路径——单线程确定性逻辑缺陷（双登记），后果在并行运行期放大（双引擎同表消费）。
- **触发时序（变异场景）**: 修复（恢复分配去重/清桶）后现有用例照常通过——序列化 round-trip 与 reader 侧 offset 回放都不经过枚举器恢复重分配路径；双登记回归（缺陷本体即现状）同样无测试可红。对照：File 族有 `TestFileSourceCheckpointRestore`，Debezium 枚举器恢复零覆盖。
- **现状**: 枚举器测试仅两用例：首分配 round-robin + parallelism 门禁；恢复分配缺失。
- **风险**: 双引擎重复消费是数据正确性缺陷（重复 CDC 事件），静默且难排查；无测试则修复无信号。
- **建议（测试形态）**: `TestDebeziumEnumeratorRestoreAssignment`——构造恢复态 `new DebeziumCdcEnumeratorState(List.of("db.a","db.b","db.c"), nsi=2)`，`restoreState` 后 `addReader(0)/addReader(1)`+`start`，收集交付断言**每表恰好出现在一个 reader 的交付中**（对缺陷本体应红——先红后绿）；再加污染输入（恢复态含已分配表）断言去重。
- **失效面枚举**: `TestDebeziumCdcSource` 枚举器 3 用例（序列化/首分配/门禁）均不含恢复分配；`TestDebeziumCdcCheckpoint` 的恢复面在 legacy SourceFunction 路径。
- **信心水平**: 确定
- **误报排除**: 不属"restore 已有测试"——`restoredOffsetsReplay...` 是 **reader** 的 restoreState（offset 回放）；缺陷在 **enumerator** 的 restoreState（表分配）。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：枚举器仅有序列化 round-trip（:96）与首分配（:108）用例，restoredOffsetsReplay...（:180）确为 reader 侧 restoreState，枚举器恢复重分配路径零测试核实；见文末「维度复核结论」

### [nop-stream-state-connectors-C10-010] 缺保护：C3-009/C4-004（P1 归并族）Debezium 引擎常驻平台共享 globalWorker 池——提交通道与饱和行为零测试

- **关联缺陷**: C3-009（=C4-004）——`GlobalExecutors.globalWorker().execute(() -> engine.run())`，池 core=4/max=30/queue=10000，每 CDC subtask 钉死 1 条共享线程；饱和后新引擎提交进深队列静默不执行（死流无信号）。
- **文件:行号**: 8 模块测试目录 grep `GlobalExecutors|globalWorker` **零命中**（live 复核）。
- **证据片段**: 唯一引擎宿主测试 `TestDebeziumCdcSource.StubbedReader`（:241-267）覆写 `createEmbeddedFunction` 返回自研 stub——真实 `DebeziumEngineWrapper` 的创建与池提交在测试中从不发生。
- **严重程度**: P2
- **并发场景**: 多 CDC subtask × 共享池资源竞争；引擎分发/背压事件同步内联占用池线程。
- **触发时序（变异场景）**: 修复（专用单线程 executor 或 caller 线程模型）后无任何测试感知差异——现有测试根本不触及提交通道；池耗尽→静默死流面（F5）同样不可达。
- **现状**: 引擎执行线程的池归属在测试中完全不可观测。
- **风险**: 修复可能只改了 wrapper 而漏掉提交点（或反之），无测试判定"引擎不再占用 globalWorker"；饱和静默死流回归不可见。
- **建议（测试形态）**: (a) `TestDebeziumEngineExecutionChannel`——hook 观察引擎 run 实际执行线程（Thread.name/池归属），断言不落在 `globalWorker`（对缺陷本体应红）；(b) 饱和行为测试（小规模参数化）：用可控 executor 占满池后提交新引擎任务，断言按声明语义 fail-fast 或排队可观测（超时 typed 失败），而非无限静默排队。
- **失效面枚举**: `TestDebeziumCdcSource*` 全族经 StubbedReader/NoEngineMessageSource 双双绕开真实提交；`TestDebeziumReal*`（docker 门控）验证功能连通，不验证池归属。
- **信心水平**: 确定
- **误报排除**: 不属"docker 集成测试已覆盖"——Real* 测试跑通即断言有数据，不区分引擎跑在哪个线程上。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：8 模块测试目录 grep GlobalExecutors|globalWorker 零命中独立复跑证实；StubbedReader:241 覆写 createEmbeddedFunction 绕开真实引擎提交核实；见文末「维度复核结论」

### [nop-stream-state-connectors-C10-011] 缺保护：C3-014 停止契约族（P1，含 C4-005/C4-006 停止面）真实引擎 stop/close 被 stub 全绕开——幽灵引擎零防护

- **关联缺陷**: C3-014——engine.close() 在 STARTING_TASKS 抛 ISE（javap 反编译证实）、wrapper 双层吞异常、stop 为 fire-and-forget 无终结性验证；快速重启时新旧引擎同名 MBean 交叠卡死（永久幽灵引擎）。
- **文件:行号**: `nop-stream/nop-stream-connector-debezium/src/test/java/io/nop/stream/connector/debezium/TestDebeziumCdcSource.java:241-267`（StubbedReader）；`TestDebeziumCdcSourceFunction.java:274-305`（`testFinallyBlockCleansUpResources` 只断言 runner 线程死亡）。
- **证据片段**:
  ```java
  @Override
  protected DebeziumCdcSourceFunction createEmbeddedFunction(DebeziumConfig engineConfig) {
      launchedConfig.set(engineConfig);
      return new DebeziumCdcSourceFunction(engineConfig) {
          @Override public void run(...) { while (!cancelled.get()) { Thread.sleep(50); } }  // stub，无引擎
  ```
- **严重程度**: P2
- **并发场景**: start→stop 快速交叠（饱和池排队窗口 × 快速取消/重启）；同 instance 名引擎代际交叠。
- **触发时序（变异场景）**: 修复（stop 重试/等待终结 + stopped 守卫 + 异常传播）后无任何测试感知——真实 wrapper 的 start/stop/close/MBean 注册路径从不在测试中运行；幽灵引擎回归不可红。第三方 ISE 契约（STARTING_TASKS 拒绝 close）零钉住，依赖升级时无守护。
- **现状**: FLIP-27 reader 测试全部经 StubbedReader；legacy function 测试经 NoEngineMessageSource；`TestDebeziumCdcSourceErrorPropagation` 测失败回调投递，不测 stop 终结性。
- **风险**: 停止契约族（三面并一项）是本单元最重的未保护面之一；修复无信号、依赖升级无防线。
- **建议（测试形态）**: `TestDebeziumEngineStopContract`——用真实 `DebeziumEngineWrapper` + 立即 stop 时序（start 后 ≤10ms stop，循环 N 次模拟快速重启），断言 (a) stop 返回后引擎线程在预算内终态（join 有上界），(b) 同名 instance 重启后 MBean 注册成功（对缺陷本体应红/超时）；(c) 饱和池窗口守卫：可控单线程 executor 阻塞提交，start 后任务执行前 stop，断言 lambda 观察到 stopped 后不再 `engine.run()`（覆盖 C4-006 面）。
- **失效面枚举**: `TestDebeziumCdcSource` 6 用例 + `TestDebeziumCdcSourceFunction` 9 用例 + `TestDebeziumResourceManagement` 1 用例——全部 stub 化引擎宿主。
- **信心水平**: 确定
- **误报排除**: 不属"ErrorPropagation 已测失败面"——它测的是**失败回调到达后 run 失败**（投递面），不是 **stop 请求后的终结性**（停止面）。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：StubbedReader:241 覆写 createEmbeddedFunction、testFinallyBlockCleansUpResources:274 仅断言 runner 死亡（:304）、testRunEnteredAtomicPreventsDoubleEntry:308 仅 assertFalse(isAlive)（:347-348）逐一核实，真实引擎 stop/close/MBean 契约零测试；见文末「维度复核结论」

### [nop-stream-state-connectors-C10-012] 缺保护：C5-002 归并族（P1，含 C8-005）RocksDB 全量快照整库物化进堆——规模行为零断言

- **关联缺陷**: C5-002（=C8-005）——默认全量 JSON checkpoint 路径 `RocksDBSnapshotSerDe` 逐值反序列化 + entries/grouped 全量驻留堆内对象图，增量开关缺省 false；包络内高概率周期性 OOM。
- **文件:行号**: `nop-stream/nop-stream-rocksdb/src/test/java/io/nop/stream/rocksdb/TestRocksDBSnapshotRestore.java`（729 行 22 用例，全部 ≤数条记录）。
- **证据片段**: 22 个 @Test 全部为小规模 round-trip/跨后端一致性（全文件 `.put(` 仅 9 处，live 计数）；无任何"状态规模 × 堆占用/驻留集合尺寸"断言；`TestRocksDBIncrementalLifecycleAndIntegrity` 覆盖增量 segment 面（但增量缺省关，非缺陷路径）。
- **严重程度**: P2
- **并发场景**: 资源面（内存包络）——非竞态；checkpoint 周期性触发即确定性。
- **触发时序（变异场景）**: 流式化改造（RocksIterator 流式包装，修复族①）后全部 round-trip 用例照常通过——功能正确性有保护、规模行为零保护；物化回归（重新引入全量驻留）同样无测试可红。
- **现状**: 快照的**正确性**（round-trip 无损）与**有界性**（堆内驻留上界）两个维度只有前者有测试。
- **风险**: P1 主立案修复（流式化）若回归，唯一后果是生产 OOM——测试零信号。
- **建议（测试形态）**: `TestRocksDBSnapshotResidencyBound`（小规模参数化，不要求真实压测）——注入 N=10^4~10^5 键，经可测 hook（如包装迭代器逐批交付 + 计数器）断言快照路径"同时在堆内的反序列化对象数 ≤ K"或"快照期间内存增量有上界"（对缺陷本体应红）。
- **失效面枚举**: `TestRocksDBSnapshotRestore` 22 用例；`TestRocksDBStateMigration`（2 key 迁移）同形态；增量族测试不覆盖缺省全量路径。
- **信心水平**: 确定
- **误报排除**: 不属"round-trip 测试已覆盖快照"——round-trip 验证**数据无损**，与**堆内驻留规模**正交；小规模下两种实现行为一致。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：TestRocksDBSnapshotRestore 729 行 / 22 @Test / .put( 9 处三项计数逐一复核属实，最大用例仅个位数键，无规模/驻留断言；见文末「维度复核结论」

### [nop-stream-state-connectors-C10-013] 缺保护：C8-001（P1）Debezium reader checkpoint offset 领先已发射——现有测试钉住缺陷载体 + 显式回避竞态窗口

- **关联缺陷**: C8-001——`snapshotState` 直抬引擎 offset、无 last-emitted 回退；offset 推进先于下游发射，256 队列在途恢复期静默丢 ≤256 条/检查点。
- **文件:行号**: `nop-stream/nop-stream-connector-debezium/src/test/java/io/nop/stream/connector/debezium/TestDebeziumCdcSource.java:166-174`；`TestDebeziumCdcCheckpoint.java:288-291`。
- **证据片段**:
  ```java
  // TestDebeziumCdcSource —— 断言 lift-all（恰为缺陷载体语义）：
  // the offset map lifts into EVERY split snapshot (instance-level copy, M-2)
  stub.offsets.put("partition-key", "base64-offset");
  List<DebeziumTableSplit> snapshot = reader.snapshotState(7L);
  for (DebeziumTableSplit split : snapshot) {
      assertTrue(split.isOffsetCarrying());
      assertEquals("base64-offset", split.getOffsetMap().get("partition-key"));   // :172
  }
  // TestDebeziumCdcCheckpoint —— 显式回避竞态：
  // Snapshot AFTER kill: capture the final committed offset. Snapshotting before cancel
  // races with the emitter thread (...)                        // :288-289
  ```
- **严重程度**: P2
- **并发场景**: 引擎投递线程推进 offset store × checkpoint 线程快照——队列在途（≤256）+ handler 在途（1）条尚未发射。
- **触发时序（变异场景）**: 修复（last-emitted 回退）后 `StubbedReader` 手工注入的 offset 仍全量抬升（stub 无发射史），`:166-174` 照常通过；legacy 路径的 kill/recover 测试把快照放在取消之后，发射-偏斜窗口被人为清零，照常通过。偏斜回归（缺陷本体）亦无测试可红。
- **现状**: 两条路径的测试**合谋钉住了缺陷语义**（一个钉 lift-all，一个回避窗口）。
- **风险**: 静默丢数据（P1 主后果）无任何测试防护；修复者还需识别 `:166-174` 断言在 last-emitted 语义下的正确形态（有发射史时应断言**回退后**的 offset）。
- **建议（测试形态）**: `TestDebeziumReaderOffsetSkew`——经 stub 投递 K 条事件、**不等消费完成**即 `snapshotState`（把 TestDebeziumCdcCheckpoint 回避掉的窗口显式测进来），断言快照 offset == last-emitted（而非 last-received）；恢复后重放恰好是在途 K 条（对缺陷本体应红）。
- **失效面枚举**: `TestDebeziumCdcCheckpoint` 的 kill/recover 用例（:288 注释处）、`TestDebeziumCdcSource` 的 lift-all 用例、`restoredOffsetsReplay...`（无发射史）。
- **信心水平**: 确定
- **误报排除**: 不属"checkpoint 测试已覆盖 offset 语义"——现有覆盖验证 offset **快照与恢复 round-trip 无损**，与 **offset 相对发射进度的偏斜**正交。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：TestDebeziumCdcSource:166-172 lift-all 断言与 TestDebeziumCdcCheckpoint:288-289「Snapshot AFTER kill…races with the emitter thread」注释逐字属实——「钉住缺陷载体 + 显式回避竞态窗口」双重判定成立；见文末「维度复核结论」

### [nop-stream-state-connectors-C10-014] 缺保护：C8-006（P1）Kafka 事务超时零裕度——transaction.timeout.ms 零断言

- **关联缺陷**: C8-006——`newProducer()` 不设 `transaction.timeout.ms`（默认 60s），且下一 epoch 事务在 barrier N 的 saveState 内立即 begin，事务存活期 ≥ checkpoint 间隔，默认组合下年龄恒超 60s，触界进入失败循环。
- **文件:行号**: kafka 测试目录 grep `transaction.timeout` **零命中**（live 复核）；`TestKafkaTwoPhaseCommitSinkLifecycle.java:201-212`（唯一 producer 属性面用例）。
- **证据片段**:
  ```java
  @Test
  void transactionalIdOverrideFailsTyped() {          // :203 —— 唯一的 producer 属性断言
      Map<String, Object> props = new LinkedHashMap<>();
      props.put("transactional.id", "hijack");
      ... assertTrue(String.valueOf(ex).contains("transactional.id"), ...);
  ```
- **严重程度**: P2
- **并发场景**: 事务年龄（saveState begin → 下一 barrier commit）与 Kafka 协调器 60s 惰性过期的竞争——非代码竞态，配置包络缺陷。
- **触发时序（变异场景）**: 修复（设 `transaction.timeout.ms` > checkpointTimeout+裕度，或启动时校验耦合）后现有用例照常通过——6 个用例无一断言 producer 属性内容（除 transactional.id 守卫）或超时耦合校验；配置回归（缺省 60s）亦无测试可红。
- **现状**: kafka 模块 2 个测试文件对事务超时零触及。
- **风险**: 失败循环（触界后 abort/retry 振荡）是长时运行才显形的缺陷，无测试则修复无信号。
- **建议（测试形态）**: (a) `TestKafkaTwoPhaseCommitSinkLifecycle` 增补：`newProducer` 产物 props 含 `transaction.timeout.ms` 且 ≥ checkpointTimeout × 系数（对缺陷本体应红）；(b) 耦合校验测试：构造 checkpointTimeout=60s + producer 超时=60s 的组合断言启动 fail-fast（对照同仓 `validateUnalignedConfig` 先例）。
- **失效面枚举**: `TestKafkaTwoPhaseCommitSinkLifecycle` 全部 6 用例；`TestKafkaSourceFunctionSharding` 无 producer 面。
- **信心水平**: 确定
- **误报排除**: 不属"lifecycle 测试已覆盖 producer 配置"——`transactionalIdOverrideFailsTyped` 只测**用户覆写被拒**，不测**平台自身设置的超时值**。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：kafka 测试目录 grep transaction.timeout 零命中独立复跑证实；唯一 producer 属性断言 transactionalIdOverrideFailsTyped:203 只测用户覆写被拒，不测平台自身超时值；见文末「维度复核结论」

### [nop-stream-state-connectors-C10-015] 缺保护（P2 命中，一并登记）：C3-011 Debezium run 重入守卫静默 return——catch-all 断言吞掉竞争差异

- **关联缺陷**: C3-011（P2，简报授权的 P2 命中登记）——重入守卫 CAS 失败静默 return 把"检测到的双进入"转译为正常 EOS（假 EOS）；触发面=程序化直构路径（owner doc 声明的合法用法）。
- **文件:行号**: `nop-stream/nop-stream-connector-debezium/src/test/java/io/nop/stream/connector/debezium/TestDebeziumResourceManagement.java:54-60`。
- **证据片段**:
  ```java
  Thread runner2 = new Thread(() -> {
      try {
          debeziumSource.run(ctx);
          secondRunReturned.set(true);      // :57 正常 return
      } catch (Exception e) {
          secondRunReturned.set(true);      // :59 抛错 —— 折叠为同一结果
      }
  });
  ```
- **严重程度**: P2
- **并发场景**: 双线程并发 `run()`（同实例），守卫 CAS 竞争。
- **触发时序（变异场景）**: 修复（CAS 失败改为 typed 异常/向上传播）后该测试照常绿——`catch(Exception){ set(true) }` 把"正常 return"与"抛错"折叠为同一断言结果；回归回静默 return 也照常绿。`TestDebeziumCdcSourceFunction#testRunEnteredAtomicPreventsDoubleEntry:308-349` 同样只断言"两线程均完成、无死锁"（:347-348 仅 `assertFalse(isAlive)`），对守卫检测到冲突后的**响应语义**完全不敏感。步骤 3"测试自己 catch 掉竞争"的典型命中。
- **现状**: 重启面（run-after-cancel 静默 EOS）有强保护（`testRunAfterCancelReEntersLoop:352` 断言重启后 runner 存活）——但那是 cancel-restart 面，非 CAS 竞争面。
- **风险**: 守卫语义修复无信号；假 EOS（数据流静默终止）回归不可见。
- **建议（测试形态）**: 双线程并发 run + 断言第二线程**终态形态**：要么抛 typed 异常（断言错误码），要么正常进入循环持续投递（断言 subscribeCount/engineCreated 随第二 run 增长）；唯独不允许"未订阅即正常返回"。
- **失效面枚举**: `TestDebeziumResourceManagement`（全文件唯一用例）、`TestDebeziumCdcSourceFunction#testRunEnteredAtomicPreventsDoubleEntry`。
- **信心水平**: 确定
- **误报排除**: 不属"守卫已有测试"——测试验证**守卫存在**（快速返回/不死锁），不验证守卫触发后的**响应是否可观测**。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：TestDebeziumResourceManagement:57/:59 正常 return 与抛错折叠为同一 secondRunReturned.set(true) 核读属实（全文件唯一用例）；TestDebeziumCdcSourceFunction:347-348 仅断言线程死亡亦核实；见文末「维度复核结论」

### [nop-stream-state-connectors-C10-016] 缺保护（测试自身缺陷）：TestBatchLoaderSourceFunction 无界自旋等待——回归时挂死而非失败

- **关联缺陷**: 非缺陷缺保护，属 C10 步骤 3 时序敏感测试审查的直接发现（测试基建健壮性）。
- **文件:行号**: `nop-stream/nop-stream-connector-batch/src/test/java/io/nop/stream/connector/batch/TestBatchLoaderSourceFunction.java:250-253`。
- **证据片段**:
  ```java
  while (collected1.size() < 2) {
      Thread.sleep(5);                       // :250-251 无 deadline
  }
  Thread.sleep(50); // let the runner park on the gate   // :253
  ```
- **严重程度**: P2
- **并发场景**: 测试主线程自旋等待 runner 线程发射前 2 条记录。
- **触发时序（变异场景）**: 发射链路任何回归（如 run() 早退、gate 语义改变）都会使 collected 永远 <2，测试**永久挂起**而非失败——CI 侧表现为吞超时/任务取消，缺陷被掩盖为基础设施故障而非红灯。
- **现状**: 全 8 模块 Thread.sleep 盘点（40 处/18 文件）中**唯一一处无上界自旋**；batch 模块自己的 `testsupport/TestAwait`（deadline 60s）已存在却未在此用例使用；`TestDebeziumCdcCheckpoint`（5×5s deadline 轮询）是同批正例。
- **风险**: 测试挂死比测试失败危害更大（阻塞整条 CI 流水线、掩盖其他真实失败）。
- **建议（测试形态）**: 改为 `TestAwait.until("first two records emitted", () -> collected1.size() >= 2, 5_000)`；附带注记：`TestMessageSourceFunctionThreadSafety.java:101` 的 `runner.join(5000)` 未接 `assertFalse(runner.isAlive())`——若 runner 挂死，后续 collected.size 断言仍可能通过（弱断言），建议同批补 `assertFalse(isAlive)`。
- **失效面枚举**: 该文件第二处 sleep（:253 单次 50ms）为有界定向等待，可接受。
- **信心水平**: 确定
- **误报排除**: 不属"短暂 sleep 可接受"——该循环退出条件依赖被测行为发生，且无上界，与 `TestAwait` 既有正例形成直接对照。
- **复核状态**: 保留（P2）—— 2026-10-14 独立复核确认：TestBatchLoaderSourceFunction:250-251 while(collected1.size()<2){sleep(5)} 无 deadline 核读属实；全 8 模块 while+sleep 站点扫描证实其为唯一无上界自旋（其余均带 deadline 守卫）；TestMessageSourceFunctionThreadSafety:101 join 后无 isAlive 断言（对照 :163/:236 有）亦核实；见文末「维度复核结论」

---

## 4. 时序敏感/资源泄漏/大规模测试审查

### 4.1 时序敏感测试审查（步骤 3）

Thread.sleep 全量盘点（8 模块 src/test，live 计数）：**40 处 / 18 文件**。分类：

1. **无界自旋（会挂死的测试缺陷，命中 1）**: `TestBatchLoaderSourceFunction:250-252` `while (collected1.size() < 2) { Thread.sleep(5); }` 无 deadline——回归时测试永久挂起而非失败。→ 登记 **C10-016**。
2. **测试自吞竞争（superficially-pass，命中 2）**:
   - `TestDebeziumCdcCheckpoint:288-291` 显式注释 "Snapshot AFTER kill: ... Snapshotting before cancel races with the emitter thread"——为避免 flaky 把快照时点移到取消之后，恰好把 C8-001（快照-发射偏斜窗口）整个排除在断言之外；测试通过与缺陷共存不冲突。→ 随 **C10-013** 登记。
   - `TestDebeziumResourceManagement:57-59` runner2 的 `catch (Exception e) { secondRunReturned.set(true); }` 把"静默 return"与"抛错"折叠为同一断言结果。→ 登记 **C10-015**。
3. **deadline 化的 sleep 轮询（可接受，非命中）**: `TestDebeziumCdcCheckpoint`（5 处 `while(...<deadline) sleep(50)`，deadline 5s）、`TestConnectorResourceManagement#awaitUntil`（30s deadline）、`TestDebeziumCdcSourceFunction`（5s）、三份 `TestAwait`（deadline 60s + 线程状态确定性信号）——有上界的平台可接受形态。
4. **Real*/集成测试的长 sleep（2-5s，非命中）**: `TestDebeziumRealMysqlCdc`(4 处)/`RealMysqlParallel`(5 处)/`RealPostgresCdc`(2 处)/`CrossDbSyncPipeline`(2 处)/`CredentialIntegration`(1 处)——docker `assumeTrue` 门控，非默认路径。
5. **未等待异步结果就断言（弱断言，注记 1）**: `TestMessageSourceFunctionThreadSafety:101` `runner.join(5000)` 未接 `assertFalse(runner.isAlive())`——若 runner 挂死，collected.size 断言仍可能通过。随 C10-016 注记，不单独立项。
6. **其余裸 sleep**: `StubbedReader.run` 的 `sleep(50)` 循环（stub 自身 run-loop，每轮有界）、`TestMessageSource`/`TestMessageAdapters`/`TestDrainableSourceSupport`/`TestFileSourceReaderRecovery`/`TestMessageSourceFunctionRestart` 各 1-2 处单次有界 sleep——均为确定性等待或注入间隔，非命中。

**时序敏感命中合计: 4 项**（1 无界自旋 + 2 自吞竞争 + 1 弱 join 注记）。

### 4.2 资源泄漏测试钩子盘点（步骤 4）

| 钩子形态 | 现状 |
|---|---|
| 订阅/监听器退订断言 | **有（范本）**：`TestMessageSourceFunctionLifecycle` 的 `RecordingSubscription.cancelCount==1`（:141/:177）——本单元最好的释放断言范式；debezium stub 的 `appendOnCancel/removeOnCancel`（`TestDebeziumCdcSourceErrorPropagation:72-75`）为空实现，未断言 |
| producer close 断言 | **无**：`TestKafkaTwoPhaseCommitSinkLifecycle` 全文无 close（FakeProducer 未覆写 close 记录调用）——C6-001（P0）零防护 → 建议形态见 C10-001 |
| HttpClient/selector 线程泄漏断言 | **无**：`TestAvroWireFormat` 无线程数/close 断言（C4-002 面）；测试自身每用例泄漏一个 client 而无感知钩子 |
| JNI/native 句柄释放断言 | **无**：rocksdb 仅断言 close 后 gauge NaN（顺序面），无 `nativeHandle_==0` 负探针、无 openDB 失败路径的 Options/Cache 泄漏断言（C6-005 面）→ C1-002 族建议形态见 C10-002 |
| 注册表/静态表清空断言 | **无**：`MANIFEST_JVM_LOCKS`、`FAILURE_LISTENERS`（debezium 测试 grep 零命中）、`StreamMetricsRegistries` meter 摘除（仅 2 个测试文件引用 registry，均无 close 后摘除复查）三张进程级表均无 size/摘除断言 |
| 临时文件清理断言 | **有**：`TestFileTwoPhaseCommitSink`（abort 删 tmp :254、kill/recover 无 `*.tmp` 残留 :333）、`TestRocksDBIncrementalLifecycleAndIntegrity#segmentStoreNeverLeavesTempOrPartialFilesAtFinalName:286`、`taskLocalCheckpointDirsStayBoundedAfterManySnapshots:108`（cp-{N} 有界）——但 restore-range 中途失败残留面（C7-003）不在钩子覆盖内 |
| 池/队列满边界参数化 | **无**：8 模块无任何"队列满/池耗尽/缓存上限淘汰"边界测试（globalWorker 池、CEP 匹配缓冲、2PC epoch 缓冲均无） |

对 P1 级资源缺陷（C6-001、C1-002 族）的建议测试形态已并入 C10-001/C10-002。

### 4.3 大规模/边界包络测试盘点（步骤 5，小规模参数化口径）

**已有边界测试**：
- `TestRocksDBIncrementalLifecycleAndIntegrity#taskLocalCheckpointDirsStayBoundedAfterManySnapshots`（N>K 次快照后 cp 目录 ≤ K——**本单元最好的包络钩子**，但仅覆盖 `cp-{N}` 面）；
- `TestBatchLoaderParallelismGate#testParallelismAboveOneFailsFast`（maxParallelism 门禁边界）+ `TestKafkaSourceFunctionSharding#copyForSubtaskRejectsIndexBeyondRecordedParallelism`（subtask index 边界）+ `TestRocksDbDefaultMaxParallelism`（缺省值=KeyGroup 上界常量断言）；
- `TestJdbcConcurrencyAndBatchSegments#largeEpochIsCommittedInSegmentsWithIdenticalResult`（maxBatchSize 提交**分段**边界——非缓冲上界）、`#zeroMaxBatchSizeIsRejected`（参数校验边界）；
- `TestJdbcConcurrencyAndBatchSegments#concurrentFirstInitializationIsSafe`（6 线程 CyclicBarrier 并发初始化——本单元最规范的并发收敛测试）。

**缺失的边界测试**（对应缺陷）：
- 有界队列满行为（B1/B2 256 槽位、CEP 匹配缓冲）→ C8-002 面；
- epoch 缓冲上限/溢出策略 → C5-005/C8-003 面；
- 台账保留窗口 → C3-002 族（C10-007 建议含 1 万 epoch 参数化）；
- 快照堆内驻留上界 → C5-002 族（C10-012 建议含 10^4~10^5 键参数化）；
- checkpoint 间隔 × 事务超时耦合校验 → C8-006（C10-014 建议 (b)）；
- registry schema 缓存上限淘汰 → C7-008（P3）面。

---

## 5. 统计与覆盖声明

### 5.1 计数

- **盘点**: 8 模块 150 个测试 java 文件（143 `Test*` + 7 支撑类）；并发原语使用 23 文件；真正的多线程测试约 12 个；真并发缺陷面覆盖仅 2 处（JDBC 首初始化竞态、File manifest 单 JVM 互斥）。
- **保护力矩阵**: 15 行（P0×1 + P1 输入 16 ID 归并 15 行全推演）= **会红（钉死缺陷）1**（C1-006）+ **条件会红 1**（C1-001，随 C1-006 合并修复经 `assertSame(userFunction)` 反转）+ **假保护 5** + **无测试 8**。
- **缺保护登记**: **16 条**（C10-001..C10-014 对应 P0/P1 矩阵行；C10-015 为简报授权的 P2 命中一并登记；C10-016 为测试自身缺陷）——按缺陷族聚合，每条引用全部归并编号。
- **时序敏感命中**: 4 项（1 无界自旋 + 2 测试自吞竞争 + 1 弱 join 注记）。
- **资源泄漏钩子**: 7 形态中 2 有（订阅退订、tmp 清理）、5 无（producer close、selector 线程、JNI 句柄、注册表清空、池/队列满）。

### 5.2 优先级提示（修复批次配套）

- **P0 修复（C6-001）必须同批带上 C10-001 建议测试**（FakeProducer 覆写 close + 传播/幂等/不误关三断言）——否则 close 语义可在任何后续重构中无声消失。
- **四大 P1 修复族优先配套**：C10-002（gauge 竞态）、C10-011（引擎停止契约）、C10-013（offset 偏斜）、C10-012（快照驻留上界）。
- **会红条目（C1-001/C1-006）修复批次必须同步反转 `TestCepOperatorSubtaskCopy:43-44` 的 `assertSame`**——不反转则修复无法落地（测试红），反转后建议以 C10-003 建议测试承接 NFA 面防护。
- 每项 P1 修复建议"先红后绿"落地：先在缺陷本体上跑新测试确认红，再修复确认绿。

### 5.3 覆盖声明

- **已覆盖**: 输入清单 P0×1 + P1×16 全部完成变异推演；全部「零命中」声明（DebeziumTableSplitEnumerator、GlobalExecutors、transaction.timeout、setQueryTimeout、FAILURE_LISTENERS、reconciledTimerKeys、openDB/dbOptions、rocksdb-restore-range、prune/retention、cep operator 线程、rocksdb 并发原语）经 live grep 逐一复核；12 个关键测试文件全文精读，引用行号逐一核对。
- **未覆盖/边界**: (1) P2/P3 缺陷（约 30 条）未逐条推演，仅在时序敏感/资源钩子审查中登记命中 2 条（C10-015/C10-016）并在 §2 侧记 5 条同类假保护形态；(2) docker 门控集成测试（`TestDebeziumReal*`、`CrossDbSyncPipeline`、`CredentialIntegration`）只盘点未执行；(3) 静态推演未实际运行变异注入——「会红/假保护/无测试」判定基于断言语义检视，置信度以「确定」为主（唯一依赖推断强度的是 C10-003 的条件会红判定，其依据为 `assertSame` 仅及 userFunction 的事实）。
- **与缺陷本体的计数关系**: 本文件 16 条登记**不并入**缺陷本体计数（C1–C8 维度），仅作为修复批次的测试配套清单与回归防护缺口台账。

---

## 维度复核结论（独立复核子代理）

- **复核人标识**: nop-stream-state-connectors C10 维度独立复核子代理（与初审者非同一角色实例），2026-10-14，工作目录 nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）。
- **复核输入**: 本文件全文 + 同目录 C1–C8 八个维度文件的「最终保留项」节（含各维度二轮复核权威版）+ 8 模块 src/test live code。未读 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`；缺保护登记的现状按口径继承对应缺陷本体的现状（取自各维度最终保留项表现状列）。
- **复核方法**: (1) 零命中声明独立复跑 grep 抽查 7 项（超出要求的 5 项）：DebeziumTableSplitEnumerator / GlobalExecutors|globalWorker / transaction.timeout / cep operator 目录 new Thread|ExecutorService / rocksdb 全模块并发原语 / jdbc+connector prune|retention / kafka lifecycle 测试 close|teardown——全部零命中确认；(2) 「会红」行逐字核对 `TestCepOperatorSubtaskCopy.java:43-44`；(3) 5 条假保护行全部读测试代码验证 superficially-pass 形态；(4) 16 条登记的 13 字段格式完整性核查；(5) 全部关联缺陷编号逐一对照各维度最终保留项；(6) 时序敏感 4 项命中逐条核读；(7) 盘点计数抽查（150 文件总数、cep 66、kafka 测试 213 行）。

### 逐条判定表

| 编号 | 原判 | 判定 | 复核理由（独立重验要点） | 现状（继承缺陷本体） |
|---|---|---|---|---|
| C10-001 | P2 | 保留 | FakeProducer 仅覆写 init/begin/commit/abort/flush/send 六方法（:41-85）、全文 grep close\|teardown 零命中、6 用例、:115 精确序列断言——全部核读/复跑证实；误报排除推理（序列断言对「缺 close」方向零敏感）成立 | C6-001（P0）：新发现，未修复 |
| C10-002 | P2 | 保留 | :113-130 单线程顺序（活值→close→NaN）+ 独立 recorder（`backend.getDbForTest()`）核读证实，4 用例全单线程；rocksdb 零并发原语独立复跑 | C1-002=C2-001=C6-004（P1）：新发现，未修复 |
| C10-003 | P2 | 保留 | 全文仅 2 断言（assertNotSame operator / assertSame udf），NFA 零断言；cep 测试仅 2 文件触 copyForSubtask、唯一身份断言即 :43-44（grep 证实）；「条件会红」推演成立（udf 与 nfaFactory 独立构造，:35-38，单落 nfaFactory 修复不动 assertSame） | C1-001（P1）：已知未修复（r4 R5-CEP-05） |
| C10-004 | P2 | 保留 | cep operator 测试目录零 Thread/ExecutorService 独立复跑证实；单 subtask 形态下「槽位复用/独占」不可区分的推演成立 | C1-005（P1）：新发现，未修复 |
| C10-005 | P2 | 保留 | 类名 8 模块测试目录 grep 零命中独立复跑；间接测试 :108-125 单线程顺序 addReader→snapshotState 核读证实 | C2-003（P1）：已知未修复（受害者侧，挂靠 WI1.1-C1-001） |
| C10-006 | P2 | 保留 | rocksdb 28 测试文件零并发原语独立复跑证实（全模块 `grep new Thread\|ExecutorService\|CountDownLatch` 零命中） | C2-010（P1）：已知未修复（受害者侧，挂靠 WI1.1-C3-009） |
| C10-007 | P2 | 保留 | :156/:159 逐字属实、断言仅本 epoch 写入存在性；prune\|retention 零命中独立复跑 | C3-002 四向族（P1）：已知未修复（r6 AR-06 + campaign 主条目） |
| C10-008 | P2 | 保留 | 全文 6 用例核读均快速路径；:239-246 确为 connect 阶段快速拒连；无挂死注入/多线程 encode | C3-003 五向族（P1）：新发现，未修复 |
| C10-009 | P2 | 保留 | 枚举器仅序列化（:96）+ 首分配（:108）用例；:180 确为 reader 侧 restoreState——枚举器恢复重分配零覆盖核实 | C3-004（P1）：新发现，未修复 |
| C10-010 | P2 | 保留 | GlobalExecutors\|globalWorker 零命中独立复跑；StubbedReader:241 覆写 createEmbeddedFunction 核实 | C3-009=C4-004（P1）：新发现，未修复 |
| C10-011 | P2 | 保留 | StubbedReader:241 / testFinallyBlockCleansUpResources:274（仅断言 runner 死亡 :304）/ testRunEnteredAtomicPreventsDoubleEntry:308（仅 assertFalse(isAlive) :347-348）/ testRunAfterCancelReEntersLoop:352 全部定位核实；真实引擎 stop/close/MBean 契约零测试 | C3-014 停止契约族（P1）：新发现，未修复 |
| C10-012 | P2 | 保留 | TestRocksDBSnapshotRestore 729 行 / 22 @Test / `.put(` 9 处三项计数逐一复核属实，最大用例个位数键，无规模/驻留断言 | C5-002=C8-005（P1）：新发现，未修复 |
| C10-013 | P2 | 保留 | TestDebeziumCdcSource:166-172 lift-all 断言与 TestDebeziumCdcCheckpoint:288-289 回避注释逐字属实——「钉住缺陷载体 + 显式回避竞态窗口」双重判定成立 | C8-001（P1）：新发现，未修复 |
| C10-014 | P2 | 保留 | transaction.timeout 零命中独立复跑；:203 唯一 producer 属性断言只测用户覆写被拒 | C8-006（P1）：新发现，未修复 |
| C10-015 | P2 | 保留 | TestDebeziumResourceManagement:57/:59 正常 return 与抛错折叠为同一 `secondRunReturned.set(true)` 核读属实（全文件唯一用例）；TestDebeziumCdcSourceFunction:347-348 仅断言线程死亡亦核实 | C3-011（P2）：新发现（r2 CAS 化修复的响应语义残留），未修复 |
| C10-016 | P2 | 保留 | :250-251 无 deadline 自旋核读属实；全 8 模块 while+sleep 站点扫描证实为唯一无上界自旋（其余均 `System.currentTimeMillis() < deadline` 守卫）；TestMessageSourceFunctionThreadSafety:101 弱 join 注记核实（同文件 :163-164/:236-237 对照组有 isAlive 断言） | 测试自身缺陷：现状即在（未修复的测试健壮性缺陷） |

### 矩阵行抽查结果

- **零命中行（抽查 7/8，全部证实）**: 行 1（kafka close）、行 5（cep operator 线程）、行 6（DebeziumTableSplitEnumerator）、行 7（rocksdb 并发原语）、行 8（prune\|retention）、行 11（GlobalExecutors）、行 15（transaction.timeout）——独立 grep 全部零命中；行 10（C3-004 恢复路径）经测试文件核读证实枚举器恢复分配用例不存在。
- **会红行（行 3）**: `TestCepOperatorSubtaskCopy.java:43-44` 的 `assertSame(operator.getUserFunction(), copy.getUserFunction(), "CepOperator user function must be shared across subtasks")` 逐字属实，钉死共享 udf 设计，「会红（钉死缺陷）」判定成立。
- **条件会红行（行 2）**: 该测试仅 2 断言且 udf 与 nfaFactory 独立构造（:35-38），nfaFactory round-trip 单独落地不动 userFunction——「条件会红（仅合并修复时经 :43 反转）」说明准确。
- **假保护行（5/5 全部证实）**: 行 4（单线程顺序 detach 断言 + 独立 recorder）、行 8（台账写入存在性断言、保留面零断言）、行 9（全快速路径 + connect 拒连非挂起）、行 13（小规模 round-trip、规模零断言）、行 14（lift-all 钉载体 + kill 后快照回避窗口）——逐一读测试代码证实 superficially-pass 形态。
- **矩阵计数复核**: 15 行 = 会红 1 + 条件会红 1 + 假保护 5 + 无测试 8，与逐行判定一致；P0×1 + 全部 P1 缺陷族（对照 C1–C8 最终保留项：C1 P1×4、C2 P1×4、C3 P1×6、C4 P1×3、C5 P1×1、C6 P0×1+P1×1、C7 P0/P1 各×1、C8 P1×2，共 31 个 ID 归并 15 行）**无遗漏、无多余立案**。

### 时序敏感命中复核（4/4 证实）

1. C10-016 无界自旋（:250-251）逐字属实，且为全模块唯一无 deadline 的 while+sleep；
2. TestDebeziumCdcCheckpoint:288-289 回避注释逐字属实；
3. TestDebeziumResourceManagement:57/:59 catch-all 折叠逐字属实；
4. TestMessageSourceFunctionThreadSafety:101 join(5000) 无 isAlive 断言属实。

### 格式与引用一致性

- 16 条登记全部具备 13 个字段（关联缺陷/文件:行号/证据片段/严重程度/并发场景/触发时序（变异场景）/现状/风险/建议（测试形态）/失效面枚举/信心水平/误报排除/复核状态），无缺项。
- 全部关联缺陷编号与归并编号经对照 C1–C8 最终保留项逐一核实一致（含 C10-008 对 C7-006① 超时面的归属、C10-011 将 C4-006（P2）并入停止契约族的口径——与 C3「停止契约族三面并一项」裁定一致；C10-005/010 的受害者侧挂靠与 C2 最终表一致）。

### 复核注记（不计降级的两处盘点计数漂移）

1. §4.1「Thread.sleep 40 处」实际为 **39 处 / 18 文件**（文件数正确，处数差 1，计数口径漂移）；
2. §1.1「143 个 Test*」按文件名匹配为 146 个 Test*.java，其中 3 份 TestAwait 拷贝与 2 个 invariant 支撑类无 @Test（实际测试类 141）——「150 总数」精确，143/7 的拆分为统计口径差异。

两处均为盘点计数 ±1~2 的漂移，不影响任何判定与登记。

**判定统计: 保留 16 / 降级 0 / 驳回 0。**

## 最终保留项

| 编号 | 对应缺陷族（终裁级） | 建议测试形态（一句话） |
|---|---|---|
| C10-001 | C6-001（P0，kafka producer 无 close） | FakeProducer 覆写 close 记录调用，断言 sink 关闭链路传播 close、恰一次且幂等、recover/abort 不误关（先红后绿） |
| C10-002 | C1-002=C2-001=C6-004（P1，gauge JNI UAF） | 双线程压窗（scrape × open/close backend）断言窗口内 gauge 读数不抛 native 异常 + 白盒断言取参点位于锁内 |
| C10-003 | C1-001（P1，NFA 条件图共享变异） | 断言 copyForSubtask 后两 operator 的 NFA states 图实例不同 + Rich 条件对象读到本 subtask 的 keyedStateStore 探针（先红后绿） |
| C10-004 | C1-005（P1，SideCollector ctx 错路由） | 2 个共享 adapter operator 双线程并发超时，断言每条超时记录的发射 ctx 身份==触发 subtask、时间戳==该 subtask 时钟（先红后绿） |
| C10-005 | C2-003（P1，枚举器裸 LinkedHashMap） | CyclicBarrier 收敛并发 addReader × checkpoint 线程循环 snapshotState，断言无 CME 且每 reader 恰收一次完整 round-robin 份额（先红后绿） |
| C10-006 | C2-010（P1，REST 直调 snapshotState） | 任务线程持续写 × 线程 B 直调 snapshotState+cleanupExpiredEntries，断言无 CME/native 异常；修复后追加断言调用被 mailbox 编组（先红后绿） |
| C10-007 | C3-002 四向族（P1，台账只增不删） | 写 N 个历史 epoch 触发清理，断言活跃高水位行保留/历史行删除/幂等守卫仍 typed 拒绝 + 万级 epoch 行数上界参数化（先红后绿） |
| C10-008 | C3-003 五向族（P1，registry 无请求超时） | ServerSocket 挂死注入断言 encode 在配置超时内 typed 失败（先红后绿）+ 多线程同 subject 断言锁在超时内释放而非永久阻塞 |
| C10-009 | C3-004（P1，枚举器恢复双登记） | 构造恢复态 restoreState+addReader+start，断言每表恰出现在一个 reader 的交付中（先红后绿）+ 污染输入（恢复态含已分配表）断言去重 |
| C10-010 | C3-009=C4-004（P1，globalWorker 常驻） | hook 观察引擎 run 实际执行线程断言不落 globalWorker（先红后绿）+ 可控 executor 占满池断言新引擎提交 fail-fast/可观测排队而非静默深队列 |
| C10-011 | C3-014 停止契约族（P1，幽灵引擎） | 真实 wrapper 快速重启循环断言 stop 后引擎线程有界终态 + 同名 instance MBean 重注册成功 + stopped 守卫覆盖饱和池排队窗口（先红后绿） |
| C10-012 | C5-002=C8-005（P1，快照整库物化） | 10^4~10^5 键参数化 + 可测 hook 断言快照期间堆内同时驻留反序列化对象数 ≤ K（先红后绿） |
| C10-013 | C8-001（P1，offset 领先发射） | stub 投递 K 条不待消费即 snapshotState，断言快照 offset==last-emitted（非 last-received）、恢复重放恰为在途 K 条（先红后绿） |
| C10-014 | C8-006（P1，事务超时零裕度） | 断言 newProducer 产物含 transaction.timeout.ms ≥ checkpointTimeout×系数（先红后绿）+ checkpoint 超时×producer 超时耦合校验 fail-fast（对照 validateUnalignedConfig 先例） |
| C10-015 | C3-011（P2，重入守卫假 EOS） | 双线程并发 run 断言第二线程终态形态：typed 异常或真实进入循环投递（subscribeCount/engineCreated 增长），唯独不允许未订阅即正常返回 |
| C10-016 | 测试自身缺陷（无界自旋） | 改用 TestAwait.until（deadline 5s）等待首 2 条发射；附带补 TestMessageSourceFunctionThreadSafety:101 的 assertFalse(runner.isAlive()) |

（16 条复核状态行已逐条回填；本节为 append-only 追加，未改动前文任何判定内容。）
