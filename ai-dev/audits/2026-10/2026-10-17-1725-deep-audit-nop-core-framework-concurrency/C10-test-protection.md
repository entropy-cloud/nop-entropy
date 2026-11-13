# C10 维度（测试保护力）— nop-core-framework 六子模块 + nop-utils 并发·异步·资源专项审计

- **审计单元**: nop-core-framework（nop-ioc / nop-config / nop-plugin / nop-security / nop-log / nop-boot）+ nop-utils（全部子模块）的 `src/test` 测试套件
- **仓库路径**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`，HEAD `6dceb20dc3`）
- **输入口径**: 同目录 C1/C2/C4/C6 四维文件 `## 最终保留项`（+维度复核结论节），合计 **保留 59 条（P1×11）**：
  - C1 保留 19：P1 族案 ×4（条目 ×5：C1-007+并入 C1-017、C1-009、C1-010、C1-021）、P2×10、P3×4
  - C2 保留 15：P1×2（C2-001、C2-002）、P2×8、P3×5
  - C4 保留 11：P1×1（C4-003 终裁门控维持）、P2×6、P3×4
  - C6 保留 14：P1×4（C6-001/002/003/004）、P2×9（C6-005/006/010 为 cross-ref 条件性）、P3×1
- **判定纪律**: 只读；未修改任何测试/产品代码与四维文件。矩阵判定逐条实际打开测试文件核对断言语义，非仅凭 grep 命中。
- **三档判级口径**:
  - **有钉** = 存在多线程/并发时序测试方法，缺陷类回归时该测试方法必然失败（给出 `测试类#方法名`）；
  - **部分** = 存在单线程功能基线或同机制邻接钉（缺陷窗口——并发交错、失败路径、可见性面——无任何断言）；
  - **无保护** = 无任何测试触达缺陷窗口，缺陷从引入到修复全程测试套件零感知。

---

## 一、测试资产盘点

### 1.1 模块 × 测试面总表

| 目录 | 测试文件数 | @Test 方法数 | 被测面归类 | 并发原语使用 |
|------|-----------|-------------|-----------|-------------|
| nop-ioc | 71（21 测试类 + 50 bean fixture） | 72 | ioc 容器：解析/注入/循环检测/prototype/XPL bean/AOP codegen/depends 拓扑 | **真多线程仅 1 文件**（TestBeanConcurrentInitDeadlock，CountDownLatch+门闩+线程状态轮询）；TestBeanDepends#testAsyncStartOrder 用 concurrentStart(true)（单线程驱动）；TestPrototypeCycleDetection 仅注释提及 |
| nop-config | 6 | 36 | config：file 源刷新保护(3)/walk(1)、订阅(5)、表达式(21)、profile(4)、加密 enhancer(2) | 零（全单线程；TestFileConfigSourceRefresh 直调 refreshConfig()，refreshInterval=0 不经定时器） |
| nop-plugin | 42（13 测试类 + 29 fixture） | 94 | plugin：api 默认方法(6)、manager 状态机/reconcile/热重载/jar 轨/HTTP resolver(73)、support AbstractPlugin 双轨(15) | **真多线程仅 1 方法**（TestPluginLifecycle#testIdempotentActivateDoesNotRerun 并发单飞段） |
| nop-security | 4 | 9 | security：KeyManager 签名/口令清零、CompositeKeyManager 聚合、JWKS、RSA helper | 零 |
| nop-log | 1（仅 nop-log-logback） | 1 | log：logback sessionId | 零；nop-log-core / nop-log-java / nop-log-log4j2 **零测试** |
| nop-boot | 1 | 3 | boot：StartupInfoLogger 值脱敏 | 零；NopApplication/生命周期面零测试 |
| nop-utils | 26 | 179 | utils：router(36)、diff(70)、fsm(12)、git(6)、image(1)、java-parser(20)、match(3)、shell(18)、table-validator(13) | 仅 java-parser 压测格式化用线程池（与审计面无关）；**nop-commons-java21 无 src/test 目录（0 文件 0 方法）** |
| **合计** | **152** | **394** | — | 审计面真多线程测试方法 **3 个** |

### 1.2 按被测面的空洞登记

1. **nop-utils/nop-commons-java21：整个模块无 src/test**——VirtualThreadTaskExecutor（C1-014/C4-004/C6-010 三重登记站点）零保护的结构性根因。
2. **nop-config 四类零覆盖**：JdbcConfigSource（全仓测试仅在 TestFileConfigSourceRefresh 头注释中被"对齐"提及一次）、DefaultConfigProvider（applyChange）、DefinitionConfigProvider（subscribeChange/updateConfigValue）、SingleThreadConfigExecutor——C1/C2/C4/C6 共 12 条保留发现落在这四个类上。
3. **nop-ioc 生命周期面零覆盖**：BeanContainerImpl 的 start 守卫/stop/restart/启动失败语义、BeanScopeImpl 销毁序、IocCoreInitializer、ProducedBeanInstance 失败广播，全部无测试（现有 72 方法集中于解析与功能注入；测试源码 grep `restart|awaitStartFinished` 仅命中 TestBeanDepinds 一处单线程调用）。
4. **nop-plugin 并发窗口零覆盖**：load/unload × reconcile 交错、deactivate 失败路径、jar 轨 computeIfAbsent 并发装载，均无多线程测试（唯一的并发单飞测试只覆盖 activate×activate）。
5. **nop-shell 失败路径零断言**：TestShellRunner 3 方法 = happy path（testTask）+ 进程创建前报错（testUnknown）+ stdin 关闭（testRunWithInput）；无异常/中断/拒绝路径的进程存活断言。
6. **nop-git / nop-image 工具类失败路径零断言**：GitRepositoryImpl 无 close/泄漏测试；ImageCompressHelper 单方法 happy path。
7. **nop-log 非采样模块**（四维零立案）+ nop-boot 仅脱敏面——空洞属实但当前无缺陷落点，登记为低优先观察项，不立缺保护发现。

---

## 二、保护力矩阵

**口径说明**：59 条保留发现 → 58 行（C1-017 并入 C1-007 行；C1-011/012 在 P1 族案行注明并案、P2 表中保留独立行）。P1×11 逐条单列（II.a），P2/P3 同族归组（II.b）。「有钉」判定反向验证抽查 3 条见 II.c。

### II.a P1 逐条详表（11 行，不允许归组）

