# C6 维度初审报告：资源生命周期与泄漏（nop-kernel 并发设施）

## 头部信息

| 项 | 内容 |
|---|---|
| **审计单元** | nop-kernel-concurrency / C6（资源生命周期与泄漏） |
| **仓库路径** | `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit` |
| **分支** | `concurrency-audit-remediation` |
| **轮次** | 第 1 轮初审（盲审） |
| **审计人** | C6 维度初审子代理（盲审轮次，独立任务） |

### 维度定义（C6——资源生命周期与泄漏）

线程/池/连接/文件句柄的关闭责任与配对；注册/注销对称；异常与取消路径的资源释放；destroy 语义完整性；监听器/回调注册泄漏。

### 盲审纪律声明

本报告为盲审轮次产出。审计过程中**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅阅读了方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md`、`ai-dev/skills/deep-audit-prompts.md`（允许）与当前 live code。C1–C5 轮已立案清单仅以任务下发文本中的标题列表形式获知（用于同族去重），未查阅其正文。

### 检查范围说明

- **A 面**：`nop-kernel/nop-commons/src/main/java/io/nop/commons/{concurrent,cache,pool}`（85 files / 7,550 行，基线 `_tmp/cra-baseline-nop-kernel-concurrency.txt`），重点：`executor`（GlobalExecutors、DefaultThreadPoolExecutor、DefaultScheduledExecutor、StandardThreadPoolExecutor、ExecutorHelper、RateLimitExecutorImpl、SequentialTaskExecutor、LimitedExecutor、ContinuationExecutor、SyncThreadPoolExecutor）、`batch`（AbstractBatchProcessService、BlockingSourceConsumeTask）、`impl`（OverflowBlockingQueue、DedupBlockingQueue）、`lock`（LocalResourceLockManager 全族）、`thread`（NamedThreadLocal、NamedThreadFactory、ThreadHelper、NopThread）、`cache`（LocalCache、CacheConfig、GlobalCacheRegistry、LocalCacheProvider、MapCache、EhcacheCache）、`pool`（6 files）。
- **B 面**（按基线 B-face enumeration「模型/元数据缓存」语义取子集）：`nop-core` 的 `resource/cache`（ResourceLoadingCache、ResourceCacheEntry、CacheEntryManagement、ReactiveObjectMap、ResourceTimestampCache）、`resource/component/ResourceComponentManager`、`resource/tenant`（ResourceTenantManager、TenantAwareResourceLoadingCache、TenantAwareDependencyManager）、`resource/watch`（FileWatcher、NioFileWatchService、FileWatchEntry）、`resource/VirtualFileSystem`、`reflect/ReflectionManager`、`execution/DefaultTaskExecutionQueue`、`resource/zip/ZipFileWatcher`；`nop-xlang` 的注册表单例（ScriptCompilerRegistry、StdDomainRegistry、EvalBackendRegistry 等——均为 append-only 常量注册表，无注销泄漏面，本轮不立案）。
- **生命周期宿主追索**（判定 destroy/init 配对所必需，跨出模块只读）：`ShutdownHook`、`CoreInitialization`（initialize/destroy/reinitialize）、`NopApplication`、`Jdk21CommonsCoreInitializer`/`VirtualThreadTaskExecutor`、`DefaultTaskExecutionQueue`、`FileWatcherFactory`（nop-cli）、`ImportDbTool`/`ExportDbTool`（nop-batch-exp）。micrometer 弱引用语义经字节码核验（1.15.x `DefaultGauge`/`CacheMeterBinder`/`CumulativeFunctionCounter` 均持 `WeakReference`）。
- **同族去重预注**：以下为下发文本告知的已立案族，本轮不重复立案，只审同族其他站点与未立案泄漏面：RoundRobinSupplier use-after-close、scheduleWithRandomDelay 幽灵、debounce、DefaultScheduledExecutor TOCTOU+destroy 语义、ReactiveObjectMap、CycleSynchronizer、ResourceMultiLock、LocalFileLock tryLock 不关句柄、ResourceCacheEntry 死锁+clear use-after-destroy、computeIfAbsent 锁内 transform、DedupBlockingQueue 墓碑、BindScheduledExecutor 全家、throttleExecute、LimitedExecutor、SequentialTaskExecutor、BlockingSourceConsumeTask、AbstractBatchProcessService destroy 丢消息、池 destroy 语义族、g_executors 借出可毁、updateThreadPool 反置、XLangSemantics 三 CHM、LocalCache 无界契约、destroyOnRemove 断链（CacheConfig 拷贝丢字段）、FileWatcher 无界队列、promiseMap、容量 0 语义分裂。

### 资源获取点配对表（C6 输出要求 1）

| # | 资源 | 获取点 | 释放/注销点 | 配对判定 |
|---|---|---|---|---|
| 1 | 线程池（DefaultThreadPoolExecutor） | `init()`/`newExecutor` | `destroy()`=`shutdown()`，无 awaitTermination | **部分**：排队任务的 wrapper future 永不完成（C6-001）；destroy 后不可 re-init、`stats()` 悬空 |
| 2 | 定时池（DefaultScheduledExecutor） | `init()` | `destroy()`=`shutdownNow()`+置 null | 良好（中断在飞任务的丢任务面归 C4 已立案族） |
| 3 | 全局池 `g_executors`（globalTimer/globalWorker/cachedThreadPool） | `computeIfAbsent` 惰性 init | **无任何平台级释放路径** | **缺失**（C6-003） |
| 4 | `g_executors.register` 覆盖 | `put` | 旧实例立即 `destroy()` | **破坏性覆盖**：借出引用失效 + 队列孤儿（C6-002） |
| 5 | `g_executors.unregister` | — | `remove` 不 destroy；**平台零调用方** | **不对称**（C6-002） |
| 6 | LocalResourceLockManager 清理定时任务 | `init` scheduleWithFixedDelay | `destroy` cancel(false) | 良好 |
| 7 | NioFileWatchService watcher 线程 | `doStart` submit | `doStop` cancel(true)+shutdown+close(WatchService) | 良好（缺 awaitTermination；非 daemon 面 → C6-007） |
| 8 | WatchKey/FileWatchEntry | `register`/`watch`（含递归 addSubEntry） | `unwatch`/`doStop` cancel | **部分**：父 subEntries 不摘除、重注册交叉误伤（C6-006） |
| 9 | RateLimitExecutorImpl promiseMap | throttle/debounce put | 任务 finally 条件 remove | 良好（被 cancel 的 future 残留至下次 put 覆盖，有界） |
| 10 | Micrometer meter 绑定 | `registerMetrics`（池）/`LocalCache.registerMetrics` | **无 unbind** | **缺失**（C6-004；已核验 1.15.x weakref，非内存滞留） |
| 11 | LocalCache 条目销毁回调 | put/removalListener | destroyOnRemove=true 时 safeDestroy | **部分**：平台模型缓存未启用 + `destroy()`=cleanUp 不失效（C6-005） |
| 12 | GlobalCacheRegistry | `register`（重复即抛） | `unregister` 平台零调用方；destroy 只 `clear()` 内容 | 部分（单例缓存常驻属设计边界，动态注册方无出路） |
| 13 | NamedThreadLocal | set | 空栈时 remove（ResourceDependsManager L97-98/L117-118）+ `close()` | 良好（设施层无钩子/无文档，见 C6-008 附注） |
| 14 | IPool 借还 | 接口定义 | **全平台无实现、无调用方** | 死设施（C6-008） |
| 15 | IContext ThreadLocal | ExecutorHelper.submit 任务体 | finally `detachContext()` | 良好 |
| 16 | ContinuationExecutor ThreadLocal | `withInitial` | `remove()` 存在但零调用 | 良好偏差（残留空 ArrayDeque，有界于线程数） |
| 17 | 虚拟线程执行器 | registerGlobalWorker | destroy=shutdown | 部分（归入 C6-002 触发链） |

---

## 发现条目

### [nop-kernel-concurrency-C6-001] executor.destroy()/shutdown() 后排队任务的 wrapper future 永不完成，等待方永久挂起

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/DefaultThreadPoolExecutor.java:91-93`；根因配套 `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:128-150`；生产受害路径 `nop-kernel/nop-core/src/main/java/io/nop/core/execution/DefaultTaskExecutionQueue.java:137-156（doStart/doStop）`
- **证据片段**:
  ```java
  // DefaultThreadPoolExecutor.java
  @Override
  public void destroy() {
      executor.shutdown();          // 仅 shutdown：队列中未启动任务被静默丢弃
  }

  // ExecutorHelper.java —— future 的完成点在任务体内
  Future<?> f = executor.submit(new Callable<Object>() {
      public Object call() {
          try { T result = task.call(); future.complete(result); ... }
      }
  });
  // 任务未执行 => future 永不 complete/completeExceptionally
  ```
