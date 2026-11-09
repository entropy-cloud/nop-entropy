# nop-kernel 销毁链与 BlockingSource 生命周期（R4-14 G27 后半，17 成员）

> Status: active
> Last Reviewed: 2026-11-09
> Source: plan 2548（`ai-dev/plans/2548-cra-phase8.54-r4-14-kernel-destroy-blockingsource-b.md`）Phase 1；批次定义 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二 :129 R4-14 行（17 finding ID / nop-kernel / PA=框架核心 plan-first）；成员发现 = `ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/`（C4-thread-pools.md / C4-round2.md / C5-memory-bounds.md / C6-resource-lifecycle.md / C6-round2.md / C7-long-running.md / C7-round2.md / C9-hot-path.md）
> Protected Area 授权：全部站点在 `nop-kernel/nop-commons`、`nop-kernel/nop-core`、`nop-kernel/nop-xlang`、`nop-utils/nop-commons-java21` = AGENTS.md「框架核心引擎」= **plan-first**。授权载体 = 本文档 + 独立 draft review + 回归面清单。
> 传导先例：[kernel-lock-discipline-interrupt-contract-and-executor-destroy-semantics.md](kernel-lock-discipline-interrupt-contract-and-executor-destroy-semantics.md)（R4-13 同模块前批，本批**复用其 C4-002 destroy 语义裁定**，不重建）；[pool-facility-hardening.md](pool-facility-hardening.md)

## 一、设计结论（六条裁定）

1. **裁定 A（提交失败必须结算 future）**：`ExecutorHelper.submit/schedule` 系列在**提交环节抛异常**（`RejectedExecutionException`，池已 destroy/shutdown）时，返回的 `CompletableFuture` 必须 `completeExceptionally`。成员：**C6-001（P1）**——本批唯一 P1。
   - 现状：`future` 在提交前创建，`executor.submit(...)` 抛出即逃逸，`future` 永不完成 → 等待方永久挂起。
   - 改法：提交点 try/catch，`catch (RuntimeException e) { future.completeExceptionally(e); throw e; }`（既结算又不改变调用方可见的抛出语义）。
   - **失效面**：`submit(Callable)` / `submit(Runnable,result)` / `schedule(Callable,delay,unit)` / `scheduleAtFixedRate` / `scheduleWithFixedDelay` 全部五个提交入口 + `GlobalExecutors` 的池注册路径。F3 全枚举见 §三。

2. **裁定 B（全局池生命周期：可重注册 + 平台级 destroy）**：`GlobalExecutors` 的 `g_executors` 缓存必须在 `destroy()` 后**移除条目**（而非保留死实例），使后续 `computeIfAbsent` 重建；并新增平台级 `destroyAll()` 供宿主关闭时统一 drain。成员：C4-005、C6-003。
   - C4-005 现状：`getExecutor(name).destroy()` 后 `g_executors` 仍持有死实例，`computeIfAbsent` 不重建 → 该名字永久不可用。
   - 改法：`destroy()` 语义改为「先移除注册表条目，再销毁实例」；`GlobalExecutors.register()` 已如此（`put` + destroy old），复用其形态。
   - `destroyAll()`：遍历 `g_executors`，逐个 destroy + 清表；宿主 `nop-boot` 的关闭链接线。**接线点归属**：`nop-boot` 非本批 PA 范围（属 2.2 批），本批只提供 API + 在 `nop-commons` 内自测，宿主接线登记 successor。

3. **裁定 C（指标绑定注销对称）**：`ExecutorHelper.registerMetrics` 返回可注销的句柄（或记录已绑定的 Meter 列表），池 `destroy()` 时注销。成员：C6-004。
   - 现状：`registerMetrics` 绑完就忘，池替换后旧 meter 永久滞留 + 新实例静默失去指标。
   - **注意**：这是 R4-13 C8-008 Deferred 的「换 registry 后重绑缺失」同族面——本批只做**池 destroy 时注销**，不做「换 registry 自动重绑」（后者归 R-G）。

