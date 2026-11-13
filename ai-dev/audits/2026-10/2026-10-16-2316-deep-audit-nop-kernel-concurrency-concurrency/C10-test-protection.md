# C10 维度：测试保护力审计（nop-kernel 并发·异步·资源专项审计）

- **维度编号**：nop-kernel-concurrency-C10（测试保护力，本单元最后批次）
- **审计对象**：`nop-kernel/nop-commons`、`nop-kernel/nop-core`、`nop-kernel/nop-xlang` 三模块 `src/test` 测试套件
- **输入口径**：同目录 8 个维度文件（C1/C2/C3/C4/C5/C6/C7/C9）`## 最终保留项` 节 + `## 维度复核结论` 节，合计 **118 条保留发现（P1×5，P2×75，P3×38）**；基线分支 `concurrency-audit-remediation`，HEAD `8c13908bfd`
- **审计日期**：2026-10-17
- **纪律声明**：本维度只读。未修改任何测试代码、产品代码与八维审计文件；所有「有钉/部分」判定均实际打开测试文件核对，未凭测试类名推断。

## 一、判定口径

| 档 | 定义 |
|---|---|
| **有钉** | 存在针对该缺陷本体的测试，缺陷回归（或修复被回退）时该测试会失败 |
| **部分** | 同类/同链存在测试，但只钉住相邻面、修复先例面或单线程语义面；缺陷本体窗口无断言 |
| **无保护** | 缺陷本体及其触发时序无任何测试触达；现套件在缺陷存在下全部通过（多数条目现状即「已知未修复 + 测试绿」的共存态） |

**反向抽查（4 条，超出规定的 3 条）**——对判定中承担「钉」角色的测试，经 `git log` 核实「修复+测试同 commit 落地」，确认钉的真实性：

1. `TestContextTaskQueue#testFlushExitWindowDoesNotLoseTask`（nop-api-core）← commit `5ee798e02e`（「P1-5 竞态经内部锁冻结确定性复现」，修复+测试同批）——钉的是 ContextTaskQueue **flush 退出窗口已修面**，非 C2-016 的 syncGet 未修面。
2. `TestRoundRobinSupplier#testResizeCloseNewResourcesOnFailure` ← commit `22129fa9f7`（「RoundRobinSupplier 扩容失败关新建资源」）——钉的是**扩容失败泄漏已修面**，非 C1-001 的收缩并发窗口。
3. `TestICacheDefaults#testPutIfAbsentConcurrentSingleWinner` / `TestCache#testPutIfAbsentAtomic` ← commit `87d09323c5`（「ICache 默认实现原子化」）+ `22129fa9f7`（「LocalCache.putIfAbsent 改 Caffeine 原子」）——putIfAbsent 原子性钉成立（C9-006 驳回结论的护栏，非 118 条内缺陷）。
4. `TestNioFileWatchService#testRegisterFailureDoesNotKillWatcher` ← commit `dbe5a83ec9`（「watchKeyMap 并发+IOException 杀循环」）——钉的是 watcher 循环存活已修面，非 C6-006 的注销不对称面。

抽查结论：四条「钉」均为**历史修复轮的回归护栏**，其保护面与 118 条缺陷本体**无一重叠**——这本身就是本维度最重要的结构性事实。

## 二、测试资产盘点

### 2.1 总量

| 模块 | 测试 java 文件 | @Test 方法总数 | 并发原语使用文件数 |
|---|---|---|---|
| nop-commons | 53 | ~330 | 9 |
| nop-core | 94 | ~460 | 6 |
| nop-xlang | 113 | ~684 | 2 |
| **合计** | **260**（其中 38 个为无 @Test 的辅助/模型类） | **1474** | **14** |

并发原语（CountDownLatch/CyclicBarrier/ExecutorService/new Thread）使用文件共 14 个，其中**真并发断言**（多线程竞争 + 唯一胜者/单次触发类断言）仅 5 处：
`TestCancellable`（4 用例，CAS 语义，质量最高）、`TestICacheDefaults#testPutIfAbsentConcurrentSingleWinner/testComputeIfAbsentConcurrentSingleWinner`（16 线程×50 轮）、`TestCache#testPutIfAbsentAtomic`（2 线程×200 轮）、`TestReflectionManager#testGetFromCacheConcurrentBuildOnlyOnce`（8 线程）、`TestExecutionContextImpl#testConcurrentCompleteAndCompleteExceptionally`（2 线程×500 轮）。其余均为功能/单线程语义测试。

### 2.2 按被测面归类（并发审计相关面）

| 被测面 | 测试类（#用例） | 覆盖性质 |
|---|---|---|
| concurrent 设施·信号量 | `TestHighWatermarkSemaphore`(2) | 滞回语义+统计计数，单线程 |
| concurrent 设施·资源轮询 | `TestRoundRobinSupplier`(2) | resize 失败回滚（历史修复面）+单线程缩扩容语义 |
| concurrent 设施·限流节流 | `TestRateLimitExecutor`(3)、`TestRateLimiter`(2) | throttle/debounce 正常路径+map 自清理（历史修复面）；吞吐冒烟 |
| concurrent 设施·顺序执行器 | `TestSequentialTaskExecutor`(1) | 仅 totalExecutionTime 纳秒单位（历史修复面） |
| concurrent 设施·取消 | `TestCancellable`(4)、`TestCompletableFuture`(1) | CAS 取消语义（真并发）、JDK isCancelled |
| lock | `TestLocalFileLock`(1)、`TestLocalLockManager`(5) | lock/tryLock 成功路径；租约过期/续租语义（真实 sleep）；10 线程 tryLockWithLease 冲突冒烟 |
| cache | `TestCache`(3)、`TestICacheDefaults`(6)、`TestMapCache`(3)、`TestGlobalCacheRegistry`(1) | putIfAbsent/computeIfAbsent 原子性（真并发，历史修复面）；MapCache async 同步执行语义；重复注册 fail-fast |
| core resource cache | `TestResourceCacheEntry`(1)、`TestResourceLoadingCache`(1) | reload-null 占位；单线程加载命中 |
| core VFS/watch | `TestDefaultVirtualFileSystemRefresh`(1)、`TestNioFileWatchService`(2) | refresh 先重建后关闭次序（早前「修一半」修复面）；watcher 注册失败存活（check2 修复面） |
| core 执行/队列 | `TestDefaultTaskExecutionQueue`(10)、`TestTaskExecutionGraph`(1)、`TestExecutionContextImpl`(3) | 队列全功能（ManualExecutor 单线程确定性）；并发 complete 单次回调 |
| core 注册表/生命周期 | `TestReflectionManager`(2)、`TestResourceComponentManager`(2)、`TestCoreInitialization`(2)、`TestErrorMessageManager`(1)、`TestDictProvider`(1)、`TestEnumDictLoader`(1) | getFromCache 并发单实例；URL 解析；reinitialize 写回；错误映射清理 |
| xlang | `TestXLangSemantics`(3)、`TestSimpleDslRegistry`(1)、`TestEvalBackendRegistry`(7) | 跨 funcName 缓存正确性；DSL 加载 happy path；后端注册表 |

### 2.3 测试空洞登记（零测试面）

以下与 118 条发现直接相关的设施/类，在三个受审模块测试套件中**零专项测试**（含间接消费亦无）：

