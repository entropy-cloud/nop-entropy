# C2 维度（锁与同步）第 1 轮初审报告——nop-core-framework + nop-utils

## 审计单元

- **审计对象**：框架层 `nop-core-framework` 六子模块（nop-ioc 99f/9,524、nop-config 40f/3,541、nop-plugin 26f/3,732、nop-security 12f/630、nop-log 11f/528、nop-boot 5f/364）+ `nop-utils`（115f/11,003）。
- **仓库**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）。
- **维度定义（C2——锁与同步）**：锁的正确性与纪律——锁序与死锁环、锁粒度与锁内 I/O、Condition await 丢失唤醒、synchronized 范围、CAS 复合操作、锁选错对象、可重入假设。执行步骤按 `ai-dev/skills/concurrency-resource-audit-prompt.md` C2 节（锁载体盘点 → 锁正确性（synchronized 集合迭代点/可重入/锁-final/DCL）→ 死锁排查（锁序图找环）→ 锁性能（锁内 I/O）→ F5 等待点超时核查）。
- **判级口径**：按共享前缀并发领域校准（P0 主路径数据竞争/可达死锁；P1 罕见但可具体化竞态/负载上升必然触发死锁；P2 理论竞态/锁粒度可证明热点/缺测试保护；P3 优化候选）。框架层被全平台并发使用；「仅启动期单线程触达」站点维持条件化降档（P2 起）。

## 盲审纪律声明

本轮为**盲审轮次**：未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅阅读允许的方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md`（全文 422 行）与 `ai-dev/skills/deep-audit-prompts.md`（前 700 行，含共享前缀/调度架构/发现完整性守则），其余结论全部基于当前 live code。主任务下发的「C1 轮已立案」反虚构预注信息仅用于**同族去重**（不重复立案 JdbcConfigSource close 竞态、插件 unload 交错、CL-002 computeIfAbsent 站点本体），未用于任何发现的证据构造。api-core 的 `FutureHelper`/`AppConfig`/`IConfigRefreshable` 仅作对照阅读，不立案。测试代码不作为生产守卫计入。

## 检查范围说明（锁站点矩阵）

基线命令：`grep -rn --include="*.java" -E "synchronized|ReentrantLock|ReadWriteLock|Collections\.synchronized" nop-core-framework nop-utils`（main 源码，排除 `_gen`），命中 **44 处锁站点**；另盘 `computeIfAbsent/compute` 9 处、`wait()/notifyAll()` 载体 1 类（ProducedBeanInstance）。逐站点判定：

| 模块 | 站点 | 锁内工作量 | 判定 |
|---|---|---|---|
| nop-ioc | `BeanContainerImpl.getBean0` L406 `synchronized(beanDef)` | createInstance = 构造器/工厂方法/supplier 用户代码 + 构造参数解析（含类加载）+ scope.add | **C2-001**（锁序环）+ 锁内用户代码放大临界区 |
| nop-ioc | `ProducedBeanInstance` ×9（L76/80/84/92/104/120/152/162/219/224） | 短临界区（状态检查/推进），init/lazy/delay 回调在锁外 | 结构合规；等待点 `wait()` 无超时 → **C2-002/C2-009** |
| nop-ioc | `BeanCreationContext` ×7 | TreeMap 快照/drain，执行在锁外 | 合规 |
| nop-ioc | `BeanScopeImpl.add/close` L74/99 | `synchronized(beans)`：closed 置位与 put 原子化 | 合规（stop/getBean 交接已闭环：add 抛 ALREADY_CLOSED、get fail-fast） |
| nop-ioc | `BeanFinder.getBeansByType/getBeansByAnnotation` computeIfAbsent（L53/108） | CHM bin 锁内 O(n) 反射 assignability 全量扫描（无递归、无 I/O） | P3 冷启动争用，不立案（合规边缘） |
| nop-ioc | `BeanContainerImpl.getClassIntrospection` L131-140 | volatile 检查-然后-行动懒初始化 | **C2-011**（P3 良性竞态） |
| nop-ioc | `BeanTopologySorter/AopBeanProcessor/BeanConditionEvaluator` computeIfAbsent | 构建期单线程 | 合规 |
| nop-config | `ChangeSubscription` ×3（L28/32/36） | 监听器快照/增删，trigger 持快照锁外迭代 | 合规；空条目驻留见 C2-007 F3 |
| nop-config | `DynamicConfigSource.setConfigVars` L38 | volatile 写包 synchronized（冗余但无害） | 合规 |
| nop-config | `JdbcConfigSource.refreshConfig/tryTriggerChange` L71/100 | 锁内 JDBC 查询（定时器单线程） | 合理；close 交互竞态属 C1 已立案族，本轮不重复 |
| nop-config | `DefaultConfigProvider.applyChange` L255-295 | **无锁**复合操作（volatile 字段交换 + diff + 监听器触发） | **C2-008** |
| nop-config | `ConfigChangeApplier` L40-54 | CAS 标记 + 单线程 executor | 复核通过（全量重读使 lost-update 不成立）；直接调用绕过面归入 C2-008 |
| nop-plugin | `PluginManagerImpl.reconcile/reloadPlugin` L227/639 `reconcileLock` | doReconcile 全程持锁（门控评估 + 激活/去激活 + `FutureHelper.syncGet(def.deactivate())`——经核实 futureCall 为调用线程内联执行，无跨线程等待） | 矩阵缺口 **C2-006**（P3） |
| nop-plugin | `PluginManagerImpl.loadPluginFromVfs/Jar` computeIfAbsent L493/569 | CHM bin 锁内 VFS 解析+`plugin.load` / HTTP 下载+SHA256+类加载+`plugin.start` | **C2-005**（CL-002 同站点 C2 深化） |
| nop-plugin | `VfsPluginDefinition.activate/doDeactivate` L381/435 `lifecycleLock` | 锁内子容器 build+start / scope.close+container.stop（用户代码）——「单飞」设计声明 | 设计内；**状态转换在锁外** → **C2-004** |
| nop-plugin | `AbstractPlugin.activate/doDeactivate` L172/257 `lifecycleLock` | 同上 + `CoreInitialization.initialize()` | 同 **C2-004**（state 非 volatile） |
| nop-plugin | `DefinitionConfigProvider.mergedView` ×7 | 通知在锁外（L88-92、L150） | 合规；subscribe 清理竞态 → **C2-007** |
| nop-plugin | `PluginScopeImpl.effects` ×4 | close 在锁外 dispose；effect 取消闭包在锁内 dispose | **C2-010**（P3） |
| nop-utils | `ShellRunner` L236 `synchronized(WindowsProcessLaunchLock)` | 仅 `builder.start()`（Windows 已知句柄继承竞态的官方规避，注释含 MS KB 引用） | 合规（文档化 workaround，不立案） |
| nop-utils | `Trie` RRWL（L22） | makeNode consumer（短内存操作，注册期）+ match 读锁遍历 | 合规 |
| nop-utils | 其余子模块（diff/fsm/git/match/image/java-parser/table-validator） | 无 synchronized/lock 载体 | 合规 |
| nop-boot / nop-security / nop-log | 无 synchronized/lock 站点 | boot 为启动主类；security 无状态工具；`LoggerConfigurator` volatile 单例（configurator-only） | 符合预期，稀疏确认 |

## 全模块锁序图（文本）

```
锁节点：
  BD(X)        = BeanDefinition(X) monitor（每 beanDef 一锁；父/派生容器共享同一 BeanDefinition 实例
                 —— buildNewInstance 传递同一 enabledBeans/orderedBeans → BD 锁为跨容器全局点）
  PBI(X)       = ProducedBeanInstance(X) monitor（每 bean 实例一锁，wait/notifyAll 载体）
  BCTX         = BeanCreationContext monitor（每顶层 getBean 调用一个）
  SCOPE        = BeanScopeImpl.beans monitor（closed 置位互斥）
  RLOCK        = PluginManagerImpl.reconcileLock
  LC(P)        = VfsPluginDefinition/AbstractPlugin lifecycleLock（每插件一锁）
  BIN(plugins) = ConcurrentHashMap bin monitor（plugins map computeIfAbsent）
  MV           = DefinitionConfigProvider.mergedView monitor
  FX           = PluginScopeImpl.effects monitor
  SUB(p)       = ChangeSubscription(p) monitor
  WLOCK        = ShellRunner.WindowsProcessLaunchLock（静态）
  TRW          = Trie ReentrantReadWriteLock

边（持锁 → 获取）：
  BD(A) → BD(B)        构造参数 ref/type 解析（createInstance 内 newInstance→resolveValue→getBean0）
  BD(A) → PBI(F)       factoryBean 路径 getBean(F, false)（BeanDefinition.newInstance L534，includeCreating=false
                       → checkBeanInitialized → runUntil 等待）——持 BD 等待 PBI
  BD(A) → SCOPE        scope.add（createInstance 内）
  RLOCK → LC(P)        doReconcile → activateIfInactive → def.activate()；deactivateIfActive → doDeactivate
  RLOCK → BD(*)/PBI(*) deactivateIfActive 触发 container.stop → destroyBean → 用户 destroy 方法
  LC(P) → BD(*)/PBI(*) activate/deactivate 内 container.start()/stop() → bean 创建/销毁 → 用户 init/destroy 代码
  BIN → 任意           computeIfAbsent 映射函数内 plugin.load/start 用户代码（无界出边）
  FX  → 任意           effect 取消闭包锁内 d.dispose() 用户代码（有界）
  等待叶               PBI(X) 上的 wait()：等待者不持 BD（getBean0 L433 在 synchronized 块外调用
                       getBeanInstance），无超时、可中断（waitQuietly 恢复中断位后抛错）

环闭合条件（本轮立案依据）：
  ① BD(A) ⇄ BD(B)：构造器级 type-injection/factoryBean/supplier 边不入 resolvedDepends（C2-001）
  ② PBI(X) ⇄ PBI(Y)：init 阶段 owner 经 includeCreating=false 交叉获取（C2-002）
  ③ BIN→任意 出边 + BD(*)：映射函数内用户代码反取其他插件/bean（C2-005）
