# C9 请求热路径审计（第 1 轮初审，盲审）

## 审计单元

- **维度**：C9——请求热路径（并发高性能形态：锁竞争频率与常数因子量化、锁内开销、ThreadLocal 频率、缓存命中常数因子、串行化点、per-record 循环）
- **模块**：nop-kernel 并发设施自身
  - A 面：`nop-kernel/nop-commons/src/main/java/io/nop/commons/{concurrent,cache,pool}`（85 files / 7,550 行）
  - B 面：nop-core / nop-xlang 全局模型缓存与注册表单例（按「模型/元数据缓存」语义取基线 B-face enumeration 子集：ResourceComponentManager、ResourceLoadingCache/ResourceCacheEntry、ReflectionManager、XLangSemantics、EvalGlobalRegistry、GlobalFunctions、StdDomainRegistry、DictProvider、I18nMessageManager、TenantAware* 包装、ResourceDependsManager、DefaultResourceChangeChecker）
- **仓库**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）

## 盲审纪律声明

本轮为盲审第 1 轮：**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件（含同目录 C1–C7 各维度文件）。方法论仅阅读 `ai-dev/skills/concurrency-resource-audit-prompt.md` 与 `ai-dev/skills/deep-audit-prompts.md` 允许节；基线仅使用 `_tmp/cra-baseline-nop-kernel-concurrency.txt`。所有结论以当前 live code 为准。边界裁定：锁的**正确性/纪律**归 C2，本维度只做竞争频率与常数因子量化；预注中已立案的 ResourceComponentManager `transformed.computeIfAbsent` 锁内 transform 不重复立案，本报告只量化其正常命中路径。

## 检查范围与串行化点清单

热路径主线实测（每请求模型/元数据获取）：`ResourceComponentManager.loadComponentModel(path)` → `findModelTypeFromPath`（fileType 子串 + CHM get）→ `makeModelCache`（CHM computeIfAbsent，命中为一次 get）→ `TenantAwareResourceLoadingCache.getCache`（tenant ThreadLocal get + 前缀判断）→ `ResourceLoadingCache.get` → `LocalCache.get`（Caffeine LoadingCache.get，含无条件 recordStats + 可选 Micrometer 指标）→ `ResourceCacheEntry.getObject`（3 个 config ref 读 + 时间戳比较 + `isChanged()` 全依赖图遍历 + `traceAllDepends` ThreadLocal get）。

### 串行化点清单表（synchronized/独占站点 ×「每请求是否经过」判定）

| # | 站点 | 锁载体 | 每请求是否经过 | 竞争窗口量化 | 判定 |
|---|------|--------|----------------|--------------|------|
| S1 | `ResourceCacheEntry.getObject`（ResourceCacheEntry.java:179）/ `clear`（:112）/ `getCacheEntryState`（:90）/ `reloadObject`（:227，synchronized 方法） | entry 实例 monitor | 仅 cache-miss / 刷新时经过；**命中路径不持锁** | 持锁执行整个 `loadObject`（模型解析，毫秒级）；同 path 并发 miss 时排队，异 path 无竞争 | 设计合理，不立案 |
| S2 | `ReflectionManager.ClassModelLoader.load()`（ReflectionManager.java:202） | loader 实例 monitor | 仅 per-class 首次 miss | 重量级 ClassModel 构建（毫秒级）在 per-class 锁内；一次性 | 设计合理，不立案 |
| S3 | `CycleSynchronizer.resolvePromise`（CycleSynchronizer.java:77-91）持锁执行全部回调；`waitCycleCompleted`（:48-52）持锁同步执行 `onCompleted.run()` | this monitor | 否（每 commit cycle 一次，秒级） | 窗口 = Σ回调时长，无上界（回调为外部代码）；窗口内 `beginCycle/waitCycleCompleted` 全阻塞 | 立案 C9-005 |
| S4 | `LocalResourceLockManager.checkTimeout`（LocalResourceLockManager.java:121-128）每 1s 遍历全部锁并逐一取 latch monitor | per-lock latch monitor + 全表遍历 | 否（周期 1s） | 每锁窗口 ~百 ns；N 锁 → 每 1s N 次 monitor acquire，占 globalTimer（5 线程共享）1 个线程 | 立案 C9-009 |
| S5 | `LocalResourceLockManager.tryLockWithLease/tryResetLease/isHoldingLock`（:156/:232/:258） | per-lock latch monitor | 仅显式用锁的路径 | putIfAbsent 无锁；synchronized 段为纯字段读（~百 ns）；await 在锁外 | 无热点，不立案 |
| S6 | `ICache.putIfAbsent` default（ICache.java:85-93）`synchronized(this)` + default `computeIfAbsent` 复合其上 | cache 实例 monitor | 取决于实现类 | 全实例单互斥（跨所有 key）。LocalCache/MapCache 已覆写为无锁原子；EhcacheCache 继承锁化 default（当前零生产调用方） | 立案 C9-006 |
| S7 | `HighWatermarkSemaphore.tryAcquire/release`（HighWatermarkSemaphore.java:46-73/:82-98） | 单 ReentrantLock | 否（流控启用点） | acquire/release 全串行于单锁；acquire 满载时 awaitNanos 挂锁外但唤醒风暴集中 | 维度集外线索 CL-015（C8 形态） |
| S8 | `TaskQueue.setParent`（TaskQueue.java:51）`synchronized` | queue monitor | 否（装配期一次） | — | 不立案 |
| S9 | `RoundRobinSupplier.resize`（RoundRobinSupplier.java:52）`synchronized(this)` | this monitor | 否（管理操作） | get() 路径无锁（volatile array + AtomicInteger），不受影响 | 不立案 |
| S10 | `OverflowBlockingQueue`/`DedupBlockingQueue` 内部均基于 `ArrayBlockingQueue`/`CHM`，无自加全局锁 | — | 批处理路径 | queue 自身锁为 JDK 内建 | 线索 CL-014/CL-016（C8 形态） |
| S11 | `ResourceComponentManager.makeModelCache`（ResourceComponentManager.java:433）CHM computeIfAbsent | CHM bin 级 | 每次模型加载经过 | 命中 = 一次无锁 get（CHM computeIfAbsent 先 get 后锁）；锁内仅首次构建 cache 对象 | 无热点，不立案 |

### 每请求 ThreadLocal get/set 频率实测

| 站点 | 频率（每请求） | 成本 |
|------|----------------|------|
| `BaseContextProvider.t_context.get()`（经 `ContextProvider.currentTenantId/currentLocale/currentContext`） | 模型缓存命中路径 2-4 次（tenant 判定 ×2、locale 解析 ×1-2）+ 每条 i18n 消息 1 次 | 单次 = 1 ThreadLocal.get（~几 ns）+ 字段读，无 set。健康 |
| `ResourceDependsManager.dependsStack.get()`（NamedThreadLocal） | 每次缓存命中 `traceAllDepends` 1 次（miss 采集时另有 get/set/remove 对） | 请求线程 stack==null 短路，健康；`collectDepends` 正常 pop 后 `remove`（:97-98），池化线程无残留 |
| `ExecutorHelper.submit` 系列 finally `ContextProvider.instance().detachContext()`（ExecutorHelper.java:143/:167/:192） | 每次 submit 包装任务结束 1 次 ThreadLocal remove | 任务侧从未 attach 时的冗余 remove（见 C9-008），成本 ~几十 ns |

### 每记录循环抽验（基线 A 面 C9 节 25 处 for/while，44 处全量 grep 复核）

- 命中热路径且无 O(n) 查找/正则/拼接问题的：`RoundRobinSupplier.get`（无锁）、`BlockingSourceConsumeTask` 批消费、`SequentialTaskExecutor` CAS 循环、`IBlockingSource.drainTo` 批量。
- 有发现的：`OverflowBlockingQueue.send` DROP_ELDEST 分支 `while(!offer(item))` 自旋（CL-014）；`DedupBlockingQueue.poll(timeout)` 每轮重置完整超时（CL-016）；`LocalResourceLockManager.checkTimeout`/`releaseLockForLocker`（:121/:205）全表 O(N) 遍历（C9-009，后者每次按 lockerId 全表线性扫）。
- `ICache.putAll` default 逐条 put（ICache.java:72-76）——Caffeine 有原生 putAll，LocalCache 未覆写，批量路径放大（C9-002 附注）。

---

## 发现条目

