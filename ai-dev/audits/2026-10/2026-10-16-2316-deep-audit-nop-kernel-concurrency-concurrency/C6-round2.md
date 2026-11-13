# C6 维度第 2 轮深挖报告：资源生命周期与泄漏（nop-kernel 并发设施）

## 头部信息

| 项 | 内容 |
|---|---|
| **审计单元** | nop-kernel-concurrency / C6（资源生命周期与泄漏） |
| **仓库路径** | `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit` |
| **分支** | `concurrency-audit-remediation`（HEAD `8c13908bfd`） |
| **轮次** | 第 2 轮深挖（盲审） |
| **审计人** | C6 维度第 2 轮子代理（盲审轮次，独立任务） |

### 本轮输入

- 第 1 轮报告 `C6-resource-lifecycle.md`（C6-001~008：P1×2 / P2×5 / P3×1 + CL-011 + 17 行资源配对表 + 正面样本清单 + 同族去重预注）——唯一允许读取的 `ai-dev/` 文件。
- 下发文本中的既有立案族标题列表（用于同族去重）：C1-015（register 锁外 destroy）、C4-005（借出可毁）、C4-010（newSingleThreadTimer 非 daemon）、CL-018（unregister 不销毁零调用方）、CL-029（ShutdownHook CAS 线索）等，均为标题级信息。
- live code（全量逐文件阅读 + 全仓 grep 交叉核验）。

### 盲审纪律声明

本报告为盲审轮次产出。审计过程中**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下除本轮指定输入 `C6-resource-lifecycle.md` 外的任何文件；未读取同目录其他维度（C1–C5、C7+）的任何文件。所有判定均基于 live code 直接取证，grep 命令与文件行号可复核。

### 本轮目标（下发指令 ①~⑤）

| # | 指令 | 产出位置 |
|---|---|---|
| ① | C6-001 失效面扩展：穷举「任务完成通知依赖任务体执行 + 池 destroy 不排空」组合站点，产出 destroy 等待方挂死站点表 | 第一节（含站点表 12 行 + 新立案 C6-009） |
| ② | C6-002 失效面扩展：register 覆盖语义全部调用方重入时序；unregister 修复方向约束；allExecutors() 外部修改面 | 第二节（C6-002 F3 扩展，无新 ID） |
| ③ | listener/watcher 注册泄漏全扫（remove 配对判定） | 第三节（扫描清单 + 新立案 C6-010） |
| ④ | B 面模型资源销毁链深化：组件卸载路径模型资源释放（编译类卸载？VFS 句柄？）；TenantAwareDependencyManager 租户清理完整性 | 第四节（新立案 C6-012、C6-013 + 两项排除结论） |
| ⑤ | CL-029 交叉核实：ShutdownHook.disposables 无 remove + destroyAll CAS 反转 | 第五节（核实成立，立案 C6-011） |

---

## 第一节　指令①：destroy 等待方挂死站点表（C6-001 失效面扩展）

### 1.1 完成通知机制盘点

平台内「CompletableFuture 的 complete 点位于任务体内部」的包装设施共 5 个（均在 `nop-kernel/nop-commons/.../concurrent/executor/ExecutorHelper.java`）：

| 设施 | complete 点 | 池 destroy 后排队任务 |
|---|---|---|
| `submit(ExecutorService, Callable)` L128-150 | 任务体 try 内 `future.complete(result)` | wrapper future 永不终态化 |
| `submit(ExecutorService, Runnable, T)` L152-174 | 同上 | 同上 |
| `schedule(ScheduledThreadPoolExecutor, ...)` L176-199 | 同上 | shutdownNow 抽取队列但不补偿 complete |
| `scheduleWithRandomDelay` L210-238 | 仅异常时 `completeExceptionally`（正常路径永不 complete，已立案"幽灵"族） | 幽灵面已立案，F3 登记 |
| `throttleExecute` L240-260 | runnable 内 complete；`finally` 释放信号量 | 排队孤儿 **且信号量许可泄漏**（finally 不执行） |

平台全部 `IThreadPoolExecutor.destroy()` 实现（DefaultThreadPoolExecutor=`shutdown()`、DefaultScheduledExecutor=`shutdownNow()`、VirtualThreadTaskExecutor=`shutdown()`）均不排空队列补偿 complete——C6-001 主根因在第 2 轮全量复核中确认无一处例外。**正面变体**：VirtualThreadTaskExecutor 为 per-task 虚拟线程执行器，无排队面；destroy 后 `execute` 抛 RejectedExecutionException 直达调用方（fail-fast，不静默挂死）。

### 1.2 destroy 等待方挂死站点表

| # | 站点 | 执行器来源 | 等待方 | 挂死判定 | 依据 |
|---|---|---|---|---|---|
| S1 | `DefaultThreadPoolExecutor.submit` ×2 → `ExecutorHelper.submit` | 自有池/IoC 池 bean（destroy 可达） | 全平台直接调用方的 `syncGet/get/whenComplete` | **成立（C6-001 主根因）** | `DefaultThreadPoolExecutor.java:91-93` |
| S2 | `DefaultScheduledExecutor.submit` ×2 / `schedule` | IoC 定时器 bean | submit/schedule 返回值的等待方 | **成立（destroy=shutdownNow 变体，与 S1 同根因）** | `DefaultScheduledExecutor.java:88-93,108-123` |
| S3 | `TaskStepHelper.timeout` 定时器（`nop-task-core/.../TaskStepHelper.java:199`） | `taskRuntime.getScheduledExecutor()` → `TaskFlowManagerImpl:77-79` → `GlobalExecutors.globalTimer()`（**永不 destroy**，C6-003） | `timeoutGuard` 与任务 promise 竞速；timer 丢失则竞速退化为纯任务驱动 | 条件性（需定时器执行器被销毁；现网 globalTimer 无销毁路径）→ **低** | `TaskFlowManagerImpl.java:77-79` |
| S4 | `LocalJobScheduler` 触发调度（`nop-job-local/.../LocalJobScheduler.java:301`） | IoC 注入 `IScheduledExecutor` | 无直接 future 等待方；job 状态机依赖 fire 推进 | **配对良好**：`stop()`（L233-237）先行 `cancelScheduledFire/cancelRunning`；残余风险仅在"不调 stop 直接 destroy 注入池"的非常规关闭序 | `LocalJobScheduler.java:123-145,233-237` |
| S5 | `RetryHelper._retryExecute`（重试 promise 在 schedule 任务体内 complete） | 调用方传入 | `retryExecute` 返回 promise 的等待方 | API 挂死面成立；**生产调用方仅 2 处**：`IExecution.withRetry`（零调用方）、`SocketClient.recv:207`（promise 丢弃，无等待方）→ 可能性低 | `RetryHelper.java:57-74` |
| S6 | `ExecutorHelper.throttleExecute` ×3（`PageProvider:99,133`、`WebPageExporter:120`） | `GlobalExecutors.globalWorker()`（queueCapacity 默认 10000，**存在排队面**） | `syncGet(waitAll(futures))`（`waitAll`=`CompletableFuture.allOf`，全量终态化才完成） | 条件挂死：需 globalWorker 被 shutdown（register 覆盖同名键——现网唯一 register 调用方注册的是 `nop-virtual-thread`，不覆盖 globalWorker）→ 触发面当前为空，**但排队孤儿伴随的信号量许可泄漏独立成立**（见 1.3-C6-009 触发链注） | `PageProvider.java:90-107,126-140`；`FutureHelper.java:389-410` |
| S7 | `DefaultTaskExecutionQueue.queueTask`（promise 在池 runnable 内 complete，states 计数靠任务体 finally 清理） | ownExecutor（`doStop`→`destroy()`）或 globalWorker | `waitForTask/waitForAllTasks/getPromise().get()` | API 挂死面成立；**生产零调用方修正**：`addTaskIfAbsent/replaceTask/waitForTask` 在全仓 src/main **零调用**，`nopTaskExecutionQueue` bean（`task-defaults.beans.xml:8`）无任何消费者 → 可能性降级（维持 C6-001 级别不变，因 S1 直连面充分） | `DefaultTaskExecutionQueue.java:203-248` |
| S8 | `AbstractBatchProcessService.start`（提交 `BlockingSourceConsumeTask` ×poolSize） | 自有池（daemon=true） | **无等待方**（`submit(task, null)` 返回值丢弃） | 不挂死；消息丢失面已立案族；排队 wrapper 孤儿无等待者 | `AbstractBatchProcessService.java:54-60,71-78` |
| S9 | `NioFileWatchService.doStart`（`executor.submit(this::checkChange)`） | 自有单线程池 | `doStop` 对 future 显式 `cancel(true)` | **正面样本**（cancel 显式终态化，与 C6-001 无关） | `NioFileWatchService.java:59,195-211` |
| S10 | `ImportDbTool:252` / `ExportDbTool:176`（submit + `FutureHelper.getFromFuture(waitAll(futures))`） | 工具自有池 | waitAll 在 `finally destroy()` **之前** | 不挂死；但单表任务异常 → waitAll 异常返回 → finally destroy → **其余排队表的任务被静默丢弃且无日志**（丢任务面，非挂死；与 CL-011 无界队列邻接） | `ImportDbTool.java:251-268` |
| S11 | `ExecutorTaskStepWrapper:33`（step ret 在池任务体内 complete；plan 349 Phase 4 已加取消兜底） | `stepRt.getTaskRuntime().getThreadPoolExecutor(executorBean)`（IoC 池 bean） | step 续延 promise | 条件挂死：容器销毁竞态窗口（池 bean destroy × 运行中任务）；取消路径已有兜底，destroy 路径无兜底 | `ExecutorTaskStepWrapper.java:30-70` |
| S12 | 跨模块登记：`InMemoryActorRuntime:307`、`CheckpointCoordinator:642,665`、`RetentionCleaner:119`（nop-stream/nop-ai，模块自有池生命周期） | 模块自有 | — | 超出本审计单元（A 面 + B 面），同族提示不展开 | — |

