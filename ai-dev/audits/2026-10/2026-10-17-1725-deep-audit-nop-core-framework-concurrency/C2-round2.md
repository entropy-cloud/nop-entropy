# C2 维度（锁与同步）第 2 轮深挖报告——nop-core-framework + nop-utils

## 审计单元

- **审计对象**：框架层 `nop-core-framework` 六子模块（nop-ioc、nop-config、nop-plugin（manager/support）、nop-security、nop-log、nop-boot）+ `nop-utils`（src/main 口径，排除 `_gen` 与测试代码）。
- **仓库**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）。
- **审计人**：C2 维度第 2 轮盲审子代理。
- **判级口径**：沿第 1 轮 2468 校准（P0 主路径数据竞争/可达死锁；P1 罕见但可具体化竞态/负载上升必然触发死锁；P2 理论竞态/锁粒度可证明热点/缺测试保护；P3 优化候选）。「启动期单线程触达」维持条件化降档。

## 本轮输入

- 第 1 轮报告 `C2-lock-sync.md`（发现 C2-001~011、CL-004~007、44 处锁站点矩阵、全模块锁序图、环闭合条件 ①②③）。
- live code 复读：`BeanContainerImpl`、`BeanDefinition`、`BeanTopologySorter`、`ProducedBeanInstance`、`BeanCreationContext`、`BeanScopeImpl`、`BeanProperty`、`BeanContainerVariableScope`、`DelegateInvocationHandler`、`resolvers/*`（全部 17 个）、`BeanDefinitionBuilder`（解析段）、`AopBeanProcessor`、`AppBeanContainerLoader`、`DelegateBeanContainer`、`BeanScopeContextImpl`、`DefaultConfigProvider`、`ChangeSubscriptions`、`DefinitionConfigProvider`、`PluginManagerImpl`、`VfsPluginDefinition`、`AbstractPlugin`、`PluginScopeImpl`。
- api-core 对照（不立案）：`FutureHelper`（syncGet/futureCall）、`ContextTaskQueue.syncGet`、`IBeanContainer`。

## 盲审纪律声明

本轮为**盲审轮次**：除本维度第 1 轮文件 `C2-lock-sync.md` 外，未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何其他文件，未读取同目录其他维度文件。api-core 仅对照阅读不立案。任务反虚构预注中「C1 轮已立案插件状态机族（unload 交错/state 非 volatile/复合读 8 站）」仅用于同族去重（插件状态机本体不重复立案，锁纪律角度仅 F3 互引）。全部新发现的证据均来自本轮 live code 逐文件复读与交错推演；凡本轮实测与任务猜测不符的（如「@Inject 集合注入不可见」「StaticObjectFactory 存在」），以实测为准并在对应矩阵中标注「验证安全/不存在」。

## 本轮目标（①~⑤）

1. **① C2-001 失效面扩展**：枚举 BeanTopologySorter 拓扑图对实际创建依赖的全部不可见边（除 InjectTypeValueResolver/factoryBean 外），逐边给出锁序环闭合条件（T1/T2 交错序列）。
2. **② C2-002 家族矩阵**：穷举 init/工厂方法/destroy 阶段交叉获取的全部调用站点与等待环闭合形态。
3. **③ 锁内用户代码全扫**：`synchronized(beanDef)`/`lifecycleLock`/`reconcileLock`（含 mergedView/effects）临界区内用户代码执行站点登记表。
4. **④ Condition/wait 面**：框架层全部 `wait(`/`await(`/`syncGet(` 站点逐一判定（丢失唤醒/虚假唤醒/无超时）。
5. **⑤ 锁契约缺失面**：public 无锁方法 vs 私有锁内实现的契约不对称；容器内部类同步契约 javadoc 缺失面。

---

## ① 依赖边不可见族全枚举（C2-001 失效面扩展）

拓扑图来源 = `BeanTopologySorter.sortBeans`（L224 `bean.collectDepends(deps)`）与 `fillResolvedDepends`（L175）。`BeanDefinition.collectDepends`（L269-277）只遍历 **constructorArgs + props** 两个 resolver 集合。逐边核查结果：

| # | 边 | 触发路径 | 是否入图 | 持 BD 锁时执行？ | 锁序环闭合条件（T1/T2 序列） | 判定 |
|---|---|---|---|---|---|---|
| E1 | `InjectTypeValueResolver` 按类型注入 | 构造参数（持锁）/props（锁外） | **否**（未实现 collectDepends，IBeanPropValueResolver L31 默认空） | 构造参数路径=是 | C2-001 已立案：T1 持 BD(A)→`getBeanByType(B,true)`→BD(B)；T2 对称→BD⇄BD 互等 | 已立案 |
| E2 | `factoryBean` 边 | `BeanDefinition.newInstance` L534 `getBean(factoryBean, false)` | **否**（beanModel 属性，非 resolver） | 是 | C2-001 第二闭合面已立案：持 BD(A) 在 PBI(F) 上 wait；F 的 init 经 resolvedDepends 反取 A 时环闭合 | 已立案 |
| E3 | `supplier` 函数 | `newInstance` L527-528 `supplier.apply(container)` | **否**（任意函数，不可静态收集） | 是 | C2-001 F3 已列：supplier(A) 内 `container.getBean(B)`→BD(B)；supplier(B) 内 getBean(A)→BD(A)，两线程对称→BD⇄BD。闭合前提=supplier 双向互取（用户代码行为） | F3 已列，本轮补全闭合序列 |
| E4 | 构造参数 `ExpressionValueResolver`（XPL 表达式） | `getConstructorArgs` L517 `resolveValue` → `action.invoke(scope.getEvalScope())` | **否** | 是 | 表达式内未绑定变量经 `BeanContainerVariableScope.getValue`（L28 `container.getBean(name)`，includeCreating=**false**）→ 持 BD(A) 先请求 BD(B)（B 未创建时），或**持 BD(A) 在 PBI(B) 上 wait**（B 创建中）→ 与 C2-001 第二闭合面同形。闭合序列：T1 持 BD(A)、表达式引用 `b`→BD(B)/PBI(B)；T2 持 BD(B)、构造器 type-inject A→BD(A) | **C2-012（新）** |
| E5 | `ioc:ignore-depends` / `ioc:force-lazy-property` 显式逃逸边 | `InjectRefValueResolver.collectDepends` L66-71 `if (!ignoreDepends)` 才收集；构造参数路径 `resolveValue` L78 `getBean(ref, true)` | **否（设计内逃逸）** | 构造参数路径=是 | 与 E1 完全同形的 BD⇄BD：T1 持 BD(A)→构造参数 ref(B, ignoreDepends)→BD(B)；T2 持 BD(B)→ref(A, ignoreDepends)→BD(A)。拓扑校验与 concurrentStart 图（`asyncStartBeans` L553 依赖=resolvedDepends）同时失明 | **C2-012（新）** |
| E6 | AOP 拦截器 bean 边 | `BeanDefinition.addInterceptors` L680 `container.getBean(interceptorModel.getBean(), true, beanCtx)`；`AopBeanProcessor` L307-323 仅 `beanModel.addIocInterceptor`，从不写 depends/props | **否**（collectDepends L269-277 不遍历 ioc:interceptors） | 否（setupInstance L618 在 BD 锁外） | BD 环不闭合（锁外）；但拓扑序违反：I 可排在 A 之后→concurrentStart 下 A 任务与 I 任务并行，A 的 setupInstance 经 includeCreating=true 拿到**未 init 的 I**（getBean0 L433+getBeanInstance L744：includeCreating=true 跳过 checkBeanInitialized）；I 的 init 动作滞留 beanCtx 直到根调用 flushActions。PBI 域：I 的 init 用户代码 `getBean(A,false)`（I 声明依赖 A，拓扑可见）与 A 的 init 用户代码 `getBean(I,false)` 交叉→C2-002 环闭合入口 +1 | **C2-013（新）** |
| E7 | `@Inject` 集合注入（List/Map/Set/collect-beans） | `BeanDefinitionBuilder` L924-932（Map）、L965-967（List）：子项均为 `InjectRefValueResolver(id, false, ignoreDepends, def)` | **是**（委托 collectDepends 收集；仅 ignoreDepends=true 时随 E5 逃逸） | — | — | **验证安全**（任务猜测不成立；唯一残余随 E5） |
| E8 | `ListValueResolver`/`MapValueResolver`/`SetValueResolver`/`PropsValueResolver`/`ConcatValueResolver`/`BeanValueResolver` 委托 | 均实现 collectDepends 逐子项收集（L67-69 等） | 是 | — | — | 验证安全（第 1 轮已核，本轮复读确认） |
| E9 | XPL 全相位动态边（`BeanContainerVariableScope` 挂载于 BeanScopeImpl 共享 eval scope，`BeanScopeImpl` L38-43 构造时 `setExtension`） | `runXpl`（init L649 / destroy L716 / delay L726 / refresh L796）、`factoryMethod.invoke`（L535-537 传入 `scope.getEvalScope()`）、prototype 回退路径 L500 | **否**（任意 XPL 文本内未绑定变量→`getBean(name)`，includeCreating=false） | 构造参数表达式=是；init/delay 相位=否（但持 PBI owner 身份） | init 相位闭合：T1 为 PBI(A) INIT owner，init XPL 引用 `b`→`getBean(b)`（false）→PBI(B) 等待（B init owner=T2）；T2 为 PBI(B) INIT owner，init XPL 引用 `a`→PBI(A) 等待→**C2-002 环，入口+1**。destroy 相位：scope 已 closed→`checkClosed` fail-fast 抛 ALREADY_CLOSED（无挂死，CL-018） | **C2-012（新）/C2-002 家族入口** |
| E10 | 运行期再解析边（config listener 闭包捕获原 beanCtx） | `subscribeConfigChange` L770-788：listener=(p,vars)->`prop.assignToObject(bean,...,beanCtx)`，config 变更线程执行 resolveValue→`getBean(ref,true)`/`getBeanByType(type,true)` | **否**（运行时边，图无此概念） | 否（listener 在 DefaultConfigProvider/DefinitionConfigProvider 锁外触发） | 闭合：config 线程重解析→惰性 bean C 首建→`synchronized(BD(C))`→C 构造器 type-inject D；并发请求线程 T2 持 BD(D) type-inject C→BD⇄BD（C2-001 环的**新入口**：config-executor 线程）。放大：死锁发生在唯一 config 单线程上→全平台配置刷新停摆（CL-005 串联面） | **C2-012（新，入口枚举）** |
| E11 | refresh 级联边 | `onRefreshConfig` L798-802→`container.refreshConfig(beanId)` 级联 configDependants（构建期 `buildRefResolver` L1093-1096 登记） | 否（运行时边） | 否 | 无锁环；同线程深度递归风险→CL-017 | CL-017 |
| E12 | `XplValueResolver`（`ioc:xpl` 值） | `resolveValue` L31-34 仅返回 `EvalCode` 引用；实际求值在 convertTo/域处理（nop-xlang `XplStdDomainHandlers`，审计单元外引用，未核实求值时点） | 否 | 若在构造参数 convertTo（L518）内求值则=是 | 保守按「不可静态收集」并入 C2-001 F3 处理，不单独闭合（本轮不虚构求值时序） | F3 已列，附注 |

