# C1 共享可变状态与可见性 —— nop-kernel 并发设施深审（第 1 轮盲审初审）

| 项 | 值 |
|---|---|
| 审计单元 | 平台并发设施自身实现：A 面 `nop-kernel/nop-commons/.../io/nop/commons/{concurrent,cache,pool}`（85 files / 7,550 行）；B 面 nop-core / nop-xlang 全局模型缓存与注册表单例（基线 46 候选中按「模型/元数据缓存」判据取相关子集） |
| 仓库 | `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`） |
| 维度 | C1：共享可变状态与可见性（线程安全与内存可见性；check-then-act 竞态；非同步惰性初始化；static 可变状态；ThreadLocal 误用；发布安全） |
| 盲审纪律 | 本轮为盲审：**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅读方法论文件（`ai-dev/skills/concurrency-resource-audit-prompt.md`）与 owner doc（`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md`、`ioc-and-config.md`，用于判级校准）与 live code。所有发现均来自当前 live code 逐文件审读。 |
| 判级口径 | 平台设施被全平台并发调用：设施内部数据结构被并发调用即必须自证线程安全；「默认用法」门槛极低（任何并发 submit/load/get 都算常见条件）。竞态发现必须写出具体 T1/T2 交错时序，写不出的一律 P2 并标注「理论风险，未具体化」。 |

## 检查范围与状态载体清单

### A 面（nop-commons concurrent/cache/pool）——逐文件审读的状态载体

| 载体 | 文件 | 共享状态 | 同步机制 | 判定 |
|---|---|---|---|---|
| 池计数 | `StandardThreadPoolExecutor` | `submittedCount`(AtomicInteger)、`lastContextStoppedTime`/`lastTimeThreadKilledItself`(AtomicLong)、`threadRenewalDelay`(plain long) | 原子类；`threadRenewalDelay` 无 volatile | 计数安全；`execute`/`force` 复合路径有账目泄漏（C1-002）；renewalDelay 跨线程可见性弱（后果轻） |
| 任务队列 | `TaskQueue extends LinkedBlockingQueue` | `parent`(volatile)、`forcedRemainingCapacity`(plain Integer) | LBQ 自身锁 + volatile parent | `force` 的 shutdown 检查-then-act 与 C1-002 同源；`forcedRemainingCapacity` 注释声称单线程读写，但 `remainingCapacity()` 可被 stats 线程读到（弱后果，并入 C1-002 失效面） |
| 池包装 | `DefaultThreadPoolExecutor` | `executor`、`config`（plain 字段） | @PostConstruct 单线程 init | init/destroy 生命周期内安全；destroy 不等待（C4 范畴，不立案） |
| 同步池 | `SyncThreadPoolExecutor` | 无实例可变状态（delegate ContinuationExecutor 的 ThreadLocal） | — | 安全（按反虚构预注：无 synchronized 方法，实测一致） |
| 调度器 | `DefaultScheduledExecutor` | `executor`(plain，可被 destroy 置 null)、`enabled` | 无锁 check-then-act | **C1-003** |
| 全局池注册 | `GlobalExecutors` | `g_executors`(CHM) | computeIfAbsent | 安全；`getExecutor` 的 get-then-create 有良性双检（computeIfAbsent 兜底） |
| 限流执行器 | `RateLimitExecutorImpl` | `promiseMap`(CHM) + debounce 局部 `holder[0]` 数组 | compute（throttle 安全）；debounce 无原子性 | **C1-004** |
| 随机延迟调度 | `ExecutorHelper.scheduleWithRandomDelay` | `AtomicReference<Future<?>> ref` | CAS 字段但 set 顺序有竞态 | **C1-005** |
| Continuation | `ContinuationExecutor` | `ThreadLocal<Continuation>`（每线程独立 ArrayDeque + inLoop） | ThreadLocal 线程封闭 | 安全（未 remove → 池化线程常驻小对象，量级可忽略） |
| 顺序执行器 | `SequentialTaskExecutor` | `status`(AtomicInteger 位掩码)、`hasTask`/`totalExecutionTime`(volatile) | CAS 状态机 | C1 面安全（hasTask 双设防丢唤醒经推演正确）；`execute` 丢任务属 C8 → CL-002；当前无生产调用方 |
| 循环同步 | `CycleSynchronizer` | `finished`/`processing`(volatile)、`cyclePromise`/`onCycleCompleted`(synchronized this 守护) | synchronized | **C1-010**（跨周期 finish/beginCycle 竞态 + 持锁跑回调） |
| 轮询资源 | `RoundRobinSupplier` | `objects`(volatile AtomicReferenceArray)、`nextIndex`(AtomicInteger) | volatile 发布 + resize synchronized | **C1-001**（shrink 关闭窗与 get 竞态） |
| 阻塞队列 | `OverflowBlockingQueue`/`DedupBlockingQueue` | 包装 ArrayBlockingQueue/CHM | 委托 JDK 队列 + CHM | C1 面安全；`send` DROP_ELDEST+容量 0 死循环、Dedup clear 竞态（弱）归 C8 → CL-001；Dedup 当前无生产调用方 |
| 信号量 | `HighWatermarkSemaphore`/`DefaultSemaphore` | `usedPermits`/`overHighWatermark` | ReentrantLock 全覆盖 | 安全（等待条件、signalAll 时机、超时边界逐条推演无误） |
| 限流器 | `DefaultRateLimiter` | Guava RateLimiter + AtomicLong 计数 | Guava 内部锁 | 安全 |
| 锁管理器 | `LocalResourceLockManager` | `locks`(CHM)、`lastCleanUpTime`(volatile)、config 字段 | CHM + per-state latch 锁 | 租约续租/过期删除互斥正确（checkTimeout/tryResetLease/isHoldingLock 均持 latch 锁）；`tryLockWithLease`:169 的无锁 expireTime 读经推演不会误删有效锁（续租自身要求 expireTime>now，时钟序封闭），不立案 |
| 锁句柄 | `ResourceLock`/`ResourceMultiLock`/`ResourceLockState` | `lock`/`locks`(plain)、`lockTime`/`expireTime`(volatile) | unlock synchronized；读路径无锁 | **C1-007**（MultiLock 排序被 Set.of 破坏）、**C1-012**（句柄字段可见性） |
| 文件锁 | `LocalFileLock` | `lock`/`channel`/`randomAccessFile`(plain) | 无 | **C1-011** |
| 缓存 | `LocalCache`(Caffeine)/`MapCache`/`GlobalCacheRegistry`/`LocalCacheProvider` | Caffeine Cache/CHM | Caffeine/CHM 原语 | C1 面安全；`getAndSet`/`remove(K,V)` 语义族分裂 → **C1-013** |
| 缓存契约 | `ICache` default 方法 | — | putIfAbsent synchronized(this) | 安全（文档已声明原子性契约） |
| ThreadLocal | `NamedThreadLocal` 载体：`ResourceDependsManager.dependsStack`、`BatchTaskGlobals.s_taskContext`、`ResourceTenantManager.s_initializingTenant`、`ContinuationExecutor.continuation` | — | remove() 纪律：dependsStack 空栈即 remove（合规）；s_taskContext 有 removeTaskContext；s_initializingTenant set(false) 不 remove（弱） | 总体合规，不立案 |
| 批处理 | `AbstractBatchProcessService`/`BlockingSourceConsumeTask` | `executor`/`queue`/`task`(init 期赋值)、`processingCount`(AtomicInteger) | 原子类 | C1 面安全；消费失败静默丢弃 → CL-003 |
| 其他 | `LimitedExecutor`（信号量双路径释放正确）、`throttleExecute`（Error 路径漏释放，P3 级不立案）、`IAtomicLong`/`QueueOverflowPolicy`/`ThreadPoolConfig`（无状态/值对象） | — | — | 安全 |

### B 面（模型/元数据缓存与注册表单例）——相关子集审读

| 载体 | 文件 | 判定 |
|---|---|---|
| 组件模型管理器 | `ResourceComponentManager`（577 行） | `_instance` volatile ✅；五张注册/缓存 map 全 CHM ✅；`makeModelCache` computeIfAbsent 单次构建 ✅；`registerComponentModelConfig`:226-229 containsKey→put 非原子（init 期，P3 不立案）；`ComponentCacheEntry.transformed` CHM ✅ |
| 缓存条目 | `ResourceCacheEntry` | `object`/`deps`/`lastLoadTime` volatile ✅；`getObject` 的 check-then-act 有 synchronized 内双检（同 key 并发加载去重成立 ✅）；`reloadObject` synchronized ✅；旧对象销毁 vs 在途读者为 RCU 式弱契约（模型不可变前提下可接受，不立案） |
| 装载缓存 | `ResourceLoadingCache`/`ResourceContentCache` | LocalCache(Caffeine) 委托 ✅；ResourceTimestampCache 全文件注释（死文件，不审） |
| 租户层 | `ResourceTenantManager`/`TenantAwareResourceLoadingCache`/`TenantAwareDependencyManager` | `_instance` volatile ✅；tenantCaches 为 Caffeine 装载缓存（同 tenant 并发创建去重 ✅）；`isSupportTenant` 惰性初始化非同步 → **C1-008**；`s_initializingTenant` ThreadLocal 合规 |
| 依赖管理 | `ResourceDependsManager`/`ResourceDependsStack`/`dependencyMap`(CHM) | ThreadLocal 栈线程封闭 ✅；merge 带 version 仲裁 ✅ |
| 反射 | `ReflectionManager`/`ClassExtension` | `_instance` final ✅；`introspectCache` Caffeine weakKeys + `tempLoaders`(CHM) per-class loader synchronized 去重（同 key 并发 introspect 去重成立 ✅）；ClassExtension 全方法 synchronized 且读返回拷贝 ✅；`newRawType` putIfAbsent 后返回本地实例（重复构建，P3 不立案） |
| 注册表 | `EvalGlobalRegistry`/`StdDomainRegistry`/`ScriptCompilerRegistry`/`EvalBackendRegistry`/`XPathOperatorRegistry`/`GlobalCacheRegistry`/`CodeLangMap`/`GlobalStatManager`/`JsonWhitelist`/`ValueResolverCompilerRegistry`/`ObjPropMapperRegistry(·Xml)` | `_instance` 全部 static final（类初始化安全发布）✅；容器全 CHM/COW ✅；EvalBackendRegistry register/unregister synchronized ✅ |
| 语义共享基座 | `XLangSemantics` 三 CHM（`OBJ_FUNCTION_HANDLES`/`PROP_ACCESSORS`/`CLASS_MODELS`） | computeIfAbsent / get-then-put（重复解析良性）✅；写路径为运行期缓存填充、无删改路径，编译期写 vs 运行期读无冲突 |
| 单例可替换槽 | `ModuleManager`/`ErrorMessageManager`/`DictProvider`/`XPathProvider`/`VirtualFileSystem`/`BeanTool`/`SchemaBasedValidator`/`JsonWhitelistChecker`/`XSchemaToJsonSchema`/`RegisterModelDiscovery`/`VarCollector` | `_instance` 非 volatile 且支持运行时 registerInstance → **C1-009**（族，F3 全清单见该条） |
| i18n | `I18nMessageManager` | `_instance` volatile ✅；`enabledLocales` plain（init 期单写，启动 HB 覆盖，P3 不立案） |
| 出界使用点（不立案） | `FileWatcher`/`ZipFileWatcher`/`CliWatchZipCommand`（RateLimitExecutorImpl.debounce 的调用方，作为 C1-004 失效面引用）；FutureHelper/ContextProvider 属 nop-api-core，仅作调用语境引用 | — |

**反虚构预注核验**：SyncThreadPoolExecutor 确无 synchronized 方法（实测）；无「全方法 synchronized」线程池类；FutureHelper/ContextProvider 未立案（越界）。

---

## 发现条目

