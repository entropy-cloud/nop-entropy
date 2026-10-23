# C6 资源生命周期与泄漏 — 初审报告（第 1 轮）

- **审计单元**: nop-peripheral（nop-dev-tools 155f + nop-frontend-support 84f + nop-demo 94f + nop-entropy-e2e 0f，共 333f/26,887 行）
- **维度**: C6 资源生命周期与泄漏（CRA WI 6.5）
- **批次**: 全单元单批·抽查模式（同 C1 批次口径）
- **CL 前缀**: 本维度集外线索使用 CL-201 起
- **盲审声明**: 本初审为盲审——未读取 `ai-dev/audits/` 下任何已落盘文件（含同目录 C1 产出）、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`。全部结论仅基于当前 live code 与 `_tmp/cra-baseline-nop-peripheral.txt` 命令基线。
- **校准声明**:
  - idea-plugin 的 IntelliJ 平台惯例已按惯例校准，以下**不立案**：session 级 listener（`session.addSessionListener`、`DebuggerContextManager.addListener`）随 session/DebuggerSession 生命周期销毁；`NopProjectService` 实现 `Disposable` + `@Service(projectService)`（plugin.xml L93），`dispose()` 经 `Cancellable` 统一释放 XLangCoreInitializer 与 componentModelConfig 注册——配对成立；`Cancellable.appendOnCancel` 在已取消后追加任务会立即执行（Cancellable.java L63-73 + L107-124），故 `XLangDebugProcess.stop()` 与后台 connect 任务之间的取消竞态不会漏掉 `connector::destroy`。
  - demo 降口径：仅临时文件/进程/socket 的结构性泄漏立案；内存模拟状态不立案（见配对表 #24）。
  - 平台基础设施（nop-kernel/nop-network/nop-rpc-simple/nop-socket）自身实现不在本单元重审，仅审 nop-dev-tools 使用方式的配对；但 **本单元唯一生产使用方** 的配对缺失照常立案（C6-001）。
- **基线命中甄别**: baseline「C6 close/进程/socket」命中的 `XLangAnnotator` / `XLangElementRenameProcessor` / `XLangScriptNodeRenameProcessor` / `XLangTagMeta` / `XLangTag` / `XLangVfsPathInJavaReferenceContributor` 经逐文件核对均为 `ProcessCanceledException` / `ProcessingContext` 字样误命中，无真实资源获取点；`XLangLanguageSubstitutor:68` 的 InputStreamReader 在 try-with-resources 中，合规。

---

## 一、资源获取点配对表

| # | 模块 | 资源 / 注册表 | 获取点 | 释放点 | 判定 |
|---|------|--------------|--------|--------|------|
| 1 | nop-xlang-debugger | `XLangDebugger.cleanupThread`（单线程 ScheduledExecutor，daemon） | 构造器 L72-73 | `close()` L86 `shutdown()`；`XLangDebuggerInitializer.destroy()` L161-167 调用 close | 配对成立 |
| 2 | nop-xlang-debugger | `suspendedThreads` 注册表（LongHashMap，持 Thread + EvalRuntime） | `makeSuspendedThread` put L567 | 仅 L99（构造后 30s **一次性** cleanup）与 L90（close clear） | **不对称 → C6-002** |
| 3 | nop-xlang-debugger | `SimpleRpcServer`（socket server） | `initialize()` L56-70 start | `destroy()` L158 stop | 配对成立（start 抛异常路径 destroy 仍会执行 stop + close debugger） |
| 4 | nop-xlang-debugger | `EvalExprProvider` 全局执行器注册 | `initialize()` L77 注册 DebugExpressionExecutor | `destroy()` L156 恢复 DefaultExpressionExecutor | 配对成立（两 app 同 JVM 重叠初始化时序漂移属测试场景边缘，不立案） |
| 5 | nop-xlang-debugger | `compileCache` / `externalPathCache`（LocalCache） | L55-56 / L104-120 | 有界（100 / 1000）自动淘汰 | 合规 |
| 6 | nop-idea-plugin | `SimpleRpcClientFactory` 自有 timer + executor | `connect()` → `newInstance()` 惰性 init（SimpleRpcClientFactory L121-128） | `XLangDebugConnector.destroy()` L49-52 → `clientFactory.destroy()`（L136-146） | 工厂侧配对成立 |
| 7 | nop-idea-plugin | **SocketClient socket fd + heartbeat + recv 线程** | `SimpleRpcClientFactory.newInstance()` L156-172（`client.connect()` + `executeRecv`） | 平台提供 `proxy_destroy()` → `SimpleRpcClient.close()` → `SocketClient.close()`；**插件侧零调用**（grep 全插件无 IRpcProxy/proxy_destroy/closeAsync/safeDestroy） | **缺失 → C6-001** |
| 8 | nop-idea-plugin | `Cancellable cleanup`（DebugProcess L63 / NopProjectService L30） | appendOnCancelTask | `stop()` L255 / `dispose()` L77 cancel | 配对成立（含 append-after-cancel 语义，见校准声明） |
| 9 | nop-idea-plugin | `XLangBreakpointHandler.breakpoints/bpMap` | registerBreakpoint L66-67 | unregisterBreakpoint L81-82；会话级、随 process 丢弃 | 合规 |
| 10 | nop-idea-plugin | session/contextManager listener | XLangDebugProcess L76 / XLangDebuggerRunner L93-94 | 平台会话生命周期绑定 | 合规（惯例校准） |
| 11 | nop-idea-plugin | `VirtualFileResource.getInputStream/getOutputStream` | L90-105 | 调用方（平台资源加载框架）负责关闭 | 合规 |
| 12 | nop-idea-plugin | `ProjectFileHelper.nopVfsPathCaches`（static Map\<Project, CachedValue\>） | `getCachedSearchResult` computeIfAbsent L174 | **无任何 remove**，项目关闭不清理 | **不对称 → C6-003** |
| 13 | nop-idea-plugin | `ProjectEnv` ThreadLocal\<Project\> | withProject L17-25 | finally 恢复旧值（save-set-restore） | 合规 |
| 14 | nop-idea-plugin | `NopProjectService` 内 XLangCoreInitializer + componentModelConfig 注册 | init() L50-57 / registerXlib L87 | Disposable.dispose → cleanup.cancel 逆序释放 | 配对成立 |
| 15 | nop-maven-shaded-plugin | `processResource` 入参 InputStream | XdslResourceTransformer L56-58 / SpringFactoryResourceTransformer L42-43 | 调用方（maven shade）所有 | 合规 |
| 16 | nop-maven-shaded-plugin | `modifyOutputStream` 内 resource InputStream | AbstractResourceTransformer L63 | finally `IoHelper.safeClose(in)` L66-68 | 合规 |
| 17 | nop-maven-shaded-plugin | SpringFactory Writer（包装 jarOut） | L65 | flush 不 close（close 会破坏 jarOut）——正确 | 合规 |
| 18 | nop-js | **GraalVM `Context`**（JavaScriptWorker） | `init()` L318-323 → createContext | ① close 任务 L115-118 ② run() finally L312 ③ FUNC_REINIT L143 | **全路径配对成立**（含 close 与 poll 竞态交错：close 任务经 run() finally 的 `cancelAllTasks()` L311 兜底执行，无泄漏交错——资源侧独立结论，与 C1 批同站点竞态的语义侧结论互不依赖） |
| 19 | nop-js | `LogOutputStream` out/err | createContext L342-343 | 内存缓冲写 SLF4J，无 OS 资源 | 合规 |
| 20 | nop-js | `JavaScriptService` executor | doStart L40 | doStop L134 `executor.destroy()` | 配对成立（先关 worker 再关池，顺序正确） |
| 21 | nop-js | refreshConfig 收缩路径被裁撤 worker | L108-116 | `shutdownGracefully()` → 队列空时自退 → run() finally 关 context | 配对成立 |
| 22 | nop-web | GlobalExecutors.globalWorker + Semaphore（validateAllPages/renderPagesTo/runParallel） | PageProvider L91-135 / WebPageExporter L117-121 | 无所有权的平台共享池；许可由 `ExecutorHelper.throttleExecute` 平台工具管理 | 合规（平台工具许可释放归 C8 面） |
| 23 | nop-demo | `DemoServiceBizModel.RESOURCE_STORE`（static ConcurrentHashMap） | testTcc put L46 | 仅 cancelTestTcc remove L69；confirm 路径无删除点 | **降口径不立案**：内存模拟 TCC 资源、demo 模块、非临时文件/进程/socket |
| 24 | nop-demo | 临时文件 / 进程 / socket | grep 全模块无 `createTempFile`/`ProcessBuilder`/`Socket` 命中（仅两个 demo Main 读 maxMemory） | — | 降口径通过 |

---

## 二、发现条目（第 1 轮初审）

### [C6-001] IDEA 插件调试会话的 socket 通道从不关闭：每次会话泄漏 fd，Disconnect 路径钉死一个 recv 线程

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/debugger/XLangDebugConnector.java:49-56`；关联 `XLangDebugProcess.java:126-149,254-261`
- **证据片段**:
  ```java
  // XLangDebugConnector —— 销毁只覆盖工厂自有线程资源
  public void destroy() {
      clientFactory.destroy();     // 仅 destroy ownTimer + ownExecutor
  }

  public IDebuggerAsync connect() {
      return clientFactory.newInstance();   // 每次 new SocketClient + connect() + executeRecv
  }
  ```
  ```java
  // SimpleRpcClientFactory.destroy() —— 不触及任何已创建的 SimpleRpcClient/SocketClient
  public void destroy() {
      destroyed = true;
      if (ownExecutor && executor != null) executor.destroy();
      if (ownTimer && timer != null) timer.destroy();
  }
  ```
