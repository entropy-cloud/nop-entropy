# C1 共享可变状态与可见性 — nop-ai-toolkit-tools-shell（WI 3.2）

- 审计人：C1 维度盲审初审员（单批次）
- 日期：2026-10-20
- 现状标注：初审（未复核），全部为盲审新发现，未对照任何历史审计记录

## 审计范围与口径

**覆盖声明（单批次全源码根）**：本报告单批次覆盖以下三个源码根的全部手写 Java 文件（不含 `_` 前缀生成文件），逐文件审读：

| ROOT | 规模 | 重点类 |
|---|---|---|
| `nop-ai/nop-ai-toolkit/src/main/java` | 74f / 7,330 | ToolManagerImpl、BashExecutor、tools/sandbox/（Docker/Host/IBashSandbox + BashExecutionTracker）、mavenvfs（DeltaVirtualFileSystem/DeltaWorkspaceReader/VfsMavenCli）、fs/LocalToolFileSystem、executor/DefaultToolExecutorProvider、tools/*Executor |
| `nop-ai/nop-ai-tools/src/main/java` | 23f / 2,088 | ThoughtStorage（ReentrantLock）、SequentialThinkingBizModel、FileToolBizModel、AiFileTool、DslToolImpl、AiToolsHelper |
| `nop-ai/nop-ai-shell/src/main/java` | 44f / 4,508 | ShellCommandExecutor、ShellCommandRegistry、BlockingQueueShellInput/Output、AbstractShellInput、BashLexer/BashSyntaxParser、DefaultShellExecutionContext、io/* |

**口径纪律声明**：

1. 手写口径：`_` 前缀文件不作为发现对象。唯一例外是 grep 引用 `_gen/_AiToolModel.java:51` 仅用于确认 `concurrencySafe` 模型属性默认值为 `false`（证据引用，非发现对象）。
2. 本单元形态为"工具执行 + 进程沙箱 + shell 循环"，属请求触达面，**启动期单线程降档一般不适用**。对 setter 注入的启动装配字段（`BashExecutor.sandbox`、`GraphqlQueryExecutor/HttpRequestExecutor.httpClient`、`SearchEngineExecutor.searchEngine`、`ToolManagerImpl.executorProvider/interceptors`、`FileToolBizModel.baseDir`、`AiFileTool.baseDir`、`SequentialThinkingBizModel.storage`）逐站点论证降档：均为 IoC 装配期单线程写入、请求期只读、无运行时 setter 调用路径（全仓 grep 验证），按误报校准第 3 条不立项。
3. ThreadLocal：基线 C1c 节为空；全根 grep `ThreadLocal|SimpleDateFormat|new Random()` 均无命中，本维度无该类载体。
4. 每个发现均给出具体触发时序（T1/T2）；写不出时序的理论竞态已标注并降 P2。
5. 平台设施（nop-commons 的 GlobalExecutors/AntPathMatcher 等）只审使用方式，不重审其内部实现。

**共享可变状态清单表（五类载体盘点摘要）**：

| 载体 | 字段/对象 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| 实例字段（单例） | `ToolManagerImpl.pathQueues` (CHM) | 各请求线程（compute） | 各请求线程 | CHM.compute 原子 | 复合语义有缺陷 → C1-001 |
| 实例字段（会话） | `ShellCommandExecutor.exportedEnv/currentWorkingDir/workingDirInitialized` | 前台调用线程 + 池线程 + 后台 job 线程 | 同左 | **无** | 数据竞争 → C1-002 |
| 实例字段（单例） | `DefaultToolExecutorProvider.executors`（HashMap） | 装配/registerTool | 每次分发 getExecutor | **无** | 守卫缺失 → C1-006 |
| 实例字段（单例） | `ShellCommandRegistry.commands/aliases`（LinkedHashMap/HashMap） | registerCommand | 每条命令 findCommand | **无** | 守卫缺失 → C1-006 |
| static 集合 | `UpdateTodosExecutor.todoLists`（static CHM） | 各请求线程 | 各请求线程 | CHM 单操作原子；clear/write 复合无 | → C1-004 |
| 实例字段（每会话 bash 执行器） | `BashExecutor.sandbox` | 启动装配 | 请求线程 | 无（启动期降档） | 不立项（口径 2） |
| 实例字段（volatile） | `ShellCommandExecutor.closed` | close() | **无读者** | volatile | 死标志 → C1-008 |
| 实例字段 | `BashExecutionTracker.inFlight`（CHM） | sandbox 执行/取消线程 | 心跳快照线程 | CHM + AtomicReference 发布不可变副本 | 设计正确，不立项 |
| 实例字段 | `ThoughtStorage.lock`（ReentrantLock） | 单例 bean 内全部操作 | 同左 | ReentrantLock 全操作互斥 + 原子换名写 | 设计正确，不立项 |
| 文件系统即共享状态 | `DeltaVirtualFileSystem` delta 目录 | installArtifact/getOutputStream | findArtifact/getInputStream | **无**（非原子写） | → C1-005 |
| 实例字段（per-invocation） | BashLexer/BashSyntaxParser 内部游标 | 构造线程 | 同线程 | 每次 execute 新建 | 无共享，不立项 |

---

## 发现

### [C1-001] ToolManagerImpl 路径串行队列：executeNow 在 CHM.compute 锁内同步执行；同步抛出时手工 result Future 永不完成

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java:101-123`（配套 `:232-246`）
- **证据片段**:
  ```java
  CompletableFuture<AiToolCallResult> result = new CompletableFuture<>();
  pathQueues.compute(pathKey, (k, tail) -> {
      CompletableFuture<Void> trigger = (tail == null
              ? CompletableFuture.completedFuture(null)
              : tail).handle((v, e) -> null);
      return trigger.thenCompose(x -> executeNow.get()          // ← 已完成的 trigger：fn 在 compute 调用线程内同步执行
              .whenComplete((r, err) -> { ... result.complete(r); })
              .thenApply(r -> (Void) null));
  });
  return result;                                                 // ← 同步抛出时永不 complete
  ```
- **严重程度**: P1
- **并发场景**: HTTP/引擎 worker 线程并发调用 `callTool`；同一 manager 实例上所有非 concurrency-safe（当前全部工具默认即 unsafe，见 `_gen/_AiToolModel.java:51` boolean 默认 false 且各 tool.xml 未覆写）且带 path 参数的工具调用都经过此队列。`executeNow.get()` 内含 `context.getExecutor().submit(...)`（如 `BashExecutor.executeAsync:104`）。
- **触发时序**: 三个子缺陷同一代码块：
  (a) **锁内执行**：T1 是某 pathKey 的首个调用（tail==null），`trigger` 为已完成 future，`thenCompose` 的函数在 T1 的 compute 调用线程内同步执行 → `executeNow.get()` 即 `IThreadPoolExecutor.submit` 在持有 pathKey 所在 CHM bin 锁的状态下运行。若线程池配置 caller-runs 溢出策略或同步执行器，则**整个工具执行**（bash 命令可达 wallSeconds=60s）都在 bin 锁内 → 所有 hash 到同一 bin 的其他 path 的 compute 调用全部阻塞。前驱已完成（已结束）的后续每次调用同样在锁内同步执行。
  (b) **重入**：T1 在锁内同步执行的工具分发链中再次对同一 pathKey 调用 callTool（如经 call-agent 嵌套派发）→ CHM.compute 递归更新 → 抛 `IllegalStateException: Recursive update`（或自死锁）。
  (c) **悬挂 future**：`executeNow.get()` 同步抛出（`context.getExecutor()` 为 null 时 NPE——`ToolExecuteContext.Builder.executor` 无默认值；或池队列满时 `RejectedExecutionException`）→ 异常只使 thenCompose 返回的链异常完成（后续调用经 `handle` 恢复，队列不中毒），但返回给调用方的手工 `result` future **没有任何代码路径完成它** → 该调用永久悬挂；`executeParallel` 的 `allOf().join()` 永不返回，且 `submitBounded:244-245` 挂在其上的 `semaphore.release()` 永不执行（信号量为批次局部，泄漏限于本批，但悬挂本身是永久的、静默的）。
- **现状**: 队列原子性本身正确（compute 内仅建链，前驱在途时 fn 由前驱完成线程执行）；缺陷在"已完成的 trigger 在锁内同步执行 + 手工 future 的完成契约依赖 whenComplete 必然被注册成功"。
- **风险**: 队列满/负载上升时（c）静默永久悬挂且无超时、无日志——调用方（ReAct 引擎）整批工具调用卡死；(a) 在同步执行器配置下退化为全 bin 串行；(b) 嵌套派发即崩。
- **建议**: (1) 将 `executeNow.get()` 移出 compute——compute 内只做 `tail` 的取换（put 哨兵链），fn 挂到锁外；(2) `result` 的完成改为挂在整条链的 `whenComplete` 外层 + 对 `executeNow.get()` 本身用 try/catch 包裹并 `result.completeExceptionally`；(3) 优先复用平台 `io.nop.commons.concurrent` 设施（AsyncJoinType / CycleSynchronizer 语义）或为 `result` 挂 `orTimeout` 兜底。
- **失效面枚举**（F3）：手工创建并在 lambda 内完成 future 的形态全根仅此一处；同文件兄弟 `submitBounded`（release 依赖调用方 future 完成，受本缺陷放大）、`executeSequential:248-266`（纯 CF 链，无手工 future，安全）。两个沙箱的 `tracker.register`（Docker:116 / Host:102）有 finally 注销，不受影响。
- **信心水平**: 确定（(a)(c) 为 CompletableFuture/CHM 规范行为推演，代码路径完整可复核）
- **误报排除**: 非"原语使用本身报问题"——CHM.compute 原子性没问题，报的是复合操作（锁内执行业务分发 + 手工 future 完成契约有泄漏路径），且给出了具体执行交错。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现——回码核验通过（ToolManagerImpl.java:101-123/232-246 与证据一致）；生产装配面补强：nop-cli CliCallToolsCommand:75 注入 SyncThreadPoolExecutor.INSTANCE（submit 经 ContinuationExecutor 调用线程内联 → 锁内全执行的实证装配）、ChatBiToolExecuteContext.getExecutor() 返回 null（生产 NPE 挂死面）、ToolExecuteContext.Builder.executor 无默认值（:87,120）；(b) 重入子项条件化（缺省异步池下嵌套发生在池线程，需同步执行器装配方可达）。同站点跨维：与 C4-005 同一代码块（C4 复核并行中，悬挂子面重叠，合并归裁定阶段）；台账查重：WI 3.1 C4-006 曾以本类 executeNow 内联语义作子项复核依据但未立案本站点，零重复。

### [C1-002] ShellCommandExecutor 会话状态（exportedEnv/currentWorkingDir）跨线程无同步：& 后台 job 与前台命令并发读写同一 HashMap

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:45-47, 80-83, 319-348, 350-370, 545-585`
- **证据片段**:
  ```java
  private Map<String, String> exportedEnv = new HashMap<>();   // L45 非线程安全、非 volatile
  private String currentWorkingDir = "/";                      // L46
  private boolean workingDirInitialized = false;               // L47
  ...
  // L551 buildEnvironment（可运行在池线程/后台job线程）：
  if (envVar.type() == EnvVar.Type.EXPORT) {
      exportedEnv.put(envVar.name(), envVar.value());          // 无锁写
  }
  // L547 前台/其他线程同时：env.putAll(exportedEnv);           // 迭代期间并发 put → CME/撕裂
  ```
- **严重程度**: P1
- **并发场景**: 同一 ShellCommandExecutor 实例（bash 会话模型，实例字段跨 execute 调用保持）上：(1) `&` 后台 job——`executeBackground:363-365` 经 `IBackgroundJobSink.submit` 把 `executeExpression(inner).join()` 交给 sink 线程，job 体执行 SimpleCommand 时在 sink 线程上跑 `buildEnvironment`/`updateContextFromResult` 写 exportedEnv/currentWorkingDir，与前台继续执行的命令并发；(2) pipeline 阶段在 `executor` 池线程上执行（`executePipeline:263`）同样写这两个字段；(3) 相继两次 execute 由不同 worker 线程执行时无任何 happens-before 边。
- **触发时序**: T1（前台）执行 `echo a & export FOO=1`：`&` 提交后台 job 后立即继续；T2（sink 线程）执行 job 体；T1 随后执行下一条命令 `env`（内置）→ `buildEnvironment:547` `env.putAll(exportedEnv)` 迭代 exportedEnv 的同时 T2 正在 `exportedEnv.put(...)`（job 体内 export 或 updateContextFromResult:564）→ ConcurrentModificationException 或撕裂快照。另一序列：T1 执行 `(cd /tmp)` 子 shell → L340/L345 在 T1 线程做 `this.exportedEnv = new HashMap<>()` 与恢复赋值；与后台 job 线程的 `exportedEnv.put` 交叠 → job 的 export 被 restore 整体抹掉（丢失更新）；`currentWorkingDir` 同理（T2 的 cd 结果被 T1 的快照恢复复活/覆盖）。`workingDirInitialized:80-83` 的 check-then-act 使两次首次执行可能各写一次（幂等、后果轻）。
- **现状**: 会话状态为裸实例字段，无锁、无 volatile、无单飞守卫（F10：bash 会话"单命令在飞"契约无运行时守卫）；唯一 volatile 的 `backgroundJobSink`（L56）不覆盖状态字段。
- **风险**: 导出变量丢失/环境撕裂、cd 目录漂移——对 AI 会话语义是静默违约；HashMap 并发 put 可致 bin 链损坏（丢条目甚至死循环）；组/子 shell 的快照恢复与后台 job 交叠时恢复到陈旧状态。
- **建议**: (1) 最小修复：exportedEnv 换 `ConcurrentHashMap`，currentWorkingDir 声明 volatile，组/子 shell 快照恢复改为对字段赋值前持同一锁（`synchronized(stateLock)`）；(2) 结构性修复：按"bash 会话单命令在飞"契约在 `execute()` 入口加运行时守卫（在飞标记 + fail-fast），或将会话状态收敛到 `IShellCommandExecutionContext` 的会话级对象并经平台 `IResourceLockManager` 串行化；(3) `&` job 与前台共享会话状态是否合法应在文档声明。
- **失效面枚举**（F3）：写 exportedEnv 的站点全清单——`buildEnvironment:551`（EXPORT put，前台/池/后台线程可达）、`updateContextFromResult:564`（前台线程）、`executeGroup:326`、`executeSubshell:340,345`（快照替换，前台线程）；写 currentWorkingDir——`execute:81`、`updateContextFromResult:579,581`、`executeGroup:327`、`executeSubshell:341,346`。全部站点均无同步，属同一缺陷族；无任何站点已修复。
- **信心水平**: 确定（并发路径 `&` job 为 WI25 显式新增且无状态隔离）
- **误报排除**: 非无状态单例误报——该类以实例字段承载跨调用会话状态是其文档化语义（L78-79 注释）；亦非"synchronized 使用本身"误报——报的是复合读改写与快照恢复的交错。
- **复核状态**: 未复核
- 复核状态: 保留但降级(P2)｜新发现——证据全部回码核实（字段 :45-47、buildEnvironment :545-555、updateContextFromResult :559-585、executeGroup/executeSubshell 快照恢复、executeBackground :363-371 均无同步）；降档依据（零生产实例化判例）：nop-ai-shell 全仓零生产消费（src/main 无任何模块 import `io.nop.ai.shell`，`new ShellCommandExecutor` 仅测试）且主触发链 `&` job 依赖 IBackgroundJobSink 仓内零接线（setBackgroundJobSink 生产调用方 0，shipped default = fail-loud 126）——按 C7-005(2.5)/C1-005(3.1) 休眠面条件化降档判例 P1→P2，条件="嵌入方以多线程驱动同一会话或接线 IBackgroundJobSink 后恢复 P1 级"；仓内不依赖接线的次级向量保留（pipeline 阶段为 SimpleCommand，`export FOO=1` 形态阶段经 BashSyntaxParser:510 判 EXPORT 后在池线程写 exportedEnv，与相邻阶段 putAll 迭代交叠）。

### [C1-003] BlockingQueueShellOutput.close() 在队列满时静默丢弃 EOF → 消费者 readAllText 永久阻塞

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/BlockingQueueShellOutput.java:42-47`（同族 `BlockingQueueShellInput.java:50-55`）
- **证据片段**:
  ```java
  @Override
  public void close() {
      if (!closed) {
          closed = true;
          queue.offer(ShellChunk.eof());   // offer 返回值被忽略；队列满(1024)时 EOF 丢失
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 生产者（命令执行线程 / pipeline 阶段池线程）与消费者（`ShellCommandExecutor.collectOutput:627-633` 经 `asInput().readAllText()` 阻塞读）分属不同线程；队列容量固定 1024 chunk（L15）。
- **触发时序**: 命令产出 >1024 个 chunk 且消费者相对较慢：T1（生产者）写入最后一个 chunk 成功（队列 size==1024 满）→ T1 立即执行 close() → `offer(EOF)` 因队满返回 false（被忽略）→ T2（消费者）`readAllText` 排空 1024 个 chunk 后 `queue.take()` 永久阻塞——EOF 已丢失，`executePipeline:280-283` 的 `thenApply(collectOutput(lastOutput))` 永不完成，整条命令挂死。消费速度快于生产时 offer 成功，故为竞态而非必然。
- **现状**: EOF 投递用忽略返回值的 `offer` 而非阻塞 `put`/超时重试；`close()` 由生产者线程调用，本可安全阻塞。
- **风险**: 流式消费路径一次性永久挂死（无超时）；当前仓内内建命令（echo/ls/cd）产出有界，触发需大输出命令或外接命令（ExternalCommandAdapter 当前 UOE，为设计扩展点），但该类是库公共 API，嵌入式用法必然暴露。
- **建议**: close() 内对 EOF 用 `queue.put`（生产者线程阻塞语义正确）或 `offer` 失败时记录失败并在 `read()` 侧以 `closed` 标志兜底判空；同时修复兄弟站点。
- **失效面枚举**（F3）：全根 `offer(` 仅两处——`BlockingQueueShellOutput.close:45`（本条）与 `BlockingQueueShellInput.sendEof:53`（同一缺陷：队满丢 EOF，读者 `read():27` 永久 take）。同族第三处 `BlockingQueueShellInput.put:39-44` 的 `eofReceived` check-then-act 与 `BlockingQueueShellOutput.write:27-30` 的 `closed` check-then-act：close 与 write 并发时 chunk 落在 EOF 之后（消费者丢尾部数据，chunk 滞留队列）——同一修复批内一并处理。
- **信心水平**: 确定（时序具体；当前内建命令产出有界，故降 P2）
- **误报排除**: 非"有界队列配置"误报——问题不在有界，而在 close 路径的投递失败被静默吞掉且无兜底读路径。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验通过（BlockingQueueShellOutput.close `offer` 忽略返回值 :42-47、write 用阻塞 put :27-30 不对称、BlockingQueueShellInput.sendEof :50-55 同族、collectOutput:627-633 消费点）。同站点跨维：与 C8-002（P1）为同一文件同一机制（EOF offer 丢弃），合并归裁定阶段——C8 侧触发链分析（单命令输出恰 1024 chunk 时消费者尚未启动 → close 时队列必满 → 确定性丢 EOF）强于本条"竞态而非必然"口径，建议终裁采用 C8 侧判级；本条另附休眠面条件（nop-ai-shell 全仓零生产实例化，grep 证据见 C1-002 复核行），若按 C1 侧独立判级维持 P2。

### [C1-004] UpdateTodosExecutor：static 进程级 todoLists 的 clear/write 复合操作竞态（同会话并行调用丢失更新）

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/UpdateTodosExecutor.java:25, 93-133`
- **证据片段**:
  ```java
  private static final Map<String, List<TodoItem>> todoLists = new ConcurrentHashMap<>(); // 进程全局、static
  ...
  if (newTodos.isEmpty()) {
      todoLists.remove(listKey);            // L100 clear 路径
  } else {
      todoLists.put(listKey, newTodos);     // L103 write 路径 —— 与 remove 无原子关系
  }
  ```
- **严重程度**: P2
- **并发场景**: 同一 session 的两条 update-todos 调用经 `ToolManagerImpl.executeParallel` 并行派发（update-todos 无 path 参数 → `ToolPathAffinity.extract` 返回 null → 即使按 unsafe 处理也不入 pathQueues 串行，见 ToolManagerImpl:101-121）；两条调用在不同 worker 线程执行。
- **触发时序**: T1（write 空列表=清除会话）与 T2（write 新列表）并行：T2 执行 `todoLists.put(listKey, newTodos)`（L103）→ T1 执行 `todoLists.remove(listKey)`（L100）→ 终态为空：T2 报告 "Created N todo items" 成功，但列表实际已被清除——写丢失。反向交错则 clear 后立即被旧写覆盖。此外 L95 `getOrDefault` 取 oldTodos 与 L103 put 之间无原子性，diff 摘要（L115-131）可基于撕裂的 old/new 对比产生错误 "Updated:" 报告。
- **现状**: CHM 保证单键操作原子，但 clear 与 write 是两个独立的复合决策，无按 listKey 的串行化，也无 CAS 式条件更新。
- **风险**: agent 会话 todo 状态静默错乱（该状态直接驱动模型后续计划行为）；static 生命周期使错乱跨请求可见。另：条目仅在显式写空时移除，会话结束不清理 → 跨会话无界增长（F4，交叠 CL-001 同族，量级小：每会话一个条目）。
- **建议**: 按 listKey 串行化（复用本模块 ToolManagerImpl 的 per-key tail-chain 模式或平台 `IResourceLockManager`），或把 remove/put 收敛为 `compute(listKey, ...)` 单次原子决策；会话关闭钩子里清理条目。
- **失效面枚举**（F3）：static 可变集合全根清单——`UpdateTodosExecutor.todoLists`（本条）、`SsrfAddressGuard.BLOCKED_HOSTS`/`BashExecutor.DANGEROUS_ENV_VARS`/`SkillExecutor.DESCRIPTION_FILE_NAMES`/`DefaultCommandChecker.BLOCKED_COMMANDS/SHELL_INTERPRETERS`（均 `Set.of/List.of` 不可变，安全）、`ToolManagerImpl.pathQueues`（CHM，单操作原子，生命周期问题见 CL-001）。同类"读-改-写无原子性"兄弟：`TodoItem` 字段为包级可变且存入共享列表后可被 read 线程读取（当前写侧总是整体替换新 List，读侧不修改，安全）。
- **信心水平**: 确定
- **误报排除**: 非"CHM 使用本身"误报——CHM 单操作原子无问题，报的是跨键复合操作缺原子性；亦非会话隔离误报——键为 sessionId，同会话并行驶入同一键。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验通过（static CHM :25、handleWrite getOrDefault:95/remove:100/put:103 复合无原子性）。并发向量修正：executeParallel 无生产调用方（ToolManagerImpl.java:95-97 注释自认），实际生产向量为 agent 引擎每轮工具 fan-out（nop-ai-agent AgentToolDispatcher 循环逐 callTool 提交 future）——update-todos 无 path 键不入 pathQueues，同会话两调用可并行，向量成立且生产接线（ai-toolkit-defaults.beans.xml 注册 UpdateTodosExecutor bean）。F4 面（会话结束条目不清理）与 CL-001（pathQueues 完成尾不驱逐）同族、与 WI 3.1 C5-015 MA-03 F4 引擎终态清理族同族不同站点——台账关联，不重复立项。

### [C1-005] DeltaVirtualFileSystem delta 目录写入非原子（TRUNCATE_EXISTING/直接 copy），并发读者可见撕裂内容 —— 同仓库原子替换模式漏修的兄弟站点

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/mavenvfs/vfs/DeltaVirtualFileSystem.java:149-167, 176-195`
- **证据片段**:
  ```java
  // L164-166 getOutputStream：就地截断写
  return Files.newOutputStream(deltaFile.toPath(),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
  // L190-191 copyToVirtual：就地覆盖复制
  Files.copy(sourceFile.toPath(), deltaFile.toPath(),
          StandardCopyOption.REPLACE_EXISTING);
  ```
- **严重程度**: P2
- **并发场景**: mavenvfs 是按 Maven 仓库语义共享的虚拟文件系统（base 只读 + delta 可写）：T1 为安装/更新 artifact 的写线程（`DeltaWorkspaceReader.installArtifact:168` → `copyToVirtual`，或任意 `getOutputStream` 调用方），T2 为解析 artifact 的读线程（`findArtifact:91` → `vfs.getFile` → `getInputStream`）。VFS 实例本身无状态（final 字段），共享可变状态即 delta 目录文件内容，无任何锁。
- **触发时序**: T1 `getOutputStream` 打开目标 → TRUNCATE_EXISTING 将文件截为 0 → T2 此时 `getFile` 命中 delta 文件（`exists && isFile` 通过）→ `newInputStream` 读到空/半截内容 → T2 将截断的 jar/pom 当作有效 artifact 消费。`copyToVirtual` 的 REPLACE_EXISTING 在部分文件系统上是非原子替换，读侧同样可见中间态。
- **现状**: 同仓库同缺陷族已有两处修复为"临时文件 + ATOMIC_MOVE 原子替换"：`LocalToolFileSystem.atomicReplace:191-211`、`ThoughtStorage.atomicWriteText:214-234`（注释明确"partial write or crash never truncates the previous content"）；本站点为漏修的兄弟，F3 同族清扫口径下应同批修复。
- **风险**: Maven 增量仓库场景下并发"安装 + 解析"读回撕裂 artifact，构建结果不可诊断地损坏；断电/kill 后 delta 文件可能永久为空（覆盖即丢）。
- **建议**: 照抄 `ThoughtStorage.atomicWriteText` 模式（同目录临时文件 + `ATOMIC_MOVE`，`AtomicMoveNotSupportedException` 降级 REPLACE_EXISTING），`getOutputStream` 改为返回临时文件流 + close 时原子换名，或直接提供 `writeAtomically(path, content)` 语义并收敛调用方。
- **失效面枚举**（F3）：三根内非原子落盘站点全清单——`DeltaVirtualFileSystem.getOutputStream:164-166`（本条，未修）、`DeltaVirtualFileSystem.copyToVirtual:190-191`（本条，未修）、`LocalToolFileSystem.atomicReplace`（已修）、`ThoughtStorage.atomicWriteText`（已修）；`DslToolImpl.saveDslFile:81` 与 `AiFileTool` 保存路径经平台 `ResourceHelper.writeText`（非本模块代码，平台 helper 是否原子未审，登记为待核兄弟）。
- **信心水平**: 很可能（撕裂读时序具体；实际暴露取决于 delta 目录的并发读写频率）
- **误报排除**: 非"文件系统级竞态皆报"——同仓库已确立原子替换为该缺陷族的标准修复（两处在案），本站点缺同一守卫且读路径真实并发可达。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验通过：非原子写两站点（getOutputStream TRUNCATE_EXISTING / copyToVirtual REPLACE_EXISTING）、已修兄弟两处逐一核实（LocalToolFileSystem.atomicReplace:191-211、ThoughtStorage.atomicWriteText:214-234，注释原文与引用一致）；R2 扩展面落定核实（DslToolImpl:81 与 AiFileTool 3 写点 → ResourceHelper.writeText:718-728 就地 Writer 直写，平台 helper 未重审、使用面成立）。降档口径核对：核心 mavenvfs 子面全仓零生产用户（DeltaVirtualFileSystem/DeltaWorkspaceReader/VfsMavenCli 在 mavenvfs 包外 src/main 零引用，VfsMavenCli.execute 为 stub）——该子面按休眠面判例条件化；但扩展面为生产接线请求可达（FileToolBizModel @BizModel("FileTool") 且 bean 已注册 → DslToolImpl.saveDslFile），整体维持 P2 不降。

### [C1-006] 两个公共注册表以非线程安全 Map 承载热路径读 + 运行时注册 API（DefaultToolExecutorProvider / ShellCommandRegistry）

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/executor/DefaultToolExecutorProvider.java:18-26, 38-57, 60-67`；`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/commands/ShellCommandRegistry.java:20-28, 36-47, 80-116`
- **证据片段**:
  ```java
  // DefaultToolExecutorProvider（每次 callTool 热路径读）
  private Map<String, IToolExecutor> executors = new HashMap<>();      // L18
  public boolean registerTool(AiToolModel toolModel) {                  // L38 — javadoc：
      ...                                                               // "eliminates the need for an
      executors.put(executor.getToolName() ..., executor);              //  explicit beans.xml bean declaration"
  ...
  // ShellCommandRegistry（每条命令 findCommand 读）
  private final Map<String, IShellCommand> commands = new LinkedHashMap<>(); // L27
  private final Map<String, String> aliases = new HashMap<>();               // L28
  ```
- **严重程度**: P2（理论风险，未具体化——当前无运行时变更方，但 API 契约明确预期运行时注册）
- **并发场景**: 注册表读方为请求线程（`ToolManagerImpl.callTool:62` 每次 `executorProvider.getExecutor`；shell `executeSimpleCommandWithContext:391`/`CheckVisitor:112` 每次 `registry.findCommand`），写方为 `registerTool`/`registerCommand`/`registerAlias`。
- **触发时序**: 写不出当前生产代码内的具体交错（grep 全仓：`registerTool` 无生产调用方，`registerCommand`/`registerAlias` 仅测试/装配可达），故按纪律降 P2 并标注"理论风险，未具体化"。风险形态：HashMap/LinkedHashMap 并发 put 触发 rehash 时，并发 get 可读到断裂 bin（丢条目）或抛异常；`getAllCommands/getAllIncludingAliases` 返回活映射的 unmodifiable 视图，迭代期间并发 register 抛 CME。
- **现状**: 两注册表均无同步、无并发容器、无写时快照；`registerTool` 的 javadoc 明确鼓励绕过 beans.xml 的运行时注册用法，即把"启动期单线程写入"的降档前提打破。
- **风险**: 一旦按 javadoc 契约使用（模型驱动的动态工具注册），工具分发/命令查找热路径上出现数据竞争，症状为偶发 NPE/丢工具/127 command not found，难复现。
- **建议**: `executors`/`commands`/`aliases` 换 `ConcurrentHashMap`（保序需求用 CHM + 有序 keySet 辅助结构）；`getAllCommands` 返回快照而非活视图；或在 javadoc 明示"仅启动期调用"并加运行时断言守卫（F10）。
- **失效面枚举**（F3）：三根内"注册表语义的可变 Map"全清单——`DefaultToolExecutorProvider.executors`（HashMap，未防护）、`ShellCommandRegistry.commands/aliases`（LinkedHashMap/HashMap，未防护）、`ToolManagerImpl.pathQueues`（CHM，读写均并发安全）、`UpdateTodosExecutor.todoLists`（CHM，见 C1-004）、`BashExecutionTracker.inFlight`（CHM，安全）。前两处同族同修。
- **信心水平**: 很可能（缺陷形态确定，触发依赖尚未存在的运行时调用方）
- **误报排除**: 非启动期降档口径的漏判——`registerTool` 的公共 API 契约（javadoc 原文）使"仅装配期写入"前提不成立，故不落入误报校准第 3 条。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验通过（DefaultToolExecutorProvider.executors HashMap :18 / ShellCommandRegistry LinkedHashMap+HashMap :27-28，均无同步，getAllCommands 返回活视图）；grep 复核：registerTool 全仓零调用方（含测试均无）、registerCommand/registerAlias 仅测试可达，当前写面=IoC 装配（ai-toolkit-defaults.beans.xml setExecutors 经 ioc:collect-beans 启动期单线程）——P2"理论风险，未具体化"判级符合口径纪律第 4 条。同族台账判例：WI 2.4 CL-415（SqlDataTypeMapping.register 并发无同步）裁 watch，7.1 逐项裁决时可对照（CHM 替换成本极低 vs javadoc 收窄+运行时断言守卫二选一）。

### [C1-007] ToolPathAffinity 仅读 input JSON：XML 节点派发路径（node 参数）绕过 per-path 串行化保护

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolPathAffinity.java:34-58`（守卫入口 `ToolManagerImpl.java:101-121`）
- **证据片段**:
  ```java
  public static String extract(AiToolCall call) {
      if (call == null) return null;
      String input = call.getInput();
      if (input == null || input.isEmpty()) {
          return null;                       // XML 派发路径（input==null，参数在 XNode 上）→ 无 path 键 → 不入队
      }
      ...
  ```
- **严重程度**: P2
- **并发场景**: ToolManagerImpl:92-100 的注释声明该队列是"process-wide chokepoint……the queue here is what actually protects the ReAct write-same-file race"。但 path 键提取只看 `call.getInput()`；XML DSL 派发（`AiToolCall.fromNode`，参数在节点属性/子节点，`call.childText` 双源读取证明该形态是一等入口）的调用 input 为空 → extract 返回 null → 直接走 `executeNow.get()`（L122），完全不串行。
- **触发时序**: T1/T2 两条 XML 形态的 write-file 调用（`path="/a.txt"` 为节点属性）经 executeParallel 并行派发 → 两者的 pathKey 均为 null → 同时进入 executor → 同一文件并发写（LocalToolFileSystem.atomicReplace 使最后写者胜，中间读撕裂；append 路径 `writeText(append=true):174-176` 则为非原子就地追加，直接交叉写坏内容）。对比：同参数的 JSON 派发调用会被 pathQueues 串行。
- **现状**: 同文件内 `runInBackgroundRequested:156-178` 与 `BashExecutor.textArg/boolArg` 均已实现"node 优先、JSON 兜底"的双源读取（注释多处援引 ToolPathAffinity precedent），唯独 ToolPathAffinity 自身是单源的——保护缺口与已修复的兄弟模式并存。
- **风险**: 串行化保护对整个 XML 派发入口静默失效；依赖该保护的上层（ReAct 并行工具扇出）在 XML 形态下回到 write-same-file 竞态，且无任何告警。
- **建议**: `ToolPathAffinity.extract` 增加节点双源回退（`call.childText("path"/"file_path"/"filePath", null)`），与 `RESERVED_KEYS` 三键一致；补一条 XML 形态下同 path 两调用被串行的回归测试。
- **失效面枚举**（F3）：双源读取家族全清单——`ToolManagerImpl.runInBackgroundRequested`（已双源）、`BashExecutor.parseJsonArgs/textArg/boolArg/intArg/longArg`（已双源）、`ReadRefExecutor/UpdateTodosExecutor/SkillExecutor`（`call.attrText/childText` 单读节点，但这些工具无 path 键需求，不在保护面内）、`ToolPathAffinity.extract`（单源，本条，唯一影响串行化覆盖面的站点）。
- **信心水平**: 确定（代码路径明确；实际暴露取决于 XML 派发在生产中的使用占比）
- **误报排除**: 非"防御性冗余"类——该提取函数是并发保护链的唯一开关，缺口即保护失效，不是理论增强项。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验通过：extract 单源（:38-41 input 空即返回 null，javadoc 自认"The dispatch path's single data source is getInput()"）、执行器侧双源读取在案（BashExecutor.textArg/boolArg 等回落 call.childText/attr*）、节点形态生产构造点核实（AiToolCall.fromNode 生产调用方 = nop-cli CliCallToolsCommand.parseToolCalls:115）、同文件 :92-100 注释自认该队列是 write-same-file 竞态的实际保护。条件化附注：当前 XML 生产入口（CLI）parallel 未设 → executeSequential 顺序派发，并发 XML 派发尚无生产调用方——缺口真实但触发面休眠；守卫缺失型（F10）+ 单点修复，维持 P2 并记条件。

### [C1-008] ShellCommandExecutor.closed 为死标志：close() 后 execute() 无任何守卫（use-after-close 不设防）

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:49, 635-641`
- **证据片段**:
  ```java
  private volatile boolean closed = false;      // L49 唯一读者：无
  ...
  @Override
  public void close() throws IOException {
      closed = true;                            // L640 写后无人读
  }
  ```
- **严重程度**: P3
- **并发场景**: 关闭方线程与后续 execute() 调用线程并发；volatile 保证了标志可见性，但可见性无意义——没有任何路径消费它。
- **触发时序**: T1 close() → T2 execute("ls") → 正常解析、执行、写文件系统——close 后仍可完整执行命令（含外接命令与重定向落盘）。
- **现状**: close() 语义为空操作（`&` job 移交 sink 后仅剩置位）；F10（声明契约无运行时守卫）的微缩形态。
- **风险**: 资源归属方（引擎关闭会话）无法确知执行器已停；后续命令继续在外部世界产生副作用。
- **建议**: `execute()` 入口 `if (closed) return FutureHelper.success(new ExecutionResult(125, "", "shell executor closed"));` 或直接抛 `ERR_AI_SHELL_INVALID_STATE`。
- **失效面枚举**（F3）：同族"closed 标志"清单——`AbstractShellInput.closed/eofSeen`（有读者：isClosed/readLine 判空，正确）、`BlockingQueueShellOutput.closed`（有读者：write 拒绝，正确）、`BlockingQueueShellInput.eofReceived`（有读者，正确）、`PrintStreamShellOutput.closed`/`FileShellOutput.closed`/`ListShellOutput.closed`（均有 write 拒绝读者，正确）——唯 `ShellCommandExecutor.closed` 无读者，同族中孤例。
- **信心水平**: 确定
- **误报排除**: 非"低频关闭路径粗粒度同步"豁免——这是守卫完全缺失而非优雅性问题。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现——回码核验通过（closed 写点 :640 唯一、execute() 全路径零读者；close() 现语义即注释声明的空操作）；同族清扫逐一核实成立（AbstractShellInput.closed/eofSeen、BlockingQueueShellOutput.closed、BlockingQueueShellInput.eofReceived、PrintStream/File/ListShellOutput.closed 均有读者，本类为孤例）。P3 恰当（守卫缺失但当前无生产关闭方依赖该语义）。

---

## 维度集外线索（overlay C2/C3/C5/C7/C9）

### [nop-ai-toolkit-tools-shell-CL-001] pathQueues 完成尾条目永不驱逐 —— 全局注册表只增不删（F4），归 C5

- 位置：`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java:32-33, 105-118`。
- 形态：`pathQueues` 每次 per-path 调用都 `compute` 写入新的尾 future 链条目，但没有任何路径在链完成后 `remove`——每个曾被并发不安全工具触碰过的唯一 path 键（含历史路径、临时文件名）都在 manager 单例上永久保留一个完成态 CF 链条目。
- 为何超出本维度：CHM 操作并发正确（C1 无恙），缺陷是共享注册表的**生命周期/无界增长**——长时运行服务中条目数随历史路径总数单调增长（F4 无界账本；单条目为 pathKey 字符串 + 完成 CF 链，量级小但不可回收）。
- 建议归维：C5（内存/长时运行）。修复方向：尾 future 完成后 `pathQueues.remove(pathKey, tail)`（compute 返回值即新尾，可挂 `whenComplete` 对比驱逐），或定期清扫完成条目。

### [nop-ai-toolkit-tools-shell-CL-002] executePipeline 多阶段占满固定线程池的线程饥饿死锁，归 C3

- 位置：`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:243-286`（默认执行器 `GlobalExecutors.globalWorker()`，L60）。
- 形态：pipeline 的 N 个阶段一次性全部 `supplyAsync(..., executor)` 提交，阶段 i>0 阻塞读阶段 i-1 的有界队列（生产者-消费者）；全部提交到同一个固定大小共享池。
- 为何超出本维度：阶段间正确性由队列 happens-before 保证（C1 无恙）；风险是**调度拓扑级死锁**——两个并发会话各提交 M 阶段 pipeline，池调度先跑满后继阶段（阻塞等前驱），前驱阶段排队无线程可用 → 全池互等（经典 thread-starvation deadlock）。
- 建议归维：C3（异步链拓扑）。修复方向：按阶段惰性提交（阶段 i 在 i-1 产出首个 chunk 后提交），或为 pipeline 提供专用池/平台 `IThreadPoolExecutor` 隔离预算。

### [nop-ai-toolkit-tools-shell-CL-003] submitBounded 在派发线程上阻塞式 acquire 信号量，归 C2

- 位置：`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java:232-246`。
- 形态：`semaphore.acquire()` 直接阻塞提交 `callTools(parallel=true)` 的调用线程直至有槽位； permits 耗尽时调用线程（可能是事件循环/ReAct 主循环线程）被挂起，无超时。
- 为何超出本维度：信号量本身使用正确（permit 与 future 生命周期绑定）；问题是**阻塞原语作用在派发线程**的并发原语选型（C2 视角：应使用平台 `HighWatermarkSemaphore`/异步排队而非线程阻塞）。
- 建议归维：C2（并发原语与阻塞面）。修复方向：改异步等待（`whenComplete` 链式排队）或至少套平台 `IThreadPoolExecutor` 的队列预算 + `QueueOverflowPolicy`。

### [nop-ai-toolkit-tools-shell-CL-004] 沙箱 reader 线程对 interrupt 不敏感 + docker kill 尽力而为的累积泄漏链，归 C9

- 位置：`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/DockerBashSandbox.java:107-110, 253-275, 321-331`；`HostBashSandbox.java:92-95, 208-226, 188-198`。
- 形态：每次 execute 新建一个 daemon reader 线程阻塞在 `InputStream.read`（不响应 interrupt）；`awaitReader` 的 grace join+interrupt 无法真正停止仍在 read 的线程；超时路径依赖 `killContainer`（docker kill/rm -f 双双 catch-all 吞异常 + `runShortCommand` 的 `p.waitFor(5s)` 返回值被忽略）。
- 为何超出本维度：无共享可变状态问题（捕获缓冲经 AtomicReference 发布不可变副本，设计正确）；风险是**长时运行线程/进程累积**——kill 失败或容器 stdio 被孙进程持有时，reader 线程与 docker 进程滞留，每次执行泄漏一个，随执行次数线性累积。
- 建议归维：C9（长时运行/线程泄漏），C6 交叠（waitFor 返回值忽略属资源清理不对称 F8）。修复方向：reader 改用带超时的 `Process.getInputStream` 非阻塞轮询或平台 `NamedThreadFactory` + 强制 `process.destroyForcibly()` 后校验线程终止；`runShortCommand` 检查 waitFor 超时并升级销毁。

---

## 维度复核结论

- 复核人：C1 维度独立复核员（WI 3.2 Phase 4，与初审/深挖代理不同任务）
- 复核日期：2026-10-20
- 复核方法：逐条回码核验（证据行号 + 证据片段 + 触发时序的 CompletableFuture/CHM/JLS 规范行为推演）→ 装配面与调用方全仓 grep（生产可达性）→ cra-index WI 1.1–3.1 台账查重与同案对照 → 降档口径核对（对照本 campaign C1-005(3.1)/C5-022(3.1) opt-in 装配面、C7-005(2.5) 零生产实例化判例）。C4 复核并行进行中，跨维同站点合并归裁定阶段。

### 逐条判定表（C1-001…010 全覆盖）

| ID | 初判 | 判定 | 最终严重度 | 现状标注 | 核验要点 |
|---|---|---|---|---|---|
| C1-001 | P1 | 保留 | P1 | 新发现 | 代码机制逐行核实；生产装配补强（CLI Sync 执行器 / ChatBi null executor / Builder 无默认值）；(b) 子项条件化；同站点 C4-005 待合并 |
| C1-002 | P1 | 保留并降级 | P2 | 新发现 | 证据全核实；降档=P1→P2 条件化（nop-shell 零生产实例化 + IBackgroundJobSink 仓内零接线）；仓内 pipeline-export 次级向量保留 |
| C1-003 | P2 | 保留 | P2 | 新发现 | 证据核实；同站点 C8-002(P1) 待合并（建议终裁取 C8 侧判级——其确定性触发链更强）；休眠面条件附注 |
| C1-004 | P2 | 保留 | P2 | 新发现 | 证据核实；向量修正为 agent 引擎 fan-out（executeParallel 无生产调用方自认于源码注释）；F4 面关联 CL-001/WI 3.1 MA-03，不重复立项 |
| C1-005 | P2 | 保留 | P2 | 新发现 | 三站点非原子写 + 两处已修兄弟 + R2 扩展面全部核实；mavenvfs 核心子面休眠条件化，扩展面（FileToolBizModel→DslToolImpl）生产接线请求可达，维持 P2 |
| C1-006 | P2 | 保留 | P2 | 新发现 | 结构核实 + grep（registerTool 全仓零调用方、register/registerAlias 仅测试）——P2 理论风险判级合规；对照 WI 2.4 CL-415 watch 判例留 7.1 |
| C1-007 | P2 | 保留 | P2 | 新发现 | 单源 extract + 双源执行器 + fromNode 生产构造点（CLI）核实；并发 XML 派发当前无生产调用方 → 条件化附注，守卫缺失型维持 P2 |
| C1-008 | P3 | 保留 | P3 | 新发现 | 死标志 + 同族孤例清扫全部核实；P3 恰当 |
| C1-009 | P2 | 保留 | P2 | 新发现 | readIndex check-then-act + 并发消费接线点 + FileShellInput 兄弟核实；向量条件与 C1-002 同（sink 零接线/模块零生产实例化）附注，P2 维持 |
| C1-010 | P3 | 保留 | P3 | 新发现 | 两锁域复合 + clearHistory 撕裂时序核实；生产接线（ai-tools-defaults.beans.xml）；后果量级与 P3 匹配 |

**判定统计**：保留 10（含降级 1：C1-002 P1→P2）、驳回 0、待定 0。
**最终保留严重度分布**：P1×1（C1-001）、P2×7（C1-002/003/004/005/006/007/009）、P3×2（C1-008/010）。
**现状标注分布**：新发现×10；已知未修复×0；已修复×0（本单元三模块并发主题历史审计 0 命中，dispatch-log 已证；全部缺陷在当前 live code 复现存）。

### 跨维同站点指针（合并归裁定阶段，C1 侧不重复判级）

1. **C1-001 ↔ C4-005**（ToolManagerImpl pathQueues 同一代码块 :104-119）：C4-005 主张"同步抛出→result 永不完成"的排队悬挂面，C1-001 主张"锁内执行/重入/悬挂"三面——悬挂子面重叠，锁内执行与重入为 C1 独有面。合并裁定取并集，不双计。
2. **C1-003 ↔ C8-002**（BlockingQueueShellOutput.close:42-47 + BlockingQueueShellInput.sendEof:50-55）：同一文件同一机制（EOF offer 满时丢弃）。C8 侧 P1 且触发链分析更强（单命令恰 1024 chunk 时 close 必然队满——消费者拓扑性滞后于完成），C1 侧 P2 基于"竞态"口径偏弱。建议合并后采用 C8-002 判级，C1-003 转同站点引用。

### 同案/台账命中清单

- **WI 3.1 邻接面**：3.1 C4-006 复核节曾以 `ToolManagerImpl.callTool` executeNow 内联/完成线程两路径语义作子项复核依据（未立案本站点）——依赖关系非重复立项，C1-001 零重复确认；3.1 台账（MA-01 流式主链族/MA-04 spawn 池泄漏等）与本单元 C1 十条无同站点。
- **F4 无界账本族**：CL-001（pathQueues 完成尾不驱逐，本单元已登记 overlay）= C1-004 的 todoLists 会话条目不清理面同族；与 WI 3.1 C5-015 MA-03 F4 引擎终态清理族（agent 侧 29 站点）同族不同站点——台账关联，不并入不双计。
- **WI 2.4 CL-415 注册表族**：`SqlDataTypeMapping.register 并发无同步`（裁 watch）与 C1-006 同为"注册表 + 运行时 register 公共 API + 非线程安全容器 + 当前零生产写方"形态——判例对照留 7.1 逐项裁决。
- **原子替换修复族**：C1-005 引用的 LocalToolFileSystem.atomicReplace / ThoughtStorage.atomicWriteText 为本单元既有修复先例（非台账在案发现），属缺陷族内部对照。

### 高风险条目子项复核说明（C1-001，唯一保留 P1）

- **(a) 锁内执行**：成立。已完成 trigger 上 `handle`/`thenCompose` 的函数在调用线程内立即执行为 CompletableFuture 规范行为；生产实证 = `CliCallToolsCommand.java:75` 注入 `SyncThreadPoolExecutor.INSTANCE`（submit 经 ContinuationExecutor 在调用线程内联跑完整 tool 执行）。后果定级受装配条件：CLI 入口单线程无并发受害者；agent 引擎缺省异步池下锁内窗口 = submit 入队（量级小）。作为结构缺陷保留 P1（与 (c) 合并定级）。
- **(b) 重入**：条件化成立。需"锁内同步执行的工具链再入同一 pathKey"（call-agent 嵌套派发 + 同步执行器装配同时成立）；缺省异步池下嵌套发生在池线程，CHM.compute 不重入。作为装配条件面保留在建议修复范围（executeNow 移出 compute 即同时消除 (a)(b)）。
- **(c) 悬挂**：结构性成立且为本条 P1 主承载。`executeNow.get()` 同步抛出时 `whenComplete` 尚未注册 → 手工 `result` 无人完成；异常被 thenCompose 吸收为链异常（队列不中毒、后续调用正常——该论证核实正确）。可达面：ToolExecuteContext.Builder.executor 无默认值（:87,120）+ 生产类 `ChatBiToolExecuteContext.getExecutor()` 返回 null（nop-datav ChatBiToolCallingLoop:165/192 以该 context 调 callTool——若工具带 path 键即静默挂死）+ 饱和池 REE。17 个 executor 的 doExecute 均有 catch-all，业务异常不走本面——同步抛出面收敛为上述三类，精确。
- **附**：executeParallel/submitBounded 联动论证核实成立（semaphore 批次局部，release 挂在返回 future 的 whenComplete 上，悬挂即不释放，泄漏限于本批）。

### 复核纪律声明

- 未修改任何原发现条目正文；本节与各条目末尾的 `复核状态` 行为本轮唯一新增内容。
- 降档仅 1 处（C1-002），依据为休眠面条件化判例（grep 证据：nop-ai-shell src/main 外零 import、`new ShellCommandExecutor` 仅测试、setBackgroundJobSink 生产调用方 0），非严重度主观调整；条件解除（嵌入方接线）时恢复 P1 级。
- C4 复核并行进行中；本节跨维指针仅登记，不做跨维合并裁定。

## 最终保留项

| 最终 ID | 严重度 | 现状 | 一句话 | 修复归属建议 |
|---|---|---|---|---|
| C1-001 | P1 | 新发现 | pathQueues 排队链：已完成 trigger 在 CHM.compute 锁内同步执行（CLI Sync 装配下全执行在锁内）+ 手工 result future 在 executeNow 同步抛出时永不完成（ChatBi null executor / 池拒绝可达）+ 同步执行器装配下同 bin 重入崩——修复须将 executeNow 移出 compute 并补 result 完成契约兜底（与 C4-005 同 diff） | 本单元工单（与 C4-005 合并单） |
| C1-002 | P2 | 新发现 | ShellCommandExecutor 会话状态（exportedEnv/currentWorkingDir）跨线程无同步：& job/pipeline 阶段/组子 shell 快照恢复三写面竞态（条件：嵌入方多线程驱动或接线 sink） | 本单元工单（与 C1-009 同批守卫方案） |
| C1-003 | P2 | 新发现 | EOF 投递用忽略返回值的 offer：队满丢 EOF → 消费者永久阻塞（与 C8-002 同站点，终裁归 C8 侧） | 并入 C8-002 工单 |
| C1-004 | P2 | 新发现 | UpdateTodosExecutor static 进程级 todoLists 的 clear/write 复合操作竞态：同会话并行调用丢失更新（向量=agent 引擎 fan-out）；会话条目不清理归 CL-001/F4 族 | 本单元工单（compute 单次原子决策 + 会话清理挂钩 CL-001） |
| C1-005 | P2 | 新发现 | DeltaVirtualFileSystem delta 目录非原子写（同仓库原子替换模式漏修兄弟）+ 扩展面 DslToolImpl/AiFileTool 经 ResourceHelper.writeText 就地写（4 写点） | 本单元工单（照抄 atomicWriteText 模式，扩展面同批） |
| C1-006 | P2 | 新发现 | 两公共注册表（DefaultToolExecutorProvider.executors / ShellCommandRegistry.commands/aliases）非线程安全 Map 承载热路径读 + 运行时注册 API 契约（当前零生产写方，对照 WI 2.4 CL-415 watch 判例） | 7.1 逐项裁决（CHM 替换或声明边界+守卫） |
| C1-007 | P2 | 新发现 | ToolPathAffinity.extract 单源读 input JSON：XML 节点派发形态绕过 per-path 串行化保护（当前并发 XML 派发无生产调用方，条件化） | 本单元工单（单点补双源回退 + 回归测试） |
| C1-008 | P3 | 新发现 | ShellCommandExecutor.closed 死标志：close() 后 execute() 无守卫（同族孤例） | 随 C1-002 守卫批次顺带 |
| C1-009 | P2 | 新发现 | ListShellInput.readIndex 非原子 check-then-act：& job 与前台并发消费同一 stdin 时行丢失/重复（FileShellInput.position 同族同修；条件同 C1-002） | 本单元工单（与 C1-002 同批） |
| C1-010 | P3 | 新发现 | SequentialThinkingBizModel.processThought 的 addThought→getAllThoughts 跨两个锁域：并发 clearHistory 使"添加成功"与分析输入撕裂（派生视图，无持久化损坏） | 7.1 裁决（addAndList 单锁域复合方法或 javadoc 声明最终一致） |
