# IoC 启动图收敛与配置发布协议设计（IoC Startup Graph and Config Publish Protocol）

> Status: active（plan 2507 Phase 1 产出；独立 draft review R1+R2 通过，0 Blocker/0 Major）
> Last Reviewed: 2026-10-26（行号 = live @ `b2b165ea8f` 亲读回读值；执行前如漂移以 live 为准并登记）
> **评审**：R1 agent_4843d9da @ 2026-10-26——1 Blocker（裁定⑤ 租约仅在 setClosed 调用时锚定 → 第二实例经配置源刷新观察 closed 永无租约、C7-004 本体未闭合 → 改「本 JVM 观察到 closed 即起租」首见时间戳锚点 + 两实例经配置源传播可测形态）/ 4 Major（M1 传导链表 CoreInitialization.destroy 因 initializationLevel 守卫跳过失败 initializer → 更正 + join 侧失败终态硬契约[cancel 孤儿图 + stop + 标记再抛]；M2 BD 域承载不成文 → owner 载体 inCreationThread volatile + synchronized(beanDef) 单站点迁移 ReentrantLock.tryLock + 陈旧边刷新；M3 defaultValue 读面含 get() null 回落边口径订正；M4 租约残余数据风险[VERIFY 中段 fail-open 双写旧分片]与留痕载体[状态行留痕由编排器复核点承担、WriteGate 仅 WARN]显式声明）/ 7 Minor（传递依赖声明、「补收集边」改「不可见边处置」、catch(Exception) Error 变体注记、60s 档误伤面接受、ConfigShardSelector final 需构造期注入形态、提交纪律、register(waiter,null) 退化语义）——全部修订入文；30+ 处 file:line 抽查零漂移
> **评审 R2**：agent_4843d9da 增量复核 @ 2026-10-26——B1/M1/M2/M3/M4 五处闭合 PASS（第二实例经 checkWriteAllowed→isClosed() 惰性锚定确可自愈、锚点为 WriteGate 内静态 volatile 时间戳无需 store；join 侧契约消除 F2 limbo；BD 三点契约实现者零临场发明；口径订正诚实）、Minor 6/7 准确、无新 B/M；5 条残留 Minor（join 收敛幂等防重入、ReentrantLock 可重入点名、两处表格断裂、结论段/拒绝表口径同步、60s 档误伤面接受正文落实）已当轮全部落文。**结论：0 Blocker / 0 Major → active 进入 Phase 2**
> Source: plan 2507（`ai-dev/plans/2507-cra-phase8.13-r2-04-ioc-startup-config-publish.md`）Phase 1 裁定①–⑦义务；发现事实源 = WI 2.2 审计目录 `ai-dev/audits/2026-10/2026-10-17-1725-deep-audit-nop-core-framework-concurrency/`（C4-thread-pools.md + C4-round2.md 的 C4-003 终裁与 C4-008 三形态、C2-lock-sync.md + C2-round2.md 的 C2-001/C2-002/C2-012、C1-shared-mutable-state.md 的 C1-007/C1-017 族案终裁与 C1-002、merge-adjudication.md 自主组 1/4 与裁定 3、C10-test-protection.md 的 C10-002/003/009 与 N3/N5）+ WI 2.5 审计目录 `ai-dev/audits/2026-10/2026-10-19-0624-deep-audit-nop-persistence-peri-concurrency/`（C1-shared-mutable-state.md 的 C1-007/C1-010 与 C1-round2 证伪勘误、C7-long-running.md 的 C7-004、merge-adjudication.md 独立 #4/#6/#14 与 §7.3 L392 协同条款、C10-test-protection.md 的 C10-012/C10-014）
> 实现锚：`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/`（BeanContainerImpl / BeanDefinition / ProducedBeanInstance / BeanTopologySorter / resolvers）+ `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/initialize/IocCoreInitializer.java` + `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/config/`（DefaultConfigReference / AbstractConfigReference / AbstractConfigProvider）+ `nop-core-framework/nop-config/src/main/java/io/nop/config/impl/DefaultConfigProvider.java` + `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java` 与 `nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmMigrationWriteGate.java` + `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java`

## 一、设计结论

