# C9 请求热路径初审报告 — nop-persistence 读写热路径（合并批 1/1）

- **仓库**: /Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- **审计对象**: nop-persistence/nop-orm、nop-orm-eql、nop-orm-model、nop-dao、nop-orm-data（5 源码根，509 files / 84,484 lines，src/main，excl `_gen/`）
- **维度定义（C9）**: 识别并发热路径上可量化的性能缺陷：锁争用、分配放大、串行化瓶颈。默认 P2/P3；升 P1 仅限（a）每查询/每事务必经链上的全平台串行化点或（b）掩盖正确性问题。
- **盲审纪律声明**: 本轮为盲审轮。未读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下任何文件；仅审计当前 live code。C1-015（元模型竞态）、C2-014（listeners 惰性初始化）等既有立案仅互引，不重复判级。
- **量化纪律**: 每条发现给出「调用频率 × 单次代价」估算链；无量化依据不立项。基准缺口按 P3 登记并注明建议 JMH 形态。
- **基线**: `_tmp/cra-baseline-nop-persistence-rw.txt` C9 节（per-entity loop sites 计数 398——为粗 grep 计数，本报告按"每查询必经链 / 每实体循环站点 / 每行装配站点"三层人工抽验）。

## 0. 热路径清单与常数因子总账

### 每查询必经链（SELECT 一次执行的完整开销分解）

| 步骤 | 站点 | 单次代价（量级） | 判定 |
|------|------|----------------|------|
| 1. EQL 编译缓存命中 | `LoadedOrmModel.compileSql:106-128` → `ICache.get(QueryPlanCacheKey)` | 键 hashCode=O(\|sqlText\|)（`QueryPlanCacheKey.hashCode:66-71` 全文哈希，典型 100–2000 字符 ≈ 0.2–2μs）+ equals 一次全文比较（哈希命中后） | 必要开销，合理 |
| 2. 参数构建 | `CompiledSql.buildParams`（paramBuilders 逐个 build） | O(参数数)，~0.1–1μs | 必要 |
| 3. fanout 判定 | `ShardFanoutEngine.planQuery` ← `JdbcQueryExecutor.executeQuerySql:108` | 每查询遍历 SQL markers + 每个表 marker 一次 `requireEntityModel`（HashMap）+ `isUseShard/getMissingShardPolicy`；无分片实体时 ≈ 0.5–2μs | 必要（plan 2321 设计内）；非分片部署亦支付（常数小） |
| 4. SQL 变换 | `transformEQL:191-195` — **每查询 new GenSqlTransformer + 全 AST 变换重建 SQL 文本/markers** | O(AST 节点数)，中等 SQL（~20 节点）≈ 5–20μs；含 SQL.SqlBuilder/IMarkedString 分配 | 设计内（租户过滤/分片/逻辑删除按请求状态注入，无法整体预编译）；量级登记 C9-002 |
| 5. 只读标记 | `SQL.begin(sql).readOnly(true).end():116-118` | 一次 SQL 包装拷贝 ≈ 0.5μs | 合理 |
| 6. JDBC 入口 | `JdbcTemplateImpl.runWithConnection→withTxnForSql→withTxn(SUPPORTS)` | 无外层事务时：TxnState 分配 + `getTxnState(allowCreate)` 新建 `JdbcTransaction`（txnId 字符串 + 2 个 listener add：defaultListener/metricsListener 各触发 COW 数组创建）+ register/unregister（ContextRegistry 查找 ×2）+ close（clearListeners + releaseConnection）≈ **6–10 次分配 + 4 次并发容器操作 ≈ 1–3μs**；有外层事务时仅 registry 查找 | auto-commit 查询的固定税，登记 C9-002 |
| 7. 方言解析 | `getDialectForQuerySpace`（map 查找→DialectManager 缓存模型） | O(1) | 合理 |
| 8. 日志 | `JdbcHelper.SQL_LOG.info("nop.jdbc.run:..."):175` 等 5 站点 | INFO 开启时每 SQL 一行全文本格式化+IO ≈ 1–5μs + 磁盘；关闭时 slf4j 参数求值 ≈ <0.1μs | 配置依赖，登记 C9-004 |
| 9. 每行变换 | `TransformedDataSet` 包装 + `transformRow:197-214` | 每行 O(fields) 的 `readRow`（binder.getValue 逐列）+ `buildValue`；每行 1–2 个对象（BaseDataRow/SingleColumnRow） | 必要，无冗余 |

