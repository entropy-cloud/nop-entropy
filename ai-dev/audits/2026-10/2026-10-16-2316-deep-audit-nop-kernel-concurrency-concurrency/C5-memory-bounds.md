# C5 内存有界性 —— nop-kernel 平台并发设施专项审计（第 1 轮初审，盲审）

## 审计单元

- **模块**：nop-kernel（A 面：`nop-kernel/nop-commons/src/main/java/io/nop/commons/{concurrent,cache,pool}`，85 files / 7,550 行；B 面：nop-core / nop-xlang 全局模型缓存与注册表单例，基线 B-face 枚举 46 files / 5,945 行，按「模型/元数据缓存」语义取子集）
- **仓库**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **维度**：C5 —— 内存有界性：缓存/队列/注册表的容量上界与淘汰机制；无界累积结构；TTL/上限声明的实现一致性；弱引用/强引用持有面
- **判级口径**：平台设施被全平台并发调用；B 面全局缓存持有模型/元数据大对象。P0 = 默认用法 + 常见条件即 OOM/不可逆；B 面全局缓存持有大对象且无界 → 常驻增长门槛极低。
- **盲审纪律声明**：本轮为盲审轮次，审计过程中**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅阅读了允许的方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md` 与基线 `_tmp/cra-baseline-nop-kernel-concurrency.txt`。全部结论基于当前 live code。
- **基线预注对齐**：C1–C4 轮已立案项（RoundRobinSupplier、debounce promiseMap、ReactiveObjectMap、DedupBlockingQueue 墓碑、SequentialTaskExecutor offer、DefaultScheduledExecutor TOCTOU、throttleExecute、LimitedExecutor、ResourceCacheEntry 死锁、computeIfAbsent 锁内 transform、globalTimer 死配置、newExecutor 零校验、nop-cached-thread-pool 无界、池 destroy 语义族、updateThreadPool 缩容反置、LocalCache 异步 commonPool）本轮不重复立案；同族仅审**累积面**与**未立案站点**，相关深化以 CL 线索登记。

## 检查范围说明

逐文件核读：cache 包全部 16 文件（LocalCache/CacheConfig/MapCache/GlobalCacheRegistry/LocalCacheProvider/EhcacheCache/CaffeineCacheLoader/ICache 系列）；concurrent 包队列与执行器（OverflowBlockingQueue/DedupBlockingQueue/SequentialTaskExecutor/TaskQueue/GlobalExecutors/RateLimitExecutorImpl/ContinuationExecutor/ContextualizedRegistry/AbstractBatchProcessService/ExecutorHelper/ThreadPoolConfig）；B 面按基线枚举逐一定位缓存字段（ReflectionManager、ResourceComponentManager、ResourceLoadingCache/ResourceCacheEntry/ResourceContentCache/ResourceTimestampCache/ReactiveObjectMap/CacheEntryManagement、租户族 5 文件、ResourceDependsManager、ModuleManager、DictProvider、I18nMessageManager、ErrorMessageManager、XLangSemantics + ObjFunctionHandle、GlobalFunctions、SimpleExprHelper、XPathHelper/XPathProvider、XDslParseHelper、StdDomainRegistry、ScriptCompilerRegistry、EvalBackendRegistry、XPathOperatorRegistry、ObjPropMapperRegistry、ValueResolverCompilerRegistry、EvalGlobalRegistry、CodeLangMap、JsonTool（facade 无缓存）、BeanTool（facade 无缓存）、GlobalStatManager、ThemeNamespaceHandler、VfsConfigLoader、RegexHelper、JsonPathCompiler、FileWatcher）。全部 CacheConfig 默认值与 CoreConfigs/CommonConfigs 配置默认值已核对。

### B 面缓存清单：容量 / TTL / 淘汰 三列判定表

| # | 缓存（字段） | 容量 | TTL | 淘汰机制 | 判定 |
|---|---|---|---|---|---|
| 1 | ReflectionManager.introspectCache（Class→IClassModel，平台最大元数据缓存） | 10000（`nop.core.reflect.model-cache-size`） | 无 | Caffeine W-TinyLFU + **weakKeys** | 有界，合格（对照组） |
| 2 | ReflectionManager.registeredClassModels / classExtensions / converterCache / rawTypeCache | 无显式容量 | 无 | MapMaker weakKeys（GC 兜底） | 有界（随 classloader 存活期），合格；invokers 为强引用注册表（应用显式注册，基数有界） |
| 3 | ResourceComponentManager.modelCaches（每 modelType 一个 ResourceLoadingCache，持有 ComponentCacheEntry→模型大对象） | 10000/类型（`per-type-size`，可按类型覆盖） | **默认无**（`timeout` 默认 null，仅 check-changed 依赖检查） | LRU 驱逐；**驱逐不执行 destroy** | 有界但有 destroy 断链（C5-003）；规模上限 = 类型数 × 10000 个模型对象常驻 |
| 4 | TenantAwareResourceLoadingCache.tenantCaches / TenantAwareModelReference.tenantCache / TenantAwareDependencyManager.tenantCaches / TenantAwareResourceCacheEntry.tenantCaches | 100 租户（`tenant-cache-container-size`） | 无 | LRU；**前两者未设 destroyOnRemove，后两者已设** | 有界；destroy 断链不一致（C5-003 F3 族） |
| 5 | ResourceContentCache.cache（完整文件文本） | 1000 条（`content-cache-size`） | 无 | LRU（按条目计数，无字节权重） | 有界；大文件场景字节量可观（C5-006） |
| 6 | ResourceTimestampCache | — | — | — | 整文件已注释，非 live 代码，不判 |
| 7 | I18nMessageManager.localeToMessages | 10000（继承 per-type-size 默认） | 无 | LRU | 有界；key=locale 受请求方可影响，每次 miss 触发 VFS 加载（集外 C8 线索） |
| 8 | I18nMessageManager.registeredMessages | 编程注册 | — | — | 有界，合格 |
| 9 | ErrorMessageManager.errorCodeMappings / subMappings | 启动加载错误码映射 | — | 全量 clear 重载 | 有界，合格 |
| 10 | DictProvider.staticDicts（static CHM） | 编程注册 addDict/removeDict 配对 | — | — | 有界，合格 |
| 11 | ModuleManager.modules / dynamicModules | 模块清单（AtomicReference 整表替换） | — | 整表替换 | 有界，合格 |
| 12 | **XLangSemantics.PROP_ACCESSORS**（propName→{Class→getter/setter} 两层 CHM） | **无上限** | 无 | **无** | **无界 + Class 强引用（C5-001）** |
| 13 | **XLangSemantics.OBJ_FUNCTION_HANDLES**（funcName→ObjFunctionHandle，每 handle 单槽强引用最近 Class） | **无上限**（funcName 基数） | 无 | 无（handle 内单槽替换） | **无界 + Class 钉死（C5-001）** |
| 14 | **XLangSemantics.CLASS_MODELS**（className→IClassModel） | **无上限** | 无 | **无** | **无界 + Class 强引用（C5-001）** |
| 15 | SimpleExprHelper.exprCache | 1000 | 无 | LRU | 有界，合格 |
| 16 | RegexHelper.s_cache | 500（`nop.regex.compile-cache-size`） | 无 | LRU | 有界，合格 |
| 17 | XPathHelper.cache / XPathProvider.cache | 1000（后者 `xpath-cache-size`） | 无 | LRU | 有界，合格 |
| 18 | JsonPathCompiler.cache | 构造参数 cacheSize | 无 | LRU | 有界，合格 |
| 19 | XDslParseHelper.defTypeCache | 1000 | 无 | LRU | 有界，合格 |
| 20 | ThemeNamespaceHandler.configCache | **newConfig(-1) 显式无上限** | 无 | 无 | 声明无界（C5-002）；实际 key=主题配置路径（VFS 基数）故现实有界 |
| 21 | GlobalStatManager sqlStats / rpcClientStats / rpcServerStats | 1000 / 3000 / 10000 | 无 | LRU | 有界，合格（key=SQL 文本/service 名，LRU 兜底动态 SQL 基数） |
| 22 | StdDomainRegistry / ScriptCompilerRegistry / EvalBackendRegistry / XPathOperatorRegistry / ObjPropMapperRegistry / ValueResolverCompilerRegistry / EvalGlobalRegistry / CodeLangMap | 启动/显式注册 | — | — | 有界，合格 |
| 23 | GlobalCacheRegistry.caches | 注册表（重名 fail-fast，register/unregister 配对） | — | — | 有界，合格 |
| 24 | ReactiveObjectMap.objMap | **无上限、无淘汰** | 无 | 无 | **结构无界**；全仓 grep 零调用点（死代码）——结构缺陷登记于 C1 轮预注范围，本轮不重复立案，建议随死代码清理 |
| 25 | ResourceDependsManager.dependencyMap | 按 VFS 路径基数 | 无 | clearDependencies 手动 | 实际有界（路径空间），合格 |

### A 面队列/池侧判定表

| 结构 | 容量来源 | 上界判定 | 备注 |
|---|---|---|---|
| OverflowBlockingQueue | 构造器 capacity（batch 默认 500，BLOCK_WAIT 默认策略） | 有界 | 容量 0 行为见 CL-008 |
| SequentialTaskExecutor.queue | 构造器注入 OverflowBlockingQueue | 有界 | execute 忽略 offer 返回值（C1 已立案，CL-009 深化背压观测面） |
| TaskQueue（StandardThreadPoolExecutor） | ThreadPoolConfig.queueCapacity **默认 10000** | 有界 | queueCapacity=0 → LinkedBlockingQueue IAE fail-fast（C5-007）；`newSingleThreadExecutor` 的 0=SynchronousQueue 语义不一致 |
| FileWatcher.queue | 无参 LinkedBlockingQueue | **无界** | C5-004 |
| RateLimitExecutorImpl.promiseMap | distinct key 基数（每 key ≤1 条，finally 清理已闭合主泄漏） | 按 key 基数 | 残留面 C5-005 |
| GlobalExecutors.g_executors | 池名空间；register 以名替换并 destroy 旧实例 | 有界 | unregister(name) 不销毁——调用方责任（非 nop-kernel 内调用点），合格附注 |
| DedupBlockingQueue.map | distinct key 基数；take/poll remove 配对 | 有界 | 墓碑语义 C1 已立案，不重复 |
| ContinuationExecutor.Continuation.tasks | 每线程 ThreadLocal 单实例，runLoop 清空 | 有界 | 池化线程常驻空 Continuation 对象（提供 remove()），可接受 |

---

## 发现条目

### [nop-kernel-concurrency-C5-001] XLangSemantics 三个静态 CHM 编译缓存：强引用 Class、无容量、无淘汰、无 weakKeys——与 introspectCache 同职责不同待遇

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/XLangSemantics.java:155,647-656,782-792,917-926,1091-1098`；`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/ObjFunctionHandle.java:29-31`
- **证据片段**:
  ```java
  // XLangSemantics.java:155 —— 按 funcName 分立的反射分派 handle
  private static final Map<String, ObjFunctionHandle> OBJ_FUNCTION_HANDLES = new ConcurrentHashMap<>();
  // :647-652 —— 按 propName 分立的属性访问器缓存（内层按 Class 强引用）
  private static final Map<String, PropAccessor> PROP_ACCESSORS = new ConcurrentHashMap<>();
  static final class PropAccessor {
      final Map<Class<?>, IPropertyGetter> getters = new ConcurrentHashMap<>();
      final Map<Class<?>, IPropertySetter> setters = new ConcurrentHashMap<>();
  }
  // :1091 —— 按 className 缓存 classModel（IClassModel 强持有 Class）
  private static final Map<String, IClassModel> CLASS_MODELS = new ConcurrentHashMap<>();
  // ObjFunctionHandle.java:29-31 —— 每个 handle 单槽强引用最近一次 Class
  private transient Pair<Class<?>, IEvalFunction> _cacheFunc = Pair.of(null, null);
  private transient Pair<Class<?>, IEvalFunction> _cacheStaticFunc = Pair.of(null, null);
  ```