1. **裁定①（C4-003 ←C4-008 三形态并案）**：concurrent-start 启动图以**框架级 join + 失败终态 + 有界超时**收敛——join 落位 = `IocCoreInitializer.initialize()` 启动序内 `appContainer.start()`（:46）之后、同一 try 界内的有界等待（覆盖 concurrent-start 全部可达面：该配置消费链唯一入口为 AppBeanContainerLoader.loadAppContainer :78，仅 app 容器读取）；join 带可配置有界超时（新键落 IocConfigs，沿 LOCK-001「默认有界 + ≤0 不设上界档」两档语义），超时按启动失败处理；`handleStartError`（:526-533）升级为失败终态汇聚点——记录失败终态（startFuture 异常终态保留可查询）、成功回调异常统一纳入失败终态、对 Error 孤儿以 join 侧兜底闭合；`awaitStartFinished`（:536-539）对失败终态快速失败。**NopApplication 零改动**（nop-boot 不入本批）：join 抛出经 `IocCoreInitializer.initialize()` catch（:47-50 恢复 parentContainer 后重抛）→ `CoreInitialization.initializeTo`（:119-123 catch→destroy→rethrow）→ `NopApplication.run()`（:43）自然传播——主任务不启动、进程非零退出、run-success（:64-68）永不打印，standalone 死容器 exit-0 伪装成功在 nop-ioc 侧最小闭合；quarkus 三绿/spring 宿主/退出码强约束归 R2-21（8.30）消费面。TaskExecutionGraph 设施 catch 面（CL-019）不动，归 R4-15。
2. **裁定②（C2-001 ←C2-012 并案）**：getBean0 锁序环以**组合路线**收敛——**跨线程等待登记与环检出（机制单点复用 2504 `ResourceCacheEntry.LoadWaitGraph`）+ 有界等待兜底**（BD 域承载 = `inCreationThread` 升 volatile 为 owner 载体 + `getBean0` :406 唯一 synchronized(beanDef) 站点迁移 per-BD `ReentrantLock.tryLock` 有界获取 + 自旋每轮重读 owner 刷新登记边防假环），构建期图与校验全部维持现状：`InjectTypeValueResolver` 维持无 collectDepends（`IBeanPropValueResolver.collectDepends(Set)` default 空实现零触碰），不可见边处置**仅入运行期等待登记/环检出、不进拓扑序（sortBeans 图）与 order 校验（lowOrderIds / ERR_IOC_BEAN_DEPEND_ON_HIGH_ORDER_BEAN）**——现网「type-injection 跨 ioc:init-order 层引用高序 bean」「ignore-depends 逃逸边」等配置零启动兼容破坏。类型→beanId 解析缝落点 = `BeanContainerImpl.getBeanByType` 内既有的 findBeanByType 解析点（运行期等待登记按解析后的 BeanDefinition/PBI 定位目标 owner，不依赖静态边集）。**allow-cycle（`nop.ioc.bean-depends-graph.allow-cycle`，:64-66）默认值维持 true**：构建期拒环不成为默认行为，运行期环检出 + 有界兜底承载，现网允许循环依赖的 beans.xml 零破坏。与审计自主组 4（collectDepends 补收集 + tryLock 兜底 + allow-cycle 裁定）的替代关系：本分支以等待图环检出 + 有界等待替代 tryLock 单腿，收集面由运行期等待覆盖取代构建期 collectDepends 补收集（后者会同时改变 sortBeans 图、order 校验与 concurrentStart 任务 DAG，触发构建期硬失败兼容破坏）。
3. **裁定③（C2-002 ←C1-002 并案）**：`ProducedBeanInstance.waitQuietly`（:194-201）**有界化**——wait() 改带超时循环，超时抛类型化错误（新码落 IocErrors，携带 beanId/createThread/initThread/等待链快照）；等待前经裁定②同一等待图登记与环检出（机制单点一处声明，裁定③为消费方）；超时档位与配置键定稿 = **`nop.ioc.bean-init.wait-timeout`，默认 60_000ms（对齐 `nop.core.component.resource-cache.load-wait-timeout` 60s 量级），≤0 = 不设上界档**（现行无界语义，确定性测试缝）；BD 锁获取域（裁定②）与 PBI 等待域（本裁定）共用同一键（同一等待链诊断同源、运维单旋钮，2504「装载等待与编译等待共用一键」先例）。selfWait 同线程 fail-fast（:190-192）与 initError 广播协议（:151-158）零改动，有界化只新增「等待超时」第三出口。`TestBeanConcurrentInitDeadlock` 机制级钉零回退。
4. **裁定④（C1-007 ←C1-017 族案；nop-api-core PA 主体）**：配置发布协议 = **单一不可变 holder + 单 volatile 引用**——location+provider 收拢为不可变 holder 对象，`DefaultConfigReference.updateValue`（:54-57）改「构造新 holder + 单 volatile 写」一次发布，逐字段 volatile 路线拒绝（不能闭合两步写撕裂——终裁语）；defaultValue 不入 holder，按**启动期限定 + javadoc 契约**承载（唯一写点 W7 setConfigModel :117 位于 doStart main 线程，读面主体为诊断面（另含 get() null 回落低频读边，与 holder 正交），审计已裁其陈旧面为纯 JMM 理论面）；**零接口签名变更为默认分支**（2499 source-compat 先例）；SPI 回归面 = `IConfigReference` 实现族 5 类 + `AbstractConfigProvider` 子类 2 类 + AppConfig 消费面（3.4 全表，落盘前 live grep 重枚举）；7 写点×8 读站全部经 holder 单发布/单读路径，读站语义零变化；R1-01/R1-02 刷新链已修面 + N3 测试类零回退；C1-008 跨键代际窗归 R4-15 不闭合（边界声明）。
5. **裁定⑤（2.5-C1-007 / C7-004 / C1-010）**：**shardSelector 热替换 = volatile + 安全发布**（`SessionFactoryImpl.shardSelector` :70 升 volatile；`ConfigShardSelector` 策略表 init 完成后 final/不可变化承载；审计建议(3)读回比对**采信最小形态**——rebuildRouting 热替换后读回同一性确认，失配保持写门冻结走 gateFailed）；**写门集群面二选一终裁 = 租约 fail-open**（拒绝配置源回写：涉 IConfigProvider 持久化面即 api-core SPI 公共面扩张，超出本批 PA 授权——关门带时间盒、租约超时自动 fail-open + 告警 + 状态行留痕、租约时钟注入可测缝、`≤0 = 关闭租约档`为显式回退通道、多实例操作矩阵归 owner doc）；**C1-010 保持机制半边 = 默认随批最小形态**（runVerify lag 静默窗后 + 校验结束前各复核一次 isClosed，失配 gateFailed 落状态行留痕，不引入分布式锁过度设计），可见性半边随裁定④承载（零独立修复，登记声明）——两面均有着落，G48 整条归批无台账悬挂；向 R2-15 作「写门持久化半边已闭合」完成注记（口径 = 租约形态）。
6. **裁定⑥（协同边界）**：R4-15 移交 = C4-009 restart 代际令牌（裁定①失败终态/join 形态为其承载前提）、C4-011 destroy 路径有界 join（消费裁定① join 原语）、CL-019 设施 catch 面、C1-008 跨键代际原子语义、C10-002/003/009 的 R4-15 成员面；R2-21 = 宿主退出码/健康联动/双宿主启动窗口门禁（4.4-C1-006 归属 R2-21 无重叠）；R2-15 = 写门完成注记接收方 + OrmMigrationOrchestrator 其余缺陷面基线。**2506「BeanContainerImpl/BeanScopeImpl 零改动承诺」由本批解除**（容器启动图改造入 scope）；`BeanScopeImpl.close` 末个 destroy 异常上抛（:100-120）与 `BeanContainerImpl.stop` 转发（:595-617）的上抛消费口径维持——handleStartError 内 stop() 已有独立 catch（:528-532），失败传导不要求其聚合语义改造；BeanScopeImpl 零改动。
7. **裁定⑦（测试纪律）**：沿 2504 §七死锁纪律——daemon 夹具线程 + latch 对齐 + 有界观察窗 + 类级 @Timeout 防挂死 + 环/超时错误码断言（`TestXDefRef.testLoop` 先例）+ 变异/缺陷形态源码 prefix 运行留痕（`_tmp/` 对比）；C4-003 失败注入 fixture（failure-injection bean + MyCreationLog 现成件）；C1-007 可见性观测缝 = 有界轮询读 + holder 代际一致性断言（禁 sleep 依赖，显式 await）；2.5 集群面单 JVM 可测形态 = 两 provider 实例/租约时钟注入（C10-014 建议形态）。

## 二、背景与动机

R2-04 收敛 7 项已确认发现（P1×5 主案 + P1 搭车 1 + P2 搭车 1），三面缺陷域：

