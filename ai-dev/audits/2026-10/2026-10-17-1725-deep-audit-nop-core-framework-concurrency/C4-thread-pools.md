# C4 线程池治理 — nop-core-framework 六子模块 + nop-utils（第 1 轮初审，盲审）

- **审计单元**: nop-core-framework（nop-ioc / nop-config / nop-plugin / nop-security / nop-log / nop-boot）+ nop-utils（nop-commons-java21 / nop-shell 等全部子模块）
- **仓库路径**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`，HEAD `6dceb20dc3`）
- **审计轮次**: 第 1 轮初审（盲审——本报告撰写过程中未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅阅读允许的方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md` 与 `ai-dev/skills/deep-audit-prompts.md`，以及当前 live code）
- **维度定义（C4 线程池与执行器治理）**: 池参数纪律、全局池配置边界与生命周期、池关闭语义、线程工厂纪律、池隔离与共享占用、调度器治理、执行载体选择。验证每个线程池/执行器的配置有界、关闭正确、任务隔离合理。
- **判级口径**: 沿用 `concurrency-resource-audit-prompt.md` 共享前缀的 P0–P3 领域校准；框架层被全平台使用，**「仅启动期单线程触达」站点维持条件化降档**；凡受非默认配置门控或可选 classpath 门控的发现，门控条件在条目内显式注明。写不出具体时序的一律降 P2 并标注"理论风险，未具体化"。

## 检查范围与执行载体清单表（7 目录全景登记）

基线 grep 口径：`grep -rn --include="*.java" -E "new Thread\(|ExecutorService|Executors\.|newFixedThreadPool|newCachedThreadPool|newScheduledThreadPool|IThreadPool|ScheduledExecutorService|ThreadPoolExecutor|newSingleThread|scheduleAtFixedRate|scheduleWithFixedDelay|ShutdownHook|addShutdownHook|ForkJoinPool|commonPool"` 于七目录 `src/main`（排除 `/target/`）。命中文件全部逐一人工核对，测试代码不计入生产载体。

| # | 目录 | 载体 | 创建方式 | 参数 | 队列/上界 | daemon/命名 | 关闭责任 | 判定 |
|---|------|------|---------|------|----------|------------|---------|------|
| 1 | nop-ioc | `BeanContainerImpl.asyncStartBeans` L542 | `TaskExecutionGraph(GlobalExecutors.globalWorker())`（共享池，非自建） | 复用 globalWorker：core=4/max=30（`CFG_CONCURRENT_GLOBAL_WORKER_MAX_POOL_SIZE` 默认 30） | TaskQueue(10000) | daemon / `nop-global-worker`（NamedThreadFactory） | 无池所有权；`stop()` 经 `Cancellable` 取消未启动任务（L599-602），在途任务跑完后 `BeanScopeImpl.add` 抛 ALREADY_CLOSED（已核实有守卫，见 C4-003 现状段） | 门控发现 C4-003（`nop.ioc.app-beans-container.concurrent-start` 默认 false） |
| 2 | nop-config | `AbstractFileConfigSource` L45 | `GlobalExecutors.globalTimer().scheduleWithFixedDelay`（共享调度器） | globalTimer：ScheduledThreadPoolExecutor core=4（ThreadPoolConfig 默认 corePoolSize=4，`global-timer.maxPoolSize=5` 对 scheduled 死配置——设施缺陷仅 F3 互引） | 定时任务无队列积压面 | daemon / `nop-global-timer` | `close()` → `Future.cancel(false)`（L113-117） | 发现 C4-001 |
| 3 | nop-config | `JdbcConfigSource` L54 | 同上 globalTimer | 同上 | 同上 | 同上 | `close()` → `closed=true` + `closeConnection` + `cancel(false)`（L159-168） | 发现 C4-001、C4-002 |
| 4 | nop-config | `SingleThreadConfigExecutor` L19 | `DefaultThreadPoolExecutor.newExecutor("nop-config-executor",1,100,true)`（自建） | core=max=1 | TaskQueue(10000)→ LinkedBlockingQueue 语义；满载 `StandardThreadPoolExecutor.RejectHandler` 抛 `RejectedExecutionException` | daemon / `nop-config-executor`（NamedThreadFactory） | `ConfigStarter.doStop` → `executor.destroy()`=`shutdown()`，无 `awaitTermination`（DefaultThreadPoolExecutor L91-93） | 发现 C4-006 |
| 5 | nop-utils/nop-commons-java21 | `VirtualThreadTaskExecutor` L17 | `Executors.newVirtualThreadPerTaskExecutor()`（自建） | 每任务一虚拟线程，无上界 | 无队列（每任务一线程） | 虚拟线程恒 daemon / `nop-virtual-thread` | `destroy()`=`shutdown()`；初始化器未覆写 `destroy()`（F8） | 发现 C4-004（无界本体已由 CL-004 覆盖，不重复立案） |
| 6 | nop-utils/nop-shell | `ShellRunner.collectError` L153 | `GlobalExecutors.cachedThreadPool().submit`（共享池） | max=-1 无界 + SynchronousQueue | 无界 | daemon / `nop-cached-thread-pool` | 无所有权；任务随进程流结束/selfDestroy 关流退出 | 发现 C4-005 |
| 7 | nop-utils/nop-shell | `ShellRunner.scheduleTimeout` L226 | `globalTimer().schedule`（一次性） | 同 #2 | — | 同 #2 | `finally` 中 `cancel(false)`（L125-127） | 正常（一次性、短任务体） |
| 8 | nop-boot | `NopApplication` L39 | 无自建线程；注册 `ShutdownHook`（nop-kernel 设施，单例 hook 线程 `NopShutdownHook`，static 块注册） | — | — | hook 线程非 daemon（JVM 退出语义） | `addDisposable(() -> CoreInitialization.destroy())` 单点注册（main 线程） | 注册面正常；设施本体缺陷 → CL-008 |
| 9 | nop-plugin | PluginManagerImpl L179-180/L407/L659 | **零线程/零执行器创建**；`FutureHelper.syncGet(plugin.deactivate())` | — | — | — | — | 终判见下文「Plugin syncGet 复核」：调用线程内联，无 C4 发现 |
| 10 | nop-log | 全部 11 文件 | **零执行载体**（LoggerConfigurator 族无任何 Executor/Thread/Timer） | — | — | — | — | 预期零发现，确认 |
| 11 | nop-security | 全部 12 文件 | **零执行载体** | — | — | — | — | 预期零发现，确认 |

`GlobalExecutors` 契约族（globalTimer/globalWorker/cachedThreadPool/syncExecutor/register/unregister）与 `DefaultThreadPoolExecutor`/`TaskQueue` 弹性扩容/`DefaultScheduledExecutor` 的设施本体缺陷按预注不在本单元重复立案（F3 互引标注）。本单元 `VirtualThreadTaskExecutor` 相关的 CL-004（无界）、设施 C4-012（nop-virtual-thread 假参数实体）均互引不重立。

---

## 第 1 轮（初审）发现

