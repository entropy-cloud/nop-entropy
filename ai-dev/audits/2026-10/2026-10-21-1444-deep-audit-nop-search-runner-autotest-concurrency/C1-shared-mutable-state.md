# C1 共享可变状态与可见性 — nop-search-runner-autotest（WI 4.5）

- **审计员**: 独立盲审初审员（合并批 C1+C4+C6）
- **日期**: 2026-10-21
- **盲审纪律**: 未阅读 `ai-dev/audits/**`（本文件除外）、`ai-dev/plans/**`、`ai-dev/bugs/**`、`ai-dev/lessons/**`、`ai-dev/backlog/**`；未执行 git log。契约以代码与 javadoc 为准。

## 审计范围与口径

- **合并批三维度覆盖声明**: 本文件承担 C1（共享可变状态与可见性）维度；C4（线程池与执行器治理）见 `C4-thread-pools.md`；C6（资源生命周期与泄漏）见 `C6-resource-lifecycle.md`。同根缺陷按维度拆面登记并互相交叉引用，合并裁定留给复核阶段。
- **审计范围**: 8 源码根、112 文件 / 11,116 行，逐文件审读——nop-search（api 8f/652、core 4f/324、lucene 5f/1,586）、nop-runner（cli-core 29f/2,447、cli 1f/11、tool 11f/795）、nop-autotest（core 47f/4,822、junit 7f/479）。
- **并发面极稀疏预校准声明**: 基线 `_tmp/cra-baseline-nop-search-runner-autotest.txt` 原语命中约 10/112 文件（LuceneSearchEngine 三 CHM、AutoTestVars ThreadLocal+synchronized、TestClock AtomicReference、MockScheduledExecutor、EntityRow、FixtureBundleRecordingSession ReentrantLock、CliWatchCommand/CliRunCommand state CHM、CliCallToolsCommand SyncThreadPoolExecutor、FileWatcherFactory、SourceCounter CHM+AtomicLong）。本审计不凑数扩面：其余约 100 个文件为一次性 CLI 命令、数据 Bean、迁移/校验工具，经审读确认无跨线程共享可变状态，登记于下方"干净清单"，不产出发现。
- **dbtool 零主源码声明**: `nop-autotest/nop-autotest-dbtool` 无 `src/main/java`（仅 4 个测试文件，已逐一核实），无主源码可审，此事实随本批登记。
- **owner doc 零覆盖声明**: 本批未读取 `docs-for-ai/` 模块 owner 文档（盲审口径下 owner doc 零覆盖），接口契约以源码 javadoc 为准——含 `ISearchEngine`/`ITextEmbedding` SPI javadoc、`TestClock` 类级契约注记、`LuceneSearchEngine.rollback` 修复注记、`CliWatchCommand.WatcherHandle` 双路径注记。
- **降档口径**: nop-autotest 为测试期执行框架（非生产请求触达），"仅测试期单线程触达"站点按条件化降档（P2→P3 或标注条件）；nop-search / nop-runner 按实际触达面判级（search 为可部署搜索引擎 bean，runner CLI watch 模式为其实际使用形态）。

## 共享可变状态清单表

