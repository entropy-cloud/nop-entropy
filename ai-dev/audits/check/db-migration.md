# db-migration 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-persistence/{nop-db-migration,nop-dbtool}
- 文件数: 约 115（src/main/java，其中 nop-db-migration 102 个 + nop-dbtool/nop-dbtool-core 7 个）
- 覆盖范围声明: 全量通读 nop-dbtool 全部 7 个 main 类；nop-db-migration 下 core/（7 文件）、executor/（18 文件）、precondition/（6 文件）、model/ 关键手写类（DbChangeModel、ColumnDefinition 等 10 个）与 _gen 抽查（_ColumnDefinition、_DbMigrationModel、_InsertDataChange 字段与getType）；`_vfs` 下 beans.xml 与 orm/app.orm.xml、nop-xdefs 中 migration.xdef 契约全文比对；并追溯验证依赖行为（DslBeanModelParser/DslXNodeToJsonTransformer 的 sub-type 解析、SqlExecHelper.executeMultiSql、BaseDataSetMeta.getFieldIndex、JdbcHelper.getDataSetMeta、mysql/h2 dialect 的 columnNameCase 配置、NopIoC autowireConstructorArgs）。`model/_gen/` 其余生成类仅抽查未逐行。测试代码不在审计范围，仅用于交叉验证行为（确认 XML 解析路径无测试覆盖）。
- 方法: 结构浏览 → grep 扫描（空 catch / new RuntimeException / synchronized / 拼接 DDL / @Inject）→ 逐命中点 Read 验证 → 核心链路深读（历史管理、引擎调度、DDL 生成、xdef 契约、dbtool 比对/发现/升级）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 8 |
| P2 | 10 |
| P3 | 2 |

> 前提说明: `MigrationEngine.migrate()` 及 `MigrationFileScanner` 在仓库内无生产调用方（仅测试使用，beans.xml 中定义了 `nopMigrationEngine` 但无人调用），下述多条 P0/P1 属"接入即触发"。`nop-dbtool` 是活代码（被 nop-cli 逆向命令、demo、`nop.orm.db-differ.auto-upgrade-database` 开关启用），其发现的问题为现实可触达。

## 发现列表

### [P0] XML 迁移文件解析后 change.type 恒为 null，全部变更被静默跳过且记录为成功

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:171`（跳过点）；根因链涉及 `core/MigrationFileScanner.java:59`、`model/DbChangeModel.java:18`
- **维度**: D1、D8
- **证据**:
```java
// MigrationEngine.executeChange
String changeType = change.getType();
if (changeType == null || changeType.isEmpty()) {
    return;   // 静默跳过，不报错
}
```
```java
// DbChangeModel: type 是普通可变字段，所有 _gen 子类（如 _InsertDataChange）均不覆盖 getType()
private String type;
```
- **现状**: migration.xdef 中 changeset 以 `xdef:bean-sub-type-prop="type"` 声明多态，XML 元素为 `<createTable>`、`<insert>` 等（无 `type` 属性、无 `xdef:bean-tag-prop`）。强类型解析器 `DslBeanModelParser.parseObject(defNode, node, subTypeProp)`（nop-xlang）**完全不使用 subTypeProp 参数**（grep 全文件无 subTypeProp），不会把 tag 名写入 type 字段；弱类型路径 `DslXNodeToJsonTransformer.parseObject` 虽有 `obj.addProp(subTypeProp, node.getTagName())`，但 DslModelParser 默认走强类型路径。平台惯例（beans.xdef 的 BeanMapValue、task.xdef 的 TaskStepModel）是子类硬编码返回类型常量，而 DbChangeModel 用可变字段且子类不覆盖。
- **风险**: 通过 `MigrationFileScanner.scan()`（即 migrate() 的默认路径）加载的任何迁移文件，其全部变更 type=null 被静默跳过，DDL 一条不执行，却写入历史表 `success=true` 并记录版本已执行——schema 未变更但系统认为已迁移，且无任何告警。
- **建议**: 让 `_gen` 模板为每个 change 子类生成 `getType()` 常量覆盖（对齐 task/orm 模式），或在 `MigrationFileScanner.loadMigration` 后按元素名回填 type；补一个"XML 文件 → 至少生成一条 DDL"的端到端测试。
- **误报排除**: 已核对测试目录无任何用例经 scanner/DslModelParser 加载 XML（全部手工 `setType("insertData")`），故测试通过不能证伪；已核对 DslBeanModelParser/TreeBeanBuilder 两条装配路径均不会填充该字段；已用 BeanMapValue 硬编码 `getBeanValueType()` 佐证平台解析器不自动填充 sub-type prop。

### [P1] 迁移失败一次后，重试必然触发历史表主键冲突并中断整个迁移流程

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:117-141`；`core/MigrationHistoryManager.java:79-81、186-197`
- **维度**: D1、D4
- **证据**:
```java
// MigrationEngine.migrate
try {
    MigrationRecord record = executeMigration(migration, context);
    localHistoryManager.recordMigration(record);   // INSERT version 主键
} catch (Exception e) {
    ...
    localHistoryManager.recordMigration(failedRecord); // 再次 INSERT 同一 version
```
```java
// MigrationHistoryManager
"version VARCHAR(200) NOT NULL PRIMARY KEY ..."
// getExecutedVersions 只取 success = TRUE 的记录
```
- **现状**: 失败记录（success=false）不进 executedVersions，因此失败迁移会被重试；但历史表以 version 为主键且失败记录不删除。第二次运行时：executeMigration 成功 → recordMigration INSERT 撞主键 → 异常进 catch → recordMigration(failedRecord) 再撞主键 → 异常从 catch 块逃逸，migrate() 循环终止。
- **风险**: 任何一个版本迁移失败一次后，此后每次运行都在主键冲突处崩溃，该版本及之后的所有迁移被永久阻塞；即使重试的 DDL 全部成功也无法登记成功。
- **建议**: recordMigration 改为 upsert（先 DELETE 旧记录再 INSERT，或 UPDATE），或失败时直接删除旧失败记录（Flyway 模式）。
- **误报排除**: 逐行核对 migrate() 的 try/catch 嵌套与 recordMigration 的 INSERT 语句；确认失败记录 version 唯一且无删除路径（removeMigrationRecord 仅 rollback 使用）。

