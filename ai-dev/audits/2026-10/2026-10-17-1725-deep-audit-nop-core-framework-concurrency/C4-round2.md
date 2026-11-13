# C4 线程池治理 — nop-core-framework 六子模块 + nop-utils（第 2 轮深挖，盲审）

- **审计单元**: nop-core-framework（nop-ioc / nop-config / nop-plugin / nop-security / nop-log / nop-boot）+ nop-utils（全部子模块），src/main 口径
- **仓库路径**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **本轮输入**: 第 1 轮报告 `C4-thread-pools.md`（C4-001~007、CL-008/009、7 目录执行载体全景 11 行、基线 grep 口径）+ live code。判级口径沿用第 1 轮声明的共享前缀 P0–P3 领域校准（框架层「仅启动期单线程触达」条件化降档、门控条件条目内显式注明、写不出具体时序降 P2 并标注理论风险）。
- **盲审纪律声明**: 本报告撰写过程中未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件，**唯一例外**为本维度第 1 轮文件 `C4-thread-pools.md`（任务指定的必要输入）。同目录其他维度文件（C1/C2/C6 等）均未读取。
- **本轮目标（逐条闭环情况）**: ① C4-003 失效面扩展（配置全链 a / 失败吞入形态分类 b / supervisor 后果精确化 c）→ 完成闭环，产出 ①a 配置链、①b 七形态分类表（F1~F7）、①c 两部署族精确化；② globalTimer/globalWorker 消费方分类终表 → 完成（本单元消费点与第 1 轮全景完全一致，无新站点）；③ TaskExecutionGraph 治理面（并发度/节点失败/cancelToken 终判）→ 完成终判，含两条正面核实与一处第 1 轮结论修正；④ 关闭序治理（销毁顺序矩阵 + CL-008 边界）→ 完成，产出矩阵并立案 C4-010/C4-011；⑤ 线程工厂/裸线程抽验 4 行 + 补漏 → 完成，零新站点。

---

## ① C4-003 失效面扩展（concurrent-start 门控族）

### ①a concurrent-start 配置项全链

| 环节 | 内容 | 实测位置 |
|------|------|---------|
| 配置项名 | `nop.ioc.app-beans-container.concurrent-start` | `IocConfigs.java:28` |
| 类型/默认值 | Boolean / **false**（门控关闭） | 同上 |
| `@Description` 声明 | **"是否初始化IoC容器" —— 与语义不符**（该描述实为 `nop.ioc.enabled`（L21）的语义，疑复制粘贴残留；对本项真实语义"是否并行启动 bean"零描述，对失败语义/异步性零提示） | `IocConfigs.java:27` |
| 消费链 1 | `AppBeanContainerLoader.loadAppContainer` L78：`builder.concurrentStart(CFG_IOC_APP_BEANS_CONCURRENT_START.get())` —— **仅 app 容器路径读取该配置**；`loadFromResource`（测试/编程式装载）不读取 | `AppBeanContainerLoader.java:78` |
| 消费链 2 | `BeanContainerBuilder` L69（primitive boolean，默认 false）→ L354 `container.setConcurrentStart(...)` | `BeanContainerBuilder.java:69,354` |
| 消费链 3 | `BeanContainerImpl.concurrentStart` 字段 → `start()` L502 分支（asyncStartBeans vs 同步启动） | `BeanContainerImpl.java:88,502` |
| docs-for-ai 声明 | **零命中**（`grep -r "concurrent-start\|concurrentStart" docs-for-ai` 无结果）——平台使用文档未收录该配置项 | — |
| 兄弟配置 | `nop.ioc.app-beans-container.start-mode`（DEFAULT/ALL_LAZY/ALL_EAGER，`IocConfigs.java:24-25`）与 concurrent-start 正交组合：ALL_LAZY 下 startBeans 仅含 delayMethod/forceInit bean，图规模缩小 | `AppBeanContainerLoader.java:80-84` |
| 文档声明缺陷归属 | `@Description` 误标 + docs 零收录 + 失败语义（C4-003）无任何文档提示，三重文档债并入 C4-008 建议项（治理面修复），不单独立案 | — |

### ①b 启动失败吞入路径全部形态（handleStartError 异常分类终表）

对 `asyncStartBeans` 提交的图任务体（`getBean0` 构造+注入+init）逐异常类别追踪传播链后的**七形态终表**：

| 形态 | 触发 | 传播路径 | 容器终态 | 可见性 | 判定 |
|------|------|---------|---------|--------|------|
| F1 Exception | bean init/构造器抛 Exception | `TaskExecutionGraph.runTask` catch(Exception) → `completeExceptionally` → allOf → whenComplete → `handleStartError`（LOG.error + stop()） | running=false / started=false（已停） | 容器日志一条 `nop.err.ioc.start-failed`；进程面 run-success | C4-003 已立案（本轮 ①c 精确化其进程后果） |
| F2 Error | bean init 抛 Error（OOM / StackOverflowError / AssertionError / 类初始化失败） | runTask **catch(Exception) 不接 Error** → 逃逸 `executor.execute` lambda → worker 线程死亡（NamedThreadFactory 挂 NopThread.DEFAULT_EXCEPTION_HANDLER，LOG.error `nop.commons.uncaught-exception` 后线程被池重建）→ **future 永不完成** | **limbo**：running=true / started=false，handleStartError 不调用，已建 bean 不销毁 | 无容器级错误日志（仅 uncaught handler 一条 tid 日志）；getBean 仍可用（checkStarted L620 因 running=true 通过）——半可用死容器 | **新立案 C4-008 形态(a)** |
| F3 依赖缺失 | depends 指向被禁用/父容器/可选模块 bean | `fillResolvedDepends` L151 注释明示"缺失的目标不进 resolvedDepends"（normalizeBeanId null 即跳过）；depends 指向 lazy 等未入图 bean 时 `waitPrevTasks` retainAll(futures.keySet()) 后为空 → 降级为无依赖任务，顺序由 getBean0 内联创建保证 | 无失败 | 无 | **核实为无缺陷**（防第 3 轮重查） |
| F4 超时 | bean init 挂死（阻塞 I/O / 锁等待） | **全链无超时**：图级无、任务级无、`awaitStartFinished`→`FutureHelper.syncGet` 无（L158-174 无超时重载路径） | async 模式：图永久 pending → limbo；sync 模式：启动主线程永久阻塞（可见） | async 不可见；sync 可见（启动挂死） | **新立案 C4-008 形态(c)**（syncGet 无超时第 1 轮已记 F5 潜伏，本轮并入形态分类） |
| F5 成功回调异常 | `runLazyProperties()`/`runDelayMethod()`（whenComplete **success 分支** L507-508，内含用户 delay 方法 `checkBeanDelayActionRun`）抛异常 | 异常逃逸 whenComplete → startFuture（= whenComplete 返回阶段）异常完成，**无任何消费者、无任何日志**；handleStartError 不在 success 分支被调用 | started **永不翻转**，bean 全部已建、图已完成 | **完全静默**（零日志）——第三种 limbo | **新立案 C4-008 形态(b)** |
| F6 声明序违约 | dependsOn/ioc:before/ioc:after 环或高阶依赖 | `BeanTopologySorter.sortBeans`（allowLoop=false 时 `ERR_IOC_BEAN_DEPENDS_GRAPH_CONTAINS_CYCLE`）+ `verifyOrderConstraints`（**无条件执行**，L107-137，`ERR_IOC_BEAN_ORDER_CONSTRAINT_VIOLATED`） | 构造容器即抛 → start() L519 catch → handleStartError + **rethrow** → initialize 失败 | 进程启动失败（fail-fast 正确） | 正常（核实） |
| F7 图结构异常 | Dag 不可达节点 | `DagAnalyzer.checkStartReachable` 抛 `ERR_GRAPH_NODES_NOT_REACHABLE` → `graph.analyze()`（asyncStartBeans L555，start() try 块内）→ 同步上抛 | 同 F6（同步失败） | 正常 | 正常（核实） |

