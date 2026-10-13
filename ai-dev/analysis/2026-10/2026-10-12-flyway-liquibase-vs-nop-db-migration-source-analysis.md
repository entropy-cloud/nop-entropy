# Flyway / Liquibase 源码级分析与 nop-db-migration 三方缺口对比

> Status: resolved
> Date: 2026-10-12
> Scope: flyway 13.10.0（`~/sources/flyway`，commit `b4251cdbec1a`，Apache-2.0）、liquibase main（`~/sources/liquibase`，commit `f58ccf20b261`，FSL-1.1-ALv2，≈5.0.x）vs nop 侧 `nop-persistence/{nop-db-migration,nop-dbtool}` + `nop-persistence/nop-orm` 的 schema 初始化路径
> Conclusion: **未达到"超过"，且当前连"可用"都未达到**。nop-db-migration 在概念模型（声明式 DSL + checksum + contexts/labels + precondition + 显式 rollback）上不输任一方，但在**工程完成度**上落后一个量级：无锁、无事务、无 dry-run、无 validate/info/baseline/repair、无入口接线、仅 H2 实测。反向看，源码分析推翻了两条流行认知——**Flyway OSS 仓库零测试，且 dry-run/undo/diff 全部是商业版空壳**，Liquibase Community 在功能面上反而领先 Flyway Community；nop 的历史表主键设计与"平台内嵌免进程"路线则优于两者。

## Context

- 要回答的问题：nop 的 DB 版本迁移能力与 Flyway / Liquibase 相比还差什么？是否可能超过？
- 为什么要看源码：本轮之前基于公开特性矩阵的对比停留在"功能打勾"层面，无法回答"这个能力是真的实现了、还是只在文档里"。本次把两个仓库 clone 到 `~/sources`，逐条落到 `file:line`。
- 约束：只做分析，不改产品代码。报告是决策参考（analysis 定位），不是 source of truth。

## 方法与取证

| 项 | 值 |
|---|---|
| flyway | `~/sources/flyway`，`b4251cdbec1a6c8ec6b15c9f3f99976d7b8ef251`，2026-10-08 "Bump version to flyway-13.10.0"，801 个 main Java 文件 / 82,911 LOC |
| liquibase | `~/sources/liquibase`，`f58ccf20b2610fd04ebcabc943842f1d6484402d`，2026-10-09 "Fix concurrent updates leaving each other's changelog locks set…"，1,495 个 Java 文件 / 165,022 LOC（`liquibase-standard` 147,526 LOC；`liquibase-core` 为空聚合模块） |
| nop | `nop-db-migration` main 10,560 LOC；`nop-dbtool` main 1,832 LOC |
| 取证方式 | 两个并行探索子代理分别对 flyway / liquibase 做全模块盘点；关键结论由本人对三端 live code 抽查核实（抽查点见 References） |
| 已知边界 | 两者均为 shallow clone（`--depth 1`），不做 git 历史演进分析；flyway OSS 仓库不含商业版源码 |

## 定位差异：三者不是同类软件

直接逐功能打勾会误导，先澄清比较基准：

- **Flyway** 是"脚本优先"引擎：迁移 = 按命名约定放置的**纯 SQL 文件**（`V1__desc.sql` / `R__desc.sql`），工具只负责按版本号顺序执行 + 记历史 + 校验。没有抽象变更模型，也就没有"跨库方言翻译"。
- **Liquibase** 是"变更模型优先"引擎：迁移 = changelog 里的 changeset，用与数据库无关的变更类型（`createTable` / `addColumn` …）描述，运行期由 108 个 `SqlGenerator` 按目标库渲染成 SQL。因此天然带 diff/generate、多格式（XML/YAML/JSON/formatted SQL）、precondition、rollback 自动生成。
- **nop-db-migration** 走的是 Liquibase 路线（`migration.xdef` 声明式模型 + 16 个 executor），但**嵌入 Nop 平台内部**，复用 `IJdbcTemplate` / `IDialect` / `NopException` / VFS / XDSL，没有独立进程、没有 CLI。
- nop 侧真正的差异化不在 db-migration，而在**模型驱动**：`DataBaseSchemaInitializer`（ORM 模型 → 建表）、`nop-dbtool` `OrmDbDiffer`（模型 vs 库 diff → 安全 ALTER）。这是 Flyway / Liquibase 都没有的一条路线（Liquibase 的 `diff-changelog` 需要连两个库，Flyway 的 `diff/model/deploy` 是商业版）。

---

## 一、flyway 13.10.0 源码事实

### 1.1 仓库形态（会改变很多人的认知）

- **OSS 仓库零测试**。`find . -type d -name test -path "*/src/*"` 结果为 0；`flyway-core/src/` 只有 `main`。CI（`.github/workflows/build-pr.yml:13-19`）是 `{ubuntu,macos,windows} × {java17}` 的 6 格**纯构建矩阵**；`build-release.yml` 额外跑一次 `flyway info` 对内存 H2 的冒烟。`pom.xml:183` 声明了 testcontainers 版本但没有任何模块真正引用。
- 因此**不要把"大厂工程质量"算在 flyway OSS 的可见代码上**——它的质量信号来自商业版私有测试矩阵和用户量。
- License 全量 Apache-2.0（`LICENSE.md`），Java 17（`pom.xml:770`），Maven + Lombok。

### 1.2 入口与执行链

