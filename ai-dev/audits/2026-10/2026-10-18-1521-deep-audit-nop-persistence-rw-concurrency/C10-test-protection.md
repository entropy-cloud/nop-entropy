# C10 测试保护力审计——nop-persistence 读写并发专项（最后批次）

- **日期**: 2026-10-18
- **仓库/分支**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（`concurrency-audit-remediation`，HEAD abccc80dde）
- **维度**: C10 测试保护力（对照 C1~C6、C9 七维度全部保留发现，评估 `src/test` 回归保护；缺保护处登记 P2）
- **审计对象**: nop-orm / nop-orm-eql / nop-orm-model / nop-dao / nop-orm-data 五模块 src/test
- **纪律声明**: 本批次只读。未修改任何测试代码、产品代码与七维度审计文件；本文件为唯一新增产出。所有「有钉/部分」判定均实际打开测试文件与对应产品代码核对；「有钉」判定按任务书完成 3 条反向验证（第 6 节）。

---

## 1. 输入口径

七维度文件 `## 最终保留项` 节（含维度复核结论节）合计 **74 条保留发现（P1×17）**：

| 维度 | 保留条数 | P1 | P2 | P3 | 文件 |
|---|---|---|---|---|---|
| C1 共享可变状态 | 30 | 5 | 16 | 9 | C1-shared-mutable-state.md:1083-1118 |
| C2 锁与同步 | **21** | 8 | 9 | 4 | C2-lock-sync.md:849-875 |
| C3 异步链 | 6 | 3 | 3 | 0 | C3-async-chains.md:236-247 |
| C4 线程池 | 3 | 0 | 3 | 0 | C4-thread-pools.md:130-138 |
| C5 内存边界 | 5 | 0 | 3 | 2 | C5-memory-bounds.md:186-195 |
| C6 资源生命周期 | 3 | 1 | 0 | 2 | C6-resource-lifecycle.md:172-180 |
| C9 热路径 | 6 | 0 | 1 | 5 | C9-hot-path.md:233-244 |
| **合计** | **74** | **17** | **35** | **22** | — |

- **口径勘误（对任务书摘要）**: 任务书写「C2 保留 20」，C2 文件终局表实为 **21 行**（C2-001~021，含 round2 新增 C2-021；P1×8 含 C2-014 降 P2 后）。总数 74 与 P1×17 口径不受影响。
- **P1 终局清单（17 条）**: C1-001/003/015/023/026，C2-001/002/003/004/005/013/015/017，C3-001/005/006，C6-001（其中 C1-001+C1-023 合并修复、C3-006+C6-001 合并工单，remediation 计数去重后为 15 个修复项）。
- **测试保护力对照基准**: 保留发现以「缺陷本体（竞态/泄漏/无界/契约缺失面）」为判定对象；同代码区域的单线程行为钉不计为对缺陷本体的保护（计入「部分」时逐条注明钉的是哪个邻接面）。

---

## 2. 测试资产盘点

### 2.1 总账

| 模块 | 测试类 | @Test | 被测面分布 |
|---|---|---|---|
| nop-orm | 86 | 402 | session/缓存/flush/impl/loader ~14 类；shard×8；enc×4；EQL 运行时集成×11；factory×3；SQL 生成×4；sql_lib×7；initialize×2；entity support ~10 |
| nop-orm-eql | 96 | 823 | parse ~35 类；compile（方言/门禁/快照）~59 类；meta/sql ~2 类——全部为语言/方言/编译期正确性 |
| nop-orm-model | 8 | 56 | 模型构建/初始化校验×7；lazy×1（**全单元唯一真并发测试所在**） |
| nop-dao | 53 | 289 | dialect ~29；jdbc 批/模板/数据源 ~15；txn×7；seq/shard/exceptions×2 |
| nop-orm-data | 2 | 8 | DaoEntityBlockingSource 行为×2 |
| **合计** | **245** | **1578** | （不含 `_gen`/测试实体 POJO；基座 JdbcTestCase/AbstractOrmTestCase 等 6 类未计入） |

### 2.2 并发相关测试资产全清单（5 模块内全部命中）

| 测试类#方法 | 性质 | 对应缺陷区域 |
|---|---|---|
| TestLazyLoadOrmModel#testConcurrentAddAndTopoRebuildKeepsAllEntities | **真并发**（50 轮×8 写线程+1 读者线程，CountDownLatch 起跑） | C1-016 机制面（topo 重建 vs put 竞态）——修复已随 99abdf8f28 落地，此为回归钉 |
| TestDaoEntityBlockingSource#testDrainToThrowsOnInterrupt | 真 Thread（单写者+中断） | drainTo 中断传播 |
| TestDaoEntityBlockingSource（其余 5 法）| 单线程语义 | drainTo 攒批/计数/中断外语义 |
| TestDaoEntityBlockingSourceClaim×2 | 单线程 mock | C2-005 邻接面（非 NULL 旧值冲突跳过） |
| TestAbstractTransactionListeners#testConcurrentAddWhileInvoking | 名为并发、实为**单线程重入**（回调内 addListener） | C2-014 迭代面（COW 快照） |
| TestAbstractTransactionListeners#testListenerTypeIsCopyOnWrite | 结构断言 | C2-014 容器选型 |
| TestTransactionTemplateAsync×4 | 单线程（FutureHelper.syncGet 同步收敛） | C2-015 邻接面（子事务组回滚隔离、回滚失败 suppressed） |
| TestAbstractTransactionAsync×2 | 单线程 | commitAsync/rollbackAsync 错误码包装 |
| TestAbstractTransactionCommitNotification×2 | 单线程 | commit 失败终态通知（DAO-02 回归钉） |
| TestJdbcTransactionAutoCommitRestore×2 | 单线程（真 H2） | C1-026/C2-017 邻接面（连接保留时 autoCommit 恢复） |
| TestDefaultTransactionManager×8 | 单线程（真 H2） | C2-013 邻接面（注册生命周期、提交/回滚持久化、metrics） |
| TestTransactionTemplate×12 | 单线程（真 H2） | 传播语义、活跃连接计数 |

**结构性事实**: 245 个测试类中，真并发测试 **1 个**（topo 重建）；使用真实多线程的测试 **2 个**（上述 1 个 + drainTo 中断）；其余全部单线程。并发缺陷面 74 条发现中仅 1 条机制面（C1-016）有真并发回归钉。

### 2.3 测试空洞登记（按被测面）

