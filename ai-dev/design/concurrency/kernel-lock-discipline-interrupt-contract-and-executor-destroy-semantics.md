# nop-kernel 锁纪律 / 调度器 / destroy 语义（R4-13 前半，G27 前半 18 成员）

> Status: active
> Last Reviewed: 2026-11-09
> Source: plan 2547（`ai-dev/plans/2547-cra-phase8.53-r4-13-kernel-lock-scheduler-destroy-a.md`）Phase 1；批次定义 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二 R4-13 行（18 finding ID / nop-kernel / PA=框架核心引擎 plan-first）；成员发现 = `ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/`（C1-shared-mutable-state.md / C2-lock-sync.md / C2-round2.md / C3-async-chains.md / C3-round2.md / C4-thread-pools.md）
> Protected Area 授权：本批触及 `nop-kernel/nop-commons`、`nop-kernel/nop-core`、`nop-kernel/nop-xlang` = AGENTS.md「框架核心引擎（nop-core/nop-xlang/nop-xdefs 内部）」= **plan-first**。授权载体 = 本文档 + 独立 draft review + 回归面清单（§五）；未过 review 不得改码。
> 传导先例：[pool-facility-hardening.md](pool-facility-hardening.md)（池设施硬化裁定与兼容矩阵）、[kernel-retirement-generation-and-model-load-locking.md](kernel-retirement-generation-and-model-load-locking.md)（R2-01 退役代际族，C1-010/C2-004 的邻接先例）

## 一、设计结论（八条裁定）

1. **裁定 A（互斥原语优先，统一 compute 纪律）**：凡「读-判-改」跨多步的 `ConcurrentHashMap` 复合操作，一律改 `compute`/`computeIfAbsent` 单原子域，禁止裸 `get` + 后续 `put/remove`。成员：C1-004（`RateLimitExecutorImpl.debounce`）、C2-003（`ResourceComponentManager` transform 锁内重活）、C2-019（`XLangSemantics.CLASS_MODELS` 锁内类加载）、C2-008（`LocalResourceLockManager` 过期判定绕过 latch）。
   - **但 compute 内禁重活**：C2-003/C2-019 的 `computeIfAbsent` 内执行编译/类加载，属「锁内重活」——compute 只做**占位**（`CompletableFuture`），构建在锁外执行后 `complete`/`completeExceptionally`。占位模式统一为：`computeIfAbsent(k, x -> new CompletableFuture<>())` → 若为**本次新建**才在锁外计算并 complete（用 `boolean[] created` 或返回值身份判定），否则 `join` 既有 future。
   - **拒绝方案**：直接 `compute` 内做构建（违反 CHM 契约：compute 内再入同 map 可死锁/IllegalStateException）；改 `synchronized` 全局锁（退化为串行）。

2. **裁定 B（可中断语义必须透传，不得包装成业务异常）**：`Lock` 家族的 `lockInterruptibly`/`tryLock(time,unit)` 语义要求中断以 `InterruptedException` 传播。成员：C2-007（`AbstractResourceLock` 家族）。改法：`tryLockWithLease` 的 `catch (InterruptedException)` 分支**重抛 `InterruptedException`**，向上让 `lockInterruptibly()` 补 `throws InterruptedException`；`unlock` 纪律统一为 `synchronized`（两组实现对齐）。
   - **兼容性裁定**：`lockInterruptibly()` 加 `throws InterruptedException` 属**源兼容性破坏**（既有调用点须加 try/catch）。本批按「实现类与接口同时声明 + 全仓调用点同步修」处理，并在 §五 回归面列出全部调用点。**拒绝方案**：把中断转为 `NopException`（现状，违反 Lock 契约，且调用方无法区分"被中断"与"锁失败"）。

3. **裁定 C（回调出锁域 + 逐回调隔离）**：`CycleSynchronizer`（C1-010 + C2-004 同站）—— ① 回调列表**快照后在监视器外**执行（消除持 `this` 锁跑任意回调的锁域扩展、自死锁、跨实例锁序环）；② 每个回调**独立 try/catch**（一个回调抛异常不得中断后续、不得以异常完成 promise）；③ 引入单调 `cycleGeneration`，旧周期 `finish` 不得清掉新周期状态。
   - **次序裁定**：先 `promise.complete(...)` 再跑回调（同时消除「回调内 await 本周期 promise」的自死锁面）。
   - **拒绝方案**：把回调改成异步 submit（改变既有同步语义，调用方依赖「回调完成后 finish 返回」）。

