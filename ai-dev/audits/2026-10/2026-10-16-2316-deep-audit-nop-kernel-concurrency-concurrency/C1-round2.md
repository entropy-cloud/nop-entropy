# C1 共享可变状态与可见性 —— nop-kernel 并发设施深审（第 2 轮盲审深挖）

| 项 | 值 |
|---|---|
| 审计单元 | 平台并发设施自身实现：A 面 `nop-kernel/nop-commons/src/main/java/io/nop/commons/{concurrent,cache,pool}`（85f/7,550 行）+ B 面 nop-core / nop-xlang 全局模型缓存与注册表单例（基线 B-face enumeration 46 候选的相关子集） |
| 仓库 | `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`，HEAD 8c13908bfd） |
| 本轮输入 | 第 1 轮报告 `C1-shared-mutable-state.md`（C1-001~013 + CL-001~003）；基线 `_tmp/cra-baseline-nop-kernel-concurrency.txt`；live code 逐文件审读 |
| 盲审纪律 | 本轮为盲审：**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件，**唯一例外**为本维度第 1 轮文件 `C1-shared-mutable-state.md`（任务指定必要输入）；未读取同目录其他维度文件（C2~C9）。 |
| 本轮目标 | ① C1-001 失效面扩展（「resize/替换期间旧资源仍被读」族全 grep + 家族矩阵）；② 非 volatile 单例槽位族 F3 补全（13 处之外）+ this-escape 构造器全扫；③ check-then-act 竞态全扫（C1-003/004 同族）；④ B 面注册表写路径可见性 + @Volatile/@Concurrent 注解语义核查；⑤ 发布安全专项（非 final 字段跨线程发布） |
| 判级口径 | 与第 1 轮一致：平台设施被全平台并发调用，设施内部数据结构必须自证线程安全；「默认用法」门槛极低；竞态发现必须写出具体 T1/T2 交错时序，写不出的一律 P2 并标注「理论风险，未具体化」。 |

---

## 本轮深挖执行记录（①~⑤）

### ① 「数组/map 替换 + 旧实例关闭/失效」家族矩阵（C1-001 失效面扩展）

grep 覆盖：`safeClose(`/`safeDestroy(`/`closeNext(`/`old.destroy`/`prevCache.invalidateAll`/`AtomicReferenceArray` swap/`AtomicReference` wholesale set/`IoHelper.safeCloseAll`，范围 A 面 + B 面（nop-commons/nop-core/nop-xlang src/main）。

| # | 站点 | 替换方式 | 替换期间读者可达旧实例？ | 旧实例关闭状态 | 判定 |
|---|---|---|---|---|---|
| 1 | `RoundRobinSupplier.resize` 收缩分支（:52-86） | volatile `AtomicReferenceArray` 整体换 + 锁外 `closeNext(toClose, size)` | **是**——`get()`:42 快照读旧数组后、解引用前被 (e) 关闭 | 尾段 [size,oldLen) 被关闭 | **C1-001（P1，第 1 轮已立案，本轮复核仍 live 未修复）** |
| 2 | `RoundRobinSupplier.close()`（:96-103） | 快照读后全段关闭 | 是——同型竞态 | 全部对象被关闭 | C1-001 失效面 ③（已登记） |
| 3 | `DefaultVirtualFileSystem.refresh`（:81-87） | volatile `deltaResourceStore`/`zipFiles` 先发布新、后 `safeCloseAll(oldZipFiles)` | **是**——在飞读者已在调用入口捕获旧 store，解析出的 `ZipEntryResource` 持有**共享旧 ZipFile 句柄** | 旧 ZipFile 句柄被 close | **C1-014（本轮新立案 P2）** |
| 4 | `DefaultVirtualFileSystem.destroy`（:89-96） | 换空 `InMemoryResourceStore` + `safeCloseAll(zipFiles)` | 同上（停机窗口） | 全部 zip 句柄被 close | C1-014 失效面（同族同窗） |
| 5 | `GlobalExecutors.register`（:102-107） | CHM `put` 原子换值，随后锁外 `old.destroy()` | **是**——读者可在 put 前一刻 `getExecutor` 取得旧实例 | 旧 executor 被 destroy（DefaultScheduledExecutor.destroy 还会置 `executor=null`，与 C1-003 复合为 NPE） | **C1-015（本轮新立案 P3）** |
| 6 | `GlobalExecutors.unregister`（:109-111） | CHM `remove`，**不 destroy** | 是——持有者继续使用旧实例（无关闭，反向是泄漏） | 未关闭 | CL-018（维度外线索，C6 形态） |
| 7 | `VirtualFileSystem.registerInstance`（:30-33） | plain 静态槽位，**先 `safeDestroy(_instance)` 后发布新** | **是**——destroy 与赋值之间的窗口内所有 `instance()` 调用方取得**已销毁的旧 fs**；且非 volatile，销毁后旧引用可被无限期读到 | 旧 fs 被销毁 | C1-009 失效面 ⑤（第 1 轮已立案；本轮修正归属：destroy-先于-发布 在 **registerInstance** 而非 unregisterInstance；`unregisterInstance`:36-39 只置 null 不销毁） |
| 8 | `VirtualFileSystemInitializer.destroy`（:27-33） | 先 `unregisterInstance` 再 `safeDestroy` | 否——正确顺序（摘引用后再销毁） | 销毁发生在不可达之后 | ✅ 正确先例（与 #7 同 carrier 对照，证明正确顺序已在库内存在） |
| 9 | `ResourceCacheEntry.clear`/`reloadObject`→`destroyObject`（:111-124, 227-249） | volatile `object` 换值 + `safeDestroy(oldObj)` | 是——读者在 `getObject`:177 锁外捕获旧 model 后继续使用，旧 model 可被销毁 | 旧模型被销毁 | 第 1 轮已裁：RCU 弱契约，模型 frozen/不可变前提下可接受，不立案（维持） |
| 10 | `ResourceComponentManager.clearAllCache`/`clearCache`（:139-146, 361-365） | 遍历清空缓存 | 是——并发读者触发重载 | 缓存条目语义（重载兜底） | ✅ 良性（缓存失效语义） |
| 11 | `ModuleManager.discover`/`clear`（:75,113-115） | `AtomicReference<Map>` 整体 set（build-then-publish） | 否——读者持旧 map 快照，旧 map 无关闭动作 | 无关闭 | ✅ 安全发布 |
| 12 | `LocalCache.buildCache` 的 `prevCache.invalidateAll()`（:111） | 重建式替换（若发生） | 理论上会——destroyOnRemove 时 invalidate 会销毁在飞条目 | — | **死代码**：`buildCache` 仅构造器调用（全文件唯一调用点 :48），替换路径不存在，登记备查 |
| 13 | `Cancellable`（:63-124） | synchronized 内 swap/null callbacks | 否——锁内串行化 + cancelled 双检 | 无资源 | ✅ 安全（带 WI20 修复注释） |
| 14 | `ReflectionManager.clearCache`（:226-229） | Caffeine clear | 是——在飞 introspect 重算 | 无销毁语义 | ✅ 良性（重复计算） |
| 15 | `StandardThreadPoolExecutor.contextStopping`（:179-200） | `setForcedRemainingCapacity(0)`→`setCorePoolSize(0)`→还原 多步原地变更 | 读方为 `remainingCapacity()`（stats），`forcedRemainingCapacity` 非 volatile（第 1 轮 C1-002 ⑦ 已登记） | 无关闭 | 维持第 1 轮登记，不另立案（contextStopping 单线程生命周期事件） |
| 16 | `ExecutorHelper.updateThreadPool`（:106-126） | 非「替换-关闭」族，属原地多步变更 → 归 ③ check-then-act | — | — | **C1-016（本轮新立案 P3）** |