```

---

## 第 1 轮（初审）发现

### [nop-core-framework-C2-001] beanDef 锁序环：构造器级 type-injection/factoryBean 依赖边对拓扑排序不可见，跨线程并发创建在 beanDef monitor 上互等死锁

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:406-425`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/resolvers/InjectTypeValueResolver.java:48-54`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:526-540`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanTopologySorter.java:174-185,224-249`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/IocConfigs.java:65-66`
- **证据片段**:
  ```java
  // BeanContainerImpl.getBean0 L406（持 beanDef 锁执行构造器/工厂解析）
  synchronized (beanDef) { //NOSONAR
      beanInstance = beanScope.get(beanDef.getId());
      if (beanInstance == null) {
          ...
          beanDef.markInCreation();
          try {
              beanInstance = beanDef.createInstance(beanScope, this, beanCtx);
  // InjectTypeValueResolver —— 未覆写 collectDepends（IBeanPropValueResolver L31 默认空实现）
  public Object resolveValue(IBeanContainerImplementor container, IBeanScope scope, BeanCreationContext beanCtx) {
      ...
      return container.getBeanByType(beanType, true, beanCtx);
  ```
- **严重程度**: P1
- **并发场景**: 并发启动 worker 线程（`nop.ioc.app-beans-container.concurrent-start=true`，经 `AppBeanContainerLoader.loadAppContainer` L78 接线，默认 false 但为公开配置）或运行期两个 HTTP worker 线程并发首次触碰两个互相 type-inject 的懒加载单例。
- **触发时序**: 前提：bean A 构造器（或工厂方法/supplier）经 `@Inject` 按类型需要 bean B，bean B 构造器按类型需要 A。该依赖边因 `InjectTypeValueResolver` 未实现 `collectDepends` 而**不进入** `BeanTopologySorter.sortBeans` 的图（L224 只收集 ref 类 resolver），也不进入 `fillResolvedDepends`（L174-185），故允许通过构建期校验且 concurrentStart 的 TaskExecutionGraph 无 A/B 间边。T1（graph worker 或请求线程）执行 `getBean(A)` → L406 持有 `BD(A)` → `createInstance → newInstance → getConstructorArgs → resolveValue → getBeanByType(B)` → 进入 `getBean0(B)` → L406 请求 `BD(B)` 阻塞；T2 同时执行 `getBean(B)` → 已持有 `BD(B)` → 构造器解析 → `getBeanByType(A)` → 请求 `BD(A)` 阻塞。T1 持 `BD(A)` 等 `BD(B)`、T2 持 `BD(B)` 等 `BD(A)`——**标准 hold-and-wind 死锁，无超时、不可中断恢复（synchronized 不响应 interrupt），线程永久挂死**。同线程路径会被 `isInCreationByCurrentThread`（L412，可重入检测）fail-fast，故单线程启动测试不可能暴露。同族第二闭合面：`BeanDefinition.newInstance` L534 `container.getBean(beanModel.getFactoryBean(), false, beanCtx)`——factoryBean 边同样未收集，且 `includeCreating=false` 使持 `BD(A)` 的线程在 `PBI(F)` 上等待（若 F 正被他线程推进 init），而 F 的 init 经 resolvedDepends 反取 A 时环闭合。
- **现状**: `synchronized(beanDef)` 只保护「scope.get 为 null → 创建 → scope.add」的单实例不变量（锁选对象本身正确）；但锁内执行的构造器解析会产生不可见依赖边，锁序图存在可闭合的环。
- **风险**: 框架层死锁：两个全局 worker（concurrentStart）或两个请求线程永久挂死，且持锁不释放导致同 beanDef 的所有后续 getBean 全部排队挂死——单点故障扩散为该 bean 的全平台调用面阻塞；无超时无自愈，只能 kill 进程。
- **建议**: (1) `InjectTypeValueResolver` 实现 `collectDepends`：构建期经 `container.findAutowireCandidate(beanType)`（或 `BeanFinder.findByType` 对 enabledBeans 预解析）解析出 beanId 集合加入 depends，使环在 `sortBeans` 被拒；(2) `BeanDefinition.collectDepends` 补收集 `beanModel.getFactoryBean()`；(3) 兜底：`getBean0` 的 `synchronized(beanDef)` 改用可超时/可中断的 `ReentrantLock.tryLock(timeout)` 并在超时时输出双方持有/等待诊断（利用 `inCreationThread` 字段定位环）。
- **失效面枚举**: 构造期依赖边全集 = {`InjectRefValueResolver`（已收集，L66-70，安全）、`InjectTypeValueResolver`（**未收集**，本案）、`BeanDefinition` props 逐 resolver（List/Map/Set/Props/Concat/BeanValueResolver 均委托子 resolver，安全）、`factoryBean`（**未收集**，同案第二闭合面）、`supplier` 函数（不可静态收集，建议文档声明 + 兜底 tryLock 兜底）、`ExpressionValueResolver/XplValueResolver`（表达式内动态 getBean 不可静态收集，同 supplier 处理）}。锁序环入口 = {concurrentStart graph worker、运行期懒加载首触（HTTP worker × 2）、`injectTo`/`getBeansOfType` 间接触发}。
- **信心水平**: 确定（依赖边不可见为代码事实；死锁时序两线程各持一锁可逐步写出）
- **误报排除**: 不是「synchronized 原语使用」误报——问题不在原语而在复合锁序；不是低频启动粗锁误报——运行期懒加载路径同样可达；同线程已被 fail-fast 守卫覆盖恰证明并发路径无守卫。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P1（终裁维持）**。专项核实：(a) InjectTypeValueResolver 全文未实现 collectDepends（IBeanPropValueResolver L31-33 默认空实现），factoryBean 边（BeanDefinition.newInstance L534）不经任何 resolver 收集——两均不入 sortBeans L224 / fillResolvedDepends L175 图，属实；(b) isInCreationByCurrentThread（L86-98）为 beanDef 实例字段与当前线程比对，跨线程无守卫；同线程 L412 fail-fast 掩护属实；跨线程互等序列逐步可达（目标 bean 不在 scope 时 L376-387 快速通道不命中，T1 持 BD(A) 阻塞于 synchronized(BD(B)) 入口、T2 对称，in-creation 检查不可达）；(c) 本案机制面（构造参数求值的不可见边）与 C1 复核驳回 C1-003 的机制面（代理 handler nullability）分属不同域，无口径冲突。终裁附注三条：① `nop.ioc.bean-depends-graph.allow-cycle` 默认 **true**（IocConfigs L64-66）——即使补收集依赖边，默认配置下 sortBeans 的拒环检查（L256-261）也不执行，建议 (1)「使环在 sortBeans 被拒」须绑定 allow-cycle 默认值裁定方能闭合，风险面较初审假设更弱守卫（附加强化证据）；② 现状 = **已知未修复**：历史 check 审计同源 P2-6「getBean0 嵌套锁多线程懒初始化死锁」裁定暂缓（处置记录明言残余面 = 跨线程并发懒初始化构造器环，待 IoC 容器锁纪律专项裁定——本轮即该专项回访）；同线程 inCreationThread 守卫系 commit 4ffaebb267 修复产物；③ 分支 head（6dceb20dc3）无本面修复。

### [nop-core-framework-C2-002] ProducedBeanInstance 跨实例 monitor 等待环：init 阶段 owner 经 includeCreating=false 交叉获取下游 bean 时环闭合，wait() 无超时无挂死可见性

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/ProducedBeanInstance.java:114-170,194-201`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:638-645`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:433`
- **证据片段**:
  ```java
  // ProducedBeanInstance.runUntil L136-141
  if (initThread != null) {
      if (initThread == Thread.currentThread())
          throw selfWait();
      waitQuietly();          // L196: wait() 无超时
      continue;
  }
  // BeanDefinition.initBean L642-644（阶段回调内，includeCreating=false）
  for (String depend : getResolvedDepends()) {
      container.getBean(depend, false, beanCtx);
  }
  ```
- **严重程度**: P1
- **并发场景**: 同上——concurrentStart worker 线程组，或运行期两个线程并发首触两个存在「init 期交叉获取」关系的懒加载单例。注：`runUntil` 等待者**不持 beanDef 锁**（`getBeanInstance` 在 L406 synchronized 块外调用），故这是纯 `PBI monitor` 域的等待环，与 C2-001 的 `BD monitor` 域互不重合。
- **触发时序**: 环闭合条件 = 两个 bean 的 INITIALIZED 阶段 owner 互相等待对方的阶段完成。T1：`getBean(X)` → 创建 X → 锁外 `setupInstance` → `flushInit` 成为 `PBI(X)` 的 INIT owner（`initThread=T1`）→ `initBean(X)` 执行用户 initMethod → 用户代码调用 `container.getBean(Y, false)` / `getBeansOfType(...)`（includeCreating=false 才会等待）→ `checkBeanInitialized(Y)` → `PBI(Y)` 的 `initThread=T2` → **T1 在 `PBI(Y)` monitor 上 `wait()`**；T2：正在推进 Y 的 INIT（`initBean(Y)` → `getBean(X, false)`（Y 经 declared dependsOn 依赖 X，拓扑合法）→ `checkBeanInitialized(X)` → `PBI(X)` 的 `initThread=T1`、status 未达 INITIALIZED → **T2 在 `PBI(X)` monitor 上 `wait()`**。T1 等 T2 放弃 `PBI(Y)` owner、T2 等 T1 放弃 `PBI(X)` owner——环闭合，双方永久 wait。同线程序列会命中 `selfWait()`（L137-138）fail-fast，故顺序启动测试必绿。
- **现状**: `runUntil` 的 owner/等待者协议本身正确（短临界区推进、锁外回调、异常广播 initError）；缺环检测与等待超时。
- **风险**: 触发形态是「X 的 init/factory 代码做类型扫描或显式拉取下游 Y（Y dependsOn X）」的 bean 组合 + 并发首触——平台内 registry 型 bean（init 收集某类型全部 bean）+ 声明依赖该 registry 的业务 bean 是常见组合；死锁后两 bean 的全部 getBean 排队挂死，jstack 可见两个 BLOCKED-on-monitor 链但无框架级诊断。
- **建议**: (1) `runUntil` 的 `waitQuietly` 改为 `wait(timeout)` 循环限时 + 超时抛出含 `initThread`/`createThread`/beanId 的诊断异常（对照 F5：所有无限期等待点必须有超时）；(2) 等待前死锁嗅探：等待链上若目标 bean 的 `initThread` 的等待目标构成环（经容器级 wait-for 图），fail-fast 抛 `ERR_IOC_BEAN_INIT_SELF_WAIT` 同类错误码。
- **失效面枚举**: 等待环入口 = {`initBean` 的 resolvedDepends 循环（声明依赖，拓扑无环，单独不可闭合）、用户 initMethod/factoryMethod 内 `getBean(name,false)`/`getBeanByType(...,false,...)`/`getBeansOfType`/`getBeansWithAnnotation`（**可闭合**）、`checkBeanLazyPropSet`/`checkBeanDelayActionRun` 阶段回调内用户代码同形态（LAZY/DELAY 相位晚于 INITIALIZED，仅当回调反取「init 依赖自己」的 bean 时可闭合，同案）}。超时缺失点全集 = {`waitQuietly` L196（本案）、`FutureHelper.syncGet` 于 `awaitStartFinished`（C2-003 相关）}；`reconcile` 内 `syncGet` 经核实为同线程内联，非等待点。
- **信心水平**: 很可能（协议与环闭合路径为代码事实；触发依赖特定但常见的 bean wiring，未观测到现场）
- **误报排除**: 不是「Condition await 通用最佳实践」式泛化——环闭合的两条具体交错序列已逐步写出；C1 轮已注意到该环，本案按任务要求从锁序角度完成锁序图与闭合条件深化，未重复立案本体以外的内容。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P1（终裁维持）**。死锁时序逐步复核全部成立：T1 为 PBI(X) INIT owner（runUntil L144 经 L121/L128/L136 检查序）→ initBean(X) 用户代码公开 getBean(Y)（includeCreating=false）→ checkBeanInitialized(Y) → L136-141 initThread=T2 ≠ 当前线程 → waitQuietly（L194-201 wait() 无超时）；T2 对称于 PBI(X)——环闭合，每步代码点亲核一致。关键前提复核：等待者不持 BD（getBeanInstance 调用点 L433 在 synchronized(beanDef) 块外）属实；同线程 selfWait（L120-121/L137-138）fail-fast 掩护属实；Y 侧可用完全可见的 resolvedDepends 声明边闭合（L642-644），单侧用户代码即足。现状 = 新发现，未修复。

### [nop-core-framework-C2-003] 容器启动守卫与启动完成信号：running check-then-act 非原子、startFuture 非 volatile 导致 awaitStartFinished 丢失唤醒形态

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:90,476-484,502-518,535-539`
- **证据片段**:
  ```java
  private CompletableFuture<?> startFuture;            // L90：非 volatile
  public void start() {
      if (running)                                     // L477：check-then-act 非原子
          throw new NopException(ERR_IOC_CONTAINER_ALREADY_STARTED)...
      running = true;                                  // L483
      ...
      startFuture = asyncStartBeans(startBeans).whenComplete(...)   // L503
  }
  public void awaitStartFinished() {
      if (startFuture != null)                         // L537：可能读到过期 null
          FutureHelper.syncGet(startFuture);
  }
  ```