4. **裁定 D（destroy 语义统一：有界等待 + 类别声明）**：`DefaultThreadPoolExecutor.destroy()` 与 `DefaultScheduledExecutor.destroy()` 统一为「停止接收 → 有界等待在飞任务 → 超时显式告警 → 强制终止」；`waitForTasksToCompleteOnShutdown`/`awaitTerminationSeconds` 配置项从**死值**变为**生效**（复用 `ThreadHelper.shutdownAndAwaitTermination`）。成员：C4-002。
   - `destroy` 后 `stats()` 由 NPE 改为**类型化 fail-fast**（`NopException`）。
   - **拒绝方案**：两类池保持语义互斥（文档声明差异）——已确认 `defaultWorker`/`globalWorker`/cached/timer 四类池的消费方均假设「destroy 后不再执行」，保留互斥会持续制造「关停后仍在跑」的观测缺口。

5. **裁定 E（提交/拒绝必须显式，不得静默丢弃）**：成员 C3-008（`SequentialTaskExecutor.execute` 忽略 offer 返回值）、C3-011（`BlockingSourceConsumeTask` 循环死亡不可感知）、C3-012（`AbstractBatchProcessService.destroy` 丢弃队列）、C3-018（`DefaultTaskExecutionQueue` 取消绑定参数反接）、C3-002（`BindScheduledExecutor` 重排失败 future 永不完成）。
   - C3-008：`execute` 检查 offer 返回值，false 时按 `QueueOverflowPolicy` 处理（block/throw），至少 `LOG.warn` + 计数；**禁止静默丢任务**。
   - C3-011：循环体最外层 `catch (Throwable)` + ERROR 日志 + `continue`（仅 cancel/中断退出）；保存 submit future 并在死亡时告警。
   - C3-012：`destroy` 先停止接收 → 有界等待 `isAllProcessed`（超时记录**丢弃清单**并告警）→ 再销毁 executor。
   - C3-018：`bindCancel(state.getPromise(), future)` 对齐注释语义；`whenComplete` 按 `state.isCancelled()` 分流 `cancelled++`（而非 `completed++`）。
   - C3-002：重排调用纳入 `try/catch(Throwable)`，失败时 `future.completeExceptionally(e)`。
   - **拒绝方案**：把 C3-013（异步上下文卫生，审计自注「理论风险未具体化」）与 C3-009/C3-010（缓存语义分叉/executor 固化）升格为行为改造——三者均为**声明/配置面**，按裁定 F 处理。

6. **裁定 F（语义分叉只做声明或配置化，不做行为重写）**：
   - C3-009（`IAsyncCache.getAsync` vs `getAllAsync` 语义分叉）：不做行为重写（改 loading 语义会改变既有 async 缓存契约），改为 **javadoc + owner doc 显式声明**各配置分支的语义差异，并让 `getAllAsync` 的 `k -> null` 形态在 javadoc 标明「不加载、返回 absent」。
   - C3-010（`LocalCache` 异步 executor 固化为 commonPool）：`CacheConfig` 增 executor 配置项，缺省仍 commonPool（**保持既有行为**，只给出口），owner doc 声明 CPU 型短任务定位。
   - C3-013（上下文卫生不一致）：`execute` 路径补与 `submit` 一致的 finally detach（**这是唯一行为改动**，理由：`getOrCreateContext` 的池线程驻留是已确认的泄漏面）；`getOrCreateContext` 保留（有正当调用方），新增只读变体供等待点使用。
   - **拒绝方案**：C3-009 改成真加载（破坏既有 async 契约）；C3-010 缺省改 globalWorker（改变既有并发形态，可能引入死锁——globalWorker 已被长任务占用面投诉过）。

7. **裁定 G（单例槽位 `static volatile` 化 + this-escape 消除）**：C1-009 族——`ModuleManager`/`ErrorMessageManager`/`VirtualFileSystem` 等 `_instance + registerInstance` 槽位统一 `static volatile`（对齐 `ResourceComponentManager` 先例，机械改动）；`ErrorMessageManager` 构造器 this-escape 改为**静态工厂内注册或惰性注册**；`VirtualFileSystem.unregisterInstance` 顺序改为「先摘引用再销毁」。
   - **拒绝方案**：改用 `AtomicReference`（对单引用槽位过度，volatile 已满足可见性且零成本）。

8. **裁定 H（`LocalFileLock` 句柄会话化）**：C1-011 —— 把「锁成功会话」的 `(fileLock, fileChannel)` 收敛为一个局部对象，`lock` 成功后赋给 `volatile FileLockHandle held`；`unlock` 只关闭 `held`；`tryLock` 的失败/超时/异常路径统一 `finally releaseHandles()`。
   - **拒绝方案**：仅加「每线程每实例」文档契约 + 运行时 owner 守卫（治标；`tryLock(0)` 失败路径的**句柄泄漏**仍需修）。

## 二、背景与动机

