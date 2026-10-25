# 2504 CRA Phase 8.10 — R2-01 2.1 kernel 剩余批：退役代际族 + RCE 死锁族（7 项）

> Plan Status: active
> Draft Review: R1 agent_450d86fc（0 Blocker、2 Major[Major-1 提交纪律粒度自相矛盾/Major-2 dockerless 测试缝未声明]+5 Minor——修订 agent_f230bc59 全量应用[live 核验后落笔]，active）
> Draft Review: 待执行（两道评审关：①本 plan 文本级独立评审随批次排期执行后回填；②Phase 1 设计裁定文档独立 draft review 为 plan-first 硬前置，0 Blocker 方可转 active 进入实现）
> Last Reviewed: 2026-10-25
> Source: roadmap 工作项 8.10（R2-01，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L152，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.1 R2-01 行（L75：finding ID 清单 7 项 / PA=**框架核心引擎 plan-first** / 依赖=无前置可并行 + 2.2-C1-007[R2-04] 刷新链上游联动注记 / exit criteria 增补三条）；族归组 = family-clustering.md `2.1-退役代际族（混合）`行 + `2.1-ResourceCacheEntry/死锁族（混合）`行 + §四#6 双轨裁定；成员发现详情 = 各单元审计目录（见 Current Baseline 逐条回读路径）；执行基线 @ `124e451af5`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.9 已 done，plan 2495–2503 均 completed；本 Baseline 全部行号为该基线 live 回读值，执行前如再漂移以 live 为准并登记）
> Related: plan 2496（R1-01 绝缘层批，completed——`DefaultScheduledExecutor` 周期链绝缘与 `periodicTasks` 守卫登记表为本批 C1-003 修复共享代码位，**零回退义务**）；plan 2497（R1-02 池设施面批，completed——`DefaultScheduledExecutor.refreshConfig` 类型化热更路径与 `ExecutorHelper.updateThreadPool` 收缩重排为本批 C1-003/C1-001 邻接代码位，**零回退义务**；RoundRobinSupplier 不在其修复面，C1-001 为遗留残余，见勘误 5）；plan 2500（R1-05 缓存失效链批，completed——`ResourceCacheEntry` 载体已承载 verdict 记忆化门/双 mtime 守卫（CACHE-001 锚点行），本批 C2-001/C2-002 修复共享该文件，**门语义零回退义务**）；plan 2503（R1-08，completed，编号续接）；**下游交互**：R2-04（roadmap 8.13——2.2-C1-007 刷新链上游联动注记落账 + 2.2-C2-002[ProducedBeanInstance] 防混边界，勘误 1）；R4-13/R4-14（roadmap 8.53/8.54——G27 束 Deps 列硬依赖 8.2/8.3/8.10，本批收口即其前置解除）；R4-32（C7-004 拆出件）
> Protected Area 与授权：本批 **PA=框架核心引擎（nop-kernel 内部）plan-first**（batch-proposal §四.3 落批行「R1-01、R1-02、R1-05、R2-01…」；AGENTS.md Protected Areas 表「框架核心引擎（nop-core/nop-xlang/nop-xdef 内部）」行）——**Phase 1 设计裁定文档 + 独立 draft review 为进入实现（Phase 2/3）的硬前置**，设计文档+回归测试为 Required Evidence。触碰面：`nop-kernel/nop-commons`（RoundRobinSupplier/DefaultScheduledExecutor）、`nop-kernel/nop-core`（DefaultVirtualFileSystem/ResourceCacheEntry/ResourceLoadingCache/ResourceComponentManager）、`nop-kernel/nop-xlang`（XplLibTagCompiler/LazyCompiledExecutableFunction）、`nop-persistence/nop-nosql/nop-nosql-lettuce`（2.5-C6-005 使用面，非保护区）。零触碰声明（E4）：产品 diff 不含 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物；xlang codegen 若因源模型/模板语义修正触发确定性再生产物，按「改源 → 再生成」路径豁免并登记（沿 plan 2498 Phase 7 先例）。

## Purpose

关闭 R2-01「2.1 kernel 剩余批」的 7 项已确认发现（batch-proposal §二.1 R2-01 行：P1×2——2.1-C1-001[退役代际族主，吸收 C2-021]、2.1-C2-001[RCE 死锁族主]；P2×5——2.1-C1-003[G24]、2.1-C1-014[G24，携 N2 修复阻断预警钉]、2.1-C2-002[G24]、2.1-C2-017[G24，与 C2-001 同变更集绑定]、2.5-C6-005[G48，双轨联动]）：①退役代际族（C1-001/C1-003/C1-014）以一个「旧代资源延迟退役」平台机制单点收敛——resize 收缩锁外关闭窗、destroy 置空 NPE 窗、VFS zip 句柄在飞读者窗三站同机制；②RCE 死锁族（C2-001/C2-017 同变更集 + C2-002）以「监视器内只做同 key 去重+占位、加载/编译在锁外」统一不变式闭合三层监视器链（entry/标签 entry/LazyFn）与 use-after-destroy 窗；③2.5-C6-005 按 §四#6 双轨联动——设施主责随 C1-001 本体，使用面（LettuceRedisConnectionProvider.refreshConfig 缩容取用窗）随批接入并带 C10-026 缩容触发回归。批末 roadmap 8.10 → done，7 项成员台账全部收敛，N2 钉同批解除，G24 行具备整组置 fixed 条件。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.1 R2-01 = 7 项成员（P1×2 + P2×5）；cra-index P1 台账 2 行（L190 `nop-kernel-concurrency-C1-001`、L191 `nop-kernel-concurrency-C2-001`，裁定批次均=R2-01、状态均=open）+ P2 搭车组 2 行（G24[L402]：七成员中 C3-015 已随 R1-01 落地、C9-001/C9-010 已随 R1-05 落地[cra-index L372 注记「G24 组行仅收敛上述三成员面，不整组置 fixed」]——**本批承接其余四成员 C1-003/C1-014/C2-002/C2-017，收口后 G24 方可整组置 fixed**；G48[L426]：2.5-C6-005 成员面随本批，2.5-C1-010 归 R2-04）；roadmap 8.10 行 Status=todo。
- **PA 授权链在位**：batch-proposal §四.3 框架核心引擎行明列 R2-01 为 plan-first 批；R1-01/R1-02/R1-05 三批同授权先例（plan 2496/2497/2500）的「设计文档先落盘提交 → 独立 draft review → 实现按工单逐项提交」执行序为本批 Phase 1 模板。
- 依赖面：无前置硬依赖（可并行）；2.2-C1-007（R2-04，cra-index L195「配置发布协议三字段裸写裸读+两步写序撕裂……WI 2.1 C1-001 刷新链上游」）为**刷新链上游联动注记**——本批修下游取用窗，R2-04 修上游发布协议，无代码依赖，仅 cra-index 双行互注（Phase 5）。

