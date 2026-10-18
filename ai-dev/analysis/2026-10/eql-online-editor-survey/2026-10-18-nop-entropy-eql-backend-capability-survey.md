# Nop Entropy EQL 后端能力调研（证据底账）

> Status: resolved
> Date: 2026-10-18
> Scope: nop-entropy / EQL 解析编译执行 / 元数据 / 数据源 / 安全 / 可复用 Web API
> Conclusion: 后端最短落地路径是新建轻量 `nop-eql-console` 模块：用 `IOrmSessionFactory.compileSql` 做「EQL→SQL+列元数据」编译预览，用 `IQueryExecutor.executeQuerySql` 执行编译产物，并自建只读/行数/超时/数据权限护栏；`nop-metadata`、`nop-report` 只能复用局部能力，不能直接承载任意 EQL 控制台。

---

## 1. 调研问题与方法

要回答：EQL 编译上下文如何构造、依赖哪些 bean；现有哪个 API 能执行任意 EQL 并拿到行列元数据；运行时实体元数据与 `querySpace` 从哪来、能否安全暴露；多数据源与方言能力位如何配置；`nop-metadata`/`nop-report`/`nop-dev-tools` 的宿主边界；权限、只读、超时、最大行数、事务、租户、逻辑删除的真实行为；错误如何结构化、行列是否保留；测试基础设施如何覆盖。

方法：只读检索源码与 `docs-for-ai/` 文档，交叉验证签名与调用链；不构建、不改代码；无法确认处标「未确认」。

前置结论：仓库内**不存在**可直接给前端调用的「任意 EQL 执行」BizModel。全仓 `@Name("eql")` 无命中，`@Name("sql")` 仅命中 `nop-metadata` 的 SQL 视图预览/创建（`nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/INopMetaEntityBiz.java:41,49`）。前端 sibling 报告提到的 `/api/report/execSql` 在后端无实现（全仓 `execSql|executeSql` 仅命中测试与 sql-lib 的 `AbstractDaoHandler`）。

## 2. EQL 编译链路

### 2.1 入口与调用链

接口 `ISqlCompiler.compile(String name, String sql, ISqlCompileContext context)`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/ISqlCompiler.java:13`），实现 `EqlCompiler.compile`（`.../compile/EqlCompiler.java:56`）。业务侧通常不直接 new，而是：

```text
IOrmTemplate.getSessionFactory().compileSql(name, eql, disableLogicalDelete)   // IOrmTemplate.java:32
  -> SessionFactoryImpl.compileSql(...)                                        // SessionFactoryImpl.java:339-355
    -> LoadedOrmModel.compileSql(...)                                          // LoadedOrmModel.java:92-133
      -> new EqlCompileContext(env, this, ...)                                 // LoadedOrmModel.java:111-112,130-131
      -> new EqlCompiler().compile(name, eql, ctx)                             // LoadedOrmModel.java:113,132
```

`IOrmSessionFactory` 公开三参/七参两个 `compileSql`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmSessionFactory.java:66-69`）；`LoadedOrmModel` 带 `QueryPlanCache`，缓存键含 `name/text/disableLogicalDelete/allowUnderscoreName/enableFilter`，显式非默认 `astTransformer` 时绕过缓存（`LoadedOrmModel.java:99-133`）。

### 2.2 `ISqlCompileContext` 与 bean 依赖

接口 7 个方法（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/ISqlCompileContext.java`）。生产实现 `EqlCompileContext`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/compile/EqlCompileContext.java:19-73`）：

| 方法 | 作用 | 生产来源 |
|---|---|---|
| `isDisableLogicalDelete()` | 跳过逻辑删除过滤 | 调用方入参 |
| `isAllowUnderscoreName()` | 下划线命名兼容 | 调用方入参 |
| `isEnableFilter()` | 注入 ORM 层数据权限 | 调用方入参 / EQL `@enableFilter` |
| `getAstTransformer()` | 方言 AST 改写 | `IPersistEnv.getDefaultAstTransformer()` |
| `getDialectForQuerySpace(qs)` | querySpace→方言 | `IPersistEnv.getDialectProvider()` |
| `resolveEntityTableMeta(name)` | 实体名→表/列/关联元数据 | `ILoadedOrmModel` + `SqlExprMetaCache` |
| `getAliasGenerator()` | 表别名 | `new SeqAliasGenerator()` |

构造参数：`IPersistEnv env`、`ILoadedOrmModel loadedOrmModel`、`disableLogicalDelete`、`IEqlAstTransformer`、`allowUnderscoreName`、`enableFilter`（`EqlCompileContext.java:30-39`）。方言取 `env.getDialectForQuerySpace`（`:61-63`）；实体取 `ormModel.resolveEntityTableMeta`（`:66-68`），底层先按全名查 `IOrmModel.getEntityModel`，允许下划线时再走 `getEntityModelBySnakeCaseName`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/meta/SqlExprMetaCache.java:43-49`），并为每个实体表绑定其 querySpace 的方言（`:52-54`）。

**最小依赖**：只需「实体名→`ISqlTableMeta`」+「querySpace→`IDialect`」；`IOrmColumnBinderEnhancer` 可空（测试用 `new EntityTableMeta(m, null, dialect)`，`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestEqlCompileSql.java:143-183`）。因此「只编译不执行」不必拉起完整 `IOrmSession`，但必须能访问运行时 `IOrmModel`。

### 2.3 编译关键节点

1. 解析 `new EqlASTParser().parseFromText(null, eql)`（`EqlCompiler.java:57`）；parser 只做 parse+AST build，不绑定 ORM（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTParser.java:22-37`）。
2. 多语句拒绝 → `nop.err.eql.not-support-multiple-statement`（`EqlCompiler.java:58-59`）。
3. 事务语句拒绝 → `nop.err.eql.not-support-transaction-statement`，带 `.source(stm)`（`EqlCompiler.java:69-73`）。
4. 过滤开关 `context.isEnableFilter() || stm.hasDecorator("enableFilter")`（`EqlCompiler.java:80`）。
5. 语义分析与改写 `EqlTransformVisitor.transform(program)`（`EqlCompiler.java:88-89`），querySpace/方言在此确定（`EqlTransformVisitor.java:310-321,938-953`）。
6. SQL 生成 `AstToSqlGenerator(dialect, enableFilter)`，pretty=true（`EqlCompiler.java:99-101`）。
7. 产物 `CompiledSql` 填 `sql/querySpace/statementKind/paramBuilders/readEntityModels/writeEntityModel/useTenantModel`（`EqlCompiler.java:108-119`），SELECT 再填 `dataSetMeta/columnBinders/fieldMetas` 与结构标志（`:121-176`）。

### 2.4 编译产物 `ICompiledSql`

