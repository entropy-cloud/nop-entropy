# plugin 生命周期锁面与配置源加固设计（Plugin Lifecycle and Config Source Hardening）

> Status: active（plan 2506 Phase 1 产出；独立 draft review R1+R2 通过，0 Blocker/0 Major）
> Last Reviewed: 2026-10-26（行号 = live @ `3df6efe0aa` 回读值，该 HEAD 产品代码与 plan 2506 执行基线 `1aafb2ccda` 一致；执行前如漂移以 live 为准并登记）
> **评审**：R1 agent_6df99f00 @ 2026-10-26——1 Blocker（start 重入路径 updateConfig 回调无法锁外派发 → 补回调延迟契约两受害点 + 时序变化声明）/ 2 Major（forceUnload 定义层恢复载体与双轨可见性未成文 → 补内部恢复方法契约；destroyForcibly 2s 等待占用共享 globalTimer 线程代价未声明 → 补代价声明与接受裁定）/ 7 Minor（成功路径不释放、close 停机延迟上界、doDeactivate 用户代码契约、既有 catch 兜底保留、时序表措辞中性化、N2 测试包位、PluginState javadoc 已裁定归 Phase 3）——全部修订入文；30+ 处 file:line 抽查零漂移；R2 增量复核结论见下行
> **评审 R2**：agent_6df99f00 增量复核 @ 2026-10-26——Blocker-1/Major-1/Major-2 三处闭合 PASS（回调延迟契约全路径复核：stop/reload/load/AbstractPlugin 轨/updateMergedView 叶锁均无临界区内 reconcileLock 获取点；monitor 可重入语义下步进段返回变更标志纯基本语义可实现；时序声明诚实）、Minor-1~7 修订准确、无新引入 B/M；2 条残留极轻项（start 异常退出派发保证、forceUnload 尾部 reconcile）已随本轮一句话落文。**结论：0 Blocker / 0 Major → active 进入 Phase 2**
> Source: plan 2506（`ai-dev/plans/2506-cra-phase8.12-r2-03-plugin-lifecycle.md`）Phase 1 裁定①–⑦义务；发现事实源 = WI 2.2 审计目录 `ai-dev/audits/2026-10/2026-10-17-1725-deep-audit-nop-core-framework-concurrency/`（C1-shared-mutable-state.md 的 C1-009/010/011/012/020、C6-resource-lifecycle.md 的 C6-001/002/003/004/014、merge-adjudication.md 并案裁定与裁定 10、C10-test-protection.md 的 C10-001/004~008 与 N1/N2/N6/N7 注记）；批次归组 = batch-proposal §二.2 R2-03
> 实现锚：`nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/`（VfsPluginDefinition / PluginManagerImpl / ServiceProxy）+ `nop-core-framework/nop-plugin/nop-plugin-support/src/main/java/io/nop/plugin/support/AbstractPlugin.java` + `nop-core-framework/nop-config/src/main/java/io/nop/config/source/`（jdbc/JdbcConfigSource + file/AbstractFileConfigSource）+ `nop-utils/nop-shell/src/main/java/io/nop/shell/ShellRunner.java`

## 一、设计结论

1. **裁定①（C1-010 锁矩阵统一）**：插件生命周期锁面以**双锁单向序 `reconcileLock → lifecycleLock`** 统一——VfsPluginDefinition 与 AbstractPlugin 双轨的 load/unload（及 start 内嵌步、stop 内嵌步、updateConfig 合并步）全程进各自 lifecycleLock，PluginManagerImpl 的 unloadPlugin/activatePlugin/deactivatePlugin（含 requireLoaded 校验段）全程进 reconcileLock；**硬规则：lifecycleLock 持有期间禁止触发 `onConfigChanged`/`manager.reconcile`**（回调延迟契约：updateConfig 拆锁内步进/锁外派发两段，外部入口与 start 内嵌重入路径两个受害点均在最外层临界区退出后派发；start 路径回调自 activate 前移至 activate 后为可观测语义收紧），ABBA 向量结构性消除。AbstractPlugin state/beanContainer/definition 升 volatile（双轨防护对齐）；复合读按「取引用→复核 state」统一模式覆盖 7 站点、统一异常 `ERR_PLUGIN_INACTIVE`；updateConfig **随批纳入 lifecycleLock**（merge-adjudication C1-020「与 C1-010 同批」条款兑现，顺带闭合 C1-013 残留 merge 义务与 C1-020 的 ACTIVATING 窗口丢更新）；activate 的容器 start 重 I/O 留在锁内（既有行为，reconcile 持 reconcileLock 期间调 activate 的阻塞代价为既有量级、非本批引入）；C1-018/019 同锁域预留（锁矩阵为其 R4-15 修复的承载前提，不预实现其本体）。
2. **裁定②（C6-002 失败落定 + 状态机恢复 API）**：中间态 try/finally 收敛终态——deactivate 失败落 **FAILED**（拒绝回退 ACTIVATED：半停容器不可信）、activate rollback 失败落 FAILED（finally 保证不被 rollback 抛错跳过）、autoInit 的 `CoreInitialization.destroy()` 纳入独立尽力段（stop 抛错不跳过 destroy）；字段置空时序改为「成功路径清理后置空、失败路径保留原引用」（FAILED 态残留 scope/container = 待恢复入口处置的显式信号）；恢复 API = **`PluginManagerImpl.forceUnload(pluginId)` 实现类公共方法**（不上 IPluginManager 接口），前置态 {ACTIVATING, DEACTIVATING, FAILED, LOADED}、ACTIVATED 类型化拒绝（新码落 PluginManagerErrors）、后置态 UNLOADED、半停容器尽力清理逐段留痕、既有 unload/activate 守卫零改动（经双轨定义层各新增的内部恢复方法绕守卫在 lifecycleLock 内原子落定：Vfs 轨 package-private / AbstractPlugin 轨 public 标注内部契约）；不重构 nop-ioc 容器 stop/close 语义（R2-04 边界）。
3. **裁定③（C6-001 资源释放与 N2 消解）**：loadPluginFromJar lambda 的 try 边界上移至 PluginClassLoader 构造点（resolvePluginResource 留 try 外），单个 finally `IoHelper.safeCloseObject` 与既有 :591 兜底同构；**两类失败终态成文**：「映射建立前失败 → lambda 局部 classLoader 确定性释放（不建映射，重试重建）」vs「映射建立后 unloadPlugin 失败 → holder+classLoader 保留可重试（N2 钉零回改）」；**断言缝 = package-private `newPluginClassLoader(...)` 工厂钩子**（测试子类同包捕获实例断言 `isClosed()`，生产零变化；既有 `classLoaderOf` 反射探针继续服务 N2「映射后失败」腿）；C6-011 同款「仓内无装配点」条件化标注统一成文；C2-005 同 lambda 锁纪律重构归 R4-15。
4. **裁定④（C1-009 close 互斥）**：close() 加 synchronized 与 refreshConfig/tryTriggerChange 同 this monitor + loadConfig 重建连接点前置 closed 守卫（类型化抛错，新码落 ConfigErrors；monitor 全量串行下生产不可达，为锁纪律防退化守卫）；cancel(false) 不等待在途的残余窗被 monitor 吸收（close 与在途轮全序化，两序均无泄漏）；connection 无需 volatile（修复后全部读写入 monitor）、closed 保持 volatile；C1-021 per-callback 隔离体与 R1-02 卸载/超时语义零触碰，既有 4+2+7 条回归零回退。
5. **裁定⑤（C6-003 构造序）**：终裁 = **先加载成功再调度**（拒绝 catch-cancel）——JdbcConfigSource 构造器 loadConfig 前置、失败路径关闭已建连接后抛（jdbc 变体附带面随案闭合）；AbstractFileConfigSource 同口径（loadConfig 前移至调度前）；KeyFile/PropsFile 直通 super 自动覆盖；调度行保留 R1-02 的 `executeOn(globalWorker)` 卸载形态不回退；与 C4-001 已修卸载面、C1-009 互斥面的同文件次序 = 先⑤后④同提交组。
6. **裁定⑥（C6-004 外层 finally 兜底）**：safeDestroy 兜底置于外层 try(:80) 的 finally（process 引用提升声明，覆盖 newProcess 后全部窗口：时序 A/B/C/D + C6-014 的 E/F 拒绝窗）；幂等论证 = 成功路径 waitFor 已返回、destroy 对已退出进程 no-op、超时路径二次 destroy 幂等；`destroyForcibly()` 升级 = destroy 后有界等待（预算 2000ms 常量）未退出再 forcibly，覆盖超时回调与兜底两调用侧；stderr 读取线程退出链 = 进程销毁 → EOF → readLine 退出（stopped 双保险）；N6 平台守卫随回归测试以 assumeTrue 形态落地；C4-005（timeout=0 挂起）不入本批、不恶化。
7. **裁定⑦（协同边界）**：R2-04 = nop-ioc 零改动承诺（`BeanScopeImpl.close` 末个 destroy 异常上抛 :116-117、`BeanContainerImpl.stop` 转发 :616 按既有行为消费，裁定②的尽力清理即对该上抛的消费方适配）；R4-15 移交清单 = C1-018/019 锁域复用 + C2-005 同 lambda 重构 + C10-004 成员面拆分（updateConfig 已随裁定①纳入，移交项 ④ 不成立）；R1-01/R1-02 = per-callback 隔离、executeOn 卸载、JDBC 超时三面零回退承诺。