- **池设施全族**：`GlobalExecutors`（g_executors/globalTimer/globalWorker，0 测试）、`StandardThreadPoolExecutor`、`SyncThreadPoolExecutor`、`ThreadPoolConfig`、`VirtualThreadTaskExecutor`、`updateThreadPool` 热更路径、`TaskQueue`（submittedCount）——全零
- **调度器**：`DefaultScheduledExecutor`、`BindScheduledExecutor`（仅 TestRateLimitExecutor/TestXplTask 间接实例化，无生命周期/异常/取消语义断言）、`ExecutorHelper.scheduleWithRandomDelay`——全零
- **队列设施**：`OverflowBlockingQueue`（仅被 TestSequentialTaskExecutor 当参数构造，溢出策略零断言）、`DedupBlockingQueue`、`IBlockingSource.drainTo/takeMulti`——全零
- **潜伏设施**：`CycleSynchronizer`、`ReactiveObjectMap`、`ResourceMultiLock`/`ResourceLock`、`ResourceTenantManager`、`LimitedExecutor`、`HighWatermarkSemaphore` 之外的 semaphore 族——全零
- **xlang 编译链**：`LazyCompiledFunction`、`CompiledTag`、`XLangSemantics` 的 CLASS_MODELS 锁域与容量、`XDef` 锁内解析面（功能解析测试存在、锁域无观察）——全零
- **资源缓存治理**：`ResourceTimestampCache`（死代码）、`ResourceContentCache`、`ResourceDependsManager`、`DefaultResourceChangeChecker`、`GlobalCacheRegistry` 驱逐面——全零
- **文件监听（nop-commons）**：`FileWatcher`、`ZipFileWatcher`（debounce 生产调用方本体）——零测试
- **批处理消费**：`BlockingSourceConsumeTask`、`AbstractBatchProcessService`——零测试
- **停机链**：`ShutdownHook`、`LifeCycleSupport`、`ThreadHelper.shutdownAndAwaitTermination`（0 调用者的现成修复件）——零测试
- **check-changed 失效链**：唯一触及 `getResourceDepends` 的集成测试 `TestXplTask#testWatch` 为 **@Disabled**（见 N3）

## 三、保护力矩阵

### 3.1 P1 单列详表（5/5 无保护）

| ID | 缺陷 | 判定 | 核对依据（实际打开的测试） | 缺口说明 |
|---|---|---|---|---|
| C1-001 | RoundRobinSupplier.resize 收缩在锁外关闭旧数组尾段，并发 get 快照解引用已关闭资源 | **无保护** | `TestRoundRobinSupplier`（2 用例） | `testResizeCloseNewResourcesOnFailure` 钉的是 22129fa9f7 已修的「扩容失败关新建」面（反向抽查②）；`testResizeShrinkAndExpand` 为单线程，**在 C1-001 缺陷存在下照常通过**（收缩后 get 走新数组，触及不到锁外关闭窗）。并发 get×resize 收缩零测试。另见 N5（误导性覆盖） |
| C2-001 | ResourceCacheEntry 持 entry 监视器加载模型，跨 entry 嵌套（xdef/x:config import/脚本三路径）+两线程反序→无超时死锁；单线程形态 SOE | **无保护** | `TestResourceCacheEntry`（1 用例） | 唯一用例钉 reload-null 占位；无嵌套加载、无循环依赖模型、无双线程反序加载用例。`TestSimpleDslRegistry`/`TestResourceComponentManager` 均为无环 happy path，单线程，触达不到监视器链 |
| C3-001 | 周期任务设施无统一异常绝缘层（29 站点），一次 Throwable 静默永久停摆 | **无保护** | 全套件 grep（零命中） | 无任何测试调度「会抛异常的周期任务」并断言后续触发继续。TestRateLimitExecutor 的 throttle/debounce 走的正是无绝缘的 DefaultScheduledExecutor，但任务从不抛异常 |
| C4-001 | globalTimer 4 线程共享定时器：容量死配置+工厂副作用改写+直连/转发混用无隔离+DB stall 放大 | **无保护** | 全套件 grep（零专项测试） | `TestLocalLockManager#setUp`、`TestTaskExecutionGraph` 仅**消费** globalTimer/cachedThreadPool，无任何容量/隔离/配置断言。池设施全族（GlobalExecutors/ThreadPoolConfig/updateThreadPool）零测试 |
| C7-007 | 模型缓存 check-changed 双漏检窗口（同 tick 双写+load/取 mtime 竞态），无 TTL 兜底→陈旧模型无限期服务 | **无保护** | `TestResourceCacheEntry`、`TestXplTask`、`TestResourceLoadingCache` | 唯一触及依赖链的 `TestXplTask#testWatch` 为 @Disabled（N3）；`TestResourceCacheEntry#testReloadToNullUsesNullPlaceholder` 钉刷新占位语义，不钉失效检测正确性；ResourceTimestampCache 死代码无测试。整条失效链零活测试 |

### 3.2 全量归组矩阵（113 条 P2/P3，按维度）

**图例**：钉=有钉；部分=部分保护；无=无保护。判定均经打开测试文件核对。

#### C1 共享可变状态（17 条：无×15，部分×2）

| ID | 级 | 判定 | 依据/最近测试 |
|---|---|---|---|
| C1-001 | P1 | 无 | 见 §3.1 |
| C1-003 | P2 | 无 | DefaultScheduledExecutor checkEnabled-then-use×destroy 置空竞态：TestRateLimitExecutor 仅正常 destroy（tearDown），无并发 destroy×use 用例 |
| C1-004 | P2 | 无 | debounce 非原子双执行：`TestRateLimitExecutor#testDebounce` 单线程 happy path（另见 C10-006） |
| C1-005 | P2 | 无 | scheduleWithRandomDelay：全套件零引用（grep 证实） |
| C1-006 | P2 | 无 | ReactiveObjectMap：零测试（grep 证实） |
| C1-007 | P2 | 无 | ResourceMultiLock：零测试（grep 证实） |
| C1-009 | P2 | 无 | 13 单例槽位+VFS 错误序+ErrorMessageManager this-escape：无可见性/发布测试；`TestErrorMessageManager` 依赖构造器注册全局槽的缺陷形态（见 N4） |
| C1-010 | P2 | 无 | CycleSynchronizer：零测试 |
| C1-011 | P2 | 无 | `TestLocalFileLock#testLockAcquireSuccess` 仅成功路径；tryLock(0) 失败泄漏与跨实例交叉写零用例 |
| C1-014 | P2 | **部分** | `TestDefaultVirtualFileSystemRefresh#testRefreshClosesOldZipOnlyAfterRebuild` 钉住「先重建后关闭」次序（早前修复半面）；在飞读者窗口与 destroy 路径零断言（另见 N2） |
| C1-002 | P3 | 无 | TaskQueue/submittedCount：StandardThreadPoolExecutor 零测试 |
| C1-008 | P3 | 无 | ResourceTenantManager：零测试 |
| C1-012 | P3 | 无 | ResourceLock/MultiLock 句柄可见性：零测试 |
| C1-013 | P3 | **部分** | `TestICacheDefaults#testEhcacheCacheBehavior` 钉住 Ehcache 侧 getAndSet 顺序语义（注释自认「显式裁决的语义损失」）；LocalCache/MapCache 侧语义与跨实现一致性零测试——分裂本体无护栏 |
| C1-015 | P3 | 无 | GlobalExecutors.register/unregister：零测试 |
| C1-016 | P3 | 无 | updateThreadPool 收缩 IAE：池热更零测试 |
| C1-017 | P3 | 无 | 实例级协作者槽位族：零可见性测试 |

#### C2 锁与同步（22 条：无×20，部分×2）