- **IoC 启动与等待环（C4-003 族案 + C2-001 族案 + C2-002 族案）**：concurrent-start 模式（`nop.ioc.app-beans-container.concurrent-start`，默认 false，docs/performance/optimize-app.md:17 明文推荐开启 + @Description 误标三重文档债）下启动 bean 任务体占共享 globalWorker（`asyncStartBeans` :541-558 经 TaskExecutionGraph :542）、框架级无 join 点（`awaitStartFinished` 生产零调用方）、启动失败仅 log+stop（`handleStartError` :526-533）——Exception 形态进程以 exit-0「run-success」/quarkus 三绿假活带死容器存活；whenComplete 汇聚点（:503-512）另有三类绕过 handleStartError 的形态：F2 Error 逃逸 runTask catch(Exception) 使图 future 永久孤儿（running=true/started=false 半可用 limbo）、F5 成功回调异常（runLazyProperties/runDelayMethod :507-508）零日志静默 started 永不翻转、F4 全链无超时永久 pending（`FutureHelper.syncGet` :158 无带超时重载）。等待环两域：BD 域——构造器级 type-injection（`InjectTypeValueResolver` :48-53 无 collectDepends）与 factoryBean 边（`BeanDefinition.newInstance` :534）不入拓扑图，跨线程各持一 beanDef monitor 在对方入口互等（synchronized 无超时不响应 interrupt）；PBI 域——init 阶段 owner 经 includeCreating=false 交叉获取（`initBean` resolvedDepends 循环 :638-645）双 owner 互等，`waitQuietly` :196 裸 wait() 无超时；同线程 selfWait（:190-192）fail-fast 使顺序启动测试必绿。
- **配置发布协议（C1-007 族案）**：运行期配置刷新经 usedRefs 常驻 `DefaultConfigReference` 三字段裸写裸读——`provider` :15 非 volatile、`updateValue` :54-57 `setLocation` :55 + `setProvider` :56 两步 plain write 撕裂窗、`AbstractConfigReference.location` :13 / `defaultValue` :16 裸字段；刷新写（W1 applyChangeToUsed :305/:308，config-executor 单线程）对全平台任意请求线程（`AppConfig.varRef(...).get()` 热路径）可见性无界。7 写点×8 读站全经同一裸字段（归位见 3.4 表）。
- **刷新链下游（2.5 三案）**：`OrmMigrationOrchestrator.rebuildRouting`（:424-449）热替换非 volatile 的 `SessionFactoryImpl.shardSelector`（:70，get :195/set :198-199），SWITCH 开门后请求线程可无限期读旧路由——业务写静默落旧分片表；新 `ConfigShardSelector` 策略表（:57-60）非 final 非安全发布；注释「替换后新查询立即生效」（:425-427）在 JMM 下不成立。写门 `setClosed`（OrmMigrationWriteGate :62-65）经 updateConfigValue 仅本 JVM 生效：completeSwitch（:297）/abort（:319-320）开门后集群其余实例写冻结不可自愈，「滚动重启」恢复路径反向加固冻结（重启从配置源重载 closed）；写门/门禁检查为瞬时读（runVerify :222-223、confirmSwitch :270-271）而冻结承诺贯穿 VERIFY/SWITCH 分钟级生命周期，无保持机制；门可见性半边经 WI2.5 C1-round2 复核证伪归平台同案 C1-007。

前批边界：plan 2504 已在 nop-kernel 落地 LoadWaitGraph 等待图 + 有界等待兜底 + 三环错误码（锚点 LOCK-001）；plan 2496/2497 已落地刷新链隔离/卸载/超时/构造序/close 互斥；plan 2506 已落地插件双轨锁契约——本批改动不回退上述已修面（义务见裁定④⑤⑥）。

## 三、核心设计

### 3.1 裁定①：concurrent-start join / 失败终态 / 有界超时（C4-003 主案 + C4-008 三形态）

**join 落位定夺（三选一：IocCoreInitializer 启动序内 / BeanContainerImpl.start 尾部 / 两者）**：终裁 = **仅 IocCoreInitializer 启动序内**。理由：concurrent-start 配置的消费链唯一入口为 `AppBeanContainerLoader.loadAppContainer` :78（`loadFromResource` 测试/编程式装载不读取）——即 concurrent-start 只在 app 容器可达，IocCoreInitializer 一处 join 即覆盖全部生产可达面；`BeanContainerImpl.start()` 尾部 join 会把「提交即返」契约改为阻塞语义，波及插件子容器等同步启动调用方（2506 消费的 activate 链），且对宿主面零增量；「两者」引入双等待点无增量收益。join 调用形态 = `awaitStartFinished` 的有界等价（复用同方法体改造，测试直接调用的既有形态 :128 零破坏）。

**join 有界超时**：新配置键 `nop.ioc.app-beans-container.start-join-timeout` 落 IocConfigs，Integer 毫秒，默认 60_000（对齐 LOCK-001 量级），**≤0 = 不设上界档**（退化为现行 syncGet 无界语义——确定性测试缝与显式回退通道，与 `nop.core.component.resource-cache.load-wait-timeout` :112-113 两档语义同构）。超时按启动失败处理：抛类型化 NopException（新码落 IocErrors），失败终态成立。`FutureHelper.syncGet`（nop-api-core）**不新增带超时重载**——有界等待在 nop-ioc 侧以 deadline 循环实现（`CompletionStage` 完成态轮询 + 剩余时间等待），零 api-core 触碰（PA 面收敛到裁定④单点）。

**失败终态与传导链**：`handleStartError` 保持「唯一汇聚点」地位并升级为失败终态落定：失败时 startFuture 保留异常终态（不再被后续 start 覆写前可查询）、`awaitStartFinished` 对异常终态快速失败（现状 syncGet 已重抛，语义保持并成文）；成功分支（:507-509）回调体（runLazyProperties/runDelayMethod/started 翻转）纳入保护——回调异常统一走 handleStartError 落失败终态而非静默（F5 闭合）；Error 形态图 future 永不完成（F2），join 侧以超时档兜底收敛到失败终态——**三形态均收敛到「失败终态可查询 + join 抛出」，无 limbo 残留**。

**调用链核对表（standalone 默认传导，零改动传播）**：

| 链路站点 | live 位置 | 传导行为 |
|---|---|---|
| IocCoreInitializer.initialize | :44-51 | `appContainer.start()` :46 后 join；join 抛出落同一 try 界 → :47-50 catch 恢复 parentContainer 后重抛 |
| CoreInitialization.initializeTo | :101-113 initializer 循环 :107 → :119-123 | catch → `destroy()` → `NopException.adapt(e)` 重抛；**但** `initializationLevel` 仅在 initializer 成功返回后推进（:107-108），`destroy()` :168 的 `initializationLevel < initializer.order()` 守卫使**失败的 IocCoreInitializer 被跳过**（其 destroy :60 container.stop() 不执行）——失败容器的 stop 不由此链承担，由 join 侧负责（见下「join 侧失败终态」）；两处 catch(Exception) 不接 Throwable：future 以 Error 完成时 destroy() 不跑，传播到 run() 仍成立（Error 变体） |
| NopApplication.run | :43 `CoreInitialization.initialize()` | 异常传播出 run()——主任务 `task.getAsInt()` :61 不执行，run-success :64-68 永不打印 |
| 进程面 | JVM 默认 | main 抛出 → 非零退出；supervisor on-failure 语义恢复 |

**join 侧失败终态（draft review M1 增补，硬契约）**：join 超时或 startFuture 异常终态时，join 侧必须先执行 handleStartError 等价的失败终态收敛——`cancellable.cancel` 收敛在飞/孤儿启动图（:556/:599-602）+ 容器 stop（销毁已建 bean、释放非 daemon 线程）+ 失败终态标记——**再**抛类型化异常；否则 F2 形态（Error 孤儿、whenComplete 永不触发）下容器 running=true 残留、已建 bean 不销毁，与「无 limbo 残留」承诺矛盾，且泄漏的非 daemon 线程可阻止「main 抛出 → 非零退出」成立。 **幂等防重入**：`stop()` 不置空 singletonScope（:605-612），二次调用会重复执行 bean destroy 用户代码——失败终态标记已成立（whenComplete 已跑 handleStartError 的 F4/F5 场景）时 join 侧跳过收敛，只抛类型化异常；收敛与标记以同一失败终态为单一事实源。

**C4-008 三形态验收面**：

