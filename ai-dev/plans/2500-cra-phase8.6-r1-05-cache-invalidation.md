# 2500 CRA Phase 8.6 — R1-05 缓存失效传播链批（R24：三面单点修复 + 别名随主，4 项）

> Plan Status: active
> Draft Review: R1 agent_b9afee28（0 Blocker、4 Major[F1 自愈通道覆盖面不对称/F2 单点修复位机械判定缺失/F3 TestXplTask 批外缺陷污染无预案/F4 墙钟敏感点登记+测试缝]+3 Minor——修订 agent_c85f2713 全量应用[live 回读后落笔]，active）
> Design Review: 设计文档 `cache-invalidation-single-point.md` 独立评审 agent_27b2c138（M-1 宽限重载风暴规范缺口+M-2 触发集/source-compat 缺口+4 Minor → 修订 agent_104312c7 落盘 → 增量复核**通过进入实现**；裁定本体全部维持，12 行编辑点集合零扩张）
> Last Reviewed: 2026-10-24
> Source: roadmap 工作项 8.6（R1-05，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L148，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.1 R1-05 行（L66：finding ID 清单 4 项 / PA 授权 / 依赖标注 / exit criteria 增补）+ §四.3 授权落批表（框架核心引擎 plan-first → R1-05 行）；家族底表 = family-clustering §三 R24 缓存失效传播缺口族行（L68，chain18）+ §四 跨单元重复消解 #4 行（L592，2.3-C9-002 → 2.1-C9-010 同一缺陷别名裁定）；成员发现详情 = WI 2.1 审计目录 `ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/`（C7-long-running.md C7-007 条目+终裁详记 §三、C9-hot-path.md C9-001 条目、C9-round2.md C9-010 条目[F3 补全 6 使用方]、C10-test-protection.md C10-005 条目+N3）与 WI 2.3 审计目录 `ai-dev/audits/2026-10/2026-10-17-2353-deep-audit-nop-service-framework-hotpath-concurrency/`（C9-hot-path.md C9-002 条目、merge-adjudication.md A10 跨单元并入裁定）；执行基线 @ `06867400b2`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.5 已 done，plan 2495/2496/2497/2498/2499 均 completed；本 Baseline 全部行号为该基线 live 回读值，执行前如再漂移以 live 为准并登记）
> Related: plan 2496（R1-01 绝缘层批，completed——C3-015 已随彼批落地，与本批同族 G24 组行的已收敛成员）；plan 2497（R1-02 池设施批，completed——plan-first 小批范式与 source-anchors 新建锚点先例[EXEC-001~003]）；plan 2498（R1-03 池消费方批，completed）/ plan 2499（R1-04 api-core 批，completed）；plan 2495（R1-00，completed）；plan 2493/2494（批次划分与 Phase 8 展开）；**下游/联动**：R3-05（roadmap 8.35，5.5 rule-metadata 批——5.5-W-03/C7-R2-001 规则热更新失效传播缺口本体修复归彼批；R24 族行裁定「2.1 C7-007 单点修复若涉失效判据须联动 5.5-W-03 验证」，本批 Phase 3 承载联动验证+注记）；R3-02（roadmap 8.32，5.2 sys-dyn 批——R24 族第 5 成员 5.2-I-04 已裁定归彼批「照抄函数级先例」，本批判据形态为其先例输入）；R2-01（2.1 剩余批——同文件相邻面 C2-001[ResourceCacheEntry 锁纪律]/C2-002/C2-017/C1-003/C1-014 彼批落地，同文件改动顺序边界见裁定⑥）；R4-32（R28 墙钟批——`ResourceCacheEntry.isRefreshEnabled`/`lastLoadTime` 的 currentTimeMillis 敏感点归彼批，本批零触碰声明见裁定⑥）
> Protected Area 授权：本批触碰 **nop-kernel 内部（框架核心引擎）= plan-first Protected Area**。授权来源 = AGENTS.md「Protected Areas」表框架核心引擎行 + batch-proposal §四.3 授权落批表（「框架核心引擎（nop-kernel/nop-core-framework 内部）plan-first（设计文档+回归测试）→ R1-01、R1-02、R1-05、……」行）+ 本 plan Dependencies 声明：**设计文档为本批 Phase 1 产物（`ai-dev/design/`），回归测试为各 Phase 强制义务（rule 25）**。plan-first 前置在 Phase 1 内闭环：设计文档落地并经独立 draft review 后，Phase 1 内实现项与 Phase 2–4 方可推进。**连带声明（nop-auth ask-first 触发评估）**：单点修复纪律下 6 直连使用方（含 nop-auth-service `DefaultDataAuthChecker`）预期**零改码**——修复位在 nop-kernel/nop-core 设施层，使用方经公开协议自动受益；若 Phase 1 裁定④改选逐点修形态且需改 `nop-auth-service` 代码，则触发 AGENTS.md「权限/认证模型（nop-auth）ask-first」，须升级用户询问后方可实施，本 plan 不预设该授权，也不默认选择该分支。本批不触碰 `nop-api-core`（io.nop.api.core.*）、不触碰 `model/*.orm.xml`。

## Purpose

