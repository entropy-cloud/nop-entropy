# C1 共享可变状态与可见性 — nop-core-framework 六子模块 + nop-utils 第 1 轮初审

- **审计单元**: `nop-core-framework/nop-ioc`（135f/17,072 行实测）、`nop-core-framework/nop-config`（40f/3,541）、`nop-core-framework/nop-plugin`（26f/3,732：api 10f/453 + manager 14f/2,848 + support 2f/431）、`nop-core-framework/nop-security`（12f/630）、`nop-core-framework/nop-log`（11f/528：core/java/log4j2/logback 四子模块）、`nop-core-framework/nop-boot`（5f/364）、`nop-utils`（119f/12,563 行实测，含 nop-commons-java21 的 VirtualThreadTaskExecutor）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`，HEAD 6dceb20dc3）
- **维度定义**: C1 共享可变状态与可见性——多线程共享的字段/集合/缓存/注册表的线程安全与内存可见性；check-then-act 竞态；非同步惰性初始化；static 可变状态；ThreadLocal 误用；发布安全。
- **盲审纪律声明**: 本轮为盲审第 1 轮。审计过程中**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何历史文件；仅阅读了方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md`、owner docs `docs-for-ai/02-core-guides/ioc-and-config.md`、`docs-for-ai/03-modules/nop-plugin.md`，其余结论全部来自当前 live code。
- **判级口径**: 框架层被全平台并发使用（IoC 容器在多线程请求下被并发 getBean/bean 创建；配置在运行期被并发读取 + 定时刷新）——竞态按「默认并发调用」判级；「仅启动期单线程触达」的站点（loader/bean 定义构建/ConfigStarter.doStart 装配段）维持条件化降档（启动期 = main 线程序列化）。P0 = 默认用法 + 常见条件即触发且不可逆；本轮无 P0。
- **越界对照（不立案）**: `nop-kernel/nop-api-core`（IConfigProvider/AppConfig/DefaultConfigReference/AbstractConfigReference 字段本体、ContextProvider、FutureHelper）、`nop-kernel/nop-commons`（GlobalExecutors/ThreadPoolConfig/Cancellable/DefaultThreadPoolExecutor）——这些属于 nop-kernel 审计单元（已有独立轮次），本文仅作为对照引用以支撑 nop-config / nop-utils 立案的写路径/读路径分析。Security 无会话缓存（实测确认稀疏）；Log 无异步 appender（实测确认 configurator-only，`LoggerConfigurator.s_instance` volatile）。

---

## 一、共享可变状态清单表（字段 × 写者 × 读者 × 同步机制 × 判定）

### nop-ioc

| 状态载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `BeanContainerImpl.enabledBeans/orderedBeans/aliases/optionalBeans`（final） | 构造器（启动期） | 全部 getBean 路径 | final 安全发布；运行期只读 | 安全 |
| `BeanContainerImpl.running/started`（volatile L70-71） | start/stop | checkStarted（每请求） | volatile | 见 C1-001（与 singletonScope 的复合读有窗口） |
| `BeanContainerImpl.singletonScope`（**非 volatile** L81） | start L484 | getBeanScope（每请求）L439 | 无 | **C1-001 立案** |
| `BeanContainerImpl.beansByType/beansByAnnotation`（CHM L73-74） | BeanFinder.computeIfAbsent（惰性，首查） | BeanFinder.getBeansByType/ByAnnotation | CHM.computeIfAbsent（原子 per-key） | 安全（compute 内全表扫描属 C2/C9 面，不立案） |
| `BeanContainerImpl.configProvider/classLoader/startMode/concurrentStart/startFuture`（非 volatile） | 启动期 setter/start | 运行期 getConfigValue/awaitStartFinished | 无（启动期 = main 线程） | 条件安全；startFuture 窗口并入 C1-001 备注 |
| `BeanContainerImpl.classIntrospection`（volatile L79） | setClassIntrospection（启动）/ 惰性 L134-138 | getConstructorArgs 等 | volatile；惰性初始化非原子 | C1-006 立案（P3） |
| `BeanDefinition.inCreationThread`（L86，非 volatile） | markInCreation/clearInCreation | isInCreationByCurrentThread | 单例路径在 `synchronized(beanDef)` 内；prototype 路径线程封闭（只读自己线程的写） | 安全（两路径均闭合） |
| `BeanDefinition.subscriptionCleanup`（L135，**非 volatile**） | subscribeConfigChange（initBean 内，可能在异步 start 线程） | destroyBean（stop 线程） | 无 | **C1-004 立案** |
| `BeanDefinition.props/constructorArgs/status/supplier/...` 等 | BeanDefinitionBuilder（启动装载期） | 运行期创建 bean | 启动期单线程写 + start 完成 volatile started 发布（同步路径） | 条件安全（concurrentStart 路径经 graph submit HB） |
| `ProducedBeanInstance.status/initThread/initError/proxyHandler` | runUntil 推进线程 / 创建线程 | 等待者 | `synchronized(this)` + wait/notifyAll | 安全（锁纪律注释明确"锁外执行回调"） |
| `ProducedBeanInstance.bean`（volatile L41） | setBean / runPhaseCallback L175（锁外） | getBean | volatile | 安全 |
| `BeanScopeImpl.beans`（CHM）+ `closed`（volatile） | add/close/remove | get/close 遍历 | add/close 在 `synchronized(beans)` 内做 checkClosed+put / closed 置位（注释声明消除 check-then-act 窗口） | 安全 |
| `BeanScopeContextImpl` 经 `ContextProvider.getContextAttr` 的 BeanContainerScope 挂载 | makeContainerScope L42-47 | getScope | CHM + context attr；**make 存在 check-then-act** | **C1-005 立案** |
| `DelegateInvocationHandler.handler`（L14，**非 volatile**） | setHandler（initBean 阶段） | invoke（每请求，经代理） | 无 | **C1-003 立案** |
| `BeanScopeContext._instance`（static volatile） | registerInstance | instance() | volatile | 安全 |

### nop-config

| 状态载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `DefaultConfigProvider.configRefs/configValues`（volatile L51-52） | changeConfigSource/applyChange（刷新线程） | getConfigRef/makeConfigRef/traceConfigVars | volatile 引导发布整 map（发布后不再变异） | 引用发布安全；**跨键复合读见 C1-008** |
| `DefaultConfigProvider.usedRefs`（CHM，继承自 AbstractConfigProvider） | makeConfigRef.computeIfAbsent（运行期，含类静态初始化并发注册） | getConfigRef/collectUnusedValues | CHM | 安全（注意构造期 configRefs==usedRefs 别名，因 CHM 仍安全） |
| `DefaultConfigReference.provider/location/defaultValue`（api-core，**全裸字段**） | DefaultConfigProvider.applyChangeToUsed→updateValue（刷新线程） | ref.get()（全平台任意请求线程） | **无任何同步/volatile** | **C1-007 立案**（写路径在 nop-config，字段本体属 nop-api-core 对照不立案） |
| `ChangeSubscriptions.simpleSubscriptions/patternSubscriptions`（CHM）+ `ChangeSubscription.listeners` | subscribe（任意线程）/ trigger（刷新线程） | trigger | CHM + synchronized + getListeners 快照拷贝 | 安全 |
| `ConfigChangeApplier.shouldUpdate/active`（AtomicBoolean） | activate/deactivate/requestUpdate/applyChange | 同左 | CAS | 安全（flag+全量重同步模型自洽） |
| `AbstractFileConfigSource.vars`（volatile）/`tasks`（COW） | refreshConfig（globalTimer 线程） | getConfigValues | volatile + COW | 安全 |
| `JdbcConfigSource.connection/errorCount`（**非 volatile**，L45/47） | refreshConfig（synchronized）/ close（**非 synchronized**） | loadConfig | 混合：refresh 有锁、close 无锁 | **C1-009 立案** |
| `JdbcConfigSource.vars`（volatile）+ `onChanges`（COW） | tryTriggerChange（synchronized） | getConfigValues | volatile+synchronized | 引用安全；**回调异常杀调度见 CL-001** |
| `ConfigStarter.g_instance`（static volatile）/ 实例字段 | doStart/doStop | applyChange（configExecutor 线程） | 启动期写 + executor submit HB / LifeCycle 时序 | 安全 |
| `SingleThreadConfigExecutor.executor`（非 volatile） | start/stop | execute | 启动时序 | 条件安全（stop 后 execute 异常路径并入 CL-001 场景） |
| `DynamicConfigSource.configVars`（volatile）/`onClose`（裸） | setConfigVars / setOnClose | getConfigValues/close | volatile / 无 | 安全（onClose 为 lifecycle 单发） |

### nop-plugin

| 状态载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `PluginManagerImpl.plugins/namesToPluginIds/configSubscriptions`（CHM） | loadPlugin/unloadPlugin/reloadPlugin | getPlugin/reconcile/resolveDepPluginIds | CHM | 容器安全；**编排竞态见 C1-010** |
| `PluginManagerImpl.reconcileInFlight/reconcileDirty` | reconcile | 同左 | `synchronized(reconcileLock)` | 安全（重入 dirty 标记设计正确） |
| `PluginManagerImpl.unresolvedPluginIds`（volatile） | doReconcile | getUnresolvedPluginIds | volatile | 安全 |
| `VfsPluginDefinition.state/lastModified/definitionConfig/coeffectEvaluator/onConfigChanged/scope/container/configProvider/lastActivationError/activationFailures/autoActivationPaused`（**全 volatile**） | activate/doDeactivate（lifecycleLock 内）/ **load/unload（锁外）** / updateConfig | getState/getScope/reconcile/checkChangedAndReload | volatile + lifecycleLock（部分路径） | **C1-010（锁外迁移）/C1-012（复合读 TOCTOU）/C1-013（RMW）立案** |
| `AbstractPlugin.state/beanContainer/definition`（**非 volatile**） | activate/doDeactivate（lifecycleLock 内）/ **load/unload（锁外）** | getState/invokeCommandAsync/getCommandBean | lifecycleLock 仅覆盖部分路径 | **C1-011 立案** |
| `PluginClassLoader.plugin/pluginConfig`（裸，装载期写）+ `closed`（volatile） | computeIfAbsent 内装载 | 运行期加载类 | CHM put 发布 | 安全 |
| `PluginScopeImpl.effects/closed` | effect/close | effects()/close | `synchronized(effects)` | 安全（close 幂等、LIFO 锁外执行） |
| `DefinitionConfigProvider.mergedView/listeners` | setMergedView/updateMergedView/setDefinitionValue | getConfigValue/getConfigReference | synchronized(mergedView) + COW | 安全 |

### nop-security / nop-log / nop-boot

| 状态载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `DefaultKeyManager.keyStore/storePassword`（裸字段） | @PostConstruct init / @PreDestroy destroy | getCertificate/getPrivateKey/getCertificateIds | IoC 生命周期 HB（init→started 发布）；destroy 无守卫 | C1-016 立案（P3，shutdown 竞态） |
| `LoggerConfigurator.s_instance`（static volatile） | registerInstance | instance() | volatile | 安全 |
| `Log4j2Configurator.changeLogLevel` 对 log4j2 Configuration 的 get-then-add | 配置刷新监听器/admin | 日志框架内部 | log4j2 自身锁 | 安全（重复 add 后写者胜，幂等） |
| `NopApplication` 实例字段 | main 启动 | — | main 单线程 | 安全 |

### nop-utils

| 状态载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `VirtualThreadTaskExecutor.config`（可变 ThreadPoolConfig，final 引用非 final 内容） | 构造器 / **任何 getConfig() 持有方** | getConfig() 消费方 | 无 | **C1-014 立案** |
| `GlobalExecutors.g_executors`（CHM，对照 kernel） | register/unregister/computeIfAbsent | getExecutor/globalWorker | CHM | 对照安全；重复注册语义并入 C1-015 |
| `MatchPatternCompilerRegistry.DEFAULT.compilers`（CHM） | static init + addCompiler | getCompiler | CHM + 类初始化 | 安全 |
| `ShellRunner.WindowsProcessLaunchLock`（static final） | createProcess | — | synchronized（仅 Windows 进程创建序列化） | 安全 |

### ThreadLocal 盘点