| 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| `indexDirs`/`indexWriters`/`searcherManagers` 三 CHM | LuceneSearchEngine.java:134-136 | getDirectory/getIndexWriter/getSearcherManager（computeIfAbsent）、removeTopic、rollback、destroy | 全部搜索/写入路径 | CHM computeIfAbsent + remove；无生命周期守卫 | **缺陷 C1-001/C6-003** |
| `analyzer`、`config`、`rootPath`（singleton bean 实例字段） | LuceneSearchEngine.java:133,137-138 | init()、destroy()（置 null） | analyzeDoc/analyzeQuery/buildQuery/getIndexWriter | 无（依赖 IoC 初始化先于请求）；destroy 置 null 与在途读者竞态 | 并入 C6-003 |
| `state`（globalState CHM，脚本可变） | CliWatchCommand.java:108,147 | processEvents（debounce 定时线程 + 初始主线程调用）；XPL 脚本 | 同左 + 后续脚本执行 | CHM 原子；脚本级复合操作无守卫 | **缺陷 C1-004**（重叠执行） |
| `globalState` CHM | CliRunCommand.java:70 | call() 主线程、scheduleWithFixedDelay 定时任务 | runTasks 脚本 | CHM；主线程写先于 schedule（HB 经任务提交） | 干净 |
| `started`（volatile）+ `tasks`（DelayQueue） | MockScheduledExecutor.java:17-18 | schedule×3、destroy | triggerNext/pollTask | volatile + DelayQueue 内部锁；`started` 从不被读 | **缺陷 C1-003/C4-003/C6-004** |
| `t_vars` ThreadLocal、`g_vars` 静态 VarsMap、`useGlobalVars` static boolean | AutoTestVars.java:34-36 | makeVarsMap/setUseGlobalVars/clear | getVarsMap 全族 | ThreadLocal + VarsMap 方法级 synchronized；旗标无 volatile | **缺陷 C1-005**（测试期条件化） |
| `s_anchor` AtomicReference\<Anchor\>（Anchor 不可变） | TestClock.java:38-49 | installAnchor/clearAnchor | currentTimeMillis | AtomicReference 发布 + 实例 synchronized | 干净（锚点跨类持久为 javadoc 已声明契约） |
| `lastTime` | TestClock.java:36 | currentTimeMillis | 同左 | 实例级 synchronized | 干净 |
| `dataMap` 双层 CHM + `loadedTables` CHM-Set | AutoTestOrmHook.java:21-22 | onRead/onUpdate/.../postXxx（computeIfAbsent） | getDataMap（AutoTestCaseDataSaver 迭代） | 双层 CHM（异 map 嵌套 computeIfAbsent，无同 map 递归）+ EntityRow 方法锁 | 干净（内层值对象见 C1-006） |
| `initData`/`changedData` HashMap、`load`/`changeType` | EntityRow.java:24-28 | onLoad/onSave/onUpdate/onDelete（synchronized） | isLoadData/hasChangedData/getInitData/getChangedData（无锁） | 写侧 synchronized，读侧裸读 | **缺陷 C1-006**（测试期条件化） |
| `EXCLUSIVE_LOCK` static ReentrantLock | FixtureBundleRecordingSession.java:32 | run()（tryLock fail-fast + try/finally unlock） | — | ReentrantLock | 干净 |
| `counterByFileExt` CHM\<String,AtomicLong\> | SourceCounter.java:18 | count()（computeIfAbsent→addAndGet/incrementAndGet） | getResults | computeIfAbsent + AtomicLong，复合操作正确 | 干净（单线程 CLI 使用） |
| 注入字段（searchEngine/resourceLocator/enhancer） | SearchEngineBizModel.java:44-48 | @Inject setter（容器初始化期） | biz action | 初始化后只读 | 干净（无状态单例被并发调用=设计意图，误报校准排除） |
| per-request 新建 | LuceneFilterBeanTransformer、BuildIndexTool、AutoTestCaseDataSaver、TagVarCollector、CsvDataDiffer.INSTANCE（无状态） | — | — | — | 干净 |
| autotest 全局静态集群（CoreInitialization/VarCollector/BaseTestCase/AppConfig） | NopJunitExtension.java:30-47,75-85 | beforeAll/afterAll | 全 JVM | 无并行守卫 | **缺陷 C1-007**（测试期条件化） |

## 发现

