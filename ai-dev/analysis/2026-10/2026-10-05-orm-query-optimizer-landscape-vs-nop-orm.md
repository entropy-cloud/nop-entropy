# ORM 查询优化器全景扫描 vs nop-orm 能力裁定

> Status: resolved
> Date: 2026-10-05
> Scope: `nop-persistence/{nop-orm, nop-orm-model, nop-orm-eql, nop-dao}`（worktree `nop-entropy-eql-cross-db-compat`，分支 `feat/eql-cross-db-compat`，HEAD `0394d0de24`）；对照 40 个参考 ORM/查询引擎（`~/sources/orm/`）
> Conclusion: **nop-orm 不是"最强大最完善"的 ORM。** 它在"跨库可移植性的失败模式设计"这一个轴上明确领先所有被调研项目（编译期响亮失败 vs 运行期 SQL 错误），在"方言能力建模 + Delta overlay + propId 稳定性 + 元模型驱动 codegen"上属于第一梯队；但在"查询优化能力、编译期类型安全、缓存成熟度、生态与一致性"四个轴上**明显落后于 linq2db / EF Core / Diesel / SQLAlchemy**，且存在若干被自己文档承认的结构性短板（无二级缓存可用实现、无变更集 API、CDC 空壳、无表继承、无 join-fetch 合并）。

---

## Context

- 需要回答三个问题：①jOOQ / Hibernate 等是否有执行优化器？②与 nop-orm 对比如何？③nop-orm 是否最强大最完善？
- 特别说明：初查 `db-compact` 分支在本仓不存在（已核对 master 全部 refs、`.bare` 的 `packed-refs`/`reflog`、`~/app` 下全部 worktree）。经确认指的是 worktree `nop-entropy-eql-cross-db-compat` / 分支 `feat/eql-cross-db-compat`，该分支对 `nop-persistence/` 有 146 文件、+23674/-4261 的改动，是本次分析对象。
- 参考 ORM 不限 Java：40 个仓库已全部浅克隆到 `~/sources/orm/`（`java/ jvm/ go/ rust/ python/ ts/ cs/ ruby/ elixir/ php/ optimizer/`）。3 个首轮 URL 失效（`vert-x3/vertx-sql-client`、`rails/activerecord` 直连、`dromara/dynamic-datasource`）已用 `eclipse-vertx/vertx-sql-client`、`rails/rails`、`baomidou/dynamic-datasource` 补齐。
- 证据分级：**实测代码**（读到 `path:line`）> **配置事实**（xdef/xml）> **grep 反向验证**（ABSENT 类结论的验证方式）> **文档转述**（本报告尽量不用）。

---

## Analysis

### Part 1 — 直接回答：jOOQ / Hibernate 有执行优化器吗？

#### 1.1 短答案

**没有。** 两者都是"忠实翻译器"，不是优化器。真正有优化器的是 **Apache Calcite** 和 **Dolt/go-mysql-server**——而这两个不是 ORM，是查询引擎/数据库。

| 项目 | 优化器 | 代价模型 | 统计/基数 | Join 重排 | 计划缓存 |
|---|---|---|---|---|---|
| **jOOQ（开源）** | ❌ 零重写 | ❌ | ❌ | ❌ | ❌（仅反射缓存） |
| jOOQ（商业） | ✅ 57 条 pattern 重写（纯规则） | ❌ | ❌ | ❌ | ❌ |
| **Hibernate ORM 8** | ❌ 零优化 | ❌ | ❌ | ❌ | ✅ 两级（设计优秀） |
| **EF Core** | ✅ 多趟规则式重写 | ❌ | ❌ | ❌ | ✅ |
| **linq2db** | ✅ 最激进的规则式重写 | ❌ | ❌ | ❌ | ✅ 最精巧 |
| **Django** | ⚠️ 5 类局部形状优化 | ❌ | ❌ | ❌ | ❌ |
| **Calcite** | ✅ 规则 + Volcano/Cascades | ✅ | ✅ | ✅ DPHyp 超图 DP | ❌（仅 memo） |
| **Dolt** | ✅（在 go-mysql-server） | ✅ | ✅ histogram | ✅ | ✅ |
| **nop-orm** | ❌ 语义等价改写，非优化 | ❌ | ❌ | ❌ | ✅ 编译计划缓存 |

#### 1.2 jOOQ：把"没有优化器"写进了代码

jOOQ 官方文档存在 61 个 `transformPatterns*` 开关（`~/sources/orm/java/jooq/conf/Settings.java:178` 起），看起来像规则引擎。但代码里有两道商业版硬门：

```java
// ~/sources/orm/java/jooq/impl/AbstractContext.java:295
&& TRUE.equals(settings().isTransformPatterns())
&& configuration().requireCommercial(() -> "SQL transformations are a commercial only feature. ...")

// ~/sources/orm/java/jooq/impl/DefaultExecuteContext.java:999
if (TRUE.equals(settings().isTransformPatterns()) && configuration().requireCommercial(...)) {
```

准确表述：

- **开源版：查询按你写的结构原样渲染。** 这是 jOOQ 的核心立场——"SQL 是一门语言，不是抽象层"。连 `QueryPart.equals` 的默认语义都是"渲染后 SQL 相等"（`java/jooq/QueryPart.java:76-82`），而不是结构相等。
- **商业版：57 条 pattern-based 重写**（Unnecessary DISTINCT / INNER JOIN / GROUP BY / ORDER BY、Merge IN predicates、OR to IN…），迭代到不动点。**仍然从不读统计信息、从不做代价决策。** 官方文档自陈其目的主要是 SQL linting 与 ParsingConnection 的 SQL 清理，并承认"这不会客观上改善大多数 RDBMS 上的执行"。
- **任何版本都没有**：join reorder、selectivity、成本模型、索引选择。
- 缓存只有反射缓存（`java/jooq/impl/CacheType.java:77-126`），官方文档直接建议用户自己缓存 SQL 与 `PreparedStatement`，并提醒 `Configuration` 非线程安全。

#### 1.3 Hibernate ORM 8：SQM → SQL AST 的 1:1 翻译

Hibernate 的两段式管线（`StandardHqlTranslator` → SQM → `StandardSqmTranslator` → SQL AST → `StandardSqlAstTranslator` → JDBC）是**忠实翻译**：

- 唯一实质的"改写"是 `pruneTableGroupJoins()`（`java/hibernate-orm/hibernate-core/src/main/java/org/hibernate/query/sqm/sql/BaseSqmToSqlAstConverter.java:838`）——剪掉没用到的 join。
- "隐式 join → 显式 join"（`consumeAttributeJoin`，同上 `:3564`）存在，但它是**语义驱动**的（`where` 里出现 `o.address.city` 才 join city），不是代价驱动。
- **全模块 grep `Cost` 只命中 4 个文件**，全部与查询优化无关（`ActionQueue` 的 `GraphEdge.breakCost`、`StandardLockingClauseStrategy`）。
- **最容易被误解的一点**：`StatisticsImplementor` / `EntityStatisticsImpl` 是**运行时遥测计数器，不喂任何成本模型**。`java/hibernate-orm/hibernate-core/src/main/java/org/hibernate/stat/internal/StatisticsImpl.java:90-91` 全是 `LongAdder`（`queryPlanCacheHitCount` / `queryPlanCacheMissCount`）。**Hibernate 不收集任何表/索引基数或直方图。**

Hibernate 真正值得学的是**计划缓存**：`QueryInterpretationCacheStandardImpl` 两级缓存（`queryPlanCache` + `hqlInterpretationCache`），键为 `(queryString, unnamedParameterIndices, statement.cacheHashCode(), resultType, lockOptions, fetchProfiles, temporalIdentifier)`（`SqmInterpretationsKey.java:25-40`）；`ConcreteSqmSelectQueryPlan` 做双重缓存并按参数绑定/queryOptions 分级失效（`:449-497`）。8.x 还把方言能力从上百个扁平 `boolean supportsXyz()` 升级为带 `Context` 维度的不可变能力对象：`SubquerySupport`（9 项 Feature）、`ValuesListSupport`、`RowValueSupport`、`LockingSupport`——**这是本次调研中最值得 nop 借鉴的单点设计**（见 §5.1）。

#### 1.4 一个必须承认的横向事实：27 个应用层 ORM 全部零统计

Django、SQLAlchemy、peewee、Tortoise、Prisma、Drizzle、TypeORM、Knex、Sequelize、Objection、ActiveRecord、Sequel、SeaORM、sqlx、GORM、bun、Ecto、Doctrine、Laravel、Phalcon、Diesel——**没有一个有统计信息、基数估计、代价模型、join 重排、索引选择**。唯一的例外方向是"索引 hint"：Django `force_index`、ActiveRecord `visit_Arel_Nodes_OptimizerHints` 发 `/*+ ... */`、MikroORM `force_index`/`use_index`。

**这不是缺陷，而是正确的默认立场**：ORM 层的统计信息本质上无法保持新鲜（除非接一条持续采集管道，那是 Calcite/Dolt 的复杂度），而**错误的行数估计比没有估计更糟**——它会诱导出比数据库原生规划器更差的计划。把 join order 与 access path 交给拥有真实统计的数据库，是正确的分工。Calcite 和 Dolt 之所以要做，是因为它们**就是**那个拥有统计的角色。

因此对 nop-orm 的评价**不能**用"没有优化器"作为主要减分项——它与 jOOQ / Hibernate / Diesel / SQLAlchemy 在这一项上立场相同且同样正确。

---

### Part 2 — nop-orm 的优化器事实核定

#### 2.1 `EqlASTOptimizer` 是命名误导，不是优化器

`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ast/EqlASTOptimizer.java`（1998 行，生成产物，带 `//__XGEN_FORCE_OVERRIDE__`）：

- **68 个 `optimizeSqlXxx` 方法**（实测 `grep -c 'public .*optimizeSql'` = 68）
- **118 处 `incChangeCount()`**
- **1 处 `new`/`instanceof`/`throw`**（全部在 `default:` 分支抛 `IllegalArgumentException("invalid ast kind")`）
- 每个方法体是同一个模板：默认原样返回 → 递归子节点 → 仅当子节点被替换才 `incChangeCount()` + 可选 `deepClone()` + 经 setter 重挂载

