# C7 维度：长时运行稳定性 第 2 轮深挖报告（盲审轮次）

## 基本信息

- **审计单元**: nop-kernel 并发设施自身——A 面 `nop-kernel/nop-commons/src/main/java/io/nop/commons/{concurrent,cache,pool}`（含 concurrent/lock、ratelimit、semaphore、batch 子包）+ B 面 nop-core/nop-xlang 全局模型缓存与注册表单例（同第 1 轮口径）
- **仓库路径**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`，HEAD `8c13908`，2026-10-16 22:32）
- **本轮输入**: 第 1 轮报告 `C7-long-running.md`（发现 C7-001~013、线索 CL-012/013、单调增长源清单 M1~M24、挂死点清单 H1~H14、时钟站点清单 9 项）+ live code 现场核读
- **盲审纪律声明**: 本轮为盲审第 2 轮深挖。除第 1 轮本维度文件 `C7-long-running.md`（任务指定唯一例外输入）外，未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何其他文件。前轮预注立案项（C3-001 周期异常绝缘、C4-001 globalTimer 死配置、C6-006 watch 交叉、C6-007 非 daemon 族等）仅做 F3 互引，未据此读取任何历史报告。全部判定基于本轮对 live code 的直接核读。
- **复核状态**: 全部条目为第 2 轮深挖产出，`复核状态: 未复核`。

## 本轮目标（任务书 ①~⑤ 逐条对应）

| 任务 | 本报告章节 | 结论摘要 |
|---|---|---|
| ① C7-001 失效面扩展（fixedRate 超间隔族负载模式对照） | 一 | 双调度器 × 双负载模式对照表完成；fixedDelay 手工链异常后**节律不可恢复**（C3-001 族节律面，调用方自兜底证据在案）；无超出 C7-001/C7-002 的新 P1 面，模式Ⅰ「零裕度常警」与模式Ⅱ「不可恢复劣化」排序补入 C7-001 F3 |
| ② 时钟敏感性终表（A/B 面全站点） | 二 | 全量 grep 补全至 **24 个站点组**（第 1 轮 9 组 → 终表 24 组）；四个专项（refill/租约/TTL/超时）全部落定；新发现墙钟超时预算站点 `DefaultTaskExecutionQueue.waitForAllTasks`、虚拟 mtime 族 `VirtualResourceDependencySet`/`InMemoryTextResource`、`NopThread.creationTime` 等；**IRateLimiter refill 免疫回拨但等待不可中断 → 立案 C7-015** |
| ③ 单调增长源抽验（8 行）与补登 | 三 | M1/M5/M9/M13/M17/M20/M23/M24 抽验**全部与 live code 一致**（M5 补充两个细化面）；补登 M25/M26/M27；B 面 listener 列表专项搜索为**负结果**（无漏登记） |
| ④ HighWatermarkSemaphore/IRateLimiter 长时深水区 | 四 | HWS 状态机全图完成，漏判面立案 **C7-016**（突发窗口超卖 highWatermark + 零文档零观测）；IRL 状态机完成，漏判面立案 **C7-015**（不可中断）；全仓 grep 证实 HWS **零生产调用**、IRL 有 4 处生产调用面 |
| ⑤ 挂死点抽验（4 行，含 future 永不完成族与 C3 交叉） | 五 | H1/H2/H8/H9 抽验**全部一致**（H14 顺带核读亦一致）；H8 同站点补登 Error 逃逸新面（C7-011 F3 扩充，不另立案）；由 H2 族延伸出调度器族 shutdown 窗口 future 永不完成 → 立案 **C7-014** |

---

## 一、任务①：fixedRate 超间隔族双负载模式对照表

对照对象：`DefaultScheduledExecutor`（JDK `ScheduledThreadPoolExecutor` 直通，C7-002 已立案）vs `BindScheduledExecutor`（timer 定时入队 + 绑定 executor 执行，C7-001 已立案）。负载模式取任务书指定的两种。

### 1.1 对照表

| 维度 | 模式Ⅰ：任务耗时恒定略小于间隔（taskTime = period − ε） | 模式Ⅱ：任务耗时抖动跨间隔（taskTime 围绕 period 振荡） |
|---|---|---|
| **DefaultScheduledExecutor.scheduleAtFixedRate**（JDK 原生） | 每周期准点起跑、跑 period−ε；**不重叠**（JDK 契约）、无积压；每周期仅剩 ε 裕度——**零裕度常警态**。每次执行在 globalTimer 4 线程之一上占住 period−ε 时长（长占型任务越多，C7-002 的共享挤压面放大越快）。节律：稳定，无补偿 | 过冲周期内不重叠但后续触发被压缩，恢复后**背靠背补偿突发**（C7-002 第 1 段）；突发倍数 = overrun/period；过冲任务过载 timer 线程时其他周期任务被顺延乃至饿死（C7-002 第 2 段）。**节律总能自愈**——压缩追赶本身就是恢复机制，最坏退化是突发而非死亡 |
| **BindScheduledExecutor.scheduleAtFixedRate**（定时入队） | 稳态队列深度≈0（入队速率=消费速率−ε/period）；但**重叠风险恒定常警**：绑定 executor 上任何一次 hiccup（GC 停顿、线程池排队、下游慢）使第 n+1 次入队落在第 n 次仍在执行期间 → **同任务两实例并发执行**（C7-001 第 1 段，违反 JDK 不重叠契约）；瞬时抖动产生瞬时积压、抖动退去后自排空。节律：表观稳定，正确性随时可破 | 过冲窗口内：(a) 每次过冲触发期都可能出现并发重叠（正确性破坏）；(b) taskTime>period 期间积压按 (taskTime−period)/taskTime 每周期单调增长，默认 queueCapacity=10000（`ThreadPoolConfig.java:25`）；(c) 抖动回落则积压自排空；**持续过冲 → 队满 → timer 线程上 RejectedExecutionException → 周期任务被 JDK 静默永久取消（C7-001 第 3 段）——节律永不恢复**，抖动即便回落任务已死，唯重启可救。三实现中唯一提供「不可恢复」终态退化 |
| **fixedDelay（JDK 版，DefaultScheduledExecutor:132）** | 周期自动伸展为 taskTime+delay，无重叠无积压，**唯一的健康退化形态** | 同左：任务过跑则下一轮顺延，节律柔性退化。但**任务异常 → JDK 静默取消周期任务**（future 异常完成、无日志）——节律死亡且无恢复（C3-001 异常绝缘族站点） |
| **fixedDelay（Bind 手工重排版，:72-116）** | command.run() 完成后才重排（:107），有效周期=taskTime+delay+调度开销，语义正确 | 同 JDK 版的柔性退化。**任务异常 → LOG.error + future.completeExceptionally → 不再重排（:100-104）→ 节律死亡不可恢复**。与 JDK 版同属 C3-001 族节律面；Bind 版比 JDK 版多一层可见性（有日志、future 有异常），但恢复责任同样全压在调用方——live 证据：`nop-sys-dao/SysDaoMessageService.java:295-301` 注释原文「BindScheduledExecutor的周期任务抛Throwable即永久停摆：fetchCandidates/claim/…的DB异常必须吞掉记日志，让下个周期继续」，其 `processNonBroadcastEvent` 以 catch(Exception) 全包验证了「每个调用方必须自建异常绝缘」这一实际工程负担 |

### 1.2 判定

1. 模式Ⅰ/Ⅱ的**主害均已被 C7-001（重叠+积压+静默死亡）与 C7-002（补偿突发+挤压）覆盖**，本轮不重复立案；对照表作为两案的失效面扩充证据保留。
2. 新增事实面（不立案，登记）：
   - **模式Ⅰ零裕度常警**：fixedRate 在 taskTime→period 时裕度趋零，Bind 层重叠概率→1、Default 层补偿概率→1；即 C7-001/C7-002 的触发门槛在「恒定略小于间隔」模式下长期贴地，等价于把偶发缺陷变成常态缺陷。此为两案风险段的时间尺度补充。
   - **fixedDelay 手工链 reschedule 拒绝路径**（:107 在绑定 executor 工作线程上抛出）→ 链死且 future 永不完成 → **立案 C7-014**（见发现条目）。
   - **fixedDelay 异常节律恢复 = 无恢复**（两形态均然），属 C3-001 族，本表仅登记节律面，按盲审预注不重复立案。

---

## 二、任务②：时钟敏感性站点对照终表（A/B 面全量）

现场 grep 口径：`System.currentTimeMillis|System.nanoTime|CoreMetrics.currentTimeMillis|CoreMetrics.nanoTime`，范围 `nop-commons`（A 面）+ `nop-core`/`nop-xlang`（B 面）。第 1 轮 9 组 → 终表 24 组。时钟源基准：`nop-api-core/.../time/CoreMetrics.java` 默认实现 = `System.currentTimeMillis()` / `System.nanoTime()`，`registerClock` 可替换（测试注入口），`IClock.nanoTime` 契约注释「实现必须保证单调递增」。

| # | 站点 | 时钟源 | 回拨/前跳后果 | 判定 |
|---|---|---|---|---|
| 1 | `DefaultScheduledExecutor` 全部 schedule 入口（JDK `ScheduledThreadPoolExecutor` 内部） | nanoTime（JDK 内部） | 免疫 ✔ | 不适用 |
| 2 | `IRateLimiter`/`DefaultRateLimiter` 令牌 refill（Guava `RateLimiter`/`SleepingStopwatch`） | nanoTime（Guava 内部） | 免疫 ✔；空闲突发上限 = maxBurstSeconds(1s)×速率，设计内有界。**但等待本身不可中断 → C7-015** | 免疫（回拨）/缺陷另立（中断） |
| 3 | `LocalFileLock` 超时（`LocalFileLock.java:46` `CoreMetrics.nanoTime()` + isExpiredNanos） | nanoTime | 免疫 ✔ | 不适用 |
| 4 | `LocalResourceLockManager` 全部租约语义（:94,118,146,152,169,233,259） | currentTimeMillis | **敏感 ✗：前跳→互斥静默破坏+续租永久失败；回拨→锁滞留等比拉长**（C7-004 已立案） | 非自愈，已立案 |
| 5 | `IResourceLock.java:82` expireAt = currentTimeMillis + leaseTime | currentTimeMillis | 同上族（C7-004 F3） | 已立案族 |
| 6 | `ResourceMultiLock.java:99,116` waitTime 计时 | currentTimeMillis | 敏感 ✗：回拨仅扭曲单次等待时长（变长/变短一次），自愈型 | 登记（C7-004 F3） |
| 7 | `ResourceCacheEntry.isRefreshEnabled:104` / `lastLoadTime:223` | currentTimeMillis | 敏感 ✗：refresh-min-interval 节流窗口失真，自愈型；:223 的 load 后取 mtime 即 C7-007 窗口二证据 | 登记（C7-004/C7-007 F3） |
| 8 | `StandardThreadPoolExecutor:109,110,178` 线程更新基准 | currentTimeMillis | 敏感 ✗：回拨延迟线程更新一拍，自愈型（C7-013 已立案） | 已立案 |
| 9 | `IBlockingSource.drainTo:92,104` / `SequentialTaskExecutor:126,138,147` 配额与耗时 | nanoTime | 免疫 ✔ | 不适用 |
| 10 | `AbstractResourceParser:92,105` / `MarkdownTestFile`（测试）解析耗时 | nanoTime | 免疫 ✔ | 不适用 |
| 11 | `TaskExecutionGraph:111,145,205,216` / `CoreInitialization:88,116` 耗时统计 | currentTimeMillis | 仅 elapsed 日志展示：回拨/前跳使单条 usedTime 日志失真一次 | 展示面，不立案 |
| 12 | `AbstractExecuteStat:88,103` / `JdbcSqlStat:716,781`（max-occur-time、last-error-time） | currentTimeMillis | 统计注解展示失真（自愈型） | 展示面，不立案 |
| 13 | `IZipOutput:46,53` zip 条目时间 | currentTimeMillis | 条目时间戳展示语义 | 展示面，不立案 |
| 14 | `NopThread.java:23` creationTime | currentTimeMillis | 线程创建时刻展示失真（回拨一拍） | 展示面，不立案（第 1 轮漏登记，本轮补全） |
| 15 | `DateHelper.java:117` 业务「当前时刻」 | currentTimeMillis | 墙钟即语义（业务要的就是墙钟） | 设计内 |
| 16 | `JsDate:37,68,216`（xlang 脚本 Date 语义） | currentTimeMillis | 同上，脚本 Date.now() 墙钟即语义 | 设计内 |
| 17 | `DefaultTaskExecutionQueue.waitForAllTasks:306,309` 等待预算 `endTime = now + timeout`、逐任务 `remaining = endTime - now` | **currentTimeMillis** | **敏感 ✗：前跳 → remaining≤0 → 立即返回 false（伪超时，任务明明即将完成）；回拨 → 预算虚增 → 超额等待（有界于任务完成）**。全仓 grep 无生产调用点 | 敏感，无调用方（C7-004 族新站点，随族登记不单独立案） |
| 18 | `VirtualResourceDependencySet.java:28` MockReference.lastModified = 创建时刻快照 | currentTimeMillis | 敏感 ✗，自愈型：回拨使新建快照小于旧值 → mtime 不等 → 误判 changed → 虚假 reload 一次（与 C7-007 的 mtime 相等比较链同一族）；前跳无影响 | 登记（C7-004/C7-007 F3 同族） |
| 19 | `InMemoryTextResource.java:146` update 时 lastModified = now | currentTimeMillis | 同 18，虚拟/内存资源 mtime 语义，回拨 → 误判 changed → 多 reload 一次，自愈型 | 登记（同族） |
| 20 | `AbstractFile.java:61` nanoTime 生成唯一后缀 | nanoTime | 免疫 ✔（用途为唯一性非计时） | 不适用 |
| 21 | TTL：`LocalCache`/Caffeine expireAfterWrite/Access | Caffeine systemTicker = nanoTime | 免疫 ✔；`ResourceLoadingCache` cacheTimeout → Caffeine（第 1 轮已核 cacheTimeout 默认 null 无兜底 → C7-007 建议面） | 免疫（时钟面） |
| 22 | 超时：`BaseTestCase:131,145`（测试基类等待预算） | currentTimeMillis | 测试代码，非生产长时面 | 出范围 |
| 23 | 超时：`CoreMetrics.timeoutToExpireTime:76` + `api-core/time/TimeOut`（expireTime=now+timeout 墙钟） | currentTimeMillis | 若被采用即把墙钟租约模式扩散到 api-core 公共层；**全仓 grep 无生产调用点** | 预留 API，登记 CL-034 |
| 24 | Guava `RateLimiter.tryAcquire(timeout)` 超时判定与等待 | nanoTime（判定/计时） | 免疫 ✔；等待实现 `sleepMicrosUninterruptibly` **不可中断** → C7-015 | 免疫（时钟）/缺陷另立（中断） |

### 2.1 四个专项结论

- **IRateLimiter 令牌 refill**：Guava `SmoothRateLimiter` 内部 `SleepingStopwatch.readMicros() = System.nanoTime()` —— 回拨免疫 ✔。但 `tryAcquire` 预留后 `stopwatch.sleepMicrosUninterruptibly(...)` —— **线程不可中断地睡满预留时长**，中断仅被记录并在睡满后补置位。这是本轮对第 1 轮挂死点 H11 判定（「可中断」）的**事实修正**，立案 C7-015。
- **租约计算**：LocalResourceLockManager 7 站点 + IResourceLock:82 全部墙钟（C7-004 已立案）；同包 LocalFileLock:46 已用 nanoTime，纪律不一致面 C7-004 已覆盖。
- **TTL 计算**：平台 TTL 全部经 Caffeine ticker（nanoTime，免疫）；唯一的墙钟 TTL-邻接面是 ResourceCacheEntry.isRefreshEnabled 的 refresh-min-interval 节流（自愈型，登记）。
- **超时计算**：新发现 `DefaultTaskExecutionQueue.waitForAllTasks` 墙钟等待预算（无生产调用方，随族登记）；`ResourceMultiLock` waitTime（登记）；Guava timeout 参数语义在 nanoTime 上（免疫）。api-core `TimeOut`/`timeoutToExpireTime` 为零调用预留 API（CL-034）。

---

## 三、任务③：单调增长源抽验（8 行）与补登

### 3.1 抽验结果（M1/M5/M9/M13/M17/M20/M23/M24 vs live code）

| 行 | 抽验结论 | 一致性 |
|---|---|---|
| M1 `GlobalExecutors.g_executors` | `ConcurrentHashMap`，register/unregister 配对（put+destroy old / remove），5 个静态名单例经 computeIfAbsent——键空间恒定 | ✔ 一致 |
| M5 `RateLimitExecutorImpl.promiseMap` | throttle：compute 内 schedule + 任务 finally `remove(k, promise)` ✔；debounce：任务 finally `remove(key, holder[0])` ✔。**补登两个细化面**：(a) 已取消未启动的 future 不经 finally → 条目残留为 cancelled 态，下次同 key 调用被覆盖（自愈，键空间有界，与第 1 轮「自愈」判定相符）；(b) **debounce 并发同 key 竞态**——`get→schedule→put` 非原子（:52-66），两线程并发对同一 key debounce 时双双 get 到 null、双双重排，put 相互覆盖后**两个任务都会触发执行**（去重语义在并发下失效）。见 CL-033 | ✔ 一致（+2 细化面） |
| M9 `LocalCache` | `buildCache:80-136` 确认无 `.scheduler(...)` 无 `.executor(...)`；`destroy():58-60` 仅一次 cleanUp——C7-009 证据链完全吻合 | ✔ 一致 |
| M13 `ResourceDependsManager.dependencyMap` | 移除路径仅 `clearDependencies()` 与 `close()`（均全量 clear），无单条淘汰——C7-008 判定吻合 | ✔ 一致 |
| M17 各 `ResourceLoadingCache` | `createCache:106-112` `config.setMaximumSize(cacheMaxSize.get())`，上限配置驱动 | ✔ 一致 |
| M20 `GlobalStatManager` | 三个 LocalCache 均以 `CacheConfig.newConfig(CFG_CORE_STAT_*_CACHE_MAX_SIZE.get())` 构造（:35,41,47） | ✔ 一致 |
| M23 `FileWatcher.queue` | `LinkedBlockingQueue` 无参构造（:24），无界 | ✔ 一致 |
| M24 `CycleSynchronizer.onCycleCompleted` | resolvePromise 中置 null（:86），每周期瞬态；**顺带发现 Error 逃逸面**（见五/3，登记为 C7-011 F3 扩充，不另立案） | ✔ 一致 |

**8/8 抽验一致，第 1 轮 M 清单无需改判。**

### 3.2 补登增长源（第 1 轮漏登记）

| # | 增长源 | 位置 | 增长驱动 | 判定 |
|---|---|---|---|---|
| M25 | `DefaultTaskExecutionQueue.states` | `nop-core/execution/DefaultTaskExecutionQueue.java:27` | addTaskIfAbsent/replaceTask 插入；完成时自动移除（:233 whenComplete finally、:245 同步异常路径）——**正常流有界（每任务瞬态）**；但任务挂死（H7 族）则条目永驻 | 有界（瞬态），挂死放大面（登记，随 H7 族） |
| M26 | `ReactiveObjectMap.objMap` | `nop-core/resource/cache/ReactiveObjectMap.java:22` | 按 key 插入，依赖变更时 replace（:47-49）；键空间任意字符串。**失败结果粘滞**：fn 抛异常 → `completeExceptionally`（:57）→ 条目**不移除**，后续同 key get 若依赖未变则永久重抛同一异常（失败被无限期缓存） | 有条件无界（键空间）+ 粘滞失败面 → 立案 C7-017 |
| M27 | `FileWatchEntry.subEntries` | `nop-core/resource/watch/FileWatchEntry.java:28` | 递归 watch 时每子目录一条（NioFileWatchService:97,172）；`unwatch:213-221` 递归 cancel 配对、ENTRY_DELETE 路径也回收（:123-126） | 有界（配对注销；但 OVERFLOW 漏事件可致子目录漏注册，见 CL-032） |

### 3.3 B 面 listener 列表专项（任务书点名的疑似漏登记面）

grep `List/Set/Collection<...Listener>` 与 `CopyOnWrite*` 于 nop-core/nop-xlang 全量：命中仅 `ReflectionManager.beanModelEnhancers`（M10-M12 已覆盖类目）与 `ErrorMessageManager`（M19）——**负结果：B 面无第 1 轮漏登记的 listener 列表增长源**。其余并发容器字段（JsonSerializerFactory、DictProvider、DefaultVirtualFileSystem、EvalGlobalRegistry、XPathOperatorRegistry、StdDomainRegistry、EvalBackendRegistry、ObjPropMapperRegistry、ValueResolverCompilerRegistry、XLangSemantics 静态表等）均为 M15/M18/M19「启动/扩展期注册+键空间静态」已覆盖类目。`ChatServiceImpl.rateLimiters`（nop-ai，per-provider map）键空间=模型配置，静态有界，非 B 面范围，仅备注。

---

## 四、任务④：HighWatermarkSemaphore / IRateLimiter 长时深水区（状态机全图）

### 4.1 HighWatermarkSemaphore 状态机全图（`semaphore/HighWatermarkSemaphore.java`）

先证使用面：全仓 grep（排除自身与测试）**零生产调用点**——第 1 轮 C7-005 已按设施契约口径立案，本轮维持该口径。

```
状态 = (usedPermits, overHighWatermark)
S0 空闲:        (0, false)
   │ tryAcquire(p) 成功 → usedPermits += p
   ├─ 若获证时 usedPermits < lowWatermark：容量检查被豁免（while 条件第二子句
   │   `usedPermits+permits > highWatermark && usedPermits >= lowWatermark` 不成立）
   │   → 可一次跳到 (lowWatermark-1)+p，p ≤ highWatermark
   │   → usedPermits 可合法超过 highWatermark（突发窗口），置 overHighWatermark=true