### [P1] recordMigration 的 success 猜测逻辑：异常 message 为 null 时失败迁移被记为成功，变更永久丢失

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationHistoryManager.java:86-91`；触发方 `core/MigrationEngine.java:133`
- **维度**: D1、D4
- **证据**:
```java
boolean success = record.isSuccess();
if (!success && record.getErrorMessage() == null) {
    // If success is false but no error message, assume this is a new migration
    success = true;   // 失败被改写为成功
}
```
```java
// MigrationEngine.migrate catch 块
failedRecord.setErrorMessage(e.getMessage());   // NPE 等异常 getMessage() == null
```
- **现状**: 失败记录的 success 以"errorMessage 是否为 null"猜测。NopException.adapt 或原始 NPE/部分 RuntimeException 的 getMessage() 为 null 时，e.getMessage() 返回 null → 失败迁移被 INSERT 为 success=true → 下次运行 executedVersions 含该版本，迁移被跳过。
- **风险**: 失败的 DDL 变更（可能只执行了一半）被登记为成功，该版本永久不再执行，导致 schema 与模型静默漂移——这是"变更丢失"型数据错误。
- **建议**: 删除猜测逻辑，显式由调用方设置 success；catch 失败时用 `StringHelper.isEmpty(e.getMessage()) ? e.toString() : e.getMessage()` 保证 message 非空。
- **误报排除**: 确认 MigrationRecord.errorMessage 无默认值、MigrationEngine 两处 catch 均直接 setErrorMessage(e.getMessage())；MigrationExecutor.execute 异常路径同样存在（该 record 虽被丢弃，但属于同一缺陷家族）。

### [P1] tableExists/getMigrationByVersion 吞掉所有异常并返回"不存在"，且 INFORMATION_SCHEMA 查询带方言缺陷

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationHistoryManager.java:160-184`
- **维度**: D1、D4
- **证据**:
```java
protected boolean tableExists() {
    try {
        String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?";
        ... .sql(checkSql, TABLE_NAME.toUpperCase()) ...
    } catch (Exception e) {
        return false;   // 连接失败 == 表不存在
    }
}
```
- **现状**: 三个问题叠加：(1) catch Exception return false 把连接故障、权限错误与"表不存在"混为一谈——瞬时故障时 getExecutedVersions 返回空集合，所有已执行迁移被判定未执行而重复执行（在非幂等迁移上直接破坏数据）；(2) 查询不带 TABLE_SCHEMA 限定，MySQL 下会命中任何 schema 中的同名表；(3) `TABLE_NAME = 'NOP_DB_MIGRATION_HISTORY'`（toUpperCase）在 PostgreSQL（字符串比较大小写敏感、表名小写存储）永远不匹配 → 每次启动都重复 CREATE TABLE 报错；Oracle 无 INFORMATION_SCHEMA 视图 → 查询抛异常被吞 → 同样每次 CREATE TABLE 失败。getMigrationByVersion 的 catch-return-null 是同型缺陷。
- **风险**: 历史表存在性误判直接导致迁移重复执行（丢/重数据）或引擎启动失败；方言上仅 MySQL/H2（列名大写）能正常工作。
- **建议**: 用 `IDialect`/JDBC DatabaseMetaData 判表存在；只在捕获"表不存在"型 SQLException 错误码时返回 false，其余异常上抛（NopException + ErrorCode）。
- **误报排除**: 已核对 h2.dialect.xml 配 tableNameCase=upper、mysql.dialect.xml 无 case 配置，确认 PostgreSQL/Oracle 场景结论；确认 getExecutedVersions 在 tableExists=false 时直接返回空集（第 38-40 行）。

