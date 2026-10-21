# C4 线程池与执行器治理 — nop-search-runner-autotest（WI 4.5）

- **审计员**: 独立盲审初审员（合并批 C1+C4+C6）
- **日期**: 2026-10-21
- **盲审纪律**: 未阅读 `ai-dev/audits/**`（本文件除外）、`ai-dev/plans/**`、`ai-dev/bugs/**`、`ai-dev/lessons/**`、`ai-dev/backlog/**`；未执行 git log。契约以代码与 javadoc 为准。

## 审计范围与口径

- **合并批三维度覆盖声明**: 本文件承担 C4（线程池与执行器治理）；C1 见 `C1-shared-mutable-state.md`，C6 见 `C6-resource-lifecycle.md`。同根缺陷跨维度交叉引用（C4-003 ↔ C1-003/C6-004），合并裁定留给复核阶段。
- **审计范围**: 8 源码根、112 文件 / 11,116 行，逐文件审读（模块划分同 C1 文件口径节）。
- **并发面极稀疏预校准声明**: 基线 C4a/C4b 节线程创建/调度命中 9 文件（CliWatchCommand、FileWatcherFactory、CliWatchZipCommand、CliCallToolsCommand、CliRunCommand、MockScheduledExecutor、NopJunitExtension、JunitAutoTestCase、NopCliApplication）+ Lucene 隐式线程（IndexWriter MergeScheduler）。本审计不凑数扩面：其余约 100 文件为一次性同步 CLI 命令与数据工具，无线程创建点，登记于清单表。
- **dbtool 零主源码声明**: `nop-autotest/nop-autotest-dbtool` 无 src/main/java（已核实，仅 4 测试文件），无线程池面可审。
- **owner doc 零覆盖声明**: 未读 owner 文档；`GlobalExecutors`/`SyncThreadPoolExecutor`/`RateLimitExecutorImpl`/`IScheduledExecutor` 契约以 nop-commons 源码为准；越界纪律：`SyncThreadPoolExecutor` 本体归 WI 2.1，本文件只审其使用面（CliCallToolsCommand:75）。
- **降档口径**: autotest 测试期执行按条件化降档；runner CLI watch 模式按实际触达面判级；search 按可部署引擎实际触达面判级。

## 线程池/线程创建点清单表

| 池/线程点 | 位置 | 参数 | 队列 | 拒绝策略 | 关闭路径 | 判定 |
|---|---|---|---|---|---|---|
| globalTimer（平台共享定时池，daemon） | 使用点：CliWatchCommand:90、CliWatchZipCommand:100、CliRunCommand:90、FileWatcherFactory:33、CliWatchCommand:88（本地兜底路径） | maxPoolSize 默认 5（CommonConfigs:22-24） | 平台 DefaultScheduledExecutor（本体归 WI 2.1，见 CL-006） | 平台侧 | 进程级（CoreInitialization.destroy）；CLI 场景随进程退出 | 使用面缺陷 C4-001/C4-002/C1-004 |
| SyncThreadPoolExecutor.INSTANCE | CliCallToolsCommand:75 | 无池（execute→ContinuationExecutor，caller 侧语义） | 无 | — | 无状态单例，无需关闭 | 使用面 P3，见 C4-004 |
| IndexWriter 默认 ConcurrentMergeScheduler（每 topic 惰性建 merge 线程） | LuceneSearchEngine.java:288-301（未显式配置，走 Lucene 默认） | Lucene 默认（maxThreadCount/maxMergeCount 自适应） | Lucene 内部 | Lucene 内部 | writer.close() 关停（destroy:194-203、removeTopic:1130-1137 配对） | 干净但随 topic 基数放大，见 C4-005 + CL-010 |
| SearcherManager 后台刷新 | LuceneSearchEngine.java:280 `new SearcherManager(writer, true, true, null)` | 第 4 参 null = 无 ScheduledExecutorService → **无后台刷新线程**，刷新全部由调用方 maybeRefresh* 驱动 | — | — | manager.close() 配对 | 干净（设计意图） |
| NioFileWatchService 内部单线程 | FileWatcherFactory:20-30（@PostConstruct start / @PreDestroy stop 配对）；CliWatchCommand:88-90 本地路径（finally stop 配对）；CliWatchZipCommand:47-60（**双重 init 不配对**） | 单线程 | — | — | stop() | **C6-001（归属 C6 文件）** |
| FutureHelper.futureCall/futureRun（ISearchEngine 异步默认实现） | ISearchEngine.java:47-57 | 无池——**caller 线程同步执行后包装**（FutureHelper.java:122-138 已核实） | — | — | — | 干净；命名误导见 CL-007 |
| autotest MockScheduledExecutor | MockScheduledExecutor.java:15-110 | 无线程（手动 triggerNext 驱动） | DelayQueue（无界） | — | destroy() 仅置死旗标 | **C4-003** |
| JUnit 平台线程 | NopJunitExtension/JunitAutoTestCase 生命周期回调 | — | — | — | afterAll destroy 配对 | 干净（顺序执行假设见 C1-007） |