### [nop-core-framework-C4-001] nop-config 刷新任务体直连 globalTimer：文件遍历 I/O 与无超时 JDBC 查询占用全局定时器线程（阻塞放大使用方实例）

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/source/file/AbstractFileConfigSource.java:44-46,53-70,72-98`；`nop-core-framework/nop-config/src/main/java/io/nop/config/source/jdbc/JdbcConfigSource.java:52-58,110-137`；`nop-core-framework/nop-config/src/main/java/io/nop/config/source/jdbc/JdbcConfig.java:91-100`
- **证据片段**:
  ```java
  // AbstractFileConfigSource L44-46：刷新体 = loadConfig()，内含 Files.walk 目录遍历
  this.refreshFuture = GlobalExecutors.globalTimer().scheduleWithFixedDelay(this::refreshConfig,
          refreshInterval, refreshInterval, TimeUnit.MILLISECONDS);
  // JdbcConfigSource L54-55 + L110-137：刷新体 = JDBC 全量查询，无 queryTimeout
  this.refreshTaskFuture = GlobalExecutors.globalTimer().scheduleWithFixedDelay(this::refreshConfig, seconds,
          seconds, TimeUnit.SECONDS);
  // JdbcConfig L94：DriverManager.getConnection 无 connectTimeout/loginTimeout 入参
  return DriverManager.getConnection(jdbcUrl, userName, password);
  ```
- **严重程度**: P2
- **并发场景**: `nop-global-timer`（ScheduledThreadPoolExecutor，core=4，daemon，全平台共享）上的周期刷新任务体 vs 同一调度器上的其他平台周期任务（`LocalResourceLockManager.checkTimeout` L104-105 锁租约过期检查、`JsonRpcWebSocketHandler.sendKeepAlive` L82 每 websocket 每 10s、nop-cluster RPC 超时调度 `ClusterRpcServiceInvoker` L68 等，平台 globalTimer 消费点共约 25 处）。
- **触发时序**: T1（timer 线程 #1）执行 `JdbcConfigSource.refreshConfig` → `loadConfig` → `stm.executeQuery(query)`（L122，未 `setQueryTimeout`）；此时 DB 网络分区（TCP 黑洞）→ 该调用无限期阻塞（MySQL 缺省 socketTimeout=0），timer 线程 #1 被永久占用（`scheduleWithFixedDelay` 语义下后续轮次顺延但不新占线程）。并行地 T2（timer 线程 #2）执行 `AbstractFileConfigSource.refreshConfig` → `Files.walk`（L86）挂起于 NFS/stale mount → 线程 #2 占死。keyFile+props 两个文件源（`ConfigStarter` L349-371 各建一实例，默认 15s 周期）+ jdbc 源最坏占满 4 线程中的 3 个；同一窗口内 `LocalResourceLockManager.checkTimeout` 的锁租约过期回收、websocket keepalive 等全部挤入剩余 1 线程，调度延迟无界放大。
- **现状**: 刷新任务体（阻塞 I/O，且 JDBC 路径完全无超时）直接注册在全局共享调度器上，未用任何隔离手段；平台已有正确范式未被复用（`AbstractPollingLeaderElector` L19、`RetryScannerImpl` L166、`SysDaoMessageService` L217-228 均用 `globalTimer().executeOn(globalWorker())` 把任务体卸载到 worker 池，调度槽与执行体分离）。
- **风险**: DB 分区/存储挂起是运维常见故障；一旦发生，故障从"配置不刷新"放大为"平台心跳面退化"——文件锁租约回收延迟（持锁泄漏窗口拉长）、WS keepalive 停摆（代理断链）、RPC 超时检测延迟。4 线程的共享定时器没有隔离边界，一个使用方的 I/O 故障变成全平台定时器故障。
- **建议**: 刷新体与调度槽解耦：(1) 短平快方案——仿平台既有范式 `GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker()).scheduleWithFixedDelay(...)`，任务体落到 worker 池；(2) 更优——直接复用本模块自建的 `SingleThreadConfigExecutor`（IConfigExecutor SPI 已存在），刷新体经它执行；(3) `JdbcConfig.loadConfig` 补 `stm.setQueryTimeout(...)`，`createConnection` 通过 `DriverManager.setLoginTimeout` 或 JDBC URL 参数补连接超时，使"占死"退化为"周期性失败重试"。设施本体（globalTimer maxPoolSize 死配置）已在 WI 2.1 C4-001 立案，本条为使用方实例，修复可独立于设施修复落地。
- **失效面枚举（F3）**: 同族"任务体直连 globalTimer"全清单——`AbstractFileConfigSource`（本条）、`JdbcConfigSource`（本条）、`LocalResourceLockManager.checkTimeout`（nop-kernel，设施单元辖区）、`JsonRpcWebSocketHandler.sendKeepAlive`（nop-graphql 辖区）、`SysDaoNamingService.cleanup`（nop-sys 辖区）、`CliRunCommand` L90（nop-cli 辖区）；同族"已隔离"对照组：`AbstractPollingLeaderElector`/`AutoRegistration`/`RetryEngineImpl`/`RetryScannerImpl`/`AbstractBatchScanner`/`SysDaoMessageService`（`executeOn` 范式已用）。本单元内两处全部立案；单元外四处移交对应单元，不在此展开。
- **信心水平**: 确定
- **误报排除**: 不是"低频路径粗粒度同步"类误报：刷新是周期性长驻任务；也不是设施缺陷重审——globalTimer 池大小死配置属设施本体（互引），本条立案的是"使用方把无超时阻塞 I/O 放上共享调度器"的载体选择缺陷，有独立修复路径（executeOn/SPI/超时三选一即可修复本条，不需要动设施）。
- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P2**。证据亲核成立：AbstractFileConfigSource L45-46 直连 scheduleWithFixedDelay（任务体 loadConfig 内 Files.walk L86）；JdbcConfigSource L54-55 直连（任务体 stm.executeQuery L122 无 setQueryTimeout；JdbcConfig.createConnection L94 DriverManager.getConnection 无 loginTimeout/connectTimeout 入参）；同池共租户 LocalResourceLockManager L104-105 直连亲核；平台既有隔离范式 executeOn 三站点亲核（AbstractPollingLeaderElector L19、AutoRegistration L205、SysDaoMessageService L217/221/228）。P2 维持（占死需 DB 分区/NFS 挂起类运维故障触发、非默认即触发，但该类故障常见且放大面=全平台定时器心跳，判级恰当）。设施本体关联：WI 2.1 nop-kernel-concurrency-C4-001（globalTimer 死配置，cra-index open）为同设施两面——彼条立设施死配置面，本条立使用方占用面，独立成立互补。现状=新发现，未修复（git log 本审计日无修复提交）。

### [nop-core-framework-C4-002] JdbcConfigSource 关闭语义缺陷：cancel(false) 不停在途刷新 + closed 检查竞态导致关闭后重建永不关闭的 Connection；onChanges 回调循环无异常守卫

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/source/jdbc/JdbcConfigSource.java:71-98,100-108,151-168`
- **证据片段**:
  ```java
  private synchronized void refreshConfig() {          // L71：synchronized(this)
      if (closed) return;                              // L72-73：检查点
      ...
      } catch (Exception e) {
          closeConnection();                           // L83：置 connection=null
          try { vars = loadConfig(); }                 // L86：重试 → createConnection 重建
          catch (Exception err2) { ... }
      }
      if (vars != null) tryTriggerChange(vars);        // L96-97：在 try/catch 之外，无守卫
  }
  public void close() {                                // L159：非 synchronized
      closed = true; closeConnection(); onChanges.clear();
      if (refreshTaskFuture != null) refreshTaskFuture.cancel(false);  // L166：不中断在途任务
  }
  ```
- **严重程度**: P2
- **并发场景**: `nop-global-timer` 线程（在途 `refreshConfig`）vs 停机线程（`ConfigStarter.doStop` → `IoHelper.safeCloseAll` → `close()`）。
- **触发时序**: T1（timer 线程）通过 L72 `closed` 检查，进入 `loadConfig`，阻塞于 `executeQuery`；T2（停机线程）执行 `close()`：`closed=true`、`closeConnection()`（关闭 T1 正在用的连接）、`cancel(false)`（不中断 T1）；T1 的 `executeQuery` 抛 SQLException → catch → `closeConnection()` → L86 `loadConfig()` 重试 → `connection==null` → `jdbcConfig.createConnection()` **成功建立新连接**（DB 对新连接可达的分区场景）并写入字段；`close()` 已返回，此后无人再关该连接 → **关闭路径 JDBC 连接泄漏**（每次"停机时恰有在途刷新"泄漏 1 个连接，连接池外部直连无池化回收）。次级面：L96-97 `tryTriggerChange`（内部裸跑 `onChanges` 回调，L104-106）在 try/catch 之外，任何回调抛出的异常将逃逸 `refreshConfig` → 按 JDK `scheduleWithFixedDelay` 语义**永久终止后续刷新且无任何日志**（F9 形态）。
- **现状**: `closed` 为 volatile 但 `close()` 与 `refreshConfig` 不共用同一锁（前者非 synchronized），检查-然后-行动窗口存在；对照组 `AbstractFileConfigSource.refreshConfig` L54-69 用 catch-all 包住包括 `tasks.run()` 回调循环在内的全路径（L64-68 注释明示知道 JDK 语义），JdbcConfigSource 仅包住 loadConfig 段，守卫不对称。
- **风险**: 停机路径连接泄漏使 DB 侧连接占用晚释放（配合 DB 侧连接上限时拖慢同实例其他服务下线）；onChanges 守卫缺失是"一次未捕获异常静默杀死周期任务"的复发族（F9），当前唯一订阅者 `ConfigChangeApplier.requestUpdate` 抛出不可达（见 C4-006 队列限深分析），故守卫缺失部分按理论风险计入本条，不单独成案。
- **建议**: (1) `close()` 与 `refreshConfig` 收敛到同一把锁（`close()` 加 synchronized），或在 `loadConfig` 重建连接前复查 `closed`（重建点守卫：`if (closed) { closeConnection(); return null; }`）；(2) 把 `tryTriggerChange` 移入 try/catch 或对 `onChanges` 循环逐回调 catch（对齐 `ChangeSubscriptions.trigger` L78-87 与 `AbstractFileConfigSource` 的既有范式）；(3) `cancel(true)` 替代 `cancel(false)` 并在 refreshConfig 增加中断响应点（可选增强）。
- **失效面枚举（F3）**: 同族"定时刷新源 close 语义"清单：`JdbcConfigSource`（本条，缺陷）、`AbstractFileConfigSource`（L113-117 cancel(false)+catch-all，在途任务可跑完但无资源重建点，关闭语义成立）、`KeyFileConfigSource`/`PropsFileConfigSource`（继承 AbstractFileConfigSource，随父类成立）、`SysDaoNamingService.cleanup`（nop-sys 辖区，移交）。本单元 4 处已全部核对。
- **信心水平**: 确定（连接泄漏链）；守卫缺失部分为理论风险（当前订阅者不可达抛出）
- **误报排除**: 不是"cancel(false) 一定错"的机械报告——`cancel(false)` 本身是合理选择，缺陷在"取消后任务体仍会重建资源且无二次检查"的组合；也不是把 volatile 可见性问题夸大——`closed` 可见性成立，竞态在检查-行动窗口。
- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P2**。逐行亲核成立：refreshConfig synchronized + closed 检查（L72-73）；close() 非 synchronized（L159-168）与在途刷新的检查-行动窗口成立；连接泄漏链闭合——catch→closeConnection（L83）→loadConfig 重试→loadConfig L111-113 connection==null 即 createConnection 重建→close() 已返回无人再关；tryTriggerChange（L100-108）在 try/catch 外、onChanges 循环（L104-106）无守卫，scheduleWithFixedDelay 杀链形态成立（守卫缺失面的现实可达性经 C4-006(b) 停机窗口 RejectedExecutionException 交叉印证，因 close 后源已死该形态无害化，维持"理论风险"计入本条的处置不变）。P2 维持。现状=新发现，未修复。