`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ICompiledSql.java`：`getQuerySpace():29`、`getStatementKind():31`、`getDataSetMeta():36`、`getReadEntityNames():41`、`getWriteEntityName():46`、`getColumnBinders():48`、`getFieldMetas():53`、`getSql():58`、`buildParams(input):63`、结构标志 `hasGroupBy/hasHaving/hasDistinct/hasSetOp/hasForUpdate/isBareCount/hasAggregate/getOrderByOrdinals:72-117`。

列元数据由 `BaseDataFieldMeta(fieldName, sourceFieldName, ownerEntityName, stdDataType, stdSqlType, false)` 构造（`EqlCompiler.java:266-277`）。**对在线编辑器的直接价值**：一次编译即可同时给出生成 SQL、列名/来源实体/StdDataType/StdSqlType、是否只读查询、querySpace，无需真的执行。

### 2.5 错误码

`OrmEqlErrors`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/OrmEqlErrors.java`）中最相关：多语句 `nop.err.eql.not-support-multiple-statement:190`、事务语句 `not-support-transaction-statement:120`、投影 `unsupported-projection-type:129`、未知实体 `unknown-entity-name:142`、未知列 `unknown-column-name:196`、实体无属性 `unknown-field-in-entity:235`、未知 querySpace `unknown-query-space:193`、多 querySpace `not-allow-multiple-query-space:187`、表源未解析 `table-source-not-resolved:199`、未知函数 `unknown-function:222`、方言能力不足 `dialect-not-support-feature:265`、ilike 不支持 `:231`、IN 列表超限 `:331`、upsert 冲突键 `:258`、insert-select-returning `:250`。

解析层错误不走这些码，而是 ANTLR 通用码：`nop.err.antlr.common.parse-fail`、`nop.err.antlr.not-end-properly`、`nop.err.antlr.lexer-parse-fail`、`nop.err.antlr.string-literal-not-end`（`nop-kernel/nop-antlr4/nop-antlr4-common/src/main/java/io/nop/antlr4/common/AntlrErrors.java:22-28`）。注意：多语句分支未 `.source(...)`（`EqlCompiler.java:58-59`），该错误无行列。

## 3. EQL 执行链路

### 3.1 现有可执行入口

`SQL` 对象承载 EQL 文本，`IOrmSession/IOrmTemplate` 执行时内部先编译再落 JDBC：`IOrmSession.executeQuery(SQL, LongRangeBean, Function<? super IDataSet,T>)`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmSession.java:245`）、`executeUpdate(SQL):243`、`executeStatement(SQL,range,callback,cancelToken):247`。`IOrmTemplate extends ISqlExecutor`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmTemplate.java:30`），实现转给当前 session（`nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/OrmTemplateImpl.java:142-150`）；便捷方法 `findAll/findPage/findFirst/executeQuery`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/api/ISqlExecutor.java:160-216`）。

实际编译在 `JdbcQueryExecutor`：`executeQuery` 先 `env.compileSql(eql.getName(), eql.getText(), eql.isDisableLogicalDelete(), eql.isAllowUnderscoreName(), eql.isEnableFilter())`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java:89-95`），再 `compiled.buildParams(markerValues)`（`:105`）、`transformEQL`（`:113`）、`jdbc().executeQuery(...)` 并用 `TransformedDataSet` 按 `compiled.getDataSetMeta()` 重塑列（`:120-123`）。`executeUpdate` 同样先编译（`:69-86`）；`executeStatement` 走 `IComplexDataSet` 且支持取消（`:127-152`）。

### 3.2 为什么必须「compile 成 SQL 再执行」

`IOrmSession.executeQuery(SQL)` 会把 `SQL.getText()` 当 **EQL** 再解析（`JdbcQueryExecutor.java:93`）；把已生成 SQL 再传进去会被 EQL 解析器误解析。正确做法是用编译产物执行入口：

```text
ICompiledSql compiled = ormTemplate.getSessionFactory().compileSql(
        name, eql, false, null, true, false, true); // 7 参：enableFilter=true
String sqlText = compiled.getSql().getText();
IDataSetMeta cols = compiled.getDataSetMeta();
IQueryExecutor qe = ormTemplate.getSessionFactory().getQueryExecutor(compiled.getQuerySpace());
ormTemplate.runInSession(session ->
    qe.executeQuerySql((IOrmSessionImplementor) session, compiled, inputParams,
        LongRangeBean.of(offset, limit), ds -> toRows(ds)));
```

注意两点：① `inputParams` 必须是**用户原始 EQL 参数值**，`executeQuerySql` 内部会再调 `compiled.buildParams(markerValues)`（`JdbcQueryExecutor.java:105`）；若先把 `buildParams` 结果传进去会二次构造并因租户参数追加而错位。② 三参 `compileSql` 内部固定 `enableFilter=false`（`SessionFactoryImpl.java:339-341`），要启用数据权限必须用 7 参并把 `enableFilter` 传 `true`、`astTransformer` 传 `null`（`LoadedOrmModel.java:96-97` 回落默认 transformer）。

证据：`IQueryExecutor.executeQuerySql(session, compiled, markerValues, range, callback)`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/IQueryExecutor.java:34-36`）；`SessionFactoryImpl.getQueryExecutor(querySpace)` 缺省回落 default（`SessionFactoryImpl.java:377-383`）；`OrmSessionImpl implements IOrmSessionImplementor`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:92`）；`IOrmTemplate.runInSession(Function<IOrmSession,T>):72`；`CompiledSql.buildParams` 按 `paramBuilders` 顺序生成（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/CompiledSql.java:146-150`）。

若不需要执行前预览，可退化一次调用 `ormTemplate.findAll(SQL.begin().name("console").append(eql).end())`，列信息只能从回调的 `IDataSet.getMeta()` 读（`nop-kernel/nop-dataset/src/main/java/io/nop/dataset/IDataSet.java:18`），且会重复编译。

### 3.3 参数绑定

EQL 参数标记是 `?`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:22-23`，`Symbol.g4:56`）。`SimpleParamBuilder` 按 `paramIndex` 从输入列表取值并 `StdDataType.convert`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/param/SimpleParamBuilder.java:21-26`）；租户等编译器自动参数由 `TenantParamBuilder` 追加，值取 `ContextProvider.currentTenantId()`，为空抛 `nop.err.core.no-tenant-id`（`.../param/TenantParamBuilder.java:18-25`）。SQL 侧 marker 值是另一套 API：`SQL.SqlBuilder.param/param0/params`（`nop-kernel/nop-core/src/main/java/io/nop/core/lang/sql/SQL.java:465-490`），`getMarkerValues():169`。

**缺口**：`SimpleParamBuilder` 不校验参数个数，少传抛裸 `IndexOutOfBoundsException`，无错误码（`SimpleParamBuilder.java:22`）。控制台必须自行校验「`?` 个数 vs 入参个数」。

### 3.4 limit / offset / timeout

