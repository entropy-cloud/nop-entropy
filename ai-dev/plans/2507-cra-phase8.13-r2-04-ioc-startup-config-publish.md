# 2507 CRA Phase 8.13 — R2-04 2.2 IoC 启动图/死锁 + 配置发布协议批（R22 刷新链，7 项）

> Plan Status: active
> Draft Review: R1 agent_0c039821 @ 2026-10-26（0 Blocker、3 Major、7 Minor；基线 20 组 file:line 抽查 17 PASS/3 DRIFT[均为勘误初稿误记]）——M1 裁定① standalone 传导默认分支与 Scope 歧义（NopApplication 入口检查 vs nop-boot 未入 Targets）→ 定夺 NopApplication 零改动、传导=join 抛出经 initialize() 自然传播、宿主面归 R2-21(8.30)；M2 裁定② 补收集边触发 `ERR_IOC_BEAN_DEPEND_ON_HIGH_ORDER_BEAN` 构建期硬失败兼容面缺失 → 默认「补收集边仅入等待登记/环检出、不进拓扑序与 order 校验」+ 类型→beanId 解析缝落点 + 与审计自主组 4 tryLock 方案替代关系声明；M3 勘误 1/2 live 值误记（OrmMigrationWriteGate :41-43/:51-57/:62-65 全文 67 行；IocCoreInitializer :46 零漂移；flipShardConfig :415）订正；Minor×7（R2-21=8.30/R2-15=8.24 批号 4 处、C10-012 N7 幻影归属删除、allowLoop :256-261、测试树 17 类表述、TestMigrationPipelineE2E 归 nop-orm-migration、裁定③超时档位/配置键命名、E3 -pl nop-kernel 以测试数非零自证触发 api-core）——修订全量应用，转 active
> Last Reviewed: 2026-10-26
> Source: roadmap 工作项 8.13（R2-04，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L155，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-04 行（L78：finding ID 清单 7 项 / 模块与验证命令 nop-core-framework + nop-persistence / PA 授权 / 依赖标注「无前置（可并行）；R4-15 协同」/ exit criteria 增补五条）+ §一 通用门禁 E1–E6 + §四.3 授权落批表（框架核心引擎 plan-first → 含 R2-04）；成员发现详情 = WI 2.2 审计目录 `ai-dev/audits/2026-10/2026-10-17-1725-deep-audit-nop-core-framework-concurrency/`（C1-shared-mutable-state.md / C2-lock-sync.md / C4-thread-pools.md / C4-round2.md / merge-adjudication.md / C10-test-protection.md，逐条回读路径见 Current Baseline）+ WI 2.5 审计目录 `ai-dev/audits/2026-10/2026-10-19-0624-deep-audit-nop-persistence-peri-concurrency/`（C1-shared-mutable-state.md / C7-long-running.md / merge-adjudication.md / C10-test-protection.md）；执行基线 @ `89d325d923`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.12 已 done，plan 2495–2506 均 completed；本 Baseline 全部行号为该基线 live 回读值，2026-10-26 亲读，执行前如再漂移以 live 为准并登记）
> Related: plan 2506（R2-03，completed——其 Non-Goals 与设计文档 `ai-dev/design/concurrency/plugin-lifecycle-and-config-source-hardening.md` §3.7 对 R2-04 做了「`BeanContainerImpl`/`BeanScopeImpl` 零改动承诺」与「插件状态机恢复 API 不重构 IoC 容器启动图/stop 语义」边界；**该零改动承诺由本批解除**：BeanContainerImpl 启动图/getBean0 改造入本批 scope，`BeanScopeImpl.close` 末个 destroy 异常上抛（live :100-120）与 `BeanContainerImpl.stop` 转发（live :595-617）的上抛行为仍按既有消费，本批不反向要求其聚合语义改造，裁定⑥作边界解除声明）；plan 2499（R1-04，completed——**nop-api-core Protected Area plan-first 先例**：SPI 变更须列全部实现为回归面 + 零接口签名变更（source-compat）默认分支，本批裁定④沿用该纪律）；plan 2504（R2-01，completed——设计 `ai-dev/design/concurrency/kernel-retirement-generation-and-model-load-locking.md`：**LoadWaitGraph 等待图环检测 + 有界等待兜底**平台先例（载体 `ResourceCacheEntry.LoadWaitGraph`，锚点 `LOCK-001`），C2-001/C2-002 死锁修复的机制单点/域内聚选型输入）；plan 2496/2497（R1-01/R1-02，completed——JdbcConfigSource per-callback 隔离 + AbstractFileConfigSource/JdbcConfigSource 卸载与超时已落地，本批 C1-007 配置发布协议为其**上游发布协议**，刷新链已修面零回退义务见裁定④）；**下游/并行协同**：R4-15（roadmap 8.55，G31 束 17 项，**同 PR 批次协同后置**——C4-009/C4-011+CL-019 与本批 C4-003 同 PR、C1-008/C2-007/C2-008/C2-016/C1-020 为 C10-009 共享条目其余成员面，移交边界见裁定⑥）、R2-21（roadmap 8.30，4.4 host 批——双宿主启动窗口就绪门禁为 C4-003 宿主联动面）、R2-15（roadmap 8.24，2.5 peri 批——「写门持久化半边归 R2-04」注记的接收方为本批 C7-004）
> Protected Area 授权：本批触碰 **框架核心引擎内部（nop-ioc 容器启动图/锁纪律 + nop-config 写路径）= 框架核心引擎 plan-first Protected Area**。授权来源 = AGENTS.md「Protected Areas」表框架核心引擎行 + batch-proposal §四.3 授权落批表（「框架核心引擎（nop-kernel/nop-core-framework 内部）plan-first（设计文档+回归测试）→ R1-01、……、**R2-04**、……」行）+ 本 plan Dependencies 声明：**设计文档为本批 Phase 1 产物（`ai-dev/design/concurrency/`），回归测试为各 Phase 强制义务（rule 25）**。plan-first 前置在 Phase 1 内闭环：设计文档落地并经独立 draft review 后，Phase 2–4 方可推进。**另触 nop-api-core（io.nop.api.core.*）**：成员 2.2-C1-007 的修复载体（DefaultConfigReference 三字段 holder 化，审计终裁「api-core 单一不可变 holder+单 volatile 引用」）与全部 7 个裸写点中 3 个（`AbstractConfigProvider.updateConfigValue/assignConfigValue/reset`）位于 `nop-kernel/nop-api-core`——**按 AGENTS.md nop-api-core 行叠加 plan-first，SPI 变更须列全部实现为回归面**（2499 先例；SPI 回归面清单见 Current Baseline 裁定④输入），由同一份 Phase 1 设计文档一并覆盖，不重复立项。本批不触碰 `model/*.orm.xml`、不触碰 nop-auth、不做 `_` 前缀生成物改动。

## Purpose

