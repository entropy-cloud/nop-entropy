# C1 共享可变状态与可见性 — 初审（盲审·全单元单批·抽查模式）

- **审计单元**: nop-peripheral = nop-dev-tools(155f/16,376L) + nop-frontend-support(84f/6,751L) + nop-demo(94f/3,760L) + nop-entropy-e2e(0f，零审计面)；全单元 333f/26,887L
- **维度**: C1 共享可变状态与可见性（`ai-dev/skills/concurrency-resource-audit-prompt.md` C1 正文 + `deep-audit-prompts.md` 共享提示词前缀）
- **盲审声明**: 本初审为盲审——未阅读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何历史记录；仅依据 live code（基线文件 `_tmp/cra-baseline-nop-peripheral.txt` @ a20ca25130）与本领域方法论。所有条目 `复核状态: 未复核`。
- **编号规则**: C1 发现 `C1-001` 起；维度集外线索（C2~C9 形态）以 `nop-peripheral-CL-0NN` 编号附记。
- **批次**: 全单元单批·抽查模式——五类载体全单元盘点（grep 基线 + 逐文件核对），重点面精读：nop-xlang-debugger 全部 9 文件、nop-api-debugger 全部 11 文件、idea-plugin 调试器/服务/资源面、maven-shaded-plugin 4 文件、nop-js JavaScriptWorker/JavaScriptService、demo 并发标记文件。基线命中文件（dev-tools 9 + frontend 2 + demo 1）全部覆盖。
- **demo 降口径声明**: nop-demo 为演示面，不按生产标准判级；仅结构性并发/资源缺陷立案（本案立案 1 条：C1-007），其余 demo 静态面只记表。
- **idea-plugin 惯例校准声明**: IntelliJ 平台面按平台线程契约校准——PSI/VFS 读在 read-action、UI 写在 EDT、调试器事件在 manager thread 均为平台惯例；本审计只对「跨线程共享的插件自有可变字段」判级，不把"未用 invokeLater 包裹"这类无证据的惯例猜测立案。经核 `XLangDebuggerRunner.dispatch()`（invokeAndWait + AtomicReference）、`XLangDebugProcess.debuggerNotification`（manager thread schedule）、`notIncludeXLangDebugger`（runReadAction）的 EDT/ReadAction 用法合规，不立案。
- **误报基线遵守**: `_` 前缀生成文件不作对象；无状态单例并发调用、volatile/CHM 原语使用本身不立案；平台设施（LocalCache/LongHashMap/ResourceLoadingCache 等）只审使用方式。

---

## 一、五类载体清单表（字段 × 写者 × 读者 × 同步机制 × 判定）

### 载体 1：singleton bean / 长生命周期对象的实例字段

| 载体（字段） | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `XLangDebugger.breakpointsMuted/lastSuspendThread/tempBreakpoint/suspended/closed/stepMode` | RPC 线程（IDE 命令）、被调试线程 | 双方 | volatile | 安全（stepMode 的复合交接缺陷见 C1-001） |
| `XLangDebugger.suspendedThreads` (LongHashMap) | 被调试线程（makeSuspendedThread）、RPC 线程（close/cleanup/get） | 双方 | `synchronized(suspendedThreads)` 全部 5 个访问点（L89/95/224/243/560） | 安全 |
| `XLangDebugger.monitorWaitInterval/notifier` | 启动配置（setter） | 被调试线程 | 无（普通字段） | 安全（配置先于 start 的 JavaBean 惯例；运行期改值无可见性保证，记观察项） |
| `XLangDebugger.cleanupThread` | 构造器创建、close() shutdown | — | 构造内安全发布 | 安全（一次性调度缺陷见 nop-peripheral-CL-003） |
| `XLangDebugger.compileCache` / `BreakpointManagerImpl.externalPathCache` | 双方 | 双方 | LocalCache（平台设施，容量 100/1000） | 安全 |
| `BreakpointManagerImpl.breakpoints` (CHM<String,IntHashMap>) | RPC 线程 | 被调试线程 | CHM + per-map `synchronized(map)`，put/remove/get/迭代均在锁内（L59/69/78/99） | 安全（`setBreakpoints`=clear+重加的非原子窗口对调试器语义无害，记观察项） |
| `BreakpointManagerImpl.useExternalPath` | 启动配置 | 双方 | 无 | 安全（同配置惯例） |
| `SuspendedThread.rt/lastBreakLocation/lastBreakFrameIndex` | 拥有线程（synchronized update L63） | RPC 线程 | `synchronized(thread)`（XLangDebugger eval/getStackInfo/getFrameVariables）+ 拥有线程免同步读 | 安全 |
| `SuspendedThread.suspended` | doSuspend/monitorWait finally | RPC 线程 | volatile | 生命周期缺陷见 C1-001 |
| `JavaScriptWorker.closed/shutdownGracefully/processing/executeThread` | worker 线程 + 关闭方 | 任意请求线程 | volatile ×4 | 安全（复合交接缺陷见 C1-002） |
| `JavaScriptWorker.context/globals/outStream/errStream/fileSystem` | 仅 worker 线程（run/init/队列任务） | 仅 worker 线程 | 线程遏制（经 taskQueue 队列交接建立 HB） | 安全——但遏制依赖约定，`public invokeFunction` 无线程守卫（F10 形态，记观察项） |
| `JavaScriptService.workers` (volatile array) | doStart/refreshConfig | 请求线程 | volatile 安全发布 + 局部快照 | 安全（refreshConfig 为管理路径） |
| `JavaScriptService.executor/workerCount/initScriptPath/jsLibLoader` | doStart 前配置 | doStart/refreshConfig | 生命周期顺序 | 安全 |
| `XLangBreakpointHandler.breakpoints/bpMap` (CHM×2) | EDT + ReadAction 线程 | manager thread | CHM（双 Map 配对非原子，降级路径无害，记观察项） | 安全 |
| `XLangBreakpointHandler.muted` | EDT（session listener） | ReadAction 线程（sendBreakpoints） | **无** | **缺陷 C1-005** |
| `XLangDebugProcess.debugger` | Backgroundable 线程（connect） | EDT/manager thread | volatile | 安全 |
| `XLangDebugProcess.isDisconnected` | stop()（EDT） | 无读者 | — | 写者唯一定位（L259），全仓无读者——死字段，记观察项，非并发缺陷 |
| `XLangSuspendContext.myActiveStack` | manager thread（addExecutionStack）/EDT（setActiveExecutionStack） | EDT | **无**（myExecutionStacks 为 COW list） | 理论可见性竞态：过期 active stack 仅致 UI 陈旧视图且 EDT 高频重查自愈，写不出具体后果时序 → 记表不立案 |
| `XLangStackFrame.pos` | EDT（getSourcePosition） | EDT | EDT 遏制 + 幂等惰性 | 安全 |
| `NopProjectService.inited` | 任意线程（init） | 任意线程 | `synchronized init()` 内检查-置位（L44-48） | 安全（守卫原子） |
| `NopProjectService.componentManager/cleanup/dictCache` | final/构造发布 | 任意线程 | final | 安全（其全局副作用见 C1-003） |
| `XLangDebuggerInitializer.server/debugger` | initialize（单线程启动） | RPC 线程（sendBreakpointNotice 取局部引用判空） | initialize→server.start() 的 Thread.start HB | 安全 |
| `DynamicJsLoader.systemJsTransformer` | 配置 setter | 资源加载线程 | 配置惯例 | 安全（其 syncGet 阻塞形态见 nop-peripheral-CL-004） |
| `AbstractResourceTransformer.time/beansResources`、`XdslResourceTransformer.FILE_TYPES` | Maven shade Mojo 执行线程 | 同线程 | 单线程执行模型 | 安全（FILE_TYPES 非 static 为风格问题，非本维度） |