它是 `nop-kernel/nop-core/.../ast/optimize/AbstractOptimizer.java` 提供的**子树递归 + copy-on-write 重挂载机制**，不含任何优化规则。

**全仓唯一消费者**：`compile/OracleEqlAstTransformer.java:139` 的内部类 `Expander`，它**只覆写 2 个方法**：

```java
// OracleEqlAstTransformer.java:142
public EqlASTNode optimizeSqlNotExpr(SqlNotExpr node, Void context) {
    if (...getOperator() == SqlOperator.SAFE_EQ) return expandDistinctFrom(...);
    return super.optimizeSqlNotExpr(node, context);
}
// OracleEqlAstTransformer.java:153
public EqlASTNode optimizeSqlBinaryExpr(SqlBinaryExpr node, Void context) {
    if (node.getOperator() == SqlOperator.SAFE_EQ) return expandIsNotDistinctFrom(node, context);
    return super.optimizeSqlBinaryExpr(node, context);
}
```

且 `setInplaceUpdate(true)`（`:90`）——类注释 `:87-88` 记录了踩坑：默认 clone 模式下**丢弃返回值 = 静默丢弃改写**。

`EqlASTProcessor.java`（499 行）同样生成、68 个方法全部 `return defaultProcess(...)` 且 `defaultProcess` 返回 null（`:496-498`），**全仓零引用 = 死代码**。

#### 2.2 ABSENT 清单（grep 反向验证，零命中）

在 `nop-persistence/` 主代码中穷举搜索后零命中：

| 能力 | 状态 | 验证方式 |
|---|---|---|
| 代价模型 / 基于代价的决策 | **ABSENT** | `cost` / `costBased` = 0 |
| 统计信息 / 基数估计 | **ABSENT** | `cardinality`、`selectivity`、`estimatedRows`、`n_distinct` = 0 |
| Join 顺序重排 | **ABSENT** | `EqlTransformVisitor`/`AstToSqlGenerator`/`CollectionOperatorTransformer` 中 `sort`/`reverse`/`shuffle` 全 0；唯一 `reorder` 命中是 `DaoQueryHelper.requireOrderColumn`（校验排序列存在） |
| 索引选择 / index hint | **ABSENT** | `forceIndex`/`useIndex`/`indexHint` = 0；`getLockHintSql` 是 JDBC 锁提示（`with(tlock)`） |
| 子查询扁平化 / 去相关 | **ABSENT** | `flatten`/`decorrelat`/`semiJoin` = 0 |
| 常量折叠 | **ABSENT** | `fold`/`constantEval` = 0 |
| 谓词下推 | **ABSENT（做的是谓词注入）** | 见 §2.4 |
| 投影裁剪 | **ABSENT（反而全量展开）** | `select *` 被展开为实体**全部**列（`EqlTransformVisitor.java:324-333` → `getAllProjections:1414` → `buildSelectItems:1467-1486`）；`usedColumns`/`columnUsage` = 0 |
| `IN` 列表拆分 | **ABSENT（仅门禁）** | `EqlTransformVisitor.java:733` 注释明写"不做任何改写"；`:743-754` 按 Oracle ORA-01795 阈值（默认 1000）硬报错 |
| DISTINCT / GROUP BY 消除 | **ABSENT** | `removeDistinct`/`eliminateGroupBy` = 0 |
| 空 `IN ()` 折叠 | **ABSENT** | 无 `1 = 0` 折叠路径 |
| AND/OR 树平衡 | **ABSENT** | 无 `CompoundBooleanOp` 归一（对比 linq2db `BinaryExpressionAggregatorVisitor`） |

#### 2.3 PRESENT 的变换全集（全部是语义等价改写，不是性能优化）

| 变换 | 锚点 | 性质 |
|---|---|---|
| 构建期 SQL-92 → 方言换形（19 处） | `parse/EqlASTBuildVisitor.java:283/301/327/346/372/379/385/391/494/501/509/558/582/610/648/654/896/975/998` | `POSITION(x IN y)`→`instr(y,x)`；`SUBSTRING(x FROM a FOR b)`→`substr`；`EXTRACT(f FROM d)`→`f(d)`；`TRIM(BOTH/LEADING/TRAILING)`→`trim/ltrim/rtrim`；`IS [NOT] DISTINCT FROM`→`SAFE_EQ`；`IS [NOT] TRUE/FALSE`→`(a IS NOT NULL) AND (a=TRUE)`；`interval '1 day'`→`SqlIntervalExpr`；`AT TIME ZONE`→`atTimeZone()`；`ROLLUP/CUBE`→`SqlGroupingOp`；`FROM (VALUES …)`→`SqlValuesTableSource` |
| 隐式 JOIN 合成（导航路径 → 显式 join，带引用计数去重） | `EqlTransformVisitor.java:968/983/1055/1103/1171/1204`；发射 `AstToSqlGenerator.java:572` | 语义必需 |
| 集合操作符 `_some`/`_all` → `EXISTS` | `compile/CollectionOperatorTransformer.java`（711 行）；`_all` 的 De Morgan + `countAllAncestors(scope)%2==1` 奇偶判定（`:436/444/526-531`） | 语义必需 |
| 谓词**注入**（不是下推） | `EqlTransformVisitor.java:435`（逻辑删除 + 租户 + 实体固定 filter）；`:766`（JOIN→ON，CROSS JOIN 例外见 `:778-779` 多租户泄漏分析注释）；`:476`（UPDATE/DELETE→WHERE，只补租户不补逻辑删除，理由 `:468-469`） | **这是 nop 最独特的能力** |
| 分页重写 LIMIT ↔ OFFSET/FETCH | `AstToSqlGenerator.java:111-121` → `nop-dao/.../pagination/{LimitOffsetPaginationHandler.java:34-44, OffsetFetchPaginationHandler.java:38-51}` | 语义必需 |
| NULLS 空序改写（CASE WHEN 兄弟键） | `sql/NullsOrderRewriter.java:69-79`；挂在紧邻排序项的前置同级键（`AstToSqlGenerator.java:671-672`，故覆写 `visitSqlOrderBy` 而非 item，理由 `:598-606`）；三道 fail-fast：`:641` 窗口上下文 / `:649` 非纯列引用 / `:661` DISTINCT 查询 | **EQL 与 bean 两通道共用**（`nop-orm/.../GenSqlHelper.java:588-603`） |
| SAFE_EQ 三节点/两节点展开（3VL 正确性） | `compile/OracleEqlAstTransformer.java:166-180`（IS NOT DISTINCT FROM 两支）/`:188-203`（IS DISTINCT FROM 三支）；禁止 naive NOT 包装（`:55` 注释：单/双 NULL 行 `NOT(NULL)=UNKNOWN` → WHERE 丢行） | 语义必需 |
| CTE 降级（`WITH AS` → 内联子查询） | `EqlTransformVisitor.java:812-817/912-917/1358-1362` | 唯一"静默降级而非报错"的分支 |
| 方言化 SQL 发射（27 处 override） | `AstToSqlGenerator.java` 各点；函数模板替换 `nop-dao/.../impl/DialectImpl.java:174-193` | 移植性 |

#### 2.4 方言层：这是 nop-orm 真正的强项

- **声明方式**：xdef schema + XDsl 实例化，**无手写 Java 方言类**。`nop-kernel/nop-xdefs/.../schema/orm/dialect.xdef:72-85` 声明 **31 个 `<features>` 能力位**；19 个 `*.dialect.xml`（2778 行）；`DialectImpl` 是**唯一实现类**（`nop-dao/.../dialect/impl/DialectImpl.java:75`）；加载器 `DialectModelLoader.java:39-46`；按 `DatabaseMetaData` 自动选方言 `DialectManager.java:94`。
- **规模**：15 个可加载方言 + 3 个抽象基座（`default` / `geo-support` / `window-expr-support`），矩阵快照 `dialect-capability-matrix.json` 记 `dialectCount: 18`。覆盖 db2、dm（达梦）、duckdb、h2(+h2gis)、mariadb、mssql、mysql(+mysql5.7)、oracle(+oracle-reverse)、postgresql(+postgis)、tdengine，`es` 标 `pseudo=true`。
- **继承链（mixin 模式）**：`postgresql.dialect.xml:3` 是 `x:extends="default.dialect.xml,geo-support.dialect.xml,window-expr-support.dialect.xml"`——**多父继承 + 能力 mixin**。`duckdb`/`postgis` 继承 postgresql；`h2gis` 继承 h2+geo；`mariadb`/`mysql5.7` 继承 mysql。
- **显式关断继承的能力位**：`mysql5.7.dialect.xml:11` 用 `<features supportSequence="false" supportReturningForUpdate="false" supportLateral="false"/>` 覆盖从 mysql 继承的 `supportLateral="true"`（LATERAL 需 MySQL 8.0.14+）。**整个 mysql5.7 差异只有 1 个元素。**
- **保守缺省铁律**：`DialectImpl` 每个 accessor 都是 `Boolean.TRUE.equals(model.getFeatures().getXxx())` → 未配置 = null → 一律 false。
- **失败模式统一为编译期硬错误**：`nop.err.eql.dialect-not-support-feature`（`OrmEqlErrors.java:234`，参数 `dialect`/`feature`）。零静默回退——除 `supportWithAsClause`（降级为内联子查询）与 `supportSomeSubQuery`（静默选 `ANY`，两者均合法）。
- **方言 xdef 的注记密度极高**：`dialect.xdef:60-66` 对 `supportILike` 给出反例分析——"postgresql 继承 false 但已注册 ilike 函数且工作正常，接标志即引入假阴性"，明确要求**勿接为第二道门**。这是极高水平的知识沉淀。

#### 2.5 双通道架构（易被误读为缺陷，实为设计）

`AstToSqlGenerator extends AstToEqlGenerator`（`AstToSqlGenerator.java:88`）：

| 通道 | 入口 | 方言感知 | 门校验 |
|---|---|---|---|
| 回显（调试/dump） | `EqlASTNode.toSqlString()`（`EqlASTNode.java:31`）→ `AstToEqlGenerator` | 否（`for update` 硬编码 `:524`、`limit` 直出 `:512`） | 否 |
| 执行 | `EqlCompiler.java:89` → `AstToSqlGenerator` | 是（27 处 override） | 是 |