### [C1-001] LuceneSearchEngine removeTopic/rollback 与在途读写竞态——已关闭 writer/manager 的瞬态失败与错误 rollback 链

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:825-858,1092-1115,1117-1173`
- **证据片段**:
  ```java
  // :830 addDocs 取缓存 writer；:848 提交后经 map 裸 get 通知刷新
  IndexWriter writer = getIndexWriter(topic);
  ...
  SearcherManager manager = searcherManagers.get(topic);
  if (manager != null) {
      manager.maybeRefresh();
  }
  ...
  } catch (Exception e) {
      rollback(topic, writer);          // :855
  // :1130 removeTopic 关闭缓存的 writer；:1158 rollback 对（可能已关闭的）writer 再 rollback
  IndexWriter writer = indexWriters.remove(topic);
  if (writer != null) { writer.close(); ... }
  ```
- **严重程度**: P2
- **并发场景**: HTTP/GraphQL worker 线程执行 search/getDoc/addDocs（SearchEngineBizModel 入口）与管理操作 removeTopic、失败路径 rollback、停机 destroy 并发。
- **触发时序**: T1 addDocs 执行到 :830 拿到缓存 writer（引用逃逸出 map）→ T2 removeTopic 执行到 :1130-1137 `indexWriters.remove(topic)` + `writer.close()`，:1140-1147 关闭 Directory → T1 继续 :837 `writer.deleteDocuments(terms)` → AlreadyClosedException → T1 进入 :855 rollback → :1159-1167 对已移除/已关闭的 manager/writer 二次 close/rollback，异常被 `LOG.error("nop.search.rollback-fail")` 吞掉（F9 残留）。同族：T2 rollback（写入失败路径）关闭 manager 时，T3 getDoc 已在 :320 持有该 manager 引用，:322 `manager.maybeRefresh()`/:323 `acquire()` 抛 AlreadyClosedException → NopException 直接打给调用方。
- **现状**: 三个 CHM 的条目被 removeTopic/rollback 移除并关闭时，已在途的本地引用无任何失效协调；失败路径对已关闭资源的二次释放依赖 Lucene 幂等性且异常仅记日志。
- **风险**: 单 topic 管理操作（removeTopic）与并发写入/搜索交叠时产生用户可见瞬态错误与误导性 rollback-fail 日志；不产生索引损坏（Lucene 文件层有防护），故非 P0/P1。
- **建议**: 引入 per-topic 读写协调（如 `ConcurrentHashMap<String, ReentrantReadWriteLock>` 或复用平台 `IResourceLockManager`）：变更/移除路径持写锁，搜索/写入路径持读锁；rollback 中对 `writer.isOpen()`（`writer.isOpen()` 存在于 IndexWriter API）预判后再 rollback，并对二次 close 显式吞 AlreadyClosedException。
- **失效面枚举（F3）**: 同族入口逐一核查——`addDocs`(:825)/`removeDocs`(:1092) 失败路径均走 `rollback`（同缺陷）；`removeTopic`(:1117) 关闭三资源（同缺陷施害方）；`destroy`(:179) 同族但归 C6-003；`refreshBlocking`(:304) 裸取 manager 后 `maybeRefreshBlocking`（同受害者）；三个 CHM 的 computeIfAbsent 获取点 `getDirectory`(:264)/`getIndexWriter`(:289)/`getSearcherManager`(:277) 为共享施害/受害面。全部登记，无一豁免。
- **信心水平**: 确定（交错序列全部可由代码直读）
- **误报排除**: 非"看到共享字段就报线程不安全"——CHM/IF 原语使用本身正确（误报校准第 2 条），缺陷在条目生命周期与在途引用的协调缺失，且有完整 T1/T2 交错与用户可见后果。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C1-002] addDocs 复合操作（delete+add+commit+refresh）非原子，同 topic 并发写无串行化守卫

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:825-858,1092-1115`
- **证据片段**:
  ```java
  IndexWriter writer = getIndexWriter(topic);
  try {
      Term[] terms = docs.stream().map(doc -> new Term(FIELD_ID, doc.getId())).toArray(Term[]::new);
      writer.deleteDocuments(terms);      // 步骤1：删旧
      ...
      writer.addDocuments(documents);     // 步骤2：加新
      writer.commit();                    // 步骤3：提交
  ```
- **严重程度**: P2
- **并发场景**: 多个 GraphQL worker 并发调用 `SearchEngineBizModel.addDocs`（@BizMutation，同一 topic），共享同一 `IndexWriter`。
- **触发时序**: T1 addDocs(docId=D) 执行到 :837 deleteDocuments → T2 addDocs(docId=D) 执行到 :837 deleteDocuments → T1 :843 addDocuments(D v1) + :845 commit → T2 :843 addDocuments(D v2) + :845 commit。Lucene buffered delete 按代际（generation）施加：早于文档加入代际的删除不作用于该文档，终态 v1、v2 两份同 docId 文档并存。T3 getDoc(:320-331) `search(query,1)` 任取其一 → 陈旧版本可能被返回且持续到该 doc 下一次 addDocs 周期。
- **现状**: delete-then-add 复合操作无 per-topic 互斥，正确性依赖调用方自证单线程（无运行时守卫，F10 家族）；SPI javadoc 未声明任何线程约束。
- **风险**: 并发索引同步场景（如多 worker 刷新同一 topic）出现重复/陈旧命中，语义违约但不损坏索引文件；无具体生产并发调用方实证，故 P2 而非 P0。
- **建议**: per-topic 写互斥（与 C1-001 建议的协调设施合并）；或在 `ISearchEngine` javadoc 明文声明实现类非线程安全的写契约并加 fail-fast 守卫。
- **失效面枚举（F3）**: `addDoc`(ISearchEngine.java:37 默认方法→addDocs，同缺陷)；`addDocs`(:825)、`removeDocs`(:1092)（removeDocs 纯删除，并发交叠语义可交换，无此问题——已逐一区分）；`SearchEngineBizModel.addDoc/addDocs/removeDocs`(:192-210) 为并发入口面；`BuildIndexTool.indexAll`(:45-80) 单线程 BatchQueue 调用 addDocs，不受影响。
- **信心水平**: 很可能（交错序列具体；终态依赖 Lucene buffered-delete 代际语义，未做实验证实）
- **误报排除**: 非指摘 IndexWriter 线程安全原语本身（Lucene writer 线程安全，误报校准排除项），而是复合业务操作（删旧+加新）的原子性缺失。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C1-003] MockScheduledExecutor 测试替身契约破坏：started 死旗标、DelayQueue 恒不过期、TimeUnit 被丢弃、周期任务不复排

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/mock/MockScheduledExecutor.java:17,25-57,66-69,102-104`；`nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/mock/MockTask.java:27-34,40-50`
- **证据片段**:
  ```java
  private volatile boolean started = true;      // :17 全类无任何读取点（死旗标）
  public synchronized <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
      ...
      MockTask task = new MockTask(callable, delay, 0, MockTaskKind.ONCE, promise); // unit 被丢弃
  // MockTask :27-28
  public long getDelay(TimeUnit unit) {
      return unit.convert(initialDelay, TimeUnit.MILLISECONDS);   // 常量，不锚定入队时刻
  ```
- **严重程度**: P2
- **并发场景**: 测试替身（IScheduledExecutor 实现），由测试线程经 `triggerNext()` 手动驱动；当前仓库内零调用方（已 grep 全仓核实）。
- **触发时序**: 非交叠竞态而是契约性缺陷复合体——(a) T-test `schedule(task, 500, MILLISECONDS)` 后调用 `triggerNext()` → `pollTask()`(:66-69) 用 `DelayQueue.poll()`（仅返回已过期元素），而 `getDelay` 恒返回常量 500ms>0 → 任务永不过期、`triggerNext` 永远返回 null；(b) `destroy()`(:102-104) 置 `started=false` 后 `schedule` 照常入队（死旗标无守卫，F10）；(c) `SCHEDULED_AT_FIXED_RATE/WITH_FIXED_DELAY` 类型的 MockTask `call()`(:40-50) 执行一次后不复排入队，周期语义未实现；(d) `schedule(callable, d, SECONDS)` 的 unit 参数被丢弃，按毫秒解释。
- **现状**: 替身的线程安全原语（synchronized + DelayQueue）使用无误，但时间语义、生命周期语义、周期语义三重违背 IScheduledExecutor 契约；javadoc 无任何"仅支持 delay=0"之类的边界声明。
- **风险**: 后续任何测试以该替身替换真执行器时，延迟任务静默不可触发、destroy 后任务继续入队——测试假绿/假挂；因当前零调用方且属测试期代码，不判 P1。
- **建议**: 要么修复（入队时锚定 `System.nanoTime()`、读取 started 守卫、周期任务执行后重算 delay 再入队、unit 归一为毫秒），要么在 javadoc 声明"仅支持 delay=0 的手动单步驱动"边界并删去 started/period 死代码。
- **失效面枚举（F3）**: 同类全方法——`schedule`(:25)/`scheduleAtFixedRate`(:36)/`scheduleWithFixedDelay`(:48)/`submit`×2(:87,:92)/`execute`(:107) 全部汇入同一缺陷链；`triggerNext`/`pollTask`(:59-69) 为触发面；`MockTask.getDelay/compareTo`(:27-34) 为语义面。`scheduleWithFixedDelay` 未 synchronized 与其余两入口不对称（DelayQueue 内部线程安全，无实际数据竞争——登记为风格不一致，不计发现）。
- **信心水平**: 确定（全部为代码直读语义，DelayQueue.poll 过期语义为 JDK 契约）
- **误报排除**: 不属于"测试专用代码简化并发模型不按生产标准报 P1"的豁免范围——本条不按 P1 报，但替身契约正确性是本维度对测试基建的明确检查项（C1 审计要点第 3 条），且零豁免声明存在于缺陷本体（无边界声明）。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C1-004] globalTimer 多线程下 debounce 任务重叠执行：CliWatchCommand globalState 复合操作竞态与 CliWatchZipCommand 同目标 zip 并发写

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliWatchCommand.java:107-151`；`nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliWatchZipCommand.java:99-137`；平台侧根因见 CL-005
- **证据片段**:
  ```java
  // CliWatchZipCommand :128
  executor.debounce("refresh", debounceWait, () -> {
      try {
          ...
          ResourceHelper.zipDir(new FileResource(dir), new FileResource(zipFile), null);
  // CliWatchCommand :126
  watcher.watch(..., debounceWait, events -> processEvents(xpl, events, state)); // state 为跨执行共享 CHM
  ```
- **严重程度**: P2
- **并发场景**: NIO watch 线程投递事件 → debounce 任务执行于 `GlobalExecutors.globalTimer()`（默认 maxPoolSize=5，`CommonConfigs.java:22-24` 已核实）；上一次 debounce 任务仍在执行时新事件到来。
- **触发时序**: T1 timer-1 执行 zip 任务（大目录 zipDir 耗时 > debounceWait=100ms）→ T2 NIO watch 线程因新文件事件调用 debounce → `RateLimitExecutorImpl.debounce`(:52-54) 对运行中任务 `old.cancel(true)` 仅置中断位（文件 I/O 不响应中断，T1 继续写）→ 50ms+ 后 timer-2 线程执行新任务 → 两个 ZipOutputStream 并发写同一 `zipFile` → 产物损坏或 IOException。CliWatchCommand 同机制：两次 `processEvents` 重叠执行，XPL 脚本对 `globalState` 的 check-then-act（读-改-写）跨执行竞态，CHM 原子性不覆盖脚本级复合操作。
- **现状**: debounce 被当作"串行化 + 防抖"使用，但平台 debounce 只取消未启动任务、不等待在跑任务；使用方无 in-flight 守卫。
- **风险**: watch-zip 长期运行下必然出现大目录场景 → zip 产物损坏；watch 脚本状态串读。均为实际触达面（runner CLI 功能本体），非测试期。
- **建议**: 使用方加 in-flight 标志（AtomicBoolean compareAndSet，运行中则置 dirty 位、完成后补偿执行一次），或平台 debounce 提供"运行中到达则运行后重跑"语义（见 CL-005）。
- **失效面枚举（F3）**: debounce 使用点全查——`CliWatchZipCommand.scheduleZipUpdate`(:127-136)（zip 并发写，最重）、`CliWatchCommand.doWatch`→`FileWatcher.watch`→`triggerChange`(:70, nop-core，经 RateLimitExecutorImpl 同族)（脚本重叠）、`FileWatcher` 内 debounce("process") 同 key 串行化假设（同 CL-005）。throttle 使用点在本批 8 根内无使用。`CliRunCommand` 不经 debounce（见 C4-001 独立缺陷）。
- **信心水平**: 确定（globalTimer 默认 5 线程 + cancel 不 join 均已核实）
- **误报排除**: 非平台设施本体缺陷报告（本体归 CL-005 overlay），本条是使用方在"多线程调度器 + 不可中断长任务"事实下缺少重叠守卫的使用缺陷。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C1-005] AutoTestVars 全局静态三件套：clear() 全局擦除竞态、ThreadLocal 池化残留链、useGlobalVars 非 volatile

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/data/AutoTestVars.java:34-36,151-163,170-191`
- **证据片段**:
  ```java
  private static final ThreadLocal<VarsMap> t_vars = new ThreadLocal<>();
  private static boolean useGlobalVars = true;      // 非 volatile，无同步
  private static final VarsMap g_vars = new VarsMap();
  ...
  public static void clear() {
      t_vars.remove();
      g_vars.clear();                               // JVM 全局擦除
  ```
- **严重程度**: P3
- **并发场景**: JUnit 并行类执行（junit-platform parallel 默认关闭、需显式开启）或同 fork 内非框架代码直用 AutoTestVars；均为测试期触达。
- **触发时序**: T1 测试类 A 的 `complete()` 执行到 :251 `AutoTestVars.clear()` → g_vars 被整体清空 → T2 并行测试类 B 的 `EntityRow.registerVar`(:95-98) 正向 g_vars `addVar` → B 的输出快照变量被擦除，checkOutput 阶段报伪失败。ThreadLocal 残留链：`useGlobalVars=false` 期间 `makeVarsMap`(:185-189) `t_vars.set(...)`，若该测试未走 `AutoTestCase.complete()` 的 finally 清理（如非 JunitAutoTestCase 体系直接调用，已实证存在：nop-code-service 测试直用 AutoTestVars），值残留于 JUnit 池化 worker 线程 → 后续切回 false 的测试类继承陈旧变量集 → 跨类污染。
- **现状**: 全局变量池为 JVM 单例，框架生命周期（initDao:165 clear → complete:251 clear）在顺序执行下配对正确；并行/直用场景无防护，`useGlobalVars` 运行时切换无同步与文档声明。
- **风险**: 仅测试期：并行套件伪失败/伪通过、变量跨测试类泄漏。按"仅测试期触达"条件化降档 P3。
- **建议**: javadoc 声明"不支持并行测试类"边界（框架级假设，见 C1-007）；`useGlobalVars` 加 volatile 或收敛为不可变配置读取；直用场景提供 try-with-resources 风格的 VarsScope。
- **失效面枚举（F3）**: 写点全查——`addVar`(:199)/`setVar`(:203)/`setVars`(:207)/`makeVarsMap`(:181)/`clear`(:159)/`setUseGlobalVars`(:155)；读者 `getVarsMap`(:170) 分叉选择全局/线程局部是竞态放大器。调用方：AutoTestCase(:165,:251,:421,:425)、EntityRow:97、AutoTestCaseResultChecker:174、TagVarCollector:69、AutoTestCaseData:123,:131、AutoTestVarCollector:22——全部登记。
- **信心水平**: 确定（机制）；触发依赖并行套件（条件）
- **误报排除**: 非"测试专用简化并发"豁免——变量池正确性直接影响测试有效性；降档而非豁免。顺序执行下框架自身配对正确，故不虚报 P2。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C1-006] EntityRow 内部 HashMap 经 getter 逃逸，读侧裸读非同步字段

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/execute/EntityRow.java:24-28,42-56`
- **证据片段**:
  ```java
  private final Map<String, Object> initData = new HashMap<>();
  private final Map<String, Object> changedData = new HashMap<>();
  private boolean load;                       // 裸字段
  private EntityChangeType changeType;
  ...
  public Map<String, Object> getInitData() { return initData; }   // 内部可变集合逃逸
  public boolean isLoadData() { return load && !initData.isEmpty(); } // 无锁读
  ```
- **严重程度**: P3
- **并发场景**: ORM 回调线程（写，synchronized）与 AutoTestCaseDataSaver 保存线程（读，无锁）。autotest 快照录制为测试期单线程顺序执行（saveOutput 在 complete() 内、测试线程上），实际并发触达不存在。
- **触发时序**: 理论时序——T1 ORM 会话线程 `onUpdate` 持行锁执行 :91 `changedData.put` → T2 保存线程经 :54 getChangedData 裸迭代同一 HashMap → 并发修改异常/脏读。当前框架内 T1、T2 同线程，序列不可达；若业务测试在 ORM 异步回调中触发 hook 则可达。
- **现状**: 写侧四个方法 synchronized 正确，读侧五个访问器（getInitData/getChangedData/isLoadData/hasChangedData/getChangeType）完全无同步且泄漏内部集合引用（隐式共享逃逸类，C1 执行步骤 1e）。
- **风险**: 测试框架被用于异步/多线程被测代码时快照数据撕裂（F1 邻形）；当前使用形态下无实害。测试期条件化降档 P3。
- **建议**: 读侧同步化（返回 defensive copy）或改用不可变快照对象；至少在类 javadoc 声明单线程契约。
- **失效面枚举（F3）**: 同类兄弟逐一登记——onLoad(:58)/onSave(:69)/onUpdate(:74)/onDelete(:80)（写侧，已同步）；onLoad:60-61 的 `changeType==null` 判断-置位复合操作在锁内（原子，干净）；5 个读访问器（缺陷面）。使用方 AutoTestCaseDataSaver:87,:109-111（裸读点）。
- **信心水平**: 确定（代码直读）；可达性依使用形态
- **误报排除**: 非"HashMap 本身不是线程安全"的泛化报告——写侧已正确同步，缺陷特定于读侧不对称与集合逃逸，且已按实际触达降档。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C1-007] autotest 框架全局静态状态集群无并行守卫：CoreInitialization/VarCollector/BaseTestCase/AppConfig 跨测试类全局翻动