| 形态 | 缺陷行为 | 闭合方式 | 验收断言 |
|---|---|---|---|
| F2 Error 孤儿 | runTask catch(Exception) 不接 Error → 图 future 永不完成 → limbo | join 超时档兜底 → 失败终态 + join 抛出（Error 注入 bean + join 上界调小） | started 不翻转、失败终态成立、awaitStartFinished 抛类型化错误 |
| F5 成功回调异常 | success 分支异常逃逸 → started 永不翻转、零日志 | 回调体保护 → 统一走 handleStartError 失败终态 | 注入 delay 方法异常 → 非静默、失败可查询 |
| F4 无超时 pending | 图/任务/syncGet 三层全无超时 → 永久 pending | join 有界化（挂死 init + 上界调小） | 超时类型化错误而非挂死 |

**exit 语义与宿主边界**：standalone 族最小闭合 = 失败终态可查询 + join 抛出经上表传播链产生非零退出；`restart()` 场景的代际竞态（C4-009）不在本批（join 使 stop 语义演进为「等旧图终止」归 R4-15 消费）；子面 (a) 启动体占共享 globalWorker 不属本批修复面（B1-01 终裁：P1 由无 join + 失败不传导承载，(a) 单独计 P2 条件化），登记 watch；quarkus 三绿/spring 宿主/退出码强约束归 R2-21。

### 3.2 裁定②：getBean0 锁序环收敛（C2-001 主案 + C2-012 边族）

**机制单点 vs IoC 域内聚选型**：终裁 = **机制单点复用** `ResourceCacheEntry.LoadWaitGraph`（nop-kernel/nop-core，public static 无状态工具类，nop-ioc 经既有依赖可达[传递依赖：nop-ioc pom 仅声明 nop-xlang，nop-xlang → nop-core；Phase 2 不新增直接依赖声明、以现有传递面为准并在实现注记登记]——IocCoreInitializer 已 import io.nop.core.*）。理由：①等待图的全局不变式是「任一线程任一时刻至多处于一个等待状态」，Thread→Thread 单图对资源装载等待与 bean 创建等待天然统一，**跨域环**（bean init 等待模型装载、模型装载线程反等 bean init）只有单图能检出，IoC 域内聚同构件对此结构性失明；②register/wouldCycle/describeChain + MAX_CHAIN_DEPTH 诊断口径直接复用，避免第二套环诊断协议；③域内聚零增量收益（LoadWaitGraph 无生命周期耦合）。代价：nop-ioc 对 nop-core 依赖方向为既有方向，零新增。

**等待登记与环检出的两域落点**（「登记→wouldCycle→类型化环错误→有界兜底」三件套，同构于 2504 §3.2.1）：

| 域 | 等待点 | 登记/检查时序 | 有界兜底 |
|---|---|---|---|
| BD 域 | `getBean0` :406 `synchronized(beanDef)` 入口（构造参数 type-injection/factoryBean/表达式/ignore-depends 全部不可见边族的汇聚点） | 进入 BD monitor **之前**登记「当前线程 → 目标 beanDef 创建 owner」并回溯（后完成登记方必命中：ConcurrentHashMap put 即可见，先到者漏检窗由后到者的检查闭合）；命中即抛类型化环错误（新码落 IocErrors，携带等待链快照 + 双方 beanId） | synchronized 无超时 → BD 临界区入口改有界获取，超时抛类型化错误（区分「环已检出即时失败」与「漏检窗有界兜底」两出口） |
| PBI 域 | `runUntil` :136-141 → `waitQuietly`（裁定③本体） | wait 前登记「等待线程 → initThread（:144 已知 owner 引用）」+ wouldCycle；命中抛类型化环错误 | wait 带超时循环（3.3） |

**BD 域机制承载三点契约（draft review M2 增补）**：①**owner 载体** = `BeanDefinition.inCreationThread`（:86，现非 volatile、仅创建窗置位、写在 beanDef monitor 内）**升 volatile**——waiter 在 monitor 外读 owner 须有 HB 边，否则「后完成登记方必命中」退化为大概率命中 + 超时兜底；②**锁原语迁移** = BD 临界区由 `synchronized(beanDef)` 改为 per-BeanDefinition `ReentrantLock.tryLock(remaining)` 有界获取（全仓唯一 synchronized(beanDef) 站点 = `BeanContainerImpl.getBean0` :406，迁移面单点；锁对象随 BeanDefinition 生命周期，不新增全局锁）；③**陈旧边刷新** = 有界自旋期间 owner 可能换手（T3→T1→旧 owner 闭合成无死锁假环），每轮重读 owner 并刷新等待图登记边后再 wouldCycle，杜绝假环误报；owner 为 null（目标尚无创建者）时 `register(waiter, null)` 合法但退化为无环检语义，由有界兜底承载。PBI 域 owner = `initThread`（:54/:144 已知引用）无此问题。 **可重入性**：`ReentrantLock` 与 monitor 重入语义一致，同线程递归 getBean（及 `isInCreationByCurrentThread` :412 fail-fast）行为不变。

**不可见边处置三类处置表（M2 增补定夺）**：

| 处置维度 | 裁定 | 理由 |
|---|---|---|
| 入等待登记/环检出 | **是**（唯一去向） | 运行期等待点是全部不可见边族（E1 type-injection / E2 factoryBean / E3 supplier / E4 表达式 / E5 ignore-depends / E9 XPL 相位文本 / E10 config 重解析）的物理汇聚点，一处覆盖全部 |
| 入拓扑序（sortBeans 图） | **否** | 补收集若喂给 sortBeans 即改变构建期排序与 concurrentStart 任务 DAG——现网靠「跨 order 高序引用 + 边不可见」才能启动的配置变构建期失败，兼容破坏 |
| 入 order 校验（lowOrderIds / ERR_IOC_BEAN_DEPEND_ON_HIGH_ORDER_BEAN） | **否** | 同上：BeanTopologySorter :235-239 校验对不可见边处置不执行，type-injection 跨 `ioc:init-order` 层引用维持现状可启动 |

据此 `InjectTypeValueResolver` **不新增** collectDepends 实现，`BeanDefinition.collectDepends` 遍历范围（constructorArgs + props）不变，`fillResolvedDepends`（:153-196）与 `BeanTopologySorter` 全文零改动；类型→beanId 解析缝落点 = `BeanContainerImpl.getBeanByType` 既有 findBeanByType 解析点（运行期登记经解析后的 BeanDefinition 定位目标 owner，无需容器参数的构建期接口）。

**allow-cycle 默认值裁定**：维持 true（默认分支绑定项）。运行期环检出 + 有界兜底承载拒环职责；改 false 为备选档（构建期拒环成为默认行为，须列兼容面与迁移声明）不采——2504 已确立「配置错误应即时类型化报告」的运行期路径等价形态，默认翻转无增量收益且破坏现网 beans.xml。

**不可静态收集面枚举（文档化声明）**：supplier 函数、XPL 表达式/变量域动态边、XplValueResolver、ignore-depends/force-lazy-property 显式逃逸边、config 监听器运行期重解析入口——不进任何静态图，其环由运行期等待图统一检出兜底；该声明随 owner doc（Phase 3）成文。

### 3.3 裁定③：PBI 等待环有界化（C2-002 主案 + C1-002 并案）