| ID | 级 | 判定 | 依据/最近测试 |
|---|---|---|---|
| C2-001 | P1 | 无 | 见 §3.1 |
| C2-002 | P2 | 无 | ResourceCacheEntry 快路径 vs clear：TestResourceCacheEntry 无并发用例 |
| C2-003 | P2 | 无 | transformed CHM bin 锁内 transform：零测试 |
| C2-004 | P2 | 无 | CycleSynchronizer 持 this 锁回调：零测试 |
| C2-005 | P2 | 无 | debounce 双执行竞态：同 C1-004 |
| C2-006 | P2 | 无 | 同 C1-003 站点 |
| C2-007 | P2 | 无 | AbstractResourceLock 中断契约：`TestLocalLockManager#testLock` 无中断场景，无 lockInterruptibly 断言 |
| C2-008 | P2 | 无 | tryLockWithLease 过期判定绕过 latch 监视器：testLock 消费该 API 但不断言不变式 |
| C2-009 | P2 | 无 | DictProvider/ModuleManager registerInstance 非 volatile：`TestDictProvider` happy path |
| C2-010 | P2 | 无 | DedupBlockingQueue：零测试 |
| C2-011 | P3 | 无 | OverflowBlockingQueue DROP_ELDEST 两段非原子：零专项测试（仅被当构造参数） |
| C2-012 | P3 | 无 | ResourceContentCache：零测试 |
| C2-013 | P3 | 无 | registerComponentModelConfig/registerClassModel check-then-put：`TestReflectionManager#testGetFromCacheConcurrentBuildOnlyOnce` 钉的是 getFromCache 构建去重，**不是** register* 的 fail-fast 契约；RCM 注册并发零测试 |
| C2-014 | P3 | 无 | ResourceTenantManager：零测试 |
| C2-015 | P2 | 无 | ReactiveObjectMap 毒化：零测试 |
| C2-016 | P2 | **部分** | flush 退出窗口面由 nop-api-core `TestContextTaskQueue#testFlushExitWindowDoesNotLoseTask` 有钉（反向抽查①；注意该测试在**受审三模块之外**）；本条本体的 **syncGet drain→await 丢失唤醒面零测试** |
| C2-017 | P2 | 无 | LazyCompiledFunction：零测试 |
| C2-018 | P3 | 无 | CompiledTag.deps：零测试 |
| C2-019 | P2 | 无 | CLASS_MODELS bin 锁内类加载：`TestXLangSemantics` 钉跨 funcName 缓存正确性，非锁域 |
| C2-020 | P3 | 无 | XDef 锁内解析：TestXDefParse 等功能测试存在，锁域无观察手段 |
| C2-021 | P3 | **部分** | 同 C1-014：refresh 次序半边有钉；destroy×无锁读者 zip-closed 窗口零测试 |
| C2-022 | P3 | 无 | DefaultTaskExecutionQueue bin 锁内 execute：TestDefaultTaskExecutionQueue 用 ManualExecutor 绕开真实提交路径 |

#### C3 异步链（20 条：无×20）

| ID | 级 | 判定 | 依据/最近测试 |
|---|---|---|---|
| C3-001 | P1 | 无 | 见 §3.1 |
| C3-002 | P2 | 无 | Bind 重排链：BindScheduledExecutor 零测试 |
| C3-003 | P2 | 无 | 同上 |
| C3-004 | P2 | 无 | Bind 取消不传播（RetryEngineImpl 暴露点）：零测试 |
| C3-005 | P2 | 无 | throttleExecute 漏 Error：TestRateLimitExecutor 任务不抛 Error |
| C3-006 | P2 | 无 | acquireUninterruptibly：全套件无中断类断言 |
| C3-007 | P2 | 无 | LimitedExecutor：零测试（grep 证实） |
| C3-008 | P2 | 无 | `TestSequentialTaskExecutor#testTotalExecutionTime` 仅钉纳秒单位（22129fa9f7 面）；execute 丢 offer 返回值零断言 |
| C3-009 | P2 | 无 | LocalCache getAsync/getAllAsync 分叉：`TestMapCache` 钉的是豁免面（MapCache futureCall 包装），LocalCache async 路径零测试 |
| C3-010 | P2 | 无 | async executor 固化 commonPool：零测试 |
| C3-011 | P2 | 无 | BlockingSourceConsumeTask：零测试 |
| C3-012 | P2 | 无 | AbstractBatchProcessService.destroy 丢在途消息：零测试 |
| C3-013 | P2 | 无 | 上下文卫生不对称：无 attach/detach 配对断言（TestExecutionContextImpl 钉的是 complete 单次回调） |
| C3-014 | P3 | 无 | drainTo 满批返回值：零测试（条目自建议「补满批单测」，至今未补） |
| C3-015 | P2 | 无 | 杀链语义：ExecutorHelper/调度站点零测试 |
| C3-016 | P2 | 无 | submit/schedule 误 cancel：零测试 |
| C3-017 | P2 | 无 | scheduleWithRandomDelay 取消窗口：零测试 |
| C3-018 | P2 | 无 | bindCancel 参数反接：TestDefaultTaskExecutionQueue 从不调用 bindCancel |
| C3-019 | P3 | 无 | sync LoadingCache reload：零测试 |
| C3-020 | P3 | 无 | debounce 非原子：同 C1-004 |

#### C4 线程池（12 条：无×12）

| ID | 级 | 判定 | 依据/最近测试 |
|---|---|---|---|
| C4-001 | P1 | 无 | 见 §3.1 |
| C4-002 | P2 | 无 | 池关闭语义死配置：StandardThreadPoolExecutor/ThreadHelper 零测试 |
| C4-003 | P2 | 无 | 工厂零校验：TestDefaultTaskExecutionQueue 的 ThreadPoolConfig 仅作 ManualExecutor 泛型参数，无校验断言 |
| C4-004 | P2 | 无 | nop-cached-thread-pool 无上界：TestTaskExecutionGraph 消费该池，无上界断言 |
| C4-005 | P2 | 无 | 全局池借出即可 destroy：零测试 |
| C4-006 | P2 | 无 | updateThreadPool 缩容反置（与 C1-016 同站点）：零测试 |
| C4-007 | P3 | 无 | SyncThreadPoolExecutor/VirtualThreadTaskExecutor 契约：零测试 |
| C4-008 | P3 | 无 | Tomcat 死机件：零测试 |
| C4-009 | P3 | 无 | LocalCache async 落 commonPool：零测试（TestMapCache 是 MapCache 豁免面） |
| C4-010 | P3 | 无 | 非 daemon timer/metrics 重复绑定：零测试 |
| C4-011 | P2 | 无 | ThreadPoolConfig 死旋钮族：零测试 |
| C4-012 | P2 | 无 | nop-virtual-thread 治理面：零测试 |

#### C5 内存上界（8 条：无×7，部分×1）

| ID | 级 | 判定 | 依据/最近测试 |
|---|---|---|---|
| C5-001 | P2 | 无 | XLangSemantics 三 static CHM 无界：无容量/淘汰断言 |
| C5-002 | P2 | 无 | LocalCache 无界默认：TestCache#testSpeed 用 weakKeys 但无上界断言 |
| C5-003 | P2 | 无 | destroyOnRemove 断链族：无驱逐销毁断言 |
| C5-004 | P3 | 无 | FileWatcher 无界队列：FileWatcher/ZipFileWatcher 零测试 |
| C5-005 | P3 | **部分** | `TestRateLimitExecutor#testDebounce/#testThrottleCleanup` 钉住正常执行后 map 自清理（22129fa9f7 修复面）；「cancel 后 schedule 抛出条目滞留」残留路径零用例 |
| C5-006 | P3 | 无 | ResourceContentCache 无字节权重：零测试 |
| C5-007 | P3 | 无 | 容量 0 三处语义分裂：零测试 |
| C5-008 | P2 | 无 | ReflectionManager 弱键失效：无 GC/淘汰测试 |

