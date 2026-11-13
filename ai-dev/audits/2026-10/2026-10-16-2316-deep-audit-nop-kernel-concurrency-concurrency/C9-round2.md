# C9 请求热路径审计（第 2 轮深挖收口，盲审）

## 审计单元

- **维度**：C9——请求热路径（并发高性能形态：锁竞争频率与常数因子量化、锁内开销、ThreadLocal 频率、缓存命中常数因子、串行化点、per-record 循环）
- **模块**：nop-kernel 并发设施自身
  - A 面：`nop-kernel/nop-commons/src/main/java/io/nop/commons/{concurrent,cache,pool}`（85f/7,550）
  - B 面：全局模型缓存与注册表单例（ResourceComponentManager、ResourceLoadingCache/ResourceCacheEntry、ReflectionManager、XLangSemantics、EvalGlobalRegistry、GlobalFunctions、StdDomainRegistry、DictProvider、I18nMessageManager、TenantAware* 包装、ResourceDependsManager、DefaultResourceChangeChecker）
- **仓库**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）

## 本轮输入

1. 第 1 轮报告 `C9-hot-path.md`（C9-001~009：P2×2 / P3×7；串行化点清单 S1-S11；基准缺口 B1-B3；CL-014~016）。
2. CL-014~016 + 11 站点串行化点清单 + live code（全部以当前工作区代码为准，逐文件重读）。

## 盲审纪律声明

本轮为盲审第 2 轮：**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件，唯一例外为本维度第 1 轮文件 `C9-hot-path.md`（必要输入）。未读取同目录 C1–C7 及各 round2 文件。C2 轮已立案的锁正确性问题（E1~E5 锁序环）不重复，只量化其正常路径。所有结论以 live code 为准；无法量化的条目一律 P3「优化候选」或归入 CL 线索。

## 本轮目标（收口轮）

- ① C9-001 失效面扩展：穷举全部走 `ResourceCacheEntry.getObject/isChanged` 的调用入口（B 面 i18n/dict/组件加载各链），量化各入口每请求 stat 次数；核实 refreshMinInterval 默认值与门语义。
- ② 热路径常驻开销终表：补全「每请求必经」常驻开销站点并量化，产出修复优先级排序输入。
- ③ 锁竞争量化收口：对第 1 轮判定「无热点」的站点抽验 3 处；补登新高频锁站点（若有）。
- ④ 基准缺口判定：B1-B3 是否需要在本单元补微基准（判据：仅当影响 P1/P2 判级）。

---

## ① C9-001 失效面扩展：getObject/isChanged 调用入口穷举与 per-request stat 量化

### 1.1 机制核实（live code 逐行确认）

- **默认值**：`CFG_COMPONENT_RESOURCE_CACHE_CHECK_CHANGED` 默认 **true**（CoreConfigs.java:86-87）；`CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL` 默认 **100**（ms，CoreConfigs.java:104-105）。
- **门语义**（ResourceLoadingCache.java:190-193）：`b = checkChanged.get() && entry.isRefreshEnabled(cacheRefreshMinInterval.get())`；`isRefreshEnabled`（ResourceCacheEntry.java:103-106）= `now - lastLoadTime > refreshMinInterval`。**门内（加载后 100ms 内）b=false，`getObject` 跳过 `isChanged`，纯字段读返回；门外每次 get 全量执行 `isChanged()` 依赖图遍历**。稳态（请求间隔>100ms）下门恒开，第 1 轮 C9-001 判定成立。
- **遍历结构核实（对第 1 轮 D 值口径的精确化）**：`loadObject` 建根 `VirtualResourceDependencySet("virtual:"+path)`（MockReference，`lastModified=CoreMetrics.currentTimeMillis()`，**根不产生 stat**，ResourceCacheEntry.java:219、VirtualResourceDependencySet.java:15-25）。`isChanged()` → `isAnyDependsChange(deps.getDepends())` 只遍历**子依赖集**（ResourceDependsManager.java:214-255）：每子 dep = 1 次 `resolveResource`（路径校验 + DeltaResourceStore 逐层解析，:298-322）+ 1 次 `IResource.lastModified()`（FileResource.java:70-71 = `java.io.File.lastModified()` 裸 stat）+ 1 个 `ResourceChangeCheckResult` 分配 + 1 次 `deps.setLastModified()` volatile 写 + HashSet add。**D = 加载期间实际 trace 的真实资源文件数（模型自身文件 + x:extends 父链 + import 资源），不含虚拟根**。i18n 链的 trace 已证实：`DeltaJsonLoader.loadFromResource` 第 78 行显式 `traceDepends(resource.getPath())`（每个 yaml 文件 1 子依赖）。
- **每 get 固定伴随**：3 次 config ref 读（checkChanged/refreshMinInterval/cacheNull，链长 2-4 字段读 ≈ 5-15ns/次）+ 2 次 `CoreMetrics.currentTimeMillis()`（volatile clock 委托）+ 无条件 `traceAllDepends`（1 次 ThreadLocal get，栈空即短路，ResourceCacheEntry.java:204-206）+ Caffeine recordStats + Micrometer（C9-002）。

