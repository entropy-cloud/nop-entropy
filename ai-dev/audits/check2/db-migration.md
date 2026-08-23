# db-migration 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-persistence/nop-db-migration + nop-persistence/nop-dbtool
- 文件数: 78（src/main/java；nop-db-migration 71 + nop-dbtool/nop-dbtool-core 7）
- 覆盖范围声明: 深读约 45 个文件（core 全部 8 个、executor 全部 17 个、precondition 全部 7 个、model 层关键类与全部 `_gen` 类的继承/集合声明、dbtool 全部 7 个、initialize 2 个），并交叉深读支撑代码以验证结论：`DslModelParser`/`DslBeanModelParser`/`DslXNodeToJsonTransformer`/`XDefinitionParser`（nop-xlang）、`KeyedList`/`ClassHelper`/`CharacterCase`（nop-commons/nop-core）、`BeanDefinitionBuilder`/`BeanDefinition`/`BeanContainerImpl`（nop-ioc）、`ErrorCode`/`MethodInvoker`（nop-api-core/nop-core）、`SqlExecHelper`/`DialectManager`/`DialectImpl`（nop-dao）、`migration.xdef`（nop-xdefs）、两模块 beans.xml、src/test 全部测试与 fixture（仅用于行为交叉验证，不作为发现来源）。其余文件（model 层空壳类、枚举）经逐文件 cat 确认为无逻辑壳类。grep 模式扫描（CCE/Exception/循环/注入/资源/方言）覆盖 100% 文件。未覆盖区域: 无（全部 78 个文件均至少经过声明级阅读或全文阅读；发现仅来自深读确认的路径）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 2 |
| P1 | 4 |
| P2 | 7 |
| P3 | 3 |

## 发现列表

### [P0] xdef 声明的 17 种变更中 9 种（sql/alterColumn/createIndex/createView/renameTable/dropView/customChange/dbTypeFilter/executeMark）解析即 ClassCastException，对应执行器全部不可达

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationFileScanner.java:60-73`、`/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/model/_gen/_DbMigrationModel.java:168-173`、`/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/db-migration/migration.xdef:122-127`
- **维度**: D1 / D8
- **证据**:

`migration.xdef` 的 changeset 声明（注意 126 行 `">` 使标签提前闭合，`xdef:key-attr="id"` 断裂为被解析器忽略的孤立子元素，见 XDefinitionParser.parseChildren line 654 对 `xdef:` 前缀子元素的静默跳过）：
```xml
<changeset xdef:name="DbChangeset"
           xdef:body-type="list"
           xdef:bean-sub-type-prop="type"
           xdef:bean-child-name="change"
           xdef:bean-body-type="List&lt;io.nop.db.migration.model.DbChangeModel>">
           xdef:key-attr="id">
```

`_DbMigrationModel.setChangeset`（生成代码）：
```java
public void setChangeset(java.util.List<io.nop.db.migration.model.DbChangeModel> value){
  checkAllowChange();
  this._changeset = KeyedList.fromList(value, io.nop.db.migration.model.DbChangeModel::getId);
}
```

继承结构（grep `_gen` 全部生成类声明确认）：仅 CreateTableChange/DropTableChange/AddColumnChange/DropColumnChange/DropIndexChange/InsertDataChange/UpdateDataChange/DeleteDataChange 的 `_gen` 基类 `extends io.nop.db.migration.model.DbChangeModel`；`_SqlChange`、`_AlterColumnChange`、`_CreateIndexChange`、`_CreateViewChange`、`_RenameTableChange`、`_DropViewChange`、`_CustomChange`、`_DbTypeFilterChange`、`_ExecuteMarkChange` 均 `extends io.nop.core.resource.component.AbstractComponentModel`。

