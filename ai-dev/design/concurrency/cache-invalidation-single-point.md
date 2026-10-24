# 模型缓存失效链单点修复设计（check-changed 判据统一与直连门语义）

> Status: active
> Last Reviewed: 2026-10-24（plan 2500 Phase 1a 产出；独立 draft review 为 plan-first 门禁，通过前 Phase 1 实现项不得启动）
> Source: plan 2500（`ai-dev/plans/2500-cra-phase8.6-r1-05-cache-invalidation.md`）Phase 1 裁定①–⑥义务；发现事实源 = WI 2.1 审计 `ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/`（C7-long-running.md C7-007 条目 + 终裁详记 §三、C9-hot-path.md C9-001、C9-round2.md C9-010[F3 补全 6 使用方]）+ WI 2.3 审计 `ai-dev/audits/2026-10/2026-10-17-2353-deep-audit-nop-service-framework-hotpath-concurrency/`（merge-adjudication A10：2.3-C9-002 别名随主）；行号 = live 回读值（plan 基线 `06867400b2` 后产品零漂移，已核实）
> 实现锚：`nop-kernel/nop-core/src/main/java/io/nop/core/resource/`（`resource/deps/DefaultResourceChangeChecker`、`resource/deps/ResourceDependsManager`、`resource/deps/IResourceChangeChecker`、`resource/deps/ResourceDependencySet`、`resource/cache/ResourceCacheEntry`、`resource/cache/ResourceLoadingCache`、`resource/cache/CacheEntryManagement`、`resource/cache/ResourceTimestampCache`）+ `nop-kernel/nop-core/src/main/java/io/nop/core/CoreConfigs.java` + `nop-kernel/nop-core/src/main/java/io/nop/core/CoreConstants.java`

## 一、设计结论

1. **裁定①（判据统一与唯一修复位）**：check-changed 失效判据的比较语义**全仓唯一实现**于载体 = 文件 `io/nop/core/resource/deps/DefaultResourceChangeChecker` + 类 `DefaultResourceChangeChecker`（经 `IResourceChangeChecker` 协议对全链生效），判据升级为「mtime 相等比较 + 宽限期承诺」双条件；判据值 = `(lastModified, recordedAt)` 承载于 `ResourceDependencySet`。窗口二（load 后取 mtime 竞态）由 **load 路径从属编辑点**（`ResourceCacheEntry.loadObject` / `ResourceDependsManager.collectDepends`）以「读前读后双 mtime + 载体裁决 + 有界保守重载」机械传递消除；窗口一（同 tick 双写）由载体内的宽限期裁决消除。checker 层不在 load 路径上，物理上无法单独消除窗口二——本设计不声称「checker 层唯一落点就地消除双窗口」，checker 层承载的是**比较语义唯一实现 + 窗口一宽限裁决**。
2. **裁定②（ResourceTimestampCache 终裁）**：**移除**。死代码 `ResourceTimestampCache.java`（全文注释）与两死配置 `CFG_COMPONENT_RESOURCE_TIMESTAMP_CACHE_SIZE/TIMEOUT`（CoreConfigs :112-119）、死常量 `CoreConstants.RESOURCE_TIMESTAMP_CACHE_NAME`（:159）一并清理；stat 税性能面完整落点 = 裁定④ entry 级 `isChanged` 结果记忆化。拒绝活化的决定性理由：时间戳缓存会在判据链上重新引入缓存粒度盲区，与窗口一/窗口二修复直接冲突（§3.2）。
3. **裁定③（TTL 兜底与自愈通道覆盖矩阵）**：`CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT` 默认值 null → **5 分钟**，作为漏检自愈通道的统一配置源；自愈载体按路径分置——门路径 = `ResourceLoadingCache` expireAfterWrite 整条目逐出冷加载（既有机制，:81 唯一消费点），6 直连路径 = 裁定④ entry 级 maxAge 强制重载（交叉引用，无路径缺载体）。expireAfterWrite 的逐出/destroy 语义代价（冷启动式自愈、非廉价重检、惰性清理迟滞）显式入 source-compat 声明；负值 = 显式关闭档（现行 null 语义等价）。
4. **裁定④（直连使用方门语义）**：主选终裁 = **`ResourceCacheEntry` 层内建门**（`isChanged` 结果短 TTL 记忆化 + maxAge 自愈 + deps-空豁免），6 直连使用方**零改码**获得与门路径同源同口径的节流与统一判据；逐点修分支（含 nop-auth-service 改码触发 ask-first）不选。`CacheEntryManagement` 3-arg timeout 变体的显式超时重建语义维持不变、null 分支维持现状（不静默改绑全局键），nop-auth 零触碰；GraphQLEngine documentCache 反例（deps 恒空零 stat）经 deps-空豁免实现零误修。
5. **裁定⑤（同链复制面自动受益验证）**：`TenantAwareResourceLoadingCache` / `TenantAwareResourceCacheEntry` / `TenantAwareDependencyManager` / `ResourceCacheEntryWithLoader` 四包装层的自动受益**不得只凭类型存在断言**，必须以非继承委托穿透断言验证（Phase 1 focused tests：租户上下文 + 计数 loader + 可编程 mtime 注入 + 阈值 0/默认双档，逐层断言修复后门语义真实到达内层 entry/判据层）。
6. **裁定⑥（边界声明）**：R2-01 锁纪律面零重构（本批只改判据与门逻辑，`loadObject` 原子化在既有 synchronized 上下文内完成，零新增锁获取顺序）；R4-32 墙钟面——本批新增时间机制（宽限锚、记忆化 TTL、maxAge）一律复用 `CoreMetrics.currentTimeMillis`，新增敏感点清单随 Phase 3 R4-32 注记登记，既有敏感点零改写、不引入可注入时钟；确定性测试缝 = 全部时间阈值可配置且 **0 = 关闭**（退化为现行语义），必红断言以可编程 mtime 注入驱动、零 sleep；5.5-W-03 联动义务成立（判据统一即判据变更面），Phase 3 以 nop-rule focused 冒烟 + R3-05 注记落地。