| # | 空洞 | 证据 |
|---|---|---|
| G1 | **异步入口零覆盖**：`runInSessionAsync/runInNewSessionAsync/commitAsync`、批量装载异步链无任何直接测试 | grep `runInNewSessionAsync|runInSessionAsync` 于 5 模块 src/test = 0 命中 |
| G2 | **引擎级单例零覆盖**：OrmSessionRegistry、TransactionRegistry、DefaultOrmModelProvider/TenantAwareOrmModelProvider、OrmTimestampHelper、DaoProvider.registerInstance、SqlExprMetaCache、DbEstimatedClock、查询计划缓存失效 | 上述类名在 src/test 全部 0 命中 |
| G3 | **模型热刷新族零覆盖**：reloadModel / clearCache 路径（C1-003/C1-004/C2-007/C1-008(c)/C2-008/C6-003 全部落空） | `reloadModel` 0 命中；`clearCache` 仅 shard 测试无关命中 |
| G4 | **全局缓存失效面零覆盖**：evictGlobalCache、per-entity 事务监听器注册（C2-010/C9-001/C2-011/C2-004 竞态面） | `evictGlobalCache|addTransactionListener` 0 命中 |
| G5 | **取消/超时语义零覆盖**：cancelToken 注册/反注册（C6-002）、orTimeout 面 | `cancelToken` 0 命中；TestJdbcTemplate#testExecuteStatement 仅 happy-path |
| G6 | **内存上界面零覆盖**：findAll/findAllByQuery 行数上限、cacheRef 单条目体积（C5-001/002/005）；唯一 limit 生效钉在 findPageAndReturnCursor 分页路径（TestEntityDaoQuery#testFindPageCursor*） | `findAllByQuery` 测试命中全部为 mock 或无 limit 断言 |
| G7 | **JMH/基准零覆盖**：5 模块内无基准；热路径常数因子（C9-002/003/006）与 C9-001 O(n²) 无性能回归护栏（C9 文件基准缺口 4 项已单列） | 无 benchmark 目录/依赖 |
| G8 | **IntArray 值语义零覆盖**：C9-006 的 equals/hashCode 缺陷面无测试（根因归属 nop-commons CL-700，但 5 根内 6 受累点无回归面） | src/test 中 IntArray 仅作构造使用 |

---

## 3. 保护力判定口径

- **有钉**: 存在测试，若缺陷机制回归（或既有修复被回退）该测试会红，且测试覆盖缺陷的主要后果面。
- **部分**: 同代码区域/同契约的邻接面（单线程语义、失败路径子相位、容器选型等）已被钉住，但缺陷本体（竞态交错/泄漏窗口/无界形态/静默跳过）不可被现有测试捕获。
- **无保护**: 无任何测试触及缺陷面。
- **N/A**: 确认性登记（无缺陷立项），无保护力可言。

---

## 4. 保护力矩阵——P1×17 单列详表

| ID | 缺陷（一行） | 判定 | 依据（实际打开核对） |
|---|---|---|---|
| C1-001 | OrmSessionRegistry 会话槽 check-then-act（同 C1-023 合并修复） | **无保护** | `OrmSessionRegistry` 在 5 模块 src/test 0 命中；无任何 get-or-create 或 context 注册表测试 |
| C1-003 | ormModel 非 volatile + 每请求 setOrmInterceptor 写 + clearCache 两步非原子 | **无保护** | `DefaultOrmModelProvider/TenantAwareOrmModelProvider/reloadModel` 0 命中；产品文件自 148bc54a91 后未动（缺陷在）；TestOrmSessionFactoryBeanRefreshConfig 只钉配置热刷新，不触模型代 |
| C1-015 | OrmComponentModel 双字段先发布后填充 | **无保护** | TestOrmComponentModel#testGetColumnPropIdsFollowsDeclarationOrder 为单线程列序钉（另一历史缺陷的回归钉，mock 布景下无并发面）；产品代码无 volatile/synchronized（缺陷在） |
| C1-023 | TransactionRegistry check-then-act（同 C1-001） | **无保护** | `TransactionRegistry` 在 src/test 0 命中 |
| C1-026 | JdbcTransaction 双取连接泄漏（可见性面） | **部分** | 邻接钉：TestJdbcTransactionAutoCommitRestore（连接保留时 autoCommit 恢复）、TestTransactionTemplate 各法（单线程活跃连接计数 getActiveConnections）；双线程双取泄漏窗口无钉，现计数测试在线程化改造后可捕获部分泄漏后果 |
| C2-001 | session 全生命周期零互斥（flush 静默跳过守卫） | **无保护** | 单线程 flush 重入补刷面被 TestFlushChangeRegistration 钉（ORM-01/06 回归钉），但那是 CL-002 已裁定的「静默跳过语义」单线程面；跨线程并发 flush 静默跳过→事务带未刷写提交，无任何测试。产品代码 `flusher != null → return` 仍在（OrmSessionImpl.java:183-186 亲验） |
| C2-002 | runInNewSessionAsync 完成线程 flush/registry 恢复/close 无同步 | **无保护** | `runInNewSessionAsync` 0 命中；OrmTemplateImpl 异步路径整体无测试 |
| C2-003 | 会话注册表单槽非原子身份交换/会话泄漏 | **无保护** | OrmSessionRegistry/put-restore 语义 0 命中 |
| C2-004 | 全局实体缓存 read-repair 迟到 put 覆盖 evict | **部分** | 邻接钉：TestGlobalCache#testGlobalCache（clearEntityGlobalCache 后可见新值）、testCollectionGlobalCacheEvictedAfterChange（集合变更后失效）；均为单线程 evict 正确性。装载 future 迟到回填 vs 并发 evict 交错无钉 |
| C2-005 | 抢占 CAS 谓词 NULL 退化（无条件 UPDATE→重复投递） | **部分** | 邻接钉：TestDaoEntityBlockingSourceClaim#testClaimConflictSkipsItem 钉住「updateByExample==0 必须跳过」（非 NULL 旧值 0 的冲突面）、testClaimSuccessDeliversItemAndAppliesAcquireValues 钉住抢占值写入。NULL 旧值退化面无钉且**结构性不可见**：session 被 mock，WHERE 谓词生成被屏蔽（见 N4）；产品代码 currentAcquireValues 跳过 null 仍在 |
| C2-013 | 事务注册协议无原子性（get→new→put 三步 + ERR_TXN_NOT_REGISTERED 提交后误报） | **无保护** | 邻接钉：TestTransactionTemplate×12、TestDefaultTransactionManager#testNewTransactionCommitPersistsData/RollbackDiscardsData（单线程传播与持久化）；并发注册交错、REQUIRED 组语义崩塌、提交成功后误报三个后果面全部无钉 |
| C2-015 | subTransactions 裸 Map 组提交迭代期并发注册丢写 | **部分** | 邻接钉：TestTransactionTemplateAsync#testAsyncSubTxnFailDoesNotRollbackMainGroup 钉住子事务挂接与组回滚隔离（单线程）；testAsyncGroupNewlyCreatedRollsBackGroup 钉新建组回滚。组提交迭代期并发注册→静默不提交丢写面无钉 |
| C2-017 | JdbcTransaction 连接生命周期无互斥（双取泄漏 + eagerRelease 僵尸连接静默回滚） | **部分** | 邻接钉：TestTransactionTemplate（连接身份/计数单线程）、TestJdbcTransactionAutoCommitRestore；doCommit 的 `connection==null` 静默 no-op 与僵尸重取无钉 |
| C3-001 | runInNewSessionAsync 上游永不完成→会话永不关闭 | **无保护** | 异步入口 0 命中；无挂起/超时/泄漏类测试 |
| C3-005 | flush 先清 dirty 后执行 SQL，批 DML 失败后重试 flush 永久 no-op | **部分** | 邻接钉：TestFlushFailure#testFlushFailureClearsFlushVisiting 钉「flush 失败后重试生效」——但注入的失败相位是**装载队列**（flusher.execute 内、dirty=false 之前），恰好绕开缺陷窗口（batchActionQueue.flush 在 dirty=false 之后，OrmSessionImpl.java:210-214 亲验）；DML 批失败→重试 no-op 面无钉，且现有测试制造「重试语义已覆盖」错觉（见 N3） |
| C3-006 | drainTo REQUIRES_NEW 横跨无限等待窗口（同 C6-001 合并工单） | **部分** | 邻接钉：TestDaoEntityBlockingSource 钉攒批/部分返回/maxElements=0 跳过/中断传播；事务边界与轮询期连接持有无钉 |
| C6-001 | drainTo 无限等待连接/事务/行锁持有无上界（同 C3-006） | **部分** | 同上；等待窗口内活跃连接数不增长、每 tick 短事务边界，均无钉 |