- **现状**: xdef schema 声明 changeset 支持 17 种变更元素并通过 schema 校验；MigrationEngine 与 beans.xml 注册了全部 17 种执行器。但解析链路上：key-attr 断裂导致 `DslXNodeToJsonTransformer.parseBodyList` 走 ArrayList 分支（`getXdefKeyAttr()==null`），`setChangeset` 被反射调用（`MethodPropertySetter` -> `ClassHelper.invoke` -> `method.invoke`，无泛型转换）后 `KeyedList.fromList` 对非 KeyedList 输入执行 `new KeyedList(..., DbChangeModel::getId)` + `addAllUnique`，`add` 内 `getKey(t)` 触发方法引用的 `checkcast DbChangeModel`，对 SqlChange 等实例抛 ClassCastException（被 `ClassHelper.invoke` 包为 NopException，无源码位置信息）。即使 key-attr 未断裂（KeyedList 分支），`MigrationFileScanner.backfillChangeTypes` 的 `for (DbChangeModel change : model.getChangeset())` for-each checkcast 同样抛 CCE。`MigrationFileScanner.CHANGE_TYPE_BY_CLASS` 仅映射 8 个类，其注释（line 49-58）自认"其余变更类在解析期即 ClassCastException"，但 xdef/执行器/beans.xml 仍完整声明注册这 9 种类型。
- **风险**: 用户按模块公共契约（migration.xdef + beans.xml 注册的执行器）编写含 `<sql>`、`<createIndex>` 等变更的迁移文件（schema 校验通过），`MigrationFileScanner.loadMigration` 即抛裸 CCE，整个 migrate 流程终止且报错无位置信息。SQL Server/Oracle 方言专属 DDL 只能靠 `<sql dbSpecific>` 表达，此路径不可用意味着跨方言迁移能力整体不可用。`dbTypeFilter`/`rollback` 的 `<changes>`（同样声明 `List<DbChangeModel>` 且 ref 了 SqlChange/CustomChange 等）同理。
- **建议**: 让 9 个变更模型类统一继承 DbChangeModel（并重新生成 `_gen`），或在 xdef 层收窄 changeset 允许的元素集与 bean-body-type 一致；修复 migration.xdef changeset 标签的 `">` 断裂恢复 key-attr；为 scanner 增加对未知元素类型的显式 NopException 报错（带文件位置）而非裸 CCE。
- **误报排除**: 全链路读过：`XDefinitionParser.parseNode/parseChildren`（key-attr 从属性读取、`xdef:` 前缀子元素被忽略）、`DslModelParser.doParseNode0`（非 editor 走 DslBeanModelParser）、`DslBeanModelParser.parseObject`（subTypeProp 参数确实未被使用，tag 名不写入 type）、`DslXNodeToJsonTransformer.parseBodyList`（key-attr 缺失走 ArrayList 分支）、`MethodPropertySetter.setProperty` -> `ClassHelper.invoke`（无泛型转换直接 method.invoke）、`KeyedList.fromList/add/getKey`（非 KeyedList 输入立即以 keyFn 提取 key）；grep 确认 19 个 `_gen` 变更类中恰好 8 个继承 DbChangeModel；测试 fixture `type-coverage/all-change-types.migration.xml` 注释亦承认 "the only tags that survive parsing"；`migrations/` 目录下 13 个使用 sql/createIndex/renameTable 的 fixture 未被任何测试引用（旁证该路径当前不可用）。

### [P0] insert/update/delete 变更的 `<column>` 元素解析为 DynamicObject，执行器遍历时 ClassCastException，XML 路径的数据变更全部不可用

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/db-migration/migration.xdef:365-382`、`/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/executor/InsertDataExecutor.java:64-75`、`/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/model/_gen/_InsertDataChange.java:51-54`
- **维度**: D1 / D8
- **证据**:

migration.xdef 中 insert/update/delete 的 column 元素无 `xdef:name`（对比 addColumn 的 `<column xdef:ref="ColumnDefinition"/>`）：
```xml
<insert xdef:name="InsertDataChange" id="!string" tableName="!string" schemaName="string">
    <columns xdef:body-type="list" xdef:key-attr="name">
        <column name="!string" value="string" valueNumeric="number" valueBoolean="boolean" valueDate="date"/>
    </columns>
</insert>
```

InsertDataExecutor 遍历强类型列模型：
```java
for (InsertColumnModel column : change.getColumns()) {
    ...
    values.append(escapeValue(column.getValue(), column.getValueNumeric(),
        column.getValueBoolean(), column.getValueDate(), dialect));
}
```
`_InsertDataChange.setColumns`: `KeyedList.fromList(value, io.nop.db.migration.model.InsertColumnModel::getName)`。

- **现状**: `<column>` 无 `xdef:name` 时 `DslBeanModelParser.parseObject` 中 `objName==null` 走 `super.parseObject`（DslXNodeToJsonTransformer），返回 DynamicObject。columns 因有 key-attr 走 KeyedList 分支，`fromList` 对 KeyedList 直接 cast 返回（不触发 keyFn checkcast），元素保持 DynamicObject 进入 `_columns`。执行时 `for (InsertColumnModel column : ...)` 的 for-each checkcast 对 DynamicObject 抛 ClassCastException。测试 fixture `it-migrations/V1.0.0__xml_path_e2e.migration.xml` 的注释直接承认该"still-open defect"："no insert/update/delete: the xdef does not declare xdef:name on their <column> elements, so columns parse to DynamicObject and the executors fail with ClassCastException"。UpdateDataExecutor（`for (UpdateColumnModel column : change.getColumns())`）同理。
- **风险**: xdef 声明、scanner 回填（InsertDataChange 继承 DbChangeModel，type 正常回填）、beans.xml 执行器注册三者俱全，用户按契约写 `<insert>` 数据初始化时执行期抛 CCE，migration 记为失败（failFast 时中止整个迁移链）。数据类变更（初始化数据、字典数据）在 XML 路径完全不可用。
- **建议**: 在 xdef 的 insert/update 的 `<column>` 上补 `xdef:name="InsertColumnModel"/"UpdateColumnModel"` 并重新生成模型；或让执行器接受 DynamicObject/Map 形式的列数据；补充从 XML 到执行的端到端测试（当前 TestChangeExecutors 全部程序化构造模型，绕过了解析层）。
- **误报排除**: 读过 `DslBeanModelParser.parseObject`（objName==null 走 super）与 `DslXNodeToJsonTransformer.parseObject`（DynamicObject.addProp 路径）；读过 `_InsertDataChange`/`_UpdateDataChange` 生成代码（KeyedList<InsertColumnModel> + `InsertColumnModel::getName` keyFn）；fixture 注释为测试作者对同一结论的独立确认；`type-coverage` 测试只做 parse 级断言（不执行 insert/update/delete），TestChangeExecutors 程序化构造，均未覆盖此路径。

### [P1] 迁移历史表 DDL 与查询硬编码单一方言，Oracle/MSSQL 不可用；`ensureHistoryTableExists(dialect)` 的 dialect 参数被完全忽略

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationHistoryManager.java:49`、`200-211`
- **维度**: D8（跨方言 DDL 生成）
- **证据**:
```java
// getExecutedVersions
String sql = "SELECT version FROM " + TABLE_NAME + " WHERE success = TRUE ORDER BY installed_on";

// buildCreateHistoryTableSQL：参数 dialect 从未使用
protected String buildCreateHistoryTableSQL(IDialect dialect) {
    return "CREATE TABLE " + TABLE_NAME + " (" +
        "version VARCHAR(200) NOT NULL PRIMARY KEY, " + ...
        "success BOOLEAN, " + ... ")";
}
```
- **现状**: 历史表 DDL 使用 `BOOLEAN` 列类型（Oracle 23c 之前、旧 MSSQL 不支持；应走 dialect 的类型映射，如 `dialect.stdToNativeSqlType`），查询使用 `success = TRUE` 字面量（Oracle/MSSQL 不支持布尔字面量）。`ensureHistoryTableExists(IDialect dialect)` 接收方言参数却完全不使用，属于 API 契约假象。
- **风险**: 平台通过 nop-dao 注册了 mysql/postgresql/oracle/mssql/mariadb/duckdb 等多方言（`_vfs/nop/dao/dialect/*.dialect.xml`），在 Oracle/MSSQL 上历史表建表或首次查询即失败，整个迁移功能不可用。H2/MySQL/PG 上可运行，故单方言测试（测试基类仅 H2）不暴露。
- **建议**: 用 `dialect.stdToNativeSqlType(StdSqlType.BOOLEAN, -1, -1)` 生成列类型，查询条件改为 `success = ?` 绑定布尔参数，或按方言生成字面量；删除或真正使用 dialect 参数。
- **误报排除**: 读过 IDialect 接口（`stdToNativeSqlType` 可用）；确认 nop-dao dialect 目录含 oracle/mssql 方言文件；AbstractMigrationTestCase 确认现有测试仅覆盖 H2；grep 确认 buildCreateHistoryTableSQL 方法体内无任何 dialect 引用。

