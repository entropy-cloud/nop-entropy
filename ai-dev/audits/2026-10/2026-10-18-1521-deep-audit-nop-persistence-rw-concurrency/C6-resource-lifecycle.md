# C6 资源生命周期与泄漏初审报告 — nop-persistence 读写热路径（合并批 1/1）

- **仓库**: /Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- **审计对象**: nop-persistence/nop-orm、nop-orm-eql、nop-orm-model、nop-dao、nop-orm-data（5 源码根，509 files / 84,484 lines，src/main，excl `_gen/`）
- **维度定义（C6）**: 验证一切分配的资源在所有路径（成功/失败/取消/中断/超时）上被正确释放：连接/事务/ResultSet/Statement 生命周期、session close 配对、监听器注册泄漏、缓存条目清理。
- **盲审纪律声明**: 本轮为盲审轮。未读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下任何文件；仅审计当前 live code。任务提及的 CL-500/501、C2-014、C3-001 等既有立案在盲审轮无法查阅原文，本报告仅以 live code 独立判定并在对应小节注明"盲审轮维持/深化"。
- **判级校准**: 沿审计 prompt 校准。每条资源发现给出**泄漏窗口触发时序**；配对完整的站点以配对表维持，不立项。
- **基线**: `_tmp/cra-baseline-nop-persistence-rw.txt` C6 两节（resource acquisition 36 站点 + closeable 8 接口）逐一判定。

## 0. 资源获取点配对表（基线站点逐一判定）

| # | 获取点 | 释放路径 | 异常路径 | 判定 |
|---|--------|---------|---------|------|
| P1 | `JdbcTemplateImpl.executeUpdate` PreparedStatement（`:268`） | finally `IoHelper.safeCloseObject(st)`（`:285`） | 同一 finally；SQLException 先 translate 再抛，st 仍关 | **配对完整** |
| P2 | `JdbcTemplateImpl.executeQuery` ResultSet+Statement（`:359-363`） | finally 关 rs、st（`:378-379`） | 同左；回调异常亦覆盖 | **配对完整** |
| P3 | `JdbcTemplateImpl.executeStatement`（`:306-309`） | finally 关 rs（恒 null，占位）、st（`:331-332`，关闭 st 连带关其 ResultSet） | 同左；cancelToken 面见 C6-002 | **配对完整（cancel 注册面除外）** |
| P4 | `JdbcTemplateImpl.callFunc` CallableStatement（`:809`） | finally `safeClose(st)`（`:827`） | 同左 | **配对完整** |
| P5 | `JdbcTemplateImpl.getDbCurrentTimestamp` Statement+ResultSet（`:842-843`） | finally 关 rs、st（`:849-850`） | 同左 | **配对完整** |
| P6 | 元数据探测 ResultSet（getIndexInfo/getImportedKeys/getColumns/getTables，`:689,725,763,770,782`） | try-with-resources | 同左 | **配对完整** |
| P7 | `JdbcHelper.prepareStatement` 两个重载（`:184-221`） | setFetchSize/setParameters 失败 catch 内 `safeClose(ps)` 后 rethrow | 覆盖 prepare 成功但参数绑定失败窗口 | **配对完整** |
| P8 | `JdbcHelper.prepareCallableStatement`（`:168-180`） | registerOutParameter 失败 catch 内 `safeCloseObject(st)` | 同左 | **配对完整** |
| P9 | `JdbcDataSetHelper.newDataSet`（`:36-49`） | 成功路径返回 `JdbcDataSet(dialect, rs, st)`，close 时连带关 statement（注释明示调用方无单独关闭职责）；失败 catch 关 st | 覆盖 | **配对完整** |
| P10 | `JdbcBatcher` PreparedStatement（`:171,307`） | 两处 finally `safeClose(ps)`（`:257,328`）；BatchUpdateException/SQLException/外层 setAutoCommit 失败均 `failRemainingCommands` 保证回调恰一次 | 覆盖（含回调自身抛异常的二次兜底 `:237-241`） | **配对完整** |
| P11 | `JdbcTransaction` Connection（懒取 `:61-75`） | commit/rollback 后 eagerRelease → `releaseConnection`（`safeClose`+置 null）；`doClose→releaseConnection`（`:113-125`）；eagerRelease=false 时 restoreAutoCommit 后由 close 释放 | rollback 无连接时不动作（`connection==null` 早退） | **配对完整** |
| P12 | `JdbcTransactionFactory.openConnection`（独立池连接，`:72-74`） | 文档契约：不参与事务、由调用方关闭（plan 2306 裁定 34） | 契约声明 | **已声明边界，维持** |
| P13 | `DialectManager.getDialectForDataSource` 连接（`:81`） | finally `safeCloseObject(conn)` | 覆盖 | **配对完整** |
| P14 | `ReadWriteRoutingDataSource` replica 连接（`:142-143`） | 返回给调用方，随事务 close 释放（与 P11 同路径）；获取失败立即换下一台，失败连接未借出 | 覆盖 | **配对完整** |
| P15 | `SimpleDataSource.getConnection`（DriverManager 直连，`:54`） | catalog/schema 设置失败 catch 内 `safeCloseObject(con)` | 覆盖 | **配对完整** |
| P16 | `JdbcDialectProvider.getDialectFromTxn` SUPPORTS 事务连接（`:63-71`） | runInTransaction SUPPORTS 创建的事务由 `TransactionTemplateImpl.cleanupTransaction` close（`newlyCreated→close`，`:431-434`） | 覆盖 | **配对完整** |
| P17 | `OrmSessionImpl` 会话（`sessionFactory.openSession`） | `OrmTemplateImpl.runInNewSession` finally 恢复 registry+close（`:222-230`）；async 路径 whenComplete close（`:255-269`）；close 幂等（`closed` 早退）、清 cache/sessionCache/onClose tasks | 见下文"同步 close 配对"节 | **配对完整** |
| P18 | `LoadedOrmModel` 引用计数 | 构造 `incRef`（`OrmSessionImpl.java:128`）/close `decRef`；`needClose+refCount==0 → doClose` 关全部 persister（`LoadedOrmModel.java:156-184`） | decRef 到 0 且 needClose 才关；session 未关则延迟 | **配对完整**（clearCache 替换面见 C6-003） |
| P19 | `OrmSessionFactoryBean` GlobalCacheRegistry 注册 | init 注册（`:135-138`）/destroy 注销（`:193-195`）；`DefaultOrmModelProvider` interceptorCache 同对称（构造注册/`close` 注销） | 覆盖 | **配对完整** |
| P20 | 事务监听器（`AbstractTransaction.listeners`） | `close→invokeCloseListener→clearListeners`（`:423-435`）；模板路径 cleanupTransaction 保证新建事务必 close | 事务生命周期内只增（增长面归 C9-001） | **生命周期有界，无跨事务泄漏** |
| P21 | `QuerySpaceEnv` ThreadLocal | `runWithQuerySpace` finally `leave(old)`，old==null 时 remove（池线程安全） | 覆盖 | **配对完整** |

