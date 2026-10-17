# C7 维度：长时运行稳定性 初审报告（盲审轮次）

## 基本信息

- **审计单元**: nop-kernel 并发设施自身——A 面 `nop-kernel/nop-commons/src/main/java/io/nop/commons/{concurrent,cache,pool}`（85 files / 7,550 行）+ B 面 nop-core/nop-xlang 全局模型缓存与注册表单例（基线 `_tmp/cra-baseline-nop-kernel-concurrency.txt` B-face enumeration 节，按「模型/元数据缓存」语义取子集）
- **仓库路径**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **维度定义 C7——长时运行稳定性**: 单调增长源；挂死点（同步等待/锁内 I/O/不产出也不报错）；周期任务退化；时钟敏感性；状态机死区；持续负载退化
- **判级口径**: 平台设施被全平台并发调用，长时运行（天级/月级不重启）是部署常态；设施级缺陷的影响面按"全平台共享"放大评估
- **盲审纪律声明**: 本轮为盲审第 1 轮初审。审计过程中未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅阅读了方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md`、`ai-dev/skills/deep-audit-prompts.md` 与当前 live code。同族已知立案（C3-001 周期异常绝缘、C4-001 globalTimer 死配置、C5-001 XLangSemantics、C6-001/002 destroy 族、C6-006 NioFileWatchService watch 交叉、C6-007 非 daemon 族）按任务预注信息做了避开与 F3 交叉标注，未据此读取任何历史报告。
- **复核状态**: 全部条目为第 1 轮初审产出，`复核状态: 未复核`。

---

## 一、单调增长源清单（A/B 面全量登记，判有界性）

| # | 增长源 | 位置 | 增长驱动 | 判定 |
|---|---|---|---|---|
| M1 | `GlobalExecutors.g_executors` | `concurrent/executor/GlobalExecutors.java:32` | register/unregister（put/remove 配对），池名为静态常量集 | 有界 |
| M2 | `GlobalCacheRegistry.caches` | `cache/GlobalCacheRegistry.java:41` | register/unregister 配对（putIfAbsent+remove(name,cache)） | 有界 |
| M3 | `ContextualizedRegistry.map` | `concurrent/ContextualizedRegistry.java:15` | 基类机制；B 面各注册表子类均为启动期注册+配对注销 | 有界 |
| M4 | `DedupBlockingQueue.map` | `concurrent/impl/DedupBlockingQueue.java:27` | 溢出丢弃路径产生孤儿条目，永不清理 | **有条件无界（C7-006）** |
| M5 | `RateLimitExecutorImpl.promiseMap` | `concurrent/executor/RateLimitExecutorImpl.java:17` | 任务结束 finally 清理；debounce 的 holder[0] 竞态可短暂残留 done 条目，下次同 key 调用自愈 | 有界（自愈） |
| M6 | `HighWatermarkSemaphore.acquireSuccess/FailCount`、`DefaultRateLimiter` 同名计数 | semaphore/ratelimit | AtomicLong 计数器，8 字节恒定 | 有界（溢出尺度百年级） |
| M7 | `SequentialTaskExecutor.totalExecutionTime` | `executor/SequentialTaskExecutor.java:51` | long 累加计数 | 有界 |
| M8 | `ContinuationExecutor` ThreadLocal Continuation | `executor/ContinuationExecutor.java:22` | 池化线程驻留空 ArrayDeque 容器 | 恒定小（P3 以下不立案） |
| M9 | `LocalCache`（Caffeine）条目 | `cache/LocalCache.java:80-136` | maxSize/weakKeys 配置决定；过期条目惰性清理不及时 | 有界但驻留（C7-009，容量面 C5 轮已立案，本条从清理及时性镜头） |
| M10 | `ReflectionManager.invokers` | `reflect/ReflectionManager.java`（B 面） | 强引用 map，按类数增长，上限=类空间 | 实践有界 |
| M11 | `ReflectionManager.reflectClasses/reflectProxies` | 同上 | 仅 `recordForNativeImage=true` 时增长，关开关即 clear | 有条件无界（native-image 构建模式专用，非长驻常态） |
| M12 | `ReflectionManager.tempLoaders` | 同上 | `getFromCache` finally remove | 有界（并发类数） |
| M13 | `ResourceDependsManager.dependencyMap`（每租户一份） | `resource/deps/ResourceDependsManager.java:47` | 仅 `clearDependencies` 全清，无逐条淘汰 | **无界（C7-008）** |
| M14 | `ResourceDependsManager.cache`（"depends-with-cache"） | 同上:45 | per-type-size 默认 10000 有上限 | 有界 |
| M15 | `ResourceComponentManager.modelCaches/modelTypeConfigs/fileTypeLoaders/modelTypeTransformers` | `resource/component/ResourceComponentManager.java:83-89` | 按 modelType/fileType 注册，启动期+配对注销 | 有界 |
| M16 | `ComponentCacheEntry.transformed` | 同上:420 | 按 transform 名（注册的 transformer 键空间） | 有界 |
| M17 | 各 `ResourceLoadingCache`（模型缓存/i18n-cache/depends-with-cache） | cache 族 | per-type-size 默认 10000 | 有界（容量面 C5 轮已立案） |
| M18 | `EvalGlobalRegistry.vars/functions`、`ScriptCompilerRegistry.compilers`、`XPathOperatorRegistry.operators`、`StdDomainRegistry.domainHandlers`、`EvalBackendRegistry.backends`、`ObjPropMapper(Xml)Registry.mappers`、`ValueResolverCompilerRegistry.resolvers` | B 面注册表 | 启动/扩展期注册+配对注销，键空间静态 | 有界 |
| M19 | `ErrorMessageManager.errorCodeMappings/subMappings`、`I18nMessageManager.registeredMessages` | core exceptions/i18n | 配置装载+locale 有界 | 有界 |
| M20 | `GlobalStatManager.sqlStats/rpcClientStats/rpcServerStats` | `core/stat/GlobalStatManager.java:28-47` | `CacheConfig.newConfig(CFG_…_CACHE_MAX_SIZE)` 有上限 | 有界 |
| M21 | `ModuleManager.modules/dynamicModules` | `core/module/ModuleManager.java:45-51` | AtomicReference 整体替换 | 有界 |
| M22 | `NioFileWatchService.watchKeyMap` | `resource/watch/NioFileWatchService.java:32` | 目录注册/cancel 配对 | 有界 |
| M23 | `FileWatcher.queue` | `resource/watch/FileWatcher.java:24` | `LinkedBlockingQueue` 无界，仅消费事件时排空 | **事件风暴下瞬时无界（CL-013 线索）** |
| M24 | `CycleSynchronizer.onCycleCompleted` | `concurrent/CycleSynchronizer.java:25` | 每周期 resolvePromise 置 null | 有界（周期瞬态） |

## 二、挂死点清单（A/B 面同步等待站点全量登记）

| # | 站点 | 等待形式 | 超时 | 可中断 | 判定 |
|---|---|---|---|---|---|
| H1 | `ExecutorHelper.throttleExecute` (`ExecutorHelper.java:243`) | `semaphore.acquireUninterruptibly()` | **无** | **不可中断** | **立案 C7-010**（有生产调用者：nop-web PageProvider/WebPageExporter） |
| H2 | `ExecutorHelper.scheduleWithRandomDelay` 返回 future | future 完成 | **成功路径永不完成** | — | **立案 C7-003** |
| H3 | `LimitedExecutor.execute` (`LimitedExecutor.java:25`) | `semaphore.acquire()` | 无 | 可中断 | 登记：上游线程被下游任务时长反压；不立案（声明语义） |
| H4 | `HighWatermarkSemaphore.tryAcquire` (`HighWatermarkSemaphore.java:51-58`) | `awaitNanos` | 有界于 timeout 参数（调用方可传巨值） | 可中断 | 立案 C7-005（状态机死区放大为永久拒绝） |
| H5 | `LocalResourceLockManager.tryLockWithLease` (`LocalResourceLockManager.java:175-184`) | `latch.await(waitTime-usedTime)` | 有界于 waitTime（默认 1s，可配巨值） | 可中断 | 有界，不立案 |
| H6 | `BlockingSourceConsumeTask.run` → `source.drainTo` (`BlockingSourceConsumeTask.java:53`) | `poll(maxWait)` | 有界（默认 1000ms） | 可中断 | 有界 |
| H7 | `BlockingSourceConsumeTask` → `consumer.acceptList` (`:64`) | 下游处理 | **无** | — | **立案 C7-012**（下游挂死=消费停摆，无看门狗） |
| H8 | `CycleSynchronizer.waitCycleCompleted` 返回的 `cyclePromise` (`CycleSynchronizer.java:48-64`) | future 完成 | **无**，依赖周期推进 | — | **立案 C7-011** |
| H9 | `BindScheduledExecutor.scheduleWithFixedDelay` 手工重排链 (`BindScheduledExecutor.java:94-108`) | 依赖 `command.run()` 完成后才重排 | **无** | — | **立案 C7-001（F3 关联）/挂死即链死** |
| H10 | `OverflowBlockingQueue.send` BLOCK_WAIT (`OverflowBlockingQueue.java:116-117`) | `queue.put()` | 无（默认策略） | 可中断 | 登记：BLOCK_WAIT 为声明语义；满队时生产者全体停摆在 C8 范围 |
| H11 | `IRateLimiter.acquire()` → `tryAcquire(1, Long.MAX_VALUE)` (`ratelimit/IRateLimiter.java:33-38`) | 令牌等待 | 速率数学上界 | 可中断 | 有界（速率倒数尺度），不立案 |
| H12 | `LocalFileLock.lock` (`lock/LocalFileLock.java:45-70`) | 轮询 sleep | 有界（timeoutMs, nanoTime 计） | lock() 模式吞中断（声明语义） | 有界，不立案 |
| H13 | `NioFileWatchService.checkChange` → `watchService.take()` (`NioFileWatchService.java:61`) | `take()` 无超时 | 无（stop() 经 shutdownNow 解除） | 可中断 | 登记：专用单线程，stop 可解除；非 daemon 面 C6-007 已立案族 |
| H14 | `ResourceCacheEntry.getObject` synchronized 加载 (`ResourceCacheEntry.java:179-202`) | 锁内 loader I/O | 无——loader 挂死则同 entry 全体请求挂起 | — | 登记：同 key 串行加载为防击穿设计；loader 挂死放大为该模型全部访问挂起（与 C7-007 同点位，不另立案） |

## 三、时钟敏感性站点清单（currentTimeMillis vs nanoTime）

| 站点 | 时钟源 | 回拨/前跳敏感性 |
|---|---|---|
| JDK `ScheduledThreadPoolExecutor`（DefaultScheduledExecutor 全部 schedule 入口） | nanoTime（JDK 内部） | 免疫 ✔ |
| Guava `RateLimiter`（DefaultRateLimiter） | nanoTime（SleepingStopwatch） | 免疫 ✔；长时间空闲突发上限 = maxBurstSeconds(1s)×速率，设计内有界 |
| `IBlockingSource.drainTo`/`SequentialTaskExecutor` 配额 | `CoreMetrics.nanoTime` | 免疫 ✔ |
| `LocalFileLock` 超时 | `CoreMetrics.nanoTime`/`isExpiredNanos` | 免疫 ✔ |
| `LocalResourceLockManager` 全部租约语义 | `CoreMetrics.currentTimeMillis` | **敏感 ✗（C7-004）** |
| `ResourceCacheEntry.isRefreshEnabled`/`lastLoadTime` | `CoreMetrics.currentTimeMillis` | 敏感 ✗（回拨使 refresh-min-interval 节流窗口失真，量级=毫秒秒级，次要，并入 C7-004 F3） |
| `ResourceDependencySet.lastModified` vs 文件 mtime | 墙钟语义 | 回拨→mtime 变小→误判 changed→多 reload 一次（自愈型）；前跳无影响（并入 C7-004 F3） |
| `StandardThreadPoolExecutor.stopCurrentThreadIfNeeded` 线程更新 | `System.currentTimeMillis` | 敏感 ✗（回拨延迟线程更新一拍，自愈型，C7-013） |
| `HighWatermarkSemaphore`/`CycleSynchronizer` | 无时钟依赖 | 不适用 |

---

## 发现条目

### [nop-kernel-concurrency-C7-001] BindScheduledExecutor.scheduleAtFixedRate 任务耗时超间隔时并发重叠执行+积压单调增长+队列满后周期任务静默永久死亡

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/BindScheduledExecutor.java:64-69`
- **证据片段**:
  ```java
  @Override
  public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
      return timer.scheduleAtFixedRate(() -> {
          executor.execute(command);
      }, initialDelay, period, unit);
  }
  ```