### [nop-core-framework-C4-003] IoC concurrent-start 异步启动面：启动 bean 任务体占共享 globalWorker、框架级无 join 点、启动失败异步吞掉后进程带死容器"成功"存活

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:476-524,502-512,526-533,535-539,541-558,594-617`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/initialize/IocCoreInitializer.java:46`
- **证据片段**:
  ```java
  // L502-512：concurrentStart 分支——start() 提交图后立即返回，started 标志异步翻转
  if (concurrentStart) {
      startFuture = asyncStartBeans(startBeans).whenComplete((ret, err) -> {
          if (err != null) { handleStartError(err); }   // 失败仅 log + stop()，不上抛
          else { runLazyProperties(); runDelayMethod(); started = true; }
  // L541-542：启动图任务体（getBean0：构造器+注入+init 方法）执行载体 = 共享 globalWorker
  TaskExecutionGraph graph = new TaskExecutionGraph(GlobalExecutors.globalWorker(), "ioc-container-start");
  // IocCoreInitializer L46：框架启动序中 start() 后无 awaitStartFinished()
  appContainer.start();
  ```
- **严重程度**: P1（门控条件：`nop.ioc.app-beans-container.concurrent-start=true`，默认 false，`IocConfigs` L28）
- **并发场景**: main 线程（`CoreInitialization.initialize()` → `IocCoreInitializer.initialize()` → `start()` 即返）vs `nop-global-worker`（core=4/max=30，daemon，平台共享池）上的 bean 启动 DAG 任务体（含用户 init 方法，可能有 DB 连接池/HTTP 客户端等阻塞初始化）。三个复合子面：(a) 启动任务体占用共享池——与 WI 1.5 MA-04「业务体恒跑共享池」同族的使用方实例：任意 bean 的阻塞 init 占满 30 worker 时，图内其余任务排队（TaskQueue 10000），启动时长被最慢 init 支配且与运行期共享池争用（启动窗口内运行期消费者尚未活跃，争用主要发生在 `restart()` 场景）；(b) 无 join 点——`awaitStartFinished()`（L535-539）在生产代码零调用方（全仓 grep 仅 `TestBeanDepends` L128 测试调用），框架启动序不等图完成即继续后续 initializer 与 `NopApplication.run` 主任务；(c) 失败语义——bean 任务失败 → `handleStartError`（L526-533）仅 `LOG.error` + `stop()`（销毁已建 bean、`running=false`），主线程早已继续，进程以退出码 0/"run-success" 姿态存活，容器永久 NOT_STARTED。
- **触发时序**: T1（main）`start()` 提交图后返回（running=true，started=false）→ 继续跑完剩余 initializer，进入应用主任务，端口/健康检查就绪；T2（worker）bean X init 抛异常 → 图 future 异常完成 → `handleStartError` → `stop()` 销毁全部已启动 bean → 日志一条 `nop.err.ioc.start-failed`；此后一切 `getBean` 抛 `ERR_IOC_CONTAINER_NOT_STARTED`，而进程对 supervisor 表现为健康。无任何机制把启动失败传导为进程失败。
- **现状**: (a) 为有意的并行化设计但无容量/超时治理；(b) join 点缺失使 (c) 的失败不可见成为必然——即使调用方想等待也必须自己知道并调用 `awaitStartFinished`；(c) 异步失败被吞入日志，违背 fail-fast 启动语义。另 `awaitStartFinished` 内 `FutureHelper.syncGet` 无超时（L538），图内任一 bean init 挂死则调用方永久阻塞（F5 挂死不可见面，当前因无调用方而潜伏）。
- **风险**: 门控开启时（该配置存在的意义就是大型应用加速启动），一次普通的 bean init 异常从"启动失败、进程退出、supervisor 告警"退化为"进程存活但全容器不可用"，故障发现从秒级退化为首个用户请求报错甚至更晚；`restart()` 场景下启动体还与运行期任务共享 30 线程，慢 init 可造成运行期任务饿死窗口。
- **建议**: (1) 框架级 join：`IocCoreInitializer.initialize()` 在 concurrentStart 模式下于 initializer 序末尾（或 `NopApplication.run` 进入主任务前）调用 `awaitStartFinished()`，并给 `syncGet` 加可配置超时；(2) 失败传导：`handleStartError` 记录失败态，`awaitStartFinished`/主任务入口检查并快速失败（非 0 退出码）；(3) 载体隔离（可选）：启动图改用 `globalTimer().executeOn(专用有界池)` 或为 `ioc-container-start` 图建独立 `DefaultThreadPoolExecutor`，避免启动体与运行期共享 30 worker。
- **失效面枚举（F3）**: 同族"异步完成回调中吞错"清单：`BeanContainerImpl.start` whenComplete（本条）、`TaskExecutionGraph.executeAsync` 末尾 `whenComplete` 仅打日志（L143-146，任务体错误已向 future 传播，不吞，正常）、`IocCoreInitializer.initialize` catch 后重抛（正常）；同族"启动体占共享池"：仅此一处（nop-ioc 全模块唯一池触点，已核对基线 grep）。同门控兄弟配置：无（concurrentStart 仅此一个开关）。
- **信心水平**: 确定（触发链完整；(a) 占池面在默认关闭门控下为条件化风险）
- **误报排除**: 不是"无状态单例并发调用"类误报——本条不涉 bean 内部并发；也不是把"异步启动"设计本身当缺陷——缺陷是**失败语义与 join 点缺失**，异步启动本身（并发创建加速）保留；门控默认关闭故不判 P0，但该配置是平台公开加速手段，失败语义违约真实可达，按 P1 门控立案。
- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P1（门控）——终裁维持，不降 P2 条件化**。三复合面逐项亲核成立：(a) L542 双参构造（runPermits=null）+ TaskQueue 弹性扩容（TaskQueue L69-83 亲核，启动图有效并发上界=max=30）；(b) awaitStartFinished 生产零调用方亲核（全仓仅定义点 L536 + 测试 TestBeanDepends L128）；(c) handleStartError（L526-533）仅 LOG.error+stop()；standalone 族 run-success 亲核（NopApplication L64-68 + 默认 task `() -> 0` L75-77，行号修正：第 2 轮 ①c 记 L92-94 为漂移）；quarkus 族三绿假活亲核（NopQuarkusBeanContainer start/stop/restart 空实现，实际 L39-51，第 2 轮记 L34-46 为漂移）。**门控终裁**：触发结构=单门控+单常见条件（任一 bean init 异常），不满足「≥3 罕见条件合取」降档前提；门控为文档声明的公开加速开关（docs/performance/optimize-app.md:17 明文推荐开启）且开启者无从知情失败语义代价（@Description 误标 L27 + docs-for-ai 零收录 + 失败语义零文档三重文档债，加重"无知情开启"风险）；触发后果在两部署族均监督不可见且持续全进程生命周期（非"仅启动期单线程触达"类衰减面）。附注：子面 (a) 占共享池若按默认态暴露单独计为 P2 条件化，P1 由 (b)+(c) 失败语义面承载；修复（join+失败传导+超时）不受判级影响均为必做，判级仅决定排期优先级，交 7.1 协调方参考本附注。仓内现状注记：唯一 yaml 显式配置为 nop-quarkus-demo application.yaml `concurrent-start: false`（关闭），仓内零开启。现状=新发现，未修复。详见文末「维度复核结论」。

