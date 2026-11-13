# C6 资源生命周期与泄漏 — nop-search-runner-autotest（WI 4.5）

- **审计员**: 独立盲审初审员（合并批 C1+C4+C6）
- **日期**: 2026-10-21
- **盲审纪律**: 未阅读 `ai-dev/audits/**`（本文件除外）、`ai-dev/plans/**`、`ai-dev/bugs/**`、`ai-dev/lessons/**`、`ai-dev/backlog/**`；未执行 git log。契约以代码与 javadoc 为准。

## 审计范围与口径

- **合并批三维度覆盖声明**: 本文件承担 C6（资源生命周期与泄漏）；C1 见 `C1-shared-mutable-state.md`，C4 见 `C4-thread-pools.md`。C6-003 与 C1-001 同域不同面（本条管"销毁后重建逃逸释放"的泄漏面，C1-001 管"在途引用失效"的竞态面），C6-004 与 C1-003/C4-003 同根不同面（队列残留 vs 替身契约），交叉引用不重复计数，合并裁定留给复核阶段。
- **审计范围**: 8 源码根、112 文件 / 11,116 行，逐文件审读（模块划分同 C1 文件口径节）。资源类型盘点：Lucene IndexWriter/SearcherManager/FSDirectory、NioFileWatchService（线程 + WatchService 句柄）、JDBC（SimpleDataSource）、OfficePackage（zip 部件流）、文件流（Reader/Writer）、autotest ORM listener 注册、MockScheduledExecutor 任务队列、CLI 进程资源。
- **并发面极稀疏预校准声明**: 基线 C6a/C6b 命中 2 文件（LuceneSearchEngine、SourceRefactor pkg.close）+ CliReverseDbCommand（SimpleDataSource，基线 C6a:460-467）+ FixtureBundleExporter.sha256（try-with-resources，干净）。本审计不凑数扩面：其余文件的流资源全部经由平台抽象（CsvHelper/JsonTool/FileHelper.writeText/IoHelper.safeCloseObject）管理，逐文件核对无裸资源获取点。
- **dbtool 零主源码声明**: `nop-autotest/nop-autotest-dbtool` 无 src/main/java（已核实，仅 4 测试文件）；"autotest 数据库连接/测试数据清理"面在本批实际由 `AutoTestCaseDataBaseInitializer`（经 IJdbcTemplate/ITransactionTemplate 平台抽象，连接生命周期归平台）承载，autotest 自身无裸 JDBC 获取点（已逐文件核对 AutoTestCaseDataBaseInitializer/AutoTestCaseResultChecker/AutoTestCaseDataSaver）。
- **owner doc 零覆盖声明**: 未读 owner 文档；资源语义以 javadoc 为准——含 `LuceneSearchEngine.rollback`（P1-4.4-S-04 注记：rollback 即"关闭且不提交"、必须先移出缓存 map）、`CliWatchCommand.WatcherHandle`（注入实例容器所有、本地实例调用方所有）、`FixtureBundleRecordingSession`（tryLock 互斥 + try/finally 注销）、`TestClock` 契约注记。
- **降档口径**: **泄漏与残留形态一律不适用启动期单线程降档**；测试期替身（MockScheduledExecutor）的残留按"测试期条件化 + 残留不豁免"双口径登记。CLI 一次性进程的进程级回收（OS 兜底）作为严重度下修因子明示，不作为豁免。

## 资源获取点配对表