## 二、背景与动机

check-changed（`nop.core.component.resource-cache.check-changed` 默认 true，CoreConfigs :86-87）是模型缓存（`ResourceComponentManager.modelCaches` 全家族：xbiz/xmeta/xdef/xpl/dict，及 i18n/dict/depends-with-cache/租户包装）的默认唯一失效链。该链存在三个已确认缺陷面，同链三面 + 一个别名（plan 2500 四成员）：

- **C7-007（正确性面）**：失效判据为单次 mtime 相等比较（`DefaultResourceChangeChecker` :21-22），存在两个永久漏检窗口——窗口一（同 tick 双写：第二次写入落在同一 mtime 粒度 tick 内，相等比较天然盲）；窗口二（load 与取 mtime 竞态：`ResourceDependsManager.collectDepends` :91 在 `task.get()` 完成后才记录 mtime，loader 读旧内容期间整文件替换 → 缓存持「旧内容+新 mtime」永久固化）。漏检后 `CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT` 默认 null 无 TTL 兜底，陈旧模型无限期服务、不可自愈。
- **C9-001（性能面）**：命中路径稳态（请求间隔 > refreshMinInterval=100ms 门）每次 get 执行 O(D) 全依赖图 walk + O(D) 次裸 stat + O(D) 分配（含 `isAnyDependsChange` :214-216 逐次 `new HashSet`）；时间戳缓存设施整体注释死代码，stat 完全无缓存。
- **C9-010（门缺失面）**：refreshMinInterval 节流门是 `ResourceLoadingCache.get` :192 的私有实现，`ResourceCacheEntry.getObject` 公开协议无任何时间门——6 处生产调用方（gateway `GatewayHttpFilter:127`、auth `DefaultDataAuthChecker:160-161`、xpl `XplLibTagCompiler:210,212,653-655`、dyn `DynOrmModelProvider:72,96`、orm `DefaultOrmModelProvider:36-41`、xdef `XDefinition:77-83`）全部以常量/默认 true 传入，每请求无条件走全依赖图 walk。
- **2.3-C9-002（别名）**：gateway 站点是 C9-010 设施缺陷在请求热路径的量化实例，family-clustering 裁定「同一缺陷，别名随主不双计」。

批行 exit criteria 的核心结构约束 = **三面单点修复**：四成员收敛到设施层的一个修复位，统一 check-changed 失效判据，禁止 checker 层、entry 层、6 个使用方各自打补丁。本文档即该唯一修复位的落点裁定与机械判定基准（closure audit 专项复核的操作化判据见 §3.1.3）。

## 三、核心设计

### 3.1 裁定①：判据统一与唯一修复位

#### 3.1.1 唯一判据载体

