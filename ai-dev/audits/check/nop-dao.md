# nop-dao 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-persistence/nop-dao
- 文件数: 118（src/main/java，实际 find 统计；`_gen/` 生成代码按规范跳过）
- 覆盖范围声明:
  - **深读全文**: `jdbc/impl/`（JdbcTemplateImpl、JdbcHelper、JdbcFactory、JdbcDialectProvider、JdbcDataSetHelper）、`jdbc/JdbcBatcher`、`jdbc/dataset/`（JdbcDataSet、JdbcComplexDataSet、JdbcStatement）、`jdbc/txn/`（JdbcTransaction、JdbcTransactionFactory）、`jdbc/datasource/` 全部 4 个实现、`txn/impl/`（AbstractTransaction、TransactionTemplateImpl、DefaultTransactionManager、GroupTransaction、TransactionRegistry）、`txn/interceptor/` 全部 2 个、`dialect/impl/`（DialectImpl、DialectSQLExceptionTranslator、SqlDataTypeMapping 未逐行）、`dialect/`（DialectManager、DialectSelector、IDialect 抽查）、`dialect/function/`、`dialect/pagination/`、`dialect/json/`、`dialect/upsert/`、`dialect/loader/`、`utils/` 全部、`api/`（AbstractSqlExecutor、QuerySpaceEnv、DaoProvider、ISqlExecutor、IJdbcTemplate 接口）、`seq/`、`metrics/DaoMetricsImpl`、`initialize/`、`DaoConfigs`。
  - **未深读**: `dialect/model/_gen/` 生成代码（仅抽查 `supportBatchUpdateCount` 字段）、`api/` 下纯实体/接口声明（IEntityDao、IEntityDaoExtension、IDaoEntity、INamedSqlBuilder 等）、`shard/`（接口+枚举型数据类）、`lock/`、`coderule/CodeRuleParams`、`DaoErrors`（仅抽查引用一致性）、resources 下各 dialect.xml（仅 mysql5.7/selector 相关核对）。
  - 方法: 结构扫描 → grep 可疑点（空 catch、new RuntimeException、printStackTrace、ThreadLocal、synchronized）→ 命中点逐一 Read 验证 → 核对调用方（含 nop-orm 中 JdbcBatcher 调用点）→ 核对 `dao-defaults.beans.xml`。
  - 说明: 全模块未发现空 catch、bare `new RuntimeException`、printStackTrace。@Inject 均为 setter 注入（无 private 字段注入），D7 整体合规；唯一 D7 相关问题见 [P2] 第 8 条（cacheProvider 装配缺失导致公共契约失效）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 3 |
| P2 | 9 |
| P3 | 4 |

## 发现列表

### [P0] JdbcBatcher.flush() 批量路径 PreparedStatement 从不关闭，每次批量提交泄漏一个 Statement

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/JdbcBatcher.java:166-244`
- **维度**: D2（资源管理）
- **证据**:
```java
PreparedStatement ps = conn.prepareStatement(sql);
JdbcException error = null;
try {
    for (BatchCommand params : commands) { ... ps.addBatch(); }
    int[] ret = ps.executeBatch();
    ...
} catch (BatchUpdateException e) { ... }
catch (SQLException ex2) { ... throw error; }
finally {
    if (daoMetrics != null)
        daoMetrics.endBatchUpdate(batchSql.getText(), meter, commandCount, error);
    // 没有 ps.close()
}
```
- **现状**: 批量执行路径中 `ps` 在内层 try/catch/finally 与外层 finally 中都没有被关闭（对比同类 `executeOne()` L293 有 `IoHelper.safeClose(ps)`）。成功、`BatchUpdateException`、普通 `SQLException` 三条路径均不关闭。
- **风险**: 该类被 `nop-orm` 的 `JdbcEntityPersistDriver.batchUpdate`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/jdbc/JdbcEntityPersistDriver.java:298-308`）使用，即**所有 ORM 实体批量保存**都走此路径。每满 `batchSize`（默认 200）条 flush 一次泄漏一个 PreparedStatement；在长事务大批量导入场景下，语句句柄持续累积直至数据库游标耗尽（如 Oracle ORA-01000）或驱动侧句柄泄漏，且 Statement 生命周期长于事务时无法被连接复用回收。
- **建议**: 在内层 `finally` 中增加 `IoHelper.safeClose(ps)`。
- **误报排除**: 已通读 flush() 全文确认两个 finally 均无 close；已确认调用方 JdbcEntityPersistDriver 自身也不关闭该 statement（它只持有 batcher）；已排除"连接关闭时统一释放"的解释——批量事务中连接在多个 flush 之间保持打开。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。`JdbcBatcher.flush()` 批量路径内层 finally 补 `IoHelper.safeClose(ps)`（与 `executeOne()` 既有清理一致，close 失败仅记日志不吞主异常），成功/BatchUpdateException/SQLException 三条路径均覆盖。测试：`nop-dao` `TestJdbcBatcher#testFlushBatchClosesStatement`、`TestJdbcBatcher#testFlushBatchFailClosesStatement`（修复前：批量成功与 executeBatch 失败两条路径 flush 后 PreparedStatement 均未 close，断言报 "expected: true but was: false"；修复后全模块 76 tests, 0 failures, 0 errors）。