**总账结论**: 基线 C6 两节全部 36 个 acquisition 站点中，35 个配对完整或为已声明边界；唯一不对称注册点是 `executeStatement` 的 cancelToken 挂接（C6-002）；唯一无界**持有**（非泄漏）窗口在 nop-orm-data 的阻塞源（C6-001）。

---

## 发现条目

### [nop-persistence-rw-C6-001] DaoEntityBlockingSource.take() 无限等待路径在 REQUIRES_NEW 事务内轮询：连接、事务与抢占行锁被无限期持有

- **文件**: `nop-persistence/nop-orm-data/src/main/java/io/nop/orm/data/source/DaoEntityBlockingSource.java:122-159,172-214`
- **证据片段**:
  ```java
  // take/takeMulti 以 maxWait=-1 进入 drainTo —— 无限等待
  public T take() throws InterruptedException {
      List<T> items = new ArrayList<>(1);
      takeMulti(items, 1);            // → drainTo(items, 1, -1, -1)
      ...
  }
  @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
  public int drainTo(Collection<? super T> c, int maxElements, long minWait, long maxWait) {
      long timeout = maxWait > 0 ? maxWait : Long.MAX_VALUE / 2;   // take: 实际无限
      ...
      boolean found = FutureHelper.waitUntil(() -> {
          List<T> items = loadItems(dao, maxElements - (c.size() - oldSize)); // 轮询 SELECT
          ...
      }, timeout, interval);                                        // interval 缺省 100ms
  ```