**①小结**：不可见边全集 = {E1（已立案）、E2（已立案）、E3（F3 已列）、E4/E5/E9/E10（**新立案 C2-012**）、E6（**新立案 C2-013**）、E11（CL-017）、E12（F3 附注）}；E7/E8 经实测**验证安全**。BD⇄BD 环闭合的充要条件不变：**两条互反不可见边均在 `newInstance`（持 BD 锁）内被求值**（E1/E2/E3/E4/E5 全部满足「构造参数路径持锁」）；BD→PBI 等待环闭合条件 = 持 BD 的构造期求值遇到 includeCreating=false 获取（E4/E9 变体）或 factoryBean/factoryMethod（E2，已立案）。

## ② C2-002 家族等待边矩阵（init/工厂/destroy 交叉获取站点穷举）

等待原语唯一：`ProducedBeanInstance.runUntil`→`waitQuietly`（L194-201）。等待环闭合要求「等 includeCreating=false 的 bean」且目标 PBI 处于他线程 owner 推进中。逐站点：

| 阶段（owner 身份） | 站点 | includeCreating | 可等待 PBI？ | 环闭合形态 |
|---|---|---|---|---|
| CREATE（持 BD(X)） | 构造参数 InjectRef/InjectType（L78/L53，true） | true | 否（L744 跳过等待） | 无 PBI 等待；BD 链→C2-001 |
| CREATE（持 BD(X)） | factoryBean（L534，false）/工厂方法内用户代码 `getBean(name,false)`/表达式 E4 | **false** | **是** | **持 BD(X) 等 PBI(Y)**：T1 持 BD(A) 在 PBI(B) wait；T2 为 PBI(B) INIT owner、initBean(B) 的 resolvedDepends ∋ A（可见边即可）→ `getBean(A,false)` → A 已 scope.add？否（T1 在 newInstance 中）→ `synchronized(BD(A))` 阻塞 → **T2 等 BD(A)、T1 等 PBI(B)**，环闭合。B 侧无需任何不可见边——比第 1 轮 F3 记载的「F 的 init 经 resolvedDepends 反取 A」更弱前提即闭合（E2 已立案面的完整序列，本轮给出逐步交错） |
| PROP_SET 推进前（setupInstance，锁外） | props 注入（L601）/addInterceptors（L618） | true | 否 | includeCreating=true 拿未初始化 bean（by-design）；BD 链 |
| INIT（PBI(X) initThread=owner） | `initBean` resolvedDepends 循环（L642-644，false） | **false** | **是** | 声明边拓扑无环→单独不闭合（第 1 轮已证）；与任何「另一侧 init 用户代码反取」组合即闭合=C2-002 本体 |
| INIT | `initMethod.invoke`（L647）用户代码 `getBean/getBeanByType/getBeansOfType/getBeansWithAnnotation/injectTo`（均为 public two-arg→includeCreating=false） | **false** | **是** | C2-002 本体（已立案）；getBeansOfType 扇出 N 条不可见等待边 |
| INIT | `runXpl(ioc:init)`（L649）变量解析（E9） | **false** | **是** | C2-002 家族入口+1（C2-012 E9 闭合序列） |
| INIT | `subscribeConfigChange`（L651-652） | — | 否（SUB 锁短临界区，无等待） | 无环 |
| INIT | `beanMethod.call0`（L655，producer 方法）用户代码 | **false** | **是** | 同 initMethod（C2-002 同族） |
| LAZY（`runLazyProperties` L730-740） | `prop.assignToObject`→resolveValue | true | 否 | 无 PBI 等待；BD 链（C2-001 入口） |
| DELAY（`runDelayMethod` L720-728） | `delayMethod`/`runXpl` 用户代码 `getBean(name,false)` | **false** | **是** | 交叉仅对 INITIALIZED 以下阶段构成等待；DELAY 晚于对方 INIT 完成→不闭合（本轮验证：`getBean(A,false)` 只等 INITIALIZED，不等 LAZY/DELAY→DELAY→INIT 方向无环） |
| DESTROY（scope.close→remove→destroyBean L704-718） | `destroyMethod.call0`（L715）/`runXpl(destroy)`（L716）用户代码 `getBean` | false | 形式上是 | `BeanScopeImpl.get`→`checkClosed`（closed 先置位）→**ALREADY_CLOSED fail-fast，无挂死**（CL-018）；对 prototype（beanScope=null）则在停机期新建实例（C6 面） |

**②小结**：新等待边站点 0（全部并入已立案 C2-002/C2-001 面或验证无环）；**新增环闭合强化**：CREATE 阶段「持 BD + factoryMethod 用户代码 `getBean(name,false)`」以**单侧不可见边**闭合 BD⇄PBI 混合环（E2/E4 变体，序列见上表第 2 行）——比第 1 轮记载的 factoryBean 专用路径覆盖面更宽（任何在工厂方法内解析 bean 的用户代码均触发）。

## ③ 锁内用户代码执行站点表（锁内 I/O 面修复直接输入）

锁载体复扫：`grep -E "synchronized|ReentrantLock|ReadWriteLock|Collections\.synchronized"` 命中 48 行，其中 4 行为注释（BeanDefinition L83/L640），**代码级锁载体集合与第 1 轮 44 站点一致，无新增载体**。临界区内用户代码站点：