- **严重程度**: P1
- **并发场景**: XPL/XScript 解释器与 xlang-java 生成代码的全部求值线程（39 个调用文件，含每请求热路径 `getProperty/setProperty/invokeObjMethod/newInstance`）；与 nop-javac 的动态类加载（`JaninoClassLoader`/`DynamicURLClassLoader`）交叉。
- **触发时序**: 无需竞态——单调累积即触发。T1：xlang-java 后端（Janino 编译）第 N 轮编译生成类 L1 中的代码调用 `XLangSemantics.getProperty(...)`，`cachedGetter` 将 (propName, L1.Class) 写入 `PROP_ACCESSORS`；T2：模型热更新触发第 N+1 轮编译，旧 DynamicURLClassLoader L1 应整体废弃；因 `PROP_ACCESSORS`/`CLASS_MODELS`/`OBJ_FUNCTION_HANDLES._cacheFunc` 持有 L1 加载的 Class 强引用且**永不清除、无 weakKeys、无容量上限**，L1 及其全部 Metaspace（类元数据、方法字节码，单 loader 典型 1–10 MB）不可回收。每轮热更新净增一个被钉死的 loader，N 轮后 Metaspace + 堆内 Class 对象单调增长 N 倍，最终 `Metaspace OOM`（不可逆，只能重启）。
- **现状**: 三个 static final CHM 从初始化起只增不减；无任何 `clear`/`remove` 入口暴露给配置刷新或缓存管理（`GlobalCacheRegistry` 未注册，不可运维清理）。
- **风险**: 平台最大卖点即动态模型/XPL 热加载；任何使用 xlang-java 后端 + 模型热更新（devtools、多轮编译、Gray 重发布进程内生效）的部署，Metaspace 不可逆增长直至 OOM。稳态纯静态类部署下有界（上界 = 模板属性名基数 × 类清单基数），但该上界**无声明、无配置、无守卫**。对照判级口径：B 面全局缓存 + 元数据对象 + 无界 + 不可逆 → 常驻增长门槛极低。
- **建议**: 对齐同职责对照组 `ReflectionManager.initCache()`（`CacheConfig.weakKeys()+maximumSize(10000)`）：PROP_ACCESSORS 内层 Class 键改用 `CollectionHelper.newConcurrentWeakMap()`（MapMaker weakKeys）或 LocalCache(weakKeys, maximumSize)；CLASS_MODELS 改 LocalCache(weakKeys)；OBJ_FUNCTION_HANDLES 的 handle 内单槽 Pair 改弱引用或随 handle 表挂 TTL；并将三表注册进 `GlobalCacheRegistry` 以纳入 `clearAllCache()` 运维路径。
- **失效面枚举**（F3 同族全清单）: (1) `XLangSemantics.PROP_ACCESSORS` —— 本案主站点，无界；(2) `XLangSemantics.OBJ_FUNCTION_HANDLES` —— 本案，无界（键 funcName）；(3) `XLangSemantics.CLASS_MODELS` —— 本案，无界（键 className）；(4) `ObjFunctionHandle._cacheFunc/_cacheStaticFunc` —— 每 handle 单槽 Class 强引用，与 (2) 合并修复；(5) `ReflectionManager.introspectCache` —— weakKeys+10000 上限，**正确对照实现**；(6) `ReflectionManager.converterCache/rawTypeCache/registeredClassModels/classExtensions` —— MapMaker weakKeys，正确；(7) `SimpleExprHelper.exprCache` 等 7 个编译缓存 —— LocalCache 有界 LRU，正确。同族仅 XLangSemantics 一个类内的三处 + ObjFunctionHandle 两槽失守。
- **信心水平**: 确定（结构事实直接可证；泄漏链依赖动态类加载场景，该场景为平台明确支持的用法）
- **误报排除**: 不属于「原语使用不是问题」——缺陷在复合面：静态 CHM + Class 键 + 无淘汰三者叠加，且同仓库已有 weakKeys 正确先例（对照第 (5)(6) 项）证明该风险已被平台认知，此处是实现漂移而非设计选择；不属于低频路径——`getProperty/invokeObjMethod` 为生成代码每节点调用热路径。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留结构缺陷，降级 P1→P2**。三 CHM（:155/:647/:1091）无容量/无淘汰/无 weakKeys/全仓零清理入口核实成立（CoreInitialization :200-201/:245-246 双清理路径均不触及、未注册 GlobalCacheRegistry）；但 P1 触发链第一环不成立——`JavaCompileTool.createDynamicClassLoader`（nop-javac:80-84）全仓生产调用方为 0、`JaninoClassLoader.createForProject` 唯一引用为注释代码（AopBeanProcessor:379）、nop-xlang-java 后端为 STATIC_GENERATED 构建期产物走 classpath 常规加载（JavaEvalExecutionBackend:54-57）、`CoreInitialization.reinitialize()` 生产零调用、全仓无 hotswap 流——「生产运行期每轮热更新编译并废弃 DynamicURLClassLoader」不可达。真实运行期动态类加载仅 JaninoScriptCompiler（每 compile 一个 evaluator 级 classloader），其类流入三 CHM 需 ≥3 条件合取。默认解释器+静态部署下 Class 均为应用 loader，稳态有界但上界无声明无守卫（维持立案）。事实修正：调用面为 196 文件（初审称 39），方向不变。修复须与 C5-008 联合设计（weakKeys 路线受值持键约束，优先有界 LRU + GlobalCacheRegistry）。现状：已知未修复。终裁详见本文件「维度复核结论」节。