### [P1] getExecutedVersions/getMigrationByVersion 以大写常量取字段，MySQL 列名小写下抛 ERR_DATASET_UNKNOWN_COLUMN

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationHistoryManager.java:42-49、130-137`
- **维度**: D1
- **证据**:
```java
String sql = "SELECT version FROM " + TABLE_NAME + " WHERE success = TRUE ORDER BY installed_on";
...
int versionIndex = row.getMeta().getFieldIndex("VERSION");
```
- **现状**: 建表 SQL 用小写列名（`version VARCHAR(200)...`）。`BaseDataSetMeta.getFieldIndex` 精确匹配且 `caseInsensitive=false`（JdbcHelper.getDataSetMeta 用单参构造器）；MySQL dialect 未配置 columnNameCase → JDBC 返回列标签 `version`（小写）→ getFieldIndex("VERSION") 抛 NopException → getExecutedVersions 的 catch 把它包装成 RuntimeException 中断迁移。
- **风险**: 在 MySQL（平台最常用数据库）上历史查询必失败，migrate() 无法执行。
- **建议**: 取列改用索引 `row.getString(0)`；或建表/查询统一大小写并经 dialect 规范化。
- **误报排除**: 已核对 BaseDataSetMeta 单参构造器 caseInsensitive=false、JdbcDataSet 经 JdbcHelper.getDataSetMeta 构造、mysql.dialect.xml 无 columnNameCase；测试仅在 H2（大写）运行故未暴露。附带：`WHERE success = TRUE` 与 `success BOOLEAN` 在 Oracle/SQL Server 亦非法，同属方言问题（见 P1-7 条合并评估）。

### [P1] AddColumnExecutor 多列 DDL 用 "; " 拼接后以单语句 executeUpdate 执行

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/executor/AddColumnExecutor.java:70-103`
- **维度**: D1
- **证据**:
```java
for (ColumnDefinition column : change.getColumns()) {
    if (!first) {
        sb.append("; ");        // 多条 ALTER 拼成一段
    }
    ...
}
// 然后
context.getJdbcTemplate().executeUpdate(... .append(sql) ...);  // 未走 executeMultiSql
```
- **现状**: `executeUpdate` 不拆分语句（对照 SqlExecutor 特意用 `isSplitStatements()` 选择 `executeMultiSql`，其实现 SqlExecHelper 会按分号拆分）。多列 addColumn 生成 `ALTER ...; ALTER ...` 单语句提交，H2/PostgreSQL/Oracle/默认 MySQL 的 PreparedStatement 均报语法错误。
- **风险**: 任何一次 addColumn 含 2 个以上列的迁移直接失败（失败又触发 P1 主键冲突链）。
- **建议**: 逐列调用 executeUpdate，或改用 executeMultiSql。
- **误报排除**: 核对 SqlExecHelper.executeMultiSql 拆分逻辑与 SqlExecutor 的分支处理，证明 executeUpdate 不具备多语句能力；核对 TestChangeExecutors.testAddColumn 仅单列（singletonList），多列无覆盖。

### [P1] DDL 生成完全忽略 dialect：类型名直拼 StdSqlType.name()、AUTO_INCREMENT、表级 COMMENT、PG 专有 ALTER 语法

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/executor/CreateTableExecutor.java:53-113`；`executor/AddColumnExecutor.java:82-99`；`executor/AlterColumnExecutor.java:52-81`
- **维度**: D1、D8
- **证据**:
```java
// CreateTableExecutor.buildColumnType: dialect 参数未使用
StdSqlType type = column.getType();
String typeName = type != null ? type.name() : "VARCHAR";   // INTEGER/DATETIME/JSON 等直拼
...
if (column.isAutoIncrement()) sb.append(" AUTO_INCREMENT");  // 仅 MySQL
sb.append(" COMMENT '").append(change.getRemark()).append("'"); // 表级 COMMENT 仅 MySQL
```
```java
// AlterColumnExecutor
sb.append(" ALTER COLUMN ").append(...)
  .append(" TYPE ").append(newType.name())   // PostgreSQL 语法
  ... sb.append(" SET NOT NULL") / (" DROP NOT NULL") / (" SET DEFAULT ")