## 二、背景与动机

R2-03 收敛 WI 2.2 六项 P1（合并后含 5 条并案），三面缺陷域：

- **插件生命周期（C1-010 族案 + C6-002 + C6-001）**：VfsPluginDefinition 的 unload :365-377（守卫读 :369-374 + 锁外迁移写 :375-376）与 load :357-363 在 lifecycleLock 外迁移状态，PluginManagerImpl.unloadPlugin :144-165 不持 reconcileLock——管理卸载与配置驱动的 reconcile 自动激活正向交错产出**孤儿活容器**（注册表无条目 ⇔ 容器活跃，不可经任何 API 停止；C2-004 为同一窗口镜像交错序）；AbstractPlugin jar 轨同形且三字段非 volatile（C1-011，双轨防护不对称）；(state, scope/container) 复合读 7 站点 TOCTOU 产出异常类型漂移（C1-012）；updateConfig :572-593 锁外 RMW + ACTIVATING 窗口热应用静默丢更新（C1-020，关联 C1-013 残留 merge 义务）。并发窗口之外，状态机对**失败**无落定：四处中间态站点（Vfs doDeactivate :434-458 / activate catch :408-422 + rollbackActivation :507-522 / AbstractPlugin doDeactivate :256-274 / activate catch :194-201 + rollbackActivation :236-242）一次 destroy-method 抛错即确定性卡死 DEACTIVATING/ACTIVATING——字段引用已先置空、unload/activate/reload 守卫全部拒绝中间态、`deactivateIfActive` :402-413 只吞异常不复位，无任何公开恢复通道。jar 轨加载另有映射前失败点三处（PluginClassLoader 构造 :571-572 / loadPlugin :574 / getPluginConfig :575）在 try(:576) 之外，坏包重试句柄线性累积（C6-001）。
- **配置源资源安全（C1-009 + C6-003）**：JdbcConfigSource.close :192-202 不 synchronized，停机撞在途刷新（任务体经 executeOn 在 globalWorker 执行，R1-02 形态）→ 新建连接写入字段后任务已 cancel，永不关闭；两配置源构造器「先调度后加载」（JdbcConfigSource :55-76 / AbstractFileConfigSource :42-57）初始加载失败即泄漏 globalTimer 永久周期任务（jdbc 变体附带连接滞留与故障期连接风暴）——泄漏窗口覆盖全运行期并跨 destroy→reinit 代际，启动期降档不适用（审计复核论证成立）。
- **Shell 进程兜底（C6-004 ←C6-014）**：ShellRunner.run :73-143 外层 try(:80) 无 finally，进程销毁仅挂超时回调 :85-88；非 timeout 异常/中断/collector 抛错路径（时序 A/B/C）与超时子路径自身（时序 D：timer 截止点晚于 diff 截止点）漏杀进程，`scheduleTimeout`/`collectError` 在内层 try(:92) 之前的拒绝窗（时序 E/F，C6-014）同样无兜底；子进程不销毁则 stderr 读取线程永久阻塞 readLine，pin 住无界 cachedThreadPool 线程；safeDestroy :259-271 无 destroyForcibly 升级。

前批边界：plan 2496（C1-021 per-callback 隔离，tryTriggerChange :118-139）与 plan 2497（构造器 executeOn 卸载 :65-66/:50-52、queryTimeout :155、连接级超时键）产物为同文件既有形态，本批零回退义务见裁定④⑤⑦。

## 三、核心设计

### 3.1 裁定①：锁矩阵统一（C1-010 主案 + C1-011/C1-012 并案 + C2-004 镜像窗 + C1-020 关联面）

#### 3.1.1 锁序单向不变式与硬规则

不变式：**reconcileLock（编排互斥）→ lifecycleLock（单插件单飞）单向获取**；反向获取被硬规则禁止——lifecycleLock 临界区内禁止触发 `onConfigChanged`（= `manager::reconcile`，loadVfsDefinition :522 注入）与任何会获取 reconcileLock 的 manager 生命周期调用。ABBA 向量复核：unloadPlugin（reconcileLock）→ holder.plugin.unload()（lifecycleLock）为合法正向；updateConfig 缺陷形态尾部回调 :589-592 在 lifecycleLock（若纳入）内调 reconcile（reconcileLock）构成反向——修复后回调一律延迟到 lifecycleLock **最外层临界区退出之后**派发，向量消除；修复后全表复核 lifecycleLock 临界区内 reconcileLock 获取点为零。