EQL 文法原生支持 `LIMIT`/`FETCH FIRST`/`OFFSET … FETCH`，limit/offset 可为 `?`（`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4:296-320`）；执行层另有 `LongRangeBean`，由方言 `IPaginationHandler` 包装分页 SQL（`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:341-351,387-392`；`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/LongRangeBean.java:38-50`）。超时 `SQL.timeout(int)`（`SQL.java:332`）→ `JdbcHelper.setQueryTimeout` 仅在 `dialect.isSupportQueryTimeout()` 时 `ps.setQueryTimeout`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcHelper.java:234-240`），上限 `nop.dao.max-query-timeout` 默认 600000ms（`nop-persistence/nop-dao/src/main/java/io/nop/dao/DaoConfigs.java:34-35`）。

**最大行数没有全局强制**：`IDataSet.setMaxRows` 接口层空实现（`nop-kernel/nop-dataset/src/main/java/io/nop/dataset/IDataSet.java:38-40`），应使用 SQL `LIMIT`/`LongRangeBean` 并在 API 层截断。可参考 metadata 策略：负值拒绝、null/0 取默认 1000、超上限封顶 10000（`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaEntityBizModel.java:71-74,441-455`）。

### 3.5 结果集结构

`IDataSet extends IRecordInput<IDataRow>`，`getMeta()` 返回 `IDataSetMeta`，支持 `detach()`（`nop-kernel/nop-dataset/src/main/java/io/nop/dataset/IDataSet.java:17-40`）；`IDataRow.toMap()` 按 meta 列名输出 Map（`.../IDataRow.java:38-52`）。`IDataSetMeta` 提供 `getFieldCount/getFieldName/getFieldStdType/getFieldMetas`（`.../IDataSetMeta.java:29-53`）；`IDataFieldMeta` 提供 `fieldName/sourceFieldName/fieldOwnerEntityName/stdDataType/stdSqlType`（`.../IDataFieldMeta.java:16-27`）。ORM 执行后用 `TransformedDataSet` 重塑列（`JdbcQueryExecutor.java:120-123`），单列查询会包装成 `SingleColumnRow`（`:200-203`），控制台需统一成行列表。

### 3.6 其他执行设施

`IJdbcTemplate` 只能执行原生 SQL，不做 EQL 编译；`SqlLibManager` 能执行 `sql-lib.xml` 注册的 `<eql>` 项，带 auth/timeout/cache（`nop-persistence/nop-orm/src/main/java/io/nop/orm/sql_lib/SqlLibManager.java:129-151`；`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/sql-lib.xdef:90-97`），适合「保存后的命名查询」而非临时文本；`DaoQueryHelper` 面向 `QueryBean`/实体，不是文本入口；`MetaEntityQueryExecutor` 接受已拼好的原生 SQL，只返回 `Map` 行、无编译期列元数据（`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaEntityQueryExecutor.java:102-148`）。

## 4. 实体元数据与补全数据源

### 4.1 运行时 API

实体全集 `IOrmModel.getEntityNames()/getEntityModels()/getEntityModel(name)`（`nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/IOrmModel.java:37-41`）。运行时取 `IOrmModel`：`IOrmTemplate.getOrmModel()`（`IOrmTemplate.java:34`，实现优先当前 session，否则 sessionFactory，`OrmTemplateImpl.java:78-85`）或 `IOrmSessionFactory.getOrmModel()`（`IOrmSessionFactory.java:52`）。

实体 `IEntityModel`：`getName/getTableName/getClassName/getDisplayName/getQuerySpace/isReadonly/isUseTenant/isUseLogicalDelete/isUseShard/getPkColumns/getColumns/getAllProps/getRelations`（`nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/IEntityModel.java:27-260`）。属性 `IEntityPropModel`：`getName/getDisplayName/getStdDataType/getJavaTypeName/getKind/isToOneRelation/isToManyRelation/getColumns`（`.../IEntityPropModel.java:17-85`）。关联：`getRefEntityName()`、`getJoin()`、`getJoinLeftProps/getJoinRightProps`（`.../IEntityRelationModel.java:42-68`）。列：`getName/getCode/getSqlText/isMandatory/getStdSqlType/getPrecision/getScale/getStdDataType`（`.../IColumnModel.java:19-54`）。补全所需信息全部可从 `IOrmModel` 构建，无需访问数据库。

### 4.2 querySpace 维度

`IEntityModel.getQuerySpace()`（`IEntityModel.java:74`），ORM 模型默认 `querySpace="default"`（`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/entity.xdef:47`）；`EntityTableMeta.getQuerySpace()` 直接取实体模型（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/meta/EntityTableMeta.java:257-259`）。一条 EQL 只允许一个 querySpace，多个即抛 `nop.err.eql.not-allow-multiple-query-space`（`EqlTransformVisitor.java:938-945`）；无 FROM 子查询可用 `@querySpace('x')`，但与外层冲突同样报错（`:310-321`）。**对前端的约束**：数据源切换不是执行参数，而是「选哪些实体」；选中某 querySpace 后补全列表必须过滤，否则用户一写跨库 JOIN 就报错。

### 4.3 能否安全暴露

能，但必须投影与裁剪。不应直接序列化 `IEntityModel`：含 `className/tableName/dbSchema/dbCatalog`、列 `sqlText`、计算属性表达式、加密辅助列（`IEntityModel.java:29-35,74`；`IColumnModel.java:32-40`）。建议 DTO 白名单：`entityName/displayName/querySpace/readonly/props[{name,displayName,stdDataType,kind,relationTarget,mandatory}]`，不返回物理表名/类名/SQL 片段；暴露面受 action-auth 控制（§7.5）。

### 4.4 现成元数据 API 盘点

| API | 返回 | 可用于 EQL 补全 | 证据 |
|---|---|---|---|
| GraphQL introspection | BizModel 对象类型/字段 | 否：Biz 类型非 ORM 实体，且默认关闭 | `GraphQLConfigs.java:51-53`；`GraphQLEngine.java:363-367` |
| `NopMetaEntity__resolveEntityFields` | 元数据目录字段 | 部分：需先有 `metaEntityId`，仅覆盖已同步/导入实体 | `INopMetaEntityBiz.java:51-53`；`NopMetaEntityBizModel.java:276-290` |
| `NopMetaEntity__previewSqlFields` | SQL 文本输出列（类型首版 null） | 否：不返回实体/关联 | `NopMetaEntityBizModel.java:268-274` |
| `NopMetaOrmModel__*` | ORM 模型行 CRUD | 否：无元数据投影方法 | `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaOrmModelBizModel.java:12-16` |
| `nop-dyn` `allProps` | 动态实体属性字典项 | 否：仅动态实体 | `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/entity/NopDynEntityMetaBizModel.java:28-29` |

结论：没有「运行时 ORM 实体元数据」的现成 Web API；`nop-metadata` 只覆盖自己目录里的逻辑实体。