**F2/F5/F4-async 三形态的共同结构性根因**：whenComplete 注册点（L503-512）是失败/完成语义的**唯一汇聚点**，而它 (1) 依赖图 future 必然完成（F2 违约）、(2) success 分支自身无守卫（F5 违约）、(3) 无超时兜底（F4-async 违约）。叠加 `awaitStartFinished` 零调用方（本轮复核：全仓生产代码仅定义点 `BeanContainerImpl.java:536` 一处，唯一调用面为测试 `TestBeanDepends`），三条违约均无二次暴露机会。

### ①c 「进程带死容器 run-success 存活」supervisor 不可见后果精确化

按部署族分两条实测链：

**(1) standalone NopApplication 族**（实测调用方：nop-datav-app L21、nop-file-app L28、nop-wf-app L27、nop-dyn-app L27、nop-cli-core L44/L53 等）：
- `NopApplication.run(args)` 默认 task 为 `() -> 0`（L92-94）→ `CoreInitialization.initialize()` 返回（concurrent-start 模式下此时尚在图启动中）后主任务**立即返回 0** → L84-88 打印 `nop.app.run-success:usedTime=...`（LOG + `CFG_PRINT_SUCCESS_FLAG` 时 stdout）——**run-success 字样可在启动图仍在执行/尚未失败时打印**。
- JVM 存活仅靠 bean 创建的非 daemon 线程；F1 失败 → `handleStartError` → `stop()` → `singletonScope.close()` 销毁全部已建 bean → 其非 daemon 线程退出 → **JVM 以退出码 0 清洁退出**。supervisor 面：进程消失 + exit 0——systemd 缺省 `Restart=on-failure` 下 **exit 0 不触发重启**，且日志最后一条是 run-success，表现为"任务正常完成"。基于日志成功字样/退出码的监督全部失效。
- 端口面：若 HTTP server 为 IoC singleton bean（standalone 应用典型形态），stop() 同步关端口 → TCP 探活可发现（该族唯一有效监督面）；若失败发生在 server bean 创建前，端口从未打开，探活同样发现。**端口探活是该族唯一可靠的死容器检测手段**。

**(2) Quarkus 宿主族**（nop-quarkus-core-starter）：
- `QuarkusIntegration.start()` 仅注册 `NopQuarkusBeanContainer`（Arc 薄包装，L47）；`NopQuarkusBeanContainer.start()/stop()/restart()` 全为**空实现**（L34-46）——HTTP 端口、health 端点（microprofile-health）均由 Quarkus 持有，与 Nop 容器存活状态无任何联动。
- Nop 容器（经 IocCoreInitializer）异步启动失败后：进程存活、端口存活、/health 存活——**三绿状态下全部 Nop bean 不可用**，每个触达 Nop bean 的请求报错。检测只能靠错误率监控，无框架级传导（`awaitStartFinished` 零调用方 + quarkus 侧无 Nop 容器状态探针）。
- 退出码面：Quarkus 进程不会因 Nop 容器死亡退出，exit code 恒不反映。

**精确化结论**：C4-003 的"进程带死容器存活"在 (1) 族表现为 **exit 0 清洁退出伪装成功**（run-success 字样 + 不触发 on-failure 重启），在 (2) 族表现为 **三绿假活**（进程/端口/health 全部健康）。两族的共同根因均为失败不传导至进程失败信号面。

---

## ② globalTimer/globalWorker 消费方分类终表（本单元，修复 plan 直接输入）

全单元 grep（`GlobalExecutors.` / `globalTimer()` / `globalWorker()` / `cachedThreadPool()` / `syncExecutor()`，nop-core-framework + nop-utils src/main）实测 **7 个消费站点，与第 1 轮 11 行全景完全一致，零遗漏、零新站点**。沿三分类口径（直连占线程 / executeOn 自救 / 短任务）终表：

| # | 消费方 | 载体与调用形态 | 任务体性质 | 分类判定 | 关联发现与修复载体输入 |
|---|--------|--------------|-----------|---------|----------------------|
| 1 | `AbstractFileConfigSource` L45 | `globalTimer().scheduleWithFixedDelay` 周期 | `Files.walk` 目录遍历 I/O（NFS/stale mount 挂死面） | **直连占线程**（无 executeOn、任务体无超时） | C4-001 已立案。修复载体三选一已给：executeOn 卸载 / 复用本模块 IConfigExecutor SPI / 任务体超时化 |
| 2 | `JdbcConfigSource` L54 | `globalTimer().scheduleWithFixedDelay` 周期 | 全量 JDBC 查询，`JdbcConfig` 无 queryTimeout/loginTimeout | **直连占线程**（最恶劣：完全无超时的阻塞 I/O） | C4-001 + C4-002 已立案。修复载体同上 + 超时参数补齐 |
| 3 | `BeanContainerImpl` L542 | `TaskExecutionGraph(GlobalExecutors.globalWorker())` 直传 executor | bean 创建 + 用户 init（长任务、阻塞面、用户代码） | **直连占线程**（门控 concurrent-start；启动体与运行期共享 30 worker） | C4-003 已立案，本轮 C4-008/009 扩展其失败/关闭面。修复载体：独立有界池或 executeOn(专用池)（第 1 轮建议(3)） |
| 4 | `ShellRunner` L226 | `globalTimer().schedule` 一次性 | cleanup：关三流 + `process.destroy()`（短任务） | **短任务**（一次性、体轻、finally cancel 闭合） | 正常（第 1 轮行 7 维持，无需修复） |
| 5 | `ShellRunner` L153 | `cachedThreadPool().submit` | stderr readLine 阻塞读（生命周期=子进程） | **直连占线程**（无界共享池上的长驻阻塞占用） | C4-005 已立案。修复载体：虚拟线程 per-task executor（模块已运行 Java 25）或独立小池 |
| 6 | `SingleThreadConfigExecutor` L19 | 自建 `DefaultThreadPoolExecutor("nop-config-executor",1,100,true)` | 配置应用 + 用户 refreshConfig（经 ChangeSubscriptions 内联） | **自建隔离池**（设计语义正确：串行化配置应用；治理缺失：无超时/无慢路径观测） | C4-006（参数纪律）+ C4-007（阻塞搁浅）已立案。修复载体：慢监听器计时告警 + 可选超时实现 |
| 7 | `VirtualThreadTaskExecutor` L17 | 自建 `newVirtualThreadPerTaskExecutor` 并 `GlobalExecutors.register` | 全局注册名 `nop-virtual-thread`，**平台内零消费者** | **自建**（无消费方，治理面缺陷） | C4-004 已立案 + CL-004（无界本体）互引 |
| — | **executeOn 自救范式** | `globalTimer().executeOn(globalWorker())` | — | **本单元零使用**（对照站点全在单元外：nop-cluster `AbstractPollingLeaderElector`/`RetryScannerImpl`、nop-sys `SysDaoMessageService` 等） | 修复 C4-001/002 时引入该既有范式即可，无需新建设施 |