**每查询 ORM 常数因子合计 ≈ 8–30μs（无事务、INFO 关）/ 15–50μs（INFO 开）**，对比 DB 网络往返 0.5–5ms 占比 <1–5%，**不构成全平台串行化点，无 P1**。

### 每实体写路径（save/update/delete 单实体）

| 步骤 | 站点 | 单次代价 | 判定 |
|------|------|---------|------|
| 拦截器链 | `OrmSessionImpl.interceptPreSave:1128-1141` 等 7 个 intercept 方法 | O(拦截器数) | 合理 |
| 校验 | `EntityPersisterImpl.checkColumnValueWhenSave:381-406` | O(cols)，列数 5–50 | 必要 |
| 时间戳/加密助手 | `OrmTimestampHelper.instance():457`、`EncAssistedHelper.instance():458` | static 字段读 O(1) | 合理 |
| 动作入队 | `queueUpdate:473-493` | 动作对象+闭包+String 键装箱（C5-004） | C5/C9 交叉，已登记 |
| 全局缓存逐出 | `evictGlobalCache:553-569` | **事务 open 时每实体 new 匿名 listener + `addListener` 的 contains O(n)+add O(n) 拷贝+sort O(n log n)** | **C9-001，本报告唯一 P2** |
| 批执行 SQL 构建 | `JdbcEntityPersistDriver.buildUpdateSql:281-293` 单槽 `lastUpdateSql` | 命中 O(propIds 比较)；失效 O(SQL 生成 ≈2–10μs) | C9-003 |
| 批执行 | `JdbcBatcher`（batchSize 上限内 addBatch） | O(1)/动作 + 驱动批 | 合理 |

### 每行装配站点（抽验，全部为必要 O(row) 工作）

`OrmAssembly.readEntity/readRow`（O(cols)、无临时集合）、`JdbcQueryExecutor.transformRow`（O(fields)，精确分配一个结果数组）、`OrmSessionImpl.internalAssemble:368-430`（O(propIds)，含版本比对分支 O(1)）、`EntityPersisterImpl.batchLoadAsync→getToLoad:595-624`（O(N) + 全局缓存 `getAll` 一次批量）。**398 个基线循环站点抽验 12 个核心站点，未发现隐藏 O(n²) 或循环内重编译/反射查找**（`newEntity` 走启动期构建的 `IBeanConstructor`，`SessionFactoryImpl.getEntityConstructor:406-415`）。

---

## 发现条目