- **文件**: `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/NopJunitExtension.java:30-47,75-85`；`nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/AutoTestCase.java:211-225,227-254`
- **证据片段**:
  ```java
  // beforeAll
  AppConfig.getConfigProvider().reset();
  ...
  ResourceComponentManager.instance().clearAllCache();
  CoreInitialization.initialize();
  // afterAll
  CoreInitialization.destroy();
  CoreMetrics.registerClock(CoreMetrics.defaultClock());
  ```
- **严重程度**: P3
- **并发场景**: JUnit 并行类执行模式下，两个测试类的 beforeAll/afterAll 并发翻动同一 JVM 全局（IoC 容器 initialize/destroy、模型缓存清空、配置 reset、VarCollector 静态注册）。
- **触发时序**: T1 类 A afterAll 执行 `CoreInitialization.destroy()` → T2 类 B beforeAll 处于 initialize 中途 → 容器被半拆毁，B 的 `BeanContainer.tryGetBean` 返回 null 或半初始化 bean（AutoTestCase.initDao:200-208 tryGetBean 链）→ NPE 或静默错数据。无任何"检测到并行即 fail-fast"守卫（F10）。
- **现状**: 框架对"JVM 内顺序执行测试类"的假设未在任何 javadoc/文档声明，也无运行时检测。
- **风险**: 用户开启 JUnit 并行后获得不可复现的批量伪失败，难以归因。测试期条件化降档 P3。
- **建议**: beforeAll 用静态 AtomicBoolean 检测并发进入并 fail-fast 报出"autotest 框架不支持并行测试类"；文档声明该边界。
- **失效面枚举（F3）**: 全局翻动点全查——`NopJunitExtension.beforeAll`(:30-47) reset/clearAllCache/initialize、`afterAll`(:75-85) destroy/registerClock/reset、`AutoTestCase.initBeans`(:211-225) container.restart()（每测试方法重启全局容器，最强翻动点）、`AutoTestCase.initDao`(:167) VarCollector.registerInstance、`complete`(:232) 重置为 no-op。TestClock 锚点跨类持久为已声明契约（javadoc 注记 2），不计入。JunitBaseTestCase:24 复用同 Extension（同面）。
- **信心水平**: 确定（机制）；触发条件为并行模式
- **误报排除**: 平台设施（CoreInitialization 等）本体不重审（误报校准第 9 条），本条针对的是 autotest 框架对全局设施的使用编排缺守卫与边界未声明。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

