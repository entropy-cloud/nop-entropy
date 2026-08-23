# nop-orm-eql 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-persistence/nop-orm-eql
- 文件数: 221（src/main/java；其中生成文件约 97 个：`ast/_gen/` 90 个、`parse/antlr/` 6 个、`parse/_EqlASTBuildVisitor.java` 1 个；另 `ast/EqlASTOptimizer.java`、`ast/EqlASTVisitor.java`、`ast/EqlASTProcessor.java`、`parse/EqlASTParser.java`、`parse/EqlParseTreeParser.java` 带 `//__XGEN_FORCE_OVERRIDE__` 生成标记，按生成代码对待，不在审计范围）
- 覆盖范围声明: 深读约 46 个手写文件，关键类包括 `sql/AstToSqlGenerator`、`sql/AstToEqlGenerator`、`compile/EqlTransformVisitor`、`compile/EqlCompiler`、`compile/CompiledSql`、`compile/CollectionOperatorTransformer`、`compile/CollectionScope`、`compile/CollectionTableSourceHelper`、`compile/SqlPropJoin`、`compile/SqlTableScope`、`compile/ExprTypeResolver`、`compile/SqlParamTypeResolver`、`parse/EqlASTBuildVisitor`、`parse/EqlParseHelper`、`meta/EntityTableMeta`、`meta/SelectResultTableMeta`、`meta/SqlExprMetaCache`、`eval/SqlExprToExpressionTransformer`、`eval/SqlExprToFilterBeanTransformer`、`utils/EqlHelper`、`utils/EqlASTBuilder`、`param/*`、`binder/OrmBinderHelper`，以及全部含逻辑的手写 AST 类（SqlSelect/SqlUnionSelect/SqlColumnName/SqlTableSource/SqlFrom/SqlWhere/SqlBinaryExpr/SqlAndExpr/SqlOrExpr/SqlNotExpr/SqlQualifiedName/SqlParameterMarker 等）与 `enums/SqlOperator`、`enums/SqlCollectionOperator`；模式扫描（catch/异常吞噬、@Inject private、SimpleDateFormat/ThreadLocal、bare RuntimeException、clone/copyExtFields）覆盖全部手写文件（约 90% 逐文件过目，其余为空壳 AST 子类与纯签名接口）。未覆盖区域: 上述生成文件、`OrmEqlErrors` 常量主体、纯枚举小类（SqlCompareRange/SqlDateTimeType/SqlIntervalUnit/SqlJoinType/SqlUnionType）。交叉验证使用了 `nop-persistence/nop-dao`（dialect/分页）、`nop-persistence/nop-orm-model`（关系元数据）、`nop-kernel/nop-core`（ASTNode）、`nop-persistence/nop-orm` 测试与 `model/antlr` 文法；P0 与两条 P2 通过 jshell + 模块编译产物实测复现。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 0 |
| P2 | 5 |
| P3 | 5 |

## 发现列表

