# C1 共享可变状态与可见性 — 第 2 轮深挖（WI 4.5 R2）

- **审计员**: 独立深挖审计员（第 2 轮，C1/C4/C6 合并批）
- **日期**: 2026-10-21
- **盲审纪律**: 未阅读 `ai-dev/audits/**` 中除第 1 轮三文件（C1/C4/C6-round1）与本文件外的一切内容；未阅读 plans/bugs/lessons/backlog；未执行 git log。允许源码、javadoc、`_tmp/cra-baseline-nop-search-runner-autotest.txt`。契约以代码与 javadoc 为准。
- **深挖任务边界**（承接第 1 轮 C1-001~C1-007，不重复登记）：(a) search api SPI 契约可变态；(b) cli-core 其余 24 文件（第 1 轮已覆盖 5 个并发面文件之外的存量）；(c) nop-tool SourceCounter 聚合状态。

## 深挖覆盖声明

- **search api 8 文件逐一审读结论**: `ISearchEngine`/`ITextEmbedding` 为 SPI 接口，唯一可变态是异步 default 方法（第 1 轮 CL-007 已登记 caller 线程同步语义）与 `buildDocument` 对调用参数的回写（本轮 C1-008）；`SearchRequest`/`SearchResponse`/`SearchableDoc`/`SearchHit` 为平台 `@DataBean` 可变 bean（平台惯例，误报校准），在 CL-007 的 caller 线程同步语义下不发生跨线程逃逸；`SearchType` 枚举、`SearchConstants` 常量类干净。
- **cli-core 其余 24 文件逐一审读结论**: `CliConvertCommand`/`CliDiffCommand`/`CliExportDbCommand`/`CliExtractCommand`/`CliFindCommand`/`CliGenCommand`/`CliGenFileCommand`/`CliGenOrmExcelCommand`/`CliImportDbCommand`/`CliJqCommand`/`CliLintCommand`/`CliRefactorCommand`/`CliRepackageCommand`/`CliReverseDbCommand`(C6-006 已登记)/`CliRgCommand`/`CliSplitCommand`/`CliValidateCommand`/`CliFileCommand`/`GenOrmHelper`/`MainCommand`/`NopExitCodeExceptionMapper`/`CliErrors`——全部为一次性 Callable 命令（picocli 每次执行新建实例）或常量/无状态工具类，实例字段仅为命令行参数绑定（picocli 初始化先于 call()，HB 经线程启动），`GenOrmHelper.newReportEngine`/`CliGenFileCommand.newReportEngine` 每次调用新建 ReportEngine（无共享）；`CliRepackageCommand` 临时目录 try/finally deleteAll 配对干净；静态字段经基线 C1a/C1b 全量核对均为 `static final` 常量。**无新发现**。唯一单例 bean `FileWatcherFactory`（@Singleton）的实例字段 `watchService` 归 C4/C6 文件分析。
- **nop-tool SourceCounter 深挖复核结论**: 维持第 1 轮"干净"判定并加严证据——`count()`(:42-44) 的 `computeIfAbsent→addAndGet/incrementAndGet` 复合操作原子；`getResults()`(:63-69) 对 CHM 做快照式迭代（弱一致，聚合读无撕裂风险）；`fileExtensions`/`includeTests`/`logDetails` 为启动期 setter 注入后只读；全仓唯一使用点为测试类 TestSourceCounter（单线程）；`removeComments`(:154-248) 纯函数。**无新发现**。
- **nop-search 其余文件复核**: `LuceneConfig`（@Inject 后只读）、`LuceneHelper`（纯函数）、`LuceneFilterBeanTransformer`（第 1 轮确认 per-request 新建）——无新发现。

## 发现

