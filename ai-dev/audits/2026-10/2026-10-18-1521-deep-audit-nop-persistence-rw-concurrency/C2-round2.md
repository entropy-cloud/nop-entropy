# C2 锁与同步 — 第 2 轮深挖（blind round 2）

- **维度**: C2 锁与同步
- **批次**: 深挖合并批（第 2 轮，全部 5 源码根）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **盲审纪律声明**: 本轮为盲审第 2 轮。未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；唯一例外为本目录 7 个维度初审文件（第 1 轮，必要输入）。所有验证基于当前 live code 逐行阅读。
- **本轮目标**（按深挖指令）:
  1. C2-013/014/015/017（dao 事务设施 P1×4）失效面扩展——事务 API 全调用面枚举（哪些上层路径并发可达：executeBlocking/异步 biz）；
  2. 「补锁三约束」（listener 锁外执行 / parent→sub 单向锁序 / 池等待不入全局锁）的死锁环反证；
  3. C2-001~003（session 零互斥族）的 F3 收口——session 并发触达的入口全枚举；
  4. 跨批次同族组 (3)（缓存 invalidation-vs-repopulate）裁定。
- **新发现编号**: C2-021（P3×1）；CL-700~799 段本轮无新增 CL。

---

## 深挖 1：事务 API 全调用面（C2-013/014/015/017 失效面扩展）

5 源码根内 `runInTransaction/runInTransactionAsync` 及事务入口全量枚举（并发可达性逐点判定）：

| # | 入口 | 位置 | 并发可达形态 | 判定 |
|---|------|------|-------------|------|
| 1 | `TransactionalMethodInterceptor`（同步分支） | `nop-dao/txn/interceptor/TransactionalMethodInterceptor.java:43` | `@Transactional` biz 方法，HTTP worker/调度线程 | 并发面=跨 context 并行（各自 registry），同 context 并行分支即 C2-013 场景 |
| 2 | `TransactionalMethodInterceptor`（异步分支） | 同上 `:34`（`proceedAsync` → `runInTransactionAsync`） | **异步 biz**：方法返回 CompletionStage，commit/cleanup 在完成线程（`completeAsyncOnContext`）——同 context 两异步分支共享同一 registry 与事务对象，C2-013/014/015/017 全部命中面 | **主并发面** |
| 3 | `TransactionServiceInterceptor` | `:33,40`（RPC 服务级事务） | 同 #1/#2，异步 RPC 链经 `runInTransactionAsync` | 同上 |
| 4 | `TransactionalFunctionInvoker` | `:47,54` | 编程式事务包装（平台工具） | 同上 |
| 5 | `JdbcTemplateImpl.withTxnForSql` → `runInTransaction(SUPPORTS)` | `JdbcTemplateImpl.java:182` | **每条 auto-commit SQL** 必经——无外层事务时每 SQL 新建/注册/注销一次事务对象（C9-002 已量化其分配税）；SUPPORTS 下 `getTxnState(true)` 若同 context 并行 SQL 与 REQUIRED 分支交错，注册协议竞态同样可达 | 高频并发面 |
| 6 | `UpsertExecutors` | `:90,109,120`（`runInTransaction(REQUIRED)` 逐语句） | upsert 批工具，调用方线程 | 并发=多事务并行，同 context 内嵌套场景受 C2-013 面 |
| 7 | `JdbcDialectProvider.getDialectFromTxn` | `:61`（`runInTransaction(SUPPORTS)`） | 方言冷解析，get-then-put（非 bin 锁内） | 冷路径；并发首查重复嗅探=CL-302 |
| 8 | `AbstractDaoHandler.runInTransaction/runLocal`（nop-orm） | `AbstractDaoHandler.java:93-106` | DAO handler 业务方法（`runInTransaction`=runInSession+REQUIRED；`runLocal`=runInNewSession+REQUIRES_NEW） | 同 context 并行分支可达 |
| 9 | `DaoEntityBlockingSource.drainTo` | `@Transactional(REQUIRES_NEW)`（nop-orm-data:161） | 消费线程各自独立 context/事务 | 与 C2-013 族无交集（REQUIRES_NEW 隔离），其自身面已立案 C3-006/C6-001 |
| 10 | `DataInitInitializer` | nop-orm `:122` | 启动期单线程 | 安全 |
| 11 | `executeBlocking` / `context.execute` 直调 | 5 源码根 src/main grep | **零命中**（平台层 API，业务层可达但持久层不自调） | 持久层的并发前提（同 context 并行）全部经上层 #1-#8 进入 |

**失效面扩展结论**：C2-013/014/015/017 的并发触发面不依赖假想误用——`@Transactional` 异步方法（#2/#3）、异步 RPC 链与**每条 auto-commit SQL 的 SUPPORTS 包装**（#5）都是平台常规路径；其中 #5 使「同一 context 内 REQUIRED 分支打开事务期间，另一分支的并行查询穿过同一 registry/事务对象」成为结构性常态。第 1 轮 P1 判级的可达性前提经本轮全调用面核实**成立且被放大**。