关闭 R2-04 批的 7 项已确认发现（P1×5 主案 + P1 搭车 1 + P2 搭车 1）：①**IoC concurrent-start 三复合面**（2.2-C4-003，←C4-008 失败形态完备性并案；B1-01 终裁 P1 维持，P1 由无 join 点 + 失败不传导承载）——框架级 join + 启动失败传导 + 有界超时，消除「进程带死容器 run-success 存活/quarkus 三绿假活」；②**getBean0 锁序环**（2.2-C2-001，←C2-012 不可见边族并案）——构造器级 type-injection/factoryBean 依赖边纳入可见性治理 + 跨线程 BD monitor 互等死锁的类型化收敛；③**ProducedBeanInstance 等待环**（2.2-C2-002，←C1-002 跨维度并案）——wait() 无超时挂死的有界化与环检出；④**配置发布协议**（2.2-C1-007 族案含 C1-017）——api-core 三字段单一不可变 holder + 单 volatile 发布，闭合 7 写点×8 读站的 JMM 数据竞争与两步写序撕裂；⑤**刷新链下游 volatile 化**（2.5-C1-007）——shardSelector 热替换可见性与安全发布；⑥**写门集群面**（2.5-C7-004）——开门单 JVM 作用域的持久化/租约 fail-open 形态；⑦**写门可见性半边 + 保持机制**（2.5-C1-010，P2-G48a 搭车）。批末 roadmap 8.13 → done，7 项成员台账全部收敛。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-04 = 7 项成员（2.2 P1×4：C4-003/C2-001/C2-002/C1-007；2.5 P1×2：C1-007/C7-004；P2 搭车×1：2.5-C1-010[G48a]）；cra-index P1 台账 6 行（`nop-core-framework-C1-007` L195 / `-C2-001` L199 / `-C2-002` L200 / `-C4-003` L201 / `nop-persistence-peri-C1-007` L233 / `-C7-004` L237，裁定批次均=R2-04，全部 open）+ P2 裁决台账 G48 行 L432（2.5-C1-010 修→提前归 R2-04；同组 2.5-C6-005 已随 R2-01/plan 2504 收敛）；并案成员按 merge-adjudication 随主 ID 承载：C1-017→C1-007 族案（WI2.2 裁定 3 前置复核裁定）、C4-008→C4-003 族案（WI2.2 裁定 3）、C2-012→C2-001（WI2.2 裁定自主补充组，P2 并入、修复共用 collectDepends+tryLock+allow-cycle 裁定三件套）、C1-002→C2-002（跨维度并案，cra-index L200 行内注记）；roadmap 8.13 行 Status=todo。
- 前置：无前置（可并行）。R1-00～R1-08（plan 2495–2503）与 R2-01/R2-02/R2-03（plan 2504/2505/2506）均 completed @ `89d325d923`——本批启动的基线门禁已满足。
- **B1-01 终裁口径**（batch exit criteria 增补第 1 条）：C4-003 门控 P1 维持、不降 P2 条件化——P1 由「无 join 点 + 失败不传导」两子面承载（WI2.2 C4-thread-pools.md「C4-003 终裁：P1（门控）维持」节五点论证），子面 (a) 启动体占共享 globalWorker 若按默认态暴露单独计 P2 条件化；修复（join+失败传导+超时）无论判级均为必做。
- **前批同域产物（本批改动边界的前提事实）**：plan 2504 已在 nop-kernel 落地 LoadWaitGraph 等待图 + 有界等待兜底 + 三环错误码（锚点 `LOCK-001`：`ResourceCacheEntry` 装载状态机 + `nop.core.component.resource-cache.load-wait-timeout` 默认 60s、≤0 不设上界档）——本批 C2-001/C2-002 的环检测/有界等待选型不得绕开该机制单点评估重复造轮子；plan 2496/2497 已在 nop-config 落地 JdbcConfigSource per-callback 隔离 + 两配置源构造序「先加载后调度」/close 互斥/executeOn 卸载/JDBC 超时——本批 C1-007 holder 化改的是 usedRefs 常驻引用的字段发布协议，**不得回退**上述刷新链已修面（回归门禁见裁定④）；plan 2506 已在 nop-plugin 落地双轨生命周期锁契约——本批不改插件层。
- 测试资产与套件注记（C10 审计）：本批成员保护力现状 = C2-001「无保护」、C4-003/C2-002/C1-007（WI2.2）「部分」、2.5-C1-007/C7-004/C1-010「假保护」（单 JVM set-then-get/开关语义测试，竞态与集群面零覆盖）；验收前置 C10 条目 = **C10-002**（WI2.2，覆盖 C4-003/C4-008/C4-009/C4-011/C2-009——本批承接 C4-003/C4-008 成员面，C4-009/C4-011/C2-009 面归 R4-15，共享条目拆分见勘误 6）、**C10-003**（覆盖 C2-001/C2-002/C1-002/C2-017——本批承接 C2-001/C2-002/C1-002 面，C2-017 的 P3 javadoc/超时重载面见 Follow-ups）、**C10-009**（覆盖 C1-007/C1-017/C1-008/C2-007/C2-008/C2-016/C1-020——本批仅承接 C1-007/C1-017 成员面）、**C10-012**（WI2.5，覆盖 2.5-C1-007）、**C10-014**（WI2.5，覆盖 C1-010/C7-004 本单元双面——两面均为本批成员，整条入批）。测试套件自身注记 N3（多测试类 setup/teardown 经 `assignConfigValue/updateConfigValue` 写全局配置 = C1-007 族运行期第二写点，修复后**强制回归面**）随裁定④落地核验；N5（`TestBeanDepends#testAsyncStartOrder` 为全单元唯一 concurrent-start 用例且仅成功路径，javadoc 覆盖边界注记建议）随 C4-003 回归测试同步补注记。