## 5. 多数据源与方言能力

### 5.1 数据源配置

数据源 bean 以 `nopDataSource_` 前缀被事务管理器收集为 querySpace（`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/beans/dao-defaults.beans.xml:14-23`）；`DefaultTransactionManager.addQuerySpace(querySpace, ds)` 注册连接工厂（`nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/DefaultTransactionManager.java:114-118`）。默认数据源键 `nop.datasource.driver-class-name/jdbc-url/username/password`（`dao-defaults.beans.xml:74-77`）。`JdbcDialectProvider` 支持 `querySpace→dialectName` 静态映射（`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcDialectProvider.java:36-53`），配置键 `nop.dao.config.query-space-to-dialect`（`DaoConfigs.java:66-67`）；未配置时按连接 `DatabaseMetaData` 自动识别（`DialectManager.java:90-100`）。读写分离是 DataSource 层装饰器，`nop.dao.readwrite` JSON 配置（`docs-for-ai/02-core-guides/datasource-and-readwrite-splitting.md:34-67`）。

### 5.2 按 querySpace 选方言与连接

方言 `IPersistEnv.getDialectForQuerySpace`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/IPersistEnv.java:40`）→ `JdbcTemplateImpl.getDialectForQuerySpace`（`JdbcTemplateImpl.java:137-143`）；连接 `IJdbcTemplate.currentConnection/openConnection(querySpace)`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/IJdbcTemplate.java:33-35`）；ORM 执行时 `OrmSessionImpl.executeQuery` 用 `sql.getQuerySpace()` 选执行器（`OrmSessionImpl.java:1451-1454`）。**前端切换参数**：只传 `querySpace` 名称，不传 jdbcUrl/账号；控制台 `listQuerySpaces` 可从 `IOrmModel` 的实体 querySpace 去重得到，再附 `IDialect.getName()`；`DialectManager.getDialectSelectors().keySet()` 可作为补充（`DialectManager.java:58-60`）。

### 5.3 方言能力位如何声明与读取

Schema `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef:84-102` 定义 `<features>` 全部能力位（`supportLateral/supportNullsFirst/supportFullJoin/supportRightJoin/supportValuesTableSource/supportRollup/supportCube/supportUpsert/supportReturningForInsert/Delete` 等）；H2 在 `h2.dialect.xml:33-39` 声明取值，`default.dialect.xml:15-26` 提供缺省。读取走 `IDialect.isSupportLateral()`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java:101`）等强类型 getter，底层 `DialectImpl` 读 `dialectModel.getFeatures()`（`DialectImpl.java:273-350`）。编译期门控示例：`if (node.getLateral() && !dialect.isSupportLateral())` 抛 `nop.err.eql.dialect-not-support-feature`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java:514-521`），NULLS 排序在 `:737`。完整模型 `IDialect.getDialectModel()`（`IDialect.java:36`），其 `getFeatures()/getFunctions()/getReservedKeywords()` 在 `_DialectModel.java:378,397,599`。

### 5.4 前端 dialect 配置化

建议新增 `EqlConsole__getDialectProfile(querySpace)`，服务端从 `IDialect.getDialectModel()` 投影：`name`（`IDialect.java:34`）、`features`、`reservedKeywords`、`functions`（`getFunctionNames():407`）、分页/转义（`getPaginationHandler():52`、`escapeSQLName`）、`defaultNullsFirst`（`isDefaultNullsFirst():250`）。这样前端不硬编码关键字/函数，也不解析 `dialect.xml`；投影时应只返回函数「名称+参数个数+返回类型」，避免泄露 SQL 模板。

## 6. 现有 Web API 与宿主模块候选

### 6.1 Web API 约定

所有入口都是 `IGraphQLEngine` 薄适配：`/graphql`、`/r/{bizObj}__{method}`、`/p/{...}`；operationName 规则 `{bizObj}__{method}`；`@BizQuery` 可 GET/POST，`@BizMutation` 走 POST；失败统一抛 `NopException` 转 `ApiResponse`（`docs-for-ai/02-core-guides/api-and-graphql.md:17-31,43-49,62-74,104-106,188-196`）。Java 方法无 `@Auth` 时自动派生 `obj:opType|obj:name`（`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/reflection/ReflectionBizModelBuilder.java:360-367`）。

### 6.2 `nop-metadata`

`INopMetaEntityBiz`（`nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/INopMetaEntityBiz.java:32-84`）：`previewSqlFields(sql)` @BizQuery（`:48-49`）、`createSqlView(sql,entityName,metaModuleId,querySpace?,displayName?)` @BizMutation（`:40-46`）、`resolveEntityFields(metaEntityId)` @BizQuery（`:51-53`）、`queryData(metaEntityId,filter?,limit?,offset?,selection?)` @BizQuery（`:55-61`）、`queryJoinData/queryAggregation`（`:63-83`）。

`queryData` 分派：物理实体走 ORM DAO；外部表/SQL 视图走 metadata 自己的 JDBC 连接（`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaEntityQueryAction.java:67-141`）。SQL 视图执行把 `sourceSql` 包成 `SELECT * FROM (<sourceSql>) _t` 再拼 filter/limit（`MetaEntityQueryExecutor.java:75-84`）。边界问题：

- `queryData` 返回 `List<Map<String,Object>>`，**没有列类型元数据**（`nop-metadata/nop-metadata-api/src/main/java/io/nop/metadata/api/dto/QueryEntityDataResultDTO.java:21-22`）。
- `previewSqlFields` 类型首版恒 null（`SqlViewField` 注释）；要类型需传 `querySpace` 走 `LIMIT 0 + ResultSetMetaData`（`SqlViewFieldTypeInferrer.java:156-160`）。
- 数据源是 `NopMetaDataSource` 的 `datasourceType + connectionConfig`（`nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/dao/entity/_gen/_NopMetaDataSource.java:41-45`），经 `MetaDataSourceConnectionProcessor.withConnection` 每次新建 `SimpleDataSource`（`.../connection/MetaDataSourceConnectionProcessor.java:199-205`）。它是与 ORM `querySpace` 并行的另一套机制（`NopMetaDataSource` 自己也有 `querySpace` 列，`_NopMetaDataSource.java:29`）。
- `requireEntity(metaEntityId,"query",context)` 会走 `checkDataAuth`（`nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:963-987`），但 EQL 文本从未进入该路径。

### 6.3 `nop-report`