- **严重程度**: P1
- **并发场景**: IDEA 主进程内：`Task.Backgroundable` 连接线程（XLangDebugProcess.sessionInitialized L108-123）创建通道；`recv` 任务在 `simple-rpc-client-executor` 池线程上阻塞于 `SocketClient.recv()` → `InputStream.read()`；heartbeat 在 `simple-rpc-client-timer` 上调度。会话停止时 EDT 调用 `XLangDebugProcess.stop()`。
- **触发时序**: T1（用户停止/断开调试会话）→ `stop()` L255 `cleanup.cancel()` → `connector.destroy()` → timer shutdownNow + executor shutdown。T2（recv 池线程）仍阻塞在 `SocketClient.recv()` 的 `is.read()` 上——`DefaultThreadPoolExecutor.destroy()` 只是 `executor.shutdown()`（DefaultThreadPoolExecutor.java L89-91，不中断在途任务），且 socket read 不响应中断；socket 自身从未 `close()`。若 debuggee 进程被杀：客户端 socket 停留 CLOSE_WAIT，fd 在 IDE 进程内保留至 IDE 退出；若用户走 Disconnect（debuggee 存活，`readTimeout=0` 且服务端 `idleTimeout=0` 双侧均无空闲回收）：连接保持 ESTABLISHED，recv 线程永久 pinned + fd + 服务端 channel 全部滞留。每次调试会话至少泄漏 1 个 fd，Disconnect 场景再 +1 个被钉死的池线程与一条活连接。
- **现状**: 获取侧完整（connect → SocketClient.connect + heartbeat + recv），释放侧只有工厂线程资源销毁；平台提供的通道级关闭契约 `((IRpcProxy) proxy).proxy_destroy()` → `DestroyHelper.safeDestroy(SimpleRpcClient)` → `SocketClient.close()`（RpcInvocationHandler.java L59-64，SimpleRpcClient.java L112-115）在插件主源码中零调用（grep `IRpcProxy|proxy_destroy|closeAsync|safeDestroy` 于 nop-idea-plugin/src/main 无命中）。
- **风险**: 长时间运行的 IDE 实例内多次调试后 fd 持续累积（CLOSE_WAIT 堆积）；Disconnect 场景每次钉死一个线程与一条活的 debug 连接，被调试应用侧的 XLangDebuggerInitializer 服务端 channel 同步滞留。泄漏点在 IDE 进程，用户重启 IDE 前不可回收。
- **建议**: `XLangDebugConnector` 保存 `connect()` 返回的 proxy，`destroy()` 中先 `((IRpcProxy) proxy).proxy_destroy()`（等价于关闭 SimpleRpcClient→SocketClient，解除 read 阻塞）再 `clientFactory.destroy()`，顺序为先关通道后关池。同仓测试 `TestRemoteDebugger.tearDown()`（L141-147：`clientFactory.destroy(); server.stop(); DestroyHelper.safeDestroy(debugger);`）即为正确配对的参照实现。
- **失效面枚举**（F3 同族清扫）: `SimpleRpcClientFactory` 全仓生产代码使用方仅 `XLangDebugConnector` 一处（grep 全仓命中仅此 + 定义处）；`SimpleRpcClient.close()` 的唯一可达调用链是 `RpcInvocationHandler` 对 `proxy_destroy` 的分发；测试面 `TestRemoteDebugger` 已正确配对——缺陷面收敛为插件这一处生产调用方。
- **信心水平**: 确定
- **误报排除**: 不是「平台基础设施自身不重审」的范畴——立案对象是本单元内唯一生产使用方的配对缺失，平台 close 契约存在且测试代码已在遵循；也不是「IDE 退出会兜底」可豁免的问题——泄漏在会话粒度发生，生命周期远短于 IDE 进程。`Cancellable` 的 append-after-cancel 语义已验证可兜住 stop 与 connect 的竞态，故不存在「另有隐含关闭路径」。
- **复核状态**: 已保留（P1）｜新发现——关闭契约缺失全链独立核实（工厂不登记已建 client、插件零 close 调用）；附精确化注记：debuggee 被杀子场景经 autoReconnect 默认 true 修正为「无限重连僵尸循环」而非 CLOSE_WAIT 永久滞留，Disconnect 场景核心泄漏主张不受影响，详见复核结论节第 1 条。

