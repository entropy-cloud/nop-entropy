# 2506 CRA Phase 8.12 — R2-03 2.2 插件生命周期/配置源/Shell 批（6 项）

> Plan Status: active
> Draft Review: R1 agent_7260c86d @ 2026-10-26（0 Blocker、2 Major、5 Minor；基线 17 组 file:line 抽查零事实错误、2 处标签级 DRIFT）——M-1 裁定③补「C6-001 映射前失败路径断言缝裁定」（既有 `classLoaderOf` 探针从 holder 读、映射前失败恒 null 不可用；默认 = `PluginManagerImpl` package-private `newPluginClassLoader` 工厂钩子，备选 fd 计数替身）；M-2 裁定①补「lifecycleLock 持有期间禁止触发 onConfigChanged/reconcile」锁序硬规则（updateConfig 尾部回调 :589-592 = manager::reconcile 需 reconcileLock，与 unloadPlugin 持 reconcileLock→lifecycleLock 构成 ABBA 向量；回调锁外派发）；Minor×5（PluginErrors→PluginApiErrors/PluginManagerErrors、AbstractPlugin :313-322 为 aware 路径/兼容路径 :326-335、ServiceProxy 复合读窗补 invoke :63-67、±1~2 行漂移以 live 为准总声明覆盖、Phase 2 e2e/接线/无静默跳过三项镜像入 Exit Criteria）——修订全量应用，转 active
> Last Reviewed: 2026-10-26
> Source: roadmap 工作项 8.12（R2-03，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L154，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-03 行（L77：finding ID 清单 6 项 / PA 授权 / 依赖标注 / exit criteria 增补）；成员发现详情 = WI 2.2 审计目录 `ai-dev/audits/2026-10/2026-10-17-1725-deep-audit-nop-core-framework-concurrency/`（C1-shared-mutable-state.md / C6-resource-lifecycle.md / merge-adjudication.md / C10-test-protection.md，逐条回读路径见 Current Baseline）；执行基线 @ `1aafb2ccda`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.11 已 done，plan 2495–2505 均 completed；本 Baseline 全部行号为该基线 live 回读值，2026-10-25 亲读）
> Related: plan 2496（R1-01 绝缘层批，completed——C1-021 tryTriggerChange per-callback 隔离已在 JdbcConfigSource 同文件落地，本批 C1-009 同文件续作，正交边界见裁定④与非 Goals）；plan 2497（R1-02 池设施批，completed——JdbcConfigSource/AbstractFileConfigSource 构造器 executeOn 卸载 + JDBC 超时两面已落地，本批 C6-003 同站点续作，卸载语义不得回退）；plan 2504（R2-01，completed——`ioc-and-config.md`/`source-anchors.md` owner doc 更新先例）；plan 2505（R2-02，completed，前一批次）；**下游/并行协同**：R2-04（roadmap 8.13，无前置可并行——IoC 启动图/死锁 + 配置发布协议批，与本批的边界见 Non-Goals 与裁定⑦）、R4-15（roadmap 8.55，G31 束 17 项，**同 PR 协同后置**——C2-005 同 lambda 结构修复/C1-018/019 同锁域工单/C10-004 共享测试面的移交边界见裁定⑦与 Follow-ups）
> Protected Area 授权：本批触碰 **nop-core-framework 内部（nop-plugin 双轨生命周期 + nop-config 配置源）= 框架核心引擎 plan-first Protected Area**。授权来源 = AGENTS.md「Protected Areas」表框架核心引擎行 + batch-proposal §四.3 授权落批表（「框架核心引擎（nop-kernel/nop-core-framework 内部）plan-first（设计文档+回归测试）→ R1-01、R1-02、……、**R2-03**、R2-04、……」行）+ 本 plan Dependencies 声明：**设计文档为本批 Phase 1 产物（`ai-dev/design/concurrency/`），回归测试为各 Phase 强制义务（rule 25）**。plan-first 前置在 Phase 1 内闭环：设计文档落地并经独立 draft review 后，Phase 1 内实现项与 Phase 2–4 才可推进。成员 C6-004 站点在 `nop-utils/nop-shell`（impl 叶，非 Protected Area，随批作零 api 叶触碰声明）。本批不触碰 `nop-api-core`（io.nop.api.core.*）、不触碰 `model/*.orm.xml`、不触碰 nop-auth、不触碰 nop-ioc 容器启动图（归 R2-04，见 Non-Goals）。

## Purpose

关闭 WI 2.2（nop-core-framework）R2-03 批的 6 项已确认 P1 发现：①插件生命周期锁面统一（C1-010 主案，含 C1-011/012 并案、←C2-004 同窗镜像）——lifecycleLock 全迁移 + reconcileLock 序列化 + volatile 对齐 + 复合读双检，消除 unload×reconcile 交错的活容器永久泄漏；②插件状态机失败落定 + 恢复 API（C6-002）——DEACTIVATING/ACTIVATING 中间态 try/finally 收敛终态 + 显式恢复入口；③jar 轨加载失败点 ClassLoader/JAR 句柄泄漏（C6-001）——前置失败点纳入资源释放，并与测试 N2「unload 失败保留可重试」钉作两类失败终态的设计冲突消解；④JdbcConfigSource close×在途刷新竞态连接泄漏（C1-009，←C4-002 并案）——close 互斥 + closed 双检；⑤配置源构造器「先调度后加载」永久定时任务泄漏（C6-003，两站点）——构造序/失败清理修正，R1-02 卸载语义保持；⑥ShellRunner 非 timeout 异常路径不销毁子进程 + stderr 线程 pin（C6-004，←C6-014 并案）——safeDestroy 兜底置于外层 try finally。批末 roadmap 8.12 → done，6 项成员台账全部收敛。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-03 = 6 项成员（**P1×6**，无 P2 搭车、无族主吸收行）；cra-index P1 台账 6 行（`nop-core-framework-C1-009/C1-010/C6-001/C6-002/C6-003/C6-004`，裁定批次=R2-03，全部 open）；并案成员（C1-011/C1-012/C2-004→C1-010；C4-002→C1-009；C6-014→C6-004）按 merge-adjudication 裁定随主 ID 承载，其 P2 台账行随主案收敛；roadmap 8.12 行 Status=todo。
- 前置：无前置（可并行）。R1-00～R1-08（plan 2495–2503）与 R2-01/R2-02（plan 2504/2505）均 completed @ `1aafb2ccda`——本批启动的基线门禁已满足。
- **前批同文件产物（本批改动边界的前提事实）**：
  - plan 2496（R1-01）已在 `JdbcConfigSource` 落地 C1-021 修复：`tryTriggerChange` per-callback 隔离 + `callbackErrorCount` 抑制/恢复（live :111-139），回归 `TestJdbcConfigSourceCallbackIsolation` 4 条——本批 C1-009 修复不得回退该隔离体。
  - plan 2497（R1-02）已在 `JdbcConfigSource` 构造器落地 executeOn 卸载（live :65-66，globalTimer 只承载调度槽、刷新任务体 hop globalWorker）与 `loadConfig` 语句级 queryTimeout（live :155），`JdbcConfig` 连接级 connect/socket 超时键（live :33-62）；`AbstractFileConfigSource` 构造器同口径卸载（live :50-52）——本批 C1-009/C6-003 修复不得回退卸载与超时语义，回归 `TestJdbcConfigSourceWorkerOffload` 2 条 / `TestJdbcConfigTimeout` 7 条 / `TestFileConfigSourceWorkerOffload` 2 条 / `TestFileConfigSourceRefresh` 3 条 / `TestFileConfigSourceWalk` 1 条为零回归门禁。