### [P0] 集合属性表源转换在外层查询生成多余 join（行数放大 / 非法 SQL），`_some`/`_all` 集合操作符同样受影响

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java:920-938`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/CollectionTableSourceHelper.java:103-122`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java:222-244`
- **维度**: D1（另涉 D8：与 `from owner.prop alias` 语义的契约不符）
- **证据**:
```java
// EqlTransformVisitor.addToManyCollectionJoin (920-938)
SqlSingleTableSource refTable = makeTableSource(source.getLocation(), ref.getRefEntityModel(), userAlias);
SqlExpr joinCondition = makeCondition(ref.getJoin(), source, refTable);
refTable.setForPropJoin(true);
SqlPropJoin join = new SqlPropJoin();
join.setLeft(source);
join.setRight(refTable);
join.setJoinType(SqlJoinType.LEFT_JOIN);
join.setExplicit(false);          // <-- 非 explicit
join.setCondition(joinCondition);
addTableFilterForPropJoin(join);
source.addPropJoin(propJoinName, join);   // <-- 注册到 owner（外层）表源上
```
```java
// CollectionTableSourceHelper.transformCollectionTableSource (103-122)
SqlPropJoin join = buildRelationJoin(visitor, ownerTable, propName, table.getAlias());
...
SqlExpr condition = join.getCondition();
if (condition != null) {
    SqlWhere where = currentSelect.makeWhere();
    where.appendFilter(condition);          // join 条件被追加到子查询 where
}
EqlASTNode parent = table.getASTParent();
if (parent != null) {
    parent.replaceChild(table, right);      // 子表又被替换进 from 列表
}
```
```java
// AstToSqlGenerator.appendPropJoins (222-244)
for (SqlPropJoin propJoin : node.getPropJoins().values()) {
    if (propJoin.isExplicit())
        continue;                            // 只跳过 explicit，集合 join 为 false
    ...
    sb.append(propJoin.getJoinType().getText());
    visitSqlTableName(propJoin.getRight().getTableName());
    sb.append("on ");
    visit(propJoin.getCondition());
```
- **现状**: `CollectionTableSourceHelper` 把 `from o.roles r` 这类集合属性表源转换为真实子表时：(a) 将 join 条件追加到当前 select 的 where；(b) 将子表 `right` 替换进当前 select 的 from；(c) 同时把该 join 以 `explicit=false` 注册为 owner 表（通常位于外层查询）的 propJoin。而 SQL 生成阶段 `appendPropJoins` 对所有非 explicit propJoin 都会在 owner 所在查询再输出一次 join。三者叠加导致子表被join 两次。jshell 实测（最小实体模型 + EqlCompiler）：
  - 子查询用法 `select o.name from AppUser o where exists (select 1 from o.roles r where r.userId='x')` 生成
    `select o.NAME as c1 from APP_USER o left join APP_ROLE r on o.SID = r.USER_ID where exists ( select 1 as c2 from APP_ROLE r where (r.USER_ID = 'x') and (o.SID = r.USER_ID) )` —— 外层多出 `left join APP_ROLE r`；
  - 集合操作符 `where o.roles._some.userId = 'x'` 生成
    `... from APP_USER o left join APP_ROLE t1 on o.SID = t1.USER_ID where exists ( select 1 as c2 from APP_ROLE t1 where ... )` —— 同样多出外层 join；
  - 同层级用法 `select o.name from AppUser o, o.roles r where r.userId='x'` 生成
    `from APP_USER o left join APP_ROLE r on o.SID = r.USER_ID, APP_ROLE r` —— 同一别名 `r` 输出两次，是多数数据库直接拒绝的非法 SQL。
- **风险**: 外层多余 `left join` 使每个 owner 行按其集合成员数重复放大：`select` 返回重复行、`count(*)` 计数膨胀，属于数据错误；`o.coll._some / o.coll._all` 是 EQL 的正式特性，触发路径现实存在。现有测试未暴露是因为 `AbstractOrmTestCase` 中每个 College 恰好只有 1 个 Class（1:1 数据下 left join 不改变行数），`nop-orm` 侧 `TestEqlQuery#testExistsWithCollectionTableSource`、`TestCollectionOperator` 均在 1:1 数据下通过。同层级写法则直接产生非法 SQL，执行期报数据库语法/别名错误。
- **建议**: 集合表源场景生成的 SqlPropJoin 不应作为 owner 表的非 explicit propJoin 参与外层 SQL 输出：要么标记 `explicit=true`（并保持 join 条件只在子查询 where 中），要么不调用 `source.addPropJoin` 而仅将条件写入子查询 where（to-many 已把条件追加到 where，propJoin 仅为让 scope 解析别名，可改为仅在局部 scope 注册）。同时为“子查询用法、同层级用法、多子行数据”补集成测试（当前 1:1 数据掩盖了行数放大）。
- **误报排除**: 已通读 `visitTableSource`（EqlTransformVisitor.java:458-509）确认 helper 对主查询与子查询内的表源都会触发；通读 `AstToSqlGenerator.visitSqlSingleTableSource/appendPropJoins` 与 `SqlTableSource.addPropJoin/getPropJoins` 确认除了 `isExplicit()` 外无任何去重/摘除机制（全仓 grep `setExplicit(true)` 仅出现在显式 join 路径 `visitJoinRight`）；用模块编译产物实测三种写法的生成 SQL（见上），并运行 `TestEqlQuery#testExistsWithCollectionTableSource`（通过，1:1 数据）确认测试掩盖路径，非误报。

### [P2] `update ... returning *` 绕过方言 returning 支持检查

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java:1474-1486`
- **维度**: D1 / D4
- **证据**:
```java
if (node.getReturnProjections() == null || node.getReturnProjections().isEmpty()) {
    // select *
    if (node.getReturnAll()) {
        List<SqlProjection> items = buildSelectItems(source, true, false);
        node.setReturnProjections(items);
    }// ELSE 没有Returning语句
} else {
    if (!dialect.isSupportReturningForUpdate()) {
        throw new NopException(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE)
                .param(ARG_DIALECT, dialect.getName())
                .param(ARG_FEATURE, FEATURE_SUPPORT_RETURNING_FOR_UPDATE);
    }
}
```
- **现状**: 方言能力校验只放在 `returnProjections` 非空的分支；`returning *`（returnAll）路径展开为投影列表后不校验。jshell 实测：`supportReturningForUpdate=false` 的方言下，`update AppUser o set o.name='x' where o.id='1' returning *` 编译通过并生成 `... returning SID, NAME`；而 `returning o.name` 则正确抛出 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`。
- **风险**: 在不支持 returning 的方言（如 MySQL 系）上生成必然执行失败的 SQL，把本应在编译期报告的错误推迟到运行期数据库报错，错误信息与平台错误码体系脱节。
- **建议**: 将 `isSupportReturningForUpdate()` 校验移到 returnAll 与 returnProjections 两个分支之前统一判断。
- **误报排除**: 通读 `visitSqlUpdate` 全方法及 `AstToEqlGenerator.visitSqlUpdate` 的 returning 输出逻辑（AstToEqlGenerator.java:341-346，`returnAll` 也会输出 `returning`）；并用 `DialectFeatures.setSupportReturningForUpdate(false)` 的最小方言实测两种写法的差异，非误报。

### [P2] 十六进制/位字面量在表达式求值转换中必然抛“不支持的表达式类型”（前缀双重剥离）

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/eval/SqlExprToExpressionTransformer.java:170-199`；对照 `parse/EqlParseHelper.java:36-56`
- **维度**: D1
- **证据**:
```java
// EqlParseHelper（解析期已剥离前缀，AST 中保存的是裸数字）
public static String bitLiteralValue(Token token) {
    String text = token.getText();
    if (text.startsWith("0b")) return text.substring(2);
    if (text.startsWith("B\'") || text.startsWith("b\'")) return text.substring(2, text.length() - 1);
    throw ...
}
// SqlExprToExpressionTransformer（又按“带前缀”解析，分支永不命中）
private Expression transformBitValue(SqlBitValueLiteral expr) {
    String str = expr.getValue();          // 已经是 "1010" 这样的裸串
    if (str.startsWith("0b")) { str = str.substring(2); }
    else if (str.startsWith("B")) { str = str.substring(1); }
    else { throw new NopException(ERR_EQL_UNSUPPORTED_EVAL_EXPR).param(ARG_EXPR, expr); }
```
`transformHexLiteral` 同理（期望 `0x...`/`X...`，实际值已是剥离后的裸十六进制串）。
- **现状**: 解析器 `EqlParseHelper.bitLiteralValue/hexLiteralValue` 已把 `0x`/`X'...'`/`b'...'` 前缀剥掉存入 AST；求值转换器又要求值自带前缀，两个分支都匹配不上，直接落入 throw。jshell 实测：`SqlExprTransformHelper.parseSqlToExpression(null, "0x4D2 = 1")` 与 `b'1010' = 1` 均抛 `ERR_EQL_UNSUPPORTED_EVAL_EXPR`。
- **风险**: 含十六进制/位字面量的 SQL 表达式无法转换为 XLang 表达式（`parseSqlToExpression` 调用方），该字面量支持形同虚设；虽然报错是显式的，但错误码语义（“不支持的表达式类型”）误导排障方向。
- **建议**: 删除转换器中的二次前缀剥离逻辑，直接 `Integer.parseInt(str, 2)` / `Integer.parseInt(str, 16)`（注意超长串应使用 Long/BigInteger 解析，避免 `transformBitValue` 中 `parseInt` 对超 31 位二进制抛 NumberFormatException）。
- **误报排除**: 通读 `EqlASTBuildVisitor.SqlBitValueLiteral_value/SqlHexadecimalLiteral_value`（委托 EqlParseHelper）确认 AST 值无前缀；实测两种字面量均抛错，非误报。

### [P2] 方言不支持 ILIKE 时静默降级为大小写敏感 LIKE

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java:457-479`
- **维度**: D1 / D4
- **证据**:
```java
ISQLFunction func = dialect.getFunction(SqlOperator.ILIKE.getText());
if (func != null) {
    ...
} else {
    LOG.debug("nop.orm.dialect-not-support-ilike,so-use-like-instead:{}", node);
    if (node.getNot())
        print(" not ");
    printBinaryExpr(node.getExpr(), SqlOperator.LIKE, node.getValue());
}
```
- **现状**: 文法中 `ILIKE` 独立成产生式（BaseRule.g4:157），`ignoreCase=true` 语义明确；当方言未注册 `ilike` 函数时，直接按普通 `LIKE` 输出，仅打 debug 日志。
- **风险**: 用户显式写 `ilike` 请求忽略大小写，在不支持该函数的方言上被静默改为大小写敏感匹配，查询结果集与语义不符且几乎无日志可查（debug 级别默认不可见），属静默语义降级。
- **建议**: 至少提升为 warn 日志并带上 location；更严格的做法是编译期抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`，或改写为 `lower(a) like lower(b)` 保持语义。
- **误报排除**: 通读 `IDialect.getFunction`/各 dialect.xml 函数注册表确认多数方言（default/mysql 系）未注册 `ilike`；通读 `AstToEqlGenerator.visitSqlLikeExpr`（ignoreCase 时用 ILIKE 操作符输出）确认降级路径可达，非误报。

### [P2] `_all` 取反改写用比较符翻转实现，三值逻辑下 NULL 语义漂移

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/CollectionOperatorTransformer.java:747-802`
- **维度**: D1
- **证据**:
```java
if (expr instanceof SqlBinaryExpr) {
    SqlBinaryExpr binaryExpr = (SqlBinaryExpr) expr;
    SqlOperator op = binaryExpr.getOperator();
    ...
    SqlOperator negatedOp = negateComparisonOperator(op);
    if (negatedOp != null) {
        binaryExpr.setOperator(negatedOp);   // EQ->NE, GT->LE ...
        return expr;
    }
}
// 无法翻转的才包 NOT
SqlNotExpr notExpr = new SqlNotExpr(); ...
```
- **现状**: `_all(cond)` 被实现为 `not exists(... NOT(cond))`，其中 `NOT(cond)` 优先用比较符翻转（`=` → `<>`、`>` → `<=` 等）而非包裹 `SqlNotExpr`。SQL 三值逻辑中 `NOT (x = 1)` 与 `x <> 1` 不等价：当 x 为 NULL 时前者为 TRUE、后者为 UNKNOWN。
- **风险**: 集合成员的比较列为可空列且存在 NULL 值时，`o.coll._all(col = v)` 会把本应判为“不满足/未知”的行判为满足（NULL 行不命中 `<>`，`not exists` 为真），查询结果与直观语义及标准 `= ALL(...)` 行为不一致。现有单测（TestCollectionOperatorTransformer）以字符串固化了翻转行为，未覆盖 NULL 场景。
- **建议**: 保持语义严格等价可统一包裹 `SqlNotExpr`（生成器已支持括号输出）；或至少在文档/测试中明确 NULL 语义约定。鉴于测试已固化现状，按边界语义问题定级 P2。
- **误报排除**: 通读 `shouldNegateExists`/`replaceLogicNode`（仅叶子 scope 取反一次，`countAllAncestors` 奇数时）确认翻转逻辑的调用条件；通读单测期望串（`t1.status <> 1`）确认现状行为，非误报。

### [P2] 集合表源 helper 的 to-one 路径丢弃用户别名，`from o.dept d` 引用 d 报“未知的实体别名”

- **文件**: `nop-persistence/nop-entropy-fix-ai-check/nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/CollectionTableSourceHelper.java:131-161`；对照 `EqlTransformVisitor.java:770-775`
- **维度**: D1 / D8
- **证据**:
```java
// CollectionTableSourceHelper.buildRelationJoin
if (ref.isToOneRelation()) {
    join = visitor.addToOneRelationJoin(ownerTable, ref);        // 未传 userAlias
} else {
    join = visitor.addToManyCollectionJoin(ownerTable, ref, propName, userAlias);
}
// EqlTransformVisitor.addToOneRelationJoin
SqlSingleTableSource refTable = makeTableSource(source.getLocation(), ref.getRefEntityModel(), null); // alias=null → 自动生成
```
- **现状**: helper 的 javadoc 声明"支持 to-one 和 to-many"，但 to-one 分支调用 `addToOneRelationJoin(ownerTable, ref)` 未传入用户别名，`makeTableSource(..., null)` 生成自动别名；`visitTableSource` 随后按 `transformed.getScopeName()`（自动别名）注册作用域，用户书写的别名 `d` 从未注册。jshell 实测：`... where exists (select 1 from o.dept d where d.name='x') or o.flag='1'` 编译抛 `ERR_EQL_UNKNOWN_ALIAS(d)`。
- **风险**: to-one 关系的集合表源语法完全不可用（用户无法引用任何列，除非碰巧与自动别名同名）；属文档承诺与实现不符的功能缺陷，报错清晰、无数据风险，故定 P2。
- **建议**: 为 `addToOneRelationJoin` 增加 userAlias 参数（与 to-many 对称），或在此路径显式抛出“不支持 to-one 集合表源”的准确错误。
- **误报排除**: 通读 `addToOneRelationJoin`/`makeTableSource`/`visitTableSource` 的别名注册链路，并用 to-one 关系模型实测编译报错，非误报。

### [P3] 死代码：未使用变量与未被调用的私有方法

- **文件**: `compile/CollectionTableSourceHelper.java:75`；`compile/CollectionOperatorTransformer.java:317-334、430-440、442-501`
- **维度**: D6（可维护性）
- **证据**:
```java
String rawName = tableName.getName();
String fullName = tableName.getFullName();   // 此后从未使用
```
```java
private void processNestedScopesInSubquery(...) { ... }   // 无调用方
private List<EqlASTNode> findAllNestedLogicNodes(...) { ... } // 无调用方
// 另有约 60 行注释掉的历史实现（findCorrespondingNode 等）
```
- **现状**: `fullName` 赋值后未使用；`processNestedScopesInSubquery`、`findAllNestedLogicNodes` 为私有方法且全文件无调用点；另有大段注释代码。
- **风险**: 无运行时危害，增加阅读与维护成本。
- **建议**: 删除死代码与注释代码。
- **误报排除**: 对两个方法名全仓 grep（含测试）确认无调用；`fullName` 在方法内无后续引用。

### [P3] 非 count 聚合函数带 `*` 参数时 `getArgs().get(0)` 数组越界

- **文件**: `compile/ExprTypeResolver.java:104-110`
- **维度**: D1（边界输入）
- **证据**:
```java
StdSqlType resolveAggFuncType(SqlAggregateFunction fn) {
    String name = fn.getName();
    if (name.endsWith(OrmEqlConstants.FUNC_COUNT)) {
        return StdSqlType.BIGINT;
    }
    SqlExpr arg = fn.getArgs().get(0);   // sum(*) 等写法 args 为空
```
- **现状**: 文法允许聚合函数 `selectAll`（`ASTERISK_`），编译期没有校验“非 count 聚合不允许 `*`”；`sum(*)` 之类输入会在这里抛未包装的 `IndexOutOfBoundsException`。
- **风险**: 无效输入得到无错误码的裸异常，排障体验差；无正确性影响（合法语句不受影响）。
- **建议**: 在 transform 阶段校验聚合函数参数形态，或此处抛带 location 的 NopException。
- **误报排除**: 通读 `EqlASTBuildVisitor.SqlAggregateFunction_selectAll` 与文法 `sqlAggregateFunction` 确认 `*` 可进入非 count 聚合；通读 `visitSqlRegularFunction` 校验逻辑确认无参数形态校验。

### [P3] `SqlTableScope.addTable` 先 put 后检查，CTE 同名时静默覆盖映射

- **文件**: `compile/SqlTableScope.java:56-68`
- **维度**: D1（轻微）
- **证据**:
```java
SqlTableSource oldTable = this.aliasToTables.put(alias, table);  // 已替换
if (oldTable != null && oldTable != table) {
    if (oldTable instanceof SqlSubqueryTableSource) {
        if (((SqlSubqueryTableSource) oldTable).isSameWithClause(table))
            return;                                               // 返回，但 map 里已是新表
    }
    throw new NopEvalException(ERR_EQL_DUPLICATE_TABLE_ALIAS)...
```
- **现状**: 同一 CTE 被多次引用时（`isSameWithClause` 为真），`put` 已经把旧表源换成新克隆，随后直接 return；后续按别名解析到新克隆。由于两个克隆元数据一致，行为等价，但“检查后放置”的意图被绕过。
- **风险**: 当前无行为差异；若未来两个“同 with 子句”的表源出现差异（例如分别追加了不同过滤），会静默取后者。
- **建议**: 同 with 子句时应保留原映射（`putIfAbsent` 或先检查再 put）。
- **误报排除**: 通读 `SqlSubqueryTableSource.isSameWithClause` 与 CTE 展开（`newCteSource` 每次生成克隆）确认触发路径。

### [P3] `EqlASTBuilder.literal` 对非标准类型统一 `toString` 为字符串字面量

- **文件**: `utils/EqlASTBuilder.java:96-116`
- **维度**: D1（边界输入）
- **证据**:
```java
} else if (value instanceof LocalTime) { ... }
else {
    SqlStringLiteral literal = new SqlStringLiteral();
    literal.setValue(value.toString());   // java.util.Date、枚举等一律转字符串
    return literal;
}
```
- **现状**: 实体过滤器值（`OrmEntityFilterModel.getValue`，经 `newBinaryExpr` 调用此方法）若为 `java.util.Date`、枚举等类型，会被 `toString` 成非标准格式字符串（如 `Sat Aug 23 ...`），再由 `AstToEqlGenerator.visitSqlStringLiteral` 以字符串字面量输出，多数数据库无法解析。
- **风险**: 仅当过滤值配置为不支持的类型时触发，产生运行期 SQL 错误或错误匹配；无注入风险（字面量有转义）。
- **建议**: 补充 `java.util.Date`/枚举分支（格式化为标准日期字面量），或对不可识别类型抛出明确配置错误。
- **误报排除**: 通读 `EqlTransformVisitor.newBinaryExpr`（line 352-360）与 `collectDefaultEntityFilter` 确认过滤值来源与调用链。

### [P3] `SqlParameterMarker.newInstance` 未复制 `masked` 标志

- **文件**: `ast/SqlParameterMarker.java:33-38`
- **维度**: D1（轻微）
- **证据**:
```java
public SqlParameterMarker newInstance() {
    SqlParameterMarker ret = new SqlParameterMarker();
    ret.setParamIndex(paramIndex);
    ret.setSqlParamBuilder(sqlParamBuilder);
    return ret;                       // masked 丢失
}
```
- **现状**: `deepClone` 经 `newInstance()` 克隆参数标记，`paramIndex`/`sqlParamBuilder` 保留但 `masked` 丢失（该类也未覆写 `copyExtFieldsTo`）。
- **风险**: 当前克隆均发生在 `SqlParamTypeResolver` 设置 masked 之前（集合操作符转换、CTE 展开），最终树上的标记随后会被重新设置 masked，故暂无实际泄漏；属于一触即发的潜在陷阱（若未来有克隆发生在类型解析之后，脱敏标记失效会导致敏感参数明文出现在 SQL 日志）。
- **建议**: `newInstance` 补充 `ret.setMasked(masked)` 并覆写 `copyExtFieldsTo`。
- **误报排除**: 通读 `ASTNode.deepClone`/生成类 `_SqlParameterMarker.deepClone` 与两个克隆调用点（`CollectionOperatorTransformer.replaceLogicNode`、`EqlTransformVisitor.newCteSource`）确认时序上暂无影响。

## 附注（已核实为非问题的疑点）

- `EqlASTBuildVisitor.SqlLikeExpr_ignoreCase`/`SqlCteStatement_recursive` 恒返回 true：经文法核实（BaseRule.g4:156-157、DMLStatement.g4:71），这两个回调只在 `ILIKE`/`RECURSIVE` 产生式命中时被调用，恒真是正确实现。
- `AstToSqlGenerator.printWhere` 中 `node.getFrom().getEntitySources()` 的 NPE 疑点：`EqlTransformVisitor.visitSqlQuerySelect`（247-249 行）对无 from 但有 where/having/orderBy/groupBy/limit 的语句已抛 `ERR_EQL_QUERY_NO_FROM_CLAUSE`，生成器仅用于已转换 AST，路径不可达。
- 表达式括号化（`requireParentheses` 与 OR/NOT 自带括号配合）：虽存在过度加括号，未发现语义破坏路径。
- `select x from dual` 路径中 `text.indexOf(sql.getText())`：已核实全部随库 dialect 模板（default/oracle）均原样内嵌 `{fields}`，marker 偏移正确；仅自定义模板改写字段时才有风险。
- 分页包装（`PrintSqlSelect`）每次 `appendTo` 新建生成器导致 `filteredSources` 不共享：随库两个分页 handler 均只调用一次 appendTo，且 filter marker 直接写入共享 buf，无重复或丢失。