`Flyway.java:166` `migrate()` → 查 verb extension → `internal/command/DbMigrate.java`。同门面还有 `info`(`:267`) / `clean`(`:302`) / `validate`(`:367`) / `baseline`(`:402`) / `repair`(`:451`) / `undo`(`:491`)。

执行粒度：**文件内逐语句**。`internal/parser/Parser.java`（826 行）手写 SQL 切分器，`DefaultSqlScriptExecutor` 逐条 `executeUpdate`。**这是 Flyway 自己承认的弱点**——`Parser.java:321` 直接挂出 `"Known parser limitations: " + KNOWN_PARSER_LIMITATIONS` 链接。

### 1.3 锁（OSS 真材实料，最容易被低估的一项）

三层：

1. 方言 advisory lock：PostgreSQL `pg_advisory_lock`（`PostgreSQLAdvisoryLockTemplate.java:37`）、MySQL `GET_LOCK`（`MySQLNamedLockTemplate.java:34`）、SQL Server `sp_getapplock`（`SQLServerApplicationLockTemplate.java:34`）。
2. 通用表锁：`base/Table.java:96 lock()` / `:115 abstract doLock()`，23 个适配器各自实现。
3. 兜底 `InsertRowLock.java`：往 `flyway_schema_history` 插一条 `installed_rank = -100` 的哨兵行，靠主键保证互斥（`InsertRowLock.java:44` javadoc 原文），**且带 10 分钟过期自愈**（`LOCK_TIMEOUT_MINS = 10`，`InsertRowLock.java:61`）。

重试预算：`RetryStrategy` 默认 50 次（`FlywayExecutor.java` 调 `setNumberOfRetries(configuration.getLockRetryCount())`，默认值见 `FlywayModel.java:90`）。`DbMigrate` 在 `:108` 用 `schemaHistory.lock(this::migrateAll)` 把整个迁移包在锁里。

### 1.4 事务

判定栈：`MigrationExecutor.canExecuteInTransaction()`（脚本由 Parser 推导，`Parser.java:482`）→ `DbMigrate.isExecuteGroupInTransaction`（组内 AND，任一非事务则整组非事务，`mixed=true` 时直接报错）→ `Database.supportsDdlTransactions()`（`Database.java:226`）→ `Configuration.isExecuteInTransaction()`。

机制是标准 Spring 式模板（`TransactionalExecutionTemplate.java:55-97`），**但有一个容易忽略的细节**：非事务组失败时仍执行 `connection.commit()`（`:82-88` 分支），因为历史表那一行必须留下来。

失败语义在 `DbMigrate.java:341-358`：只有 `supportsDdlTransactions() && executeGroupInTransaction` 才回滚，否则写 `success=false` 行并打印 "Please restore backups and roll back database and code!"。

### 1.5 历史表与 checksum

`JdbcTableSchemaHistory.java`（**没有 `JdbcSchemaHistory` 这个类**）。10 列、`installed_rank` 主键、**没有 dirty 列**（"dirty" 在全部 Java 源码中零命中）。建表本身在锁内做，且带 10 次重试自愈（`:112-156`）。

checksum：`ChecksumCalculator.java`，**CRC32**，逐行读取以做到编码/换行符无关（`:32-45`），存 `checksum INT`。**没有版本化 checksum 方案**——同一个算法从古用到今。

### 1.6 validate 与状态机

`MigrationInfoImpl.validate()`（`:200-290`）逐条比对，错误码集中在 `api/CoreErrorCode.java:24-53`（29 个）。状态机 `api/MigrationState.java` 约 19 个状态。**out-of-order 的全部实现就是一行**（`ResolvedMigration.java:90-91`，已抽查核实）：

```java
if ((getVersion().compareTo(context.lastApplied) < 0) && !context.outOfOrder) {
    return MigrationState.IGNORED;
}
```

即默认下"晚到的低版本"被**静默标记为 IGNORED**，不报错、不执行、不校验。

### 1.7 OSS 与商业版的边界（对本次对比极其关键）

`flyway-core/.../internal/proprietaryStubs/` 是一组**空壳**：`DryRunSupportStub`（已抽查，`resolveOutputStream` 直接 `return null`，`getPriority() = -100`）、`UndoCommandExtensionStub`、`DiffCommandExtensionStub`、`GenerateCommandExtensionStub`、`ModelCommandExtensionStub`、`PrepareCommandExtensionStub`、`DeployCommandExtensionStub`、`CheckCommandExtensionStub` 等。

结论：**Flyway Community 没有 dry-run、没有 undo、没有 diff/generate/deploy/model**。`StatementInterceptor` 这个拦截接缝虽是 public 的，但 OSS 树内零实现。

### 1.8 扩展与配置

`java.util.ServiceLoader` + `PluginRegister`（`:190-191`）。扩展点 14 个（`extensibility/` 下）。配置面 `api/configuration/Configuration.java` 677 行 / 约 70 个 accessor；`CoreConfigurationParameters` 列了 55 个参数。Callback 有 **48 个事件**（`api/callback/Event.java`）。

---

## 二、liquibase（main ≈ 5.0.x）源码事实

### 2.1 仓库形态