### 7 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `89d325d923` 2026-10-26 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级） | 单元 / 模块 | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 2.2-C4-003（P1 门控，B1-01 终裁维持；←C4-008 三形态并案） | 2.2 / nop-core-framework（nop-ioc） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java`：`concurrentStart` 字段 :88 / setter :146-147、`start` :476-524（concurrentStart 分支 :502-512：`startFuture = asyncStartBeans(...).whenComplete(...)` :503-512，err 分支 `handleStartError(err)` :505、成功分支 `runLazyProperties(); runDelayMethod(); started = true` :507-509）、`handleStartError` :526-533（仅 `LOG.error` :527 + `stop()` :529）、`awaitStartFinished` :536-539（`FutureHelper.syncGet(startFuture)` :538，**FutureHelper（nop-api-core）无带超时 syncGet 重载**，`syncGet` :158）、`asyncStartBeans` :541-558（`new TaskExecutionGraph(GlobalExecutors.globalWorker(), "ioc-container-start")` :542、任务体 `getBean0` :548、`executeAsync` :557）、`startBean` :560-568、`stop` :595-617（cancel :599-602 + singletonScope.close :605-612 + 异常上抛 :615-616）、`checkStarted` :619-622；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/initialize/IocCoreInitializer.java`：`appContainer.start()` :47（启动序**无 awaitStartFinished 调用**，生产零调用方——全仓仅 `TestBeanDepends` :128）；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/IocConfigs.java`：`CFG_IOC_APP_BEANS_CONCURRENT_START` :28（默认 false，docs/performance/optimize-app.md:17 明文推荐开启 + @Description 误标三重文档债） | concurrent-start 模式下启动 bean 任务体占共享 globalWorker、框架级无 join 点、启动失败仅 log+stop 后进程以 exit-0「run-success」/quarkus 三绿假活姿态带死容器存活；←C4-008 三形态（F2 Error 逃逸 runTask catch(Exception) 使图 future 永久孤儿 running=true 半可用 limbo、F5 成功回调异常完全静默 started 永不翻转、F4 全链无超时永久 pending）为绕过 handleStartError 的失败形态完备性扩展 | **框架级 join + 失败传导 + 有界超时**（裁定①定形态）：IocCoreInitializer 启动序 join（生产调用 awaitStartFinished 或等价编排）、join 带可配置有界超时（沿 LOCK-001「默认有界 + ≤0 不设上界档」形态、新配置键落 IocConfigs）、启动失败终态可查询 + awaitStartFinished 对失败快速失败、handleStartError 汇聚点兜底闭合 C4-008 三形态（Error/回调异常/超时均收敛到失败终态而非 limbo）；宿主退出码/健康联动边界（standalone 传导默认分支 + quarkus/spring 宿主面）按裁定①⑥定夺；**设施面 TaskExecutionGraph.runTask catch(Exception)（CL-019）不动，归 R4-15 同 PR** |
| 2 | 2.2-C2-001（P1 终裁维持；←C2-012 不可见边族并案；历史 check 审计 P2-6 同源回访） | 2.2 / nop-core-framework（nop-ioc） | `BeanContainerImpl.getBean0` :373-434——`synchronized (beanDef)` :406、锁内 `beanDef.createInstance(...)` :419（构造器/工厂/supplier 用户代码执行于 BD monitor 内）、`isInCreationByCurrentThread` :394/:412（实例字段比对当前线程，仅同线程 fail-fast :411-415）；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/resolvers/InjectTypeValueResolver.java`：`resolveValue` :48-53（`getBeanByType(beanType, true, ...)` :53，**全文无 collectDepends**）；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java`：`newInstance` :526-540（factoryBean 边 `container.getBean(beanModel.getFactoryBean(), false, beanCtx)` :534，includeCreating=false）；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanTopologySorter.java`（:224 起 collectDepends 仅收集 ref 类 resolver；`allowLoop` 拒环 :256-261）；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/IocConfigs.java`：`nop.ioc.bean-depends-graph.allow-cycle` :64-66（**默认 true——即使补收集，默认配置下 sortBeans 拒环检查也不执行**） | 构造器级 type-injection/factoryBean 依赖边不入拓扑图：跨线程并发创建（concurrentStart worker 或运行期双请求线程懒加载首触）各持一 beanDef monitor 在对方入口互等，synchronized 无超时不响应 interrupt，单点故障扩散为该 bean 全部 getBean 永久排队 | 裁定②三选一（**默认 = 不可见边补收集 + 跨线程等待图环检测 + 有界等待兜底**组合，复用 2504 LoadWaitGraph 先例的「登记→回溯→类型化环错误→有界兜底」三件套形态；须绑定 allow-cycle 默认值一次性裁定——默认分支 = 维持 true、拒环依赖运行期环检出兜底，改 false 档为备选分支并显式列兼容面）；supplier/表达式内动态 getBean 不可静态收集面文档化声明（同审计失效面枚举） |
| 3 | 2.2-C2-002（P1 终裁维持；←C1-002 跨维度并案） | 2.2 / nop-core-framework（nop-ioc） | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/ProducedBeanInstance.java`：`runUntil` :114-170（owner/等待者协议：`initThread` :54 登记 :144、`initError` 广播 :151-158/:162-168、**`waitQuietly()` :132/:139 → `wait()` :196 无超时**）、`selfWait` :190-192（同线程 fail-fast，顺序启动测试必绿的原因）；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java`：`initBean` resolvedDepends 循环 :638-645（`container.getBean(depend, false, beanCtx)` :643，includeCreating=false 等待语义）；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java` :433 `getBeanInstance` 在 synchronized(beanDef) 块外调用（等待者不持 BD 锁——纯 PBI monitor 域） | init 阶段 owner 经 includeCreating=false 交叉获取下游 bean 时跨实例 PBI monitor 等待环闭合：双 owner 互等永久 wait、无挂死可见性、jstack 仅见 BLOCKED 链无框架级诊断；同线程 selfWait 掩护使单线程测试不可能暴露 | **有界等待 + 等待链环嗅探**（裁定③定形态）：waitQuietly 改有界 wait 循环 + 超时类型化错误（携带 beanId/initThread/createThread/等待链快照）；等待前环检出与裁定②共享等待登记机制（机制单点评估）；既有 `TestBeanConcurrentInitDeadlock`（@Timeout(30s) + join(15000)+isAlive 断言）机制级钉零回退 |
| 4 | 2.2-C1-007（P1 族案，含 C1-017 并入；WI 2.1 C1-001 刷新链上游同案注记） | 2.2 / nop-core-framework（nop-config）+ **nop-kernel（nop-api-core）** | 字段本体（nop-api-core）：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/config/DefaultConfigReference.java`——`provider` :15 非 volatile、`setProvider` :37-39、`getAssignedValue` :50-52（`provider.get()` :51 双裸字段兜底读）、**`updateValue` :54-57（`setLocation(loc)` :55 + `setProvider(provider)` :56 两步 plain write 撕裂窗）**；`AbstractConfigReference.java`——`location` :13 / `setLocation` :29-31、`defaultValue` :16 / `setDefaultValue` :42-44；`AbstractConfigProvider.java`——`updateConfigValue` :89-93（:92 调 updateValue）、`assignConfigValue` :82-87（:86）、`reset` :67-79（:76）、`getConfigValue` :97-113（:101 `ref.get()`）。写路径（nop-config）：`nop-core-framework/nop-config/src/main/java/io/nop/config/impl/DefaultConfigProvider.java`——`applyChangeToUsed` :297-315（**运行期刷新主写点** :305/:308，config-executor 单线程）、`updateRefs` :133-172（changeConfigSource 启动切换 :144/:147 + BaseTestCase 测试路径 :166）、`setConfigModel` :100-122（`ref.setDefaultValue` :117 裸写）、`changeConfigSource` :124-131（`configSource` :50/:125 plain 字段三连发布）、`applyChange` :255-295（configValues :261 / configRefs :290 双 volatile 表发布 + 逐键 usedRef 更新 :293）；`AppConfig.java`（nop-api-core）——hostId 运行期写 :53（updateConfigValue 通道） | 运行期配置刷新经 usedRefs 常驻 DefaultConfigReference 三字段裸写裸读，无任何 happens-before：刷新值对全平台任意请求线程（`AppConfig.varRef(...).get()` 热路径）可见性无界 + location/provider 两步写序撕裂；7 写点（W1 applyChangeToUsed、W2/W3 updateRefs 主+测试、W4 assignConfigValue、W5 updateConfigValue[OrmMigrationWriteGate.setClosed/flipShardConfig 等]、W6 reset、W7 setConfigModel）× 8 读站（getConfigValue/collectUsedRefs/collectUnusedValues/getAssignedValue/isDynamic/toString/traceConfigVars/wrapper 链 CastType·Placeholder·OverrideConfigReference）全经同一裸字段 | **api-core 单一不可变 holder + 单 volatile 引用**（审计终裁；裁定④定形态与回归面）：location+provider 收拢为不可变对象，updateValue 改单 volatile 引用一次发布（逐字段 volatile 不能闭合撕裂——终裁语）；defaultValue 写点（W7）随 holder 或启动期限定声明；**零接口签名变更为默认分支**（2499 先例 source-compat）；SPI 回归面 = `IConfigReference` 实现族（DefaultConfigReference/PlaceholderConfigReference/OverrideConfigReference/CastTypeConfigReference/AbstractConfigReference）+ `AbstractConfigProvider` 子类（SimpleConfigProvider nop-api-core / DefaultConfigProvider nop-config，live 全仓 grep 仅此两子类）+ AppConfig 消费面；R1-01/R1-02 刷新链已修面 + N3 测试类回归零回退 |
| 5 | 2.5-C1-007（P1） | 2.5 / nop-persistence（nop-orm-migration + nop-orm + nop-dao） | `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java`——`flipShardConfig` :412-423（`AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, ...)` :415，W5 写点）、**`rebuildRouting` :424-449（`((SessionFactoryImpl) sessionFactory).setShardSelector(selector)` :432/:436 热替换；:426-428 注释宣称「替换后新查询立即生效」；非 SessionFactoryImpl 宿主降级 restart-required :442-443）**；`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java`——`shardSelector` :70 非 volatile、`getShardSelector` :195（每查询普通读）、`setShardSelector` :198-199；`nop-persistence/nop-dao/src/main/java/io/nop/dao/shard/ConfigShardSelector.java`——`strategies` :57 / `defaultStrategyName` :58 / `entityBindings` :59 / `hasConfig` :60（**非 final 非 volatile**，init 期赋值后经普通字段发布） | rebuildRouting 热替换非 volatile 的 shardSelector：SWITCH 开门后请求线程可无限期读旧路由，业务写静默落旧分片表（主路径数据丢失面）；新 selector 策略表非 final 非安全发布，竞态读者可见未完全构造实例；注释「立即生效」断言在 JMM 下不成立 | **volatile 化 + 安全发布**（裁定⑤；batch exit criteria「刷新链下游 volatile 化同批」）：`SessionFactoryImpl.shardSelector` 升 volatile；`ConfigShardSelector` init 完成后策略表 final/不可变化承载；可见性确认读回比对（审计建议(3)）随裁定⑤取舍；e2e restart-required 降级路径与既有 `TestOrmMigrationWriteGate`/`AbstractOrmTestCase` set-then-get 面零回改（C10-012 注记：`TestMigrationPipelineE2E` restart-required 降级断言 :95 不因本修复改变、零回改） |
| 6 | 2.5-C7-004（P1） | 2.5 / nop-persistence（nop-orm-migration + nop-orm） | `nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmMigrationWriteGate.java`——`isClosed` :41-43、`checkWriteAllowed` :51-57、**`setClosed` :62-65（`AppConfig.getConfigProvider().updateConfigValue(...)` 进程内裸写，javadoc 自述「原子生效于本 JVM」）**；调用方 `OrmMigrationOrchestrator.java`——`completeSwitch` :289-303（**`setClosed(false)` :297 开门不回写配置源**）、`abort` :317-323（:320 同型单 JVM 开门）；`rebuildRouting` 收敛门=「全部实例重建完成」:426-428（多实例为设计自身声明拓扑） | 写门开门翻转仅本 JVM 生效且无持久化回写无租约：completeSwitch 后集群其余实例写冻结不可自愈——文档化恢复路径「滚动重启」重启后从配置源重载 closed，冻结经重启反向加固，只能逐节点手工改配置 | **写门持久化或租约 fail-open 形态**（裁定⑤二选一，**默认 = 租约 fail-open**：关门带时间盒、超时自动 fail-open + 告警 + 状态行留痕 + 文档化多实例操作矩阵[审计建议(2)(3)]；备选 = 配置源回写，须评估 IConfigProvider 持久化面触碰范围，若涉 api-core 接口扩展则 SPI 回归面追加并升级裁定）；与 R2-15「写门持久化半边归 R2-04」注记对接（裁定⑥） |
| 7 | 2.5-C1-010（P2，G48a 搭车；勘误：可见性半边经复核证伪归平台同案） | 2.5 / nop-persistence（nop-orm-migration） | `OrmMigrationOrchestrator.java`——`runVerify` 门禁 :222-223（**瞬时 `isClosed()` 检查 :222 + 分钟级 lag 静默窗与三级校验窗 :223 之后**）、`confirmSwitch` 门禁 :270-271（flip+rebuild 时长窗）、`abort` 读 :319（窗口极短，归 C1-006 不入本批）；写门无持有者/租约/token 概念（`OrmMigrationWriteGate.java` 全文 :25-70） | 写门/门禁检查是瞬时读而冻结承诺贯穿整个 VERIFY/SWITCH 分钟级生命周期：窗口内外部翻转无感知，VERIFY 在漂移数据上「passed」落账、completeSwitch 开门不复查门状态历史；可见性半边（原误报排除 a「翻转可见性合格」）经 WI2.5 复核证伪——provider 原地裸换无 volatile 边，归平台 2.2-C1-007 同案 | **可见性半边随裁定④ holder 化承载**（G48a 随批条款）；**保持机制半边按裁定⑤定夺**（默认 = 审计最低成本形态：lag 静默窗后、校验结束前各复核一次 isClosed 并落状态行留痕、失配 gateFailed——不引入分布式锁过度设计）；C10-014 整条为本批验收前置（两面均本批成员） |