- **严重程度**: P2（启动期单线程触达，条件化降档）
- **并发场景**: 启动主线程调用 `start()`（concurrentStart=true 时异步启动）与另一线程（管理线程/宿主框架线程）调用 `awaitStartFinished()`；或两个线程并发调用 `start()`/`restart()`。
- **触发时序**: (a) 丢失唤醒：T1 执行 `start()`，已通过 L477 检查、尚未执行 L503 赋值；T2 调用 `awaitStartFinished()` 读 `startFuture`——字段非 volatile 且无任何同步屏障，T2 可读到 null（或未完全发布的 CompletableFuture）→ 直接返回，调用方误认为启动已完成 → 过早发起依赖未初始化 bean 的请求。(b) 双重启动：T1、T2 同时通过 L477 的 `running` 检查 → 各自执行 L483-518 → `singletonScope` 被覆盖，先建 scope 的 bean 永不销毁（泄漏）。
- **现状**: `running/started` 为 volatile 但仅保证单变量可见性，不构成复合操作原子性；`startFuture` 无 volatile/锁保护发布。
- **风险**: 正常部署中 start 由平台启动序列单线程调用，风险被「启动期单线程」惯例掩盖；一旦宿主（如 Quarkus/Spring 适配层、运维触发的 restart 端点）并发调用 start/awaitStartFinished，出现过早放行流量或双 scope 泄漏。
- **建议**: 引入容器级启动锁（或 `synchronized(startStateLock)`）包裹 `start()/stop()/restart()` 的状态检查与翻转；`startFuture` 声明为 volatile 或统一经锁内读写；`awaitStartFinished` 读到「running && !started && startFuture==null」时显式等待而非直接返回。
- **失效面枚举**: 同族入口 = {`start()` 守卫 L477（本案）、`restart()` L452-455（stop/start 非原子序列，重启窗口内 getBean 抛 NOT_STARTED——fail-fast 可接受）、`awaitStartFinished` L536-539（本案）、`stop()` L595-617 与 in-flight getBean 的交接（已由 BeanScopeImpl add/close 原子化 + checkClosed fail-fast 覆盖，复核通过）}。`IBeanContainer.awaitStartFinished` 在 api-core 有接口默认空实现，仓库内无生产调用方（grep 全仓确认），故降档 P2 而非 P1。
- **信心水平**: 确定（可见性/原子性缺陷为代码事实；可达性受调用方约束）
- **误报排除**: 不是「volatile 字段本身使用」误报——缺陷在复合操作与发布协议；已按判级口径对启动期单线程触达做条件化降档而非按主路径 P1 处理。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P2（维持，含启动期条件化降档）**。代码事实全核实：startFuture 非 volatile（L90）、running check-then-act（L477-483，两线程可同时通过）、awaitStartFinished 读过期 null（L536-539）。降档依据复核属实：IBeanContainer.awaitStartFinished 为接口 default 空实现（api-core L23），全仓 grep 无生产调用方。现状 = 新发现，未修复。

### [nop-core-framework-C2-004] 插件状态机转换部分位于 lifecycleLock 之外：unload guard 与 activate 交错产生孤儿激活（容器运行但注册表无条目）；AbstractPlugin.state 非 volatile

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/VfsPluginDefinition.java:357-377,550-563,573-593`；`nop-core-framework/nop-plugin/nop-plugin-support/src/main/java/io/nop/plugin/support/AbstractPlugin.java:75,150-166`
- **证据片段**:
  ```java
  // VfsPluginDefinition.unload L366-377 —— 不持 lifecycleLock
  public void unload() {
      if (state == PluginState.ACTIVATED || state == PluginState.ACTIVATING
              || state == PluginState.DEACTIVATING) {
          throw new NopException(ERR_PLUGIN_NOT_DEACTIVATED)...
      }
      this.state = PluginState.UNLOADED;
      this.definitionConfig = new LinkedHashMap<>();
  }
  // AbstractPlugin L75 —— state 非 volatile（对比 VfsPluginDefinition L104 已补 volatile）
  private PluginState state = PluginState.UNLOADED;
  ```
- **严重程度**: P2
- **并发场景**: 管理/热重载线程执行 `PluginManagerImpl.unloadPlugin`（不持 reconcileLock、不持 lifecycleLock）与 reconcile/config-executor 线程执行 `reconcile → activateIfInactive → def.activate()`（持 lifecycleLock）并发。
- **触发时序**: T1 进入 `def.activate()` 已持 `lifecycleLock`，通过三重状态检查（此刻 state=LOADED），尚未执行 L401 `state = ACTIVATING`；T2 执行 `unloadPlugin` → `holder.plugin.unload()`（无锁）读 state=LOADED → 守卫放行 → 写 `state=UNLOADED` 并从 `plugins` 移除 holder、注销 name 索引与配置订阅；T1 继续：写 `ACTIVATING` → `activateInternal()` 构建并 **start 子容器** → 写 `state=ACTIVATED`。终态：子容器与 activator 已运行，但 holder 已从注册表移除——`getPlugin(id)=null`、`unloadPlugin(id)` 抛 `ERR_PLUGIN_DEFINITION_NOT_LOADED`，**该运行中容器从此无法经任何入口停止，泄漏至进程退出**。`VfsPluginDefinition.start()`（load+activate 序列，L550-563）与 `updateConfig` 的 `state==ACTIVATED → provider.updateMergedView` 检查（L580-586，与 doDeactivate 置 `configProvider=null` 交错时对已脱离容器热应用）属同窗口变体。
- **现状**: activate/deactivate 的相位推进已入 `lifecycleLock`（单飞声明），但 load/unload/start/updateConfig 四个转换点全部锁外，volatile state 只解决可见性不解决转换原子性；`AbstractPlugin` 更弱——state 连 volatile 都没有（unload guard 读到过期值即误放行/误拒）。
- **风险**: 热重载/配置驱动的 reconcile 场景下产生不可卸载的孤儿容器（类加载器、连接、线程全部滞留）；`AbstractPlugin`（jar 轨）上的 guard 判定基于无同步读，可放行对 ACTIVATING 插件的 unload，反向产生半卸载状态。
- **建议**: `load/unload/start/stop/updateConfig` 的状态读-判定-写整体纳入 `lifecycleLock`（activate/deactivate 已在该锁内，无新增争用面）；`AbstractPlugin.state` 补 volatile 作为最低限度；或统一收敛到 manager 侧：`unloadPlugin/activatePlugin` 全部持 `reconcileLock` 后再操作（与 reloadPlugin 同纪律）。
- **失效面枚举**: 锁外转换点全集 = {`VfsPluginDefinition.load` L358、`unload` L366、`start` L550、`stop` L566-570（syncGet 同线程 + 锁外 unload）、`updateConfig` L573（**本案 ×5**）；`AbstractPlugin.load`、`unload` L~150、`start`、`stop`、`updateConfig`（**同族 ×5，且 state 非 volatile 恶化**）}。已受控点 = {`activate` L381、`doDeactivate` L435（两类均持锁）}。manager 侧 `unloadPlugin` 尾部三连（remove/unregister/unsubscribe）与 reconcile 快照的交错见 C2-006。
- **信心水平**: 确定（交错序列可完整写出；终态不可达卸载是接口契约事实）
- **误报排除**: 不是「生命周期低频粗锁」误报——问题恰是应锁未锁；窗口虽窄但 reconcile 由配置变更自动触发（`subscribeGlobalConfig` 回调），与人工 unload 并发是热重载常态而非构造场景。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P2（维持；事实修正一处）**。unload L366-377 无锁守卫、activate L380-424 lifecycleLock 内相位推进、start/stop/updateConfig 锁外转换、AbstractPlugin.state L75 非 volatile——全部亲核属实；VfsPluginDefinition L104 volatile 对照属实（该 volatile 系 commit 4ffaebb267 补充）。**修正**：终态描述「unloadPlugin(id) 抛 ERR_PLUGIN_DEFINITION_NOT_LOADED」不准确——unloadPlugin 对 holder==null 为**静默 no-op**（PluginManagerImpl L146-147），NOT_LOADED 由 activatePlugin 的 requireLoaded（L209-215）抛出；「孤儿容器无法经任何入口停止」的实质成立，判级不受影响。现状 = 部分已知未修复（volatile 面已历史修复；锁外转换面未修复）。

### [nop-core-framework-C2-005] loadPlugin computeIfAbsent 映射函数内 HTTP 下载 + SHA256 + 类加载 + plugin.start：CHM bin 锁长临界区与递归 computeIfAbsent 风险

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/PluginManagerImpl.java:492-500,567-596`；`nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/resolver/HttpPluginResourceResolver.java:101-131`
- **证据片段**:
  ```java
  private IPlugin loadPluginFromJar(ArtifactCoordinates coords) {
      String id = coords.toString();
      return plugins.computeIfAbsent(id, k -> {
          List<URL> urls = resourceResolver.resolvePluginResource(coords);  // HTTP 下载 + SHA256
          PluginClassLoader classLoader = new PluginClassLoader(...);
          IPlugin plugin = classLoader.loadPlugin();                        // 类加载
          ...
          plugin.start(coords.getGroupId(), ...);                           // 用户代码/子容器
          return new PluginHolder(plugin, classLoader);
      }).plugin;
  }
  ```