`liquibase-core` 是空聚合，全部代码在 `liquibase-standard`（147,526 LOC）。测试规模与 flyway 形成刺眼对比：**`liquibase-standard/src/test` 242 个 Java 测试文件，`liquibase-integration-tests` 46 个**（全仓 `*Test.java` 251 个）。CI（`.github/workflows/ci-test.yml`）跑 `{ubuntu,macos,windows} × {JDK 17/21/25}` 单测矩阵 + 按 `testSystem` 展开的集成测试矩阵（Cockroach/DB2/Derby/Firebird/H2/Hsql/Informix/MariaDB/MSSQL/MySQL/Oracle/Postgres/Snowflake/SQLite/SybaseASA 等，由 `liquibase-extension-testing` 提供 TestSystem 骨架）。License 5.x 起为 FSL-1.1-ALv2（两年后转 Apache-2.0）。

### 2.2 入口与执行链

`Liquibase.update()`（`:154`）→ `CommandScope("update")` → `AbstractUpdateCommandStep.run()`（`:67`），顺序是：

1. `FastCheckService.isUpToDate(...)` 短路（`:103`）——已是最新的库直接返回，不碰锁
2. `LockServiceFactory...getLockService(database).waitForLock()`（`:105`）
3. `ChangeLogIterator` + `UpdateVisitor` + `ShouldRunChangeSetFilter` 驱动执行（`:127`）
4. `finally` 里 `if (lockService.hasChangeLogLock()) lockService.releaseLock();`（`:158-163`）

### 2.3 锁

`StandardLockService.java`（544 行，**没有 `JdbcLockService` 这个类**）。`DATABASECHANGELOGLOCK` 只有三列：`LOCKED` / `LOCKGRANTED` / `LOCKEDBY`（`CreateDatabaseChangeLogLockTableGenerator.java:29-31`）。

`waitForLock()`（`:257-300`，已抽查）：默认等 **5 分钟**、每 **10 秒**轮询一次（`GlobalConfiguration.java:86-96`），超时抛 `LockException` 并报出持锁者。`acquireLock()`（`:302-367`）是"先 SELECT LOCKED，再 UPDATE 抢占"，`rowsUpdated == 0` 判定"别人更快"。

值得注意：**HEAD commit 本身就是锁 bug 修复**——`LockServiceFactory.resetAll()` 会把并发场景下别的数据库的锁服务单例一起丢掉，导致它们无法释放自己持有的锁；修复方式是新增 `resetDatabase(Database)`，调用点改为 `AbstractUpdateCommandStep.java:213`、`LockServiceCommandStep.java:66-69`（后者带解释注释）。**锁的正确性到 2026-10-09 还在修**，这一事实对"自研锁是否足够简单"的判断是重要参照。

锁表本身**没有过期自愈**（对比 Flyway 的 `LOCK_TIMEOUT_MINS = 10`），需要 `releaseLocks` 命令人工清理。

### 2.4 历史表

`CreateDatabaseChangeLogTableGenerator.java:52-62`（已抽查核实），14 列：

```
ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE,
MD5SUM(35), DESCRIPTION(255), COMMENTS(255), TAG(255),
LIQUIBASE, CONTEXTS, LABELS, DEPLOYMENT_ID
```

**既无主键也无唯一约束**——与 Flyway（`installed_rank` PK）和 nop（`version` PK）都不同，去重完全靠应用层。`StandardChangeLogHistoryService.init()`（`:111-302`）会自动 ALTER 补齐历史版本缺失的列。`ExecType` 五值：`EXECUTED/FAILED/SKIPPED/RERAN/MARK_RAN`（`ChangeSet.java:74-90`）。

### 2.5 checksum 与 validate

`ChangeSet.generateCheckSum(ChecksumVersion)`（`:397-418`）：MD5，格式 `version:md5`，**有版本化方案 `ChecksumVersion` V1..V9**（当前根 scope 钉在 V9，`Scope.java:171-172`）。逃生舱 `validCheckSums`（`:187`、`isCheckSumValid :1435-1459`）支持 `1:any` / `1:all` / `1:*` 通配——这是运维救回"改了已执行 changeset"场景的关键机制，Flyway 只有 `repair`。

validate 由 `ValidatingVisitor` 实现，**固定 9 个桶**：invalidMD5Sums / failedPreconditions / errorPreconditions / duplicateChangeSets / setupExceptions / changeValidationExceptions / validationErrors / warnings / duplicatesFromDifferentFiles。

### 2.6 rollback（三者中最强）

- 默认路径 `AbstractChange.generateRollbackStatements`（`:524-526`）走 `createInverses()` 生成逆操作，**20 个文件覆盖了 `createInverses`**（本人 `grep -rl` 计数，含基类声明）。
- **不支持自动生成**、必须手写 `<rollback>`：`DropTableChange`、`InsertDataChange`（均未 override）。
- 命令面完整：`rollback`（按 tag）/ `rollbackCount` / `rollbackToDate` + 各自的 `*Sql` 变体、`futureRollbackSql`、`updateTestingRollback`（先跑再回滚，等于自带 dry-run 回归）。

### 2.7 precondition（三者中最强）

`META-INF/services/liquibase.precondition.Precondition` **21 项**：`And/Or/Not` 嵌套 + `TableExists/ColumnExists/IndexExists/PrimaryKeyExists/UniqueConstraintExists/ForeignKeyExists/ViewExists/SequenceExists/TableIsEmpty/RowCount/Sql/DBMS/ChangeSetExecuted/ChangeLogPropertyDefined/RunningAs/ObjectQuotingStrategy/CustomPreconditionWrapper/FailedPrecondition/ErrorPrecondition/PreconditionContainer`。