| # | 缺陷 ID | 缺陷要点 | 判级 | 依据（实际核对） |
|---|---------|---------|------|-----------------|
| 1 | **C1-007 + C1-017（并入）** | DefaultConfigReference location/provider/defaultValue 裸写裸读、刷新写无 HB、hostId 运行期写-写竞争、两步写序撕裂 | **部分** | 功能面有单线程基线：`TestConfigExpressionResolver`（21 方法，经 SimpleConfigProvider/DefaultConfigReference fixture 解析引用值）、`TestMultiConfigVarExpression`、`TestAppBeansFilter`、`TestBeanConditionUnlessProperty`（均经 assignConfigValue 走被flag的写路径）。**零并发/零可见性测试**：无任何测试在刷新线程写的同时从其他线程读引用值；无 wrapper 链（StaticValue/占位符）刷新可见性断言。注：跨单元消费者 OrmMigrationWriteGate 回归面在 nop-orm 辖区，本单元套件不覆盖 |
| 2 | **C1-009** | JdbcConfigSource.close() 不持 monitor 且 connection 裸读写：close 与在途刷新竞态→连接重建后泄漏 | **无保护** | JdbcConfigSource **全单元零测试**（grep 实证：唯一命中为 TestFileConfigSourceRefresh.java:5 头注释文字）。无 close×refresh 交错测试，无连接关闭断言 |
| 3 | **C1-010（含 C1-011/012 并案）** | VfsPluginDefinition.unload/load 在 lifecycleLock 外迁移 + 不持 reconcileLock：unload 与 reconcile 自动激活竞态→孤儿活容器；跨定义门控 TOCTOU | **部分** | 有相邻钉：`TestPluginLifecycle#testIdempotentActivateDoesNotRerun` 后半段以 2 线程并发 activatePlugin 钉死激活单飞（activatedCount==1，两调用均 true）——这是全单元唯一的插件真并发测试；`TestPluginLifecycle` 其余 13 方法 + `TestAbstractPlugin`（15 方法）+ `TestCoeffectReconcile`/`TestReconcileTopologicalOrder` 单线程钉死六态状态机、reconcile 固定点、门控级联的顺序语义。**缺陷窗口零覆盖**：load/unload 与 reconcile 的并发交错、unload×activate TOCTOU、跨定义门控竞态无任何多线程测试（tearDown 全部单线程顺序清理） |
| 4 | **C1-021** | JdbcConfigSource 定时刷新回调无异常防护→任一 onChange 抛出即永久静默杀死 DB 配置热更新链 | **无保护** | JdbcConfigSource 零测试（同 #2）。同型文件源保护已被钉：`TestFileConfigSourceRefresh#testRefreshErrorDoesNotKillNextRefresh` 证明该缺陷形态在本套件可测（坏载荷存活断言模式现成），jdbc 侧未被移植 |
| 5 | **C2-001** | beanDef 锁序环：type-injection/factoryBean 依赖边不入拓扑图→跨线程并发创建在 beanDef monitor 上互等死锁 | **无保护** | 无跨线程并发创建测试。`TestBeanConcurrentInitDeadlock` 针对的是 BCC/PBI 等待链（ ProducedBeanInstance 面），不经 beanDef 锁序；`TestBeanDepends#testAsyncStartOrder` 虽开 concurrentStart(true) 但为双 bean 顺序 happy path，无 type-injection/factoryBean 交叉边、无死锁面断言；`TestConstructorCycleDetection`/`TestBeanParentLoop` 为同线程环检测 |
| 6 | **C2-002** | ProducedBeanInstance 跨实例等待环：init 期交叉获取 + wait() 无超时→双 owner 互等 | **部分** | 有机制级钉：`TestBeanConcurrentInitDeadlock#testConcurrentInitNoDeadlock`（@Timeout(30s)）多线程钉死 owner+wait/notify 状态机的 2026-08-20 死锁形态（T1 持 ctxA 早取 raw P ↔ T2 作 init owner 于 D 构造内阻塞；旧代码必死锁、新代码收敛 + resolvedDepends 契约断言）。**残余缺陷窗口零覆盖**：双 owner 互等环（两线程各自 init 期交叉获取下游 bean）、wait() 无超时挂死可见性——现有场景为单 owner + 早取者，不构造环 |
| 7 | **C4-003（终裁 P1 门控维持）** | concurrent-start：启动 bean 任务体占共享 globalWorker、框架级无 join 点、启动失败异步吞掉后进程带死容器"成功"存活 | **部分** | `TestBeanDepends#testAsyncStartOrder` 是 concurrentStart(true) 的唯一用例：经 BeanContainerBuilder 开门控 → start → awaitStartFinished → 断言 ioc:after 创建序——钉死**成功路径**的门控贯通与拓扑保持。**缺陷窗口零覆盖**：无任何测试注入启动失败 bean 验证失败传导、无 main 线程与启动图 join 语义测试、无"失败后容器状态/进程存活"断言（handleStartError→stop 路径零触达） |
| 8 | **C6-001** | jar 轨插件加载失败路径泄漏 PluginClassLoader 与 JAR 文件句柄 | **无保护** | 失败路径有 **entry 级**钉：`TestPluginManager#testCompatStartFailureCleansUpAndDoesNotKeepEntry`（断言 start 失败后 getLoadedPlugins 为空 + stop 被调用）、`TestPluginUnloadFailure#testMissingPluginJsonFailsWithExplicitError`（断言无残留 entry）、`TestHttpPluginResourceResolver#testManagerLoadPluginFailsOnChecksumMismatch`（断言无半加载 entry）。**但无一断言 classLoader 已关闭/JAR 句柄已释放**——C6-001 泄漏形态（entry 已清而句柄滞留）与全部现有断言正交，缺陷在位时测试全绿 |
| 9 | **C6-002** | 插件 deactivate/activate 失败路径状态机永久卡死，半停容器引用置空不可达（4 同族站点） | **无保护** | 失败路径钉均不在 deactivate 面：激活失败→FAILED 可重试（`TestPluginLifecycle#testActivationFailureSetsFailedWithReadableError`、`TestAbstractPlugin#testStartPropagatesActivationFailureExplicitly`）；unload 段 stop 失败→可重试（`TestPluginUnloadFailure#testUnloadFailureKeepsClassLoaderUsableAndRetryable`）。**deactivate 段 stop 抛错→doDeactivate 卡死**（缺陷本体）零测试：无任何测试注入 deactivate 失败并断言状态收敛；TestPluginLifecycle/TestReloadPlugin 的 tearDown 对 deactivate 异常 catch-and-ignore，即使踩中也无断言 |
| 10 | **C6-003** | 配置源构造器"先调度后加载"：初始加载失败泄漏永久定时刷新任务（jdbc 源附带连接重建循环） | **无保护** | 构造器失败路径零测试。`TestFileConfigSourceRefresh` 的桩 `ScriptedFileConfigSource` 以 `refreshInterval=0L` 构造（AbstractFileConfigSource.refreshInterval>0 才调度——源码 L44-46 核实），**从不触达定时器调度与 close()**；构造期 loader==null 恒返回空表，初始加载永不抛。JdbcConfigSource 零测试。无任何"构造抛出后 future 已取消"形态断言 |
| 11 | **C6-004（修复与 C6-014 同 diff）** | ShellRunner 非 timeout 异常/中断路径不销毁已创建子进程，进程与管道读取线程泄漏 | **无保护** | `TestShellRunner` 3 方法均无进程资源断言：testTask happy path；testUnknown 的 `_unknown_call` 在命令解析期抛错（未达进程创建）；testRunWithInput 验证 stdin 关闭语义（timeout 路径 happy）。无"run 中途异常/中断后子进程存活"探针，无进程树/流关闭断言 |

**P1 判级统计**：有钉 0 / 部分 4（C1-007、C1-010、C2-002、C4-003）/ 无保护 7（C1-009、C1-021、C2-001、C6-001、C6-002、C6-003、C6-004）。

### II.b P2/P3 归组表（47 行 / 47 条；59 条 − P1 行覆盖的 12 条 = 47，C1-017 已并入 P1 行）