- **严重程度**: P1
- **并发场景**: 调用 `ITaskExecutionQueue.addTaskIfAbsent` 的业务线程在 `state.getPromise()` 上等待；另一线程（IoC 容器关闭、管理操作）调用 `queue.stop()` → `doStop()` → `executor.destroy()`。
- **触发时序**: T1 提交任务 A，任务 A 进入池队列（pool 忙）→ T2 调用 `doStop()`：`shutdown()` 使 A 停留在队列中且永不执行（JDK `shutdown` 语义：排空但不运行不取消）→ A 的 `State.promise` 永不 complete → T1 若已获取 `getPromise()` 并 `get()`/`FutureHelper.get()` 则**永久阻塞**；同时 `states` map 条目永不移除，`getPendingTaskCount()` 永久虚高。注意 `FutureHelper.bindCancel` 只在 future 被外部 cancel 时传导，shutdown 不会触发它。
- **现状**: 平台全部 `IThreadPoolExecutor.destroy()` 实现均只调用 `shutdown()`/`shutdownNow()`，从不遍历排空队列补完（`completeExceptionally`）被丢弃任务的 wrapper future；平台自带的 `ThreadHelper.shutdownAndAwaitTermination`（正确范式）存在但无任何池 destroy 路径使用它。
- **风险**: 任何"停止队列/重启模块/重新部署"操作后，等待任务完成的调用方（HTTP 请求线程、编排器）永久挂起，且无异常、无日志；挂死对健康检测不可见（对照 F5）。全局池因 C6-003 从不 destroy，本缺陷在"自有池 + stop"路径必然可触发。
- **建议**: `DefaultThreadPoolExecutor.destroy()` 改为 `executor.shutdown(); List<Runnable> dropped = executor.shutdownNow();` 后对 dropped 中 `ExecutorHelper` 包装任务回调 `completeExceptionally(ERR_DESTROYED)`（需 `ExecutorHelper.submit` 返回或注册排队任务清单）；或统一改用 `ThreadHelper.shutdownAndAwaitTermination(service, timeout, unit)` 并在文档中规定 destroy 必须使所有在途 future 终态化。
- **失效面枚举**（F3，同族 destroy/关闭点逐一核对）: ① `DefaultThreadPoolExecutor.destroy()`（本条主根因）——排队任务孤儿；② `DefaultScheduledExecutor.destroy()`（shutdownNow）——已调度未触发的 `ExecutorHelper.schedule` wrapper future 同样孤儿（等待 `schedule(...).get()` 者挂起）；running 中任务因中断走 catch → `completeExceptionally`，不受影响；③ `VirtualThreadTaskExecutor.destroy()`（shutdown）——per-task executor 无排队面，挂起面限于 destroy 前已 accept 未跑的任务；④ `SyncThreadPoolExecutor.destroy()` 空——无资源；⑤ `GlobalExecutors.register` 覆盖旧池时调用的 `old.destroy()`——同①（并入 C6-002）；⑥ `AbstractBatchProcessService.destroy()`——submit 的 wrapper future 被丢弃但无等待方（消息丢失面已属 C 轮已立案族，不重复）；⑦ `NioFileWatchService.doStop`——cancel(true) 显式取消已提交任务，future 终态化，**不受本缺陷影响**（正面样本）。
- **信心水平**: 确定（JDK `ThreadPoolExecutor.shutdown` 语义 + 完成点在任务体内，代码可直接推演）
- **误报排除**: 不属于"池 destroy 语义族"已立案内容的重复——该族立案的是 destroy 语义/TOCTOU，本条是**排队 future 永不终态化导致等待方挂死**这一独立后果面，且给出跨模块具体受害链（DefaultTaskExecutionQueue promise）；测试代码不涉及。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **降级 P1→P2（条件化）；主根因主张驳回，残余面保留**。a) 完成点在任务体内核实成立（`ExecutorHelper.submit` L128-150/L152-174、`schedule` L176-199）。b) **初审 JDK 语义前提错误**：`ThreadPoolExecutor.shutdown()` 的 JDK 契约是「previously submitted tasks are executed」——排队任务会被继续执行并排空队列，仅 `shutdownNow()` 丢弃队列；初审证据注释「队列中未启动任务被静默丢弃」与触发时序「shutdown() 使 A 停留在队列中且永不执行」不成立。经 JDK javadoc + 实测程序复核（`_tmp/c6-review/ShutdownSemanticsTest.java`：忙池+排队任务+shutdown()，排队任务的 wrapper future 正常完成；`ShutdownNowTest.java`：shutdownNow 丢弃 delayed 任务的 wrapper future 永不完成）。另核 `TaskQueue.force()`（L55-66）在 `parent.isShutdown()` 时抛 REE——destroy 后新提交 fail-fast，无僵尸入队路径。故 **S1（DefaultThreadPoolExecutor 主根因）不成立**：destroy()=shutdown() 后排队任务照常执行、wrapper future 照常终态化。c) 残余成立面 = **S2 变体**：`DefaultScheduledExecutor.destroy()`=shutdownNow() 丢弃排队任务、其 wrapper future 永不终态化（实测证实；destroy 可达：`nopJobScheduledExecutor`/`nopRpcPollScheduledExecutor` 均声明 destroy-method="destroy"）；F3 补全第⑧项：`BindScheduledExecutor.destroy()`→`timer.destroy()` 同 shutdownNow 变体（登记，属 C4 已立案族不展开）。d) 生产等待方为零：S7 修正亲核成立（`nopTaskExecutionQueue` bean 全仓 src/main 唯一出现点为 beans.xml 声明本身，ITaskExecutionQueue 无消费者）；S4 配对亲核成立（LocalJobScheduler deactivate 先行 cancelScheduledFire/cancelRunning）；S3 走 globalTimer 永不 destroy。按判级口径「当前无生产调用方站点条件化降档」→ **P2**（条件：池 destroy 时内有排队任务且存在等待方）。throttleExecute 信号量许可泄漏同理条件化（仅任务被 shutdownNow 丢弃时 finally 不执行）。e) S10 机制修正：单表失败后 destroy()=shutdown() 下其余排队表任务**仍会继续执行**（非「静默丢弃」），结果被忽略；CLI 非 daemon 线程会等待其跑完。修复建议维持（destroy 终态化补偿 + 与 C6-009 同 diff 接线）。现状：已知未修复（git log 核实设施文件审计后无提交）。

---