关闭 R24 缓存失效传播缺口族在本批的 4 项成员（P1 族主 2.1-C7-007 + P2 搭车 3 项：G24 两成员 2.1-C9-001/C9-010 + G38 别名成员 2.3-C9-002）。四成员是**同一条 check-changed 失效链的三个面 + 一个别名**：C7-007（正确性面——mtime 相等比较双漏检窗口 + 无 TTL 兜底 → 陈旧模型无限期服务；ResourceTimestampCache 死代码死配置）、C9-001（性能面——ResourceLoadingCache 命中路径稳态每请求全依赖图 walk + 裸 stat 税）、C9-010（门缺失面——ResourceCacheEntry 直接使用方绕过 refreshMinInterval 门，6 处生产调用方每请求无条件 stat 遍历）、2.3-C9-002（C9-010 设施缺陷在 gateway 请求热路径的量化实例，family-clustering §四#4 裁定「同一缺陷，别名随主不双计」）。本批按批行 exit criteria 落**单点修复**：四成员收敛到 checker/deps/cache-entry 设施层的一个修复位，统一 check-changed 失效判据，裁定 ResourceTimestampCache 活化或移除——**禁止 checker 层、entry 层、6 个使用方各自打补丁**。随本体落地 C10-005+N3 验收前置（恢复/改写被 `@Disabled` 的 TestXplTask 链路测试 + 双漏检窗口确定性测试）；若判据变更触及「依赖钉定→失效检测」通道语义，按 R24 族行执行 5.5-W-03 联动验证并产出 R3-05 注记。批末 roadmap 8.6 → done，四成员台账收敛。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.1 R1-05 = 4 项成员（P1×1 族主 + P2×3[含 1 别名]）；cra-index P1 台账 1 行（`nop-kernel-concurrency-C7-007`，裁定批次=R1-05，open，L194）+ P2 裁决台账 2 组行（G24 七成员组[C9-001/C9-010 两成员 → R1-05，其余五成员归 R2-01/R1-01 已落地或待落地]、G38 单成员别名[2.3-C9-002 → R1-05「别名随主」]，均回填「修→提前（R24 族批）→实际批号 R1-05」）；roadmap 8.6 行 Status=todo（L148）。
- 前置：无前置（可并行，批行 Deps 列）。R1-00~R1-04（plan 2495–2499）均 completed @ `06867400b2`——本批启动的基线门禁已满足；plan 2499 daily log 收口条目明记「下一批 8.6（R1-05 R24 缓存失效链，4 项）待立项」。
- **三面绑定结构与单点修复纪律（批行 exit criteria 第一句）**：「C7-007 与 C9-001/C9-010 同链三面**单点修复**」——C7-007 复核终裁已显式注明「checker 层修复同步收敛 C9-010 正确性面、stat 税性能面按 C9 本维度处置」，C9-010 复核建议③「无论哪种，同时修复 C9-001 的根因（时间戳缓存/结果记忆化）则 E1-E6 全部受益」，2.3-C9-002 merge-adjudication A10 裁定「修复单点归属 WI 2.1 C9-010 设施工单……不产生独立修复工单、不重复计数」。三面一修复位 = 本批的核心结构约束，Phase 1 设计文档必须给出唯一修复位的落点声明。
- 2.3 审计 merge-adjudication X14 行已在对端（WI 2.1）预留「本单元新增 C9-002 实例面一行」映射口径——本批收口时两单台账注记须互引一致（Phase 4）。

### 四成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `06867400b2` 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级） | 单元 / 模块 | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向（单点修复位收敛） |
|---|---|---|---|---|---|
| 1 | 2.1-C7-007（P1 族主） | 2.1 / nop-kernel（nop-core） | `nop-kernel/nop-core/src/main/java/io/nop/core/resource/deps/DefaultResourceChangeChecker.java:16-25`（`:21-22` 纯 mtime 相等比较 `lastModified != current`）；`resource/deps/ResourceDependsManager.java:81-95`（`:91` `collectDepends` 在 `task.get()` 完成后才 `deps.setLastModified(resource.lastModified())`——窗口二）与 `:235-255`（`:246` `isDependencyChanged` 相等分支 mtime 涂新）；`resource/cache/ResourceCacheEntry.java:216-225`（`loadObject`：`:220` 经 `collectDependsTo` 加载、`:222-223` 加载完成后才写 `deps`/`lastLoadTime`——读内容与记 mtime 非原子）；`resource/cache/ResourceTimestampCache.java:1-54`（全文注释死代码）；`CoreConfigs.java`（`:86-87` `CFG_COMPONENT_RESOURCE_CACHE_CHECK_CHANGED` 默认 true、`:98-99` `CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT` 默认 null 无 TTL 兜底、`:112-117` `CFG_COMPONENT_RESOURCE_TIMESTAMP_CACHE_SIZE/TIMEOUT` 死配置[TIMEOUT 默认 100ms]） | check-changed 为默认唯一失效链（`nop.core.component.resource-cache.check-changed` 默认开启）且失效判据存在两个永久漏检窗口：窗口一=同 tick 双写（mtime 相等比较对同一 mtime 粒度 tick 内的第二次写入天然盲）；窗口二=load 后取 mtime 竞态（loader 读旧内容期间整文件替换 → 缓存持「旧内容+新 mtime」永久固化）；漏检后 cacheTimeout 默认 null 无 TTL 兜底，陈旧模型（xbiz/orm/xdsl/xmeta 全家族 = 平台语义载体）无限期服务、不可自愈；ResourceTimestampCache 自 2023 年起整体注释死代码，其 size/timeout 两配置成死配置，失效检测 100% 依赖单次 mtime 相等比较 | 窗口一/窗口二在**一个判据载体**上消除（审计建议组合选型：读前读后双 mtime / 涂新前内容指纹确认或宽限期——Phase 1 裁定①定夺选型与唯一落点）；TTL 兜底语义裁定（启用 `CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT` 或等价形态作为漏检自愈通道——裁定③）；ResourceTimestampCache 活化或移除裁定（与 C9-001 的 stat 税面一次裁决——裁定②）；死配置随裁定同步处置 |
| 2 | 2.1-C9-001（P2，G24） | 2.1 / nop-kernel（nop-core） | `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceLoadingCache.java:190-196`（`:192` 门 `checkChanged.get() && entry.isRefreshEnabled(cacheRefreshMinInterval.get())`——门内[加载后 100ms 内]跳过 `isChanged`，门外稳态恒走全依赖图 walk）；`ResourceCacheEntry.java:163-174`（`isChanged` → `ResourceComponentManager.isAnyDependsChange`）；`ResourceDependsManager.java:214-216`（`isAnyDependsChange` 每次调用 `new HashSet<>()`）+ `:235-255`（递归逐 dep `checkChanged` → 每 dep 1 次裸 stat + 1 个 `ResourceChangeCheckResult` 分配）；受累放大面 = `ResourceComponentManager.modelCaches` 全家族（xbiz/xmeta/xdef/xpl/dict）+ `I18nMessageManager`「i18n-cache」（每条 i18n 消息 1-3 次 get）+ `TenantAwareResourceLoadingCache`/`depends-with-cache`/`DictProvider`（F3 修正面） | 模型缓存命中路径稳态（请求间隔 >100ms）每次 get 重复执行 O(D) 全依赖图遍历 + O(D) 次裸 stat 系统调用 + O(D) 分配（D=20、QPS=1000 → 约 2 万 stat/s，本地 2-10% 单核，bind-mount/NFS ×50）；时间戳缓存设施被整体注释，stat 完全无缓存 | stat 税性能面与成员 1 在**同一修复位**收敛：时间戳缓存活化（带 TTL、判据安全前提见裁定②）或 entry 级 `isChanged` 结果记忆化，二选一一次裁决（裁定②）；`new HashSet` 分配面随修复位一并处置；i18n/dict/租户包装等 F3 受累面随单点自动受益，不逐面打补丁 |
| 3 | 2.1-C9-010（P2，G24；F3 补全后 **6 使用方**） | 2.1 / nop-kernel 设施 + 使用方跨模块（单点纪律下使用方零改码） | 设施：`ResourceCacheEntry.java:176-208`（`getObject(checkChanged, loader)`——`:178` `value == null || checkChanged && isChanged()`，公开协议把「是否检查」完全交予调用方，**无任何时间门**）；门只实现在 `ResourceLoadingCache.get:192` 一层。6 直连使用方（live 回读一致）：① `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java:127`（`handlerCache.getObject(true, handlerLoader)` 硬编码 true；entry 于 `:95` init 创建，无 LocalCache 层/无指标/无 TTL；每 HTTP 请求 1 次）＝成员 4 别名同位点；② `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/auth/DefaultDataAuthChecker.java:160-161`（`modelCache.getObject(true, …, timeout)` 硬编码 true，经 `nop-kernel/.../cache/CacheEntryManagement` 3-arg 变体——`CFG_AUTH_DATA_AUTH_CACHE_TIMEOUT` 默认 null 使 `isRefreshEnabled(null)` 恒 false、clear 重建分支恒死，等效纯裸 walk；携带 auth filter 的 EQL 每查询触发）③ `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/xlib/XplLibTagCompiler.java:210,212,653-655`（`cachedCompiledTagForNodeMode`/`cachedCompiledTag` `.getObject(CFG_XPL_LIB_TAG_RELOADABLE.get())`，config 默认 true；运行时动态标签调用路径）④ `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynOrmModelProvider.java:72,96`（`cache.getObject(CFG_ORM_MODEL_CACHE_CHECK_CHANGE.get(), …)`，默认 true）⑤ `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java:36-41`（`interceptorCache.getObject(CFG_ORM_INTERCEPTOR_CACHE_CHECK_CHANGE.get(), …)` 默认 true——F3 复核补全，频率与②同级或更高）⑥ `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/impl/XDefinition.java:77-83`（`defaultsDsl.getObject(true, …)` 硬编码 true——F3 复核补全，编译期偏多）。反例登记防误修：`GraphQLEngine.documentCache`（text-parse 条目 deps 恒空 → `isChanged` 恒 false 零 stat，不受累） | refreshMinInterval 节流门是 `ResourceLoadingCache.get` 的私有实现，`ResourceCacheEntry.getObject` 公开协议无门——6 处生产调用方全部以常量/默认 true 传入，平台对「新鲜度检查成本」的唯一节流装置在最主要的高频使用方上不生效（gateway 每 HTTP 请求、data-auth 每实体查询、动态标签每标签、dyn-orm 每模型获取、orm interceptor 每操作路径、xdef 默认 extends 每编译期解析） | 在 `ResourceCacheEntry` 设施层内建门（entry 级 `isChanged` 结果短 TTL 记忆化、对齐 `isRefreshEnabled` 节流语义——审计建议①形态）＝**单点修复覆盖全部 6 使用方，使用方零改码**；逐点修形态（审计建议②：改用 ResourceLoadingCache/改 config 默认）为备选，若裁定④选择该分支则模块清单扩展且②站点改码触发 nop-auth ask-first 升级（见 PA 连带声明）；`CacheEntryManagement` 3-arg timeout 变体的修复后行为随裁定④显式声明；GraphQLEngine 反例零误修声明 |
| 4 | 2.3-C9-002（P2，G38——别名，随主不双计） | 2.3 / nop-service-framework（nop-gateway） | `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java:127`——与成员 3 使用方①**同一站点同一缺陷**（family-clustering §四#4 L592：「同一缺陷（使用方实例面=设施单点）」，merge-adjudication A10：修复单点归属 WI 2.1 C9-010，本单元保留实例面/验收锚点） | Gateway 每请求对模型缓存做无节流变更检查 → 每请求 ≥1（依赖树 N 文件则 N 次）stat 系统调用 + 依赖树递归走查 + 每次一个 HashSet 分配；与同文件 `bizObjCache`（经 ResourceLoadingCache 100ms 节流）形成双标 | **不设独立修复位**：随成员 3 的设施层单点闭合（修复后 handlerCache 默认获得节流与统一判据）；本批义务 = 别名面随主闭合验证（Phase 2：gateway 聚合门禁 + handlerCache 面行为验证 + 零独立改码确认），台账与对端注记互引（Phase 4） |

