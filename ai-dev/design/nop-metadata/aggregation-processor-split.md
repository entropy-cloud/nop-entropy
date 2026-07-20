# nop-metadata Aggregation Executor Processor Split Design

> Status: active
> Last Reviewed: 2026-07-20
> Source: `ai-dev/plans/04-nop-metadata-aggregation-processor-split.md`

## Decision

`MetaAggregationExecutor`（当前 3468 行）按 7 条执行路径拆分为独立 Processor：

1. `EntityAggregationProcessor` — entity 单表聚合（经平台 ORM `IOrmTemplate` 或 bypass EQL `TableReferenceExecutor`）
2. `ExternalAggregationProcessor` — external 单表聚合（限定表名原生 SQL，`withConnection`）
3. `SqlAggregationProcessor` — sql 视图聚合（sourceSql 子查询，`withConnection`）
4. `EntityEntityJoinAggregationProcessor` — entity↔entity 跨表聚合（同库原生 JOIN SQL，经 `orm().executeQuery`）
5. `ExternalExternalJoinAggregationProcessor` — external↔external 跨表聚合（同库原生 JOIN SQL，`withConnection`）
6. `MixedSameDbJoinAggregationProcessor` — 同库混合端点 JOIN 聚合（entity ↔ external/sql，同库原生 GROUP BY over JOIN）
7. `CrossDbInMemoryAggregationProcessor` — 跨库应用层聚合（复用 `MetaJoinExecutor.executeJoin` + 内存 GROUP BY）

`MetaAggregationExecutor` 保留为公共入口类，`executeAggregation` 改为路径分派器（≤ 500 行）：校验输入 → 构造 `AggregationContext` → 按 tableType/joinType 分派到对应 Processor → 返回结果。

## Processor Boundaries

每个 Processor 对应一个执行路径，从 `executeAggregation` 的 dispatch 分支到最终结果返回。