### 平台设施与测试资产盘点（live 实读 @ `89d325d923`）

- **可复用平台件（修复不得重复造轮子）**：`ResourceCacheEntry.LoadWaitGraph`（`nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceCacheEntry.java` :450 静态内聚类，register/wouldCycle/unregister + describeChain，`LOCK-001` 锚点；调用点 :370-402 先例）+ 三环错误码（`CoreErrors` :364-375，`nop.core.component.resource-cache.load-reentrant-loop/circular-wait/wait-timeout`）+ 有界等待配置先例（`nop.core.component.resource-cache.load-wait-timeout` 默认 60s、≤0 不设上界档）——裁定②③的等待登记/环回溯/类型化超时按「机制单点复用 vs IoC 域内聚同构件」一次裁定；`ResourceRetirementQueue`（`RETIRE-001`）、`FutureHelper.syncGet` :158（无超时重载——裁定①的有界 join 原语落位须评估新增重载是否触碰 nop-api-core，随裁定④ PA 面一并声明）、`NamedThreadLocal`、`io.nop.commons.concurrent.*`；错误码落 `IocErrors`/`ConfigErrors`/`CoreErrors` 既有错误族（两档错误处理策略，英文消息）。
- **既有测试资产（回归门禁与测试基建）**：nop-ioc 测试树顶层 `io/nop/ioc` 包 17 个测试类（全树 71 文件含夹具/子包）——`TestBeanDepends`（`testAsyncStartOrder` :121-136：**全单元唯一 concurrent-start 用例**，成功路径双 bean 顺序断言，concurrentStart(true) :124 + awaitStartFinished :128）、`TestBeanConcurrentInitDeadlock`（2026-08-20 已修形态机制级钉：@Timeout(30s) + t1.join(15000)+isAlive 断言 + resolvedDepends 契约断言，C2-002 零回退门禁）、`TestConstructorCycleDetection`/`TestPrototypeCycleDetection`/`TestBeanParentLoop`/`TestReentrantCircularRepro`（同线程环检测）；nop-api-core/nop-config 配置面测试（`TestConfigExpressionResolver` 21 方法经 SimpleConfigProvider fixture 等，C10-009 反向核对在案）；nop-orm `TestOrmMigrationWriteGate` :50-105（单 JVM 开/关/读写放行 5 用例）+ nop-orm-migration `TestMigrationPipelineE2E`（:74-75/:83/:182-193 + restart-required 断言 :95）；nop-orm-migration `TestSourcePositionPinner`/`TestIncrementalLagProbe`/`AbstractMigrationE2ETestCase`；nop-dao `TestShardStrategy`/`TestShardFanoutPolicy`。
- **死锁测试纪律先例**：2504 设计 §七 + `LOCK-001/002/003` 实现期资产——daemon 夹具 + 有界等待窗 + 类级 @Timeout 防挂死整套件 + 环错误码断言（`TestXDefRef.testLoop` :73-79 迁移先例）——本批裁定⑦直接沿用。

### Baseline 勘误（输入不一致登记）

1. **WI 2.2 nop-ioc 行号零实质漂移**：审计载 getBean0 `L406`/`BeanDefinition.newInstance L534`/`initBean L642-644`/`runUntil L114-170`/`waitQuietly L194-201`/start 分支 `L502-512`/`handleStartError L526-533`/`awaitStartFinished L535-539`/`asyncStartBeans L541-542`——live 逐一核对全部命中（awaitStartFinished 实际 :536-539 vs 审计 535-539，±1 行以 live 为准）；`IocCoreInitializer` 审计 L46 = live `appContainer.start()` :46（零漂移；draft review R1 复核订正本勘误初稿的「→ :47」误记）；`IocConfigs` 审计 L64-66 → live allow-cycle :64-66、concurrent-start :28 ✓。
2. **WI 2.5 行号漂移（OrmMigrationOrchestrator/OrmMigrationWriteGate，缺陷本体零修复）**：审计载 completeSwitch `setClosed` :285 → live :297；abort `setClosed` :307-308 → live :320；runVerify 门禁 :215 → live :222-223；confirmSwitch 门禁 :263 → live :270-271；abort isClosed 读 :307 → live :319；flipShardConfig updateConfigValue :388 → live :415；rebuildRouting :397-426 → live :424-449（setShardSelector :405/:409 → live :432/:436；restart-required 降级 :413-416/:418-419 → live 注释 :426-428 + 降级分支 :442-443）；`OrmMigrationWriteGate` 审计 :38-59/:38-65 → live 全文 67 行（isClosed :41-43、checkWriteAllowed :51-57、setClosed :62-65；draft review R1 复核订正本勘误初稿误记的 :44-70/:44-46/:67-70）；`SessionFactoryImpl` 审计 :70/:194-200 → live :70/:194-200（get :195、set :198-199）✓。成因 = 审计基线后该文件的注释/文档行插入（非本 campaign 修复），执行时以 live 回读为准。
3. **C1-007 写点统计的 live 归位**：审计 C1-017 的「W1~W7」未逐点给行号——本 Baseline 成员表 4 已按 live 归位 7 写点（W1 applyChangeToUsed :305/:308、W2 updateRefs :144/:147、W3 updateRefs 测试路径 :166、W4 assignConfigValue :86、W5 updateConfigValue :92、W6 reset :76、W7 setConfigModel :117），其中 **W4/W5/W6 三点位于 nop-api-core `AbstractConfigProvider`**——这是「是否触碰 nop-api-core」判定的直接依据（判：触碰，PA 叠加声明见头部）；8 读站以「AbstractConfigProvider.getConfigValue :101 / collectUsedRefs :127 / DefaultConfigProvider.collectUnusedValues :78 / getAssignedValue :51 / isDynamic :47 / AbstractConfigReference.toString :37 / traceConfigVars :201 / wrapper 链（CastType/Placeholder/OverrideConfigReference 委托）」归位，全平台业务读经 `AppConfig.varRef().get()`/`getConfigValue` 汇入前四站。
4. **batch-proposal §二.5 G48 行与 R2-04 行的成员对应核对（一致性确认，非错位）**：G48 行「R2-04（C1-010）+ R2-01（C6-005）」与 R2-04 行成员清单第 7 项 `2.5-C1-010`、exit criteria 增补第 5 条「C1-010 写门可见性半边（G48a）随批」一致；G48 把 2.5-C1-010 **整条**归 R2-04（修→提前，无 successor 残留），故本批承接面 = 可见性半边（随裁定④）+ 保持机制半边（裁定⑤默认随批最小形态）——裁定⑤须对后者一次定夺，防「半边已修、整条无法判满」的台账悬挂。
5. **2506 边界解除事实**：2506 头部 Non-Goals 与其设计文档 §3.7/裁定⑦的「本批对 nop-ioc 容器内部（BeanContainerImpl/BeanScopeImpl）零改动承诺」**由本批解除**——`BeanContainerImpl`（start/getBean0/asyncStartBeans/handleStartError）改造入本批 scope；`BeanScopeImpl.close`（:100-120 逐 bean 销毁后上抛末个异常）维持既有上抛行为按既有消费，若裁定①的失败传导需要触碰该上抛链则须在裁定⑥内显式扩大声明，默认不触碰。
6. **C10 共享条目成员面拆分**：C10-002（C4-003/C4-008 本批面 ∥ C4-009/C4-011/C2-009 R4-15 面）、C10-003（C2-001/C2-002/C1-002[随主并案] 本批面 ∥ C2-017 P3 面 → Follow-ups）、C10-009（C1-007/C1-017 本批面 ∥ C1-008/C2-007/C2-008/C2-016/C1-020 R4-15 面）——三条目均不整条判满，cra-index 回填时按成员面注记（同 plan 2506 勘误 7 先例）；C10-012（2.5-C1-007 单成员）与 C10-014（C1-010/C7-004 双面均为本批成员）整条入批。
7. **owner doc 现状**：`docs-for-ai/02-core-guides/ioc-and-config.md` 现有「模型装载与退役代际并发契约」节（:234，plan 2504 产物）与「插件生命周期与配置源生命周期并发契约」节（:253，plan 2506 产物）——本批 owner doc 更新为**新建节**（IoC 启动图/死锁收敛契约 + 配置发布协议契约），非改写既有节；`docs-for-ai/04-reference/source-anchors.md` 已有 `CFG-001`（ConfigStarter）/`IOC-001~003`/`LOCK-001~003`/`RETIRE-001~002` 锚点——本批为**扩展既有 IOC/CFG 锚点族 + 新建配置发布协议锚点条目**，非新建条目族。

## Goals