**表格结论**：C6-001 的根因修复（destroy 补偿 complete 或统一 `ThreadHelper.shutdownAndAwaitTermination` + 终态化）可一次性关闭 S1/S2/S7 三行；S6 的信号量泄漏需 `throttleExecute` 单独补 try/finally-语义（信号量释放移入 submit 前的拒绝路径 + 排队补偿）；S3/S5/S11 为条件性低概率面。

### 1.3 新立案

---

### [nop-kernel-concurrency-C6-009] BaseThreadPoolConfig 声明的停机排空配置（waitForTasksToCompleteOnShutdown / awaitTerminationSeconds）全链路零消费——排空能力的配置开关存在但结构性不可达

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/BaseThreadPoolConfig.java:17-28`；应接线而未接线的消费点 `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:73-93`（newThreadPoolExecutor）、`DefaultThreadPoolExecutor.java:91-93`（destroy）
- **证据片段**:
  ```java
  // BaseThreadPoolConfig —— @ConfigBean，IoC/配置面可设
  private boolean waitForTasksToCompleteOnShutdown = false;
  private int awaitTerminationSeconds = 0;
  // getter/setter 齐全（L21-28）

  // ExecutorHelper.newThreadPoolExecutor —— 从不读取这两个字段，
  // 从不调用 executor.setWaitForTasksToCompleteOnShutdown(...)
  // DefaultThreadPoolExecutor.destroy() —— 只 executor.shutdown()，不读 awaitTerminationSeconds
  ```
- **严重程度**: P2
- **并发场景**: 任何使用 `ThreadPoolConfig`（全平台池定义的统一配置 bean）声明线程池的宿主/模块，在 beans.xml 或配置中设置 `waitForTasksToCompleteOnShutdown=true` + `awaitTerminationSeconds=N` 以期优雅停机的运维/嵌入方，与容器关闭线程并发。
- **触发时序**: 宿主按 Spring 风格直觉为池 bean 配置两个停机属性 → IoC 装配成功（属性存在 setter，注入无报错）→ 停机时 `destroy()` 仍执行裸 `shutdown()`：排队任务照旧被静默丢弃、wrapper future 照旧永不终态化（C6-001/S1）→ 配置的 `awaitTerminationSeconds` 等待同样不存在 → **调用方以为配置了排空，实际行为与未配置完全一致**，挂死/丢任务在"已做防护"的认知下发生。全仓 grep（java+xml+yaml）证实：两个属性零读取、零 XML 设置、`setWaitForTasksToCompleteOnShutdown` 零调用——字段从写第一天起就是死配置。
- **现状**: JDK 原生修复路径现成（`ThreadPoolExecutor.setWaitForTasksToCompleteOnShutdown(true)` 使 `shutdown()` 改为消费完队列、`awaitTermination` 限时等待），`ThreadHelper.shutdownAndAwaitTermination` 平台工具也已存在（第 1 轮已指出无人使用）；缺的只是 destroy() 与工厂方法各一行接线。
- **风险**: (a) 契约真实性缺陷：配置面承诺的停机语义静默失效，且是**安全语义**（排空）失效，与 F5"不可检测"叠加；(b) 与 C6-001 构成同一缺陷的"修复旋钮已存在但未接线"形态——修复 C6-001 时若不同步接线这两个字段，配置面继续误导。
- **建议**: 二选一并落档：(a) 接线——`ExecutorHelper.newThreadPoolExecutor/newScheduledExecutor` 读取 `isWaitForTasksToCompleteOnShutdown()` 调用 `executor.setWaitForTasksToCompleteOnShutdown(...)`；`DefaultThreadPoolExecutor.destroy()`/`DefaultScheduledExecutor.destroy()` 按 `awaitTerminationSeconds>0` 执行 `shutdown()+awaitTermination`（推荐，最小改动且兑现既有配置）；(b) 删除两个字段及 setter，消除误导。无论哪种，补一条 destroy 排空单测。
- **失效面枚举**（F3）: ① `ExecutorHelper.newThreadPoolExecutor`（DefaultThreadPoolExecutor/StandardThreadPoolExecutor 共用，主面）；② `ExecutorHelper.newScheduledExecutor`（DefaultScheduledExecutor，同样不读）；③ `VirtualThreadTaskExecutor`（私有 config 仅设 name，两字段天然无消费点，登记不改）；④ `SyncThreadPoolExecutor`（无池无队列，不适用）；⑤ `BaseThreadPoolConfig` 的 `@ConfigBean` 注解面（任何未来通过配置中心覆盖属性的部署均受 ①② 影响）；⑥ 已立案的 C6-001（本条是其配置面补充，不重复计数）。
- **信心水平**: 确定（全仓 grep 零消费 + 字段/default/setter 直接可读）
- **误报排除**: 非 C6-001 重复——C6-001 立案的是"destroy 不排空导致等待方挂死"的行为缺陷，本条立案的是**修复该行为所需的配置开关已声明但结构性死亡**的契约真实性缺陷；二者修复动作不同（前者改 destroy 语义，后者接线或删除字段）。非"测试代码简化"豁免——全部为 src/main 生产代码。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P2**。死配置亲核成立：两字段（BaseThreadPoolConfig L17-19）全仓 java+xml+yaml 零消费（唯一出现点为定义文件自身）；`awaitTermination` 生产调用仅 nop-ai 模块自有池与 ThreadHelper 工具本体，池 destroy 路径无一使用；`ThreadHelper.shutdownAndAwaitTermination`（L62）存在但零池接线。**与 C6-001 复核结论的联动**：C6-001 主根因（shutdown 丢弃排队任务）经本轮 JDK 语义核验被驳回后，本条的定位反而上升——`waitForTasksToCompleteOnShutdown/awaitTerminationSeconds` 接线成为 S2 残余面（DefaultScheduledExecutor.shutdownNow 丢排队任务）与停机排空需求的主修复旋钮，维持「与 C6-001 同 diff 修复」建议（改指 C6-001 残余面）。现状：已知未修复。

---

## 第二节　指令②：C6-002 失效面扩展（g_executors 契约族）

本轮对 `GlobalExecutors` 注册表三方语义（register 覆盖 / unregister / allExecutors）做了调用方全量清点与重入时序推演。**结论：无新立案站点，全部作为 C6-002 的 F3 扩展与修复约束登记**（同族旧站点规则）。

### 2.1 register 覆盖语义调用方全量清点（重入/重跑时序）

全仓 grep `GlobalExecutors.register|registerGlobalWorker`（src/main，排除 _gen/target）：**唯一调用链** = `Jdk21CommonsCoreInitializer.initialize()` → `CFG_ENABLE_VIRTUAL_THREAD_POOL.get()`（默认 true，`CommonConfigs.java:67` 已复核）→ `VirtualThreadTaskExecutor.registerGlobalWorker()` → `GlobalExecutors.register(new VirtualThreadTaskExecutor())`。

重入时序（三种生命周期路径逐一推演）：

| 路径 | 时序 | 后果 |
|---|---|---|
| 首次 initialize | register(v1) 入表 | 正常 |
| `CoreInitialization.destroy()` → 再 `initialize()` | **Jdk21CommonsCoreInitializer 未覆写 `destroy()`**（`ICoreInitializer.destroy()` 为空默认方法，已读接口确认）→ destroy 阶段 v1 **既不注销也不销毁**，继续留在 g_executors 且**可继续接受任务**（僵尸窗口）→ 下次 initialize 执行 `register(v2)` → `old.destroy()`（v1）在**新生命周期开始时**才发生 | (a) destroy 后 re-init 前的窗口内任务仍提交到 v1（违背"平台已销毁"直觉）；(b) v1 被销毁时刻其上在飞任务：running 完成、**排队任务 wrapper future 永不终态化**（C6-001 叠加）→ 僵尸窗口内提交的任务在覆盖时刻被牺牲。此僵尸窗口归入 **C6-003 F3 扩展**（全局池无销毁编排的又一后果面） |
| `CoreInitialization.reinitialize()` | destroy-loop 调 initializer.destroy()（同样 no-op）→ init-loop 再 register(v2) → v1.destroy() | 同上，且窗口更短；注意 reinitialize 当前全仓零调用方（见 2.4） |

**对第 1 轮 C6-002 的两处精确化修正**（诚实修正，不改变级别）：
1. C6-002 举例的"长期持有借出引用"三处（`ModelBasedBatchTaskBuilderFactory:210`、`AbstractBizEntityService:59`、`TaskFlowManagerImpl:85`）经本轮逐文件复核：`AbstractBizEntityService.getExecutor`（L51-58）与 `TaskFlowManagerImpl.getThreadPoolExecutor`（L84-88）均为**按名即时解析、不缓存字段**——暴露窗口是"覆盖时刻的在飞/排队任务"，不是"长持引用失效"。第 1 轮表述偏严，实际风险窗口更窄但同样成立。
2. `VirtualThreadTaskExecutor.stats()` 返回 `null`（L54-56）——经 grep，`IThreadPoolExecutor.stats()` 在生产代码零外部调用方（仅 `BindScheduledExecutor:125` 内部委托），当前无 NPE 消费面，登记不改。

### 2.2 unregister 零调用方的修复方向约束

`GlobalExecutors.unregister`（L109-111）：`remove` 不 destroy，全仓 src/main 零调用方（与 CL-018 登记一致，本轮确认零调用状态未变）。修复方向约束（供 Remediation 立项参考）：
- 若给 unregister 加 destroy-on-remove 语义：调用方必须保证无借用方——与 register 覆盖同构的借出可毁风险（C4-005/C6-002 同位点），且必须先有 C6-001 的终态化补偿，否则销毁排队任务仍产生挂死等待方。
- **推荐方向**：将 `Jdk21CommonsCoreInitializer` 补写 `destroy()` 覆写（`GlobalExecutors.unregister(NOP_VIRTUAL_THREAD)` + 显式 destroy 返回值），一举关闭 2.1 的僵尸窗口，同时让 unregister 获得第一个调用方以固化其语义契约；javadoc 明确"unregister 不销毁，销毁责任在调用方"或改为销毁语义二选一，禁止维持现状的双向矛盾。
- 与 C1-015（register 锁外 destroy）、C4-005（借出可毁）、CL-018（unregister 零调用不销毁）三点互引：四者共享 `g_executors` 单点，Remediation 应在同一改动内统筹，避免分四次修出语义漂移。

### 2.3 allExecutors() 暴露内部 map 的外部修改面

`GlobalExecutors.allExecutors()`（L34-36）返回 `g_executors` 活引用。全仓 grep（java，排除 _gen/target/test）：**零调用方**。当前是潜在修改面而非活跃修改面：任何未来调用方可绕过 register/unregister 契约直接 put/remove（remove 同样不 destroy；put 绕过覆盖销毁守卫）。修复建议维持第 1 轮 C6-002 建议(3)：返回 `Collections.unmodifiableMap(new HashMap<>(g_executors))` 快照。登记为 C6-002 F3 ⑤ 的状态更新（零调用方已复核）。

### 2.4 关联发现：reinitialize() 全仓零调用方

`CoreInitialization.reinitialize/reinitialize(int)` 为 public 生命周期 API，全仓 grep（java+docs-for-ai）**零调用方**。它本身无缺陷，但它是第三节 C6-010 的唯一触发路径——该 API 的"承诺可重入"与其下 Initializer 实现群的"不可重入"之间的矛盾，是 C6-010 判级的核心依据。

---

## 第三节　指令③：listener/watcher 注册泄漏全扫（remove 配对判定）

### 3.1 全扫清单与判定

对 A/B 面及生命周期宿主的全部注册类 API 逐点核验（grep 覆盖 `addXxxListener|registerObserver|registerInstance|register*|addWatch|appendOnCancel*|addDisposable|bindTo|registerMetrics|removalListener`）：

| # | 注册站点 | 注销/移除配对 | 判定 |
|---|---|---|---|
| L1 | `LocalCache` removalListener（buildCache L110-114） | 与 cache 实例同生命周期，构造期一次性绑定 | 配对良好 |
| L2 | `ExecutorHelper.registerMetrics` / `LocalCache.registerMetrics`（bindTo） | 无 unbind（micrometer 弱引用已核验） | 已立案（C6-004），不重复 |
| L3 | `NioFileWatchService.watch/unwatch` + `FileWatchEntry.subEntries` | 不对称（父表不摘除、重注册交叉误伤） | 已立案（C6-006），不重复 |
| L4 | `FileWatcher.cleanup`（appendOnCancelTask） | ICancellable 聚合，watch 关闭即触发 | 配对良好（pending debounce 瞬态面第 1 轮已声明不立案） |
| L5 | `Cancellable`（`lang/impl/Cancellable.java`）本身 | `removeOnCancel` 存在；cancel 后 callbacks 置 null；取消后 append 立即执行（javadoc 声明） | 设施本身健全——但该语义与 Initializer 复用模式组合成 **C6-010**（见 3.2） |
| L6 | `ResourceComponentManager.registerComponentModelConfig`（L216-248，返回 undo ICancellable） | 调用方 `RegisterModelDiscovery.processModel` L128-136 将 undo 挂入传入 cancellable → `RegisterModelCoreInitializer.cleanup` | 配对成立，但 cleanup Cancellable 复用 → **C6-010** |
| L7 | `EvalGlobalRegistry.registerStaticFunctions/registerFunction`（L95-124） | 返回 remove 句柄，`functions.remove(name, func)` | 配对良好（受 C6-010 间连） |
| L8 | `ShutdownHook.addDisposable` | 无 remove API；destroyAll CAS 反转 | **C6-011**（第五节立案） |
| L9 | `GlobalCacheRegistry.register/unregister` | putIfAbsent+抛重复 / remove(pair) | 已立案观察项（第 1 轮 #12），无新面 |
| L10 | `VirtualFileSystem.registerInstance`（`safeDestroy(_instance)` 旧实例） | **正面确认**：`IVirtualFileSystem extends IDestroyable`（接口 L20），`DefaultVirtualFileSystem.destroy()`（L95-102）关闭全部 ZipFile；`ConfigStarter:538` 亦显式调用 | 配对良好（本轮复核第 1 轮正面样本，成立） |
| L11 | `GlobalMeterRegistry.registerInstance` | `s_instance.close()` 旧 registry + 替换；check-then-act 非原子 | 唯一调用方 `QuarkusIntegration:50`（启动期单线程）→ **CL-030**（维度集外线索） |
| L12 | `ResourceComponentManager.registerInstance` / `ResourceTenantManager.registerInstance` / `RegisterModelDiscovery.registerInstance` | 旧实例直接丢弃、不 destroy | 单例替换语义，调用方仅测试/初始化期 → 登记不改 |
| L13 | `NamedThreadLocal`（ResourceDependsManager dependsStack） | collectDepends 空栈即 remove + close() remove | 已立案附注（第 1 轮 #13），配对良好 |
| L14 | `cancelToken.appendOnCancel*` 全仓 30 处 | 请求级/任务级 token 短生命周期；`TaskStepHelper` 同步异常路径显式 `removeOnCancel`；`RegisterModelDiscovery:132` cleanup::cancel 自反 | 抽查配对良好；无长生命周期 token 无限追加站点 |
| L15 | cache `removalListener`（TenantAwareDependencyManager tenantCaches，destroyOnRemove=ON） | 值类型 `ResourceDependsManager implements Closeable` → `DestroyHelper.safeDestroy` 处理 AutoCloseable → **链路真实可达**（本轮修正性确认：第 1 轮"正面样本"成立） | 配对成立，但 close() 不清内部 cache → **C6-013** |
| L16 | `DefaultResourceChangeChecker` | 无状态检查器，无监听列表 | 无注册面 |

### 3.2 新立案

---

### [nop-kernel-concurrency-C6-010] ICoreInitializer 实现群复用构造期 Cancellable 字段：destroy 后 reinitialize 路径上「注册即撤销」——cleanup 句柄在取消态被 append 立即触发，xdef/全局函数/方言等核心注册在重初始化后静默丢失

- **文件**: 主证 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/initialize/XLangCoreInitializer.java:38,45-63,84-88`、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/initialize/RegisterModelCoreInitializer.java:15,23-29`；机制配套 `nop-kernel/nop-commons/src/main/java/io/nop/commons/lang/impl/Cancellable.java:89-96（appendOnCancel javadoc"如果当前已经处于取消状态，则会立刻执行callback函数"）、131-147（tryRunCallbacks）`；触发路径 `nop-kernel/nop-core/src/main/java/io/nop/core/initialize/CoreInitialization.java:210-257（reinitialize 复用 initializers 实例，不重建）`
- **证据片段**:
  ```java
  // XLangCoreInitializer —— 字段在构造期创建一次，initialize() 从不重建
  private Cancellable cleanup = new Cancellable();
  public void initialize() {
      cleanup.append(EvalGlobalRegistry.instance().registerStaticFunctions(GlobalFunctions.class)); // L47
      ...
      cleanup.append(ResourceComponentManager.instance().registerComponentModelConfig(config)); // L81 xdef
  }
  public void destroy() {
      JaninoScriptCompiler.unregister();
      cleanup.cancel();          // cancelled=true, callbacks=null
  }
  // Cancellable.appendOnCancel：取消态 append → tryRunCallbacks 立即执行刚注册的 undo
  ```
  同构模式共 **8 处**：`XLangCoreInitializer`、`RegisterModelCoreInitializer`（全部 register.model 模型类型注册）、`DaoDialectInitializer`（nop-dao）、`AiCoreInitializer`、`XuiCoreInitializer`（nop-ui）、`ReflectionHelperMethodInitializer`（nop-core）、`ReportCoreInitializer`、`RecordCoreInitializer`——逐文件核实 `initialize()` 内均无 `cancellable = new Cancellable()` 重建。
- **严重程度**: P2
- **并发场景**: 非并发缺陷，是生命周期重入缺陷。触发者：宿主调用 `CoreInitialization.reinitialize()`（public API，热更新/模型重载的预期入口）。
- **触发时序**: reinitialize() 的 destroy-loop 按逆序调用各 initializer.destroy() → `cleanup.cancel()`（cancelled=true）→ init-loop **在同一实例上**再调 initialize()（reinitialize 不置空 `initializers` 列表，与 `initializeTo` 的失败路径不同——后者 destroy 后置 null、下次加载全新实例，**不受影响**）→ `cleanup.append(...)` 作用于取消态 Cancellable → 按 javadoc 语义**立即执行**刚 append 的 undo 任务 → EvalGlobalRegistry 全局函数注销、`ValueResolverCompilerRegistry` "expr" resolver 移除、**xdef modelType 从 ResourceComponentManager.modelTypeConfigs 反注册**——初始化"完成"后平台核心注册表处于已撤销状态，后续任何 xdsl/xdef 模型解析报 ERR_COMPONENT_NO_LOADER_FOR_PATH 类错误。
- **现状**: reinitialize() 当前全仓零调用方（2.4），故为休眠缺陷；但该 API 是平台公开生命周期契约，且 8 个 Initializer 分布于 nop-kernel/nop-dao/nop-ai/nop-ui/nop-report/nop-format 六个模块，逐一修补的遗漏风险高（修 1 处漏 7 处）。
- **风险**: 一旦宿主采用 reinitialize（热更新场景的合理选择），平台以"初始化成功"状态运行而 xdef/全局函数/方言/报表模型注册全部丢失，故障表现为加载期报错且日志无任何注销痕迹（撤销发生在注册后微秒级），定位成本极高。
- **建议**: 两层修复：(a) 各 Initializer `initialize()` 首行 `cancellable = new Cancellable()` 重建（8 处机械修改，可用一个抽象基类 `AbstractCancellableInitializer` 统一收敛）；(b) 防御性方案——`Cancellable.appendOnCancel` 对"取消态 append"记 WARN 日志，使该模式在测试期暴露。
- **失效面枚举**（F3）: ① `XLangCoreInitializer.cleanup`（xdef 模型类型 + GlobalFunctions/DebugHelper/LogFunctions + expr resolver，本条主证）；② `RegisterModelCoreInitializer.cleanup`（全部业务模型类型注册，主面同级）；③ `DaoDialectInitializer/AiCoreInitializer/XuiCoreInitializer/ReportCoreInitializer/RecordCoreInitializer/ReflectionHelperMethodInitializer`（同构模式，同修）；④ `initializeTo` 失败路径（destroy→initializers=null→新实例）**不受影响**（正面边界）；⑤ `IocCoreInitializer/ConfigInitializer/VirtualFileSystemInitializer` 等无 Cancellable 字段的实现（不同模式，不适用）；⑥ 无 Cancellable 复用的 `JaninoScriptCompiler.register/unregister`（显式对称 API，配对良好）。
- **信心水平**: 确定（Cancellable 取消态 append 立即执行为 javadoc 声明 + 代码直读可推演；8 处模式逐文件核实）
- **误报排除**: 非 C6-002（池注册表）与 CL-018（unregister 零调用）的同族重复——本条是 **ICoreInitializer 生命周期层**的注册/撤销对称性缺陷，资源类型是全局函数表/模型类型注册表而非线程池；非"reinitialize 零调用=无事发生"——判级已按零调用现状降为 P2 而非 P1，且 `@Internal` 注解仅标注接口不改变 public API 可达性。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P2（休眠）**。机制逐环亲核成立：8 处构造期 Cancellable 名单与 live code 一致（XLangCoreInitializer L38、RegisterModelCoreInitializer L15、DaoDialectInitializer L16、AiCoreInitializer L16、XuiCoreInitializer L9、ReflectionHelperMethodInitializer L27、ReportCoreInitializer L16、RecordCoreInitializer L16；grep 证实全部 initialize() 内无重建）；`Cancellable.appendOnCancel` 取消态 append 立即执行（javadoc L59 + tryRunCallbacks L107-124 取走并置空 callbacks）；`reinitialize()` 复用 initializers 实例不重建（CoreInitialization L212-264），而 destroy→initialize 路径因 destroy 置 initializers=null 重载全新实例**不受影响**——触发面唯一性判定正确。可达性亲核：`reinitialize` 全仓 java 零调用方（grep 证实，与 2.4 一致），历史对照 plan 346/2310 亦确认「reinitialize 生产零调用」曾两轮独立复证——休眠缺陷 + public 生命周期 API 契约矛盾 + 8 模块散布修复遗漏风险，P2 维持（不升 P1：零调用方；不降 P3：API 公开契约与实现群系统性矛盾）。修复建议 (a)(b) 维持。现状：已知未修复。

---

## 第四节　指令④：B 面模型资源销毁链深化

### 4.1 指令中两个疑问的排除结论（负结果，诚实报告）

1. **编译类卸载**：nop-xlang 主路径**无动态类加载**——grep `IDynamicClassLoader|defineClass|URLClassLoader` 在 `nop-xlang/src/main/java` 零命中（仅 `GlobalFunctions.java` 提及 ClassLoader 字样）；模型缓存驻留的是解析后内存对象图，不持 Class/ClassLoader。`nop-javac` 的 `DynamicURLClassLoader`（JavaCompileTool:90）与 `ClassLoaderImpl`（JdkJavaCompiler:131）属构建期/可选编译后端：`GenAopProxy:66` 已用 try-with-resources 显式关闭并注释 Windows 句柄问题（正面）。→ C6-005 的危害前提在"类泄漏"维度不成立，维持"模型持 OS 资源"这一未证实前提。
2. **VFS 句柄**：`DefaultVirtualFileSystem` 持有 `List<ZipFile>`（OS 文件句柄），但 `IVirtualFileSystem extends IDestroyable`（接口 L20），`destroy()`（L95-102）`IoHelper.safeCloseAll(zipFiles)`；`refresh()` 先发布新 store 再关旧 zip（防 zip closed 读异常，注释明确）；`VirtualFileSystemInitializer.destroy` 与 `ConfigStarter:538` 两路都可达 destroy。→ **配对良好**（第 1 轮正面样本经本轮深挖确认成立）。唯一保留面：`DefaultVirtualFileSystem` 的 destroy 依赖接口继承的 IDestroyable 生效——`DestroyHelper.safeDestroy` 走 `instanceof IDestroyable` 分支成立，无误。

### 4.2 组件卸载路径的实际形态澄清

下发指令中的 `unloadComponent` 在全仓**不存在**（grep 零命中）。实际卸载入口为：`ResourceComponentManager.clearCache(modelType)`（L361-365，仅 `cache.clear()`，不清 dependsManager——依赖集靠时间戳 checkChanged 自愈，无功能缺陷）、`removeCachedModel`（L367-373，`cache.remove(path)` + `entry.clear()` 显式销毁，半配对）、`clearAllCache`（L139-146）。三者最终都落在第 1 轮 C6-005 已立案的断链上（destroyOnRemove=false + ComponentCacheEntry 非 IDestroyable + destroy()=cleanUp），本轮不再重复立案。

### 4.3 租户清理路径完整性（C6-012/C6-013 立案依据）

`ResourceTenantManager.clearForTenant`（L153-158）→ `provider.clearForTenant` + `GlobalCacheRegistry.clearForTenant` → 各注册缓存 `clearForTenant`。链路逐环核验：`ICacheManagement.clearForTenant` 默认**空实现**（接口 L23-25）；`TenantAwareResourceLoadingCache` 覆写为 `tenantCaches.remove(tenantId)`（L56-58）；`TenantAwareDependencyManager.tenantCaches` 容器开启 destroyOnRemove 且值 Closeable → 链路真实（正面）。但 `TenantAwareResourceLoadingCache` 的容器与值类型存在双断，见 C6-012。

---

### [nop-kernel-concurrency-C6-012] 租户模型缓存清理路径双断：TenantAwareResourceLoadingCache 的 tenantCaches 容器未开 destroyOnRemove，且值类型 ResourceLoadingCache 不可销毁——clearForTenant 丢弃整棵租户缓存对象图而无任何销毁回调

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/tenant/TenantAwareResourceLoadingCache.java:38-41（容器构造）,56-58（clearForTenant）`；值类型 `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceLoadingCache.java:51（类声明，仅 implements IResourceLoadingCache）`；调用链 `ResourceTenantManager.java:153-158`、`GlobalCacheRegistry.java:73-75`
- **证据片段**:
  ```java
  // TenantAwareResourceLoadingCache —— 容器 LocalCache 无 .destroyOnRemove()（对照
  //   TenantAwareDependencyManager 同文件族 L23-25 的 .destroyOnRemove() 写法）
  this.tenantCaches = LocalCache.newCache(name,
          newConfig(CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE.get()),
          k -> new ResourceLoadingCache<>("tenant-" + name, loader, listener, cacheMaxSize, cacheTimeout));
  public void clearForTenant(String tenantId) { tenantCaches.remove(tenantId); }  // 驱逐即丢弃

  // 值类型 ResourceLoadingCache —— 非 IDestroyable/AutoCloseable：
  public class ResourceLoadingCache<V> implements IResourceLoadingCache<V> { ... }
  // 即便容器补开 destroyOnRemove，removalListener 里 DestroyHelper.safeDestroy(value) 也是 no-op
  ```
