# nop-kernel 锁纪律 / 调度器 / destroy 语义（R4-13 前半，G27 前半 18 成员）

> Status: active
> Last Reviewed: 2026-11-09
> Source: plan 2547（`ai-dev/plans/2547-cra-phase8.53-r4-13-kernel-lock-scheduler-destroy-a.md`）Phase 1；批次定义 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二 R4-13 行（18 finding ID / nop-kernel / PA=框架核心引擎 plan-first）；成员发现 = `ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/`（C1-shared-mutable-state.md / C2-lock-sync.md / C2-round2.md / C3-async-chains.md / C3-round2.md / C4-thread-pools.md）
> Protected Area 授权：本批触及 `nop-kernel/nop-commons`、`nop-kernel/nop-core`、`nop-kernel/nop-xlang` = AGENTS.md「框架核心引擎（nop-core/nop-xlang/nop-xdefs 内部）」= **plan-first**。授权载体 = 本文档 + 独立 draft review + 回归面清单（§五）；未过 review 不得改码。
> 传导先例：[pool-facility-hardening.md](pool-facility-hardening.md)（池设施硬化裁定与兼容矩阵）、[kernel-retirement-generation-and-model-load-locking.md](kernel-retirement-generation-and-model-load-locking.md)（R2-01 退役代际族，C1-010/C2-004 的邻接先例）
>
> **基线勘误 E-2547-1（draft review R1 B-1）**：本批原 18 成员中 **C3-002 已在 plan 2496（提交 `646673a90e`）修复落地**——`BindScheduledExecutor.scheduleWithFixedDelay` 现委托 `PeriodicTaskHelper.scheduleWithFixedDelay`，其 `scheduleRound`/`dispatchRound` 均以 `try/catch(Throwable) → guard.rejectChain(e)` 收口。本批将其改列为 **already-landed 证据项**（不计入 Fix 工单数），有效实施工单为 **17 项**。

## 一、设计结论（八条裁定）

1. **裁定 A（互斥原语优先，统一 compute 纪律）**：凡「读-判-改」跨多步的 `ConcurrentHashMap` 复合操作，一律改 `compute`/`computeIfAbsent` 单原子域，禁止裸 `get` + 后续 `put/remove`。成员：C1-004（`RateLimitExecutorImpl.debounce`）、C2-003（`ResourceComponentManager` transform 锁内重活）、C2-019（`XLangSemantics.CLASS_MODELS` 锁内类加载）、C2-008（`LocalResourceLockManager` 过期判定绕过 latch）。
   - **但 compute 内禁重活**：C2-003/C2-019 的 `computeIfAbsent` 内执行编译/类加载，属「锁内重活」——compute 只做**占位**（`CompletableFuture`），构建在锁外执行后 `complete`/`completeExceptionally`。
   - **占位模式的唯一正确形态（draft review R1 B-4 勘正）**：**必须用 `boolean[] created` 或 `AtomicBoolean` 判定「是否本次新建」**——`ConcurrentHashMap.computeIfAbsent` 的返回值**无法区分**"既有"与"新建"（原设计并列写「返回值身份判定」是错的，照此实现会导致无人 `complete`、该 key 永久挂起）。
     - 字段值类型须由 `Map<K, V>` 改为 `Map<K, CompletableFuture<V>>`（`ComponentCacheEntry.transformed`、`XLangSemantics.CLASS_MODELS` 两处）；
     - 消费点 `join` 后必须把 `ExecutionException` 的 cause 解包并转 `NopException`（与既有错误码语义一致）。
   - **拒绝方案**：`computeIfAbsent` 内直接构建（违反 CHM 契约，compute 内再入同 map 可能死锁/`IllegalStateException`）；全局 `synchronized`（串行化）；**「返回值身份判定」**（语义上不可行，见上）。

