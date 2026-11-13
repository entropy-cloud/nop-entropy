# nop-persistence ORM 切片束 b（R4-19 G45 后半，15 成员）

> Status: draft
> Last Reviewed: 2026-11-10
> Source: plan 2553（`ai-dev/plans/2553-cra-phase8.59-r4-19-orm-slice-b.md`）Phase 1；批次定义 = batch-proposal.md §二 :134 R4-19 行；成员发现 = `ai-dev/audits/2026-10/2026-10-18-1521-deep-audit-nop-persistence-rw-concurrency/`
> PA：**无**（15 项全 Java 层守卫/契约/上限，零 orm.xml 触碰）
> 特殊约束：**C2-016 = C10-017 现状容忍钉**——修复前必须先改 `TestTransactionTemplate:326 testMultipleCommit`（无断言体、钉住双提交不抛现状）；C5-001/005（+C2-009 钉选配）受 **N4 真 H2 义务**（禁全 mock；挂靠 `AbstractJdbcTestCase`/`JdbcTestCase` 既有基建）；C2-009 补钉义务；C2-018=CL-200 与 C1-016 同批设计（volatile 已由 2552 落）。
> 成员取证（fresh session @ 2026-11-10）：原样 12 + 部分覆盖 3（C2-007/2517②+2552B、C2-016/2552M、C4-002/2517⑤）+ 已修 0。

## 一、设计结论（十三条裁定）