| 站点 | 锁 | 用户代码类型 | 长临界区风险 | 判定 |
|---|---|---|---|---|
| `BeanContainerImpl.getBean0` L406-425 → `BeanDefinition.createInstance` L546-585 → `newInstance` L526-540：`supplier.apply`（L528）、`getConstructorArgs` L517 resolver 求值（ExpressionValueResolver 直接执行 XPL）、`factoryBean` 获取（L534）、`factoryMethod.invoke`（L535）/`constructor.invoke`（L539） | BD(X) monitor | **bean 构造器/工厂方法/supplier 全量用户代码**（可任意 I/O：DB 连接、HTTP、类加载、文件） | **高**——单 bean 构造耗时被放大为该 beanDef 全局排队；且求值产生不可见依赖边（①） | C2-001 已立案本体+放大面；结构性修复需早暴露（spring 式）或改可超时锁 |
| 同上 L518 `convertTo`（xpl 域参数在构造期求值，经 nop-xlang 域处理器） | BD(X) | XPL 求值 | 中 | 随上案 |
| 同上 L570 `scope.add` → `BeanScopeImpl.add` L74-79 `synchronized(beans)` | BD(X)→SCOPE 嵌套 | 无用户代码 | 短 | 合规（嵌套序 BD→SCOPE 固定，无反向站点，复扫确认 SCOPE 内无 getBean0 调用） |
| 同上 L582 `LOG.error`（createInstance catch） | BD(X) | 日志 appender I/O | 低-中（异步 appender 下短；同步 appender 下文件 I/O） | P3 记录：建议错误日志移出锁外或标注 |
| `VfsPluginDefinition.activate` L381-423 → `activateInternal` L460-505：`BeanContainerBuilder.build`（解析 beans 模型）+ `container.start()`（**全部 eager bean 构造+init 用户代码**）+ `activator.activate(scope, config)`（**插件 activator 全量用户代码**，可 I/O/回调 manager API） | LC(P) lifecycleLock（reconcile 路径外层还持 RLOCK） | 插件激活全链 | **极高**——RLOCK 持有期间串行执行 N 个插件的容器启动；activator 调 manager API 形成 LC→BIN/RLOCK 反向边（C2-014） | 「单飞」设计声明内（第 1 轮矩阵），本轮登记入表并立案 C2-014 的环闭合序列 |
| `VfsPluginDefinition.doDeactivate` L435-458：`scope.close()`（LIFO **全部用户 disposer**）→`container.stop()`（**全部 bean destroyMethod 用户代码**） | LC(P)（reconcile 路径外层 RLOCK） | 插件去激活全链 | 高 | 同上；`rollbackActivation` L507-522 同形 |
| `AbstractPlugin.activate` L172-202 → `buildBeanContainer(true)` L214-234：`CoreInitialization.initialize()`（全平台初始化）+ `AppBeanContainerLoader.loadFromResource`（解析+构建）+ `container.start()` | LC(P) | 平台初始化+容器启动 | **极高** | C2-004 同站（状态转换面）；用户代码面本轮入表 |
| `AbstractPlugin.doDeactivate` L257-274：`c.stop()` + `CoreInitialization.destroy()` | LC(P) | 全平台销毁 | 极高 | 同上 |
| `PluginManagerImpl.reconcile` L227-243 `synchronized(reconcileLock)` → `doReconcile` → `activateIfInactive`（L387-400→def.activate()→LC+容器启动）与 `deactivateIfActive`（L402-413→`FutureHelper.syncGet(def.deactivate())`：futureCall **同线程内联**（api-core FutureHelper L122-129 实测），即当前线程直接执行 doDeactivate 全链） | RLOCK | 多插件激活/去激活串联 | **极高**（最重临界区：一次 reconcile 可能启动/停止全部插件容器） | C2-006 已立案矩阵面；本轮补充：临界区内含 deactivate 内联执行（非跨线程等待，复核维持第 1 轮判定） |
| `PluginManagerImpl.reloadPlugin` L639-687：`syncGet(oldDef.deactivate())`（内联全链）+ `loadVfsDefinition`（VFS 解析+XML 解析）+ map 三连 + `reconcile()`（重入） | RLOCK | I/O（VFS/XML）+ 生命周期 | 高 | C2-006 面；VFS/XML 解析在锁内=锁内 I/O 直接证据 |
| `PluginManagerImpl.loadPluginFromVfs` L493-499 computeIfAbsent：`loadVfsDefinition`（VFS 读取+DSL 解析）+ `plugin.load`+订阅 | BIN（CHM bin） | I/O | 中 | C2-005 已立案（jar 轨最重） |
| `DefinitionConfigProvider` mergedView 站点（L73-87 applyMergedView diff、L99-102 snapshot、L104-108 containsKey、L140-149 setDefinitionValue、L164-181 getConfigReference、L216-227 getConfigValueForPrefix+setIn、L268-281 getConfigValue） | MV monitor | 无用户代码（纯 map 操作+值 diff `Objects.equals`） | 低（O(view) 但无回调；notifyListeners 全部在锁外 L88-92/L150，复核通过） | 合规 |
| `PluginScopeImpl.effect` 返回闭包 L53-59：`d.dispose()` 在 effects 锁内 | FX | 用户 disposer | 中 | C2-010 已立案（同族不对称唯一漏改点，复核维持） |
| `ProducedBeanInstance` ×9 站点 | PBI monitor | 无用户代码（状态推进/登记）；阶段回调一律锁外（L148-168） | 短 | 合规（类注释 L23-24 明文纪律，维持第 1 轮判定） |
| `BeanCreationContext` ×7 站点 | ctx monitor | 无（快照/drain；runActions 锁外 L118-122） | 短 | 合规 |
| `BeanScopeImpl.add/close` L74/99 `synchronized(beans)`：close 的 remove→`container.destroyBean`→**destroyMethod 用户代码在 beans 锁外**（close L102-112 先出锁再遍历） | SCOPE | 无（add）/destroy 在锁外 | 短 | 合规（第 1 轮已闭环） |
| `ChangeSubscription` ×3（L28/32/36） | SUB(p) | 监听器快照/增删 | 短 | 合规（trigger 持快照锁外迭代，本轮复读 ChangeSubscriptions.trigger L66-90 确认） |
| `DynamicConfigSource.setConfigVars` L38 | this monitor | volatile 写 | 短 | 合规（冗余无害，维持） |
| `JdbcConfigSource.refreshConfig/tryTriggerChange` L71/100 | this monitor | JDBC 查询 | 中（定时器单线程自串行） | 维持第 1 轮判定（close 交互归 C1 族不重复） |
| `Trie` RRWL、`ShellRunner` L236 | TRW/WLOCK | makeNode/`builder.start()` | 短/文档化 workaround | 维持合规 |

**③小结**：锁内用户代码重灾区排序 = RLOCK（reconcile 全链）≥ LC(P)（activate/deactivate 全链）> BIN（jar 轨下载+start）> BD(X)（单 bean 构造）> 其余。该表为「锁内 I/O 面」修复的直接输入：结构性修复优先级 RLOCK/LC 拆分（reconcile 快照外移、activate 两阶段）> BIN 映射瘦身（C2-005 已立案）> BD 临界区（C2-001 已立案）。

## ④ Condition/wait 面判定表

框架层 `wait(/notify` 载体 grep：**唯一 Object.wait 站点 = ProducedBeanInstance.waitQuietly（L194-201）**（notifyAll ×4：L95/107/156/167）。`await(` 无原生站点；阻塞等待全部经 `FutureHelper.syncGet`（8 处，逐站判定；futureCall 实测为同线程内联 api-core FutureHelper L122-129）：

| 站点 | 等待对象 | 跨线程？ | 丢失唤醒 | 虚假唤醒 | 超时 | 判定 |
|---|---|---|---|---|---|---|
| `ProducedBeanInstance.waitQuietly` L196 | PBI 条件（status/initThread/initError） | 是 | 否（四转换点全部 notifyAll；owner 异常路径 L152-158 广播）——残余缺口=C2-009（Error 于 markPropSet 前逃逸无广播） | 否（waitQuietly 处于 `while(true)` 重查循环 L118-146） | **无** | C2-002/C2-009 已立案（本轮复核维持） |
| `BeanContainerImpl.awaitStartFinished` L538 `syncGet(startFuture)` | 容器异步启动 graph | **是**（globalWorker 执行） | —（CompletableFuture） | — | **无** | C2-003 F3 已列；本轮复核：startFuture 非 volatile（L90）+ 无超时，维持 C2-003 判定 |
| `PluginManagerImpl` L180/L407/L659、`VfsPluginDefinition` L568、`AbstractPlugin` L342 `syncGet(...deactivate())` | futureCall 包装 | **否（同线程内联）** | — | — | 不适用 | **非等待点**（实测 futureCall 内联执行后返回已完成 promise）；第 1 轮判定维持 |
| `VfsPluginDefinition.invokeCommand` L615、`AbstractPlugin.invokeCommand` L424 `syncGet(invokeCommandAsync(...))` | **IPluginCommand 用户实现返回的 CompletionStage** | **是**（命令实现可分派到任意执行器） | — | — | **无** | **新登记（C2-017）**：公共同步 API 对用户异步实现无限期阻塞、无超时声明、不可配置；阻塞发生在调用线程（可中断，CompletableFuture.get 响应中断），非挂死但违反 F5「无限期等待点必须有超时」 |
| api-core `ContextTaskQueue.syncGet`（对照，不立案） | context 任务队列 | 是 | — | — | 无 | api-core 越界，仅对照记录 |