**回调延迟契约（硬规则的落地机制，覆盖两个受害点）**：updateConfig 拆为锁内步进与锁外派发两段——锁内步进段（合并 mergedView + 热应用 + 状态判定，返回是否发生变更）只在已持 lifecycleLock 的前提下执行；`onConfigChanged` 派发只发生在两个非临界区位点：①外部公共入口 updateConfig：synchronized 步进段返回后（此时本层 monitor 已释放）即派发；②start 内嵌路径：lifecycleLock 为可重入 monitor，start 持锁调用步进段后**外层临界区仍在**，故变更标志随段内返回值带出，start 在自身临界区退出后统一派发——**派发保证**：以 finally 语义承载，activate 抛错（异常退出临界区）时变更标志非空仍派发（缺陷形态下回调先于 activate 必然执行，修复不得让失败路径丢失 reconcile 通知，否则回退 C1-020 族症状）。**时序变化声明**：start 路径的回调自「activate 之前」移至「activate 完成之后」——缺陷形态下回调（reconcile）在 state=LOADED 窗口触发、与 activate 构成交错的输入之一；修复后 reconcile 见到的是 ACTIVATED 终态（自动激活守卫幂等 no-op），属消除交错面的可观测语义收紧，随批在回归测试中固化。 AbstractPlugin 对齐面：state/beanContainer/definition 升 volatile（Vfs 轨 :104-133 既有风格的轨间对齐，C1-011 本体）；lastActivationError 随同族对齐；坐标/时间戳字段维持现状（启动期写读，C1-011 失效面枚举的条件安全口径）。

#### 3.1.2 方法 × 持锁矩阵（最终契约）

| 载体 · 方法 | 持锁 | 契约要点 |
|---|---|---|
| VfsPluginDefinition · load :357-363 | lifecycleLock | 迁移写（definitionConfig/state/loadTime）不可分割 |
| VfsPluginDefinition · unload :365-377 | lifecycleLock | 守卫读 :369-374 与迁移写 :375-376 同锁——C2-004 正反两序镜像交错均不可达 |
| VfsPluginDefinition · activate :379-424 | lifecycleLock（既有 :381 保持） | 容器构建/start/activator 重 I/O 留锁内（见 3.1.3） |
| VfsPluginDefinition · doDeactivate :434-458 | lifecycleLock（既有 :435 保持） | scope.close/container.stop 留锁内；失败落定见裁定② |
| VfsPluginDefinition · start :549-563 | lifecycleLock（状态分支判定 + load/updateConfig 步进锁内） | activate 锁内重入（同一临界区）；内嵌 updateConfig 步进的回调延迟至 start 临界区退出后派发（回调延迟契约受害点②，时序变化声明见 3.1.1） |
| VfsPluginDefinition · stop :565-570 | 两步各自锁内原子 | deactivate→unload 中间窗由 unload 守卫拒绝兜底（可观测、可重试），不引入复合锁 |
| VfsPluginDefinition · updateConfig :572-593 | lifecycleLock | merge + 热应用 + 态判定原子；`onConfigChanged` 回调锁外派发；纳入后 ACTIVATING 窗口丢更新（C1-020）结构性消失——updateConfig 与 activateInternal 快照读 :461 同锁串行，配置落点要么在快照前、要么在终态后热应用，无需 dirty-replay；C1-013 merge RMW 义务随锁闭合 |
| AbstractPlugin · load :131-148 / unload :150-164 / activate :166-202 / doDeactivate :256-274 / start aware 段 :312-324 / stop aware 段 :339-345 | lifecycleLock | 与 Vfs 轨同形对齐（unload 守卫读+写 :154-161/:162-163 同锁） |
| AbstractPlugin · start/stop 兼容路径 :326-335/:346/:349-358 | 不入状态机锁 | 非 aware 轨无六态迁移（LifeCycleSupport 既有语义），锁迁移面显式豁免 |
| PluginManagerImpl · unloadPlugin :144-165 | reconcileLock | 含 holder 检查、unload/stop、holder 移除、索引/订阅清理、classLoader 关闭、尾部 reconcile（重入） |
| PluginManagerImpl · activatePlugin :167-175 / deactivatePlugin :176-185 | reconcileLock | 含 requireLoaded 校验段（TOCTOU 随序列化消解）；deactivate 持锁跨 futureCall 任务体全时长，与 doReconcile 的 deactivateIfActive syncGet :407 既有量级一致 |
| PluginManagerImpl · reconcile :226-243 / reloadPlugin :637-688 | reconcileLock（既有保持） | 单飞 + dirty 重入合并语义不变 |
| PluginManagerImpl · getPlugin/getLoadedPlugins/allPlugins | 无锁 CHM 读（保持） | 读侧一致性由 3.1.4 复合读模式承载 |

#### 3.1.3 锁内可能外呼站点逐一裁定

| 站点 | 外呼内容 | 裁定 |
|---|---|---|
| Vfs activateInternal :475-477 | BeanContainerBuilder.build + container.start()（bean init 用户代码，重 I/O） | 留锁内（既有行为 :381-424）。reconcile 持 reconcileLock 期间经 activateIfInactive 调 activate 的阻塞代价 = 既有行为、非本批引入；单向锁序下只有串行延迟、无环 |
| Vfs activateInternal :492-503 | container.getBean(activator) + activator.activate(scope, config)（用户代码） | 留锁内（既有行为）。契约条款：activator 内调用 IPluginManager 生命周期 API/reconcilePlugins 不受支持（平台代码侧由硬规则闭合；用户代码侧契约化并登记 watch——activator→reconcileLock × reconcile→lifecycleLock 的既有环同型面，仓内 activator 均不触 manager API） |
| Vfs doDeactivate :449/:453、rollbackActivation :514-520 | s.close()（effect dispose）/ c.stop()（bean destroy，用户代码） | 留锁内（既有行为；终态收敛需与状态迁移同临界区）。契约条款与 activator 行同口径：effect dispose / bean destroy-method 内调用 IPluginManager 生命周期 API 不受支持（用户代码侧契约化并登记 watch） |
| Vfs updateConfig :584 | provider.updateMergedView（热应用 → bean 属性重绑定监听） | 留锁内（与 merge 原子 = C1-020 闭合的前提）；DefinitionConfigProvider 内部为 synchronized(mergedView) 叶锁，从不回引 lifecycleLock/reconcileLock，无环 |
| Vfs updateConfig :589-592 | onConfigChanged.run() = manager::reconcile | **锁外派发**（回调延迟契约受害点①：外部公共入口在 synchronized 步进段返回后派发；start 内嵌路径归受害点②，见 3.1.2 start 行与 3.1.1 契约） |
| AbstractPlugin buildBeanContainer :217-219 / doDeactivate :270 | CoreInitialization.initialize()/destroy()（全局初始化/拆除） | 留锁内（既有行为；CL-014 全局拆除面为维度集外交接项，本批不触碰） |
| AbstractPlugin buildBeanContainer :225-229 | AppBeanContainerLoader.loadFromResource + container.start() | 留锁内（既有行为，同 Vfs activate 论证） |

