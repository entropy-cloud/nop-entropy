# C1 共享可变状态与可见性 — nop-core-framework 六子模块 + nop-utils 第 2 轮深挖（盲审）

- **审计单元**: `nop-core-framework/nop-ioc`、`nop-config`、`nop-plugin`（api/manager/support）、`nop-security`、`nop-log`（core/java/log4j2/logback）、`nop-boot` + `nop-utils`（含 nop-commons-java21/nop-java-parser/nop-match/nop-diff 等 10 子模块，src/main 口径）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`，HEAD 6dceb20dc3）
- **本轮输入**: 第 1 轮报告 `C1-shared-mutable-state.md`（C1-001~016 + CL-001~003）、基线 `_tmp/cra-baseline-nop-core-framework.txt`（C1 节 static/ThreadLocal 清单）、live code。
- **盲审纪律声明**: 本轮为盲审第 2 轮。除唯一允许的输入文件 `C1-shared-mutable-state.md` 外，**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何其他文件；未读取同目录其他维度文件。全部结论来自本轮对 live code 的直接取证。
- **本轮目标（逐条执行完毕）**:
  ① C1-007 失效面扩展（配置刷新发布族写/读路径全枚举 + 跨单元移交面精确化）→ 见 §一、发现 C1-017；
  ② C1-010/011/012 家族矩阵收口（三状态载体迁移全表 + 遗漏站点补全 + 孤儿/卡死后果判定）→ 见 §二；
  ③ check-then-act 全扫（守卫-迁移原子性逐站点判定）→ 见 §三；
  ④ 单例槽位与静态状态（static non-final 全枚举 + BeanContainerImpl 容器字段启动期写 vs 运行期读）→ 见 §四；
  ⑤ 发布安全（非 final 字段跨线程发布站点补扫）→ 见 §五。
- **判级口径**: 沿第 1 轮 2468 口径——框架层默认并发调用判级；仅启动期单线程触达的站点条件化降档；P0 = 默认用法 + 常见条件即触发且不可逆。

---

## 一、目标①：C1-007 失效面扩展——「配置刷新发布」族写/读路径全枚举

### 1.1 写路径全枚举（nop-config 拥有的全部 DefaultConfigReference 写点，共 7 处）

| # | 写点 | 字段 | 执行线程 | 同步机制 |
|---|---|---|---|---|
| W1 | `DefaultConfigProvider.applyChangeToUsed` L297-315 | location + provider（经 updateValue 两步） | configExecutor 刷新线程（运行期） | **无**（C1-007 已立案主路径） |
| W2 | `DefaultConfigProvider.updateRefs` L133-153（changeConfigSource 启动切换） | location + provider | main（doStart L484） | 启动期时序 |
| W3 | `DefaultConfigProvider.updateRefs` L155-171（`BaseTestCase.isTestRunning()` 分支，makeConfigRef 后 ref.updateValue） | location + provider | **任意测试线程（运行期）** | **无** |
| W4 | `AbstractConfigProvider.reset` L73-78 | location + provider | 测试框架 afterAll 线程 | **无** |
| W5 | `AbstractConfigProvider.assignConfigValue` L82-87 | location + provider | **任意调用线程**（AppConfig.hostId() 运行期即走此路径，见 W7） | 仅 hostId 场景有 `synchronized(CFG_HOST_ID)`（不排他于 W1 刷新线程） |
| W6 | `AbstractConfigProvider.updateConfigValue` L89-93 | location + provider | 任意调用线程 | **无** |
| W7 | `AppConfig.hostId()` L44-56 → `s_provider.updateConfigValue(CFG_HOST_ID, hostId)` | location + provider（经 W6） | **任意请求线程首次调用** | synchronized(CFG_HOST_ID) 仅互斥 hostId 自身，不互斥 W1/W4 |

补充第三字段写点：`DefaultConfigProvider.setConfigModel` L117 `ref.setDefaultValue(varModel.getDefaultValue())`（main 启动期，对**已发布且可能已被并发读**的 usedRefs 原地写）。

### 1.2 字段本体与写序（api-core 对照，字段不立案、移交面精确化）

- `AbstractConfigReference`（api-core）：`location`（L13，裸）、`defaultValue`（L16，裸）、`setLocation` L29-31、`setDefaultValue` L42-44。
- `DefaultConfigReference`（api-core）：`provider`（L15，裸）、`setProvider` L37-39、`updateValue` L54-57 **先 `setLocation(loc)` 后 `setProvider(provider)`——两步裸写，无任何单引用原子发布**。
- 读侧 `IConfigReference.get()` 默认实现（api-core L45-51）：`getAssignedValue()` → `provider.get()`，null 时回退 `getDefaultValue()`——**provider 与 defaultValue 两个裸字段共同构成热读路径**，任何只覆盖 provider/location 的修复都留下 get() 兜底读的裸面。
- **同包不对称证据**：`PlaceholderConfigReference`（api-core L176-179）已对缓存字段 volatile 化并注释「配置引用会被多线程并发get()，缓存字段的发布/更新必须保证跨线程可见」——同包内 `DefaultConfigReference`/`AbstractConfigReference` 未同步升级，证明可修而未修（非设计取舍）。
- `configValue` 本体：`StaticValue`（final value，不可变）、`ValueWithLocation`（final loc/value，不可变）、`castType` 匿名包装（无状态，委托 provider.get()）、`DefaultConfigValueEnhancer`（cipher final）——**locator/值链全部不可变，刷新族的裸写面收敛在 Reference 三字段本身**。
- `DefaultConfigProvider` 自身字段复核：`configSource`/`configModel`（裸，启动期写）被刷新线程 `applyChange`/`buildConfigRefs` 读取——HB 经 `changeApplier.activate()`（doStart L231，晚于全部启动期写）→ executor submit 边建立，**条件安全**；`configRefs`/`configValues` volatile（第 1 轮已判定）；`configModel==null` 时 buildConfigRefs/createRef 均空容错（启动期文件源早触发场景安全）。

### 1.3 读路径全枚举（get()/ref.get() 全站点）

| # | 站点 | 读什么 |
|---|---|---|
| R1 | `AbstractConfigProvider.getConfigValue` L97-113 → `ref.get()` | provider + defaultValue 兜底（`AppConfig.var` 全平台最热入口） |
| R2 | 静态生成引用 `ApiConfigs.CFG_*.get()`（`AppConfig.varRef` 产物；框架层 src/main 直读站点 grep 计 20 处，遍及 nop-ioc/nop-config/nop-boot） | provider + defaultValue |
| R3 | `AbstractConfigProvider.collectUsedRefs` L127（getConfigValueForPrefix 批量前缀读） | provider |
| R4 | `DefaultConfigProvider.collectUnusedValues` L78 `getConfigRef(key).get()` | provider；注意该路径从 **configRefs 代际表**取 ref，读到旧代快照（自愈性陈旧，非新增缺陷） |
| R5 | `DefaultConfigProvider.traceConfigVars` L190-204 | **getLocation() + isDynamic()（=provider.isDynamic()）+ get() 三者连读**——location→provider 两步写序下可观测撕裂对（新 loc + 旧 value） |
| R6 | `getProvider()` 直取：`castType` L319/L321（刷新构建期）、`AppConfig.withPlaceholder` L96（instanceof 判别） | provider |
| R7 | `IConfigReference.map(fn)` 惰性 supplier 包装 | 任意线程延迟 get() |
| R8 | `AppConfig.hostId` L47-50 | get() + updateConfigValue（读-改-写全局配置面） |

### 1.4 跨单元移交面精确化（结论）

- **api-core 侧修复物**：`AbstractConfigReference.location`/`defaultValue`、`DefaultConfigReference.provider` 三字段 volatile 化，或重构为**单一不可变 holder（loc+provider+defaultValue 三元组）+ 单 volatile 引用**（推荐，同时消除两步写序撕裂）；`IConfigReference.get()` 默认实现不动。
- **nop-config 侧立案锚点**：上表 W1~W7 七处写路径全部经同一裸字段协议——修复 api-core 字段后七处自动闭合，无需逐点改写；但回归面必须覆盖 W3~W7（测试框架 + hostId 运行期路径），不能只回归刷新链。
- **回归面**：全平台配置读（R1~R8）+ `@r-cfg` 响应式 bean 属性重绑定（BeanDefinition.subscribeConfigChange，第 1 轮 C1-007 已列第二暴露层）。

---

## 二、目标②：插件状态机迁移全表（C1-010/011/012 家族矩阵收口）

### 2.1 VfsPluginDefinition（state volatile；lifecycleLock 仅覆盖 activate/doDeactivate）

| 方法 | 锁 | 守卫 | 迁移 | 孤儿/卡死后果判定 |
|---|---|---|---|---|
| `load` L357-363 | **无锁** | **无任何状态守卫（任意态可迁）** | →LOADED | 与 ACTIVATED 交错时把活容器降级为 LOADED：getScope() 返回 null、deactivate 幂等 no-op（state≠ACTIVATED）→ **容器孤儿**。manager 自身只在 computeIfAbsent 内对新定义调用（不可达），接口直调可达（宿主误用面）；同族后果归 C1-010 修复（load 进 lifecycleLock + 守卫） |
| `unload` L366-377 | **无锁** | 守卫读（ACTIVATED/中间态抛） | →UNLOADED | **C1-010 主案**：守卫读与迁移写不原子，与 reconcile activate 正向交错 → 活容器泄漏 + 状态机破坏（UNLOADED 但活跃） |
| `activate` L380-424 | lifecycleLock | 幂等/中间态抛/门控 | ACTIVATING→ACTIVATED/FAILED | 锁内单飞，安全（in-flight 注释与实现一致） |
| `doDeactivate` L434-458 | lifecycleLock | ≠ACTIVATED no-op | DEACTIVATING→LOADED | 锁内；state/scope/container 清空与 close/stop 顺序正确 |
| `start` L550-563 | **无锁** | `state==UNLOADED` 读（L555） | →load（无锁）→activate（锁） | **C1-010 F3 已列**：check-then-act 跨两次迁移，与 unloadPlugin/unload 交错同 C1-010 后果；另有与并发 load(config) 交错丢定义配置（弱化面） |
| `stop` L566-570 | **无锁**（deactivate 段有锁，unload 段无） | 无 | deactivate→unload | **C1-010 F3 已列**：deactivate 完成（LOADED）与 unload 守卫读之间，reconcile 可再激活 → unload 正向交错孤儿（同 C1-010 主案第二入口） |
| `updateConfig` L573-593 | **无锁** | `state==ACTIVATED` 读（L580） | 不迁移 state；definitionConfig 引用替换 | 与 activate 的 ACTIVATING 窗口交错 → **热应用静默丢失**（新立案 C1-020）；与并发 updateConfig 丢合并（C1-013 已立案） |
| `onActivationFailed/Succeeded` L345-355 | lifecycleLock 内 | — | activationFailures/autoActivationPaused | C1-013（RMW 良性） |

### 2.2 AbstractPlugin（state/beanContainer/definition 全裸——C1-011 已立案）

| 方法 | 锁 | 守卫 | 迁移 | 判定 |
|---|---|---|---|---|
| `load` L132-148 | **无锁** | 无守卫 | →LOADED | C1-011 家族（jar 轨 computeIfAbsent 挡并发 load，unload 交错可达） |
| `unload` L151-164 | **无锁** | 守卫读（aware） | →UNLOADED | C1-011(b)：与 activate 锁内迁移正向交错同 C1-010 后果 |
| `activate` L167-202 | lifecycleLock | 幂等/中间态/非 LOADED+FAILED 抛 | ACTIVATING→ACTIVATED/FAILED | 锁内安全 |
| `doDeactivate` L256-274 | lifecycleLock | ≠ACTIVATED no-op | DEACTIVATING→LOADED | 锁内安全；**L268-271 autoInit 时在锁内调用 `CoreInitialization.destroy()`（全局拆除）→ CL-014 线索** |
| `start` L310-335 | **无锁** | `state==UNLOADED` 读（L319） | groupId/Artifact/Version 裸写 →load→activate | C1-011 家族；坐标三字段非 volatile（第 1 轮 F3 条件安全判定维持；错误路径 describePluginId 跨线程读最坏读到 null 走类名兜底，无正确性破坏） |
| `stop` L338-347 | **无锁**（deactivate 段锁内、unload 段无锁） | 无 | 同上 | C1-010/011 家族 |
| `doStop` L350-358（compat 轨） | LifeCycleSupport 机制 | — | 读 beanContainer → container.stop() | compat 单线程生命周期，条件安全；beanContainer 裸读并入 C1-011 |
| `invokeCommandAsync` L395-409 | **无锁** | `getState()!=ACTIVATED` 读（L400）→ getCommandBean 读 beanContainer | — | **第 1 轮 C1-012 F3 七站点清单的遗漏兄弟（第 8 站）**：state 检查后 deactivate 可翻转 → container.stop() → 在途命令在已停容器上解析 bean（checkStarted 抛 NOT_STARTED，异常类型漂移同 C1-012）；state 裸读（C1-011）叠加可见性面。**并入 C1-012 修复批次，不另立案** |

### 2.3 PluginManagerImpl（编排侧补全站点）

| 方法 | 锁 | 守卫 | 判定 |
|---|---|---|---|
| `reconcile/doReconcile` L226-300 | reconcileLock + inFlight/dirty | state 读 L287-288 | 第 1 轮判定安全（重入 dirty 设计正确）维持 |
| `activateIfInactive` L387-400 | reconcileLock | getState 复读 + isAutoActivationPaused | def.activate 锁内复读兜底；异常捕获收敛——安全 |
| `deactivateIfActive` L402-413 | reconcileLock | getState 复读 | 同上 |
| `unloadPlugin` L145-165 | **无锁** | holder!=null + def.unload 内守卫 | **C1-010 主案**（不持 reconcileLock）；`plugins.remove` 与 `unregisterNameIndex` 间窗口见 C1-018/C1-019 |
| `activatePlugin/deactivatePlugin` L168-185 | **无锁** | requireLoaded | **C1-010 修复建议已列**（纳入 reconcileLock）；与 reload 交错见 C1-019 |
| `reloadPlugin` L638-688 | reconcileLock（可重入） | L650/L658 state 复读 | 自身段内一致；但 `L677 remove → L683 put` 窗口对**不持锁的 loadPlugin** 开放 → **C1-019 双活定义** |
| `checkChangedAndReload` L606-626 | 无锁 | state 读 L609-610 + lastModified | reloadPlugin 未包 try（仅 checkChanged 有）→ 集合中任一 def 已被并发 unload 时 NOT_LOADED 异常冲出整轮检查（宿主轮询中断，弱化面，登记 matrix 不立案）；reloadPlugin 自身持锁复读（L650）挡住状态竞态 |
| `loadPlugin/loadPluginFromVfs/Jar` L130-142/L492-500/L567-596 | CHM computeIfAbsent（per-key） | map 槽位 | 槽位级原子，但**与 reload/unload 的 remove/put 窗口无互斥** → C1-019；compute 内 `plugin.load/start` 在 bin 锁内（CL-002 已登记） |
| `hasActivatedPluginByName(Excluding)` L772-795 | reconcileLock（reconcile 调用时） | holder.plugin.getState()==ACTIVATED | **跨定义复合门控 TOCTOU**：评估通过后被评估方被**不持 reconcileLock 的 deactivatePlugin** 去激活 → A 带失效依赖激活（运行期 INACTIVE 错误）。根因同 C1-010（生命周期操作未与 reconcile 串行化），修复同源（activatePlugin/deactivatePlugin 纳入 reconcileLock 后闭合），matrix 登记 |
| `registerNameIndex/unregisterNameIndex` L527-541 | **无锁** | remove→isEmpty→map.remove 三步 | **C1-018 立案**（见下） |

**② 收口结论**：第 1 轮 8 处锁外迁移 + 7 处复合读之外，本轮补全 3 站（AbstractPlugin.invokeCommandAsync→C1-012 家族第 8 站；VfsPluginDefinition.load 无守卫任意态迁移→C1-010 修复批次；跨定义门控 TOCTOU→C1-010 同源）+ 新立案 2 条（C1-018 索引孤儿、C1-019 注册表窗口双活）+ 1 条 P3（C1-020）。未发现新的卡死（永久等待）形态站点：全部交错后果均为孤儿/泄漏/异常漂移，无 monitor 循环等待。

---

## 三、目标③：check-then-act 全扫判定表

grep 框架层 `if (running)/if (closed)/if (state...)/== PluginState/s_instance == null` 全站点（基线 C1 节 + live 扫描），逐站点原子性判定：

| 站点 | 守卫与迁移原子性 | 判定 |
|---|---|---|
| `BeanContainerImpl.start` L477 `if(running)` → L483 迁移 | check-then-act 无锁 | C1-001 已立案（复合状态窗口根因） |
| `BeanContainerImpl.checkStarted` L619-622 `!started && !running` 双 volatile 复合读 | 非原子；stop 方向窗口：running=false 后 started 尚 true → 检查通过 → getBean 进入正在 close 的 singletonScope → BeanScopeImpl checkClosed 抛 ALREADY_CLOSED（BeanScopeImpl add/get 均守卫） | C1-001 同根因 stop 方向变体：后果为在途请求瞬态异常（无泄漏——scope.add 的 synchronized(beans)+checkClosed 消除注册窗口），matrix 登记不另立案 |
| `BeanScopeImpl.add/close/get` L48/L73/L90 | synchronized(beans) 内 checkClosed+put / closed 置位 | 原子（第 1 轮正面确认维持） |
| `JdbcConfigSource.refreshConfig` L72 `if(closed)` 与 `close()`（无锁） | 非原子 | C1-009 已立案 |
| `PluginScopeImpl.effect/close` L48/L73 | synchronized(effects) | 原子（正面确认维持） |
| `VfsPluginDefinition.unload` L369 / `activate` L382-392 / `doDeactivate` L436 / `start` L555 / `updateConfig` L580 / `getScope` L327 / `requireActiveScope/Container` L653/L661 | unload/start/updateConfig/getScope/require* 无锁、activate/doDeactivate 锁内 | C1-010/012/020 已立案或本表 §二收口 |
| `AbstractPlugin.unload` L155 / `activate` L173-182 / `doDeactivate` L258 / `start` L319 / `invokeCommandAsync` L400 | 同上分布 | C1-011/012 家族（§二） |
| `PluginManagerImpl.doReconcile` L287-292 / `computeDeactivationGroup` L315 / `activateIfInactive` L388 / `deactivateIfActive` L403 / `checkChangedAndReload` L610 / `reloadPlugin` L650/L658 / `hasActivatedPluginByNameExcluding` L790 | reconcileLock 内复读 + def 侧 lifecycleLock 兜底 | 编排面收敛（对无锁生命周期操作开放的面已归 C1-010/C1-019） |
| `ConfigChangeApplier.requestUpdate/applyChange` | AtomicBoolean CAS flag+全量重同步 | 原子（正面确认维持） |
| `LoggerConfigurator.instance` L32 `s_instance==null` | 只读判空抛异常（非迁移） | 安全 |
| `PluginManagerImpl.reconcile` L228 `reconcileInFlight` | synchronized(reconcileLock) 内 | 原子 |

**③ 结论**：C1-004/005 同族（「守卫读→迁移写不原子」）在框架层已穷举——生产代码新增可立案同族站点为零，全部交错面已收敛进 C1-001/004/005/009/010/011/018/019/020 的既有或本轮修复批次。

---

## 四、目标④：单例槽位与静态状态

### 4.1 static non-final 全枚举（src/main，共 12 处）

| 字段 | 判定 |
|---|---|
| `ConfigStarter.g_instance`（volatile，L77，类初始化建默认实例 + registerInstance 替换） | volatile 发布，安全 |
| `BeanScopeContext._instance`（volatile，L19） | 安全（第 1 轮维持） |
| `LoggerConfigurator.s_instance`（volatile，L19） | 安全（instance() 判空抛错非惰性迁移） |
| `DeltaJavaMerger/DeltaClassMerger/DeltaEnumMerger/JavaParserCodeFormatter.INSTANCE`（nop-java-parser） | static 初始化器唯一赋值、全仓无再赋值（grep 证实）——JLS 12.4 类初始化发布，安全 |
| `Or/And/PatternMatchPatternCompiler.INSTANCE`（nop-match） | 同上，安全 |
| `JavaLoggerConfigurator/LogbackConfigurator.INSTANCE`（nop-log-java/logback） | 同上，安全 |
| 基线已列：`MatchPatternCompilerRegistry.DEFAULT`（final+CHM）、`ShellRunner.WindowsProcessLaunchLock`（final+synchronized） | 安全（维持） |

**④-静态结论**：运行期写点为零（12 处均为「static 初始化唯一赋值」或「volatile 单发替换」），api-core 越界对照（`AppConfig.s_provider` volatile、`BeanContainer` static volatile registerInstance）同样安全。**无新立案。**

### 4.2 BeanContainerImpl 容器字段（C1-001 singletonScope 之外的补全）

| 字段 | 写者 | 读者 | 判定 |
|---|---|---|---|
| `running`/`started`（volatile L70-71） | start L483/L509/L516、stop L597-598 | checkStarted L620 | 单字段 volatile 安全；**复合读窗口**（stop 方向）见 §三 matrix，C1-001 同根因 |
| `singletonScope`（裸 L81） | start L484 | getBeanScope L439（每请求） | C1-001 已立案 |
| `startFuture`（裸 L90） | start L480/L503 | awaitStartFinished L537 | C1-001 F3 已列；**本轮补全实测**：全框架层 awaitStartFinished 无生产调用方（仅接口默认实现），宿主直调面才暴露，后果维持「轻」 |
| `cancellable`（裸 L86） | asyncStartBeans L556、stop L599-601 | stop | C1-001 F3 已列（cancel 丢失），维持 |
| `configProvider`/`classLoader`/`startMode`/`concurrentStart`/`aliases`（裸） | 启动期 setter/start | 运行期 | 启动期写 + 容器安全发布（volatile/static 槽位）→ 条件安全。**正面对照实测**：VfsPluginDefinition.activateInternal L475-483 的 setConfigProvider→start→volatile 字段发布顺序正确（写先于 `this.container` volatile 发布，读者必然可见） |
| `beansByType`/`beansByAnnotation`（CHM L73-74） | BeanFinder 惰性 computeIfAbsent | 每请求 | 安全（维持，compute 内全表扫描归 C2/C9） |

**④-容器结论**：C1-001 的「容器生命周期状态无复合同步」根因之下，其余字段均为已登记 F3 面或条件安全，无新增独立立案。

---

## 五、目标⑤：发布安全（非 final 字段跨线程发布站点补扫）

对框架层全部 `private` 非 final 非 volatile 对象字段扫描（nop-ioc/nop-config/nop-plugin×3/nop-security/nop-log×4/nop-boot src/main），逐类判定：

| 载体 | 判定 |
|---|---|
| `IocCoreInitializer.appContainer/parentContainer`（L23-24，裸） | initialize（main）写、destroy（main/stop）读；容器本体经 `BeanContainer.registerInstance`（api-core static volatile）安全发布——字段自身生命周期封闭，安全 |
| `ProducedBeanInstance.proxyHandler`（L49，裸） | `setBeanWithProxyHandler`/`setHandler` 均 synchronized——monitor 内读写，等待者经 status/wait 建立 HB，安全（与 C1-003 的裸面仅在 `DelegateInvocationHandler.handler` 本体，已立案） |
| `UtilFactoryBean.value`（L11） | IoC 属性注入期单创建线程写（ProducedBeanInstance monitor HB），安全 |
| `BeanCreationContext.initActions/lazyPropActions/delayActions`（L14-18） | per-creation 线程封闭（concurrentStart 下 graph 任务单线程持有），flushActions 后经 HB 消费，安全 |
| `BeanDefinition` 装载字段（beanClass/supplier/initMethod 等 L102-133） | BeanDefinitionBuilder 装载期单线程写 + 容器发布 HB，安全；`subscriptionCleanup`/`inCreationThread` 已立案（C1-004/安全） |
| 装载器（BeanContainerBuilder/AopBeanProcessor/BeanDefinitionBuilder/BeanConditionEvaluator 的 map 字段） | 构建期线程封闭，安全 |
| `DelegateInvocationHandler.handler`（L14） | C1-003 已立案 |
| `AbstractPlugin.state/beanContainer/definition` | C1-011 已立案 |
| `JdbcConfigSource.connection` | C1-009 已立案 |
| `DefaultKeyManager.keyStore/storePassword` | C1-016 已立案 |
| `SingleThreadConfigExecutor.executor`（裸） | start 写（doStart）→ execute 读（config 回调线程）：HB 经启动时序；stop 后 execute 并发面已并入 CL-001 场景，维持 |
| `PluginManagerImpl.resourceResolver/pluginConfigProvider/globalConfigProvider`（@Inject 裸 setter 字段） | IoC init 期注入 → reconcile 线程读：bean 初始化先于订阅注册（loadPlugin 时 manager 已存在）；early-reconcile 窗口 globalConfigProvider 为 null 时走 AppConfig 兜底（L124-127）——条件安全 |
| `VfsPluginDefinition.lastChangeTime/loadTime`（裸 L105-106，**Timestamp 可变对象**） | load（管理线程/compute 内）写、getLastChangeTime/getLoadTime（IPlugin 消费方）读——无 HB；消费面仅描述/诊断（reconcile 变更检测用 volatile lastModified，不读这两个）→ 可见性缺陷真实但后果止于诊断值陈旧；**Timestamp getter 逃逸可变对象**同族归低危。并入 §七「低危补全清单」不单独立案（第 1 轮 C1-011 F3 已对 AbstractPlugin 同名字段做条件安全判定，本条为 Vfs 轨对称补记） |

**⑤ 结论**：C1-011 三字段之外的同族发布站点已全量核查，无新增立案；两处低危补记（Vfs 时间戳字段、checkChangedAndReload 异常中断）登记于 §七。

---

## 六、发现条目（本轮新增）

### [nop-core-framework-C1-017] DefaultConfigReference 发布协议字段面扩展：defaultValue 第三字段裸写裸读、updateValue 两步写序撕裂、运行期第二写点（hostId/updateConfigValue/测试路径）与刷新线程写-写竞争

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/impl/DefaultConfigProvider.java:117, 144-151, 163-166, 297-315`、`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/config/AbstractConfigProvider.java:73-93`、`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/config/AppConfig.java:44-56`（写路径锚点；字段本体 `DefaultConfigReference.java:15, 54-57`、`AbstractConfigReference.java:13, 16, 29-31, 42-44`、读侧 `IConfigReference.java:45-51`——api-core 对照不立案）
- **证据片段**:
  ```java
  // DefaultConfigReference（api-core 对照）——两步裸写，location 先于 provider
  public void updateValue(SourceLocation loc, IConfigValue<T> provider) {
      setLocation(loc);            // plain write ①
      setProvider(provider);       // plain write ②
  }
  // IConfigReference.get()（api-core 对照）——provider 与 defaultValue 双裸字段共同构成热读路径
  default T get() {
      T value = getAssignedValue();       // provider.get() —— 裸读
      if (value == null) value = getDefaultValue();   // defaultValue —— 裸读
      return value;
  }
  // AbstractConfigProvider.updateConfigValue（nop-config 写路径 W6）
  public <T> void updateConfigValue(IConfigReference<T> ref, T value) {
      dirty = true;
      DefaultConfigReference<T> df = makeConfigRef(null, ref.getName(), ref.getValueType());
      df.updateValue(ref.getLocation(), StaticValue.build(...));   // 与刷新线程 W1 同字段裸写
  }
  // AppConfig.hostId —— 运行期任意请求线程经 W6 写 nop.host-id 引用
  ```