#### C6 资源生命周期（13 条：无×13）

| ID | 级 | 判定 | 依据/最近测试 |
|---|---|---|---|
| C6-001 | P2 | 无 | shutdownNow wrapper future 永不终态化：DefaultScheduledExecutor/BindScheduledExecutor 零生命周期测试 |
| C6-002 | P2 | 无 | GlobalExecutors register 顶替销毁/unregister：零测试（消费方测试不触达注册表语义） |
| C6-003 | P2 | 无 | 全局执行器无平台级 destroy：`TestCoreInitialization` 钉 CoreInitialization 层写回，不钉执行器面僵尸窗口 |
| C6-004 | P2 | 无 | metrics 无注销对称：零测试 |
| C6-005 | P2 | 无 | 模型缓存销毁链三断点：TestResourceComponentManager/TestSimpleDslRegistry 无 destroy 断言 |
| C6-006 | P2 | 无 | `TestNioFileWatchService`（同类）钉注册失败存活（dbe5a83ec9 面）；unwatch 不摘 subEntries/交叉误伤零用例 |
| C6-007 | P2 | 无 | 非 daemon 线程族+LifeCycleSupport 吞异常：零测试 |
| C6-008 | P3 | 无 | IPool 死设施：零测试 |
| C6-009 | P2 | 无 | 停机排空配置零消费：零测试 |
| C6-010 | P2 | 无 | `TestCoreInitialization#testReinitializeWritesBackInitializers` 钉 reinitialize 写回机制，**不**断言 initializer 注册的 Cancellable 在 reinitialize 后仍有效——缺陷存在下该测试照常通过 |
| C6-011 | P2 | 无 | ShutdownHook CAS 反转：零测试 |
| C6-012 | P2 | 无 | 租户模型缓存清理双断：零测试 |
| C6-013 | P3 | 无 | ResourceDependsManager.close 遗漏：零测试 |

#### C7 长运行（17 条：无×15，部分×2）

| ID | 级 | 判定 | 依据/最近测试 |
|---|---|---|---|
| C7-007 | P1 | 无 | 见 §3.1 |
| C7-001 | P2 | 无 | BindScheduledExecutor fixedRate 三段劣化：零测试 |
| C7-002 | P2 | 无 | DefaultScheduledExecutor 补偿突发：零测试 |
| C7-003 | P2 | 无 | scheduleWithRandomDelay 链死：零测试 |
| C7-004 | P2 | **部分** | `TestLocalLockManager#testTryResetLeaseOnExpired/#testIsHoldingLockOnExpired` 钉过期/续租语义（真实 sleep）；但用例对时钟源不敏感——currentTimeMillis→nanoTime 的修复不会破坏也不会被验证，回拨场景零覆盖 |
| C7-005 | P2 | **部分** | `TestHighWatermarkSemaphore#testWatermark` 钉滞回语义（含把死区行为**钉为预期正确**，见 N1）；`#testAcquireStats` 钉统计计数；自愈路径不存在故无可钉 |
| C7-006 | P2 | 无 | DedupBlockingQueue 溢出孤儿：零测试 |
| C7-008 | P2 | 无 | dependencyMap 无淘汰：零测试 |
| C7-009 | P2 | 无 | LocalCache 无 scheduler：零测试 |
| C7-010 | P2 | 无 | throttleExecute 不可中断：无中断测试 |
| C7-011 | P2 | 无 | CycleSynchronizer 死区：零测试 |
| C7-012 | P2 | 无 | BlockingSourceConsumeTask Error 减员：零测试 |
| C7-013 | P3 | 无 | 墙钟线程更新延迟：零测试 |
| C7-014 | P3 | 无 | shutdown 窗口 future 永不完成：零测试 |
| C7-015 | P2 | 无 | `TestRateLimiter` 吞吐冒烟+计数器；等待不可中断零断言 |
| C7-016 | P3 | 无 | 突发窗口超卖：零测试（滞回语义被 N1 钉为正确） |
| C7-017 | P3 | 无 | ReactiveObjectMap 粘滞：零测试 |

#### C9 热路径（9 条：无×9）

| ID | 级 | 判定 | 依据/最近测试 |
|---|---|---|---|
| C9-001 | P2 | 无 | check-changed 全依赖图遍历：同 C7-007——唯一链路测试 @Disabled |
| C9-004 | P2 | 无 | 生成代码 Class.forName：零测试 |
| C9-010 | P2 | 无 | ResourceCacheEntry 直连使用方绕门：E3/E4/E5/E6 使用方在受审模块外，本三模块内零观察 |
| C9-002 | P3 | 无 | recordStats 常数税：无统计开关断言 |
| C9-003 | P3 | 无 | 2 层 CHM+强键：`TestXLangSemantics` 钉正确性面，非性能/容量面 |
| C9-005 | P3 | 无 | CycleSynchronizer 持锁回调：零测试 |
| C9-007 | P3 | 无 | `TestDictProvider` happy path，无分配/克隆断言 |
| C9-008 | P3 | 无 | ExecutorHelper：零测试 |
| C9-009 | P3 | 无 | LocalResourceLockManager 维护 O(N)：TestLocalLockManager 消费 cleanupTimer 但无成本/隔离断言 |

### 3.3 矩阵统计

| 判定 | 条数 | 占比 | 明细 |
|---|---|---|---|
| 有钉（缺陷本体） | **0** | 0% | — |
| 部分 | **7** | 5.9% | P2×4（C1-014、C2-016、C7-004、C7-005）+ P3×3（C1-013、C2-021、C5-005） |
| 无保护 | **111** | 94.1% | 含全部 5 条 P1 |
| 合计 | 118 | 100% | — |

分级透视：P1 5/5 无保护；P2 75 条中 71 无保护、4 部分；P3 38 条中 35 无保护、3 部分。现有测试套件对 118 条并发缺陷的**本体回归保护力≈0**；全部既有「钉」均落在历史修复轮的相邻面（putIfAbsent 原子性、resize 扩容失败、debounce 自清理、watcher 存活、VFS 关闭次序、flush 退出窗口），与 118 条缺陷本体无一重叠。

## 四、缺保护发现登记（缺回归保护，统一 P2）

> 编号自 `nop-kernel-concurrency-C10-001` 起。严重度统一按任务口径记 P2（缺口严重度，非缺陷严重度）。

**nop-kernel-concurrency-C10-001｜RoundRobinSupplier 收缩并发窗口无回归测试**
- 涉及缺陷：C1-001（P1）
- 缺口描述：该类仅有的 2 个测试均为单线程；收缩时「锁外 closeNext 旧数组尾段 × 并发 get 快照解引用」的 use-after-close 窗口零触达。生产触发链（Lettuce 连接池 + IoC 配置刷新自动 resize）意味着回归风险真实存在，而 `testResizeShrinkAndExpand` 在缺陷存在下照常通过，构成虚假安全感（见 N5）。
- 建议回归测试形态：两线程确定性交错测试——线程 A 在 resize(收缩) 的发布点与 closeNext 之间用栅栏/监视器冻结（仿 TestContextTaskQueue 的内部锁冻结手法或注入可阻塞的 AutoCloseable），线程 B 循环 get()，断言取出的资源`closed==false`；辅以「resize 后旧代资源在无在飞读者前不被关闭」的代际断言。