#### 3.1.4 复合读双检 7 站点与统一异常类型

统一模式：**先取引用（volatile 读）→ 复核 state → 复核不通过即快速失败**；统一异常 = `ERR_PLUGIN_INACTIVE`（PluginApiErrors 既有码）——deactivate 翻转窗内消费方一律获 INACTIVE，不再泄漏 NOT_STARTED/ISE 混型。残余窗声明：双检不能消除「复核通过后 deactivate 才开始」的在途窗（请求排空 quiescence 为 plan Non-Goal follow-up），双检目标是把可消除的交错形态统一收敛为 INACTIVE。

| # | 站点 | 复合读 | 处置 |
|---|---|---|---|
| 1 | Vfs getScope :326-328 | (scope, state) | 双检（取 scope → 复核 ACTIVATED；未通过返回 null，形态保持） |
| 2 | Vfs requireActiveScope :651-657 | (scope, state) | 双检，失败抛 INACTIVE |
| 3 | Vfs requireActiveContainer :659-665 | (container, state) | 双检同型 |
| 4 | Vfs invokeCommandAsync :599-608 | requireActiveContainer → getBean | 消费站点 3 的双检结果，同一引用贯穿（不二次取字段） |
| 5 | ServiceProxy.Handler.invoke :61-76 + resolveTarget :78-89 | getScope → resolveTarget | invoke 处单次取 scope 引用贯穿 resolveTarget（现有参数传递形态保持），窗由站点 1 双检覆盖 |
| 6 | Vfs updateConfig :580-586 | (state, configProvider) | 随锁迁移消解（lifecycleLock 内与 doDeactivate 互斥，双检退化不需要） |
| 7 | PluginManagerImpl requireLoaded :209-215 | (holder, plugins) | 随 reconcileLock 序列化消解（unloadPlugin/activatePlugin/deactivatePlugin 同锁，摘除窗对校验段关闭）；AbstractPlugin aware 路径 invokeCommandAsync :398-409 的 (state, beanContainer) 为同型附加面，双检覆盖 |

### 3.2 裁定②：中间态失败落定 + 状态机恢复 API（C6-002）

**状态机全集与落定原则**：PluginState 六态 = UNLOADED / LOADED / ACTIVATING / ACTIVATED / DEACTIVATING / FAILED（nop-plugin-api 既有枚举，零新增态、零改值语义）——ACTIVATING/DEACTIVATING 是 lifecycleLock 临界区内的过渡态，任何失败路径必须在退出临界区前经 finally 落定到既有终态（LOADED 或 FAILED），不滞留中间态；lastActivationError 随 FAILED 落定记因（activate 失败路径既有，deactivate 失败路径同款补记）。

**失败终态表**（try/finally 收敛：中间态置位后清理步骤逐段尽力、失败聚合，临界区出口 finally 依标志落定）：

| 失败站点 | 中间态 | 终态 | 字段处置 |
|---|---|---|---|
| Vfs doDeactivate s.close :449 / c.stop :453 抛 | DEACTIVATING | FAILED（finally 落定） | scope/container/configProvider **保留原引用**（成功路径清理后置空；失败路径保留供恢复入口与诊断） |
| Vfs activate catch :408-422 → rollbackActivation c.stop :519 抛 | ACTIVATING | FAILED（finally 落定，不再被 rollback 抛错跳过） | 同上；rollback 内清理异常降级为留痕（LOG.error），不阻断 FAILED 落定 |
| AbstractPlugin doDeactivate c.stop :266 / CoreInitialization.destroy :270 抛 | DEACTIVATING | FAILED（finally）；destroy 纳入独立尽力段——c.stop 抛错不跳过 destroy | beanContainer 保留 |
| AbstractPlugin activate catch :194-201 → rollbackActivation :236-242 抛 | ACTIVATING | FAILED（finally） | 同 Vfs |

取舍定夺：**deactivate 失败不回退 ACTIVATED**——半停容器（effect 部分回退、bean 部分 destroy）谎报健康态会放大级联误判（hasActivatedPluginByName 读 state）；FAILED 语义扩展成文：FAILED 态字段非 null = 残留半停容器待恢复入口处置的显式信号（PluginState javadoc 语义随批注记级更新归 Phase 3 owner doc 面）。`unload()` 对 FAILED 态残留字段增加幂等尽力清理后再落 UNLOADED（成功路径字段已空、零行为变化；防「丢弃引用漏停容器」）。`deactivateIfActive` :402-413 的 catch 吞异常形态保持——状态复位义务随 try/finally 移入定义层，manager 侧零改动；FAILED 可重试语义使 reconcile 下轮迭代自然重试激活。 **恢复 API 契约（`forceUnload(pluginId)`）**：入口载体 = PluginManagerImpl **实现类公共方法**（不上 IPluginManager 接口——接口为跨模块消费契约，加方法即公共面扩张，超出本批 PA 授权（框架核心引擎内部）；仓内无 beans.xml 装配点、宿主不可达是现状，plan follow-up 已预留宿主装配落地时的公共面走查通道）。前置态 = {ACTIVATING, DEACTIVATING, FAILED, LOADED}（LOADED 受理 = 既有 unload 语义等价；UNLOADED 幂等返回并记日志）；ACTIVATED **类型化拒绝**（新错误码落 `PluginManagerErrors`——manager 侧生命周期语义族，ARG_PLUGIN_ID/ARG_PLUGIN_STATE 既有参数族），须先走 deactivatePlugin 正常通道，不强行销毁健康活容器。后置态 = UNLOADED；manager 侧动作 = holder 移除 + name 索引/全局订阅清理 + holder.classLoader 关闭（jar 轨）+ **尾部 reconcile**（与 unloadPlugin :163 同侧语义：依赖方拓扑需重新评估；forceUnload 已持 reconcileLock，monitor 可重入安全），与 unloadPlugin 成功路径同侧语义。尽力清理 = 对残留 scope/container 逐段 safeClose/safeStop、异常逐段 catch 留痕不阻断状态迁移；诊断留痕 = lastActivationError 保留 + WARN（pluginId、前态、清理段结果）。与守卫关系 = unload/activate 既有守卫零改动，forceUnload 是中间态唯一合法出口。**锁内复核契约**：manager 的前置态判定在 lifecycleLock 外读取；定义层恢复方法在 lifecycleLock 内再次复核——若受理时为 ACTIVATING 而等锁期间在途激活已**成功**落 ACTIVATED，则无可恢复对象，恢复方法不做任何迁移并返回「未恢复」，manager 以与前置拒绝相同的类型化错误拒绝（holder 保留，调用方走 deactivatePlugin 正常通道）；在途激活**失败**落 FAILED 则正常恢复到 UNLOADED。该复核使「不强杀健康活容器」原则在等锁窗口内同样成立，且两种在途结局均确定性可测（回归 `TestPluginStateRecovery` 两用例以 Thread.State.BLOCKED 对齐）。**定义层恢复载体（守卫零改动的前提机制）**：既有 unload 守卫（Vfs :369-374 / AbstractPlugin :154-161）拒绝 ACTIVATING/DEACTIVATING 且 state 为私有字段，manager 侧 forceUnload 不得直接调 unload() 穿越守卫——双轨定义层各新增一个**内部恢复方法**（不经 unload 守卫；在各自 lifecycleLock 内原子完成：逐段 safeClose(scope)/safeStop(container)/configProvider 清理 → 字段置空 → 落 UNLOADED；lastActivationError 保留供诊断；每段异常 catch 留痕不阻断），forceUnload 在 reconcileLock 内按前置态判定后调用之。可见性载体：Vfs 轨与 PluginManagerImpl 同包（io.nop.plugin.manager.impl）→ package-private；AbstractPlugin 轨跨包（io.nop.plugin.support）→ public 方法但 javadoc 标注「平台内部恢复契约，非插件开发者 API」（nop-plugin-support 为 impl 模块非 nop-\*-api，不构成 Protected Area 公共面扩张）。R2-04 边界 = 恢复 API 只在插件定义/manager 层实施，`BeanScopeImpl.close`（:100-120 逐 bean 销毁全执行后上抛末个异常）与 `BeanContainerImpl.stop`（:595-617 转发 scope.close 异常）的上抛行为按既有消费，不反向要求 IoC 聚合语义改造。