**P1 小计**: 有钉 0 / 部分 8（C1-026、C2-004、C2-005、C2-015、C2-017、C3-005、C3-006、C6-001）/ 无保护 9（C1-001、C1-003、C1-015、C1-023、C2-001、C2-002、C2-003、C2-013、C3-001）。

---

## 5. 保护力矩阵——P2/P3 归组表（57 条）

| 归组 | 缺陷 ID | 判定 | 邻接钉 / 依据 |
|---|---|---|---|
| 会话守卫与写路径 | C1-002 | 无保护 | checkContext 缺失面/惰性守卫族无测试 |
| 模型代与热刷新 | C1-004、C2-007、C1-008(c)+C2-008、C6-003 | 无保护 | G3 空洞：reloadModel/clearCache 0 命中 |
| 运行期可变注册面 | C1-005、C2-011 | 无保护 | `addDaoListener` 仅 TestOrmUpsertWiring 装配级命中；并发可见性/覆盖面无钉 |
| 静态槽与单例可见性 | C1-006、C1-024、C1-029、C1-028+C2-020 | 无保护 | OrmTimestampHelper/DaoProvider 0 命中 |
| 装载队列族 | C1-007、C2-009 | **部分** | TestBatchLoadQueueCleanup（ORM-05 清理契约）、TestCollectionBatchLoadCache（ORM-04 future 完成回填）、TestFlushFailure#testFlushFailureSkipsDelayTasks（失败跳过延迟任务）均为单线程钉；半 volatile 字段面与静默跳过守卫族（collectionActions 补发回调）无钉 |
| 查询计划缓存 | C1-008(a)(b)、C1-013 | **部分** | TestCompiledSqlStructInfoCache 钉住计划缓存命中正确性与 struct flags 存活（单线程）；双编译/跨代陈旧/可变 POJO 逸出面无钉；TestQueryPlanCacheKey 钉 key 组成 |
| 共享模型读侧 | C1-009、C1-020 | **部分** | C1-009：TestDataBaseSchemaInitializer 钉住静态字段行为面（null 容忍/过滤语义），但测试自身以保存-恢复方式写该静态（见 N2）；C1-020：TestOrmTemplate#testJoinOnNonPkColumn 钉住 join-on-non-pk 语义（单线程）——两者的读侧懒写/并发纪律面均无钉 |
| 全局缓存值与失效 | C1-010、C1-027 | 无保护 | 防御拷贝/回填竞态面无钉 |
| 元模型逸出与冻结守卫 | C1-011、C1-014、C1-017、C1-022、C1-016(残面)、C1-018、C1-019、C1-021 | 无保护 | `checkAllowChange/getFieldExprMetas/anyEntityUseTenant/DaoEntityResource` 0 命中；C1-018 修复（短名冲突抛错）已落地但**无回归钉**（grep DUPLICATE_ENTITY_SHORT_NAME 于测试 0 命中）；C1-021 修复（volatile）已落地无钉 |
| topo 重建竞态 | C1-016（主机制面） | **有钉** | TestLazyLoadOrmModel#testConcurrentAddAndTopoRebuildKeepsAllEntities——修复与回归钉同 commit 落地（99abdf8f28），反向验证见第 6 节 |
| EQL/SQL 元数据缓存 | C1-012、C2-012 | 无保护 | `EqlParseTreeParser/SqlExprMetaCache` 0 命中；海量 parse/compile 测试均为单线程正确性钉，并发冷路径面无钉 |
| 事务设施 | C1-025、C2-006、C2-014、C2-016 | **部分** | C1-025：TestTransactionTemplate#testRollback 钉 rollbackOnly 生命周期（单线程），状态字段跨线程可见性面无钉；C2-006：TestFlushFailure 钉失败复位族但钉错相位（N3），dirty 先复位 no-op 面无钉；C2-014：COW 迭代面真钉（testConcurrentAddWhileInvoking+testListenerTypeIsCopyOnWrite），注册原子性面无钉（产品代码 :56/:81-85 非 volatile 亲验仍在）；C2-016：testMultipleCommit 单线程双提交容忍（无断言体，N5） |
| 事务杂项 | C2-018、C2-019、C2-021 | 无保护 | 锁内动态加载吞吐面、DbEstimatedClock、REQUIRED 加入路径双读面 0 命中 |
| 异步链 | C3-002、C3-004 | 无保护 | runInSessionAsync 双分支契约、flushAsync 异步驱动拓扑违约面均零覆盖 |
| 延迟任务重放 | C3-003 | **部分** | 失败跳过面被钉（testFlushFailureSkipsDelayTasks）；跨 flush 重放面无钉且缺陷仍在（BatchActionQueueImpl finally `delayTasks = null` 局部遮蔽亲验，:234） |
| 载体契约 | C4-001、C4-002、C4-003 | 无保护 | 伪异步/完成线程/懒取上下文三契约无任何钉（连 javadoc 契约测试都没有） |
| 内存上界 | C5-001、C5-002、C5-005 | 无保护 | G6 空洞：行数上限断言零覆盖；C5-005 的 loadItems 超量返回面（F3 勘误⑥）被 mock 返回值恰好遮蔽（mock 恒返回 ≤maxElements） |
| 内存配置 | C5-003 | **部分** | TestOrmSessionFactoryBeanRefreshConfig 钉缓存容量/超时配置传导；per-entity 乘数口径无钉 |
| 批量分配 | C5-004 | 无保护 | — |
| 资源生命周期 | C6-002 | 无保护 | G5 空洞 |
| 读写分离 | C1-030 | **部分** | TestReadWriteRoutingDataSource（hint/轮转/权重/故障面 8 法）+ TestReadWriteSplittingOrmE2E（路由正确性+连接归还）均为单线程钉；并发轮转纪律面无钉 |
| 监听器 O(n²) | C2-010、C9-001 | **部分** | TestAbstractTransactionListeners 钉容器语义（含去重/排序触发）；C2-010 的每实体监听器堆积形态与 C9-001 的聚合/注册成本均无钉（三者与 C2-014 同工单） |
| 热路径常数因子 | C9-002、C9-003 | 无保护 | G7 空洞 |
| SQL INFO 日志税 | C9-004 | **部分** | TestSqlLogSwitch 钉开关机制（logger 级别隔离可关闭）；但 testSqlLogPrintsAtInfoByDefault **主动钉住缺省 INFO 全文本行为**——即被 C9-004 审计的行为本身（见 N1） |
| 元模型命中路径 | C9-005 | N/A | 确认性登记，无缺陷立项 |
| IntArray 值语义 | C9-006 | 无保护 | G8 空洞 |

### 判定统计

| 判定 | 条数 | 占比 |
|---|---|---|
| 有钉 | 1（C1-016 机制面） | 1.4% |
| 部分 | 24 | 32.4% |
| 无保护 | 48 | 64.9% |
| N/A | 1（C9-005） | 1.4% |
| **合计** | **74** | 100% |

