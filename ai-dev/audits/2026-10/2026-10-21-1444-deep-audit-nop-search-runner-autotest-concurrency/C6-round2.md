# C6 资源生命周期与泄漏 — 第 2 轮深挖（WI 4.5 R2）

- **审计员**: 独立深挖审计员（第 2 轮，C1/C4/C6 合并批）
- **日期**: 2026-10-21
- **盲审纪律**: 未阅读 `ai-dev/audits/**` 中除第 1 轮三文件与本文件外的一切内容；未阅读 plans/bugs/lessons/backlog；未执行 git log。契约以代码与 javadoc 为准。
- **深挖任务边界**（承接第 1 轮 C6-001~C6-006，不重复登记）：(a) autotest core 47 文件的资源面——AutoTestCase 初始化/清理、junit 集成资源；(b) nop-runner nop-tool 11 文件的资源面。

## 深挖覆盖声明

- **autotest core 47 文件资源面逐一审读结论**：
  - **AutoTestCase 初始化/清理失败窗口逐一分析**（initDao :159-188 与 complete :227-254 的配对）：(1) `AutoTestCaseDataBaseInitializer.initialize()`(:173) 抛错窗口——ormHook 尚未注册（null），AfterEach → complete :234 null 守卫跳过 remove、:251 finally `AutoTestVars.clear()` 兜底——**配对成立**；(2) `addDaoListener`(:180) 成功 / `addInterceptor`(:181) 抛错窗口——complete :234-237 双 remove，remove 未注册 interceptor 依赖 nop-orm 列表 remove 幂等（平台契约，CL 交叉）——**基本成立**；(3) `caseData` 为 null（NOT_USE 模式 initCaseDataDir :134-135 提前返回）时 initDao :164 `caseData.getInitVars()` NPE——错误处理域（非资源泄漏），不立条。
  - **junit 集成资源**：`NopJunitExtension.beforeAll`(:34) / `afterAll`(:78) 经 `BaseTestCase.beginTest/endTest` 调用 `BaseContextProvider.clear()`（BaseContextProvider.java:76/:81，已回码核实）——IContext ThreadLocal 类边界兜底存在，方法级残留面归 C1-009（C1 文件）；TestClock `registerClock(new TestClock())`(:32) / `afterAll`(:77) 恢复 defaultClock 配对干净；`VarCollector.registerInstance`(:167) / complete :232 重置 no-op 配对干净（第 1 轮结论维持）。
  - **流/连接资源**：全模块 grep 核实——裸流获取点仅 `FixtureBundleExporter.sha256`(:214, try-with-resources)；CSV/JSON/XML/Excel 全部经 `CsvHelper`/`JsonTool`/`FileHelper`/`ResourceHelper` 平台抽象（TestCaseJsonDataSplitter:132,:148,:199,:250、AutoTestCaseDataSaver:88,:119、AutoTestCaseResultChecker:98、AutoTestCaseData:326、FixtureBundleExporter:200、FixtureBundleValidator:97、FixtureBundleImporter:364、TransformTableMigration:113 逐一核对）；JDBC 零裸获取（AutoTestCaseDataBaseInitializer/AutoTestCaseResultChecker 经 IJdbcTemplate/IOrmTemplate）——第 1 轮结论维持。
  - **FixtureBundle 族**：RecordingSession tryLock + try/finally 注销（第 1 轮已登记干净）；Exporter 的 manifest merge/write(:127-135) 为纯文件写（平台抽象）；Importer/Validator/Manifest/SensitiveRules 无资源获取点。