| 组 | 缺陷 ID（级） | 判级 | 依据（实际核对） |
|----|--------------|------|-----------------|
| C1 组 | C1-001（P2）singletonScope 非安全发布 + start/stop 复合状态窗口 | 无保护 | 无 restart 期间并发 getBean 测试；无并发启动/停止交错测试（ioc 测试 grep 无 restart 站点） |
| | C1-002（P2）concurrentStart 跨实例 monitor 等待环死锁 | 部分 | 同机制邻接钉 = TestBeanConcurrentInitDeadlock#testConcurrentInitNoDeadlock（BCC/PBI 等待链不死锁）；环本体（concurrentStart 下多 bean 交叉等待）无测试 |
| | C1-004（P2）subscriptionCleanup 锁外 check-then-act→监听泄漏+死 bean 复活 | 无保护 | DefinitionConfigProvider 零直接测试；并发订阅/退订无测试 |
| | C1-005（P2）makeContainerScope 上下文属性 check-then-act | 无保护 | BeanScopeContextImpl 全单元零测试引用 |
| | C1-008（P2）applyChange 双 volatile 代际撕裂 | 无保护 | DefaultConfigProvider.applyChange 无直接测试；TestFileConfigSourceRefresh 钉的是源级快照回写（另一层），provider 代际面零覆盖 |
| | C1-011（P2）AbstractPlugin 裸字段 + 锁外迁移（C1-010 并案） | 部分 | `TestAbstractPlugin` 15 方法单线程钉死全状态机（含 start 幂等不重建容器=容器泄漏钉、FAILED 态收敛）；并发可见性面零覆盖 |
| | C1-012（P2）插件激活态复合读 TOCTOU（C1-010 并案） | 部分 | requireLoaded/getScope/requireActiveScope 的单线程语义经 TestPluginLifecycle/TestAbstractPlugin 覆盖；并发 unload×getService 无测试（ getService INACTIVE 快速失败为单线程钉） |
| | C1-014（P2）VirtualThreadTaskExecutor config 逃逸/stats null/refreshConfig 空体 | 无保护 | nop-commons-java21 无测试目录，结构性零 |
| | C1-018（P2）namesToPluginIds 判空摘键三步非原子→门控静默失效 | 部分 | requires 门控功能面有单线程钉（TestCoeffectReconcile#testActivatePluginReturnsFalseWhenGateUnsatisfied、testReconcileWiringAndDependencyCascade）；并发 unregister 孤儿化窗口无测试 |
| | C1-019（P2）reloadPlugin remove→put 窗口→双活定义+订阅清理丢失 | 部分 | `TestReloadPlugin` 4 方法单线程钉死 reload 语义全链（端到端重放/门控关闭/失败可恢复/jar 轨显式失败）；并发 loadPlugin×reloadPlugin 窗口无测试 |
| | C1-006（P3）getClassIntrospection 非原子惰性初始化 | 无保护 | 无并发首调测试（与 C2-011 同站） |
| | C1-015（P3）registerGlobalWorker 重复注册替换旧全局执行器 | 无保护 | VirtualThreadTaskExecutor 零测试（同 C1-014） |
| | C1-016（P3）DefaultKeyManager.destroy 置空竞态 | 部分 | `TestKeyManager#testDestroyZeroesStorePassword` 单线程钉死 destroy 清零契约（F-C1-3 回归）；destroy×在途密钥访问并发窗口无测试 |
| | C1-020（P3）updateConfig ACTIVATING 窗口热应用静默丢失 | 部分 | `TestPluginLifecycle#testConfigDomainDefinitionLevel` 钉死 LOADED 缓存 + ACTIVATED 热应用 + 全局无污染；ACTIVATING 过渡窗（六态探针测试已能观测该态但未在窗内注入 updateConfig）零覆盖 |
| C2 组 | C2-003（P2）start 守卫非原子 + startFuture 非 volatile | 无保护 | awaitStartFinished 仅 TestBeanDepends 单线程调用一次；并发 start×awaitStart 丢失唤醒形态无测试 |
| | C2-004（P2）插件锁外状态转换→孤儿激活；AbstractPlugin.state 补 volatile | 部分 | 顺序状态机有全量钉（TestPluginLifecycle 六态探针可观测 ACTIVATING/DEACTIVATING）；锁外交错窗口无并发测试 |
| | C2-005（P2）jar 轨 computeIfAbsent 映射内 HTTP+SHA256+类加载+start 长临界区 | 部分 | jar 轨功能链有端到端钉（TestPluginUnloadFailure、TestHttpPluginResourceResolver#testManagerLoadPluginJarTrackEndToEnd）；并发装载互斥长临界区/递归 computeIfAbsent 无测试 |
| | C2-006（P3）reconcileLock 保护矩阵缺口（钉死契约防回归） | 部分 | reconcile 语义有大量单线程钉（TestCoeffectReconcile 7、TestReconcileTopologicalOrder 6、TestPluginLifecycle#testPluginContextRegistry）；契约"矩阵"本身（哪些操作持锁）无测试可表达，交错面零覆盖 |
| | C2-007（P2）DefinitionConfigProvider.subscribeChange 清理闭包 check-then-act→孤儿 list 失聪 | 部分 | 底层结构有钉：`TestChangeSubscriptions` 5 方法（通配契约/退订/坏监听器隔离，全单线程）；provider 清理闭包的 isEmpty→remove 并发窗口无测试 |
| | C2-008（P2）DefaultConfigProvider.applyChange 无锁复合操作 | 无保护 | applyChange 并发入口零测试（同 C1-008，provider 层零直接测试） |
| | C2-009（P2）Error 逃逸无失败广播→后续获取者永久 wait() | 无保护 | TestBeanConcurrentInitDeadlock 全部断言基于成功收敛路径；init 抛 Error 的广播语义无测试 |
| | C2-010（P3）PluginScopeImpl effect 取消闭包锁内 dispose | 部分 | effect 顺序语义有钉（TestPluginLifecycle#testActivatorWiringAndEffectQuiescence LIFO+quiescence、testScopeContract close 幂等/close 后拒注册）；dispose 死锁面无并发测试 |
| | C2-011（P3）getClassIntrospection 重复实例（良性记录） | 无保护 | 同 C1-006 |
| | C2-012（P2）不可见边族扩展（ignore-depends/表达式动态边/运行期再解析） | 部分 | resolvedDepends 边有钉（TestBeanDepends 13 方法含 testRefTopologyFilter/testNoFalsePositiveOnValidOrder + testAsyncStartOrder 并发下拓扑保持）；ignore-depends 逃逸边与表达式动态边在 concurrentStart 下无测试 |
| | C2-013（P2）AOP 拦截器 bean 边不入拓扑图 | 无保护 | `TestAop` 3 方法为 codegen/反射/动态生成功能钉，**不使用 concurrentStart**——拦截器未初始化注入缺陷窗口（并发启动）不可达于现有测试 |
| | C2-016（P3）updateConfigValue 两段锁→键复活 | 部分 | 定义级 updateConfig 顺序语义有钉（TestPluginLifecycle#testConfigDomainDefinitionLevel、TestReloadPlugin 重放断言）；并发 updateConfigValue×摘除窗口无测试 |
| | C2-017（P3）includeCreating 语义与无界等待契约零声明 | 部分 | includeCreating=true 早可见性有行为钉（TestBeanConcurrentInitDeadlock Step1 assertSame raw P）；awaitStartFinished 无界等待、半成品可见契约无测试断言（javadoc 类缺陷，测试仅能钉行为面） |
| C4 组 | C4-001（P2）文件遍历/无超时 JDBC 查询直连 globalTimer | 无保护 | 定时任务路径零测试（TestFileConfigSourceRefresh 不经定时器；JdbcConfigSource 零测试）；无任何 globalTimer 占用面观测测试 |
| | C4-002（P2）close 与在途刷新竞态→关闭后重建永不关闭的 Connection | 无保护 | 同 C1-009（JdbcConfigSource 零测试） |
| | C4-004（P3）VirtualThreadTaskExecutor 治理四面 | 无保护 | 零测试目录（同 C1-014） |
| | C4-005（P3）stderr 阻塞读占共享无界池；timeout=0 挂起泄漏 | 无保护 | TestShellRunner 无 stderr 洪泛/timeout=0 挂起场景，无线程存活断言 |
| | C4-006（P3）SingleThreadConfigExecutor 参数虚假/拒绝误标/停机竞态/stop 未 start NPE | 无保护 | SingleThreadConfigExecutor 零直接测试（TestConfigStarterProfiles 仅测 profile 解析） |
| | C4-007（P2）用户 refreshConfig 与监听器串行搁浅单线程 config-executor | 无保护 | subscribeConfigChange/onRefreshConfig 链路零测试（grep 实证零命中） |
| | C4-008（P2，门控）完成回调族三形态吞错（Error 孤儿/成功回调异常静默/无超时 pending） | 无保护 | testAsyncStartOrder 仅成功路径；启动失败注入、Error 任务、超时语义零测试（设施根因 CL-019 在 nop-kernel 辖区同样零覆盖，见 §五） |
| | C4-009（P2，双门控）restart 代际竞态：旧图 whenComplete 自毁新生代 | 无保护 | restart() 全单元零测试调用（AutoTestCase/JunitBaseTestCase 面在 nop-autotest，非本单元） |
| | C4-010（P2）singleton 销毁序=CHM 哈希序 | 无保护 | `TestBeanContainer#testDestroy` 仅断言单 bean 销毁布尔；多 bean 销毁序 vs 依赖序无断言（MyCreationLog 有序记录设施已存在但未用于销毁序） |
| | C4-011（P3，门控）destroy 路径不 join 在途启动图 | 无保护 | CoreInitialization.destroy/IocCoreInitializer 销毁序零测试（各测试类 @AfterAll destroy 均为安静路径） |
| C6 组 | C6-005（cross-ref→C1-021，随 P1）变更分发保护块外+监听器异常杀链 | 无保护 | 同 C1-021（JdbcConfigSource 零测试；文件源保护面对称钉在 TestFileConfigSourceRefresh#testFailingListenerDoesNotBlockOthers 形态可移植） |
| | C6-006（cross-ref→C4-010）singleton 销毁哈希序 | 无保护 | 同 C4-010 |
| | C6-007（P2）destroy 链异常传播（destroyMethod 抛错跳过 ioc:destroy + 末异常覆盖） | 无保护 | 无 destroyMethod 抛错测试 fixture（test/io/entropy/beans 下无 failing-destroy bean；TestBeanContainer#testDestroy 为 happy path） |
| | C6-008（P2）停机期 bean 解析双重失效（访问位先灭+指针先切） | 无保护 | 无 stop 期间并发 getBean 测试 |
| | C6-009（P2）onContainerStop 关 scope 不摘注册条目→ALREADY_CLOSED 永久化 | 部分 | scope 自身契约有钉（TestPluginLifecycle#testScopeContract：close 幂等、close 后 effect 拒注册）；容器重启后条目复活/上下文滞留面零测试（onContainerStop 链路零触达） |
| | C6-010（cross-ref→C4-004/WI 2.1）VirtualThreadTaskExecutor 生命周期三面残留 | 无保护 | 零测试目录（F3⑥ 增量面同样零覆盖） |
| | C6-011（P2 条件性）IPluginManager 无聚合关闭路径 | 无保护 | 无相关测试；**反向证据**：TestPluginLifecycle/TestReloadPlugin/TestPluginUnloadFailure 的 tearDown 均被迫手写逐插件 deactivate+unload 循环——测试代码自身在为该 API 缺口买单 |
| | C6-012（P2 理论）GitRepositoryImpl close 责任外置 | 无保护 | TestGitRepositoryImpl 4 方法全为 diff/content 功能测试，无 close/泄漏断言 |
| | C6-013（P3）ImageCompressHelper 异常路径 ImageWriter 不 dispose | 无保护 | 唯一测试 testTransparentPngCompressedWithWhiteBackground 为 happy path，无损坏输入/中途失败用例 |
| | C6-014（P2 条件性，与 C6-004 同 diff）进程创建与内层 try 间失败窗口 | 无保护 | 同 C6-004（调度器/线程池拒绝注入零测试） |

### II.c 「有钉/部分」判定反向验证抽查（3 条）

1. **TestBeanConcurrentInitDeadlock#testConcurrentInitNoDeadlock**（支撑 C2-002/C1-002 的"部分"判级）：反向核对通过——若 ProducedBeanInstance 回退至旧 wait 语义，T1 永不完成 → L114 `t1.join(15000)` + L117 `assertFalse(t1.isAlive())` 失败，且类级 `@Timeout(30s)` 兜底防挂死整套件；断言含 resolvedDepends 契约（L124）。钉真实有效，但其场景确为"单 owner + ctxA 早取者"，不含双 owner 环——"部分"而非"有钉"判定成立。
2. **TestPluginLifecycle#testIdempotentActivateDoesNotRerun** 并发单飞段（支撑 C1-010/C1-012 的"部分"判级）：反向核对通过——若激活单飞失效（activator 重跑），`AgentInstanceRecorder.activatedCount` 为 2，L279 `assertEquals(1, ...)` 必失败；`done.await()` 保证断言前两线程收敛。但该钉仅约束 activate×activate，load/unload×reconcile 交错无钉——"部分"判定成立。
3. **TestFileConfigSourceRefresh#testRefreshErrorDoesNotKillNextRefresh**（支撑 C1-021/C6-005 的"无保护"判级中"可测性已证明"备注）：反向核对通过——若 refreshConfig 向定时任务泄漏异常，L86 `assertDoesNotThrow` 立即失败；坏载荷后第三轮恢复断言（L92-93）钉死"保留下轮刷新"语义。该钉属文件源（已修复面），jdbc 源零移植——同型缺陷在 JdbcConfigSource 上现有套件零感知，"无保护"判定成立。