### [nop-kernel-concurrency-C5-002] LocalCache「未配置 = 无界」契约无守卫：maximumSize=0/负值直通 Caffeine 无上限，MapCache 恒无界，ThemeNamespaceHandler 现实持有 newConfig(-1) 无界缓存

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCache.java:80-92`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/CacheConfig.java:27,58-62`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/MapCache.java:24-36`；`nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/ThemeNamespaceHandler.java:26`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCacheProvider.java:39-45`
- **证据片段**:
  ```java
  // LocalCache.buildCache —— maximumSize<=0 时完全跳过上限设置
  Caffeine builder = Caffeine.newBuilder();
  if (config.getMaximumSize() > 0) {
      builder.maximumSize(config.getMaximumSize());
  }
  if (config.getExpireAfterWrite() != null && config.getExpireAfterAccess() == null) { ... }
  // MapCache —— 实现 ICache 但无任何容量/TTL 机制，config 恒为 new CacheConfig()（maximumSize=0）
  public MapCache(String name, boolean threadSafe) {
      this.name = name;
      this.map = threadSafe ? new ConcurrentHashMap<>() : new HashMap<>();
  }
  // ThemeNamespaceHandler —— 显式 -1（无上限）
  private ICache<String, ThemeConfig> configCache =
      LocalCache.newCache("theme-config-cache", newConfig(-1), this::loadConfig);
  ```
- **严重程度**: P2
- **并发场景**: 平台所有通过 `ICache`/`CacheConfig` 抽象使用缓存的调用方；多线程并发 put。
- **触发时序**: 非竞态，声明-实现一致性缺陷。任何调用方以 `new CacheConfig()`（maximumSize 默认 0、expire 默认 null）或 `newConfig(0)`/`newConfig(-1)` 构造 LocalCache，即得到**无上限、无 TTL、强引用**的缓存；`getConfig()` 返回该配置却无任何「无界」告警日志或文档断言。后续使用规模超过预期时（键基数 = 用户输入可控时更甚），堆占用随键基数单调增长直至 OOM。
- **现状**: 「0 = 无界」是隐含约定（`isCacheTypeChanged` 将 <0 视为类型变化、`updateCache` 对 0 跳过），但无 fail-fast、无 WARN 日志、`ICache.getConfig()` 消费方无从感知。基线内全部 nop-kernel 现存站点（除 ThemeNamespaceHandler 显式 -1 外）均配置了正数上限，故当前**现实无界站点**为 ThemeNamespaceHandler（key=主题配置路径，VFS 基数有界，实际风险低）；MapCache 的 4 个生产用法（ServiceContextImpl/ValidationContext/XDefMergeLoader/RecordMappingContext）均为请求/加载器作用域，实例级无界但生命周期短。`LocalCacheProvider.getCache(name)` 的 `caches` CHM 按 name 无上限增殖（当前 nop-kernel 内零调用方，潜伏）。
- **风险**: 平台设施被全平台复用：下一个按 CacheConfig 语义新建缓存的模块很容易以默认配置得到无界缓存且不自知（误报校准中「有界声明存在的缓存不算缺陷」——此处恰是**上限声明缺位**）。长期运行的服务在键基数缓慢增长（如动态 SQL 文本、动态路径）时退化为本维度 P1 形态。
- **建议**: LocalCache.buildCache 对 `maximumSize<=0 && maximumWeight<=0 && 无任何 expire` 的组合打 WARN（指明 cache 名与「无界」语义），或在 CacheConfig 上显式提供 `unbounded()` 静态工厂以区分「有意无界」与「忘配」；ThemeNamespaceHandler 补一个有限上限（主题文件基数小，newConfig(1000) 足够）；MapCache 文档标注恒无界语义。
- **失效面枚举**: (1) `LocalCache.buildCache` maximumSize/maximumWeight/expire 三条件全跳过路径 —— 本案；(2) `MapCache` 全类 —— 恒无界（设计如此，需文档化）；(3) `ThemeNamespaceHandler.configCache` —— 现实无界站点，本案证据；(4) `LocalCacheProvider.caches` per-name CHM —— 潜伏无界（零调用方）；(5) 基线内其余 20 个 LocalCache 站点 —— 均配置正数上限，合格（清单见 B 面判定表 1/5/15–19/21 行）。`newCache`/`new LocalCache` 全部 22 个站点已逐一核对（grep 全量）。
- **信心水平**: 确定（结构事实）；现实无界站点的实际风险为「有趣的猜测」级（主题路径基数小）
- **误报排除**: 不属于「CacheConfig 配置了上限的缓存不算缺陷」——本条恰指上限未配置时的守卫缺失；不报 SimpleExprHelper 等已配置站点。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P2**。全部证据核实成立（LocalCache.buildCache:80-84 `maximumSize>0` 守卫、CacheConfig:27 默认 0、MapCache:27-32 恒 `new CacheConfig()`、ThemeNamespaceHandler:26 `newConfig(-1)`、LocalCacheProvider:40 per-name computeIfAbsent 增殖且零调用方）；现实无界站点仅 theme-config（VFS 基数有界），缺陷定性为平台中心缓存抽象的契约守卫缺失，判级维持。round2 终表 17 生产站点计数修正（22→17）复核认可。现状：已知未修复。

### [nop-kernel-concurrency-C5-003] destroyOnRemove 断链族：CacheConfig 拷贝构造器丢弃 destroyOnRemove/refreshAfterWrite；模型缓存与租户容器驱逐时不执行销毁回调

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/CacheConfig.java:43-52`；`nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceLoadingCache.java:106-113`；`nop-kernel/nop-core/src/main/java/io/nop/core/resource/tenant/TenantAwareResourceLoadingCache.java:31-34`；`nop-kernel/nop-core/src/main/java/io/nop/core/resource/tenant/TenantAwareModelReference.java:20-23`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCache.java:110-114`
- **证据片段**:
  ```java
  // CacheConfig 拷贝构造器 —— 8 个字段复制清单中缺 destroyOnRemove 与 refreshAfterWrite
  public CacheConfig(CacheConfig config) {
      this.weakKeys = config.weakKeys;
      this.weakValues = config.weakValues;
      this.maximumSize = config.maximumSize;
      this.maximumWeight = config.maximumWeight;
      this.expireAfterWrite = config.expireAfterWrite;
      this.expireAfterAccess = config.expireAfterAccess;
      this.async = config.async;
      this.useMetrics = config.useMetrics;   // destroyOnRemove / refreshAfterWrite 丢失
  }
  // LocalCache 驱逐回调 —— 仅 destroyOnRemove=true 才销毁被驱逐值
  builder.removalListener((key, value, cause) -> {
      if (config.isDestroyOnRemove())
          DestroyHelper.safeDestroy(value);
  });
  // ResourceLoadingCache.createCache —— 模型缓存未设 destroyOnRemove
  CacheConfig config = new CacheConfig().useMetrics();
  config.setMaximumSize(cacheMaxSize.get());
  ```