S1 突发后:      (U > highWatermark, true)
   │ 此后【一切】tryAcquire（哪怕 permits=1、U < highWatermark）都进 while 等待
   │   → 等待唯一出口：release 使 usedPermits 从 ≥lowWatermark 跨到 <lowWatermark
   │     （release:92-95，跨低水位瞬间清标志 + signalAll）
S2 滞回等待态:  (U ≥ lowWatermark, true)   ← 持证者泄漏/挂死 ⇒ 永久滞留（C7-005 已立案）
   │ 跨低水位 → (U < lowWatermark, false) → 回到类 S0，可再次突发
```

状态转移面之外的三要素核查（转移/泄漏恢复/观测）：
- **转移**：如上图。**漏判新面：突发窗口超卖**——usedPermits 合法上限是 `lowWatermark − 1 + highWatermark`（获证瞬间 usedPermits<lowWatermark 时容量检查被豁免 + permits≤highWatermark 限制），即**同时持证数可超出 highWatermark 达 lowWatermark−1 个**。以 highWatermark 对下游资源池做容量规划的使用者会被超卖；且类 Javadoc 为空（`/** */`，:9-11），突发豁免语义零文档。→ **立案 C7-016**。
- **泄漏恢复**：无租约/无强制回收/`resetStats` 只清计数不清状态——C7-005 已立案，不重复。
- **观测**：`getUsedPermits`/`availablePermits` 存在，但 `overHighWatermark` 无读取接口、等待中线程数（Condition waiters）不可观测——并入 C7-016（观测子面），整改归属与 C7-005 建议(2)同点位。
- **次级观测（不立案）**：ReentrantLock 非公平 + 仅跨低水位时 signalAll——反复突发下 S1→S2 等待者与新到获证者（突发窗口豁免）存在饥饿窗口，量级受滞回动力学约束，P3 以下。

### 4.2 IRateLimiter/DefaultRateLimiter 状态机全图（Guava SmoothRateLimiter 包装）

使用面：**有 4 处生产调用**——`nop-batch RateLimitConsumer`（长时批处理限流）、`nop-ai ChatServiceImpl:363` / `EmbeddingServiceImpl:223`（AI 配额）、`nop-datav NopDatavShareAccessGuard`（其注释明示**弃用** DefaultRateLimiter 自建计数器）。

```
状态 = (storedPermits[0..maxPermits], nextFreeTicketMicros)
refill:  storedPermits ← min(max, stored + (now-nextFree)×rate)   // nanoTime，免疫回拨
acquire/tryAcquire(p, timeout):
   ① 同步预留: nextFreeTicketMicros ← max(now, nextFreeTicketMicros)
      本次等待 = nextFreeTicketMicros − now（并发调用者各自预留、债务线性累加）
   ② canAcquire 判定: 债务 ≤ timeout → 预留并进入 ③；否则立即 false（不睡眠，非阻塞 ✔）
   ③ stopwatch.sleepMicrosUninterruptibly(等待)   ← 不可中断睡满，中断被吞、睡满后补置位