### 3.3 裁定③：映射前失败资源释放与 N2 消解（C6-001）

**try 收窄形态**：loadPluginFromJar :567-596 的 lambda 内 try 边界上移至 PluginClassLoader 构造点之前；resolvePluginResource :570 留 try 外（其失败时无 classLoader 可释放，且 HttpPluginResourceResolver.download 自带 finally 清理——审计 F3⑥ 配对成立口径）；lambda 局部变量先声明后赋值，单一 finally 兜底 `IoHelper.safeCloseObject`——**释放条件 = 仅失败路径**（成功路径 :582 返回 holder 时 classLoader 归 holder 持有，finally 以成功标志/局部引用置空判定不释放；无条件释放会关闭在用 classLoader），与既有 :591 兜底同构，替代既有 catch 内嵌套 try/finally 的双释放路径（:583-594 形态）；既有 catch 内 plugin.unload()/stop() 的插件级兜底（:585-589，非 aware 插件 stop 兼容路径清理）**保留**，只合并 classLoader 释放位。**构造期抛错窗（失败点①的精确形态）**：PluginClassLoader 构造器内 loadPluginConfig 抛错时（缺 plugin.json），调用方局部引用尚未赋值、实例不可达，lambda 的 finally 无法代为释放——该窗由 **PluginClassLoader 构造器自清理**闭合（loadPluginConfig 失败即 close 已注册的 jar 资源后原样重抛，close 自身异常以 suppressed 挂载不吞）；lambda finally 覆盖构造成功之后的失败点（loadPlugin / getPluginConfig / plugin.load·start）。观测分工：构造期窗由同包子类覆写 loadPluginConfig + close 记录旗标直测（`TestPluginClassLoaderConstructorCleanup`），构造后窗由工厂钩子捕获实例断言 isClosed（`TestPluginLoadFailureResourceRelease`）。 **两类失败终态表**：

| 失败类别 | 窗口 | 终态 |
|---|---|---|
| 映射前失败：构造器 loadPluginConfig（PluginClassLoader :60-76，缺 plugin.json 抛 ERR_PLUGIN_NO_PLUGIN_CLASS_NAME）/ loadPlugin :574 / getPluginConfig :575 / plugin.load·start·unload 段 :577-589 | computeIfAbsent lambda 内、holder 入 map 前 | lambda 局部 classLoader 确定性 safeCloseObject 释放；不建映射，重试重建（泄漏线性累积链断开） |
| 映射后失败：unloadPlugin :144-165 内 unload/stop 抛错 | holder 已在 map | holder + classLoader 保留可重试（:154-158 既有注释语义零回改） |

**N2 兼容核对表**（`TestPluginUnloadFailure#testUnloadFailureKeepsClassLoaderUsableAndRetryable` :111-139 逐断言）：

| 断言 | 修复后 |
|---|---|
| :124 stop 失败上抛 RuntimeException | 不受影响（不同站点：unloadPlugin 路径零改动） |
| :126 assertFalse(classLoader.isClosed()) | 不受影响（映射后失败保留语义 = N2 钉本体） |
| :128 assertEquals(plugin, manager.getPlugin) | 不受影响（holder 保留） |
| :132-135 重试成功：isClosed true + holder 移除 + stop 真实调用 | 不受影响 |
| testMissingPluginJsonFailsWithExplicitError :142-152：ERR_PLUGIN_NO_PLUGIN_CLASS_NAME + map 空 | 错误码与传播不变；新增释放路径对未赋值局部引用 no-op |

**断言缝裁定**：映射前失败不建映射 → 既有 `classLoaderOf` 反射探针（TestPluginUnloadFailure :98-108，从 holder 读）恒 null 不可用。终裁 = **package-private `newPluginClassLoader(...)` 工厂钩子**（PluginManagerImpl 内，lambda 改经其构造，方法体 = 现构造表达式原样搬移，生产零变化）；测试子类须置于**同包** src/test/java/io/nop/plugin/manager/impl/ 下覆写捕获实例、断言 `isClosed()`（package-private 对异包子类不可覆写；既有 N2 测试位于 io.nop.plugin.manager 包不动，新增捕获子类落 impl 包为该缝的既定形态）；既有探针继续服务 N2「映射后失败」腿，两缝各司其职；Phase 2 不得临场自加其他缝（备选 fd 计数替身的拒绝见 §四）。 **条件化标注（C6-011 同款）**：PluginManagerImpl 仓内无 beans.xml 装配点（仅测试实例化），loadPluginFromJar 生产可达性 = 宿主装配（预期未落地用法）——本修复的价值表述与回归测试均为该条件化口径，随宿主装配落地自然兑现。 **R4-15 C2-005 边界**：本批不做 computeIfAbsent lambda 锁纪律重构（bin 锁内 HTTP 下载/容器构建长临界区、映射函数瘦身/锁外构建/putIfAbsent 回写归 R4-15，merge-adjudication 裁定 10 同 lambda 协同条款）；工厂钩子设计为 C2-005 重构后仍成立的缝（钩子调用点随映射函数搬移，不依赖当前 lambda 形态）。