### 前批边界（R1-01/R1-02/R1-05 已落产物与共享代码位——零回退义务）

- **R1-01 绝缘层**（plan 2496）：`DefaultScheduledExecutor` 周期方法路由绝缘核心、`periodicTasks` 守卫登记表（live :39/:122-137）与 `destroy()` 统一拒绝终态（`rejectChain` :122-125）。本批 C1-003 修复共享 destroy/checkEnabled 代码位——绝缘拒绝终态语义（future 有终态、不悬挂、链不复活）不回退；`TestPeriodicTaskInsulation`（9 条）与下游绝缘回归为共享代码位门禁。
- **R1-02 池设施**（plan 2497）：`DefaultScheduledExecutor.refreshConfig` scheduled 专用类型化路径（live :74-97）与 `ExecutorHelper.updateThreadPool` 收缩重排为 **TPE 池热更面**修复。**RoundRobinSupplier 不在 R1-02 修复面**：live @ `124e451af5` 核对 resize 收缩锁外 closeNext（:52-86）维持原样——C1-001 设施残余与 2.5-C6-005 取用窗均未修复（勘误 5）；本批 `DefaultScheduledExecutor` 改动不回退 refreshConfig 类型化异常语义与 C10-010 成员面测试。
- **R1-05 缓存失效链**（plan 2500）：`ResourceCacheEntry` 载体已承载 entry 内建门（`shouldReloadObject` live :215-253，verdict 记忆化/maxAge/deps-空豁免）与 load 双 mtime 守卫（`executeLoadWithMtimeGuard` :313-326）、判据统一（`DefaultResourceChangeChecker`，source-anchors CACHE-001 锚点行）。本批 C2-001/C2-002 修复共享该文件——**门语义零回退**（6 直连使用方契约与 `nop.core.component.resource-cache.*` 配置键行为不变）；C2-001 占位化改造与门/记忆化状态的交互由 Phase 1 裁定⑥显式声明。

### 7 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `124e451af5` 回读值，执行前如再漂移以 live 为准并登记；漂移成因见勘误 3）