| 粒度 | 裁定 |
|---|---|
| 文件 | `nop-kernel/nop-core/src/main/java/io/nop/core/resource/deps/DefaultResourceChangeChecker.java` |
| 类 | `DefaultResourceChangeChecker`（平台默认 `IResourceChangeChecker` 实现，`ResourceComponentManager:91` 默认接线；自定义 checker 经协议替换时继承宽限=0 退化档，见下） |
| 机制 | 变更裁决语义唯一实现：`changed := (current != recorded) 或 (current == recorded 且 now - recordedAt < G)`；载体同时提供 stat 型入口（`checkChanged` 族，自取 current）与纯比较入口（load 路径持 m1/m2 时复用同一裁决，避免第三次 stat）——两入口机械收敛到同一比较实现 |
| 判据值 | `(lastModified, recordedAt)` 承载于 `ResourceDependencySet`（含虚拟根子类）；`recordedAt` 为宽限锚，簿记规则 = **仅在 lastModified 值变化时重置**（重读同值不重锚，防持续访问的热条目宽限窗口永续） |

裁决语义契约（载体内部，全仓唯一）：

```text
check(recorded, recordedAt):
    current := stat(path).lastModified
    if current != recorded            → CHANGED（新 tick 写入，返回 current 供记录）
    if now - recordedAt < G           → CHANGED（宽限期内保守：同 tick 双写不可排除）
    otherwise                         → UNCHANGED
G = 0                                  → 退化为现行「纯 mtime 相等」语义（关闭档）
```

协议扩展形态：`IResourceChangeChecker` 增加宽限感知 check 变体（default 方法，缺省委托既有 2-arg 入口 = 宽限 0 档）——既有自定义 checker 实现 source-compat 保持旧语义；`ResourceComponentManager` 的委托实现机械透传新变体。既有 2-arg 入口保留为宽限 0 档，不删签名。

#### 3.1.2 双窗口消除语义

**窗口二（load 竞态）——load 原子化，从属编辑点承载**：

- `ResourceCacheEntry.loadObject`：load 前取 m1（经载体 resolve，不走任何缓存层）；load 完成后（`collectDependsTo` 已记录 m2 到判据值）以纯比较入口裁决 m1 vs m2；不等 → 内容可能在 load 期间被替换 → **保守重载**，重载本身重新执行双 mtime 纪律；有界重试（重试上限为常量），超限 → `NopException` 显式失败（新错误码，携带 path）——禁止静默固化「旧内容+新 mtime」，禁止以返回旧值兜底（plan rule 24）。
- `ResourceDependsManager.collectDepends`（编译路径同链，审计失效面枚举同点位）：`task.get()` 前取 m1、完成后取 m2，同裁决同纪律——依赖集登记路径不引入同形态竞态。
- 机制声明：m1/m2 的**采集与传递**是机械簿记；「两 mtime 是否同值」的裁决经载体唯一实现，从属编辑点零内联比较。

**窗口一（同 tick 双写）——载体宽限期裁决**：

- 宽限期 `G` 为判据语义的组成部分（非门参数）：记录的判据值自记录时刻起 G 内为「未承诺」态，未承诺期内 check 保守判 CHANGED → 触发重载（重载重读内容，含宽限窗口内已落盘的同 tick 写入）；重载记录的 mtime 值若未变，宽限锚**不重置**（簿记规则，§3.1.1）——热条目不会因反复重载而永续宽限。
- G 默认 **2 秒**（覆盖容器 bind-mount/网络盘 1–2s mtime 报告粒度；ext4/overlayfs 1ms 粒度下宽限成本由门限速吸收，见下）；新配置键 `nop.core.component.resource-cache.change-grace-period`（Duration，0 = 关闭档）。
- 宽限期内保守 CHANGED 的重载频度由既有节流装置限速：门路径受 `isRefreshEnabled(refreshMinInterval)`（默认 100ms）约束，直连路径受裁定④ verdict 记忆化（同源 100ms）约束——**无每请求重载放大**；宽限冗余重载仅发生在变更事件后 G 窗口内、以门间隔为节奏，上界 ≈ G/100ms 次/条目。
- 残余漏检（宽限期最后一个门间隔内落盘的同 tick 写）由裁定③ TTL 兜底兜住——陈旧有界（≤ maxAge），非无限期。

#### 3.1.3 从属编辑点枚举表（closure audit 操作化判据）

**机械约束声明**：`DefaultResourceChangeChecker` 不在 load 路径上（`loadObject`/`collectDepends` 现状不经 checker 裁决），窗口二物理上必须由 load 路径从属编辑点承载——本设计声明 checker 层为**比较语义唯一实现位**，不声称其为「就地消除双窗口的唯一落点」。