- **严重程度**: P1
- **并发场景**: 与 C1-007 同根因（刷新写 vs 全平台读无 HB），但本轮扩展出三类第 1 轮未覆盖的失效面：(a) **第三字段**——`defaultValue` 参与 `get()` 兜底读热路径，`setConfigModel` L117 启动期对已发布 usedRefs 原地写，任何只 volatile 化 provider/location 的修复留裸；(b) **写-写竞争**——`AppConfig.hostId()` 运行期任意请求线程首次调用经 W6 对 `nop.host-id` 引用裸写（仅 synchronized(CFG_HOST_ID)，不排他于刷新线程 W1），`nop.host-id` 一旦出现在刷新批（运行期新增该键）即与 applyChangeToUsed 对同一 DefaultConfigReference 无互斥双写（丢失更新 + 撕裂）；测试框架 W3/W4/W5 在测试运行期任意线程写同理；(c) **跨字段撕裂**——updateValue 两步写序下，R5（traceConfigVars 连读 location+get()）与错误路径 getLocation() 可观测「新 loc + 旧 value」或反向撕裂对。
- **触发时序**: (a) T2 请求线程 `CFG_X.get()` 值为 null 走 `getDefaultValue()`——若该 ref 的 defaultValue 曾被 setConfigModel 写入而 T2 线程池创建早于该写且无后续同步动作，JMM 下可无限期读到旧 null；(b) T1 刷新线程 applyChangeToUsed 对 `nop.host-id` 执行 `updateValue(loc, castType(...))` 的同时 T3 请求线程首调 `AppConfig.hostId()` 走 `updateConfigValue` 裸写 StaticValue(uuid)——两线程对同两字段无任何公共锁/CAS，后写覆盖前写（UUID 分配被配置值覆盖或反之），且中间态可被并发读者观测；(c) T1 执行 setLocation 新值后、setProvider 前被切出，T2 traceConfigVars 读到新 location 打印旧值。
- **现状**: 第 1 轮 C1-007 已立案「刷新线程写/请求线程读」的可见性面，修复建议为「provider/location 升级 volatile 或 VarHandle setRelease」——本轮证明该修复建议**不完备**：defaultValue 未覆盖、两步写序撕裂未消除、W3~W7 五个非刷新写点未纳入回归面。
- **风险**: 运维改配置不重启的「语义违约」面扩大（兜底缺省值陈旧 + hostId 类运行期写点丢失更新）；trace/诊断日志撕裂误导排障；修复批次若按 C1-007 原建议执行将留下静默裸面（**remediation-blocking**）。
- **建议**: 单一不可变 holder（loc+provider+defaultValue 三元组 record）+ 单 volatile 引用替换，updateValue 改为整体替换 holder（一处修复同时闭合 C1-007 与本条全部三类面）；api-core 修复归属跨单元流转（同包 `PlaceholderConfigReference` L176-179 已有 volatile 先例注释，证明该包已认可「配置引用并发 get」前提）；nop-config 侧回归面必须覆盖 W1~W7 七写点 × R1~R8 八读站（§一清单即回归清单）。
- **失效面枚举**: 写路径 W1~W7 全表（§1.1，七处同字段同修）；字段面 = location + provider + **defaultValue**（第 1 轮仅前两者）；撕裂面 = R5 traceConfigVars、错误路径 getLocation() 消费方；写-写面 = hostId × W1（同 ref）、W3/W4/W5 测试路径 × W1（测试运行期）、并发 updateConfigValue × updateConfigValue（synchronized(CFG_HOST_ID) 仅排自身）；`DefaultConfigProvider.configSource/configModel` 启动期字段经 changeApplier.activate HB 复核为条件安全（§1.2，不扩面）。
- **信心水平**: 确定（字段/写点/读站逐行枚举自 live code；写-写交错为 JMM 确凿数据竞争，实践触发窗口窄但语义违约面与修复不完备性确定）
- **误报排除**: 非对 C1-007 的重复立案——新增三类独立失效面（第三字段、写-写竞争维、跨字段撕裂维）与修复面完备性证明；非把 api-core 字段越界立案（锚点仍为 nop-config 写路径 + api-core 移交面）；hostId 写点非理论路径（`AppConfig.hostId` 是公开 API，首次调用即触发）。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留，**终裁并入 C1-007 族案（合并立案，不独立计数 P1）**——a) 三字段裸写裸读、b) 刷新写 vs 请求读无 HB、c) hostId 运行期写点（W7→W6，synchronized(CFG_HOST_ID) 不排他 W1）全部亲核成立；W 写点抽验 W1~W6 直呼站点 + W7 转运口全核；W3 线程精度修正（第 3 轮：doStart main，L214→L484）独立确认成立；d) 修复载体裁定合理——api-core 单一不可变 holder+单 volatile 引用为最小完备修复（逐字段 volatile 不能闭合 R5 traceConfigVars L191-201 连读撕裂）；e) 关系终裁=同根因同字段面同修复载体的同一缺陷，并入 C1-007 作为修复面完备性扩展，双编号保留追溯，统计按 1 条 P1 族案计。备注：面 (a) defaultValue 兜底旧值实践上多被任务提交 HB 边闭合（纯 JMM 理论面），合并案 P1 由刷新可见性面+写-写竞争+撕裂面独立支撑（详见维度复核结论特别指令 1/争议点⑤）。现状=新发现。