- **nop-runner nop-tool 11 文件资源面逐一审读结论**：`LogProcessor`(:26 Reader / :40 Writer) 两处获取点**均无"获取后、try 前可抛语句"窗口**（:40 getWriter 为 try 前最后一条语句，获取本身成功即入 try）——与 C6-005 的 SourceRefactor 形态区分，**干净**；`LineIterator`/`ChunkIterator` 不拥有资源（持有外部传入 Reader/Iterator 引用）；`AbstractMigrateTask`（readXml/readJson 平台抽象）、`IdentifierTransformer`/`TokenPattern`/`TokenPatternNormalizer`/`FileExtFilter`（纯计算）、`SourceCounter`（FileHelper.readText 无句柄面）、`SourceRefactor`（C6-005 为其唯一资源面）——除 C6-005 外无新发现。
- **zip 产物写入语义核实**（C6-008 证据链）：`ResourceHelper.zipDir`(:579) → `IZipTool.zipDirTo`(:75-85) → `getZipOutput(zipFile)` 直接打开目标资源输出流**就地写入**，无临时文件 + rename 阶段（IZipTool.java:75-85、JdkZipTool.java:22-33、ResourceHelper.java:579-594 逐层回码核实）。

## 发现

### [C6-007] CliWatchCommand 丢弃 watcher.watch() 返回的 ICancellable——注入路径 WatchKey/handler 注册与注销不对称，共享单例 watchKeyMap 单调累积

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliWatchCommand.java:124-126,93-105`；生产方 `nop-kernel/nop-core/src/main/java/io/nop/core/resource/watch/FileWatcher.java:57-87`；注销机制 `nop-kernel/nop-core/src/main/java/io/nop/core/resource/watch/NioFileWatchService.java:32,139-150,213-221`
- **证据片段**:
  ```java
  // CliWatchCommand:124-126 —— 返回值（cleanup 句柄）被丢弃
  watcher.watch(Arrays.asList(watchDirs).stream()...collect(Collectors.toList()),
          fileNamePatterns == null ? null : Arrays.asList(fileNamePatterns),
          debounceWait, events -> processEvents(xpl, events, state));
  // FileWatcher:57-86 —— cleanup 仅经 cancellable 送达
  Cancellable cancellable = new Cancellable();
  Runnable cleanup = watchService.watch(watchPath, ..., listener);
  cancellable.appendOnCancelTask(cleanup);       // :85 唯一送达通道
  ```
- **严重程度**: P2
- **并发场景**: 非线程竞态，为注册表对称性缺陷（C6 执行步骤 5）：`register`（NioFileWatchService.register :147 `watchKeyMap.put(key, entry)`）与 `unwatch`(:213-221 `watchKeyMap.remove` + `key.cancel()`) 的配对通道是 watch() 返回的 cleanup Runnable，经 FileWatcher 包装为 ICancellable 后被使用方丢弃。
- **触发时序**: T1 CDI 路径 CliWatchCommand 执行 → 每个 watchDir 递归注册 1..N 个 WatchKey 进共享单例（FileWatcherFactory @Singleton）的 watchKeyMap，cleanup 通道随 ICancellable 被垃圾回收丢弃 → T2 命令返回（EOF），finally 因 `locallyOwned=false` 跳过清理 → WatchKey 与 listener 永久滞留共享单例（直至容器停机）。同 JVM 反复调用：每次调用净增一组 key，全部指向各自 FileWatcher 的 queue/debounce 链——旧调用的脚本在"返回后"仍被执行（执行面归 C4-007）。
- **现状**: 注销代码路径存在且完整（unwatch 递归摘除 subEntry），缺陷纯在使用方未保存/调用注销句柄；本地路径（factory==null）经 watcher.stop() → doStop 全量 cancel 兜底，掩盖了句柄缺失。
- **风险**: 泄漏与残留形态不降档（本批口径）：共享单例注册表随调用次数单调增长（WatchKey + FileWatchEntry + listener + FileWatcher 实例）；单进程 CLI 由容器停机兜底（下修因子），嵌入/测试场景反复调用线性累积且旧 watch 持续消费事件。
- **建议**: `doWatch` 接住 ICancellable 并在 finally 无条件 `cancel()`（unwatch 只摘本调用注册的 key，对注入/本地两路径均安全且幂等）；本地路径的 watcher.stop() 保留为服务级兜底。
- **失效面枚举（F3）**: 注册/注销点全查（8 根内）——`CliWatchCommand:124`（丢弃，本缺陷）；`CliWatchZipCommand:103`（同样丢弃返回 cleanup，但 finally destroy :89-91 停**自有**服务 → doStop :200-203 全量 cancel 兜底——豁免，已区分）；`FileWatcherFactory.newFileWatcher`(:32-34)（仅生产 FileWatcher，注册发生在 watch() 调用，无独立面）；nop-core `FileWatcher.watch:85`/`NioFileWatchService.watch:179`（cleanup 生产方，契约完整，平台侧随 CL-011 移交）。add/removeListener 族（ORM listener）第 1 轮已核对配对，不重复。
- **信心水平**: 确定（丢弃点、兜底差异、注册表累积机制均可代码直读）
- **误报排除**: 非平台设施本体重审（unwatch 机制本体完整，移交口径不变）——缺陷在使用方丢弃注销句柄的使用编排；非"finally 有 stop 即安全"表观判定（注入路径 finally 为空）。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C6-008] CliWatchZipCommand 产物就地覆盖写非原子：zipDir 直接打开目标 zip 输出流写入，读方可见半成品产物（F6 邻形）

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliWatchZipCommand.java:128-136`；写入机制 `nop-kernel/nop-core/src/main/java/io/nop/core/resource/zip/IZipTool.java:75-85`
- **证据片段**:
  ```java
  // CliWatchZipCommand:131（debounce 任务内）
  ResourceHelper.zipDir(new FileResource(dir), new FileResource(zipFile), null);
  // IZipTool:75-78 —— 直接对目标资源开输出流，无 temp+rename
  default void zipDirTo(IFile dir, IResource zipFile, ZipOptions options, Predicate<IFile> filter) {
      IZipOutput out = getZipOutput(zipFile, options);
      out.addDir("", dir, true, filter);
  ```