有专门测试守护该分层：`TestEqlToSqlStringBypass.java`（类注释 `:35-36` 明确"与编译通道输出形态不同"）。

#### 2.6 编译管线（有序，含锚点）

```
env.compileSql (JdbcQueryExecutor.java:56/75)
  → 计划缓存 LoadedOrmModel.java:102-124  key=QueryPlanCacheKey(name,sqlText,disableLogicalDelete,allowUnderscoreName,enableFilter)
      ⚠️ 非默认 IEqlAstTransformer 强制 bypass 缓存（:95-100，含裁定注释）
  → EqlCompiler.compile (EqlCompiler.java:46)
      fail-fast：多语句(:48) / COMMIT|ROLLBACK(:59)
      PARSE   EqlASTParser:22 → EqlParseTreeParser:22 → EqlASTBuildVisitor:63（19 处归一化）
      NORMALIZE SqlJoinTableSource.java:50（CROSS → CROSS_JOIN）
      transformBeforeAnalyze (:74-76) —— OracleEqlAstTransformer:74 故意空实现（方言此时不可得）
      TRANSFORM EqlTransformVisitor:226（collectNames → visitSqlProgram → SqlParamTypeResolver）
      transformAfterAnalyze (:81-83) —— OracleEqlAstTransformer:82（唯一真实生产消费者）
      GENERATE AstToSqlGenerator（1062 行，27 处方言 override）
      → CompiledSql（SQL 文本 + ISqlExprMeta[] + param builder + dataSetMeta）
  → 运行期 GenSqlTransformer:70（分片表名:130 / filter marker:155 / 参数计数校验:210）
  → 运行期分页改写 JdbcTemplateImpl:364-368
  → JDBC PreparedStatement + dialect.getSQLExceptionTranslator()（JdbcTemplateImpl:318-360）
  → 行→实体 OrmAssembly.getPropValues:98 → OrmSessionImpl.internalAssemble:365
```

#### 2.7 非 EQL 层的关键事实（用于后文维度裁定）

| 维度 | 事实 | 锚点 |
|---|---|---|
| 元模型 | 纯 XDSL（`orm.xdef`/`entity.xdef`/`sql-lib.xdef`/`dialect.xdef`/`orm-interceptor.xdef`/`view-entity.xdef`） | `nop-kernel/nop-xdefs/.../schema/orm/` |
| propId 稳定性 | 列的稳定整数编号，删除后**不重排**以保历史 JSON 兼容 | `entity.xdef:86-89` |
| 属性访问 | 生成 switch（`PROP_ID_TO_NAME` 双向表 + `orm_propValue(int)` switch），**零反射**；binder 数组 driver init 时一次构建 | `_Course.java:149-167`；`JdbcEntityPersistDriver.java:86` |
| DAO | **运行时动态创建的泛型单例**（`OrmDaoProvider.java:56-63`），零 codegen | 同上 |
| 批量 | `BatchActionQueueImpl` 按 entityName 分组 + 按 id 排序避免锁冲突 + **拓扑序两轮**（正序 save+update / 逆序 delete） | `BatchActionQueueImpl.java:62-87/153-200` |
| 乐观锁 | 声明式 `versionProp`（无 `@Version` 注解）：SET `version=version+1` + WHERE `and version=?` + 影响行数校验 | `GenSqlHelper.java:306-312/333-338`；`EntityPersisterImpl.java:505-521` |
| 多租户 | 写入强制 + 读取自动注入 + L1 缓存按租户隔离 + `OrmEntitySet` 跨租户重载 | `EntityPersisterImpl.java:427-453`；`GenSqlHelper.java:703-713`；`TenantOrmSessionEntityCache.java:32-61` |
| 数据权限 | `FILTER` syntax marker → `IEntityFilterProvider` → `IDataAuthChecker`；sql-lib 层还有 `roles`/`permissions` | `GenSqlTransformer.java:81-92`；`SqlLibManager.java:130-161` |
| 缓存 | **统一 `ICache` 抽象 + 完整 Caffeine 实现 + 分布式 provider SPI（Hazelcast/JCache）+ Micrometer 可观测 + 租户级全局失效 + 实体级 `_t` memo**。详见 §4.1.1 | `nop-commons/cache/{ICache,LocalCache,ICacheProvider,GlobalCacheRegistry}.java`；`HazelcastCache`；`JdbcTemplateImpl:371-395`；`OrmEntity:551` |
| 规模 | orm-eql 238 main / 90 test / **713 个测试方法**；orm 152 main / 148 test；dao 120 / 49；**orm-model 91 main / 6 test** | 实测 |
| 快照证据 | 15 个方言 SQL golden（35 条查询，`contains` 片段断言非逐字）；EQL 语料基线棘轮（1152 行、7 语料、36 语句）；方言能力矩阵棘轮（**无 features 维度**） | `src/test/resources/__snapshot/`；`TestEqlCorpusBaseline.java`；`dialect-capability-matrix.json` |

#### 2.8 关键区分：(A) 兼容性改写 / (B) 规则式重写 / (C) 代价优化 / (D) 执行期优化

> **本节是全文最重要的口径澄清。** 首版报告曾把 (A)+(B)+(C) 压成一栏"查询优化能力"给 ★☆☆☆☆，导致 (D) 被漏判、且把大量高价值兼容性工作误记为"无优化"。以下四类必须分开评。

| 类别 | 定义 | 成功判据 | 失败模式 |
|---|---|---|---|
| **(A) 语义兼容/可移植性改写** | 把一种方言写法翻译成另一种 | "目标方言**必须能表达**这个语义" | 编译期报 `dialect-not-support-feature` |
| **(B) 规则式查询重写** | 把语义等价的查询改写成**更省的**查询 | "改写后**更省**"（省列/省 join/省比较） | 无失败模式，靠等价性测试守护 |
| **(C) 代价优化（CBO）** | 统计 + 成本模型 + join 重排 + 索引选择 | "在所有可行计划中选最省的" | 估算错误 → 比数据库更差 |
| **(D) 执行期/运行时优化** | 计划缓存、L1/L2、批量化消 N+1、keyset 分页、SQL 模板复用 | "少一次查询 / 少一次 IO / 少一次编译" | — |

**为什么 (A) 不等于 (B)**：判据根本不同。(A) 的问题域是"这个语义在目标方言上怎么表达"，(B) 的问题域是"这个查询的工作量能不能减"。**`AstToSqlGenerator` 的 27 处 override 里没有一处改变查询工作量——它们只改变 SQL 的拼写。**

**最干净的反证（投影裁剪）**：nop 的 `select *` 被**全量展开成实体全部列**（`EqlTransformVisitor.java:1467` `buildSelectItems`，遍历 `getFieldExprMetas()` 全部条目）。这正是 (B) 类优化被**主动放弃**的位置——linq2db 有 `SqlQueryColumnOptimizerVisitor`（584 行两趟法），EF Core 有 `SqlTreePruner.PruneSelect`。所以这一维度上 nop 不是"做了兼容所以也强"，而是**这一格本来就空**。

**(B) 的空档部分是"不可做"而非"没做"**：EQL 的输出契约是**实体**而非 `ResultSet`，`propId` 驱动全列装配（`GenSqlHelper.genLoadSqlPart:80`），导航路径驱动隐式 join（`EqlTransformVisitor.java:968`）。投影裁剪会直接与实体装配/懒加载/`OrmEntitySet` 契约冲突。真正可做且便宜的 (B) 只有两条：空 `IN ()` → `1=0` 折叠（§6 P0）、feature 驱动的 GROUP BY 归约到主键（Django `collapse_group_by` 同款，需加一个能力位）。

#### 2.9 (D) 执行期/运行时优化的实证清单（首版报告漏判，本次补齐）

| (D) 类优化 | 证据 | 对标 |
|---|---|---|
| **N+1 消除** | `OrmBatchLoadQueueImpl.flush():577` 按 entityName 分组 → `_flushEntity:679` **每实体一条 SQL**；`_flushCollection` 先于 `_flushEntity`（集合加载会带出后续实体，故须先跑）；`OrmSessionImpl.assembleCollectionInMemory` 内存装配兜底；`maxBatchLoadSize` 分片（`EntityPersisterImpl.java:201-214`） | ent `loadOwner`（`car_query.go:416`）、Django `prefetch_related`、EF Core `Include` |
| **keyset（游标）分页** | `DaoQueryHelper.queryToFindNextSql:318` / `queryToFindPrevSql:338` + `appendPkOrderBy:364`；多键展开为 `(f1 cmp v1 or (f1=v1 and f2 cmp v2) ...)`，**刻意不用行值构造**以兼容 SQL Server；prev 同时翻转 `isDesc` 与 `nullsFirst`；排序列必须是本实体简单列否则显式报错（`requireOrderColumn:441`） | linq2db `wrapPaginateSubQuery`、MikroORM `wrapPaginateSubQuery`、Sequel 同款 |
| **SQL 模板按 propId 集复用** | `GenSqlHelper.genLoadSqlPart:80` / `genUpdateSql:192` / `genDeleteSql` 等按 `IntArray propIds` 生成 `EntitySQL`，驱动内单槽缓存（`JdbcEntityPersistDriver.java:62-73`，仅缓存默认方言防污染） | Diesel `QueryId` 思路的 Java 版 |
| **零反射属性访问** | 生成 switch（`_Course.java:149-167`）+ driver init 时一次构建 binder 数组（`JdbcEntityPersistDriver.java:86`），之后零反射 | Diesel/SeaORM 编译期分发 |
| **JDBC 批** | `nop-dao/.../jdbc/JdbcBatcher.java:49`，`addBatch:175` / `executeBatch:177`，失败时统一回调并清队列（`:252`） | — |
| **编译计划缓存** | `LoadedOrmModel.compileSql:102-124`，5 元组键；非默认 transformer 强制 bypass（`:95-100` 含裁定注释） | Hibernate 两级缓存的简化版 |
| **L1 命中免查** | `OrmSessionEntityCache`（`LinkedHashMap`，为录制回放保确定性）/ `TenantOrmSessionEntityCache` 租户隔离 | — |
| **批量写入拓扑序** | `BatchActionQueueImpl:153-200` 正序 save+update / 逆序 delete 两轮；按 id 排序避免锁冲突（`:62-87`） | Hibernate `ActionQueue` |
| **JOIN 去重（唯一一条真 (B)）** | `EqlTransformVisitor.java:986/1022` `join.incRef()`——同一关联被多条路径引用时只生成一次 join | MikroORM `deduplicate-joins`、Kysely `DeduplicateJoinsTransformer` |