**nop-kernel-concurrency-C10-002｜ResourceCacheEntry 跨 entry 嵌套加载无死锁测试**
- 涉及缺陷：C2-001（P1）
- 缺口描述：嵌套三路径（xdef/x:config import/脚本）与双线程反序死锁、循环依赖 SOE 形态均零测试；现有唯一用例钉 reload-null 占位。
- 建议回归测试形态：构造 A→B→A 循环依赖的最小模型对（或 xdef import 环），双线程反序加载 + 带 watchdog 超时（如 5s 未完成即 fail），断言不挂死不 SOE；单线程环形加载断言类型化错误而非 StackOverflowError。

**nop-kernel-concurrency-C10-003｜周期任务异常绝缘与调度器语义零测试**
- 涉及缺陷：C3-001（P1）、C3-002、C3-003、C3-016、C3-017、C7-001、C7-002、C7-003、C7-014、C6-001
- 缺口描述：DefaultScheduledExecutor/BindScheduledExecutor/scheduleWithRandomDelay 全族零专项测试。周期任务抛 RuntimeException/Error 后是否续跑、fixedRate 重叠/积压/拒绝路径、取消传播、shutdown 窗口 future 终态化，全部无护栏——而这正是 C3-001 判 P1 的设施面。
- 建议回归测试形态：参数化的「任务第 N 次执行抛 X」用例（X=Exception 与 Error 各一），断言后续周期触发继续且计数递增；Bind 层用慢任务（>period）断言不重叠；scheduleAtFixedRate 拒绝注入断言 caller future completeExceptionally 而非静默停摆。

**nop-kernel-concurrency-C10-004｜GlobalExecutors/全局池注册表生命周期零测试**
- 涉及缺陷：C4-001（P1）、C4-002、C4-004、C4-005、C1-003、C1-015、C2-006、C6-002、C6-003
- 缺口描述：g_executors 注册表（register 顶替销毁/unregister 无释放/globalTimer 容量与隔离/destroy 置空竞态）零专项测试；现有测试仅作为消费方实例化，未断言任何注册表语义。C4-001 的修复（配置接线+隔离）若无回归护栏，极易回归。
- 建议回归测试形态：注册表语义表驱动测试（register 新增/顶替/unregister 幂等/destroy 后 get 的错误形态）；globalTimer 容量配置断言（config.maxPoolSize 与 CFG 值一致、不被工厂副作用改写）；destroy×并发提交的竞态用例（断言类型化异常而非 NPE）。

**nop-kernel-concurrency-C10-005｜check-changed 失效链零活测试（唯一集成测试 @Disabled）**
- 涉及缺陷：C7-007（P1）、C9-001、C9-010
- 缺口描述：模型缓存失效检测是 P1 正确性链，但三个受审模块内零活测试；唯一触及 getResourceDepends 的 TestXplTask#testWatch 整类 @Disabled（见 N3）。双漏检窗口（同 tick 双写/load-取 mtime 竞态）与 refreshMinInterval 门完全无护栏。
- 建议回归测试形态：窗口二用确定性双 mtime 注入（可编程 Resource.lastModified()）断言「load 期间 mtime 变更→不固化旧内容+新 mtime」；窗口一用同 tick 双写模拟断言重检触发；加一条 checkChanged 默认开启的冒烟（改文件→模型失效）。修复 C7-007 时先恢复/改写被 @Disabled 的链路测试。

**nop-kernel-concurrency-C10-006｜debounce/throttle 并发原子性与残留路径无回归测试**
- 涉及缺陷：C1-004、C2-005、C3-020、C5-005（残留面）
- 缺口描述：生产调用方（FileWatcher/ZipFileWatcher/CliWatchZipCommand）在用，但现有 3 个用例全是单线程 happy path；「并发同 key debounce 双执行/holder NPE」「cancel 后 schedule 抛出条目滞留」零覆盖。FileWatcher 本体亦零测试，修复验证只能靠 RateLimitExecutorImpl 侧。
- 建议回归测试形态：N 线程×同 key 并发 debounce 断言恰好执行一次（仿 TestICacheDefaults 的 16 线程×50 轮手法）；cancel 后使 schedule 抛出（注入故障 timer）断言 promiseMap 无残留条目。

**nop-kernel-concurrency-C10-007｜锁设施中断契约与失败路径泄漏无测试**
- 涉及缺陷：C2-007、C2-008、C3-006、C3-007、C7-010、C1-011
- 缺口描述：`TestLocalFileLock` 仅成功路径（tryLock(0) 失败路径 raf/channel 泄漏零覆盖）；`TestLocalLockManager` 无中断场景（lockInterruptibly 契约、acquireUninterruptibly 可中断性、LimitedExecutor 幻影 release 全零）；tryLockWithLease 的 latch 监视器不变式无断言。
- 建议回归测试形态：竞争双线程下 tryLock(0) 失败后断言句柄关闭（注入可探测 close 的 stream 工厂或 file-handle 计数）；对阻塞中的 acquire 发 interrupt 断言响应性；LimitedExecutor 中断路径断言 permits 恢复。

**nop-kernel-concurrency-C10-008｜模型缓存驱逐不销毁/销毁链断点零测试**
- 涉及缺陷：C5-003、C6-005、C6-012、C6-013、C7-008
- 缺口描述：ResourceComponentManager/ResourceLoadingCache/TenantAware 容器的「驱逐即销毁」契约与 destroy() 释放完整性零断言；CacheConfig 拷贝构造器缺字段这类回归只能靠契约测试拦截。
- 建议回归测试形态：带销毁探针的模型对象注入缓存，触发容量驱逐/clearCache/destroy，断言探针触发次数；CacheConfig 拷贝构造全字段镜像断言（防再漏字段）。

**nop-kernel-concurrency-C10-009｜ContextTaskQueue.syncGet 丢失唤醒面无测试（flush 面已有钉先例）**
- 涉及缺陷：C2-016
- 缺口描述：flush 退出窗口已有确定性复现测试（nop-api-core TestContextTaskQueue，内部锁冻结手法，反向抽查①确认）；同类的 syncGet drain→await 丢失唤醒窗口（enqueue 谓词 syncing>0 过窄 + flush 兜底静默放弃）零覆盖。该测试在受审三模块之外，属 api-core 治理通道，此处登记缺口并指认可复制手法。
- 建议回归测试形态：仿既有手法——冻结内部锁把纳秒窗放大为确定性步骤，构造「drain 完成后、await 前插入 enqueue+flush」交错，断言任务最终执行/唤醒到达；补「enqueue 返回 true 的等待方必然被后续 flush 唤醒」的不变式用例。

**nop-kernel-concurrency-C10-010｜池工厂校验与热更路径零测试**
- 涉及缺陷：C4-003、C4-006、C4-011、C4-012、C1-016、C5-007
- 缺口描述：newExecutor/ThreadPoolConfig 零校验（MAX_VALUE 队列、负值静默、core>max 无守卫）、updateThreadPool 收缩 IAE、死旋钮矩阵全零测试；「容量 0」三处语义分裂也无契约测试。
- 建议回归测试形态：表驱动参数校验测试（非法组合断言类型化异常而非静默/I裸 IAE）；热更 grow/shrink 双向冒烟（收缩后池仍可执行任务）；容量 0/负值跨工厂语义一致性断言。