```

- **漏判新面（立案 C7-015）**：③ 的不可中断性 + 接口默认 `acquire() = tryAcquire(1, Long.MAX_VALUE)`（`IRateLimiter.java:33-35`）构成「无上界不可中断阻塞」公共 API；实态调用面中最长为 RateLimitConsumer 的 20 分钟（`RateLimitConsumer.java:21`）。
- **语义澄清（登记）**：② 的预检使「等待」被 timeout 截断（第 1 轮 H11 的「速率数学上界」在 tryAcquire 路径成立）；`acquire()` 默认路径 timeout=Long.MAX_VALUE 使预检恒过，等待 = 速率倒数 × 既有债务，数学无上界。
- 泄漏恢复：无状态泄漏可能（storedPermits 有界于 maxPermits；债务随睡眠消耗不累积）✔；观测：success/fail 计数 ✔（NopDatavShareAccessGuard 注释提到的 getAcquireFailCount 返回错计数缺陷在当前分支已不存在——`DefaultRateLimiter.java:39-41` 返回 acquireFailCount，应为 remediation 分支已修，登记备考）。

---

## 五、任务⑤：挂死点抽验（H1/H2/H8/H9，另核 H14）

| 行 | 抽验结论 | 一致性 |
|---|---|---|
| H1 `ExecutorHelper.throttleExecute` | `semaphore.acquireUninterruptibly()` 在 `ExecutorHelper.java:243` 原样在案：无超时、不可中断、调用线程直接阻塞（生产调用者 nop-web PageProvider/WebPageExporter） | ✔ 一致 |
| H2 `ExecutorHelper.scheduleWithRandomDelay`（future 永不完成族） | `:210-238` 原样在案：`command.call()` 内仅 `completeExceptionally` 分支，成功路径 future 永不 complete（C7-003）；`future.exceptionally → ref.get().cancel(false)` 与 timer 线程 `ref.set` 的 ghost-chain 竞态成立 | ✔ 一致 |
| H8 `CycleSynchronizer.waitCycleCompleted` | cyclePromise 仅在 resolvePromise 完成、无超时、推进者死亡即永久挂（C7-011）——**核读中发现同站点 Error 逃逸新面**：`resolvePromise:74-102` 的 catch 只捕 `Exception`（:96），回调抛 Error 时跳过 `:93-95 promise.complete(finished)` → **周期已结束但等待方永久挂死**，同时 onCycleCompleted 列表已在 :86 先行置 null、剩余回调永久丢失、Error 还会打入周期推进者。这是 C7-011「catch(Exception) 转 completeExceptionally」未覆盖的分支（Exception 路径等待方尚能收到异常），属同站点漏判面，按预注**登记为 C7-011 F3 扩充，不另立案** | ✔ 一致（+Error 面补登） |
| H9 `BindScheduledExecutor.scheduleWithFixedDelay` 手工链 | `:88-116` 核读：重排严格依赖 command.run() 完成（挂死即链死）；任务异常 → completeExceptionally 且不再重排（链死）；执行体 enqueue 拒绝路径有 catch（:109-111）——**但 :107 重排调用自身抛出（timer 已销毁时 `checkEnabled` 抛 NopException / `RejectedExecutionException`）发生在绑定 executor 工作线程 lambda 内，无人捕获 → 链死 + 返回的 future 永不完成** → 立案 C7-014 | ✔ 一致（+新面立案） |
| H14（顺带核读）`ResourceCacheEntry.getObject` synchronized 加载 | `:176-208`：双检 + synchronized(loadObject)，loader I/O 持 entry 监视器，同 key 串行、loader 挂死放大为该 entry 全体访问挂起——与第 1 轮登记一致；异常路径 `object=null` 重试语义良好（:187-191） | ✔ 一致 |

抽验 4+1 行全部一致；由 H2 族延伸的新站点立案 1 条（C7-014）。

---

## 发现条目（第 2 轮新增，自 C7-014 起）

### [nop-kernel-concurrency-C7-014] 调度器族 shutdown 窗口 caller-facing future 永不完成：bindCancel 单向绑定 + Bind 手工链 reschedule 拒绝路径链死

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/BindScheduledExecutor.java:107`（重排拒绝路径）；`nop-api-core/src/main/java/io/nop/api/core/util/FutureHelper.java:313-318`（bindCancel 单向性）；`concurrent/executor/ExecutorHelper.java:128-199`（submit/schedule 同族）
- **证据片段**:
  ```java
  // BindScheduledExecutor 私有 schedule()，重排发生在绑定 executor 的 worker lambda 内：
  executor.execute(() -> {
      ...
      command.run();          // :100
      ...
      schedule(command, delay, delay, unit, future, timerHandle);   // :107 —— 此处抛出无人接
  });
  // FutureHelper.bindCancel —— 仅 promise→future 单向：
  public static void bindCancel(CompletionStage<?> promise, Future<?> future) {
      promise.whenComplete((value, ex) -> { if (!future.isDone()) future.cancel(true); });
  }   // JDK future 被 shutdownNow cancel ⇒ promise 永不 complete，反向无传播
  ```