### 平台设施与验收前置盘点（live 实读 @ `06867400b2`）

- **修复位设施清单**（`nop-kernel/nop-core/src/main/java/io/nop/core/resource/`）：`deps/DefaultResourceChangeChecker`（25 行，mtime 相等比较载体）、`deps/ResourceDependsManager`（272 行，collectDepends/isAnyDependsChange/isDependencyChanged）、`deps/ResourceDependencySet`+`VirtualResourceDependencySet`（依赖集与虚拟根——审计口径「D 不含虚拟根」）、`cache/ResourceCacheEntry`（249 行，getObject/isChanged/isRefreshEnabled/loadObject）、`cache/ResourceLoadingCache`（243 行，get 门）、`cache/CacheEntryManagement`（data-auth 3-arg timeout 变体载体）、`cache/ResourceTimestampCache`（54 行全文注释死代码）+ `CoreConfigs` 4 组配置键。
- **同链复制面（自动受益声明对象）**：`TenantAwareDependencyManager`/`TenantAwareResourceLoadingCache`/`TenantAwareResourceCacheEntry`（同链多租户包装，C7-007 F3 与 C9-010 F3⑧ 均登记为「随本案修复自动受益」——Phase 1 裁定⑤须给出委托穿透断言，不得只凭类型存在断言；`TenantAwareResourceCacheEntry` 为**装饰器**：`getObject`/`isRefreshEnabled` 经 `getCacheEntry()` 委托内部 per-tenant `ResourceCacheEntry`，entry 层单点门可穿透生效）。
- **1-arg 协议复制面（零改码依据）**：`cache/ResourceCacheEntryWithLoader` **extends** `ResourceCacheEntry`，1-arg `getObject(checkChanged)` 纯委托 2-arg `getObject(checkChanged, loader)`——使用方③ `XplLibTagCompiler` 的 `cachedCompiledTag`/`cachedCompiledTagForNodeMode` 即该子类，2-arg 协议上的单点修复对其零改码生效。
- **C10-005 + N3 验收前置（G24 组行「C10-005+N3 随本体（验收前置）」；E2 同 diff 规则）**：check-changed 失效链零活测试——唯一触及依赖链的 `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xpl/TestXplTask.java` 类级 `@Disabled`（live `:37`），双漏检窗口与 refreshMinInterval 门零护栏。修复形态（C10 报告建议）：窗口二用确定性双 mtime 注入（可编程 `Resource.lastModified()`）断言「load 期间 mtime 变更 → 不固化旧内容+新 mtime」；窗口一用同 tick 双写模拟断言重检触发；加一条 checkChanged 默认开启的冒烟（改文件 → 模型失效）；恢复或改写被 `@Disabled` 的链路测试。注意 C10-005 涉及缺陷 = C7-007/C9-001/C9-010 恰为本批三成员——**无越界承接面**（与 2497 批 C10-010 涉及面大于成员面的情形不同，本批 C10-005 与成员面完全重合）。
- **既有测试基线**：`nop-kernel/nop-core/src/test/java/io/nop/core/resource/cache/TestResourceCacheEntry.java`（钉 reload-null 占位语义——修复不得回退）、`.../resource/TestResourceLoadingCache.java`（单线程命中路径）；`TestXplTask` 整类 `@Disabled`。整条失效链现役活测试为零（C10 矩阵「无保护」），rule 25 新增测试即该链首批护栏。
- **相邻面边界（零触碰声明对象）**：① `ResourceCacheEntry` 锁纪律面——`getObject`/`reloadObject` 持 entry 监视器加载语义 = C2-001 缺陷本体（R2-01，open）+ C2-002 快路径无锁读面（R2-01），本批只改判据与门逻辑、不重构锁形态（裁定⑥）；② 墙钟面——`isRefreshEnabled:104`/`loadObject:223` 的 `currentTimeMillis` 敏感点 = C7-004/C7-008 F3 登记面（R4-32），本批不换时钟源；③ R24 族内批外面——5.5-W-03（C7-R2-001，归 R3-05）、5.2-I-04（归 R3-02「照抄函数级先例」）不在本批 4 成员清单内。