**与 CL-005（configExecutor 串联，C2 轮立案）的接驳面**：#1/#2 的刷新体（globalTimer 线程）→ onChange 回调内联 `requestUpdate` → config-executor（#6）→ `ChangeSubscriptions.trigger` 内联用户代码——三段载体链中 **globalTimer 段是唯一未隔离段**，修复 #1/#2 后该链全线隔离收敛。本表即修复 plan 的站点-载体-发现三元输入。

---

## ③ TaskExecutionGraph 治理面终判（asyncStartBeans 启动图）

**(a) 图的并发度（并行启动多少 bean）**：`BeanContainerImpl` L542 使用 `TaskExecutionGraph` 双参构造（`runPermits=null`）→ **图自身无并发上界**；所有无依赖（root）bean 在 `executeAsync` 内一次性全部提交（`noDepends` 循环）。实际上界 = globalWorker 池：实测 `TaskQueue.offer`（`TaskQueue.java`）为 Tomcat 型弹性扩容——`submittedCount > poolSize` 且 `poolSize < maximumPoolSize` 时 offer 返回 false 触发新建线程 → 爆发提交下池**弹性扩容至 max=30**（`CFG_CONCURRENT_GLOBAL_WORKER_MAX_POOL_SIZE` 默认 30），超出部分入 TaskQueue（容量 10000，拒绝不可达）。**终判：并行启动 bean 数上界 = 30（运行期与 restart 窗口与业务任务共享），非 core=4**。第 1 轮行 1 的"core=4/max=30"参数记载维持，本轮补充弹性扩容语义：max 可达。（TaskQueue 弹性扩容的设施本体问题按预注仅 F3 互引 WI 2.1，此处只固定其对启动图的有效并发结论。）

**(b) 节点失败对整图的影响**：实测传播链——节点任务体异常 → 该节点 future `completeExceptionally` → **全部下游节点经 `waitPrevTasks` 的 whenComplete 在未提交状态即异常级联**（任务体永不执行，fast-fail 正确）；**独立分支不受影响继续执行**，直到容器侧 `handleStartError`→`stop()`→`cancellable.cancel()` 才使队列中未启动任务被 cancelToken 跳过；在途任务跑完。整图 future（endFutures allOf）异常完成。**终判：失败隔离语义正确（下游不执行、全局快速收敛、无僵尸排队）**。

**(c) cancelToken 语义终判（复核第 1 轮"正面"结论）**：
- 机制：`runTask` lambda 开头检查 `cancelToken.isCancelled()`（**仅任务开始时一次，任务体内无检查点**——bean 创建不可中断）；取消后 `future.cancel(false)` → 下游经 CancellationException 级联失败。
- **stop-forever 路径（destroy / 失败后 stop）：正面维持**。实证链：`stop()` 置 running/started=false → 队列任务被跳过 → 在途任务跑完时 `getBean0` → `getBeanScope` 返回已 close 的旧 scope → `BeanScopeImpl.add` L53-58 `synchronized(beans){ checkClosed(); put }` 抛 `ERR_IOC_BEAN_SCOPE_ALREADY_CLOSED`（本轮重读 BeanScopeImpl 确认守卫原子化注释与实现一致）。第 1 轮"核实有守卫"结论**复核成立**。
- **restart 路径：守卫被绕过，第 1 轮结论修正**。`BeanContainerImpl.getBeanScope`（L436-446）读的是容器**当前字段** `singletonScope`——`restart()` = stop()+start() 会创建**新** BeanScopeImpl（L484），旧图在途任务此刻跑完 `getBean0` → 读到**新 scope** → add 成功 → **旧代 bean 混入新代**，BeanScopeImpl 的 ALREADY_CLOSED 守卫对此完全失效（新 scope 未关闭）。叠加旧图 whenComplete 的自毁面 → **新立案 C4-009**。**终判：cancelToken 清理语义「仅 stop-forever 路径正面」；restart 路径不成立**。

**附：跨线程交叉创建死锁假设的排除（本轮深挖中提出并证伪，登记防重查）**。假设链：allow-cycle 默认 true → 环成员并行提交 → 两 worker 各持一个 bean monitor 交叉等待 → 死锁。证伪（三层防线实测）：(1) 显式 dependsOn/ioc:before/ioc:after 环 → `verifyOrderConstraints`（无条件）+ sortBeans 拒绝，**构造期失败**；(2) ref 环 → `fillResolvedDepends` 仅收 `refPos < 自身 topoIndex` 的目标（L173-179），环被位置过滤为**无环偏序**，喂给图的 depends 集合必然无环 → DagAnalyzer 的去环逻辑（loopEdges）对 IoC 启动图不可达；(3) 构造器环 → `getBean0` L394/L412 `isInCreationByCurrentThread` 同线程检测抛 CYCLE。既有回归测试 `TestBeanConcurrentInitDeadlock`（owner+wait/notify 状态机修复后）覆盖的是 BeanCreationContext 代死锁（已修复面），不与本假设冲突。

---

## ④ 关闭序治理：ShutdownHook → CoreInitialization.destroy() 销毁顺序矩阵

**排序机制实测**：initializer 集经 ServiceLoader 装载、`SafeOrderedComparator` 按 `order()` 升序（`CoreInitialization.loadInitializers` L110-124）；`destroy()`（L146-190）按 `CollectionHelper.reverseList` **逆序销毁**，`initializationLevel` 守卫跳过未初始化项，**逐 initializer catch(Throwable) 继续下销**（L163-166，单个销毁失败不阻断后续——正面），cleanups 后清 ReflectionManager/GlobalCache/ResourceTenantManager。`destroy()` 为 static synchronized——hook 线程与显式 destroy 互斥（ShutdownHook 本体的 TreeSet/守卫缺陷属 CL-008，nop-kernel 辖区，维持边界不展开；本单元注册面 `NopApplication` L44-46 单点 `addDisposable(() -> CoreInitialization.destroy())` 复核正常）。

**本单元矩阵**（order 值全部实测）：

| order（初始化升序 → 销毁逆序） | initializer | initialize 内容 | destroy 内容 | 矩阵判定 |
|------|------------|----------------|-------------|---------|
| 2800（INTERNAL） | Logback/Log4j2/JavaLogger Configurator | 日志装配 | 无资源性 destroy | **正确**：最先初始化、最后销毁，全程日志可用 |
| 2840（START_CONFIG） | `ConfigInitializer` → `ConfigStarter` | 配置源装载+刷新注册、config-executor、VFS 注册、i18n/errorcode | `doStop`：cancellable.cancel → applier.deactivate → **configExecutor.stop** → vfs.destroy → configService.stop → `safeCloseAll(closeableSources)`（源最后关） | **正确**：2840 先于 4900 初始化、后于 4900 销毁 → **配置源存活期覆盖容器全生命周期，"配置源先于使用它的 bean 销毁"不成立缺陷**；doStop 内部序（先停消费者 executor 再关源）与第 1 轮 C4-006 的关闭窗口分析吻合（doStop 实际区间 L526-551，第 1 轮记 L529-535 为其子段，维持） |
| 4900（IOC） | `IocCoreInitializer` | 容器装载+start | `container.stop()`：cancelToken cancel + singletonScope.close | **两处缺口**：scope 内销毁无序（→ C4-010）；stop 不 join 在途图任务（→ C4-011） |
| 5000（默认 NORMAL_PRIORITY） | `Jdk21CommonsCoreInitializer`（未覆写 order()） | 注册虚拟线程执行器 | **未覆写 destroy**（接口 default 空）→ 执行器悬空 | C4-004 已立案，维持；本轮补充实测：其 order=5000 → 销毁序在 IOC **之前**（5000>4900 逆序先销），因 destroy 为空无实际影响 |
| — | 插件 | nop-plugin **无 ICoreInitializer**（基线复核零命中）——插件以普通 IoC bean 存活 | 随 singletonScope.close 销毁 | 插件销毁时序受 C4-010 哈希序支配（无插件专用销毁序保障）；`PluginManagerImpl` 零线程（CL-009 终判维持） |