- 测试资产与套件注记（C10 审计）：P1 六成员中 C1-009/C6-001/C6-002/C6-003/C6-004 为「无保护」、C1-010 为「部分保护」；验收前置 C10 条目 = C10-001（覆盖 C1-009/C6-003jdbc 面）、C10-004（覆盖 C1-010/011/012——**与 R4-15 共享条目**，成员面拆分见勘误 7）、C10-005（覆盖 C6-002；`MockStopControl.failStop` 桩现成）、C10-006（覆盖 C6-001；`classLoaderOf` 反射探针在位）、C10-007（覆盖 C6-004/C6-014；需按 N6 补平台守卫）、C10-008（覆盖 C6-003file 面；Scripted 桩注入构造期抛错 loader 即可）。全部按 E2「C10 验收前置与本体同 diff 补测」随各工单落地。测试套件自身缺陷注记 N1（TestReloadPlugin tearDown 丢弃 future、可吸收 C6-002 症状）/N2（unload 失败保留 classLoader 钉）/N6（TestShellRunner 平台守卫）/N7（TestFileConfigSourceRefresh refreshInterval=0 不触达调度与 close）中，N2 须与本批 C6-001 修复设计冲突消解（裁定③）、N6 随 C6-004 回归测试同步补平台守卫、N1/N7 为回归面陷阱登记（Follow-ups），N3/N4/N5 与本批无涉。

### 6 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `1aafb2ccda` 2026-10-25 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级） | 单元 / 模块 | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 2.2-C1-010（P1 主案；含 C1-011/C1-012 并案、←C2-004 同窗镜像） | 2.2 / nop-core-framework（nop-plugin） | `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/VfsPluginDefinition.java`：`unload` :365-377（守卫读 :369-374 + 锁外迁移写 :375-376）、`load` :357-363（锁外写 state/definitionConfig）、`start` :549-564（UNLOADED 分支锁外调 load）、`stop` :565-571（deactivate 锁内 + unload 锁外）、`activate` :379-424（lifecycleLock :381 内迁移，正面对照）、`getScope` :326-328 / `requireActiveScope` :651-657 / `requireActiveContainer` :659-665（复合读 TOCTOU）、`updateConfig` :573-59x（锁外 RMW，关联 P3 C1-020 面）；`PluginManagerImpl.java`：`unloadPlugin` :145-166（不持 reconcileLock；holder==null 静默 no-op :147）、`activatePlugin` :168-175 / `deactivatePlugin` :177-185（生命周期操作不持 reconcileLock）、`requireLoaded` :209-215（TOCTOU）、`reconcile` :226-243（reconcileLock，编排侧唯一持锁点）、`deactivateIfActive` :404-413（catch 吞异常不复位状态）；`AbstractPlugin.java`（nop-plugin-support）：state :75 / beanContainer :69 / definition :74 **非 volatile**、`getState` :94-98 无锁读、`load` :131-148 / `unload` :151-164 锁外迁移、`activate` :166-202（lifecycleLock :172）、`doDeactivate` :256-274、aware 路径 :313-322 / `start` 兼容路径 :326-335；`ServiceProxy.java` `invoke` :63-67 + `resolveTarget` :82-90（getScope 后 resolveTarget 同族复合读） | unload/load（及 start 的 load 步、stop 的 unload 步）在 lifecycleLock 外迁移状态且 unloadPlugin 不持 reconcileLock：管理卸载与配置驱动的 reconcile 自动激活正向交错（T1 守卫读 LOADED → T2 完整激活建容器 → T1 锁外写 UNLOADED+plugins.remove+关 classLoader）→ 子容器永远运行、状态机破坏、注册表不可达的孤儿活容器；并案面：AbstractPlugin 三字段非 volatile（C1-011，jar 轨可见性+同形锁外迁移）、(state, scope/container) 复合读非原子 7 处同族（C1-012，异常类型漂移） | **lifecycleLock 全迁移统一**（batch exit criteria 原文）：VfsPluginDefinition 与 AbstractPlugin 双轨的 load/unload（及 start/stop 内嵌步）全程进各自 lifecycleLock，迁移写不可分割；`PluginManagerImpl.unloadPlugin/activatePlugin/deactivatePlugin`（含 requireLoaded 校验段）统一纳入 reconcileLock 序列，与 reconcile 编排互斥；AbstractPlugin state/beanContainer/definition 升 volatile（对齐 Vfs 轨既有风格）；复合读按「取 scope/container 前后双检 state」统一模式覆盖 7 处同族；锁矩阵全表（含 updateConfig 与 P3 C1-020 的关联处置、C1-018/019 同锁域预留）由 Phase 1 裁定①一次定稿——防临场自加执行项 |
| 2 | 2.2-C6-002（P1） | 2.2 / nop-core-framework（nop-plugin） | 四处中间态无失败落定同族站点：`VfsPluginDefinition.doDeactivate` :434-458（:440 置 DEACTIVATING、:443-445 引用先置空、:456 LOADED 不可达——s.close()/c.stop() 任一抛错即卡死）、`VfsPluginDefinition.activate` catch :408-422（rollbackActivation :411 内 c.stop() :519 抛错则 :412 `state=FAILED` 被跳过）、`VfsPluginDefinition.rollbackActivation` :507-522、`AbstractPlugin.activate` catch :194-201（rollbackActivation :236-242 内 c.stop() 抛错则 state=FAILED 跳过）、`AbstractPlugin.doDeactivate` :256-274（含 autoInit→`CoreInitialization.destroy()` :268-271 异常时同样被跳过）。异常传播链上游：`BeanScopeImpl.close` :116-117 上抛末个 destroy 异常 → `BeanContainerImpl.stop` :616 原样上抛（nop-ioc，R2-04 领域，本批只作既有可观测行为消费）。守卫拒绝中间态三面：unload 守卫（Vfs :369-374 / AbstractPlugin :154-161）、activate 守卫（Vfs :386-391 / AbstractPlugin :177-181）、`reloadPlugin` 经 oldDef.unload() | 插件 deactivate/activate 失败路径状态机永久卡死 DEACTIVATING/ACTIVATING：单线程一次 destroy-method 抛错即确定性复现；scope/container/configProvider 引用在清理前已置 null，半停容器字段侧不可达；unload/activate/reload 全部守卫拒绝中间态、reconcile 的 deactivateIfActive 只吞异常不复位——任何公开 API 均无法把状态推离中间态，泄漏面=半停容器中未完成 destroy 的 bean 持有的线程池/连接 | try/finally 将中间态收敛为终态（失败落 FAILED 或按 Phase 1 裁定②定夺的终态语义）；清理完成后再置空字段（或保留至终态落定供诊断/恢复路径使用）；autoInit 的 `CoreInitialization.destroy()` 纳入失败保障；为 DEACTIVATING/ACTIVATING 提供显式恢复入口（**插件状态机恢复 API**——batch exit criteria 原文；形态与语义边界由裁定②定夺，在插件定义层实施，不重构 nop-ioc 容器启动/停止语义） |
| 3 | 2.2-C6-001（P1） | 2.2 / nop-core-framework（nop-plugin） | `PluginManagerImpl.loadPluginFromJar` :567-596——前置失败点三处全部在 try（:576）之外：`new PluginClassLoader(...)` :570-571（构造器内 `loadPluginConfig` 经 getResource 打开 jar 后可抛 `ERR_PLUGIN_NO_PLUGIN_CLASS_NAME`）、`classLoader.loadPlugin()` :574（ClassNotFoundException/ctor 异常）、`pluginConfigProvider.getPluginConfig(coords)` :575；catch 内 finally :591 `IoHelper.safeCloseObject(classLoader)` 仅覆盖 try 内失败。computeIfAbsent 失败不建映射 → 重试每次重建全新 classLoader，泄漏线性累积 | jar 轨插件加载失败路径泄漏 PluginClassLoader 与 JAR 文件句柄：坏包被周期性重试时句柄数与 Metaspace 单调增长，Windows 上打开的 jar 句柄锁住缓存文件 | 前置失败点纳入 try（lambda 内先声明 classLoader 局部变量、try 内赋值、失败路径 finally `IoHelper.safeCloseObject` 后再抛；`getPluginConfig` 一并纳入）；复用 `io.nop.commons.util.IoHelper.safeCloseObject` 与 :591 现有兜底同构。**与测试 N2 设计冲突消解**（batch exit criteria 原文，裁定③定夺）：本修复只覆盖「load-lambda 内、holder/映射建立之前」的失败点（lambda 局部资源、computeIfAbsent 不建立映射 → 失败即确定性释放）；`unloadPlugin` 失败路径保留 classLoader 的可重试设计**不动**（`TestPluginUnloadFailure#testUnloadFailureKeepsClassLoaderUsableAndRetryable` :111-140 的 `assertFalse(classLoader.isClosed())` 钉保持有效，零回改）——「映射前失败即释放」与「映射后失败保留可重试」两类失败终态在设计文档成文区分。**R4-15 协同**：本批不做 lambda 锁纪律重构（C2-005 的映射函数瘦身/锁外构建/putIfAbsent 回写归 R4-15，merge-adjudication 裁定 10 同 lambda 协同条款） |
| 4 | 2.2-C1-009（P1 主案；←C4-002 同位点并案） | 2.2 / nop-core-framework（nop-config） | `nop-core-framework/nop-config/src/main/java/io/nop/config/source/jdbc/JdbcConfigSource.java`（**审计行号已漂移，本列为 live 值，见勘误 3**）：`connection` :53（非 volatile）、`refreshConfig` :82-109（synchronized，R1-02 后任务体经 executeOn 在 globalWorker 线程执行）、`tryTriggerChange` :118-139（synchronized + per-callback 隔离，plan 2496 产物）、`loadConfig` 连接重建 :141-144（锁内 `createConnection`）、`closeConnection` :185-190（裸读写字段）、**`close` :192-202（不 synchronized；`closed=true` :194 → `closeConnection()` :195 → `cancel(false)` :200 不等待在途）** | close() 与在途刷新无互斥：停机撞在途刷新（T2 通过 closed 检查持锁进入 loadConfig → T1 close 置位+关连接+cancel → T2 判 connection==null 重建新连接完成本轮）→ 新连接写入字段后刷新任务已取消，**永不关闭**，每次「停机×刷新在途」泄漏一条直连 | close() 与 refreshConfig/tryTriggerChange 同 monitor 互斥 + closed 双检（close 后 loadConfig 不再 createConnection；或按裁定④定夺的等价单飞形态）；**不得回退** plan 2496 per-callback 隔离体（C1-021 回归 4 条零回归门禁）与 plan 2497 卸载/超时语义（卸载回归 2+2 条零回归门禁）；修复后任务体仍在 worker 池执行（R1-02 语义），仅互斥关系变化 |
| 5 | 2.2-C6-003（P1） | 2.2 / nop-core-framework（nop-config） | 两构造器「先调度后加载」（**审计行号已漂移，本列为 live 值，见勘误 3/4**）：`JdbcConfigSource` 构造器 :55-76（调度 :65-66 executeOn 形态 → `loadConfig` :72；:73-75 catch 抛 NopException 后任务无引用可关）；`AbstractFileConfigSource` 构造器 :42-57（调度 :50-52 → `loadConfig` :56）；子类 `KeyFileConfigSource` :23 / `PropsFileConfigSource` :26 直通 super 构造器；调用方 `ConfigStarter` :152-154（构造抛错时源未入 closeableSources）、`doStop` :526-551 仅关列表、`loadJdbcSource` :373-380。jdbc 变体附带：泄漏任务 DB 恢复后重建连接并永久持有（closed 恒 false）、DB 故障期间周期性连接风暴 | 配置源构造器先注册周期任务后执行可失败初始化：初始加载失败 → 对象半构造 → 已调度 refreshTaskFuture 无任何引用可 cancel → 永久周期任务泄漏于 JVM 静态 globalTimer（启动期降档不适用——泄漏窗口覆盖全运行期并跨 destroy→reinit 代际，审计复核论证成立） | 构造序/失败清理修正（先加载成功再调度，或构造器 catch 中抛出前 cancel——方案与两站点同口径由裁定⑤定夺）；若采「先加载后调度」，调度行保留 R1-02 的 `executeOn(globalWorker)` 卸载形态不回退；KeyFile/PropsFile 随 super 构造器一并覆盖；文件源变体同口径 |
| 6 | 2.2-C6-004（P1 主案；←C6-014 同 diff 并案） | 2.2 / **nop-utils（nop-shell）**——勘误 2 | `nop-utils/nop-shell/src/main/java/io/nop/shell/ShellRunner.java`：`run` :73-143——**外层 try :80 无 finally**（C6-014 修复位置约束：兜底必须置于外层 finally，内层 finally 不覆盖 :83-92 窗口）；进程创建 :83、safeDestroy 仅挂 scheduleTimeout 回调 :84-88、内层 finally :121-127 仅 cancel 两 future 不销毁进程；`collectError` :149-170（stderr 读取线程退出仅依赖 stopped 位或 EOF，进程不销毁则 `cancel(false)` 不中断 → 线程永久阻塞 readLine，pin 住无界 cachedThreadPool 线程）；`safeDestroy` :258-271（仅 destroy 无 destroyForcibly 升级）；四时序 A（stdin 断管 IOException）/B（中断 rethrow）/C（collector 抛错）/D（waitFor(diff) 超时子路径自身漏杀——timer 截止点晚于 diff 截止点，cancel 时 safeDestroy 通常尚未执行） | 非 timeout 异常/中断路径不销毁已创建子进程：每次泄漏一个可能仍存活的 OS 进程 + 三根管道 fd + 一个被 pin 死的 cachedThreadPool 线程（无界池线性放大）；连「超时路径自身」在时序 D 子路径上也漏杀，调用方收到 TIMEOUT 却进程实际存活 | safeDestroy 兜底置于**外层 try(:80) 的 finally**（←C6-014 同 diff，batch exit criteria 原文）：成功路径 waitFor 已返回、destroy 幂等无害；失败/中断/collector 抛错路径强制销毁；时序 D 子路径由外层 finally 一并覆盖；`safeDestroy` 追加 `destroyForcibly()` 升级（忽略 SIGTERM 的子进程不再「上报已终止实际存活」）；stderr 读取线程退出保障随兜底成立；C4-005（timeout=0 无限等待→finally 永不可达）为同文件相邻不同缺陷不入本批（载体治理 P3 相邻，见 Non-Goals）；生产调用点 `MavenProject` :22（redirectErrorStream(true)+timeout=0，暴露面=时序 B）随批回归核对 |