| 资源 | 获取点 | 释放点 | 成功/异常/取消/超时路径 | 判定 |
|---|---|---|---|---|
| FSDirectory | getDirectory:264（computeIfAbsent） | removeTopic:1140-1147、destroy:206-215 | computeIfAbsent 抛 NopException 时不入 map（无泄漏）；**destroy 后重建无释放** | **C6-003**；只增不逐出 **C6-002** |
| IndexWriter | getIndexWriter:289（computeIfAbsent） | removeTopic:1130-1137、destroy:194-203、rollback:1167-1172 | 失败路径先移除再 rollback（:1153-1157 注记正确）；同上两缺陷 | **C6-002/C6-003** |
| SearcherManager | getSearcherManager:277（computeIfAbsent，`new SearcherManager(writer,...)` 不拥有 writer） | removeTopic:1120-1127、destroy:182-191、rollback:1159-1166 | close 顺序 manager→writer→dir 正确；获取/释放（acquire/release）全路径 finally 配对（:332-334,:473-475,:533-535,:612-614,:747-749） | 获取/释放干净；条目面同 **C6-002/C6-003** |
| Analyzer | init:173 | destroy:218-221 | destroy 置 null 与在途读者竞态（并入 C6-003 面） | 并入 **C6-003** |
| NioFileWatchService | FileWatcherFactory:20-24（@PostConstruct start）；CliWatchCommand:88-89（本地兜底 start）；CliWatchZipCommand:50-53（init） | factory @PreDestroy:28-30；CliWatchCommand finally:100-103；CliWatchZip finally destroy:89-91 | 前两者配对正确；**CliWatchZipCommand CDI 路径 init 双触发覆盖已启动实例** | **C6-001** |
| SimpleDataSource | CliReverseDbCommand:60-67 | 无 | 无任何 close | **C6-006** |
| OfficePackage | SourceRefactor:70-71（loadFromResource 在 try 外） | finally:85 pkg.close() | **获取后、try 前抛错 → 不关闭** | **C6-005** |
| Reader/Writer（LogProcessor） | LogProcessor:22,:32 | finally safeCloseObject :29,:43 | 全路径配对 | 干净 |
| CsvRecordInput（TransformTableMigration） | TransformTableMigration:93-96 | finally:109-111 | 配对 | 干净 |
| FileInputStream（FixtureBundleExporter.sha256） | :214 try-with-resources | 同 | 配对 | 干净 |
| ORM listener/interceptor 注册 | AutoTestCase:180-181；FixtureBundleRecordingSession:44-45 | AutoTestCase.complete:234-237（@AfterEach 保证到达）；RecordingSession finally:52-53 | 配对；initDao 中段抛错由 AfterEach complete 兜底移除 | 干净 |
| VarCollector 静态注册 | AutoTestCase:167 | complete:232（重置 no-op，注释说明防 NPE） | complete 在 @AfterEach 保证执行 | 干净 |
| MockScheduledExecutor 任务队列 + promise | schedule×3/submit×2:25-57,87-94 | 无排空点；destroy 仅置旗标 | **destroy 后残留任务 + 永不完成的 promise** | **C6-004** |
| 临时文件/zip 产物 | CliWatchZipCommand zipDir 覆盖写 | 覆盖写语义（无临时文件残留） | 并发写损坏面归 C1-004 | 干净（本维度） |
| CoreInitialization（全局容器） | NopCliApplication:44-46,49-54、NopJunitExtension:46,76 | destroy 双调用点（run 主路径 + ShutdownEvent observer） | 幂等性归平台契约，见 CL-009 | CL 登记 |

## 发现