**矩阵结论**：单元级销毁顺序设计（配置源后于容器、日志最后、逐项容错）**基本正确**；缺陷集中在容器内部——scope 销毁序无契约（C4-010）与容器销毁不等在途任务（C4-011），以及 CL-008 边界外的设施面（CL-019/020 登记）。「销毁顺序错误导致 config source 在使用它的 bean 之后销毁」这一具体担忧**实测不成立**（2840/4900 序正确）；成立的是 bean 之间的相对销毁序无契约。

---

## ⑤ 线程工厂/裸线程：抽验 4 行 + 补漏

**补漏 grep**（第 1 轮模式 + 本轮新增 `new NopThread(` / `Thread.ofVirtual` / `new Timer(` / `ThreadFactory` / `newSingleThreadExecutor`）于 7 目录 src/main 重跑：**零新站点**；nop-boot / nop-plugin / nop-log / nop-security 零命中复核成立。11 行全景维持，无需增行。

**抽验 4 行结果**：

| 行 | 抽验内容 | 结果 |
|----|---------|------|
| 行 2（AbstractFileConfigSource L45） | 全文重读：L45 直连 scheduleWithFixedDelay、close()=cancel(false)（L113-117）、refreshConfig catch-all（L54-69 含 JDK 语义注释） | **与第 1 轮一致**；补充事实：构造器内 `this.vars = loadConfig()` 首载同步于构造线程（main，启动期单线程，无并发面） |
| 行 4（SingleThreadConfigExecutor L19） | `newExecutor("nop-config-executor",1,100,true)` 参数一致；`ConfigStarter.doStop` 实际区间 **L526-551**（cancellable.cancel → deactivate → executor.stop → vfs.destroy → configService.stop → safeCloseAll） | **与第 1 轮一致**（L529-535 为 doStop 中段行号，判定不变） |
| 行 5（VirtualThreadTaskExecutor L17） | 文件与 `Jdk21CommonsCoreInitializer` 全文重读：L17 无上界、destroy 未覆写、门控仅 initialize 读一次 | **与第 1 轮一致**；新增事实：未覆写 `order()` → 默认 `IOrdered.NORMAL_PRIORITY=5000`（销毁序先于 IOC，空 destroy 无影响）；`stats()` 违约与命名误导维持 C4-004 |
| 行 8（NopApplication L39/44） | `ShutdownHook.getInstance().addDisposable(() -> CoreInitialization.destroy())` 单点注册（L44-46）；nop-boot 全模块 grep 零自建线程/执行器 | **与第 1 轮一致**；本轮补充：`run(args)` 默认 task `() -> 0` 使主任务即返（①c 依赖事实） |

---

## 第 2 轮新增发现

