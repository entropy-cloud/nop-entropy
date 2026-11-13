# nop-core-framework 并发·异步·资源专项审计 — 跨维度合并裁定（merge-adjudication）

- **裁定人**: 跨维度合并裁定子代理（独立于 4 维度初审/复核子代理，以证据为准）
- **裁定日期**: 2026-10-17
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`，HEAD `6dceb20dc3`）
- **三态判定规则**: 同一缺陷（同一代码位点 + 同一根因 + 同一后果 → 合并保留最高严重度，登记主 ID 与被并 ID）；同族相邻（互列关联，独立计数）；不同缺陷（独立成立）。
- **方法**: ① 逐对打开 4 维度发现原文比对位点/根因/后果三要素；② 对拟判「同一缺陷」的 6 组关键候选做 live code 亲核（ProducedBeanInstance.runUntil/waitQuietly、BeanContainerImpl.getClassIntrospection 与生命周期字段、JdbcConfigSource.close/refreshConfig/tryTriggerChange、InjectRefValueResolver.collectDepends）；③ 复核轮已裁的 cross-ref 项仅做台账登记不重复裁定；④ 对照 `ai-dev/audits/cra-index.md`（WI 1.1–2.1）与 C10 保护力矩阵做家族归组与跨单元映射。

---

## 一、输入口径

| 维度 | 保留 | 驳回 | P1（族案条目） | P2 | P3 | 来源节 |
|---|---|---|---|---|---|---|
| C1 共享可变状态 | 19 | 2（C1-003、C1-013） | 5 条 ID / 4 族案（C1-007+017、C1-009、C1-010+011/012、C1-021） | 10 | 4 | `C1-shared-mutable-state.md` §最终保留项/§维度复核结论（含 round2/round3） |
| C2 锁与同步 | 15 | 2（C2-014、C2-015） | 2（C2-001、C2-002） | 8 | 5 | `C2-lock-sync.md` + `C2-round2.md` |
| C4 线程池治理 | 11 | 0 | 1（C4-003，门控） | 6 | 4 | `C4-thread-pools.md` + `C4-round2.md` §最终保留项（复核终版） |
| C6 资源生命周期 | 14 | 0 | 4（C6-001/002/003/004） | 9（含 3 条 cross-ref 待并） | 1 | `C6-resource-lifecycle.md` + `C6-round2.md` |
| **合计** | **59** | **4** | **11 族案条目**（12 条 ID） | **33** | **14** | |

- 复核轮已裁、本裁定仅登记不重复裁定的项：**C1-017→C1-007 族案**、**C1-011/C1-012→C1-010 族案（并案）**、**C6-005→C1-021 cross-ref**、**C6-006→C4-010 cross-ref**、**C6-010→C4-004 + WI 2.1 三重登记 cross-ref**、**4 条驳回（C1-003/C1-013/C2-014/C2-015）**。
- C10（缺保护 C10-001~018 + N1~N7）不参与合并裁定主体；其家族归组（如 C10-016 将 C2-003/C1-001/C1-005 归同一测试目标族）作为本裁定的旁证引用。

---

## 二、必裁交叉项逐一裁定

### 裁定 1：C1-010（含 C1-011/012 并案）↔ C2-004 / C2-006（插件状态机锁外迁移双镜头）

| 配对 | 判定 | 依据 |
|---|---|---|
| **C2-004 ↔ C1-010 族案** | **同一缺陷 → 并入 C1-010 族案** | 三要素逐项重合：①位点同一——均为 `VfsPluginDefinition.unload`（L366-377 无锁守卫读+迁移写）× `activate`（L380-424 lifecycleLock 内迁移）× `PluginManagerImpl.unloadPlugin`（L145-165 不持 reconcileLock）的同一 check-then-act 窗口，双方各自的触发时序（C1-010 的「unload 守卫读先落→activate 完成后迁移写」与 C2-004 的「activate 已进锁过守卫→unload 抢先写 UNLOADED+摘 holder」）是**同一窗口的两个镜像交错序**，终态同为孤儿活容器（注册表无条目 ⇔ 容器活跃、不可经任何入口停止）；②根因同一——load/unload/start/stop/updateConfig 锁外迁移 + manager 生命周期操作不入 reconcileLock；③修复载体同一——lifecycleLock 全迁移 + reconcileLock 序列化。C2-004 的第二面（AbstractPlugin.state 非 volatile）恰为 C1-011 已并面。C2-004 的两处事实修正（unloadPlugin 对 holder==null 静默 no-op；volatile 面系 4ffaebb267 历史修复）随并入移入主案备注。 |
| **C2-006 ↔ C1-010 族案** | **同族相邻 → 独立保留（P3），互列关联** | C2-006 自身复核终裁「无独立于 C2-004 的可达损坏」——其唯一 damage 面（快照过期正交错 → 孤儿激活）即 C1-010 序列，随并入消失；保留的独立价值 = ①锁矩阵契约钉死（防后续维护者在 doReconcile 新增写操作引入新窗口）、②`activateIfInactive` catch(RuntimeException\|Error) 静默记日志的结构化拒绝语义缺失。此二者在 C1-010 修复（reconcileLock 序列化）落地后仍是独立的防回归/可观测性义务，不构成同一缺陷。 |

### 裁定 2：C2-005 ↔ C2-014（已驳回，仅残余面归并）

**裁定：C2-014 驳回维持，无并入计数。** 复核已证伪其环闭合第二腿（aware 轨映射函数仅 load、非 aware 轨 build 不 start，均不持 LC）。残余真面归并登记：① LC(P) 持有期间 activator 用户代码回调 manager API 对 BIN/RLOCK 的**阻塞**（非死锁）→ 归 C2-005 的结构性修复首选（映射函数瘦身：双检+锁外 start+putIfAbsent，BIN 不再跨用户代码，环自然断开）；② 矩阵契约面 → 归 C2-006。C2-005 独立保留 P2。

### 裁定 3：C1-007（含 C1-017）↔ C4-007 / C4-008（配置刷新链：发布面 vs 执行载体面 vs 回调吞错面——三态逐对）

| 配对 | 判定 | 依据 |
|---|---|---|
| **C1-007 族案 ↔ C4-007** | **不同缺陷（同刷新链相邻，互列关联）** | 位点不同（`DefaultConfigReference` 三字段发布协议 + applyChangeToUsed 写路径 vs `ChangeSubscriptions.trigger` 内联执行 + `BeanDefinition.onRefreshConfig` 用户方法链）；根因不同（无 happens-before 的裸字段发布 vs 单线程执行载体无超时/无慢路径观测）；后果不同（值可见性无界/写-写竞争/撕裂 vs 阻塞搁浅全平台配置传播）。修复互不覆盖：C4-007 的慢监听器计时/超时隔离不闭合 C1-007 的可见性面；api-core holder 化不消除监听器阻塞面。 |
| **C1-007 族案 ↔ C4-008** | **不同缺陷（无合并、无同链关系）** | C4-008 属 nop-ioc concurrent-start 启动图完成回调（whenComplete 汇聚点），与配置发布协议分属不同子系统，仅「回调吞错」形态同构。C4-008 的真正归属裁定见下（并入 C4-003 族案）；其「回调无兜底」形态与 C1-021（JdbcConfigSource 回调杀链）为跨站点形态同族，登记形态级关联（平台级「回调兜底纪律」治理时可一并审视三站点：whenComplete 汇聚点 / tryTriggerChange / ChangeSubscriptions.trigger——三者后者已设防）。 |

**C4-008 另案裁定（同裁定 3 范围内）**：**C4-008 → 并入 C4-003 族案（同一缺陷）**。三要素重合：①位点同一——同为 `BeanContainerImpl` L502-512 whenComplete 汇聚点 + L526-539 handleStartError/awaitStartFinished；②根因同一——失败/完成语义唯一汇聚点无兜底 + 框架级无 join 点（C4-008 的三形态 F2/F5/F4-async 正是绕过 handleStartError 的路径，C4-round2 明言其为「C4-003(b)(c) 的机制深化」；C4-003 现状栏已预见 syncGet 无超时 F5 面）；③后果同族——启动失败不可见/容器 limbo/进程带死容器存活。C4-003 为主案（P1，门控终裁维持，P1 由无 join + 失败不传导承载），C4-008 并入为失败形态完备性扩展（Error 孤儿/成功回调零日志/无超时 pending 三形态为修复验收面）；设施根因（TaskExecutionGraph.runTask catch(Exception)）维持 CL-019 移交 nop-kernel 不变。

### 裁定 4：C1-009 ↔ C4-002（JdbcConfigSource close 竞态——双登记同位点逐对判定）

**裁定：同一缺陷 → C4-002 并入 C1-009（主 ID = C1-009，P1）。** live code 亲核（JdbcConfigSource L71-98/L100-108/L158-168）：两案位点同一（`close()` 非 synchronized vs `refreshConfig`/`tryTriggerChange` synchronized；`connection` 裸字段 L47；loadConfig L111-112 锁内重建）；交错序列同一（timer 在途刷新通过 closed 检查 → 停机线程 close 置位+关连接+cancel(false) → 在途刷新 catch 后 loadConfig 重试 → createConnection 新建连接写入字段 → 无人再关）；后果同一（每次「停机撞在途刷新」泄漏一条永不关闭的直连）。C4-002（P2）为主案的 P2 复述 + 同站点扩展表述，按最高严重度并入 P1 主案。
**次级面切分**：C4-002 的次级面（`tryTriggerChange` 在 try 外、onChanges 循环无守卫 → F9 杀链形态）**归 C1-021**（该站点该面的既定主案，C6-005 cross-ref 同此），不随 C4-002 并入 C1-009——C1-009 的修复（close synchronized 化 + closed 双检）不覆盖回调守卫面，两修复载体不同。

### 裁定 5：C6-005 / C6-006（复核轮已裁）——台账登记确认

- **C6-005 → cross-ref 并入 C1-021（P1）**：确认。同站点（JdbcConfigSource.tryTriggerChange），C6 侧两条触发时序已被复核推翻，判级随 C1-021；F3④（任务循环无逐任务隔离）已被 C1-021 修复建议（per-task 隔离）覆盖。
- **C6-006 → cross-ref 并入 C4-010（P2）**：确认。同位点（BeanScopeImpl.close CHM 哈希序销毁），修复载体统一为 topoIndex 逆序销毁；C6-006 的 F3②（BeanScopeContextImpl.onContainerStop 集合间任意序）作为次级面随 C4-010 修复覆盖。

### 裁定 6：C6-003 ↔ C4-001 / C4-002（config source 调度族三面）

| 配对 | 判定 | 依据 |
|---|---|---|
| **C6-003 ↔ C4-001** | **不同缺陷（同调度点相邻，修复协同）** | C6-003 = 构造器「先调度后加载」的失败路径泄漏（调度成功 → loadConfig 抛 → 任务无引用可关，永久周期任务 + jdbc 连接滞留）；C4-001 = 运行期载体选择缺陷（无超时阻塞 I/O 直连 4 线程共享 globalTimer → 平台定时器面退化）。窗口不同（构造期单次 vs 运行期周期）、后果不同（泄漏 vs 占死放大）。修复协同：两者均触及同一构造器/scheduleWithFixedDelay 调用点，按「先加载成功再调度（C6-003）+ executeOn/SPI 卸载 + 超时（C4-001）」同 diff 实施。 |
| **C6-003 ↔ C4-002** | **不同缺陷** | C4-002 已并入 C1-009（裁定 4）；C6-003 与 C1-009 同文件不同窗口不同后果（构造期调度泄漏 vs 停机 close 竞态连接重建泄漏），修复载体不同（构造序调整/catch cancel vs close synchronized 化）。互列相邻。 |

### 裁定 7：C6-010（三重登记）——台账确认

**裁定：确认复核处置，C6-010 → cross-ref 并入（本单元统计不计独立条目）。** 归属切分：面 1（初始化器无 destroy 覆写 + 不注销）→ 本单元 **C4-004**（使用侧载体，主 ID）；面 1 的设施本体、面 2（重初始化孤儿任务/register 覆盖 old.destroy 无等待）、面 3（destroy 仅 shutdown 无 awaitTermination）→ **WI 2.1 nop-kernel C6-002/C6-003**（设施本体，其 round2 已将 Jdk21CommonsCoreInitializer 僵尸窗口并入 C6-003 F3）。C6-010 独立增量 = F3 全族清单（⑥ GlobalExecutors.register 覆盖式关闭无等待面）随 C4-004/设施案作为修复输入。判级归属：C6-010 的 P2 由设施本体条目（kernel C6-002/003 P2）承载；本单元侧 C4-004 维持 P3（模块门控——nop-commons-java21 不在根 pom 默认 modules，判级论证成立），不因吸收而升档（缺陷本体计数主体在设施单元，见 §五映射表第 6/7 行）。

### 裁定 8：C1-018 / C1-019 ↔ C1-010（插件注册表操作族——三态逐对）

| 配对 | 判定 | 依据 |
|---|---|---|
| **C1-018 ↔ C1-010** | **不同缺陷（同模块注册表族相邻）** | 数据结构不同（`namesToPluginIds` 名字索引 vs def 状态机/plugins 注册表）、交错对不同（register/unregister 三步 vs unload×reconcile）、后果不同（requires 门控静默失效+级联误去激活 vs 孤儿活容器+状态机破坏）、修复不同（computeIfPresent 原子化 vs lifecycleLock+reconcileLock 序列化）。关联：C1-018 修复「纳入 reconcileLock」与 C1-010 修复同锁域，同批实施。 |
| **C1-019 ↔ C1-010** | **不同缺陷（同修复批次相邻）** | 交错对不同（reloadPlugin remove→put 窗口 × loadPlugin computeIfAbsent vs unload×activate）、后果不同（双活定义+订阅清理丢失 vs 孤儿活容器）。关联：C1-010 修复建议「loadPlugin 纳入 reconcileLock 序列」**同时闭合** C1-019 的窗口（reload 持锁 + load 入锁 → remove→put 段对 load 不再开放），修复协同登记，但缺陷本体独立（后果与验收面不同）。 |

### 裁定 9：C2-007 ↔ C1-008（配置订阅/应用面——不同类判定）

**裁定：不同缺陷。** C2-007 = `DefinitionConfigProvider.subscribeChange` 清理闭包的 check-then-act → 孤儿 list → 该 pattern 监听器**永久失聪**（F9 静默、不可自愈）；C1-008 = `DefaultConfigProvider.applyChange` 双 volatile 代际窗 + usedRef 逐键更新 → 跨键读**撕裂**（瞬态、自愈）。不同类、不同协议（订阅生命周期 vs 刷新发布代际）、不同后果方向。另注：C1-013（驳回）残留的 updateConfig merge 原子性义务在 C1-020 修复批次跟踪；C2-016（DefinitionConfigProvider.updateConfigValue 两段锁）与本对亦互不隶属。三者同属「配置订阅/应用生命周期」家族，互列关联。

### 裁定 10（必裁内延伸）：C1-018/C1-019 之间的自关系 + C2-005 ↔ C6-001

- C1-018 ↔ C1-019：不同缺陷（名字索引孤儿化 vs 注册表槽位窗口；前者后果=门控失效、后者=双活定义）；同批次同锁域修复协同。
- C2-005 ↔ C6-001：**不同缺陷（同 lambda 修复协同）**。C2-005 = computeIfAbsent 映射内长临界区/递归面（锁纪律）；C6-001 = L571-575 前置失败点 PluginClassLoader/JAR 句柄泄漏（资源生命周期）。C2-005 结构性修复（映射函数瘦身+锁外构建+putIfAbsent 回写）重构同一段 lambda，实施时须与 C6-001 的 try 收窄一并设计（竞争失败方关闭自建 classLoader）。

---

## 三、自主补充裁定组（5 组，含 live code 亲核）

> 以下均为必裁清单之外的跨维度/同维度重复，经 live code 亲核后裁定。

### 自主组 1：C1-002 ↔ C2-002 —— **同一缺陷 → C1-002 并入 C2-002（主 ID = C2-002，P1）**

- live code 亲核：`ProducedBeanInstance.runUntil`（L118-146）`initThread != null` 分支仅做同线程 `selfWait()` 检查，跨线程落入 `waitQuietly()`（L194-201）——裸 `wait()`，无超时、无跨实例环检测。两案位点（ProducedBeanInstance.java:114-170 + BeanContainerImpl.java:406-434/433）、机制（concurrentStart 下两线程互为对方 INIT owner，经 initBean 动态 getBean 交叉等待）、后果（永久互等死锁，无自愈）完全同一。
- 主 ID 取 C2-002：P1（复核终裁维持、逐步交错亲核）高于 C1-002 的 P2；且 C2-002 证明**单侧可见声明边即可闭合**（Y 侧仅需 resolvedDepends），前提更弱、证据更深。C1-002 的失效面枚举（markPropSet 等待段/runDelayMethod 单线程面）并入主案 F3。
- 关联：C2-009（同等待原语的失败广播不对称）为**不同缺陷**保留独立——C2-002 修复（wait(timeout)+环嗅探）同时改善 C2-009 的挂死可检测性，互列；C2-017 的带超时重载依赖 C2-002 修复落地，随行。

### 自主组 2：C1-006 ↔ C2-011 —— **同一缺陷 → C2-011 并入 C1-006（主 ID = C1-006，P3）**

- live code 亲核：`BeanContainerImpl.getClassIntrospection`（L131-144）两案引用逐字同一（volatile + 本地变量非原子惰性初始化，注释自认只防半成品不防重复创建）。判定同一（重复实例、发布安全、良性 P3）、现状同一（历史修复 4ffaebb267 后残余）。主 ID 取 C1 维度（共享可变状态本体归属），C2-011 的「历史修复后残余面已知」现状标注随并入。

### 自主组 3：C2-003 ↔ C1-001 —— **同一缺陷 → C2-003 并入 C1-001（主 ID = C1-001，P2）**

- live code 亲核：`BeanContainerImpl` 生命周期字段 `running`/`started` volatile，`singletonScope`/`cancellable`/`startFuture` **全部裸字段**——两案共同根因即「容器生命周期状态无复合同步」。C1-001 立案位点（L70-90/L436-446/L476-524/L594-622）覆盖 C2-003 全部位点（L90/L477/L503/L537）；C1-001 的修复建议（单一启动锁覆盖 running/started/singletonScope 三元组 + `if(running)` CTA 同锁闭合 + startFuture volatile）**逐项包含** C2-003 的修复建议（启动锁 + startFuture volatile + awaitStartFinished 显式等待）。后果同族（双启动 scope 泄漏/restart 窗口孤儿单例/丢失唤醒）。C2-003 的降档论证（awaitStartFinished 无生产调用方）与启动期条件化口径随并入为主案判级备注。
- 旁证：C10-016 将 C2-003/C1-001/C1-005 归入同一测试目标族（并发 start×awaitStart / restart×getBean），与合并判定一致（C1-005 为不同缺陷，仍独立）。
- 关联：C4-009（restart 代际竞态）与 C6-008（停机解析双重失效）为本案**相邻不同缺陷**——C4-009 的代际令牌修复与本案启动锁互补；C6-008 的只读解析通道独立。三者同经 restart 窗口，互列。

### 自主组 4：C2-012 ↔ C2-001 —— **同一缺陷（族）→ C2-012 并入 C2-001 族案（主 ID = C2-001，P1）**（C2 复核移交清单①兑现）

- live code 亲核：`InjectRefValueResolver.collectDepends` L66-70 `if (!ignoreDepends)` 条件跳过 + L78 构造参数路径 `getBean(ref, true, beanCtx)` 持 BD 锁求值；`InjectTypeValueResolver` L53 `getBeanByType(type, true)` 无 collectDepends——E1/E2（C2-001 主案）与 E4/E5/E9/E10（C2-012）共享同一根因（不可见依赖边不入拓扑图）与同一位点域（collectDepends 收集缺口 + getBean0 synchronized 内求值），后果同一（BD⇄BD / BD⇄PBI 死锁；C2-012 另含运行期 config-executor 单线程死锁放大入口 E10）。C2-012 为族案失效面完备性扩展（P2 并入），修复共用：collectDepends 补收集 + tryLock 兜底 + allow-cycle 默认值裁定（复核附注保留：默认 true 下补收集仍不拒环）。
- C2-013（AOP 拦截器边）判**同族相邻独立保留**：其边在 setupInstance（BD 锁外）求值，不构成 BD monitor 环，主后果为未初始化拦截器注入（非死锁）；但其 collectDepends 补遍历 ioc:interceptors 为 C2-001 修复的自然扩展、其 PBI 交叉面为 C2-002 入口 +1——与 C2-001/C2-002 族案互列，修复同批设计。

### 自主组 5：C6-014 ↔ C6-004 —— **同一缺陷 → C6-014 并入 C6-004（主 ID = C6-004，P1）**

- 依据：同根因（进程销毁仅挂超时 timer 路径，失败/中断/调度拒绝路径无 destroy 兜底）、同后果（子进程 + 管道 fd + 被 pin 的 cachedThreadPool 线程泄漏）、**同一修复 diff**（safeDestroy 置于外层 try(L80) 的 finally，幂等兜底覆盖 C6-004 的 A-D 全时序 + C6-014 的 E/F/F' 三面——C6 复核已确认「同 diff」约束）。C6-014 的独立价值（修复位置约束：内层 finally 不覆盖 L83-L92 窗口）转为**主案修复约束条款**；其「触发归因修正」（REE 窗口仅存在于宿主/测试显式销毁全局执行器或替换同名注册时，非 CoreInitialization.destroy）随并入为主案触发面精度备注。
- 关联：C4-005（timeout=0 无限等待 → stderr 线程驻留）为同文件**相邻不同缺陷**——C6-004 族案的外层 finally 修复不解除 timeout=0 下调用线程悬挂（finally 永不可达），C4-005 的载体修复（虚拟线程/独立小池）独立必要，互列。

---

## 四、合并后台账（59 条逐条归属）

> 「主」= 合并案主 ID（保留计数）；「并」= 被并 ID（不再独立计数）；「相邻」= 同族相邻独立保留；「独立」= 无合并关系。

### C1 维度（19 条 → 合并后 15 案）

| ID | 复核终判级 | 本裁定归属 | 说明 |
|---|---|---|---|
| C1-001 | P2 | **主**（←并 C2-003） | 容器生命周期状态复合同步族案；吸收 startFuture/awaitStartFinished/启动守卫面 |
| C1-002 | P2 | **并 → C2-002** | 跨实例 monitor 等待环与 C2-002 同一缺陷（自主组 1） |
| C1-004 | P2 | 独立 | subscriptionCleanup 锁外 CTA；与 C2-007 同订阅生命周期族相邻互列 |
| C1-005 | P2 | 独立 | makeContainerScope CTA；与 C6-009 同文件相邻互列 |
| C1-006 | P3 | **主**（←并 C2-011） | getClassIntrospection 良性惰性初始化（自主组 2） |
| C1-007 | P1 | **主**（←并 C1-017，复核已裁） | 配置发布协议族案；api-core holder 化 + W1~W7×R1~R8 回归面 |
| C1-008 | P2 | 独立 | applyChange 代际撕裂；与 C2-008 同方法两面相邻互列 |
| C1-009 | P1 | **主**（←并 C4-002） | JdbcConfigSource close 竞态族案；次级回调守卫面归 C1-021 |
| C1-010 | P1 | **主**（←并 C1-011、C1-012（复核已裁）、C2-004（裁定 1）） | 插件生命周期锁面族案；连带闭合跨定义门控 TOCTOU |
| C1-011 | P2 | **并 → C1-010** | 复核已裁并案；本裁定台账登记 |
| C1-012 | P2 | **并 → C1-010** | 复核已裁并案；本裁定台账登记 |
| C1-014 | P2 | 独立 | VTE config 逃逸/stats null/refreshConfig 空体；与 C4-004 同站点治理族相邻（共享 stats 面同修） |
| C1-015 | P3 | 独立 | registerGlobalWorker 替换语义；与 C6-010 面2/F3⑥ 同事件两面相邻；跨单元对齐 WI 2.1 CL-018 |
| C1-016 | P3 | 独立 | DefaultKeyManager destroy 竞态（NPE 表述纠偏维持） |
| C1-017 | P1 | **并 → C1-007** | 复核已裁族案合并；本裁定台账登记 |
| C1-018 | P2 | 独立 | 名字索引孤儿化；与 C1-010 同锁域批次相邻（裁定 8） |
| C1-019 | P2 | 独立 | reload remove→put 窗口；C1-010 修复同闭其窗口（裁定 8） |
| C1-020 | P3 | 独立 | updateConfig ACTIVATING 窗口；与 C1-010 同批（updateConfig 进 lifecycleLock），顺带闭合 C1-013 残留义务 |
| C1-021 | P1 | **主**（←并 C6-005（cross-ref，复核已裁）+ C4-002 次级面） | 回调杀链族案；平台绝缘方案归 WI 2.1 C3-001 统筹 |

### C2 维度（15 条 → 合并后 11 案）

| ID | 复核终判级 | 本裁定归属 | 说明 |
|---|---|---|---|
| C2-001 | P1 | **主**（←并 C2-012） | 不可见边死锁族案（自主组 4）；allow-cycle 默认值裁定附注保留 |
| C2-002 | P1 | **主**（←并 C1-002） | PBI 跨实例等待环（自主组 1）；修复=wait(timeout)+环嗅探 |
| C2-003 | P2 | **并 → C1-001** | 同一根因同修复载体（自主组 3） |
| C2-004 | P2 | **并 → C1-010** | 同一窗口镜像交错（裁定 1）；事实修正随主案备注 |
| C2-005 | P2 | 独立 | bin 锁长临界区；C2-014 残余面归此；与 C6-001/CL-002 同站点协同 |
| C2-006 | P3 | 独立（相邻 C1-010 族案） | 锁矩阵契约钉死 + catch-all 观测残余（裁定 1） |
| C2-007 | P2 | 独立 | 订阅清理孤儿 list；与 C1-008/C2-016/C1-004 同族相邻（裁定 9）；与 CL-012 修复方案互斥注记 |
| C2-008 | P2 | 独立 | applyChange 入口并发面；与 C1-008 同方法两面相邻 |
| C2-009 | P2 | 独立 | Error 逃逸失败广播不对称；与 C2-002 同等待原语相邻；与 C4-008(a)/CL-019 形态同族（catch(Exception) 不接 Error） |
| C2-010 | P3 | 独立 | effect 闭包锁内 dispose |
| C2-011 | P3 | **并 → C1-006** | 同方法同判定（自主组 2） |
| C2-012 | P2 | **并 → C2-001** | 同族失效面扩展（自主组 4）；E4/E5/E9/E10 随主案修复 |
| C2-013 | P2 | 独立（相邻 C2-001/C2-002 族案） | 拦截器边缺口；collectDepends 扩展随 C2-001 批次 |
| C2-016 | P3 | 独立 | updateConfigValue 两段锁键复活 |
| C2-017 | P3 | 独立（随行 C2-002/C2-013） | includeCreating/invokeCommand 契约面；带超时重载依赖 C2-002 落地；C2-015 驳回残余 javadoc 建议归此 |

### C4 维度（11 条 → 合并后 9 案）

| ID | 复核终判级 | 本裁定归属 | 说明 |
|---|---|---|---|
| C4-001 | P2 | 独立 | globalTimer 直连占用；与 C6-003 同调度点修复协同；与 WI 2.1 C4-001 同设施两面（映射表） |
| C4-002 | P2 | **并 → C1-009**（主面）+ **C1-021**（次级回调守卫面） | 裁定 4 |
| C4-003 | P1 | **主**（←并 C4-008） | concurrent-start 失败语义族案（裁定 3 延伸）；门控终裁附注保留 |
| C4-004 | P3 | **主**（←并 C6-010 cross-ref，复核处置确认） | VTE 治理面；设施本体面归 WI 2.1（映射表 6/7 行） |
| C4-005 | P3 | 独立 | stderr 阻塞读占共享池；与 C6-004 族案同文件相邻（自主组 5） |
| C4-006 | P3 | 独立 | config 执行器参数纪律/误标日志；停机窗口触发面与 C1-021 相邻（后果面不同） |
| C4-007 | P2 | 独立 | config-executor 串行搁浅；与 C1-007 族案不同缺陷（裁定 3）；CL-003/CL-005/CL-017 根位点归属 |
| C4-008 | P2 | **并 → C4-003** | 三形态完备性扩展（裁定 3 延伸）；设施根因 CL-019 移交不变 |
| C4-009 | P2 | 独立（同 PR 批次 C4-003 族案/C4-011） | restart 代际竞态；与 C1-001 相邻不同缺陷（自主组 3 关联） |
| C4-010 | P2 | **主**（←并 C6-006 cross-ref，复核已裁） | 销毁序哈希序族案；与 C6-007 同 close 链相邻 |
| C4-011 | P3 | 独立（同 PR 批次 C4-003 族案） | destroy 路径 join 缺失；与 C4-003 启动/关闭对偶 |

### C6 维度（14 条 → 合并后 10 案）

| ID | 复核终判级 | 本裁定归属 | 说明 |
|---|---|---|---|
| C6-001 | P1 | 独立 | PluginClassLoader 前置失败泄漏；与 C2-005 同 lambda 修复协同（裁定 10） |
| C6-002 | P1 | 独立 | 插件状态机失败落定缺失（4 站点）；与 C1-010 族案同状态机两面相邻（并发窗口 vs 失败卡死），同 lifecycleLock 域同批实施 |
| C6-003 | P1 | 独立 | 先调度后加载泄漏；与 C4-001/C1-009 相邻不同缺陷（裁定 6） |
| C6-004 | P1 | **主**（←并 C6-014） | 进程销毁兜底缺失族案（自主组 5）；修复=外层 finally |
| C6-005 | P2 | **并 → C1-021** | 复核已裁 cross-ref；台账登记 |
| C6-006 | P2 | **并 → C4-010** | 复核已裁 cross-ref；台账登记 |
| C6-007 | P2 | 独立 | destroy 链异常聚合（含 Error 穿透面）；与 C4-010/C6-002 相邻；与 C2-009/C4-008(a) 形态同族 |
| C6-008 | P2 | 独立 | 停机解析双重失效；与 C1-001 restart 窗口相邻 |
| C6-009 | P2 | 独立 | onContainerStop 关闭不摘条目；与 C1-005 同文件相邻；与 CL-006/CL-007 同文件不同形态 |
| C6-010 | P2 | **并 → C4-004（单元侧）+ WI 2.1 C6-002/003（设施侧）** | 复核处置确认（裁定 7）；F3⑥ 清单随案 |
| C6-011 | P2（条件性） | 独立 | IPluginManager 无聚合关闭；与 C6-001 同条件化口径（仓内无装配点） |
| C6-012 | P2（理论） | 独立 | GitRepository close 责任外置；降 P3 与否留 7.1 协调方（C6 复核移交事项 5） |
| C6-013 | P3 | 独立 | ImageWriter 异常路径不 dispose |
| C6-014 | P2 | **并 → C6-004** | 同根因同修复 diff（自主组 5）；修复约束条款随主案 |

---

## 五、跨单元关联映射表（单列，不重复立项）

| # | 本单元条目 | 对端（cra-index WI 1.1–2.1 台账） | 关系与处置 |
|---|---|---|---|
| 1 | C1-007 族案（含 C1-017） | WI 2.1 `nop-kernel-concurrency-C1-001`（RoundRobinSupplier.resize；标注「IoC 刷新派发链可达」） | **刷新链上下游**：对端标注的派发链上游即本单元刷新链（applyChange → subscriptions.trigger → @r-cfg 重绑定）。两案修复独立（对端修 resize 收缩关闭面，本端修 api-core holder 发布协议），回归面重叠（配置刷新触发路径）——Phase 8 回归设计共享触发基建，不双改。 |
| 2 | C1-021（含 C6-005 cross-ref） | WI 2.1 `nop-kernel-concurrency-C3-001`（周期任务设施无统一异常绝缘层，29 处生产站点） | **设施-实例**：C1-021 为该设施清单的 nop-config 站点实例。平台级绝缘层方案必须吸收 C1-021 的「trigger 独立 try-catch + per-task 隔离」语义，勿双改；C1-021 的 jdbc 轨修复可在设施方案落地前先行（一行守卫），落地后由绝缘层覆盖。 |
| 3 | C4-003 族案（含 C4-008）+ C4-009 + C4-011 + CL-019 | WI 2.1 未覆盖新位点（CL-019 = TaskExecutionGraph.runTask catch(Exception)，经对照核实） | **同 PR 批次绑定**：IoC 启动图 join / 失败传导 / 超时 / 代际令牌一体修复（复核产出）；CL-019 主处置移交 nop-kernel（catch Throwable 或包装 completeExceptionally），其消费侧回归 = C4-008 形态(a)，与 C10-002 失败传导测试共享用例。 |
| 4 | CL-008 | WI 2.1 `nop-kernel-concurrency-C6-011`（ShutdownHook 再入守卫反转，P2 休眠） | **同位点**：主处置与修复归设施单元；本单元仅登记线索（NopApplication 注册面正常）。 |
| 5 | CL-020 | WI 2.1 `nop-kernel-concurrency-C6-003`（全局池零生命周期挂接） | **同位点**：主处置归设施单元；其 round2 已将 Jdk21CommonsCoreInitializer 僵尸窗口并入 C6-003 F3。 |
| 6 | C6-010（三重登记） | WI 2.1 `C6-002`（register 覆盖即 destroy / unregister 无 destroy）+ `C6-003` | **使用方↔设施两面**：面 1 归本单元 C4-004；面 2/面 3 与 F3⑥（register 覆盖式关闭无等待）归设施案。C6-010 转 cross-ref 不独立计数。 |
| 7 | Jdk21CommonsCoreInitializer 注册链（C4-004 / C1-015 / C6-010 门控与注册唯一性事实） | WI 2.1 `C6-002` 可达性终审 | **可达性证明输入**：本单元实测事实——`GlobalExecutors.register` 调用族全仓仅 VTE 一处、`CFG_ENABLE_VIRTUAL_THREAD_POOL` 仅 initialize 读一次、根 pom L558 `nop-commons-java21` 注释停用（默认 classpath 不可达，显式引入时可达）——为 kernel C6-002 register-replace 面的可达性终审直接输入。 |
| 8 | C4-001 | WI 2.1 `nop-kernel-concurrency-C4-001`（globalTimer 死配置 + 4 线程共享放大） | **同设施两面**：对端=设施死配置面，本端=使用方占用面（AbstractFileConfigSource/JdbcConfigSource 两站点）。独立成立互补，修复统筹（本端 executeOn/SPI/超时三选一可独立落地）。 |
| 9 | C1-015 | WI 2.1 `CL-018`（GlobalExecutors register/unregister 契约） | **消费侧实例**：C1-015（registerGlobalWorker 重复注册）为该契约的消费侧具体面，修复须与契约裁定对齐（幂等注册/定向错误）。 |
| 10 | CL-013 | WI 2.1 台账 | **未登记新面**：registerCleanup 零生产调用 + destroy 早退丢弃 cleanup，经 grep 核对 WI 2.1 目录零命中——移交 nop-kernel 线时按新登记面入台账（非重复）。 |
| 11 | C1-007 族案回归面（跨单元消费者） | nop-orm / nop-ai-core / nop-web 等使用方单元 | api-core 修复归属移交 + 回归面必测清单：`OrmMigrationWriteGate.setClosed`（门禁契约 vs 裸协议可见性，最锐利实证，必测）、`LlmConfigHelper.resolveApiKey`（CHM 锁内配置写放大）、`WebPageExporter`、`OrmMigrationOrchestrator`（C1-round3 §四清单）。 |

---

## 六、CL 台账终表（21 条编号；处置 = 各维度复核 + 本裁定去重合并 + 移交清单）

> CL 线索不计入发现数。本表为终态登记；「立案落格」指线索已升格为正式发现（其编号保留追溯）。

| CL | 内容 | 复核处置 | 本裁定去重/合并 | 终态归属 |
|---|---|---|---|---|
| CL-001 | JdbcConfigSource 定时刷新回调杀链 | 确认，升格立案 | = C1-021 本体 | **升格立案 C1-021（P1）**；C6-005 cross-ref 同案 |
| CL-002 | loadPlugin computeIfAbsent 锁内 HTTP/类加载/start | 确认，移交 C2/C9 | 立案落格 = C2-005（锁纪律面）；C9 性能面随 C2-005 修复收敛 | C2-005 关联线索（不重复计数） |
| CL-003 | reconcile 持锁阻塞单线程 configExecutor | 确认，移交 C3/C8 | C4-007 为其框架级根位点（内联治理面），随 C4-007 修复收敛 | C4-007 关联线索 + C3/C8 移交行保留 |
| CL-004 | VTE 无界全局 worker / stats null / destroy 无等待 | 确认，移交 C4/C8/C6 | 无界本体未在本单元立案（C4 报告明示不重复）；stats/destroy 面落格 C1-014/C4-004/C6-010 | C4-004/C1-014 关联线索 + 设施面归 WI 2.1 |
| CL-005 | configExecutor 队列 100 拒绝行为 + reconcile 串联 | 确认，移交 C8/C4 | 与 C4-006（参数纪律）、C4-007（串联根位点）协同；拒绝路径经 C4-006(a) 证明运行期不可达 | C4-006/C4-007 关联线索 + C8 移交行保留 |
| CL-006 | destroyBean 不摘 scope 条目 → 二次 destroy | 确认，移交 C6 | 与 CL-018 同位点（同经 BeanScopeImpl.remove→destroyBean）；C4-010/C6-007 修复同链 | C6 移交行保留（同位点相邻族注记） |
| CL-007 | 请求域 scope 停机弱一致 + DynamicConfigSource.onClose 可见性 | 确认，移交 C1/C6 | 与 C1-005/C6-009 同文件相邻（不同缺陷形态） | C1/C6 移交行保留 |
| CL-008 | ShutdownHook 守卫反转 + TreeSet 非线程安全 | 确认 | = WI 2.1 C6-011 同位点 | 设施单元主处置（映射表 4 行） |
| CL-009 | PluginManagerImpl syncGet 内联终判 | 确认（零立案） | 无合并 | **归档（零缺陷终判维持）** |
| CL-010 | 插件子容器不经 PluginClassLoader（隔离失效） | 确认 | 功能面，非计量面 | 插件设计线移交 |
| CL-011 | DefaultShellOutputCollector 无界收集 | 确认 | — | C5 维度移交 |
| CL-012 | ChangeSubscriptions 空订阅条目滞留 | 确认 | 与 C2-007 修复方案互斥注记（若采用常驻条目方案则本面加重） | C7 维度移交（含 C2-007 协同注记） |
| CL-013 | registerCleanup 零调用 + destroy 早退丢弃 | 确认（WI 2.1 未登记新面） | — | nop-kernel 新面移交（映射表 10 行） |
| CL-014 | autoInit 锁内 CoreInitialization.destroy 全局拆除 | 确认 | 与 C6-011 F3⑤ 设计观察项同面注记 | C4/跨单元移交（CoreInitialization 本体 nop-core） |
| CL-015 | checkChangedAndReload 批量中断 | 确认（P3 宿主守卫） | — | 宿主轮询侧守卫（P3 行保留） |
| CL-016 | PlaceholderConfigReference 刷新后返回未替换模板且缓存不重建 | 确认 | 与 C1-007 修复协议选型关联（该类不作范本） | api-core 功能缺陷轨移交 |
| CL-017 | configDependants 级联无环检测 + config 单线程重绑定 | 确认（与 CL-005 合并量化） | 重绑定入口 = C2-012 E10（已并 C2-001 族案） | 与 CL-005 合并行保留 + C2-001 族案关联 |
| CL-018 | BeanScopeImpl.close 哈希序销毁 | 确认，移交 C6 | 立案落格 = C4-010（吸收 C6-006）；同位点相邻族 | C4-010 关联线索（不重复计数） |
| CL-019 | TaskExecutionGraph.runTask 仅 catch(Exception) | 确认移交（WI 2.1 未覆盖新位点） | C4-008（已并 C4-003 族案）为其消费侧用例；C2-009/C6-007 为形态同族 | nop-kernel 移交（映射表 3 行）；回归面与 C10-002 共享 |
| CL-020 | GlobalExecutors 三全局池零生命周期挂接 | 确认 | = WI 2.1 C6-003 同位点 | 设施单元主处置（映射表 5 行） |
| CL-021 | OsUserHelper.createMacUser 口令入进程参数 | 确认 | — | 安全线移交 |

**CL 终态统计**：21 条编号 —— 升格立案 1（CL-001→C1-021）、立案落格 2（CL-002→C2-005、CL-018→C4-010）、同位点并入设施单元 2（CL-008、CL-020）、零立案归档 1（CL-009）、关联线索保留 8（CL-003/004/005/006/007/012/014/017）、纯移交 7（CL-010/011/013/015/016/019/021）。

---

## 七、驳回项残余面处置（维持 4 条驳回，登记去向）

| 驳回 ID | 维持理由（复核） | 残余面去向 |
|---|---|---|
| C1-003 | handler==null 不可达（两构造分支均闭合） | 环依赖早引用 by-design；无残余义务 |
| C1-013 | ++activationFailures 实际锁内；updateConfig merge 纯理论 | merge 原子性义务 **并入 C1-020 修复批次**（updateConfig 进 lifecycleLock 时顺带闭合） |
| C2-014 | LC⇄BIN 环第二腿不成立（映射函数不持 LC） | 残余阻塞面归 **C2-005**（结构性修复首选）/ C2-006（矩阵契约）（裁定 2） |
| C2-015 | ISE 窗口经容器路径不可达（与 C1-003 同口径） | javadoc 契约声明归 **C2-017** 随行 |

---

## 八、统计节

### 8.1 合并组清单（主 ID ← 被并 ID，共 14 组）

| # | 主 ID（判级） | ← 被并 ID（原判级） | 裁定来源 |
|---|---|---|---|
| 1 | C1-007（P1） | C1-017（P1） | 复核已裁，台账登记 |
| 2 | C1-010（P1） | C1-011（P2）、C1-012（P2） | 复核已裁并案，台账登记 |
| 3 | C1-010（P1） | C2-004（P2） | 本裁定（裁定 1） |
| 4 | C1-021（P1） | C6-005（P2 cross-ref）；另收 C4-002 次级面 | 复核已裁 + 裁定 4 |
| 5 | C4-010（P2） | C6-006（P2 cross-ref） | 复核已裁，台账登记 |
| 6 | C4-004（P3）+ WI 2.1 C6-002/003 | C6-010（P2 cross-ref） | 复核处置确认（裁定 7） |
| 7 | C1-009（P1） | C4-002（P2） | 本裁定（裁定 4，live code 亲核） |
| 8 | C1-001（P2） | C2-003（P2） | 本裁定（自主组 3，live code 亲核） |
| 9 | C1-006（P3） | C2-011（P3） | 本裁定（自主组 2，live code 亲核） |
| 10 | C2-001（P1） | C2-012（P2） | 本裁定（自主组 4，live code 亲核；C2 复核移交清单①兑现） |
| 11 | C2-002（P1） | C1-002（P2） | 本裁定（自主组 1，live code 亲核） |
| 12 | C4-003（P1） | C4-008（P2） | 本裁定（裁定 3 延伸） |
| 13 | C6-004（P1） | C6-014（P2） | 本裁定（自主组 5） |
| — | （C2-006、C2-013、C1-018、C1-019、C6-002、C6-003、C4-009、C4-011、C4-005、C4-001、C1-014、C1-008/C2-008、C2-007、C1-004、C6-007、C6-008、C6-009、C6-001/C2-005、C1-015/C6-010 面2 等均判**同族相邻**，独立保留，见 §四互列注记） | | |

### 8.2 合并后总数与严重度分布

| 口径 | P0 | P1 | P2 | P3 | 合计 |
|---|---|---|---|---|---|
| 合并前（59 保留，按 ID） | 0 | 12 条 ID / 11 族案条目 | 33 | 14 | 59 |
| 并入数 | — | −1（C1-017） | −12（C1-002/C1-011/C1-012/C2-003/C2-004/C2-012/C4-002/C4-008/C6-005/C6-006/C6-010/C6-014） | −1（C2-011） | **−14** |
| **合并后** | **0** | **11** | **21** | **13** | **45** |

- 有效发现数 = 59 − 14（并入）= **45 条合并案**。分维度：C1 15、C2 11、C4 9、C6 10。
- 驳回 4 条维持（C1-003/C1-013/C2-014/C2-015），残余面处置见 §七。
- 判级归属特别注记：C6-010 的 P2 由设施本体条目（WI 2.1 C6-002/003，P2）承载；本单元侧主 ID C4-004 维持 P3（nop-commons-java21 非默认 classpath 门控论证成立），不因吸收升档——上表 P2/P3 计数已按此口径（C6-010 的 P2 移出本单元统计）。
- C6-012（P2 理论风险）降 P3 与否按 C6 复核移交事项 5 留 7.1 协调方批量裁量，本裁定不主动降级。

### 8.3 P1 合并变动单独说明（11 族案条目逐条）

| P1 族案 | 变动 | 说明 |
|---|---|---|
| C1-007（含 C1-017） | 吸收 1 条（复核已裁） | P1 维持；由刷新可见性面 + hostId 写-写竞争 + 两步写序撕裂面支撑，不依赖 defaultValue 理论面。 |
| C1-009 | **新并 C4-002（P2）** | 同一位点同一交错同一泄漏后果（live code 亲核）；P1 维持；C4-002 次级回调守卫面移交 C1-021 修复载体。 |
| C1-010（含 C1-011/012） | **新并 C2-004（P2）** | 同一 check-then-act 窗口镜像交错 + AbstractPlugin volatile 面与 C1-011 重合；P1 维持（附窄窗口备注）；修复批次扩展为 lifecycleLock 全迁移 + reconcileLock 序列化 + volatile 对齐 + 复合读双检。 |
| C1-021（含 C6-005 cross-ref） | 收编 C4-002 次级面 | P1 维持；触发面终裁不变（可插拔 IConfigExecutor + 公开 SPI 回调），修复 = trigger 独立 try-catch + per-task 隔离；平台绝缘统筹归 WI 2.1 C3-001。 |
| C2-001 | **新并 C2-012（P2）** | 同一 collectDepends 不可见边根因，E4/E5/E9/E10 为扩展面（含 config-executor 单线程死锁放大入口）；P1 维持；修复须绑定 allow-cycle 默认值裁定（复核附注保留）。 |
| C2-002 | **新并 C1-002（P2）** | 跨维度同一缺陷（同 wait/notify 协议跨实例环）；主 ID 取 C2-002（P1、单侧可见边即闭合、时序更深），C1-002 的 P2 与 F3 面吸收；P1 维持。 |
| C4-003（含 C4-008） | **新并 C4-008（P2）** | 同一 whenComplete 汇聚点无兜底 + 无 join 根因，三形态（Error 孤儿/成功回调静默/无超时）为修复验收面；P1（门控）终裁维持不降；设施根因 CL-019 移交不变；同 PR 批次含 C4-009/C4-011。 |
| C6-001 / C6-002 / C6-003 | 无变动 | 均独立成立；C6-003 与 C4-001/C1-009 判相邻不并（裁定 6）；C6-002 与 C1-010 族案同状态机两面相邻、同批实施。 |
| C6-004 | **新并 C6-014（P2）** | 同根因同后果同一修复 diff；P1 维持；修复约束条款（外层 try L80 的 finally）与触发归因修正（REE 窗口真实范围）随主案。 |

### 8.4 同族相邻登记表（修复批次协同，防碎片化实施）

| 修复批次 | 成员（含相邻项） | 协同要点 |
|---|---|---|
| 插件生命周期批次 | C1-010 族案（主）+ C2-006 + C1-018 + C1-019 + C1-020 + C6-002 | lifecycleLock 全迁移 / reconcileLock 序列化 / volatile 对齐 / computeIfPresent 原子化 / updateConfig 进锁（顺带闭合 C1-013 残留）/ 失败终态收敛 + forceUnload |
| IoC 启动图批次 | C4-003 族案（主）+ C4-009 + C4-011 + CL-019（移交）+ C1-001（←C2-003）+ C2-009 | 框架级 join / 失败传导 / 超时 / 代际令牌 / 启动锁三元组 / catch(Throwable) 广播——同 PR 面设计 |
| 配置刷新链批次 | C1-007 族案（主）+ C1-008 + C2-008 + C1-021（含 C6-005）+ C4-007 + C4-001 + C6-003 + C4-006 | api-core holder 化 / 代际快照 / applyChange synchronized / trigger 守卫 / 慢监听器观测 / 载体卸载与构造序（同调度点同 diff）/ 参数纪律 |
| JdbcConfigSource 批次 | C1-009（主，←C4-002）+ C1-021 + C6-003（jdbc 面） | close synchronized 化 + closed 双检 / trigger 守卫 / 先加载后调度——同类三面一次改写 |
| ShellRunner 批次 | C6-004 族案（主，←C6-014）+ C4-005 + CL-021 | 外层 finally destroy 兜底 / timeout=0 载体改造（虚拟线程）/ 口令参数（安全线同文件留意） |
| VTE 治理批次 | C4-004（主，←C6-010 cross-ref）+ C1-014 + C1-015 | 防御性快照 / stats 契约 / destroy 覆写+unregister+限时 await / 幂等注册——与 WI 2.1 kernel C6-002/003/CL-018 对齐设计 |
| 销毁链批次 | C4-010（主，←C6-006）+ C6-007 + C6-008 + C6-009 + CL-006/CL-018 | topoIndex 逆序销毁 / 异常聚合（含 Error 口径）/ 停机解析通道 / close 后摘条目 |

### 8.5 与 C10 缺保护登记的归组映射（引用，不改 C10 文件）

C10-001（JdbcConfigSource 真空）覆盖合并后的 C1-009/C1-021/C6-003jdbc/C4-002（已并 C1-009）；C10-002 覆盖 C4-003 族案（含 C4-008）/C4-009/C4-011/C2-009 + CL-019 消费侧；C10-003 覆盖 C2-001 族案（含 C2-012）/C2-002（含 C1-002）/C1-002 已并注记；C10-004 覆盖 C1-010 族案（含 C2-004）/C1-018/C1-019/C2-005/C2-006/C6-011；C10-009 覆盖 C1-007 族案/C1-008/C2-007/C2-008/C2-016/C1-020；C10-010 覆盖 C4-010（含 C6-006）/C6-007/C6-008/C6-009；C10-016 覆盖 C1-001（含 C2-003）/C1-005——C10 的家族归组与本裁定合并结果一致，无需调整 C10 文件。

---

## 九、裁定一致性声明

- 本裁定不修改 4 维度文件与 C10 文件；全部判定以各维度复核终裁 + live code 亲核为据。
- 与 C1 复核口径的自洽性：C1-003/C2-015 驳回域（beanMethod!=null 代理 handler）与 C2-013 保留域（beanMethod==null 普通拦截器半成品注入）分属不同机制域，两裁定并行成立（C2 复核争议点 7 维持）。
- 与历史 check 审计的判级张力：C2-001 族案 P1 与历史 P2-6 暂缓裁定的张力，按本轮专项属性裁定（C2 复核终裁维持 P1），登记知悉。
- 后续入口：本目录各维度文件的修复 plan 编制以 §四归属表 + §八统计为准；合并案主 ID 为 Phase 8 立项与 9.1 对账的唯一计数键（被并 ID 作为追溯别名随案登记）。