> **修正声明**：首版报告把 (D) 混在"缓存成熟度 ★★☆☆☆"一栏，导致"查询优化能力"整栏被压成 ★☆☆☆☆。修正后 (D) 独立评为 **★★★★☆**（第一梯队），(B) 独立评为 ★☆☆☆☆，(C) ☆☆☆☆☆ 且全行业同空。综合结论方向不变但理由改变：**实质空档只有 (B)，且 (B) 中大部分对 nop 属"不可做"。**

---

### Part 3 — 40 个参考项目全景表

> 路径前缀 `~/sources/orm/`。"规则式"= 语义等价 AST 重写，无代价无统计。

#### 3.1 JVM / Java（13）

| 项目 | 优化器 | 代价 | 编译缓存 | 可移植性机制 | 类型化 DSL |
|---|---|---|---|---|---|
| **Apache Calcite** ★ | 规则(151 个) + Volcano/Cascades | ✅ | 元数据 memo | `SqlDialect` + 15 个 `supportsXyz` + `SqlImplementor` | ✅ `RelDataType` |
| Hibernate ORM 8 | ❌（仅 `pruneTableGroupJoins`） | ❌ | ✅ 两级（优秀） | `Dialect` + `SubquerySupport`/`ValuesListSupport`/`RowValueSupport`/`LockingSupport` 能力对象 | ✅ `JdbcType`/`JavaType` |
| jOOQ | ❌ 开源 / 57 条规则商业版 | ❌ | ❌ | `SQLDialect` **枚举** + `supportedBy()` 集合 + 渲染期 switch | ✅✅ 最强（codegen + `DataType`） |
| Ebean | ❌ | ❌ | ❌（`CQueryPlanManager` 是遥测） | `DatabasePlatform` 25 flag + **`SqlLimiter` 策略对象**（Oracle 三层 ROWNUM 包裹） | ✅ `DbType`（含 4 个 pgvector 类型） |
| Kotlin Exposed | ❌（仅 `OpBuilder` 构树归一） | ❌ | ❌ | `DatabaseDialect` + `FunctionProvider` + `DataTypeProvider` **三层分离** | ✅ infix DSL |
| Micronaut Data | ❌ **编译期生成 SQL** | ❌ | 仅 insert/update | `Dialect` 枚举 | ⚠️ 弱 |
| OpenJPA | ❌ | ❌ | ✅ `CompilationCache` | `DBDictionary`（~200 flag + 函数模板串） | ⚠️ |
| EclipseLink | ❌ | ❌ | ✅ `jpqlParseCache` | `DatabasePlatform` 39 子类 | ✅ |
| MyBatis | ❌ 纯模板 | ❌ | ✅ `MappedStatement` + 一级缓存 | ❌ 无方言 | ❌ 字符串 |
| MyBatis-Plus | 只有 AND/OR 悬挂消除 | ❌ | 复用 MyBatis | 复用 MyBatis | ❌ |
| Jdbi | ❌ 模板 | ❌ | ✅ `CachingSqlParser`（LRU 1000） | ❌ | ❌ |
| vertx-sql-client | ❌ 模板 | ❌ | ✅ `PreparedStatementCache` | ✅ 每 driver codec | ❌ |
| dynamic-datasource | 完全不涉及 SQL（DataSource 路由） | — | — | — | — |

#### 3.2 非 Java（27）

| 项目 | 语言 | 优化器 | 编译缓存 | 可移植性 | 类型化 |
|---|---|---|---|---|---|
| **EF Core** | C# | ✅ **最系统的规则式**：`QueryOptimizingExpressionVisitor`（`Any(i=>i==x)`→`Contains`）、`SubqueryMemberPushdown`、`SqlTreePruner`（JOIN 消除+投影剪枝，**倒序删表**）、`SqlExpressionSimplifying`（no-op CAST/嵌套 COALESCE/CompareTo 化简） | ✅ 表达式树键 + per-key lock | 68 个 `*TypeMapping.cs` + `TagWith` | ✅ LINQ 表达式树 |
| **linq2db** | C# | ✅ **最激进**：`JoinsOptimizer`（LEFT JOIN 消除 + 重复 JOIN 合并）、`SqlQueryColumnOptimizer`（两趟法未用列消除）、`SqlQueryOrderByOptimizer`（ORDER BY 上移，**带正确性论证**，排除 ROLLUP/CUBE）、`ExpressionTreeOptimizerVisitor`（常量折叠）、`BinaryExpressionAggregatorVisitor`（AND/OR 树平衡**防栈溢出**）、`SqlNullsOrderingLoweringVisitor` | ✅ **最精巧**：浅桶键+深验证+滑动过期+热度层级+容量淘汰+sweep | `BasicSqlBuilder` 5218 行 + 每 provider `SqlOptimizer` + `SqlProviderFlags` 位标志 | ✅ + 静态分析器 |
| **Kysely** | TS | ✅ **最干净的插件式重写**：`deduplicate-joins`、`handle-empty-in-lists`（`x IN ()`→`1=0`） | ❌（`CompiledQuery` frozen 不存储） | `DialectAdapterBase` 6 getter + 4 compiler 子类 | ✅ 强 |
| Django | Python | ⚠️ 5 类：`exists()` 重写为 `SELECT 1 LIMIT 1`、`collapse_group_by`（feature 驱动）、`split_exclude`→`NOT EXISTS`、`promote/demote_joins`、`trim_joins` | ❌（`get_compiler` **非** cached_property） | `backends/*/features.py` 487 行 + `operations.py` 动态 compiler | ❌ |
| SQLAlchemy | Python | ⚠️ `_normalize_froms` FROM/JOIN 去重 | ✅ **全行业标杆**：`_compile_w_cache` + 结构化 `CacheKey` + `_collect_params` 无副作用断言 | `dialects/*/base.py` ~18k 行 | ORM `Mapped[T]` |
| MikroORM | TS | ⚠️ `pruneJoinsForPagination` **+ `transferConditionsToJoin` 配套**、`wrapPaginateSubQuery`（keyset）、`preparePartitionLimit` | ⚠️ 仅每实例 memo | `AbstractSqlPlatform` + 5 方言 | 泛型 |
| Sequelize v7 | TS | ⚠️ `minifyAliases`（唯一把"缩短 SQL 文本"当显式优化目标）、`groupedLimit` 窗口函数 | ❌ | `DialectSupports` `freezeDeep` + `extendSupport` | ✅ 全 TS |
| Laravel | PHP | ⚠️ `compileGroupLimit`（`row_number()` 模拟 `LIMIT PER GROUP`）、**四种 IN 变体全折叠为空条件** | ❌ | 5 个 `*Grammar` + `compileComponents` 数据驱动骨架 | ❌ |
| Knex | JS | ❌（`_preValidate()` 是方言可覆写的静态子句-动词检查） | ❌ | 12 dialect | ❌ |
| ActiveRecord | Ruby | ❌ | ⚠️ `StatementCache` 三态 + **参数超限自动降级 unprepared** | `supports_*?` + `arel/visitors/*` 覆写 `ToSql` | `with_cast_value` |
| Sequelize(其他) | — | — | — | — | — |
| Diesel | Rust | ❌ | ✅ **`TypeId` 编译期键**（`QueryId` trait + 三分类 unsafe 判定，文档即设计说明） | `backend.rs` + 子模块 | ✅ **最强**（`Selectable`/`Queryable` 编译期约束 select 与 load 类型一致） |
| sqlx | Rust | ❌ | ⚠️ `MtimeCache` 缓存的是离线类型信息 | 按 DB crate 分 | ✅ **编译期 SQL+参数+输出列校验**（`SqlSafeStr` 只认 `&'static str`） |
| SeaORM | Rust | ❌（生成器在外部 `sea-query`） | ❌ | `DbBackend` + 宏 | ✅ 强 |
| GORM | Go | ❌ | ❌（`prepare_stmt.go` 是驱动级） | `Dialector` 7 方法 | 泛型 |
| ent | Go | ⚠️ eager loading 外键去重批量化 + `LimitNeighbors` 窗口函数 | ❌ | `Builder.dialect` 沿树传播 | ✅ 强 codegen |
| bun | Go | ❌ | ❌ | ✅ **`Feature` 位标志** + `guardLineComment`（CVE-2024-44906 修复） | `model_table_*` |
| Ecto | Elixir | ❌（`ecto_sql` 不在仓库） | ✅ **`:nocache` 传染式合并** + 三态 ETS 原子切换 | adapter 回调 | ✅ 宏 DSL |
| Doctrine ORM | PHP | ❌ | ⚠️ 缓存 **DQL→AST 的 `ParserResult`**（md5 key 含 platform+filterHash+hydrationMode+盐） | DBAL `AbstractPlatform`（未 clone） | ❌ |
| Phalcon | PHP | ❌ | ❌ | `Db/Dialect.php` 1730 行 + `supports*()`；`Dialect::limit()` **强制整数化** | ❌ PHQL |
| peewee / Tortoise / Drizzle / TypeORM / Sequelize(v6) / Prisma 8 | — | ❌ | ❌（Drizzle `$withCache`/TypeORM `src/cache/` 都是**结果**缓存） | 各自 mechanism | 强弱不等 |
| **Dolt** ★ | Go | ✅ 在 go-mysql-server；本仓是**统计生产者**：histogram 含 `DistinctCount`/`NullCount`/`UpperBound`、按索引 qualifier 组织 | GMS 提供 | session root / branch 感知 | ❌ |
| **Calcite** ★ | Java | ✅ Volcano + **DPHyp 超图 DP 连接重排序** + 151 规则 + Hep 无代价引擎 | 元数据 memo | 物理属性 + `RelSubset` | ❌ |

---

### Part 4 — 逐维度裁定：nop-orm 是不是最强最完善的？

#### 4.1 评分（★越多越强；"参考标杆"为该维度的行业最优）

