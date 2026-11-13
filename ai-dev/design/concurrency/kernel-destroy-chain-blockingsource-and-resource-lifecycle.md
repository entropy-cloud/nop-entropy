# nop-kernel 销毁链与 BlockingSource 生命周期（R4-14 G27 后半，17 成员）

> Status: active
> Last Reviewed: 2026-11-09
> Source: plan 2548（`ai-dev/plans/2548-cra-phase8.54-r4-14-kernel-destroy-blockingsource-b.md`）Phase 1；批次定义 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二 :129 R4-14 行（17 finding ID / nop-kernel / PA=框架核心 plan-first）；成员发现 = `ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/`（C4-thread-pools.md / C4-round2.md / C5-memory-bounds.md / C6-resource-lifecycle.md / C6-round2.md / C7-long-running.md / C7-round2.md / C9-hot-path.md）
> Protected Area 授权：全部站点在 `nop-kernel/nop-commons`、`nop-kernel/nop-core`、`nop-kernel/nop-xlang`、`nop-utils/nop-commons-java21` = AGENTS.md「框架核心引擎」= **plan-first**。授权载体 = 本文档 + 独立 draft review + 回归面清单。
> 传导先例：[kernel-lock-discipline-interrupt-contract-and-executor-destroy-semantics.md](kernel-lock-discipline-interrupt-contract-and-executor-destroy-semantics.md)（R4-13 同模块前批，本批**复用其 C4-002 destroy 语义裁定、C1-010 generation 守卫、C2-019 CLASS_MODELS**，不重建）；[pool-facility-hardening.md](pool-facility-hardening.md)
>
> **基线勘误 E-2548-1（draft review R1）**：
> - **C6-001 判级 = P2（条件化），非 P1**——权威台账 `C6-resource-lifecycle.md:83` 已「降级 P1→P2；主根因主张驳回（`ThreadPoolExecutor.shutdown()` 的 JDK 契约为「previously submitted tasks are executed」，排队任务会被排空）」，残余成立面 = **S2 变体**：`DefaultScheduledExecutor.destroy()`= `shutdownNow()` 丢弃排队任务（live `DefaultScheduledExecutor.java:120-131` 已证实）。本批 17 项**全 P2**。
> - **`ExecutorHelper` 只有 3 个 CF 入口**（`submit(Callable)` / `submit(Runnable,T)` / `schedule(Callable,...)`）；`scheduleAtFixedRate`/`scheduleWithFixedDelay` **不在本文件**（返回 `Future<?>`，已由 R1-01 绝缘链覆盖）。第 4 个同形态入口 = `scheduleWithRandomDelay`（C7-003 幽灵链），**本批显式 out-of-scope**（登记 Deferred）。
> - **`GlobalExecutors` 无 `destroy()` 方法**（只有 `register`/`unregister`/`allExecutors` + 各池 getter）。C4-005 的真实形态见裁定 B（重写）。
> - **执行期勘误 E-2548-2（G10 实施中实测）**：C7-008 的「`ResourceCacheEntry` 失效回调里移除依赖集」
>   **会造成功能回归**——`TestResourceCacheInvalidationChain` 3 red（entry.clear 淘汰 dependencyMap 条目
>   会断开其它条目经该依赖集的变更感知链）。**唯一形态修正**：`removeDependency` API + 容量告警保留
>   （供显式清理路径），entry 生命周期内**不自动淘汰**；单调增长风险由水位告警观测。
> - **`IRateLimiter.tryAcquire(int,long)` 已存在**（`IRateLimiter.java:23`），`acquire()` 默认走 `tryAcquire(1, Long.MAX_VALUE)`——C7-015 的真实可做面见裁定 E（重写）。

## 一、设计结论（六条裁定）