**④小结**：同族穷举完成——超时缺失点全集 = {PBI.waitQuietly（C2-002/C2-009）、awaitStartFinished syncGet（C2-003）、invokeCommand syncGet（**新 C2-017**）}；其余 6 处 syncGet 实测同线程内联，非等待点。无未判定等待原语残留。

## ⑤ 锁契约缺失面

1. **`IBeanContainerImplementor.getBean(String, boolean includeCreating, BeanCreationContext)` / `getBeanByType(...)`（L46-48）零 javadoc**：`includeCreating=true` 的实际语义是「可能返回**未完成属性赋值与 init** 的半成品实例」（getBean0 L376-387 + getBeanInstance L744 跳过 checkBeanInitialized），`false` 的语义是「阻塞等待 INITIALIZED（无超时，撞环即永久 wait）」。该二元开关是全框架最核心的同步契约，接口与实现类均无声明；`InjectRefValueResolver`/`InjectTypeValueResolver` 硬编码 true（L78/L53），`initBean` 依赖循环硬编码 false（L643）——调用方 unknowingly 拿半成品或 unknowingly 进入无界等待。**新立案 C2-017（契约面）**。
2. **public 无锁 vs 私有锁内不对称（复核汇总，均已在前轮立案，本轮 F3 互引）**：`VfsPluginDefinition.updateConfig/unload/start/stop`（C2-004 ×5）；`PluginManagerImpl.loadPlugin/unloadPlugin/activatePlugin/deactivatePlugin`（C2-006）；`DefaultConfigProvider.applyChange/changeConfigSource`（C2-008）；`BeanContainerImpl.start/stop/restart/awaitStartFinished`（C2-003）。本轮新增不对称点：`DefinitionConfigProvider.updateConfigValue`（L201-207）先 `containsKey`（锁1）后 `setDefinitionValue`（锁2）——两段独立临界区拼出的 check-then-act，同文件其余复合操作（applyMergedView）均为单临界区——**新立案 C2-016**。
3. **StaticObjectFactory：本仓库不存在**（全仓 grep 无命中）；容器内部类同步契约 javadoc 现状 = `ProducedBeanInstance`（L23-24 有明文锁纪律声明，合规）、`BeanCreationContext`（L37-40 flushActions 契约声明，合规）、`BeanScopeImpl`（add/close 注释声明原子化理由，合规）、`BeanScopeContextImpl`（**无任何并发声明**——其 `scopes` 为 CHM 且按 request context 隔离，单上下文单线程访问，实测无站点竞争，仅记录）、`DelegateBeanContainer`（纯转发无状态，无需声明）。javadoc 缺失面收敛为 C2-017 的接口契约项。
4. **`DelegateInvocationHandler.handler` 可见性契约缺失**（字段无 volatile、读写无共同锁）：写侧 `setHandler`（经 PBI monitor，ProducedBeanInstance L224-229 synchronized），读侧代理 `invoke`（L38-42）不取任何锁——无 happens-before 通道；后果为创建窗口内 `getBean(x,true)` 获取者调用代理抛 `IllegalStateException("bean-proxy-not-initialized")`（fail-fast，非静默错值），理论上可见性延迟无界（实践中经后续 getBean 的 monitor 通道收敛）。**新立案 C2-015**。

---

## 第 2 轮（深挖）新增发现

### [nop-core-framework-C2-012] 依赖边不可见族失效面扩展：ignore-depends 显式逃逸边、表达式变量域动态边、运行期再解析入口——C2-001 闭合前提的单侧化与新入口枚举

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/resolvers/InjectRefValueResolver.java:66-71,78`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerVariableScope.java:28`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanScopeImpl.java:38-43`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:491-509,770-788`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/resolvers/ExpressionValueResolver.java:33-35`
- **证据片段**:
  ```java
  // InjectRefValueResolver L66-71 —— ignoreDepends 时依赖对拓扑图显式失明
  public void collectDepends(Set<String> depends) {
      if (!ignoreDepends) {
          depends.add(ref);
      }
  }
  // L78 —— 构造参数路径仍持 BD 锁执行获取
  return container.getBean(ref, true, beanCtx);
  // BeanContainerVariableScope L28 —— 任意 XPL 未绑定变量 → bean 获取（includeCreating=false）
  public Object getValue(String name) { return container.getBean(name); }
  ```
- **严重程度**: P2
- **并发场景**: 与 C2-001 相同的并发首触面（concurrentStart worker 线程组/运行期两请求线程），但触发配置更宽：`ioc:ignore-depends` 属性、`ioc:force-lazy-property` 引用（构造器将 ignoreDepends 置 true）、构造参数 XPL 表达式、init/delay XPL 文本中的裸变量引用、config 变更监听器触发的属性重解析。
- **触发时序**: (a) ignore-depends BD⇄BD：bean A 构造参数 `<ref bean="B" ioc:ignore-depends="true"/>`，bean B 构造参数对称引用 A。T1 `getBean(A)`→持 BD(A)→resolveValue→`getBean(B,true)`→BD(B) 阻塞；T2 对称→BD(A) 阻塞。拓扑校验（sortBeans L224）与 concurrentStart 图（asyncStartBeans L553 只依赖 resolvedDepends）对该边双双失明，构建期无任何拒绝。同线程路径由 `isInCreationByCurrentThread`（L412）fail-fast，顺序启动测试必绿。(b) 表达式单侧闭合（比 C2-001 双侧不可见边更弱前提）：A 构造参数表达式 `@expr:b` → `BeanContainerVariableScope.getValue` → `getBean("b")`（includeCreating=**false**）→ 若 B 尚未创建则持 BD(A) 请求 BD(B)；若 B 创建中则**持 BD(A) 在 PBI(B) 上无限期 wait**——T2 只需正在推进 B 的 INIT 且 B 的 resolvedDepends ∋ A（完全可见的声明边）即可闭合 BD⇄PBI 混合环。(c) 运行期入口：config 变更监听器闭包（subscribeConfigChange L770-788 捕获原 beanCtx）在 config-executor 单线程上重执行 `prop.assignToObject`→惰性 bean 首建→BD 锁链——若与请求线程在互反不可见边两端相遇，死锁发生在唯一 config 线程上，全平台配置刷新随之停摆。
- **现状**: E1/E2（C2-001 已立案）之外，本轮实测新增 4 条不可见边（E4/E5/E9/E10）全部满足「构造参数路径持 BD 锁求值」或「运行期新入口」；`@Inject` 集合注入（任务猜测项）经 BeanDefinitionBuilder L924-932/L965-967 实测**已收集、验证安全**。
- **风险**: 开发者用 `ignore-depends` 解决启动顺序问题的每一处配置，都是 C2-001 死锁面的显式订阅；表达式动态引用完全不可审计；config 线程死锁放大为平台级配置通道瘫痪。三者的共同根因是「不可见边无兜底守卫」——C2-001 建议的 tryLock 兜底可一并覆盖，但 ignore-depends 还需构建期显式确认（当前静默通过）。
- **建议**: (1) `ignore-depends` 构造参数路径：构建期记录逃逸边清单，若两端 bean 均非 lazy-init 则告警或拒绝（逃逸语义应仅用于懒引用）；(2) 表达式/变量域动态获取：在 `BeanContainerVariableScope.getValue` 文档钉死「构造期调用持 beanDef 锁」契约，构造参数路径的表达式求值改为锁外预解析或在 `getBean0` 的 tryLock 兜底下运行；(3) config 监听器重解析入口：重绑定失败/阻塞时打点告警（config 单线程被挂死无任何自愈信号）。
- **失效面枚举**: 本案失效边全集 = {E4 构造参数 ExpressionValueResolver（持锁，BD⇄BD/BD→PBI 双形态）、E5 ignore-depends/force-lazy-property ref（持锁，BD⇄BD）、E9 XPL 相位文本变量解析（init/delay 持 PBI owner 身份→C2-002 入口；destroy 相位 ALREADY_CLOSED fail-fast 无环）、E10 config 监听器重解析（运行期 BD 链入口，config 单线程放大）}；验证安全集 = {E7 集合注入、E8 委托 resolver（collectDepends 全实现）}；附注 = E12 XplValueResolver 求值时点未核实不闭合。入口全集 = {concurrentStart worker、HTTP worker ×2、config-executor 单线程}。
- **信心水平**: 确定（逐边代码事实+闭合序列逐步写出；E10 的 config 线程死锁放大以单线程 executor 架构为代码事实）
- **误报排除**: 非 C2-001 重复——C2-001 F3 仅列「ExpressionValueResolver/XplValueResolver 同 supplier 处理」一句，未给出闭合条件、未覆盖 ignore-depends 显式逃逸（其 F3 依赖边全集不含该项）与运行期入口；本案为该族失效面的补全立案，合并裁定阶段可与 C2-001 按同族相邻处理。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P2（维持；指派抽验 2 边通过）**。抽验边 1（E5 ignore-depends/force-lazy-property 构造参数路径）：InjectRefValueResolver L32 `ignoreDepends = ignoreDepends || resolvedBean.isIocForceLazyProperty()`、L66-70 collectDepends 条件跳过、L78 `getBean(ref, true, beanCtx)` 构造参数路径持 BD 锁求值——三点与 live code 一致。抽验边 2（E10 config 监听器重解析入口）：subscribeConfigChange L774-776 listener 闭包 `prop.assignToObject(bean, ..., beanCtx)` → resolveValue → getBean/getBeanByType(true) → 惰性 bean 首建 BD 锁链，监听器经 DefaultConfigProvider.applyChange → subscriptions.trigger 在 config-executor 单线程执行（SingleThreadConfigExecutor poolSize=1，L18 核实）——与 live code 一致。E4 佐证：ExpressionValueResolver L32-34 `action.invoke(scope.getEvalScope())` → BeanContainerVariableScope L28 `getBean(name)`（includeCreating=false），BD⇄BD / BD→PBI 双形态成立。E7 集合注入「验证安全」复核属实（BeanDefinitionBuilder collect-beans 均构造 InjectRefValueResolver，L924-932/L965-967）。附注：ignore-depends 逃逸边的「构建期静默通过」在 allow-cycle 默认 true（IocConfigs L64-66）下与 C2-001 同源放大，构建期告警建议应与 allow-cycle 裁定合并。现状 = 新发现，未修复。

### [nop-core-framework-C2-013] AOP 拦截器 bean 依赖边不入拓扑图：collectDepends 不遍历 ioc:interceptors，concurrentStart 下产生未初始化拦截器注入与拓扑序违反

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:269-277,675-686`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/AopBeanProcessor.java:301-325`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:376-387,433`
- **证据片段**:
  ```java
  // BeanDefinition.collectDepends L269-277 —— 只遍历 constructorArgs + props
  public void collectDepends(Set<String> deps) {
      for (IBeanPropValueResolver resolver : constructorArgs) { resolver.collectDepends(deps); }
      for (BeanProperty prop : props.values()) { prop.getValueResolver().collectDepends(deps); }
  }
  // addInterceptors L680 —— 拦截器 bean 在 setupInstance（BD 锁外）按需创建
  IMethodInterceptor interceptor = (IMethodInterceptor) container.getBean(interceptorModel.getBean(), true, beanCtx);
  // AopBeanProcessor L323 —— 构建期仅登记模型，从不写 depends/props
  beanModel.addIocInterceptor(model);
  ```