`onFail` / `onError` 四值 `HALT/CONTINUE/MARK_RAN/WARN`（`PreconditionContainer.java:27-61`），其中 **`MARK_RAN` 会写一条历史行**（`ChangeSet.java:796-800`）——条件不满足时"记录为已跑过"，这是跳过型语义的正确实现方式。

### 2.8 diff / snapshot / dry-run

- `LoggingExecutor`（334 行，已抽查）用 `updatesDatabase() == false` 这一个标志同时驱动 precondition 的 `onUpdateSQL` 语义和 change 抑制（`LoggingExecutor.java:310`），`updateSql` 免费提供。
- `DiffGenerator` / `SnapshotGenerator` 双 SPI；`SnapshotControl` 默认 12 种 `DatabaseObject`。
- **商业门槛以调用点布尔检查散布在 OSS 代码里**：`LicenseServiceUtils.isProLicenseValid()` 出现在 `DiffCommandStep.java:108`（JSON 输出）、`InternalSnapshotCommandStep.java:122`、`StoredDatabaseLogic.java:18`（**存储过程不进 OSS snapshot**）等。formatted SQL 里 `rollbackSqlFile` / `tagDatabase` / `include file:` / `includeAll` 四种写法在 OSS 直接被拒（`InvalidFormattedSqlPatternsForOssUtil.java:18-47`）。
- **没有 policy 引擎**（全仓零命中），营销里的 "policy checks" 只在 `ProCommandsRegistry.java:25-37` 的命令名列表里露了个名字。

### 2.9 其他

- 命令面 **42 个内置命令**，`CommandStep` 经 ServiceLoader 发现（由 `eu.somatik.serviceloader-maven-plugin` 在构建期生成 services 文件）。
- 47 个 `Change` + **108 个 `SqlGenerator`** + 25 个 `DataType`，全部按 priority 链式选择。
- `Scope` / `ScopeManager` 提供线程级上下文（`Scope.java` 664 行），配置经 `ConfigurationValueProvider` SPI 分层解析。
- 已知 TODO 不少：`ChangeSet.java:653` 的 `serialize()` 直接 `throw new RuntimeException("TODO")`；`ConstraintsConfig.java:416` 同款。

---

## 三、nop-db-migration 现状（对 live code 复核）

以下结论全部在本轮重新核实，与 `ai-dev/audits/check/db-migration.md`（2026-08-19 审计、2026-08-22 处置）的遗留项一致。

| 事实 | 证据 |
|---|---|
| 无生产调用方 | 全仓 grep `MigrationEngine` / `nopMigrationEngine` / `DbMigrationInitializer`，`nop-db-migration` 目录外零命中；无任何 pom 依赖该模块（仅 `nop-persistence/pom.xml:23` 的 module 声明） |
| 启动器是空的 | `initialize/DbMigrationInitializer.java:20-22` `initialize()` 方法体为空 |
| 无锁 | 模块内无任何 lock 相关代码 |
| 无事务 | 模块内无 `transact` / `begin` / `commit`；`IJdbcTemplate.executeUpdate` 走 `SUPPORTS` 传播，无环境事务时即 autocommit |
| 无 dry-run / SQL 预览 | `IChangeExecutor` 无输出变体 |
| 无 baseline / repair / out-of-order / 缺失检测 | `migrate()`（`MigrationEngine.java:123-191`）只遍历文件侧，不反查"库里有而文件没有"的版本 |
| validate 只有 checksum 一项 | `MigrationEngine.java:164-185` 内联实现，`ERR_DB_MIGRATION_CHECKSUM_MISMATCH` |
| 历史表 8 列、`version` 主键、**无 `error_message`** | `MigrationHistoryManager.buildCreateHistoryTableSQL` |
| upsert 是两条非原子语句 | `recordMigration`：先 UPDATE，`updated == 0` 再 INSERT |
| checksum = MD5(version + description + Σ(type + JsonTool.stringify(change))) | `MigrationEngine.calculateChecksum`（`MigrationEngine.java:315-334`） |
| rollback 仅支持显式 `<rollback>` 块 | `MigrationEngine.java:336-405`；`generateRollbackSql` 在 main 代码中零调用方 |
| `runOn` 只识别 `NEVER` | `MigrationEngine.java:154`；`ALWAYS` / `ON_CHANGE` 无行为 |
| `executeMark` 声明无实现 | `migration.xdef:438` 声明，`registerDefaultExecutors` 无对应项，type=null 被静默跳过 |
| contexts 匹配是单串全等 | `MigrationContext.java:114-119`；labels 却是逗号 OR（`MigrationEngine.java:229-240`），**两者语义不一致** |
| 扫描只认 `.migration.xml` 且不递归 | `MigrationFileScanner.java:58`、`:115-134` |
| 16 个 executor / 5 个 precondition | `registerDefaultExecutors` / `registerDefaultPreconditionCheckers`（已逐行核对） |
| 方言族只认 6 类 | `executor/DdlSyntax.java:43-71`（mysql/postgres/oracle/mssql/h2/other）——注意这**不是** nop-dao 的限制：nop-dao 有 19 个 dialect.xml / ~70 方法 `IDialect`，db-migration 却只消费了 `escapeSQLName`、`stdToNativeSqlType`、`getName` 三个方法，并用 `getName().toLowerCase()` 字符串匹配绕开了 `DialectModel.features` |
| 测试 | 10 个测试类，**仅 H2**（`AbstractMigrationTestCase`），无 CI job |

nop 侧另外两条 schema 演进路径（与 db-migration **互不知晓**）：