**nop-kernel-concurrency-C10-011｜零调用潜伏设施缺「处置护栏」——修复或删除均无回归面**
- 涉及缺陷：C1-005、C1-006、C1-007、C1-010、C2-004、C2-010、C2-015、C7-003、C7-005、C7-006、C7-011、C7-016、C7-017、C9-005（族）
- 缺口描述：CycleSynchronizer/ReactiveObjectMap/ResourceMultiLock/scheduleWithRandomDelay/DedupBlockingQueue/HighWatermarkSemaphore（部分）等零生产调用方设施，其缺陷条目多为 deferred/删除二选一处置；当前删除无「引用已移除」护栏、修复无行为钉，任何方向处置都缺回归面。特别地 C7-005 的滞回语义被现有测试**钉为预期正确**（N1），自愈型修复会被现测试阻断。
- 建议回归测试形态：若删除——以编译期存在性断言或 archunit 类规则钉「类已移除」；若修复——按各缺陷条目的建议行为补最小语义钉（如 ReactiveObjectMap 首调不 NPE、ResourceMultiLock 排序获取跨锁集无环、CycleSynchronizer 代际清位）。C7-005 修复须同步修订 testWatermark 的死区断言。

**nop-kernel-concurrency-C10-012｜缓存容量治理面（无界默认/弱键失效/无淘汰账本）零测试**
- 涉及缺陷：C5-001、C5-002、C5-008、C7-008、C7-009、C9-002、C9-003
- 缺口描述：「未配置=无界」「弱键被值持键恒失效」「dependencyMap 只增不删」均为结构性缺陷，现有缓存测试只测原子性与功能，无任何容量/淘汰/回收断言，修复（有界 LRU、GlobalCacheRegistry 注册、逐条淘汰）后也无护栏防退化。
- 建议回归测试形态：容量上界行为测试（maximumSize 生效→驱逐发生→estimatedSize 有界）；weakKeys 有效性测试（去强引用+GC 提示后条目可回收）；dependencyMap 逐条淘汰后 estimatedSize 回落断言。

## 五、测试套件自身缺陷注记（N1~N5）

| # | 位置 | 问题 | 影响 |
|---|---|---|---|
| N1 | `nop-commons/.../concurrent/TestHighWatermarkSemaphore#testWatermark` | 把 C7-005 的滞回死区行为**作为预期正确语义钉死**（「新请求必须等待，即使释放到可以满足…应该超时而非获取成功」）。若按 C7-005 建议补自愈路径，该断言将失败 | 修复阻断风险：C7-005/C7-016 整改时必须同步修订本测试，否则修复被回归测试「证伪」 |
| N2 | `nop-core/.../store/TestDefaultVirtualFileSystemRefresh#testRefreshClosesOldZipOnlyAfterRebuild` | 断言 refresh 返回后旧 zip **立即** closed。C1-014/C2-021 的「退役代际/延迟销毁」修复（等在飞读者退出再关）会推迟关闭时机，与本断言冲突；且该测试只覆盖「修一半」的次序面，可能误导修复者以为窗口已被覆盖 | 修复阻断风险 + 虚假覆盖感 |
| N3 | `nop-xlang/.../xpl/TestXplTask`（整类 @Disabled） | 唯一覆盖 watch→debounce→checkChanged→模型重载整条链的集成测试被禁用，涉及 C1-004/C3-020/C7-007/C9-001/C9-010 的生产使用形态 | check-changed P1 链路的「休眠覆盖」：矩阵计为无保护；恢复或重写该测试应作为 C10-005 修复的一部分 |
| N4 | `nop-core/.../exceptions/TestErrorMessageManager` | 测试注释明确依赖「new ErrorMessageManager() 会把自己注册为全局错误消息管理器」这一**缺陷形态**（C1-009 this-escape 族），并以 @AfterEach 恢复全局槽 | 制度化了构造器副作用假设；C1-009 修复（构造器不再注册）时该测试的 setup/teardown 假设需同步调整（断言本身不受影响，非阻断级） |
| N5 | `nop-commons/.../concurrent/TestRoundRobinSupplier#testResizeShrinkAndExpand` | 测试名暗示覆盖 resize 收缩，实际为单线程语义用例，在 C1-001 缺陷存在下照常通过，不触达锁外关闭窗 | 虚假覆盖感：审阅者易误判 C1-001 已有回归保护（本矩阵已纠正计为无保护） |

另记（非缺陷，脆弱性观察）：`TestRateLimitExecutor`/`TestLocalLockManager`/`TestRateLimiter#testLimit` 依赖 `Thread.sleep` 真实时序（30ms~2s），在慢 CI 上存在漂移风险；`TestLocalLockManager#testLock` 消费 `GlobalExecutors.globalTimer()` 但从不销毁，依赖全局单例的跨用例存活——这正是 C1-009/C6-003 所述「测试常驻 JVM 重注册窗口」的现实土壤。

## 六、CL 线索测试观察（单列，不计入 118 条）

| CL | 内容 | 测试观察 |
|---|---|---|
| C1-CL-002 / C2-CL-004 | SequentialTaskExecutor.execute 丢弃 offer 返回值 | TestSequentialTaskExecutor 仅 1 用例钉纳秒单位（22129fa9f7 面），offer 满队列返回值零断言；且用例以 `Runnable::run` 同步执行，结构性掩盖该缺陷 |
| C1-CL-003 / C2-CL-006 | BlockingSourceConsumeTask 整批静默丢弃+中断杀循环 | 零测试 |
| C1-CL-017 | DefaultScheduledExecutor.setEnabled 运行期无效 | 零测试（TestRateLimitExecutor 构造 newSingleThreadTimer 未触达 enabled 语义） |
| C1-CL-018 | GlobalExecutors.unregister 移除不销毁 | 零测试（见 C10-004） |
| C4-CL-007/023/024、C5-CL-008~010/025~029、C7-CL-012/013/032~034、C9-CL-014/015/016/035/036 | 各维度集外线索 | 均落在零测试面（池/批处理/监听/xlang 注册表），无测试观察可登记；随对应 C10 缺保护项的设施面一并补齐 |

## 七、结论

1. **保护力结构性缺失**：118 条保留发现中 111 条（94.1%）无任何测试保护，5 条 P1 全部无保护；7 条「部分」中无一覆盖缺陷本体的核心窗口。三模块 1474 个测试方法几乎全部作用于功能正确性面，并发正确性断言仅 5 处，且全部是历史修复轮的相邻面护栏。
2. **修复批次必须自带回归测试**：鉴于现套件零兜底，Phase 8 各修复批次应把本文件 §4 的 C10-001~012 视为修复验收的一部分（每缺陷合入时补钉），而非独立后补批次。
3. **两处修复阻断预警**：N1（HighWatermarkSemaphore 死区被钉为正确）、N2（VFS 旧 zip 立即关闭被钉死）需在 C7-005/C1-014 修复的同一变更集内修订，否则修复会被现有测试驳回。
4. **休眠覆盖**：TestXplTask（@Disabled）是 check-changed P1 链路唯一的端到端形态参考，恢复/改写优先级应随 C7-007 修复排定。

---

*审计方法备注：判定基于逐文件打开核对（14 个并发相关测试类全读 + 关键源文件 RoundRobinSupplier/ReflectionManager/ContextTaskQueue 测试交叉核对 + 全套件 grep 反证零测试面）；「有钉」反向抽查 4 条均经 git log 证实修复+测试同 commit。本文件为 C10 维度终版，输入口径锁定为八维最终保留项 118 条（P1×5）。*

## C10 复核结论（独立复核子代理，2026-10-17）

复核人：C10 独立复核子代理（与初审相互独立）。基线 HEAD `8c13908bfd` 证实一致。方法：抽验 13 行矩阵条目（≥规定的 10 行：P1×5 全查 + 部分×5 + 无保护×3，全部亲自打开被引测试与被测源码核对）、C10-001~012 逐条对账、N1~N5 逐条打开核实、21 个「零测试面」声明全量 grep 反证、反向抽查 4 commit 逐一 `git show` 核实、八维 `## 最终保留项` 全量对账。本节仅追加，未改动上文任何初审内容。