### [nop-kernel-concurrency-C1-001] RoundRobinSupplier.resize 收缩路径：并发 get() 可取得正在被关闭的资源（use-after-close）

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/RoundRobinSupplier.java:41-45, 47-87, 89-93`
- **证据片段**:
  ```java
  public T get() {
      AtomicReferenceArray<T> objects = this.objects;          // (a) 读取快照
      int index = Math.abs(nextIndex.getAndIncrement() % objects.length());
      return objects.get(index);                               // (b) 从快照取对象
  }
  public void resize(int size) {
      ...
      synchronized (this) {
          ...
          // 缩小
          this.objects = array;          // (c) 发布新数组
          toClose = objects;             // (d) 旧数组待关
      }
      ...
      } finally {
          if (toClose != null)
              closeNext(toClose, size);  // (e) 在锁外关闭旧数组 [size, oldLen) 段
      }
  ```
- **严重程度**: P1
- **并发场景**: 配置刷新线程（调用 `resize`，生产调用点 `LettuceRedisConnectionProvider.updateConnections`:98，即 redis 连接数运行期调整）与任意业务线程（调用 `get()` 获取 redis 连接，`LettuceRedisConnectionProvider`:63/74/83，每请求热路径）。
- **触发时序**: T1（业务）在 (a) 捕获旧数组 oldLen=10，计算得 index=7，尚未执行 (b)；T2（刷新）完成 (c)(d) 发布新数组并退出锁，随即在 (e) 对旧数组下标 [5,10) 段执行 `IoHelper.safeClose(objects.get(i))` 关闭 index=7 的连接；T1 继续 (b) 取到该**已关闭**的连接并交付业务使用。结果：业务线程拿到已 close 的 redis 连接（Lettuce 侧表现为连接已关闭异常/断连），且该资源在借出方毫不知情时被销毁。T1/T2 顺序反转（(b) 先于 (e)）则无害——缺陷窗为 (a) 与 (b) 之间被 (e) 插入。
- **现状**: `resize` 收缩时对旧数组尾段的关闭发生在 volatile 发布之后、锁外，`get()` 持有旧数组引用的读者无任何防护；扩容路径（`objects.length() < size`）无此问题（旧数组全段保留）。`close()` 与并发 `get()` 同理（关闭全部对象而 get 仍可取得）。
- **风险**: 运行期调整 redis 连接池大小（`resize` 公开 API）叠加常态业务取连接即触发；表现为偶发「连接已关闭」错误与借出对象失效，属具体化竞态（明确触发链）。
- **建议**: 关闭动作移入 `synchronized (this)` 并使 `get()` 亦在锁内读取（量级为连接数，锁代价可忽略）；或采用两阶段退役：`resize` 仅将旧数组标记 retiring，延迟一轮 `nextIndex` 绕行后再关闭（借用代际计数）。修复后应补「resize 与 get 并发」回归测试。
- **失效面枚举**: ① `resize` 收缩分支——本条主体；② `resize` 扩容分支——旧对象不关闭，安全；③ `close()`——与 get 存在同型竞态（对象关闭后仍可被 get 返回），同族待修；④ 唯一生产使用方 `LettuceRedisConnectionProvider`（get×3 路径 + resize 调用点）——全部受影响；⑤ 测试代码无相关覆盖（不构成守卫）。
- **信心水平**: 确定（时序具体、生产调用链存在）
- **误报排除**: 非「volatile 字段直接报不安全」：问题不在 `objects` 的可见性（volatile 正确），而在「快照读-再解引用」与锁外销毁的复合时序；也非已声明边界（round-robin 语义未在 owner doc 声明允许返回已关闭对象）。
- **复核状态**: 保留 P1；新发现（设施本体口径）；get:41-45 快照窗、resize 收缩锁外 closeNext:83-86、close():96-103 同族逐行亲核，生产调用方 LettuceRedisConnectionProvider（get×3:63/74/84 + refreshConfig→resize:98）与 IoC 配置订阅→refreshConfig 派发链（BeanDefinition:799-803/BeanContainerImpl:359-371）亲核证实运行期可达，触发条件仅「运行期改连接池配置×常态取连接」两项合取、时序每步可信——终裁维持 P1；历史 check/nop-commons.md 仅立案扩容失败泄漏（已修 commit 22129fa9f7），收缩 use-after-close 系本轮首报

### [nop-kernel-concurrency-C1-002] StandardThreadPoolExecutor.execute 拒绝重试路径：queue.force 抛出时 submittedCount 永久泄漏

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/StandardThreadPoolExecutor.java:152-175`、`TaskQueue.java:55-66`
- **证据片段**:
  ```java
  public void execute(Runnable command, long timeout, TimeUnit unit) {
      submittedCount.incrementAndGet();                 // (a) 先记账
      try {
          super.execute(command);
      } catch (RejectedExecutionException rx) {
          if (super.getQueue() instanceof TaskQueue) {
              final TaskQueue queue = (TaskQueue) super.getQueue();
              try {
                  if (!queue.force(command, timeout, unit)) {
                      submittedCount.decrementAndGet(); // (b) 仅 force 返回 false 时回冲
                      throw new RejectedExecutionException(...);
                  }
              } catch (InterruptedException x) { ... submittedCount.decrementAndGet(); ... }
              // 缺口：force 内部抛出 RejectedExecutionException（下述）时既不回冲也不重抛前清账
          } else { submittedCount.decrementAndGet(); throw rx; }
      }
  }
  // TaskQueue.force:
  if (parent == null || parent.isShutdown())
      throw new RejectedExecutionException("...task-queue-not-running");  // (c) 直接抛出
  ```
- **严重程度**: P2
- **并发场景**: 应用停机线程执行 `DefaultThreadPoolExecutor.destroy()` → `executor.shutdown()`，同时业务线程仍在经 `execute`/`submit`（`DefaultThreadPoolExecutor.execute`:97 直接进入本 override；`ExecutorHelper.submit` 亦最终走 `execute`）提交任务。
- **触发时序**: T2 执行 `shutdown()` 使池进入 SHUTDOWN；T1 在 (a) 完成计数 +1，`super.execute` 因 `isShutdown` 抛 REE 进入 catch；T1 调 `queue.force` 命中 (c) `parent.isShutdown()` 为 true 抛出 REE——该异常路径**未经过 (b) 回冲**，直接向调用方传播；`submittedCount` 永久 +1。多次并发提交累积偏移。
- **现状**: 计数回冲只覆盖「force 返回 false」「force 被中断」「非 TaskQueue 队列」三路，遗漏「force 抛 REE」路。
- **风险**: `submittedCount` 是 `TaskQueue.offer` 弹性启发式的输入（`offer`:77 `parent.getSubmittedCount() <= parent.getPoolSize()`）：虚高使 offer 持续走「入队」分支、抑制向 maxPoolSize 扩容，池弹性退化；同时 `getSubmittedCount()` 监控口径失真。停机场景本身危害有限（池将亡），但运行期 `unregister/register`（`GlobalExecutors.register`:102-107 会 destroy 旧实例）与仍在使用旧实例的线程并发时，同一竞态发生在**活池交接期**。
- **建议**: 将 `queue.force(...)` 包入 try-catch (RejectedExecutionException)，异常路径先 `submittedCount.decrementAndGet()` 再重抛；或在方法入口记 `boolean accounted` 于 finally 统一回冲。
- **失效面枚举**: ① 本 override 的 force-REE 路径——本条主体；② `force` 返回 false 路径——已回冲 ✅；③ `InterruptedException` 路径——已回冲 ✅；④ 非 TaskQueue 队列路径——已回冲 ✅；⑤ `afterExecute`:94-95 的 decrement——任务真正入队后由 worker 执行，正常 ✅；⑥ `ExecutorHelper.newThreadPoolExecutor`:81-83 的 `maxPoolSize<0` 分支用原生 TPE + SynchronousQueue（不走 TaskQueue/本 override），不受影响 ✅；⑦ `TaskQueue.forcedRemainingCapacity` 非 volatile 被 stats 线程经 `remainingCapacity()` 交叉读——同文件弱可见性问题，后果仅监控口径抖动，随本条一并记录不单独计数。
- **信心水平**: 确定（代码路径完整可推演；触发需 shutdown 与提交并发）
- **误报排除**: 非「原语使用误报」：缺陷在跨方法的异常账目路径遗漏（复合操作），非 AtomicInteger 本身；非低频路径——`execute` 是全平台提交任务的默认主路径，shutdown 交接为部署期常态。
- **复核状态**: 降级 P2→P3；新发现（设施本体口径）；force 抛 REE 无回冲属实（:152-175/TaskQueue:61-63 亲核），但该路径仅 parent.isShutdown() 时可达且 SHUTDOWN 后 execute 不再走 offer（TPE.isRunning 短路）——泄漏只发生在将亡实例的 submittedCount 监控口径上，无活池后果；且原「抑制向 maxPoolSize 扩容」方向判断反了：TaskQueue.offer:74-83 实测推演，submittedCount 虚高使 `submittedCount<=poolSize` 更易为 false→offer 返回 false→促使 TPE 提前扩容至 max（过扩容而非抑制）

### [nop-kernel-concurrency-C1-003] DefaultScheduledExecutor：checkEnabled-then-use 与 destroy() 竞态，提交方读到已置空的 executor 触发 NPE

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/DefaultScheduledExecutor.java:88-98, 100-141`
- **证据片段**:
  ```java
  public void destroy() {
      if(executor != null) {
          executor.shutdownNow();
          executor = null;                 // (a) 无锁置空
      }
  }
  private void checkEnabled() {
      if (executor == null) { throw new NopException(ERR_SERVICE_NOT_ACTIVE)...; }   // (b) 检查
  }
  public <T> CompletableFuture<T> submit(Callable<T> task) {
      checkEnabled();
      return ExecutorHelper.submit(executor, task);   // (c) 使用（与 (b) 之间无原子性）
  }
  ```
- **严重程度**: P2
- **并发场景**: 停机线程调用 `destroy()`（IoC PreDestroy / `GlobalExecutors.register` 替换旧实例），业务/定时线程调用 `submit`/`schedule`/`scheduleAtFixedRate`/`execute`（globalTimer 被 `LocalResourceLockManager.init`:104 等平台设施持用）。
- **触发时序**: T1 进入 `submit`，在 (b) 通过检查（此刻 executor 非 null）；T2 执行 (a) `executor = null`；T1 继续 (c)，把 `null` 传给 `ExecutorHelper.submit` → `executor.submit(...)` 抛 `NullPointerException`，而非声明的 `ERR_SERVICE_NOT_ACTIVE`。`schedule`:120-123、`scheduleAtFixedRate`:126-129、`scheduleWithFixedDelay`:132-135、`execute`:138-141 同型。
- **现状**: 检查与使用之间无同步、无局部变量快照（`ExecutorHelper.submit(executor, ...)` 读取的是字段而非检查时的值），`executor` 亦非 volatile。
- **风险**: 停机/实例替换窗口内的提交以裸 NPE 失败：调用方（多为定时任务重提交路径）收到非类型化异常，错误语义违约；若调用方按「服务未激活」做降级判断，NPE 会走错分支。属失败路径错误类型缺陷，无数据损坏。
- **建议**: 方法体内先 `ScheduledThreadPoolExecutor e = this.executor;` 快照后判空使用（单字段快照即可同时修复可见性与 check-then-act）；或将 destroy 改为仅 `shutdownNow()` 不置 null、`isDestroyed()` 已有 `isShutdown()` 判定可覆盖。
- **失效面枚举**: ① `submit(Callable)`:108-111——受影响；② `submit(Runnable,V)`:113-117——受影响；③ `schedule`:120-123——受影响；④ `scheduleAtFixedRate`:126-129——受影响；⑤ `scheduleWithFixedDelay`:132-135——受影响；⑥ `execute`:138-141——受影响；⑦ `stats()`:84-86——destroy 后传 null 给 `ExecutorHelper.getStats` 同样 NPE（管理端点，同族）；⑧ 兄弟类 `DefaultThreadPoolExecutor.destroy`:91-93 不置 null（提交方得到 REE 而非 NPE，走 C1-002 路径），同族不同症，已单独登记；⑨ `BindScheduledExecutor` 委托 timer，间接受 ①-⑥ 影响。
- **信心水平**: 确定（交错唯一且必现于窗口内）
- **误报排除**: 非「低频路径粗粒度同步合理」——这不是锁粒度问题而是检查-使用非原子；后果为类型化错误违约，按判级口径不到 P1（无损坏/泄漏/挂死），P2。
- **复核状态**: 保留 P2；新发现（设施本体口径）；六入口+stats() 检查-使用非原子与 destroy 置空 :88-93 逐行亲核（:84-86/:107-141 全部核过，无局部快照）；使用方并发常态成立——WI 1.4 nop-job C4（6 个 scanner fallback + RpcPollTaskManager 共用 globalTimer）、WI 1.5 C4-001/MA-04（wf/task 全簇调度恒跑 globalTimer）为使用方条目指向本设施，本条按设施本体口径立案，Phase 6 合并裁定跨单元关联

### [nop-kernel-concurrency-C1-004] RateLimitExecutorImpl.debounce：get-cancel-schedule-put 非原子 + holder[0] 不安全发布，导致重复执行与 promiseMap 陈旧条目

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/RateLimitExecutorImpl.java:50-67`
- **证据片段**:
  ```java
  public void debounce(final Object key, long delay, Runnable task) {
      Future<?> old = promiseMap.get(key);      // (a) 无锁读
      if (old != null) old.cancel(true);        // (b) 取消旧任务
      final Future<?>[] holder = new Future<?>[1];   // plain 数组，非 volatile
      Future<?> future = executorService.schedule(() -> {
          try { task.run(); }
          finally { promiseMap.remove(key, holder[0]); }   // (c) 定时线程读 holder[0]
      }, delay, TimeUnit.MILLISECONDS);
      holder[0] = future;                       // (d) 在提交之后才写
      promiseMap.put(key, future);              // (e) 无原子性
  }
  ```