- **严重程度**: P2
- **并发场景**: `nop.ioc.app-beans-container.concurrent-start=true` 时 graph worker 并行创建「被拦截 bean A」与「拦截器 bean I」（二者间唯一依赖边 = A 需 I 已创建，且该边不入图）；或运行期两线程并发首触。
- **触发时序**: (a) 拓扑序违反：图无 A→I 边，I 可排在 A 之后（beanTopoIndex(I) > beanTopoIndex(A)）。T1 创建 A：createInstance 完成（scope.add）→ setupInstance L618 addInterceptors → `getBean(I, true)` → includeCreating=true 语义（getBeanInstance L744：跳过 flushInit/checkBeanInitialized）→ 若 T2 正在创建 I（在 scope、status<PROPERTY_SET），T1 **立即拿到未完成属性赋值/未 init 的拦截器实例**并装配进 `$$aop_interceptors`；I 的 init 动作滞留 A 的 beanCtx，直到最外层 `flushActions`（L308）才补跑——窗口内 A 的代理方法调用触达半成品拦截器。(b) PBI 域：I 的 init 用户代码经 `getBean(A,false)`（I 声明依赖 A 的可见边）与 A 的 init 用户代码 `getBean(I,false)` 交叉时按 C2-002 序列闭合等待环——本案为该族贡献一个此前未枚举的交叉获取入口。
- **现状**: 拦截器边在 BD 锁外执行（setupInstance），不构成 BD monitor 环；构建期 AopBeanProcessor 已解析出拦截器 BeanDefinition（getInterceptor L324-338），收集该边无技术障碍但未实现。
- **风险**: 每个被 AOP 拦截的 bean（平台高频形态）都携带此不可见边；concurrentStart 下拦截器初始化顺序失约——拦截器若依赖 init 期状态（如配置缓存、连接），业务在窗口内得到未定义行为且无报错；该窗口无任何测试保护。
- **建议**: (1) `BeanDefinition.collectDepends` 补遍历 `beanModel.getIocInterceptors()`（拦截器 bean id 已在构建期解析，直接加入 deps）——使 sortBeans 校验、fillResolvedDepends、concurrentStart 图三处同时看见该边，I 被强制排在 A 之前；(2) `addInterceptors` 的获取改为 `getBean(interceptor, false, beanCtx)`（等待完整初始化）或在装配前显式 `checkBeanInitialized`；(3) 兜底测试：concurrentStart + 拦截器 init 计数断言。
- **失效面枚举**: 受影响站点全集 = {`addInterceptors` L680（唯一获取点）、`BeanDefinition.collectDepends` L269（收集缺口）、`AopBeanProcessor` L307-323（登记点）}；后果面 = {未初始化拦截器注入（includeCreating=true 语义放大）、拓扑序违反、C2-002 交叉获取入口 +1}；不受影响面 = {非拦截 bean 的 props 注入（E8 已收集）、`ioc:proxy` 代理创建（createProxy 无 bean 依赖）}。
- **信心水平**: 确定（collectDepends 遍历范围、addIocInterceptor 不写 depends、includeCreating=true 跳过等待均为代码事实；交错序列可逐步写出）
- **误报排除**: 非includeCreating 语义误报——includeCreating=true 的「拿半成品」在 props 注入场景是有意的循环依赖缓解，但拦截器边本可入图消除该需要；非 C2-002 重复——本案立案本体是依赖边收集缺口（构建期可修），PBI 交叉仅为后果之一。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P2（维持；与 C1 复核口径自洽性澄清）**。代码事实亲核：collectDepends 只遍历 constructorArgs + props（L269-277），不遍历 beanModel.getIocInterceptors()；addInterceptors L680 `getBean(interceptorModel.getBean(), true, beanCtx)` 在 setupInstance（BD 锁外，L618）执行；AopBeanProcessor 仅 addIocInterceptor 登记模型（L307-323），从不写 depends/props。**自洽性论证（对 C1-003 驳回）**：拦截器 bean 为普通 bean（beanMethod==null、非 ioc:proxy）时 getBeanInstance L744 条件 `!includeCreating || (getBeanMethod()!=null && !onlyProducer)` = false → **跳过 checkBeanInitialized** → 半成品拦截器注入可达；C1 复核驳回 C1-003 的两条依据——(i) beanMethod==null 代理分支 handler 自构造非 null（BeanDefinition L558）、(ii) beanMethod!=null 时 L744 条件为真 checkBeanInitialized 照常执行——均针对**代理 bean 的 handler nullability**，与本案**普通拦截器 bean 的半成品注入**分属不同机制域：两裁定分别成立、无矛盾。P2 维持（触发需 concurrentStart=true 或并发首触；后果为窗口期未定义行为，同实例自愈）。现状 = 新发现，未修复。