`nop-report-core` 只依赖 `nop-ooxml-xlsx`（`nop-report/nop-report-core/pom.xml:19`），**不依赖 nop-orm/nop-orm-eql**。数据集在 XPT 模板里由 xlib 标签声明（`nop-report/nop-report-core/src/main/resources/_vfs/nop/report/xlib/xpt-rt.xlib`）：`<JdbcDataSet>` 原生 JDBC（`:5-32`）、`<UseJdbcDataSet>`（`:34-54`）、`<UseOrmDataSet>` 走 `ormTemplate.findAll`（`:56-76`）、`<UseQueryDataSet>`（`:78-97`）。`nop-report-service` 只有 CRUD BizModel，渲染入口是 `IReportEngine` 而非 BizModel（`docs-for-ai/03-modules/nop-report.md:19-23`）。报表数据集是「模板作者声明」而非「用户即席输入」，无行列元数据、无权限/限流护栏、无 Web API；扩展它会把渲染域和 SQL 控制台域耦合，且需给 `report-core` 新增 ORM 依赖。

### 6.4 `nop-dev-tools`

模块只有 `nop-api-debugger`、`nop-maven-shaded-plugin`、`nop-xlang-debugger`（`nop-dev-tools/pom.xml:20-24`）。`nop-api-debugger` 只依赖 `nop-api-core`，是调试协议接口/DTO（`nop-dev-tools/nop-api-debugger/pom.xml:14-19`）；`nop-xlang-debugger` 依赖 `nop-xlang/nop-api-debugger/nop-rpc-simple`（`nop-dev-tools/nop-xlang-debugger/pom.xml:14-32`），以 `ICoreInitializer` 启动 `SimpleRpcServer`（`.../initialize/XLangDebuggerInitializer.java:44-68`）。它是 IDE 远程调试通道，不是 Web/BizModel 宿主；扩展它会引入 `nop-biz/nop-web`，破坏「纯调试库」定位。`nop-metadata-web` 以 test scope 依赖它（`nop-metadata/nop-metadata-web/pom.xml:47-50`），也说明只是测试期设施。

### 6.5 宿主对比

| 维度 | 新建 `nop-eql-console` | 扩展 `nop-metadata` | 扩展 `nop-report` |
|---|---|---|---|
| 依赖方向 | 新模块→`nop-biz`/`nop-orm`/`nop-orm-eql`，向下正确 | metadata-dao 已依赖 `nop-orm`（`nop-metadata/nop-metadata-dao/pom.xml:27`），可行 | report-core 仅依赖 xlsx（`pom.xml:19`），需新增 ORM 依赖，破坏分层 |
| 领域边界 | EQL 控制台单一职责 | 「联邦元数据」+「任意 SQL 控制台」混杂 | 「报表渲染」+「SQL 控制台」混杂 |
| 可复用 | ORM 编译/执行/元数据/方言 | SQL 文本解析、外部数据源、页面与权限 | 仅 XPT 渲染 |
| 行列元数据 | `ICompiledSql.getDataSetMeta()` 直接提供 | 现有 `queryData` 不返回列 | 无 |
| 安全护栏 | 需新建（可参考 metadata 的 limit/select-only） | 已有 select-only/limit/连接白名单 | 无即席执行护栏 |
| 发布形态 | 新模块组，加根 pom `<modules>` | 追加到既有 8 子模块（`nop-metadata/pom.xml:20-28`） | 追加到 report 子模块 |
| 结论 | **推荐** | 备选（最快出原型） | 否决 |

## 7. 安全与权限

### 7.1 语句面

`Eql.g4` 只 import `Symbol, Comments, DMLStatement`，DDLStatement/TCLStatement/DCLStatement 被注释（`nop-persistence/nop-orm-eql/model/antlr/Eql.g4:20`）；`sqlDmlStatement` 只含 select/insert/upsert/update/delete，`createTable/alterTable/dropTable/commit/rollback/grant/revoke` 全部注释（`:32-52`）。事务语句虽在文法（`sqlTransactionStatement: sqlCommit | sqlRollback`，`:54-62`），但 `EqlCompiler` 编译期拒绝（`EqlCompiler.java:69-73`）。**结论**：EQL 通道不能执行 DDL；能写库的只有 INSERT/UPSERT/UPDATE/DELETE。只读控制台必须按 `getStatementKind()` 只放行 SELECT（`ICompiledSql.java:31`），执行层不会替你拒绝 DML。

### 7.2 只读控制

`SQL.readOnly` 只是读写分离的「路由意图」：SELECT 自动 `readOnly(true)`（`JdbcQueryExecutor.java:116-118`），DML 不标；`JdbcTemplateImpl` 只据此选主/从（`docs-for-ai/02-core-guides/datasource-and-readwrite-splitting.md:60-67`）。没有内置只读账号/只读事务开关。控制台必须：拒绝非 SELECT、拒绝 `SELECT ... FOR UPDATE`（`ICompiledSql.hasForUpdate():92`）、禁用 `@dump`（会打印 AST，`EqlCompiler.java:75-78`）。

### 7.3 数据权限（行级）

ORM 层数据权限在 SQL 生成期通过 `SyntaxMarker` 注入：`AstToSqlGenerator` 在 `enableFilter` 时给主表 WHERE 段（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java:336-342`）与 JOIN 非主表（`:840-852`）加 `FILTER` marker；执行期 `GenSqlTransformer` 调 `IEntityFilterProvider`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlTransformer.java:221`），默认实现从 `IDataAuthChecker.getFilter(bizObj,"sql",ctx)` 取规则（`.../filter/DataAuthEntityFilterProvider.java:29-38`）。

**关键**：`enableFilter` 来自 `context.isEnableFilter()` 或 EQL `@enableFilter`（`EqlCompiler.java:80`），而 `SQL` 默认 `enableFilter=false`（`nop-kernel/nop-core/src/main/java/io/nop/core/lang/sql/SQL.java:111-113`）；`JdbcQueryExecutor` 只把 `eql.isEnableFilter()` 传下去（`JdbcQueryExecutor.java:93-94`）。**结论**：直接 `executeQuery(SQL.begin().append(eql).end())` 默认**不会**应用用户行级数据权限，必须显式 `.enableFilter(true)`（`SQL.java:297-302`）或 `@enableFilter`；若走 `compileSql` 手工路径，三参重载固定 `enableFilter=false`（`SessionFactoryImpl.java:339-341`），必须用 7 参传 `true`。这是 P0 安全缺口。另注意：数据权限条件以 `SyntaxMarker` 形式存在，`compiled.getSql().getText()` 在 `GenSqlTransformer` 展开前**不包含**该条件文本（`AstToSqlGenerator.java:849` → `GenSqlTransformer.java:221`），因此编译预览 SQL 与实际执行 SQL 可能不同，前端应提示。

### 7.4 租户与逻辑删除

逻辑删除：实体 `useLogicalDelete` 且 `!context.isDisableLogicalDelete()` 时自动追加删除标志条件（`EqlTransformVisitor.java:457-460`）。租户：实体 `useTenant` 时自动追加 `tenantId = ?`，参数由 `TenantParamBuilder` 从上下文取（`:462-465,475-484`），无租户上下文直接抛错（`TenantParamBuilder.java:21-25`）。二者不受 `enableFilter` 控制，默认总是生效；控制台不应提供关闭逻辑删除的开关（`SQL.disableLogicalDelete`，`SQL.java:277-285`），否则会看到已删除数据。

