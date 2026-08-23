# nop-orm-eql 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-persistence/nop-orm-eql
- 文件数: 221（src/main/java，实测；其中 79 个为生成物：`ast/_gen/` 72 个、`parse/antlr/` 6 个、`parse/_EqlASTBuildVisitor.java` 1 个）
- 覆盖范围声明:
  - 按平台规则未审计生成物（`_` 前缀与 `parse/antlr/`），仅抽查其结构以确认调用契约（如 `_EqlASTBuildVisitor` 对 `SqlCteStatement_recursive(ctx.recursive)`、`SqlLikeExpr_ignoreCase(ctx.ignoreCase)` 的调用方式）。
  - 手写代码 142 个文件全部过目：`compile/`（10 个，EqlTransformVisitor/EqlCompiler/CollectionOperatorTransformer/CollectionScope/CollectionTableSourceHelper/SqlPropJoin/SqlTableScope/ExprTypeResolver/SqlParamTypeResolver/CompiledSql 逐行深读）、`sql/`（4 个逐行深读）、`parse/` 手写部分（5 个）、`eval/`（3 个）、`meta/`（13 个）、`param/`（4 个）、`utils/`（3 个）、`binder/`（2 个）、`enums/`（7 个）、根接口（6 个）全部读完；`ast/` 非 `_gen` 的 46 个类中约 20 个逐行读完（SqlSelect/SqlUnionSelect/SqlColumnName/SqlTableSource/SqlSingleTableSource/SqlFrom/SqlWhere/SqlTableName/SqlQualifiedName/SqlLiteral 系列等），其余为空壳类（仅 extends `_gen` 基类，无逻辑）快速扫描；`EqlASTVisitor.java` 全文核对遍历完备性；`EqlASTOptimizer.java`/`EqlASTProcessor.java`（XGen 模板产物、共约 2260 行）仅结构浏览，未逐行审计。
  - 为验证跨模块契约，参照了 nop-dao（IDialect/DialectImpl/IPaginationHandler/方言 XML）、nop-api-core（FilterBeans）、nop-orm-model（OrmEntityModelInitializer）、nop-orm 测试资源（app.orm.xml）与 EQL 语法（model/antlr/*.g4）。
  - 测试代码不在范围内，但读取了 TestCollectionOperatorTransformer/TestSqlExprToFilterBean 以确认发现点未被现有用例覆盖。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 3 |
| P2 | 7 |
| P3 | 4 |

## 发现列表

### [P1] EQL→FilterBean 桥接中 NOT 条件被静默丢弃

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/eval/SqlExprToFilterBeanTransformer.java:81-86`
- **维度**: D1（正确性）
- **证据**:
```java
private TreeBean transformNot(SqlNotExpr expr) {
    TreeBean not = FilterBeans.or(
            transform(expr.getExpr()));
    not.setLocation(expr.getLocation());
    return not;
}
```
- **现状**: `not (expr)` 被转换为 `FilterBeans.or(...)`（应为 `FilterBeans.not(...)`，同类中 `transformUnary` 第 134-135 行的正确写法可对照）。已核对 nop-api-core 的 `FilterBeans.or(TreeBean...)`：单参数时直接原样返回 `filters[0]`。
- **风险**: 经 `SqlExprTransformHelper.parseSqlToFilter`（模块定位中"EQL 到 GraphQL 的桥接"公开 API）解析 `not (o.status = 3)` 得到的过滤条件等同于 `o.status = 3`，取反语义完全丢失，过滤结果错误且无任何报错。语法 `NOT expr` 生成 SqlNotExpr（BaseRule.g4:135），路径可达；模块内测试 TestSqlExprToFilterBean 未覆盖 NOT。
- **建议**: 改为 `FilterBeans.not(transform(expr.getExpr()))` 并补充 NOT 用例的回归测试。
- **误报排除**: 已确认 `FilterBeans.or` 单参原样返回（FilterBeans.java:326-331），排除"or 单参等价于 not"的可能；已确认 `SqlNotExpr` 由语法 `NOT expr` 产生，非死代码路径。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。`transformNot` 改用 `FilterBeans.not(...)`（与 `transformUnary` 的正确写法一致）。测试：`TestSqlExprToFilterBean#testNotExpr`（红验证：修复前 `not (o.status = 3)` 的过滤器 tagName 为 "eq"，取反语义完全丢失；修复后为 "not" 包裹 eq）、`#testNonLiteralValueThrowsNopException`（配合 [P2] 裸异常条目）。修复后全模块 45 tests, 0 failures, 0 errors。

### [P1] 集合操作符 `_all` 对 BETWEEN/LIKE/IN/IS NULL/NOT 子条件的取反不完整，查询语义反转

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/CollectionOperatorTransformer.java:688-741`（消费点 613-618、515-518）
- **维度**: D1（正确性）
- **证据**:
```java
private SqlExpr negateConditionForAll(SqlExpr expr) {
    if (expr instanceof SqlAndExpr) { ... }
    if (expr instanceof SqlOrExpr) { ... }
    if (expr instanceof SqlBinaryExpr) {
        ...
        SqlOperator negatedOp = negateComparisonOperator(op);
        if (negatedOp != null) {
            binaryExpr.setOperator(negatedOp);
        }
    }
    return expr;   // SqlBetweenExpr/SqlLikeExpr/SqlInValuesExpr/SqlIsNullExpr/SqlNotExpr 原样返回
}
```
- **现状**: `_all` 语义编译为 `NOT EXISTS(子查询 + 取反后的条件)`（`replaceLogicNode` 中 `allCount % 2 == 1` 时取反条件、`shouldNegateExists` 设置 `not exists`）。`negateConditionForAll` 只处理 And/Or/比较运算，其他谓词类型原样返回（未取反）。
- **风险**: 例如 `where o.roles._all.status between 1 and 3` 应编译为 `not exists(... where t1.status not between 1 and 3)`，实际编译为 `not exists(... where t1.status between 1 and 3)`——含义从"所有角色的 status 都在区间内"反转为"没有任何角色在区间内"，静默返回错误结果。同样影响 `_all` + LIKE/IN/IS NULL/NOT。现有测试仅覆盖 `=`/AND/OR 组合，未覆盖这些谓词。
- **建议**: 对未支持的谓词类型抛出 NopException（明确不支持）或补全取反逻辑（between→not between、in→not in、is null→is not null、not X→X）。
- **误报排除**: 已沿 `transform → transformToExistsClauses → transformCollectionScope → replaceLogicNode` 全链路核对取反时机；已确认 `findLogicUnitNode` 对 between 等谓词返回谓词节点本身（其父为 Where），即取反对象就是该谓词；测试文件确认无此类用例。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复（采用审计建议的"补全取反"而非抛错）：SqlBetweenExpr/SqlLikeExpr/SqlInValuesExpr/SqlIsNullExpr 翻转 not 标志；SqlNotExpr 解包（双重取反消除）；比较运算符之外的无法改写条件（裸布尔列、未知运算符等）统一包装为 SqlNotExpr 兜底，杜绝静默丢失取反语义。测试：`TestCollectionOperatorTransformer` 新增 5 个用例（testAllWithBetween/testAllWithIn/testAllWithIsNull/testAllWithLike/testAllWithBooleanColumnCondition），红验证全部复现审计预测的错误输出（如 `not exists(... where t1.status between 1 and 3)` 未取反）。遗留说明（超出本条最小修复范围，见文末新发现）：用户以 `not(...)` 包裹整个 `_all` 逻辑单元时 `shouldNegateExists` 只统计 `_all` 祖先数、不感知显式外层取反，编译极性仍可能错——该缺口对 `=` 同样存在、非本次修复引入。

### [P1] UPDATE/DELETE 中相关子查询引用目标表别名时丢失表前缀，可能绑定到子查询表的同名列

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java:327-329、366-368`（配合 `sql/AstToSqlGenerator.java:306-310`、`ast/SqlColumnName.java:59-63`）
- **维度**: D1（正确性）
- **证据**:
```java
// AstToEqlGenerator.visitSqlUpdate / visitSqlDelete
if (node.getAlias() != null)
    this.ownerShouldBeIgnored = node.getAlias().getAlias();
...
// AstToSqlGenerator.visitSqlColumnName
String owner = node.getResolvedOwner();
if (owner != null && owner.equals(ownerShouldBeIgnored))
    owner = null;      // 相关子查询内的 o.id 同样被去掉前缀
```
- **现状**: UPDATE/DELETE 生成 SQL 时不输出目标表别名，因此用 `ownerShouldBeIgnored` 按别名"字符串"全局丢弃列前缀。该字段在访问语句时设置、从不按作用域恢复，作用于整个生成器后续访问的所有节点，包括 where 子句里相关子查询中的列。
- **风险**: `delete from User o where o.id in (select r.userId from Role r where r.userId = o.id)` 生成 `... where r.userId = ID`（无前缀）。数据库按内层作用域优先解析，若 Role 表存在同名列（ORM 实体普遍有 `id`/ID 列），该条件绑定到 `r.ID`，语义完全改变，可能删改错误行；若不存在同名列则报无法定位列的错误。已核对：transform 阶段子查询 scope 的父链包含 UPDATE/DELETE 的 scope（`visitSqlDelete` 设置 currentScope 后 `visitChild(where)`），别名 `o` 可解析、其 resolvedOwner 即为被忽略的别名；已核对 `visitSqlUpdate/visitSqlDelete` 的 SQL 输出确实不带目标表别名（`getTableAlias` 对父节点非 SqlSingleTableSource 返回 null）。
- **建议**: 仅当列的 tableSource 就是 UPDATE/DELETE 的 resolvedTableSource 且不在子查询作用域内时才丢弃前缀；或对目标表始终输出别名（方言允许时），从根上避免按名字匹配。
- **误报排除**: 已核对生成器每语句新建（EqlCompiler 单语句限制），排除跨语句污染；已确认丢弃逻辑按 `getResolvedOwner()`（别名）而非 tableSource 身份判断，子查询内引用必然命中。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复（生产 SQL 路径 `AstToSqlGenerator`）。新增 `resolveIgnoredOwner`：仅当列的 tableSource 即 UPDATE/DELETE 的 resolvedTableSource 且不在子查询作用域内时丢弃前缀（沿 AST 父链判定）；相关子查询内引用目标表改用物理表名限定（生成 `r.USER_ID = APP_USER.SID`，标准 SQL 外层引用，MySQL/PostgreSQL/Oracle/H2 均支持）；子查询内同名别名遮蔽时（tableSource 不同）保留别名前缀——原实现按别名"字符串"匹配，遮蔽场景同样被误删前缀，本修复一并覆盖。EQL 文本生成器（`AstToEqlGenerator`，调试/回显路径）保持原行为未动（保留 `o.` 前缀会使 EQL 回显文本因别名未声明而不可重解析）。测试：`TestEqlCompileSql#testDeleteCorrelatedSubQueryQualifiesTargetTable`（红验证：修复前生成 `r.USER_ID = SID` 无限定，正是审计预测的同名列误绑场景）、`#testDeleteSubQueryShadowAliasKeepsPrefix`（红验证：修复前遮蔽别名的前缀被误删）、`#testUpdateTopLevelColumnsDropAlias`（顶层 SET/WHERE 丢前缀行为不变的对照用例）。兼容性：仅影响带别名的 UPDATE/DELETE 且 where 含子查询的 EQL——此前这些场景要么静默绑定错误列、要么依赖数据库报错；顶层输出与无子查询语句完全不变。

### [P2] 无 FROM 的子查询会整体重置 querySpace 与 dialect

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java:246-255`
- **维度**: D1（正确性）
- **证据**:
```java
} else {
    if (node.getWhere() != null || ... )
        throw new NopException(ERR_EQL_QUERY_NO_FROM_CLAUSE).source(node);

    String querySpace = findQuerySpaceWhenNoFrom(node);
    this.querySpace = querySpace;                    // 无条件覆盖外层状态
    this.dialect = context.getDialectForQuerySpace(querySpace);
}
```
- **现状**: `visitSqlQuerySelect` 对无 FROM 的 select（如投影中的标量子查询 `select 1`）无条件覆盖 visitor 级的 `querySpace`/`dialect` 字段，且该分支不登记 `querySpaceToEntityNames`，不会触发多 querySpace 校验。外层查询的实体若位于非默认 querySpace，子查询返回后 `dialect`、最终 `transformer.getQuerySpace()`（被 EqlCompiler 用于 `compiledSql.setQuerySpace` 与 SQL 路由）都已变成默认 space。
- **风险**: 非默认数据源场景下（多 querySpace 部署），含无 FROM 子查询的 EQL 会被路由到错误数据源执行（报表错误或表不存在），且后续 order by/limit 的方言改写使用错误方言。默认单库部署无影响。
- **建议**: 仅当 `querySpace == null`（顶层首次确定）时才赋值，或区分顶层语句与嵌套子查询。
- **误报排除**: 已核对 `addResolvedEntity`（实体路径）有 `if (dialect == null)` 保护而此分支没有；已确认嵌套子查询经 `visitSqlQuerySelect` 进入同一分支；已确认 `EqlCompiler` 使用 `transformer.getQuerySpace()` 做最终路由。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。无 from 分支仅在 `this.querySpace == null`（首次确定）时赋值 querySpace/dialect，嵌套无 from 子查询不再重置外层状态；若嵌套子查询通过 decorator 显式指定了不同的 querySpace 则抛 `ERR_EQL_NOT_ALLOW_MULTIPLE_QUERY_SPACE`（与实体路径 addResolvedEntity 的多空间校验语义一致，fail-loud 而非静默错路由）；无 decorator 的嵌套子查询继承外层空间。测试：`TestEqlCompileSql#testQuerySpaceNotResetByNoFromSubQuery`（红验证：修复前 spaceA 实体 + `(select 1)` 被路由回 default）、`#testNoFromSubQueryWithConflictingQuerySpaceThrows`（红验证：修复前不抛错）。遗留镜像边缘（未动，见文末新发现）：`from (select 1) t, Entity@spaceA` 中子查询先于实体被访问时 dialect 保持首次确定的默认值，涉及 addResolvedEntity 的 `if (dialect == null)` 保护逻辑，影响面更小。

### [P2] CTE 的 recursive 标志被解析器硬编码为 true，所有 CTE 都按 `WITH RECURSIVE` 输出

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java:365-368`（消费点 `sql/AstToEqlGenerator.java:409-412`）
- **维度**: D1、D8（与语法契约不符）
- **证据**:
```java
@Override
public boolean SqlCteStatement_recursive(Token token) {
    return true;      // 语法中 recursive=RECURSIVE? 是可选 token，被忽略
}
```
- **现状**: 语法 `sqlCteStatement: recursive=RECURSIVE? name=... AS LP_ statement=sqlSelect RP_`（DMLStatement.g4:70-72）明确 RECURSIVE 可选；生成的 `_EqlASTBuildVisitor` 把该 token 传给此方法，方法无视 token 恒返回 true。SQL 输出端 `visitSqlSelectWithCte` 据此打印 `with recursive`。
- **风险**: 普通（非递归）CTE 在 MySQL/PostgreSQL 上侥幸兼容，但 Oracle 方言（nop-dao 提供 oracle.dialect.xml，未覆盖 supportWithAsClause，继承 default 的 true）不接受 `WITH RECURSIVE` 关键字，EQL CTE 在 Oracle 上必然语法报错；此外 H2 分支 `appendWithColumns` 会因 recursive=true 额外追加分号列段。
- **建议**: 改为 `return token != null;`，并补非递归 CTE 的 SQL 输出测试。
- **误报排除**: 已确认生成代码调用 `SqlCteStatement_recursive(ctx.recursive)` 且 ctx.recursive 缺省时为 null；已确认 `AstToEqlGenerator.visitSqlSelectWithCte` 依据 `stm.getRecursive()` 输出 `recursive` 关键字；模块测试无 CTE 用例。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 复查非问题，审计前提有误。生成物 `_EqlASTBuildVisitor.java:555-556` 对 `setRecursive` 的调用被 `if(ctx.recursive != null)` 保护——未写 RECURSIVE 关键字时 setRecursive 根本不会被调用，而 `_SqlCteStatement.recursive` 字段默认 false，因此非递归 CTE 正常输出 `with name as (...)`、不含 recursive。手写 `EqlASTBuildVisitor.SqlCteStatement_recursive` 的硬编码 `return true` 仅在 token 存在时执行，行为与建议的 `token != null` 完全等价（同文件 `SqlLikeExpr_ignoreCase` 同理：语法中 ILIKE 为必选 token）。「所有 CTE 都按 WITH RECURSIVE 输出」不成立。已补验证测试 `TestEqlParser#testCteRecursiveFlag`（非递归→recursive=false 且输出无 recursive；递归→true 且输出含 recursive），该测试在修复前代码上同样通过，进一步佐证非问题。未做代码改动（最小 diff 原则）。

### [P2] `select *` 搭配显式 JOIN 时抛裸 IllegalStateException

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java:1121-1131、1146-1158`
- **维度**: D1、D4
- **证据**:
```java
List<SqlProjection> getAllProjections(SqlQuerySelect select) {
    ...
    for (SqlTableSource source : from.getTableSources()) {
        items.addAll(getSourceSelectItems(source));   // 不递归展开 join
    }
}
...
} else {
    throw new IllegalStateException("nop.err.invalid-source:" + source);
}
```
- **现状**: `from A a join B b on ...` 按语法解析为单个 `SqlJoinTableSource` 元素（DMLStatement.g4:144-148）。`select *` 展开时对每个 tableSource 调用 `getSourceSelectItems`，该方法只处理 Single/Subquery 两类，遇到 JOIN 源抛 `IllegalStateException`，既未展开也不递归。
- **风险**: 合法 EQL `select * from A a join B b on a.x = b.x` 直接崩溃，且是无位置信息、无错误码的裸异常（违反错误处理两档策略）；逗号分隔的多表 `from A a, B b` 则正常，行为不一致。
- **建议**: 对 JOIN 源递归取 left/right 的投影，或抛带 source/location 的 `NopException` 明确不支持。
- **误报排除**: 已确认 `select *` 时 projections 为空、selectAll=true 走 `getAllProjections`（visitSqlQuerySelect:265-271）；已确认带 owner 的 `o.*` 不受影响（经 scope 取单表源）。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。`getSourceSelectItems` 对 SqlJoinTableSource 递归收集左右表投影（与逗号多表行为对齐）；剩余 else 防御分支由裸 `IllegalStateException("nop.err.invalid-source:...")` 改为 `NopException(ERR_EQL_ONLY_SUPPORT_SINGLE_TABLE_SOURCE).source(source).param(ARG_TABLE_SOURCE, ...)`。测试：`TestEqlCompileSql#testSelectAllWithExplicitJoin`（红验证：修复前抛审计引用的裸异常文本 `IllegalState nop.err.invalid-source:SqlJoinTableSource`；修复后正常展开 4 列，dataSetMeta 字段数=4）。兼容性：合法 EQL 由崩溃变为正常编译，无既有行为回退。

### [P2] UPDATE RETURNING 投影为非列表达式时在 SQL 生成阶段 NPE

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java:134-156`（根因配合 `compile/EqlTransformVisitor.java:1456-1469`）
- **维度**: D1、D4
- **证据**:
```java
// AstToSqlGenerator.visitSqlExprProjection
ISqlExprMeta exprMeta = expr.getResolvedExprMeta();   // 可能为 null
...
List<String> colNames = exprMeta.getColumnNames();     // NPE
```
- **现状**: `resolveExprMeta` 只在 SELECT 语句的 `resolveSelectFields` 与参数类型推断中调用（已 grep 全模块确认仅 EqlTransformVisitor:582 与 SqlParamTypeResolver:81 两处）。`visitSqlUpdate` 对 `returnProjections` 仅做别名分配和子节点访问，函数等非列表达式的 `resolvedExprMeta` 保持 null；SQL 生成在 EqlCompiler 的 `Guard.notNull(exprMeta, "fieldMeta")`（仅 SELECT 分支、且在生成之后）之前执行。
- **风险**: 在支持 RETURNING 的方言（语法 DMLStatement.g4:32 允许任意 `sqlProjections_`）上执行 `update User o set ... returning upper(o.name)` 抛出无诊断信息的 NPE，而非明确报错。
- **建议**: 在 `visitSqlUpdate` 中对 returnProjections 调用 `ExprTypeResolver`（与 `resolveSelectFields` 一致），或在生成器中对 null meta 抛带位置的 NopException。
- **误报排除**: 已确认 `resolveSelectFields` 仅由 SELECT/CTE/子查询表源路径调用；已确认 EqlCompiler 的 Guard 位于 `genSql.visit(stm)` 之后且不覆盖 UPDATE 分支。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复（采用审计建议的 transform 侧方案）：`visitSqlUpdate` 对 returnProjections 与 SELECT 语句的 `resolveSelectFields` 一致地经 `ExprTypeResolver.resolveExprMeta` 推导表达式类型（列投影 meta 已解析时为 no-op，函数等非列表达式补建 meta，SQL 生成走 colNames==null 的表达式打印分支）。测试：`TestEqlCompileSql#testUpdateReturningNonColumnExpr`（`update ... returning upper(o.name)`，红验证：修复前 NPE `Cannot invoke ...ISqlExprMeta.getColumnNames() because "exprMeta" is null`——正是审计预测的崩溃点；修复后正常输出 returning 子句）。

### [P2] 复合外键含常量关联条件时 buildValue 装配错主键槽位

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/meta/EntityRefPropExprMeta.java:55-71`
- **维度**: D1（正确性）
- **证据**:
```java
Object[] pk = new Object[propModel.getJoin().size()];
int idx = 0;
for (IEntityJoinConditionModel join : propModel.getJoin()) {
    if (join.getLeftPropModel() != null) {
        pk[idx] = row[fromIndex + idx];
        idx++;
    } else {
        pk[idx] = join.getLeftValue();   // 未 idx++，且槽位未按 join 列表位置对齐
    }
}
```
- **现状**: `pk` 数组按 join 条件列表整体长度分配，但 leftValue（常量）分支不推进 `idx`，且 leftProp 分支的槽位使用"已见列条件计数"而非该 join 在列表中的位置。一旦常量条件位于两个列条件之间（如 `on leftProp=a / on leftValue='X' / on leftProp=b`），`pk[1]` 先被赋 'X' 再被 b 覆盖，`pk[2]` 恒为 null。
- **风险**: 该形状的 to-one 关联（测试模型 SimsCollege.testRef 即为 leftProp+leftValue 混合形态）作为投影查询时构造出错误关联 id，`internalLoad` 加载错误实体或失败。列名收集端（EntityTableMeta.makePropMeta）只收集列条件、顺序与 row 布局一致，进一步印证槽位计算应使用 join 列表位置。
- **建议**: 以 join 列表下标为槽位：`pk[i] = leftPropModel != null ? row[fromIndex + colIdx++] : leftValue`。
- **误报排除**: 已核对 `makePropMeta` 的 colNames 构造顺序（仅 leftProp&&isColumn&&right!=null 的条件按序入列）与 buildValue 的 row 索引语义，确认只有槽位错位、无行错位；`colBinders.size()==1` 短路分支不受影响。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复，按审计建议公式：`pk[i] = leftPropModel != null ? row[fromIndex + colIdx++] : leftValue`（pk 槽位按 join 列表位置对齐，列值按列条件出现顺序取自 row）。测试：`TestEntityMetaBuildValue#testRefPropBuildValueWithConstantJoinCondition`（leftProp=a / leftValue='X' / leftProp=b 混合形态，fake IEqlQueryContext 记录 castId 收到的 pk；红验证：修复前 pk=[aValue,bValue,null]——'X' 被 b 覆盖、槽位 2 恒 null，与审计推演一致；修复后 [aValue,X,bValue]）。`colBinders.size()==1` 短路分支不受影响。

### [P2] EntityTableMeta 组件分支条件与注释相反且必然 NPE；kv 表 DATETIME 回退映射到 decimal 列

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/meta/EntityTableMeta.java:73-78、219-224`；`meta/ComponentExprMeta.java:86-94`
- **维度**: D1（正确性，含死代码路径）
- **证据**:
```java
// 注释：无主键的实体不支持查询component，因为component需要知道owner对象...
if (entityModel.isNoPrimaryKey()) {                       // 与注释相反：无主键才注册
    for (IEntityComponentModel propModel : entityModel.getComponents()) {
        propExprMetas.put(propModel.getName(), makeComponentMeta(dialect, idExprMeta, propModel, colBinders));
    }
}
...
} else if (timestampValue != null) {
    valueExprMetas.put(StdDataType.DATETIME.getName(),
            makeValueType(decimalValue, dialect, StdSqlType.DECIMAL, StdDataType.TIMESTAMP));  // 应传 timestampValue
}
```
- **现状**: (1) `isNoPrimaryKey()==true` 恒等价于 `idExprMeta==null`（OrmEntityModelInitializer.initIdProp 保证），因此该分支构造的每个 `ComponentExprMeta.idExprMeta` 均为 null，而 `buildValue` 首行 `idExprMeta.buildValue(...)` 必 NPE；同时有主键实体（仓库中唯一组件用例 SimsExam）反而注册不上组件元数据，条件与注释语义相反。(2) kv 表 DATETIME 回退分支以 `decimalValue` 作为 baseMeta，取 decimal 列作 DATETIME 值列（列名来自 baseMeta），且 `decimalValue` 为 null 时 `makeValueType` 内 NPE；真实 kv 实体 NopBatchTaskVar（有 timestampValue、无 dateTimeValue）即命中此分支。
- **风险**: (1) 为仓库内不可达路径（无 noPrimaryKey 实体），但一旦出现即运行期 NPE，且正常组件实体无法通过 EQL 选取组件；(2) 当前仓库无 `getValueExprMeta` 调用方（已全仓 grep），属潜在缺陷；调用后 kv 表按 DATETIME 取值会读到 DECIMAL_VALUE 列。
- **建议**: 组件分支条件改为 `!isNoPrimaryKey()`（与注释一致）；DATETIME 回退改传 `timestampValue` 并用 TIMESTAMP 类型；补一条 kv 表取值测试。
- **误报排除**: 已核对 initIdProp 确认 noPrimaryKey⇔idProp==null；已核对 makeValueType 的列名来源；已确认两路径当前均无仓库内调用方（如实降级为 P2 而非 P1）。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，两处均已修复。(1) 组件注册条件改为 `!entityModel.isNoPrimaryKey()`（与注释语义一致：有主键实体可查询 component；无主键实体不再构造 idExprMeta=null、buildValue 必然 NPE 的 ComponentExprMeta；`select *` 展开按 isColumn 过滤不受影响）。(2) kv 表 DATETIME 回退改传 `timestampValue` + `StdSqlType.TIMESTAMP`。测试：`TestEntityMetaBuildValue#testComponentRegisteredForEntityWithPrimaryKey`（红验证：修复前有主键实体 `getFieldExprMeta("ext")` 返回 null）、`#testKvTableDatetimeFallsBackToTimestampColumn`（无 dateTimeValue/decimalValue、有 timestampValue 的 kv 实体；红验证：修复前 `makeValueType` 内 NPE——baseMeta 即 decimalValue 为 null，正是审计预测的崩溃点；修复后 DATETIME 取值映射到 TIMESTAMP_VALUE 列）。

### [P2] 多处裸 JDK 异常，违反平台错误处理策略（部分可由用户输入触达）

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/eval/SqlExprToFilterBeanTransformer.java:64、97、113、117、137、175`；`eval/SqlExprToExpressionTransformer.java:177、190`；`compile/EqlTransformVisitor.java:1156`（已计入前条）；`parse/EqlParseHelper.java:39、48`；`compile/CollectionScope.java:74、113`；`param/EntityPropParamBuilder.java:85`
- **维度**: D4、D7
- **证据**:
```java
// SqlExprToFilterBeanTransformer
throw new IllegalArgumentException("expr-not-literal:" + expr);
...
// SqlExprToExpressionTransformer:190，注意行尾全角分号
throw new IllegalArgumentException("invalid hex value；" + expr.getValue());
...
// EntityPropParamBuilder
throw new UnsupportedOperationException();
```
- **现状**: 平台约定禁止 bare RuntimeException（IllegalArgumentException/IllegalStateException/UnsupportedOperationException 均为其子类），公共 API 应使用 NopException+ErrorCode+.param(...)。`parseSqlToFilter` 是公开 API，用户传入 `a = b + 1`（右操作数非字面量）即触达 `getValue` 的裸 IAE，无位置信息；hex 错误消息还含全角"；"。
- **风险**: 错误信息不可定位、无法国际化、无法按错误码分类；公开 API 上以裸异常暴露给调用方。
- **建议**: 公开 API 路径统一改用 `OrmEqlErrors` 中定义的 ErrorCode（已有 `ERR_EQL_UNSUPPORTED_EVAL_EXPR` 可复用）；内部防御分支至少补 `.loc()` 与英文规范化消息。
- **误报排除**: 逐处阅读上下文确认真实存在；EqlParseHelper/CollectionScope 的两处经语法核对为不可达防御分支（qualify 名不能以 `.` 开头），如实降级说明。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，全部站点已清理。公开 API 路径：`SqlExprToFilterBeanTransformer` 6 处与 `SqlExprToExpressionTransformer` 2 处（含全角"；"文案一并清理）统一改 `NopException(ERR_EQL_UNSUPPORTED_EVAL_EXPR).param(ARG_EXPR, expr)`（复用既有错误码，与 transform() 兜底一致）；`EntityPropParamBuilder` 的裸 `UnsupportedOperationException` 改 NopException。内部防御分支：`EqlParseHelper` 2 处改 `NopException` + `.loc(loc(token))`（新增 `ERR_EQL_INVALID_BIT_LITERAL`/`ERR_EQL_INVALID_HEX_LITERAL`，顺带修正 bitLiteralValue 中误写为 hex 的文案）；`CollectionScope` 2 处改 `NopException` + `.source(colNameNode)`（新增 `ERR_EQL_INVALID_COLLECTION_OPERATOR_PATH`）。`OrmEqlErrors` 为手写接口（非生成物）可安全扩展；本模块无 i18n 聚合资源文件需同步。`EqlTransformVisitor` 的 1 处已随 [P2] select*+JOIN 条目一并处置。测试：`TestSqlExprToFilterBean#testNonLiteralValueThrowsNopException`（红验证：修复前公开 API `a = b + 1` 抛裸 IllegalArgumentException；修复后为带错误码 NopException）。

### [P3] ERR_EQL_FUNC_TOO_MANY_ARGS 错误参数误传 minArgCount

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java:1370-1373`
- **维度**: D4
- **证据**:
```java
if (fn.getMaxArgCount() < node.getArgs().size()) {
    throw new NopException(ERR_EQL_FUNC_TOO_MANY_ARGS).source(node).param(ARG_FUNC_NAME, node.getName())
            .param(ARG_ARG_COUNT, argCount).param(ARG_MAX_ARG_COUNT, fn.getMinArgCount());
}
```
- **现状**: `ARG_MAX_ARG_COUNT` 传的是 `getMinArgCount()`，复制粘贴错误。
- **风险**: 函数参数过多时错误提示中的上限值错误，误导排障。
- **建议**: 改为 `fn.getMaxArgCount()`。
- **误报排除**: 与上方 TOO_FEW_ARGS 分支（1365-1367）对照确认。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复为 `.param(ARG_MAX_ARG_COUNT, fn.getMaxArgCount())`。No new test required: 仅异常诊断参数修正，不改变任何控制流与生成 SQL。

### [P3] SingleColumnExprMeta 构造器重复校验 binder，dataType 未校验

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/meta/SingleColumnExprMeta.java:28-31`
- **维度**: D1
- **证据**:
```java
public SingleColumnExprMeta(String columnName, IDataParameterBinder binder, IOrmDataType dataType) {
    Guard.notNull(binder, "binder");
    Guard.notNull(binder, "dataType");   // 应为 Guard.notNull(dataType, "dataType")
```
- **现状**: 第二个 Guard 仍检查 binder，dataType 为 null 不会被拦截。
- **风险**: dataType 为 null 时错误延后到 `getOrmDataType()` 调用方以 NPE 暴露；当前仓库内调用点均传非 null，实际影响低。
- **建议**: 修正为 `Guard.notNull(dataType, "dataType")`。
- **误报排除**: 直接文本核对。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复为 `Guard.notNull(dataType, "dataType")`。No new test required: 构造期防御校验的参数名修正；当前仓库内调用点均传非 null，唯一语义变化是未来误传 null 时抛出正确命名的校验异常而非延后 NPE。

### [P3] SqlTableSource.hasPropJoins 逻辑写反（当前无调用方）

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ast/SqlTableSource.java:33-36`
- **维度**: D1
- **证据**:
```java
public boolean hasPropJoins() {
    return propJoins != null && propJoins.isEmpty();   // 语义相反
}
```
- **现状**: 仅在 propJoins 非null且为空时返回 true，与命名语义相反。全仓 grep 无调用方。
- **风险**: 死代码；一旦被使用即产生反向判断。
- **建议**: 改为 `propJoins != null && !propJoins.isEmpty()` 或删除。
- **误报排除**: 全仓 grep 确认无调用方，如实标注为死代码。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复为 `propJoins != null && !propJoins.isEmpty()`（保留方法未删除：public API，删除属破坏性变更）。No new test required: 全仓无调用方的死代码逻辑修正，无行为语义变化。

### [P3] SelectResultTableMeta.getFieldExprMeta 忽略 allowUnderscoreName 参数

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/meta/SelectResultTableMeta.java:31-33`
- **维度**: D8（契约一致性）
- **证据**:
```java
public ISqlExprMeta getFieldExprMeta(String name, boolean allowUnderscoreName) {
    return fieldExprMetas.get(name);
}
```
- **现状**: 接口参数 `allowUnderscoreName` 被忽略。外层实体列允许下划线名访问（`EntityTableMeta.getFieldExprMeta` 支持），但子查询结果字段不支持，行为不一致。
- **风险**: `allowUnderscoreName=true` 时对子查询别名使用下划线名引用会得到"字段不存在"，与实体路径行为漂移；影响面小（子查询别名通常 camelCase）。
- **建议**: 文档化该限制，或按投影 fieldName 的下划线形式建立辅助索引。
- **误报排除**: 已核对 `resolveColName` 对子查询源同样传入 `context.isAllowUnderscoreName()`。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复（审计建议的辅助索引方案）：构造时按 `camelCaseToUnderscore(fieldName, true)` 建下划线辅助索引（putIfAbsent 防重名冲突），直接命中优先、miss 且 allowUnderscoreName 时回退下划线查找，与实体字段的 `getPropByUnderscoreName` 访问语义保持一致。测试：`TestEqlCompileSql#testSubQueryFieldByUnderscoreName`（allowUnderscoreName=true 时 `t.user_id` 访问子查询别名 userId；红验证：修复前抛 `ERR_EQL_FIELD_NOT_IN_SUBQUERY: user_id`；修复后正常解析）。

## 超出审计的新发现（fix-ai-check 分支，2026-08-22 处置时发现）

1. **[已修复] NOT-ILIKE 的 not 标志在 SQL/EQL 生成时丢失**：语法 `expr=sqlExpr_bit not=NOT? ignoreCase=ILIKE value=...`（BaseRule.g4:157）解析器正确设置 SqlLikeExpr.not，但 `AstToEqlGenerator.visitSqlLikeExpr` 与 `AstToSqlGenerator.visitSqlLikeExpr` 均不输出 not，`o.name not ilike 'a%'` 生成 `o.name ilike 'a%'`，语义静默反转（与 [P1] transformNot 同族缺陷）。已修复：基础生成器非 ignoreCase 路径输出 `not` 前缀；AstToSqlGenerator 的 ilike 方言函数路径用 `not (...)` 包裹、LIKE 回退路径输出 `not` 前缀。测试：`TestEqlParser#testNotILike`（红验证：修复前输出不含 not）。
2. **[暂缓] `_all` 逻辑单元被显式 not(...) 包裹时编译极性错**：`shouldNegateExists`/`replaceLogicNode` 只按 `_all` 祖先计数决定 not-exists 极性，不感知包裹整个逻辑单元的显式 `not`，`not (o.roles._all.status = 1)` 编译结果与 `∃(status≠1)` 语义不符（空集合等场景出错）。该缺口对 `=` 同样存在（非 negateConditionForAll 补全引入），涉及 exists 极性与条件取反的组合语义设计，与本条审计的谓词取反属不同层面，最小修复未展开，建议下轮审计立项处理。
3. **[暂缓] 无 FROM 子查询先于实体访问时的镜像方言错配**：`from (select 1) t, Entity@spaceA` 中子查询表源先被访问且 this.querySpace 尚为 null 时，先确定 default 方言；随后实体将 querySpace 覆写为 spaceA，但 `addResolvedEntity` 的 `if (dialect == null)` 保护使 dialect 仍为 default。与本报告 [P2] 无 FROM 子查询条目互为镜像，影响面更小（需逗号多表且首表为无 from 子查询且实体在非默认空间），未随本次最小修复动 addResolvedEntity。
4. **[裁定不修复] EQL 文本生成器的 UPDATE/DELETE 前缀丢弃**：`AstToEqlGenerator.visitSqlColumnName`（调试/回显路径）仍按 ownerShouldBeIgnored 按名丢前缀。不改的原因：UPDATE/DELETE 的 EQL 文本不输出目标表别名，保留 `o.` 前缀会使回显文本因别名未声明而不可重解析；且该路径仅用于 dump/调试，不参与执行。生产 SQL 路径已在本报告 [P1] UPDATE/DELETE 条目中修复。

## 未发现问题的维度说明（核查证据）

- **D2 资源管理**: 模块为纯编译期变换（无流/连接/文件 I/O），未发现资源泄漏点。
- **D3 并发与线程安全**: `SqlExprMetaCache.entityMetas` 为 ConcurrentHashMap，get+computeIfAbsent 模式安全；`SingleColumnExprMeta.s_meta` 为 HashMap 但仅在静态初始化块写入（类初始化语义保证安全发布），之后只读；解析器/visitor/生成器均为每编译周期新实例（EqlCompileContext.getAliasGenerator 每次 new SeqAliasGenerator，唯一调用方 EqlTransformVisitor 仅取一次）；本模块无 beans.xml、无 @Inject，D7 IoC 规则不适用。
- **D5 SQL 注入**: 字符串字面量经 `dialect.getStringLiteral`→`StringHelper.escapeSql`（单引号加倍，反斜杠按方言）；非生成别名列别名经 `dialect.escapeSQLName`，生成别名（t1/c2）字符集受控；分页经 `IPaginationHandler.buildPageExpr` 以 AST/ISqlExpr 包装拼接，limit/offset 为参数标记或字面量节点而非字符串拼接；参数统一经 `SqlParameterMarker`→`markValue("?")` 绑定；`@sqlText` 列文本仅来自 ORM 模型元数据（可信输入）。未发现用户输入直达 SQL 文本的注入点。
- **D6 性能**: EqlCompiler 每次编译重新解析 EQL 文本（本模块无解析缓存，缓存属上层职责）；`replaceAllProjection` 对多 `*` 选择列表存在 O(n·k) 重复访问、CollectionOperatorTransformer.mergeConditions 为 O(n²)，n 均为选择项/集合条件数，量级小，不构成实际风险。