4. **裁定 D（缓存 destroyOnRemove 断链族）**：`CacheConfig` 拷贝构造器补齐 `destroyOnRemove`/`refreshAfterWrite`；`ResourceLoadingCache` / `TenantAwareResourceLoadingCache` / 模型缓存驱逐路径执行销毁回调。成员：C5-003、C6-005、C6-012。
   - C5-003 现状：`CacheConfig(CacheConfig)` 漏拷两字段 → `destroyOnRemove` 在克隆路径恒 false。
   - C6-005：`ResourceLoadingCache` 驱逐/关闭路径上模型对象的 `destroy` 结构性不可达。
   - C6-012：`TenantAwareResourceLoadingCache.tenantCaches` 容器未开 `destroyOnRemove` 且值类型不触发。

5. **裁定 E（阻塞等待必须可中断/有上界）**：成员 C7-010（`ExecutorHelper.throttleExecute` 的 `acquireUninterruptibly`）、C7-015（`DefaultRateLimiter` 的 `sleepMicrosUninterruptibly` + `IRateLimiter.acquire()` 默认无上界）、C7-002（`scheduleAtFixedRate` 错过后补偿突发）。
   - C7-010：改 `tryAcquire(timeout)` 循环 + 检查中断/池状态；超时抛类型化异常而非永久挂起。
   - C7-015：`IRateLimiter` 增 `tryAcquire(timeout)`；默认 `acquire()` 给上界（避免 20 分钟不可打断）。
   - C7-002：`scheduleAtFixedRate` 的错过补偿——`ScheduledThreadPoolExecutor` 原生 fixed-rate 在延迟后会突发补偿；改为**不补偿**（延迟后按原节奏继续）或在 javadoc 显式声明补偿语义。**注意**：切换语义会改变既有调度行为，需在 §五 回归面覆盖 `nop-job`/`nop-batch` 的周期任务。

6. **裁定 F（监听/注册表生命周期 + 非 daemon 线程）**：成员 C6-006（`FileWatchEntry` unwatch 不摘父 subEntries → 单调累积；同路径重复 watch 共享 `WatchKey` 导致注销交叉误伤）、C6-007（`NioFileWatchService` 等非 daemon 线程阻止 JVM 退出 + 关闭路径缺 `awaitTermination`）、C7-008（`ResourceDependsManager.dependencyMap` 无逐条淘汰）、C7-011（`CycleSynchronizer` 状态机死区）、C4-011（`ThreadPoolConfig` 5 字段死旋钮）、C4-012（nop-virtual-thread 误导性开关）、C9-004（`XLangSemantics` 每次 `Class.forName`）。
   - C7-011 死区：**注意与 R4-13 的 C1-010 同文件**——C1-010 修的是「旧周期不刷新周期状态」（generation 守卫，已在 2547 完成）；C7-011 是「推进者死亡后 processing 永久 true、beginCycle 永久抛错」——**不同失效面**，本批需新增「beginCycle 在 processing 期间可被强制重置」或「超时自动清理」的逃生门。**必须先读 2547 的改动，避免语义冲突**（§四 显式登记）。
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

## 三、逐成员裁定表