### [nop-core-framework-C4-004] VirtualThreadTaskExecutor 治理面：初始化器 destroy 不对称、stats() 违约返回 null、registerGlobalWorker 命名误导、门控无动态治理与关闭时静默

- **文件**: `nop-utils/nop-commons-java21/src/main/java/io/nop/commons/jdk21/VirtualThreadTaskExecutor.java:17,20-24,30-32,48-50,56-58`；`nop-utils/nop-commons-java21/src/main/java/io/nop/commons/jdk21/Jdk21CommonsCoreInitializer.java:10-13`
- **证据片段**:
  ```java
  public class VirtualThreadTaskExecutor implements IThreadPoolExecutor {
      private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor(); // L17 无上界
      private VirtualThreadTaskExecutor() { config.setName(NOP_VIRTUAL_THREAD); }  // L22 仅命名
      public static void registerGlobalWorker() {                                  // L30-32：名不副实
          GlobalExecutors.register(new VirtualThreadTaskExecutor());               //   注册名是 nop-virtual-thread
      }
      @Override public ThreadPoolStats stats() { return null; }                    // L48-50 契约违约
  // Jdk21CommonsCoreInitializer：仅 initialize()，未覆写 ICoreInitializer.destroy()（接口 default 空实现）
  ```
- **严重程度**: P3（模块门控：`nop-commons-java21` 不在根 pom 默认 modules（根 pom L558 注释停用），无任何运行时模块依赖，仅 classpath 显式引入时生效；`CFG_ENABLE_VIRTUAL_THREAD_POOL` 默认 true）
- **并发场景**: `CoreInitialization.initialize()`（main 线程）注册执行器 vs `CoreInitialization.destroy()`（main/hook 线程）与虚拟线程 executor 上的在途任务。
- **触发时序**: (a) **destroy 不对称（F8）**：`initialize()` 注册执行器 → 平台 destroy 时 `ICoreInitializer.destroy()` 走 default 空实现 → 执行器既不从 `GlobalExecutors` 注销也不 `shutdown()`；热重初始化（destroy 后再 initialize）时靠 `GlobalExecutors.register` L102-107 顺带 `old.destroy()` 收敛，但"仅 destroy 不重初始化"路径下执行器带病存活（虚拟线程 daemon，JVM 退出不受阻，实害有限故判 P3）。(b) **stats() 违约**：任何按 `IThreadPoolExecutor.stats()` 契约消费 `GlobalExecutors.allExecutors()` 的监控路径拿到 null 即 NPE（当前平台内唯一 stats 消费点 `BindScheduledExecutor.stats()` L125 仅在包装时触达，本执行器无包装方，潜伏）。(c) **门控语义**：`CFG_ENABLE_VIRTUAL_THREAD_POOL.get()` 仅在 initialize 时读一次，运行期翻转配置无效果且无日志；门控关闭时零注册零日志，"虚拟线程池为何不存在"不可观测。(d) **命名误导**：`registerGlobalWorker()` 实际注册名 `nop-virtual-thread`（`GlobalExecutors` 无该名静态访问器，仅 `requireExecutor("nop-virtual-thread")` 可达），阅读者会误认为替换了 global worker——现实误导成本已存在（本审计初查即被误导一次）。
- **现状**: 执行器本体无界（每任务一虚拟线程）属 CL-004 已立案面；`ThreadPoolConfig` 假参数实体（config 对象挂全套 core/max/queue 默认值但对虚拟线程无意义）属设施 C4-012 已立案面——本条仅立案初始化器/治理面新增四点，不与前两者重复计数。
- **风险**: 面向未来接入方：谁按 stats 契约遍历全局执行器就 NPE；谁因方法名以为换掉了 globalWorker 就会误估容量；热 destroy 路径执行器悬空。
- **建议**: (1) `Jdk21CommonsCoreInitializer` 覆写 `destroy()`：`GlobalExecutors.unregister(NOP_VIRTUAL_THREAD)` + 执行器 shutdown；(2) `stats()` 返回零值 `ThreadPoolStats` 或虚拟线程计数（`Thread.getAllStackTraces` 太重，可用 `executor` 的 `ManagementFactory` 虚拟线程计数），至少不返回 null；(3) 方法更名 `registerVirtualThreadExecutor()`（或deprecated 旧名）；(4) 门控关闭时 `LOG.info("nop.jdk21.virtual-thread-pool-disabled")`。
- **失效面枚举（F3）**: `ICoreInitializer` 实现族 destroy 覆写核对：`IocCoreInitializer`（覆写，销毁容器，对称）、`Jdk21CommonsCoreInitializer`（本条，未覆写）、`LogbackConfiguratorInitializer`/`Log4j2ConfiguratorInitializer`/`JavaLoggerConfiguratorInitializer`（无资源持有，无需覆写，核过）。平台 `GlobalExecutors.register` 调用族：仅本类一处。
- **信心水平**: 确定
- **误报排除**: 不重复 CL-004（无界）与设施 C4-012（假参数）；本条四点均为独立可修的治理面缺陷；P3 判级已计入模块不在默认 classpath 的影响衰减。
- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P3**。四点亲核成立：destroy 未覆写（ICoreInitializer.destroy() 为 default 空方法、Jdk21CommonsCoreInitializer 仅 initialize()）；stats() 返回 null（实际 L40-42，初审记 L48-50 为漂移）；registerGlobalWorker 注册名=NOP_VIRTUAL_THREAD（实际 L25-27，初审记 L30-32 为漂移）；门控 CFG_ENABLE_VIRTUAL_THREAD_POOL 仅 initialize 读一次（L11）。模块门控前提亲核：根 pom L558 `<module>nop-commons-java21</module>` 确为注释停用。设施本体关联：WI 2.1 C6-round2 已将本执行器 destroy 僵尸窗口并入 C6-003 F3 扩展（同位点注记，主处置在设施单元）。P3 维持。现状=新发现，未修复。

### [nop-core-framework-C4-005] ShellRunner 将 stderr 阻塞读取任务提交到共享无界 cachedThreadPool，无超时命令挂起进程即泄漏阻塞线程

- **文件**: `nop-utils/nop-shell/src/main/java/io/nop/shell/ShellRunner.java:150-170,224-230,260-271`
- **证据片段**:
  ```java
  // L153-160：stderr 读取任务提交到平台共享无界池，任务体阻塞在 readLine 直至进程退出/流关闭
  return GlobalExecutors.cachedThreadPool().submit(() -> {
      final BufferedReader errReader = this.createReader(process.getErrorStream(), command.getEncoding());
      try {
          String line = errReader.readLine();
          while ((line != null) && !stopped.get()) { collector.onError(line); line = errReader.readLine(); }
  // L226：超时销毁任务在 globalTimer 一次性调度；timeout=0 时无任何清理调度
  return GlobalExecutors.globalTimer().schedule(cleanup, command.getTimeout(), TimeUnit.MILLISECONDS);
  ```