### 平台设施与测试资产盘点（live 实读 @ `1aafb2ccda`）

- **插件双轨载体**：VFS 轨 = `VfsPluginDefinition`（字段已全面 volatile :101-133，lifecycleLock :120）；jar 轨 = `AbstractPlugin`（同族字段非 volatile :69-75——双轨防护不对称，C1-011 面）。编排侧 = `PluginManagerImpl`（plugins CHM + reconcileLock 单飞 + reconcileInFlight/reconcileDirty 重入合并）。恢复/诊断可用既有件：`lastActivationError`/`activationFailures`/`autoActivationPaused`（:131-133，FAILED 态可读）。装配点事实（审计复核口径维持）：PluginManagerImpl 仓内无 beans.xml 装配点（仅测试实例化），宿主装配为预期但未落地用法——C6-001 的条件化标注随裁定③成文。
- **既有测试资产（回归门禁与测试基建）**：nop-plugin-manager 13 测试文件（`TestPluginLifecycle`/`TestPluginUnloadFailure`[N2 钉 + classLoaderOf 反射探针]/`TestReloadPlugin`[N1]/`TestPluginManager`/`TestReconcileTopologicalOrder`/`TestChangeDetection` 等）；nop-plugin-support 3 文件（`TestAbstractPlugin` 等）；nop-config source 面 5 文件（`TestJdbcConfigSourceCallbackIsolation` 4 条[plan 2496]/`TestJdbcConfigSourceWorkerOffload` 2 条+`TestJdbcConfigTimeout` 7 条[plan 2497]/`TestFileConfigSourceRefresh` 3 条[N7 注记]/`TestFileConfigSourceWalk` 1 条/`TestFileConfigSourceWorkerOffload` 2 条[plan 2497]）；nop-shell 3 文件（`TestShellRunner`[N6 平台守卫注记]/`TestShellCommand`/`TestShellLib`）。
- **可复用平台件**：`IoHelper.safeCloseObject`（C6-001 兜底同构）、`FutureHelper.syncGet`/`futureCall`（生命周期契约既有形态）、`NopException`+错误码（错误处理两档策略，新增错误码落 `PluginApiErrors`/`PluginManagerErrors`/`ConfigErrors` 既有错误族）。