### 载体 2：static 可变字段

| 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `ProjectFileHelper.nopVfsPathCaches` (static CHM<Project,CachedValue>) | 任意线程 computeIfAbsent | 任意线程 | CHM | **缺陷 C1-004**（无移除路径 → Project 泄漏） |
| `ConditionExprHelper.s_conditionOpToFilterOp/s_filterOpToConditionOp` (static HashMap) | 仅 static 初始化块（L205-211） | 任意线程 | 类初始化安全发布，此后只读 | 安全（ effectively immutable；非 final 声明为风格问题） |
| `VueTemplateParser.INSTANCE` (public static 非 final) | 类初始化 | 任意线程 | 类初始化发布；全仓无重新赋值点（grep 仅测试读取）；解析器无状态 | 安全 |
| `DemoServiceBizModel.RESOURCE_STORE` (static CHM) | HTTP/Biz worker 线程 | 同 | CHM | **缺陷 C1-007**（复合操作非原子 + 无界，demo 降口径） |
| `DeltaDemoMain.globalArgs` / `QuarkusDemoMain.globalArgs` (static String[]) | main 线程启动期 | 平台启动 | main 先于并发阶段 | 安全（demo，单线程启动惯例） |
| `XLangStdDomainXdefRefReference.XDEF_REF_PROPS` (static final String[]) | 类初始化 | 只读 | final | 安全 |
| idea-plugin `src/test/resources/_vfs/test/java` 下 `XDefBodyType/XDefOverride.textMap` | 测试 fixture 源码（非编译参与方） | — | — | 测试资源文件，不属产品审计面，不立案 |

### 载体 3：全局注册表 / 缓存使用点

| 载体 | 写者 | 读者 | 同步机制/配对 | 判定 |
|---|---|---|---|---|
| `NopAppListener.appFrameCreated` 注册 `VirtualFileSystem/DictProvider/ResourceComponentManager` 全局单例 | EDT 启动期一次 | 全插件 | app 生命周期一次 | 安全 |
| `NopProjectService.init` → `XLangCoreInitializer.initialize()` 改写 EvalGlobalRegistry/XPathProvider/EvalExprProvider/JaninoScriptCompiler 全局静态；`dispose()` 反注册 | 项目打开/关闭 | 全 IDE 进程 | **项目级生命周期绑全局注册表** | **缺陷 C1-003** |
| `XLangDebuggerInitializer` ↔ `EvalExprProvider.registerGlobalExecutor` | initialize/destroy | 表达式求值 | register/restore 严格配对（L77/L156），register 为 initialize 末步异常安全 | 安全 |
| `BreakpointManagerImpl.externalPathCache`、`XLangDebugger.compileCache` | 双方 | 双方 | LocalCache 容量上限 | 安全 |

### 载体 4：ThreadLocal

| 载体 | 写者 | 读者 | 清理方式 | 判定 |
|---|---|---|---|---|
| `ProjectEnv.s_project`（全单元唯一 ThreadLocal，grep 证实） | withProject | currentProject | finally 恢复前值（L22-24），符合平台清理规范 | 安全（`set(null)` 而非 `remove()` 仅留空 Entry，无强引用滞留，记观察项） |

### 载体 5：隐式共享 / 逃逸（构造器/getter 泄漏、跨线程传参）

| 载体 | 逃逸路径 | 判定 |
|---|---|---|
| `SourceLocation.text/cellPath/externalPath`（`@ImmutableBean` 上的 3 个非 volatile 惰性缓存字段，nop-api-core 所有） | `BreakpointManagerImpl.toSourcePath` 在每断点检查热路径由任意被调试线程 `loc.setExternalPath(...)`（L136）；getCellPath/toString 同类惰性写 | 良性竞态：幂等惰性缓存、引用写原子、值确定性（externalPathCache 按 path 确定）、不参与 equals/hashCode 的字段最坏重算。属平台既有发布惯用法的使用面，记表不立案；若平台侧收紧 SourceLocation 不可变契约，此处为首批受影响调用方 |
| `JavaScriptWorker.taskQueue` 交接 Consumer | 队列 HB 安全发布 | 安全（队列本身无界见 nop-peripheral-CL-002） |
| `DebugNotifier`/`sendBreakpointNotice` 跨线程 DTO（StackInfo/BreakpointHitMessage） | RPC 序列化边界，构造后不再变 | 安全 |
| `XLangExecutionStack.frames`（manager thread 构造 → COW list → EDT 读） | final 字段语义安全发布 | 安全 |
| 构造器启动线程/构造器注册监听器扫描 | 全单元 `this` 逃逸扫描：`XLangDebugger` 构造器仅向外部 scheduler 提交 `this::cleanup`（不泄露未完成构造状态——cleanup 只读已初始化的 final 字段） | 安全 |
| long/double 非原子读写、`++` 复合递增扫描 | 计数仅 `LongAdder completedTasks`；无共享 long/double 裸字段、无共享 SimpleDateFormat/Random/Date（grep 证实） | 安全 |

---

## 二、风险立案

### [C1-001] XLangDebugger 挂起交接非原子：resume/step 命令与断点命中交叠产生"幽灵挂起线程"并丢失挂起

- **文件**: `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/XLangDebugger.java:472-482, 534-544, 195-204`
- **证据片段**:
  ```java
  private void doSuspend(SuspendedThread thread) {        // L472
      thread.setSuspended(true);                          // L478
      suspended = true;                                   // L479
      lastSuspendThread = thread;                         // L480
      monitorWait(thread);                                // L481
  }
  private void monitorWait(SuspendedThread thread) {      // L534
      if (suspended) {                                    // L535 —— 关闭路径：整块跳过
          monitorLock.lock();
          ...
          finally { thread.setSuspended(false); ... }     // L549 复位仅在此分支内
      }
  }
  private void monitorNotify(DebugStepMode stepMode) {    // L195
      this.stepMode = stepMode;
      this.suspended = false;                             // L197 非原子清标志
      ...
  }
  ```