### [C1-008] buildDocument 回写调用方 SearchableDoc（doc.setEmbedding 缓存副作用），SPI 实现突变调用参数且契约未声明

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:1034-1049`
- **证据片段**:
  ```java
  if (embedding == null && doc.isAutoGenerateEmbedding() && textEmbedding != null) {
      String textForEmbedding = buildTextForEmbedding(doc);
      if (!StringHelper.isEmpty(textForEmbedding)) {
          try {
              embedding = textEmbedding.embed(textForEmbedding);
              if (embedding != null && embedding.length > 0) {
                  doc.setEmbedding(embedding); // Cache for potential reuse
              }
  ```
- **严重程度**: P3
- **并发场景**: GraphQL worker 线程经 `SearchEngineBizModel.addDoc/addDocs`（@BizMutation）调用；docs 列表为请求域反序列化对象（单请求独占），当前实现（CL-007：caller 线程同步执行）下突变发生在调用方线程自身，无跨线程竞态。
- **触发时序**: 当前实现为契约缺陷而非竞态——T1 调用方 `addDocs(topic, docs)` → 引擎 :1043 对调用方持有的 doc 对象回写 `embedding` 字段 → addDocs 返回后调用方对象的 embedding 已被引擎替换为（无 ITextEmbedding bean 部署下的）hash 伪向量。**升级路径（理论风险，未具体化）**：SPI 以 `addDocsAsync` 交付异步语义，任何实现以真实异步线程覆写该方法后，T1 调用方线程在 addDocs 返回后读 doc 字段与 T2 引擎线程 :1043 写 doc 字段构成数据竞争（float[] 引用 + 可变对象跨线程，无 HB）。写不出当前实现下的具体交错，按校准规则不升 P2。
- **现状**: `ISearchEngine.addDocs`(:41) javadoc 无任何"实现可能保留/突变输入对象"声明；`SearchableDoc.setEmbedding`(:150) 的 caller 所有权约定仅存在于实现内部注释（"Cache for potential reuse"）。
- **风险**: 当前部署形态（无 ITextEmbedding bean → hash 伪向量回写）下，调用方复用同一 doc 对象再次 addDocs 时静默跳过重新生成（缓存命中伪向量）；未来异步实现接入时升级为真实数据竞争。均为语义/契约面，无数据损坏。
- **建议**: 移除 :1043 回写（改为 addDocs 局部变量传递 embedding），或在 `ISearchEngine.addDocs` javadoc 明文声明"实现可能修改传入 doc 的 embedding 字段"；同步在 `SearchableDoc` 类注记该字段的所有权移交语义。
- **失效面枚举（F3）**: `buildDocument`(:996) 为唯一回写点；调用面全查——`addDocs`(:825 经 :840 `this::buildDocument`)、`addDoc`(ISearchEngine.java:37 默认方法→addDocs，同面)、`BuildIndexTool.indexAll`(:58-72，每次新建 doc，突变不可见)、`SearchEngineBizModel.addDoc/addDocs`(:192-203，请求域对象)；`analyzeDoc`(:342)/`vectorSearch`(:547)/`parseQueryVector`(:628) 均只读不回写（逐一核对）。`ITextEmbedding.embed` 返回数组 :1054 直接交 Lucene 字段构造，无引擎侧保留。
- **信心水平**: 确定（回写行为代码直读）；竞态升级路径标注理论风险
- **误报排除**: 非"@DataBean 可变是平台惯例"的惯例本身报告（误报校准不覆盖）——缺陷点是 SPI 实现对**调用方所持对象**的隐性突变且无契约声明，属 C1 执行步骤 1e"可变对象参数逃逸"的定向形态。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C1-009] AutoTestCase.initDao 的 IContext ThreadLocal 方法级残留——C1-005 同族补充面（t_context 未列入第 1 轮失效面枚举）

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/AutoTestCase.java:183-187`；平台侧载体 `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/BaseContextProvider.java:15,36-42`
- **证据片段**:
  ```java
  // AutoTestCase.initDao（每个测试方法执行一次）
  IContext context = ContextProvider.getOrCreateContext();  // 无则 newContext(true) → t_context.set
  context.setUserId("autotest");
  context.setUserName("autotest-name");
  context.setUserRefNo("autotest-ref");
  IUserContext.set(null);
  // complete()(:227-254) 与 JunitAutoTestCase.destroy 均无 t_context 清理
  ```
- **严重程度**: P3
- **并发场景**: JUnit 测试线程与 `BaseContextProvider.t_context`（static ThreadLocal\<IContext\>）；顺序模式下全程主线程；并行模式下各方法 worker 线程各自残留。
- **触发时序**: 顺序模式——T1 类 A 方法 1 的 initDao :183 `getOrCreateContext` 经 BaseContextProvider(:36-42) `newContext(true)` → `t_context.set(context)`（userId=autotest）→ 方法 1 的 complete() 无清理 → **同线程**方法 2 的 initDao 复用同一未关闭 context（getOrCreateContext 的 isClosed 检查永假——无 close 调用点），方法间变量/属性跨用例残留；类边界由 `NopJunitExtension.beforeAll→BaseTestCase.beginTest`(:76) / `afterAll→endTest`(:81) 的 `BaseContextProvider.clear()` 兜底移除。**并行模式（条件化）**——beforeAll/afterAll 运行于类调度线程，方法 worker 线程 Y 上 initDao 设置的 t_context 在 afterAll 于线程 X 执行 clear() 时**不被清除**，Y 线程（若被线程池复用于后续类的并行方法）携带 userId=autotest 的陈旧 context 进入后续用例——后续代码读 `ContextProvider.currentUserId()` 得 "autotest"（审计字段 stamp 形态的污染）。
- **现状**: 第 1 轮 C1-005 的失效面枚举覆盖了 `t_vars`/`g_vars`/`useGlobalVars` 三件套，未覆盖同一 initDao 路径上的第四个 ThreadLocal（t_context）；类边界有兜底清理（beginTest/endTest 已核实），方法边界与并行 worker 线程无清理。
- **风险**: 仅测试期——顺序模式下方法间共享同一 context 对象（属性互相可见）；并行模式下跨类线程池复用线程时污染后续用例的上下文读取。按"仅测试期触达 + 类边界已有兜底"条件化降档 P3。
- **建议**: `AutoTestCase.complete()` 的 finally 中增加 `BaseContextProvider.clear()`（对齐 :187 `IUserContext.set(null)` 的对称清理）；或在 C1-005 建议的"不支持并行测试类"边界声明中一并覆盖本面。
- **失效面枚举（F3）**: t_context 写点全查（8 根内）——`AutoTestCase.initDao:183`（唯一 stamp 点，本缺陷）；`FixtureBundleRecordingSession.run:48` `ContextProvider.getOrCreateContext().setUserId("autotest")`（同面，session 作用域无 clear——但 tryLock 独占 + 调用方生命周期短，且类边界 beginTest/endTest 兜底，登记为同族兄弟状态：残留窗口更短）；清理点全查——`BaseTestCase.beginTest:76`/`endTest:81`（类边界兜底，已核实存在）、`BaseContextProvider.clear/detachContext`(:26-33)；`complete()`(:227-254) 无清理（缺陷面）。
- **信心水平**: 确定（清理点与残留链均可代码直读）；并行污染为条件化场景
- **误报排除**: 非重复登记——第 1 轮 C1-005 枚举清单不含 t_context（已逐字核对），本条为 F3 同族清扫的补漏；非平台设施本体重审（BaseContextProvider 归平台，本条针对 autotest 使用方缺清理对称）。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

## 补充既有发现的同族面（F3 清扫，不新增编号）

- **C1-001/C6-003 族补充**: `NopCliApplication.run`(:40-47) 的 `factory == null` 分支经 `new NopApplication().run(...)` + `CoreInitialization.destroy()`(:45) 后**进程可能继续存活**（Quarkus 主路径才退出）——destroy 后引擎 bean 的三 CHM 重建窗口（C6-003）在 CLI 形态同样可达，登记为同族触达面补充。
- **C1-007 族补充**: `NopTestConfigProcessor.process`(:31-33) 的 `CoreMetrics.registerClock(new TestClock())` 为第 1 轮 C1-007"全局翻动点"清单的遗漏项——类级全局时钟替换，afterAll(:77) 恢复配对干净，但并行模式下同属无守卫全局翻动（与 C1-007 同族同判级）。

## 维度集外线索（overlay）

### [nop-search-runner-autotest-CL-011] FileWatcher.stop() 语义为"停掉整个底层 NioFileWatchService"——对注入实例调用将杀死共享单例服务（平台 nop-core，C4/C6 交界）
- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/watch/FileWatcher.java:41-47`；使用面 `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliWatchCommand.java:84-104`
- **线索**: `FileWatcher.stop()` 直接委托 `watchService.stop()`。FileWatcherFactory（@Singleton）注入路径下 FileWatcher 包装的是**容器级共享** watchService；当前 CliWatchCommand 以 `WatcherHandle.locallyOwned` 旗标（javadoc :67-73）规避了对注入实例调用 stop，footgun 处于休眠——任何未来调用方对注入实例调用 stop() 将终止该 JVM 内所有 watcher 的事件泵线程。归属 nop-core 平台类契约面，移交平台审计单元；本批不动立。

## 继续判定

**判定：C1 维度继续（保留 1 个方向，其余收敛）。** 理由：本轮三个深挖方向中，search api SPI 可变态与 SourceCounter 已收敛（前者仅 C1-008 一个 P3 契约面，后者确认干净），cli-core 24 文件确认干净；剩余值得第 3 轮跟进的唯一方向是"被测业务代码在 autotest 执行期自行创建的线程/异步链与 AutoTestVars/t_context ThreadLocal 的交叉"（跨入被测代码域，超出本模块 8 根边界，建议随业务模块并发审计覆盖而非本单元第 3 轮）。本单元 C1 建议收敛。

## 复核结论（任务 B 追加）

- **C1-008**: 逐行回码确认 :1043 回写存在、SPI javadoc 无突变声明、F3 全查属实。P3 判级核对：当前 caller 线程语义（CL-007 已核实 FutureHelper.futureCall/futureRun）下无跨线程竞态，升级路径依赖未发生的实现替换——"理论风险降档"口径正确。**已保留（P3）**。
- **C1-009**: 回码确认 BaseContextProvider.t_context(:15) / beginTest:76 / endTest:81 清理点、AutoTestCase.initDao:183 stamp 点、complete() 无清理。顺序模式方法间残留与并行模式 worker 线程残留两条链均成立。P3 条件化与 C1-005 同族口径一致。**已保留（P3）**。
- **CL-011**: 回码确认 FileWatcher.stop():45-47 直接委托 watchService.stop()、FileWatcherFactory @Singleton 共享。休眠 footgun 定性正确，移交口径正确。**已保留（CL 登记）**。
- **同案命中查重**: C1-008/C1-009 与 cra-index 台账 WI 1.1–4.4 无同案命中（SPI 参数突变、autotest 上下文 ThreadLocal 均为首报）；C1-009 与 C1-005 为同族补漏非重复（枚举清单不含 t_context，已逐字核对）。
- **第 1 轮七条复核结果与最终保留项见 `C1-shared-mutable-state.md` 的「维度复核结论」「最终保留项」两节。**