### [C6-002] XLangDebugger.suspendedThreads 只进不出：清理任务为一次性调度，线程与最后挂起现场的 EvalRuntime 图被永久保留

- **文件**: `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/XLangDebugger.java:72-103,558-571`
- **证据片段**:
  ```java
  private ScheduledExecutorService cleanupThread = Executors
          .newSingleThreadScheduledExecutor(NamedThreadFactory.newThreadFactory("xlang-debugger-cleanup", true));
  ...
  public XLangDebugger() {
      cleanupThread.schedule(this::cleanup, 30, TimeUnit.SECONDS);   // 一次性，非 scheduleAtFixedRate
  }
  ...
  private SuspendedThread makeSuspendedThread(EvalRuntime rt) {
      ...
      SuspendedThread thread = suspendedThreads.get(currentThread.getId());
      if (thread == null) {
          thread = new SuspendedThread(currentThread, rt, this::toSourcePath);
          suspendedThreads.put(currentThread.getId(), thread);
      }
  ```
- **严重程度**: P2（宿主为 thread-per-request 模型时升级 P1）
- **并发场景**: 服务端（debuggee 进程）HTTP/RPC 工作线程执行到 `checkBreakpoint` 命中断点时注册；调试器清理线程仅构造后 30s 运行一次；RPC 服务线程通过 getSuspendedThreads/getStackInfo 读取。
- **触发时序**: T0 应用启动、调试器开启（`nop.xlang.debugger.enabled`）→ 30s 时 cleanup 唯一一次运行，此时表通常为空 → 之后任意工作线程每次首次命中断点/单步时 `makeSuspendedThread` put（L567）→ 该条目（`Thread` 强引用 + 最后一次挂起点的 `EvalRuntime`，即完整 scope/frame 对象图）在 resume 后仍留存，且 30s 后死亡的线程条目也永不再清理（put L567 / remove L99 一次性 / clear L90 仅 close）→ 长驻服务中每个曾命中断点的线程各永久滞留一份断点现场。
- **现状**: 注册/注销不对称。条目按设计跨断点复用（stepOver/stepOut 依赖 `getSuspendedThread(currentThread)` 读取 lastBreakFrameIndex，L401/L424），因此不能在 resume 时简单移除，但周期性清理显然是本意（一次性 `schedule` 疑为 `scheduleAtFixedRate` 笔误），而一次性调度使清理完全失效。
- **风险**: 池化线程模型下以线程数为上界，但每条目保留最后一次断点处的业务对象图（请求数据、scope 变量），调试器开启的长驻服务出现可感知的常驻内存滞留；若宿主为每请求新建线程（如部分简单 socket 容器），则演化为无界增长（条目数随历史请求数单调上升），属 P1「长时运行必然触发的无界增长」。
- **建议**: 将 L80 改为 `scheduleWithFixedDelay(this::cleanup, 30, 30, TimeUnit.SECONDS)`（保留 close 时 shutdown 即可）；cleanup 已按 `isAlive()` 判活删除，逻辑无需变动。
- **失效面枚举**: `suspendedThreads` 是该类唯一长生命周期注册表（put L567 / remove L99 / clear L90 三点已全量核对）；同族清理器 `cleanupThread` 本身在 close() 有 shutdown，配对成立；`compileCache`/`externalPathCache` 均为有界 LocalCache，无同类问题。
- **信心水平**: 确定
- **误报排除**: 不是「有界即合规」的误报——上界不是配置声明而是宿主线程模型的偶然属性，且条目载荷（EvalRuntime 全量现场）远大于普通缓存项；也不是 C1 的共享状态可见性问题（本条目只主张注册表对称性，setSuspended 的可见性排序已在 doSuspend 注释中处理）。
- **复核状态**: 已保留（P2）｜新发现——L80 一次性 schedule、put L567/cleanup L94-103/clear L90 三点、条目 EvalRuntime 载荷（SuspendedThread.java L34-37）均独立核实；C1 维度 CL-003 为本条同站点线索已归并，详见复核结论节第 2 条。