- **严重程度**: P2
- **并发场景**: RPC 调试命令线程（SimpleRpcServer worker，执行 resume/stepOver 等 → monitorNotify）与被调试线程（执行 checkBreakpoint → doSuspend）；IDE 侧因 suspend 通知（notifier 在 doSuspend 之前触发，L466/L395）认为线程已挂起。
- **触发时序**: T_dbg 命中断点，执行 L478-480（`thread.suspended=true`，全局 `suspended=true`，`lastSuspendThread` 已发布）→ T_ui 此时执行 resume()：monitorNotify 将 `suspended=false`（L197），resumeCondition 上无等待者，signal 丢失 → T_dbg 进入 monitorWait，L535 读到 `suspended==false` → 整个 if 块跳过，`thread.setSuspended(false)`（L549）永不执行 → 被调试线程继续运行，但其 `SuspendedThread.suspended` 永久为 true。
- **现状**: doSuspend 先置 per-thread 挂起标志、monitorWait 仅在"全局 suspended 为真"分支内复位该标志；命令线程的 `suspended=false` 写落在 L479 与 L535 之间时，两条线索失配。
- **风险**: (1) `getSuspendedThreads()`（L223-233 按 `thread.isSuspended()` 过滤）持续把运行中的线程上报为挂起，IDE 线程面板出现幽灵挂起项；(2) IDE 对幽灵线程发起 getFrameVariables/getExprValue → `eval()` 在 `synchronized(thread)` 内读 EvalRuntime，而该线程正在运行并持续改写 rt/scope —— 对非并发设计的 EvalRuntime/IEvalScope 形成读写并发（变量面板异常/CME），影响面限于调试会话；(3) 反向变体：用户 step 命令在同一窗口被吞（挂起被跳过），IDE 已收到 suspend 通知却等不到停住。幽灵状态持续到该线程下次真实挂起（L549 finally 复位）或线程死亡且被清理——而清理为一次性（nop-peripheral-CL-003），可长期滞留。
- **建议**: monitorWait 的标志复位移到无条件位置：进入 monitorWait 即 lock，`finally` 中复位 per-thread 标志，循环条件改为在锁内检查快照；或 doSuspend 与 monitorNotify 统一走 monitorLock 临界区，使 `suspended` 的写与等待进入互斥（恢复"signal-before-wait 由锁内条件重查兜底"的完整语义）。
- **失效面枚举**（F3 同族清扫）: monitorNotify 调用方 5 处——resume(L191)、stepInto(L149)、stepOver(L159)、stepOut(L169)、runToPosition(L236→resume)，均经同一 monitorNotify，同族暴露；suspend()（L179）直接写 `suspended=true` 不受本缺陷影响（只加挂不起负作用）。doSuspend 调用方 6 处——checkSuspend/checkTempBreakpoint/checkStepInto/checkStepOver/checkStepOut/checkHitBreakpoint，全部经同一 doSuspend 路径，无例外兄弟。同单元另一处挂起标志 SuspendedThread.suspended 已 volatile，非同族。
- **信心水平**: 确定（交错序列可完全具体化；doSuspend L473-477 的既有注释表明该处曾修过一次可见性序，本条是其未覆盖的交接窗口）
- **误报排除**: 非"看到共享字段即报线程不安全"——所有单字段均为 volatile，缺陷在复合交接序列而非原语；非"低频路径粗同步"校准项（断点命中×用户 resume 是调试器主路径交叠）；也非已声明边界（无文档声明 resume 可丢失）。
- **复核状态**: 已保留（P2）｜新发现——交错序列独立复算成立，通知点先于 doSuspend 使窗口系统性暴露，详见复核结论节第 1 条。

### [C1-002] JavaScriptWorker 关闭路径任务搁浅：execute 检查-入队非原子，shutdown 窗口内提交的任务永不执行、future 永不完成

- **文件**: `nop-frontend-support/nop-js/src/main/java/io/nop/js/engine/JavaScriptWorker.java:98-103, 105-119, 283-316, 325-337`
- **证据片段**:
  ```java
  public void execute(Consumer<Boolean> task) {          // L98
      if (closed)                                        // L99 检查
          throw new NopException(ERR_JS_CONTEXT_ALREADY_CLOSED);
      taskQueue.add(task);                               // L102 入队 —— 与检查非原子
  }
  ...
  // run() 退出路径：
  if (shutdownGracefully) { closed = true; break; }      // L298-300
  } finally {
      closed = true;
      cancelAllTasks();                                  // L311 排空后不再有消费者
      ...
  }
  ```
- **严重程度**: P1
- **并发场景**: 调用方线程（HTTP worker/资源加载线程，经 `JavaScriptService.invokeAsync` → `schedule` → `execute`）与关闭方（quarkus/spring 停机线程执行 `doStop`→`close`，或配置刷新线程执行 `refreshConfig` 缩容→`shutdownGracefully`）与 worker 线程（1s 轮询退出）。
- **触发时序**: T_caller 在 L99 读到 `closed==false` 后被抢占 → T_shutdown 置 `closed=true`，worker 线程按 L298-300/L310-311 退出循环并排空队列（cancelAllTasks 的最后一次 poll 已返回 null）→ T_caller 恢复执行 L102 将 task 入队 → 队列永久无消费者，task 永不执行，`schedule` 返回的 CompletableFuture 永不完成。
- **现状**: 关闭采用"标志位 + 队列排空"两步，但生产者的检查-入队与消费者的最后排空之间无同步点；被搁浅的 schedule 任务没有异常完成路径。
- **风险**: 停机/缩容窗口内的在途调用永久挂起——已证实调用方 `DynamicJsLoader.transformToSystemJs` 以 `FutureHelper.syncGet(future)` 无显式超时等待（`nop-web/.../DynamicJsLoader.java:127-131`，syncGet 委托 context 实现的 get 语义），该调用线程将无限阻塞；停机期请求线程滞留进一步阻碍优雅停机。同窗口内 `close()` 入队的关闭任务也可能搁浅（context 关闭由 worker finally 兜底，故资源后果被掩盖，唯独业务 future 无兜底）。
- **建议**: 消费者退出后提供拒绝语义：worker finally 排空后置 `closed=true`（已有），`execute` 改为入队后复查 `closed`（复查为真则从队列移除并异常完成）；或 `schedule` 侧持 `closed` 状态注册表，worker 退出时对存量等待 future 统一 `completeExceptionally`。优先复用平台 `QueueOverflowPolicy`/`IBlockingSource` 语义（见 nop-peripheral-CL-002）。
- **失效面枚举**（F3 同族清扫）: 经 `execute()` 的全部提交点——`schedule()`（invokeAsync 全部函数、`reinitWorkers` 的 FUNC_REINIT 调度，JavaScriptService.java L50-55/L67-93）与 `close()` 的关闭任务（L115）——同一非原子窗口，无例外兄弟；`invokeFunction` 直接调用（JavaScriptService L76 嵌套调用分支）不经队列，不受影响但也无守卫（见载体 1 观察项）。缩容路径 `refreshConfig`（L108-115）与全量停机 `doStop`（L126-135）为同族关闭入口，均触发同一窗口。
- **信心水平**: 确定（交错可具体化；搁浅任务无任何兜底完成路径经代码核实）
- **误报排除**: 非原语误用误报——CHM/volatile 单点均正确，缺陷在 check-then-act 复合序列（对照 C1 执行步骤 4 与 F10）；非"有界声明的队列"校准项（此处无界且无策略）；非测试代码豁免面。
- **复核状态**: 已保留（P1）｜新发现——搁浅任务无兜底完成路径经独立核实，syncGet 无显式超时（FutureHelper.java:158-173），详见复核结论节第 2 条。