### [nop-core-framework-C4-008] concurrent-start 完成回调族三形态吞错：Error 使启动图 future 成孤儿（started 永不翻转、无容器级错误）、成功回调异常完全静默、全链无超时永久 pending

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:502-512,526-539`；`nop-kernel/nop-core/src/main/java/io/nop/core/execution/TaskExecutionGraph.java`（runTask catch(Exception) 段，设施本体）；`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/FutureHelper.java:158-174`（syncGet 无超时）
- **证据片段**:
  ```java
  // BeanContainerImpl L503-512：whenComplete 是失败/完成语义唯一汇聚点，三分支均无兜底
  startFuture = asyncStartBeans(startBeans).whenComplete((ret, err) -> {
      if (err != null) { handleStartError(err); }        // 依赖"图 future 必然完成"——Error 形态下违约
      else { runLazyProperties(); runDelayMethod(); started = true; }  // 自身抛出则静默、started 永不翻转
  });
  // TaskExecutionGraph.runTask：catch (Exception e) 不接 Error → Error 逃逸 executor.execute lambda
  } catch (Exception e) { LOG.error("nop.task.run.error:taskName={}", taskName, e); future.completeExceptionally(e); }
  // FutureHelper.syncGet L158-174：无超时参数路径，图 pending 则调用方永久阻塞
  ```
- **严重程度**: P2（门控条件：`nop.ioc.app-beans-container.concurrent-start=true`，默认 false；`awaitStartFinished` 生产零调用方使三形态均无二次暴露）
- **并发场景**: `nop-global-worker`（弹性扩容至 max=30，daemon）上的启动图任务体（用户 init 代码，可抛任意 Throwable/可挂死）vs main 线程（start() 即返后继续跑后续 initializer 与应用主任务）。
- **触发时序**: **形态(a) Error**：T1（worker）执行 bean X 的 init 抛 Error（如静态初始化失败/AssertionError）→ `runTask` catch(Exception) 不接 → Error 逃逸 executor lambda → 线程按 `NopThread.DEFAULT_EXCEPTION_HANDLER` 记 `nop.commons.uncaught-exception` 日志后死亡（池重建线程）→ 节点 future **永不完成** → allOf 永不完成 → whenComplete 不触发 → `handleStartError` 不调用、已建 bean 不销毁、`started` 永不翻转。容器 limbo（running=true/started=false），`checkStarted`（L620）因 running=true **通过**，getBean 可继续按需创建 bean——半可用死容器。**形态(b) 成功回调异常**：图全部成功完成后，whenComplete success 分支的 `runLazyProperties()`/`runDelayMethod()`（内含用户 delay 方法）抛异常 → 异常使 whenComplete 阶段异常完成，**无消费者、无任何日志**，`started=true`（L509）不执行 → 同为 limbo 且**零可见性**。对照 sync 模式：同异常走 start() L519 catch → handleStartError + rethrow → 启动大声失败。**形态(c) 无超时**：任一 bean init 挂死（阻塞 I/O）→ 图永久 pending，无任何超时层（图/任务/syncGet 三层全无）→ 永久 limbo。
- **现状**: 三形态为 C4-003(b)(c) 的机制深化——第 1 轮已立案 Exception 形态（F1）的吞错与无 join 点，本轮实测证明该 whenComplete 汇聚点还存在**三类绕过 handleStartError 的路径**：future 不完成（Error）、success 分支自身抛出（回调异常）、永不完成（挂死）。F1/F6/F7 形态传播正确（见①b 终表）；缺陷面限定于 F2/F5/F4-async。
- **风险**: 门控开启时，Error/回调异常/挂死三类启动失败全部退化为"无错误日志（或仅一条 tid 日志）、容器半可用或假可用、started 永久 false"的静默状态——比 F1（有日志、容器已停、端口探活可发现）更难定位；`isStarted()` 观测点（若有消费方）成为唯一线索。
- **建议**: (1) `handleStartError` 前移兜底：whenComplete 外再挂 `.exceptionally()` 或将回调体包 try/catch，success 分支异常统一走 `handleStartError`；(2) TaskExecutionGraph 的 `catch (Exception)` 改 `catch (Throwable)`（设施修复，见 CL-019 移交）或在单元侧将任务体包 catch(Throwable) 后以 Exception 再抛；(3) 框架级 join + 超时（C4-003 建议(1) 的具体化）：initializer 序末尾 `awaitStartFinished`，`syncGet` 换带超时形态，超时按启动失败处理；(4) ①a 的三重文档债同批修复（`@Description` 更正 + docs-for-ai 收录 + 失败语义说明）。
- **失效面枚举（F3）**: 同族"whenComplete/回调吞错"全清单：`BeanContainerImpl.start` L503-512（本条，三形态）、`TaskExecutionGraph.executeAsync` 末尾 whenComplete 仅打日志（任务体错误已向 future 传播，不吞，正常）、`IocCoreInitializer.initialize` catch 后重抛（正常）。同根设施面：`TaskExecutionGraph.runTask` catch(Exception)（nop-kernel/nop-core 辖区 → **CL-019 移交**）。同门控：concurrent-start 仅此一个开关，无兄弟站点。
- **信心水平**: 确定（三形态传播链逐行实测；Error 形态的唯一外部可见性 = NopThread uncaught handler 日志，已核实其存在与内容）
- **误报排除**: 不是重复立案 C4-003——第 1 轮立案的是 Exception 形态的吞错+无 join 点，本条立案的是**绕过 handleStartError 的三类新路径**及其结构性根因（汇聚点无兜底）；也不是把"用户 delay 方法抛异常"当用户代码问题——sync 模式同一异常会导致启动失败，async 模式静默，是框架分支行为不对称。
- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P2（门控），设施根因移交注明**。三形态逐行亲核成立：(a) TaskExecutionGraph.runTask 实际 L223 `catch (Exception e)` 不接 Error→节点 future 永不完成→whenComplete 不触发→handleStartError 不调用→limbo（running=true/started=false）；checkStarted L620-621 因 running=true 通过（半可用亲核）；唯一可见性=NopThread.DEFAULT_EXCEPTION_HANDLER（L48-54 `nop.commons.uncaught-exception:tid=`）经 NamedThreadFactory L82 产 NopThread 亲核成立；(b) success 分支 runLazyProperties/runDelayMethod 抛出→started=true（L509）不执行、L511 完成日志同被跳过→startFuture（whenComplete 返回阶段）异常完成零消费零日志（awaitStartFinished 生产零调用方独立 grep 亲核）；sync 模式同异常走 start() L519 catch→handleStartError+rethrow——分支不对称亲核成立；(c) FutureHelper.syncGet（实际 L158-175，原记 L158-174 为漂移）无超时路径亲核。**处置裁定：维持本条（使用方/框架面=whenComplete 汇聚点无兜底）；设施根因（runTask catch 面）移交 nop-kernel=CL-019（经对照面核实为 WI 2.1 未覆盖的设施新位点），修复须两联动**。P2 维持。现状=新发现，未修复。

### [nop-core-framework-C4-009] concurrent-start 下 restart() 代际竞态：旧图 whenComplete 延迟触发 handleStartError→stop() 自毁新生代容器；在途旧任务经 getBeanScope 读新 scope 产生代际混入

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:452-455,476-524,526-533,535-558,594-617`；生产调用方：`nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/AutoTestCase.java:219`、`nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/JunitBaseTestCase.java:36`
- **证据片段**:
  ```java
  // restart() = stop() + start()，同一容器实例，字段被新生代覆写
  public void restart() { stop(); start(); }                          // L452-455
  // 旧图（第一次 start 注册）的 whenComplete 在旧图终止后异步触发：
  //   handleStartError → stop() → cancellable.cancel()——此刻 this.cancellable 已是【新图】的
  if (cancellable != null) { cancellable.cancel(ICancellable.CANCEL_REASON_STOP); cancellable = null; }  // L599-602
  // 旧图在途任务跑完 getBean0 → getBeanScope 读【当前字段】singletonScope = 新 scope：
  private IBeanScope getBeanScope(BeanDefinition bean) { ... beanScope = singletonScope; ... }            // L438-439
  ```
- **严重程度**: P2（双重门控：`nop.ioc.app-beans-container.concurrent-start=true`（默认 false）**且** 调用 `restart()`；restart 的全仓生产调用方实测仅 nop-autotest 两处，`IBeanContainer.restart()` 为 nop-api-core 公共接口契约，任何嵌入方可调）
- **并发场景**: 旧启动图（globalWorker 上的在途 bean 任务）与 restart 主线程（stop→start 序列，非原子、无互斥）以及新启动图（同池提交）三代并发。
- **触发时序**: T1（main）`restart()`：stop()（cancel **旧** cancellable、关旧 scope）→ start()（running=true、创建**新** singletonScope、提交**新**图、`this.cancellable = 新`）。此后两个异步后果竞速：**(a) 自毁链**：旧图队列任务被旧 cancelToken 跳过 → futures 以 CancellationException 级联 → 旧图 allOf 异常完成 → **第一次 start 注册的 whenComplete** 触发 `handleStartError` → `stop()`——此刻 running=true（新代）→ running/started 置 false、**新 cancellable 被 cancel**（新图队列任务全部跳过）、**新 singletonScope 被关闭销毁** → restart 调用方已返回并认为成功，容器整体死亡（getBean 抛 NOT_STARTED）。当 (a) 在新 start() 完成字段赋值前触发则无害（stop 已停容器），触发时机不可控，构成典型 check-then-act 代际竞态。**(b) 代际混入**：旧图在途任务（未被 cancelToken 检查覆盖，已开始执行）跑完 `getBean0` → `getBeanScope` 读到**新 scope** → 半成品旧代 bean `add` 进新 scope 成功（BeanScopeImpl 的 ALREADY_CLOSED 守卫只管旧 scope）→ 新代单例集中混入旧代实例，其依赖解析跨代。
- **现状**: sync 模式下 restart 无此竞态（stop/start 全在调用线程串行，无异步残留）；concurrent-start 模式下 stop() 既不等旧图终止也不作废旧 whenComplete 的容器引用，`handleStartError` 无代际检查（不校验"该失败是否属于当前代启动"）。`startFuture` 字段被第二次 start 覆写，旧图完成态彻底失联（仅 whenComplete 闭包还持有）。
- **风险**: autotest 场景（`JunitBaseTestCase`/`AutoTestCase` 每用例 restart）在 concurrent-start 配置下将出现**用例间随机容器自毁**：表现为测试间歇性 `ERR_IOC_CONTAINER_NOT_STARTED`，与用例内容无关、不可稳定复现，极易误判为用例自身问题；嵌入方热重启同理产生整机不可用。
- **建议**: (1) 代际令牌：start() 时递增 generation 计数，`handleStartError`/whenComplete 闭包捕获启动时代际，触发时校验 `generation == 捕获值` 不符则仅记 debug 日志返回；(2) `stop()` 中 cancellable 判空后增加同代校验（cancel 仅作用于本代 cancellable）；(3) 修复 C4-003 建议的框架级 join 后此竞态自然收敛（join 使 restart 的 stop 语义变为"等旧图终止后再开新代"）；(4) `BeanScopeImpl` 免受混入的结构性修法：图任务闭包捕获创建时的 scope 引用而非经容器字段二次解析（可与 (1)(2) 任选）。
- **失效面枚举（F3）**: 同族"restart/生命周期重入 + 异步残留"：`BeanContainerImpl.restart`（本条，唯一复合点）；`CoreInitialization.reinitialize`（按 order 逆序销毁再初始化，**同步逐个执行**，无并发残留，核对正常）；`NopQuarkusBeanContainer.restart()`（空实现，无面）。`restart()` 全仓生产调用方核对：nop-autotest 两处（本条）、其余为接口声明/空实现/委托（`DelegateBeanContainer` L45 委托同容器，随本条收敛）。
- **信心水平**: 确定（自毁链字段级逐行核实；触发竞速窗口真实存在——旧图终止时机由剩余任务时长决定，与 restart 交错概率随 bean 数量增大而增高）
- **误报排除**: 不是把 restart() 的同步 stop+start 设计当缺陷——sync 模式下该实现正确；立案点是 concurrent-start 模式下 stop 的"cancel 即返回、不等终止"语义与无代际校验的 whenComplete 的组合。双重门控（concurrent-start + restart 调用）已显式注明，未按无条件 P1 处理。
- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P2（双门控）**。字段级亲核成立：restart L452-455=stop()+start() 同一实例；旧图 whenComplete 闭包存活于旧图 future（startFuture 字段 L480 被第二次 start 覆写但闭包不失效）；handleStartError→stop() 读当前字段 cancellable（L599-602）/singletonScope（L605-607）无代际校验——新生代自毁链成立；getBeanScope（L436-446）读当前 singletonScope 字段——在途旧任务经新 scope add 成功（BeanScopeImpl.add 的 ALREADY_CLOSED 守卫 L74-77 仅护旧 scope）代际混入成立；restart 生产调用方全仓独立 grep 亲核=nop-autotest 两处（AutoTestCase L219、JunitBaseTestCase L36），NopQuarkusBeanContainer.restart 空实现亲核。P2 维持。现状=新发现，未修复。