### 1. 矩阵抽验明细（13 行）

| 行 | 初审判定 | 复核裁定 | 核对证据 |
|---|---|---|---|
| C1-001 | 无 | **维持** | `TestRoundRobinSupplier` 实测 2 用例均单线程；`testResizeShrinkAndExpand` 收缩后 `get()` 重读 `this.objects` 新数组，缺陷存在下照常通过；`RoundRobinSupplier.java:83-86` 证实 `closeNext(toClose, size)` 在 finally 中锁外执行——缺陷面与无保护判定均成立 |
| C2-001 | 无 | **维持** | `TestResourceCacheEntry` 唯一用例钉 reload-null 占位；全套件仅 2 文件触及 ResourceCacheEntry；`TestResourceComponentManager`(2)/`TestSimpleDslRegistry`(1) 均单线程无环 happy path，无嵌套/反序/环形用例 |
| C3-001 | 无 | **维持** | 三模块测试 `scheduleAtFixedRate/scheduleWithFixedDelay/scheduleWithRandomDelay` 零引用；`TestRateLimitExecutor` 3 用例任务体仅 countDown/incrementAndGet，从不抛异常 |
| C4-001 | 无 | **维持** | GlobalExecutors/ThreadPoolConfig/StandardThreadPoolExecutor/updateThreadPool 测试引用仅 3 处且全为消费（TestLocalLockManager:30、TestTaskExecutionGraph:20、TestDefaultTaskExecutionQueue:49 返回 null 的 ManualExecutor.getConfig），零容量/隔离/校验断言 |
| C7-007 | 无 | **维持** | `TestXplTask` 类级 `@Disabled`（:37）且 `testWatch` 确调 `getResourceDepends`（:66）；`TestResourceLoadingCache` 单线程命中、`isResourceChanged` 恒 false；失效链零活测试成立 |
| C1-014 | 部分 | **维持** | `testRefreshClosesOldZipOnlyAfterRebuild` :65-66 钉「重建期间不关 + refresh 返回后已关」次序面；在飞读者窗口与 destroy 路径零断言 |
| C2-016 | 部分 | **维持** | `TestContextTaskQueue#testFlushExitWindowDoesNotLoseTask` 以内部锁冻结确定性钉 flush 退出面；该类 2 用例均不触 syncGet，drain→await 面零覆盖 |
| C5-005 | 部分 | **维持** | `testDebounce`/`testThrottleCleanup` 经反射读 promiseMap 钉正常执行后自清理；cancel-后-schedule-抛出残留路径零用例 |
| C7-004 | 部分 | **维持** | 两个过期/续租用例真实 sleep（lease=100ms/sleep=250ms）；`LocalResourceLockManager` 7 站点 `CoreMetrics.currentTimeMillis()`（:94~:259）——换 nanoTime 两用例既不破坏也不被验证，回拨场景零覆盖，判定成立 |
| C7-005 | 部分 | **判定维持、依据修正** | 见 §2 修正 1：testWatermark 从未进入 overHighWatermark 死区态，「把死区行为钉为预期正确」不成立；「部分」改以「钉住单线程滞回准入语义（相邻面）+统计计数，死区本体零触达」支撑，仍符合「部分」定义 |
| C2-013 | 无 | **维持** | `testGetFromCacheConcurrentBuildOnlyOnce`（8 线程 CyclicBarrier）钉 getFromCache 构建去重；register* 的 check-then-put 面零触达 |
| C6-010 | 无 | **维持** | `testReinitializeWritesBackInitializers` 仅断言 initializers 字段写回/destroy 复位，不涉 initializer 注册的 Cancellable 有效性，缺陷存在下照常通过 |
| （顺带核）C1-011/C2-022/C3-018/C3-009/C5-002/C6-006 | 无 | **维持** | TestLocalFileLock 仅成功路径；TestDefaultTaskExecutionQueue 10 用例全 ManualExecutor、bindCancel 全套件测试零调用；LocalCache getAsync/getAllAsync 零测试；TestCache#testSpeed 用 weakKeys 无上界断言；TestNioFileWatchService 无 unwatch 用例 |

### 2. 修正行清单（4 处，均不改变矩阵统计）

1. **C7-005 行依据修正**：`HighWatermarkSemaphore` 的 overHighWatermark 标志仅在「usedPermits<lowWatermark 时超高获证」的突发窗口置位；`testWatermark` 全程未进入该态（used 峰值 9<10），:19/:23 两处 assertFalse 失败的真实原因是容量不足（8+3>10、9+2>10），在任何自愈型修复（含 C7-005 建议的租约/forceReset/可观测三方案）下断言结果均不变。原文「含把死区行为**钉为预期正确**」应删，替换为「钉住单线程滞回准入语义（相邻面）；死区本体（标志置位→跨低水位清除唯一路径）零触达」。
2. **C7-016 行括注修正**：「（滞回语义被 N1 钉为正确）」同上不成立，该行径记「零测试」即可（突发窗口超高获证路径同样零触达）。
3. **§2.1 资产盘点数字勘误**（实测，grep 口径 `^\s*@Test(\s|\(|$)`）：文件数 53/94/113=260 **精确**；@Test 总数 **1474 精确**，但分模块拆分应为 **346/502/626**（原文 ~330/~460/~684）；并发原语文件按文中口径（CountDownLatch/CyclicBarrier/ExecutorService/new Thread）实测 **8/3/0=11**（原文 9/6/2，且正文「共 14 个」与表内合计 17 自相矛盾）。
4. **§2.1「真并发断言仅 5 处」漏计 2 处**：`FastIntegerFormatterTest#testThreadSafety`（100 线程×1000 轮共享 Format 实例正确性断言）、`TestAesEncryptedValueFormat#testConcurrentEncryptDistinctIvsNoCorruption`（16 线程×50 轮 IV 唯一性+可解密断言）。两者均为真并发正确性断言，但保护对象（text formatter/AES cipher）不在 118 条缺陷面内——「全部真并发断言与 118 条缺陷本体无一重叠」的结构性结论**不降反升**，仅需把「仅 5 处」改记「7 处（含 2 处漏计，均不保护 118 条任一缺陷）」。

### 3. 缺保护登记 C10-001~012 裁定表

| 编号 | 涉及缺陷 ID 与八维文件自洽 | 建议测试形态可执行 | 裁定 |
|---|---|---|---|
| C10-001 | ✓（C1-001） | ✓ 锁冻结/注入可探测 close 的资源，两手法均有在库先例（TestContextTaskQueue、TestRoundRobinSupplier.TestResource） | 维持 |
| C10-002 | ✓（C2-001） | ✓ 循环模型对+watchdog 超时可行 | 维持 |
| C10-003 | ✓ 10 ID 均属调度器族，与 C3/C7/C6 终裁一致 | ✓ | 维持 |
| C10-004 | ✓ 9 ID 均属 GlobalExecutors 注册表族 | ✓ | 维持 |
| C10-005 | ✓（C7-007/C9-001/C9-010） | ✓ 可编程 mtime 注入可行；「先恢复 @Disabled 链路测试」与 N3 呼应正确 | 维持 |
| C10-006 | ✓ 4 ID 一致（TestRateLimitExecutor 实测 3 用例全单线程 happy path） | ✓ 16 线程×50 轮手法在库（TestICacheDefaults） | 维持 |
| C10-007 | ✓ 6 ID 一致（TestLocalFileLock 实测仅成功路径；TestLocalLockManager 无中断场景） | ✓ | 维持 |
| C10-008 | ✓ 5 ID 一致 | ✓ | 维持 |
| C10-009 | ✓（C2-016） | ✓ 手法先例经实证（同文件 flush 面） | 维持 |
| C10-010 | ✓ 6 ID 一致 | ✓ | 维持 |
| C10-011 | ✓ 14 ID 均为零调用/条件化设施 | ⚠ C7-005 相关表述须修正（见 §2 修正 1）：「滞回语义被现有测试钉为预期正确、自愈型修复会被现测试阻断」不成立，修复不被阻断；改为「testWatermark 注释按死区意图书写且与断言脱节（used=11 系陈旧草稿注释），修复时同步清理注释即可，断言无需修订」 | **修正后维持** |
| C10-012 | ✓ 7 ID 一致（TestCache#testSpeed weakKeys 无上界断言已实证） | ✓ | 维持 |