审计单元生产代码 `grep -rn "ThreadLocal"` 零命中（nop-ioc/nop-config/nop-plugin/nop-security/nop-log/nop-boot/nop-utils 的 main 源码均无自建 ThreadLocal）。跨线程上下文依赖全部经 `ContextProvider`（nop-api-core，越界对照）。无 ThreadLocal 误用立案。

---

## 二、发现条目

### [nop-core-framework-C1-001] BeanContainerImpl.singletonScope 非安全发布 + start/stop 复合状态窗口，restart 期间并发 getBean 产出孤儿单例

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:70-90, 436-446, 476-524, 594-622`
- **证据片段**:
  ```java
  private volatile boolean running;          // L70
  private volatile boolean started;          // L71
  private IBeanScope singletonScope;         // L81 —— 非 volatile
  ...
  public void start() {
      if (running)
          throw new NopException(ERR_IOC_CONTAINER_ALREADY_STARTED)...   // L477-478（check-then-act）
      running = true;                        // L483
      singletonScope = new BeanScopeImpl(...);// L484（plain write，晚于 volatile write）
      ...
      started = true;                        // L516（仅同步路径；concurrentStart 路径在 whenComplete 内）
  }
  ```
- **严重程度**: P2
- **并发场景**: 容器 restart（`restart()`=stop+start，运行期管理操作）或 concurrentStart 异步启动窗口（`nop.ioc.app-beans-container.concurrent-start=true`，默认 false）期间，常驻请求线程/预启动定时任务线程并发调用 `getBean/getBeanByType`。
- **触发时序**: (a) 窗口竞态——T1 restart：`stop()` 置 running=false → `start()` L483 置 running=true（此刻 singletonScope 尚为 null，L484 未执行）→ T2 请求线程 `checkStarted()`（L620，running=true 通过）→ `getBean0` L374 `getBeanScope` L439 读到 `singletonScope==null` → 走 L391-404 的"无 scope"分支：对**单例** bean 执行 markInCreation/createInstance/setupInstance 后**不注册到任何 scope** 直接返回 → T3 后续 getBean（此时 L484 已执行）经正常路径再创建并注册第二个实例。后果：同一"单例"两个活跃实例，孤儿实例永不销毁（不在任何 scope.close 覆盖面内）。(b) 可见性竞态——T2 读 `running==true`（volatile read 仅与 L483 **之前**的写建立 HB）后读 `singletonScope`，与 L484 的写之间无 happens-before 边，JMM 语义下可读到 null 或未完整发布的 BeanScopeImpl（unsafe publication）。
- **现状**: singletonScope 为普通字段，运行期读路径（每请求 getBean）与启动/重启写路径之间无同步；`running=true` 先于 singletonScope 赋值，形成"通过 started 检查但 scope 未就绪"的窗口。
- **风险**: restart 与流量并存时产生重复单例 + 泄漏实例（其 @PreDestroy/destroy-method 永不执行，持有的连接/线程池随之泄漏）；concurrentStart 开启时启动窗口内所有外部 getBean 都暴露于 unsafe publication。
- **建议**: 将 `singletonScope` 声明为 volatile 并调整 start() 内写序（先赋值 singletonScope 再置 running=true）；或引入单一启动锁覆盖 (running, started, singletonScope) 三元组的复合迁移；`start()` 的 `if(running)` check-then-act 同锁内闭合。
- **失效面枚举**: 同族状态载体——`startFuture`（L90，awaitStartFinished L536-539 读，非 volatile，并发 await 可见性同病，后果轻）、`cancellable`（L86，stop 置 null 与 asyncStartBeans L556 写竞态，stop-vs-start 管理操作并发，后果为 cancel 丢失）、`BeanContainerImpl.restart()` 无任何互斥（两次并发 restart 可交错 stop/start 序列，ERR_IOC_CONTAINER_ALREADY_STARTED 只防 running==true 的 start，不防交错）。全部登记，均属同一"容器生命周期状态无复合同步"根因。
- **信心水平**: 确定（窗口竞态路径代码可达性逐行核实；可见性部分为 JMM 语义推断，x86 TSO 上几乎不可复现，ARM 上可具化）
- **误报排除**: 非"低频管理路径粗粒度 synchronized 合理"的误报校准对象——本案恰是管理路径**缺**同步且读路径是每请求热路径；getBean 在启动窗口被调用是文档化的 concurrentStart 语义（init/delay 方法派生线程会 getBean）。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P2——L81 裸字段/L483-484 写序/checkStarted L619-622/restart 无互斥/getBeanScope L439/getBean0 null-scope 分支孤儿创建逐行亲核；F3 面（startFuture/cancellable/restart 交错）维持。现状=新发现（无修复提交）。跨单元备注：与 cra-index WI 2.1 nop-kernel-concurrency-C1-001 无同案关系，其「IoC 刷新派发链可达」与本单元 C1-007/017 刷新链上下游相关。

### [nop-core-framework-C1-002] ProducedBeanInstance 跨实例 monitor 等待链在 concurrentStart 下可成环死锁

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/ProducedBeanInstance.java:114-170`、`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:406-434`
- **证据片段**:
  ```java
  // ProducedBeanInstance.runUntil —— 等待者锁在本实例 monitor 上
  if (initThread != null) {
      if (initThread == Thread.currentThread())
          throw selfWait();          // 仅防"同线程"自等待
      waitQuietly();                 // 跨线程等待，无环检测
      continue;
  }
  ```
- **严重程度**: P2
- **并发场景**: `nop.ioc.app-beans-container.concurrent-start=true`（默认 false）时，`asyncStartBeans`（L541-558）的 TaskExecutionGraph 多 worker 并行创建 bean；两个 bean 的 initBean（L632-673）经 `container.getBean(depend, false, beanCtx)` / XPL init 块 / `BeanContainerVariableScope.getValue` 动态解析到**不在各自 resolvedDepends 静态闭包内**的相互依赖。
- **触发时序**: T1 创建 A：createInstance（synchronized(beanDef A)）→ setupInstance（markPropSet）→ getBeanInstance→checkBeanInitialized→runUntil(INITIALIZED)，成为 A 的 owner（initThread=T1）→ 锁外执行 initBean(A) → 动态 getBean(B) → B 已由 T2 注册 → checkBeanInitialized → B.initThread=T2 → T1 在 **B 的实例 monitor** 上 wait。T2 对称地：owner of B → initBean(B) → getBean(A) → A.initThread=T1 → T2 在 **A 的实例 monitor** 上 wait。两线程互相等待对方推进，无超时、无环检测 → 永久挂死；`awaitStartFinished`（FutureHelper.syncGet）随之永久阻塞，启动卡死。
- **现状**: runUntil 的 selfWait 守卫只覆盖同线程重入（bean-init-self-wait，owner doc 已声明）；跨线程的实例 monitor 循环等待无任何打断机制（wait 无超时，对照 F5）。
- **风险**: 开启 concurrentStart 且存在运行期动态相互依赖的 bean 对时，启动期可达死锁且不可自愈（无 watchdog）。
- **建议**: waitQuietly 带期限并在超时后输出双方 initThread 诊断（或全程持有等待图做死锁检测）；更优：initBean 动态 getBean 时沿用 `resolvedDepends` 同一套环检测（把运行期 depend 注册进本轮创建上下文做 DFS）。
- **失效面枚举**: 同族等待点——`markPropSet` 等待段（L128-134，等待创建线程完成 setup，创建线程不依赖其他 bean 的 init 推进，无环风险）；`runDelayMethod/runLazyProperties`（容器侧顺序推进，单线程，无环）；`BeanDefinition.initBean L642-645` 的 `container.getBean(depend, false, beanCtx)`（同步路径单线程 → 触发 selfWait 异常而非死锁，已守卫）。唯一成环面即 concurrentStart 的跨线程 initBean，全类仅此一处。
- **信心水平**: 很可能（交错序列完整可写；前置条件 = concurrentStart 开启 + 静态分析未捕获的相互 init 依赖，二者叠加使其罕见）
- **误报排除**: 非"CompletableFuture/并发原语本身使用"误报——死锁产生在平台自建 wait/notify 协议的跨实例组合上；也非"低频路径粗锁"——死锁后果为启动永久挂死。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P2——runUntil L136-141 跨实例 monitor 等待无环检测、无超时亲核证实；selfWait 仅覆盖同线程；单线程路径经 selfWait 守卫收敛（对照核实）。前置 concurrentStart（默认 false）+ 静态闭包外动态互依，维持 P2。现状=新发现。

### [nop-core-framework-C1-003] DelegateInvocationHandler.handler 裸字段 + getBean(includeCreating=true) 绕过 init 等待，AOP 代理 handler 可无限期不可见

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/DelegateInvocationHandler.java:14, 27-37`、`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:675-686, 742-755`、`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:376-387`
- **证据片段**:
  ```java
  // DelegateInvocationHandler
  private InvocationHandler handler;                       // 非 volatile
  public void setHandler(InvocationHandler handler) { this.handler = handler; }
  ...
  // BeanDefinition.getBeanInstance —— includeCreating 路径跳过 init 等待
  if (!includeCreating || (getBeanMethod() != null && !onlyProducer)) {
      beanCtx.flushInit(getBeanTopoIndex());
      bean.checkBeanInitialized();                          // includeCreating=true 时不执行
  }
  ```
- **严重程度**: P2
- **并发场景**: concurrentStart 异步启动（或多线程运行期首次创建 AOP bean）：T2 正在创建拦截器 bean B（已 `scope.add` 注册半成品， ProducedBeanInstance 注册于 createInstance L569-571，而 handler 回填发生在 initBean L654-661）；T1 创建 AOP bean A，在 setupInstance→addInterceptors（L680）以 `includeCreating=true` 解析 B。
- **触发时序**: T1 L680 `getBean(B, true, beanCtx)` → getBean0 L376-387 includeCreating 分支：scope.get 返回 B 的半成品实例 → getBeanInstance L744 条件为 false（beanMethod==null 且 onlyProducer==false）→ **跳过 checkBeanInitialized** 直接返回 B 的代理（handler 尚为 null）→ T1 将其写入 A 的 interceptors 数组（L684）。此后任何线程调用 A 的代理方法 → `DelegateInvocationHandler.invoke` L33 读到 `handler==null` → `IllegalStateException("nop.err.ioc.bean-proxy-not-initialized")`。若 T2 随后 setHandler，T1 线程此前的读没有 HB 边，后续调用读裸字段——JMM 下无"必然可见"保证（实践上短暂窗口后可见）。运行期正常 getBean（includeCreating=false）有 monitor HB（setHandler 在 ProducedBeanInstance 状态推进的临界区内，等待者经 status 读取建立 HB），不 expose。
- **现状**: includeCreating 快速通道是环依赖设计的一部分，但对"AOP 代理 + beanMethod==null"组合返回的对象在 init 完成前 handler 为 null，且该读取无同步保障。
- **风险**: concurrentStart 期间启动的 AOP bean 首批调用可能抛 bean-proxy-not-initialized；interceptors 数组在并发启动下持有"未接线"代理，属启动期瞬态故障（不损坏状态），但失败表现为用户代码 ISE 而非容器错误。
- **建议**: includeCreating 分支对 `beanModel.isIocProxy()` 且 handler 未回填的对象退化为等待（`bean.checkBeanInitialized()`）；或 DelegateInvocationHandler.handler 声明为 volatile（消除可见性面），并让 includeCreating 返回前至少完成 markPropSet 之后的 handler 接线检查。
- **失效面枚举**: `includeCreating=true` 全部调用点（grep 全仓共 2 处）：(1) `BeanDefinition.addInterceptors` L680——本案主路径；(2) `InjectRefValueResolver.resolveValue` L78——@Inject ref 环依赖早暴露路径，同样可取到 handler==null 的 AOP 代理，注入到正在创建的 bean 字段，同病同修。`getBeanByType(includeCreating=true)`（L316-329）与 `tryGetBeanByType`（L192-203，固定 false）——getBeanByType 的 true 变体当前无调用方，属潜伏兄弟，修复时应一并覆盖。
- **信心水平**: 确定（代码路径逐行可达；瞬态 ISE 必然，永久 stale 仅 JMM 理论）
- **误报排除**: 非"代理/反射原语使用"误报——问题在平台自建的快通道与裸字段组合；失败对用户可见（ISE），非静默。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: **驳回**——证据链不成立：handler==null 仅存在于 beanMethod!=null 分支（唯一空 handler 构造点 createInstance L562），而该组合在 includeCreating=true/onlyProducer=false 下 getBeanInstance L744 条件为真、checkBeanInitialized 照常执行（ProducedBeanInstance monitor HB 保证 handler 已回填）；beanMethod==null 的 AOP 代理在构造期即注入 handler（L558 `new DelegateInvocationHandler((InvocationHandler) bean)`）——includeCreating 快速通道不可能返回 null-handler 代理。全仓 `new DelegateInvocationHandler` 恰 2 处（grep 穷举）。残余面（早暴露代理委托半成品 target）属环依赖早引用 by-design 语义（对照 Spring early-reference），不立案。

### [nop-core-framework-C1-004] BeanDefinition.subscriptionCleanup 锁外 check-then-act，stop 与异步启动竞态导致配置监听泄漏并复活已销毁 bean

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:135, 704-718, 757-790`
- **证据片段**:
  ```java
  private Cancellable subscriptionCleanup;                 // L135，非 volatile
  // destroyBean（stop 线程）
  if (subscriptionCleanup != null) { subscriptionCleanup.cancel(); subscriptionCleanup = null; }   // L710-713
  // subscribeConfigChange（initBean 内，可能在异步 start worker 线程）
  for (String configVar : prop.getReactiveConfigVars()) {
      if (subscriptionCleanup == null)
          subscriptionCleanup = new Cancellable();         // L779-780
      Runnable cleanup = configProvider.subscribeChange(configVar, propChangeListener);
      subscriptionCleanup.appendOnCancelTask(cleanup);     // L781-782
  }
  ```