### [nop-core-framework-C4-010] singleton scope 销毁序 = ConcurrentHashMap 哈希迭代序：创建序有拓扑契约、销毁序无任何依赖约束，destroy 方法可运行于其依赖已销毁之后

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanScopeImpl.java:44-47,81-103`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:594-617`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:704-718`
- **证据片段**:
  ```java
  // BeanScopeImpl：beans 为 ConcurrentHashMap（非插入序）；close 直接迭代销毁，无排序
  private final Map<String, ProducedBeanInstance> beans = new ConcurrentHashMap<>();   // L44
  public void close() {
      synchronized (beans) { closed = true; }
      for (Map.Entry<String, ProducedBeanInstance> entry : beans.entrySet()) {         // L86：哈希序
          remove(beanName, entry.getValue());   // → container.destroyBean → beanDef.destroyBean
      }
  // BeanDefinition.destroyBean L704-718：仅销毁自身（subscriptionCleanup/destroyMethod/ioc:destroy），无依赖感知
  ```
- **严重程度**: P2（无门控，影响每次容器关闭；实害多为关闭期 destroy 失败与资源晚释放，autotest 热重启窗口放大为跨用例状态泄漏）
- **并发场景**: 单线程销毁（stop 调用线程）；缺陷非并发竞态而是**顺序契约缺失**——与 C4-009 的并发混入面正交。
- **触发时序**: 容器创建序有完整契约（`BeanTopologySorter` 按 dependsOn/ioc:before/ioc:after/ref 拓扑排序，`beanTopoIndex` 全程维护）；销毁时 `stop()` L607 仅调 `singletonScope.close()`，后者按 CHM `entrySet()` 迭代序（桶哈希序，与插入序、拓扑序均无关）逐个 `remove` → `destroyBean`。设 bean A（如缓存刷新器）注入 bean B（如 DataSource）：销毁若先迭代到 B → B.close()（连接池关闭）→ 后迭代到 A → A 的 destroy 方法（刷缓存落库/向 B 提交任务）对已关闭的 B 操作 → 抛异常（被 close() L89-93 catch 记 `nop.err.ioc.destroy-bean-fail` 后继续）或静默丢数据；A 自身持有的资源因 destroy 中断而泄漏。CHM 迭代序随 bean 名哈希与容量变化，**同一应用每次销毁顺序可不同**——失败呈偶发性。
- **现状**: 全链无任何销毁排序逻辑（grep `destroyOrder/reverseTopo` 零命中；`destroyBean` L704-718 实测无依赖感知）；`verifyOrderConstraints` 注释明示顺序契约仅约束创建序。第 1 轮核实记录"concurrent-start 失败路径在途 bean 不泄漏进已关闭 scope"针对的是 add/close 互斥（维持成立），未覆盖销毁**彼此之间**的顺序。
- **风险**: (1) 每次关闭的 destroy 失败噪声掩盖真实关闭缺陷（日志逐条 `destroy-bean-fail` 但退出码仍 0）；(2) 关闭期资源释放失败=句柄/连接晚释放或泄漏至进程退出；(3) autotest 每 restart 全量销毁重建，哈希序差异使"依赖他 bean 的 destroy"偶发失败，污染测试结论；(4) 用户按 Spring 习惯（反注册序销毁）编写有状态销毁逻辑时契约性违约。
- **建议**: (1) `close()` 销毁前按 `beanTopoIndex` 降序排序迭代（`beans.keySet().stream().sorted(comparing(topoIndex).reversed())`，一次性 O(n log n)，语义=反创建序销毁，与 Spring 契约对齐）；(2) 或至少按 `ioc:destroy` 增加 before/after 声明能力并文档化"销毁序无保证"（现状语义显式化）；(3) docs-for-ai service-layer/model-first 文档补充销毁序契约说明。
- **失效面枚举（F3）**: 同族"销毁序"全清单：`BeanScopeImpl.close`（本条根位点）；`BeanScopeContext.instance().onContainerStop`（自定义 scope 移除，无销毁面）；`ConfigStarter.doStop` 的 `safeCloseAll(closeableSources)`（源间相互独立，列表序销毁无依赖面，核对正常）；`CoreInitialization.destroy` initializer 逆序（**正确**，见④矩阵）。bean 级依赖引用族：全部 `destroyMethod`/`ioc:destroy` 用户代码（随根位点受益）。
- **信心水平**: 确定（机制链完整实测；具体实害形态取决于用户 destroy 代码是否使用注入依赖——平台自身 bean 的 destroy 多为自包含，故风险表述限定于该条件）
- **误报排除**: 不是把"ConcurrentHashMap 作为存储"当缺陷（存储选型正确）；立案的是销毁迭代无排序的契约缺失。也不是重复 C4-002（JDBC 源关闭竞态，跨线程面）与本条（同线程顺序面）正交。P2 未升 P1：实害限于关闭窗口、多数 destroy 自包含、无数据损坏面。
- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P2**。亲核成立：BeanScopeImpl beans=ConcurrentHashMap（L29）、close() 按 entrySet() 迭代（L104）无任何排序、逐 bean catch 继续（L106-111）；BeanDefinition.destroyBean（实际 L702-718）仅 subscriptionCleanup.cancel+destroyMethod+ioc:destroy，无依赖感知；与 C4-009 正交（同线程顺序面 vs 跨线程代际面）定位准确。P2 维持。现状=新发现，未修复。

### [nop-core-framework-C4-011] 关闭序缺口：IocCoreInitializer.destroy→stop() 不 join 在途启动图任务，后续 initializer 销毁（VFS/config 源）与在途 bean 创建跨线程竞态

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/initialize/CoreInitialization.java:146-190`（销毁驱动）；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/initialize/IocCoreInitializer.java:54-62`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:594-617`；`nop-core-framework/nop-config/src/main/java/io/nop/config/starter/ConfigStarter.java:526-551`
- **证据片段**:
  ```java
  // CoreInitialization.destroy L152：逆序逐个销毁，awaitStartFinished 概念不存在于 destroy 路径
  for (ICoreInitializer initializer : CollectionHelper.reverseList(list)) { ... initializer.destroy(); ... }
  // IocCoreInitializer.destroy L60：container.stop() = cancel + 关 scope，立即返回，不等在途图任务
  container.stop();
  // BeanContainerImpl.stop L599-602：cancel 后无 join；在途任务继续在 globalWorker 上跑
  if (cancellable != null) { cancellable.cancel(...); cancellable = null; }
  ```
- **严重程度**: P3（门控条件：concurrent-start=true **且** 停机时启动图仍在执行——停机撞启动尾窗，窗口窄；后果为关闭期异常与资源面，非数据损坏）
- **并发场景**: 销毁线程（JVM hook / 主线程，逆序逐 initializer）vs globalWorker 上仍在执行 bean 创建的旧图在途任务（cancelToken 仅在任务**开始**时检查，在途不可中断）。
- **触发时序**: 停机信号到达时 concurrent-start 图仍在跑（慢 init 拖长启动尾窗）→ destroy 逆序：IOC 先销毁（cancel 未启动任务、关 scope——在途任务此刻仍在 `getBean0` 中，可能正读 VFS 资源/触发配置读取/建 JDBC 连接）→ 销毁继续下行至 2840 `ConfigStarter.doStop`：`vfs.destroy()`（在途任务若经 VFS 装载模型即撞已销毁 VFS）、`safeCloseAll` 关配置源（在途任务经 configProvider 读值尚安全——内存快照；经源刷新链则撞 C4-002 的重建泄漏形态）→ 在途任务最终 add 进已关 scope 抛 ALREADY_CLOSED 或以资源异常失败（仅 `nop.task.run.error` 日志）。销毁序矩阵本身正确（见④），缺陷仅在 IOC 销毁是**cancel-and-return**而非 cancel-and-join。
- **现状**: `awaitStartFinished()` 存在但 destroy 路径不调用（与启动路径同样的 join 缺失，对称面）；第 1 轮 C4-003 建议(1) 只覆盖启动序 join，未覆盖关闭序。
- **风险**: 停机期异常噪声掩盖真实关闭问题；与 C4-002 组合时在途刷新/在途 bean 创建可在源关闭后重建 JDBC 连接（停机期泄漏）；语义上"容器已销毁"与"worker 上仍有容器任务在跑"并存，破坏销毁序矩阵的串行假设。
- **建议**: `IocCoreInitializer.destroy()` 在 stop() 前加有界 join：`FutureHelper.futureGet(cancelToken, timeout)` 等待 startFuture 终态（超时记 warn 后继续，避免停机被挂死 init 卡住——与 C4-008(c) 的超时建议共用参数）；或 `stop()` 内完成同等待待。与 C4-003/C4-008 的 join/超时修复合并为同一 PR 面。
- **失效面枚举（F3）**: 同族"destroy 不等异步任务"：`IocCoreInitializer.destroy`（本条）、`Jdk21CommonsCoreInitializer`（无 destroy，executor 悬空——C4-004 已立案）、`ConfigInitializer.destroy`（doStop 先 deactivate 再停 executor，关闭自身管理的载体，序正确）、`SingleThreadConfigExecutor` 经 `ConfigStarter.doStop` 停止时无 awaitTermination（DefaultThreadPoolExecutor L91-93，第 1 轮 C4-006 已核，排队任务有 active 守卫兜底，维持）。init 阶段在途任务与后续 initializer 的启动序竞态不存在（initialize 同步串行，图任务由 start() 提交后 initialize 才返回——但同样无 join，属 C4-003 已立案面，不重复计数）。
- **信心水平**: 确定（机制链完整；窗口窄且双重门控，按第 1 轮口径降 P3）
- **误报排除**: 不是重复 C4-003（启动路径 join 缺失导致失败不可见）——本条是**关闭路径**的 join 缺失导致销毁序矩阵串行假设被在途任务打破，两者共用修复但失效面不同；也不是重复 C4-010（同线程销毁序契约）——本条是跨线程在途任务与销毁的竞态。
- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P3（门控）**。亲核成立：CoreInitialization.destroy 实际 L158-206（原记 L146-190 为漂移；L167 reverseList 逆序、L173-178 逐 initializer catch(Throwable) 的矩阵正面结论亲核维持）；IocCoreInitializer.destroy L60 container.stop()；stop() cancel-and-return（L599-602）；在途任务不可中断（TaskExecutionGraph L196-203 cancelToken 仅任务开始时检查亲核）。与 C4-003（启动路径 join 缺失）/C4-010（同线程销毁序）边界维持。P3 维持。现状=新发现，未修复。

---

## 维度集外线索（自 CL-019 起）

### [nop-core-framework-CL-019]（设施本体，nop-kernel/nop-core 辖区，移交）TaskExecutionGraph.runTask 仅 catch(Exception)：任务体抛 Error 使图 future 永久孤儿，所有图消费方（含 IoC 启动图）共享该缺陷

- **位置**: `nop-kernel/nop-core/src/main/java/io/nop/core/execution/TaskExecutionGraph.java`（runTask 的 `catch (Exception e)` 段）
- **形态**: C3（异步链完成语义），设施本体缺陷，非本单元辖区（本单元后果已以 C4-008 形态(a) 立案）。
- **要点**: `executor.execute` lambda 内 `catch (Exception e)` 不接 Error/Throwable——任务体抛 Error 时 lambda 逃逸、节点 future 永不完成，图的 allOf 永久 pending，所有下游 whenComplete（含 BeanContainerImpl 的 handleStartError 汇聚点）永不触发。设施的全部消费方（IoC 启动图及平台其他 TaskExecutionGraph 使用点）继承同一违约面。worker 线程侧有 NopThread uncaught handler 兜底日志（`nop.commons.uncaught-exception`），非完全静默，但完成语义已破坏。
- **移交建议**: nop-kernel 设施单元复核 catch 面并改 Throwable（或包装后 completeExceptionally）；回归面含 IoC concurrent-start 图（C4-008 形态(a) 为其用例）。
- **复核处置**（独立复核子代理 2026-10-17）: **确认，移交 nop-kernel（nop-core 辖区）**。亲核：runTask 实际 L223 `catch (Exception e)`。对照面核实：WI 2.1 目录（2026-10-16-2316）全量 grep 无本位点立案——其 C3-001 针对周期调度设施（DefaultScheduledExecutor/BindScheduledExecutor 杀链语义）、其 Error 孤儿案针对 ExecuteHelper.throttleExecute（不同类）——CL-019 为 WI 2.1 未覆盖的设施新位点，移交有效且必要；回归面含 IoC 启动图（C4-008 形态(a) 为其用例）。

### [nop-core-framework-CL-020]（设施本体，nop-kernel/nop-commons 辖区，移交）GlobalExecutors 三个全局池零生命周期挂接：CoreInitialization.destroy 不触达 g_executors，热重初始化依赖撞名 register 的副作用收敛

- **位置**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:39-116`
- **形态**: C6（生命周期对称性），设施本体缺陷。
- **要点**: globalTimer/globalWorker/cachedThreadPool 经 `computeIfAbsent` 懒创建后驻留 `g_executors`；`CoreInitialization.destroy()` 销毁全部 initializer 与缓存，但**从不 unregister/shutdown 全局池**——热重初始化（autotest、reinitialize 场景）复用旧池实例（线程与旧代上下文残留），唯一收敛路径是同名 `register` 撞旧触发 `old.destroy()`（L102-107），三个内建池无任何调用方走该路径。daemon 语义使 JVM 退出无阻塞，常态实害限于热重初始化的池状态残留与统计失真。
- **移交建议**: nop-kernel 设施单元评估在 CoreInitialization.destroy 的 cleanup 段挂接全局池 shutdown/unregister，或明示"全局池进程级单例、不随平台生命周期销毁"契约并文档化。
- **复核处置**（独立复核子代理 2026-10-17）: **确认（线索），与 WI 2.1 C6-003 同位点（注明）**。亲核：g_executors computeIfAbsent 懒创建（GlobalExecutors L39/L72/L86）；CoreInitialization.destroy（实际 L158-206）全程不触达 g_executors；唯一收敛路径=同名 register 撞旧 old.destroy()（L102-107），三内建池无调用方走该路径。设施单元 WI 2.1 已以 nop-kernel-concurrency-C6-003 立案「全局池无任何平台级释放路径（缺失）」且其 C6-round2 将 Jdk21CommonsCoreInitializer destroy 僵尸窗口并入 C6-003 F3 扩展——主处置在设施单元，本单元不重复立案。

---

## 核实为无缺陷的记录（防第 3 轮重查）

1. **启动图 depends 集无环（三防线）**：dependsOn/ioc:before/ioc:after 环被 `verifyOrderConstraints`（无条件）+ sortBeans 拒绝（构造期失败）；ref 环被 `fillResolvedDepends` 位置过滤（`refPos < 自身 topoIndex`，L173-179）化为无环偏序——喂给 TaskExecutionGraph 的 depends 集合必然无环，DagAnalyzer 去环逻辑对 IoC 启动图不可达；构造器环由 `getBean0` `isInCreationByCurrentThread` 同线程检测拦截。**跨线程交叉创建 AB 死锁假设经代码链证伪**。
2. **异步启动窗口 getBean 并发创建同 bean 安全**：`scope.add`（BeanDefinition L570）在 `createInstance` 内、于 `getBean0` 的 `synchronized(beanDef)` 临界区（L406-425）中执行，并发 getBean 阻塞等锁后经 `beanScope.get` 复用，无重复单例。
3. **依赖缺失（F3 形态）**：`fillResolvedDepends` 对缺失目标（normalizeBeanId null）跳过；指向未入图 bean 的 depends 经 `waitPrevTasks` retainAll 降级为无依赖任务，顺序由内联创建保证——无 NPE、无失败。
4. **①b 表 F1/F6/F7 形态传播正确**：Exception → handleStartError；声明序违约与图结构异常同步上抛（start() L519 catch + rethrow）。
5. **⑤ 基线补漏**：新增 5 类 grep 模式（`new NopThread(`/`Thread.ofVirtual`/`new Timer(`/`ThreadFactory`/`newSingleThreadExecutor`）7 目录重跑零新站点；nop-boot/nop-plugin/nop-log/nop-security 零命中复核成立；11 行全景维持。
6. **② 消费方覆盖完备**：本单元 `GlobalExecutors` 消费 grep 实测 7 站点与第 1 轮全景完全一致，executeOn 自救范式单元内零使用（修复时引入即可）。

---

## 最终保留项（第 2 轮新增）

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| nop-core-framework-C4-008 | P2（门控：concurrent-start） | nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:502-512,526-539 | 完成回调族三形态吞错：Error 使图 future 孤儿（limbo 半可用）、成功回调异常零日志静默、全链无超时永久 pending |
| nop-core-framework-C4-009 | P2（双门控：concurrent-start + restart） | nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:452-455,594-617；nop-autotest AutoTestCase.java:219、JunitBaseTestCase.java:36 | restart 代际竞态：旧图 whenComplete 触发 handleStartError→stop() 自毁新生代；在途旧任务经 getBeanScope 字段读取混入新 scope |
| nop-core-framework-C4-010 | P2 | nop-ioc/src/main/java/io/nop/ioc/impl/BeanScopeImpl.java:44-47,81-103 | singleton 销毁序 = CHM 哈希迭代序，创建序拓扑契约在销毁侧零对应，destroy 可运行于依赖已销毁之后 |
| nop-core-framework-C4-011 | P3（门控：concurrent-start + 停机尾窗） | nop-ioc/src/main/java/io/nop/ioc/initialize/IocCoreInitializer.java:54-62；CoreInitialization.java:146-190 | destroy 路径 IOC stop 不 join 在途启动图任务，后续 VFS/config 源销毁与在途 bean 创建跨线程竞态 |

| CL 线索 | 形态 | 一句话摘要 |
|---------|------|-----------|
| nop-core-framework-CL-019 | C3（nop-kernel/nop-core 辖区，移交） | TaskExecutionGraph.runTask 仅 catch(Exception)，任务体 Error 使图 future 永久孤儿（C4-008 形态(a) 的设施根因） |
| nop-core-framework-CL-020 | C6（nop-kernel/nop-commons 辖区，移交） | GlobalExecutors 三全局池零生命周期挂接，destroy 不触达，热重初始化依赖撞名 register 副作用收敛 |

**统计**: 第 2 轮新增发现 4 条（P2×3 / P3×1，无 P0/P1），维度集外线索 2 条（CL-019、CL-020）。累计本维度 11 条（第 1 轮 7 + 第 2 轮 4）+ CL 4 条。①~⑤ 五项深挖指令全部闭环（①b 七形态终表、①c 双部署族精确化、② 分类终表 7 站点、③ 并发度/失败隔离/cancelToken 终判 + 死锁假设证伪、④ 销毁矩阵 + 两缺口立案、⑤ 抽验 4 行 + 补漏零新增）；预注互引维持：设施本体（WI 2.1 globalTimer 死配置 / TaskQueue 弹性扩容 / GlobalExecutors 契约族 / C4-012 假参数）与 C1/C2 轮 27+6 条同族站点仅 F3 登记。

**终止信号判定**: 本轮新增最高判级 P2，无 P0/P1 → 按深挖规则不强制第 3 轮。终止信号满足：(1) 五项深挖指令全部形成终表/终判/矩阵，无遗留"待查"项；(2) F3 同族站点经两轮全量 grep 与逐点核对，本单元内站点全部入案，无新同族站点；(3) ⑤ 基线补漏双模式 grep 零新载体；(4) 6 条正面核实记录封存可重查面。**结论：C4 维度（线程池治理）在 nop-core-framework + nop-utils 单元建议终止**；若修复 plan 落地后需验证，属复核轮而非深挖轮。