---

## 三、缺保护登记（缺保护发现，严重度统一 P2）

> 编号自 `nop-core-framework-C10-001` 起。每条含：缺口描述 / 涉及缺陷 ID / 建议回归测试形态（不含实现代码）。归并原则：同一测试目标族合并立案，全部 11 条 P1 缺口 100% 覆盖，P2 缺口按"整类零测试 > 单窗口缺失"择重并入。

### [nop-core-framework-C10-001] JdbcConfigSource 全类测试真空（close 竞态 / 刷新回调杀链 / 连接生命周期零覆盖）
- **缺口描述**：JdbcConfigSource 在全单元 src/test 中零直接测试（唯一出现为文件源测试头注释的"对齐"文字）。C1-009（close×在途刷新竞态→连接重建后泄漏）、C1-021+C6-005（定时回调无防护→DB 热更新链永久静默死亡）、C4-002（关闭后重建永不关闭的 Connection）三条高危缺陷全部处于零感知状态；而同型文件源的保护面已有成熟钉（TestFileConfigSourceRefresh），证明该缺陷形态在本套件的可测性已被验证但未向 jdbc 轨移植。
- **涉及缺陷 ID**：C1-009（P1）、C1-021（P1）、C6-005（P1 cross-ref）、C4-002（P2）、C6-003（P1，jdbc 面）
- **建议回归测试形态**：以可控 Loader 桩（仿 ScriptedFileConfigSource 模式）构造 JdbcConfigSource：①坏回调存活测试——onChange 注入抛错监听器，断言后续刷新轮仍执行且其余监听器可达；②close×刷新交错测试——后台线程持刷新在途时调 close()，断言连接最终关闭（无重建后滞留），close 幂等；③回调触发/不变更不触发语义钉（对齐文件源三断言）。并发用例以门闩控制进入点，断言收敛而非时序。
- **严重度**：P2

### [nop-core-framework-C10-002] concurrent-start 失败语义与关闭 join 零回归保护
- **缺口描述**：concurrent-start 门控仅有 1 个成功路径用例（TestBeanDepends#testAsyncStartOrder，双 bean 顺序断言）。启动失败的吞没（handleStartError→stop 自毁）、完成回调三形态吞错（Error 孤儿/成功回调异常静默/无超时 pending）、restart 代际竞态、destroy 不 join 在途图——C4 终裁 P1 及其同批修复面（C4-003/C4-008/C4-009/C4-011 + C2-009 失败广播）全部零测试。缺陷让进程"带死容器 run-success 存活"，测试套件对此完全无感。
- **涉及缺陷 ID**：C4-003（P1，部分）、C4-008（P2）、C4-009（P2）、C4-011（P3）、C2-009（P2）、C2-001 的并发面
- **建议回归测试形态**：①失败传导测试——concurrentStart(true) 下注入 init 抛异常（及 Throwable/Error 形态）的 bean，断言 start()/awaitStartFinished 以明确异常收敛（不静默成功）且容器状态可观测；②失败广播测试——init 抛 Error 后第二获取者不得永久 wait（带超时断言）；③join 语义测试——启动图在途时 stop()/destroy()，断言在途任务收敛后再继续销毁链；④restart 代际测试——旧图延迟回调触发时新生代容器不被自毁（代际令牌行为钉）。
- **严重度**：P2

### [nop-core-framework-C10-003] IoC 跨线程等待环（beanDef 锁序环 + PBI 双 owner 环）无死锁回归钉
- **缺口描述**：现存唯一 IoC 多线程测试钉死的是 2026-08-20 已修复形态（BCC/PBI 单 owner 等待链）。C2-001 的 beanDef monitor 互等环（type-injection/factoryBean 边 + 双线程交叉创建）与 C2-002 残余的双 owner 互等环（wait() 无超时）均无测试；一旦回归，套件以测试挂死（而非断言失败）呈现，且无 @Timeout 边界的用例会拖挂整个构建。
- **涉及缺陷 ID**：C2-001（P1）、C2-002（P1，残余环面）、C1-002（P2）、C2-017（P3，includeCreating 早可见行为面可一并钉）
- **建议回归测试形态**：①beanDef 环测试——两 bean 经 type-injection/factoryBean 交叉引用，双线程各自从不同入口并发 getBean，全测试 @Timeout 边界 + 期望收敛（或环检测显式报错）断言；②双 owner 环测试——仿 TestBeanConcurrentInitDeadlock 门闩模式构造两 owner 交叉获取，断言 wait 带超时后以可观测错误收敛而非无限挂起；③沿用线程状态轮询 + 门闩的既有确定性时序基建（TestConcurrentP/D 模式）。
- **严重度**：P2

### [nop-core-framework-C10-004] 插件生命周期并发窗口（unload/load × reconcile 交错与注册表竞态）零覆盖
- **缺口描述**：插件轨唯一真并发测试仅覆盖 activate×activate 单飞。C1-010（unload 与 reconcile 自动激活竞态→孤儿活容器）、C1-018（namesToPluginIds 三步非原子→门控静默失效）、C1-019（reloadPlugin remove→put 窗口→双活定义）、C2-004/C2-005/C2-006（锁外交错/bin 锁长临界区/reconcileLock 矩阵）全部只有单线程顺序钉；C6-011（无聚合关闭）的 API 缺口反被各测试类 tearDown 的手写清理循环证实。
- **涉及缺陷 ID**：C1-010（P1，部分）、C1-011、C1-012（P2，并案残余面）、C1-018、C1-019（P2）、C2-004、C2-005、C2-006（P2/P3）、C6-011（P2 条件性）
- **建议回归测试形态**：①unload×reconcile 交错测试——门控满足的活插件上并发执行 unloadPlugin 与 reconcilePlugins，断言终态单一（无孤儿活容器：注册表无条目 ⇔ 容器已停）；②门控翻转×注册索引并发测试——并发 unload 与 requires 依赖方激活，断言门控判定不静默失效；③reload×load 并发测试——reloadPlugin 进行中并发 loadPlugin 同 id，断言最终单一定义存活且订阅可达；④manager 聚合关闭契约测试（close 后 allPlugins 空、订阅清理、容器停）。
- **严重度**：P2

### [nop-core-framework-C10-005] 插件 deactivate 失败路径状态卡死零回归
- **缺口描述**：失败路径测试全部落在 activate 失败（→FAILED 可重试）与 unload 段 stop 失败（→可重试）两形态；C6-002 缺陷本体——deactivate 段 stop/容器关闭抛错致 doDeactivate 终态置位不可达、状态机永久卡死——零测试。tearDown 的 catch-and-ignore 还会主动吸收该缺陷症状，进一步降低套件敏感度。
- **涉及缺陷 ID**：C6-002（P1）、C2-010（P3，dispose 死锁面）
- **建议回归测试形态**：注入 deactivate 失败的探针插件（子容器 stop 抛错，仿 MockStopControl.failStop 模式作用于 deactivate 段），断言：①deactivate 收敛到明确终态（成功回 LOADED 或显式 FAILED，不可滞留 DEACTIVATING）；②失败后插件可恢复（重试 deactivate 成功或 forceUnload 入口可达）；③scope effects 全量回退（quiescence 在失败路径同样成立）。
- **严重度**：P2

### [nop-core-framework-C10-006] jar 轨加载失败句柄释放（PluginClassLoader/JAR close）零断言
- **缺口描述**：现有失败路径钉全部为 entry 级（"不得残留半加载 entry"），C6-001 泄漏形态（entry 已清而 classLoader/JAR 句柄滞留）与全部断言正交——缺陷在位时套件全绿。注意交互约束：TestPluginUnloadFailure#testUnloadFailureKeepsClassLoaderUsableAndRetryable 钉死"unload 失败路径 classLoader 不得关闭（可重试）"，修复设计须区分"可重试保留"与"终态释放"两类失败，勿使新断言与既有钉冲突（见 N2）。
- **涉及缺陷 ID**：C6-001（P1）
- **建议回归测试形态**：对 loadPlugin 全失败点矩阵（HTTP 校验失败/缺 plugin.json/start 失败终态等）各注入失败，断言失败后经反射/包内可见通道取出的 PluginClassLoader `isClosed()` 为真且 jar 缓存句柄释放（或经由 File/URL 连接不可再读断言）；可重试类失败（unload 段）保持既有"不关闭"钉不回退。
- **严重度**：P2

