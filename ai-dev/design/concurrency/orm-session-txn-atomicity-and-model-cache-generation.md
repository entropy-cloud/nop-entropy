# ORM 会话/事务原子化与模型缓存世代化契约（R2-14 批 13 项裁定）

> Status: active
> Last Reviewed: 2026-10-30（plan 2517 Phase 1 产出；本批唯一设计裁决权威；C2-004×C1-027「失效世代号」设计 SSOT）
> Source: plan 2517（`ai-dev/plans/2517-cra-phase8.23-r2-14-orm.md`）Current Baseline 13 成员表 + Baseline 勘误 6 条（含勘误 6 C2-001 写入口族区间订正 :495-644）；发现事实源 = WI 2.4 审计目录 `ai-dev/audits/2026-10/2026-10-18-1521-deep-audit-nop-persistence-rw-concurrency/`；行号 = live `299925b489`（分支 `concurrency-audit-remediation`）2026-10-30 亲读回读值，执行期漂移以 live 为准并登记于 plan 勘误
> 实现锚：nop-orm `impl/OrmSessionRegistry`（:20-27）、`impl/OrmTemplateImpl`（runInNewSession :213-232 / runInNewSessionAsync :244-271[whenComplete :262-269] / register/unregister :273-280）、`session/OrmSessionImpl`（字段 :102-108 / flush 门 :183-185 / flusher 赋值 :208 / dirty=false :211 / clearDirty :212 / batchActionQueue.flush :214 / checkContext :229-232 / 写入口族 :495-644[仅 checkValid+checkNotReadOnly] / close :768-770 / flushImmediately oldDirty 模板 :1008-1030）、`persister/EntityPersisterImpl`（batchLoad 回填 :175-183 / loadAsync 回填 :228-243 / lock :259-261 / evict 点 :466/:487/:499 / updateGlobalCache :538-545 / evictGlobalCache :553-569 / getToLoad :595-624）、`persister/CollectionPersisterImpl`（:96-100/:148-154/:172-183/:217-229）、`persister/BatchActionQueueImpl`（flushAsync :145 / actionMap.clear :234）；nop-orm `factory/DefaultOrmModelProvider`（:17/:36-43/:45-49）、`factory/TenantAwareOrmModelProvider`（:16/:40-42/:46）、`factory/LoadedOrmModel`（:33/:58-60）；nop-orm-model `model/OrmComponentModel`（:26-27/:43-50/:106-117）；nop-dao `txn/impl/TransactionRegistry`（:23-30）、`txn/impl/TransactionTemplateImpl`（getTxnState :125-167 / runWithoutTransaction :170-181 / REQUIRES_NEW :285-290 / NOT_SUPPORTED :291-299 / createNewTransaction :338/:352 / cleanup :406-438）、`txn/impl/DefaultTransactionManager`（:158-182）、`txn/impl/AbstractTransaction`（subTransactions :60/:105-118 / :194-221 / :351-372 / :437-451）、`jdbc/txn/JdbcTransaction`（connection :35 / getConnection :61-75 / doRollback :78-89 / doCommit :92-100 / restoreAutoCommit :102-111 / releaseConnection :113-120 / doClose :122-125）；nop-orm-data `source/DaoEntityBlockingSource`（take :122-126 / drainTo REQUIRES_NEW :172 / waitUntil :196 / loadItems :216-237 / tryClaim :246-263 / currentAcquireValues :265-285）；平台载体 `BaseContext.getAttrs()`（nop-api-core :191，attributes CHM :46）
> Related：平台上下文传播/收尾泵先例 = [bindcancel-context-unification.md](bindcancel-context-unification.md)（thenOnContext 出口泵语义，R1-04 已收口面，本批零触碰 nop-api-core）；模型发布 volatile 先例 = [ioc-startup-graph-and-config-publish-protocol.md](ioc-startup-graph-and-config-publish-protocol.md) §3.3（shardSelector 单 volatile 发布，裁定②同型投影）；R10 自并族 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/inventory-ledgers.md` §二.2 族行（C2-013/C2-015「自并」单点）；**下游 SSOT 消费**：R4-18（8.58）C1-027 到达时引用本裁定⑥不重建第二套世代语义；R4-18 C1-016 唯一真并发钉零弱化义务见 §四.3

## 一、裁定总表

| 裁定 | 成员 | 唯一结论 |
|------|------|---------|
| ① | 2.4-C1-001（P1；←C1-023/C2-003 双并入；C10-001） | 两注册表（OrmSessionRegistry/TransactionRegistry）`instance()` 改 `context.getAttrs().computeIfAbsent(CONTEXT_KEY, k -> new …)` 内联原子化（预设，零 nop-api-core 触碰）；put/restore 改 remove(key,value) 两参 + 不匹配仍无条件 close；skip-session-close info 升 WARN；替身 context 约束声明（§3.1） |
| ② | 2.4-C1-003（P1；C10-003 半边） | `DefaultOrmModelProvider.ormModel`/`TenantAwareOrmModelProvider.ormModel`/`LoadedOrmModel.ormInterceptor` 三点位 volatile 化；clearCache 顺序 = interceptorCache.clear() 先于新模型赋值发布；setOrmInterceptor 变化时才写；TenantAware 同 diff；模型代发布协议切片（C2-007/C1-004）归 R4-18/R4-19 只登记接缝 |
| ③ | 2.4-C1-015（P1） | OrmComponentModel `colPropIds`/`propIds` 解析期预计算（预设，与 colsByPropId 同法，运行期只读）；列序钉零弱化；C1-016 同族零触碰 |
| ④ | 2.4-C2-001（P1；C10-004 半边） | session 级 `ReentrantLock` 全生命周期互斥（预设，锁域=session 级一次定夺；可重入承载 CascadeFlusher 回调重入），flush 重入静默跳过→锁内串行；写入口族 :495-644 补 checkContext 对称；同族守卫清单落位（§3.4） |
| ⑤ | 2.4-C2-002（P1；C10-002） | runInNewSessionAsync 收尾（flush/registry 恢复/close）经 whenComplete 投递回 context 队列（预设，对齐 thenOnContext 出口泵语义），完成线程只投递不执行；close 幂等 :768-770 兜底 |
| ⑥ | 2.4-C2-004（P1；C10-005 半边；**C1-027 世代号 SSOT**） | read-repair 回填世代化 = nop-orm 模块内世代 fence（CHM<String,AtomicLong> per-persister）+ 版本化 value + fence CAS 回填；唯一公式与 fence 生命周期见 §3.4；lock() 路径排除项保留；集合路径同 diff；世代号权威在本裁定，R4-18 消费不重建 |
| ⑦ | 2.4-C2-005（P1；C10-006） | 抢占谓词 NULL 显式化 = IS NULL 谓词（预设）：oldValues 中源值为 null 的抢占字段生成 `col IS NULL` 进 WHERE，使 NULL 初始态参与 CAS；回归钉穿透谓词生成层 |
| ⑧ | 2.4-C2-013（P1；C10-007；R10 自并与⑨同变更集） | 事务注册协议原子化：get+new+register 收敛为 registry compute/putIfAbsent 语义（败者 close 自建、采用胜者）；runWithoutTransaction/NOT_SUPPORTED 改挂起标记（per-context 事务门闩备选）；cleanup remove 失败先身份比对、不符降级 WARN 非抛错 |
| ⑨ | 2.4-C2-015（P1；C10-007；与⑧同变更集[R10]） | subTransactions 惰性 init synchronized(this) + 全部迭代点持锁快照 `new ArrayList<>(values())` + 快照外迟到子事务显式拒绝（fail-fast，禁静默丢弃）；listeners COW 纪律保持 |
| ⑩ | 2.4-C2-017（P1；←C1-026 并入；C10-008） | JdbcTransaction 事务对象为 monitor 串行化五触点（getConnection/doCommit/doRollback/releaseConnection/doClose，预设）；临界区内含池获取、**禁锁内取 registry/CHM 全局锁**；doCommit null 早退补 WARN 留痕（非静默 no-op、不改抛错语义） |
| ⑪ | 2.4-C3-001（P1；C10-002；与⑤同变更集） | 兜底关闭 = context 生命周期反向登记（预设）：registry put 新会话时在 context 关闭钩子挂 session 兜底 close 登记，future 完成路径保持现状；异步入口族七类单一修复点覆盖；泄漏守卫指标 = 挂起会话计数 WARN |
| ⑫ | 2.4-C3-005（P1；←C2-006 并入；C10-009 钉错相位注记） | flush 顺序修正 = dirty=false/clearDirty 移至 batchActionQueue.flush() 成功之后（预设，对齐 flushImmediately oldDirty 保守恢复模板）；actionMap.clear 失败路径面同 diff 核对登记；新钉相位 = batchActionQueue.flush 失败（dirty=false 之后），旧装载相位钉保留并存 |
| ⑬ | 2.4-C6-001（P1；←C3-006 并入；C10-010；N4 约束） | drainTo 方法级 REQUIRES_NEW 摘除，waitUntil 循环体外置事务（runWithoutTransaction 形态），循环体内逐轮 `runInTransaction` 短事务包络 loadItems（**N4：tryClaim 抢占 CAS 与轮询读同一短事务**）；「空闲持连」守卫指标随裁定成文；queryTimeout 子项挂验收清单 |
| ⑭ | 测试缝与零回退钉 | 红①–红⑬驱动器（§四.1）+ 零回退钉全列（§四.3）+ C10 集落名（全量 6 + 半边 3 + 009 注记，§四.4） |

## 二、背景与动机

R2-14 批 13 项 P1（WI 2.4 单元有效工单全集）覆盖 nop-persistence 四模块三类缺陷面：**复合操作无原子性**（context 注册表三步 get-or-create、事务注册查-建-放协议、read-repair 迟到回填、抢占谓词 NULL 退化）、**同步纪律缺失**（ormModel/ormInterceptor 非 volatile、OrmComponentModel 先发布后填充、session 全生命周期零互斥、异步收尾在任意完成线程、subTransactions 裸 LinkedHashMap、JdbcTransaction 连接生命周期无互斥）、**顺序/边界错误**（flush 先声明干净后执行 SQL、drainTo 无限等待横跨 REQUIRES_NEW 事务持连持锁）。本文档一次裁定 13 项 + 测试缝，使 Phase 2 实施只有一种合法形态。

## 三、核心设计契约

### 3.1 裁定①：Context 注册表原子化（实例创建层）

- **唯一结论 = `context.getAttrs().computeIfAbsent(CONTEXT_KEY, k -> new OrmSessionRegistry())`（TransactionRegistry 同型）**。载体 = `BaseContext.getAttrs()`（:191 已暴露 ConcurrentHashMap，attributes CHM :46 在案）——单步原子安全、缺陷在 get→new→setAttribute 复合层；computeIfAbsent 使双线程并发首触恰好一实例。**替身 context 约束声明**：非 BaseContext 谱系的替身 IContext 实现若 getAttrs 返回非 CHM，原子性不成立——契约落 owner doc：`IContext.getAttrs()` 实现方须返回并发安全 Map（接口 :110 契约随 Phase 3 落账句）；平台侧零触碰（R1-04 已收口面）。
- **put/restore 原子化（C2-003 吸收子项）**：`runInNewSession`/`runInNewSessionAsync` finally 恢复改为**原子 put**（registry 单步 put，ContextualizedRegistry 底层 CHM 单步安全）；**close 判定唯一规则 = put 返回值==session 才静默关闭，否则 WARN + 仍无条件 close 本次会话**——close 幂等（:768-770 在案）兜底消除「跳过-关闭」分支的会话泄漏半边；`unregisterSession` 公共 API 升两参身份一致移除（`remove(key, value)` 语义，防并发替换误删他方会话）；`skip-session-close-when-not-current-session` info 升 **WARN**（rule 24 可观测）。
- **closed context 首触行为变化声明**：computeIfAbsent 直写 `getAttrs()` 绕过既有 `setAttribute` 的 `checkClosed` 校验——closed context 上首触 ORM 将创建并注册 registry（而非抛 IllegalStateException）；该变化为可接受语义收窄（closed context 上后续 session 操作由 session/env 侧校验拦截），随 owner doc 契约句登记（Phase 3）。
- **C1-023/C2-003 吸收面闭环**：TransactionRegistry get-or-create 逐字节同构随同一形态修复（M1）；单槽 put/restore 原子性 + finally 无条件 close 兜底为 C2-003 两子项 (a)(b)，同 diff 落地。
- **N8 两层不混淆注记**：本裁定只修**实例创建层**（registry 单例本身）；事务**协议层**（registry 内 txn 查-建-放）归裁定⑧，两层独立不互相替代。

### 3.2 裁定②③：模型发布可见性与组件预计算

- **裁定② volatile 化三点位**：`DefaultOrmModelProvider.ormModel`（:17）、`TenantAwareOrmModelProvider.ormModel`（:16）、`LoadedOrmModel.ormInterceptor`（:33）加 `volatile`（`StaticOrmModelProvider` final 字段为同族正确反证）。**clearCache 顺序唯一结论 = `interceptorCache.clear()` 先于新模型赋值发布**——volatile 写作发布边，混合态**单向化**：唯一残留窗 = 旧模型 + 新 interceptor（clear 与赋值之间到达的读者），该窗内读者以旧模型结构解释新 interceptor 属降级危害（interceptor 为横切回调、非结构映射）且窗口极窄（两条相邻语句），不承诺全序双态消除；不采整体同步块：读路径每请求热路径，volatile 单写点已足。`setOrmInterceptor` 改引用变化时才写（`if (ormModel.getOrmInterceptor() != interceptor)`），每请求热写降偶发写。TenantAware 同 diff 同修（clearCacheForTenant :46 邻接面同顺序口径）。模型代发布协议切片（C2-007/C1-004）只登记接缝归 R4-18/R4-19，不代做。
- **裁定③ 解析期预计算（预设）**：`colPropIds`/`propIds` 在 `OrmEntityModelInitializer`/`initProps()` 完成组件装配后一次性赋值（与 `colsByPropId` 同法），字段收 final，运行期 getter 纯读——先发布后填充窗口结构性消除（模型对象发布后不可变态）。备选（保留惰性 + volatile + 局部填充最后赋引用）被拒：模型重装载每次重置窗口、热路径 volatile 读无收益。列序钉 `TestOrmComponentModel#testGetColumnPropIdsFollowsDeclarationOrder` 零弱化；C1-016 同族邻接（`LazyLoadOrmModel` topo 面）零触碰。