- `nop-orm` `initialize/DataBaseSchemaInitializer.java`：按 ORM 模型建缺失表 + 索引，分片感知，**只 CREATE 不 ALTER**。
- `nop-dbtool` `DataBaseUpgrader.java`（1,832 LOC 模块）：`OrmDbDiffer` 比对模型与库 → 生成 ALTER，**拒绝 drop**（类注释明写），由 `nop.orm.db-differ.auto-upgrade-database` 条件装配启用（`dbtool-defaults.beans.xml`），**无并发锁**（审计 P1 已裁定暂缓立项）。

---

## 四、12 维度三方矩阵

判定：✅ 领先 / 🟡 可用但有边界 / ❌ 缺失。所有带 `file:line` 的格子均为本人或子代理打开核实。

| # | 维度 | Flyway 13.10.0 | Liquibase ≈5.0.x | nop-db-migration | 赢家 |
|---|---|---|---|---|---|
| 1 | **并发锁** | ✅ 三层：方言 advisory lock（`pg_advisory_lock` / `GET_LOCK` / `sp_getapplock`）+ 表锁 + 兜底 `InsertRowLock`（`installed_rank=-100` 主键互斥、10 分钟过期自愈，`InsertRowLock.java:44,61`）；重试默认 50 | 🟡 专用 `DATABASECHANGELOGLOCK`，默认等 5min / 轮询 10s（`StandardLockService.java:257-300`）；**无过期自愈**，且 HEAD commit（2026-10-09）仍在修并发释放 bug | ❌ 零锁代码 | **Flyway** |
| 2 | **事务原子性** | 🟡 四层判定栈（脚本/组/库/配置），支持 DDL 事务的库上 per-migration 原子；不支持则写 `success=false` 并要求恢复备份（`DbMigrate.java:341-358`） | 🟡 `runInTransaction` per-changeset（`ChangeSet.java:192,756`），默认开；DDL 非事务库上同样无解 | ❌ 零事务代码，多语句变更可半途残留 | 平手（都是"尽力而为"） |
| 3 | **validate / checksum / repair** | 🟡 CRC32（`ChecksumCalculator.java`），19 态状态机 + 29 错误码；`repair` 可修；**无 validCheckSum 逃生舱** | ✅ MD5 + **版本化 `ChecksumVersion` V1..V9** + `validCheckSums` 通配逃生舱（`ChangeSet.java:1435-1459`）；`ValidatingVisitor` 9 桶；`clearChecksums` | 🟡 MD5 全文序列化（内容覆盖面不输）；但只有 checksum 一项检查，无 repair / clearChecksums | **Liquibase** |
| 4 | **out-of-order / baseline / 缺失检测** | ✅ 三者齐备：`outOfOrder` 开关（默认 `IGNORED`，`ResolvedMigration.java:90-91`）、`baseline` + `baselineOnMigrate`、`APPLIED_*_NOT_RESOLVED` 检测"库里有文件没有" | ✅ `changelog-sync` 相当于 baseline；`UnexpectedChangesetsCommandStep` 单独检测意外 changeset；顺序由 changelog 位置决定，无 out-of-order 概念 | ❌ 四项全无；后补的低版本会被**静默执行** | **Flyway / Liquibase** |
| 5 | **dry-run / SQL 预览** | ❌ **商业版空壳**：`DryRunSupportStub.resolveOutputStream` 返回 `null`（`DryRunSupportStub.java:26-36`） | ✅ `LoggingExecutor.updatesDatabase()==false`（`LoggingExecutor.java:310`），`updateSql` / `rollbackSql` / `futureRollbackSql` / `updateTestingRollback` 全免费 | ❌ 无 | **Liquibase** |
| 6 | **rollback 自动生成** | ❌ OSS 无 undo（`UndoCommandExtensionStub`）；`generateRollbackSql` 一类接口不存在 | ✅ **20 个 `createInverses()` 覆盖**（含 createTable / addColumn / renameTable / createIndex …）；`dropTable` / `insert` 需显式 `<rollback>`；按 tag / count / date 回滚 | 🟡 只支持显式 `<rollback>` 块；`generateRollbackSql` 有零星实现但**零调用方**（`IChangeExecutor.java:11`） | **Liquibase** |
| 7 | **precondition** | ❌ 无 precondition 概念（只有 callback 事件） | ✅ **21 个内置 + and/or/not 嵌套 + `onFail`/`onError` 四值 + `MARK_RAN` 写历史**（`PreconditionContainer.java:27-61`） | 🟡 5 个平铺 checker，无嵌套无 onFail；**未知 precondition 静默跳过**（`MigrationEngine.java:281-283`） | **Liquibase** |
| 8 | **多库方言 SPI** | ✅ `DatabaseType` ServiceLoader + 23 个适配器模块（`flyway-database/*`，合计 21.8k LOC） | ✅ `SqlGenerator` SPI 108 个 + `DataType` 25 个，priority 链式选择；60+ 库 | ⚠️ **底座是三家里最强的，但 db-migration 只接了 3 个方法**：底座 = `IDialect`（`IDialect.java`，423 行 / ~70 方法）+ 19 个 `dialect.xml`（XDSL `x:extends` 组合，按 JDBC URL / 产品名自动选）+ `DialectFeatures` 33 个 `supportXxx` 位 + 独立的 `DdlSqlCreator` + 9 个 `ddl_*.xlib` 方言 DDL 模板；**实际消费面**：`escapeSQLName`（多数 executor）、`stdToNativeSqlType`（仅 3 处：`MigrationHistoryManager.java:226`、`AddColumnExecutor.java:122`、`AlterColumnExecutor.java:75`）、`getName`（3 处），**字面量方法 / `isSupport*` / `getDropTableSql` / `getCreateSequenceSql` / `isReservedKeyword` 全部未用**；且 `DdlSyntax.java:49-71` 用 `dialect.getName().toLowerCase()` 硬编码 6 族，**绕开 `DialectModel.features` 重新发明 mini-dialect**；**仅 H2 实测** | **Flyway / Liquibase**（生产验证的广度）；*但论方言抽象的完备度，nop 底座第一* |
| 9 | **脚本发现与多格式** | 🟡 5 类 resolver（Java / SQL / OS 脚本 / Baseline / 自定义），但**只有 `.sql` 一种数据格式**，命名约定严格 | ✅ XML / YAML / JSON / formatted SQL 四格式 + `include` / `includeAll`（深度/过滤/`minDepth`/`maxDepth`/`endsWithFilter`） | 🟡 仅 `.migration.xml`；XDSL 理论上可扩 JSON/YAML/XLSX 但扫描器只认一个后缀；`scanDirectory` 不递归（`:115-134`） | **Liquibase** |
| 10 | **历史表 schema** | 🟡 10 列，`installed_rank` PK（顺带兼作锁行锚点）；**无 `error_message`**，失败原因不留痕 | ❌ 14 列，**无主键无唯一约束**（`CreateDatabaseChangeLogTableGenerator.java:52-62`），靠应用层去重 | ✅ 8 列，`version` PK（最干净）；有 `success` 位；**但无 `error_message`**，且与模块自带 `orm/app.orm.xml`（`id` PK、含 error_message）**双定义冲突** | **nop**（主键设计），但需收敛双定义 |
| 11 | **扩展与嵌入 API** | 🟡 ServiceLoader + 14 个扩展点 + 48 个 callback 事件；但**商业功能被 `Tier`/`LicenseGuard` 硬闸**，OSS 侧 `proprietaryStubs` 十余个空壳 | ✅ 27 个 services 文件、21 precondition + 108 SqlGenerator + 12 DatabaseObject 全部可插拔；`CommandStep` 可自定义 | ❌ 16 项硬编码 map + `registerExecutor` / `registerPreconditionChecker` 两个方法；无 CLI、无 maven plugin、无启动入口、无配置项 | **Liquibase** |
| 12 | **测试与 CI** | ❌ **仓库内零测试**；CI 6 格纯构建 + 一次 H2 `info` 冒烟 | ✅ 242（standard）+ 46（integration-tests）个测试类；CI 单测 `{3 OS} × {JDK 17/21/25}` + 按 `testSystem` 展开的真实库矩阵（15+ 种库） | 🟡 10 个测试类、79.24% 指令覆盖，但**只跑 H2**，无 CI job、无容器 | **Liquibase** |