**判据比较语义仅一处实现，其余为机械传递**：mtime 不等裁决 + 宽限裁决全仓唯一实现于载体类；下表全部从属编辑点只做判据值采集、簿记、协议传递与门限速，零内联第二套比较。closure audit 专项复核判据：①产品 diff 路径 ⊆ 下表「允许编辑」集合；②mtime 不等式变更裁决在载体类外 grep 零命中；③四成员确无第二修复位（checker/entry/使用方多层并存补丁即违规）。

| # | 允许编辑点（文件 · 机制；路径省略共同前缀 `nop-kernel/nop-core/src/main/java/`） | 归属裁定 | 允许的改动性质 |
|---|---|---|---|
| 1 | `io/nop/core/resource/deps/DefaultResourceChangeChecker` · 载体 | ① | 比较语义唯一实现（mtime 相等 + 宽限承诺）；stat 型与纯比较两入口 |
| 2 | `io/nop/core/resource/deps/IResourceChangeChecker` · 载体协议 | ① | 宽限感知 check 变体（default 方法向后兼容）；既有签名零删除 |
| 3 | `io/nop/core/resource/component/ResourceComponentManager` · 协议委托面 | ① | 新变体机械透传（`checkChanged` 委托扩展），零逻辑新增 |
| 4 | `io/nop/core/resource/deps/ResourceDependencySet` · 判据值承载 | ① | 宽限锚字段 + 簿记访问器（机械扩展）；`VirtualResourceDependencySet` 仅当继承不覆盖时零改动，需覆写时属本条允许面 |
| 5 | `io/nop/core/resource/cache/ResourceCacheEntry` · 窗口二 load 原子化（`loadObject`）+ 门所在类 | ①④ | m1 采集 + 载体裁决调用 + 有界保守重载；`isChanged` 结果记忆化 + maxAge 自愈 + deps-空豁免（§3.4） |
| 6 | `io/nop/core/resource/deps/ResourceDependsManager` · 窗口二记录原子化（`collectDepends`）+ 窗口一涂新点（`isDependencyChanged` :246） | ① | m1/m2 采集与载体裁决；涂新点宽限锚簿记（值变重锚/同值不重锚） |
| 7 | `io/nop/core/resource/cache/ResourceLoadingCache` · 门所在类 | ①③ | `get` 门与 entry 门协同；:81 TTL 唯一消费点承接默认值变更（零逻辑变更预期，语义钉面） |
| 8 | `io/nop/core/resource/cache/CacheEntryManagement` · 3-arg 变体声明面 | ④ | 语义 javadoc 钉（§3.4）；逻辑零变更预期 |
| 9 | `io/nop/core/resource/cache/ResourceTimestampCache` | ② | 整文件删除 |
| 10 | `io/nop/core/CoreConfigs` | ①②③ | 新增宽限键；两死配置删除；TTL 键默认值变更 |
| 11 | `io/nop/core/CoreConstants` | ② | `RESOURCE_TIMESTAMP_CACHE_NAME` 删除 |
| 12 | `io/nop/core/CoreErrors` · 错误码登记 | ① | 窗口二重试超限错误码（机械登记） |

表外产品路径出现在 diff = 违反单点纪律。测试代码（nop-core `resource/` 测试、nop-xlang `TestXplTask`）不受本表约束，但受 E2 同 diff 规则约束（§六）。

#### 3.1.4 判据统一一致性声明

- **门路径 vs 6 直连路径可对照**：两路径消费同一载体、同一 entry 判据状态——同一 entry 在同一时刻无论经 `ResourceLoadingCache.get`（门路径）或 `getObject(true, …)`（直连路径）判定，失效裁决结果同值；差异仅在节流装置的接线位（门路径 = `isRefreshEnabled` 先置门，直连路径 = entry 内记忆化门），节流阈值同源单键（`CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL`，默认 100ms）。
- **消费形态语义影响**：`TenantAwareResourceLoadingCache`、`depends-with-cache`（`ResourceDependsManager.cache`，`runWhenDependsChanged` 通道）、i18n-cache、dict 等全部既有消费形态行为增量 = ①宽限期内门限速保守重载、③maxAge 周期性自愈重载——失效传播及时性单调改善，零接口/协议/配置键名变化；判定语义经共享载体自动一致，无逐面适配。

### 3.2 裁定②：ResourceTimestampCache 移除终裁

**终裁 = 移除**（C7-007 建议 (4) 与 C9-001「恢复时间戳缓存」建议的冲突，一次裁决取移除）。

