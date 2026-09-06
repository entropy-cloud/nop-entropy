# nop-dao 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-persistence/nop-dao
- 文件数: 107（src/main/java，无 `_` 前缀生成文件、无 `_gen/` 目录）
- 覆盖范围声明: 深读约 50 个文件（JdbcBatcher、JdbcTemplateImpl、JdbcHelper、JdbcDataSet/JdbcStatement/JdbcComplexDataSet/JdbcDataSetHelper、JdbcTransaction(JdbcTransactionFactory)、TransactionTemplateImpl、DefaultTransactionManager、AbstractTransaction、GroupTransaction、TransactionRegistry、两个事务拦截器、SimpleDataSource/DynamicDataSource/DataSourceConfig/HikariDataSourceFactory、DialectManager、DialectImpl、DialectSQLExceptionTranslator、SqlDataTypeMapping、DialectSelector、DialectModelLoader、TemplateSQLFunction/NativeSQLFunction、DbEstimatedClock、DaoHelper、SqlExecHelper、TransactionalFunctionInvoker、DaoMetricsImpl、DaoProvider、AbstractSqlExecutor、QuerySpaceEnv、Snowflake/Uuid 序列、4 个 JsonTypeHandler、3 个异常类、shard 类、DaoConfigs、beans.xml 等），其余约 57 个接口/纯数据类（api 包接口、dialect/model 数据类、txn/jdbc 接口、coderule 接口）做逐文件快速浏览 + 模式扫描（注入注解、线程不安全类、RuntimeException、资源关闭），模式扫描覆盖 100%；深读约 47%。跨模块验证了 IDataParameters/IDataRow 索引约定、MathHelper.secureRandom、JdbcBatcher 的三个调用方（JdbcEntityPersistDriver、JdbcInsertBatchConsumer/JdbcUpdateBatchConsumer）、EntityPersisterImpl 回调链、setStopOnError 全仓库调用情况（含测试）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 2 |
| P2 | 6 |
| P3 | 10 |

## 发现列表

### [P1] JdbcBatcher 将负数更新计数伪造为 1，SUCCESS_NO_INFO 驱动下批量更新乐观锁检查失效

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/JdbcBatcher.java:{312-318}`（配合 `JdbcBatcher.java:{86-87}`、`dialect/impl/DialectImpl.java:{244-246}`）
- **维度**: D1
- **证据**:
```java
// JdbcBatcher.java:312
void onSuccess(BatchCommand command, int updateCount) {
    if (command.singleChange && !checkSingleChange) {
        if (updateCount < 0)
            updateCount = 1;      // SUCCESS_NO_INFO(-2) / EXECUTE_FAILED(-3) 一律当作成功 1 行
    }
    command.onComplete(updateCount, null);
}

// JdbcBatcher.java:86 构造函数：checkSingleChange 恒为 false
if (!dialect.isSupportBatchUpdateCount())
    this.checkSingleChange = dialect.isSupportBatchUpdateCount();