**矩阵小结**：Liquibase 赢 6 项，Flyway 赢 1 项（锁），nop 赢 1 项（历史表主键），其余 4 项平手或都缺。**nop 在 12 个维度中，除"历史表主键设计"外没有任何一项领先**。

---

## 五、与 nop 的关系

### 5.1 可借鉴（按性价比排序）

1. **Liquibase 的 `LoggingExecutor` 模式实现 dry-run**——一个 `updatesDatabase()` 布尔贯穿全链路，比"另写一套 SQL 生成器"便宜一个数量级。nop 侧对应做法：给 `IJdbcTemplate` 一个只记录不执行的实现，`MigrationContext` 加 `dryRun` 开关，`IChangeExecutor.execute` 不改签名。
2. **Liquibase 的 `MARK_RAN`**——precondition 不满足时写一条"已跑过"的历史行，而不是跳过。这是"某次升级只需在特定库上做一次"的正确表达，nop 目前只能靠 `ignore` 或 `dbTypeFilter` 绕。
3. **Flyway 的 `InsertRowLock` 兜底**——在历史表上插哨兵行 + 过期自愈，**不需要任何方言专属 API**，是 nop 最省事的锁方案（advisory lock 可作为后续优化）。
4. **Liquibase 的 `validCheckSums` 逃生舱**——checksum 冲突时允许显式声明"接受任意值"，避免生产上为了改一个描述文字而被迫手工改历史表。
5. **Liquibase 的历史表列自动升级**（`StandardChangeLogHistoryService.init()` 自动 ALTER 补列）——nop 若把历史表收敛到 ORM 模型驱动，这一步天然获得。
6. **Flyway 的 `MigrationState` 状态机 + 错误码表**——把"当前版本处于什么状态"显式枚举出来，比在 `migrate()` 里内联 if 判断更可测、更可文档化。

### 5.2 不可借鉴（明确排除）

1. **Liquibase 的无主键历史表**——不要学。应用层去重是历史包袱，nop 的 `version` PK 是对的。
2. **Flyway 的手写 SQL Parser（826 行启发式）**——nop 不需要：`<sql>` 变更已经显式声明，`SqlExecHelper.executeMultiSql` 的分号切分够用。Flyway 自己都在 `Parser.java:321` 挂了"已知解析器限制"。
3. **Liquibase 的 `Scope` / `ScopeManager` 全局上下文**——与 NopIoC + `IJdbcTemplate` 注入模型重复。
4. **Flyway 的商业版 stub 架构**（`proprietaryStubs` + `LicenseGuard` + `Tier`）——Nop 不做双版本分发，无此需求。
5. **两者的"独立进程 + CLI 优先"交付形态**——nop 的定位是平台内嵌能力，照搬 CLI 会破坏与 `nop-cli` / deploy 流水线的统一性。