### [nop-kernel-concurrency-C6-002] GlobalExecutors.register 覆盖同名字池即同步 destroy 旧池；unregister 无 destroy 且平台零调用方；allExecutors() 暴露可变注册表

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:102-111`；触发链 `nop-utils/nop-commons-java21/src/main/java/io/nop/commons/jdk21/Jdk21CommonsCoreInitializer.java:9-13`、`VirtualThreadTaskExecutor.java:25-26`
- **证据片段**:
  ```java
  public static void register(IThreadPoolExecutor executor) {
      IThreadPoolExecutor old = g_executors.put(executor.getName(), executor);
      if (old != null) {
          old.destroy();          // 覆盖即销毁：借出方仍持有旧引用
      }
  }

  public static IThreadPoolExecutor unregister(String name) {
      return g_executors.remove(name);   // 不 destroy；全平台 grep 零调用方
  }
  ```
- **严重程度**: P1
- **并发场景**: embedder/测试/基准中 `CoreInitialization.destroy()` → `initialize()` 生命周期循环；任何按 `GlobalExecutors.getExecutor(name)/requireExecutor(name)` 解析过池引用并长期持有的组件（`ModelBasedBatchTaskBuilderFactory:210`、`AbstractBizEntityService:59`、`TaskFlowManagerImpl:85` 均以配置名解析）；与 `registerGlobalWorker()` 所在初始化线程。
- **触发时序**: 第 1 轮 initialize：`CFG_ENABLE_VIRTUAL_THREAD_POOL` 默认 true（`CommonConfigs.java:67`），ServiceLoader 注册的 `Jdk21CommonsCoreInitializer` 执行 `registerGlobalWorker()`，"nop-virtual-thread" 入表 → 业务模块 X 解析并缓存该 executor 引用，且已 submit 任务（wrapper future 未完成，见 C6-001 根因同源）→ 宿主调用 `CoreInitialization.destroy()` 后再次 `initialize()` → `registerGlobalWorker()` 再次执行 → `register()` 将新实例 put 同名键并**同步 `old.destroy()`**（`shutdown()`）→ X 手中的旧引用变为已关闭池：execute 抛 RejectedExecutionException、在飞任务执行完队列即停、排队任务 future 永不终态化（C6-001 缺陷叠加）。`unregister` 正相反：remove 后不 destroy，旧池线程照常运行——同一注册表两个方向的责任分配互相矛盾。
- **现状**: register 的"覆盖即销毁"没有任何使用中检查（引用计数/宽限期/惰性销毁），unregister 又完全不放资源；`allExecutors()`（L34-36）直接返回内部 `g_executors` 活引用，外部可绕过 register/unregister 直接增删。
- **风险**: 平台级"借出即可被销毁且不可恢复"在 register 侧的制度化：任何按文档用 `register()` 替换池的宿主都会静默打断全部旧池借用方；排队任务等待方永久挂起（与 C6-001 复合）；unregister 零调用意味着任何动态注册的池只进不出（本平台当前仅 virtual-thread 一个动态注册点，累积面暂为 1）。
- **建议**: (1) register 覆盖时改为 `unregister + 延迟销毁`（如交给 ShutdownHook 或在新实例上提供 `evictOldPool` 显式调用）；(2) unregister 补 destroy 或重命名为 `detach` 并在 javadoc 明确释放责任；(3) `allExecutors()` 返回 `Collections.unmodifiableMap(new HashMap<>(g_executors))` 快照；(4) 在 register javadoc 声明"同名覆盖将立即销毁旧实例，调用方须保证无借用方"。
- **失效面枚举**: ① `register` 覆盖销毁（本条）；② `unregister` 零调用累积（本条）；③ C4 轮已立案的"g_executors 借出可毁"（getExecutor 借出后被任意 destroy）——同根因不同入口，此处仅登记不展开；④ `globalTimer()/cachedThreadPool()/globalWorker()` 三个 computeIfAbsent 工厂的返回值被强制转型 `(IScheduledExecutor)`（L39）——若 register 以同名放入非定时实现将 CCE，属借出族边界，不另行立案；⑤ `GlobalCacheRegistry.register/unregister`（同名注册即抛、unregister 平台零调用方，仅 ReflectionManager 与 ResourceComponentManager 两个常驻单例注册）——单例常驻属设计边界，若未来出现模块级动态注册将复刻本缺陷，登记为观察项。
- **信心水平**: 确定（代码路径完整可推演；触发循环在 benchmark/测试/embedder 中实际使用）
- **误报排除**: 与已立案的"g_executors 借出可毁"区分：该条立案的是 destroy 与借出的竞争窗口，本条立案的是 **register 覆盖分支主动销毁旧实例的资源释放纪律缺失**与 **unregister 侧释放责任真空**——任务下发明确要求本轮审"register 覆盖旧实例时的资源释放、unregister 零调用方的累积面"。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **降级 P1→P2**。结构性事实全部亲核成立：register 覆盖即 `old.destroy()`（GlobalExecutors L102-107）、unregister remove 不 destroy（L109-111）、allExecutors() 返回活引用（L34-36）、Jdk21CommonsCoreInitializer 无 destroy 覆写、`CFG_ENABLE_VIRTUAL_THREAD_POOL` 默认 true（CommonConfigs L67）。触发链可达性亲核：`CoreInitialization.destroy()` 将 initializers 置 null（CoreInitialization L197）→ 再 initialize() 经 ServiceLoader 重载**全新实例** → registerGlobalWorker 再次 register → v1.destroy()。可达路径实证：(1) NopJunitExtension afterAll/beforeAll 每测试类一轮 destroy→initialize（测试面高频真实）；(2) AbstractPlugin doDeactivate(autoInit)→destroy、re-activate→initialize（生产可达、条件化）；(3) nop-lint CheckMojo Maven 多模块同一 JVM 逐 mojo destroy→initializeTo 循环（Jdk21CommonsCoreInitializer order=NORMAL_PRIORITY ≤ REGISTER_COMPONENT，生产工具链可达）。**P1 终裁疑点裁定：降 P2**——P1 的挂死后果随 C6-001 主张一并失效：被覆盖销毁的是 per-task 虚拟线程执行器（无排队面，in-flight 任务正常完成、destroy 后新提交 REE fail-fast）；round 2「借出方均按名即时解析」亲核成立（ModelBasedBatchTaskBuilderFactory L209-216、AbstractBizEntityService L51-58、TaskFlowManagerImpl L84-88，均无字段缓存）。残留后果=窄窗口 REE（可见异常）+ 僵尸窗口（destroy 后 re-init 前旧池可继续接活，语义缺陷，归 C6-003 F3）+ unregister/allExecutors 死代码面；无不可逆生产故障链，且具体触发需 destroy→initialize 生命周期循环（生产主路径 NopApplication 为 init 一次/destroy 一次，无循环）。维持全部修复建议（与 C1-015/C4-005/CL-018 同点统筹）。现状：已知未修复。

---

### [nop-kernel-concurrency-C6-003] 全局执行器无平台级 destroy 生命周期：宿主关闭时 globalWorker/globalTimer/cachedThreadPool 在飞与排队任务无 drain、无优雅关闭

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:32-111`；宿主侧 `nop-core-framework/nop-boot/src/main/java/io/nop/boot/NopApplication.java:39-41`、`nop-kernel/nop-commons/src/main/java/io/nop/commons/service/ShutdownHook.java:48-78`、`nop-kernel/nop-core/src/main/java/io/nop/core/initialize/CoreInitialization.java:158-206`
- **证据片段**:
  ```java
  // GlobalExecutors：只有惰性 init，没有类级 destroy/init 钩子
  public static IThreadPoolExecutor globalWorker() {
      return g_executors.computeIfAbsent(NOP_GLOBAL_WORKER, key -> {
          DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
          ...
          executor.init();
          return executor;
      });
  }
  // NopApplication 关闭钩子只销毁 CoreInitialization（清缓存），不触池
  ShutdownHook.getInstance().addDisposable(() -> { CoreInitialization.destroy(); });
  ```
- **严重程度**: P2
- **并发场景**: 全平台借用 `GlobalExecutors.globalWorker()`（IoC 容器启动图 `BeanContainerImpl:542`、`DefaultTaskExecutionQueue` 默认池）、`globalTimer()`（配置热刷新 `JdbcConfigSource:54`/`AbstractFileConfigSource:45`、`ZipFileWatcher:38`、`LocalResourceLockManager:104`、cli watch debounce）与 `cachedThreadPool()` 的任务，与 JVM shutdown hook 线程并发。
- **触发时序**: 应用收到停机信号 → JVM 运行 ShutdownHook → `CoreInitialization.destroy()` 清空模型缓存与租户资源，但 **`g_executors` 中三个全局池不在任何销毁清单中**（grep 证实无任何生产代码对 globalWorker/globalTimer/cachedThreadPool 调用 destroy）→ 在飞任务被进程退出硬切断（daemon 线程不阻塞退出，但任务无完成机会）：`globalTimer` 上挂着的配置热刷新任务、锁租约超时检查、debounce 任务直接消失；`globalWorker` 上 IoC 启动图与队列任务的 wrapper future 永不终态化（C6-001 同根因）。destroy 后同 JVM re-init 时旧池原样复用（无重置点），跨生命周期的任务可能落到上一生命周期的池上。
- **现状**: `GlobalExecutors` 无 `destroy()`/`shutdown()` 静态方法，未注册进 `ShutdownHook.disposables`，`CoreInitialization` 亦不触及；三个池均为 daemon 线程（`setThreadDaemon(true)`），故 JVM 能退出——表现为"静默丢任务"而非"挂死不退"。
- **风险**: 停机窗口内（a）`LocalResourceLockManager.checkTimeout` 停止执行（若锁管理器晚于池销毁则租约泄漏，当前锁管理器自身 PreDestroy cancel 配对正确，此风险仅在非常规关闭顺序出现）；（b）在飞异步任务结果丢失且无日志；（c）embedder 反复 init/destroy 无法重置全局池状态。判 P2 而非 P1：daemon 线程使后果限于"停机期任务丢失"，非常态运行泄漏。
- **建议**: 为 `GlobalExecutors` 增加 `static void destroyAll()`（遍历 `g_executors` 逆序 destroy，用 `ThreadHelper.shutdownAndAwaitTermination` 并按 C6-001 建议终态化队列任务），注册进 `ShutdownHook`（在 `CoreInitialization.destroy` 之前执行，顺序经 `SafeOrderedComparator` 控制）。
- **失效面枚举**: ① `NOP_GLOBAL_TIMER`（本条，定时任务消失面）；② `NOP_GLOBAL_WORKER`（本条，容器启动图/任务队列）；③ `NOP_CACHED_THREAD_POOL`（本条，`TaskExecutionGraph` 等使用）；④ `NOP_SYNC_EXECUTOR`（`SyncThreadPoolExecutor.INSTANCE`，无资源，不适用）；⑤ `NOP_VIRTUAL_THREAD`（destroy 有实现但无人调，与①–③同状态）；⑥ 各模块自建池（DefaultTaskExecutionQueue ownExecutor 等）——有 LifeCycleSupport/PreDestroy 配对，不属本条。
- **信心水平**: 确定
- **误报排除**: 不是"低频路径粗粒度锁"类误报，也不是已立案的"池 destroy 语义族"（该族针对单池 destroy 实现，本条是**全局注册表缺少销毁编排**这一编排层缺口）；平台设施被宿主管理的判级口径已按任务要求代入。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P2**。核心事实亲核成立：`g_executors` 三个惰性池无任何平台级 destroy 编排——全仓 grep `GlobalExecutors.*destroy` 零命中；NopApplication 仅注册 `CoreInitialization.destroy()` 进 ShutdownHook（L39-41），CoreInitialization.destroy 不触池（L158-206 亲读）；三池 daemon=true、停机表现为静默丢任务而非挂死。round 2 补充的僵尸窗口（destroy 后 re-init 前旧虚拟线程池可继续接活）机制亲核成立（Jdk21CommonsCoreInitializer 无 destroy 覆写）。修复建议（destroyAll + ShutdownHook 有序注册 + 终态化补偿）维持，且应与 C6-001/C6-002/C6-009 同 diff 统筹。现状：已知未修复。

---