- **严重程度**: P1
- **并发场景**: 多个消费者线程（IBlockingSource 语义）+ 生产方写入；K 个阻塞消费者 × 每人 1 个事务连接。
- **触发时序（泄漏窗口）**: T0 消费者调用 `take()` → `@Transactional(REQUIRES_NEW)` 拦截器 `createNewTransaction→txn.open()→doOpen→getConnection0()` —— **事务打开即刻从池取走连接并 setAutoCommit(false)**（`JdbcTransaction.doOpen:128-134`）→ `waitUntil` 每 100ms 执行一次 SELECT（空队列持续无产出）→ 事务永不提交/回滚，连接、事务快照、以及上一轮 `tryClaim` 成功行上的 **UPDATE 行锁**全部保持，直到有数据到达或线程被中断。若生产方停止/慢于消费，K 个消费者各自钉死 1 个池连接：池上限 20、消费者 20 个先于生产者启动 → **全应用 DB 访问饥饿**；PostgreSQL 类库上长事务快照还会阻断 vacuum。
- **现状**: 连接最终会随事务结束归还（非永久泄漏），但**持有窗口无上界**且出现在"队列空"这一**正常稳态**，非异常路径；`drainTo` 带有限 maxWait 时窗口=maxWait，`take/takeMulti`（maxWait=-1）窗口无限。
- **风险**: 负载上升/消费先于生产启动时必然触发的连接池耗尽（校准条款 P1"负载上升必然触发的……资源耗尽"）；长事务快照Retention；抢占行锁跨轮询周期持有削弱 `tryClaim` 的抢占语义窗口。
- **建议**: (1) `take`/无限等待路径去掉方法级 REQUIRES_NEW——把事务边界下沉到**单次轮询**内部（每轮 loadItems+tryClaim 一个短事务，数据到达后合并返回），空闲时零持有；(2) 或在 `@Transactional` 外用 `runWithoutTransaction` 包裹 waitUntil 循环、循环体内逐轮 `runInTransaction`；(3) 为阻塞源补充"最大空闲持连时长"守卫指标（复用平台 `IPoolMetrics`）。
- **失效面枚举（F3）**: ① `take():122-126` → `takeMulti:157-159`（maxWait=-1）—— 本体，窗口无限；② `poll():129-136`（timeout=0，走 maxWait==0 分支直接查询，`:180-184`）—— 事务纳秒级，**安全**；③ `poll(timeout,unit):139-144` —— 窗口=maxWait，**有界**（长 maxWait 时同病，随参数归入①族）；④ `drainTo(c,maxElements):147-154`（0,0）—— **安全**；⑤ 同一 `@Transactional(REQUIRES_NEW)+轮询` 形态在本 5 根内无其他站点（nop-batch/nop-job 不在本轮范围，登记待跨模块轮复核）。
- **信心水平**: 确定（代码链路）；`@Transactional` 生效依赖该 bean 经平台事务拦截器装配（beans.xml 在部署层，源码根内未见否定证据）——若拦截器未装配则 downgrade 为 P3（无事务化，行为改变）。
- **误报排除**: 非池化基础设施自身问题（JdbcTransaction 生命周期由模板正确 close——P11 配对完整）；缺陷在使用方把**无限等待循环**放进了**事务边界**，属使用方式错误，正是本维度审查对象。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。REQUIRES_NEW + waitUntil + txn.open 即取连接（JdbcTransaction.doOpen→getConnection0 亲验）成立；与 C3-006 同位点合并工单终裁**确认**（同 C3-006）；K≥8 池饥饿阈值亲验（maxSize 默认 8）

---