家族矩阵结论：「先发布后关闭」的正确先例（#8、#11、#13）与「先关闭后发布」的错误站点（#7）、「发布后关闭但在飞读者未防护」的站点（#3、#5）并存；#1/#3/#5 为本轮矩阵产出的立案站点。

### ② 非 volatile 单例槽位族 F3 补全（C1-009 的 13 处之外）+ this-escape 全扫

grep `static.*_instance|INSTANCE =`（约 150+ 写点）逐一定性。**C1-009 家族新增第 14 处**：

- **[新增 ⑭] `nop-kernel/nop-commons/src/main/java/io/nop/commons/env/DnsResolver.java:17,24`** —— `private static IDnsResolver _instance = DEFAULT;` 非 final 非 volatile，带 `registerInstance`（:23-25）。读点：`NetAddress.resolve:143`（`DnsResolver.instance().resolve(host)`，网络连接路径）。全仓 grep `DnsResolver.registerInstance` 当前无生产调用方（潜伏，与 XPathProvider 同级）。→ 并入 C1-009 F3 清单，不另编号。

**非 volatile 可替换槽全清单（F3 终版，14 处）**：① ModuleManager ② ErrorMessageManager（+构造器 this-escape）③ DictProvider ④ XPathProvider（null 初值）⑤ VirtualFileSystem（register 先销毁后发布）⑥ BeanTool ⑦ JsonWhitelistChecker ⑧ XSchemaToJsonSchema ⑨ RegisterModelDiscovery ⑩ VarCollector ⑪ SchemaBasedValidator ⑫ SyncThreadPoolExecutor.INSTANCE（public 非 final，全仓无重赋值点，无状态）⑬ （第 1 轮清单内 13 处如上）⑭ **DnsResolver（本轮新增）**。

**public static 非 final 但全仓无重赋值的无状态单例（新登记子族，不立案）**：`JavaSerializer.INSTANCE`、`SimpleTextTokenizer.INSTANCE`、`DefaultThreadLocalRandom.INSTANCE`、`ArrayLengthGetter.INSTANCE`、`MissingHookExtPropertyMaker/Setter.INSTANCE`、`MapBeanObjectAdapter.INSTANCE`、`XNodeAdapter.INSTANCE`、`TreeBeanBuilder.INSTANCE`、`CascadeSelector.INSTANCE`、`XDslCleaner.INSTANCE`、`XDslExtracter.INSTANCE`、`IncludeTagCompiler.INSTANCE`、`ScriptTagCompiler.INSTANCE`、`SimpleBakResourceHistory.INSTANCE`、`DevResourceNamespaceHandler.INSTANCE`——初值经类初始化安全发布（JLS 12.4.2），无运行期写点，非 final 属规范化欠账（建议 final 化，机械改动，无并发后果）。

**volatile/final 安全组复核（抽样重验）**：JsonTool/I18nMessageManager/ResourceComponentManager/ResourceTenantManager（volatile）✅；GlobalCacheRegistry/FormatCheckers/EvalGlobalRegistry/ReflectionManager/TreeTableLayout/CodeLangMap/GlobalStatManager/DeltaJsonLoader/JsonCleaner/JsonMerger/StdDomainRegistry/ScriptCompilerRegistry/EvalBackendRegistry/EvalBackendRouter/XPathOperatorRegistry/ObjPropXmlMapperRegistry/ContinuationExecutor.INSTANCE（final）✅；GlobalMeterRegistry.s_instance（volatile）✅；ExecutorHelper.SYNC_EXECUTOR（static volatile，无运行期写点）✅。

**this-escape 构造器全扫结论**：
- 全局注册表级 this-escape 全仓**唯一**：`ErrorMessageManager()`（:114-115）→ `NopException.registerErrorMessageManager(this)`（已并入 C1-009，维持）。
- **[新登记] `ResourceComponentManager` 构造序隐蔽点**（:81）：字段初始化器 `dependsManager = ResourceTenantManager.instance().makeDependencyManager(this)` 在 `changeChecker`（:91）与五张 map（:83-89）初始化**之前**执行，构造中的 `this` 逃逸到 `TenantAwareDependencyManager`/`ResourceDependsManager`。经核两者构造器仅存储引用、不回调（`ResourceDependsManager`:46-48、`TenantAwareDependencyManager`:22-28），当前良性；但若未来依赖管理器在构造期调用 `checker.checkChanged`，将命中 null `changeChecker` NPE。属构造顺序脆弱性，登记不立案。