### 7.5 操作权限与 action-auth

默认 `nop.auth.enable-action-auth=false`，权限链路整体不生效（`docs-for-ai/02-core-guides/auth-and-permissions.md:5-9`）。`@Auth(permissions="...")` 构建期转 `ActionAuthMeta`（`ReflectionBizModelBuilder.java:360-367`），执行期由 `GraphQLActionAuthChecker` 校验（`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLActionAuthChecker.java:91-151`）。页面/菜单/权限条目在 `action-auth.xml` 声明，如 `NopMetaEntity:query`/`mutation`（`nop-metadata/nop-metadata-web/src/main/resources/_vfs/nop/metadata/auth/_nop-metadata.action-auth.xml:144-150`，生成物）。自定义方法不写 `@Auth` 会派生 `obj:query|obj:method`（`ReflectionBizModelBuilder.java:364-366`），新控制台每个方法都应显式 `@Auth`。`CrudBizModel.requireEntity` 会做单条数据权限检查（`CrudBizModel.java:963-987`），但只对 `NopMetaEntity` 行生效，不覆盖任意 EQL 结果。

### 7.6 连接与注入

EQL 的实体/字段名由模型解析，不是字符串拼接；表名/列名最终由方言 `escapeSQLName` 输出（`DialectImpl.java:630`）；参数走 `?` 绑定（`SimpleParamBuilder.java:21-26`）。若未来支持原生 SQL 模式，`MetaEntityQueryExecutor` 的标识符白名单可参考（`MetaEntityQueryExecutor.java:58-64`），但只覆盖 metadata external 路径，不是通用 SQL 白名单。

## 8. 错误模型

`NopException` 保存 `SourceLocation loc`（`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/exceptions/NopException.java:69,271-283`）；`ErrorMessageManager.defaultBuildErrorMessage` 把 `errorCode/description/params/sourceLocation/status/bizFatal/details` 写入 `ErrorBean`（`nop-kernel/nop-core/src/main/java/io/nop/core/exceptions/ErrorMessageManager.java:223-240`），`sourceLocation` 是 `SourceLocation.toString()`（`:227`，格式 `[line:col:len:pos]path?...`，`SourceLocation.java:194-206`）。

**REST `/r/` 丢失行列**：`GraphQLEngine.buildRpcResponse` 对异常走 `buildResponseForException`（`GraphQLEngine.java:664-675`）→ `ErrorMessageManager._buildResponse` 调 `ApiResponse.setError`（`ErrorMessageManager.java:471-476`）；`ApiResponse.setError` 只复制 `status/code/msg/bizFatal/details`，**不复制 `sourceLocation` 与 `params`**（`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/ApiResponse.java:178-195`）。前端 lint 不能依赖 `/r/` 默认错误体。

**GraphQL 保留行列、丢失单条 errorCode**：异常转 `ErrorBean`（`GraphQLEngine.java:642`），`GraphQLResponseBean.addError` 把 `error.getSourceLocation()` 解析成 `GraphQLSourceLocation{line,column}`（`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/graphql/GraphQLResponseBean.java:127-157`）；但 `GraphQLErrorBean` 只有 `message` 与 `locations`（`.../GraphQLErrorBean.java:18-25`），错误码只在响应顶层 `errorCode`（`GraphQLResponseBean.java:65-70`），多错误时只保留第一个。

**行列精度**：解析错误用 `offendingToken.getLine()` 与 `getCharPositionInLine()`（`.../AbstractParseTreeParser.java:155-177`）；语义错误用 AST 节点在 build 时写入的 `ParseTreeHelper.loc(ctx)`（`.../EqlASTBuildVisitor.java:303` 等），`EqlCompiler` 多数 `.source(node)`。`ParseTreeHelper.loc` 的 path 来自 `token.getInputStream().getSourceName()`（`ParseTreeHelper.java:18-22`）；`EqlCompiler` 传 `null` 给 `parseFromText`（`EqlCompiler.java:57`），`loc==null` 时 path 为 `IntStream.UNKNOWN_SOURCE_NAME`（`AbstractParseTreeParser.java:56-62`），因此**行列有效、文件名无效**。新 API 可传 `SourceLocation.fromLine("eql",1,0)` 获得可读路径（`SourceLocation.java:113-121`）。ANTLR `charPositionInLine` 是 0-based，而 `GraphQLSourceLocation.column` 直接取 `getCol()` 未 +1（`GraphQLResponseBean.java:147-156`），前端 CodeMirror marker 需统一基准。另：`nop-metadata.previewSqlFields` 把解析异常包成 `NopMetadataException(ERR_SQL_VIEW_PARSE_FAILED, e)`（`SqlSelectFieldExtractor.java:69-73`），而 `NopException(ErrorCode, Throwable)` 不复制 loc（`NopException.java:90-94`），该 API 的解析错误没有行列。

建议控制台不依赖框架默认错误体裁剪，显式返回 `{ok:false,errors:[{errorCode,message,line,column,severity,params}]}`；服务端 catch `NopException` 后读 `getErrorCode()/getErrorLocation()/getParams()` 自行组装。

## 9. 测试基础设施

编译测试可直接构造最小 `ISqlCompileContext`，无需 Spring/DB（`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestEqlCompileSql.java:143-183`，`@BeforeAll/@Test` 见 `:54,236`）；端到端编译+执行用 `TestEqlCompiler extends AbstractOrmTestCase`（`nop-persistence/nop-orm/src/test/java/io/nop/orm/eql/TestEqlCompiler.java:31-38`）。`AbstractOrmTestCase` 手动装配 `OrmSessionFactoryBean`、`DefaultOrmColumnBinderEnhancer`、`LocalCacheProvider`（`nop-persistence/nop-orm/src/test/java/io/nop/orm/AbstractOrmTestCase.java:36-61`）。

容器+DB 测试用 `JunitBaseTestCase`（`nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/JunitBaseTestCase.java:21`），快照用 `JunitAutoTestCase`（`.../JunitAutoTestCase.java:30`）。组合方式：`@NopTestConfig(localDb=true, initDatabaseSchema=TRUE)` + `JunitBaseTestCase` + `@Inject IGraphQLEngine`（`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaDataContractBizModel.java:30-59`）。BizModel 测试必须经 `IGraphQLEngine`，不要直接调 `bizObj.method(...)`，否则缺 session/事务/权限（`docs-for-ai/02-core-guides/testing.md:64-88`）；快照模式见同文档 `:100-125`。