## 维度集外线索（overlay C2/C3/C5/C7/C8/C9）

### [nop-search-runner-autotest-CL-001] FileWatcher.processing volatile check-then-act 丢失唤醒（平台 nop-core）
- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/watch/FileWatcher.java:66-71,106-124`
- **线索**: `triggerChange` 的 `if (processing) return;` 与 `processChangeEvent` 的 `processing=true → handler → processing=false` 构成非原子 check-then-act：事件在最后一次 `queue.drainTo` 判空之后、`processing=false` 之前到达时被入队但不调度 debounce → 该批事件滞留到下一次文件变化才被处理（丢失唤醒）。overlay C2（同步原语复合操作）/C9。影响使用方 CliWatchCommand（本批 C4-002 关联）。
- **归属**: nop-core（框架核心），不随本 WI 立项，移交平台并发审计单元。

### [nop-search-runner-autotest-CL-002] BuildIndexTool.indexAll 全文件读入内存无大小上界（C5/C8 overlay）
- **文件**: `nop-search/nop-search-core/src/main/java/io/nop/search/core/index/BuildIndexTool.java:63`
- **线索**: `doc.setContent(FileHelper.readText(file, null))` 对 indexDir 白名单内任意命中文件全量载入，无单文件大小上限；经 `SearchEngineBizModel.indexDir` admin 触达。C5（内存有界性）线索，非本批维度。

### [nop-search-runner-autotest-CL-003] IOrmSessionFactory add/removeDaoListener 列表的并发契约（C2 overlay）
- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/AutoTestCase.java:180-181,235-236`
- **线索**: autotest 在测试线程对共享 SessionFactory 注册/注销 listener 与 interceptor，ORM 回调可能在其他线程遍历该列表——注册点的线程安全契约由 nop-orm 侧实现决定，本批未审本体。测试期单线程下无实害；overlay C2。