### 3.3 裁定④⑤⑪：session 全生命周期互斥与异步收尾

- **裁定④ 锁域一次定夺 = session 级 `ReentrantLock`**（非操作级）：session 对象自带 lock 字段，覆盖 flush（:180-234 全段）与 close（:768-791）及写入口族生命周期变更；**操作级（仅 flush 内 CascadeFlusher 段）被拒**——close 与 flush 的交错面（半关闭会话上的遍历）恰在操作级锁域之外，复刻残留。**可重入性论证**：CascadeFlusher 回调链（interceptPreFlush/interceptor/session.flushImmediately 重入 :1008-1030）在同线程内再入 flush/写入口，ReentrantLock 可重入承载，无需 owner-thread 独占形态。**死锁面论证**：锁域内现有调用 = persister DML（经 batchActionQueue→jdbc，不取 context attrs 锁）与 interceptor 用户回调（不取同 session 锁——单 session 单回调链）；唯一跨锁点 = flush 段内 `OrmSessionRegistry.instance()` 无调用（亲读核实），registry computeIfAbsent 的 CHM bin 锁与 session lock 无嵌套获取路径——无环成立；**硬边界**：禁止在 session lock 临界区内调用 `registerSession/unregisterSession` 之外的 context 注册表写操作与外部 listener 回调（锁序与裁定⑧锁约束同纪律）。**静默跳过消除**：`flusher != null → return`（:183-185）废弃，竞争线程锁内等待而非跳过——「事务带未刷写提交」丢更新面闭合；flushImmediately 的 `if (this.flusher == null)` 双建窗随同收敛入锁。**写路径 checkContext 对称**：写入口族 :495-644（save/saveDirectly/updateDirectly/deleteDirectly/lock/delete/flushActionQueue 族，现仅 checkValid+checkNotReadOnly）逐入口补 `checkContext()`（:229-232 复用，读路径已对称），跨线程误用 fail-fast。
- **同族守卫清单落位**：`SessionBatchActionQueue.queues` 惰性创建改 computeIfAbsent（:33-41，覆盖丢弃消除）；`OrmSessionEntityCache` 裸 HashMap/visiting 门（:42/:114/:253-284）纳入 session lock 域声明（flush 段内访问点已在锁内，逐项核对登记边界，不另设第二锁）；`sessionRevVersion` 写点随 flush 段入锁。
- **裁定⑤ 收尾线程纪律 = 收尾回 context 队列（预设）**：`runInNewSessionAsync` whenComplete（:262-269）回调体改为向 context 队列投递「会话收尾」任务（flush/registry 恢复/close 整段，设施具名 = `context.execute(...)`/`ContextTaskQueue`，对齐 thenOnContext 出口泵语义——完成线程只投递不执行）。**降级路径三句声明**：(1) BaseContext 型 context 在队列满/未绑定时 `execute` 降级为**投递线程内联执行**——bound+processingThread 串行化语义不变（内联即投递线程自身为 context 绑定线程），残留的跨线程并发窗口由裁定④ session 互斥兜底，不另设守卫；(2) **closed context 上投递异常被 ContextTaskQueue 吞弃**——该面由裁定⑪关闭钩子兜底（context 关闭流程先于投递吞弃触发会话兜底 close，会话不泄漏）；(3) vert.x 型 IContext 实现为真投递（任务在 context 绑定线程执行，无降级）。备选（引用计数/owner-thread 校验）被拒：计数与 close 幂等语义重复且新增泄漏态。〔精确化：ContextTaskQueue tasks 为无界队列不存在「队列满」，真实触发=enqueue 时 syncing==0〕〔精确化：closed context 异常抛自 BaseContext.execute 的 checkClosed（enqueue 前），随被丢弃 stage 丢失；ContextTaskQueue 吞的是 flush 内 task.run 异常，另一位点〕
- **裁定⑪ 兜底关闭 = context 生命周期反向登记（预设）**：registry `put` 新会话顶替 oldSession 时，向 context 关闭钩子（onClose 属性伴生登记形态，BaseContext.close 零触碰）挂该 session 的兜底 close 登记；future 完成路径收尾时摘除登记。上游永不完成 → context 关闭时兜底 close，注册表滞留泄漏闭合；返回 future 超时兜底被拒（平台设施越界 + 超时值无法通用定夺）。异步入口族七类（runInSessionAsync 委托分支等 F3 枚举）经同一 registry put 位点单一覆盖；泄漏守卫指标 = 挂起登记计数超阈值周期 WARN。