### [nop-core-framework-C10-007] ShellRunner 进程/线程泄漏路径零断言
- **缺口描述**：TestShellRunner 仅覆盖 happy path 与进程创建前报错。C6-004（非 timeout 异常/中断不销毁子进程）、C6-014（进程创建与内层 try 间拒绝窗口）、C4-005（stderr 阻塞读滞留共享池线程）全部无进程存活/线程收敛断言。
- **涉及缺陷 ID**：C6-004（P1）、C6-014（P2，同 diff 面）、C4-005（P3）
- **建议回归测试形态**：①以长时静默子进程（如 `sleep`）注入运行中异常/中断（关闭 stdout 流、取消 token、执行器拒绝），断言子进程 destroy 被调用（ProcessHandle/`pid` 存活探针）且输出收集线程收敛；②stderr 洪泛 + timeout 场景断言 cachedThreadPool 无滞留线程（池活跃数回归基线）；③进程创建后内层 try 前注入失败（拒绝型执行器桩），断言兜底销毁（覆盖 C6-014 窗口，与 C6-004 同断言族）。
- **严重度**：P2

### [nop-core-framework-C10-008] 配置源构造器"先调度后加载"泄漏（file+jdbc 双轨）零覆盖
- **缺口描述**：C6-003 的构造序缺陷（scheduleWithFixedDelay 先于初始 loadConfig，构造抛出即永久定时任务滞留，源码 AbstractFileConfigSource L42-50 亲核）无任何测试触达：现有文件源测试桩固定 refreshInterval=0（不调度），构造期恒成功；jdbc 轨零测试。构造失败后的 future 取消语义（cancel 后不再触发）同样零断言。
- **涉及缺陷 ID**：C6-003（P1）、C4-001（P2，定时器占用面同路径）
- **建议回归测试形态**：①构造失败测试——注入构造期即抛错的 Loader（refreshInterval>0），断言构造异常抛出的同时刷新任务已被取消（延时后无 refreshConfig 再入，计数器归零断言）；或按修复序"先加载成功再调度"断言失败时不产生任何调度；②close 后不再触发断言（close→等待≥2 个刷新周期→load 计数不变）。
- **严重度**：P2

### [nop-core-framework-C10-009] 配置发布协议可见性/原子性面零并发测试（provider 层整体零覆盖）
- **缺口描述**：C1-007+C1-017（P1 族案，含 hostId/updateConfigValue 运行期写-写竞争与两步写序撕裂）、C1-008/C2-008（applyChange 无锁复合操作代际撕裂）、C2-016（updateConfigValue 键复活）、C2-007（订阅清理 check-then-act）共享同一缺口：配置 provider/引用层零并发测试、provider 层零直接测试。现有单线程功能钉（表达式解析、LOADED/ACTIVATED 热应用）证明写路径功能正确，但对"刷新线程写 × 业务线程读"的可见性与原子性零约束。
- **涉及缺陷 ID**：C1-007（P1）、C1-017（并入）、C1-008、C2-007、C2-008（P2）、C2-016（P3）、C1-020（P3，ACTIVATING 窗口）
- **建议回归测试形态**：①引用刷新可见性测试——多读者线程循环读 getConfigValue，写者线程经 updateValue/assignConfigValue 刷新，断言读者有限步内可见新值（或至少不出现撕裂组合：location/provider/defaultValue 三元组一致性快照断言）；②applyChange 原子性测试——并发 applyChange 与读快照，断言单次读到的键值集来自同代际（无新键配旧值组合）；③订阅并发测试——并发 subscribe/unsubscribe 同 pattern + 触发变更，断言存活订阅可达、退订订阅不可达（孤儿 list 失聪检测）；④ACTIVATING 窗口测试——六态探针于 ACTIVATING 注入 updateConfig，断言不静默丢弃（或明确契约化延迟应用语义）。
- **严重度**：P2

### [nop-core-framework-C10-010] singleton 销毁序与 destroy 链异常传播零回归
- **缺口描述**：TestBeanContainer#testDestroy 仅断言单 bean 销毁布尔；C4-010/C6-006（销毁序=CHM 哈希序，依赖先于依赖方销毁）、C6-007（destroyMethod 抛错跳过同 bean ioc:destroy + 末异常覆盖 + onContainerStop 遮蔽）、C6-008（停机期解析双重失效）、C6-009（容器 stop 后 scope 条目永久 ALREADY_CLOSED）零测试。测试基建已具备条件：MyCreationLog 有序记录设施与失败注入 bean 模式均为现成件。
- **涉及缺陷 ID**：C4-010（P2）、C6-006（cross-ref）、C6-007、C6-008、C6-009（P2）
- **建议回归测试形态**：①销毁序测试——A→B→C 依赖链容器 stop，以 MyCreationLog 断言逆依赖销毁序（C 先于 A）；②destroy 异常聚合测试——bean 的 destroyMethod 与 ioc:destroy 各注入抛错，断言两者均执行且异常聚合上报（非仅末个）；③停机并发解析测试——stop 进行中并发 getBean，断言快速失败于明确错误（非半初始化实例）；④重启后 scope 条目测试——bind 自定义 scope → 容器 stop → 重启，断言 scope 可重新绑定（非 ALREADY_CLOSED 滞留）。
- **严重度**：P2

### [nop-core-framework-C10-011] nop-commons-java21 零测试模块：VirtualThreadTaskExecutor 治理面结构性无保护
- **缺口描述**：nop-commons-java21 无 src/test 目录。C1-014（config 逃逸/stats() 违约 null/refreshConfig 空体）、C4-004（destroy 不对称/命名误导/无观测）、C6-010（生命周期三面残留）、C1-015（registerGlobalWorker 替换旧全局执行器→持有旧引用的提交方被拒绝）在结构上不可能被任何回归测试保护。
- **涉及缺陷 ID**：C1-014（P2）、C4-004（P3）、C6-010（P2 cross-ref）、C1-015（P3）
- **建议回归测试形态**：为 VirtualThreadTaskExecutor 建最小测试类：①getConfig() 返回防御性快照（修改返回值不影响内部配置）；②stats() 契约测试（非 null、字段自洽）；③refreshConfig 生效测试；④destroy 后 submit 语义测试（明确拒绝，不静默）+ registerGlobalWorker 注销后旧引用提交的收敛行为断言。
- **严重度**：P2

### [nop-core-framework-C10-012] 配置执行器/定时器治理面零覆盖（SingleThreadConfigExecutor + 传播链）
- **缺口描述**：SingleThreadConfigExecutor 零直接测试（TestConfigStarterProfiles 仅 profile 解析）；C4-006（queue 参数虚假/拒绝误标/停机窗口/stop 未 start NPE）、C4-007（用户 refreshConfig 与监听器串行搁浅全平台传播）、C4-001（globalTimer 直连 I/O 放大）全部无治理面测试；subscribeConfigChange 链路在测试代码中零引用。
- **涉及缺陷 ID**：C4-001（P2）、C4-006（P3）、C4-007（P2）、C1-004（P2，同在 provider 订阅链）
- **建议回归测试形态**：①executor 生命周期测试——未 start 即 stop 不 NPE、destroy 后 submit 显式失败；②传播隔离测试——订阅一个阻塞监听器 + 一个计数监听器，断言（按修复后语义）单监听器阻塞不搁浅其余监听器/或有界等待后收敛（现契约先钉现状，修复后改断言）；③refresh 体抛错的传播路径断言（入全局定时器调用方的异常去向可观测）。
- **严重度**：P2

### [nop-core-framework-C10-013] DefaultKeyManager destroy 并发窗口零覆盖
- **缺口描述**：C1-016（destroy 与在途密钥访问并发→keyStore/storePassword 置空竞态）仅有顺序 destroy 清零钉（TestKeyManager#testDestroyZeroesStorePassword，F-C1-3）；并发面零测试。
- **涉及缺陷 ID**：C1-016（P3）、nop-core-framework-C10 系列中唯一 nop-security 落点
- **建议回归测试形态**：读者线程循环 getPrivateKey/getCertificate 与 destroy() 并发，断言读者要么成功要么收到明确错误（NPE 不允许），收敛后全部调用显式失败——把"清零安全 + 置空竞态收敛"合并为一条存活/失败两态断言。
- **严重度**：P2

### [nop-core-framework-C10-014] ImageCompressHelper 异常路径资源释放零覆盖
- **缺口描述**：唯一测试为 happy path（透明 PNG 白底合成）。C6-013（异常路径 ImageWriter 不 dispose、流不关闭）的触发面（损坏输入、压缩循环中途失败）零用例。
- **涉及缺陷 ID**：C6-013（P3）
- **建议回归测试形态**：损坏图像字节/中途截断文件输入，断言 compressImageWithLimit 抛出明确异常且（经可观测通道，如临时文件清理或 writer 状态探针）无资源滞留；连续失败调用不累积句柄（可重复执行 N 次不耗尽句柄的冒烟断言）。
- **严重度**：P2

### [nop-core-framework-C10-015] GitRepositoryImpl close 责任面零覆盖
- **缺口描述**：TestGitRepositoryImpl/TestGitRepositoryManager 共 6 方法全为 diff/pull/exists 功能测试；C6-012（每次获取新建 JGit Repository、close 责任外置无配对保障）零资源语义测试。
- **涉及缺陷 ID**：C6-012（P2 理论）
- **建议回归测试形态**：javadoc 契约钉 + 使用方模式测试——按声明的责任划分调用（谁获取谁关闭），以 JGit Repository 句柄可观测通道（重复 close 幂等 / 未 close 收集器探针）断言配对；若修复采用引用计数缓存，断言并发获取同一 repo 返回共享实例且末次 close 后释放。
- **严重度**：P2