1. **裁定 A（destroy 侧终态化补偿，非提交点结算）**：成员 **C6-001（P2，条件化）**。
   - **真实失效面（draft review R1 B-1/B-3 勘正）**：`DefaultScheduledExecutor.destroy()` 用 `shutdownNow()`
     丢弃**已排队未执行**的任务；这些任务的 wrapper future（由 `ExecutorHelper.submit/schedule` 创建并
     已返回给调用方）**永不完成** → 调用方永久挂起。`DefaultThreadPoolExecutor.destroy()` 用 `shutdown()`
     会排空队列（JDK 契约），**不命中**。
   - **修复形态（唯一权威表述见 §四.1「待结算登记表」；本节仅为摘要）**：`ExecutorHelper` 新增
     `submitTracked` / `submitTracked` / `scheduleTracked` 三个**独立方法名**（不用同 arity 重载，避免与
     `submit(ExecutorService, Runnable, T)` 的 lambda 解析歧义），内部把返回的 `CompletableFuture` 登记到
     调用方传入的 `Set<CompletableFuture<?>> pending`；`DefaultScheduledExecutor` 持有该表，
     `destroy()` 按 §四.1 的顺序（**先** `periodicTasks` 拒绝循环、**再** `shutdownNow()`、**最后** pending 逐项
     `completeExceptionally(ERR_SERVICE_NOT_ACTIVE)`）收口。`BindScheduledExecutor` 补 inner→outer 异常传播
     （仅在底层 timer 能终态化内层 future 时有效，本批保证面 = `DefaultScheduledExecutor`）。
     **不得**依赖「对 `shutdownNow()` 返回的 `List<Runnable>` 识别 wrapper」——live 上该载体不存在
     （`ExecutorHelper` 提交匿名 `Callable`，BL-N1）。
   - **提交点 try/catch 只作附带一致化**：`ExecutorHelper` 的 3 个 CF 入口在 `executor.submit(...)` 抛 REE 时
     该 future 是**方法局部变量、调用方拿不到**——因此 `completeExceptionally` 对直接调用方**零可观测变化**。
     design 明确：**该点不作为任何 Exit Criteria 的验收面**（仅防止 future 泄漏，属卫生性改动）。
   - **契约变化（draft review R3 MJ-6，显式声明）**：destroy 时**在飞任务**的 future 也会被提前
     `completeExceptionally`，任务随后成功的结果被丢弃（`CompletableFuture.complete*` 幂等，静默失败）。
     这是「即时停机保证 future 有终态」的**取舍**，不是缺陷；载体 `pin6001d` 断言该语义。
   - **拒绝方案**：把「提交点结算」当作主修复（不可观测，同 2547 E-2547-3 的形态错误）；依赖
     `shutdownNow()` 返回值 wrapper 识别（载体不存在）；只改 `DefaultThreadPoolExecutor`（它不命中）。
   - **C4-002/C6-009 的关系**：2547 已给 `DefaultThreadPoolExecutor.destroy()` 加了有界等待（`shutdown` +
     `awaitTermination`）；`DefaultScheduledExecutor` 仍是裸 `shutdownNow()`。本批的终态化补偿是其**等价替代**
     （不排空但保证 future 有终态），不引入有界等待（避免改变 timer 的即时停机语义）。

2. **裁定 B（全局池：destroy 回调回注册表 + 平台级 destroyAll）**：成员 C4-005、C6-003。
   - **真实缺陷（draft review R1 B-4 勘正）**：`GlobalExecutors` **没有** `destroy()`——调用方是
     **借用方直接调 `executor.destroy()`**（`IThreadPoolExecutor extends IDestroyable`），注册表无法拦截；
     之后 `g_executors` 仍持有死实例，后续 `getExecutor(name)` 返回已销毁对象（use-after-destroy）。
     `register()` 是 `put` + `old.destroy()`——**这也是缺陷形态**（顶替时不 drain），不是正确先例。
   - **选定形态**：在 `GlobalExecutors` **各池 getter 保持原语义**的前提下，新增
     **`destroyAndUnregister(String name)`** 作为唯一受支持的平台池销毁入口（`unregister` + `destroy` 原子序）；
     并**显式登记 residual**：借用方绕过该入口直接 `destroy()` 仍会使注册表持有死实例（登记为
     watch-only + owner doc 声明「平台池不得由借用方直接 destroy」）。
   - **拒绝方案**：(a) 分发点返回不可销毁包装——破坏 `IThreadPoolExecutor` 语义（调用方需要 destroy 自家池）；
     (b) 清空整个 map（误伤其他池）；(c) 让 `register` 承担 drain（改变既有热更语义）。
   - **`destroyAll()`**：遍历 `g_executors` → `unregister` + `destroy`，供宿主关闭；**接线 = roadmap 8.55（R4-15）**
     （见 Deferred，非本批）。

3. **裁定 C（指标绑定注销对称）**：`ExecutorHelper.registerMetrics` / `LocalCache.registerMetrics` **返回
   `AutoCloseable` 句柄**（唯一形态，不留「或记录 Meter 列表」的二义），由池 / LocalCache 实例字段持有，
   `destroy()` 时 `close()` 注销。成员：C6-004。
   - 现状：`registerMetrics` 绑完就忘，池替换后旧 meter 永久滞留 + 新实例静默失去指标。
   - `"tenant-"+name` 同名 meter 去重列为本组子面（G4）。
   - **注意**：这是 R4-13 C8-008 Deferred 的「换 registry 后重绑缺失」同族面——本批只做**池 destroy 时注销**，不做「换 registry 自动重绑」（后者归 R-G）。