### 3.4 裁定④：close 互斥（C1-009 ←C4-002）

形态定稿 = **close() 加 synchronized（与 refreshConfig :82 / tryTriggerChange :118 同 this monitor）+ loadConfig 重建连接点 :142 前置 closed 守卫**（closed=true 抛类型化异常，新码落 `ConfigErrors`；monitor 保证 close 与刷新任务体全量串行，该守卫在修复后生产路径不可达，定位为锁纪律的防退化守卫——防未来新增调用点绕过互斥重建连接）。 **残余窗论证**：cancel(false) 不等待在途任务，旧形态下 close 可与在途 refreshConfig 交叠；修复后 close 在 monitor 上等待在途轮完成——「任务体新建连接写入字段」与「close 置位+关连接」被全序化：close 先行则 closed 置位后任务体下轮入口检查 :83-84 退出（重建守卫双保险）；任务体先行则 close 随后关闭其连接。两序均无泄漏，刷新任务体仍在 worker 线程执行（R1-02 语义），close 可能在停机线程——同 monitor 即串行，无线程亲和约束。**停机延迟代价声明**：close 入 monitor 后须等待在途轮完成，上界 = 一轮 loadConfig（SQL 段受 queryTimeout 约束）+ tryTriggerChange 用户回调段（时长无平台上界）；缺陷形态 close 不等待但以连接泄漏为代价——本批接受「停机等待一轮在途刷新」换取零泄漏，回调时长治理属用户代码契约（per-callback 隔离已限制异常面，不限制时长）。 **volatile 裁定**：closed 保持 volatile（既有）；connection 修复后全部读写（refreshConfig/closeConnection/loadConfig/close 四站点）均在 monitor 内，无需 volatile；errorCount :45 / callbackErrorCount :51 既有 monitor 内自洽维持（后者仅在 tryTriggerChange 临界区内访问）。 **正交性**：C1-021 per-callback 隔离体（tryTriggerChange 内部结构 :118-139）零触碰；R1-02 executeOn 卸载与 queryTimeout 零回退；回归门禁 = TestJdbcConfigSourceCallbackIsolation 4 条 + TestJdbcConfigSourceWorkerOffload 2 条 + TestJdbcConfigTimeout 7 条 + TestFileConfigSourceRefresh/Walk/WorkerOffload 全绿。 **同文件次序**：与裁定⑤（构造序）同一 nop-config 提交组，先⑤（启动期构造面）后④（停机期 close 面），C1-021 隔离体三面零互踩。

### 3.5 裁定⑤：构造序修正（C6-003）

二选一终裁 = **先加载成功再调度**（拒绝 catch-cancel，理由见 §四）。两站点统一落法：

- JdbcConfigSource 构造器 :55-76：loadConfig 前置，调度行 :65-66 后移至加载成功之后（`executeOn(globalWorker)` 卸载形态原样保留——R1-02 语义零回退）；loadConfig 失败路径关闭已建连接（:142-143 可能已 createConnection——jdbc 变体附带面随案闭合）再抛 NopException。
- AbstractFileConfigSource 构造器 :42-57：`this.vars = loadConfig()`（:56）前移至调度 :50-52 之前；KeyFileConfigSource :23 / PropsFileConfigSource :26 直通 super 构造器自动覆盖。

构造期竞态声明：先加载后调度后，构造期间无任务存在（调度在构造尾部、首轮刷新仍有 initial delay），「构造未完成即刷新」窗不存在。ConfigStarter 前提面零改动：构造抛错时源未入 closeableSources（:133-137/:152-156）、doStop :526-551 仅关列表的既有形态不再是泄漏前提（泄漏源头在构造器内消除）。嵌入场景 destroy→initialize 循环的逐次累积随构造序修正一并断开。

### 3.6 裁定⑥：外层 finally 兜底（C6-004 ←C6-014）

**兜底形态**：safeDestroy 兜底置于外层 try(:80) 的 finally（process 引用提升声明，null 时 no-op——顺带覆盖 newProcess :83 自身失败窗）；内层 finally :121-127 的两 future cancel 保持。幂等论证：成功路径 waitFor 已返回/进程已退出，destroy 对已退出进程 no-op；超时主路径 safeDestroy 已执行，二次 destroy 幂等；stopped 置位幂等。 **时序覆盖表**：

| 时序 | 触发 | 缺陷漏杀点 | 外层 finally 闭合方式 |
|---|---|---|---|
| A | stdin 断管 IOException :95 | finally :121-127 仅 cancel | process 非 null → 兜底销毁 |
| B | 中断 rethrow :128-130 | 同上 | 同上（MavenProject :22 生产调用点暴露面即此，timeout=0+redirectErrorStream，随批回归核对零改动） |
| C | collector 抛错 | 同上 | 同上 |
| D | waitFor(diff) 超时子路径 :112-116（timer 截止点晚于 diff 截止点，cancel 时 safeDestroy 未执行） | TIMEOUT 上报但进程存活 | 兜底销毁补杀 |
| E/F | scheduleTimeout :85 / collectError :90 抛 REE（内层 try 之前，C6-014 两面） | 无任何销毁路径 | process 非 null → 兜底销毁 |

**destroyForcibly 升级**：safeDestroy 内 destroy() 后有界等待（预算 2000ms 常量，不引入配置键）未退出再 destroyForcibly()；升级同时覆盖超时回调与外层 finally 两调用侧——忽略 SIGTERM 的子进程不再「上报已终止实际存活」。**共享 timer 阻塞代价声明与裁定**：超时回调侧 safeDestroy 运行在 `GlobalExecutors.globalTimer()` 共享调度池线程（scheduleTimeout :224-230；池 corePoolSize 默认 4，同为配置源刷新调度槽载体），最坏占用一条 timer 线程 2s（仅当已超时子进程还忽略 SIGTERM 的双重罕见路径）；影响面 = 该线程上其他周期任务的**触发时刻**延后 ≤2s（任务体经 executeOn 在 worker 执行，不受影响）。裁定 = **接受并声明**：把等待挪至调用方线程会使超时路径的 forcibly 升级失去承载（run 已返回 TIMEOUT），另开 worker 跳转为罕见路径增加机制面；2s 预算与 4 线程池容量下的 ≤2s 触发抖动属可接受量级，随批登记为 watch。 **stderr 线程退出链**：进程销毁 → error 流 EOF → readLine 返回 → 读取任务退出（:157 stopped=true 循环条件双保险）；collectError 线程有界收敛断言入回归，不再 pin 无界 cachedThreadPool 线程。 **N6 平台守卫**：新回归测试对 POSIX 特有面（trap 忽略 SIGTERM 探针）沿用 assumeTrue(!PlatformEnv.isWindows()) 形态（testRunWithInput :84 先例），进程存活探针用长时静默子进程 + @Timeout 防构建挂死；TestShellRunner 既有三方法零回归。 **C4-005 边界**：timeout=0 下 waitFor 无限阻塞在 try 内、外层 finally 不可达——兜底不解除也不恶化该已立案相邻缺陷（载体治理归后续批次）。