**有界化形态**：`waitQuietly`（:194-201）改带超时循环——等待前经裁定② PBI 域登记与环检出；deadline = 当前时间 + `nop.ioc.bean-init.wait-timeout`；remaining ≤ 0 抛类型化超时错误（新码落 IocErrors，携带 beanId/createThread/initThread/`describeChain` 等待链快照）；中断处理（:197-199 恢复中断位后抛）保持。**配置键定稿**：`nop.ioc.bean-init.wait-timeout`（Integer 毫秒，默认 60_000 对齐 LOCK-001 量级；≤0 = 不设上界档 = 现行无界语义）；BD 域有界获取（裁定②）与 PBI 等待共用此键——同一 bean 创建等待链的诊断同源，运维单旋钮。裁定① join 键为容器级总预算，与本键（单次跨线程等待上界）独立成键、各自 ≤0 档。 **60s 默认档误伤面接受声明**：现行无界等待对合法慢创建（>60s 构造器/初始化）不误伤，有界化后此类 bean 的等待者将以超时错误失败——沿 LOCK-001 先例接受该代价（死锁/永久挂死的确定性暴露优于极少数慢创建的无界等待），owner doc 须注明 `≤0` 档为生产逃生口、慢创建场景调大键值。

**与既有协议兼容表**：

| 协议点 | live 形态 | 兼容裁定 |
|---|---|---|
| selfWait 同线程 fail-fast | :190-192 `ERR_IOC_BEAN_INIT_SELF_WAIT` | 零改动先行：登记/有界逻辑只作用于跨线程等待分支 |
| initError 广播 | :151-158 catch(RuntimeException\|Error) 置 initError + notifyAll | 零改动：等待者被唤醒后重抛语义不变 |
| markPropSet/markPropSetFailed | :91-109 PROPERTY_SET 推进与失败广播 | 零改动 |
| 等待者重查循环 | :118-146 while(true) status/initError 重查 | 结构保持，仅 wait 点有界化 |

**`TestBeanConcurrentInitDeadlock` 零回退核对**：@Timeout(30s)（:54）/ t1+t2 join(15000)+isAlive 断言（:114-118）/ 早可见同实例断言（:66）/ resolvedDepends 契约断言（:124）——修复后全断言原样通过（该测试的 T1/T2 等待在门闩放行后有界内完成，不触超时档）；机制级钉不得改写、不得弱化。

### 3.4 裁定④：配置发布协议单一不可变 holder（C1-007 族案，nop-api-core PA 主体）

**holder 形态**：location + provider 收拢为不可变 holder 对象；`DefaultConfigReference` 持单 volatile 引用；`updateValue`（:54-57）语义 = 「构造新 holder（携带新 loc + 新 provider）→ 单 volatile 写发布」，撕裂窗结构性消失（读侧要么见全旧代、要么见全新代，location 与 provider 恒同代）；`getAssignedValue`（:50-52）/`getProvider`（:41-43）/`isDynamic`（:46-48）读 holder 成员，读站语义零变化。**defaultValue 处置**：不入 holder——唯一写点 W7 `setConfigModel`（DefaultConfigProvider :117 `ref.setDefaultValue`）位于 ConfigStarter doStart 启动序 main 线程（WI2.2 第 3 轮精度修正确认），读面主体为诊断字段（toString/getDefaultValue/traceConfigVars）**加一条低频语义读边**：`IConfigReference.get()`（:45-51）在 `getAssignedValue()==null` 时回落 `getDefaultValue()`（如 `updateConfigValue(ref, null)` 后）——该读边与 holder 正交（defaultValue 独立字段、启动期写定后只读），不构成撕裂面；draft review M3 订正本口径；以「启动期单线程写、运行期只读」javadoc 契约承载，与审计 C1-017 面 (a) 理论面裁定一致；入 holder 反而迫使 W7 走整体替换且复杂化「仅改 default 不动 assigned」语义。

**7 写点 × 8 读站适配表（全部经 holder 单发布/单读，按 plan 成员表 4 归位逐点核对）**：

| 写点 | live 位置 | 适配 |
|---|---|---|
| W1 applyChangeToUsed（运行期刷新主写点） | :305 / :308 | 经 updateValue → holder 单发布 |
| W2 updateRefs（changeConfigSource 主路径） | :144 / :147 | 同上 |
| W3 updateRefs（BaseTestCase 测试路径） | :166 | 同上 |
| W4 assignConfigValue | AbstractConfigProvider :86 | 同上 |
| W5 updateConfigValue | AbstractConfigProvider :92 | 同上（OrmMigrationWriteGate.setClosed / flipShardConfig 等消费） |
| W6 reset | AbstractConfigProvider :76 | 同上（重采样回写） |
| W7 setConfigModel（defaultValue） | DefaultConfigProvider :117 | 启动期限定契约承载（不入 holder） |

| 读站 | live 位置 | 适配 |
|---|---|---|
| R1 getConfigValue | AbstractConfigProvider :101 `ref.get()` | 读 holder，零语义变化 |
| R2 collectUsedRefs | AbstractConfigProvider :127 | 同上 |
| R3 collectUnusedValues | DefaultConfigProvider :78 | 同上 |
| R4 getAssignedValue | DefaultConfigReference :51 | 读 holder 成员 |
| R5 isDynamic | DefaultConfigReference :47 | 同上 |
| R6 AbstractConfigReference.toString | :37 | 同上（诊断面） |
| R7 traceConfigVars | DefaultConfigProvider :191-203 | 同上（诊断面） |
| R8 wrapper 链 | CastTypeConfigReference / PlaceholderConfigReference / OverrideConfigReference 委托 | 经 get()/getProvider() 透传，零改码预期 |

**api-core SPI 回归面全表（5 + 2 + AppConfig；落盘前重跑全仓 grep 重枚举：`implements IConfigReference|extends AbstractConfigReference|extends AbstractConfigProvider`，含测试 mock）**：

| 类 | 模块 | 回归方式 |
|---|---|---|
| DefaultConfigReference | nop-api-core | 可见性/撕裂并发测试 + 既有功能钉 |
| AbstractConfigReference | nop-api-core | 诊断面（toString/getDefaultValue）回归 |
| CastTypeConfigReference / PlaceholderConfigReference / OverrideConfigReference | nop-api-core | wrapper 链委托行为回归 |
| SimpleConfigProvider | nop-api-core | `TestConfigExpressionResolver` 21 方法既有面全绿 |
| DefaultConfigProvider | nop-config | 刷新链回归 + 7 写点逐点冒烟 |
| AppConfig 消费面 | nop-api-core | hostId 写（:53 updateConfigValue 通道）+ varRef 读热路径 |

**零接口签名变更默认分支**：IConfigReference/IConfigProvider 零方法增删（新增接口方法即公共面扩张，须独立论证——默认拒绝，沿 2499 先例）。**零回退清单**：R1-01/R1-02 = JdbcConfigSource per-callback 隔离 4 条 + WorkerOffload 2+2 条 + Timeout 7 条 + 构造序/close 互斥回归（2506 裁定④⑤）全绿；N3 测试类（TestPluginLifecycle/TestReloadPlugin/TestShellRunner/TestBeanConditionUnlessProperty/TestMultiConfigVarExpression/TestAppBeansFilter 等 setup/teardown 经 assignConfigValue/updateConfigValue 写全局配置）为强制回归面。**边界声明**：C1-008 跨键代际撕裂窗（applyChange 双 volatile 表发布 :261/:290）归 R4-15，本批 holder 化只闭合 usedRef 单键发布协议。