分维度无保护数：C1×17、C2×8、C3×2、C4×3、C5×4、C6×2、C9×3（合计 39）。P1×17 内：**有钉 0**、部分 8、无保护 9。

---

## 6. 「有钉」判定抽查——3 条反向验证

| # | 被抽查判定 | 反向验证（若修复回退，测试是否会红） | 结论 |
|---|---|---|---|
| 1 | C1-016 有钉（TestLazyLoadOrmModel#testConcurrentAddAndTopoRebuildKeepsAllEntities） | 读完测试与 LazyLoadOrmModel.java:160-197：putEntityModel 若移出 synchronized(entityModelMap)（即回退 99abdf8f28 修复），`topoEntryInited=false` 写与读者线程重建交错→最终拓扑表缺实体→50 轮×8 写线程下 `assertTrue(names.contains(name))` 必红；产品代码 ：168-172 注释逐字记录了该「修复前」失效模式 | **成立** |
| 2 | C2-005 邻接钉（TestDaoEntityBlockingSourceClaim#testClaimConflictSkipsItem） | 若 tryClaim 无视 updateByExample 返回值（恒视为抢占成功），drainTo 返回 n=1，`assertEquals(0, n)` 必红 | **成立**（注意：只护非 NULL 冲突面，NULL 退化面不在此钉范围内） |
| 3 | C2-015 邻接钉（TestTransactionTemplateAsync#testAsyncSubTxnFailDoesNotRollbackMainGroup） | 若 runInTransactionAsync 在子任务失败时回滚主事务组，mainTxn.rollbackCount≥1，`assertEquals(0, mainTxn.rollbackCount)` 必红 | **成立**（注意：单线程，组提交迭代期并发注册面不在此钉范围内） |

---

## 7. 缺保护登记（本维度发现，统一 P2）

> 编号自 `nop-persistence-rw-C10-001` 起。按修复工单族归组；每条含缺口描述、涉及缺陷、建议回归测试形态（不含实现代码）。P1 无保护/部分项全覆盖，P2 缺口择最重族登记。

### nop-persistence-rw-C10-001 会话/事务注册表 get-or-create 与单槽交换无回归钉
- **缺口**: OrmSessionRegistry 与 TransactionRegistry 的 `instance()` check-then-act 与单槽 put/restore 在整个测试套件零覆盖；注册表双实例、槽互踩、会话身份交换三个后果面全部裸奔。
- **涉及**: C1-001、C1-023（P1 无保护）
- **建议回归测试形态**: (1) 真 context（或BaseContext 测试替身）上双线程并发首触 `OrmSessionRegistry.instance()`/`TransactionRegistry.instance()`，断言两线程取得同一实例；(2) 单线程交错序列测试：put(S1)→put(S2)→收尾 S1→收尾 S2 的注册表状态与 close 调用断言（模拟 C2-003 时序，无需真线程）。
- **严重度**: P2

### nop-persistence-rw-C10-002 runInNewSessionAsync 生命周期族零覆盖（异步入口全裸奔）
- **缺口**: 异步会话入口（runInSessionAsync/runInNewSessionAsync）无任何直接测试；完成线程收尾、上游永不完成泄漏、注册表恢复交错三个 P1 级后果面全部无钉。
- **涉及**: C2-002、C2-003、C3-001（P1 无保护×3）
- **建议回归测试形态**: (1) 用受控（手动完成的）future 驱动 runInNewSessionAsync：断言上游完成前 session 已注册、完成后 flush+close+注册表恢复各发生一次；(2) 上游永不完成 + context 关闭（或超时兜底路径）→ 断言 session 最终被 close（修复后语义）；(3) 恢复点与嵌套 runInNewSession 交错的确定性时序测试。
- **严重度**: P2

### nop-persistence-rw-C10-003 模型提供器可见性与热刷新失效族零覆盖
- **缺口**: reloadModel/clearCache 全链路零测试；ormModel 可见性、旧模型无限期读取、queryPlanCache 跨代陈旧三个缺陷族的回归面不存在。
- **涉及**: C1-003（P1 无保护）、C1-004、C2-007、C1-008(c)+C2-008、C6-003
- **建议回归测试形态**: (1) reloadModel 后新开 session 断言读到新模型结构（列/实体增删可见）；(2) reloadModel 后执行同一 EQL 断言编译计划按新模型重编（C1-008(c)+C2-008 的失效语义）；(3) getOrmModel 并发读压测下断言 interceptor 状态一致性（修复后形态）。
- **严重度**: P2

### nop-persistence-rw-C10-004 session flush 跨线程互斥无回归钉
- **缺口**: flush 静默跳过守卫的单线程重入面已被 ORM-01/06 钉住，但跨线程并发 flush（静默跳过→未刷写提交；或双 CascadeFlusher 并发遍历）零覆盖。
- **涉及**: C2-001（P1 无保护）、C1-002
- **建议回归测试形态**: 双线程并发 flush 同一 session，断言二者均成功（锁语义）或第二者显式报错（fail-fast 语义）——任一修复取向都可断言，唯「静默跳过+调用方感知成功」必须红；配合事务提交拦截器层断言「提交时无滞留批动作」。
- **严重度**: P2

### nop-persistence-rw-C10-005 全局缓存 read-repair vs evict 交错无回归钉
- **缺口**: 单线程 evict 语义已钉，但装载 future 迟到回填覆盖并发 evict（陈旧值无界驻留）无测试。
- **涉及**: C2-004（P1 部分）、C1-027、C1-010
- **建议回归测试形态**: 注入受控 batchLoad future（延迟完成），在等待窗口内触发 evictGlobalCache，future 完成后断言缓存中不存在迟到回填值（或已被世代号失效）。
- **严重度**: P2

### nop-persistence-rw-C10-006 抢占 CAS NULL 退化面无回归钉
- **缺口**: 抢占测试只护非 NULL 冲突跳过面；NULL 旧值退化（重复投递）无钉，且 mock 屏蔽 WHERE 生成使其结构性不可测（N4）。
- **涉及**: C2-005（P1 部分）
- **建议回归测试形态**: 真 H2 上建含可空抢占字段的任务表，插入 NULL 初始态行，两消费者串行模拟两次 tryClaim（或双线程），断言仅一次投递成功；或捕获 example 断言生成谓词含 `IS NULL`。
- **严重度**: P2

### nop-persistence-rw-C10-007 事务注册协议原子性与组提交丢写无回归钉
- **缺口**: 传播/持久化单线程语义已钉；并发注册交错（REQUIRED 组语义崩塌、提交成功后误报 ERR_TXN_NOT_REGISTERED）与组提交迭代期并发注册子事务（静默不提交）无钉。
- **涉及**: C2-013（P1 无保护）、C2-015（P1 部分）
- **建议回归测试形态**: (1) 同 context 双线程并行 REQUIRED 进入，断言注册表中单事务实例、双方正常提交、无 ERR_TXN_NOT_REGISTERED；(2) runWithoutTransaction 窗口交错序列测试（确定性时序）；(3) 组事务提交迭代进行中注册新子事务，断言其被提交（修复后语义）。
- **严重度**: P2