| Finding | 文件（live 锚） | 唯一形态 | 拒绝方案 |
|---|---|---|---|
| **C6-001** | `nop-commons/.../executor/ExecutorHelper.java:195-273`（五个 submit/schedule 入口） | 提交点 try/catch → `future.completeExceptionally(e)` 后重抛；五个入口同形态 | 仅文档声明（等待方仍挂） |
| C4-005 | `.../executor/GlobalExecutors.java:32-36,51-62,102-111` | `destroy()` 先移除 `g_executors` 条目再销毁（复用 `register()` 形态） | 清空整个 map（误伤其他池） |
| C4-011 | `.../executor/ThreadPoolConfig.java:23,27`（+ `NamedThreadFactory`） | 死旋钮**删除**或接线；倾向删除 + owner doc 声明（`@ConfigBean` 契约变更需登记） | 保留假承诺 |
| C4-012 | `nop-utils/nop-commons-java21/.../Jdk21CommonConfigs`；`CommonConfigs.java:67` | 默认值改 false 或显式声明「仅注册不生效」；destroy 回收 | 静默保留 |
| C5-003 | `.../cache/CacheConfig.java:43-52`（拷贝构造） | 补 `destroyOnRemove`/`refreshAfterWrite` 拷贝 | 无 |
| C6-003 | `.../executor/GlobalExecutors.java:32-111` | 新增 `destroyAll()`（遍历 + 清表）；宿主接线登记 successor | 只做 API 不测 |
| C6-004 | `.../executor/ExecutorHelper.java:201-208`；`.../cache/LocalCache` | `registerMetrics` 返回注销句柄，`destroy()` 时注销 | 换 registry 重绑（R-G） |
| C6-005 | `nop-core/.../resource/cache/ResourceLoadingCache.java:106-113` | 驱逐/关闭路径执行模型 `destroy` 回调 | 无 |
| C6-006 | `nop-core/.../resource/watch/FileWatchEntry.java:28,74-80` | `unwatch` 摘除父 subEntries；同路径重复 watch 复用同一 entry（不共享 WatchKey 注销） | 无 |
| C6-007 | `nop-core/.../resource/watch/NioFileWatchService.java:58,195-211` | 线程 daemon 化 + 关闭路径 `awaitTermination` | 无 |
| C6-012 | `nop-core/.../resource/tenant/TenantAwareResourceLoadingCache.java:38-41,56-58` | 容器开 `destroyOnRemove` + 值类型销毁 | 无 |
| C7-002 | `.../executor/DefaultScheduledExecutor.java:126-129` | 显式声明/关闭 fixed-rate 补偿（见裁定 E 注） | 无 |
| C7-008 | `nop-core/.../resource/deps/ResourceDependsManager.java:47,68-95,155-167` | 逐条淘汰（依赖集随缓存条目失效而清理） | 无 |
| C7-010 | `.../executor/ExecutorHelper.java:240-260` | `tryAcquire(timeout)` 循环 + 中断/池状态检查 + 超时类型化异常 | `acquireUninterruptibly`（现状） |
| C7-011 | `.../concurrent/CycleSynchronizer.java:36-46,48-64,66-106` | 死区逃生门（见 §四 与 R4-13 的分工） | 无 |
| C7-015 | `.../ratelimit/DefaultRateLimiter.java:50-58`；`IRateLimiter.java:33-39` | 增 `tryAcquire(timeout)`；默认 `acquire()` 给上界 | 无 |
| C9-004 | `nop-xlang/.../exec/XLangSemantics.java:401-423,631-637,1091-1099` | 复用 `CLASS_MODELS` 缓存（R4-13 C2-019 已建） | 每次 `Class.forName` |

## 四、跨成员 / 跨批共享机制

1. **`ExecutorHelper` 五入口同形态**（C6-001）：五个提交入口必须**同 diff** 加结算，禁止只修 `submit(Callable)`。参照 `nop-batch` 的 `C6-002`（提交点完成兜底）先例。
2. **`GlobalExecutors` 注册表语义**（C4-005 / C6-003）：`register`（已正确：put + destroy old）、`unregister`（已存在）、`destroy`（**本批新增移除语义**）、`destroyAll`（本批新增）四者必须一致——注册表条目生命周期 = 实例生命周期。
3. **与 R4-13（plan 2547）的分工（重要，防冲突）**：
   - `CycleSynchronizer`：C1-010（旧周期不污染新周期，generation 守卫）**已由 2547 完成**；C7-011（推进者死亡 → processing 永久 true）是本批新面，改的是 `beginCycle` 的**逃生门**，**不得回退 2547 的 generation 语义**。
   - `DefaultThreadPoolExecutor.destroy`：C4-002（有界等待、仅 true&&>0）**已由 2547 完成**；本批 C4-005/C6-003 只管**注册表移除**与**平台级 destroyAll**，不改 destroy 内部的等待逻辑。
   - `XLangSemantics.CLASS_MODELS`：C2-019（占位模式）**已由 2547 完成**；本批 C9-004 只是**复用该缓存**，不重建。