### ③ check-then-act 竞态全扫

grep `if ((executor|queue|task|timer|_instance|instance|config|pool) == null)` 与生命周期标志（`enabled/started/destroyed/closed`）全部站点：

| 站点 | 判定 |
|---|---|
| `DefaultScheduledExecutor.checkEnabled` ×6 + `stats()`（:100-141） | C1-003（已立案，复核仍 live） |
| `DefaultScheduledExecutor.enabled` 标志（:27，非 volatile） | 仅 `init()`（@PostConstruct 单线程）消费，运行期 `setEnabled` 无效果——非竞态而是生命周期语义缺口 → CL-017 |
| `DefaultThreadPoolExecutor`/`DefaultScheduledExecutor` 的 `init` `Guard.checkState(executor==null)` | init 双调用守卫，IoC 单线程装配 ✅ |
| `GlobalExecutors.getExecutor`:52-61 get-then-null-check | computeIfAbsent 兜底，良性双检 ✅ |
| `LocalResourceLockManager.init`:103-105 `if (timer == null)` | @PostConstruct 单线程 ✅ |
| `XPathProvider.instance()`:31 null 检查 | C1-009 家族 ④（已登记） |
| `VirtualFileSystem.isInitialized()` 与 `instance()` 的 TOCTOU（调用方：`VirtualFileSystemInitializer`:30、`IResourceChangeChecker`:15、`I18nMessageManager`:166） | 两读之间可被 `unregisterInstance` 置 null → 已检查通过但 `instance()` 抛类型化异常；窗口极窄、后果为类型化错误，随 C1-009 ⑤ 登记 |
| `ResourceCacheEntry.getObject`:176-203 check-then-double-checked-lock | synchronized 内双检正确 ✅（第 1 轮已验，复核通过） |
| `XLangSemantics.cachedGetter`:782-790 / `:916-923` get-then-put | 重复解析良性（CHM put 幂等值），✅ |
| `SequentialTaskExecutor`:132/137 `task == null` | 循环取空判断，非竞态 ✅ |
| `ExecutorHelper.updateThreadPool`:106-126 多步 TPE 重配置 | **C1-016（本轮新立案 P3）** |
| `GlobalStatManager.getSqlStats` 等 synchronized 惰性初始化 | ✅（复核通过） |

### ④ B 面注册表写路径可见性核查 + 注解语义

| 注册表 | 单例发布 | 容器与写路径 | 判定 |
|---|---|---|---|
| `ResourceComponentManager` | `_instance` volatile ✅ | 五张 map 全 CHM；`makeModelCache` computeIfAbsent ✅；`registerComponentModelLoader/Transformer` putIfAbsent 原子 + 冲突抛错 ✅；`registerComponentModelConfig`:226-229 containsKey→put 非原子（init 期，维持第 1 轮 P3 不立案） | ✅（除已立案项） |
| `EvalGlobalRegistry` | static final ✅ | `vars`/`functions` CHM，registerVariable/registerFunction 为 CHM.put 原子替换 + 覆盖日志；运行期可注销（unregister ×2）但读方经 CHM 弱一致遍历安全 | ✅ |
| `StdDomainRegistry` | static final ✅ | `domainHandlers` CHM；registerDefaults 全在构造器（类初始化安全发布）；运行期 register/unregister CHM 原子 | ✅ |
| `ScriptCompilerRegistry` | static final ✅ | `compilers` CHM，put 原子 | ✅ |
| `XPathOperatorRegistry` | static final ✅ | CHM（第 1 轮已验，抽样复核通过） | ✅ |
| `ObjPropMapperRegistry(·Xml)` | static final ✅ | CHM | ✅ |
| `EvalBackendRegistry` | static final ✅ | register/unregister synchronized ✅ | ✅ |
| `XLangSemantics` | （静态容器，无单例槽） | 三 CHM（`OBJ_FUNCTION_HANDLES`:155/`PROP_ACCESSORS`:647/`CLASS_MODELS`:1091）全 computeIfAbsent / 原子 put；`PropAccessor` 内层 getters/setters 亦 CHM | ✅ |
| `ReflectionManager` | static final + 私有构造 ✅ | 全字段 final；`introspectCache` 构造器内初始化（:135-189）；`tempLoaders` CHM；`beanModelEnhancers` COW 列表；`registeredClassModels` 等并发弱 map | ✅ |
| `ModuleManager` | plain（C1-009 ①） | `modules`/`dynamicModules` AtomicReference<Map> build-then-set 整体发布 ✅；`tenantModuleDiscovery` plain setter → 并入 C1-017 | 部分 |
| `ErrorMessageManager` | plain（C1-009 ②） | `errorCodeMappings`/`subMappings` CHM ✅；`errorMessageBuilder`/`varResolver` plain setter → 并入 C1-017 | 部分 |
| `GlobalCacheRegistry` | static final ✅ | `caches` CHM + putIfAbsent（带顺序注释）✅ | ✅ |
| `I18nMessageManager` | volatile ✅ | `enabledLocales` plain（init 单写，维持第 1 轮 P3 不立案） | ✅ |
| `DefaultVirtualFileSystem` | （实例由 VirtualFileSystem 槽位持有） | `namespaceHandlers` CHM ✅；`deltaResourceStore`/`zipFiles` volatile ✅（声明与 ：39 注释相符，实测核实） | 容器 ✅（refresh 时序 → C1-014） |