### [nop-persistence-rw-C9-001] 单事务逐实体注册事务监听器：AbstractTransaction.addListener 的 O(n²) 复合开销（COW 拷贝 × contains × 每加必 sort）

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/AbstractTransaction.java:80-88`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:553-569`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/jdbc/JdbcEntityPersistDriver.java:167-184`
- **证据片段**:
  ```java
  // AbstractTransaction.addListener — 每个 per-entity 匿名监听器都走全量检查+排序
  public void addListener(ITransactionListener listener) {
      if (listeners == null)
          listeners = new CopyOnWriteArrayList<>();
      if (!listeners.contains(listener)) {       // O(n) 线性扫描
          listeners.add(listener);               // COW：O(n) 数组拷贝
          if (listeners.size() > 1)
              listeners.sort(ITransactionListener::compareTo); // O(n log n) + 两次数组拷贝
      }
  }
  // EntityPersisterImpl.evictGlobalCache:560 — 事务 open 时每个实体 DML new 一个匿名监听器
  this.env.txn().addTransactionListener(querySpace, new ITransactionListener() {
      @Override public void onAfterCommit(ITransaction txn) {
          globalCache.removeAsync(getCacheKey(entity));
      }
  });
  ```
- **严重程度**: P2
- **并发场景**: 单事务写线程内的序列化 CPU 开销（非锁争用）；并发事务各自持有自己的 listener 列表，互不争用。注册线程与 commit/rollback 回调线程的并发安全已由 COW 保证（代码注释已裁定）。
- **触发时序**: 一次 flush 写 N 个实体（启用 useGlobalCache 的实体，或 lock 路径 N 次锁）→ 第 i 次 add：contains 扫 i-1 项 + COW 拷贝 i-1 项 + sort（i log i，COW sort 需 getArray/setArray 各一次全量拷贝）→ 总计 Σ ≈ N²/2 次元素拷贝 + N²/2 次比较 + N·(N log N)/2 排序操作，外加 N 个监听器分配及其捕获的 entity/shard 引用（延长实体可达性至事务提交，内存面见 C5-004 互引）。
- **量化链**: N=1,000（大业务事务）：~5×10⁵ 元素拷贝 + ~5×10⁶ 排序比较 ≈ **10–50ms 纯 CPU**；N=10,000（批量导入单事务）：~5×10⁷ 元素拷贝（累计 ~400MB–1.6GB 数组复制流量，Young GC 压力显著）+ ~6×10⁸ 比较 ≈ **1–10s**，相对批执行本身的 DB 时间（1 万行 batch ≈ 数百 ms）开销反超一个量级。频率口径：每事务一次 flush × 每实体一次 add。
- **现状**: 功能正确（监听器恰一次回调、close 清理——C6 配对表 P20）；代价随事务写规模二次增长。
- **建议**: (1) `evictGlobalCache` 改为**事务级单监听器**：per-txn 惰性注册一个 listener + `Set<String> pendingEvictKeys` 汇总（onAfterCommit 逐 key removeAsync），add 退化为 Set.add O(1)——与 C2-014 惰性初始化同点位，一次修复收敛两案；(2) 若保留逐实体注册，`AbstractTransaction.addListener` 应先判断 `listener` 实现是否 ordered，全同 order 时跳过 sort。
- **失效面枚举（F3）**: ① `EntityPersisterImpl.evictGlobalCache:553-569` —— save/update/delete 三路共用的注册点（最高频）；② `JdbcEntityPersistDriver.lock:173-178` —— 每次悲观锁一个匿名监听器（低频，同病）；③ `CollectionPersisterImpl.flushCollectionChange` 的集合动作回调 —— 走 BatchAction 队列不注册事务监听器，**安全**；④ `DefaultTransactionManager.newTransaction:200-205` —— 每事务固定 2 个 listener（default+metrics），常数，安全；⑤ 其他 `addListener` 调用点（`TransactionTemplateImpl.getTxnState` 路径）—— 每事务常数个，安全。
- **信心水平**: 确定（复杂度可从代码直接推导）；绝对耗时为量级估算。
- **误报排除**: 非"synchronized/COW 原语使用本身"被报——原语用法正确；报的是复合调用模式（per-entity 注册 × O(n) 结构性操作）在声明规模（批量写）下的可量化退化，符合 C9 立项口径；判 P2 未升 P1：写事务非"每查询必经"，且无正确性掩盖。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。addListener 的 O(n) contains + COW 拷贝 + 每加必 sort 亲验成立（AbstractTransaction.java:80-88）；与 C2-014 同点位修复归并确认（C2-014 降 P2 后两者同为 P2 同工单）

---

### [nop-persistence-rw-C9-002] 每查询常数因子登记：计划缓存命中路径的全文哈希 + 每查询 GenSqlTransformer 全 AST 变换 + auto-commit 路径事务对象churn（含 JMH 基准缺口）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java:89-124,191-195`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:106-128`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/QueryPlanCacheKey.java:66-79`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/TransactionTemplateImpl.java:125-167`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:160-213`
- **证据片段**:
  ```java
  // QueryPlanCacheKey.hashCode — 每次缓存查找对完整 SQL 文本重新哈希
  public int hashCode() {
      int h = name == null ? 0 : name.hashCode();
      h = sqlText.hashCode() * 31 + h;      // O(|sqlText|)
      ...
  }
  // JdbcQueryExecutor.transformEQL:191-195 — 每查询新建变换器并全量变换
  private SQL transformEQL(IOrmSession session, ICompiledSql compiled, List<Object> params) {
      return new GenSqlTransformer(env.getShardSelector(), env.getOrmModel(), env,
              session.getLoadedOrmModel(), this.env.getEntityFilterProvider())
              .transform(compiled.getSql(), params).end();
  }
  // JdbcTemplateImpl.withTxn:180-191 — auto-commit 查询也过一遍 SUPPORTS 事务模板
  T ret = transactionTemplate.runInTransaction(dynamicQuerySpace, TransactionPropagation.SUPPORTS, txn -> {...});
  ```