### [nop-core-framework-C2-014] 插件生命周期锁与插件注册表 bin 锁的跨线程互等环：jar 轨 computeIfAbsent 映射内 plugin.start→activate 持 LC，lifecycleLock 内 activator 用户代码回调 loadPlugin 请求 BIN——LC(P)⇄BIN(x) 闭合序列具体化

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/PluginManagerImpl.java:492-500,567-596`；`nop-core-framework/nop-plugin/nop-plugin-support/src/main/java/io/nop/plugin/support/AbstractPlugin.java:172-202,214-234`；`nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/VfsPluginDefinition.java:380-424,486-504`
- **证据片段**:
  ```java
  // PluginManagerImpl.loadPluginFromJar L569 —— BIN(bin(x)) 内执行 plugin.start（用户代码）
  return plugins.computeIfAbsent(id, k -> {
      ...
      plugin.start(coords.getGroupId(), ...);   // → AbstractPlugin.activate() → synchronized(lifecycleLock)
      return new PluginHolder(plugin, classLoader);
  }).plugin;
  // VfsPluginDefinition.activateInternal L492-503 —— LC(P) 内 activator 用户代码可回调 manager API
  Object activatorBean = container.getBean(activatorName);
  Disposable returned = ((IPluginActivator) activatorBean).activate(scope, config);
  ```
- **严重程度**: P2
- **并发场景**: 插件 activator（在 LC(P) 内执行，VFS 轨经 activateInternal L499，jar 轨经子容器 bean init）回调 `PluginManager.loadPlugin/activatePlugin`——jar 轨插件无 requires 门控，activator 自行加载依赖插件是 manager 公开 API 的正常用法；同时另一线程并发加载同 bin 的其他插件。
- **触发时序**: T1：reconcile 线程（持 RLOCK）→ `activateIfInactive(P)` → `def.activate()` 持 **LC(P)** → 子容器启动 → activator 用户代码调用 `pluginManager.loadPlugin(x)` → `loadPluginFromJar` → `plugins.computeIfAbsent(x, ...)` 请求 **BIN(x)**，阻塞。T2：另一线程 `loadPluginFromJar(x)` 已持 **BIN(x)** → 映射函数执行 `plugin.start(x)` → `AbstractPlugin.activate()` → 用户 activator/子容器 bean 代码调用 `activatePlugin(P)` 或任何会触发 `def(P).activate()` 的 manager 入口 → 请求 **LC(P)**，阻塞。T1 持 LC(P) 等 BIN(x)、T2 持 BIN(x) 等 LC(P)——**互等死锁**，synchronized 原语无超时不可中断；RLOCK 在 T1 外层使 reconcile 全部入口一并排队。第 1 轮 C2-005 仅记载同线程递归（`Recursive update`）与「跨线程组合仍可构造互等」的方向性提示，本轮给出具体闭合序列与双方持有/等待集。
- **现状**: 锁序图（第 1 轮）已有「BIN→任意」与「LC(P)→任意」两条无界出边，但未登记二者互反的环闭合形态；manager 对「activator 内回调 manager API」无重入检测、无锁序声明、无 javadoc 警告。
- **风险**: 触发依赖插件用户代码调用 manager API，但该调用模式是 jar 轨插件组装依赖的唯一手段（jar 轨不参与 requires/reconcile）；死锁后 RLOCK/BIN 两个全局点同时挂死——后续一切插件加载、reconcile、配置驱动的级联激活全部永久排队，且 config-executor 线程（subscribeGlobalConfig→reconcile，CL-005 串联面）亦被吸收，故障面扩散至配置刷新。
- **建议**: (1) 结构性：`loadPluginFromJar` 映射函数瘦身（C2-005 建议的双检+锁外 start+putIfAbsent 回写）——BIN 不再跨越用户代码，环自然断开（**首选**，与 C2-005 修复重合）；(2) `AbstractPlugin.activate`/`VfsPluginDefinition.activate` 的 javadoc 钉死「activator 内禁止同步调用 PluginManager 生命周期 API」，manager 侧对「持插件锁的 activate 调用」加线程标记检测并 fail-fast 报错；(3) 长期：插件激活改为两阶段（容器构建/激活与 activator 执行分离，activator 在全部锁外执行）。
- **失效面枚举**: 环闭合入口全集 = {VFS 轨：activateInternal L499 activator.run→manager API（LC(P)→任意）、jar 轨：BIN 映射内 plugin.start→activate→LC(x)（BIN→LC，C2-005 F3 已登记出边）}；manager API 反向边全集 = {loadPlugin（→BIN+尾部 reconcile→RLOCK）、activatePlugin/deactivatePlugin（→LC）、reloadPlugin（→RLOCK）、unloadPlugin（→LC 经 plugin.unload 守卫）}；同线程路径 = reconcileLock 可重入 + futureCall 内联，均无死锁（实测复核）。
- **信心水平**: 很可能（双方持有/等待集与交错序列为代码事实；触发需插件 activator 调用 manager API 的具体用户代码，未观测现场）
- **误报排除**: 非 C2-005 重复——C2-005 立案本体是 bin 锁长临界区与同线程递归；本案是新环闭合（LC⇄BIN 跨线程互等）及其触达 manager 公开契约的具体路径，修复与 C2-005 首选方案重合，合并裁定阶段按同站相邻处理。非 C2-006 重复——RLOCK 仅作为放大面出现，矩阵缺口本体不重复计数。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**驳回（环闭合第二腿证据不成立）**。逐分支亲核 loadPluginFromJar 映射函数（L569-596）：(1) stateMachineAware 插件（AbstractPlugin.isStateMachineAware 硬编码 true，DefaultPlugin 无覆写——仓库内全部 AbstractPlugin 系插件均 aware）走 `plugin.load(config)` 分支——仅解析定义+置 LOADED，**无 activate、不持 LC**；(2) 非 aware 分支 `plugin.start(4-arg)` → 兼容路径 doStart → buildBeanContainer(**false**) 构建**不 start** 容器、不调用 activate——**不持 LC**；(3) VFS 轨映射函数（L493-499）仅 load + 名称索引 + 配置订阅——**不持 LC**。初审证据链「plugin.start(coords.getGroupId(), ...) // → AbstractPlugin.activate() → synchronized(lifecycleLock)」与实际分支不符：aware 轨的 start(4-arg)→activate 链仅存在于映射函数**之外**的显式 start 调用。故「BIN 持有者在映射函数内请求 LC(P)」的边在当前代码不存在，LC⇄BIN 跨线程互等环**不可闭合**，死锁本体驳回。残余真面：LC(P) 持有期间 activator 用户代码回调 manager API 形成对 BIN/RLOCK 的**阻塞**（非死锁）——该面已由 C2-005（BIN 长临界区）、C2-006（RLOCK 矩阵）与 ③ 表「LC→任意 出边」覆盖，随合并裁定，不独立立案。唯一理论替代路径（非 aware 插件于 CoreInitialization 未初始化时加载 → 映射函数内全平台初始化 → bean init 用户代码回调 manager API）需「插件先于平台初始化加载」的罕见前提，不足以支撑 P2 立案。

### [nop-core-framework-C2-015] ioc:proxy + ioc:bean-method 的代理 handler 回填数据竞争：includeCreating=true 获取者拿到 handler==null 代理，读写无 happens-before 通道，瞬态 IllegalStateException 无框架级契约

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/DelegateInvocationHandler.java:12-42`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:556-567,654-661`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/ProducedBeanInstance.java:224-229`
- **证据片段**:
  ```java
  // DelegateInvocationHandler —— handler 字段无 volatile；invoke 无锁读
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (handler == null)
          throw new IllegalStateException("nop.err.ioc.bean-proxy-not-initialized:" + method);
  // BeanDefinition.createInstance L563 —— 代理对象在 createInstance（scope.add 前）即发布
  producedBeanInstance.setBeanWithProxyHandler(createProxy(proxyHandler), proxyHandler);
  // BeanDefinition.initBean L657 —— 真实 handler 到 INIT 阶段才回填
  beanInstance.setHandler(((InvocationHandler) instance));
  ```
- **严重程度**: P3
- **并发场景**: bean 声明 `ioc:proxy`（无 beanMethod）或 `ioc:proxy`+`ioc:bean-method`；T2 经 `getBean(A, true)`（InjectRef/InjectType 注入语义）在 A 创建窗口内获取代理并调用其方法；T1 为创建线程，在 initBean 阶段才调用 setHandler。
- **触发时序**: T1 创建 A：createInstance 内代理已发布（scope.add 后对 includeCreating=true 获取者可见，handler=null）→ T2 获取代理并 invoke → 读 handler==null → 抛 ISE（fail-fast，非静默错值）→ T1 initBean 完成 setHandler 后窗口关闭。JMM 剖面：写侧 setHandler 经 PBI monitor（ProducedBeanInstance L224 synchronized），读侧 invoke 不取任何共同锁→无 happens-before；实践中 T2 后续任何经 `getBean()`（PBI monitor）的路径会建立可见性，理论上无限期 stale null 无监控器通道排除（仅理论，未构造实测）。
- **现状**: null 检查+明确错误消息是刻意的 fail-fast 设计；但「代理发布早于 handler 回填」的窗口、「includeCreating=true 获取者恰好落在窗口内」的组合、以及 handler 字段的可见性契约均无文档与测试。
- **风险**: 业务在启动期/热创建窗口偶发 `bean-proxy-not-initialized` ISE，堆栈不指向任何业务代码，排查成本高；无重试或等待语义（对照 includeCreating=false 路径会等待 INITIALIZED，行为不一致）。
- **建议**: (1) `handler` 字段补 volatile（最低成本闭合可见性）；(2) `InjectRefValueResolver`/`InjectTypeValueResolver` 对 `isIocProxy()` bean 改用 includeCreating=false 语义或获取后 `checkBeanInitialized`（消除窗口而非暴露窗口）；(3) 在 DelegateInvocationHandler javadoc 声明「null→非 null 单向迁移、短暂 ISE 为预期行为」契约。
- **失效面枚举**: 发布-回填站点全集 = {createInstance L558（setBean 直接代理，handler 已就绪——安全）、L562-563（setBeanWithProxyHandler，handler 延迟回填——**本案**）}；回填点全集 = {initBean L657（setHandler）}；读取面全集 = {代理 invoke（无锁）、`getBean()`（PBI monitor 通道，安全）}。
- 信心水平：确定（字段无 volatile、读写锁不对称、发布早于回填均为代码事实；后果为可自愈的瞬态失败故 P3）
- **误报排除**: 非「fail-fast 设计」误报——设计选择了 ISE 而非静默，但选择暴露窗口不等于窗口无并发缺陷（可见性缺失独立成立）；非 C2-002 族——无等待环，纯可见性/时序面。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**驳回（与 C1 复核驳回 C1-003 同口径）**。逐分支亲核：(1) isIocProxy && beanMethod==null（BeanDefinition L558）→ `setBean(createProxy(new DelegateInvocationHandler((InvocationHandler) bean)))`——handler 自构造起非 null，includeCreating=true 快速通道返回的代理不存在 null handler；(2) isIocProxy && beanMethod!=null（L562-563 空 handler 构造）→ getBeanInstance L744 条件 `!includeCreating || (getBeanMethod()!=null && !onlyProducer)` 在 includeCreating=true/onlyProducer=false 时为**真** → checkBeanInitialized 阻塞至 INITIALIZED（setHandler 于 initBean L657 先行完成，PBI monitor wait/notify 建立 HB）→ ISE 窗口经容器路径**不可达**；(3) onlyProducer=true 路径返回 getCreatedBean()（L749-753，非代理对象），不暴露 DelegateInvocationHandler。本案立案场景「T2 经 getBean(A,true) 读到 handler==null 代理」与 C1 复核驳回的场景为同一机制面——按复核规则「引用不存在的可达路径 → 驳回」。残余 = handler 字段非 volatile 的纯理论 JMM stale（初审自认「仅理论，未构造实测」，且无监控器通道外的具体发布序列），无替代证据，不构成独立缺陷；javadoc 契约声明建议归入 C2-017 契约面随行处理。

