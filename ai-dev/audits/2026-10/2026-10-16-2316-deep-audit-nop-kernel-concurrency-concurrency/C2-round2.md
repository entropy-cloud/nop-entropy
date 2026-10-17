# C2 锁与同步 —— nop-kernel 并发·异步·资源专项审计（第 2 轮深挖，盲审）

## 基本信息

- **审计单元**: nop-kernel 平台并发设施自身（A 面 + B 面，第 2 轮按失效面扩展至 xlang 编译设施与 api-core 等待面）
- **仓库路径**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`，HEAD 8c13908bfd）
- **维度**: C2 锁与同步（同第 1 轮）
- **审计日期**: 2026-10-17（任务批次 2026-10-16-2316）
- **轮次**: 第 2 轮深挖（盲审）
- **判级口径**: 平台设施被全平台并发调用——锁缺陷按「多任务多线程默认并发调用」口径判级
- **本轮输入**: 第 1 轮报告 `C2-lock-sync.md`（C2-001~015、CL-004~006、E1/E2/E3 锁序图）、CL-004~006、基线 B-face enumeration 节、live code

## 盲审纪律声明

本轮为盲审轮次。审计过程中**仅读取**了本维度第 1 轮文件 `C2-lock-sync.md`（指令允许的唯一例外）与当前 live code；未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何其他文件（含同目录其他维度文件）。前轮已立案 15 条不重复立案；C1 轮已立案的 RoundRobinSupplier/ResourceMultiLock/LocalFileLock/CycleSynchronizer 回调等同族站点仅在 F3 枚举中登记。所有新结论仅基于当前 live code 的逐行核验。

## 本轮目标（逐条对应深挖指令）

1. **① C2-001 失效面扩展**：穷举 A/B 面「synchronized/lock 块内调用可能触发类加载/模型编译/文件 I/O」站点，判定递归嵌套可达性，与 C2-003 合并产出「锁内重活站点表」（见下节表 R2-1）。
2. **② Condition await 面**：全量 grep `await(/awaitNanos` 站点，逐一判定丢失唤醒/虚假唤醒/谓词循环（见下节表 R2-2）。
3. **③ 锁序图收口**：横向验证第 1 轮「仅 E1/E2/E3 三处可闭合锁序环」结论，重点配对 TaskQueue 锁 vs 池锁、entry 监视器 vs LocalResourceLockManager 锁表、GlobalExecutors 内部锁 vs 池内部锁（见下节「锁序图收口 R2-3」）。
4. **④ 读写锁/StampedLock 缺位面**：读多写少却用独占锁的站点判定（见下节 R2-4）。
5. **⑤ 可重入假设与锁对象逃逸面**（见下节 R2-5）。

---

## R2-1 ① 锁内重活站点表（与 C2-001/C2-003/C2-012 合并的全量视图）

| # | 站点 | 锁对象 | 锁内操作 | 递归/嵌套可达性 | 死锁环闭合条件 | 判定 |
|---|---|---|---|---|---|---|
| 1 | `ResourceCacheEntry.getObject/reloadObject`（ResourceCacheEntry.java:176-240） | per-entry 监视器 | 模型解析+编译（loader.loadObjectFromPath） | 可达：loader 内 `collectDependsTo` → `requireComponentModel` → 其他 entry；`XplLibHelper.java:66` xlib import 链实证 | 循环模型依赖 + 两线程反序（C2-001，P1 已立案） | C2-001 维持 |
| 2 | **`XplLibTagCompiler` 每标签 `ResourceCacheEntryWithLoader`**（XplLibTagCompiler.java:108,129,210,653-655） | per-tag entry 监视器 | `buildCompiledTag`：标签源文件加载（I/O）+ XNode 解析 + attr 模板解析 + `traceDepends` | 可达：`parseTag`（L196-198）在**其他模型**的 entry 监视器内调用 tag 编译；`getTransform().call1` 为编译期用户代码，可 require 任意模型 | 同 C2-001 家族，环经「模型 entry → 标签 entry → 模型 entry」闭合（触发条件：模板模型加载期使用标签） | **C2-017 立案（与 #3 合并）** |
| 3 | **`LazyCompiledFunction.compile`（synchronized）**（XplLibTagCompiler.java:734-770） | per-LazyFn 监视器 | 完整 XPL 编译：`loadSource()` 文件 I/O + `XplParseHelper.runImportExprs`（import 表达式求值，可加载任意模型）+ `compileTool.compileFunction` + 生成类绑定 `EvalBackendRouter.bindTagFunction` | **可达**：调用点 `parseTag`（L226，宏标签立即执行 `runMacroExpression`）与 `getFunctionModel`（L651-656）位于模型 entry 监视器链内；compile 内部经 import/宏执行再入其他 entry / 其他 LazyFn | **E4**: entry(A)→LazyFn(B)→entry(C) vs entry(C)→LazyFn(B)；**E5**: LazyFn(B)→LazyFn(C) vs LazyFn(C)→LazyFn(B)（互引宏标签库）。触发条件同为循环 import + 并发 | **C2-017 立案（P2）** |
| 4 | `ResourceComponentManager.loadComponentModel(transform)`（ResourceComponentManager.java:343） | transformed CHM bin | `transformer.transform`（模型→模型编译/变换） | 可达（transformer 自环理论面） | 同 bin 串行化；自环 → IllegalStateException | C2-003 维持 |
| 5 | **`XLangSemantics.CLASS_MODELS.computeIfAbsent`**（XLangSemantics.java:1094-1098） | CHM bin | `ClassHelper.forName` 类加载 + `ReflectionManager.getClassModel` 全量 ClassModel 反射构建（生成代码入口 `newInstance(loc,display,className,...)` 首触） | 键=className，同线程无同键自环；`getClassModel` 内部经 `ClassModelLoader` 链为**有向无环**（超类偏序，见 R2-3） | 无环；同 bin 跨键串行化（预热突发） | **C2-019 立案（P2）** |
| 6 | **`XDslParseHelper.defTypeCache.computeIfAbsent`**（XDslParseHelper.java:344-346,353,362,367） | Caffeine bin | `new XDefTypeDeclParser().parseFromText`（XDef 类型声明递归下降解析） | 无自环（纯解析，不再入缓存） | 无环；同 bin 跨键串行化 | **C2-020 立案（P3，与 #7/#8 合并）** |
| 7 | **`EnumDictLoader.cache.computeIfAbsent`**（EnumDictLoader.java:44-54） | concurrent weak map bin | `buildDictFromEnum`（枚举常量反射遍历 + 字典构建） | 无自环 | 无环 | C2-020 合并 |
| 8 | **`TemplateFileGenerator.tplForceOverrides.computeIfAbsent`**（TemplateFileGenerator.java:506-510） | CHM bin | `readTextHeader(tplFile)` 文件头 I/O（codegen 期） | 无自环 | 无环；codegen 单线程为主 | C2-020 合并 |
| 9 | `ResourceContentCache.getCachedText`（ResourceContentCache.java:52） | Caffeine bin | `resource.readText()` 文件 I/O | 无自环 | 无环 | C2-012 维持 |
| 10 | **`DefaultTaskExecutionQueue.addTaskIfAbsent`**（DefaultTaskExecutionQueue.java:192-201） | `states` CHM bin | `queueTask` → `executor.execute`（任务提交；拒绝策略 CallerRuns 时任务体在 bin 锁内执行） | **条件可达**：任务体若再调 `addTaskIfAbsent` 同 key → 同线程递归 compute → IllegalStateException；CallerRuns + 任务图递归提交放大 | 需 CallerRuns 拒绝策略或同步拒绝实现 + 递归任务图 | **C2-022 立案（P3）** |
| 11 | **`DefaultVirtualFileSystem.refresh/destroy`（synchronized）**（DefaultVirtualFileSystem.java:70-94） | `this` 监视器 | `VfsConfigLoader.loadDefault()`（VFS 配置文件 I/O，经资源系统自身）+ `ClassHelper.safeNewInstance`（反射类加载）+ `builder.build`（zip 打开 I/O）；destroy 面 zip 关闭 | 配置加载经资源系统→模型缓存链可达 entry 监视器（单向，refresh 仅启动/dev 变更触发，无反向边 → 无环） | 无环（无 entry→DFS 监视器反向边） | **C2-021 立案（P3，含 destroy 读者窗口）** |
| 12 | **`NioFileWatchService.watch`（synchronized）**（NioFileWatchService.java:156-192） | `this` 监视器 | `Files.walkFileTree` 全树遍历（文件系统 I/O）+ 逐目录 register | filter 谓词为用户代码（理论嵌套）；watch 注册低频，无反向边 | 无环 | C2-021 合并 |
| 13 | `GlobalExecutors` 3× `computeIfAbsent`（GlobalExecutors.java:39,72,86） | CHM bin | 池构造 + `init()`（含 `prestartAllCoreThreads` → mainLock → 启动线程） | 一次性；`getExecutor` 未命中回退 computeIfAbsent，他线程同键阻塞于 bin 后直取结果，无递归异常 | 无环（单向单次） | 不立案（第 1 轮裁定维持，本轮补证） |
| 14 | `LocalCacheProvider.getCache`（LocalCacheProvider.java:40-47） | CHM bin | `new LocalCache` 构造（轻量，一次性） | 无 | 无环 | 不立案 |
| 15 | `DateHelper/RegexHelper/FastIntegerFormatter computeIfAbsent`（DateHelper.java:272、RegexHelper.java:28、FastIntegerFormatter.java:64） | CHM/静态 bin | `DateTimeFormatter.ofPattern` / `Pattern.compile`（有界 CPU 编译，微秒~毫秒级） | 无自环 | 无环 | 不立案（有界 CPU，无 I/O；C9 镜头注记） |
| 16 | `ClassModel.getBeanModel`（ClassModel.java:279-290，synchronized(this)） | per-ClassModel 监视器 | `BeanModelBuilder.buildFromClassModel`（全量属性/方法反射构建） | **无跨类递归**：BeanModelBuilder 全文无 `getBeanModelForClass`/`getClassModel` 调用（grep 实证）→ 无 ClassModel↔ClassModel 监视器环 | 无环；同类首触串行化（与 ReflectionManager tempLoaders 同型可接受） | 不立案（表内留档） |
| 17 | `GlobalStatManager` 3× synchronized 惰性 init（GlobalStatManager.java:33-49） | `this` 监视器 | `LocalCache.newCache` 构造（轻量） | 无 | 无环 | 不立案 |
| 18 | `CoreInitialization.initializeTo/destroy/reinitialize`（static synchronized，CoreInitialization.java:74,158,208-212） | Class 监视器 | 全平台 initializer 链执行（重 I/O/模型加载/模块装配） | initializer 内再入 `initialize()` → synchronized 可重入，安全；他线程同键阻塞至完成（预期语义） | 无环（单锁） | 不立案（启动期粗锁可接受，表内留档） |
| 19 | `XLangExprParser.getDefaultCompiler`（XLangExprParser.java:56-60） | `s_lock` 静态锁 | `ServiceLoader.load` classpath 扫描（一次性） | 无 | 无环 | 不立案 |
| 20 | `IEvalScope.computeIfAbsent` default（IEvalScope.java:45-54） | per-scope 监视器 | 任意 `builder.apply` | **条件可达**：builder 内调用父/子 scope 的 computeIfAbsent → scope 监视器链 child→parent；反序共享线程对 → 环。nop-kernel 内当前仅 `IExecutionContext.java:72` 委托转发，无重量级内置 builder | 需跨线程共享 scope（C1/C3 相邻面） | **CL-020 登记** |

**① 结论**：C2-001 的「锁内加载/编译→跨监视器嵌套」族站点从第 1 轮的 1 个主站点扩展为上表 #1~#3 的**三层链**（模型 entry → 标签 entry → LazyFn 监视器）；递归嵌套在「循环 import/循环模型依赖 + 并发」下可达，闭合条件与 C2-001 同类。新增闭合环 E4/E5（详见 C2-017）。

---

## R2-2 ② Condition await 面全量判定

全仓 grep（nop-commons/nop-core/nop-xlang/nop-api-core main 源，排除 `_gen`）结果：`await/awaitNanos` 共 4 处、`signalAll/signal` 共 3 处、裸 `Object.wait(` **0 处**。逐站点判定：

| # | 站点 | 等待原语 | signal 侧 | 谓词循环 | 虚假唤醒 | 判定 |
|---|---|---|---|---|---|---|
| 1 | `HighWatermarkSemaphore.acquire`（HighWatermarkSemaphore.java:57 `awaitNanos`） | ReentrantLock+Condition | release L94：向下穿越 lowWatermark 时 `signalAll`，与等待谓词（`overHighWatermark ‖ usedPermits≥low`）边界对齐 | `while` 循环重查 ✓ | awaitNanos 带剩余时间 ✓ | **无缺陷**（第 1 轮结论复核维持） |
| 2 | `LocalResourceLockManager.tryLockWithLease`（L178 `latch.await(waitTime-usedTime)`） | CountDownLatch（非 Condition） | release/forceUnlock `countDown`（L135,198,221）；CDL 语义对虚假唤醒稳健 | `do-while` 重试 putIfAbsent ✓；旧 latch 唤醒后重抢 ✓ | CDL 无虚假唤醒面 | **无缺陷**（中断契约违约已由 C2-007 覆盖；绕过监视器已由 C2-008 覆盖） |
| 3 | `BaseTestCase`（L138 `barrier.await`） | CyclicBarrier（测试基建） | — | — | — | 审计对象外，注记 |
| 4 | **`ContextTaskQueue.syncGet`**（ContextTaskQueue.java:93 `queueReady.await()`） | ReentrantLock+Condition | ①`whenComplete→signalAll`（L52，promise 完成，注册先于 await ✓）；②`enqueue→signalAll`（L118，**仅当 `syncing>0`**） | `do-while(true)` 重查 result ✓、虚假唤醒安全 ✓ | — | **C2-016 立案（P2）**：drain→await 过渡存在丢失唤醒窗口（见条目） |

**② 结论**：4 处 await 站点中 2 处无缺陷（复核维持）、1 处对象外、1 处新立案。`ContextTaskQueue` 的缺陷形态是「signal 侧条件谓词（`syncing>0`）与被通知方实际状态转换之间的窗口」，而非经典的 signal-before-await 排序错误——`whenComplete` 信号注册先于 await 是正确的；缺口在 drain 阶段退出与 `syncing++` 之间 enqueue 既不触发 signal 也不被调用方 flush 补救的组合。

---

## R2-3 ③ 锁序图收口

### 3.1 第 1 轮结论横向验证（指定配对逐对核查）

| 配对 | 核查证据 | 结论 |
|---|---|---|
| TaskQueue 锁 vs 池锁（mainLock） | TaskQueue 对 parent 的全部调用（offer L59-79 的 `getPoolSize/getSubmittedCount`、take L92-99 的 `currentThreadShouldBeStopped`、poll L84-89 的 `stopCurrentThreadIfNeeded`、force L48-56 的 `isShutdown`）均在**进入 LBQ 内部 putLock/takeLock 之前或返回之后**顺序执行，无任何「持队列锁调 parent / 持 parent 锁入队」嵌套。`StandardThreadPoolExecutor`（extends JDK TPE）自身无新锁（仅原子字段）；唯一嵌套是 JDK 内部 `setCorePoolSize` 持 mainLock → `drainTo`（putLock），**单向**，反向（putLock→mainLock）不存在 | **无环** |
| entry 监视器 vs LocalResourceLockManager 锁表 | grep 实证：nop-core/nop-xlang 全部模型加载/编译路径**无任何 `IResourceLockManager` 调用**（该设施在 nop-kernel 内仅 lock 包自引用）；模型加载路径与锁管理器无共享路径，两组锁无交集 | **无环（无共享路径）** |
| GlobalExecutors 内部锁（CHM bin）vs 池内部锁（mainLock） | `computeIfAbsent` → `init()` → `prestartAllCoreThreads`（mainLock→启动线程→worker takeLock）为**单向一次性**边 bin→mainLock→takeLock；worker 运行期不持任何池锁调用 GlobalExecutors；他线程同键回退路径经 CHM bin 阻塞后直取结果（CHM 保证），无递归异常 | **无环（单向单次）** |
| CycleSynchronizer（E3） | 第 1 轮 C2-004 已立案，本轮复核维持 | 已立案 |

### 3.2 第 1 轮结论修正与新闭合环

1. **修正 1（ClassModelLoader「无嵌套」结论不精确）**：`ClassModelBuilder` 在构建期调用 `reflectionManager.getClassModel(inf/superClass)`（ClassModelBuilder.java:374,472,517），而 `getFromCache` 持 per-class `ClassModelLoader` 监视器（ReflectionManager.java:336-350）→ **存在嵌套边 ClassModelLoader(A)→ClassModelLoader(B)**。但该边仅沿「超类/接口解析」产生，Java 类层级为严格偏序（JLS 禁止类成为自身的超类型）→ 监视器图**可证无环**（最深子类链形如串行锁链，仅构成预热期串行化）。降为验证注记，不立案。
2. **新闭合环 E4/E5（LazyFn 监视器节点）**：`XplLibTagCompiler.parseTag`（L196-226）在**模型 entry 监视器内**（编译使用该标签的模板模型时）依次获取：标签缓存 entry 监视器（L210/212，首触时锁内执行 buildCompiledTag = 源文件 I/O + 解析）→ `compiledTag.lazyCompile()`（L226）→ `LazyCompiledFunction.compile` 监视器（L734，锁内执行 import 表达式求值 L800 与宏执行）→ 宏体内可再 require 任意模型 entry / 其他 LazyFn。据此新增两条可闭合锁序环：
   - **E4**: `entry(A) → LazyFn(B) → entry(C)` 与反序 `entry(C) → LazyFn(B)`（B 的标签在 C 的编译期被宏调用，且 B 的惰性编译 import 依赖 A）——与 C2-001 同触发类（循环 xlib import + 并发）。
   - **E5**: `LazyFn(B) → LazyFn(C)` 与 `LazyFn(C) → LazyFn(B)`（两个 xlib 互用对方宏标签）。
   全部边均有代码实证（链路见 C2-017 证据），**第 1 轮「全模块唯一可闭合锁序环在 E1/E2/E3」的结论不完整**，修正为「E1/E2/E3/E4/E5，其中 E4/E5 为本轮新增」。

### 3.3 其余新增嵌套边（单向，无环）

- `LazyFn(B) → EvalBackendRouter.this`（`applyGeneratedClassBinding` → `bindTagFunction` → `recordDecision` synchronized，XplLibTagCompiler.java:760-762、EvalBackendRouter.java:302-306）：EvalBackendRouter 全部 synchronized 方法为叶子（不获取其他锁），单向无环。
- `DefaultVirtualFileSystem.this → 模型缓存 entry`（refresh 内配置加载，表 R2-1 #11）：refresh 无反向边，单向。
- `LocalResourceLockManager` 内部：latch 监视器 → CHM bin（locks.putIfAbsent）单向；第 1 轮结论维持。

---

## R2-4 ④ 读写锁/StampedLock 缺位面

全 A/B 面无 `ReadWriteLock/ReentrantReadWriteLock/StampedLock` 使用（grep 0 命中）。逐候选判定「读多写少但独占锁」：

| 候选 | 读路径频率 | 判定 |
|---|---|---|
| `ServiceContextImpl`（18 个 synchronized getter/setter，L56-147） | 请求域对象，读写均单线程为主 | 过锁但零争用；**不对称缺陷**：`newChildContext`（L121-132）无锁读 `cache/userContext` 等字段而 setter synchronized → CL-019 一并登记 |
| `ExecutionContextImpl.isDone/getError`（L184-221 synchronized 读） | 每次完成判定，per-execution 实例 | 零争用，不立案 |
| `ClassExtension.getHelperMethods`（L29-31 synchronized 拷贝） | 仅 ClassModelBuilder L627 构建期调用 | 非热路径，不立案 |
| `EvalBackendRouter.recordDecision/getRecentDecisions`（L291-306） | 写为主（每次编译裁决）、环形缓冲、有界 | 非读多写少，不立案 |
| `XLangSemantics.PROP_ACCESSORS/OBJ_FUNCTION_HANDLES`、各注册表 | CHM 无锁读 | 正确形态（对照） |
| `ReflectionManager.introspectCache/converterCache`、`GlobalCacheRegistry` | Caffeine/CHM 无锁读 | 正确形态（对照） |

**④ 结论**：不存在「全局读多写少结构误用独占锁导致可量化读争用」的立案级站点；真实读热路径均已落在 CHM/Caffeine/volatile 快照形态。纪律缺口仅剩 CL-019 记录的 context 族不对称锁。

---

## R2-5 ⑤ 可重入假设与锁对象逃逸面

1. **synchronized(this) 方法间互调的重入依赖**：逐类核对——`CycleSynchronizer`（beginCycle/waitCycleCompleted/resolvePromise 互不调用，无重入依赖）；`DefaultVirtualFileSystem.refresh→buildResourceStore`（非同步方法，无依赖）；`ExecutionContextImpl.complete→fireBeforeComplete`（回调在锁外执行，内部 synchronized 块仅快照）；`ClassModel.getBeanModel`（构建器无同实例二次进入）。**结论：无站点隐式依赖监视器可重入性；若改 ReentrantLock 无结构性破坏**，但 C2-004 立案的「持锁回调」面在改造时须同步把回调移出锁（try/finally 纪律）。
2. **锁对象逃逸**：
   - `LocalResourceLockState.getLatch()`（LocalResourceLockManager.java:84）——包私有类，`synchronized(lock.getLatch())` 的 4 处使用全部在 manager 内部，`IResourceLockState` 接口不暴露 latch → **未逃逸** ✓。
   - `ResourceCacheEntry` 以 this 为监视器，entry 对象不超出 `ResourceLoadingCache` 内部 → 未逃逸 ✓。
   - `CycleSynchronizer.this`、`DefaultVirtualFileSystem.this` 作为公有对象即锁对象——外部可 `synchronized(x)` 干预（理论面），全仓无此调用 → 注记不立案。
   - `TaskQueue`：`setParent` synchronized(this) 与 LBQ 内部双锁无交叉 → 无逃逸面 ✓。
3. **本轮 ⑤ 发现**：`CompiledTag.deps` 非 volatile（XplLibTagCompiler.java:171），在 LazyFn 监视器内写（L753,769）、经 `ResourceCacheEntry.isChanged→isObjectChanged`（L178-183）**无锁读**——同文件 `compiledFn/compileException`（L720-721）已带 volatile 与可见性注释，`deps` 为同族遗漏 → **C2-018 立案（P3）**。

---

## 第 2 轮（深挖）发现

### [nop-kernel-concurrency-C2-016] ContextTaskQueue.syncGet 的 drain→await 过渡存在丢失唤醒窗口：enqueue 信号谓词 syncing>0 与 drain 退出窗口错位，且调用方 flush 补救路径在处理者未退出时静默放弃——同文件 flush 已修（endProcessIfIdle）而 syncGet 未修的不对称

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/ContextTaskQueue.java:69-105`（syncGet drain 段 finally `endProcess()` L81、`queueReady.await()` L93）、`L113-125`（enqueue 仅 `syncing>0` 才 signalAll L117-118）、`L127-135`（flush 的 `beginProcess` 失败即 return）、`L161-173`（endProcessIfIdle——已修复的对照面）、`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/BaseContext.java:227-244`（runOnContext 的 flush 兜底）
- **证据片段**:
  ```java
  // syncGet：drain 段退出——无条件 endProcess，无 idle 复检
  if (beginProcess()) {
      try { do { Runnable task = tasks.poll(); if (task == null) break; ... } while (true); }
      finally { endProcess(); }            // ← 此处到 syncing++ 之间是窗口
  }
  lock.lock(); try {
      syncing++; f = result.get();
      if (f != null) return f.syncGet();
      queueReady.await();                  // 等待谓词 = result 完成
  // enqueue：tasks.add 在锁外先做，随后：
  if (syncing > 0) { queueReady.signalAll(); return true; }   // syncing==0 → 无信号
  return false;                            // → 调用方 runOnContext 走 flush() 兜底
  // flush：处理者尚未退出时 beginProcess 直接失败放弃
  public void flush() { if (!beginProcess()) return; ... }
  // 对照：flush 自己的循环用了带复检的 endProcessIfIdle（L161-173 注释明确描述了同一窗口）
  ```
- **严重程度**: P2（窗口窄但三重修复机会全部错过；若 promise 完成依赖滞留任务则升级为永久挂死 → P1 条件已注明）
- **并发场景**: 该队列是全平台异步上下文桥（`BaseContext` 每个请求/异步上下文一个实例；`FutureHelper.syncGet` 经 `ContextProvider` 全平台路由至此）。设计用法即「异步线程 runOnContext + 上下文线程 syncGet」并发。
- **触发时序**: T1（上下文线程）syncGet：beginProcess 成功（processingThread=T1）→ poll 到 null → break → **尚未执行 finally endProcess**。T2（异步线程）runOnContext：`tasks.add(task)` → lock → `syncing==0` → 不 signal，返回 false → 调 `flush()` 兜底 → `beginProcess()`：`processingThread==T1≠T2 && !=null` → **返回 false，flush 静默返回，任务无人执行**。T1 恢复：endProcess（processingThread=null）→ lock → syncing++ → result 仍 null → `await()`。此后队列中有滞留任务、syncing==1，enqueue 类信号永不再来；T1 仅等 result。**若 result 的完成依赖于滞留任务执行**（runOnContext 提交的正是完成该 promise 的收尾动作——编排/流式场景的常规形态），则 T1 永久挂死（F5，栈停在 await 无日志）；若 promise 可独立完成，任务被任意延迟至下次 promise 完成才有机会被 drain（F8 延迟清算）。窗口宽度 = 「最后一次空 poll」到「endProcess 释放 processingThread」之间的调度间隙。
- **现状**: `endProcessIfIdle`（L161-173）的 Javadoc 注释逐字描述了这一 check-then-act 窗口并在 flush 循环内做了二次确认修复；`syncGet` 的 drain 段仍用无条件 `endProcess()`，从 drain 退出到 await 谓词（result）之间未复检 `tasks` 非空——**同文件内修复只覆盖了 flush 面、漏掉 syncGet 面**（与 C2-010 send/offer 同族不对称）。
- **风险**: 平台异步上下文的静默任务滞留；依赖任务序的 promise 永久等待。多上下文高并发下窗口叠加概率线性放大。
- **建议**: syncGet 的 drain 段退出改为 `endProcessIfIdle()` 返回 false 时继续循环（与 flush 同构）；或将 enqueue 的 signal 谓词放宽为 `syncing > 0 || processingThread != null`（处理中的线程最终会经过 awaiting 段，signal 不丢失）；两修其一即可闭合窗口，建议前者（复用已验证的 endProcessIfIdle 语义）。
- **失效面枚举**（F3：ContextTaskQueue/BaseContext 等待-通知面全量）:
  1. `syncGet` drain 段 finally `endProcess()`（L81）——主站点。
  2. `syncGet` await 段（L93）——谓词仅含 result，不含 tasks 非空，随本案一并修复（await 前补 tasks 空判定亦可）。
  3. `enqueue` signal 谓词 `syncing>0`（L117）——谓词过窄，随本案一并裁定。
  4. `flush`（L127-135）——自身循环已用 endProcessIfIdle 正确，但入口 `beginProcess` 失败静默放弃是本案触发链的一环，修复方案 1 使其不再是唯一兜底。
  5. `endProcessIfIdle`（L161-173）——正确对照面（留档）。
  6. `BaseContext.runOnContext`（L227-244）——调用方，随设施修复自动覆盖。
  7. `whenComplete→signalAll`（L52）——promise 完成信号，注册先于 await，正确，不立案。
- **信心水平**: 确定（三步交错均在代码语义内可构造；同文件对照注释自证窗口真实存在）
- **误报排除**: 不是「signal 谓词保守即误报」——谓词 `syncing>0` 恰好漏掉 `processingThread!=null` 的 drain 退出段，而 flush 入口的 beginProcess 失败路径使兜底失效；排除「窄窗口可接受」校准：该设施承载全平台 FutureHelper.syncGet，且同文件注释已认定该窗口需要修复。
- **复核状态**: 复核维持 P2，处置=维持条目并标注「nop-api-core 边界面，Phase 6 移交」（不降 CL、不驳回）。三步交错逐行亲核成立：syncGet drain 段 break（L68）→finally endProcess（L81）→syncing++（L87）之间的窗口内，T2 enqueue 读 syncing==0 不 signal（L117-118）返回 false→BaseContext.runOnContext flush 兜底 beginProcess 因 processingThread==T1 失败静默放弃（BaseContext L230-235）→任务滞留；flush 循环已用 endProcessIfIdle（L161-173 注释自证同一窗口）而 syncGet 未用的不对称属实。边界事实亲核：该类位于 nop-api-core（io.nop.api.core.context），属审计单元 A/B 面之外；但其承载全平台 FutureHelper.syncGet/BaseContext 路由（BaseContext L312），缺陷经核实且影响面大，驳回将丢失已验证缺陷、降 CL 将低估已证实严重度——维持条目+移交标注（api-core 公共 API 变更受 plan-first 保护约束，修复须走 Phase 6 对应治理）。现状标注：flush 面 endProcessIfIdle 系早前修复轮已落地（commit 5ee798e02e），syncGet 面遗留未修——本条为该修复面的剩余缺口，新发现

### [nop-kernel-concurrency-C2-017] LazyCompiledFunction.compile 持自身监视器执行完整 XPL 编译（源文件 I/O + import 表达式求值 + 宏执行 + 生成类绑定），且在模型 entry 监视器链内被调用——锁序图新增节点集与 E4/E5 两条可闭合环（C2-001 家族扩展，①+③ 合并裁定）

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/xlib/XplLibTagCompiler.java:734-770`（compile synchronized）、`L800`（parseSource→`runImportExprs`）、`L194-236`（parseTag：entry 监视器内 getObject + lazyCompile + 宏立即执行）、`L651-656`（getFunctionModel）、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/LazyCompiledExecutableFunction.java:80`（运行时首次调用触发 getCompiledFn→compile）
- **证据片段**:
  ```java
  public synchronized void compile() {                 // 持 per-LazyFn 监视器
      ...
      compiled = true;
      ResourceDependencySet deps = new VirtualResourceDependencySet(...);
      ResourceComponentManager.instance().collectDependsTo(deps, () -> {
          ArrowFunctionExpression ast = parseSource(compileTool);   // loadSource 文件 I/O
          compiledFn = compileTool.compileFunction(ast);            // 完整编译
          applyGeneratedClassBinding();                             // → EvalBackendRouter（叶子锁）
          ...
      });
  }
  // parseTag（在其他模型 entry 监视器内执行——编译使用该标签的模板模型时）：
  compiledTag = this.cachedCompiledTagForNodeMode.getObject(...); // 标签缓存 entry 监视器（首触 buildCompiledTag：源 I/O+解析）
  ...
  compiledTag.lazyCompile();                          // → LazyFn 监视器
  if (tag.isMacro()) ret = ExprEvalHelper.runMacroExpression(callExpr, scope, ...); // 宏立即执行，可 require 任意模型
  ```
- **严重程度**: P2（触发类与 C2-001 相同——循环 xlib import + 并发；因多一层「编译期宏执行」条件，闭合概率低于 C2-001 主环，故不升 P1；C2-001 的修复方案必须同时覆盖本条节点，否则死锁环仍可经 LazyFn 节点闭合）
- **并发场景**: 多请求线程并发编译/执行使用 xlib 宏标签的模板/脚本（宏在编译期展开 = `parseTag` 在**模板模型的 entry 监视器内**执行 `runMacroExpression`）；全部经 `ResourceComponentManager` 模型加载通道。
- **触发时序**: （E4）T1 加载并编译模板 M（持 entry(M) 监视器）→ M 使用 lib B 的宏标签 → parseTag → `LazyFn(T_B).compile`（持 LazyFn 监视器）→ `runImportExprs` 求值 import → require 模型 `/c/base.xlib` → 阻塞于 entry(C)。T2 同时加载 C（持 entry(C) 监视器）→ C 的编译期同样宏调用 lib B 标签 → 阻塞于 `LazyFn(T_B)` 监视器 → **entry(M)→LazyFn(B)→entry(C) 与 entry(C)→LazyFn(B) 互逆，环闭合**，两层监视器均无超时。（E5）lib B 的标签体宏调用 lib C 的标签、lib C 的标签体宏调用 lib B 的标签：T1 持 LazyFn(B) 编译中触发 LazyFn(C)、T2 持 LazyFn(C) 触发 LazyFn(B) → 监视器对互逆闭合。（延迟面）非死锁场景下，`LazyFn(T).compile` 把「首次调用该标签的全部并发请求」串行在一次完整编译上（源 I/O+编译可达百毫秒级），同标签热标签首请求尾延迟放大。
- **现状**: `compiled=true` 先置 + `compileException` 缓存已正确处理 Exception 重放（不可恢复态已修）；但监视器覆盖面 = 完整编译 + 编译期用户代码（import 求值/宏执行），与 C2-001 的 entry 监视器覆盖面问题同构。
- **风险**: 与 C2-001 叠加后，修复 C2-001 的「锁外加载」方案若只移 `loader.loadObjectFromPath`，`parseTag→lazyCompile` 链仍在模型 entry 监视器内制造 LazyFn 节点嵌套，E4 环不完全消除；表现为偶发全平台编译通道挂死（对健康检测不可见）。
- **建议**: (a) `LazyCompiledFunction.compile` 改 per-key 单飞：监视器内只做 compiled 双检 + 占位（`CompletableFuture`/`FutureHelper.futureCall`），编译在监视器外执行后 complete；(b) `parseTag`/`getFunctionModel` 的 `lazyCompile()` 调用移出模板 entry 监视器（编译产物返回后再触发惰性编译）；(c) 与 C2-001 修复方案合并设计，把「entry 监视器只覆盖同 key 去重 + 占位」的不变式贯穿三层链（模型 entry / 标签 entry / LazyFn）。
- **失效面枚举**（F3：「持锁编译/编译期用户代码」同族全量）:
  1. `LazyCompiledFunction.compile`（L734-770）——主站点（编译+I/O+import 求值+宏）。
  2. `parseTag` 内 `lazyCompile()` 调用（L226）与宏执行（L228-230）——模板 entry 监视器内的触发链，本案一并裁定。
  3. `getFunctionModel`（L651-656）——同链第二入口。
  4. `cachedCompiledTag/ForNodeMode` 的 `buildCompiledTag`（经 ResourceCacheEntryWithLoader，L108/129）——标签源 I/O + 解析在标签 entry 监视器内，C2-012 同族形态，随本案 (c) 修复覆盖（单独判 P3 而非独立立案：无环闭合路径，仅锁内 I/O）。
  5. `BuildExecutableProcessor.buildIdentifierCall`（L1321-1323）→ `LazyCompiledExecutableFunction`——包装不触发编译（invoke 时才 compile），编译面与 #1 同案。
  6. `ResourceCacheEntry.getObject` 主环（C2-001）——同族主站点，不重复。
  7. `XLangSemantics.CLASS_MODELS`（编译期类解析）——独立立案 C2-019。
- **信心水平**: 很可能（全部边有代码实证；死锁环闭合依赖「宏标签编译期互调/循环 import」的用户配置形态，与 C2-001 同级前提）
- **误报排除**: 不是「编译慢即误报」——问题在监视器覆盖任意编译期用户代码并与其他监视器构成可闭合环；E4/E5 的每条边均可指出调用点行号，非推演想象。
- **复核状态**: 复核维持 P2。LazyCompiledFunction.compile synchronized 锁内完整编译链（parseSource 文件 I/O + compileFunction + applyGeneratedClassBinding→EvalBackendRouter 叶子锁）亲核；parseTag（L195-236）→getObject（标签 entry 监视器）→lazyCompile（L226）→宏立即执行 runMacroExpression（L228-230）链亲核；getFunctionModel（L651-656）第二入口亲核；LazyCompiledExecutableFunction 运行时首触编译面（getCompiled→getCompiledFn→compile）亲核。「parseTag 位于模型 entry 监视器内」的可达性经两条具体路径确认：①AbstractDslParser.runPreParse/runPostParse 在加载期 entry 监视器内 compileTagBody/执行钩子（宏标签在其内展开）；②applyCompileConfig 的 ImportTagCompiler.parseTag（x:config c:import）。E4/E5 有向边全部有代码实证，闭合需循环 xlib 形态用户配置——与 C2-001 同根因族、多一层编译期宏条件，「不升 P1、修复须与 C2-001 同一变更集」判定正确。现状：新发现（与 C2-001 同族扩展节点，见维度复核结论三节点关系）

### [nop-kernel-concurrency-C2-018] CompiledTag.deps 非 volatile：LazyFn 监视器内写、ResourceCacheEntry.isChanged 热路径无锁读，无 happens-before 边——同文件 compiledFn/compileException 已 volatile 的自证遗漏（⑤）

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/xlib/XplLibTagCompiler.java:171`（`ResourceDependencySet deps;` 声明，非 volatile）、`L753,769`（compile() 监视器内写）、`L178-183`（`isObjectChanged()` 无锁读）、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceCacheEntry.java:166`（`isChanged→isObjectChanged` 调用点，快路径无锁）
- **证据片段**:
  ```java
  static class CompiledTag implements IObjectChangeDetectable {
      ResourceDependencySet deps;                    // L171：非 volatile，无锁保护读
      ...
      private volatile IEvalFunction compiledFn;     // L720：volatile + 可见性注释
      private volatile RuntimeException compileException;  // L721
      public boolean isObjectChanged() {             // L178：无锁读 deps
          ResourceDependencySet deps = this.deps;
          if (deps == null) return false;
          return ResourceComponentManager.instance().isAnyDependsChange(deps.getDepends());
      }
  ```
- **严重程度**: P3（后果为依赖变化检测的假阴性/脏读迭代，不自愈但不挂死）
- **并发场景**: T1 请求线程首次调用标签 → `LazyFn(T).compile`（持 LazyFn 监视器）→ 编译完成后 `compiledTag.deps = deps`（普通写）。T2 另一请求线程（或 dev 模式 checkChanged=true 的全部后续请求）→ `ResourceCacheEntry.getObject(checkChanged=true)` 快路径 → `isChanged()` → `isObjectChanged()` 读 `deps`——T2 的读与 T1 的写之间**仅有「entry.object 发布」这条 HB 边可依赖，而 deps 的写入发生在发布之后**（惰性编译），无任何同步边。
- **触发时序**: T2 读到 deps==null（写未可见）→ 恒判「未变化」→ 依赖文件已变更时不触发重载（假阴性，陈旧标签函数继续服务）；或读到引用但 `VirtualResourceDependencySet` 内部集合对 T2 部分可见 → `isAnyDependsChange` 迭代到不一致快照 → 结果不确定（假阴/假阳）。deps 写入后随时间与后续同步动作（其他 volatile 读、锁）最终可见，偏差收敛但无时限保证。
- **现状**: 同一内部类中 `compiledFn`/`compileException` 的声明处注释明确写了「invoke() 无锁读取编译产物……volatile 补齐无发布中介场景的可见性」——`deps` 的消费路径（isObjectChanged 同样无锁读）是同型场景，为遗漏而非设计差异。
- **风险**: dev 热重载语义（标签依赖文件变更后重编译）偶发失效；生产 checkChanged=false 时不读此路径，危害限于 dev/可重载配置面。
- **建议**: `deps` 补 volatile（与 L720-721 同型，一行修复）；或把 `compiledTag.deps = deps` 提前到 compile() 内 volatile 写 `compiledFn` 之前并由读方以 `compiledFn != null` 作为 deps 就绪的门控（利用已有 volatile 定序）。
- **失效面枚举**（F3：CompiledTag/依赖集可见性面全量）:
  1. `CompiledTag.deps` 声明与 isObjectChanged 读（L171/178-183）——本案。
  2. `LazyFn.compile` 内 `compiledTag.deps = deps`（L753,769）——写侧，随本案加 volatile 覆盖。
  3. `compiledFn/compileException`（L720-721）——正确对照（留档）。
  4. `ResourceCacheEntry.isChanged→isObjectChanged`（ResourceCacheEntry.java:160-170）——无锁调用面，随本案覆盖（entry 自身 object 字段 volatile 正确）。
  5. `ResourceComponentManager.ComponentCacheEntry.isObjectChanged`（L423-426）——其 model 的 IObjectChangeDetectable 转发，若 model 为 CompiledTag 随本案覆盖。
  6. `ResourceComponentManager.collectDependsTo` ThreadLocal 面——归属 C1（第 1 轮盲区自评 (b)），不重复。
- **信心水平**: 很可能（可见性缺口确定；实际触发频率取决于 JIT/屏障实现，JMM 层面允许）
- **误报排除**: 不是「理论 JMM 强迫症」——同文件同类字段已用 volatile 自证该场景在本平台的必要性标准；deps 是同一不可变发布模式的遗漏成员。
- **复核状态**: 复核维持 P3。deps 非 volatile（L171）+ isObjectChanged 无锁读（L178-183）+ 写侧监视器内两处赋值（catch L753 / 正常路径 L769）亲核；volatile compiledFn/compileException（L720-721 含可见性注释）对照亲核。git 史核实：兄弟字段 volatile 系早前修复轮（commit aab58b47be）补齐、该轮未覆盖 deps——「遗漏而非设计差异」由修复历史坐实。现状：新发现（同族先例已部分修复的遗留成员）

### [nop-kernel-concurrency-C2-019] XLangSemantics.CLASS_MODELS.computeIfAbsent 在 CHM bin 锁内执行类加载（ClassHelper.forName）+ 全量 ClassModel 反射构建——生成代码 `new` 首触路径的锁内重活（C2-003 同族新站点，①）

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/XLangSemantics.java:1094-1098`（CLASS_MODELS + computeIfAbsent）、`L631-637`（resolveClass→`ClassHelper.forName`）、`NewObjectExecutable.java:47`（解释器入口复用编译期 classModel 的对照面）
- **证据片段**:
  ```java
  private static final Map<String, IClassModel> CLASS_MODELS = new ConcurrentHashMap<>();
  /** NewObjectExecutable 完整语义（生成代码入口；按 className 缓存 classModel）。 */
  public static Object newInstance(SourceLocation loc, String display, String className,
                                   Object[] argValues, IEvalScope scope) {
      IClassModel classModel = CLASS_MODELS.computeIfAbsent(className, k ->
              ReflectionManager.instance().getClassModel(resolveClass(k)));  // bin 锁内：类加载+反射建模
  ```
- **严重程度**: P2（与 C2-003 同校准：首触重量级 + 全平台并发调用 + 跨键 bin 串行化；无自环则不涉异常面）
- **并发场景**: 生成代码（Java backend 产物）并发执行 `new SomeClass{}`——每类首触走 computeIfAbsent；预热期（首发流量/批量任务启动）多类并发首触。
- **触发时序**: T1 首触 `com.a.Foo` → bin 锁内 `forName`（触发类加载器锁/父委托）+ `getClassModel`（内部 `ClassModelLoader(A)` 监视器 + 全类反射扫描，毫秒级；沿超类链可嵌套多级 ClassModelLoader 监视器，见 R2-3 修正 1）→ 同 bin 的无关类 `com.b.Bar` 首触请求在 bin 上阻塞至 T1 完成。无同键自环（className→同线程不会递归求同键）；ClassModelLoader 链按超类偏序无环（R2-3），故无死锁面，纯串行化/尾延迟。
- **现状**: 注释自称「生成代码入口」——与解释器入口 L47（复用编译期 classModel，「零查找」）并列；生成路径未享受零查找，也未按 Future 占位去重。
- **风险**: 预热突发期跨键串行化放大首请求尾延迟；类加载器锁在 bin 锁内引入与外部类加载活动的隐式耦合（自定义类加载器死锁的教科书形态：bin 锁顺序化使风险收窄为延迟而非死锁——因 ClassModelLoader 链无环且类加载父子委托无环）。
- **建议**: 与 C2-003 建议同构：`computeIfAbsent(className, k -> new CompletableFuture<IClassModel>())` 占位后锁外构建、`complete`/`completeExceptionally`；或接入 `LocalCache.computeIfAbsentAsync`（平台现成设施）。低优先级替代：预热期批量预载常用类。
- **失效面枚举**（F3：运行时「compute 锁内类加载/建模」同族）:
  1. `XLangSemantics.CLASS_MODELS.computeIfAbsent`（L1096）——主站点，本案。
  2. `XLangSemantics.OBJ_FUNCTION_HANDLES`（L231）/`PROP_ACCESSORS`（L655）——轻量 new，正确（第 1 轮已判，留档）。
  3. `ReflectionManager.getFromCache`（L336-350）——per-class loader 监视器只覆盖构建、`introspectCache.put` 在锁外，正确形态（对照）；其嵌套链无环（R2-3 修正 1）。
  4. `ReflectionManager.converterCache.computeIfAbsent`（L419）——构建轻量（第 1 轮已判）。
  5. `EnumDictLoader.cache.computeIfAbsent`——dict 构建，归 C2-020。
  6. `NewObjectExecutable.execute`（L47）——编译期 classModel 复用路径，零查找，正确（对照）。
- **信心水平**: 确定（锁内类加载+建模事实）；影响为延迟面
- **误报排除**: 不是「CHM 使用即误报」——mapping 内容为阻塞类加载 + 反射全量建模，属审计 prompt C2 定向检查项；无环故无挂死误报成分。
- **复核状态**: 复核维持 P2。CLASS_MODELS.computeIfAbsent（L1094-1098）bin 锁内 resolveClass（ClassHelper.forName，L631-637）+ ReflectionManager.getClassModel 全量建模亲核；无同键自环、ClassModelLoader 嵌套链沿超类偏序无环（R2-3 修正 1）亲核——纯延迟面判 P2 与 C2-003 同校准，正确。现状：新发现。三节点关系：同「锁内重活」纪律族、非死锁族（见维度复核结论）

### [nop-kernel-concurrency-C2-020] 锁内解析/构建族（低频面）：XDslParseHelper.defTypeCache 在 Caffeine bin 锁内做 XDef 类型递归解析、EnumDictLoader 在 weak-map bin 锁内构建枚举字典、TemplateFileGenerator 在 CHM bin 锁内做模板文件头 I/O（C2-012 同族扩展，①）

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslParseHelper.java:344-346,353,362,367`、`nop-kernel/nop-core/src/main/java/io/nop/core/dict/EnumDictLoader.java:44-54`、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/tpl/TemplateFileGenerator.java:506-510`
- **证据片段**:
  ```java
  static final ICache<String, XDefTypeDecl> defTypeCache = LocalCache.newCache("def-type-parse-cache", newConfig(1000));
  return defTypeCache.computeIfAbsent(text, k -> new XDefTypeDeclParser().parseFromText(loc, text));  // bin 锁内递归下降解析
  ...
  return cache.computeIfAbsent(classModel.getRawClass(), clazz -> buildDictFromEnum(dictName, classModel)); // bin 锁内枚举反射构建
  ...
  boolean tplOverride = tplForceOverrides.computeIfAbsent(tplFile.getPath(), p -> {
      String srcText = readTextHeader(tplFile); ...   // bin 锁内文件头 I/O
  ```
- **严重程度**: P3（三站点均为低频面：XDef 解析集中在 DSL 加载期、枚举字典按类一次、模板头读取在 codegen 期；无正确性问题）
- **并发场景**: 并行模块装配期多线程解析各 DSL 的类型声明；并发首查枚举字典；codegen 多模板并发（当前主要单线程 maven 执行）。
- **触发时序**: 同 C2-012 形态——同 bin 无关键在 mapping 期间被串行化。XDef 文本可能为长复合类型声明（递归下降 + 每属性节点遍历），单次毫秒级；`readTextHeader` 为磁盘 I/O（经 contentCache 时为 C2-012 同一 Caffeine 面的嵌套调用——bin 套 bin，两把不同 Caffeine 实例的锁，方向恒定 tplForceOverrides→contentCache，无环）。异常面：parse 抛出时 Caffeine 不缓存异常，下次重试（正确）；无墓碑形态。
- **现状**: 与 C2-012（ResourceContentCache）同族——「Caffeine bin 锁内做解析/I/O」的平台惯例形态，第 1 轮仅覆盖了 readText 站点；本轮按「同族新站点立案」扩展登记。
- **风险**: DSL 批量装配期尾延迟；无死锁/丢失面。
- **建议**: 维持现状可接受（判 P3 依据）；若统一治理，采用 C2-003 的 Future 占位模式一并改造三站点；`tplForceOverrides` 可改为 get-then-putIfAbsent（值幂等布尔，竞态安全且无锁内 I/O）。
- **失效面枚举**（F3：C2-012 同族「bin 锁内解析/构建/I/O」补充清单）:
  1. `XDslParseHelper.parseAttrDefType/parseDefType/cacheDefType`（L353/362/367）——解析，本案。
  2. `EnumDictLoader.loadDict`（L46）——枚举字典构建，本案。
  3. `TemplateFileGenerator` tplForceOverrides（L508）——文件头 I/O，本案；嵌套 contentCache（C2-012）为单向 bin→bin，无环（注记）。
  4. `ResourceContentCache.getCachedText`（C2-012 主站点）——不重复。
  5. `LocalCacheProvider.getCache`（L40）——构造轻量一次性，不立案。
  6. `DateHelper/RegexHelper/FastIntegerFormatter`（表 R2-1 #15）——有界 CPU 编译，不立案（留档）。
- **信心水平**: 确定（锁内操作事实）；影响推断（低频）
- **误报排除**: 不是「缓存 mapping 慢即误报」——按频率校准判 P3 而非漏报；三站点无异常路径残留（对照 C2-015 的失败留存形态已核）。
- **复核状态**: 复核维持 P3。三站点亲核：XDslParseHelper.defTypeCache（L344-367）、EnumDictLoader.loadDict（L44-54）、TemplateFileGenerator.tplForceOverrides（L506-510，readTextHeader 锁内 I/O + 嵌套 contentCache 单向无环）；低频面校准正确。现状：新发现

### [nop-kernel-concurrency-C2-021] DefaultVirtualFileSystem 持 this 监视器执行 VFS 配置加载（文件 I/O + 反射 newInstance + zip 构建），destroy 与无锁读者的 zip-closed 窗口——refresh 注释自证已修 refresh 面而 destroy 面同窗未修（①+C2-002 家族注记）

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DefaultVirtualFileSystem.java:70-77`（refresh synchronized）、`L79-83`（buildResourceStore：`VfsConfigLoader.loadDefault()` + `ClassHelper.safeNewInstance` + `builder.build`）、`L87-94`（destroy synchronized：替换 store 后 `safeCloseAll(zipFiles)`）、`L24-27`（volatile 读路径声明注释）
- **证据片段**:
  ```java
  public synchronized void refresh(boolean refreshDepends) {
      // 先构建并发布新store，最后再关闭旧的zipFiles。
      // 如果先关闭旧zip，构建期间读线程通过volatile读到旧store并访问其中的ZipEntryResource
      // 会抛出zip file closed异常                    ← 注释自证读者窗口存在
      List<ZipFile> oldZipFiles = this.zipFiles;
      this.buildResourceStore();      // 监视器内：配置文件 I/O + 反射建 store + 打开 zip
      IoHelper.safeCloseAll(oldZipFiles);
  }
  public synchronized void destroy() {
      ... this.deltaResourceStore = newDelta;   // 先替换
      IoHelper.safeCloseAll(zipFiles);          // 后关闭——但「更早」拿到旧 zip 资源的读者仍在读
  ```
- **严重程度**: P3（refresh 频率：启动 + dev 文件变更回调；destroy：停机；读者窗口后果为偶发 zip closed 异常）
- **并发场景**: dev 模式 FileWatcher 触发 `refresh()` 与请求线程 `getResource`（无锁 volatile 读）并发；停机 `destroy()` 与尾延请求并发。
- **触发时序**: （锁内重活）T1 refresh 持 this 监视器：`loadDefault()` 经资源系统自身读取 `/nop/vfs/_vfs.xml`（可经模型缓存链触 entry 监视器——单向边，R2-3）；`safeNewInstance` 反射加载 store builder 类；`builder.build` 打开全部 vfs zip。期间全部 `getResource/getChildren` 读路径不取该监视器（volatile 快照）→ 无读阻塞（设计意图），但 refresh 自身被串行化（两次 refresh 并发时后者持等，前者含 I/O 全程）。（destroy 读者窗口）T2 请求线程已从旧 store 取得 ZipEntryResource 并开始读 → T1 destroy：替换 deltaResourceStore → `safeCloseAll(zipFiles)` 关闭旧 zip → T2 的读抛 `zip file closed`。refresh 的注释修复只保证「新读者不会拿到将关闭的 store」，未保护**已拿到旧 store 资源的在读者**——与 C2-002（ResourceCacheEntry 无锁读 vs 锁内销毁）同族：销毁未等待在读者。
- **现状**: volatile 快照发布设计正确且有注释；destroy 顺序与 refresh 相同（先替换后关闭），但读者窗口的本质是「无宽限期销毁」，两处同窗。
- **风险**: dev 热刷新/停机期偶发请求失败（zip closed / NPE），低频难复现；无死锁。
- **建议**: destroy 采用 C2-002 建议的「标记失效 + 延迟关闭」：旧 zipFiles 移交后台清除周期（或宽限期后关闭）；refresh 的 `safeCloseAll(oldZipFiles)` 同理。至少在 `IVirtualFileSystem.refresh/destroy` Javadoc 声明「调用方需保证无在读者」。
- **失效面枚举**（F3：VFS「锁内重活 + 无宽限期销毁」站点）:
  1. `DefaultVirtualFileSystem.refresh`（L70-77）——锁内配置 I/O + zip 构建，本案。
  2. `DefaultVirtualFileSystem.destroy`（L87-94）——读者窗口，本案（C2-002 家族同型注记）。
  3. `buildResourceStore`（L79-83）——被构造器与 refresh 共用；构造期无并发，不立案。
  4. `updateInMemoryLayer/getInMemoryLayer`（L60-67）——转发 volatile 字段，无锁，正确（对照）。
  5. `NioFileWatchService.watch`（L156-192，synchronized + `Files.walkFileTree` L161）——锁内全树遍历 I/O，注册低频、无反向边无环，随本案一并裁定（合并 P3，不独立立案）。
  6. `NioFileWatchService.doStop`（L196-208）——监视器内 cancel/close，与 watch 互斥正确，不立案。
  7. `ResourceCacheEntry.clear`（C2-002 主站点）——同族主站点，不重复。
- **信心水平**: 确定（窗口与锁内 I/O 事实）；危害低频
- **误报排除**: 不是「refresh 慢即误报」——立案点是 (a) 监视器内文件 I/O+类加载的纪律面（表 R2-1 #11）与 (b) 注释自证但未修全的读者窗口；后者与 C2-002 同判据。
- **复核状态**: 复核维持 P3。refresh synchronized 锁内 VfsConfigLoader.loadDefault + safeNewInstance + builder.build（L70-83）、注释自证读者窗口、destroy 同窗未修（L87-94）、NioFileWatchService.watch 锁内 walkFileTree（L156-192）逐点亲核；「volatile 快照发布正确、缺宽限期销毁」判定正确。现状：新发现

### [nop-kernel-concurrency-C2-022] DefaultTaskExecutionQueue.addTaskIfAbsent 在 CHM bin 锁内执行 executor.execute 提交——CallerRuns/同步拒绝策略下任务体在 bin 锁内执行，递归任务图可触发同线程递归 compute（①）

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/execution/DefaultTaskExecutionQueue.java:192-201`（addTaskIfAbsent）、`L203-247`（queueTask：`executor.execute` 在 mapping 内）
- **证据片段**:
  ```java
  State taskState = states.computeIfAbsent(taskRef, k -> {
      State state = new State(taskRef, taskName, source, description);
      queueTask(state, task);        // mapping 内提交任务
      return state;
  });
  void queueTask(State state, IExecution<?> task) {
      totalTasks.incrementAndGet();
      executor.execute(() -> { ... task.executeAsync(state) ... });   // 若拒绝策略为 CallerRuns：整个任务体在 bin 锁内跑
  ```
- **严重程度**: P3（默认执行器为 `GlobalExecutors.globalWorker()`（doStart L152-161），标准 TPE 语义下 execute 非阻塞、拒绝即抛——递归面需调用方注入 CallerRuns/同步策略的执行器才可达；putIfAbsent 语义下同键并发只有一线程进 mapping，无重复提交）
- **并发场景**: 任务编排场景并发提交任务（TaskExecutionGraph 层级展开时父任务完成回调再提交子任务）。
- **触发时序**: （默认面）T1/T2 并发 `addTaskIfAbsent("k")` → putIfAbsent 去重正确（同键仅一线程进 mapping）；`executor.execute` 非阻塞，bin 持有时间极短——**当前默认配置无缺陷**。（注入面）调用方经 `setExecutor` 注入 CallerRunsPolicy 池或 SyncThreadPoolExecutor：T1 进 mapping → 任务体同线程执行 → `task.executeAsync` 若同步再调 `addTaskIfAbsent("k", ...)`（子任务同 key，如自引用周期任务）→ 同线程对同键递归 compute → CHM 抛 `IllegalStateException: Recursive update`（或对非 CHM map 死锁）；不同 key 则嵌套拉长 bin 持有时间。
- **现状**: mapping 内做的是「创建 State + 提交」两步；提交语义与拒绝策略耦合未被声明（Javadoc 无「mapping 内禁止阻塞/重入」契约）。
- **风险**: 设施为公共 API（ITaskExecutionQueue），注入自定义执行器是接口明示能力；递归任务图 + CallerRuns 组合下偶发 IllegalStateException，任务静默失败。
- **建议**: mapping 内只做「创建 State + putIfAbsent」，提交移到 computeIfAbsent 之外（胜出者提交——需局部标志判定胜出）；或 Javadoc 显式契约「executor 不得使用调用者运行策略」。
- **失效面枚举**（F3：队列/图提交面 compute 站点）:
  1. `DefaultTaskExecutionQueue.addTaskIfAbsent`（L194）——主站点，本案。
  2. `DefaultTaskExecutionQueue.replaceTask`（L249-261）——先 queueTask 后 put（无 compute），无递归面，正确（对照）。
  3. `Dag.addNode`（Dag.java:202-222）——mapping 仅 new DagNode，轻量正确（对照；其 analyze 由 TaskExecutionGraph.analyze synchronized 保护，analyze 为内存 DAG 计算，轻量，不立案——表 R2-1 注记）。
  4. `GlobalExecutors.computeIfAbsent`（表 R2-1 #13）——一次性建池，不立案。
  5. `BlockingSourceConsumeTask`（CL-006）——消费语义面，不重复。
- **信心水平**: 很可能（默认面无缺陷已逐点排除；注入面为接口明示能力与 mapping 内提交的组合风险）
- **误报排除**: 不是「compute 内做轻活即误报」——queueTask 含原子计数与 execute 调用，默认态下按「极短持锁」校准判 P3；递归面为定向检查项（compute 内重入）而非想象构造。
- **复核状态**: 复核维持 P3。addTaskIfAbsent→states.computeIfAbsent→queueTask→executor.execute（L192-247）亲核；默认 executor=GlobalExecutors.globalWorker()（doStart L140-145 亲核）标准 TPE 语义下「默认面无缺陷」排除正确；setExecutor 注入面为接口明示能力（L132-133 亲核），CallerRuns+同键递归→CHM IllegalStateException 成立。现状：新发现

---

## 维度集外线索（不立案展开）

### [nop-kernel-concurrency-CL-019]（维度集外线索）ExecutionContextImpl.onBeforeComplete 丢失注册竞态 + ServiceContextImpl 不对称锁——context 族锁纪律缺口（C1/C2 边界，建议归属 C1 复核）

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/context/ExecutionContextImpl.java:84-93`（onBeforeComplete）、`L109-128`（complete：先检查 done → 锁外 fireBeforeComplete（快照+置空 beforeCompletes）→ 锁内置 done）、`nop-kernel/nop-core/src/main/java/io/nop/core/context/ServiceContextImpl.java:56-147`（18 个 synchronized getter/setter）与 `L121-132`（newChildContext 无锁读字段）
- **C8/C1 形态**: (a) T1 complete() 首检 done=false 后进入 fireBeforeComplete（已快照并置空 beforeCompletes、尚未置 done=true）→ T2 调 onBeforeComplete：isDone()==false 通过 → 新建列表并添加回调 → 该回调既不会被本次 fireBeforeComplete 执行（快照已取走），也不会被后续执行（complete 随后置 done，后续 onBeforeComplete 全部 IllegalStateException）——**回调静默丢失**。(b) ServiceContextImpl 写路径 synchronized 而 `newChildContext` 读 `cache/userContext/context` 等字段完全无锁——写可见性无 HB 边（同族不对称，与 CL/④ 判定一致）。
- **为什么不在 C2 立案**: ExecutionContextImpl/ServiceContextImpl 为请求域对象，既不属于 A 面 nop-commons 并发设施，也不属于 B 面全局缓存/注册表单例（审计对象边界外）；且缺陷主面是状态机窗口（F8）与可见性（C1 镜头），非锁序/await 正确性。

### [nop-kernel-concurrency-CL-020]（维度集外线索）IEvalScope.computeIfAbsent 默认实现 synchronized(this) 承载任意 builder——跨线程共享 scope 时的跨 scope 监视器嵌套环潜伏面

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/lang/eval/IEvalScope.java:45-54`、委托点 `IExecutionContext.java:71-73`
- **C8 形态**: 默认实现以 scope 自身为监视器、在锁内执行任意 `builder.apply` 并写回。scope 经 parentScope 链关联；若 builder 内访问/计算父 scope 的 computeIfAbsent，形成 child→parent 监视器链，两个线程反序进入即可闭合环。当前 nop-kernel 内无重量级内置 builder（唯一调用为接口委托转发）， scopes 按执行域线程封闭使用（跨线程共享属 C1/C3 面）——故仅登记纪律缺口：公共接口默认实现「锁内任意回调」且无契约注释（对照 ICache.computeIfAbsent 的 Javadoc 明确 mapping 锁外契约）。
- **为什么不在 C2 立案**: 嵌套环的两个前提（跨线程共享 scope、builder 再入其他 scope）均为设施外使用形态；建议修复为文档契约补注（一行 Javadoc）。

---

## 本轮验证后维持「无问题」裁定的第 1 轮站点（复核留档）

1. **HighWatermarkSemaphore await 面**（R2-2 #1）：while 谓词重查、awaitNanos 剩余时间、signal 条件与等待谓词边界对齐——维持无立案。
2. **LocalResourceLockManager latch await 循环**（R2-2 #2）：CDL 对虚假唤醒稳健、do-while 重抢、旧 latch 唤醒语义正确——维持（C2-007/C2-008 已覆盖其各自面）。
3. **TaskQueue vs 池锁**（R2-3 3.1）：全部 parent 调用为顺序执行，唯一嵌套为 JDK 内部单向 mainLock→putLock——维持无环。
4. **GlobalExecutors vs 池锁**（R2-3 3.1）：单向一次性 bin→mainLock→takeLock，他线程同键回退经 CHM 保证无递归——维持。
5. **ClassModel.getBeanModel**（表 R2-1 #16）：构建器无跨类递归调用（grep 实证），无监视器环——维持不立案。
6. **CoreInitialization/XLangExprParser/GlobalStatManager/LocalCacheProvider/DateHelper 族**（表 R2-1 #17-19、#14-15）：启动期/一次性/有界 CPU——维持不立案。

## 第 2 轮发现清单汇总

| ID | 严重程度 | 文件 | 一句话摘要 |
|---|---|---|---|
| C2-016 | P2 | ContextTaskQueue.java / BaseContext.java | syncGet drain→await 过渡丢失唤醒窗口：enqueue 谓词 syncing>0 与退出窗口错位 + flush 兜底静默放弃（flush 已修 endProcessIfIdle 而 syncGet 未修） |
| C2-017 | P2 | XplLibTagCompiler.java / LazyCompiledExecutableFunction.java | LazyFn 监视器内完整 XPL 编译+import 求值+宏执行，且在模型 entry 监视器链内触发——锁序图新增 E4/E5 两条可闭合环（C2-001 家族扩展） |
| C2-018 | P3 | XplLibTagCompiler.java | CompiledTag.deps 非 volatile：监视器内写、isChanged 热路径无锁读，同文件 compiledFn/compileException 已 volatile 自证遗漏 |
| C2-019 | P2 | XLangSemantics.java | CLASS_MODELS.computeIfAbsent bin 锁内类加载+ClassModel 全量构建（生成代码 new 首触热路径，C2-003 同族） |
| C2-020 | P3 | XDslParseHelper.java / EnumDictLoader.java / TemplateFileGenerator.java | bin 锁内 XDef 解析/枚举字典构建/模板文件头 I/O 族（C2-012 同族扩展，低频面） |
| C2-021 | P3 | DefaultVirtualFileSystem.java / NioFileWatchService.java | VFS refresh 监视器内配置 I/O+zip 构建；destroy 与无锁读者 zip-closed 窗口（refresh 注释自证修一半）；watch 锁内 walkFileTree |
| C2-022 | P3 | DefaultTaskExecutionQueue.java | addTaskIfAbsent 的 CHM bin 锁内 executor.execute 提交——CallerRuns 注入下递归任务图可触发递归 compute（默认面无缺陷） |

CL 线索：CL-019（ExecutionContextImpl 回调丢失注册 + ServiceContextImpl 不对称锁，建议 C1 复核）、CL-020（IEvalScope.computeIfAbsent 锁内任意 builder 契约缺口）。

## 统计与终止信号判定

- 第 2 轮新增发现：**7 条**（P2 × 3：C2-016/017/019；P3 × 4：C2-018/020/021/022；P0/P1 × 0）
- 新增维度集外线索：**2 条**（CL-019、CL-020）
- 立案排除并留档：HighWatermarkSemaphore、LRLM await 循环、TaskQueue/池锁配对、GlobalExecutors/池锁配对、ClassModel.getBeanModel、CoreInitialization、XLangExprParser、GlobalStatManager、LocalCacheProvider、DateHelper/RegexHelper 族（各判定理由见上表）
- **锁序图终版**：可闭合环 = E1（C2-001）/ E2（C2-003）/ E3（C2-004）+ **E4/E5（本轮新增，C2-017）**；其余全部嵌套边已逐一验证为单向或无共享路径（R2-3）。
- **终止信号判定**：本轮无新增 P0/P1。第 1+2 轮累计 C2 维度 22 条发现中，P1 仅 C2-001 一条且其触发前提（循环模型依赖）已明示；本轮三条 P2 均为窄窗口（C2-016）/同族扩展（C2-017，其修复与 C2-001 合并设计）/首触延迟面（C2-019），继续第 3 轮的边际收益低——C2 维度的锁站点已两轮全覆盖（A 面 84 处 synchronized + B 面扩展至 xlang 编译链与 api-core await 面），失效面枚举（F3）各条均闭环。**判定：C2 维度审计终止，不再建议第 3 轮**；唯一附带条件为 C2-001 与 C2-017 的修复必须作为同一变更集设计（三层监视器链的不变式统一为「锁内只做去重+占位」），避免只修 entry 层后 E4 残余闭合。
- 盲区自评：(a) `VirtualResourceDependencySet` 内部集合的可见性细节未逐行核验（C2-018 判级基于 JMM 通则）；(b) nop-kernel 之外模块（nop-task/nop-workflow 等）对 `IResourceLockManager`/`ITaskExecutionQueue`/CallerRuns 注入的实际使用形态未审计（超出本单元边界）；(c) `ExprEvalHelper.runMacroExpression` 的内部执行栈未逐帧展开（宏执行可再入的站点集合以「任意模型/标签」上界处理）。