### Baseline 勘误（输入不一致登记）

1. **任务书成员释文与权威台账错位（本 plan 以 batch-proposal + cra-index + 审计条目为准）**：起草任务书对六成员的释文中，C6-001 写作「BeanScopeImpl 哈希序销毁同站点」、C6-002 写作「computeIfAbsent bin 锁内 HTTP 下载/类加载/start」、C6-003 写作「TPE 池热更路径」——三者均与本批成员不符：①「BeanScopeImpl 哈希序销毁」= **2.2-C4-010**（←C6-006，cra-index L353 CL-018 落格行），归 **R4-15**；②「computeIfAbsent bin 锁内 HTTP 下载/类加载/start」= **2.2-C2-005**（cra-index L353 CL-002 落格行），归 **R4-15**；③「TPE 池热更路径/refreshConfig 类型化」= **2.1-C4-006** 收缩语义面，已由 plan 2497（R1-02）Phase 1 裁定⑤修复（`Default*Executor.refreshConfig` 类型化），非本批成员。权威释文以本 Baseline 六成员表为准（与 batch-proposal R2-03 行 exit criteria 增补、cra-index 六台账行、审计条目四方一致）。
2. **batch-proposal R2-03 模块列不完整**：列出的模块/验证命令仅 nop-core-framework；实测成员缺陷面触达 **nop-utils**（ShellRunner 在 `nop-utils/nop-shell`，nop-utils 为仓库顶层聚合模块，nop-shell 为其 `<module>` 子项）。本 plan 验证命令按实际 diff 触达补 `./mvnw test -pl nop-utils -am` 通道（Phase 3）；C6-004 站点为 impl 叶非 Protected Area，随批作零 api 叶触碰声明（同 plan 2497 勘误 1 先例）。
3. **JdbcConfigSource 行号漂移（R1-01+R1-02 两轮前批产物叠加）**：审计载 close :158-168 → live :192-202；审计载构造器 :49-65 → live :55-76（调度 :54 → live :65-66）；审计载 connection :47 → live :53；审计载 refreshConfig :71-108 → live :82-109；审计载 loadConfig 重建 :111-112 → live :141-144。成因 = plan 2496 C1-021（tryTriggerChange per-callback 隔离 + callbackErrorCount 字段插入）+ plan 2497 C4-001（构造器 executeOn 注释块与改写、loadConfig applyQueryTimeout 行插入）。缺陷本体零修复（close 仍不 synchronized、先调度后加载仍在），执行时以 live 回读为准。
4. **AbstractFileConfigSource 行号漂移（R1-02）**：审计载构造器 :42-51 → live :42-57（调度 :45 → live :50-52，executeOn 注释块插入）。缺陷本体（先调度后加载，:50-52 → :56）零修复。
5. **C6-005 残余面核对（零承接声明）**：2.2-C6-005（refreshConfig 变更分发在保护块外、监听器异常永久杀死刷新链）已由 merge-adjudication 裁定 cross-ref 并入 **C1-021**，而 C1-021 已由 plan 2496（R1-01）修复落地——live 回读确认 per-callback 隔离在位（`tryTriggerChange` :118-139，catch Throwable + callbackErrorCount<10 抑制 + 整轮成功清零）。本批**不承接** C6-005/C1-021 任何修复义务；唯一关联 = C1-009 修复（close 互斥）不得回退该隔离体，`TestJdbcConfigSourceCallbackIsolation` 4 条为零回归门禁（裁定④显式声明）。
6. **R1-02 修复面与本批的边界核对（残余面结论）**：plan 2497 已闭合的面——配置刷新任务体 globalTimer 占用（2.2-C4-001 卸载面，executeOn 在位）、JDBC 超时两面（queryTimeout + connect/socket 键在位）、2.1-C4-006 refreshConfig 类型化——本批均不重做；本批在 nop-config 的净增面仅 C1-009（close 互斥）与 C6-003（构造序/失败清理）两项，且均须在 R1-02 改造后的 live 形态上实施（executeOn 调度行是 C6-003 修正的承载行、worker 线程执行是 C1-009 互斥设计的线程背景）。
7. **C10-004 共享条目成员面拆分**：C10-004（插件生命周期锁面测试缺口）覆盖 C1-010/011/012（本批成员）与 C1-018/019/C2-005（R4-15 成员）——本批只承接 C1-010/011/012 成员面的验收前置测试（锁迁移+volatile+复合读双检的并发回归），C1-018/019 名字索引/reload 窗口与 C2-005 锁纪律面的测试归 R4-15 同其本体落地；C10-004 条目不整条判满，cra-index 回填时按成员面注记。
8. **owner doc 现状**：`docs-for-ai/02-core-guides/ioc-and-config.md` 现文无插件生命周期内容（grep「插件/plugin」零命中；现覆盖 IoC 规则/配置优先级/数据源绑定 + R2-01 增补的退役代际节）——本批 owner doc 更新为**新建节**而非更新既有节；`docs-for-ai/04-reference/source-anchors.md` 已有 `PLG-005`（IPluginManager 契约）/`PLG-007`（HttpPluginResourceResolver）/`PLG-009`（AbstractPlugin）等锚点条目——本批为**扩展既有 PLG 锚点系列**（生命周期锁契约/状态机恢复 API/失败终态语义），非新建条目族。
9. **测试套件注记处置去向**：N2 = 裁定③消解对象（钉零回改，两类失败终态成文）；N6 = 随 C6-004 回归测试同步补平台守卫（Phase 2 工单内义务）；N1（TestReloadPlugin tearDown 丢弃 future，可吸收 C6-002 卡死症状）/N7（TestFileConfigSourceRefresh refreshInterval=0 不触达调度与 close，覆盖声明误导）= 本批新增测试**不得复用**这两种弱形态（新测试显式 await/显式走调度路径），既有测试改写不入本批 scope（Follow-ups 登记，防越界扩批）。

## Goals