### [C1-003] NopProjectService 以项目级生命周期驱动全局注册表：多项目开窗/关窗交叉销毁另一项目的 XLang 全局状态

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/services/NopProjectService.java:44-58, 75-78`
- **证据片段**:
  ```java
  private synchronized void init(Project project) {      // 每项目一份 service 实例，各 init 一次
      if (inited) { return; }
      inited = true;
      ProjectEnv.withProject(project, () -> {
          XLangCoreInitializer xlang = new XLangCoreInitializer();
          xlang.initialize();                            // 写全局静态注册表
          cleanup.appendOnCancelTask(xlang::destroy);    // 项目 dispose 时全局反注册
          ...
  ```
- **严重程度**: P2
- **并发场景**: 非单点数据竞争，而是共享全局可变状态（EvalGlobalRegistry/XPathProvider/EvalExprProvider/JaninoScriptCompiler 全局静态注册表，见 `nop-xlang/.../initialize/XLangCoreInitializer.java:46-86`）的生命周期错配：每个打开的项目各执行一次 initialize，任一项目 dispose 时 destroy 全局反注册。
- **触发时序**: 用户在 IDE 同进程打开项目 P1、P2（IntelliJ 多项目窗体共存）→ P1、P2 各自 init，先后向同一全局注册表重复注册/覆盖 XPathProvider 等实例 → 用户关闭 P1 → `dispose()` → `xlang.destroy()` 反注册全局静态函数/XPath/表达式设施 → P2 的补全、字典求值、xlib 加载所依赖的全局设施被拆走，且 P2 的 `inited` 已置位不会重建 → P2 功能静默降级直至重启 IDE。
- **现状**: 项目级 Disposable（`cleanup`）持有对进程级注册表的注销权；注册计数与反注册作用域未区分 app 级与 project 级。
- **风险**: 多项目场景下先关闭任一项目即破坏其余项目的 XLang 支撑面，且为静默失败（无日志告警）；单项目场景 register/destroy 配对正确，缺陷仅在共享进程的多项目交错时显形。
- **建议**: 区分层级：app 级注册（NopAppListener 已有先例）只做一次并挂 app 生命周期；project 级仅注册 per-project 委托体（`registerXlib` 经 `ResourceComponentManager.instance()` 委托当前项目即是正确形态）。或为全局注册引入引用计数，最后一个项目 dispose 时才反注册。
- **失效面枚举**（F3 同族清扫）: 单元内全部"局部生命周期 × 全局注册表"使用点——`NopAppListener.appFrameCreated`（app 级一次，配对无问题，安全）、`XLangDebuggerInitializer.initialize/destroy`（registerGlobalExecutor 与 restore 严格配对且 register 为末步，安全）、`NopProjectService.registerXlib`（委托 per-project manager，安全）、`ProjectDictProvider/ProjectResourceComponentManager`（无注册状态，安全）——同族 4 处中仅本处错配。
- **信心水平**: 很可能（错配结构经代码确证；实际后果依赖 IntelliJ 多项目共存场景与 XLangCoreInitializer.destroy 的完整反向清单，需复核轮实测）
- **误报排除**: 非平台"约定大于配置"项（无任何文档声明单项目约束）；非 NopIoC 单例惯例误报（这是插件自建生命周期，不涉 bean 作用域）；`synchronized init` 本身正确，立案点是注册作用域而非守卫。
- **复核状态**: 已保留（P2）｜新发现——错配结构独立核实；附精确化注记：destroy() 实际反注册集不含 XPathProvider/EvalExprProvider（二者未挂 cleanup，见复核结论节第 3 条）。

### [C1-004] ProjectFileHelper 静态缓存以 Project 为键且无移除路径：关闭的项目对象被静态 Map 强引用滞留（IDE 进程级缓慢泄漏）

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/utils/ProjectFileHelper.java:49, 140-144, 170-187`
- **证据片段**:
  ```java
  private static final Map<Project, CachedValue<Collection<String>>> nopVfsPathCaches =
          new ConcurrentHashMap<>();                     // L49 全仓唯一写入点为 computeIfAbsent
  ...
  return caches.computeIfAbsent(project, (p) -> {        // L174 只进不出
      return CachedValuesManager.getManager(p).createCachedValue(...);
  }).getValue();
  ```
- **严重程度**: P2
- **并发场景**: 任意工作线程（annotator/completion/调试器均有调用）读缓存的插件进程长驻静态 Map；IDE 项目反复打开/关闭是常态操作。
- **触发时序**: 项目 P 打开 → 首次调用 `getCachedNopVfsPaths` → computeIfAbsent 以强引用写入 P 及其 CachedValue → 用户关闭 P（IntelliJ dispose Project，期望可 GC）→ 静态 Map 条目仍强引用 P → P 及其整个服务图（含 NopProjectService、ResourceComponentManager 及缓存模型）不可回收 → 用户重开同路径项目生成新 Project 实例再入一条。每次开关项目净增一条不可回收条目。
- **现状**: CHM 原语使用正确、CachedValue 依赖失效机制正确；缺陷是键对象（Project）为可 dispose 生命周期对象却无对称移除（对照 F8 清理不对称 / F4 只增不删，量级受"历史打开过的项目数"约束，属缓慢单调增长）。
- **风险**: 长时运行的 IDE 会话中反复开关项目导致堆持续增长（每条目一个完整 Project 服务图）；CachedValue 内缓存的 vfs 路径集合也一并滞留。另存在语义面：IDEA 平台对插件持有已关闭 Project 强引用有明确的内存泄漏判定惯例。
- **建议**: 按平台惯例改造：改用 `ProjectManager.getInstance(project)` 级消息订阅在 project closed 时 `map.remove(p)`；或以 `CachedValuesManager` 项目级键替代自建静态 Map；最简方案为 `MessagingContext`/`ProjectCloseListener` 钩子补 remove。
- **失效面枚举**（F3 同族清扫）: 全单元 Project/项目级键静态 Map 仅此一处（grep `static ... Map` 全量核对：`PsiClassHelper.primitiveTypeWrapper` 为 static final `Map.of` 不可变键集，安全；`ConditionExprHelper` 两表无键对象滞留，安全；demo 静态面见载体 2 表）。同仓库形态先例（平台其他模块的 Project 键缓存）不在本单元，不展开。
- **信心水平**: 确定（无 remove 路径经全仓 grep 确证；泄漏后果为平台已知反模式）
- **误报排除**: 非"CHM 使用即误报"——立案点是生命周期配对缺失而非容器选型；非无界集合校准豁免（该 Map 无上限且键为重型生命周期对象，非"有界声明存在"）；量级未夸大：增速为每项目开关一次一条。
- **复核状态**: 已保留（P2）｜新发现——无 remove 路径经独立 grep 复现（全文件仅 L49/L141 两处出现）；跨维度与 C6-003 同站点，合并裁定归并防双计，详见复核结论节第 4 条。

### [C1-005] XLangBreakpointHandler.muted 非 volatile：EDT 写、后台 ReadAction 线程读，断点静默状态可过期发送

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/debugger/XLangBreakpointHandler.java:42, 51-57, 119-129`
- **证据片段**:
  ```java
  private boolean muted;                                  // L42 非 volatile
  public void breakpointsMuted(boolean muted) {           // L51 —— XDebugSession listener，EDT
      this.muted = muted;
      ...
  }
  public void sendBreakpoints() {                         // L119
      IDebuggerAsync debugger = debugProcess.getDebugger();
      if (debugger != null) {
          List<Breakpoint> bps = new ArrayList<>(bpMap.values());
          debugger.updateBreakpointsAsync(bps, muted)     // L123 —— 连接建立时在后台 ReadAction 线程调用
  ```
- **严重程度**: P3
- **并发场景**: 写者：EDT（`XLangDebugProcess` session listener `breakpointsMuted` 回调）；读者：`startDebugSession` 的 `ReadAction.run(myBreakPointHandler::sendBreakpoints)`（Backgroundable 线程）与后续 EDT 上的 register/unregister。
- **触发时序**: T_bg（连接建立）执行 sendBreakpoints 读 `muted` → 此时 T_edt 尚未发生同步写或写后未建立 happens-before（字段非 volatile，无锁交接）→ T_bg 读到过期值，将错误的 muted 状态随 `updateBreakpointsAsync` 发往远端调试器 → 远端按过期静默位决定是否在断点处停住。
- **现状**: 单字段可见性缺陷；初连窗口（startDebugSession 与用户静默操作交叠）之外，register/unregister 与 breakpointsMuted 同在 EDT 顺序执行，无竞态。
- **风险**: 连接建立瞬间的静默状态不一致——用户已静默的断点仍可能命中（或反之），调试行为与 UI 指示短暂背离；一次 update 周期后自愈，无结构性后果。
- **建议**: `muted` 声明为 volatile（同文件 `XLangDebugProcess.debugger` 已是该惯例）；或 sendBreakpoints 统一经 EDT/锁内读取后传值。
- **失效面枚举**（F3 同族清扫）: 本类非 final 字段仅 `muted` 一处（`breakpoints/bpMap/debugProcess` 均为 final/CHM，安全）；同目录 `XLangDebugProcess` 的对应状态 `debugger` 已 volatile——同族兄弟已按正确惯例书写，唯独本字段遗漏。
- **信心水平**: 确定（竞态存在）；后果轻微（可具体化但窗口极窄、单次自愈）
- **误报排除**: 非"理论竞态无时序"——写读线程身份（EDT 写 / ReadAction 读）经调用链确证，时序已具体化；按判级校准窗口窄、单次自愈故定 P3 而非 P2。
- **复核状态**: 已保留（P3）｜新发现——EDT 写者链（XLangDebugProcess L78-80）与 ReadAction 读者链（L167→L123）独立核实，详见复核结论节第 5 条。

### [C1-006] runToPosition 临时断点检查-清除非原子：多线程同时命中导致一次 runToPosition 停住多个线程

- **文件**: `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/XLangDebugger.java:484-512, 236-239`
- **证据片段**:
  ```java
  private boolean matchTempBreakpoint(SourceLocation loc, EvalRuntime rt) {
      Breakpoint bp = this.tempBreakpoint;               // L485 读
      if (bp == null) return false;
      if (bp.getLine() != loc.getLine()) return false;
      if (!bp.getSourcePath().equals(toSourcePath(loc))) return false;
      ...
      tempBreakpoint = null;                             // L503 清除 —— 在判定通过之后
      ...
  }
  ```
- **严重程度**: P3
- **并发场景**: 两个及以上被调试线程并发执行到同一临时断点行（runToPosition 由 IDE 对单线程发出，但多线程常在同行汇聚，如并行任务的汇聚点）。
- **触发时序**: T1、T2 同时执行 L485 均读到非 null 的 tempBreakpoint → 各自通过行/路径匹配 → 各自执行 L503 置 null（幂等）→ 各自返回 true → `checkTempBreakpoint` 对两个线程各构造一次挂起并 doSuspend——runToPosition 语义（"运行到该处停一次"）被放大为多线程齐停。
- **现状**: 一次性标志的读-判-清为三步非原子复合操作（对照 C1 执行步骤 4）。
- **风险**: 调试器行为偏离用户预期（多余线程停住）；无崩溃/数据后果，resume 一次即全部恢复。
- **建议**: 以 `AtomicReference.compareAndSet(bp, null)` 或在同步块内完成"读-判-清"，仅让 CAS 成功的线程挂起。
- **失效面枚举**（F3 同族清扫）: 同类"易失单次标志"仅 `tempBreakpoint` 一处；`lastSuspendThread`（L150/160/170）读后仅判空不发清除语义，无双停同族；`checkHitBreakpoint` 的 lastLoc 同行抑制（L451）为 per-thread 状态，无跨线程共享。
- **信心水平**: 确定（交错平凡可复现）；后果限于调试体验
- **误报排除**: 非"volatile 即安全"误报——字段已 volatile 但复合操作仍非原子，正是 C1 步骤 4 的定向形态；非生产数据面（调试器一次性断点），故 P3。
- **复核状态**: 已保留（P3）｜新发现——读 L485/清 L503 非原子窗口独立核实，详见复核结论节第 6 条。

### [C1-007]（demo 降口径）DemoServiceBizModel.testTcc 对 RESOURCE_STORE 的检查-行动非原子：并发同资源双重预留；且静态存储无界只增

- **文件**: `nop-demo/nop-spring-gateway-demo-service/src/main/java/io/nop/demo/gateway/service/DemoServiceBizModel.java:28, 40-46, 69`
- **证据片段**:
  ```java
  private static final Map<String, String> RESOURCE_STORE = new ConcurrentHashMap<>();  // L28
  ...
  String currentState = RESOURCE_STORE.get(resourceId);            // L40 检查
  if ("reserved".equals(currentState)) {
      throw new IllegalStateException("Resource is already reserved: " + resourceId);
  }
  RESOURCE_STORE.put(resourceId, "reserved");                      // L46 行动 —— 与检查非原子
  ```
- **严重程度**: P3（demo 降口径：演示面不按生产标准判级，本条为结构性并发缺陷故立案）
- **并发场景**: BizModel 单例服务的多个请求线程并发调用 `testTcc`，同 resourceId（同 txn:branch）并发到达。
- **触发时序**: T1、T2 同时执行 L40 读到 null（或非 reserved）→ 双双通过 L41 检查 → 双双 L46 put 成功返回"reserved successfully" → 幂等/防重预留守卫被击穿（F10 形态）。
- **现状**: 选用 CHM 说明作者有并发意识，但防重语义依赖 get-then-put 复合操作；此外 `RESOURCE_STORE` 仅在 `cancelTestTcc`（L69）删除，无 confirm/超时清理路径，条目随未取消的事务单调增长（F4 形态，与防重缺陷同源记录）。
- **风险**: demo 演示 TCC 语义时给出错误示范（双预留可通过）；长压演示下静态 Map 无界增长。按降口径不升级、不影响生产判级。
- **建议**: 若保留演示目的，改 `RESOURCE_STORE.putIfAbsent(resourceId, "reserved")` 并以返回值判定重复——一行修复同时消除两个结构性问题；或注明该 demo 仅单线程演示。
- **失效面枚举**（F3 同族清扫）: 本类仅此一个共享载体（`test` 无状态、`cancelTestTcc` 的 remove 幂等）；demo 单元其余静态面（`DeltaDemoMain.globalArgs`、`QuarkusDemoMain.globalArgs`）为启动期 main 单线程写，非同族。
- **信心水平**: 确定
- **误报排除**: 非"CHM 即线程安全"误报——正是复合操作误用（本领域误报校准第 2 条的反例形态）；按 demo 降口径判 P3 而非生产口径的 P1/P2，符合批次声明。
- **复核状态**: 已保留（P3，demo 降口径维持）｜新发现——L40-46 非原子、L69 唯一删除点、类内无 confirm 方法均独立核实，详见复核结论节第 7 条。

---

## 三、维度集外强信号线索（CL 附记）

### [nop-peripheral-CL-001]（C7/C5 形态，强）JavaScriptWorker 死亡无监督：init 失败即成"死 worker"，调度黑洞 + 队列无限积压

- **文件**: `nop-frontend-support/nop-js/src/main/java/io/nop/js/engine/JavaScriptWorker.java:277-316`；`nop-frontend-support/nop-js/src/main/java/io/nop/js/engine/JavaScriptService.java:67-93`；`nop-frontend-support/nop-web/src/main/java/io/nop/web/page/DynamicJsLoader.java:127-131`
- **线索**: `run()` 中 `this.init()`（装载初始化脚本）抛错时 worker 线程直接退出（finally 置 closed 但仅对该 worker），`JavaScriptService.invokeAsync` 仍按 `getWaitingTasks()` 最小队列把后续任务全部路由到死 worker（L78-92 无存活检查），任务无限积压、future 永不完成；已证实调用方 `DynamicJsLoader` 以 `FutureHelper.syncGet` 无超时阻塞等待。属 C7（挂死对调用方不可见，F5）与 C5（无界积压）复合形态，且有"一个 worker 死→全量流量压向它"的放大结构。
- **建议方向**: worker 存活探测 + 故障转移/重建（对照 F5 看门狗）。
- **状态**: 已复核确认（独立核实 run L276-316 / invokeAsync L78-92 / syncGet L131；放大表述修正见复核结论节 CL-001 条）｜新发现线索，去向 C7/C5，供 7.1 聚类。

### [nop-peripheral-CL-002]（C5/C8 形态，强）JavaScriptWorker.taskQueue 为无界 LinkedBlockingQueue，无溢出策略

- **文件**: `JavaScriptWorker.java:54`（`new LinkedBlockingQueue<>()`）；`execute()` 无任何容量/背压判断
- **线索**: 平台已有 `IBlockingQueue`/`QueueOverflowPolicy`（容量+溢出策略）未使用；叠加 CL-001 的死 worker 路由后积压无上界。与 C1-002 同一根因簇但属容量治理问题，归 C5/C8。
- **状态**: 已复核确认（L54 无界队列独立核实）｜新发现线索，去向 C5/C8；与 WI 2.1 C4-003（newExecutor 参数零校验，JavaScriptService.java:40 为其 F3 缺陷面成员，已知未修复）家族关联防双计。

### [nop-peripheral-CL-003]（C6/C7 形态，中强）XLangDebugger 清理为一次性调度：30 秒后死线程条目（含 EvalRuntime 全量引用）永不再清

- **文件**: `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/XLangDebugger.java:72-73, 80, 94-103`
- **线索**: 构造器 `cleanupThread.schedule(this::cleanup, 30, SECONDS)` 为**一次性**调度（非 scheduleAtFixedRate）；调试会话常超 30 秒，此后死线程的 `SuspendedThread`（强引用整个 `EvalRuntime`/作用域树）滞留 `suspendedThreads` 直至 `close()`。泄漏有界（受调试线程数约束）但清理意图明显未实现，属 C6 清理不对称/C7 单调增长形态。
- **状态**: 已复核确认（L80 一次性 schedule 独立核实）｜与 C6 维度 C6-002 同站点同缺陷，按重复关闭，处置归 C6-002 防双计。

### [nop-peripheral-CL-004]（C3/C9 形态，弱）DynamicJsLoader 在资源加载路径同步等待 JS transform future

- **文件**: `nop-frontend-support/nop-web/src/main/java/io/nop/web/page/DynamicJsLoader.java:127-131`
- **线索**: `invokeAsync(...)` 后立即 `FutureHelper.syncGet(future)`——异步链同步化，调用线程（资源加载，可能为请求线程）阻塞在 JS 引擎 rollup 转换上；上游若有模型缓存则热度有限（未核实），按 C9 证据标准记弱信号，需 C3/C9 轮核实调用频率与缓存层级。
- **状态**: 已复核确认（弱信号维持，L127-131 独立核实）｜去向 C3/C9，调用频率与缓存层级待核。

---

## 四、终止声明与统计

- **零发现豁免**: 不适用——本维度有 7 条立案。
- **覆盖声明**: 五类载体全单元盘点完成；基线命中 12 文件全部精读；dev-tools 主战场（xlang-debugger/api-debugger/idea-plugin 调试器与资源服务面/maven-shaded-plugin）逐文件核对；frontend 其余 Java 面（nop-web/nop-web-page/nop-css/nop-ui 非生成面）经 grep 标记 + 命中点核对；demo 面按降口径核对全部并发标记文件。
- **统计**: 载体盘点 40 项（判定：安全 31 / 立案 7 / 观察项 2）；立案 7 = P1×1（C1-002）+ P2×3（C1-001/003/004）+ P3×3（C1-005/006/007）；CL 线索 4 条（CL-001 强、CL-002 强、CL-003 中强、CL-004 弱）。全部条目复核状态：未复核。

---

# C1 深挖第 2 轮（追加）

- **轮次任务**: C1-002/C6-001 同族放大——JavaScriptWorker 与 XLangDebugger/XLangDebugConnector 之外的线程协作与 socket/连接资源站点穷举（api-debugger 面、maven-shaded-plugin 进程面、idea-plugin 其余服务、demo 复核）。方法同初审（C1 正文 + 共享前缀五类载体口径）；盲审例外仅限本目录 C1/C6 两份初审文件，其余历史记录未读。
- **编号规则**: 本轮新发现 `C1-R2-001` 起。

## 一、线程协作站点清扫表（第 2 轮穷举）

| 面 | 检查内容 | 判定 |
|---|---|---|
| nop-api-debugger 全部 11 文件（合计 715 行） | 逐文件核对：全部为 Serializable DTO（Breakpoint/StackInfo/ThreadInfo/DebugVariable/BreakpointHitMessage/LineLocation/DebugValueKey/StackTraceElement）+ 纯接口（IDebugger/IDebuggerAsync/IBreakpointManager，CompletionStage 签名无实现状态） | 无线程协作站点、无实例可变共享状态、无资源获取点——**面闭合**（维持初审 C6「纯 DTO/接口」结论） |
| nop-maven-shaded-plugin 进程面 | grep `ProcessBuilder|Runtime.exec|fork`：零命中（仅 demo 两个 Main 读 `maxMemory()` 的 `Runtime.getRuntime()`，非进程创建）；4 文件为 shade 转换器，运行于 Maven Mojo 单线程执行模型；`static { CoreInitialization.initialize(); }` 为全仓标准初始化用法 | 无进程创建面——**面闭合**（资源配对见 C6 初审配对表 #15-17） |
| idea-plugin 其余服务/扩展点 | 逐文件核对实例字段：XLangCompletionContributor、XLangAnnotator、XLangDocumentationProvider、XLangHighlightRangeExtension、ProjectVirtualFileSystem（无私有字段）、XLangDebugExecutor（仅 static final EXECUTOR_ID 声明性扩展点）、NopIcons（static final Icon）、ProjectDictProvider/ProjectResourceComponentManager（无累积状态，初审已覆盖） | 无跨线程可变实例字段——**面闭合**（PSI/EDT/ReadAction 平台线程契约沿用初审惯例校准声明） |
| NopVirtualFile.children（vfs 面） | 非 volatile 惰性缓存（L40 声明、L122-136 惰性求值），PSI 上下文内构造与使用 | 良性竞态（幂等惰性缓存、引用写原子、最坏重算），与初审载体 5 表 SourceLocation 惰性缓存字段同形态——**记表不立案**，若平台收紧 PSI 发布契约此处与 SourceLocation 同为首批受影响调用方 |
| DebugExpressionExecutor（xlang-debugger 桥接面） | 无状态：final debugger 引用 + pass-through `checkBreakpoint`，运行于表达式求值线程 | 安全 |
| PageProvider/WebPageExporter 的 GlobalExecutors.globalWorker + ExecutorHelper.throttleExecute | 平台共享池 + 平台许可管理工具 | 维持 C6 初审配对表 #22 判定（合规，许可释放归 C8 面） |
| ReportController.globalTimer（demo 面） | 单发 5min 延迟任务（L133），无共享可变状态引入 | 无 C1 缺陷；临时文件生命周期侧缺陷见 C6-R2-001 |
| demo 其余并发标记文件 | TccGatewayInterceptorTest 为测试面（非产品审计面）；demo 静态面（DemoServiceBizModel.RESOURCE_STORE、两 Main.globalArgs）复核维持初审载体 2 表判定 | 无新发现 |

## 二、立案（第 2 轮）

### [C1-R2-001] DebugNotifier.sendStack 裸读外层 server 字段与 destroy() 置 null 竞态：停机窗口内断点命中使业务线程 NPE

- **文件**: `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/initialize/XLangDebuggerInitializer.java:93-94, 108-116, 155-169`
- **证据片段**:
  ```java
  // DebugNotifier.sendStack —— 5 个 notify 方法的唯一汇聚点，直接读外层字段，无局部快照判空
  ApiResponse<?> res = ApiResponse.success(message);
  server.broadcast(RpcConstants.CMD_NOTICE, (short) 0, res);      // L116
  ...
  // destroy() —— 同一非 volatile 字段被置 null
  public void destroy() {                                          // L155
      EvalExprProvider.registerGlobalExecutor(DefaultExpressionExecutor.INSTANCE);
      if (server != null) {
          server.stop();
          ...
          debugger = null;                                         // L168
          server = null;                                           // L169
      }
  }
  ```
  同文件 `sendBreakpointNotice`（channel-open 线程入口）的守卫写法对照：
  ```java
  XLangDebugger debugger = this.debugger;                          // L92 局部快照
  if (debugger == null || !debugger.isSuspended()) return;
  SimpleRpcServer server = this.server;                            // L93 局部快照
  if (server == null) return;                                      // L94 判空后才使用
  ```
- **严重程度**: P3
- **并发场景**: 写者：停机线程执行 `CoreInitialization.destroy()` → 本初始化器 `destroy()`；读者：被调试应用的 HTTP/RPC 工作线程命中断点/单步，经 checkXXX → `notifier.notifyXxx` → `DebugNotifier.sendStack` 裸读外层 `server` 字段。字段非 volatile、无锁、无局部快照，读写线程间无 happens-before（启动期发布由 Thread.start 链覆盖，停机期无任何同步）。
- **触发时序**: T1（工作线程）在 checkHitBreakpoint（XLangDebugger L383-385）进入 `notifier.notifyBreakAt` → `sendStack`，执行到 `getfield server` 之前 → T2（停机线程）`destroy()` 执行 L169 `server = null` → T1 读到 null → `server.broadcast` 抛 NPE → 异常沿 checkXXX → `DebugExpressionExecutor.execute` 传播进被求值的业务表达式，该请求以裸 NPE 失败、suspend 通知丢失。通知点先于 doSuspend 的标志写入（初审 C1-001 已核实的 L384→doSuspend 顺序），挂起状态未半置，无幽灵状态残留。
- **现状**: 同类内两处对外层 `server` 的跨线程读取，`sendBreakpointNotice` 用「局部快照 + 判空」守卫（L92-94），`sendStack` 裸读无守卫——同文件同字段两种写法并存，属守卫遗漏而非平台惯例。
- **风险**: 窗口为「调试器开启 + 停机瞬时有在途请求命中断点」的交叠；后果为单个业务请求以无错误码的 NPE 失败 + 一次通知丢失，不破坏状态、不泄漏资源、不可积累。按初审 C1-005/C1-006 校准（调试器面、可具体化但后果轻微、单次自愈）判 P3，而非「罕见但可具体化竞态」的 P1——该判级要求竞态有功能性后果，本条发生时请求本就处于停机丢弃期。
- **建议**: `sendStack` 复制同文件 `sendBreakpointNotice` 的守卫形态：方法首行 `SimpleRpcServer s = server; if (s == null) return;`，后续用局部引用 broadcast；如需同时覆盖可见性，可将 `server`/`debugger` 声明为 volatile（与 idea-plugin 侧 `XLangDebugProcess.debugger` 惯例一致）。
- **失效面枚举**（F3 同族清扫）: DebugNotifier 全部 5 个活跃通知方法 notifySuspend/notifyStepInto/notifyStepOver/notifyStepOut/notifyBreakAt（XLangDebugger 调用点 L372/384/395/416/437/466）全部经 `sendStack` 单点汇聚，同一暴露、无例外兄弟；notifyLog 在 DebugNotifier 中为空实现，不受影响；`sendBreakpointNotice` 已守卫（安全）；外层 `debugger` 字段的唯一读取点（sendBreakpointNotice）已用局部快照（安全）。同单元其他线程桥接点（DebugExpressionExecutor、XLangDebuggerInitializer.server/debugger 发布）初审已核安全。
- **信心水平**: 确定（交错序列可完全具体化；同文件双写法不对称经逐行核对）
- **误报排除**: 非「看到共享字段即报线程不安全」——读路径必有 NPE 后果且时序完全具体；非「停机路径粗同步合理」校准项——修复为一行守卫且同文件已有正确形态，非优雅性问题；非平台基础设施重审（本类为本单元自有代码）。
- **复核状态**: 已保留（P3）｜新发现——守卫不对称独立核实；行号勘误：守卫快照实际在 L89（初审标注 L92），其余行号全部准确，详见复核结论节第 8 条。

## 三、终止声明（第 2 轮）

- **清扫终止信号**: 五类载体 + 本轮四个放大面（api-debugger / maven-shaded-plugin 进程面 / idea-plugin 其余服务 / demo 复核）全部闭合，单元内无未盘点线程协作面残留；本轮唯一新发现为 P3 单点守卫遗漏，失效面枚举已收敛到 `sendStack` 单点，无同族放大空间。
- **轮次终止信号**: 初审 7 条 + 本轮 1 条之后，继续第 3 轮的预期增量为零——建议 C1 维度深挖迭代在此终止，进入独立复核阶段。全部条目（C1-001~007、C1-R2-001、CL-001~004）复核状态：未复核。

---

# C1 维度复核结论（独立复核）

- **复核方式**: 独立复核子代理对 C1 全部 8 条发现 + 4 条 CL 线索逐条重读源码核实；以下 file:line 均为复核者独立打开 live code（worktree @ concurrency-audit-remediation）读取/grep 所得，非转抄初审。现状标注经独立查证：2026-09 全仓审计（`2026-09-30-1726-deep-audit-nop-entropy-full/` 全部 13 文件）对 nop-demo/nop-frontend-support/nop-dev-tools/nop-entropy-e2e 四模块**零提及**（grep 证实）；cra-index 无 WI 6.5/nop-peripheral 条目；campaign 各单元提及 `XLangDebugConnector`/`SimpleRpcClientFactory`/`JavaScriptService.java:40` 者均为**设施侧不同缺陷面**（WI 4.3 C1-005 回调丢失、WI 2.1 CL-024/C4-003 工厂 init 非幂等与 newExecutor 校验缺失），与本维度立案面无同站点重复。
- **P0 候选**: 无。C1-002 为最重条目，其触发窗口依赖停机/缩容与在途提交的交叠，非生产主路径必然时序，P1 判级恰当。

## 逐条核实结果

1. **C1-001（P2）成立，保留**。独立核实：`XLangDebugger.java` doSuspend L472-482（per-thread 置位 L478 → 全局 `suspended=true` L479 → monitorWait L481）；monitorWait L534-556 的守卫读在 L535、`thread.setSuspended(false)` 复位仅在 L548-551 的 finally 内；monitorNotify L195-204 在 L197 清 `suspended`、L198-203 锁内 signal——signal 先于等待者进入则丢失。T_dbg 越过 L479 后被抢占、T_ui 完成 monitorNotify、T_dbg 在 L535 读到 false 的交错序列成立；且 5 个通知点（L372/384/395/416/437/466）全部先于 doSuspend，IDE 收到 suspend 通知后发起 resume 恰好系统性对准该窗口，非边角时序。维持 P2 而非 P1 的裁量理由：后果限于调试会话（幽灵挂起项 + eval 与运行线程对 `SuspendedThread.rt` 的读写并发——`update` 为 synchronized L63、eval 侧 `synchronized(thread)` L281-285 已核实，CME 面真实但影响面收敛），且该线程下次真实挂起即自愈。
2. **C1-002（P1）成立，保留**。独立核实：`JavaScriptWorker.java` execute 检查 L99 与入队 L102 非原子；run 循环优雅退出 L297-300、finally L309-315 置 `closed=true` 后 cancelAllTasks（L311）最后一次 poll 为 null；`schedule()` L121-130 返回的 future 无任何兜底完成路径；`close()` L109-119 的关闭任务同样可搁浅（context 由 finally L312 兜底关闭，业务 future 无兜底——与初审一致）。调用方阻塞面独立核实：`DynamicJsLoader.java` L129 invokeAsync → L131 `FutureHelper.syncGet(future)`（`FutureHelper.java:158-173` 委托 context syncGet，调用点无显式超时）。JavaScriptService 侧 L67-93（invokeAsync 路由）、L108-116（refreshConfig 收缩）、L126-135（doStop）全部核实。P1（停机窗口在途调用永久挂起）恰当。
3. **C1-003（P2）成立，保留（附精确化注记）**。独立核实：`NopProjectService.java` init L44-58（`inited=true` L48、`xlang.initialize()` L52、`cleanup.appendOnCancelTask(xlang::destroy)` L53）、dispose L75-78；plugin.xml L93 `<projectService>`（每项目一实例）。`XLangCoreInitializer.java`（实际路径 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/initialize/`）initialize L46-65 写进程级全局（EvalGlobalRegistry L47-49、XPathProvider L51、ValueResolverCompilerRegistry L53、EvalExprProvider L58-60、Janino L62、ResourceComponentManager L81），destroy L85-88 经 cleanup.cancel 反注册。**精确化注记**：L51 XPathProvider.registerInstance 与 L58-60 EvalExprProvider 注册**未挂 cleanup**，destroy() 实际反注册集 = 全局静态函数 + expr resolver compiler + XDEF 组件配置 + Janino——P2 的核心错配（项目级 Disposable 持进程级注册表注销权）与 P2 功能受损结论不变，但「XPath/表达式设施被拆走」的枚举偏宽，修复设计时以实际反注册集为准。
4. **C1-004（P2）成立，保留**。独立核实：`ProjectFileHelper.java:49` 静态 CHM 声明；全文件 `nopVfsPathCaches` 仅 L49/L141 两处出现，唯一写入为 `getCachedSearchResult` L174 computeIfAbsent，**无任何 remove**（grep 全 nop-dev-tools 证实）。F3 清单独立复核成立：idea-plugin 主源码 static Map 仅此一处可变键（`PsiClassHelper.java:71` primitiveTypeWrapper 为 `static final Map.of` 不可变，安全）。泄漏链（Project dispose 后被静态强引用滞留）成立，P2 恰当。
5. **C1-005（P3）成立，保留**。独立核实：`XLangBreakpointHandler.java:42` 非 volatile `muted`；写者链 EDT——`XLangDebugProcess.java:78-80` session listener → `breakpointsMuted`；读者链 Backgroundable 线程——`sessionInitialized` L108-123 → `startDebugSession` L165-168 `ReadAction.run(myBreakPointHandler::sendBreakpoints)` → L123 读 muted。同文件 final/CHM 兄弟字段（L40/41/44）与 `XLangDebugProcess.debugger` volatile（L58）核实，F3 成立。窗口窄、单次自愈，P3 恰当。
6. **C1-006（P3）成立，保留**。独立核实：`XLangDebugger.java` matchTempBreakpoint L484-512——读 L485、判定通过后 L503 置 null，读-判-清三步非原子；runToPosition L235-239。双 debuggee 线程同行汇聚即双停，幂等 null 写不阻止。P3（调试语义偏离、无数据后果）恰当。
7. **C1-007（P3，demo 降口径）成立，保留**。独立核实：`DemoServiceBizModel.java` L28 static CHM；L40-46 get-then-put 非原子（结构性 F10）；L69 唯一删除点在 cancelTestTcc，**类中无 confirm 方法**，未取消条目单调滞留。降口径 P3 恰当。
8. **C1-R2-001（P3）成立，保留（附行号勘误）**。独立核实：`XLangDebuggerInitializer.java` 字段 L41-42 非 volatile；`DebugNotifier.sendStack` L116 裸读外层 `server` 无快照判空；destroy L154-171 在 L168/L169 置 null；对照方法 `sendBreakpointNotice` 的守卫快照实际在 **L89**（`XLangDebugger debugger = this.debugger`）与 **L93-95**（server 快照 + 判空）——初审证据片段标注的「L92 局部快照」应为 **L89**，其余行号（L93/L94/L116/L168/L169）与调用点清单（L372/384/395/416/437/466）经逐行核对全部准确。同文件双写法不对称成立，P3 恰当。

## 抽样安全判定失败数

抽样 8 项「安全/记表」判定复核：① BreakpointManagerImpl breakpoints CHM + per-map synchronized 全 4 访问点（L59/69/78-81/99-101）锁内成立；② NopProjectService.inited synchronized 检查-置位（L44-48）原子成立；③ SuspendedThread.update synchronized（L63）+ suspended volatile（L38）成立；④ ProjectEnv ThreadLocal finally 恢复前值（L17-25）成立；⑤ JavaScriptService.workers volatile 数组整体替换（L22，doStart 先于 execute 发布）成立；⑥ IndexHtmlProvider GlobalCacheRegistry register/unregister 严格配对（L54-56/L59-62）成立；⑦ XLangSuspendContext myExecutionStacks COW（L25）+ myActiveStack 非 volatile（L24）事实核实，记表不立案裁量合理；⑧ NopVirtualFile.children 惰性非 volatile（L40/L123-127）事实核实，与载体 5 SourceLocation 同形态记表合理。**安全判定失败数：0**。

## CL 裁定清单

- **CL-001 确认（保留线索）**：`JavaScriptWorker.run` L276-316 init 失败即线程退出（finally 置 closed 仅对该 worker）、`JavaScriptService.invokeAsync` L78-92 路由无存活检查、`DynamicJsLoader` L131 syncGet 无超时——三点独立核实。**放大表述修正**：「全量流量压向死 worker」过强——死 worker 首个任务因 waiting==0 被路由后队列恒 ≥1，仅在各活队列非空且其为 least-waiting 时继续吸入任务；核心结论（被路由任务永久搁浅 + future 永不完成 + 无监督）不变。去向：C7/C5 线索保留（F5 看门狗族，与 campaign 既有挂死不可见家族关联），供 7.1 聚类。
- **CL-002 确认（保留线索）**：`JavaScriptWorker.java:54` 无界 LinkedBlockingQueue、execute 无容量/溢出判断，独立核实。去向：C5/C8 线索保留；与 WI 2.1 C4-003（newExecutor 参数零校验，同单元 JavaScriptService.java:40 为其 F3 缺陷面成员）家族关联，防双计。
- **CL-003 确认（并入 C6-002，防双计）**：与 C6-002 为**同站点同缺陷**（XLangDebugger.java L80 一次性 schedule，复核独立核实）；C6 维度已作正式立案，本线索按重复关闭，处置归 C6-002。
- **CL-004 确认（弱信号维持）**：`DynamicJsLoader.java:127-131` invokeAsync 后立即 syncGet 独立核实；调用频率与缓存层级未核实，维持 C3/C9 弱信号移交。

## 现状标注汇总

- 8 条发现（C1-001~007、C1-R2-001）经查证**全部为新发现**：2026-09 全仓审计 G 系列对四模块零提及（初审批次判断经 grep 证实，未被推翻）；cra-index 与 campaign 各单元无同站点立案。
- 4 条 CL 线索全部确认；CL-003 与 C6-002 同站点归并，CL-001/002/004 保留为跨维度线索。
- 跨单元 cross-ref 登记 1 处：CL-203（见 C6 复核节）与本维度无冲突。