### Baseline 勘误（输入不一致登记）

1. **C7-007 审计文件行引用漂移（3 处，复核轮引用 live 准确）**：文件行载 `ResourceDependsManager.java:197-209` → live 该区间为 `traceDepends`/`traceAllDepends`，涉事方法实际为 `isDependencyChanged(deps,checked):235-255`（涂新 `:246`）与 `isAnyDependsChange:214-216`；文件行载 `ResourceLoadingCache.java:196-206` → live `get` 为 `:190-196`（门 `:192`）；文件行载 `DefaultResourceChangeChecker.java:22-29` → live 方法 `:16-25`（相等比较 `:21-22`）。复核轮自身的引用（`:246`/`:192`/`:21-22`）经 live 回读全部准确；`ResourceCacheEntry.java:216-225` 零漂移。本 Baseline 按 live 回读值书写。
2. **C9-010 标题与 F3 计数不一致（复核已裁决，按 6 执行）**：条目标题写「4 处生产调用方」，同条目复核状态段 F3 补全 2 站点后明文「直接使用方实为 **6 个非 4 个**」（补 ⑤ `DefaultOrmModelProvider.interceptorCache` ⑥ `XDefinition.defaultsDsl`）。本 plan 全文按 6 使用方清单执行；「ResourceCacheEntry 层内建门」单点修复覆盖面按 6 计。
3. **batch-proposal R1-05 验证命令列不完整**：仅列 `./mvnw test -pl nop-kernel -am`。别名成员验收面在 nop-gateway（属 nop-service-framework 聚合，pom `<module>nop-gateway</module>` live 核实），且使用方回归面涉及 nop-auth/nop-persistence/nop-dyn——本 plan 验证命令按实际 diff 与回归义务补列（Phase 2/3）；单点修复纪律下使用方模块预期零改码，回归以聚合门禁承载。
4. **batch-proposal R1-05 模块列「nop-kernel；nop-service-framework」为缺陷面归属口径，非改码面承诺**：单点修复位预期全落 nop-kernel/nop-core；nop-service-framework 为别名验收面（GatewayHttpFilter `:127` 零改码预期）。若裁定④改选逐点修分支，模块清单按裁定扩展（含 nop-auth ask-first 触发评估），在 Phase 1 设计文档显式声明后回填本 plan。
5. **R24 族共 5 成员缺陷实体，本批仅承接 4 项**：族行第 5 成员 5.2-I-04（动态模型删除后零缓存失效面，P2）已由 p2-adjudication G74 裁定归 **R3-02**（roadmap 8.32，「I-04 照抄函数级先例」），不在本批 finding 清单（L66）内——本批在 Non-Blocking Follow-ups 登记族面 residual 归属，防「族行闭环≠族面全闭环」被误读，不越界承接。
6. **CoreConfigs 行号微漂移 + 默认值补记**：审计载 `:86-88`（check-changed）→ live 声明体 `:86-87`（`@Description` 在 `:85`）；`:98-99`（cache timeout 默认 null）准确；补记 `CFG_COMPONENT_RESOURCE_TIMESTAMP_CACHE_TIMEOUT` 默认 **100ms**（live `:116-117`，审计仅记「成死配置」未记默认值）——活化裁定②时该默认值是现成基线，登记备裁定引用。执行以 live 为准。
7. **source-anchors.md 现无模型缓存/check-changed 锚点条目，且 docs-for-ai 全域无该契约承载**：grep `docs-for-ai/` 零命中 `check-changed`/`resource-timestamp-cache`/`refresh-min-interval`——roadmap 8.6 Owner Doc 列的「模型缓存锚点」为**本批新建**条目（沿 2497 EXEC 先例），非更新既有条目；该契约当前无任何 owner doc，新建即首个权威锚点。
8. **TestXplTask `@Disabled` 位置核实**：C10 报告载类级 `@Disabled`（`:37`）——live 核实 `:37` 类级注解成立，零漂移；N3 与 C10-005 的「恢复/改写」义务以该现状为起点。

## Goals

- Phase 1 设计文档一次裁决（plan-first 前置，独立 draft review 通过），六裁定：①check-changed 判据统一与唯一修复位落点（双窗口消除方案选型；三面共用一个判据载体，禁止两套判据/多处打补丁）；②ResourceTimestampCache 活化或移除裁定（C9-001 stat 税性能面与 C7-007 死代码清理面的一次性收敛，含活化判据安全前提与死配置处置）；③TTL 兜底语义（漏检自愈通道，配置契约变化声明）；④直连使用方门语义（entry 级记忆化单点覆盖 6 使用方 vs 逐点修的选型，含 CacheEntryManagement 3-arg 变体与 GraphQLEngine 反例声明）；⑤TenantAware 同链复制面自动受益验证声明；⑥边界声明（与 R2-01 C2-001 锁纪律边界、与 R4-32 墙钟面零触碰边界、与 5.5-W-03 判据联动口径）。
- 三面单点修复本体落地并各带回归测试（rule 25）：C7-007 双漏检窗口闭合 + TTL 兜底 + ResourceTimestampCache 处置；C9-001 stat 税面随修复位收敛；C9-010 门缺失面单点覆盖 6 使用方（或裁定④备选形态）。
- C10-005+N3 验收前置随本体同 diff 落地：`TestXplTask` 恢复/改写 + 双窗口确定性测试 + checkChanged 默认开启冒烟。
- 2.3-C9-002 别名面随主闭合验证：gateway handlerCache 面行为验证 + 使用方零改码确认（或裁定④实际清单），不设独立修复位。
- 5.5-W-03 联动验证义务（R24 族行）落地：判据变更验证 + R3-05 注记产出（Phase 3，不得静默跳过）。
- Owner doc 随批更新：`docs-for-ai/04-reference/source-anchors.md` **新建**模型缓存锚点（check-changed 判据契约、门语义、直连使用方协议、TTL/兜底语义、ResourceTimestampCache 裁定结果）。
- E1–E6 通用门禁全过（batch-proposal §一）：全量构建绿 / 每工单强制并发回归测试（先红后绿或变异/回退论证）/ 验证通道留证 / 授权声明 / owner doc 清单 / 批级独立 closure audit。