- **严重程度**: P2
- **并发场景**: 两个管理/请求线程并发 `loadPlugin` 两个坐标恰好同 bin 的插件；或插件 start/activator 用户代码内回调 `loadPlugin`/`getPlugin`。
- **触发时序**: (a) 序列化：T1 `computeIfAbsent(id1)` 持 `BIN` 执行下载（网络秒级）+ 全文件 SHA256 + `plugin.start()`（可能启动子容器）；T2 `computeIfAbsent(id2)`（id2 与 id1 同 bin，CHM 按 `(n-1)&hash` 分桶，不同坐标同 bin 概率不可忽略）在 `BIN` 上阻塞——T2 的插件加载被一次网络下载串行化；极端情况（下载挂起）下所有同 bin 加载全部停摆，且 `unloadPlugin` 对已注册条目的 `plugins.get/remove` 虽不用 bin 锁，但「unload 失败重试」注释（L154-157）声明的可重试性在 BIN 长临界区下被放大为长阻塞。(b) 递归：映射函数内 `plugin.start()` 用户代码调用 `loadPlugin(同 id)` → CHM 检测同线程递归更新抛 `IllegalStateException("Recursive update")`；调用 `loadPlugin(其他同 bin id)` 则形成 bin 锁自等待（同线程可重入 synchronized 不阻塞——CHM bin 为 synchronized，同线程重入不悬挂，但跨线程组合仍可构造互等）。
- **现状**: VFS 轨（L493）同形态（`loadVfsDefinition` + `plugin.load` + 订阅均在 bin 锁内，临界区较短）；jar 轨把网络 I/O 与用户生命周期代码全部放进映射函数。该站点本体已在 C1 轮登记（CL-002 家族），本案为锁纪律维度的深化立案：临界区构成、递归面、与 unload 重试协议的交互。
- **风险**: 插件加载吞吐被网络与用户代码串行化；下载源故障时同 bin 加载面整体挂起（无超时的 HTTP 客户端行为取决于 IHttpClient 配置）；递归 `Recursive update` 异常将插件置于 map 外但 PluginClassLoader 已创建（泄漏一个类加载器，L569 路径 catch 仅覆盖 RuntimeException 且会关闭——核实后该泄漏仅剩 Error 路径）。
- **建议**: 先 `plugins.get` 双检，映射函数只做「构造 PluginHolder」之前的轻量步骤；resolve/download/类加载/start 移到 bin 锁外，完成后经 `plugins.putIfAbsent` 回写（竞争失败方关闭自己创建的 classLoader 后返回已注册 holder）；或改用显式 per-pluginId `ConcurrentHashMap<String, Object> lockMap` + 双检。
- **失效面枚举**: 映射函数重逻辑站点全集 = {`loadPluginFromJar` L569（HTTP+SHA256+类加载+start，**最重**）、`loadPluginFromVfs` L493（VFS 解析+load+订阅）、`namesToPluginIds` L532（轻，合规）、`DefaultConfigProvider.makeConfigRef`（轻，合规）、`ChangeSubscriptions` L45/53（轻，合规）、`BeanScopeContextImpl` L47（轻，合规）、`BeanFinder` L53/108（O(n) 反射扫描，合规边缘）}。同族兄弟（unload/reload）不经 computeIfAbsent，已另案 C2-004/C2-006。
- **信心水平**: 确定（临界区构成为代码事实；递归面依赖用户代码行为，标注为风险而非确定缺陷）
- **误报排除**: 不是对 CL-002 的重复立案——本案只裁锁纪律剖面（bin 锁长临界区 + 递归协议），资源/异步剖面仍归 C1 原案；合并裁定阶段可按「同族相邻」处理。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P2（维持）**。jar 轨映射函数（L569-596）内 HTTP 下载（HttpPluginResourceResolver.resolvePluginResource 缓存未命中 → download）+ SHA256 校验 + PluginClassLoader 类加载 + plugin.start 亲核属实；catch 仅覆盖 RuntimeException（L583）+ finally safeCloseObject → Error 路径类加载器泄漏残余，与初审记载一致；L154-157 unload 可重试注释核实。现状 = 新发现（C1 轮 CL-002 同站点，锁纪律深化面），未修复。