### [C6-003] ProjectFileHelper.nopVfsPathCaches 以 Project 为键的静态缓存无项目关闭清理：项目对象与全量 VFS 路径集被永久强引用

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/utils/ProjectFileHelper.java:49,170-187`
- **证据片段**:
  ```java
  private static final Map<Project, CachedValue<Collection<String>>> nopVfsPathCaches = new ConcurrentHashMap<>();
  ...
  public static <T> T getCachedSearchResult(
          Map<Project, CachedValue<T>> caches, Project project, Function<Project, T> searcher) {
      return caches.computeIfAbsent(project, (p) -> {
          return CachedValuesManager.getManager(p).createCachedValue(() -> { ... });
      }).getValue();
  }
  ```
- **严重程度**: P2
- **并发场景**: EDT/后台索引线程在任意 XLang 文件补全、字典解析、annotator 路径上触发 computeIfAbsent 写入；无并发正确性问题（ConcurrentHashMap），问题纯在生命周期侧。
- **触发时序**: 打开项目 A → 任一 XLang 功能触发 `getCachedNopVfsPaths(A)` → 静态 Map 写入 `(A → CachedValue)` → 关闭项目 A（IntelliJ dispose Project 及其全部服务）→ 静态 Map 仍强引用 Project 对象 + CachedValue + 全量 vfs 路径集合（`findAllNopVfsPaths` 的扫描结果，可达数万字符串）→ 项目元数据与扫描结果在 IDE 进程内永久滞留；反复 open/close 累积。
- **现状**: 注册点唯一（L174 computeIfAbsent），全插件 grep 无任何 `nopVfsPathCaches.remove` / 项目关闭钩子；IntelliJ 插件的惯例是静态缓存用弱键（`CacheBuilder.weakKeys()`）或挂接项目 `Disposable` 清理，两者均未采用。
- **风险**: 典型 IDEA 插件项目泄漏模式：每次项目关闭泄漏一个 Project 对象图 + 一次全量 VFS 扫描结果；对频繁切换项目的用户造成可观测的 IDE 内存增长。
- **建议**: 改用弱键缓存，或在 `NopProjectService.dispose()`（已是项目级 Disposable，plugin.xml L93 注册）中追加 `nopVfsPathCaches.remove(project)`；二者取一即可闭合配对。
- **失效面枚举**: 全插件 static 可变状态仅两处：本条目与 `NopPluginBundle.INSTANCE`（DynamicBundle，平台所有、应用级、无项目键）——同类问题面收敛为本条目。
- **信心水平**: 确定
- **误报排除**: 不是「CachedValue 自带失效即有界」——失效（invalidation）只影响值是否重算，不影响 Map 条目与键（Project）的强引用存活；也不是平台惯例豁免项——IntelliJ 平台自身对 Project 键静态缓存明确要求弱引用或 Disposable 挂钩，此处属于违背惯例而非惯例本身。
- **复核状态**: 已保留（P2）｜新发现——无 remove 路径独立 grep 复现；跨维度与 C1-004 同站点，移交合并裁定归并防双计，详见复核结论节第 3 条。

---

## 三、维度集外线索附记（CL-201 起）

### [CL-201] JavaScriptWorker FUNC_REINIT 半状态：旧 Context 已关闭而 init 失败后 globals 悬挂指向已关闭 Context

- **文件**: `nop-frontend-support/nop-js/src/main/java/io/nop/js/engine/JavaScriptWorker.java:142-144,318-323`
- **信号**: `invokeFunction` 的 FUNC_REINIT 分支先 `IoHelper.safeClose(this.context)` 再 `this.init()`；若 `runInitScript` 抛出（init 脚本错误），`this.globals` 仍指向旧 Context 的 Value，而旧 Context 已关闭——后续所有 `globals.invokeMember` 调用按 closed-context 异常失败，worker 线程不退出、不回滚。命中 F2「半个成功」/F9「吞异常后状态不一致」。资源侧本身无泄漏（新旧 Context 均会被 close 路径覆盖）。属同单元 C1/C3 维度线索，移交裁定。
- **信心水平**: 很可能
- **状态**: 已复核确认（L142-144/L318-323/L360-373 独立核实，机制可上调为确定；触发前提为 reinit 时 init 脚本失败）｜保留线索，移交 C1/C3 面修复聚类。

### [CL-202] BreakpointManagerImpl.toSourcePath 对全局共享 SourceLocation 的跨线程写入

- **文件**: `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/BreakpointManagerImpl.java:122-138`
- **信号**: `loc.setExternalPath(externalPath)` 写入的 `SourceLocation` 来自编译产物缓存，天然被多线程共享；`externalPath` 字段非 volatile，无同步。写入值收敛相同、实际危害低，但属于对共享不可变对象的「修复式写」，与 C1 发布安全口径相关。移交同单元 C1 裁定。
- **信心水平**: 有趣的猜测
- **状态**: 已复核确认（L122-138/L136 独立核实）｜与 C1 载体 5 表 SourceLocation 行同案：良性幂等惰性写，维持不立案 watch；平台 SourceLocation 契约收紧时与 C1 侧一并复查。

### [CL-203] JavaScriptService 执行器以 -1/-1 参数创建，队列上界未声明

- **文件**: `nop-frontend-support/nop-js/src/main/java/io/nop/js/engine/JavaScriptService.java:40`
- **信号**: `DefaultThreadPoolExecutor.newExecutor("javascript-engine", -1, -1)`——参数语义（-1 → 默认值）与最终队列容量/最大线程数无声明；JS 任务队列 `LinkedBlockingQueue` 本身无界（JavaScriptWorker L54）。若平台默认队列无界，慢 JS 任务堆积即无界积压，属 C4/C8 面。移交裁定。
- **信心水平**: 很可能
- **状态**: 已复核确认（JavaScriptService.java:40 独立核实）｜**已知未修复**：同站点已由 WI 2.1 nop-kernel-concurrency-C4-003（P2，复核保留，F3 缺陷面成员明列本站点）立案，本体处置归该工单，本单元记 cross-ref 防双计；消费侧无界队列面归 CL-002。

### [CL-204] NopProjectService.init 先置 inited 再初始化：初始化中途失败后永久半初始化

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/services/NopProjectService.java:44-58`
- **信号**: `synchronized init()` 在执行 XLangCoreInitializer.initialize()/registerXlib 之前先 `inited = true`；中途抛出则异常传播给当次调用方，但后续所有 `get()` 直接返回半初始化服务（XLang 初始化器未注册/已注册失败状态被固化），无重试与失败标记。命中 F2 半成功族。移交 C1/生命周期面裁定。
- **信心水平**: 很可能
- **状态**: 已复核确认（L48 先于 L52 独立核实，机制确定为代码级事实）｜保留线索，去向生命周期修复面；与 C1-003 同文件，可同 diff 修复。