## 深挖 2：「补锁三约束」死锁环反证（C2-016 修复设计的必答项）

对第 1 轮锁序图提出的三条补锁硬约束，逐条给出反证（违反即成环的具体链路）与「当前树内无违反」的核实：

**约束 (1) listener 回调必须锁外执行（锁内快照、锁外回调）**
- 反证环：若修复引入事务监视器 M(T) 且 `invokeListener` 在持 M(T) 期间执行——监听器代码是公开扩展面（`ITransactionTemplate.afterCommit/beforeCommit` 公共 API，第 1 轮 C2-014 已核实），业务 listener 完全可以「afterCommit 内发消息/开新事务」（经 #2/#4 入口再次进入 `runInTransaction` → `registerTransaction` → 若需获取另一事务 M(T')）。T1 持 M(T) 等待 M(T')（listener 内同步事务），T2 持 M(T') 在 commit 中遍历等待获取 M(T)（其 listener 注册于 T）→ 环。
- 树内核实：现树 listener 均轻量（metrics 计数、`removeAsync` 异步失效），当前无锁故无违反；补锁时必须维持 `invokeListener`（`AbstractTransaction.java:374-387`）的快照+锁外形态。**约束成立。**

**约束 (2) 组事务 parent→sub 单向取锁**
- 树内核实：全部 parent→sub 出边——`openSubTransactions`(:445-451)、`commitSubTransactions`(:365-372)、`commitSubTransactionsAsync`(:351-363)、`rollbackSubTransactions`(:194-210)、`closeSubTransactions`(:437-443)；**sub→parent 反向边在树内不存在**（`GroupTransaction` doOpen/doCommit/doRollback 全空实现；sub 事务的任何方法不回调 parent）。
- 反证环：若补锁后允许 sub 持 M(S) 时调用 parent 的 `markRollbackOnly`（「向父传播 rollback-only」是修复轮很容易顺手加的『改进』）——T1 parent commit 持 M(P) → `commitSubTransactions` → `subTxn.commit()` 等 M(S)；T2 sub 路径持 M(S) → `parent.markRollbackOnly` 等 M(P) → 环。**约束必须作为修复评审的显式检查项，而非默认。**

**约束 (3) 连接获取（池等待）不得与全局锁同临界区**
- 反证环：池等待秒级（Hikari connectionTimeout 默认 30s）。若修复把 `getConnection` 放入 registry 级/CHM bin 级全局锁——池耗尽时所有等该锁的线程被一个池等待串行挂起，且与「持连接等锁」的线程互等即成环。
- 树内核实：当前 `JdbcDialectProvider.getDialectFromTxn` 的 SUPPORTS 事务+池获取在 `get-then-put` **锁外**执行（核实 ：52-58：`get(querySpace)==null` 判定在 CHM 原子方法之外）——树内唯一把事务/池等待拉进锁内的是 `SqlExprMetaCache.computeIfAbsent` 备选装配链（已立案 C2-012，含 P2 升级条件），与本约束互证。**约束成立；修复时事务监视器内允许池等待（per-txn 锁、池内部锁为叶子），但禁止再向 registry/CHM/meta-cache 锁方向取锁。**

## 深挖 3：C2-001~003 F3 收口——session 并发触达入口全枚举

以 `OrmTemplateImpl` 为根的全量入口清单（grep + 逐行核对，5 源码根内）：

| 类别 | 入口 | 触达方式 |
|---|---|---|
| 注册表直读 | `currentSession`:176 / `requireSession`:181 / `flushSession`:189 / `clearSession`:196 | 取槽内 session 直接操作（无任何互斥） |
| 会话模板 | `runInSession`:204（含 Runnable 重载 :283）/ `runInNewSession`:214 / `runInSessionAsync`:234 / `runInNewSessionAsync`:244 / `registerSession`:278 / `unregisterSession`:273 | put/get/restore 单槽复合操作（C2-003 主现场） |
| 便捷透传 | `OrmTemplateImpl` 全部 get/load/save/update/delete/findAll 族（:291 起，约 30+ 方法，全部 runInSession 包装） | 同 context 内与上述模板共用同一槽/session |
| DAO 层 | `OrmEntityDao`×21 处 runInSession（:292-:719）；`AbstractDaoHandler.runInTransaction/runLocal`（:93-106） | 经 orm() 模板进入，承继全部面 |
| AOP 层 | `SingleSessionMethodInterceptor`（async 分支→runInSessionAsync）、`SingleSessionServiceInterceptor.interceptAsync`、`SingleSessionFunctionInvoker.invokeAsync`（:35-39） | **异步 biz 主通道**：@SingleSession 异步方法 → runInNewSessionAsync 承继 C2-002 的跨线程收尾 |
| 数据源层 | `DaoEntityBlockingSource.tryClaim`（nop-orm-data:260，runInSession） | 消费线程独立 context，主要承载 C3-006/C2-005 面 |
| 启动期 | `DataInitInitializer`:72 | 单线程，安全 |
| 跨根引用 | `nop-orm-rpc/RpcEntityPersistDriver`×7 处 runInSession | 范围外（5 根之外），登记交叉引用 |
| 隐式重入通道 | 全部 `syncGet` 等待点（`SessionBatchActionQueue:46`、`OrmBatchLoadQueueImpl:594`、`OrmSessionImpl:874`、`IBatchActionQueue:43`） | CL-401 已登记：等待期排空 context 队列 = 任意同 context 业务重入 session |