- **严重程度**: P2
- **并发场景**: `concurrentStart=true` 的异步启动：T2（graph worker）正在 initBean→subscribeConfigChange 登记响应式配置监听；T1（stop/restart 线程或 handleStartError→stop）执行 `singletonScope.close()`→逐 bean `destroyBean`。
- **触发时序**: T2 处理 bean 的第 1 个 configVar：已创建 `subscriptionCleanup=c1` 并挂第 1 条 cleanup → T1 destroyBean 读到 c1，`c1.cancel()`（快照回调执行）并置 `subscriptionCleanup=null` → T2 继续第 2 个 configVar：`subscriptionCleanup==null` → 新建 `c2` 并挂后续 cleanup → T2 结束。结果：`c2` 无人持有、永不 cancel——第 2 个（及以后）configVar 的监听器**永久留在全局 ConfigProvider 上**；配置变更时 refresh 线程仍回调 `propChangeListener` → `prop.assignToObject(bean,...)` 对**已 destroy 的 bean** 重新赋值（复活写入），且监听器表只增不减（长时运行泄漏，对照 F8/C7）。
- **现状**: 登记与清理是对同一字段的跨线程 check-then-act，无锁、无 volatile；stop 与异步创建之间无栅栏（stop() 仅 cancel graph 的 cancellable，不等待在途 bean 创建完成）。
- **风险**: restart/启动失败自动 stop 场景下，被停止 bean 的响应式配置订阅泄漏在存活的全局配置系统上，持续触发对死亡 bean 的属性写入（可能触发其下游资源操作）；多次 restart 泄漏线性累积。
- **建议**: 为 subscriptionCleanup 的创建/取消引入 per-beanDef 锁（或 volatile + CAS 语义的持有交换）；根治方向是 stop() 先 `graph` 级等待在途创建任务完成（TaskExecutionGraph 已有任务句柄）再进入 scope.close。
- **失效面枚举**: 同族"stop 与运行期登记竞态"——`ConfigStarter.cancellable`（L101/504/527，configLogLevel 的 cleanup 登记，但其发生在 doStart 单线程内、cancel 在 doStop，时序隔离，安全）；`BeanScopeImpl.add vs close`（已用 synchronized(beans) 原子化，注释明确，安全——同一容器内两种注册面对 stop 竞态的防护不对称，正是本条反差证据）；`PluginManagerImpl.configSubscriptions`（CHM + unload 单点清理，插件路径无双阶段竞态，安全）。生产代码全仓仅 BeanDefinition.subscriptionCleanup 一处立案。
- **信心水平**: 确定（交错序列完整；appendOnCancel-after-cancel 语义已核对 Cancellable（立即执行），不构成救回）
- **误报排除**: 非"资源清理对称"泛化——这里登记与清理各自存在，缺的是跨线程互斥；后果链（监听器泄漏→死亡 bean 复活写入）具体。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P2——L135/L710-713/L779-782 亲核证实；stop()（L594-622）仅 cancel graph cancellable、不等待在途创建亲核；交错链（c1 cancel→置 null→c2 无人持→监听泄漏+死 bean 复活写入）成立。concurrentStart 前提下维持 P2。现状=新发现。

### [nop-core-framework-C1-005] BeanScopeContextImpl.makeContainerScope 对上下文属性的 check-then-act，共享 IContext 双线程绑定 scope 竞态

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanScopeContextImpl.java:35-64`
- **证据片段**:
  ```java
  private BeanContainerScope makeContainerScope(IBeanContainer container) {
      Map<IBeanContainer, BeanContainerScope> containerScopes = getContainerScopes();
      if (containerScopes == null) {                        // check
          containerScopes = new ConcurrentHashMap<>();
          ContextProvider.setContextAttr(CONTEXT_KEY, containerScopes);   // act
      }
      return containerScopes.computeIfAbsent(container, k -> new BeanContainerScope());
  }
  ```
- **严重程度**: P2
- **并发场景**: 自定义 scope（非 singleton/prototype）bean 的 `bind` 路径（`BeanScopeContext.runWithScope`）；两个线程共享同一 IContext 实例（平台上下文传播语义允许：同一逻辑调用的并行分支挂同一 context），首次 bind 并发。
- **触发时序**: T1 `getContainerScopes()` 返回 null → 构造 map1；T2 同刻也返回 null → 构造 map2；T1 `setContextAttr(KEY, map1)`，T2 `setContextAttr(KEY, map2)`（后写覆盖）→ T2 在 map2 上 bind 的 scope 可达；T1 若在 set 之前已 computeIfAbsent 并 bind 到 map1 → map1 被 map2 覆盖，**T1 的 scope 永久失联** → 后续 `getScope`（L74-80）抛 `ERR_IOC_SCOPE_NOT_OPENED`，或 `onContainerStop`（L83-98）遍历不到该 scope → scope 内 bean 永不销毁（泄漏）。
- **现状**: 容器级 attr map 的初始化未原子化（无 computeIfAbsent 于 context attr 层——ContextProvider attr 接口不支持原子 putIfAbsent，平台设施缺口）。
- **风险**: 使用 request/custom scope 且同上下文并行（异步扇出后汇聚绑定）的应用，偶发 SCOPE_NOT_OPENED 或 scope 泄漏；难以复现定位。
- **建议**: bind 入口对 `getContainerScopes()` 结果做二次校验（若 set 后读回非自身实例则迁移绑定）；或改用 `ContextProvider.getOrCreateContext()` 返回 context 上的 CHM attr + `putIfAbsent` 语义（需 api-core 提供原子 attr API，作为平台设施提案）；绑定路径加 per-context 锁兜底。
- **失效面枚举**: 同类 check-then-act on 外部注册面——`PluginManagerImpl`（全部 CHM computeIfAbsent，安全）；`DefaultConfigProvider.makeConfigRef`（usedRefs CHM computeIfAbsent，安全）；`BeanContainerImpl.getClassIntrospection`（见 C1-006，弱化版同形）。生产代码中仅本条作用于"非并发容器的宿主编码容器"（context attr），独此一处。
- **信心水平**: 很可能（交错可写；前提 = 双线程共享同一 IContext 且首次绑定并发——平台异步传播语义下成立但窗口极窄）
- **误报排除**: 非"低频启动路径"——bind 发生在每次 custom-scope 请求（如 request scope 每请求 bind）；也非"ContextProvider 本身"问题（api-core 不立案），立案点在 nop-ioc 的使用侧原子性缺失。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P2（边界 P2/P3 备注）——makeContainerScope L41-48 check-then-act 亲核证实；后写覆盖→本地 map 绑定失联→SCOPE_NOT_OPENED 或 onContainerStop 遍历不到→泄漏。前提 = 共享 IContext + 每 context 首次绑定并发；因后果含不可自愈 scope 泄漏，维持 P2。现状=新发现。

### [nop-core-framework-C1-006] getClassIntrospection 非原子惰性初始化：重复实例 + 与 setClassIntrospection 的覆盖竞态

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:131-144`
- **证据片段**:
  ```java
  public IBeanClassIntrospection getClassIntrospection() {
      // 派生容器（buildNewInstance）未主动设置时才走懒初始化；volatile + 本地变量
      // 避免并发首次调用时重复发布不完整对象
      IBeanClassIntrospection introspection = classIntrospection;
      if (introspection == null) {
          introspection = new DefaultBeanClassIntrospection(classLoader);
          classIntrospection = introspection;
      }
      return introspection;
  }
  ```
- **严重程度**: P3
- **并发场景**: 派生容器（`buildNewInstance` L115-117 未复制 classIntrospection）在多线程下并发首次 `getBean`（prototype 构造参数转换经 `getConstructorArgs` L518 调用）。
- **触发时序**: T1/T2 同时读到 null → 各自 new 一个 DefaultBeanClassIntrospection 并 volatile 写 → 两线程分别返回**不同**实例（last-writer-wins 覆盖）；对象本体因 volatile 写安全发布，无半成品暴露。
- **现状**: 注释声称避免"重复发布不完整对象"（成立），但不避免重复创建与覆盖；无正确性破坏，仅重复构造开销与实例身份不一致。
- **风险**: 功能性无影响（两个实例均无状态缓存，仅持有 classLoader）；若未来向 DefaultBeanClassIntrospection 增加缓存字段，将变为双缓存不一致。管理性改进项。
- **建议**: 改为标准 double-checked locking（已具备 volatile）或 `AtomicReference.updateAndGet( cur -> cur==null ? new ... : cur )`。
- **失效面枚举**: 审计单元内非同步惰性初始化全量清单：`getClassIntrospection`（本条）、`GlobalExecutors.globalTimer/cachedThreadPool/globalWorker`（CHM computeIfAbsent，安全）、`MatchPatternCompilerRegistry`（static init + CHM，安全）、`BeanScopeContext._instance`（static 直接初始化，安全）。仅本条为裸惰性初始化。
- **信心水平**: 确定
- **误报排除**: 属"防御性代码冗余/结构"类 P3，不宣称正确性缺陷；不与 C1-005 混排（无失联后果）。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P3——volatile+本地变量非原子惰性初始化亲核证实（代码注释自认只防半成品不防重复创建）；无正确性破坏。维持 P3。现状=新发现。