### [C6-001] CliWatchZipCommand init() 非幂等：CDI 路径 @PostConstruct 与 call() 双重启动，第一个 NioFileWatchService 被覆盖泄漏

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliWatchZipCommand.java:47-60,78,89-91`
- **证据片段**:
  ```java
  private NioFileWatchService watchService;

  @PostConstruct
  public void init() {
      watchService = new NioFileWatchService();
      watchService.start();
  }
  ...
  // call() 内:
      init();                      // :78 二次调用，覆盖字段
  ...
  } finally {
      destroy();                   // :90 只停最后一个
  ```
- **严重程度**: P2
- **并发场景**: 非线程竞态，为生命周期路径叠加：Quarkus/CDI 工厂路径（NopCliApplication:49-55 的 factory 注入分支）下 picocli 命令实例由容器创建，`@PostConstruct init()` 自动执行；随后 `call()` :78 手工再调 `init()`。
- **触发时序**: T1 容器创建 CliWatchZipCommand 实例 → @PostConstruct init() 创建并 start 实例 A（`NioFileWatchService` :58 持有 `ExecutorHelper.newSingleThreadExecutor` watch 线程 + JVM WatchService 句柄）→ T2 call() 执行 init() → 字段被实例 B 覆盖，A 失去唯一引用且永不 stop → A 的 watch 线程与 WatchService 句柄泄漏至进程退出；finally destroy() 仅 stop B。双容器路径的事实依据：CliWatchCommand.WatcherHandle javadoc（:67-73）明文记录 jdk11 launcher 与 factory==null 分支不做容器注入，即两路径并存。
- **现状**: init() 无幂等守卫（无 `if (watchService != null) return`）；`destroy()` 的 null 守卫只覆盖"从未 init"，不覆盖"init 两次"。
- **风险**: CDI 路径每次 watch-zip 调用泄漏一个单线程执行器 + WatchService 句柄；同一 JVM 内多次执行（测试/嵌入场景）线性累积。泄漏与残留形态不适用启动期降档；CLI 单次进程的 OS 兜底作为下修因子，判 P2。
- **建议**: init() 加幂等守卫；或移除 call() 内手工 init、改为惰性初始化（首次使用时创建）；@PostConstruct 与手工调用二选一。
- **失效面枚举（F3）**: NioFileWatchService 生命周期点全查——`FileWatcherFactory`(:20-30, PostConstruct/PreDestroy 配对，干净)；`CliWatchCommand.newFileWatcher` 本地兜底路径(:88-90, finally stop 配对，干净)；`CliWatchCommand` 注入路径（容器所有，不 stop——WatcherHandle javadoc 明文声明所有权，干净）；`CliWatchZipCommand`（本缺陷）。同族 init 双调用形态：`CliWatchZipCommand.call` 为 8 根内唯一 init-in-call 点（逐一核对其余 28 个 cli-core 命令无 @PostConstruct+call 叠加）。
- **信心水平**: 确定（双路径存在有代码内注记实证；@PostConstruct 是否触发取决于 CDI 工厂路径，触发即泄漏）
- **误报排除**: 非"finally 有 destroy 即安全"的表观判定——泄漏机制是字段覆盖而非未调用 destroy；非平台设施本体（NioFileWatchService 归 nop-core，此处只审使用编排）。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C6-002] LuceneSearchEngine topic 级资源（Directory/Writer/SearcherManager）只增不逐出：无上限、无 TTL、removeTopic 为唯一显式回收点

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:134-136,253-301`
- **证据片段**:
  ```java
  private final Map<String, Directory> indexDirs = new ConcurrentHashMap<>();
  private final Map<String, IndexWriter> indexWriters = new ConcurrentHashMap<>();
  private final Map<String, SearcherManager> searcherManagers = new ConcurrentHashMap<>();
  ...
  return indexDirs.computeIfAbsent(topic, key -> {
      rootPath.mkdirs();
      Path topicPath = rootPath.toPath().resolve(key);
      return FSDirectory.open(topicPath);       // 每 topic 常驻一个
  ```
- **严重程度**: P2
- **并发场景**: 长驻搜索引擎 bean，任一请求线程经 `getSearcherManager/getIndexWriter/getDirectory`（topic 来自请求参数，经 `isValidTopicName` 校验后仅约束字符集，不约束基数）触发按需创建。
- **触发时序**: 非交叠竞态，为无界账本形态（F4 邻族）：每新增一个 topic → 1 个 FSDirectory（文件句柄组）+ 1 个 IndexWriter（write.lock + ramBufferSizeMB=10MB 写缓冲上限/个）+ 1 个 SearcherManager 常驻三 CHM，仅 `removeTopic`(:1117) 显式回收或进程级 `destroy`(:179) 兜底；无容量上限、无 TTL/LRU、无空闲逐出。topic 基数由调用方决定（biz 层 addDoc/addDocs 的 topic 参数 admin 可控；平台内已知消费形态 "nop-code-<indexId>" 随仓库/索引基数增长）。
- **现状**: 注册无对称的自动回收路径；条目键空间不可枚举上限（用户可控键，C5 校准第 4 条形态）。
- **风险**: 多租户/多索引长驻部署下 fd 与堆占用随历史 topic 单调增长 → fd 耗尽或 OOM；单 writer 10MB 缓冲上限使放大倍数可估（N×~10MB + fd），数万 topic 量级即危险。泄漏与残留形态不降档。
- **建议**: 引入 topic 条目上限 + LRU/TTL 逐出（复用平台 `GlobalCacheRegistry`/`ICache`+`CacheConfig` 形态），逐出路径复用 removeTopic 的三段关闭序列；或在 owner 契约中声明 topic 基数包络并加 fail-fast。
- **失效面枚举（F3）**: 三 CHM 同族获取点全查——getDirectory(:264)/getIndexWriter(:289)/getSearcherManager(:277) 全部无逐出；回收点全查——removeTopic(:1117-1150，显式)、rollback(:1158-1173，失败路径部分回收——dir 有意保留供重建，与注记一致，不算缺陷)、destroy(:179-222，进程级)。biz 层未提供 removeTopic 的 biz action（SearchCoreConstants 定义了 ACTION_REMOVE_TOPIC 但 SearchEngineBizModel 无对应方法——管理面缺口，随本条登记）。
- **信心水平**: 确定（代码直读）；触达规模依赖部署形态
- **误报排除**: 非"缓存无淘汰"的泛化 OOM 报告——三类资源均为重资源（fd+锁+写缓冲）且键空间用户可控，符合 F4 无界账本画像；未与声明包络混排（owner doc 零覆盖，无包络声明可援引，已在风险节注明）。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C6-003] destroy() 与在途 computeIfAbsent 交错：销毁后新建的 Directory/Writer/Manager 逃逸释放，analyzer 置 null 窗口 NPE

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:165-222,253-301`
- **证据片段**:
  ```java
  @PreDestroy
  public void destroy() {
      searcherManagers.values().forEach(manager -> { ... manager.close(); ... });
      searcherManagers.clear();          // 清空后仍可被并发 computeIfAbsent 重新填入
      ...
      this.analyzer.close();
      this.analyzer = null;              // :220 与在途 analyzeQuery/buildQuery 读者竞态
  ```
- **严重程度**: P2
- **并发场景**: 容器停机线程执行 @PreDestroy destroy；业务 worker 线程仍在处理 search/addDocs/getDoc 请求（停机窗口在途流量）。
- **触发时序**: T1 worker 进入 `getDirectory` 的 `indexDirs.computeIfAbsent` mapping（:264-273，含磁盘 mkdirs/FSDirectory.open，耗时窗口）→ T2 destroy 执行 :206-215 关闭既有 dirs 并 `indexDirs.clear()` → T1 mapping 完成将新 FSDirectory `put` 入 map → destroy 返回、IoC 容器销毁完毕，而新 dir（及后续经 :277/:289 链重建的 writer/manager）已注册进三个"无人再持有"的 map → **永不关闭**（fd + write.lock 残留至进程退出）。同窗口 T1 调用 analyzeQuery(:365-367) 读 `analyzer`（:220 已置 null）→ NPE。
- **现状**: destroy 无"拒绝新访问"守卫（无 started/closed 旗标，F10 家族）；computeIfAbsent 获取路径在 destroy 后依旧畅通；三个 map 的 clear 与重建之间无同步语义。
- **风险**: 每次带在途流量的停机都可能重建逃逸资源：fd/write.lock 泄漏使同 rootPath 的后续启动可能撞 Lucene 锁残留；NPE 打给在途请求为次生噪音。泄漏与残留形态不降档；触发依赖停机窗口在途流量（服务化部署常态），P2。
- **建议**: 增加 `volatile boolean running` 生命周期旗标：destroy 先置 false 再关闭，获取路径 fail-fast（对照 C4-003 建议形态）；computeIfAbsent 后二次校验 running，若已关闭则立即关闭刚创建的资源并不入 map（或改用全局读写锁协调 destroy 与获取路径——与 C1-001 建议设施合并）。
- **失效面枚举（F3）**: destroy 关闭序列全查——managers(:182-191)/writers(:194-203)/dirs(:206-215)/analyzer(:218-221) 四段全部暴露于同一窗口；重建入口 getDirectory(:264)/getIndexWriter(:289)/getSearcherManager(:277) 三 computeIfAbsent 同族；读 analyzer 的 analyzeDoc(:357)/analyzeQuery(:366)/analyzeTerm(:486)/buildQuery(:863,:970)/getIndexWriter(:292) 为 NPE 受害面。`removeTopic` 无此窗口（条目 remove 语义），已区分。
- **信心水平**: 确定（交错可由代码直读；write.lock 残留后果基于 Lucene FSDirectory 锁文件语义）
- **误报排除**: 非平台 IoC 生命周期审计（误报校准第 9 条）——缺陷在引擎自身 destroy 与获取路径之间缺协调；亦非低频路径粗同步（停机在途流量是服务化常态路径）。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C6-004] MockScheduledExecutor destroy 不排空任务队列、不完成挂起 promise——残留任务与永久挂起的等待方

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/mock/MockScheduledExecutor.java:18,25-57,87-94,102-104`
- **证据片段**:
  ```java
  private final DelayQueue<MockTask> tasks = new DelayQueue<>();
  ...
  @Override
  public void destroy() {
      started = false;      // 不排空 tasks、不 complete 挂起的 promise
  }
  ```
- **严重程度**: P2
- **并发场景**: 测试期——业务代码经 `submit`/`execute` 提交任务并持有返回的 CompletableFuture 等待；替身 destroy 时队列内未触发任务与未完成 promise 的残留。
- **触发时序**: T1 被测代码 `executor.submit(task)` 持有 promise → 测试收尾 destroy()（tasks 未空、promise 未完成）→ 残留任务滞留 DelayQueue（对象残留，随替身 GC）；若等待方经 FutureHelper.get 等待该 promise → 永久挂起（destroy 不 exceptionally complete）→ 测试挂死而非快速失败。
- **现状**: destroy 仅写死旗标（读取点为零，见 C4-003）；`schedule` 的 `promise.exceptionally(err -> { tasks.remove(task); ... })`(:29-32) 只在任务执行失败后清理（此时已被 poll 移除，实为 no-op），不存在"destroy 清理"路径。
- **风险**: 测试期替身掩盖真执行器的 shutdown 语义（真执行器 shutdown 后等待方至少收到 RejectedExecution/超时），产生假保护与测试挂死。残留形态不降档；替身当前零调用方为下修因子，P2。
- **建议**: destroy() 中 `tasks.clear()` 并对每个任务 `promise.completeExceptionally(new IllegalStateException("executor destroyed"))`（对齐 F8 清理对称）；配合 C4-003 的 started 守卫。
- **失效面枚举（F3）**: 队列写点全查——schedule(:28)/scheduleAtFixedRate(:40)/scheduleWithFixedDelay(:52) 三处 add 全部无 destroy 协调；promise 完成点全查——MockTask.call(:44,:47)（仅手动触发路径）；清理点全查——exceptionally 回调(:29-32,:41-44,:53-56) 三处均为失效清理。`triggerNext`(:59-64) 不受影响面。
- **信心水平**: 确定
- **误报排除**: 非替身简化并发模型豁免——豁免不覆盖"残留资源 + 等待方永久挂起"的语义缺陷（本批口径：泄漏与残留形态不降档）；当前零调用方作为严重度下修因子已明示。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C6-005] SourceRefactor.refactorOfficeFile：loadFromResource 位于 try 块之外，失败路径 OfficePackage 不关闭