### [nop-persistence-rw-C6-002] executeStatement 向 ICancelToken 注册 ds::cancel 后永不移除（F8 注册/反注册不对称）

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:306-312`；`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/ICancelToken.java:25-32`
- **证据片段**:
  ```java
  JdbcComplexDataSet ds = new JdbcComplexDataSet(st, dialect);
  if (cancelToken != null) {
      cancelToken.appendOnCancelTask(ds::cancel);   // 注册后无任何 removeOnCancel 配对
  }
  ds.execute();
  ...
  } finally {
      IoHelper.safeCloseObject(rs);   // rs 恒为 null
      IoHelper.safeCloseObject(st);   // statement 已关 —— JDBC 句柄无泄漏
  ```
- **严重程度**: P3
- **并发场景**: 单请求执行线程注册；取消动作由取消发起线程（如超时看门狗/用户断连处理）在 token 上触发。
- **触发时序（泄漏窗口）**: T1 请求处理中执行第 i 条 `executeStatement` → token 追加 `ds_i::cancel`（强引用 ds_i→PreparedStatement_i→Connection 引用链）→ 语句执行完，finally 关闭 st（**JDBC 句柄已释放**）→ token 不清理任务 → 第 2..N 条语句逐条累加 → token 存活至请求结束，期间 N 个已关闭的 `JdbcComplexDataSet+PreparedStatement` 对象图不可回收。若 token 在请求后被取消（迟到的 cancel），对已关闭 statement 调 `statement.cancel()` 抛 SQLException → `translate` 为 NopException（`JdbcComplexDataSet.cancel:86-90`），其影响取决于 token 聚合器对单任务异常的隔离（实现不在本 5 根）。
- **现状**: JDBC 句柄无泄漏（P3 站点配对完整）；泄漏面是**内存驻留**（N×已关闭语句对象图，单请求内 N=执行语句数，通常个位到百位）+ 过期回调风险。`ICancelToken.removeOnCancel` 接口存在但全仓本调用点未用。
- **风险**: 常规请求量级可忽略；长生命周期 token（流式/订阅场景跨请求复用）下按语句数单调增长，量级 = 语句数 × (语句对象 ~200B + 参数数组)，10³ 条语句 ≈ 数百 KB——有界 by token 生命周期，不构成 P2。
- **建议**: finally 中配对移除：注册时构造 `Consumer<String> task = r -> ds.cancel()`，finally 调 `cancelToken.removeOnCancel(task)`；或改用 `token.isCancelled()` 在执行后短路。
- **失效面枚举（F3）**: ① `JdbcTemplateImpl.executeStatement:310-312` —— 全 5 根唯一 cancel-token 注册点（grep `appendOnCancelTask` 仅此一处 + 接口 default）；② `OrmSessionImpl.executeStatement:1458-1463` → `JdbcQueryExecutor.executeStatementSql:137-152` —— 透传同一 token，同族（修复须覆盖透传链）；③ `JdbcComplexDataSet.cancel:86-90` —— 被注册动作本体，已关闭后调用抛错面；④ `ICancelToken.appendOnCancel/removeOnCancel` 接口对 —— 平台设施配对能力齐备，本站点未用。
- **信心水平**: 确定（不对称存在）；"过期 cancel 抛错破坏其他取消任务"为有趣猜测（依赖 token 聚合实现）。
- **误报排除**: 非"池化对象借还"或"主路径成功即泄漏"（finally 已关句柄）；立项点是可指认的注册/反注册不对称（F8 家族画像定向命中）。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**

---

### [nop-persistence-rw-C6-003] DefaultOrmModelProvider.clearCache 旧 LoadedOrmModel 未经 close 释放——现状无泄漏的防御性登记

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java:41-47`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:156-184`
- **证据片段**:
  ```java
  @Override
  public void clearCache() {
      OrmModel ormModel = new OrmModelLoader().loadOrmModel(false);
      this.ormModel = new LoadedOrmModel(env, ormModel);  // 旧 LoadedOrmModel 直接被替换
      this.interceptorCache.clear();                       // 未调用旧实例 close()
  }
  ```
- **严重程度**: P3
- **并发场景**: 管理操作线程触发 `reloadModel`（`SessionFactoryImpl.reloadModel:460-463`）与在途请求线程持有旧 model 引用（session 构造时 `incRef`）并发。
- **触发时序（窗口推演）**: T1 请求打开 session → 旧 model refCount=1 → T2 管理端 `clearCache()` 替换字段，旧 model **未 close**（`needClose=false`）→ T1 请求结束 `decRef→0`，因 `needClose==false` 不触发 `doClose` → 旧 model 及其全部 persister/driver 无关闭钩子执行，依赖 GC 回收。**现状判定为安全**：`IEntityPersistDriver`/`ICollectionPersistDriver` 非 AutoCloseable，`EntityPersisterImpl.close → IoHelper.safeClose(driver)` 对非 Closeable 为 no-op（`EntityPersisterImpl.java:83-86`），driver 仅持有模型/方言/绑定器等无稀缺资源对象——旧图整体可达性终止后即可回收，无句柄/连接泄漏。
- **现状**: 热更新路径的关闭钩子**从不执行**（与 `close()` 路径的 `doClose` 语义不一致）；这是"当前无资源、未来会咬人"的结构性不对称。
- **风险**: 若未来 persist driver 增加持有资源（语句缓存池、文件句柄、外部连接），`reloadModel` 每次热更新泄漏一个模型代的资源；且无编译期守卫提示。
- **建议**: `clearCache` 中对旧实例先置 `close()`（其 `refCount>0` 时本就延迟到 doClose，语义安全），或注释明确"IEntityPersistDriver 必须保持无资源"约束并加接口断言测试。
- **失效面枚举（F3）**: ① `DefaultOrmModelProvider.clearCache:43-47` —— 本体；② `TenantAwareOrmModelProvider.clearCache:44-47` —— 同族：同样直接替换 `this.ormModel` 不 close 旧实例；但其 `tenantCaches` 配置了 `destroyOnRemove()`（`:24-27`），驱逐路径有关闭钩子，仅 null/替换路径同病；③ `OrmSessionFactoryBean.clearCache:165-179` —— 清缓存而非换模型，走 `holder.clearCache()` 即 ①；④ `LoadedOrmModel.close/decRef:156-174` —— 正常路径配对完整。
- **信心水平**: 确定（现状无泄漏）；风险为结构性预判。
- **误报排除**: 非已发生的泄漏（无稀缺资源被占）；立项为 F8"清理不对称"的防御性低危形态，与 CL-604 同一事实，台账合并时择一保留即可。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**；CL-604（C5 侧同事实登记）并入本条，择一保留裁定为「C6-003 为主条目、CL-604 互引」，与本条自注「台账合并时择一保留即可」一致

---

## 同步路径 session close 配对（维持判定，深化 CL-500/501 口径）

> 盲审轮无法读取 CL-500/501 原文，以下为 live code 独立复核结论，供台账合并时对照。

1. **`OrmTemplateImpl.runInNewSession:214-231`**: `finally` 中 `registry.put(sessionFactory, oldSession)` 恢复先行，`s == session` 才 close——嵌套/替换场景下"非当前会话不关"的守卫完整；`session.flush()` 位于 try 内，flush 抛错仍会走 finally 关闭。**同步路径配对完整**（异步不关闭面为 C3-001 已立案族，本轮不重复）。
2. **`OrmTemplateImpl.runInNewSessionAsync:244-270`**: `ContextProvider.thenOnContext(future).whenComplete` 中先条件 flush（err==null）再恢复 registry+close；callback 同步抛错经 `FutureHelper.reject` 进入同一完成链。取消（future cancelled）亦触发 whenComplete。**配对完整**。
3. **`OrmSessionImpl.close:768-791`**: `closed` 标志幂等；`loadedOrmModel.decRef()`；`cache.clear()`（触发全部实体 `orm_detach`）+ `sessionCache.clear()`；`onClose` 任务逐个 try/catch（单任务失败记日志不阻断其余）。**关闭副作用完备**。
4. **registry 泄漏面**: `runInNewSession` 的守卫意味着若回调内部调用 `unregisterSession()` 后未自行 close，session 将不关（代码注释明示），并留下 `LOG.info("nop.orm.skip-session-close-when-not-current-session")`——该日志是唯一的逃逸可观测点。grep 确认 5 根内框架自身无此调用模式，逃逸面=业务显式 `unregisterSession` 误用，登记为使用契约（不立项）。

## 监听器注册面汇总（与 C2-014 互引，泄漏维度判定）

| 注册点 | add | remove | 泄漏判定 |
|--------|-----|--------|---------|
| `SessionFactoryImpl.addInterceptor:249-254` / `removeInterceptor:257-261` | copy-on-write 替换 | 支持 | 对称 |
| `SessionFactoryImpl.addDaoListener:273-278` / `removeDaoListener:287-291` | `MultiOrmDaoListener`（COW 列表） | 支持 | 对称 |
| `MultiOrmInterceptor.add/removeInterceptor` | ArrayList | 支持 | 对称（非线程安全集合，变更须启动期——与 C2-014 惰性初始化面互引，泄漏面不成立） |
| `AbstractTransaction.addListener/removeListener` | COW+排序 | 支持；close 时 `clearListeners` 兜底 | 生命周期有界；增长速率面归 C9-001 |
| `EntityPersisterImpl.evictGlobalCache:560` / `JdbcEntityPersistDriver.lock:173` 匿名监听器 | 每 DML/lock 一个 | 依赖事务 close 清理 | **事务内有界、跨事务无泄漏**（C9-001 承接其开销面） |
| `GlobalCacheRegistry`（sessionFactory cacheManagement / interceptorCache） | init/构造 | destroy/close 注销 | 对称 |
| `ICancelToken.appendOnCancelTask` | executeStatement | **无配对 remove** | **C6-002 立项** |

## CL 线索（CL-600 段）

- **CL-600**: DaoEntityBlockingSource 无限等待持事务/连接（C6-001 台账线索；跨模块复核 nop-batch/nop-job 同形态阻塞源）。
- **CL-601**: cancelToken 挂接 ds::cancel 不移除（C6-002；F8 家族台账，与 nop-graphql 侧 token 聚合实现联动复核）。
- **CL-604**: DefaultOrmModelProvider/TenantAwareOrmModelProvider 的 clearCache 旧模型不 close（C6-003；与 C5 报告同条目互引）。

---

**总计**: C6 发现 3 条（P1×1：C6-001；P3×2：C6-002、C6-003），配对表 21 站点判定（20 配对完整/已声明边界，1 不对称立项），维持判定 2 组（session close 同步配对、监听器注册面），CL 线索 3 条。**末位编号 C6-003**。C6-001 为唯一 P1，泄漏/持有窗口触发时序已给出：REQUIRES_NEW 事务在 `txn.open()` 即取连接，`take()` 的 waitUntil 无限轮询使持有窗口无上界。

## 维度复核结论（独立复核合并批，2026-10-18）

- **复核方式**: 与初审/深挖代理独立的第 2 轮合并复核批。本维度 3 条发现证据逐条亲验；P1×1 逐条子项复核；历史对照面已解除盲审。
- **裁定统计**: 3 条全部**保留**（降级 0、驳回 0）——P1×1 维持 / P3×2 维持。
- **C6-001 终裁（维持 P1）**: REQUIRES_NEW 事务打开即取连接（TransactionTemplateImpl.createNewTransaction→txn.open()→JdbcTransaction.doOpen→getConnection0 亲验）、waitUntil 无限等待（take/takeMulti maxWait=-1 → Long.MAX_VALUE/2 亲验）、行锁跨轮询持有三点全部行级确认；K≥8 池饥饿阈值亲验（DataSourceConfig.maxSize 默认 8 + HikariDataSourceFactory 直取）。正常稳态（队列空）即触发的持有窗口无上界，P1 成立。
- **合并裁定终裁**: **C6-001+C3-006 同位点单工单——确认**（同一方法、同一根因、双维度投影；修复重叠 >80%；事务边界内移 + 单 tick 超时单一 work item；台账双锚点保留、remediation 计数 1 项）。
- **与 C2-005 三面关系终裁**: C2-005（tryClaim CAS 谓词退化）为**独立缺陷**，不随合并工单吞并——但其 UPDATE 运行在 drainTo 事务内，「事务边界内移」实现必须保证 tryClaim 与 loadItems 的 SELECT/UPDATE 仍同一短事务（抢占原子性约束），该实现约束确认并写入合并工单验收项。
- **round2 抽验复核**: 5 站点失败路径抽验（session close 取消路径 / createNewTransaction 失败回滚 / cleanupTransaction 先释放后抛错 / eagerRelease 默认值 / 元数据探测）复核维持；21 站点配对表维持（唯一不对称点 C6-002 已立案）。
- **CL 处置（3 条确认）**: CL-600（跨模块同形态阻塞源复核线索）、CL-601（cancelToken 家族台账）、CL-604（并入 C6-003，择一保留裁定为 C6-003 为主条目）均确认，无驳回。

## 最终保留项

| ID | 初判 | 终裁 | 条目 |
|---|---|---|---|
| C6-001 | P1 | **P1**（与 C3-006 合并工单） | drainTo 无限等待连接/事务/行锁持有无上界 |
| C6-002 | P3 | P3 | executeStatement cancelToken 注册不反注册 |
| C6-003 | P3 | P3（CL-604 并入） | clearCache 旧模型不 close（防御性） |

**维度终局**: P1×1 / P3×2，无驳回、无撤案。
