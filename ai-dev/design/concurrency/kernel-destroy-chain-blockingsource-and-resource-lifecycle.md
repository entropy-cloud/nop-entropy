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
> - **`IRateLimiter.tryAcquire(int,long)` 已存在**（`IRateLimiter.java:23`），`acquire()` 默认走 `tryAcquire(1, Long.MAX_VALUE)`——C7-015 的真实可做面见裁定 E（重写）。

## 一、设计结论（六条裁定）

1. **裁定 A（destroy 侧终态化补偿，非提交点结算）**：成员 **C6-001（P2，条件化）**。
   - **真实失效面（draft review R1 B-1/B-3 勘正）**：`DefaultScheduledExecutor.destroy()` 用 `shutdownNow()`
     丢弃**已排队未执行**的任务；这些任务的 wrapper future（由 `ExecutorHelper.submit/schedule` 创建并
     已返回给调用方）**永不完成** → 调用方永久挂起。`DefaultThreadPoolExecutor.destroy()` 用 `shutdown()`
     会排空队列（JDK 契约），**不命中**。
   - **修复形态**：在 `DefaultScheduledExecutor.destroy()` 中，对 `shutdownNow()` 返回的 `List<Runnable>`
     逐项结算——若为可识别的 wrapper（`InsulatedPeriodicTask` / 本类的 task wrapper），补
     `completeExceptionally(ERR_SERVICE_NOT_ACTIVE)`。同类处理 `BindScheduledExecutor`（它委托底层 timer）。
   - **提交点 try/catch 只作附带一致化**：`ExecutorHelper` 的 3 个 CF 入口在 `executor.submit(...)` 抛 REE 时
     该 future 是**方法局部变量、调用方拿不到**——因此 `completeExceptionally` 对直接调用方**零可观测变化**。
     design 明确：**该点不作为任何 Exit Criteria 的验收面**（仅防止 future 泄漏，属卫生性改动）。
   - **拒绝方案**：把「提交点结算」当作主修复（不可观测，同 2547 E-2547-3 的形态错误）；只改
     `DefaultThreadPoolExecutor`（它不命中）。
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
   - **`destroyAll()`**：遍历 `g_executors` → `unregister` + `destroy`，供宿主关闭；**接线 = successor**
     （见 Deferred，非本批）。

3. **裁定 C（指标绑定注销对称）**：`ExecutorHelper.registerMetrics` 返回可注销的句柄（或记录已绑定的 Meter 列表），池 `destroy()` 时注销。成员：C6-004。
   - 现状：`registerMetrics` 绑完就忘，池替换后旧 meter 永久滞留 + 新实例静默失去指标。
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
   - C4-011 死旋钮：`threadPriority`/`allowCoreThreadTimeOut` 等全仓零消费者 → 要么接线，要么**删除字段**并在 owner doc 声明（倾向后者：不给假承诺）。**注意 `ThreadPoolConfig` 是 `@ConfigBean`，删字段属契约变更**，需在 §五 声明。
   - C4-012：`nop-commons-java21` 的虚拟线程开关默认 true 但注册表内是假参数实体、平台 destroy 不回收 → 改默认或显式声明。
   - C9-004：`XLangSemantics` 静态方法调用每次 `Class.forName` → 复用既有 `className → 模型` 缓存（复用 R4-13 C2-019 的 `CLASS_MODELS`）。

## 二、背景与动机

R4-14 的 17 成员共同形态：**平台设施在「销毁/驱逐/等待」三个动作上缺少对称性与出口**。R4-13 修的是「拿锁/调度/提交」的纪律，R4-14 修的是**它们的收尾**——两者是同一系统生命周期的两端。C6-001 是本批唯一 P1，也是整条链上最直接的伤害（future 永不完成 = 调用方永久挂起）。

成员按裁定分组：

| 裁定 | 成员 | 数量 | 判级 |
|---|---|---|---|
| A 提交失败结算 | C6-001 | 1 | **P1** |
| B 全局池生命周期 | C4-005、C6-003 | 2 | P2 |
| C 指标注销对称 | C6-004 | 1 | P2 |
| D 缓存销毁链 | C5-003、C6-005、C6-012 | 3 | P2 |
| E 阻塞等待可中断/有界 | C7-010、C7-015、C7-002 | 3 | P2 |
| F 监听/注册表/死旋钮 | C4-011、C4-012、C6-006、C6-007、C7-008、C7-011、C9-004 | 7 | P2 |
| **合计** | | **17** | |

