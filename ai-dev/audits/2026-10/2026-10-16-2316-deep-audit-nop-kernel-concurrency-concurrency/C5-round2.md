# C5 内存有界性 —— nop-kernel 平台并发设施专项审计（第 2 轮深挖，盲审）

## 审计单元

- **模块**：nop-kernel（A 面：`nop-kernel/nop-commons/src/main/java/io/nop/commons/{concurrent,cache,pool}`；B 面：nop-core / nop-xlang 全局模型缓存与注册表单例）
- **仓库**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **维度**：C5 —— 内存有界性（缓存/队列/注册表容量上界与淘汰机制；无界累积结构；TTL/上限声明的实现一致性；弱引用/强引用持有面）
- **本轮输入**：(1) 本维度第 1 轮报告 `C5-memory-bounds.md`（C5-001~007、CL-008~010、B 面 25 项三列判定表）；(2) 基线 `_tmp/cra-baseline-nop-kernel-concurrency.txt`（A C1 static final collections 层 grep 输出 + B-face enumeration 46 files / 5,945 行）；(3) live code 全量复读（本轮新核读 20+ 文件）。
- **盲审纪律声明**：本轮为盲审轮次。除上述第 1 轮本维度文件、方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md`（附录 A 格式）与基线文件外，**未读取** `ai-dev/audits/`（含同目录其他维度文件）、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件。全部结论基于当前 live code。
- **反虚构预注对齐**：第 1 轮已立案 7 条（C5-001~007）不重复；C4 轮池配置死旋钮族、ResourceTimestampCache 死代码、ThemeNamespaceHandler 显式无界、ReactiveObjectMap 死代码已知——同族新站点立案，旧站点仅在 F3 枚举登记。

## 本轮目标（指令 ①~⑤ 执行记录）

1. **① C5-001 失效面扩展**：对基线 B-face enumeration 全集 + 全 nop-core/nop-xlang `static final CHM/Map` 站点（附录 C 第③层 grep 全量复现）中第 1 轮 25 项判定表**未覆盖**的站点逐一补三列判定（见「B 面补充判定表」）；XLangSemantics 三 CHM 键生命周期量化（见「C5-001 键生命周期量化」节）。
2. **② LocalCache 配置面全枚举**：全量复核 `LocalCache.newCache`/`newConfig`/`new LocalCache`/`new MapCache` 站点（grep 全量，含 nop-kernel 全部子模块），产出「LocalCache 配置终表」。
3. **③ 弱引用/软引用使用面**：`WeakReference|SoftReference|weakKeys|weakValues|newConcurrentWeakMap|WeakHashMap` 全仓 src/main grep，逐站点判定 → 发现 **weakKeys 被值持键失效族**（C5-008/C5-009，本轮最重要新发现）。
4. **④ listener/list 累积**：`CopyOnWriteArrayList|CopyOnWriteArraySet|LinkedList|ArrayDeque|ArrayList|List|Set` 字段型累积结构全量筛查，全局单例容器逐一判定（见「列表型累积判定表」）。
5. **⑤ CL-024 交叉核对**：`GlobalExecutors.register/unregister` 全仓调用面 + nop-rpc-simple/nop-stream-runtime per-client/topic 池创建路径核实 → g_executors 界面量化结论（见「CL-024 交叉核对」节）。

---

## ① B 面 static CHM/Map 补充判定表（第 1 轮 25 项之外的全部站点）

第 1 轮判定表基于 B-face enumeration 25 项；本轮对 `static final Map/Set + new ConcurrentHashMap/HashMap` 与实例级全局注册表做**反向全量核查**，下列站点为第 1 轮未逐一判定者。三列判定（容量/TTL/淘汰）：

| # | 站点（字段） | 容量 | TTL | 淘汰/清理机制 | 判定 |
|---|---|---|---|---|---|
| 26 | `ReflectionManager.rawTypeCache`（Class→IRawType，`newConcurrentWeakMap`） | 无上限 | 无 | weakKeys **因值持键失效**（C5-008）；仅 `clearCache()`（destroy/reinitialize 路径） | **事实无界（本轮新立案 C5-008）** |
| 27 | `ReflectionManager.registeredClassModels`（Class→IClassModel 显式注册） | 显式注册基数 | 无 | register/unregisterClassModel 配对；weakKeys 同样失效但注册驱动有界 | 有界（注册驱动）；weakKeys no-op 归入 C5-008 F3 |
| 28 | `ReflectionManager.converterCache` / `registeredConverters`（Type→ITypeConverter） | 注册基数 + 自动查找缓存 | 无 | unregisterTypeConverter 配对 + converterCache 级联 remove；`clearCache()` 覆盖；ArrayTypeConverter 值持键（同族失效） | 有界（注册+reinit 清理）；ArrayTypeConverter 条目归入 C5-008 F3 |
| 29 | `ReflectionManager.beanModelEnhancers`（CopyOnWriteArrayList） | 显式注册基数 | 无 | register/unregisterBeanModelEnhancer 配对 | 有界，合格 |
| 30 | `ReflectionManager.tempLoaders`（Class→ClassModelLoader 互斥） | 在建类基数 | 无 | computeIfAbsent + `:348` remove 配对 | 有界，合格 |
| 31 | `ReflectionManager.reflectClasses` / `reflectProxies`（native-image 记录集） | 反射触及面 | 无 | `setRecordForNativeImage(false)` 时 clear；默认关闭 | 有界（默认空），合格 |
| 32 | `ClassExtension.helperMethods`（ArrayList\<IFunctionModel\>，挂于 weak-keys classExtensions 值内） | 显式注册基数 | 无 | addHelperMethods/removeHelperMethods 配对 API；classExtensions 弱键可能被 helperMethods→参数类型→Class 强链钉住（**很可能**，归入 C5-008 F3） | 有界（注册驱动），附失效疑点 |
| 33 | `EvalBackendObservation.STEADY_STATE_WARNED_KEYS`（static CHM keySet） | **显式 1024 上界**（`STEADY_STATE_DEDUP_MAX_KEYS`） | 无 | 超界回退每次 WARN（「宁噪声不无界内存」） | 有界，合格——**平台内唯一自带显式上界+注释声明的 static 集，对照组** |
| 34 | `JsonWhitelist.whitelistClasses` / `whitelistPrefixes`（static CHM-backed Set） | 显式注册基数 | 无 | 仅 add，无 remove（全局白名单语义）；注册驱动有界 | 有界，合格（功能缺陷另见 CL-025） |
| 35 | `JsonSerializerFactory.DEFAULT_FACTORY.serializers` / `JsonDeserializerFactory.DEFAULT_FACTORY.beanDeserializers`（name-keyed CHM） | 显式注册基数 | 无 | register/unregister 配对 | 有界，合格 |
| 36 | `JsonSerializerFactory.serializersForClass` / `JsonDeserializerFactory.beanDeserializersForClass`（Class-keyed weak map） | 显式注册基数 | 无 | 配对注销；值可能持键（弱键疑似失效）但注册驱动有界 | 有界，合格（归入 C5-008 F3 注记） |
| 37 | `EnumDictLoader.cache`（Class→DictBean，weak map，自动装载） | 触及枚举类基数 | 无 | 无注销 API；值 buildDictFromEnum 持枚举 Class → weakKeys 失效 | **弱键失效族，P3 立案（C5-009）** |
| 38 | `EvalGlobalRegistry.vars` / `functions`（static 持单例 CHM） | 显式注册基数 | 无 | registerVariable/unregisterVariable、registerFunction 返回 Runnable 注销句柄、registerStaticFunctions 返回 ICancellable | 有界，合格 |
| 39 | `DefaultTaskExecutionQueue.states`（taskRef→State，bean 实例） | 在途任务基数 | 无 | 任务完成 `whenComplete.finally` remove、取消即清、replaceTask 条件删 | 有界（完成即删），合格；computeIfAbsent 锁内 execute 另见 CL-028 |
| 40 | `NioFileWatchService.watchKeyMap` + `FileWatchEntry.subEntries`（watch 树） | 活跃 watch 基数 | 无 | watch 返回注销句柄 → unwatch 递归清理；close() 全清 | 有界（配对），合格 |
| 41 | `DefaultVirtualFileSystem.namespaceHandlers`（VFS 单例） | namespace 基数（协议数） | 无 | 启动注册 | 有界，合格 |
| 42 | `BaseTestCase.g_testConfigs` / `g_barriers`（static CHM，测试基础设施） | 测试名基数 | 无 | clear/reset + remove + 等待排空循环 | 有界，合格（仅测试路径） |
| 43 | `ForbiddenObjectMethods.methods`（static HashMap，public addMethod） | 启动注册 7 项 | 无 | 无 remove（禁止表语义） | 有界（启动固定）；**非线程安全可变 static HashMap** 另见 CL-026 |
| 44 | `InternalTagCompilers.tags` / `OutputModelHandlers.handlers` / `PredefinedGenericTypes`×2 / `LexicalScopeAnalysis`×2 等枚举/编译器查找表族 | 编译期常量 | 无 | 不可变或 static 初始化 | 有界，合格 |
| 45 | `TemplateFileGenerator.tplForceOverrides`（CHM，nop-codegen 实例字段） | 模板路径基数 | 无 | 生成器实例生命周期（XCodeGenerator 每次代码生成运行创建） | 有界（实例生命周期），合格 |
| 46 | `ExecutionContextImpl.attributes` / `EvalScopeImpl` / `XLangCompileScope` 底层 map | 请求/编译作用域键数 | 无 | 作用域结束整体丢弃 | 有界（短生命周期），合格 |

**结论（①）**：反向核查未发现第 1 轮 25 项之外**遗漏的无界 static CHM 站点**；但发现第 1 轮判定表 row 2「MapMaker weakKeys（GC 兜底），合格」的判定**需要修正**——该族 weakKeys 因值持键全部失效（见 C5-008），修正后的真实内存语义由「随 classloader 存活期」变为「强引用直至 `clearCache()`（destroy/reinitialize）或（introspectCache）LRU 10000 驱逐」。

### C5-001 键生命周期量化（指令 ① 专项）

对 `XLangSemantics` 三 CHM 的全仓触点核查（`grep OBJ_FUNCTION_HANDLES|PROP_ACCESSORS|CLASS_MODELS` + `grep XLangSemantics.* clear|remove|invalidate|reset` 全仓 = **0 命中**）：

- **PROP_ACCESSORS**（:647）：唯一写路径 `propAccessor()` computeIfAbsent（:655）+ `accessors.getters.put(clazz, reader)`（:789）/ `setters.put(clazz, setter)`（:923）。**Class 键永不可达删除**：无 remove/clear 调用、不在 GlobalCacheRegistry、CoreInitialization.destroy/reinitialize 的 `ReflectionManager.clearCache()+GlobalCacheRegistry.clearAllCache()` 双清理路径均不触及。累积粒度 = 历史出现过的全部 (propName, Class) 二元组——**每代动态类加载的每个被求值属性类都净增一条且永不回收**（C5-001 判定的键生命周期量化确认：三表均为「只增不减、无任何清理入口」）。
- **CLASS_MODELS**（:1091）：唯一写路径 `newInstance` computeIfAbsent（:1096）。除无清理入口外，量化出第 1 轮未写的**陈旧性缺陷**：computeIfAbsent 首 Class 恒胜——热更新产生新 classloader 后，同名 className 的 `newInstance` **永远返回旧 loader 的 IClassModel**（不仅是内存钉死，还是语义钉死：新模型实例不会被实例化）。增长形态：className 集合扩大才增长；但旧 loader 的 Class 只要不重名即被单条目永久持有。
- **OBJ_FUNCTION_HANDLES**（:155）+ `ObjFunctionHandle._cacheFunc/_cacheStaticFunc`（:29-31）：量化修正第 1 轮的表述——handle 内单槽为**最新一代自愈**语义：`pair.getLeft() == clazz` 失配即整体替换槽位（:63/:84），同 funcName 的旧 Class 在该函数下一次异类调用时即被释放。**单槽钉死量 = 2 × funcName 基数（instance/static 各一），非逐代单调**。funcName 为 String 键、基数有限。因此三表中之本表 Class 钉死风险最轻，主累积面在 PROP_ACCESSORS（逐代单调）与 CLASS_MODELS（首代永久）。

### ② LocalCache 配置终表（指令 ②，C5-002 契约守卫修复的直接输入）

生产站点全量复核：`LocalCache.newCache` 共 **17 处**（第 1 轮称 22 处 = 17 生产 + 2 工厂定义 + 3 测试；本轮以生产站点为准），`new MapCache` 生产 4 类 5 处，`LocalCacheProvider` 1 处。配置终表（maximumSize / expireAfterWrite / expireAfterAccess / weakKeys）：

| # | 缓存名（站点） | maximumSize | expireAfterWrite | expireAfterAccess | weakKeys | 值持键? | GlobalCacheRegistry 注册 |
|---|---|---|---|---|---|---|---|
| 1 | reflection-class-model-cache（ReflectionManager:189） | 10000（`nop.core.reflect.model-cache-size`） | 无 | 无 | **true**（**失效**，见 C5-008） | 是（IClassModel→getType()） | **已注册** |
| 2 | xpath-compile-cache（XPathProvider:27，nop-core） | 1000（`nop.core.xml.xpath-cache-size`） | 无 | 无 | 无 | 否 | 未 |
| 3 | xpath-compile-cache（XPathHelper:25，nop-xlang，**与 #2 同名异站点**） | 1000（硬编码） | 无 | 无 | 无 | 否 | 未 |
| 4 | simple-expr-compile-cache（SimpleExprHelper:19） | 1000 | 无 | 无 | 无 | 否 | 未 |
| 5 | def-type-parse-cache（XDslParseHelper:337，**无 loader 手工 put**） | 1000 | 无 | 无 | 无 | 否 | 未 |
| 6 | regex-compile-cache（RegexHelper:18） | 500（`nop.regex.compile-cache-size`） | 无 | 无 | 无 | 否 | 未 |
| 7 | nop-jq-jsonpath-compile（JsonPathCompiler:22） | 1000（默认 `DEFAULT_CACHE_SIZE`） | 无 | 无 | 无 | 否 | 未 |
| 8 | jdbc-sql-stat-cache（GlobalStatManager:35） | 1000（`nop.core.stat.sql-cache-max-size`） | 无 | 无 | 无 | 否 | 未 |
| 9 | rpc-client-stat-cache（GlobalStatManager:41） | 3000（`...rpc-client-cache-max-size`） | 无 | 无 | 无 | 否 | 未 |
| 10 | rpc-server-stat-cache（GlobalStatManager:47） | 10000（`...rpc-server-cache-max-size`） | 无 | 无 | 无 | 否 | 未 |
| 11 | resource-content-cache（ResourceContentCache:43） | 1000（`...content-cache-size`）+useMetrics | 无 | 无 | 无 | 否 | 未 |
| 12 | ResourceLoadingCache.createCache（:112，每 modelType/租户容器一份） | per-type-size **10000**（可按类型覆盖） | timeout 配置非空时设置（`nop.core.component.resource-cache.timeout` **默认 null**） | 无 | 无 | 否（ResourceCacheEntry 按 path） | **已注册**（ResourceComponentManager:438） |
| 13 | 租户容器 tenantCaches（TenantAwareResourceLoadingCache:31） | tenant-cache-container-size **100** | 无 | 无 | 无 | — | 未 |
| 14 | resource-dependency-manager 租户容器（TenantAwareDependencyManager:23） | 100 +useMetrics | 无 | 无 | 无 | — | 未；**destroyOnRemove 已设**（正确对照） |
| 15 | TenantAwareResourceCacheEntry.tenantCaches（:20） | 100 +useMetrics | 无 | 无 | 无 | — | 未；**destroyOnRemove 已设** |
| 16 | TenantAwareModelReference.tenantCache（:20） | 100 +useMetrics | 无 | 无 | 无 | — | 未；**destroyOnRemove 未设**（C5-003 F3 维持） |
| 17 | theme-config-cache（ThemeNamespaceHandler:26） | **newConfig(-1) 显式无上限**（第 1 轮 C5-002 已立案） | 无 | 无 | 无 | 否 | 未 |
| M1 | service-cache（ServiceContextImpl:139，MapCache） | MapCache 恒无界 | — | — | — | — | 请求作用域 |
| M2 | ValidationContext:103（MapCache） | 同上 | — | — | — | — | 请求作用域 |
| M3 | XDefMergeLoader:52/56/61（MapCache，3 构造器） | 同上 | — | — | — | — | loader 实例作用域 |
| M4 | record-mapping-cache（RecordMappingContext:36，MapCache） | 同上 | — | — | — | — | 请求作用域 |
| P1 | LocalCacheProvider.caches（per-name CHM，:44 buildCache） | 按 name 增殖，无上限 | — | — | — | — | nop-kernel 内零调用方（潜伏，第 1 轮已注） |

**终表结论（②）**：(a) 全部 17 个生产 LocalCache 站点中，**16 个配置了正数 maximumSize**，唯第 1 轮已立案的 theme-config-cache(-1) 例外——第 1 轮「22 个站点」计数经全量复核修正为 17 生产站点，无遗漏无界站点；(b) **expireAfterAccess 全平台零生产使用**（仅 LocalCache/CacheConfig 机制层存在），全平台无一处访问过期配置——CacheConfig 的该配置面是死旋钮族；(c) weakKeys 生产使用仅 ReflectionManager 一处且已失效（见下）；(d) 终表可直接作为 C5-002 契约守卫修复（WARN/unbounded() 工厂）的验收清单；(e) **边界注记**：nop-kernel 之外尚有 3 处生产 LocalCache 站点（nop-dyn DynCodeGen、nop-datav DashboardPanelQueryCache、NopDatavShareAccessGuard），属其他审计单元，不在本表。

### ③ 弱引用/软引用使用面判定（指令 ③）

全仓 src/main grep `WeakReference|SoftReference|weakKeys|weakValues|newConcurrentWeakMap|WeakHashMap` 的完整站点清单与判定：

| 站点 | 机制 | 值持键? | 判定 |
|---|---|---|---|
| ReflectionManager.introspectCache | Caffeine weakKeys+maximumSize(10000) | **是**（IClassModel→getType().getRawClass() 即键本身） | weakKeys 死代码化，**LRU 10000 是唯一真实上界**；归入 C5-008 F3 |
| ReflectionManager.registeredClassModels / converterCache / rawTypeCache / registeredConverters | MapMaker weakKeys（`CollectionHelper.newConcurrentWeakMap()`） | **是**（registeredClassModels 值经 getRawClass 持键；rawTypeCache 值 GenericRawTypeImpl.rawClass 即键；converterCache 的 ArrayTypeConverter(rawClass) 持键） | C5-008 |
| ClassExtension.extendedClasses | `List<WeakReference<Class>>` + 迭代清理 | —（正确模式） | 合格（正面对照：清理逻辑正确） |
| JsonSerializerFactory.serializersForClass / JsonDeserializerFactory.beanDeserializersForClass | MapMaker weakKeys | 疑似（序列化器可引用目标类） | 注册驱动有界，F3 注记 |
| EnumDictLoader.cache | MapMaker weakKeys | **是**（DictBean 持枚举常量→枚举 Class） | C5-009 |
| LocalCache.weakValues 配置面 | Caffeine weakValues | — | **全平台零生产使用**（死配置面，归 C5-002 契约守卫修复一并处理） |
| SoftReference | — | — | **全仓零使用**（无软引用缓存，无判定对象） |

**结论（③）**：第 1 轮只确认 introspectCache 用 weakKeys；本轮全量判定发现**平台全部 weakKeys 生产使用（共 8 处）中，凡是自动装载且值语义上必然持有键所指 Class 的（introspectCache、registeredClassModels、rawTypeCache、EnumDictLoader.cache），weakKeys 均被值持键强链失效**——这是 Java weak-map 的经典反模式（value → key 强引用使 key 永远强可达）。B 面「大对象缓存」中**该用弱值/有界缓存而误用弱键**的正是这一族；正确对照是 `ClassExtension.extendedClasses`（WeakReference 列表 + 迭代清理）与 introspectCache 的 maximumSize 兜底。

### ④ 列表型累积判定表（指令 ④）

第 1 轮以 map 为主；本轮补列表型（全局单例容器；请求/实例作用域短生命周期结构从略，见①表 #45/#46）：

| 结构 | 容量 | 清理机制 | 判定 |
|---|---|---|---|
| ReflectionManager.beanModelEnhancers（CopyOnWriteArrayList） | 显式注册基数 | register/unregister 配对 | 有界，合格 |
| ErrorMessageManager.subMappings 值列表（CHM\<code, CopyOnWriteArrayList\>，:82） | 错误码基数 × 参数过滤组合 | 同 filter 原位替换（:83-98 synchronized(list)）+ clearErrorCodeMappings 全清重载 | 有界，合格 |
| ShutdownHook.disposables（TreeSet\<IDestroyable\>，全局单例） | 显式注册基数 | **无 remove API**，持有至 JVM 退出；destroyAll 重入逻辑缺陷（CL-029） | 有界（注册驱动）；生产唯一注册点 NopApplication:39 |
| ClassExtension.helperMethods（ArrayList，synchronized） | 显式注册基数 | addHelperMethods/removeHelperMethods 配对 API（生产代码只见 add 调用点 ReflectionManager:313） | 有界（注册驱动）；弱键宿主失效疑点归 C5-008 F3 |
| ContinuationExecutor.Continuation.tasks（ArrayDeque，第 1 轮已判） | 栈帧生命周期 | runLoop 清空 | 维持第 1 轮判定 |
| CallbackSet.callbacks / Cancellable.callbacks / CycleSynchronizer.onCycleCompleted | 对象生命周期 | 对象销毁即弃 | 有界，合格（非全局） |
| ResourceMultiLock.locks、TagList、KeyedList、SimpleTextTemplate.parts 等值持有器 | 构造期固定 | 不可变语义 | 有界，合格 |
| FileWatcher（handler 为 watch() 闭包单实例，非列表） | — | watch 注销句柄 | 无列表累积面，合格 |

**结论（④）**：全局单例列表型累积结构中**无新增无界站点**；唯一无 remove 的 ShutdownHook.disposables 为注册驱动有界（CL-029 登记其生命周期/线程安全交叉缺陷）。

### ⑤ CL-024 交叉核对：g_executors 注册界面量化（指令 ⑤）

- **A 面界面事实**：`GlobalExecutors.g_executors`（static CHM，池名→池）唯一写路径为 `register()`（以名替换并 destroy 旧实例，第 1 轮已注 unregister 不销毁）与三处固定名 computeIfAbsent（globalTimer/cachedThreadPool/globalWorker）。**全仓 `GlobalExecutors.register` 生产调用点仅 1 处**：`nop-utils/nop-commons-java21/.../VirtualThreadTaskExecutor.java:26`（固定名，启动期一次）。动态调用名消费面（`getExecutor/requireExecutor(executorName)`，如 nop-batch ModelBasedBatchTaskBuilderFactory:210、nop-task TaskFlowManagerImpl:85）为**只读查找**，未知名 fail-fast 抛 `ERR_CONCURRENT_UNKNOWN_THREAD_POOL`，不产生注册累积。
- **越界模块核实（仅量化，不立案）**：CL-024 所指 per-tenant/topic 池线性累计源为 `SimpleRpcClientFactory`（nop-rpc-simple，`init()` 内 `newSingleThreadTimer("simple-rpc-client-timer-"+clientName)` + `newExecutor("simple-rpc-client-executor-"+clientName,5,10)`）与 `StreamControlRpcProxyFactory`（nop-stream-runtime，`newSingleThreadTimer("stream-rpc-client-"+topic)`）。核实：`DefaultThreadPoolExecutor.newExecutor`/`DefaultScheduledExecutor.newSingleThreadTimer` **均不注册进 g_executors**（独立实例，nop-kernel 之外模块自持）；`SimpleRpcClientFactory.destroy()` 对 ownExecutor/ownTimer 配对销毁（:139-146）→ 累计受 bean 生命周期约束，每实例恒 1 timer 线程 + 1 池。
- **量化结论**：CL-024 担心的「g_executors 按名累积」在当前代码**不可达**——per-tenant/topic 池根本不流经 g_executors；g_executors 键空间 = beans/静态注册的池名（基数 ≈ 部署配置数，个位数量级）。残界面为**接口合同缺口**：`GlobalExecutors.register` 对动态名（如含 tenantId/topic 的名字）无任何断言或上限，未来模块以每租户名注册即可复现线性累计。建议（不立案）：register() javadoc 声明「键空间必须为部署期静态池名」契约，或对 name 计数超过阈值打 WARN。

---

## 发现条目

### [nop-kernel-concurrency-C5-008] ReflectionManager 弱键族失效：值强引用键致 weakKeys 永不生效，rawTypeCache 事实无界强持有全部被反射 Class——第 1 轮判定表 row 2「合格」判定修正

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/ReflectionManager.java:122-126,279,419,447-456`；`nop-kernel/nop-core/src/main/java/io/nop/core/type/impl/GenericRawTypeImpl.java:22,30-31`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/util/CollectionHelper.java:203-204`
- **证据片段**:
  ```java
  // ReflectionManager.java:122-126 —— 三张 weak map（MapMaker weakKeys）
  private final Map<Type, ITypeConverter> converterCache = CollectionHelper.newConcurrentWeakMap();
  private final Map<Type, ITypeConverter> registeredConverters = CollectionHelper.newConcurrentWeakMap();
  private final Map<Class<?>, IRawType> rawTypeCache = CollectionHelper.newConcurrentWeakMap();
  // :279 —— registeredClassModels.put(classModel.getRawClass(), classModel)
  //         值 IClassModel 经 getType().getRawClass() 强持有同一 Class → 值持键
  // :447-456 —— rawTypeCache 自动装载且值持键：
  public IRawType newRawType(Class<?> clazz) {
      IRawType type = rawTypeCache.get(clazz);
      if (type == null) {
          GenericRawTypeImpl rawType = new GenericRawTypeImpl(clazz);  // :22 private final Class<?> rawClass
          rawTypeCache.putIfAbsent(clazz, rawType);                    // 值→键强引用，weakKeys 永不触发
  // CollectionHelper.java:204 —— new MapMaker().weakKeys().concurrencyLevel(32).makeMap()
  ```
- **严重程度**: P2
- **并发场景**: 全平台任意线程的反射路径（`getBeanModelForClass/getClassModel/newRawType/getConverter`），与 nop-javac/Janino 动态类加载（xlang-java 后端多轮编译）交叉；写侧由 MapMaker CHM 并发安全，缺陷在内存语义而非竞态。
- **触发时序**: 非竞态、声明-实现不一致缺陷。T1：任意 Class C 经反射路径触发 `newRawType(C)`，`rawTypeCache` 记入 C→GenericRawTypeImpl(C)，**值持有键 C** → C 经「map entry → value → key」永远强可达，weakKeys 的「键弱可达即清除」条件永不满足；T2：模型热更新产生新 classloader L1'，L1 全部类失去业务引用，但 rawTypeCache 条目仍强持 L1 的 Class → L1 loader 及其 Metaspace（单 loader 典型 1–10 MB）不可回收；T3：同链 `registeredClassModels`（值经 getRawClass 持键）、`introspectCache`（Caffeine weakKeys 同被值持键失效，仅 maximumSize=10000 LRU 兜底）、`converterCache` 的 ArrayTypeConverter 条目（值持键）同病。唯一清理 = `clearCache()`（CoreInitialization.destroy:200 / reinitialize:245 全量重建路径）。
- **现状**: rawTypeCache 无 maximumSize、无 TTL、弱键失效 → **事实无界强映射**；registeredClassModels 为显式注册驱动（基数=应用注册数）故现实有界但弱键声明同样无效；introspectCache 因 LRU 10000 兜底保持有界（弱键仅名义存在）。与第 1 轮 C5-001 的区别：C5-001 三表**无任何清理入口**（P1）；本组存在 destroy/reinitialize 全量清理入口，且 introspectCache 有 LRU 兜底，但**凡不经历全量 reinitialize 的动态类加载流（devtools 单模块热替换、进程内 Gray 重编译）均无自动回收**。
- **风险**: 使用 xlang-java 后端 + 不触发 CoreInitialization.reinitialize 的热加载部署下，rawTypeCache 逐代钉死旧 loader（与 C5-001 同型 Metaspace 不可逆增长，规模同量级：N 轮 × 1–10 MB/loader）；即便纯静态部署，弱键声明与真实语义（强引用直至显式 clearCache）相悖，等价于「上限声明缺位」的声明-实现一致性缺陷（维度定义内）。**第 1 轮判定表 row 2 判「有界（随 classloader 存活期），合格」据此修正为「强引用直至 clearCache/reinitialize，rawTypeCache 事实无界」**。
- **建议**: (1) rawTypeCache 改为 `LocalCache.newCache("raw-type-cache", new CacheConfig().maximumSize(10000), null)`（对齐 introspectCache 的 LRU 兜底，注册进 GlobalCacheRegistry）；(2) registeredClassModels/converterCache 若保留 weakKeys，必须消除值→键强链（如对 IRawType/IClassModel 改持 `WeakReference<Class>`，或直接弃用弱键声明改有界缓存+destroyOnRemove），避免「假弱键」继续误导后续实现；(3) 本条与 C5-001 修复立项合并设计——C5-001 建议中的「改 MapMaker weakKeys」方案对本族**无效**（值持键），修复必须选 weakValues 或有界 LRU 路线。
- **失效面枚举**: (1) `rawTypeCache` —— 值持键 + 无上限，本案主站点；(2) `registeredClassModels` —— 值持键，注册驱动有界，弱键失效（同族）；(3) `converterCache` —— ArrayTypeConverter 条目值持键（IdentityTypeConverter 条目不持），同族部分失效；(4) `introspectCache` —— 值持键致 weakKeys 失效，maximumSize 10000 兜底（有界，语义修正项）；(5) `registeredConverters` —— 值为应用显式注册转换器，注册驱动有界；(6) `ClassExtension.helperMethods`（宿主 classExtensions 弱键）—— IFunctionModel 参数类型可能强链被扩展 Class，**很可能**同族（未完全证实，随 (2) 修复一并核查）；(7) `ClassExtension.extendedClasses` —— WeakReference 列表+迭代清理，**正确对照**；(8) `EnumDictLoader.cache` —— 同族独立站点，见 C5-009。
- **信心水平**: 确定（值持键强链为直接代码事实：GenericRawTypeImpl.rawClass final 字段、IClassModel.getRawClass()=getType().getRawClass()；「弱键失效」为 JVM weak-reference 语义必然而非猜测）
- **误报排除**: 不属于「原语使用不是问题」——weakKeys 配置存在本身即声明了「随 GC 回收」的内存语义，实现使该声明恒假，属声明-实现一致性缺陷（维度定义第 3 项）；不重复 C5-001（XLangSemantics 无清理入口站点），本案站点与其不同且有独立修复路径；不报有 remove 配对且值不持键的 JsonSerializerFactory/JsonDeserializerFactory Class-map（注册驱动有界，F3 注记）。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P2**。值持键判定逐子项核实成立：rawTypeCache 值 `GenericRawTypeImpl.rawClass`（final 字段，构造器赋值，GenericRawTypeImpl:22/:30-31）即键；registeredClassModels 值 IClassModel 经 `ClassModel.type`→getRawClass 持键（ReflectionManager:277-282）；converterCache 的 EnumTypeConverter/ArrayTypeConverter/DataBeanTypeConverter 三类值持键（ReflectionManager:419-443）；introspectCache 同病但 maximumSize 10000 兜底（initCache:185-191）；唯一清理入口 `clearCache()`（:226-230）← CoreInitialization destroy(:200)/reinitialize(:245)。第 1 轮判定表 row 2 修正裁定成立。与 C5-001 联合修复设计约束成立（weakKeys 路线对本族无效，须走 weakValues 或有界 LRU）。**连带修正**：C5-009 驳回后，③ 结论「4 处因值持键失效」修正为 3 处（introspectCache/registeredClassModels/rawTypeCache）+ converterCache 部分失效；EnumDictLoader 不属本族。现状：已知未修复。

### [nop-kernel-concurrency-C5-009] EnumDictLoader.cache 弱键失效：DictBean 持枚举 Class，无注销 API、无上限——动态生成枚举场景逐代钉死

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/dict/EnumDictLoader.java:36,44-52`
- **证据片段**:
  ```java
  // :36 —— weak map 声明
  private Map<Class<?>, DictBean> cache = CollectionHelper.newConcurrentWeakMap();
  // :47-51 —— 值在构建时捕获 classModel（→枚举 Class），值持键：
  return cache.computeIfAbsent(classModel.getRawClass(), clazz -> {
      return buildDictFromEnum(dictName, classModel);   // DictBean 持枚举常量数组 → 持枚举 Class
  });
  ```
- **严重程度**: P3
- **并发场景**: 多线程并发字典装载（`DictProvider.getDict` → loader 链），与动态模型（nop-dyn 代码生成、xlang-java 编译枚举）交叉。
- **触发时序**: 非竞态。T1：任意代码按类名装载枚举字典 `loadDict(locale, "com.x.MyEnum")`，cache 记入 MyEnum.class→DictBean，DictBean 内部持有枚举 values 数组（强引用 MyEnum 及其 loader）→ weakKeys 失效，条目永不清除；T2：动态模型热更新重生成枚举类（新 loader），旧枚举 Class 仅被本 cache 条目强持 → 旧 loader Metaspace 不可回收；T3：类名不含 `.` 或非合法类名的字典走其他 loader，本 cache 键空间 = 「全类名形态的枚举字典名」基数。
- **现状**: 无注销/清理 API（不属于 ICache 体系，未注册 GlobalCacheRegistry，`clearAllCache()` 不触及）；CoreInitialization 的 `ReflectionManager.clearCache()` 亦不覆盖该私有字段。稳态（仅系统枚举、静态部署）下有界：键基数 = 系统枚举字典名个数（十至百级，条目小）。
- **风险**: 动态枚举（规则/报表 DSL 中生成的枚举类型）+ 热更新场景下逐代钉死 loader；稳态下仅常驻可忽略（KB 级 × 枚举数）。判 P3 因触发前提（动态生成枚举类 + 不全量 reinit 的热更新）窄于 C5-008。
- **建议**: 对齐 C5-008 方案：改有界 LocalCache（maximumSize 1000 足够）并在模型重载清理路径注册；或 DictBean 仅持枚举类名的弱句柄、values 惰性解析。
- **失效面枚举**: (1) `EnumDictLoader.cache` —— 本案；(2) `DictProvider.staticDicts` —— 显式 addDict/removeDict 配对（第 1 轮 row 10），合格；(3) `ReflectionManager` 弱键族 —— 见 C5-008（同根因不同站点）；(4) `ClassExtension.extendedClasses` —— WeakReference+迭代清理正确对照。
- **信心水平**: 确定（结构事实：weakKeys + 值持键 + 无清理 API 均可直接证实）；泄漏链依赖「动态生成枚举类」场景（很可能存在但未在本单元内构造实证）
- **误报排除**: 不属于「弱键使用即合格」误报——本条恰证明弱键声明在该使用形态下恒失效；不报静态枚举稳态（有界、量级 KB）。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **驳回**。值持键前提证伪：DictBean（nop-api-core/beans/DictBean.java:43-70）全部字段为 String / List&lt;DictOptionBean&gt; / Set&lt;String&gt; / transient Map&lt;String,…&gt; / Map&lt;String,Object&gt; attrs——不持有枚举 Class 或任何枚举常量；`buildOptions`（EnumDictLoader:106-150）中 `Enum[] items` 与 `field.getValue(null)` 均为局部变量，仅 `toString()` 后的 String 进入 `DictOptionBean.value`（DictOptionBean.java:34-42 无 Class 字段）。值→键强链不存在，weakKeys 按设计生效（枚举 Class 失去强引用后条目可被 GC 清除）；「无注销 API」属实但无害（弱键即清理机制）；稳态有界（键=触及枚举类基数）。按复核规则「引用不存在的代码/证据不成立且无替代证据 → 驳回」执行。连带修正：③ 表 EnumDictLoader 行、补充判定表 #37、终止判定「P3×1」相应核销。

---

## 维度集外线索（不立案展开）

### [nop-kernel-concurrency-CL-025]（维度集外线索，功能性缺陷）JsonWhitelist.add 包前缀注册被截断为 "*"，前缀白名单永不匹配

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/JsonWhitelist.java:43-49`
- **形态**（正确性/安全特性失效）: `className.endsWith(".*")` 分支执行 `whitelistPrefixes.add(className.substring(className.length() - 1))`——对 `"a.b.*"` 截取的是**最后一个字符 `"*"`** 而非 `"a.b.*"`；`contains()` 逐前缀做 `className.startsWith(packagePrefix)`，`"*"` 前缀对任何合法类名恒 false。注册 `a.b.*` 静默无效（多个前缀塌缩为单一 `"*"` 条目）。fail-closed（拒绝序列化），生产注册点均为全类名（如 SentinelRuleConfig:40-42）故当前无现实触发；`addResource`（读白名单文件，支持 `.*` 行语法）全仓生产零调用——潜伏。建议：改 `substring(0, className.length()-1)`（或 `substring(0, length-2)` 去点，按意图裁 tape）。属正确性立案面。

### [nop-kernel-concurrency-CL-026]（维度集外线索，C1 族新站点）ForbiddenObjectMethods 公开可变 static HashMap 非线程安全

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/impl/ForbiddenObjectMethods.java:20-35`
- **形态**（C1 共享可变状态）: `private static final Map<String, List<MethodInvokerKey>> methods = new HashMap<>()`（非 CHM），`public static void addMethod` 运行期可写（`computeIfAbsent(k -> new ArrayList<>())` + `list.add`），`contains` 并发读。XScript 编译线程并发调用 `contains` 与运行期 `addMethod`（public API，无同步）存在 CHM 结构损坏风险。当前仅 static 块注册 7 项，现实触发面窄；内存有界（C5 不立案）。建议改 CHM + `CopyOnWriteArrayList` 或冻结为不可变表。

### [nop-kernel-concurrency-CL-027]（维度集外线索，缓存管理语义）CacheEntryManagement.remove(key) 实现为整缓存 clear()

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/CacheEntryManagement.java:24-32`
- **形态**（正确性/性能）: `remove(@Nonnull V key)` 忽略 key 直接 `cache.clear()`——单条目失效语义被放大为全缓存失效。逐条目失效场景（如单文件模型刷新）将整体重载全部条目（VFS 重解析放大）；内存方向安全（多删不漏删）故 C5 不立案，但属缓存管理 API 语义缺陷。另注：`stats()` 恒返回 null，ICacheManagement 统计面断链。建议实现 `IResourceCacheEntry` 的按 key 移除或文档声明该 wrapper 仅整清语义。

### [nop-kernel-concurrency-CL-028]（维度集外线索，C2 族新站点）DefaultTaskExecutionQueue.addTaskIfAbsent 在 computeIfAbsent 映射函数内执行 executor.execute

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/execution/DefaultTaskExecutionQueue.java:193-201`
- **形态**（C2 锁内重操作，与基线预注「computeIfAbsent 锁内 transform」已立案族同型的新站点）: `states.computeIfAbsent(taskRef, k -> { ... queueTask(state, task); ... })` 在 CHM bin 锁内调用 `executor.execute`（可能触发拒绝策略、池任务启动、甚至同步拒绝异常）。锁内执行外部副作用会放大 bin 争用；拒绝异常自映射函数抛出时 CHM 保证不入表（无内存泄漏，C5 合格），但异常会以「taskRef 已占用」表象外溢。建议映射函数仅登记 State，queueTask 移出 computeIfAbsent 后执行。

### [nop-kernel-concurrency-CL-029]（维度集外线索，C1/C6 交叉）ShutdownHook.disposables 无 remove、TreeSet 非线程安全、destroyAll 重入条件反转致重复销毁

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/service/ShutdownHook.java:39,60-78`
- **形态**（C1 线程安全 + C6 生命周期）: (1) `disposables = new TreeSet<>`（非线程安全集合）被任意线程 `addDisposable`、shutdown 线程遍历；(2) 无 remove API，注册项强持有至 JVM 退出（注册驱动有界，C5 不立案）；(3) `destroyAll` 的 `if (!destroyed.compareAndSet(false, true) && CollectionHelper.isEmpty(disposables)) return;` 条件写反——首次调用 CAS 成功进入销毁；**后续任何调用 CAS 失败（!false=true）且集合非空时再次整体 destroy**，重复 destroy 全部 disposable（Spring 场景 removeRuntimeShutdownHook + 显式 destroyAll 双路径下可实际发生）。建议：`if (!destroyed.compareAndSet(false, true)) return;` 并将 disposables 改 CHM-backed 有序结构。

---

## 总计与终止信号判定

- **新增发现 2 条**：P2 × 1（**C5-008** ReflectionManager 弱键族失效/rawTypeCache 事实无界，含对第 1 轮判定表 row 2 的修正）、P3 × 1（**C5-009** EnumDictLoader.cache 弱键失效）。**无新增 P0/P1**。
- **维度集外线索 5 条**（CL-025~029）：JsonWhitelist 前缀截断 bug、ForbiddenObjectMethods 非线程安全 static HashMap、CacheEntryManagement.remove 全清语义、DefaultTaskExecutionQueue computeIfAbsent 锁内 execute（C2 族新站点）、ShutdownHook destroyAll 重入反转。
- **指令 ① 结论**：B 面 static CHM/Map 反向全量核查（补充判定表 #26~#46，21 个新判定站点）未发现第 1 轮遗漏的无界站点；但修正第 1 轮 row 2 的「weakKeys 合格」判定（C5-008）。XLangSemantics 三 CHM 键生命周期量化：三表均无任何 remove/clear 入口（全仓 0 命中），CoreInitialization 双清理路径均不触及；PROP_ACCESSORS 逐代单调累积（主面）、CLASS_MODELS 首代永久钉死兼语义陈旧、OBJ_FUNCTION_HANDLES 单槽最新代自愈（风险最轻，修正第 1 轮表述）。
- **指令 ② 结论**：LocalCache 配置终表 17 生产 newCache + 5 MapCache + LocalCacheProvider 全量登记；expireAfterAccess/weakValues 全平台零生产使用（死配置面）；唯一无上限站点仍为已立案的 theme-config-cache。
- **指令 ③ 结论**：平台全部 8 处 weakKeys 生产使用中 4 处因值持键失效（introspectCache/registeredClassModels/rawTypeCache/EnumDictLoader.cache），SoftReference 全仓零使用。
- **指令 ④ 结论**：全局单例列表型累积无新增无界站点；ShutdownHook 无 remove（注册驱动有界）随 CL-029 登记。
- **指令 ⑤ 结论**：CL-024 的 g_executors 按名累积风险当前不可达（per-tenant/topic 池不流经 g_executors；全仓唯一 register 为固定名一次性调用）；残界面为 register 动态名无合同防护。
- **终止信号判定**：满足终止条件——(a) 无新增 P0/P1（全部 P2/P3）；(b) ①~⑤ 深挖指令全部闭环：static CHM 全量反查无遗漏、LocalCache 配置面全枚举闭环（终表可直接作为 C5-002 守卫修复验收输入）、弱引用面全判定、列表型全判定、CL-024 界面闭合；(c) 剩余开放项均为跨维度立案面（CL-025~029 各归正确性/C1/C2/C6）。**建议 C5 维度终止，不启动第 3 轮**；C5-008 与 C5-001 修复立项必须联合设计（weakKeys 路线对本族无效，须走 weakValues 或有界 LRU）。