- Phase 1 设计文档一次裁决（plan-first 前置，独立 draft review 通过），七项裁定无临场发明空间：①C4-003 join/失败传导/超时形态 + 宿主面边界；②C2-001 锁序环修复路线（含 allow-cycle 默认值裁定）；③C2-002 有界等待 + 环嗅探形态（与②机制单点关系）；④C1-007 api-core holder 形态 + SPI 回归面 + 刷新链零回退清单；⑤2.5-C1-007/C7-004/C1-010 刷新链下游三案形态；⑥与 R4-15/R2-21/R2-15 边界与移交清单（含 2506 零改动承诺解除声明）；⑦测试缝与死锁测试纪律。
- 七项成员全部修复并各带回归测试（rule 25）：C4-003（含 C4-008 三形态验收面）、C2-001（含 C2-012 不可见边族）、C2-002（含 C1-002 并案面）、C1-007（含 C1-017 族案）、2.5-C1-007、2.5-C7-004、2.5-C1-010。
- C10 成员面验收前置随本体落地（E2 同 diff）：C10-002 成员面、C10-003 成员面、C10-009 成员面、C10-012、C10-014。
- 关键工单先红后绿或变异/回退论证留痕（E2）：C4-003（失败注入后进程面假活红）、C2-001（双线程互等挂死红——缺陷形态下测试超时）、C2-002（双 owner 环挂死红）、C1-007（刷新写×并发读可见性/撕裂红）、2.5-C1-007（热替换读旧 selector 红）、2.5-C7-004/C1-010（窗口翻转无感知红）。
- Owner doc 随批更新：`docs-for-ai/02-core-guides/ioc-and-config.md`（新建节）、`docs-for-ai/04-reference/source-anchors.md`（IOC/CFG 锚点扩展 + 配置发布协议锚点）。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 R4-15（roadmap 8.55）的任何工单**：C4-009（restart 代际竞态自毁新生代）/C4-011（destroy 不 join 在途图）缺陷本体与 **CL-019**（TaskExecutionGraph.runTask catch(Exception) 设施面）均在 R4-15 同 PR 承接——本批 C4-003 修复在宿主/汇聚点侧闭合三形态，不改 TaskExecutionGraph 设施 catch 面（裁定⑥写明移交接口）；C1-008/C2-007/C2-008/C2-016/C1-020（配置订阅/应用面 C10-009 其余成员）；C2-005/C1-018/C1-019（2506 已移交面）。
- **不做 R2-21（roadmap 8.30，4.4 host 批）的任何工单**：双宿主启动窗口就绪门禁（4.4-C1-006）、quarkus 宿主 start/stop/restart 联动改造——本批 C4-003 只保证 nop-ioc 侧失败终态可查询与 standalone 默认传导分支，宿主退出码/健康检查联动为 R2-21 消费面（裁定①⑥边界）。
- **不做 R2-15（roadmap 8.24，2.5 peri 批）成员**：链编排/状态行/PubSub 11 项——本批 2.5 三成员之外不触碰 OrmMigrationOrchestrator 其余缺陷面（C1-006 abort 交错窗、C1-008 惰性组件、C3-008/C3-009 键派生与路由等）；写门持久化半边由本批 C7-004 承接后向 R2-15 作完成注记。
- **不承接相邻已修面**：2.1-C1-001/C2-001/C2-017（plan 2504 已修，本批只消费其 LoadWaitGraph 机制与错误码先例）；2.2-C1-009/C6-003/C1-010/C6-001/C6-002/C6-004（plan 2496/2497/2506 已修）；2.2-C1-008 跨键代际撕裂窗（R4-15 面——本批 holder 化只闭合 usedRef 单键发布协议，applyChange 双 volatile 表的跨键原子性语义归 R4-15，裁定④边界声明）。
- 不触碰 `model/*.orm.xml`、nop-auth、nop-plugin（2506 已收口面）、`_` 前缀生成物；除裁定④ PA 面外不扩 nop-api-core 接口签名（新增 IConfigProvider/IConfigReference 方法即 SPI 公共面扩张，须独立裁定并全列实现回归面——默认拒绝）。
- 不做 Deferred/Follow-ups 节所列项（C2-017 P3 javadoc/超时重载治理、配置发布跨键原子语义 owner 声明细化等）。

## Scope

### In Scope

- `nop-core-framework/nop-ioc`：`BeanContainerImpl`（start/asyncStartBeans/handleStartError/awaitStartFinished/getBean0）、`IocCoreInitializer`（启动序 join）、`ProducedBeanInstance`（有界等待/环嗅探）、`BeanDefinition`/`InjectTypeValueResolver`（不可见边收集，按裁定②）+ 单元测试。
- `nop-kernel/nop-api-core`：`DefaultConfigReference`/`AbstractConfigReference`（holder 化）+ `AbstractConfigProvider` 三写点适配 + 单元测试（SPI 回归面按裁定④清单执行）。
- `nop-core-framework/nop-config`：`DefaultConfigProvider`（7 写点中 4 点适配 holder 发布协议）+ 单元测试。
- `nop-persistence`：`SessionFactoryImpl`（volatile）、`ConfigShardSelector`（安全发布）、`OrmMigrationWriteGate`（租约/持久化形态）、`OrmMigrationOrchestrator`（门禁复核 + rebuildRouting 适配）+ 单元测试。
- 设计文档：`ai-dev/design/concurrency/ioc-startup-graph-and-config-publish-protocol.md`（裁定①–⑦，最终状态写法）。
- Owner doc：`docs-for-ai/02-core-guides/ioc-and-config.md`（新建节）、`docs-for-ai/04-reference/source-anchors.md`（锚点扩展）。

### Out Of Scope

- Non-Goals 所列全部项；R4-15/R2-21/R2-15/R-G 及后续批次工单；C4-009/C4-011/CL-019/C2-017/C1-008 等相邻缺陷本体。

## Execution Plan

### Phase 1 - IoC 启动图/配置发布协议设计裁定文档 + 独立评审（plan-first 前置）

