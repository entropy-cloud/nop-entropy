# C2 锁与同步 —— nop-kernel 并发·异步·资源专项审计（第 1 轮初审，盲审）

## 基本信息

- **审计单元**: nop-kernel 平台并发设施自身（A 面 + B 面）
- **仓库路径**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **维度**: C2 锁与同步——锁的正确性与纪律：锁序（多锁获取顺序与死锁环）、锁粒度与锁内 I/O、Condition await 丢失唤醒/虚假唤醒处理、synchronized 范围（过宽/过窄）、CAS/原子复合操作、读写锁使用、锁的可重入假设
- **审计日期**: 2026-10-17（任务批次 2026-10-16-2316）
- **轮次**: 第 1 轮初审（盲审）
- **判级口径**: 平台设施被全平台并发调用——锁缺陷按「多任务多线程默认并发调用」口径判级；「常见条件」门槛极低。

## 盲审纪律声明

本轮为盲审轮次。审计过程中**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下的任何历史文件；仅阅读了允许的方法论文件（`ai-dev/skills/concurrency-resource-audit-prompt.md`、`ai-dev/skills/deep-audit-prompts.md`）、基线文件（`_tmp/cra-baseline-nop-kernel-concurrency.txt`）与当前 live code。所有结论仅基于当前 live code。

## 检查范围说明

### A 面（nop-kernel/nop-commons/src/main/java/io/nop/commons/{concurrent,cache,pool}，85 files / 7,550 行）

全量阅读了 concurrent 包全部子包（executor/lock/impl/semaphore/ratelimit/batch/thread）、cache 包全部实现类、pool 包（仅接口 + 配置类，无锁面实现，不构成审计对象）。锁站点清单（对照基线 A C2 节 45 处，含逐一判定）：

| 锁站点 | 文件 | 判定 |
|---|---|---|
| TaskQueue.setParent / forcedRemainingCapacity | concurrent/executor/TaskQueue.java:51,37,110-121 | C2-012 观察注记（不立案，见条目外说明）；offer/poll/take 等待-通知面复用 LinkedBlockingQueue 双锁原语，take→poll(timeout) 降级路径与 TPE 语义兼容，无丢失唤醒 |
| TaskQueue.offer 的 force=false 触发建线程 | TaskQueue.java:69-84 + StandardThreadPoolExecutor.java:152-175 | 与 Tomcat 同源模式，TPE execute/addWorker 失败→force 兜底闭环，无死锁环 |
| LocalResourceLockManager 锁表（CHM + CountDownLatch 双用监视器） | lock/impl/LocalResourceLockManager.java:123,169,232,258 | **C2-008 立案**（tryLockWithLease 绕过 latch 监视器）；checkTimeout/tryResetLease/isHoldingLock 三处纪律正确 |
| CycleSynchronizer synchronized(this) 持锁回调 | concurrent/CycleSynchronizer.java:48-53,77-91 | **C2-004 立案** |
| OverflowBlockingQueue 包装器锁传递 | concurrent/impl/OverflowBlockingQueue.java:115-134 | **C2-011 立案（P3）**（DROP_ELDEST offer/poll 两段内锁非原子） |
| DedupBlockingQueue 复合状态（map+queue）无共同锁 | concurrent/impl/DedupBlockingQueue.java:40-54,122-126 | **C2-010 立案**（send 异常路径墓碑） |
| ICache default putIfAbsent synchronized(this) | cache/ICache.java:85-93 | mappingFunction 在锁外执行（L50），符合注释契约，无立案 |
| LocalCache→Caffeine 委托（putIfAbsent asMap / computeIfAbsent→cache.get(key,fn)） | cache/LocalCache.java:219-221,234-237 | C2-012 立案（I/O 在 bin 锁内，经 ResourceContentCache 使用面） |
| MapCache.computeIfAbsent→map.computeIfAbsent | cache/MapCache.java:86-87 | 仅 ConcurrentMap 直通，无立案（使用方传入含重逻辑 mapping 时风险由使用方承担，本维度不展开） |
| GlobalExecutors.computeIfAbsent（3 处，锁内 init 建线程池） | executor/GlobalExecutors.java:39,72,86 | 一次性初始化，持 bin 锁建池可接受，无立案 |
| HighWatermarkSemaphore ReentrantLock+Condition | semaphore/HighWatermarkSemaphore.java:46-99 | 无丢失唤醒：signal 条件（向下穿越 lowWatermark）与等待条件（usedPermits≥low 或 over 标志）边界对齐（落在 lowWatermark 恰好值上时等待者本就不可 Proceed）；while 循环防虚假唤醒；awaitNanos 带超时。无立案 |
| LimitedExecutor Semaphore | executor/LimitedExecutor.java:22-42 | acquire/finally-release 配对正确（含 execute 抛出路径），无立案 |
| RateLimitExecutorImpl promiseMap.compute / debounce | executor/RateLimitExecutorImpl.java:29-72 | **C2-005 立案** |
| SequentialTaskExecutor CAS 状态机 | executor/SequentialTaskExecutor.java:96-182 | CAS/volatile hasTask 协议推演无丢失唤醒（offer 可见→hasTask=true→CAS 调度闭环）；**CL-004 登记**（execute 忽略 offer 返回值） |
| BindScheduledExecutor | executor/BindScheduledExecutor.java:65-116 | scheduleWithFixedDelay 重排链的 timerHandle 晚初始化由 future.isDone 兜底收敛，无死锁；**CL-005 登记**（scheduleAtFixedRate 拒绝即静默停摆） |
| SyncThreadPoolExecutor | executor/SyncThreadPoolExecutor.java | 无 synchronized（与反虚构预注一致，不预设）；委托 ContinuationExecutor 线程封闭 trampoline，无锁面问题 |
| ExecutorHelper.scheduleWithRandomDelay / submit / throttleExecute | executor/ExecutorHelper.java:210-260 | 见条目外观察（C3 形态）；throttleExecute permit 配对正确 |
| RoundRobinSupplier resize synchronized(this) | concurrent/RoundRobinSupplier.java:47-87 | resize 竞态已被 C1 轮立案，不重复；本轮核对：close()（L96-103）不参与 resize 锁纪律，但 safeCloseObject 幂等 + volatile 读快照，后果为可能双重 close（幂等），不立案 |
| DefaultThreadPoolExecutor / DefaultScheduledExecutor / GlobalExecutors 生命周期 | executor/DefaultScheduledExecutor.java:88-141 | **C2-006 立案**（checkEnabled-then-use TOCTOU） |
| ErrorMessageManager synchronized(list) 纪律 | core/exceptions/ErrorMessageManager.java:81-98 | 正面样例：COW list 的 check-set-add 在 synchronized(list) 内原子化，读路径快照迭代，无立案 |
| ReflectionManager tempLoaders + ClassModelLoader.load() synchronized | core/reflect/ReflectionManager.java:193-213,336-350 | 正面样例：per-class 构建串行化 + 双重检查正确（load 内重查 introspectCache），无立案 |
| EvalBackendRegistry synchronized register/unregister | xlang/backend/EvalBackendRegistry.java:55-92 | 注册期写锁 + 运行期无锁 CHM 读（写时无复制但注册低频、读为 CHM 弱一致 get），无立案 |
| StdDomainRegistry / EvalGlobalRegistry / ScriptCompilerRegistry / XPathOperatorRegistry / ObjPropMapperRegistry / ObjPropXmlMapperRegistry | 各注册表类 | static final 单例（类初始化 HB 安全发布）+ CHM 注册表，注册期写/运行期读，无立案 |
| XLangSemantics.OBJ_FUNCTION_HANDLES.computeIfAbsent | xlang/exec/XLangSemantics.java:155,231 | mapping 为轻量 new ObjFunctionHandle，无立案 |
| JsonWhitelist CHM-backed set | core/lang/json/JsonWhitelist.java:24-25,51-58 | 并发面无问题（CHM 弱一致迭代）；发现 add() 的 substring 逻辑缺陷属功能/安全形态，非 C2，见「条目外观察」 |
| ContextualizedRegistry / GlobalCacheRegistry / LocalAtomicLong / DefaultRateLimiter / CodeLangMap / thread 包 | — | CodeLangMap 为普通 HashMap 但全仓无 addMapping 外部调用方（构造后事实不可变），P3 级以下不立案；其余无锁面问题 |
| AbstractBatchProcessService / BlockingSourceConsumeTask | concurrent/batch/ | **CL-006 登记** |

### B 面（nop-core / nop-xlang 全局模型缓存与注册表单例，按「模型/元数据缓存」语义取基线子集）

| 锁站点 | 文件 | 判定 |
|---|---|---|
| ResourceComponentManager（volatile 单例、CHM 注册表、modelCaches.computeIfAbsent、transformed.computeIfAbsent） | core/resource/component/ResourceComponentManager.java | **C2-003 立案**（L343）；makeModelCache（L430-441）持 bin 锁建 cache 为一次性操作，无立案；注册 check-then-put 并入 **C2-013** |
| ResourceLoadingCache / ResourceCacheEntry（per-entry 监视器 + volatile 字段） | core/resource/cache/ResourceLoadingCache.java、ResourceCacheEntry.java | **C2-001 立案**（持 entry 锁做模型加载 + 递归依赖加载死锁环）；**C2-002 立案**（无锁读 vs 锁内销毁）；同 key 并发加载经 Caffeine per-key 去重创建空 entry + entry 监视器串行真正加载，去重设计本身正确 |
| TenantAwareResourceLoadingCache / ResourceTenantManager | core/resource/tenant/ | 租户级委托转发干净；**C2-014 立案（P3）**（isSupportTenant 无同步惰性初始化）；runInitializeTenantTask ThreadLocal finally set(false) 未 remove（池化线程残留，轻微，注记于条目） |
| DictProvider / I18nMessageManager / ErrorMessageManager / ModuleManager | core/dict、core/i18n、core/exceptions、core/module | I18nMessageManager 经 ResourceLoadingCache 加载 i18n yaml（与 C2-001 同族加载面，随 C2-001 覆盖）；ErrorMessageManager 无立案；**C2-009 立案**（registerInstance 非 volatile 家族）；I18nMessageManager.enabledLocales 非 volatile（启动期写后只读，注记） |
| ReflectionManager / ModuleManager 注册期写 | — | **C2-013 立案**（P3，注册 check-then-put 非原子） |
| ReactiveObjectMap | core/resource/cache/ReactiveObjectMap.java | 异常路径留下 exceptionally-completed future 条目不清理 → 见 C2-002 失效面枚举同族（失败条目驻留），单独判 P2 并入 C2-002 家族注记？——独立立案为 **C2-015**（条目缺失清理，复用同一失效面枚举） |
| ResourceContentCache / ResourceTimestampCache（后者已注释停用） | core/resource/cache/ | **C2-012 立案（P3）** |
| XDslParseHelper / XPathHelper / SimpleExprHelper / CodeLangMap / BeanTool / VirtualFileSystem / VfsConfigLoader / ThemeNamespaceHandler / GlobalStatManager | — | 无自有锁面（无状态工具或 CHM 直通），无立案 |

### 锁序图（文本）

A/B 面实际存在的多锁嵌套边（持锁 A → 获取锁 B）：

```
E1: ResourceCacheEntry(pathA).monitor  --依赖加载-->  ResourceComponentManager.modelCaches bin  --require-->  ResourceCacheEntry(pathB).monitor
    （仅此一条真实嵌套链；C2-001 的死锁环 = pathA→pathB 与 pathB→pathA 两条边对拼）
E2: modelCaches bin(transformed CHM) --transform--> 任意模型加载链（C2-003，可回环到自身 bin）
E3: CycleSynchronizer.this --callback--> 任意用户代码（含另一个 CycleSynchronizer.this / cyclePromise.get）（C2-004）
其余：LocalResourceLockManager 仅单 latch 嵌套 CHM bin（单向，无环）；ErrorMessageManager synchronized(list) 无嵌套；
     ReflectionManager ClassModelLoader.monitor 无嵌套；各注册表 synchronized 仅注册期单锁。
结论：全模块唯一可闭合的锁序环在 E1/E2/E3 三处，均已立案。
```