- **严重程度**: P2
- **并发场景**: Caffeine 驱逐（LRU 容量 10000 触发）与租户容器淘汰（tenant-cache-container-size=100 触发）发生在任意业务线程的缓存写入路径上，与被驱逐对象的 destroy 回调并发。
- **触发时序**: T1：某 modelType 下模型文件数/键基数达到 10000，新键插入触发 Caffeine 驱逐旧 `ResourceCacheEntry`；T2：`LocalCache` removalListener 检查 `isDestroyOnRemove()`=false（ResourceLoadingCache 未设置）→ 直接丢弃，`ResourceCacheEntry.clear()`（含 `listener.onDestroyed` + `DestroyHelper.safeDestroy`）**永不执行**。凡模型对象持有需显式释放的资源（注册了 ICreationListener 的组件、IoC 内组件模型、带文件句柄/子注册的对象），其释放语义在驱逐路径整体失效；仅纯 POJO 模型靠 GC 兜底无碍。租户场景：TenantAwareResourceLoadingCache 的租户容器淘汰整个租户的缓存树时同样不销毁（对照：TenantAwareDependencyManager/TenantAwareResourceCacheEntry 设了 destroyOnRemove，行为不一致）。
- **现状**: `cloneInstance()`/拷贝构造器当前在 nop-kernel 内无 CacheConfig 调用方（潜伏缺陷，API 一旦被复用即静默改变语义）；驱逐不销毁是**现在时**——每条被 LRU 驱逐的模型条目都走此路径。
- **风险**: 驱逐本应是无界与有界之间的安全阀；销毁回调断链使「有界缓存」的边界语义不完整——内存有界但非堆资源（句柄、外部注册）随驱逐泄漏。同时 refreshAfterWrite 在拷贝后静默丢失，复制配置得到的缓存永远不刷新，语义漂移无告警。
- **建议**: (1) CacheConfig 拷贝构造器补齐 `destroyOnRemove`/`refreshAfterWrite` 两字段；(2) ResourceLoadingCache.createCache 补 `.destroyOnRemove()`（ResourceCacheEntry 已实现 IDestroyable，destroy()→clear() 链路现成）；(3) TenantAwareResourceLoadingCache.tenantCaches 与 TenantAwareModelReference.tenantCache 对齐 TenantAwareDependencyManager 的 destroyOnRemove 用法。
- **失效面枚举**: (1) `CacheConfig(CacheConfig)` 拷贝构造器 —— 丢 2 字段，本案；(2) `CacheConfig.cloneInstance()` —— 同上（委托拷贝构造器）；(3) `ResourceLoadingCache.createCache` —— 未设 destroyOnRemove，驱逐不销毁（每 modelType 一处，modelCaches 全部键共用）；(4) `TenantAwareResourceLoadingCache.tenantCaches` —— 未设；(5) `TenantAwareModelReference.tenantCache` —— 未设；(6) `TenantAwareDependencyManager.tenantCaches` —— 已设，正确；(7) `TenantAwareResourceCacheEntry.tenantCaches` —— 已设，正确；(8) `ResourceContentCache` —— 值为不可变 ContentEntry（long+String），无需销毁，合格。
- **信心水平**: 确定（字段遗漏为直接代码事实；驱逐泄漏链对「带销毁语义的模型」成立）
- **误报排除**: 不属于「有界缓存即为合格」——本条审的是有界缓存的**边界语义完整性**（维度定义中「淘汰机制的实现一致性」）；纯 POJO 模型不受影响已在现状中显式划界。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P2**。全部证据核实成立：CacheConfig 拷贝构造器 :43-52 确缺 destroyOnRemove/refreshAfterWrite 两字段；`cloneInstance()` nop-kernel 生产零调用（潜伏定性准确）；ResourceLoadingCache.createCache :106-113 未设 destroyOnRemove；租户四容器 2 设（TenantAwareDependencyManager:23-25 / TenantAwareResourceCacheEntry:20-22）2 未设（TenantAwareResourceLoadingCache:31-32 / TenantAwareModelReference:20-23）不对称核实；ResourceCacheEntry 实现 IDestroyable 且 destroy()→clear()→listener.onDestroyed+DestroyHelper.safeDestroy 链路在案。现状：已知未修复。