### 3.4 裁定⑥：实体缓存 read-repair 世代化（C1-027 SSOT）

- **世代化形态唯一结论 = nop-orm 模块内世代 fence + 版本化 value + fence CAS 回填**。评审修正：`ICache` 现有原子能力仅为插入-only（computeIfAbsent/putIfAbsent），**不存在条件覆盖 compute**，evict 失效世代亦无存放点——扩展 ICache 触碰未申报的 nop-commons，被拒（§五）；世代 fence 落 nop-orm 模块内，零平台设施触碰。结构：persister 持 `ConcurrentHashMap<String, AtomicLong> genFence`（key = cacheKey，per-globalCache/EntityPersisterImpl 实例一枚）；全局缓存 value 由裸 `Object[]` 升为携带世代号的封装值。
- **三具名变量与单一公式**：`readGen` = load 发起时从 fence 读取的世代快照；`fenceGen` = 回填时刻 fence 当前世代；`valueGen` = 回填值携带世代（= readGen）。**唯一回填公式：仅当 `readGen == fenceGen` 才回填**——实现 = `fence.compareAndSet(readGen, readGen + 1)`，CAS 胜者才执行 put（put 值携带 valueGen=readGen），CAS 败者（evict/他方回填已推进世代）放弃回填；**不再使用「低于/不等即放弃」的不等式表述**（判据只有相等性，无序数比较）。evict 点（:466/:487/:499→evictGlobalCache :553-569，onAfterCommit removeAsync）保持 remove 语义 + **fence incrementAndGet（记失效世代）**——evict 后任何持有旧 readGen 的迟到回填 CAS 必败。集合路径 `CollectionPersisterImpl`（:96-100/:148-154/:172-183/:217-229，同步 put :229 与 `_batchLoad` :148-154）同 diff 同规范（同 fence 机制，key = collection cacheKey）。
- **fence 生命周期**：entry 随对应缓存 key 的活跃度存续——**弱引用清理为预设**（`Map<String, WeakReference<AtomicLong>>` 形态或等效：value 弱可达且无在答回填时 GC 可回收，〔评审 R2 Major 修正：fence entry 初始值不取 0——新建 fence 取 per-persister 单调分配器（如 epoch.incrementAndGet() * 1_000_000），使任何重建 entry 的初始世代严格大于全部历史已发放世代，消除「fence 身份重置×gen-0 快照碰撞」放行旧世代回填的破口；cap 清空对 readGen>0 快照本安全，唯 gen-0 碰撞为唯一破口〕——空 fence 与世代 0 一致，不会误放行旧世代回填，因 value 亦已随缓存逐出）；**容量上界兜底** = 超过 100_000 entry 时整体清空重建并 WARN 一次（清空即全量「首触」，语义安全同上，只损并发回填去重精度不损正确性）。零配置键、零持久化、随 persister 生命周期消亡。
- **C1-027 SSOT 条款（单侧声明）**：本裁定为「失效世代号」设计权威——世代号来源（进程内模块内 fence 单调序，非 DB 列、非时间戳、非 ICache 扩展）、载体（版本化 value + CHM fence）、比对判据（readGen==fenceGen 相等性唯一公式）在此定稿；R4-18（C1-027，批次列勘误 3 以 R4-18 为准）到达时**引用本裁定消费，不重建第二套世代语义**；两 work item 不合并执行（C2-004=read-repair 顺序竞态、C1-027=失效判据世代面，互不替代）。**PA 触发声明**：预设世代号在 value/fence 层实现零 orm 触碰、零 nop-commons 触碰；若演进为「实体表新增世代列」形态，须先在 plan PA 行 + 本文档修订 + draft review 完成 plan-first 申报方可实施。