- **严重程度**: P3
- **并发场景**: 全部 worker 线程的每查询路径；共享结构（queryPlanCache/SqlExprMetaCache）为并发容器读，无争用热点（LocalCache/Caffeine 读路径无锁）。
- **触发时序/量化链**: 每查询 = (a) `QueryPlanCache.get`：TextHash O(L)，L=200 字符 ≈ 0.2μs、L=2000 ≈ 2μs（动态拼 SQL 文本长者靠 LRU 1000 兜底，命中时仍全文哈希）；(b) `planQuery` marker 扫描 ≈ 0.5–2μs；(c) `GenSqlTransformer.transform`：O(AST 节点) ≈ 5–20μs（中等 SQL），此为最大项——租户过滤/分片/逻辑删除需按请求状态注入，属设计内必要；(d) SUPPORTS 事务包装：新建 JdbcTransaction + txnId 字符串 + 2×addListener（各建一次 COW 数组）+ 注册/注销 2 次 context map 操作 ≈ 6–10 分配 ≈ 1–3μs；(e) `SQL.begin(sql).readOnly(true).end()` 拷贝 ≈ 0.5μs。合计 ≈ **8–30μs/查询**，占典型 0.5–5ms DB 往返的 0.5–5%。无单点全平台串行化（无全局锁），不满足升 P1 条款。
- **现状**: 三项均为架构性常数（正确性所需），无免费消除项；登记目的=(1) 给后续优化提供分解基线（(d) 可在 withTxnForSql 中对"无事务且 SUPPORTS"短路为直接借还连接，省 6–10 分配）；(2) 登记**基准缺口**。
- **风险**: 高 QPS 场景（每秒数万查询）ORM 层 CPU 占比上升至单核可感；不构成劣化趋势或正确性风险。
- **建议**: 基准缺口登记——在 nop-benchmark 增加 JMH 基准：`EqlPlanCacheHitBenchmark`（AverageTime，键=固定 EQL 文本，测 compileSql 命中路径）；`GenSqlTransformerBenchmark`（参数化 AST 规模 5/20/50 节点）；`AutoCommitTxnOverheadBenchmark`（对照 withTxn 直连）。修复方向：(d) SUPPORTS 短路；(a) 可选——`QueryPlanCacheKey` 预计算并缓存 hashCode（final 字段，构造时算一次）。
- **失效面枚举（F3）**: ① `executeQuerySql:99-124` / `executeUpdateSql:76-86` / `executeStatementSql:137-152` —— 三入口同构支付 (a)–(e)，同族；② `MdxQueryExecutor`（findListByQuery 入口）—— 复用同一 compile+transform 链，同族；③ `SqlLibManager.doInvoke:104-117` —— sql-lib 路径仅 buildSql（模板渲染），不走 GenSqlTransformer，支付面更小，安全；④ `DaoQueryHelper.queryToSelectObjectSql` 系 —— 每次调用重建 SQL builder（dao 直查路径），同族小项，随 ③ 口径登记不单列。
- **信心水平**: 很可能（μs 级估算基于结构复杂度，无实测）
- **误报排除**: 非低频路径（明确为每查询必经链）；非"无界缓存"或正确性问题；按 C9"默认 P2/P3、无量化依据不报"口径，此条以完整估算链立项为 P3 并附基准缺口。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**。round2 抽验通过（QueryPlanCacheKey.hashCode 全文哈希、transformEQL 每查询新建 GenSqlTransformer 亲验确认）