### [nop-kernel-concurrency-C5-004] FileWatcher 事件队列无界：handler 停滞时文件事件单调累积，无容量、无丢弃策略、无积压观测

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/watch/FileWatcher.java:31,62-83,106-124`
- **证据片段**:
  ```java
  // :31 —— 无容量上界的 LinkedBlockingQueue
  private final BlockingQueue<FileChangeEvent> queue = new LinkedBlockingQueue<>();
  // :62-63 —— 每个文件事件无条件入队（add 永不失败）
  public void onFileChange(Path root, Path path) {
      queue.add(new FileChangeEvent(FileChangeEvent.CHANGE_TYPE_MODIFY, path));
      this.triggerChange(handler);
  }
  // :117-123 —— handler 执行期间 processing=true，drain 循环独占；事件继续入队
  this.processing = true;
  try { handler.accept(events); } finally { processing = false; }
  ```
- **严重程度**: P2
- **并发场景**: NioFileWatchService 监听线程（生产者）与 globalTimer 调度的 debounce 任务线程（消费者，经 RateLimitExecutorImpl.debounce("process", ...)）。
- **触发时序**: 无需竞态。T1：前端构建 / git checkout / 批量模型导入一次性产生 10^4–10^6 个文件变更事件，全部 `queue.add` 成功（无界）；T2：消费者按 debounce 间隔批量 drain，若 `handler.accept`（如 dev 模式模型重编译）单轮耗时数秒且持续停滞（handler 内部 I/O 阻塞、依赖的服务不可用），生产速率 > 消费速率 → 队列深度随时间线性增长。规模量级：单个 FileChangeEvent ≈ 100 B（Path + int），10 事件/秒的持续变更率下 24h 累积 ≈ 8.6×10^5 条 ≈ 86 MB/天；handler 完全停滞时按监听器速率全速累积，长时运行数天内可推进到 GB 级 → OOM。无任何积压深度指标（queue.size() 未暴露 metrics）。
- **现状**: 队列声明无容量、无 QueueOverflowPolicy 应用（平台自备的 `OverflowBlockingQueue`/`QueueOverflowPolicy` 未用——共享前缀所述「平台设施该用未用」形态）、无积压告警。
- **风险**: dev/文件监听场景（nop-core 资源热更新）长时运行必然在故障条件下无界增长；F7 OOM 同族（无界队列）。队列中每个事件还强引用 Path→监视目录文件名，基数受文件系统影响。
- **建议**: 换用 `OverflowBlockingQueue`（容量如 10000，`DROP_ELDEST` + removeProcessor 记日志），使过载语义显式有界；暴露 queue.size() 到 GlobalMeterRegistry 积压指标；对 mergeEvents 已有的「同路径仅保留最后事件」逻辑可在入队侧以 DedupBlockingQueue 复用（同键覆盖），既去重又天然限幅到文件基数。
- **失效面枚举**: (1) `FileWatcher.queue` —— 本案无界站点；(2) `OverflowBlockingQueue` —— 有界对照组（batch 默认 500）；(3) `TaskQueue`/`ExecutorHelper.newSingleThreadExecutor` —— 有界（容量 0 语义问题见 C5-007）；(4) `DedupBlockingQueue` —— 有界（底层队列 + 键基数限幅）；(5) `ContinuationExecutor.Continuation.tasks` —— 栈生命周期，有界。全 concurrent 包队列结构已逐一核对，无界仅 FileWatcher 一处（nop-kernel 范围内）。
- **信心水平**: 确定（结构事实 + 量级推演）；「handler 长期停滞」为故障条件而非默认路径，故 P2 而非 P1
- **误报排除**: 不属于「测试/工具代码简化」——FileWatcher 是 nop-core 生产资源监听设施；不属于「有界声明存在」——此处无任何上界声明。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留缺陷，降级 P2→P3**。无界队列证据核实成立（:31 无界 LBQ、:62/:75/:81 无条件 add、:106-124 drain 循环、无积压 metrics）；但全仓唯一生产调用方为 nop-cli CliWatchCommand/FileWatcherFactory（开发期 watch 命令，会话级长运行），无服务端常驻消费方——OOM 需「handler 长期停滞」故障条件叠加开发期会话内持续累积，按「当前无生产调用方/仅开发期调用面 → 条件化降档」降 P3。修复建议不变（OverflowBlockingQueue/DedupBlockingQueue 复用 + 积压指标）。与 C7-013 同位点（Phase 6 合并裁定）。若服务端嵌入方开始消费 FileWatcher 应回升。现状：已知未修复。

### [nop-kernel-concurrency-C5-005] RateLimitExecutorImpl promiseMap 累积面复核：主泄漏已由 finally 清理闭合，残留「schedule 失败后取消条目滞留」与 key 基数无淘汰

- **文件**: `nop-kernel/nop-entropy-concurrency-audit/nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/RateLimitExecutorImpl.java:22,29-67`
- **证据片段**:
  ```java
  private final ConcurrentMap<Object, Future<?>> promiseMap = new ConcurrentHashMap<>();
  // debounce —— cancel 旧任务后若 schedule 抛出，旧条目已取消但无人移除
  public void debounce(final Object key, long delay, Runnable task) {
      Future<?> old = promiseMap.get(key);
      if (old != null)
          old.cancel(true);                       // 旧 future 进入 cancelled 终态
      final Future<?>[] holder = new Future<?>[1];
      Future<?> future = executorService.schedule(() -> {
          try { task.run(); }
          finally { promiseMap.remove(key, holder[0]); }   // 任务执行路径的清理
          return null;
      }, delay, TimeUnit.MILLISECONDS);
      holder[0] = future;
      promiseMap.put(key, future);                // 常规路径覆盖旧条目
  }
  ```
- **严重程度**: P3
- **并发场景**: 任意调用 throttle/debounce 的业务线程与 globalTimer 调度线程。
- **触发时序**: T1：线程 A 对 key K 调 debounce，条目 F1 入 map；T2：线程 B 对 K 再次 debounce，`F1.cancel(true)`（F1 变 cancelled，其任务体永不执行，`finally` 内 `promiseMap.remove` 不会发生）；T3：B 的 `executorService.schedule` 因 executor 已 shutdown/饱和**抛出异常** → `promiseMap.put` 未执行 → K 条目停留在已取消的 F1 上。该条目此后只能被 K 的**下一次** debounce/throttle 覆盖；若 K 永不再使用，条目（Future + key 引用 + 捕获闭包）永久滞留。每 key 恒 ≤1 条，总量上界 = 历史出现过的 distinct key 数，无淘汰机制。
- **现状**: throttle/debounce 主泄漏（任务完成后不移除）已由 `finally { promiseMap.remove(...) }` 闭合（本轮确认为已修复状态）；残留上述 cancel-path 缺口与「key 基数即上界但无声明」两点。nop-kernel 内唯一调用方 FileWatcher 使用固定 key "process"，现实无累积。
- **风险**: 其他模块按「key=业务对象/请求 id」复用该执行器时，条目数随键基数增长且单条目滞留到下次同 key 调用；单条目小（<200 B），百万级 distinct key ≈ 数十 MB——长尾而非 OOM 主源。
- **建议**: debounce 将 cancel+schedule+put 收进单个 `promiseMap.compute(key, ...)` 原子块，schedule 抛出时在 compute 内回滚（remove 或恢复旧值）；或在类 javadoc 声明「key 基数即内存上界」契约。
- **失效面枚举**: (1) `debounce` —— cancel 后 schedule 抛出的滞留窗口，本案；(2) `throttle` —— compute 原子块内 schedule 抛出时 compute 默认不修改 map（异常传播中止 put），无滞留，合格；(3) `replace` —— 委托 debounce(0,...)，与 (1) 同状态。
- **信心水平**: 确定（时序可直接构造）；影响量级 P3
- **误报排除**: 不重复 C1 轮已立案的 promiseMap 条目泄漏主体——本条仅登记修复后**残留**的 cancel-path 缺口与 key 基数上界声明缺失。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P3**。cancel-path 残留时序核实成立（debounce :52-66：`old.cancel(true)` 后 `executorService.schedule` 抛出（shutdown/饱和）→ `promiseMap.put` 不执行 → 已取消条目滞留至同 key 下次调用）；throttle compute 异常传播中止 put 无滞留核实。现状：主泄漏确由前次 campaign commit 22129fa9f7 修复（finally 自清理在案），本条为修复后残留登记，残留未修复。与 debounce 非原子四登记族（C1-004/C2-005/C3-020/C7-CL-033）同源，Phase 6 合并修复须一并覆盖 cancel-path 回滚。

### [nop-kernel-concurrency-C5-006] ResourceContentCache 按条目计数无字节权重：1000 条完整文件文本可常驻数十至百 MB 级

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceContentCache.java:24-58`；`nop-kernel/nop-core/src/main/java/io/nop/core/CoreConfigs.java:144-145`
- **证据片段**:
  ```java
  // 值 = 整个资源文件的完整文本
  private static class ContentEntry {
      final long lastModified;
      final String text;
  }
  // 容量仅按条目数（newConfig(int) → maximumSize），无 weight 维度
  cache = LocalCache.newCache(RESOURCE_CONTENT_CACHE_NAME, newConfig(config.get()).useMetrics(), null);
  // 默认 1000 条
  CFG_RESOURCE_CONTENT_CACHE_SIZE = varRef(s_loc, "nop.core.resource.store.content-cache-size", Integer.class, 1000);
  ```
- **严重程度**: P3
- **并发场景**: 多线程并发读写生成产物/配置文本缓存（`getCachedText/updateCachedText`）。
- **触发时序**: 非竞态。注释声明该缓存用途包含「生成组件时可以选择将生成结果缓存在内存中」：单份生成代码/模板文本典型 10–200 KB，1000 条上限下常驻内存 = 条目数 × 平均文本字节数（Java String UTF-16 再乘 2），最坏 1000 × 100 KB × 2 ≈ **200 MB 常驻**；默认配置即达到，无告警、无字节上限声明。Caffeine `maximumWeight` + `weigher` 机制在 CacheConfig 中完全未暴露（仅 maximumSize 条目数），LocalCache.buildCache 有 maximumWeight 分支但 CacheConfig 无 weigher 字段，机制断链。
- **现状**: 有界（条目数）但边界度量与风险维度（字节）错配。
- **风险**: 大文本资源（代码生成、导入导出中间文本）场景堆预算被隐性挤占；与真正 OOM 之间只隔「文本变大」一维。
- **建议**: CacheConfig 增加 weigher/maximumWeight 配置面并打通 LocalCache（builder 分支已存在），ResourceContentCache 按字符数设权重上限（如总量 64 MB）；或对该缓存加 expireAfterAccess 冷却。
- **失效面枚举**: (1) `ResourceContentCache.cache` —— 本案；(2) `GlobalStatManager.rpcServerStats`（10000 条 RpcServerStat）——条目小，字节风险可忽略，合格；(3) `ResourceLoadingCache` 系列 —— 值为模型对象，数量级更大但逐条目为独立模型（字节同样不可控），根因同「CacheConfig 无字节权重面」，修复应落在 CacheConfig 一处（同族单点）。
- **信心水平**: 确定（度量错配为结构事实）；实际累积规模依赖使用方存放的文本大小（很可能）
- **误报排除**: 不属于「有界即合格」误报——本条审的是上界的度量维度与声明包络（维度步骤 3「中间产物放大倍数」）；不报条目小且键基数低的其余缓存。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P3**。度量错配证据核实成立（ContentEntry long+String 值、newConfig 条目数上限、CoreConfigs:144 默认 1000、CacheConfig 无 weigher 字段而 LocalCache.buildCache:101-102 有 maximumWeight 分支的机制断链）。现状：已知未修复。