### 3.5 裁定⑦⑬：抢占 NULL 谓词与 drainTo 出事务

- **裁定⑦ = IS NULL 谓词（预设）**：`tryClaim`（:246-263）example 装配改为按声明抢占字段全集生成谓词——源值非 null 用等值条件、**源值为 null 生成 `col IS NULL`**（SqlBuilder isNull 能力，nop-orm `GenSqlHelper.appendExampleFilter` 旁证面只读不触碰）；三字段全 NULL 时 UPDATE 仍带主键 + 三 IS NULL 条件，CAS 语义恢复（两消费者只有一个匹配成功）。备选 fail-fast（oldValues.isEmpty 拒绝抢占）被拒：合法 NULL 初始态被禁用属行为面收缩。**回归钉穿透谓词生成层**：session mock 屏蔽 WHERE 谓词生成是缺陷结构性不可见根因（C10-006 注记）——钉改走真实 SQL 生成/执行层断言（谓词文本断言 + 真 DB 集成双形态）。
- **裁定⑬ = 事务边界内移（预设）**：`drainTo` 方法级 `@Transactional(REQUIRES_NEW)`（:172）摘除，方法体在无事务上下文执行（消除等待窗口持连）；`waitUntil`（:196）循环体内每轮 `runInTransaction` **短事务包络范围 = loadItems（含 tryClaim）+ `dao.flushSession()`（:204）+ `c.addAll`**——三段同短事务，抢占 CAS 落库与消费可见同界。**N4 实现约束成文**：tryClaim 抢占 CAS 与轮询读必须同一短事务（否则 UPDATE 失去事务串行化前提、CAS 失效）——该约束为裁定⑬验收项，与裁定⑦互不替代、可同变更集落地。**跨裁定依赖句**：⑬的循环体外包络消费裁定⑧的挂起标记机制（无事务上下文下 registry 挂起位保证 loadItems 轮询不误挂入既有事务）——若组 F（⑬）先于组 E（⑧）落地，须同变更集携带⑧挂起标记最小实现，或暂以 `runWithoutTransaction` 形态包络并在 plan 勘误登记该过渡形态。poll(timeout) 有界路径（:139-144）同病随参数归族同修；poll()/drainTo(c,max) 纳秒窗排除项声明保留。「空闲持连」守卫指标 = 空队列轮询期活跃连接数为 0（IPoolMetrics 复用断言）；queryTimeout 子项（C3-006 吸收面）挂本工单验收清单；@Transactional 拦截器装配前提声明保留（未装配则降级 P3 口径，不改判级）。