- 清理面：`ResourceTimestampCache.java` 整文件 + `CFG_COMPONENT_RESOURCE_TIMESTAMP_CACHE_SIZE/TIMEOUT` 两配置键 + `CoreConstants.RESOURCE_TIMESTAMP_CACHE_NAME` 常量。三者互相唯一引用，全仓 grep 零活引用（live 核实：仅注释死代码与配置声明命中）。
- 拒绝活化的判据安全性论证（缓存粒度与判据安全性叠加面）：(a) 时间戳缓存 TTL 窗口内 stat 不发生 → 该窗口内 mtime 变化不可见 = **窗口一同形态漏检由缓存层重新引入**，判据本体修复被缓存层致盲；(b) 窗口二修复依赖 load 前后两次**真实** stat 读数（m1/m2），时间戳缓存拦截 stat 后 m1≡m2 恒成立，load 原子化被架空；(c) 节流收益已被裁定④ verdict 记忆化完全覆盖——整链 O(1) 短路严格优于 per-file O(D) 缓存命中，且记忆化缓存的是**裁决结果**而非判据输入，不参与裁决、无致盲面。
- stat 税性能面完整落点 = 裁定④ entry 级记忆化（本裁定不使 C9-001 悬空）；`isAnyDependsChange :214-216` 的 `new HashSet` 逐次分配随记忆化摊薄（记忆化命中 → 零 walk 零分配；未命中路径的单次 HashSet 为循环依赖防御所需，保留不做独立优化——NFS/慢存储场景的深度优化归 plan Deferred 节既定条款）。
- 静默断档禁令（rule 24）：两死配置**随代码删除**（键不复存在，不残留「配了不认」）；已配置该两键的存量部署属配置契约移除，键移除清单随 source-anchors 新锚点登记（owner doc 义务，§3.3）。

### 3.3 裁定③：TTL 兜底与自愈通道覆盖矩阵

**终裁**：`CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT`（`nop.core.component.resource-cache.timeout`）默认值 null → **`Duration.ofMinutes(5)`**，作为漏检自愈通道的统一配置源；负值 = 显式关闭档（`createCache`/`refreshConfig` 既有 `!isNegative()` 守卫语义，等价现行 null 行为——关闭通道显式可声明，非静默缺失）。

**自愈通道覆盖矩阵**（义务 (a)：两路径各自载体，无路径缺载体）：

| 路径 | 自愈载体 | 语义 | 及时性 |
|---|---|---|---|
| 门路径（`ResourceLoadingCache` 族：modelCaches 全家族、i18n-cache、dict、depends-with-cache、租户包装内层） | expireAfterWrite 整条目逐出（既有机制，`ResourceLoadingCache:81` 唯一消费点经 `createCache`/`refreshConfig` 落 `setExpireAfterWrite`）→ 下次访问冷加载重建 | **冷启动式自愈，非陈旧期内廉价重检**：整条目丢弃、loader 全量重放 | TTL + Caffeine 惰性 maintenance 迟滞（C7-009，open）+ 首次后续访问间隔 |
| 6 直连路径（GatewayHttpFilter / DefaultDataAuthChecker / XplLibTagCompiler / DynOrmModelProvider / DefaultOrmModelProvider / XDefinition 的 entry 不经 :81 消费点） | **entry 级 maxAge 强制重载**（裁定④门内建，交叉引用裁定④；不经 expireAfterWrite，无逐出迟滞） | 就地整条目重载（复用 `reloadObject` 既有重载/销毁路径），不经判据裁决（判据可能已漏检，强制绕过） | TTL（判定即时，仅受记忆化门节奏约束） |

**source-compat 声明**（义务 (b)）：

1. 默认值变更 = 行为变更：全部 `ResourceLoadingCache` 条目默认获得 5 分钟生命周期；逐出即冷加载，首请求延迟上升、loader 副作用重放（loader 幂等性为既有契约）；对「null = 条目永久驻留」的既有依赖方，需关闭时显式配置负值。
2. expireAfterWrite 逐出/destroy 语义代价：整条目丢弃 → 下次请求冷加载重建（非廉价重检）；`ResourceLoadingCache` 的 `CacheConfig` 未启用 `destroyOnRemove` → 逐出不触发 `entry.clear()`/destroy 显式销毁，资源释放交 GC——**维持现状生命周期形态，不扩展**（显式销毁形态归 R2-01/C2-002 边界，裁定⑥）；实际释放时点受 C7-009 惰性清理迟滞影响，自愈及时性下界 = TTL + maintenance 迟滞 + 首次访问。
3. 命名键优先序不变：`nop.core.component.resource-cache.{name}.timeout` 显式配置时优先于全局键（`OverrideConfigReference` 既有语义，live 核实）；本批仅变更全局键默认值。
4. owner doc 义务：source-anchors 新建模型缓存锚点登记——键默认值变更、负值关闭档、命名键优先序、键移除清单（裁定②）、宽限键（§3.1.2）。