### nop-persistence-rw-C10-008 JdbcTransaction 连接生命周期互斥与泄漏面无回归钉
- **缺口**: 连接身份/autoCommit 恢复/活跃计数单线程面已钉；双取泄漏、eagerRelease 僵尸连接静默回滚、doCommit null 静默 no-op 无钉。
- **涉及**: C2-017（P1 部分）、C1-026（P1 部分）
- **建议回归测试形态**: (1) 双线程并发 getConnection 断言返回同一连接（或竞败方显式失败）；(2) eagerRelease 配置下 commit 后并发 getConnection 断言显式失败而非取得新连接；(3) 强制 doCommit 时 connection=null 断言有告警/异常而非无痕 no-op；(4) 压测后 getActiveConnections 归零断言（泄漏捕获）。
- **严重度**: P2

### nop-persistence-rw-C10-009 flush 半成功残留（dirty 先复位）钉错相位
- **缺口**: 现有失败测试的失败点在装载相位（dirty=false 之前），「批 DML 失败后重试 flush 永久 no-op」的缺陷窗口零覆盖，且现有测试制造重试语义已被覆盖的错觉（N3）。
- **涉及**: C3-005（P1 部分）、C2-006
- **建议回归测试形态**: 注入批 DML 执行期失败（约束冲突/触发器错误，落点在 batchActionQueue.flush），断言：dirty 状态保守恢复（对齐 flushImmediately 的 oldDirty||dirty 哲学）、恢复后重试 flush 真正补写剩余动作、新会话可读到全部修改。
- **严重度**: P2

### nop-persistence-rw-C10-010 drainTo 事务边界与连接持有窗口无回归钉
- **缺口**: 攒批/中断语义已钉；REQUIRES_NEW 横跨无限等待、等待窗口连接持有、每 tick 短事务边界无钉。
- **涉及**: C6-001、C3-006（P1 合并工单，均部分）
- **建议回归测试形态**: 包装 DataSource 计数借出：drainTo 阻塞等待期间断言活跃借出数不随轮询增长（修复后为 0 或每 tick 归还）；空队列 take() 在有界时间内可被中断且无连接滞留。
- **严重度**: P2

### nop-persistence-rw-C10-011 静态槽/可见性族零回归钉（含已落修复无钉项）
- **缺口**: 静态单例槽与跨线程可见性面整族零覆盖；C1-018（短名冲突抛错）、C1-021（volatile）修复已落地但无回归钉，回退不可检测。
- **涉及**: C1-006、C1-024、C1-029、C1-028+C2-020、C1-011、C1-025、C1-018、C1-019、C1-021（P2 族）
- **建议回归测试形态**: (1) 已落修复补钉：短名冲突注册断言抛错且模型状态不含半注册实体；动态实体注册后 anyEntityUseTenant 立即翻转；(2) 静态槽结构守卫测试（反射断言 `_instance` 类字段为 final/volatile，回退即红）；(3) registerInstance 写后跨线程读压力测试（可选）。
- **严重度**: P2

### nop-persistence-rw-C10-012 运行期可变注册面与元数据逸出族零回归钉
- **缺口**: interceptor/daoListener 运行期增删可见性、addDaoListener 覆盖、EqlParseTreeParser 有状态单例、CompiledSql 可变面、活 Map 逸出全部无钉。
- **涉及**: C1-005、C2-011、C1-012、C1-013、C1-014、C1-017、C1-022（P2 族）
- **建议回归测试形态**: (1) 并发 add/removeInterceptor 与并发 ORM 操作压测断言无丢失/无 CME（修复后形态）；(2) getFieldExprMetas 等返回集合不可变性断言（返回拷贝或不可变视图则过）；(3) 并发冷解析压力测试断言语句级隔离（共享单例修复后形态）。
- **严重度**: P2

### nop-persistence-rw-C10-013 异步载体与执行上下文契约零回归钉
- **缺口**: 伪异步载体、完成线程执行模型、连接获取上下文三个契约（C4 全维度）连 javadoc 级契约测试都没有；flushAsync 同步完成假设无守卫测试。
- **涉及**: C4-001、C4-002、C4-003、C3-004（P2 族）
- **建议回归测试形态**: (1) 契约钉测试：返回真异步（非同步完成）future 的 SPI 驱动下，flushAsync 应 fail-fast 或保序（修复取向断言）；(2) 文档契约固化：用架构测试（ArchUnit 类规则）断言异步 API javadoc 含完成线程语义声明（防契约进一步缺失）。
- **严重度**: P2

### nop-persistence-rw-C10-014 内存上界面（findAll 族 limit 静默丢弃）零回归钉
- **缺口**: 行数上限断言零覆盖；findAllByQuery limit 被静默丢弃、loadItems 超量返回、cacheRef 单条目无上限均无测试。
- **涉及**: C5-001、C5-002、C5-005（P2 族，C5-005 含 F3 勘误⑥）
- **建议回归测试形态**: (1) 真 H2 大于 limit 的表，`findAllByQuery(query.setLimit(N))` 断言返回 ≤N 行（当前实现必红，即回归钉）；(2) loadItems 侧 mock 返回 >maxElements 批次，断言 drainTo 返回数仍 ≤maxElements（当前必红）；(3) cacheRef 单条目体积契约测试（可选，防回归为观测指标）。
- **严重度**: P2

### nop-persistence-rw-C10-015 热路径结构性回归钉（IntArray 值语义 / 单槽缓存命中 / O(n²) 聚合）
- **缺口**: C9-006 修复后（值 equals）无命中语义回归钉；单槽 SQL 缓存命中率与 O(n²) 监听器聚合无护栏；JMH 基准缺口（C9-002）仍在。
- **涉及**: C9-006、C9-003、C9-001、C9-002（P2/P3 族）
- **建议回归测试形态**: (1) IntArray 值相等单测（equals/hashCode 对称性，落点 nop-commons，按 CL-700 移交；5 根内补 persistDriver 缓存命中回归——同 propIds 两次生成断言命中同槽）；(2) 监听器聚合结构断言（单事务 N 实体注册事件数 O(1)）；(3) JMH 基准移交 nop-benchmark（对齐 C9 基准缺口 4 项）。
- **严重度**: P2

---

## 8. 测试套件自身缺陷注记（钉住缺陷行为/结构性盲区，N1~N6）

| # | 位置 | 缺陷 | 处置建议 |
|---|---|---|---|
| N1 | nop-dao `TestSqlLogSwitch#testSqlLogPrintsAtInfoByDefault` | **主动钉住被审计缺陷行为**：断言「每条 SQL 缺省以 INFO 全文本输出」（:71-80），与 C9-004（每条 SQL INFO 日志税）正面冲突。C9-004 修复（降默认级别/采样）必须同步改写此测试，否则修复被测试阻塞 | C9-004 工单挂联动改测项；改写为「缺省级别=修复后目标级别」断言 |
| N2 | nop-orm `TestDataBaseSchemaInitializer`（:29-36） | **测试自身是 C1-009 反模式的使用者**：setup/teardown 以保存-恢复方式直接写静态字段 `specifyQuerySpaces`；钉住的是静态行为面（null 容忍/过滤），对并发写纪律零守护 | 修复 C1-009 时同步重构为实例化/参数化注入形态，消除静态写 |
| N3 | nop-orm `TestFlushFailure#testFlushFailureClearsFlushVisiting` | **钉错相位**：注入的失败点在装载队列（dirty=false 之前），「flush 失败后重试生效」的断言对 C3-005/C2-006 的缺陷窗口（批 DML 执行期、dirty=false 之后）不成立，形成重试语义已被覆盖的错觉 | 保留本测试（其相位仍有价值），按 C10-009 补 DML 相位失败测试 |
| N4 | nop-orm-data `TestDaoEntityBlockingSourceClaim` / `TestDaoEntityBlockingSource` | **mock 屏蔽 SQL 语义**：`mock(IOrmSession).updateByExample` 使 WHERE 谓词生成不可观测（C2-005 结构性盲区）；`mock(IEntityDao).findAllByQuery` 使 limit 生成不可观测（C5-005⑥ 盲区——mock 恒返回 ≤maxElements，超量返回面永不可测） | C10-006/C10-014 的回归测试必须用真 H2（或谓词捕获），不得沿用全 mock |
| N5 | nop-dao `TestTransactionTemplate#testMultipleCommit`（:327-333） | **无断言体**：双提交容忍测试只验证「不抛异常」，对 C2-016 状态机面（双开/复合检查）无任何状态断言 | 补提交后状态断言（单次实际提交、状态终态、监听器单次通知） |
| N6 | nop-orm-model `TestLazyLoadOrmModel#testConcurrentAddAndTopoRebuildKeepsAllEntities` | **测试局限注记**（非缺陷）：(1) reader 线程 busy-loop 至 interrupt，无优雅停机；(2) IEntityModel 为 mock，`setDependByOtherEntity` 落在 mock 上——C1-016 残面（冻结模型字段写可见性）对本测试结构性不可见 | 保留；C1-016 残面若修复需另补值断言测试 |