### [P1] saveCacheData 在 SQL 无 cacheRef 时返回 null 而非原数据集，启用 cacheProvider 后所有普通查询 NPE

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:403-409`（调用点 L336、L340）
- **维度**: D1（正确性）
- **证据**:
```java
private IDataSet saveCacheData(IDataSet ds, SQL sql, LongRangeBean range) {
    if (cacheProvider == null)
        return ds;

    CacheRef cacheRef = sql.getCacheRef();
    if (cacheRef == null)
        return null;      // 应为 return ds
    ...
}
// executeQuery 中:
ds = saveCacheData(ds, sql, range);
T ret = callback.apply(ds);      // ds == null
readCount = ds.getReadCount();   // NPE
```
- **现状**: 只要 `cacheProvider != null` 且当前 SQL 未设置 cacheRef（绝大多数查询），`executeQuery` 即对 null 数据集调用 callback，且 L340 `ds.getReadCount()` 必然 NPE。
- **风险**: 一旦通过 `JdbcFactory.setCacheProvider(...)` 编程式启用查询缓存（公共 API，见 IJdbcTemplateFactory），**全部无 cacheRef 的查询都会崩溃**。当前因 beans.xml 未装配 cacheProvider（见 [P2] 第 8 条）而未爆发，属被配置掩盖的确定性 bug。
- **建议**: `cacheRef == null` 分支改为 `return ds`。
- **误报排除**: 已核对 getCacheData/saveCacheData 全部调用链与返回值使用；本意显然是"无需缓存则原样返回"，`return null` 与方法契约（返回缓存包装后的数据集）矛盾。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。`cacheRef == null` 分支改回 `return ds`；同时在 `getCacheData` 中对"SQL 设置了 cacheRef 但容器未装配 cacheProvider"增加一次性 WARN（见 [P2] 第 4 条处置）。测试：`TestJdbcTemplate#testFindAllWithoutCacheRefWhenCacheProviderConfigured`（红测试已验证：修复前该用例 NPE——"ds is null"，正是审计预测的崩溃点；修复后全模块 102 tests, 0 failures, 0 errors）。