4. **裁定 D（缓存 destroyOnRemove 断链族）**：`CacheConfig` 拷贝构造器补齐 `destroyOnRemove`/`refreshAfterWrite`；`ResourceLoadingCache` / `TenantAwareResourceLoadingCache` / 模型缓存驱逐路径执行销毁回调。成员：C5-003、C6-005、C6-012。
   - C5-003 现状：`CacheConfig(CacheConfig)` 漏拷两字段 → `destroyOnRemove` 在克隆路径恒 false。
   - C6-005：`ResourceLoadingCache` 驱逐/关闭路径上模型对象的 `destroy` 结构性不可达。
   - C6-012：`TenantAwareResourceLoadingCache.tenantCaches` 容器未开 `destroyOnRemove` 且值类型不触发。

5. **裁定 E（阻塞等待：可中断/可配置上界）**：成员 C7-010、C7-015、C7-002。
   - **C7-010**（`ExecutorHelper.throttleExecute`）：`semaphore.acquireUninterruptibly()` 改
     `tryAcquire(timeoutMs)` 循环 + 检查中断；**签名不变**（超时值取新增配置键
     `nop.commons.concurrent.throttle-acquire-timeout-ms`，缺省 60s；`<=0` = 无限，保持现语义）；
     超时抛 `NopException(ERR_CONCURRENT_THROTTLE_ACQUIRE_TIMEOUT)`；中断时恢复标志并抛
     `NopException`（该方法签名不声明受检异常）。**3 个消费方**（`PageProvider:99,133`、
     `WebPageExporter:120`）全部纳入 §五 回归。
   - **C7-015（重写，draft review R1 B-6 勘正）**：`IRateLimiter.tryAcquire(int,long)` **已存在**
     （`IRateLimiter.java:23`）——**没有「增接口」这件事**。真实可做面两项：
     (i) `acquire()/acquire(permits)` 的默认实现由 `tryAcquire(permits, Long.MAX_VALUE)` 改为
     **可配置有限上界**（新键 `nop.commons.concurrent.rate-limit-acquire-timeout-ms`，缺省 5min；
     `<=0` = 无限保持现语义），超时抛 `NopException(ERR_CONCURRENT_RATE_LIMIT_ACQUIRE_TIMEOUT)`；
     (ii) **Guava `sleepMicrosUninterruptibly` 的不可中断面**（`DefaultRateLimiter.tryAcquire` 内部）——
     裁定为 **watch-only residual + 调用方超时护栏**（不改 Guava 内部，理由：`DefaultRateLimiter` 是
     Guava `RateLimiter` 的薄包装，替换实现属设施重写；调用方 `RateLimitConsumer`/`RateLimitTaskStepWrapper`
     已用 `tryAcquire(permits, 20min)` 给出上界，20 分钟内不可中断是可接受残余）。
   - **C7-002**（`scheduleAtFixedRate` 错过补偿）：裁定为 **Decision（声明面）**，不改行为——
     `ScheduledThreadPoolExecutor` 的 fixed-rate 错过补偿是 JDK 原生语义，改它会改变 `nop-stream` 的
     5 处周期任务节奏。产出 = `DefaultScheduledExecutor.scheduleAtFixedRate` javadoc + owner doc
     显式声明「延迟后按 fixed-rate 语义补偿（可能突发）」。**item type = Decision，非 Fix**。

6. **裁定 F（监听/注册表生命周期 + 非 daemon 线程）**：成员 C6-006（`FileWatchEntry` unwatch 不摘父 subEntries → 单调累积；同路径重复 watch 共享 `WatchKey` 导致注销交叉误伤）、C6-007（`NioFileWatchService` 等非 daemon 线程阻止 JVM 退出 + 关闭路径缺 `awaitTermination`）、C7-008（`ResourceDependsManager.dependencyMap` 无逐条淘汰）、C7-011（`CycleSynchronizer` 状态机死区）、C4-011（`ThreadPoolConfig` 5 字段死旋钮）、C4-012（nop-virtual-thread 误导性开关）、C9-004（`XLangSemantics` 每次 `Class.forName`）。
   - **C7-011（重写，draft review R1 M-2 勘正）**：`restart()` **已经是逃生门**（live `CycleSynchronizer.java:143-145`，
     经 `resolvePromise(false, snapshotGeneration())` → `processing=false` + generation 守卫）。audit 原文是
     「**提供了 `restart()` 解除入口但无自动触发**」。本批唯一形态 = **声明面 + 观测**：
     在 `beginCycle` 的抛错路径与类 javadoc 中显式写明「推进者死亡时由外部调用 `restart()` 解除；
     本类不提供自动超时清理（无时钟依赖，且自动清理会与 2547 的 generation 语义冲突）」。
     **显式拒绝**「超时自动清理」（理由如上）。**C1-010 的既有钉 `pin0110` 必须列入不回退清单**。
   - C4-011 死旋钮：`threadPriority`/`allowCoreThreadTimeOut` 全仓零消费者 → **删除这两个字段**并在 owner doc
     声明。**注意 `ThreadPoolConfig` 是 `@ConfigBean`，删字段属契约变更**，已在 §五 声明（删字段，非接线）。
   - C4-012 四面处置（draft review R1 M-8 拆分 + R3 MJ-4 补面）：
     **(a) Fix** — `Jdk21CommonsCoreInitializer` 补 `destroy()`：`GlobalExecutors.unregister(NOP_VIRTUAL_THREAD)`
     返回非 null 时才 destroy（未注册时不 NPE）。
     **(b) Decision** — 默认值 `true→false` **不改**（audit 定为治理面非故障面），owner doc 声明。
     **(c) Fix** — `VirtualThreadTaskExecutor.getConfig()` 返回**真实** `ThreadPoolConfig`（现返回构造的假参数实体）；
     `stats()` 若无法给出真实数据则返回零值 `ThreadPoolStats` 并在 javadoc 注明「虚拟线程池不适用」。
     二者择一，G15 中须写明选择。
     **(d) Decision** — `refreshConfig()` 保持空实现，owner doc 声明「虚拟线程池无容量旋钮，热更为 no-op」（不给假承诺）。
     四面**分项验收**，不混在一项。
   - C9-004：`XLangSemantics.resolveStaticMethodCollection` 每次 `Class.forName` → **新增独立缓存**
     `Map<String, IMethodModelCollection>`（key = `className + '\0' + funcName`）。**不复用** `CLASS_MODELS`
     （其值类型只到 `IClassModel`，`join` 会引入阻塞）。见 §三。