R4-13 的 18 成员同属一个系统性形态：**平台并发原语在「复合操作原子性 / 中断契约 / 回调锁域 / 关停语义 / 失败可见性」五个面上存在纪律缺口**。这些原语的消费方遍布全平台（线程池、锁、缓存、调度、模型缓存），单点缺陷经消费方放大后极难定位——这也是本批被列为 Protected Area、要求 plan-first 的原因：改错的代价是全平台级回归。

成员按裁定分组：

| 裁定 | 成员 | 数量 |
|---|---|---|
| A 互斥原语 | C1-004、C2-003、C2-008、C2-019 | 4 |
| B 中断契约 | C2-007 | 1 |
| C 回调锁域 | C1-010、C2-004 | 2 |
| D destroy | C4-002 | 1 |
| E 提交/拒绝显式 | C3-002、C3-008、C3-011、C3-012、C3-018 | 5 |
| F 声明/配置面 | C3-009、C3-010、C3-013 | 3 |
| G 单例槽位 | C1-009 | 1 |
| H 文件锁会话 | C1-011 | 1 |
| **合计** | | **18** |

## 三、逐成员裁定表

| Finding | 文件（live 锚） | 唯一形态 | 拒绝方案 |
|---|---|---|---|
| C1-004 | `nop-commons/.../executor/RateLimitExecutorImpl.java:50-67` | `debounce` 改 `promiseMap.compute(key, ...)`：内部 cancel 旧 + schedule 新 + 返回新 future；schedule 回调内仅 `remove(key, 捕获的 future)`，删除 `Future<?>[] holder` | 双 check + 局部锁（仍非原子） |
| C1-009 | `nop-core/.../module/ModuleManager.java:43-50`；`exceptions/ErrorMessageManager.java:42-45,172-17x`；`resource/VirtualFileSystem` | 槽位 `static volatile`；`ErrorMessageManager` 构造器 this-escape → 静态工厂/惰性注册；`unregisterInstance` 先摘引用再销毁 | `AtomicReference` 化（过度） |
| C1-010 | `nop-commons/.../CycleSynchronizer.java:36-46,66-102` | 引入 `cycleGeneration`；旧周期 finish 不更新周期状态 | 全局锁 |
| C1-011 | `nop-commons/.../lock/LocalFileLock.java:31-37,45-90,118-128` | 句柄会话化（`volatile FileLockHandle held`）+ 统一 `finally releaseHandles()` | 仅文档契约 + owner 守卫（不修泄漏） |
| C2-003 | `nop-core/.../resource/component/ResourceComponentManager.java:334-344` | per-(entry,transform) `CompletableFuture` 占位，锁外 transform | compute 内直接 transform |
| C2-004 | `nop-commons/.../CycleSynchronizer.java:48-53,74-101` | 回调快照出锁域 + 逐回调 try/catch + 先 complete 再回调 | 异步 submit 回调 |
| C2-007 | `nop-commons/.../lock/impl/AbstractResourceLock.java:50-69`；`ResourceLock.java:78-84`；`ResourceMultiLock` | 中断重抛 `InterruptedException` + 接口补 `throws` + 全仓调用点同步修 + `unlock` 纪律统一 | 中断转 `NopException` |
| C2-008 | `nop-commons/.../lock/impl/LocalResourceLockManager.java:169-171` | 判定+删除包入 `synchronized (existingLock.getLatch())` | 注释声明脆弱性 |
| C2-019 | `nop-xlang/.../exec/XLangSemantics.java:1094-1098,631-637` | 同 C2-003 占位模式；`computeIfAbsent` 内不做 `ClassHelper.forName` | 预热批量预载（仅缓解） |
| C3-002 | `nop-commons/.../executor/BindScheduledExecutor.java:88-116` | 重排纳入 try/catch(Throwable) → `completeExceptionally` | 仅 isDone 前置检查 |
| C3-008 | `nop-commons/.../executor/SequentialTaskExecutor.java:79-83` | 检查 offer 返回值 → 按 overflow policy 处理/抛 REE；至少 WARN+计数 | 静默忽略 |
| C3-009 | `nop-commons/.../cache/LocalCache.java:280-299`；`IAsyncCache.java:39-93` | **声明面**：javadoc + owner doc 显式化各分支语义；不重写行为 | 改成真加载（破坏契约） |
| C3-010 | `nop-commons/.../cache/LocalCache.java:80-136`；`CacheConfig.java:20-52` | `CacheConfig` 增 executor 配置项，缺省仍 commonPool（只给出口）；owner doc 声明定位 | 缺省改 globalWorker（改变并发形态，有死锁风险） |
| C3-011 | `nop-commons/.../batch/BlockingSourceConsumeTask.java:48-76` | 循环最外层 `catch(Throwable)`+ERROR+continue；保存 future 死亡告警 | 无 |
| C3-012 | `nop-commons/.../batch/AbstractBatchProcessService.java:71-78` | 停接收 → 有界等待 `isAllProcessed`（超时记丢弃清单+告警）→ 销毁 executor | 立即 destroy（现状） |
| C3-013 | `nop-commons/.../executor/ExecutorHelper.java:141-144,165-168,190-193`；`DefaultThreadPoolExecutor` | `execute` 路径补 finally detach；新增 `getContextIfPresent`（只读）供等待点 | 保留现状（泄漏面） |
| C3-018 | `nop-core/.../execution/DefaultTaskExecutionQueue.java:236-240`；`State.cancel:110-113` | `bindCancel(state.getPromise(), future)`；`whenComplete` 按 `isCancelled()` 分流计数 | 无 |
| C4-002 | `nop-commons/.../executor/BaseThreadPoolConfig.java:17-35`；`DefaultThreadPoolExecutor.java:91-93`；`DefaultScheduledExecutor.java:88-9x` | destroy 有界等待（`ThreadHelper.shutdownAndAwaitTermination`）+ 配置生效 + `stats()` 类型化 fail-fast | 文档声明差异 |