### 3.5 裁定⑤：刷新链下游三案（2.5-C1-007 / C7-004 / C1-010）

**2.5-C1-007（shardSelector）**：`SessionFactoryImpl.shardSelector`（:70）升 volatile（写少读多热路径，开销可忽略）；`ConfigShardSelector` 策略表（现为 init() 后赋值 :57-60、:88-90 已 unmodifiableMap——「final 承载」需构造期注入形态[工厂方法构造完整策略表后一次赋值]，Phase 2 按此形态重构，不改外部装配契约；strategies :57 / defaultStrategyName :58 / entityBindings :59 / hasConfig :60）init 完成后 final/不可变化承载（构造后不再变更，安全发布）；审计建议(3)读回比对**采信最小形态**：rebuildRouting 热替换（:432/:436）后读回 `getShardSelector()`（:195）做实例同一性确认，失配 = 可见性异常 → 保持写门冻结走 gateFailed——把不可观测的可见性缺陷转化为显式失败。e2e restart-required 降级断言（TestMigrationPipelineE2E :95）零回改。

**2.5-C7-004（写门集群面）二选一终裁 = 租约 fail-open**。形态：**租约锚定 = 本 JVM 观察到门进入 closed 态即起租，不论经本地 `setClosed(true)` 还是经配置源刷新观察到 closed**（draft review B1 定夺：live 中 setClosed 仅本 JVM 生效 :62-65，其余实例的 closed 态来自配置源刷新——若租约只在 setClosed 调用时锚定，第二实例永无租约、冻结不可自愈，C7-004 本体未闭合）；锚点落位 = `isClosed()` 惰性检查处缓存「首见 closed 时间戳」（配置值由 true→false 时清零），到期判定 `now - firstSeenClosed ≥ lease`；新配置键落 OrmConfigs，默认 1800s 覆盖 VERIFY+SWITCH 分钟级全程，`≤0 = 关闭租约档`退化为现行永久语义（单机部署/确定性测试缝）；租约到期自动 fail-open + **WARN 告警日志留痕**（到期开门是显式事件而非静默；「状态行留痕」不由 OrmMigrationWriteGate 承担——其为 nop-orm 静态工具、无 MigrationJobState/store 访问权——而由编排器 runVerify/confirmSwitch 复核点[裁定 C1-010 保持机制]在观察到 fail-open 时落账 gateFailed）；**残余数据风险声明（draft review M4）**：fail-open 若发生于 VERIFY 中段（flipShardConfig :412 前）、增量链未 stop（:278-279 后才 stop）时，写直达旧分片路由——即写门本意要防的双写窗；本设计以「默认租约 ≥ 迁移全程」隐含规避并**显式接受**该残余：owner doc 硬条款 = 租约默认值须 ≥ 迁移全程、到期 WARN 为运维干预信号、运维干预矩阵成文；租约时钟经注入缝可测（C10-014 两 provider 实例/时钟注入形态）；多实例操作矩阵（哪些节点关门、confirm/complete 顺序、租约语义下运维干预点、到期告警处置）归 Phase 3 owner doc；两实例可测形态 = 第二实例经**配置源传播** closed（非直调 setClosed）后由注入时钟推进至到期观察 fail-open。拒绝配置源回写备选：回写涉 IConfigProvider 持久化面 = nop-api-core SPI 公共面扩张，超出本批 PA 授权且须全实现回归面重列（裁定④零签名默认分支同款纪律）。

**2.5-C1-010（写门保持机制）**：可见性半边随裁定④承载（WI2.5 C1-round2 证伪记录认账：provider 原地裸换无 volatile 边归平台同案，零独立修复、登记承载声明）；保持机制半边 = **默认随批最小形态**：runVerify 在 lag 静默窗（:230 awaitLagZero）后、三级校验结束（:253 verifyResult 落账）前各复核一次 isClosed，confirmSwitch 在 rebuildRouting 完成后（:279）复核一次；失配即 gateFailed 并落状态行留痕——不引入 token/分布式锁过度设计（审计最低成本形态）。两面均落地，G48 整条归批无悬挂。**R2-15 完成注记口径**：「写门持久化半边已按租约 fail-open 形态闭合」，OrmMigrationOrchestrator 同文件其余缺陷（C1-006/C1-008/C3-008 等）实施时以本批复核后 live 形态为基线。

### 3.6 裁定⑥：协同边界与移交清单

| 协同对象 | 边界裁定 | 移交/消费接口 |
|---|---|---|
| R4-15（8.55，G31） | C4-009 restart 代际令牌、C4-011 destroy 路径有界 join、CL-019 TaskExecutionGraph catch(Throwable)、C1-008 跨键代际原子语义、C10-002/003/009 的 R4-15 成员面（C4-009/C4-011/C2-009/C1-008/C2-007/C2-008/C2-016/C1-020 测试）不越界修复 | 裁定①失败终态/join 原语为其承载前提；C10 成员面拆分表见 plan 勘误 6 |
| R2-21（8.30，4.4 host 批） | 宿主退出码/健康检查联动、quarkus 三绿消费、双宿主启动窗口就绪门禁（4.4-C1-006）不入本批 | nop-ioc 侧失败终态可查询 + standalone 默认传导为消费面；4.4 归属核对无重叠 |
| R2-15（8.24，2.5 peri 批） | 链编排/状态行/PubSub 11 项不入批 | 写门持久化半边完成注记（3.5 口径） |
| plan 2506 | **BeanContainerImpl 零改动承诺由本批解除**（start/asyncStartBeans/handleStartError/awaitStartFinished/getBean0 入 scope）；`BeanScopeImpl.close` 上抛（:100-120）与 `BeanContainerImpl.stop` 转发（:595-617）消费口径维持——handleStartError 内 stop 独立 catch（:528-532）已隔离失败传导对其的影响；BeanScopeImpl 零改动作显式声明 | 插件层零触碰 |
| plan 2496/2497/2504 | 刷新链已修面 + LoadWaitGraph 既有语义零回退（裁定④⑤清单）；LoadWaitGraph 复用为只读消费（register/unregister 既有 API，不改其实现） | — |

**C10 共享条目成员面拆分表（cra-index 回填口径，勘误 6 兑现；三条目均不整条判满）**：

| C10 条目 | 本批成员面（随本体同 diff） | 非 R2-04 面（归属） |
|---|---|---|
| C10-002 | C4-003 / C4-008 失败传导与三形态测试（裁定①） | C4-009 代际竞态、C4-011 destroy join、C2-009 失败广播（R4-15 同 PR 面） |
| C10-003 | C2-001 / C2-002 / C1-002[随主] 等待环测试（裁定②③） | C2-017 P3 javadoc / 带超时重载面（Follow-ups / R4-15 残余） |
| C10-009 | C1-007 / C1-017 发布协议测试（裁定④） | C1-008 / C2-007 / C2-008 / C2-016 / C1-020（R4-15 配置订阅/应用面） |