### 3.7 裁定⑦：协同边界声明

- **R2-04（roadmap 8.13）**：本批对 nop-ioc 容器内部（BeanContainerImpl/BeanScopeImpl）**零改动**（Phase 3 零触碰确认）；其异常上抛行为（BeanScopeImpl.close :110-120 末个 destroy 异常上抛、BeanContainerImpl.stop :610-620 转发）作为既有可观测行为消费——裁定② try/finally 与 forceUnload 尽力清理即消费方适配；子容器并发启动/启动失败传导、配置发布协议 holder 化（C1-007）均不属本批。
- **R4-15（roadmap 8.55）移交清单**：①C1-010 锁矩阵（3.1.2 全表 + 单向锁序）作为 C1-018/C1-019 修复的承载前提移交（名字索引/reload 窗口缺陷本体不越界修复）；②loadPluginFromJar lambda 锁纪律重构（C2-005）在裁定③ try 收窄后的形态上继续；③C10-004 共享条目按勘误 7 拆分——本批只承接 C1-010/011/012 成员面测试，C1-018/019/C2-005 测试随其本体归 R4-15；④updateConfig 分支已由裁定①判**随批纳入**，原预设的移交项不成立（merge-adjudication C1-020「与 C1-010 同批」条款兑现， cra-index 回填按此注记）。
- **R1-01/R1-02（plan 2496/2497）正交承诺**：tryTriggerChange per-callback 隔离、构造器 executeOn 卸载、queryTimeout/连接级超时三面零回退；本批在 nop-config 的净增面仅 close 互斥与构造序两项，且均在 R1-02 改造后的 live 形态上实施（worker 线程执行是裁定④互斥设计的线程背景、executeOn 调度行是裁定⑤的承载行）。
- **R1-05（plan 2500）变更判据消费口径（批前既有 drift 承接）**：`DefaultResourceChangeChecker.checkChanged` 的「mtime 相等但写入年龄 < 宽限期保守判 CHANGED」是模型缓存层重读策略（重读廉价、实例身份由缓存门承载）；插件 HMR `checkChangedAndReload` 的成文契约为「lastModified 相等不触发 reload」且 reload 为重量级迁移、定义对象身份对持有方可观测——该消费点改用同一载体的**纯比较入口** `isLoadMismatch`（单一载体原则维持，宽限语义不外溢到插件轨）。刚写入/刚加载的 plugin.xml 在宽限窗内不再伪重载；既有 `TestChangeDetection` 两用例为其回归（本批修复前基线即红，非本批引入）。

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|---|---|
| updateConfig 仅回调锁外、合并步留锁外 | 保留 C1-020 ACTIVATING 窗口丢更新与 C1-013 merge RMW 面，需额外 dirty-replay 机制补偿；锁内化以零额外机制结构性闭合两案（merge-adjudication C1-020 同批条款即默认） |
| deactivate 失败回退 ACTIVATED 语义 | 半停容器（effect 部分回退、bean 部分 destroy）谎报健康态，级联误判放大；FAILED + forceUnload 是诚实的失败语义 |
| 恢复 API 上 IPluginManager 接口 | 跨模块公共契约面扩张超出本批 PA 授权（框架核心引擎内部）；仓内无装配点现状下接口方法无真实调用方，实现类公共方法 + 既有 follow-up 升级通道已覆盖宿主暴露面 |
| 恢复 API 对 ACTIVATED 也强制受理 | 强销健康活容器绕过 deactivate 语义（effect 回退/容器 stop 的正常时序被跳过）；类型化拒绝引导走正常通道，forceUnload 定位为卡死态恢复 hatch 而非万能强杀 |
| C6-001 断言缝取 fd 计数差值替身（UnixOperatingSystemMXBean） | 平台守卫 + 容差噪声（其他线程 fd 抖动）、无法归因到具体 classLoader 实例、Windows 不可用；工厂钩子零平台依赖、实例级可归因、生产零变化 |
| C6-001 只把三个前置点塞进既有 catch | 既有 try :576 内已有一套 catch+finally 释放路径，二次嵌套不如 try 边界上移 + 单一 finally 统一（与 :591 兜底同构） |
| C1-009 取异步单飞重建形态（重建队列/独立锁原语） | 现有 this monitor 已覆盖 refreshConfig/tryTriggerChange 全部在途面，close 入 monitor 即全量串行；新原语无增量收益且扩大审计面 |
| C6-003 取 catch-cancel 形态 | 保留「先调度后加载」缺陷形态，靠 catch 记忆清理，未来新增失败点即复发；load-first 结构性消除（审计「最小改动」判语让位于形态正确性） |
| C6-004 兜底挂内层 try | C6-014 已钉死约束：:83-92 窗口（调度/提交拒绝）不 covered，唯外层 finally 覆盖（merge-adjudication 自主组 5 修复位置条款） |
| destroyForcibly 无预算直接升级 | 健康慢退出进程被剥夺 SIGTERM 优雅退出机会；2000ms 有界等待保留优雅退出窗口 |
| activate 容器 start 移出 lifecycleLock（「锁内重 I/O」优化） | 移出即重开 activate×deactivate×unload 交错窗（lifecycleLock 的存在理由）；生命周期为低频管理操作，锁内 I/O 是既有量级，reconcile 侧阻塞为既有行为 |

## 五、与已有设计的关系

- 同目录 [pool-facility-hardening.md](pool-facility-hardening.md)：裁定⑤保留的 `executeOn(globalWorker)` 卸载形态即其调度槽契约的消费；裁定④互斥设计的线程背景（任务体在 worker 执行）由其承载。
- 同目录 [periodic-task-insulation.md](periodic-task-insulation.md)：C1-021 per-callback 隔离体（plan 2496）为裁定④的零触碰面——回调隔离与 close 互斥同文件正交。
- 同目录 [kernel-retirement-generation-and-model-load-locking.md](kernel-retirement-generation-and-model-load-locking.md)：无共享代码位（kernel 退役代际 vs 本批插件/配置源/Shell 三面），测试纪律契约（§七）沿用其夹具纪律先例。
- **R2-04/R4-15**：边界与移交见 3.7；本批锁矩阵与 forceUnload 契约为两个后续批次的直接输入。
- **owner doc 去向预登记（Phase 3 落账）**：`docs-for-ai/02-core-guides/ioc-and-config.md` 新建「插件生命周期与配置源生命周期契约」节（双轨锁契约 + 状态机六态/失败终态/恢复 API + 配置源构造序契约，派生摘要标注「以本设计文档为准」）；`docs-for-ai/04-reference/source-anchors.md` 扩展 PLG 锚点系列（生命周期锁契约/状态机恢复 API/失败终态语义）+ 配置源构造序锚点。