## 二、背景与动机

R4-14 的 17 成员共同形态：**平台设施在「销毁/驱逐/等待」三个动作上缺少对称性与出口**。R4-13 修的是「拿锁/调度/提交」的纪律，R4-14 修的是**它们的收尾**——两者是同一系统生命周期的两端。本批 **17 项全 P2**（C6-001 已由权威台账降级 P1→P2，理由见 E-2548-1），C6-001 仍是本批**最先实施**项（依赖最少），其伤害形态为「future 无终态 → 调用方挂到自身超时」。

成员按裁定分组：

| 裁定 | 成员 | 数量 | 判级 |
|---|---|---|---|
| A destroy 侧终态化 | C6-001 | 1 | **P2** |
| B 全局池生命周期 | C4-005、C6-003 | 2 | P2 |
| C 指标注销对称 | C6-004 | 1 | P2 |
| D 缓存销毁链 | C5-003、C6-005、C6-012 | 3 | P2 |
| E 阻塞等待可中断/有界 | C7-010、C7-015、C7-002 | 3 | P2 |
| F 监听/注册表/死旋钮 | C4-011、C4-012、C6-006、C6-007、C7-008、C7-011、C9-004 | 7 | P2 |
| **合计** | | **17** | |

## 三、逐成员裁定表（以**方法名**为锚，行号仅作提示；draft review R1 M-12 后重锚）