```
- **现状**: 三个执行器签名接收 IDialect 但只用于 escapeSQLName。跨库问题：DATETIME/JSON 等枚举名非通用类型；AUTO_INCREMENT 在 PG/Oracle/SQL Server 无效；表级 `COMMENT '...'` 在 PG/Oracle/SQL Server 均为语法错误；`ALTER COLUMN x TYPE ... SET NOT NULL` 是 PostgreSQL 专有（MySQL 需 MODIFY，SQL Server 不支持 TYPE 子句）；remark/默认值中的单引号不转义会破坏 SQL。nop-dbtool 的 DdlSqlCreator 按 dialect 走 xpl 模板库，本模块未复用。
- **风险**: 同一迁移模型在不同数据库上生成无效 DDL，或类型映射错误（错类型属于会破坏数据的 DDL 错误）。
- **建议**: 复用 nop-orm 的 DdlSqlCreator/方言模板生成 DDL，废弃手写拼接；至少为类型映射与自增/注释走 dialect 接口。
- **误报排除**: 已列 StdSqlType 枚举常量确认 name() 输出（含 DATETIME、INTEGER、JSON）；已核对三个执行器中 dialect 的全部使用点仅为 escapeSQLName。

### [P1] xdef 契约大面积未实现：preconditions/ignore/failOnError/runOn/contexts/labels 均不被引擎评估

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:96-145`（migrate 无任何过滤）；`precondition/`（6 文件零调用方）；契约在 `nop-kernel/nop-xdefs/.../db-migration/migration.xdef`
- **维度**: D8
- **证据**:
```java
// migration.xdef: "只有满足所有前置条件时才会执行此迁移"
<preconditions xdef:body-type="list" xdef:key-attr="id"> ... </preconditions>
ignore="!boolean=false"  failOnError="!boolean=true"  runOn="enum:...RunOnChange"
```
```java
// MigrationEngine.migrate: 只按 version 判重，无 precondition/ignore/context/label/runOn 任何检查
if (executedVersions.contains(version)) { continue; }
```
- **现状**: 模型与 xdef 支持上述属性，但 MigrationEngine/Executor 从不读取；5 个 IPreconditionChecker 实现（及表/列/索引/外键存在性检查）在模块内外均无注册和调用（beans.xml 也未注册）；DbMigrationErrors 中 ERR_DB_MIGRATION_PRECONDITION_FAILED、ERR_DB_MIGRATION_CHECKSUM_MISMATCH、ERR_DB_MIGRATION_EXECUTION_FAILED 均无使用点。
- **风险**: 用户为危险变更（如 dropTable）配置的 precondition 保护完全不生效——在不符合条件的库上照样执行破坏性 DDL；ignore=true 的迁移照常执行。属安全语义契约失效。
- **建议**: 在 executeMigration 前评估 preconditions/ignore/context 匹配；checker 经 beans 注册进引擎。
- **误报排除**: grep 全仓库 IPreconditionChecker/PreconditionChecker 引用（precondition 包外零命中）；grep DbMigrationErrors 各 ErrorCode 引用确认无消费点。

### [P1] nop-dbtool 自动升级无并发互斥，多实例同时启动会重复/交错执行 DDL

- **文件**: `nop-persistence/nop-dbtool/nop-dbtool-core/src/main/java/io/nop/dbtool/core/DataBaseUpgrader.java:52-79`；`initialize/DataBaseUpgradeInitializer.java:35-39`
- **维度**: D3
- **证据**:
```java
@PostConstruct
public void init() {
    DataBaseUpgrader upgrader = new DataBaseUpgrader(this.jdbcTemplate, this.ormSessionFactory);
    upgrader.upgrade();   // 无任何数据库锁
}
// upgradeByQuerySpace: discover → 拼多条 DDL → executeMultiSql，无锁无版本互斥
```
- **现状**: 该功能由配置 `nop.orm.db-differ.auto-upgrade-database=true` 启用（beans 条件装配），每次应用启动执行。无 advisory lock/GET_LOCK/独立锁表，也无升级版本记录。滚动发布或多副本同时启动时，两个实例并发 discover+执行 DDL：重复 CREATE TABLE 报错导致启动失败，或交错执行半套 DDL 后另一方再叠加，最终 schema 不确定。
- **风险**: 生产滚动更新场景（现实触发路径）下升级失败或产生不一致 schema。
- **建议**: 升级前获取数据库级锁（MySQL GET_LOCK / PG advisory lock），或引入升级版本表做 CAS。
- **误报排除**: 核对 dbtool-defaults.beans.xml 的条件装配确认启用路径；grep 全类无锁相关调用。