- **严重程度**: P3（模块门控：nop-shell 为 CLI/dev 工具模块，并发度=并发 shell 命令数，非服务热路径）
- **并发场景**: 共享 `nop-cached-thread-pool`（max=-1 无界 + SynchronousQueue，daemon，平台共享，同池还有 `WebPageExporter` 等使用方）上的阻塞读线程 vs 主调用线程（`readOutput` 在调用线程读 stdout）与 globalTimer 超时销毁任务。
- **触发时序**: T1（cached 线程）`readLine()` 阻塞等待 stderr；`command.getTimeout()>0` 时 T2（timer）到点 `safeDestroy`（L260-271：关三流 + `process.destroy()`）→ 流关闭使 T1 readLine 抛 IOException 或返回 EOF → 线程退出（该路径闭合）。`timeout==0`（L225 显式分支：无超时）且子进程永不退出也不写 EOF → T1 线程永久阻塞于 readLine，cached 池的 60s keepalive 对阻塞线程无效 → 每个挂起命令永久泄漏 1 个平台共享池线程直至 JVM 退出（daemon 不阻断退出，实害=线程与进程句柄驻留）。
- **现状**: 超时路径清理闭合（流关闭→读阻塞解除，设计自洽）；缺陷仅在载体选择：长生命周期阻塞 I/O 放上与业务共享的无界池，占用时长与进程生命周期耦合且无并发上界（N 并发命令=N 线程，K 挂起=K 永久占用）。
- **风险**: nop-shell 的调用方（nop-cli 命令、dev 工具）批量执行外部命令时，池线程数随并发无界增长；挂起进程使共享池驻留死线程，与 `WebPageExporter` 等同池业务互相可见（无界池无饥饿，但可观测性与资源占用劣化）。
- **建议**: stderr 读取改用 `Executors.newVirtualThreadPerTaskExecutor()`（nop-shell 运行于 Java 25，虚拟线程的阻塞读正是其设计场景，且天然每命令一线程、无共享池占用）；或为 ShellRunner 建独立小池并在 javadoc 声明 timeout=0 的线程驻留语义。
- **失效面枚举（F3）**: nop-utils 全部 10 子模块线程创建点核对：仅 `nop-shell/ShellRunner`（本条）与 `nop-commons-java21/VirtualThreadTaskExecutor`（C4-004）两处，其余 8 子模块（diff/fsm/git/image/router/match/table-validator/java-parser）基线 grep 零命中，逐一确认无同族站点。ShellRunner 内部另一载体 `scheduleTimeout`（一次性、任务体轻）判定正常。
- **信心水平**: 确定
- **误报排除**: 不是把"每命令一线程"当缺陷——无界池承载等价并发本身成立；立案点在"共享池上的无界阻塞占用"与"timeout=0 路径永久泄漏"的组合；P3 已计入模块的工具属性。
- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P3**。亲核成立：L153 cachedThreadPool().submit stderr 读取（readLine 阻塞至进程退出/流关闭）；scheduleTimeout L225-229 timeout=0 走 else 分支无任何清理调度；超时路径闭合（safeDestroy 实际 L259-271 关三流解除读阻塞）。P3 维持（CLI/dev 工具模块）。F3 独立重跑（七目录双模式 grep，见文末复核结论）零新站点，nop-utils 10 子模块核对维持。现状=新发现，未修复。