### [nop-core-framework-C2-006] reconcileLock 保护矩阵缺口：unload/activate/deactivatePlugin 均不持锁，与 reconcile 快照交错的残余窗口

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/PluginManagerImpl.java:86-89,129-192,217-243,637-688`
- **证据片段**:
  ```java
  private final Object reconcileLock = new Object();      // L86
  // 锁矩阵：持 reconcileLock 的只有 reconcile() 与 reloadPlugin()
  @Override
  public void unloadPlugin(String pluginId) {              // L145：不持 reconcileLock
      PluginHolder holder = plugins.get(pluginId);
      if (holder != null) {
          ... holder.plugin.unload();
          plugins.remove(pluginId);
          unregisterNameIndex(pluginId);
          unsubscribeGlobalConfig(pluginId);
          IoHelper.safeCloseObject(holder.classLoader);
          reconcile();                                     // L163：尾部才取锁
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: config-executor 线程执行 `reconcile()`（持锁做 collectVfsDefinitions 快照 + 拓扑激活）与管理线程执行 `unloadPlugin/activatePlugin/deactivatePlugin`（全程无锁）并发。
- **触发时序**: T1 `reconcile()` 持 `reconcileLock` → `collectVfsDefinitions()` 快照含 P；T2 `unloadPlugin(P)` 完成 unload + map 移除；T1 继续 `activateIfInactive(P)` → `def.activate()` 内 `lifecycleLock` 下重读 state=UNLOADED → 抛 `ERR_PLUGIN_DEFINITION_NOT_LOADED` → 被 `activateIfInactive` catch（L396-399）记日志返回 false——**该交错被状态机守卫转化为 fail-fast**。残余窗口：T1 已在 `def.activate()` 中（ACTIVATING）而 T2 的 guard 恰在读 state 时 T1 尚未写 ACTIVATING → 即 C2-004 的孤儿激活序列（本案不重复计数）；`reloadPlugin` 持锁与 `unloadPlugin` 无锁并行的「双 unload」交错由 UNLOADED 态幂等/守卫兜底（第二次 unload 抛 NOT_DEACTIVATED 或 no-op）。锁矩阵审计结论：矩阵缺口本身未产生独立于 C2-004 的可达损坏。
- **现状**: reconcile 采用「CHM 快照 + per-def 状态机守卫」而非全局互斥；幂等/守卫设计覆盖了大多数交错。
- **风险**: 交错结果依赖 `activateIfInactive` 的 catch-all（RuntimeException|Error 静默记日志），真实故障（如孤儿激活前的异常）只留 error 日志，无结构化拒绝语义；后续维护者在 doReconcile 中新增对 holder/索引的写操作时会在无矩阵约束下引入新窗口。
- **建议**: 在类注释中钉死锁矩阵（reconcileLock 持有者 = reconcile/reloadPlugin；lifecycleLock 持有者 = activate/doDeactivate；其余转换必须经状态机守卫 + 幂等）；或最小改动——`unloadPlugin/activatePlugin/deactivatePlugin` 的「状态判定 + 生命周期调用」段收敛到 `reconcileLock` 内，消除快照过期面。
- **失效面枚举**: 无锁生命周期入口全集 = {`loadPlugin` L130、`unloadPlugin` L145、`activatePlugin` L168、`deactivatePlugin` L177、`checkChangedAndReload` L606（内部 reloadPlugin 持锁，检查段无锁——交交错产生重复 reload，由 reload 幂等兜底）}；持锁入口 = {`reconcile` L226、`reloadPlugin` L638}。
- **信心水平**: 确定（矩阵构成为代码事实；「无独立可达损坏」的判定基于逐交错推演）
- **误报排除**: 不是为凑数的矩阵洁癖立案——判级 P3 恰因现状守卫覆盖了可达交错；本案价值在钉死契约防止回归（对照 F10：声明式纪律无运行时守卫）。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P3（维持）**。锁矩阵亲核：持 reconcileLock 仅 reconcile（L227）/reloadPlugin（L639）；loadPlugin/unloadPlugin/activatePlugin/deactivatePlugin/checkChangedAndReload 全程无锁属实；activateIfInactive catch(RuntimeException|Error)（L396-399）+ 状态机守卫使快照过期交错 fail-fast 的推演与代码一致；「无独立于 C2-004 的可达损坏」判定维持。现状 = 新发现，未修复。

### [nop-core-framework-C2-007] DefinitionConfigProvider.subscribeChange 清理闭包 check-then-act：并发订阅/退订同 pattern 时监听器注册进孤儿 list，变更通知永久丢失

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/DefinitionConfigProvider.java:247-265`
- **证据片段**:
  ```java
  public Runnable subscribeChange(String pattern, IConfigChangeListener listener) {
      CopyOnWriteArrayList<IConfigChangeListener> list =
              listeners.computeIfAbsent(pattern, k -> new CopyOnWriteArrayList<>());
      list.add(listener);
      Runnable globalCleanup = globalProvider.subscribeChange(pattern, listener);
      return () -> {
          CopyOnWriteArrayList<IConfigChangeListener> l = listeners.get(pattern);
          if (l != null) {
              l.remove(listener);
              if (l.isEmpty()) {
                  listeners.remove(pattern);      // 与并发 computeIfAbsent 交错的 check-then-act
              }
          }
          ...
      };
  }
  ```
- **严重程度**: P2
- **并发场景**: 插件子容器内 bean 的响应式配置订阅：旧 bean 销毁线程执行清理闭包（container.stop → destroyBean → subscriptionCleanup.cancel），与配置热应用/新 bean 装配线程对同一 pattern 执行 `subscribeChange` 并发。
- **触发时序**: T1（清理）：`listeners.get(pattern)` 取到 list L（此刻仅含其 listener）→ `L.remove(listener)` → L 变空；T2（订阅）：`computeIfAbsent(pattern)` 返回**尚未被摘除的** L → `L.add(listener2)`；T1 继续：`L.isEmpty()` 为 true → `listeners.remove(pattern)`。终态：listener2 挂在已从 map 摘除的孤儿 L 上，`applyMergedView → notifyListeners` 遍历 `listeners.entrySet()` 永远触达不了 listener2——**该 bean 的配置热更新静默失效（F9 形态）**。
- **现状**: 写路径（applyMergedView/setDefinitionValue）锁纪律正确（通知在 `mergedView` 锁外），唯独订阅/清理协议存在复合操作窗口。
- **风险**: 插件热重载或 bean 级 @ConfigReference 频繁换绑场景下，配置变更不再生效且无任何报错——与「ACTIVATED 态热应用（bean 属性真实重绑定）」的类级契约（类注释 L32）直接相悖。
- **建议**: 清理闭包改为 `listeners.compute(pattern, (k, l) -> { if (l != null) l.remove(listener); return (l == null || l.isEmpty()) ? null : l; })`——CHM compute 的 bin 锁使「摘空条目」与「新建订阅」原子互斥；或 map 值改为不摘除（对照 ChangeSubscriptions 的常驻条目方案，代价是空条目按 pattern 基数驻留）。
- **失效面枚举**: 同族订阅/清理协议全集 = {`DefinitionConfigProvider.subscribeChange`（**有环**，本案）、`ChangeSubscriptions.subscribeSimple/subscribePattern`（条目永不摘除 → **无孤儿环**，但空 `ChangeSubscription` 对象按 pattern 基数永久驻留——P3 级 F8 残留，单独不立案）、`BeanDefinition.subscribeConfigChange` 的 `subscriptionCleanup`（Cancellable 聚合，复核通过）、`PluginManagerImpl.subscribeGlobalConfig/unsubscribeGlobalConfig`（CHM 独立键 put/remove，无环）}。
- **信心水平**: 很可能（交错序列完整；触发需同 pattern 的订阅与清理恰好重叠，窗口为数条指令）
- **误报排除**: 不是「ConcurrentHashMap 原语使用」误报——缺陷在 get/remove/isEmpty 三步复合操作；后果是静默失效而非异常，恰是 F9 高危形态。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P2（维持；交错序列细节修正）**。check-then-act 非原子结构核实（L253-260）。**修正**：初审写序列「T2 add 后 T1 的 isEmpty() 仍为 true」不可达——CopyOnWriteArrayList 一旦 T2 的 add 完成，T1 随后的 isEmpty() 必为 false、不摘条目、无孤儿。真实窗口在「isEmpty() 判定为 true」与「listeners.remove(pattern) 执行」两条指令之间：T2 computeIfAbsent 返回尚未摘除的条目并 add(listener2)，T1 随后执行 remove(pattern) 将 listener2 钉死在孤儿 list。机制（非原子 check-then-act → 孤儿 list → 配置通知永久丢失，F9 静默形态）与后果成立，判级维持；修复建议（cleanup 改 listeners.compute 原子化）经复核仍正确。现状 = 新发现，未修复。

### [nop-core-framework-C2-008] DefaultConfigProvider.applyChange 无锁复合操作：configValues/configRefs 交换 + 双基线 diff + 监听器触发仅靠单线程 executor 契约保护，public 入口可绕过

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/impl/DefaultConfigProvider.java:50-52,124-131,255-315`；`nop-core-framework/nop-config/src/main/java/io/nop/config/impl/ConfigChangeApplier.java:40-54`
- **证据片段**:
  ```java
  public void applyChange() {                               // L255：public 且无同步
      Map<String, ValueWithLocation> lastVars = this.configValues;
      Map<String, ValueWithLocation> vars = configSource.getConfigValues();
      this.configValues = vars;                             // 复合写 1
      ... 双基线 diff 计算 changed ...
      Map<String, DefaultConfigReference<?>> refs = buildConfigRefs(vars);
      this.configRefs = refs;                               // 复合写 2
      applyChangeToUsed(changed, refs);
      subscriptions.trigger(this, changed);                 // 监听器触发
  }
  ```
- **严重程度**: P2
- **并发场景**: config-executor 单线程（`SingleThreadConfigExecutor`，`newExecutor("nop-config-executor", 1, 100, true)`）内驱动的定时/事件刷新，与任何直接调用 `applyChange()` 的线程（方法为 public，`ConfigStarter.applyChange` 即直接委托）并发；`changeConfigSource` L124-131 同面（`configSource` 字段甚至非 volatile，L50）。
- **触发时序**: T1（executor 线程）执行 `applyChange()` 已读完 `lastVars` 并进入 diff 循环；T2（直接调用方）并发进入 `applyChange()` 读到同一 `lastVars` → 两轮 diff 基于**同一基线**各自触发 `subscriptions.trigger` → 监听器收到重复变更通知（bean 属性重绑执行两次，非幂等 listener 下语义未定义）；交错写序列（T1 写 configValues=V2、T2 写 configValues=V3、T1 后写 configRefs=refs(V2)）可使 `configRefs` 与 `configValues` 短暂指向不同代快照——`getConfigRef` 读到旧代 ref。
- **现状**: `ConfigChangeApplier` 的 CAS 协议（`shouldUpdate.compareAndSet` + 全量重读）经复核**成立**：标记被并发置位漏掉的更新由下一轮全量重读兜住，无 lost-update——单线程 executor 是唯一防线；防线之外的方法可见性（public）与字段可见性（configSource 非 volatile）未闭环。
- **风险**: 宿主/运维代码直接调用 `applyChange`（方法无任何「仅限 executor 调用」声明）即破坏串行假设；重复通知在插件 reconcile（订阅了全局配置键，见 PluginManagerImpl.subscribeGlobalConfig）上表现为重复级联激活扫描，放大长临界区占用。
- **建议**: `applyChange` 加 `synchronized`（低频路径，代价可忽略）或将方法可见性降为包内 + 仅暴露 `ConfigChangeApplier.requestUpdate`；`configSource` 字段补 volatile。
- **失效面枚举**: 复合操作入口全集 = {`applyChange`（**本案**）、`changeConfigSource` L124（同面，另含 configSource 可见性）、`updateRefs`（仅被 changeConfigSource 调用，随案覆盖）、`ConfigChangeApplier.activate/deactivate/requestUpdate`（CAS 协议，复核通过）}。对照面：`usedRefs.computeIfAbsent`（makeConfigRef L212）无递归无 I/O，复核通过。
- **信心水平**: 确定（无同步复合操作为代码事实；重复通知后果依赖 listener 幂等性）
- **误报排除**: 不是「volatile 原语使用」误报；CAS 协议本体经逐步推演确认成立后未立案，本案只裁协议外的绕过面——符合「复合操作误用才是问题」的校准口径。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P2（维持；表述修正一处）**。applyChange L255-295 public 无同步复合操作（configValues 写 L261 / configRefs 写 L290 / trigger L294 非原子）、configSource 非 volatile（L50）、changeConfigSource L124-131 同面——亲核属实；ConfigChangeApplier CAS 协议（requestUpdate compareAndSet + applyChange 全量重读）复核成立，维持「不立案」。**修正**：ConfigStarter.applyChange 为 **protected**（L489-491，子类可达）非 public——绕过面表述修正为「DefaultConfigProvider.applyChange 本身 public + ConfigStarter 子类 protected 可达」，实质不变。现状 = 新发现，未修复。

### [nop-core-framework-C2-009] ProducedBeanInstance 异常路径不对称：Error 逃逸后实例滞留 STATUS_CREATED 且无失败广播，后续获取者永久 wait()

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:546-585,591-630`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:429-431`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/ProducedBeanInstance.java:91-109,128-134`
- **证据片段**:
  ```java
  // BeanDefinition.createInstance L574 —— 仅 catch Exception
  } catch (Exception e) {
      if (producedBeanInstance != null) {
          producedBeanInstance.markPropSetFailed(e);
      }
      ...
  }
  // BeanContainerImpl.getBean0 L429-431 —— setupInstance 在锁外调用且外层无兜底
  if (created) {
      beanDef.setupInstance(beanInstance, beanScope, this, beanCtx);
  }
  ```
- **严重程度**: P2
- **并发场景**: 创建线程在 `scope.add` 之后、`markPropSet()` 之前抛出 `java.lang.Error`（用户 setter/init 登记路径上的 `NoClassDefFoundError`/`StackOverflowError` 等——AOP 拦截器装配、XPL 求值均为 Error 高发面），此后任意线程经 includeCreating=false 获取该 bean。
- **触发时序**: T1 创建 A：`createInstance` 已 `scope.add`（实例入 scope，status=STATUS_CREATED）→ 返回后 `setupInstance`（L430，锁外、无 Error 捕获）在属性赋值中抛 Error → `setupInstance` 内 catch(Exception) 不命中 → Error 传播出 `getBean0`（finally 仅 clearInCreation）→ **实例滞留 scope 且 status=CREATED、initError=null**。T2 之后任何 `getBean("A")`（includeCreating=false）→ `checkBeanInitialized → runUntil(INITIALIZED)` → status < PROPERTY_SET 且 `createThread(T1) != T2` → **`wait()` 永久阻塞**：PROPERTY_SET 相位按协议只能由 createThread 推进，而 createThread 已死亡，无任何线程能唤醒 T2。
- **现状**: Exception 路径完整（`markPropSetFailed` 广播 + 等待者重抛，注释明示该意图）；`runUntil` 的阶段回调也 catch Error——唯独「PROP_SET 标记前的 Error」这一段无兜底，协议对称性破缺。
- **风险**: 一次 OOM/类加载 Error 后，该 bean 的全部后续获取永久挂死且无诊断（线程栈只显示 wait()）；挂死不可自愈不可检测（对照 F5）。
- **建议**: `getBean0` 对 `setupInstance` 调用补 `catch (Throwable t) { beanInstance.markPropSetFailed(t); throw t; }`；或 `createInstance`/`setupInstance` 的 catch 放宽为 `Throwable`（Error 亦广播后 rethrow）。
- **失效面枚举**: 失败广播点全集 = {`createInstance` catch（Exception，覆盖 newInstance 段——Error 漏）、`setupInstance` catch（Exception，覆盖属性赋值段——Error 漏，**本案**）、`runUntil` 阶段回调 catch（RuntimeException|Error，**已覆盖**）、`markPropSet/markPropSetFailed`（notifyAll 语义正确）}。滞留后果入口 = {`getBean(name,false)`、`getBeanByType(...,false,...)`、`initBean` 的 depend 循环、`getBeansOfType/getBeansWithAnnotation`}。
- **信心水平**: 确定（协议不对称与滞留后果为代码事实；Error 触发概率低故 P2）
- **误报排除**: 不是「吞异常」F9 的简单重复——问题是失败广播机制对 Throwable 子集失效导致**他人挂死**，属锁/条件变量协议纪律；Exception 路径已有修复意图注释，本案为其未闭合的另一半。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P2（维持）**。失败广播不对称亲核：createInstance catch(Exception)（L574）、setupInstance catch(Exception)（L621）、getBean0 L429-431 调 setupInstance 无 Throwable 兜底；Error 逃逸后实例滞留 scope 且 status=STATUS_CREATED、initError=null；runUntil 检查序（L128-134：status<PROPERTY_SET 仅 createThread 可推进，其余 wait）→ 创建线程死亡后任何获取者永久 wait() 且无广播——每步成立。现状 = 新发现，未修复。

### [nop-core-framework-C2-010] PluginScopeImpl effect 取消闭包在 effects monitor 内执行用户 dispose

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/PluginScopeImpl.java:53-60,70-87`
- **证据片段**:
  ```java
  return () -> {
      synchronized (effects) {
          if (effects.remove(d)) {
              d.dispose();                 // 用户 dispose 在锁内
          }
      }
  };
  ```
- **严重程度**: P3
- **并发场景**: 插件 activator 或业务代码单独取消某个 effect（调用 effect() 返回的 Disposable），与其它线程的 `effect()` 注册/`effects()` 快照并发。
- **触发时序**: T1 调用 Disposable.run → 持 `FX` → `d.dispose()` 执行用户回调（慢 I/O 或回调内再取其他锁 `L2`）；T2 同时调用 `scope.effect(d2)` 阻塞在 `FX` 上——T1 的用户代码耗时被放大为注册面阻塞；若 dispose 回调内再调用 `L2` 而他线程持 `L2` 等 `FX`，可构成跨类锁序环（需 dispose 代码配合，未观测）。
- **现状**: `close()`（L70-87）的纪律正确——快照后锁外 LIFO dispose；唯独单 effect 取消路径不对称。
- **风险**: 锁内用户代码违反本类族「锁内不回调用户代码」的既定纪律（ProducedBeanInstance 类注释 L23-24 明文声明该纪律），dispose 慢或死锁时波及同 scope 全部注册/查询。
- **建议**: 与 close() 对齐：`synchronized(effects){ removed = effects.remove(d); } if (removed) d.dispose();`。
- **失效面枚举**: 同类回调站点 = {`effect()` 返回闭包（锁内，本案）、`close()`（锁外，合规）、`ProducedBeanInstance.runPhaseCallback`（锁外，合规）、`BeanCreationContext.runActions`（锁外，合规）、`DefinitionConfigProvider.notifyListeners`（锁外，合规）}——同族仅此一处漏改（F3 清扫完成）。
- **信心水平**: 确定
- **误报排除**: 不是对「synchronized 使用」的泛化报告——同文件 close() 已示范正确纪律，本案是同族不对称修复建议。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P3（维持）**。effect() 返回闭包在 synchronized(effects) 内执行 d.dispose()（L53-59）与 close() 快照后锁外 LIFO dispose（L70-87）的不对称亲核属实；同族纪律对照（ProducedBeanInstance L23-24 类注释明文）核实。现状 = 新发现，未修复。

### [nop-core-framework-C2-011] BeanContainerImpl.getClassIntrospection volatile 检查-然后-行动：并发首调可产生重复实例（良性，判 P3 记录）

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
- **并发场景**: 派生容器未显式 setClassIntrospection 时，两个线程并发首调 `getBeanByType/injectTo` 等路径。
- **触发时序**: T1、T2 同时读到 `classIntrospection == null` → 各自构造 `DefaultBeanClassIntrospection` → 后写者覆盖前写者。volatile 保证发布安全（无半成品对象，注释声明正确），仅产生实例重复。
- **现状**: 发布安全性成立；实例唯一性不成立。
- **风险**: 极低——两个完整实例各自持有反射缓存，功能等价，仅缓存内存与冷启动重复反射开销；无正确性影响（introspection 无跨请求可变业务状态）。
- **建议**: `classIntrospection.compareAndExchange`-式收敛或直接构造期注入（派生容器在 buildNewInstance 中显式复制父值）。
- **失效面枚举**: 同形态懒初始化全集 = {`getClassIntrospection`（volatile，本案）、`LoggerConfigurator.s_instance`（volatile，全局单例注册语义，重复注册有日志提示，合规）、`VfsPluginDefinition.scope/container/configProvider`（volatile，activate 单飞下写入，合规）}；仓库内未发现无 volatile 的双检锁（DCL 全扫通过）。
- **信心水平**: 确定（重复为代码事实，后果评估为良性）
- **误报排除**: 按 P3 优化候选口径登记并明确「不构成正确性缺陷」，避免复核阶段被误升级。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P3（维持）**。volatile + 本地变量检查-然后-行动（L131-140）亲核属实；发布安全性成立、实例唯一性不成立的判定维持。现状 = **历史修复后的残余面已知**（volatile+本地变量形态系 commit 4ffaebb267「classIntrospection 懒初始化本地变量」修复产物；残余重复实例面未再修复），良性记录定位维持。

---

## 维度集外线索（不立案展开）

- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**确认（移交 C4/C8/C6）**。亲核属实：`Executors.newVirtualThreadPerTaskExecutor()` 无并发上限/背压、经 `registerGlobalWorker()` 注册为全局 worker、`stats()` 返回 null、`destroy()` 仅 `shutdown()` 无 awaitTermination（VirtualThreadTaskExecutor L18-68）。锁纪律本体无缺陷的定位维持。

### [nop-core-framework-CL-004]（维度集外线索，不立案展开）VirtualThreadTaskExecutor：全局 worker 注册无界虚拟线程执行器，无并发上限/背压，stats()=null，destroy 无等待
- **文件**: `nop-utils/nop-commons-java21/src/main/java/io/nop/commons/jdk21/VirtualThreadTaskExecutor.java:16-68`
- 形态归属：C4（线程池治理——`newVirtualThreadPerTaskExecutor` 每任务一线程无上限、经 `registerGlobalWorker` 注册为全局 worker 后 IoC 容器启动等框架路径共享该池）+ C8（无拒绝/背压语义）+ C6（`destroy()` 仅 shutdown 无 awaitTermination，在途任务资源回收未定义）+ 可观测性（`stats()` 返回 null，池治理面板失明）。锁纪律本体无缺陷，不在 C2 裁决范围，移交 C4/C8 轮次。

- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**确认（移交 C8/C4）**。亲核属实：`DefaultThreadPoolExecutor.newExecutor("nop-config-executor", 1, 100, true)`（SingleThreadConfigExecutor L18）；C2 邻接串联面核实——subscribeGlobalConfig 的 reconcile 回调经 DefaultConfigProvider.applyChange → subscriptions.trigger 运行于该单线程（PluginManagerImpl L548-558 + ConfigChangeApplier executor 路径），插件激活与配置刷新同流串联。

### [nop-core-framework-CL-005]（维度集外线索，不立案展开）SingleThreadConfigExecutor 队列上限 100 的满载行为与配置线程上的重任务串联
- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/starter/SingleThreadConfigExecutor.java:18`
- 形态归属：C8（`DefaultThreadPoolExecutor.newExecutor(name, poolSize=1, queueSize=100, true)` 队列满后的拒绝/阻塞行为未在本轮验证，拒绝任务去向未声明）+ C2 邻接观察：`PluginManagerImpl.subscribeGlobalConfig` 把 reconcile（含子容器启动）挂到该单线程上，配置刷新与插件激活在单执行流上串联，长任务互相放大延迟。移交 C4/C8 轮次核实拒绝策略。

- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**确认（移交 C6）**。亲核属实：destroyBean（BeanContainerImpl L625-631）销毁实例但不摘 BeanScopeImpl 条目，scope.close → remove → destroyBean 的二次 destroy 面成立；与 remove(name, bean)（BeanScopeImpl L81-87，摘除+销毁正确配对）的不对称核实。**移交 C6 时请与 CL-018（close 销毁顺序）注明为同位点相邻族（同经 BeanScopeImpl.remove → destroyBean 路径）**。

### [nop-core-framework-CL-006]（维度集外线索，不立案展开）BeanContainerImpl.destroyBean 不摘除 scope 条目：与 stop() 的二次 destroy 面
- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:624-631`；`BeanScopeImpl.java:80-87,96-118`
- 形态归属：C6（资源生命周期——`destroyBean(beanName, bean)` 销毁实例但 `BeanScopeImpl` 条目仍在，容器 `stop() → scope.close()` 会对同一 bean 再次 destroy；destroyMethod 的二次调用安全性完全依赖用户代码幂等）。对照 `remove()`（摘除 + 销毁，正确配对）构成同族不对称。移交 C6 轮次。

- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**确认（移交 C1/C6）**。亲核属实：onContainerStop 对 containerScope.scopes（CHM）弱一致迭代关闭请求域 scope（BeanScopeContextImpl L83-98）；DynamicConfigSource.onClose 非 volatile 且 setOnClose/close 无同步（L56-63）。

### [nop-core-framework-CL-007]（维度集外线索，不立案展开）请求域 scope 生命周期与上下文属性的弱一致交互
- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanScopeContextImpl.java:60-71,83-98`；`nop-core-framework/nop-config/src/main/java/io/nop/config/source/DynamicConfigSource.java:50-63`
- 形态归属：C1/C6——`onContainerStop` 对 `containerScope.scopes` 弱一致迭代关闭请求域 scope，与并发请求线程 `bind/unbind` 交错时「刚绑定的 scope 可能漏关/已解绑 scope 重复关」；`DynamicConfigSource.onClose` 非 volatile 且 setOnClose/close 无同步。C1 轮共享状态盘点若未覆盖此处，移交复核阶段合并。

---

## 汇总

### 发现清单

| 编号 | 严重程度 | 文件 | 摘要 |
|---|---|---|---|
| nop-core-framework-C2-001 | P1 | BeanContainerImpl L406 / InjectTypeValueResolver / BeanDefinition L534 / BeanTopologySorter | beanDef 锁序环：type-injection/factoryBean 边不可见 → 跨线程 beanDef monitor 互等死锁 |
| nop-core-framework-C2-002 | P1 | ProducedBeanInstance L114-170 / BeanDefinition L642-644 | PBI 跨实例 monitor 等待环闭合条件深化；wait() 无超时无挂死可见性 |
| nop-core-framework-C2-003 | P2 | BeanContainerImpl L90/477/503/537 | start 守卫非原子 + startFuture 非 volatile（awaitStartFinished 丢失唤醒形态）——启动期条件化降档 |
| nop-core-framework-C2-004 | P2 | VfsPluginDefinition L358-593 / AbstractPlugin L75 | 插件状态转换部分在 lifecycleLock 外 → unload×activate 孤儿激活窗口；AbstractPlugin.state 非 volatile |
| nop-core-framework-C2-005 | P2 | PluginManagerImpl L493/569 / HttpPluginResourceResolver L101 | computeIfAbsent 映射函数内 HTTP+SHA256+类加载+start：bin 锁长临界区与递归面（CL-002 同站点 C2 深化） |
| nop-core-framework-C2-006 | P3 | PluginManagerImpl L86-243 | reconcileLock 矩阵缺口审计：无锁生命周期入口经状态机守卫大多 fail-fast，钉死契约防回归 |
| nop-core-framework-C2-007 | P2 | DefinitionConfigProvider L247-265 | 订阅清理 check-then-act → 孤儿 list 上监听器永久失聪 |
| nop-core-framework-C2-008 | P2 | DefaultConfigProvider L255-315 / ConfigChangeApplier | applyChange 无锁复合操作，单线程 executor 契约可被 public 入口绕过 |
| nop-core-framework-C2-009 | P2 | BeanDefinition L574/621 / ProducedBeanInstance L128 | Error 逃逸后 STATUS_CREATED 滞留无失败广播 → 后续获取者永久 wait() |
| nop-core-framework-C2-010 | P3 | PluginScopeImpl L53-60 | effect 取消闭包在 effects monitor 内执行用户 dispose（同族不对称） |
| nop-core-framework-C2-011 | P3 | BeanContainerImpl L131-140 | getClassIntrospection volatile 检查-然后-行动重复实例（良性，记录） |

### CL 线索

- CL-004：VirtualThreadTaskExecutor 无界全局 worker（C4/C8/C6）
- CL-005：SingleThreadConfigExecutor 队列上限拒绝行为 + 配置线程重任务串联（C8/C4）
- CL-006：destroyBean 与 scope 条目不对称 → 二次 destroy（C6）
- CL-007：请求域 scope 停机弱一致迭代 + DynamicConfigSource.onClose 可见性（C1/C6）

### 总计

- **C2 发现：11 条**（P0×0、P1×2、P2×6、P3×3）；**维度集外线索：4 条**（CL-004~007）。
- 盲审去重声明：JdbcConfigSource close 竞态、插件 unload 交错本体、computeIfAbsent bin 锁内 I/O 本体（CL-002）均未重复立案，仅按任务指派做同族/锁纪律深化并显式交叉引用。
- 待复核事项：C2-001/C2-002 的死锁时序建议子项复核逐交错验证；C2-005 与 C1 轮 CL-002 的合并裁定归跨报告合并裁定阶段。

---

## 维度复核结论

- **复核人**: C2 维度独立复核子代理（与初审/第 2 轮盲审代理相互独立）。
- **复核日期**: 2026-10-17。
- **复核对象**: `C2-lock-sync.md`（C2-001~011 + CL-004~007）+ `C2-round2.md`（C2-012~017 + CL-017/018 + 锁内用户代码站点表 20 行），合计 17 条发现 + 6 条 CL 线索。
- **复核方法**: 全部 file:line 证据亲自打开 live code 核对（行号漂移以实际代码为准修正，实测漂移均 ≤2 行）；P1 发现逐步交错推演；本阶段解除盲审，对照 `ai-dev/audits/check/nop-core-framework.md`（历史 check 审计）、`cra-index.md`、git log（commit 4ffaebb267 / bfcd5f1a83 / 428a196557 等）做现状标注；C1 轮 C1-003 复核裁定作为 C2-013/C2-015 的一致性基准；F3 完备性独立 grep 补查（锁载体/wait/notifyAll/syncGet/computeIfAbsent 四个全集）。
- **裁定口径**: 保留/降级/驳回三级；裁定可改严重度、不改事实；证据不成立且无替代证据 → 驳回。

### 一、逐条裁定汇总表

| 编号 | 初审判级 | 复核裁定 | 现状标注 | 一句话依据 |
|---|---|---|---|---|
| C2-001 | P1 | **保留 P1（终裁维持）** | 已知未修复（历史 check 审计 P2-6 暂缓项同源） | 不可见边与跨线程互等序列逐步核实；allow-cycle 默认 true 强化「无构建期守卫」 |
| C2-002 | P1 | **保留 P1（终裁维持）** | 新发现，未修复 | BD⇄PBI 混合环双腿逐代码点核实，wait 无超时属实 |
| C2-003 | P2 | 保留 P2 | 新发现，未修复 | 代码事实全核实；「无生产调用方」降档依据复核属实 |
| C2-004 | P2 | 保留 P2（事实修正 1 处） | 部分已知未修复（volatile 面已修复） | 孤儿激活交错成立；unloadPlugin 对 holder==null 为静默 no-op 而非抛错 |
| C2-005 | P2 | 保留 P2 | 新发现，未修复 | 映射函数 HTTP+SHA256+类加载+start 亲核属实 |
| C2-006 | P3 | 保留 P3 | 新发现，未修复 | 锁矩阵与守卫 fail-fast 推演一致 |
| C2-007 | P2 | 保留 P2（交错序列修正） | 新发现，未修复 | 真实窗口在 isEmpty() 与 remove(pattern) 之间，机制与后果不变 |
| C2-008 | P2 | 保留 P2（表述修正 1 处） | 新发现，未修复 | 复合操作无锁属实；ConfigStarter.applyChange 系 protected 非 public |
| C2-009 | P2 | 保留 P2 | 新发现，未修复 | catch(Exception) 不对称 + 永久 wait 推演逐点核实 |
| C2-010 | P3 | 保留 P3 | 新发现，未修复 | 锁内 dispose 与 close 锁外不对称属实 |
| C2-011 | P3 | 保留 P3 | 历史修复后的残余面已知（4ffaebb267） | volatile+本地变量即历史修复产物，残余重复实例良性记录 |
| C2-012 | P2 | 保留 P2（指派抽验 2 边通过） | 新发现，未修复 | E5 构造参数路径、E10 config 单线程入口与 live code 一致 |
| C2-013 | P2 | 保留 P2（与 C1-003 口径自洽） | 新发现，未修复 | 拦截器为 beanMethod==null 普通 bean，L744 条件为 false 跳过等待——与 C1 裁定（代理面）分属不同域 |
| C2-014 | P2 | **驳回** | —（不立案） | 环闭合第二腿不成立：映射函数 aware 轨仅 load、非 aware 轨 build 不 start，均不持 LC |
| C2-015 | P3 | **驳回** | —（不立案，同 C1-003） | ISE 窗口经容器路径不可达（L744 条件为真阻塞至 INITIALIZED / handler 自构造非 null），与 C1 复核同口径 |
| C2-016 | P3 | 保留 P3 | 新发现，未修复 | 两段锁 check-then-act 亲核属实 |
| C2-017 | P3 | 保留 P3 | 新发现，未修复 | 接口零 javadoc 与 8 处 syncGet 判定全数核实 |

**CL 线索处置**: CL-004 确认（移交 C4/C8/C6）、CL-005 确认（移交 C8/C4）、CL-006 确认（移交 C6，与 CL-018 注明同位点）、CL-007 确认（移交 C1/C6）、CL-017 确认（与 CL-005 合并量化）、CL-018 确认（移交 C6，与 CL-006 同位点）。**6 确认 / 0 驳回**。

### 二、特别复核指令终裁

1. **C2-001 终裁 = P1 维持**。(a) `InjectTypeValueResolver` 全文无 collectDepends（默认空实现 IBeanPropValueResolver L31-33）；factoryBean 边（BeanDefinition.newInstance L534，`getBean(factoryBean, false, beanCtx)`）不经 resolver 收集——两均不入图，属实。(b) `isInCreationByCurrentThread`（L86-98）为实例字段比对当前线程，跨线程无效；同线程 L412 fail-fast 掩护属实；跨线程 T1/T2 各持一 BD 锁在对方 monitor 入口互等的序列逐步可达（目标不在 scope 时 L376-387 快速通道不命中）。(c) 与 C1-003 复核口径自洽：本案面（构造参数求值的不可见依赖边）与 C1 驳回面（代理 handler nullability 的 includeCreating 快速通道）不同域，无冲突。(d) 维持 P1 的附加依据与修正：`nop.ioc.bean-depends-graph.allow-cycle` 默认 **true**（IocConfigs L64-66）——即使按建议 (1) 补收集，默认配置下 sortBeans 拒环检查（L256-261）也不执行，修复须绑定 allow-cycle 默认值裁定；现状为历史 check 审计 P2-6 暂缓项的同源回访（该处置记录明言「随 IoC 容器锁纪律专项一并裁定」，本轮即该专项），P1 与历史 P2 的判级张力留合并裁定阶段知悉。
2. **C2-002 终裁 = P1 维持**。环闭合双腿逐步核实：X 侧 = init 用户代码公开 `getBean(Y)`（includeCreating=false，L182）→ checkBeanInitialized → PBI(Y) initThread=T2 → wait（L136-141/L194-201 无超时）；Y 侧 = resolvedDepends 声明边（L642-644，完全可见即可）；等待者不持 BD（L433 在 synchronized 块外）；同线程 selfWait（L120-121/L137-138）掩护属实。每步可信 → 按复核规则维持 P1。
3. **C2-012 指派抽验通过**。E5：InjectRefValueResolver L32（force-lazy-property 并入 ignoreDepends）/L66-70（collectDepends 条件跳过）/L78（构造参数路径持 BD 锁 getBean）三点一致；E10：subscribeConfigChange L774-776 闭包 → resolveValue → 惰性首建 BD 锁链，运行于 config-executor 单线程（SingleThreadConfigExecutor L18，poolSize=1）一致。
4. **CL 处置**: 全部 6 条确认（核实细节见各条目追加的复核状态行）；CL-006 与 CL-018 已互相注明为 C6 同位点相邻族（同经 BeanScopeImpl.remove → destroyBean 路径）。

### 三、争议点与修正记录（不改判级的事实性修正）

1. **C2-007 交错序列修正**：初审所写「T2 add 后 T1 的 isEmpty() 仍为 true」不可达（CopyOnWriteArrayList 语义）；真实竞态窗口为「isEmpty() 判定」与「listeners.remove(pattern) 执行」两指令之间。机制、后果（F9 静默失效）与修复建议不变。
2. **C2-004 终态描述修正**：unloadPlugin 对 holder==null 是静默 no-op（PluginManagerImpl L146-147），不抛 ERR_PLUGIN_DEFINITION_NOT_LOADED（该错误由 activatePlugin 的 requireLoaded L209-215 抛出）；「孤儿容器不可卸载」实质成立。
3. **C2-008 绕过面表述修正**：ConfigStarter.applyChange 为 protected（子类可达）；绕过面主体是 DefaultConfigProvider.applyChange 本身的 public 可见性。
4. **C2-001 建议修正**：建议 (1) 的「使环在 sortBeans 被拒」在 allow-cycle 默认 true 下不生效，须连带默认值裁定或独立逃逸边校验。
5. **C2-014 驳回论证**：aware 轨映射函数仅 `plugin.load(config)`（无 LC）；非 aware 轨 `plugin.start(4-arg)` → doStart → buildBeanContainer(false) 构建不 start、不 activate（无 LC）；VFS 轨映射函数仅 load+订阅。初审引用链「plugin.start → AbstractPlugin.activate() → synchronized(lifecycleLock)」在映射函数内不成立 → LC⇄BIN 环不可闭合。残余阻塞面（LC 内 activator 回调 manager API）并入 C2-005/C2-006。
6. **C2-015 驳回论证**：与 C1 复核驳回 C1-003 完全同口径——L744 条件使 beanMethod!=null 代理在 includeCreating=true 时照常执行 checkBeanInitialized（阻塞至 INITIALIZED，handler 已回填 + HB 成立）；beanMethod==null 代理 handler 自构造非 null；onlyProducer 路径返回 createdBean 非代理。残余 volatile 理论面无具体触发序列，不立案；javadoc 建议归 C2-017 随行。
7. **C2-013 与 C1-003 的口径边界（自洽声明）**：C1 复核裁定「includeCreating=true 时 checkBeanInitialized 照常执行」的适用域是 **beanMethod!=null 的代理 bean**（L744 第二析取支为真）；C2-013 的拦截器 bean 是 **beanMethod==null 的普通 bean**（第二析取支为假 → 跳过 checkBeanInitialized → 半成品注入可达）。两裁定分别成立、无矛盾；C2-015 恰因落在 C1 已裁定域内而驳回。

### 四、F3 完备性补查（独立 grep）

- **锁载体全集**：`synchronized|ReentrantLock|ReadWriteLock|Collections.synchronized`（main 源码，排除 _gen/test）命中 48 行，其中注释 2 行（BeanDefinition L83/L640）+ 本轮复核确认其余命中全部落入两轮矩阵/站点表——**无漏登记载体**。
- **wait/notifyAll 全集**：scope 内唯一 Object.wait 站点 = ProducedBeanInstance.waitQuietly（L194-201），notifyAll ×4（L95/107/156/167）——与 ④ 表一致，无残留。
- **syncGet 全集**：8 处（VfsPluginDefinition L568/615、PluginManagerImpl L180/407/659、AbstractPlugin L342/424、BeanContainerImpl L538）——6 处 futureCall 同线程内联（FutureHelper.futureCall L122-129 亲核）非等待点，2 处真等待点（awaitStartFinished、invokeCommand）均已立案——无残留。
- **computeIfAbsent 全集**：18 处命中逐一对表——运行期重负载面（loadPluginFromJar/loadPluginFromVfs）已立案 C2-005，其余为构建期单线程或轻量映射，与 ③ 表/失效面枚举一致——无漏立案新站点。
- **维度集外新线索（复核新增，不立案，移交合并阶段）**：`BeanDefinition.getConstructorArgs` L517 以 **null beanCtx** 调 `resolveValue`；`BeanContainerImpl.getBeanByType` L327-328 在 beanCtx==null 时新建 BeanCreationContext 且**无 flushActions**（对照 getBean L305-309 新建即 flush）——经构造参数**按类型注入**惰性首建的 bean，其 init 动作登记进孤儿上下文无人 flush，init 推迟至下一次显式 checkBeanInitialized 触发（对照：ref 路径经 getBean 新建即 flush，两路径不对称）。非锁/同步缺陷，属 init 调度/契约面（C1/C6 或功能域），建议合并阶段裁定是否立案。

### 五、复核统计

- **发现 17 条**：保留 **15**（P1×2、P2×8、P3×5）、驳回 **2**（C2-014、C2-015）、降级 0、升级 0。
- **CL 线索 6 条**：确认 **6**、驳回 0（其中 CL-006/018 移交 C6 时注明同位点；CL-017 与 CL-005 合并量化）。
- **现状分布**：新发现未修复 12；已知/部分已知未修复 3（C2-001 = 历史 P2-6 暂缓同源、C2-004 = volatile 面已修复、C2-011 = 历史修复后残余）；驳回 2。
- **P1 终裁**：C2-001、C2-002 均**维持**（触发时序逐步可信，见本节二.1/二.2）。

---

## 最终保留项

复核后 C2 维度（锁与同步）最终保留清单（15 条 + 6 CL 移交项）：

| # | 编号 | 终判级 | 站点 | 保留要点 |
|---|---|---|---|---|
| 1 | C2-001 | P1 | BeanContainerImpl.getBean0 L406 / InjectTypeValueResolver / BeanDefinition L534 | beanDef 锁序环：type-injection/factoryBean 边不入拓扑图 → 跨线程 monitor 互等死锁；修复须含 allow-cycle 默认值裁定；历史 P2-6 暂缓项同源回访 |
| 2 | C2-002 | P1 | ProducedBeanInstance.runUntil L136-141/L194-201 / BeanDefinition L642-644 | PBI 跨实例等待环：init 期交叉获取 + wait 无超时 → 双 owner 互等；修复 = wait(timeout) + 等待链环嗅探 |
| 3 | C2-003 | P2 | BeanContainerImpl L90/L477/L503/L537 | start 守卫非原子 + startFuture 非 volatile；启动期条件化降档维持 |
| 4 | C2-004 | P2 | VfsPluginDefinition L358-593 / AbstractPlugin L75 | 锁外状态转换 → 孤儿激活；AbstractPlugin.state 补 volatile 为最低限度；unloadPlugin 静默 no-op 事实已修正 |
| 5 | C2-005 | P2 | PluginManagerImpl L493/L569 / HttpPluginResourceResolver | jar 轨 computeIfAbsent 映射内 HTTP+SHA256+类加载+start：bin 锁长临界区；结构性修复（双检+锁外 start+putIfAbsent）同断 C2-014 残余面 |
| 6 | C2-006 | P3 | PluginManagerImpl L86-243 | reconcileLock 矩阵缺口：钉死契约防回归 |
| 7 | C2-007 | P2 | DefinitionConfigProvider L247-265 | 订阅清理 check-then-act → 孤儿 list 失聪（窗口 = isEmpty 与 remove(pattern) 之间）；修复 = cleanup 改 compute 原子化 |
| 8 | C2-008 | P2 | DefaultConfigProvider L255-295 / ConfigChangeApplier | applyChange 无锁复合操作；建议 synchronized + 可见性收窄（ConfigStarter 为 protected 已修正表述） |
| 9 | C2-009 | P2 | BeanDefinition L574/L621 / BeanContainerImpl L429-431 | Error 逃逸无失败广播 → 后续获取者永久 wait()；修复 = catch Throwable 广播后 rethrow |
| 10 | C2-010 | P3 | PluginScopeImpl L53-59 | effect 取消闭包锁内 dispose；与 close() 对齐 |
| 11 | C2-011 | P3 | BeanContainerImpl L131-140 | getClassIntrospection 重复实例（良性记录；历史修复后残余） |
| 12 | C2-012 | P2 | InjectRefValueResolver L32/L66-78 / BeanContainerVariableScope L28 / BeanDefinition L770-788 | 不可见边族扩展（E4/E5/E9/E10）；ignore-depends 构建期告警建议与 allow-cycle 裁定合并 |
| 13 | C2-013 | P2 | BeanDefinition L269-277/L675-686 / AopBeanProcessor L301-325 | AOP 拦截器 bean 边不入拓扑图；collectDepends 补遍历 ioc:interceptors（beanMethod==null 普通 bean 快速通道跳过等待，与 C1-003 驳回域不冲突） |
| 14 | C2-016 | P3 | DefinitionConfigProvider L200-213 | updateConfigValue 两段锁 → 键复活；单临界区化修复 |
| 15 | C2-017 | P3 | IBeanContainerImplementor L45-47 / invokeCommand / awaitStartFinished | includeCreating 语义与无界等待契约零声明；javadoc + 带超时重载（依赖 C2-002 修复落地）；C2-015 残余 javadoc 建议随行 |

**CL 移交项（全部确认）**：CL-004 → C4/C8/C6（虚拟线程全局 worker 无界）；CL-005 → C8/C4（config 单线程队列 100 拒绝行为 + reconcile 串联）；CL-006 → C6（destroyBean 二次 destroy；与 CL-018 同位点）；CL-007 → C1/C6（请求域 scope 停机弱一致 + DynamicConfigSource.onClose 可见性）；CL-017 → 与 CL-005 合并量化（configDependants 级联无环检测）；CL-018 → C6（哈希序销毁非逆拓扑序；与 CL-006 同位点）。

**合并裁定阶段移交清单**：① C2-012 与 C2-001 同族合并（修复方案共用）；② C2-005 与 C1 轮 CL-002 同站合并；③ C2-001 的 P1 判级与历史 P2-6 暂缓裁定的判级张力（建议以本轮专项属性裁定）；④ 集外新线索「getBeanByType 孤儿上下文」（四.F3）是否立案；⑤ C2-014 残余阻塞面并入 C2-005/C2-006 的处置注明；⑥ C2-015 驳回与 C1-003 驳回的口径对齐记录。

**终止信号**：C2 维度复核完成——保留项全部经 live code 逐步核实，驳回项均有分支级反证；两轮枚举空间（锁载体/等待原语/依赖边/用户代码站点/契约面）经独立 grep 复核闭合。**同意第 2 轮报告「不再发起第 3 轮」的终止建议**。