C10-012（2.5-C1-007 单成员）与 C10-014（C1-010/C7-004 双面均为本批成员）整条入批随裁定⑤验收。

### 3.7 裁定⑦：测试缝与死锁测试纪律

- **死锁纪律（沿 2504 §七）**：daemon 夹具线程 + latch 对齐（强制窗口交错）+ 有界观察窗（观察超时即判红并放行 JVM 退出，禁无限 join）+ 类级 @Timeout 防挂死拖垮整套件 + 环/超时**错误码断言**（`TestXDefRef.testLoop` :73-79 迁移先例）；禁仅类型/修饰符存在断言（「字段已 volatile」不构成通过形态），每件必含修复后可观测行为断言。
- **变异/缺陷形态留痕**：修复前红证据以缺陷形态源码 prefix 运行整类对比留痕（`_tmp/`，2506 C1-010 先例），恢复动作以 grep/读取校验断言可执行行真实存在（变异探针纪律）。
- **C4-003 失败注入 fixture**：failure-injection bean（init/delay 方法按注入类型抛 Exception/Error/阻塞）+ MyCreationLog 有序记录现成件；join 上界经配置键调小实现确定性超时注入（零 sleep 依赖长等待）。
- **C1-007 观测缝**：写者线程经 updateValue 刷新 × 多读者线程有界轮询读 + holder 代际一致性断言（location/provider 恒同代）；撕裂窗断言基于单发布语义（单键值无中间态），显式 await 非 sleep。
- **2.5 集群面单 JVM 可测形态**：两 provider 实例/租约时钟注入（C10-014 建议形态）——租约超时 fail-open 经注入时钟确定性触发；窗口翻转线程 × 校验线程经 latch 交错。
- **既有资产零回退锚**：`TestBeanDepends#testAsyncStartOrder`（:121-136，成功路径 + N5 javadoc 覆盖边界注记随批补）、`TestBeanConcurrentInitDeadlock`（3.3 核对表）、`TestOrmMigrationWriteGate` 5 用例（:49-105）、e2e restart-required（:95）、同线程环检测四类（TestConstructorCycleDetection 族）、N3 测试类。

**新增配置键契约表（命名契约，Phase 2 机械登记；两档语义均沿 LOCK-001）**：

| 配置键 | 落点 | 默认 | ≤0 档语义 | 承载裁定 |
|---|---|---|---|---|
| `nop.ioc.app-beans-container.start-join-timeout` | IocConfigs | 60_000ms | join 不设上界（现行 syncGet 无界语义） | ①（容器启动总预算） |
| `nop.ioc.bean-init.wait-timeout` | IocConfigs | 60_000ms | bean 等待不设上界（现行无界语义） | ②③（BD 有界获取 + PBI 有界等待共用） |
| `nop.orm.migration.write-gate-lease-seconds` | OrmConfigs | 1800s | 关闭租约档（现行永久关门语义，单机/测试缝） | ⑤（租约 fail-open） |

错误码登记（机械登记，英文消息，沿两档错误处理策略）：IocErrors 新增 join 超时 / BD 环等待 / BD 等待超时 / PBI 环等待 / PBI 等待超时五类（环错误携带 `describeChain` 等待链快照，构造守卫防 i18n 重入——`ResourceCacheEntry.ringError` :433-443 先例同构）；OrmErrors 新增租约语义配套面（如租约到期留痕码）；不触碰 CoreErrors 既有三环码。

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|---|---|
| 裁定① join 落 BeanContainerImpl.start 尾部 | 把「提交即返」契约改阻塞，波及插件子容器同步启动链；且 concurrent-start 仅 app 容器可达，IocCoreInitializer 一处即全覆盖，尾部 join 零增量收益 |
| 裁定① 改 NopApplication 入口显式检查 | 扩 Scope 入 nop-boot；自然传播链（3.1 核对表）已使 join 抛出转化为非零退出，显式检查无增量 |
| 裁定① 新增 FutureHelper 带超时 syncGet 重载 | nop-api-core 公共面扩张须独立裁定；deadline 循环在 nop-ioc 侧零触碰承载同等语义 |
| 裁定② 构建期 collectDepends 补收集（自主组 4 原样） | 收集面直通 sortBeans 图 + lowOrderIds 校验 + concurrentStart DAG 三处——跨 order type-injection 现网可启动配置变构建期硬失败（M2 兼容面缺失）；运行期等待覆盖达成同等死锁闭合零兼容破坏 |
| 裁定② 纯 tryLock 有界等待单腿 | 环仅延迟暴露、错误延迟放大（LOCK-001 已拒绝该单腿，沿用其拒绝理由）；本分支为「环检出即时类型化 + 漏检窗有界兜底」双出口 |
| 裁定② IoC 域内聚同构等待图 | 跨域环（bean init × 模型装载）结构性失明 + 第二套环诊断协议；LoadWaitGraph 无状态工具类复用零耦合代价 |
| 裁定② allow-cycle 默认改 false | 构建期拒环成默认行为即兼容翻转；运行期环检出已承载拒环职责，默认翻转无增量收益 |
| 裁定④ 逐字段 volatile | 不能闭合 location/provider 两步写撕裂（审计终裁语）；holder 单发布是撕裂的结构性消除 |
| 裁定④ defaultValue 入 holder | W7 为启动期 main 线程唯一写点、读面主体诊断化（get() null 回落读边与 holder 正交），陈旧面经审计裁定为纯 JMM 理论面；入 holder 徒增 W7 整体替换代价 |
| 裁定⑤ 配置源回写 | IConfigProvider 持久化面 = api-core SPI 公共面扩张，超出本批授权；租约 fail-open 在零 SPI 触碰下消除永久冻结 |
| 裁定⑤ C1-010 token/分布式锁保持机制 | 单 JVM 值比对复核即可闭合窗口翻转无感知面；分布式原语为过度设计且引入新故障面 |
| 裁定⑤ shardSelector 只 volatile 不做读回确认 | 读回确认一行成本把残余可见性异常转化为显式 gateFailed；纯 volatile 下漏检窗静默 |

## 五、与已有设计的关系

- 同目录 [kernel-retirement-generation-and-model-load-locking.md](kernel-retirement-generation-and-model-load-locking.md)：裁定②③直接复用其 LoadWaitGraph 等待图/登记→wouldCycle→类型化环错误→有界兜底三件套与两档超时语义（LOCK-001 锚点，只读消费）；测试纪律（3.7）沿其 §七。
- 同目录 [plugin-lifecycle-and-config-source-hardening.md](plugin-lifecycle-and-config-source-hardening.md)：其 §3.7 对 R2-04 的「nop-ioc 零改动承诺」由本批解除（裁定⑥）；其裁定④⑤落地的 close 互斥与构造序为裁定④零回退清单成员。
- 同目录 [bindcancel-context-unification.md](bindcancel-context-unification.md)：裁定④ SPI 回归面全列 + 零接口签名默认分支沿其 §3.5 纪律（2499 先例）。
- **R2-04 域内**：R1-01/R1-02 刷新链（plan 2496/2497）为本批 C1-007 holder 化的下游已修面，零回退义务成文；`nop.core.component.resource-cache.load-wait-timeout`（2504）为超时键命名与两档语义的命名空间先例（本批新键落 nop.ioc 前缀，不共享键）。
- **owner doc 去向预登记（Phase 3 落账）**：`docs-for-ai/02-core-guides/ioc-and-config.md` **新建**「IoC 启动图与配置发布协议并发契约」节（join/失败终态契约、等待图环检出与有界等待契约、holder 单发布协议、写门租约/复核契约——派生摘要标注「以本设计文档为准」）；`docs-for-ai/04-reference/source-anchors.md` **扩展** IOC/CFG 锚点族 + 新建配置发布协议锚点条目。