- **文件**: `nop-runner/nop-tool/src/main/java/io/nop/tool/refactor/SourceRefactor.java:70-87`
- **证据片段**:
  ```java
  private void refactorOfficeFile(IResource sourceFile, IResource targetFile) {
      OfficePackage pkg = new OfficePackage();
      pkg.loadFromResource(sourceFile);      // try 之外：抛错即泄漏
      try {
          ...
          pkg.saveToResource(targetFile, DisabledEvalScope.INSTANCE);
      } finally {
          pkg.close();
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: 无并发面；单线程 CLI/库调用，资源生命周期缺陷（F8 清理不对称：获取点与守卫范围错位）。
- **触发时序**: 线性可达——`loadFromResource`（解析 xlsx/docx zip 容器，持有部件流/文件句柄）对损坏或非 Office 格式文件抛异常 → `try` 未进入 → `pkg.close()` 不执行 → 包内已打开的资源依赖 GC/进程退出回收。
- **现状**: close 已在 finally（成功与处理期异常路径配对），唯独获取完成前的异常窗口裸露。
- **风险**: 库形态复用（批量 refactorDir 循环内遇坏文件继续跑）时句柄短暂累积；CLI 一次性进程由 OS 兜底。P3。
- **建议**: `OfficePackage pkg = new OfficePackage(); try { pkg.loadFromResource(...); ... } finally { pkg.close(); }`——将获取后的首步纳入 try 范围。
- **失效面枚举（F3）**: 同文件兄弟路径全查——`refactorResource` 文本分支(:55-61) 经 ResourceHelper 读写（平台管理，干净）；`refactorOfficeFile` 为 8 根内唯一 OfficePackage 获取点。nop-tool 其余流使用点（LogProcessor:22/:32、TransformTableMigration:93-111、FixtureBundleExporter:214）逐一核对均 try/finally 或 try-with-resources 配对（见配对表）。
- **信心水平**: 确定
- **误报排除**: 非风格挑剔——获取与守卫错位是 F8 校准的定向形态；进程级兜底已作为严重度下修因子而非豁免。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C6-006] CliReverseDbCommand SimpleDataSource 配置后未关闭

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliReverseDbCommand.java:60-67`
- **证据片段**:
  ```java
  SimpleDataSource dataSource = new SimpleDataSource();
  dataSource.setPassword(password);
  dataSource.setUsername(username);
  dataSource.setUrl(jdbcUrl);
  ...
  JdbcMetaDiscovery discovery = JdbcMetaDiscovery.forDataSource(dataSource);
  ...
  DataBaseMeta meta = discovery.discover(catalog, schemaPattern, table == null ? "%" : table);
  ```