## 六、成员 → 裁定映射

| 成员（判级/台账） | 面 | 裁定 | 修复位（live : 行） | 回归测试（预定名） |
|---|---|---|---|---|
| 2.2-C1-010（P1 主案） | unload/load 锁外迁移 × reconcile 交错孤儿容器 | ① | VfsPluginDefinition load :357-363/unload :365-377/start :549-563/stop :565-570；PluginManagerImpl unloadPlugin :144-165/activatePlugin :167-175/deactivatePlugin :176-185 | TestPluginLifecycleLockMigration |
| — ←C1-011（并案） | AbstractPlugin 三字段非 volatile + 同形锁外迁移 | ① | AbstractPlugin load :131-148/unload :150-164/start :312-324 + volatile 对齐 :69-76 | TestPluginLifecycleLockMigration（双轨对齐腿） |
| — ←C1-012（并案） | 复合读 7 站点 TOCTOU 异常漂移 | ① | 3.1.4 全表站点 | TestPluginLifecycleLockMigration（INACTIVE 类型断言腿） |
| — ←C2-004（并案） | 同窗镜像交错 | ① | unload 守卫读+写同锁（Vfs :365-377） | TestPluginLifecycleLockMigration（反序腿） |
| 2.2-C6-002（P1） | 中间态失败卡死 + 无恢复入口 | ② | Vfs doDeactivate :434-458/activate :408-422/rollbackActivation :507-522；AbstractPlugin :194-201/:236-242/:256-274；PluginManagerImpl forceUnload（新增） | TestPluginStateRecovery |
| 2.2-C6-001（P1） | 映射前失败 ClassLoader/JAR 句柄泄漏 | ③ | PluginManagerImpl loadPluginFromJar :567-596（try 收窄 + newPluginClassLoader 缝） | TestPluginLoadFailureResourceRelease |
| 2.2-C1-009（P1 主案，←C4-002） | close×在途刷新竞态连接泄漏 | ④ | JdbcConfigSource close :192-202/loadConfig :141-144 | TestJdbcConfigSourceCloseRace |
| 2.2-C6-003（P1） | 先调度后加载永久任务泄漏 | ⑤ | JdbcConfigSource 构造器 :55-76；AbstractFileConfigSource 构造器 :42-57 | TestConfigSourceConstructorLeak |
| 2.2-C6-004（P1 主案，←C6-014） | 非 timeout 路径不销毁进程 + stderr 线程 pin | ⑥ | ShellRunner run :73-143/safeDestroy :259-271 | TestShellRunnerProcessDestroy |

并案回归归属：C4-002 随 C1-009（TestJdbcConfigSourceCloseRace）、C6-014 随 C6-004（TestShellRunnerProcessDestroy 的 E/F 拒绝窗腿）；C10 成员面 = C10-001（裁定④⑤jdbc 面）/ C10-004 成员面（裁定①）/ C10-005（裁定②）/ C10-006（裁定③）/ C10-007（裁定⑥）/ C10-008（裁定⑤file 面），全部随对应工单同 diff。

## 七、测试纪律契约

夹具纪律沿用 kernel 先例 §七：daemon 夹具线程 + latch 对齐 + 有界观察窗 + @Timeout；禁仅类型/修饰符存在断言（「字段已 volatile」「方法已 synchronized」不构成通过形态），每件必含修复后可观测行为断言。

| 成员 | 修复前红形态（可观测） | 修复后绿形态（可观测） |
|---|---|---|
| C1-010 | 门闩对齐 unloadPlugin×reconcile 交错：unload 返回后 getState==UNLOADED 但容器仍 started（孤儿活容器探针）；或交叠窗计数 >0 | 终态单一：注册表无条目 ⇔ 容器已停；运行时串行化压力断言零交叠；deactivate 翻转窗消费方一律 INACTIVE（无混型） |
| C6-002 | MockStopControl.failStop 作用于 deactivate 段：异常后 getState 停留 DEACTIVATING，activate/unload/reload 全守卫 assertThrows 链拒绝 | deactivate 失败落 FAILED（非卡死）；forceUnload → UNLOADED → 重 load/activate 端到端可用；AbstractPlugin autoInit destroy 异常不跳过 CoreInitialization.destroy |
| C6-001 | 工厂钩子捕获实例：坏包 loadPlugin 失败后 isClosed()==false（句柄滞留）；重试再失败实例数增长且均未关闭 | 每次捕获实例 isClosed()==true、无累积；N2 既有断言原样全绿（零回改实证） |
| C1-009 | 停机撞在途刷新：close 后 connection 字段出现新建连接且无关闭调用（stub Connection 计数） | close 后 createConnection 不再被调用、字段连接已关闭；closed 守卫命中抛类型化异常（防退化腿） |
| C6-003 | 构造期 loadConfig 抛错（refreshInterval>0）：构造异常抛出后周期任务仍触发（刷新计数增长） | 失败构造零调度（jdbc+file 两站）；jdbc 变体已建连接已关闭；新测试显式走调度路径（不得以 refreshInterval=0 弱形态伪装，N7 禁令） |
| C6-004 | 长时静默子进程 + 运行中异常/中断：run 抛出后进程存活探针为存活、读取线程滞留 | 进程已销毁（含时序 D 与 E/F 窗）；destroyForcibly 升级路径对 trap-ignoring 探针生效（POSIX 守卫）；collectError 线程有界收敛 |

端到端两条链路（Phase 2 义务，rule 22）：①插件全生命周期——load → activate → 命令路由 → 失败注入（destroy 抛错）→ FAILED 落定 → forceUnload → UNLOADED → 重 load/activate → unload → 重载（恢复 API 从入口到可重载的完整状态机闭环）；②配置源全生命周期——构造成功 → 显式刷新 → 变更回调 → close → 连接关闭/任务取消/无泄漏。接线验证（rule 23）：forceUnload 在 PluginManagerImpl 公共面真实可达（经 manager 实例调用链断言，非仅定义存在）；unloadPlugin×reconcile 串行化在并发压力下零交错窗。无静默跳过（rule 24）：forceUnload 对 ACTIVATED 类型化拒绝而非 no-op；safeDestroy 兜底自身抛错留痕不吞（:268-270 既有 catch+LOG 保留）；closed 守卫命中抛类型化异常非静默 return；裁定③④⑤修正路径无空 catch。