| # | Finding ID（判级/台账） | 单元 / 模块 | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向（Phase 1 对应裁定块） |
|---|---|---|---|---|---|
| 1 | 2.1-C1-001（P1，退役代际族主；cra-index L190；吸收 C2-021） | 2.1 / nop-kernel（nop-commons） | `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/RoundRobinSupplier.java:41-45`（get 快照读→解引用）、`:47-87`（resize 收缩 :60-61 发布新数组后 finally 锁外 closeNext :83-86）、`:89-93`（closeNext）、`:96-103`（close 全关闭与并发 get 同族面）；生产链 `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:61-68/:72-79/:82-89`（get×3）与 `:92-100`（refreshConfig→resize） | resize 收缩在锁外关闭旧数组尾段，并发 get 经快照取得已关闭资源（use-after-close；Lettuce redis 连接池生产在用，IoC 配置刷新自动触发 resize；扩容分支安全） | 裁定①（退役代际平台机制单点方案）：旧代资源「发布新代 + 延迟退役关闭」统一形态；get 热路径锁形态与性能取向（锁内读取 vs 快照+退役）一次定稿；close() 同族面（F3③）同 diff 覆盖 |
| 2 | 2.1-C2-001（P1，RCE 死锁族主；cra-index L191） | 2.1 / nop-kernel（nop-core + nop-xlang） | `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceCacheEntry.java:255-287`（getObject :258 synchronized 内 :265 loadObject）、`:348-361`（reloadObject 同链）、`:295-306`（loadObject → :316 collectDependsTo→loader）、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/ResourceComponentManager.java:99-128`（ModelLoader.loadObjectFromPath 递归加载）、递归入口 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/functions/GlobalFunctions.java:438-441`（loadDslModel→requireComponentModel） | 持 entry 监视器执行模型加载（解析+编译 I/O），加载器递归经 ResourceComponentManager 加载依赖模型：循环依赖模型 + 两线程相反顺序 = 无超时永久死锁（E1 环；单线程循环 = 可重入监视器无限重载至 StackOverflowError，Error 逃出 catch(Exception)） | 裁定②（C2-001/C2-017 同变更集方案——统一不变式：监视器内只做同 key 去重+占位，加载/编译在锁外）：entry 层 getObject/reloadObject 占位化 + 循环依赖检测类型化收敛（SOE 面一并闭合） |
| 3 | 2.1-C1-003（P2，G24） | 2.1 / nop-kernel（nop-commons） | `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/DefaultScheduledExecutor.java:115-126`（destroy :118 无锁置空 + 绝缘 rejectChain :122-125）、`:144-149`（checkEnabled）、六入口 `:152-155/:158-161/:164-167/:170-173`（四参 scheduleAtFixedRate 委托六参，六参内 :185 直用 executor 字段）`/:196-208`（scheduleWithFixedDelay 经 PeriodicTaskHelper 以 this 重入 schedule）、`:211-214`（execute）、`:111-113`（stats） | checkEnabled-then-use 与 destroy 置空竞态：窗口内提交以裸 NPE 失败而非类型化 ERR_SERVICE_NOT_ACTIVE（六入口+stats() 全部同型；globalTimer 全平台共享，停机/实例替换窗可达） | 裁定①：快照/终态形态一次定稿（局部快照 vs destroy 不置 null——与 `isDestroyed()` :140-142、R1-01 拒绝终态、R1-02 refreshConfig 类型化语义的交互显式声明）；六入口+stats() 参数化回归 |
| 4 | 2.1-C1-014（P2，G24；**携 N2 修复阻断预警钉——同批解除**） | 2.1 / nop-kernel（nop-core） | `nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DefaultVirtualFileSystem.java:70-77`（refresh 先发布后关旧 zipFiles）、`:87-94`（destroy 同型）、`:79-84`（buildResourceStore 发布点）；受害读点 `nop-kernel/nop-core/src/main/java/io/nop/core/resource/impl/ZipEntryResource.java:98-100`（共享 ZipFile 直读）；唯一生产 refresh 入口 `nop-service-framework/nop-biz/src/main/java/io/nop/biz/dev/DevToolBizModel.java:46` | refresh/destroy 发布新 store 后关闭旧 zipFiles：发布前已捕获旧 store 的在飞读者经共享 ZipFile 句柄读 → `zip file closed`（「先发布后关闭」只护后发读者；DevTool 热刷新 × jar 层部署） | 裁定①/⑤：旧代 zip 关闭时机按退役代际机制延迟（在飞读者退出后关）；缓解对照 `AutoCloseZipEntryResource`（io/nop/core/resource/zip/ 包，按路径重开）为三选一支路；**N2 钉 `TestDefaultVirtualFileSystemRefresh#testRefreshClosesOldZipOnlyAfterRebuild`（nop-core 测试 :41-68，「refresh 返回后旧 zip 立即 closed」断言）与退役修复冲突，同变更集改写**（裁定⑤定改写后断言语义） |
| 5 | 2.1-C2-002（P2，G24；**勘误 1 防混：非 2.2-C2-002 ProducedBeanInstance**） | 2.1 / nop-kernel（nop-core） | `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceCacheEntry.java:135-148`（clear 锁内 listener.onDestroyed + safeDestroy）、`:363-370`（destroyObject，reloadObject 换新旧对象销毁同案）、getObject 快路径 :255-256/:286（无锁交出引用）；失效入口 `ResourceLoadingCache.java:163-167`（remove→entry.clear）、`:240-241`（state_loadFrom putIfAbsent 失败 clear）；生产失效链 = ResourceVersionedModelStore 热重载 / CoreInitialization clearAllCache / GlobalCacheRegistry.clearForTenant | 快路径无锁读与 clear()/remove() 锁内销毁并发：读者持已交出引用继续使用已销毁模型（use-after-destroy 窗；无引用计数、无宽限期、无延迟销毁队列） | 裁定③（失效/销毁时序方案）：标记失效+延迟销毁（与裁定①退役代际机制同构复用）或读者登记——机制分支二选一定稿；**终态不得为 javadoc-only/warn-only**（rule 16 防假收敛）；与 R1-05 门语义共存声明 |
| 6 | 2.1-C2-017（P2，G24；**与 C2-001 同变更集绑定**） | 2.1 / nop-kernel（nop-xlang） | `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/xlib/XplLibTagCompiler.java:734-770`（LazyCompiledFunction.compile synchronized 持自身监视器完整编译：:800 runImportExprs import 求值+宏执行+生成类绑定）、`:210/:226/:232`（parseTag 在模型 entry 监视器链内 getObject→lazyCompile→宏立即执行）、`:651-656`（getFunctionModel 第二入口）、`:108`（cachedCompiledTagForNodeMode 标签 entry，buildCompiledTag 锁内 I/O 随案子面）、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/LazyCompiledExecutableFunction.java:80`（运行时首触 getCompiled） | LazyFn 监视器内完整 XPL 编译且在模型 entry 监视器链内触发：锁序图新增环④/环⑤两条可闭合环——只修 entry 层（C2-001）则环④残余闭合，**必须与 C2-001 同一变更集**（家族裁定：统一不变式贯穿三层链） | 裁定②：LazyFn 层 compile per-key 单飞（监视器内只做 compiled 双检+占位）+ parseTag/getFunctionModel 惰性编译调用点移出模板 entry 监视器 + buildCompiledTag 随案覆盖（失效面#4，P3 判级随案不独立计数） |
| 7 | 2.5-C6-005（P2，G48；**双轨联动 §四#6，已知未修复前案**[check/nosql-cdc.md 2026-08-19 P3 + WI 2.1 C1-001 P1 设施主责]） | 2.5 / nop-persistence（nop-nosql-lettuce） | `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:92-100`（refreshConfig→resize 无排空/宽限）、取用侧四入口 `:61-68/:72-79/:82-89`（pubsub :140-146 独立建连豁免）、`:103-125`（doStart 建池）、`:128-138`（doStop 安全） | refreshConfig 收缩经 resize 立即 close 被裁剪共享连接：已交付在用连接被热关，取用窗口内瞬时命令失败（显式 close 不走 autoReconnect；有界自限但对外为配置变更瞬间错误尖峰） | 裁定④（双轨联动方案）：设施面（裁定①产物）对「已交付在用共享连接」的关闭时机契约（延迟关闭宽限窗/排空形态——共享 RoundRobin 无借还制）+ Lettuce 使用面接入 + `getConnection` javadoc 共享连接不得 close 契约；**C10-026 义务**：缩容触发回归测试先红（现有 testClientResourcesNotLeakedAcrossStartStop 不触发 resize，缩容排空修复不触红——C10-test-protection.md :99/:536-546） |

### C10/N 验收前置（随本体落地的测试保护义务）

- **N2**（2.1 C10 :326/:419）：`TestDefaultVirtualFileSystemRefresh#testRefreshClosesOldZipOnlyAfterRebuild`（live :41-68）断言「refresh 返回后旧 zip 立即 closed」，与 C1-014 退役代际修复（延迟关闭）冲突——**同变更集改写**，改写后断言语义由裁定⑤确定化（保留「重建期间不得提前关闭」断言，改写「立即关闭」断言为新关闭时机契约的可观测等价断言）。
- **C10-001 + N5**（2.1 C10 :78/:329）：`TestRoundRobinSupplier` 现有 2 用例为单线程/已修面钉——`testResizeShrinkAndExpand` 在 C1-001 缺陷存在下照常通过（虚假覆盖感）；本批以真实并发 resize×get 断言替代/扩展（N5 误导覆盖随批改写）。
- **C10-002**（2.1 C10，ResourceCacheEntry 族死锁面）：C2-001/C2-017/C2-002 零并发保护——本批死锁复现测试（见 Phase 2 死锁测试纪律）即其落地形态。
- **C10-026**（2.5 C10 :536-546）：连接池缩容不排空假保护——Phase 3 新增触发 resize/refreshConfig 缩容的用例（先红后绿）。

### Baseline 勘误（输入不一致登记）