### 3.4 裁定④：直连使用方门语义

**主选终裁 = `ResourceCacheEntry` 层内建门**——6 使用方零改码获得节流 + 统一判据 + 自愈；2.3-C9-002 别名面随之闭合。备选逐点修分支（列 6 站点改码清单 + nop-auth ask-first 升级）**不选**：单点修复纪律下使用方改码即第二修复位，且 auth 面触发 ask-first。

entry 门契约（`getObject(checkChanged, loader)`，`checkChanged=true` 时）：

1. **deps-空豁免**：`deps` 判据集的依赖收集为空（text-parse 类条目，`isChanged` 恒 false 零 stat）→ 门全部旁路，行为逐字节维持现状。
2. **verdict 记忆化（节流）**：`isChanged()` 裁决结果按 entry 记忆化，TTL = `CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL` 同源（默认 100ms；0 = 关闭档，逐请求 walk，即现行直连行为）；FALSE → 短路返回缓存值（零 walk 零 stat）；TRUE → 既有 synchronized 重载路径。记忆化状态在 `loadObject` 完成时重置（新加载后首查必走真实裁决），`clear()` 后经未加载态自然旁路。C9-001 stat 税面在此收敛：命中路径 walk 频度 ≤ 1/100ms/条目，与门路径同口径。
3. **maxAge 强制重载（自愈，裁定③载体）**：`now - lastLoadTime > TTL`（TTL 源 = `CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT`，默认 5 分钟）→ 强制重载，不经判据裁决；仅对 deps 非空 entry 生效（豁免面见 1）；`checkChanged=false` 调用不触发（尊重显式不检查语义）。

**6 使用方逐一行为声明**（零改码依据）：每站点自动获得「100ms 记忆化节流 + 5min maxAge 自愈 + 载体统一判据」——①gateway `handlerCache.getObject(true, …)`（别名成员 4 同位点）；②auth `modelCache.getObject(true, …, timeout)`；③xpl `cachedCompiledTag(ForNodeMode)`（经 `ResourceCacheEntryWithLoader` 1-arg 纯委托到达 2-arg 门，穿透断言见裁定⑤）；④dyn-orm、⑤orm interceptor、⑥xdef defaultsDsl。

**`CacheEntryManagement` 3-arg 变体行为显式声明**：`getObject(checkChanged, loader, timeout)` 的「`isRefreshEnabled(timeout)` → `clear()` 重建」显式超时语义**维持不变**；`CFG_AUTH_DATA_AUTH_CACHE_TIMEOUT` 默认 null → 分支恒死**维持现状**（不静默改绑全局键——配置契约显式性；该键行为变更归 nop-auth owner 批次）。DefaultDataAuthChecker 站点的节流与自愈由底层 entry 门承载，nop-auth 产品代码零触碰——ask-first 不触发。

**GraphQLEngine documentCache 反例零误修声明**：deps 恒空前提（text-parse 条目无依赖钉定 → `isChanged` 恒 false 零 stat）经 deps-空豁免获得零误修保证——记忆化与 maxAge 均不启用，该站点行为不变；后续任何对该站点的门/判据改动须先核对 deps 恒空前提不被破坏（plan Non-Blocking Follow-ups 反例守卫承接）。

### 3.5 裁定⑤：同链复制面委托穿透验证形态

四包装层**均为委托/装饰器形态而非判据语义复制**（除 `ResourceCacheEntryWithLoader` 为继承但 1-arg 纯委托），自动受益不得只凭类型存在断言；Phase 1 必须交付下表穿透断言（focused tests）：