### [nop-kernel-concurrency-C6-004] 指标绑定无注销对称：池/缓存的 meter 绑定即忘，生命周期替换后旧 meter 永久滞留、新实例静默失去指标

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:201-208`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCache.java:50-51, 74-78`
- **证据片段**:
  ```java
  public static void registerMetrics(ExecutorService executor, ThreadPoolConfig config) {
      if (config.isEnableMetrics() && !StringHelper.isEmpty(config.getName())) {
          MeterRegistry registry = GlobalMeterRegistry.instance();
          if (registry != null) {
              new ExecutorServiceMetrics(executor, config.getName(), Collections.emptyList()).bindTo(registry);
          }   // 无任何对应 remove/unbind；IDestroyable.destroy() 不触指标
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 池/缓存销毁重建线程（`CoreInitialization` destroy→initialize 循环、`GlobalExecutors.register` 覆盖、IoC bean 重启）与 Prometheus 抓取线程并发读 meter。
- **触发时序**: 池 P1（name="X"）init → `registerMetrics` 绑定 meter 集（executor.completed/queued/active/...，Meter Id 含 tags name="X"）→ P1 destroy → **meter 无任何移除路径**，滞留于 GlobalMeterRegistry（Prometheus registry 强持有 meter 对象本身）→ 重建同名 P2 → `bindTo` 以相同 Meter Id 注册 → micrometer `getOrCreateMeter` 命中既有 meter 直接返回 → **P2 的全部指标静默落在 P1 的旧 meter 上**（弱引用目标已清空后恒为 NaN/0）。每次生命周期替换循环不新增 meter（同 Id 去重），累积面 = 生命周期中出现过的不重名池/缓存数。
- **现状**: 绑定点 2 处（`newScheduledExecutor:69`、`newThreadPoolExecutor:91`、`LocalCache.registerMetrics:76`），全仓 grep 无 `registry.remove`/unbind 调用；`LocalCache.destroy()`、`IThreadPoolExecutor.destroy()` 均不清理指标。已核验 micrometer 1.15.x：`DefaultGauge`、`CacheMeterBinder`（`WeakReference<C> cacheRef`）、`CumulativeFunctionCounter` 均持弱引用——**排除"死亡执行器被注册表强持有"的内存滞留链**，滞留物是 meter 对象本身与失真指标。
- **风险**: (a) 生命周期替换后指标永久失真且无告警（观测面静默失效，运维按旧池数据误判）；(b) meter 对象按不重名实例数单调累积（含 Timer 内部聚合结构），长期运行的多实例替换场景缓慢增长；(c) 与 F8"注册/注销对称"直接冲突。
- **建议**: `ExecutorHelper` 提供 `unregisterMetrics(name)`（按 Meter Id 前缀 `executor.*` + tag name 过滤 `registry.remove`），在 `DefaultThreadPoolExecutor/DefaultScheduledExecutor.destroy()` 与 `LocalCache.destroy()` 中调用；或统一封装 `MetricBinderHandle implements AutoCloseable` 由设施持有、destroy 时关闭。
- **失效面枚举**: ① `ExecutorHelper.registerMetrics` × `newThreadPoolExecutor`（本条）；② 同 × `newScheduledExecutor`（本条同修）；③ `LocalCache.registerMetrics`（CaffeineCacheMetrics，同样无 unbind；租户缓存 `TenantAwareResourceLoadingCache:31-33` 每租户创建 `ResourceLoadingCache` 且 `useMetrics()`，meter 名 `"tenant-"+name` 不含租户 ID——所有租户共用同一 Meter Id，第 2+ 租户指标永远缺失，属同根因变体）；④ `ResourceComponentManager` 模型缓存 `"model-cache-{modelType}"`——名空间有界，低风险；⑤ LocalCacheProvider 按名建缓存（`caches.computeIfAbsent`，名由代码定义，有界）。
- **信心水平**: 确定（绑定/无注销可直接验证；micrometer 弱引用语义经 javap 字节码核验）
- **误报排除**: 已主动排除"死亡执行器被全局注册表强持有导致内存泄漏"这一更强指控（经字节码核验 1.15.x 弱引用不成立），按实际后果降为 P2，不虚构内存滞留。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P2，一处措辞限定**。绑定/无注销亲核成立：`ExecutorHelper.registerMetrics`（L201-208）bindTo 后无任何 remove 路径、`LocalCache.registerMetrics`（L81-87）同样无 unbind、`LocalCache.destroy()`=cleanUp() 不触指标；`TenantAwareResourceLoadingCache` loader 以 `"tenant-" + name` 命名（L38-41 亲核）——所有租户共用同一 Meter Id、第 2+ 租户指标缺失的变体成立。**措辞修正**：「全仓 grep 无 registry.remove」需限定为「审计单元设施面（ExecutorServiceMetrics/CaffeineCacheMetrics）无 remove」——nop-stream 自有模块 meter（EngineMetrics/TaskNodeMetrics）存在 registry.remove 配对，属模块内生命周期管理，不影响本条结论。micrometer 1.15.x 弱引用排除内存滞留的核验方向正确（DefaultGauge 弱引用为 micrometer 标准行为），维持「非内存泄漏、观测失真 + meter 对象缓增」的 P2 定级。现状：已知未修复。

---

### [nop-kernel-concurrency-C6-005] 模型缓存销毁链断裂（destroyOnRemove 断链的泄漏后果面）：平台模型缓存驱逐/关闭路径上模型对象的 destroy 回调结构性不可达

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceLoadingCache.java:106-113`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCache.java:58-60, 110-114`；`nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/ResourceComponentManager.java:418-428`
- **证据片段**:
  ```java
  // ResourceLoadingCache.createCache —— 平台模型缓存的统一构造点：未设 destroyOnRemove
  LocalCache<String, ResourceCacheEntry<V>> createCache() {
      CacheConfig config = new CacheConfig().useMetrics();   // destroyOnRemove=false
      ...
      return LocalCache.newCache(getName(), config, this::newCacheEntry);
  }

  // LocalCache —— 驱逐回调受 destroyOnRemove 门控；destroy() 只做 cleanUp 不失效
  builder.removalListener((key, value, cause) -> {
      if (config.isDestroyOnRemove()) DestroyHelper.safeDestroy(value);
  });
  public void destroy() { cache.cleanUp(); }   // 不 invalidateAll：常驻条目销毁回调不触发

  // ResourceComponentManager.ComponentCacheEntry —— 包裹层不可销毁
  static class ComponentCacheEntry implements IObjectChangeDetectable {   // 非 IDestroyable
      Object model;
      Map<String, Object> transformed = new ConcurrentHashMap<>();
  }
  ```
- **严重程度**: P2
- **并发场景**: 模型缓存达到 `CFG_COMPONENT_RESOURCE_CACHE_PER_TYPE_SIZE` 上限或 TTL 到期时 Caffeine 驱逐线程/读取线程执行 removalListener；`CoreInitialization.destroy()` → `GlobalCacheRegistry.clearAllCache()` → `ResourceLoadingCache.clear()` 路径。
- **触发时序**: 窗口 1（驱逐路径）：模型 M1 以 ResourceCacheEntry 形式驻留 "model-cache-x" → 缓存超限，M1 条目被 LRU 驱逐 → removalListener 因 `destroyOnRemove=false` 直接返回 → `ResourceCacheEntry.clear()` 不执行 → `listener.onDestroyed(M1)`（本处 listener 为 null）与 `DestroyHelper.safeDestroy(M1)` 均不发生 → M1 等待 GC；M1 若持有需显式释放的资源（注册表反注册、文件句柄、字体/工作簿缓存等）则**泄漏至进程退出**（Caffeine 无 finalizer 兜底）。窗口 2（关闭路径）：宿主 destroy → `LocalCache.destroy()`=`cleanUp()`，常驻条目不失效、回调不触发，与窗口 1 同后果。窗口 3（显式 clear 路径，半断）：`clear()` → invalidateAll → safeDestroy(ResourceCacheEntry)（IDestroyable，能级联到 `ResourceCacheEntry.clear()`）→ 但 `clear()` 内 `safeDestroy(oldObj)` 的 oldObj 是 ComponentCacheEntry——**非 IDestroyable/AutoCloseable，safeDestroy 为 no-op** → 基础模型与 `transformed` 派生模型依然不可销毁。
- **现状**: destroyOnRemove 链路只在三处租户相关缓存显式开启（`TenantAwareOrmModelProvider`、`TenantAwareDependencyManager`、`TenantAwareResourceCacheEntry`）；平台中央模型缓存（ResourceComponentManager 全部 modelType）、`ResourceDependsManager.cache`、反射模型缓存均未开启；`IResourceLoadingCache` 接口无 destroy 语义，`TenantAwareResourceLoadingCache`/`ResourceLoadingCache` 均未实现 `IDestroyable`，`clearForTenant` 只 remove 容器条目同样不触发销毁。
- **风险**: 当前驻留模型为冻结内存对象时后果限于 GC 前多驻留 + `ICreationListener.onDestroyed` 契约失效（依赖该回调做反注册的扩展方将累积）；一旦任一模型加载器返回持 OS 资源的对象（报表模板、脚本引擎句柄），驱逐路径即成为句柄泄漏窗口。判 P2：现网模型语义以内存为主，泄漏不可逆性未证实到 P1。
- **建议**: (1) `ResourceLoadingCache.createCache` 对 `ResourceCacheEntry` 值统一 `.destroyOnRemove()`（值已实现 IDestroyable，链路现成）；(2) `ComponentCacheEntry` 实现 `IDestroyable`（destroy = 对 model 与 transformed 值逐一 `DestroyHelper.safeDestroy`）；(3) `LocalCache.destroy()` 改为 `cache.invalidateAll(); cache.cleanUp();`；(4) `IResourceLoadingCache` 增加 destroy 语义或让 `TenantAwareResourceLoadingCache`/`ResourceLoadingCache` 实现 `IDestroyable` 并被 `GlobalCacheRegistry` 关闭路径调用。
- **失效面枚举**: ① `ResourceComponentManager.makeModelCache` 全部 modelType 缓存（本条主面）；② `ResourceDependsManager.cache`（`"depends-with-cache"`，值非 destroyable，同构）；③ `ReflectionManager.introspectCache`（值为 ClassModel，内存型，开启 destroyOnRemove 亦无实义，登记不修）；④ 三个租户缓存（已开启 destroyOnRemove，链路通，正面样本）；⑤ C5 轮已立案的 CacheConfig 拷贝丢 `destroyOnRemove` 字段——经 grep `cloneInstance()` 在主代码零调用，该断链当前无触发点，本条不依赖它独立成立。
- **信心水平**: 确定（链路代码完整可推演；危害量级依赖"模型持 OS 资源"这一未证实前提，故 P2）
- **误报排除**: 非已立案"destroyOnRemove 断链（CacheConfig 拷贝丢字段）"的重复——本条是该断链被要求的**泄漏后果面**审计产出，指认的是"平台默认模型缓存根本未启用该链 + 包裹层不可销毁 + destroy() 不失效"三个独立断点；模型为纯内存对象时不夸大为 P1。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P2**。三断点逐一亲核成立：① `ResourceLoadingCache.createCache`（L106-113）仅 `.useMetrics()` 无 destroyOnRemove；② `ComponentCacheEntry`（ResourceComponentManager L418-428）仅 implements IObjectChangeDetectable，`ResourceCacheEntry.clear()`（L111-124）级联的 `DestroyHelper.safeDestroy(oldObj)` 对其 no-op，且 makeModelCache 传 listener=null；③ `LocalCache.destroy()`（L68-70）仅 cleanUp 不 invalidateAll，removalListener 受 destroyOnRemove 门控（L110-114）、clear()=invalidateAll（L244-247）后回调同样 no-op——窗口 3「半断」的实际机制比初审描述更彻底（默认链上完全无销毁回调），结论方向不变。危害前提「模型持 OS 资源」维持未证实声明，round 2 已补证 xlang 主路径无动态类加载（C6-005 危害面收缩），P2 维持。修复建议(1)(2)(3)(4) 与 C6-012 建议(1)(2) 合并实施。现状：已知未修复。

---

### [nop-kernel-concurrency-C6-006] 文件监听注册表生命周期不对称：unwatch 不摘除父 subEntries（单调累积），同路径重复 watch 共享 WatchKey 导致注销交叉误伤

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/watch/FileWatchEntry.java:28, 74-80`；`nop-kernel/nop-core/src/main/java/io/nop/core/resource/watch/NioFileWatchService.java:139-150（register）, 213-221（unwatch）`；`FileWatcher.java:57-87`
- **证据片段**:
  ```java
  // NioFileWatchService.unwatch —— 只摘全局表 + cancel key，父 entry.subEntries 中残留
  private void unwatch(FileWatchEntry entry) {
      this.watchKeyMap.remove(entry.getWatchKey());
      entry.getWatchKey().cancel();
      for (FileWatchEntry subEntry : entry.getSubEntries().values()) {
          unwatch(subEntry);        // 递归取消子孙，但从不从父的 subEntries 中摘除自己
      }
  }
  // FileWatchEntry —— subEntries 只有 put/get，无 remove-by-parent 入口
  private Map<Path, FileWatchEntry> subEntries = new ConcurrentHashMap<>();
  ```