---

## 四、覆盖范围与盲区自评

- **已读关键文件**：nop-xlang-debugger 全部 9 个主源码文件 + TestRemoteDebugger；nop-idea-plugin debugger 全部 13 文件、resource/services 全部 7 文件、annotator/rename/baseline 命中文件、ProjectFileHelper、NopVirtualFile 面（grep 无资源点）、plugin.xml 服务注册；nop-maven-shaded-plugin 全部 4 主源码文件；nop-api-debugger 全部 11 文件（纯 DTO/接口，逐类核对无资源）；nop-js 引擎 2 文件 + 全模块资源 grep；nop-web 页面导出面（PageProvider/WebPageExporter）；nop-demo 全模块资源 grep + baseline 命中文件。
- **跨单元佐证读取**（仅用于使用方配对判定，不作为发现对象）：SimpleRpcClientFactory/SimpleRpcClient（nop-rpc-simple）、SocketClient/ClientConfig（nop-socket）、Cancellable（nop-commons）、DefaultThreadPoolExecutor/ScheduledExecutor（nop-commons）、RpcInvocationHandler（nop-rpc-core）。
- **盲区自评**：① GraalVM Context 内部资源（Truffle 内存/编译缓存）随 `IoHelper.safeClose(context)` 关闭的完整性未深入（依赖 GraalVM 语义）；② nop-idea-plugin 的 PSI/VFS 包装类（lang/psi、lang/script/psi 共 40+ 文件）按 grep 甄别无资源获取点，未逐文件精读；③ SimpleRpcServer 服务端 channel 的 idleTimeout=0 语义对服务端资源的影响属 nop-network 单元，未展开；④ nop-web 其余子模块（nop-css/nop-ui/nop-web-site 等）为静态资源型，按 grep 甄别未精读。
- **统计**：资源获取点配对盘点 24 项；立案 3（P1×1，P2×2）；CL 线索 4。

---

# C6 深挖第 2 轮（追加）

- **轮次任务**: 双线收尾——①C1-002/C6-001 同族放大：JavaScriptWorker 与 XLangDebugger/DebugConnector 之外的 socket/连接/进程资源站点穷举（api-debugger 面、maven-shaded-plugin 进程面、idea-plugin 其余服务）；②C6-002/C6-003 同族：注册表/缓存「只进不出」形态的第 3 处排查。方法同初审；盲审例外仅限本目录 C1/C6 两份初审文件。
- **编号规则**: 本轮新发现 `C6-R2-001` 起；CL 前缀沿用 CL-2NN。

## 一、socket/连接/进程站点清扫表（第 2 轮穷举）

| 面 | 检查内容 | 判定 |
|---|---|---|
| 全单元 socket/连接 grep | `new Socket|ServerSocket|SocketChannel|HttpURLConnection|URLConnection|openStream|openConnection|HttpClient|new ProcessBuilder|Runtime.exec` 于 nop-dev-tools + nop-frontend-support + nop-demo 全量：仅 demo 两个 Main 读 `maxMemory()`（非进程）与两个测试文件注入平台 `IHttpClient`（测试面） | 生产代码零新增站点——**面闭合**；C6-001 的失效面收敛（SimpleRpcClientFactory 唯一生产调用方 = XLangDebugConnector）维持成立 |
| nop-api-debugger 面 | 11 文件逐类核对：纯 Serializable DTO + 接口，无资源获取点 | **面闭合**（维持初审结论） |
| nop-maven-shaded-plugin 进程面 | 零进程创建（grep 证实）；资源配对 = 初审配对表 #15-17（processResource 入参流归 shade 框架、modifyOutputStream finally safeClose、Writer 不 close 仅 flush 均已核） | **面闭合** |
| idea-plugin 其余服务 | completion/annotator/doc/vfs/execution/icons 面无 socket/流/临时文件获取点；ProjectVirtualFileResource 的流生命周期 = 初审 #11（调用方关闭，合规）；NopVirtualFile 为 PSI 包装无 OS 资源 | **面闭合** |
| demo 临时文件面（**初审 #24 甄别修正**） | 初审 #24 以 `createTempFile` 为 grep 模式判定「无临时文件站点」——**该模式漏检 `ResourceHelper.getTempResource`**；全单元 `getTempResource|createTempFile|File.createTemp|deleteOnExit` 复查命中 ReportController 2 处（L67/L85），初审 #24 的「降口径通过」结论对临时文件面不成立 | **修正立案 → C6-R2-001** |