## 三、逐成员裁定表（以**方法名**为锚，行号仅作提示；draft review R1 M-12 后重锚）

| Finding | 文件 / 方法（方法名锚） | 唯一形态 | 拒绝方案 |
|---|---|---|---|
| **C6-001**（P2） | `DefaultScheduledExecutor.destroy()`；`BindScheduledExecutor`（委托底层 timer） | `destroy()` 对 `shutdownNow()` 返回的排队任务逐项结算 `completeExceptionally(ERR_SERVICE_NOT_ACTIVE)`；`ExecutorHelper` 三入口的提交点 try/catch 仅作卫生性附带（**不作 Exit Criteria**） | 提交点结算当主修复（不可观测）；只改 DTE（不命中） |
| C4-005 | `GlobalExecutors`（`getExecutor`/`cachedThreadPool`/`globalWorker`/`globalTimer` + `register`/`unregister`/`allExecutors`） | 新增 `destroyAndUnregister(String)`（`unregister` + `destroy` 原子序）；**residual 登记**：借用方直调 `destroy()` 仍会留下死实例 | 分发点不可销毁包装（破坏语义）；清空整 map |
| C4-011 | `ThreadPoolConfig`（`threadPriority` / `allowCoreThreadTimeOut` 两字段） | **删除**这两个字段 + owner doc 声明（全仓零消费者、零配置）；「分支丢弃」的其余 3 参数**显式 out-of-scope** | 接线（无消费者，白做） |
| C4-012 | `CommonConfigs.CFG_ENABLE_VIRTUAL_THREAD_POOL`；`Jdk21CommonsCoreInitializer`（无 destroy 覆写） | (a) **Fix**：`Jdk21CommonsCoreInitializer` 增 `destroy()` → `GlobalExecutors.unregister(NOP_VIRTUAL_THREAD)` + destroy； (b) **Decision**：默认值 `true→false` **不改**（audit 定为治理面非故障面），owner doc 声明 | 混在一项（不可验收） |
| C5-003 | `CacheConfig` 拷贝构造 | 补 `destroyOnRemove` / `refreshAfterWrite` 拷贝；`executor`（transient）**不拷贝**并注明理由 | 无 |
| C6-003 | `GlobalExecutors` | 新增 `destroyAll()`（遍历 unregister+destroy）；**宿主接线 = successor**（见 Deferred） | 只做 API 不测；本批接线 nop-boot |
| C6-004 | `ExecutorHelper.registerMetrics`；`LocalCache.registerMetrics` | 定为唯一形态：`registerMetrics` 返回 **`AutoCloseable` 句柄**，由池/LocalCache 实例字段持有，`destroy()` 时 `close()`；`"tenant-"+name` 同名 meter 去重**列入 G4 子面** | 「或记录 Meter 列表」二义；换 registry 重绑（R-G） |
| C6-005 | `ResourceLoadingCache.createCache()`；`LocalCache.destroy()` | `createCache` 加 `destroyOnRemove(true)` + `ComponentCacheEntry implements IDestroyable`；`LocalCache.destroy()` 改 `invalidateAll() + cleanUp()` | 无 |
| C6-006 | `FileWatchEntry`（`addSubEntry`）；`NioFileWatchService`（`watch` / `unwatch` / `checkChange`） | ① `FileWatchEntry` 增 `parent` 字段，`watch()` 与 `checkChange` 的 ENTRY_CREATE 分支传入；`unwatch` 递归时回写父的 `subEntries.remove`；② 同路径重复 `watch` **复用同一 entry 并返回既有 cleanup 句柄**（旧句柄仍有效、幂等） | 安全反注册旧 entry（会产生交叉误伤）；fail-fast（破坏既有调用方） |
| C6-007 | `NioFileWatchService`（`doStart`/`doStop`） | 线程 daemon 化 + `doStop` 增有界 `awaitTermination(5s)`；**语义变更登记**：JVM 退出不再等待 watcher（改为随 JVM 直接退出） | 裸 `shutdown()`（现状） |
| C7-002 | `DefaultScheduledExecutor.scheduleAtFixedRate` | **Decision（声明面）**：javadoc + owner doc 声明「fixed-rate 错过补偿是 JDK 原生语义」；不改行为 | 改不补偿（影响 nop-stream 5 处节奏） |
| C7-008 | `ResourceDependsManager`（`dependencyMap` 及相关方法） | 逐条淘汰：`ResourceCacheEntry` 失效/清理回调里移除该 path 的依赖集；`dependencyMap` 增 `remove(path)` 与容量告警 | 前缀批量清（误伤） |
| C7-010 | `ExecutorHelper.throttleExecute` | `tryAcquire(timeout)` 循环 + 中断检查；新配置键缺省 60s；超时 `ERR_CONCURRENT_THROTTLE_ACQUIRE_TIMEOUT` | `acquireUninterruptibly`（现状） |
| C7-011 | `CycleSynchronizer`（`beginCycle` / `restart`） | **Decision（声明面 + 观测）**：`beginCycle` 抛错路径与 javadoc 写明「由外部 `restart()` 解除，无自动超时」；**拒绝自动超时**（无时钟 + 与 generation 冲突） | 新增超时自动清理（回退 2547 语义） |
| C7-015 | `IRateLimiter.acquire()` / `acquire(permits)`；`DefaultRateLimiter`（Guava 包装） | (i) `acquire()/acquire(permits)` 默认上界改为可配置有限值（新键缺省 5min，`<=0`=无限）；(ii) Guava 不可中断面 = **watch-only residual + 调用方超时护栏**（不改 Guava 内部） | 「增 tryAcquire(timeout)」（已存在，无事可做）；替换 Guava 实现（设施重写） |
| C9-004 | `XLangSemantics.resolveStaticMethodCollection` | 新增**独立**缓存 `Map<String, IMethodModelCollection>`（key = `className + '\0' + funcName`）；**不**复用 `CLASS_MODELS`（其值类型只到 `IClassModel`，且 `join` 会引入阻塞）；`ClassNotFoundException → null` 语义保持；`Class.forName` → `ClassHelper.forName` 统一 primitive/数组语义 | 复用 CLASS_MODELS（只省一半且引入阻塞/异常语义变化） |