### [nop-core-framework-C10-016] 容器 start/stop 守卫与请求域 scope 生命周期并发面零覆盖
- **缺口描述**：C2-003（start 守卫 CTA + startFuture 非 volatile 丢失唤醒）、C1-001（singletonScope 非安全发布 + restart 窗口孤儿单例）、C1-005（makeContainerScope check-then-act）、C6-008 的 start 侧并发面共用零覆盖现状：awaitStartFinished 仅被单线程调用一次，restart 全单元零调用。
- **涉及缺陷 ID**：C2-003（P2）、C1-001（P2）、C1-005（P2）
- **建议回归测试形态**：①并发 start×awaitStart 测试——多线程同时 start/await，断言全部在有限时间内返回且容器可用（@Timeout 边界）；②restart×getBean 测试——restart 期间并发 getBean，断言无孤儿单例（新代际容器实例一致性：同一代内 getBean 同实例，跨代不串）；③共享上下文双线程绑定 scope 测试（门闩控制进 makeContainerScope 临界窗），断言恰一容器作用域建立或显式失败。
- **严重度**：P2

---

## 四、统计

### 4.1 保护力矩阵总账（58 行 = 59 条，C1-017 并入 C1-007 行）

| 判级 | 行数 | 占比 | P1 内分布（11 行） |
|------|------|------|-------------------|
| 有钉 | **0** | 0% | 0 |
| 部分 | **20** | 34.5% | 4（C1-007、C1-010、C2-002、C4-003） |
| 无保护 | **38** | 65.5% | 7（C1-009、C1-021、C2-001、C6-001、C6-002、C6-003、C6-004） |

- **按维度**：C1（18 行）部分 9 / 无保护 9；C2（15 行）部分 9 / 无保护 6；C4（11 行）部分 1 / 无保护 10；C6（14 行）部分 1 / 无保护 13。
- **P1 结论**：11 条 P1 中 **0 条有钉**，4 条部分（其"钉"均为邻接机制钉或成功路径钉，无一约束缺陷窗口本体），7 条无保护。P1 修复的全部回归面当前依赖人工验证。
- **整类零测试站点**（结构性空洞）：JdbcConfigSource、DefaultConfigProvider、DefinitionConfigProvider、SingleThreadConfigExecutor、BeanScopeImpl、IocCoreInitializer、VirtualThreadTaskExecutor（整模块无测试目录）、PluginScopeImpl、DefaultConfigReference（并发面）。
- **审计面真并发测试方法仅 3 个**：TestBeanConcurrentInitDeadlock#testConcurrentInitNoDeadlock、TestPluginLifecycle#testIdempotentActivateDoesNotRerun（并发段）、TestBeanDepends#testAsyncStartOrder；对照 59 条缺陷，覆盖密度约 0.05 测试/缺陷。

### 4.2 缺保护发现统计

- **登记 16 条**（nop-core-framework-C10-001 ~ C10-016），严重度统一 P2。
- 覆盖校验：11 条 P1 的缺口 100% 被引用（C10-001 覆盖 C1-009/C1-021/C6-005/C6-003jdbc 面；C10-002 覆盖 C4-003/C4-008/C4-009/C4-011/C2-009；C10-003 覆盖 C2-001/C2-002/C1-002；C10-004 覆盖 C1-010/C1-011/C1-012/C1-018/C1-019/C2-004~006/C6-011；C10-005 覆盖 C6-002；C10-006 覆盖 C6-001；C10-007 覆盖 C6-004/C6-014/C4-005；C10-008 覆盖 C6-003/C4-001；C10-009 覆盖 C1-007/C1-017/C1-008/C2-007/C2-008/C2-016/C1-020；C10-010 覆盖 C4-010/C6-006/C6-007/C6-008/C6-009）；P2 最重缺口并入 C10-011 ~ C10-016。

---

## 五、CL 注记（维度集外线索的测试保护力单列）

四维 CL 线索均"不立案展开"，其测试保护力状态单列如下，供移交线参考：

| CL | 内容 | 设施辖区 | 测试保护力现状 |
|----|------|---------|---------------|
| CL-001 | 文件源定时刷新回调异常杀链 | 已升格 C1-021（本单元） | 文件源侧**有钉**（TestFileConfigSourceRefresh 三断言）；jdbc 侧无（见 C10-001） |
| CL-004/CL-017 | 虚拟线程全局 worker 无界 / configDependants 级联无环检测 | C4/C8/C6 移交 | 零测试（nop-commons-java21 无测试目录 + config 级联链零覆盖，C10-011/C10-012 相邻） |
| CL-005/CL-006/CL-007/CL-018 | config 队列满载 / destroyBean 二次销毁 / 请求域停机弱一致 / 哈希序销毁 | C4/C6/C8 移交 | 零测试（C10-010/C10-012/C10-016 覆盖相邻面） |
| CL-008 | ShutdownHook 再入守卫反转 + TreeSet 非线程安全 | nop-kernel（WI 2.1 C6-011 已立案） | 本单元零测试；设施单元处置（nop-boot 仅脱敏测试） |
| CL-009 | PluginManagerImpl syncGet 调用线程内联（零立案） | 已终判无缺陷 | TestPluginLifecycle/TestPluginManager 顺序调用面即行为基线，无需并发钉 |
| CL-010/CL-011/CL-012/CL-013/CL-014/CL-015/CL-016/CL-021 | 插件类隔离 / 无界输出收集 / 空订阅滞留 / registerCleanup 丢面 / 跨单元各面 / 口令入进程参数 | 各移交线 | 本单元均零测试；CL-011（DefaultShellOutputCollector 无界收集）与 TestShellRunner happy path 正交；CL-013（registerCleanup 零生产调用）为"无测试可写"形态（行为不存在），移交时注意先落行为再落测试 |
| CL-019 | TaskExecutionGraph.runTask 仅 catch(Exception)→Error 孤儿 | nop-kernel（移交有效） | 本单元零直接测试；其 IoC 消费面即 C4-008 形态(a)——C10-002 的失败传导测试落地时即同时构成 CL-019 的消费侧回归面，两线共享用例 |
| CL-020 | GlobalExecutors 三全局池零生命周期挂接 | nop-kernel（WI 2.1 C6-003 已立案） | 本单元零测试；C6 组所有 destroy 路径测试（C10-002/C10-010）落地后将天然覆盖 destroy 不触达全局池的可观测面 |

---

## 六、测试套件自身缺陷注记（N1~N7）

| # | 位置 | 注记 |
|----|------|------|
| **N1** | `TestReloadPlugin#tearDown`（nop-plugin-manager） | `plugin.deactivate()` 返回的 ICompletableFuture 被丢弃未等待（对比 TestPluginLifecycle#tearDown 用 `FutureHelper.syncGet`）——清理去激活不 await，在途 deactivation 可能跨用例滞留，引入用例顺序依赖；且 catch-all 吞掉 deactivate 异常，恰好会吸收 C6-002 卡死症状，降低套件对该缺陷的敏感度 |
| **N2** | `TestPluginUnloadFailure#testUnloadFailureKeepsClassLoaderUsableAndRetryable` | 钉死"unload 失败路径 classLoader 不得关闭"——与 C6-001 泄漏形态共享"失败保留资源"哲学（虽属可重试设计而非缺陷）。C6-001 修复若统一"失败即关闭"，将与此钉冲突；修复设计必须区分两类失败终态，否则出现"修复被测试打回"的假阳性 |
| **N3** | 多个测试类的 setup/teardown（TestPluginLifecycle、TestReloadPlugin、TestShellRunner#@BeforeAll、TestAbstractPlugin#testCompatPathW2W4Baseline、TestBeanConditionUnlessProperty、TestMultiConfigVarExpression、TestAppBeansFilter） | 经 `AppConfig.assignConfigValue/updateConfigValue` 写全局配置——正是 C1-007/C1-017 族flag的运行期第二写点（hostId/测试路径写-写竞争面）。单线程测试假设下安全；这批测试是修复后的**强制回归面**（修复不得破坏其语义），但目前对写-写竞争本身零保护 |
| **N4** | `TestBeanConcurrentInitDeadlock` Step3 就绪轮询（L101-109） | 线程状态启发式接受 BLOCKED **或** WAITING，不区分挂停对象——若回归使 t1 挂停于错误监视器，Step3 仍放行，仅靠后续 join 超时兜底；可观测性弱但 @Timeout(30s) 有界，判记录不改级 |
| **N5** | `TestBeanDepends#testAsyncStartOrder` | 全单元唯一 concurrent-start 用例且仅成功路径双 bean——存在"并发启动已被测试覆盖"的套件感知错觉；实际 C4-003/008/009/011 失败语义零覆盖（见 C10-002）。建议在该用例 javadoc 显式标注覆盖边界，防后续修复者误判已有回归保护 |
| **N6** | `TestShellRunner#testTask/testUnknown` | 依赖真实 shell 与任务脚本目录（testTask 无平台守卫，testRunWithInput 有 isWindows 守卫而前两者无）——环境依赖面造成潜在 flaky，非缺陷钉；C6-004/C6-014 回归测试落地时应同步补平台守卫模式 |
| **N7** | `TestFileConfigSourceRefresh`（ScriptedFileConfigSource 桩） | 以 refreshInterval=0 构造，从不触达定时调度与 close()——注释宣称验证"定时任务路径"，实际仅验证 refreshConfig() 方法体；C6-003（构造调度序）与 close 取消语义在该测试的覆盖声明之外，易被误读为已覆盖 |