1. **任务口径「C2-002（ProducedBeanInstance 跨实例 monitor 等待环）」与本案成员不符**：ProducedBeanInstance 跨实例 monitor 等待环 = **2.2-C2-002**（nop-core-framework，cra-index L200「ProducedBeanInstance 跨实例 monitor 等待环 wait 无超时」，批次 **R2-04**/roadmap 8.13）；本案 2.1-C2-002 = ResourceCacheEntry 快路径无锁读 × clear() 锁内销毁 use-after-destroy 窗口（审计 `C2-lock-sync.md:115`，family-clustering 归 RCE 死锁/失效链族）。任务口径中「C2-002 锁序方案」随之订正为「失效/销毁时序方案」。本 plan 以审计本体为准；防混注记落 Non-Goals 与 Phase 5 台账（两行互注）。
2. **退役代际族非全 P1**：族主 C1-001 为 P1，成员 C1-003/C1-014 均 P2（cra-index P1 台账无独立行，由 G24 行 L402 承载）；本批合计 P1×2 + P2×5，与 batch-proposal 一致。
3. **行号漂移登记（成因各注）**：`DefaultScheduledExecutor` 审计载 destroy :88-93/六入口 :107-141/stats :84-86 → live destroy :115-126（置空 :118）/六入口 :152-214/stats :111-113（R1-01 绝缘层 `periodicTasks` 登记表+trackPeriodicTask 与 R1-02 refreshConfig 类型化路径插入致 +27 行量级漂移）；`ResourceCacheEntry` 审计载 clear L111-124/getObject L176-225/reloadObject L227-240 → live clear :135-148/getObject :255-287/reloadObject :348-361（plan 2500 R1-05 门语义 `shouldReloadObject` :215-253 与 `executeLoadWithMtimeGuard` :313-326 插入致漂移；C2-001 审计 F3 内 L90-92/L111-124 同步对应 :114-116/:135-148）；`DefaultVirtualFileSystem` refresh live :70-77/destroy :87-94（审计初载 :81-87/:89-96 已由维度复核修正，live 回读一致）；`ResourceComponentManager` ModelLoader 审计载 :99-129 → live :99-128、makeModelCache L433-440 → live :431-441、ComponentCacheEntry L418-428 → live :418-428（微漂）；`ResourceLoadingCache` remove 审计载 L163-169 → live :163-167、state_loadFrom L240-241 → live :240-241（微漂）；其余站点（RoundRobinSupplier :41-45/:47-87/:89-93/:96-103、XplLibTagCompiler :108/:185/:210/:226/:232/:651-656/:734-770/:800、LazyCompiledExecutableFunction :80、LettuceRedisConnectionProvider :61-89/:92-100/:103-146、ZipEntryResource :98-100、DevToolBizModel :46、GlobalFunctions :438-441）live 回读零漂移。本 Baseline 已按 live 回读值书写；执行时再以 live 为准。
4. **2.5-C6-005 与 2.1-C6-005 同号异站防混**：本批成员 = **2.5**-C6-005（nop-persistence-peri 单元，Lettuce 共享连接缩容热关取用窗，使用面 P2）；**2.1**-C6-005（nop-kernel 单元，平台模型缓存销毁链三断点）归 **R4-14**（G27 后半，roadmap 8.54），不在本批。台账引用一律带单元前缀。
5. **R1-02 边界**：R1-02（plan 2497）已修设施面 = TPE 池热更路径（`ExecutorHelper.updateThreadPool` 收缩重排、`DefaultThreadPoolExecutor.refreshConfig` 钳制 WARN+类型化、`DefaultScheduledExecutor.refreshConfig` scheduled 专用路径）；**RoundRobinSupplier.resize 不在 R1-02 修复面**——live @ `124e451af5` 核对其收缩锁外 closeNext 维持原样（:52-86），即 2.5-C6-005 复核状态「缩容面未修」的残余面在当前基线仍成立；本批设施修复（裁定①）即该残余面的主责闭环。
6. **G24 对账**：G24 七成员（C1-003/C1-014/C2-002/C2-017/C3-015/C9-001/C9-010）中 C3-015 随 R1-01（plan 2496）、C9-001/C9-010 随 R1-05（plan 2500）已落；cra-index L372 注记「G24 组行仅收敛上述三成员面，不整组置 fixed」——本批四成员收敛是该注记的解除条件（Phase 5 执行）。
7. **cra-index L372 G24 收敛注括号成员与 G24 实际成员不符（台账历史漂移）**：cra-index L372（plan 2500 R1-05 批次收敛注）括号「（其余成员 5.2-I-04 归 R3-02、4.4-C4-016 归 R2-14 等维持原批次）」与 G24 实际成员（L402：2.1-C1-003、2.1-C1-014、2.1-C2-002、2.1-C2-017、2.1-C3-015、2.1-C9-001、2.1-C9-010）不符——系台账历史漂移，非 G24 成员面变化；Phase 5 回填时顺带订正该括号（改为与 L402 实际成员一致的表述）。

## Goals

- 7 项成员全部修复并各带 ≥1 条 focused 回归测试（rule 25，测试名可辨识工单，如 `*_C1_001`/`*_C2_017` 后缀）；可复现项先红后绿或变异/回退论证（E2）。**死锁/时序类测试纪律（C2-001/C2-002/C2-017）**：测试须含真实死锁/use-after-destroy 复现（双线程反序 + latch 对齐 + 有界观察窗 + BLOCKED 态/销毁时序断言）或等效确定性断言（latch 强制交错的窄窗断言、单线程环类型化收敛断言），**禁仅类型/修饰符存在断言**（如「synchronized 已移除」不构成通过形态）。
- 退役代际族三站（RoundRobinSupplier 收缩窗 / DefaultScheduledExecutor destroy 置空窗 / DefaultVirtualFileSystem zip 句柄在飞读者窗）以同一平台机制收敛（裁定①），机制契约成文并可在三站点行为断言中交叉验证。
- RCE 死锁族以统一不变式「监视器内只做同 key 去重+占位，加载/编译在锁外」贯穿三层监视器链：C2-001（entry 层）与 C2-017（LazyFn 层）**同一变更集**落地（家族裁定硬约束），C2-002 销毁时序机制（裁定③）与退役代际机制复用设计。
- 2.5-C6-005 双轨联动闭合：设施面关闭时机契约（裁定①/④）+ Lettuce 使用面接入 + C10-026 缩容触发回归先红后绿；2.5-C6-005 台账行双轨注记收敛。
- N2 修复阻断预警钉同批解除（C1-014 修复同变更集改写，改写后断言在新契约下可红可绿）；C10-001+N5、C10-002 随本体收敛。
- Owner doc 随批更新：`docs-for-ai/04-reference/source-anchors.md`（RoundRobinSupplier/VFS/模型加载锁纪律锚点）+ `docs-for-ai/02-core-guides/ioc-and-config.md`（模型加载与配置刷新链并发契约）；2.2-C1-007（R2-04）上游联动注记落 cra-index 双行；roadmap 8.10 → done；G24 行整组置 fixed。
- E1–E6 通用门禁全过（batch-proposal §一）：全量构建绿 / 每工单强制并发回归测试（先红后绿或变异/回退论证）/ 批行验证命令留证 / 零触碰声明 / owner doc 清单 / 批级独立 closure audit。

## Non-Goals

- **不承接 2.2-C2-002**（ProducedBeanInstance 跨实例 monitor 等待环——R2-04/roadmap 8.13；勘误 1 防混，本批 C2-002 修复不得越界改 nop-ioc）。
- **不承接 2.1-C6-005**（平台模型缓存销毁链三断点——R4-14/G27 后半；勘误 4 防混：与本批 2.5-C6-005 同号异站，本批不改 `LocalCache.destroy`/`destroyOnRemove` 链）。
- **不承接 2.1-C1-015**（GlobalExecutors.register 锁外 destroy 旧实例——P3 潜伏，唯一生产调用方一次性引导；归 Deferred 登记；C1-003 修复后其 NPE 放大器面自然收敛，不改 `GlobalExecutors` register/unregister 语义）。
- **不承接 R4-13/R4-14 成员**（2.1 锁纪律/调度器/销毁链束：C2-003/C2-019/C3 族/C4 族/C5-003/C6-001 等——G27 两束依赖本批但不属本批；`ResourceComponentManager.loadComponentModel(transform)` CHM bin 锁重计算[C2-003]随其批次）。
- **不回退前批产物**：R1-01 绝缘层（periodicTasks 拒绝终态/重臂/失败可见）、R1-02 池设施（refreshConfig 类型化/收缩重排/池工厂 fail-fast/cached 上界熔断）、R1-05 缓存失效链（entry 内建门/双 mtime 守卫/判据统一）语义零回退；共享代码位改动以 Phase 1 裁定⑥声明边界。
- 不做批外退役代际相邻站点（I18nMessageManager 等同族加载面随 C2-001 修复自动覆盖的不另行立项；非本批 F3 清单站点登记 cra-index 不改码）。
- 不触碰 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物（零触碰声明见 plan 头部）。
- 不做 Deferred 节所列项（GlobalExecutors 延迟销毁改造、AutoCloseZipEntryResource 全量迁移评估支路外的扩展、死锁可观测性平台建设等）。