| 维度 | nop-orm | 评分 | 标杆 | 裁定 |
|---|---|---|---|---|
| **方言能力建模** | 31 个 xdef 能力位 + 15 方言 + 3 个 mixin 基座 + 多父继承 + 显式关断 + `Boolean.TRUE.equals` 保守缺省 + 统一错误码 | ★★★★☆ | Hibernate 8 的 `SubquerySupport` 带 `Context` 维度 | **领先**。唯一可改进点：能力位是扁平 boolean，缺"按场景（QUERY/INSERT）区分"的二维表达——这正是 Hibernate 8 升级解决的问题 |
| **不支持时的失败模式** | 一律**编译期** `nop.err.eql.dialect-not-support-feature`（唯一例外 `supportWithAsClause` 降级内联） | ★★★★★ | — | **全场最强**。对比：Hibernate `UnsupportedOperationException`（运行期）、jOOQ `supportedUntil` 隐式退化、MyBatis 无方言、Exposed `UnsupportedByDialectException`（运行期） |
| **跨库语义正确性** | 3VL SAFE_EQ 两/三支展开、NULLS 空序 CASE 兄弟键改写（含三道 fail-fast）、`_all` 的 De Morgan 奇偶判定、LIMIT↔FETCH、`IS TRUE` 真值表展开 | ★★★★★ | — | **全场最强**。这些是 Hibernate/jOOQ/Django 都没有的系统性工作 |
| **查询优化能力 (A) 跨库兼容性改写** | 31 能力位 + 3VL SAFE_EQ 两/三支展开 + NULLS 空序 CASE 兄弟键改写（三道 fail-fast）+ `_all` De Morgan 奇偶判定 + LIMIT↔FETCH + `IS TRUE` 真值表展开 | ★★★★★ | — | **全场最强**。对比 Hibernate/jOOQ/Django 都没有系统性工作 |
| **查询优化能力 (B) 规则式重写** | ❌ 零优化规则。唯一一条：JOIN 引用计数去重（`EqlTransformVisitor.java:986/1022`）。**且大部分属"不可做"**——EQL 输出契约是实体、propId 驱动全列装配、导航路径驱动隐式 join，投影裁剪/谓词下推会与装配和懒加载契约冲突 | ★☆☆☆☆ | linq2db / EF Core / Kysely | **空**。可做且便宜的只有：空 `IN ()` 折叠、feature 驱动 GROUP BY 归约 |
| **查询优化能力 (C) 代价优化** | ❌ 零统计、零成本模型、零 join 重排、零索引选择 | ☆☆☆☆☆ | Calcite / Dolt | **空，且全行业同空**（jOOQ/Hibernate/Diesel/SQLAlchemy/Django 全空）。**不做是对的**——错误估计比没有估计更糟 |
| **查询优化能力 (D) 执行期/运行时** | N+1 消除（每实体一条批量 SQL）+ keyset 分页 + SQL 模板按 propId 集复用 + 零反射 + JDBC 批 + **有界 Caffeine 编译计划缓存**（`OrmSessionFactoryBean:95-96`）+ L1/L2/session/查询结果四层缓存（统一 `ICache`，见 §4.1.1）+ 实体级 `_t` memo + 批量写入拓扑序 + JOIN 去重 | ★★★★☆ | ent / Diesel 同档 | **第一梯队**（首版漏判缓存基础设施，本次修正）。缓存维度**不弱于 linq2db**，见 §4.1.1 对比表 |
| **编译期类型安全** | `QueryBean` + `FilterBeans` + 字符串属性路径（需 `isValidPropPath` 校验）；EQL 有 AST 但无类型安全构造 API | ★★☆☆☆ | Diesel / sqlx / EF Core / ent | **弱**。无 LINQ 表达式树、无方法名派生查询（无 `findByXxx`） |
| **查询库 / SQL 复用** | `*.sql-lib.xml`（native/eql/query 三类）+ `fragments` + 启动期语法校验 + `roles`/`permissions` 数据权限下沉 | ★★★★☆ | jOOQ 的 57 条 pattern / Django 的 sqlmigrate | **领先**（这是 nop 独有的组合） |
| **Delta / overlay 能力** | XDSL `x:extends`（多路径/super）+ `x:override`(replace/remove/merge) + `x:gen-extends` + VFS `DeltaResourceStore` + 合并冲突报 location + `_dump` 来源标注 + 模型热重载（refCount 延迟销毁） | ★★★★☆ | — | **领先**，但见 §4.3 的两个硬短板 |
| **元模型驱动 codegen** | 极端 model-first：CRUD SQL 运行时生成（`GenSqlHelper`）、DAO 运行时动态创建（零生成）、属性访问生成 switch（零反射）、binder 数组一次构建 | ★★★★★ | — | **全场最强之一**（无同类对手把"零反射 + 零 DAO 生成 + 模型驱动 SQL"三者同时做到） |
| **propId 稳定性** | 删除后不重排，保历史 JSON 兼容，为 `[[propId,old,new]]` 紧凑差量预留 | ★★★★★ | EF Core shadow property id / Diesel `QueryId` | **领先** |
| **多租户 / 数据权限** | 写入强制 + 读取自动注入 + L1 缓存租户隔离 + 跨租户集合重载 + `FILTER` marker + `DataAuthEntityFilterProvider` + CROSS JOIN 特例防泄漏 | ★★★★★ | — | **全场最强**。对比：Hibernate 无内置多租户（需 hibernate-envers/第三方），EF Core 无全局行级安全（需 `HasQueryFilter` 逐实体手写），jOOQ 无 |
| **批量 / 拓扑序** | `BatchActionQueueImpl` 分组 + 按 id 排序 + 正序 save/update、逆序 delete 两轮 | ★★★★☆ | Hibernate `ActionQueue`（有 `breakCost` 启发式） | **领先**（Hibernate 的 ActionQueue 是同类最复杂实现） |
| **缓存成熟度** | **统一 `ICache` 抽象 + 完整 Caffeine 实现 + 分布式 provider SPI + Micrometer 可观测 + 租户级全局失效 + 实体级 memo**。**不弱于 linq2db** | ★★★★☆ | linq2db `QueryCache` / Hibernate 两级缓存 | **基础设施强于 linq2db**（分布式 provider / 可观测性 / 租户失效 / 实体 memo 四项领先），弱在无"缓存能否复用"的显式判定抽象。**唯一实质缺口**：L2 开启后强制全属性加载 + 无"按查询缓存集合 + tag 失效"的结果缓存 |

#### 4.1.1 缓存基础设施的完整事实（首版报告严重误判，本次修正）

首版报告称"L2 半成品、只有 LocalCacheProvider、计划缓存是裸 Map 无上限、实体上无缓存"——**四处全错**。核实结果：

| 事实 | 证据 |
|---|---|
| **统一 `ICache` 抽象** | `nop-commons/cache/ICache.java:22-79`：`getIfPresent` / `computeIfAbsent` / `getAllPresent`（批量）/ `putIfAbsent` / `getAndSet` / `getConfig` / `estimatedSize` / 异步变体 |
| **`LocalCache` = 完整 Caffeine 封装** | `LocalCache.java:36`；`maximumSize:83` / `expireAfterWrite:87` / `expireAfterAccess:91` / `recordStats:108` / Micrometer `CaffeineCacheMetrics.bindTo:76` / `AsyncLoadingCache:120` / policy introspection `:166-172` / `CacheStats`（evictionCount、evictionWeight、hitCount、missCount、loadSuccessCount、loadFailureCount）`:186-194` / `IConfigRefreshable` / `IDestroyable`。淘汰策略为 Caffeine W-TinyLFU，**比 linq2db 手写的"热度层级提升 + 逐级淘汰"更成熟** |
| **`ICacheProvider` 是完整 SPI，可插分布式** | `ICacheProvider.java:10-23`；装配链 `SessionFactoryConfig.globalCache` → `SessionFactoryImpl:71/202/206` → `IOrmSessionFactory.getGlobalCache():50` → `OrmTemplateImpl:88` → `EntityPersisterImpl:94 env.getGlobalCache(name)`。已有 **`HazelcastCache`**（`nop-cluster/nop-hazelcast/nop-hazelcast-cache`）与 `JavaxCache`（JCache）两个实现；`orm-defaults.beans.xml:26` 用 `ioc:default="true"` 允许覆盖 |
| **编译计划缓存 = 有界 Caffeine 缓存，非裸 Map** | `OrmSessionFactoryBean.java:95-96` `LocalCache.newCache(name, newConfig(CFG_QUERY_PLAN_CACHE_SIZE).useMetrics())`；类型为 `ICache<QueryPlanCacheKey, IOrmCachedQueryPlan>`（`SessionFactoryImpl:73`，`IOrmSessionFactory:48`）；`:56-58` `refreshConfig()` 热刷新重设 maximumSize |
| **L2 全局实体缓存 = 有界 + 有过期** | `OrmSessionFactoryBean:102-107` `CacheConfig(maximumSize=CFG_ENTITY_GLOBAL_CACHE_SIZE, expireAfterWrite=CFG_ENTITY_GLOBAL_CACHE_TIMEOUT)`；命中路径 `EntityPersisterImpl:523-533 loadFromGlobalCache`；写路径 `:535-540 updateGlobalCache`（异步）；失效 `:550 evictGlobalCache`，**事务内注册 `ITransactionListener.onAfterCommit`（`:556-565`）避免未提交事务污染缓存** |
| **JdbcTemplate 查询结果缓存** | `JdbcTemplateImpl:371-395`：`CacheRef`(cacheName+cacheKey) + **`LongRangeBean` range 一致性校验**（`:393` warn `cache-get-data-range-mismatch`）+ per-cacheName `cacheMap.computeIfAbsent:384` + `clearQueryCache:212` / `clearQueryCacheFor:221` / `evictQueryCache:228` + 无 provider 时一次性 warn（`:373` `noCacheProviderWarned.compareAndSet`） |
| **session 通用缓存** | `OrmSessionImpl:1285-1290` `MapCache<>("orm-session-cache", false)`——session 作用域不需要过期，语义正确；`:323-324/767-768` close 时 clear |
| **实体级 memo（`_t` 临时属性）** | `OrmEntity.java:551-552` `make_t().computeIfAbsent(key, fn)`；reset/unload 自动清空；官方示例与说明 `docs-for-ai/05-examples/entity-class.java:53/67`。**这是 linq2db / EF Core / Diesel 都没有的形态** |
| **租户级全局失效** | `GlobalCacheRegistry.java:26-81`：`clearForTenant(tenantId):49` / `clearAllCache():55` / `register:65` / `unregister:74`；ORM 侧 `OrmSessionFactoryCacheManagement` 注册（`OrmSessionFactoryBean:124-127`），`clearCache():154-163` 三管齐下（查询结果 + 全局实体 + EQL 编译 + ormModel） |
| **ORM 层可观测性** | `OrmSessionFactoryBean:115-119` `OrmMetricsImpl(GlobalMeterRegistry)` / `EmptyOrmMetricsImpl`；plan cache `useMetrics()`；`CaffeineCacheMetrics` |