### 5.3 nop 独有、两者都没有的牌

1. **XDSL 单模型多格式**：`migration.xdef` 一份契约即可获得 XML / JSON / YAML / XLSX，Liquibase 要维护 4 套 parser，Flyway 只有 SQL。
2. **`_delta` 叠加**：环境 / 客户级差异用 Delta 覆盖迁移文件，两强都没有等价物。
3. **与 ORM 模型同源**：`nop-dbtool` 的 `OrmDbDiffer` 已经能做"模型 vs 库"的 diff 并生成安全 ALTER。**Flyway 的 diff/generate 是商业版，Liquibase 的 diff-changelog 需要连两个库**——nop 只需连一个库 + 读自己的 ORM 模型。
4. **precondition 用 XPL 表达式**（`customCondition`）：表达力强于 Liquibase 的 `SqlPrecondition`，但目前零测试。

---

## 六、缺口清单（按优先级）

> 与 `ai-dev/audits/check/db-migration.md` 的 P0/P1/P2 遗留项对齐；本清单在此基础上补入本轮源码对比新增的项。

### P0 —— 不做则"根本不能用"

1. **接线**：`DbMigrationInitializer.initialize()` 为空、全仓零生产调用方。需要启动入口 + `nop.db.migration.locations` 配置 + `nop-cli` / maven 侧调用。参照 Flyway `DbMigrate` 与 Liquibase `AbstractUpdateCommandStep` 的完整链路形态（锁 → 扫描 → 过滤 → 执行 → 记录）。
2. **并发锁**：参照 5.1-3 的 `InsertRowLock` 方案（历史表哨兵行 + 过期自愈 + 重试预算），默认开启。`nop.orm.db-differ.auto-upgrade-database` 路径（`DataBaseUpgrader`）同样无锁，应共用同一把锁。
3. **事务原子性**：per-migration 用 `ITransactionManager` 包裹；不支持 DDL 事务的库降级为"逐语句 + 失败即停 + 记录断点"。

### P1 —— 不做则"不敢上生产"

4. **validate 补齐**：`APPLIED_MIGRATION_NOT_RESOLVED`（库里有文件没有）+ `RESOLVED_MIGRATION_NOT_APPLIED` 显式报错；`info()` 输出待执行清单。
5. **dry-run**：`LoggingExecutor` 模式（5.1-1）。
6. **baseline + outOfOrder + repair / clearChecksum**：接管存量库的必需品。
7. **历史表双定义收敛**：`orm/app.orm.xml`（`id` PK + `error_message`）与手写 DDL（`version` PK，无 `error_message`）二选一；**`error_message` 必须落库**（审计遗留 P2，本轮确认仍未修）。
8. **DDL 方言矩阵**：复用 `nop-dbtool` 的 `DdlSqlCreator` / `_vfs/nop/orm/xlib/ddl/*.xlib` 替换 `executor/*` 里的手写 `StringBuilder`（`OrmDbDiffer` 已在用，接入点现成）；同时把 `DdlSyntax` 的 `getName().toLowerCase()` 族字符串匹配改为查询 `DialectModel.features` / `dialect.xml`——否则就是"有方言层却在旁边再做一个"。成本低于原估，因为底座已在 nop-dao 里。
9. **真实库 CI 矩阵**：至少 MySQL / PostgreSQL / Oracle / SQL Server / H2 五库（Liquibase 的 `testSystem` 矩阵是现成参照）。

### P2 —— 打磨

10. **precondition 体系**：`onFail(WARN/CONTINUE/MARK_RAN/HALT)` + `and/or/not` 嵌套 + 新增 `rowCount` / `sqlCheck` / `dbms`；**未知 precondition 必须 fail-loud 而非静默跳过**（`MigrationEngine.java:281-283`）。
11. **contexts / labels 语义统一**：`matchesContext` 改为逗号 OR（`MigrationContext.java:114-119`），与 `matchesLabels` 对齐。
12. **补半成品**：`executeMark` 补 executor 或从 xdef 删除；`runOn` 的 `ALWAYS` / `ON_CHANGE` 实现或收窄契约；删除与 `MigrationEngine.executeMigration` 重复的死代码 `MigrationExecutor`。
13. **扫描器**：支持 `.sql` 直挂迁移 + 递归目录 + `include`。
14. **清理**：`src/test/resources/_vfs/nop/db-migration/migrations/V1.0.0~V1.0.13` 14 个 fixture 无测试引用，其中一个用 `<custom>` 与 xdef 的 `customChange` 不符——补测试或删除。
15. **文档**：`docs-for-ai/` 补 owner doc；`docs/db-migration/*.md` 描述的 CLI / GraphQL / 配置项**均不存在**，属误导源，应更正或标注为历史设计稿。

---

## 结论