- **严重程度**: P2
- **并发场景**: watcher 线程（checkChange）处理 ENTRY_DELETE 时调用 `unwatch(subEntry)`；应用线程调用 `watch()` 注册、持有 cleanup Runnable 后调用；两者并发读写 `watchKeyMap`（CHM，安全）与 `entry.subEntries`（CHM，安全）——本条非数据竞争，是纯生命周期不对称。
- **触发时序**: 窗口 1（单调累积）：递归 watch 根目录 R → 应用在 R 下创建子目录 D1（ENTRY_CREATE → `R.addSubEntry(D1)`）→ 删除 D1（ENTRY_DELETE → `unwatch(D1)`：全局表摘除 + key cancel，**D1 仍留在 `R.subEntries` 中**，持有 WatchKey/Path/filter/listener 引用）→ 周而复始创建/删除**不同名**子目录（构建产物、时间戳目录、临时工作区）→ `R.subEntries` 随历史出现过的目录名单调增长，无任何清理路径。窗口 2（交叉误伤）：对同一路径两次调用 `watch()`（重启监听而未先执行旧 cleanup）→ `Path.register` 对同一 WatchService 返回**同一个 WatchKey**，`register()` L147 `watchKeyMap.put(key, newEntry)` 覆盖值 → 旧 watch 的 cleanup 触发 `unwatch(oldEntry)` → `watchKeyMap.remove(同一 key)` + `key.cancel()` → **新 watch 的注册被一并摘除**，此后该路径事件静默丢失（无日志、无错误）。附带面：`FileWatcher.watch` 的 cleanup 仅做 `watchService` unwatch（L85 `appendOnCancelTask(cleanup)`），不取消已排入 globalTimer 的 pending debounce 任务，unwatch 后最近一次 debounce 仍会触发一次空 `processChangeEvent`（闭包短暂持 handler，瞬态，不立案）。
- **现状**: `FileWatchEntry` 没有向父摘除的 API；`NioFileWatchService.watch` 对重复注册无防重入守卫；`FileWatcher` 的 ICancellable 只聚合 watchService cleanup。
- **风险**: 长驻 dev-watch/热更新进程随目录churn内存缓慢增长（量级小：每条目数百字节，但无上界、无监控）；交叉误伤面使"重启监听"这一常见运维操作静默失效——文件变化不再触发，且不可检测（对照 F5）。
- **建议**: (1) `unwatch` 增加 `parent.getSubEntries().remove(entry.getPath(), entry)`（`watch()`/`checkChange` 注册处传递 parent 引用）；(2) `NioFileWatchService.watch` 检测同路径已有 entry 时先安全反注册旧 entry 或 fail-fast 拒绝重复 watch；(3) `FileWatcher` cleanup 中一并取消 pending debounce（持 `RateLimitExecutorImpl` 引用或返回可取消句柄）。
- **失效面枚举**: ① `NioFileWatchService.unwatch` 全部 3 个入口（doStop 全清路径不受影响——`watchKeyMap.clear()` 兜底；ENTRY_DELETE 路径与 cleanup 路径均触发窗口 1）；② `FileWatchEntry.addSubEntry` 两个调用方（watch 的 walkFileTree 与 checkChange 的 ENTRY_CREATE），都只增不删；③ `ZipFileWatcher.watchDirAndAutoZip`（调试工具，单 watch，仅窗口 1 暴露）；④ `CliWatchCommand`/`FileWatcherFactory`（单 watch + 容器 PreDestroy stop，配对良好，正面样本）；⑤ 已立案"FileWatcher 无界队列"（`queue` LinkedBlockingQueue 无界，C5 面）与本条 subEntries 累积为同文件不同资源，不重复计数。
- **信心水平**: 确定（窗口 1 与窗口 2 均为确定性代码行为；窗口 2 的 java.nio"同 Watchable 同 service 返回同 key"语义为 JDK 契约）
- **误报排除**: CHM 并发读写已有注释守卫、非本条内容；窗口 2 的双 watch 前提在当前仓库主路径中未发现现网触发点，故整条判 P2 而非 P1，且窗口 1 有明确泄漏窗口时序，不适用"写不出时序降 P2"条款。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P2**。两窗口逐一亲核成立：窗口 1——`unwatch`（NioFileWatchService L213-221）仅摘全局 watchKeyMap + cancel key + 递归子孙，无父引用可摘除自身；`FileWatchEntry`（L28/L74-80）subEntries 只有 put/get 无 remove 入口；ENTRY_DELETE 路径（L122-127）与 watch cleanup 路径均触达，目录 churn 单调累积成立。窗口 2——`register`（L139-150）对同路径重复注册无防重入守卫，`path.register` 同 WatchService 返回同 WatchKey（JDK 契约），`watchKeyMap.put(key, newEntry)` 覆盖后旧 cleanup 触发交叉误伤成立。附带面（debounce 瞬态）不立案维持。doStop 兜底（watchKeyMap.clear）确认。修复建议(1)(2)(3) 维持。现状：已知未修复。

---