**@Volatile/@Concurrent 注解语义核查结论**：nop-kernel 全模块 **不存在** `@Volatile`/`@Concurrent`/`@ThreadSafe`/`@GuardedBy` 任何形式（自定义或第三方）的注解——此前的「注解」实为代码注释（如 `DefaultVirtualFileSystem:39` 的「通过volatile保证引用替换的安全发布」是中文注释，且其声明与注释相符，已逐字段核实）。唯一并发风格注解是 `com.google.errorprone.annotations.Immutable`（`LowestCommonAncestorFinder.Color`:60），为 error-prone 编译期检查注解，无运行时可见性语义，且仅用于嵌套值类。**结论：B 面可见性保证完全由 JMM 原语（volatile/final/CHM/synchronized）承载，无「伪注解」误导源。**

### ⑤ 发布安全专项（非 final 字段跨线程发布）

| 载体 | 发布链 | 判定 |
|---|---|---|
| `RateLimitExecutorImpl.debounce` 的 `holder[0]` | 提交后写 plain 数组元素，定时线程读 | **已立案 C1-004**（复核仍 live） |
| `ThreadPoolConfig`（全部字段 plain int/boolean/String，可变 POJO） | IoC init 线程构建 → 同线程 `newThreadPoolExecutor` 读（HB 安全）；**运行期** `refreshConfig` 线程写（`setCorePoolSize`）与其他 refresh 线程读之间无 HB | 并入 **C1-016** |
| `CacheConfig`（Caffeine 配置 POJO，plain 可变） | `ResourceLoadingCache.refreshConfig`（配置线程写 `setMaximumSize/setExpireAfterWrite`）→ `LocalCache.updateCache`（可能另一线程读同字段） | 与 C1-016 同形，随条登记失效面 |
| `LocalCache` 的 `cache/loadingCache/asyncCache/config`（非 final，构造器一次性写） | 经 CHM computeIfAbsent / volatile 单例槽 / final 字段持有者发布 → 全部创建点安全发布 ✅ | 安全 |
| `ResourceCacheEntry.object/deps/lastLoadTime` | volatile ✅（复核通过） | 安全 |
| `ResourceComponentManager.ComponentCacheEntry.model`（plain，非 volatile） | `ModelLoader.loadObjectFromPath` 内先写 `entry.model` 后经 Caffeine（CHM）put 发布 → 读方经同 CHM 读取，safe publication 成立 ✅ | 安全 |
| `ClassExtension.extendedClasses`（`List<WeakReference<Class>>`） | 全方法 synchronized，读返回拷贝 ✅（复核通过） | 安全 |
| `WeakReference` 包装其余站点 | A/B 面 grep 仅 ClassExtension 一处使用 | 安全 |
| `CycleSynchronizer.finished/processing` | volatile ✅（复合缺陷维持 C1-010） | — |
| `pool` 包（IPool/IPoolMetrics/IPooledObject/PoolConfig/PoolStats/PoolAcquireOptions） | **全包无实现类**（接口 + 值对象），C1 面为空；`PoolConfig` 为 plain 可变 POJO，一旦实现落地需按 ⑤ 纪律发布 | 登记 |

---

## 发现条目（新编号自 C1-014 起）

### [nop-kernel-concurrency-C1-014] DefaultVirtualFileSystem.refresh/destroy：发布新 store 后关闭旧 zipFiles，在飞读者持有的共享 ZipFile 句柄被关闭（zip file closed）

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DefaultVirtualFileSystem.java:36-96`（关键 ：37-40, 81-96）、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/impl/ZipEntryResource.java:25, 98-100`、触发入口 `nop-service-framework/nop-biz/src/main/java/io/nop/biz/dev/DevToolBizModel.java:46`
- **证据片段**:
  ```java
  // DefaultVirtualFileSystem
  private volatile IDeltaResourceStore deltaResourceStore;
  private volatile List<ZipFile> zipFiles;
  public synchronized void refresh(boolean refreshDepends) {
      // 先构建并发布新store，最后再关闭旧的zipFiles。
      // 如果先关闭旧zip，构建期间读线程通过volatile读到旧store并访问其中的ZipEntryResource
      // 会抛出zip file closed异常
      List<ZipFile> oldZipFiles = this.zipFiles;
      this.buildResourceStore();          // (a) 发布新 store + 新 zipFiles
      IoHelper.safeCloseAll(oldZipFiles); // (b) 关闭旧 zip 句柄
  }
  // ZipEntryResource —— VFS jar 层资源实体（FileScanHelper.scanZip:44 逐条创建）
  private final ZipFile zipFile;          // (c) 持有共享的旧 ZipFile 句柄
  public InputStream getInputStream() {
      return zipFile.getInputStream(entry);  // (d) 直接在共享句柄上读
  }
  ```