1. **没有超过，且当前不是"略逊"而是"未交付"**。nop-db-migration 有完整的概念骨架（16 executor + 5 precondition + checksum + contexts/labels + 显式 rollback + xdef 契约），但没有锁、没有事务、没有 dry-run、没有 validate / baseline / repair、没有入口、只跑过 H2。对标 Flyway Community 或 Liquibase Community 的核心命令面，缺口率约 2/3。
2. **源码分析推翻了两条流行认知**，对比时必须修正：
   - Flyway OSS **没有测试**（`find -type d -name test` → 0），CI 只是构建矩阵；它的质量背书不在这个仓库里。
   - Flyway Community **没有 dry-run / undo / diff / generate / deploy / model**，全是 `proprietaryStubs` 空壳。在这几个维度上 Liquibase Community 反而领先。
3. **nop 唯一真正领先的结构性资产是"模型驱动"而非"版本化脚本"**：`OrmDbDiffer` + `DataBaseSchemaInitializer` + `_delta` 组合，是一条 Flyway / Liquibase 都没有的路线（等价物 Atlas 是另一个商业产品）。**建议的主路径不是把 nop-db-migration 补成第四好的版本化迁移工具，而是把 `OrmDbDiffer` 的 diff 结果落成 nop-db-migration 的变更文件**，让"声明式 desired-state"产出"可审计的版本化历史"。这条路径补齐了 Flyway/Liquibase 各自缺的那一半：Liquibase 有模型但需要连两个库，Flyway 有历史但只能手写 SQL。
4. 若决定补齐 nop-db-migration 本体，P0 三项（接线 / 锁 / 事务）是不可跳过的前置，预计工作量远大于其余 P1+P2 之和——因为这三项决定了它能否被调用，而非功能多少。

## Open Questions

- [ ] 主路径选择：补齐 nop-db-migration 本体（版本化脚本路线），还是把 `OrmDbDiffer` 输出接到 nop-db-migration（模型驱动路线）？两条路的重叠部分（锁、事务、历史表、dry-run）可以共用，但入口形态不同。
- [ ] 历史表最终形态：手写 DDL（现状）还是 ORM 模型驱动？后者受 plan-first 保护（`model/*.orm.xml` 变更），需要单独立项。
- [ ] `nop.orm.db-differ.auto-upgrade-database` 与版本化迁移是否共存？共存时以谁为准、如何避免同一列被 ALTER 两次？
- [ ] 是否需要 plain `.sql` 迁移文件支持？（XDSL 路线下 `<sql>` 变更已可表达，但对 DBA 友好度不如裸 SQL。）

## References

### 本仓库

- `nop-persistence/nop-db-migration/`（main 10,560 LOC）
- `nop-persistence/nop-dbtool/nop-dbtool-core/`（main 1,832 LOC）
- `nop-persistence/nop-orm/src/main/java/io/nop/orm/initialize/DataBaseSchemaInitializer.java`
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/db-migration/migration.xdef`
- `ai-dev/audits/check/db-migration.md`（2026-08-19 审计 + 2026-08-22 处置，本报告 P0/P1/P2 与其对齐）
- `ai-dev/analysis/2026-10/coverage-baseline-wi4-2026-10-02.md`（nop-db-migration 覆盖率 79.24% 指令 / 59.46% 分支）
- `ai-dev/analysis/2026-10/2026-10-05-shardingsphere-vs-nop-orm-gap-analysis.md`（同类三方对比的格式先例）

### 外部源码（本轮 clone）

- flyway 13.10.0 — `~/sources/flyway`，`b4251cdbec1a6c8ec6b15c9f3f99976d7b8ef251`（Apache-2.0）
- liquibase main ≈5.0.x — `~/sources/liquibase`，`f58ccf20b2610fd04ebcabc943842f1d6484402d`（FSL-1.1-ALv2）

### 本人抽查核实的锚点（三端）

- flyway：`ResolvedMigration.java:90-91`（outOfOrder → IGNORED）、`DryRunSupportStub.java:26-36`（返回 null）、`InsertRowLock.java:44,61`（哨兵行 + 10 分钟过期）、`ChecksumCalculator.java:32-45`（CRC32）、`find -type d -name test` → 0
- liquibase：`CreateDatabaseChangeLogTableGenerator.java:52-62`（14 列、无 PK）、`StandardLockService.java:257-300`（waitForLock）、`LoggingExecutor.java:310`（`updatesDatabase()==false`）、`grep -rl createInverses` → 20 文件、`find */src/test` → 242 + 46 个测试类
- nop：`MigrationEngine.java:123-191,154,315-334,336-405`、`MigrationHistoryManager.buildCreateHistoryTableSQL` / `recordMigration`、`MigrationContext.java:114-119`、`MigrationFileScanner.java:58,115-134`、`DdlSyntax.java:43-71`、`DbMigrationInitializer.java:20-22`、`DataBaseUpgrader.java` 类注释与 `dbtool-defaults.beans.xml`
- nop 方言底座（回答"nop-dao 是否支持 dialect"）：`nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java`（423 行 / ~70 方法）、`.../dialect/model/DialectModel.java`、`nop-file/nop-file-dao/_dump/nop-app/nop/schema/orm/dialect.xdef:31-41`（`DialectFeatures` 33 个 `supportXxx`）、`nop-dao/src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml`（19 个，`postgresql.dialect.xml:2` 为 `x:extends` 组合示例）、`nop-orm/src/main/java/io/nop/orm/ddl/DdlSqlCreator.java` + `nop-orm/src/main/resources/_vfs/nop/orm/xlib/ddl/*.xlib`（9 个方言模板）、`nop-dbtool-core/src/main/java/io/nop/dbtool/core/diff/OrmDbDiffer.java`（`DdlSqlCreator` 的现有调用方）