| 包装层 | 委托形态 | 穿透断言要求（阈值 0 档 + 默认档双档） |
|---|---|---|
| `TenantAwareResourceLoadingCache` | 组合+委托：`get(path)` → `getCache(path).get` → 门路径 → entry 门 | 租户上下文下连续两次 get：门限速 + entry 记忆化生效（计数 loader 断言 walk 次数）；G>0 宽限保守重载经包装层可达 |
| `TenantAwareResourceCacheEntry` | 装饰器：`getObject` → `getCacheEntry().getObject` → per-tenant `ResourceCacheEntry` | 直连 entry 门（记忆化 + maxAge）经 `getCacheEntry()` 真实到达内层 entry（计数 loader + mtime 注入） |
| `TenantAwareDependencyManager` | 组合+委托：`getImpl()` → per-tenant `ResourceDependsManager` | 宽限裁决经 `getImpl()` 到达判据消费层：G=0 等价性钉（退化为现行语义）+ G>0 保守判定钉（同 tick 双写注入 → 判已变更） |
| `ResourceCacheEntryWithLoader` | 继承，1-arg `getObject` 纯委托 2-arg | 1-arg 调用到达 2-arg entry 门（记忆化/maxAge 生效） |

断言载体 = 计数 loader（walk/stat 计数）+ 可编程 `Resource.lastModified()` 注入 + 阈值可配置缝；禁止以「包装类存在且未改动」替代行为断言。

### 3.6 裁定⑥：边界声明

**与 R2-01（C2-001/C2-002 同文件锁纪律面）**：本批只改判据与门逻辑——`getObject` 快路径与加载的监视器持有形态、`reloadObject` synchronized 语义、`clear`/`destroy` 生命周期形态零重构；窗口二 load 原子化（m1 采集/载体裁决/有界重试）在既有 synchronized 上下文内完成，**零新增锁获取顺序**（C2-001 死锁面零新增）；`TestResourceCacheEntry` 既有钉（reload-null 占位语义）零回退；R2-01 后续锁修复以本批落盘后基线为准。

**与 R4-32（墙钟面）**：(a) 既有 currentTimeMillis 敏感点（`isRefreshEnabled:104` / `loadObject:223`）零改写、不换时钟源、不引入可注入时钟（换源工程归 R4-32 本体）；(b) 本批新增时间机制——宽限锚裁决、verdict 记忆化 TTL、entry maxAge——**一律复用 `CoreMetrics.currentTimeMillis`**（R4-32 换源自动覆盖新增点），新增敏感点清单（预计 3 类消费点，非零新增）随 Phase 3 R4-32 注记登记于 cra-index/日志；(c) **确定性测试缝**：G / 记忆化 TTL / maxAge 阈值全部可配置且 0 = 关闭；窗口一/二必红断言以可编程 mtime 注入驱动，不依赖真实时间推进；宽限开启态断言的确定性 = 测试执行时长 ≪ G（秒级阈值 + 即时断言），零 sleep。

**与 5.5-W-03（R3-05 联动口径）**：本批判据统一构成判据变更面（宽限期内保守重载 + maxAge 周期重载为对外可见行为变化）→ **联动义务成立**（默认分支，不做「判据语义对外不变」论证）；Phase 3 验证形态 = nop-rule focused 冒烟（规则定义行变更 → 规则模型失效传播在宽限窗口内不降级：热更新经保守重载及时生效，钉定集语义零变化）+ R3-05 注记落账；W-03 缺陷本体（`DaoRuleModelLoader:84` traceDepends 钉定粒度缺口）零触碰归 R3-05——本批判据修复不遮蔽、亦不替代该钉定面修复。

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|---|---|
| ResourceTimestampCache 活化（带 TTL 复活降 stat 税） | 缓存粒度致盲判据：TTL 窗口内 stat 缺失 = 窗口一同形态漏检重现；拦截 load 前后 stat 使 m1≡m2 恒成立、窗口二修复被架空；节流收益被 verdict 记忆化严格覆盖（§3.2） |
| 「checker 层唯一落点就地消除双窗口」 | 机械不可行：checker 不在 load 路径，窗口二必须由 load 路径从属编辑点承载；声称该形态即违反单点修复位的机械判定（§3.1.3） |
| 判据比较实现下沉到 entry 层 / checker 与 entry 双判据并存 | 违反批行单点纪律（checker/entry 两套判据 = 多层并存补丁）；closure audit 判据②grep 全仓唯一即不成立 |
| 窗口一以内容指纹（size+hash）为逐次 check 判据 | 把 stat 税换为 read 税：每次 check 读内容+哈希，与同批 C9-001 性能面目标直接冲突；宽限期裁决零额外 syscall 且确定性可测 |
| 6 使用方逐点修（改用 ResourceLoadingCache / 改 config 默认 / 逐站补门） | 使用方改码 = 第二修复位违反单点纪律；②站点改 nop-auth-service 触发 ask-first；逐站行为漂移不可对照（§3.4） |
| `CFG_AUTH_DATA_AUTH_CACHE_TIMEOUT` null 分支改绑全局 TTL 键 | 静默变更 nop-auth 配置语义；配置契约变更须 owner 批次显式裁决，本批只声明现状（§3.4） |
| maxAge 自愈对 deps 空条目生效 | 对 documentCache 类 text-parse 条目引入无意义周期重载（行为回归且零自愈收益——该面本就零漏检）；以 deps-空豁免保持零误修（§3.4） |
| 宽限锚随每次重载重置 | 持续访问的热条目宽限窗口永续 → 永续保守重载风暴；「值变才重锚」簿记规则消除该放大（§3.1.1/3.1.2） |