### 3.6 裁定⑧⑨：事务注册协议原子化（R10 自并单点，同变更集）

- **裁定⑧**：(a) `getTxnState` 主分支（:157-164）的 get+new+register 三步收敛为 registry `compute`/`putIfAbsent` 语义——**compute 体内取 `synchronization ?? newTransaction`**（保留 `DefaultTransactionManager.getRegisteredTransaction` :163-168 的 `getTransactionFactory(querySpace).getSynchronization(querySpace)` 回退，JTA 型外部同步器不被原子化遮蔽；compute 函数体内无 I/O 无锁获取）——并发下败者 close 自建事务、采用胜者（半个成功消除）；`createNewTransaction`（:338/:352）put 覆盖 prevTxn 面随同归一。(b) `runWithoutTransaction`（:170-181）/`NOT_SUPPORTED`（:291-299）注销-执行-重挂窗口改**挂起标记**（registry entry 挂 suspended 位，查找侧跳过）——per-context 事务门闩为备选不采（引锁放大）。**锁序约束（硬边界）**：若引入任何注册表门闩，锁只保护注册表结构变更，**不得覆盖 task 执行与 commit**（listener 外部回调），对齐 merge-adjudication N8 与锁序图补锁裁定。(c) `cleanupTransaction`（:406-438）unregister 失败先做身份比对（registry 现值==本事务？）：一致→真异常保留；不一致→并发方已接管，**降级 WARN 非抛错**——伪 ERR_TXN_NOT_REGISTERED（提交成功误报触发上游重试与数据重复）消除。；compute 体异常路径：已半建事务对象 safeClose（沿 createNewTransaction :339-345 IoHelper.safeCloseObject 先例），CHM 不落 entry、异常传播，调用方重入 getTxnState 重走原子路径
- **裁定⑨（与⑧同一变更集）**：`subTransactions` 惰性 init `synchronized(this)`（:105-111）+ 全部迭代消费点（commitSubTransactions :365-372 / commitSubTransactionsAsync :351-363 / rollbackSubTransactions :194-210 / rollbackSubTransactionsAsync :212-221 / closeSubTransactions :437-443 / openSubTransactions :445-451 / isSubTxnRollbackOnly :146-154）改持锁快照 `new ArrayList<>(subTransactions.values())` 后迭代 + **快照外迟到子事务显式拒绝**（提交开始后 addSubTransaction 抛 fail-fast，禁静默不提交丢写）；listeners COW 纪律（:54-56）保持，同一状态机两集合部件同步纪律归一。备选（单线程契约声明）被拒：多数据源 txnGroupMap 并行分支为设计场景，声明式排除即放弃 P1 缺陷面。

### 3.7 裁定⑩⑫：连接生命周期锁纪律与 flush 顺序

- **裁定⑩ = 事务对象 monitor 串行化五触点（预设）**：`getConnection`/`doCommit`/`doRollback`/`releaseConnection`/`doClose`（:61-125）以 JdbcTransaction 实例为 monitor 串行化——双取池连接泄漏（:61-75 双取窗）与 eagerRelease 后并发重取僵尸连接（:92-100 提交后写丢失）闭合；临界区内含池获取（dataSource.get()），**禁止锁内再取 registry/CHM 全局锁**（锁序裁定，与裁定⑧锁约束同纪律）。CAS 状态机备选被拒（五触点全状态化改造面大、monitor 已充分）。`doCommit` null 早退（:93）**补 WARN 留痕**（显式可观测，非静默 no-op；不改抛错语义——eagerRelease+正常 close 双路径下 null 为合法终态）。getConnection0 单线程排除项保留；C1-026 吸收面（check-then-act 原子性）随同一修复闭合。
- **裁定⑫ = dirty 移至成功后（预设）**：`flush()`（:211-214）的 `dirty=false`/`cache.clearDirty()` 移至 `batchActionQueue.flush()` **成功返回之后**（对齐 `flushImmediately` :1008-1030 oldDirty 保守恢复模板——flush 中 internalMarkDirty 再置位不被回滚覆盖的既有注释语义保持）；批 DML 失败后重试 flush 不再被 `!dirty` 短路 no-op，捕获-重试编排的「半个成功」残留消除。备选（失败保守恢复 dirty=true）被拒：恢复窗内 dirty 状态两义、重试语义弱于顺序修正。`BatchActionQueueImpl.flushAsync` finally `actionMap.clear()`（:234，C2-006 吸收增量）同 diff 核对：notifyUnsubmittedFailure 已覆盖未提交部分，**「已提交失败」部分回调面**逐项核对登记（不扩批）；saveDirectly/updateDirectly/deleteDirectly 承继面登记不扩批。