E2E 用 Playwright，每个 package 自动拉起 Quarkus dev 后端（`nop-entropy-e2e/README.md:8-16`）；RPC helper `rpc(request,operation,params,timeoutMs)` POST `/r/{operation}`（`nop-entropy-e2e/packages/e2e-shared/src/RpcClient.ts:80-105`）；启动命令见 `nop-entropy-e2e/packages/nop-code-e2e/playwright.config.ts:35-50`；可直接借鉴 `symbol-search.spec.ts` 的 `loginRpc + rpc` 纯 API 验证（`.../tests/symbol-search.spec.ts:1-50`）。**缺口**：现有 e2e 没有 `nop-metadata`；控制台挂新 app 时需新增 package 与 `webServer`。

建议测试矩阵：编译层覆盖未知实体/属性、多语句、commit、方言能力位、参数个数；执行层覆盖 SELECT 返回列（0 行仍有列）、limit 截断、tenant 缺失、逻辑删除过滤、data-auth 开关差异；API 层覆盖只读拒绝 DML、错误含 line/column、权限拒绝；E2E 用 `rpc` 验 `/r/EqlConsole__*`。

## 10. 缺口清单

**P0**：① 无任意 EQL 的 Web API（全仓无入口）；② 无「行+列」一体化响应（`QueryEntityDataResultDTO` 只有 items）；③ 无运行时 ORM 元数据 API；④ 任意 EQL 默认 `enableFilter=false`，行级数据权限被绕过；⑤ 执行层不拒绝 DML，`readOnly` 只是路由；⑥ REST 错误体丢弃 `sourceLocation`。

**P1**：⑦ 参数个数无校验（裸 `IndexOutOfBoundsException`）；⑧ 无 querySpace 枚举 API；⑨ 无方言能力 DTO；⑩ 无统一 limit/timeout 策略（`IDataSet.setMaxRows` 空实现）；⑪ 多语句错误无位置；⑫ metadata 预览丢失错误位置；⑬ EQL 错误 path 为 `unknown`；⑭ 列类型只有 `StdDataType/StdSqlType`，无 precision/scale/原生类型名。

**P2**：⑮ 无 EQL 格式化 API（`AstToEqlGenerator` 已能 pretty print，`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java:95-126`）；⑯ `executeQuery` 无 `ICancelToken`（仅 `executeStatement` 有，`IOrmSession.java:245-248`）；⑰ 无查询历史/收藏持久化；⑱ IN 列表上限默认 1000（`OrmEqlErrors.java:331`，`CoreConfigs.java:43`）；⑲ `@dump` 可被用户触发（`EqlCompiler.java:75-78`）；⑳ 原生 SQL 模式无通用白名单。

## 11. 后端落地方案建议

### 方案 A（推荐）：新建 `nop-eql-console` 模块组

```text
nop-eql-console/
├── nop-eql-console-api/      // DTO only, depends nop-api-core
├── nop-eql-console-service/  // BizModel, depends nop-biz + nop-orm + nop-orm-eql
├── nop-eql-console-web/      // pages + action-auth + beans, depends nop-web
└── nop-eql-console-app/      // optional standalone Quarkus host
```

不需要 `model/dao/meta/codegen`（控制台无持久化实体，查询历史后续再加）。依赖方向：新模块→`nop-service-framework`/`nop-persistence`，不反向依赖（§6.5）。发布：根 `pom.xml` `<modules>` 增加一项（现状 `pom.xml:515-554`）。前端接入：页面 `_vfs/nop/eql-console/pages/*.page.yaml`，菜单/权限 `action-auth.xml`；可先挂 `nop-metadata-app` 或 `nop-quarkus-demo` 验证，再独立 app。

建议 API（`@BizModel("EqlConsole")`）：`listQuerySpaces`/`listEntities(querySpace?)`/`getEntitySchema(entityName)`/`getDialectProfile(querySpace)`/`compile(eql,params?)`/`execute(eql,params?,limit=1000,offset=0,timeoutMs?)` 为 `@BizQuery`，权限 `EqlConsole:read`；`executeUpdate(eql,params?)` 为 `@BizMutation`，权限 `EqlConsole:write`；`format(eql)` 为 `@BizQuery`。`execute` 返回 `{columns[],rows[],rowCount,truncated,elapsedMs,sql}`，`compile` 返回 `{ok,sql,querySpace,statementKind,columns[],errors[]}`。

实现要点：`compile` 用 `getSessionFactory().compileSql(name,eql,false,null,true,false,true)`（7 参，`enableFilter=true`）取 SQL/`getDataSetMeta`/`statementKind`/`querySpace`，catch `NopException` 转结构化错误（§8）；`execute` 编译一次→`getQueryExecutor(querySpace).executeQuerySql(..., 原始参数值, ...)`（§3.2，参数由执行器内部 `buildParams` 构造），`limit` 由 `LongRangeBean`+内部截断双保险；`executeUpdate` 只允许四种写语句，`@BizMutation` 自动 REQUIRED 事务（`nop-service-framework/nop-biz/src/main/java/io/nop/biz/decorator/TransactionActionDecoratorCollector.java:41-51`）；安全默认值强制 SELECT、编译时 `enableFilter=true`（7 参 `compileSql`）、不暴露 `disableLogicalDelete`、timeout 默认 30s 且不超 `nop.dao.max-query-timeout`、limit 默认 1000/最大 10000（对齐 `NopMetaEntityBizModel.java:71-74`）；元数据 DTO 按 §4.3 白名单投影。

### 方案 B（备选）：在 `nop-metadata` 内新增 `NopEqlConsoleBizModel`

复用其页面/菜单/action-auth、`NopMetaDataSource` 外部数据源、`SqlSelectFieldExtractor` 的 SELECT-only 校验、`SqlViewFieldTypeInferrer` 的 `LIMIT 0` 类型推断。代价：模块定位被扩展为「任意 EQL 控制台」，且 metadata `querySpace` 与 ORM `querySpace` 两套机制并存（§6.2），前端易混淆。适用：2 周内出 demo、接受后续拆分。

### 方案 C：扩展 `nop-report`

否决：`nop-report-core` 不依赖 ORM（`pom.xml:19`），报表数据集的 EQL 只是 XPT 模板内部实现（`xpt-rt.xlib:56-76`），没有即席执行 API/权限/列元数据；强行扩展会破坏报表分层。

推荐 A：依赖方向正确；编译/执行/元数据/方言四件事都能直接用现有 ORM/EQL API，新模块只做「协议+护栏+DTO」；安全边界在一处收口；查询历史/保存/导出/执行计划都可在模块内演进，不污染 `nop-metadata`/`nop-report`。

## 12. 关键证据清单