### [P1] 动态 querySpace 启用时（默认开启），方言/异常翻译仍按原始 querySpace 解析，异构数据源下分页 SQL 用错方言

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:312-321, 173-175, 229-233`
- **维度**: D1（正确性）
- **证据**:
```java
// executeQuery:
IDialect dialect = getDialectForQuerySpace(sql.getQuerySpace());  // 原始 querySpace（可能为 null/default）
SQL pagedSql = buildPagedSql(sql, range, dialect);
return runWithConnection("jdbc.executeQuery", pagedSql, range, conn -> {
    st = JdbcHelper.prepareStatement(dialect, conn, pagedSql);     // dialect 未跟随映射

// withTxn:
String dynamicQuerySpace = useDynamicQuerySpace(querySpace);      // QuerySpaceEnv 映射
T ret = transactionTemplate.runInTransaction(dynamicQuerySpace, ...); // 连接来自 dynamic querySpace
```
- **现状**: `CFG_ORM_ENABLE_DYNAMIC_QUERY_SPACE` 默认 `true`（DaoConfigs）。事务与连接按 `QuerySpaceEnv` 映射后的 querySpace 获取，但 dialect（分页语法、超时支持、SQLException 翻译、Clob 处理）一律按 SQL 原始 querySpace（通常是 default）解析。`executeUpdate`/`executeStatement` 同样。
- **风险**: 当动态 querySpace 指向与 default 不同种类的数据库（QuerySpaceEnv 的设计用途之一，如读写分离到异构库）时，`buildPagedSql` 会按 default 方言生成分页 SQL（如对 SQL Server 生成 `LIMIT ? OFFSET ?`）导致 SQL 语法错误；异常翻译、queryTimeout 能力判断也随之错误。
- **建议**: dialect 统一经 `useDynamicQuerySpace(...)` 映射后获取，或直接从当前 `IJdbcTransaction`/连接解析。
- **误报排除**: 已确认 useDynamicQuerySpace 仅作用于 withTxn 的连接选择；同构数据源（常见部署）下无影响，故定 P1 而非 P0。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复，实现比审计建议更集中：直接在 `JdbcTemplateImpl.getDialectForQuerySpace` 覆写方法内统一经 `useDynamicQuerySpace(...)` 映射后再解析方言，executeQuery/executeUpdate/executeStatement 与 exists* 元数据探测路径一次性对齐（审计原文只点名三个执行方法）。测试：`TestJdbcTemplate#testGetDialectForQuerySpaceFollowsDynamicQuerySpace`。附带发现并修复（超出审计）：`QuerySpaceEnv.leave` 的"enter/leave 匹配校验"与 `runWithQuerySpace` 的恢复语义矛盾——leave 用 enter 返回的旧值校验当前值，任何嵌套/普通切换必然抛 `query-space-enter-leave-not-match`；该类全仓库无生产调用方故从未暴露（本报告"补充说明"中"恢复逻辑正确"的判断有误）。已改为标准的"恢复 enter 返回的旧值"语义（null 时 remove）。

### [P1] runInTransactionAsync 与 runInTransaction 在"已存在主事务 + 新建子事务"场景下回滚行为不一致，异步路径会回滚整个主事务组

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/TransactionTemplateImpl.java:343-352 vs 365-375`（场景构造在 L135-144）
- **维度**: D1/D8（正确性、契约一致性）
- **证据**:
```java
// 异步版:
private CompletionStage<Void> rollbackTransactionAsync(TxnState state, Throwable e) {
    if (state.groupTxn != null) {
        if (state.newlyCreated && state.groupTxn.isTransactionOpened())
            return state.groupTxn.rollbackAsync(e);   // 回滚整个主事务组
    } ...

// 同步版:
private void rollbackTransaction(TxnState state, Throwable e) {
    if (state.groupTxn != null) {
        if (state.groupNewlyCreated && state.groupTxn.isTransactionOpened())
            state.groupTxn.rollback(e);               // 仅当本层新建了 group 才回滚
    } else if ...
```
- **现状**: `getTxnState` L135-144 在"主事务已注册但该 querySpace 子事务不存在"时设置 `state.newlyCreated = true`（`groupNewlyCreated` 保持 false）。此时子任务失败：异步路径条件 `newlyCreated` 命中，回滚**整个主事务组**（含其他 querySpace 的子事务）；同步路径条件 `groupNewlyCreated` 不命中，**连子事务都不回滚**（依赖异常向外传播由 owner 回滚）。
- **风险**: (1) 异步嵌套事务（TransactionServiceInterceptor / TransactionalMethodInterceptor 的 async 分支）内层失败时提前回滚外层主事务，若外层捕获内层异常后仍尝试 commit，将提交一个已回滚的连接（数据丢失或二次异常）；(2) 同步/异步语义漂移使行为不可预测。
- **建议**: 异步版条件改为与同步版一致的 `state.groupNewlyCreated`；同时评估同步版在该场景是否应至少回滚新建的子事务。
- **误报排除**: 已逐行比对两方法的条件字段（newlyCreated / groupNewlyCreated）及 getTxnState/createNewTransaction 对这两个标志的赋值路径。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。异步版条件改为与同步版一致的 `state.groupNewlyCreated`。审计提出的开放问题"同步版是否应至少回滚新建的子事务"按同步版既有语义裁定：异常向外传播、由主事务 owner 统一回滚（若本层再回滚子事务，owner 回滚组时会二次回滚同一子事务，风险大于收益）。测试：`TestTransactionTemplateAsync#testAsyncSubTxnFailDoesNotRollbackMainGroup`（红测试已验证：修复前主事务组被回滚 rollbackCount=1，修复后=0）、`#testAsyncGroupNewlyCreatedRollsBackGroup`（新建组场景仍正常回滚）。

### [P2] tryExistsByTemplate 对同一模板 SQL 执行两次查询，findAll 结果整体丢弃

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:584-586`
- **维度**: D6（性能）
- **证据**:
```java
SQL sql = SQL.begin().querySpace(querySpace).sql(sqlText).end();
List<Object> list = findAll(sql);   // 完整执行并物化全部结果，list 从未使用
return exists(sql);                 // 再执行一次（findFirst 语义）
```
- **现状**: `existsTable/existsColumn/existsIndex/...` 走 dialect 模板探测时，同一 SQL 被执行两次，第一次还把整个结果集拉到内存。
- **风险**: 元数据探测型 SQL（通常低频）被双倍执行；`findAll` 对返回大结果集的模板（如列清单）造成不必要物化。属明确的无效代码 + 性能浪费。
- **建议**: 删除 `findAll` 行，仅保留 `exists(sql)`。
- **误报排除**: `list` 为局部变量无任何后续引用；已确认 findAll/exists 均独立发起 executeQuery。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。删除结果整体丢弃的 `findAll(sql)` 行，仅保留 `exists(sql)`。测试：`TestJdbcTemplate#testExistsTemplateSingleExecution`（反射调用私有 tryExistsByTemplate + 子类覆写 findAll/exists 计数，断言 findAllCalls=0、existsCalls=1；修复前 findAllCalls=1）。

### [P2] JdbcBatcher 批次失败时回调语义错误：已成功语句也回调异常，stopOnError 时剩余命令回调丢失

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/JdbcBatcher.java:199-224`
- **维度**: D4/D8（错误处理、回调契约）
- **证据**:
```java
int[] ret = e.getUpdateCounts();
int i = 0;
BatchCommand params;
while (i < ret.length && (params = commands.pollFirst()) != null) {
    if (ret[i] >= 0) {
        LOG.debug("...result-success...");   // 明知该语句成功
    } else {
        LOG.error("...result-fail...");
    }
    params.onComplete(null, cause);          // 成功的语句也回调 (null, cause)
    i++;
}
...
if (stopOnError)
    throw error;                             // 未消费的命令不回调
```
- **现状**: `BatchCommand.onComplete(Integer, Throwable)` 契约为逐条通知成败，但 `ret[i] >= 0`（已成功，含驱动返回 SUCCESS_NO_INFO 之外的正计数）的命令也收到 `(null, cause)`；`stopOnError=true` 抛出后，队列中剩余未确认命令的回调永久丢失。
- **风险**: 上层（如 ORM 批量保存的 per-row 回调，用于生成主键/统计变更数）会把成功行误判为失败，或对部分行收不到任何通知，导致批量结果状态不一致。
- **建议**: 成功语句回调 `(ret[i], null)`；抛出前对剩余命令统一回调失败。
- **误报排除**: 已核对 onSuccess/onComplete 全部调用点与 BiConsumer 语义。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。驱动确认成功的命令回调 `(ret[i], null)`；`Statement.SUCCESS_NO_INFO(-2)` 视为成功但计数未知，回调 `(null, null)`；`stopOnError` 抛出前对剩余未确认命令统一回调 `(null, error)`。`stopOnError=false` 时剩余命令保持既有语义（留在队列等待下次 flush 续传，不做双重回调）。测试：`TestJdbcBatcher#testFlushBatchFailCallbackSemantics`（红测试已验证：修复前成功命令也收到 (null, cause) 导致用例报错；修复后按 H2 实测 updateCounts=[1,-3,1] 断言成功者 (1,null)、失败者 (null,cause)，并验证全表数据一致）。

### [P2] AbstractTransaction 异步 commit/rollback 中 future.exceptionally 返回值被丢弃，错误码包装失效

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/AbstractTransaction.java:239-246, 329-331`
- **维度**: D4（错误处理）
- **证据**:
```java
future.exceptionally(err -> {
    invokeListener(listener -> listener.onAfterCompletion(this, CompleteStatus.UNKNOWN, err), true);
    error = null;
    rollbackOnly = false;
    throw newError(ERR_TXN_ROLLBACK_FAIL).cause(err);   // 包装结果被丢弃
});
return future.thenAccept(v -> { ... });                  // 挂在原 future 上
```
- **现状**: `exceptionally` 返回的新 stage 被丢弃，后续 `thenAccept` 链在原 future 上，异常以原始形态传播；`ERR_TXN_COMMIT_FAIL`（commitAsync L329-331 同样问题）与 `ERR_TXN_ROLLBACK_FAIL` 的包装永远不会到达调用方。
- **风险**: 调用方无法通过 ErrorCode 识别"提交失败/回滚失败"类别（平台错误处理两档策略要求 NopException + ErrorCode）；仅包装丢失，异常本身与 exceptionally 内副作用（listener 通知、状态清理）仍会执行，故为 P2。
- **建议**: `future = future.exceptionally(...)` 接住返回值再链式。
- **误报排除**: 已确认副作用仍执行（exceptionally 的 lambda 在原 future 异常完成时运行），排除"listener 不通知"的更严重解释。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。rollbackAsync 与 commitAsync 均改为 `future = future.exceptionally(...)` 接住返回值再链式，ERR_TXN_ROLLBACK_FAIL/ERR_TXN_COMMIT_FAIL 的错误码包装可达调用方（exceptionally 内副作用原已执行，此处仅补回丢失的包装传播）。测试：`TestAbstractTransactionAsync#testRollbackAsyncWrapsErrorWithErrorCode`、`#testCommitAsyncWrapsErrorWithErrorCode`。

### [P2] beans.xml 未给 nopJdbcTemplate 装配 cacheProvider（setter 亦无 @Inject），ISqlExecutor 查询缓存契约在标准容器中静默失效

- **文件**: `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/beans/dao-defaults.beans.xml:76-80`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:91-93`
- **维度**: D8/D7（契约、装配规范）
- **证据**:
```xml
<bean id="nopJdbcTemplate" ioc:default="true" class="io.nop.dao.jdbc.impl.JdbcTemplateImpl">
    <property name="dialectProvider" ref="nopDialectProvider"/>
    <property name="daoMetrics" ref="nopDaoMetrics"/>
    <!-- 无 cacheProvider -->
</bean>
```
```java
public void setCacheProvider(ICacheProvider cacheProvider) {   // 无 @Inject（对比 setTransactionTemplate 有）
    this.cacheProvider = cacheProvider;
}
```
- **现状**: `ISqlExecutor` 公共契约包含 `getCacheCacheProvider/clearQueryCache/clearQueryCacheFor/evictQueryCache`，SQL 的 `cacheRef` 机制完整实现于 JdbcTemplateImpl，但标准容器装配下 `cacheProvider` 恒为 null——`getCacheData`/`saveCacheData` 直接短路，所有缓存 API 静默 no-op。全仓库检索无任何 beans.xml 为其注入 cacheProvider。
- **风险**: 用户按文档为 SQL 设置 cacheRef 后不生效且无任何告警；`getCacheProvider()` 违反接口非空预期。同时也掩盖了 [P1] 第 2 条的 NPE bug。
- **建议**: 要么在 beans.xml 中装配（如 `@cfg` 控制的 nopDaoCacheProvider），要么在 `setCacheProvider` 加 @Inject 并提供默认实现；至少应在 `sql.getCacheRef() != null && cacheProvider == null` 时打告警日志。
- **误报排除**: 已全仓库 grep cacheProvider 的 xml/java 装配点，确认无其他模块补装配。

> **处置（fix-ai-check 分支，2026-08-22）**: 部分修复 + 暂缓。已落地最小 fail-loud 措施：`getCacheData` 检测到 `sql.cacheRef != null && cacheProvider == null` 时输出一次性 WARN（每实例一次，避免热路径刷屏），用户按文档设置 cacheRef 后不再完全无声。标准容器默认装配哪个 ICacheProvider（选型、TTL/容量策略、与平台缓存体系的整合）属平台级设计决策，裁定暂缓，待查询缓存专项立项处理。

### [P2] HikariDataSourceFactory 忽略 DataSourceConfig 的 idleTimeout/maxLifetime/backgroundValidationInterval/validationQuerySql 配置

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/datasource/HikariDataSourceFactory.java:11-30`
- **维度**: D8（契约一致性）
- **证据**:
```java
dataSource.setMaximumPoolSize(config.getMaxSize());
dataSource.setMinimumIdle(config.getMinSize());
if (config.getConnectionTimeout() != null)
    dataSource.setConnectionTimeout(...);
dataSource.setConnectionInitSql(config.getConnectionInitSql());
// config.getIdleTimeout() / getMaxLifetime() /
// getBackgroundValidationInterval() / getValidationQuerySql() 均未使用
```
- **现状**: `DataSourceConfig`（@DataBean，供多数据源/动态数据源编程式创建）定义了 4 个生命周期/校验字段，工厂构造 HikariDataSource 时全部静默丢弃。
- **风险**: 使用 `nopDataSourceFactory.newDataSource(config)` 的调用方（如多租户新增数据源）配置空闲超时、最大存活时间、连接校验 SQL 均不生效，连接长期滞留或失效连接不被检测。注意：主数据源路径（beans.xml 的 nopHikariConfig）不受影响，已直接配置 idleTimeout/maxLifetime。
- **建议**: 工厂内补齐 4 个字段的映射（validationQuerySql 可映射为 connectionTestQuery 或 dataSourceProperties）。
- **误报排除**: 已通读工厂全文与 DataSourceConfig 全部 getter，确认无引用。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。idleTimeout/maxLifetime 直接映射；validationQuerySql → connectionTestQuery；backgroundValidationInterval → keepaliveTime（Hikari 无后台校验选项，keepalive 为语义最近近似；仅当 0 < interval < maxLifetime 时应用，否则保持 Hikari 默认值，避免 Hikari 启动即抛 IllegalStateException）。附带修复：driverClassName 为 null 时跳过 setDriverClassName（Hikari 可按 jdbcUrl 推断；实测 null 值在 surefire 等类加载环境下直接 NPE）。测试：`TestHikariDataSourceFactory` 三个用例（映射生效/无效 keepalive 跳过/缺省不动）。

### [P2] DialectSelector 字符串比较把 null（通配）排最前，与 int 比较（0 排最后）矛盾，版本特化 selector 会被通配 selector 屏蔽

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/DialectSelector.java:147-160`（使用点 DialectManager.java:98-105）
- **维度**: D1（逻辑正确性，特定条件触发）
- **证据**:
```java
int compare(String s1, String s2) {
    if (Objects.equals(s1, s2)) return 0;
    if (s1 == null) return -1;   // null（通配）排最前
    if (s2 == null) return 1;
    ...
}
int compare(int v1, int v2) {
    if (v1 == v2) return 0;
    if (v1 == 0) return 1;      // 0（通配）排最后
    if (v2 == 0) return -1;
    ...
}
```
- **现状**: 类注释与 int 比较的设计意图均为"针对具体版本的 dialect 排在前面"；但 `productVersion`/`driverName` 为 null（通配）的 selector 排在最前。`DialectManager.getDialectName` 顺序遍历取**第一个** match，通配 selector 先命中后，带版本的特化 selector 永远不会被选中。
- **风险**: 当前模块内置 selector yaml 均无版本区分（每个 productName 仅一条），默认配置不受影响；但一旦为同一 productName 配置"通配 + 版本特化"两条 selector（框架明确支持的用法，仓库中已存在 mysql5.7.dialect.xml），版本特化即失效，5.7 专属语法运行在错误方言上。
- **建议**: `compare(String)` 中 null 排最后（返回 1/-1 对调），与 `compare(int)` 一致。
- **误报排除**: 已核对 Collections.sort + 首个 match 命中的选择逻辑；已核对全部内置 selector.yaml 确认当前无触发。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复，且比审计建议多一步：仅把 `compare(String)` 的 null 改排最后并不能修复触发场景——compareTo 先比 dialectName，同名产品下"通配 mysql + 特化 mysql5.7"按名称排序仍是通配在前、特化永不命中。完整修复：特异性字段（driverName/driverMajorVersion/driverMinorVersion/productVersion）全部挪到 dialectName 之前比较，dialectName 退为最后的稳定排序字段；null（通配）在字符串比较中排最后，与 compare(int) 的 0 约定一致。内置 selector yaml 每产品仅一条，排序字段调整对现网配置无影响；equals/compareTo 比较的字段集合不变。测试：`TestDialectSelector` 三个用例（排序、int 约定、模拟 DialectManager 顺序取首个 match）。

### [P2] DynamicDataSource 静态 ThreadLocal 切换数据源后无 remove/恢复封装，线程池复用存在串库风险

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/datasource/DynamicDataSource.java:22-38`
- **维度**: D3（并发与线程安全）
- **证据**:
```java
static ThreadLocal<DataSource> s_dataSource = new ThreadLocal<>();

public DataSource switchDataSource(DataSource dataSource) {
    DataSource old = s_dataSource.get();
    s_dataSource.set(dataSource);
    return old;   // 仅返回旧值，无 try/finally 封装，类内无任何 remove()
}
```
- **现状**: 切换后清理责任完全交给调用者手工"switch 回旧值"；本类没有 remove()，也没有类似 QuerySpaceEnv.runWithQuerySpace 的 runWith 封装（对比同模块 `QuerySpaceEnv` 已实现规范的 enter/leave 恢复）。
- **风险**: 在线程池（Web 容器/异步 executor）中调用者遗漏恢复时，后续复用该线程的无关请求会路由到上一次切换的数据源——跨请求数据串写。当前 beans.xml 中 DynamicDataSource 的 bean 定义被注释（默认 Hikari 直连），属按需启用的公共组件。
- **建议**: 提供 `runWith(dataSource, task)` 封装（finally 恢复/null 时 remove），并在文档/方法注释中标明手工恢复义务。
- **误报排除**: 已 grep 全仓库确认无调用方（当前未启用）且类内无 remove 路径；风险为启用该组件后的模式性缺陷而非当前活跃 bug，故定 P2。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。新增静态方法 `runWith(dataSource, task)`：保存旧值 → 切换 → finally 恢复（旧值为 null 时 remove），任务抛异常也保证恢复；`switchDataSource` 保留不动以兼容手工模式。测试：`TestDynamicDataSource` 四个用例（恢复、嵌套、异常路径、已有手工切换值时恢复旧值而非清空）。

### [P2] JdbcDataSetHelper.newDataSet 返回的数据集只关闭 ResultSet，PreparedStatement 泄漏

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcDataSetHelper.java:42-53`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/dataset/JdbcDataSet.java:87-93`
- **维度**: D2（资源管理）
- **证据**:
```java
public static IDataSet newDataSet(Connection conn, SQL sql) {
    ...
    PreparedStatement st = JdbcHelper.prepareStatement(dialect, conn, sql);
    ResultSet rs = st.executeQuery();
    return new JdbcDataSet(dialect, rs);   // st 的引用丢失
}
// JdbcDataSet.close(): 只 rs.close()
```
- **现状**: 公共 helper 返回的 `JdbcDataSet.close()` 仅关闭 rs；创建的 PreparedStatement 引用直接丢失，调用者无任何途径关闭它。SQLException 路径下 st 同样不关闭。
- **风险**: 外部调用者（公共 API）每次查询泄漏一个 Statement，游标耗尽风险同 [P0] 第 1 条。仓库内当前无调用方（已 grep 全仓库确认），故降为 P2。
- **建议**: 参照 JdbcTemplateImpl.executeQuery 的模式，由 helper 自持 st 并在 close 时一并关闭（如包装数据集持有 statement 引用）。
- **误报排除**: 已确认 JdbcDataSet 构造函数只接收 rs；已确认仓库内无调用点，标注为公共 API 缺陷。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。`JdbcDataSet` 新增持有 statement 的三参构造，`close()` 关闭 rs 后在 finally 中一并安全关闭 statement（rs.close 抛错不吞）；`JdbcDataSetHelper.newDataSet` 传入 st，executeQuery 抛错的 SQLException 路径同样先关 st 再翻译异常。原两参构造保留（调用方自管语句生命周期的场景）。测试：`TestJdbcDataSetHelper` 两个用例（正常 close 与执行失败路径，代理跟踪 statement 的 close 调用；另实测 H2 对不存在的表在 prepare 阶段即抛错，故用除零 SQL 触发 executeQuery 失败路径）。

### [P2] DefaultJsonTypeHandler.toLiteral 生成 `JSON '...'` 字面量时 JSON 文本中的单引号未转义

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/json/DefaultJsonTypeHandler.java:16-26`（调用链 `DialectImpl.getValueLiteral` L561-563）
- **维度**: D5（安全/SQL 注入）
- **证据**:
```java
public String toLiteral(Object value, IDialect dialect) {
    if (value == null) return "NULL";
    return "JSON '" + toJsonText(value) + "'";
}
private String toJsonText(Object value) {
    if (value instanceof String) return (String) value;   // 原样内插
    return JsonTool.stringify(value);                     // JSON 转义 " 但不转义 '
}
```
- **现状**: 字面量以单引号包裹但内容未做 SQL 单引号转义（对比 `DialectImpl.getStringLiteral` 使用 `StringHelper.escapeSql`）。
- **风险**: 含 `'` 的 JSON 值经 `getValueLiteral` 生成内联 SQL 时产生语法错误或注入点（如 RawText 包装的用户可控 JSON 用于生成 SQL 文本/初始化数据脚本的场景）。
- **建议**: 对 toJsonText 结果应用 `StringHelper.escapeSql(text, false)` 后再拼接。
- **误报排除**: 已核对 getValueLiteral 调用链（RawText 值路由到此）及 escapeSql 在 getStringLiteral 中的既有用法，确认此处遗漏。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。`toLiteral` 对 JSON 文本应用 `StringHelper.escapeSql(text, false)` 后再拼接，与 `DialectImpl.getStringLiteral` 的既有做法一致。测试：`TestDefaultJsonTypeHandler` 四个用例（null、无单引号、String 值含单引号、Map 序列化值含单引号）。

### [P3] JdbcDataSet.next() 后半段为不可达死代码，且内部 rs.next() 返回值未检查

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/dataset/JdbcDataSet.java:108-129`
- **维度**: D1/D8
- **证据**:
```java
public IDataRow next() {
    if(!hasNext())
        throw new NoSuchElementException();
    if (hasNext != null) {          // 到达此处 hasNext 必为 true（否则上面已抛）
        if (hasNext) { ...return this; }
        return null;                // 不可达
    }
    try {
        rs.next();                  // 不可达；且返回值被忽略
        ...
```
- **现状**: 第一个 `if(!hasNext())` 保证后续 `hasNext != null && hasNext == true`，L118 `return null` 与 L121-129 整段不可达；不可达段中 `rs.next()` 返回值未检查，若未来被复用会成为隐患。
- **风险**: 维护性风险与契约混淆（next() 语义上永不返回 null）。
- **建议**: 删除死代码段。
- **误报排除**: 已核对 hasNext() 的缓存逻辑保证非空。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。删除不可达段，`next()` 简化为 hasNext 校验 + 读计数 + 返回 this，可观测行为不变。No new test required: 纯死代码删除、无行为语义变化，既有经 executeQuery/findAll 的全部测试即覆盖 next() 语义。

### [P3] cleanupTransaction 异常参数误用：ARG_TXN 传入 state.groupTxn（该分支下必为 null）

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/TransactionTemplateImpl.java:384-388`
- **维度**: D4（错误信息质量）
- **证据**:
```java
} else if (state.newlyCreated && state.groupTxn == null) {
    if (!transactionManager.unregisterTransaction(state.txn)) {
        ex = new NopException(ERR_TXN_NOT_REGISTERED).param(ARG_TXN, state.groupTxn);  // 应为 state.txn
    }
}
```
- **现状**: 分支条件已限定 `state.groupTxn == null`，异常参数却传 groupTxn，排障时关键信息（哪个事务未注册）恒为 null。
- **风险**: 故障诊断信息缺失。
- **建议**: 改为 `.param(ARG_TXN, state.txn)`。
- **误报排除**: 直接的复制粘贴错误，已核对相邻分支。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。改为 `.param(ARG_TXN, state.txn)`。No new test required: 仅异常诊断参数修正，不改变任何控制流。

### [P3] SimpleDataSource.setDriverClassName 对 null 输入抛裸 NPE

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/datasource/SimpleDataSource.java:103-111`
- **维度**: D1/D4
- **证据**:
```java
public void setDriverClassName(String driverClassName) {
    this.driverClassName = driverClassName.trim();          // null 时 NPE
    String driverClassNameToUse = driverClassName.trim();   // 二次 trim 冗余
    ...
}
```
- **现状**: 配置漏填 driverClassName（DataSourceConfig 中允许为空，Hikari 可按 jdbcUrl 推断）时抛出不带上下文的 NPE，且与后一行重复 trim。
- **风险**: 配置错误时的报错不满足错误处理规范（应给出参数名与来源）；`driverClassName` 为 null 的 DataSourceConfig 经 `JdbcFactory.newSimpleDataSource` 即可触达。
- **建议**: 前置 `StringHelper.isEmpty` 判断并抛带参数的 NopException；去掉重复 trim。
- **误报排除**: 已确认 newSimpleDataSource 无判空直接调用。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。空/空白 driverClassName 抛 `NopException(ERR_DAO_MISSING_DRIVER_CLASS_NAME)`（新增错误码，zh/en i18n 聚合文件已同步）；去除二次 trim。测试：`TestSimpleDataSource` 三个用例（null、空白、合法值 trim 后加载 org.h2.Driver）。

### [P3] MysqlUpsertHandler.buildUpsert 返回 null 的空实现；JdbcStatement 多个 setter 异常 translate 的 action 名复制粘贴错误

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/upsert/MysqlUpsertHandler.java:10-13`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/dataset/JdbcStatement.java:185-192, 216-219`
- **维度**: D8/D4
- **证据**:
```java
public class MysqlUpsertHandler implements IUpsertHandler {
    @Override
    public SQL.SqlBuilder buildUpsert(String tableName, String[] columnNames) {
        return null;   // 调用即 NPE
    }
}
// JdbcStatement.setLocalDate / setTimestamp:
} catch (SQLException e) {
    throw dialect.getSQLExceptionTranslator().translate("setBoolean", e);  // action 名错误
}
```
- **现状**: (1) MysqlUpsertHandler 是唯一的具体 upsert 实现，dialect 模型的 `upsertHandler` 字段可指向它，一旦被调用直接 NPE（当前仓库无调用方，`_DialectModel.getUpsertHandler` 未被消费）；(2) JdbcStatement 的 setLocalDate/setTimestamp 异常翻译 action 名标为 "setBoolean"，setJsonString/setString 等也有零星复用（如 JdbcDataSet 多处 `translate("rs.get", e)`）。
- **风险**: 死代码误导 + 异常日志中操作名与实际不符，影响排障。
- **建议**: 删除或补全 MysqlUpsertHandler；修正 translate action 字符串。
- **误报排除**: 已 grep 确认 buildUpsert/upsertHandler 当前无消费方。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。(1) 删除 `MysqlUpsertHandler` 与 `IUpsertHandler`：唯一实现恒返回 null，全仓库零调用方、零 xml/xdef 引用（dialect 模型的 `_upsertHandler` 为 String 字段，无类型依赖），属误导性死代码；未来实现 MySQL upsert 时应连同集成测试重新引入。(2) JdbcStatement 的 setLocalDate/setTimestamp 异常翻译 action 名由 "setBoolean" 修正为正确名称。No new test required: 死代码删除与日志文案修正，均无行为语义变化。

## 补充说明（非缺陷观察）

- 事务注册表基于 `IContext` attribute（TransactionRegistry），随请求上下文销毁释放，未发现 ThreadLocal 泄漏路径；QuerySpaceEnv 的 enter/leave 恢复逻辑：本报告检查时认为正确，经修复 [P1] 第 2 条时实测发现 leave 的匹配校验使 runWithQuerySpace 必然抛错，已在 fix-ai-check 分支修正为标准恢复语义（null 时 remove）。
- JdbcTemplateImpl 各执行路径的 rs/st 关闭顺序规范（finally 中 rs 先于 st）；metrics 均判空；SQL 参数一律经 TypedValueMarker/binder 绑定，未发现字符串拼接注入路径（existsTable 的表名经 escapeSQLName 转义）。
- Snowflake ID 生成器与 DbEstimatedClock 的并发控制（synchronized + fetching 标志 + double-check）经推演无竞态。
- D7 平台约定整体合规：无 private 字段 @Inject、无 @Value、无 bare RuntimeException、错误码统一走 DaoErrors + NopException.param(...)。