Status: completed
Targets: `ai-dev/design/concurrency/ioc-startup-graph-and-config-publish-protocol.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [x] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14：只写决策与契约，不写 Proposed-vs-Current、不写类签名代码），须含以下七项裁定：
  - [x] **裁定① C4-003 concurrent-start 三复合面（本批核心裁定之一）**：join 点落位（IocCoreInitializer 启动序内 vs BeanContainerImpl.start 尾部 vs 两者）与调用形态一次定夺；join 有界超时形态（新配置键落 IocConfigs，沿 LOCK-001「默认有界 + ≤0 不设上界档」两档语义；超时按启动失败处理）；失败传导链（handleStartError 记录失败终态[startFailed 类标志或 startFuture 异常终态]→ awaitStartFinished 快速失败 → standalone 默认分支的进程面传导口径；**死容器 exit 语义**：exit-0 伪装成功在 standalone 族的最小闭合 = 失败终态可查询 + awaitStartFinished 抛出；**NopApplication 零改动**（nop-boot 不入本批 Scope/Targets——`NopApplication.run()` 对 `CoreInitialization.initialize()` 无吞点，join 抛出经 initialize() 自然传播使主任务不启动、进程非零退出，传导链以此为默认分支并在设计文档以调用链核对成文；draft review M1 定夺）——超出该默认分支的宿主联动[quarkus 三绿、spring 宿主、退出码强约束]显式划归 R2-21（roadmap 8.30）消费面并核对 4.4 归属无重叠）；C4-008 三形态验收面（F2 Error 孤儿→汇聚点 exceptionally/兜底闭合、F5 成功回调异常→统一走失败终态、F4 无超时 pending→join 有界化闭合）逐形态成文；**预设默认分支**：join 落 IocCoreInitializer 启动序末尾 + 超时失败即传导 + whenComplete 汇聚点兜底全形态闭合；备选分支（改 NopApplication 入口做显式检查——需扩 Scope 入 nop-boot）须给出为何默认传播链不足的论证后方可采。
  - [x] **裁定② C2-001 getBean0 锁序环**：三路线一次定夺——**默认 = 组合路线**：不可见边补收集（InjectTypeValueResolver.collectDepends 经容器按类型预解析 + BeanDefinition factoryBean 边收集，使声明边入拓扑图）+ 跨线程 BD 等待登记与环检出（复用 LoadWaitGraph 三件套形态：登记→wouldCycle→类型化环错误；机制单点复用 vs IoC 域内聚同构件的选型须给出理由）+ 有界等待兜底（检出竞态漏检窗）；备选 = 纯锁序重排（论证为何对「锁内用户代码解析出不可静态收集边」不成立）或纯 tryLock 有界等待（环仅延迟暴露、错误延迟放大——LOCK-001 已拒绝该单腿方案，沿用其拒绝理由）。**allow-cycle 默认值裁定（终裁附注①的硬绑定项）**：默认分支 = 维持 true（构建期拒环不成为默认行为，运行期环检出兜底承载；兼容面=现网允许循环依赖的 beans.xml 零破坏），改 false 为备选分支并显式列兼容面与迁移声明；supplier/表达式内动态 getBean 不可静态收集面按审计失效面枚举文档化声明。**补收集边的拓扑序/order 校验形态（draft review M2 增补）**：`BeanTopologySorter.fillResolvedDepends` 对每条 collectDepends 产出边做 `lowOrderIds` 校验、违反即抛 `ERR_IOC_BEAN_DEPEND_ON_HIGH_ORDER_BEAN`——现网 type-injection 跨 `ioc:init-order` 层引用高序 bean 的配置今天因边不可见而可启动，若补收集边直接入拓扑序将由「可启动」变「构建期硬失败」。默认分支 = **补收集边仅入等待登记/环检出，不进拓扑序与 order 校验**（零启动兼容破坏）；备选 = 入拓扑序 + order 违反降级 warn 并文档化，须列兼容面与迁移声明。类型→beanId 解析缝：`IBeanPropValueResolver.collectDepends(Set)` 无容器参数（default 空实现），容器侧预解析落点须在设计文档点名。与审计自主组 4 共用修法（collectDepends 补收集 + tryLock 兜底 + allow-cycle 裁定）的替代关系：本默认组合以等待图环检出 + 有界等待替代纯 tryLock 兜底，收集面相同，须在设计文档显式声明替代理由。
  - [x] **裁定③ C2-002 等待环有界化**：waitQuietly 有界 wait 循环 + 超时类型化错误（携带 beanId/initThread/createThread/等待链快照，错误码落 IocErrors）；默认超时档位与配置键命名一次定夺（沿 LOCK-001 两档语义：默认有界[建议对齐 `nop.core.component.resource-cache.load-wait-timeout` 60s 量级或独立 IoC 键]、≤0 不设上界档，键落 IocConfigs）；等待前环嗅探与裁定②等待登记机制的共享关系（同机制单点则一处声明、域内聚则两处同构声明）；与既有 selfWait fail-fast（:190-192）和 initError 广播（:151-158）协议的兼容性声明；`TestBeanConcurrentInitDeadlock` 机制级钉零回退核对表。
  - [x] **裁定④ C1-007 配置发布协议单一不可变 holder（本批核心裁定之二，nop-api-core PA 主体）**：holder 形态（location+provider 收拢不可变对象、DefaultConfigReference 持单 volatile 引用、updateValue 单发布零撕裂）与 7 写点×8 读站全集对照表（按 Baseline 成员表 4 归位点逐点核对适配）；**api-core 触碰面与 SPI 回归面义务**（2499 先例）：`IConfigReference` 实现族 5 类 + `AbstractConfigProvider` 子类 2 类 + AppConfig 消费面的回归面全列，`grep "implements IConfigReference|extends AbstractConfigReference|extends AbstractConfigProvider"` 全仓重枚举登记最终清单（含测试 mock）；**零接口签名变更默认分支**（新增接口方法即公共面扩张，须独立论证）；defaultValue 写点（W7/W6）处置（随 holder 或启动期限定文档化）；与 R1-01/R1-02 刷新链已修面零回退清单（JdbcConfigSource per-callback 隔离 4 条 + 卸载 2+2 条 + 超时 7 条 + 构造序/互斥回归全列为零回归门禁）+ N3 测试类（assignConfigValue/updateConfigValue setup/teardown 语义）强制回归面清单 + C1-008 跨键代际窗边界声明（R4-15 面，本批不闭合）。
  - [x] **裁定⑤ 2.5-C1-007/C7-004/C1-010 刷新链下游三案**：2.5-C1-007 = shardSelector volatile + ConfigShardSelector 策略表 final/不可变化 + 替换可见性确认读回比对（审计建议(3)）取舍；2.5-C7-004 = **默认租约 fail-open**（关门带时间盒、超时自动开启 + 告警 + 状态行留痕、租约时钟注入可测缝、文档化多实例操作矩阵）vs 备选配置源回写（须评估 IConfigProvider 持久化面触碰范围，涉 api-core 接口扩展则升级裁定并追加 SPI 回归面）二选一成文；2.5-C1-010 = 可见性半边随裁定④（零独立修复，登记承载声明）+ 保持机制半边**默认随批最小形态**（runVerify lag 静默窗后 + 校验结束前复核 isClosed、失配 gateFailed 并落状态行留痕）——若判不移交则显式声明 successor（防 G48 整条归批后的台账悬挂，见勘误 4）；三案与 R2-15 的完成注记口径。
  - [x] **裁定⑥ 协同边界声明**：与 R4-15（C4-009/C4-011+CL-019 同 PR 移交接口——本批失败终态/join 形态为其承载前提；C10-002/C10-003/C10-009 成员面拆分表）；与 R2-21（quarkus/spring 宿主启动窗口联动消费面 + 4.4-C1-006 归属核对结论）；与 R2-15（写门持久化半边完成注记）；与 2506 的边界解除事实（BeanContainerImpl 零改动承诺解除、BeanScopeImpl.close 上抛消费口径维持/或扩大声明）；与 2496/2497/2504 已落地语义的正交承诺。
  - [x] **裁定⑦ 测试缝与死锁测试纪律**：沿 2504 §七纪律成文——daemon 夹具 + 有界等待窗 + 类级 @Timeout（防挂死拖垮整套件）+ 环错误码断言（`TestXDefRef.testLoop` 先例）+ 变异/缺陷形态源码 prefix 运行留痕（2506 C1-010 先例：`_tmp/` 缺陷形态基线整类运行对比）；C4-003 失败注入 fixture（failure-injection bean + MyCreationLog 模式现成件）；C1-007 并发可见性测试的观测缝（有界轮询读 + holder 代际断言；禁 sleep 依赖，显式 await）；2.5 集群面单 JVM 可测形态（两 provider 实例/租约时钟注入——C10-014 建议形态）。
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动（plan-first 门禁）——记录于本 plan 头部 Draft Review 行或 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑦全部项，且独立 draft review 记录在案（plan 头部 Draft Review 行或 daily log）
- [x] 裁定①含死容器 exit 语义两部署族边界与 C4-008 三形态验收面；裁定②含 allow-cycle 默认值裁定与机制单点选型理由；裁定④含 SPI 回归面全列清单（live 重枚举）与 R1-01/R1-02 零回退门禁清单；裁定⑤含 C7-004 两分支取舍与 C1-010 保持机制半边去向（无悬挂）；裁定⑥含 2506 边界解除声明
- [x] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

> **Phase 1 执行记录（2026-10-26）**：设计文档 `ai-dev/design/concurrency/ioc-startup-graph-and-config-publish-protocol.md`（~245 行）落盘并登记 `ai-dev/design/concurrency/README.md`（层级表 + 阅读顺序第 9 位 + 实现锚）。七裁定唯一结论：①join 落 `IocCoreInitializer.initialize()` 内 start() 后同 try 界 + `nop.ioc.app-beans-container.start-join-timeout` 默认 60s/≤0 无界 + handleStartError 升级失败终态汇聚（C4-008 F2/F5/F4 全收敛）+ **NopApplication 零改动**（传导 = join 抛出经 initialize() 自然传播非零退出；宿主面归 R2-21）；②组合路线 = 复用 2504 `LoadWaitGraph` 机制单点（BD/PBI 双域登记→wouldCycle→类型化环错误→有界兜底）+ 不可见边处置「仅入等待登记、不进拓扑序与 order 校验」+ allow-cycle 维持 true + BD 域承载三点契约（inCreationThread 升 volatile owner 载体 / `getBean0` :406 唯一 synchronized(beanDef) 迁移 per-BD ReentrantLock.tryLock / 自旋每轮重读 owner 刷新边）；③waitQuietly 有界化 + `nop.ioc.bean-init.wait-timeout` 默认 60s 与 BD 域共用 + 60s 误伤面接受声明；④location+provider 单一不可变 holder + 单 volatile 引用 + 7 写点×8 读站适配表 + SPI 回归面 5+2+AppConfig + 零接口签名 + defaultValue 不入 holder（写点启动期唯一 + get() null 回落低频读边正交）；⑤shardSelector volatile + ConfigShardSelector 构造期注入 final 化 + 读回确认；**写门终裁 = 租约 fail-open**（`nop.orm.migration.write-gate-lease-seconds` 默认 1800s/≤0 关闭；**锚定 = 本 JVM 观察到 closed 即起租**[首见时间戳，不论 setClosed 或配置源刷新]；残余双写窗显式接受 + owner doc 硬条款；WriteGate 仅 WARN、状态行留痕归编排器复核点）+ C1-010 保持机制最小形态；⑥R4-15/R2-21/R2-15 移交清单 + 2506 BeanContainerImpl 零改动承诺解除；⑦2504 §七死锁测试纪律 + 3 配置键契约表 + IocErrors 五新码。
>
> 独立 draft review R1（agent_4843d9da，fresh session，30+ 处 file:line 抽查零漂移）：**1 Blocker**（租约仅在 setClosed 时锚定 → 第二实例永无租约、C7-004 本体未闭合）+ **4 Major**（传导链表 CoreInitialization.destroy 因 initializationLevel 守卫跳过失败 initializer → join 侧失败终态硬契约[cancel 孤儿图 + stop + 标记再抛 + 幂等防重入]；BD 域机制承载不成文；defaultValue 读面口径；租约残余数据风险与留痕载体）+ 7 Minor 全修；R2 增量复核 0B/0M，5 残留 Minor（幂等、可重入点名、两处表格断裂、口径同步、60s 接受正文）当轮落文。设计文档 Status → active。零产品代码改动。`check-doc-links --strict` 退出码 0。

### Phase 2 - 七成员实现 + 单元测试（nop-ioc 三成员 + api-core/config 一成员族 + nop-persistence 三成员）

Status: planned
Targets: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/`（impl/initialize/resolvers）及 `src/test/`、`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/config/` 及 `src/test/`、`nop-core-framework/nop-config/src/main/java/io/nop/config/impl/` 及 `src/test/`、`nop-persistence/nop-orm/`、`nop-persistence/nop-orm-migration/`、`nop-persistence/nop-dao/`（含各自 `src/test/`）