## Scope

### In Scope

- `nop-kernel/nop-commons`：RoundRobinSupplier（C1-001）、DefaultScheduledExecutor（C1-003）+ 两类单元测试目录。
- `nop-kernel/nop-core`：DefaultVirtualFileSystem（C1-014 + N2 同 diff 改写）、ResourceCacheEntry/ResourceLoadingCache（C2-001 entry 层 + C2-002）、ResourceComponentManager（C2-001 递归面环检测）+ 测试目录。
- `nop-kernel/nop-xlang`：XplLibTagCompiler（C2-017 LazyFn 层 + parseTag/getFunctionModel 调用点 + buildCompiledTag 随案）、LazyCompiledExecutableFunction（首触链按裁定②）+ 测试目录。
- `nop-persistence/nop-nosql/nop-nosql-lettuce`：LettuceRedisConnectionProvider（2.5-C6-005 使用面接入 + javadoc 契约）+ 测试目录（C10-026 义务）。
- 设计裁定文档：`ai-dev/design/concurrency/` 新建一篇（裁定①–⑥，plan-first 前置物）。
- Owner doc：`docs-for-ai/04-reference/source-anchors.md`、`docs-for-ai/02-core-guides/ioc-and-config.md` 核对与更新；cra-index 台账回填（P1 两行 + G24 四成员 + G48 C6-005 成员面 + N2/C10 注记 + 2.2-C1-007 联动注记 + 勘误 1/4 防混注记）；roadmap 8.10 → done。

### Out Of Scope

- Deferred 节所列项；R2-04、R4-13/R4-14、R4-32 等后续批次工单；2.2-C2-002 / 2.1-C6-005 / 2.1-C1-015 防混与移交面（见 Non-Goals）；批外退役代际相邻站点改码。

## Execution Plan

> 五个 Phase 顺序执行：设计裁定（Phase 1，plan-first 硬前置 + 独立评审）→ nop-kernel 实现+单测（Phase 2，六成员两族）→ nop-persistence 使用面（Phase 3，2.5-C6-005）→ 全量验证 + owner doc（Phase 4）→ closure audit + 收口（Phase 5）。Phase 2 内部两族可分会话续作（每工单（或按 Phase 2 提交纪律的族/同变更集边界）完成即提交 + 即时勾选，交接以 plan 勾选状态为进度事实源）；Phase 2 提交粒度收敛为共 3 提交——退役代际族（C1-001/C1-003/C1-014+N2）一提交 + C2-001+C2-017 单提交 + C2-002 独立提交；Phase 3 依赖 Phase 1 裁定④与设施面机制（可与 Phase 2 并行开工，收口依赖设施面落地）。

### Phase 1 - 设计裁定文档（plan-first 前置）+ 独立 draft review

Status: completed
Targets: `ai-dev/design/concurrency/kernel-retirement-generation-and-model-load-locking.md`（新建，退役代际与模型加载锁纪律设计——按 design 写作指南终态形态，只写选型/理由/拒绝的替代方案）

- Item Types: `Decision`

- [x] **裁定① 退役代际平台机制单点方案**（C1-001/C1-003/C1-014 同一机制族）：「发布新代 + 延迟退役关闭」统一形态（代际引用计数 / 宽限窗延迟关闭 / 在飞读者登记，三选一或组合，含拒绝理由）；RoundRobinSupplier get 热路径锁形态与性能取向（锁内读取 vs 无锁快照+退役，redis 每请求热路径代价声明）；C1-003 形态定稿（六入口局部快照 vs destroy 不置 null——与 `isDestroyed()` 语义、R1-01 `periodicTasks` 拒绝终态、R1-02 refreshConfig 类型化路径的交互逐项声明）；close() 全关闭与并发 get 同族面（C1-001 F3③）覆盖方式
- [x] **裁定② C2-001/C2-017 同变更集方案**（三层监视器链统一不变式：监视器内只做同 key 去重+占位，加载/编译在锁外）：entry 层 getObject/reloadObject per-key 占位形态（占位与 R1-05 门/记忆化状态共存语义）；循环依赖检测与类型化收敛（单线程环 SOE 面、双线程环、失败占位清理——不得复刻 C2-015 式条目驻留）；LazyFn 层 compile per-key 单飞；parseTag/getFunctionModel 惰性编译调用点移出 entry 监视器的具体边界；buildCompiledTag 随案子面覆盖声明
- [x] **裁定③ C2-002 失效/销毁时序方案**：标记失效+延迟销毁（与裁定①机制同构复用）或读者登记，二选一定稿并给理由；**终态不得为 javadoc-only/warn-only**（rule 16——已确认 live defect 不得以声明替代修复）；覆盖面含 clear 与 reloadObject→destroyObject 两销毁点（F3 #1/#2）及 ResourceLoadingCache.remove/state_loadFrom 两失效入口
- [x] **裁定④ 2.5-C6-005 双轨联动方案**（§四#6）：设施面对「已交付在用共享连接」的关闭时机契约（延迟关闭宽限窗/排空——共享 RoundRobin 无借还制下的形态与参数）；Lettuce 使用面接入方式（refreshConfig 缩容行为 + `getConnection` javadoc 共享连接不得 close 契约）；双轨验收口径（设施行为钉 + 使用面行为钉各一）；**测试缝子句**：live `LettuceRedisConnectionProvider.connectionSupplier` 为 private、仅 doStart 内硬构造、无 setter/工厂缝，refreshConfig 在 connectionSupplier==null 时跳过，测试类与 provider 异包（测试包 `io.nop.nosql.lettuce` vs provider 包 `io.nop.nosql.lettuce.impl`）——现有测试无法注入 fake supplier；随修复抽取 `protected` 连接供应商工厂方法（如 `createConnectionSupplier(factory, n)`），并显式声明为 C6-005 修复面一部分；Phase 3 测试项引用该缝（dockerless 下经真实 refreshConfig→resize 链跑通 C10-026 先红/绿）
- [x] **裁定⑤ N2 钉改写契约**：退役代际关闭时机的可观测测试缝定义——改写后 `TestDefaultVirtualFileSystemRefresh` 断言语义确定化（保留「重建期间不得提前关闭」；「立即关闭」断言改为新契约的确定性等价断言），消除虚假覆盖感
- [x] **裁定⑥ 共享代码位边界声明**：R1-01 绝缘层（periodicTasks/重臂/拒绝终态）、R1-02 池设施（refreshConfig 类型化/收缩重排）、R1-05 缓存门（shouldReloadObject/双 mtime/记忆化）零回退清单 + 本批改动与其交界的回归门禁清单（`TestPeriodicTaskInsulation` 9 条、C10-010 成员面、CACHE-001 契约测试面）
- [x] **独立 draft review**（fresh-session 子代理，想象性分析必做——「每个 Phase 我真的知道改哪个文件/怎么写吗」+ 设计到代码断层清单）：0 Blocker 方可转 active 进入 Phase 2；评审记录与 task id 登记本 plan
- [x] 设计文档与 plan 状态同步：设计文档提交；本 plan Plan Status 视评审结论 draft → active
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定①–⑥ 全部落定，每块含选型、理由、拒绝的替代方案及原因（rule 14 终态形态，无 Proposed vs Current 演进叙事）
- [x] 死锁复现测试的修复后行为定义可追：C2-001 双线程环/单线程环、C2-017 环④、C2-002 use-after-destroy 在修复后的可观测预期（有界完成/类型化错误/延迟销毁时机）在裁定①②③中成文
- [x] 独立 draft review 通过（0 Blocker；Major/Minor 处置记录 + reviewer task id 登记在案）
- [x] Owner doc 去向预登记：source-anchors.md / ioc-and-config.md 更新点清单（落 Phase 4），或显式声明 `No owner-doc update required` + 理由
- [x] `ai-dev/logs/` 对应日期条目已更新