### [nop-kernel-concurrency-C5-007] 容量 0 值三处语义不一致：TaskQueue(0)=构造异常、newSingleThreadExecutor(0)=SynchronousQueue、ThreadPoolConfig 无 0 值校验

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:49-54,73-93`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/TaskQueue.java:43-45`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ThreadPoolConfig.java:26`
- **证据片段**:
  ```java
  // ExecutorHelper.newSingleThreadExecutor —— 0 被解释为「无队列直递」
  BlockingQueue<Runnable> queue = queueSize == 0 ? new SynchronousQueue<>()
          : new LinkedBlockingQueue<>(queueSize);
  // ExecutorHelper.newThreadPoolExecutor —— queueCapacity 原样传入 TaskQueue
  BlockingQueue<Runnable> queue = new TaskQueue(config.getQueueCapacity());   // TaskQueue(0) → LinkedBlockingQueue(0) → IAE
  // ThreadPoolConfig.queueCapacity 默认 10000，无 0/负值守卫
  private int queueCapacity = 10000;
  ```
- **严重程度**: P3
- **并发场景**: 池初始化（配置加载）与池运行期任务提交。
- **触发时序**: 用户在 ThreadPoolConfig 中显式配 `queueCapacity=0`（常见意图：「不要排队」）：T1：`DefaultThreadPoolExecutor.init()` → `new TaskQueue(0)` → `LinkedBlockingQueue(0)` 抛 `IllegalArgumentException`（裸 IAE，无错误码、不指示是 queueCapacity 配置错误）——init fail-fast 但诊断信息差；同一平台内 `newSingleThreadExecutor(name, 0, daemon)` 的 0 却是合法的 SynchronousQueue 语义。0 值意图无法以一致方式表达，配置者被迫试错。
- **现状**: 三处（TaskQueue 路径 / newSingleThreadExecutor 路径 / OverflowBlockingQueue 的 ArrayBlockingQueue 路径）对 0 有三种行为：异常 / 直递 / 异常。
- **风险**: 配置错误以低质量异常形式暴露（P3 诊断缺陷）；与 CL-008 同属平台队列容量语义面，易被误配为静默语义（见 CL-008 DROP_NEWEST 分支）。
- **建议**: ThreadPoolConfig/ExecutorHelper 对 queueCapacity 统一校验（0 显式映射为 SynchronousQueue 或 fail-fast 带错误码 `ERR_CONCURRENT_INVALID_QUEUE_CAPACITY`），三处对齐同一语义。
- **失效面枚举**: (1) `newThreadPoolExecutor`→TaskQueue —— 0=IAE，本案；(2) `newSingleThreadExecutor` —— 0=SynchronousQueue，语义漂移对照；(3) `OverflowBlockingQueue(0,policy)` → `ArrayBlockingQueue(0)` IAE（CL-008 详析）；(4) `DefaultThreadPoolExecutor.newExecutor(threadName,poolSize,queueSize,...)` —— queueSize 透传 (1)。
- **信心水平**: 确定
- **误报排除**: 非误报校准对象——三处同平台设施对同一配置值的语义分裂是声明一致性缺陷（维度定义「上限声明的实现一致性」），非原语使用问题。
- **复核状态**: 未复核
- **复核状态**（维度复核 2026-10-17）: **保留 P3**。三处 0 值语义分裂证据核实成立（ExecutorHelper:49-54 `queueSize==0`→SynchronousQueue、:85 `new TaskQueue(queueCapacity)` → LinkedBlockingQueue(0) IAE、ThreadPoolConfig queueCapacity 默认 10000 无 0/负值校验——实际行号 :24，初审记 :26 为行号漂移）。现状：已知未修复。

---

## 维度集外线索（不立案展开）

### [nop-kernel-concurrency-CL-008]（维度集外线索，不立案展开）OverflowBlockingQueue 容量 0/失配下的四策略行为族——对 C1 轮 CL-001 候选的可达性深化

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/impl/OverflowBlockingQueue.java:37-51,115-134`
- **形态**（C8 背压流控）: `send()` 的 DROP_ELDEST 分支 `while (!offer(item)) { T t = poll(); if (t != null) onRemoveItem(t); }` 在「offer 恒失败且 poll 恒空」时死循环空转。**可达性深化**：常规 2-arg 构造器 `new OverflowBlockingQueue(0, DROP_ELDEST)` 在构造期即被 `ArrayBlockingQueue` 的 `capacity < 1` IAE 拦截（fail-fast），自旋**不可达**；可达路径仅剩 4-arg 构造器注入容量字段=0 而实际队列非零容量（或反之）的失配组合——当前 nop-kernel 无该调用形态。另外三种策略在容量 0 下的行为：DROP_NEWEST = 每条消息静默 `onRemoveItem` 销毁（黑洞）；BLOCK_WAIT = `put` 永久阻塞（挂死）；FAIL = fail-fast 抛 ERR_QUEUE_FULL（唯一良性）。nop-kernel 生产站点仅 AbstractBatchProcessService（默认 500）与 SequentialTaskExecutor（构造器传入）。
- **建议方向**: 4-arg 构造器加 `capacity` 与实际队列 remainingCapacity 一致性断言；DROP_NEWEST 黑洞路径打 WARN 计数。属 C8 立项面。

### [nop-kernel-concurrency-CL-009]（维度集外线索，不立案展开）SequentialTaskExecutor.execute 忽略 offer 返回值——DROP/FAIL 策略下任务静默丢失无背压观测（对 C1 轮已立案项的观测面深化）

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/SequentialTaskExecutor.java:80-83`
- **形态**（C8 背压流控）: `execute(Runnable)` 仅 `queue.offer(command); onMessageQueued();`，不检查返回值。队列满且策略为 DROP_NEWEST/FAIL 时任务被静默丢弃（FAIL 也只走 OverflowBlockingQueue.send 才抛错，offer 路径不抛），调用方无异常、无指标、无日志。C1 轮已从并发正确性角度立案该 offer；此处补充其对 C8 的意义：任务丢失=静默背压失效。建议在 DROP/FAIL 生效路径补 rejection 计数器。

### [nop-kernel-concurrency-CL-010]（维度集外线索，不立案展开）I18nMessageManager.localeToMessages 以请求方可影响的 locale 为键，miss 即触发 VFS 加载

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/i18n/I18nMessageManager.java:48-49,161-180`
- **形态**（C8 过载行为）: locale 取自 `ContextProvider.currentLocale()`（最终源自请求头/Accept-Language 类输入），任意新 locale 字符串触发 `localeToMessages.get(locale)` → VFS 查找 + 装载。内存有界（LRU 10000）故非 C5，但键空间可被外部注入以放大 miss 成本（每次 miss 走完整 VFS 解析链）。建议 normalizeLocale 先行收敛 + 未命中 locale 的负缓存。属 C8 立项面。

---

## 总计