---

### [nop-persistence-rw-C9-003] JdbcEntityPersistDriver 单槽 SQL 缓存在交替 propIds/并发混访下退化为逐次重新生成

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/jdbc/JdbcEntityPersistDriver.java:62-73,125-148,150-185,187-224,281-293`
- **证据片段**:
  ```java
  // 单槽缓存：volatile 良性竞争（注释自认），但每驱动每 SQL 形态仅记忆最后一份
  private volatile EntitySQL loadSql;
  private volatile EntitySQL lockSql;
  private volatile EntitySQL batchLoadSqlPart;
  private volatile EntitySQL lastUpdateSql;
  // loadAsync:130-135 — propIds 不匹配即整体重新生成
  if (dialect != this.dialect || !loadSql.propIds.equals(propIds)) {
      loadSql = GenSqlHelper.genLoadSql(dialect, entityModel, binders, propIds);
      if (dialect == this.dialect)
          this.loadSql = loadSql;
  }
  ```
- **严重程度**: P3
- **并发场景**: driver 为 per-entity-model 全局单例（`SessionFactoryImpl.createEntityPersistDriver:386-390` 每模型一个），被所有 session/线程共享；多线程各自请求不同懒加载属性子集时交叉覆写单槽。
- **触发时序**: T1 线程加载实体 A 的懒属性 {p1}（slot ← SQL{p1}）→ T2 线程加载实体 B 的懒属性 {p2}（slot ← SQL{p2}，不等价→重新生成）→ T1 再加载同形态 {p1}（slot 已被覆写→再次重新生成）——混访下命中率趋近 0%。同步单线程场景：flush 批内实体脏属性集交替（先改 a、b 两字段，再改 c、d 字段…），`buildUpdateSql:287-292` 每个新形态重新 `genUpdateSql`。
- **量化链**: `GenSqlHelper.genLoadSql/genUpdateSql` ≈ 列遍历 + StringBuilder ≈ 2–10μs/次。批量 flush 10,000 updates 交替 2 个形态 → 10,000 次生成（应 2 次）≈ **20–100ms/flush** 额外 CPU；并发混访的单实体懒加载 ≈ 每次加载多 2–10μs。相对单行 DB 往返（≥100μs）占比 ≤10%，批量 flush 场景占比 <10%，无串行化点。
- **现状**: 注释已明示"良性竞争"裁定（正确性安全——EntitySQL 为不可变值，丢失只是缓存失效）；单槽是有意的内存换复杂度取舍。
- **建议**: (1) 若懒加载属性子集可枚举（通常 ≤ eager/all/少数组合），换 2–4 槽或 per-propIds 小 ConcurrentHashMap（键=IntArray，模型封闭键空间，C5 安全）；(2) `buildUpdateSql` 在批执行入口按 propIds 分组预处理后再逐组构建，消除批内交替失效。
- **失效面枚举（F3）**: ① `loadSql`（loadAsync:129-135）—— 本体；② `lockSql`（lock:154-159）—— 同构；③ `batchLoadSqlPart`（batchLoadAsync:192-197）—— 同构；④ `lastUpdateSql`（buildUpdateSql:287-292）—— 同构（批路径，最高频）；⑤ `findLatestSql/insertSql/deleteSql`（init 期一次构建，`JdbcEntityPersistDriver.java:88-96`）—— 非懒构建，**安全**；⑥ `JdbcCollectionPersistDriver.loadSql/batchLoadSqlPart`（`:63-67,81,110`）—— 同族同构，修复须覆盖。
- **信心水平**: 确定（失效模式）；耗时为量级估算。
- **误报排除**: 非"原语使用"误报——volatile 已保证正确性，本条纯性能面；按 C9 校准判 P3（SQL 生成 ≪ DB 时间，无争用无串行化）。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**。量级结论维持；机制判定由 C9-006 修正（退化归因于引用相等而非交替负载），本条保留作量级来源、修复轮以 C9-006 方案为准

---

### [nop-persistence-rw-C9-004] 每条 SQL 无条件 INFO 级日志（含批量路径逐批全文本）：默认配置下的热路径日志税

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:175,276,322`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/JdbcBatcher.java:195-196,203,312`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcHelper.java:58,79-84`
- **证据片段**:
  ```java
  // JdbcHelper — 专用 logger "io.nop.dao.sql"，dumpSql 在 info-enabled 时构建全文缓冲
  public static final Logger SQL_LOG = LoggerFactory.getLogger("io.nop.dao.sql");
  public static void dumpSql(String title, SQL sql) {
      if (SQL_LOG.isInfoEnabled()) { ... SQL_LOG.info("title={},...,sql=\n{}", ...); }
  }
  // JdbcTemplateImpl.runWithConnection:175 — 每查询一行（含完整 SQL 文本）
  JdbcHelper.SQL_LOG.info("nop.jdbc.run:usedTime={},querySpace={},range={},name={},sql={}", ...);
  ```