**修正后的对比**：

| 维度 | nop | linq2db | 判定 |
|---|---|---|---|
| 淘汰/过期策略 | 外包 Caffeine（W-TinyLFU） | 手写（浅桶键 + 深验证 + 热度层级 + 容量淘汰） | **nop 更成熟** |
| 分布式 provider | SPI + Hazelcast + JCache | 无 | **nop 领先** |
| 可观测性 | Micrometer + CacheStats + OrmMetrics | 有 `CacheStats` 但无 metrics 导出 | **nop 领先** |
| 跨租户失效 | `clearForTenant` | 无对应概念 | **nop 领先** |
| 实体级 memo | `_t` + `computeIfAbsent` | 无 | **nop 领先** |
| "缓存能否复用"的显式判定 | 无 | 无（但 Hibernate 有 `ConcreteSqmSelectQueryPlan.isCompatibleWith(bindings, queryOptions)`） | **三家都缺**，见 §6 P2 |
| 单飞/防惊群 | 依赖 Caffeine `get(key, fn)` 原子语义 | 单飞 sweep | 相当 |

**本轮核实发现的两个真实缺陷（净增量）**：

| # | 缺陷 | 证据 | 影响 | 修法 |
|---|---|---|---|---|
| 1 | **`refreshConfig()` 硬转 `LocalCache`，与分布式 provider 冲突** | `OrmSessionFactoryBean:56` `(LocalCache<?,?>) sessionFactory.getQueryPlanCache()`；`:61` `(LocalCacheProvider) sessionFactory.getGlobalCache()` | 用户注入 Hazelcast/JCache provider 后，**任何配置刷新触发即 `ClassCastException`**。同时说明 plan cache 目前无法走分布式 provider | 改为按 `ICacheProvider` 接口能力判定（`instanceof` 或 `ICacheManagement` 扩展点）；plan cache 本身应可配置为独立 provider |
| 2 | **`ICache.computeIfAbsent` 默认实现非原子** | `ICache.java:34-46` 是 `get` then `put` 两步；`LocalCache:219`、`MapCache:86-87` 已覆写为原子，**`HazelcastCache` / `JavaxCache` 未覆写** → 落到非原子默认 | 走分布式 provider 时 `computeIfAbsent` 存在竞态（同一 key 重复加载） | 默认实现改 `putIfAbsent` 语义；或两个分布式 impl 补覆写 |
| **变更集 / diff API** | ❌ 只有原料（`orm_dirtyOldValues()`/`orm_dirtyNewValues()`/`orm_dirtyPropIds()`），无 ChangeSet 装配、无序列化 | ★☆☆☆☆ | EF Core `ChangeTracker` / Hibernate `PersistentEntityChange` | **缺失** |
| **CDC / 变更日志** | ❌ `nop-cdc-core` 只有空 interface，`nop-cdc-debezium` 只有 pom；`IOrmDaoListener` 无生产实现 | ☆☆☆☆☆ | Debezium / Canal | **缺失** |
| **表继承 / discriminator** | ❌ 只有 `ext:baseClass` 的 Java 继承 | ☆☆☆☆☆ | JPA 单表/joined-table 全家桶 | **缺失** |
| **关联预加载合并** | ❌ 一律独立批量查询（`OrmBatchLoadQueueImpl`），不能合并为一次 join | ★★☆☆☆ | Hibernate `join fetch` / EF Core `Include` 单查询 / ent `loadOwner` | **弱**（N+1 已解，但 round-trip 数未优化） |
| **测试充分性** | orm-eql 713 测试方法 / 90 文件 + 三套棘轮（方言 golden / 语料基线 / 能力矩阵）+ H2 真库执行 + `@Testcontainers` PG/MySQL（需 `nop.test.docker.enabled` 门控）；**但 orm-model 91 main / 6 test** | ★★★☆☆ | — | **不均衡**。`OrmEntityModelInitializer`（730 行、十余类派生逻辑）只有 6 个测试文件 |
| **死代码 / 未接线信号** | `view-entity.xdef` + 7 个类零使用、`OrmManyToManyHelper`(194行) 零引用、`IOrmRevisionSupport` 零引用、`TenantAwareOrmModelProvider` 零引用、`TenantCachedQueryPlan` 因无 `setTenantModel(true)` 而死、`EqlASTProcessor` 499 行零引用、8 个方言能力位 xdef 自陈"预留未接线" | ★★☆☆☆ | — | **弱**。诚实度很高（xdef 逐条注记），但客观上是成熟度信号 |
| **生态 / 第三方 / 文档** | 内部平台，无外部生态 | ★☆☆☆☆ | 全部对照项目 | **不参与竞争**（不同定位） |

#### 4.2 结论

**不是最强大最完善的 ORM。** 分轴裁定：

- **明确领先（且是全场最强）**：跨库语义正确性、不支持时的失败模式、多租户/数据权限强制注入、元模型驱动 codegen 深度、propId 稳定性、批量拓扑序、查询库 + 数据权限下沉、**(D) 执行期/运行时优化**。
- **第一梯队**：方言能力建模、Delta overlay、乐观锁/修订链、测试证据基础设施（orm-eql 部分）。
- **落后一整档**：**(B) 规则式查询重写**（且其中大部分对 nop 属"不可做"，见 §2.8）、编译期类型安全查询 API。
- **同行业同空**：**(C) 代价优化**——不构成减分项。
- **明显缺失**：变更集/diff API、CDC、表继承、关联预加载单查询合并、数据库原生序列（`getGeneratedKeys`/`nextval`）、schema migration 与 ORM 模型联动、按查询缓存集合 + tag 失效的结果缓存。
- **不参与竞争**：生态、第三方、人才供给。

一句话定位（准确，非贬低）：

> **nop-orm 是"可逆计算 + 跨库可移植"的编译型 ORM，不是优化型 ORM。** 它把工程投入压在**语义正确性、可诊断失败、与执行期 IO 削减**上，而不是查询计划重写与代价决策——这个取舍在"一套 EQL 跑 15 个数据库"的场景下是理性的，但它不等于"最完善"。
>
> ⚠️ **口径警示（首版报告已修正，见 §2.8）**：不要把 nop 在 `eql-cross-db-compat` 分支的大量方言兼容工作读成"查询优化"。兼容性改写 (A) 的判据是"目标方言能否表达该语义"，规则式重写 (B) 的判据是"改写后是否更省"——两者判据不同。首版报告曾把 (A)/(B)/(C) 压成一栏给 ★☆☆☆☆，既误判了 (B)、又漏判了 (D)，属口径错误。修正后：(A) ★★★★★ / (B) ★☆☆☆☆（且大部分不可做）/ (C) ☆☆☆☆☆（全行业同空）/ (D) ★★★★☆。

#### 4.3 Delta overlay 的诚实短板

机制本身 production-ready（方言层多父+mixin 是最佳证明：`postgresql.dialect.xml:3` 三父继承、`mysql5.7` 用 1 个元素关断 3 个继承能力位），但对"ORM 实体级细粒度定制"这个具体诉求只做到一半：

| 问题 | 证据 |
|---|---|
| **跨模块实体覆盖是"整体替换"而非"列级 patch"** | `OrmModelLoader.java:93-101`：同名实体只有三选一——整体替换 / 整体替换 / **抛异常**。列级 `x:override="merge"` 只在单文件内生效 |
| **改列必重生成 Java** | 否则 `_Xxx` 的 `PROP_ID_TO_NAME` 静态表与模型不一致 → `orm_propName` 返回错值。Delta 省不掉编译，只省掉"复制整份文件" |
| **默认实体层（系统列约定）没有自动生效路径** | `orm.xdef:10` / `entity.xdef:63` / `sql-lib.xdef:3` 声明的三个 `default-*.xml` 路径**在仓库中都不存在**。⚠️ **但这不是缺陷** —— 该机制**按设计软失败**：`XDefinition.java:73` `LOG.trace("nop.xdef.ignore-unknown-default-extends")` + `return null`，另有 `x:extends="none"` 显式抑制。清理它反而有害（补文件会导致 defaults 双重加载；改声明对 `wf`/`xlib`/`xbiz` 无目标路径）。全仓共 6 处声明（主源 `nop-xdefs`）+ 1 处测试资源 |
| ~~defaults 文件本身有疑似笔误~~ —— **本行经三轮核实后判定为误报，已撤销** | ~~`default.orm.xml:28`：`deleteFlagProp="delVersion" deleteVersionProp="delVersion"`~~ ⇒ **这不是笔误**：(a) `OrmEntityModelInitializer.java:287-288` 有自动同化机制（只声明 `deleteVersionProp` 时自动同化 `deleteFlagProp`）；(b) `docs-for-ai/02-core-guides/logical-deletion.md:9/:11/:76` 明文规定 **方案 A（推荐）** 就是"将 `deleteFlagProp` 和 `deleteVersionProp` 同时指向它"，`orm-model-design.md:477` 亦标"默认推荐 `delVersion`"。**按"笔误"去修会把正确代码改成错的** |
| **漏写 `x:extends="super"` 的失败模式是静默的** | `docs-for-ai/02-core-guides/delta-customization.md:87` 自陈"整文件替换，其余 bean 静默丢失，且不报错" |
| **overlay 后无法卸载一段继承来的能力** | 只能 `x:override="replace"` 置空（方言层用 `<forUpdateNowait x:override="replace"/>` 表达"未配置"，说明可行但需手工技巧） |