- **发现**：7 条 —— P1 × 1（C5-001 XLangSemantics 三 CHM Class 强引用无界缓存），P2 × 3（C5-002 无界契约无守卫、C5-003 destroyOnRemove 断链族、C5-004 FileWatcher 无界队列），P3 × 3（C5-005 promiseMap 残留、C5-006 字节权重缺失、C5-007 容量 0 语义分裂）。
- **维度集外线索**：3 条（CL-008/009/010，均 C8 形态，含对 C1 轮 CL-001 候选的可达性深化：DROP_ELDEST 容量 0 自旋经常规构造器不可达，真实风险面是 4-arg 失配构造与 DROP_NEWEST 黑洞）。
- **B 面缓存三列判定要点**：25 项判定中，**无界 3 项**（XLangSemantics 三 CHM，C5-001）、**声明无界但现实有界 1 项**（ThemeNamespaceHandler newConfig(-1)）、**结构无界但死代码 1 项**（ReactiveObjectMap，零调用点）、**有界但淘汰销毁断链 3 项**（modelCaches + 两个 TenantAware 容器，C5-003）、**有界但度量错配 1 项**（ResourceContentCache，C5-006）；其余 16 项容量/TTL/淘汰三列合格——所有 LRU 类缓存均无 TTL（依赖容量上限），ReflectionManager.introspectCache 是唯一 weakKeys+上限双保险实现（对照组）。
- **每条发现均含**：文件+行号、证据片段、判级、并发场景、触发时序、规模量级、F3 失效面全枚举、信心水平、误报排除（≥10 行格式）。

---

## 维度复核结论（2026-10-17，独立复核子代理）

**复核范围与方法**：对 C5-001~009 全部 9 条发现 + CL-008~010/025~029 共 8 条维度集外线索 + 两轮判定表做独立逐条复核。全部 file:line 证据亲自打开核对（行号漂移按实际代码修正）；历史盲审解除，对照面含 `ai-dev/audits/cra-index.md`（WI 1.1–1.7 台账）、本单元 dispatch-log、`ai-dev/plans/2461..2467`、git log 现状核实；F3 完备性以独立 grep 复扫。

### 一、逐条裁定汇总表

| 条目 | 初审 | 复核裁定 | 终判级 | 现状标注 | 关键复核依据 |
|---|---|---|---|---|---|
| C5-001 | P1 | **保留+降级** | **P2** | 已知未修复（本审计新立案，无 remediation commit） | 结构事实全部成立；P1 触发链第一环生产不可达（见节二） |
| C5-002 | P2 | 保留 | P2 | 已知未修复 | 全部证据核实（buildCache `>0` 守卫/MapCache 恒无界/theme `newConfig(-1)`/Provider 潜伏） |
| C5-003 | P2 | 保留 | P2 | 已知未修复 | 拷贝构造器缺 2 字段 + 租户四容器 2/2 不对称 + ResourceCacheEntry.destroy 链核实 |
| C5-004 | P2 | **保留+降级** | **P3** | 已知未修复 | 证据成立；唯一生产调用方为 nop-cli watch（开发期会话级），条件化降档 |
| C5-005 | P3 | 保留 | P3 | 主泄漏已修（commit 22129fa9f7，前次 campaign）；残留未修复 | cancel-path 残留时序核实；与 debounce 非原子族同位点 |
| C5-006 | P3 | 保留 | P3 | 已知未修复 | 度量错配证据核实 |
| C5-007 | P3 | 保留 | P3 | 已知未修复 | 三处 0 值语义分裂证据核实 |
| C5-008 | P2 | 保留 | P2 | 已知未修复 | 值持键判定核实成立（GenericRawTypeImpl.rawClass final 等）；row 2 修正是 |
| C5-009 | P3 | **驳回** | — | 不适用 | 值持键前提证伪（DictBean 全 String 字段，weakKeys 实际生效） |

**统计**：9 条 = 保留 6（C5-002/003/005/006/007/008）+ 降级 2（C5-001 P1→P2、C5-004 P2→P3）+ 驳回 1（C5-009）。终判级分布：**P1×0、P2×4（001/002/003/008）、P3×4（004/005/006/007）**。

### 二、C5-001 终裁（特别复核指令——含热更新可达性裁定）

**a) 结构事实——全部核实成立**：三 static CHM（XLangSemantics.java:155/:647/:1091）无容量、无淘汰、无 weakKeys；`grep OBJ_FUNCTION_HANDLES|PROP_ACCESSORS|CLASS_MODELS` 全仓仅类内命中，无任何 clear/remove 入口；`CoreInitialization` 双清理路径（:200-201 destroy、:245-246 reinitialize）均不触及；未注册 `GlobalCacheRegistry`。round2 对 OBJ_FUNCTION_HANDLES 单槽「最新代自愈」的量化修正（ObjFunctionHandle.java:36/:63/:73/:84 失配即整体替换）核实无误。