- **严重程度**: P3
- **并发场景**: 全部 worker 线程每 SQL 一次；日志 appender 为共享串行化资源（异步 appender 可缓解）。
- **触发时序/量化链**: 默认日志级别 INFO 时：每查询 1 行 `nop.jdbc.run`（含 SQL 全文本，L≈200–2000 字符）+ prepareStatement 内的 `dumpSql("pagedSql:", ...)` 第二行（分页查询双倍）≈ **2–10μs 格式化 + appender IO**；批量路径每批再 +1 行（`execute-batch-success` 含 sql 文本）。10k QPS ≈ 2 万行/秒 ≈ 4–40MB/s 日志流量 + appender 争用。INFO 关闭时仅剩 slf4j 参数装箱（5 参重载，无字符串拼接）≈ <0.5μs。
- **现状**: logger 名独立（`io.nop.dao.sql`），生产可通过日志配置单独降噪；slf4j 惰性求值使关闭成本可忽略。属"默认值偏 dev 习惯"而非结构缺陷。
- **风险**: 未调日志配置的生产部署产生线性于 QPS 的日志 IO 与磁盘占用；同步 appender 下引入每查询一次跨线程锁（logback ConsoleAppender）——量级 μs，不构成串行化瓶颈。
- **建议**: (1) 将逐 SQL `nop.jdbc.run` 行降为 DEBUG（保留 `usedTime` 超阈值时 WARN/慢日志通道，对齐平台慢 SQL 惯例）；(2) 或文档明确生产必须为 `io.nop.dao.sql` 配 WARN。
- **失效面枚举（F3）**: ① `runWithConnection:175` —— 所有 SQL 的公共出口（最高频）；② `executeUpdate:276`、`executeStatement:322`、`callFunc:818` —— 具名站点同族；③ `JdbcBatcher:195,203,312` —— 批路径 3 行；④ `dumpSql:79-84` —— prepareStatement 分页分支第二行；⑤ `ShardFanoutEngine.FANOUT_LOG` —— 引擎级汇总日志（每 fanout 查询 1 行，非每行/每分片），**安全**。
- **信心水平**: 确定（站点与成本结构）；默认日志级别对 `io.nop.dao.sql` 的实际取值依赖部署配置（仓库内未见显式覆盖，按平台缺省 INFO 推定——此假设已标注）。
- **误报排除**: 非低频管理日志；判 P3 因可通过配置完全消除且无正确性影响；未按"日志参数急切求值"误报口径豁免——本站点参数本身是惰性的，成本在 info-enabled 分支与 appender，是配置默认值问题。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**

---