| Finding | 文件 / 方法（方法名锚） | 唯一形态 | 拒绝方案 |
|---|---|---|---|
| **C6-001**（P2） | `ExecutorHelper.submitTracked/submitTracked/scheduleTracked`（**新方法名**）；`DefaultScheduledExecutor.destroy()` / `submit` / `schedule`；`BindScheduledExecutor.schedule` / `submit` | **待结算登记表**（完整形态见 §四.1）：新增 `*Tracked` 独立方法名（**不用同 arity 重载**）；`DefaultScheduledExecutor.destroy()` 顺序 = **periodicTasks 拒绝循环 → `shutdownNow()` → pending 逐项 `completeExceptionally(ERR_SERVICE_NOT_ACTIVE)`**；`BindScheduledExecutor` 补 inner→outer 传播（适用边界见 §四.1）。提交点 try/catch 仅卫生性（**不作 Exit Criteria**） | 提交点结算当主修复（不可观测）；对 `shutdownNow()` 返回值识别 wrapper（**live 无该载体**，BL-N1）；只改 DTE（不命中） |
| C4-005 | `GlobalExecutors`（`getExecutor`/`cachedThreadPool`/`globalWorker`/`globalTimer` + `register`/`unregister`/`allExecutors`） | 新增 `destroyAndUnregister(String)`（`unregister` + `destroy` 原子序）；**residual 登记**：借用方直调 `destroy()` 仍会留下死实例 | 分发点不可销毁包装（破坏语义）；清空整 map |
| C4-011 | `ThreadPoolConfig`（`threadPriority` / `allowCoreThreadTimeOut` 两字段 + 对应 getter/setter） | **删除**这两个字段与访问器（全仓零 Java 消费者、零 yaml/xml/properties 配置）；**契约变更声明**：`ThreadPoolConfig` 是 `@ConfigBean` + `Serializable`，删除属公共属性/二进制面变更 → §五 增「全仓 grep 编译级回归 + 序列化兼容声明」；「分支丢弃」的其余 3 参数**显式 out-of-scope** | 接线（无消费者，白做）；保留但 `@Deprecated`+WARN（仍留有假旋钮，用户以为生效） |
| C4-012 | `CommonConfigs.CFG_ENABLE_VIRTUAL_THREAD_POOL`；`Jdk21CommonsCoreInitializer`；`VirtualThreadTaskExecutor`（`getConfig` / `stats` / `refreshConfig`） | 四面分项：(a) **Fix** `Jdk21CommonsCoreInitializer.destroy()` → `unregister` 非 null 时 destroy；(b) **Decision** 默认值不翻转 + owner doc；(c) **Fix** `getConfig()` 返回真实 config / `stats()` 返回零值或真实统计（二选一，G15 写明）；(d) **Decision** `refreshConfig()` 空实现 + javadoc「no-op」 | 混在一项（不可验收）；只修 (a) 而丢弃 (c)/(d)（假参数实体仍在） |
| C5-003 | `CacheConfig` 拷贝构造 | 补 `destroyOnRemove` / `refreshAfterWrite` 拷贝；`executor`（transient）**不拷贝**并注明理由 | 无 |
| C6-003 | `GlobalExecutors` | 新增 `destroyAll()`（遍历 unregister+destroy）；**宿主接线 = roadmap 8.55（R4-15）**（见 Deferred） | 只做 API 不测；本批接线 nop-boot |
| C6-004 | `ExecutorHelper.registerMetrics`；`LocalCache.registerMetrics` | 定为唯一形态：`registerMetrics` 返回 **`AutoCloseable` 句柄**，由池/LocalCache 实例字段持有，`destroy()` 时 `close()`；`"tenant-"+name` 同名 meter 去重**列入 G4 子面** | 「或记录 Meter 列表」二义；换 registry 重绑（R-G） |
| C6-005 | `ResourceLoadingCache.createCache()` / `destroy()`；`ResourceCacheEntry`（已 `implements IDestroyable`）；`LocalCache.destroy()` | ① `createCache` 加 `config.setDestroyOnRemove(true)`；② `ResourceLoadingCache` **实现 `IDestroyable`**（`destroy() = cache.invalidateAll() + cache.cleanUp()`），使缓存关闭/驱逐路径执行 entry 的 `destroy()`；③ `LocalCache.destroy()` 改 `invalidateAll() + cleanUp()`（现为仅 `cleanUp()`，会跳过 `destroyOnRemove` 回调） | 只改 `LocalCache.destroy`（entry 层不触发）；只加 `destroyOnRemove` 而不给 RLC 销毁语义（假阳性） |
| C6-012 | `TenantAwareResourceLoadingCache`（`tenantCaches` 容器构造） | ① 容器 `CacheConfig` 显式 `setDestroyOnRemove(true)`；② 值类型 `ResourceLoadingCache` **实现 `IDestroyable`**（同 C6-005 ②，故 G7 依赖 G6 的 ②）→ 租户缓存被驱逐/清理时其内部 entry 获得销毁回调 | 只补容器 `destroyOnRemove` 而不给 RLC 加销毁语义（audit `C6-round2.md:238` 明示为「看似已修、实际 no-op」的假阳性） |
| C6-006 | `FileWatchEntry`（`addSubEntry`）；`NioFileWatchService`（`watch` / `unwatch` / `checkChange`） | ① `FileWatchEntry` 增 `parent` 字段，`watch()` 与 `checkChange` 的 ENTRY_CREATE 分支传入；`unwatch` 递归时回写父的 `subEntries.remove`；② 同路径重复 `watch` **复用同一 entry 并返回既有 cleanup 句柄**（旧句柄仍有效、幂等） | 安全反注册旧 entry（会产生交叉误伤）；fail-fast（破坏既有调用方） |
| C6-007 | `NioFileWatchService`（`doStart`/`doStop`）；`ExecutorHelper.newSingleThreadExecutor` 调用点 | 线程 daemon 化 + `doStop` 增有界 `awaitTermination(5s)`；**载体可观测路径（R3 MJ-8）**：`executor` 为 private 无 getter → 测试经**线程名扫描** `Thread.getAllStackTraces()` 过滤 `nio-file-watch-service` 前缀并断言 `isDaemon()`；`pin6007b` 用「`doStop()` 返回耗时 < 5s + 线程名消失」断言有界关闭。**语义变更登记**：JVM 退出不再等待 watcher | 裸 `shutdown()`（现状）；加 getter 仅为测试（污染 API） |
| C7-002 | `DefaultScheduledExecutor.scheduleAtFixedRate` | **Decision（声明面 + 行为观测）**：javadoc + owner doc 声明「fixed-rate 错过补偿是 JDK 原生语义」；不改行为。**载体 = 行为观测**（单线程 timer、周期 50ms、任务体 sleep 200ms × 3 轮 → 第 2/3 轮 start 不晚于理论时刻 + 一个周期），**不用 `_content` 文本断言** | 改不补偿（影响 nop-stream 5 处节奏） |
| C7-008 | `ResourceDependsManager`（`dependencyMap` 及相关方法） | `removeDependency(path)` API + 容量水位告警（10000）；**E-2548-2 勘正**：entry 失效回调**不**自动淘汰（会断开失效重载链，实测 3 red），API 供显式清理路径 | 前缀批量清（误伤）；entry.clear 自动淘汰（E-2548-2 功能回归） |
| C7-010 | `ExecutorHelper.throttleExecute` | `tryAcquire(timeout)` 循环 + 中断检查；新配置键缺省 60s；超时 `ERR_CONCURRENT_THROTTLE_ACQUIRE_TIMEOUT` | `acquireUninterruptibly`（现状） |
| C7-011 | `CycleSynchronizer`（`beginCycle` / `restart`） | **Decision（声明面 + 行为观测）**：`beginCycle` 抛错路径与 javadoc 写明「由外部 `restart()` 解除，无自动超时」；**拒绝自动超时**（无时钟 + 与 generation 冲突）。**载体 = `pin7011` 行为观测**（抛错后 `processing=true` → `restart()` → 新周期可 `beginCycle`）+ `pin0110`/`pin0110b` 不回退 | 新增超时自动清理（回退 2547 语义） |
| C7-015 | `IRateLimiter.acquire()` / `acquire(permits)`；`DefaultRateLimiter`（Guava 包装） | (i) `acquire()/acquire(permits)` 默认上界改为可配置有限值（新键缺省 5min，`<=0`=无限）；(ii) Guava 不可中断面 = **watch-only residual + 调用方超时护栏**（不改 Guava 内部） | 「增 tryAcquire(timeout)」（已存在，无事可做）；替换 Guava 实现（设施重写） |
| C9-004 | `XLangSemantics.resolveStaticMethodCollection` | 新增**独立**缓存 `Map<String, IMethodModelCollection>`（key = `className + '\0' + funcName`）；**不**复用 `CLASS_MODELS`（其值类型只到 `IClassModel`，且 `join` 会引入阻塞）；`ClassNotFoundException → null` 语义保持；`Class.forName` → `ClassHelper.forName` 统一 primitive/数组语义。**载体可观测路径（R3 MJ-9 + closure audit Major-3 勘正）**：TCCL 计数方案对平台类名不可达（类已被系统 loader 加载）→ `pin9004a` = 两次解析 `assertSame` 实例同一性（命中缓存 ⇔ 同一实例） | 复用 CLASS_MODELS（只省一半且引入阻塞/异常语义变化）；「类加载计数==1」（对已加载类不可计量） |