### [nop-core-framework-C1-007] 运行期配置刷新经 DefaultConfigReference 裸字段发布，无 happens-before——刷新值对并发读者可见性无界

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/impl/DefaultConfigProvider.java:297-315`（写路径锚点；对照 `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/config/DefaultConfigReference.java:15, 37-57`、`AbstractConfigReference.java:14-17`——字段本体属 nop-api-core，越界不立案）
- **证据片段**:
  ```java
  // DefaultConfigProvider.applyChangeToUsed（刷新线程）
  used.updateValue(var.getLocation(), castType(var, used.getValueType()));   // L308
  // DefaultConfigReference（api-core 对照）
  private IConfigValue<T> provider;                          // 非 volatile
  public void updateValue(SourceLocation loc, IConfigValue<T> provider) {
      setLocation(loc);                                      // plain write
      setProvider(provider);                                 // plain write
  }
  // 读者（任意请求线程）：ref.get() -> provider.get()
  ```
- **严重程度**: P1
- **并发场景**: 配置定时刷新（AbstractFileConfigSource/JdbcConfigSource → ConfigChangeApplier → SingleThreadConfigExecutor 单线程 applyChange）写；**全平台任意请求线程**经 `AppConfig.varRef(...).get()` / `getConfigValue` 读。这是「默认并发调用」口径的直接命中面：配置读是全平台热路径。
- **触发时序**: T1（configExecutor）applyChange → `used.updateValue` 两次 plain write（location、provider）→ 无任何 volatile/synchronized/final 发布手段；T2（请求线程）持同一 DefaultConfigReference 调用 `get()` → 与 T1 的写无 happens-before 边 → JMM 允许 T2 无限期读到旧 provider（实现上受 TSO 与读者侧偶然同步动作约束，通常短暂陈旧，但语义上无界）。配套：@r-cfg 响应式 bean 的属性重绑定（BeanDefinition.subscribeConfigChange 的 propChangeListener 在刷新线程写 bean 字段）对业务读者同样无 HB——同一根因的第二暴露层。
- **现状**: per-key 的引用对象替换（configRefs volatile 整表发布）是安全的，但"已登记 usedRef 的值更新"退化为裸字段写——热路径读与刷新写之间是标准数据竞争（JLS 意义）。
- **风险**: 配置变更（如开关、限流阈值、日志级别、加密密钥轮换引用）发布延迟不确定；对依赖刷新生效的运维操作（改配置不重启）形成"语义违约"面：变更在部分线程长期不生效。无数据损坏，故不判 P0。
- **建议**: `DefaultConfigReference.provider/location/defaultValue` 升级 volatile（api-core 侧修复，属跨单元提案，本案作为写路径发现的修复去向）；或 updateValue 经 `VarHandle.setRelease` / `AtomicReference` 发布。回归面：全平台配置读路径（ConfigReference.get 消费者）。
- **失效面枚举**: `updateValue` 全部调用方（F3 清扫）：`DefaultConfigProvider.updateRefs` L133-152（changeConfigSource 启动切换）、`applyChangeToUsed` L297-315（运行期刷新，本案）、`AbstractConfigProvider.reset`（测试框架）、`assignConfigValue`/`updateConfigValue`（测试/动态写）——五处全部经同一裸字段，修复一处即全覆盖（同字段同修）；`setConfigModel`→`ref.setDefaultValue` L117（裸写，启动期，条件安全）。ChangeSubscription/ChangeSubscriptions 侧已有 synchronized，无需修。
- **信心水平**: 确定（数据竞争在 JMM 意义确凿；实际陈旧时长依赖硬件与读者行为，标注为"可见性无界、实践有界"）
- **误报排除**: 非"volatile 原语缺失属性能风格"——这是刷新功能的核心正确性契约（reactive config 的"可见"承诺）；亦非把 api-core 字段越界立案：立案锚点是 nop-config 拥有的写路径，字段修复归属另行跨单元流转。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P1，并与 C1-017 **合并立案（本条为主案）**——三字段裸写裸读（DefaultConfigReference L15/updateValue L54-57、AbstractConfigReference L13/L16）、W1 刷新写（applyChangeToUsed L305/L308）vs R1~R8 读（get() L45-51 双裸字段兜底）无 HB 逐行亲核；JMM 数据竞争确凿、全平台热路径。修复载体终裁=api-core 单一不可变 holder+单 volatile 引用（逐字段 volatile 不能闭合撕裂）。统计口径与 C1-017 合并计 1 条 P1 族案（详见维度复核结论特别指令 1）。现状=新发现。

### [nop-core-framework-C1-008] applyChange 多配置项刷新非原子：configValues/configRefs 代际窗口 + usedRef 逐项更新，跨键读撕裂

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/impl/DefaultConfigProvider.java:255-315`
- **证据片段**:
  ```java
  public void applyChange() {
      Map<String, ValueWithLocation> lastVars = this.configValues;      // L256
      Map<String, ValueWithLocation> vars = configSource.getConfigValues();
      this.configValues = vars;                                          // L261 —— 先发布新值表
      ...
      Map<String, DefaultConfigReference<?>> refs = buildConfigRefs(vars);
      this.configRefs = refs;                                            // L290 —— 后发布新引用表
      applyChangeToUsed(changed, refs);                                  // L293 —— 再逐键更新 usedRef
      subscriptions.trigger(this, changed);                              // L294
  }
  ```
- **严重程度**: P2
- **并发场景**: 刷新线程执行 applyChange（多键变更批）期间，任意请求线程经 `getConfigValue`/`getConfigRef`/`makeConfigRef`→`buildFromDefined` 读配置。
- **触发时序**: T1 在 L261 与 L290 之间被调度切出；T2 读 `configRefs`（旧表）→ `buildFromDefined(旧 ref)`（旧值）或直接 `getConfigRef().get()`（经 applyChangeToUsed 已更新的键=新值、未及更新的键=旧值）→ T2 一次业务操作读取的两个配置键分属新旧两代（例如 `nop.datasource.jdbc-url`=新、对应 `username`=旧）。相似撕裂窗口亦存在于 applyChangeToUsed 的逐键 for 循环（键间无原子性）。
- **现状**: volatile 只保证**每键**最终一致，无"一批变更作为一个代际"的原子快照语义；设计上未声明跨键原子性。
- **风险**: 相关键对（url/credential、开关+阈值、证书+口令）在刷新瞬间被一致读到的组合错误；bean 的 onRefreshConfig 与逐键 prop 重绑定使受影响 bean 短暂处于混合配置态。属瞬态、自愈（下一轮读取一致），故 P2。
- **建议**: 若需跨键一致性：以不可变 `ConfigSnapshot`（含 refs+values 单引用）替代双字段，读者经单 volatile 引用取整代快照；至少在 owner doc 声明"跨键无原子刷新语义"，使 @r-cfg 使用方避免构造相关键对假设。
- **失效面枚举**: 同形多步发布——`changeConfigSource` L124-131（三连发布 configSource→configValues→configRefs，启动期调用，条件安全但同形，修复时应同构）；`updateRefs` L133-172（逐键 updateValue，同撕裂面，与 C1-007 同修复载体）；`ChangeSubscriptions.trigger`（快照一致，安全）。
- **信心水平**: 确定（窗口与读取路径直读代码；后果为瞬态不一致而非损坏）
- **误报排除**: 非"volatile 使用本身"误报——问题在**两个** volatile 字段的代际错位与逐键更新，属复合操作缺失，方法论第 4 步明确要求检查的形态。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P2——applyChange L255-295 configValues（L261）先于 configRefs（L290）发布 + applyChangeToUsed 逐键亲核；跨键撕裂窗与瞬态自愈判定成立。维持 P2。现状=新发现。

### [nop-core-framework-C1-009] JdbcConfigSource.close() 不持 monitor 且 connection 裸读写：close 与在途刷新竞态导致连接重建后泄漏

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/source/jdbc/JdbcConfigSource.java:45-47, 71-137, 158-168`
- **证据片段**:
  ```java
  private int errorCount = 0;          // L45
  private Connection connection;       // L47 —— 非 volatile
  private synchronized void refreshConfig() { ... }        // L71 —— 刷新持 this 锁
  private Map<String, ValueWithLocation> loadConfig() throws SQLException {
      if (connection == null) {
          connection = jdbcConfig.createConnection();      // L111-112 —— 锁内重建
      }
      ...
  }
  @Override
  public void close() {                // L159 —— 不 synchronized
      closed = true;
      closeConnection();               // L161 —— 裸读写字段
      ...
  }
  ```
- **严重程度**: P1
- **并发场景**: 应用停机 `ConfigStarter.doStop()`（L547 `IoHelper.safeCloseAll(closeableSources)`）与 globalTimer 上在途的 `refreshConfig`（DB 慢查询期间刷新可持续整个 interval 窗口）并发。
- **触发时序**: T2（timer 线程）refreshConfig 已通过 `closed` 检查（closed 尚为 false），持有 this 锁进入 loadConfig → T1（stop 线程）close()：`closed=true`（volatile 写，不抢锁）→ `closeConnection()`：读到 connection 非空则关闭并置 null（与 T2 的字段读写无互斥——T1 不在 monitor 内）→ T2 执行 L111 `if (connection == null)` 判真 → **createConnection() 新建连接并赋给字段**，完成本轮查询 → 刷新任务此后被 cancel，字段中的新连接**永不关闭** → JDBC 连接泄漏（每次"停机撞上刷新"泄漏一个）。
- **现状**: close 与 refresh 的互斥不完整（一半 synchronized 一半裸奔）；closed 检查与 connection 重建之间存在窗口。
- **风险**: 停机期连接泄漏在连接池外直连场景（jdbcConfig.createConnection 为直连）表现为 DB 侧会话悬挂；若上层有池化则转化为池泄漏。触发频率低（停机 ∩ 刷新在途）但链路具体、后果不可自愈。
- **建议**: close() 改为 synchronized（与 refreshConfig 互斥）+ 双重检查 closed；或 connection 改为持锁内的单飞重建（loadConfig 入口二次校验 closed）。
- **失效面枚举**: 同族"定时刷新资源 vs close"全量对照：`AbstractFileConfigSource`（无 per-instance 可关闭资源，close 仅 cancel future——安全）；`KeyFileConfigSource/PropsFileConfigSource`（继承 AbstractFileConfigSource，安全）；`DynamicConfigSource`（无连接，安全）；`ConfigRouterProcessor/ProfileConfigSource`（无状态，安全）。**全模块仅 JdbcConfigSource 一处立案**，无未修兄弟。
- **信心水平**: 确定（交错序列逐行成立；`errorCount` 同锁内访问已核安全）
- **误报排除**: 非"close 时序由 LifeCycle 保证"误报——cancel(false) 不等待在途任务，时序无保证；也不是把"DB 驱动行为"当缺陷——泄漏由本类字段管理逻辑造成。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P1——close() L158-168 无锁 vs refreshConfig L71 synchronized、connection 裸字段 L47、loadConfig L111-112 锁内重建、cancel(false) 不等待在途（L165-167）逐行亲核；停机撞刷新→新建连接永不关闭链成立。维持 P1。现状=新发现。

### [nop-core-framework-C1-010] VfsPluginDefinition.unload/load 在 lifecycleLock 外迁移状态 + unloadPlugin 不持 reconcileLock：unload 与 reconcile 自动激活竞态导致活容器泄漏

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/VfsPluginDefinition.java:357-377, 379-424`、`PluginManagerImpl.java:145-165, 226-243`
- **证据片段**:
  ```java
  // VfsPluginDefinition.activate —— lifecycleLock 内
  synchronized (lifecycleLock) {
      if (state == PluginState.ACTIVATED) return true;
      ...
      this.state = PluginState.ACTIVATING;
      ... activateInternal(); this.state = PluginState.ACTIVATED; ...
  }
  // VfsPluginDefinition.unload —— 无锁！
  public void unload() {
      if (state == PluginState.ACTIVATED || state == PluginState.ACTIVATING
              || state == PluginState.DEACTIVATING) { throw ...; }
      this.state = PluginState.UNLOADED;               // L375 —— 锁外写
      this.definitionConfig = new LinkedHashMap<>();
  }
  ```