## 二、注册表/缓存「只进不出」第 3 处排查表（C6-002/C6-003 同族）

| 候选载体 | 检查内容 | 判定 |
|---|---|---|
| 单元内全部 static 集合（idea-plugin + frontend + demo） | 逐条核对 grep 命中：idea-plugin 全部为 effectively immutable（INSTANCE 模式/`Map.of`/`List.of`/`Pattern`/token 表/`PsiClassHelper.primitiveTypeWrapper`）；frontend 仅 `ConditionExprHelper` 两表（类初始化后只读，初审 C1 已核）；demo 命中均为 `_gen/` 生成文件（豁免）或初审已立案面 | 无第 3 处 |
| `IndexHtmlProvider` 的 GlobalCacheRegistry（frontend 唯一使用点） | `@PostConstruct init()` register（L56）/ `@PreDestroy destroy()` unregister（L61）+ `extensionCache.clear()`，bean 生命周期严格配对；`ResourceCacheEntry+CacheEntryManagement` 即平台有界缓存管理机制的标准用法 | 配对成立，非同族 |
| XLangDebuggerRunner L93-94 `getContextManager().addListener` | 会话级 listener，随 DebuggerSession 生命周期销毁 | 惯例校准通过（初审校准声明 #2 覆盖） |
| NopProjectService / NopAppListener / XLangDebuggerInitializer / EvalExprProvider 注册点 | 复核维持初审配对表 #1/#3/#4/#8/#14 判定 | 配对成立 |
| 实例级注册表 | `suspendedThreads`（C6-002 已立案）、`compileCache`/`externalPathCache`（有界 LocalCache 100/1000）、`JavaScriptService.workers`（volatile 数组整体替换，受 workerCount 约束）、maven 转换器 `beansResources`（Mojo 实例私有、受 jar 条目约束、构建期即弃） | 无新增同族 |
| 队列族（F4 关联） | `IBlockingQueue/QueueOverflowPolicy/ArrayBlockingQueue` 全单元 grep：唯一命中 `JavaScriptWorker.taskQueue`（无界 LinkedBlockingQueue） | 已由 CL-002 覆盖，非新增 |

**排查结论**：产品代码中「只进不出」注册表/缓存收敛为已立案的 2 处（C6-002 suspendedThreads、C6-003 ProjectFileHelper.nopVfsPathCaches）+ demo 降口径 1 处（C1-007 RESOURCE_STORE），**无第 3 处**。

## 三、立案（第 2 轮）

### [C6-R2-001]（demo 降口径）ReportController 临时文件生命周期缺口：一次性定时删除无失败处理、无重启清扫；且初审 #24 的临时文件面甄别存在 grep 模式漏检

- **文件**: `nop-demo/nop-spring-report-demo/src/main/java/spring/test/controller/ReportController.java:67, 76, 85, 95, 129-137`
- **证据片段**:
  ```java
  private ResponseEntity<FileSystemResource> downloadFile(IResource resource, String fileName) {
      ResponseEntity<FileSystemResource> response = buildResponse(
              new FileSystemResource(resource.toFile()), ...);

      GlobalExecutors.globalTimer().schedule(() -> {
          resource.delete();                       // L134 —— 返回值被忽略（IResource.delete() 返回 boolean）
          return null;
      }, 5, TimeUnit.MINUTES);                     // 一次性调度，无重试、无对账
      return response;
  }
  ```
  获取点（×2，`genDayRandPath()` 保证每次唯一路径，无并发碰撞）：
  ```java
  IResource resource = ResourceHelper.getTempResource("demo");   // L67 / L85
  ...
  } catch (Exception e) {
      resource.delete();                                          // L76 / L95 失败路径删除，返回值同样被忽略
      throw NopException.adapt(e);
  }
  ```
- **严重程度**: P3（demo 降口径：演示面不按生产标准判级；本案为临时文件结构性缺口，满足初审声明的「仅临时文件/进程/socket 的结构性泄漏立案」门槛）
- **并发场景**: 无数据竞争问题（随机路径、CHM 无共享容器）；缺陷纯在生命周期侧——删除任务运行于平台共享 `globalTimer` 线程，与请求线程、停机时序解耦。
- **触发时序**: T1（HTTP 线程）exportExcel L67 生成临时文件 → downloadFile L133 注册 +5min 单发删除任务 → (a) T2（timer 线程）5min 后执行 `resource.delete()`，文件被下游进程/杀毒/只读句柄占用时 `delete()` 返回 false 被静默忽略，条目永久滞留且无任何重试或对账；(b) 应用在 5min 窗口内重启/停止：timer 任务与文件一同消失，`temp:/demo/` 下孤立文件永久滞留，启动路径无清扫钩子。
- **现状**: 成功路径（延迟删除）与异常路径（立即删除）配对意图完整，但删除结果无核验、重启窗口无兜底；平台 `getTempResource` 基于 `genDayRandPath()`，滞留文件不可枚举回收。
- **风险**: demo 长期演示/压测下 temp 目录缓慢累积孤立文件（结构性、单调、不可自愈）；附带生命周期正确性问题：固定 5min 延迟不感知下载完成，大文件慢速下载会被中途删除（下载截断）。按 demo 降口径不升级。
- **建议**: 最小闭合：`if (!resource.delete()) LOG.warn(...)` 记录失败 + 删除任务改 `scheduleWithFixedDelay` 重试至成功或上限次数；或在 demo 启动钩子中清扫 `temp:/demo/` 命名空间。演示目的下亦可保留现状但注释声明清理边界。
- **失效面枚举**（F3 同族清扫）: 全单元 `getTempResource|createTempFile|File.createTemp|deleteOnExit` 复查仅本类 2 处获取点（L67/L85），共用同一 `downloadFile` 释放点，无例外兄弟；`deleteOnExit` 零命中；maven-shaded-plugin/idea-plugin/frontend 面无临时文件获取点（见清扫表）。
- **信心水平**: 确定（缺口经代码与平台 `IResource.delete()` 契约核实）
- **误报排除**: 非「demo 一切豁免」——初审声明的降口径门槛明确将临时文件列为应立案面；非「有删除路径即配对成立」——删除结果核验缺失与重启窗口缺口是结构性缺口而非风格问题；非「有界即合规」——条目上界仅由演示流量偶然约束。
- **复核状态**: 已保留（P3，demo 降口径维持）｜新发现——获取点 L67/L85、失败删除 L76/L95、一次性 5min 延迟删除 L133-136（返回值忽略）均独立核实，详见复核结论节第 4 条。