**收口结论**：session 并发触达入口集合已闭合——除启动期一处外，全部入口在「同上下文并行」（executeBlocking(ordered=false)/异步 biz/异步完成线程收尾）下可达，无遗漏类别；C2-001/002/003 的修复（owner-thread 守卫或会话级互斥）在 `OrmSessionImpl` 一层落点即可覆盖全部入口，无需逐入口修补。

---

## 发现条目（本轮新增）

### [nop-persistence-rw-C2-021] REQUIRED 加入路径的双读非原子：openExistingTransaction 外层检查与 AbstractTransaction.open 内层守卫读同一非 volatile 字段，迟到加入者收到伪 ERR_TXN_ALREADY_STARTED（合法加入被拒）

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/TransactionTemplateImpl.java:311-324（openExistingTransaction）`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/AbstractTransaction.java:389-399（open）`
- **证据片段**:
  ```java
  // TransactionTemplateImpl.openExistingTransaction（REQUIRED 分支 :270-273 调用）
  } else if (!state.txn.isTransactionOpened()) {   // 外层检查：读 opened（非 volatile）
      state.txn.open();                            // → AbstractTransaction.open
      state.newlyOpen = true;
  }
  // AbstractTransaction.open
  if (opened)                                      // 内层守卫：第二次读同一字段
      throw newError(ERR_TXN_ALREADY_STARTED);     // :393-394 加入者收到伪失败
  invokeOpenListener(); doOpen(); openSubTransactions();
  opened = true;                                   // :398
  ```
- **严重程度**: P3
- **并发场景**: 同一 IContext 的两个并行分支先后进入 `@Transactional(REQUIRED)`（第 1 轮深挖 1 的 #2/#5 路径）：T1 在 `getTxnState` 新建并注册事务对象（`registerTransaction`，**此时尚未 open**——open 在 `openExistingTransaction` 中才发生）；T2 随后经 `getRegisteredTransaction` 拿到**同一个事务对象**（合法加入者，语义上应与 T1 共用事务）。
- **触发时序**: (a) **伪拒绝**：T1 的 `opened=true` 写（:398）对 T2 无 happens-before（事务对象无任何同步，C1-025 已立案可见性面）——T2 外层检查读到 stale `false` 通过 → 进入 `open()` → 内层守卫此刻读到 fresh `true`（时间窗内可见性偶然成立）→ 抛 `ERR_TXN_ALREADY_STARTED` → **本应加入事务的分支以异常失败**；请求级后果=偶发伪失败，上游重试即成功，无数据损坏。(b) **双开**：T2 内层守卫同样读到 stale `false` → 两次 `doOpen()`（双池连接）——该分支即第 1 轮 C2-016(a) 已立案面（其失效面枚举已注明经 C2-013 注册覆盖后可达），本条不重复计数，仅补全「加入路径」这一具体到达链。
- **现状**: 外层检查与内层守卫是对**同一个非同步布尔字段的两次独立读**，两次读之间没有任何原子性约束；平台 REQUIRED 语义（已开即加入）依赖这两读一致，而 JMM 与调度都不提供该保证。
- **风险**: 并行分支下合法加入者偶发伪失败（错误码 ERR_TXN_ALREADY_STARTED 指向「重复开启」，与真实根因——无同步的加入协议——完全无关，排障误导性强）；发生率随同 context 并行度线性上升（GraphQL 并行 datafetcher/异步 biz 扇出）。判 P3：后果为伪失败（可用性）而非数据错误，且 T1 正常路径不受影响。
- **建议**: 纳入 C2-016 的 CAS 状态机方案一并修复：`open` 改为 `AtomicBoolean.opened.compareAndSet(false, true)` 成功者执行 doOpen，失败者**不抛错而是视为加入成功**（REQUIRED 语义下 open 竞争失败=加入，MANDATORY/显式 open 场景才需要区分报错——可在 `openExistingTransaction` 传参区分）；或将「注册+打开」收敛为 registry `compute` 原子段（与 C2-013 建议(1) 同一刀切）。修复后本条与 C2-016(a) 同时闭合。
- **失效面枚举（F3）**: 「外层检查+内层守卫」双读结构的全部站点逐一核对——(a) `openExistingTransaction` 主分支 `state.txn.open()`（:321-323，本条主案）；(b) `openExistingTransaction` 组分支 `state.groupTxn.open()`（:315-318，组事务开启与另一分支加入 `addSubTransaction` 竞争，双读同构，随同一修复覆盖）；(c) `createNewTransaction` 内 `txn.open()`（:343-347，REQUIRES_NEW 每次新建新对象，无双读窗口，安全）；(d) `MANDATORY` 分支（:279-283，`getTxnState(true)` 后仅读 `isOpened()` 判定不调 open，无伪拒绝面，安全）；(e) `AbstractTransaction.open` 内层守卫本体（:393，已在 C2-016 立案，本条为其到达链补全）。逐一状态：双读窗口仅 (a)(b) 两处，同一修复点覆盖。
- **信心水平**: 确定（双读结构与两条交错均可由代码直读推出；后果为伪失败，无需硬件假设）
- **误报排除**: 不属于 C2-016 重复计数——该条立案「双开后双连接」的事务语义面，本条立案「合法加入被伪拒绝」的可用性面，二者是同一非原子守卫的两个相反后果，修复方案共用但后果与验收面不同；不属于 C2-013 重复——那是注册表 put 覆盖层，本条前提恰是注册**成功**之后的加入窗口；不按 P2 报——伪失败可由上游重试吸收、无持久化影响，按校准落 P3。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**。openExistingTransaction 外层检查与 AbstractTransaction.open 内层守卫双读同一非 volatile 字段逐行亲验成立（TransactionTemplateImpl.java:311-324 + AbstractTransaction.java:389-399）；伪拒绝/双开两分支与 F3 五点枚举成立；纳入 C2-016 CAS 修复确认