## 四、跨成员共享机制（防各修一半）

1. **`CompletableFuture` 占位模式**（C2-003 / C2-019 共用）：`computeIfAbsent(k, x -> new CompletableFuture<>())` 的返回值身份判定「是否本次新建」——新建者负责锁外计算并 `complete`/`completeExceptionally`，非新建者 `join`。该模式必须**同一形态**在两处落地（禁止一处 join 一处轮询）。参照 `LocalCache.computeIfAbsentAsync` 既有实现。
2. **`InterruptedException` 传播链**（C2-007）：接口 → 实现（`AbstractResourceLock`/`LocalFileLock`/`ResourceMultiLock`）→ 全仓调用点。三者必须同 diff，否则编译即断——这是本批最强的原子性约束。
3. **`destroy` 有界等待**（C4-002 与 C3-012 共用）：统一用 `ThreadHelper.shutdownAndAwaitTermination`（若不存在则本批新增，作为**共享设施**而非两处各写一遍）。
4. **回调链异常隔离**（C2-004 / C3-011 / C3-013）：统一为「快照 → 逐项 try/catch(Throwable) → ERROR 记日志并继续」三段式。

## 五、回归面清单（PA 授权必附）

本批改动触及平台并发原语，回归面必须**逐成员列出**消费方：

| 原语 | 改动的兼容性影响 | 必跑回归 |
|---|---|---|
| `RateLimitExecutorImpl.debounce` | 行为不变（语义更严格） | `nop-commons` 全量；`nop-core` FileWatcher/ZipFileWatcher 相关 |
| `CycleSynchronizer` | 回调执行时机变化（出锁后） | `nop-commons` 全量 |
| `Lock` 家族 `throws InterruptedException` | **源兼容性破坏**——全部调用点须同步 | 全仓编译 + `nop-commons`/`nop-core`/`nop-persistence` 全量 |
| `LocalFileLock` | 行为不变 | `nop-commons` 全量 |
| `ResourceComponentManager.transform` | 行为不变（并发正确性提升） | `nop-core` 全量 + `nop-xdefs` 模型加载相关 |
| `XLangSemantics.CLASS_MODELS` | 行为不变 | `nop-xlang` 全量 + `nop-core` |
| `DefaultThreadPoolExecutor/ScheduledExecutor.destroy` | **derive 语义变化**（有界等待） | `nop-commons` 全量；`nop-stream`/`nop-job`/`nop-task` 的池消费面 |
| `BlockingSourceConsumeTask` / `AbstractBatchProcessService` | 关停语义变化（有界等待） | `nop-commons` 全量 |
| `IAsyncCache` / `LocalCache` / `CacheConfig` | 纯声明 + 新增配置项（缺省不变） | `nop-commons` 全量 |
| `DefaultTaskExecutionQueue` | 计数语义修正 | `nop-core` 全量 |

**E1 全量门禁**：`./mvnw clean install -T 1C -DskipTests` 必须 BUILD SUCCESS；**E3 分解通道**：`./mvnw test -pl nop-kernel -am` + 各消费模块 focused 测试。

## 六、实施顺序（依赖驱动）

1. **组 1（无兼容性破坏，可并行）**：C1-004、C1-009、C1-011、C2-003、C2-008、C2-019、C3-002、C3-008、C3-011、C3-012、C3-018
2. **组 2（行为语义变化，需回归面）**：C1-010 + C2-004（同文件，必须同 diff）、C3-013、C4-002
3. **组 3（源兼容性破坏，最后）**：C2-007（全仓调用点改造）
4. **组 4（纯声明/配置）**：C3-009、C3-010

## 七、拒绝方案汇总（25 条已在各裁定内联标注）

见 §一 各裁定的「拒绝方案」行与 §三 表末列。