4. **缓存销毁回调链**（C5-003 / C6-005 / C6-012）：三者是「配置漏拷 → 驱逐不触发 → 租户容器不开」的同一条链，必须**同批按依赖序**修（C5-003 先，否则 C6-005/C6-012 即使改对也不会被触发）。

## 五、回归面清单（PA 授权必附）

| 改动 | 消费方（live 锚） | 必跑回归 |
|---|---|---|
| **`ExecutorHelper.submit/schedule` 结算**（C6-001） | 全平台所有 `IThreadPoolExecutor` 使用者 | `nop-kernel` 全量 + `nop-job`/`nop-task`/`nop-batch` 的池消费面 |
| **`GlobalExecutors.destroy/destroyAll`**（C4-005/C6-003） | `GlobalExecutors` 四类池（defaultWorker/cached/globalWorker/timer）全部消费方 | `nop-kernel` 全量；`nop-core-framework/nop-boot` 编译级（接线归 2.2） |
| **`ThreadPoolConfig` 字段删除/接线**（C4-011） | `@ConfigBean` 配置面（若有 yaml 配置这些键） | 全仓 grep 配置键 + `nop-kernel` 全量 |
| **`CacheConfig` 拷贝/销毁链**（C5-003/C6-005/C6-012） | `nop-core` 模型缓存、租户缓存、`nop-orm` 模型 | `nop-core` 全量 + `nop-xdefs` + `nop-orm` 模型加载面 |
| **`FileWatchEntry`/`NioFileWatchService`**（C6-006/C6-007） | `nop-core` 资源监听（开发期热加载） | `nop-core` 全量 |
| **`ResourceDependsManager`**（C7-008） | `nop-core` 依赖分析 | `nop-core` 全量 |
| **`CycleSynchronizer` 逃生门**（C7-011） | **零生产调用方**（全仓仅自身） | `nop-commons` 全量 |
| **`IRateLimiter`/`DefaultRateLimiter`**（C7-015） | `nop-batch` 的 `RateLimitConsumer`、`nop-ai-gateway` 准入 | `nop-kernel` 全量 + `nop-batch` |
| **`ExecutorHelper.throttleExecute`**（C7-010） | 全仓 `throttleExecute` 调用方（grep） | `nop-kernel` 全量 |
| **`DefaultScheduledExecutor.scheduleAtFixedRate`**（C7-002） | `nop-job` 扫描、`nop-batch` 周期、`nop-wf` 扫描 | `nop-kernel` 全量 + `nop-job` + `nop-wf` |
| **`XLangSemantics` 静态方法调用**（C9-004） | 全平台 XLang 生成代码 | `nop-xlang` 全量 + `nop-core` |
| **`nop-commons-java21` 虚拟线程开关**（C4-012） | 虚拟线程执行路径 | `nop-commons-java21` 编译 + `nop-kernel` 全量 |

**E1 全量门禁**：`./mvnw -o clean install -DskipTests -T 1C -pl '!nop-runner/nop-cli-core,!nop-runner/nop-cli'` BUILD SUCCESS（沿 2547 先例，排除已知面）。
**E3 分解通道**：`./mvnw test -pl nop-kernel -am` + 上表逐条。

## 六、实施顺序（依赖驱动）

1. **组 1（P1，最先）**：C6-001（五入口结算）
2. **组 2（池生命周期 + 指标）**：C4-005、C6-003、C6-004
3. **组 3（缓存销毁链，依赖序）**：C5-003 → C6-005 → C6-012
4. **组 4（监听/注册表）**：C6-006、C6-007、C7-008
5. **组 5（等待可中断/有界）**：C7-010、C7-015、C7-002
6. **组 6（死旋钮/虚拟线程/类加载缓存）**：C4-011、C4-012、C9-004
7. **组 7（CycleSynchronizer 逃生门，最后——需先复核 2547 语义）**：C7-011

## 七、拒绝方案汇总（内联于 §三 表末列，共 17 条）