### [P2] MigrationHistoryManager 抛 bare RuntimeException，违反平台错误处理规范

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationHistoryManager.java:55-57`
- **维度**: D4、D7
- **证据**:
```java
} catch (Exception e) {
    throw new RuntimeException("Failed to get executed versions", e);
}
```
- **现状**: 平台规范要求框架层使用 NopException + ErrorCode + .param(...)（DbMigrationErrors 已定义可用错误码），此处用裸 RuntimeException 且丢失 querySpace/version 上下文参数。
- **风险**: 错误码体系失效，监控/国际化无法按错误码处理；cause 保留但上下文缺失。
- **建议**: 改为 `throw new NopException(ERR_DB_MIGRATION_EXECUTION_FAILED, e).param(ARG_VERSION, ...)`。
- **误报排除**: 该行是模块内唯一 bare RuntimeException（grep 全模块确认），其余错误路径已用 NopException。

### [P2] JdbcMetaDiscovery.createMeta 自赋值笔误，DataBaseMeta 的产品/驱动信息恒为 null

- **文件**: `nop-persistence/nop-dbtool/nop-dbtool-core/src/main/java/io/nop/dbtool/core/discovery/jdbc/JdbcMetaDiscovery.java:207-213`
- **维度**: D1
- **证据**:
```java
DataBaseMeta meta = new DataBaseMeta();
meta.setDriverName(meta.getDriverName());         // 应为 metaData.getDriverName()
meta.setDriverVersion(meta.getDriverVersion());   // 同上
meta.setProductName(meta.getProductName());       // 同上
meta.setProductVersion(meta.getProductVersion()); // 同上
```
- **现状**: 从刚 new 出来的空 meta 取值再设置回自身，JDBC DatabaseMetaData 的真实值从未被读取。
- **风险**: 逆向出的模型元数据（productName/driverName 等）永远为空，影响 CLI 逆向输出与依赖这些字段的下游判断；属明确复制粘贴 bug。
- **建议**: 四行改为从 `metaData` 读取。
- **误报排除**: 逐字核对源码确认自赋值；DataBaseMeta 对应字段默认 null。

### [P2] 历史表存在两套冲突定义：app.orm.xml 的 NopDbMigrationHistory 与 MigrationHistoryManager 手写建表

- **文件**: `nop-persistence/nop-db-migration/src/main/resources/_vfs/nop/db-migration/orm/app.orm.xml`；`core/MigrationHistoryManager.java:79-107、186-197`
- **维度**: D8
- **证据**:
```xml
<!-- app.orm.xml: id 为主键，含 error_message -->
<column name="id" code="ID" precision="36" primary="true" .../>
<column name="version" code="VERSION" domain="version" mandatory="true" .../>
```
```java
// MigrationHistoryManager: version 为主键，无 id/error_message 列
"version VARCHAR(200) NOT NULL PRIMARY KEY, description VARCHAR(500), ... success BOOLEAN, installed_by VARCHAR(100)"
INSERT INTO nop_db_migration_history (version, description, type, checksum, installed_on, execution_time, success, installed_by) VALUES (...)
```
- **现状**: 模块自带 ORM 实体模型（会被平台 ORM schema 初始化流程建表：id 主键、含 errorMessage 列），而 MigrationHistoryManager 自己建/写另一套结构（version 主键、无 id）。二者作用于同一物理表 `nop_db_migration_history`。
- **风险**: 若 ORM 初始化先建表，recordMigration 的 INSERT 缺 id（NOT NULL 主键）且不含 error_message → 写入失败；MigrationRecord.errorMessage 也从未持久化（内存字段），失败原因无法事后审计。若 MigrationHistoryManager 先建表，ORM 初始化又会认为结构不符。
- **建议**: 二选一收敛（推荐复用 ORM 实体 + dao），或至少让两者结构一致并持久化 errorMessage。
- **误报排除**: 核对模块内不存在 io.nop.db.migration.entity 包源码，确认 app.orm.xml 实体无 Java 类仅剩模型；两处表名大小写归一后确为同一表。

### [P2] checksum 计算过弱且从不校验，isValidateChecksum 与 R__ 可重复迁移语义形同虚设

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:113-120、187-205`
- **维度**: D8
- **证据**:
```java
// checksum 只含 version + description + 各 change 的 type 字符串，不含任何变更内容
for (DbChangeModel change : migration.getChangeset()) {
    sb.append(change.getType() != null ? change.getType() : "");
}
// migrate() 中从未调用 getMigrationByVersion/checksum 比对
```
- **现状**: 已执行迁移文件内容被修改（改列类型、改 SQL）后 checksum 不变、也无人比对；MigrationContext.isValidateChecksum() 无任何读取点；xdef 声明 repeatable 类型"每次校验和变化都重新执行"，实现按 `R__` 前缀 + version 全名判重，成功执行一次后永不重跑；`type` 属性（MigrationType）与 `runOn` 亦被 R__ 前缀推断取代。
- **风险**: 变更内容漂移无法检测，R__ 视图/脚本更新后不生效，配置项产生虚假安全感。
- **建议**: checksum 覆盖变更内容（序列化 change）；migrate 中对已执行版本比对 checksum（isValidateChecksum 控制）；R__ 迁移按 checksum 变化重执行（update 历史记录而非 insert）。
- **误报排除**: grep 确认 getMigrationByVersion、ERR_DB_MIGRATION_CHECKSUM_MISMATCH、isValidateChecksum 在 main 代码中无调用/读取点。