---

## 9. CL 线索（跨维度移交注记，单列）

| 编号 | 内容 | 移交去向 |
|---|---|---|
| CL-C10-01 | IntArray 值 equals 修复的回归钉落点在 nop-commons（CL-700 已登记）；5 根内 6 受累点的 persistDriver 缓存命中回归钉按 C10-015(1) 随修复轮补 | nop-commons 修复轮 + nop-persistence 修复轮 |
| CL-C10-02 | N1（TestSqlLogSwitch 钉住缺省 INFO）与 C9-004 工单互为前置：修复轮必须包含测试改写，否则 closure gate 会被既有测试卡死 | C9-004 修复工单 |
| CL-C10-03 | 已落修复无钉清单：C1-018（短名冲突抛错）、C1-021（anyEntityUseTenant volatile）、LazyLoadOrmModel 锁化（仅机制面有钉）——修复轮收口时按 C10-011 补钉，防止回退不可检测 | 修复轮验收清单 |
| CL-C10-04 | 基准缺口与 C9 文件「基准缺口登记」节合并计数，不重复立案（C10-015(3) 仅作移交注记） | nop-benchmark |
| CL-C10-05 | 本维度判定方法学沉淀：全 mock 会话/DAO 测试对「SQL 谓词/limit 生成」类缺陷结构性失明（N4），后续并发/语义审计应将「mock 覆盖度」列为测试资产盘点固定项 | 审计方法学（lessons 候选） |

---

## 10. 统计与结论

- **测试资产**: 245 测试类 / 1578 @Test；真并发测试仅 1 个（nop-orm-model TestLazyLoadOrmModel），占 0.4%；异步入口、引擎级单例、模型热刷新、全局缓存失效、取消/超时、内存上界、JMH 七大被测面为零覆盖（G1~G8）。
- **保护力矩阵**: 74 条保留发现——有钉 1（1.4%）/ 部分 24（32.4%）/ 无保护 48（64.9%）/ N/A 1。**P1×17：有钉 0、部分 8、无保护 9**。
- **缺保护登记**: C10-001 ~ C10-015 共 **15 条**（统一 P2；全覆盖 9 条 P1 无保护 + 8 条 P1 部分 + 12 个最重 P2 族，按修复工单族归组）。
- **测试套件自身缺陷注记**: N1 ~ N6 共 **6 条**（N1/N4 为修复轮硬前置：既有测试钉住缺陷行为、mock 结构性屏蔽缺陷面）。
- **结论**: 本单元并发缺陷的整体回归保护接近裸奔——除 topo 重建竞态（修复+真并发回归钉同轮落地）与一批单线程邻接语义外，74 条发现中近七成无任何测试触及；P1 级缺陷无一拥有能捕获其并发后果的回归测试。若按「缺保护先补钉、修复随钉落地」推进，C10-001~015 的 15 条 P2 即为修复轮的回归测试任务清单。

---

*审计执行：C10 子代理（测试保护力维度），2026-10-18。只读纪律：除本文件外未修改任何文件。*

---

## C10 复核结论

- **日期**: 2026-10-18
- **复核人**: C10 独立复核子代理（与初审代理相互独立，怀疑姿态抽验）
- **基线**: HEAD abccc80dde（与初审一致，无仓库漂移）；本节为唯一追加产出，未改动上文任何行。

### R1. 抽验明细（抽验 24 行/组，超出 ≥10 行要求）

**P1 行（10 条）**：

| 行 | 抽验证据（亲自打开核对） | 结论 |
|---|---|---|
| C1-001 无保护 | `OrmSessionRegistry.instance()` check-then-act 亲验在（get-or-create 无同步，OrmSessionRegistry.java:22-30）；5 模块 src/test grep `OrmSessionRegistry\|TransactionRegistry` = 0 命中复测 | **成立** |
| C1-003 无保护 | DefaultOrmModelProvider.java:17 / TenantAwareOrmModelProvider.java:16 `ormModel` 均 non-volatile 亲验在；`reloadModel` 测试 0 命中复测；git 证实 DefaultOrmModelProvider 自 148bc54a91 后零提交；TestOrmSessionFactoryBeanRefreshConfig 两法只钉 global cache/plan cache 配置传导（:45/:77），不触模型代 | **成立** |
| C1-015 无保护 | OrmComponentModel.java:26-27 `colPropIds/propIds` 非 volatile、`getColumnPropIds()`（:106-117）check-then-act 惰性填充亲验在；TestOrmComponentModel 唯一法为单线程列序钉 | **成立** |
| C1-023 无保护 | TransactionRegistry 测试 0 命中复测 | **成立** |
| C2-001 无保护 | OrmSessionImpl.java:183-186 `flusher != null → return` 静默跳过守卫亲验在（行号精确命中）；TestFlushChangeRegistration 确为 ORM-01/06 单线程 changedDuringFlush 重放钉（:25-30 注释、两法无线程），非跨线程互斥 | **成立** |
| C2-002/C2-003 无保护 | `runInNewSessionAsync\|runInSessionAsync` 5 模块测试 0 命中复测 | **成立** |
| C2-005 部分 | 邻接钉两法亲验（TestDaoEntityBlockingSourceClaim :79 mock updateByExample=0、:103 旧值 0、:109 `assertEquals(0, n)`）；产品缺陷在（DaoEntityBlockingSource.java:263-280 currentAcquireValues 三字段均 `if (v != null)` 跳过，NULL 退化仍在）；N4 mock 屏蔽属实 | **成立** |
| C2-013 无保护 | TestDefaultTransactionManager×8（真 H2，JdbcTestCase 基座）+ TestTransactionTemplate×12 复测均单线程（grep Thread/Executor/CountDownLatch = 0）；并发注册交错/REQUIRED 组崩塌/ERR_TXN_NOT_REGISTERED 误报三后果面无钉 | **成立** |
| C2-015 部分 | testAsyncSubTxnFailDoesNotRollbackMainGroup `assertEquals(0, mainTxn.rollbackCount)`（:67）+ 组挂接断言（:71）；FakeTransactionManager + FutureHelper.syncGet 单线程收敛亲验 | **成立** |
| C1-026/C2-017 部分 | TestJdbcTransactionAutoCommitRestore×2 单线程真 H2 autoCommit 恢复钉亲验；泄漏窗口/僵尸重取/doCommit null no-op 无钉 | **成立** |
| C3-005 部分 | 失败相位亲验：CascadeFlusher.execute() 内 `session.flushBatchLoadQueue()`（:90）先于 `dirty=false`（OrmSessionImpl:210-213）；testFlushFailureClearsFlushVisiting 失败注入为装载队列（drop 表 :49 + enqueue proxy :47）亲验。**另发现初审未及的精化点，见 R2-1** | **成立（含精化）** |
| C3-006/C6-001 部分 | `@Transactional(REQUIRES_NEW)` 标注 drainTo（DaoEntityBlockingSource.java:172）+ `FutureHelper.waitUntil` 轮询窗口亲验在（maxWait<0 → Long.MAX_VALUE/2）；TestDaoEntityBlockingSource 6 法攒批/部分返回/maxElements=0/中断钉、零事务边界断言复测 | **成立** |