- **严重程度**: P3
- **并发场景**: globalTimer（DefaultScheduledExecutor）销毁线程（`destroy()` → `shutdownNow()`）× 绑定 executor 工作线程（正执行 command.run() 的重排点）× 持有返回 future 并等待的调用方线程
- **触发时序**: T1 应用停机/重建顺序中 `DefaultScheduledExecutor.destroy()` 执行 shutdownNow；T2 某条 BindScheduledExecutor.scheduleWithFixedDelay 链的任务恰在绑定 executor 上执行完毕，进入 `:107` 重排 → `timer.schedule` 经 `checkEnabled()` 抛 ERR_SERVICE_NOT_ACTIVE（或对已 shutdown 的 ScheduledThreadPoolExecutor 抛 RejectedExecutionException）→ 异常沿 worker lambda 逸出，被线程池吞为未捕获栈（无结构化日志、无 future 通知）→ **链永久死亡且返回的 future 永不完成**。同族：ExecutorHelper.submit/schedule 的 pending 任务在 shutdownNow 下被 cancel（JDK future 侧），bindCancel 单向 → **返回给调用方的 CompletableFuture 同样永不完成**；BindScheduledExecutor 一次性 `schedule():55-62` 初排抛出可同步传播给调用方（fail-fast，无此面）。生产调用面：`SysDaoMessageService:217/221/228`（3 条 fixedDelay 链）、`AbstractBatchScanner:135`、`AbstractPollingLeaderElector:19`、`AutoRegistration:205`（executeOn 组合）。时间尺度：触发窗口 = 每次停机/重建事件（秒~分钟级）；挂死持续 = 进程生命周期（无超时可救）。
- **现状**: 当前仓库内全部调用方在 doStop 中只 `cancel(false)`、未发现 `get()/join()` 等待返回 future 的用法，故危害为潜伏合同缺陷；C7-003 已立案 scheduleWithRandomDelay 的「成功路径永不完成」，本案是其 shutdown 语义面在「排定即可能被取消」的全部 future-returning 调度入口上的推广，且给出确切根因（bindCancel 缺反向传播 + :107 重排无绝缘）
- **风险**: 任何调用方一旦引入「停机时 join 周期任务以保数据落盘/会话收敛」的常规写法（集群选主、自动注册、消息扫描均属典型场景），停机流程即永久挂死；长时运行系统停机窗口是必然事件，缺陷在首次「join-at-stop」重构时确定性显形
- **建议**: (1) `:107` 重排包 try/catch：拒绝时 `future.completeExceptionally(e)` + LOG.warn，链死可见；(2) bindCancel 补反向传播（f.whenComplete(cancelled → promise.completeExceptionally(CancellationException))），使 shutdownNow 下所有 pending promise 统一收敛；(3) Javadoc 声明「future 在 timer 销毁后不保证完成，禁止 join-at-stop」的过渡口径
- **失效面枚举**: future-returning 调度入口全清单——BindScheduledExecutor.scheduleWithFixedDelay 重排路径:107（本案主站点）；ExecutorHelper.submit:128-150 / submit:152-174 / schedule:176-199（pending 被 shutdownNow 取消后 promise 永不完成，bindCancel 单向根因）；ExecutorHelper.scheduleWithRandomDelay:210-238（C7-003 已立案，互引）；BindScheduledExecutor.schedule:55-62（初排同步抛出，fail-fast，无缺陷）；DefaultScheduledExecutor 直通入口（JDK Future 语义，取消即完成，无此面）
- **信心水平**: 确定（单向绑定与重排抛出路径均为代码直读事实；危害依赖调用方 join 模式，已按 P3 计）
- **误报排除**: 非「shutdown 场景无关紧要」豁免——停机挂死属 F5「不产出也不报错」形态且拉长发布窗口；非 C7-003 重复——本案站点（bindCancel 单向 + :107）与机制（shutdown 取消传播缺失）均未被前轮覆盖
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3**。bindCancel 单向（FutureHelper :311-317，promise→future 无反向传播）与 :107 重排在 worker lambda 内无兜底（command.run() 的 try/catch :99-105 不覆盖其后的 schedule 调用；外层 :109-111 catch 仅覆盖 executor.execute 入队失败）均亲核成立；生产调用面 5 处（SysDaoMessageService:217/221/228、AbstractBatchScanner:135、AbstractPollingLeaderElector:19、AutoRegistration:205）grep 确认；「当前调用方均不 join-at-stop」的 P3 依据成立。与 C7-001/C3-001 同属平台级统一排期族。现状：新发现，已知未修复。