## Non-Goals

- **不做 5.5-W-03（C7-R2-001）本体修复**：规则热更新失效传播缺口（`DaoRuleModelLoader:84` traceDepends 只钉定义行）归 R3-05（roadmap 8.35）；本批只承载「判据变更联动验证 + R3-05 注记」，不改 nop-rule 代码。
- **不做 5.2-I-04**（R24 族第 5 成员，动态模型删除零缓存失效）：归 R3-02（已裁定），本批不越界承接。
- **不做 ResourceCacheEntry 锁纪律与生命周期相邻面**：C2-001（持 entry 监视器加载嵌套死锁）、C2-002（快路径无锁读×clear 锁内销毁）、C2-017/C1-003/C1-014（G24 组内其余成员）归 R2-01；本批不重构持有监视器加载语义、不改 clear/destroy 生命周期形态（裁定⑥边界），`TestResourceCacheEntry` 既有钉零回退。
- **不做墙钟面**：`isRefreshEnabled`/`lastLoadTime` 的 currentTimeMillis 敏感点归 R4-32（R28 单调时钟注入批），本批零触碰。
- **不做 C9 性能面独立量化优化轮**：i18n/dict 放大链等 C9-001 修正面随单点受益即收口；不做独立基准测试建设（B1 基准缺口另案）。
- **不做 GraphQLEngine documentCache 面**：deps 恒空零 stat 反例，登记防误修，零改码。
- 不触碰 `nop-api-core`、`model/*.orm.xml`；不触碰 nop-auth（裁定④默认分支下 nop-auth-service 零改码；若裁定改选逐点修需改 nop-auth-service 代码，须先按 ask-first 升级询问，本 plan 不预设授权）。
- 不做 Deferred 节所列项（W-03 本体、5.2-I-04、stat 税深度优化等长期方向）。

## Scope

### In Scope

- `nop-kernel/nop-core` 资源失效链设施面：`DefaultResourceChangeChecker`/`ResourceDependsManager`/`ResourceCacheEntry`/`ResourceLoadingCache`/`CacheEntryManagement` + `ResourceTimestampCache`（活化或移除）+ `CoreConfigs` 配置项语义 + nop-core 单元测试。
- C10-005+N3 验收前置：`nop-kernel/nop-xlang` `TestXplTask` 恢复/改写 + nop-core 失效链新测试。
- 2.3-C9-002 别名面验证：`nop-service-framework/nop-gateway` GatewayHttpFilter handlerCache 面行为验证（预期零改码）。
- 判据变更使用方回归面：nop-auth-service/nop-orm/nop-dyn-service/nop-xlang 聚合门禁回归（预期零改码；实际清单以裁定④为准）。
- Owner doc：`docs-for-ai/04-reference/source-anchors.md` 新建模型缓存锚点；触达模块 owner doc 归属核对与缺口声明（以实际 diff 为准）。
- 5.5-W-03 联动验证注记（R3-05 传导载体落 cra-index/日志）。

### Out Of Scope

- Deferred 节与 Non-Blocking Follow-ups 所列项；R3-05/R3-02/R2-01/R4-32 等后续批次工单；R24 族批外面（见 Non-Goals）。

## Execution Plan

### Phase 1 - 单点修复设计文档 + nop-kernel 失效链本体实现 + 单元测试（plan-first 前置）