### [nop-kernel-concurrency-C6-007] 非 daemon 线程创建点族（C4-010 同族其他站点）：watcher/工具池线程阻止 JVM 退出，且多条关闭路径缺 awaitTermination

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/watch/NioFileWatchService.java:58, 195-211`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ThreadPoolConfig.java:29`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/DefaultThreadPoolExecutor.java:29-31`；`nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ImportDbTool.java:232`、`ExportDbTool.java:150`
- **证据片段**:
  ```java
  // NioFileWatchService —— watcher 线程显式非 daemon
  executor = ExecutorHelper.newSingleThreadExecutor("nio-file-watch-service", 1, false);

  // doStop —— cancel(true) 后立即 shutdown，无 awaitTermination
  if (future != null) future.cancel(true);
  ...
  if (executor != null) executor.shutdown();

  // ThreadPoolConfig —— IoC 声明池默认非 daemon
  private boolean threadDaemon = false;

  // DefaultThreadPoolExecutor 三参工厂默认非 daemon
  public static DefaultThreadPoolExecutor newExecutor(String threadName, int poolSize, int queueSize) {
      return newExecutor(threadName, poolSize, queueSize, false);
  }
  ```
- **严重程度**: P2
- **并发场景**: JVM 退出序列（shutdown hook 线程）与仍在执行监听回调/导入任务的池线程并发；宿主对服务 bean 的 `@PreDestroy` 调用线程。
- **触发时序**: 窗口 1（watcher 阻塞退出）：`NioFileWatchService.doStop` → `future.cancel(true)` 发出中断 → watcher 线程此刻正执行某个监听回调（如 `FileWatcher` handler → `processEvents` → 用户脚本/zip 重打包等**不响应中断的阻塞 I/O**）→ cancel 与 shutdown 均不等待 → `doStop` 返回，非 daemon 线程继续存活直至回调返回 → 若回调挂在不可中断阻塞点上，**JVM 永不退出**（所有非 daemon 线程存活即阻止退出），停机挂死不可检测。窗口 2（默认非 daemon 的池 bean）：经 IoC 以默认 `ThreadPoolConfig`（threadDaemon=false）声明的池，若宿主关闭未调用其 destroy（如 bean 声明遗漏销毁钩子），常驻工作线程阻止 JVM 退出。
- **现状**: 已立案的 C4-010 针对的是 `DefaultScheduledExecutor.newSingleThreadTimer`（不设 daemon 即 false）；本条覆盖**其余站点**：`NioFileWatchService`（显式传 false）、`ThreadPoolConfig` 默认值、`DefaultThreadPoolExecutor.newExecutor` 三参重载、以及直接消费这两个默认的 `ImportDbTool`/`ExportDbTool`（`newExecutor("import-db"/"export-db", threadCount, Integer.MAX_VALUE)`，daemon=false）。正面样本：`AbstractBatchProcessService.start` 显式传 `daemon=true`；全局池显式 `setThreadDaemon(true)`。
- **风险**: CLI/嵌入场景停机挂死需 kill -9；对长驻服务，destroy 链完整时风险被掩盖，链上任何一环缺失（bean 未注册 PreDestroy、destroy 抛异常被 `LifeCycleSupport.stop` 吞掉——`LifeCycleSupport.java:102-105` catch 后 ignore）即退化为窗口 2。
- **建议**: (1) `NioFileWatchService.doStop` 在 `executor.shutdown()` 后追加 `ThreadHelper.shutdownAndAwaitTermination`（或 `awaitTermination(1, SECONDS)`）；(2) 为"内部设施池"统一默认 daemon=true，将 daemon=false 保留给显式声明的业务池（`ThreadPoolConfig` 默认值反转属行为变更，需 plan-first，此处为建议方向）；(3) `LifeCycleSupport.stop` 吞异常处至少累计告警，避免销毁链断裂不可见。
- **失效面枚举**: ① `NioFileWatchService` watcher 线程（本条主面）；② `ThreadPoolConfig.threadDaemon=false` 默认值 × 所有未显式设置的 IoC 池声明（本条）；③ `DefaultThreadPoolExecutor.newExecutor` 三参重载全部调用方：`DefaultTaskExecutionQueue:145`（daemon 取自传入 config）、`AbstractBizEntityService` 经 GlobalExecutors（不适用）、`ImportDbTool:232`/`ExportDbTool:150`（显式非 daemon，本条）；④ `DefaultScheduledExecutor.newSingleThreadTimer`——C4-010 已立案，仅列不展开；⑤ `newSingleThreadExecutor` 其余调用方：`NioFileWatchService`（本条）；⑥ nop-stream 内多处 `Executors.newSingleThreadExecutor`/`newSingleThreadTimer`（StreamControlRpcProxyFactory:62 等）——超出本审计单元，登记为跨模块同族提示。
- **信心水平**: 很可能（窗口 2 为确定语义；窗口 1 需要回调恰在不可中断阻塞点，未构造实测）
- **误报排除**: 非 C4-010 重复（仅审其余站点）；非"测试代码简化并发"豁免情形——全部为 src/main 生产代码；`LifeCycleSupport` 重启语义（stop 后回 CREATED）已核对，不构成泄漏。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P2**。全部站点亲核成立：NioFileWatchService 显式非 daemon（L58 `newSingleThreadExecutor("nio-file-watch-service", 1, false)`）+ doStop cancel(true) 后裸 shutdown 无 awaitTermination（L195-211）；ThreadPoolConfig.threadDaemon 默认 false（L29）；DefaultThreadPoolExecutor 三参重载默认 false（L29-31）；ImportDbTool（L231）/ExportDbTool（L150）消费三参重载即非 daemon；LifeCycleSupport.stop catch 后 ignore（L98-105 亲读）。窗口 1 需「回调恰在不可中断阻塞点」前提、初审已自标信心「很可能」，维持 P2 与信心口径。与 CL-031（LeidenDetector，nop-graph 跨模块同族）互相印证。现状：已知未修复。

---

### [nop-kernel-concurrency-C6-008] pool 包为死设施：IPool 全平台无实现无调用方，PoolAcquireOptions 为空壳，借还配对契约整体不可验证

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/pool/IPool.java:13`、`PoolAcquireOptions.java:1-12`、`PoolConfig.java`、`IPooledObject.java`（包内 6 files）
- **证据片段**:
  ```java
  public interface IPool<O extends IPooledObject> extends AutoCloseable {
      O acquire(PoolAcquireOptions options);
      void release(O object, boolean shouldDestroy);   // shouldDestroy 契约无实现承载
      ...
  }

  public class PoolAcquireOptions {
      // private boolean allowWait;      —— 唯一字段被注释，空类
  }
  ```
- **严重程度**: P3
- **并发场景**: 不适用（无运行时行为）；影响的是"平台提供池设施"这一契约声明与 `concurrency-resource-audit-prompt` 共享前缀中向审计/开发 AI 推荐的设施清单的真实性。
- **触发时序**: 泄漏窗口：无（纯结构性发现）。核验过程：`grep -r "io.nop.commons.pool"` 在全仓 src/main（除包自身）**零命中**；`implements IPool` 全仓零实现；`PoolAcquireOptions` 唯一字段被注释。`PoolConfig` 中 idleTimeout/removeAbandonedTimeout/autoInitMinIdle 等驱逐与泄漏回收参数没有任何消费者。
- **现状**: 与方法论文件向 AI 推荐的"平台自带 IPool/PoolConfig/PoolAcquireOptions"清单不符——推荐使用一个不存在的实现会诱导使用方自造轮子（违背平台设施复用原则）或误以为存在泄漏回收能力（removeAbandoned）。
- **风险**: 使用方按文档/共享前缀假设存在带 abandoned 回收的池设施，实际自行实现时缺失回收路径 → 借出泄漏风险转嫁各业务模块；或对 `IPool` 编程后无法实例化。
- **建议**: 三选一并落档：(a) 删除 pool 包（推荐，最小面）；(b) 补齐实现（SimplePool + 对 PoolConfig 驱逐参数接线 + 借还配对测试）；(c) 在 `docs-for-ai` 明示"pool 包为预留契约、无实现，请勿引用"。无论哪种，同步修正审计 prompt 共享前缀中的设施清单。
- **失效面枚举**: ① `IPool`（本条）；② `IPooledObject.onBorrowFromPool/onReturnToPool/checkValid/destroy` 回调契约（无实现方消费）；③ `PoolConfig` 全部驱逐参数（同）；④ `PoolAcquireOptions`（空壳）；⑤ `IPoolMetrics`/`PoolStats`（仅数据壳，无生产者）；旁证：`EhcacheCache.close()` 空实现但已在 javadoc 声明"生命周期归 CacheManager 持有方"且明示零生产调用方——**已声明边界，不立案**。
- **信心水平**: 确定（全仓 grep 可复核）
- **误报排除**: 非运行时缺陷类误报校准项；"有界声明存在的池"豁免不适用（根本没有实现与声明）；不计入资源泄漏计数，单独立项为契约真实性缺陷。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P3**。亲 grep 复核：`io.nop.commons.pool` 全仓 src/main 仅包内 6 文件（IPool/IPooledObject/PoolConfig/PoolAcquireOptions/IPoolMetrics/PoolStats）自引用，零外部消费者、零 implements IPool 实现。死设施契约缺陷成立，P3（无运行时行为）维持。处置建议三选一维持，同步修正审计 prompt 设施清单。现状：已知未修复。

---

## 维度集外线索（C8 形态，不立案展开）

### [nop-kernel-concurrency-CL-011]（维度集外线索，不立案展开）ImportDbTool/ExportDbTool 线程池队列容量 Integer.MAX_VALUE——无界队列使导入/导出无背压

- **文件**: `nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ImportDbTool.java:232`、`ExportDbTool.java:150`
- **形态**: `DefaultThreadPoolExecutor.newExecutor("import-db"/"export-db", config.getThreadCount(), Integer.MAX_VALUE)` → `TaskQueue(Integer.MAX_VALUE)`：任务提交速率超过 threadCount 消费速率时队列无界增长（每条目为完整导入任务闭包，含数据行引用），过载时 OOM 而非降级；满策略/拒绝路径完全不可达。属 C8（背压流控）形态；其 daemon=false 一面已并入 C6-007 窗口 2。建议后续 C8 轮次按工具声明的批量规模核对并给出有界队列 + `QueueOverflowPolicy` 配置。

---

## 汇总

### 发现清单