## 四、跨成员 / 跨批共享机制

1. **`ExecutorHelper` 五入口同形态**（C6-001）：五个提交入口必须**同 diff** 加结算，禁止只修 `submit(Callable)`。参照 `nop-batch` 的 `C6-002`（提交点完成兜底）先例。
2. **`GlobalExecutors` 注册表语义**（C4-005 / C6-003）：`register`（已正确：put + destroy old）、`unregister`（已存在）、`destroy`（**本批新增移除语义**）、`destroyAll`（本批新增）四者必须一致——注册表条目生命周期 = 实例生命周期。
3. **与 R4-13（plan 2547）的分工（重要，防冲突）**：
   - `CycleSynchronizer`：C1-010（旧周期不污染新周期，generation 守卫）**已由 2547 完成**；C7-011（推进者死亡 → processing 永久 true）是本批新面，改的是 `beginCycle` 的**逃生门**，**不得回退 2547 的 generation 语义**。
   - `DefaultThreadPoolExecutor.destroy`：C4-002（有界等待、仅 true&&>0）**已由 2547 完成**；本批 C4-005/C6-003 只管**注册表移除**与**平台级 destroyAll**，不改 destroy 内部的等待逻辑。
   - `XLangSemantics.CLASS_MODELS`：C2-019（占位模式）**已由 2547 完成**；本批 C9-004 只是**复用该缓存**，不重建。
4. **缓存销毁回调链**（C5-003 / C6-005 / C6-012）：三者是「配置漏拷 → 驱逐不触发 → 租户容器不开」的同一条链，必须**同批按依赖序**修（C5-003 先，否则 C6-005/C6-012 即使改对也不会被触发）。

## 五、回归面清单（PA 授权必附；draft review R1 M-5 后按 live grep 重写）

> 区分「必须跑测试」与「仅编译」。锚点均为 2026-11-09 live grep 值。