- **严重程度**: P2
- **并发场景**: 多租户开启（`CFG_TENANT_RESOURCE_ENABLED`）时，`ResourceTenantManager.clearForTenant`（租户下线/清理）或 Caffeine 容器缓存按 `CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE` LRU 驱逐租户条目，与业务线程并发读取同租户缓存。
- **触发时序**: 租户 T1 的模型缓存条目被驱逐/清理 → `tenantCaches.remove(T1)` → LocalCache removalListener 触发但 `destroyOnRemove=false` → **per-tenant `ResourceLoadingCache`（内含自己的 Caffeine LocalCache、全部该租户模型 ResourceCacheEntry、以及构造期绑定的 micrometer meter）整棵对象图无销毁回调地转为 GC 待收** → 模型对象上任何 `ICreationListener.onDestroyed` 契约（扩展方反注册钩子）不触发；与 C6-005 窗口 1 同构但发生在租户维度，且多一层"容器配置本可一键修复却被值类型不可销毁二次阻断"的结构。
- **现状**: 触达面核实：`ResourceComponentManager.makeModelCache` → `ResourceTenantManager.makeLoadingCache(name, loader, listener)` → enableTenant 时返回 `TenantAwareResourceLoadingCache`——即**租户模式下全部 modelType 模型缓存**都走此结构；对照正面样本：`TenantAwareDependencyManager.tenantCaches`（destroyOnRemove=ON）与 `TenantAwareResourceCacheEntry`/`ResourceCacheEntry`（值实现 IDestroyable，`ResourceCacheEntry.java:30` 已核）两链路均真实可达。
- **风险**: 当前模型以冻结内存对象为主 → 后果为 GC 前多驻留 + `onDestroyed` 契约失效（同 C6-005 量级判断）；修复时若只补容器 destroyOnRemove 而不给 `ResourceLoadingCache` 加销毁语义，将得到"看似已修、实际 no-op"的假阳性——这是本条独立于 C6-005 立案的核心原因（修复路径的必经两步都缺）。
- **建议**: (1) `ResourceLoadingCache` 实现 `IDestroyable`（destroy = `cache` 内条目逐一 `ResourceCacheEntry.clear()` 或 `LocalCache.invalidateAll + cleanUp`，复用 C6-005 建议(1)(3) 的接线）；(2) `TenantAwareResourceLoadingCache` 容器 CacheConfig 加 `.destroyOnRemove()`；(3) `clearForTenant` 语义补单测：驱逐后断言模型 onDestroyed 回调被调用。
- **失效面枚举**（F3）: ① `TenantAwareResourceLoadingCache.tenantCaches`（本条主面：容器无 destroyOnRemove + 值不可销毁双断）；② `GlobalCacheRegistry.clearForTenant` 的覆盖缺口——`orm-interceptor-cache`（DefaultOrmModelProvider:20）、`data-auth-cache`（DefaultDataAuthChecker:70）、`dyn-loaded-orm-model-cache`（DynOrmModelProvider:34）三个 `CacheEntryManagement` 缓存**未注册进 GlobalCacheRegistry**，`clearAllCache/clearForTenant` 均不可达（当前靠 ResourceCacheEntry 时间戳自愈 + 纯内存值，登记不修）；③ `ICacheManagement.clearForTenant` 默认空实现——凡未覆写的注册缓存（如 ReflectionManager.introspectCache）在租户清理时静默 no-op（反射模型租户无关，登记不改）；④ 三个已立案时确认为正面的租户链（TenantAwareDependencyManager / TenantAwareResourceCacheEntry / TenantAwareOrmModelProvider）不适用；⑤ C6-005（非租户默认链）——同族不同站点，互引不重复。
- **信心水平**: 确定（代码路径完整可推演，容器配置与值类型层级直接可读）
- **误报排除**: 非 C6-005 重复——C6-005 立案的是平台默认（非租户）模型缓存链的三个断点；本条立案的是**租户清理路径**的独立双断（断点位置、修复动作、触发入口均不同），且含 ②③ 两个本轮新发现的覆盖缺口。下发指令明确要求深化"租户清理路径完整性"，本条即其产出。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P2，F3 ② 子项事实修正**。主面亲核成立：tenantCaches 容器 `LocalCache.newCache(name, newConfig(SIZE), loader)` 无 `.destroyOnRemove()`（TenantAwareResourceLoadingCache L38-41，对照 TenantAwareDependencyManager L26-27 的 `.useMetrics().destroyOnRemove()` 写法）；`clearForTenant`=remove（L56-58）；值类型 `ResourceLoadingCache implements IResourceLoadingCache` 非 IDestroyable/AutoCloseable（L51）——容器/值双断、修复必经两步均缺的核心论证成立。**F3 ② 修正**：初审称 orm-interceptor-cache/data-auth-cache/dyn-loaded-orm-model-cache 三缓存「未注册进 GlobalCacheRegistry，clearAllCache/clearForTenant 均不可达」——**证伪**：DefaultOrmModelProvider L26 register（close L31 unregister）、DefaultDataAuthChecker L71 register（L77 unregister）、DynOrmModelProvider L35 register（L50 unregister），三者均已注册且 `CacheEntryManagement.clearForTenant` 有覆写（L40-41）租户清理可达；该子项由「登记缺口」改判为正面样本。主面（本条双断）不受影响。修复建议(1)(2)(3) 与 C6-005 合并实施维持。现状：已知未修复。