### [nop-persistence-rw-C9-005] 元模型查找命中路径确认：OrmComponentModel 惰性 Map 命中路径 O(1) 无每调用重建（C1-015 竞态面互引，不重复判级）

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/OrmComponentModel.java:42-51,105-118`；`nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/OrmEntityModel.java:68-108`；`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/meta/SqlExprMetaCache.java:28-36`
- **证据片段**:
  ```java
  // OrmComponentModel.getColumnPropIdMap — 惰性初始化后命中路径仅一次字段读
  public Map<String, Integer> getColumnPropIdMap() {
      if (colPropIds == null) {
          colPropIds = new HashMap<>();
          for (OrmComponentPropModel propModel : this.getProps()) { ... }
      }
      return colPropIds;
  }
  // OrmEntityModel — 启动期 init 固化 colsByPropId 数组/eagerLoadProps 等，热路径数组直取
  private OrmColumnModel[] colsByPropId;      // getColumnByPropId O(1)
  private ImmutableIntArray eagerLoadProps;   // 预计算 IntArray
  ```
- **严重程度**: P3（确认性登记，非缺陷立项）
- **并发场景**: 模型对象跨线程共享；C1-015 已立案"无同步惰性初始化"的可见性/竞态面——本条仅审**命中路径开销**（该维度未审面）。
- **触发时序/量化链**: 命中路径 = `colPropIds != null` 一次字段读 + 返回引用，O(1)、无分配、无锁，≈ <1ns；未命中仅在首次访问每组件发生一次（构建 O(props)，props 为模型常数 ≤ 数十）。竞态后果（C1-015 面）是重复构建相同的确定性 HashMap 与理论上的未安全发布读取——**无每调用常数因子退化**；`OrmEntityModel` 的 propId→column 查找走 `colsByPropId` 数组 O(1)，`getEagerLoadProps/getAllPropIds` 为预计算 ImmutableIntArray 引用返回；`SqlExprMetaCache.getEntityTableMeta` 命中为 ConcurrentHashMap.get O(1)。
- **现状**: 命中路径无量化问题；唯一的架构性残留是每次 `EntitySQL` 生成期对 `propIds.equals` 的数组比较（O(propIds)，见 C9-003 单槽判定），已在其条目覆盖。
- **风险**: 无（就本维度而言）；并发正确性面以 C1-015 台账为准。
- **建议**: 无需行动；若 C1-015 修复时引入同步，注意选择"启动期 init 一次"而非每次判空（避免给命中路径加内存屏障——模型在 `LoadedOrmModel` 构造后即封闭，存在启动期固化窗口）。
- **失效面枚举（F3）**: ① `OrmComponentModel.getColumnPropIdMap/getColumnPropIds` —— 本体（惰性、无 volatile）；② `OrmEntityModel` 全部预计算字段 —— init 期固化，命中安全；③ `SqlExprMetaCache.getEntityTableMeta` —— get+computeIfAbsent，命中 O(1)（未命中双查为良性，正确性面归 C1 族）；④ `OrmCompositePk.parse/build` —— 每复合主键调用分配，调用频率=复合主键实体访问频次，单次 ≈100ns，量级不足立项。
- **信心水平**: 确定
- **误报排除**: 本条不按缺陷报，而是对任务指定站点（"C1-015 已立案竞态，命中路径开销未审"）的**命中路径审结**：无每调用重建、无锁、无分配，判定为无 C9 问题；全字段列出以供复核轮对照。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**（确认性登记）

---

## 基准缺口登记（对照 nop-benchmark 惯例）

| 缺口 | 建议形态 |
|------|---------|
| EQL 计划缓存命中路径（含 QueryPlanCacheKey 哈希） | JMH AverageTime；`@State(Scope.Benchmark)` 预热填满 cache，测 compileSql 命中；参数化 sqlText 长度 100/1000 |
| GenSqlTransformer 全 AST 变换 | JMH AverageTime；参数化 AST 节点数 5/20/50；单线程即可（变换器 per-query 新建，无共享态） |
| auto-commit 查询事务包装开销 | JMH AverageTime；对照 `runWithConnection` 直连 vs `withTxn(SUPPORTS)`；Ban传统 IO，用 mock Connection |
| 批量写 listener 注册 O(n²)（C9-001） | JMH 单批 SampleTime；N=100/1000/10000，测 flush 总耗时中 addListener 占比（修复前后对照即验收基准） |

## CL 线索（CL-600 段）

- **CL-603**: `AbstractTransaction.addListener` 每加必 sort + COW 拷贝（C9-001 结构根因）；若未来新增 per-action 注册点会线性放大，修复 C9-001 时应一并收敛 API（或文档禁止 per-action 注册模式）。

---

**总计**: C9 发现 5 条（P2×1：C9-001；P3×4：C9-002、C9-003、C9-004、C9-005——其中 C9-005 为命中路径审结确认条），热路径总账 3 组（每查询必经链 9 步分解、每实体写路径 7 步、每行装配站点抽验 12/398），基准缺口 4 项，CL 线索 1 条。**末位编号 C9-005**。无 P1：未发现每查询/每事务必经链上的全平台串行化点，亦无掩盖正确性问题（C9-001/C9-003 的正确性面均已由既有裁定确认安全）。

## 维度复核结论（独立复核合并批，2026-10-18）

- **复核方式**: 与初审/深挖代理独立的第 2 轮合并复核批。本维度 6 条发现（含 round2 新增 C9-006）证据逐条亲验；历史对照面已解除盲审。
- **裁定统计**: 6 条全部**保留**（降级 0、驳回 0）——P2×1 维持 / P3×5 维持。
- **C9-006 机制修正终裁（维持 P3，机制判定确认）**: IntArray/ImmutableIntArray/MutableIntArray 三类均未覆写 equals/hashCode（grep 计数 0 + 类层次亲验：ImmutableIntArray implements IntArray 接口、无值语义父类）；6 处 `.propIds.equals` 引用比较点逐一核对（JdbcEntityPersistDriver:130/155/193/288 + JdbcCollectionPersistDriver:80/109）；懒加载路径每次新建 MutableIntArray（OrmSessionImpl.java:855-867 亲验）、`toImmutable()` 返回 this、「恒 miss（结构性、单线程即成立）」的机制修正**确认**——C9-003 原「交替/并发退化」归因被正确修正，修复方向由「加槽」改为「equals 值语义/内容比较」。量级（P3，单次 2-10μs）维持不升 P2。
- **C9-001 与 C2-014 同点位归并**: 确认（监听器聚合修复同时消解 O(n²) 与注册原子性面；C2-014 降 P2 后两案同为 P2、同一工单）。
- **round2 抽验复核**: C9-002 抽验通过（QueryPlanCacheKey.hashCode 全文哈希、GenSqlTransformer 每查询新建亲验）；「无 P1（无每查询必经全平台串行化点）」的维度结论维持。
- **CL 处置（2 条确认）**: CL-603（addListener 每加必 sort 结构根因，随 C9-001/C2-014 修复收敛）确认；**CL-700（IntArray 家族无值相等语义，根因载体 nop-commons）确认**——移交 nop-commons 修复轮，5 根内受累面 6 点（C9-006 F3）+ 平台其他模块需全仓 grep 补齐回归面的提示维持。无驳回。

## 最终保留项

| ID | 初判 | 终裁 | 条目 |
|---|---|---|---|
| C9-001 | P2 | P2（与 C2-014 同工单） | 单事务逐实体监听器 O(n²) |
| C9-002 | P3 | P3 | 每查询常数因子（含 JMH 缺口） |
| C9-003 | P3 | P3（机制以 C9-006 为准） | 单槽 SQL 缓存退化（量级来源） |
| C9-004 | P3 | P3 | 每条 SQL INFO 日志税 |
| C9-005 | P3 | P3 | 元模型命中路径审结（确认性登记） |
| C9-006 | P3 | P3（根因归 nop-commons CL-700） | IntArray 无值 equals——缓存命中率恒 0% |

**维度终局**: P2×1 / P3×5，无驳回、无撤案。