- **严重程度**: P1
- **并发场景**: globalTimer（DefaultScheduledExecutor，corePoolSize=4）调度线程 × 绑定业务 executor 的工作线程
- **触发时序**: T1=timer 周期线程按 fixed-rate 每 period 触发一次，仅做 `executor.execute(command)` 入队（timer 侧任务本身耗时≈0）；T2=业务 executor 线程执行 command 耗时 > period。交叠后果分三段：(1) timer 在 command 尚未跑完时再次入队 → **同一任务两个实例在 executor 上并发执行**，打破 JDK `scheduleAtFixedRate` "不会并发执行" 的契约，非幂等/非重入的周期任务状态损坏；(2) 任务持续超时时入队速率(1/period) > 消费速率(1/taskTime)，绑定 executor 队列积压按 (taskTime-period)/taskTime 速率单调增长——默认 queueCapacity=10000、taskTime=2s/period=1s 时约 5.6 小时填满；(3) 队列满后 `executor.execute` 抛 `RejectedExecutionException`，异常发生在 timer 线程的 JDK periodic task 内 → 按 JDK 语义该周期任务被**静默取消**（BindScheduledExecutor 的 LOG 不在任何路径上），周期功能永久停止且无任何日志。
- **现状**: scheduleAtFixedRate 在 Bind 层退化为"定时入队"，既无重叠互斥，也无积压治理，更无拒绝路径的异常绝缘（C3-001 立案的是周期任务异常后调度停止的绝缘缺失家族，本条是 Bind 层特有的"任务耗时>间隔"节律退化面，为未立案面）
- **风险**: 平台设施被全平台使用；长时运行下下游一旦变慢（GC 停顿、锁等待、下游服务退化），周期任务从"重叠→积压→静默死亡"三段式劣化，"不产出也不报错"，只能靠重启恢复
- **建议**: (1) scheduleAtFixedRate 重排为 BindScheduledExecutor.schedule 同款手工链（仿照 :88-116 的 schedule），用一个 `AtomicBoolean running` 跳过上一轮未完成任务，保持"不重叠"契约并使节律退化为"跳过"而非积压；(2) execute 拒绝时 `future.completeExceptionally` 并 LOG.error，消除静默死亡；(3) 或在 Javadoc 明确声明该实现与 JDK fixed-rate 语义的偏离点
- **失效面枚举**: 同族周期站点全清单——`DefaultScheduledExecutor.scheduleAtFixedRate:126`（JDK 原生，不重叠但有补偿突发，C7-002 另案）；`BindScheduledExecutor.scheduleWithFixedDelay:72`（手工链，任务挂死即链死，本案 F3 关联）；`ExecutorHelper.scheduleWithRandomDelay:210`（手工链，C7-003）；`LocalResourceLockManager.checkTimeout` 经 `scheduleWithFixedDelay:105`（fixed-delay，异常绝缘 C3-001 族）；`FileWatcher` 经 debounce（非 fixed-rate）。逐一状态：仅 DefaultScheduledExecutor.scheduleAtFixedRate 符合 JDK 不重叠契约，其余 Bind/手工链均有本案或 C7-002/C7-003 所述偏离。
- **信心水平**: 确定（三段行为均可从代码+JDK 语义直接推导）
- **误报排除**: 不是"原语本身使用"误报——缺陷在 Bind 装饰器改变了 fixed-rate 的核心契约（不重叠、失败可见），且测试代码中没有覆盖 task>period 场景的用例；也非有界声明豁免——积压容量是绑定 executor 的隐式属性，调用方无从感知
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核）——**保留发现，降级 P1→P2（条件化）**。三段行为全部核实且关键两段经 JDK 实证测试确认（`_tmp/c7-001-jdk-fixedrate-test/`，JDK 26 运行）：①JDK STPE 非重叠契约仅约束 timer 上的 wrapper lambda（瞬时入队），Bind 层将实际 command 移到绑定 executor 异步执行，两实例并发成立（实测 taskTime=1.5s/period=0.5s 下 maxConcurrent=2）——特别复核指令的疑点裁定：**初审未误读 JDK 语义，「并发重叠」成立**；②积压算术正确（queueCapacity 默认 10000=ThreadPoolConfig:25；10000/0.5≈5.6h）；③队满 REE 沿 wrapper 逸出→JDK 周期任务静默永久取消实测确认（触发停止、无日志、future 异常完成）。**降级依据**：全仓 grep 生产调用方——nop-stream×5、nop-integration-feishu×1 共 7 处 scheduleAtFixedRate 全部直接持有裸 JDK ScheduledExecutorService，经 BindScheduledExecutor/IScheduledExecutor fixedRate 的仓库内生产调用方为零，初审「平台设施被全平台使用」对该入口不成立，按复核口径「当前无生产调用方站点维持条件化降档」处理（与 C7-003/C3-008 同款条件化；公共 API 契约缺陷一旦被调用即确定性三段劣化，修复优先级仍应居前）。联动：WI 1.6 C8-001（「修复须平台级统一」）为使用方同族面；C7-014 为同 shutdown/future 语义族。现状：新发现，已知未修复。

### [nop-kernel-concurrency-C7-002] DefaultScheduledExecutor.scheduleAtFixedRate 错过后补偿突发 + 共享 timer 队头阻塞挤压后续调度

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/DefaultScheduledExecutor.java:126-129`；`concurrent/executor/GlobalExecutors.java:38-49`
- **证据片段**:
  ```java
  @Override
  public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
      checkEnabled();
      return executor.scheduleAtFixedRate(command, initialDelay, period, unit);
  }
  ```
- **严重程度**: P2
- **并发场景**: globalTimer 线程池（corePoolSize=4，`CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE` 默认 5 但被 `ExecutorHelper.newScheduledExecutor:63` `setMaxPoolSize(0)` 覆写为死配置——C4-001 已立案，本条不重复）承载全平台周期任务：LocalResourceLockManager.checkTimeout 锁清理、FileWatcher debounce、各模块 cleanup
- **触发时序**: T1=任务 A 以 fixed-rate period=1s 提交到共享 timer；T2=某次执行因 I/O 阻塞 30s。JDK fixed-rate 语义：阻塞期间不并发执行、不报错；恢复后本应执行的被压缩为**背靠背补发**（补偿突发，30 次积压瞬间连发）。若 timer 内同时存在 4 个此类超时任务（corePoolSize=4），第 5 个周期任务（如锁租约清理 checkTimeout）从"延迟"恶化为"完全不执行"——全平台周期设施被少数慢任务挤压顺延，无任何隔离与告警。
- **现状**: 直接委托 JDK，无超时包裹、无任务时长统计、无积压/延迟可观测指标；Clockwise 时钟源为 JDK 内部 nanoTime（免疫回拨），挤压顺延与时钟无关、纯调度语义问题
- **风险**: 长时运行（天级）下慢任务出现概率趋近必然；补偿突发使周期任务的下游在恢复瞬间承受突发流量（速率放大 = overrun/period 倍）；锁清理等关键自愈任务被饿死时与 C7-004 叠加为锁表永久泄漏
- **建议**: (1) 在 DefaultScheduledExecutor 层对 scheduleAtFixedRate 包装计时守卫（超 period 的执行记 WARN 指标），并为关键清理任务提供 `scheduleWithFixedDelay` 优先指引；(2) 修正 C4-001 的死配置使 globalTimer 池大小真实可配（4→可调），为周期任务提供最小隔离；(3) 暴露 per-task 最近执行延迟指标（FutureHelper 层或 stats()）
- **失效面枚举**: 全部 fixed-rate 站点——DefaultScheduledExecutor:126（本案）；BindScheduledExecutor:65（C7-001）；IScheduledExecutor 接口其余实现仅此二者；fixed-delay 站点（DefaultScheduledExecutor:132、BindScheduledExecutor:72、LocalResourceLockManager:105）为"完成+delay"语义无补偿突发，但同受共享池挤压（本案第 2 段覆盖）
- **信心水平**: 确定
- **误报排除**: 非误报校准中"原语使用"——作为平台唯一共享 timer 的门面，调度退化语义未声明、未隔离、未观测，属于设施自身长时运行缺陷；与 C4-001 的区分：C4-001 是池大小配置死，本案是 scheduleAtFixedRate 的错过补偿/挤压语义，二者根因不同层
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。:126-129 直通委托亲核；补偿突发（JDK time+=period 零延迟连发，文档化+实测语义）与共享 timer 挤压（globalTimer corePoolSize 默认 4=ThreadPoolConfig:18；maxPoolSize 被 ExecutorHelper.newScheduledExecutor:63 覆写死——C4-001 面）均与 live code 一致。现状：新发现，已知未修复。

### [nop-kernel-concurrency-C7-003] ExecutorHelper.scheduleWithRandomDelay 成功路径 future 永不完成；首次异常永久终止周期链；cancel 存在 ghost-chain 竞态

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:210-238`
- **证据片段**:
  ```java
  public static <T> CompletableFuture<T> scheduleWithRandomDelay(
          IScheduledExecutor executor, Runnable task, long initialDelay,
          long minDelay, long maxDelay, TimeUnit timeUnit) {
      CompletableFuture<T> future = new CompletableFuture<>();
      AtomicReference<Future<?>> ref = new AtomicReference<>();
      Callable<Void> command = new Callable<>() {
          @Override
          public Void call() {
              try {
                  task.run();
                  ref.set(executor.schedule(this, MathHelper.random().nextLong(minDelay, maxDelay), timeUnit));
              } catch (Throwable e) {
                  future.completeExceptionally(e);
              }
              return null;
          }
      };
      ...
  }
  ```