## 四、跨成员 / 跨批共享机制

1. **待结算登记表（settlement registry）**（C6-001；BL-N1 后的唯一形态）
   - **方法形态（R3 MJ-11 勘正）**：`ExecutorHelper` 新增**独立方法名** `submitTracked(ExecutorService, Callable, Set)`
     / `submitTracked(ExecutorService, Runnable, T, Set)` / `scheduleTracked(ScheduledThreadPoolExecutor, Callable,
     long, TimeUnit, Set)`。**禁止**用同 arity 重载——`submit(ExecutorService, Callable, Set)` 与既有
     `submit(ExecutorService, Runnable, T)` 同 arity，值兼容 lambda 会产生解析歧义。原三入口签名与行为**零变化**，
     仅新增 try/catch 卫生性（**非验收面**）。
   - **登记协议**：`*Tracked` 内 `pending.add(future)` → `future.whenComplete((r,e) -> pending.remove(future))`；
     `executor.submit/schedule` 抛 REE 时 `pending.remove(future)` 后原样抛出（不泄漏）。`pending == null` 视为
     不登记（调用方可传 null 走原语义）。
   - **`DefaultScheduledExecutor` 接线**：持有一个「尚无终态的已提交 future」集合；`submit/schedule` 全部改走
     `*Tracked` 并传入该集合；`PeriodicTaskHelper` 逐轮 `this.schedule(...)` 因此自动纳管。
   - **`destroy()` 顺序（R3 MJ-5 勘正，顺序是语义的一部分）**：
     1. **先**执行既有 `periodicTasks` 拒绝循环（`guard.rejectChain(ERR_SERVICE_NOT_ACTIVE)`）并 `clear()`——
        这一步让周期链先获得拒绝终态，`PeriodicTaskHelper.watchNativeHandle` 的 `!chain.isDone()` 守卫因此短路，
        **不会**出现「chain.reject + rejectChain」双触发 listener（`TestPeriodicTaskInsulation` 的
        `rejections.size()==1` 得以保持）。
     2. **再** `shutdownNow()`。
     3. **最后**快照 `pending` 逐项 `completeExceptionally(ERR_SERVICE_NOT_ACTIVE)`，然后 `clear()`。
     `completeExceptionally` 幂等，与在飞任务并发安全。
   - **`BindScheduledExecutor`**：`schedule/submit` 内层 future 的异常终态**传播**到外层 future
     （`inner.whenComplete((r,e) -> { if (e != null && !future.isDone()) future.completeExceptionally(e); })`）。
     **适用边界（R3 MJ-13）**：仅当底层 `timer` 能终态化内层 future 时有效；本批保证面 = `DefaultScheduledExecutor`，
     其他 `IScheduledExecutor` 实现（含各 mock timer）不在本批保证范围，在 owner doc 声明。
   - **在飞任务契约（R3 MJ-6）**：destroy 会提前失败在飞任务的 future，任务随后成功的结果被丢弃。
     这是「即时停机 + future 必有终态」的显式取舍。
   - **可观测性（4 载体，均确定性）**：`pin6001`（排队任务 → 异常终态 + cause 类型 + 执行计数 0）、
     `pin6001b`（`DefaultThreadPoolExecutor` 对照：`shutdown` 排空 → 正常完成）、
     `pin6001c`（`BindScheduledExecutor` 外层亦异常完结）、`pin6001d`（在飞任务：destroy 后 future 异常完结）。
   `scheduleAtFixedRate` / `scheduleWithFixedDelay` 不在 `ExecutorHelper`；`scheduleWithRandomDelay`（C7-003）**显式 Deferred**。