无 `new Thread` 裸创建点（8 根内 grep 0 命中，与基线 C4a 一致）；无 commonPool 显式依赖点；无 ForkJoinPool 自定义。

## 发现

### [C4-001] CliRunCommand 周期任务异常即静默停摆：scheduleWithFixedDelay 返回 Future 被丢弃，F9 静默吞异常

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliRunCommand.java:89-99`
- **证据片段**:
  ```java
  if (interval > 0) {
      GlobalExecutors.globalTimer().scheduleWithFixedDelay(() -> {
          runTasks(globalState);
      }, interval, interval, TimeUnit.MILLISECONDS);   // 返回的 ScheduledFuture 未接

      try {
          System.in.read();                            // 主线程停留于此
  ```
- **严重程度**: P1
- **并发场景**: 主线程阻塞于 stdin；globalTimer worker 线程周期执行 runTasks → runFile → `xpl.invoke(scope)`。runTasks 抛任何异常（脚本解析失败、`.xrun` 文件被编辑/删除导致 `ERR_CLI_DIR_NOT_CONTAINS_TASK_FILE`/`ERR_CLI_FILE_NOT_TASK_FILE`、ResourceComponentManager 加载失败）。
- **触发时序**: T1 timer 线程执行 runTasks 抛 NopException → JDK 语义（ScheduledThreadPoolExecutor 契约）：周期任务任一次执行遇异常，**后续所有执行被抑制**，异常仅存于返回的 Future → 该 Future 被丢弃，无人 get/日志 → 周期循环无声死亡 → 主线程继续阻塞在 `System.in.read()`，进程存活但功能已死。
- **现状**: watch/interval 模式的核心循环对"任务失败"零感知、零恢复、零日志；`runTasks` 同时在 :87 被主线程首次调用（该次异常正常上抛退出，与周期路径行为不一致）。
- **风险**: `nop run -t <interval>` 是 runner 的实际使用形态（按实际触达面不降档）：开发期脚本处于半编辑态是常态 → 首次异常即永久停摆，用户误以为仍在 watch。长时运行必然触发 + 异常静默吞掉致状态不一致（F9 对照项），判 P1。
- **建议**: 包裹 `runTasks` 捕获并 `LOG.error`（对照同批 CliWatchZipCommand:133-135 的正确形态）；持有 ScheduledFuture，在异常时决定重排或退出进程（exit code 非 0）。
- **失效面枚举（F3）**: 平台周期调度使用点全查——`CliRunCommand:90`（本缺陷）；`CliWatchZipCommand` 经 RateLimitExecutorImpl debounce（异常在 lambda 内 :129-135 被 catch+log，干净）；`CliWatchCommand` 经 FileWatcher debounce（异常未被 catch，静默但循环不死——独立登记为 C4-002）；autotest MockScheduledExecutor 无周期调度执行线程（手动驱动，不复排缺陷归 C1-003）。`CliRunTaskCommand`(:103) syncGetResult 单次同步等待，非周期面。
- **信心水平**: 确定（JDK scheduleWithFixedDelay 异常抑制为官方契约；代码路径无任何对冲）
- **误报排除**: 非平台设施本体审计（DefaultScheduledExecutor 归 WI 2.1/CL-006）——缺陷在使用方丢弃 Future 且无异常对冲的使用形态；亦非低频路径粗同步的误报校准项（这是功能主路径）。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C4-002] CliWatchCommand 脚本异常在 globalTimer 任务中被静默吞掉；与 CliWatchZipCommand 同族不对称

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliWatchCommand.java:122-126,138-151`；对照 `CliWatchZipCommand.java:128-136`
- **证据片段**:
  ```java
  // CliWatchCommand: 回调直抛
  watcher.watch(..., debounceWait, events -> processEvents(xpl, events, state));
  private void processEvents(...) {
      ...
      xpl.invoke(scope);            // XPL 脚本异常直接冒泡出 debounce 任务
  // CliWatchZipCommand（对照，正确形态）:
  } catch (Exception e) {
      LOG.error("nop.zip-tool.zip-update-failed: ...", e);
  ```
- **严重程度**: P2
- **并发场景**: debounce 任务在 globalTimer worker 上执行 `processEvents`；XPL 脚本（用户 watch 的 `-e` 脚本）抛异常。
- **触发时序**: T1 timer 线程执行 debounce 任务 → `xpl.invoke(scope)` 抛异常 → 经 `RateLimitExecutorImpl.debounce`(:56-64) 的 schedule 包装冒泡至 `DefaultScheduledExecutor`，存入返回 Future 后被丢弃（FileWatcher.triggerChange:70 未持有）→ 一次脚本执行失败无任何日志；后续事件仍会触发新执行（循环不死），故与 C4-001 不同：**失败不可见而非功能死亡**。另 F9 联动：脚本半途失败后 `globalState` 可能停留在中间态，下一次执行读到脏状态继续跑。
- **现状**: 同批同族三个 watch 类对任务异常三种处理（CliRunCommand 丢弃→循环死；CliWatchCommand 丢弃→失败不可见；CliWatchZipCommand catch+log 正确），处理不对称。
- **风险**: watch 模式脚本执行失败对用户完全静默（脚本抛错与脚本成功在外部表现一致），开发调试成本高；状态中间态可能污染后续执行。
- **建议**: processEvents 内 catch + LOG.error（对齐 CliWatchZipCommand 形态）；如需失败即停，参照 C4-001 建议显式退出。
- **失效面枚举（F3）**: FileWatcher 消费者全查——`CliWatchCommand`（本缺陷）；nop-core `FileWatcher.processChangeEvent:119 handler.accept(events)` 为同族中间层（异常同样无人捕获，平台侧随 CL-001 移交）；`CliWatchZipCommand`（已正确，豁免）。无其他 IFileWatchListener 消费者在本批 8 根内。
- **信心水平**: 确定
- **误报排除**: 不是"异常应否上抛"的风格之争——判定依据是消费者侧异常零可见性（连日志都没有），F9 校准项的直接实例；对照兄弟已证明仓库内既有正确形态。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C4-003] MockScheduledExecutor destroy 生命周期无效：死旗标、不拒绝后续提交、与真执行器生命周期契约不对称

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/mock/MockScheduledExecutor.java:17,102-104`
- **证据片段**:
  ```java
  @Override
  public void destroy() {
      started = false;      // 唯一写点；全类无读取点
  }
  ```
- **严重程度**: P2
- **并发场景**: 测试期——测试框架按 IScheduledExecutor 生命周期契约（init → 提交任务 → destroy 拒绝新任务/排空）管理替身；destroy 与任务提交/触发在测试线程顺序发生（单线程触达）。
- **触发时序**: 非交叠竞态而是生命周期契约缺失——T1 destroy() 置 started=false → T2（甚至同线程后续）调用 schedule/submit 照常入队成功、triggerNext 照常执行：destroy 后执行器行为与 destroy 前完全一致。对照真执行器（DefaultScheduledExecutor）destroy 后提交将 fail-fast。
- **现状**: C4 执行步骤 3（关闭路径：shutdown + 后续提交拒绝）与步骤 6（F8 池生命周期与持有者对称）双双不满足；`started` 为只写死旗标。与 C1-003（时间/周期语义）、C6-004（队列残留/promise 挂起）同根不同面。
- **风险**: 测试替身掩盖真实执行器的关闭语义，依赖它的测试无法暴露生产代码在 shutdown 窗口提交任务的缺陷（假保护）。当前零调用方，测试期条件化 P2。
- **建议**: schedule/submit/triggerNext 入口检查 `started`，destroy 后 fail-fast（抛 IllegalStateException 或 ERR 已关闭 ErrorCode）；与 C6-004 的排空/完成语义一并修复。
- **失效面枚举（F3）**: 提交面全查——schedule(:25)/scheduleAtFixedRate(:36)/scheduleWithFixedDelay(:48)/submit(:87,:92)/execute(:107)，全部无 started 守卫；triggerNext(:59)/pollTask(:66) 触发面同样无守卫。对照面：GlobalExecutors.register(:92-97) 旧池 destroy 的平台形态（正确对照）。
- **信心水平**: 确定
- **误报排除**: 非"替身简化并发模型豁免"——豁免针对并发模型简化，不针对生命周期契约缺失造成的假保护（F10 校准项为定向检查）。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C4-004] CliCallToolsCommand 对 SyncThreadPoolExecutor 的使用面：join() 无超时无取消路径

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliCallToolsCommand.java:72-85`
- **证据片段**:
  ```java
  IToolExecuteContext context = ToolExecuteContext.builder()
          .workDir(workDir)
          .fileSystem(fileSystem)
          .executor(SyncThreadPoolExecutor.INSTANCE)   // :75 使用面（本体归 WI 2.1）
          .build();
  ...
  AiToolCallsResponse response = toolManager.callTools(calls, context).join();  // :85 无超时
  ```
- **严重程度**: P3
- **并发场景**: CLI 单次调用，callTools 经注入执行器异步编排工具调用，主线程 `join()` 无限期等待。
- **触发时序**: 单线程可触发——任一工具调用挂死（如工具内部读 stdin、网络无超时）→ join() 永不返回，CLI 挂死且无诊断输出；无 cancellation 钩子，Ctrl-C 外无退出路径。
- **现状**: SyncThreadPoolExecutor 使用本身无违规（无界并发提交场景不存在，工具批为一次性有限集）；缺陷仅在等待点无超时。
- **风险**: AI 工具批执行链路的可诊断性；CLI 进程挂死需人工干预。P3。
- **建议**: `join()` 改为 `FutureHelper.get(future, timeout)` 或 `orTimeout(...)` 并在超时路径输出挂死工具 id；失败/超时后对 context 内在途任务执行取消。
- **失效面枚举（F3）**: SyncThreadPoolExecutor 使用点全查（8 根内）——仅 CliCallToolsCommand:75 一处（与基线一致）；`GlobalExecutors.syncExecutor()`(GlobalExecutors:87-89) 为同单例的另一获取路径，本批无调用。
- **信心水平**: 确定
- **误报排除**: 严守越界纪律——不对 SyncThreadPoolExecutor 本体（ContinuationExecutor 语义）作任何判定；本条只覆盖使用方等待点。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C4-005] Lucene 每 topic IndexWriter 隐式 ConcurrentMergeScheduler 线程随 topic 基数无上界放大

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:288-301`
- **证据片段**:
  ```java
  IndexWriterConfig writeConfig = new IndexWriterConfig(analyzer);
  writeConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
  writeConfig.setRAMBufferSizeMB(config.getRamBufferSizeMB());
  return new IndexWriter(dir, writeConfig);   // 未配置 MergeScheduler → 默认 ConcurrentMergeScheduler
  ```
- **严重程度**: P3
- **并发场景**: topic 首次触达时 `computeIfAbsent` 创建 IndexWriter，Lucene 默认 ConcurrentMergeScheduler 在合并时惰性创建 "Lucene Merge Thread" 线程（每 writer 上限由 Lucene 默认 maxThreadCount 控制）。
- **触发时序**: 非交叠竞态，为容量形态：topic 基数 N（用户可控，见 C6-002）× 每 writer 常驻/惰性 merge 线程与 fd → 线程与句柄总量随 N 线性放大，无全局上界配置点。
- **现状**: 单 writer 线程模型符合 Lucene 惯例（非缺陷）；缺的是 topic 级总量治理视角，与 C6-002 同根的容量面。
- **风险**: 多 topic 长驻部署下线程/fd 缓慢累积；不单独构成 OOM/耗尽证据链，P3 观察项。
- **建议**: 治理点在 topic 生命周期（C6-002 建议的逐出/TTL）；如需进一步收敛，可为 IndexWriterConfig 显式配置共享/串行 MergeScheduler。
- **失效面枚举（F3）**: 线程创建面全查——`getIndexWriter`(:289) 为唯一 IndexWriter 创建点；`getSearcherManager`(:280) 已确认无后台刷新线程；destroy(:194-203)/removeTopic(:1130-1137) 的 writer.close() 关停 merge 线程配对（干净）。第三方线程模型随 CL-010 移交。
- **信心水平**: 很可能（Lucene 默认 MergeScheduler 行为为文档化常识，未逐版本核对）
- **误报排除**: 非"使用线程池原语即报"——单 writer 的 merge 线程是 Lucene 设计意图（误报校准第 2 条邻形）；本条仅登记容量放大形态，未按 P2 立项。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

## 维度集外线索（overlay C2/C3/C5/C7/C8/C9）

### [nop-search-runner-autotest-CL-005] RateLimitExecutorImpl debounce/throttle 的 cancel 语义与在跑无守卫（平台 nop-commons，C2 overlay）
- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/RateLimitExecutorImpl.java:50-67`
- **线索**: debounce 对运行中任务 `old.cancel(true)` 仅置中断位（不 join），随后照常调度新任务——多线程执行器下两个任务重叠运行；throttle 分支 promiseMap compute 内 schedule（key 条目在任务 finally 清理，取消路径残留由下次调用覆盖）。平台本体缺陷面，是本批 C1-004 的根因侧；overlay C2/C4。归属 nop-commons 并发审计单元（与 WI 2.1 相邻）。