- **严重程度**: P2
- **并发场景**: DevTool 热刷新线程调用 `VirtualFileSystem.instance().refresh(true)`（`DevToolBizModel:46`，dev server 文件变更热重载），同时任意业务/请求线程正处于一次 `getResource`/模型加载调用中——该调用入口已通过 volatile 读捕获**旧** `deltaResourceStore`，正在旧 store 的 jar 层解析资源。zip 层在打包部署形态下恒存在（`DeltaResourceStoreBuilder`:191-197 对 `libPaths` 中每个 jar `newZipFile` 并 `scanZip` 生成共享句柄的 `ZipEntryResource`）。
- **触发时序**: T2（刷新线程）进入 `refresh`，(a) 发布新 store；T1（业务线程）在此之前已捕获旧 store，正沿旧 store 解析 `/nop/xxx/model.xml` 得到 `ZipEntryResource`（持有旧 ZipFile 句柄）；T2 继续 (b) `safeCloseAll(oldZipFiles)` 关闭该句柄；T1 调用 (d) `zipFile.getInputStream(entry)` → JDK 抛 `IllegalStateException: zip file closed`（若 T1 在 (d) 已打开流后被 (b) 关闭，则后续 `read()` 抛 IOException）。**refresh 注释声称的防护只覆盖「发布之后才读」的线程；「发布之前已捕获旧 store 的在飞线程」不在防护内**——恰是同类 `RoundRobinSupplier`（C1-001）的「快照读-解引用间被关闭」同型窗口。
- **现状**: 作者已在注释中显式认知本 hazard 并做「先发布后关闭」排序，但排序只能保护后发读者；`destroy()`（:89-96）同型（换空 store + 关全部 zip，停机窗口）。在飞窗口大小 = 单次 VFS 调用时长（毫秒级），触发概率随 dev 刷新频率 × 并发请求量。
- **风险**: dev 热刷新窗口内并发请求偶发「zip file closed」异常（类型化异常伪装成资源损坏）；生产部署若启用 DevTool 端点 + jar 形态 libPaths，同类窗口同样成立。无数据损坏，重试即恢复，但错误归因困难。
- **建议**: 三选一：① `refresh` 将 (b) 延迟到「旧 store 不可达」之后（如对 oldZipFiles 做代际引用计数或延迟一个刷新周期再关，`round-robin` 退役同款思路）；② zip 层资源改用 `AutoCloseZipEntryResource`（:96-141 已有按路径重开句柄的实现，先例在同包）；③ refresh/destroy 前置 `synchronized` 排空判定不可行（读者不持锁），至少在 javadoc 声明「refresh 期间在飞 jar 资源读取可能失败」契约。补「refresh 与并发 getResource」回归测试。
- **失效面枚举**: ① `refresh`:81-87——本条主体；② `destroy`:89-96——同型（停机窗口，后果轻）；③ `ZipEntryResource.getInputStream`:98-100 / `getZipFile`:60——受害读点；④ `FileScanHelper.scanZip`:44——共享句柄注入点（`new ZipEntryResource(path, file, entry)` 持 live ZipFile）；⑤ 缓解对照 `AutoCloseZipEntryResource.getEntry/getInputStream`（:96-141，按路径重开，先例在同包）；⑥ 非 zip 部署（`libPaths` 无 jar）时 `oldZipFiles` 为空列表，窗口为空——触发面依赖部署形态；⑦ `namespaceHandlers`/`deltaResourceStore` 本身 volatile 发布正确（问题不在可见性，在关闭时序）；⑧ 调用方 `DevToolBizModel:46` 为当前唯一生产 refresh 入口（grep 全仓核实）。
- **信心水平**: 确定（共享句柄持有 + 关闭时序 + 唯一 refresh 入口均为代码直读事实；JDK ZipFile.close 后 getInputStream 抛 IllegalStateException 为规范行为）
- **误报排除**: 非「volatile 字段误报」——`deltaResourceStore`/`zipFiles` volatile 正确，缺陷在**旧句柄生命周期与在飞读者解引用的复合时序**；非「已声明边界」——注释声明的防护目标（避免 zip file closed）恰恰未覆盖在飞读者；非低频路径——VFS 资源读取是全平台最高频路径之一，dev 刷新为常规操作。
- **复核状态**: 保留 P2；新发现；refresh（实际 :70-77，原报告记 :81-87 系行号漂移，已按 live code 修正）/destroy:87-94/ZipEntryResource final 共享 ZipFile + getInputStream 直用、DevToolBizModel:46 唯一 refresh 入口、DeltaResourceStoreBuilder:197 scanZip 共享句柄注入逐行亲核；synchronized 仅护刷新线程、读者走 volatile 无锁，在飞读者窗口成立；订正：AutoCloseZipEntryResource 实际位于 io/nop/core/resource/zip/ 包（非 impl 同包），类存在、先例引用有效