### 1.2 入口穷举表（全部 getObject/isChanged 调用链 × 每请求 stat 量化）

| # | 入口链 | 缓存实例 | checkChanged 来源 | 门 | 单次 stat 数 | 每请求频率（量化口径） |
|---|--------|----------|-------------------|-----|--------------|------------------------|
| E1 | `ResourceComponentManager.loadComponentModel(path[/transform])` → model-cache-{type}（ResourceComponentManager.java:386-390/:334-344） | model-cache-*（TenantAware 包装） | ResourceLoadingCache.get 内部（默认 true） | **有**（100ms） | D_model（xbiz≈10-30、xlib≈1-5、dict≈1-2、validator≈1-3、xmeta/orm≈1-5） | 每请求模型获取 3-10 次（第 1 轮口径）；kernel 内证实调用方：XLang.loadTpl/getTag/getTagAction、SchemaLoader、XplLibHelper、DictProvider.defaultLoadDictBean、BizValidatorHelper.runValidator、RecordMappingManagerImpl、MarkdownTool |
| E2 | `I18nMessageManager.getMessage/getLocaleMessages` → i18n-cache（I18nMessageManager.java:48, 168-186） | i18n-cache | ResourceLoadingCache.get（默认 true） | **有**（100ms） | 每次消息解析 **1-3 次** `localeToMessages.get`（locale 未命中回退 parentLocale/defaultLocale，:176-183）× D_i18n（该 locale 全部 yaml 文件 + x:extends 父链，≥1） | **每条 i18n 消息解析 1 次**（错误消息渲染、字典 translateToLocale 逐 option、模板 @i18n: 变量）——全表最高频入口 |
| E3 | **`GatewayHttpFilter.filterAsync` → `handlerCache.getObject(true, handlerLoader)`**（nop-gateway GatewayHttpFilter.java:100-127） | 裸 `ResourceCacheEntry`（init 时 `new ResourceCacheEntry<>(modelPath)`） | **硬编码 true** | **无**（getObject 直调，绕过 refreshMinInterval） | D_gateway（/nop/main/app.gateway.xml 及其 import，≈1-10） | **每个经过 gateway filter 的 HTTP 请求 1 次**（默认 model-path 非空即启用）——**C9-010** |
| E4 | **`DefaultDataAuthChecker.getAuthModel` → `modelCache.getObject(true, loader, timeout)`**（nop-auth-service DefaultDataAuthChecker.java:160-161） | data-auth-cache（CacheEntryManagement→ResourceCacheEntry/TenantAware） | **硬编码 true** | **无**（3-arg timeout 仅做到期 clear 重建，默认 `CFG_AUTH_DATA_AUTH_CACHE_TIMEOUT=null` 时 clear 分支恒 false，:31-35） | D_auth≈1-2（data-auth 配置文件） | **每次数据权限检查 1 次**；`DataAuthEntityFilterProvider.getEntityFilter`（nop-orm DataAuthEntityFilterProvider.java:28-38）在 `JdbcQueryExecutor.transformEQL`（:191-195）中**每 EQL 查询执行**调用 → **每实体查询 1-2 stat**——**C9-010** |
| E5 | **`XplLibTagCompiler.getFunctionModel/buildCallExpression` → `cachedCompiledTag.getObject(CFG_XPL_LIB_TAG_RELOADABLE.get())`**（XplLibTagCompiler.java:210-212/:653-655） | 裸 `ResourceCacheEntryWithLoader`（每 tag 一个） | config 默认 **true**（XLangConfigs.java:30-31） | **无**（getObject 直调） | D_tag（xlib 文件 + compile 期间 import，≈1-5） | 每次**运行时标签调用**（`IXplTag.invoke*` → `buildTagArgValues` → `tag.getFunctionModel()`，IXplTag.java:86-109、XplTag.java:93-94、XplTagHelper.java:77；动态调用方如 WfActorAssignSupport.java:157 每 workflow actor 解析 1 次）；编译缓存命中的模板不经过——**C9-010** |
| E6 | `DynOrmModelProvider` → `cache.getObject(CFG_ORM_MODEL_CACHE_CHECK_CHANGE.get(), …)`（nop-dyn-service DynOrmModelProvider.java:72/:96） | 裸 CacheEntry/WithLoader | config 默认 **true**（OrmConfigs.java:82-83） | **无** | D_dyn≈1-5（动态 orm 模型链） | 动态模型部署下每 orm 模型获取——**C9-010** |
| E7 | `GraphQLEngine.parseOperation` → `documentCache.get(query).getObject(CFG_GRAPHQL_PARSE_CACHE_CHECK_CHANGED.get())`（GraphQLEngine.java:321；config 默认 true，GraphQLConfigs.java:68-69） | graphql-document-cache（LocalCache<ResourceCacheEntryWithLoader>） | config 默认 true | **无** | **0**（负结果）：loader 为纯文本解析（parseOperationFromText，:260-268），不触 VFS → 子依赖集为空 → `getDepends()==null` → `isAnyDependsChange(null)` 直接 false，**零 stat** | 每 GraphQL 请求 1 次，但每次仅付 ~数十 ns（1 CHM get + 1 config 读 + 2 currentTimeMillis） |
| E8 | `ResourceDependsManager.runWhenDependsChanged` → depends-with-cache（ResourceDependsManager.java:44/:126-128） | depends-with-cache | ResourceLoadingCache.get（默认 true） | 有 | D_task | 仅 `TemplateFileGenerator`（codegen，:290）——冷路径 |
| E9 | `ResourceLoadingCache.checkRefresh` → `entry.checkRefresh` → `isChanged()`（ResourceLoadingCache.java:175-181） | 任意 | forceRefresh=false 时无条件 walk | 无 | D | **kernel main 无调用方**（grep 证实）——死路径，不立案 |
| E10 | `state_saveTo/loadFrom`、`getCacheEntryState`（synchronized） | 任意 | — | — | — | 预热/管理路径，非请求路径 |