---

### Part 5 — 值得借鉴的具体设计（按投入产出比排序）

> 这些是从 40 个项目里筛出的、**可以直接落到 nop-orm 上**的点。

#### 5.1 高价值（架构级，建议进 roadmap）

| # | 来源 | 设计 | 为什么适合 nop |
|---|---|---|---|
| 1 | **Hibernate 8** `dialect/sql/ast/spi/SubquerySupport.java:76-104` | 把方言能力从上百个扁平 `boolean supportsXyz()` 升级为带 `Context` 维度的不可变能力对象（Builder 模式）。`ValuesListSupport.Context` = `QUERY`/`INSERT`；`SubquerySupport.Feature` 9 项含 `LATERAL`、`MUTATION_JOIN`、`IN_PREDICATE_LHS` | nop 的 31 个扁平能力位**表达不了**"这个方言在 INSERT 场景支不支持 values"这类二维问题。加 `Context` 维度成本极低，且能顺手解决 `supportOutputForUpdate`/`supportUpdateFromJoin` 这类"声明了但没有场景"标志的语义 |
| 2 | **linq2db** `Internal/Linq/Builder/BinaryExpressionAggregatorVisitor.cs:8-52` | AND/OR 树平衡：展开嵌套同类二元节点成 leafs，**leafs > 3 时 `CompactTree` 重新平衡**。注释给出量化动机：N 个叶子导致 N×M 递归，约 1000 项栈溢出；平衡后是 `LOG2(N)*M` | 这是**正确性问题不是性能问题**。nop 的 `CollectionOperatorTransformer` 合并 `_some`/`_all` 条件时（`mergeConditions`）同样会构造深层 AND/OR 树，长条件列表下有栈溢出风险。成本极低（一个 visitor），收益是硬正确性 |
| 3 | **linq2db** `SqlQueryColumnOptimizerVisitor.cs:13`（两趟法）| **两趟**：第一趟收集所有列引用，第二趟删除未用列。单趟"边走边删"会漏 | nop 现在是**全量展开**（`buildSelectItems:1467`）。若要做投影裁剪（例如支持 `QueryBean` 的 `fields` 裁剪），必须两趟。参考实现 + 正确性论证都在这里 |
| 4 | **MikroORM** `QueryBuilder.ts:4448` + `:4512` | JOIN 剪枝**必须与"搬条件"配套**：`pruneJoinsForPagination` 删 JOIN，`transferConditionsToJoin` 把被删 alias 的 WHERE 条件搬到对应 ON | 只有 MikroORM 同时实现了这两个。**只做前者会丢条件**——这是很多 ORM 的隐性 bug 来源。nop 若做 JOIN 剪枝必须成对实现 |
| 5 | **EF Core** `SqlTreePruner.cs:223-227` | 剪 JOIN/表必须**倒序**遍历，注释解释原因：后一个 join 的 predicate 可能引用前一个表 | 同上，是配套的正确性细节 |
| 6 | **Kysely** `src/plugin/` + `query-executor-base.ts:45-59` | 插件即优化器：`transformQuery` 钩子 + `OperationNodeTransformer`（节点级/列表级/parent-aware 统一重写原语）+ **插件必须返回同 kind 否则抛错** | nop 已有 `IEqlAstTransformer`（before/after analyze 两槽）+ 生成的 `EqlASTOptimizer` 递归机制 = **基础设施已完备，缺的只是规则集和插件契约**。加规则的成本极低 |
| 7 | **Django** `sql/compiler.py:202-233` | `collapse_group_by`：若 `features.allows_group_by_selected_pks`，把 GROUP BY 归约到主键列。**功能依赖归约**由数据库能力驱动 | nop 的 `GroupBy` 处理目前无归约。MySQL/PG 支持归约、SQLite 不支持——正好可以用现有 31 能力位加一个表达 |
| 8 | **Laravel/Kysely** | 空 `IN ()` → `1 = 0`，`NOT IN ()` → `1 = 1`。四种 IN 变体（in / not in / inRaw / notInRaw）全部覆盖 | nop 目前 IN 列表为空时的行为未定义。低成本、高价值（空 IN 是 NPE 类 bug 的常见来源） |
| 9 | **linq2db** `SqlQueryOrderByOptimizer.cs:271-303` | ORDER BY 上移给出**两个充分条件**（DISTINCT 输出列完全决定 / 是分组键的函数）并**显式排除 ROLLUP/CUBE/GROUPING SETS** | 全调研中唯一给出**可读正确性证明**的优化。nop 的 `NullsOrderRewriter` 已经在做同类事（且已有三道 fail-fast），这个"正确性论证 + 显式排除"的做法应作为 nop 后续优化的模板 |

#### 5.2 中价值（局部可抄）

| # | 来源 | 设计 | 适配 nop 的方式 |
|---|---|---|---|
| 10 | **linq2db** `QueryCache.cs:47` | 浅桶键（`ResultType,ContextType,ConfigurationID,QueryFlags,ChainHash`）+ 桶内 `Query.Compare` 深验证 + 滑动过期 + 热度层级（`PendingHitPromotionMinHits=5`）+ 每桶 16 上限 + 每 5 分钟 sweep + `volatile` 发布 | nop 的 `LoadedOrmModel` 编译计划缓存目前是单键 Map，无过期无容量。Ecto 的**传染式 `:nocache`** 合并（`planner.ex:1089/1100`：任一段不可缓存则整体不可缓存）可以直接套到 nop 的 `QueryPlanCacheKey` 上（如 `enableFilter` 参与键已是同类设计，`QueryPlanCacheKey.java:22-23` 有注释说明） |
| 11 | **Diesel** `connection/statement_cache/mod.rs:1-104` | 用 `TypeId` 做 prepared statement 缓存键；四类 unsafe 明确分类（带绑定参数的 `IN`、变行数 `INSERT`、`UPDATE`、`SqlLiteral`）；`:93-104` **主动说明"我们没做 perfect-hashing map，因为只省纳秒而整体要花微秒到毫秒"** | nop 的方言 SQL 已有模板化机制，可把"哪些查询可缓存"做成显式分类。**更值得学的是那条工程判断**——明确说出优化的收益上界 |
| 12 | **Ebean** `SqlLimiter` + `OracleRownumSqlLimiter.java:31-40` | 分页不是"渲染时换个关键字"，而是**方言提供一个整查询重写策略对象**（Oracle 三层 `ROWNUM` 嵌套包裹） | nop 的 `IPaginationHandler` 已是策略对象（`LimitOffset`/`OffsetFetch`），方向一致。可补的是"策略对象能表达任意整查询变换"这个定位的文档化 |
| 13 | **Hibernate** `ConcreteSqmSelectQueryPlan.java:449-497` | 计划缓存**分级失效**：参数绑定级失效 vs queryOptions 级失效；`jdbcSelect.isCompatibleWith(bindings, queryOptions)` 把"缓存能否复用"变成可回答的问题 | nop 目前 `QueryPlanCacheKey` 是 5 元组全量键（`QueryPlanCacheKey.java:19-33`），粒度粗。可引入"部分键 + 兼容性判定"降低 key 空间 |
| 14 | **Knex** `querycompiler.js:85-113` | `_preValidate()`：一张 `invalidClauses` 表，方言可覆写，"这个子句对这个动词无意义就编译期抛错" | 成本极低。nop 的 `AstToSqlGenerator.checkLockOptions`（`:132-142`）已经做了这一族的事（nowait/skipLocked/forUpdate 三者互斥），但**抛的是无错误码的 `IllegalArgumentException`——全管线唯一破例**，应统一到 `NopException` + 错误码 |
| 15 | **Bun** `dialect/feature/feature.go:13` | `type Feature = internal.Flag`（位标志）+ 名字表用于错误信息 | nop 的 31 个能力位若未来膨胀，可考虑位标志。**但** nop 的能力位是 xdef 声明的（需要 XML 可读性），位标志在 XML 里不可读——**这条不建议抄**，列出仅为对照 |
| 16 | **ActiveRecord** `statement_cache.rb:46` + `database_statements.rb:39-52` | 三态缓存（完整 Query / PartialQuery / **绑定数超限自动降级 unprepared**） | nop 的 `IN` 列表超限是**编译期硬报错**（`EqlTransformVisitor.java:743-754`），比 ActiveRecord 更严格。但可考虑"编译期报错 + 提示改用 sql-lib 片段/临时表"的操作建议 |
| 17 | **Sequelize** `minifyAliases` | 唯一把"缩短 SQL 文本"当显式优化目标并把别名映射做成双向表的实现（效果：PG 14+ 更好的 prepared statement 复用 + 减少网络字节） | nop 的 `SeqAliasGenerator`（`sql/SeqAliasGenerator.java`）已是序列生成器，可对比是否需要别名压缩策略 |
| 18 | **Calcite** `RelMdUtil.guessSelectivity` | 无统计时的兜底常数**公开可查**：`IS_NOT_NULL`×0.9 / `EQUALS`×0.15 / `COMPARISON`×0.5 / 其他×0.25 | 若 nop 未来考虑任何形式的基数感知（如分页 total 估算），这类常数应显式集中并有出处，而不是散落在代码里 |

#### 5.3 明确不建议抄

| 来源 | 做法 | 不抄的理由 |
|---|---|---|
| jOOQ 商业版 | 57 条 pattern 重写迭代到不动点 | nop 的 EQL 输入面比 jOOQ DSL 宽得多（含 DDL/事务/窗口/分组操作符），pattern 集合会失控；且 jOOQ 自己承认这主要不是性能优化 |
| Hibernate | `AbstractSqlAstTranslator` 全量 visitor 分派（8 类 statement × 数十子句） | nop 的双通道（`AstToEqlGenerator` 基类 + 27 处 override）已经用更小的 override 面达到同等效果 |
| Diesel | `TypeId` 做缓存键 | 需要 Rust 级类型系统承载"SQL 结构相同性"。Java 的 `propId`/类型映射无法直接对应该语义 |
| sqlx | `SqlSafeStr` 只认 `&'static str` | 这是 Rust 无反射 + 强 `Drop` 语义的产物。Java 侧 nop 已有等价且更实用的方案：`DaoQueryHelper` 对 owner/field/func/props-key 全量 `isValidPropPath` 校验（`:104-125`，含 `update ... set` 的 key，`:300-306` 有注释说明动机） |
| Bun | `Feature` 位标志 | 见 §5.2 #15 |