**b) 热更新可达性终裁——P1 触发链第一环在生产接线中不可达，降 P2**：
1. `JavaCompileTool.createDynamicClassLoader`（nop-javac:80-84，创建 JaninoClassLoader/DynamicURLClassLoader）**全仓生产调用方为 0**；
2. `JaninoClassLoader.createForProject` 唯一非定义引用点为注释代码（AopBeanProcessor:379 起整段注释）；
3. nop-xlang-java 后端 = `EvalBackendCapability.STATIC_GENERATED`（JavaEvalExecutionBackend:54-57），javadoc 明示「扫描清单为构建期扫描任务产物；生成类 classpath 常规加载」——**非运行期逐轮编译**，缺省惰性空态全部资源走解释器动态路径；
4. `CoreInitialization.reinitialize()` 生产调用方为 0（仅测试）；全仓 hotswap/hot-reload 运行期类替换流 grep 0 命中；
5. XLangSemantics 的真实调用面 = 解释器 executables（nop-xlang exec/*，app classloader）、构建期 Gen__* 产物（app classpath）与 truffle 节点——默认路径流入三 CHM 的 Class 均为应用 classloader，稳态有界（上界 = propName 基数 × 活跃类基数）；
6. 真实存在的运行期动态类加载仅 `JaninoScriptCompiler`（每 compile 新建 ScriptEvaluator → Janino 内部 evaluator 级 classloader，注册于 ScriptCompilerRegistry 的 `java` 语言）；其编译类流入三 CHM 需「`java:` 脚本内定义类 + 该类实例经解释/生成路径被属性访问或方法分派 + 脚本多轮重编译」≥3 条件合取。

**c) 自洽性**：「稳态有界但上界无声明无守卫」表述自洽成立。按复核规则（P1 需每步可信的触发时序；≥3 罕见条件合取或纯理论降 P2；无生产调用方面条件化降档），**终裁 P2**。若平台未来接入运行期 Java 编译后端（DYNAMIC_TRANSLATION 能力落地、进程内多轮编译启用），应回升 P1。修复建议维持，且必须与 C5-008 联合设计——PROP_ACCESSORS 内层 value（IPropertyGetter/IPropertySetter）可经 Method/Field 间接持 Class，weakKeys 路线同样受值持键约束，优先有界 LRU + GlobalCacheRegistry 注册。

**事实修正（不改判级）**：初审「39 个调用文件」实为 **196 个**（全量 grep 含 Gen__* 生成语料与 truffle 节点），调用面更广，热路径定性不变。ThreadPoolConfig queueCapacity 实际行号 :24（初审记 :26，行号漂移修正）。

### 三、C5-008 值持键核实与 C5-009 驳回（连带修正）

- **C5-008 核实成立（保留 P2）**：rawTypeCache 值 `GenericRawTypeImpl.rawClass`（final 字段，构造器赋值）即键；registeredClassModels 值 IClassModel 经 `ClassModel.type`→getRawClass 持键（ReflectionManager:277-282）；converterCache 的 EnumTypeConverter/ArrayTypeConverter/DataBeanTypeConverter 值持键（ReflectionManager:419-443）；introspectCache 同病但 maximumSize 10000 兜底（initCache:185-191）。唯一清理入口 `clearCache()`（:226-230）← CoreInitialization destroy/reinitialize。第 1 轮判定表 row 2「weakKeys 合格」修正裁定成立。与 C5-001 联合修复设计约束成立。
- **C5-009 驳回**：值持键前提**证伪**——DictBean（nop-api-core/beans/DictBean.java:43-70）全部字段为 String / List&lt;DictOptionBean&gt; / Set&lt;String&gt; / transient Map，不持枚举 Class 或枚举常量；`buildOptions`（EnumDictLoader:106-150）中 `Enum[] items` 与 `field.getValue(null)` 均为局部变量，仅 `toString()` 后的 String 进入 `DictOptionBean.value`（DictOptionBean:34-42 无 Class 字段）。值→键强链不存在，weakKeys 按设计生效（枚举 Class 失去强引用后条目可被 GC 清除）；「无注销 API」属实但无害（弱键即清理机制）；稳态有界（键=触及枚举类基数）。按「证据不成立且无替代证据 → 驳回」执行。
- **连带修正**：round2 ③ 结论「4 处因值持键失效」修正为 **3 处（introspectCache/registeredClassModels/rawTypeCache）+ converterCache 部分失效**；②终表 weakKeys 列与补充判定表 #37 中 EnumDictLoader 标注作废；round2 终止判定「P3×1（C5-009）」核销。

### 四、维度集外线索处置（8 条全部确认）

| 线索 | 处置 | 复核备注 |
|---|---|---|
| CL-008 | 确认（维持不立案，C8 面） | 证据核实（2-arg 构造 `ArrayBlockingQueue(0)` IAE fail-fast → DROP_ELDEST 自旋不可达；4-arg 失配构造可达；DROP_NEWEST 黑洞/BLOCK_WAIT 挂死/FAIL 良性）。与 C1-CL-001/C9-CL-014 三登记合并裁定 |
| CL-009 | 确认（维持不立案，C8 面） | SequentialTaskExecutor:80-83 offer 返回值忽略核实。与 C1-CL-002/C2-CL-004/C3-008/C7-CL-012 同位点（合计五登记），合并裁定 |
| CL-010 | 确认（维持不立案，C8 面） | localeToMessages ResourceLoadingCache（LRU 10000）+ getLocaleMessages miss→VFS 装载链核实 |
| CL-025 | 确认（功能性缺陷成立） | `substring(length-1)` 取末字符 `"*"` 核实（JsonWhitelist:43-49），前缀集 startsWith 恒 false；fail-closed、生产注册全类名、`addResource` 零调用=潜伏。**与 C1 轮备注、C9-CL-036 三登记合并裁定（Phase 6）** |
| CL-026 | 确认（C1 族新站点） | 非线程安全 static HashMap + public addMethod（computeIfAbsent+add）与并发 contains 核实 |
| CL-027 | 确认（缓存管理语义） | `remove(key)` 忽略 key 直接 `clear()` + `stats()` 恒 null 核实 |
| CL-028 | 确认（C2 族新站点） | computeIfAbsent 映射函数内 queueTask→executor.execute 核实（DefaultTaskExecutionQueue:193-201）。**与 C2-022（P3，同站点 CallerRuns 递归 compute 面）同位点，合并裁定** |
| CL-029 | 确认（**已并入 C6-011**） | `!cas && isEmpty` 反转（二次调用集合非空时重复 destroy）核实；C6-round2 第五节已立案 C6-011 并声明 CL-029 不再以 CL 形式维持，C5 侧登记关闭 |

### 五、判定表修正与 F3 完备性复核

- 第 1 轮 B 面表 row 2 修正（C5-008）**成立**；row 24 ReactiveObjectMap 死代码独立复核 0 调用点**成立**；row 33 EvalBackendObservation 1024 显式上界对照组**成立**（EvalBackendObservation:67/:88）。
- round2 补充判定表 #26~#46：#37（EnumDictLoader）判定**作废**（见节三）；其余 20 项经独立 grep（`static final Map/Set + new ConcurrentHashMap/HashMap` 全量复扫 nop-core/nop-xlang/nop-commons）复核未发现遗漏无界站点——新扫出的 static map 均为 static-init 枚举/查找表（XDefOverride/StdSqlType/ClassHelper primitive maps 等，初始化后不可变），「无遗漏」结论维持。
- round2 指令⑤ CL-024 交叉核对（per-tenant/topic 池不流经 g_executors；SimpleRpcClientFactory.destroy 配对销毁）独立核实成立，g_executors 累积风险当前不可达的结论维持。
- LocalCache 配置终表 17 生产站点抽查（#1/#11/#12/#13/#14/#16/#17）与本复核一致；expireAfterAccess/weakValues 全平台零生产使用结论成立。

### 六、争议点（供协调方/Phase 6 合并裁定参考）

1. **C5-001 P1→P2**：初审的「生产热更新每轮编译并废弃 DynamicURLClassLoader」链第一环在本仓库生产接线不可达（节二）；结构缺陷（无界 + 零清理入口）本身无争议。若运行期编译后端落地应回升 P1。
2. **C5-009 驳回**：初审未打开 DictBean/DictOptionBean 字段清单即断言「持枚举常量数组」；本复核以字段级证据证伪。C5-008 家族清单相应收缩。
3. **C5-004 P2→P3**：设施本体公共性与「当前调用面仅 CLI watch」之间按「无生产调用方条件化降档」口径取后者；服务端嵌入方开始消费 FileWatcher 时应回升。
4. XLangSemantics 调用面计数修正（39→196），热路径方向不变。
5. C5-005 的 cancel-path 残留与 debounce 非原子四登记族（C1-004/C2-005/C3-020/C7-CL-033）同源，Phase 6 合并修复时须一并覆盖（compute 原子化 + schedule 失败回滚）。

---

## 最终保留项（C5 维度复核后口径）

| # | 发现 | 终判级 | 一句话结论 |
|---|---|---|---|
| 1 | C5-001 XLangSemantics 三 static CHM 无界强引用 Class、零清理入口 | **P2**（自 P1 降） | 结构缺陷确凿；生产热更新逐轮编译流不可达，条件化降档；修复与 C5-008 联合设计（有界 LRU + GlobalCacheRegistry 注册，weakKeys 路线无效） |
| 2 | C5-002 LocalCache「未配置=无界」契约无守卫 | P2 | maximumSize≤0 直通无上限 + MapCache 恒无界 + theme `newConfig(-1)`；补 WARN/`unbounded()` 工厂 |
| 3 | C5-003 destroyOnRemove 断链族 | P2 | CacheConfig 拷贝构造器缺 2 字段（潜伏）+ ResourceLoadingCache 与两个 TenantAware 容器驱逐不销毁（另两个已设，不对称） |
| 4 | C5-004 FileWatcher 事件队列无界 | **P3**（自 P2 降） | 证据成立；唯一生产调用方为 nop-cli watch（开发期会话级）；与 C7-013 合并裁定 |
| 5 | C5-005 RateLimitExecutorImpl debounce cancel-path 残留 | P3 | 主泄漏已修（commit 22129fa9f7）；残留 cancel 后 schedule 抛出条目滞留；与 debounce 非原子族合并修复 |
| 6 | C5-006 ResourceContentCache 无字节权重 | P3 | 条目数上限与字节风险度量错配；CacheConfig weigher 配置面断链 |
| 7 | C5-007 容量 0 三处语义分裂 | P3 | TaskQueue(0) IAE / newSingleThreadExecutor(0) SynchronousQueue / ThreadPoolConfig 无校验 |
| 8 | C5-008 ReflectionManager 弱键族失效 | P2 | 值持键致 weakKeys 恒失效；rawTypeCache 事实无界（唯一清理 clearCache()）；row 2 修正成立；与 C5-001 联合设计 |

**驳回**：C5-009（EnumDictLoader 值持键证伪，weakKeys 生效，不立案；连带修正 round2 ③ 结论与 #37 判定）。
**线索处置**：CL-008~010/025~029 全部确认（CL-029 已并入 C6-011；CL-025 与 C1 备注/C9-CL-036 三登记合并；CL-028 与 C2-022 同位点合并；CL-008/009/010 归 C8 立项面）。
**复核后级分布**：P1×0、P2×4（C5-001/002/003/008）、P3×4（C5-004/005/006/007）。