- **严重程度**: P2
- **并发场景**: 两个及以上业务线程对同一 key 并发调用 `debounce`（生产形态：`FileWatcher` 多个 watch 路径回调、`ZipFileWatcher`、`CliWatchZipCommand`——watch 回调可来自不同 watch 线程）；以及 `delay=0`（`replace(key,task)`:70-72 即 debounce(key,0,…)）时定时线程与提交线程竞争。
- **触发时序（竞态一：重复执行）**: T1 执行 (a) 读得 null；T2 执行 (a) 亦读得 null；T1 schedule task1、(e) put f1；T2 schedule task2、(e) put f2（覆盖 f1）。f1 从未被取消，delay 到期后 task1 与 task2 **均执行**——「取消上次任务」的去抖契约被破坏，回调重复触发（FileWatcher 场景 = handler 被重复调用）。
- **触发时序（竞态二：holder[0] 不安全发布 → NPE/陈旧条目）**: T1 在 `schedule` 提交后、(d) 执行前，T2（定时线程，delay=0）已开始执行任务体；T2 在 (c) 读 `holder[0]`：与 (d) 之间无 happens-before（提交 HB 只覆盖提交前状态）→ 可读到 null；`ConcurrentHashMap.remove(key, null)` 按 JDK 契约抛 NPE，且异常发生在 FutureTask 的 finally 中被 Future 吞掉（F9 静默）；即便读到引用，也可能读到未完全发布的值。结果：map 中残留指向已完成 future 的陈旧条目，直到同 key 下一次 debounce 才被覆盖；key 基数大时为慢性泄漏（长时运行 C7 关联）。
- **现状**: `throttle` 用 `compute` 原子化（正确先例就在同类同文件 :32），`debounce` 未复用该纪律。
- **风险**: 去抖语义静默失效（重复执行副作用型任务）+ 定时器表陈旧条目累积；均无显式报错，问题在负载/并发上升后显现。
- **建议**: 以 `promiseMap.compute(key, …)` 重写 debounce：compute 内完成「cancel 旧 future + schedule 新任务 + 返回新 future」，schedule 回调中仅 `remove(key, 新future)`（引用直接捕获为 effectively-final 局部变量，消除 holder 数组）；cancel 与 schedule 的顺序在 compute 串行域内天然安全。
- **失效面枚举**: ① `debounce`:50-67——本条主体；② `replace`:70-72（=debounce(key,0)）——竞态二最易触发；③ `throttle`:29-47——compute 原子，安全 ✅；④ 生产调用方 `FileWatcher.watch`:68-73（key="process"，watch 回调线程并发）、`ZipFileWatcher`、`nop-cli CliWatchZipCommand`——全部受 ①② 影响；⑤ 测试 `TestRateLimitExecutor` 为单线程语义测试，不构成并发守卫。
- **信心水平**: 确定（竞态二有 JDK 契约支撑；竞态一交错明确）
- **误报排除**: 非「CHM 原语误报」：`promiseMap` 本身线程安全，缺陷在跨 (a)-(e) 的复合操作；亦非 F10 类「声明单线程」——接口无单线程约定，`IRateLimitExecutor` 即为并发设施。
- **复核状态**: 保留 P2；新发现；debounce:50-67 双竞态逐行亲核（竞态一双 null→双 schedule 重复执行宽窗成立；竞态二 holder[0] 提交后写不被 executor 提交 HB 覆盖，delay=0 定时线程可读 null→CHM.remove(key,null) NPE 于 finally 中被吞），throttle compute 先例 :32 属实；生产调用方 FileWatcher:70/ZipFileWatcher:46/CliWatchZipCommand:128 全部亲核；历史 check/nop-commons.md 审计（commit 22129fa9f7）仅修自清理泄漏且 holder[0] 模式系该修复引入，非原子性为首报

### [nop-kernel-concurrency-C1-005] ExecutorHelper.scheduleWithRandomDelay：AtomicReference 后写覆盖 + 任务体无 isDone 守卫，取消可被在飞迭代吞没（幽灵周期任务）

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:210-238`
- **证据片段**:
  ```java
  AtomicReference<Future<?>> ref = new AtomicReference<>();
  Callable<Void> command = new Callable<>() {
      public Void call() {
          try {
              task.run();                                            // (a) 无 future.isDone() 检查
              ref.set(executor.schedule(this, ...));                 // (b) 重调度并覆盖句柄
          } catch (Throwable e) { future.completeExceptionally(e); }
          return null;
      }
  };
  Future<?> f = executor.schedule(command, initialDelay, timeUnit);
  ref.set(f);                                                        // (c) 提交之后才写初值
  future.exceptionally(ex -> { ref.get().cancel(false); return null; });  // (d) 唯一的取消通道
  ```
- **严重程度**: P2
- **并发场景**: 调用方线程（发起 cancel）与调度器线程（执行迭代）并发；对比同类 `BindScheduledExecutor.scheduleWithFixedDelay`（:73-115）——其每个迭代入口均有 `future.isDone()` 双重守卫，本方法缺失该守卫。
- **触发时序**: T2（timer 线程）第 N 轮 `call()` 已出队开始执行 (a)；T1（调用方）`future.cancel(false)` → exceptionally 通道 (d) 执行 `ref.get().cancel(false)`，cancel 的是第 N 轮 future——该任务**已在执行**，cancel 无效；T2 完成 (b) 重调度第 N+1 轮并 `ref.set(f(N+1))`。此后无任何通道再触发 (d)（future 已终态），第 N+1 轮及以后永远继续执行——取消静默丢失，周期任务成为幽灵任务。另一时序：T2 首轮在 (c) 之前抢跑（initialDelay=0），(c) 将 ref 覆盖回已完成的首轮 future，此后 (d) 永远 cancel 到已完成任务。
- **现状**: 状态载体（AtomicReference）本身安全，缺陷在「先提交后发布句柄」的顺序与任务体缺少终态守卫的组合。
- **风险**: 取消/关闭路径泄漏一个永不停止的周期任务（含其副作用），属取消路径资源泄漏的具体化竞态。当前 grep 全仓无生产调用方（仅接口声明），为潜伏缺陷——一旦被周期性后台任务（如退避重试、抖动调度）采用即按上述时序复现。
- **建议**: 对齐 `BindScheduledExecutor` 先例：① 每轮 `call()` 入口与 (b) 前检查 `future.isDone()`；② 初值写入改为 `AtomicReference` 先 set 占位或在 call() 内 `if (future.isDone()) return null;`；③ 更简方案：改用 `scheduleWithFixedDelay` 平台实现（BindScheduledExecutor 已修此问题）。
- **失效面枚举**: ① `ExecutorHelper.scheduleWithRandomDelay`——本条主体；② `ExecutorHelper.submit/schedule`:128-199——cancel 走 `FutureHelper.bindCancel` 绑定底层 JDK future，取消链完整 ✅；③ `BindScheduledExecutor.scheduleWithFixedDelay`——有 isDone 双守卫 ✅（但 `timerHandle.set(f)`:115 在 schedule 后写，窗口被任务入口 isDone 守卫兜住，残余仅一次空触发，无害）；④ `BindScheduledExecutor.scheduleAtFixedRate`:65-69——JDK 原生周期语义，cancel 传播依赖调用方，无本条形态 ✅。
- **信心水平**: 确定（交错具体；当前无生产调用方，故按潜伏缺陷 P2 而非 P1）
- **误报排除**: 非「FutureTask 原语误报」：`BindScheduledExecutor` 同文件族已用守卫修复同型问题，证明这是已知必须防御的形态（F3 思路），本处是漏修兄弟。
- **复核状态**: 保留 P2（潜伏）；新发现；:210-238 亲核确认任务体无 isDone 守卫 + ref 后写覆盖 + exceptionally 单次取消通道，对照先例 BindScheduledExecutor:91/96 双守卫属实；全仓 grep 无生产调用方（仅 IScheduledExecutor.java:34-36 default 委托）——潜伏标注维持；备注：WI 1.6 C8-001↔nop-job C4-003 的 BindScheduledExecutor 链死停摆为使用方 P1，载体同族但站点不同，不构成重复立案

### [nop-kernel-concurrency-C1-006] ReactiveObjectMap.get：首次调用即 NPE（CHM.remove(key, null)）+ 失败结果永久毒化缓存条目（死代码中的确定性缺陷）

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ReactiveObjectMap.java:44-62`
- **证据片段**:
  ```java
  public Object get(String key, IEvalFunction fn, IEvalScope scope) {
      ReactiveObject obj = objMap.get(key);
      if (obj == null || obj.isChanged()) {
          objMap.remove(key, obj);      // (a) obj==null 时 = CHM.remove(key, null)
          obj = new ReactiveObject(key);
          ReactiveObject oldObj = objMap.putIfAbsent(key, obj);
          if (oldObj != null) { obj = oldObj; }
          else {
              try { obj.future.complete(...collectDependsTo(...)); }
              catch (Exception e) { obj.future.completeExceptionally(e); }   // (b) 失败后条目仍留在 map
          }
      }
      return FutureHelper.getFromFuture(obj.future);
  }
  ```