- **严重程度**: P3
- **并发场景**: 无并发面；CLI 单次执行，JDBC 数据源生命周期缺陷。
- **触发时序**: 线性可达——dataSource 配置后传入 discovery，`discover` 过程打开的 Connection 归属 nop-dao/dbtool 侧契约（CL-008）；dataSource 本身在整个 call() 生命周期无 close 调用，若 SimpleDataSource 持有池化连接则驻留至进程退出。
- **现状**: 获取-释放不对配（零释放点）；使用面缺陷，SimpleDataSource 的 close 契约本体归 nop-dao 审计单元。
- **风险**: CLI 进程内无实害（进程退出 OS 兜底）；嵌入复用时连接泄漏。P3。
- **建议**: call() 内 try-with-resources（若 SimpleDataSource 实现 AutoCloseable）或 finally 显式 close；close 契约以 nop-dao 侧核对为准（CL-008）。
- **失效面枚举（F3）**: JDBC 获取点全查（8 根内）——`CliReverseDbCommand:60` 为唯一裸数据源获取点；autotest 侧 JDBC 全部经 IJdbcTemplate/IOrmTemplate 抽象（AutoTestCaseDataBaseInitializer:53,134,227 等），无裸 Connection/DataSource 获取（dbtool 零主源码，另登记）。GenOrmHelper(:60-68) ReportEngine/ExcelWorkbook 为内存对象，无句柄面。
- **信心水平**: 很可能（获取-释放不对称确定；泄漏后果取决于 SimpleDataSource 是否池化，本体契约未审）
- **误报排除**: 非平台设施本体重审——本条限于 CLI 使用方零释放点的事实；进程级兜底已作为下修因子。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