### [CL-205]（甄别修正记录，非缺陷）初审配对表 #24 的临时文件 grep 模式漏检

- **信号**: 初审 #24 以 `createTempFile`/`ProcessBuilder`/`Socket` 为甄别模式得出「demo 无临时文件/进程/socket 站点」；本轮复查证实 `ResourceHelper.getTempResource`（demo 2 处）未被该模式覆盖。进程面与 socket 面经本轮更大模式集复核维持闭合结论，仅临时文件面需以 C6-R2-001 修正。此条记录修正本身，供复核轮对账 #24 的现状标注。
- **状态**: 已复核确认（记录性条目，不计入发现统计）——漏检属实（getTempResource 于 ReportController L67/L85 独立核实），进程面/socket 面经复核维持闭合。

## 四、覆盖与终止声明（第 2 轮）

- **本轮新增覆盖**: api-debugger 11 文件复核、maven-shaded-plugin 4 文件进程面 grep、idea-plugin 非调试器面 20+ 扩展点文件实例字段核对、IndexHtmlProvider 配对核对、全单元 socket/临时文件/队列/静态集合四组 grep 复查、`IResource.delete`/`getTempResource`/`genDayRandPath` 平台契约核实（仅作语义佐证，不作发现对象）。
- **清扫终止信号**: socket/连接/进程面全单元穷举闭合（生产代码零新增站点）；「只进不出」注册表/缓存第 3 处排查闭合（收敛为已立案 2 处 + demo 降口径 1 处）；临时文件面甄别修正后唯一站点已立案。
- **轮次终止信号**: 初审 3 条 + 本轮 1 条之后，继续第 3 轮的预期增量为零——建议 C6 维度深挖迭代在此终止，进入独立复核阶段。全部条目（C6-001~003、C6-R2-001、CL-201~205）复核状态：未复核。
- **统计**：本轮清扫表 2 张（socket/连接/进程 5 面 + 只进不出 6 项）；立案 1（P3×1）+ 记录性修正 1（CL-205）；累计立案 4（P1×1，P2×2，P3×1）。

---

# C6 维度复核结论（独立复核）

- **复核方式**: 独立复核子代理对 C6 全部 4 条发现 + 5 条 CL 线索逐条重读源码核实（含跨模块佐证面 SimpleRpcClientFactory/SimpleRpcClient/SocketClient/ClientConfig/RpcInvocationHandler/DefaultThreadPoolExecutor 的独立读取）；以下 file:line 均为复核者独立 grep/读取所得，非转抄初审。现状标注经独立查证（同 C1 复核节）：2026-09 G 系列对四模块零提及、cra-index 无 WI 6.5 条目、campaign 既有提及均为设施侧不同缺陷面。
- **P0 候选**: 无。C6-001 为会话停止/断开路径的泄漏，非「正常成功路径即泄漏」的生产主路径 P0 形态，且载体为 IDE 插件工具面，P1 恰当。

## 逐条核实结果