### [P2] InsertDataExecutor.generateRollbackSql 返回无 WHERE 的全表 DELETE

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/executor/InsertDataExecutor.java:42-47`
- **维度**: D1
- **证据**:
```java
public String generateRollbackSql(AbstractComponentModel change, IDialect dialect) {
    InsertDataChange insertData = (InsertDataChange) change;
    StringBuilder sb = new StringBuilder();
    sb.append("DELETE FROM ").append(dialect.escapeSQLName(insertData.getTableName()));
    return sb.toString();   // 无 WHERE：回滚将清空整表
}
```
- **现状**: insertData 的"自动回滚 SQL"是对目标表的无条件 DELETE。当前 `generateRollbackSql` 接口在模块内无调用方（rollback 走显式 RollbackDefinition），该地雷尚未接线；但 xdef 注释承诺"Nop 会尝试自动生成回滚 SQL"。
- **风险**: 一旦自动回滚功能接线（或调用方按接口契约使用），回滚一个 insertData 变更会删光整张业务表。
- **建议**: 至少限定按插入的主键删除，或未实现时返回 null 并移除误导性注释。
- **误报排除**: grep 确认 generateRollbackSql 无 main 调用方（列入报告而非更高 severity 的原因）；CreateIndexExecutor/CreateTableExecutor 的 `DROP ... IF EXISTS` 在 MySQL 8 也不支持，同属未接线风险。

### [P2] beans.xml 装配与引擎实现漂移：nopMigrationExecutor 缺 dbTypeFilter 键，nopMigrationEngine 无人消费

- **文件**: `nop-persistence/nop-db-migration/src/main/resources/_vfs/nop/db-migration/beans/default.beans.xml:11-43`
- **维度**: D7、D8
- **证据**:
```xml
<bean id="nopMigrationEngine" class="io.nop.db.migration.core.MigrationEngine" ioc:auto-inject="true">
<!-- nopMigrationExecutor 的 executors map 共 15 项，无 dbTypeFilter -->
```
- **现状**: (1) beans 装配的 nopMigrationExecutor 不含 `dbTypeFilter` 与 `executeMark`（MigrationEngine 内存注册则含 dbTypeFilter），经 beans 路径执行 `<dbTypeFilter>` 变更会报 unknown change type；(2) `nopMigrationEngine`、`nopMigrationHistoryManager` 在仓库内无任何消费者，引擎整体未接线；(3) MigrationEngine 持有的 historyManager 字段（含 getHistoryManager()）在构造后基本废弃——migrate() 每次自建 localHistoryManager；(4) MigrationEngine.registerExecutor 后注册的执行器不会进入 DbTypeFilterExecutor 内部表，嵌套分发将失败。
- **风险**: 同一引擎两条装配路径行为不一致；IoC 注册的 bean 契约（getHistoryManager 可用）与实现不符。
- **建议**: 收敛为单一路径装配；dbTypeFilter 依赖注入改为每次 execute 时委托父表或统一注册点。
- **误报排除**: 已核对 NopIoC autowireConstructorArgs 能为 MigrationHistoryManager(IJdbcTemplate,String) 自动选参最少构造器（"无法实例化"为误报，已排除）；比对 beans map 与 registerDefaultExecutors 注册键差异。

### [P2] rollback 原地 reverse 模型列表，二次回滚顺序错误

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:237-243`
- **维度**: D1
- **证据**:
```java
List<DbChangeModel> rollbackChanges = migration.getRollback().getChanges();
Collections.reverse(rollbackChanges);   // 修改解析出的模型本身
for (DbChangeModel change : rollbackChanges) {
    executeChange(change, context);
}
```
- **现状**: 反转直接作用于 ORM 模型对象（可复用的解析缓存）。同一 migration 对象第二次 rollback（或 migrate 与 rollback 混用同一加载缓存）时以相反顺序执行。
- **风险**: 二次回滚执行顺序颠倒，依赖顺序的回滚（先删索引后删列）失败。
- **建议**: 复制列表后再 reverse；另注：rollback 失败仅记录内存 record，不落历史表。
- **误报排除**: 核对 AbstractComponentModel 冻结机制未被用于阻止 mutation（rollback 未 freeze）。