- **严重程度**: P2（平台公共 API 当前仓库内无生产调用点；一旦被使用即确定性触发，应升 P1）
- **并发场景**: globalTimer 单线程执行 command；调用方线程消费返回的 future
- **触发时序**: (1) 成功路径——`command.call()` 中只有 `completeExceptionally` 分支，**正常路径 future 永远不 complete**：调用方若按惯例 `future.get()`/`FutureHelper.get(future)` 等待即无限挂死，且无任何超时参数可救（挂死点清单 H2）；(2) 链死——`task.run()` 抛任意异常（含一次性瞬态故障）→ `completeExceptionally` → 无重排 → 周期任务永久停止，仅 future 上留有异常（C3-001 周期异常绝缘家族成员，此处登记为该族在手工重排链上的具体站点）；(3) ghost chain——取消方 `future.exceptionally(ex -> { ref.get().cancel(false); })` 与 timer 线程 `ref.set(新任务)` 交错：T1 timer 完成 task.run() 尚未执行 `ref.set`，T2 取消方读到旧 ref（已完成 future）cancel 无效，随后 T1 `ref.set` 挂上新链 → 取消后链条仍继续运行
- **现状**: 三段缺陷（永不完成、链死、取消竞态）叠加；泛型 `T` 从未被生产
- **风险**: 长时运行下周期性辅助任务（random-delay 常用于错峰清理/巡检）首次遇到瞬态异常即永久消失且无日志（异常仅在 future 上，无人监听即静默）
- **建议**: 成功路径补 `future.complete(null)`（或改签名 `CompletionStage<Void>` 语义化"链存活句柄"）；异常路径 LOG.error 后重排（可配最大连续失败数）；取消竞态改为在 command 内检查 `future.isCancelled()` 后不再重排
- **失效面枚举**: 手工重排周期链全清单——ExecutorHelper.scheduleWithRandomDelay（本案）；BindScheduledExecutor.scheduleWithFixedDelay:72-116（有链死与挂死面，无"永不完成"缺陷——其 future 在任务异常时会 completeExceptionally，正常路径保持未完成但它是"链存活句柄"设计，调用方以 cancel 为目的，未立案）；RateLimitExecutorImpl.throttle/debounce（一次性调度非周期链，promiseMap 有界自愈，M5）
- **信心水平**: 确定
- **误报排除**: 非测试代码；非"future 未消费即无危害"——挂死点 H2 的成立不依赖当前是否有调用者，公共 API 契约缺陷按平台设施口径登记
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2（条件化已标注）**。ExecutorHelper:210-238 亲核：成功路径无 complete、异常 completeExceptionally 不重排（链死）、`future.exceptionally → ref.get().cancel(false)` 与 timer 线程 `ref.set` 的 ghost-chain 竞态全部成立；零生产调用方亲核（仅 IScheduledExecutor:34-36 default 方法引用）。现状：新发现，已知未修复。