---

## 第 1 轮（初审）发现

### [nop-kernel-concurrency-C2-001] ResourceCacheEntry 持 entry 监视器执行模型加载（解析+编译 I/O），加载器递归经 ResourceComponentManager 加载依赖模型，循环依赖模型 + 两线程相反顺序可达成无超时死锁

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceCacheEntry.java:176-225`（getObject 持锁调用 loadObject）、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/ResourceComponentManager.java:99-129`（ModelLoader→loadObjectFromPath）、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/functions/GlobalFunctions.java:439`（脚本可触发 requireComponentModel）
- **证据片段**:
  ```java
  // ResourceCacheEntry.getObject
  if (value == null || checkChanged && isChanged()) {
      synchronized (this) {                      // 持 entry 监视器
          ...
          value = loadObject(loader);            // 锁内执行模型解析+编译
  // loadObject:
  T result = ResourceComponentManager.instance().collectDependsTo(deps,
          () -> loader.loadObjectFromPath(path)); // loader 内部可再入 loadComponentModel → 其他 entry 的监视器
  ```
- **严重程度**: P1
- **并发场景**: HTTP/graphql worker 线程并发请求存在依赖关系的多个模型（如 xlib A import xlib B）；全部经 `ResourceComponentManager.loadComponentModel → ResourceLoadingCache.require → ResourceCacheEntry.getObject` 路径。`XplLibHelper.java:66` 证实 xpl 标签库加载会经同一入口递归加载其他标签库；`GlobalFunctions.java:439` 的 `std:requireComponentModel` 使**用户脚本内容**也能在任意模型的加载过程中触发任意模型加载。
- **触发时序**: T1 请求加载 `/a/comp.xlib`：获得 entry E1 监视器（synchronized(this)）→ 在 E1 锁内解析，遇到 `import /b/base.xlib` → 经 ResourceComponentManager 请求 `/b/base.xlib` → 尝试获取 entry E2 监视器并阻塞。T2 同时请求 `/b/base.xlib`：已持有 E2 监视器 → 解析中遇到 `import /a/comp.xlib`（循环依赖的模型文件）→ 尝试获取 E1 → 阻塞。E1/E2 均为普通 synchronized 监视器，**无超时、无租约**，两线程永久互等；后续所有请求 E1/E2 对应模型路径的线程全部级联挂死。模型循环依赖本是应当以解析错误报告的用户配置错误，实际失效形态却是线程池级永久死锁。
- **现状**: 模型加载采用 per-entry 监视器串行化（同 key 去重正确），但监视器覆盖范围包含完整的依赖递归加载链，且 synchronized 无超时保护。
- **风险**: 默认 dev 模式 `checkChanged=true` 下依赖变化即重载，加载链常见；一旦出现循环模型依赖（手写 xlib/xmeta 互相 import、AI 生成模型的常见错误），不是干净报错而是全平台模型加载通道永久挂死（F5 挂死且对健康检测不可见——线程栈停在 synchronized 上无日志）。
- **建议**: 缩小 entry 监视器覆盖面：在锁内只做「同 key 去重 + 占位」，把 `loader.loadObjectFromPath` 移出监视器（先以 per-key Future/占位符去重，加载在锁外执行，完成后 CAS 写回 object 字段——字段已为 volatile，具备改造条件）；或至少对依赖加载引入可中断/可超时的获取路径（tryLock + 超时转解析错误）。修复时可参照本仓库 ReflectionManager.getFromCache 的 tempLoaders 模式（per-class loader 监视器只覆盖构建，introspectCache.put 在锁外）。
- **失效面枚举**（F3：同族「持锁执行可递归加载」站点全量清单）:
  1. `ResourceCacheEntry.getObject`（L179-202）——主站点，本案。
  2. `ResourceCacheEntry.reloadObject`（L227-240，synchronized）——checkRefresh/强制刷新路径，同一加载链，同案覆盖。
  3. `ResourceCacheEntry.getCacheEntryState`（L90-92，synchronized）——仅快照不加载，无风险。
  4. `ResourceCacheEntry.clear`（L111-124，synchronized）——销毁不加载，无递归风险（销毁面归 C2-002）。
  5. `I18nMessageManager.localeToMessages`（i18n-cache，经同一 ResourceLoadingCache）——loadI18nMessages 不递归加载模型，无死锁环，仅共享「锁内 I/O」形态。
  6. `TenantAwareResourceLoadingCache`——纯转发到上述 entry，随本案修复自动覆盖。
  7. `ReactiveObjectMap.get`——无锁但同样支持递归 fn，无监视器环（其自身缺陷见 C2-015）。
- **信心水平**: 确定（嵌套监视器链与递归入口均有代码实证；死锁环触发以「循环模型依赖 + 并发」为前提）
- **误报排除**: 不是「原语使用即误报」——这里的问题不是 synchronized 本身，而是监视器临界区内执行任意可再入的用户模型加载（锁扩展），且 JDK 各注册表（ReflectionManager）已展示了同场景的正确形态，说明这是可实现且未实现的纪律缺口。低频启动路径粗锁的误报校准不适用：模型加载是全平台每请求热路径。
- **复核状态**: 复核维持 P1。证据全链亲核：(a) getObject:179 持监视器加载属实（synchronized(this) L179、loadObject L186）；(b) 跨 entry 嵌套三路径全部实证——①SchemaLoader.loadXDefinition 在加载期经 RCM 加载 schema xdef（任意 DSL 模型加载必经）；②x:config 的 c:import 经 applyCompileConfig→ImportTagCompiler.parseTag→XplCompiler.loadLib:446→XplLibHelper.loadLib:66→RCM.loadComponentModel（无环守卫）；③pre/post-parse 钩子+用户脚本（GlobalFunctions.loadDslModel）；(c) 循环模型依赖真实可配（x:config import/脚本均为用户内容，解析层无环检测）——补强事实：单线程循环经可重入监视器无限重载直至 StackOverflowError（Error 逃出 getObject 的 catch(Exception)），双线程反序即永久死锁，两条件（循环依赖+并发）非罕见条件合取；(d) 命名勘误：GlobalFunctions.java:439 的全局函数名为 `loadDslModel`（内部调 requireComponentModel），初审判词「std:requireComponentModel」为名实不符，证据链不受影响。现状：新发现。三节点关系见维度复核结论

### [nop-kernel-concurrency-C2-002] ResourceCacheEntry 快路径无锁读与 clear()/remove() 锁内销毁并发 → 读者使用已销毁模型（use-after-destroy 窗口）

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceCacheEntry.java:111-124`（clear 锁内销毁）、`176-207`（getObject 快路径不持锁返回）、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceLoadingCache.java:163-169`（remove→entry.clear）
- **证据片段**:
  ```java
  // getObject：checkChanged=false 或未变化时完全不加锁
  public T getObject(boolean checkChanged, IResourceObjectLoader<T> loader) {
      Object value = object;                 // 无锁读
      if (value == null || checkChanged && isChanged()) {
          synchronized (this) { ... }
      }
      ...
      return normalizeObject(value);         // 直接交出引用
  // clear：另一个线程锁内销毁同一对象
  public void clear() {
      synchronized (this) {
          Object oldObj = this.object;
          this.object = null;
          if (oldObj != null && oldObj != Null.NULL) {
              if (listener != null) { listener.onDestroyed((T) oldObj); }
              DestroyHelper.safeDestroy(oldObj);   // 读者仍持有 oldObj
  ```
- **严重程度**: P2
- **并发场景**: 请求线程（getObject 快路径拿走模型引用后继续执行）与失效线程（dev 热重载的 `ResourceVersionedModelStore.java:87 removeCachedModel`、`CoreInitialization.java:200/245` 的 clearAllCache、租户清理 `GlobalCacheRegistry.clearForTenant`）并发。entry 监视器只保护销毁方，不保护已交出的引用。
- **触发时序**: T1 请求线程执行 getObject：读到 object=v1，checkChanged 判定未变化（或为 false）→ 不进 synchronized → 返回 v1，开始遍历/执行该模型。T2 失效线程（文件变更回调）执行 `ResourceLoadingCache.remove(path)` → `entry.clear()` → 获得监视器 → `DestroyHelper.safeDestroy(v1)` + listener.onDestroyed(v1)。T1 此后继续使用 v1。当模型实现 IDestroyable 且 destroy 释放真实资源（关闭编译产物/句柄/子模型）时，T1 使用已释放资源；模型为纯只读值对象时 destroy 为 no-op，后果退化为可见性层面的脏读。
- **现状**: 读路径为性能考虑故意无锁（设计权衡），但「销毁必须晚于所有读者」这一前提没有任何机制保障（无引用计数、无宽限期、无延迟销毁队列）。
- **风险**: dev 热重载/租户登出清理是常态操作；对承载真实资源的模型类型（编译缓存、打开的文件句柄类模型）造成偶发性使用已释放资源，故障形态为偶发 NPE/已关闭异常，难复现。
- **建议**: 采用「标记失效 + 延迟销毁」：clear 只做 `object=null` 与逻辑失效（依赖检查返回 true），实体销毁延迟到无读者窗口（如经由 `GlobalCacheRegistry` 的清除周期统一执行），或读者侧返回前经 listener 计数登记。至少应在 `ResourceLoadingCache.remove` 的 Javadoc 声明「调用方需保证无并发读者」并在平台内收敛失效入口。
- **失效面枚举**（F3：同族「锁内销毁 vs 无锁读者」站点）:
  1. `ResourceCacheEntry.clear`（L111-124）——主站点。
  2. `ResourceCacheEntry.reloadObject→destroyObject`（L236-238）——重载换新时销毁旧对象，与正在使用旧对象的读者并发，同案。
  3. `ResourceLoadingCache.remove`（L163-169）→ `entry.clear()`——失效入口。
  4. `ResourceLoadingCache.get`（L194-196）value==null 时 `cache.remove(path, entry)`——null 结果条目，销毁面为空，无实际风险。
  5. `ResourceLoadingCache.state_loadFrom`（L240-241）putIfAbsent 失败 `entry.clear()`——预热期并发，同族。
  6. `ResourceComponentManager.ComponentCacheEntry`（transformed CHM 随 entry 丢弃）——内含 model 的销毁依赖外层 ResourceCacheEntry.clear，随本案覆盖。
  7. `TenantAwareResourceLoadingCache.clear/remove`（L42-44,68-79）——转发至上述站点。
- **信心水平**: 确定（窗口客观存在）；实际危害取决于模型是否承载可销毁资源（很可能）
- **误报排除**: 不是「把读性能优化当问题」——问题不在无锁读本身，而在销毁语义与无锁读不兼容且无任何补偿机制；模型为纯值对象时本条自动降级为文档缺口，仍非原语误报。
- **复核状态**: 复核维持 P2。clear 锁内销毁（L111-124）+ getObject 快路径无锁交出引用（L176-207）亲核，窗口客观存在；失效入口（ResourceLoadingCache.remove L163-169、ResourceVersionedModelStore 热重载、CoreInitialization clearAllCache）确认为常态操作。现状：新发现

### [nop-kernel-concurrency-C2-003] ResourceComponentManager.loadComponentModel(transform) 在 ConcurrentHashMap bin 锁内执行 transformer.transform（编译级重计算），transformer 内再入同一 map 存在递归 compute 风险

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/ResourceComponentManager.java:334-344`
- **证据片段**:
  ```java
  IResourceLoadingCache<ComponentCacheEntry> cache = makeModelCache(modelType, resourcePath);
  ComponentCacheEntry entry = cache.require(resourcePath);
  if (StringHelper.isEmpty(transform) || modelType.equals(transform))
      return entry.model;
  return entry.transformed.computeIfAbsent(transform, k -> transformer.transform(entry.model));
  ```
- **严重程度**: P2
- **并发场景**: 多个请求线程并发请求同一 `(resourcePath, transform)` 组合（如同一 validator 路径的同一变换视图）。transformer 为注册的 `IComponentTransformer`（模型→模型编译/变换，重量级）。
- **触发时序**: （锁内重计算形态）T1 进入 computeIfAbsent → 持 transformed CHM 的 bin 锁 → 执行 transformer.transform（可能秒级）；T2 请求**哈希到同一 bin 的任意 (entry, transform) 组合**（含完全无关模型）→ 在 CHM bin 上阻塞直至 T1 的变换完成 → 无关请求被无界串行化。（递归形态）transformer 实现内部对**同一 resourcePath 请求同一 transform**（如变换逻辑需要加载自身依赖视图、循环别名）→ 同线程再入同一 CHM key 的 computeIfAbsent → 递归 update（CHM 检测到时抛 IllegalStateException，未检测到时结构化风险）。
- **现状**: transformed 结果缓存用 CHM.computeIfAbsent 去重，mapping 函数为重量级变换，二者组合违反「compute 内不做重逻辑」纪律。
- **风险**: 平台设施锁内编译 = 全平台该 bin 上所有变换缓存操作串行化点；负载上升时表现为无解释的尾延迟；递归变换场景直接异常/挂死（当前未发现生产 transformer 自环，标注理论风险成分）。
- **建议**: 改为「per-(entry,transform) Future 占位」模式：`transformed.computeIfAbsent(transform, k -> new CompletableFuture<>())` 持有占位（轻量），变换在锁外执行后 `complete`，失败 `completeExceptionally`；或复用 `FutureHelper.futureCall` + per-key 去重 map（LocalCache asyncCache 的 `computeIfAbsentAsync` 即平台现成设施）。
- **失效面枚举**（F3：B 面「compute 持锁做重逻辑」同族全量）:
  1. `ResourceComponentManager.loadComponentModel(transform)`（L343）——主站点。
  2. `ResourceComponentManager.makeModelCache`（L433-440）——compute 内建 ResourceLoadingCache + GlobalCacheRegistry.register，一次性、轻量（毫秒级），不立案。
  3. `LocalCache.computeIfAbsent → cache.get(key, fn)`——Caffeine bin 锁内 mapping，使用面为 ResourceContentCache（C2-012）与各调用方 mapping；去重收益与代价并存，随 C2-012 一并评估。
  4. `GlobalExecutors` 3 处 computeIfAbsent 建池——一次性，不立案。
  5. `ErrorMessageManager.subMappings.computeIfAbsent`（L81-98）——mapping 仅 new COW list，轻量，正确。
  6. `ReflectionManager.converterCache.computeIfAbsent`（L419-443）——converter 构建轻量（枚举扫描/类型构建），可接受，不立案。
  7. `XLangSemantics.OBJ_FUNCTION_HANDLES.computeIfAbsent`（L231）——new ObjFunctionHandle，轻量，正确。
  8. `RateLimitExecutorImpl.throttle` compute 内 schedule（L32-46）——调度入队轻量，不立案。
- **信心水平**: 很可能（锁内重计算确定；生产 transformer 自环未具体化到已知站点）
- **误报排除**: 不是「CHM 使用即误报」——问题在 mapping 函数的重量级内容与潜在自环，属审计 prompt C2 执行步骤 2 明列的定向检查项（computeIfAbsent 内持锁做重 I/O/递归）。
- **复核状态**: 复核维持 P2。L343 transformed CHM computeIfAbsent 内 transformer.transform 亲核（ComponentCacheEntry.transformed 为 CHM，L420）；同 bin 跨键串行化与同键递归 compute（IllegalStateException）两面成立。现状：新发现

### [nop-kernel-concurrency-C2-004] CycleSynchronizer 持 this 监视器执行任意回调：回调内阻塞等待本周期 promise 自死锁、跨实例回调锁序环、回调异常中断后续回调且 promise 以异常完成（状态撕裂）

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/CycleSynchronizer.java:48-53,74-101`
- **证据片段**:
  ```java
  public synchronized CompletableFuture<Boolean> waitCycleCompleted(Runnable onCompleted) {
      if (!processing) {
          if (onCompleted != null)
              onCompleted.run();          // ① 未开周期时：持 this 锁同步执行任意回调
          ...
  private CompletableFuture<Boolean> resolvePromise(boolean finished) {
      ...
      synchronized (this) {
          ...
          for (Runnable callback : callbacks) {
              callback.run();             // ② 全部回调在 this 监视器内执行
          }
      }
      if (promise != null) {
          promise.complete(finished);     // ③ complete 在锁外——回调若等待 promise 即自死锁
  ```
- **严重程度**: P2（缺陷在设施内可完整构造；当前全仓 grep 无生产调用方，未达 P1 的「默认并发调用」前提）
- **并发场景**: 设施为公共 API（nop-commons），任何采用「周期末回调」模式的模块（流/批处理的 cycle 收尾）都是目标使用面。
- **触发时序**: （自死锁）T1：`waitCycleCompleted(cb)` 注册 cb，cb 内部逻辑为「等待本周期结束通知」即 `cyclePromise.get()`（回调闭包可持有该 promise 引用）。T2：周期结束调用 `completeCycle()` → resolvePromise 持 this 监视器 → L87 执行 cb → cb 阻塞在 promise.get() → 而 complete（L94）必须等监视器释放 → **单线程自死锁，永久**。（跨实例环）T1 在 syncA 的回调内调用 syncB.waitCycleCompleted（获取 B 的监视器，持 A 等B）；T2 同时在 syncB.resolvePromise 的回调内调用 syncA.waitCycleCompleted（持 B 等A）→ 经典 AB-BA 锁序环。（状态撕裂）回调列表两个回调，第一个抛 RuntimeException → L96 捕获 → 第二个回调**静默丢弃**（不再执行）且所有等待者经 L98 收到 completeExceptionally——周期实际已完成但被上报为失败，`onCycleCompleted` 已置 null，回调永久丢失（F9）。
- **现状**: 回调在监视器内同步执行且无异常隔离（无 per-callback try/catch）。
- **风险**: 一旦有模块按注释语义使用该设施，上述三种失效形态任一即可复现；自死锁线程卡在 synchronized 上对健康检测不可见。
- **建议**: 回调快照后在监视器外执行（`callbacks` 局部变量已存在，把 for 循环移出 synchronized 块即可，注意与 complete 的先后序：先 complete 再跑回调可同时消除自死锁面）；每个回调独立 try/catch；Javadoc 明确「回调内禁止阻塞等待本周期 promise / 禁止再入其他 synchronizer」。
- **失效面枚举**（F3：同族「持锁回调」站点全量）:
  1. `CycleSynchronizer.waitCycleCompleted`（L49-53）——①面。
  2. `CycleSynchronizer.resolvePromise`（L84-90）——②③面，同案。
  3. `OverflowBlockingQueue.removeProcessor`（onRemoveItem）——在调用方线程执行但**不在锁内**（send/clear 均无锁），无此形态。
  4. `LocalCache removalListener`（L110-114）——Caffeine 回调，在 Caffeine 内部锁面执行，destroy 逻辑轻量（DestroyHelper），潜在同族但当前回调体无阻塞调用，注记不立案。
  5. `SequentialTaskExecutor.doTask→task.run`——队列消费线程内执行，无监视器，正确形态。
- **信心水平**: 确定（三类交错均可在设施语义内构造）；「当前无生产调用方」为 grep 全仓实证
- **误报排除**: 不是「持锁执行 listener 即误报」——低频启动回调粗锁可接受，但本设施回调语义就是周期收尾通知（含可能阻塞的收尾逻辑），锁内执行直接构成可达死锁；且异常撕裂面（F9）独立于阻塞假设成立。
- **复核状态**: 复核维持 P2（条件化）。亲核：回调在 this 监视器内执行（resolvePromise L87-89）、complete 在锁外（L94）——回调等待 cyclePromise 即单线程自死锁；waitCycleCompleted 持锁跑回调（L49-53）；回调异常致后续回调静默丢弃+全体等待者收到异常完成（L96-99）；跨实例 AB-BA 环成立。「当前全仓无生产调用方」复核再证（仅类自引用），条件化降档口径维持。现状：新发现

### [nop-kernel-concurrency-C2-005] RateLimitExecutorImpl.debounce 的 get-cancel-schedule-put 复合操作非原子 → 并发同 key 双执行；replace()（delay=0）的 holder[0] 晚初始化与定时线程竞态 → remove(key, null) NPE + 条目泄漏

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/RateLimitExecutorImpl.java:50-72`
- **证据片段**:
  ```java
  public void debounce(final Object key, long delay, Runnable task) {
      Future<?> old = promiseMap.get(key);        // ① get
      if (old != null)
          old.cancel(true);                        // ② cancel（只取消当时看到的）
      final Future<?>[] holder = new Future<?>[1];
      Future<?> future = executorService.schedule(() -> {
          try { task.run(); }
          finally { promiseMap.remove(key, holder[0]); }  // ④ holder[0] 可能尚未赋值
      }, delay, TimeUnit.MILLISECONDS);
      holder[0] = future;                          // ③ schedule 之后才赋值
      promiseMap.put(key, future);                 // ⑤ put（非 CAS，非 compute）
  }
  ```
- **严重程度**: P2（双执行面：当前生产用户 FileWatcher/NioFileWatchService 单 watch 线程触发 debounce，双执行需多线程调用方；holder NPE 面与调用方线程数无关，独立存在但窗口极窄——按判级口径均落 P2，若出现多线程 debounce 用户则升 P1）
- **并发场景**: debounce 当前用户：`FileWatcher.triggerChange`（key 恒为 `"process"`，经 `NioFileWatchService` 单线程 watch 执行器调用）、`nop-cli-core CliWatchZipCommand`。timer 线程（globalTimer）与提交线程天然双线程。
- **触发时序**: （双执行）T1 debounce("k")：①读到旧 future → ②取消 → schedule f1 → ⑤put(f1)。T2 在 T1 的①之后、⑤之前进入：①读到**旧 future（或 null）**→ ②取消的是旧 future，f1 从未被取消 → T2 schedule f2 → put(f2)。f1、f2 **都被执行**——去抖失效，"最后一击生效"契约破坏。（holder NPE）T1 debounce(key, 0, task)（即 `replace`）：schedule 返回后定时线程立即领取任务（delay=0）→ task.run() 完成 → 进入 finally 读 `holder[0]`，此刻提交线程恰在 schedule 返回后、`holder[0] = future` 赋值前被抢占 → holder[0]==null → `promiseMap.remove(key, null)` 抛 NPE（CHM 两条目 remove 对 null value 抛 NPE）→ 该异常吞掉 finally 中本应执行的清理 → map 中 key→future 条目泄漏（占位直到下次同 key debounce 覆盖；期间 throttle 对该 key 恒判 old.isDone()==true 可恢复，泄漏为有界但非零）。
- **现状**: throttle 用 compute 保证了原子性，debounce 是 get/cancel/schedule/put 四段无锁复合，且任务闭包引用晚绑定的 holder 数组。
- **风险**: debounce/throttle 是限流去重基建；双执行使「去抖后仅执行一次」语义失效（对 watch 触发的重编译/重加载场景造成重复动作），NPE 面使清理失效。
- **建议**: 用 `promiseMap.compute(key, ...)` 重写 debounce：在 compute 内完成 cancel 旧 future + 创建新 future + 放入 map（返回新 future）；holder 竞态以局部 final future 捕获（把 remove 改为 `promiseMap.remove(key, future)`，future 作为 effectively-final 局部变量先赋值再被闭包引用——需将 schedule 目标改为先创建 FutureTask 再提交的形态）。
- **失效面枚举**（F3：同族复合操作站点）:
  1. `debounce`（L50-67）——主站点（两个子缺陷）。
  2. `replace`（L70-72）——debounce(key,0,task) 直通，holder 面全量继承。
  3. `throttle`（L29-47）——compute 原子，正确形态（对照组）；但 compute 内 schedule（锁内调度）见 C2-003 失效面第 8 条，不立案。
  4. `ExecutorHelper.scheduleWithRandomDelay`（L210-238）——AtomicReference<Future> ref 在 schedule 后 set，与 holder 同构晚初始化，但取消路径仅 future.exceptionally 时读 ref，最坏多跑一个周期后由任务内检查收敛，无 NPE 面，不立案。
- **信心水平**: 确定（交错可构造）；实际触发概率窄
- **误报排除**: 不是「原语误报」——CHM 本身正确，缺陷在四段复合操作缺原子性与闭包晚绑定；与 throttle 的 compute 形成同文件内正确/错误形态对照，非风格问题。
- **复核状态**: 复核维持 P2。亲核：debounce get-cancel-schedule-put 四段非原子（L52-66）+ holder 晚绑定（L61 读/L65 赋值）——CHM.remove(key, null) 抛 NPE 面成立；throttle 的 compute 正确对照（L32-46）亲核。现调用方 FileWatcher:70 debounce("process",…) 经 NioFileWatchService 单 watch 线程 + CliWatchZipCommand 亲核——双执行面维持「需多线程调用方」条件化标注，holder NPE 面与线程数无关独立存在。现状：新发现

### [nop-kernel-concurrency-C2-006] DefaultScheduledExecutor checkEnabled-then-use 与 destroy() 的 TOCTOU：并发 destroy 使 executor 置 null 后提交路径 NPE

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/DefaultScheduledExecutor.java:88-93,100-141`
- **证据片段**:
  ```java
  public void destroy() {
      if(executor != null) {
          executor.shutdownNow();
          executor = null;              // 非volatile字段置null
      }
  }
  private void checkEnabled() {
      if (executor == null) { throw new NopException(ERR_SERVICE_NOT_ACTIVE)... }
  }
  public <T> CompletableFuture<T> submit(Callable<T> task) {
      checkEnabled();                   // 检查后
      return ExecutorHelper.submit(executor, task);   // 使用前可被destroy置null → NPE
  ```
- **严重程度**: P2
- **并发场景**: 应用停机/热更新线程执行 `destroy()`（@PreDestroy）与业务线程并发调用 submit/schedule/execute。
- **触发时序**: T1 业务线程执行 `submit(task)` → L109 checkEnabled 通过（executor 非 null）→ 调度切换；T2 停机线程执行 destroy() → `executor = null` → T1 恢复执行 L110 `ExecutorHelper.submit(executor, ...)` → 对 null 解引用 → NPE（而非设计的 ERR_SERVICE_NOT_ACTIVE fail-fast）。
- **现状**: 生命周期方法 destroy 与提交方法之间无同步，checkEnabled 检查与使用非原子。
- **风险**: 停机窗口的提交请求以 NPE 而非受控错误失败；对照组 DefaultThreadPoolExecutor 不置 null（shutdown 后 execute 由 TPE 抛 RejectedExecutionException，fail-fast 正确），同族行为不一致。
- **建议**: destroy 不置 null（改为 `shutdownNow()` + isDestroyed 判 shutdown 状态，与 DefaultThreadPoolExecutor 对齐）；或 executor 字段 volatile + 局部变量快照（`ExecutorService e = executor; if (e == null) throw ...; e.execute(...)`）。
- **失效面枚举**（F3：同族 check-then-use 站点）:
  1. `DefaultScheduledExecutor.submit(Callable)`（L108-111）。
  2. `DefaultScheduledExecutor.submit(Runnable,V)`（L114-117）。
  3. `DefaultScheduledExecutor.schedule`（L120-123）。
  4. `DefaultScheduledExecutor.scheduleAtFixedRate`（L126-129）。
  5. `DefaultScheduledExecutor.scheduleWithFixedDelay`（L132-135）。
  6. `DefaultScheduledExecutor.execute`（L138-141）——1-6 同案。
  7. `DefaultThreadPoolExecutor`——executor 不置 null，无此缺陷（对照）。
  8. `BindScheduledExecutor.destroy`（L158-160）——委托 timer.destroy，自身无字段置 null，无此缺陷。
  9. `LocalResourceLockManager.destroy`（L109-115）——仅 cancel schedulePromise 不置 null 锁表，无此缺陷。
- **信心水平**: 确定
- **误报排除**: 不是「生命周期竞态可接受」——同类 DefaultThreadPoolExecutor 已展示无缺陷形态，且 NPE 违反平台「错误处理两档策略」（内部实现也应抛受控异常而非 NPE）。
- **复核状态**: 复核维持 P2。destroy 置 null（非 volatile，L88-93）+ 6 处提交方法 checkEnabled-then-use（L108-141）逐点亲核；DefaultThreadPoolExecutor 不置 null 的对照亲核。现状：新发现

### [nop-kernel-concurrency-C2-007] AbstractResourceLock 家族违反 Lock 接口线程中断契约：lockInterruptibly() 不声明 InterruptedException、中断被转换为 NopException（LocalFileLock 已由 C1 轮立案的同族站点）

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/lock/impl/AbstractResourceLock.java:50-69`、`lock/impl/ResourceLock.java:78-84`（unlock 纪律不对称）、`lock/impl/ResourceMultiLock.java:83-89`
- **证据片段**:
  ```java
  @Override
  public void lockInterruptibly() {      // Lock 契约要求 throws InterruptedException
      lock();                             // → tryLockWithLease → latch.await 被中断时：
  }
  // LocalResourceLockManager.tryLockWithLease:
  } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw NopException.adapt(e);       // 中断转为 NopException 抛出
  }
  ```
- **严重程度**: P2
- **并发场景**: 按 `java.util.concurrent.locks.Lock` 契约使用该锁族的调用方：线程池 shutdownNow 中断持等线程、`Future.cancel(true)` 打断等锁请求。
- **触发时序**: T1 调用 `lock.tryLock(5, TimeUnit.SECONDS)`（Lock 契约声明 InterruptedException）→ 在 `LocalResourceLockManager.tryLockWithLease` L178 的 `latch.await` 处被 T2 的 interrupt 打断 → L184 `throw NopException.adapt(e)` → T1 收到 NopException。T1 按 Lock 契约写的 `catch (InterruptedException e)` **捕获不到**，中断语义对调用方不可见（中断标志虽被 L183 重置，但异常类型违约）；`lockInterruptibly()` 甚至无法通过编译期声明表达可中断性。
- **现状**: 锁族在等待路径确实可中断（await 会醒），但把 `InterruptedException` 重包装为 NopException，违反 Lock 接口的异常契约；与 C1 轮已立案的 LocalFileLock 契约违反同族（本条覆盖 AbstractResourceLock/ResourceLock/ResourceMultiLock 三个类，不重复 LocalFileLock）。
- **风险**: 调用方无法按标准契约区分「拿锁超时失败」与「被中断」；框架级中断传播（停机、超时取消）在该锁族上行为与 JDK Lock 不一致，误导使用方编写错误的恢复逻辑。
- **建议**: `tryLockWithLease` 的中断路径改抛原始 InterruptedException（向上传播到 lockInterruptibly/tryLock(time,unit) 的签名处）；`lockInterruptibly()` 补 `throws InterruptedException` 声明。同时统一 ResourceLock.unlock（synchronized）与 ResourceMultiLock.unlock（非同步）的纪律——建议均为无锁 CAS 置空（字段可改 AtomicReference）或均 synchronized。
- **失效面枚举**（F3：锁实现族契约逐类核对——「还有哪些锁实现违反契约」）:
  1. `AbstractResourceLock.lockInterruptibly`（L57-59）——不声明中断异常，违约，本案。
  2. `AbstractResourceLock.tryLock(time,unit)`（L67-69）——中断转 NopException，违约，本案。
  3. `AbstractResourceLock.lock`（L50-54）——Lock.lock 契约不允许抛 InterruptedException，当前抛 NopException(中断) 属可辩护偏差（保留中断标志），注记。
  4. `AbstractResourceLock.tryLock()`（L62-64）——waitTime=0 不进入 await，无中断面，合规。
  5. `ResourceLock`（unlock synchronized，L78-84）与 `ResourceMultiLock`（unlock 非同步，L83-89）——同族纪律不对称，本案一并裁定。
  6. `LocalFileLock`——C1 轮已立案，不重复（其 L60-65 sleep 吞中断不重置标志为同族内**另一**缺陷：`catch (InterruptedException e) { if (interruptable) throw e; }` 非 interruptable 路径吞掉中断且标志已清，登记为本案同族补遗）。
  7. `IResourceLockManager` 默认方法（lock/tryLock 包装，IResourceLockManager.java:41,52）——随实现类继承上述违约。
- **信心水平**: 确定
- **误报排除**: 不是「异常适配即误报」——NopException.adapt 中断是错误处理维度的合理选择，但发生在实现 `Lock` 接口的类上即构成接口契约违约；排除依据是审计对象为「锁实现族违反契约」的定向检查项。
- **复核状态**: 复核维持 P2。IResourceLock extends java.util.concurrent.locks.Lock 亲核；lockInterruptibly 无 throws InterruptedException 声明且直通 lock()（AbstractResourceLock L57-59）亲核；中断被 NopException.adapt 转换（LocalResourceLockManager L182-184，中断标志已重置）亲核；ResourceLock.unlock synchronized（L78-84）vs ResourceMultiLock.unlock 非同步（L83-89）不对称亲核。现状：新发现

### [nop-kernel-concurrency-C2-008] LocalResourceLockManager.tryLockWithLease 的过期判定+删除绕过 latch 监视器，违反同文件注释声明的「与 tryResetLease/isHoldingLock 使用同一把锁」不变式

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/lock/impl/LocalResourceLockManager.java:169-171`（对照 117-129/229-252/255-273）
- **证据片段**:
  ```java
  // checkTimeout（L122 注释）：与tryResetLease/isHoldingLock使用同一把锁，避免续租过程中读到旧的expireTime而误删锁
  synchronized (lock.getLatch()) { if (lock.getExpireTime() <= now) removeExpiredLock(lock); }
  ...
  // tryLockWithLease（L169）——同型判定，无监视器：
  if (existingLock.getExpireTime() < CoreMetrics.currentTimeMillis()) {
      removeExpiredLock(existingLock);   // 锁外判定 + 锁外删除 + countDown
      continue;
  }
  ```
- **严重程度**: P2（理论风险，未具体化——见触发时序说明）
- **并发场景**: 锁竞争者（tryLockWithLease 循环）与持锁者（tryResetLease 续租）并发；后台 checkTimeout 每 1s 扫描。
- **触发时序**: 逐点推演结论：由于 `ResourceLockState.expireTime` 为 volatile（ResourceLockState.java:20-21 注释明示）且判定为单变量一次读，JMM 的 volatile 定序使「T1 读到已过期旧值」与「T2 基于同一旧值续租成功」不可同时成立（T2 续租要求 expireTime > now，与 T1 判定的 expireTime < now 在同一时钟下矛盾），当前代码在正确时钟下收敛、互斥不被破坏。**写不出必然触发的交错**，故按规则降 P2 标注。风险在纪律层面：该站点的「判定-删除」与同文件其余三处不一致，绕过了注释显式声明的 invariant；一旦后续重构（expireTime 去 volatile、判定改多字段、或引入时钟回拨容忍逻辑），此处即成为互斥破坏点（竞争者 countDown 已被续租锁的 latch → 所有等待者提前唤醒重抢，持锁者无感知继续执行）。
- **现状**: 四处 expire 判定中三处在 `synchronized(latch)` 内，一处（tryLockWithLease L169）在外。
- **风险**: 当前无害（依赖 volatile 收敛），重构易碎点；时钟回拨场景下判定与续租的时钟基准不一致可放大窗口。
- **建议**: 将 L169-171 的判定+删除包入 `synchronized (existingLock.getLatch())`，与 checkTimeout 完全同构；或在注释中显式声明「此处依赖 expireTime volatile 单变量收敛」的脆弱性前提。
- **失效面枚举**（F3：LocalResourceLockManager 全部 expire 判定/删除站点）:
  1. `checkTimeout`（L121-128）——监视器内，正确。
  2. `tryLockWithLease`（L169-171）——绕过监视器，本案。
  3. `tryResetLease`（L232-249）——监视器内，正确。
  4. `isHoldingLock`（L258-271）——监视器内，正确。
  5. `removeExpiredLock`（L131-137）——被 2 在锁外调用、被 1/3/4 在锁内调用，引用条件删除（locks.remove(id, lock)）本身幂等安全。
  6. `forceUnlock`（L217-226）——无条件删除（force 语义），合规。
  7. `releaseLockForLocker`（L204-209）——CHM 弱一致迭代 + 引用条件删除，合规。
- **信心水平**: 很可能（纪律缺口确定；当前无必现交错）
- **误报排除**: 不是「volatile 即安全」的无病呻吟——同一文件注释把「同一把锁」声明为不变式，本条是「代码与自声明不变式矛盾」的纪律发现，并给出未来重构的破坏路径。
- **复核状态**: 复核维持 P2（理论风险标注维持）。tryLockWithLease L169-171 判定+删除在 latch 监视器外亲核（对照 checkTimeout L123/tryResetLease L232/isHoldingLock L258 三处监视器内）；ResourceLockState.expireTime volatile（L21-22 注释明示并发读写背景）亲核——「volatile 单变量一次读使判定与续租在正确时钟下收敛、当前无必现交错」推演正确，纪律缺口（代码与自声明不变式矛盾）成立。现状：新发现

### [nop-kernel-concurrency-C2-009] 单例 registerInstance 热替换对非 volatile 静态字段：DictProvider / ModuleManager 的 unsafe publication 风险（同族 volatile 正确形态已存在）

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/dict/DictProvider.java:35-45`、`nop-kernel/nop-core/src/main/java/io/nop/core/module/ModuleManager.java:43-49`
- **证据片段**:
  ```java
  // DictProvider
  private static IDictProvider _instance = new DictProvider();   // 非final非volatile
  public static void registerInstance(IDictProvider provider) {
      _instance = Guard.notNull(provider, "dictProvider");       // IoC初始化线程热替换
  }
  // ModuleManager：同型
  private static ModuleManager _instance = new ModuleManager();
  public static void registerInstance(ModuleManager instance) { _instance = instance; }
  ```
- **严重程度**: P2（理论风险，未具体化——需 JMM 重排序才产生可见性失败，写不出 wall-clock 必现交错）
- **并发场景**: IoC 容器初始化线程执行 registerInstance 替换单例；请求线程经 `instance()` 读取。
- **触发时序**: T1 初始化线程执行 `registerInstance(newProvider)`：新实例构造（dictLoaders 等非 final 字段赋值）与 `_instance` 写入之间无释放屏障（非 volatile/非 final）。T2 请求线程读 `_instance` 命中新引用但无 happens-before 边 → 按 JMM 允许观察到字段默认值（如 dictLoaders 为 null）或旧字段值。实际 x86/HS 下静态写通常带平台级刷新，未具体化到必现交错 → P2。
- **现状**: 同族单例三种形态并存：volatile（ResourceComponentManager.java:71、I18nMessageManager.java:38、ResourceTenantManager.java:46——正确）；static final（StdDomainRegistry、EvalGlobalRegistry、ReflectionManager 等——不可替换，正确）；非 volatile 非 final + registerInstance（DictProvider、ModuleManager——缺口）；ErrorMessageManager 非 final 非 volatile 但**无** registerInstance（无替换路径，事实安全）。
- **风险**: 平台明确支持单例热替换（registerInstance 是公开 API），测试/多容器场景（Spring 集成、Autotest 重建容器）会真实走替换路径。
- **建议**: DictProvider/ModuleManager 的 `_instance` 补 volatile（与 ResourceComponentManager 同型，一行修复）。
- **失效面枚举**（F3：B 面单例发布形态全量）:
  1. `DictProvider._instance`——非volatile+可替换，本案。
  2. `ModuleManager._instance`——非volatile+可替换，本案。
  3. `ErrorMessageManager._instance`（L42）——非final非volatile、无替换路径，事实安全，注记。
  4. `ResourceComponentManager._instance`（L71）——volatile，正确。
  5. `I18nMessageManager._instance`（L38）——volatile，正确。
  6. `ResourceTenantManager._instance`（L46）——volatile，正确。
  7. `ReflectionManager/StdDomainRegistry/EvalGlobalRegistry/ScriptCompilerRegistry/XPathOperatorRegistry/EvalBackendRegistry/ObjPropXmlMapperRegistry/CodeLangMap/GlobalCacheRegistry`——static final，类初始化安全发布，正确。
- **信心水平**: 很可能（JMM 缺口确定，实际触发依赖平台重排序）
- **误报排除**: 不是「单例模式风格问题」——同一代码库内同族单例已用 volatile 形态自证这是本平台认定必要的纪律（含 registerInstance 的类全部三处正确形态均为 volatile），非风格偏好。
- **复核状态**: 复核维持 P2（理论风险标注维持）。DictProvider._instance / ModuleManager._instance 非 final 非 volatile + registerInstance 热替换亲核；同族 volatile 正确形态（RCM L71、I18nMessageManager、ResourceTenantManager）与 static final 形态亲核。现状：新发现

### [nop-kernel-concurrency-C2-010] DedupBlockingQueue 复合状态（map+queue）无共同锁：send() 异常路径不回滚 map 条目 → key 永久墓碑，后续同 key 项全部静默丢弃

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/impl/DedupBlockingQueue.java:122-126`（对照 offer 40-54 的回滚）
- **证据片段**:
  ```java
  @Override
  public void send(T item) throws InterruptedException {
      K key = keyFunc.apply(item);
      if (map.put(key, item) == null)     // 先占坑
          queue.send(item);               // 抛出（ERROR策略队列满 / BLOCK_WAIT被中断）→ 无人清理 map
  }
  // 对照 offer() 有回滚：
  if (!queue.offer(o)) { map.remove(key, o); return false; }
  ```
- **严重程度**: P2（当前全仓 grep 无生产调用方；一旦投用即单线程可复现，届时升 P1）
- **并发场景**: 复合不变式「map 中的条目 = 队列中在途条目」跨越 CHM bin 锁与内层队列锁两个临界区，无共同锁；异常路径打破配对。内层 queue 常配 `OverflowBlockingQueue`，其 `send` 在 `QueueOverflowPolicy.ERROR` 策略下队列满时抛 `ERR_QUEUE_FULL`（OverflowBlockingQueue.java:128-133）。
- **触发时序**: 单线程即可：T1 `dedupQueue.send(k, item)`，key=k 首次出现 → `map.put(k,item)` 返回 null（已占坑）→ `queue.send(item)` 因队列满（ERROR 策略）抛 NopException → map 中遗留 `k→item`，但 item 从未入队。此后所有 `send(k, *)` / `offer(k, *)` 见 map 非 null → 直接返回/丢弃，**永不入队**；消费者 take() 只能从队列取得条目再 map.remove，k 的墓碑条目永远无人移除 → key k 的全部后续事件永久静默丢失（F2 半个成功 + F8 清理不对称）。
- **现状**: offer() 实现了失败回滚，send() 是同族内的漏修兄弟（F3 族内不一致）。
- **风险**: 去重队列的典型用途（同 key 事件合并）下，一次瞬时过载即造成该 key 的永久静默丢弃，且无任何日志。
- **建议**: send 加 try/catch 回滚 `map.remove(key, item)`；或与 offer 统一为单实现（send = offer + BLOCK_WAIT 语义）。
- **失效面枚举**（F3：复合状态站点）:
  1. `DedupBlockingQueue.send`（L122-126）——主站点。
  2. `DedupBlockingQueue.offer`（L40-54）/`offer(timeout)`（L57-69）——有回滚，正确。
  3. `DedupBlockingQueue.clear`（L116-119）——queue.clear 先销毁、map.clear 后清引用，两步间消费者可取走在途项并 map.remove（收敛）；map 中未入队项（若已存在墓碑）被静默清引用不销毁，随本案修复自然消除。
  4. `OverflowBlockingQueue`——单层状态（仅 queue），无复合站点（其 DROP_ELDEST 非原子另案 C2-011）。
- **信心水平**: 确定（单线程交错即成立）
- **误报排除**: 不是「有界队列满抛错即设计」——抛错本身合规，缺陷在复合状态未回滚且与同文件 offer 的回滚形态自相矛盾。
- **复核状态**: 复核维持 P2（条件化）。send 无回滚（L122-126）vs offer/offer(timeout) 有回滚（L49-51/L64-67）亲核；ERROR 策略队列满抛 ERR_QUEUE_FULL（OverflowBlockingQueue L128-133，抛前 onRemoveItem 已销毁 item——map 墓碑还引用已销毁对象，危害面与初审一致）。「当前全仓无生产调用方」复核再证（仅类自引用），条件化标注维持。现状：新发现

### [nop-kernel-concurrency-C2-011] OverflowBlockingQueue.send 的 DROP_ELDEST 分支 offer/poll 两段内锁非原子：并发下超额驱逐或驱逐他线程刚入队项

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/impl/OverflowBlockingQueue.java:122-127`
- **证据片段**:
  ```java
  } else if (overflowPolicy == QueueOverflowPolicy.DROP_ELDEST) {
      while (!offer(item)) {        // 内锁获取 #1
          T t = poll();             // 内锁获取 #2（与#1非原子）
          if (t != null)
              onRemoveItem(t);
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: 多生产者线程并发对同一满队列 send（DROP_ELDEST 策略），消费者并发 poll。
- **触发时序**: 队列满（容量 C）。T1 offer 失败 → T2 offer 失败 → T1 poll 取出 x（驱逐 #1）→ T2 poll 取出 y（驱逐 #2）→ T1 offer 成功 → T2 offer 成功。净效果正确（进 2 出 2），但 (a) 驱逐数量依赖交错，T2 本可复用 T1 腾出的空位却额外驱逐了一个元素；(b) T1 的 poll 可能取走 T2 在其 offer 失败后刚入队的新项（「eldest」判定失真）。均为语义扰动，无死锁/丢失入队（循环兜底最终成功）。
- **现状**: DROP_ELDEST 的「取最旧再放新」未做成原子置换。
- **风险**: 过载期驱逐行为不确定、可观测统计偏差；无正确性破坏。
- **建议**: 短期内可接受；若需精确语义，用单锁复合（自定义 ArrayBlockingQueue 子类在一个锁区完成 poll+offer）或文档声明「并发驱逐计数不精确」。
- **失效面枚举**（F3：OverflowBlockingQueue.send 全分支）:
  1. `BLOCK_WAIT`（L116-117）——queue.put 单原子操作，正确。
  2. `DROP_NEWEST`（L118-121）——offer+onRemoveItem，两段但失败侧无状态残留，正确。
  3. `DROP_ELDEST`（L122-127）——本案。
  4. `ERROR`（L128-133）——offer+抛错，正确（其异常路径被 DedupBlockingQueue.send 放大为 C2-010）。
- **信心水平**: 确定
- **误报排除**: 判级按后果定 P3 而非漏报：逐点核对无丢失唤醒（put/offer 内锁条件通知由 ArrayBlockingQueue 保证）、无死锁（单锁方向一致）。
- **复核状态**: 复核维持 P3。DROP_ELDEST while(!offer){poll+onRemoveItem}（L122-127）两段内锁非原子亲核；语义扰动无正确性破坏判定正确。现状：新发现

### [nop-kernel-concurrency-C2-012] ResourceContentCache.getCachedText 经 LocalCache.computeIfAbsent 在 Caffeine bin 锁内执行 resource.readText() 文件 I/O

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceContentCache.java:47-56`、`nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCache.java:219-221`
- **证据片段**:
  ```java
  // ResourceContentCache
  if (allowLoad)
      return cache.computeIfAbsent(resource.getPath(), key -> {
          return new ContentEntry(resource.lastModified(), resource.readText());  // 文件I/O
      }).text;
  // LocalCache
  public V computeIfAbsent(K key, Function<...> mappingFunction) {
      return cache.get(key, mappingFunction);   // Caffeine: mapping 在内部 CHM bin 锁内执行
  ```
- **严重程度**: P3
- **并发场景**: 请求线程并发读取资源文本（模板/配置/脚本源码）；Caffeine 的 `Cache.get(key, mappingFunction)` 在 per-bin 锁内执行 mapping。
- **触发时序**: T1 首读 pathA → bin 锁内 readText（磁盘毫秒级，慢盘/NFS 更长）；T2 请求**哈希同 bin**的无关 pathB → 阻塞至 T1 读盘完成。同 path 去重为预期收益；跨 key 串行化为代价。
- **现状**: bin 冲突概率随缓存规模降低（默认容量下 bins 充足），单 key 串行化为有意设计。
- **风险**: 慢存储（NFS 容器卷）下偶发跨请求尾延迟；无正确性问题。
- **建议**: 如需消除，改为 getIfPresent + 双重检查 putIfAbsent（ContentEntry 不可变，竞态安全），或文档声明该权衡。
- **失效面枚举**（F3：LocalCache.computeIfAbsent 生产使用面全量）:
  1. `ResourceContentCache.getCachedText`（L52）——I/O，本案。
  2. `TenantAwareResourceLoadingCache.tenantCaches`（L31-33）——mapping 仅 new ResourceLoadingCache（构造轻量，无 I/O），不立案。
  3. `ReflectionManager.introspectCache`——无 loader（LocalCache.newCache(name, config, null)），computeIfAbsent 未用，不立案。
- **信心水平**: 确定（锁内 I/O 事实）；影响幅度推断
- **误报排除**: 按 C9 口径本应要求量化，此处以「锁内 I/O」C2 定向检查项立案并自降 P3；非「Caffeine 使用即误报」——问题在 mapping 内容为阻塞 I/O。
- **复核状态**: 复核维持 P3。getCachedText→LocalCache.computeIfAbsent→Caffeine get(key,fn)（bin 锁内 readText）亲核。现状：新发现

### [nop-kernel-concurrency-C2-013] 注册表启动期 check-then-put 非原子族：ResourceComponentManager.registerComponentModelConfig / ReflectionManager.registerClassModel / GlobalExecutors.register

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/ResourceComponentManager.java:226-229`、`nop-kernel/nop-core/src/main/java/io/nop/core/reflect/ReflectionManager.java:276-279`、`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:102-107`
- **证据片段**:
  ```java
  // ResourceComponentManager
  Guard.checkArgument(!modelTypeConfigs.containsKey(modelType),   // 检查
          "component with the same model type already exists", modelType);
  modelTypeConfigs.put(modelType, config);                        // 放置（非原子）
  // ReflectionManager.registerClassModel 同型：
  if (registeredClassModels.containsKey(...)) throw ...;
  registeredClassModels.put(classModel.getRawClass(), classModel);
  ```
- **严重程度**: P3
- **并发场景**: 模块并行初始化（Spring/NopIoC 多线程 bean 装配、热插拔模块）时两个线程注册同一 modelType/classModel。
- **触发时序**: T1 containsKey=false → T2 containsKey=false → T1 put → T2 put（静默覆盖）→ 重复注册的 fail-fast 契约失效，先注册者的 cancellable 清理路径与实际生效配置错位。
- **现状**: 契约是「重复注册即报错」，实现是检查与放置两步。
- **风险**: 低频启动期竞态，后果为错误配置静默生效，难排查。
- **建议**: 改 `putIfAbsent` 判旧值抛错（对照同文件 `registerComponentModelLoader` L260-264 已是正确 putIfAbsent 形态——同文件内即有正确范本）。
- **失效面枚举**（F3：注册 check-then-put 全量）:
  1. `ResourceComponentManager.registerComponentModelConfig`（L226-229）——本案。
  2. `ReflectionManager.registerClassModel`（L276-279）——本案。
  3. `GlobalExecutors.register`（L102-107）——put+destroyOld，无唯一性契约，语义不同不立案；但并发同名 register 双 destroy 旧实例可能（destroy 幂等），注记。
  4. `GlobalCacheRegistry.register`——putIfAbsent 后抛错，正确（对照）。
  5. `ResourceComponentManager.registerComponentModelLoader/Transformer`——putIfAbsent 判旧，正确（对照）。
  6. `EvalBackendRegistry.register`——synchronized + get/put，正确（对照）。
  7. `EvalGlobalRegistry.registerVariable`/`StdDomainRegistry.registerStdDomainHandler`——put 覆盖语义（replace-by-design，有日志），契约即允许覆盖，不立案。
- **信心水平**: 确定
- **误报排除**: 启动期粗锁/竞态可容忍的校准条款不适用于**契约违约**（声明 fail-fast 却可能静默覆盖）；判 P3 因频率低。
- **复核状态**: 复核维持 P3。三站点亲核：RCM registerComponentModelConfig containsKey-then-put（L226-229）、ReflectionManager.registerClassModel 同型（L276-279）、GlobalExecutors.register 为 put+destroyOld 无唯一性契约（注记口径正确）；同文件 putIfAbsent 正确范本（registerComponentModelLoader L260-264）亲核。现状：新发现

### [nop-kernel-concurrency-C2-014] ResourceTenantManager.isSupportTenant 无同步惰性初始化非 volatile 字段（热路径，幂等但每次重读配置）

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/tenant/ResourceTenantManager.java:100-111`
- **证据片段**:
  ```java
  public boolean isSupportTenant(String resourcePath) {
      if (disabledTenantPaths == null) {                     // 非volatile，无锁
          disabledTenantPaths = CFG_TENANT_RESOURCE_DISABLED_PATHS.get();
          if (disabledTenantPaths == null)
              disabledTenantPaths = Collections.emptySet();
      }
      if (enabledTenantPaths == null) { ... }
  ```
- **严重程度**: P3
- **并发场景**: 全部模型加载请求线程（`supportTenant` 在 ResourceComponentManager.ModelLoader L113 每次加载调用）。
- **触发时序**: T1/T2 同时见 null → 双双初始化（同一不可变值，重复覆盖，无害）；T3 在无 happens-before 下读字段可能持续见 null → 每次调用重读配置（`CFG_*.get()`）→ 热路径重复配置解析开销。无可变状态撕裂（Set 均为不可变实例）。
- **现状**: 惰性初始化无锁无 volatile，值幂等不可变，正确性无损。
- **风险**: 仅性能（每请求多两次配置读取）+ 未来若字段变为可变集合则成 C1 缺陷。
- **建议**: 启动期（首次 discover/init）一次性初始化为 final 字段；或 volatile + 双重检查。
- **失效面枚举**（F3：同族惰性初始化）:
  1. `ResourceTenantManager.isSupportTenant`（L101-111）——本案。
  2. `ResourceTenantManager.runInitializeTenantTask`（L76-88）——ThreadLocal set(true)/finally set(false)：false 残留池化线程（未用 remove()），量级为 Boolean 引用，轻微，注记随本案。
  3. `TaskQueue.forcedRemainingCapacity`（TaskQueue.java:37）——非volatile Integer，注释声明单线程使用（contextStopping）；核对调用面：写仅 StandardThreadPoolExecutor.contextStopping（管理线程），读为同线程 setCorePoolSize 内部路径 remainingCapacity，监控路径（ExecutorHelper.getStats）不读该字段——单线程假设**当前成立**，不立案，注记。
- **信心水平**: 确定（形态）；影响小
- **误报排除**: 不可变幂等值的无锁惰性初始化正确性无虞，故仅 P3；排除「见共享字段即报线程不安全」的误报模式——本案明确无正确性影响。
- **复核状态**: 复核维持 P3。isSupportTenant 无同步惰性初始化（L100-111，值幂等不可变）+ runInitializeTenantTask ThreadLocal finally set(false) 未 remove（L76-88）亲核。现状：新发现

### [nop-kernel-concurrency-C2-015] ReactiveObjectMap.get 异常路径留下 exceptionally-completed 条目不清理 → 失败结果永久缓存

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ReactiveObjectMap.java:39-61`
- **证据片段**:
  ```java
  ReactiveObject oldObj = objMap.putIfAbsent(key, obj);
  if (oldObj != null) {
      obj = oldObj;
  } else {
      try {
          obj.future.complete(collectDependsTo(obj.deps, () -> fn.call0(null, scope)));
      } catch (Exception e) {
          obj.future.completeExceptionally(e);   // 条目仍留在 objMap，无 remove
      }
  }
  ...
  return FutureHelper.getFromFuture(obj.future);
  ```
- **严重程度**: P2
- **并发场景**: 多请求线程并发按 key 求值（ winner/loser 模式，与 C2-002 的 ResourceCacheEntry 同为「首次求值+依赖缓存」形态）。
- **触发时序**: T1 首次 get(key) → putIfAbsent 胜出 → fn.call0 抛异常（瞬时故障：资源暂不可读/脚本临时错误）→ future 以异常完成，**条目留存**。T2 及之后所有 get(key)：`obj != null && obj.isChanged()`——失败对象从未 `deps.freeze()`（对比 ResourceCacheEntry.loadObject L221 有 freeze），未冻结的 VirtualResourceDependencySet 依赖为空/未收集 → `isAnyDependsChange(空)` 返回 false → isChanged()==false → 直接返回异常 future → **此后该 key 永久抛同一异常**，无任何自愈路径（除非进程重启或外部清空 map——该类无 clear API）。
- **现状**: 成功路径依赖复用正确，失败路径无条目淘汰。
- **风险**: 一次瞬时求值失败固化为永久失败（F9 吞异常 → 状态不一致的持久化变体）。
- **建议**: catch 分支补 `objMap.remove(key, obj)`；或失败时也 complete 并冻结空 deps 使 isChanged 恒 true（迫使下次重算）。
- **失效面枚举**（F3：「失败条目留存」同族）:
  1. `ReactiveObjectMap.get` catch 分支（L55-56）——本案。
  2. `ResourceCacheEntry.getObject` 失败路径（L186-190）——失败时 `object = null`（下次重载），**正确形态**（对照）。
  3. `LocalCache/Caffeine`——loader 抛错由 Caffeine 内部不缓存异常结果，正确（对照）。
  4. `RateLimitExecutorImpl.throttle`——任务 finally remove(k, promise)，失败也清理，正确（对照）。
- **信心水平**: 很可能（isChanged 对未冻结依赖返回 false 基于 VirtualResourceDependencySet/ResourceDependencySet 代码推断，未逐行核对 isAnyDependsChange 对空集语义——复核阶段建议确认）
- **误报排除**: 不是「缓存异常结果供快速失败」的设计——该类无 TTL、无失效 API，且依赖集未冻结导致唯一自愈条件（依赖变化检测）失效，非声明边界。
- **复核状态**: 复核维持 P2，补条件化标注「当前全仓无生产调用方」（复核亲核：仅类自引用）——与 C2-004/C2-010 同判例，判级不改。初审判级中「未冻结依赖→isChanged 恒 false」的推断部分经复核坐实：getDepends() 未收集时返回 null（ResourceDependencySet L113-117），isAnyDependsChange(null) 走 depends!=null 守卫返回 false（ResourceDependsManager L218-228），且该类 isChanged 无 ResourceCacheEntry 式 deps==null→true 兜底、无 clear API——永久毒化链完整成立。现状：新发现

---

## 维度集外线索（C8 形态，不立案展开）

### [nop-kernel-concurrency-CL-004]（维度集外线索，不立案展开）SequentialTaskExecutor.execute 忽略 queue.offer 返回值：队列满时任务静默丢弃，BLOCK_WAIT 容量语义失效

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/SequentialTaskExecutor.java:80-83`
- **C8 形态**: `queue.offer(command)` 的返回值被丢弃——OverflowBlockingQueue 在 DROP_NEWEST/ERROR 策略下满队列 offer 返回 false（任务被销毁或将被 send 抛错），在 BLOCK_WAIT 策略下 offer 也不阻塞（未走 send 的 put 路径）→ 单线程执行器的「容量上限 + 满时阻塞」声明两层全部失效：过载即静默丢任务，无日志无指标。
- **为什么不在 C2 立案**: 属背压/过载行为（生产-消费边界的满队列语义），非锁正确性。SequentialTaskExecutor 的 CAS 调度协议本身经推演无丢失唤醒。

### [nop-kernel-concurrency-CL-005]（维度集外线索，不立案展开）BindScheduledExecutor.scheduleAtFixedRate 将任务转发给下游 executor，下游拒绝即 JDK 周期任务静默停摆

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/BindScheduledExecutor.java:65-69`
- **C8 形态**: `timer.scheduleAtFixedRate(() -> executor.execute(command), ...)`——下游 executor 队列满/关闭时 execute 抛 RejectedExecutionException，异常落在 timer 线程的周期任务体内；JDK ScheduledThreadPoolExecutor 语义为「周期任务一旦抛异常即取消后续全部触发」，且该 Future 无人读取 → 固定速率任务**无日志、无指标、永久静默停摆**（F9+F5）。scheduleWithFixedDelay 重写版（L72-116）恰好正确处理了该面（catch 后 completeExceptionally），同文件内正确/缺陷形态对照明确。
- **为什么不在 C2 立案**: 过载降级语义（拒绝策略与周期调度交互），非锁问题。

### [nop-kernel-concurrency-CL-006]（维度集外线索，不立案展开）BlockingSourceConsumeTask.run 批次出队后处理失败仅记日志继续 → 批次静默丢失，无半个成功恢复语义

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/batch/BlockingSourceConsumeTask.java:59-74`
- **C8 形态**: `source.drainTo(items)` 已将条目从队列移除后，`consumer.acceptList(items)` 抛异常仅 `LOG.error` 继续下一轮——该批数据永久丢失（drain 已出队，无重投/死信/恢复路径），处理计数在 finally 中归还导致 `isAllProcessed()` 仍可返回 true。过载/下游故障时系统表现 为有界静默降级，但丢失无界累积。
- **为什么不在 C2 立案**: 消费失败语义（F2），非锁问题。

---

## 条目外观察（未立案，供其他维度认领）

1. **ExecutorHelper.scheduleWithRandomDelay**（`nop-kernel/nop-commons/.../executor/ExecutorHelper.java:210-238`）：任务成功路径永不 `future.complete`（仅异常路径 completeExceptionally）→ 调用方若 join 该 future 将永久挂起（C3 异步正确性/F5 形态）。当前无 nop-kernel 内生产调用方（仅 IScheduledExecutor default 方法暴露）。
2. **JsonWhitelist.add**（`nop-kernel/nop-core/.../lang/json/JsonWhitelist.java:43-49`）：`whitelistPrefixes.add(className.substring(className.length() - 1))` 取的是**最后一个字符**（`*`）而非包前缀 → 前缀白名单完全失效（功能/安全形态，非并发）。
3. **FileWatcher.processing**（`nop-kernel/nop-core/.../resource/watch/FileWatcher.java:33`）：非 volatile boolean，watch 线程读、timer 线程写（processChangeEvent），跨线程可见性缺口（C1 形态）。
4. **ResourceCacheEntry/ReactiveObjectMap 失效面**（C2-002/C2-015）与 **Caffeine removalListener 在内部锁面执行 destroy**（C2-004 失效面第 4 条注记）——复核阶段建议并入同族裁定。

## 第 1 轮发现清单汇总

| ID | 严重程度 | 文件 | 一句话摘要 |
|---|---|---|---|
| C2-001 | P1 | ResourceCacheEntry.java / ResourceComponentManager.java / GlobalFunctions.java | 持 entry 监视器执行模型加载且加载器递归跨 entry，循环依赖模型可达无超时死锁 |
| C2-002 | P2 | ResourceCacheEntry.java / ResourceLoadingCache.java | 快路径无锁读 vs 锁内销毁，use-after-destroy 窗口 |
| C2-003 | P2 | ResourceComponentManager.java:343 | CHM bin 锁内执行 transformer.transform，递归 compute 风险 |
| C2-004 | P2 | CycleSynchronizer.java | 持 this 锁执行任意回调：自死锁/跨实例锁序环/回调撕裂（当前无生产调用方） |
| C2-005 | P2 | RateLimitExecutorImpl.java:50-72 | debounce 四段非原子双执行 + holder 晚绑定 NPE/泄漏 |
| C2-006 | P2 | DefaultScheduledExecutor.java | checkEnabled-then-use 与 destroy TOCTOU → NPE（6 处同族） |
| C2-007 | P2 | AbstractResourceLock.java / ResourceLock.java / ResourceMultiLock.java | Lock 中断契约违约族 + unlock 纪律不对称 |
| C2-008 | P2 | LocalResourceLockManager.java:169 | 过期判定绕过 latch 监视器，违反自声明不变式（理论风险） |
| C2-009 | P2 | DictProvider.java / ModuleManager.java | registerInstance 单例非 volatile，unsafe publication（理论风险） |
| C2-010 | P2 | DedupBlockingQueue.java:122 | send 异常路径不回滚 map → key 永久墓碑（当前无生产调用方） |
| C2-011 | P3 | OverflowBlockingQueue.java:122 | DROP_ELDEST offer/poll 非原子 |
| C2-012 | P3 | ResourceContentCache.java:52 | 文件 I/O 在 Caffeine bin 锁内 |
| C2-013 | P3 | ResourceComponentManager.java / ReflectionManager.java / GlobalExecutors.java | 注册 check-then-put 非原子（同文件已有正确范本） |
| C2-014 | P3 | ResourceTenantManager.java:100 | 热路径无同步惰性初始化（幂等，仅性能） |
| C2-015 | P2 | ReactiveObjectMap.java:55 | 失败条目留存 → 求值结果永久毒化 |

CL 线索：CL-004（SequentialTaskExecutor 静默丢任务）、CL-005（BindScheduledExecutor 周期任务静默停摆）、CL-006（BlockingSourceConsumeTask 批次丢失）。

## 统计

- 第 1 轮（初审）发现：**15 条**（P1 × 1，P2 × 9，P3 × 4，P2-理论风险标注 × 2 含在 P2 内）
- 维度集外线索：**3 条**（CL-004 ~ CL-006）
- 立案排除（经核验无问题并留档判定理由）：TaskQueue offer/poll/take 等待-通知面、HighWatermarkSemaphore 条件面、SequentialTaskExecutor CAS 协议、LocalResourceLockManager 其余三处判定、ErrorMessageManager COW 纪律、ReflectionManager tempLoaders、EvalBackendRegistry、各 static final 注册表、ICache default 契约、RoundRobinSupplier（resize 已由 C1 立案，close 幂等）
- 盲区自评：(a) ModelLoader 的具体 loader 实现（如 XLibModelLoader）仅抽查了递归入口证据，未逐个审计各文件类型 loader 的内部锁；(b) ResourceDependencySet/ResourceDependsManager 的内部锁面未展开（collectDependsTo 的 ThreadLocal 面归属 C1/C3）；(c) ehcache/EhcacheCache 为零调用方适配器，仅核对了语义裁定注释未深审；(d) pool 包仅接口无实现，真实连接池实现在其他模块，不在本单元。

---

## 维度复核结论（独立复核子代理，2026-10-17）

### 复核方法与范围

独立复核（与初审/深挖代理相互独立，怀疑姿态）：对 C2-001~022 全部 22 条发现逐条亲自打开 file:line 重核证据（行号漂移以 live code 为准修正）、重走触发时序、按判级口径复核严重度；并做历史审计对照（本阶段解除盲审：`ai-dev/audits/cra-index.md` WI 1.1–1.7 台账、同目录 C1 轮文件、git log 修复历史核实）。历史对照结论：本单元 22 条发现均不在 WI 1.1–1.7 台账中（零重复立项成立），全部为「新发现（本轮审计）」；两处与早前 check-findings 修复轮存在同文件先例关联（C2-016 flush 面、C2-018 兄弟字段，见各条复核状态）。

### 总裁定

| 裁定 | 数量 | 明细 |
|---|---|---|
| 保留 | **22** | P1×1（C2-001 维持）、P2×13、P3×8 |
| 降级 | 0 | — |
| 驳回 | 0 | — |

标注修正 2 处（不改判级）：**C2-015** 补「当前全仓无生产调用方」条件化标注（复核亲核仅类自引用，与 C2-004/C2-010 同判例，初审判词漏注）；**C2-016** 补「nop-api-core 边界面，Phase 6 移交」标注（见下）。证据勘误 1 处（不改裁定）：C2-001 引用的全局函数名实为 `loadDslModel`（GlobalFunctions.java:439 调 requireComponentModel），初审「std:requireComponentModel」名实不符，file:line 与证据链不受影响。

### C2-001 终裁（特别复核指令）

**终裁：维持 P1。**

- **a) 持监视器加载**：属实。`ResourceCacheEntry.getObject` L179 `synchronized(this)` 内 L186 调 loadObject→`loader.loadObjectFromPath`（完整解析+编译在监视器内）；reloadObject（L227，synchronized）同链。
- **b) 跨 entry 嵌套路径**：三条路径全部实证（强于初审的证据面）——①任意 DSL 模型加载必经 `AbstractDslParser.doParseResource`→`DslNodeLoader.loadFromNode`→`SchemaLoader.loadXDefinition`→RCM（加载 A 的 entry 监视器内获取 schema xdef 的 entry 监视器）；②DSL 模型 `x:config` 的 `c:import from=*.xlib` 经 `applyCompileConfig`→`ImportTagCompiler.parseTag:68`→`XplCompiler.loadLib:448`→`XplLibHelper.loadLib:66`→RCM（**无环守卫**，scope.addLib 仅去重注册不阻止加载）；③pre/post-parse 钩子与用户脚本（`GlobalFunctions.loadDslModel`→`requireComponentModel`）在加载期监视器内执行任意模型加载。
- **c) 可达性**：循环模型依赖真实可配（import/脚本均为用户内容，解析层无环检测）；触发时序逐步可信——T1 持 entry(A) 加载 A→import B→阻塞于 entry(B)；T2 持 entry(B) 加载 B→import A→阻塞于 entry(A)；两监视器均为普通 synchronized 无超时。**补强事实（复核新证）**：单线程遇循环依赖经可重入监视器无限重载直至 StackOverflowError（Error 逃出 getObject 的 catch(Exception)）——即当前代码对循环模型依赖**不存在任何干净报错路径**，单线程形态是 SOE、双线程形态是永久死锁，佐证「用户配置错误→灾难性失效」的 P1 定性。
- **d) 三节点关系（供 Phase 6）**：**C2-001 与 C2-017 为同一根因族的两个节点集**，同属一张锁序图（E1 = entry↔entry；E4/E5 = 经 LazyFn 监视器的 entry↔LazyFn↔entry 与 LazyFn↔LazyFn），触发类相同（循环 import/模型依赖 + 并发），**必须作为同一变更集修复**（统一不变式：监视器内只做同 key 去重+占位，加载/编译在锁外）——只修 entry 层则 E4/E5 残余闭合，与深挖轮终止信号判定一致。**C2-019 不属于该死锁族**：同属「锁内重活」纪律族（与 C2-003 同校准），但 CLASS_MODELS 键空间无自环、ClassModelLoader 嵌套链沿超类偏序可证无环、getClassModel 不再入模型 entry——纯延迟面缺陷，P2 独立成立，修复可独立排期。
- **e) 判级**：P1 门槛（具体触发时序含锁获取顺序、每步可信）满足；前提为单一可配条件（循环依赖）+ 默认并发（checkChanged 默认 true，CoreConfigs L86-88 亲核），非 ≥3 罕见条件合取。维持。

### C2-016 边界处置（特别复核指令）

**处置：维持条目 + 标注「nop-api-core 边界面，Phase 6 移交」（三选一中的第一项）。**

理由：①缺陷经逐行亲核成立（drain→await 窗口 + enqueue 谓词 syncing>0 过窄 + flush beginProcess 失败静默放弃，三重修复机会全错过；endProcessIfIdle 注释自证同窗）；②该类承载全平台 `FutureHelper.syncGet`/`BaseContext` 路由（BaseContext L312 亲核），驳回丢失已验证缺陷、降 CL 低估已证实严重度，均不可取；③修复触及 `io.nop.api.core.*` 公共 API——按 AGENTS.md 属 plan-first 保护区，修复工作必须归入 Phase 6 的 api-core 治理通道，而非本单元直接修复。现状标注：flush 面 `endProcessIfIdle` 系早前修复轮已落地（commit 5ee798e02e），本条为该修复的剩余缺口（syncGet 面未修）。

### CL 线索处置（5 条）

| CL | 处置 | 复核依据与合并裁定 |
|---|---|---|
| CL-004（SequentialTaskExecutor.execute 丢弃 offer 返回值） | **确认（与 C1 轮 CL-002 同一站点，合并裁定）** | execute:81 返回值丢弃亲核；OverflowBlockingQueue 满队列 offer=false 语义亲核；C8 形态归属正确。C1 轮 CL-002 已由 C1 复核确认（execute:81 亲核、全仓零生产调用方）——两编号指向同一缺陷，Phase 6 按 C1 CL-002 台账合并处置，本条不重复立项 |
| CL-005（BindScheduledExecutor.scheduleAtFixedRate 转发拒绝→JDK 周期任务静默停摆） | **确认** | L65-69 亲核：下游 execute 抛 RejectedExecutionException 落在 timer 线程周期任务体内→JDK 取消后续全部触发且 Future 无人读；scheduleWithFixedDelay 重写版（L72+ catch+completeExceptionally）同文件对照亲核。与 WI 1.6 C8-001↔nop-job C4-003（使用方链式再调度停摆）为**同设施不同缺陷面**（本条为设施转发面），独立成立 |
| CL-006（BlockingSourceConsumeTask 整批静默丢弃） | **确认（与 C1 轮 CL-003 同一站点，合并裁定）** | drainTo 出队后 consumer.acceptList 失败仅 LOG.error 继续（L59-74）亲核；C1 轮 CL-003 已由 C1 复核确认（含中断杀循环面）——合并裁定同 CL-004 |
| CL-019（ExecutionContextImpl.onBeforeComplete 丢失注册 + ServiceContextImpl 不对称锁） | **确认（移交 C1 维度台账）** | complete() 首检 done 与置 done 分离为两个同步块、fireBeforeComplete 居间（L109-128）亲核——T2 在窗口内注册的回调既不在本次快照、后续又遇 IllegalStateException，静默丢失成立；ServiceContextImpl 写 synchronized/newChildContext 无锁读（L121-132）亲核。请求域对象归属 C1 镜头更准，按条目自身建议移交 |
| CL-020（IEvalScope.computeIfAbsent 默认实现锁内任意 builder） | **确认（纪律缺口，一行 Javadoc 契约补注）** | default 方法 synchronized(this) 承载任意 builder（L45-54）亲核；环闭合两前提（跨线程共享 scope、builder 再入他 scope）均为设施外使用形态——维持不立案登记，修复=对照 ICache.computeIfAbsent 补契约注释 |

### F3 完备性复核（独立 grep）

初审/深挖枚举经独立重查**确认完备**：①await 面——4 模块 main 源 `.await(/awaitNanos` 恰 4 处（LRLM L178、HighWatermarkSemaphore L57、BaseTestCase L138、ContextTaskQueue L93），`signal(All)` 恰 3 处，裸 `Object.wait` 0 处，与 R2-2 表逐一对应；②`ReadWriteLock/StampedLock` 0 命中，R2-4 缺位面结论成立；③B 面 synchronized 站点扫描（resource/reflect/exec/compile）未发现漏立站点。补全 3 处核验记录（均不立案，无环/单锁/轻量，不改判级）：`AutoCloseZipEntryResource.getEntry`（synchronized 内开 ZipFile，per-resource 首触一次，同 ClassModel.getBeanModel「首触串行化可接受」判例）；`InMemoryTextResource` cloneInstance/writeText（单锁无嵌套）；`XplTagLib.init()`（xlib 加载期 entry 监视器内执行，仅构建 XplLibTagCompiler 对象不编译标签，无重活）。

### 争议点与遗留

1. **C2-001 命名勘误**：初审「std:requireComponentModel」实为 `loadDslModel`（已如上注明），不构成驳回事由。
2. **C2-017「entry 监视器内 parseTag」的常规性**：复核确认可达路径（加载期 compileTagBody/x:config import），但比 C2-001 的「任意 DSL 加载必经 schema 加载」多一层「模型内容含宏标签/imports」条件——维持 P2 不升 P1 与该条件差一致。
3. **C2-016 与判级口径**：按「api-core 设施被全平台并发调用」口径 P2 成立（窗口窄+后果可达永久挂死+三重修复机会错过）；若 Phase 6 认定 promise 完成依赖滞留任务的编排形态为常态，可升 P1（条目已注明）。
4. **审计单元边界**：C2-016（nop-api-core）为两轮中唯一越出 A/B 面的立案条目，已以移交标注收口；后续 Phase 6 对账时该条应出现在 api-core 治理清单而非 nop-commons/nop-core/nop-xlang 修复清单。

## 最终保留项（复核后）

| ID | 终裁严重度 | 现状标注 | 一句话摘要（复核确认） |
|---|---|---|---|
| C2-001 | **P1（维持）** | 新发现 | ResourceCacheEntry 持 entry 监视器加载模型，跨 entry 嵌套三路径实证（schema xdef/x:config import/脚本），循环依赖+两线程反序可达无超时死锁；单线程形态为 SOE |
| C2-002 | P2 | 新发现 | 快路径无锁读 vs clear 锁内销毁，use-after-destroy 窗口（热重载/租户清理常态操作） |
| C2-003 | P2 | 新发现 | transformed CHM bin 锁内 transformer.transform，同 bin 串行化+递归 compute 面 |
| C2-004 | P2（条件化：无生产调用方） | 新发现 | CycleSynchronizer 持 this 锁执行回调：自死锁/跨实例环/异常撕裂 |
| C2-005 | P2（条件化：双执行面需多线程调用方） | 新发现 | debounce 四段非原子双执行 + holder 晚绑定 NPE/泄漏 |
| C2-006 | P2 | 新发现 | DefaultScheduledExecutor destroy 置 null 与 6 处 checkEnabled-then-use TOCTOU→NPE |
| C2-007 | P2 | 新发现 | AbstractResourceLock 族 Lock 中断契约违约（lockInterruptibly 无 throws、中断转 NopException）+ unlock 纪律不对称 |
| C2-008 | P2（理论风险） | 新发现 | tryLockWithLease 过期判定绕过 latch 监视器，违反自声明不变式（当前 volatile 收敛） |
| C2-009 | P2（理论风险） | 新发现 | DictProvider/ModuleManager registerInstance 单例非 volatile，unsafe publication |
| C2-010 | P2（条件化：无生产调用方） | 新发现 | DedupBlockingQueue.send 异常路径不回滚 map→key 永久墓碑（offer 有回滚对照） |
| C2-011 | P3 | 新发现 | OverflowBlockingQueue DROP_ELDEST offer/poll 两段内锁非原子（语义扰动） |
| C2-012 | P3 | 新发现 | ResourceContentCache 文件 I/O 在 Caffeine bin 锁内 |
| C2-013 | P3 | 新发现 | 注册表 check-then-put 非原子族（RCM/ReflectionManager；同文件有 putIfAbsent 正确范本） |
| C2-014 | P3 | 新发现 | ResourceTenantManager.isSupportTenant 无同步惰性初始化（幂等，仅性能）+ ThreadLocal 残留注记 |
| C2-015 | P2（复核补注：当前无生产调用方） | 新发现 | ReactiveObjectMap 失败条目留存→永久毒化（复核坐实：未收集依赖 getDepends()=null→isChanged 恒 false） |
| C2-016 | P2（复核补注：nop-api-core 边界面，Phase 6 移交） | 新发现（flush 面先例修复 commit 5ee798e02e，syncGet 面遗留） | ContextTaskQueue.syncGet drain→await 丢失唤醒窗口：enqueue 谓词 syncing>0 过窄 + flush 兜底静默放弃 |
| C2-017 | P2 | 新发现 | LazyCompiledFunction.compile 持自身监视器完整编译+import 求值+宏执行，模型 entry 监视器链内触发——E4/E5 可闭合环（C2-001 同族节点，须同变更集修复） |
| C2-018 | P3 | 新发现（兄弟字段 volatile 系早前修复轮 aab58b47be 所补，deps 为遗漏成员） | CompiledTag.deps 非 volatile：监视器内写、isChanged 无锁读 |
| C2-019 | P2 | 新发现 | XLangSemantics.CLASS_MODELS bin 锁内类加载+ClassModel 全量构建（同锁内重活纪律族、无环非死锁族，独立修复） |
| C2-020 | P3 | 新发现 | 锁内解析/构建低频族：XDef 解析/枚举字典/模板文件头 I/O |
| C2-021 | P3 | 新发现 | DefaultVirtualFileSystem 监视器内配置 I/O+zip 构建；destroy 与无锁读者 zip-closed 窗口（注释自证修一半） |
| C2-022 | P3 | 新发现 | DefaultTaskExecutionQueue bin 锁内 executor.execute 提交（默认面无缺陷，CallerRuns 注入面递归 compute） |

CL 台账（不计入 22 条）：CL-004 确认（并 C1 CL-002）、CL-005 确认、CL-006 确认（并 C1 CL-003）、CL-019 确认（移交 C1）、CL-020 确认（Javadoc 契约补注）。

**复核统计**：22 条全部保留（P1×1 维持、P2×13、P3×8），降级 0、驳回 0；标注修正 2（C2-015 无调用方条件化、C2-016 api-core 边界移交）；证据勘误 1（C2-001 函数名，不影响裁定）；F3 复核确认完备（补 3 条不立案核验记录）；锁序图终版 E1/E2/E3/E4/E5 维持深挖轮结论。