---

## 七、终止声明

- 本维度为只读审计：未修改任何测试代码、产品代码、四维文件；全部判定基于实际打开测试文件核对的断言语义（关键文件全文读取：TestBeanConcurrentInitDeadlock、TestFileConfigSourceRefresh、TestShellRunner、TestChangeSubscriptions、TestAbstractPlugin、TestPluginLifecycle、TestPluginUnloadFailure、TestReloadPlugin、TestHttpPluginResourceResolver、TestImageCompressHelper、TestKeyManager/TestCompositeKeyManager；其余经方法级清单+断言抽样核对）。
- 「有钉/部分含钉」判定完成 3 条反向验证（II.c），全部通过且支撑原判级。
- 输入口径核对：59 条保留发现（C1×19 + C2×15 + C4×11 + C6×14，P1×11）与四维文件 `## 最终保留项` 节逐条对齐，无遗漏、无多计。

---

## C10 复核结论（独立复核子代理，2026-10-17）

> 复核人与 C10 初审相互独立，以怀疑姿态抽验；只读复核 live code（`nop-core-framework/{nop-ioc,nop-config,nop-plugin,nop-security,nop-log,nop-boot}` + `nop-utils` 全部 `src/test` 与相关 `src/main`），未修改任何测试/产品代码与四维文件。HEAD `6dceb20dc3`。

### 一、矩阵抽验明细（15 行亲核，超过最低 10 行要求）

**P1 行 ×7**（要求 6）：

| 行 | 缺陷 ID | 亲核证据 | 裁定 |
|----|---------|---------|------|
| 1 | C1-007+017（部分） | `TestConfigExpressionResolver` 实测 21 个 @Test、L37/L208-262 经 SimpleConfigProvider/DefaultConfigReference fixture；TestBeanConditionUnlessProperty L44-46、TestMultiConfigVarExpression L50-58、TestAppBeansFilter L40-49 均走 assignConfigValue 写路径；四文件 grep Thread/Concurrent/Executor/latch 零命中 | **维持「部分」** |
| 3 | C1-010（部分） | TestPluginLifecycle L257-286 双线程 + start/done 门闩并发单飞段、L279 `assertEquals(1, AgentInstanceRecorder.activatedCount)` 断言亲核（与 II.c #2 引用行号精确一致）；TestPluginLifecycle 14 @Test（并发段 1 + 其余 13）、TestAbstractPlugin 15、TestCoeffectReconcile 7、TestReconcileTopologicalOrder 6 全部实测吻合；tearDown 全单线程顺序清理 | **维持「部分」** |
| 5 | C2-001（无保护） | nop-ioc 全测试树 grep `new Thread|ExecutorService|CountDownLatch|CyclicBarrier|CompletableFuture` 仅命中 TestBeanConcurrentInitDeadlock 一文件；testAsyncStartOrder 为双 bean 顺序 happy path 无死锁面断言；TestConstructorCycleDetection/TestBeanParentLoop 零并发原语 | **维持「无保护」** |
| 7 | C4-003（部分） | testAsyncStartOrder（L120-134）为全单元唯一 concurrentStart 用例：start→awaitStartFinished→创建序断言→stop，纯成功路径；`handleStartError|whenComplete|startFuture` 在 nop-ioc 测试树零命中 | **维持「部分」** |
| 2/4 | C1-009、C1-021（无保护） | JdbcConfigSource 在 7 目录测试树唯一出现为 TestFileConfigSourceRefresh.java:5 头注释"对齐 JdbcConfigSource"文字；L86 `assertDoesNotThrow` + L92-93 第三轮恢复断言亲核（与 II.c #3 行号精确一致） | **维持「无保护」** |
| 8 | C6-001（无保护） | 三处引用钉逐一打开：TestPluginManager#testCompatStartFailureCleansUpAndDoesNotKeepEntry L130-140（断言 stopped + entries 空）、TestPluginUnloadFailure#testMissingPluginJsonFailsWithExplicitError L142-153（错误码 + 无 entry）、TestHttpPluginResourceResolver#testManagerLoadPluginFailsOnChecksumMismatch L433-450（错误码 + jar 不落盘 + 无 entry）——全部 entry 级，load 失败面无任何 classLoader/JAR 句柄断言 | **维持「无保护」** |
| 9 | C6-002（无保护） | `failStop` 注入全仓仅 TestPluginUnloadFailure L122-137（unload 段）；deactivate 段失败注入零用例；TestPluginLifecycle#tearDown L116-118 / TestReloadPlugin#tearDown L148-150 对 deactivate 异常 catch-and-ignore 亲核 | **维持「无保护」** |

**「部分」行 ×2（P2/P3）**：C1-016（TestKeyManager#testDestroyZeroesStorePassword L75-90 单线程清零钉亲核，nop-security 四文件零并发原语）——维持；C2-007（TestChangeSubscriptions 实测 5 @Test、零并发原语）——维持。

**「无保护」行 ×2（P2/P3）**：C4-010（TestBeanContainer#testDestroy L212-217 仅单 bean 销毁布尔亲核）——维持；C1-005（BeanScopeContextImpl 在 nop-ioc 测试树零引用 grep 实证）——维持。

**追加亲核行 ×4**：C2-013（TestAop 3 @Test、零 concurrentStart）；C4-007（subscribeConfigChange/onRefreshConfig 测试树零命中）；C6-012/C6-013（TestGitRepositoryImpl 4 + TestGitRepositoryManager 2 = 6 方法功能面、TestImageCompressHelper 1 happy 方法）；C6-009（testScopeContract 存在、onContainerStop 测试树零触达）。**15 行判级零推翻。**

### 二、修正行清单（4 处，均为依据/文本级，不改判级）

1. **II.b C6-011 行依据 + C10-004 缺口描述**："TestPluginLifecycle/TestReloadPlugin/**TestPluginUnloadFailure** 的 tearDown 均被迫手写逐插件 deactivate+unload 循环"——TestPluginUnloadFailure **无 tearDown/@AfterEach 插件清理循环**（仅 @BeforeAll/@AfterAll 的 CoreInitialization init/destroy；其插件卸载在用例体内完成）。"tearDown 手写清理买单"反向证据仅适用于 TestPluginLifecycle（L111-127）与 TestReloadPlugin（L143-158）两处。C6-011「无保护」判级维持（证据强度略降、方向不变）。
2. **§1.2 第 3 条**："grep `restart|awaitStartFinished` 仅命中 **TestBeanDepinds** 一处"——（a）"TestBeanDepinds"为笔误（TestBeanDepends）；（b）实际还命中 TestXplService.restart()（bean 生命周期方法，非容器 restart）。实质结论（BeanContainerImpl.restart 零测试调用、awaitStartFinished 仅单线程一次）经独立 grep 维持。
3. **§1.1 nop-plugin 拆分**："42（13 测试类 + 29 fixture）"——实际含 @Test 的测试类 15 个（manager 13 + support TestAbstractPlugin + api TestPluginDefaultMethods）；TestHelloCommand/TestHostCommand 为无 @Test 的 fixture 命名类。盘点级偏差，任何判级不依赖该拆分，94 @Test 方法总数实测吻合。
4. **C10-001 建议测试形态精度**："以可控 Loader 桩（仿 ScriptedFileConfigSource 模式）构造 JdbcConfigSource"——JdbcConfigSource.loadConfig 为 private、无源级 loader 接缝，不能直接仿桩；**可执行接缝是 `JdbcConfig.createConnection()`（public，L91）**，经子类桩化返回动态代理 Connection/Statement/ResultSet 即可落地①②③三形态。可执行性维持，落位方式按此修正。

### 三、C10-001~016 裁定表