- Phase 1 设计文档一次裁决（plan-first 前置，独立 draft review 通过）：①C1-010 锁矩阵统一（lifecycleLock 全迁移范围、reconcileLock 序列化范围、AbstractPlugin volatile、复合读双检模式、updateConfig/C1-020 关联处置、C1-018/019 同锁域预留）；②C6-002 中间态失败落定终态语义 + 插件状态机恢复 API 契约；③C6-001/N2 两类失败终态消解；④C1-009 close 互斥方案（与 R1-01/R1-02 已落地语义正交）；⑤C6-003 构造序/失败清理方案（两站点同口径）；⑥C6-004 外层 finally 兜底语义（幂等 destroy + destroyForcibly 升级 + stderr 线程退出 + N6 平台守卫）；⑦与 R4-15/R2-04 的协同边界声明。
- 六项成员全部修复并各带回归测试（rule 25）：C1-010（lifecycleLock 全迁移统一，含 011/012 并案与 C2-004 镜像窗闭合）、C6-002（状态机收敛 + 恢复 API）、C6-001（前置失败点释放 + N2 冲突消解）、C1-009（close 互斥 + closed 双检）、C6-003（两站点构造序修正）、C6-004（外层 finally + destroyForcibly，←C6-014 同 diff）。
- C10 成员面验收前置随本体落地（E2 同 diff）：C10-001（C1-009/C6-003jdbc 面）、C10-004 成员面（C1-010/011/012）、C10-005（C6-002）、C10-006（C6-001）、C10-007（C6-004/C6-014，含 N6 平台守卫）、C10-008（C6-003file 面）。
- 关键工单先红后绿或变异/回退论证留痕（E2）：C1-010（修复前 unload×reconcile 交错红）、C6-002（destroy 抛错卡死红）、C6-001（前置失败点句柄滞留红）、C1-009（停机撞刷新连接重建红）、C6-003（构造失败任务滞留红）、C6-004（非 timeout 异常进程存活红）。
- Owner doc 随批更新：`docs-for-ai/02-core-guides/ioc-and-config.md`（新建插件生命周期与配置源生命周期契约节：双轨锁契约、状态机六态+失败终态+恢复 API、配置源构造序契约）、`docs-for-ai/04-reference/source-anchors.md`（扩展 PLG 锚点系列 + 配置源构造序锚点）。
- E1–E6 通用门禁全过（batch-proposal §一）：全量构建绿 / 每工单强制并发回归测试（先红后绿或变异/回退论证）/ 验证通道留证 / 授权声明 / owner doc 清单 / 批级独立 closure audit。

## Non-Goals

- **不做 R2-04（roadmap 8.13）的任何工单**：IoC 启动图/死锁批（2.2-C4-003 concurrent-start 三复合面[启动 bean 占 globalWorker+无 join 点+死容器 exit-0 伪装成功]、C2-001 getBean0 锁序环、C2-002 ProducedBeanInstance wait 环、C1-007 配置发布协议三字段单一不可变 holder、2.5-C1-007/C7-004/C1-010 刷新链下游面）**尚未执行**。本批边界：①插件状态机恢复 API（C6-002）只在插件定义层（nop-plugin-support/manager）实施，不重构 nop-ioc 容器启动图/stop 语义——`BeanContainerImpl.stop`/`BeanScopeImpl.close` 的异常上抛行为作为既有可观测行为消费；②子容器并发启动/启动失败传导语义归 R2-04，本批不改 `BeanContainerBuilder`/容器 start 路径；③配置发布协议（C1-007 holder）与配置源资源安全（C1-009/C6-003）为不同缺陷面，本批不做 holder 化改造。
- **不做 R4-15（roadmap 8.55）的任何工单**：C1-018/C1-019（插件注册表族，与 C1-010 同锁域——本批把锁域边界写清供其复用，不越界修复其缺陷本体）、C2-005（loadPluginFromJar 同 lambda 锁纪律重构——本批只做资源安全最小修复）、C4-010（BeanScopeImpl 哈希序销毁）、C4-009/C4-011、C10-004 的 R4-15 成员面。
- **不承接 2.2 相邻面**：C1-021/C6-005（plan 2496 已修，勘误 5 零承接）、C4-001/C4-002-C4-001 面（plan 2497 已修）、C4-005（ShellRunner timeout=0 挂起，载体治理 P3 相邻——外层 finally 修复不解除 timeout=0 下调用线程悬挂，C4-005 独立必要）、C4-010（R4-15）、C1-007/C1-017（R2-04）、C10-002/009/010/012 等非成员 C10 面（R4-15/R2-04 随其本体）、C6-010 三重登记面（cross-ref 归 WI 2.1 + 本单元 C4-004，均非本批）。
- 不触碰 `nop-api-core`（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth、nop-ioc 容器内部（`BeanContainerImpl`/`BeanScopeImpl` 改造归 R2-04，本批对其零改动并在 Phase 3 作零触碰确认）；不做 `_` 前缀生成物改动。
- 不做 Deferred/Follow-ups 节所列项（N1/N7 既有测试改写、插件状态机 quiescence 排空机制、ShellRunner 载体虚拟线程化等）。

## Scope

### In Scope

- `nop-core-framework/nop-plugin`（nop-plugin-manager + nop-plugin-support）双轨生命周期：`VfsPluginDefinition`/`AbstractPlugin`/`PluginManagerImpl`/`ServiceProxy` 的锁迁移、状态机收敛、恢复 API、loadPluginFromJar 资源释放 + 单元测试。
- `nop-core-framework/nop-config`：`JdbcConfigSource`（close 互斥 + 构造序）、`AbstractFileConfigSource`（构造序；executeOn 形态保留）+ 单元测试。
- `nop-utils/nop-shell`：`ShellRunner`（外层 finally 兜底 + destroyForcibly + stderr 线程退出）+ 单元测试（含 N6 平台守卫）。
- 设计文档：`ai-dev/design/concurrency/plugin-lifecycle-and-config-source-hardening.md`（裁定①–⑦，最终状态写法）。
- Owner doc：`docs-for-ai/02-core-guides/ioc-and-config.md`（新建插件生命周期/配置源生命周期契约节）、`docs-for-ai/04-reference/source-anchors.md`（PLG 锚点扩展）。

### Out Of Scope

- Non-Goals 所列全部项；R2-04/R4-15/R-G 及后续批次工单；C4-005 等相邻缺陷；N1/N7 既有测试改写。

## Execution Plan

### Phase 1 - 生命周期/配置源/Shell 三面设计裁定文档 + 独立评审（plan-first 前置）