### [nop-kernel-concurrency-C7-015] IRateLimiter 阻塞等待不可中断（Guava sleepMicrosUninterruptibly）：批量限流 20 分钟不可打断、接口默认 acquire() 无上界——第 1 轮 H11「可中断」判定修正

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/ratelimit/DefaultRateLimiter.java:50-58`；`concurrent/ratelimit/IRateLimiter.java:33-39`；消费面 `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/RateLimitConsumer.java:21,37`
- **证据片段**:
  ```java
  // DefaultRateLimiter.tryAcquire —— 直通 Guava：
  boolean b = rateLimiter.tryAcquire(permits, timeout, TimeUnit.MILLISECONDS);
  // Guava RateLimiter.tryAcquire 内部（预留后）：
  //   stopwatch.sleepMicrosUninterruptibly(microsToWait);
  // Uninterruptibles.sleepUninterruptibly：捕获 InterruptedException 后循环续睡直至睡满，
  //   结束才补置 interrupt 标志 —— 等待全程对中断免疫
  // IRateLimiter 接口默认：
  default void acquire(){ tryAcquire(1, Long.MAX_VALUE); }   // timeout 预检恒过 ⇒ 等待无上界
  // RateLimitConsumer（生产调用面）：
  static final long RATE_LIMIT_TIMEOUT = 1000 * 60 * 20L;    // 20 分钟
  if (permits > 0 && !rateLimiter.tryAcquire(permits, RATE_LIMIT_TIMEOUT)) { throw ...; }
  ```
- **严重程度**: P2
- **并发场景**: 批处理 worker 线程（RateLimitConsumer.consume，逐 chunk 限流）、AI 请求线程（ChatServiceImpl:363 / EmbeddingServiceImpl:223，默认 timeout 1000ms，`AiCoreConfigs.java:49`）× 停机/取消方（shutdownNow、批任务 cancel）
- **触发时序**: T1 批处理 worker 消费大 chunk（permits=items.size()），令牌债务使预留等待达分钟级（≤20 分钟上限）；T2 用户取消批任务/应用停机 → shutdownNow/cancel(true) 中断 worker；T3 worker 正处 Guava 睡眠 → **中断被吞、持续睡满全额预留时长**（中断标志延迟到睡满后补置），期间该线程不可用于停机收尾；多 worker 同时在睡则停机延迟取最大者。`acquire()` 默认路径（timeout=Long.MAX_VALUE）下等待 = 速率倒数 × 既有预留债务，数学无上界且同样不可中断——任何新调用方误用即得无限期不可打断阻塞。时间尺度：tryAcquire 显式 timeout 路径有界于参数（现网最大 20 分钟/次）；acquire() 默认路径无上界。
- **现状**: 第 1 轮挂死点 H11 判定「可中断」**有误，本轮修正**：Guava 的 timeout 仅用于「是否值得预留」的预检，一旦预留则睡眠不可中断；ISemaphore 族（DefaultSemaphore/HighWatermarkSemaphore）均为可中断语义，IRateLimiter 是平台并发设施中唯一的不可中断阻塞源，且接口 Javadoc 未声明该差异
- **风险**: 停机窗口被限流等待拉长至 20 分钟级（批处理是长时任务常态形态）；取消响应性失效使运维误判 hang；接口默认方法诱导无界用法。另注：NopDatavShareAccessGuard 注释明示因语义不合弃用 DefaultRateLimiter 自建计数器，佐证该设施契约不清已造成实际选型规避
- **建议**: (1) IRateLimiter 接口 Javadoc 强制声明「等待不可中断，interrupt 延迟交付」；(2) 为长等待场景提供可中断变体（自研令牌桶以 `ISemaphore`/LockSupport.park 实现，或提供 `tryAcquireInterruptibly`）；(3) 审计 `acquire()` 默认方法：要么删除要么改为内部有界（如 60s 上限 + 抛错），杜绝无上界不可中断公共 API；(4) RateLimitConsumer 可将 chunk permits 拆批以压缩单次最长等待
- **失效面枚举**: IRateLimiter 中断面全清单——IRateLimiter.acquire():33-35 / acquire(permits):37-39（默认无上界，本案）；tryAcquire(permits, timeout):23（有界但不可中断，本案）；DefaultRateLimiter.tryAcquire:50-58（Guava 透传，本案）；RateLimitConsumer.consume:37（20 分钟生产面）；ChatServiceImpl.checkRateLimit:363 / EmbeddingServiceImpl:223（1s 默认，低危同面）；Guava RateLimiter 内部（依赖库，按文档化行为采信）；对照面：DefaultSemaphore.tryAcquire（可中断 ✔）、HighWatermarkSemaphore.tryAcquire（可中断 ✔）
- **信心水平**: 确定（Guava `Uninterruptibles.sleepUninterruptibly` 的延迟中断语义为其源码级文档化行为，跨版本稳定）
- **误报排除**: 非「timeout 参数已声明」豁免——timeout 限定时长而非可中断性，调用方无从预期停机信号被吞 20 分钟；非误报校准表任何豁免项；H11 修正属盲审复核职责内的判定纠错而非虚构立案
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2，H11 修正采纳**。IRateLimiter :33-39 默认 acquire()=tryAcquire(1,Long.MAX_VALUE) 无上界不可中断亲核；DefaultRateLimiter :50-58 直通 Guava 亲核；Guava Uninterruptibles.sleepUninterruptibly「中断吞下、睡满补置位」为源码级稳定文档化行为，第 1 轮 H11「可中断」判定确有误、本轮修正成立；RateLimitConsumer :21 RATE_LIMIT_TIMEOUT=20 分钟、:37 tryAcquire(permits,20min) 生产面亲核确认。现状：新发现，已知未修复。

### [nop-kernel-concurrency-C7-016] HighWatermarkSemaphore 突发窗口合法超卖 highWatermark（上限 = lowWatermark−1+highWatermark），滞回状态机零文档零观测

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/semaphore/HighWatermarkSemaphore.java:9-11,50-64,92-95`
- **证据片段**:
  ```java
  /**
   */                                    // 类 Javadoc 为空：滞回/突发豁免语义零文档
  while (overHighWatermark ||
          (usedPermits + permits > highWatermark && usedPermits >= lowWatermark)) {
      ... // usedPermits < lowWatermark 时第二子句不成立 ⇒ 容量检查被豁免，直接放行
  }
  usedPermits += permits;
  if (usedPermits > highWatermark) { overHighWatermark = true; }   // :62-63 超卖后仅置内部标志
  ```