**「部分」行（另 5 组）**：查询计划缓存组（TestCompiledSqlStructInfoCache×2 + TestQueryPlanCacheKey×1 单线程钉亲验）✓；C2-014（testConcurrentAddWhileInvoking 实为单线程回调内 addListener 重入亲验、testListenerTypeIsCopyOnWrite 结构断言亲验；产品 AbstractTransaction.java:56 非 volatile + :81-85 惰性 check-then-act 行号精确命中）✓；C1-030（两 RW 测试文件无真线程——仅 ThreadLocal:42 与注释，单线程钉亲验）✓；C9-004/C1-009/C5-003/C1-020 邻接钉均按引核对 ✓。

**「无保护」行（另 4 组）**：C5-001/002/005（findAllByQuery 测试命中全为 mock 或无 limit 断言复测；唯一 setLimit 在 TestEntityDaoQuery:115 分页路径）✓；C4-001~003（flushAsync 5 模块测试 0 命中复测）✓；元模型逸出组（checkAllowChange/getFieldExprMetas/anyEntityUseTenant/DaoEntityResource 均 0 命中复测；DUPLICATE_ENTITY_SHORT_NAME 测试 0 命中复测——C1-018 已落修复无钉成立）✓；静态槽组（OrmTimestampHelper/DaoProvider.registerInstance/EqlParseTreeParser/SqlExprMetaCache 均 0 命中复测）✓。

**「有钉」C1-016**：真并发形态亲验（50 轮×8 adder+1 busy-loop reader、CountDownLatch 起跑、join-isAlive 防假失败）；产品侧 putEntityModel `synchronized(entityModelMap)` + `volatile topoEntryInited`（LazyLoadOrmModel.java:41/:168-197）+ :165-171 注释逐字记录修复前失效模式亲验；修复 commit 99abdf8f28 在史（提交信息含「LazyLoadOrmModel入锁消竞态」）。**反向验证 #1 成立**；第 6 节反向验证 #2（C2-005，:109 断言）、#3（C2-015，:67 断言）复核均成立。

### R2. 修正行清单（6 项，均不推翻初审主结论）

1. **C10-009 缺口表述过概化（精化）**: 初审称「现有失败测试的失败点在装载相位（dirty=false 之前）」。复核发现 `testFlushFailureSkipsDelayTasks` 的失败相位**恰在缺陷窗口内**：flushUpdate → EntityPersisterImpl:492 `enqueueUpdate` 仅入队，SQL 实际执行在 OrmSessionImpl:212 `batchActionQueue.flush()`（dirty=false 之后）——drop college 表致其失败即发生在 C3-005/C2-006 缺陷窗口。但该测试只断言延迟任务跳过（:125），未断言重试行为，故「DML 相位失败后重试语义零覆盖、重试已覆盖错觉」的结论不变。建议改写为：「重试语义钉（ClearsFlushVisiting）失败点在装载相位；唯一落在 DML 相位的失败测试（SkipsDelayTasks）只断言延迟任务跳过，重试行为零断言」。
2. **C2-016 判定偏高（部分 → 应为无保护）**: 其唯一依据 testMultipleCommit 无断言体（N5 自认「无任何状态断言」），按第 3 节口径不构成邻接钉；且该测试钉住「双提交不抛」现状，属 N1 同型（CAS fail-fast 修复取向会被其阻塞）。采纳则：部分 24→23、无保护 48→49（总数 74、有钉 1、N/A 1、P1 内部统计均不变）。
3. **C1-030 行测试法计数**: TestReadWriteRoutingDataSource 实为 **12** @Test（:140~:430），非「8 法」；判定不受影响。
4. **C3-003 行号微偏**: BatchActionQueueImpl finally 局部遮蔽赋值实为 **:235**（写 :234），遮蔽声明在 :152；「缺陷仍在」亲验成立。
5. **N1 精度注记**: testSqlLogPrintsAtInfoByDefault 在 ：66 显式 `setLevel(Level.INFO)`，钉的是「INFO 级别下输出 SQL 全文本」而非严格「缺省配置=INFO」。若 C9-004 修复仅下调日志配置缺省级别，该测试不会红（不阻塞）；若改产品日志调用级别（INFO→DEBUG）或采样，必红（阻塞）。CL-C10-02「修复轮必须包含测试改写」作为防御性要求维持。
6. **测试资产类计数口径差**: 按「含 @Test 的测试文件」复测为 **249**（nop-orm 84 / nop-orm-eql 99 / nop-orm-model 8 / nop-dao 56 / nop-orm-data 2），初审记 245；**@Test 总数 1578 双向精确一致**。类计数差 ±4 属纳入口径差，不影响任何判定（真并发测试=1 复核成立）。

### R3. C10-001~015 裁定表

| 登记 | 涉及缺陷与矩阵自洽 | 形态可执行性 | 裁定 |
|---|---|---|---|
| C10-001 | ✓（C1-001/C1-023 均 P1 无保护） | ✓ | 维持 |
| C10-002 | ✓（C2-002/C2-003/C3-001） | ✓（亲验 `runInNewSessionAsync(Function<IOrmSession, CompletionStage<T>>)`，OrmTemplateImpl:244——手动 future 可注入） | 维持 |
| C10-003 | ✓（5 ID 均矩阵无保护） | ✓ | 维持 |
| C10-004 | ✓（C2-001/C1-002） | ✓ | 维持 |
| C10-005 | ✓（C2-004 部分 + C1-027/C1-010 无保护） | ✓ | 维持 |
| C10-006 | ✓（C2-005） | ✓（已按 N4 要求设计为真 H2/谓词捕获） | 维持 |
| C10-007 | ✓（C2-013/C2-015） | ✓ | 维持 |
| C10-008 | ✓（C2-017/C1-026） | ✓ | 维持 |
| C10-009 | ✓（C3-005/C2-006） | ✓ | 维持；缺口表述按 R2-1 精化 |
| C10-010 | ✓（C6-001/C3-006） | ✓ | 维持 |
| C10-011 | ✓（9 ID 全部对应矩阵无保护项，含已落修复无钉 C1-018/C1-021） | ✓ | 维持 |
| C10-012 | ✓（7 ID） | ✓ | 维持 |
| C10-013 | ✓（C4-001~003/C3-004） | ✓（ArchUnit 当前非依赖项，可改 javadoc 反射断言，不阻塞） | 维持 |
| C10-014 | ✓（C5-001/C5-002/C5-005 含 F3⑥） | ✓（亲验 drainTo 超量返回面 mock 可构造：loadItems 返回 >maxElements 时 c 可超限） | 维持 |
| C10-015 | ✓（C9-006/003/001/002） | ✓（IntArray 落点 nop-commons 已按 CL-700 移交；nop-benchmark 模块在） | 维持 |