Status: planned
Targets: `ai-dev/design/concurrency/`（新建：模型缓存失效判据统一设计——check-changed 单点修复）、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/`（deps/DefaultResourceChangeChecker、deps/ResourceDependsManager、cache/ResourceCacheEntry、cache/ResourceLoadingCache、cache/CacheEntryManagement、cache/ResourceTimestampCache）、`nop-kernel/nop-core/src/main/java/io/nop/core/CoreConfigs.java`、`nop-kernel/nop-core/src/test/java/io/nop/core/resource/`、`nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xpl/TestXplTask.java`

- Item Types: `Decision`（设计文档六裁定）、`Fix`（2.1-C7-007/2.1-C9-001/2.1-C9-010 全部为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10-005+N3 验收前置）

- [ ] 设计文档落 `ai-dev/design/`（最终状态写法，rule 14：只写决策与契约，不写 Proposed-vs-Current、不写类签名代码），须含以下六项裁定：
  - [ ] **裁定① check-changed 判据统一与唯一修复位（本批核心裁定）**：双漏检窗口消除方案选型（窗口二=「读前读后双 mtime」load 原子化——load 前取 m1、load 后取 m2、m1≠m2 立即重载；窗口一=涂新加内容指纹确认或宽限期——C7-007 建议(1)(2)的组合选型）+ **唯一判据载体落点**（checker 层 / ResourceDependsManager 涂新点 / ResourceCacheEntry 层——一处承载，禁止 checker 层一处、entry 层又一处两套判据并存）+ 判据统一后 ResourceLoadingCache 门路径与 6 直连使用方路径行为一致性声明（同链同判据：无论经门或直连，同一 entry 的失效判定结果可对照）+ 对既有 `TenantAwareResourceLoadingCache`/`depends-with-cache` 等消费形态的语义影响声明。**单点修复位机械判定**：设计文档须以「文件 + 类 + 机制」粒度声明唯一判据载体，并**显式枚举允许的从属编辑点**——窗口二 load 原子化方法（`ResourceCacheEntry.loadObject` / `ResourceDependsManager.collectDepends`）、窗口一涂新点（`isDependencyChanged:246`）、门所在类（`ResourceCacheEntry` / `ResourceLoadingCache`）、裁定②涉及文件（`ResourceTimestampCache`）——并声明「判据比较语义仅一处实现，其余为机械传递」。机械约束：checker 层（`DefaultResourceChangeChecker`）不在 load 路径上，物理上无法单独消除窗口二——若设计选 checker 层承载判据，必须搭配 load 路径从属编辑点，禁止声称「checker 层唯一落点就地消除双窗口」。closure audit 专项复核按此操作化：产品 diff 路径 ⊆ 声明集合 + 判据实现 grep 全仓唯一。
  - [ ] **裁定② ResourceTimestampCache 活化或移除（两审计建议方向相反，一次裁决）**：C9-001 建议恢复带 TTL 时间戳缓存降 stat 税 vs C7-007 建议(4) 清理死代码死配置——二选一并给出 stat 税性能面的完整落点（活化=时间戳缓存成为判据链一部分，须证明缓存 TTL 窗口不重新引入窗口一形态的漏检盲区[缓存粒度与判据安全性的叠加面显式论证]；移除=死代码与 `CFG_COMPONENT_RESOURCE_TIMESTAMP_CACHE_SIZE/TIMEOUT` 两死配置一并清理，stat 税改道 entry 级 `isChanged` 结果记忆化或其他形态，C9-001 性能面不得悬空）；`new HashSet` 逐次分配面随裁决一并处置。
  - [ ] **裁定③ TTL 兜底语义**：`CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT`（默认 null 无兜底）是否改默认值或以何种兜底形态启用（漏检自愈通道——C7-007 建议(3)，审计建议「哪怕 5 分钟级」）+ 配置契约变化（默认值变更 = 行为变更）的 source-compat 声明与 owner doc 义务登记；若判维持 null 默认，须给出漏检自愈的替代通道或显式 residual 裁定。**覆盖矩阵义务（live 回读）**：该配置全仓唯一消费点 = `ResourceLoadingCache.java:81`（经 `createCache`/`refreshConfig` 落为 `setExpireAfterWrite`——TTL 到期**整条目逐出 + destroy**、下次访问冷加载重建，属冷启动式自愈，**非**陈旧期内重检）；6 直连使用方（GatewayHttpFilter/DefaultDataAuthChecker/XplLibTagCompiler/DynOrmModelProvider/DefaultOrmModelProvider/XDefinition）的 entry 不经该消费路径，**启用该配置对 C9-010 门缺失面零效果**。裁定③义务追加：(a) 漏检自愈通道须按「门路径 vs 6 直连路径」分别声明各自的自愈载体——直连路径自愈若由裁定④ entry 级记忆化 TTL 承载，须交叉引用裁定④，不得留任一路径无载体；(b) source-compat 声明须计入 expireAfterWrite 逐出/destroy 语义代价（整条目丢弃 → 下次请求冷加载，非廉价重检）。
  - [ ] **裁定④ 直连使用方门语义（C9-010 面）**：主选 = `ResourceCacheEntry` 层内建门（entry 级 `isChanged` 结果短 TTL 记忆化、对齐 `isRefreshEnabled` 节流语义——6 使用方零改码获得节流，2.3-C9-002 别名面随之闭合）；备选 = 逐点修（须列全部 6 站点改码清单，且②站点触 nop-auth-service 触发 ask-first 升级——默认不选）；`CacheEntryManagement` 3-arg timeout 变体（`CFG_AUTH_DATA_AUTH_CACHE_TIMEOUT` 默认 null 时 clear 重建分支恒死）修复后行为显式声明；GraphQLEngine documentCache 反例（deps 恒空零 stat）零误修声明。
  - [ ] **裁定⑤ TenantAware 同链复制面自动受益验证**：`TenantAwareDependencyManager`/`TenantAwareResourceLoadingCache`/`TenantAwareResourceCacheEntry` 包装的**委托穿透断言**（测试或代码追踪——三者均为委托/装饰器形态，须断言包装路径真实穿透到修复后的 entry 层判据/门语义，不得只凭类型存在断言自动受益）。
  - [ ] **裁定⑥ 边界声明**：与 R2-01 C2-001/C2-002 同文件锁纪律边界（ResourceCacheEntry 持有监视器加载语义不重构，本批只改判据与门逻辑；`TestResourceCacheEntry` 既有钉零回退；R2-01 后续修复以本批落盘后基线为准）；与 R4-32 墙钟面边界（既有 currentTimeMillis 敏感点不换时钟源；**新增时间机制两条义务**：(a) 本批新增的时间机制（宽限期计时、`isChanged` 结果记忆化 TTL、时间戳缓存 TTL 等）一律复用现有时钟入口 `CoreMetrics.currentTimeMillis`（R4-32 换源自动覆盖新增点），并在 Phase 3 的 R4-32 注记中登记新增敏感点清单——零新增亦须显式登记；(b) 宽限期/TTL 形态须同时声明确定性测试缝——阈值可配置为 0，保证 E2 断言不依赖真实时间推进）；与 5.5-W-03 判据联动口径（判据变更是否触及「依赖钉定→失效检测」通道语义的判定 + Phase 3 联动验证形态选型：nop-rule focused 冒烟或链路审查+注记——默认**义务成立**[判据统一即判据变更面]，除非裁定显式论证判据语义对外不变）。
- [ ] 设计文档独立 draft review（独立子 agent，含想象性分析）通过后，本 Phase 后续实现项与 Phase 2 方可启动（plan-first 门禁）——记录于本 plan 头部 Draft Review 行或 daily log
- [ ] 2.1-C7-007 本体修复：双漏检窗口闭合 + TTL 兜底 + ResourceTimestampCache 处置（按裁定①②③实施；`ResourceLoadingCache`/直连使用方公开协议零 API 签名变更，或按裁定④声明协议变化）
- [ ] 2.1-C9-001/C9-010 设施面修复：stat 税落点 + 直连使用方门语义单点（按裁定②④实施；6 使用方零改码为默认形态）
- [ ] 本体单元测试（rule 25 显式覆盖 + C10-005 成员面验收前置）：
  - [ ] 窗口二确定性测试：可编程 mtime 注入断言「load 期间 mtime 变更 → 不固化旧内容+新 mtime」（C10-005 建议形态；修复前红、修复后绿）
  - [ ] 窗口一确定性测试：同 tick 双写模拟断言重检触发（修复前红、修复后绿）
  - [ ] checkChanged 默认开启冒烟：改文件 → 模型失效（check-changed 默认 true 的生产形态链路测试）
  - [ ] `TestXplTask` 恢复/改写：解除类级 `@Disabled`（或改写为等效链路测试），覆盖 watch→debounce→checkChanged→模型重载整条链（N3 义务；与本体**同 diff**——E2）。**C1-004 恢复预案**（C10 报告 N3 明记：该链涉及 C1-004[debounce 非原子]，归 R4-13 open、不在本批成员面）——解除类级 `@Disabled` 后 debounce/watch 面断言可能因 C1-004 变红：checkChanged 失效链断言必须全绿；debounce/watch 面断言若因 C1-004 失败，允许方法级隔离 + 原因注记 + R4-13 对端回归义务传导（或改写为绕开 debounce 触发形态的等效链路测试）；**禁止整类回退 `@Disabled`**（静默跳过嫌疑，rule 24）；隔离注记落 daily log 与 cra-index R4-13 可见载体
  - [ ] 门语义一致性断言：直连 `getObject` 使用方修复后默认获得节流（对照 ResourceLoadingCache 门路径行为，6 使用方代表性站点至少 1 处断言）；`CacheEntryManagement` 3-arg 变体行为钉（按裁定④）
  - [ ] TTL 兜底行为断言（按裁定③；若判维持 null 默认则改为 residual 行为钉）
  - [ ] stat 税落点行为断言（按裁定②：活化=时间戳缓存命中路径断言+判据安全断言；移除+记忆化=记忆化节流断言）
  - [ ] TenantAware 包装委托穿透断言（按裁定⑤；含 `TenantAwareResourceCacheEntry` 装饰器穿透与 `ResourceCacheEntryWithLoader` 1-arg 委托面）
- [ ] **接线验证**（rule 23）：≥1 条断言证明新判据在真实 loadObject→collectDependsTo→checker 链上运行时生效（非仅单类单元面）——证据随执行记录登记
- [ ] **无静默跳过**（rule 24）：新判据分支在无法判定时显式失败或保守重载，不得以静默返回旧值兜底；裁定②若判移除，死配置清理不得残留「配了不认」的静默断档
- [ ] **提交纪律**：设计文档与实现分离提交（设计文档先落盘提交 → 独立 draft review → 实现按工单逐项提交，单会话超时可无损接续）；nop-core 失效链一提交；**C10-005+N3 测试与本体同一 commit**（E2 同 diff 核验依据）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 设计文档存在于 `ai-dev/design/`、为最终状态写法、含裁定①–⑥全部项，且独立 draft review 记录在案（plan 头部 Draft Review 行或 daily log）
- [ ] 2.1-C7-007/C9-001/C9-010 三面 landed 于**单一修复位**（设计文档声明的唯一判据载体；checker/entry/使用方多层并存补丁 = 违反批行单点纪律，不得勾选本项），各带 ≥1 条 focused 回归测试且测试名可辨识对应工单
- [ ] C10-005+N3 成员面覆盖在案：`TestXplTask` 恢复/改写与本体同 diff、双窗口确定性测试 + 默认开启冒烟 + 门语义一致性 + TenantAware 断言全部在案；`TestXplTask` 恢复如触发 C1-004 预案，方法级隔离 + 原因注记 + R4-13 传导已登记（无整类 `@Disabled` 回退）
- [ ] 必红证据（E2）：窗口二（load 期间 mtime 变更注入）、窗口一（同 tick 双写模拟）、门缺失（直连使用方节流前行为对照）至少三项有先红后绿或变异/回退论证留痕；其余工单按 E2 逐条可复现口径执行
- [ ] 接线验证证据登记（哪条真实链、哪条断言证明运行时生效）
- [ ] `./mvnw test -pl nop-kernel -am` BUILD SUCCESS（批行验证命令）
- [ ] Owner doc 裁定：登记锚点定稿去向 = Phase 3 统一落账（单一动作，无替代分支——模型缓存锚点为新建条目[Baseline 勘误 7]，本 Phase 不落锚点内容，不存在 `No owner-doc update required` 分支）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 2.3-C9-002 别名面随主闭合验证 + 使用方回归面

Status: planned
Targets: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java`（预期零改码，验证面）、`nop-auth/nop-auth-service`、`nop-persistence/nop-orm`、`nop-dyn/nop-dyn-service`（预期零改码，聚合门禁回归面）