### [nop-search-runner-autotest-CL-004] nop-autotest-dbtool 零主源码核对登记（登记类，非缺陷）
- **文件**: `nop-autotest/nop-autotest-dbtool/`（无 src/main/java）
- **线索**: 已核实仅 4 个测试文件（TestJdbcMetaDiscovery/TestOrmDbDiffer/TestOrmModelDiffer/TestDataBaseUpgradeInitializer），无主源码可审，与批前登记一致。
- **复核状态**: 已确认（R2 复核员独立 find 核实：无 `*/main/*` Java 文件，4 个测试文件逐一吻合）

## 维度复核结论（WI 4.5 R2 独立复核员）

- **复核范围与方法**: 本文件 7 条初审发现 + 3 条 CL 全部逐行回码验证（LuceneSearchEngine :825-858/:1092-1115/:1117-1173、CliWatchCommand :107-151、CliWatchZipCommand :99-137、MockScheduledExecutor/MockTask 全文、AutoTestVars :34-36 及 VarsMap 全方法、EntityRow 全文、NopJunitExtension/AutoTestCase 全文、SourceCounter 全文、TestClock 全文含契约注记）；越界声明核对（SyncThreadPoolExecutor 本体未判定、平台设施本体未重审）；跨单元对照 cra-index 台账（WI 1.1–4.4 对本三模块零条目，无同案命中）。
- **三态判定统计**: 保留 7 / 降级 0 / 驳回 0。
- **降档口径核对**: C1-005/C1-006/C1-007 三条"测试期条件化降档"均成立——降档前提（JUnit 并行默认关闭、autotest 录制为测试线程顺序执行、saveOutput 在 complete() 同线程）经回码逐条证实，无误用降档。
- **发现级证据复核**: C1-001 的 T1/T2 交错全部可由代码直读复现（writer 引用逃逸 :830、removeTopic 关闭 :1130-1147、rollback 二次释放 :1159-1172）；C1-002 的 Lucene buffered-delete 代际语义维持"很可能"信心（审计员自标注未实验证实，P2 判级不受影响——无论终态为 v1/v2/双存，非原子性均成立）；C1-003 四重契约破坏（死旗标 :17 全类无读取、DelayQueue 恒不过期 getDelay :27-29、TimeUnit 丢弃 :27、周期不复排 MockTask.call :40-50）全部直读证实，"零调用方"经 R2 独立 grep 复核（全仓仅自身模块 1 文件命中）。
- **R2 深挖补充（编号见 C1-round2.md）**: C1-008（buildDocument 回写调用方 SearchableDoc）、C1-009（IContext ThreadLocal 方法级残留，C1-005 同族补漏）；同族面补充：NopTestConfigProcessor :31-33 TestClock 全局翻动点并入 C1-007 清单。
- **最终保留项**:
  - **P2×4**: C1-001（removeTopic/rollback 与在途读写竞态）、C1-002（addDocs 复合操作非原子）、C1-003（MockScheduledExecutor 替身契约三重破坏）、C1-004（debounce 重叠执行 + zip 并发写）。
  - **P3×3**: C1-005（AutoTestVars 三件套）、C1-006（EntityRow 读侧裸读 + 集合逃逸）、C1-007（autotest 全局静态集群无并行守卫）。
  - CL: CL-001 确认移交平台（FileWatcher 丢失唤醒）、CL-002 确认（BuildIndexTool 全量读入，C5 overlay）、CL-003 确认（ORM listener 契约，C2 overlay）、CL-004 确认（dbtool 零主源码，登记类）。