Status: completed
Targets: `ai-dev/design/concurrency/plugin-lifecycle-and-config-source-hardening.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [x] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14：只写决策与契约，不写 Proposed-vs-Current、不写类签名代码），须含以下七项裁定：
  - [x] **裁定① C1-010 锁矩阵统一（本批核心裁定）**：lifecycleLock 全迁移的站点全集（VfsPluginDefinition.load/unload/start 内嵌 load 步/stop 内嵌 unload 步 + AbstractPlugin 同形站点双轨对齐）；`PluginManagerImpl.unloadPlugin/activatePlugin/deactivatePlugin`（含 requireLoaded 校验段）纳入 reconcileLock 序列的范围与锁序（reconcileLock 与 lifecycleLock 的获取顺序一次钉死为 **reconcileLock → lifecycleLock 单向**，防新锁序环）；**锁序硬规则（draft review Major-2 增补）**：lifecycleLock 持有期间**禁止**触发 `onConfigChanged`/`manager.reconcile`——`updateConfig` 尾部回调（live :589-592 `onConfigChanged.run()` = manager::reconcile → 需 reconcileLock）必须在 lifecycleLock 释放后派发（锁内只做配置快照与状态迁移，回调锁外调用），否则「unloadPlugin 持 reconcileLock 等 lifecycleLock」×「updateConfig 持 lifecycleLock 等 reconcileLock」构成 ABBA 环；设计文档锁序声明须显式复核该向量并列出全部「锁内可能外呼」的站点；AbstractPlugin state/beanContainer/definition volatile 化；复合读「取 scope/container 前后双检 state」统一模式及其 7 处同族站点清单（getScope/requireActiveScope/requireActiveContainer/updateConfig/invokeCommandAsync/ServiceProxy.resolveTarget/requireLoaded）；C2-004 镜像交错窗闭合声明（unload 守卫读+迁移写进锁后正反两序均不可达）；**updateConfig（P3 C1-020 面）处置预设两分支**：默认**随批纳入 lifecycleLock**（merge-adjudication C1-020 行「与 C1-010 同批」条款，顺带闭合 C1-013 残留义务，P3 面零成本顺带）；仅当设计裁定判**不纳入**时，显式声明留给 R4-15 锁域协同并在 cra-index 注记——两分支落地形态预设，无临场自加执行项空间；C1-018/019 同锁域预留声明（本批锁矩阵为 R4-15 的承载前提，不预实现其缺陷修复）。
  - [x] **裁定② C6-002 中间态失败落定 + 插件状态机恢复 API**：deactivate/activate 失败的终态语义（FAILED 落定 + lastActivationError 记因；deactivate 失败是否可回退 ACTIVATED 语义的取舍一次定夺）；字段置空时序（清理完成后再置空，或保留至终态供恢复路径）；autoInit→CoreInitialization.destroy() 的失败保障；**恢复 API 契约**（batch exit criteria 原文「插件状态机恢复 API」）：入口命名/语义（forceUnload 类：从 DEACTIVATING/ACTIVATING/FAILED 直达 UNLOADED 的显式通道，含半停容器的尽力清理与诊断留痕）、可见性（IPluginManager 公共 API 还是实现类方法——按装配点现状[仓内无 beans.xml 装配]与最小契约面定夺）、与 deactivate/unload 既有守卫的关系、恢复 API 不承担 IoC 容器 start 图修复（R2-04 边界）的显式声明。
  - [x] **裁定③ C6-001 资源释放与 N2 冲突消解**：前置失败点（ClassLoader 构造/loadPlugin/getPluginConfig）纳入释放路径的形态（try 收窄 + finally safeCloseObject，与 :591 既有兜底同构）；**两类失败终态成文区分**——「映射建立前失败 → lambda 局部资源确定性释放」vs「映射建立后 unloadPlugin 失败 → holder+classLoader 保留可重试（N2 钉语义维持零回改）」；与 N2 测试断言的兼容性核对表（修复后 `testUnloadFailureKeepsClassLoaderUsableAndRetryable` 全断言仍绿）；**断言缝裁定（draft review Major-1 增补）**：「映射前失败」路径 computeIfAbsent 不建映射 → 既有 `classLoaderOf` 反射探针（从 holder 读）恒 null 不可用，C10-006 回归测试须经裁定③授权的最小可测性缝观测——默认 = 在 `PluginManagerImpl` 引入 package-private 的 `newPluginClassLoader(...)` 工厂钩子（测试子类捕获实例后断言 `isClosed()`；生产行为零变化），备选 = `UnixOperatingSystemMXBean` fd 计数差值替身（须声明平台守卫与容差）；二选一在设计文档成文，Phase 2 不得临场自加其他缝；C6-011 同款「仓内无装配点」条件化标注统一成文；R4-15 C2-005 同 lambda 边界（本批不改 lambda 锁纪律结构）。
  - [x] **裁定④ C1-009 close 互斥方案**：close() 与 refreshConfig/tryTriggerChange 同 monitor 互斥 + closed 双检（close 后 loadConfig 不再 createConnection）或等价单飞形态（二选一，防重建窗）；与 plan 2496 per-callback 隔离体（C1-021）和 plan 2497 executeOn 卸载/queryTimeout 语义的正交性声明（修复后任务体仍在 worker 线程执行、隔离行为不变、既有 3+2+2+7 条回归零回退）；errorCount/callbackErrorCount 字段在互斥改造后的可见性核对。
  - [x] **裁定⑤ C6-003 构造序/失败清理方案（两站点同口径）**：「先加载成功再调度」vs「构造器 catch 抛出前 cancel」二选一并给出两站点（JdbcConfigSource/AbstractFileConfigSource）+ 两子类（KeyFile/PropsFile 直通 super）的统一落法；若采构造序调整，executeOn 卸载形态保留声明（R1-02 语义不回退）；jdbc 变体附带面（构造失败时已建连接的关闭）随方案一并闭合；与 C4-001 卸载面（已修）、C1-009 互斥面（同文件）的实施次序声明（防同文件三面互踩）。
  - [x] **裁定⑥ C6-004 外层 finally 兜底语义**：safeDestroy 置于外层 try(:80) finally 的幂等兜底（成功路径无害性论证：waitFor 已返回后 destroy 幂等）；时序 A/B/C/D 全覆盖声明（含 D「超时子路径自身漏杀」由外层 finally 闭合）；`destroyForcibly()` 升级条件（timeout 场景 destroy 后追加）；stderr 读取线程退出保障（进程销毁 → EOF → readLine 退出链）；N6 平台守卫随回归测试落地的形态；与 C4-005（timeout=0 挂起，不入本批）的边界声明。
  - [x] **裁定⑦ 协同边界声明**：与 R2-04 的边界（插件状态机恢复 API vs IoC 启动图；BeanContainerImpl/BeanScopeImpl 零改动承诺 + 本批对其异常上抛行为的消费口径）；与 R4-15 的移交清单（C1-018/019 同锁域复用口径、C2-005 同 lambda 结构修复、C10-004 共享条目成员面拆分表、updateConfig 分支若判不纳入的移交）；与 R1-01/R1-02 已落地语义的正交承诺。
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，本 Phase 后续实现项与 Phase 2 方可启动（plan-first 门禁）——记录于本 plan 头部 Draft Review 行或 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑦全部项，且独立 draft review 记录在案（plan 头部 Draft Review 行或 daily log）
- [x] 裁定①锁矩阵含锁序声明且与 reconcile 既有单飞语义兼容；裁定②恢复 API 契约含 R2-04 边界声明；裁定③含 N2 兼容性核对表；裁定④⑤含与 R1-01/R1-02 已落地语义的正交性声明；裁定⑥含时序 D 闭合论证
- [x] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

> **Phase 1 执行记录（2026-10-26）**：设计文档 `ai-dev/design/concurrency/plugin-lifecycle-and-config-source-hardening.md`（~207 行）落盘并登记 `design/concurrency/README.md`（层级表 + 阅读顺序第 8 位 + 实现锚）。独立 draft review R1（agent_6df99f00，fresh session，30+ 处 file:line 抽查零漂移）：**1 Blocker**（start :549-563 持 lifecycleLock 重入调 updateConfig，monitor 可重入致回调无法按「锁外派发」字面实现——补「回调延迟契约」：步进段返回变更标志、两受害点[外部入口/start 内嵌]均在最外层临界区退出后派发 + finally 派发保证 + 时序变化声明[start 路径回调自 activate 前移至 activate 后]）+ **2 Major**（forceUnload 在守卫零改动下无定义层载体——补双轨内部恢复方法契约与可见性[Vfs package-private / AbstractPlugin public 内部契约]；destroyForcibly 2s 等待占共享 globalTimer 线程代价未声明——补代价声明 + 接受裁定 + watch）+ 7 Minor 全部入文。R2 增量复核（同 agent）：三处闭合 PASS、无新 B/M、2 残留极轻项（start 异常退出派发保证、forceUnload 尾部 reconcile）已一句话落文。设计文档 Status → active。设计撰写子代理亲读登记的 Baseline 轻微偏差（unloadPlugin live :144-165 vs plan :145-166；ConfigStarter 入列 :133-137/:152-156；AbstractPlugin `lastActivationError` :76 亦非 volatile 纳入对齐面；IPluginManager 位于 nop-plugin-manager 非 nop-plugin-api）均以 live 为准、不改 plan 正文。`check-doc-links --strict` 退出码 0。零产品代码改动。

### Phase 2 - 六成员实现 + 单元测试（nop-core-framework 五成员 + nop-utils ShellRunner 一成员）

Status: planned
Targets: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/`（VfsPluginDefinition/PluginManagerImpl/ServiceProxy）、`nop-core-framework/nop-plugin/nop-plugin-support/src/main/java/io/nop/plugin/support/AbstractPlugin.java`、`nop-core-framework/nop-config/src/main/java/io/nop/config/source/jdbc/JdbcConfigSource.java`、`.../source/file/AbstractFileConfigSource.java`、`nop-utils/nop-shell/src/main/java/io/nop/shell/ShellRunner.java`、对应 `src/test/` 树（nop-plugin-manager/nop-plugin-support/nop-config/nop-shell）