- Item Types: `Proof`（别名面验证 + 回归）；若裁定④判逐点修则相应站点为 `Fix`（须先过 ask-first 升级，见 PA 连带声明）

- [ ] 别名面行为验证：GatewayHttpFilter handlerCache（`:127`）在修复后默认获得节流与统一判据——代表性站点 focused 测试或模块内验证（与 Phase 1 门语义一致性断言同口径），验证单点修复对别名站点生效
- [ ] 使用方零改码确认（默认分支）：`git diff` 过滤确认 nop-gateway/nop-auth-service/nop-orm/nop-dyn-service/nop-xlang 产品代码零命中并登记；若裁定④为逐点修则按实际清单逐站验证
- [ ] 消费方聚合门禁：`./mvnw test -pl nop-service-framework -am` BUILD SUCCESS（勘误 3 补列命令；nop-auth/nop-persistence/nop-dyn 若有 focused 触达按实际登记）
- [ ] 对端台账注记核对：2.3 审计 merge-adjudication A10/X14 口径与 WI 2.1 侧注记互引一致（Phase 4 回填前预检）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 2.3-C9-002 别名面随主闭合验证 landed（行为验证 + 零独立修复位确认，或裁定④实际清单登记）
- [ ] 使用方模块 diff 确认登记（零改码或实际清单）
- [ ] `./mvnw test -pl nop-service-framework -am` BUILD SUCCESS；其他触达聚合门禁逐条登记
- [ ] Owner doc 裁定：gateway/使用方契约面若无文本变化显式登记 `No owner-doc update required` + 理由（核对结论归 Phase 3 统一）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 5.5-W-03 联动验证注记（R3-05）+ 全量验证 + owner doc 更新

Status: planned
Targets: 受影响聚合模块全量测试；`docs-for-ai/04-reference/source-anchors.md`；触达模块 owner doc 归属核对；cra-index/日志中的 R3-05 联动注记

- Item Types: `Proof`