覆盖面附注（不占新增编号）：C3-005（throttleExecute 漏 Error）未落入 C10-006/C10-007 任一涉及清单，建议并入 C10-007；C3-015（杀链语义）建议并入 C10-003 或 C10-013。

### 4. 补充发现（复核新增缺保护登记，续 C10-013 起）

**nop-kernel-concurrency-C10-013｜DefaultTaskExecutionQueue/SequentialTaskExecutor 提交-取消面「名义覆盖、实质零保护」**
- 涉及缺陷：C2-022、C3-018、C3-008（关联 C3-013）
- 缺口描述：与 N5 同型的误导性覆盖——`TestDefaultTaskExecutionQueue` 有 10 个用例但全部注入 ManualExecutor（execute 仅入队、drainPending 在调用线程同步执行），结构性绕开 bin 锁内真实提交路径；`bindCancel` 全套件测试零调用（grep 证实），C3-018 参数反接缺陷无任何触达；`TestSequentialTaskExecutor` 唯一用例以 `Runnable::run` 同步执行（与初审 C1-CL-002 判断一致），offer 返回值丢弃缺陷被结构性掩盖。审阅者易据「队列测试很全」误判该族已有保护。
- 建议回归测试形态：注入真实 IThreadPoolExecutor 的提交冒烟（断言提交异常不丢 submittedCount、states 计数自洽）；bindCancel 正反接各一（断言 cancel 只到达目标 future）；满队列下 execute 断言 OverflowBlockingQueue 溢出策略生效而非静默丢弃。

**nop-kernel-concurrency-C10-014｜BlockingSource 批处理消费链零测试**
- 涉及缺陷：C3-011、C3-012、C7-012、C3-014
- 缺口描述：`BlockingSourceConsumeTask`、`AbstractBatchProcessService`、`IBlockingSource.drainTo/takeMulti` 三模块测试零引用（grep 证实，与 §2.3 一致但初审未单独立登记）；消费线程 Error 永久减员、destroy 丢在途消息、drainTo 满批返回值少计全部无护栏。
- 建议回归测试形态：注入抛 Exception 与 Error 各一的 consumer，断言 Exception 后消费继续、Error 后线程不减员或有告警；destroy 时在途消息排空/计数断言（不静默丢）；drainTo 边界用例（maxElements=1/恰满/超满）断言返回值。

### 5. N1~N5 复核

| # | 初审主张 | 复核裁定 |
|---|---|---|
| N1 | testWatermark 把死区行为钉为预期正确，自愈修复将使断言失败 | **部分成立、核心主张修正**。注释层成立：注释确写「新请求必须等待，即使释放到可以满足…应该超时而非获取成功」，且 `(used=11)`/`(11->7)`/`(7->3)` 系与断言脱节的陈旧草稿注释（断言期望这些获取失败）。断言层不成立：overHighWatermark 全程 false（置位需 usedPermits<lowWatermark 时超高获证，测试未构造该前置态），:19/:23 失败均为容量不足，C7-005 三项建议修复均不改变任何断言结果。影响由「修复阻断风险」降格为「注释级误导（清理注释即可，非阻断）」 |
| N2 | refresh 返回后旧 zip 立即 closed 被钉死，与退役代际修复冲突 | **维持**。:65-66 断言实证；「等在飞读者退出再关」的修复会推迟关闭时机，阻断风险成立 |
| N3 | TestXplTask 整类 @Disabled，check-changed 链唯一集成测试休眠 | **维持**。类级 @Disabled（:37）实证，testWatch 确覆盖 watch→debounce→checkChanged→模型重载全链 |
| N4 | 测试依赖构造器注册全局槽缺陷形态，@AfterEach 恢复 | **维持（非阻断定性正确）**。小注：断言全部走本地 `mgr` 引用，功能性上并不依赖该副作用，宜读作「注释声明副作用并以 @AfterEach 补偿全局污染」 |
| N5 | testResizeShrinkAndExpand 名不副实，缺陷存在下照常通过 | **维持**。单线程实证，收缩后 get 走新数组，不触达锁外关闭窗 |

另记段（Thread.sleep 漂移风险、TestLocalLockManager 不销毁 globalTimer）核实无误，维持。

### 6. 口径一致性核对（与八维 `## 最终保留项` 逐文件对账）

- **总数 118** ✓：C1×17 + C2×22 + C3×20 + C4×12 + C5×8 + C6×13 + C7×17 + C9×9（C9-006 驳回未计入，与 C9 文件一致）。
- **P1×5** ✓：C1-001/C2-001/C3-001/C4-001/C7-007，与各维终裁逐一相符。
- **P2×75** ✓（9+13+16+7+4+11+12+3）；**P3×38** ✓（7+8+3+4+4+2+4+6）。各维内 ID 级对照（ID 与判级）零偏差。
- 矩阵内部统计自洽 ✓：部分×7 = P2×4（C1-014/C2-016/C7-004/C7-005）+ P3×3（C1-013/C2-021/C5-005）；无×111；P2 无 71、P3 无 35。
- 唯一勘误在 §2.1 资产盘点（见 §2 修正 3/4），不影响 118 条口径。

### 7. 反向抽查复核（4/4 证实）

`git show` 逐一核实：① `5ee798e02e` 含 ContextTaskQueue flush 退出窗口修复（endProcessIfIdle 锁内原子判定）+「P1-5 竞态经内部锁冻结确定性复现」红验证同 commit；② `22129fa9f7` 含 RoundRobinSupplier 扩容失败关新建资源修复 + TestRoundRobinSupplier 新增同 commit；③ `87d09323c5` ICache 默认实现原子化 + TestICacheDefaults（491 行）同 commit，`22129fa9f7` 另含 LocalCache.putIfAbsent 改 Caffeine 原子 + TestCache 扩充；④ `dbe5a83ec9` watchKeyMap 并发+IOException 杀循环修复 + TestNioFileWatchService 同 commit。四条「钉」均为历史修复轮回归护栏、保护面与 118 条缺陷本体无重叠——初审核心结构性事实成立。

### 8. 复核终裁

**最终统计维持**：118 条（P1×5 / P2×75 / P3×38）、有钉×0、部分×7、无保护×111、「本体回归保护力≈0」的结构性结论，经 13 行抽验 + 21 类零测试面 grep 反证 + 4 commit 反向抽查 + 八维全量对账后**全部维持**。修正 4 处（C7-005 行与 C7-016 行依据表述、N1 影响定级由「修复阻断」降为「注释级误导」、§2.1 三处盘点数字），补充登记 C10-013/014 两条，C10-011 修正后维持。初审结论 §七需随之微调：第 3 条「两处修复阻断预警」应改为「一处」（N2；N1 降格为注释清理项）。其余结论（修复批次自带回归测试、TestXplTask 恢复优先级）复核无异议。C10 维度审计可关闭。