- **严重程度**: P3（设施零生产调用点，按合同缺陷口径）
- **并发场景**: 多业务线程 tryAcquire/release 竞争；突发窗口 = usedPermits < lowWatermark 期间的批量获证
- **触发时序**: T1 突发窗口内 usedPermits=lowWatermark−1，请求 permits=highWatermark → 检查豁免放行 → usedPermits = lowWatermark−1+highWatermark，**同时持证数超出 highWatermark 达 lowWatermark−1**；此后 overHighWatermark=true 进入滞回（C7-005 已立案的死区面不重复）。后果：以 `maxPermits()`/`availablePermits()`（二者均以 highWatermark 为容量语义）做下游资源容量规划的调用方被合法超卖；而标志对外不可见（无 getter、无 stats 字段、Condition 等待数不可观测），超卖与滞回拒绝均无法与容量上限区分。时间尺度：每次突发事件即成立（事件级）；超卖持续 = 排水至 lowWatermark 的时长（秒~分钟级，取决于持有者）。
- **现状**: 构造器校验 high>low、permits≤high（:42-44），但对「低水位下豁免容量检查」这一核心语义无任何文档/注解；`resetStats` 只清计数；第 1 轮 C7-005 立案的是「滞回死区+无自愈」，本案立案的是「突发豁免导致的容量语义越界 + 状态不可观测」，二者为同一状态机的不同漏判面
- **风险**: 设施一旦被采用（当前零调用点），超卖面在使用首日即随第一次突发成立；容量语义与 API 返回值自相矛盾（availablePermits()=0 时 usedPermits 已 > highWatermark），排障时误导性强
- **建议**: (1) 类 Javadoc 补全状态机文档：突发窗口、超卖上限 lowWatermark−1+highWatermark、滞回清除条件；(2) 或收紧语义：豁免时 clamp 到 highWatermark（牺牲部分突发能力换容量承诺）；(3) 暴露 `isOverHighWatermark()`/等待线程数并入 stats（与 C7-005 建议(2)同点位合并实施）
- **失效面枚举**: ISemaphore 实现族逐一状态——HighWatermarkSemaphore（本案：超卖+零文档+零观测）；DefaultSemaphore（JDK Semaphore，release 可使 permits 超过初始 maxPermits 形成对称超卖，但其语义即「非公平计数器」无容量承诺、文档无误导，登记不立案）；LimitedExecutor/ExecutorHelper 内裸 Semaphore（同 DefaultSemaphore 形态）；C7-005（同状态机死区面，互引不重复）
- **信心水平**: 确定（状态转移逐步推导；超卖上限由构造校验与 while 条件直接代数得出）
- **误报排除**: 非 C7-005 重复——本案不是死区/泄漏面而是容量语义与可观测面；非「原语使用」误报——缺陷在设施自身合同（文档空白 + API 语义矛盾），非调用方误用
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3（设施契约口径）**。突发窗口豁免代数亲核成立（usedPermits<lowWatermark 时 while 第二子句恒假，permits≤highWatermark 上限内放行 → usedPermits 上限=lowWatermark−1+highWatermark）；空 Javadoc（:9-11）、overHighWatermark 无读取接口、availablePermits :111-118 以 highWatermark 为容量语义与超卖自相矛盾均核实；零生产调用亲核确认。与 C7-005 修复同点位合并实施。现状：新发现，已知未修复。