- **严重程度**: P2（当前无生产调用方，缺陷本体为确定性必现）
- **并发场景**: 任意线程首次调用某 key（单线程即触发 (a) 的 NPE）；多线程时另有竞态一/二（见下）。
- **触发时序（缺陷一：首次使用 NPE）**: 任一线程首次调用 `get(key)`：`objMap.get(key)` 返回 null → 条件短路为 true → (a) `ConcurrentHashMap.remove(key, null)`。JDK 契约：key 或 value 为 null 时 remove(key,value) 抛 `NullPointerException`。**首次调用必然 NPE**——该类按现状不可用。意图显然是 `if (obj != null) objMap.remove(key, obj)`。
- **触发时序（缺陷二：失败毒化）**: T1 putIfAbsent 胜出执行 fn 抛异常 → (b) future 异常完结，但条目未从 map 移除；`ReactiveObject.deps` 从未 freeze/登记（与 `ResourceCacheEntry.loadObject` 不同，无 `deps.freeze()` + traceAllDepends 回灌），后续 `obj.isChanged()` 依赖未冻结的 `VirtualResourceDependencySet` 判定，通常返回 false → 后续所有调用者（含 putIfAbsent 落败方 T2，其已 `obj = oldObj`）经 `FutureHelper.getFromFuture` 永远取回同一异常——失败结果被缓存且无自动重试路径（F9 状态不一致）。
- **触发时序（缺陷三：删除-重插竞态）**: T1、T2 同时读到同一 changed obj：T1 remove 成功、T2 remove(key, obj) 失败（值不匹配）继续；T1 putIfAbsent(obj1)、T2 putIfAbsent(obj2) 落败取 obj1 —— 此分支语义尚可（胜者结果共享）；但若 T1 的 fn 尚在执行而 T3 因 `obj.isChanged()` 再次进入删除分支，会移除 T1 **正在加载**的条目并第三轮重算——重复执行 fn（有 `collectDependsTo` 副作用登记路径时非幂等风险）。
- **现状**: grep 全仓无生产调用方（纯死代码），故未按 P1 立案；但类为 public API 面，随时可能被采用，缺陷一为必现缺陷。
- **风险**: 一经启用：首次调用 NPE（必现）；异常路径永久毒化；并发下重复执行。
- **建议**: 修复 (a) 为条件删除；失败路径补 `objMap.remove(key, obj)` 回滚 + future 完结（对齐 `ResourceCacheEntry.getObject`:185-191 的「失败清空下次重载」语义）；或直接删除该死类。修复需附「首调用、失败重试、并发 get」三用例。
- **失效面枚举**: ① `ReactiveObjectMap` 全类（唯一入口 get:44-62）——本条主体；② 同目录 `ResourceCacheEntry.getObject` 的 check-then-act——有 synchronized 双检 ✅（正确先例）；③ `ResourceComponentManager.loadComponentModel`:343 的 `transformed.computeIfAbsent`——CHM 原子 ✅。全族仅本类失守。
- **信心水平**: 确定（NPE 为 JDK 契约级必现；毒化路径经与 ResourceCacheEntry 对照确认缺回滚）
- **误报排除**: 非「测试代码标准」问题，也非「无调用方=无缺陷可报」：public 设施的必现 NPE 属设施自证失败；按无生产触发链降档 P2 而非驳回。
- **复核状态**: 保留 P2（死代码）；新发现；get:44-62 亲核，首调用 `objMap.remove(key, obj)` 于 obj==null 时即 `CHM.remove(key,null)` NPE 为 JDK 契约必现（类按现状不可用），失败路径 completeExceptionally 后条目无回滚永久毒化属实；全仓 grep 无调用方核实，潜伏定级维持