覆盖度核对：9 条 P1 无保护 + 8 条 P1 部分全部被登记覆盖 ✓；初审声明「P2 缺口择最重族登记」成立，但存在 2 处应登记未登记（见 R4）。

### R4. 补充发现（编号续 C10-016 起，共 2 条）

**nop-persistence-rw-C10-016 批动作失败回调补偿（C2-009 族）已落修复零回归钉**
- **缺口**: commit 0084822b0b 为 BatchActionQueueImpl 新增 +84/-9 行失败补偿——`notifyUnsubmittedFailure`（中断后未提交动作统一补发失败通知）、collActions 失败路径 `onFailure`、flushAsync `errorRef` 双源判定（waitAll 对 rejected ResolvedPromise 不透传异常）。5 模块测试 grep `notifyUnsubmittedFailure\|collActions\|collectionActions\|ICollectionAction\|BATCH_FLUSH_ABORTED` = **0 命中**；testFlushFailureSkipsDelayTasks 虽执行到该补偿路径但只断言延迟任务跳过，补偿行为本身（回调确实送达）无任何断言——回退不可检测。与 C10-011 所登记的 C1-018/C1-021 同属「已落修复无钉」，但初审未登记本项（矩阵行已记「collectionActions 补发回调无钉」却未入登记清单，CL-C10-03 清单亦漏）。
- **涉及**: C2-009（P2，矩阵部分）
- **建议回归测试形态**: 批 flush 中途失败断言未提交动作收到 onFailure 补发（mock IBatchAction 计数）；collectionActions 在整体失败时收到 onFailure（非静默）；ResolvedPromise rejected 场景 flushAsync 错误经 errorRef 传播到调用方。
- **严重度**: P2

**nop-persistence-rw-C10-017 事务状态机复合检查（C2-016）保护力降级 + 现状容忍行为钉**
- **缺口**: C2-016（P2）唯一「部分」依据 testMultipleCommit 无断言体，按口径应判无保护；且该测试主动钉住「双提交不抛」现状——若 CAS 状态机修复取向为对复合状态违规 fail-fast，将被既有测试阻塞（N1 同型，初审未注记）。
- **涉及**: C2-016（P2）
- **建议回归测试形态**: 提交后状态断言（实际提交恰好一次、状态达终态、监听器恰好通知一次）；双开 / 提交后关闭 / rollback 后 commit 等复合检查断言（fail-fast 取向）或显式容忍语义断言（保守取向），二者择一并同步改写 testMultipleCommit。
- **严重度**: P2

### R5. N1~N6 注记复核

| # | 复核证据 | 裁定 |
|---|---|---|
| N1 | TestSqlLogSwitch :64-81 亲验：:66 显式 setLevel(INFO)、:71-80 三条 INFO 断言与初审引用一致 | 成立（精度注记见 R2-5） |
| N2 | TestDataBaseSchemaInitializer :29/:31/:36 静态字段保存-恢复写亲验，恰为 C1-009 反模式 | 成立 |
| N3 | 相位亲验：CascadeFlusher.execute:90 `flushBatchLoadQueue()` 在 dirty=false 之前；ClearsFlushVisiting 失败注入为装载队列 | 成立（连带精化见 R2-1） |
| N4 | 全 mock 亲验：updateByExample mock（Claim:79）、findAllByQuery mock（Claim:87 / Source:53,89,107）——谓词与 limit 生成不可观测、超量返回面不可测均成立 | 成立 |
| N5 | testMultipleCommit :326-333 无断言体亲验（初审写 ：327-333，自方法体起算，实质一致） | 成立（并升级为 R4-C10-017） |
| N6 | reader busy-loop（:88-92 `while(!isInterrupted)`）亲验；dynModel 返回 mock IEntityModel，`setDependByOtherEntity` 落点在 OrmModelTopEntryBuilder:35（topo 重建路径调用）→ 落在 mock 上，残面结构性不可见亲验 | 成立 |

### R6. 口径核对

- **74 条 / P1×17**: 与 7 维度文件 `## 最终保留项` 逐表核对一致——C1×30（P1×5/P2×16/P3×9）、C2×21（P1×8/P2×9/P3×4，含 C2-014 降级、C2-021）、C3×6（P1×3）、C4×3、C5×5（P2×3/P3×2）、C6×3（P1×1）、C9×6（P2×1/P3×5）；合计 74 = P1×17 + P2×35 + P3×22。初审对任务书的「C2 保留 20→21」勘误核实成立。
- **P1 终局清单 17 ID**: 与 C1/C2/C3/C6 四文件终裁逐一吻合（C1-001/003/015/023/026、C2-001/002/003/004/005/013/015/017、C3-001/005/006、C6-001）✓。
- **矩阵内部自洽**: P2/P3 归组表 57 ID 全覆盖（C1×25+C2×13+C3×3+C4×3+C5×5+C6×2+C9×6）；有钉 1 + 部分 24 + 无保护 48 + N/A 1 = 74 ✓；P1 小计（0/8/9）✓；分维度无保护 39（C1×17、C2×8、C3×2、C4×3、C5×4、C6×2、C9×3，P2/P3 子集口径）亲算吻合 ✓；C1-016 主机制面（有钉）与残面（随元模型逸出组无保护）不重复计数 ✓。
- **修正后口径（若采纳 R2-2）**: 部分 23 / 无保护 49，其余不变。

### R7. 最终统计裁定

| 项 | 初审 | 复核裁定 |
|---|---|---|
| 保留发现总数 74 / P1×17 | ✓ | **维持** |
| 有钉 1（仅 C1-016 机制面）/ N/A 1（C9-005） | ✓ | **维持**（反向验证 3 条全部复核成立） |
| 部分 24 / 无保护 48 | ✓ | **建议修正**：C2-016 部分→无保护，即部分 23 / 无保护 49 |
| P1 内 有钉 0 / 部分 8 / 无保护 9 | ✓ | **维持** |
| 缺保护登记 15 条（C10-001~015，P2） | ✓ | **维持**；建议增补 C10-016/C10-017 → 17 条 |
| 测试套件注记 N1~N6 | ✓ | **维持**（N1/N5 附精度注记） |
| 「近七成无任何测试触及、P1 无一拥有能捕获并发后果的回归钉」总结论 | ✓ | **维持**（无保护 48/49 ≈ 65%~66%，加 N/A 后非钉保护不足三成的量级判断成立） |

**复核总评**: 初审矩阵的判定纪律与证据质量整体扎实——24 行/组抽验中 23 项判定与证据精确成立（含全部行号级引用），唯一实质偏差是 C2-016 一条的「部分」判定偏宽；两处补充登记（C10-016/C10-017）均为初审已触及但未入登记清单的「已落修复无钉/现状行为钉」盲区。最终统计维持：74 条 / P1×17 / 有钉 1，缺保护登记建议 15→17 条。

*复核执行：C10 独立复核子代理，2026-10-18。只读纪律：仅追加本节，未修改上文与任何代码。*