1. **C6-001（P1）成立，保留（附触发时序精确化注记）**。独立核实链：`XLangDebugConnector.java` readTimeout=0（L28）+ reconnectPolicy（L31-36）、destroy 仅 `clientFactory.destroy()`（L49-51）、connect 不持有 proxy（L53-55）；`XLangDebugProcess.java` stop→cleanup.cancel（L255）、connect→appendOnCancelTask(connector::destroy)（L131-132）；`SimpleRpcClientFactory.java` newInstance L156-172 创建 SocketClient + connect（L169）+ executeRecv（L170）但**不登记任何已建 client**，destroy L136-146 仅销毁 ownExecutor/ownTimer；`DefaultThreadPoolExecutor.destroy` = `executor.shutdown()`（L91-93，不中断在途任务）；`SocketClient.recv` L197-233 阻塞于 `is.read()`（L171→BinaryCommand.readPacketFromStream），`SocketClient.close` L274-277 在插件主源码零调用；`SimpleRpcClient.close` L112-115 与 `RpcInvocationHandler` proxy_destroy → `DestroyHelper.safeDestroy` 分发链核实存在且插件不可达；服务端 idleTimeout=0（`XLangDebuggerInitializer.java:61`）核实；修复参照 `TestRemoteDebugger.tearDown` L142-146（clientFactory.destroy / server.stop / DestroyHelper.safeDestroy(debugger)）核实。**精确化注记**：「debuggee 被杀 → fd 停留 CLOSE_WAIT 至 IDE 退出」子场景不精确——`ClientConfig.autoReconnect` 默认 true（ClientConfig.java:28）且连接器已设 reconnectPolicy，debuggee 死亡后 recv 循环退出并经 `SocketClient.reconnect()`（L139-142）→ `cleanup()`（L279-294）关闭旧 fd，随后对死进程进入**无限重连重试循环**（执行器已 shutdown 语境下的僵尸重试），后果等价劣化但机制不同：**核心泄漏主张不受影响**——Disconnect 场景（debuggee 存活）下连接保持 ESTABLISHED、recv 线程被钉死、双侧无空闲回收（readTimeout=0 + idleTimeout=0）全部核实成立。维持 P1。
2. **C6-002（P2）成立，保留**。独立核实：`XLangDebugger.java` cleanupThread L72-73、构造器 L80 `schedule(this::cleanup, 30, TimeUnit.SECONDS)` 确为一次性（非 scheduleAtFixedRate）；cleanup L94-103 按 isAlive 删除；put 点唯一在 makeSuspendedThread L567；条目复用面（stepOver/stepOut 依赖 getSuspendedThread L401/L424）核实，resume 时不能简单移除的主张成立；close L89-91 clear 兜底核实。条目载荷含最后挂起点 EvalRuntime 对象图（SuspendedThread 持 rt/lastBreakLocation，独立读 SuspendedThread.java L34-37 证实）。P2（宿主线程池化时有界但载荷重；thread-per-request 升 P1 的条件注记合理）恰当。
3. **C6-003（P2）成立，保留**。独立核实：`ProjectFileHelper.java:49` 静态 CHM、L174 computeIfAbsent 唯一写入点、全插件 grep 无 remove/无项目关闭钩子（独立复现）；CachedValue 失效不影响键强引用的论证成立。**跨维度重复登记**：与 C1-004 为同站点同缺陷，建议合并裁定归并为单一工单（登记面互补：C1 侧记并发/泄漏双面，C6 侧记生命周期面），防双计。
4. **C6-R2-001（P3，demo 降口径）成立，保留**。独立核实：`ReportController.java` 获取点 L67/L85（`ResourceHelper.getTempResource("demo")`）、失败路径删除 L76/L95（boolean 返回值忽略）、`downloadFile` L129-139 的 globalTimer 一次性 5min 延迟删除 L133-136（`resource.delete()` 返回值忽略）。初审 #24 的 grep 模式漏检属实（见 CL-205）。降口径 P3 恰当。

## 抽样安全判定失败数

抽样 6 项配对/清扫判定复核：① 配对 #1 cleanupThread 构造/close shutdown 配对（L80/L86）成立；② 配对 #3 SimpleRpcServer start/stop 配对（initialize L70 / destroy L158）成立；③ 配对 #4 EvalExprProvider register/restore 严格配对（L77/L156）成立；④ 配对 #18 GraalVM Context 三路径关闭（close 任务 L115-118、run finally L312、FUNC_REINIT L143）成立；⑤ 「只进不出」第 3 处排查中 IndexHtmlProvider GlobalCacheRegistry register/unregister 配对（L54-56/L59-62 + extensionCache.clear）独立核实成立；⑥ idea-plugin static 集合清扫结论成立（grep 独立复现：除 C6-003 立案面外均为 Map.of/INSTANCE/Pattern 等不可变形态）。**安全判定失败数：0**（#24 临时文件面甄别漏检已由初审 R2 自行修正立案，不计入复核失败）。

## CL 裁定清单

- **CL-201 确认（保留线索）**：`JavaScriptWorker.java` L142-144 FUNC_REINIT 先 safeClose 旧 context 再 init、init L318-323 中 runInitScript L360-373 失败时 `this.globals` 仍指旧（已关闭）Context 的 Value——代码级可证（信心水平可由「很可能」上调为「确定」，触发前提为 reinit 时 init 脚本失败）；worker 不退出不回滚，后续 invokeMember 按 closed-context 失败。F2/F9 形态，资源侧无泄漏（新旧 context 均被 close 路径覆盖）的判断核实成立。去向：移交 C1/C3 面修复聚类。
- **CL-202 确认（维持不立案，watch）**：`BreakpointManagerImpl.java` toSourcePath L122-138、L136 `loc.setExternalPath(...)` 独立核实；与 C1 载体 5 表 SourceLocation 行为同案判定（幂等惰性写、值确定、良性），两处应随平台 SourceLocation 契约收紧一并复查。
- **CL-203 确认（cross-ref 防双计）**：`JavaScriptService.java:40` `newExecutor("javascript-engine", -1, -1)` 独立核实。**已知未修复**：同站点已被 WI 2.1 `nop-kernel-concurrency-C4-003`（P2，复核保留，F3 缺陷面成员明列 `JavaScriptService.java:40 -1,-1 静默 cached`）立案，本体处置归 WI 2.1 C4-003；本单元消费侧无界队列面已由 CL-002 覆盖。
- **CL-204 确认（保留线索）**：`NopProjectService.java` L48 `inited = true` 先于 L52 `xlang.initialize()`，中途失败后 inited 恒真、get() 永久返回半初始化服务，无重试/失败标记——独立核实成立。F2 半初始化形态，去向：生命周期修复面（与 C1-003 同文件，可同 diff 修复）。
- **CL-205 确认（记录性条目）**：初审 #24 以 `createTempFile`/`ProcessBuilder`/`Socket` 为甄别模式、漏检 `ResourceHelper.getTempResource`（ReportController L67/L85 独立核实）属实；进程面/socket 面经独立 grep 维持闭合。核对属实，不计入发现统计。

## 现状标注汇总

- 4 条发现（C6-001~003、C6-R2-001）经查证**全部为新发现**；无历史编号可引用、无修复提交。
- 跨单元 cross-ref 1 处：CL-203 → WI 2.1 nop-kernel-concurrency-C4-003（已知未修复，P2）；其余无 campaign 台账重复。
- 跨维度同站点 1 组：C6-003 ≡ C1-004，移交合并裁定归并。
- 5 条 CL 线索全部确认（CL-203 带 cross-ref、CL-202 维持 watch、CL-205 记录性条目不计入统计）。
