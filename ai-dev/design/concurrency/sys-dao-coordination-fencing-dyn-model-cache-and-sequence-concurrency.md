# sys-dao DB 行协调 fencing/动态模型缓存与序列并发/懒初始化卫生契约（R3-02 5.2 批 10 成员+测试缝裁定）

> Status: active（plan 2525 Phase 1 产出物；独立设计评审 R2'（agent_6d3c482e，2026-11-02）APPROVED with corrections——2 Major[F1 R17 矩阵事实源/F2 ICache 不可达 API 形态改裁]+4 Minor 全部落稿闭环，转 active 后为该批实施唯一设计裁决权威。评审史：R2 初稿评审因用量上限中断未全文落盘[摘要见 commit 3052ba06a0]，其可核实发现已并入修订，其余由 R2' fresh 复审覆盖）
> Source: 批次成员表、PA 判定、勘误登记与红绿判据以执行计划 `ai-dev/plans/2525-cra-phase8.32-r3-02-sys-dyn.md` 为准（本文档为最终状态文档，勘误不落此处）；成员发现详情 = WI 5.2 审计目录 `ai-dev/audits/2026-10/2026-10-21-2148-deep-audit-nop-sys-dyn-concurrency/`（merge-adjudication §一 裁定 6 项 :34-41 / §二 缺陷族表 :47-56 / §六 权威表 :100-118）；行号 = live @ `69e95df564` 全文亲读核实值（plan 基线 `bf47511cac` 后零成员面漂移，两处测试计数漂移见 §八勘误）
> 实现锚：nop-sys-dao `SysDaoResourceLockManager`（tryLockWithLease :114-174[:123 saveNew 首试 / :142-156 过期删除窗 / :148-151 deleteFrom 条件仅 lockName+lockGroup+version **无 holderId**] / backoff :186-193 / saveNew :204-224[version 恒 0] / tryResetLease :227-245[:235-242 条件已带 holderId，:237 set version+1 后**零 setVersion 回写**] / isHoldingLock :248-257[依赖内存 version 一致] / releaseLock :260-273[:265-269 已带 holderId+version]）、`SysDaoLeaderElector`（tryRenewLease :119-127[:121 orm_disableVersionCheckError(true) 无条件续期] / checkLeader :78-110 / refreshLeader :112-117 / checkFollower :129-166[:144/:160 两 changeLeader 入口] / tryBecomeLeader :168-186[:180 saveEntityDirectly 主键冲突互斥] / changeLeader :194-217[:198 epoch 本地+1 / :204 关版本检查 / :208 orm_readonly 判定 / :214 onBecomeLeader] / restartElection :219-238）、`NonBroadcastEventProcessor`（claim :127-140[:135 一次性 leaseExpireTime / :139 tryUpdateManyWithVersionCheck] / process :142-149 / handleResult :151-186[:177 updateEntityDirectly 无条件写] / handleProcessEventError :188-201[:200 同面]）、`SysDaoMessageService`（durableSubscriptions CHM :143 / leaseTimeout :139 / ensureNonBroadcastProcessor :305-325[:306 判空/:307 new，字段 :133 非 volatile] / ensureBroadcastProcessor :375-386[:376 同型，字段 :131] / invokeDurableConsumers :509-536[:514 warn 后置 PROCESSED 丢弃] / invokeConsumer :538-559[:543 FutureHelper.syncGet 无超时] / subscribe :566-579[:569 computeIfAbsent+add] / resolveSubscriberId :581-589 / DurableSubscription.cancel :607-620[:609 delegate.cancel / :610 get / :612 remove(state) / :613-618 判空摘键 / :617 getConsumers().remove 条件移除]）、`SysSequenceGenerator`（cache :56 / defaultCache :58-59[TTL 60s] / clearCache :97-99 / removeCache :101-103 / generateLong :183-201[:185 synchronized(item) 内 :199 syncFromDb] / generateString :204-228[:211 同型] / syncFromDb :230-241 / doSyncFromDb :243-266[:246 session.lock 行锁等待] / findSeqItem :268-298[:282-286 SEQ_DEFAULT 回退共享 item + :285 defaultCache.put]）；nop-sys-service `SysCompactExtFieldHelper`（双 volatile :24-25 / refreshCache :32-56[:51-52 两行赋值非原子] / 写时复制注释 :50）；nop-dyn-service `InMemoryCodeCache`（类只读发布契约注释 :45-49 / mergedStore/dynResourceStore volatile :78-79 / clear :91-100 / addModule :127-133[:131 runOnLoadModule] / removeModule :135-148[:140 runOnUnloadModule / :147 clearMergedStore] / genViewFile :247-260[:259 addToMergedStore] / genPageFile :283-296[:295 同] / genBizObjFiles :298-332[:309-310 捕获已发布实例 / :326 publishedMerged.merge 原地结构修改 / :327-328 回写] / clearMergedStore :334-337 / getOrmModel :349-352[:346-348 ABBA 锁序注释] / getResourceStore :400-417[:406 moduleStores merge 重建 / :409 DynResourceStore 挂 prepare 回调] / addToMergedStore :419-425[:421 addResource 原地写]）、`DynResourceStore`（getResource synchronized :20-28[:22 命中读也过全局 monitor / :26 prepare.accept miss 生成] / getChildren :30-33 无锁 / saveResource :40-43 管理写路径）、`DynCodeGen`（tenantCache :77-78[LocalCache maximumSize=CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE 默认 100] / destroy :97-113[:105-110 getAllKeys+removeTenantCache get+remove 两步] / initTenantCache :130-136[runInitializeTenantTask 单飞通道] / getCodeCache :142-148 / getTenantCodeCache :151-168[:160 synchronized(ref) DCL——单飞正确有钉] / getUsedTenantIds :198-200 / clearForTenant :210-214 / removeTenantCache :219-223 / removeDynModule :367-369[全仓库零调用方悬空] / reloadModel :394-405 / prepareUnloadModule :447-449[平台钩子空实现] / prepareResource :485-534[锁内 miss 生成回调：DB+XPL]）、`DynOrmModelProvider`（entityModelLoader :31 非 volatile / getEntityModelLoader :41-45）、`NopDynModuleBizModel`（saveEntity :61-65 / generateByAI :138-174[:151 注释自证无唯一索引 / :152-155 findFirstByExample TOCTOU 检查 / :169-170 save+flush / :172-173 generateForModule+reloadModel] / ERR_DYN_MODULE_NAME_EXISTS 已在 :155）、`NopDynFunctionMetaBizModel`（afterEntityChange 先例 :31-47[:36 METHOD_DELETE 集合移除 / :46 generateBizModel(true,false)]）、`NopDynEntityMetaBizModel`（全类 :23-44 零变更钩子）、`nop-dyn/model/nop-dyn.orm.xml`（NopDynModule 实体 :210-213[tableName :212 / moduleName 列 :218-219 mandatory precision=100 / 现无 indexes 段，columns 段止于 :251]）；唯一索引写法先例 = `nop-sys/model/nop-sys.orm.xml` :550-559（`<indexes><index name unique="false"><column/></index></indexes>`，xdef 同一 schema 承载 unique 属性）；再生成通道 = `nop-dyn/nop-dyn-codegen/postcompile/gen-orm.xgen`（maven install 通道自动执行）；R17 对齐先例 = plan 2503 落地面 **DebeziumMessageSource**（nop-message-debezium，source monitor 复合操作原子化 + 注册后置 start 成功，commit `03e6203a46`）与 LocalMessageService WI23 身份注销（:75-82，plan 23）两处[R2' Major-1 事实源分行修正]；平台对照零触碰面 = `FutureHelper`（nop-kernel/nop-api-core，syncGet :158 / tryResolve :177 / **waitResult(Future,long) :262 既有带超时等待 API[R2 勘误修正——消费不新增]**）与 `IResourceLockManager` 契约（消费面零修改）
> Related: [host-startup-readiness-gate-future-exception-termination-upload-close-and-credential-pair-concurrency.md](host-startup-readiness-gate-future-exception-termination-upload-close-and-credential-pair-concurrency.md)（plan 2524——本文结构模板与密度基准 + 挂死类红钉失败模式=超时预算先例）；[ledger-retention-policy.md](ledger-retention-policy.md)（R01 族 SSOT——5.2-MA-04 已随 plan 2502 按裁定⑤落地，本批零动作仅注记维持）；[cluster-election-lifecycle-instance-snapshot-and-rpc-termination-concurrency.md](cluster-election-lifecycle-instance-snapshot-and-rpc-termination-concurrency.md)（plan 2521——4.2 选主纪律族先例，裁定①子批②引用不重开）；plan 2503（R1-08 4.1-MA-05 订阅生命周期族已落地形态——裁定③ R17 对齐矩阵的事实源）；plan 2511（R2-13 条件更新纪律——裁定①子批②族纪律参照，异单元禁照抄细节）

## 一、裁定总表

| 裁定 | 成员 | 唯一结论 |
|------|------|---------|
| ① | 5.2-MA-01（P1，C2-001 主 + C2-002/C2-004/C2-007/C2-102 吸收；三子批同序列交付） | **条件写/fencing 统一形态**：子批① 过期删除补 holderId 条件（:148-151 对齐 releaseLock :265-269 已修形态）+ tryResetLease 成功回写 `setVersion(v+1)` 恢复锁行状态机（C2-002 激活回升条款注记随行）；子批② changeLeader/tryRenewLease 改条件更新按影响行数判定，版本检查不再关闭（:121/:204 两处 disableVersionCheckError 撤除）；子批③ 续租挂点 = invokeConsumer 有界等待循环本体（等待切片即续租切片，R2 Blocker 修订——process 线程 dispatch 阻塞期间无独立续约时段）+ handleResult/handleProcessEventError 两站点（:177/:200）改条件写（version+leaseOwner 谓词）0 行放弃写；豁免枚举 forceUnlock/tryBecomeLeader/restartElection/refreshLeader 闭合（§3.1）；MA-01×I-05 协同矩阵成文（§3.10） |
| ② | 5.2-I-01（P1，C1-001） | **mergedStore 写时复制整体替换**：genBizObjFiles 恢复路径（:326）与 addToMergedStore（:421）不再对已发布实例原地 merge/addResource——写点持 synchronized 重建新 InMemoryResourceStore 后整体替换 volatile 引用；synchronized+volatile 保留，**不触反向钉死**（merge-adjudication §一.3#6 相容裁定）；内核 ResourceTreeNode 零触碰 |
| ③ | 5.2-I-02（P1，C1-002；R17 族对齐） | **cancel 判空+摘键改 `durableSubscriptions.compute(topic,…)` 单步**（与 subscribe :569 computeIfAbsent 同 CHM bin 锁互斥）；getConsumers 条件移除（:617）语义保持在摘键成功分支执行；R17 对齐矩阵三兑现点 = 复合操作同键互斥 / 按 SubscriptionState 身份移除 / 注册注销配对（§3.3） |
| ④ | 5.2-MA-02（P2，C2-003 主 + C2-006/C2-101 吸收；反向钉死同 diff） | **getResource 命中路径无锁化**：DynResourceStore.getResource（:20-28）去 synchronized——先无锁读，miss 时 synchronized(this) 内二次确认后执行 prepare（DCL，范本 DynCodeGen.getTenantCodeCache :151-168）；**钩子移出案否决**（§3.4/§六），「锁内用户钩子」残留面登记 nop-dyn.md 生成期窗口声明；锁序 this→CHM 单向不变（TestInMemoryCodeCacheDeadlock 零回退）；反向钉死同 diff 改写 = 结构断言预设（getResource 无 synchronized + 三生成方法 synchronized 与 volatile 断言保留），禁纯删 |
| ⑤ | 5.2-MA-03（P2，C6-002 主 + C6-102/C1-101 吸收） | **三转变点对称治理**：①tenantCache 注册 removalListener 执行 clear()+on-unload（回调线程代价论证见 §3.5，异常吞掉记 log 不杀 Caffeine 内部）；②冷启动维持同步 DCL 单飞（后台化否决——可见性语义扩张），容器上限 ≥ 活跃租户数语义落 nop-dyn.md owner doc；③destroy 零平台面闭合 = remove 原子摘除→摘除实例 clear()（ICache 无 computeIfPresent[R2' Major-2 改裁]）+ DCL 发布后复验自愈；getUsedTenantIds 按「缓存驻留」语义精确化声明 |
| ⑥ | 5.2-MA-05（P2，C2-005 主 + C1-007 吸收） | **回退项按 seqName 各建 item**（findSeqItem :282-286 回退分支不再共享 SEQ_DEFAULT 锁对象）+ DB 环节语句/锁等待超时上限（独立配置键承载）+ clearCache/removeCache（:97-103）同步 defaultCache.invalidate/invalidateAll；generateLong/generateString 两入口同 diff；批量段号预取不扩张 |
| ⑦ | 5.2-MA-06（P2，C1-003 主 + C1-005 吸收 + C1-004 搭车） | **三站点发布卫生**：ensure*Processor×2（:305-325/:375-386）DCL + 字段 :131/:133 volatile 化（测试照抄 TestDynCodeGenTenantCacheInit :73-90 闩锁迁移）；DynOrmModelProvider.entityModelLoader（:31）volatile + DCL；SysCompactExtFieldHelper 双 map 合一不可变 Snapshot record 单 volatile 发布（W-01 随之消解）；TestSysCompactExtFieldHelper 零回退 |
| ⑧ | 5.2-I-03（P2，C1-008；ORM plan-first） | **moduleName 唯一索引**：nop-dyn.orm.xml NopDynModule 实体加 `<indexes>` 段（unique="true" 单列 moduleName，写法沿 nop-sys.orm.xml :550-559 先例）+ gen-orm.xgen 通道再生成（`_` 生成物零手改）；存量库升级含重复行预检 + 手工 DDL；应用层检查（:152-155）保留为顺序路径友好失败；并发双建钉 = 唯一索引违反路径驱动 + 变异回退论证绑定；输家得到 ERR_DYN_MODULE_NAME_EXISTS 归一转译 |
| ⑨ | 5.2-I-04（P2，C6-101） | **NopDynModuleBizModel/NopDynEntityMetaBizModel 补 afterEntityChange**（照抄 NopDynFunctionMetaBizModel :31-47 形态）；删除失效作用域 = 模块级删除走全量 reloadModel、实体级走 generateBizModel(refreshOrmModel=true)；removeDynModule（:367-369）接线为模块级 afterEntityChange 删除分支内部路径；三面不可达钉（codeCache/VFS in-memory 层/bizObjectManager） |
| ⑩ | 5.2-I-05（P2，C8-001；MA-01 协同） | **既有平台 API 分片等待**：invokeConsumer :543 syncGet 改 `FutureHelper.waitResult(stage.toCompletableFuture(), 切片)` 循环（**R2 勘误修正：waitResult :262 既有 API 在案，原「无带超时公共 API」负结论作废**；消费既有 API 零平台变更）；总预算超时 → 返回 `new ConsumeLater(0)`（:533 先例通道，经 handleResult :165-172 处置——**禁与 retryDelayProvider 异常通道混同**，R2 Major 勘正）；超时取值 = 独立配置键 `nop.sys.event.consume-wait-timeout`（默认=leaseTimeout 10000ms）；stall 看门狗否决（Non-Blocking Follow-up）；与裁定①协同矩阵（§3.10）区分各 0 行/中止路径 |
| ⑪ | 测试缝与零回退钉（全批） | 红①–红⑩驱动器表（当前即红五条红①③⑤⑦⑨ / 构造性两条红②④ / 挂死超时一条红⑩ / 假保护转正两条红⑧与 C10-101 面 / 竞态功能钉红⑥）；零回退钉 = 基线 163 tests（nop-sys 100 + nop-dyn 63）+ 既有钉族点名；时序敏感搭车 2 项；C10 13 缺保护 + 2 假保护转正同 diff（§5） |

## 二、背景与动机

R3-02 批 10 项（P1×3 + P2×7）覆盖 nop-sys-dao 与 nop-dyn-service 两域五类缺陷面：**DB 行协调原语域**（MA-01——锁行/选主/事件认领五站点以非条件写或无 fencing 写决出协调结果：过期锁行删除缺 holderId 使重建行 version 恒 0 的版本防护失效（saveNew :204-224 无 setVersion），互斥无声破坏；tryResetLease DB 成功但内存实体不回写使 isHoldingLock :248-257 恒假；changeLeader :204 关版本检查使双 follower 同轮询周期瞬态双主；tryRenewLease :121 无条件续期可延长对手租约；事件认领 :135 一次性租约 + 完成写 :177/:200 无条件直写使慢消费被接管后双节点并发处理同一事件）、**动态模型发布域**（I-01——genBizObjFiles :326 对已发布 mergedStore 原地 merge、addToMergedStore :421 原地 addResource，底层 ResourceTreeNode.children 普通 TreeMap 无同步，违反类自声明只读契约 :45；I-04——模块/实体删除零缓存失效，已删模型含权限逻辑继续服务直至重启）、**生成锁域**（MA-02——getResource :21 全方法 synchronized 使高频命中读与锁内 DB I/O+XPL+用户钩子同一全局临界区，生成锁=平台级限速器）、**生命周期与卫生域**（I-02——cancel 复合序列与 subscribe 交错孤儿化订阅者；MA-03——tenantCache 驱逐/冷启动/destroy 三转变点无对称治理；MA-05——回退路径共享锁 item + 锁内 DB 往返 + defaultCache 只装不清；MA-06——懒初始化无同步非 volatile + 双 volatile 瞬态错配）、**等待域**（I-05——invokeConsumer :543 无超时 syncGet 使挂死消费者永久停摆轮询管线，与 C2-102 双向放大）。本文档一次裁定 10 成员 + 测试缝（裁定⑪），使实施只有一种合法形态；I-03 为批次内唯一 ORM plan-first 触点（授权三件套见 plan 头部 Protected Area 段）。

## 三、核心设计契约

### 3.1 裁定①：DB 行协调原语条件写/fencing 三子批（MA-01）

- **子批①锁行状态机（SysDaoResourceLockManager）**：tryLockWithLease 过期删除窗 :148-151 的 deleteFrom 条件补 `.eq(holderId, existing.getHolderId())`——与同文件已修两处（releaseLock :265-269 / tryResetLease :241）形态对齐，B 竞争者的删除不得命中 A 刚插入的新行（重建行 version 恒 0，仅 version 条件对重建行无效）。tryResetLease :235-242 SQL 成功（executeUpdate==1）后回写内存实体：`entity.setVersion(v+1)` + expireAt 同步回写——isHoldingLock :256 的 version 相等判定与 releaseLock :269 的删除条件依赖该回写才成立，锁行状态机闭环。**C2-002 激活回升条款注记**：维持 P2 的前提「无生产周期续约调用方」在修复后弱化——上游若引入 watchdog 周期续约，本站点即承载互斥承重面，届时判级复核义务随 owner doc 登记。
- **子批②选主条件更新（SysDaoLeaderElector）**：changeLeader :194-217 与 tryRenewLease :119-127 改手工条件 UPDATE（EQL）按影响行数判定——changeLeader 谓词 `where clusterId=? AND leaderEpoch=旧值 AND expireAt=旧值`，影响行数=1 方可 onBecomeLeader(:214)/onElectionCompleted；tryRenewLease 谓词 `where leaderId=self AND leaderEpoch=current AND expireAt=旧值`。两处 `orm_disableVersionCheckError(true)`（:121/:204）撤除，版本检查不关闭——条件更新自带 CAS 语义后实体级版本检查为冗余第二防护面，保留将把「条件命中但版本漂移」混为失败。**豁免枚举核对表（失效面闭合）**：

| 站点 | 处置 | 理由 |
|----|----|----|
| tryBecomeLeader :168-186 | 豁免（零改） | saveEntityDirectly :180 以 clusterId 主键插入，并发双插主键冲突互斥——结构性 CAS 在位 |
| restartElection :219-238 | 豁免（零改） | 人为 bump epoch 触发全局重选的运维语义自洽；:232 disableVersionCheck 为其语义构成部分，非协调竞态 |
| refreshLeader :112-117 | 豁免（零改） | leader 自身未过期续期，版本检查未关闭（无 disable 调用）——安全面 |
| forceUnlock（SysDaoResourceLockManager :107-111） | 豁免（零改） | 管理端按主键强删的显式越权语义 |
| checkFollower 两入口 :144/:160 | 覆盖 | 经 changeLeader 收敛，无独立写点 |

- **子批③事件认领续租与完成写 fencing（NonBroadcastEventProcessor × SysDaoMessageService 协同）**：claim :127-140 保持 tryUpdateManyWithVersionCheck 认领 CAS。**续租挂点（R2 Blocker 修订——唯一可执行挂点 = invokeConsumer 有界等待循环本体）**：消费等待全程发生在 dispatchCallback→invokeDurableConsumers→invokeConsumer 的同步阻塞内（`process(NopSysEvent)` :142-149 线程在 dispatchCallback 返回前无任何执行机会——「dispatch 等待期间」在 NonBroadcastEventProcessor 内不存在独立时段，原文挂点不可执行），故续租切片与裁定⑩等待切片合一：等待循环每耗尽一个 leaseTimeout/3 切片即执行一次续租再继续等待；续租回调由 process 在 dispatch 期间以线程绑定上下文提供给等待循环（**线程绑定上下文由 process 入口注入、出口 finally 清除——轮询 executor 线程复用，残留将污染后续 dispatch[R2' Minor-5]**；broadcast 路径不注入——NopSysBroadcastEvent 无租约语义，纯超时预算）；续租动作 = 条件 UPDATE（谓词 `where eventStatus=CLAIMED AND leaseOwner=self AND version=认领时版本`，成功写 leaseExpireTime 推进 leaseTimeout；**续租 UPDATE 必须独立事务 session 执行——禁落入消费回调自身事务，否则消费事务回滚连带回滚续租致租约失守[R2' Minor-5]**）；**续租 0 行 = 已被接管** → 立即中止等待，后续完成写走 fencing 必然 0 行放弃（显式日志可见，终态归接管方）。完成写 = handleResult :177 与 handleProcessEventError :200 两站点 updateEntityDirectly 改条件写（谓词 `where version=认领时版本 AND leaseOwner=self`），0 行即放弃写并显式日志（**无静默吞**——放弃语义必须可见，与「已被接管」终态归接管方语义对齐）；两站点同族同修禁只修其一。
- **MA-01×I-05 协同矩阵（两种 0 行路径区分）**：见 §3.10。**备选拒绝**：过期删除改按 expireAt 时间比较（引入节点间时钟偏移进谓词——plan Risks 明文拒绝）；tryResetLease 回写用乐观实体 save（ORM 全字段 update 放大写面，手工 SQL 条件写为最小面）；子批②保留 disableVersionCheckError 叠加条件更新（双防护面语义冲突，见上）。

### 3.2 裁定②：mergedStore 写时复制发布（I-01）

- **形态**：genBizObjFiles 恢复路径 :320-331——不再 `publishedMerged.merge(moduleStore)` 原地结构修改，改为在 synchronized 写点内新建 InMemoryResourceStore、将 moduleStores 相关模块内容并入新实例后整体替换 `mergedStore`/`dynResourceStore` 两 volatile 引用（与 getResourceStore :404-411 的重建形态同构）；addToMergedStore :419-425 同法——新建含新资源的 store 后整体替换，已发布旧实例对外只读。写点全部在既有 synchronized 方法内（genBizObjFiles :298 / genViewFile :247 / genPageFile :283），读点无锁读 volatile——发布安全由 volatile 写保证，读侧永不观察到底层 TreeMap 的结构修改。
- **不触反向钉死论证**：本裁定不改任何方法 synchronized 修饰符、不改两字段 volatile——TestInMemoryCodeCacheContract :177-194 断言零触碰（merge-adjudication §一.3#6 相容裁定兑现）；MA-02 改写断言时以本裁定已落形态为基线（批内软序 I-01 先行）。
- **失效面枚举闭合**：写点 = genBizObjFiles merge :326 + addToMergedStore :421 两处（全类 grep 闭合，genModuleCoreFiles/genOrmModel 走 clearMergedStore :334-337 置空引用安全面）；同族入口 DynResourceStore.getChildren :30-33 为无锁只读透传，COW 后底层实例不再被写，零处置；clearMergedStore :334-337 与 getResourceStore :400-417 均为引用级操作，安全面登记。**内核零触碰声明**：ResourceTreeNode（nop-core）children 的懒初始化 TreeMap 为只读引用面，本批零触碰——COW 在模块侧落。

### 3.3 裁定③：cancel×subscribe compute 单步归一（I-02）

- **形态**：DurableSubscription.cancel :607-620 的「get :610 → remove(state) :612 → 判空摘键 :613-618」复合序列改 `durableSubscriptions.compute(topic, (k, list) -> { if (list == null) return null; list.remove(state); return list.isEmpty() ? null : list; })` 单步——与 subscribe :569 `computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())` 同键同 CHM bin 锁互斥，T2 的 computeIfAbsent+add 不可能交错进 T1 的 remove→判空之间（CHM compute 对同一 key 互斥为平台保证）。**getConsumers 条件移除（:617 `localService.getConsumers().remove(topic, new CopyOnWriteArrayList<>())`）必须位于 compute lambda 内摘键成功分支执行[R2' Minor-4]——置于 compute 返回之后将与 subscribe 的 computeIfAbsent+add 重开交错窗（T2 重订阅后 T1 误摘其轮询集合条目）**；锁序 = durableSubscriptions bin → consumers bin 单向（live 亲读无反向嵌套路径）。空 topic 摘除动机（:615-616 注释自证：空 topic 残留使轮询持续扫描且事件被 PROCESSED 丢弃）语义保持；delegate.cancel :609 保持先于 map 操作（取消顺序语义零变更）。
- **R17 族对齐矩阵（与 R1-08 4.1-MA-05 plan 2503 已落地形态逐项对齐，禁同族两形态分叉；事实源分行标注[R2' Major-1 修正]）**：

| 对齐点 | 已落地形态（live 亲读） | 本裁定兑现 |
|----|----|----|
| 复合操作互斥口径 | **事实源 = nop-message-debezium `DebeziumMessageSource`**（plan 2503 落地，commit `03e6203a46`）：subscribe/cancel/stop 复合操作统一 source monitor（:145 `synchronized(this)`，「reject-if-stopped + start + register」原子化） | cancel 摘键与 subscribe 注册同键互斥（CHM compute——机制不同物、互斥语义同层：复合序列单步化后同键交错不可能） |
| Subscription 身份集 | **事实源 = nop-message-core `LocalMessageService`**（WI23，plan 23 落地，非 2503）：cancel :75-82 按 Subscription 身份 `consumers.get(topic).remove(this)` | `list.remove(state)` 按 SubscriptionState 身份移除（既有语义保持） |
| 注册/注销配对 | **事实源 = DebeziumMessageSource**（plan 2503）：注册移至 start 成功之后（:140-145 注释自证——预修复形态 start 失败留下无法 cancel 的注册） | 摘键与轮询集合移除（getConsumers 条件移除）同一原子分支完成——无「map 已摘而轮询集合残留」中间态 |

- **非目标登记**：resolveSubscriberId :581-589 的 get-then-use 非原子读仅产生重复 subscriberId（无协调后果），登记不立项；broadcast 路径 dispatchBroadcastToSubscribers :388-409 只读 subscriptions 快照，零触碰。

### 3.4 裁定④：getResource 命中路径无锁化（MA-02，反向钉死同 diff）

- **形态（DCL，范本 getTenantCodeCache :151-168）**：DynResourceStore.getResource :20-28 去 synchronized——先无锁读 `store.getResource(path, true)`；miss 时 `synchronized(this)` 内二次确认（防双生成），仍 miss 才执行 prepare.accept :26 并复读返回。命中读（VFS 请求热路径）不再经过全局 monitor——懒生成触发时的全局限速器面消除，管理端编辑不再令平台级动态模型请求排队。锁序不变式：DynResourceStore monitor 为叶锁（prepare → InMemoryCodeCache synchronized 方法单向获取，无反向路径），this→CHM 单向锁序与 TestInMemoryCodeCacheDeadlock :77（testGetOrmModelAndRemoveModuleNoDeadlock）钉死形态一致，零回退。
- **钩子移出案否决（一次定夺禁第三形态）**：prepare 回调（DynCodeGen.prepareResource :485-534，含 DB 查询+XPL 生成）与用户 on-load/on-unload 钩子（addModule :131 / removeModule :140 持锁执行）**不移出临界区**。否决理由：①移出即废除「所有代码生成 synchronized 保护确保单线程生成」的类级契约（:49），共享 IEvalScope（:61）的槽位交叉写需按 I-06（P3 独立工单）逐生成入口局部化，修复面跨工单扩张；②生成互斥承担的是正确性（scope/模板变量共享）而非纯性能，弱化为 per-call 锁将引入新的竞态面，风险大于收益；③命中路径无锁化后全局串行仅剩 miss 生成段——限速器本体（高频读被低频生成阻塞）已消除。**残留面登记义务**：「锁内用户钩子（DB I/O + XPL + on-load/on-unload）仍在生成临界区内」显式登记 nop-dyn.md 生成期窗口声明（Phase 3 统一落账）。
- **反向钉死同 diff 改写口径（TestInMemoryCodeCacheContract :177-194）**：默认预设 = **结构断言**——新增 `DynResourceStore.getResource` 反射断言无 synchronized 修饰 + 保留既有三生成方法（genPageFile/genViewFile/getOrmModel）synchronized 断言与 mergedStore/dynResourceStore volatile 断言（:178-191）；吞吐>1 行为断言因 CI 时序敏感列为备选（R1 Minor-6e 口径），不作为默认。**禁纯删**——改写后断言集合的保护力必须覆盖「命中路径重新加锁」回归。I-01 先行不改断言（裁定②零触碰），MA-02 改写时核 I-01 已落形态。生成期窗口文档声明（「生成期间动态模型短暂不可用」）落 nop-dyn.md。

### 3.5 裁定⑤：租户缓存三转变点对称治理（MA-03）

- **①驱逐（removalListener）**：tenantCache（:77-78，LocalCache/Caffeine）注册 removalListener，容量驱逐时对被摘除实例执行 clear()（含逐模块 on-unload，与 destroy :104-109 手工清理语义对称）。**回调线程代价论证（勘误 9 条款）**：on-unload 执行面 = DynCodeGen.prepareUnloadModule 平台钩子（:447-449 空实现）+ 模块 on-unload.xpl 模板（存在才执行）——当前为轻量面，回调线程执行代价可接受；listener 内异常 catch 后记 log（Caffeine 驱逐回调抛异常不得毒化缓存内部状态）；若未来钩子变重，后台化候选登记 Non-Blocking Follow-up 不入本批。
- **②冷启动初始化**：**后台化否决，维持同步 DCL**——getTenantCodeCache :151-168 已由 synchronized(ref) DCL + initTenantCache :130-136 runInitializeTenantTask 单飞通道保证全模块 DB+生成恰一次执行（TestDynCodeGenTenantCacheInit :73-90 钉死）；请求线程等待首访租户初始化属预期语义（该租户模型不可用则请求本身无法服务），后台化引入「资源暂不可用」窗口需全调用方 fail-fast 改造为跨面扩张。预算治理的真正源头 = 容量驱逐制造的重初始化风暴，由「容器上限 ≥ 活跃租户数」owner doc 声明（nop-dyn.md，Phase 3 落账）+ ①驱逐治理闭合，不另设等待预算机制。
- **③destroy 孤儿窗（R2' Major-2 修正——原子 API 形态改裁）**：removeTenantCache :219-223 的 get→remove 两步与 getTenantCodeCache DCL :160-166 交错可产生孤儿实例（DCL 持 ref 在摘除后 set，实例 on-load 副作用已发生却无任何入口可达清理）。原拟 `tenantCache.asMap().computeIfPresent(…)` **不可达**：`ICache`（nop-commons）无 asMap/computeIfPresent（仅 computeIfAbsent :44/remove :101/remove(K,V) :109），按字面落地须改平台源码、违反 §四.11 零触碰边界——故改裁为**零平台面构造性闭合**：①removeTenantCache 改「`tenantCache.remove(tenantId)` 原子摘除（既有单键原子 API）→ 对摘除所得实例 clear()（含逐模块 on-unload）」——先摘除后清理配对；②getTenantCodeCache DCL 发布（put）**后复验** `get(tenantId)==ref`，不一致 = destroy 并发摘除 → 对新初始化实例执行 clear()+on-unload 自愈并按 miss 重取。两交错全闭合：destroy-remove 先于 put（T1 发布后复验捕获，自愈清理由 T1 承担）/ 后于 put（destroy 摘除所得即 T1 实例，清理由 destroy 承担）——孤儿实例在任何交错下都有唯一清理者。**残留复扫被拒**（只能检测不能阻止副作用）；**平台 ICache 增 computeIfPresent 候选被拒**（跨零触碰边界，收益仅免一次复验读，登记 Non-Blocking Follow-up 供平台演进）。destroy :98-113 循环体随 removeTenantCache 形态同步。
- **getUsedTenantIds 失真修正**：:198-200 返回 tenantCache.getAllKeys()——语义精确化为「缓存驻留租户集」（非活跃租户全集），①removalListener 保证驱逐即移除后返回值如实；javadoc 声明落 owner doc。clearForTenant :210-214 手工对称路径零改（与 removalListener 双路径幂等——clear 语义对称）。

### 3.6 裁定⑥：序列默认回退路径（MA-05）

- **per-seqName item**：findSeqItem :282-286 回退分支不再把共享的 SEQ_DEFAULT item 直接放入 defaultCache——改为按 seqName 复制默认配置新建独立 item（配置快照同源，锁对象独立），多序列名取号的互斥从单一 `synchronized(item)` :185/:211 分散到各自 item——一个冷门序列的 DB 慢（doSyncFromDb :246 session.lock 行锁等待）不再拖住全部默认序列取号。
- **DB 环节超时上限**：syncFromDb 链路（REQUIRES_NEW 事务 :309-315）加语句/锁等待超时上限，独立配置键承载（`nop.sys.seq.sync-db-timeout`，默认值实现期对齐平台语句超时惯例），超时按取号失败抛出（NopException 语义化错误码），禁静默重试空转。
- **defaultCache 同步失效（C1-007）**：clearCache :97-99 增 `defaultCache.invalidateAll()`、removeCache :101-103 增 `defaultCache.invalidate(cacheKey)`——管理端回拨 nextValue 后经主 cache 失效传播到回退项，最长 TTL 60s 兜底窗不再存在。generateLong :183-201 / generateString :204-228 两入口同 diff（共享 findSeqItem/syncFromDb，单点修复双入口生效）。**批量段号预取不扩张**（Non-Blocking Follow-up）；`TestSysSequenceGenerator` 既有钉零回退。

### 3.7 裁定⑦：懒初始化与快照发布卫生（MA-06）

- **ensure*Processor×2（SysDaoMessageService）**：字段 broadcastProcessor :131 / nonBroadcastProcessor :133 volatile 化；ensureNonBroadcastProcessor :305-325 与 ensureBroadcastProcessor :375-386 改 DCL（检查-volatile 读-synchronized(this) 内二次确认-赋值，范本 getTenantCodeCache :151-168）——timer 线程 × batch XLang 线程（claimNonBroadcastEvents :330-333 经 AOP 进入）双实例竞态与 JMM 可见性窗消除。测试照抄 TestDynCodeGenTenantCacheInit :73-90 双线程闩锁形态迁移（红⑦）。
- **DynOrmModelProvider**：entityModelLoader :31 volatile + getEntityModelLoader :41-45 DCL——一行面最小修复（循环依赖注释 :39-40 语义保持，BeanContainer.getBeanByType 解析结果缓存行为不变）。
- **SysCompactExtFieldHelper Snapshot**：双 volatile 字段 :24-25 合一为不可变 Snapshot record（positions+configs 双 map 构造后不可变），refreshCache :32-56 构建完成后单 volatile 写发布（:51-52 两行赋值非原子窗随之消解）——W-01（C1-004 watch）随快照对象化搭车消化（merge-adjudication §四 W-01 兑现）；读点改快照单读。既有 `TestSysCompactExtFieldHelper` 零回退。R23 族纪律：三站点同法不同站，同批交付禁并工单。

### 3.8 裁定⑧：moduleName 唯一索引（I-03，ORM plan-first 专节）

- **orm 模型变更面**：`nop-dyn/model/nop-dyn.orm.xml` NopDynModule 实体（:210-213）columns 段（止于 :251）后加 `<indexes>` 段——`<index name="UX_NOP_DYN_MODULE_NAME" unique="true"><column name="moduleName"/></index>`，写法沿 `nop-sys/model/nop-sys.orm.xml` :550-559 既有 `<indexes>` 先例（同一 xdef schema 承载 unique 属性，零 xdef 变更）。**再生成链路**：权威源模型 → `nop-dyn/nop-dyn-codegen/postcompile/gen-orm.xgen` → `./mvnw install -pl nop-dyn -am` 通道自动再生成 dao 侧 `_` 生成物——**生成物零手改**，生成后 `git status` 仅再生成差异为一致性核对判据。
- **存量库升级 DDL 落点**：唯一索引对既有库不自动生效（orm 模型索引为新建库 DDL 推导源）——存量升级 = 重复行预检（moduleName 分组 count>1 查询，命中须人工合并/清理后建索引）+ `CREATE UNIQUE INDEX` 手工运维 DDL，随实现以脚本形态交付并在 nop-dyn.md 注记升级义务；预检缺失即加索引将整体失败（plan Risks 条款兑现）。
- **两层并存语义**：应用层 findFirstByExample 检查（:152-155）**保留**为顺序路径友好失败（ERR_DYN_MODULE_NAME_EXISTS :155 既有错误码直接返回，零 DB 往返浪费）；唯一索引为跨节点并发权威防护。禁删检查层。
- **并发双建钉可构造性论证（防空洞钉；R2' Minor-3 复核成立+补注记）**：单 JVM + 真实 DB 通道下「两线程同moduleName窗口内双插」依赖调度时机，缺陷态稳定变红不可构造——故红⑧采**唯一索引违反路径驱动**形态：修复后直接并发双插（第二次 insert 违反唯一索引）断言输家失败赢家成功；缺陷态红证以**变异/回退论证**交付——摘除 orm.xml indexes 段 → 钉红（双建均成功产生重复行）→ 恢复 → 钉绿，恢复动作以 grep 断言确认 indexes 段真实存在（R1 Minor-5 变异探针纪律，防恢复半写入假绿）。**可构造性事实链（R2' 亲读核实）**：localDb = `jdbc:h2:mem:<uuid>` 每次测试运行全新内存库（变异回退逐运行生效，无「旧表已存在跳过建索引」陷阱——改文件库则该陷阱经 DataBaseSchemaInitializer :82 existsTable 判定显形）；DDL 物化链完整（orm `<indexes>` → DataBaseSchemaInitializer :83-89 → DdlSqlCreator.addIndex :241 → ddl.xlib :433 `create ${index.unique?'unique ':''}index`）；双线程各持独立 biz session/事务可构造；输家 flushSession 阻塞于唯一索引至赢家提交后收 duplicate key → catch 转译可达。**钉注记两条**：①输家阻塞窗 = 赢家整个事务（含 generateForModule+reloadModel :172-173 codegen）——测试等待预算给足并在钉内注明失败模式语义；②转译 catch 必须覆盖 saveEntity :169 与 flushSession :170 两个抛出点。
- **错误转译归一**：输家（后提交者）的 DB 重复键异常转译为 ERR_DYN_MODULE_NAME_EXISTS（与顺序路径 :155 归一——同一业务语义无论并发与否同一错误面），转译显式 catch（沿 addDefaultSequence :171-176 ERR_SQL_DUPLICATE_KEY 识别先例形态），**禁止裸 DB 异常暴露**与静默重试吞掉。

### 3.9 裁定⑨：删除失效 afterEntityChange（I-04）

- **形态（照抄先例）**：NopDynModuleBizModel 与 NopDynEntityMetaBizModel 各补 `@BizAction afterEntityChange` override（形态照抄 NopDynFunctionMetaBizModel :31-47：先 super 再按 action 分派）。函数级先例的分层论证（:45「函数级变更不涉及实体结构」）在模块/实体级对应重审结论：
  - **模块级（NopDynModuleBizModel）**：METHOD_DELETE → 调用 removeDynModule(:367-369，接线为内部路径——悬空入口禁第三形态) + reloadModel()（**全量 reload**）。作用域论证：模块删除涉及 codeCache.removeModule :135-148、VFS in-memory 层（reloadModel :397-399 updateInMemoryLayer）、bizObjectManager setDynamicBizModels :401 三面同步——按模块精确失效无法原子覆盖后两面（共享 codeCache 与 ModuleManager 全局态），全量 reloadModel 是既有唯一同步三面的通道。
  - **实体级（NopDynEntityMetaBizModel）**：METHOD_DELETE → generateBizModel(entityMeta, true, true)（refreshOrmModel=true——实体结构（列/关系）变化需 ORM 模型全量重载，与函数级 false 区分即先例 :45 论证的镜像应用）。
- **已删模型在途请求语义**：删除后至 reload 完成前，在途请求按旧模型完成（既有生成/加载通道语义，不引入强制中断）——窗口语义随生成期窗口声明一并落 nop-dyn.md。**三面不可达钉**：删除已发布模块后断言 codeCache 模块不可达 + VFS in-memory 层资源不可达 + bizObjectManager 动态 bizModel 不可达（红⑨，当前即红）。`TestNopDynFunctionMetaAfterChange` 既有钉零回退。「文档声明手动重发布」备选已被 batch exit criteria 否决（照抄先例为既定方向）。

### 3.10 裁定⑩：invokeConsumer 带超时等待（I-05）与 MA-01 协同矩阵

- **形态（唯一预设默认分支——既有平台 API 消费，零平台变更）**：invokeConsumer :543 `FutureHelper.syncGet(...)` 改分片等待 `FutureHelper.waitResult(stage.toCompletableFuture(), 切片)`（**R2 勘误修正：`waitResult(Future<T>, long timeout)` :262 既有公共 API 真实存在**——原文「无带超时等待公共 API」负结论作废，其为 :158 syncGet / :177 tryResolve 之外的第三等待 API，超时/中断/执行异常均 NopException.adapt 抛出；消费既有 API 仍零平台变更，禁向 FutureHelper 新增 API）。等待循环 = 「waitResult 切片 → 续租回调 → 判定继续/中止」三步（切片 = leaseTimeout/3，与裁定①子批③同一循环）；总预算 consume-wait-timeout 耗尽 → **返回 `new ConsumeLater(0)`**（:533 全挂起先例同通道）。**ConsumeLater 通道勘正（R2 Major）**：超时不走 retryDelayProvider/异常路径——ConsumeLater 返回值经 handleResult :165-172 既有分支处置（回 WAITING + scheduleTime=now+max(minProcessDelay,0) + incRetryTimes）；retryDelayProvider 是异常路径通道（handleProcessEventError :189 经 retryDelayProvider.apply），两通道语义不同禁混同（超时=可重试慢消费非异常；异常=retryDelayProvider 可判 FAILED）；InterruptedException 恢复中断位后按消费失败异常上抛（processNonBroadcastEvent :294-303 catch 记日志，下周期继续）。续租回调语义见裁定①子批③（同一循环）。一处修复覆盖两入口（invokeDurableConsumers :535 / dispatchBroadcastToSubscribers :400 均经 invokeConsumer）。
- **超时取值**：独立配置键 `nop.sys.event.consume-wait-timeout`（默认 = leaseTimeout 10000ms）——leaseTimeout 已承载 MA-01 子批③续租窗口语义，混用同一键会在调整租约时隐改等待预算；独立键使两语义解耦可调。**stall 看门狗否决**：无界等待本体消除后轮询管线不再可被单消费者永久挂死，看门狗属可用性优化面，登记 Non-Blocking Follow-up（禁第三形态条款）。
- **MA-01×I-05 协同矩阵（两种 0 行路径区分，红①③/红⑩双钉机械证明）**：

| 0 行路径 | 触发条件 | 语义 | 事件终态 |
|----|----|----|----|
| 超时放弃写（裁定⑩） | 消费等待超过 consume-wait-timeout，完成写**未发起** | 消费失败可重试（at-least-once） | 回 WAITING 经 ConsumeLater 通道（handleResult :165-172——minProcessDelay 兜底 + incRetryTimes） |
| 续租失守中止（裁定①子批③） | 等待循环内续租条件更新 0 行——租约已被第二节点接管，本节点立即中止等待 | 已被接管，本节点放弃 | 中止后完成写（含错误路径写）fencing 0 行放弃，终态归接管方 |
| fencing 拒绝写（裁定①子批③） | 完成写条件更新 0 行——租约已被第二节点接管 | 已被接管，本节点放弃 | 终态归接管方（其完成写胜出） |

时序交互：consume-wait-timeout（默认 10s）≥ leaseTimeout，等待循环按 leaseTimeout/3 切片推进且每切片后先续租再续等——正常慢消费由续租保活永不触达总预算；总预算耗尽仅在消费者真挂死时发生（超时→ConsumeLater），续租条件写 0 行（已被接管）则不等预算即时中止。fencing 谓词以 DB 行自身字段为界，不引入外部时钟依赖。

## 四、对齐纪律条款

1. **MA-01 三子批一处闭合 + 跨单元纪律引用不重开**：三子批同一提交序列交付（族主 C2-001 与四吸收成员同批）；条件更新/fencing 纪律沿 1.6 fencing 族与 4.2 选主纪律族（plan 2511）范式引用，站点细节（NopSysLock version 恒 0 / leaderEpoch / leaseOwner 语义）禁照抄先例实现；C2-002 激活回升条款注记随行（§3.1）。
2. **MA-01×I-05 同批协同**：两成员同批交付，协同矩阵（§3.10）禁把超时放弃与 fencing 拒绝混同；I-05 修复禁顺手触碰 cleanupExpiredEvents :253-281（MA-07 面，零触碰声明见 plan 勘误 4）。
3. **MA-02 反向钉死同 diff + I-01 相容边界**：断言改写与 MA-02 修复同一 diff（§一.3#6 成文）；I-01 COW 保留 synchronized+volatile 不触红（§3.2）；改写禁纯删，结构断言默认预设（§3.4）；TestInMemoryCodeCacheDeadlock 锁序钉零回退。
4. **MA-03 三子面单批不混同 + R23/MA-06 共享基座**：驱逐/冷启动/destroy 三子面分别验证；MA-03 与 MA-06 共享 DynCodeGen tenantCache DCL 测试基座但缺陷面不同不并工单。
5. **MA-05 两入口同批 + R03 方向引用**：generateLong/generateString 同 diff；R03 锁内 I/O 族纪律方向引用，per-item 锁本体保留，批量预取不扩张。
6. **MA-06 R23 族 + W-01 搭车**：三站点同批；测试形态照抄 TestDynCodeGenTenantCacheInit 迁移；Snapshot record 消化 W-01（cra-index :458 搭车注记兑现）。
7. **I-03 ORM plan-first 三件套 + 应用层检查保留**：授权三件套 = plan 头部申报 + 本文档 §3.8 专节 + 独立 draft review 记录；检查层禁删；importExcel :68-95 无去重为有意差异零触碰；I-08（无租户上下文旁路缓存）留 7.1 零触碰。
8. **I-04 照抄先例 + removeDynModule 接线禁第三形态**：作用域定夺（模块级全量 reload / 实体级 refreshOrmModel=true）一次定夺；「手动重发布」备选已否决。
9. **同变更集绑定（C10 随本体，E2）**：13 条缺保护 + 2 条假保护转正（C1-008/C6-101）与各自本体同 diff，禁先修本体后补测拆分；时序敏感改写 2 处（TestDaoLeaderElector sleep→状态轮询、TestSysCodeRuleGeneratorCache sleep→可控 clock）随批。
10. **硬序与软序**：无跨批硬依赖；批内软序 = I-01 先于 MA-02（同文件族断言基线清楚）+ MA-01 与 I-05 同批协同 + MA-06 基座先行于 MA-03 扩展面；同文件多成员（SysDaoMessageService 承载 I-02/MA-06/I-05、InMemoryCodeCache 承载 I-01/MA-02）diff 分组登记防混。
11. **禁触边界**：nop-api-core/nop-core/nop-commons 零触碰（`ResourceTreeNode`/`LocalCache`/`FutureHelper` 均只读引用）；nop-sys-api/nop-dyn-api 零触碰；`IResourceLockManager`/`ISequenceGenerator`/`IMessageService` 契约签名零变更；`nop-sys/model/nop-sys.orm.xml` 零触碰；`_` 生成物零手改（I-03 经 gen-orm.xgen 通道再生成）；跨单元同纪律站点（1.6/4.2/2.5 MA-01 族、4.1/2.5 I-02 族）零触碰；MA-04 零动作（R1-07 已落地，SSOT=[ledger-retention-policy.md](ledger-retention-policy.md) 裁定⑤）；如执行期需触 api 叶或额外 orm 模型，沿 plan 先例申报义务追加 plan-first，禁静默扩权。

## 五、裁定⑪：测试缝与零回退钉

### 5.1 红①–红⑩驱动器表（先红后绿，E2；红钉全部 plain-JVM 或既有测试树形态可跑）

| 红 | 成员 | 缺陷态红判据 → 修复态绿 | 形态 |
|----|------|------------------------|------|
| ① | MA-01 | 双竞争者同读过期行→A 接管插入新行→B 删除不得命中 A 行（holderId 断言）**当前即红**；双 follower 至多一个 onBecomeLeader（条件更新后）；连续两次续约均成功（回写钉）+ 慢消费第二认领者→完成写被 fencing 拒绝 0 行 | 构造性交错（锁管理器/elector/事件处理器三层各一钉，DB 内存假体沿 TestSysDaoResourceLockManager :76-181 既有形态） |
| ② | I-01 | T1 循环读 store 资源 × T2 genBizObjFiles merge/addToMergedStore——COW 后读侧永不撕裂；构造性交错或变异论证（删 COW 替换→并发遍历异常即红证） | 构造性交错红 |
| ③ | I-02 | T1 cancel 进入 remove 后闩锁暂停 × T2 subscribe 完成→T1 摘键→断言 topic 键存活且 state2 可收消息，**当前即红** | 构造性交错红（闩锁形态） |
| ④ | MA-02 | 命中路径无锁化 = 反向钉死改写断言本体（getResource 无 synchronized 结构断言）+ 与红钉同体；可选并发命中吞吐>1 备选形态 | 结构断言（同 diff 改写） |
| ⑤ | MA-03 | 容器上限 2 + 三租户→驱逐触发 removalListener 的 clear()+on-unload 断言（修复后绿）；destroy 孤儿实例钉（DCL×destroy 交错构造）——**当前即红** | 当前即红 |
| ⑥ | MA-05 | 并发同序列名取号无重复值断言（现有 testSequence 仅单线程）+ defaultCache 失效钉（removeCache 后回退项重新装载） | 竞态功能钉 |
| ⑦ | MA-06 | 双线程闩锁迁移到 ensure*Processor（照抄 TestDynCodeGenTenantCacheInit :73-90），**当前即红**；Snapshot 面断言双 map 同代际 | 当前即红 |
| ⑧ | I-03 | 并发双 generateByAI 断言仅一成功、输家 ERR_DYN_MODULE_NAME_EXISTS——唯一索引违反路径驱动（修复后才能红），缺陷态红证绑定变异/回退论证（摘 indexes→红→恢复→绿→grep 断言 indexes 存在） | 假保护转正 |
| ⑨ | I-04 | 删除已发布模块→codeCache/VFS in-memory 层/bizObjectManager 三面均不可达，**当前即红**；实体级删除钉同型 | 当前即红 |
| ⑩ | I-05 | stub 消费者返回未完成 future + 有限 timeout→断言轮询任务按超时路径返回 ConsumeLater（handleResult 后事件回 WAITING）而非永久阻塞；**挂死类失败模式=超时**，给足有限 timeout 并在钉内注释失败模式语义（2522 红④先例） | 挂死超时钉（确定性悬挂非竞态） |

### 5.2 零回退钉清单与 C10 映射

- **基线零回退**：163 tests（勘误 5 r7 等价分解通道口径：nop-sys 100 + nop-dyn 63），Phase 2/3 全绿零回退；点名既有钉族：TestDynCodeGenTenantCacheInit / TestInMemoryCodeCacheDeadlock / TestInMemoryCodeCacheContract（:177-194 仅 MA-02 同 diff 改写）/ TestSysEventCleanup / TestNopDynFunctionMetaAfterChange / TestSysLedgerRetention_MA_04 / TestSysCompactExtFieldHelper / TestSysDaoMessageService 18 用例族 / TestSysDaoResourceLockManager 6 场景族 / TestDaoLeaderElector / TestSysSequenceGenerator / TestSysCodeRuleGeneratorCache。
- **时序敏感搭车 2 项**（C10 建议，组 B/A 落地）：TestDaoLeaderElector :63/:72 ThreadHelper.sleep(1000) → 状态轮询断言（MA-01 子批②同 diff）；TestSysCodeRuleGeneratorCache :118 Thread.sleep(**2000**[R2' Minor-6 勘误——plan 载 2002 系笔误]) → 可控 clock 注入（批级搭车——与 MA-05 非同文件，同模块时序敏感治理）。
- **C10 映射同 diff**（SSOT=inventory-p2-c10 :626/:809，勘误 4 口径）：I-01→C1-001；I-02→C1-002；I-03→C1-008（假保护转正）；I-04→C6-101（假保护镜像）；I-05→C8-001；MA-01→C2-001/C2-102/C2-002/C2-004；MA-02→C2-003（反向钉死同 diff）；MA-03→C6-002；MA-05→C2-005；MA-06→C1-003；MA-04 面 2 条已随 R1-07 零承载；MA-07 面 C8-004 不在本批。载体命名沿 `Test*_<工单>` 后缀惯例（如 `TestSysDaoFencing_MA01`）。
- **端到端/接线/无静默跳过义务**（rule 22/23/24）：fencing 链 = 过期接管全链钉（tryLockWithLease 入口→releaseLock 出口）；事件链 = 慢消费接管全链钉（claim→handleResult）；订阅链 = cancel×subscribe 交错全链钉（subscribe 入口→invokeDurableConsumers 送达出口）；模型链 = 删除失效全链钉（BizModel 删除入口→三面出口）。MA-01 0 行放弃写显式可见；I-05 超时显式消费失败语义；I-03 唯一索引违反显式转译。

## 六、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|---------|
| ① 过期删除改按 expireAt 时间比较谓词 | 引入节点间时钟偏移进协调谓词（plan Risks 明文）；holderId 条件与同文件已修两点形态对齐，零新增语义 |
| ① tryResetLease 回写用实体 save（ORM update） | 全字段 update 放大写面且无条件语义；手工条件 SQL + setVersion 回写为最小闭环 |
| ② 对 InMemoryResourceStore/ResourceTreeNode 加同步 | 内核零触碰边界；热路径加锁反向劣化；COW 整体替换为模块侧最小面 |
| ② addToMergedStore 改全量重建 mergedStore | 单资源生成路径全量重建代价高；按模块重建后合并的写时复制已满足只读发布（写点持锁，重建频率=生成频率低频） |
| ③ cancel 全过程持服务级 monitor（粗粒度锁） | 轮询线程 dispatch 与 subscribe/cancel 同锁将把取消路径串到消费路径上；CHM compute 同键互斥已闭合交错窗且粒度最小 |
| ④ 钩子移出临界区/读写分离深度改造 | 正确性互斥（共享 scope 槽位）而非纯性能；跨入 I-06 工单面；命中无锁化已消除限速器本体（§3.4 否决理由） |
| ④ 反向钉死改写选吞吐>1 行为断言为默认 | CI 时序敏感（R1 Minor-6e）；结构断言确定性强且与「禁纯删」保护力等价 |
| ⑤ 冷启动后台化 | 「资源暂不可用」窗口需全调用方 fail-fast 改造（跨面扩张）；同步 DCL 单飞已钉死正确；风暴源头由容器上限声明+驱逐治理闭合 |
| ⑤ destroy 残留复扫（第二遍扫描） | 只能检测不能阻止孤儿实例 on-load 副作用；「remove 原子摘除→摘除实例清理 + DCL 发布后复验自愈」才是零平台面构造性闭合（R2' Major-2 改裁——ICache 无 computeIfPresent，平台增 API 被拒） |
| ⑤ removalListener 内同步等待 on-unload 完成（有界预算阻塞驱逐线程） | Caffeine 驱逐回调阻塞毒化缓存内部；当前钩子轻量（prepareUnloadModule :447-449 空实现+可选 xpl），变重时后台化为 Follow-up |
| ⑥ MA-05 批量段号预取队列 | audit P3 候选不主张；item 拆分+超时上限后锁放大面已收敛（Non-Blocking Follow-up） |
| ⑥ 超时上限落 orm 模型/DDL 层 | 超时为运行时行为非模型结构；配置键承载零模型变更（PA 判定表第 6 行 orm 否列兑现） |
| ⑦ ensure*Processor 用 synchronized 方法整体包裹 | processNonBroadcastEvent :294-303 每轮调用，方法级锁将周期串行化；DCL 检查-volatile 读为热路径零锁形态 |
| ⑧ 分布式锁/单飞替代唯一索引（§五#6 选型复核） | 应用层单飞无法闭合跨节点竞态（check-then-act 同 TOCTOU）；唯一索引为数据库侧权威防护（family-clustering §三.5#6 已裁） |
| ⑨ 按模块精确失效替代全量 reload | bizObjectManager/VFS in-memory 层为全局态，精确失效无法原子覆盖三面；全量 reloadModel 为既有唯一同步通道（§3.9） |
| ⑩ 向 FutureHelper 新增带超时等待公共 API | 触发 nop-api-core plan-first 追加申报（PA 表第 10 行条件申报）；模块内 toCompletableFuture().get 为零平台面等价形态；若实施期确需平台 API，须四件证据链申报禁静默扩权 |
| ⑩ I-05 stall 看门狗 | 无界等待本体消除后停摆不可再现；看门狗为可用性优化（Non-Blocking Follow-up，禁第三形态） |

## 七、失效面核对总表与 PA 零触碰声明

- **失效面核对（grep 同族兄弟方法逐一登记）**：SysDaoResourceLockManager 全部写点 = tryLockWithLease 删除 :148-151（修）/tryResetLease :235-242（修）/releaseLock :265-269（已修零改）/saveNew :204-224（插入语义零改）/forceUnlock :107-111（豁免）；SysDaoLeaderElector 全部写点 = changeLeader :194-217（修）/tryRenewLease :119-127（修）/refreshLeader :112-117（豁免）/tryBecomeLeader :168-186（豁免）/restartElection :219-238（豁免）；NonBroadcastEventProcessor 全部写点 = claim :127-140（认领 CAS 零改+续租新增）/handleResult :177（修）/handleProcessEventError :200（修）；SysDaoMessageService durableSubscriptions 全部读写点 = subscribe :569 / cancel :610-618（修）/getBroadcastSubscriptions :497-503 / invokeDurableConsumers :510（只读安全面零改）；InMemoryCodeCache mergedStore 全部写点 = getResourceStore :408-409（引用级安全）/genBizObjFiles :326-330（修）/genViewFile :259 / genPageFile :295（经 addToMergedStore 修）/clearMergedStore :334-337（置空安全）；DynCodeGen tenantCache 全部转变点 = get :152/DCL :160-167（零改）/removeTenantCache :219-223（修）/destroy :105-110（随修）/clearForTenant :210-214（零改幂等）/removalListener（新增）；SysSequenceGenerator cache 写点 = findSeqItem :295/:285/:290（回退分支修）/clearCache :97-99 / removeCache :101-103（修）；懒初始化写点 = ensure*×2 :306-307/:376-377（修）/DynOrmModelProvider :42-43（修）/SysCompactExtFieldHelper :51-52（修）。
- **PA 零触碰声明**：头部 9/10 成员零 orm.xml、零 `_` 生成物、零 nop-sys-api/nop-dyn-api、零 nop-api-core/nop-kernel 平台设施触碰；唯一 plan-first 触点 = I-03 `nop-dyn/model/nop-dyn.orm.xml`（授权三件套见 plan 头部；`nop-sys/model/nop-sys.orm.xml` 零触碰——MA-01/MA-05 全部在 Java 层条件写与锁粒度）；载体九文件分属 nop-sys-dao（5）/nop-sys-service（1）/nop-dyn-service（3）三 impl 叶；E3 双通道命令 `./mvnw test -pl nop-sys -am` 与 `./mvnw test -pl nop-dyn -am` 直达，I-03 再生成追加 install 通道验证（plan 勘误 3）。

## 八、勘误（live 漂移登记，live @ `69e95df564`）

1. **测试计数漂移（plan 成员表/钉族点名 vs live）**：TestSysDaoResourceLockManager live 为 **6** 测试方法（:76/:88/:105/:129/:155/:181——plan 基线节记「4 场景族」）；TestSysDaoMessageService live 为 **18** 用例（plan 基线节记「12 用例族」）——两处为批前 landed 钉计数滞后，零回退钉口径以 live 计数为准。
2. **行号微漂移（缺陷机制零漂移）**：DurableSubscription.cancel 的 getConsumers 条件移除 live :617（plan 载 :619）；restartElection live :219-238（plan 载 :220-238）；DynCodeGen.destroy live :97-113（plan 载 :98-113）；generateByAI 方法体 live :138-174（plan 载 :139-176）；genPageFile addToMergedStore 调用 live :295、方法体 :283-296（plan 载 :295 一致）；clearMergedStore live :334-337、getResourceStore live :400-417（plan 载 :334-336/:400-416）；其余成员锚点（MA-01 五站点、volatile :78-79、tenantCache :77-78、DCL :160、ensure* :305-325/:375-386、syncGet :543、序列 :56-59/:97-103/:183-228/:246/:282-286、双 volatile :24-25/:51-52、afterEntityChange 先例 :31-47、orm 实体 :210-219）零漂移。
3. **负结论登记（R2 勘误二次修正）**：`FutureHelper.waitResult(Future<T>, long timeout)` :262 **既有公共 API 真实存在**（超时/中断/执行异常均 NopException.adapt 抛出）——原「live 仅 syncGet :158 / tryResolve :177、无带超时等待公共 API」负结论**作废**（漏读 :262）；裁定⑩改为消费该既有 API 的分片等待形态，平台 API 零新增结论不变；audit 原建议的 `tryGet(future, timeout)` 命名形态仍不存在，事实维持。`FutureHelper` 归属 nop-kernel/nop-api-core 维持。
4. **续租挂点勘误（R2 Blocker，2026-11-02 修订落稿）**：原裁定①子批③「dispatch 等待期间以 leaseTimeout/3 分数间隔续租」的挂点不可执行——消费等待全程阻塞在 `process(NopSysEvent)` :142-149 → dispatchCallback → invokeConsumer 调用链内，NonBroadcastEventProcessor 在 dispatch 返回前无执行机会；修订后唯一挂点 = invokeConsumer 有界等待循环本体（与裁定⑩等待切片合一），续租回调由 process 经线程绑定上下文注入（§3.1 子批③修订文本为准）。