### [nop-core-framework-C4-006] SingleThreadConfigExecutor 参数纪律与关闭窗口：queue=100 深度不可达（参数虚假）、满载拒绝异常落入 globalTimer 调用方、deactivate→destroy 竞态、stop 未 start 时 NPE

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/starter/SingleThreadConfigExecutor.java:17-30`；`nop-core-framework/nop-config/src/main/java/io/nop/config/impl/ConfigChangeApplier.java:30-54`；`nop-core-framework/nop-config/src/main/java/io/nop/config/starter/ConfigStarter.java:267-273,481-487,526-551`
- **证据片段**:
  ```java
  // SingleThreadConfigExecutor L19：core=max=1，queue=100，daemon
  executor = DefaultThreadPoolExecutor.newExecutor("nop-config-executor", 1, 100, true);
  // ConfigChangeApplier L41-44：CAS 合并门——shouldUpdate 未被消费前不再入队（队列深度上界=1）
  if (this.active.get()) {
      if (this.shouldUpdate.compareAndSet(false, true)) {
          executor.execute(this::applyChange);
  // ConfigStarter doStop L529-535：deactivate 先于 configExecutor.stop()，但存在检查-行动窗口
  ```
- **严重程度**: P3（拒绝路径在运行期不可达，实际可达面仅关闭窗口竞态，后果良性；参数虚假为治理债）
- **并发场景**: 唯一生产者=globalTimer 线程（经 `configSource.addOnChange` 回调链：`AbstractFileConfigSource.refreshConfig` L60-62 / `JdbcConfigSource.tryTriggerChange` L104-106 → `requestUpdate`）+ 唯一消费者=nop-config-executor 单线程；停机时三者交错。
- **触发时序**: **(a) 队列满载不可达证明**：`executor.execute` 全平台唯一调用点是 `ConfigChangeApplier.requestUpdate`（IConfigExecutor 消费方 grep 全仓核对），入队前 `shouldUpdate` CAS(false→true) 成功才入队，仅 `applyChange` L49 消费时复位——故在途+排队任务总数 ≤1，queue=100 永不触顶，`StandardThreadPoolExecutor.RejectHandler` 的 `RejectedExecutionException` 运行期不可达，"满载丢弃/阻塞"问题不存在，但 100 的容量参数是虚假语义（读者会误以为允许 100 个积压变更）。**(b) 关闭窗口竞态（唯一可达拒绝路径）**：T1（timer）执行 `requestUpdate` 读 `active.get()==true` → T2（停机）`deactivate()` 置 active=false、`configExecutor.stop()` → executor shutdown → T1 继续 CAS 成功 → `execute` 于已 shutdown 执行器抛 `RejectedExecutionException`：文件源路径被 `refreshConfig` 外层 catch 捕获但**误标**为 `nop.config.refresh-file-source-fail`（L64-68，日志语义错误）且**跳过本轮 tasks 列表中剩余回调**；JDBC 源路径无守卫（见 C4-002，停机期无害但日志面同样误标）。**(c)** `stop()`（L27-29）在 `start()` 未被调过时 `executor.destroy()` NPE——`ConfigStarter.initConfigExecutor` L272 恒先 start，现实不可达，防御缺失。
- **现状**: 池参数有界、daemon、NamedThreadFactory、`destroy()`=shutdown()（无 `awaitTermination`，排队中的 applyChange 可能在 `safeCloseAll` 之后仍跑，但 `applyChange` 内 `active` 检查 L50-51 兜底，实害=无）。
- **风险**: 主要是治理债与排障误导：虚假容量参数、误标错误日志（把关window拒绝记成文件刷新失败）、防御缺失；无生产可用性实害。
- **建议**: (1) `queueSize` 收敛为小值（如 4）或 `DefaultThreadPoolExecutor.newExecutor` 增加 javadoc 说明该重载参数语义；(2) `requestUpdate` 对 `execute` 包 try-catch 并在拒绝时复位 `shouldUpdate`（防标志悬挂）+ 专用错误消息；(3) `stop()` 加 null 防御或状态检查；(4) `AbstractFileConfigSource` 的 catch 日志区分"刷新失败"与"onChange 回调失败"。
- **失效面枚举（F3）**: `DefaultThreadPoolExecutor.newExecutor` 三参重载全仓调用点核对：`SingleThreadConfigExecutor`（本条）、nop-kernel 设施内部与测试（设施单元辖区）；`IConfigExecutor` 实现族：`SingleThreadConfigExecutor`（默认回退实现，`ConfigStarter` L269-271）+ ServiceLoader 可插拔实现（当前仓内无其他实现类）。同族"execute 异常落入定时回调链"站点：`AbstractFileConfigSource.refreshConfig`（catch 全包，误标）、`JdbcConfigSource.tryTriggerChange`（无守卫，C4-002 立案）。
- **信心水平**: 确定
- **误报排除**: 未把"单线程串行配置应用"当缺陷（这是 IConfigExecutor SPI 的设计语义，且 CAS 合并是有意的去抖设计）；未把不可达的队列满载虚报为 P1 溢出——经深度上界证明后按参数纪律降 P3。
- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P3**。逐点亲核成立：newExecutor("nop-config-executor",1,100,true)（L19）；队列深度上界 ≤1 证明成立（ConfigChangeApplier L40-46 CAS 门 + applyChange L49 复位；IConfigExecutor 仓内唯一实现类=SingleThreadConfigExecutor 亲核）；(b) 停机窗口拒绝路径成立且文件源误标日志成立（AbstractFileConfigSource L60-68 for 循环在 try 内，剩余回调被跳过）；(c) stop() L23-25 直接解引用 executor 字段、未 start 即 NPE 防御缺失成立（ConfigStarter L272 恒先 start，现实不可达）；destroy()=shutdown() 无 awaitTermination（DefaultThreadPoolExecutor L91-92 亲核）。P3 维持。现状=新发现，未修复。

### [nop-core-framework-C4-007] 用户刷新代码与全部变更监听器串行运行于单线程 config-executor，无超时无隔离：一个阻塞的 bean refreshConfig 方法搁浅全平台配置传播

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:755-790,792-806`；`nop-core-framework/nop-config/src/main/java/io/nop/config/impl/ChangeSubscriptions.java:58-88`；`nop-core-framework/nop-config/src/main/java/io/nop/config/impl/DefaultConfigProvider.java:255-295`
- **证据片段**:
  ```java
  // BeanDefinition L764-768：bean 级订阅回调 = 用户 refreshConfig 方法 + 属性重绑定
  if (refreshConfigMethod != null || beanModel.getIocRefreshConfig() != null || !configDependants.isEmpty()) {
      refreshListener = (p, vars) -> { onRefreshConfig(bean, container, scope); };  // → L797 用户方法
  // ChangeSubscriptions L78-86：全部监听器在调用线程（=nop-config-executor 单线程）内联串行执行
  for (IConfigChangeListener listener : triggered) {
      try { listener.onConfigChange(provider, oldValues); }
      catch (Exception e) { LOG.error("nop.config.trigger-change-listener-fail:...", e); }
  ```
- **严重程度**: P2
- **并发场景**: 配置传播管线两级载体：globalTimer 线程（源刷新，C4-001）→ `nop-config-executor` 单线程（`ConfigChangeApplier.applyChange` → `DefaultConfigProvider.applyChange` L255-295 → `subscriptions.trigger` L294）；监听器集合含：bean 的 `ioc:refresh-config` 用户方法、`reactive-config-vars` 属性重绑定、`configDependants` 级联刷新（`BeanContainerImpl.refreshConfig` L359-371）、log level 等框架订阅（`ConfigStarter.configLogLevel` L499-504）。
- **触发时序**: T1（config-executor 单线程）执行某数据源 bean 的 `refreshConfig` 用户方法（如改 `jdbc.url` 后触发重连测试，连接测试无超时）→ 该方法无限期阻塞 → 同一变更批次中排在其后的全部监听器（其他 bean 的刷新、log level 热更）与后续所有配置变更的传播全部延迟，延迟无上界；CAS 合并语义保证通知不丢（`shouldUpdate` 保持 true，恢复后补跑一轮）但无任何看门狗/超时/慢监听器日志，搁浅不可观测。异常面已闭合（逐监听器 catch，L82-85 注释明示意图），仅阻塞面敞开。
- **现状**: 执行载体为框架默认回退实现（`SingleThreadConfigExecutor`，`ConfigStarter` L269-271），SPI 允许替换但默认面无任何用户代码执行治理；与 CL-003（reconcile 阻塞 configExecutor，C1/C2 轮已立案的使用方实例）同族——本条立案的是框架级根位点（`ChangeSubscriptions.trigger` 内联执行 + `BeanDefinition.onRefreshConfig` 用户方法链），修复此处即同时收敛该族。
- **风险**: 单个写得不好的 refreshConfig 方法（重连、远程拉取、锁等待）把"配置热更新"退化为"配置永不更新"，且因合并语义无积压告警、无队列深度可观测，故障定位困难（表现为"改了配置不生效"）。
- **建议**: (1) 在 `ChangeSubscriptions.trigger` 逐监听器计时，超阈值（如 1s）打慢监听器告警日志（最小改动，可观测性优先）；(2) 提供可选的带超时/独立执行器的 IConfigExecutor 默认实现（复用平台 `IThreadPoolExecutor` + `FutureHelper.futureGet(cancelToken, timeout)` 范式）；(3) owner 文档（docs-for-ai 配置指南）明示"refreshConfig 方法必须非阻塞"契约。
- **失效面枚举（F3）**: 同族"监听器/回调内联于治理线程"全清单：`ChangeSubscriptions.trigger`（本条根位点）、`ConfigStarter.configLogLevel` 订阅（轻量，随根位点受益）、`BeanDefinition.subscribeConfigChange` 两类监听器（本条）、nop-plugin reconcile 经 configExecutor（CL-003 已立案，互引）。`trigger` 的调用方唯一（`DefaultConfigProvider.applyChange` L294），无其他内联执行点遗漏。
- **信心水平**: 确定（机制链完整；具体阻塞体取决于用户代码，平台自身监听器均轻量）
- **误报排除**: 不是"单线程执行器"本身当缺陷（串行化配置应用是有意设计，保证顺序一致）；立案的是治理缺失（无超时/无慢路径观测），并已注明通知不丢的合并语义，未夸大为"配置丢失"。
- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P2**。亲核成立：BeanDefinition.subscribeConfigChange refreshListener→onRefreshConfig（实际 L755-789，onRefreshConfig 实际 L792-805：refreshConfigMethod.call0+runXpl+configDependants 级联）；ChangeSubscriptions.trigger 内联逐监听器（L78-87，异常面闭合、仅阻塞面敞开定位准确）；DefaultConfigProvider.applyChange 末行 subscriptions.trigger（实际 L294）。根位点（trigger 内联+用户方法链）与 CL-003 互引边界维持。P2 维持。现状=新发现，未修复。

---

## 维度集外线索

### [nop-core-framework-CL-008]（维度集外线索，不立案展开）ShutdownHook.destroyAll 再入守卫失效 + TreeSet 非线程安全 + 销毁后不清空（C1/C6 形态，设施位于 nop-kernel，非本单元辖区）

- **位置**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/lock/../service/ShutdownHook.java:42,59-69`（注册面 `nop-core-framework/nop-boot/src/main/java/io/nop/boot/NopApplication.java:39` 正常）
- **形态**: C1（共享可变状态）+ C6（生命周期对称性），非 C4。
- **要点**: (1) `disposables` 为普通 `TreeSet`，`addDisposable` 无同步；(2) `destroyAll` 守卫 `if (!destroyed.compareAndSet(false, true) && CollectionHelper.isEmpty(disposables)) return;`——CAS 失败且集合**非空**时不返回，第二调用方将与第一调用方并发迭代同一 TreeSet（CME/重复 destroy），该守卫疑似把"isEmpty 才放行二次销毁"写反（意向应为二次调用直接 return）；(3) 销毁后 `disposables` 不清空，二次调用全量重跑 destroy。当前生产代码 `addDisposable` 唯一调用点在 main 线程启动期、`destroyAll` 唯一触发点是 JVM hook（单次），并发窗口理论上需"显式 destroyAll 与 hook 并发"，现实可达性低，故仅登记线索。
- **移交建议**: nop-kernel（nop-commons）设施单元复核该守卫逻辑意向；最小修复为 disposables 换 `ConcurrentSkipListSet` + 守卫改为 CAS 失败即 return + 销毁后 clear。
- **复核处置**（独立复核子代理 2026-10-17）: **确认（线索），与 WI 2.1 C6-011 同位点（注明）**。守卫反转逐行亲核成立：ShutdownHook L72 `if (!destroyed.compareAndSet(false, true) && CollectionHelper.isEmpty(disposables)) return;`——二次调用时 disposables 永非空（无 remove API、销毁后不清空）→条件恒假必然重跑全部 disposables + TreeSet（L39）非线程安全并发迭代面；行号修正：守卫实际 L72（本条原记 42,59-69 为漂移）。设施单元 WI 2.1 已以 nop-kernel-concurrency-C6-011（P2，休眠结构性缺陷）立案并完成维度复核（其 C6-round2 第五节），主处置与修复归 nop-kernel 设施单元，本单元不重复立案。

### [nop-core-framework-CL-009]（维度集外线索，不立案展开）PluginManagerImpl syncGet 面复核终判（C3 形态线索归档：调用线程内联，无跨线程等待，无 C4 发现）

- **位置**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/PluginManagerImpl.java:179-183,407,659`；`VfsPluginDefinition.java:427-432`
- **形态**: C3（异步链），复核任务要求终判，登记归档。
- **要点**: `deactivatePlugin` = `FutureHelper.futureCall(...)`——`futureCall` 在**调用线程内同步执行** callable（`FutureHelper` L122-127，`try { task.call() }`）后包装完成态；`VfsPluginDefinition.deactivate()` 同样 `futureCall(doDeactivate)` 内联完成；`FutureHelper.syncGet` 对已完成 future 走 `getFromFuture` 即返（L158-172），不跨线程阻塞。三个 syncGet 站点（L179-180 deactivatePlugin、L407 deactivateIfActive、L659 reloadPlugin）全部在调用线程内联完成，无池参与、无跨线程等待、无挂起泄漏面。nop-plugin 全模块（api/manager/support 三个子模块 main 代码）基线 grep 零线程/零执行器创建（仅测试代码有 `Executors.newFixedThreadPool(2)`，非生产守卫不计）。**终判：C2 轮结论复核成立，本维度零立案。**
- **复核处置**（独立复核子代理 2026-10-17）: **确认（终判零立案维持）**。亲核：FutureHelper.futureCall（L122-128）在调用线程内联 task.call() 后包装完成态；syncGet（L158-175）对完成态 future 快速返回；PluginManagerImpl 三 syncGet 站点（deactivatePlugin/deactivateIfActive/reloadPlugin）全部调用线程内联、无池参与无跨线程等待。零立案终判维持。

---

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| nop-core-framework-C4-001 | P2 | nop-config/src/main/java/io/nop/config/source/file/AbstractFileConfigSource.java:44-46；jdbc/JdbcConfigSource.java:52-58 | 文件遍历与无超时 JDBC 查询直连 4 线程共享 globalTimer，故障放大为平台定时器面退化 |
| nop-core-framework-C4-002 | P2 | nop-config/src/main/java/io/nop/config/source/jdbc/JdbcConfigSource.java:71-98,159-168 | close 与在途刷新竞态致关闭后重建永不关闭的 Connection；onChanges 循环无守卫（周期任务可被异常静默杀死） |
| nop-core-framework-C4-003 | P1（门控） | nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:502-558；initialize/IocCoreInitializer.java:46 | concurrent-start 模式：启动体占共享 globalWorker、框架无 join 点、启动失败吞入日志后进程带死容器成功存活 |
| nop-core-framework-C4-004 | P3 | nop-utils/nop-commons-java21/src/main/java/io/nop/commons/jdk21/VirtualThreadTaskExecutor.java:30-32,48-50 | 初始化器 destroy 不对称、stats() 违约 null、registerGlobalWorker 名不副实、门控无观测 |
| nop-core-framework-C4-005 | P3 | nop-utils/nop-shell/src/main/java/io/nop/shell/ShellRunner.java:150-170 | stderr 阻塞读占共享无界 cachedThreadPool，timeout=0 挂起进程泄漏阻塞线程 |
| nop-core-framework-C4-006 | P3 | nop-config/src/main/java/io/nop/config/starter/SingleThreadConfigExecutor.java:17-30 | queue=100 深度不可达参数虚假；拒绝异常误标日志；deactivate→destroy 竞态窗口；stop 未 start NPE |
| nop-core-framework-C4-007 | P2 | nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:755-806；nop-config/src/main/java/io/nop/config/impl/ChangeSubscriptions.java:58-88 | 用户 refreshConfig 与全部变更监听器串行内联于单线程 config-executor，阻塞即搁浅全平台配置传播且不可观测 |

| CL 线索 | 形态 | 一句话摘要 |
|---------|------|-----------|
| nop-core-framework-CL-008 | C1/C6（nop-kernel 辖区） | ShutdownHook 再入守卫逻辑反写 + TreeSet 非线程安全 + 销毁不清空 |
| nop-core-framework-CL-009 | C3 终判归档 | PluginManagerImpl syncGet 全部站点调用线程内联，复核成立，零立案 |

**统计**: 第 1 轮初审发现 7 条（P1×1[门控] / P2×3 / P3×3），维度集外线索 2 条（CL-008、CL-009，CL 编号自 CL-008 起连续）。预注互引不重复立案：设施本体（WI 2.1 C4-001 globalTimer 死配置、C4-012 nop-virtual-thread 假参数）、CL-003（reconcile 阻塞 configExecutor）、CL-004（VirtualThreadTaskExecutor 无界）。

**核实为无缺陷的记录**（防复核轮重查）: (1) `BeanScopeImpl.add/close` 经 `synchronized(beans)` 原子化（L50-56,81-84），concurrent-start 失败路径在途 bean 创建不会泄漏进已关闭 scope（add 抛 ALREADY_CLOSED）；(2) `TaskExecutionGraph` 未启动任务经 cancelToken 跳过（L197-203），ioc 启动图失败后 `stop()` 的池清理语义成立；(3) `ConfigChangeApplier` 逐监听器异常闭合（`ChangeSubscriptions` L82-85），F9 异常吞没面已闭合，仅阻塞面敞开（C4-007）；(4) `AbstractFileConfigSource.refreshConfig` catch-all 已防 scheduleWithFixedDelay 异常死循环（L64-68）；(5) nop-boot 启动序无 main 之外的自建线程，`NopShutdownHook` 注册面单点正常；(6) nop-log/nop-security 零执行载体，预期零发现成立。

---

## 维度复核结论

**复核信息**: 独立复核子代理，2026-10-17，与第 1 轮初审及第 2 轮深挖相互独立，以怀疑姿态逐条复核。仓库 `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`，HEAD `6dceb20dc3`）。方法：两报告全部 11 条发现 + 4 条 CL 逐条亲开 live code 行级核对（行号漂移以实际代码为准修正）；历史盲审按任务指令解除（已读 `ai-dev/audits/cra-index.md`、WI 2.1 目录 `2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency`、git log 修复对照）；F3 基线 grep 独立重跑。

### 裁定汇总表

| 编号 | 初判 | 复核裁定 | 现状 | 备注 |
|------|------|---------|------|------|
| C4-001 | P2 | **保留 P2** | 新发现，未修复 | 与 WI 2.1 C4-001 为同设施两面（使用方占用面 vs 设施死配置面），互补独立 |
| C4-002 | P2 | **保留 P2** | 新发现，未修复 | 连接泄漏链逐行亲核闭合 |
| C4-003 | P1（门控） | **保留 P1（门控）——终裁维持** | 新发现，未修复 | 终裁理由见下节 |
| C4-004 | P3 | **保留 P3** | 新发现，未修复 | 设施兄弟面：WI 2.1 C6-003 F3 同位点注记 |
| C4-005 | P3 | **保留 P3** | 新发现，未修复 | — |
| C4-006 | P3 | **保留 P3** | 新发现，未修复 | — |
| C4-007 | P2 | **保留 P2** | 新发现，未修复 | — |
| C4-008 | P2（门控） | **保留 P2** | 新发现，未修复 | 使用方/框架面维持立案；设施根因移交 CL-019，修复两联动 |
| C4-009 | P2（双门控） | **保留 P2** | 新发现，未修复 | restart 生产调用方=nop-autotest 两处（AutoTestCase L219、JunitBaseTestCase L36）亲核 |
| C4-010 | P2 | **保留 P2** | 新发现，未修复 | — |
| C4-011 | P3（门控） | **保留 P3** | 新发现，未修复 | — |
| CL-008 | 集外线索 | **确认** | = WI 2.1 C6-011 同位点 | 主处置在设施单元（已立案 P2 休眠），本单元不重复立案 |
| CL-009 | 集外终判 | **确认（零立案维持）** | — | futureCall 调用线程内联亲核 |
| CL-019 | 设施移交 | **确认移交** | WI 2.1 未覆盖的新位点 | 移交有效且必要（对照面核实见下） |
| CL-020 | 设施移交 | **确认** | = WI 2.1 C6-003 同位点 | 主处置在设施单元，本单元不重复立案 |

### C4-003 终裁：P1（门控）维持，不降 P2 条件化

1. **触发结构不满足降档前提**：降 P2 规则针对「≥3 罕见条件合取或纯理论」；本条触发=单门控（concurrent-start=true）+ 单常见条件（任一 bean init 抛 Exception），触发链每步亲核可信（start() L503-512 → whenComplete → handleStartError L526-533 → stop()），非理论风险。
2. **门控是文档声明的公开加速开关**：`docs/performance/optimize-app.md:17` 明文推荐「设置为true, 则启动时在线程池上执行bean容器的初始化操作，不阻塞主线程」——开启属被官方性能指南背书的单旗标运维动作。开启者无从知情失败语义代价：@Description 误标（IocConfigs L27，把 `nop.ioc.enabled` 的语义复制给了本项）+ docs-for-ai 零收录（grep 实测零命中）+ 失败语义零文档，三重文档债加重"无知情开启"风险，支持 P1 而非削弱。
3. **触发后果监督不可见且不可逆**：standalone 族 exit-0 清洁退出伪装成功（run-success 字样 + systemd on-failure 不重启）；quarkus 族三绿假活（NopQuarkusBeanContainer start/stop/restart 全空实现，容器状态与进程/端口/health 零联动）。容器永久 NOT_STARTED 贯穿全进程生命周期，非"仅启动期单线程触达"类可衰减面。
4. **非孤立面**：同门控延伸出运行期面（C4-009 restart 代际竞态自毁新生代），故障面不止于启动窗口。
5. **附注（不改事实）**：子面 (a) 启动体占共享 globalWorker 若按默认态暴露单独计为 P2 条件化；P1 由 (b) 无 join 点 + (c) 失败不传导承载。修复（框架级 join + 失败传导 + 超时）无论判级如何均为必做；判级仅决定排期优先级，交 7.1 协调方参考本附注。仓内现状注记：唯一 yaml 显式配置为 nop-quarkus-demo `concurrent-start: false`（关闭），仓内零开启。

### CL 处置详注

- **CL-008（确认，同位点并入）**：守卫反转逐行亲核成立（ShutdownHook L72——二次调用时 disposables 永非空，条件恒假必然重跑全部销毁；TreeSet L39 非线程安全）。WI 2.1 已以 nop-kernel-concurrency-C6-011（P2 休眠）立案并完成维度复核，主处置归设施单元。行号修正：守卫实际 L72（原记 42,59-69 为漂移）。
- **CL-009（确认，零立案维持）**：`FutureHelper.futureCall` L122-128 调用线程内联亲核，三站点无池参与无跨线程等待。
- **CL-019（确认，移交有效且必要）**：亲核 runTask 实际 L223 `catch (Exception e)`。对照面核实：WI 2.1 全目录 grep 无本位点立案——其 C3-001 针对周期调度设施（DefaultScheduledExecutor/BindScheduledExecutor 杀链语义），其 Error 孤儿案针对 `ExecuteHelper.throttleExecute`（不同类）——CL-019 为 WI 2.1 未覆盖的设施新位点；回归面含 IoC 启动图（C4-008 形态(a) 为其用例）。
- **CL-020（确认，同位点并入）**：亲核 g_executors computeIfAbsent 懒创建、CoreInitialization.destroy（L158-206）全程不触达 g_executors、唯一收敛路径=同名 register 撞旧 old.destroy()（L102-107）。WI 2.1 已以 C6-003 立案「全局池无任何平台级释放路径（缺失）」且其 C6-round2 将 Jdk21CommonsCoreInitializer 僵尸窗口并入 C6-003 F3，主处置归设施单元。

### 行号漂移修正清单（机制全部核实不变）

| 条目 | 原记录 | 实测 |
|------|--------|------|
| C4-004 registerGlobalWorker | L30-32 | L25-27 |
| C4-004 stats() | L48-50 | L40-42 |
| C4-007 onRefreshConfig | L792-806（第 1 轮记 755-790/792-806） | subscribeConfigChange L755-789、onRefreshConfig L792-805 |
| CL-008 守卫 | 42,59-69 | TreeSet L39、destroyed L41、守卫 L72 |
| 第 2 轮 ①c standalone 默认 task | L92-94 | L75-77 |
| 第 2 轮 ①c NopQuarkusBeanContainer 空实现 | L34-46 | L39-51 |
| 第 2 轮 ③ fillResolvedDepends refPos 过滤 | L173-179 | L181-183 |
| C4-011 CoreInitialization.destroy | L146-190 | L158-206（L167 逆序、L173-178 逐项容错） |
| C4-008 syncGet | L158-174 | L158-175 |

### F3 完备性独立复核

双模式基线 grep（第 1 轮模式组 + `new NopThread(`/`Thread.ofVirtual`/`new Timer(`/`ThreadFactory`/`newSingleThreadExecutor`）于七目录 src/main 独立重跑：执行载体命中文件集={AbstractFileConfigSource、JdbcConfigSource、SingleThreadConfigExecutor、BeanContainerImpl、VirtualThreadTaskExecutor、ShellRunner} 共 6 文件，`GlobalExecutors.*` 消费站点={AbstractFileConfigSource L45、JdbcConfigSource L54、BeanContainerImpl L542、ShellRunner L153/L226} 共 5 站点 + 自建 1（VirtualThreadTaskExecutor）——与第 1 轮 11 行全景及第 2 轮 7 站点终表完全一致，**零缺漏、零新站点，F3 补全为空**。第 2 轮正面核实抽验 4 项全部成立：① BeanScopeImpl add/close 原子化（L74-77/L99-101）；② TaskQueue 弹性扩容（L69-83，启动图有效并发上界=30）；③ waitPrevTasks retainAll 降级（TaskExecutionGraph L161-169）；④ BeanTopologySorter verifyOrderConstraints 无条件执行（sort 流程 L79 调用）+ fillResolvedDepends refPos 位置过滤（L181-183）——第 2 轮死锁假设证伪链复核成立。

### 现状标注

11 条发现全部为**新发现，未修复**：git log 核实本审计日（2026-10-17）前后无任何针对上述发现点的修复提交；相关文件上的历史修复注释（AbstractFileConfigSource catch-all/vars 回写、BeanScopeImpl add/close 原子化、ShellRunner stdin 关闭等）属此前 check-findings 战役产出，与本维度 11 条无重叠。

### 复核统计

复核 11 条发现 + 4 条 CL = 15 项；**保留 11（P1×1[门控维持] / P2×6 / P3×4）、降级 0、驳回 0**；CL 确认 4（同位点并入 2：CL-008→WI 2.1 C6-011、CL-020→WI 2.1 C6-003；零立案维持 1：CL-009；移交有效 1：CL-019）。跨单元联动修复面：C4-003/C4-008/C4-009/C4-011 与 CL-019 同批（IoC 启动图 join/失败传导/超时/代际令牌一体修复）；C4-001 与 WI 2.1 C4-001 同设施统筹。

---

## 最终保留项（复核终版）

| 编号 | 严重程度（复核终裁） | 文件路径 | 一句话摘要 | 现状 |
|------|---------------------|---------|-----------|------|
| nop-core-framework-C4-001 | P2 | nop-config/src/main/java/io/nop/config/source/file/AbstractFileConfigSource.java:45；jdbc/JdbcConfigSource.java:54 | 文件遍历与无超时 JDBC 查询直连 4 线程共享 globalTimer，故障放大为平台定时器面退化 | 新发现，未修复 |
| nop-core-framework-C4-002 | P2 | nop-config/src/main/java/io/nop/config/source/jdbc/JdbcConfigSource.java:71-98,159-168 | close 与在途刷新竞态致关闭后重建永不关闭的 Connection；onChanges 循环无守卫 | 新发现，未修复 |
| nop-core-framework-C4-003 | **P1（门控，终裁维持）** | nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:502-558；initialize/IocCoreInitializer.java:46 | concurrent-start 模式：启动体占共享 globalWorker、框架无 join 点、启动失败吞入日志后进程带死容器成功存活 | 新发现，未修复 |
| nop-core-framework-C4-004 | P3 | nop-utils/nop-commons-java21/src/main/java/io/nop/commons/jdk21/VirtualThreadTaskExecutor.java:25-27,40-42 | 初始化器 destroy 不对称、stats() 违约 null、registerGlobalWorker 名不副实、门控无观测 | 新发现，未修复 |
| nop-core-framework-C4-005 | P3 | nop-utils/nop-shell/src/main/java/io/nop/shell/ShellRunner.java:150-170,224-230 | stderr 阻塞读占共享无界 cachedThreadPool，timeout=0 挂起进程泄漏阻塞线程 | 新发现，未修复 |
| nop-core-framework-C4-006 | P3 | nop-config/src/main/java/io/nop/config/starter/SingleThreadConfigExecutor.java:17-30 | queue=100 深度不可达参数虚假；拒绝异常误标日志；停机窗口竞态；stop 未 start NPE | 新发现，未修复 |
| nop-core-framework-C4-007 | P2 | nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:755-805；nop-config/src/main/java/io/nop/config/impl/ChangeSubscriptions.java:58-88 | 用户 refreshConfig 与全部变更监听器串行内联于单线程 config-executor，阻塞即搁浅全平台配置传播且不可观测 | 新发现，未修复 |
| nop-core-framework-C4-008 | P2（门控） | nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:503-512,526-539 | 完成回调族三形态吞错：Error 使图 future 孤儿、成功回调异常零日志、全链无超时永久 pending（设施根因=CL-019，修复两联动） | 新发现，未修复 |
| nop-core-framework-C4-009 | P2（双门控） | nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:452-455,594-617；nop-autotest AutoTestCase.java:219、JunitBaseTestCase.java:36 | restart 代际竞态：旧图 whenComplete 自毁新生代容器；在途旧任务混入新 scope | 新发现，未修复 |
| nop-core-framework-C4-010 | P2 | nop-ioc/src/main/java/io/nop/ioc/impl/BeanScopeImpl.java:29,104 | singleton 销毁序=CHM 哈希迭代序，创建序拓扑契约在销毁侧零对应 | 新发现，未修复 |
| nop-core-framework-C4-011 | P3（门控） | nop-ioc/src/main/java/io/nop/ioc/initialize/IocCoreInitializer.java:60；nop-kernel/nop-core/src/main/java/io/nop/core/initialize/CoreInitialization.java:158-206 | destroy 路径 IOC stop 不 join 在途启动图任务，后续 VFS/config 源销毁与在途 bean 创建跨线程竞态 | 新发现，未修复 |

| CL 线索 | 复核处置 | 一句话摘要 |
|---------|---------|-----------|
| nop-core-framework-CL-008 | 确认（=WI 2.1 C6-011 同位点，主处置在设施单元） | ShutdownHook 再入守卫逻辑反写 + TreeSet 非线程安全 + 销毁不清空 |
| nop-core-framework-CL-009 | 确认（零立案终判维持） | PluginManagerImpl syncGet 全部站点调用线程内联 |
| nop-core-framework-CL-019 | 确认移交（WI 2.1 未覆盖新位点，回归面含 IoC 启动图） | TaskExecutionGraph.runTask 仅 catch(Exception)，任务体 Error 使图 future 永久孤儿（C4-008 形态(a) 设施根因） |
| nop-core-framework-CL-020 | 确认（=WI 2.1 C6-003 同位点，主处置在设施单元） | GlobalExecutors 三全局池零生命周期挂接，destroy 不触达 |