1. **裁定 A（模型代残余：延迟关闭）**：C2-007 残余。`DefaultOrmModelProvider.clearCache` 换代时旧 `LoadedOrmModel` 调 **`close()`**（provider 本身不持引用——引用只归 session；`decRef()` 会把 refCount 推负且永不触发销毁[R1-Major-1]；`close()`=needClose 标记 + refCount<=0 即刻销毁/否则末 session decRef 延迟销毁——恰为 2552 裁定 B 生命周期）；`setOrmInterceptor` 登记「仅模型构建期调用」契约注记（请求路径热写面由 volatile COW 可见性兜底，全量治理 watch-only）。**TenantAwareOrmModelProvider 的 tenantCaches destroyOnRemove TOCTOU 面=R1-Minor-11 残余**（与 setOrmInterceptor 一并 watch-only 登记）。
2. **裁定 B（批队列 swap-then-capture）**：C2-009。`BatchActionQueueImpl`/`OrmBatchLoadQueueImpl` 的 capture-then-null 形态改 **swap-then-capture**（先置新空容器再捕获旧引用——重入 flush 追加进新容器，不落被丢弃旧容器；**三处捕获槽全换：collectionActions + delayTasks + actionMap**[R1-Major-2：actionMap.clear 的 finally 擦除是同一静默丢失类，ContextTaskQueue.syncGet 排空同线程任务时可重入 enqueue]）；`flush()` 重入（`flushing==true`）改「完成后重放」语义：记录待 flush 标记，由当前 flush 完成尾**循环重放直至标记不再置位**（重放本身可再入[R1-Minor-9]）。**补钉义务**：`TestBatchActionQueueSwapCapture`（单线程可驱动；真 H2 E2E 钉为 watch-only 增强）。
3. **裁定 C（事务监听器注册原子性）**：C2-014 + C9-001 同点位合并。`AbstractTransaction.addListener` 惰性 init 收进 `synchronized(this)`（与 :112-126 addSubTransaction 同监视器）+ **listeners 加 volatile**（R1-Minor-8：invokeListener/getListeners 锁外读首注册 stale-null 面）；`EntityPersisterImpl.evictGlobalCache` 改**事务级单 listener**（经 `env.txn().getRegisteredTransaction` 访问事务；首注册者挂聚合回调 + 并发 `pendingEvictKeys` Set，后续 evict 仅追加；afterCompletion 自注销）——O(n²) 面消除。错失窗口（commit 线程消费 Set 后追加的 key）不劣于现行 late-listener-lost 窗口，且由 2517-⑥ genFence 缓解[R1-Minor-8 注记]。载体：`TestTransactionListenerAtomic`（并发注册不丢 + 单 listener 断言）。
4. **裁定 D（事务状态机 CAS）**：C2-016。**先改 `TestTransactionTemplate:326 testMultipleCommit`**：改写为行为断言（首次 commit 真提交、二次 commit 以 `ERR_TXN_ALREADY_COMMITTED` 语义 fail-fast 或显式 no-op-with-WARN——取 no-op+WARN 档保持兼容）；实现用 `AtomicReference<TxnState>`（OPEN/COMMITTED/ROLLED_BACK/CLOSED）CAS 门控 open/commit/rollback/close——open 双开 fail-fast、listener 回调保持锁外。**Fix+Decision 显式裁定（R1-Major-7）**：①`markRollbackOnly` 后 commit **改变现行抛 ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT 行为为自动走 rollback 语义 + WARN**（用户可见语义变更，载体断言显式覆盖）；②CAS 失败转移：doCommit/listener 异常时终态置 ROLLED_BACK（尽力）+ 异常透传；③`isTransactionOpened()` 映射保持 OPEN/COMMITTING 为 true（TransactionTemplateImpl 门控与 commitSubTransactions :388 行为保持）。生产调用面已核验：仅 TransactionTemplateImpl:402-404（newlyOpen 门控）+ AbstractTransaction:398（子事务），testMultipleCommit 改写零外溢。载体：`TestTransactionStateCas`。
5. **裁定 E（锁内加载外移）**：C2-018。`LazyLoadOrmModel.loadEntityModel` 的 DB+codegen 加载移出 `synchronized(entityModelMap)` 块——per-key `ConcurrentHashMap<String, CompletableFuture<IEntityModel>>` 去重（并发同 key 共享单次加载）；装载完成后重新进入锁域做 put + 短名预检（复用裁定 K/2552 的预检逻辑——putEntityModel 自身重获锁，拓扑协议不破坏）；topo 失效（topoEntryInited=false）保留全局锁。**重入守卫（R1-Major-3）**：ThreadLocal 在飞 key 集——`IDynamicEntityModelProvider` 加载体内同步回环 `getEntityModel` 同 key（或跨 key 环 A→B→A）时 fail-fast（否则 CF.join 自死锁无监视器可救）；契约注记同时登记（provider 不得回环）。载体：`TestLazyLoadConcurrentLoad`（并发同 key 单次加载断言）。
6. **裁定 F（runInSessionAsync 契约对齐）**：C3-002。`OrmTemplateImpl:253` 既有会话分支补 `try/catch(Throwable)→FutureHelper.reject`（与新会话分支异常契约一致）。
7. **裁定 G（delayTasks 字段清空）**：C3-003。`BatchActionQueueImpl:236` `delayTasks = null` → `this.delayTasks = null`（局部遮蔽一行修正——跨 flush 重放面消除）。载体：TestFlushFailure 扩钉。
8. **裁定 H（两阶段拓扑契约 fail-fast）**：C3-004。`BatchActionQueueImpl` 阶段依赖 future 若同步完成假说被异步驱动证伪（未完成即消费），显式 `checkDone` fail-fast（`ERR_XXX_FUTURE_NOT_DONE` 型）+ javadoc 契约声明（阶段1 collect → waitAll → 阶段2）。载体：结构钉 + 注入未完成 future 的行为钉。
9. **裁定 I（伪异步载体契约钉定）**：C4-001。`JdbcEntityPersistDriver`/`JdbcCollectionPersistDriver`/`AbstractTransaction` 的 Async API 全部补 javadoc 载体契约（「同步执行于调用线程；返回 future 仅作完成信号；调用方不得假设载体切换」）+ `FutureHelper` 引用相等依赖点补注。纯声明面（C4-002/003 宿主承载），**声明面钉通道**：随批 diff + closure 抽验。
10. **裁定 J（flush 载体隔离装配点）**：C4-002 残余。`OrmTemplateImpl` 收尾回队列面已由 2517⑤ 落；本批补 **flush executor 可装配点**（`setFlushExecutor(Executor)`，缺省 null=调用线程现行语义）+ javadoc 声明。**作用域显式限定（R1-Major-4）**：seam 仅承载 `runInNewSessionAsync` finalize 路径（:286-296 dispatchSessionFinalize→session.flush）——用户直调 `session.flush()`（OrmSessionImpl:1098）不在 seam 内（session/factory 无 OrmTemplate 依赖，全量接管需 IPersistEnv 贯穿=超范围，watch-only 登记）。载体断言只对 finalize 路径。
11. **裁定 K（连接获取点语义声明）**：C4-003。`JdbcTransaction.getConnection` 懒取点 javadoc 声明线程语义（可落完成/context 排空线程；池等待放大停顿的风险面）+ 获取耗时 WARN（>1s 结构化日志）。纯声明+观测面。
12. **裁定 L（findAll 全量物化防线）**：C5-001 + C5-005 合并（⑦⑧⑨⑥ 汇聚点）。`ISqlExecutor.findAll` 汇聚点加可配置行数上限（键 `nop.dao.find-all-max-rows`[R1-Major-5 命名勘正：守卫在 nop-dao，DaoErrors 约定]，缺省 0=不设上界档保持现行语义；>0 超限抛 `ERR_DAO_FIND_ALL_ROW_LIMIT_EXCEEDED`[DaoErrors 新码——nop-dao 不能依赖 nop-orm 的 OrmErrors]）。**抽取器级执行（R1-Minor-6）**：上限以计数装饰 RowMapperAllExtractor 在 IDataSet 迭代期抛出（post-hoc list.size() 检查不约束已物化内存）；通用 `executeQuery(sql,range,callback)` 路径不在守卫内（显式声明）。`findAllByQuery` 尊重 `query.getLimit()`（limit>0 时转 findPage 语义不再静默丢弃——DaoQueryHelper 零 getLimit 读已核验）。**真 H2 钉**（挂靠 `AbstractJdbcTestCase`）。
13. **裁定 M（缓存单条目上限）**：C5-002。`saveCacheData` 加单条目上限（键 `nop.dao.query-cache-max-rows`[R1-Minor-10 命名勘正：nop-dao 约定]，缺省 0=不设上界档；>0 超限放弃缓存 + WARN `nop.dao.query-cache-entry-too-large`）。**瞬态分配不设界注记**：IDataSet 无 getReadCount，行数仅在 toCacheData 物化后可得——守卫约束持久缓存内存，瞬态物化分配仍无界（声明）。载体：注入 cacheProvider + 上限配置的行为钉。
14. **裁定 N（C10 前置）**：C10-014（真 H2 义务）由裁定 L 载体承载；C10-017 由裁定 D 先改钉承载；C10-005 残面（C1-027 已落 2552）复绿确认。

## 二、回归面清单

| 改动 | 回归 |
|---|---|
| 全部 | `./mvnw -o test -pl nop-persistence -am`（归因形态沿 2552：四模块逐模块绿承载，上游 flake 族群 solo 绿归因） |
| C2-016 | testMultipleCommit 改写后新钉 + 既有 TestTransactionTemplate 其余用例零回退 |
| C1-016 零弱化钉 | `TestLazyLoadOrmModel` 复绿（裁定 E 触 LazyLoadOrmModel） |
| 2517/2552 既有钉 | TestCra2517* 全族 + TestDaoSliceA 复绿 |

**E1**：`./mvnw -o clean install -DskipTests -T 1C -pl '!nop-runner/nop-cli-core,!nop-runner/nop-cli'` BUILD SUCCESS。
**E3**：`./mvnw -o test -pl nop-persistence -am`（归因形态：四模块逐模块绿 + 上游 flake 族群 solo 绿归因）。