- Item Types: `Fix`（七成员全部为已确认 live defect[含 G48a P2 搭车的已裁决修项]，不得写成 Feature）、`Proof`（单元测试 + C10 成员面验收前置）

- [ ] C4-003 实现（含 C4-008 三形态）：按裁定①实施框架级 join + 有界超时 + 失败终态与快速失败 + 汇聚点兜底
- [ ] C2-001 实现（含 C2-012 边族）：按裁定②实施不可见边补收集 + 等待图环检出 + 有界等待兜底（allow-cycle 按裁定②定夺形态，无临场自加项）
- [ ] C2-002 实现（含 C1-002 并案面）：按裁定③实施有界等待 + 类型化超时/环错误；既有机制级钉零回改
- [ ] C1-007 实现（含 C1-017 族案）：按裁定④实施 api-core holder 化 + nop-config 四写点适配 + SPI 回归面全列执行（零接口签名默认分支）
- [ ] 2.5-C1-007 实现：按裁定⑤实施 shardSelector volatile + ConfigShardSelector 安全发布（+ 读回确认如裁定采信）
- [ ] 2.5-C7-004 实现：按裁定⑤实施租约 fail-open（或备选回写形态）；多实例操作矩阵 owner doc 内容同步产出
- [ ] 2.5-C1-010 实现：可见性半边随 C1-007（零独立代码，登记承载）+ 保持机制半边按裁定⑤默认形态实施（runVerify/confirmSwitch 窗口复核 + 失配 gateFailed 留痕）
- [ ] 回归测试（rule 25 显式覆盖 + C10 成员面验收前置同 diff，先红后绿或变异/回退论证留痕；全部死锁/并发测试按裁定⑦纪律）：
  - [ ] C4-003（C10-002 成员面）：失败注入 bean + concurrentStart(true) → join 语义下失败传导可观测断言（awaitStartFinished 抛出/容器失败终态可查询；修复前红 = 进程面 run-success 姿态可查询但无失败信号）；Error 注入 bean → 无 limbo（started 不翻转且失败终态成立，F2 闭合）；成功回调异常注入 → 非静默（F5 闭合）；join 超时注入（挂死 init + 上界调小）→ 类型化超时失败（F4 闭合）；`testAsyncStartOrder` 成功路径零回归 + N5 覆盖边界 javadoc 注记
  - [ ] C2-001（C10-003 成员面）：双线程互为 type-injection 环 fixture 并发首触 → 有界窗内类型化环错误/超时错误而非永久 BLOCKED（修复前红 = 断言窗内不可完成）；factoryBean 环 fixture 同型；不可见边补收集后拓扑可观测断言（构建期日志/图内容）；`TestConstructorCycleDetection`/`TestPrototypeCycleDetection`/`TestBeanParentLoop`/`TestReentrantCircularRepro` 零回归
  - [ ] C2-002（C10-003 成员面）：双 owner 互等环 fixture（init 期 includeCreating=false 交叉获取）→ 类型化环错误或有界超时（修复前红 = @Timeout 窗内挂死）；`TestBeanConcurrentInitDeadlock` 全断言零回改
  - [ ] C1-007（C10-009 成员面）：刷新线程写 × 多读者线程可见性测试（有界窗内读者读到新值；修复前红 = 陈旧读观测）；撕裂窗断言（updateValue 后 location/provider 代际一致——holder 单发布）；7 写点逐点冒烟（W1–W7 经 holder 路径）；N3 测试类（TestPluginLifecycle/TestReloadPlugin/TestShellRunner/TestBeanConditionUnlessProperty/TestMultiConfigVarExpression/TestAppBeansFilter 等）全绿强制回归；R1-01/R1-02 零回退门禁（per-callback 隔离 4 条 + WorkerOffload 2+2 条 + Timeout 7 条 + 构造序/互斥回归）；SPI 回归面测试执行（SimpleConfigProvider/DefaultConfigProvider/wrapper 链）
  - [ ] 2.5-C1-007（C10-012）：rebuildRouting 热替换后并发读者有界窗内读到新 selector 断言（修复前红 = 读旧 selector 观测）；策略表不可变/安全发布断言；e2e restart-required 降级断言零回改核对（:95）
  - [ ] 2.5-C7-004（C10-014 成员面）：租约超时 fail-open 断言（时钟注入）；开门后新写放行 + 状态行留痕断言；`TestOrmMigrationWriteGate` 既有 5 用例 + e2e 门序列零回归
  - [ ] 2.5-C1-010（C10-014 成员面）：VERIFY 窗口内翻转线程 × 校验线程交错 → 校验结束前复核命中失配 gateFailed 留痕断言（修复前红 = passed 落账观测）；confirmSwitch 窗口同型