### [nop-search-runner-autotest-CL-006] DefaultScheduledExecutor / globalTimer 本体（登记类）
- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:37-50`
- **线索**: 队列有界性、拒绝策略、shutdown 语义未审（平台设施不重审口径）；本批全部使用面已按"daemon + maxPoolSize 有界（timer 默认 5 / worker 默认 30）"消费。本体归 WI 2.1。

### [nop-search-runner-autotest-CL-007] ISearchEngine 异步默认实现为 caller 线程同步执行，命名误导（C3 overlay）
- **文件**: `nop-search/nop-search-api/src/main/java/io/nop/search/api/ISearchEngine.java:47-57`
- **线索**: `searchAsync/addDocAsync/addDocsAsync` default 方法经 `FutureHelper.futureCall/futureRun`（FutureHelper.java:122-138）在**调用方线程同步执行**后包装 CompletionStage——无池分发、无异步性；`SearchEngineBizModel.searchAsync`(:179) 透传该语义。C3（异步链）线索：接口以 Async 命名交付同步语义，SPI 实现者可能误假设池隔离。契约层面登记。

### [nop-search-runner-autotest-CL-012] RateLimitExecutorImpl.debounce 的 get/put 非原子窗口（CL-005 同类补充面，平台 nop-commons）
- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/RateLimitExecutorImpl.java:50-67`
- **线索**: `promiseMap.get(key)`(:52) 与 `promiseMap.put(key, future)`(:66) 之间无同步，并发同 key 双调度。当前使用点均单生产者线程未触达。随 CL-005 归 nop-commons 并发审计单元。**复核状态**: 已确认（R2 复核员回码证实）。