> **Phase 1 回填注（2026-10-26）**：设计文档落盘（提交 b208c2aa83）+ 独立评审 agent_47d95478 **通过进入实现**（0 Blocker/0 Major/5 Minor：m-1 口径精确化与 m-2 委托族零 diff 登记已落盘于设计文档，m-3/m-4/m-5 为 Phase 2 实现注记）；评审期间发生一次脚本截断事故（提交 2e8fd1d7ab 误删 65 行），已从 b208c2aa83 恢复重建并重新正确应用（提交 0b6c9c6a6d，doc-links EXIT=0）。### Phase 2 - nop-kernel 实现 + 单测（退役代际族 C1-001/C1-003/C1-014 + RCE 族 C2-001/C2-002/C2-017）

Status: planned
Targets: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/RoundRobinSupplier.java`、`.../concurrent/executor/DefaultScheduledExecutor.java`、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DefaultVirtualFileSystem.java`、`nop-kernel/nop-core/src/test/java/io/nop/core/resource/store/TestDefaultVirtualFileSystemRefresh.java`（N2 同 diff 改写）、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceCacheEntry.java`、`.../cache/ResourceLoadingCache.java`、`.../component/ResourceComponentManager.java`、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/xlib/XplLibTagCompiler.java`、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/LazyCompiledExecutableFunction.java`、三模块测试目录

- Item Types: `Fix`（六项均为已确认 live defect，不得写成 Feature）、`Proof`（回归测试）

- [ ] **C1-001**：resize 收缩/关闭与 get 快照窗消除（按裁定①形态落地）；close() 全关闭同族面同 diff；`TestRoundRobinSupplier` 扩展真实并发 resize×get 断言（N5 误导覆盖改写——单线程名不副实用例以并发断言替代或补强）
- [ ] **C1-003**：六入口（submit×2/schedule/scheduleAtFixedRate×2/scheduleWithFixedDelay/execute）+ stats() check-then-use 原子化（按裁定①形态）；destroy 终态与绝缘拒绝链共存（R1-01 零回退）；参数化回归覆盖七读点
- [ ] **C1-014**：refresh/destroy 旧代 zip 关闭时机按裁定①/⑤（延迟退役）；**N2 钉同变更集改写**（同提交）；缓解支路（AutoCloseZipEntryResource）若为裁定①所选则随条落地；在飞读者回归（refresh×并发 getResource）
- [ ] **C2-001 + C2-017（同一变更集，单提交，commit message 声明绑定）**：entry 层锁外加载+占位+环检测类型化（ResourceCacheEntry/ResourceLoadingCache/ResourceComponentManager）；LazyFn 层 compile per-key 单飞 + parseTag/getFunctionModel 调用点按裁定②（XplLibTagCompiler/LazyCompiledExecutableFunction）；buildCompiledTag 随案；循环依赖以类型化错误收敛（不再 SOE/死锁）
- [ ] **C2-002**：clear 与 reloadObject→destroyObject 两销毁点按裁定③（延迟销毁或读者登记）；ResourceLoadingCache.remove/state_loadFrom 失效入口行为一致；与 R1-05 门语义共存零回退
- [ ] **死锁/时序类测试纪律（硬门禁）**：①C2-001——双线程反序真实死锁复现（循环 import 模型夹具[x:config c:import 环或 xlib import 环，取审计实证路径] + latch 对齐 + 有界观察窗 + 双线程 BLOCKED 态断言=修复前红；修复后绿=有界完成或类型化环错误、无 BLOCKED 残留）+ 单线程环类型化收敛断言（修复前 SOE 红）；②C2-017——环④双线程反序复现，或 min-priority 等效确定性断言（宏标签编译期用户代码执行时对对应 LazyFn/entry 监视器 tryLock 成功=未持有；编译体移出监视器的运行时可观测断言），禁仅「synchronized 已移除」型存在断言；③C2-002——use-after-destroy 真实复现（读者持引用窗口内触发 clear，修复前红=销毁已发生且读者仍持引用；修复后绿=按裁定③语义销毁延迟至读者窗口外，销毁时机可观测）；④线程卫生：先红复现用 daemon 夹具线程 + 有界观察窗（超时即判红并放行 JVM 退出），或声明专用测试类 fork 策略
- [ ] 其余成员 E2：C1-001 并发 close 交割断言先红（快照窗内 close 先于 get 交付）/修复后绿；C1-003 并发 destroy×提交断言裸 NPE 形态先红/修复后类型化或成功绿；C1-014 在飞读者 zip file closed 先红/修复后读取完成绿；变异/回退论证为 E2 允许支路
- [ ] **接线验证**（rule 23）：真实加载链上断言——C2-001/C2-017 经真实 ResourceComponentManager/XplCompiler 加载夹具模型（非 mock loader 直调）；C1-001/C6-005 联动经真实 RoundRobinSupplier（fake 资源）+ Lettuce refreshConfig 链（Phase 3）；C1-014 经真实 DefaultVirtualFileSystem.refresh + ZipEntryResource 读取
- [ ] **无静默跳过**（rule 24）：占位失败/环检测/延迟关闭超时路径显式类型化报错或日志可见，不吞异常不留悬挂占位
- [ ] **R1-01/R1-02/R1-05 零回退门禁**：`TestPeriodicTaskInsulation` 9 条 + 下游绝缘回归、C10-010 成员面（池工厂校验/热更双向）、CACHE-001 契约测试面全绿
- [ ] **提交纪律**：Phase 2 共 3 提交——退役代际族（C1-001/C1-003/C1-014+N2）一提交 + C2-001+C2-017 单提交（声明绑定）+ C2-002 独立提交，按族/同变更集边界完成即提交；每提交 commit message 带工单 ID 与 plan 号；C1-014 修复与 N2 改写同提交（E2 同 diff 核验依据）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 六成员全部 landed：C1-001（收缩窗消除+close 同族面）、C1-003（七读点原子化）、C1-014（延迟关闭+N2 同变更集解除）、C2-001+C2-017（三层链同变更集，循环依赖类型化收敛）、C2-002（两销毁点时序收敛），各带 ≥1 条 focused 回归测试且测试名可辨识工单
- [ ] 必红证据（E2）：死锁/时序类三件（C2-001 双线程环 BLOCKED、C2-017 环④或等效确定性断言、C2-002 销毁时序）+ C1-001/C1-003/C1-014 线程交割断言，先红后绿或变异/回退论证留痕（快照 `_tmp/phase2-*.log` 各含 RED 与 GREEN 两段）
- [ ] 接线验证证据登记（哪条真实链断言证明运行时生效）
- [ ] N2 钉解除核验：改写后测试在修复前基线可红、修复后绿（虚假覆盖感消除的实证）
- [ ] `./mvnw test -pl nop-kernel -am` BUILD SUCCESS（含 nop-commons/nop-core/nop-xlang 全量与绝缘/池/缓存门零回退面）
- [ ] Owner doc 裁定：source-anchors.md / ioc-and-config.md 受影响面登记更新去向（归 Phase 4 统一落账但本 Phase 显式登记）；或 `No owner-doc update required` + 理由
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - nop-persistence 2.5-C6-005 Lettuce 使用面双轨接入

Status: planned
Targets: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java`（:92-100 refreshConfig + :82-89 getConnection javadoc）、`nop-persistence/nop-nosql/nop-nosql-lettuce/src/test/java/`（C10-026 义务）