### [P2] JdbcMetaDiscovery.getCatalogs/getSchemas 的 ResultSet 未关闭

- **文件**: `nop-persistence/nop-dbtool/nop-dbtool-core/src/main/java/io/nop/dbtool/core/discovery/jdbc/JdbcMetaDiscovery.java:115-153`
- **维度**: D2
- **证据**:
```java
DatabaseMetaData metaData = conn.getMetaData();
ResultSet rs = metaData.getCatalogs();   // 未用 try-with-resources
List<String> ret = new ArrayList<>();
while (rs.next()) { ... }                // 循环后未 close
```
- **现状**: 两个方法的 ResultSet 泄漏（同文件 initSchemas/discoverTables 等都正确使用了 try-with-resources，仅这两处遗漏）。dataSource 模式下 finally 关闭 Connection 会间接释放；`forConnection` 模式（连接由外部管理、不关闭）下 ResultSet 保持打开，游标/语句资源悬挂，多次调用累积。
- **风险**: 长连接上反复调用 catalogs/schemas 枚举（CLI 逆向工具路径）导致语句句柄泄漏。
- **建议**: 补 try-with-resources。
- **误报排除**: 核对 closeConnection 仅在 dataSource 模式关闭连接；forConnection 分支确认连接不关闭。

### [P2] 数据变更执行器的值/默认值/注释/where 均为字符串直拼，类型与转义处理缺失

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/executor/InsertDataExecutor.java:81-86`；`executor/UpdateDataExecutor.java:70-73、78-83`；`executor/CreateTableExecutor.java:80-89`；`executor/AddColumnExecutor.java:94-99`
- **维度**: D1、D5
- **证据**:
```java
protected String escapeValue(String value, IDialect dialect) {
    if (value == null) return "NULL";
    return "'" + value.replace("'", "''") + "'";   // 数字/布尔/日期也一律加引号
}
// UpdateDataExecutor / DeleteDataExecutor:
sb.append(" WHERE ").append(change.getWhere());    // where 原样拼接
// CreateTableExecutor:
sb.append(" DEFAULT ").append(column.getDefaultValue()); // 无引号包装、无转义
sb.append(" COMMENT '").append(change.getRemark()).append("'"); // 单引号未转义
```
- **现状**: InsertColumnModel 携带 value/valueNumeric/valueBoolean/valueDate 四种值字段（见 xdef），但执行器只读字符串 value 并统一加单引号（PG boolean 可隐转，数值/日期在部分库报错）；`DEFAULT abc` 生成未加引号的标识符式默认值（非法 SQL）；remark/where 含单引号即产生语法错误。这些值来自打包的 migration XML（开发者资源而非用户输入），注入风险低，主要是正确性问题。
- **风险**: 含引号文本或非字符串字面量默认值/列值生成非法 DDL/DML，迁移失败。
- **建议**: 按 xdef 的类型化字段取值并按 dialect 生成字面量；DEFAULT/COMMENT 走转义。
- **误报排除**: 核对 xdef 中 insert/update 列定义确有 valueNumeric 等属性而 _InsertColumnModel 已生成对应字段，执行器未使用。

### [P2] IndexExistsChecker 查询不存在的 INFORMATION_SCHEMA.INDEXES 视图

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/precondition/IndexExistsChecker.java:40`
- **维度**: D1
- **证据**:
```java
String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = ? AND INDEX_NAME = ?";
```
- **现状**: SQL 标准与主流数据库均无 `INFORMATION_SCHEMA.INDEXES`（MySQL 为 STATISTICS，PostgreSQL 无该视图须用 pg_indexes，SQL Server 在 sys.indexes）。H2 恰好有该视图，测试环境掩盖问题。
- **风险**: 该 checker 一旦接线（修 P1 precondition 问题后必然接线），在 MySQL/PG/Oracle 上抛异常导致迁移中断。
- **建议**: 按 dialect 选择索引元数据查询；或改用 JDBC DatabaseMetaData.getIndexInfo。
- **误报排除**: 当前因 precondition 未接线暂不可达，故定 P2 而非 P1；TableExistsChecker/ColumnExistsChecker 的大写比较与无 schema 限定问题同源（见 P1 tableExists 条）。