- **严重程度**: P2
- **并发场景**: debounce 任务线程（globalTimer）执行 zipDir 覆盖写 `zipFile` 期间，外部消费方（watch-zip 的功能目的即为第三方消费该产物——部署/同步工具）并发读取同一文件。与 C1-004 的"两次 zip 并发写"施害方向互补：C1-004 管写-写，本条管写-读。
- **触发时序**: T1 debounce 任务执行 zipDir → `getZipOutput` 打开 `zipFile` 输出流（文件即被截断为 0/部分长度）→ 逐 entry 写入（大目录耗时秒级）→ T2 消费方在窗口内读取 → 拿到截断/损坏的 zip（中央目录未写即非完整 zip）。单次事件触发一次覆盖，长时 watch 下每次变更重演；读方撞上写窗口即中招，无自愈标记。
- **现状**: 覆盖写语义本身第 1 轮 C6 配对表已核（"无临时文件残留"），但仅从残留角度判定干净；**原子性/读方可见性面未审**——无 write-to-temp + atomic rename 阶段（逐层回码核实），损坏窗口 = 整个 zip 写入时长。
- **风险**: watch-zip 产物的每一次更新窗口都是消费方读损坏的暴露窗口；若消费方以"文件存在即可读"轮询（该功能的典型消费形态），损坏读取概率与变更频率×写入时长成正比。功能主路径、实际触达面，不降档。
- **建议**: debounce 任务内改为 `zipFile.tmp` 写入 + `Files.move(tmp, zipFile, REPLACE_EXISTING, ATOMIC_MOVE)`（同目录 rename 原子性）；或复用平台 `LocalFileLock` 协调写读。平台 `zipDirTo` 若普遍需要该语义可增加 `zipDirToAtomic` 形态（归平台侧增强，本条立使用方择用义务）。
- **失效面枚举（F3）**: zip 产物写入点全查（8 根内）——`CliWatchZipCommand:131`（本缺陷，watch 长驻反复写）；`CliRepackageCommand.call`(:27-45, 写**新**输出 jar + 临时目录 finally deleteAll——产物为一次性新文件，无覆盖窗口，豁免)；`ResourceHelper.zipLocalDir`（本批无调用）。F6 家族对照——本批无持久化 checkpoint/账本面，zip 产物为唯一"声称可直接消费的落盘数据"。
- **信心水平**: 确定（写入路径逐层回码；损坏窗口与消费方行为依据功能定位推断）
- **误报排除**: 非"覆盖写即缺陷"的泛化报告——判定依据是 F6 定向检查项（rename 原子性缺失）+ 该产物被设计为外部消费（watch-zip 命令的功能描述）；单次 CLI 手工 zip 场景无此问题（已区分豁免 CliRepackageCommand）。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