### 3.8 守卫指标与可观测一览（键名/指标名一次定夺，零二源）

| 名称 | 形态 | 裁定 | 语义 |
|------|------|------|------|
| `nop.orm.session.pending-close` | gauge（挂起兜底登记计数） | ⑪ | runInNewSessionAsync 上游未完成、登记待兜底关闭的会话数；非零即泄漏面可见，超阈值（缺省 100）周期 WARN |
| `nop.orm.data.source.idle-connections` | 断言指标（测试证据，IPoolMetrics 复用） | ⑬ | 空队列轮询期活跃持有连接数恒 0（红⑬断言载体） |
| `nop.orm.skip-session-close-when-not-current-session` | WARN 日志（info 升级） | ① | put/restore 不匹配分支可观测（rule 24，非静默跳过） |
| `nop.dao.txn.cleanup-identity-mismatch` | WARN 日志（新增） | ⑧ | cleanup 身份比对不符降级路径留痕（伪 ERR_TXN_NOT_REGISTERED 消除后仍可见） |
| `nop.dao.jdbc.commit-on-released-connection` | WARN 日志（新增） | ⑩ | doCommit null 早退显式留痕（非无痕 no-op） |
| 世代 fence（nop-orm 模块内 per-persister `CHM<String, AtomicLong>`） | 无配置键、无持久化 | ⑥ | 单调世代序；evict incrementAndGet 记失效世代、回填经 CAS(readGen→readGen+1) 相等性判据；弱引用清理 + 容量上界 100_000 兜底（§3.4 生命周期）；进程重启归零安全（缓存同进程生命周期） |

零新配置键声明：本批全部 13 裁定不引入任何 `@cfg:` 配置键（世代号/指标为内置形态），与 R01 族保留参数零交集（本批无账本/保留期面）。

### 3.9 零 orm 触碰声明与两处条件申报条款

- **零 orm 触碰声明（13/13 PA 判定兑现）**：全部 13 成员 = `src/main/java` 纯代码修复；站点分布于 nop-orm/nop-orm-model/nop-dao/nop-orm-data 四模块，无任何 `model/*.orm.xml` 触碰（PA 判定表见 plan Baseline，13/13 判「否」）。
- **条件申报条款一（C2-004 世代列形态）**：裁定⑥若演进为「实体表新增世代列」，触发 ORM 模型结构 plan-first——须先在 plan PA 行 + 本文档修订 + draft review 申报后方可实施；预设分支不触。
- **条件申报条款二（ContextAttrs 平台工具形态）**：裁定①预设为两 registry 模块内联 `computeIfAbsent`，零 nop-api-core 触碰；若 Phase 2 执行中确需抽平台级工具落 `io.nop.api.core.*`，触发 nop-api-core plan-first，须在设计文档修订申报并列入 draft review 审查面后方可实施；预设分支不触。

## 四、裁定⑭：测试缝与零回退钉

### 4.1 红①–红⑬驱动器（先红后绿，E2；缺陷态红判据 → 修复后绿口径）

| 红 | 成员 | 载体形态 | 缺陷态红 → 修复后绿 |
|----|------|---------|-------------------|
| ① | C1-001 | 双线程并发首触 instance()（真/替身 context）+ put/restore 交错序列 | 双实例/漏 close 红 → 同实例 + 无条件 close 绿（C10-001） |
| ② | C1-003 | reloadModel 后并发读断言 | 旧模型可见红 → volatile 后新模型可见绿 |
| ③ | C1-015 | 并发首触组件列映射 | 空/部分 Map 红 → 完整只读绿 |
| ④ | C2-001 | T2 并发 flush 后事务提交断言 | 静默跳过丢刷写红 → 互斥绿（C10-004 半边） |
| ⑤ | C2-002 | 完成线程收尾与在用线程交错 | 半操作红 → 收尾队列化绿（C10-002） |
| ⑥ | C2-004 | miss 回填 vs 提交 evict 交错 | 缓存=v1 无界驻留红 → 世代围栏放弃回填绿（C10-005 半边） |
| ⑦ | C2-005 | 两消费者 NULL 初始行（穿透谓词生成层：SQL 文本 + 真 DB） | 双双匹配红 → IS NULL 谓词单方匹配绿（C10-006） |
| ⑧ | C2-013 | 并发注册交错 + cleanup 交错 | 提交成功误报 ERR_TXN_NOT_REGISTERED 红 → 身份比对降级 WARN 绿（C10-007） |
| ⑨ | C2-015 | 组提交迭代期并发注册 | 迟到子事务静默不提交红 → 快照 + 显式拒绝绿（C10-007） |
| ⑩ | C2-017 | 并行 getConnection 连接身份断言 | 双取泄漏红 → 单锁串行绿；doCommit null 早退 WARN 断言（C10-008） |
| ⑪ | C3-001 | 上游永不完成 + context 关闭钩子 | session 永不关红 → 兜底关闭绿（C10-002） |
| ⑫ | C3-005 | batchActionQueue.flush 失败相位注入（dirty=false 之后）→ 重试 flush | 重试短路 no-op 红 → 顺序修正后重试生效绿；装载相位旧钉保持绿（C10-009） |
| ⑬ | C6-001 | take 空队列活跃连接数断言 | 恒 1 持连红 → 空闲零持有绿（C10-010） |

### 4.2 测试资产落位