### [nop-kernel-concurrency-C7-017] ReactiveObjectMap 失败结果粘滞缓存：异常 future 永驻 objMap，瞬时故障被固化为永久失败直至依赖变更

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ReactiveObjectMap.java:44-62`
- **证据片段**:
  ```java
  public Object get(String key, IEvalFunction fn, IEvalScope scope) {
      ReactiveObject obj = objMap.get(key);
      if (obj == null || obj.isChanged()) {
          objMap.remove(key, obj);
          obj = new ReactiveObject(key);
          ReactiveObject oldObj = objMap.putIfAbsent(key, obj);
          if (oldObj != null) { obj = oldObj; }
          else {
              try {
                  obj.future.complete(ResourceComponentManager.instance().collectDependsTo(obj.deps,
                          () -> fn.call0(null, scope)));
              } catch (Exception e) {
                  obj.future.completeExceptionally(e);   // 失败条目原样留在 objMap
              }
          }
      }
      return FutureHelper.getFromFuture(obj.future);     //  exceptional future → 每次重抛同一异常
  }
  ```
- **严重程度**: P3（全仓 grep 无生产调用点，按设施合同缺陷口径）
- **并发场景**: 多线程对同 key 的响应式求值；失败发生在外部资源抖动（DB/远端瞬时异常）时
- **触发时序**: T1 key K 首次求值，fn.call0 执行中抛瞬时异常（如连接池瞬满）→ deps 仅部分收集或为空 → future 异常完成，obj 留存 objMap；T2 之后任何线程 get(K)：obj≠null 且 `isChanged()` 依赖未变（空/部分 depends 无变化）→ 不重算 → `getFromFuture` 重抛同一陈旧异常 → **瞬时故障被固化为永久失败**，自愈条件是「某个已收集依赖发生变化」——deps 为空时永不满足。时间尺度：首次失败即粘滞；粘滞持续 = 至依赖变更或进程重启（长时运行月级常态下近乎永久）。对照：同包 `ResourceCacheEntry.getObject:187-191` 装载失败时 `object=null` 允许下次重试——同仓库内正确范式已有先例。
- **现状**: 成功结果有依赖变更失效链（设计意图如 :38 注释），失败结果无任何失效/重试路径；`isChanged()` 每次对全部 depends 逐个 stat，也构成每次 get 的持续开销面（负载相关，量级小）
- **风险**: 一旦接入生产（响应式求值语义天然面向高频读），一次抖动即把该 key 打成「永久失败」，故障表象（业务报错）远离根因（历史瞬时异常），排障困难；且与平台「装载失败不缓存」的既有范式不一致
- **建议**: (1) catch 分支同步 `objMap.remove(key, obj)`（或仅保留 remove-if-match 语义）使失败可重试；(2) 或失败条目加粘滞宽限期（如 lastLoadTime+refreshMinInterval 后允许重算）；(3) 补充失败重试语义的单测
- **失效面枚举**: 同族响应式/失败缓存站点——ReactiveObjectMap.objMap（本案）；ResourceCacheEntry.getObject 失败路径:187-191（失败清空可重试 ✔ 无缺陷，对照面）；LocalCache/Caffeine 加载失败（Caffeine LoadingCache 失败不入缓存 ✔ 对照面）；ResourceLoadingCache.newCacheEntry（经 ResourceCacheEntry，失败可重试 ✔）。逐一状态：粘滞失败为 ReactiveObjectMap 独有
- **信心水平**: 确定（代码路径直读；无调用点故按合同缺陷判 P3）
- **误报排除**: 非「C5 缓存容量」重复——本案无容量维度，立案点是失败态的生命周期；非理论竞态——粘滞语义是确定性行为（无需并发交错即成立）
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3（条件化）**。失败粘滞（:56-58 catch 后无 objMap.remove）、deps 空时 isChanged 恒 false 永不重算、对照 ResourceCacheEntry :187-191 失败清空可重试范式均亲核成立；零生产调用亲核确认。现状：新发现，已知未修复。

---

## 维度集外线索（不立案展开，自 CL-032 起）

### [nop-kernel-concurrency-CL-032]（维度集外线索）NioFileWatchService.checkChange 对 OVERFLOW 事件无处理：事件风暴下递归 watch 静默产生永久性目录空洞

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/watch/NioFileWatchService.java:127-129`
- **证据片段**:
  ```java
  } else {
      LOG.debug("nop.core.resource.watch.unhandled-event:{}", event.kind());   // OVERFLOW 落此
  }
  ```