- Item Types: `Fix`（2.5-C6-005 已确认 live defect）、`Proof`（回归测试）

- [ ] refreshConfig 缩容行为按裁定④接入设施退役机制：已交付在用共享连接不被热关（延迟关闭宽限窗/排空语义），取用窗口瞬时命令失败面消除
- [ ] `getConnection` javadoc 共享连接契约（调用方不得 close）+ `getAsyncCommands`/`getSyncCommands` 取用侧语义核对（F3 ③四入口一致）
- [ ] **C10-026 义务**：新增触发 resize/refreshConfig 缩容的回归测试（现有 testClientResourcesNotLeakedAcrossStartStop 不触发 resize、缩容修复不触红——新用例必须先红）：经裁定④测试缝（`protected` 连接供应商工厂方法，dockerless）走真实 refreshConfig→resize 链；在用连接被 close 断言（修复前红）→ 延迟关闭语义绿（销毁时机可观测）
- [ ] **双轨联动验证**（rule 23）：真实 LettuceRedisConnectionProvider（经裁定④测试缝注入 fake connection factory，dockerless）refreshConfig 链 + 设施面 RoundRobinSupplier 行为钉交叉验证（get 交付连接在收缩窗口内存活断言）
- [ ] **提交纪律**：nop-nosql-lettuce 一提交（或与设施提交关系声明），commit message 带 `2.5-C6-005` 与 plan 号
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 2.5-C6-005 landed：refreshConfig 缩容不再热关在用共享连接，带 ≥1 条 focused 回归测试且测试名可辨识工单；C10-026 义务落地（缩容触发用例先红后绿）
- [ ] 必红证据（E2）：缩容 close 窗断言先红后绿或变异/回退论证留痕（快照 `_tmp/phase3-c6-005-*.log`）
- [ ] 接线验证证据登记（真实 refreshConfig 链 + 设施机制行为钉）
- [ ] `./mvnw test -pl nop-persistence -am` BUILD SUCCESS（或子模块通道 `-pl nop-persistence/nop-nosql/nop-nosql-lettuce -am` 并登记理由）
- [ ] Owner doc 裁定：2.5-C6-005 双轨注记文本供 Phase 5 台账回填；nop-persistence 无 03-modules owner doc 缺口声明归 R-G 轨（Phase 4 登记）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 全量验证 + owner doc 更新