**① 结论**：C9-001 失效面确认并扩展——第 1 轮 F3 第 1 项（i18n 每条消息受累）经 trace 链证实成立（E2，且修正精确口径：每消息 1-3 次 get、D 不含虚拟根）；**新发现 4 个绕过 100ms 门的直接使用方（E3-E6）**，其中 gateway（每 HTTP 请求）与 data-auth（每实体查询）为每请求必经，立案 **C9-010**。GraphQL 文档解析缓存为**负结果**（deps 空短路，零 stat），从 C9-001 修正面中明确排除。

### 1.3 新发现

### [nop-kernel-concurrency-C9-010] ResourceCacheEntry 直接使用方绕过 refreshMinInterval 门：门只实现在 ResourceLoadingCache.get 一层，4 处生产调用方硬编码/config 默认 checkChanged=true，gateway 每 HTTP 请求、data-auth 每实体查询、XPL 标签运行动态调用每标签、dyn-orm 每模型获取均无条件执行全依赖图 stat 遍历

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceCacheEntry.java:176-208`（getObject(checkChanged) 无门）、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceLoadingCache.java:190-193`（门仅在此层）、`nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java:100-127`、`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/auth/DefaultDataAuthChecker.java:160-161` + `nop-persistence/nop-orm/src/main/java/io/nop/orm/filter/DataAuthEntityFilterProvider.java:28-38` + `nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java:191-195`、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/xlib/XplLibTagCompiler.java:210-212,653-655` + `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/IXplTag.java:86-109`、`nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynOrmModelProvider.java:72,96`
- **证据片段**:
  ```java
  // 门只存在于 ResourceLoadingCache.get（第 1 轮 C9-001 的路径）
  boolean b = checkChanged.get() && entry.isRefreshEnabled(cacheRefreshMinInterval.get());
  // 而直接持有 ResourceCacheEntry 的使用方全部直调 getObject，无任何时间门：
  handler = handlerCache.getObject(true, handlerLoader);            // Gateway，硬编码 true
  return modelCache.getObject(true, this::loadDataAuthModel, ...);  // DataAuth，硬编码 true
  compiledTag = this.cachedCompiledTag.getObject(CFG_XPL_LIB_TAG_RELOADABLE.get()); // 默认 true
  return cache.getObject(CFG_ORM_MODEL_CACHE_CHECK_CHANGE.get(), k -> {...});       // 默认 true
  ```
- **严重程度**: P2
- **并发场景**: 全部 worker 线程每请求并行触发：gateway 部署下每 HTTP 请求 1 次（E3）；启用 data-auth filter 的 ORM 部署下每 EQL 查询 1 次（E4，`transformEQL` → `getEntityFilter` → `getAuthModel`）；含动态标签调用的 xpl 执行每标签 1 次（E5）；动态模型部署每 orm 模型获取 1 次（E6）。无锁竞争（遍历无锁），纯每请求常数 I/O 税，与 C9-001 同为读路径共享 volatile 写（`deps.setLastModified`，正确性归 C1/C2）。
- **触发时序**: T（请求线程）→ `getObject(true, loader)` → `object != null` 但 `checkChanged && isChanged()` 为真值判断（ResourceCacheEntry.java:178）→ `isAnyDependsChange` 分配 `new HashSet<>()` → 递归 D 个子依赖 → 每 dep `resolveResource`（路径校验 + DeltaStore 解析）+ 1 次 `File.lastModified()` stat + 1 个 `ResourceChangeCheckResult` + 1 次 volatile 写。**E3/E4/E5 不经过 `isRefreshEnabled`，100ms 门完全不生效，稳态每次调用恒走全程**；E4 的 3-arg timeout 变体在默认 `CFG_AUTH_DATA_AUTH_CACHE_TIMEOUT=null` 时 `isRefreshEnabled(null)` 恒 false，连周期性 clear 重建也不发生，等效于纯裸 walk。
- **现状**: refreshMinInterval 门是 `ResourceLoadingCache.get` 的私有实现，`ResourceCacheEntry.getObject` 的公开协议将「是否检查」完全交予调用方；仓库内 4 个直接使用方全部以常量/默认 true 传入。即平台对「新鲜度检查成本」的唯一节流装置在最主要的高频使用方上未生效。E3 处 `handlerCache` 为单条目裸 entry（`init()` 中按 `CFG_GATEWAY_MODEL_PATH` 默认值 `/nop/main/app.gateway.xml` 创建），无 LocalCache 层、无指标、无 TTL。
- **风险**: 量化链：单 dep = 路径校验+DeltaStore 解析（~100-500ns）+ stat（本地 page-cache 热 1-5μs，bind-mount/NFS 50-500μs）+ 2 次分配（~100B）。E3：D_gateway≈5 → 每请求 +5-25μs（本地）/ +0.25-2.5ms（NFS），1,000 QPS 下 5k-25k stat/s（本地 5-100ms CPU/s，NFS 0.25-12.5 核纯 stat）。E4：每查询 1-2 stat → 1,000 QPS × 2 查询/请求 = 2k-4k stat/s。E5：动态标签调用每标签 1-5 stat。全部为**可并行摊薄**的延迟项而非串行化点，故 P2 而非 P1；NFS/慢存储下 E3 有升级条件（同 C9-001 升级口径）。
- **建议**: ① 在 `ResourceCacheEntry` 层内建门（`isChanged` 结果按 entry 级 `lastCheckTime` 短 TTL 记忆化，对齐 isRefreshEnabled 语义），使直接使用方默认获得节流——单点修复覆盖 4 个使用方；② 或逐点修：E3/E4 将硬编码 true 改为经 ResourceLoadingCache（gateway handlerCache 改用 `ResourceLoadingCache` 单条目实例；data-auth 改用 `runWhenDependsChanged`/ResourceLoadingCache，天然获得门+指标+GlobalCacheRegistry 注册）；E5 将 `CFG_XPL_LIB_TAG_RELOADABLE` 默认改 false（生产不可变标签编译一次）或经 ResourceLoadingCache；③ 无论哪种，同时修复 C9-001 的根因（时间戳缓存/结果记忆化）则 E1-E6 全部受益。
- **失效面枚举**（F3——`ResourceCacheEntry.getObject/isChanged` 直接调用全清单，grep `.getObject(`/`.checkRefresh(` 全仓复核）:
  1. `GatewayHttpFilter.handlerCache`（:127，硬编码 true，每 HTTP 请求，受累最重）；
  2. `DefaultDataAuthChecker.modelCache`（:161，硬编码 true，经 CacheEntryManagement 3-arg 变体，timeout 默认 null 使 clear 分支失效，每实体查询，受累次重）;
  3. `XplLibTagCompiler.cachedCompiledTag`/`cachedCompiledTagForNodeMode`（:210/:212/:653/:655，config 默认 true，运行时动态标签调用路径；编译缓存命中的静态模板不经）;
  4. `DynOrmModelProvider.cache`（:72/:96，config 默认 true，动态部署）;
  5. `GraphQLEngine.documentCache` 条目（:321，config 默认 true 但 **deps 恒空 → isChanged 恒 false，零 stat，不受累**，登记为反例防止误修）;
  6. `ResourceLoadingCache.get`（有门，C9-001 口径，不重复立案）;
  7. `checkRefresh` 外部入口（无 kernel main 调用方，不受累）;
  8. `TenantAwareResourceCacheEntry`（tenant 启用时为 2-4 的包装，多 1 次 ThreadLocal get + CHM get，默认 tenant 关闭）。
- **信心水平**: 确定（4 个调用点代码链路完整、config 默认值逐一核实；stat 成本为公开常数）
- **误报排除**: 不与 C9-001 重复——001 立案的是「有门但门外恒走」的 ResourceLoadingCache 层，本条立案的是「完全无门」的直接使用协议缺陷，修复面不同（001 修遍历成本，本条修门缺失）；不属于 C2 锁正确性——此处无锁主张；E7 GraphQL 已实测排除，避免把「每请求调用 getObject」误当「每请求 stat」。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2，F3 补全 2 站点（补全不改判级、反而补强）**。(a) 四使用方逐一亲核成立：E3 GatewayHttpFilter:127 硬编码 true（init :93-99 + CFG_GATEWAY_MODEL_PATH 默认非空，每 HTTP 请求）；E4 DefaultDataAuthChecker:161 硬编码 true + CFG_AUTH_DATA_AUTH_CACHE_TIMEOUT 默认 null（NopAuthConfigs:73-74）→ CacheEntryManagement:55-58 经 isRefreshEnabled(Duration) default（null→false）**clear 重建分支恒死**核实；链路 transformEQL:191-195→GenSqlTransformer.transformFilter:217-222（FILTER 语法标记触发）→DataAuthEntityFilterProvider:27-38→getFilter:197-198→getAuthModel:160-161 无中间 memo——**频率口径精化**：仅携带 auth filter 标记的 EQL（启用 data-auth 的实体）触发，非全部 EQL；E5 XplLibTagCompiler:210/212/653/655 + CFG_XPL_LIB_TAG_RELOADABLE 默认 true（XLangConfigs:30-31），动态调用链亲核（XLang.getTagAction→IXplTag.invokeWithNamedArgs:85-109→XplTag:93-94→getFunctionModel:653-655），「编译缓存命中的模板不经过」限定成立（BizFilterEvaluator:97/WfActorAssignSupport:157 动态调用方实证）；E6 DynOrmModelProvider:72/96 config 默认 true（OrmConfigs:82-83）。(b) GraphQLEngine 负结果（零 stat）核实成立：parseOperationFromText:260-268 纯文本解析不触 VFS→getDepends()==null（ResourceDependencySet:113-116）→isAnyDependsChange 短路 false。(c) **F3 补全**：初审 E1-E10 穷举遗漏 2 个直接使用方——⑤ `DefaultOrmModelProvider.interceptorCache`（DefaultOrmModelProvider.java:36-38，CFG_ORM_INTERCEPTOR_CACHE_CHECK_CHANGE 默认 true（OrmConfigs:78-79），OrmSessionFactoryBean:184 默认装配，SessionFactoryImpl.getLoadedOrmModel:150-152→OrmTemplateImpl 多个每操作路径，**频率与 E4 同级或更高**）；⑥ `XDefinition.getDefaultExtendsNode`（XDefinition.java:77-84，硬编码 true 无门，编译期偏多）。直接使用方实为 **6 个非 4 个**，「ResourceCacheEntry 层内建门」单点修复覆盖面相应扩大。终裁 P2 维持。现状：新发现（本 campaign），已知未修复（HEAD 8c13908bfd）。

---

## ② 热路径常驻开销终表（每请求必经站点 × 量化）

量化口径：单次成本为 live code 常数估计（公开实现常数）；「每请求数量级」基于第 1 轮 3-10 次模型获取/请求 + 本轮入口频率证据（E2 每消息、E4 每查询）。**修复优先级按「每请求总成本」降序即表序**（P2 修复项置顶）。

| 优先 | 站点 | 每 Request 必经 | 每请求次数级 | 单次成本 | 每请求总成本（本地盘口径） | 关联 |
|------|------|-----------------|--------------|----------|---------------------------|------|
| 1 | `ResourceCacheEntry.isChanged` 全依赖图 walk（E1） | 是（模型获取） | 3-10 次 × D_model(10-30) = **30-300 stat** | 1.1-5.6μs/dep（resolve+stat+分配） | **30μs-1.7ms**；NFS ×50 | C9-001（P2） |
| 2 | **Gateway 直调 getObject walk（E3）** | gateway 部署每请求 | 1 次 × D_gateway(1-10) | 同上 | 1-50μs；NFS 0.05-5ms | **C9-010（P2）** |
| 3 | **DataAuth getObject walk（E4）** | 启用时每实体查询 | 查询数(1-5) × D_auth(1-2) | 同上 | 2-50μs；NFS 0.1-5ms | **C9-010（P2）** |
| 4 | **XPL 标签动态调用 walk（E5）** | 动态标签路径每标签 | 0-10 次 × D_tag(1-5) | 同上 | 0-250μs | **C9-010（P2）** |
| 5 | i18n 每消息 walk（E2） | 每条 i18n 消息 | 消息数(1-50) × 1-3 get × D_i18n(≥1) | 1.1-5.6μs/dep + 3 config 读 | 1-800μs（选项渲染页最重） | C9-001 修正面（P2 伴生） |
| 6 | 生成代码静态调用 Class.forName（XLangSemantics.resolveStaticMethodCollection） | 生成代码执行 | 每静态调用 1 次 | 150-550ns | 万次调用级路径 1.5-5.5ms | C9-004（P2） |
| 7 | 生成代码属性访问 2 层 CHM（PROP_ACCESSORS） | 生成代码执行 | 每属性访问 1 次 | 30-80ns | 千次属性访问 30-80μs | C9-003（P3） |
| 8 | `JsonWhitelist.contains` + `getBeanModelForClass` | JSON 响应序列化（`CFG_JSON_SERIALIZE_ONLY_DATA_BEAN` 默认 **true**，CoreConfigs.java:182-183） | 每序列化对象 1 次 | whitelist：1 CHM-set hash + 前缀集 startsWith 循环 ≈ 50-200ns；getClassModel：2 次弱键 map get（registeredClassModels + introspectCache，弱引用 hash ≈ 60-150ns）+ recordStats | 千对象响应 0.1-0.4ms | 本轮新量化（无立案，健康）；前缀注册功能缺陷见 CL-036 |
| 9 | `ContextProvider.currentContext/currentTenantId/currentLocale`（volatile `_instance` 读 + `t_context.get()`） | 是（遍布全链） | 模型命中路径 2-4 次 + 每条 i18n 消息 1 次 + 业务层若干 | 5-15ns | <1μs | 第 1 轮已量化，健康 |
| 10 | `ResourceLoadingCache.get` 固定伴随（3 config ref 读 + 2 currentTimeMillis + traceAllDepends ThreadLocal get） | 每次模型/i18n get | 5-60 次 | ~30-80ns 合计 | 0.2-5μs | C9-002 同层常数 |
| 11 | LocalCache recordStats + Micrometer（useMetrics） | 每次缓存 get/put | 5-60 次 | 40-125ns | 0.2-7.5μs | C9-002（P3） |
| 12 | `TenantAwareResourceLoadingCache.getCache`（tenant 判定：ThreadLocal get + isEmpty；tenant 启用时另加 `isSupportTenant` 前缀集迭代 + moduleName 解析 + `CFG_RESOURCE_NOT_SUPPORT_TENANT_CACHE.get()`） | tenant 启用部署 | 每模型获取 1 次 | 关闭：~10ns；启用：+100-500ns | 启用时 0.3-5μs | ResourceTenantManager.java:91-124（本轮核实；`enabledTenantPaths` 惰性缓存后为 1-2 次 startsWith） |
| 13 | `ReflectionManager.getClassModel`（2 弱键 map get） | 反射/xlang/JSON | 数十次 | 60-150ns | 3-15μs | S2 抽验对象，健康 |
| 14 | `makeModelCache`/`findModelTypeFromPath`（CHM get ×2 + fileType 子串） | 每模型获取 | 3-10 次 | ~40-80ns | 0.1-0.8μs | S11 抽验对象，健康 |
| 15 | `EvalGlobalRegistry`/`StdDomainRegistry`/`DictProvider.getDict` 缓存键 | eval 期/字典渲染/编译期 | 各 0-数十次 | CHM get 15-40ns；getDict 键分配 30-60ns | <10μs | C9-007（P3）；StdDomainRegistry 为编译期为主，非命中路径 |
| 16 | GlobalFunctions 查找（OBJ_FUNCTION_HANDLES 1 层 CHM + 单槽字段读） | xlang 全局函数调用 | 每调用 1 次 | ~20-50ns | <5μs | C9-003 F3 已覆盖 |
| 17 | GraphQL 文档缓存 getObject（deps 空） | 每 GraphQL 请求 | 1 次 | ~30-80ns（零 stat） | <0.1μs | 本轮负结果（1.2-E7） |

**终表结论**：每请求常驻成本的前四位全部是同一个根因（依赖图 stat walk）的四个入口——C9-001 + C9-010 一并修复即可消除表中 1-5 行约 90% 的常驻 I/O 税；其后为 C9-004/003 的生成代码常数；8-17 行为亚微秒级健康常数，不建议单独修复。

---

## ③ 锁竞争量化收口

### 3.1 「无热点」抽验（3 处，读代码确认判定理由）

- **S1 `ResourceCacheEntry.getObject` 命中路径不持锁——判定确认**。命中路径（object 非空且未变更，:176-178）只做 volatile 字段读 + isChanged 遍历，**不进入任何 synchronized**；`synchronized(this)` 仅在 miss/reload（:179）、`clear`（:112）、`getCacheEntryState`（:90）、`reloadObject`（:227）——后两者为预热/管理路径。同 path 并发 miss 在 entry monitor 上排队属设计内串行（第 1 轮量化不变）。
- **S2 `ReflectionManager` per-class 首次 miss——判定确认**。命中路径 = `registeredClassModels.get`（弱键 CHM）+ `introspectCache.get`（Caffeine 弱键，ReflectionManager.java:329-339），无锁；miss 路径 `tempLoaders.computeIfAbsent`（CHM bin 锁，锁内仅建 loader 对象）+ `ClassModelLoader.load()` per-class monitor（:202-210，锁内重量级构建但一次性，且记忆化防并发重复构建）。管理操作 `clearIntrospectCache`（:377）非请求路径。
- **S11 `ResourceComponentManager.makeModelCache`——判定确认**。`modelCaches.computeIfAbsent`（:433-441）命中为一次无锁 CHM get；锁内仅 `makeLoadingCache` 构建（一次性）；异 modelType 无竞争。注意锁内含 `GlobalCacheRegistry.register`（仅构建期一次，非每次）。
- **附加抽验 S9 `RoundRobinSupplier.get`（:40-45）**：`AtomicReferenceArray` + `AtomicInteger.getAndIncrement` 无锁，判定确认。**误报排除记录**：抽验中曾怀疑 `Math.abs(nextIndex.getAndIncrement() % length)` 在计数器回绕（2^31 次调用）后产生负索引——核实为**非问题**：模运算先于 abs，结果有界于 (-length, length)，length < 2^31 时 abs 恒非负，无越界。

### 3.2 新高频锁站点排查（grep 全 A 面 + B 面 synchronized）

全量 grep 复核（nop-commons {concurrent,cache,pool} + nop-core {resource,dict,i18n,lang/json,reflect} + nop-xlang {exec,xdef,xpl}）：第 1 轮清单 S1-S11 无遗漏的每请求锁站点。新增候选逐一排查：

- `ClassExtension` 全方法 synchronized 含读方法 `getHelperMethods`（ClassExtension.java:21-49）——**仅 ClassModelBuilder 构建期调用**（ClassModelBuilder.java:602-627），非请求路径，不立案。
- `ClassModel.getBeanModel` 双检锁（ClassModel.java:274-292）——初始化后命中为 1 次字段读（~1ns，健康）；但 `beanModel` 字段**非 volatile**（:48），存在双检锁无 volatile 的可见性/安全发布缺口——正确性归 C1/C2，登记 **CL-035**，本维度只确认命中常数健康。
- `XplLibTagCompiler.compile()` synchronized（:734）——编译期，非请求路径。
- `DefaultVirtualFileSystem.refresh/destroy` synchronized（:70/:87）——dev 热替换/关闭路径，无每请求调用方（grep 证实），不立案。
- `InMemoryTextResource.writeText/cloneInstance`、`NioFileWatchService.watch/doStop`、`AutoCloseZipEntryResource.getEntry`——sys 模板/监听/zip 冷路径，不立案。

**③ 结论**：8 处「无热点」判定抽验 3+1 处全部维持原判；**无新高频锁站点**。热路径锁面收口为：请求路径零互斥（除 C9-005/S3 周期边界与 C9-009/S4 周期维护，均已立案）。

---

## ④ 基准缺口判定

判定标准（本轮指令）：仅当缺口影响 P1/P2 判级时才在本单元补微基准；否则登记为 C10 缺保护候选。

- **B1（ResourceLoadingCache 命中 stat 税，关联 C9-001 P2）→ 不补，登记 C10 候选**。理由：C9-001 的 P2 判级建立在代码路径确定性（本轮逐行复核维持）+ stat 成本的存储依赖区间（本地 1-5μs / NFS 50-500μs）。本地微基准只能证实区间低端（page-cache 热路径），而 P2↔P1 边界由**部署存储形态**决定，本地基准无法裁决；判级不因该缺口改变。
- **B2（recordStats/useMetrics 常数税，关联 C9-002 P3）→ 不补**。P3 判级本身即「量化后的优化候选」，基准只影响收益估计不影响判级。
- **B3（XLangSemantics 属性访问，关联 C9-003 P3）→ 不补**。同 B2。
- **新增 B4 候选（C9-010 门缺失税）**：`GatewayHttpFilterGetObjectBenchmark`/`DataAuthGetObjectBenchmark`（稳态命中 × tmpfs/bind-mount 两象限，`-prof perfnorm` 看 syscalls）——同样不改变 P2 判级（判级依据是「每请求无条件 walk」的代码事实），登记 C10。
- **设施现状更正**：第 1 轮 B4 记录「未检索到现有 JMH 基准」——本轮确认 `nop-benchmark/` 模块存在（json/orm/stream/xlang/xpl 五个子模块，含 JMH 基建），但确无 cache/deps/gateway 方向的基准；B1-B4 均可挂载于 `nop-benchmark`（B3 可入 `nop-benchmark-xlang`）。

**④ 结论**：本轮不建微基准；B1-B4 全部登记为 C10 缺保护候选（附挂载位置建议）。

---

## 维度集外线索（不立案展开）

### [nop-kernel-concurrency-CL-035]（维度集外线索，C1/C2 形态）ClassModel.getBeanModel 双检锁的 beanModel 字段缺 volatile

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/impl/ClassModel.java:48, 274-292`
- `private IBeanModel beanModel;`（:48，非 volatile）+ 双检锁惰性构建（:277-291）。无 volatile 的双检锁在 JMM 下存在半初始化对象经数据竞争发布的理论窗口（final 字段语义可保 BeanModel 自身 final 字段安全，但非 final 缓存字段可能被观察为过期）。命中路径常数健康（1 次字段读），正确性裁定归 C1/C2。

  **复核处置**：确认（首立案）。证据亲核成立。跨维度关系注明：C2-round2 :46/:117 已检**同站点**但仅做锁序/重入面分析（无环、无重入依赖→不立案留档），未触及可见性面——本条为该站点首立案，非重复登记；与 C1-009 非 volatile 单例槽族**不同族**（实例字段 DCL vs 静态单例槽），归 C1/C2 裁定（本维度集外）。现状：已知未修复。

### [nop-kernel-concurrency-CL-036]（维度集外线索，正确性/安全形态）JsonWhitelist.add 前缀注册写入 "*" 死键，包前缀白名单永不匹配

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/JsonWhitelist.java:21-27`
- ```java
  if (className.endsWith(".*")) {
      whitelistPrefixes.add(className.substring(className.length() - 1)); // 取到的是 "*"，应为 substring(0, length-1)
  }
  ```
- `"com.foo.*".substring(len-1)` = `"*"`；`contains` 中 `className.startsWith("*")` 对任何类名恒 false——**凡经 `.*` 形式注册的前缀白名单条目全部失效**。在 `CFG_JSON_SERIALIZE_ONLY_DATA_BEAN` 默认 true（CoreConfigs.java:182-183）下，前缀注册的类在 JSON 序列化时将被 `ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE` 拒绝（fail-closed，不扩大攻击面但破坏功能）。与并发无关，归正确性/安全审计队列；建议同节修为 `substring(0, className.length() - 1)` 并补单测。

  **复核处置**：确认（三登记合并）。代码亲核成立；**行号修正**：本条所引 :21-27 应为 **:36-55**（add :36-44 / contains :46-55，`whitelistPrefixes.add(…)` 在 :43）。C5 复核已确认并显式注明「与 C1 轮备注、C9-CL-036 三登记合并裁定（Phase 6）」——**主登记归 C5-CL-025**。现状：已知未修复。

---

## 深挖继续规则判定（终止信号）

- 本轮新增 P0/P1：**0**（C9-010 为 P2；其 NFS 升级条件与 C9-001 同口径，已在风险节注明）。
- 新增 P2：1（C9-010）；CL：2（CL-035/036，均维度集外）。
- 四项收口指令全部闭环：①入口穷举达不动点（grep `.getObject(`/`.checkRefresh(`/`ResourceCacheEntry` 全部构造点复核，E1-E10 闭合，含 1 项负结果排除）；②终表产出且修复优先级可直接排序；③抽验 3+1 处维持原判、无新高频锁站点；④基准判定完成（不补，C10 候选）。
- **终止信号成立：清单闭合，建议 C9 维度收口，不需要第 3 轮。**

## 总计

| 类别 | 数量 | 明细 |
|------|------|------|
| 本轮新发现 | 1 | C9-010（P2）：ResourceCacheEntry 直接使用方绕过 refreshMinInterval 门（gateway/data-auth/xpl-tag/dyn-orm 四站点 + GraphQL 反例排除） |
| C9-001 失效面扩展 | 10 入口 | E1-E10（i18n 每消息 1-3 get×D 证实；D 不含虚拟根的口径修正；GraphQL deps 空零 stat 负结果） |
| 热路径常驻开销终表 | 17 行 | 第 1-5 行同根因（依赖图 stat walk），C9-001+C9-010 一并修复可消除 ~90% 常驻 I/O 税 |
| 锁竞争收口 | 抽验 3+1 | S1/S2/S11（+S9 附带，排除一自产误报）维持「无热点」原判；无新增高频锁站点；CL-035（C1/C2） |
| 基准缺口判定 | 4 | B1-B4 全部不补、登记 C10 缺保护候选；nop-benchmark 五子模块可挂载 |
| 维度集外线索 | 2 | CL-035（双检锁缺 volatile）、CL-036（JsonWhitelist 前缀死键） |
| 累计（两轮） | C9-001~010 | P2×3、P3×7；CL-014~016、CL-035~036 |