### [nop-kernel-concurrency-C7-004] LocalResourceLockManager 全部租约语义建立在墙钟上，NTP 步进即破坏互斥/延迟自愈；与 LocalFileLock 的 nanoTime 纪律不一致

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/lock/impl/LocalResourceLockManager.java:94,118,146,152,169,233,259`
- **证据片段**:
  ```java
  public void checkTimeout() {
      long now = CoreMetrics.currentTimeMillis();       // :118
      ...
      if (lock.getExpireTime() <= now) { removeExpiredLock(lock); }
  }
  ...
  if (expireTime > current) {                            // tryResetLease :240
      state.setExpireTime(current + leaseTime);
  ```
- **严重程度**: P2
- **并发场景**: 持锁线程、等锁线程、globalTimer 上的 checkTimeout 清理线程，三方全部以 `CoreMetrics.currentTimeMillis()`（默认实现=`System.currentTimeMillis()`，见 `nop-api-core/.../time/CoreMetrics.java:21-24`）为租约基准
- **触发时序**: 前跳——T1 持锁者于 t0 获租约 expireTime=t0+10s（默认 defaultLeaseTime）；T2 主机 NTP 步进 +15s（VM 迁移/手工校时/大偏差纠正均可产生）；交叠后果：expireTime 瞬间整体过期，checkTimeout 批量 removeExpiredLock，而原持锁者的临界区仍在执行 → **同一资源双持有，互斥静默破坏**，且 `tryResetLease` 读到 `expireTime>current` 为 false，续租永久失败，持锁者无法自救。回拨——now 倒退使 expireTime 集体"延长"，checkTimeout 不清理，崩溃持锁者的锁滞留时间=回拨量，资源不可用窗口等比拉长。
- **现状**: 同一 lock 包内 `LocalFileLock`（`LocalFileLock.java:47` `CoreMetrics.nanoTime()+isExpiredNanos`）已采用单调时钟纪律，LocalResourceLockManager 未跟进；此外 checkTimeout 自身是 JDK fixed-delay 周期任务，若其抛出 RuntimeException 任务被静默取消（C3-001 家族站点），锁表自愈死亡后 `locks` map 转为纯单调增长（F3 关联）
- **风险**: 资源锁用于平台内互斥（如部署/迁移类操作），时钟步进是生产环境常态事件；互斥破坏的后果（重复执行临界区）不可逆；时间尺度：单次 NTP 步进>10s 即触发，恢复需重启或人工 forceUnlock
- **建议**: 租约到期判定改用 `CoreMetrics.nanoTime()` 基准（对齐 LocalFileLock 与 JDK 调度器），仅展示层保留墙钟；或引入"nanoTime 差值 + 墙钟"双检（nanoTime 判超时、墙钟仅告警）。涉及 `IResourceLock.java:82` 的 expireAt 计算与 `ResourceMultiLock.java:99,116` 的 waitTime 计时同步整改
- **失效面枚举**: 墙钟租约/计时站点全清单——LocalResourceLockManager.newLock:94、checkTimeout:118、tryLockWithLease:146/152/169、tryResetLease:233、isHoldingLock:259（本案）；IResourceLock.java:82（expireAt=millis，同族待整改）；ResourceMultiLock.java:99,116（waitTime 计时，回拨仅影响等待时长，量级小）；ResourceCacheEntry.isRefreshEnabled:104（毫秒节流失真，自愈型）；StandardThreadPoolExecutor:109-110,178（线程更新，C7-013）；LocalFileLock:47（已免疫，无需改）；JDK 调度器/Guava RateLimiter（已免疫，见时钟清单）
- **信心水平**: 确定（触发时序具体；NTP 步进在生产属常见条件）
- **误报排除**: 非"超时计算用墙钟"泛化告警——本案的失效后果是互斥语义破坏（并发正确性级），非单纯超时不准；已核对误报校准表，不属任何豁免项
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。7 站点墙钟（:94/:118/:146/:152/:169/:233/:259）逐一亲核；前跳→expireTime 整体过期+tryResetLease :237 else 分支 removeExpiredLock 使续租永久失败、互斥静默破坏的时序成立；同包 LocalFileLock:46 nanoTime 对照成立（行号 47→46 漂移修正）。整改提示：CL-034（api-core TimeOut 同构）应与本案同批统一为 nanoTime 基准。现状：新发现，已知未修复。

### [nop-kernel-concurrency-C7-005] HighWatermarkSemaphore 滞回状态机死区：overHighWatermark 后无租约/无强制恢复/无 waiter 可观测，一次持证泄漏即永久 100% 拒绝

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/semaphore/HighWatermarkSemaphore.java:19,38-99`
- **证据片段**:
  ```java
  private boolean overHighWatermark = false; // 新增状态标志
  ...
  // 关键修改：如果曾经超过高水位，必须等到低水位以下
  while (overHighWatermark ||
          (usedPermits + permits > highWatermark && usedPermits >= lowWatermark)) {
      if (nanos <= 0) { acquireFailCount.incrementAndGet(); return false; }
      nanos = belowLowWatermark.awaitNanos(nanos);
  }
  ...
  // 关键修改：只有当降到低水位以下时才清除标志并通知
  if (oldUsed >= lowWatermark && usedPermits < lowWatermark) {
      overHighWatermark = false;
      belowLowWatermark.signalAll();
  }
  ```
- **严重程度**: P2
- **并发场景**: 多业务线程 tryAcquire/release 竞争；唯一出标志路径是 release 中 usedPermits 自 >=lowWatermark 跨到 <lowWatermark 的瞬间
- **触发时序**: T1 突发窗口（usedPermits<lowWatermark）批量获证使 usedPermits>highWatermark → overHighWatermark=true；T2 其中任一持证者在业务路径挂死/异常后未 release（F8 清理不对称，调用方缺陷）；交叠后果：usedPermits 永远停在 >=lowWatermark → 标志永不清除 → **所有**后续 tryAcquire（哪怕只申请 1 permit、usedPermits<max）在 timeout 内全部失败/挂起， acquireFailCount 持续增长但无 waiter 数、无 holder 诊断接口，故障表现为下游"全体超时"而设施自身无异常。时间尺度：单次泄漏事件后永久，直至进程重启。
- **现状**: 滞回本身是声明设计（防抖），但降级态缺乏三要素：无租约/超时强制回收、无强制复位 API（`resetStats` 只清计数不清状态）、无挂起队列可观测；状态机存在死区（标志只进不出，除非水位自然回落）
- **风险**: 作为全平台可用的并发门闩设施，一次调用方泄漏被放大为整体服务拒绝且不可诊断；长时运行月级必然遇到至少一次调用方缺陷
- **建议**: (1) 增加"可选租约"模式：获证时记 deadline，由 globalTimer 周期扫描强制回收（对齐 LocalResourceLockManager 的租约自愈模式）；(2) 暴露 `getWaitingThreads()`/`forceReset()` 管理接口并入 stats；(3) 在 Javadoc 声明 overHighWatermark 死区条件，提示调用方必须 try/finally release
- **失效面枚举**: ISemaphore 实现族——HighWatermarkSemaphore（本案，滞回状态机）；DefaultSemaphore（JDK Semaphore 包装，无状态标志，泄漏仅损失容量不死区，无需整改）；LimitedExecutor 的裸 Semaphore（同 DefaultSemaphore 形态）；RateLimitExecutorImpl/ExecutorHelper.throttleExecute 的 Semaphore（泄漏由 H1/C7-010 覆盖）。逐一状态：死区为 HighWatermarkSemaphore 独有。
- **信心水平**: 确定（状态机转移可达性已逐步验证：标志仅在获证超高的突发窗口置位、仅在跨低水位清除；持有态挂死即死区成立）
- **误报排除**: 未按"原语使用"误报处理——缺陷不在 ReentrantLock/Condition 用法，而在设施自身的降级状态机缺少自愈与可观测；也未按 F8 把调用方泄漏当设施缺陷单独立案，本案立案点是"泄漏被滞回机制放大为永久死区"
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。标志置位（:62-63）/清除（:92-94 跨低水位）唯一路径、resetStats 只清计数（:136-139）、无标志读取接口与 waiter 观测均亲核成立；零生产调用（仅测试）亲核确认。与 C7-016 为同一状态机不同漏判面（死区 vs 超卖/可观测），修复同点位合并实施。现状：新发现，已知未修复。

### [nop-kernel-concurrency-C7-006] DedupBlockingQueue 溢出丢弃路径产生孤儿 map 条目：键永久无法投递 + 条目只增不删

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/impl/DedupBlockingQueue.java:39-49,104-110`；`concurrent/impl/OverflowBlockingQueue.java:115-134`
- **证据片段**:
  ```java
  @Override
  public void send(T item) throws InterruptedException {
      K key = keyFunc.apply(item);
      if (map.put(key, item) == null)      // 先写 map
          queue.send(item);                 // 后入队；溢出时条目被丢弃/销毁但 map 不回滚
  }
  ```
- **严重程度**: P2（当前仓库内无生产调用点；长时运行缺陷本体确定）
- **并发场景**: 生产线程（offer/send）× 消费线程（take/poll）× 满队溢出路径（OverflowBlockingQueue 的 DROP_NEWEST/DROP_ELDEST/QUEUE_FULL 策略，其中 DROP 类策略会 `onRemoveItem` 销毁条目）
- **触发时序**: T1 生产者 `send(item)` 将 key→item 写入 map（put 返回 null，判定为新键）；T2 `queue.send(item)` 时队满，DROP_NEWEST 策略 offer 失败 → `onRemoveItem(item)` 销毁条目且**不抛异常**；交叠后果：queue 中从未存在该 key 的条目，但 map 中 key→item(已销毁) 永久驻留。此后 T3 生产者再次 `offer(key, item2)`：`map.put` 返回旧值≠null → 直接 return false，item2 **不入队** → 该 key 的消息永久无法投递，且 map 条目在 item2 上继续滞留。QUEUE_FULL 策略下同样成立：send 抛 ERR_QUEUE_FULL 但 map 已插入且不回滚，调用方重试反而落入"map 命中→不再入队"死区。
- **现状**: map 的清理只挂在 take/poll 成功路径上（`map.remove(key)`），溢出/销毁路径无对应清理；即 F4"无界账本只增不删"在内存结构上的变体
- **风险**: 增长时间尺度 = 溢出事件频率（负载高峰期每小时可累计数百条）；key 基数若为记录 ID 则无界，月级驻留达十万级条目 + 被引用对象的强引用滞留；叠加"永久不可投递"造成静默丢消息
- **建议**: (1) `send`/`offer` 失败路径（含抛错路径）回滚 `map.remove(key, item)`；(2) 或改序为先 `queue.send` 成功后写 map，以 take 侧 map 缺失兜底（需配合 removeProcessor 感知）；(3) 为 DROP_NEWEST/DROP_ELDEST 策略下的 dedup 语义补充文档：溢出时"替换"承诺不成立
- **失效面枚举**: 同族入队站点全清单——DedupBlockingQueue.offer:39-49（有 queue.offer 失败回滚 `map.remove(key,o)`，但**不处理**底层 queue.send 内部 DROP 销毁——经 send 的 DROP_NEWEST 路径 map 同样孤儿）；DedupBlockingQueue.offer(timeout):51-62（同 offer，底层是带超时 offer 无内部丢弃，安全）；DedupBlockingQueue.send:104-110（本案，无任何回滚）；OverflowBlockingQueue.send 各策略（DROP_NEWEST:118-121、DROP_ELDEST:122-127、QUEUE_FULL:128-133 均为孤儿制造源）。逐一状态：send 与"经 send 的 DROP 路径"均缺陷。
- **信心水平**: 确定
- **误报排除**: 非有界声明豁免——IBlockingQueue 契约声明"队列已满按 QueueOverflowPolicy 处理"，未声明"替换后永不投递/条目滞留"；也不是纯 C8 溢出行为——立案主体是溢出之后的单调增长与死区（C7 形态），溢出策略选择本身不在本案展开
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2（条件化）**。send :122-126 无回滚亲核；OverflowBlockingQueue.send 的 DROP_NEWEST :118-121/QUEUE_FULL :128-133 为孤儿制造源、DROP_ELDEST 入队成功无孤儿——初审失效面枚举准确（offer 路径 :49-51/:64-66 回滚完整，因 OverflowBlockingQueue.offer :75-77 裸透传不销毁）；零生产调用亲核确认。现状：新发现，已知未修复。

### [nop-kernel-concurrency-C7-007] 模型缓存陈旧数据面：check-changed 为默认唯一失效链且 mtime 比较存在永久漏检窗口，无 TTL 兜底

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/deps/DefaultResourceChangeChecker.java:22-29`；`resource/deps/ResourceDependsManager.java:197-209`；`resource/cache/ResourceCacheEntry.java:216-225`；`resource/cache/ResourceLoadingCache.java:196-206`
- **证据片段**:
  ```java
  // DefaultResourceChangeChecker
  long current = resource.lastModified();
  boolean changed = lastModified != current;      // 纯 mtime 相等比较

  // ResourceDependsManager.isDependencyChanged
  ResourceChangeCheckResult result = changeChecker.checkChanged(deps.getResourcePath(), deps.getLastModified());
  if (result.isChanged()) return true;
  // 内容没有修改，但是可能文件的修改时间变化了
  deps.setLastModified(result.getLastModified()); // mtime 被涂新，下一次写入若落在同一 mtime tick 内即漏检

  // ResourceCacheEntry.loadObject：load 完成后才记录 mtime
  this.deps = deps;
  this.lastLoadTime = CoreMetrics.currentTimeMillis();
  ```
- **严重程度**: P1
- **并发场景**: 文件写入方（发布/热更新/IDE 保存）× 模型访问线程（checkChanged 默认开启：`CFG_COMPONENT_RESOURCE_CACHE_CHECK_CHANGED` 默认 true，`CoreConfigs.java:86-88`）× 各模型缓存（ResourceComponentManager.modelCaches 全家族）
- **触发时序**: 漏检窗口一（同 tick 双写）——T1 文件在 t 被修改，某次 checkChanged 发现 changed 后 reload 并把 `deps.lastModified` 涂为 t；T2 发布流程在同一 mtime 粒度 tick（ext4/overlayfs 报告 1ms，但网络盘/容器 bind mount/部分 FS 为 1-2s 粒度；合并写入场景常见同 ms 内两连写）内再次写入 → 后续 checkChanged `lastModified == current` 判未变 → **陈旧模型无限期服务**（cacheTimeout 默认 null 无 TTL 兜底，`CoreConfigs.java:98-99`），直到文件再次被触碰或进程重启。漏检窗口二（load 与取 mtime 竞态）——T1 访问线程触发 reload，loader 流式读取旧内容期间；T2 写入方完成整文件替换（mtime=t2）；T1 loader 读完后 `collectDepends` 才调用 `resource.lastModified()` 记下 t2 → 缓存持旧内容+新 mtime → **该次修改永久丢失**（同上无兜底）。
- **现状**: ResourceTimestampCache（原 lastModified 缓存）整体被注释为死代码（`ResourceTimestampCache.java` 全文注释），其配置 `CFG_COMPONENT_RESOURCE_TIMESTAMP_CACHE_SIZE/TIMEOUT` 成为死配置；死代码本身不引入陈旧（checkChanged 每次实时 stat），但说明该链路曾有的时间戳缓存层已被拆除，失效检测 100% 依赖单次 mtime 相等比较
- **风险**: 模型（xbiz/orm/xdsl/xmeta 全家族）是平台语义的载体；陈旧模型=行为级错误（旧 SQL、旧校验、旧权限声明），不可自愈直至下次变更；生产热更新/容器卷挂载（mtime 粒度粗）是常态部署形态，时间尺度=发布窗口内即可触发
- **建议**: (1) reload 时采用"读前读后双 mtime"——load 前取 m1，load 后取 m2，m1≠m2 则立即重载（消除窗口二）；(2) 对 `deps.setLastModified(result.getLastModified())` 改为仅在内容指纹（size+快速 hash 或版本号）确认未变时涂新，或给涂新加宽限期；(3) 给模型缓存补默认 TTL 兜底（启用现成的 `CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT`，哪怕 5 分钟级）作为漏检自愈；(4) 清理 ResourceTimestampCache 死代码与死配置，消除误导
- **失效面枚举**: check-changed 链全站点——DefaultResourceChangeChecker.checkChanged:22-29（mtime 相等，本案主站点）；ResourceDependsManager.isDependencyChanged:197-209（mtime 涂新放大器）；ResourceDependsManager.collectDepends:71-95（load 后取 mtime 的窗口二）；ResourceCacheEntry.isChanged:163-174 / getObject:176-208（消费链，无独立缺陷）；ResourceComponentManager.checkChanged:158-160（委托，无独立缺陷）；TenantAwareDependencyManager/TenantAwareResourceLoadingCache（同链多租户复制，随本案修复自动受益）；ResourceTimestampCache（死代码，建议删除）；EhcacheCache（TTL 归 CacheManager 自管，不在此链）
- **信心水平**: 很可能（窗口二为确定竞态；窗口一的触发依赖 FS mtime 粒度与写入模式，已在常见部署形态下具体化）
- **误报排除**: 未按"理论竞态"降级处理——两个窗口均给出了具体文件级时序与常态化触发条件（发布双写/容器挂载粒度）；也不属"已声明边界"——check-changed 的设计意图就是"文件变化自动失效"（ResourceLoadingCache 类注释原文），漏检即语义违约
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P1**。双窗口亲核成立：窗口二=collectDepends :87-91 在 task.get() 之后 deps.setLastModified(resource.lastModified())，loader 读内容与记 mtime 非原子，写并发即「旧内容+新 mtime」永久固化（ResourceCacheEntry.loadObject :216-225 同链）；窗口一=DefaultResourceChangeChecker :21-22 纯 mtime 相等比较对同 tick 双写天然盲；check-changed 默认 true（CoreConfigs :86-88）、cacheTimeout 默认 null 无 TTL 兜底（:98-99）、ResourceTimestampCache 全文注释死代码均核实。C9-010 叠加关系：4 直接使用方（gateway/data-auth/xpl-tag/dyn-orm）绕 refreshMinInterval 门走同一 isChanged 链，checker 层修复两案同步收敛（C9-010 的每请求 stat 税性能面按其本维度处置）；cra-index WI 1.1-1.7 使用方台账无同位点（首立案）。措辞修正（不改判级）：isDependencyChanged :246 涂新在相等分支为同值 no-op，「涂新放大器」表述不精确——窗口一本体是相等比较对同 tick 双写盲，非涂新引入。终裁详记见文末「维度复核结论」。现状：新发现，已知未修复。

### [nop-kernel-concurrency-C7-008] ResourceDependsManager.dependencyMap 无逐条淘汰：每租户一份、依赖集只增不删（含已淘汰缓存条目的孤儿依赖集）

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/deps/ResourceDependsManager.java:47,68-95,155-167`；`resource/tenant/TenantAwareDependencyManager.java:26-31`
- **证据片段**:
  ```java
  private final Map<String, ResourceDependencySet> dependencyMap = new ConcurrentHashMap<>();
  ...
  @Override
  public void clearDependencies() {
      dependencyMap.clear();          // 唯一的移除路径：全量清空
  }
  ...
  private ResourceDependencySet makeDepends(ResourceDependsStack stack, String path) {
      ...
      dep = dependencyMap.get(path);  // 只读命中
  ```
- **严重程度**: P2
- **并发场景**: 全部加载/编译路径（collectDepends/collectDependsTo/traceDepends）在请求线程上向 dependencyMap merge 条目（ResourceDependsStack.updateDepends）
- **触发时序**: 增长无特定交错，是纯累积语义——(1) 每个被 trace 过的**不同资源路径**插入一条，永不单条移除；(2) `ResourceCacheEntry` 淘汰（LRU/TTL）或 `TenantAwareResourceLoadingCache.tenantCaches` 按 tenant 容器上限（默认 100）淘汰租户后，其 `virtual:` 前缀依赖集与租户内路径集**仍留在**对应 ResourceDependsManager 的 dependencyMap 中成为孤儿；(3) `TenantAwareDependencyManager` 为每租户 new 一个 ResourceDependsManager，各自持有一份无淘汰 dependencyMap
- **现状**: 与 C5 轮容量立案的关系：C5-001（XLangSemantics）与模型缓存容量面是"条目×大小"的堆量问题；本案是**增长速率/时间尺度**镜头——dependencyMap 键空间可随运行时动态扩张（虚拟路径、动态生成模型、逐租户路径），且无 TTL、无 LRU、无单条失效，属于从容量轮口径补遗漏的无淘汰账本（F4 族）
- **风险**: 静态模型路径部署下有界（文件集固定），此时仅内存常驻；动态路径形态（多租户+运行时生成模型/脚本）下，月级驻留估算：每条目 ≈ 路径串+ResourceDependencySet(+dependsMap 传递引用) ≈ 0.5-2KB，10 万动态路径 ≈ 50-200MB/租户池，且强引用阻断其模型对象的回收；同时拖慢 `clearDependencies` 全清
- **建议**: (1) 为 dependencyMap 引入与模型缓存一致的上限治理（替换为 LocalCache 弱值/上限容器，或挂接 clearCache(modelType) 时按前缀清理）；(2) ResourceCacheEntry.clear()/缓存淘汰回调时同步清理其 `virtual:` 依赖集；(3) 最低限度先在 stats/管理端暴露 dependencyMap 估计大小，使增长可观测
- **失效面枚举**: 同族依赖账本——ResourceDependsManager.dependencyMap（本案）；TenantAwareDependencyManager.tenantCaches（有 100 上限，容器有界，但其成员内部 dependencyMap 随本案）；ResourceDependsStack.depMap/depStack（请求/编译周期瞬态，stack 空即随线程清理，有界）；ResourceComponentManager.modelCaches（有界 M15）；GlobalCacheRegistry.caches（有界 M2）
- **信心水平**: 确定（增长语义确定；量级为按结构估算）
- **误报排除**: 非"有界声明存在"豁免——`clearDependencies` 只在显式全清时调用，无自动淘汰声明；也不是 C5 重复立案——本案判级依据是时间尺度（月级累积+不可回收孤儿），并已标注与 C5 的合并口径
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。dependencyMap（实际 :42，初审 :47 行号漂移修正）唯一移除路径 clearDependencies :57-59/close :51-54 全清亲核；TenantAwareDependencyManager 每租户 new ResourceDependsManager（loader :26-28）、容器上限 100 亲核；孤儿依赖集面成立。现状：新发现，已知未修复。

### [nop-kernel-concurrency-C7-009] LocalCache 未配置 Caffeine scheduler/executor：过期条目惰性清理迟滞；async 模式加载落在 ForkJoinPool.commonPool

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCache.java:80-136`
- **证据片段**:
  ```java
  private void buildCache(CacheConfig config, ICacheLoader<K, V> loader) {
      Caffeine builder = Caffeine.newBuilder();
      if (config.getMaximumSize() > 0) { builder.maximumSize(config.getMaximumSize()); }
      ...
      if (config.isAsync()) {
          if (loader != null) {
              AsyncLoadingCache<K, V> cache = builder.buildAsync(createCacheLoader(loader));
  ```
- **严重程度**: P2
- **并发场景**: (a) 全部使用 expireAfterWrite/expireAfterAccess 的 LocalCache（含 B 面各 ResourceLoadingCache 底层）；(b) `config.isAsync()=true` 的 LocalCache 的加载线程
- **触发时序**: (a) Caffeine 未设置 `Scheduler`（builder 无 `.scheduler(...)`）时过期清理是惰性的——依赖对同一 key 的后续 get/put 或写路径的 amortized maintenance；对"写一批后不再访问"的缓存（模型缓存、i18n、dict），**已过期条目无限期驻留**，`destroyOnRemove` 配置的销毁回调同样推迟到条目被实际摘除才执行；`destroy()`/`clear()` 之外无周期性 `cleanUp()` 调度点（全仓库 LocalCache 使用点未发现定时 cleanUp）。时间尺度：条目过期后驻留时长 = 下一次该 key 访问的间隔，上限=进程生命周期。(b) async 缓存加载经 `CaffeineCacheLoader.asyncLoad` 落在 Caffeine 默认 executor = `ForkJoinPool.commonPool()`：阻塞型 loader（DB/远端）在负载上升时占满 commonPool，平台内所有并行流/supplyAsync/CompletableFuture 默认池用户被顺带拖慢——持续负载退化面。
- **现状**: 容量上限面（maximumSize 缺失）已由 C5 轮立案；本案立案点是清理及时性（scheduler 缺失）与加载线程归属（executor 缺失）两个配置缺口
- **风险**: (a) 内存驻留超出业务直觉（"已过期"≠"已释放"），destroyOnRemove 的资源（如需 close 的模型）延迟释放；(b) commonPool 饥饿是全局性退化，故障表象远离根因
- **建议**: (1) `builder.scheduler(Scheduler.systemScheduler())`（JDK9+ 免费及时过期）；(2) `builder.executor(...)` 指向 globalWorker 或专用池，或文档强制 async 模式 loader 必须非阻塞；(3) `GlobalCacheRegistry` 增加周期 cleanUp 巡检（复用 globalTimer，与 C3-001 修复一并做异常绝缘）
- **失效面枚举**: 缓存实现族清理及时性——LocalCache/Caffeine（本案，惰性）；MapCache（裸 ConcurrentHashMap，无过期语义，不适用）；EhcacheCache（TTL/清理归 CacheManager，由持有方配置，不在 A 面责任内）；Async 缺省 executor 面——LocalCache async 分支（本案）；ICacheLoader.asyncLoad/asyncLoadAll/asyncReload:97-190（透传 Caffeine 所给 executor，根因同在 buildCache 未设 executor）
- **信心水平**: 确定（Caffeine 惰性维护与默认 commonPool 均为其文档化行为）
- **误报排除**: 非 C5 重复——已剔除容量上限维度；非"原语使用"误报——缺失的是设施层配置（scheduler/executor），且给出明确可修点
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。buildCache :81-136 全链无 .scheduler()/.executor() 亲核；destroy() 仅一次 cleanUp（:58-60）亲核；async 分支 buildAsync 默认 executor=ForkJoinPool.commonPool 为 Caffeine 文档化行为，阻塞型 loader 占用 commonPool 的全局退化面成立。现状：新发现，已知未修复。

### [nop-kernel-concurrency-C7-010] ExecutorHelper.throttleExecute 使用 acquireUninterruptibly：semaphore 耗尽时调用线程永久挂起且停机中断无法解除

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:240-260`
- **证据片段**:
  ```java
  public static CompletableFuture<Void> throttleExecute(Executor executor, Semaphore semaphore, Runnable task) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      semaphore.acquireUninterruptibly();
      try {
          executor.execute(() -> { ... finally { semaphore.release(); } });
      } catch (Exception e) { ... }
      return future;
  }
  ```
- **严重程度**: P2
- **并发场景**: 调用方线程（生产调用者：`nop-frontend-support/nop-web/.../PageProvider.java`、`WebPageExporter.java` 的页面渲染/导出路径）在提交任务前于调用线程上直接获取信号量
- **触发时序**: T1 并发请求把 semaphore 许可耗尽（n 个在途任务慢）；T2 后续请求线程进入 `acquireUninterruptibly()`；T3 运维执行优雅停机（线程被 interrupt）→ 中断被吞，线程继续等待；若在途任务之一挂死（H7 族），许可永不释放 → 挂起的调用线程**永久阻塞且不可中断**，停机流程卡死在等待工作线程结束
- **现状**: 挂死点清单 H1；等待无超时参数、不可中断，且无 acquire 等待时长指标（Semaphore 侧仅成功/失败计数）
- **风险**: web 渲染路径线程被成批钉死时表现为线程池耗尽；与停机流程互锁拉长发布窗口；时间尺度=下游单次慢化/挂死即触发
- **建议**: 改为 `semaphore.tryAcquire(timeout, unit)`（可中断+有界，超时返回失败 future），或复用平台 `ISemaphore`（HighWatermark/Default 均为可中断语义）；至少允许中断并在 InterruptedException 时不提交任务
- **失效面枚举**: 平台内"调用线程等待许可/资源"站点——ExecutorHelper.throttleExecute:243（本案，不可中断）；LimitedExecutor.execute:25（acquire 可中断但无超时，H3，登记不立案）；LocalResourceLockManager.tryLockWithLease latch.await（有界，H5）；HighWatermarkSemaphore.tryAcquire（有界+可中断，H4）；IPool.acquire（pool 族接口，仓库内无实现无调用，随实现落地时需按有界+可中断验收）
- **信心水平**: 确定
- **误报排除**: 非背压声明豁免——限流等待本身是设计，缺陷仅在"不可中断+无界"的组合；有真实生产调用者，非死代码
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。acquireUninterruptibly :243 亲核（无超时+不可中断）；生产调用者 nop-web 三站点（PageProvider:99/:133、WebPageExporter:120）grep 亲核确认；在途任务挂死→许可不释放→调用线程永久不可中断阻塞链条成立。现状：新发现，已知未修复。

### [nop-kernel-concurrency-C7-011] CycleSynchronizer 状态机死区：周期推进者死亡后 processing 永久为 true，beginCycle 永久抛错、cyclePromise 永不完成

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/CycleSynchronizer.java:36-46,48-64,66-106`
- **证据片段**:
  ```java
  public synchronized boolean beginCycle() {
      if (finished) return false;
      if (processing)
          throw new NopException(ERR_CONCURRENT_CYCLE_ALREADY_BEGIN);
      processing = true;
      this.cyclePromise = null;
      return true;
  }
  ...
  public void restart() {
      resolvePromise(false);   // 唯一的死区解除入口，需人工/外部触发
  }
  ```
- **严重程度**: P2（当前仓库内无生产调用点；作为平台设施契约缺陷登记）
- **并发场景**: 周期推进线程（beginCycle→…→completeCycle/finish）× 等待方线程（waitCycleCompleted 拿 future 后 get/回调）
- **触发时序**: T1 推进者 beginCycle 置 processing=true；T2 推进者在到达 completeCycle/finish 前死亡（未捕获 Error、线程池 shutdownNow 打断、runnable 被 cancel）→ processing 永久 true；交叠后果：此后所有 beginCycle 永久抛 ERR_CONCURRENT_CYCLE_ALREADY_BEGIN（服务不可用），已注册的 `cyclePromise` 永不 complete，等待方按无超时挂死（挂死点 H8）；`onCycleCompleted` 回调在 `resolvePromise` 的 `synchronized(this)` 块内执行，任一回调阻塞/抛异常将阻塞全部同步操作并吞掉剩余回调（异常经 catch 转入 completeExceptionally，但同一批后序回调已丢失）
- **现状**: 提供了 restart() 解除入口但无自动触发；无 processing 超时/看门狗；回调在 monitor 内执行是设计选择但无文档警示
- **风险**: 长时运行月级下推进线程意外死亡概率不可忽略；故障形态为"不产出也不报错"（F5）——异常只在 future 上，无人监听即静默
- **建议**: (1) beginCycle 支持"processing 超时强制回收"或由 globalTimer 对 processing 时长看门；(2) completeCycle/finish 用 finally 语义封装指引（或提供 `runCycle(Runnable)` 模板方法保证配对）；(3) 回调移出 synchronized 块执行（先拷贝快照再跑）
- **失效面枚举**: 状态机单例类，无同族兄弟类；回调持锁执行形态同族——`ResourceCacheEntry.clear():111-124`（synchronized 内 listener.onDestroyed+safeDestroy，同样锁内回调，但其锁粒度为单 entry，风险低，登记不立案）；`resolvePromise` 回调丢失面与 F9（吞异常）交叉，已并入本案
- **信心水平**: 确定
- **误报排除**: 非低频粗锁豁免——问题不是锁粒度而是状态机不可达恢复路径；restart() 的存在说明作者意识到死区，但无自动恢复使其仍是缺陷而非边界声明（Javadoc 无任何声明）
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。beginCycle :40-41 抛 ERR 死区、processing 无超时看门、restart :104-106 唯一强制出口、resolvePromise 锁内回调 :84-90、catch(Exception) :96 的 Error 逃逸面（第 2 轮 F3 扩充亲核成立：Error 跳过 :93-95 promise.complete → 等待方永久挂、剩余回调已先行丢失）均核实；零生产调用亲核确认。现状：新发现，已知未修复。

### [nop-kernel-concurrency-C7-012] BlockingSourceConsumeTask 只捕获 Exception：Error 使消费线程静默永久减员，提交方丢弃 future 无重启无告警

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/batch/BlockingSourceConsumeTask.java:61-75`；`concurrent/batch/AbstractBatchProcessService.java:57-70`
- **证据片段**:
  ```java
  try {
      filterMessages(items);
      if (!items.isEmpty()) {
          consumer.acceptList(items);
      }
  } catch (Exception e) {                    // Error 逃逸
      ...
  } finally {
      processingCount.addAndGet(-size);
  }
  } while (!isCancelled());
  ```
  ```java
  for (int i = 0; i < poolSize; i++) {
      executor.submit(task, null);           // future 被丢弃（AbstractBatchProcessService.start:66-68）
  }
  ```
- **严重程度**: P2
- **并发场景**: 消费线程池（poolSize 个线程跑同一 run 循环）× 生产侧 send 线程（经 OverflowBlockingQueue）
- **触发时序**: T1 消费循环处理批次中抛出 Error（OOM、StackOverflow、NoClassDefFoundError 等长时运行月级必然 encountered 的事件）；T2 Error 穿透 catch(Exception) 终止 run()，该 executor.submit 的 future 以异常完成但**被 start() 丢弃**；交叠后果：消费能力永久减 1，无日志挂钩、无重启、无告警（Error 的 stackTrace 仅存在于被丢弃 future 中）；poolSize=1 时批处理整体停止，而生产侧继续 send——BLOCK_WAIT 策略下生产者线程逐个挂起（H10），系统呈"不产出也不报错"假死。挂死点 H7（consumer.acceptList 下游挂死）同点位叠加。
- **现状**: 任务无自愈/看门狗；消费健康度仅能从 getQueueSize/getProcessingCount 间接推断，无活跃消费线程数指标
- **风险**: 长时运行（月级）Error 事件概率高；故障呈慢性劣化（消费速率阶梯下降）直至完全停摆
- **建议**: (1) run 循环外层捕获 Throwable，Error 记 LOG.error 后退出并在 AbstractBatchProcessService 层重新补充提交消费任务（限次+退避，防 Error 风暴）；(2) start() 保留 future 列表并在 destroy 前检查异常完成；(3) 暴露 activeConsumerCount 指标
- **失效面枚举**: 消费循环族——BlockingSourceConsumeTask.run（本案）；NioFileWatchService.checkChange（独立单线程 watch 循环，InterruptedException 后行为已处理，Error 同样致死但 stop 可重启进程级恢复，且 C6-006/C6-007 族已覆盖 watch 面，本案不重复立案）；FileWatcher.processChangeEvent（周期触发非常驻循环，异常经 debounce future 丢弃，与 C7-003 同形态，登记）
- **信心水平**: 确定
- **误报排除**: 未按"测试专用简化并发模型"豁免——AbstractBatchProcessService 是 @PostConstruct 生产基类；catch(Exception) 是常见习惯写法，缺陷在平台设施作为长驻循环时缺少 Error 兜底与死亡可见性
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2，一处细节修正**：Error 并非完全「无日志」——DefaultThreadPoolExecutor.submit 经 ExecutorHelper.submit（:152-174），其包装 Callable catch(Throwable) 含 LOG.error("nop.executor.execute-fail")（:159/:180），Error 堆栈会落日志后才被丢弃 future/线程死亡吸收；「无重启、无消费能力恢复、无专项告警」主体缺陷不变。行号核实：catch(Exception) 在 :66，start 的 submit 丢弃在 :58-60。另注：本条与 C1-CL-003/C2-CL-006/C3-011 为同站点四登记（dispatch-log:42 已列），Phase 6 合并裁定。现状：新发现，已知未修复。

### [nop-kernel-concurrency-C7-013] StandardThreadPoolExecutor 线程更新基准使用 System.currentTimeMillis，时钟回拨延迟线程更新

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/StandardThreadPoolExecutor.java:104-120,178`
- **证据片段**:
  ```java
  protected void stopCurrentThreadIfNeeded() {
      if (currentThreadShouldBeStopped()) {
          long lastTime = lastTimeThreadKilledItself.longValue();
          if (lastTime + threadRenewalDelay < System.currentTimeMillis()) {
              if (lastTimeThreadKilledItself.compareAndSet(lastTime, System.currentTimeMillis() + 1)) {
                  throw StopPooledThreadException.INSTANCE;
              }
          }
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: 池工作线程 afterExecute 钩子（context reload 后的线程更新机制，tomcat JreMemoryLeakProtection 风格）
- **触发时序**: T1 context 停止记录 lastContextStoppedTime（墙钟）；T2 系统时钟回拨 Δ；交叠后果：`lastTime + threadRenewalDelay < now` 判定被推迟 Δ，旧线程的自觉退出延迟一拍；回拨叠加时 `System.currentTimeMillis()+1` 可能小于 lastTime，CAS 自愈。影响为线程更新延后（内存泄漏防护窗口拉长），非正确性问题
- **现状**: 与 C7-004 同属墙钟敏感性站点，但失效为自愈型（下个自然过期点恢复），量级=回拨幅度
- **风险**: 仅在重度热部署（context 频繁 reload）+ 时钟步进组合下可感；低概率低后果
- **建议**: 线程更新计时改 `System.nanoTime()` 差值（与 NopThread.creationTime 的用途解耦：creationTime 保留墙钟仅作展示）
- **失效面枚举**: 墙钟自愈型计时站点——StandardThreadPoolExecutor:109-110,178（本案）；ResourceCacheEntry.isRefreshEnabled:104（refresh-min-interval 节流失真，自愈型，登记）；ResourceDependencySet.lastModified 对 mtime（回拨→误判 changed→多 reload 一次，自愈型，登记）；C7-004 所列租约站点为非自愈型（已另案）
- **信心水平**: 确定
- **误报排除**: 定级 P3 依据"失效自愈+低后果"；不属误报——clock 维度要求站点逐一登记，本案即为清单中自愈型的代表立案
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3**。:109/:110/:178 System.currentTimeMillis 亲核（初审 :104-120 实为 :106-116，漂移修正）；自愈型低后果判定成立。现状：新发现，已知未修复。

---

## 维度集外线索（C8 形态，不立案展开）

### [nop-kernel-concurrency-CL-012]（维度集外线索，不立案展开）SequentialTaskExecutor.execute 忽略 queue.offer 返回值：满队时任务静默丢弃且绕过 overflow policy/removeProcessor

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/SequentialTaskExecutor.java:79-83`
- **证据片段**:
  ```java
  @Override
  public void execute(Runnable command) {
      queue.offer(command);      // 返回值被忽略；未走 queue.send() 的溢出策略路径
      onMessageQueued();
  }
  ```