### [nop-kernel-concurrency-C1-007] ResourceMultiLock：排序获取意图被 Set.of 无序性破坏，跨锁集重叠时可形成死锁环

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/lock/impl/ResourceMultiLock.java:34-37, 105-120`
- **证据片段**:
  ```java
  public ResourceMultiLock(IResourceLockManager lockManager, Set<String> resourceIds, ...) {
      ...
      this.resourceIds = Set.of(LockHelper.sortResourceIds(resourceIds));  // (a) 排序后交给无序集合
  }
  ...
  for (String resourceId : resourceIds) {          // (b) 遍历顺序 ≠ 排序顺序
      IResourceLock lock = lockManager.getLock(resourceId, holderId);
      if (!lock.tryLockWithLease(remainWait, leaseTime + remainWait)) return false;
      locks.add(lock);                             // (c) 逐个持锁
  ```
- **严重程度**: P2（死锁可具体化，但当前无生产调用方构造 MultiLock）
- **并发场景**: 两个线程分别获取两个**部分重叠但不同**的资源集合的多锁（有序获取是 `LockHelper.sortResourceIds` + 排序遍历这一设计的存在理由，即全序防环）。
- **触发时序**: `Set.of(...)`（ImmutableCollections.SetN）迭代顺序由元素哈希探测决定，**不是**排序顺序，且不同内容集合的迭代顺序相互独立。设 T1 持集合 {A,B}（迭代序 [B,A]），T2 持集合 {A,B,C}（迭代序 [A,C,B]）：T1 先锁 B 再申请 A；T2 先锁 A、再锁 C、再申请 B —— T1 持 B 等 A、T2 持 A 等 B，**成环死锁**。`LocalResourceLockState.latch.await(waitTime)`（`LocalResourceLockManager.tryLockWithLease`:178）带超时，T1 会在 waitTime 后超时退出，死锁在 waitTime（默认 1s）后解体为获取失败——故非永久挂死，但多锁语义在争用下必然出现「本可成功却失败」的活锁式退化，且 `ResourceMultiLock.tryLockWithLease` 失败路径 `unlock()` 逆序释放已得锁（:122-125），行为正确。若替换为无超时实现（`IResourceLockManager` 其他实现，如 `SysDaoResourceLockManager` DB 锁），死锁持续时间不可控。
- **现状**: 排序结果被 `Set.of` 静默丢弃；注释与 `LockHelper.sortResourceIds` 的存在均表明排序是刻意的死锁预防措施。
- **风险**: 多资源顺序锁定的业务（记录组锁定等）在并发下出现周期性获取失败/超时锁退化；换用无超时锁管理器时升级为长死锁。
- **建议**: 用 `new TreeSet<>(...)` 或直接保存排序数组/List 承载遍历顺序（`Set.of` 仅用于去重时会破坏顺序，需 `LinkedHashSet`+排序或 `TreeSet`）；补「两个重叠集合并发 tryLock」回归测试断言无死锁（限时 join）。
- **失效面枚举**: ① `ResourceMultiLock` 构造器+tryLockWithLease——本条主体；② `ResourceLock`（单资源）——无顺序问题 ✅；③ `LocalResourceLockManager.tryLockWithLease` 单资源路径——CHM putIfAbsent 原子，无序问题 ✅；④ 生产调用方扫描：`getMultiLock`（IResourceLockManager:27-28 default 工厂）当前无生产调用（仅接口 + 实现类），潜伏；⑤ `SysDaoResourceLockManager`（nop-sys）实现同接口，若其 multi-lock 路径复用 ResourceMultiLock 则同样受影响（当前未见 MultiLock 构造调用）。
- **信心水平**: 确定（SetN 迭代序规范行为；时序具体）；严重度受「无生产调用方」压制
- **误报排除**: 非「锁原语使用误报」：这是防死锁设计被数据结构选择破坏的复合缺陷；非低频路径豁免——该类存在的唯一目的就是多锁顺序获取。
- **复核状态**: 保留 P2（潜伏）；新发现；:35 排序结果交 Set.of、:105 无序遍历逐行亲核，LockHelper.sortResourceIds:22-28 排序属实，SetN 迭代序非排序序属实，LocalResourceLockManager:178 `latch.await(waitTime-usedTime)` 带超时（死锁在 waitTime 后解体为获取失败）亲核属实；getMultiLock/new ResourceMultiLock 全仓零调用核实，潜伏定级维持

### [nop-kernel-concurrency-C1-008] ResourceTenantManager.isSupportTenant：非同步惰性初始化两个 Set 字段（理论风险，未具体化）

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/tenant/ResourceTenantManager.java:56-57, 100-111`
- **证据片段**:
  ```java
  private Set<String> enabledTenantPaths;      // 非 volatile、无锁
  private Set<String> disabledTenantPaths;
  public boolean isSupportTenant(String resourcePath) {
      if (disabledTenantPaths == null) {                 // (a) check
          disabledTenantPaths = CFG_TENANT_RESOURCE_DISABLED_PATHS.get();  // (b) act
          ...
      }
      if (enabledTenantPaths == null) { ... }            // 同型
      for (String enabledPath : enabledTenantPaths) { ... }  // (c) 遍历
  ```
- **严重程度**: P2
- **并发场景**: 多个请求线程首次并发加载模型（`ModelLoader.loadObjectFromPath` → `ResourceTenantManager.supportTenant` 为每模型加载必经热路径）。
- **触发时序**: T1、T2 同时通过 (a) 的 null 检查 → 各自执行 (b) 重复初始化（两个线程各持一份等值 Set，后写覆盖先写——此半段良性）。理论残留：T1 在 (c) 遍历时，T2 的引用写入对 T1 无 happens-before；若配置返回的 Set 内部含非 final 可变结构（如裸 HashSet），T1 可能见到部分构造的内部表 → NPE/异常遍历。经核：`CFG_TENANT_RESOURCE_DISABLED_PATHS.get()` 产出的集合最终来源于配置反序列化/不可变包装链，final 字段冻结语义大概率阻断该后果——**故危害链无法完全具体化，按规约降 P2 标注理论风险**。
- **现状**: 写一次后不再变更；无 volatile/synchronized/computeIfAbsent 任一防护；同类惰性初始化在 `GlobalStatManager.getSqlStats`（synchronized ✅）与 `ResourceComponentManager.makeModelCache`（CHM computeIfAbsent ✅）均有正确先例。
- **风险**: 以平台设施判级口径，热路径 check-then-act 未自证线程安全；实际后果上限为重复初始化（良性）与低概率遍历异常（取决于配置集实现细节）。
- **建议**: 字段改 `private volatile Set<String>`（最简，写一次读多）；或启动期在 discover/init 阶段显式初始化消除惰性；对齐 GlobalStatManager 的 synchronized 先例亦可。
- **失效面枚举**: ① `disabledTenantPaths` check-act-遍历——本条主体；② `enabledTenantPaths` 同型——同族，一并修复；③ `s_initializingTenant`（ThreadLocal）——线程封闭 ✅；④ 同文件 `_instance`——volatile ✅；⑤ 兄弟惰性初始化 `GlobalStatManager.getSqlStats`/`getRpcClientStats`/`getRpcServerStats`——synchronized ✅；`ResourceComponentManager.makeModelCache`——computeIfAbsent ✅；`ReflectionManager.initCache`——构造器内执行 ✅。全族仅本处失守。
- **信心水平**: 竞态存在=确定；危害链=有趣的猜测（按理论风险 P2 处理）
- **误报排除**: 未按「无同步即报」处理：已推演重复初始化良性、并核查了配置集合的 final 链缓解；仅以「设施未自证 + 理论残留」立案，符合盲审降档规约。
- **复核状态**: 降级 P2→P3；新发现；check-then-act 形态属实（:56-57/:100-111 亲核），但危害链复核确证不可具体化：两 Set 来源（Collections.emptySet/singleton 或 CFG_*.get() 预构建对象）要么每线程新建（无跨线程共享即无 unsafe publication 面）要么早已安全发布（重复初始化良性覆盖）——缺陷收敛为「写一次读多字段缺 volatile」规范化欠账，与 C1-012（P3）同带对齐；原报告自评「危害链=有趣的猜测」成立

### [nop-kernel-concurrency-C1-009] 非 volatile 可替换单例槽位族（_instance + registerInstance）：跨线程重注册无可见性保证；ErrorMessageManager 构造器 this-escape

- **文件**: 族代表：`nop-kernel/nop-core/src/main/java/io/nop/core/module/ModuleManager.java:43-50`、`nop-kernel/nop-core/src/main/java/io/nop/core/exceptions/ErrorMessageManager.java:42-45, 172-174`、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/VirtualFileSystem.java:18-35`、`nop-kernel/nop-core/src/main/java/io/nop/core/dict/DictProvider.java:35-45`、`nop-kernel/nop-core/src/main/java/io/nop/core/lang/xml/XPathProvider.java:18-29`
- **证据片段**:
  ```java
  private static ModuleManager _instance = new ModuleManager();   // 非 final 非 volatile
  public static void registerInstance(ModuleManager instance) { _instance = instance; }
  ...
  // ErrorMessageManager 构造器：
  public ErrorMessageManager(){ NopException.registerErrorMessageManager(this); }  // this-escape
  ```
- **严重程度**: P2
- **并发场景**: 启动/测试引导线程调用 `registerInstance`（Nop 常态：IoC 装配、单测 runner、Quarkus/CLI 引导各换实例），工作线程调用 `instance()`；以及测试/热交换场景中 unregister-then-register 与并发读交叠。
- **触发时序**: T1（引导）执行 `_instance = newInstance`（plain store）；T2（工作线程）读 `_instance`：无 volatile → JMM 不保证 T2 见到新引用，可**持续读到旧实例**（旧实例若已 destroy/VFS 已 unregister，则为「已销毁实例继续被使用」）；`VirtualFileSystem` 更敏感：`unregisterInstance` 先 `DestroyHelper.safeDestroy(_instance)` 再置 null/换新，工作线程可看到「已 destroy 的旧 fs」。`XPathProvider._instance` 初值 null，instance() 依赖非空检查抛错——工作线程在重注册窗口可 transiently 抛 `ERR_XML_NO_XPATH_PROVIDER`。
- **现状**: 同仓已有正确先例（`ResourceComponentManager`/`ResourceTenantManager`/`JsonTool`/`I18nMessageManager` 均 `static volatile _instance`）；本族为漏修兄弟（F3）。实际触发依赖「registerInstance 与并发 instance() 交叠」——常规启动用「容器初始化完成 → 提交工作」的 HB 兜住，故按理论风险 P2；测试/动态替换场景（Nop 单测常驻 JVM 逐用例重注册）则窗口真实存在。
- **风险**: 重注册后请求线程短期内使用旧/已销毁实例（VFS、DictProvider、ErrorMessageManager 等），表现为偶发的初始化错误或旧配置残留；难以复现、随并发布局变化。
- **建议**: 族内全部 `static volatile` 化（对齐 ResourceComponentManager 先例，机械改动）；`ErrorMessageManager` 构造器 this-escape 改为静态工厂内注册或惰性注册；`VirtualFileSystem.unregisterInstance` 顺序改为「先摘引用再销毁」。
- **失效面枚举**: 非 volatile 可替换槽全清单（grep `static [A-Za-z].* _instance` 全量核对）：① `ModuleManager`（plain）；② `ErrorMessageManager`（plain + 构造器 this-escape）；③ `DictProvider`（plain，另含 staticDicts CHM ✅）；④ `XPathProvider`（plain + null 初值）；⑤ `VirtualFileSystem`（plain + unregister 先销毁后置空）；⑥ `BeanTool`（plain）；⑦ `SchemaBasedValidator`（plain）；⑧ `JsonWhitelistChecker`（plain）；⑨ `XSchemaToJsonSchema`（plain）；⑩ `RegisterModelDiscovery`（plain）；⑪ `VarCollector`（plain，unittest 包）；⑫ `SyncThreadPoolExecutor.INSTANCE`（public 非 final 可变静态）；⑬ `XPathProvider.cache` 外其余字段不涉。对照已安全组（volatile）：ResourceComponentManager、ResourceTenantManager、JsonTool、I18nMessageManager；(final)：ReflectionManager、EvalGlobalRegistry、StdDomainRegistry、ScriptCompilerRegistry、EvalBackendRegistry、XPathOperatorRegistry、GlobalCacheRegistry、ResourceContentCache、CodeLangMap、GlobalStatManager、TreeTableLayout、JsonCleaner、DeltaJsonLoader、JsonMerger。
- **信心水平**: 竞态存在=确定；实际触发链依赖重注册交叠=很可能（常规启动被 HB 兜住）
- **误报排除**: 未按「类初始化安全发布」误判：final/静态初始化的初值发布安全，问题只出在**运行期 registerInstance 替换**路径，该路径在本平台（单测、多 app 引导）真实使用。
- **复核状态**: 保留 P2；新发现；族内 13 处站点逐一亲核（ModuleManager:43、ErrorMessageManager:42+构造器 `NopException.registerErrorMessageManager(this)` this-escape 属实、VirtualFileSystem:18+registerInstance:31-33 先 safeDestroy 后发布（第 2 轮归属修正核实无误）、DictProvider:35、XPathProvider:18 null 初值、BeanTool:37、SchemaBasedValidator:43、JsonWhitelistChecker:14、XSchemaToJsonSchema:26、RegisterModelDiscovery:54、VarCollector:14、SyncThreadPoolExecutor.INSTANCE:16、DnsResolver:17）；registerInstance 触发以测试常驻 JVM 逐用例重注册与动态替换窗口为现实面，且 VirtualFileSystem「销毁先于发布」为确定性错误序，维持 P2 不降 P3；安全组 volatile/final 抽样复核通过（RCM:71、RTM:46、I18nMessageManager:38 实测）；F3 清单订正见维度复核结论 §4

### [nop-kernel-concurrency-C1-010] CycleSynchronizer：旧周期 finish 与新周期 beginCycle 交叠可清掉新周期 processing 状态（潜伏）；持 this 锁执行回调属锁域扩展

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/CycleSynchronizer.java:36-46, 66-102`
- **证据片段**:
  ```java
  public synchronized boolean beginCycle() {
      if (finished) return false;
      if (processing) throw new NopException(ERR_CONCURRENT_CYCLE_ALREADY_BEGIN);
      processing = true;             // (a) 新周期开启
      this.cyclePromise = null;
      return true;
  }
  private CompletableFuture<Boolean> resolvePromise(boolean finished) {
      ...
      synchronized (this) {
          promise = cyclePromise;    // (b) 若已被 beginCycle 置 null 则为 null
          if (promise != null) cyclePromise = null;
          this.finished = finished;
          this.processing = false;   // (c) 无条件清位——可能清掉 (a) 刚立起的新周期
          ...
          for (Runnable callback : callbacks) { callback.run(); }   // (d) 持锁跑任意回调
      }
  ```
- **严重程度**: P2（当前无生产调用方，潜伏；时序具体）
- **并发场景**: 周期 N 的收尾线程调用 `finish()`/`completeCycle()`/`restart()`（三者同入 `resolvePromise`），调度线程随即开始周期 N+1 调用 `beginCycle()`——类的设计目标即跨线程周期协调，两线程跨周期交叠属于合法使用而非误用。
- **触发时序**: T2 执行 (a) `processing=true, cyclePromise=null`（周期 N+1 开启，尚未有等待者）；T1 随后进入 `resolvePromise` 同步块：(b) 读到 cyclePromise==null → (c) 无条件 `processing=false`。周期 N+1 此后的一切 `waitCycleCompleted`（:48-53 `!processing` 分支）立即返回 `completedFuture(finished=true)`，等待者被错误地放行——「等待本周期完成」语义被静默击穿。
- **现状**: `finished`/`processing` 为 volatile 但缺代际（cycle generation）标识，`resolvePromise` 无法区分「自己的周期」与「已开启的新周期」。另 (d) 在持有 this 监视器时执行任意回调：回调若阻塞等待「另一线程的 waitCycleCompleted 返回」，而后者被本监视器阻塞 → 锁域扩展死锁（此半为 C2 形态，随条记录）。
- **风险**: 采用该设施的调度/批处理循环在跨线程交叠期放行未完成周期，属语义违约；当前全仓无调用方，潜伏。
- **建议**: 引入单调 `cycleGeneration`：beginCycle 递增并保存到 promise；resolvePromise 仅当 generation 匹配才清 processing/complete promise（不匹配则忽略旧周期收尾）。回调移出同步块执行（先拷贝列表、清位、退锁后再 run）。
- **失效面枚举**: ① `finish()`/`completeCycle()`/`restart()` 三入口共用的 `resolvePromise`——本条主体；② `beginCycle`/`waitCycleCompleted`——作为受影响读侧；③ 全仓调用方扫描：无生产调用（仅本类），潜伏缺陷；④ 同类先例 `BindScheduledExecutor`/`ExecutorHelper` 的周期控制未用本类（各自实现，不受影响）。
- **信心水平**: 确定（时序在类自身语义内成立）；潜伏性确定（无调用方）
- **误报排除**: 非「volatile 字段误报」：volatile 已保证可见性，缺陷是**跨周期状态复用缺代际隔离**的复合语义缺陷；回调持锁半段如实标注为 C2 相邻形态。
- **复核状态**: 保留 P2（潜伏）；新发现；beginCycle:36-46 与 resolvePromise:74-102 逐行亲核，跨周期无代际清 processing（:83 无条件置 false）与持 this 锁跑回调（:87-89）均属实，交叠时序在类自身语义内成立；全仓 grep 无调用方核实，潜伏定级维持

### [nop-kernel-concurrency-C1-011] LocalFileLock：违反 Lock 接口的线程安全契约（实例字段无同步）+ tryLock(0) 失败路径泄漏已打开句柄

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/lock/LocalFileLock.java:31-37, 45-90, 118-128`
- **证据片段**:
  ```java
  public class LocalFileLock implements Lock {          // Lock 契约要求实现可被多线程共享使用
      private FileLock lock;                              // (a) 实例可变字段，无任何同步
      private RandomAccessFile randomAccessFile = null;
      private FileChannel channel = null;
      ...
      do {
          randomAccessFile = new RandomAccessFile(lockFile, "rw");   // (b) 每轮覆盖
          channel = randomAccessFile.getChannel();
          lock = channel.tryLock();
          if (timeoutMs <= 0) break;                     // (c) lock==null 时直接 break
          ...
  ```
- **严重程度**: P2（Lock 契约违约 + 资源泄漏；当前生产无调用方，测试直连）
- **并发场景一（共享实例）**: 两个线程共享同一 `LocalFileLock` 实例（`Lock` 接口的标准用法，如 `LockHelper.unlockAll` / 任何 `juc.Lock` 消费方）并发 `lock()`：T1 成功获得 FileLock（字段记 f1/c1/r1）；T2 的 `tryLock` 返回 null 进入轮询，(b) 持续用 r2/c2 覆盖实例字段；T1 随后 `unlock()` → `releaseHandles()` 关闭的是 **T2 刚写入的 r2/c2**，而 T1 真正持有的 f1 所在 channel 成孤儿——JVM 内 FileLock 句柄不释放，同进程其他进程视图上锁仍被持有，且 fd 泄漏。字段交叉写还可使 unlock 半途关闭不同对象的组合（lock 与 channel 版本错位）。
- **触发时序二（泄漏）**: `tryLock()`（=tryLock(0,false)）在锁被占用时：(b) 打开 raf+channel → `lock==null` → (c) `break` → 返回 false，**未调用 releaseHandles**，raf/channel 字段保持打开直到 GC 清理（FileChannel 的 Cleaner 是兜底而非确定性释放）。高频轮询场景 fd 增长。
- **现状**: 字段非 volatile 无锁；`Lock` 接口 javadoc 明确要求实现「能够被多线程使用」；当前仅 `TestLocalFileLock` 单测使用（测试不是生产守卫）。
- **风险**: 一旦被多线程共享使用（该类以 `Lock` 接口交付，天然邀请共享用法），出现锁句柄错乱、跨进程锁假死与 fd 泄漏；`tryLock` 轮询路径确定性泄漏 2 个句柄/次。
- **建议**: 将 (a) 组字段收敛为「锁成功会话」局部对象（lock 成功后赋给 volatile `FileLockHandle held`），unlock 仅关闭 held；tryLock 失败/超时/异常路径统一 finally `releaseHandles()`（对齐代码中异常路径已有的 releaseHandles 先例 :87-89）；或者明示「每线程每实例」契约并加运行时 owner 线程守卫（F10 fail-fast）。
- **失效面枚举**: ① `lock()`/`lockInterruptibly()`/`tryLock()`/`tryLock(time,unit)` 四入口共用的 tryLock 循环——本条主体；② `unlock()`/`releaseHandles`:118-128——字段交叉写受影响；③ 异常路径 releaseHandles（:87-89）——已有防护 ✅（说明作者已意识到该模式，(c) 路径是遗漏）；④ 不可中断路径 `catch (InterruptedException e) { if (interruptable) throw e; }`——吞中断未恢复中断位（`lock()` 默认 interruptable=false），取消不可见（F9/F5 弱形态，随条记录）；⑤ 生产调用方：无（仅测试），潜伏。
- **信心水平**: 确定（字段覆盖时序与 (c) 泄漏路径均为代码直读事实）
- **误报排除**: 非「未使用=不报」：public `Lock` 实现的契约违约属设施自证失败；泄漏路径与共享实例路径均具体化到行级。
- **复核状态**: 保留 P2（潜伏）；新发现；字段 :30-32 无同步、tryLock(0) 失败路径 :54-55 break 不调 releaseHandles（raf/channel 留存字段至下次覆盖）、releaseHandles:124-132 逐行亲核，共享实例交叉写时序成立；历史 check/nop-commons.md（commit 22129fa9f7）修复的是「恒 false 返回 + 异常路径清理」，本条两缺陷为该修复遗留面，首报核实；仅 TestLocalFileLock 使用核实

### [nop-kernel-concurrency-C1-012] ResourceLock / ResourceMultiLock 句柄字段可见性与重入守卫非原子（线程封闭假设未声明）

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/lock/impl/ResourceLock.java:21, 47-84`、`ResourceMultiLock.java:28, 57-95, 92-95`
- **证据片段**:
  ```java
  // ResourceLock
  private IResourceLockState lock;                       // (a) plain 字段
  public boolean tryLockWithLease(long waitTime, long leaseTime) {
      this.lock = lockManager.tryLockWithLease(...);     // (b) 无锁写
      return this.lock != null;
  }
  public synchronized void unlock() {                    // (c) 只有 unlock 是 synchronized
      IResourceLockState lock = this.lock; ...
  }
  // ResourceMultiLock
  if (locks != null && !locks.isEmpty())                 // (d) check-then-act 重入守卫，非原子
      throw new NopException(ERR_LOCK_NOT_ALLOW_REENTRANT)...;
  this.locks = new ArrayList<>(resourceIds.size());      // (e) 无锁写
  ```
- **严重程度**: P3
- **并发场景**: 跨线程「A 线程 tryLock、B 线程 unlock/isHoldingLock/tryResetLease」或同一线程对同一锁句柄并发重入调用。
- **触发时序**: T1 执行 (b) 写 `this.lock`（plain store）；T2 执行 (c) 读 `this.lock`——(c) 的 synchronized(this) 与 (b) 的无锁写不构成 happens-before → T2 可读到 null（误判未持锁，unlock 变 no-op → **管理器侧租约悬挂至过期**）或读到旧值。ResourceMultiLock 的 (d)(e)：同一线程对同一 multi-lock 并发调用（或两线程共享句柄）双双通过 (d) 检查 → 各自 (e) 覆盖 `locks` → 先建列表被丢弃且其已获资源锁无人释放（泄漏至租约过期）。
- **现状**: 平台惯例中锁句柄通常线程封闭（try/finally 同线程），该假设成立时无实际危害——但契约未在任何 javadoc/owner doc 声明（已核 `xlang-and-xpl-basics.md` 无相关声明），且 `unlock()` 单独 synchronized 的不对称恰恰暗示作者预期跨线程调用。
- **风险**: 跨线程使用时解锁失效（资源锁定至租约超时，默认 10s）与多锁句柄丢失；属理论风险（依赖跨线程用法的出现）。
- **建议**: `lock`/`locks` 改 volatile（写一次读多次，代价可忽略）；ResourceMultiLock 重入守卫改为 `synchronized` 方法级或 CAS 占位；或在接口 javadoc 显式声明线程封闭契约并加 owner 线程断言。
- **失效面枚举**: ① `ResourceLock.lock` 字段全读点：isHoldingLock:47-52 / tryLockWithLease:64-67 / tryResetLease:70-75 / unlock:78-84——前三个读点均无锁；② `ResourceMultiLock.locks` 读点：isHoldingLock:58-67 / tryResetLease:70-80 / unlock:83-89 / tryLockWithLease:94-97——同型；③ `AbstractResourceLock.defaultWaitTime/defaultLeaseTime`——构造期写后只读 ✅；④ 管理器侧 `LocalResourceLockState`——volatile + latch 锁 ✅（对照证明作者已掌握正确模式，本条为句柄层遗漏）。
- **信心水平**: 很可能（竞态按 JMM 成立；触发依赖跨线程用法这一未声明假设）
- **误报排除**: 非「低频路径粗锁」类误报：报告的是写读无 HB 的可见性缺陷本身；按「写不出生产触发链」如实降 P3 而非虚构 P1。
- **复核状态**: 保留 P3；新发现；ResourceLock:21 plain 字段、:65 无锁写、:78 仅 unlock synchronized 的不对称（写侧 :81 置 null 在锁内、:65 赋值在锁外，写读无 HB）亲核属实；ResourceMultiLock:94-97 重入守卫非原子、:97 无锁写亲核属实；线程封闭契约未在任何 owner doc 声明核实；P3 定级维持

### [nop-kernel-concurrency-C1-013] ICache 族同一接口方法语义分裂：getAndSet 非原子（LocalCache）与 remove(K,V) 引用等值（default）对照 MapCache 的原子/等值实现

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/ICache.java:95-99, 109-116`、`LocalCache.java:199-204`、`MapCache.java:91-93, 107-109`
- **证据片段**:
  ```java
  // ICache default
  default V getAndSet(K key, V value) { V old = getIfPresent(key); put(key, value); return old; }  // (a) 非原子
  default boolean remove(K key, V object) {
      V value = getIfPresent(key);
      if (value == object) { remove(key); return true; }   // (b) 引用等值（==）
      return false;
  }
  // LocalCache.getAndSet —— 继承 (a)（get + put 两步）
  // MapCache.getAndSet —— map.put(key, value)（原子返回旧值）；MapCache.remove(K,V) → map.remove(key, object)（equals 语义）
  ```
- **严重程度**: P3
- **并发场景**: 两线程对同一 key 并发 `getAndSet`（以缓存值做读-改-写，如状态标记、游标）；或调用方以 equals 相等但引用不同的对象调用 `removeIfMatch`。
- **触发时序**: T1 执行 (a) 的 `getIfPresent`（得 old1）；T2 亦得 old1；T1 put(v1)、T2 put(v2) —— 最终值=v2，两线程都返回 old1，T1 的写入被静默覆盖（丢失更新）。对照 MapCache：`map.put` 原子，T1 返回 old、T2 返回 v1——**同一接口方法在两个兄弟实现上返回值序列不同**。(b) 的 `==` 判定：调用方传入反序列化/新建的等值对象时永远 false（删除静默失败），而 MapCache 同名方法 equals 判定成功。
- **现状**: `putIfAbsent`/`computeIfAbsent` 的原子性契约已在 ICache javadoc 显式声明并有正确实现（LocalCache:234-237 Caffeine asMap、MapCache CHM）；`getAndSet`/`remove(K,V)` 未纳入契约，实现各自漂移。当前 grep 无生产 `ICache.getAndSet` 调用方（仅测试中 AtomicBoolean 的同名方法），理论风险。
- **风险**: 调用方按 MapCache 语义编写的代码迁移到 LocalCache（平台主缓存实现）时出现丢失更新与删除失效，属跨实现语义陷阱而非当下故障。
- **建议**: 在 ICache javadoc 为 getAndSet 补「非原子，并发下仅最后一次写入生效」契约声明（最小修）；或将 LocalCache.getAndSet 改为 `cache.asMap().put(key, value)`（Caffeine asMap.put 返回旧值，天然原子，与 putIfAbsent 修复先例 :234-237 同款）；ICache default remove(K,V) 改 equals（对齐 MapCache）并注明。
- **失效面枚举**: ① `ICache.getAndSet` default + LocalCache 继承——非原子；② `MapCache.getAndSet`:107-109——原子（对照组）；③ `EhcacheCache`——未覆写 getAndSet，继承 ICache 锁化 default 的说明见类注释（:18-22，synchronized(this) 于 default computeIfAbsent/putIfAbsent 路径；getAndSet 未锁化，仍为 (a) 非原子）；④ `ICache.remove(K,V)` default `==` 判定；⑤ `MapCache.remove(K,V)`:91-93 equals；⑥ `EhcacheCache.remove(K,V)`:112-114 委托 ehcache equals ✅；⑦ 生产调用方：无 ICache.getAndSet 生产调用（潜伏）。
- **信心水平**: 确定（行为差异可代码直证；触发依赖未来调用方）
- **误报排除**: 未按「default 方法天生弱一致」豁免：同接口其他方法已建立原子性契约先例，本条是契约覆盖不全的 F3 族缺口。
- **复核状态**: 保留 P3；新发现；ICache:95-99/109-116 default、LocalCache:200-204 两步非原子、MapCache:91-93（equals）/107-109（原子 put 返回旧值）、EhcacheCache 未覆写 getAndSet（类注释 :19-20 自认继承 default，且注释称「锁化 default」与 ICache:95-99 实际未锁化不符——注释欠账随条订正）逐一亲核，三实现语义分裂成立；putIfAbsent 原子性契约 javadoc :78-84（历史修复产物）对照核实；全仓无 ICache.getAndSet 生产调用方核实

---

## 维度集外线索（C8 形态，不立案展开）

### [nop-kernel-concurrency-CL-001]（维度集外线索，不立案展开）OverflowBlockingQueue.send 的 DROP_ELDEST 策略在容量为 0 时退化为无限自旋

- **形态归属**: C8 背压/流控（溢出策略边界），非 C1。
- **严重度候选**: P1（配置即可触发：`BatchQueueConfig.queueSize=0` + `DROP_ELDEST` → `new OverflowBlockingQueue<>(0, DROP_ELDEST)` 构造合法 → `send` 中 `while(!offer(item)){poll()}` 永远 offer 失败、poll 恒 null，调用线程永久自旋；构造器无容量下限校验）。
- **一句话证据**: `OverflowBlockingQueue.java:122-127`（`while (!offer(item)) { T t = poll(); ... }`）+ 构造器 :45-47 无 capacity>0 守卫。

### [nop-kernel-concurrency-CL-002]（维度集外线索，不立案展开）SequentialTaskExecutor.execute 忽略 queue.offer 返回值，队列满时任务静默丢失

- **形态归属**: C8 过载行为/静默丢弃（F2「半个成功」邻接），非 C1。
- **严重度候选**: P1/P2（offer 走 `OverflowBlockingQueue.offer`:75-77 直通语义——满即返回 false，**不经过任何 QueueOverflowPolicy**；`SequentialTaskExecutor.java:80-83` `queue.offer(command); onMessageQueued();` 返回值被丢弃且无日志，任务永不执行、调用方无感知。缓解：全仓 grep 无生产调用方，潜伏）。
- **一句话证据**: `SequentialTaskExecutor.java:81`（offer 结果丢弃）对照同类 `send(item)` 才应用溢出策略（`OverflowBlockingQueue.java:115-134`）。

### [nop-kernel-concurrency-CL-003]（维度集外线索，不立案展开）BlockingSourceConsumeTask：消费失败整批静默丢弃、drainTo 中断杀死消费线程不可感知

- **形态归属**: C8 生产-消费边界（失败/过载语义）+ F2/F9，非 C1。
- **严重度候选**: P2（`run()` 中 `consumer.acceptList(items)` 异常仅 LOG.error，无重试/死信/重排队——整批消息丢失；`drainTo` 抛 InterruptedException 时任务线程直接退出循环，该 pool 线程的 future 异常无人消费（`AbstractBatchProcessService.start` submit 后丢弃返回值），消费并发度静默下降不可观测）。
- **一句话证据**: `BlockingSourceConsumeTask.java:63-85`（catch Exception 仅日志；InterruptedException 直接 rethrow 终止 run loop）+ `AbstractBatchProcessService.java:52-54`（submit 返回值弃置）。

---

## 维度外备注（非并发发现，盲审转交用，不计入 C1 总数）

- **JsonWhitelist.add 前缀白名单失效（安全-功能性 bug）**: `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/JsonWhitelist.java:44-48` — `whitelistPrefixes.add(className.substring(className.length() - 1))` 对 `a.b.*` 取到的是最后一个字符 `"*"` 而非前缀 `"a.b."`（应为 `substring(0, length-1)`），导致所有包前缀白名单条目永不匹配（`contains` 的 `startsWith("*")` 恒 false）。方向为「拒真」而非「放行」，无直接安全放权，但白名单机制按现状对前缀条目完全失效。属功能缺陷，转交错误处理/安全维度处置。

---

## 结论与总计

**C1 维度初审发现总计：13 条（P1×1，P2×9，P3×3）+ CL 线索 3 条 + 维度外备注 1 条。**

| ID | 一句话 | 严重度 | 信心 |
|---|---|---|---|
| C1-001 | RoundRobinSupplier.resize 收缩在锁外关闭旧数组尾段，并发 get 可取得已关闭资源（Lettuce 连接池生产在用） | P1 | 确定 |
| C1-002 | StandardThreadPoolExecutor 拒绝重试路径 force 抛 REE 时 submittedCount 永久泄漏，池弹性启发式失真 | P2 | 确定 |
| C1-003 | DefaultScheduledExecutor checkEnabled-then-use 与 destroy 置空竞态 → NPE 替代类型化错误 | P2 | 确定 |
| C1-004 | RateLimitExecutorImpl.debounce 非原子 get/cancel/put + holder[0] 不安全发布 → 重复执行与 NPE/陈旧条目 | P2 | 确定 |
| C1-005 | scheduleWithRandomDelay 取消被在飞迭代吞没 + 句柄后写覆盖 → 幽灵周期任务（潜伏） | P2 | 确定 |
| C1-006 | ReactiveObjectMap 首次调用必 NPE（remove(key,null)）+ 失败结果永久毒化（死代码） | P2 | 确定 |
| C1-007 | ResourceMultiLock 排序获取被 Set.of 无序性破坏 → 跨重叠锁集死锁环（带超时可解体，潜伏） | P2 | 确定 |
| C1-008 | ResourceTenantManager.isSupportTenant 非同步惰性初始化（理论风险，未具体化） | P2 | 理论 |
| C1-009 | 非 volatile 可替换单例槽位族（13 处清单）跨线程重注册无可见性保证 + ErrorMessageManager 构造器 this-escape | P2 | 很可能 |
| C1-010 | CycleSynchronizer 旧周期 finish 与新周期 beginCycle 交叠清掉 processing（潜伏）；持锁跑回调 | P2 | 确定 |
| C1-011 | LocalFileLock 违反 Lock 线程安全契约（共享实例字段交叉写）+ tryLock(0) 失败泄漏句柄 | P2 | 确定 |
| C1-012 | ResourceLock/ResourceMultiLock 句柄字段可见性与重入守卫非原子（线程封闭假设未声明） | P3 | 很可能 |
| C1-013 | ICache.getAndSet/remove(K,V) 语义在 LocalCache/MapCache/EhcacheCache 三实现间分裂（F3 契约缺口） | P3 | 确定 |

正面确认（经推演安全、不立案的 重点载体）：HighWatermarkSemaphore（锁全覆盖+水位信号正确）、LocalResourceLockManager 租约续租/过期互斥、ResourceCacheEntry.getObject 同 key 并发加载去重、ReflectionManager tempLoaders 并发 introspect 去重、XLangSemantics 三 CHM、SequentialTaskExecutor CAS 状态机、LimitedExecutor 信号量双路径释放、throttle 的 compute 原子化。平台设施中「check-then-act 必须自证线程安全」的纪律在多数中心路径（模型缓存、反射、注册表）成立，失守点集中在**生命周期交叠（shutdown/register/resize 与并发使用）**与**潜伏未用设施**两族。

---

## 维度复核结论

> 复核人：C1 维度独立复核子代理（与初审代理相互独立，怀疑姿态复核）。复核基线：分支 `concurrency-audit-remediation`，HEAD 8c13908bfd；历史对照面（cra-index WI 1.1–1.7 台账、`ai-dev/audits/check|check2/nop-commons.md|nosql-cdc.md`、历史修复 commit）已解除盲审。

### 1. 复核方法与覆盖

- **证据复核**：17 条发现的全部 file:line 证据亲自打开核对（nop-commons concurrent/cache/pool、nop-core、nop-xlang src/main），行号漂移按 live code 修正（C1-014 refresh 实际 :70-77；其余条目行号核验一致）；无一条引用不存在的代码（C1-014 建议所引 `AutoCloseZipEntryResource` 存在，位于 `io/nop/core/resource/zip/` 包，仅「同包」表述不准）。
- **判级复核**：P1（C1-001）按「具体触发时序+每步可信」标准逐环亲验后终裁维持（见 §3）；两条 P2 降级 P3（C1-002/C1-008，理由见 §3）；C1-016 保留 P3 但触发时序经 JDK 26 实测后事实修正。
- **调用方核验**：全部「无生产调用方/潜伏」声明亲自 grep 复核——ReactiveObjectMap、scheduleWithRandomDelay、CycleSynchronizer、LocalFileLock（仅测试）、getMultiLock/ResourceMultiLock、SequentialTaskExecutor、GlobalExecutors.unregister：均为零调用 ✅；FileWatcher:70 / ZipFileWatcher:46 / CliWatchZipCommand:128（debounce）、LettuceRedisConnectionProvider（RoundRobinSupplier 唯一生产方）、Jdk21CommonsCoreInitializer:12（GlobalExecutors.register 唯一生产方）逐一证实。
- **JDK 语义实测**（C1-016 判定依据）：本机 JDK 26（Zulu 26.0.1）最小程序实测——`setMaximumPoolSize(max<core)` 抛 IAE（严格）；`setCorePoolSize(core>max)` 不抛（已放宽）。
- **修复现状核实**：`git log b3a9c76bed..HEAD` 对本单元全部涉审文件零改动；历史修复 commit 22129fa9f7（check-findings 批次）与本轮 17 条无一同案修复（其 LocalFileLock/LocalCache.putIfAbsent/throttle/debounce 自清理/RoundRobinSupplier 扩容失败各修复点均为相邻面，不覆盖本轮发现）→ 17 条现状全部为**新发现**（含 3 条「新发现（设施本体口径）」），无「已知未修复/已修复」。
- **F3 完备性抽查**：C1-009 族亲自按 `static [A-Za-z_<>, ]* _instance`（排除 volatile/final）全量重扫 nop-commons/nop-core/nop-xlang src/main——命中 12 站点 + SyncThreadPoolExecutor.INSTANCE（非 `_instance` 命名）= **13 个具体站点**，与两轮清单并集一致，无新增遗漏；第 2 轮清单中的「⑬」为第 1 轮清单残项占位，非独立站点（清单计数订正为 13，非 14）。

### 2. 逐条裁定表

| ID | 裁定 | 现状标注 | 复核依据（一句话） |
|---|---|---|---|
| C1-001 | **保留 P1** | 新发现（设施本体口径） | 快照窗+锁外关闭逐行核实；Lettuce 生产链 + IoC 配置刷新派发链（BeanContainerImpl:359-371）亲核证实运行期可达；触发仅两项条件合取，时序每步可信 |
| C1-002 | **降级 P2→P3** | 新发现（设施本体口径） | 账目缺口属实但仅 isShutdown 时可达、SHUTDOWN 后 execute 不再走 offer——泄漏限于将亡实例监控口径；且「抑制扩容」方向判断反了（虚高促使 offer 返回 false→提前扩容） |
| C1-003 | 保留 P2 | 新发现（设施本体口径） | 六入口+stats() 非原子与 destroy 置空亲核；globalTimer 被 WI 1.4/WI 1.5 使用方全平台共享（跨单元关联） |
| C1-004 | 保留 P2 | 新发现 | 双竞态亲核（重复执行宽窗；holder[0] 无 HB→CHM.remove(key,null) NPE 被吞）；三个生产 debounce 调用方亲核 |
| C1-005 | 保留 P2（潜伏） | 新发现 | 无 isDone 守卫+句柄后写+单次取消通道亲核；对照先例 BindScheduledExecutor:91/96 属实；全仓零调用核实 |
| C1-006 | 保留 P2（死代码） | 新发现 | 首调用 NPE 为 JDK 契约必现、失败毒化无回滚亲核；零调用核实 |
| C1-007 | 保留 P2（潜伏） | 新发现 | 排序→Set.of 丢弃亲核；latch 超时解体亲核；MultiLock 零调用核实 |
| C1-008 | **降级 P2→P3** | 新发现 | 形态属实但危害链确证不可具体化（每线程新建或预构建已发布，重复初始化良性）——收敛为缺 volatile 规范化欠账，与 C1-012 同带 |
| C1-009 | 保留 P2 | 新发现 | 13 站点逐一亲核；VirtualFileSystem 先销毁后发布为确定性错误序 + 测试常驻 JVM 重注册窗口现实，维持 P2；F3 计数订正为 13 |
| C1-010 | 保留 P2（潜伏） | 新发现 | 跨周期无代际清位 + 持锁跑回调亲核；零调用核实 |
| C1-011 | 保留 P2（潜伏） | 新发现 | tryLock(0) break 路径泄漏与共享实例交叉写亲核；历史修复（22129fa9f7）覆盖面外遗留核实 |
| C1-012 | 保留 P3 | 新发现 | 写读无 HB + 重入守卫非原子亲核；契约未声明核实 |
| C1-013 | 保留 P3 | 新发现 | 三实现语义分裂逐一亲核（EhcacheCache 注释「锁化 default」与实际未锁化不符，随条订正） |
| C1-014 | 保留 P2 | 新发现 | 在飞读者窗口亲核（行号修正 :70-77）；DevTool 唯一入口、scanZip 共享句柄亲核；AutoCloseZipEntryResource 存在（zip 包，非 impl 同包） |
| C1-015 | 保留 P3 | 新发现（设施本体口径） | register/unregister 亲核；唯一生产调用方一次性引导（潜伏）；与 WI 1.6 CL-002/WI 1.4 C4 使用面跨单元关联 |
| C1-016 | 保留 P3（时序事实修正） | 新发现 | JDK 26 实测推翻原 T1/T2 交错时序；真实缺陷为单线程收缩必抛 IAE（更简单更确定）；config POJO 并发交叉写残余成立 |
| C1-017 | 保留 P3（事实修正） | 新发现 | 四管理器实例槽位亲核；事实修正：DynCodeGen.java:92/100 存在生产 setTenantResourceProvider 写点（非仅测试） |

### 3. 争议点与裁量说明

1. **C1-001 终裁（P1 维持）**：时序三步（快照读→resize 发布+退锁→锁外 closeNext 尾段关闭→get 解引用）全部在 live code 逐行可信；生产触发链完整（LettuceRedisConnectionProvider 三 get 路径为 redis 每请求热路径；`IConfigRefreshable` 经 IoC `configProvider.subscribeChange→container.refreshConfig→refreshConfigMethod.call0` 派发亲核，运行期改 `connectionPoolSize` 即自动触发 resize）。触发条件合取仅 2 项（运维刷新 × 常态流量，后者近乎恒真），未达「≥3 稀有条件」降档线。后果为借出已关闭连接（请求级失败、借出方无感知），无不可逆损坏——P1 依据是**具体时序+生产热路径+契约静默违约**的组合，而非后果量级；若 7.1 协调方按「运维触发低频」再校准，降档下限为 P2（带「刷新窗内偶发连接关闭错误」标注），不建议低于 P2。
2. **C1-002 降级依据**：`force` 抛 REE 的唯一条件是 `parent==null || parent.isShutdown()`（TaskQueue:56-57/62-63）；正常满载拒绝（REE 来自 RejectHandler）走 force 等待/返回 false/中断三路均已有回冲。故泄漏只在已 shutdown 实例上发生，且 SHUTDOWN 后 `execute` 在 offer 之前即被 TPE 拒绝——`submittedCount` 失真对队列弹性启发式**无活池影响**，仅监控口径。另原报告风险方向有误：`offer` 的 `submittedCount<=poolSize` 分支为「入队」判定（TaskQueue:77），虚高使该分支更易跳过→`return false`→TPE 提前 addWorker（**过扩容**而非「抑制扩容」）。
3. **C1-008 降级依据**：复核确认危害链不可具体化——`CFG_*.get()` 返回对象要么每次新建（各线程私有，无 unsafe publication 面）要么为配置层预构建已发布实例（racy 重读最多拿到旧引用，完全构造）；两线程重复初始化为等值覆盖（良性）。缺陷实质为「写一次读多缺 volatile」的规范欠账，与 C1-012（P3）对齐。
4. **C1-016 时序修正不改判级**：原 T1/T2 交错叙事在 JDK 26 语义下不成立（(a) 不触发）；但同站点存在**更简单的确定性缺陷**（单线程收缩 core>new-max 必抛 IAE，updateThreadPool 无法收缩此类池），缺陷事实更强、修复面不变，P3 维持（触发仍需运行期收缩型 refresh；grow 路径在放宽后的 setCorePoolSize 下自愈）。历史关联：check/nop-commons.md P3「newScheduledExecutor 改写调用方 config」暂缓项（refreshConfig 承重约定）应随本条一并设计。
5. **C1-009 维持 P2（不降 P3）**：registerInstance 运行期写点以 Nop 单测常驻 JVM 逐用例重注册与多 app 引导为现实模式；且族内含两处**确定性错误序/错误结构**（VirtualFileSystem registerInstance 先 safeDestroy 后发布；ErrorMessageManager 构造器 this-escape 使半初始化实例经 NopException 静态槽可达），超出纯 volatile 欠账范畴。

### 4. CL 线索处置（5 条）

| CL | 裁定 | 依据 |
|---|---|---|
| CL-001（DROP_ELDEST 容量 0 无限自旋） | **驳回** | 核心证据「capacity=0 构造合法」不成立：`new OverflowBlockingQueue<>(0, DROP_ELDEST)` 委托 `new ArrayBlockingQueue<>(0)`，JDK 契约 capacity<1 抛 IllegalArgumentException——构造即 fail-fast，非自旋；`while(!offer){poll()}` 无限自旋仅当经四参构造注入「offer 恒败且 poll 恒空」的自定义队列才可达（全仓无此用法）；容量≥1 下并发 poll 瞬时 null 的自旋有界可前进。`BatchQueueConfig.queueSize=0` 的实际形态是启动期 IAE（参数护栏问题，转参数护栏类处置，非本条自旋形态） |
| CL-002（SequentialTaskExecutor.execute 丢弃 offer 返回值） | **确认** | execute:81 返回值丢弃且无日志亲核；OverflowBlockingQueue.offer:75-77 直通语义亲核；与 send（:115-134 走溢出策略）不对称属实；全仓零生产调用方（潜伏）。C8 形态归属正确，独立行动意义成立 |
| CL-003（BlockingSourceConsumeTask 整批静默丢弃 + 中断杀死消费线程不可感知） | **确认** | run:54-57 中断终止消费循环、:66-71 整批仅 LOG.error（无重试/死信/重排队）、AbstractBatchProcessService.start:59 submit future 弃置——三点亲核成立。C8+F2/F9 形态归属正确 |
| CL-017（DefaultScheduledExecutor.setEnabled 运行期无效） | **确认** | enabled 唯一消费点 init:75-77 亲核（全文核实无其他读点）；全仓 setEnabled 调用方为 0（含配置路径）——潜伏契约缺口成立，C6/C7 形态归属正确 |
| CL-018（GlobalExecutors.unregister 移除不销毁） | **确认** | unregister:109-111 仅 remove 亲核；全仓调用方为 0；与 register:104-106 自动 old.destroy() 不对称成立。C6 形态归属正确 |

### 5. 与 WI 1.1–1.7 使用方台账的对照（Phase 6 合并口径）

- **指向本单元设施的使用方条目**：WI 1.4 C4（globalTimer/globalWorker 池占用、C4-008 fallback 挂池）、WI 1.5 C4-001/MA-04（wf/task 全簇调度恒跑 globalTimer）、WI 1.6 C8-001↔nop-job C4-003（BindScheduledExecutor 手工链式再调度链死停摆，平台级统一修复建议）、WI 1.6 CL-002（tcc doScan 实占 globalWorker）。以上均为**使用方站点缺陷**，与本单元 C1 各条（设施本体）零重复；本单元 C1-003/C1-005/C1-015 已在条目备注互引，Phase 6 合并裁定跨单元关联，不重复立项。
- **「旧实例生命周期 vs 在飞读者」统一根因族**（第 2 轮已建议合并修复）：C1-001（RoundRobinSupplier）、C1-014（DefaultVirtualFileSystem）、C1-015（GlobalExecutors）+ C1-003（destroy 置空放大器）——建议 Phase 6 以一个「退役代际/延迟销毁」平台模式统一裁定。
- **历史审计对照**：`ai-dev/audits/check/nop-commons.md`（2026-08 批次，commit 22129fa9f7 修复）与本轮无同案——其 RoundRobinSupplier（扩容失败泄漏）、LocalFileLock（恒 false+异常路径）、LocalCache.putIfAbsent、throttle/debounce（自清理）各修复点均为相邻面；本轮对应发现（C1-001 收缩窗、C1-011 两缺陷、C1-004 非原子、C1-013 语义分裂）为该批次的**遗留邻接面**，现状标注仍为「新发现」。

### 6. 台账计数订正

- 第 1 轮结论「13 条（P1×1，P2×9，P3×3）」计数有误：按条目实际为 **P1×1、P2×10、P3×2**；第 2 轮「合计 17 条：P1×1、P2×10、P3×6」有误：复核前实际 **P1×1、P2×11、P3×5**。
- **复核后终版：17 条 = P1×1、P2×9、P3×7**（C1-002、C1-008 两条 P2→P3）；驳回 0 条；CL 线索 5 条：确认 4、驳回 1（CL-001）。

---

## 最终保留项

| 序 | ID | 终裁严重度 | 一句话（复核后） | 修复归属建议 |
|---|---|---|---|---|
| 1 | C1-001 | **P1** | RoundRobinSupplier.resize 收缩在锁外关闭旧数组尾段，并发 get 经快照取得已关闭资源（Lettuce redis 连接池生产在用，IoC 配置刷新自动触发 resize） | Phase 8；与 C1-014/C1-015/C1-003 合并为「退役代际」统一修复族 |
| 2 | C1-003 | P2 | DefaultScheduledExecutor checkEnabled-then-use 与 destroy 置空竞态→六入口+stats() NPE 替代类型化错误（globalTimer 全平台共享） | Phase 8；同上族（destroy 不置 null + 局部快照） |
| 3 | C1-004 | P2 | RateLimitExecutorImpl.debounce 非原子 get-cancel-schedule-put + holder[0] 不安全发布→重复执行与 NPE/陈旧条目（FileWatcher/ZipFileWatcher/CliWatchZipCommand 在用） | Phase 8；按 throttle compute 先例重写 |
| 4 | C1-005 | P2 | scheduleWithRandomDelay 取消被在飞迭代吞没+句柄后写覆盖→幽灵周期任务（潜伏，无调用方；对齐 BindScheduledExecutor 双守卫） | deferred（带 Why Not Blocking）或随 Phase 8 批量 |
| 5 | C1-006 | P2 | ReactiveObjectMap 首次调用必 NPE（remove(key,null)）+失败结果永久毒化（死代码，public API 自证失败） | Phase 8 修复或删除死类（二选一，删除优先） |
| 6 | C1-007 | P2 | ResourceMultiLock 排序获取被 Set.of 无序性破坏→跨重叠锁集死锁环（latch 超时解体为失败；潜伏，零调用） | deferred（带 Why Not Blocking） |
| 7 | C1-009 | P2 | 非 volatile 可替换单例槽位族 13 处 + VirtualFileSystem registerInstance 先销毁后发布（确定性错误序）+ ErrorMessageManager 构造器 this-escape | Phase 8（机械 volatile 化 + 两处确定性错误序单独修） |
| 8 | C1-010 | P2 | CycleSynchronizer 旧周期 finish 清掉新周期 processing（无代际隔离）+持 this 锁跑回调（潜伏，零调用） | deferred（带 Why Not Blocking） |
| 9 | C1-011 | P2 | LocalFileLock 违反 Lock 共享使用惯例（实例字段交叉写）+ tryLock(0) 失败路径泄漏 raf/channel（潜伏，仅测试） | deferred（带 Why Not Blocking；历史批次遗留邻接面） |
| 10 | C1-014 | P2 | DefaultVirtualFileSystem.refresh/destroy 发布新 store 后关旧 zip 句柄，在飞读者经共享 ZipFile 读→zip file closed（DevTool 热刷新×jar 部署） | Phase 8；同 C1-001 退役代际族 |
| 11 | C1-002 | P3（降） | StandardThreadPoolExecutor force 抛 REE 时 submittedCount 无回冲——仅将亡实例监控口径失真（方向订正：虚高致过扩容而非抑制扩容） | deferred |
| 12 | C1-008 | P3（降） | ResourceTenantManager 两 Set 惰性初始化缺 volatile（危害链不可具体化，重复初始化良性） | deferred（机械 volatile 化可随 C1-009 同 diff） |
| 13 | C1-012 | P3 | ResourceLock/ResourceMultiLock 句柄字段可见性与重入守卫非原子（线程封闭契约未声明） | deferred（volatile 化可随 C1-009 同 diff） |
| 14 | C1-013 | P3 | ICache.getAndSet/remove(K,V) 语义三实现分裂（LocalCache 非原子/`==` vs MapCache 原子/equals；契约缺口） | 最小修：javadoc 补契约 + LocalCache asMap().put；deferred |
| 15 | C1-015 | P3 | GlobalExecutors.register put 后锁外 destroy 旧实例→持旧引用在飞提交方 use-after-destroy（与 C1-003 复合；唯一生产调用方一次性引导） | 同 C1-001 退役代际族 |
| 16 | C1-016 | P3 | updateThreadPool 收缩缺陷：JDK 26 实测修正——单线程收缩「当前 core>新 max」必抛 IAE（无法收缩）；config POJO 并发交叉写残余 | deferred；与 check 批次暂缓项「newScheduledExecutor config 突变」一并设计 |
| 17 | C1-017 | P3 | 实例级协作者槽位族（changeChecker/tenantResourceProvider/tenantModuleDiscovery/errorMessageBuilder/varResolver）plain 重注入无可见性保证（DynCodeGen 生产写点已核实） | deferred（volatile 化可随 C1-009 同 diff） |

**CL 台账（不计入 17 条）**：CL-002/003/017/018 确认（移交 C8/C6 对应维度处置）；CL-001 驳回（构造 fail-fast 证伪自旋前提）。

**维度复核终裁：C1 维度 17 条全部保留（15 条原级 + 2 条降级 + C1-016 时序修正），0 条驳回；CL 线索确认 4 驳回 1。建议 C1 维度审计关闭，进入 Phase 6 合并裁定。**