## 维度集外线索（overlay C2/C3/C5/C7/C8/C9）

### [nop-search-runner-autotest-CL-008] SimpleDataSource / JdbcMetaDiscovery 连接生命周期契约（C6 overlay，本体归 nop-dao/dbtool）
- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliReverseDbCommand.java:62-67`（使用点）
- **线索**: `discover` 内部 Connection 的打开/关闭语义、discover 异常路径连接回收，由 nop-dao 侧实现决定，本批未审本体。与 C6-006 联动。

### [nop-search-runner-autotest-CL-009] CoreInitialization.destroy 幂等契约与 NopCliApplication 双调用点（登记类，C6 overlay）
- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/NopCliApplication.java:44-54,58-60`
- **线索**: `run()` 主路径 :45/:54 destroy + `stop(@Observes ShutdownEvent)` :59 destroy——正常 Quarkus 路径下 destroy 至少两次到达；幂等性为平台契约（本批不审本体）。若非幂等则 CLI 停机二次异常，移交平台生命周期审计单元核实。

### [nop-search-runner-autotest-CL-010] Lucene ConcurrentMergeScheduler 每 topic 线程与句柄（第三方库线程模型，C4/C6 交界）
- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:288-301`
- **线索**: 未显式配置 MergeScheduler → Lucene 默认 ConcurrentMergeScheduler 按 writer 惰性建合并线程；与 C6-002 的 topic 基数无界叠加放大线程/fd 占用。第三方线程模型线索，随 C6-002 治理（topic 逐出）一并收敛。
- **复核状态**: 已确认（R2 复核员回码 getIndexWriter :288-301 无 MergeScheduler 配置）

## 维度复核结论（WI 4.5 R2 独立复核员）

- **复核范围与方法**: 本文件 6 条初审发现 + 3 条 CL 全部逐行回码验证（CliWatchZipCommand 全文含 init/destroy/call 三点、LuceneSearchEngine :134-136/:165-222/:253-301/:1117-1173、MockScheduledExecutor/MockTask 全文、SourceRefactor :69-87、CliReverseDbCommand :59-87、LogProcessor/TransformTableMigration/FixtureBundleExporter/FixtureBundleRecordingSession 资源配对逐点核对）；C6-002 的 ACTION_REMOVE_TOPIC 管理面缺口经 SearchCoreConstants(:18) 与 SearchEngineBizModel 全文（无 removeTopic 方法）双向核实；跨单元对照 cra-index 台账零同站点命中。
- **三态判定统计**: 保留 6 / 降级 0 / 驳回 0。
- **C6-001 子项复核说明**: (a) 双重 init 机制证实——@PostConstruct init(:49-53) 与 call() :78 手工再调，字段覆盖后实例 A 失去唯一引用；(b) finally destroy(:89-91) 只 stop 字段当前指向的实例 B 证实；(c) CDI/jdk11 双路径前提经 CliWatchCommand.WatcherHandle javadoc(:67-73) 与 NopCliApplication.factory 分支证实；**R2 补充后果放大因子：NioFileWatchService 的 watch 线程为非守护线程（NioFileWatchService.java:58 → ExecutorHelper.newSingleThreadExecutor 第三参 daemon=false 回码核实）——泄漏实例 A 阻止 JVM 自然退出，后果从"句柄泄漏"升级为"进程挂住"，并入本条风险节**。
- **C6-002/C6-003**: 三 CHM 无逐出、destroy 与 computeIfAbsent 交错窗口（mapping 耗时含磁盘 mkdirs/FSDirectory.open）、analyzer 置 null(:220) NPE 窗口全部回码证实；rollback 注记（P1-4.4-S-04，:1153-1157）确认 dir 有意保留非缺陷，与条目内豁免一致。
- **C6-004 子项复核补充**: 周期类任务（SCHEDULED_AT_FIXED_RATE/WITH_FIXED_DELAY）的 promise **经 triggerNext 手动触发后也不完成**（MockTask.call :43-44 仅 ONCE 分支 complete）——"永不完成的 promise"在周期任务上无条件成立，不依赖 destroy 时机，并入本条证据。
- **C6-005/C6-006**: 获取-守卫错位窗口（loadFromResource 在 try 外 :70-71）与 SimpleDataSource 零释放点（:59-87 全文无 close）证实；P3 判级与进程级兜底下修因子口径正确。
- **R2 深挖补充（编号见 C6-round2.md）**: C6-007（CliWatchCommand ICancellable 丢弃——注入路径注册不注销）、C6-008（CliWatchZipCommand 产物就地覆盖写非原子，F6 邻形）。
- **最终保留项**:
  - **P2×6**: C6-001（watch-zip 双重 init 泄漏，含非守护线程放大因子）、C6-002（topic 资源只增不逐出）、C6-003（destroy 与在途重建交错逃逸）、C6-004（Mock destroy 不排空）、C6-007（R2：watch 注册不注销）、C6-008（R2：zip 产物非原子覆盖写）。
  - **P3×2**: C6-005（SourceRefactor 获取-守卫错位）、C6-006（SimpleDataSource 未关闭）。
  - CL: CL-008 确认移交 nop-dao（SimpleDataSource/JdbcMetaDiscovery 本体）、CL-009 确认登记（CoreInitialization.destroy 三调用点幂等性归平台生命周期单元）、CL-010 确认（随 C6-002 治理收敛）。
