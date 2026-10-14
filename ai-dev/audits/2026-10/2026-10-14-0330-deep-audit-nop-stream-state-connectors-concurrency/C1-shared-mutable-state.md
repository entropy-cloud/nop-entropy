# C1 共享可变状态与可见性 — nop-stream-state-connectors（第 1 轮初审）

> 审计域：并发·异步·资源·内存·长时运行，维度 C1（共享可变状态与可见性）。
> 审计对象：nop-stream-cep、nop-stream-rocksdb、nop-stream-connector、nop-stream-connector-batch、nop-stream-connector-jdbc、nop-stream-connector-debezium、nop-stream-connector-kafka、nop-stream-format-avro 共 8 个模块的 `src/main/java`（140 files / 25,458 行）。`_` 前缀生成文件不作为发现对象。
> 盲审纪律：本轮仅审计 live code，未读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下任何历史记录。
> 前置阅读：AGENTS.md、docs-for-ai/INDEX.md、docs-for-ai/03-modules/nop-stream.md、docs-for-ai/03-modules/nop-stream-connectors.md、_tmp/cra-baseline-nop-stream-state-connectors.txt。

## 0. 检查范围说明

按 C1 维度执行步骤，对 8 个模块逐类盘点了五类共享可变状态载体：

1. **static 可变字段**：全量 grep（`static` 非 final + `static final` 集合/工具类）后确认仅 1 处真静态可变容器：`FileTwoPhaseCommitSink.MANIFEST_JVM_LOCKS`（见 C1-004）。基线中其余 "static" 命中均为 static import / static 方法 / static 嵌套类，非可变状态。
2. **ThreadLocal**：8 个模块 main 源码中 **零 ThreadLocal**（含 NamedThreadLocal）——grep 证实，无需池化线程清理检查。
3. **全局注册表/缓存**：`StreamMetricsRegistries.registry()` 的两处使用（CepOperator 的 per-subtask tag counter——R5-CEP-03 修复后按实例隔离，正确；RocksDBMetricsRecorder 的 6 个 gauge——见 C1-002）。nop-stream-format-avro 的双缓存为实例级 ConcurrentHashMap（安全，见清单表）。
4. **singleton bean 实例字段**：8 个模块的组件均由 SPI 工厂 / `copyForSubtask` 每作业每 subtask 实例化，不经 NopIoC 单例容器；无 singleton 共享实例字段。共享面收敛在「编译产物跨 subtask 引用共享」（见 C1-001）与「基类 synchronizedMap 状态」（见清单表 §2）。
5. **隐式共享（参数/逃逸）**：这是本组模块的真实 C1 面，逐条核对了三条跨线程边界——
   - **任务线程模型**：`TaskProcessingTimeService` 的回调经 mailbox 投递、在任务线程执行（nop-stream-core，已核实其 javadoc 契约与实现一致），因此 CepOperator / 各 sink 的实例状态在「任务线程封闭」假设下成立；
   - **split 交付边界**：`LocalSourceCoordinator.ReaderChannel.deliver → onSplitsDelivered → reader.addSplits` 回调在**调用方线程**（coordinator/同伴 subtask 线程）直接执行，与读者任务线程并发——`FileSourceReader` 对此有 `synchronized(this)` 守卫，其余三个 FLIP-27 reader 没有（见 C1-003）；
   - **嵌入式引擎线程**：MessageSourceReader / DebeziumCdcSourceReader 的 daemon runner 线程与任务线程经 bounded queue + volatile 字段通信（Thread.start() happens-before 发布 queue；runnerFailure volatile）——核对无缺口。
   - **checkpoint 快照面（F1 对照）**：CepOperator.snapshotState 对 timer ledger 做 per-entry 拷贝（`typedCopy` + `new ArrayList<>`）；JDBC/File/Kafka sink 的 pendingCommits 经 `Collections.synchronizedMap` + `synchronized(pending)` 批拷贝；KafkaSourceFunction.snapshotState 与 run() 同任务线程（mailbox 安全点）——均无活引用逃逸。
6. **long/double 非原子读写 / ++ 复合递增**：`committedHighWater`（volatile long，原子读写）、`currentOffset`（BatchLoaderSourceFunction/Reader 均 volatile）、计数器均走 AtomicLong/ micrometer Counter——无裸 ++ 跨线程共享。
7. **F10 守卫对照**：声明"单写者"的组件均有运行时守卫：BatchConsumerSinkFunction（`validateParallelism` fail-fast + 描述符 SINGLE_INSTANCE 双层钉定）、BatchLoaderSource（`ParallelismCheckable` 部署期拒绝）、`TwoPhaseCommitSinkFunction.copyForSubtask` 基类默认对 subtask>0 抛 typed——守卫面完整。

**跨线程归属裁定依据**（引用，均为 live code，非历史记录）：`OperatorChain.deepCopy` 为引用拷贝不序列化（nop-stream-core `OperatorChain.java:252-259`）；`GraphExecutionPlan` / `RemoteGraphExecutionPlanBuilder` 逐 subtask `deepCopy(taskLocation)`；`TaskExecutor` 固定线程池每 subtask 一线程并发执行。nop-stream-core 自身不在本轮 8 模块发现范围内，上述引用仅用于判定 8 模块内代码的可达线程交错。

## 1. 共享可变状态清单表（字段 × 写者 × 读者 × 同步机制 × 判定）

| # | 字段/载体（模块:类） | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| S1 | `CepOperator.registeredEventTimeTimersByKey` / `reconciledTimerKeys` / `currentWatermark` / `capturedKeyClass`（cep） | 任务线程（processElement/processWatermark/onProcessingTime） | 同一任务线程（snapshotState/drain） | mailbox 任务线程封闭（TaskProcessingTimeService 回调经 mail 投递） | ✅ 封闭成立 |
| S2 | `NFAFactoryImpl.states` / `windowTimes` → `State`/`StateTransition`/condition 对象（cep） | 各 subtask 的 `NFA.open()/close()`（setRuntimeContext/open/close 变异） | 全部 subtask 的 `NFA.process` 条件求值 | **无**（引用共享 + 非 volatile 可变字段） | ❌ **C1-001（P1）** |
| S3 | `SharedBuffer.eventsBufferCache`/`entryCache`（Guava Cache）（cep） | 任务线程（write-through） | 任务线程 + cache-stats 定时回调（亦任务线程） | Guava Cache 线程安全 + 线程封闭 | ✅ |
| S4 | `RocksDBKeyedStateBackend.cachedStorageKey` 单槽缓存、`states`/`stateTypes`/`cfHandles`/`restoreAggregateFunctions`（rocksdb） | 任务线程 | 任务线程 | 任务线程封闭（javadoc 声明 + 核对一致） | ✅ |
| S5 | `RocksDBMetricsRecorder.dbRef`（rocksdb） | 任务线程 `close()`（set null） | ops scrape 线程（gauge 闭包） | AtomicReference + synchronized —— 但 **get 在锁外** | ❌ **C1-002（P1）** |
| S6 | `FileTwoPhaseCommitSink.MANIFEST_JVM_LOCKS`（connector） | commit 路径各 subtask 线程 | 同左 | ConcurrentHashMap.computeIfAbsent + ReentrantLock + FileLock（jvmLock→fileLock 固定序） | ✅ 互斥正确；注册表本身无淘汰 → **C1-004（P3）** |
| S7 | `FileTwoPhaseCommitSink.currentBuffer`（volatile List）（connector） | invoke（任务线程） | saveState/rollback（任务线程）+ close | `synchronized(currentBuffer)` 单一监视器 | ✅ |
| S8 | `FileTwoPhaseCommitSink.charset`（volatile，DCL）（connector） | charset() 惰性解析 | 全部写路径 | volatile + synchronized DCL | ✅ |
| S9 | `TwoPhaseCommitSinkFunction.pendingCommits`（synchronizedMap）（core 基类，8 模块内 3 sink 消费） | saveState（任务线程）/commit/abort（commit 通知线程） | finishCommit/restoreFromEpoch 批迭代 | synchronizedMap 互斥量 + 迭代前 `synchronized(pending)` 批拷贝（基类 RL-4） | ✅（使用方核对无锁外迭代） |
| S10 | `JdbcTwoPhaseCommitSink.initialized/dialect/insertDataSql/insertLedgerSql/ledgerExistsSql/keyedColumns/upsertSql/deleteSql`（jdbc） | beginTransaction/saveState/commit 首用线程 | commit 线程 | 全部 volatile + synchronized(this) DCL | ✅ |
| S11 | `KafkaSourceFunction.restoredOffsets`（volatile TreeMap）（kafka） | run() markConsumed / initializeState | snapshotState / run() | volatile 引用 + 同任务线程（mailbox 安全点 collect 处排空 mail） | ✅ |
| S12 | `KafkaTwoPhaseCommitSink.producer/transactionOpen/initDone/committedHighWater`（全 volatile）（kafka） | invoke/saveState/rollback/recover（任务线程） | commit（通知线程，仅读 committedHighWater） | volatile（long 读原子） | ✅ |
| S13 | `MessageSourceFunction`（running/failed/pendingError/subscription/shutdownLatch 全 volatile + local 订阅引用）（connector） | run（任务线程）/订阅回调线程/cancel | 各自 | volatile + CountDownLatch + 1s 有界轮询；R5-CON-04 local-ref 契约 | ✅ |
| S14 | `MessageSourceReader.queue/runnerThread/runnerFailure`（connector） | launchRunner（任务线程，Thread.start() 发布）/ runner 线程写 runnerFailure | runner 线程 / 任务线程 pollNext | happens-before（thread.start）+ volatile | ✅ |
| S15 | `MessageSourceReader.token` / `DebeziumCdcSourceReader.adoptedSplits`、`restoredOffsetByTable` / `BatchLoaderSourceReader.pendingSplit/started/requestedAssignment`（connector/debezium/batch） | addSplits/restoreState（**交付回调线程** = coordinator/同伴 subtask 线程） | pollNext/snapshotState/start（读者任务线程） | **无**（普通字段/LinkedHashMap，无 volatile 无锁） | ⚠️ **C1-003（P2，后果有界）** |
| S16 | `DebeziumCdcSourceFunction`（running/draining/pendingError/completionLatch/subscription/source 全 volatile + runEntered AtomicBoolean）（debezium） | run/cancel/truncateForDrain | 引擎回调线程 | volatile + latch + CAS 重入守卫 | ✅ |
| S17 | `BatchConsumerSinkFunction.buffer`（ArrayList）（batch） | consume/flush/close（单任务线程） | 同左 | **契约声明单写者** + validateParallelism fail-fast 守卫（F10 形态） | ✅（已声明边界 + 守卫，按误报校准不立项） |
| S18 | `AvroWireFormat.writerSchemaIds` / `AvroSchemaRegistryClient.schemaByIdCache/latestBySubject`（avro） | encode/decode 首用线程 | 同类 | ConcurrentHashMap；CachedSchema 为 immutable record 经 CHM put 安全发布 | ✅ 正确性；`computeIfAbsent` 内持锁做 HTTP 注册（同 subject 编码串行 + bin 锁内 I/O）→ **移交 C2/C9 维度**，非 C1 |
| S19 | `FileSplitEnumerator.discoveredFiles/assignedFiles/finishedFiles/splitById`（并发集合）（connector） | enumerator 方法 | 同左 | ConcurrentHashMap/newKeySet + coordinator `enumeratorLock` | ✅ |
| S20 | `MessageTopicSplitEnumerator`/`DebeziumTableSplitEnumerator`/`BatchLoaderSplitEnumerator` 实例集合（含 `computeIfAbsent(subtask, k -> new ArrayList<>())`） | enumerator 方法 | 同左 | coordinator 单线程（enumeratorLock）封闭 | ✅ |