### [nop-kernel-concurrency-C9-001] 模型缓存命中路径每次执行全依赖图新鲜度遍历，且资源时间戳检查为裸 stat 系统调用（时间戳缓存设施被整体注释，意图声明未实现）

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceLoadingCache.java:190-198`、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceCacheEntry.java:163-208`、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/deps/DefaultResourceChangeChecker.java:16-25`、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/deps/ResourceDependsManager.java:214-255`、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceTimestampCache.java:1-55`（全文注释）、`nop-kernel/nop-core/src/main/java/io/nop/core/CoreConfigs.java:100-110`
- **证据片段**:
  ```java
  // ResourceLoadingCache.get：每次缓存命中都进入
  ResourceCacheEntry<V> entry = cache.get(path);
  boolean b = checkChanged.get() && entry.isRefreshEnabled(cacheRefreshMinInterval.get()); // 默认 true/100ms
  V value = entry.getObject(b, loader);
  // ResourceCacheEntry.isChanged：>100ms 后每次 get 为 true
  return ResourceComponentManager.instance().isAnyDependsChange(deps.getDepends());
  // ResourceDependsManager.isAnyDependsChange：每次检查分配新 HashSet + 递归遍历
  public boolean isAnyDependsChange(Collection<ResourceDependencySet> depends) {
      return isAnyDependsChange(depends, new HashSet<>());
  }
  // DefaultResourceChangeChecker.checkChanged：每个 dep 一次
  long current = resource.lastModified();   // FileResource → java.io.File.lastModified() = 裸 stat 系统调用
  return new ResourceChangeCheckResult(changed, current);  // 每 dep 一个分配
  ```
- **严重程度**: P2
- **并发场景**: 全部 HTTP/GraphQL worker 线程在每请求加载模型（xbiz/xmeta/xpl/dict/i18n/orm 元数据）时并行触发；遍历本身无锁但每 dep 执行 `deps.setLastModified(...)` volatile 写（读路径写共享状态，纯性能口径记录，正确性归 C1/C2）。
- **触发时序**: T1（任意请求线程）调用 `loadComponentModel(path)`，缓存命中且 `now - lastLoadTime > 100ms`（稳态恒真）→ `getObject(true,...)` → `isChanged()` → 沿 `ResourceDependencySet` 递归遍历该模型全部传递依赖 D 个节点 → 每 dep 调 `DefaultResourceChangeChecker.checkChanged` → 1 次 `File.lastModified()` stat 系统调用 + 1 个 `ResourceChangeCheckResult` 分配 + HashSet add；稳态下**每次**模型缓存命中都重复该全程。
- **现状**: 刷新门限（100ms）只压住加载后首百毫秒；稳态命中率 100% 的缓存条目仍逐请求付出 O(D) 遍历 + O(D) 次 stat + O(D) 分配。`CoreConfigs` 中 `CFG_COMPONENT_RESOURCE_TIMESTAMP_CACHE_SIZE`（注释明言"缓存IResource.lastModified的调用结果，避免频繁发出系统调用"，默认 10000 条）与配套 timeout 配置仍存在，但实现类 `ResourceTimestampCache` 被**整体注释**（1-55 行全部为注释代码），无任何调用方——时间戳缓存意图声明与实现脱节，stat 调用完全无缓存。
- **风险**: 量化链：单次 stat（本地 ext4/page-cache 热）≈ 1-5μs，容器 bind-mount/NFS/virtualfs 下 50-500μs；设 D=20（一个 xbiz 的自身+x:extends 父链+xlib import+依赖 meta），QPS=1000 的模型读取 → 本地盘 ≈ 2 万次 stat/s ≈ 20-100ms CPU/s（单核 2-10%），NFS 场景 ≈ 1-10s CPU/s（1-10 核被纯 stat 消耗）且 worker 线程延迟直接叠加 20×(50-500)μs = 1-10ms/请求。i18n 路径放大：`I18nMessageManager.localeToMessages`（`"i18n-cache"` ResourceLoadingCache，I18nMessageManager.java:48）使**每条 i18n 消息解析**（异常消息渲染、字典翻译）都走一遍依赖遍历；字典翻译路径（DictProvider.translateToLocale → getMessage）在选项渲染时逐 option 触发。
- **建议**: 恢复/重写 `ResourceTimestampCache`（按 live code 语义重建为带 TTL 的 `LocalCache<String,Long>`，`DefaultResourceChangeChecker` 先查缓存）；或对 `ResourceCacheEntry.isChanged` 的 dep-walk 结果加短 TTL 记忆化（entry 级 lastCheckTime）。无论哪种，`isAnyDependsChange` 的 `new HashSet<>()` 应复用调用方传入或在 entry 级缓存 checked set。
- **失效面枚举**（F3 同族清扫——所有经过 `ResourceLoadingCache.get` 热路径的缓存实例）:
  1. `I18nMessageManager.localeToMessages`（"i18n-cache"）——受累，**每条消息**触发（最痛）；
  2. `ResourceComponentManager.modelCaches` 全部 model-cache-*（xbiz/xmeta/xdef/xpl/dict 等）——受累，每请求模型加载触发；
  3. `TenantAwareResourceLoadingCache`（包装 2）——同链路，多一层 tenant 判定；
  4. `ResourceDependsManager.cache`（"depends-with-cache"，runWhenDependsChanged 路径）——受累；
  5. `DictProvider.defaultLoadDictBean` → 经 2 间接受累；
  6. `ReflectionManager.introspectCache`——**不受累**（非 ResourceLoadingCache，无 dep-walk，仅 Caffeine getIfPresent）；
  7. A 面 `LocalCache` 其他直接实例——不受累（dep-walk 是 ResourceLoadingCache 层逻辑）。
- **信心水平**: 确定（代码链路完整可复现；stat 成本为公开常数）
- **误报排除**: 不属于「有界声明存在的缓存」误报——问题不在缓存容量而在命中路径的重复重活；不属于「低频路径粗锁合理」——这是每请求路径；不与 C2 重复——此处不主张锁问题，只量化命中路径的重复 I/O 与分配。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。（a）门语义亲核成立：`ResourceLoadingCache.get:192` 门内（加载后 100ms 内）`b=false`，`getObject:178` 走 `value==null||checkChanged&&isChanged()` 假分支纯字段读返回（仅余 ：204-206 无条件 traceAllDepends 1 次 ThreadLocal get）；门外（稳态恒真）`b=true` 每次 `isChanged()` 全依赖图 walk。（b）量化链可信：D 口径经 round2 精确化为「不含虚拟根的真实 trace 文件数」（VirtualResourceDependencySet 根 MockReference 构造期快照不产生 stat，:28/:40-41 亲核），D=20×QPS1000→2 万 stat/s，本地 1-5μs→2-10% 单核，算术成立；i18n 放大链亲核成立（I18nMessageManager "i18n-cache":47-48、getLocaleMessages :168-186 每消息 1-3 次 get、DeltaJsonLoader:78 每 yaml traceDepends）。（c）NFS P1 升级条件表述合规——条件式双前提（NFS 类慢 stat 存储 × 持续高 QPS 模型密集路径），与本维度判级校准（P1 仅限实证全平台吞吐瓶颈）一致，不构成 P1 主张。（d）与 C7-007（P1，同链正确性面：mtime 双漏检窗口+无 TTL 兜底→陈旧模型不可逆）判级一致性核对成立：两案各按本维度校准（C7 维度 P1=不可逆正确性缺陷；C9 维度 P1=全平台吞吐瓶颈），C7-007 复核终裁已显式注明「checker 层修复同步收敛 C9-010 正确性面、stat 税性能面按 C9 本维度处置」——同链两面两判级系口径差异而非矛盾。终裁 P2 维持。现状：新发现（本 campaign），已知未修复（HEAD 8c13908bfd，审计后零代码提交；ResourceTimestampCache 自 2023 年 ef5086e2eb 起即为全文注释死代码）。

### [nop-kernel-concurrency-C9-002] LocalCache 无条件 recordStats 且 ResourceLoadingCache 全线 useMetrics：每次缓存命中叠加两层统计计数常数因子；putAll 走逐条 default

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCache.java:108-114, 224-226, 229-231`、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceLoadingCache.java:106-113`、`nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/ICache.java:72-76`
- **证据片段**:
  ```java
  // LocalCache.buildCache：recordStats 无条件，所有 LocalCache 生效
  builder.recordStats();
  builder.removalListener((key, value, cause) -> {
      LOG.trace("nop.commons.cache-item-removal:cache={},key={},value={},cause={}", name, key, value, cause);
      ...
  // ResourceLoadingCache.createCache：每个模型缓存再叠 Micrometer
  CacheConfig config = new CacheConfig().useMetrics();
  ...
  // ICache.putAll default：逐条 put，LocalCache 未覆写
  default void putAll(Map<? extends K, ? extends V> map) {
      for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) { put(...); }
  ```
- **严重程度**: P3（优化候选，量化依据如下）
- **并发场景**: 全部 worker 线程的每次缓存 get/put；计数器为 Caffeine 条带化 LongAdder 与 Micrometer counter（内部 Meter 标识查找 + LongAdder），无锁竞争但非零常数。
- **触发时序**: T（任意线程）每次 `ResourceComponentManager.loadComponentModel` 命中 → Caffeine `LoadingCache.get` 记录 hitCounter（recordStats）→ `CaffeineCacheMetrics` 绑定的 Micrometer `cache.gets` counter 自增（带 tag 查找）。两条计数链每次 get 各执行一次。
- **现状**: `recordStats()` 无条件对所有 LocalCache 开启（即便调用方从不读 `stats()`）；`ResourceLoadingCache.createCache` 固定 `.useMetrics()`，为每个 model-cache-* 绑定 Micrometer 指标；`LocalCache.putAll` 未覆写为 Caffeine 原生批量接口，批量预热走 N 次单条 put（每次各计 1 次 put 指标）。
- **风险**: 量化：Caffeine hit 记录 ≈ 10-25ns（条带 LongAdder），Micrometer counter inc ≈ 30-100ns（含 MeterId/tag 查找路径）；合计每次命中 +40-125ns。单请求 3-10 次模型获取 → +0.1-1μs/请求，相对 stat 遍历（C9-001）是小项，但在 stat 修复后会成为剩余常数的主要构成之一。GC 压力无（无分配）。属可测量的全局常数税，收益需 JMH 佐证。
- **建议**: ① `recordStats()` 改为 `config.isUseMetrics()/显式开关` 条件启用；② 高频缓存（model-cache-*/i18n-cache）评估关闭 Micrometer gets/puts 计数或降采样；③ `LocalCache` 覆写 `putAll` 委托 `cache.putAll(map)`。建议 JMH 基准形态：`LocalCacheGetHitBenchmark`（`Realm-Mode` avg, 4 线程混合 get，对比 recordStats on/off × useMetrics on/off 四象限）。
- **失效面枚举**: 全部 LocalCache 实例同族受影响——`reflection-class-model-cache`（ReflectionManager.java:189，无 useMetrics 仅 recordStats）、全部 `model-cache-*`、`i18n-cache`、`resource-dependency-manager` tenantCaches（useMetrics+destroyOnRemove，TenantAwareDependencyManager.java:24-27）、`depends-with-cache`；`MapCache`（无统计）与 `EhcacheCache`（无 Caffeine 层）不受影响。
- **信心水平**: 确定（代码路径确定；绝对 ns 数为公开实现常数级估计）
- **误报排除**: 不属于「synchronized/容器原语本身不是问题」的排除项——此处在量化命中常数因子，非正确性主张；判 P3 因单项收益小且指标可观测性有其价值。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3，剔除 putAll 子项（事实性修正）**。核心证据亲核成立：`builder.recordStats()` 无条件（LocalCache.java:108）、`ResourceLoadingCache.createCache` 固定 `new CacheConfig().useMetrics()`（ResourceLoadingCache.java:107）。**但「LocalCache.putAll 未覆写」证伪**：live code LocalCache.java:229-231 已覆写 `putAll` 委托 Caffeine 原生 `cache.putAll(map)`（git 核实自 6b08406549/2025-11-04 即存在，早于审计），MapCache.java:117-118、EhcacheCache.java:101-102 亦原生覆写——ICache default 逐条 putAll（ICache.java:71-75）在全仓实现类中**零可达站点**，第 1 轮「每记录循环抽验」表及本条现状/建议③相应子项一并作废。判级不变：P3 本就基于 recordStats/useMetrics 两层统计常数（40-125ns/命中），putAll 子项非判级依据。现状：新发现（本 campaign），已知未修复。

### [nop-kernel-concurrency-C9-003] XLangSemantics 生成代码属性访问路径每次经 PROP_ACCESSORS 两层 ConcurrentHashMap 查找（解释器同语义为 per-node transient 零查找），且 Class 强键永不淘汰

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/XLangSemantics.java:647-656, 782-792, 917-926`、对照 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/GetPropertyExecutable.java:108-123`
- **证据片段**:
  ```java
  private static final Map<String, PropAccessor> PROP_ACCESSORS = new ConcurrentHashMap<>();
  static final class PropAccessor {
      final Map<Class<?>, IPropertyGetter> getters = new ConcurrentHashMap<>();
      final Map<Class<?>, IPropertySetter> setters = new ConcurrentHashMap<>();
  }
  static PropAccessor propAccessor(String propName) {
      return PROP_ACCESSORS.computeIfAbsent(propName, k -> new PropAccessor());
  }
  private static IPropertyGetter cachedGetter(SourceLocation loc, String display, String propName, Object bean) {
      Class<?> clazz = bean.getClass();
      PropAccessor accessor = propAccessor(propName);          // 第 1 层：String 键 CHM computeIfAbsent
      IPropertyGetter reader = accessor.getters.get(clazz);    // 第 2 层：Class 键 CHM get
      ...
  ```
- **严重程度**: P3（量化后的优化候选）
- **并发场景**: xlang-java 后端生成代码（`GetPropertyExecutable`/`SetPropertyExecutable` 的生成入口 `XLangSemantics.getProperty/setProperty/selfAssignProperty`）在全部 worker 线程高频执行；解释器路径不经此表。
- **触发时序**: T（执行生成代码的线程）每次属性访问 → `getProperty` → `cachedGetter` → 第 1 层 `PROP_ACCESSORS.computeIfAbsent(propName)`（String hash + get，命中无锁）→ 第 2 层 `accessors.getters.get(clazz)`（Class identity hash + get）→ 热路径共 **2 次 CHM 查找**；对照解释器 `GetPropertyExecutable._cacheGetter`（transient 单槽 Pair，:108）命中为 1 次字段读 + 引用比较，零 map 查找。
- **现状**: 生成代码每属性访问固定 2 次 CHM 查找 + `readPropValue` try/catch 包装；解释器同语义操作为 O(1) 字段读。`PROP_ACCESSORS`/`OBJ_FUNCTION_HANDLES`（:155，按 funcName 分立）为全局静态 CHM，键为 String、值为含 Class 强键 CHM 的 PropAccessor，**无任何淘汰路径**——属性名空间在生成代码中为编译期常量（有界），但 Class 键随 dev 热重载（新 Class 对象）只增不减，旧 classloader 被强引用滞留。
- **风险**: 量化：单次 CHM get ≈ 15-40ns → 每属性访问 +30-80ns；一个执行 1,000 次属性访问的模板/后端函数 → +30-80μs。相对解释器形态（~5ns/访问）放大 ~6-16 倍。类泄漏侧：每次热重载为每个访问过的 (propName, oldClass) 保留永久强引用，长驻开发/插件化部署下累积（该泄漏面正确性归 C6/C1，此处仅登记为量化副产物）。
- **建议**: ① 对热点调用点把 getter 解析上移到生成期常量或 per-callsite 单槽缓存（对齐解释器 `_cacheGetter` 形态，OBJ_FUNCTION_HANDLES 的 per-handle 单槽已是该形态，属性访问可镜像）；② 若保留全局表，Class 键改 weak（`CollectionHelper.newConcurrentWeakMap` 在本仓库已广泛使用，如 ReflectionManager.java:107）。
- **失效面枚举**: 同族查找点逐一——`cachedGetter`/`cachedSetter`（2 层，受累）；`invokeObjMethod` → `OBJ_FUNCTION_HANDLES.computeIfAbsent(funcName)` + per-handle 单槽 `_cacheFunc`（1 层 CHM + 单槽字段读，影响约为本条一半）；`newInstance` → `CLASS_MODELS`（:1091-1098，单层 CHM，已缓存 IClassModel，形态健康）；`getStaticProperty`/`invokeStaticMethodResolved`（无缓存，见 C9-004）；解释器 `GetPropertyExecutable._cacheGetter`/`SetPropertyExecutable`（transient 单槽，健康）。同族 Class 强键无淘汰清单：`PROP_ACCESSORS`（Class 键）、`OBJ_FUNCTION_HANDLES` 值内单槽 Pair（强 Class 引用）、`CLASS_MODELS`（String 键→IClassModel 强引用，class 泄漏面同源）。
- **信心水平**: 确定（路径与对照实现均为 live code）
- **误报排除**: 非「缓存存在即合理」误报——对比基线（解释器单槽）证明常数因子回归是结构性的；判 P3 而非 P2 因绝对量（ns 级/访问）不足以构成可证明的争用或吞吐瓶颈。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3**。证据亲核成立：PROP_ACCESSORS CHM（XLangSemantics.java:647）+ PropAccessor 内嵌 getters/setters 双 CHM（:649-653）+ `propAccessor` computeIfAbsent（:655-657）；`cachedGetter` 每属性访问 2 层 CHM 查找（:783-793，初审行号 :782-792 微漂 1 行）；解释器对照 `GetPropertyExecutable._cacheGetter` transient 单槽 Pair（:107-125，命中 1 次字段读）成立；OBJ_FUNCTION_HANDLES（:155/:231）1 层 CHM+单槽形态确认。Class 强键无淘汰（PROP_ACCESSORS/OBJ_FUNCTION_HANDLES/CLASS_MODELS）成立。P3 校准合规（ns 级/访问、无量化竞争、收益需 JMH）。现状：新发现（本 campaign），已知未修复。

### [nop-kernel-concurrency-C9-004] 生成代码静态方法调用每次执行 Class.forName（caller-sensitive + 初始化检查），未复用 newInstance 已有的 className→模型缓存模式

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/XLangSemantics.java:401-423, 631-637, 1091-1099`
- **证据片段**:
  ```java
  static IMethodModelCollection resolveStaticMethodCollection(String className, String funcName) {
      Class<?> clazz;
      try {
          clazz = Class.forName(className);   // 每次调用：caller-sensitive + 链接/初始化检查
      } catch (ClassNotFoundException e) { return null; }
      return ReflectionManager.instance().getClassModel(clazz).getStaticMethodsByName(funcName);
  }
  // 对照：newInstance 对同形态需求已有缓存
  private static final Map<String, IClassModel> CLASS_MODELS = new ConcurrentHashMap<>();
  IClassModel classModel = CLASS_MODELS.computeIfAbsent(className, k ->
          ReflectionManager.instance().getClassModel(resolveClass(k)));
  ```
- **严重程度**: P2
- **并发场景**: xlang-java 生成代码的全部 worker 线程；`invokeStaticMethodResolved` 为「生成代码入口」（javadoc 自述），每个静态方法调用执行一次。
- **触发时序**: T（执行生成代码的线程）每次静态方法调用 → `resolveStaticMethodCollection` → `Class.forName(className)`（JVM 侧 caller 栈检查 + 已初始化判断 + 调用方 classloader 查找）→ `getClassModel`（弱键 map get + Caffeine getIfPresent，2 次查找）→ `getStaticMethodsByName`（CHM get）。同进程同类名解析结果恒定，却每次调用重做全链。
- **现状**: `Class.forName(String)` 单参形式是 caller-sensitive 方法（走 `Reflection.getCallerClass`），且绕开了 `ClassHelper.forName` 的 primitive/数组名处理；与之对照，同类中 `newInstance` 已用 `CLASS_MODELS` CHM 按 className 缓存 `IClassModel`，`resolveStaticMethodCollection` 未复用该模式，`getStaticMethodsByName` 结果（`MethodModelCollection` 引用）也未缓存。
- **风险**: 量化：`Class.forName`（类已加载、仅查找路径）≈ 100-500ns/次（caller 检查 + classloader findLocal/class 缓存查找），比 `loader.loadClass` 直查慢数倍；叠加 getClassModel 2 次 map get ≈ +50ns。单次静态调用合计 ~150-550ns。高频数值/工具静态调用循环（如 XScript 调 MathHelper 链）中每次调用支付；1 万次调用/请求的批量计算路径 → 1.5-5.5ms/请求。无锁竞争（纯常数因子），不构成 P1，但为生成代码路径上可消除的固定税。
- **建议**: 复用 `CLASS_MODELS` 模式：为 (className, funcName) → `IMethodModelCollection` 增加静态 CHM 缓存（注册/卸载时机与 CLASS_MODELS 对齐）；并将 `Class.forName` 替换为 `ClassHelper.forName`（统一 primitive/数组语义，避开 caller-sensitive 形式）。
- **失效面枚举**: 同族「每次调用重复类解析」清单——`resolveStaticMethodCollection`（本条，受累）；`resolveClass`（:631，ClassHelper.forName 无应用级缓存，仅 commonClassCache 32 个预注册类 + classloader 缓存，`instanceOf`/`castValue`/`getStaticProperty` 每调用受累，程度≈本条一半）；`resolveConverter`（:622，SysConverterRegistry CHM get，健康）；`CLASS_MODELS`/`PROP_ACCESSORS`/`OBJ_FUNCTION_HANDLES`（已缓存，健康）；解释器侧 BuildExecutableProcessor 编译期解析（编译期一次，健康）。
- **信心水平**: 确定
- **误报排除**: 不属于「JDK 类加载自带缓存所以无成本」——caller-sensitive 形式与初始化检查的常数仍逐次支付（此为可测公开行为）；不与 C9-003 混排：003 是缓存命中常数，004 是完全缺失缓存。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。证据亲核成立：`resolveStaticMethodCollection` 每次调用 `Class.forName(className)`（XLangSemantics.java:630-639，初审行号 :631-637 微漂）；`invokeStaticMethodResolved` 生成代码入口 javadoc 自述（:400-414）；对照 `CLASS_MODELS` 缓存模式已存在（:1092-1098）而静态方法解析未复用；`resolveClass`（:630-637 区域）ClassHelper.forName 无应用级缓存。量化链（150-550ns/静态调用、万次调用级路径 1.5-5.5ms/请求）合理，满足本维度 P2「量化支撑（每请求开销）」要求。与 C2-019（CLASS_MODELS computeIfAbsent 锁内构建，C2-round2 立案 P2）同文件不同方法面，无重复。现状：新发现（本 campaign），已知未修复。

### [nop-kernel-concurrency-C9-005] CycleSynchronizer 持锁执行外部回调：resolvePromise 在 synchronized(this) 内串行运行全部回调，waitCycleCompleted 在持锁状态下同步执行 onCompleted

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/CycleSynchronizer.java:48-64, 74-102`
- **证据片段**:
  ```java
  public synchronized CompletableFuture<Boolean> waitCycleCompleted(Runnable onCompleted) {
      if (!processing) {
          if (onCompleted != null)
              onCompleted.run();                 // 持 this monitor 同步执行外部回调
          return CompletableFuture.completedFuture(finished);
      }
      ...
  private CompletableFuture<Boolean> resolvePromise(boolean finished) {
      ...
      synchronized (this) {
          ...
          List<Runnable> callbacks = this.onCycleCompleted;
          if (callbacks != null) {
              this.onCycleCompleted = null;
              for (Runnable callback : callbacks) {
                  callback.run();                // 持锁串行执行全部回调
              }
              ...
  ```
- **严重程度**: P3
- **并发场景**: 周期提交协调（如批处理/流 checkpoint 周期边界）：提交线程调 `completeCycle/finish`，其他线程调 `beginCycle/waitCycleCompleted`。
- **触发时序**: T1（提交线程）进入 `resolvePromise` 持有 this monitor → 依次运行 n 个回调（每个为外部代码，时长无上界）→ 期间 T2 调 `beginCycle`/`waitCycleCompleted` 在 monitor 上阻塞，延迟 = Σ回调时长；`waitCycleCompleted` 在 `!processing` 分支下回调直接内联于锁内执行，调用方线程被回调时长阻塞且持锁。
- **现状**: 锁窗口由外部回调时长决定（无上界）；回调串行（设计使然）但持锁执行放大了窗口对无关方法（`isFinished/isProcessing` 为 volatile 读不受影响，`beginCycle/waitCycleCompleted` 受影响）的阻塞面。回调若再回调本对象或做阻塞 I/O，窗口进一步无界膨胀（回调递归进入 synchronized 会自死锁/长阻塞——该正确性面归 C2，此处仅量化窗口）。
- **风险**: 量化：频率 = 每 cycle 一次（典型秒-分钟级），非每请求路径；竞争窗口 = Σ回调时长（实测场景通常 μs-ms 级）。仅当回调含阻塞操作（如回调内做 flush/远端调用）时，`beginCycle`（周期起点）可被拖长至回调总时长，造成周期启动延迟。因非每请求路径且窗口通常小，判 P3。
- **建议**: 锁内仅快照 `callbacks`/`promise` 并复位状态，锁外执行回调（回调列表为局部快照后并发安全）；`waitCycleCompleted` 的 `!processing` 分支将 `onCompleted.run()` 移出 synchronized 方法（先读 finished 快照、锁外执行）。
- **失效面枚举**: 本类仅 2 处持锁回调站点（:51/:87），均已登记；`RoundRobinSupplier.resize`/`TaskQueue.setParent` 等其他 synchronized 站点无回调（见清单表 S8/S9）；同仓库使用方（批处理服务）回调时长归使用方审计。
- **信心水平**: 确定
- **误报排除**: 不属于「低频路径粗锁合理」豁免——问题非粗锁而是持锁执行无界外部代码（结构性窗口不可控）；非 C2 重复——不主张死锁结论，只量化持锁窗口与阻塞面。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3**。证据亲核成立：`waitCycleCompleted` synchronized 方法内 `!processing` 分支内联执行 `onCompleted.run()`（CycleSynchronizer.java:48-53）；`resolvePromise` 持 `synchronized(this)` 串行 run 全部回调（:87-96）。持锁窗口=Σ回调时长（外部代码无上界）成立。非每请求路径（每 commit cycle 一次），P3 校准合规。C2-round2 :117 已核该类无重入依赖（正确性面排除留档），本条仅量化窗口面，无重复。现状：新发现（本 campaign），已知未修复。

### [nop-kernel-concurrency-C9-006] ICache.putIfAbsent 默认实现为全实例 synchronized(this)，computeIfAbsent 默认实现复合其上；EhcacheCache 继承该锁化 default（当前零生产调用方，为潜伏串行化模板）

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/ICache.java:44-58, 85-93`、`nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/EhcacheCache.java:18-21`（javadoc 自述继承锁化 default）
- **证据片段**:
  ```java
  default boolean putIfAbsent(K key, V value) {
      synchronized (this) {          // 全实例跨所有 key 单一互斥
          if (containsKey(key)) return false;
          put(key, value);
          return true;
      }
  }
  default V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
      V v = get(key);
      if (v != null) return v;
      V newValue = mappingFunction.apply(key);   // 计算在锁外（双算风险换吞吐，正确性有 putIfAbsent 兜底）
      ...
      if (putIfAbsent(key, newValue)) ...
  ```
- **严重程度**: P3
- **并发场景**: 任何未覆写 `putIfAbsent`/`computeIfAbsent` 的 ICache/ISyncCache 实现类的并发 put/compute 调用。
- **触发时序**: T1..Tn（多 worker）对同一 cache 实例不同 key 并发 `putIfAbsent` → 全部在 `synchronized(this)` 上串行；窗口 = containsKey+put 两次全操作（Ehcache 3 的 cache.put 有堆内锁+可能的序列化开销）。同 key 场景下默认 computeIfAbsent 还会双算 mappingFunction（结果兜底正确）。
- **现状**: 三实现逐一核对：`LocalCache.putIfAbsent` 已覆写为 Caffeine `asMap().putIfAbsent`（无锁原子，LocalCache.java:234-237）、`computeIfAbsent` 委托 Caffeine（健康）；`MapCache` 覆写为 CHM 原子（MapCache.java:86-99，健康）；`EhcacheCache` 按 plan 2448 语义裁定显式继承锁化 default，且 javadoc 声明当前**零生产调用方**。故现状无生产热路径受害实例，但该 default 是一个「继承即获得全实例串行化」的潜伏模板，新增实现类（尤其分布外的自定义 ICache）极易踩中。
- **风险**: 量化：串行化吞吐上界 = 1/(单次 containsKey+put 时长)；Ehcache 堆内 put ≈ 100ns-1μs → 单实例 put 吞吐上界 100 万-1000 万 ops/s，读不受影响。当前无生产调用方 → 理论风险，按判级校准记 P3「优化候选/防御性登记」。
- **建议**: 为 `EhcacheCache` 覆写 `putIfAbsent`（org.ehcache.Cache.putIfAbsent 原生原子）；在 `ICache.putIfAbsent` javadoc 顶部加显式警告「覆写义务：默认实现串行化全实例」。若 plan 2448 裁定禁止直接映射（语义差异），至少补 CHM 辅助键集或文档红线。
- **失效面枚举**: ICache 实现全清单——`LocalCache`（覆写，健康）、`MapCache`（覆写，健康）、`EhcacheCache`（继承 default，受累面但零调用方）、`ISyncCache` 接口继承链（default 同源，随实现类判定）、仓库内其他 `implements ICache` 实现类经 grep `implements ICache|ISyncCache` 复核为上述三类（本审计范围 A 面）。
- **信心水平**: 确定（形态确定；无生产受害实例故降 P3）
- **误报排除**: 不属于「synchronized 原语本身不是问题」——这里量化的是实例级互斥跨 key 串行化的吞吐上界；不判 P2 因当前零生产调用方、无竞争频率可证。
- **复核状态**: 未复核
- **复核状态**: 已复核——**驳回（主体证据证伪），残留降为防御性备注**。核心主张「EhcacheCache 继承锁化 default putIfAbsent」被 live code 证伪：`EhcacheCache.putIfAbsent` 已原生覆写（EhcacheCache.java:106-108，`cache.putIfAbsent` 原子原语；plan 2448 语义裁定 javadoc :19-20 自述仅 getAndSet/computeIfAbsent 继承 default）；且 ICache.computeIfAbsent default 内部 `putIfAbsent` 调用经虚分派落到该覆写——`synchronized(this)` 在全部四个实现（LocalCache :234-237 Caffeine 原子、MapCache :86-99 CHM 原子、EhcacheCache :106-108 原生、NosqlCache :110-111 原生，全仓 grep 复核无第五实现）中**零可达站点**。残留仅为「default 方法对新增实现是模板陷阱」，而 ICache javadoc 覆写义务警告（本条建议②）已于 87d09323c5（2026-10-05，审计前）存在、建议①（覆写 EhcacheCache.putIfAbsent）亦已完成——缺陷主体与整改面双双落空。按复核规则「引用不存在的代码→驳回」精神，主体证据不成立且无可达替代站点，驳回立案；「新增 ICache 实现须覆写 putIfAbsent/computeIfAbsent」转为防御性备注随 owner doc 维护。现状：初审误判（其「三实现逐一核对」小节对 EhcacheCache 的裁定与 plan 2448 javadoc 原文不符）。

### [nop-kernel-concurrency-C9-007] DictProvider.getDict 命中路径每次分配三元 Arrays.asList 缓存键；无 cache 调用方每次走 config 读取与（locale 不匹配时）全字典 deepClone+逐选项 i18n

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/dict/DictProvider.java:88-124, 155-175`
- **证据片段**:
  ```java
  Object cacheKey = null;
  if (cache != null) {
      cacheKey = Arrays.asList("dict", dictName, locale);   // 命中路径也每次分配
      dict = (DictBean) cache.get(cacheKey);
      if (dict != null)
          return dict;
  }
  ...
  if (CFG_DICT_RETURN_NORMALIZED_LABEL.get()) {   // 每次调用读 config ref
      dict = dict.normalize();
  }
  ...
  // translateToLocale：locale 不匹配时 dict.deepClone() + 每 option 一次 i18n.getMessage
  ```
- **严重程度**: P3（优化候选）
- **并发场景**: 字典字段渲染/选项下拉拉取的 worker 线程；`cache` 参数由调用方传入（如会话/请求级缓存）。
- **触发时序**: T（worker）每次 `getDict`：即使缓存全命中，也先分配 `Arrays.asList` 包装（1 对象 + 数组）再 hash（3 元素级联 hash：String+String+String）；`cache == null` 的调用方（直接调 DictProvider 且未建缓存的路径）每次完整走 loader/RCM 加载链 + `normalize()`，locale 不匹配时 `deepClone()` 整字典 + O(选项数) 次 `i18n.getMessage`（每条再触发 C9-001 的 i18n 依赖遍历——交叉放大）。
- **现状**: 命中常数含 1 次分配 + 3 元素 hash ≈ 30-60ns/次（相对 CHM String 键 get 的 ~20ns 翻倍）；无 cache 分支的重活是结构性的（deepClone + N 次 i18n + C9-001 stat 遍历）。
- **风险**: 量化：选项渲染页（100 字段 × 每 dict 20 选项）= 2,000 次 getDict → 命中路径 +60-120μs 分配/hash；无 cache 时每次全量重载（毫秒级）+ 2,000×(i18n 全链) —— 依赖调用方是否传 cache。单独不构成瓶颈，但与 C9-001 相乘。
- **建议**: 缓存键改为预拼接 String（`"dict|" + dictName + "|" + locale`，一次分配一次 hash）或调用方持有的复用 key builder；对无 cache 调用方评估平台级 `GlobalCacheRegistry` 短 TTL 缓存兜底。
- **失效面枚举**: `getDict`（本条）；`existsDict`（:72-86，静态字典/枚举分支后回退 VFS exists()——冷路径，不立案）；`translateToLocale`（受累点，随 getDict 判定）；`EnumDictLoader.loadDict`（loader 分支，归使用方）；同仓库字典消费方（nop-dao/前端 biz）归使用方审计。
- **信心水平**: 很可能（分配/hash 成本确定；「无 cache 调用方」的线上占比未在本审计单元内证实，故整体 P3）
- **误报排除**: 非泛泛「减少分配」——键分配发生在每次命中（最高频点），有明确频率×代价链；判 P3 因无证据表明其单独构成可证明争用。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3**。证据亲核成立：命中路径每次分配 `Arrays.asList("dict", dictName, locale)` 缓存键（DictProvider.java:97，3 元素级联 hash）、每次读 `CFG_DICT_RETURN_NORMALIZED_LABEL` config ref（:115）、`translateToLocale` locale 不匹配时 `deepClone()`+逐 option `i18n.getMessage`（:155-175，每 option 交叉触发 C9-001 i18n 依赖 walk 的放大链成立）。P3 判级的自降理由（「无 cache 调用方线上占比未证实」）合规。现状：新发现（本 campaign），已知未修复。

### [nop-kernel-concurrency-C9-008] ExecutorHelper.submit/schedule 包装每次任务 2 次分配 + 双 future 绑定，且任务侧 finally 无条件 detachContext（ThreadLocal remove，任务从未 attach 时为纯冗余）

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:128-174, 176-199`
- **证据片段**:
  ```java
  public static <T> CompletableFuture<T> submit(ExecutorService executor, Callable<T> task) {
      CompletableFuture<T> future = new CompletableFuture<>();        // 分配 1
      Future<?> f = executor.submit(new Callable<Object>() { ... });  // 分配 2（匿名类实例）
      ...
      } finally {
          // 确保ThreadLocal上的IContext被清除
          ContextProvider.instance().detachContext();                 // 每次（即使从未 attach）
      }
  ```
- **严重程度**: P3（量化登记，优化候选）
- **并发场景**: 每次异步任务提交（调用方线程分配/绑定，worker 线程执行 finally detach）。
- **触发时序**: 每次 submit/schedule：调用方 2 次分配 + `FutureHelper.bindCancel`（额外监听器注册）；worker 侧任务结束时 `_instance.currentContext()`（ThreadLocal get）+ `remove()`（ThreadLocal table 清理），而包装器本身从未在 worker 上 attach 上下文（上下文注入是调用方/上层装饰职责）——多数任务下该 remove 清的是「空」，固定支付。
- **现状**: 三个站点（submit(Callable)/submit(Runnable)/schedule）同构；`scheduleWithRandomDelay`/`throttleExecute`（:210-259）为另一形态（含 AtomicReference 分配），同类。
- **风险**: 量化：每任务固定开销 ≈ 2 分配（~48B）+ CompletableFuture 状态机 + bindCancel 监听器 ≈ 100-200ns + 40-80B；万级任务/秒 → 1-2ms CPU/s + ~1MB/s 分配速率（年轻代压力小项）。detachContext ≈ 20-50ns/任务（ThreadLocal remove 触发 table 探测）。量级不足以单独立 P2；登记为批量化机会（方法论 C9 步骤 5 口径）。
- **建议**: ① detach 改为条件式：包装器记录「是否由本包装器 attach 过」，否则仅当 `currentContext()!=null` 才 remove（保留防御语义、去掉常态开销）；② 批量提交场景提供 `submitAll`/`invokeAll` 适配减少包装分配。若修，需同步核对三类站点 + `BindScheduledExecutor.executeOn`（BindScheduledExecutor.java:142，同构 finally detach）共 4 处同族。
- **失效面枚举**: `submit(Callable)`(:128)、`submit(Runnable,T)`(:152)、`schedule`(:176)、`scheduleWithRandomDelay`(:210)、`throttleExecute`(:240，无 detach，仅包装分配) —— 前 3 处 detach 同族；`BindScheduledExecutor.executeOn`(:142) 为同语义兄弟（另一类，需一并清扫）；`DefaultThreadPoolExecutor.submit`（DefaultThreadPoolExecutor.java:81-86）委托 executorHelper 同族受累。
- **信心水平**: 确定（开销形态确定；「多数任务未 attach」由包装器自身代码结构证实——包装链内无 attach 调用）
- **误报排除**: 非测试代码豁免场景（生产公共设施）；判 P3 因量级小且有防御性注释意图，属「量化后的优化候选」。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3**。证据亲核成立：`submit(Callable)`（ExecutorHelper.java:128-149）/`submit(Runnable,T)`（:152-173）/`schedule`（:176-199）三站点各 2 次分配（CompletableFuture+匿名 Callable）+`FutureHelper.bindCancel`+finally 无条件 `ContextProvider.instance().detachContext()`，包装链内无 attach 调用故多数任务下 remove 清「空」的结构判断成立。P3 校准合规（百 ns 级/任务，量级不足以 P2）。同族 `BindScheduledExecutor.executeOn`/`scheduleWithRandomDelay`/`throttleExecute` 枚举留档不重复核。现状：新发现（本 campaign），已知未修复。

### [nop-kernel-concurrency-C9-009] LocalResourceLockManager 周期维护为全锁表 O(N) 遍历 + 逐锁 monitor 获取，且共享 5 线程 globalTimer；releaseLockForLocker 每次全表线性扫

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/lock/impl/LocalResourceLockManager.java:117-129, 204-209`、`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:22-25`（timer 池默认 5）
- **证据片段**:
  ```java
  public void checkTimeout() {
      ...
      for (LocalResourceLockState lock : locks.values()) {      // 每 1s 全表遍历
          synchronized (lock.getLatch()) {                       // 每锁一次 monitor acquire
              if (lock.getExpireTime() <= now) removeExpiredLock(lock);
          }
      }
  ...
  public void releaseLockForLocker(String lockerId) {
      for (ResourceLockState lock : locks.values()) {            // 每次调用全表线性扫
          if (lock.getLockerId().equals(lockerId)) releaseLock(lock);
  ```
- **严重程度**: P3
- **并发场景**: globalTimer 调度线程（checkTimeout 每 1s）vs 持锁线程（tryResetLease/isHoldingLock 与 checkTimeout 争同一 latch monitor）；releaseLockForLocker 由会话清理/断连路径调用。
- **触发时序**: 每 1s：timer 线程遍历 N 个活跃锁，逐个取 latch monitor（与正在续租的持锁线程短暂竞争，各 ~百 ns 窗口）；N 大时该循环独占 timer 池 5 线程之一的整段时间。断连清理：O(N) 扫描 per 调用。
- **现状**: per-lock 锁窗口极小（2 次 volatile 读 + 条件 remove），竞争概率低；问题在扫描的 O(N) 常数与 timer 池占用，以及与续租路径的 monitor 乒乓随 N 线性增加。
- **风险**: 量化：N=10,000 活跃锁 → 每 1s 10,000 次 monitor acquire ≈ 0.1-1ms CPU（可忽略 CPU，但 timer 任务延迟 + 最长 1ms）；monitor 乒乓使续租路径偶发 +100ns-1μs 延迟。N=10 万（极端）→ 扫描 ~10-30ms/s，timer 池 1/5 线程 1-3% 占用。`releaseLockForLocker` 在断连风暴（10k 会话同时清理）→ 10k × O(N=10k) = 1 亿次比较 ≈ 秒级 CPU。当前调用方规模未知 → P3。
- **建议**: ① 用延迟队列/最小堆（按 expireTime）替代全表扫，仅弹出到期项；② `releaseLockForLocker` 维护 lockerId→locks 反向索引（CHM<String, Set>）；③ 评估给周期维护任务专用单线程 executor，与业务定时任务隔离。
- **失效面枚举**: `checkTimeout`(:117，定时全表扫，本条)；`releaseLockForLocker`(:204，按需全表扫，本条同族)；`dump`/`hasLock`(:47/:53，管理/冷路径，不立案)；`tryLockWithLease` 的过期清理分支(:169-171)单锁粒度（健康）。同仓库使用方（分布式锁/会话管理）归使用方审计。
- **信心水平**: 确定（形态与量级估算链完整；实际 N 分布未证实，故 P3）
- **误报排除**: 非后台周期任务一概豁免——此处量化了「随 N 增长 + 共享调度池占用 + 与续租路径 monitor 乒乓」三重常数；判 P3 因当前无大 N 证据。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3**。证据亲核成立：`checkTimeout` 每 1s 全锁表 O(N) 遍历+逐锁 latch monitor acquire（LocalResourceLockManager.java:117-129，锁内仅 2 次 volatile 读+条件 remove，窗口极小但逐锁取锁成立）；`releaseLockForLocker` 每调用全表线性扫（:204-209）；globalTimer 池默认 5 线程（CommonConfigs `nop.commons.concurrent.global-timer.maxPoolSize` 默认 5）确认。量化链（N=1 万→0.1-1ms CPU/s、断连风暴 10k×O(N) 秒级）合理，自降 P3（当前无大 N 证据）合规。现状：新发现（本 campaign），已知未修复。

---

## 维度集外线索（C8 形态，不立案展开）

### [nop-kernel-concurrency-CL-014]（维度集外线索，不立案展开）OverflowBlockingQueue.send 的 DROP_ELDEST 分支为无退避自旋 poll

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/impl/OverflowBlockingQueue.java:122-128`
- `while (!offer(item)) { T t = poll(); if (t != null) onRemoveItem(t); }`——多生产者并发时 offer 失败（容量被他人占住）而 poll 返回 null（瞬间被他人取走）会立即重试自旋，无 backoff/yield；满载+多生产者下形成忙等 CPU 消耗与不公平推进。属过载行为形态（背压/降级策略实现），归 C8。

  **复核处置**：确认（残余形态成立）+ 三登记合并注明。代码亲核成立（OverflowBlockingQueue.java:122-127）。注意 C1-CL-001 的「容量 0 无限自旋」形态已被 C1 复核**驳回**（构造 IAE fail-fast 不可达）；C5-CL-008 深化可达性（真实风险面=4-arg 失配构造与 DROP_NEWEST 黑洞）。CL-014 的「多生产者满载下有界自旋无退避（忙等 CPU+不公平推进）」与 C1 驳回理由「容量≥1 下自旋有界可前进」相容，系该族第三登记的**残余面**（有界但不公平、无退避的过载忙等，优化候选）。维持不立案展开；Phase 6 与 C1-CL-001（驳回）/C5-CL-008 合并裁定。现状：已知未修复。

### [nop-kernel-concurrency-CL-015]（维度集外线索，不立案展开）HighWatermarkSemaphore 全部许可操作串行于单把 ReentrantLock，acquire 等待者在锁内 awaitNanos

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/semaphore/HighWatermarkSemaphore.java:46-98`
- tryAcquire/release/availablePermits/getUsedPermits 全部串行于单锁；水位回落后 `signalAll` 唤醒全部等待者（惊群重排队）。作为流控设施其自身吞吐上界 = 单锁吞吐；许可竞争激烈时成为流控点的额外瓶颈。归 C8（流控设施过载形态）。

  **复核处置**：确认，注明与 C2 的关系。代码亲核成立（HighWatermarkSemaphore.java:46-98 单 ReentrantLock、awaitNanos 持锁内、release 降穿 lowWatermark 时 signalAll）。C2 两轮均亲核该类的 await/signal **正确性**面并裁定无缺陷（C2-round2 :62/:385「无缺陷」留档）；CL-015 是**吞吐形态**面（单锁吞吐上界+signalAll 惊群重排队），与 C2 的正确性排除不同 facet、无冲突。维持不立案展开（C8 形态）。现状：已知未修复。

### [nop-kernel-concurrency-CL-016]（维度集外线索，不立案展开）DedupBlockingQueue.poll(timeout) 每轮循环重置完整 timeout，实际最长等待可为 timeout 的数倍

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/impl/DedupBlockingQueue.java:101-113`
- `do { T o = queue.poll(timeout, unit); ... } while(true)`——取出到过期/重复项时继续用**原始完整 timeout** 重新 poll，连续 m 个过期项最长等待 m×timeout，超时契约违约。属队列过载/去重策略下的边界行为，归 C8（或 C2 等待语义，由主 agent 裁定归口）。

  **复核处置**：确认。代码亲核成立（DedupBlockingQueue.java:101-112，do-while 每轮以原始完整 timeout 重新 poll，连续 m 个过期/重复项最长等待 m×timeout，超时契约违约成立）。C1 预注已注明「Dedup 当前无生产调用方」（潜伏面）；C2 :412 已核 offer/offer(timeout) 回滚正确（不同面）。维持不立案展开，归口 C8/C2 由整合者裁定。现状：已知未修复。

---

## 基准缺口登记（方法论 C9 步骤 6）

| # | 建议基准 | 形态 |
|---|----------|------|
| B1 | `ResourceLoadingCacheHitBenchmark`：稳态命中（>100ms 后）模型 get，D=5/20/50 依赖 × 本地 tmpfs/磁盘两象限——验证 C9-001 的 stat 税与修复收益 | JMH avg + `-prof perfnorm`（syscalls/insn） |
| B2 | `LocalCacheGetBenchmark`：recordStats on/off × useMetrics on/off——验证 C9-002 常数税 | JMH avgt, 4-8 线程 |
| B3 | `XLangSemanticsPropAccessBenchmark`：生成代码 getProperty vs 解释器形态 vs 拟议单槽缓存——验证 C9-003 | JMH avgt |
| B4 | `nop-benchmark` 现状：未检索到上述任一设施的现有 JMH 基准（基准缺口按 P3 一并归入各条建议节） | — |

---

## 总计

| 类别 | 数量 | 明细 |
|------|------|------|
| C9 发现 | 9 | P2 ×2（C9-001 stat 遍历、C9-004 Class.forName）；P3 ×7（C9-002/003/005/006/007/008/009） |
| P0/P1 | 0 | 无「高 QPS 下构成全平台吞吐瓶颈」的实证串行化点（C9-001 在 NFS/慢 stat 存储下有升级条件，已在风险节注明） |
| 维度集外线索 | 3 | CL-014 / CL-015 / CL-016（C8 形态，编号自 CL-014 起） |
| 串行化点清单 | 11 | S1-S11（3 处立案/线索关联，8 处判定无热点并注明理由） |
| 基准缺口 | 3 | B1-B3 |

---

## 维度复核结论

（独立复核子代理，2026-10-17；对第 1、2 轮全部 10 条发现 + 5 条 CL 线索逐条裁定。复核方式：亲自打开全部 file:line 核对 live code，行号漂移以实际代码修正；历史对照面按本阶段授权读取 C1/C2/C5/C7 各轮文件与 cra-index 台账；git log 核实现状。裁定可改严重度，不得改事实。）

### 一、特别复核指令裁定

**1. C9-001（P2）终裁：保留 P2**

- (a) **门语义亲核成立**：`ResourceLoadingCache.get:192` `b = checkChanged.get() && entry.isRefreshEnabled(cacheRefreshMinInterval.get())`，默认 true/100ms（CoreConfigs.java:86-87/:104-105）。**门内**（加载后 100ms 内）`b=false`，`getObject:178` 走假分支纯字段读返回，仅余 ：204-206 无条件 `traceAllDepends` 1 次 ThreadLocal get（栈空短路）——门内短路确认；**门外**（稳态请求间隔>100ms 恒真）`b=true` 每次 `isChanged()` 全依赖图 walk——门外全量确认。
- (b) **量化链可信**：round2 已精确化 D 口径为「不含虚拟根的真实 trace 文件数」（`VirtualResourceDependencySet` 根为 MockReference 构造期 currentTimeMillis 快照、不产生 stat，:17-28/:40-41 亲核；子依赖逐个 `resolveResource`+`File.lastModified()` 裸 stat（FileResource.java:70-71）+`ResourceChangeCheckResult` 分配+`deps.setLastModified` volatile 写，ResourceDependsManager.java:235-255 亲核）。D=20×QPS1000→2 万 stat/s；本地 1-5μs→20-100ms CPU/s（单核 2-10%，prompt 所引「6%」为区间中值）——算术成立，D=20 属 xbiz 复杂模型（自身+x:extends 父链+xlib import+meta）合理量级。P2 所需量化支撑（每请求开销×频率）具备。
- (c) **NFS P1 升级条件表述合规**：为条件式双前提（NFS 类慢 stat 存储 × 持续高 QPS 模型密集路径），非 P1 主张；与本维度校准（P1 仅限「高 QPS 下全平台吞吐瓶颈」的实证）一致。
- (d) **与 C7-007 判级一致性核对成立**：同链两面——C7-007 P1 为正确性面（mtime 相等比较双漏检窗口+cacheTimeout 默认 null 无 TTL 兜底→陈旧模型不可逆服务），C9-001 P2 为性能面（每请求重复 walk+stat 税）。两判级各按本维度校准，非矛盾；C7-007 复核终裁已显式注明「checker 层修复同步收敛 C9-010 正确性面、stat 税性能面按 C9 本维度处置」，跨维度闭环已建立。
- (e) **终裁 P2 维持**。i18n 放大链（E2）亲核成立：I18nMessageManager "i18n-cache"（:47-48）、`getLocaleMessages` 每消息 1-3 次 get（:168-186）、DeltaJsonLoader:78 每 yaml 显式 traceDepends。

**2. C9-010（P2）终裁：保留 P2 + F3 补全 2 站点（不改判级）**

- (a) **四使用方全部亲核成立**：
  - E3 `GatewayHttpFilter:127` 硬编码 `getObject(true, handlerLoader)`——`init()` 中 `handlerCache = new ResourceCacheEntry<>(modelPath)`（:93-99），`CFG_GATEWAY_MODEL_PATH` 默认 `/nop/main/app.gateway.xml` 非空即启用（GatewayConfigs.java:20-21），`filterAsync` 每 HTTP 请求执行——确认。
  - E4 `DefaultDataAuthChecker:161` 硬编码 `getObject(true, …, CFG_AUTH_DATA_AUTH_CACHE_TIMEOUT.get())`——timeout 默认 **null**（NopAuthConfigs.java:73-74），`CacheEntryManagement` 3-arg 变体（:55-58）经 `isRefreshEnabled(Duration)` default（IResourceCacheEntry :15-19 `duration==null return false`）→ **clear 重建分支恒死**确认；链路 `JdbcQueryExecutor.transformEQL:191-195` → `GenSqlTransformer.transformFilter:217-222`（FILTER 语法标记触发）→ `DataAuthEntityFilterProvider.getEntityFilter:27-38` → `DefaultDataAuthChecker.getFilter:197-198 → getAuthModel:160-161`（无中间 memo）确认。**频率口径精化（不改判级）**：仅携带 auth filter 语法标记的 EQL（即启用 data-auth 的实体）触发，非全部 EQL——初审「启用 data-auth filter 的 ORM 部署下每 EQL 查询」表述按此理解。
  - E5 `XplLibTagCompiler:210/:212/:653/:655` `getObject(CFG_XPL_LIB_TAG_RELOADABLE.get())`——config 默认 **true**（XLangConfigs.java:30-31）确认；运行时动态标签调用链亲核：`XLang.getTagAction`（XLang.java:94-109）→ `IXplTag.invokeWithNamedArgs/generateXjson/generateNode/executeWithArgs`（IXplTag.java:85-109）→ `XplTag.getFunctionModel:93-94` → `XplLibTagCompiler.getFunctionModel:653-655`，每标签调用 1 次；「编译缓存命中的模板不经过」限定成立（模板编译期绑定函数模型；动态调用方实证 BizFilterEvaluator.java:97、WfActorAssignSupport.java:157）。
  - E6 `DynOrmModelProvider:72/:96` `getObject(CFG_ORM_MODEL_CACHE_CHECK_CHANGE.get(), …)`——config 默认 **true**（OrmConfigs.java:82-83）确认。
- (b) **GraphQLEngine 负结果成立**：loader `parseOperationFromText`（GraphQLEngine.java:260-268）纯文本解析不触 VFS → `loadObject` 建根后无子依赖 → `ResourceDependencySet.getDepends()` 返回 null（:113-116 `dependsMap==null → null`）→ `isAnyDependsChange(null)` 短路 false，**零 stat**（每请求仅付 1 次 CHM get+config 读+2 次 currentTimeMillis）。反例登记有效，防止误修。
- (c) **F3 补全（本轮独立 grep 新发现，补全不改判级、反而补强）**：初审 E1-E10 穷举遗漏 2 个直接使用方：
  - **⑤ `DefaultOrmModelProvider.interceptorCache`**（nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java:36-38）：`interceptorCache.getObject(OrmConfigs.CFG_ORM_INTERCEPTOR_CACHE_CHECK_CHANGE.get(), …)`，config 默认 **true**（OrmConfigs.java:78-79 "nop.orm.interceptor-cache-check-change"）；`OrmSessionFactoryBean:184` 默认装配 `new DefaultOrmModelProvider(sessionFactory)`，`SessionFactoryImpl.getLoadedOrmModel:150-152` 每 getOrmModel 调用穿透 → OrmTemplateImpl 多个每操作路径（:83-84/:154/:159/:456 等）触发——**频率与 E4 同级甚至更高**（每 ORM 模型获取），绕门直 walk。
  - **⑥ `XDefinition.getDefaultExtendsNode`**（nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/impl/XDefinition.java:77-84）：`this.defaultsDsl.getObject(true, …)` **硬编码 true 无门**，xdef 默认 extends 解析路径（编译期偏多，频率较低）。
  - 直接使用方实为 **6 个非 4 个**；C9-010 建议的「ResourceCacheEntry 层内建门」单点修复方案覆盖面相应扩大（亦覆盖 ⑤⑥），P2 维持。

**3. CL-014~016 / CL-035 / CL-036 处置**

| CL | 裁定 | 处置说明 |
|----|------|---------|
| CL-014 | 确认（残余形态） | 与 C1-CL-001/C5-CL-008 **三登记合并**：C1-CL-001「容量 0 无限自旋」已被 C1 复核驳回（IAE fail-fast 不可达）；C5-CL-008 深化可达性（4-arg 失配构造/DROP_NEWEST 黑洞）。CL-014 的「多生产者满载有界自旋无退避」与 C1 驳回理由相容，系该族第三登记残余面。Phase 6 合并裁定归该族。 |
| CL-015 | 确认 | 与 C2 关系注明：C2 两轮亲核其 await/signal **正确性**面裁定无缺陷并留档（C2-round2 :62/:385）；CL-015 是**吞吐形态**面（单锁吞吐上界+signalAll 惊群），不同 facet 无冲突。维持不立案展开（C8）。 |
| CL-016 | 确认 | 代码亲核成立（:101-112 每轮重置完整 timeout，m×timeout 契约违约）；C1 预注注明「Dedup 当前无生产调用方」（潜伏面）；归口 C8/C2 由整合者裁定。 |
| CL-035 | 确认（首立案） | 证据亲核成立（ClassModel.java:48 非 volatile + :275-292 双检锁）。跨维度关系：C2-round2 :46/:117 已检**同站点**但仅做锁序/重入分析（无环、无重入→不立案），未触及可见性面——本条为该站点首立案；与 C1-009 非 volatile 单例槽族**不同族**（实例字段 DCL vs 静态单例槽），无重复登记。归 C1/C2 裁定（本维度集外）。 |
| CL-036 | 确认（三登记合并） | 证据亲核成立（`substring(length-1)` 取末字符 "*"，`contains` 的 `startsWith("*")` 恒 false；fail-closed）。**行号修正**：本条所引 JsonWhitelist.java:21-27 应为 **:36-55**（add :36-44 / contains :46-55，`whitelistPrefixes.add(…)` 在 :43，与 C5-CL-025 所引 :43-49 对齐）。C5 复核已确认并显式注明「与 C1 轮备注、C9-CL-036 三登记合并裁定（Phase 6）」——**主登记归 C5-CL-025**。 |

### 二、事实性修正清单（复核发现，不改判级原则下订正）

1. **C9-002 putAll 子项证伪**：LocalCache.putAll 已覆写为 Caffeine 原生批量（LocalCache.java:229-231，git 核实自 6b08406549/2025-11-04 即存在，早于审计），MapCache:117-118、EhcacheCache:101-102 亦覆写——「LocalCache 未覆写」及第 1 轮每记录循环抽验表相应行有误，ICache default 逐条 putAll 全仓零可达站点。
2. **C9-006 核心证据证伪**（→驳回）：EhcacheCache.putIfAbsent 已原生覆写（EhcacheCache.java:106-108）；computeIfAbsent default 的 putIfAbsent 经虚分派落到覆写，synchronized(this) 在全部四个实现（含 NosqlCache:110-111）中零可达站点；审计建议①②已被审计前批次（87d09323c5/2026-10-05 javadoc 覆写义务警告 + plan 2448 裁定）实现。
3. **C9-010 F3 补全 2 站点**：DefaultOrmModelProvider.interceptorCache（每 ORM 模型获取，config 默认 true）、XDefinition.getDefaultExtendsNode（硬编码 true）——直接使用方 4→6。
4. **行号漂移修正**：C9-003 cachedGetter :782-792→:783-793；C9-004 resolveStaticMethodCollection :631-637→:630-639、invokeStaticMethodResolved :401-423→:400-414；CL-036 :21-27→:36-55。其余条目行号亲核一致。

### 三、F3 完备性复核

- C9-001 失效面 7 项（round2 扩展为 E1-E10 入口穷举）：经独立 grep（`.getObject(`/`.checkRefresh(`/`new ResourceCacheEntry`/`new CacheEntryManagement` 全仓）复核，**穷举达不动点成立**，唯一遗漏即上节补全 ⑤⑥（均并入 C9-010 面，不另立案）。
- C9-010 失效面 8 项中 TenantAwareResourceCacheEntry 包装层（:55 委托 getObject）与 CacheEntryManagement（:51/:57 委托）为透传层非独立站点，判定正确。
- 串行化点清单 S1-S11：round2 抽验 S1/S2/S11（+S9 附带）维持原判，本轮对照 C2-round2 交叉验证（ClassModel.getBeanModel、ClassExtension 等排除面与 C2 留档一致），无异议。

### 四、现状标注

- 9 条保留发现全部为 **已知未修复**（缺陷本体在 HEAD 8c13908bfd 亲核在案；该 commit 为审计 docs 提交，其后零代码提交；涉及代码文件的最后实质改动均早于审计日）。
- C9-006 驳回项不涉修复；其防御性备注（新增 ICache 实现覆写义务）已由 ICache javadoc 现行文本承载。
- ResourceTimestampCache 死代码自 ef5086e2eb（2023-03-07 初版即注释态）持续至今，与 C7-007 建议④（清理死代码死配置）同一整改面。

## 最终保留项

（判级以本节为准；现状全部为已知未修复）

| # | 名称 | 终裁判级 | 裁定依据 | 现状 |
|---|------|---------|---------|------|
| C9-001 | 模型缓存命中路径全依赖图遍历+裸 stat（ResourceTimestampCache 死代码，意图未实现） | **P2** | 门内短路/门外全量亲核；D 不含虚拟根口径+量化链算术成立；NFS 升级条件表述合规；与 C7-007（P1 正确性面）同链两面判级自洽 | 已知未修复 |
| C9-004 | 生成代码静态方法调用每次 Class.forName（未复用 CLASS_MODELS 缓存模式） | **P2** | 证据与量化链（150-550ns/调用）亲核成立，满足 P2 量化支撑 | 已知未修复 |
| C9-010 | ResourceCacheEntry 直接使用方绕过 refreshMinInterval 门（E3 gateway 每 HTTP 请求/E4 data-auth 每实体查询/E5 xpl 动态标签/E6 dyn-orm，**F3 补全至 6 使用方**：+DefaultOrmModelProvider.interceptorCache/XDefinition） | **P2** | 四使用方+补全 2 站点亲核成立；GraphQLEngine 零 stat 负结果核实；E4 timeout=null 死 clear 分支核实；单点修复（entry 层内建门）覆盖面扩大 | 已知未修复 |
| C9-002 | LocalCache 无条件 recordStats + ResourceLoadingCache 全线 useMetrics（每次命中两层统计常数） | P3 | 核心证据成立；**putAll 子项剔除**（LocalCache 已覆写为 Caffeine 原生批量，初审事实性错误） | 已知未修复 |
| C9-003 | XLangSemantics 生成代码属性访问 2 层 CHM（解释器为单槽零查找）+ Class 强键永不淘汰 | P3 | 证据亲核成立（行号微漂修正）；ns 级/访问无量化竞争，P3 校准合规 | 已知未修复 |
| C9-005 | CycleSynchronizer 持锁执行外部回调（waitCycleCompleted 内联 + resolvePromise 串行全部回调） | P3 | 证据亲核成立；非每请求路径，P3 校准合规 | 已知未修复 |
| C9-007 | DictProvider.getDict 命中路径每次分配三元缓存键 + 无 cache 调用方 deepClone/逐 option i18n | P3 | 证据亲核成立；自降理由（线上占比未证实）合规 | 已知未修复 |
| C9-008 | ExecutorHelper submit/schedule 每任务 2 分配+bindCancel+无条件 detachContext | P3 | 三站点亲核成立；百 ns 级/任务，P3 校准合规 | 已知未修复 |
| C9-009 | LocalResourceLockManager 周期维护全表 O(N)+逐锁 monitor + releaseLockForLocker 线性扫 + 共享 5 线程 globalTimer | P3 | 证据与量化链亲核成立（timer 默认 5 确认）；无大 N 证据，P3 校准合规 | 已知未修复 |
| ~~C9-006~~ | ~~ICache.putIfAbsent default 全实例 synchronized + EhcacheCache 继承~~ | **驳回** | 核心证据证伪：EhcacheCache.putIfAbsent 原生覆写（:106-108）、四实现零可达 synchronized 站点、javadoc 覆写义务警告已在（87d09323c5）；残留仅为防御性备注 | 初审误判 |

CL 线索终态（均不立案展开）：CL-014 确认（与 C1-CL-001 驳回/C5-CL-008 三登记合并，残余=有界自旋无退避）；CL-015 确认（吞吐形态面，与 C2 正确性排除不同 facet）；CL-016 确认（m×timeout 契约违约，潜伏无生产调用方）；CL-035 确认（首立案，归 C1/C2；C2 仅做过同站点锁序面）；CL-036 确认（行号修正 :36-55，主登记归 C5-CL-025，三登记合并 Phase 6 裁定）。

**复核统计**：10 条发现——保留 9（P2×3 维持 / P3×6 维持）、驳回 1（C9-006）；事实性修正 4 处（C9-002 putAll 子项、C9-006 主体、C9-010 F3 补 2 站点、行号漂移 4 条目）；CL×5 全部确认并注明跨维度归属；F3 补全不改判级。终态与 C7-007 终裁交叉一致（checker 层修复同步收敛正确性面 + stat 税性能面）。