- **严重程度**: P1
- **并发场景**: 配置变更订阅回调在 configExecutor 线程触发 `PluginManagerImpl.reconcile()`（doReconcile→activateIfInactive→def.activate()）；与此同时管理/宿主线程调用 `PluginManagerImpl.unloadPlugin(pluginId)`。两条路径无共同锁（reconcile 持 reconcileLock，unloadPlugin 不持；activate 持 def 的 lifecycleLock，unload 不持）。
- **触发时序**: T1 unloadPlugin → `holder.plugin.unload()` 守卫读 state==LOADED 通过（尚未写 UNLOADED，被调度切出）→ T2 reconcile：`doReconcile` L288 读 `def.getState()==LOADED` → gate 满足 → `activateIfInactive`→`def.activate()` 获 lifecycleLock，重检 state 仍 LOADED → ACTIVATING → activateInternal 构建**子容器并 start、执行 activator、注册 effect** → state=ACTIVATED → T1 恢复：无锁写 `state=UNLOADED`、清空 definitionConfig → T1 继续 `plugins.remove(pluginId)`、关 classLoader（null）→ 结果：子容器**永远运行**（无人 stop、activator effect 永不回退）、状态机被破坏（UNLOADED 但容器活跃）、注册表已移除无法通过任何 API 恢复。反向交错（unload 先写 UNLOADED，activate 后进锁）则 activate 抛 NOT_LOADED 被 activateIfInactive 捕获记日志——那条是安全的，缺陷在"守卫读"与"迁移写"不原子的正向交错。
- **现状**: activate/doDeactivate 已按"in-flight 单飞"注释全部进 lifecycleLock；unload/load（以及 start 的 load 步）被遗漏在锁外——owner doc 声明了 unload 守卫语义但未声明 unload 与 reconcile 的串行化，非已声明边界。
- **风险**: 运维卸载插件撞上配置驱动的 reconcile 时产生不可恢复的资源泄漏（整个插件子容器+effect），且状态不可观测（显示 UNLOADED 实际活跃），后续同名插件重新 load 会形成双活。
- **建议**: `VfsPluginDefinition.unload()/load()` 全程进 lifecycleLock（与 activate/doDeactivate 同锁，迁移写不可分割）；`PluginManagerImpl.unloadPlugin/activatePlugin/deactivatePlugin` 统一纳入 reconcileLock 序列（或至少 unloadPlugin），保证生命周期操作与 reconcile 编排互斥。
- **失效面枚举**: 同族锁外生命周期迁移全量（F3）：`VfsPluginDefinition.load` L357-363（锁外写 state/definitionConfig，本案孪生）、`VfsPluginDefinition.start` L550-563（UNLOADED 时调 load——锁外）、`VfsPluginDefinition.stop` L566-570（deactivate 锁内 + unload 锁外）、`AbstractPlugin.load` L132-148、`AbstractPlugin.unload` L151-164、`AbstractPlugin.start` L319-322、`AbstractPlugin.stop` L342-344——共 8 处同形，其中 6 处属 AbstractPlugin/C1-011 联动立案；`PluginManagerImpl.reloadPlugin` 全程持 reconcileLock（安全，正面对照）。
- **信心水平**: 确定
- **误报排除**: owner doc 对 unload 守卫的声明是"状态检查存在"，不等于声明跨线程原子性；in-flight 单飞声明只覆盖 activate/deactivate，unload 缺失即守卫缺失（F10），非已声明边界。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P1（附窗口备注）——unload L366-377 守卫读+迁移写均无锁、activate L380-424 lifecycleLock 内复读亲核证实；正向交错链（守卫读 LOADED→T2 完整激活→T1 锁外写 UNLOADED+plugins.remove+classLoader 关闭→孤儿活容器）逐行成立；owner doc 仅声明守卫存在、未声明串行化，误报排除维持。窗口备注：T1 守卫读须落于 T2 pre-ACTIVATING 窄段且迁移写落于激活完成前——低概率但每步具体可信、后果不可恢复，维持 P1。**族关系终裁：C1-010/011/012 同族相邻合并立案（本条主案）**，统一修复批次=lifecycleLock 全迁移+reconcileLock 序列化+volatile 对齐+复合读双检。现状=新发现。

### [nop-core-framework-C1-011] AbstractPlugin.state/beanContainer/definition 非 volatile 且 load/unload 锁外迁移（jar 轨同族缺陷）

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-support/src/main/java/io/nop/plugin/support/AbstractPlugin.java:65-78, 131-164, 166-202, 256-274, 394-418`
- **证据片段**:
  ```java
  private IBeanContainer beanContainer;                 // L69 —— 非 volatile
  private PluginState state = PluginState.UNLOADED;     // L75 —— 非 volatile
  ...
  public PluginState getState() {                        // L94-98 —— 无锁读
      if (!isStateMachineAware()) return PluginState.LOADED;
      return state;
  }
  ```
- **严重程度**: P2
- **并发场景**: jar 轨（uber jar）插件：`PluginManagerImpl.loadPluginFromJar` 在 CHM compute 内 start 插件（可被宿主多线程触发），activate/doDeactivate 在 lifecycleLock 内迁移 state/beanContainer；`invokeCommandAsync`（请求线程）读 `getState()!=ACTIVATED` 判活；deactivate 由 stop/reload 线程执行。
- **触发时序**: (a) 可见性——T1 在 lifecycleLock 内 `state=ACTIVATED; beanContainer=container`（锁内写）；T2 请求线程 `getState()` 无锁读裸字段，与 T1 的锁无 HB（T2 未经过 lifecycleLock）→ 可能读到 UNLOADED（误判 INACTIVE 拒绝服务）或 ACTIVATED 但 beanContainer 旧值 null（`getCommandBean` L383 判 null 走宿主回退，行为静默漂移）。(b) 与 C1-010 同形的锁外迁移——`unload` L151-164 守卫读+写 UNLOADED 均无锁，与 activate 的锁内迁移可正向交错（jar 轨由 computeIfAbsent 挡住并发 load，但 unload 来自 unloadPlugin 无锁路径，交错可达）。
- **现状**: VfsPluginDefinition 已全面 volatile 化（其 L98-103 注释明确补 volatile 的动机），AbstractPlugin 同族字段未同步升级——同仓库两轨防护不对称。
- **风险**: jar 轨插件命令路由在激活瞬间偶发 INACTIVE 误报或静默回退宿主容器；unload 竞态与 C1-010 同后果（活容器泄漏）。
- **建议**: state/beanContainer/definition 升级 volatile（对齐 VfsPluginDefinition 的既有风格）；load/unload 迁移进 lifecycleLock（与 C1-010 同一修复批次）。
- **失效面枚举**: 本类字段全量：`pluginGroupId/pluginArtifactId/pluginVersion`（start 写、describePluginId 读，非 volatile，启动期 set 后读，条件安全）、`lastChangeTime/loadTime`（Timestamp 可变对象 setter 暴露，低危）、`autoInit`（锁内写 L268、doStop 读 L355 兼容路径无锁，非 aware 轨单线程生命周期，条件安全）；接口级兄弟 `IPlugin` 的 default 实现未涉及状态。核心缺陷字段 3 个（state/beanContainer/definition），全部在本条立案。
- **信心水平**: 确定（可见性面确凿；unload 交错同 C1-010 论证）
- **误报排除**: 非"启动期写读安全"误报——invokeCommandAsync 是请求期读路径；非把 VfsPluginDefinition 已修状态误当本类已修。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P2——state/beanContainer/definition 裸字段、load/unload 锁外迁移、getState/invokeCommandAsync 无锁读亲核证实；CHM 发布仅覆盖加载时点、激活后已持引用读者无 HB；Vfs 轨 volatile 化不对称证实。与 C1-010/012 同批合并修复。维持 P2。现状=新发现。

### [nop-core-framework-C1-012] 插件激活态 (state, scope/container) 复合读非原子：requireLoaded/getScope/requireActiveScope 的 TOCTOU

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/VfsPluginDefinition.java:326-328, 651-665`、`PluginManagerImpl.java:145-150, 168-185, 209-215`
- **证据片段**:
  ```java
  public IPluginScope getScope() {
      return state == PluginState.ACTIVATED ? scope : null;   // state 读与 scope 读非原子
  }
  private PluginScopeImpl requireActiveScope() {
      PluginScopeImpl s = scope;
      if (state != PluginState.ACTIVATED || s == null) { throw ERR_PLUGIN_INACTIVE; }
      return s;                                                // 检查后 s 可能已被 deactivate 置 closed
  }
  ```
- **严重程度**: P2
- **并发场景**: 业务请求线程经 `ServiceProxy`（每次 invoke 读 `plugin.getScope()`）或 `invokeCommandAsync` 访问激活容器；管理线程并发 `deactivatePlugin`（deactivate→doDeactivate：state=DEACTIVATING → scope=null/container=null → scope.close() → container.stop()）。
- **触发时序**: T2 请求线程 `requireActiveScope` 读 scope=s（非 null）、读 state==ACTIVATED（T1 尚未翻转）→ 通过检查返回 s → T1 执行 doDeactivate：s.close()（effects 全部回退）、container.stop() → T2 继续用 s 解析 bean（`getService`→`container.getBeanByType`→checkStarted 抛 NOT_STARTED）或在已回退 effect 的资源上操作 → 用户代码收到容器层异常而非 INACTIVE 快速失败；反向交错（state 先翻转）则 INACTIVE 正常。`PluginManagerImpl.requireLoaded`+`activate/deactivatePlugin` 同形：requireLoaded 取到 holder 后 holder 可被 unloadPlugin 移除（plugins.remove L158），随后对已卸载定义执行 activate。
- **现状**: 单字段均 volatile（可见性 OK），缺的是 (state, scope) 二元组的事务性读；deactivate 无请求排空（quiescence）机制，owner doc 的 quiescence 断言仅描述 scope.close 语义。
- **风险**: 插件下线瞬间的在途请求收到混型异常（NOT_STARTED/ISE 而非 INACTIVE），影响调用方错误处理分支；requireLoaded TOCTOU 使对已卸载插件的 activate 走到 define 层抛 NOT_LOADED（可接受错误但路径混乱）。无状态损坏，管理操作瞬态窗口。
- **建议**: doDeactivate 先置 state=DEACTIVATING 再等一个短暂排空点（或提供 `awaitQuiesce`），getScope 类读取改为读 DEACTIVATING 即快速失败（检查 `state != ACTIVATED` 放在取 scope **之后**复核）；PluginManagerImpl 侧把 holder 校验与 activate 调用纳入 reconcileLock。
- **失效面枚举**: 同形复合读：`VfsPluginDefinition.getScope` L326-328、`requireActiveScope` L651-657、`requireActiveContainer` L659-665、`updateConfig` L580-586（state==ACTIVATED 检查后 configProvider 读，已 null 防御，弱化同形）、`invokeCommandAsync` L599-608（requireActiveContainer 后 getBean）、`ServiceProxy.Handler.invoke` L82-90（getScope 后 resolveTarget）、`PluginManagerImpl.requireLoaded` L209-215。7 处同族，全部登记；修复以统一"读 scope 前后双检 state"模式覆盖。
- **信心水平**: 确定（TOCTOU 窗口真实；后果为异常类型漂移，非损坏）
- **误报排除**: 非"volatile 单字段已安全"误报——问题恰在多字段复合读；错误处理可依赖方（INACTIVE 契约）被窗口破坏。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P2——getScope L326-328/requireActiveScope L651-657/requireActiveContainer L659-665/ServiceProxy.resolveTarget 亲核证实；(state,scope/container) 复合读后 deactivate 翻转→异常类型漂移成立。与 C1-010/011 同批合并修复（「取 scope 前后双检 state」模式）。维持 P2。现状=新发现。

