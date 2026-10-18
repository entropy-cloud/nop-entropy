# C4 线程池与执行器治理审计报告 — nop-persistence 读写热路径

- **审计对象**: nop-orm / nop-orm-eql / nop-orm-model / nop-dao / nop-orm-data（5 源码根，src/main）
- **维度定义（C4）**: 线程池/执行器的配置有界、关闭正确、任务隔离；连接获取的执行上下文；池关闭责任
- **盲审纪律声明**: 本轮为盲审。未读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下任何文件；仅依据 `_tmp/cra-baseline-nop-persistence-rw.txt`（C3/C4 节）与当前 live code。api-core（FutureHelper/BaseContext/ContextTaskQueue/ContextProvider）按任务书仅作对照引用。
- **批次**: 合并批 1/1（C3+C4 合并初审，单批完成）
- **判级口径**: ORM 层被全平台数据访问调用
- **载体全景扫描（基线 C4 两节复核）**: 基线 grep 命中 2 处，经逐一核实均为**非线程池**：(1) `SessionFactoryConfig.java:160` `queryExecutors` 为 `Map<String, IQueryExecutor>` 查询执行器 bean 表（SessionFactoryConfig.java:157-165），非 ExecutorService；(2) `nop-dao/UpsertExecutors.java` 为 upsert 批处理工具类（类注释明示单语句/逐条事务策略），无线程创建。补充扫描 `new Thread(|Executors.|newFixedThreadPool|newCachedThreadPool|IThreadPool|ForkJoinPool|commonPool|parallelStream|scheduleAtFixedRate|new Timer(` 于 5 源码根 src/main：**零命中**。基线 C4 定时节一节为空（`== C4: scheduled/timer ==` `(empty)`），本轮复核确认（DaoDialectInitializer 的 Cancellable 仅包装组件注册注销，无调度）。
- **核心结论**: **ORM 持久层五源码根不创建任何线程或线程池**——如实记录，因此无队列上界/拒绝策略/池关闭责任问题；真实执行载体为三：调用方线程（伪异步 futureCall/futureRun）、context 任务队列排空线程（完成回调）、消费方线程（DaoEntityBlockingSource 轮询）。治理缺陷形态从"池配置"转移到"执行载体未声明、未隔离、不可观测"。

---