| Processor | 来源方法（MetaAggregationExecutor） | 核心逻辑 | 依赖的私有 helper |
|-----------|--------------------------------------|----------|-------------------|
| EntityAggregationProcessor | `executeEntityAggregation` → `executeEntityAggregationViaEql` / `executeEntityAggregationBypassEql` | entity 表校验 → propToCol → loadMeasures/Dimensions → needsBypass 判定 → 构造 SQL → executeQuery/JDBC | `resolveEntityColumns`, `rewriteFilterToColumns`, `loadEntityMeasures`, `loadEntityDimensions`, `collectRows` |
| ExternalAggregationProcessor | `executeExternalAggregation` → `buildExternalAggregationSql` + `collectBindParams` + `executeJdbcQuery` | 解析数据源 → loadExternalMeasures/Dimensions → withConnection → 构造 SQL → JDBC 查询 | `buildFromClause` (external 分支), `loadExternalMeasures`, `loadExternalDimensions`, `executeJdbcQuery` |
| SqlAggregationProcessor | (同上 `executeExternalAggregation`，tableType=sql 分支) | 与 external 相同，仅 `buildFromClause` 返回 `(<sourceSql>) _t` 不同 | `buildFromClause` (sql 分支), `loadExternalMeasures`, `loadExternalDimensions`, `executeJdbcQuery` |
| EntityEntityJoinAggregationProcessor | `executeEntityEntityJoinAggregation` | join 加载 → 端点解析 → entity 校验 → self-join 守卫 → 加载 JoinMeasure/JoinDimension → 构造 JOIN SQL → orm().executeQuery | `loadJoinMeasures`, `loadJoinDimensions`, `rewriteFilterToColumns`, `joinExecutor` |
| ExternalExternalJoinAggregationProcessor | `executeExternalExternalJoinAggregation` → `buildExternalExternalJoinSql` | 数据源解析 → 列集合解析 → side 解析 → 构造 JOIN SQL → withConnection → JDBC | `resolveTableColumnNames`, `loadExternalJoinMeasures`, `loadExternalJoinDimensions`, `buildExternalExternalJoinSql`, `executeJdbcQuery` |
| MixedSameDbJoinAggregationProcessor | `executeMixedSameDbJoinAggregation` → `buildMixedSameDbJoinSql` → `checkEntityTableVisible` | 端点位置识别 → 同库判定(连接可达性实测) → 构造 JOIN SQL → withConnection → JDBC | `resolveEntityColumns`, `resolveTableColumnNames`, `loadExternalJoinMeasures` (mixed), `loadExternalJoinDimensions` (mixed), `buildMixedSameDbJoinSql`, `checkEntityTableVisible`, `executeJdbcQuery` |
| CrossDbInMemoryAggregationProcessor | `executeCrossDbJoinAggregation` → `memoryGroupBy` + `resolveAndValidateLookupKeys` | Self-join guards → 跨库字段解析 → 复用 executeJoin → 合并行 key 校验 → 内存 GROUP BY → having(orderBy → truncate | `loadCrossDbMeasures`, `loadCrossDbDimensions`, `memoryGroupBy`, `truncateCrossDb`, `joinExecutor.executeJoin`, MemoryFilterEvaluator, MemoryOrderByComparator |

## Shared State: AggregationContext

### Service Dependencies（跨 Processor 共享）

| 字段 | 类型 | 来源 | 用途 |
|------|------|------|------|
| `ctx` | `MetaQueryContext` | BizModel.buildQueryContext() | daoProvider / orm / connectionService / tableRefExecutor / dataSourceResolver / fieldResolver / filterTranslator |
| `joinExecutor` | `MetaJoinExecutor` | BizModel 注入 | join 加载 + 端点解析 + 实体注册校验 + executeJoin (跨库) |
| `SUPPORTED_DIALECTS` | `Set<String>` | 常量 | H2/MySQL/PostgreSQL |

### Per-request State（由分派器在 executeAggregation 中构造）

| 字段 | 类型 | 说明 |
|------|------|------|
| `table` | `NopMetaTable` | 目标逻辑表 |
| `measureNames` | `List<String>` | 选定指标名 |
| `dimensionNames` | `List<String>` | 选定维度名 |
| `filter` | `TreeBean` | 用户过滤器（已默认合并） |
| `joinId` | `String` | 可选 JOIN 主键 |
| `limit` | `Long` | 分页上限 |
| `offset` | `Long` | 分页偏移 |
| `having` | `TreeBean` | 聚合后过滤 |
| `orderBy` | `List<OrderFieldBean>` | 聚合结果排序 |

### Helper Methods（从 MetaAggregationExecutor 抽取到 AggregationContext）

| 方法 | 原位置 | 说明 |
|------|--------|------|
| `safeAlias(name)` | private static | 安全化别名 |
| `buildResult(items)` | private static | `Map{"items": items}` |
| `aggSqlOf(aggFunc, column, measureName)` | private | aggFunc→SQL 函数包装 |
| `executeJdbcQuery(conn, sql, params, limit, offset, metaTableId)` | private | JDBC 查询 |
| `collectRows(ds)` | private | IDataSet→List<Map> |
| `requireName(value, what)` | private static | 非空守卫 |
| `resolveTableColumnNames(table, fieldDao, ctx)` | private | 解析表列名集合 |
| `resolveExternalFieldOrThrow(columns, field, table, side, joinId)` | private | 外部字段校验 |
| `resolveSharedDataSourceOrThrow(table, ctx, joinId)` | private | 数据源解析 |
| `resolveEntityColumns(entity, ctx)` | private | propToCol 映射 |
| `rewriteFilterToColumns(filter, propToCol)` | private | filter 属性名→列名 |
| `resolveEntityFieldColumn(entityFieldId, name, table, ctx, propToCol)` | private | entityFieldId→columnCode |
| `buildNameToExprTable(...)` | private static | name→aggSql/column 反查表 |
| `buildJoinNameToExprTable(...)` | private static | JOIN name→aggSql/qualifiedCol |
| `nameResolverFor(...)` | private static | having name→aggSql 回调工厂 |
| `buildOrderByClause(...)` | private static | ORDER BY 子句构造 |
| `loadMeasures(table, names, ctx)` | private | 加载 NopMetaTableMeasure |
| `loadDimensions(table, names, ctx)` | private | 加载 NopMetaTableDimension |
| `entityEndpointTypeOf(ep)` | private static | 端点类型字符串 |
| `newArrayHolder()` | private static | 数组持有者（闭包 bypass） |
| `containsIgnoreCase(Set, String)` | private static | 大小写不敏感包含 |
| `equalsStr(a, b)` | private static | null-safe 字符串相等 |
| `crossDbAliasOf(join)` | private static | 跨库 alias |
| `getCaseInsensitiveObj(map, key)` | private static | Map 大小写不敏感取值 |
| `findKeyIgnoreCase(map, key)` | private static | 大小写不敏感键查找 |
| `safeProductName(metaData)` | private static | DB 产品名称 |
| `messageOf(throwable)` | private static | 异常消息提取 |
| `externalTableFromForJoin(table, alias)` | private | JOIN FROM 子句 |
| `buildEntityFromClause(physicalTable, schema, alias)` | private | entity FROM 子句 |
| `isEntityTableVisible(metaData, schema, tableName)` | private static | 表可见性检测 |
| `checkTableExists(metaData, schema, tableName)` | private static | 表存在性检测 |

### Inner Types（从 MetaAggregationExecutor 移到 AggregationContext 或包级别）

| 类型 | 新位置 | 说明 |
|------|--------|------|
| `MeasureSpec` | `AggregationContext.MeasureSpec` | 单表指标规格 |
| `DimensionSpec` | `AggregationContext.DimensionSpec` | 单表维度规格 |
| `JoinMeasureSpec` | `AggregationContext.JoinMeasureSpec` | JOIN 指标规格 |
| `JoinDimensionSpec` | `AggregationContext.JoinDimensionSpec` | JOIN 维度规格 |
| `JoinField` | `AggregationContext.JoinField` | JOIN 字段解析结果 |
| `CrossDbMeasureSpec` | `AggregationContext.CrossDbMeasureSpec` | 跨库指标规格 |
| `CrossDbDimensionSpec` | `AggregationContext.CrossDbDimensionSpec` | 跨库维度规格 |
| `CrossDbFieldSpec` | `AggregationContext.CrossDbFieldSpec` | 跨库字段基类 |
| `CrossDbField` | `AggregationContext.CrossDbField` | 跨库字段解析结果 |
| `MemAggAccumulator` | `AggregationContext.MemAggAccumulator` | 内存聚合累加器 |
| `JoinFieldResolver` | `AggregationContext.JoinFieldResolver` | entity↔entity 字段解析 |
| `JoinExternalSideResolver` | `AggregationContext.JoinExternalSideResolver` | external↔external side 解析 |
| `JoinMixedSideResolver` | `AggregationContext.JoinMixedSideResolver` | 混合端点 side 解析 |
| `CrossDbFieldResolver` | `AggregationContext.CrossDbFieldResolver` | 跨库字段解析 |
| `JoinFieldResolverFn` | `AggregationContext.JoinFieldResolverFn` | JOIN field 解析函数式接口 |

### ErrorCode Constants（留在 MetaAggregationExecutor，外部已有引用）

所有 `ERR_AGGR_*` 和 `HAVING_EXPR_ATTR` 等公共常量保持原位（`MetaAggregationExecutor` 类），确保 `MemoryFilterEvaluator`、`MemoryOrderByComparator`、`ExpressionMeasureValidator` 及测试类的import 引用不受影响。

### Public Static Methods（留在 MetaAggregationExecutor）

- `preprocessHavingArithmetic(TreeBean, Map, NopMetaTable, List, List)` — 外部测试和 MemoryFilterEvaluator 引用
- `substituteAndValidateHavingExpr(String, Map, NopMetaTable, List, List)` — 外部测试引用
- `containsHavingArithmeticLeaf(TreeBean)` — 外部测试引用
- `HAVING_EXPR_ATTR` — 外部测试和 MemoryFilterEvaluator 引用

## Call Graph

```
BizModel.queryAggregation()
  │
  └─► MetaAggregationExecutor.executeAggregation(table, measures, dims, filter, joinId, limit, offset, having, orderBy, ctx)
        │
        ├─ 校验 measures/dimensions 非空
        ├─ 默认过滤器自动应用
        ├─ 构造 AggregationContext(ctx, joinExecutor, table, ...)
        │
        ├─ joinId != null ?
        │   ├─ YES → executeJoinAggregation(ctx)
        │   │   ├─ loadValidatedJoin + resolveEndpoint
        │   │   ├─ entity↔entity, same qs → EntityEntityJoinAggregationProcessor.execute(ctx)
        │   │   ├─ entity↔entity, cross qs → CrossDbInMemoryAggregationProcessor.execute(ctx)
        │   │   ├─ external↔external, same qs → ExternalExternalJoinAggregationProcessor.execute(ctx)
        │   │   ├─ external↔external, cross qs → CrossDbInMemoryAggregationProcessor.execute(ctx)
        │   │   └─ mixed → MixedSameDbJoinAggregationProcessor.execute(ctx)
        │   │       ├─ 同库（连接可达性实测通过）→ 原生 GROUP BY over JOIN
        │   │       └─ 不可同库 → CrossDbInMemoryAggregationProcessor.execute(ctx)
        │   │
        │   └─ NO → tableType dispatch:
        │       ├─ entity → EntityAggregationProcessor.execute(ctx)
        │       │   ├─ needsBypass? (granularity || expression)
        │       │   │   ├─ YES → BypassEQL path (JDBC)
        │       │   │   └─ NO → ViaEQL path (orm().executeQuery)
        │       ├─ external → ExternalAggregationProcessor.execute(ctx)
        │       └─ sql → SqlAggregationProcessor.execute(ctx)
        │
        └─ buildResult → Map{items}
```

**关键约束**：
- Processor 之间**无相互调用**。所有调用关系由 `MetaAggregationExecutor` 分派器单点控制。
- `MixedSameDbJoinAggregationProcessor` 和 `CrossDbInMemoryAggregationProcessor` 之间有一个运行时分支：同库判定失败（entity 表在选定 external 连接不可见）时从混合路径转到跨库路径。
- Processor 内的子分支（如 EntityAggregationProcessor 的 bypassEQL vs viaEQL）是 Processor 内部实现细节，分派器不关心。

## Input/Output Contract（每个 Processor）

### EntityAggregationProcessor
- **Input**: `AggregationContext`（所有 per-request 字段）
- **Output**: `List<Map<String, Object>>` — items rows (dimension alias → value, measure alias → aggregated value)
- **Error Codes**: ERR_AGGR_ENTITY_NOT_REGISTERED, ERR_AGGR_UNSUPPORTED_DIALECT, ERR_AGGR_AGG_FUNC_UNSUPPORTED, ERR_AGGR_EXPRESSION_DIALECT_UNSUPPORTED, ERR_AGGR_FIELD_NOT_RESOLVED

### ExternalAggregationProcessor
- **Input**: `AggregationContext`
- **Output**: `List<Map<String, Object>>` — items rows
- **Error Codes**: ERR_AGGR_UNSUPPORTED_DIALECT, ERR_AGGR_AGG_FUNC_UNSUPPORTED, ERR_AGGR_FIELD_NOT_RESOLVED, ERR_AGGR_EXEC_FAILED, ERR_AGGR_EXPRESSION_DIALECT_UNSUPPORTED

### SqlAggregationProcessor
- **Input**: `AggregationContext`
- **Output**: `List<Map<String, Object>>` — items rows
- **Error Codes**: Same as ExternalAggregationProcessor + "sourceSql is empty"

### EntityEntityJoinAggregationProcessor
- **Input**: `AggregationContext` + resolved `Endpoint` left/right + `NopMetaTableJoin`
- **Output**: `List<Map<String, Object>>` — items rows
- **Error Codes**: ERR_AGGR_JOIN_SELF_JOIN, ERR_AGGR_JOIN_COMPILE_FAILED, ERR_AGGR_JOIN_FIELD_SIDE_UNRESOLVED, ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH

### ExternalExternalJoinAggregationProcessor
- **Input**: `AggregationContext` + resolved `Endpoint` left/right + `NopMetaTableJoin`
- **Output**: `List<Map<String, Object>>` — items rows
- **Error Codes**: ERR_AGGR_JOIN_SELF_JOIN, ERR_AGGR_JOIN_SIDE_REQUIRED, ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE, ERR_AGGR_UNSUPPORTED_DIALECT, ERR_AGGR_EXPRESSION_DIALECT_UNSUPPORTED

### MixedSameDbJoinAggregationProcessor
- **Input**: `AggregationContext` + resolved `Endpoint` left/right + `NopMetaTableJoin`
- **Output**: `List<Map<String, Object>>` — items rows (may delegate to CrossDbInMemoryAggregationProcessor if cross-DB)
- **Error Codes**: ERR_AGGR_JOIN_MIXED_ENTITY_TABLE_EMPTY, ERR_AGGR_JOIN_MIXED_CROSS_DB_DEFERRED, ERR_AGGR_JOIN_SIDE_REQUIRED, ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE, ERR_AGGR_UNSUPPORTED_DIALECT

### CrossDbInMemoryAggregationProcessor
- **Input**: `AggregationContext` + resolved `Endpoint` left/right + `NopMetaTableJoin`
- **Output**: `List<Map<String, Object>>` — items rows (memory-grouped)
- **Error Codes**: ERR_AGGR_JOIN_SELF_JOIN, ERR_AGGR_CROSS_DB_FIELD_KEY_MISSING, ERR_AGGR_EXPRESSION_MEMORY_NOT_COMPUTABLE, ERR_AGGR_HAVING_EXPR_MEMORY_NOT_COMPUTABLE

## Rationale

- 单类 3468 行难以维护、单测覆盖率难提升、AI 阅读 token 消耗大
- 7 条路径相互独立（无共享内部状态），天然可拆
- 拆分后入口仅做分派（≤ 500 行），每个 Processor 聚焦单一执行路径（≤ 800 行）
- ErrorCode 常量留在 `MetaAggregationExecutor` 可最小化外部引用改动（`MemoryFilterEvaluator`、`MemoryOrderByComparator`、`ExpressionMeasureValidator` 及测试类无需修改 import）
- 公共静态方法留在 `MetaAggregationExecutor` 确保测试兼容（`TestHavingArithmeticPreprocess` 等不受影响）

## Rejected Alternatives

- **保留单类 + 内部 method 分组**：仍无法降低单类 token 消耗，单测覆盖率仍受限
- **按 tableType 拆（3 类）**：JOIN 路径下端点组合复杂，3 类内部仍需大量分派，效果有限
- **按 measure/dimension/having/orderBy 拆**：与执行路径正交，不解决核心 7 路径分派问题
- **ErrorCode 常量全部迁移到 AggregationContext**：破坏 `MemoryFilterEvaluator`、`MemoryOrderByComparator`、`ExpressionMeasureValidator` 及所有测试类的 `MetaAggregationExecutor.ERR_AGGR_*` 引用，不必要的大规模 import 变更