// DialectImpl.java:244 所有方言硬编码返回 false（其它 isSupportXxx 均读 features，唯此方法不读）
public boolean isSupportBatchUpdateCount() {
    return false;
}
```
- **现状**: 当 JDBC 驱动对 `executeBatch()` 返回 `Statement.SUCCESS_NO_INFO`(-2)（如 MySQL Connector/J 开启 `rewriteBatchedStatements=true`、部分 PostgreSQL 批处理配置）时，`onSuccess` 会把 -2 伪造成 1 回调给上层。已验证完整调用链：`JdbcEntityPersistDriver.batchExecuteCommand`（`addCommand(sql, true, callback)`，singleChange=true）→ `EntityPersisterImpl.queueUpdate` 回调中 `checkUpdateResult(ret, entity); incOptimisticLockVersion(entity);`，而 `checkUpdateResult` 的 `count == 0` 分支正是乐观锁保护（抛 `ERR_ORM_UPDATE_ENTITY_NOT_FOUND`）。
- **风险**: 乐观锁更新（`where version=?`）实际影响 0 行时，驱动因不返回计数而报告 SUCCESS_NO_INFO，本类将其报告为 1 行成功，`checkUpdateResult(1)` 通过、版本号照常递增——并发丢失更新被静默吞掉，数据库与 ORM 内存状态不一致。单条路径（`executeOne` 走 `executeUpdate()` 返回真实计数 0）不受影响，只有批量多条时触发。
- **建议**: 至少区分 `SUCCESS_NO_INFO` 与 `EXECUTE_FAILED`：EXECUTE_FAILED 不应伪造成成功；对 singleChange=true 且返回 SUCCESS_NO_INFO 的场景，将"假定成功"的策略显式化为可配置项，或在 DialectModel features 中暴露 `supportBatchUpdateCount` 让计数可信的方言启用严格检查（接口与字段已存在但 `DialectImpl.isSupportBatchUpdateCount()` 硬编码 false，使该开关成为死配置）。
- **误报排除**: 已通读 `JdbcBatcher` 全文、`DialectImpl.isSupportBatchUpdateCount` 及全仓库对该方法的引用（无任何子类/配置覆盖）；已读 `JdbcEntityPersistDriver.batchExecuteCommand`（`addCommand(sql, true, ...)` 确认 singleChange=true）、`EntityPersisterImpl.queueUpdate/checkUpdateResult` 确认回调消费链；已确认 `setBatchUpdateCount` 特性无任何启用路径。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。`DialectImpl.isSupportBatchUpdateCount()` 改读 `features.supportBatchUpdateCount`（激活既有死配置，DialectFeatures 模型字段已存在）；`onSuccess` 区分两种负计数——`EXECUTE_FAILED(-3)` 回调 0 行（上层乐观锁检查可发现失败），`SUCCESS_NO_INFO(-2)` 仅对 singleChange 保守假定 1 行（既有语义显式化，计数不可信时的唯一选择）。回归测试 `TestJdbcBatcher.testExecuteFailedReportedAsZeroNotSuccess`（stash 红：旧码 -3 伪造 1）/`testSuccessNoInfoAssumedSingleRowForSingleChange`（旧码同值，绿——语义保留项）。
### [P1] JdbcBatcher：stopOnError=false 时批处理失败后残留命令会与后续批次混合，且可能用新 SQL 文本执行旧参数

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/JdbcBatcher.java:{193-258}`（配合 `addCommand` 135-145）
- **维度**: D1
- **证据**:
```java
// flush() 的 BatchUpdateException 分支（节选）
int[] ret = e.getUpdateCounts();
try {
    int i = 0;
    BatchCommand params;
    while (i < ret.length && (params = commands.pollFirst()) != null) {
        ... // 仅回调驱动已确认的 ret.length 条命令
        i++;
    }
} catch (Exception e2) { ... }

if (stopOnError) {
    BatchCommand remain;
    while ((remain = commands.pollFirst()) != null) {
        remain.onComplete(null, error);   // stopOnError=true 时清空并统一回调失败
    }
    throw error;
}
// stopOnError=false 时：commands 中残留未执行的命令，flush 结束只置 this.sql = null

// 下一次 addCommand：
if (this.sql != null && !Objects.equals(this.sql, sql.getText())) {   // this.sql 已被置 null，不会触发 flush
    flush();
}
this.sql = sql.getText();
commands.addLast(new BatchCommand(sql, singleChange, callback));     // 新命令直接追加到残留命令之后
```
- **现状**: `stopOnError=false` 且驱动在批处理中途停止继续执行（`getUpdateCounts().length < commands.size()`，MySQL 非 rewrite 模式的默认行为）时，未被驱动确认的命令残留在 `commands` 队列中。下次 `addCommand` 因 `this.sql` 已被置 null 不会触发 flush，新 SQL 文本被记录后新命令追加到残留命令之后；下次 flush 用 `conn.prepareStatement(sql)`（新 SQL 文本）执行，而 `setParams(ps, params)` 给残留命令用的是旧命令自己的参数。
- **风险**: 两种数据错误：(1) 残留命令的旧参数被塞进新 SQL 的 PreparedStatement——参数个数不同则抛 SQLException，个数恰好相同则把旧命令的值写到语义完全不同的语句里（静默数据错乱）；(2) 即使 SQL 文本相同，残留命令可能已被驱动执行过（取决于驱动语义），导致重复执行。类注释明示 stopOnError=false 是公开支持的用法（"作为测试数据插入时可能会设置 stopOnError=false"）。
- **建议**: stopOnError=false 且残留命令非空时，要么丢弃残留命令并回调失败（与 stopOnError=true 的兜底一致），要么在 flush 出口保证 commands 清空/仅保留确定未执行的命令，并在 addCommand 中以 `commands.peekFirst()` 的 SQL 文本（而非 `this.sql` 字段）判断是否需要先 flush。
- **误报排除**: 已通读 JdbcBatcher 全文并核对 `flush()` finally 中 `this.sql = null` 与 `addCommand` 的判重逻辑；全仓库 grep `setStopOnError`（含 src/test）确认当前无生产调用方（仅本类与测试类出现），故不评 P0；`flushNoBatch`/`executeOne` 路径已对照确认该残留问题仅存在于批量路径。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。`flush()` 的 BatchUpdateException 部分确认循环之后统一 `failRemainingCommands(error)` 清空队列（与 stopOnError=true 兜底一致）；同类出口全覆盖：prepare/addBatch 阶段 SQLException、外层事务设置 SQLException、executeOne stopOnError 中断、回调自身抛异常。新批次不再与残留混合。回归测试 `testResidualCommandsFailedAndClearedWhenDriverStops`（stub 驱动 BatchUpdateException([1]) + stopOnError=false；stash 红：残留命令零回调丢失）。
### [P2] DialectImpl.jdbcSet(ResultSet)：空字符串转 null 时 updateNull 缺 +1，写错列

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java:{650-660}`
- **维度**: D1
- **证据**:
```java
public void jdbcSet(ResultSet rs, int index, Object value) {
    if (value instanceof String) {
        String str = value.toString();
        if (convertStringToNull && str.isEmpty()) {
            try {
                rs.updateNull(index);          // <-- 未 +1
            } catch (SQLException e) {
                throw getSQLExceptionTranslator().translate("rs.set", e);
            }
            return;
        }
        ...
    }
    ...
    try {
        rs.updateObject(index + 1, value);     // 正常路径 +1
    }
```
- **现状**: `jdbcSet` 的 index 约定为 0-based（同方法内 `updateObject(index + 1)`、`jdbcSetClob/jdbcSetBlob` 均为 `index + 1`），唯独空字符串转 null 分支调用 `rs.updateNull(index)` 少加 1。`convertStringToNull` 默认为 true（`CFG_AUTO_CONVERT_EMPTY_STRING_TO_NULL` 默认 true，DaoConfigs.java:55-56）。
- **风险**: 通过可更新 ResultSet 路径（`JdbcDataSet.setObject` → `dialect.jdbcSet(rs, index, "")`）更新空字符串时，null 会写到 index 的前一列（index=0 时部分驱动直接抛列无效异常），造成数据写错列。
- **建议**: 改为 `rs.updateNull(index + 1)`。
- **误报排除**: 已核对 `DialectImpl` 中全部 4 个 ResultSet 写方法的索引处理（jdbcSetClob:693、jdbcSetBlob:702 均 +1）；已确认 `JdbcDataSet.setObject` 是唯一调用方且其读方法（getBoolean 等）均为 0-based（内部 +1）；全仓库 grep `updateRow()` 确认可更新 ResultSet 路径当前无生产调用方，故评 P2 而非 P1。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。`rs.updateNull(index + 1)`。回归测试 `TestDialectWriteIndex.testJdbcSetEmptyStringNullIndexConverted`（ResultSet 动态代理记录调用；stash 红：旧码收到 `updateNull:0`）。
### [P2] JdbcDataSet.setJsonString：updateObject 未做 0-based 到 1-based 转换

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/dataset/JdbcDataSet.java:{182-188}`
- **维度**: D1
- **证据**:
```java
@Override
public void setJsonString(int index, String value) {
    try {
        rs.updateObject(index, value, Types.OTHER);   // 同类其它 set 方法均为 index + 1
    } catch (SQLException e) {
        throw translate("rs.setJsonString", e);
    }
}
```
- **现状**: `JdbcDataSet` 的 `IDataParameters`/`IDataRow` 索引约定为 0-based（`setBoolean` 307/309、`setString` 320/326 等全部 `index + 1`；接口默认实现 `IDataParameters.setJsonString` 委托 `setString`）。本方法直接 `rs.updateObject(index, ...)`。
- **风险**: 与 `DialectImpl.jdbcSet(ResultSet)` 同类 off-by-one：一旦通过可更新 ResultSet 写 JSON 列即写错列或抛异常；且错误消息 action 标为 "rs.setJsonString"（实际为 "rs.set" 风格不一，此处正确）。当前无调用方（全仓库 `.setJsonString(` 无外部调用点），属潜伏契约缺陷。
- **建议**: 改为 `rs.updateObject(index + 1, value, Types.OTHER)`，或复用 `dialect.jdbcSet(rs, index, value)` 路径。
- **误报排除**: 已读 `IDataParameters` 接口的 default 实现确认 0-based 委托语义；已读 `JdbcStatement.setJsonString`（参数侧，正确委托 `setString`）对比；全仓库 grep 确认该方法无调用方，评 P2。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。`rs.updateObject(index + 1, value, Types.OTHER)`。回归测试 `TestDialectWriteIndex.testSetJsonStringIndexConverted`（stash 红：旧码收到 `updateObject:1`）。
### [P2] SimpleDataSource.getConnection：setCatalog/setSchema 抛异常时连接泄漏

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/datasource/SimpleDataSource.java:{40-62}`
- **维度**: D2
- **证据**:
```java
Connection con = DriverManager.getConnection(url, mergedProps); // NOSONAR

if (catalog != null)
    con.setCatalog(catalog);      // 抛 SQLException 时 con 不会被关闭

if (this.schema != null) {
    con.setSchema(this.schema);
}
return con;
```
- **现状**: 连接建立成功后 `setCatalog`/`setSchema` 失败（catalog/schema 不存在或权限不足时驱动抛 SQLException）会直接把异常抛给调用方，已打开的 `con` 没有 close。
- **风险**: 无连接池场景（`MetaDataSourceConnectionProcessor` 用 SimpleDataSource 探测用户配置的外部库、`JdbcConnectionConfig`、`CliReverseDbCommand`）下，配置错误的 catalog/schema 会导致每次探测泄漏一个物理连接，长期运行的服务进程可耗尽数据库连接数。
- **建议**: catalog/schema 设置包 try/catch，失败时 `IoHelper.safeCloseObject(con)` 后再抛出。
- **误报排除**: 已通读 SimpleDataSource 全类（无其它 close 兜底）；已确认三个生产使用方均把它作为直连 DataSource（无池包装，Hikari 场景不经过此类）；SimpleDataSource 无 close 钩子可依赖。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。catalog/schema 设置包 try/catch，失败时 `IoHelper.safeCloseObject(con)` 后再抛。回归测试 `TestSimpleDataSource.testConnectionClosedWhenSetSchemaFails`——H2 对未知 schema 抛 JdbcSQLSyntaxErrorException，利用命名内存库"最后连接关闭即丢库"语义断言无泄漏连接存活（stash 红：泄漏连接使建表跨 close/reopen 存活）。
### [P2] JdbcBatcher 非批量异常路径不回调、不清空队列，回调契约与 BatchUpdateException 分支不一致

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/JdbcBatcher.java:{237-239}`（配合 `{296-309}`、`{270-278}`）
- **维度**: D4
- **证据**:
```java
} catch (SQLException ex2) {                 // addBatch/参数绑定阶段的 SQLException
    error = dialect.getSQLExceptionTranslator().translate(batchSql, ex2);
    throw error;                             // commands 未清空、所有 callback 均不触发
}

// executeOne（flushNoBatch 逐条执行）：
} catch (SQLException e) {
    error = ...translate(params.sql, e);
    params.onComplete(null, e);
    ...
    if (stopOnError)
        throw error;                         // flushNoBatch 的 do-while 中断，剩余命令无回调
}
```
- **现状**: `BatchUpdateException` 分支专门补齐了"剩余命令统一回调失败，避免上层回调丢失"（229-236 行注释明示该契约），但普通 `SQLException` 分支（批准备/addBatch 阶段失败）与 `executeOne` 在 stopOnError=true 中断 `flushNoBatch` 时，队列中剩余命令的 callback 全部丢失、commands 也不清空。
- **风险**: 上层按回调对账的逻辑（`IBatchAction.onSuccess/onFailure` 语义：每个命令恰好一次回调）在异常路径下丢失回调；同一 batcher 实例若被复用还会遇到与 P1 第二条相同的残留命令问题。因异常整体向上抛出、事务通常随之回滚，实际数据影响有限，主要是契约不一致。
- **建议**: 两个异常出口统一按"剩余命令回调 (null, error) 并清空 commands"处理，与 BatchUpdateException 分支对齐。
- **误报排除**: 已通读 flush/flushNoBatch/executeOne 三个执行路径并比对 BatchUpdateException 分支的兜底逻辑；已读 `IBatchAction.EntityBatchAction` 与 `EntityPersisterImpl.queueSave/queueUpdate` 确认回调恰好一次的语义预期。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。批准备/addBatch 失败与 executeOne stopOnError 中断两个出口统一 `failRemainingCommands`（每命令恰好一次回调），队列同步清空防残留混入后续批次。回归测试 `testPrepareFailFailsAllCommandsAndKeepsQueueClean`/`testExecuteOneStopOnErrorFailsRemainingCommands`（stash 红：命令零回调）。
### [P2] JdbcBatcher forceTxn 场景：已回调"成功"的命令随后被整体 rollback，回调结果与数据库状态背离

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/JdbcBatcher.java:{201-236}`
- **维度**: D1
- **证据**:
```java
int[] ret = e.getUpdateCounts();
try {
    int i = 0;
    ...
    while (i < ret.length && (params = commands.pollFirst()) != null) {
        if (ret[i] >= 0) {
            params.onComplete(ret[i], null);    // ① 先回调成功
        ...
    }
} catch (Exception e2) {
    if (resetAutoCommit)
        conn.rollback();                        // ② 后整体回滚
    throw NopException.adapt(e2);
}

if (resetAutoCommit)
    conn.rollback();                            // ②' 主路径同样在回调之后回滚
```
- **现状**: `forceTxn=true`（`JdbcInsertBatchConsumer` 即如此使用）且原连接 autoCommit=true 时，批处理部分成功后出错：驱动确认成功的命令先收到 `(count, null)` 成功回调，随后整个事务被 rollback。回调方若在回调中已执行 `evictGlobalCache`/状态推进（`EntityPersisterImpl` 的回调模式），其内存状态与已回滚的数据库不一致。
- **风险**: 当前两个生产调用方中 `JdbcInsertBatchConsumer` 的 callback 为 null、`JdbcEntityPersistDriver` 不开 forceTxn，故现实影响为零；但这是公开 API 组合（forceTxn + callback）下的契约缺陷，一旦有调用方依赖回调语义即出现状态背离。
- **建议**: 回滚后再回调，或失败时统一回调 `(null, error)`（含驱动已确认成功的命令），并在注释中固化"forceTxn 下批处理是原子单元"的契约。
- **误报排除**: 已核对 resetAutoCommit 仅在 forceTxn 且原 autoCommit=true 时置位；已读 `JdbcInsertBatchConsumer`（setForceTxn(true)、callback=null）与 `JdbcEntityPersistDriver`（默认 forceTxn=false）确认现实触发面，据此评 P2 而非 P1。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。成功路径 commit 前移至回调之前（"先 commit 成功再回调"）；失败路径 forceTxn 分支整体 rollback 后所有命令（含驱动已确认成功的）统一回调失败再抛错，消除"先回调成功、随后被回滚"的背离。回归测试 `testForceTxnBatchFailRollsBackAndFailsAllCommands`（stub 驱动 BatchUpdateException([1])；stash 红：驱动确认的第 1 条曾被先回调 (1,null)）。
### [P2] TransactionTemplateImpl.runInTransactionAsync：回滚自身的失败被吞，与同步版行为不一致

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/TransactionTemplateImpl.java:{195-209}`
- **维度**: D4
- **证据**:
```java
if (NopException.shouldRollback(err)) {
    return rollbackTransactionAsync(state, err).whenComplete((a, b) -> {
        throw NopException.adapt(err);      // b（rollback 自身的异常）被丢弃
    }).thenApply(v -> null);
} else {
    return commitTransactionAsync(state).exceptionally(err2 -> {
        rollbackTransaction(state, err2);   // commit 失败会补 rollback 并抛 err2
        throw NopException.adapt(err2);
    }).thenApply(v -> FutureHelper.returnResult(ret, err));
}
```
- **现状**: 异步版中 `rollbackTransactionAsync` 自身失败（连接已断时 rollback 再抛）时，`whenComplete((a, b) -> throw adapt(err))` 只抛原始 err，回滚失败信号 b 完全丢失。同步版 `runInTransaction`（222-232 行）中 `rollbackTransaction(state, e)` 抛出的异常会直接传播（覆盖原异常，虽有另一个方向的缺陷，但至少可见）。另外异步分支在 `shouldRollback(err)==false && err!=null` 时执行 commit 后仍返回原 err，语义与同步版一致（设计如此），不另行报告。
- **风险**: 回滚失败（通常意味着数据库连接/事务状态异常）被静默吞掉，运维只能看到原始业务异常，无法感知资源可能未释放；与同步版行为不一致增加排障成本。
- **建议**: `whenComplete` 中对 b 非 null 时 addSuppressed 或以 err2 包装抛出；同步版 catch 中 rollback 抛出的异常也应抑制为 suppressed 而非覆盖原异常，统一两版行为。
- **误报排除**: 已通读 `runInTransactionAsync` 全文并逐行比对同步版 `runInTransaction`；已确认 `cleanupTransaction` 挂在外层 future 上、在 rollback/commit 完成后执行，不会兜住 rollback 失败。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。异步版经 `rollbackStage.handle(...)` 在成功/失败两条路径都重抛原始业务异常，rollback 自身的失败作为 suppressed 附加（`err.addSuppressed(b)`）；另补同步抛出防御 catch（ResolvedPromise 内联执行路径）。同步版 rollback/commit-补-rollback 链路的失败同样 suppressed 化（原始异常不再被 rollback 失败覆盖，两版行为统一）。实现注记：`ResolvedPromise.whenComplete` 对已失败 stage 的 action 异常只记日志不替换失败（audit 建议的 whenComplete 方案因此无效）、`exceptionally` 只覆盖失败路径会吞掉成功回滚后的原始异常——handle 是两路径都替换的唯一正确挂点（测试驱动发现，红→修两轮）。回归测试 `TestTransactionTemplateAsync.testAsyncRollbackFailureAttachedAsSuppressed`/`testSyncRollbackFailureAttachedAsSuppressed`（FailingRollbackTxn 注入；stash 红：异步版原始异常被 rollback-fail 覆盖/同步版 suppressed 为空）。
### [P3] JdbcBatcher.flush：prepareStatement 抛 SQLException 时 daoMetrics 计时器不关闭

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/JdbcBatcher.java:{165-167}`
- **维度**: D2
- **证据**:
```java
Object meter = daoMetrics == null ? null : daoMetrics.beginBatchUpdate(sql);
int commandCount = commands.size();
PreparedStatement ps = conn.prepareStatement(sql);   // 在内层 try 之外
JdbcException error = null;
try {
    ...
} finally {
    IoHelper.safeClose(ps);
    if (daoMetrics != null)
        daoMetrics.endBatchUpdate(...);              // prepareStatement 失败时不会执行
```
- **现状**: `beginBatchUpdate` 之后、内层 try 之前执行 `conn.prepareStatement(sql)`，此处抛 SQLException 时走外层 catch 直接抛出，`endBatchUpdate` 不会被调用，`Timer.Sample` 计时丢失（对比 `executeOne` 的 prepareStatement 在 try 内，处理正确）。
- **风险**: 仅度量丢失（Sample 可被 GC，无资源泄漏），偶发 SQL 语法错误时批量指标少计一次。
- **建议**: 将 `conn.prepareStatement(sql)` 移入内层 try（ps 初始化为 null，finally 中 safeClose 判空即可，`IoHelper.safeClose` 本就容忍 null）。
- **误报排除**: 已逐行比对 flush 与 executeOne 的 try 边界差异；已确认外层 `catch (SQLException e)` 直接 translate 抛出，无兜底调用 endBatchUpdate。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。`prepareStatement` 移入内层 try（ps 初始化 null，`IoHelper.safeClose` 容忍 null），finally 恒调 `endBatchUpdate`。回归测试 `testMetricsEndedWhenPrepareFails`（IDaoMetrics 记录桩；stash 红：无 endBatchUpdate）。
### [P3] JdbcTemplateImpl.executeStatement：rs 局部变量为死代码

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:{277,307}`
- **维度**: D1
- **证据**:
```java
ResultSet rs = null;        // 277 行声明后从未赋值（rs 由 JdbcComplexDataSet 内部持有）
...
} finally {
    IoHelper.safeCloseObject(rs);   // 永远 close(null)，无效果
    IoHelper.safeCloseObject(st);
```
- **现状**: 声明的 `rs` 从未被赋值，finally 中的 safeCloseObject(rs) 是无操作；真正的 ResultSet 由 `JdbcComplexDataSet`（其 close 关闭 statement，statement.close 关联关闭 rs）管理，生命周期正确。
- **风险**: 无运行时危害；误导维护者以为 executeStatement 路径存在游离 ResultSet 需要单独关闭。
- **建议**: 删除该局部变量与对应的 safeCloseObject 调用。
- **误报排除**: 已通读 executeStatement 全方法确认 rs 无赋值点；已确认 `JdbcComplexDataSet` 构造时持有 st、finally 中 safeCloseObject(st) 已覆盖资源释放。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。删除 `rs` 局部变量与 finally 中的 `safeCloseObject(rs)`。免测试：纯死代码删除，无行为面。
### [P3] JdbcTemplateImpl.getTableMeta 表名未转义直接拼接 SQL

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:{850-853}`
- **维度**: D5
- **证据**:
```java
public IDataSetMeta getTableMeta(String querySpace, String tableName) {
    SQL sql = SQL.begin().querySpace(querySpace).select().star().from().sql(tableName).where().alwaysFalse().end();
    return executeQuery(sql, IDataSet::getMeta);
}
```
- **现状**: `tableName` 直接拼入 SQL 文本，未走 `dialect.escapeSQLName()`；同类方法 `existsTable`（456-457 行）对表名做了转义，两者不一致。
- **风险**: 调用方（当前为 `JdbcBatchConsumerProvider`，表名来自批量任务配置）若传入含空格/保留字的表名会产生语法错误；作为公共 API 也存在被上层误用于拼接不可信输入的可能。
- **建议**: 与 existsTable 对齐，使用 `dialect.escapeSQLName(tableName)`。
- **误报排除**: 已比对同文件 existsTable 的转义处理；已确认当前唯一外部调用方传入内部配置表名，无现实注入链，故评 P3。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。`getTableMeta` 表名经 `dialect.escapeSQLName()` 转义，与 existsTable 对齐。回归测试 `TestJdbcTemplate.testGetTableMetaEscapesTableName`（H2 建含空格表名 `"MY ENTITY"`；stash 红：旧码拼出非法 SQL 抛 bad-sql-grammar）。附注：不存在表仍抛 bad-sql-grammar——H2 在 prepare 期校验表存在，alwaysFalse 条件无法绕过，现状保留（原审计建议未涉及）。
### [P3] DialectSQLExceptionTranslator：异常翻译路径每次执行 Pattern.compile 且模板未转义正则元字符

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectSQLExceptionTranslator.java:{162-175}`
- **维度**: D6
- **证据**:
```java
if (errorCode == null && current.getErrorCode() == 0 && current.getSQLState() == null) {
    String msg = current.getMessage();
    for (Map.Entry<String, ErrorCode> entry : vendorCodeToErrorCodes.entrySet()) {
        String key = entry.getKey();
        if (key.contains("_")) {
            Pattern regex = Pattern.compile(key.replace('_', ' '), ...);  // 每次翻译都重新编译
            if (regex.matcher(msg).matches()) {
```
- **现状**: 当驱动的异常既无 errorCode 也无 SQLState 时，对每个含下划线的 vendor code key 现场编译正则并做全文匹配（`matches()` 要求整条消息与 key 对应模式完全一致，几乎不可能命中，且 key 中如含 `(`、`)` 等元字符未被 `Pattern.quote` 转义）。
- **风险**: 低频异常路径上的无谓编译开销与几乎恒 false 的匹配；正则元字符未转义导致语义不可预期。
- **建议**: 在构造函数中预编译并 `Pattern.quote` 各 key；或删除该消息匹配分支（`matches()` 全匹配基本不可能命中）。
- **误报排除**: 已读 buildVendorCodeToErrorCodes 确认 key 来自 dialect 模型配置；该分支仅在 errorCode==0 且 SQLState==null 的罕见驱动行为下进入，评 P3。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复（部分采纳建议）。构造函数预编译 `messagePatterns` 消除每次翻译的重复编译；**报告建议的 Pattern.quote 复核后不适用**——duckdb 方言的 errorCode 值 `.+_with_name_.+_does_not_exist.*`（duckdb.dialect.xml:102）本身即正则模式，字面量化会破坏其匹配语义，故保留正则语义仅消除重复编译。回归测试 `TestDialectSQLExceptionTranslator` ×3（红=编译级：旧码无 buildMessagePatterns；含 duckdb 正则语义固化用例）。
### [P3] UnknownEntityException.getEntityId 对非 String 主键强转 ClassCastException

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/exceptions/UnknownEntityException.java:{37-41}`
- **维度**: D1
- **证据**:
```java
public UnknownEntityException(String entityName, Object entityId) {
    super(ERR_DAO_UNKNOWN_ENTITY);
    param(ARG_ENTITY_NAME, entityName).param(ARG_ENTITY_ID, entityId);   // entityId 为 Object
}
public String getEntityId() {
    return (String) getParam(ARG_ENTITY_ID);                              // 强转 String
}
```
- **现状**: 构造器接受 Object 类型主键（数值/复合主键均常见，`IEntityDao.requireEntityById(Object id)` 直接透传），getter 却强转 String。
- **风险**: 数值主键实体在异常处理代码中调用 getEntityId() 时抛 ClassCastException，掩盖原始异常。
- **建议**: 返回类型改为 Object，或用 `StringHelper.toString`。
- **误报排除**: 已核对 `IEntityDao.requireEntityById(Object id)` 与 `loadEntityById(Object id)` 的参数类型，确认数值主键是平台常规用法。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。`getEntityId()` 返回 `entityId.toString()`（null 安全）。回归测试 `TestUnknownEntityException` ×4（stash 红：数值主键 ClassCastException）。
### [P3] SnowflakeSequenceGeneator 类名拼写错误（Geneator）

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/seq/SnowflakeSequenceGeneator.java:{35}`
- **维度**: D8
- **证据**:
```java
public class SnowflakeSequenceGeneator implements ISequenceGenerator {
```
- **现状**: 类名缺少 r（应为 Generator），全仓库引用方均需随之拼写错误，跨模块检索时易漏。
- **风险**: 无运行时危害；可维护性与检索性问题。
- **建议**: 重命名并保留旧名过时别名或直接全量替换（引用点少）。
- **误报排除**: 已全仓库 grep 确认该类名仅以当前拼写出现。另已验证其 `MathHelper.secureRandom()` 为懒加载单例（非每次新建 SecureRandom），无热路径性能问题；synchronized 生成与时钟回拨处理正确。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。全量重命名 `SnowflakeSequenceGenerator`（类/构造器/logger），同步更新唯一代码引用方 `nop-sys-dao SysSequenceGenerator` 与 `docs-for-ai/03-runbooks/generate-business-code.md` 提及。免新增测试：纯重命名，编译 + 既有测试即覆盖（nop-sys-dao 编译验证见处置日志）。
### [P3] DialectImpl.escapeSQLName 对空字符串抛 StringIndexOutOfBoundsException

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java:{418-426}`
- **维度**: D1
- **证据**:
```java
public String escapeSQLName(String name) {
    String rename = renameMap.get(name);
    if (rename != null)
        return rename;
    if(name.charAt(0) == '"'){        // name 为空串时越界
```
- **现状**: 未做空串/blank 防御，空串直接抛 StringIndexOutOfBoundsException 而非带上下文的 NopException。
- **风险**: 上层若以空列名/表名调用（元数据动态生成场景），得到裸数组越界异常难以定位。当前调用方一般先经模型校验，触发面窄。
- **建议**: 入口加 `StringHelper.isEmpty` 校验并抛带参数名的异常。
- **误报排除**: 已核对 renameMap.get(null) 行为（CaseInsensitiveMap 一般容忍 null key，未进一步依赖）；主要风险点 charAt(0) 已确认无守卫。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。入口 `StringHelper.isEmpty` 守卫，抛新错误码 `ERR_DIALECT_INVALID_SQL_NAME`（DaoErrors 常量定义，带 name 参数；与模块既有错误码同形态）。回归测试 `TestDialectWriteIndex.testEscapeSQLNameEmptyRejected`（stash 红：StringIndexOutOfBoundsException）。
### [P3] AbstractTransaction.listeners 非线程安全，异步事务下存在竞态

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/AbstractTransaction.java:{53,77-99}`
- **维度**: D3
- **证据**:
```java
private List<ITransactionListener> listeners;          // 普通 ArrayList

public void addListener(ITransactionListener listener) {
    if (listeners == null)
        listeners = new ArrayList<>();
    if (!listeners.contains(listener)) {
        listeners.add(listener);
        ...
```
- **现状**: `invokeListener` 通过 `new ArrayList<>(listeners)` 复制规避了遍历中的修改，但 `addListener/removeListener` 本身无同步；`commitAsync/rollbackAsync`（doCommitAsync 走 `FutureHelper.futureRun` 在其它线程执行）路径上，若异步回调线程与注册线程并发操作 listeners，存在丢失更新或 ArrayList 扩容竞态。
- **风险**: 常规同步事务按上下文单线程使用无问题；`runInTransactionAsync` 链路中 listener 回调发生在 executor 线程，若业务在 future 回调里再 addListener（如 metricsListener 延迟注册）可能竞态。概率低。
- **建议**: 改用 CopyOnWriteArrayList（listener 数量小，写少读多，代价可忽略）。
- **误报排除**: 已通读 AbstractTransaction 全部 listener 相关方法与 commitAsync/rollbackAsync 线程切换点（`thenOnContext`/`futureRun`），确认异步路径确实跨线程调用 listener。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。`listeners` 改 `CopyOnWriteArrayList`（懒初始化保留），`invokeListener` 直接遍历快照迭代器（防御性拷贝移除）。回归测试 `TestAbstractTransactionListeners` ×4（stash 红×2：容器类型断言 + 回调中并发注册行为）。
### [P3] AbstractPaginationHandler.prepareStatement 将 long limit 强转 int，超界时语义错误

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/pagination/AbstractPaginationHandler.java:{29-34}`
- **维度**: D1
- **证据**:
```java
public void prepareStatement(LongRangeBean bounds, PreparedStatement ps) throws SQLException {
    if (bounds.getLimit() > 0) {
        long limit = bounds.getLimit();
        ps.setMaxRows((int) limit);      // long -> int 截断
    }
}
```
- **现状**: `LongRangeBean.getLimit()` 为 long（可用 `Long.MAX_VALUE` 表示"取全部"），强转 int 溢出为负值后 `setMaxRows` 语义错误。
- **风险**: 当前 nop-dao 内无调用方（4 参 `JdbcHelper.prepareStatement(dialect, conn, sql, range)` 在本模块与 batch-jdbc 中均未被调用，分页走 SQL 改写路径），属潜伏缺陷。
- **建议**: `limit > Integer.MAX_VALUE` 时不设置 maxRows 或钳制到 Integer.MAX_VALUE。
- **误报排除**: 已全仓库 grep 4 参 `JdbcHelper.prepareStatement` 调用点（仅 batch-jdbc 使用 3 参版本），确认无现实触发路径，评 P3。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。`limit > Integer.MAX_VALUE` 时不设置 maxRows（Long.MAX_VALUE = 取全部 = 不限制；避免强转溢出为负）。回归测试 `TestAbstractPaginationHandler` ×4（stash 红：Long.MAX_VALUE 被 `setMaxRows(-1)`）。
### [P3] IJdbcTemplate.callFunc 实现语义与命名不符：返回 update count 而非函数结果

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:{778-807}`
- **维度**: D8
- **证据**:
```java
public Object callFunc(@Nonnull SQL sql) {
    Object ret = runWithConnection("jdbc.callFunc", sql, null, conn -> {
        ...
        st = JdbcHelper.prepareCallableStatement(dialect, conn, sql);  // 注册了 OUT 参数
        ...
        count = st.executeUpdate();       // 却执行 update 并返回 count
        ...
        return count;
```
- **现状**: `prepareCallableStatement` 注册了 `registerOutParameter(1, Types.VARCHAR)`，暗示预期读取 OUT 参数，但实现执行 `executeUpdate()` 并返回更新计数，从未读取 OUT 值；接口 `Object callFunc(SQL)` 也无 javadoc 说明契约。
- **风险**: 全仓库无调用方，属潜伏契约漂移；一旦被使用，调用方按命名预期拿到的是计数而非函数返回值。
- **建议**: 明确契约——要么实现 `execute()` + `getString(1)` 返回 OUT 值，要么改名/加文档说明返回 update count。
- **误报排除**: 已读 `prepareCallableStatement`（JdbcHelper.java:149-161，确实注册 OUT 参数）；全仓库 grep `callFunc` 确认无外部调用方（SQL.SqlBuilder 中的同名方法是另一语义）。

> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。实现改为 `st.execute()` + `st.getObject(1)` 读取 OUT 参数（prepareCallableStatement 已注册），接口 javadoc 显式化 `{? = call func(...)}` 契约。回归测试 `TestJdbcTemplate.testCallFuncReturnsOutParamValue`（H2 CREATE ALIAS 函数；stash 红：旧码返回计数/null）。实现注记：H2 函数调用经 execute() 恒返回 ResultSet 形态，OUT 参数是唯一稳定的结果载体（初版按 execute() 返回值分流会得 null，由该测试驱动修正）。
## 其它已检查未见问题的重点区域（覆盖范围说明）

- **事务模板传播语义**（REQUIRED/SUPPORTS/MANDATORY/REQUIRES_NEW/NOT_SUPPORTED/NEVER）与 TxnState 的 prevTxn 恢复、组事务（getMainTxnGroup/addSubTransaction）路径逐分支走查，未发现注册泄漏或恢复错乱；`runInTransaction` 在非回滚异常时提交部分写入是与 Nop `shouldRollback` 语义一致的设计决策。
- **查询缓存**（JdbcTemplateImpl.getCacheData/saveCacheData）：sql 文本与 range 双重校验、cacheMap 用 ConcurrentHashMap、noCacheProviderWarned 用 AtomicBoolean 防日志风暴，未见问题。
- **DbEstimatedClock**：fetching 标志 + synchronized + volatile 的并发刷新协议走查（含 data==null 首次并发、DB 查询持锁期间其他线程用旧缓存的降级），未见错误；TimeData.elapsedTime 的非原子写仅影响过期判断精度，无害。
- **D7 平台规范**：全模块仅 4 处 `@Inject` 且全部为 setter 注入（无 private 字段注入）、无 Spring `@Value`/`@Autowired`；核心 bean（DefaultTransactionManager/TransactionTemplateImpl/JdbcTemplateImpl/DaoMetricsImpl）均在 `_vfs/nop/dao/beans/dao-defaults.beans.xml` 注册，`nopDaoMetrics` 同时实现 IDaoMetrics/ITransactionMetrics（接口继承已验证），注入类型匹配。
- **D5 安全**：existsTable 模板渲染经 `dialect.getStringLiteral` 转义；参数绑定全部走 PreparedStatement set；`escapeSQLName` 的引号处理正确；未见敏感信息日志输出。
- **RowMapper/DataSet 读取路径**：JdbcDataSet 的 wasNull 处理、Clob/Blob 流读取（jdbcGet/jdbcGetString）、hasNext 缓存状态机均正确。