Status: planned
Targets: 受影响聚合模块全量测试；`docs-for-ai/04-reference/source-anchors.md`、`docs-for-ai/02-core-guides/ioc-and-config.md`（roadmap 8.10 Owner Doc 列两份）；触达模块 owner doc 归属核对

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路：`-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明）
- [ ] 批行验证命令（E3）逐条登记：`./mvnw test -pl nop-kernel -am`、`./mvnw test -pl nop-persistence -am`——每条登记模块/测试数/退出码/快照路径
- [ ] 零触碰确认（E4）：本批产品 diff 不含 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物路径（`git diff --name-only` 核对登记；codegen 再生产物若有按豁免路径登记）；R1-01/R1-02/R1-05 产物文件（InsulatedPeriodicTask/InsulatedPeriodicChain/PeriodicTaskHelper/ExecutorHelper/GlobalExecutors/CommonConfigs、DefaultResourceChangeChecker 等）diff 零回退核对
- [ ] Owner doc 更新（E5）：①source-anchors.md——退役代际机制/模型加载锁纪律/VFS 刷新契约锚点新增或既有行（VFS-003/CACHE-001）受影响面更新；②ioc-and-config.md——模型加载与配置刷新链并发契约节（refreshConfig→resize 链、循环依赖类型化语义、共享连接池缩容契约）；逐份核对与更新或 `No owner-doc update required` + 理由
- [ ] 缺口声明：实际 diff 触达模块无 `docs-for-ai/03-modules/` 条目者（nop-nosql-lettuce 等，以实际 diff 为准）按 R-G 治理轨先例登记（执行记录 + daily log），不在本批新建 owner doc
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module <受影响模块> --severity high`（与 Closure Gates 受影响模块一致：nop-kernel、nop-persistence）退出码 0；若有批前既有发现，逐条裁定登记
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] E1 + 批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] 两份 Owner Doc 核对与更新结论逐份登记（含 `No owner-doc update required` 声明者）；缺口声明登记；check-doc-links --strict 退出码 0
- [ ] 零触碰确认（E4）+ 前批产物零回退实证登记
- [ ] scan-hollow 受影响模块逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.10 行、cra-index 台账

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（调用链运行时连通、无空方法体/静默吞异常/占位悬挂）；重点抽查：三层监视器链不变式运行时真实生效（非仅类型系统）、退役代际三站行为交叉一致、死锁复现测试真实先红证据
- [ ] cra-index 台账回填：P1 两行（C1-001 L190 / C2-001 L191）状态收敛（open → fixed/本批）；G24 行（L402）四成员收敛注记 + 整组置 fixed（勘误 6 解除条件达成）；G48 行（L426）C6-005 成员面收敛注记（双轨注记 §四#6 对账，2.5-C1-010 维持 R2-04）；N2 钉解除注记 + C10-001/C10-002/C10-026 收敛注记；**2.2-C1-007（R2-04）上游联动注记**（cra-index L195 行与本批行互注：下游取用窗已闭、上游发布协议归 R2-04，防两批各修一半或重复立项）；勘误 1/4 防混注记（2.2-C2-002、2.1-C6-005 各归其批）；勘误 7 顺带订正（L372 G24 收敛注括号成员改为与 L402 实际成员一致）
- [ ] roadmap 8.10 行 Status → done
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2504-cra-phase8.10-r2-01-kernel.md --strict` 退出码 0；scan-hollow 全绿复核
- [ ] Closure Evidence 写入本 plan `## Closure` 段（rule 27）
- [ ] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 7 成员行全部收敛（P1 两行 + G24 四成员 + G48 C6-005 成员面）+ N2/联动/防混注记可核验 + roadmap 8.10 = done
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 7 项成员全部修复收敛：2.1-C1-001（退役代际机制+close 同族面）、2.1-C1-003（七读点原子化）、2.1-C1-014（延迟关闭+N2 同变更集解除）、2.1-C2-001+C2-017（三层链同变更集+循环依赖类型化收敛）、2.1-C2-002（销毁时序机制）、2.5-C6-005（双轨联动+使用面接入）
- [ ] 死锁/时序类测试纪律达成：C2-001/C2-002/C2-017 各含真实死锁/use-after-destroy 复现或等效确定性断言（先红证据在案），无仅类型存在断言
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25，测试名可辨识工单）；可复现项有先红后绿或变异/回退论证（E2）
- [ ] C10/N 前置全部落地：N2 同变更集解除且改写后可红可绿、C10-001+N5 并发断言替代、C10-002 死锁复现、C10-026 缩容触发用例
- [ ] 前批产物零回退实证：R1-01 绝缘层（`TestPeriodicTaskInsulation` 9 条+下游回归）、R1-02 池设施（C10-010 成员面+类型化语义）、R1-05 缓存门（CACHE-001 契约面）diff 与测试双核对
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令（`-pl nop-kernel -am`、`-pl nop-persistence -am`）BUILD SUCCESS（E1/E3）
- [ ] Owner doc 更新完成：source-anchors.md / ioc-and-config.md 逐份核对与更新（或 `No owner-doc update required` 声明）；缺口声明登记（E5）
- [ ] 零触碰确认（E4）：产品 diff 不含 nop-api-core/`model/*.orm.xml`/nop-auth/`_` 前缀生成物
- [ ] 受影响模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外/P3/相邻面，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）三层监视器链不变式与退役代际机制运行时真实生效（真实加载链/refreshConfig 链断言，不只类型系统），（b）无空方法体/静默跳过/no-op/悬挂占位作为正常实现
- [ ] `./mvnw compile`（受影响模块）
- [ ] `./mvnw test`（受影响模块）
- [ ] checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2504-cra-phase8.10-r2-01-kernel.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 7 成员行收敛 + G24 整组 fixed + roadmap 8.10 → done（E6）

## Deferred But Adjudicated

### 2.1-C1-015 GlobalExecutors.register 锁外 destroy 旧实例（P3 潜伏）

- Classification: `watch-only residual`
- Why Not Blocking Closure: P3 判级本体（当前唯一生产调用方 `nop-utils/nop-commons-java21/src/main/java/io/nop/commons/jdk21/Jdk21CommonsCoreInitializer.java`——不在 nop-kernel 内，grep nop-kernel 会落空——一次性引导，运行期重注册触发链不存在）；本批 C1-003 修复（destroy 置空/快照形态）已收敛其 NPE 放大器面；batch-proposal R2-01 清单不含此条。
- Successor Required: `no`（如未来出现运行期重注册特性，随该特性批次按退役代际机制补延迟销毁；cra-index P3 归档承载）

### AutoCloseZipEntryResource 全量迁移（C1-014 建议②支路，若裁定①未取该支路时成立）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 裁定①若以退役代际机制闭合 C1-014，按路径重开的 zip 资源形态属同缺陷的替代实现而非新增能力；迁移评估仅在退役机制暴露维护负担时才有增量价值。若裁定①选取 AutoClose 支路，本条删除（无 deferred 对象）。
- Successor Required: `no`（VFS 后续治理批按需立项）

### 死锁/挂死对健康检测不可见的平台可观测性（F5 通用面：线程栈停于 synchronized 无日志）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本批以类型化收敛+有界占位消除三层链死锁成因，监测面为平台级横切能力（线程池挂死探测/monitor 报警），无既定批次承载；单独立项评估不阻塞本批缺陷闭环。
- Successor Required: `no`（经 backlog 立项评估）

## Non-Blocking Follow-ups

- **2.2-C2-002 / 2.1-C6-005 防混注记**：Phase 5 台账回填时在两行各注「与本批 2.1-C2-002 / 2.5-C6-005 同号异站，批次归属 R2-04 / R4-14」（勘误 1/4）。
- **R4-13/R4-14 前置解除注记**：两束 Deps 列硬依赖 8.2/8.3/8.10——本批收口后在 cra-index 对应族行注记前置达成（防重复核对）。
- **环④/环⑤运维侧声明对账**：若 Phase 1 裁定②的环检测同时覆盖 xlib import 解析层，则「循环 xlib import 应为配置错误报告」的解析层义务随本批闭合；若未覆盖，登记 owner doc 契约声明（ioc-and-config.md 更新点核对项）。
- **2.5-C6-005 双轨台账对账**：cra-index L426 G48 行注记中「C10-026←C6-005 验收前置」随 Phase 3 落地后对账勾稽。

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<如用子 agent，记录 session ID>>
- Evidence:
  - 每条 Exit Criterion 的验证结果（PASS/FAIL + 对应的 live code path 或 test name）
  - 每条 Closure Gate 的验证结果（PASS/FAIL + evidence 来源）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2504-cra-phase8.10-r2-01-kernel.md --strict` 退出码为 0（确认无未勾选项 + Closure Evidence 已写入）
  - Anti-Hollow 检查结果：<<三层监视器链不变式与退役代际机制运行时连通追踪结果>>；`scan-hollow-implementations.mjs` 退出码为 0
  - Deferred 项分类检查：<<确认无 in-scope live defect 被降级>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>