- [ ] 5.5-W-03 联动验证（R24 族行义务，默认成立）：按裁定⑥选型执行——nop-rule 规则链 focused 冒烟（规则热更新场景在本批判据下行为正确：定义行变更 → 失效传播不受判据变更遮蔽）或失效链代码审查证明本批判据变更不改变 W-03 缺陷前提（traceDepends 钉定粒度缺口）与 R3-05 修复面；联动注记落 cra-index 对应行/日志，R3-05 执行时可依赖（**不得静默跳过**）
- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路：`-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明，沿 2497/2499 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-kernel -am`（Phase 1 已留证则终态复跑）+ Phase 2 补列命令 BUILD SUCCESS 且登记；实际触达模块 focused 测试逐条登记（模块/测试数/退出码/快照路径）
- [ ] 零触碰确认：本批产品 diff 不含 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth（默认分支）、`_` 前缀生成物路径（`git diff --name-only` 核对登记）
- [ ] R4-32 注记：本批新增墙钟敏感点清单（按裁定⑥(a)——宽限期/记忆化 TTL/时间戳缓存 TTL 等新增 `CoreMetrics.currentTimeMillis` 消费点；零新增则显式登记「零新增」）落 cra-index/日志注记，声明 R4-32 换源面覆盖新增点
- [ ] Owner doc 更新：`source-anchors.md` **新建**模型缓存锚点条目（check-changed 判据契约与窗口语义、门语义与直连使用方协议、TTL/兜底语义、ResourceTimestampCache 裁定结果——内容源 = Phase 1 设计文档裁定，派生摘要标注「以设计文档为准」，按边界规则不直接链接 ai-dev 路径，沿 `INSUL-001`/`EXEC-001` 先例）
- [ ] 缺口声明：触达模块中无 `docs-for-ai/03-modules/` 条目者（以实际 diff 为准）按 R-G 治理轨先例登记，不在本批新建 owner doc；nop-core 为平台设施，owner 归宿即本批新建锚点（非缺口）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel --severity high`（+实际触达模块）退出码 0；若有批前既有发现，逐条裁定登记
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] W-03 联动验证结论 + R3-05 注记落账（载体可独立核验）；无静默跳过
- [ ] E1 + 批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] source-anchors 模型缓存锚点条目落地（新建）；check-doc-links --strict 退出码 0
- [ ] scan-hollow 触达模块逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.6 行、cra-index 台账（P1 台账 + P2 裁决台账 G24/G38 + 2.3 审计对端注记）

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（单点修复位唯一性复核、调用链运行时连通、无空方法体/静默吞异常）与**单点纪律专项复核**（操作化判据见裁定①：产品 diff 路径 ⊆ 设计文档声明的修复位+从属编辑点集合；判据比较语义实现 grep 全仓唯一；四成员确无第二修复位）
- [ ] cra-index 台账回填：P1 台账 `nop-kernel-concurrency-C7-007` 行收敛（open → 已修[R1-05, plan 2500]）；P2 裁决台账 G24 行 **C9-001/C9-010 成员面**收敛注记（G24 组行其余成员 C1-003/C1-014/C2-002/C2-017 归 R2-01、C3-015 已随 R1-01——只收敛本批成员面，**不整组置 fixed**）；G38 行收敛（别名随主闭合）；2.3-C9-002 与 2.1-C9-010 互引注记与 2.3 审计 A10/X14 口径一致
- [ ] roadmap 8.6 行 Status → done
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2500-cra-phase8.6-r1-05-cache-invalidation.md --strict` 退出码 0；scan-hollow 全绿复核
- [ ] Closure Evidence 写入本 plan `## Closure` 段（rule 27）
- [ ] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 四成员行状态全部收敛（G24 仅本批成员面）、roadmap 8.6 = done、对端注记互引一致
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 4 项成员全部收敛：2.1-C7-007（双窗口+TTL 兜底+ResourceTimestampCache 裁定）、2.1-C9-001（stat 税面）、2.1-C9-010（门缺失面 6 使用方）、2.3-C9-002（别名随主闭合验证，零独立修复位）
- [ ] **单点修复纪律达成**：三面收敛于设计文档声明的唯一判据载体，checker/entry/使用方无第二修复位（closure audit 专项复核）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；关键项有先红后绿或变异/回退论证（E2：窗口二 mtime 注入、窗口一同 tick 双写、门缺失对照至少三项）
- [ ] C10-005+N3 验收前置随本体同 diff 落地（TestXplTask 恢复/改写 + 双窗口 + 冒烟）；确认无越界承接（C10-005 涉及缺陷与本批三成员完全重合）
- [ ] 六裁定成文（Phase 1 设计文档）且相互一致；5.5-W-03 联动验证 + R3-05 注记落账（或裁定⑥显式论证判据语义对外不变 + 注记声明）
- [ ] 相邻面零回改实证：ResourceCacheEntry 锁纪律面（C2-001/C2-002 前提）零触碰；墙钟面按裁定⑥(a)——既有 currentTimeMillis 敏感点零改写 + 新增敏感点已登记 R4-32 注记清单；`TestResourceCacheEntry` 既有钉零回退
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-kernel -am` BUILD SUCCESS + Phase 2 补列命令 BUILD SUCCESS（E1/E3）
- [ ] Owner doc 更新完成：source-anchors.md 新建模型缓存锚点；缺口声明登记（E5）
- [ ] 受影响模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外/successor 项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）新判据/门语义在真实失效链与直连使用方路径上运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile`（受影响模块）
- [ ] `./mvnw test`（受影响模块）
- [ ] checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2500-cra-phase8.6-r1-05-cache-invalidation.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 四成员行收敛 + roadmap 8.6 → done（E6）

## Deferred But Adjudicated

### 5.5-W-03 规则热更新失效传播缺口本体修复（C7-R2-001，P1）

- Classification: `out-of-scope improvement`（批外已立项 P1，非本批降级）
- Why Not Blocking Closure: 已裁定归属批次 R3-05（roadmap 8.35，batch-proposal §二 R3-05 行 finding 清单内）；本批承载其前置联动（判据变更验证 + R3-05 注记，Phase 3），W-03 缺陷前提不被本批判据变更遮蔽由该联动验证保证；两批协同符合 R24 族行「使用方↔设施切分防双计不防登记」纪律。
- Successor Required: `yes`
- Successor Path: roadmap 8.35（R3-05）；cra-index `5.5-W-03`/C7-R2-001 行注记

### ResourceTimestampCache 移除分支下的 stat 税深度优化（仅当裁定②判移除且 entry 级记忆化不完全摊薄 NFS/慢存储场景时成立）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 裁定②若判「活化」则该项不成立；若判「移除」，C9-001 性能面已由记忆化落点收敛（P2 判级对应的节流语义达成），NFS 场景深度优化为长期方向，无既定工单。
- Successor Required: `no`（若 R3-05/后续观测显示仍需强化，经 backlog 立项）

## Non-Blocking Follow-ups

- **5.2-I-04 先例对账**（R24 族第 5 成员，归 R3-02「照抄函数级先例」）：R3-02 执行时按本批判据形态对齐失效传播先例；本批在 cra-index 族注记登记归属，不越界承接改码。
- **C9-001 F3 修正面的受益抽查**：i18n-cache/dict/depends-with-cache 等受累面随单点自动受益；Phase 1/2 门语义一致性断言覆盖代表性站点，全家族逐面断言不设独立义务（受益语义由设计文档裁定①一致性声明承载）。
- **GraphQLEngine documentCache 反例守卫**：登记防误修；若后续有人对该站点补门或改判据，先核对 deps 恒空前提（零 stat）不被破坏。

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<如用子 agent，记录 session ID>>
- Evidence: <<每条 Exit Criterion 与 Closure Gate 的验证结果（PASS/FAIL + 证据来源）；check-plan-checklist --strict 退出码；Anti-Hollow 检查结果；Deferred 项分类检查>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>