2. **`GlobalExecutors` 注册表语义**（C4-005 / C6-003）：现状 `register` = `put` + `old.destroy()`（**也是缺陷形态**，
   顶替时不 drain）；`unregister` 已存在但**不 destroy**；**无 `destroy` / `destroyAll`**。本批新增
   `destroyAndUnregister(String)` 与 `destroyAll()`；借用方直调 `destroy()` 的 use-after-destroy 登记为 residual。
3. **与 R4-13（plan 2547）的分工（重要，防冲突）**：
   - `CycleSynchronizer`：C1-010（旧周期不污染新周期，generation 守卫）**已由 2547 完成**；C7-011（推进者死亡 → processing 永久 true）是本批新面，改的是 `beginCycle` 的**逃生门**，**不得回退 2547 的 generation 语义**。
   - `DefaultThreadPoolExecutor.destroy`：C4-002（有界等待、仅 true&&>0）**已由 2547 完成**；本批 C4-005/C6-003 只管**注册表移除**与**平台级 destroyAll**，不改 destroy 内部的等待逻辑。
   - `XLangSemantics.CLASS_MODELS`：C2-019（占位模式）**已由 2547 完成**；本批 C9-004 **不复用**该缓存，
     而是新增独立 `Map<String, IMethodModelCollection>`（见 §三）——`CLASS_MODELS` 值类型只到 `IClassModel`，
     且其 `join` 语义会引入阻塞，复用会污染 C2-019 的占位契约。
4. **缓存销毁回调链**（C5-003 / C6-005 / C6-012）：三者是「配置漏拷 → 驱逐不触发 → 租户容器不开」的同一条链。
   **但不存在实施依赖**（draft review R1 M-1 勘正）：`CacheConfig.cloneInstance()` 全仓**零生产调用方**，
   因此 C6-005 / C6-012 的载体测试可直接构造 `CacheConfig` 并显式 `setDestroyOnRemove(true)`，**不依赖 C5-003**。
   **唯一的真实依赖在 C6-005 与 C6-012 之间**：C6-012 的租户容器驱逐要触达 entry 的 `destroy()`，前提是
   `ResourceLoadingCache implements IDestroyable`（C6-005 ②）。故实施序 = C5-003 → C6-005（含②）→ C6-012，
   其中 **C6-005 ② 是 C6-012 的硬前提**；C5-003 仅居首为阅读便利。

## 五、回归面清单（PA 授权必附；draft review R1 M-5 后按 live grep 重写）

> 区分「必须跑测试」与「仅编译」。锚点均为 2026-11-09 live grep 值。