- Item Types: `Fix`（六成员全部为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10 成员面验收前置）

- [ ] C1-010 实现（含 C1-011/012 并案、C2-004 镜像窗）：按裁定①实施 lifecycleLock 全迁移 + reconcileLock 序列化 + AbstractPlugin volatile + 复合读双检（updateConfig 分支按裁定①预设形态，无临场自加项）
- [ ] C6-002 实现：按裁定②实施中间态 try/finally 终态收敛 + 字段置空时序修正 + autoInit destroy 保障 + 状态机恢复 API
- [ ] C6-001 实现：按裁定③实施前置失败点释放（try 收窄 + finally）；N2 钉断言零回改
- [ ] C1-009 实现：按裁定④实施 close 互斥 + closed 双检（或裁定等价形态）；per-callback 隔离体与卸载/超时语义零回改
- [ ] C6-003 实现：按裁定⑤实施两站点构造序/失败清理修正（同口径）；executeOn 形态保留
- [ ] C6-004 实现：按裁定⑥实施外层 finally safeDestroy 兜底（←C6-014 同 diff）+ destroyForcibly 升级 + stderr 线程退出保障
- [ ] 回归测试（rule 25 显式覆盖 + C10 成员面验收前置同 diff，先红后绿或变异/回退论证留痕）：
  - [ ] C1-010（C10-004 成员面）：unload×reconcile 交错并发测试（修复前红：孤儿活容器/状态机破坏可观测断言——如 unload 后容器仍 start、getState 与容器实态不一致）；lifecycleLock 全迁移后 load/unload 与 activate 的互斥断言；AbstractPlugin volatile 化可见性冒烟（双轨对齐）；复合读双检的 deactivate 翻转窗异常类型断言（INACTIVE 快速失败而非 NOT_STARTED 混型）；PluginManagerImpl 生命周期操作与 reconcile 串行化断言
  - [ ] C6-002（C10-005；`MockStopControl.failStop` 桩复用）：destroy-method 抛错 → deactivate 落终态（非 DEACTIVATING 永久卡死）断言；activate rollback 失败 → FAILED 落定断言；恢复 API 从中间态/FAILED 态恢复到 UNLOADED 并可重新 load/activate 的端到端断言；AbstractPlugin.autoInit destroy 异常路径不跳过 CoreInitialization.destroy 断言
  - [ ] C6-001（C10-006；断言缝按裁定③授权形态——默认 package-private `newPluginClassLoader` 工厂钩子捕获实例）：缺 plugin.json 坏包 loadPlugin 失败 → 捕获的 classLoader 已关闭断言（`isClosed()`）；重试再失败 → 每次捕获实例均已关闭、无累积断言；N2 兼容：`testUnloadFailureKeepsClassLoaderUsableAndRetryable` 原样全绿（既有 `classLoaderOf` 探针仅用于 N2「映射后失败」腿）
  - [ ] C1-009（C10-001 成员面）：停机撞在途刷新交错测试（修复前红：close 后 connection 字段出现新建连接且永不关闭的可观测断言——stub Connection 计数）；close 后 loadConfig 不再 createConnection 断言；`TestJdbcConfigSourceCallbackIsolation` 4 条 + `TestJdbcConfigSourceWorkerOffload` 2 条 + `TestJdbcConfigTimeout` 7 条零回归
  - [ ] C6-003（C10-001 jdbc 面 + C10-008 file 面）：构造期 loadConfig 抛错（stub/staged loader 注入）→ 无残留周期任务断言（scheduled future 未注册或已 cancel 可观测）；jdbc 变体构造失败 → 已建连接已关闭断言；两站点同口径；`TestFileConfigSourceRefresh/Walk/WorkerOffload` 零回归（注意 N7：新测试不得以 refreshInterval=0 弱形态伪装调度路径覆盖）
  - [ ] C6-004（C10-007）：非 timeout 异常（stdin 断管/collector 抛错/中断）→ 子进程已销毁断言（Process stub 或存活探针）；时序 D waitFor 超时子路径 → 进程已销毁断言；destroyForcibly 升级路径断言；stderr 读取线程有限时退出断言（线程存活计数/ join 有界）；N6 平台守卫（非 Unix/Windows 兼容面跳过守卫形态）；`TestShellRunner`/`TestShellCommand`/`TestShellLib` 既有用例零回归