| 登记 | 涉及缺陷 ID 与矩阵自洽 | 建议形态可执行性 | 裁定 |
|------|----------------------|----------------|------|
| C10-001 | ✓（C1-009/C1-021/C6-005/C4-002/C6-003jdbc 面，与矩阵行 2/4/8 及 II.b 一致；构造序缺陷在 JdbcConfigSource L52-64 亲核属实） | 可执行，接缝修正见上文二.4 | **维持** |
| C10-002 | ✓（C4-003/C4-008/C4-009/C4-011/C2-009 与矩阵一致；handleStartError 零触达亲核） | 可执行（failure-injection bean fixture 与 MyCreationLog 模式均为现成件） | **维持** |
| C10-003 | ✓（C2-001/C2-002/C1-002/C2-017；TestBeanConcurrentInitDeadlock 门闩/轮询基建亲核存在） | 可执行（TestConcurrentP/D fixture 亲核在位） | **维持** |
| C10-004 | ✓（9 条 ID 全部与矩阵行一致；tearDown 证据修正见二.1） | 可执行（jar 轨 buildPluginJar/门控 fixture 亲核在位） | **维持** |
| C10-005 | ✓（C6-002/C2-010；deactivate 失败注入零用例亲核） | 可执行且成本低于描述——MockPluginStopFail+MockStopControl.failStop 桩现成，直接对 deactivate 段复用即可 | **维持** |
| C10-006 | ✓（C6-001；N2 交互约束与 isClosed 双向断言亲核一致） | 可执行（反射探针模式已在 TestPluginUnloadFailure#classLoaderOf 在位） | **维持** |
| C10-007 | ✓（C6-004/C6-014/C4-005；TestShellRunner 三方法亲核无进程/线程断言；nop-shell 其余 4 测试文件亦无 timeout/stderr 面） | 可执行（需按 N6 补平台守卫，注记已自行声明） | **维持** |
| C10-008 | ✓（C6-003/C4-001；AbstractFileConfigSource 构造 L44-48 先 schedule 后亲核，ScriptedFileConfigSource 固定 `0L` 亲核） | 可执行（Scripted 桩注入构造期抛错 loader 即可） | **维持** |
| C10-009 | ✓（7 条 ID 与矩阵行 1 及 II.b 一致） | 可执行（DefaultConfigReference/applyChange/订阅 API 均为公共面；ACTIVATING 窗口经 StateProbeActivator 基建可达） | **维持** |
| C10-010 | ✓（C4-010/C6-006/C6-007/C6-008/C6-009；MyCreationLog 设施亲核在位） | 可执行 | **维持** |
| C10-011 | ✓（C1-014/C4-004/C6-010/C1-015；nop-commons-java21 无 src/test 亲核） | 可执行（getConfig/stats/refreshConfig/destroy/submit API L35-65 亲核存在） | **维持** |
| C10-012 | ✓（C4-001/C4-006/C4-007/C1-004；TestConfigStarterProfiles 4 方法仅 profile 解析亲核） | 可执行（SingleThreadConfigExecutor start/stop/execute API 亲核在位） | **维持** |
| C10-013 | ✓（C1-016） | 可执行 | **维持** |
| C10-014 | ✓（C6-013） | 可执行（compressImageWithLimit API 亲核存在） | **维持** |
| C10-015 | ✓（C6-012） | advisory 可执行（javadoc 钉非测试形态，属刻意设计；引用计数分支待修复方案） | **维持** |
| C10-016 | ✓（C2-003/C1-001/C1-005；awaitStartFinished 单次调用、restart 零调用亲核） | 可执行 | **维持** |

覆盖图缺口：16 条登记的「涉及缺陷 ID」并集 = 55 条，**C2-012、C2-013（P2）、C1-006、C2-011（P3）4 条未被任何登记引用**——见下节补充发现。

### 四、补充发现（缺保护登记，编号续 C10-017 起）

#### [nop-core-framework-C10-017] IoC 拓扑不可见边族（AOP 拦截器边/ignore-depends 边/表达式动态边）在并发启动下的回归面零覆盖且未被登记引用
- **缺口描述**：C2-013（P2，矩阵「无保护」）与 C2-012（P2，矩阵「部分」）共享同一测试空洞——全部现有测试的依赖边覆盖仅及 resolvedDepends 静态边（TestBeanDepends 13 方法亲核）；TestAop 3 方法为 codegen/反射/动态生成功能钉、不使用 concurrentStart，ignore-depends 逃逸边与表达式动态边在并发启动下无任何用例。该两缺陷未被 C10-001~016 任何一条登记引用，而 C10-003 的建议形态只覆盖 type-injection/factoryBean 交叉边与双 owner 环——若按 C10-003 落地测试而不同时纳入不可见边族，C2-012/C2-013 的回归面仍为零。
- **涉及缺陷 ID**：C2-013（P2）、C2-012（P2）
- **建议回归测试形态**：与 C10-003 共用门闩基建：concurrentStart(true) 下构建三类 bean 图各一——①bean 经 `ioc:interceptors` 引用拦截器 bean；②ignore-depends 声明的逃逸边；③经表达式/变量解析的动态边——@Timeout 边界内并发 getBean，断言收敛且（按修复语义）边等待行为与声明一致；拦截器 bean 未初始化注入面以探针 bean 记录注入时序断言。
- **严重度**：P2

#### [nop-core-framework-C10-018] BeanContainerImpl.getClassIntrospection 并发首调窗口零覆盖且未被登记引用（P3 良性，最小闭环）
- **缺口描述**：C1-006（P3 非原子惰性初始化）与 C2-011（P3 良性重复实例）矩阵判「无保护」（同站）但未被任何登记引用——16 条登记覆盖了全部其余 P3（C2-010/C2-016/C1-020/C2-017/C4-004/C1-015/C4-006/C6-013 均有归属），唯此站点成洞。良性记录判级合理，但覆盖图完整性要求补一条最小登记。
- **涉及缺陷 ID**：C1-006（P3）、C2-011（P3）
- **建议回归测试形态**：多线程并发首调触发 getClassIntrospection 的 bean 图，断言 introspection 缓存单实例（经缓存条目同一性断言），@Timeout 边界——可与 C10-016 的并发 start 用例合并在同一测试类落位。
- **严重度**：P3

### 五、N1~N7 注记复核（逐条打开对应测试，7/7 属实）

| # | 亲核证据 | 裁定 |
|---|---------|------|
| N1 | TestReloadPlugin#tearDown L147 `plugin.deactivate()` 返回值丢弃不等待；对照 TestPluginLifecycle#tearDown L115 `FutureHelper.syncGet`；两者均 catch-and-ignore RuntimeException | **属实** |
| N2 | TestPluginUnloadFailure L127-133：失败路径 `assertFalse(classLoader.isClosed())` + 成功重试 `assertTrue(classLoader.isClosed())` 双向钉亲核 | **属实** |
| N3 | TestPluginLifecycle L125-126、TestReloadPlugin L157、TestShellRunner @BeforeAll `updateConfigValue`、TestAbstractPlugin testCompatPathW2W4Baseline（L285 起）、TestBeanConditionUnlessProperty L44-46、TestMultiConfigVarExpression L50-58、TestAppBeansFilter @BeforeEach/@AfterEach L40-49——全部经 AppConfig 写全局配置亲核 | **属实** |
| N4 | TestBeanConcurrentInitDeadlock L101-109 就绪轮询接受 BLOCKED 或 WAITING、L108 断言同款启发式、@Timeout(30s) L54 有界 | **属实** |
| N5 | testAsyncStartOrder 全单元唯一 concurrentStart 用例、纯成功路径亲核；`handleStartError|whenComplete` 测试树零命中 | **属实** |
| N6 | testTask/testUnknown 无平台守卫、testRunWithInput L84 `assumeTrue(!PlatformEnv.isWindows())`、依赖 test/shell-tasks 真实脚本亲核 | **属实** |
| N7 | ScriptedFileConfigSource 构造 `super(..., 0L)` L36 亲核、三个用例全部直调 refreshConfig()、AbstractFileConfigSource 构造 L44-48 refreshInterval>0 才调度亲核——套件确实从不触达定时器与 close() | **属实** |

### 六、口径核对

- **总数**：C1 19（P1 族案×4/条目×5、P2×10、P3×4）+ C2 15（P1×2、P2×8、P3×5）+ C4 11（P1×1、P2×6、P3×4）+ C6 14（P1×4、P2×9、P3×1）= **59 条、P1×11**——与四维文件 `## 最终保留项`/`## 维度复核结论` 节逐条对齐，**一致**。
- **行数**：58 行 = 11 P1 行（覆盖 12 条，C1-007 行含 C1-017）+ 47 II.b 行（C1 14 + C2 13 + C4 10 + C6 10，亲数吻合）——**一致**。
- **§4.1 总账**：有钉 0 / 部分 20 / 无保护 38 经逐行重算吻合；P1 内部分 4（C1-007、C1-010、C2-002、C4-003）/ 无保护 7 吻合；按维度 C1 9/9、C2 9/6、C4 1/10、C6 1/13 全部重算吻合——**一致**。
- **§1.1 盘点**：测试文件数（71/6/42/4/1/1/26）、@Test 方法数（72/36/94/9/1/3/179，合计 394）、nop-log 三子模块无测试、nop-commons-java21 无 src/test、审计面并发原语文件仅 3 个——全部独立实测吻合（nop-plugin 13/29 拆分偏差见二.3，不影响总数）。§4.2 "真并发测试方法 3 个"将 testAsyncStartOrder 计入、§1.1 标注其"单线程驱动"——两处口径为"测试自建线程 vs 框架级并发路径"之别，非矛盾，予以注明。
- **II.c 行号**：L114/L117/L124（TestBeanConcurrentInitDeadlock）、L279（TestPluginLifecycle）、L86/L92-93（TestFileConfigSourceRefresh）逐一精确核对，**全部一致**。

### 七、最终统计裁定

- **保护力矩阵 58 行判级全部维持**（抽验 15 行 + 佐证 grep 若干，零推翻）；有钉 0 / 部分 20 / 无保护 38、P1×11 中 0 有钉 4 部分 7 无保护的结论**维持**。
- **修正 4 处**（二节：C6-011 证据面收窄、TestBeanDepinds 笔误与 restart grep 口径、nop-plugin 盘点拆分、C10-001 接缝落位），均不影响判级与统计。
- **缺保护登记 16 条全部维持 + 复核补充 2 条（C10-017 P2、C10-018 P3），合计 18 条**。补登后缺保护登记对 59 条保留发现的引用覆盖由 55/59 升至 **59/59 全覆盖**。
- N1~N7 全部属实；CL 注记节抽查（CL-001 文件源三断言有钉、CL-019 与 C10-002 共享用例）与代码事实一致。
- **初审结论整体成立，最终统计维持。**