## 六、成员 → 裁定映射

| 成员（判级/台账） | 面 | 裁定 | 修复位（live : 行） | 回归测试（预定名） |
|---|---|---|---|---|
| 2.2-C4-003（P1 门控终裁维持；←C4-008 三形态并案） | concurrent-start 无 join + 失败不传导 + 三形态 limbo | ① | IocCoreInitializer :44-51（join）；BeanContainerImpl start :502-512 / handleStartError :526-533 / awaitStartFinished :536-539；IocConfigs（join 键）；IocErrors（超时码） | TestIocConcurrentStartFailurePropagation |
| 2.2-C2-001（P1；←C2-012 E4/E5/E9/E10 并案） | 不可见边跨线程 BD monitor 互等死锁 | ② | BeanContainerImpl getBean0 :373-434（BD 入口登记/有界获取）；LoadWaitGraph 消费；IocErrors（环/超时码） | TestBeanDefCrossThreadCycleDetection |
| 2.2-C2-002（P1；←C1-002 跨维度并案） | PBI 双 owner 互等 wait() 无超时 | ③ | ProducedBeanInstance runUntil :114-170 / waitQuietly :194-201；IocConfigs（bean-init 等待键） | TestPbiWaitBoundedAndCycleSniff |
| 2.2-C1-007（P1 族案，←C1-017 并入） | 三字段裸写裸读 + 两步写撕裂 | ④ | DefaultConfigReference :14-57（holder 化）；AbstractConfigProvider W4/W5/W6 适配；DefaultConfigProvider W1/W2/W3/W7 适配 | TestConfigReferencePublishProtocol |
| 2.5-C1-007（P1） | shardSelector 热替换可见性 + 非安全发布 | ⑤ | SessionFactoryImpl :70/:195/:198-199（volatile）；ConfigShardSelector :57-60（final 化）；OrmMigrationOrchestrator rebuildRouting :424-449（读回确认） | TestShardSelectorHotSwapVisibility |
| 2.5-C7-004（P1） | 写门开门单 JVM 作用域无自愈 | ⑤ | OrmMigrationWriteGate :62-65（租约形态）；OrmConfigs（租约键）；completeSwitch :289-303 / abort :308-325 适配 | TestWriteGateLeaseFailOpen |
| 2.5-C1-010（P2，G48a 搭车） | 写门瞬时检查无保持 + 可见性半边 | ⑤（+④承载） | OrmMigrationOrchestrator runVerify :217-258 / confirmSwitch :264-284（窗口复核）；可见性半边零独立代码（登记承载） | TestWriteGateWindowRecheck |

并案回归归属：C4-008 三形态随 C4-003（TestIocConcurrentStartFailurePropagation 的 F2/F5/F4 三腿）、C2-012 不可见边族随 C2-001（等待登记覆盖断言）、C1-002 随 C2-002（双 owner 环腿）、C1-017 随 C1-007（W4/W5/W6 hostId/写-写竞争腿）；C10 成员面 = C10-002（裁定①）/ C10-003（裁定②③）/ C10-009（裁定④）/ C10-012（裁定⑤ shardSelector）/ C10-014 双面（裁定⑤写门），全部随对应工单同 diff；C10-002/003/009 仅成员面判记，R4-15 面不越界。

## 七、测试纪律契约

夹具纪律沿 §3.7（daemon + latch + 有界观察窗 + 类级 @Timeout + 错误码断言 + 变异留痕）。红绿形态表（E2 可追）：

| 成员 | 修复前红形态（可观测） | 修复后绿形态（可观测） |
|---|---|---|
| C4-003 | concurrentStart(true) + 失败注入：进程面 run-success 姿态可查询但无失败信号（handleStartError 后容器 NOT_STARTED 而调用方无感知） | join 抛出/失败终态可查询；F2/F5/F4 三腿各自类型化收敛（Error 孤儿→超时兜底失败终态；回调异常→非静默；挂死→超时类型化） |
| C2-001 | 双线程互为 type-injection 环并发首触：@Timeout 窗内 BLOCKED 挂死（测试超时红） | 有界窗内类型化环错误/超时错误收敛，无 BLOCKED 残留；factoryBean 环 fixture 同型 |
| C2-002 | 双 owner 互等环（init 期 includeCreating=false 交叉获取）：@Timeout 窗内挂死 | 类型化环错误或有界超时收敛；TestBeanConcurrentInitDeadlock 全断言零回改 |
| C1-007 | 刷新写 × 并发读：陈旧读观测/holder 代际撕裂组合观测 | 有界窗内读者读到新值；location/provider 单代际一致性断言恒真；7 写点逐点冒烟经 holder 路径 |
| 2.5-C1-007 | rebuildRouting 热替换后并发读者读旧 selector 观测 | 有界窗内读到新 selector；读回确认失配走 gateFailed；策略表不可变断言；e2e restart-required 断言零回改 |
| 2.5-C7-004 | 开门仅本 JVM：第二 provider 实例冻结不自愈（两实例形态） | 租约超时（注入时钟）fail-open + 告警 + 状态行留痕；开门后新写放行；TestOrmMigrationWriteGate 5 用例零回归 |
| 2.5-C1-010 | VERIFY 窗口内翻转线程 × 校验线程交错：passed 落账观测（失配被掩盖） | 复核命中失配 gateFailed + 状态行留痕；confirmSwitch 窗口同型 |

**端到端两链路**（rule 22）：①IoC 并发启动→失败传导→容器终态——concurrentStart 全链：start → 图执行 → 失败注入 → 失败终态 → join 感知 → 已建 bean 销毁 → stop 可重入（入口 = 容器 start，出口 = 调用方可观测失败终态）；②配置发布→刷新链下游——updateConfigValue/applyChange 写 → holder 单发布 → 下游读者（写门 isClosed / CFG_ORM_SHARD→selector）读到新值（发布协议从写点入口到下游出口完整走通）。**接线验证**（rule 23）：join 在 IocCoreInitializer 启动序真实执行（非测试直调 awaitStartFinished 自证）；环检出/等待登记在 getBean0/runUntil 等待路径真实注册断言；holder 单发布路径被 W1/W5/W2 真实调用断言（计数器/链路断言）。**无静默跳过**（rule 24）：join 超时/环检出/等待超时全部类型化抛出；租约 fail-open 告警 + 留痕而非静默开启；门禁复核失配 gateFailed 而非跳过校验；C4-008 兜底路径无空 catch。