### [nop-kernel-concurrency-C1-015] GlobalExecutors.register：CHM 值槽位「put 后锁外 destroy 旧实例」，与持旧引用的在飞使用者构成 use-after-destroy 窗口（C1-001 家族新站点，与 C1-003 复合）

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:102-111`
- **证据片段**:
  ```java
  public static void register(IThreadPoolExecutor executor) {
      IThreadPoolExecutor old = g_executors.put(executor.getName(), executor); // (a) 原子换值
      if (old != null) {
          old.destroy();                   // (b) 锁外销毁旧实例
      }
  }
  public static IThreadPoolExecutor unregister(String name) {
      return g_executors.remove(name);     // (c) 移除但不销毁
  }
  ```
- **严重程度**: P3（潜伏——当前唯一生产调用方为一次性引导）
- **并发场景**: 引导/重注册线程调用 `register`（生产调用点：`Jdk21CommonsCoreInitializer`:12 → `VirtualThreadTaskExecutor.registerGlobalWorker()`，双次引导/同 JVM 多 app/测试套件重入时 old 非 null），同时工作线程经 `globalTimer()`/`getExecutor()`/`cachedThreadPool()` 持有旧实例提交任务。
- **触发时序**: T2 执行 (a) 换入新实例；T1 在 (a) 之前已 `g_executors.get` 取得旧实例引用、尚在 `submit` 路径中；T2 执行 (b) `old.destroy()`：若旧实例为 `DefaultScheduledExecutor`，destroy 为 `shutdownNow(); executor = null`（:88-93）——T1 随后的 `submit` 经 C1-003 的 checkEnabled-then-use 路径将 `null` 传入 `ExecutorHelper.submit` → **NPE**（而非 REE/类型化错误）；若旧实例为 `DefaultThreadPoolExecutor`（destroy=shutdown），T1 得 REE——任务提交失败且无「实例已被替换」的语义线索。窗口 = (a) 与 (b) 之间的任意长度（destroy 含 shutdownNow，非瞬时）。
- **现状**: 与 C1-001 同族（「替换后关闭旧实例，读者快照可达」），但 destroy 发生在 put **之后**（发布顺序正确），窗口纯为「持旧引用的在飞使用者」；CHM 值可见性本身正确。`unregister`（(c)）反向不对称：移除不销毁，责任甩给调用方且全仓无调用方执行销毁 → 泄漏面（CL-018）。
- **风险**: 运行期重注册（该 API 为 public 全局替换点，运维端点/未来特性可及）时的任务提交失败与 NPE；当前触发链仅存于双次引导/测试重入（引导期无并发流量，HB 兜住），故降 P3 潜伏。
- **建议**: register 语义改为「先摘后毁」或延迟销毁：将 old 先放入 retired 列表并在其上标记 retiring，`getExecutor` 不再返回 retired 实例后延迟 destroy；最小修为在 javadoc 声明「register 不得在流量期调用」契约 + 对 DefaultScheduledExecutor.destroy 的置 null 行为（C1-003 建议一并修）做防御。回归测试：并发 register 与 submit 断言无 NPE。
- **失效面枚举**: ① `register`:102-107——本条主体；② `unregister`:109-111——不销毁（泄漏，CL-018）；③ 三个 computeIfAbsent 工厂（globalTimer/cachedThreadPool/globalWorker）——重注册后被旁路，旧实例持有者不受保护；④ 生产调用方 `Jdk21CommonsCoreInitializer`:12（一次性）——当前无流量期触发链；⑤ 复合失效：`DefaultScheduledExecutor.destroy` 置 null 使本条后果从 REE 恶化为 NPE（C1-003 失效面 ⑧ 关联）；⑥ `SyncThreadPoolExecutor.INSTANCE` 经 `syncExecutor()`:98-100 返回，不经 g_executors，不受本条影响 ✅。
- **信心水平**: 确定（时序具体）；触发链现状=很可能偏弱（依赖运行期重注册的出现）
- **误报排除**: 非「CHM 原语误报」——问题不在 map 可见性而在旧实例销毁时序；非重复立案——第 1 轮仅在 C1-002 失效面 ⑥ 提及 register 会 destroy 旧实例（作为计数泄漏语境），未就「读者 use-after-destroy」立案本站点。
- **复核状态**: 保留 P3；新发现（设施本体口径）；register:102-107/unregister:109-111 亲核，生产调用方 Jdk21CommonsCoreInitializer:12→VirtualThreadTaskExecutor.registerGlobalWorker:26 亲核（一次性引导，运行期重注册触发链潜伏）；使用方关联：WI 1.6 CL-002（tcc doScan 实占 globalWorker）、WI 1.4 C4（globalWorker 默认 30 线程）——Phase 6 合并裁定跨单元关联

### [nop-kernel-concurrency-C1-016] ExecutorHelper.updateThreadPool 多步 TPE 重配置非原子 + ThreadPoolConfig POJO 非 volatile：并发 refreshConfig 可瞬态违反 core≤max 抛 IllegalArgumentException

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:106-126`、`DefaultThreadPoolExecutor.java:56-65`、`DefaultScheduledExecutor.java:61-70`、`ThreadPoolConfig.java:16-31`
- **证据片段**:
  ```java
  public static void updateThreadPool(ThreadPoolExecutor executor, ThreadPoolConfig config) {
      if (config.getMaxPoolSize() > 0 && config.getMaxPoolSize() != executor.getMaximumPoolSize()) {
          if (config.getMaxPoolSize() > executor.getCorePoolSize()) {
              executor.setCorePoolSize(config.getMaxPoolSize());   // (a) 收缩预处理
          }
          executor.setMaximumPoolSize(config.getMaxPoolSize());    // (b)
          if (config.getCorePoolSize() > 0 && ...) {
              executor.setCorePoolSize(config.getCorePoolSize());  // (c)
          }
      } else if (...) { executor.setCorePoolSize(...); }           // (d)
      if (config.getKeepAliveSeconds() > 0 && ...) { executor.setKeepAliveTime(...); } // (e)
  }
  // DefaultThreadPoolExecutor.refreshConfig
  public void refreshConfig() {
      if (config.getMaxPoolSize() > 0 && config.getCorePoolSize() > config.getMaxPoolSize()) {
          config.setCorePoolSize(config.getMaxPoolSize());         // (f) 原地改共享 config
      }
      if (executor != null) { ExecutorHelper.updateThreadPool(executor, config); }
  }
  ```