---

### Part 6 — 建议（按优先级）

| 优先级 | 建议 | 依据 | 成本 |
|---|---|---|---|
| **P0** | 修 `refreshConfig()` 硬转 `LocalCache`/`LocalCacheProvider`（分布式 provider 下 `ClassCastException`）；`ICache.computeIfAbsent` 默认实现改原子语义 | §4.1.1 缺陷 1、2 | 极低 |
| **P0** | 统一 `AstToSqlGenerator.checkLockOptions` 的异常类型到 `NopException` + 错误码 | §5.2 #14；当前是全管线唯一破例 | 极低 |
| **P0** | 补空 `IN ()` → `1=0` / `NOT IN ()` → `1=1` | §5.2 #8 | 极低 |
| ~~P0~~ **已撤销** | ~~修 `default.orm.xml:28` 的 `deleteFlagProp` 疑似笔误 + 补齐 `xdef:default-extends` 声明的不存在路径~~ —— **两条都撤销**：前者是文档化的推荐写法（见 §4.3 撤销行）；后者按设计软失败，补齐反而有害 | §4.3 | — |
| **P0** | 修 `OrmSessionFactoryBean.refreshConfig()`：守卫恒假导致 global cache 的容量/过期热刷新在**所有部署**中永久失效 | §4.1.1 缺陷 1 | 极低 |
| **P1** | 引入 AND/OR 树平衡（防栈溢出） | §5.1 #2；`CollectionOperatorTransformer.mergeConditions` 有构造深层树的风险 | 低 |
| **P1** | 方言能力位加 `Context` 维度（对齐 Hibernate 8 `SubquerySupport`） | §5.1 #1；能消解 8 个"声明未接线"标志的语义 | 中 |
| **P1** | 建 `SqlTreePruner` 等价物：两趟法 JOIN/投影剪枝（含"搬条件"配套 + 倒序遍历） | §5.1 #3/#4/#5；需先确认 `QueryBean.fields` 语义 | 中高（且会改变输出形态，需重新棘轮方言 golden） |
| **P2** | 把 `IEqlAstTransformer` 升级为**插件式规则集**（对齐 Kysely 的 `transformQuery` 契约 + 同 kind 校验） | §5.1 #6；基础设施已完备，只是缺规则与契约 | 中 |
| ~~P2~~ **已撤销** | ~~编译计划缓存加过期 + 容量上限 + 传染式 nocache~~ —— 撤销原因：核实发现该缓存**已经**是有界 Caffeine 缓存（`OrmSessionFactoryBean.java:95-96` `LocalCache.newCache(..., newConfig(CFG_QUERY_PLAN_CACHE_SIZE).useMetrics())`），且 `:56-58` 支持热刷新。原建议基于错误前提 | §4.1.1 | — |
| **P2** | `ICache.computeIfAbsent` / `putIfAbsent` 的默认实现违反 `ICache.java:66-69` javadoc 明文要求的 check-then-act 原子性；`JavaxCache.refreshConfig()`（`:105-107`）是空方法体 | §4.1.1 缺陷 2/3 | 低 |
| **P2** | 方言能力矩阵棘轮补 `features` 维度 | §2.7；当前 31 个能力位取值无棘轮守护，靠 24 个分散测试类覆盖 | 低 |
| **P2** | 补 `nop-orm-model` 测试（当前 91 main / 6 test，`OrmEntityModelInitializer` 730 行仅 6 文件） | §4.1 测试不均衡 | 中 |
| **P3** | 变更集/diff API（把已有原料装配成 ChangeSet） | §4.1 缺失项；`entity.xdef:86-89` 预留了 `[[propId,old,new]]` 格式但无实现 | 中 |
| **P3** | ~~二级缓存：补可插拔 cache provider~~ —— 撤销原因：核实发现 provider SPI **已存在且已有分布式实现**（`ICacheProvider` + `LocalCacheProvider` / `HazelcastCache` / `JavaxCache`）。L2 仅剩"开启即强制全属性加载"一项待处理 | §4.1.1 | 中 |
| **P3** | 清理死代码（`EqlASTProcessor` 499 行、`view-entity` 7 类、`OrmManyToManyHelper`、`IOrmRevisionSupport`、`TenantAwareOrmModelProvider`、`TenantCachedQueryPlan`） | §4.1 | 低（但需逐项确认无外部引用） |

**明确不建议做的**：在 ORM 层实现代价模型 / 统计信息 / join 重排 / 索引选择。理由见 §1.4——错误的估计比没有估计更糟，且这与 jOOQ / Hibernate / Diesel / SQLAlchemy 的立场一致。若确实需要，应走"接一个真正的查询引擎（如 Calcite）做联邦/物化视图层"的路线，那是一个独立的设计决策，不属于 ORM 职责。

---

## Open Questions

- [ ] `EqlASTOptimizer` 的命名是否应改为 `EqlASTTransformer` / `EqlRewriterBase` 以消除误导？它是生成器模板产物，改名涉及 `nop-codegen` 模板（Protected Area: plan-first）
- [ ] `dialect-capability-matrix.json` 补 `features` 维度后，是否应把 8 个"声明未接线"标志的取值也纳入棘轮？
- [ ] `supportWithAsClause=false` 时静默降级为内联子查询，与"其余能力位一律编译期硬报错"的纪律不一致——是有意的例外，还是应统一？
- [ ] 若引入 JOIN/投影剪枝，方言 golden 快照（当前 35 条 `contains` 断言）需重生成，且断言强度应从 `contains` 提升到逐字——是否值得单独开一个 work item？
- [ ] `TenantCachedQueryPlan` 死代码路径（`IEntityModel.setTenantModel` 无调用者）——是遗漏了接线点，还是该特性已废弃应删除？

---

## References

### 内部

- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ast/EqlASTOptimizer.java`
- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/{EqlCompiler,EqlTransformVisitor,OracleEqlAstTransformer,CollectionOperatorTransformer}.java`
- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/{AstToSqlGenerator,AstToEqlGenerator,NullsOrderRewriter}.java`
- `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/{IDialect.java,impl/DialectImpl.java}`
- `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml`（19 个）
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/{orm,entity,sql-lib,dialect,orm-interceptor,view-entity}.xdef`
- `ai-dev/audits/eql-wi0/{eql-corpus-baseline.md, dialect-capability-matrix.json}`
- `docs-for-ai/02-core-guides/{model-first-development.md, delta-customization.md, ioc-and-config.md}`

### 外部（已克隆至 `~/sources/orm/`）

- Java/JVM：`java/{jooq,hibernate-orm,mybatis-3,openjpa,eclipselink,jdbi,micronaut-data,ebean,mybatis-plus,dynamic-datasource}`、`jvm/{kotlin-exposed,vertx-sql-client}`
- 优化器对照组：`optimizer/{calcite,dolt}`
- 其他语言：`go/{gorm,ent,bun}`、`rust/{diesel,sea-orm,sqlx}`、`python/{sqlalchemy,django,peewee,tortoise-orm,alembic}`、`ts/{prisma,drizzle-orm,typeorm,kysely,mikro-orm,sequelize,knex,objection}`、`cs/{efcore,linq2db}`、`ruby/{activerecord,sequel}`、`elixir/ecto`、`php/{doctrine-orm,illuminate-database,phalcon}`

### 关键外部锚点

- jOOQ 无优化器：`java/jooq/impl/AbstractContext.java:295`、`java/jooq/impl/DefaultExecuteContext.java:999`
- jOOQ 值语义相等：`java/jooq/QueryPart.java:76-82`
- Hibernate 零优化：`java/hibernate-orm/hibernate-core/src/main/java/org/hibernate/query/sqm/sql/BaseSqmToSqlAstConverter.java:838`
- Hibernate 遥测非成本模型：`java/hibernate-orm/hibernate-core/src/main/java/org/hibernate/stat/internal/StatisticsImpl.java:90-91`
- Hibernate 能力对象：`java/hibernate-orm/hibernate-core/src/main/java/org/hibernate/dialect/sql/ast/spi/SubquerySupport.java:76-104`
- Calcite DPHyp：`optimizer/calcite/core/src/main/java/org/apache/calcite/plan/volcano/DpHyp.java:46`
- Calcite 兜底选择性常数：`optimizer/calcite/core/src/main/java/org/apache/calcite/rel/metadata/RelMdUtil.java:504`
- EF Core SqlTreePruner 倒序删表：`cs/efcore/src/EFCore.Relational/Query/SqlTreePruner.cs:223-227`
- linq2db AND/OR 树平衡：`cs/linq2db/Source/LinqToDB/Internal/Linq/Builder/BinaryExpressionAggregatorVisitor.cs:37-52`
- linq2db ORDER BY 上移正确性论证：`cs/linq2db/Source/LinqToDB/Internal/SqlQuery/Visitors/SqlQueryOrderByOptimizer.cs:271-303`
- linq2db QueryCache：`cs/linq2db/Source/LinqToDB/Internal/Linq/QueryCache.cs:47`
- MikroORM JOIN 剪枝+搬条件配套：`ts/mikro-orm/packages/sql/src/query/QueryBuilder.ts:4448` / `:4512`
- Kysely 插件式优化器：`ts/kysely/src/plugin/deduplicate-joins/deduplicate-joins-transformer.ts:9`、`ts/kysely/src/query-executor/query-executor-base.ts:40-59`
- Diesel TypeId 缓存键与其工程判断：`rust/diesel/src/connection/statement_cache/mod.rs:1-104`
- SQLAlchemy 编译缓存：`python/sqlalchemy/lib/sqlalchemy/sql/elements.py:694-771`
- Django `exists()`/`collapse_group_by`/`split_exclude`：`python/django/django/db/models/sql/query.py:662` / `python/django/django/db/models/sql/compiler.py:202` / `python/django/django/db/models/sql/query.py:2124`
- Ecto 传染式 `:nocache`：`elixir/ecto/lib/ecto/query/planner.ex:1089-1102`
- Dolt 统计生产者 histogram：`optimizer/dolt/go/libraries/doltcore/sqle/statspro/stats_kv.go:450-452`