### [nop-core-framework-C2-016] DefinitionConfigProvider.updateConfigValue 两段锁 check-then-act：containsKey 与 setDefinitionValue 之间合并视图可被并发摘除，配置键复活

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/DefinitionConfigProvider.java:200-213,71-93,247-265`
- **证据片段**:
  ```java
  public <T> void updateConfigValue(IConfigReference<T> ref, T value) {
      if (containsKey(ref.getName())) {          // 锁1：mergedView.containsKey（L104-108）
          setDefinitionValue(ref.getName(), value); // 锁2：重新取锁 put（L140-151）
      } else {
          globalProvider.updateConfigValue(ref, value);
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: 插件子容器内 bean 的配置引用写入（`IConfigReference` setter 路径）与定义级配置热应用（`VfsPluginDefinition.updateConfig`→`provider.updateMergedView`→applyMergedView 摘除键）并发。
- **触发时序**: T1：`containsKey(K)` 为 true（K 在视图）→ 尚未进入 setDefinitionValue；T2：`updateMergedView(newView)` 不含 K → `mergedView.clear()+putAll` 摘除 K 并按 diff 触发「键移除」通知；T1：`setDefinitionValue(K, v)` 重新取锁 put → K **复活**——视图与定义级配置域真实状态分叉，后续读取方拿到已删除键的旧值，且无任何报错（F9 静默形态）。反向交错（containsKey=false→T2 加入 K→T1 走 globalProvider 分支）产生定义级/全局双写不一致，同窗。
- **现状**: 同文件的 applyMergedView（L73-87） demonstrates 正确形态——diff、clear、putAll 全部在**单一** mergedView 临界区内；updateConfigValue 是本类唯一把判定与写入拆到两个临界区的复合操作（assignConfigValue 无判定不受影响）。
- **风险**: 插件热重载 + bean 回写配置的组合场景下定义级配置域状态腐化；窗口为数条指令、触发概率低，但后果静默持久。
- **建议**: 单临界区化：`synchronized (mergedView) { if (mergedView.containsKey(name)) { mergedView.put(name, value); ... } else ... }`（或将 setDefinitionValue 改为返回布尔的重载，在锁内完成分支判定）；通知仍在锁外触发（维持 L150 纪律）。
- **失效面枚举**: 本类复合操作全集 = {applyMergedView（单临界区，合规）、setDefinitionValue（单临界区，合规）、updateConfigValue（**两段，本案**）、subscribeChange 清理闭包（C2-007 已立案，不重复计数）}；触发入口 = {`IConfigReference` 值写回（api-core DefaultConfigReference 委托）、`assignConfigValue`（无判定，安全）}。
- **信心水平**: 确定（两段锁结构为代码事实；交错序列可完整写出；触发概率低故 P3）
- **误报排除**: 非 C2-007 重复——C2-007 立案 subscribeChange 清理闭包的孤儿 list，本案是 updateConfigValue 的键复活，同文件不同协议；非 C2-008 重复——C2-008 是 DefaultConfigProvider.applyChange 无锁复合，本案是锁内两段拆分。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P3（维持）**。updateConfigValue 两段锁（containsKey L104-108 锁1 + setDefinitionValue L140-151 锁2）与同文件 applyMergedView 单临界区（L73-87 diff/clear/putAll）对照亲核属实；「键复活」与「反向双写不一致」两个交错方向均可逐步写出；触发入口（IConfigReference 值写回 / assignConfigValue 无判定）核实。现状 = 新发现，未修复。

### [nop-core-framework-C2-017] 容器/插件同步契约缺失面：includeCreating 二元语义零声明、公共同步入口的无限期阻塞无契约——调用方 unknowingly 拿半成品或 unknowingly 无界等待

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/api/IBeanContainerImplementor.java:46-48`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:285-313,373-434,536-539`；`nop-core-framework/nop-plugin/nop-plugin-support/src/main/java/io/nop/plugin/support/AbstractPlugin.java:421-425`；`nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/VfsPluginDefinition.java:611-616`
- **证据片段**:
  ```java
  // IBeanContainerImplementor L46-48 —— 全框架核心同步开关，无任何 javadoc
  Object getBean(@Nonnull String name, boolean includeCreating, BeanCreationContext beanCtx);
  <T> T getBeanByType(Class<T> requiredType, boolean includeCreating, BeanCreationContext beanCtx);
  // BeanContainerImpl.getBean0 —— true=可能返回未赋值未init实例；false=无超时阻塞至 INITIALIZED
  if (includeCreating && beanScope != null) { ... return createdBean; }   // L376-387 半成品直返
  bean.checkBeanInitialized();                                            // L433/false → runUntil 无超时等待
  ```
- **严重程度**: P3
- **并发场景**: 任何实现 `IPluginActivator`/`IPluginCommand`/bean init 代码的插件作者调用 `container.getBean(name, true, null)`（以为「true=包含创建中=更宽松安全」实为拿到半成品）；或调用 `plugin.invokeCommand(...)`（公共同步门面）而命令实现把任务分派到自身执行器后永久阻塞/慢 I/O。
- **触发时序**: (a) 半成品面：`getBean(A, true)` 命中 scope 中 status<PROPERTY_SET 的实例→跳过 checkBeanInitialized（L744）→调用方对返回对象调用任意业务方法→未赋值字段 NPE/未 init 状态，且与 C2-013 的拦截器面叠加放大。(b) 无界等待面：`invokeCommand` L424/L615 `syncGet(用户 CompletionStage)`——无超时、无取消传导、阻塞行为未在接口 javadoc 声明；命令实现误用单线程执行器（CL-005 形态）即把调用线程永久挂起。`awaitStartFinished` L538 同形（C2-003 已立案，本案仅归档契约面不重复计数）。
- **现状**: 实现类内部注释（getBean0 L393/409 循环依赖检测注释）质量高，但接口契约零声明；includeCreating=true 的两处硬编码调用方（InjectRef L78/InjectType L53）与 false 硬编码（initBean L643）隐含「注入走 true、init 走 false」的架构意图，从未成文。
- **风险**: 契约缺失使每一处新调用方都在重新掷骰子：拿半成品（true）或引入无界等待点（false/invokeCommand）；评审与静态检查均无依据可循（对照 F10：声明式纪律无运行时守卫的文档形态）。
- **建议**: (1) `IBeanContainerImplementor.getBean/getBeanByType` 补 javadoc：includeCreating=true「可能返回未完成 PROPERTY_SET/INIT 的实例，调用方自行承担；注入专用」、false「阻塞至 INITIALIZED，当前实现无超时（引用 C2-002 修复项）」；(2) `IPlugin.invokeCommand` 契约声明「同步等待命令实现完成，无超时；命令实现必须自限阻塞」；(3) 长期：为 getBean(false) 增加带超时重载（依赖 C2-002 的 wait(timeout) 落地）。
- **失效面枚举**: 契约缺失点全集 = {`getBean(name,includeCreating,ctx)`、`getBeanByType(...,includeCreating,ctx)`（**接口 L46-48**）、`IPlugin.invokeCommand/invokeCommandAsync` 的同步门面语义、`awaitStartFinished`（C2-003 已立案）}；硬编码调用方全集 = {InjectRefValueResolver L78（true）、InjectTypeValueResolver L53（true）、BeanDefinition.initBean L643（false）、BeanContainerImpl 内部 L548/L565（true,false 组合）}；javadoc 现状 = {ProducedBeanInstance/BeanCreationContext/BeanScopeImpl 有并发声明（合规），IBeanContainerImplementor/DelegateBeanContainer/BeanScopeContextImpl 无（缺失面）}；StaticObjectFactory 全仓不存在（任务假设项实测排除）。
- **信心水平**: 确定（契约缺失为代码事实；后果依赖调用方行为故 P3）
- **误报排除**: 非文档洁癖立案——includeCreating 是行为开关而非命名歧义，true/false 两侧各对应一个已立案的并发缺陷族（C2-013/C2-002），契约声明是二者修复的防回归配套；invokeCommand 无超时单独成文而非挂在 C2-003 名下（不同入口不同对象）。
- **复核状态**: 未复核
**复核状态**: 【已复核·独立复核子代理 2026-10-17】**保留 P3（维持）**。IBeanContainerImplementor getBean/getBeanByType（L45-47）零 javadoc 亲核属实；includeCreating 硬编码调用方（InjectRef L78 / InjectType L53 = true；initBean L643 = false）核实；8 处 syncGet 全数复核——6 处 futureCall 同线程内联（FutureHelper.futureCall L122-129 亲核：task.call() 调用线程直接执行）非等待点，L538 awaitStartFinished 与 L615/L424 invokeCommand 为真等待点且无超时——与 ④ 表判定完全一致。现状 = 新发现，未修复。

---

## 维度集外线索（不立案展开）

- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**确认（与 CL-005 合并量化）**。亲核属实：onRefreshConfig → container.refreshConfig(beanId) → onRefreshConfig 同线程级联（BeanDefinition L798-803 + BeanContainerImpl L359-371），configDependants 构建期登记（BeanDefinitionBuilder buildRefResolver `resolvedBean.addConfigDependant(bean.getId())`）无环校验；C2 邻接面（级联/重绑定运行于唯一 config 线程，重绑定可触发惰性首建 = C2-012 E10 入口）核实。

### [nop-core-framework-CL-017]（维度集外线索，不立案展开）configDependants 级联刷新无环检测/深度上限；config-executor 单线程上执行 bean 属性重绑定用户代码
- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:792-803`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:359-371`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/BeanDefinitionBuilder.java:1093-1096`
- 形态归属：健壮性（`onRefreshConfig` → `container.refreshConfig(beanId)` → `onRefreshConfig` 同线程级联，configDependants 图构建期无环校验——A/B 互为配置依赖时 StackOverflowError）+ C2 邻接（级联与属性重绑定监听器全部运行在唯一 config 线程上，重绑定可触发惰性 bean 创建（C2-012 E10 入口），单线程被挂死则全平台配置刷新停摆）。移交复核阶段与 CL-005 合并裁定量化。

- **复核状态**: 【已复核·独立复核子代理 2026-10-17】**确认（移交 C6，与 CL-006 同位点注明）**。亲核属实：close 以 `beans.entrySet()`（CHM 弱一致哈希序）销毁、非逆拓扑序（BeanScopeImpl L104-112）；C2 邻接面复核良性——destroy 期 getBean 经 checkClosed fail-fast 抛 ALREADY_CLOSED（get L64-67 先于 map 访问），prototype（beanScope=null）停机期新建实例面归 C6。

### [nop-core-framework-CL-018]（维度集外线索，不立案展开）BeanScopeImpl.close 以 CHM 哈希序销毁 bean（非逆拓扑序），destroy 用户代码可见已销毁依赖
- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanScopeImpl.java:96-118`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:624-631`
- 形态归属：C6（销毁顺序 = `beans.entrySet()` 弱一致迭代序，与创建/拓扑序无关；A.destroyMethod 使用依赖 B 时 B 可能已销毁）。C2 邻接面已验证良性：destroy 期 `getBean` 命中已 closed scope → `checkClosed` fail-fast 抛 ALREADY_CLOSED（无挂死）；对 prototype（beanScope=null）则在停机期新建实例（资源面）。移交 C6 轮次，与 CL-006（destroyBean 二次 destroy 面）同族相邻。

---

## 汇总

### 新增发现清单

| 编号 | 严重程度 | 文件 | 摘要 |
|---|---|---|---|
| nop-core-framework-C2-012 | P2 | InjectRefValueResolver L66-78 / BeanContainerVariableScope L28 / BeanScopeImpl L38-43 / BeanDefinition L770-788 | 依赖边不可见族扩展：ignore-depends 显式逃逸边、表达式变量域动态边、运行期再解析入口——逐边锁序环闭合条件（集合注入经实测排除） |
| nop-core-framework-C2-013 | P2 | BeanDefinition L269-277,675-686 / AopBeanProcessor L301-325 | AOP 拦截器 bean 边不入拓扑图：concurrentStart 下未初始化拦截器注入 + C2-002 交叉获取入口 +1 |
| nop-core-framework-C2-014 | P2 | PluginManagerImpl L492-596 / AbstractPlugin L172-234 / VfsPluginDefinition L380-504 | LC(P)⇄BIN(x) 跨线程互等环闭合序列（jar 轨 computeIfAbsent 内 plugin.start→activate；LC 内 activator 回调 loadPlugin）——C2-005 递归面的跨线程具体化 |
| nop-core-framework-C2-015 | P3 | DelegateInvocationHandler L12-42 / BeanDefinition L556-567,654-661 | ioc:proxy handler 回填与代理调用的数据竞争（无 happens-before），瞬态 ISE 窗口 |
| nop-core-framework-C2-016 | P3 | DefinitionConfigProvider L200-213 | updateConfigValue 两段锁 check-then-act：配置键复活窗口 |
| nop-core-framework-C2-017 | P3 | IBeanContainerImplementor L46-48 / BeanContainerImpl L536-539 / AbstractPlugin L421-425 | 同步契约缺失面：includeCreating 二元语义零声明；invokeCommand/awaitStartFinished 无界阻塞无契约 |

### 本轮 CL 线索

- CL-017：configDependants 级联刷新无环检测/深度上限 + config 单线程重绑定用户代码（健壮性/C2 邻接）
- CL-018：BeanScopeImpl.close 哈希序销毁非逆拓扑序 + destroy 期 getBean fail-fast 验证良性（C6）

### ①~⑤ 目标完成度与终止信号判定

- **①**：不可见边全集枚举完成（12 条边逐条判定：3 条已立案、4 条新立案、2 条实测安全、1 条 F3 附注、1 条移交 CL），逐边闭合条件已给出。
- **②**：等待环站点穷举完成（CREATE/PROP_SET/INIT/LAZY/DELAY/DESTROY 六阶段 × 全部获取站点），新增「CREATE 阶段单侧不可见边闭合 BD⇄PBI 混合环」序列强化；DELAY→INIT 方向经推演确认无环。
- **③**：锁内用户代码站点表完成（20 行，覆盖 BD/LC/RLOCK/BIN/MV/FX/PBI/SCOPE/SUB/WLOCK/TRW 全部载体）；锁载体复扫确认与第 1 轮 44 站点一致（新增 4 行 grep 命中均为注释）。
- **④**：wait/await/syncGet 面穷举完成（唯一 Object.wait + 8 处 syncGet 逐站判定：1 处真等待点新立案、6 处同线程内联排除、1 处已立案）。
- **⑤**：契约缺失面收敛完成（includeCreating 接口契约、invokeCommand 门面契约新立案；StaticObjectFactory 实测不存在；三类容器内部类 javadoc 合规确认）。

**终止信号判定：本轮新增发现全部为 P2/P3（P0×0、P1×0、P2×3、P3×3），且五项深挖指令的枚举空间均已闭合**——锁载体集合无新增、等待原语集合无新增、依赖边集合闭合（含实测排除项）、用户代码站点表覆盖全部锁载体、契约面收敛。剩余不确定性仅剩两处已显式标注的范围外引用（E12 XplValueResolver 求值时点在 nop-xlang 域处理器内、api-core FutureHelper/ContextTaskQueue 对照项），均不构成本审计单元内的可达缺陷。**建议 C2 维度终止深挖，不再发起第 3 轮**；遗留事项（C2-012 与 C2-001 同族合并、C2-014 与 C2-005 同站合并、CL-017 与 CL-005 合并、CL-018 与 CL-006 同族合并）移交跨报告合并裁定阶段。