---

### [nop-kernel-concurrency-C6-013] ResourceDependsManager.close() 销毁链不完整：清 dependencyMap 与 ThreadLocal 但遗漏内部 depends-with-cache（含 meter 绑定）——唯一真实工作的 destroyOnRemove 链本身留残

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/deps/ResourceDependsManager.java:35（内部 cache 字段）,52-55（close()）`；容器 `nop-kernel/nop-core/src/main/java/io/nop/core/resource/tenant/TenantAwareDependencyManager.java:23-28,31-35`
- **证据片段**:
  ```java
  public class ResourceDependsManager implements IResourceDependencyManager, Closeable {
      private final ThreadLocal<ResourceDependsStack> dependsStack = new NamedThreadLocal<>("resource-depends-stack");
      private final Map<String, ResourceDependencySet> dependencyMap = new ConcurrentHashMap<>();
      private final IResourceLoadingCache<Object> cache = new ResourceLoadingCache<>("depends-with-cache", null, null);

      @Override
      public void close() throws IOException {
          dependsStack.remove();
          dependencyMap.clear();
          // cache 不清不销毁 —— 内部 Caffeine 及其 meter 随对象图等待 GC
      }
  ```
- **严重程度**: P3
- **并发场景**: 多租户下租户依赖管理器被 `TenantAwareDependencyManager.tenantCaches`（destroyOnRemove=ON，链路真实）驱逐或 `clearDependencies()` 全清时，`safeDestroy` → `close()` 执行，与仍在读取 `runWhenDependsChanged` 缓存的业务线程并发。
- **触发时序**: 租户 T1 条目驱逐 → removalListener → `safeDestroy(ResourceDependsManager)` → `close()` 清 map + ThreadLocal → **内部 `depends-with-cache`（每租户实例各一个，构造期 `new ResourceLoadingCache(...)` 即绑定 CaffeineCacheMetrics meter）不被清理** → 残留依赖集对象 + 失源 meter（弱引用目标随 GC 清空后读数恒 0/NaN，且所有租户实例共用同名 Meter Id `"depends-with-cache"`，第 2+ 租户指标静默缺失——C6-004 ③ 同族变体）。
- **现状**: 值类型 `ResourceDependencySet` 纯内存、无 OS 资源 → 泄漏不可逆性低，判 P3；此链是第 1 轮确认"destroyOnRemove 链路通"的正面样本，本条指出该正面链自身的收尾缺口，不改变其正面定性。
- **风险**: 长驻多租户服务随租户 churn 缓慢累积内存条目（有 GC 上界）+ 租户维度指标失真；无挂死、无句柄泄漏。
- **建议**: `close()` 追加 `cache.clear()`（或给 ResourceLoadingCache 加销毁语义后一并调用，与 C6-012 建议(1) 合并实施）；`depends-with-cache` 的 meter 命名并入 C6-004 修复（名中含租户维度或改用 TenantAware 前缀去重策略时统一处理）。
- **失效面枚举**（F3）: ① tenantCaches 驱逐路径 close()（本条）；② `TenantAwareDependencyManager.clearDependencies`（L32-35，tenantCaches.clear() → 同一 safeDestroy → 同残留，全租户版本）；③ 非租户单例 `ResourceDependsManager`（ResourceComponentManager.dependsManager 的 shareCache）——进程级单例，close 无调用方，不适用；④ meter 面归 C6-004 ③，不重复计数。
- **信心水平**: 确定
- **误报排除**: 非 C6-012 重复——C6-012 是"链路断"（回调不触发），本条是"链路通但 close 收尾不全"（回调触发后仍留残）；非 CL-018（unregister 零调用）——资源类型与层均不同。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P3**。亲核成立：`ResourceDependsManager.close()`（L52-55）仅清 dependsStack+dependencyMap，内部 `depends-with-cache` 字段（L35）不清不销毁；容器 destroyOnRemove=ON 且值 Closeable（TenantAwareDependencyManager L26-27 + L18-19），safeDestroy→close() 链路真实可达；`clearDependencies()`（L32-35）→tenantCaches.clear() 同路径同残留。值纯内存、P3 定级合理。修复建议（close() 追加 cache.clear()，与 C6-005/C6-012 的 ResourceLoadingCache 销毁语义合并实施）维持。现状：已知未修复。

---

## 第五节　指令⑤：CL-029 交叉核实——成立，立案 C6-011

### [nop-kernel-concurrency-C6-011] ShutdownHook.destroyAll 的 CAS 守卫条件反转：幂等保护只在「无可销毁物」时生效——二次调用必然重复销毁全部 disposables；disposables 为非线程安全 TreeSet 且无 remove API

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/service/ShutdownHook.java:38（disposables 声明）,51-53（addDisposable）,60-70（destroyAll）`；唯一注册点 `nop-core-framework/nop-boot/src/main/java/io/nop/boot/NopApplication.java:39-41`
- **证据片段**:
  ```java
  private Set<IDestroyable> disposables = new TreeSet<>(SafeOrderedComparator.DEFAULT);  // 非线程安全
  private final AtomicBoolean destroyed = new AtomicBoolean(false);

  public void destroyAll() {
      if (!destroyed.compareAndSet(false, true) && CollectionHelper.isEmpty(disposables)) {
          return;   // 反转：仅当「CAS 失败 且 disposables 为空」才跳过
      }
      for (IDestroyable disposable : disposables) {
          disposable.destroy();   // 二次调用（disposables 非空）必然重跑
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: (a) 时序型——宿主按 `removeRuntimeShutdownHook` 的 javadoc 注释（"for spring context"）所暗示的嵌入模式**手动调用 `destroyAll()`**（热重载/受管停机），随后 JVM 退出触发 `run()` → destroyAll 第二次执行；(b) 并发型——应用线程运行期 `addDisposable` 与另一线程 `destroyAll()` 迭代同一非线程安全 TreeSet。
- **触发时序**: 窗口 1（重复销毁）：首次 `destroyAll()`：CAS false→true 成功，短路进入循环，销毁 d1..dn → JVM 退出 `run()` → `destroyAll()`：CAS 失败 → `!false && isEmpty(disposables)` → disposables **无 remove API 永不为空** → 条件为假 → **再次遍历销毁 d1..dn**。幂等守卫与保护目标完全反转：它恰恰在"有东西可销毁"（需要保护）时不保护，在"没东西可销毁"（无需保护）时保护。非幂等 disposable（任意业务 lambda、非对称 destroy 实现）被二次执行——double-release、二次状态翻转。窗口 2（并发破坏）：TreeSet（TreeMap 红黑树）在迭代中被并发 add → CME 或结构损坏；两个线程并发 destroyAll 时 CAS 失败方不返回（`&&` 右侧非空为假）而是**同时进入循环并发迭代同一 TreeSet**——`destroyed` 标志本可提供的互斥被 `&&` 右侧短路条款废除。
- **现状**: 全仓核验：`destroyAll()` 外部调用方零（唯一调用点是 `run()`），`addDisposable` 生产注册点仅 NopApplication 1 处（lambda → `CoreInitialization.destroy()`，后者自带 initializers==null 幂等早退）→ 当前触发面为空、属**结构性休眠**；`removeRuntimeShutdownHook` 的存在证明宿主手动接管停机是设计内场景，届时 destroyAll 即被期望可手动调用。CL-029 线索内容（无 remove + CAS 反转）经逐行核实**全部成立**，另有 TreeSet 非线程安全为本轮新增面。
- **风险**: 嵌入式宿主一旦按设计内姿势手动调用 destroyAll（或并发调用），全局停机钩子产生重复销毁/并发结构破坏；因发生在 JVM 退出路径，症状（二次清理异常、卡死）极难归因。
- **建议**: (1) `destroyAll` 修正为标准幂等模式：`if (!destroyed.compareAndSet(false, true)) return;`（一行删除 `&& CollectionHelper.isEmpty(disposables)`）；(2) `disposables` 改 `ConcurrentSkipListSet`（保序 + 线程安全）；(3) 补 `removeDisposable` 或在 javadoc 声明"一次性注册表，不可注销"；(4) 补幂等单测：连续两次 destroyAll 断言 disposable.destroy 恰被调用一次。
- **失效面枚举**（F3）: ① `destroyAll` 二次调用（本条主面，当前零外部调用方=休眠）；② `destroyAll` 并发调用（同上）；③ `addDisposable` × destroyAll 迭代并发（同 TreeSet 面）；④ `run()`（JVM 钩子单次触发，单独不触发缺陷，正面边界）；⑤ `disposables` 无 remove 的累积面——注册点仅 1，量级 1 条目，登记不改；⑥ CL-029 线索本体——本轮立案关闭该线索（并入 C6-011，不再以 CL 形式维持）。
- **信心水平**: 确定（条件反转与 TreeSet 线程安全均为代码直读事实；唯一不确定的是触发窗口的宿主行为，已如实标注休眠状态）
- **误报排除**: 非"防御式编程过度设计"类误报——`destroyed` 标志 + CAS 的存在证明作者意图是幂等保护，实现与意图相悖即为缺陷；非 CL-018/unregister 族（资源类型不同：本条是销毁编排器自身，不是被编排资源）；判 P2 而非 P1 已计入"当前全仓零手动调用方"的休眠事实。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P2（休眠）**。全部事实亲核成立：CAS 守卫条件反转（ShutdownHook L72 `if (!destroyed.compareAndSet(false, true) && CollectionHelper.isEmpty(disposables)) return;`——二次调用时 disposables 永不为空（无 remove API、destroy 后不清空），条件恒假必然重跑全部 disposables）；disposables 为非线程安全 TreeSet（L39）；并发 destroyAll 双线程同时进入循环成立。CL-029 线索核实成立、并入本条关闭的处置维持。休眠现状亲核：destroyAll 唯一调用点 run()（L64-67）、addDisposable 生产注册点仅 NopApplication L39-41（lambda→CoreInitialization.destroy，后者 initializers==null 幂等早退）。`removeRuntimeShutdownHook` javadoc「for spring context」证明宿主手动接管停机为设计内场景——P2（休眠结构性缺陷）维持。修复建议(1)(2)(3)(4) 维持。现状：已知未修复。

---

## 维度集外线索

### [nop-kernel-concurrency-CL-030]（维度集外线索，不立案展开）GlobalMeterRegistry.registerInstance 非原子 check-then-act + LocalCache.registerMetrics 无 null 防护

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/metrics/GlobalMeterRegistry.java:30-35`、`nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCache.java:72-77`
- **形态**: `registerInstance` 的 `if (s_instance != instance) { s_instance.close(); s_instance = instance; }` 为非原子比较-关闭-替换：两线程并发替换可产生双 close 与丢失更新（后者被前者覆盖回旧实例）。`LocalCache.registerMetrics` 直接 `.bindTo(GlobalMeterRegistry.instance())` 不判 null（对照 `ExecutorHelper.registerMetrics:203-206` 有 null 判断）——当前默认 `SimpleMeterRegistry` 恒非 null，但 `registerInstance(null)` 可使后续任何 useMetrics 缓存构造 NPE。唯一调用方 `QuarkusIntegration:50`（启动期单线程）→ 当前无活跃触发面。属 C7（可观测性）/全局单例替换契约形态；若未来出现运行期 registry 热替换（多租户隔离指标等需求）应先修此竞态。

### [nop-kernel-concurrency-CL-031]（维度集外线索，不立案展开）nop-graph LeidenDetector.runWithTimeout 每次调用创建非 daemon 单线程池，shutdownNow 无 awaitTermination——C6-007 同族跨模块站点

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/LeidenDetector.java:192-204`
- **形态**: `Executors.newSingleThreadExecutor()`（默认线程工厂，**非 daemon**）+ `future.get(timeout)` + `finally shutdownNow()`。超时路径有界不挂死；但任务若停在不可中断阻塞点，非 daemon 线程存活至任务返回，阻止 JVM 退出，且 shutdownNow 与线程实际退出无同步（无 awaitTermination）。与已立案 C4-010/C6-007（非 daemon 创建点族）同族，站点在 nop-graph（审计单元外），按第 1 轮 nop-stream 先例登记为跨模块同族提示，交由对应模块轮次处置。

---

## 汇总

### 本轮新增发现清单

| ID | 标题 | 指令来源 | 级别 | 信心 |
|---|---|---|---|---|
| nop-kernel-concurrency-C6-009 | 停机排空配置（waitForTasksToCompleteOnShutdown/awaitTerminationSeconds）全链路零消费，排空能力结构性不可达 | ① | P2 | 确定 |
| nop-kernel-concurrency-C6-010 | ICoreInitializer 实现群复用构造期 Cancellable：reinitialize 路径注册即撤销（×8 模块） | ③ | P2 | 确定 |
| nop-kernel-concurrency-C6-011 | ShutdownHook.destroyAll CAS 守卫反转（二次调用必重复销毁）+ 非线程安全 TreeSet + 无 remove（CL-029 核实立案） | ⑤ | P2 | 确定 |
| nop-kernel-concurrency-C6-012 | 租户模型缓存清理双断：tenantCaches 容器未开 destroyOnRemove + 值类型不可销毁 | ④ | P2 | 确定 |
| nop-kernel-concurrency-C6-013 | ResourceDependsManager.close() 遗漏内部 depends-with-cache（含 meter）——正面销毁链的收尾缺口 | ④ | P3 | 确定 |

### CL 线索

| ID | 形态 | 级别 |
|---|---|---|
| nop-kernel-concurrency-CL-030 | GlobalMeterRegistry.registerInstance 竞态 + LocalCache.registerMetrics 无 null 防护（C7 形态） | 集外线索 |
| nop-kernel-concurrency-CL-031 | LeidenDetector 非 daemon 临时池（C6-007 同族跨模块站点） | 集外线索 |
| ~~CL-029~~ | **关闭**：核实成立，并入 C6-011 立案 | 已处置 |

### 指令②产出（无新 ID 的深化登记）

- register 唯一调用链重入时序表（2.1）：Jdk21CommonsCoreInitializer 无 destroy 覆写 → destroy 与 re-init 之间旧虚拟线程池僵尸存活可提交；覆盖销毁发生在新生命周期开始——僵尸窗口归 C6-003 F3 扩展。
- unregister 修复方向约束（2.2）：推荐由 Jdk21 initializer 补 destroy() 覆写接线，与 C1-015/C4-005/C6-002/CL-018 四点同位统筹。
- allExecutors() 零调用方复核（2.3）：潜在修改面，维持快照修复建议。
- 第 1 轮 C6-002 两处精确化修正（2.1 尾）：借出方均为按名即时解析（非长持引用）；VirtualThreadTaskExecutor.stats()=null 无生产消费面。

### 对第 1 轮的诚实修正

- C6-001 的"生产受害路径 DefaultTaskExecutionQueue"：`nopTaskExecutionQueue` bean 与 `ITaskExecutionQueue` API 在全仓 src/main **零消费者**（本轮 1.2-S7 修正）——挂死面在设施契约层维持成立，实际触发可能性降级；C6-001 级别不变（S1 直连面充分）。
- 正面样本 `VirtualFileSystem.registerInstance` destroy-old 配对：经深挖**确认成立**（IVirtualFileSystem extends IDestroyable；DefaultVirtualFileSystem.destroy 关 ZipFile；ConfigStarter:538 双路可达）——第 1 轮正面定性无误，本轮补齐证据链（4.1）。
- 正面样本 `TenantAwareDependencyManager` destroyOnRemove 链：**确认真实可达**（safeDestroy 处理 AutoCloseable），但 close() 收尾不全 → C6-013，不改变正面定性（3.1-L15）。

### 总计

- **本轮立案**：5 条（P0×0，P1×0，P2×4，P3×1），编号 C6-009~C6-013
- **维度集外线索**：2 条新增（CL-030/CL-031），1 条关闭（CL-029 → C6-011）
- **排除结论**（负结果）：编译类卸载面不存在（xlang 主路径无动态类加载）；VFS ZipFile 句柄配对良好；`unloadComponent` API 不存在（实际卸载入口为 clearCache/removeCachedModel/clearAllCache，均落在已立案 C6-005 链上）

### 终止信号判定

本轮五个指令（①~⑤）全部执行完毕，逐项产出：

1. **穷举面已闭合**：ExecutorHelper 5 个完成通知设施 × 全部池 destroy 变体 × 全部生产调用方（S1~S12）已枚举并逐行判定；register/unregister/allExecutors/initializer 族/listener 注册面/租户清理链/ShutdownHook 均完成全仓 grep 闭合，无遗留未判定站点。
2. **无新增 P0/P1**：本轮 5 条均为 P2/P3，且 C6-009/C6-010/C6-011 的活跃触发面在当前代码中均为休眠（死配置零设置、reinitialize 零调用、destroyAll 零手动调用），风险以"契约真实性与生命周期重入"形态存在而非现网可复现故障。
3. **边际收益递减**：第 1 轮 8 条 + 本轮 5 条后，A 面（concurrent/cache/pool）与 B 面（模型缓存/注册表/租户/VFS/watch）的资源获取-释放配对表已无未知项；剩余疑点均跨出审计单元（nop-stream/nop-ai/nop-graph 模块自有池，CL-031 型）或依赖未证实前提（模型持 OS 句柄，C6-005 前提声明不变）。

**判定：C6 维度可终止**，不建议第 3 轮深挖。Remediation 阶段建议优先序：C6-001+C6-009 同一改动内接线修复（行为缺陷与其死配置旋钮），C6-002/C6-003/C1-015/C4-005/CL-018 统筹 g_executors 单点，C6-010 以抽象基类一次性收敛 8 处，C6-011/C6-012/C6-013 为独立小改。