- [ ] **端到端验证**（rule 22）：≥1 条 IoC 并发启动→失败传导→容器终态链路测试（concurrentStart 全链：start→图执行→失败注入→失败终态→join 感知→已建 bean 销毁→stop 可重入；rule 22 入口点=容器 start、出口点=调用方可观测失败终态）；≥1 条配置发布→刷新链下游链路测试（updateConfigValue/applyChange 写 → holder 单发布 → 下游读者[写门 isClosed / CFG_ORM_SHARD.get()→selector] 读到新值——发布协议从写点入口到下游出口完整走通）
- [ ] **接线验证**（rule 23）：join 在 IocCoreInitializer 启动序真实执行断言（非测试直接调 awaitStartFinished 的自证）；环检出/等待登记在 getBean0/runUntil 等待路径真实注册断言；holder 单发布路径被 applyChangeToUsed/updateConfigValue/updateRefs 真实调用断言（计数器/标志位或链路断言）
- [ ] **无静默跳过**（rule 24）：join 超时/环检出/等待超时全部类型化抛出而非静默吞；租约 fail-open 告警+留痕而非静默开启；门禁复核失配 gateFailed 而非跳过校验；C4-008 兜底路径无空 catch
- [ ] **提交纪律**：设计文档与实现分离提交（Phase 1 先落盘提交 → 独立 draft review → 实现按工单逐项提交）；七工单按模块分组提交——**nop-ioc 组**（C4-003/C2-001/C2-002）、**nop-config + nop-api-core 组**（C1-007 holder 两模块同变更集）、**nop-persistence 组**（2.5-C1-007/C7-004/C1-010），C10 成员面测试与对应本体**同一 commit**（E2 同 diff 核验依据）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 七成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 成员面验收前置（C10-002/003/009[均成员面]、C10-012、C10-014）同 diff 落地
- [ ] 必红证据（E2）：七成员至少各一项先红后绿或变异/回退论证留痕（C4-003 假活红 / C2-001 互等挂死红 / C2-002 环挂死红 / C1-007 可见性或撕裂红 / 2.5-C1-007 旧 selector 红 / 2.5-C7-004 冻结不自愈红 / 2.5-C1-010 窗口翻转无感知红），快照落 `_tmp/` 并登记
- [ ] 零回退实证：`TestBeanConcurrentInitDeadlock`、`TestBeanDepends`、`TestConstructorCycleDetection` 族、N3 测试类、R1-01/R1-02 刷新链回归、`TestOrmMigrationWriteGate`、e2e restart-required 断言全绿（钉零回改项逐条 git diff 为空核对）
- [ ] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [ ] `./mvnw test -pl nop-core-framework -am` BUILD SUCCESS；`./mvnw test -pl nop-kernel -am` BUILD SUCCESS（api-core 触达通道）；`./mvnw test -pl nop-persistence -am` BUILD SUCCESS
- [ ] Owner doc 裁定：ioc-and-config.md 新建节 + source-anchors.md 锚点扩展的内容定稿归 Phase 3 统一落账（本 Phase 显式登记该去向）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: 受影响聚合模块全量测试（nop-kernel / nop-core-framework / nop-persistence）；`docs-for-ai/02-core-guides/ioc-and-config.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路：`-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2497/2506 Phase 3 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-core-framework -am`、`./mvnw test -pl nop-kernel -am`（api-core SPI 回归面通道——勘误见下）、`./mvnw test -pl nop-persistence -am` BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径）；实际触达模块以 git diff 为准逐条登记
- [ ] SPI 回归面通道（若裁定④零接口签名成立）：`./mvnw test -pl nop-kernel -am` 覆盖 api-core + 全仓受影响实现模块抽查（按裁定④清单最低覆盖 = AbstractConfigProvider 两子类所属模块 + N3 测试类所在 nop-core-framework/nop-plugin/nop-shell 模块测试绿）；若裁定④涉接口签名变更则按 2499 先例全实现模块通道逐列执行并登记
- [ ] 零触碰确认：本批产品 diff 不含 `model/*.orm.xml`、nop-auth、`_` 前缀生成物、nop-plugin（2506 收口面）；`BeanScopeImpl` 若零改动作显式零触碰声明（`git diff --name-only` 核对登记）
- [ ] Owner doc 更新：`ioc-and-config.md` **新建**「IoC 启动图与配置发布协议并发契约」节（concurrent-start join/失败终态契约、环检出与有界等待契约、配置发布 holder 单发布协议、写门租约/复核契约——内容源 = Phase 1 设计文档裁定，派生摘要标注「以设计文档为准」）；`source-anchors.md` **扩展** IOC/CFG 锚点族（启动图契约/锁序环收敛锚点 + 配置发布协议锚点，LOCK/RETIRE 既有条目交叉引用）
- [ ] 缺口声明：触达模块中无 `docs-for-ai/03-modules/` 条目者（nop-ioc/nop-config/nop-orm-migration 等，以实际 diff 为准）按 R-G 治理轨先例登记（plan 执行记录 + daily log），不在本批新建 owner doc
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel --severity high`、`--module nop-core-framework --severity high`、`--module nop-persistence --severity high` 退出码 0；若有批前既有发现，逐条裁定登记
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] E1 + 批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] ioc-and-config.md 新建节 + source-anchors.md 锚点扩展落地；check-doc-links --strict 退出码 0
- [ ] scan-hollow 受影响模块逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] 零触碰确认登记（orm.xml/nop-auth/生成物/nop-plugin；BeanScopeImpl 口径）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.13 行、cra-index 台账

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（调用链运行时连通、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：P1 台账 6 行（L195/L199/L200/L201/L233/L237）状态收敛（open → 已修/本批）+ 并案成员行（C1-017/C4-008/C2-012/C1-002）随主案注记 + P2-G48 行（L432）C1-010 成分收敛注记（C6-005 成分已随 R2-01 收敛，本行整组可闭）+ C10-002/003/009 共享条目成员面注记（勘误 6）+ C10-012/C10-014 整条收敛
- [ ] roadmap 8.13 行 Status → done
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2507-cra-phase8.13-r2-04-ioc-startup-config-publish.md --strict` 退出码 0；scan-hollow 全绿复核
- [ ] Closure Evidence 写入本 plan `## Closure` 段（rule 27）
- [ ] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 7 项成员（6 P1 行 + G48 P2 行）状态全部收敛（并案行随主案注记）、roadmap 8.13 = done
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 7 项成员全部修复：C4-003（含 C4-008 三形态）、C2-001（含 C2-012）、C2-002（含 C1-002）、C1-007（含 C1-017）、2.5-C1-007、2.5-C7-004、2.5-C1-010
- [ ] batch-proposal R2-04 行 exit criteria 增补五条逐条达成并留证：①B1-01 终裁 P1 维持——C4-003 修复以无 join+失败不传导两面承载（join+失败传导+超时落地，判级不降档留证）；②C4-003 同 PR 批次边界——C4-009/C4-011+CL-019 移交 R4-15 清单在案（本批未越界改 TaskExecutionGraph 设施 catch 面）；③配置发布协议三字段单一不可变 holder——7 写点×8 读站对照表逐点落地、api-core PA 义务（SPI 回归面全列）执行留证；④2.5-C1-007/C7-004 刷新链下游 volatile 化同批——shardSelector volatile + 安全发布 + 写门持久化/租约形态落地；⑤C1-010 写门可见性半边（G48a）随批——holder 化承载声明 + 保持机制半边去向成文（无台账悬挂）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；七成员均有先红后绿或变异/回退论证（E2）；C10 成员面验收前置同 diff 落地（C10-002/003/009 仅成员面，R4-15 面未越界承接）
- [ ] 设计文档裁定①–⑦成文且经独立 draft review；R1-01/R1-02 刷新链已修面（per-callback 隔离/卸载/超时/构造序）与 2504 LoadWaitGraph 既有语义零回退实证（既有回归全绿 + diff 抽验）
- [ ] R4-15/R2-21/R2-15 边界守约：TaskExecutionGraph 设施面/宿主联动面/链编排面零越界修复、移交清单在案（裁定⑥）；2506 的 BeanScopeImpl.close 上抛消费口径维持或扩大声明已登记
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-core-framework -am`、`./mvnw test -pl nop-kernel -am`、`./mvnw test -pl nop-persistence -am` BUILD SUCCESS（E1/E3）
- [ ] Owner doc 更新完成：ioc-and-config.md（新建契约节）、source-anchors.md（IOC/CFG 锚点扩展）；缺口声明登记（E5）
- [ ] 受影响模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外/后置协同项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）join/失败传导/holder 单发布/环检出/租约 fail-open 路径运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile`（受影响模块）
- [ ] `./mvnw test`（受影响模块）
- [ ] checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2507-cra-phase8.13-r2-04-ioc-startup-config-publish.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 7 成员收敛 + roadmap 8.13 → done（E6）

## Deferred But Adjudicated

### C4-009/C4-011+CL-019（IoC 启动图同 PR 后置成员：restart 代际竞态/destroy 不 join/TaskExecutionGraph 设施 catch 面）

- Classification: `out-of-scope improvement`（batch-proposal R2-04 行 exit criteria 第 2 条的显式后置安排，非本批降级）
- Why Not Blocking Closure: batch exit criteria 原文「C4-003 同 PR 批次（C4-009/011+CL-019 在 R4-15）」——三成员在 R4-15（G31）落地；本批 C4-003 的失败终态/join 形态（裁定①）为其承载前提，移交接口在裁定⑥成文。
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/`（R4-15 批 plan，尚未立项；cra-index 对应行注记）

### 4.4-C1-006 双宿主启动窗口就绪门禁（quarkus/spring 宿主启动联动）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 宿主进程退出码/健康检查联动属 R2-21（roadmap 8.30）工单面；本批已使失败终态在 nop-ioc 侧可查询且 standalone 默认分支传导（裁定①边界），宿主消费面入批即越界。
- Successor Required: `yes`
- Successor Path: R2-21 批（batch-proposal §二.2 L95）

### C2-017（P3：includeCreating 语义与无界等待契约零声明——javadoc + awaitStartFinished 带超时重载）

- Classification: `watch-only residual`
- Why Not Blocking Closure: P3 归档面非 live defect 立项；其「带超时重载」依赖 C2-002/裁定①的超时原语落地——若裁定①③实现中该原语自然成型则随批顺带，否则留 R4-15 C10-003 残余面。
- Successor Required: `no`（R4-15 成员面 / R-G 治理轨登记）

## Non-Blocking Follow-ups

- **R4-15 同 PR 协同交接清单**（batch-proposal R2-04 依赖列「R4-15 协同」的落点）：①C4-003 失败终态与 join 形态（裁定①）作为 C4-009 代际令牌/C4-011 关闭序 join 修复的承载前提移交；②TaskExecutionGraph.runTask catch(Throwable)（CL-019 设施面）在本批汇聚点兜底形态上继续；③C10-002/C10-003/C10-009 共享条目的 R4-15 成员面（C4-009/C4-011/C2-009/C1-008/C2-007/C2-008/C2-016/C1-020 测试）随其本体落地；④C1-008 跨键代际原子语义（applyChange 双表发布）在本批 holder 化后的形态上继续。交接载体 = Phase 1 设计文档裁定⑥ + cra-index 对应行注记。
- **R2-15 对接注记**：本批 C7-004 落地后向 R2-15 作「写门持久化半边已闭合」完成注记（R2-15 批行 exit criteria 原文注记义务）；OrmMigrationOrchestrator 同文件 R2-15 成员（C1-006/C1-008/C3-008 等）实施时以本批门禁复核后的 live 形态为基线。
- **配置发布跨键原子性 owner 声明细化**：本批 holder 化闭合单键发布协议后，「一批变更作为一个代际」的跨键快照语义仍归 C1-008（R4-15）；若 owner doc 新建节需先行为使用方声明「跨键无原子刷新语义」约束，其措辞以裁定④边界声明为准。
- no remaining plan-owned work（占位，closure 时按实际复核重写）

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<每条 Exit Criterion 和 Closure Gate 的验证结果>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