| ID | 标题 | 级别 | 信心 |
|---|---|---|---|
| nop-kernel-concurrency-C6-001 | executor.destroy()/shutdown() 后排队任务 wrapper future 永不完成，等待方永久挂起 | P1 | 确定 |
| nop-kernel-concurrency-C6-002 | GlobalExecutors.register 覆盖即销毁旧池 / unregister 零调用无释放 / allExecutors 暴露可变注册表 | P1 | 确定 |
| nop-kernel-concurrency-C6-003 | 全局执行器无平台级 destroy 生命周期，停机不 drain 在飞/排队任务 | P2 | 确定 |
| nop-kernel-concurrency-C6-004 | 指标绑定无注销对称：meter 滞留 + 生命周期替换后新实例静默失观测 | P2 | 确定 |
| nop-kernel-concurrency-C6-005 | 模型缓存销毁链断裂：驱逐/关闭路径模型 destroy 回调结构性不可达 | P2 | 确定 |
| nop-kernel-concurrency-C6-006 | 文件监关注册表不对称：父 subEntries 单调累积 + 同路径重 watch 注销交叉误伤 | P2 | 确定 |
| nop-kernel-concurrency-C6-007 | 非 daemon 线程创建点族（watcher/默认配置/工具池）+ 关闭路径缺 awaitTermination | P2 | 很可能 |
| nop-kernel-concurrency-C6-008 | pool 包死设施：IPool 无实现无调用方，借还契约不可验证 | P3 | 确定 |

### CL 线索

| ID | 形态 | 级别 |
|---|---|---|
| nop-kernel-concurrency-CL-011 | ImportDbTool/ExportDbTool 无界任务队列（C8 背压形态） | 集外线索 |

### 总计

- **立案发现**：8 条（P0×0，P1×2，P2×5，P3×1）
- **维度集外线索**：1 条（CL-011）
- **正面样本（配对良好，供复核对照）**：LocalResourceLockManager 定时任务 cancel 配对；NioFileWatchService doStop 的 cancel(true)+close 配对；ResourceDependsManager 的 NamedThreadLocal 空栈即 remove；ExecutorHelper.submit 的 finally detachContext；VirtualFileSystem.registerInstance 的 destroy-old 配对；AbstractBatchProcessService 显式 daemon=true。
- **与已立案族的边界**：本报告未重复立案 C1–C5 已列条目；C6-002（register 覆盖面）与已立案"g_executors 借出可毁"、C6-005（泄漏后果面）与已立案"destroyOnRemove 断链/CacheConfig 拷贝丢字段"、C6-007 与已立案 C4-010（newSingleThreadTimer 非 daemon）的边界均在各条"误报排除/失效面枚举"中显式声明。

---

## 维度复核结论

> 复核人：C6 维度独立复核子代理（与初审/第 2 轮子代理相互独立）。复核日期：2026-10-17。复核对象：本文件 C6-001~008 + CL-011 + 17 行配对表，及 `C6-round2.md` C6-009~013 + CL-030/031 + S1~S12 站点表。复核方式：逐条亲开 live code 核对证据（行号漂移以实际代码修正）、关键语义经实测程序验证、历史对照面（cra-index WI 1.1–1.7、plan 346/2310、历史审计）按本阶段解除盲审的授权查阅。逐条裁定明细已回填各条目 `**复核状态**（维度复核 2026-10-17）` 行（两文件，只追加）。

### 一、总体裁定统计

| 裁定 | 条目 | 数量 |
|---|---|---|
| 维持原级 | C6-003、C6-004、C6-005、C6-006、C6-007、C6-008、C6-009、C6-010、C6-011、C6-012、C6-013 | 11 |
| 降级 | C6-001（P1→P2 条件化，主根因主张驳回、残余面保留）、C6-002（P1→P2） | 2 |
| 驳回（整条） | 无 | 0 |

- **复核后级别分布**：P0×0，**P1×0**，P2×11，P3×2（原初审+第 2 轮：P1×2/P2×9/P3×2）。
- **现状标注**：13 条全部「已知未修复」（git log 核实：审计 HEAD `8c13908bfd` 之后无任何设施文件提交；本审计目录与 plan 2468 尚未提交）。设施本体口径，无「已修复」项。
- **CL 处置**：CL-011 确认但**与 C4-003 同位点**（C4 维度已立案 P2、复核保留、已知未修复，F3 明列 ImportDbTool/ExportDbTool）——不重复立案，转同位点注记，交 Remediation 与 C4-003 同 diff 处置；CL-030 确认（registerInstance 非原子 + LocalCache.registerMetrics 无 null 判均亲核成立；唯一调用方 QuarkusIntegration:50 启动期单线程亲核），维持集外线索；CL-031 确认（LeidenDetector L192-204 非 daemon + shutdownNow 无 awaitTermination 亲核成立），维持跨模块同族提示交 nop-graph 轮次；CL-029 前轮已并入 C6-011 关闭，维持。

### 二、本轮核心终裁

#### 1. C6-001 终裁：降 P1→P2（条件化），主根因主张驳回

- **a) 完成点在任务体内**：核实成立（`ExecutorHelper.submit` L128-150/L152-174、`schedule` L176-199——wrapper `CompletableFuture` 的 complete/completeExceptionally 全部在提交给池的 Callable 任务体内）。
- **b) JDK 语义前提错误（本轮关键修正）**：初审主张「`shutdown()` 使排队任务永不执行/被静默丢弃」，这是 `shutdownNow()` 的语义。JDK `ThreadPoolExecutor.shutdown()` 契约为「previously submitted tasks are executed」——排队任务会被工人线程继续排空执行，`tryTerminate()` 在队列非空时不终止。经实测程序双重验证（`_tmp/c6-review/ShutdownSemanticsTest.java`：忙池+排队 wrapper 任务+`shutdown()`，排队任务的 wrapper future 1s 内正常完成；`ShutdownNowTest.java`：`shutdownNow()` 丢弃 delayed 任务的 wrapper future 永不完成）。另核 `TaskQueue.force()`（L55-66）在 `parent.isShutdown()` 时抛 REE——destroy 后的新提交 fail-fast，不存在「destroy 后提交被 force 入队永不执行」的隐藏路径。**故 S1（DefaultThreadPoolExecutor 主根因）不成立**：`destroy()`=shutdown() 后排队任务照常执行、wrapper future 照常终态化，等待方最终获得结果（至多延迟到队列排空）。
- **c) 残余成立面（保留为条件化 P2）**：`DefaultScheduledExecutor.destroy()`=`shutdownNow()` 丢弃排队/延迟任务，其 wrapper future 永不终态化（实测证实；destroy 生产可达：`nopJobScheduledExecutor`、`nopRpcPollScheduledExecutor` 两 bean 均声明 `destroy-method="destroy"`）。F3 补全第⑧项：`BindScheduledExecutor.destroy()`→`timer.destroy()` 同 shutdownNow 变体（属 C4 已立案族，登记不展开）。
- **d) 生产受害链清点（终裁依据）**：S7 零消费者亲核成立（`nopTaskExecutionQueue` bean 全仓 src/main 唯一出现点为 `task-defaults.beans.xml:8` 声明本身，`ITaskExecutionQueue` 无任何消费者）；S4 配对亲核成立（LocalJobScheduler `deactivate()` 先行 cancelScheduledFire/cancelRunning，容器逆序销毁使其先于 executor destroy）；S3 走 globalTimer 永不 destroy；S5 两个调用方均无等待方（IExecution.withRetry 零调用、SocketClient.recv 丢弃返回 promise）；S6 依赖 globalWorker 被 shutdown（现网唯一 register 调用注册的是 nop-virtual-thread，不覆盖 globalWorker）。**唯一残余面的具体生产等待方 = 0**，按判级口径「当前无生产调用方站点维持条件化降档」→ **P2**（触发条件：池 destroy 时内有排队任务且存在等待方）。throttleExecute 信号量许可泄漏同理条件化。
- **e) 连带修正**：配对表 Row 1 的「排队任务的 wrapper future 永不完成（C6-001）」判定撤销，改为「基本配对（shutdown 排空执行良好；缺 awaitTermination、stats() 悬空）」；S 站点表 S1 撤销、S2 维持（等待方为零）、S10 机制修正（单表失败后 shutdown() 下其余排队表任务仍会继续执行而非被丢弃——结果被忽略，且 CLI 非 daemon 线程会等待其跑完）；「平台全部 destroy 无一补 completeExceptionally」的表述维持成立，但其后果仅对 shutdownNow 变体兑现。

#### 2. C6-002 终裁：降 P1→P2

- **结构性事实全部成立**：register 覆盖即 `old.destroy()`（L102-107）、unregister remove 不 destroy（L109-111）、allExecutors() 活引用（L34-36）。
- **触发链可达性裁定**：destroy→initialize 循环真实存在——(1) 测试面高频真实（NopJunitExtension afterAll→destroy / beforeAll→initialize，每测试类一轮，同 fork 内循环）；(2) 生产可达但条件化（AbstractPlugin doDeactivate(autoInit)→destroy、re-activate→initialize 的插件热插拔路径）；(3) 生产工具链（nop-lint CheckMojo Maven 多模块同一 JVM 逐 mojo destroy→initializeTo 循环；Jdk21CommonsCoreInitializer order=NORMAL_PRIORITY ≤ REGISTER_COMPONENT 级别）。生产主路径（NopApplication）为 init 一次/destroy 一次，无循环。机制上 destroy 置 initializers=null → 重载全新 Initializer 实例 → registerGlobalWorker 再次 register → v1.destroy()，链路成立。
- **降级理由**：P1 的「借出引用失效→等待方永久挂起」后果随 C6-001 主张一并失效——被覆盖销毁的是 per-task 虚拟线程执行器（newVirtualThreadPerTaskExecutor 无排队面：in-flight 任务正常完成、destroy 后提交 REE fail-fast 可见）；round 2 已自证并经本轮亲核「借出方均按名即时解析」（ModelBasedBatchTaskBuilderFactory L209-216、AbstractBizEntityService L51-58、TaskFlowManagerImpl L84-88，无字段缓存）。残留后果为窄窗口 REE + 僵尸窗口（归 C6-003 F3）+ unregister/allExecutors 死代码面，无不可逆生产故障链 → **P2**。