### [nop-core-framework-C1-013] activationFailures `++` 非原子与 updateConfig 合并读改写：并发丢更新（良性方向）

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/VfsPluginDefinition.java:132-133, 345-355, 573-593`
- **证据片段**:
  ```java
  private volatile int activationFailures;             // volatile 但 RMW 非原子
  private void onActivationFailed() {
      if (++activationFailures >= AUTO_ACTIVATION_FAILURE_THRESHOLD && !autoActivationPaused) {
          autoActivationPaused = true; ...
  }
  // updateConfig
  Map<String, Object> merged = new LinkedHashMap<>(definitionConfig);   // 读
  merged.putAll(config);                                                 // 改
  this.definitionConfig = merged;                                        // 写（volatile 引用替换）
  ```
- **严重程度**: P3
- **并发场景**: reconcile 线程（activateIfInactive）与用户线程（显式 activatePlugin）并发激活失败；多配置回调线程并发 updateConfig。
- **触发时序**: T1/T2 同时失败 → 同读 activationFailures=4 → 各自写 5 → 计数丢一次 → 达到阈值 5 的暂停延迟一轮（无害方向）；T1/T2 并发 updateConfig 不同键 → 后写覆盖前写的 merged → 先到键的定义级配置**丢失累积**（下次 activate 用缺键配置）。
- **现状**: volatile 保证了可见性但不提供 RMW 原子性；updateConfig 为读-改-写引用替换。
- **风险**: 失败计数丢失仅延迟自动暂停（自愈方向，无害）；配置累积丢失需要"两个线程对同一插件并发 updateConfig"——当前调用方（config 订阅回调）无此并发形态，理论风险未具体化。
- **建议**: activationFailures 改 AtomicInteger.incrementAndGet（或纳入 lifecycleLock）；updateConfig 的 merge 纳入 lifecycleLock（顺手与 C1-010 的锁方案统一）。
- **失效面枚举**: 同类 RMW 面全查：`activationFailures`（本条）、`autoActivationPaused`（布尔置位，幂等，安全）、`BeanDefinition.addConfigDependant` L169-174（check-then-act，仅装载期单线程，条件安全）、`ConfigChangeApplier`（AtomicBoolean CAS，安全）。全仓生产代码无其他裸 RMW 计数。
- **信心水平**: 确定（代码事实）；后果影响为理论/良性
- **误报排除**: 不按 P2 报：并发 updateConfig 的调用形态当前不存在（无具体时序），按纪律降 P3 标注"理论风险，未具体化"。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: **驳回**——主面不成立：onActivationFailed/onActivationSucceeded 唯一调用点在 activate() 的 synchronized(lifecycleLock) 临界区内（L406/L414 亲核），++activationFailures 实际锁保护、无并发 RMW，「reconcile 与用户线程并发激活失败丢计数」不可发生；副面（updateConfig merge RMW）初审判词自认无生产并发调用形态（纯理论），且已由 C1-020 修复建议（updateConfig 纳入 lifecycleLock）覆盖——不构成独立立案，merge 原子性义务并入 C1-020 修复批次跟踪（详见维度复核结论争议点②）。

### [nop-core-framework-C1-014] VirtualThreadTaskExecutor：getConfig() 逃逸可变 ThreadPoolConfig、refreshConfig() 空体、stats() 返回 null 的全局契约面

- **文件**: `nop-utils/nop-commons-java21/src/main/java/io/nop/commons/jdk21/VirtualThreadTaskExecutor.java:16-67`
- **证据片段**:
  ```java
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final ThreadPoolConfig config = new ThreadPoolConfig();   // 可变对象
  ...
  @Override
  public ThreadPoolConfig getConfig() { return config; }            // 内部可变状态直接逃逸
  @Override
  public ThreadPoolStats stats() { return null; }                   // 契约值缺失
  @Override
  public void refreshConfig() { }                                   // 空实现
  ```
- **严重程度**: P2
- **并发场景**: 该实例经 `GlobalExecutors.register` 注册为全局单例 `nop-virtual-thread`（`Jdk21CommonsCoreInitializer.initialize`，`CFG_ENABLE_VIRTUAL_THREAD_POOL` 门控）；任何持有 `GlobalExecutors.getExecutor("nop-virtual-thread")`/`allExecutors()` 的代码可读改其 config，并与并发读方（metrics/诊断/配置刷新循环遍历 allExecutors 调 refreshConfig）无同步交错。
- **触发时序**: T1 诊断/管控代码 `executor.getConfig().setMaxPoolSize(8)`（写裸字段）→ T2 同刻 `getConfig().getMaxPoolSize()` 读到撕裂前的旧值/写序中间态（ThreadPoolConfig 字段全裸、非 volatile）→ 若 T1 是"刷新管线：读 config→按参数调整池"，对 VTE 则一切写入无任何效果（refreshConfig 空体、executor 与 config 无联动），管控方却以为已生效（静默违约）；stats() 为 null，任何 `stats().getActiveCount()` 形态的消费直接 NPE。
- **现状**: config 是"可变但实际无效"的摆设状态：既被逃逸暴露，又不参与任何执行语义；refreshConfig/stats 两个接口方法违反 IThreadPoolExecutor 隐含契约（其余实现 DefaultThreadPoolExecutor/DefaultScheduledExecutor 均有真实现）。
- **风险**: 运维按统一线程池治理接口操作全局虚拟线程执行器时得到静默 no-op 与 NPE；逃逸的可变 config 是全局单例上的共享可变状态，任何写者都是未声明 API。
- **建议**: getConfig() 返回防御性快照（或不可变视图）；stats() 返回空 ThreadPoolStats 实例而非 null；refreshConfig() 显式抛"不支持"或按接口默认语义 no-op + 日志；若保留可变 config 则加 volatile/锁。
- **失效面枚举**: 同族 IThreadPoolExecutor 实现对照（越界 kernel 类仅对照）：`DefaultThreadPoolExecutor`（config 参与真实执行+refreshConfig 真实现——但仍逃逸同一可变对象，kernel 单元立案口径）、`DefaultScheduledExecutor`（同上）、`SyncThreadPoolExecutor`（refreshConfig 空体+stats 空实现——**同族兄弟，属 kernel 单元，此处登记为移交线索**）、`BindScheduledExecutor`（委托实现，安全）。nop-utils 单元内仅 VirtualThreadTaskExecutor 一个实现，无未立案兄弟。
- **信心水平**: 确定
- **误报排除**: 非"接口空实现是风格"——GlobalExecutors 是全平台执行器治理入口，空实现破坏统一契约（F9 静默违约形态）；config 逃逸不是 getter 惯例问题而是全局单例共享可变状态暴露。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P2——config 逃逸（L35-37）/stats() null（L40-42）/refreshConfig 空体（L55-57）亲核证实；GlobalExecutors 全局单例契约面成立。维持 P2。现状=新发现。

### [nop-core-framework-C1-015] registerGlobalWorker 重复注册替换旧全局执行器：持有旧引用的提交方被 shutdown 拒绝

- **文件**: `nop-utils/nop-commons-java21/src/main/java/io/nop/commons/jdk21/VirtualThreadTaskExecutor.java:25-27`（对照 `nop-kernel/.../GlobalExecutors.java:102-108`）
- **证据片段**:
  ```java
  public static void registerGlobalWorker() {
      GlobalExecutors.register(new VirtualThreadTaskExecutor());   // 每次调用 new 新实例
  }
  // GlobalExecutors.register（kernel 对照）
  IThreadPoolExecutor old = g_executors.put(executor.getName(), executor);
  if (old != null) { old.destroy(); }                              // 旧实例立即 shutdown
  ```
- **严重程度**: P3
- **并发场景**: `CoreInitialization.initialize()` 有 synchronized + initialized volatile 守卫（kernel，对照核实），常规路径仅执行一次；重复初始化（测试框架多轮 initialize/destroy 循环）或两套初始化器共存（不同 classloader/不同 ICoreInitializer order 场景重复注册同名）时，registerGlobalWorker 再次执行。
- **触发时序**: T1 第二次 registerGlobalWorker → 新 VTE-B put 覆盖 → 旧 VTE-A.destroy()→executor.shutdown() → 此前捕获了 A 引用的任务图（如 TaskExecutionGraph 构造时 `GlobalExecutors.globalWorker()` 取到的 A——注意 globalWorker() 按名查 map，capture 后不再查）继续向 A 提交 → RejectedExecutionException；在 A 上在途的任务不受影响（shutdown 允许完成）。
- **现状**: 注册即替换语义本身由 kernel 定义；VTE 侧未做幂等防御（不检查同名已注册实例）。
- **风险**: 仅在非常规重复初始化流程触发；表现为零星拒绝异常，难归因。
- **建议**: registerGlobalWorker 幂等化（检测同名已注册同类型实例则跳过）；或在 destroy 里记录"已关闭"由 GlobalExecutors 侧在提交路径给出定向错误。
- **失效面枚举**: 同族全局注册点：`GlobalExecutors.globalTimer/cachedThreadPool/globalWorker`（computeIfAbsent 幂等，安全）；`ContextProvider.registerInstance`（重复注册抛 ALREADY_INITIALIZED，有守卫——正面对照）；`LoggerConfigurator.registerInstance`（重复注册仅告警日志，安全）；`AppConfig.registerConfigProvider`（api-core，越界）。VTE 是唯一"无条件 new+替换"的注册点。
- **信心水平**: 确定（代码语义）；触发依赖非常规初始化序列
- **误报排除**: 不判 P2：常规运行仅一次注册（CoreInitialization 守卫已对照核实），无默认可达并发时序，按"理论风险未具体化"降 P3。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P3——GlobalExecutors.register L102-107 replace+destroy 语义、CoreInitialization 守卫、CFG_ENABLE_VIRTUAL_THREAD_POOL 门控（Jdk21CommonsCoreInitializer 亲核）均证实。跨单元备注：与 cra-index WI 2.1 CL-018（GlobalExecutors register/unregister 契约）修复对齐。维持 P3。现状=新发现。

### [nop-core-framework-C1-016] DefaultKeyManager.destroy() 与在途密钥访问并发：keyStore/storePassword 置空竞态

- **文件**: `nop-core-framework/nop-security/src/main/java/io/nop/security/key/DefaultKeyManager.java:28, 38-54, 56-72`
- **证据片段**:
  ```java
  private KeyStore keyStore;
  ...
  @PreDestroy
  public void destroy() {
      if (keyStore != null) { keyStore = null; }
      if (storePassword != null) {
          Arrays.fill(storePassword, '\0');       // 口令清零
          storePassword = null;
      }
  }
  @Override
  public PrivateKey getPrivateKey(String keyId) {
      return (PrivateKey) keyStore.getKey(keyId, storePassword);   // 在途请求读裸字段
  }
  ```
- **严重程度**: P3
- **并发场景**: 停机序列（容器 stop→destroy bean）与仍在处理的请求（签名/验签经 getPrivateKey）并发。
- **触发时序**: T1 请求线程已读 keyStore（非 null）进入 getKey → T2 destroy：keyStore=null、storePassword 清零 → T1 以全零口令调用 KeyStore.getKey → 返回 null 或抛 UnrecoverableKeyException → 请求失败。反向：T1 读 keyStore 前被置 null → NPE 裸抛（非 NopException）。
- **现状**: 字段非 volatile；生命周期无在途请求排空；失败以 NPE/底层异常形式暴露。
- **风险**: 仅停机窗口内的少量在途请求失败；安全侧清零设计本身正确（防口令残留），缺陷仅是并发时序未定义。
- **建议**: destroy 先摘除引用再清零的顺序已对；补 volatile + getPrivateKey 的 null 检查转 NopException（错误处理规范）；容器层 graceful shutdown 时序（先停接入层再 destroy 安全 bean）为根治方向。
- **失效面枚举**: 同族：`CompositeKeyManager`（final list 委托，被委托 manager 的 destroy 时序即本条，无独立缺陷）；`KeySetHelper.getPublicKeySet`（读 certificate，同暴露面，同修）；`SecurityHelper/BcHelper/RsaHelper`（全静态无状态，安全）。模块内唯一有状态类即 DefaultKeyManager，与任务预注"KeyManager 静态状态面预期稀疏"一致。
- **信心水平**: 确定（窗口真实，后果轻）
- **误报排除**: 非"无状态工具类"误报——DefaultKeyManager 是有状态 bean；不按 P2 报因默认部署下停机在途请求窗口极窄且无不可逆后果。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P3（附事实纠偏）——destroy L44-54 置空/清零 vs getPrivateKey 在途裸读证实；纠偏：原文「NPE 裸抛（非 NopException）」不成立——getPrivateKey L66-72 try-catch 内 NPE 经 NopException.adapt 收敛，失败形式为 NopException；停机窗口在途请求失败后果与 P3 判级不变（详见维度复核结论争议点③）。现状=新发现。

---

## 三、维度集外线索（不立案展开）

### [nop-core-framework-CL-001]（维度集外线索，不立案展开）

- **形态归属**: C7（长时运行）/ F9（吞异常→状态不一致）/ F5（挂死不可见）
- **严重度候选**: P1（定时调度永久静默死亡）
- **一句话证据**: `JdbcConfigSource.refreshConfig`（L71-98）中 `tryTriggerChange(vars)`（L96-97）位于 try 块**之外**——任一 onChange 回调（`changeApplier::requestUpdate`，其 executor.execute 在池销毁后抛 RejectedExecutionException，或用户回调 RuntimeException）直接冲出 scheduleWithFixedDelay 任务体，按 JDK 语义**永久取消后续刷新**且无任何告警；同族 `AbstractFileConfigSource.refreshConfig`（L53-70）已在 try 内全量保护并注释说明该 JDK 语义——两兄弟防护不对称，F3 清扫实证。
- **归属单元**: nop-config（本维度记录，建议 C7/F9 维度跟进）
- **复核处置（2026-10-17 独立复核，终裁）**: **确认，已升格立案 C1-021（P1）**——台账标注「升格立案」；触发面终裁表述见 C1-round3.md §二/§五及本文件维度复核结论特别指令 2。

### [nop-core-framework-CL-002]（维度集外线索，不立案展开）

- **形态归属**: C2（锁与同步原语）/ C9（热路径锁内重操作）
- **严重度候选**: P2
- **一句话证据**: `PluginManagerImpl.loadPluginFromJar/loadPluginFromVfs`（L493-500, 567-596）在 `ConcurrentHashMap.computeIfAbsent` 的 bin 锁内执行 HTTP 下载（resourceResolver.resolvePluginResource）、PluginClassLoader 构建、子容器 start 等长 I/O——同 bin 的 getPlugin/resolveDepPluginIds（reconcile 每轮调用）全部阻塞在映射锁上。
- **归属单元**: nop-plugin
- **复核处置（2026-10-17 独立复核）**: **确认**——loadPluginFromJar computeIfAbsent lambda 内 resolvePluginResource（HTTP 下载）/PluginClassLoader 构建/子容器 start 亲核证实；移交 C2/C9 维度。

### [nop-core-framework-CL-003]（维度集外线索，不立案展开）

- **形态归属**: C3（异步链）/ C8（流控串行化）
- **严重度候选**: P2
- **一句话证据**: `PluginManagerImpl.reconcile` 持 reconcileLock 期间经 `deactivateIfActive`→`FutureHelper.syncGet(def.deactivate())` 同步等待插件异步停机（L407）；配置订阅回调（`(p,v)->reconcile()`）运行在**单线程** configExecutor 上，reloadPlugin（持锁做 deactivate/unload/load 全程）会令后续所有配置变更 applyChange 排队阻塞。
- **归属单元**: nop-plugin
- **复核处置（2026-10-17 独立复核）**: **确认**——doReconcile 持 reconcileLock 内 `FutureHelper.syncGet(def.deactivate())` + 配置订阅回调 `(p,v)->reconcile()` 运行于单线程 configExecutor（subscribeGlobalConfig/ChangeSubscriptions.trigger 链亲核）证实；移交 C3/C8 维度。

---

## 四、总计

| 判级 | 数量 | 条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 3 | C1-007（配置刷新裸字段发布无 HB）、C1-009（JdbcConfigSource close 竞态连接泄漏）、C1-010（插件 unload vs reconcile 竞态活容器泄漏） |
| P2 | 9 | C1-001、C1-002、C1-003、C1-004、C1-005、C1-008、C1-011、C1-012、C1-014 |
| P3 | 4 | C1-006、C1-013、C1-015、C1-016 |
| **发现总计** | **16** | |
| CL 维度集外线索 | 3 | CL-001（nop-config 定时刷新被回调杀死）、CL-002（computeIfAbsent 锁内重 I/O）、CL-003（reconcile 持锁阻塞单线程配置执行器） |

**正面确认（非发现，供复核轮对照）**: BeanScopeImpl add/close 的 `synchronized(beans)` 原子化、ProducedBeanInstance 的"锁内短临界区+锁外用户回调"纪律、`inCreationThread` 单例/prototype 双路径闭合、ChangeSubscriptions/ChangeSubscription 快照语义、ConfigChangeApplier 的 flag+全量重同步 CAS 模型、ChangeSubscription 触发异常隔离注释、DefinitionConfigProvider mergedView 全锁化、PluginScopeImpl effect/close 幂等、Cancellable append-after-cancel 立即执行语义——均实测正确，避免复核轮重复排查。

---

## 维度复核结论

- **复核代理**: C1 维度独立复核子代理（与初审三轮相互隔离，怀疑姿态逐条亲核）；复核日期 2026-10-17；复核对象 = C1-001~021 全部 21 条发现 + CL-001~003/014~016 共 7 条维度集外线索。
- **复核方法**: ① 逐条打开 file:line 亲核证据（行号漂移以实际代码为准，本轮实测漂移均 ≤3 行，不影响任何结论）；② P1 逐子项明细复核；③ F3 完备性独立 grep 穷举（`.updateValue(` 全仓 src/main = 8 站点、配置引用 `.setDefaultValue(` 外呼唯一 = `DefaultConfigProvider.setConfigModel` L117、`.setLocation(` 配置面外呼 = 0、`new DelegateInvocationHandler` = 2 处、`addOnChange` 生产注册唯一 = ConfigStarter L486、`includeCreating=true` getBean 调用 = 2 处）；④ 历史对照面（`ai-dev/audits/cra-index.md` WI 1.1–2.1 台账、`ai-dev/plans/2469-cra-wi2.2-nop-core-framework.md`）已解除盲审，仅用于现状标注与跨单元关联。
- **裁定汇总**: 保留 19 / 驳回 2（C1-003、C1-013）/ 严重度变更 0 / 合并裁定 1（C1-017 并入 C1-007 族案）；CL 确认 6 / 驳回 0。

### 裁定汇总表

| ID | 初审判级 | 终裁 | 关键依据（复核亲核） | 现状 |
|---|---|---|---|---|
| C1-001 | P2 | **保留 P2** | L81 裸字段、L483 running 先于 L484 singletonScope 写序、checkStarted L619-622、restart() 无互斥、getBeanScope L439、getBean0 null-scope 分支孤儿创建逐行核实 | 新发现 |
| C1-002 | P2 | **保留 P2** | runUntil L136-141 跨实例 monitor 等待无环检测无超时；selfWait 仅覆盖同线程；前置 concurrentStart（默认 false）+ 静态闭包外动态互依 | 新发现 |
| C1-003 | P2 | **驳回** | handler==null 不可达（详见争议点①）；全仓仅 2 处 DelegateInvocationHandler 构造，两分支均闭合 | — |
| C1-004 | P2 | **保留 P2** | L135/L710-713/L779-782 证实；stop()（L594-622）仅 cancel graph cancellable、不等待在途创建 | 新发现 |
| C1-005 | P2 | **保留 P2**（边界 P2/P3 备注） | makeContainerScope L41-48 check-then-act 证实；前提 = 共享 IContext + 每 context 首次绑定并发；后果含不可自愈 scope 泄漏 | 新发现 |
| C1-006 | P3 | **保留 P3** | volatile + 本地变量非原子惰性初始化证实（代码注释自认只防半成品不防重复） | 新发现 |
| C1-007 | P1 | **保留 P1**（主案，吸收 C1-017） | 三字段裸写裸读 + W1 刷新写 vs R1~R8 读无 HB 逐行核实；JMM 数据竞争确凿、全平台热路径 | 新发现 |
| C1-008 | P2 | **保留 P2** | applyChange L255-295：configValues L261 → configRefs L290 代际窗 + applyChangeToUsed 逐键证实；瞬态自愈 | 新发现 |
| C1-009 | P1 | **保留 P1** | close() L158-168 无锁 vs refreshConfig L71 synchronized；connection 裸字段 L47；loadConfig L111-112 锁内重建；cancel(false) 不等待在途（L165-167）——停机撞刷新 → 新建连接永不关闭 | 新发现 |
| C1-010 | P1 | **保留 P1**（附窗口备注） | unload L366-377 守卫读+迁移写均无锁、activate L380-424 锁内复读证实；正向交错链逐行成立（详见争议点④窗口备注） | 新发现 |
| C1-011 | P2 | **保留 P2** | state/beanContainer/definition 裸字段、load/unload 锁外、getState/invokeCommandAsync 无锁读证实；CHM 发布仅覆盖加载时点 | 新发现 |
| C1-012 | P2 | **保留 P2** | getScope L326-328 / requireActiveScope L651-657 / requireActiveContainer L659-665 / ServiceProxy.resolveTarget 证实 | 新发现 |
| C1-013 | P3 | **驳回** | `++activationFailures` 仅在 activate() 的 synchronized(lifecycleLock) 临界区内调用（L406/L414 亲核），实际有锁保护无 RMW 竞态；updateConfig merge 面无生产并发调用方且已由 C1-020 修复建议覆盖（详见争议点②） | — |
| C1-014 | P2 | **保留 P2** | config 逃逸（L35-37）/stats() null（L40-42）/refreshConfig 空体（L55-57）亲核；全局单例契约面成立 | 新发现 |
| C1-015 | P3 | **保留 P3** | GlobalExecutors.register L102-107 replace+destroy 语义亲核；CoreInitialization 守卫 + CFG 门控；与 WI 2.1 CL-018（GlobalExecutors register/unregister 契约）修复对齐 | 新发现 |
| C1-016 | P3 | **保留 P3**（附事实纠偏） | destroy L44-54 置空/清零 vs 在途读证实；纠偏：NPE 非「裸抛」（详见争议点③），判级不变 | 新发现 |
| C1-017 | P1 | **保留，并入 C1-007 族案** | W1~W7 写点（本人抽验 6/7 直呼站点 + hostId 转运口全核）、R1~R8 读站、W3 线程精度修正（doStart main，L214→L484）全部亲核成立 | 新发现 |
| C1-018 | P2 | **保留 P2** | registerNameIndex computeIfAbsent→add 与 unregisterNameIndex remove→isEmpty→remove 三步亲核；孤儿集 → 门控静默失效链成立 | 新发现 |
| C1-019 | P2 | **保留 P2** | reloadPlugin L677-683 remove→put 窗口亲核；复核补充：T2 computeIfAbsent 持 bin 锁执行慢 lambda 期间 T1 put 阻塞、T2 完成后被覆盖——交错可达性成立且非削弱 | 新发现 |
| C1-020 | P3 | **保留 P3** | activateInternal 快照读 L461 + updateConfig L580 终态判定亲核；ACTIVATING 窗口双跳过、静默分叉成立 | 新发现 |
| C1-021 | P1 | **维持 P1** | 全部结构事实独立复核成立（详见下节终裁） | 新发现（CL-001 升格立案） |

### 特别复核指令终裁

**1. C1-007/C1-017（配置发布协议裸字段族）——终裁：同根因族合并立案。**
- a) 三字段裸写裸读：成立。`DefaultConfigReference.provider`（L15）/`AbstractConfigReference.location`（L13）/`defaultValue`（L16）全裸；`updateValue` L54-57 两步裸写；读侧 `IConfigReference.get()` L45-51 的 provider 读与 defaultValue 兜底读共同构成热路径。
- b) 刷新线程写 vs 请求线程读无 happens-before：成立。W1（applyChangeToUsed L305/L308，configExecutor 刷新线程）对 usedRef 原地裸写，任意请求线程 `ref.get()` 裸读，中间无任何同步动作——标准 JMM 数据竞争。
- c) `AppConfig.hostId()` 运行期写点：成立。L46-58 经 W7→W6 裸写，`synchronized(CFG_HOST_ID)` 仅互斥 hostId 自身、不排他 W1 刷新线程；「nop.host-id 运行期新增进刷新批」的写-写竞争前提核实成立。W 写点分类抽验：W1/W2/W3/W4/W5/W6 六处直呼站点 + W7 转运口全部亲核（含第 3 轮对 W3 的精度修正——`updateRefs` 唯一调用方 `changeConfigSource` L128，唯一调用方 `ConfigStarter` L214→L484 doStart，W3 确为启动 main 线程，修正确认）。
- d) 修复载体裁定：合理。逐字段 volatile 无法闭合 R5（traceConfigVars L191-201 location→isDynamic→defaultValue→get() 连读，已亲核）的跨字段撕裂；**单一不可变 holder（loc+provider+defaultValue 三元组）+ 单 volatile 引用整体替换**为最小完备修复，此为终裁修复方案；api-core 归属移交 + nop-config 回归面 = W1/W4/W5/W6/W7 运行期写点 × R1~R8 + wrapper 链 + §四跨单元运行期消费者（OrmMigrationWriteGate 必测）——采纳第 3 轮结论。
- e) 两条 P1 关系终裁：**同根因、同字段面、同修复载体的同一缺陷，C1-017 并入 C1-007 作为修复面完备性扩展，合并立案**。编号双保留用于追溯；统计口径按 **1 条 P1 族案**计（避免同一缺陷双计 P1 与修复工作量）。C1-017 的三类扩展面中，面 (a)（defaultValue 兜底旧值无界陈旧）在实践上多被任务提交 HB 边闭合（纯 JMM 理论面，见争议点⑤），合并案 P1 由 W1 刷新可见性面 + 写-写竞争 + 撕裂面独立支撑，不依赖面 (a)。

**2. C1-021——终裁：维持 P1。**
- 结构事实全部独立复核成立：`tryTriggerChange(vars)` 位于 try 块外（JdbcConfigSource L96-97）、`task.run()` 循环无任何防护（L104-106）、兄弟类 `AbstractFileConfigSource` 全量 try-catch 并成文注释 JDK 永久取消语义（L64-67）、`addOnChange` 生产唯一注册点 = ConfigStarter L486、`IConfigExecutor` 经 `SysServiceLoader` 可插拔（initConfigExecutor L266-273）、`ChangeSubscriptions.trigger` per-listener 异常隔离（L78-84 LOG.error）亲核。
- 第 3 轮对第 2 轮「reconcile 回调直冲任务体」表述的纠偏：核实成立——插件 reconcile 回调运行于 `subscriptions.trigger`（configExecutor 线程、有隔离），不在 tryTriggerChange 同步回调内。
- P1 终裁依据：触发面 (i) 可插拔 IConfigExecutor 运行期拒绝 + (ii) 公开 SPI `IConfigSource.addOnChange` 宿主回调抛出，均为代码可达的真实运营扩展面；默认布线经 `shouldUpdate` CAS 门（ConfigChangeApplier L40-46，已核）队列深度 ≤1、REE 不可达——**不满足 P0「默认用法+常见条件」判定正确**；后果 = DB 配置热更新链永久静默死亡、零日志、不可自愈、可观测性为模块内最差档；兄弟类成文设防构成 F10 守卫缺失的防护不对称。修复建议（trigger 独立 try-catch + LOG.error，勿并入 loadConfig 重试 try；更优 per-task 隔离）合理，采纳。

**3. C1-010/011/012（插件状态机族）——关系裁定：同族相邻合并立案（C1-010 主案）。**
- 三者同属「插件生命周期锁面不完整」根因：lifecycleLock 仅覆盖 activate/doDeactivate（load/unload/start/stop/updateConfig 锁外）+ manager 生命周期操作（unloadPlugin/activatePlugin/deactivatePlugin）不持 reconcileLock。C1-010（Vfs 轨 unload×reconcile 正向交错 → 孤儿活容器）后果最重为主案 P1；C1-011（jar 轨裸字段可见性 + 同形锁外迁移）与 C1-012（(state,scope/container) 复合读 TOCTOU → 异常类型漂移）为同批次 P2 并案。
- 统一修复批次：① VfsPluginDefinition/AbstractPlugin 的 load/unload/start/stop/updateConfig 迁移进各自 lifecycleLock；② manager 侧 unloadPlugin/activatePlugin/deactivatePlugin 纳入 reconcileLock 序列（同时闭合第 3 轮 §三已核实的跨定义门控 TOCTOU——覆盖关系判定复核成立，无需独立立案）；③ AbstractPlugin state/beanContainer/definition 升级 volatile 对齐 Vfs 轨；④ 复合读站点统一「取 scope 前后双检 state」模式。第 3 轮「宿主绕过 manager 直调 IPlugin 生命周期属宿主误用面，建议 owner doc 补『生命周期操作必须经 IPluginManager』」——采纳。

**4. CL-001~003/014~016 处置：**

| CL | 处置 | 说明 |
|---|---|---|
| CL-001 | **确认——已升格立案 C1-021** | 台账标注「升格立案」；终裁见上节 2 |
| CL-002 | **确认** | loadPluginFromJar computeIfAbsent lambda 内 HTTP 下载/PluginClassLoader 构建/子容器 start（L567-596 段亲核）——移交 C2/C9 维度 |
| CL-003 | **确认** | doReconcile 持 reconcileLock 内 `FutureHelper.syncGet(def.deactivate())` + 配置订阅回调 `(p,v)->reconcile()` 运行于单线程 configExecutor（subscribeGlobalConfig/trigger 链亲核）——移交 C3/C8 维度 |
| CL-014 | **确认** | doDeactivate L268-271 autoInit 时锁内 `CoreInitialization.destroy()` 亲核——移交 C4/跨单元（CoreInitialization 本体属 nop-core） |
| CL-015 | **确认** | checkChangedAndReload L606-626 仅 checkChanged 包 try、reloadPlugin 裸调亲核——P3，宿主轮询侧守卫 |
| CL-016 | **确认** | `PlaceholderConfigReference.get()` L56-58 hash 不匹配分支返回未替换原始模板值、actualValue/refValueHash 永不重建、生产零调用方（grep 亲核）——潜伏功能缺陷（非 C1 共享状态），移交 nop-kernel/nop-api-core 功能缺陷轨 |

### 争议点与纠偏记录

1. **C1-003 驳回理由（证据链不成立）**：初审场景假设「includeCreating=true 对 beanMethod==null 的 AOP 代理返回 handler==null」。实际（createInstance L554-565 亲核）：(i) `beanMethod==null && isIocProxy` 分支在构造期即 `setBean(createProxy(new DelegateInvocationHandler((InvocationHandler) bean)))`——handler 自构造起非 null，includeCreating 快速通道返回的代理不存在 null handler；(ii) `beanMethod!=null`（唯一空 handler 构造点 L562）组合下 `getBeanInstance` L744 条件 `!includeCreating || (getBeanMethod() != null && !onlyProducer)` 在 includeCreating=true/onlyProducer=false 时为**真**——checkBeanInitialized 照常执行，经 ProducedBeanInstance monitor HB 保证 handler 已回填。两分支均闭合，`handler==null` ISE 场景不可达。全仓 `new DelegateInvocationHandler` 恰 2 处（grep 亲核），无第三构造点。残余面（早暴露代理委托半成品 target）属环依赖早引用 by-design 语义（对照 Spring early-reference），不立案。
2. **C1-013 驳回理由（主面无竞态 + 副面纯理论）**：`onActivationFailed`/`onActivationSucceeded` 唯一调用点在 `activate()` 的 `synchronized(lifecycleLock)` 临界区内（L406/L414 亲核），`++activationFailures` 实际锁保护——「reconcile 线程与用户线程并发激活失败丢计数」不可发生；updateConfig merge RMW 面真实存在但初审判词自认「当前调用方无此并发形态，理论风险未具体化」，且 C1-020 的修复建议（updateConfig 纳入 lifecycleLock）已覆盖该面——不构成独立立案，merge 原子性义务并入 C1-020 修复批次跟踪。
3. **C1-016 事实纠偏（判级不变）**：原文「T1 读 keyStore 前被置 null → NPE 裸抛（非 NopException）」不成立——`getPrivateKey` L66-72 的 `keyStore.getKey` 在 try-catch 内，NPE 被 `NopException.adapt(e)` 收敛，失败形式为 NopException。停机窗口在途请求失败的本质后果与 P3 判级不变。
4. **C1-010 窗口备注（判级不变）**：坏交错要求 T1 的 unload 守卫读落于 T2 的 pre-ACTIVATING 段（doReconcile 状态检查/门控评估/activateIfInactive 进锁前，窄窗口）且 T1 的迁移写落于 T2 激活完成前（宽窗口，覆盖整个慢激活）——属低概率但每步具体可信、后果不可恢复（子容器+effect 孤儿、状态机破坏、注册表不可达）的事件；与 C1-009 同判级口径（停机/管理操作 ∩ 在途操作，链路具体、后果不可逆），维持 P1。
5. **C1-017 面 (a) 备注（合并案判级不依赖此面）**：defaultValue 启动期裸写的「无界陈旧」在实践上多被线程创建/任务提交的 HB 边闭合（启动后创建的请求线程经 executor submit 获得 HB）——纯 JMM 理论面；C1-017 的实证面为 (b) 写-写竞争与 (c) 跨字段撕裂 + 与 C1-007 共享的刷新可见性面。
6. **第 3 轮精度修正（W3 线程）独立确认**：`updateRefs` 唯一调用方 `changeConfigSource`（L128），后者唯一调用方 `ConfigStarter.updateConfigSource`（doStart 第 9 步，L214→L484）——W3 为启动 main 线程，第 2 轮「任意测试线程（运行期）」不成立，修正采纳；C1-017 判级由运行期五写点（W1/W4/W5/W6/W7）独立支撑，不受影响。

### 现状标注

- **全部 21 条发现均为新发现**（本轮审计产出；`git log` 核实分支 `concurrency-audit-remediation` 上无针对上述站点的修复提交；既有相邻修复仅 plan 2430 的 api-core `reset()` 重采样（ba900ab74f，即 W4 写点来源）与 plan 344 时代的 BeanScopeImpl/BeanDefinition 守卫，均先于本轮审计且非本批发现的修复）。
- **CL-001 台账关联**：升格立案为 C1-021（P1），原始线索保留编号。
- **WI 2.1 台账跨单元关联**（cra-index 已登记项）：① `nop-kernel-concurrency-C1-001`（RoundRobinSupplier.resize）标注的「IoC 刷新派发链可达」——其上游派发链即本单元 C1-007/017 的配置刷新链（applyChange → subscriptions.trigger → @r-cfg 重绑定），两案修复相互独立但回归面重叠（配置刷新触发路径），7.1 合并裁定时应单列；② `nop-kernel-concurrency-CL-018`（GlobalExecutors register/unregister 契约）——本单元 C1-015（registerGlobalWorker 重复注册）为其消费侧实例，修复须与该契约裁定对齐；③ `nop-kernel-concurrency-C3-001`（周期任务设施无统一异常绝缘层）——本单元 C1-021 为其「29 处生产站点」清单的 nop-config 实例，绝缘层平台级方案应吸收 C1-021 的独立 try-catch 语义（勿双改）。

## 最终保留项

**P1 族案 ×4**（条目 ×5）：
1. **C1-007 + C1-017（合并族案）**——配置发布协议裸字段族：location/provider/defaultValue 三字段裸写裸读、刷新写无 HB、hostId 运行期写-写竞争、两步写序撕裂；修复 = api-core 单一不可变 holder + 单 volatile 引用，回归面 = W1/W4/W5/W6/W7 × R1~R8 + wrapper 链 + 跨单元运行期消费者（OrmMigrationWriteGate 必测）。
2. **C1-009**——JdbcConfigSource.close() 无锁 vs 在途刷新：连接重建后永不关闭；修复 = close() synchronized 化 + closed 双检。
3. **C1-010（主案）**——VfsPluginDefinition.unload/load 锁外迁移 × reconcile 自动激活竞态 → 孤儿活容器；修复 = load/unload 进 lifecycleLock + 生命周期操作纳 reconcileLock（连带闭合跨定义门控 TOCTOU）。
4. **C1-021**——JdbcConfigSource 定时刷新回调无防护 → DB 配置热更新链永久静默死亡；修复 = trigger 独立 try-catch + LOG.error（per-task 隔离更优），回归测试含坏回调存活断言。

**P2 ×10**：C1-001（singletonScope 非安全发布 + 生命周期复合状态窗口）、C1-002（concurrentStart 跨实例 monitor 等待环死锁）、C1-004（subscriptionCleanup 锁外 check-then-act → 监听泄漏 + 死 bean 复活写入）、C1-005（makeContainerScope 上下文属性 check-then-act → scope 失联/泄漏）、C1-008（applyChange 双 volatile 代际撕裂）、C1-011（AbstractPlugin 裸字段 + 锁外迁移，与 C1-010 同批）、C1-012（插件激活态复合读 TOCTOU，与 C1-010 同批）、C1-014（VirtualThreadTaskExecutor config 逃逸/stats null/refreshConfig 空体）、C1-018（namesToPluginIds 判空摘键三步非原子 → 门控静默失效）、C1-019（reloadPlugin remove→put 窗口 → 双活定义 + 订阅清理丢失）。

**P3 ×4**：C1-006（getClassIntrospection 非原子惰性初始化）、C1-015（registerGlobalWorker 重复注册替换，与 WI 2.1 CL-018 对齐）、C1-016（DefaultKeyManager destroy 置空竞态，NPE 表述已纠偏）、C1-020（updateConfig ACTIVATING 窗口热应用静默丢失；修复时顺带闭合 C1-013 残留的 merge 原子性）。

**CL 确认移交**：CL-002 → C2/C9；CL-003 → C3/C8；CL-014 → C4/跨单元；CL-015 → P3 宿主守卫；CL-016 → api-core 功能缺陷轨。CL-001 → 已升格 C1-021。

**统计（终裁口径）**：发现 21 条 → 保留 19（P1 条目 ×5 / 族案 ×4、P2 ×10、P3 ×4）、驳回 2（C1-003、C1-013）；CL 7 条 → 确认 6（含 1 条升格立案）、驳回 0。