## 五、四成员 → 裁定映射

| 成员 | 面 | 消费裁定 | 修复位 |
|---|---|---|---|
| 2.1-C7-007（P1 族主） | 正确性：双漏检窗口 + 无 TTL 兜底 + 时间戳缓存死代码 | ①双窗口闭合 + ②移除终裁 + ③TTL 兜底默认启用 | 判据载体（比较语义）+ load 路径从属编辑点（窗口二）+ 配置键（兜底） |
| 2.1-C9-001（P2，G24） | 性能：稳态 O(D) walk + O(D) 裸 stat + 逐次分配 | ②移除 + stat 税落点 = ④记忆化；HashSet 分配随记忆化摊薄 | entry 门（`ResourceCacheEntry`） |
| 2.1-C9-010（P2，G24） | 门缺失：6 直连使用方绕过节流门 | ④entry 级门单点覆盖 6 使用方零改码 | entry 门（`ResourceCacheEntry`） |
| 2.3-C9-002（P2，G38 别名） | gateway 实例面 = C9-010 同位点 | ④随主闭合，零独立修复位（Phase 2 行为验证 + 零改码确认） | —（随成员 3） |

## 六、C10-005 + N3 验收前置声明

- **同 diff 义务（E2）**：`nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xpl/TestXplTask.java`（:37 类级 `@Disabled`）解除/改写与判据本体**同一 commit**；覆盖 watch→debounce→checkChanged→模型重载整条链。
- **F3 预案（批外缺陷污染）**：解除类级 `@Disabled` 后，debounce/watch 面断言可能因 C1-004（debounce 非原子，R4-13 open，不在本批成员面）变红——处置 = checkChanged 失效链断言必须全绿；debounce/watch 面失败允许方法级隔离 + 原因注记 + R4-13 对端回归义务传导（或改写为绕开 debounce 触发形态的等效链路测试）；**禁止整类回退 `@Disabled`**（rule 24 静默跳过嫌疑）；隔离注记落 daily log 与 cra-index R4-13 可见载体。
- **双窗口确定性测试形态**：窗口二 = 可编程 mtime 注入断言「load 期间 mtime 变更 → 不固化旧内容+新 mtime」（先红后绿）；窗口一 = 同 tick 双写模拟（两次写同 mtime）+ G 双档断言（G=0 关闭档红/默认档绿）；另含 checkChanged 默认开启冒烟（改文件 → 模型失效）。
- **接线验证（rule 23）**：≥1 条断言证明新判据在真实 `loadObject → collectDependsTo → checker` 链上运行时生效（非仅单类单元面）；穿透断言按裁定⑤表执行。
- **无越界承接确认**：C10-005 涉及缺陷（C7-007/C9-001/C9-010）与本批三成员完全重合，无批外面承接。

## 七、与已有设计的关系

- 同目录 `pool-facility-hardening.md` / `periodic-task-insulation.md` / `bindcancel-context-unification.md`：executor/取消链设施面，与本设计零共享代码位（本批改动收敛于 `io.nop.core.resource` 失效链，不触碰 `io.nop.commons.concurrent`）。
- `R2-01`（plan 待立项，C2-001/C2-002/C2-017/C1-003/C1-014）：`ResourceCacheEntry` 锁纪律与生命周期面 successor，边界见裁定⑥；本批落盘基线为其输入。
- `R4-32`（R28 墙钟批）：`CoreMetrics.currentTimeMillis` 换源面 successor，本批新增敏感点经 Phase 3 注记并入其清单。
- `R3-05`（roadmap 8.35）/`R3-02`（roadmap 8.32）：5.5-W-03 本体修复与 5.2-I-04 先例对账 successor；本批判据形态（§3.1）为两批的先例输入。
- owner doc 去向：`docs-for-ai/04-reference/source-anchors.md` 新建模型缓存锚点（内容源 = 本文档裁定，派生摘要标注「以设计文档为准」），归 plan 2500 Phase 3 统一落账。