---

## 跨批次同族裁定（第 3 组，按指令⑤）

### 同族组 (3)：缓存 invalidation-vs-repopulate —— **裁定：双维度重复立案 1 组（合并）+ 同族相邻 2 处（保留）**

- **重复立案合并**：C1-008(c)（reloadModel 不清 queryPlanCache，跨代陈旧计划继续执行）与 C2-008（同一位点、同一根因，C2 视角立案）经本轮交叉核对确认为**同一位点同一缺陷**——盲审轮 C1/C2 两维度各自独立命中（锚点同为 `SessionFactoryImpl.reloadModel`→`ormModelHolder.clearCache()` 未触达 `env.getQueryPlanCache()`）。**裁定：合并为一个缺陷、一个修复项**（`DefaultOrmModelProvider/TenantAwareOrmModelProvider.clearCache` 内聚 `queryPlanCache.clear()` + 计划携带模型代版本校验，按 C2-008 建议执行）；台账保留双锚点（C1-008/C2-008 互引），remediation 计数不重复。注意区分：CL-410（TenantCachedQueryPlan 多租户条目互相踢出、重编永不收敛）是**同缓存上的相邻独立缺陷**（机制=整实例覆盖 put，非失效缺失），独立修复项，不随本合并吞并。
- **同族相邻保留**：C1-027（JDBC 查询结果缓存「miss→慢查询→put」与 `clearQueryCacheFor/evictQueryCache` 竞赛）与 C2-004（全局实体缓存 read-repair 迟到 put 覆盖并发 evict）——**同族相邻而非同一缺陷**：缓存载体不同（命名查询缓存 vs per-entity 全局实体缓存）、写者读者不同、失效触发方不同，但竞态形状相同（失效与回填无顺序围栏）。修复归并建议：统一采用「失效世代号/版本围栏」平台口径（clear 递增 epoch，回填携带 epoch、put 前校验），两个 work item 共享同一设计，不合并执行。

## 终止判定

- **本轮新增**: 1 条（P0×0 / P1×0 / P2×0 / P3×1：C2-021）。
- **终止信号**: (a) 事务 API 调用面枚举闭合（11 类入口，并发可达性逐点判定，无未分类入口）；(b) 三约束反证完成且树内无违反（唯一违反形态即 C2-012 已立案）；(c) session 触达入口集合闭合（9 类，全部归并到同一修复落点）；(d) 新增发现仅 P3 且其修复点与既有 P1/P2 方案共用（C2-016 CAS 状态机覆盖）。
- **判定**: C2 维度第 2 轮**终止**，不建议第 3 轮。修复轮入口：C2-013/014/015/016/017/021 按「注册协议原子化 + CAS 状态机 + 单锁连接生命周期」三切片执行，遵守三约束。