## 维度复核结论（WI 4.5 R2 独立复核员）

- **复核范围与方法**: 本文件 5 条初审发现 + 3 条 CL 全部逐行回码验证（CliRunCommand 全文、CliWatchCommand 全文、CliWatchZipCommand 全文、CliCallToolsCommand 全文、MockScheduledExecutor/MockTask 全文、LuceneSearchEngine :288-301、RateLimitExecutorImpl 全文、GlobalExecutors/ExecutorHelper/CommonConfigs 契约）；越界纪律核对（DefaultScheduledExecutor/SyncThreadPoolExecutor 本体零判定）；跨单元对照 cra-index 台账零同站点命中。
- **三态判定统计**: 保留 5 / 降级 0 / 驳回 0。
- **P1 唯一条目（C4-001）子项复核说明**: (a) JDK 周期任务异常抑制契约——ScheduledThreadPoolExecutor 官方语义（任一次执行遇异常即抑制后续全部执行），引用无误；(b) future 丢弃——:90 返回值未接任何变量，回码证实；(c) 可抛触发面——ERR_CLI_FILE_NOT_TASK_FILE(:106)/ERR_CLI_DIR_NOT_CONTAINS_TASK_FILE(:120)/loadComponentModel(:127) 解析失败均实存，**R2 补充第四触发面：目录形态 `file.listFiles()` 瞬时 IO 失败返回 null → :112 `Arrays.sort(subFiles)` NPE（无需脚本错误，文件系统瞬时不可读即触发）——并入本条触发时序**；(d) 主线程停留 :94-98 与"进程存活功能已死"后果链成立；(e) :87 首次调用与周期路径行为不一致成立。P1 判级（长时运行必然触发 + F9 静默吞 + 实际触达面）维持。
- **其余条目**: C4-002 的三 watch 类异常处理不对称经三文件对照证实；C4-003/C4-006（R2）替身生命周期/取消契约缺失链闭合；C4-004 join() 无超时 :85 证实；C4-005 维持 P3 观察项（Lucene 默认 MergeScheduler 为文档化行为）。
- **junit 生命周期线程面（R2 深挖）**: GlobalExecutors 池注册表为进程级 static CHM，CoreInitialization.destroy 不触碰（每类 initialize/destroy 循环无线程churn、无池泄漏链）；本批 8 根内无 globalTimer 跨类残留任务使用点。**确认干净，无新立条目**。
- **最终保留项**:
  - **P1×1**: C4-001（CliRunCommand 周期任务异常静默停摆——含 R2 补充的 listFiles NPE 触发面）。
  - **P2×4**: C4-002（脚本异常不可见）、C4-003（Mock destroy 生命周期无效）、C4-006（R2：Mock 取消语义缺失）、C4-007（R2：注入路径命令返回后 watch 残留活跃）。
  - **P3×2**: C4-004（join 无超时）、C4-005（MergeScheduler 容量观察）。
  - CL: CL-005/CL-006 确认移交（nop-commons / WI 2.1）、CL-011/CL-012 确认（新增移交）、CL-007 确认（ISearchEngine 同步语义，经 FutureHelper :122-138 回码）。