- [ ] **端到端验证**（rule 22）：≥1 条插件全生命周期链路测试（load→activate→命令路由→失败注入→恢复 API→unload→重载 的完整状态机闭环，覆盖裁定②恢复 API 从入口到可重载的完整路径）+ ≥1 条配置源全生命周期链路测试（构造成功→刷新→close→无泄漏 的完整路径）
- [ ] **接线验证**（rule 23）：恢复 API 在 PluginManagerImpl/宿主调用链上真实可达断言（不是仅定义存在）；C1-010 修复后 unloadPlugin 与 reconcile 的运行时串行化断言（并发压力下零交错窗）
- [ ] **无静默跳过**（rule 24）：恢复 API 对不可恢复形态显式抛类型化异常而非静默 no-op；C6-004 兜底失败（destroy 抛错）显式留痕不吞；C1-009/C6-003 修正路径无空 catch
- [ ] **提交纪律**：设计文档与实现分离提交（Phase 1 设计文档先落盘提交 → 独立 draft review → 实现按工单逐项提交，单会话超时可无损接续）；六工单按模块分组提交（nop-plugin 一组、nop-config 一组、nop-shell 一组），C10 成员面测试与对应本体**同一 commit**（E2 同 diff 核验依据）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 六成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 成员面验收前置（C10-001/004[成员面]/005/006/007/008）同 diff 落地
- [ ] 必红证据（E2）：六成员至少各一项先红后绿或变异/回退论证留痕（C1-010 交错红 / C6-002 卡死红 / C6-001 句柄滞留红 / C1-009 连接重建红 / C6-003 任务滞留红 / C6-004 进程存活红），快照落 `_tmp/` 并登记
- [ ] N2 兼容性实证：`TestPluginUnloadFailure#testUnloadFailureKeepsClassLoaderUsableAndRetryable` 修复后原样全绿（钉零回改）
- [ ] 既有回归零回退：plan 2496/2497 在 JdbcConfigSource/AbstractFileConfigSource 的全部既有测试 + nop-plugin 既有测试树 + nop-shell 既有测试树全绿
- [ ] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选（插件全生命周期链路 + 配置源全生命周期链路；恢复 API 运行时可达 + unloadPlugin×reconcile 串行化；恢复 API 不可恢复形态类型化抛错 + 兜底失败留痕）
- [ ] `./mvnw test -pl nop-core-framework -am` BUILD SUCCESS；`./mvnw test -pl nop-utils -am` BUILD SUCCESS（勘误 2 补充通道，以实际触达为准登记）
- [ ] Owner doc 裁定：ioc-and-config.md 新建节 + source-anchors.md PLG 锚点扩展的内容定稿归 Phase 3 统一落账（本 Phase 显式登记该去向）；否则显式写 `No owner-doc update required` + 理由
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: 受影响聚合模块全量测试（nop-core-framework / nop-utils）；`docs-for-ai/02-core-guides/ioc-and-config.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路：`-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2497 Phase 3 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-core-framework -am`、`./mvnw test -pl nop-utils -am` BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径）；实际触达模块以 git diff 为准逐条登记
- [ ] 零触碰确认：本批产品 diff 不含 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth、nop-ioc 容器内部（BeanContainerImpl/BeanScopeImpl——R2-04 领域）、`_` 前缀生成物路径（`git diff --name-only` 核对登记）
- [ ] Owner doc 更新：`ioc-and-config.md` **新建**插件生命周期与配置源生命周期契约节（双轨锁契约、状态机六态+失败终态+恢复 API 契约、配置源构造序契约——内容源 = Phase 1 设计文档裁定，派生摘要标注「以设计文档为准」）；`source-anchors.md` **扩展** PLG 锚点系列（生命周期锁契约/状态机恢复 API 锚点，PLG-005/009 受影响面更新）+ 配置源构造序锚点（如无既有条目则新建）
- [ ] 缺口声明：触达模块中无 `docs-for-ai/03-modules/` 条目者（nop-shell/nop-config 等，以实际 diff 为准）按 R-G 治理轨先例登记（plan 执行记录 + daily log），不在本批新建 owner doc
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-core-framework --severity high` 与 `--module nop-utils --severity high`（受影响模块全集与 Closure Gates 一致）退出码 0；若有批前既有发现，逐条裁定登记
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] E1 + 批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] ioc-and-config.md 新建节 + source-anchors.md 锚点扩展落地；check-doc-links --strict 退出码 0
- [ ] scan-hollow 受影响模块逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] 零触碰确认登记（含 nop-ioc 容器内部零改动）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.12 行、cra-index 台账

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（调用链运行时连通、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：P1 台账 6 行（C1-009/C1-010/C6-001/C6-002/C6-003/C6-004）状态收敛（open → 已修/本批）+ 并案成员行（C1-011/C1-012/C2-004/C4-002/C6-014）随主案注记 + C10-004 共享条目成员面注记（勘误 7）
- [ ] roadmap 8.12 行 Status → done
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2506-cra-phase8.12-r2-03-plugin-lifecycle.md --strict` 退出码 0；scan-hollow 全绿复核
- [ ] Closure Evidence 写入本 plan `## Closure` 段（rule 27）
- [ ] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 6 成员行状态全部收敛（并案行随主案注记）、roadmap 8.12 = done
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 6 项成员全部修复：C1-010（lifecycleLock 全迁移统一，含 C1-011/012 并案与 C2-004 镜像窗）、C6-002（状态机收敛 + 恢复 API）、C6-001（前置失败点释放 + N2 冲突消解）、C1-009（close 互斥 + closed 双检）、C6-003（两站点构造序修正）、C6-004（外层 try finally 兜底 + destroyForcibly，←C6-014 同 diff）
- [ ] batch-proposal R2-03 行 exit criteria 增补四条逐条达成并留证：C6-001 与测试 N2 设计冲突消解（N2 钉零回改实证）；C6-004 外层 try finally；C1-010 lifecycleLock 全迁移统一；插件状态机恢复 API（端到端恢复链路测试在案）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；六成员均有先红后绿或变异/回退论证（E2）；C10 成员面验收前置同 diff 落地（C10-004 仅成员面，R4-15 面未越界承接）
- [ ] 设计文档裁定①–⑦成文且经独立 draft review；R1-01/R1-02 已落地语义（per-callback 隔离、executeOn 卸载、JDBC 超时）零回退实证（既有回归全绿 + diff 抽验）
- [ ] R2-04/R4-15 边界守约：nop-ioc 容器内部零改动、C2-005/C1-018/019 缺陷本体未越界修复、移交清单在案（裁定⑦）
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-core-framework -am`、`./mvnw test -pl nop-utils -am` BUILD SUCCESS（E1/E3）
- [ ] Owner doc 更新完成：ioc-and-config.md（新建契约节）、source-anchors.md（PLG 锚点扩展）；缺口声明登记（E5）
- [ ] 受影响模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外/相邻治理项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）恢复 API/互斥/兜底路径运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile`（受影响模块）
- [ ] `./mvnw test`（受影响模块）
- [ ] checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2506-cra-phase8.12-r2-03-plugin-lifecycle.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 6 成员行收敛 + roadmap 8.12 → done（E6）

## Deferred But Adjudicated

### C4-005（ShellRunner timeout=0 无限等待 → stderr 线程驻留）载体治理

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 同文件相邻**不同缺陷**（C6-004 族案的外层 finally 修复不解除 timeout=0 下调用线程悬挂——finally 永不可达，须载体层[虚拟线程/独立小池]独立修复）；判级载体治理 P3 相邻，审计失效面⑤显式声明不入本批；仓内唯一生产调用点 MavenProject timeout=0 的暴露面（时序 B）已由本批 C6-004 兜底覆盖非超时路径。
- Successor Required: `yes`
- Successor Path: 载体治理归属批次以 cra-index 为准（P3 相邻面；若 R-G 治理轨/后续立项承接，经 backlog 对账）；cra-index `2.2-C6-004` 行注记

### N1/N7 既有测试弱形态改写（TestReloadPlugin tearDown 丢弃 future；TestFileConfigSourceRefresh refreshInterval=0 不触达调度）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 两者为测试套件自身注记（非产品缺陷）；本批义务 = 新增测试不复用弱形态 + 新测试显式 await/显式走调度路径（Phase 2 工单内已承载）；既有测试改写属批外测试治理，且 N1 的 tearDown 形态在本批 C6-002 卡死修复后不再有「吸收症状」的现行危害（状态机不再卡死）。
- Successor Required: `no`（R-G 测试基建治理轨登记即可）

### updateConfig 进 lifecycleLock（P3 C1-020 面）——仅当 Phase 1 裁定①判「不纳入」时成立

- Classification: `watch-only residual`
- Why Not Blocking Closure: 裁定①若判「随批纳入」则该项随 C1-010 本体落地、不入本节（merge-adjudication C1-020 行「与 C1-010 同批」条款为默认）；若判「不纳入」则残留面已由锁矩阵显式声明归属 R4-15 锁域协同，静默窗口不存在（C1-020 为 P3 归档面，非 live defect 立项）。
- Successor Required: `no`（R4-15 同锁域协同承载）

## Non-Blocking Follow-ups

- **R4-15 同 PR 协同交接清单**（batch-proposal R2-03 依赖列「R4-15（G31）同 PR 协同后置」的落点）：①C1-010 锁矩阵（reconcileLock/lifecycleLock 锁序与持锁方法全集）作为 C1-018/C1-019 修复的承载前提移交；②loadPluginFromJar lambda 的锁纪律重构（C2-005：映射函数瘦身+锁外构建+putIfAbsent 回写）在本批 try 收窄后的形态上继续；③C10-004 共享条目的 R4-15 成员面（C1-018/019/C2-005 测试）随其本体落地；④updateConfig 分支若裁定①判不纳入则随本清单移交。交接载体 = Phase 1 设计文档裁定⑦ + cra-index 对应行注记，可独立核验。
- **R2-04 上游联动注记**：本批 C6-003 修正后的配置源构造序与 R2-04 的 C1-007 配置发布协议（三字段单一不可变 holder）共享 nop-config 域——R2-04 起草时按本批 Phase 1 设计文档裁定⑦的边界声明对齐（本批不做 holder 化）。
- **插件状态机 quiescence（请求排空）机制**：C1-012 复合读双检仅消除异常类型漂移，不提供 deactivate 前的在途请求排空（audit 建议的 awaitQuiesce 为独立机制）——本批不做，若宿主装配落地后出现实测需求经 backlog 立项。
- **restore/恢复 API 的宿主暴露面**：仓内 PluginManagerImpl 无 beans.xml 装配点（审计复核口径），恢复 API 的宿主可见性按裁定②最小契约面定夺；宿主装配落地时如需升级为公共 API，按 `nop-api-core` 之外的模块公共面流程另行走查（非本批义务）。

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<如用子 agent，记录 session ID>>
- Evidence: <<每条 Exit Criterion 的验证结果（PASS/FAIL + 对应的 live code path 或 test name）；每条 Closure Gate 的验证结果；check-plan-checklist --strict 退出码；Anti-Hollow 检查结果；Deferred 项分类检查>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>