### [P1] 各执行器生成的 DDL 硬编码 MySQL/H2 方言语法，在 PostgreSQL/Oracle/MSSQL 上产生语法错误

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/executor/CreateTableExecutor.java:76-89`、`AddColumnExecutor.java:73-88`、`AlterColumnExecutor.java:52-78`、`DropIndexExecutor.java:51-61`
- **维度**: D8（跨方言 DDL 生成）
- **证据**:

CreateTableExecutor（AUTO_INCREMENT 关键字 + MySQL 式表级 COMMENT）：
```java
if (column.isAutoIncrement()) {
    sb.append(" AUTO_INCREMENT");
}
...
if (StringHelper.isNotBlank(change.getRemark())) {
    sb.append(" COMMENT '").append(AddColumnExecutor.escapeComment(change.getRemark())).append("'");
}
```

AlterColumnExecutor（PostgreSQL 专有语法串联）：
```java
sb.append("ALTER TABLE ").append(dialect.escapeSQLName(change.getTableName()))
  .append(" ALTER COLUMN ").append(dialect.escapeSQLName(change.getColumnName()));
if (newType != null) {
    sb.append(" TYPE ").append(dialect.stdToNativeSqlType(newType, precision, -1).toString());
}
if (change.getNewNullable() != null) {
    if (change.getNewNullable()) sb.append(" DROP NOT NULL");
    else sb.append(" SET NOT NULL");
}
```

DropIndexExecutor（MySQL 式 `DROP INDEX name ON table`）：
```java
sb.append("DROP INDEX ");
sb.append(dialect.escapeSQLName(change.getName()));
if (StringHelper.isNotBlank(change.getTableName())) {
    sb.append(" ON ").append(dialect.escapeSQLName(change.getTableName()));
}
```
- **现状**: 执行器仅在表名/列名转义和类型映射上使用了 dialect，其余 DDL 语法全部硬编码：`AUTO_INCREMENT`（MySQL/H2/MariaDB 支持，PG 用 SERIAL/IDENTITY，Oracle 用 IDENTITY，MSSQL 用 IDENTITY）；列内联 `COMMENT '...'`（MySQL 专属，PG/Oracle 需独立 `COMMENT ON` 语句，MSSQL 无此语法）在 CreateTableExecutor 与 AddColumnExecutor 均存在；`ALTER COLUMN x TYPE y`（PG/H2 语法，MySQL 需 `MODIFY COLUMN`，MSSQL/Oracle 不允许同一语句多动作，且 `SET/DROP NOT NULL` 在 MySQL/MSSQL 上是语法错误）；`ADD COLUMN`（Oracle 不支持 `COLUMN` 关键字）。`ADD COLUMN` 之后的内联 `COMMENT` 同样是 MySQL 专属。测试 fixture 注释亦承认 "CreateTableExecutor always appends a MySQL-only table-level COMMENT clause"。
- **风险**: xdef 的设计目标注释明确写着"类似 Liquibase 的数据库无关性：使用抽象的变更类型，支持多数据库方言"，但同一变更在 PG/Oracle/MSSQL 上执行即报 SQL 语法错误，迁移在异构数据库环境不可用；带 remark 的 createTable 在 H2 上也会失败（fixture 被迫规避）。
- **建议**: 把 DDL 语句构造下沉到 IDialect 层（nop-orm 的 DdlSqlCreator 已有分方言实现可复用），或按 dialect 分支生成 COMMENT ON / SERIAL / MODIFY COLUMN 等变体。
- **误报排除**: 逐行读过 4 个执行器的 SQL 构造代码并对照 IDialect 能力（`escapeSQLName`/`stdToNativeSqlType` 是仅有的两处方言使用）；确认 nop-dao 方言注册表含 oracle/mssql/postgresql；fixture 注释独立确认 MySQL-only COMMENT 问题在 H2 上即触发。

### [P1] repeatable 迁移与 checksum 校验完全未实现：R__ 迁移执行一次后即使内容变更也永不重跑；checksum 算法本身不含变更内容

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:41-45,119-128,193-211`、`/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationContext.java:94`
- **维度**: D1 / D8
- **证据**:

静态导入但零使用的 checksum 错误码（grep 全模块无 `ERR_DB_MIGRATION_CHECKSUM_MISMATCH` 使用点）：
```java
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_CHECKSUM_MISMATCH;
```

migrate 对 repeatable 与 versioned 一视同仁地跳过：
```java
if (executedVersions.contains(version)) {
    continue;
}
```

checksum 只拼接版本号/描述/变更类型名，不含任何变更内容：
```java
sb.append(migration.getVersion()); ... sb.append(migration.getDescription());
for (DbChangeModel change : migration.getChangeset()) {
    if (change.getType() != null) sb.append(change.getType());
}
```
- **现状**: xdef 文档（migration.xdef line 23 与 `@type` 注释）承诺 repeatable 迁移"每次校验和变化都重新执行"；`MigrationContext.isValidateChecksum()`（默认 true）存在但引擎从不读取；`getMigrationByVersion`/`MigrationRecord.checksum` 的读写链路存在但无比较逻辑。`record.setType(isRepeatable(version) ? "REPEATABLE" : "VERSIONED")` 只影响历史记录标签。另外 checksum 算法只拼 `change.getType()`，修改迁移文件里的 SQL/列定义后 checksum 不变，即使将来接上校验也无法检测内容变更。
- **风险**: 用户按文档使用 R__ 前缀的可重复迁移（典型场景：视图定义、统计脚本），首次执行后修改内容重新部署，迁移被静默跳过，数据库中的视图/脚本保持旧版本——数据/结构漂移且无任何告警。这是与声明的功能契约（Flyway 语义）的实质偏离。
- **建议**: migrate 前对 repeatable 版本读取历史 checksum 并比较，不一致则重新执行并 update 历史行；checksum 计算纳入变更体（各 change 的关键属性序列化或原始 XML 内容哈希）；实现或移除 `isValidateChecksum` 配置项。
- **误报排除**: 全文读过 MigrationEngine/MigrationExecutor/MigrationHistoryManager，grep 确认 `ERR_DB_MIGRATION_CHECKSUM_MISMATCH`、`isValidateChecksum`、`getMigrationByVersion` 在 main 代码中的全部引用点（后两者无调用方/无比较逻辑）；xdef 文档原文确认 repeatable 语义承诺。