### [P2] nop-dbtool 每次启动全库元数据发现 + 每表 N+1 元数据查询

- **文件**: `nop-persistence/nop-dbtool/nop-dbtool-core/src/main/java/io/nop/dbtool/core/DataBaseUpgrader.java:81-95`；`discovery/jdbc/JdbcMetaDiscovery.java:456-471、538-571、590-616`
- **维度**: D6
- **证据**:
```java
// genUpgradeSql
DataBaseMeta oldDbMeta = discovery.discover(null, null, "%");  // 全库所有表
// JdbcMetaDiscovery.discover: 对每张表逐一调用
metaData.getImportedKeys(catalog, schemaPattern, table.getTableName())   // discoverRelations
metaData.getIndexInfo(catalog, schemaPattern, table.getTableName(), false, false) // discoverIndexes
metaData.getIndexInfo(catalog, schemaPattern, table.getTableName(), true, false)  // discoverUniqueKeys
```
- **现状**: auto-upgrade 开启后每次应用启动：全表枚举 + 每表 3 次（外键/索引/唯一键）+ 每表 1 次主键元数据查询。库内表多（含无关业务表，pattern="%")时启动时间与库负载显著增加，且升级流程没有任何"上次已比对"的短路。
- **风险**: 大库上启动缓慢、元数据查询风暴；多副本滚动发布时叠加（配合 P1 无锁问题放大）。
- **建议**: 只对 ORM 模型涉及的表名做发现（表名 IN 列表或分批 pattern）；引入升级版本记录跳过无变化场景。
- **误报排除**: 核对 discover 的调用参数与循环结构确认 N+1；确认无任何缓存/短路逻辑。

### [P3] MigrationVersionComparator 对双 null 模型的比较违反 Comparator 契约

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationVersionComparator.java:34-37`
- **维度**: D1
- **证据**:
```java
if (m1 == m2) return 0;
if (m1 == null) return -1;
if (m2 == null) return 1;
```
- **现状**: 两个不同的 null 引用比较时双向都返回 -1，违反反对称性，TimSort 在特定输入下抛 "Comparison method violates its general contract"。scanner 不会产出 null 模型，现实触发面极窄。
- **风险**: 极端情况下排序崩溃。
- **建议**: `if (m1 == null && m2 == null) return 0;` 前置。
- **误报排除**: 确认 scanner/loadMigration 不产生 null 元素，故仅 P3。

### [P3] MigrationExecutor.execute 异常路径构造的 MigrationRecord 被直接丢弃（无效代码）

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationExecutor.java:62-74`
- **维度**: D4、D8
- **证据**:
```java
} catch (Exception e) {
    MigrationRecord record = new MigrationRecord();
    record.setVersion(...); ... record.setSuccess(false); record.setErrorMessage(e.getMessage());
    throw NopException.adapt(e);   // record 构造后未使用即抛出
}
```
- **现状**: 失败记录构造后被丢弃，调用方拿不到 record；该 execute 方法在 main 代码中无调用方（MigrationEngine 用自己的 executeMigration），属与 MigrationEngine 重复实现的死代码，且与 recordMigration 的 success 猜测逻辑（P1）形成同类隐患。
- **风险**: 维护性混乱：两套执行/记录逻辑（MigrationExecutor vs MigrationEngine.executeMigration）并存、行为不一致（beans 装配用前者、引擎用后者）。
- **建议**: 删除 MigrationExecutor 或让 MigrationEngine 委托它，收敛单一实现。
- **误报排除**: grep 确认 MigrationExecutor.execute 无 main 调用方，仅 beans 装配注入 executors map。

## 附注

- 未发现 private 字段注入、Spring @Value 误用等 D7 IoC 违规（模块内仅 DataBaseUpgradeInitializer 使用 protected @Inject，合规）。
- 未发现 Connection/Statement 手工管理泄漏（两模块均委托 IJdbcTemplate，唯一资源问题是上述 getCatalogs/getSchemas 的 ResultSet）。
- nop-dbtool 的 OrmDbDiffer/OrmModelDiffer 比对逻辑（含大小写规范化、默认值转义比较、displayName/comment 优先级归并）实现质量明显优于 nop-db-migration，未发现结构性缺陷；其风险集中在 DataBaseUpgrader 的无锁与全量发现（已列 P1/P2）。