### [nop-core-framework-C1-018] namesToPluginIds 依赖名索引 unregister 三步非原子：与 registerNameIndex 并发孤儿化索引集，requires 门控永久失效并级联误去激活

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/PluginManagerImpl.java:527-541, 780-795`
- **证据片段**:
  ```java
  private void registerNameIndex(VfsPluginDefinition plugin) {
      String name = plugin.getName();
      if (name == null) return;
      namesToPluginIds.computeIfAbsent(name, k -> ConcurrentHashMap.newKeySet()).add(plugin.getPluginId());
  }                                                                    // get-set 与 add 非原子
  private void unregisterNameIndex(String pluginId) {
      for (Map.Entry<String, Set<String>> entry : namesToPluginIds.entrySet()) {
          if (entry.getValue().remove(pluginId) && entry.getValue().isEmpty()) {
              namesToPluginIds.remove(entry.getKey());                     // remove→isEmpty→map.remove 三步无锁
          }
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 同名插件的卸载（`unloadPlugin` L160 / `reloadPlugin` L672/L678）与加载（`loadPluginFromVfs` compute 内 L496 / `reloadPlugin` L681）在两个线程并发；两方法均不持 reconcileLock（unloadPlugin 无锁、loadPlugin 仅 CHM 槽位锁）。
- **触发时序**: T1（unload 最后一个 name1 定义）`unregisterNameIndex`：`S1.remove(id)` → S1 空 → `isEmpty()` 判 true →（切出）；T2（loadPlugin 同名新定义）`registerNameIndex`：`computeIfAbsent(name1)` **取回尚在 map 中的 S1** → `S1.add(newId)`；T1 恢复：`namesToPluginIds.remove(name1)` **无条件执行**（不再复查空性）→ S1（含 newId）成为孤儿集，map 中 name1 无条目。此后 `hasActivatedPluginByName(name1)` L781 `namesToPluginIds.get(name1)==null` → false：依赖 name1 的全部定义 requires 腿永久判不满足——已激活的依赖方被 `computeDeactivationGroup` 级联去激活、待激活方永不自动激活；直至下一次同名定义 register/unregister 往复才可能恢复。
- **现状**: CHM 保证 per-key 原子与弱一致遍历，但「判空后摘键」与「取集后添加」两个复合动作之间无任何互斥/重试；注释未声明 unregister 与 register 的串行化边界。
- **风险**: 管理操作并发（运维卸载撞上宿主/服务解析触发的懒加载，或 HMR reload × load）下依赖门控静默失效——无异常、无日志（get(name)==null 是合法「未加载」语义），表现为插件依赖服务 INACTIVE，难归因；reconcile 幂等性无法自愈（不动点迭代每次都读到同一孤儿态）。
- **建议**: unregister 判空摘键改为原子化：`namesToPluginIds.computeIfPresent(name, (k, v) -> { v.remove(id); return v.isEmpty() ? null : v; })`（CHM mapping function 内原子，天然消除窗口）；或 register 侧 add 后二次校验「自身仍在 map 中，否则重试」。纳入 reconcileLock 亦可（与 C1-010 修复批次同锁域）。
- **失效面枚举**: 同类「非并发容器宿主编码容器 check-then-act」全量：`registerNameIndex`（本条对偶面）、`unregisterNameIndex` 遍历所有 entry（`remove(pluginId)&&isEmpty` 在单 entry 内两步，本身对同 id 并发 unregister 是 CHM set 原子 remove + 幂等 map.remove——安全）；`namesToPluginIds` 其余读者（resolveDepPluginIds L467、hasActivatedPluginByName L781）只读安全；`configSubscriptions`（CHM put/remove 单键原子，安全）。生产代码仅此一处「判空摘键」形态。
- **信心水平**: 确定（交错序列逐行成立；CHM 无 mapping 级复合原子性是 JDK 语义事实）
- **误报排除**: 非「CHM 已线程安全」误报——缺陷恰在 CHM 之上的复合序列；非 C1-010 重复——C1-010 是 def 状态机 vs reconcile，本案是 manager 侧 name 索引的独立数据结构竞态，后果（门控永久失效）与修复（computeIfPresent）均不同。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P2——registerNameIndex computeIfAbsent→add 与 unregisterNameIndex remove→isEmpty→remove 三步（L527-541 段）亲核；孤儿集→hasActivatedPluginByName get==null→门控静默失效且 reconcile 不可自愈链成立。维持 P2。现状=新发现。

### [nop-core-framework-C1-019] loadPlugin 与 reloadPlugin/unloadPlugin 注册表窗口竞态：computeIfAbsent 落入 remove→put 窗口产生双活定义、全局配置订阅清理丢失

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/PluginManagerImpl.java:638-688, 492-500, 145-165`
- **证据片段**:
  ```java
  // reloadPlugin（持 reconcileLock；但 loadPlugin/unloadPlugin 均不持该锁）
  plugins.remove(pluginId);              // L677
  unregisterNameIndex(pluginId);         // L678 —— 摘除「同名共 id」的名字索引
  unsubscribeGlobalConfig(pluginId);     // L679 —— remove+run cleanup（map 单键）
  newDef.load(definitionConfig);         // L680
  registerNameIndex(newDef);             // L681
  subscribeGlobalConfig(newDef);         // L682 —— 覆盖 configSubscriptions[pluginId]
  plugins.put(pluginId, new PluginHolder(newDef, null));   // L683 —— 覆盖窗口内他人放入的 holder
  // loadPluginFromVfs：plugins.computeIfAbsent(pluginId, id -> { loadVfsDefinition; plugin.load;
  //                 registerNameIndex; subscribeGlobalConfig; return holder; })   —— 仅 CHM 槽位锁
  ```
- **严重程度**: P2
- **并发场景**: HMR 热重载（宿主轮询 `checkChangedAndReload` → reloadPlugin，或显式调用）与运行期懒加载（宿主/服务解析 `loadPlugin(sameId)`）并发；reload 持 reconcileLock 对 loadPlugin 无约束力。
- **触发时序**: T1 reloadPlugin 已过 L658 状态检查、进入 L677-683 段；T2 `loadPlugin(id)` 的 computeIfAbsent 在 L677 之后、L683 之前完成 lambda：**第二份定义对象** T2def 完成 load/registerNameIndex/subscribeGlobalConfig 并被 CHM put → T1 的 L683 put **覆盖** T2def 的 holder。后果链：(1) T2def 成为**孤儿活定义**——持有者（T2 调用方）可对其 activatePlugin → 第二套子容器 + activator 副作用与 T1 的 newDef **双活**（reconcile 只见 map 中 newDef，孤儿永不 deactivate）；(2) T2 的 subscribeGlobalConfig 写入的 cleanup 先于 T1 的 L682 被覆盖 → **cleanup Runnable 丢失**，T2def 的全局配置监听器永久留在 config provider 上（每次相关配置变更触发一次 reconcile——幂等但累积）；(3) 名字索引 set 按 pluginId 去重，两 def 同 id 不出错（弱化面）。unloadPlugin 侧对应交错（T2 落入 unload→remove 窗口取到 UNLOADED 旧 holder）为瞬态 NOT_LOADED，可接受（第 1 轮 C1-012 requireLoaded 已述），不并入本案。
- **现状**: reloadPlugin 的 remove→put 非原子槽位迁移只考虑了「自身可重入 reconcile」，未考虑不持 reconcileLock 的 loadPlugin 读者；loadPlugin 的 computeIfAbsent 语义是「无则建」，无「正在被重建」的感知。
- **风险**: 插件双活 = 双倍容器/连接/线程资源 + 同名服务两份实例（持有者路由到旧 def，getPlugin 路由到新 def，行为分叉）；订阅泄漏随每次竞态累积。管理操作并发触发（dev/HMR 场景概率不低），后果不可自愈。
- **建议**: reloadPlugin 全程以 `plugins.compute(pluginId, ...)` 原子替换 holder（remove+put 合一），register/unregister 名字索引与订阅操作移入 compute lambda 内；或将 loadPlugin 纳入 reconcileLock 序列（与 C1-010 修复建议同批次统一）；双活面兜底：loadPlugin 命中 computeIfAbsent 后复读 state，UNLOADED 即抛定向异常。
- **失效面枚举**: 同族「注册表槽位复合迁移」全查：`unloadPlugin` L146-163（unload→remove 窗口，本案弱化面）、`reloadPlugin` L677-683（本案主面）、`loadPluginFromVfs/Jar` computeIfAbsent（槽位原子，作为被竞态方）、`configSubscriptions` put/remove（单键原子，泄漏面为「cleanup 被覆盖」语义缺陷而非容器竞态）、`namesToPluginIds`（索引竞态独立立案 C1-018）。生产代码无其他 remove→put 复合槽位。
- **信心水平**: 确定（窗口逐行成立；双活路径以「T2 调用方对返回 def 显式 activate」为前提，T2 仅持有不激活时后果降为订阅泄漏）
- **误报排除**: 非 C1-010 重复——C1-010 是 unload 与 reconcile-activate 在 def 状态机上的交错（后果 = 活容器 + 状态机破坏），本案是 map 槽位在 reload 段的重开窗口与懒加载碰撞（后果 = 双活定义 + 订阅清理丢失），站点、交错对、后果均不同；非「computeIfAbsent 已安全」误报——原子性仅覆盖单次槽位读改写，覆盖不了跨锁窗口。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P2——reloadPlugin L677-683 remove→put 窗口亲核；复核补充细节：T2 computeIfAbsent 持 CHM bin 锁执行慢 lambda（XML 解析）期间 T1 的 put 阻塞、T2 完成写入后被 T1 覆盖——交错可达性成立且非削弱；双活定义+cleanup 覆盖丢失后果成立。维持 P2。现状=新发现。

### [nop-core-framework-C1-020] updateConfig 热应用判定与 activate 过渡态竞态：ACTIVATING 窗口内的配置更新被静默丢弃至下次重激活

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/VfsPluginDefinition.java:460-462, 550-563, 573-593`
- **证据片段**:
  ```java
  // activateInternal（lifecycleLock 内）——配置域快照在激活开始时点一次性读取
  Map<String, Object> config = definitionConfig;          // L461
  DefinitionConfigProvider provider = new DefinitionConfigProvider(...);
  provider.setMergedView(config);                          // 旧快照送达子容器
  ...
  // updateConfig（无锁）
  Map<String, Object> merged = new LinkedHashMap<>(definitionConfig);
  merged.putAll(config);
  this.definitionConfig = merged;
  if (state == PluginState.ACTIVATED) {                    // L580 —— ACTIVATING 时为 false
      DefinitionConfigProvider provider = configProvider;
      if (provider != null) provider.updateMergedView(merged);   // 热应用被跳过
  }
  Runnable callback = onConfigChanged;                     // reconcile 回调
  if (callback != null) callback.run();                    // reconcile 只激活 LOADED/FAILED —— 亦跳过
  ```
- **严重程度**: P3
- **并发场景**: T1（reconcile/configExecutor 线程）正在 `activate()`：已置 state=ACTIVATING、快照 definitionConfig（旧）、构建子容器（慢 I/O 窗口）；T2（宿主线程）调用 `start(config)`（state≠UNLOADED 且 config 非空 → 走 updateConfig）或直接 `updateConfig(config)`。
- **触发时序**: T2 在 T1 处于 ACTIVATING 时执行 updateConfig：merged 已写入 definitionConfig 字段 → L580 `state==ACTIVATED` 判 false（ACTIVATING）→ 热应用跳过 → onConfigChanged → reconcile → doReconcile 对 ACTIVATING 无动作（仅激活 LOADED/FAILED）→ T1 完成：state=ACTIVATED、provider.mergedView = **旧快照**。结果：T2 的配置既未热应用到活容器、也无任何机制重放（下一次 reconcile 见 ACTIVATED 直接跳过），definitionConfig 字段值与 provider 视图**静默分叉**，直至下次 deactivate→activate（activateInternal 重读字段）才生效。
- **现状**: 热应用判定只认 ACTIVATED 终态，ACTIVATING 过渡态的更新既不等待也不重放；状态字段 volatile 只保证可见性，不构成「更新落在快照前还是后」的序语义。
- **风险**: 携带配置的插件启动/更新与自动激活并发时，配置静默不生效（无异常无日志）；依赖定义级配置域的门控行为与预期漂移。触发依赖「start/updateConfig 恰与 reconcile 激活并发」（自动激活由配置变更驱动，与带配置 start 并发现实可达），后果自愈周期未知（可能无限期）。
- **建议**: updateConfig 纳入 lifecycleLock（与 C1-013 的 merge 原子化建议同批次）：锁内复读 state，ACTIVATING 时改为「标记 dirty，activate 完成（ACTIVATED 置位前）锁内复查重放 updateMergedView」；或将 activateInternal 的 definitionConfig 快照改为激活完成前锁内终读。
- **失效面枚举**: 同形「状态判定的过渡态盲区」：`updateConfig` L580（本案）；`activate` L382 幂等检查（ACTIVATED 直接 return true——对 start 的幂等语义正确，无更新语义，安全）；`AbstractPlugin` 轨无 updateConfig（jar 轨无定义级配置域，安全）；`doDeactivate` L436 非 ACTIVATED no-op（DEACTIVATING 窗口的并发 deactivate 幂等，正确）。生产代码仅此一处「过渡态静默丢更新」。
- **信心水平**: 确定（交错逐行成立）；后果评级受「当前生产调用方 updateConfig 频度低」压制（第 1 轮 C1-013 同口径降档）
- **误报排除**: 非 C1-012 重复——C1-012 的 updateConfig 面是「state==ACTIVATED 检查后 configProvider 被 deactivate 置 null」（读方防到 null，弱化同形），本案是「ACTIVATING 窗口内热应用与回调双双静默跳过」（丢更新），方向相反（deactivate 侧 vs activate 侧）、后果不同（异常漂移 vs 静默丢失）；非 C1-013 重复——C1-013 是并发 updateConfig 的 merge RMW 丢键，本案是与 activate 的过渡态竞态。
- **复核状态**: 未复核
- **复核状态（终裁 2026-10-17 独立复核）**: 保留 P3——activateInternal 快照读 L461 + updateConfig L580 终态判定亲核；ACTIVATING 窗口热应用与 reconcile 双跳过、definitionConfig 与 provider 视图静默分叉至下次重激活成立；调用频度低维持 P3。修复备注：updateConfig 纳入 lifecycleLock 时顺带闭合 C1-013（已驳回）残留的 merge RMW 原子性义务。现状=新发现。

---

## 七、维度集外线索

### [nop-core-framework-CL-014]（维度集外线索，不立案展开）

- **形态归属**: C4（生命周期/销毁顺序）/ C1（全局静态状态并发拆除面）
- **严重度候选**: P2
- **一句话证据**: `AbstractPlugin.doDeactivate`（L268-271，lifecycleLock 内）与 `doStop`（L355）在 autoInit 路径调用 `CoreInitialization.destroy()`——单插件停机触发**全局初始化状态**（全部 ICoreInitializer 链，含 IoC 容器 stop）拆除，与其他线程的运行期使用无任何协调/引用计数；autoInit 置位条件（插件激活早于 CoreInitialization 初始化）使主要暴露面在测试/隔离插件场景，生产默认时序下不可达。
- **归属单元**: 调用点在 nop-plugin-support（本维度记录）；全局状态本体在 nop-core（CoreInitialization，越界单元）——建议 C4/生命周期维度跟进跨单元核查 initialize/destroy 的并发守卫。
- **复核处置（2026-10-17 独立复核）**: **确认**——doDeactivate L268-271 autoInit 时 lifecycleLock 内 `CoreInitialization.destroy()` 亲核证实；移交 C4/跨单元（CoreInitialization 本体属 nop-core）。

### [nop-core-framework-CL-015]（维度集外线索，不立案展开）

- **形态归属**: C5（异常与稳定性）/ F9（错误处理中断批量流程）
- **严重度候选**: P3
- **一句话证据**: `PluginManagerImpl.checkChangedAndReload`（L606-626）仅对 `checkChanged` 包 try，`reloadPlugin`（L623）裸调——批量变更检测中任一 def 已被并发 unload（NOT_LOADED）或 deactivate 段失败时异常冲出整轮循环，本轮剩余插件的变更检测全部中断；守卫读（L610）与 reload 之间无重检。建议宿主轮询侧 try-per-def 或 reloadPlugin 对 NOT_LOADED 幂等跳过。
- **归属单元**: nop-plugin
- **复核处置（2026-10-17 独立复核）**: **确认**——checkChangedAndReload L606-626 仅 checkChanged 包 try、reloadPlugin L623 裸调亲核；P3，宿主轮询侧守卫（try-per-def 或 NOT_LOADED 幂等跳过）。

### CL-001 升格建议（留复核轮裁定，不重复立案）

本轮对 CL-001（`JdbcConfigSource.refreshConfig` L96-97 `tryTriggerChange(vars)` 位于 try 块外）做了独立复核：**确认属实且建议升格为 P1 正式立案**。复核要点：(1) `tryTriggerChange`（synchronized）内 `task.run()` 直跑 onChange 回调——生产回调含 `changeApplier::requestUpdate`（停机竞态下 `SingleThreadConfigExecutor.execute` 可抛 RejectedExecutionException）与 `PluginManagerImpl` 的 `(p,v)->reconcile()`（订阅回调，RuntimeException 直冲任务体）；(2) `scheduleWithFixedDelay` 任务体未捕获异常按 JDK 语义**永久静默取消**后续执行——DB 配置热更新与全部 if-property 插件门控从此失联，无任何日志；(3) 兄弟类 `AbstractFileConfigSource.refreshConfig`（L53-70）已全量 try 包裹并注释说明该 JDK 语义——同模块防护不对称证明作者已知该陷阱而 Jdbc 轨遗漏；(4) 触发条件（定时刷新在途 + 任一回调异常）属「常见条件」，后果不可逆且不可观测。第 1 轮判级候选 P1 成立。
- **复核处置（2026-10-17 独立复核，终裁）**: 确认升格——**已立案为 C1-021（P1）**（见 C1-round3.md §五）；第 2 轮要点 (1) 中「`PluginManagerImpl` 的 `(p,v)->reconcile()` 直冲任务体」表述经第 3 轮纠偏（插件 reconcile 回调运行于 subscriptions.trigger、有异常隔离，非本任务体同步回调），真实触发面 = 可插拔 IConfigExecutor 运行期拒绝 + 公开 SPI addOnChange 宿主回调抛出 + 停机窗口（后果被 close 吞没）三类，终裁维持 P1；台账标注「升格立案」。

---

## 八、总计与终止信号判定

| 判级 | 数量 | 本轮条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 1 | C1-017（配置发布协议字段面扩展：defaultValue + 写序撕裂 + 运行期写点全集——C1-007 同根因修复面完备化） |
| P2 | 2 | C1-018（name 索引孤儿化门控失效）、C1-019（reload×load 注册表窗口双活定义） |
| P3 | 1 | C1-020（updateConfig ACTIVATING 窗口热应用静默丢失） |
| **新增发现总计** | **4** | （累计 C1-001~020，前轮 16 条不重复） |
| CL 线索 | 2 | CL-014（CoreInitialization.destroy 全局拆除）、CL-015（checkChangedAndReload 批量中断）；CL-001 复核确认并**建议升格 P1** |

**正面确认（本轮新增，供复核轮对照）**：static non-final 12 站全部安全（类初始化单发/volatile）；`PlaceholderConfigReference` volatile 先例（api-core 修复可行性证据）；VfsPluginDefinition.activateInternal 的 setConfigProvider→start→volatile 发布顺序正确；`ProducedBeanInstance.proxyHandler` synchronized 封闭；`BeanContainerImpl` 除 C1-001 已立案三元组外全部字段条件安全（startFuture 无框架内调用方实测）；`configModel/configSource` 启动期写经 changeApplier HB 条件安全；BeanScopeImpl 的 stop 方向窗口由 checkClosed+同步 add 消除注册泄漏。

**终止信号判定**：
- ③（check-then-act）与 ④（静态状态）两目标已**穷举闭合**：生产代码无新增可立案同族站点，全部交错面收敛进既有立案或本轮修复批次。
- ⑤（发布安全）同族补扫完成，无新增立案。
- ① 产生 1 条 P1（C1-017），但与 C1-007 同根因、同修复载体（api-core holder 化一次修复全覆盖），**族枚举已完备**（7 写点 × 3 字段 × 8 读站全表），不开启新的未知探索面。
- **按深挖继续规则（新增 P1 → 建议第 3 轮）**：形式上建议第 3 轮，但第 3 轮范围应**收窄为复核轮**而非盲挖轮——对象仅限：(a) C1-017/C1-007 修复面合并裁定的复核（含 CL-001 升格裁定）；(b) ②矩阵中「跨定义门控 TOCTOU」是否随 C1-010 修复（activatePlugin/deactivatePlugin 纳入 reconcileLock）闭合的验证。C1 维度在 nop-core-framework + nop-utils 单元内的**盲挖信号已终止**（三个子目标穷举 + 两处 P1 均为已知族的修复面完备化，无新族）。