#### 3. C6-010 复核：维持 P2（休眠）

机制逐环亲核成立（8 处构造期 Cancellable 名单与 live code 完全一致、initialize 内无一重建；取消态 append 立即执行为 javadoc+代码双证；reinitialize 复用实例）。可达性：`reinitialize` 全仓零调用方亲核复证（与 round 2 一致，plan 346/2310 历史审计亦曾两轮独立复证同一事实）。destroy→initialize 路径因 initializers=null 重载新实例而不受影响的边界判定正确。休眠 + public API 契约矛盾 + 8 模块散布 → P2 维持。

### 三、其他修正与补全（不改判级）

1. **C6-012 F3 ② 证伪修正**：初审称 orm-interceptor-cache / data-auth-cache / dyn-loaded-orm-model-cache 三缓存「未注册进 GlobalCacheRegistry、clearAllCache/clearForTenant 均不可达」——亲核**证伪**：三者分别于 DefaultOrmModelProvider L26、DefaultDataAuthChecker L71、DynOrmModelProvider L35 注册（且均在 close/unregister 配对），`CacheEntryManagement.clearForTenant` 有覆写（L40-41）。该子项由「登记缺口」改判为正面样本；C6-012 主面（tenantCaches 容器双断）不受影响。
2. **C6-004 措辞限定**：「全仓 grep 无 registry.remove」应限定为「审计单元设施面（ExecutorServiceMetrics/CaffeineCacheMetrics）无 remove」——nop-stream EngineMetrics/TaskNodeMetrics 存在模块内 meter remove 配对，不影响本条结论。
3. **C6-005 窗口 3 机制澄清**：默认模型缓存链上 destroyOnRemove=false 时 clear()→invalidateAll→removalListener 整链无销毁回调（比初审「半断」描述更彻底），结论方向不变。
4. **C6-001 F3 补全第⑧项**：`BindScheduledExecutor.destroy()`→`timer.destroy()`→shutdownNow 变体（同 S2 族；设施本体属 C4 已立案族，登记不展开，不改判级）。
5. **S10 机制修正**：ImportDbTool/ExportDbTool 单表失败后 `destroy()`=shutdown() 下其余排队表任务仍会继续执行（非「静默丢弃」），结果被忽略；「不挂死」判定维持。
6. **行号漂移修正**：LocalCache.registerMetrics 实际 L81-87（初审 74-78）；TenantAwareResourceLoadingCache 容器构造实际 L38-41（round2 证据 31-33）；DefaultTaskExecutionQueue doStart/doStop 实际 L137-156（无漂移）。均在复核状态行内以实际行号标注。
7. **F3 完备性复核**：以 `grep "public void destroy()"` 对 executor 包全量清点 + `ExecutorHelper.submit`/`IThreadPoolExecutor.submit` 全仓调用点清点，S1~S12 站点表无遗漏站点；listener/watcher 注册面（round 2 L1~L16）抽样复验无遗漏。未发现需新立案的缺漏。

### 四、争议点（供 Remediation/后续审计参考）

1. **C6-001 修复方向调整**：原建议「shutdownNow + 对 dropped 回调 completeExceptionally」对 shutdown()-型池是不必要的（队列会排空）；修复面应收窄为：(a) `DefaultScheduledExecutor.destroy()`（及 BindScheduledExecutor 传递路径）对被丢弃排队任务的 wrapper future 终态化补偿；(b) 与 C6-009 同 diff 接线 `waitForTasksToCompleteOnShutdown/awaitTerminationSeconds`（shutdown-型池获得有界等待的排空语义）。
2. **ImportDbTool/ExportDbTool 失败语义**（S10 修正的延伸）：单表失败后剩余排队表任务在 shutdown() 下仍会继续执行——「失败即停」的工具语义与实际「后台继续导入」行为不一致，属行为正确性面，超出 C6 立案口径，建议 nop-batch 轮次认领。
3. **C6-002 的插件/工具链触发面**：AbstractPlugin deactivate→destroy 与 lint mojo 循环是 destroy→initialize 的真实生产路径，g_executors 统筹修复（C1-015/C4-005/C6-002/CL-018 同点）时应将这两个入口纳入回归面。
4. **C6-009 与 C6-001 的关系重述**：C6-001 主张修正后，C6-009 的「死配置」从「C6-001 的修复旋钮」升格为「shutdown-型池停机排空的唯一在册手段」，接线价值上升，修复优先级建议随 C6-001 残余面同批。

---

## 最终保留项

| ID | 标题（复核后口径） | 复核后级别 | 信心 | 现状 |
|---|---|---|---|---|
| nop-kernel-concurrency-C6-001 | destroy 终态化缺口（**收窄后口径**：DefaultScheduledExecutor/BindScheduledExecutor 的 shutdownNow 丢弃排队任务，wrapper future 永不终态化；shutdown()-型池排空执行无此问题）——条件化：需 destroy 时有排队任务且有等待方，当前生产等待方为零 | P2 | 确定（残余面）/ 主根因主张驳回 | 已知未修复 |
| nop-kernel-concurrency-C6-002 | GlobalExecutors.register 覆盖即销毁旧池 / unregister 无释放零调用 / allExecutors 活引用——触发链真实（测试高频、插件与 lint 工具链生产可达）但无不可逆生产故障链 | P2 | 确定 | 已知未修复 |
| nop-kernel-concurrency-C6-003 | 全局执行器无平台级 destroy 生命周期 + destroy→re-init 僵尸窗口 | P2 | 确定 | 已知未修复 |
| nop-kernel-concurrency-C6-004 | 指标绑定无注销对称：meter 滞留 + 生命周期替换后观测失真（措辞限定：remove 缺失限于审计单元设施面） | P2 | 确定 | 已知未修复 |
| nop-kernel-concurrency-C6-005 | 平台模型缓存销毁链三断点（destroyOnRemove 未启用 + ComponentCacheEntry 不可销毁 + destroy()=cleanUp） | P2 | 确定 | 已知未修复 |
| nop-kernel-concurrency-C6-006 | 文件监关注册表不对称：父 subEntries 单调累积 + 同路径重 watch 注销交叉误伤 | P2 | 确定 | 已知未修复 |
| nop-kernel-concurrency-C6-007 | 非 daemon 线程创建点族 + 关闭路径缺 awaitTermination + LifeCycleSupport 吞异常 | P2 | 很可能 | 已知未修复 |
| nop-kernel-concurrency-C6-008 | pool 包死设施：IPool 无实现无调用方 | P3 | 确定 | 已知未修复 |
| nop-kernel-concurrency-C6-009 | 停机排空配置（waitForTasksToCompleteOnShutdown/awaitTerminationSeconds）全链路零消费——shutdown-型池排空唯一在册手段未接线 | P2 | 确定 | 已知未修复 |
| nop-kernel-concurrency-C6-010 | ICoreInitializer 复用构造期 Cancellable：reinitialize 路径注册即撤销（×8 模块；休眠：reinitialize 全仓零调用） | P2 | 确定 | 已知未修复 |
| nop-kernel-concurrency-C6-011 | ShutdownHook.destroyAll CAS 守卫反转 + 非线程安全 TreeSet + 无 remove（休眠：零手动调用方） | P2 | 确定 | 已知未修复 |
| nop-kernel-concurrency-C6-012 | 租户模型缓存清理双断（tenantCaches 容器未开 destroyOnRemove + ResourceLoadingCache 不可销毁）；F3 ② 三缓存注册事实已修正为正面样本 | P2 | 确定 | 已知未修复 |
| nop-kernel-concurrency-C6-013 | ResourceDependsManager.close() 遗漏内部 depends-with-cache（含 meter） | P3 | 确定 | 已知未修复 |

### CL 线索处置（复核后）

| ID | 复核裁定 | 备注 |
|---|---|---|
| CL-011 | 确认线索，**与 C4-003 同位点**，不重复立案 | C4-003（P2、复核保留、已知未修复）已明列 ImportDbTool/ExportDbTool Integer.MAX_VALUE 直通；交 Remediation 与 C4-003 同 diff 处置；daemon 面归 C6-007 |
| CL-030 | 确认，维持集外线索 | registerInstance 非原子 + LocalCache.registerMetrics 无 null 判均亲核成立；唯一调用方启动期单线程，无活跃触发面 |
| CL-031 | 确认，维持集外线索 | LeidenDetector 非 daemon 临时池 + shutdownNow 无 awaitTermination 亲核成立；交 nop-graph 轮次 |
| CL-029 | （round 2 已处置）并入 C6-011 | 复核维持 |

### 复核后总计

- **立案发现**：13 条（P0×0，**P1×0**，P2×11，P3×2）；降级 2 条（C6-001/C6-002 P1→P2），整条驳回 0，维持 11。
- **修复优先序（复核后建议）**：① C6-009 接线 + C6-001 残余面（DefaultScheduledExecutor destroy 终态化补偿）同 diff；② g_executors 单点统筹（C6-002/C6-003 + C1-015/C4-005/CL-018，回归面含插件 deactivate 与 lint mojo 循环）；③ C6-010 抽象基类一次收敛 8 处；④ C6-005+C6-012+C6-013 模型缓存销毁链合并实施；⑤ C6-004/C6-006/C6-007/C6-011 独立小改；⑥ C6-008 按三选一落档。C4-003（含 CL-011 同位点）随 C4 轮批次。