## 2. 发现条目

### [nop-stream-state-connectors-C1-001] CEP：NFAFactory 跨 subtask 共享编译态 State 图，Rich 条件对象被每个 subtask 的 NFA.open()/close() 原地变异（runtimeContext last-writer-wins）

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:1097-1121`；`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:193-213`；`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/pattern/conditions/RichIterativeCondition.java:44-53`；`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/pattern/conditions/RichCompositeIterativeCondition.java:53-59`；`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:328-339`
- **证据片段**:
  ```java
  // NFACompiler.NFAFactoryImpl（每个 subtask 的 NFA 共享同一 states 集合与 windowTimes）
  public NFA<T> createNFA() {
      return new NFA<>(states, windowTimes, windowTime, timeoutHandling);
  }
  // NFA.open()（每个 subtask 的 CepOperator.open() 都会对【同一批】State 持有的条件对象执行）
  public void open(RuntimeContext cepRuntimeContext, Configuration conf) {
      for (State<T> state : getStates()) {
          for (StateTransition<T> transition : state.getStateTransitions()) {
              IterativeCondition<T> condition = transition.getCondition();
              FunctionUtils.setFunctionRuntimeContext(condition, cepRuntimeContext); // 原地覆盖
              FunctionUtils.openFunction(condition, conf);                            // 重复 open
  // RichIterativeCondition：被覆盖的共享可变字段
  private transient RuntimeContext runtimeContext;
  public void setRuntimeContext(RuntimeContext runtimeContext) {
      this.runtimeContext = runtimeContext;   // 非 volatile，last-writer-wins
  ```
- **严重程度**: P1
- **并发场景**: 同 JVM 并行部署（parallelism ≥ 2，`OperatorChain.deepCopy` 为引用拷贝不序列化，`CepOperator.copyForSubtask()` 原样传递同一 `nfaFactory` 引用；`GraphExecutionPlan`/`RemoteGraphExecutionPlanBuilder` 逐 subtask `deepCopy(taskLocation)` 均走同一路径）；每个 subtask 运行在 `TaskExecutor` 固定线程池的独立线程上，各自在 `open()` → `initNfa()` → `nfa.open()` 中触碰同一批 State 图内嵌的条件对象。
- **触发时序**: T0（stream-task-executor-0 线程）执行 `CepOperator(subtask0).open()` → `nfa.open(ctx0)` 到 `RichIterativeCondition.setRuntimeContext(ctx0)`（共享条件对象 C.runtimeContext=ctx0）→ T1（stream-task-executor-1 线程）执行 `CepOperator(subtask1).open()` → 对**同一个** C 执行 `setRuntimeContext(ctx1)`（C.runtimeContext=ctx1，且 C.open() 第二次执行）→ 此后 T0 的每一条记录在 `NFA.checkFilterCondition → condition.filter` 中调用 `getRuntimeContext()` 时**持久地**读到 ctx1（subtask1 的 CepRuntimeContext，绑定 subtask1 的 keyedStateStore 与 subtask 坐标）——不是瞬时竞态而是作业生命周期的持续错配；同理任一 subtask 先 `close()` 会对全部 subtask 共享的条件执行 closeFunction。open 交错发生在启动窗口（TaskExecutor 同时提交全部 subtask），后 open 者覆盖先 open 者。
- **现状**: `CepOperator.copyForSubtask()` 的 javadoc 把 "NFA factory" 归入 "(all immutable configuration) are shared across subtasks"——该不变式对 Rich 条件不成立：`NFA.open()/close()` 是对共享对象的写路径，`RichIterativeCondition.runtimeContext` 是非 volatile 可变字段，`RichCompositeIterativeCondition.setRuntimeContext` 还会把覆盖传播到全部嵌套条件。
- **风险**: 触达面是主流模式形态——`Pattern.where().where()` 链与 `.subtype()` 在 `Pattern.java:200/240` 生成 `RichAndCondition`，一切 NOT（notNext/notFollow）条件在 `NFACompiler.java:355/367/369` 包成 `RichNotCondition`：即"并行 CEP + where 链或 NOT"的生产部署全部命中。后果：(a) 用户 Rich 条件经 `getRuntimeContext()` 读到**别的 subtask** 的 keyedStateStore/子任务坐标——过滤判定读错键控状态 = 静默语义损坏；(b) Rich 条件的 `open()` 被执行 N 次（资源重复分配）；(c) 一个 subtask 的 `close()` 连带关闭所有 subtask 的条件资源。
- **建议**: `NFAFactoryImpl.createNFA()` 对 `states` 做深拷贝（对每个 State/StateTransition/条件做序列化 round-trip 拷贝，模式已具备 `serializationRoundTripCopy` 同款手段），使条件对象随 subtask 私有；或将 `NFA.open/close` 改为只对"本 NFA 实例新建的条件包装"执行生命周期（共享图只读化）。最小修：`CepOperator.copyForSubtask()` 对 `nfaFactory` 做序列化 round-trip 拷贝（NFAFactory 已是 Serializable），一行收口。复核 Rich 生命周期契约可参照 nop-stream-core `FunctionUtils` 的既有语义。
- **失效面枚举**（F3 同族清扫）: 共享编译图上的全部运行期写路径逐一登记——① `NFA.open()` 的 `setFunctionRuntimeContext`+`openFunction`（本条主体，所有 State×所有 transition 的条件）；② `NFA.close()` 的 `closeFunction`（同面，交叉关闭兄弟 subtask）；③ `RichCompositeIterativeCondition.setRuntimeContext/open/close` 对嵌套条件的传播（放大面 ①②，非独立缺陷）；④ `NFA.windowTimes`（同被共享的 HashMap）——运行期只读（`getWindowTimes()` 返回 unmodifiable 包装），经 final 字段 + 线程池 submit 安全发布，**无缺陷**；⑤ `State/StateTransition` 图结构本体——编译期构建后运行期无写点（grep 证实），**无缺陷**；⑥ 同模式被多作业复用时同一 factory 再跨作业共享——与 ①② 同根因，修复 ① 即覆盖。
- **信心水平**: 确定
- **误报排除**: 不属于"无状态单例被并发调用"——条件对象有被写路径变异的可变字段；不属于"平台基础设施自身不重审"——缺陷代码（NFACompiler/NFA/Rich*Condition）全部位于审计对象 nop-stream-cep 内；不属于低频管理路径——`open()` 是每次并行部署的必经路径，filter 求值是每记录热路径。
- **复核状态**: 已保留（P1，2026-10-14 复核：引用共享前提链逐环验证成立——GraphExecutionPlan.java:454 与 RemoteGraphExecutionPlanBuilder.java:313 均对同一 original 逐 subtask deepCopy(taskLocation)，CepOperator.copyForSubtask(:329-339) 引用透传 nfaFactory，NFAFactoryImpl.createNFA(:1119-1121) 共享同一 states，NFA.open(:193-204) 变异共享条件，RichIterativeCondition.runtimeContext 非 volatile。一处修正：cepRuntimeContext 的 subtask 身份恒为硬编码 0/1（r6 CEP-03 已知未修复残余），「读到别的 subtask 坐标」成分退化；但 initNfa(:606-617) 的 CepRuntimeContext 绑定 per-operator keyedStateStore，跨 subtask 泄漏键控状态的主后果成立。现状：二轮复核修正为「已知未修复」——r4（ai-dev/audits/2026-09/2026-09-30-0530-deep-audit-nop-stream-quality-r4/01-cep-correctness.md）R5-CEP-05（P3）已登记同一机制（copyForSubtask 按引用共享 nfaFactory、NFA open/close 跨子任务竞争驱动 Rich 条件、runtimeContext last-write-wins、open N 次、交叉 close，修复建议同为 nfaFactory 序列化深拷贝），r5/r6 未结转、git log 无修复提交、live code 二轮复核仍未修复；一轮复核的「新发现」系漏查 r4 所致。判级维持 P1（本轮并发审计口径「罕见但可具体化竞态」，触发时序已具体化到行级；r4 的 P3 系质量审计旧口径，其自评危害处于潜伏态））

### [nop-stream-state-connectors-C1-002] RocksDB：metrics gauge 闭包在 synchronized 之外取 `dbRef.get()`，close 窗口内 scrape 线程可对已关闭 native handle 调 getAggregatedLongProperty（native use-after-free）

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java:83-101`（`RocksDBKeyedStateBackend.java:978-990` 的 close 顺序依赖此处配对成立）
- **证据片段**:
  ```java
  private void gauge(MeterRegistry registry, String meterName, String property) {
      registry.gauge(meterName, Tags.empty(), dbRef, ref -> readProperty(ref.get(), property));
  }                                                          // ^^^^ ref.get() 在进入 synchronized 前求值
  public synchronized double readProperty(RocksDB db, String property) {
      if (db == null) { return Double.NaN; }                  // null 守卫只挡"已置空后"的 scrape
      try { return db.getAggregatedLongProperty(property); ... // 对已 close 的 handle = native UAF
  ```
- **严重程度**: P1
- **并发场景**: ops HTTP 服务器的 Prometheus scrape 线程（`/metrics` 端点求值 gauge）vs 任务线程的 `RocksDBKeyedStateBackend.close()`（区域恢复/作业取消/重启路径会关闭旧 backend 并新建）。gauge 注册在进程级 `StreamMetricsRegistries` 组合注册表，backend 关闭后仍保持注册（micrometer 弱引用仅弱持 state 对象，recorder 由 backend 字段强持到 close）。
- **触发时序**: T1（scrape 线程）求值 gauge → 参数表达式 `ref.get()` 返回**存活**的 `RocksDB@X`（此刻在 `readProperty` 的 synchronized 之外）→ T2（任务线程）进入 `backend.close()`：`metricsRecorder.close()`（synchronized，`dbRef.set(null)`，释放锁）→ 继续关闭 cfHandles 并 `db.close()`（native 句柄释放）→ T1 此时才进入 `readProperty(RocksDB@X, prop)`：monitor 已空闲、`db != null` 通过守卫 → `db.getAggregatedLongProperty(prop)` 作用在已释放的 native handle 上。窗口宽度 = `ref.get()` 求值与 native 属性读取之间（含进入 monitor 的时间），scrape 周期性发生 × checkpoint 驱动的 backend 重建（恢复/重部署必然发生）→ 长时运行下窗口可具体命中，后果是该进程 SIGSEGV（类 javadoc 自认："reading aggregated properties on a closed handle crashes the JVM in native code"）。
- **现状**: `close()` 与 `readProperty` 互为 synchronized 的配对是设计意图（两处 javadoc 均声明"sync so an in-flight scrape cannot race the detach"），但 db 引用在**进入监视器之前**已被闭包取出，synchronized 只互斥了方法体，挡不住"取旧引用 → close → 用旧引用"的交错。
- **风险**: 稳定 scrape 的生产部署（owner doc「指标采集速查」标准形态）叠加任意一次任务重启/区域恢复即可能进程崩溃——比数据损坏更早发生的可用性事故；同时使 `RocksDBKeyedStateBackend.close()` 精心编排的"先摘 gauge 后关 native"顺序失去保证。
- **建议**: 把取值移进监视器：新增 `private synchronized double readCurrent(String property) { return readProperty(dbRef.get(), property); }`，gauge 闭包改调 `recorder::readCurrent`（依赖既有 close 顺序：monitor 内读到非 null 时 native 尚未关闭；读到 null 返回 NaN）。或等价地在 `readProperty` 内改为消费 AtomicReference 并对 `RocksDBException`/`IllegalStateException` 兜 NaN。无需引入新锁。
- **失效面枚举**（F3 同族清扫）: ① 全模块 gauge/跨线程读 native 点 grep：仅此一处 gauge 注册（`rocksdb` 模块 `registry.gauge` 唯一命中），无兄弟未修；② `RocksDBSnapshotSerDe`/`RocksDBIncrementalSnapshotStrategy`/`copyColumnFamilyRange` 对 db 的使用全部在任务线程 restore/snapshot 路径（与 close 同线程序列化，无并发窗口）；③ 同族模式横向 grep：cep/connector 族无"close native + 跨线程 metric"组合（CepOperator 的 counter 只从任务线程 increment，micrometer counter 无 native 引用）；④ `getDbForTest()/getDb()` 测试与包内访问点——非生产路径，不适用。
- **信心水平**: 确定
- **误报排除**: 不属于"原语使用不是问题"——这是复合操作（取引用→关闭→用引用）误用 synchronized 的典型形态；不属于有界声明缓存/低频路径——scrape 是周期性的，close 是恢复路径的必经操作；"平台 nop-kernel 设施不重审"不适用（缺陷在本模块类内）。
- **复核状态**: 已保留（P1，2026-10-14 复核：live code 证实 gauge 闭包 `ref -> readProperty(ref.get(), property)` 的 ref.get() 在进入 synchronized 之前求值；RocksDBKeyedStateBackend close 顺序 recorder.close()(:989)→db.close()(:1013) 且 gauge 无摘除点，「取旧引用→close→用旧引用」交错成立，null 守卫与 synchronized 均不可达该窗口。窗口窄但后果 SIGSEGV、javadoc 自认 crash 风险、scrape 周期性 × 恢复必经 close，触发序列具体。现状：新发现）

### [nop-stream-state-connectors-C1-003] FLIP-27 reader 族 addSplits 跨线程守卫不对称：Message/Debezium/Batch 三个 reader 的分片状态无同步无 volatile，与读者任务线程存在可见性竞态（后果自愈或受 recovery=NONE 声明边界兜底）

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceReader.java:59-63,108-127,130-160,181-183`；`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceReader.java:68-71,160-193,254-279`；`nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceReader.java:66-69,96-111,122-152`；对照已守卫兄弟 `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSourceReader.java:166-178`
- **证据片段**:
  ```java
  // MessageSourceReader —— token 普通字段：交付线程写，任务线程读，无 volatile 无锁
  private transient MessageTopicSplit token;                 // :62
  public void addSplits(List<MessageTopicSplit> splits) {    // :108 交付回调线程执行
      ...
      if (this.token == null) { this.token = newToken; if (running) launchRunner(); return; }
  // DebeziumCdcSourceReader —— LinkedHashMap：交付线程 put，任务线程 iterate/isEmpty
  private final Map<String, DebeziumTableSplit> adoptedSplits = new LinkedHashMap<>();         // :68
  private final Map<String, Map<String, String>> restoredOffsetByTable = new LinkedHashMap<>();// :69
  // 对照：FileSourceReader 的同入口有守卫（证明跨线程模型真实）
  public void addSplits(List<FileSplit> splits) { synchronized (this) { assignedSplits.addAll(splits); ... } }
  ```
- **严重程度**: P2
- **并发场景**: split 交付回调 `LocalSourceCoordinator.ReaderChannel.deliver → drainPending → onSplitsDelivered → reader.addSplits` 在**调用方线程**执行：并行度 ≥ 2 时即同伴 subtask 的任务线程（其 pollNext → requestSplits → handleSplitRequest → assignSplits → channel.deliver 链），或执行 `startEnumerator` 的首读者任务线程——与目标 reader 的任务线程（pollNext/snapshotState/start）并发。无共同锁、无 volatile，JMM 无 happens-before 边。
- **触发时序**: T1（同伴 subtask 线程）执行 `MessageSourceReader.addSplits` 写 `this.token = newToken`（普通写）→ T2（读者任务线程）此刻在 `snapshotState` 读 `token` 读到陈旧 null → checkpoint 持久化空 token 列表（后果：恢复后订阅 token 丢失、reader 重新 requestSplits——由 owner doc 声明的 recovery=NONE / "token 不承载遍历真相"语义兜底，重投递行为归后端）；同形交错下 `pollNext` 读到陈旧 `token==null` → 多发一次 requestSplits / `queue==null` 兜底 launch 自愈。Debezium reader：T1 `adoptedSplits.put` 与 T2 `pollNext.isEmpty()`/`snapshotState` 迭代的交错最坏可产生陈旧读或（理论上）CME，但该 reader 的 D-4 守卫使"首发交付后新增 put"路径被 typed 拒绝，实际可交错窗口收敛到首发交付与首次快照之间——首轮快照时 T2 对空 map 返回 `List.of()` 不迭代，迭代分支要求 map 非空而此时交付已完成。Batch reader 同形（`pendingSplit`/`started` 陈旧读由 `pollNext` 的防御性 adopt 路径自愈）。**上述交错均写不出"持久损坏"级后果**——按证据标准降 P2 并标注：理论风险，后果未具体化为数据损坏。
- **现状**: 四个 FLIP-27 reader 中仅 `FileSourceReader` 对 `addSplits/snapshotState/restoreState/close/pollNext` 使用统一 `synchronized(this)`（owner doc AR-15-② 亦钉定该形态）；其余三个 reader 的交付面字段裸奔，属同族缺陷的不对称残留。
- **风险**: 现阶段后果被"自愈轮询 + recovery=NONE 声明边界"限死（多发一次请求、丢一个不承载真相的 token、一次延迟启动）；风险在于**演化**：任何未来把 token/adoptedSplits 赋予遍历语义的改动（如 debezium offset map 按 split 携带）都会把该竞态升级为真实损坏，且无编译期信号。
- **建议**: 收敛到 FileSourceReader 已验证的形态：三个 reader 的 `addSplits`（及其触达字段）与 `snapshotState/pollNext` 统一到同一实例监视器（或最小改动：`token`/`pendingSplit` 加 volatile、`adoptedSplits/restoredOffsetByTable` 换 `Collections.synchronizedMap`/CHM 并保持 put-then-iterate 串行）。改动局部、无跨模块面。
- **失效面枚举**（F3 同族清扫）: ① `FileSourceReader.addSplits` —— **已守卫**（synchronized(this)，正确）；② `MessageSourceReader.addSplits` —— 未守卫（本条）；③ `DebeziumCdcSourceReader.addSplits` —— 未守卫（本条）；④ `BatchLoaderSourceReader.addSplits` —— 未守卫（本条）；⑤ 三个 legacy 函数族 `MessageSourceFunction`/`DebeziumCdcSourceFunction`/`KafkaSourceFunction` —— 全 volatile 字段 + latch/CAS，**无缺陷**；⑥ enumerator 侧（`FileSplitEnumerator`/`MessageTopicSplitEnumerator`/`DebeziumTableSplitEnumerator`/`BatchLoaderSplitEnumerator`）—— coordinator `enumeratorLock` 线程封闭 + 并发集合，**无缺陷**；⑦ `SourceReaderOperator` 的 preOpenSplits 缓冲（core，context 引用）——不在对象范围。
- **信心水平**: 很可能（竞态存在=确定；后果等级=很可能有界）
- **误报排除**: 不属于"无状态单例并发调用"——token/adoptedSplits 是真实可变状态且写者跨线程；不按 P1 报是因为当前写出的交错后果均可自愈或被 owner doc 声明的 recovery=NONE 边界吸收（校准规则：写不出实质损坏时序 → 降级并标注）；FileSourceReader 的守卫形态证明这不是"设计意图上的单线程假设"，而是守卫缺口。
- **复核状态**: 已保留（P2，2026-10-14 复核：LocalSourceCoordinator.ReaderChannel.drainPending → cb.accept 证实交付回调在调用方线程同步执行；token(:62)/adoptedSplits(:68-69)/restoredOffsetByTable/pendingSplit(:66) 均普通字段，FileSourceReader 同入口 synchronized(this) 对照成立。字段清单小误不影响主体：BatchLoaderSourceReader.started 实为 volatile(:58)，但 split 载体 pendingSplit 非 volatile，竞态成立。后果自愈/recovery=NONE 吸收，原文已自标理论风险，P2 恰当。现状：新发现）

### [nop-stream-state-connectors-C1-004] FileTwoPhaseCommitSink.MANIFEST_JVM_LOCKS 进程级静态锁注册表只增不删

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:96-99,389-391`
- **证据片段**:
  ```java
  /** AR-14: intra-JVM manifest locks keyed by canonical output directory. */
  private static final ConcurrentHashMap<String, ReentrantLock> MANIFEST_JVM_LOCKS =
          new ConcurrentHashMap<>();
  ...
  ReentrantLock jvmLock = MANIFEST_JVM_LOCKS.computeIfAbsent(
          outputDirPath.toAbsolutePath().normalize().toString(), k -> new ReentrantLock());
  ```
- **严重程度**: P3
- **并发场景**: 不涉及竞态——互斥本身正确（computeIfAbsent 原子 + jvmLock→FileLock 固定锁序）。本条是 C7 长时运行口径下借 C1 清单登记的**单调增长源**：静态 map 无任何 remove/淘汰路径。
- **触发时序**: 不适用（无交错时序；增长条件 = 进程生命周期内出现过的不同输出目录规范路径数）。长驻多作业形态（OpsJobManager 周期性受理每作业独立 outputDir 的 file sink 作业）下每作业永久驻留一个 ReentrantLock（约几十字节）。
- **现状**: 键空间 = 作业提交历史中出现过的输出目录集合，增长速率受作业提交数约束，单条目开销可忽略；同目录重复提交复用同一条目。
- **风险**: 万级作业/数年级长驻进程下累积至可观测但无害的内存占用；无正确性影响（锁永不被错误复用——键为规范绝对路径）。
- **建议**: 不立项修复；若未来做 C7 治理，可在 sink close/abort 收尾路径对"本进程不再有任何活跃 sink 指向该目录"做引用计数摘除，或保留现状并在 javadoc 标注"进程级、随目录数缓增、有意不淘汰"。
- **失效面枚举**（F3 同族清扫）: 8 模块全量 grep static 非 final / static final 集合：`MANIFEST_JVM_LOCKS` 是唯一真静态可变容器；`NopStreamOffsetBackingStore.REGISTRY`（debezium 消费的静态 offset 注册表）位于 nop-message-debezium（本轮范围外），其在范围内的全部触点（`newFreshOffsetStore` 的 AR-03 clearConnector、实例名隔离、failure-listener 的 register/unregister 对称 finally）核对为**正确使用**，注册表本身若需治理属该模块维度。
- **信心水平**: 确定
- **误报排除**: 不按 P1"长时运行必然触发的无界增长"报——增长上界=不同输出目录数、单条目常量级，不构成 owner 规模包络内的实质风险；不报互斥正确性问题（computeIfAbsent+固定锁序经核对无缺陷）。
- **复核状态**: 已保留（P3，2026-10-14 复核：live code 证实静态 CHM 仅 :390 一处 computeIfAbsent、无任何 remove/淘汰路径，互斥正确性成立；非竞态缺陷，键空间有界，信息性保留不立项。现状：新发现——core-runtime 并发审计 C2 仅核过该锁的锁序，未登记增长面）

## 3. 已核对无缺陷的重点面（避免复核轮重复排查）

| 面 | 结论 |
|---|---|
| CepOperator 任务线程封闭 | ✅ 处理时间回调经 mailbox 在任务线程执行（TaskProcessingTimeService 契约与实现一致）；timer ledger 快照逐条深拷贝，无 F1 活引用逃逸 |
| CepOperator 迟到计数器 | ✅ R5-CEP-03 修复后 per-instance tag，无跨实例计数污染 |
| FileTwoPhaseCommitSink 2PC 全路径 | ✅ currentBuffer 单监视器、charset volatile DCL、manifest jvmLock→FileLock 固定序、fsync/ATOMIC_MOVE 链与本维度无关处无共享状态缺口 |
| JdbcTwoPhaseCommitSink | ✅ 4 组惰性 SQL/标志全 volatile + synchronized DCL；currentBuffer 单监视器；pendingCommits 经基类 synchronizedMap |
| KafkaSourceFunction / KafkaTwoPhaseCommitSink | ✅ 全 volatile 状态面；committedHighWater volatile long 原子读；snapshotState 与 run 同任务线程 |
| MessageSourceFunction / DebeziumCdcSourceFunction | ✅ volatile + latch + local-ref 订阅契约 + runEntered CAS 重入守卫 + 失败监听器 register/unregister 对称 finally |
| 嵌入式 runner 线程（Message/Debezium reader） | ✅ queue 经 Thread.start() happens-before 发布；runnerFailure volatile；close join 有 5s typed 超时 |
| BatchConsumerSinkFunction / BatchLoaderSource(Reader) | ✅ 单写者契约有 validateParallelism fail-fast 守卫（F10 形态）；offset volatile（AR-10） |
| nop-stream-format-avro 缓存 | ✅ 正确性（CHM + immutable record 安全发布）；`computeIfAbsent` 内持 HTTP I/O 与 `latestBySubject` 不感知换版（scope 缓存边界）移交 C2/C9 与已声明边界口径 |
| RocksDBKeyedStateBackend 本体 | ✅ 任务线程封闭声明与实际触点一致；`RocksDB.loadLibrary()` 静态初始化安全；close 顺序正确（其保障被 C1-002 的 gauge 旁路击穿） |

## 4. 覆盖声明

- 逐文件通读：CepOperator(1510)、NFA(1153)、NFACompiler(关键节)、SharedBuffer(582)、RocksDBKeyedStateBackend(1068)、RocksDBMetricsRecorder(102)、FileTwoPhaseCommitSink(699)、FileSourceReader(495)、MessageSourceReader(264)、MessageSourceFunction(223)、DebeziumCdcSourceFunction(687)、DebeziumCdcSourceReader(362)、KafkaSourceFunction(419)、KafkaTwoPhaseCommitSink(323)、BatchLoaderSourceReader(238)、BatchConsumerSinkFunction(214)、AvroWireFormat(130)、AvroSchemaRegistryClient(130)。
- 定向核查（grep + 通读）：8 模块 static 可变字段全量、ThreadLocal 全量（0 命中）、Rich 条件族、NFAState/DeweyNumber/ComputationState（值对象/不可变）、PatternStream/PatternStreamBuilder/各 ConnectorFactory/各 SplitEnumerator/各 Serializer（不可变配置或线程封闭）、FileSplitEnumerator、BatchLoaderSourceFunction。
- 跨线程归属裁定引用的 core/runtime 文件（仅作上下文，非发现对象）：TaskProcessingTimeService、TaskExecutor、OperatorChain.deepCopy、GraphExecutionPlan、RemoteGraphExecutionPlanBuilder、LocalSourceCoordinator.ReaderChannel、SourceReaderOperator、TwoPhaseCommitSinkFunction、NopStreamOffsetBackingStore。
- 未覆盖说明：`_gen/` 生成类（_CepPatternModel 等）按纪律排除；nop-stream-core/runtime 自身的并发缺陷（如 ReaderChannel 回调不做 mailbox 投递的架构选择）留待对应模块维度。

## 深挖第 2 轮追加

> 维度：C1（共享可变状态与可见性），模块组 nop-stream-state-connectors（nop-stream-cep、nop-stream-rocksdb、nop-stream-connector、nop-stream-connector-batch、nop-stream-connector-jdbc、nop-stream-connector-debezium、nop-stream-connector-kafka、nop-stream-format-avro）。
> 盲审边界：本轮仅读取第 1 轮自身文件（C1-shared-mutable-state.md）+ live code，未读取其他审计目录、plans、bugs、lessons。
> 与第 1 轮关系：不重复第 1 轮已覆盖的 18 个逐文件通读对象与 10 组「已核对无缺陷」面；本轮专攻其覆盖声明之外的盲区——未盘点文件/包（adapters、SharedBufferAccessor、NFAStateNameHandler、rocksdb state 族、jdbc/debezium/batch 辅助类）、format-avro 与 connector-batch 的对象逃逸面、发布安全（构造器泄漏 this）。

## 0. 本轮检查范围（第 1 轮未盘点的文件/包）

- **cep**：`functions/adaptors/` 全部 4 个 adapter、`PatternProcessFunctionBuilder`、`nfa/sharedbuffer/SharedBufferAccessor`、`nfa/compiler/NFAStateNameHandler`、`model/builder/EvalFunctionCondition`、`pattern/Quantifier`、`operator/StreamRecordComparator`、`operator/CepRuntimeContext`、`time/`、`FilePendingCommit`（connector）。
- **rocksdb**：`AbstractRocksDBState` + 8 个 state 类（Value/List/Map/Aggregating/Reducing/Internal*）、`RocksDBKeyEncoder`、`RocksDBSnapshotSerDe`、`RocksDBValueSerDe`、`RocksDBStateBackend`、`incremental/`（Restore/SnapshotStrategy）、`RocksDbTtlAware`。
- **connector 族辅助**：`MessageSinkFunction`、`BatchLoaderSource`（全文）、`BatchLoaderSplitEnumerator`、各 EnumeratorState POJO、`DebeziumCdcSource`（全文）、`DebeziumSourceSupport`、`CdcEventOps`、`JdbcTwoPhaseCommitSinkBuilder`、`JdbcTwoPhaseCommitSinkConnectorFactory`、Kafka 两个 factory。
- **专项**：发布安全 grep（构造器内 new Thread / 注册监听器 / `new X(this)` this 逃逸）、getter 泄漏内部集合、非 final 实例字段全量清单（8 模块，排除 `_gen/`）。
- **方法**：对每个新盘点载体回答「谁写 × 谁读 × 同步机制」；跨线程项写出具体交错序列；同族缺陷按 F3 全量 grep 清扫。

**结构性事实（本轮核实，两条新发现的共同前提）**：CEP 算子的用户函数走「job-graph 模板 + 引用拷贝」模型——`PatternStream.select/flatSelect/process` 在 client 侧经 `PatternProcessFunctionBuilder` 创建**单个** adapter 实例（内嵌用户函数）→ 构成 `CepOperator` 的 udf → 部署期 `CepOperator.copyForSubtask()`（`CepOperator.java:329-339`）**原样传递 `getUserFunction()` 引用**，javadoc（:322-328）将 "user process function" 归入 "(all immutable configuration) are shared across subtasks"；而每 subtask 的 `AbstractUdfStreamOperator.open()`（nop-stream-core `operators/AbstractUdfStreamOperator.java:74-77`，仅作归属裁定上下文）都会对**同一共享实例**执行 `setFunctionRuntimeContext` + `openFunction`。connector 族 sink/source 则相反：`JdbcTwoPhaseCommitSink.copyForSubtask(int)`(:385)、`DebeziumCdcSourceFunction.copyForSubtask(int)`(:524)、Kafka/Batch 族均返回真实逐字段拷贝——两族模型不同，是第 1 轮清单 S7-S12 成立而 CEP udf 面失守的分界。

## 1. 新增发现

### [nop-stream-state-connectors-C1-005] CEP：PatternTimeoutFlatSelectAdapter 的共享 SideCollector ctx 槽位被全部 subtask 复用，超时匹配记录可经别的 subtask 的 Context 发出（跨任务数据错路由）

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/functions/adaptors/PatternTimeoutFlatSelectAdapter.java:45,56-65,73-77,84-106`
- **证据片段**:
  ```java
  private transient SideCollector<T> sideCollector;              // :45 共享实例上的可变字段
  public void open(Configuration parameters) {
      super.open(parameters);
      ...
      if (sideCollector == null) {                                // :62 检查-然后-行动（共享实例）
          sideCollector = new SideCollector<>(...);
      }
  }
  public void processTimedOutMatch(Map<String, List<IN>> match, Context ctx) throws Exception {
      sideCollector.setCtx(ctx);                                  // :75 先写共享槽
      flatTimeoutFunction.timeout(match, ctx.timestamp(), sideCollector); // :76 用户回调内经槽读回
  }
  private static final class SideCollector<T> implements Collector<T> {
      private transient Context ctx;                              // :88 非 volatile 槽位
      public void collect(T record) { ctx.output(timedOutPartialMatchesTag, record); } // :100
  ```
- **严重程度**: P1
- **并发场景**: 同 JVM 并行部署（parallelism ≥ 2）：全部 subtask 的 `CepOperator` 拷贝经 `copyForSubtask()`（`CepOperator.java:336` `getUserFunction()` 透传）共享**同一个** adapter 实例。各 subtask 的任务线程在 `processTimedOutSequences`（`CepOperator.java:1354-1365`）中调用 `getUserFunction().processTimedOutMatch(...)`——即两个任务线程并发进入同一 adapter 的同一方法，操作同一 `sideCollector` 槽位。事件时间超时在 watermark 推进时逐 subtask 独立触发、处理时间超时定时器逐 subtask 独立注册，两个 subtask 的超时批次时间上重叠是常态而非异常。
- **触发时序**: T0（subtask0 任务线程）进入 `processTimedOutMatch(match0, ctx0)` 执行到 :75 `sideCollector.setCtx(ctx0)`（槽位=ctx0）→ T1（subtask1 任务线程）进入 `processTimedOutMatch(match1, ctx1)` 执行到 :75 `setCtx(ctx1)`（槽位被覆写=ctx1）→ T0 继续执行 :76 `flatTimeoutFunction.timeout(match0, ts0, sideCollector)`，用户超时回调内调用 `collector.collect(x)` → `SideCollector.collect` :100 读到槽位当前值 **ctx1** → match0 的超时记录经 **subtask1 的 Context** 发出：落入 subtask1 的侧输出流（错任务、错路由），且 subtask0 自身输出中该记录永久丢失。窗口宽度 = T1 的 `setCtx` 到 T0 本次超时回调内全部 `collect` 完成之间——无任何同步可收敛该窗口（adapter 无锁、槽位非 ThreadLocal、非方法参数）。
- **现状**: `SideCollector` 被设计为 adapter 字段一次性创建（open 惰性初始化），`ctx` 是跨调用存活的共享可变槽；而该方法的真实并发模型（每 subtask 任务线程 × 共享 adapter 实例）使「set-then-use」不具原子性。附带缺陷：`open()` 中 `if (sideCollector == null)`（:62）在共享实例上是检查-然后-行动（F10 形态），两 subtask 的 open 竞争可各建一个实例（后果轻，但证明该字段的生命周期从未按共享模型设计）。
- **风险**: 触达面 = 「并行 CEP + flatSelect 带 timeout 处理器」即 `PatternStream.flatSelect(OutputTag, PatternFlatSelectFunction, PatternFlatTimeoutFunction)` 的全部生产部署。后果是超时记录错路由/丢失（语义违约、数据损坏），且无声无息——输出仍会出现在某个侧输出流中，只是归属错误；下游按 subtask 维度聚合时表现为静默错账。
- **建议**: 将 collector 生命周期收敛到单次调用：`processTimedOutMatch` 内 new 方法局部 `SideCollector`（每次构造开销一次小对象）并把 `ctx` 经构造器传入，删除字段 `sideCollector` 与 `setCtx` 槽位；或最小改动——`SideCollector.collect` 改为消费 `ThreadLocal<Context>`。随 C1-006 的 udf 深拷贝修复后共享面消失，本缺陷亦被覆盖，但两者机制独立（本条在单实例内即不安全），建议独立修复以防回退。
- **失效面枚举**（F3 同族清扫）: ① `PatternTimeoutFlatSelectAdapter.sideCollector`/`SideCollector.ctx` —— **命中**（本条）；② `PatternTimeoutSelectAdapter.processTimedOutMatch`（:68-75）—— `ctx` 为方法参数、结果经 `ctx.output` 直接发出，无共享槽 —— **无缺陷**（其 open/close 面归 C1-006）；③ `PatternFlatSelectAdapter`/`PatternSelectAdapter` —— 无超时路径、无可变槽 —— **无缺陷**（生命周期归 C1-006）；④ `CepOperator.context`/`collector`（:1391 `ContextFunctionImpl`，javadoc "instantiated once per operator"）—— 随 operator 逐 subtask 拷贝，实例私有 —— **无缺陷**；⑤ 用户直接 `implements PatternProcessFunction + TimedOutPartialMatchHandler`（`PatternStream.process` 路径）—— 框架不提供槽位、`ctx` 为参数 —— 框架侧无缺陷（用户自建可变槽属用户代码契约）；⑥ 共享 `flatTimeoutFunction` 被多 subtask 并发调用本身 —— 归 C1-006 的 udf 共享面，不重复计数。
- **信心水平**: 确定（机制与交错序列确定；触发需要两 subtask 超时批次重叠，属负载相关而非罕见路径）
- **误报排除**: 不属于「原语使用不是问题」——缺陷是复合操作（setCtx → 用户回调 → collect 读槽）在共享实例上的非原子序列；不属于「无状态单例被并发调用是设计意图」——`sideCollector`/`ctx` 是真实跨线程可变状态，且写读之间隔着用户回调这一任意长度窗口；不按 P2 降级——触发时序已具体化到行级且后果是记录错路由，非理论风险。
- **复核状态**: 已保留（P1，2026-10-14 复核：live code 证实共享 adapter 实例经 PatternProcessFunctionBuilder:98 单次创建 + CepOperator.copyForSubtask 引用透传跨 subtask 共享；sideCollector(:45) 槽位 setCtx(:75)→用户回调→collect 读槽(:100) 的复合序列无任何同步收敛；processTimedOutSequences(:1345-1365) 每任务线程调用，错路由经别 subtask 的 ContextFunctionImpl（错 output collector + 错 timestamp()）后果具体到行。现状：新发现）

### [nop-stream-state-connectors-C1-006] CEP：共享 udf（4 个 adapter 及用户直传 PatternProcessFunction）被每个 subtask 的 open()/close()/setRuntimeContext 原地变异——Rich 用户函数 runtimeContext last-writer-wins、open N 次、首个 close 关闭全部 subtask 的用户函数资源（C1-001 输出侧同族）

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:320-339,1345-1365`；`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/functions/adaptors/PatternFlatSelectAdapter.java:44-53`；`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/functions/adaptors/PatternSelectAdapter.java:44-53`；`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/functions/adaptors/PatternTimeoutFlatSelectAdapter.java:56-71`；`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/functions/adaptors/PatternTimeoutSelectAdapter.java:55-66`；归属裁定上下文（非发现对象）：nop-stream-core `operators/AbstractUdfStreamOperator.java:74-77,137-139`、`common/functions/AbstractRichFunction.java:45`
- **证据片段**:
  ```java
  // CepOperator.copyForSubtask —— javadoc 把 user process function 归入 immutable configuration
  // "the NFA factory, user process function, comparator, skip strategy ... (all immutable
  //  configuration) are shared across subtasks"
  public CepOperator<IN, KEY, OUT> copyForSubtask() {
      return new CepOperator<>(inputSerializer, isProcessingTime, nfaFactory, comparator,
              afterMatchSkipStrategy, getUserFunction(), lateDataOutputTag, keyClass); // 引用透传
  // PatternFlatSelectAdapter —— 每 subtask 的 open 都在【同一共享实例】上执行
  public void open(final Configuration parameters) {
      FunctionUtils.setFunctionRuntimeContext(flatSelectFunction, getRuntimeContext()); // 覆盖共享用户函数
      FunctionUtils.openFunction(flatSelectFunction, parameters);                       // 第 N 次 open
  public void close() { FunctionUtils.closeFunction(flatSelectFunction); } // 首个 close 关全局
  // AbstractRichFunction —— 被覆盖的共享可变字段
  private transient RuntimeContext runtimeContext;   // 非 volatile，last-writer-wins
  ```
- **严重程度**: P1
- **并发场景**: 与 C1-001 同一共享模型：`OperatorChain.deepCopy` 引用拷贝使全部 subtask 的 `CepOperator` 共享同一 udf 实例（adapter 或用户直传的 `PatternProcessFunction`，`PatternStreamBuilder.java:158-187` 直接作为 udf 装配）；各 subtask 运行在 TaskExecutor 独立线程，`open()` → `AbstractUdfStreamOperator.open()`（:76 `setFunctionRuntimeContext(userFunction, new StreamingRuntimeContext())` 覆写共享 adapter 的 `runtimeContext` 字段）→ adapter.open() 再把 context 传播进共享内层用户函数；任务结束各自调 `close()`。
- **触发时序**: T0（stream-task-executor-0）执行 `CepOperator(subtask0).open()` 到 `AbstractUdfStreamOperator.open()` :76（共享 adapter.runtimeContext=ctx0）→ T1（stream-task-executor-1）对**同一实例**执行同序列（adapter.runtimeContext=ctx1）→ T0 的 adapter.open() :46 `getRuntimeContext()` 此时可能已读到 ctx1，把**别的 subtask 的** context 写入共享的 Rich 用户函数（`flatSelectFunction.runtimeContext=ctx1`）→ 此后 T0 每条匹配输出中用户 Rich 函数经 `getRuntimeContext()` **持久**读到 subtask1 的 context（keyedStateStore/子任务坐标错配）——持续错配而非瞬时竞态；同理任一 subtask 先执行 close()（:139 `closeFunction`）会关闭全部 subtask 仍在使用的用户函数资源：其余 subtask 后续 `processMatch` 触达已 close 的资源（连接/池/缓冲）抛错或静默异常。open 交错发生在启动窗口（全 subtask 同时提交），close 交错发生在取消/恢复窗口。
- **现状**: `copyForSubtask` javadoc 的 "user process function (immutable) 共享" 不变式对 Rich 用户函数不成立——`setRuntimeContext/open/close` 是框架对共享对象的运行期写路径，与第 1 轮 C1-001 的 NFA 条件面同根因、不同载体：C1-001 的失效面枚举只清点了 NFA 图内条件（①-⑥），`functions/adaptors/` 与用户直传 udf 族是其未清扫的同族残留。
- **风险**: 触达面 = 一切并行 CEP 且用户函数为 Rich（复用 RuntimeContext / 持有需 open 的资源）的部署，以及全部 4 个 adapter 路径（select/flatSelect/带 timeout 两形态）。后果：(a) Rich 用户函数读到别的 subtask 的 RuntimeContext——若用于 keyed state 访问即静默读错状态；(b) `open()` 执行 N 次（资源重复分配，如连接池 N 倍建立）；(c) 一个 subtask 的 close 拆掉其余 subtask 正在输出的用户函数资源——作业收尾阶段的错误风暴或恢复循环。
- **建议**: 与 C1-001 收口为同一修复：`CepOperator.copyForSubtask()` 对 `nfaFactory` 与 `getUserFunction()` 一并做序列化 round-trip 深拷贝（使条件对象与 udf 随 subtask 私有）；同时修正 javadoc 的 immutable 清单表述（user process function 不再列入）。若用户函数不可序列化则退化为「uDF 工厂化」：`PatternStream` API 接受 `Supplier<PatternProcessFunction>`、每 subtask 调用一次——改动在 cep 模块内可收口，core 的 `AbstractUdfStreamOperator` 契约不动。
- **失效面枚举**（F3 同族清扫）: ① `PatternFlatSelectAdapter.open/close` —— **命中**；② `PatternSelectAdapter.open/close` —— **命中**；③ `PatternTimeoutFlatSelectAdapter.open/close`（super 链 + timeoutFunction 传播）—— **命中**（其共享槽位另立 C1-005）；④ `PatternTimeoutSelectAdapter.open/close` —— **命中**；⑤ 用户直传 `PatternProcessFunction`（`PatternStream.process` → `PatternStreamBuilder` :158/:174 装配为 udf）—— **命中**（Rich 用户函数同机理）；⑥ `comparator`（`CepOperator.java:195` final 共享）—— grep 证实仅有 `stream.sorted(comparator)`（:1188）使用、无 open/close/setRuntimeContext 写点 —— **无缺陷**；⑦ `afterMatchSkipStrategy`（共享）—— aftermatch 族全部字段 final（本轮 grep 证实，无 Flink 版的 isConfigured 类可变标志）—— **无缺陷**；⑧ connector 族 sink/source（`JdbcTwoPhaseCommitSink.copyForSubtask(int)`:385、`DebeziumCdcSourceFunction.copyForSubtask(int)`:524、Kafka/Batch/File 族）—— 逐字段真实拷贝，非引用共享 —— **无缺陷**；⑨ NFA 图内条件族 —— 第 1 轮 C1-001 已立项（同根因，修复方案合并）。
- **信心水平**: 确定
- **误报排除**: 不属于「无状态单例并发调用」——Rich 生命周期调用是对共享对象的写路径；不属于「平台基础设施不重审」——缺陷载体（adapter 族、copyForSubtask 的共享清单）全部位于审计对象 nop-stream-cep 内，core 的 AbstractUdfStreamOperator 仅作语义裁定依据；不与 C1-001 重复计数——C1-001 限于 NFA 图内条件对象（输入侧过滤路径），本条是 udf/adapter 族（输出侧匹配发射路径），载体、触达方法（processMatch/processTimedOutMatch vs filter）、失效面枚举均不重叠，且两处 javadoc 不变式违例需分别修正。
- **复核状态**: 已保留（P1，2026-10-14 复核：AbstractUdfStreamOperator.open(:74-78)/close(:137-140) 对共享 udf 执行生命周期、4 个 adapter 与用户直传 udf 同机理，机制成立。后果 (a) 修正：open 传给 udf 的是每次新建的空 StreamingRuntimeContext（无身份、keyedStateStore=null），last-writer-wins 双方为等价空上下文，「读到别的 subtask 的 keyedStateStore/坐标」在当前框架不可达（属 udf 从不接收真实 per-subtask context 的框架级缺口，邻近 r6 CEP-03）；保留 P1 依据后果 (b) open N 次（N-1 资源泄漏）与 (c) 首个 close 关闭全部 subtask 共享的用户函数资源（取消/恢复窗口错误风暴），两者与 context 值无关、独立成立。现状：新发现（二轮复核确认：r4 R5-CEP-05 登记的是 NFA 条件面，且其误报排除显式将 user function 共享排除在外——「user function 的共享符合引擎约定，不计入」，udf/adapter 载体为首立；同根因已知未修复：r4 R5-CEP-05（P3）；同族已知未修复：r6 CON-03（P2，sink 族 StreamSinkOperator.copyForSubtask 只拷 2PC sink））

## 2. 本轮新核对无缺陷面（供后续轮次免查）

| 面 | 结论 |
|---|---|
| MessageSinkFunction | ✅ 仅 final 字段（messageService/topic），consume 无状态同步发送 |
| BatchLoaderSource 全文 / Serializers | ✅ final 字段；SplitSerializer/EnumeratorStateSerializer 无状态；单写者有 validateParallelism 部署期守卫 |
| BatchLoaderSplitEnumerator / 各 EnumeratorState POJO | ✅ coordinator 单线程封闭 + 序列化边界（第 1 轮 S19/S20 口径延伸确认） |
| DebeziumCdcSource.initialOffsets（非 final） | ✅ javadoc 声明 "applied before deployment"（声明边界）+ submit happens-before 覆盖 createReader 读取；仅 test 访问器旁路 |
| DebeziumSourceSupport / CdcEventOps / JdbcTwoPhaseCommitSinkBuilder / 三个 ConnectorFactory | ✅ 静态无状态门面 / builder 相位单线程配置持有 / 每 job 实例化 |
| format-avro 逃逸面（任务指定项） | ✅ `writerSchemaIds`/`schemaByIdCache`/`latestBySubject` 均 private 无 getter 泄漏；`avroSerializer/avroDeserializer` 返回的 lambda 闭包 this，但实例状态全为 CHM，并发调用安全；writer/encoder 每次调用新建（分配浪费归 C9，非 C1） |
| connector-batch 逃逸面（任务指定项） | ✅ BatchLoaderSourceReader/Split/EnumeratorState 无内部集合外泄 getter；split 经 String/JSON 序列化边界交付 |
| rocksdb state 族（AbstractRocksDBState + 8 state 类 + KeyEncoder + 2 SerDe + incremental 2 类 + RocksDBStateBackend） | ✅ 全部 per-backend 实例化（`getState(...)` 内 `new RocksDB*State<>(this, cf, ...)` 缓存于 backend 的 states map），任务线程封闭；`setFunctionRuntimeContext/openFunction` 在 rocksdb 模块 grep 为 0 命中——无 Rich 生命周期写点；ttl 槽 bindTtl 在 getState 装配期单线程写入 |
| SharedBufferAccessor（sharedBuffer 非 final） | ✅ 仅构造期赋值、任务线程封闭（第 1 轮 S3 延伸）；`NFAStateNameHandler` 为 per-compile 实例（NFACompiler:145），静态方法为纯解码 |
| Quantifier.innerConsumingStrategy / Pattern 4 个非 final 字段 | ✅ builder 相位单线程；NFACompiler 无 pattern 写点（grep 证实），编译期后只读 |
| StreamRecordComparator / CepRuntimeContext | ✅ 无状态（值语义）；CepRuntimeContext final 字段、per-subtask 构造 |
| FilePendingCommit | ✅ DataBean POJO，快照/提交经第 1 轮 S9 的 synchronized(pending) 路径 |
| 发布安全（任务指定项） | ✅ 8 模块 grep：无构造器内 new Thread/注册监听器/`new X(this)` this 逃逸；唯二的线程启动在 `launchRunner` 方法内（MessageSourceReader:87-90、DebeziumCdcSourceReader:132-134，第 1 轮 S14 已核 Thread.start() happens-before）；无 getter 返回内部可变集合（仅 rocksdb `getStates()` 返回内部 map，但其调用链 snapshot 全在任务线程） |

## 3. 覆盖声明

- 本轮逐文件通读：PatternTimeoutFlatSelectAdapter(107)、PatternFlatSelectAdapter(61)、PatternSelectAdapter(61)、PatternTimeoutSelectAdapter(76)、PatternProcessFunctionBuilder(150)、EvalFunctionCondition(30)、AbstractRocksDBState(92)、BatchLoaderSource(231)、DebeziumCdcSource(225)、MessageSinkFunction(57)、CdcEventOps(52)、FilePendingCommit(90)；SharedBufferAccessor(头 120 行)。
- 定向核查（grep + 关键节）：8 模块非 final 实例字段全量清单（排除 `_gen/`）、发布安全三态 grep（构造器 this 逃逸 / new Thread / addListener）、getter 泄漏、`setFunctionRuntimeContext|openFunction` 在 rocksdb 的命中（0）、`copyForSubtask` 在 connector 族的真实拷贝形态、NFACompiler 对 Pattern/Quantifier 的写点（0）、comparator/afterMatchSkipStrategy 的运行期写点（0）、NFAStateNameHandler 全部使用点。
- 归属裁定引用的 core 文件（仅上下文，非发现对象）：`AbstractUdfStreamOperator`(:74-77,:137-139)、`AbstractRichFunction`(:45)、`FunctionUtils`(:37-56)。
- 未覆盖说明：`_gen/` 生成类按纪律排除；cep `model/` 非 `_gen` 部分（CepPatternModel 族）为 XLang 模型 bean、单线程装配，本轮判定无共享写路径但未逐行通读；nop-stream-core 自身（含 AbstractUdfStreamOperator 对共享 udf 的架构选择）留待该模块维度。
- 序号接续：本轮新增 C1-005、C1-006；第 1 轮 C1-001~004 维持不变，未发现需推翻第 1 轮结论的证据。

## 深挖第 3 轮追加

> 维度：C1（共享可变状态与可见性），模块组 nop-stream-state-connectors（nop-stream-cep、nop-stream-rocksdb、nop-stream-connector、nop-stream-connector-batch、nop-stream-connector-jdbc、nop-stream-connector-debezium、nop-stream-connector-kafka、nop-stream-format-avro）。
> 盲审边界：本轮仅读取第 1/2 轮自身文件（C1-shared-mutable-state.md、C1-round2.md）+ live code，未读取其他审计目录、plans、bugs、lessons。
> 与前两轮关系：不重复第 1 轮 18 个逐文件通读对象与两轮「已核对无缺陷」面；本轮专攻两轮覆盖声明中显式留白的角落（cep `model/` 非 `_gen` 族、cep `time/`、split POJO 可变性、`copyForSubtask` 逐字段完整性、值对象/枚举/序列化钩子/配置载体）。

## 0. 本轮检查范围（前两轮免查清单之外的角落）

- **任务指定同族深挖**：第 2 轮 C1-005/C1-006 的同族面收口核查——`copyForSubtask` 五个实现的**逐字段完整性**（Jdbc :385/:400 拒绝浅拷贝漏字段、File :197、KafkaSourceFunction :158、KafkaTwoPhaseCommitSink :288、DebeziumCdcSourceFunction :524）；split 交付边界的 POJO 载体（`FileSplit`/`MessageTopicSplit`/`BatchLoaderSplit`/`DebeziumTableSplit` 及 4 个 EnumeratorState POJO）。
- **cep 显式留白**：`time/`（TimeContext/TimerService）、`model/` 非 `_gen` 族 + `CepPatternBuilder`（第 2 轮声明"未逐行通读"）、conditions 族（SimpleCondition/SubtypeCondition/BooleanConditions/IterativeCondition）、`State`/`StateTransition`（含非 final `condition` 字段的 setter 写点验证）、`StateTransitionAction`/`StateType`/`QuantifierProperty`/`ConsumingStrategy`/`WithinType`/`TimeBehaviour`/`DmlMode`/`FollowKind`/`AfterMatchSkipStrategyKind` 全部枚举、`NFAState`/`ComputationState`/`DeweyNumber` 可变性、`SharedBufferNode`/`SharedBufferEdge`/`NodeId`/`EventId`/`Lockable`、aftermatch 3 个单例（INSTANCE 共享面）、`NopCepConfigs`/`SharedBufferCacheConfig`、`PatternStream`/`PatternStreamBuilder` 实例字段。
- **connector 显式留白**：`MessageSource`（186 行全文）、`FileSource`（433 行全文，含 3 个内嵌 Serializer 与 `sample()`）、9 个 ConnectorFactory 字段面、`RocksDBOptionConfig`、`StreamAvroException`。
- **全局 grep 三连（8 模块）**：线程敌对工具字段（SimpleDateFormat/Calendar/Random/Matcher/MessageDigest——0 命中）、`Collections.synchronizedXxx`（0 命中，基类 pendingCommits 在 core，第 1 轮 S9 已核）、`new Random`/`MessageDigest` 任意使用（0 命中）、`getInstance`/static INSTANCE（仅 aftermatch 3 个无状态单例）、`readObject`/`writeObject`/`readResolve` 钩子（3 处，逐一核对）。

## 1. 新增发现

**零新发现。** 本轮所有新盘点载体均核对为无缺陷（判定依据见 §2 表），未产生 C1-007 及之后条目；亦无散在低价值条目（无 P2/P3 值得立项项）。

## 2. 本轮新核对无缺陷面（供后续轮次免查）

| 面 | 结论 |
|---|---|
| copyForSubtask 完整性（任务指定项） | ✅ 五个实现全部经规范构造器/序列化 round-trip 重建：Jdbc（canonical constructor 全字段 + `unmodifiableList` 防御拷贝 + buffer/pendingCommits 新建）、File（私有拷贝构造器 + fresh currentBuffer）、KafkaSourceFunction（consumerProps 构造器内 `new LinkedHashMap<>` 防御拷贝、restoredOffsets fresh、rateLimit 两 volatile 逐字段复制、limiter 在 run() 内每 subtask 新建）、KafkaTwoPhaseCommitSink（producerProps 防御拷贝 + transactional.id 追加 identity）、Debezium（`serializationRoundTripCopy` 深拷贝后仅覆写 subtaskIndex/totalParallelism/credentialProvider=null）；无漏拷贝的共享可变字段。共享的 recordMapper/opMapper/deleteKeyMapper/valueDeserializer 为用户 Function，javadoc 显式声明 immutable configuration 共享（声明边界） |
| split POJO 族（任务指定项） | ✅ FileSplit/MessageTopicSplit/BatchLoaderSplit 全 final 字段（FileSplit 光标推进走 `withCurrentOffset` copy-on-write）；DebeziumTableSplit 构造器对 offsetMap 做 `new LinkedHashMap<>(offsetMap)` 防御拷贝、`withOffsetMap` 新建实例；`getOffsetMap()` 虽泄漏内部 map 引用，但全部 4 个调用点只读或整体替换，map 构造后事实不可变；`restoredOffsetByTable` 持引用不修改内层 |
| Debezium offset map 跨线程链路 | ✅ `DebeziumCdcSourceReader.snapshotState` → `embeddedFunction.snapshotState` 返回 `NopStreamOffsetBackingStore.toSerializable(store.getOffsets())`：store.data 为 ConcurrentHashMap（弱一致迭代无 CME/撕裂），toSerializable 在任务线程新建 TreeMap——`withOffsetMap` 的防御拷贝消费的是任务线程私有副本，与 runner 线程无共享写读；store 本体属 nop-message-debezium（范围外），范围内使用方式正确 |
| cep time/ | ✅ 纯接口（TimeContext/TimerService 无任何字段） |
| cep model/ 非 _gen 族 + CepPatternBuilder | ✅ 4 个模型类为空壳（继承 `_gen` 基类，按纪律排除本体）；CepPatternBuilder 无实例/静态字段，全局部变量，装配相位单线程 |
| conditions 族 | ✅ SimpleCondition（final filterFunction + 序列化守卫钩子）、SubtypeCondition（final subtype）、BooleanConditions/IterativeCondition 无状态；唯一可变字段 RichIterativeCondition.runtimeContext 已由 C1-001 立项，无同族残留 |
| State/StateTransition 写点 | ✅ 第 1 轮 ⑤ 结论复核成立：`State.stateType`（makeStart）与 `StateTransition.condition`（setCondition, :66）的全部写点均在 NFACompiler 编译相位（grep 证实 :354-:919 及 :1072 `updateWithGreedyCondition` 为 factory 封印前的图构造），运行期零写点 |
| NFA 值对象族 | ✅ ComputationState/DeweyNumber 全 final；NFAState 的 4 个非 final 字段（partialMatches/completedMatches/stateChanged/isNewStartPartialMatch）为 NFA 实例私有 → 每 subtask 一份，任务线程封闭 |
| SharedBuffer 载体族 | ✅ SharedBufferNode（edges ArrayList）与 NodeId/EventId（含无调用者的序列化 setter，grep 证实）均封闭于任务线程内的 SharedBuffer；Lockable 为 AtomicInteger + CAS 循环，javadoc 自声明复合操作边界 |
| aftermatch 单例 | ✅ NoSkipStrategy/SkipPastLastStrategy/SkipToNextStrategy 的 INSTANCE 共享但无状态（全 final 字段，第 2 轮 ⑦ 结论延伸确认） |
| PatternStream/PatternStreamBuilder | ✅ 全 final 字段，client 侧装配相位对象 |
| 配置/枚举载体 | ✅ NopCepConfigs（IConfigReference 常量接口）、SharedBufferCacheConfig/RocksDBOptionConfig（全 final 字段不可变 bean）、全部 9 个枚举无可变实例字段 |
| connector 描述符 | ✅ MessageSource/FileSource 不可变配置持有 + 每次调用新建 enumerator/reader/serializer（serializer 无状态）；9 个 ConnectorFactory 仅 static final ConnectorCapabilityDescriptor（不可变注册元数据） |
| 序列化钩子 | ✅ 3 处 readObject（FileTwoPhaseCommitSink :142 重建 transient currentBuffer/outputDirPath/charset；DebeziumCdcSourceFunction :173 重建 config/latch；SimpleCondition :85 仅 defaultReadObject）均正确重建 transient 态，无跨线程发布缺口 |
| 全局 grep 三连 | ✅ 8 模块：线程敌对工具字段 0、`Collections.synchronizedXxx` 0（基类 pendingCommits 属 core，第 1 轮 S9 已核）、`new Random`/`MessageDigest` 0、`getInstance`/static INSTANCE 仅 aftermatch 3 个无状态单例 |

## 3. 终止声明（深挖终止信号）

- **累计发现（本维度三轮）**：C1-001（P1，CEP 共享 NFA 条件图 runtimeContext 错配）、C1-002（P1，RocksDB gauge native use-after-free 窗口）、C1-003（P2，三个 FLIP-27 reader addSplits 可见性竞态）、C1-004（P3，静态锁注册表只增不删）、C1-005（P1，共享 SideCollector ctx 槽位跨 subtask 错路由）、C1-006（P1，共享 udf Rich 生命周期 last-writer-wins）。本轮追加：**0 条**。
- **终止依据**：第 1 轮完成 18 个大文件逐行通读 + 五类载体全量盘点；第 2 轮完成 adapters/rocksdb state 族/辅助类/逃逸面/发布安全；本轮完成剩余全部文件（含两轮显式留白的 `model/`、`time/`、split POJO、copyForSubtask 完整性、值对象/枚举/钩子/配置载体）与三组全局 grep。至此 8 模块 `src/main/java` 全部非 `_gen` 文件均已被三轮之一覆盖（逐行通读、字段清单盘点或 grep 全量排除），C1 五类载体中每类均已收敛到已立项条目或已核对无缺陷面，无新的可疑载体残留。
- **对后续轮次的建议**：C1 维度可关闭；若复核阶段推翻 C1-001/006 的共享模型裁定（`OperatorChain.deepCopy` 引用拷贝语义变化），需重启本维度复查共享面清单（第 1 轮清单表 S1-S20 + 本轮 §2 表为基线）。

## 4. 覆盖声明

- 本轮逐文件/逐面通读：TimerService(37)、TimeContext(44)、CepPatternBuilder(246)、CepPatternSingleModel（空壳核验）、SimpleCondition(90)、State(120+grep)、StateTransition(字段面+写点 grep)、SharedBufferNode(72)、Lockable(80+)、MessageSource(186)、FileSource(433)、DebeziumTableSplit(83)、DebeziumCdcSourceReader(:55-299 增读)、RocksDBOptionConfig(117)、NopCepConfigs/SharedBufferCacheConfig/EventComparator（全文）、JdbcTwoPhaseCommitSink(:270-410)、FileTwoPhaseCommitSink(:130-210)、KafkaSourceFunction(:93-170)、KafkaTwoPhaseCommitSink(:270-330)、DebeziumCdcSourceFunction(:165-185,:495-585)、NopStreamOffsetBackingStore（归属裁定上下文，字段面+关键节）。
- 定向核查（grep）：全部枚举字段面、`setCondition`/`makeStart`/`setEventId`/`setPageName` 全部调用点、`getOffsetMap()` 全部调用点、9 个 factory 字段面、`getInstance`/static INSTANCE、`readObject`/`writeObject`/`readResolve`、线程敌对工具字段、`Collections.synchronizedXxx`、`new Random`/`MessageDigest`、`PatternStream`/`PatternStreamBuilder`/`NFAState`/`ComputationState`/`DeweyNumber` 字段面。
- 未覆盖说明：`_gen/` 生成类按纪律排除；nop-stream-core（AbstractUdfStreamOperator、TwoPhaseCommitSinkFunction 基类、SourceRateLimiter、LocalSourceCoordinator）与 nop-message-debezium（NopStreamOffsetBackingStore 本体）仅为归属裁定上下文，非发现对象，留待对应模块维度。
- 序号接续：本轮新增 0 条（无 C1-007）；第 1 轮 C1-001~004、第 2 轮 C1-005~006 维持不变，未发现需推翻前两轮结论的证据。

## 维度复核结论

Independent review completed 2026-10-14 by review sub-agent（工作目录 nop-entropy-concurrency-audit）。本轮解除盲审：已读取 ai-dev/audits/ 历史审计与 git log 做现状归属；全部 6 条发现逐条重读 live code 与调用方验证。审计第 3 轮终止声明中预设的「复核推翻共享模型即重启维度」触发条件已核查，不成立。

**C1-001/005/006 共同前提（任务指定重点）逐环验证成立**：
1. `GraphExecutionPlan.java:454` 与 `RemoteGraphExecutionPlanBuilder.java:313` 均在 `for (taskIndex...)` 循环内对**同一** `original.getOperatorChains().get(0)` 执行 `deepCopy(taskLocation)`（remote 路径 `original` 为该节点反序列化后的单例 JobVertex，:291）；
2. `OperatorChain.deepCopy(TaskLocation)`（nop-stream-core `OperatorChain.java:274-280`）逐 operator 委托 `copyForSubtask(location)`，自身无拷贝语义；
3. `CepOperator.copyForSubtask()`（`CepOperator.java:329-339`）对 `nfaFactory` 与 `getUserFunction()` **引用透传**，无序列化 round-trip——「引用拷贝」前提成立；
4. `NFAFactoryImpl.createNFA()`（`NFACompiler.java:1119-1121`）把同一 `states` 集合传入每个新 NFA；`NFA.open()`（`NFA.java:193-204`）对共享条件执行 `setFunctionRuntimeContext/openFunction`；`RichIterativeCondition.runtimeContext` 为非 volatile transient 字段——C1-001 变异面成立；
5. `PatternProcessFunctionBuilder` 在 client 侧单次创建 adapter（:73/:98/:122/:147）→ `PatternStreamBuilder:159/:175` 装配为唯一 udf——C1-005/006 共享实例成立。

| 发现 | 判定 | 理由（2-4 行） |
|---|---|---|
| C1-001（P1，NFA 共享条件图） | **已保留 P1** | 前提链 1-4 全部 live code 证实；`initNfa`（:606-617）的 `CepRuntimeContext` 绑定 per-operator `keyedStateStore`，跨 subtask last-writer-wins 泄漏键控状态的主后果成立。一处修正：该 context 的 subtask 身份恒为硬编码 0/1（:609-613，即 r6 CEP-03 已知未修复残余），「读到别的 subtask 坐标」成分退化，不影响定级。 |
| C1-002（P1，gauge native UAF 窗口） | **已保留 P1** | live code 证实 gauge 闭包 `ref -> readProperty(ref.get(), property)` 的 `ref.get()` 在进入 synchronized 前求值；`RocksDBKeyedStateBackend` close 顺序 recorder.close()(:989)→db.close()(:1013)、gauge 无摘除点——「取旧引用→close→用旧引用」交错成立，null 守卫与 synchronized 均挡不住。窗口窄但后果 SIGSEGV、javadoc 自认 crash、scrape 周期性×恢复必经 close，时序具体。 |
| C1-003（P2，三 reader addSplits 无守卫） | **已保留 P2** | `ReaderChannel.drainPending → cb.accept` 证实交付回调在调用方线程同步执行；`token`(:62)/`adoptedSplits`(:68-69)/`pendingSplit`(:66) 均普通字段，`FileSourceReader` 同入口 `synchronized(this)` 对照成立，不对称属实。小误不影响主体：`BatchLoaderSourceReader.started` 实为 volatile(:58)，但 split 载体 `pendingSplit` 非 volatile。后果自愈/recovery=NONE 吸收、原文已自标理论风险，P2 恰当。 |
| C1-004（P3，静态锁注册表只增不删） | **已保留 P3** | live code 证实静态 CHM 仅 :390 一处 `computeIfAbsent`、无 remove/淘汰路径；互斥正确性核对成立。非竞态缺陷、键空间有界（=历史输出目录数），信息性保留，不立项。 |
| C1-005（P1，SideCollector ctx 槽位错路由） | **已保留 P1** | 共享 adapter 字段 `sideCollector`(:45)、`setCtx`(:75)→用户回调→`collect` 读槽(:100) 的复合序列无任何同步收敛；`processTimedOutSequences`(:1345-1365) 每任务线程调用；错路由经别 subtask 的 `ContextFunctionImpl`（错 output collector + 错 `timestamp()`）后果具体到行。 |
| C1-006（P1，共享 udf Rich 生命周期） | **已保留 P1（后果 (a) 修正）** | `AbstractUdfStreamOperator.open`(:74-78)/`close`(:137-140) 对共享 udf 执行生命周期、4 个 adapter 与用户直传 udf 同机理——机制成立。后果 (a) 修正：open 传给 udf 的是**每次新建的空** `StreamingRuntimeContext`（无身份、`keyedStateStore=null`→`getKeyedStateStore()` 抛 typed），last-writer-wins 双方为等价空上下文，「读到别的 subtask 的 keyedStateStore/坐标」在当前框架不可达（属 udf 从不接收真实 per-subtask context 的框架级缺口，邻近 r6 CEP-03）。保留 P1 依据后果 (b) open N 次（N-1 资源泄漏）与 (c) 首个 close 关闭全部 subtask 共享的用户函数资源（取消/恢复窗口错误风暴）——两者与 context 值无关、独立成立。修复建议（copyForSubtask 对 nfaFactory+udf 一并 round-trip 深拷贝）不变。 |

**现状归属（本轮解除盲审后标注）**：
- 6 条均为**新发现**（本轮审计首次立项；历史审计 r5/r6/core-runtime-0130/adversarial 均未登记同机制条目）。
- 邻近已知未修复（同族不同机制，不计入本维度）：C1-001 ↔ r6 `01-cep-flow-correctness.md` CEP-03（P3 保留，CepRuntimeContext 硬编码身份，live code :609-613 复核仍未修复）；C1-006 ↔ r6 `03-connectors-security.md` CON-03（P2 保留，sink 族 `StreamSinkOperator.copyForSubtask` 只拷 2PC sink）。
- git log 核查：上述文件近 20 次提交（plan 2326 R6 治理及更早）均无针对本组 6 条的修复，无「已修复+SHA」项。

**Result**: 6 保留（P1×4、P2×1、P3×1），0 降级，0 驳回。0 新发现。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状标注 |
|---|---|---|---|---|
| C1-001 | P1 | nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java | 并行 CEP 各 subtask 经引用拷贝共享 NFA 编译态 State 图，Rich 条件对象被每个 subtask 的 NFA.open()/close() 原地变异，runtimeContext/keyedStateStore last-writer-wins（静默读错键控状态） | 新发现（邻近已知未修复：r6 CEP-03 P3） |
| C1-002 | P1 | nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java | metrics gauge 闭包在 synchronized 之外取 dbRef.get()，close 窗口内 scrape 线程可对已关闭 native handle 调 getAggregatedLongProperty（SIGSEGV） | 新发现 |
| C1-003 | P2 | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceReader.java | Message/Debezium/Batch 三个 FLIP-27 reader 的 addSplits 交付面字段无同步无 volatile，与读者任务线程存在可见性竞态（后果自愈，理论风险）；对照 FileSourceReader 已守卫 | 新发现 |
| C1-004 | P3 | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java | MANIFEST_JVM_LOCKS 进程级静态锁注册表只增不删（互斥正确，增长有界，信息性不立项） | 新发现 |
| C1-005 | P1 | nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/functions/adaptors/PatternTimeoutFlatSelectAdapter.java | 共享 adapter 的 SideCollector ctx 槽位被全部 subtask 复用，超时匹配记录可经别的 subtask 的 Context 发出（错路由+错时间戳） | 新发现 |
| C1-006 | P1 | nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java | 共享 udf（4 adapter+用户直传）被每 subtask 的 open()/close() 原地变异：open N 次（N-1 资源泄漏）、首个 close 关闭全部 subtask 的用户函数资源；后果 (a) 经复核修正（空 context 不可达 keyedStateStore 路径） | 新发现（同族已知未修复：r6 CON-03 P2） |

## 二轮复核结论（独立复核子代理，第二次派发）

> 复核人标识：C1 二轮独立复核子代理（与初审者、一轮复核者均非同一角色实例），2026-10-14，工作目录 nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）。
> 输入：本文件三轮初审全文 + live code（8 模块 src/main/java 及归属裁定所需的 nop-stream-core/nop-stream-runtime 上下文）+ ai-dev/audits/ 下 nop-stream 历史目录（r3/r4/r5/r6、2026-09-30 adversarial、core-runtime 并发审计 2026-10-14-0130）的结论节 + git log。
> 与本文件已有一轮复核产物的关系：本文件已含一轮复核节（同为 2026-10-14，判定 6 保留、现状全标「新发现」）。本轮未采信其任何结论作为证据，6 条逐条重读 live code 独立重验；**判定结论与其一致（6 保留 / 0 降级 / 0 驳回），但修正 C1-001 的现状归属、补充 C1-006 的同根因邻近项**。按 append-only 纪律，前文（含一轮复核节）未改动，本节与 C1-001/C1-006 复核状态行的现状尾部修正为权威标注。

### 逐条判定表

| 发现 | 原判级 | 判定 | 一句话理由（二轮重验要点） | 现状标注（二轮权威） |
|---|---|---|---|---|
| C1-001 | P1 | 保留 P1 | 前提链全环 live code 重验成立：GraphExecutionPlan:452-457 / RemoteGraphExecutionPlanBuilder:311-313 对同一 original 逐 subtask deepCopy(taskLocation)；OperatorChain.deepCopy(TaskLocation):266-282 委托 op.copyForSubtask；CepOperator.copyForSubtask:329-339 引用透传 nfaFactory+udf；NFAFactoryImpl.createNFA:1118-1121 共享同一 states；NFA.open:193-204/close:210-217（经 CepOperator:427 initNfa、:689 close 驱动）变异共享条件；RichIterativeCondition.runtimeContext 非 volatile；initNfa:606-617 的 CepRuntimeContext 绑定 per-operator keyedStateStore（:489/:494 per-operator 创建）——跨 subtask 键控状态泄漏主后果成立；subtask 身份硬编码 0/1 的一轮修正亦重验成立 | **已知未修复**（历史编号 r4 R5-CEP-05，P3：同机制、同修复建议已立项未修；r5/r6 未结转、无修复提交。一轮复核「新发现」系漏查 r4 所致） |
| C1-002 | P1 | 保留 P1 | gauge 闭包 `ref -> readProperty(ref.get(), property)`（RocksDBMetricsRecorder:83）在进入 synchronized 前求值；recorder.close()（backend :988-990）→db.close()（:1013）顺序 + gauge 无摘除点挡不住「取旧引用→close→用旧引用」；null 守卫不可达该窗口；类 javadoc 自认 closed-handle 原生崩溃 | 新发现（r4-r6、core-runtime 并发审计 0 命中；recorder 创建于 c6dbc769fe、末次触碰 53be7ab83c，均为功能提交非修复） |
| C1-003 | P2 | 保留 P2 | 交付回调在调用方线程同步执行（LocalSourceCoordinator.ReaderChannel.drainPending:229-244 的 cb.accept）；token(:62)/adoptedSplits(:68-69)/restoredOffsetByTable(:69)/pendingSplit(:67) 均普通字段、addSplits 无同步（Message:108 / Debezium:161 / Batch:97）；FileSourceReader.addSplits:164-176 synchronized(this) 对照成立；原文字段清单小误（Batch started 实为 volatile:58）不影响主体；后果自愈/recovery=NONE 吸收，P2 恰当 | 新发现（addSplits 历史 0 命中；sibling core-runtime 审计 C1-001 登记的是 enumerator 侧锁纪律——BatchLoaderSplitEnumerator.assigned、DebeziumTableSplitEnumerator.shareBySubtask，载体与本条 reader 侧交付面不重叠） |
| C1-004 | P3 | 保留 P3 | 静态 CHM 仅 :98 声明 + :390 一处 computeIfAbsent、无任何 remove/淘汰路径；computeIfAbsent + jvmLock→FileLock 固定序互斥正确；键空间=历史输出目录数有界，信息性登记不立项 | 新发现（core-runtime C2:434 仅核过该锁的锁序，未登记增长面） |
| C1-005 | P1 | 保留 P1 | 共享 adapter 经 PatternProcessFunctionBuilder:73/98/122/147 client 侧单次创建 → PatternStreamBuilder:158-165 装配为唯一 udf → copyForSubtask 引用透传；sideCollector(:45) 槽位 setCtx(:75)→用户回调窗口→collect 读槽(:100) 无任何同步收敛；processTimedOutSequences:1345-1365 每任务线程调用，错路由经别 subtask 的 per-operator context（错 output collector + 错 timestamp） | 新发现（SideCollector/PatternTimeoutFlatSelectAdapter 历史 0 命中） |
| C1-006 | P1 | 保留 P1 | AbstractUdfStreamOperator.open(:74-78)/close(:137-139) 对共享 udf 驱动 setRuntimeContext/openFunction/closeFunction、4 adapter 同机理（PatternFlatSelectAdapter:43-53 实证）、FunctionUtils 对 RichFunction 无条件传播；后果 (a) 一轮修正重验成立——open 传无参 `new StreamingRuntimeContext()`（字段全默认、keyedStateStore=null→getKeyedStateStore() typed 抛错），last-writer-wins 双方为等价空上下文，错配不可达；P1 依据后果 (b) open×N（N-1 资源重复分配泄漏）与 (c) 首个 close 关闭全部 subtask 共享用户函数资源（取消/恢复窗口错误风暴）独立成立 | 新发现（udf/adapter 载体历史 0 登记——r4 R5-CEP-05 的误报排除显式将 user function 共享排除：「user function 的共享符合引擎约定，不计入」；同根因已知未修复：r4 R5-CEP-05（P3）；同族已知未修复：r6 CON-03（P2）） |

### 不确定项与口径说明

- **无判定级不确定项**。两点口径显式记录：
  1. C1-001/005/006 的 P1 均依赖「用户侧 Rich/有状态条件或函数 + parallelism ≥ 2」的部署形态（当前全部测试与示例为无状态 lambda 形态——r4 R5-CEP-05 同口径评估为「危害潜伏」）。按本轮并发审计判级口径维持 P1：触发时序已具体化到行级（启动 open 窗口 / 超时批次重叠 / 取消窗口），不属「P2=理论竞态无具体时序」；主后果（键控状态错配 / 记录错路由 / 共享资源交叉关闭）可具体化且静默。
  2. 任务简报所引「commit 3db616f46d（F10 首订阅 check-then-act 修复）」经 `git show` 核实为 **nop-ai-gateway**（ChannelMessageServiceImpl）修复，与本审计单元无交集；nop-stream 侧 F10 形态登记于 sibling core-runtime 并发审计（SplitEnumerator 锁纪律、BasicTypeInfo、TwoPhaseCommitSinkFunction TOCTOU），载体均在 nop-stream-core（单元范围外），与本组 6 条无同载体冲突。
- 一轮复核节中其余事实性内容（前提链行号、close 顺序、字段清单小误记录）经二轮重验均属实，予以确认；其「现状归属」一节关于「历史审计均未登记同机制条目」的结论因漏查 r4 被 C1-001 行修正，其余 5 条现状归属经独立检索维持。

## 最终保留项（二轮复核后权威版）

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|---|---|---|---|---|
| C1-001 | P1 | nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java（copyForSubtask :329-339；同族 NFA.java:193-217、NFACompiler.java:1118-1121、RichIterativeCondition.java:44-53） | 并行 CEP 各 subtask 经引用拷贝共享 NFA 编译态 State 图，Rich 条件对象被每 subtask 的 NFA.open()/close() 原地变异，per-operator keyedStateStore last-writer-wins（静默读错键控状态 + open×N + 交叉 close） | 已知未修复（r4 R5-CEP-05，P3） |
| C1-002 | P1 | nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java | metrics gauge 闭包在 synchronized 之外取 dbRef.get()，close 窗口内 scrape 线程可对已关闭 native handle 调 getAggregatedLongProperty（SIGSEGV） | 新发现 |
| C1-003 | P2 | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceReader.java（同族 connector-debezium/DebeziumCdcSourceReader.java、connector-batch/BatchLoaderSourceReader.java） | Message/Debezium/Batch 三个 FLIP-27 reader 的 addSplits 交付面字段无同步无 volatile，与读者任务线程存在可见性竞态（后果自愈，理论风险）；对照 FileSourceReader 已守卫 | 新发现 |
| C1-004 | P3 | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java | MANIFEST_JVM_LOCKS 进程级静态锁注册表只增不删（互斥正确，增长有界，信息性不立项） | 新发现 |
| C1-005 | P1 | nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/functions/adaptors/PatternTimeoutFlatSelectAdapter.java | 共享 adapter 的 SideCollector ctx 槽位被全部 subtask 复用，超时匹配记录可经别的 subtask 的 Context 发出（错路由+错时间戳） | 新发现 |
| C1-006 | P1 | nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java（同族 functions/adaptors/ 4 个 Adapter） | 共享 udf（4 adapter+用户直传）被每 subtask 的 open()/close() 原地变异：open N 次（N-1 资源泄漏）、首个 close 关闭全部 subtask 的用户函数资源 | 新发现（同根因已知未修复：r4 R5-CEP-05；同族：r6 CON-03） |