| # | 事实 | path:line |
|---|---|---|
| 1 | EQL 编译入口 | `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java:56` |
| 2 | 解析不绑定 session | `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTParser.java:22-37` |
| 3 | 多语句/事务语句编译期拒绝 | `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java:58-73` |
| 4 | enableFilter 开关与编译期列元数据 | `.../EqlCompiler.java:80,137-170` |
| 5 | 编译上下文实现 | `nop-persistence/nop-orm/src/main/java/io/nop/orm/compile/EqlCompileContext.java:19-73` |
| 6 | 编译上下文由 LoadedOrmModel 构造（含计划缓存） | `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:92-133` |
| 7 | 会话工厂 compileSql / 最小编译上下文可自实现 | `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:339-355`；`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestEqlCompileSql.java:143-183` |
| 8 | 编译产物接口（SQL/列/statementKind/参数） | `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ICompiledSql.java:29-117` |
| 9 | 执行入口 `IOrmSession.executeQuery/Update/Statement` | `nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmSession.java:243-248` |
| 10 | JdbcQueryExecutor 编译+执行+列重塑 | `nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java:89-124` |
| 11 | 编译产物执行入口 / 按 querySpace 取执行器 | `.../loader/IQueryExecutor.java:34-36`；`.../factory/SessionFactoryImpl.java:377-383` |
| 12 | 参数构建顺序（含租户） | `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/CompiledSql.java:146-150` |
| 13 | 用户参数按索引取、无校验 / 租户参数 | `.../param/SimpleParamBuilder.java:21-26`；`.../param/TenantParamBuilder.java:18-25` |
| 14 | 租户/逻辑删除自动过滤 | `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java:457-484` |
| 15 | 单 querySpace 强制 | `.../EqlTransformVisitor.java:938-945` |
| 16 | 数据权限 marker + provider | `.../sql/AstToSqlGenerator.java:336-342,840-852`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/filter/DataAuthEntityFilterProvider.java:29-38` |
| 17 | SQL 默认 enableFilter=false | `nop-kernel/nop-core/src/main/java/io/nop/core/lang/sql/SQL.java:111-113` |
| 18 | SELECT 只标 readOnly，不拒 DML | `nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java:116-118` |
| 19 | 文法排除 DDL、保留事务语句 | `nop-persistence/nop-orm-eql/model/antlr/Eql.g4:20,32-62` |
| 20 | 方言能力位定义 | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef:84-102` |
| 21 | 能力位门控与方言模型投影 | `.../sql/AstToSqlGenerator.java:514-521`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java:36,407` |
| 22 | querySpace 默认 default | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/entity.xdef:47` |
| 23 | 数据源 bean 按 `nopDataSource_` 前缀收集 | `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/beans/dao-defaults.beans.xml:14-23` |
| 24 | metadata `queryData` 仅 items、无列类型 | `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaEntityBizModel.java:298-322`；`nop-metadata/nop-metadata-api/src/main/java/io/nop/metadata/api/dto/QueryEntityDataResultDTO.java:21-22` |
| 25 | metadata SQL 执行与独立数据源机制 | `.../service/query/MetaEntityQueryExecutor.java:75-84,102-148`；`.../service/connection/MetaDataSourceConnectionProcessor.java:199-205` |
| 26 | report core 不依赖 ORM | `nop-report/nop-report-core/pom.xml:19` |
| 27 | report 数据集执行（EQL 仅模板内部） | `nop-report/nop-report-core/src/main/resources/_vfs/nop/report/xlib/xpt-rt.xlib:56-76` |
| 28 | dev-tools 是调试库而非 Web 宿主 | `nop-dev-tools/pom.xml:20-24`；`.../XLangDebuggerInitializer.java:44-68` |
| 29 | GraphQL introspection 默认关闭 | `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/GraphQLConfigs.java:51-53` |
| 30 | 默认 action 权限派生 | `.../reflection/ReflectionBizModelBuilder.java:360-367` |
| 31 | 单条数据权限入口 | `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:963-987` |
| 32 | REST 错误体丢弃 sourceLocation | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/ApiResponse.java:178-195` |
| 33 | GraphQL 保留 line/column、单条错误无 errorCode | `.../beans/graphql/GraphQLResponseBean.java:127-157`；`.../graphql/GraphQLErrorBean.java:18-25` |
| 34 | ANTLR token 行列 / EQL 解析 path=unknown | `nop-kernel/nop-antlr4/nop-antlr4-common/src/main/java/io/nop/antlr4/common/AbstractParseTreeParser.java:56-62,155-177` |
| 35 | 动作默认事务 | `nop-service-framework/nop-biz/src/main/java/io/nop/biz/decorator/TransactionActionDecoratorCollector.java:41-51` |
| 36 | 测试基类 / e2e RPC helper | `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/JunitBaseTestCase.java:21`；`nop-entropy-e2e/packages/e2e-shared/src/RpcClient.ts:80-105` |

## 13. Open Questions

- [ ] v1 是否开放 DML？写权限、事务边界、影响行数、误操作防护（WHERE 必填？）需产品裁定。
- [ ] 数据源切换 UX：切实体补全列表（推荐），还是允许显式 `@querySpace`？冲突提示需产品化。
- [ ] 实体元数据暴露粒度：普通只读用户是否可见全部实体/字段？按 action-auth 还是白名单裁剪？未确认既有约定。
- [ ] 是否支持原生 SQL 模式？若支持，需独立连接白名单/危险语句拒绝/只读账号，不能复用 EQL 的 DDL 排除保证。
- [ ] 错误列号基准：ANTLR 0-based vs GraphQL 1-based（§8），需前后端统一并写入 API 契约。
- [ ] 查询历史/收藏持久化归属：新模块自建表，还是复用 `nop-sys`/`nop-metadata`？涉及新 ORM 模型与 codegen，需另立 plan。
- [ ] 大结果集导出：`IDataSet` 流式导出还是先落文件？需评估内存与超时。
- [ ] 取消/超时：`executeQuery` 无 `ICancelToken`，长查询只能靠 `SQL.timeout`+连接层；是否引入 `executeStatement` 的 cancel 能力？
- [ ] `nop-metadata` 的 `NopMetaDataSource.querySpace` 与 ORM querySpace 是否应统一命名/映射？当前是两套机制，统一会影响既有模型，需 owner doc 裁定。

## 附：最小调用链（伪代码，未编译验证）

```java
ICompiledSql compiled = ormTemplate.getSessionFactory().compileSql(
        "console", eql, false, null, true, false, true); // enableFilter=true
if (compiled.getStatementKind() != SqlStatementKind.SELECT) throw ...; // 只读模式
if (compiled.hasForUpdate()) throw ...;
IQueryExecutor executor = ormTemplate.getSessionFactory()
        .getQueryExecutor(compiled.getQuerySpace());
return ormTemplate.runInSession(session ->
        executor.executeQuerySql((IOrmSessionImplementor) session, compiled, inputParams,
                LongRangeBean.of(offset, limit), ds -> readRows(ds, limit)));
```

注意：数据权限开关必须在编译前决定；`compileSql` 手工路径与 `SQL+executeQuery` 路径的 `enableFilter` 来源不同（`EqlCompiler.java:80` vs `SQL.java:297-302`），实现时二选一并写测试钉死。