### [nop-persistence-rw-C4-001] 执行载体全景：全模块"Async"API 为调用方线程伪异步（futureCall 同步执行后返回已完成 promise），载体未声明且后续链式优化隐式依赖同步完成约定

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/jdbc/JdbcEntityPersistDriver.java:125-127, 188-190, 233-236`；`JdbcCollectionPersistDriver.java:104-106`；`nop-dao/src/main/java/io/nop/dao/txn/impl/AbstractTransaction.java:462-467`；`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/FutureHelper.java:122-138`（对照）
- **证据片段**:
  ```java
  // FutureHelper（api-core，对照）：futureCall/futureRun 均为"当场执行、当场完成"
  public static <T> CompletionStage<T> futureCall(Callable<?> task) {
      try { Object o = task.call(); return toCompletionStage(o); }   // 无 executor 参数，无线程切换
      ...
  // JdbcEntityPersistDriver
  public CompletionStage<Void> batchLoadAsync(...) {
      return FutureHelper.futureCall(() -> { ...jdbc().executeQuery(sql, ...); return null; });
  // AbstractTransaction
  protected CompletionStage<Void> doCommitAsync() { return FutureHelper.futureRun(this::doCommit); }
  ```
- **严重程度**: P2
- **并发场景**: 全部调用方线程——HTTP worker、GraphQL 执行线程、调度线程、DaoEntityBlockingSource 消费线程；不存在 ORM 专属执行线程。
- **触发时序**: 判定性登记而非竞态。任何调用方以异步 API 语义使用（预期"提交后返回、I/O 在别处执行"）时：T1 调用 `session.internalBatchLoadAsync`/`persister.batchLoadAsync`/`txn.commitAsync` → futureCall/futureRun 在 **T1 当前线程**同步执行全部 JDBC I/O → 返回的 future 已完成。后果一：API 名义异步、实际同步，调用方线程被数据库 I/O 占用（事件循环/Reactor 线程调用 ORM 即被 JDBC 阻塞，无任何提示）；后果二：本模块多处链式正确性优化**隐式依赖同步完成**——`EntityPersisterImpl.batchLoadAsync` L167 `shardFuture != FutureHelper.voidPromise()` 引用相等判空（依赖 `success(null)` 返回 VOID_PROMISE 单例，FutureHelper.java:79-81）、`FutureHelper.collectWaiting` L422-425 对已完成 future 的跳过、`BatchActionQueueImpl.flushAsync` L173/L196 的 `isError` 中断（C3-004 已立案其异步失效面）、`ContextProvider.thenOnContext` L208 的短路——任一扩展驱动改为真异步完成，这些点静默变义。
- **现状**: 同步完成约定无任何文档声明（接口 javadoc 未写"实现必须同步完成"），无运行时守卫。
- **风险**: 语义误导型契约缺陷：以异步语义编写的上层（并发装载多实体、并行多事务）实际全串行在单线程上，性能模型与代码形态背离；扩展驱动接入时链式优化点集体失效（与 C3-004 同根，本条登记载体面，不重复计数其拓扑后果）。
- **建议**: (1) 在 IEntityPersistDriver/ICollectionPersister/IOrmTemplate 异步接口 javadoc 明示"当前实现为调用方线程同步执行，返回已完成 promise"的载体契约；(2) 对依赖同步完成的引用相等优化点补充注释或改为语义等价的显式判断（如 `FutureHelper.isDone` 判定）；(3) 若未来引入真异步驱动，载体契约需升级为显式 Executor 注入点（平台设施 `io.nop.commons.concurrent.executor.IThreadPoolExecutor` 可复用，含 ThreadPoolConfig 队列/拒绝配置）。
- **失效面枚举（F3）**: 伪异步载体全点清单（均 futureCall/futureRun 同一形态，共享同一约定依赖）——(1) JdbcEntityPersistDriver.loadAsync:127；(2) batchLoadAsync:190；(3) batchExecuteAsync:236；(4) JdbcCollectionPersistDriver.batchLoadCollectionAsync:106；(5) AbstractTransaction.doCommitAsync:463 / doRollbackAsync:467；(6) ITransaction 默认 commitAsync/rollbackAsync（ITransaction.java:38/42）；(7) `IOrmEntityFileStore.batchLoadResource` 默认实现反向收敛（IOrmEntityFileStore.java:35-38 syncGet）。全部为同一判定的实例，非独立缺陷。
- **信心水平**: 确定
- **误报排除**: 非"原语使用误报"——登记对象是公开异步 API 的载体契约缺失与对它的隐式依赖，非 CompletableFuture 用法本身；也非性能臆测（调用方线程执行 JDBC 是代码可证明的事实）。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。futureCall 同步执行亲验成立；载体契约缺失与隐式依赖同步完成的判定成立

---

### [nop-persistence-rw-C4-002] 完成线程执行模型：session.flush/commit/rollback 等 JDBC I/O 落在"完成上游 future 的任意线程"上（BaseContext.runOnContext 由触发线程同步排空队列），无载体隔离、命名与可观测性

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/OrmTemplateImpl.java:255-269`；`nop-dao/src/main/java/io/nop/dao/txn/impl/TransactionTemplateImpl.java:195-224`；对照 `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/BaseContext.java:228-245, 295-308`、`ContextProvider.java:172-212`
- **证据片段**:
  ```java
  // OrmTemplateImpl.runInNewSessionAsync：flush/close 挂在完成回调上
  return ContextProvider.thenOnContext(future).whenComplete((ret, err) -> {
      try { if (err == null) { session.flush(); } }   // 完成回调线程执行整段 JDBC 批写
      finally { ... session.close(); }
  });
  // BaseContext（对照，默认运行时实现）自述：
  // "BaseContext是降级实现：不提供worker线程池，任务经runOnContext投递到context任务队列，
  //  无其他处理线程时在当前调用线程上同步执行"  (BaseContext.java:296-298)
  ```
- **严重程度**: P2
- **并发场景**: 上游 future 的完成线程（可能是任意池的工作线程/事件循环/定时器线程）成为 ORM 执行载体；同一 context 的任务队列由 `ContextTaskQueue` 保证单线程串行（beginProcess 的 processingThread 守卫，ContextTaskQueue.java:175-188），因此**不会并发**，但**执行线程不确定**且无隔离。
- **触发时序**: T1（请求线程）调用 `ormTemplate.runInNewSessionAsync(cb)`，cb 返回由下游线程池 W 完成的 future F。F 在 W 线程完成 → `thenOnContext` 包装的 promise 在 W 上触发 `context.execute` → `isRunningOnContext()` false → `runOnContext` 入队失败（无人在处理）→ **W 线程**绑定 context 并 `taskQueue.flush()` → 在 W 线程上执行 `session.flush()`：CascadeFlusher + 全部实体 INSERT/UPDATE/DELETE 批写 + 事务提交（`TransactionTemplateImpl` 的 completeAsyncOnContext handler → `commitAsync` → doCommit JDBC，同为 context 队列任务，同在 W 执行）。慢 SQL（秒级）即阻塞 W 线程相应时长；若 W 为事件循环线程（GraphQL/Reactor 异步链的典型完成线程），该 loop 上全部请求随之停摆。且 W 线程名/池归属与 ORM 无关，问题定位时线程栈不可归因（无载体命名、无指标）。
- **现状**: 垂直运行时（vert.x）可覆盖 executeBlocking 提供真 worker 线程池，但默认运行时按自述"在当前调用线程上同步执行"；ORM 层对"完成线程可能是什么"无约束、无检查、无文档。
- **风险**: 负载上升/慢查询时任意宿主线程池被 ORM I/O 侵占，故障面扩散到与数据访问无关的链路；liveness 风险的边界取决于宿主线程池（故判 P2 而非 P1——不构成数据损坏，且是否阻塞事件循环取决于上游运行时装配）。
- **建议**: (1) 文档声明完成线程执行模型（runInNewSessionAsync javadoc 补"flush 在上游 future 完成线程执行"）；(2) 中期提供 ORM 专属 flush executor 装配点（复用 `IThreadPoolExecutor`，有界队列 + CallerRunsPolicy 显式声明），将 context 队列的 JDBC 任务导向受控载体；(3) 为该路径补充可观测性（flush 耗时指标已有 `env.getOrmMetrics().onFlush()`，补载体线程池标识维度需防标签基数爆炸——C7 注意项）。
- **失效面枚举（F3）**: 在完成线程上执行 JDBC 的全部入口——(1) runInNewSessionAsync 的 whenComplete flush/close（缺陷本体主例）；(2) `TransactionTemplateImpl.runInTransactionAsync` completeAsyncOnContext handler 内的 rollbackAsync/commitAsync（L195-221，handler 经 thenOnContext 在 context 排空线程执行 JDBC commit/rollback）；(3) `AbstractTransaction.rollbackAsync/commitAsync` 的监听器回调段（L242-257/L334-347，onAfterCompletion 在 context 排空线程执行，监听器若做 I/O 同样侵占宿主线程）；(4) 对照无缺陷点：runInSessionAsync 既有会话分支（回调同线程，无跨线程完成）。四处共享同一载体模型，一处治理方案覆盖。
- **信心水平**: 确定（执行线程归属由 ContextProvider/BaseContext 代码直接可证；"W 可能是事件循环"取决于宿主装配，故影响面表述保守）
- **误报排除**: 不重审 api-core 自身实现（BaseContext 为降级实现属其声明边界——但"ORM 层将整段 JDBC I/O 交给完成线程"是**本模块的使用方式**决策，属 C4 审查范围）；非"无状态单例并发调用"误报。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。完成线程执行模型判定成立；「不判 P1」（是否阻塞事件循环取决于宿主运行时装配）的理由采纳

---

### [nop-persistence-rw-C4-003] 连接获取执行上下文：事务连接懒取（getConnection）可发生在完成回调/context 队列排空线程，阻塞式池获取在串行队列上放大停顿；跨线程字段交接经 CF happens-before 判定成立

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/txn/JdbcTransaction.java:53-75, 122-134`；`JdbcTransactionFactory.java:23, 48-64`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:121, 229-232`
- **证据片段**:
  ```java
  // JdbcTransaction：连接懒取，获取点 = 首次 SQL 执行点
  public Connection getConnection() {
      if (connection == null) {
          LOG.trace("nop.dao.txn.fetch-connection:conn={}", this);
          connection = dataSource.get();      // = JdbcTransactionFactory.getConnection → dataSource.getConnection()
          if (isTransactionOpened()) { if (JdbcHelper.getAutoCommit(connection, dialect)) ... }
  // OrmSessionImpl：会话绑定 context，操作须运行在 context 上（默认开启）
  private final IContext context = CFG_ORM_SESSION_CHECK_CONTEXT.get() ? ContextProvider.currentContext() : null;
  ```
- **严重程度**: P2
- **并发场景**: 事务对象在调用方线程创建（openTransaction），但首条 SQL 可能延迟到完成回调线程（C4-002 模型）或 DaoEntityBlockingSource 轮询线程（C3-006 模型）执行——连接获取点线程 ≠ 事务创建线程。
- **触发时序**: (a) 阻塞获取串行放大：T1 请求 context X 中 `runInNewSessionAsync` 的 flush 任务排队 → 线程 W 排空 X 队列 → `session.flush()` → `runWithConnection` → `txn.getConnection()` → `dataSource.getConnection()`（Hikari 池耗尽时阻塞至 connectionTimeout，默认可达数秒）→ **context X 的整条串行任务队列**在此期间停摆（单 processingThread 守卫，后续任务无法被其他线程接管——beginProcess L180"已有其他线程负责处理则放弃"），一次池等待放大为整个 context 的停顿。(b) 跨线程可见性判定：`connection` 字段非 volatile，创建线程懒取后由完成线程读取——交接链为 调用线程写 future → CompletableFuture 完成（fence）→ 完成线程被调度执行回调 → 读 `connection`，happens-before 成立（对照 api-core thenOnContext0 的 CF 链），**此项判定为无缺陷**，登记以闭合排查。
- **现状**: `eagerReleaseConnection` 默认 true（JdbcTransactionFactory.java:23），commit/rollback 后立即归还，缩小了跨线程持有窗口（对照 C3-006 的长窗口为例外形态）；懒取设计本身正确，缺陷在获取点的线程归属无治理。
- **风险**: 池竞争/慢获取时，停顿从"单个请求"放大为"单个 context 的全部串行后续"（含同 context 的其他异步 ORM 链）；与 C4-002 叠加使宿主完成线程同时承担"等池 + 跑 SQL"双重时长。
- **建议**: (1) 文档声明连接获取点=首个 SQL 执行点及其线程语义；(2) 对 context 队列内的任务引入获取超时显式配置（Hikari connectionTimeout 已有，补 ORM 层声明与告警阈值）；(3) 长期看与 C4-002 的 flush executor 方案合并治理（连接获取随 flush 任务迁移到受控载体）。
- **失效面枚举（F3）**: 连接获取点全清单及线程归属——(1) `JdbcTransaction.getConnection`（懒取，缺陷本体）；(2) `JdbcTransaction.doOpen`（openTransaction 同步段调用，调用方线程，无跨线程问题）；(3) `JdbcTransactionFactory.openConnection`（独立池连接，设计裁定注释 L67-69 明示不参与事务，调用方线程）；(4) `DialectManager.java:81`（方言探测，启动/低频路径，合理）；(5) `ReadWriteRoutingDataSource.getConnection`（调用方线程同步路由，C6 连接释放面移交，不属 C4）。(1) 为唯一跨线程获取点。
- **信心水平**: 很可能（(a) 的串行放大链路各环节均由代码证明，"池耗尽阻塞"为 Hikari 标准行为，触发依赖池竞争强度；中 (b) 无缺陷为确定判定）
- **误报排除**: 已显式区分"无缺陷判定"（可见性）与"缺陷"（获取点线程治理缺失），未将非 volatile 字段臆报为竞态；不属"平台基础设施自身实现"（连接获取决策在本模块）。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。(b) 跨线程可见性无缺陷判定复核确认（CF happens-before 链成立）；(a) 懒取连接获取点线程治理缺失立案成立

---

## C4 附：线程池/线程创建点清单（盘点交付物——空表结论）

| 扫描项 | 命中 | 判定 |
|---|---|---|
| 自建线程池（IThreadPoolExecutor/StandardThreadPoolExecutor/JDK Executors） | 0 | 无自建池，无关闭责任、无队列上界/拒绝策略问题 |
| `new Thread(` | 0 | 无 |
| commonPool 间接使用（parallelStream/无 executor 的 runAsync/supplyAsync） | 0 | 全部 futureCall/futureRun 均当场执行 |
| ForkJoinPool/NopForkJoinWorkerThread | 0 | 无 |
| 定时任务（scheduleAtFixedRate/Timer） | 0 | 基线 C4 定时节为空，复核一致 |
| 载体误报排除 | SessionFactoryConfig.queryExecutors（查询执行器 bean 表）、UpsertExecutors（upsert 工具类） | 均非线程池 |
| 真实执行载体 | 调用方线程（伪异步，C4-001）、context 队列排空线程（完成回调，C4-002）、消费方线程（DaoEntityBlockingSource 轮询，C3-006 承载） | 治理缺口在载体契约而非池配置 |

**CL 交叉引用**: DaoEntityBlockingSource 轮询/连接持有与抢占模型的完整条目见 `C3-async-chains.md` C3-006（执行载体面与本表第 8 行互引）；flushAsync 同步完成依赖的拓扑后果见 C3-004（本报告 C4-001 只登记载体契约面）。

---

## 第 2 轮深挖（无新增）

- **维度**: C4 线程池与执行器治理｜**批次**: 深挖合并批（第 2 轮，全部 5 源码根）｜**仓库/分支**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（`concurrency-audit-remediation`）
- **盲审纪律声明**: 第 2 轮仍为盲审。未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件（唯一例外为本目录 7 个第 1 轮初审文件，必要输入）。
- **复核范围**: 第 1 轮结论「ORM 持久层五源码根不创建任何线程或线程池」的否定性验证——本轮以第 2 轮深挖过程为载体复核：C2 维度对事务 API 全调用面（11 类入口）与 C3 维度对 runInSession 全调用点（8 类调用方）的穷举中，全部执行载体仍收敛于第 1 轮判定的三类（调用方线程伪异步 / context 队列排空线程 / 消费方轮询线程），**未经过任何新线程/池创建点**；grep 补充扫描 `new Thread(|Executors.|IThreadPool|scheduleAtFixedRate|CompletableFuture.*Async(` 于 5 根 src/main 仍为零命中（CompletableFuture 的 async 工厂全部经 FutureHelper/context 设施，无线程创建）。
- **新增发现**: 无（C4-004 起编号保留）。第 1 轮 3 条发现（C4-001 载体契约、C4-002 完成线程执行模型、C4-003 连接获取执行上下文）维持原判级；第 2 轮 C2/C3/C6 深挖产出的关联事实（事务完成回调在 context 排空线程执行 JDBC 的调用面枚举、drainTo 消费线程 K 量化）分别由 `C2-round2.md`/`C3-round2.md`/`C6-round2.md` 承载，本维度不重复计数。
- **终止判定**: 终止，不建议第 3 轮。本维度为盘点型结论（零自建线程），无未闭合检查项。

## 维度复核结论（独立复核合并批，2026-10-18）

- **复核方式**: 与初审/深挖代理独立的第 2 轮合并复核批。本维度 3 条发现证据逐条亲验（P2×3，无 P1）；历史对照面已解除盲审。本维度无独立 round2 文件，第 2 轮否定性验证（五源码根零自建线程/池）已内嵌于本文件「第 2 轮深挖（无新增）」节，本轮复核确认其结论。
- **裁定统计**: 3 条全部**保留**（降级 0、驳回 0）——P2×3 维持。
- **判级复核**: C4-001（伪异步载体契约缺失）、C4-002（完成线程执行模型）、C4-003（连接获取上下文）三条均为「治理/契约缺失」型登记而非竞态立案，P2 判级一致（不构成数据损坏、影响面取决于宿主装配）——复核采纳。C4-003(b) 的「跨线程可见性无缺陷」判定复核确认（CF happens-before 链成立），该否定性结论维持。
- **盘点交付物终裁**: 「五源码根不创建任何线程或线程池」的否定性验证维持（真实执行载体三类：调用方线程伪异步 / context 队列排空线程 / 消费方轮询线程）；基线 C4 节两处命中（SessionFactoryConfig.queryExecutors、UpsertExecutors）非线程池的误报排除维持。
- **CL 处置**: 本维度无自有 CL 登记（CL-400/C3-006 交叉引用归 C2/C3 维度台账）。

## 最终保留项

| ID | 初判 | 终裁 | 条目 |
|---|---|---|---|
| C4-001 | P2 | P2 | Async API 伪异步载体契约缺失 |
| C4-002 | P2 | P2 | 完成线程执行模型无载体隔离 |
| C4-003 | P2 | P2 | 连接获取执行上下文（懒取跨线程） |

**维度终局**: P2×3，无驳回、无撤案；盘点结论（零自建池）维持。