新增/扩展测试类按站点归位：nop-orm（TestOrmSessionRegistry 并发首触、TestOrmTemplateAsync 扩展、TestOrmSessionConcurrent 新建、TestFlushFailure 扩展 batchActionQueue 相位变体、TestGlobalCache 扩展世代交错、TestOrmComponentModel 扩展并发钉）、nop-dao（TestTransactionTemplate/TestTransactionTemplateAsync/TestDefaultTransactionManager 扩展协议交错、TestJdbcTransaction 并发连接钉）、nop-orm-data（TestDaoEntityBlockingSourceClaim 扩展 NULL 谓词穿透钉 + TestDaoEntityBlockingSourceDrainTo 新建持连钉）。补测类声明：新增断言即新钉、缺陷态红→修复后绿；红绿快照落 `_tmp/` 并登记。

### 4.3 零回退钉（全列，钉零回改逐条 git diff 为空核对）

- `TestLazyLoadOrmModel`（nop-orm-model，**全单元唯一真并发钉**，C1-016 所在，R4-18）——**零触碰零弱化**；`LazyLoadOrmModel` 锁纪律既有正确面（putEntityModel/checkTopoEntryReady 同锁约束）零 diff。
- `TestFlushFailure#testFlushFailureClearsFlushVisiting`（:34，装载相位钉）——**仅并存不弱化**（C10-009 钉错相位注记：新钉相位 = batchActionQueue.flush 失败[dirty=false 之后]，与旧钉语义对照留痕；唯一允许的钉改写即此新增，旧钉本体零回改）。
- `TestOrmComponentModel#testGetColumnPropIdsFollowsDeclarationOrder`（:32 列序钉）——零弱化。
- `TestDaoEntityBlockingSourceClaim` 两钉（含 :99 testClaimConflictSkipsItem 非 NULL 冲突面）——零回退（非 NULL 等值语义不变）。
- `TestTransactionTemplate`×12（含 :327 testMultipleCommit = C10-017 现状容忍钉，C2-016 面，R4-19）——**零触碰**；`TestTransactionTemplateAsync`×4、`TestDefaultTransactionManager`×8、`TestJdbcTransactionAutoCommitRestore`×2 邻接钉族——零回退。
- `TestGlobalCache#testGlobalCache`/`testCollectionGlobalCacheEvictedAfterChange`（单线程 evict 正确性钉）——零回退。
- `BaseContext`/`ContextProvider`/`thenOnContext` 平台语义（plan 2499 落地面）——零触碰；nop-commons `ContextualizedRegistry` 原语零触碰（缺陷在复合操作层）。
- 上游环境抖动按 plan 2517 Baseline 勘误 5 三联裁定（stash 基线同红 / solo 绿 / 恢复复跑全绿）处理留痕，不得静默重跑或跳过。

### 4.4 组 A–F 落位与同变更集绑定（Phase 2 提交纪律对齐）

| 组 | 成员（裁定） | 模块 | 同变更集绑定 |
|----|-------------|------|-------------|
| A | C1-001（①）+ C2-002（⑤）+ C3-001（⑪） | nop-orm impl | ⑤⑪同变更集（收尾队列化与兜底登记共享 registry put 位点） |
| B | C2-001（④）+ C3-005（⑫，含 C2-006 吸收面） | nop-orm session/persister | ④与⑤⑪共享会话互斥设计 |
| C | C1-003（②）+ C1-015（③） | nop-orm factory + nop-orm-model | 增补段②一体 |
| D | C2-004（⑥） | nop-orm persister | C1-027 SSOT 单侧声明 |
| E | C2-013（⑧）+ C2-015（⑨）+ C2-017（⑩，含 C1-026 吸收面） | nop-dao txn | ⑧⑨同变更集（R10 自并单点，硬绑定） |
| F | C2-005（⑦）+ C6-001（⑬，含 C3-006 吸收面） | nop-orm-data | ⑦⑬同变更集（N4 约束硬绑定）；F 先于 E 落地时⑬依赖⑧挂起标记——同变更集带最小实现或暂用 runWithoutTransaction 并登记（§3.5 跨裁定依赖句） |

### 4.5 端到端验证义务（rule 22，归 Phase 2）

- **链一（上下文并发主链，覆盖裁定①④⑧⑨⑩）**：真/替身 context 上并发首触 ORM 访问（registry 单实例断言）→ session 注册 → 并发 flush 互斥（无静默跳过）→ 事务注册协议并发（REQUIRED 组语义保持）→ 组事务提交/回滚（子事务无丢失）→ 连接释放（无双取泄漏）。
- **链二（异步/资源链，覆盖裁定⑤⑦⑪⑬⑥）**：runInNewSessionAsync 挂起与完成两路径（收尾队列化/兜底关闭 + pending-close gauge 变化）→ drainTo 空闲零持连 → 有数据短事务抢占单方匹配（NULL 初始行穿透谓词层）→ read-repair 世代交错场景（回填放弃断言）。

### 4.6 锁序总表（全批新增锁域一致性核对）

| 锁域 | 裁定 | 允许嵌套 | 禁止 |
|------|------|---------|------|
| context attrs CHM bin 锁 | ① | 无（computeIfAbsent 函数体仅 new，无 I/O 无锁获取） | 函数体内取任何其他锁 |
| session ReentrantLock | ④ | 可重入（同线程 CascadeFlusher 回调链） | 临界区内 context 注册表写操作、外部 listener 回调 |
| subTransactions synchronized(this) | ⑨ | 子事务自身 commit/rollback（外部回调在快照迭代中锁外执行） | 持锁执行子事务 commit（快照后锁外） |
| JdbcTransaction monitor | ⑩ | 临界区内 dataSource.get()（池获取） | 锁内取 registry/CHM 全局锁 |

**同对象注（⑨×⑩）**：dao 默认装配下 `AbstractTransaction` 子事务与 `JdbcTransaction` 可为同一实例——⑨ `synchronized(this)` 与 ⑩ monitor 在该实例上为**同一监视器（可重入安全）**；**禁止⑩实现为独立锁对象**（如私有 lock 字段）：独立锁将使「父事务持 subTxn 锁迭代快照 → 子事务 commit 取⑩独立锁」与「⑩独立锁内路径回调取 subTxn 锁」构成混合 monitor 交错序（ABBA 面）；⑩ monitor 必须以事务实例自身为 monitor（或显式声明子类监视器=实例自身），保持可重入同一性。