- **严重程度**: P3（理论风险，时序可具体化但触发依赖并发 refresh 分发）
- **并发场景**: 配置中心两次连续变更触发两个 refresh 线程并发对同一池执行 `refreshConfig`（IConfigRefreshable 契约，`BindScheduledExecutor.refreshConfig`:42-44 亦透传）；以及 refresh 线程写 `config` 字段与另一线程读（(f) 与 (a)-(e) 跨线程）。
- **触发时序**: 目标池当前 core=10/max=10。T1 携新配置 core=2/max=2（收缩）：执行 (a) `setCorePoolSize(2)`（此刻 core=2,max=10）；T2 携新配置 core=5/max=5 插入：`config.max(5) != executor.max(10)` 成立且 `5 > executor.core(2)` → (a') `setCorePoolSize(5)`、(b') `setMaximumPoolSize(5)`、(c') core==5 跳过；T1 恢复执行 (b) `setMaximumPoolSize(2)`——此刻 executor.core=5 > 2，`ThreadPoolExecutor.setMaximumPoolSize` 抛 `IllegalArgumentException`（要求 max≥core），refresh 半途而废：池停留在 T2 的 core=5/max=5，配置既非 T1 亦非 T2 的目标值，异常向配置刷新监听器传播。`config` POJO 字段全 plain（`ThreadPoolConfig:16-31`），T1/T2 还可能各自 (f) 交叉写同一 config 对象，读到撕裂的 core/max 组合进一步放大窗口。
- **现状**: TPE 单方法调用内部同步，但 (a)-(e) 五步序列无原子性；(f) 直接原地变更被池共享的 config 对象（`getConfig()` 还将其暴露给监控路径）。收缩排序意图正确（先 core 后 max），缺的是并发 refresh 间的互斥。
- **风险**: 运行期调参（`nop.vfs` 线程池、globalTimer/globalWorker 的 maxPoolSize 均可配置刷新）在连续变更/多配置源场景下偶发失败 + 半应用状态；无数据损坏，属可用性/一致性缺陷。
- **建议**: refreshConfig 方法级 synchronized（重配置低频，锁代价可忽略）；或快照式——先把 config 字段读到不可变局部结构再按序应用；ThreadPoolConfig 若定位为值对象应改 immutable + 整体替换引用（volatile）。
- **失效面枚举**: ① `updateThreadPool`:106-126——本条主体；② `DefaultThreadPoolExecutor.refreshConfig`:56-65 与 `DefaultScheduledExecutor.refreshConfig`:61-70——(f) 原地写共享 config 同族；③ `BindScheduledExecutor.refreshConfig`:42-44——透传到 ①；④ `SyncThreadPoolExecutor.refreshConfig`:23-25 空实现——不受影响 ✅；⑤ `CacheConfig` 同形（`ResourceLoadingCache.refreshConfig:100-107` 写共享 POJO + `LocalCache.updateCache` 读，Caffeine policy.set 线程安全故后果仅撕裂应用，随条登记不另立案）；⑥ 触发前提「两个 refresh 并发」取决于配置分发器线程模型（本轮未在 nop-kernel 范围内找到并发分发证据）——按理论风险 P3。
- **信心水平**: 竞态存在=确定（(a)/(b) 间可插入即可复现）；生产触发链=未具体化（分发线程模型未证）
- **误报排除**: 非「TPE 原语误报」——单方法均线程安全，报告的是跨方法五步序列的复合原子性；非「低频路径粗锁豁免」场景——已如实按 P3 理论风险立案并注明前提未证。
- **复核状态**: 保留 P3（触发时序事实修正）；新发现；本机 JDK 26（Zulu 26.0.1）实测：`setCorePoolSize` 已放宽（core>max 不抛 IAE）、`setMaximumPoolSize` 严格要求 max≥core（实测 IAE）——据此原 T1/T2 交错时序不成立：收缩场景 (a) 条件 `newMax>currentCore` 为 false 不触发，T1 直接在 (b) 抛 IAE；真实缺陷更简单且确定性：**单线程**收缩「当前 core>新 max」的池（core=max=10→(5,5)）即在 (b) 必抛 IllegalArgumentException，updateThreadPool 无法收缩此类池；(f) 原地改共享 config POJO（DefaultThreadPoolExecutor.refreshConfig:55-65）的并发交叉写作为并发面残余成立；同站点历史关联：check/nop-commons.md P3「newScheduledExecutor 改写调用方 config」暂缓裁定（refreshConfig 承重约定），修复应一并设计

### [nop-kernel-concurrency-C1-017] 实例级可替换组件槽位族（非 static）：setter 注入的协作者字段全 plain，运行期重注入对热路径读方无可见性保证

- **文件**: 族代表：`nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/ResourceComponentManager.java:91,148-150`、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/tenant/ResourceTenantManager.java:87-94`、`nop-kernel/nop-core/src/main/java/io/nop/core/module/ModuleManager.java:57-64`、`nop-kernel/nop-core/src/main/java/io/nop/core/exceptions/ErrorMessageManager.java:56-63`
- **证据片段**:
  ```java
  // ResourceComponentManager —— checkChanged:158 / resolveResource:526 每次模型加载热路径读
  private IResourceChangeChecker changeChecker = DefaultResourceChangeChecker.INSTANCE; // (a) plain
  public void setChangeChecker(IResourceChangeChecker changeChecker) {
      this.changeChecker = Guard.notNull(changeChecker, "changeChecker");               // (b) 无同步写
  }
  // ResourceTenantManager —— getUsedTenants/clearForTenant/getTenantResourceStore 读
  private ITenantResourceProvider tenantResourceProvider;                              // (c) plain
  // ModuleManager —— getEnabledModuleMap(includeTenant=true) 每次模块资源解析读
  private ITenantModuleDiscovery tenantModuleDiscovery;                                // (d) plain
  // ErrorMessageManager —— 全平台异常→消息转换热路径读
  private IErrorMessageBuilder errorMessageBuilder;                                    // (e) plain
  private IErrorMessageVarResolver varResolver;                                        // (f) plain
  ```
- **严重程度**: P3（理论风险：写一次读多，运行期重注入当前无生产触发链）
- **并发场景**: IoC/测试引导线程调用 setXxx 重注入协作者（平台单测惯例：`TestTenantIsolationBinding:154-205` 对 `setTenantResourceProvider` 做 save/swap/restore），工作线程经 `instance()` 持有管理器后并发读该字段（模型加载每次读 `checkChanged`；异常处理每次读 `errorMessageBuilder`）。
- **触发时序**: T1（引导/测试）执行 (b) plain store 新 checker；T2（工作线程）在 `checkChanged`:159 读 `changeChecker`——无 volatile/synchronized 构成 HB，JMM 不保证 T2 见到新引用，可持续使用旧 checker（旧实现若已随旧容器销毁，则为「已销毁协作者继续被调用」）；与 C1-009 同构，只是槽位从 static 降为实例字段——**第 1 轮 C1-009 的静态槽位清单之外的第二个维度**。
- **现状**: 常规启动路径「容器装配完成 → 提交工作」的事件屏障兜住（与 C1-009 同一豁免逻辑）；真实窗口在测试套件运行期 swap/restore 与 dev 热装配。全仓 grep `setChangeChecker` 无生产调用方，`setTenantResourceProvider` 仅测试调用。
- **风险**: 潜伏；一旦出现运行期重注入特性（多租户 provider 动态切换、checker 插件化），按上述时序在热路径复现旧实例滞留。无数据损坏。
- **建议**: 族内字段全部 volatile 化（写一次读多，代价可忽略，机械改动）；或在接口 javadoc 声明「仅容器初始化期注入」契约并加断言。
- **失效面枚举**: ① `ResourceComponentManager.changeChecker`——读点 `checkChanged`:159、`resolveResource`:526（每模型加载必经）；② `ResourceTenantManager.tenantResourceProvider`——读点 `getUsedTenants`:142-147、`clearForTenant`:150-155、`getTenantResourceStore`:210-214（租户模式下每次租户 store 解析）；③ `ModuleManager.tenantModuleDiscovery`——读点 `getEnabledModuleMap`:166-173；④ `ErrorMessageManager.errorMessageBuilder`/`varResolver`——读点为全平台异常消息构建路径（:145-165 邻域）；⑤ 对照组：同文件静态槽 `_instance` 已 volatile（RCM:71/RTM:46）——证明作者掌握正确模式，实例槽为遗漏层；⑥ 生产写点：均无（仅测试 `TestTenantIsolationBinding:155,205` 与潜在 IoC 装配）——理论风险定级依据。
- **信心水平**: 竞态存在=确定；触发链=理论（写点当前非运行期）
- **误报排除**: 非「C1-009 重复立案」——C1-009 清单全部为 static 槽位，本条为实例字段族，载体与修复面不同；非「IoC 标准模式豁免」——字段 final 化或 volatile 化与 IoC setter 注入完全兼容，无平台约束强迫 plain。
- **复核状态**: 保留 P3（事实修正）；新发现；RCM:91 plain + setter:148-150（读点 :159/:526）、ModuleManager.tenantModuleDiscovery plain（:57 邻域）、EMM errorMessageBuilder/varResolver plain、RTM:90 逐行亲核；事实修正：`setTenantResourceProvider` 存在生产写点 nop-dyn-service DynCodeGen.java:92/100（设 provider 后复位 null，动态代码生成路径），非原报告所称「仅测试调用」——触发链略强于原判断但非请求热路径常态，P3 维持

---

## 维度集外线索（续，不立案展开）

### [nop-kernel-concurrency-CL-017]（维度集外线索，不立案展开）DefaultScheduledExecutor.setEnabled 运行期无效，配置中心禁用全局 timer 不生效

- **形态归属**: C6 生命周期/C7 长运行语义缺口，非 C1。
- **严重度候选**: P2/P3（`enabled` 仅 `init()`:75-77 消费；运行期 `setEnabled(false)`（:33-35）对已启动 executor 无任何效果，无 stop/re-start 通道。若运维/配置路径以 enabled 作为 timer 开关，将静默失效）。
- **一句话证据**: `DefaultScheduledExecutor.java:27`（字段）+ `:75-77`（唯一消费点在 init）——`grep` 全仓无运行期 `setEnabled(false)` 调用方，属潜伏契约缺口。

### [nop-kernel-concurrency-CL-018]（维度集外线索，不立案展开）GlobalExecutors.unregister 移除不销毁，线程池泄漏路径与 register 自动销毁不对称

- **形态归属**: C6 资源生命周期，非 C1。
- **严重度候选**: P3（`unregister`:109-111 仅 `g_executors.remove(name)`，返回值含销毁责任但全仓无调用方、无销毁——若被使用，旧池线程泄漏至 JVM 退出；与 `register`:104-106 的自动 `old.destroy()` 形成不对称契约）。
- **一句话证据**: `GlobalExecutors.java:109-111` + 全仓 `GlobalExecutors.unregister` 调用方为 0。

---

## 结论与总计（第 2 轮）

**本轮新增发现总计：4 条（P2×1，P3×3）+ CL 线索 2 条（CL-017/018）。前轮 13 条复核：全部仍 live（未修复），无重复立案。**

| ID | 一句话 | 严重度 | 信心 |
|---|---|---|---|
| C1-014 | DefaultVirtualFileSystem.refresh 发布新 store 后关旧 zip 句柄，在飞读者经共享句柄 ZipEntryResource 读 → zip file closed（DevTool 热刷新 × jar 层部署） | P2 | 确定 |
| C1-015 | GlobalExecutors.register put 后锁外 destroy 旧 executor，持旧引用在飞提交方 use-after-destroy（与 C1-003 复合为 NPE）；unregister 不销毁（→CL-018） | P3 | 确定（触发链潜伏） |
| C1-016 | updateThreadPool 五步 TPE 重配置非原子 + ThreadPoolConfig POJO 非 volatile，并发 refreshConfig 瞬态 core>max → IllegalArgumentException + 半应用 | P3 | 竞态确定/触发链未具体化 |
| C1-017 | 实例级可替换协作者槽位族（changeChecker/tenantResourceProvider/tenantModuleDiscovery/errorMessageBuilder/varResolver）全 plain，重注入无可见性保证（C1-009 的实例字段维面色） | P3 | 竞态确定/理论风险 |

**五条深挖线的终止信号核验**：
1. **①家族矩阵闭合**：「替换+关闭」模式全 grep（safeClose/safeDestroy/closeNext/safeCloseAll/AtomicReference swap）产出 16 行矩阵，立案站点穷尽（#1/#3/#5），其余为安全先例或已登记项。
2. **②单例槽位枚举闭合**：`static.*_instance|INSTANCE` 约 150+ 写点全量定性，C1-009 家族补至 14 处（DnsResolver），无 final 无状态子族 15 处登记不立案；this-escape 全扫仅 ErrorMessageManager 一处（已立案）+ RCM 构造序脆弱点（已登记）。
3. **③check-then-act 闭合**：null 检查/生命周期标志站点全扫（见执行记录表），仅 updateThreadPool 一处新失守（C1-016），其余为已立案项或正确先例。
4. **④B 面写路径闭合**：13 张注册表/管理器逐一核查，容器全 CHM/COW/AtomicReference/锁保护；@Volatile/@Concurrent 类注解确认为**不存在**（唯一 @Immutable 为 error-prone 编译期注解），可见性全部由 JMM 原语承载且与注释相符。
5. **⑤发布安全闭合**：非 final 跨线程发布站点全查，唯一实锤（holder[0]）已在前轮立案；LocalCache/ComponentCacheEntry/WeakReference 包装发布链均核实安全。

**终止判定**：本轮新增最高严重度为 P2（C1-014，dev 触发面），无新增 P0/P1；五条深挖线全部闭合且第 1 轮 13 条无修复、无需第 3 轮排雷。**建议 C1 维度审计终止**（合计 17 条：P1×1、P2×10、P3×6），C1-014~017 与 C1-001 家族修复时应合并处理「旧实例生命周期与在飞读者」这一统一根因。