- **线索说明**: WatchKey 事件积压溢出时 OS 投递 OVERFLOW，当前实现仅 debug 日志跳过。溢出期间丢失的 ENTRY_CREATE（尤其新建子目录）意味着对应子树**永不注册** watch（register 仅发生在 walkFileTree 与 CREATE 事件两处）→ 该子树文件变化静默失察直至进程重启。事件风暴（构建/批量解压）是 overflow 的常态诱因，属「持续负载退化+不可逆静默降级」形态。watch 域疑似与前轮 C6-006（watch 交叉）立案空间交叠，盲审约束下无法核对，**由整合者裁定是否并入 C6-006 或转独立立案**。建议方向：OVERFLOW 时对该 key 的 root 做一次子树重扫（对比已注册 subEntries 补注册）。
- **复核处置（独立复核）**: **确认成立，建议独立保留**。OVERFLOW 落 :127-129 else debug 分支亲核；注册仅 walkFileTree（:161-176）与 ENTRY_CREATE（:92-101）两处亲核，溢出漏 CREATE → 子树永不注册成立。与 C6-006 的关系：同文件不同机制面（C6-006=注册表生命周期不对称/unwatch 不摘父 subEntries；本案=OVERFLOW 事件丢失语义），修复互不覆盖——建议独立保留、同批修复，归并裁定权在整合者。另注：第 2 轮 M27 行「配对注销」判定宜补注「父 subEntries 不摘除（C6-006 面）」对齐。

### [nop-kernel-concurrency-CL-033]（维度集外线索）RateLimitExecutorImpl.debounce 并发同 key 双调度竞态：get→schedule→put 非原子，去重语义并发下失效（双执行）

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/RateLimitExecutorImpl.java:50-67`
- **证据片段**:
  ```java
  Future<?> old = promiseMap.get(key);          // :52  T1/T2 均读到 null
  if (old != null) old.cancel(true);
  Future<?> future = executorService.schedule(...);  // 两线程各排一个
  holder[0] = future;
  promiseMap.put(key, future);                  // :66 put 相互覆盖，后写胜出
  ```
- **线索说明**: 两个线程并发对同一 key debounce 时，双方都 get 到 null（或旧值），双双 schedule，put 相互覆盖后**两个定时任务都会触发**（未被 cancel 的一方照常执行）——debounce「只保留最后一次」语义在并发下被破坏，且 map 中残留的败者条目由其自身 finally remove(key, holder[0]) 清理（可自愈）。当前主要调用者 FileWatcher 的 handler 幂等（重复 reload 无正确性害），故未达立案线。属正确性/重复执行形态（C5 空间）。建议：debounce 主体改 `promiseMap.compute(key, ...)` 原子段内完成 cancel+schedule+put（schedule 回调在 compute 外执行的约束需另行设计），或接受文档化「并发 debounce 至多执行两次」。
- **复核处置（独立复核）**: **确认成立，维持不立案登记**。get(:52)→cancel→schedule(:56)→put(:66) 非原子亲核，并发同 key 双执行成立；同文件 throttle :32-46 用 compute 原子段恰证 debounce 为异常点；FileWatcher handler 幂等未达立案线的定性维持。与第 2 轮 M5 细化面(b)一致。现状：已知（登记）。

### [nop-kernel-concurrency-CL-034]（维度集外线索）api-core `TimeOut`/`CoreMetrics.timeoutToExpireTime` 为零调用预留 API：墙钟 expireTime 模式若被启用将把 C7-004 租约缺陷面扩散到全平台公共层

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/time/TimeOut.java:22`；`time/CoreMetrics.java:76-80`
- **证据片段**:
  ```java
  this.expireTime = CoreMetrics.timeoutToExpireTime(timeout);   // expireTime = 墙钟 now + timeout
  ```
- **线索说明**: 全仓 grep 无生产调用点。其实现与 C7-004 立案的 LocalResourceLockManager 墙钟租约同构（expireAt=currentTimeMillis+timeout），若后续被公共 API 采纳，NTP 步进即可在其全部使用点上复现「伪过期/伪有效」。属预警性登记：整改 C7-004 时应将本类一并纳入（统一为 nanoTime 基准或双检），或在 Javadoc 标注墙钟敏感性禁止用于互斥语义。
- **复核处置（独立复核）**: **确认成立，预警登记有效**。TimeOut :22 expireTime=CoreMetrics.timeoutToExpireTime(timeout)=currentTimeMillis()+timeout（CoreMetrics :76-80）亲核；全仓生产调用为零（仅 TestTimeOut）亲核确认。与 C7-004 复核意见一致：整改 C7-004 时一并统一为 nanoTime 基准。现状：已知（登记）。

---

## 终止信号判定

- **本轮新增发现**: C7-014（P3）、C7-015（P2）、C7-016（P3）、C7-017（P3）——**无新增 P0/P1**（最高 P2×1，且为有界等待的不可中断面，非不可逆正确性破坏）。
- **前轮 P1 两条（C7-001/C7-007）复核**: 本轮对照表与抽验未发现超出已立案失效面的新 P1 级站点；C7-001 的模式Ⅰ/模式Ⅱ劣化排序、C7-007 的窗口二证据链（`ResourceDependsManager.collectDepends` 的 load 后取 mtime、`ResourceCacheEntry.loadObject:223`）均与 live code 复核一致。
- **深挖收敛证据**: 任务书 ①~⑤ 全部执行完毕——① 无新立案主害面（对照表归档）；② 24 站点组终表闭合，四个专项全部落定；③ 8/8 抽验一致 + 3 行补登 + listener 负结果；④ 两状态机全图完成，漏判面均已立案（P2/P3 各半）；⑤ 5 行抽验一致，仅延伸出 P3 一条。
- **判定**: 满足「全部 P2/P3 + 终止信号」条件，**建议 C7 维度于本轮收敛终止**，不再启动第 3 轮。遗留开放项仅 CL-032（与 C6-006 的归并裁定需整合者按非盲审权限处理）。

---

## 审计覆盖说明与局限

- 时钟终表基于 `System.currentTimeMillis|System.nanoTime|CoreMetrics.*` 全量 grep，Guava/Caffeine/JDK 依赖库内部时钟按其文档化语义采信（nanoTime 系）；`CoreMetrics.registerClock` 注入自定义 IClock 的场景（测试）不在生产面内。
- 「无生产调用点」判定基于全仓 Java 源 grep（HighWatermarkSemaphore、DefaultTaskExecutionQueue/ITaskExecutionQueue、ReactiveObjectMap、TimeOut、executeOn 之外 BindScheduledExecutor 直接构造点均无命中），不排除经 beans.xml/反射装配未被发现（与第 1 轮同一局限口径）。
- Guava `RateLimiter.tryAcquire` 不可中断结论基于 Guava `Uninterruptibles.sleepUninterruptibly` 的源码级稳定行为，未逐版本核对仓库锁定版本号；该行为自 Guava 15 起无变化。
- CycleSynchronizer Error 面、debounce 双执行竞态、OVERFLOW 空洞三个同族/集外面按预注分别以 F3 扩充与 CL 登记，未占用新发现编号。
- 本轮未读取 C3/C4/C5/C6 各维度报告，与之的交叉（C3-001、C4-001、C6-006、C6-007、C7-004 族内站点）全部依据任务书预注与第 1 轮报告内的互引信息，归并裁定权在整合者。