| 改动 | 真实消费方（live 锚） | 必跑回归 |
|---|---|---|
| **C6-001 `DefaultScheduledExecutor.destroy` 终态化** | `DefaultScheduledExecutor` 的 IoC bean（destroy-method="destroy"；`nopJobScheduledExecutor`/`nopRpcPollScheduledExecutor`）；`BindScheduledExecutor` 委托方 | `nop-kernel` 全量 + `nop-job`（scheduled executor bean 面） |
| **C4-005 / C6-003 `GlobalExecutors` 新 API** | 全部平台池消费方（`getExecutor`/`cachedThreadPool`/`globalWorker`/`globalTimer`） | `nop-kernel` 全量；`nop-core-framework/nop-boot` **仅编译**（接线归 successor） |
| **C4-011 `ThreadPoolConfig` 删 2 字段** | 全仓 grep 配置键 = 0（已确认无 yaml/xml/properties 配置） | `nop-kernel` 全量 |
| **C4-012 虚拟线程 initializer destroy** | `nop-utils/nop-commons-java21`；虚拟线程执行路径 | `nop-commons-java21` 编译 + `nop-kernel` 全量 |
| **C5-003 `CacheConfig` 拷贝构造** | **零生产调用方**（`cloneInstance()` 全仓仅定义处） | `nop-commons` 全量 + 直测 clone 往返 |
| **C6-005 `ResourceLoadingCache` + `LocalCache.destroy`** | `nop-core` 模型缓存；`nop-orm` 模型加载；`nop-xdefs` | `nop-core` 全量 + `nop-xdefs` + `nop-orm` 模型面 |
| **C6-012 `TenantAwareResourceLoadingCache`** | `nop-core` 租户缓存 | `nop-core` 全量 |
| **C6-006 `FileWatchEntry`/`NioFileWatchService`** | `nop-core` 资源监听（开发期热加载） | `nop-core` 全量 |
| **C6-007 `NioFileWatchService` daemon + awaitTermination** | 同上 | `nop-core` 全量 |
| **C7-008 `ResourceDependsManager`** | `nop-core` 依赖分析 | `nop-core` 全量 |
| **C7-010 `throttleExecute`** | **3 处**：`nop-frontend-support/nop-web`（`PageProvider:99,133`、`WebPageExporter:120`） | `nop-commons` 全量 + `nop-web` 全量 |
| **C7-015 `IRateLimiter.acquire` 默认上界** | `nop-ai-core`（`ChatServiceImpl:453-461`、`EmbeddingServiceImpl:288`）、`nop-task-core`（`RateLimitTaskStepWrapper:37`）、`nop-batch-core`（`RateLimitConsumer:37`） | `nop-commons` 全量 + `nop-ai-core` + `nop-task-core` + `nop-batch-core` |
| **C7-002 fixed-rate 声明** | **纯文档**；`scheduleAtFixedRate` 生产调用：`nop-stream` 5 处（`TaskManager:461`、`JobCoordinator:741`、`TaskCheckpointWiring:292`、`StreamMetricsReporter:87`、`OpsJobManager:406`）+ `nop-integration-feishu`（`FeishuClient:293`）——**不改行为**，仅编译级 | `nop-stream` / `nop-integration-feishu` **仅编译** |
| **C9-004 `XLangSemantics` 静态方法缓存** | 全平台 XLang 生成代码的静态方法调用 | `nop-xlang` 全量 + `nop-core` |
| **C7-011 声明面** | **零生产调用方**（全仓仅自身） | `nop-commons` 全量 |

**E1 全量门禁**：`./mvnw -o clean install -DskipTests -T 1C -pl '!nop-runner/nop-cli-core,!nop-runner/nop-cli'` BUILD SUCCESS（沿 2547 先例；`nop-js`/`nop-graphql-grpc` 为 pom 注释 module 不可编译，零 diff）。
**E3 分解通道**：`./mvnw test -pl nop-kernel -am`（clean）+ 上表逐条。

## 六、实施顺序（依赖驱动）

1. **组 1（P1，最先）**：C6-001（五入口结算）
2. **组 2（池生命周期 + 指标）**：C4-005、C6-003、C6-004
3. **组 3（缓存销毁链，依赖序）**：C5-003 → C6-005 → C6-012
4. **组 4（监听/注册表）**：C6-006、C6-007、C7-008
5. **组 5（等待可中断/有界）**：C7-010、C7-015、C7-002
6. **组 6（死旋钮/虚拟线程/类加载缓存）**：C4-011、C4-012、C9-004
7. **组 7（CycleSynchronizer 逃生门，最后——需先复核 2547 语义）**：C7-011

## 七、拒绝方案汇总（内联于 §三 表末列，共 17 条）