2. **裁定 B（中断语义透传，采用「不破坏源兼容」的米策略）**：成员 C2-007。
   - **真实缺陷点**（draft review R1 B-2 勘正）：中断转 `NopException` 的位置是 **`LocalResourceLockManager.tryLockWithLease:182-184`**（`catch (InterruptedException e) { Thread.currentThread().interrupt(); throw NopException.adapt(e); }`），**不是** `AbstractResourceLock`（该类只有 `lock/lockInterruptibly/tryLock/tryLock(time,unit)` 四个方法，无 `tryLockWithLease`）。
   - **裁定：米策略（最小源破坏）**——`tryLockWithLease` 的签名**不变**（仍不抛受检异常，避免全仓 19 处测试 + nop-sys-dao 双实现改造），但：
     - `LocalResourceLockManager` 捕获中断时**保留 cause**（`NopException.adapt(e)` 天然带 cause，需确认并加断言）；
     - `AbstractResourceLock.lockInterruptibly()` 与 `tryLock(long, TimeUnit)` 内识别 `e.getCause() instanceof InterruptedException` → **重抛为 `InterruptedException`**（这两处方法本身可 `throws`，且全仓 `lockInterruptibly` 零调用点、`tryLock(time,unit)` 调用面窄）。
   - **拒绝方案（原设计）**：让 `tryLockWithLease` 抛受检 `InterruptedException` —— 会打断 `IResourceLock`/`IResourceLockManagerImplementor` 两接口 + `LocalResourceLockManager`/**`SysDaoResourceLockManager`(nop-sys-dao)** 两实现 + `IResourceLock.tryLockAndExecute` 默认方法 + 19 处测试调用。收益（更强的编译期契约）不抵风险（跨模块编译断 + PA 批次扩散）。
   - **`unlock` 纪律统一**：`ResourceLock.unlock`（synchronized）与 `ResourceMultiLock.unlock`（非同步）对齐为 `synchronized`。
   - **`LocalFileLock` 不在本传播链**（它 `implements java.util.concurrent.locks.Lock` 且已正确声明 `throws InterruptedException`）——其缺陷在 C1-011 的句柄会话，见裁定 H。

3. **裁定 C（回调出锁域 + 逐回调隔离）**：`CycleSynchronizer`（C1-010 + C2-004 同站）—— ① 回调列表**快照后在监视器外**执行（消除持 `this` 锁跑任意回调的锁域扩展、自死锁、跨实例锁序环）；② 每个回调**独立 try/catch**（一个回调抛异常不得中断后续、不得以异常完成 promise）；③ 引入单调 `cycleGeneration`，旧周期 `finish` 不得清掉新周期状态。
   - **次序裁定**：先 `promise.complete(...)` 再跑回调（同时消除「回调内 await 本周期 promise」的自死锁面）。
   - **拒绝方案**：把回调改成异步 submit（改变既有同步语义，调用方依赖「回调完成后 finish 返回」）。

4. **裁定 D（destroy 语义：配置项生效 + 有界等待，但缺省行为不变）**：成员 C4-002。
   - **配置项生效**：`waitForTasksToCompleteOnShutdown`/`awaitTerminationSeconds` 从死值变为生效（复用 `ThreadHelper.shutdownAndAwaitTermination`）。
   - **关键语义边界（draft review R1 M-2）**：**仅当 `waitForTasksToCompleteOnShutdown==true && awaitTerminationSeconds>0` 才走有界等待**；否则保持现有 `shutdown()` 语义（否则 `awaitTerminationSeconds=0` 会退化为 `shutdown + 立即 shutdownNow`，**比现状更激进地中断在飞任务**）。
   - **真实缺陷点（draft review R1 M-1 勘正）**：`DefaultThreadPoolExecutor.destroy()` 的缺陷是 **`init()` 未调用时 NPE**（缺 null 守卫）；`destroy` 后 `stats()` **本就不会 NPE**（`destroy` 只 `shutdown()`、不置 `executor=null`，`ThreadPoolExecutor` 的 getter 在 shutdown 后仍可用）。`DefaultScheduledExecutor` 的 `destroy` 后 `stats()` 已由 C1-003 用 `snapshotExecutor()` 改为 `NopException`（**already-landed**，本批不重做）。
   - **拒绝方案**：两类池保持语义互斥（文档声明差异）——`defaultWorker`/`globalWorker`/cached/timer 四类池的消费方均假设「destroy 后不再执行」，保留互斥会持续制造「关停后仍在跑」的观测缺口。

5. **裁定 E（提交/拒绝必须显式，不得静默丢弃）**：成员 C3-008（`SequentialTaskExecutor.execute` 忽略 offer 返回值）、C3-011（`BlockingSourceConsumeTask` 循环死亡不可感知）、C3-012（`AbstractBatchProcessService.destroy` 丢弃队列）、C3-018（`DefaultTaskExecutionQueue` 取消绑定参数反接）、C3-002（`BindScheduledExecutor` 重排失败 future 永不完成）。
   - C3-008：`execute` 检查 offer 返回值，false 时按 `QueueOverflowPolicy` 处理（block/throw），至少 `LOG.warn` + 计数；**禁止静默丢任务**。
   - C3-011：循环体最外层 `catch (Throwable)` + ERROR 日志 + `continue`（仅 cancel/中断退出）；保存 submit future 并在死亡时告警。
   - C3-012（**次序已按 draft review R1 B-5 勘正**）：原设计「cancel 后等 `isAllProcessed`」**逻辑不成立**——`BlockingSourceConsumeTask.run` 是 `do { drainTo } while (!isCancelled())`，cancel 后下一轮即退出、不再 drain，`isAllProcessed` 永不真。正确次序 = **① 置「停止接收」位（拒绝新入队）→ ② 让消费循环继续 drain 至队列空（`isAllProcessed` 变真）→ ③ 再 `task.cancel()` → ④ 销毁 executor**。若超时未 drain 完，则 `drainTo(list)` 显式取残量并**逐条记录丢弃清单**后告警（`IBlockingQueue` 无快照 API，只有 `size/isEmpty/poll/take/clear`，故用 drain）。
   - C3-018：`bindCancel(state.getPromise(), future)` 对齐注释语义；`whenComplete` 按 `state.isCancelled()` 分流 `cancelled++`（而非 `completed++`）。
   - C3-002：重排调用纳入 `try/catch(Throwable)`，失败时 `future.completeExceptionally(e)`。
   - **拒绝方案**：把 C3-013（异步上下文卫生，审计自注「理论风险未具体化」）与 C3-009/C3-010（缓存语义分叉/executor 固化）升格为行为改造——三者均为**声明/配置面**，按裁定 F 处理。

6. **裁定 F（语义分叉只做声明或配置化，不做行为重写）**：
   - C3-009（`IAsyncCache.getAsync` vs `getAllAsync` 语义分叉）：**性质 = 公共契约变更（draft review R1 M-9）**，不是普通文档项——`IAsyncCache.getAllAsync` 的 javadoc 现明确承诺 "creating or retrieving those values if necessary"，而 async 路径以 `k -> null` 直接违约。裁定 = **契约收窄**：javadoc 与 owner doc 同步声明「async 路径不加载、返回 absent」，并在 plan 标 `contract change` + 配一条断言 async 分支返回 absent 的载体。**拒绝**真加载方案（改变既有 async 契约）。
   - C3-010（`LocalCache` 异步 executor 固化为 commonPool）：`CacheConfig` 增 executor 配置项，缺省仍 commonPool（**保持既有行为**，只给出口），owner doc 声明 CPU 型短任务定位。
   - C3-013（上下文卫生不一致）：**改点限定（draft review R1 M-10）** = `DefaultThreadPoolExecutor.execute`、`DefaultScheduledExecutor.execute`、`BindScheduledExecutor.execute` 三个直通入口补与 `ExecutorHelper.submit/schedule` 一致的 finally detach。新增只读变体：`IContextProvider`（`nop-api-core` 公开接口）**以 `default` 方法新增**（避免破坏 `BaseContextProvider` 与测试 `RawProvider` 等既有实现）。
   - **拒绝方案**：C3-009 改成真加载（破坏既有 async 契约）；C3-010 缺省改 globalWorker（改变既有并发形态，可能引入死锁——globalWorker 已被长任务占用面投诉过）。

7. **裁定 G（单例槽位 `static volatile` 化 + this-escape 消除）**：成员 C1-009。
   - **可观测成员清单（11 项，draft review R1 M-4）**：`VarCollector`、`ModuleManager`、`BeanTool`、`JsonWhitelistChecker`、`VirtualFileSystem`、`DictProvider`、`XSchemaToJsonSchema`、`RegisterModelDiscovery`、`RecordMappingManager`(nop-record-mapping)、`DnsResolver`、`MarkdownTool`(nop-markdown)。
   - **范围裁定**：`RecordMappingManager`（nop-record-mapping）与 `MarkdownTool`（nop-markdown）**不在 R4-13 的 nop-kernel Scope 内** → 登记 Deferred（本批只做 nop-commons/nop-core/nop-xlang 内的 9 项）。
   - **`ErrorMessageManager` 单列（draft review R1 M-5）**：它**没有 `registerInstance`**（`_instance` 只在静态初始化赋值一次、此后永不替换），不属「可替换槽位族」；其真实缺陷是**构造器 this-escape**（`:114-116` 内 `NopException.registerErrorMessageManager(this)`，原设计写的 `:42-45,172-17x` 是错行号）。改为静态工厂/惰性注册。
   - **`VirtualFileSystem` 改点勘正（draft review R1 M-6）**：错序在 **`registerInstance:31-34`**（`DestroyHelper.safeDestroy(_instance); _instance = instance;` → 先销毁旧再发布新），**不是** `unregisterInstance`（后者只置 null、本就无销毁）。改法 = 先 `_instance = instance`（volatile），再 `safeDestroy(old)`。
   - **拒绝方案**：`AtomicReference` 化（对单引用槽位过度，volatile 已满足可见性且零成本）。

8. **裁定 H（`LocalFileLock` 句柄会话化）**：C1-011 —— 把「锁成功会话」的 `(fileLock, fileChannel)` 收敛为一个局部对象，`lock` 成功后赋给 `volatile FileLockHandle held`；`unlock` 只关闭 `held`；`tryLock` 的失败/超时/异常路径统一 `finally releaseHandles()`。
   - **拒绝方案**：仅加「每线程每实例」文档契约 + 运行时 owner 守卫（治标；`tryLock(0)` 失败路径的**句柄泄漏**仍需修）。

## 二、背景与动机

R4-13 的 18 成员同属一个系统性形态：**平台并发原语在「复合操作原子性 / 中断契约 / 回调锁域 / 关停语义 / 失败可见性」五个面上存在纪律缺口**。这些原语的消费方遍布全平台（线程池、锁、缓存、调度、模型缓存），单点缺陷经消费方放大后极难定位——这也是本批被列为 Protected Area、要求 plan-first 的原因：改错的代价是全平台级回归。

成员按裁定分组：

| 裁定 | 成员 | 数量 |
|---|---|---|
| A 互斥原语 | C1-004、C2-003、C2-008、C2-019 | 4 |
| B 中断契约（米策略） | C2-007 | 1 |
| C 回调锁域 | C1-010、C2-004 | 2 |
| D destroy | C4-002 | 1 |
| E 提交/拒绝显式 | C3-008、C3-011、C3-012、C3-018（**C3-002 已 landed，移出**） | 4 |
| F 声明/配置面 | C3-009（contract change）、C3-010、C3-013 | 3 |
| G 单例槽位 | C1-009（9 项；2 项跨模块 Deferred） | 1 |
| H 文件锁会话 | C1-011 | 1 |
| **Fix 合计** | | **17** |
| already-landed 证据项 | C3-002（plan 2496） | 1 |
| **原成员总数** | | **18** |

## 三、逐成员裁定表

| Finding | 文件（live 锚） | 唯一形态 | 拒绝方案 |
|---|---|---|---|
| C1-004 | `nop-commons/.../executor/RateLimitExecutorImpl.java:50-67` | `debounce` 改 `promiseMap.compute(key, ...)`；**lambda 捕获机制（draft review R1 M-7）**：不能写 `Future<?> f = schedule(() -> promiseMap.remove(key, f))`（`f` 创建时未初始化，编译不过）。正确形态 = 在 `compute` 内先 `CompletableFuture<Void> cf = new CompletableFuture<>()`，把它放进 map 并在 schedule 的 lambda 里捕获**该 effectively-final 变量**；或先建 `FutureTask` 再提交，remove 时用 `(Future) task` 自比较 | 双 check + 局部锁（仍非原子） |
| C1-009 | `nop-core/.../module/ModuleManager.java:43-50`；`exceptions/ErrorMessageManager.java:42-45,172-17x`；`resource/VirtualFileSystem` | 槽位 `static volatile`；`ErrorMessageManager` 构造器 this-escape → 静态工厂/惰性注册；`unregisterInstance` 先摘引用再销毁 | `AtomicReference` 化（过度） |
| C1-010 | `nop-commons/.../CycleSynchronizer.java:36-46,66-102` | 引入 `cycleGeneration`；旧周期 finish 不更新周期状态 | 全局锁 |
| C1-011 | `nop-commons/.../lock/LocalFileLock.java:31-37,45-90,118-128` | 句柄会话化（`volatile FileLockHandle held`）+ 统一 `finally releaseHandles()` | 仅文档契约 + owner 守卫（不修泄漏） |
| C2-003 | `nop-core/.../resource/component/ResourceComponentManager.java:334-344` | per-(entry,transform) `CompletableFuture` 占位，锁外 transform | compute 内直接 transform |
| C2-004 | `nop-commons/.../CycleSynchronizer.java:48-53,74-101` | 回调快照出锁域 + 逐回调 try/catch + 先 complete 再回调 | 异步 submit 回调 |
| C2-007 | `nop-commons/.../lock/impl/AbstractResourceLock.java:50-69`；`ResourceLock.java:78-84`；`ResourceMultiLock` | 中断重抛 `InterruptedException` + 接口补 `throws` + 全仓调用点同步修 + `unlock` 纪律统一 | 中断转 `NopException` |
| C2-008 | `nop-commons/.../lock/impl/LocalResourceLockManager.java:169-171` | 判定+删除包入 `synchronized (existingLock.getLatch())` | 注释声明脆弱性 |
| C2-019 | `nop-xlang/.../exec/XLangSemantics.java:1094-1098,631-637` | 同 C2-003 占位模式；`computeIfAbsent` 内不做 `ClassHelper.forName` | 预热批量预载（仅缓解） |
| C3-002 | **already-landed（plan 2496 / `646673a90e`）**：`BindScheduledExecutor.scheduleWithFixedDelay` 现委托 `PeriodicTaskHelper`，其 `scheduleRound`/`dispatchRound` 均 `try/catch(Throwable) → guard.rejectChain(e)` | **本批不做代码改动**；计入 closure 的「证据项」而非 Fix 工单 | — |
| C3-008 | `nop-commons/.../executor/SequentialTaskExecutor.java:79-83` | 检查 offer 返回值 → 按 overflow policy 处理/抛 REE；至少 WARN+计数。**边界（draft review R1 M-8）**：`execute()` 当前**零生产调用方**——唯一消费方 `WebSocketSendPump` 自行 `queue.offer` 并在 false 时断连，不受本修复影响 | 静默忽略 |
| C3-009 | `nop-commons/.../cache/LocalCache.java:280-299`；`IAsyncCache.java:39-93` | **声明面**：javadoc + owner doc 显式化各分支语义；不重写行为 | 改成真加载（破坏契约） |
| C3-010 | `nop-commons/.../cache/LocalCache.java:80-136`；`CacheConfig.java:20-52` | `CacheConfig` 增 executor 配置项，缺省仍 commonPool（只给出口）；owner doc 声明定位 | 缺省改 globalWorker（改变并发形态，有死锁风险） |
| C3-011 | `nop-commons/.../batch/BlockingSourceConsumeTask.java:48-76` | 循环最外层 `catch(Throwable)`+ERROR+continue；保存 future 死亡告警 | 无 |
| C3-012 | `nop-commons/.../batch/AbstractBatchProcessService.java:71-78` | 停接收 → 有界等待 `isAllProcessed`（超时记丢弃清单+告警）→ 销毁 executor | 立即 destroy（现状） |
| C3-013 | `nop-commons/.../executor/ExecutorHelper.java:141-144,165-168,190-193`；`DefaultThreadPoolExecutor` | `execute` 路径补 finally detach；新增 `getContextIfPresent`（只读）供等待点 | 保留现状（泄漏面） |
| C3-018 | `nop-core/.../execution/DefaultTaskExecutionQueue.java:236-240`；`State.cancel:110-113` | `bindCancel(state.getPromise(), future.toCompletableFuture())`——**第二参必须转 `CompletableFuture`**（`bindCancel(CompletionStage, Future)`，而 live `future` 是 `CompletionStage`，直接传不编译）；`whenComplete` 按 `isCancelled()` 分流计数 | 无 |
| C4-002 | `nop-commons/.../executor/BaseThreadPoolConfig.java:17-35`；`DefaultThreadPoolExecutor.java:91-93`；`DefaultScheduledExecutor.java:88-9x` | destroy 有界等待（`ThreadHelper.shutdownAndAwaitTermination`）+ 配置生效 + `stats()` 类型化 fail-fast | 文档声明差异 |

## 四、跨成员共享机制（防各修一半）

1. **`CompletableFuture` 占位模式**（C2-003 / C2-019 共用）：`computeIfAbsent(k, x -> new CompletableFuture<>())` 的返回值身份判定「是否本次新建」——新建者负责锁外计算并 `complete`/`completeExceptionally`，非新建者 `join`。该模式必须**同一形态**在两处落地（禁止一处 join 一处轮询）。参照 `LocalCache.computeIfAbsentAsync` 既有实现。
2. **`InterruptedException` 传播链**（C2-007）：接口 → 实现（`AbstractResourceLock`/`LocalFileLock`/`ResourceMultiLock`）→ 全仓调用点。三者必须同 diff，否则编译即断——这是本批最强的原子性约束。
3. **`destroy` 有界等待**（C4-002 与 C3-012 共用）：统一用 `ThreadHelper.shutdownAndAwaitTermination`（若不存在则本批新增，作为**共享设施**而非两处各写一遍）。
4. **回调链异常隔离**（C2-004 / C3-011 / C3-013）：统一为「快照 → 逐项 try/catch(Throwable) → ERROR 记日志并继续」三段式。

## 五、回归面清单（PA 授权必附；draft review R1 B-7 已按实际消费方重写）

> PA 批次的「回归面」就是授权载体本身。下表按 live grep 的实际消费方列出，漏项即授权不完整。

| 原语 / 改动 | 消费方（live 锚） | 必跑回归 |
|---|---|---|
| **`IResourceLock` 族中断语义**（C2-007） | 接口 `IResourceLock:45`、`IResourceLockManagerImplementor:14`；实现 `LocalResourceLockManager:145`、**`SysDaoResourceLockManager`（`nop-sys/nop-sys-dao`）:114**、`ResourceLock:64`、`ResourceMultiLock:92`；默认方法 `IResourceLock.tryLockAndExecute:80`；测试 19 处（TestLocalLockManager 4 / TestSysDaoResourceLockManager 9 / TestSysDaoFencing_MA01 6） | **`nop-kernel` 全量 + `nop-sys` 全量（必须）** |
| **`RateLimitExecutorImpl.debounce`**（C1-004） | `FileWatcher:68-73`、`ZipFileWatcher:46`、`nop-cli CliWatchZipCommand:128` | `nop-commons` 全量 + `nop-cli` 相关 |
| **`CycleSynchronizer`**（C1-010 + C2-004） | **零生产调用方**（全仓仅自身）→ 改动风险仅限设施本身 | `nop-commons` 全量 |
| **`LocalFileLock`**（C1-011） | `nop-datav`（`AlertEvaluator:147,371`）、`nop-web`（`ResourceWithHistoryProvider:36,43`） | `nop-commons` 全量 + `nop-datav` + `nop-web` |
| **`LocalResourceLockManager` 过期判定**（C2-008） | 同上锁族消费方 | `nop-commons` 全量 + `nop-sys` |
| **`ResourceComponentManager.transform`**（C2-003） | 全平台模型加载（`nop-core` 核心路径） | `nop-core` 全量 + `nop-xdefs` + `nop-xlang` |
| **`XLangSemantics.CLASS_MODELS`**（C2-019） | 全平台 XLang 执行（生成代码 `new` 首触路径） | `nop-xlang` 全量 + `nop-core` |
| **`SequentialTaskExecutor.execute`**（C3-008） | **零生产调用方**（唯一消费方 `WebSocketSendPump` 自行 `queue.offer`，绕过 execute） | `nop-commons` 全量；`nop-graphql-core` 编译级 |
| **`BindScheduledExecutor`**（C3-002，already-landed） | 定时重排面 | 无需（证据项） |
| **`BlockingSourceConsumeTask` / `AbstractBatchProcessService`**（C3-011 / C3-012） | `nop-commons` 批处理基类；`AbstractBatchProcessService:77` destroy | `nop-commons` 全量 |
| **`ExecutorHelper.execute` 上下文卫生**（C3-013） | `DefaultThreadPoolExecutor`（globalWorker/cached 实际载体）、`DefaultScheduledExecutor`、`BindScheduledExecutor` | `nop-commons` 全量 |
| **`DefaultTaskExecutionQueue`**（C3-018） | `nop-core:153` destroy；任务执行队列全链 | `nop-core` 全量 |
| **线程池 `destroy()`**（C4-002） | `nop-commons`（`AbstractBatchProcessService:77`、`BindScheduledExecutor:156`、**`GlobalExecutors.register:133`**）、`nop-core`（`DefaultTaskExecutionQueue:153`）、`nop-batch-exp`（`ExportDbTool:197`/`ImportDbTool:273`）、`nop-js`（`JavaScriptService:178`）、`nop-ai-shell`（`ShellCommandExecutor:878`）、`nop-ai-agent`（`AgentDispatchExecutor:118`）、`nop-graphql-grpc`（`GrpcServer:160`）、`nop-http-client-jdk`（`JdkHttpClient:157`）、`nop-rpc-simple`（`SimpleRpcServer:176`/`SimpleRpcClientFactory:153`）、`nop-config`（`SingleThreadConfigExecutor:24`） | `nop-kernel` 全量 + 上述 8 个模块全量 |
| **`IAsyncCache` 契约收窄**（C3-009） | `LocalCache` 异步面 | `nop-commons` 全量 + owner doc |
| **`CacheConfig` executor 出口**（C3-010） | `LocalCache` builder | `nop-commons` 全量 |
| **单例槽位 volatile**（C1-009） | `nop-core`/`nop-commons`/`nop-xlang` 各单例 | `nop-kernel` 全量 |

**特别风险行**：`GlobalExecutors.register:133` 在旧实例 `destroy()` 后替换——destroy 若变为有界等待，`register` 可能阻塞（配置热更新路径）。必须在该路径回归中显式验证不阻塞（或 destroy 保持异步）。

**E1 全量门禁**：`./mvnw clean install -T 1C -DskipTests` 必须 BUILD SUCCESS。
**E3 分解通道**：`./mvnw test -pl nop-kernel -am` + 上表「必跑回归」列逐条执行并登记签名。

## 六、实施顺序（依赖驱动）

1. **组 1（无兼容性破坏，可并行）**：C1-004、C1-009、C1-011、C2-003、C2-008、C2-019、C3-002、C3-008、C3-011、C3-012、C3-018
2. **组 2（行为语义变化，需回归面）**：C1-010 + C2-004（同文件，必须同 diff）、C3-013、C4-002
3. **组 3（源兼容性破坏，最后）**：C2-007（全仓调用点改造）
4. **组 4（纯声明/配置）**：C3-009、C3-010

## 七、拒绝方案汇总（25 条已在各裁定内联标注）

见 §一 各裁定的「拒绝方案」行与 §三 表末列。