## 补充既有发现的复核因子（不新增编号）

- **C6-001 后果放大因子**: `NioFileWatchService` 的 watch 线程为**非守护线程**（NioFileWatchService.java:58 `newSingleThreadExecutor("nio-file-watch-service", 1, false)`，ExecutorHelper.java:49 第三参 daemon=false 回码核实）→ C6-001 泄漏的实例 A 不止泄漏句柄：其非守护 watch 线程**阻止 JVM 自然退出**，嵌入/测试场景下 C6-001 的后果从"句柄泄漏"升级为"进程挂住"。建议复核阶段并入 C6-001 风险节。
- **C6-004 对照补充**: MockTask.call(:43-44) 周期类任务（SCHEDULED_AT_FIXED_RATE/WITH_FIXED_DELAY）的 promise **经 triggerNext 手动触发后也不完成**（仅 ONCE 分支 complete）——C6-004"永不完成的 promise"的残留面在周期任务上无条件成立（不依赖 destroy 时机），并入 C6-004 证据。

## 继续判定

**判定：C6 维度收敛（不再开第 3 轮）。** 理由：两个深挖方向的存量文件（autotest core 47 + nop-tool 11）经逐一审读仅产出 C6-007/C6-008 两条（均属第 1 轮已建立的缺陷根——watch 生命周期编排与 zip 写入语义——的邻接面，非全新缺陷域）；AutoTestCase 初始化/清理失败窗口、junit 集成资源配对、流/连接平台抽象覆盖经回码全部证实干净。残余未审面仅两项且均有明确归属：nop-orm 侧 `removeInterceptor` 对未注册 interceptor 的幂等性（平台契约，CL 交叉引用即可）、H2 mem 库随容器 restart 的连接池释放链（平台池语义，归 WI 2.1 域）。本单元 C6 建议收敛。

## 复核结论（任务 B 追加）

- **C6-007**: 逐行回码确认 ：124 丢弃点、Cancellable.appendOnCancelTask(FileWatcher:85) 唯一送达通道、注入/本地路径兜底差异、watchKeyMap（NioFileWatchService:32）累积机制。与 C4-007 同根不同面成立（合并裁定不重复计数）。P2 判级核对：残留不降档口径正确，单进程兜底已作下修因子明示。**已保留（P2）**。
- **C6-008**: 逐层回码确认 zipDir→zipDirTo→getZipOutput 就地写入链（ResourceHelper:579 / IZipTool:75-85 / JdkZipTool:22-33）、CliRepackageCommand 豁免依据（新文件 + finally 清理）。F6 定向检查项命中成立。P2 判级核对：功能主路径 + 写入窗口秒级 + 消费方无自愈，未夸大为 P0/P1。**已保留（P2）**。
- **同案命中查重**: C6-007/C6-008 与 cra-index 台账 WI 1.1–4.4 无同案命中（ICancellable 丢弃、zip 产物原子性均为首报）；C6-008 与第 1 轮 C1-004 为同文件互补面（写-写 vs 写-读），非重复。
- **第 1 轮六条复核结果与最终保留项见 `C6-resource-lifecycle.md` 的「维度复核结论」「最终保留项」两节。**