- **线索说明**: 队列满时（容量>0 且 policy 非 DROP_ELDEST）offer 返回 false，任务**静默丢失**：无异常、无日志、不触发 `removeProcessor`/`DestroyHelper.safeDestroy` 清理，也不做 BLOCK_WAIT 的背压阻塞。属 C8 背压/溢出行为形态，C7 不展开。建议修复方向：改用 `queue.send(command)`（走 QueueOverflowPolicy 与 removeProcessor），或检查返回值 fail-fast。
- **复核处置（独立复核）**: **确认成立，归并处理**。:80-83 亲核成立——但为跨维度重复位点：与 C1-CL-002/C2-CL-004/C3-008 同点位（C5 轮复核已注明「合计五登记」），其中 C3-008 已立案（P2）并经复核保留。处置：归并至 C3-008 合并裁定，本维度不另立。现状：已知（重复登记）。

### [nop-kernel-concurrency-CL-013]（维度集外线索，不立案展开）FileWatcher 无界事件队列 + debounce 风暴推迟 + 处理任务落在 globalTimer

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/watch/FileWatcher.java:24,50-57,122-140`
- **证据片段**:
  ```java
  private final BlockingQueue<FileChangeEvent> queue = new LinkedBlockingQueue<>();
  ...
  executor.debounce("process", debounce, () -> processChangeEvent(handler));
  ```
- **线索说明**: 事件风暴（git 操作/批量构建）下 LinkedBlockingQueue 无界积压；连续事件间隔 < debounce 时 processChangeEvent 被无限推迟（debounce 语义），处理延迟无上界；且处理经 RateLimitExecutorImpl 落在 **globalTimer** 线程上执行（模型重载等重活阻塞全局定时器——C4-001 阻塞放大面的具体制造者之一）。属 C8 过载积压/降级行为形态（阻塞面归 C4-001），C7 不展开。建议：处理任务迁移出 globalTimer（executeOn 专用池）；debounce 改"最大等待上限"式（首事件后 maxWait 必触发）。
- **复核处置（独立复核）**: **确认成立，合并注明**。三段全部亲核：queue 无界（:31）、debounce 连续事件无限推迟（RateLimitExecutorImpl.debounce :50-67 cancel+重排语义）、处理落 globalTimer（构造点仅 nop-cli CliWatchCommand:90/FileWatcherFactory:33 传 GlobalExecutors.globalTimer()）——补充限定：当前唯一构造方为 nop-cli 开发态 watch 命令，服务端常态部署不经此路径（不改变 CL 定性）。与 C5-004（FileWatcher 无界队列）同位点，注明合并裁定。补充 F3 观察（不改判级）：processChangeEvent :106-124 存在 lost-wakeup 窗口——末次 drainTo 空退出与 finally processing=false 之间到达的事件，其 triggerChange 被 processing=true 短路且 debounce 任务已结束，事件滞留队列直至下一事件到达；建议随 C5-004 修复一并处理。

---

## 审计覆盖说明与局限

- A 面 85 文件中接口/配置/统计类（IAsyncCache、ISyncCache、CacheStats、ThreadPoolStats、PoolStats、QueueOverflowPolicy、AsyncJoinType、IAtomicLong 族、NamedThreadLocal 等）经快速核读无 C7 维度缺陷；Caffeine/Guava/ehcache/JDK 依赖库内部行为按其文档化语义采信。
- 挂死点与单调增长清单中的"仓库内无生产调用点"判定基于全仓 Java 源 grep（ CycleSynchronizer、HighWatermarkSemaphore、DedupBlockingQueue、SequentialTaskExecutor、scheduleWithRandomDelay 均无 nop-commons 之外的生产引用；throttleExecute 有 nop-web 调用者）；不排除经 beans.xml/反射装配的调用方式未被发现。
- C7 判级未出现 P0：未发现"默认用法+常见条件即触发且不可逆"的站点；C7-001/C7-007 为 P1，其余 P2/P3。

---

## 维度复核结论（独立复核轮，2026-10-17）

复核人：C7 维度独立复核子代理（与初审相互独立，怀疑姿态复核）。复核范围：第 1 轮 C7-001~013 + CL-012/013 + M1~M24/H1~H14/时钟 9 组，第 2 轮 C7-014~017 + CL-032/033/034 + 时钟终表 24 组 + 双调度器对照表。方法：17 条发现逐条打开 live code（HEAD 8c13908）亲核证据；对 C7-001 按特别复核指令编写并运行 JDK 语义实证测试；历史对照面（cra-index WI 1.1-1.7 台账、C1~C6/C9 各维度报告、dispatch-log、C5-004/C3-008/C6-006/C9-010 具体条目）按解除盲审后的口径核对使用方/同族面。

### 一、终裁汇总表

| 条目 | 初审判级 | 复核裁级 | 裁定 | 关键复核依据 |
|---|---|---|---|---|
| C7-001 | P1 | **P2（条件化）** | 保留+降级 | 三段行为 JDK 实证成立；但 fixedRate 经 Bind 层生产调用方为零（全仓 7 处 fixedRate 均裸 JDK ScheduledExecutorService），按「无生产调用方条件化降档」口径降级 |
| C7-002 | P2 | P2 | 保留 | 直通委托/补偿突发/共享池挤压亲核一致 |
| C7-003 | P2 | P2（条件化） | 保留 | 三段缺陷亲核一致；零生产调用已条件化 |
| C7-004 | P2 | P2 | 保留 | 7 站点墙钟亲核；互斥破坏时序成立；LocalFileLock nanoTime 对照成立 |
| C7-005 | P2 | P2（条件化） | 保留 | 状态机置位/清除唯一路径亲核；零生产调用 |
| C7-006 | P2 | P2（条件化） | 保留 | 孤儿路径与回滚差异亲核；失效面枚举准确 |
| C7-007 | P1 | **P1** | 保留 | 双窗口亲核成立+无 TTL 兜底+check 链默认开启；后果不可逆；终裁详记见下 |
| C7-008 | P2 | P2 | 保留 | 无单条淘汰亲核（行号 47→42 漂移修正） |
| C7-009 | P2 | P2 | 保留 | 无 scheduler/executor 配置亲核 |
| C7-010 | P2 | P2 | 保留 | 不可中断等待+nop-web 3 生产站点亲核 |
| C7-011 | P2 | P2（条件化） | 保留 | 死区/锁内回调/Error 逃逸面亲核；零生产调用 |
| C7-012 | P2 | P2 | 保留（细节修正） | 「无日志」不精确：ExecutorHelper.submit 包装 catch(Throwable) 有 LOG.error；无重启/无恢复主体不变；与 C1-CL-003/C2-CL-006/C3-011 四登记合并 |
| C7-013 | P3 | P3 | 保留 | 墙钟站点亲核（行号漂移修正） |
| C7-014 | P3 | P3 | 保留 | bindCancel 单向+:107 无兜底亲核；调用面 5 处确认 |
| C7-015 | P2 | P2 | 保留 | H11 修正采纳；Guava 语义+20 分钟生产面亲核 |
| C7-016 | P3 | P3（条件化） | 保留 | 超卖代数+零文档零观测亲核 |
| C7-017 | P3 | P3（条件化） | 保留 | 失败粘滞确定性行为亲核；零生产调用 |
| CL-012 | 线索 | 线索 | 确认+归并 | 与 C1-CL-002/C2-CL-004/C3-008 同点位（五登记），归并 C3-008（已复核保留 P2） |
| CL-013 | 线索 | 线索 | 确认+注明 | 与 C5-004 同位点合并；globalTimer 落点仅 nop-cli 开发态构造；补 lost-wakeup F3 观察 |
| CL-032 | 线索 | 线索 | 确认+注明 | 与 C6-006 同文件不同机制面，建议独立保留同批修复 |
| CL-033 | 线索 | 线索 | 确认 | 竞态亲核；不立案定性维持 |
| CL-034 | 线索 | 线索 | 确认 | 同构预警有效，随 C7-004 整改一并纳入 |

### 二、C7-001 终裁详记（含 JDK 语义核实结果）

特别复核指令疑点「JDK STPE 的 scheduleAtFixedRate 不会并发执行同一任务，初审『并发重叠』是否错误」——**裁定：初审无误，重叠成立**。核实链：(1) `BindScheduledExecutor.scheduleAtFixedRate`（:65-69）是**直接委托**：`timer.scheduleAtFixedRate(() -> executor.execute(command), ...)`——JDK 非重叠契约仅约束 timer 线程上的 wrapper lambda；(2) wrapper 本身耗时≈0（仅入队），JDK 恒以 period 节律放行下一轮 wrapper；(3) command 在绑定 executor（生产组合为 globalWorker，多线程）上异步执行——两实例并发不受 JDK 契约保护。实证测试（`_tmp/c7-001-jdk-fixedrate-test/TestFixedRateSemantics.java`，JDK 26 单文件运行）：TEST1（taskTime=1.5s/period=0.5s）wrapper 正常触发 9 次、command **maxConcurrent=2 并发实例**；TEST2 队满 REE 从 wrapper 逸出后周期任务**静默永久死亡**（后续零触发、无任何日志、future 异常完成）。积压算术复核正确（queueCapacity 默认 10000=ThreadPoolConfig:25；taskTime=2s/period=1s → 10000/0.5≈5.6h 填满）。**降级依据（P1→P2 条件化）**：全仓 grep 生产调用方——nop-stream×5（TaskManager:343/JobCoordinator:693/TaskCheckpointWiring:275/StreamMetricsReporter:80/OpsJobManager:342）、nop-integration-feishu×1（FeishuClient:262）共 7 处 scheduleAtFixedRate 全部直接持有裸 `ScheduledExecutorService`（Executors.newSingleThreadScheduledExecutor），不经 IScheduledExecutor/Bind 层；Bind 层 fixedRate 仓库内零生产调用，初审「平台设施被全平台使用」对该入口不成立。按判级口径「当前无生产调用方站点维持条件化降档」→ P2（公共 API 契约缺陷，一旦被调用即确定性三段劣化，修复排期仍应居前）。修复联动面：WI 1.6 C8-001（「修复须平台级统一」已裁定）、C7-014（同 shutdown/future 语义族）、C7-002（Default 层同入口对照面）。

### 三、C7-007 终裁详记

(1) **窗口一（同 tick 双写）**：DefaultResourceChangeChecker :21-22 纯 `lastModified != current` 相等比较；check-changed 默认 true（CoreConfigs :86-88）；cacheTimeout 默认 null（:98-99）无 TTL 兜底——漏检后陈旧模型无限期服务成立；ResourceTimestampCache 全文注释死代码核实（其 size/timeout 两配置成死配置）。(2) **窗口二（load 后取 mtime 竞态）**：ResourceCacheEntry.loadObject :220 经 collectDependsTo → ResourceDependsManager.collectDepends :87-91 `task.get()` 完成后才 `deps.setLastModified(resource.lastModified())`——loader 读内容与记 mtime 非原子，整文件替换落在两者之间即「旧内容+新 mtime」永久固化。(3) **C9-010 叠加**：4 直接使用方（gateway 每 HTTP 请求/data-auth 每实体查询/xpl-tag 每动态标签/dyn-orm 每模型获取）绕过 refreshMinInterval 门走同一 isChanged 链——本案修复于 checker 层（读前读后双 mtime/内容指纹/默认 TTL）即同步收敛 C9-010 的正确性面（C9-010 的每请求 stat 税性能面按其本维度处置）。(4) **使用方关系**：cra-index WI 1.1-1.7 台账无同位点登记（平台设施本体口径首立案）。(5) **措辞修正（不改判级）**：isDependencyChanged :246 涂新在相等分支为同值 no-op，「mtime 被涂新」非窗口一成因，窗口一本体是相等比较对同 tick 双写天然盲。**P1 维持**：窗口二为每步可信的具体竞态（发布窗口写读交叠即触发）、窗口一在粗粒度 mtime 部署形态（容器 bind mount/网络盘）具体化；后果为不可逆行为级错误且无自愈；判级按设施被全平台并发调用口径（模型缓存为全平台语义载体）。

### 四、CL 处置汇总

- **CL-012**：确认成立，五登记重复位点（C1-CL-002/C2-CL-004/C3-008/C7-CL-012，C5 轮复核注明「合计五登记」），归并 C3-008（已复核保留 P2）合并裁定，本维度不另立。
- **CL-013**：确认成立，与 C5-004 同位点注明合并；补充限定（globalTimer 落点的当前构造方仅 nop-cli 开发态 watch 命令，服务端常态部署不经此路径）；补充 lost-wakeup F3 观察（processChangeEvent 的 processing 标志窗口，事件滞留至下一事件）。
- **CL-032**：确认成立，与 C6-006 同文件不同机制面（生命周期不对称 vs OVERFLOW 事件丢失），修复互不覆盖——建议独立保留、同批修复，归并裁定权在整合者。
- **CL-033**：确认成立，维持不立案登记（FileWatcher handler 幂等）；throttle 用 compute 而 debounce 未用的不对称亲核。
- **CL-034**：确认成立，预警登记有效，随 C7-004 整改统一为 nanoTime 基准。

### 五、清单类（M/H/时钟）复核附注

- 第 2 轮抽验 8/8 M 行、4+1 H 行与 live code 一致，本轮抽样复核无异议。
- 行号漂移修正 3 处：M13/C7-008 dependencyMap :47→:42；C7-004 所引 LocalFileLock :47→:46；C7-013 所引 StandardThreadPoolExecutor :104-120→:106-116。
- M27 建议补注：unwatch 递归 cancel 但不摘除父 entry 的 subEntries 引用（C6-006 面），「配对注销」判定宜对齐 C6-006。
- 时钟终表 24 组中本轮亲核 #3/#4/#7/#8/#17/#23/#24，均与 live code 一致；H1/H2/H8/H9/H11/H14 亲核一致（H11 即 C7-015 修正，已采纳）。

### 六、统计与现状

- **17 条发现**：保留 17、降级 1（C7-001 P1→P2 条件化）、驳回 0。终态判级：**P1×1（C7-007）/ P2×12 / P3×4**。
- **5 条 CL**：确认 5（CL-012 归并、CL-013/CL-032 注明跨维度关系、CL-033/CL-034 维持登记）。
- **现状标注**：17 条均为本轮审计新发现；缺陷本体在 live code（HEAD 8c13908）全部在案未修复（已知未修复）；历史 git 整改（22129fa9f7/dbe5a83ec9 等早期批次）未触及本案任何条目的核心缺陷面。
- **Phase 6 合并裁定输入**：C7-012∈BlockingSourceConsumeTask 四登记（C1-CL-003/C2-CL-006/C3-011）；CL-012∈SequentialTaskExecutor 五登记；CL-013↔C5-004；CL-032↔C6-006（建议独立保留）；C7-001/C7-014 与 C3-001/WI 1.6 C8-001 平台级统一排期。

---

## 最终保留项

按复核终裁保留的立案项（判级以本节为准；「条件化」=当前仓库内无生产调用方，按设施契约缺陷登记，一旦被调用即确定性触发）：

| # | 名称 | 终裁判级 | 裁定依据 | 现状 |
|---|---|---|---|---|
| C7-007 | 模型缓存 check-changed 双漏检窗口+ResourceTimestampCache 死代码 | **P1** | 双窗口亲核成立、无 TTL 兜底、check 链默认开启、后果不可逆（陈旧模型无限期服务）；checker 层修复同步收敛 C9-010 正确性面 | 已知未修复 |
| C7-001 | BindScheduledExecutor.scheduleAtFixedRate 三段劣化（重叠/积压/静默死亡） | **P2（条件化）** | 三段 JDK 实证成立；Bind 层 fixedRate 零生产调用方，条件化降档；公共 API 契约缺陷优先修复 | 已知未修复 |
| C7-002 | DefaultScheduledExecutor fixedRate 补偿突发+共享 timer 挤压 | P2 | JDK 语义+共享池亲核一致 | 已知未修复 |
| C7-003 | scheduleWithRandomDelay future 永不完成/链死/ghost-chain | P2（条件化） | 三段亲核一致；零生产调用 | 已知未修复 |
| C7-004 | LocalResourceLockManager 墙钟租约（互斥破坏/续租失效） | P2 | 7 站点亲核；与 LocalFileLock nanoTime 纪律不一致成立；随批整改 CL-034 | 已知未修复 |
| C7-005 | HighWatermarkSemaphore 滞回死区（无自愈/无观测） | P2（条件化） | 状态机亲核；零生产调用；与 C7-016 同点位修复 | 已知未修复 |
| C7-006 | DedupBlockingQueue send 溢出孤儿条目（死键+滞留） | P2（条件化） | 孤儿路径亲核；零生产调用 | 已知未修复 |
| C7-008 | ResourceDependsManager.dependencyMap 无逐条淘汰 | P2 | 只增不删+每租户一份亲核 | 已知未修复 |
| C7-009 | LocalCache 无 scheduler/executor（清理迟滞+commonPool 加载） | P2 | buildCache 亲核 | 已知未修复 |
| C7-010 | throttleExecute acquireUninterruptibly 不可中断无界等待 | P2 | nop-web 3 生产站点亲核 | 已知未修复 |
| C7-011 | CycleSynchronizer 状态机死区（restart 唯一出口+锁内回调+Error 逃逸） | P2（条件化） | 死区/Error 逃逸亲核；零生产调用 | 已知未修复 |
| C7-012 | BlockingSourceConsumeTask Error 致消费线程永久减员 | P2 | catch(Exception)+future 丢弃亲核；「无日志」细节修正（ExecutorHelper 包装有 LOG.error）；四登记合并裁定 | 已知未修复 |
| C7-013 | StandardThreadPoolExecutor 墙钟线程更新延迟 | P3 | 自愈型低后果 | 已知未修复 |
| C7-014 | 调度器 shutdown 窗口 caller-facing future 永不完成（bindCancel 单向+:107 无兜底） | P3 | 亲核成立；当前无 join-at-stop 调用方；与 C3-001/C8-001 族统一排期 | 已知未修复 |
| C7-015 | IRateLimiter 等待不可中断（H11 修正） | P2 | Guava 语义修正采纳；20 分钟生产面（RateLimitConsumer）亲核 | 已知未修复 |
| C7-016 | HighWatermarkSemaphore 突发窗口合法超卖+零文档零观测 | P3（条件化） | 超卖代数亲核；零生产调用；与 C7-005 同点位修复 | 已知未修复 |
| C7-017 | ReactiveObjectMap 失败粘滞缓存 | P3（条件化） | 确定性行为亲核；零生产调用 | 已知未修复 |

线索保留（不立案）：CL-012（归并 C3-008）、CL-013（合并 C5-004）、CL-032（独立保留，与 C6-006 同批修复）、CL-033（登记）、CL-034（预警登记，随 C7-004 整改）。