四域间无固定获取序冲突：attrs bin 锁瞬时、session lock 不在 attrs/bin 锁内获取（§3.1/§3.3 论证）、subTxn 锁与 JdbcTransaction monitor 同对象可重入（上注），跨对象路径（多数据源 subTxn 为独立 JdbcTransaction 实例）顺序为「subTxn 锁外调子事务 commit → 子事务 monitor」，反向（monitor 内取他实例 subTxn 锁）不存在——无环成立，closure audit 抽查临界区边界。

### 4.7 C10 集落名（随本体同 diff，E2）

**全量承 6**：C10-001（红①）、C10-002（红⑤⑪）、C10-006（红⑦）、C10-007（红⑧⑨）、C10-008（红⑩）、C10-010（红⑬）；**半边承 3**：C10-003（红②③，C1-003 面）、C10-004（红④，C2-001 面）、C10-005（红⑥，C2-004 面）——R4-18/R4-19 成员面（C1-004/C2-007/C1-008(c)/C1-002/C1-027/C1-010 等）随其本体批落地，半边拆分以 plan 勘误 4 口径为准；**C10-009 注记**（红⑫钉错相位条款，§4.3 旧钉并存）。接线与无静默跳过义务（rule 23/24）：computeIfAbsent 真实被双 registry 入口消费断言、session 互斥真实在并发 flush 用例生效、世代围栏真实拦截迟到回填、短事务包络真实包裹 tryClaim（事务/连接计数证据）；cleanup 身份不符降级留痕、迟到子事务显式拒绝、doCommit null WARN、新增公共分支零空方法体/TODO。

## 五、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|---------|
| ① 平台级 ContextAttrs 工具落 nop-api-core | 触发 plan-first 申报 + 本批零平台触碰预设；模块内联 computeIfAbsent 已闭合（条件申报条款二保留出口） |
| ② clearCache 整体同步块 | 读路径每请求热路径加锁无收益；volatile 单写点 + clear 先行序已消除混合态 |
| ③ 惰性 + volatile + 最后赋引用 | 模型重装载每次重置竞态窗；解析期预计算结构性消除 |
| ④ 操作级锁（仅 CascadeFlusher 段） | close×flush 交错面在锁域外复刻残留；session 级一次闭合 |
| ④ owner-thread fail-fast 单独作默认 |CascadeFlusher 回调重入/同 context 多分支合法并发被误杀；锁内串行语义最窄 |
| ⑤ 引用计数收尾 | 与 close 幂等语义重复且新增计数泄漏态；队列化对齐平台出口泵先例 |
| ⑥ evict 侧延迟双删 / 时间戳围栏 | 窗口概率性不消除无界驻留 / 墙钟回拨面；版本化 value 比对确定性围栏 |
| ⑦ oldValues.isEmpty() fail-fast | 合法 NULL 初始态被禁用属行为面收缩；IS NULL 谓词恢复 CAS 本体 |
| ⑧ per-context 事务门闩作默认 | 锁放大覆盖执行/commit 面；挂起标记零锁闭合窗口（门闩仅备选且受锁序硬边界约束） |
| ⑨ 单线程契约声明 | 多数据源并行分支为设计场景；声明式排除即放弃 P1 丢写面 |
| ⑩ CAS 状态机 | 五触点全状态化改造面大；monitor 串行化最小充分 |
| ⑪ future 超时兜底 | 超时值无法通用定夺 + 平台设施越界；context 生命周期登记零新键闭合 |
| ⑫ 失败保守恢复 dirty=true | 恢复窗 dirty 两义；顺序修正 + oldDirty 模板语义最强 |
| ⑬ 每轮独立短事务不含 tryClaim | 破坏 N4 同事务约束即抢占 CAS 失效；短事务包络必须含 loadItems 全体 |
| ⑥ 扩展 ICache 原子 compute 能力（nop-commons） | 触碰未申报的 nop-commons 平台设施（条件覆盖原语为平台面能力扩展，须独立申报）；模块内 CHM fence + CAS 零平台触碰即闭合 |

## 六、与已有设计的关系

- [bindcancel-context-unification.md](bindcancel-context-unification.md)：R1-04 平台上下文传播/收尾泵先例——裁定⑤收尾队列化消费其 thenOnContext 出口泵语义，平台侧零代做零触碰；CL-401/402 可达性根因已随 plan 2499 收口。
- [ioc-startup-graph-and-config-publish-protocol.md](ioc-startup-graph-and-config-publish-protocol.md) §3.3：shardSelector 单 volatile 发布先例——裁定②同型投影，模型代发布协议切片（C2-007/C1-004）接缝归 R4-18/R4-19 只登记不代做。
- R10 自并族（inventory-ledgers §二.2 族行）：裁定⑧⑨同变更集承载，族修复口径「归一/补偿/声明」取归一；台账 C2-013/C2-015 独立收敛不双计。
- **下游 SSOT**：R4-18（8.58）到达时 (a) C1-027 引用裁定⑥消费世代号裁定不重建、不与 C2-004 合并执行；(b) C1-016 唯一真并发钉零弱化义务确认（§4.3）。R4-19（8.59）C2-016 现状容忍钉边界确认（§4.3 testMultipleCommit 零触碰）。就绪注记归 plan 2517 Phase 4。
- owner doc 落点（Phase 3 统一落账，本 Phase 零 owner doc 变更）：`docs-for-ai/02-core-guides/model-first-development.md`（session/事务并发互斥契约节、模型热刷新可见性与组件预计算声明、全局缓存世代语义声明[含 C1-027 SSOT 指向]、IBlockingSource 抢占契约节、drainTo 事务边界契约句、事务注册协议原子性契约句、连接生命周期锁纪律句、flush 失败重试语义句、`IContext.getAttrs()` 并发 Map 契约句、源码锚点补行）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