| 改动 | 真实消费方（live 锚） | 必跑回归 |
|---|---|---|
| **C6-001 `DefaultScheduledExecutor.destroy` 终态化** | `DefaultScheduledExecutor` 的 IoC bean（destroy-method="destroy"；`nopJobScheduledExecutor`/`nopRpcPollScheduledExecutor`）；`BindScheduledExecutor` 委托方 | `nop-kernel` 全量 + `nop-job`（scheduled executor bean 面） |
| **C4-005 / C6-003 `GlobalExecutors` 新 API** | 全部平台池消费方（`getExecutor`/`cachedThreadPool`/`globalWorker`/`globalTimer`） | `nop-kernel` 全量；`nop-core-framework/nop-boot` **仅编译**（接线归 8.55/R4-15） |
| **C4-011 `ThreadPoolConfig` 删 2 字段** | 全仓 grep 配置键 = 0（已确认无 yaml/xml/properties 配置）；**Java 消费方**：`getThreadPriority`/`setThreadPriority`/`getAllowCoreThreadTimeOut`/`setAllowCoreThreadTimeOut` 全仓调用 = 0（编译级证据）。**契约变更声明**：`ThreadPoolConfig` 为 `@ConfigBean` + `Serializable`，删字段后旧序列化流反序列化会忽略该属性（向后兼容），但**外部二进制依赖该 getter/setter 的代码会编译失败**——本仓无此依赖，跨项目使用方需在升级说明中声明 | `nop-kernel` 全量 + 全仓 `-DskipTests` 编译 |
| **C4-012 虚拟线程 initializer destroy** | `nop-utils/nop-commons-java21`；虚拟线程执行路径 | **`nop-commons-java21` 需先补测试基建（R3 MJ-10）**：`pom.xml` 加 `junit-jupiter` test 依赖 + 新建 `src/test/java`；该模块仅在 `java21-modules` profile 激活（`nop-utils/pom.xml:36-46`），故回归须显式 `-P java21-modules`。门禁 = 该 profile 下 `nop-commons-java21` 测试 + `nop-kernel` 全量 |
| **C5-003 `CacheConfig` 拷贝构造** | **零生产调用方**（`cloneInstance()` 全仓仅定义处） | `nop-commons` 全量 + 直测 clone 往返 |
| **C6-005 `ResourceLoadingCache` + `LocalCache.destroy`** | `nop-core` 模型缓存；`nop-orm` 模型加载；`nop-xdefs` | `nop-core` 全量 + `nop-xdefs` + `nop-orm` 模型面 |
| **C6-012 `TenantAwareResourceLoadingCache`** | `nop-core` 租户缓存 | `nop-core` 全量 |
| **C6-006 `FileWatchEntry`/`NioFileWatchService`** | `nop-core` 资源监听（开发期热加载） | `nop-core` 全量 |
| **C6-007 `NioFileWatchService` daemon + awaitTermination** | 同上 | `nop-core` 全量 |
| **C7-008 `ResourceDependsManager`** | `nop-core` 依赖分析 | `nop-core` 全量 |
| **C7-010 `throttleExecute`** | **3 处**：`nop-frontend-support/nop-web`（`PageProvider:99,133`、`WebPageExporter:120`） | `nop-commons` 全量 + `nop-web` 全量 |
| **C7-015 `IRateLimiter.acquire` 默认上界** | `nop-ai-core`（`ChatServiceImpl:453-461`、`EmbeddingServiceImpl:294`）、`nop-task-core`（`RateLimitTaskStepWrapper:37`）、`nop-batch-core`（`RateLimitConsumer:37`） | `nop-commons` 全量 + `nop-ai-core` + `nop-task-core` + `nop-batch-core` |
| **C7-002 fixed-rate 声明** | **纯文档**；`scheduleAtFixedRate` 生产调用：`nop-stream` 5 处（`TaskManager:461`、`JobCoordinator:741`、`TaskCheckpointWiring:292`、`StreamMetricsReporter:87`、`OpsJobManager:406`）+ `nop-integration-feishu`（`FeishuClient:293`）——**不改行为**，仅编译级 | `nop-stream` / `nop-integration-feishu` **仅编译** |
| **C9-004 `XLangSemantics` 静态方法缓存** | 全平台 XLang 生成代码的静态方法调用 | `nop-xlang` 全量 + `nop-core` |
| **C7-011 声明面** | **零生产调用方**（全仓仅自身） | `nop-commons` 全量 |

**E1 全量门禁**：`./mvnw -o clean install -DskipTests -T 1C -pl '!nop-runner/nop-cli-core,!nop-runner/nop-cli'` BUILD SUCCESS（沿 2547 先例；`nop-js`/`nop-graphql-grpc` 为 pom 注释 module 不可编译，零 diff）。
**E3 分解通道**：`./mvnw test -pl nop-kernel -am`（clean）+ 上表逐条。

## 六、实施顺序

1. **组 1（P2，最先——依赖最少且独立）**：C6-001（destroy 侧终态化）
2. **组 2（池生命周期 + 指标）**：C4-005、C6-003、C6-004
3. **组 3（缓存销毁链）**：C5-003 → C6-005 → C6-012（**C6-005 ② 是 C6-012 的硬前提**；C5-003 居首仅阅读便利，见 §四.4）
4. **组 4（监听/注册表）**：C6-006、C6-007、C7-008
5. **组 5（等待可中断/有界）**：C7-010、C7-015、C7-002
6. **组 6（死旋钮/虚拟线程/类加载缓存）**：C4-011、C4-012、C9-004
7. **组 7（CycleSynchronizer 逃生门，最后——需先复核 2547 语义）**：C7-011

## 七、拒绝方案汇总（内联于 §三 表末列，共 17 条）