### [P1] CreateTableExecutor 静默丢弃表级 `<primaryKey>`/`<uniqueConstraint>`/`<foreignKey>` 声明，生成的表缺少主键/唯一/外键约束

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/executor/CreateTableExecutor.java:52-92`、`/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/model/_gen/_CreateTableChange.java:28-66`
- **维度**: D1 / D8
- **证据**:

xdef（migration.xdef line 169-198）与生成模型声明了表级约束属性：
```java
// _CreateTableChange.java
private io.nop.db.migration.model.ForeignKeyConstraint _foreignKey;
private io.nop.db.migration.model.PrimaryKeyConstraint _primaryKey;   // 注释: 主键约束（可选，也可以在列上直接指定 primaryKey）
private io.nop.db.migration.model.UniqueConstraint _uniqueConstraint;
```

而 buildCreateTableSql 只遍历列：
```java
if (change.getColumns() != null) {
    for (ColumnDefinition column : change.getColumns()) {
        ...
        if (column.isPrimaryKey()) sb.append(" PRIMARY KEY");
```
grep 确认 CreateTableExecutor 全文无 `getPrimaryKey()`/`getUniqueConstraint()`/`getForeignKey()` 引用。
- **现状**: xdef 注释明确引导用户"主键约束（可选，也可以在列上直接指定 primaryKey）"，但使用表级 `<primaryKey columnNames="...">`（复合主键的唯一表达方式，列级 primaryKey 无法表达多列顺序）或 `<uniqueConstraint>`/`<foreignKey>`（模型类 ForeignKeyConstraint/UniqueConstraint/PrimaryKeyConstraint 均已生成）时，执行器完全不读取这些属性，建出的表无主键/无约束，且迁移记录 success=true。
- **风险**: 复合主键表、外键约束、唯一约束静默丢失，不报任何错误；依赖主键/唯一性的数据完整性（去重、引用完整性）失效，后续 ORM 访问按有主键假设工作会出错。触发路径现实：按 xdef 声明编写表级约束即可。
- **建议**: buildCreateTableSql 补充对 `getPrimaryKey()`（`PRIMARY KEY (cols)`）、`getUniqueConstraint()`（`CONSTRAINT ... UNIQUE (...)`）、`getForeignKey()`（`FOREIGN KEY ... REFERENCES ...`，注意 ForeignKeyAction 到 `ON DELETE CASCADE/SET NULL/NO ACTION/RESTRICT` 的映射已定义在枚举中但同样无人消费）的 DDL 生成。
- **误报排除**: 读过 _CreateTableChange 生成代码确认三个约束属性与 getter 存在；读过 CreateTableExecutor 全文确认零引用；读过 xdef createTable 段确认三种约束元素是对外声明的合法语法；ForeignKeyAction 枚举有 onDelete/onUpdate 映射值但 grep 无消费者。

### [P2] DbMigrationErrors 大量错误码把常量名/描述/argNames 传错位置，错误码全部退化为 "io.nop.db.migration"

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/DbMigrationErrors.java:17-80`
- **维度**: D4 / D8
- **证据**:

ErrorCode 仅有两个 define 重载（`ErrorCode.java:27-33`）：
```java
public static ErrorCode define(String errorCode, String message, String... argNames) {
    return new ErrorCode(-1, errorCode, message, argNames);
}
```

而本模块大多写成三参形态（错误码/描述/argNames 整体错位）：
```java
ErrorCode ERR_DB_MIGRATION_UNKNOWN_CHANGE_TYPE = ErrorCode.define(
    "io.nop.db.migration",                          // 被当作 errorCode
    "ERR_DB_MIGRATION_UNKNOWN_CHANGE_TYPE",         // 被当作 message
    "Unknown change type: {changeType}");           // 被当作 argNames[0]
```
仅 `ERR_DB_MIGRATION_HISTORY_QUERY_FAILED`（line 73-77）采用了与 DaoErrors 一致的正确两参+argNames 形态。
- **现状**: `define(String,String,String...)` 把 "io.nop.db.migration" 绑定为 errorCode、"ERR_..." 常量名绑定为 message、可读描述文本进入 argNames。结果：所有此类错误的 `getErrorCode()` 均为同一字符串 "io.nop.db.migration"（无区分度，无法按码定位），`getDescription()` 为常量名而非人类可读信息；NopException 的参数绑定机制以 argNames 为键收集参数，argNames 中混入描述文本会造成参数表错乱。
- **风险**: 抛出这些异常时错误报告/日志/前端展示显示常量名而非语义描述，错误码去重与分类失效。当前无数据危害，属错误处理规范缺陷。
- **建议**: 参照 DaoErrors 统一为 `define("nop.err.db-migration.xxx", "描述", ARG_...)` 两参+argNames 形态。
- **误报排除**: 读过 ErrorCode.java 全文确认无 `(String,String,String)` 三参重载，varargs 匹配路径唯一；对比 DaoErrors 的正确用法；确认受影响常量（除 HISTORY_QUERY_FAILED 外全部）及其抛出点（SqlExecutor/MigrationEngine/MigrationHistoryManager 等实际在用）。

### [P2] precondition 体系为死代码：模型 `preconditions` 字段与 5 个 IPreconditionChecker 无任何调用方，且 3 个 checker 的 INFORMATION_SCHEMA 查询不兼容 Oracle、大小写匹配在 MySQL 可失效

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:153-175`、`/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/precondition/TableExistsChecker.java:53-57`、`ColumnExistsChecker.java:34-35`、`ForeignKeyExistsChecker.java:36-38`
- **维度**: D8 / D1
- **证据**:

xdef（line 44-47）承诺 "只有满足所有前置条件时才会执行此迁移"，但 MigrationEngine.executeMigration 不做任何前置检查：
```java
protected MigrationRecord executeMigration(DbMigrationModel migration, MigrationContext context) {
    long startTime = System.currentTimeMillis();
    List<DbChangeModel> changeset = migration.getChangeset();
    if (changeset != null) {
        for (DbChangeModel change : changeset) {
            executeChange(change, context);
        }
    }
```
grep 全模块：`IPreconditionChecker`/`getPreconditions` 在 precondition 包与 `_gen` 之外零引用。

checker 使用 INFORMATION_SCHEMA + toUpperCase 匹配：
```java
String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?";
... .sql(sql, tableName.toUpperCase(), ...)
```
- **现状**: 解析出的 `preconditions` 被引擎完全忽略（带前置条件的迁移无条件执行）；TableExists/ColumnExists/ForeignKeyExists 三个 checker 依赖 `INFORMATION_SCHEMA`（Oracle 不存在该 schema，应使用 ALL_TABLES 等）并用 `toUpperCase` 精确匹配（MySQL `lower_case_table_names=1` 时 TABLE_NAME 存小写，匹配不到）。IndexExistsChecker 用 JDBC 元数据（正确方向），但也无人调用。
- **风险**: 用户按 xdef 声明前置条件（如 expect="notExists" 实现幂等建表），条件被静默忽略，迁移总是执行——与"前置条件保护"的契约相反，可能造成重复执行类错误。checker 一旦接线，Oracle/大小写问题会转化为误判。
- **建议**: 在 executeMigration 前接入 precondition 检查（ERR_DB_MIGRATION_PRECONDITION_FAILED 已定义未用）；checker 元数据探测统一改走 JDBC DatabaseMetaData（参照 IndexExistsChecker/MigrationHistoryManager.tableExists 的做法）。
- **误报排除**: grep 确认 IPreconditionChecker/getPreconditions 的全部引用点；读过 5 个 checker 全文与 MigrationEngine/Executor 全文确认无调用；TestPreconditionCheckers 直接调用 checker（测试内闭环），不证明引擎接线。

### [P2] 模型控制字段 ignore/runOn/contexts/labels/failOnError/type 全面未接线，声明的迁移控制语义均不生效

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:123-148`、`/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/model/_gen/_DbMigrationModel.java:66-79,421-454`
- **维度**: D8
- **证据**:

migrate 主循环仅判断版本号：
```java
for (DbMigrationModel migration : migrations) {
    String version = migration.getVersion();
    if (executedVersions.contains(version)) {
        continue;
    }
    try {
        MigrationRecord record = executeMigration(migration, context);
```
xdef 声明 `ignore`（"用于临时禁用某个迁移"）、`runOn`（always/onChange/never）、`contexts`（"用于在不同环境（开发、测试、生产）中控制迁移的执行"）、`failOnError`、`type`（versioned/repeatable）；`MigrationContext.matchesContext`/`hasLabel` 亦无调用方。grep 确认 migrate/executeChange 路径无 `isIgnore()`/`getRunOn()`/`getContexts()`/`isFailOnError()`/`getLabels()` 读取。
- **现状**: 设置 `ignore="true"` 的迁移照常执行；`runOn="never"` 照常执行；contexts/labels 过滤不存在（生产库会执行仅面向测试环境的迁移）；`failOnError="false"` 被全局 context.failFast 覆盖，粒度承诺不成立。
- **风险**: 与 xdef 文档承诺的环境隔离/禁用语义直接冲突，多环境部署时可能执行不该执行的迁移（数据污染路径），但因这些字段目前只能通过 XML 声明（该 XML 路径的多数变更类型本身受 P0 影响），当前实际暴露面有限，故定 P2。
- **建议**: migrate 循环补充 `migration.isIgnore()` 跳过、`context.matchesContext(migration.getContexts())` 过滤、runOn 分支；failOnError 与全局 failFast 取与。
- **误报排除**: 读过 migrate/rollback/executeMigration/executeChange 全部路径；grep 五个访问器在 core 包的引用（仅 MigrationContext 自身定义 matchesContext/hasLabel 无调用方）。

### [P2] MigrationResult.isSuccess 恒为 true：migrate 记入失败记录后不更新整体成功标志

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:134-147`、`/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationResult.java:34-49`
- **维度**: D1 / D8
- **证据**:

failFast=false 时失败仅记入 failedRecord，result 的 success 字段无人触碰：
```java
} catch (Exception e) {
    MigrationRecord failedRecord = new MigrationRecord();
    ...
    failedRecord.setSuccess(false);
    localHistoryManager.recordMigration(failedRecord);
    result.addRecord(failedRecord);
    if (context.isFailFast()) {
        throw NopException.adapt(e);
    }
}
```
MigrationResult 中 `success` 初始 true，只有 `setErrorMessage` 会置 false，而 migrate/rollback 从不调用。
- **现状**: `result.isSuccess()` 对包含失败记录的结果仍返回 true；调用方（当前为测试与未来集成方）若以 isSuccess 判断迁移整体成败（failFast=false 场景）会误判成功。getExecutedCount() 返回 records.size() 把失败记录也计为"executed"，语义同样含混。
- **风险**: 上层编排（如启动脚本据此决定是否告警/回滚）得到假阳性成功。当前仓库内无 main 代码消费方，危害未激活，定 P2。
- **建议**: addRecord 时同步 `if (!record.isSuccess()) this.success = false;`，或 migrate 结束时聚合；getExecutedCount 过滤 `record.isSuccess()`。
- **误报排除**: 读过 MigrationResult 全文（setErrorMessage 是唯一置 false 入口）与 migrate/rollback 两条路径（均未调用 setErrorMessage）。

### [P2] recordMigration 用 DELETE+INSERT 两步替换历史行，非原子，中断时丢失迁移历史

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationHistoryManager.java:84-118`
- **维度**: D1 / D2
- **证据**:
```java
public void recordMigration(MigrationRecord record) {
    String deleteSql = "DELETE FROM " + TABLE_NAME + " WHERE version = ?";
    jdbcTemplate.executeUpdate(... .sql(deleteSql, record.getVersion()) ...);

    String sql = "INSERT INTO " + TABLE_NAME + " (version, ...) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    jdbcTemplate.executeUpdate(... .sql(sql, record.getVersion(), ...) ...);
}
```
- **现状**: 重试/重跑场景（先前失败记录存在）先删后插，两条语句间无事务包裹（IJdbcTemplate.executeUpdate 各自独立提交）。若进程在两步之间崩溃或 INSERT 因任何原因失败（如 description 超过 VARCHAR(500)），该版本的历史行已删除且未重建。getExecutedVersions 随后不包含该版本，重跑会再次执行本已执行过的变更（DDL 幂等性无保障时直接报错或产生重复数据）。另注：checksum VARCHAR(100) 足够（md5 32 字符），description VARCHAR(500) 与超长描述之间存在同类截断/失败风险。
- **风险**: 异常路径下的历史丢失导致重复执行；并发两个迁移进程同时 DELETE+INSERT 也可能交错产生主键冲突（本模块无锁机制，幂等仅靠 executedVersions 预读）。
- **建议**: 改为单条 upsert（按方言 MERGE/ON DUPLICATE KEY/ON CONFLICT），或显式包事务；至少把 INSERT 失败时回补 DELETE 掉的旧行。
- **误报排除**: 读过 IJdbcTemplate/SqlExecHelper 确认两次 executeUpdate 无隐式事务合并；读过 migrate 调用点确认 recordMigration 在 executeMigration 成功后调用（此时 DDL 已生效，历史丢失即意味着 DDL 重复执行）。

### [P2] beans.xml 注册的 nopMigrationHistoryManager 与类构造器契约不匹配；MigrationEngine.historyManager 字段为死代码

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/resources/_vfs/nop/db-migration/beans/default.beans.xml:11-13`、`/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:49,59-62,102-108`
- **维度**: D7 / D8
- **证据**:

MigrationHistoryManager 仅有带参构造器：
```java
public MigrationHistoryManager(IJdbcTemplate jdbcTemplate, String querySpace) {
```
beans.xml 声明（无构造器参数）：
```xml
<bean id="nopMigrationHistoryManager" class="io.nop.db.migration.core.MigrationHistoryManager" ioc:auto-inject="true">
</bean>
```
MigrationEngine 中字段被 migrate() 内新建的 localHistoryManager 完全取代：
```java
private MigrationHistoryManager historyManager;
...
public MigrationResult migrate(MigrationContext context) {
    MigrationHistoryManager localHistoryManager = new MigrationHistoryManager(
        context.getJdbcTemplate(), context.getQuerySpace());
```
- **现状**: NopIoC 的 `BeanDefinitionBuilder.autowireConstructorArgs` 会对无参配置的 bean 自动选择 public 构造器并注入，IJdbcTemplate 可按类型注入，但 `String querySpace` 参数无 @InjectValue 配置将解析为 null（构造器内 fallback "default"，勉强可用但与显式注入语义不符）；该 bean 无任何引用方，纯冗余。MigrationEngine 的 historyManager 字段（含 `MigrationEngine(IJdbcTemplate, String)` 构造器注入路径）在 migrate/rollback 中均被 localHistoryManager 覆盖，注入的 jdbcTemplate/querySpace 实际失效——两份语义冲突的初始化路径并存。
- **风险**: bean 定义本身可实例化但语义悬空；未来有人注入 nopMigrationHistoryManager 或依赖 engine.getHistoryManager() 会拿到与 migrate 实际使用不一致的实例（不同 querySpace）。IoC 配置与实现漂移，可维护性风险。
- **建议**: 删除 nopMigrationHistoryManager bean 定义（或补 ioc:constructor-arg）；MigrationEngine 统一使用单一 historyManager 来源（字段优先、为空时再建）。
- **误报排除**: 读过 nop-ioc 的 BeanDefinitionBuilder.autowireConstructorArgs（自动选参构造器）与 BeanContainerImpl.start（非 lazy singleton eager 实例化）确认该 bean 会在容器启动时被构建而非报错；grep 确认 nopMigrationHistoryManager 与 getHistoryManager() 无引用方。

### [P2] JdbcMetaDiscovery.uniqueConstraintByIndexName 对 null INDEX_NAME 直接 NPE（H2 分支 null.replaceAll；其他方言 CharacterCase.normalize(null)）

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-dbtool/nop-dbtool-core/src/main/java/io/nop/dbtool/core/discovery/jdbc/JdbcMetaDiscovery.java:596-654`
- **维度**: D1
- **证据**:

discoverUniqueKeys 未判空即转换：
```java
try (ResultSet rs = metaData.getIndexInfo(catalog, schemaPattern, table.getTableName(), true, false)) {
    while (rs.next()) {
        String indexName = rs.getString("INDEX_NAME");
        ...
        indexName = uniqueConstraintByIndexName(indexName);
```
uniqueConstraintByIndexName：
```java
if ("h2".equals(this.dialect.getName())) {
    indexName = indexName.replaceAll("_(INDEX|index)_.$", "");
}
return normalizeColName(indexName);   // CharacterCase.normalize(null) -> str.toUpperCase() NPE
```
对比 discoverIndexes（line 559）对 columnName 做了 `StringHelper.isEmpty` 检查（但 indexName 同样未检查，仅因 HashMap 接受 null key 而"侥幸"不崩）。
- **现状**: JDBC `getIndexInfo` 的契约允许返回无索引信息的行（如 MySQL Connector/J 的表统计行 INDEX_NAME 为 null；PG 部分版本对统计项返回 null）。H2 方言命中 null.replaceAll 直接 NPE；非 H2 方言 `CharacterCase.normalize(null)`（`str.toUpperCase(Locale.ROOT)`）NPE——前提是 dialect 配置了 columnNameCase（如 postgresql.dialect.xml `columnNameCase="lower"`）。
- **风险**: DataBaseUpgrader 自动升级流程（discover -> differ -> upgrade）在上述驱动行为下抛裸 NPE，自动升级中断。触发依赖具体驱动/数据库的元数据返回形态，属特定条件触发，定 P2。
- **建议**: 在 uniqueConstraintByIndexName 入口对 null/空 indexName 直接 continue（调用方处理），与 discoverIndexes 的 columnName 判空对齐。
- **误报排除**: 读过 CharacterCase.normalize 实现（无 null 防护）；读过 postgresql.dialect.xml 确认 columnNameCase="lower" 非空；对照 discoverIndexes 已有判空写法说明作者在其他分支意识到了该形态。

### [P3] dbType 匹配的方言标识漂移：文档宣称 "sqlserver"，实际 dialect.getName() 返回 "mssql"

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/executor/DbTypeFilterExecutor.java:53`、`SqlExecutor.java:65-74`、`/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/db-migration/migration.xdef:347`
- **维度**: D8
- **证据**:

匹配逻辑：
```java
String currentDbType = dialect.getName().toLowerCase();
for (String allowedType : dbTypes) {
    if (allowedType != null && allowedType.toLowerCase().equals(currentDbType)) {
```
xdef 注释宣称取值集：`@dbType 数据库类型：mysql | postgresql | oracle | sqlserver | h2 等`。而 `DialectImpl.getName()` 取 dialect.xml 文件名去后缀（nop-dao 的 `_vfs/nop/dao/dialect/mssql.dialect.xml` -> "mssql"）。
- **现状**: 用户按文档写 `dbType="sqlserver"`（dbSpecific/dbTypeFilter）时与 "mssql" 永不匹配，对应 SQL/子变更被静默跳过，无告警。
- **风险**: SQL Server 用户的方言专属变更静默失效。
- **建议**: 文档改为 "mssql"，或匹配时建立别名表（sqlserver->mssql）。
- **误报排除**: 读过 DialectImpl.getName（文件名派生）与 dialect 注册目录文件名清单；确认 DbTypeFilterExecutor/SqlExecutor 是 getName() 的仅有两处匹配消费方。

### [P3] MigrationEngine.migrate 原地排序调用方传入的 migrations 列表（副作用 + 不可变列表抛 UnsupportedOperationException）

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:110-117`
- **维度**: D1
- **证据**:
```java
List<DbMigrationModel> migrations;
if (context.getMigrations() != null && !context.getMigrations().isEmpty()) {
    migrations = context.getMigrations();
} else {
    migrations = scanner.scan(context.getMigrationPaths());
}
Collections.sort(migrations, MigrationVersionComparator.INSTANCE);
```
- **现状**: 走 context.getMigrations() 分支时直接对调用方列表原地排序（修改外部可见状态）；若传入 `List.of(...)`/`List.copyOf(...)` 等不可变列表，`Collections.sort` 抛 UnsupportedOperationException（注：`Collections.singletonList` 因 set(0) 可用而不触发，现有测试恰好只用 singletonList/ArrayList，未暴露）。rollback 路径（line 246）已正确改为拷贝后 reverse，说明同类的拷贝惯例此处遗漏。
- **风险**: 调用方列表顺序被隐式篡改；不可变列表输入直接崩溃。低概率边界，定 P3。
- **建议**: `migrations = new ArrayList<>(migrations)` 后再 sort。
- **误报排除**: 读过 Collections/SingletonList 的 set 语义（index 0 允许）解释测试通过原因；对照 rollback 的拷贝写法。

### [P3] MigrationVersionComparator 存在死代码与不可达分支：parseVersionParts 无调用方，compare 中 d1==null 判断不可达

- **文件**: `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check/nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationVersionComparator.java:54-61,116-130`
- **维度**: D1（可维护性）
- **证据**:
```java
if (r1 && r2) {
    String d1 = extractDescription(v1);
    String d2 = extractDescription(v2);
    if (d1 == null && d2 == null) return 0;   // extractDescription 只返回 "" 或子串，永不为 null
    if (d1 == null) return -1;
    if (d2 == null) return 1;
    return d1.compareTo(d2);
}
```
`parseVersionParts`（且 NumberFormatException 静默吞为 0）grep 全模块无调用方；比较实际走 `StringHelper.compareVersions`。
- **现状**: 三个 null 分支不可达；parseVersionParts 为死代码且其异常吞噬模式（解析失败静默按 0 处理）若被未来调用方采用会引入版本排序错误。
- **风险**: 当前无运行时影响；维护性噪音。
- **建议**: 删除 parseVersionParts 与不可达 null 分支，或修正 extractDescription 契约注释。
- **误报排除**: 读过两方法全文与 grep 调用点（compare 使用 StringHelper.compareVersions 而非 parseVersionParts）。

## 补充说明（不计入发现）

- `generateRollbackSql`（含 `DROP TABLE IF EXISTS` 等 Oracle 不支持的语句）在 main 代码无任何调用方，属死代码；其方言问题已并入 P1 跨方言条目语境，不单列。
- `UpdateDataExecutor`/`DeleteDataExecutor` 的 `where` 子句直接拼接 SQL：输入来自受控的迁移 XML 资源文件而非用户输入，不构成注入面；`InsertDataExecutor.escapeValue` 对单引号做了转义、类型化字面量（数值/布尔/日期）处理正确。
- `CustomChangeExecutor`/`CustomConditionChecker` 执行 XPL 脚本：脚本体来自平台资源加载链（受信），与平台其他 XPL 用法一致，不列为安全问题。
- `DataBaseUpgrader` 逐 querySpace 全库 discover（`discover(null,null,"%")`）为有意设计（代码注释说明无法确定表匹配模式），每个 querySpace 仅执行一次，无循环内重复 IO。
- dbtool 的 `DataBaseUpgradeInitializer` 使用 `@Inject protected` 字段注入并经 dbtool-defaults.beans.xml 注册（ioc:condition 控制启用），符合 Nop IoC 规范；未发现 D7 类问题。
- 深读范围内未发现流/连接泄漏（JdbcMetaDiscovery 的 ResultSet 均用 try-with-resources、连接按来源条件关闭；两模块无文件流操作）、未发现 SimpleDateFormat 等非线程安全类共享、未发现 bare RuntimeException（executor 层异常均由 NopException 或引擎统一 adapt）。
