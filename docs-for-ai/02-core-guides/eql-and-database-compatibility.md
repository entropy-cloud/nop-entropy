# EQL 与数据库兼容性

本页集中回答两个问题：**EQL 怎么写**，以及 **ORM 层如何屏蔽多数据库差异**。

sql-lib 文件结构、`${}` 参数化规则和 `@SqlLibMapper` 用法见 `05-examples/sql-lib-and-mapper.java`。EQL/SQL-lib 调试排障见 `04-reference/debugging-checklist.md`。

## EQL：实体查询语言

### 什么是 EQL

EQL = 标准 SQL + 实体关联属性自动展开。它不是一门新语言，而是在标准 SQL 语法基础上增加实体属性导航能力。

```sql
-- EQL: 使用实体名 + 属性名（驼峰），关联属性自动 LEFT JOIN
SELECT o.id, o.customer.name FROM NopAppOrder o WHERE o.customer.status = 1

-- 等价的 SQL: 需要手写 JOIN ON
SELECT o.ID, c.NAME FROM APP_MALL_ORDER o LEFT JOIN APP_MALL_CUSTOMER c ON o.CUSTOMER_ID = c.ID WHERE c.STATUS = 1
```

### `<eql>` 与 `<sql>` 的区别

| 维度 | `<eql>` | `<sql>` |
|------|---------|---------|
| 标识符 | 实体名 + 属性名（驼峰） | 数据库列名（DDL 实际大小写） |
| 关联 | `o.customer.name` 自动 LEFT JOIN | 需手写 JOIN ON |
| 方言适配 | 自动翻译为目标数据库方言 | 直接执行，需自行处理方言差异 |
| 适用场景 | 实体相关查询，优先使用 | 原生方言、复杂聚合、DDL 操作 |

**规则：能写 `<eql>` 就不用 `<sql>`。** `<sql>` 仅用于 EQL 无法表达的原生方言/复杂聚合。

### EQL 完整语法支持

EQL 的语法可用性 = **grammar 接受度 ∩ 方言能力位 ∩ 方言继承链** 三层交集；排名族窗口函数另受方言函数登记维度约束（见下方「分析窗口语法」段的函数登记说明）。grammar 层支持 `SELECT` / `FROM` / `WHERE` / `GROUP BY` / `HAVING` / `ORDER BY` / 子查询 / 窗口函数全集；具体到某一数据库能否执行，以该方言能力位与函数登记为准，勿以语法全集宣示为准。

与 JPQL 的区别：JPQL 需要手写 `JOIN FETCH`，EQL 直接点属性名即可自动展开关联表。

#### 分析窗口语法（2026-10-02 WI1 扩展）

语法层（grammar）现接受完整分析窗口形态：`OVER (PARTITION BY ...)` 单子句、`OVER ()` 空参、frame 三单位 `ROWS`/`RANGE`/`GROUPS`（含 `BETWEEN ... AND ...` 与单 bound 简写、`UNBOUNDED PRECEDING|FOLLOWING`、`CURRENT ROW`、`expr PRECEDING|FOLLOWING`）、命名窗口 `WINDOW w AS (...)` 子句与 `OVER w` 引用。**方言层可用性另算**：窗口函数能否翻译到目标数据库取决于方言函数登记与能力开关（翻译期不支撑时抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`），不以语法层接受度为准。

**迁移说明（新关键字）**：`RANGE` / `WINDOW` / `GROUPS` / `ROW` / `PRECEDING` / `FOLLOWING` / `UNBOUNDED` 已新增为关键字，并同时登记为非保留字（unreserved word）——既有查询把它们用作列名 / 别名的写法不受影响；但 framed 窗口表达式中应避免以这些词做标识符。

#### TUMBLE 时间切片伪表函数（2026-10-03 WI17 扩展，stream-only）

语法层现接受 `TUMBLE(表名.时间列, INTERVAL <n> <单位>)` 作为 FROM 表源的一个变体（`sqlTumbleTableSource`，AST 类 `SqlTumbleTableSource`），固定单位 `SECOND`/`MINUTE`/`HOUR`/`DAY`/`WEEK`（`MICROSECOND` 仅整毫秒接受）。时间列以 bigint epoch-millis 承载。INTERVAL 字面量必须为正的固定时长——非字面量 / 非正数 / 日历单位（`MONTH`/`QUARTER`/`YEAR`）提取时长时 fail-fast（`nop.err.eql.invalid-interval-value`）。

**范围限定**：TUMBLE 是流执行目标的 stream-only 语法（时间切片窗口，D4 裁定），ORM/RDBMS 编译通道不支持该表源——作用域注册与表元数据解析均 fail-fast（`nop.err.eql.table-source-not-resolved`），`toSQL()` 的 TUMBLE 渲染归 WI20（round-trip 不对称在案：勿用 toSQL 断言 TUMBLE 查询）。`TUMBLE` 关键字同时登记为非保留字——既有查询把 `tumble` 用作列名 / 表名的写法不受影响。`HOP`/`SESSION` 伪表函数未进入语法层。

**frame 能力开关（2026-10-02 WI2）**：frame 三单位分别对应方言能力位 `supportWindowFrameRows` / `supportWindowFrameRange` / `supportWindowFrameGroups`（dialect `<features>` 下发，default.dialect.xml 集中缺省 `false`）；翻译期未启用抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`。各方言启用值以 WI3 实跑矩阵产出为准，勿凭文档宣示开启。**已实测开启（2026-10-02 WI3 实跑矩阵，H2 2.4.240 与 PostgreSQL 16 七组全 PASS）**：h2 与 postgresql 三能力位全开；h2gis（经 h2）、postgis 与 duckdb（经 postgresql）经 x:extends 继承同为全开。 **未开启（frame 查询翻译期 fail-fast，断言覆盖）**：dm、mariadb、mssql、mysql、mysql5.7、oracle 共 6 个。

### 语法溯源：BaseRule.g4 注释态规则（2026-10-03 WI0 核查）

`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 中 4 条 SQL-92 标准函数规则（`positionFunction` / `substringFunction` / `extractFunction` / `trimFunction`，现位于 `:299-314` 注释块）**自首次提交 `aa1b310122`（2022-06-27 "增加antlr解析配置示例"）起即为注释态，从未在本仓库生效过**。git 证据（真实输出摘录）：

```console
$ git log -S positionFunction --all -- '*BaseRule.g4'
commit aa1b3101220fe1f215a07a82faa5dd3de884fb96
Author: canonical <canonical_entropy@163.com>
Date:   Mon Jun 27 00:02:34 2022 +0800

    增加antlr解析配置示例
```

`git log -G "positionFunction|trimFunction|extractFunction|convertFunction" --all -- '*BaseRule.g4'` 结果相同（仅命中该 commit，可排除"曾取消注释后又注释"的变体）。使用约束：

- **不得把这 4 条规则当作"EQL 曾正确实现过"的先例** —— 不存在"曾做过又被回退"的历史。将来复活它们（`POSITION`/`SUBSTRING`/`EXTRACT`/`TRIM`）应按新语法设计（WI5 承接），不需要"避免重蹈覆辙"的额外论证。
- 同一区间内 **`sqlExpr_special`（`:286-289`）是活规则**（`CAST` 语法的挂载点），只是其备选分支中的 `//| extractFunction` 被注释 —— 不要把整段 `:284-314` 当死代码。
- `STRING` token **不可词法化**：`EqlLexer.java` 与 `EqlParser.java`（`nop-persistence/nop-orm-eql/.../parse/antlr/`）的 token 表在第 81 行分叉 —— parser 侧多出 `STRING=358`（lexer 以 `BIT_NUM_=357` 结束）。该 token 在词法器里不存在，因此引用它的 `characterSet_` 规则（`BaseRule.g4:384-385`）今天永久不可达（详细登记见 EQL 跨库兼容性 roadmap 的 Current Baseline 与 BUG-15 条目）。

### EQL 常见错误

| 错误码 | 根因 | 修正 |
|--------|------|------|
| `nop.err.eql.unknown-column-name` | 用了数据库列名（如 `update_time`） | 改用实体属性名（如 `updateTime`） |
| `nop.err.eql.unknown-table-name` | 用了表名而非实体名 | 实体名 = `app.mall.dao.entity.XXX` 的简名 |

## 数据库兼容性机制

Nop ORM 通过 Dialect 层屏蔽多数据库差异，使同一套 ORM 模型和 EQL 语句可以在 MySQL、PostgreSQL、Oracle 等数据库上运行。

### 空字符串自动转 NULL

**默认行为：** ORM 在保存字符串字段时，自动将空字符串（`""`）规范化为 `NULL`。

**原因：** Oracle 数据库不区分空字符串和 `NULL`（`""` 被 Oracle 当作 `NULL` 处理）。为了让应用在 Oracle 和其他数据库上行为一致，Nop 默认统一将空字符串转为 `NULL` 存储。

**配置项：**

```yaml
nop.orm.auto_convert_empty_string_to_null: true  # 默认值
```

设置为 `false` 可关闭此行为，允许存储空字符串（仅在确定不使用 Oracle 时推荐）。

**实现路径：**

| 阶段 | 实现位置 | 行为 |
|------|---------|------|
| 参数绑定（PreparedStatement） | `DialectImpl#getStringBinder()` → `DataParameterBinders.STRING_EX` | `setValue` 时检查 `isEmpty()` → `setNull` |
| ResultSet 更新 | `DialectImpl#jdbcSet(ResultSet, ...)` | 空字符串 → `rs.updateNull()` |

**注意：** `STRING_EX` 使用 `StringHelper.isEmpty()` 判断，即 `null` 和 `""` 都会被存为 `NULL`。这不会影响读取——读取时 `NULL` 自然返回 `null`。

### VARCHAR 超长自动提升为 CLOB

当字符串长度超过 Dialect 配置的 `maxStringSize` 时，ORM 自动将 VARCHAR 写入提升为 CLOB（通过 `setCharacterStream`），避免超长字符串导致数据库截断或报错。

二进制数据（ByteString / byte[]）超过 `maxBytesSize` 时同理自动提升为 BLOB。

**实现：** `DialectImpl#jdbcSet(PreparedStatement, ...)` 和 `DialectImpl#jdbcSet(ResultSet, ...)` 中根据 `getMaxStringSize()` / `getMaxBytesSize()` 判断。

### 日期类型读取兼容

H2 数据库的 `Date` 类型在读取 1899 年以前的日期时会少一天。Dialect 通过 `useGetStringForDate` 特性标志，改用 `ResultSet.getString()` 读取日期字符串，规避此问题。

### Dialect 特性标志

Dialect 通过 `DialectFeatures` 模型声明各数据库的语法差异，ORM 引擎据此调整生成的 SQL。常见标志：

| 标志 | 含义 | 典型差异数据库 |
|------|------|---------------|
| `useAsInFrom` | `FROM t AS v` 是否允许 `AS` | Oracle 不允许 |
| `supportNullsFirst` | 是否支持 `NULLS FIRST/LAST` | 不支持时 ORM 用 `IF(ISNULL(f),0,1)` 模拟 |
| `supportDeleteTableAlias` | DELETE 语句是否允许表别名 | MySQL 不允许 |
| `supportUpdateTableAlias` | UPDATE 语句是否允许表别名 | MySQL 不允许 |
| `supportILike` | 是否支持 `ILIKE` 大小写不敏感匹配 | PostgreSQL 支持 |
| `supportSequence` | 是否支持数据库序列 | Oracle 支持，MySQL 不支持 |
| `supportReturningForUpdate` | UPDATE 是否支持 `RETURNING` 子句 | PostgreSQL 支持 |
| `supportFullJoin` / `supportRightJoin` | 是否支持 FULL / RIGHT JOIN | 部分数据库不支持 |
| `supportLateral` | 是否支持 `LATERAL` 子查询表源 | MySQL 8.0.14+/PG/DuckDB 支持；未配置 = 保守 false（编译期报错） |
| `supportWithAsClause` | 是否支持 CTE（WITH AS） | MySQL 8.0+ 支持 |
| `supportBatchUpdate` | 是否支持批量更新 | 大部分数据库支持 |

> 完整标志列表见 `_DialectFeatures.java`。这些标志由 dialect 模型文件（`_vfs/nop/dao/dialect/*.dialect.xml`）配置，开发时不需要手工干预。

### `LIKE … ESCAPE`：执行路径无条件输出（2026-10-03 WI1 BUG-1）

EQL 的 `expr like 'a!%' escape '!'` 会把 escape 字符解析进 AST 并在生成的 SQL 中**无条件输出** `escape '<literal>'`（`AstToSqlGenerator`）；回显路径（`toSqlString()`）目前不回显 escape（WI1 起草时曾预期随 WI2 BUG-4 批次治理，但 WI2 plan 的 BUG-4 范围经 draft review 收口为 selectAll/LIMIT/字面量三项，ESCAPE 回显未获授权承接——已登记为 WI2 plan Non-Blocking Follow-up，待 roadmap 立项授权后再动）。

裁定依据：全仓没有"该方言是否支持 ESCAPE"的字段载体，且已取证方言全部支持该子句，分叉只在**默认 escape 字符**——显式 escape 字符覆盖默认值差异，因此无条件输出、不设方言门（裁定过程见框架开发档案的 WI1 裁决文档 §5）。

**默认 escape 字符三分类**（证据等级逐行标注；未取证者不得计入覆盖率）：

| 方言 | `ESCAPE` 子句 | 默认 escape 字符 | 证据等级 |
|------|--------------|-----------------|---------|
| Oracle | 支持 | NULL（无默认） | `[文档]` |
| DB2 | 支持 | NULL（无默认） | `[文档]` |
| H2 | 支持 | backslash（`ESCAPE ''` 显式关闭） | `[文档]` |
| PostgreSQL | 支持 | backslash（PG 自述偏离 SQL 标准） | `[文档]` |
| MySQL | 支持 | backslash（`NO_BACKSLASH_ESCAPES` 开启时失效） | `[文档]` |
| MSSQL | 支持 | 无默认（`[]` 括号或显式 ESCAPE） | `[文档]` |
| SQLite | 支持 | 无默认（必须显式 ESCAPE） | `[文档]` |
| DuckDB | 支持 | 未取证 | `[unverified]` |
| MariaDB | 支持 | 未取证（MySQL 家族推测 backslash） | `[unverified]` |
| DM8 | 未取证 | 未取证 | `[unverified]` |
| TDengine | 未取证 | 未取证 | `[unverified]` |

**ILIKE + ESCAPE 组合显式报错**：`ilike` 在执行路径经方言函数改写（如 Oracle 的 `regexp_like(x,p,'i')`），改写后的函数形式没有 ESCAPE 挂载点——静默丢弃会改变匹配语义，因此 `ignoreCase=true` 且携带 escape 时编译期报错，错误码 `nop.err.eql.dialect-not-support-feature`（`feature=ilike+escape`）。"发现真实不支持 ESCAPE 子句的方言时改写为函数调用"已登记为 Deferred（触发时 DM8 优先复核）。

### EQL 回显路径契约：`selectAll`/`LIMIT`/字面量（2026-10-03 WI2 BUG-4）

`toSqlString()`/`@dump` 的回显路径（`AstToEqlGenerator`）与执行路径（`AstToSqlGenerator`）分工：

- **`select *`**：解析层形态（`selectAll` 为真且 projections 为空）回显 `*`；经 `EqlTransformVisitor` 展开后的 AST（projections 非空）继续回显展开投影——分页内嵌渲染（`PrintSqlSelect`）依赖这一点，不允许把裸 `*` 文本嵌进外层分页包装 SQL。
- **`LIMIT`/`OFFSET`**：回显经 `printLimit` 钩子输出（复用 `visitSqlLimit`）；执行路径的 limit 在 `visitSqlQuerySelect` 经 `dialect.getPaginationHandler().buildPageExpr(...)` 消费（`AstToSqlGenerator.printLimit` 刻意置空，避免与外层分页文本双份）。修复前回显整体丢失 limit/offset，回显文本再喂回编译器会静默变义。
- **布尔字面量**：回显输出 EQL 关键字形态 `true`/`false`（可重新解析回 `SqlBooleanLiteral`）；此前硬编码 `1`/`0`——重解析为 NUMBER，boolean→integer 类型语义分歧。执行路径继续走 `dialect.getBooleanValueLiteral`（`trueString`/`falseString` 方言形态）。
- **字符串字面量**：回显维持 `escapeSql(value, false)` 的 `''` 双写形，与执行路径 `escapeSlash=false` 方言的 `getStringLiteral` 渲染一致。已知既有缺口（非回显缺陷）：EQL parse 层 `EqlParseHelper.stringLiteralValue` 只 unescape 反斜杠形态、不 un-double `''`（lexer 的 `STRING_` 规则接受 `''` 但值层不还原），故含引号值的回显文本重解析后值含字面 `''`——已登记 WI2 plan Non-Blocking Follow-up，修它属 parse 语义变更需另行立项。

无方言门（roadmap BUG-4 明令"无需方言门"）。回归测试：`TestEqlSelectAllLimitEcho`（回显 7 例，含 round-trip）、`TestEqlSelectAllLimitCompile`（执行路径不变 + 单 LIMIT 断言）。

### `LATERAL` 子查询表源与 `supportLateral`（2026-10-03 WI1 BUG-2）

EQL 的 `from (select ...) x, lateral (select ...) y` 在回显路径无损打印 `lateral` 关键字；执行路径按方言能力门控：`supportLateral=false`（或未配置——**保守缺省 = 不支持**）时编译期报错 `nop.err.eql.dialect-not-support-feature`（`feature=supportLateral`），`true` 时输出 `lateral`。修复前该关键字被静默丢弃（`lateral (sub)` 与 `(sub)` 输出字节相同，退化为不相关子查询）。

能力矩阵（证据等级逐行标注；unverified 边界维持保守缺省，不计入覆盖率）：

| 方言 | `LATERAL` | 证据 | 证据等级 | 配置 |
|------|-----------|------|---------|------|
| PostgreSQL | 支持（9.0 引入；承诺基线 PG 15） | PG 文档 SELECT | `[文档]` | 显式 `true` |
| MySQL | 支持（8.0.14 引入 lateral derived tables） | MySQL 手册 §13.2.11.9 | `[文档]` | 显式 `true` |
| DuckDB | 支持（LATERAL 关键字可省略、自动识别相关子查询） | duckdb.org friendly_sql | `[文档]` | 显式 `true` |
| PostGIS | 继承 PostgreSQL | 运行于 PG | `[文档]`（继承） | 继承 `true` |
| MariaDB | 不支持 | roadmap WI1 登记 | `[roadmap 登记]` | 显式 `false` |
| MySQL 5.7 | 不支持（8.0.14+ 才有） | MySQL 手册 | `[文档]` | 显式 `false`（覆盖继承） |
| H2 | 不支持（官方 grammar 无 LATERAL） | h2database.com grammar | `[文档]` | 未配置（=false） |
| MSSQL | 无 LATERAL 关键字（CROSS/OUTER APPLY 形态） | MS Learn T-SQL | `[文档]` | 未配置（=false） |
| SQLite | 无 LATERAL 关键字 | sqlite.org grammar | `[文档]` | 未配置（=false） |
| Oracle | 12.1+ 边界未获一手确认 | — | `[unverified]` | 未配置（=false） |
| DB2 | 11.5 支持，9.7 边界未获一手确认 | — | `[unverified]` | 未配置（=false） |
| TDengine / DM8 | 文档零提及 / 无一手文档 | — | `[unverified]` | 未配置（=false） |

### `INTERVAL` 字面量：PG 字符串形态可解析（2026-10-03 WI1 BUG-5 骨架）

EQL 的 INTERVAL 现在接受两种语法形态，构建期归一化装入同一个 `SqlIntervalExpr`：

- **既有形态（deprecated，不禁止、不报错）**：`interval 1 day` —— `expr=sqlExpr + unit`，回显输出 `interval 1 DAY`（单位规范为 token 文本）。该形态不经方言模板直接输出，在 PostgreSQL 上非法，**标记 deprecated**；禁止它属破坏性变更，roadmap 明令不做，仅在本文档标注。
- **PG 字符串形态（新增）**：`interval '1 day'` —— 文法 `sqlIntervalExpr` 的 `INTERVAL STRING_` 备选分支；构建期（手写 `EqlASTBuildVisitor.visitSqlIntervalExpr_string`）解析为 value+unit（单位大小写不敏感，接受复数形式 `'3 hours'` → HOUR）。多字段值（`'1-2'`）与非法单位在归一化层显式报错 `nop.err.eql.invalid-interval-unit`；文法层失败（`'1-2' year to month`，qualifier 无文法支持）走 antlr 错误码，两者不混同。

**方言模板骨架**：`dialect.xdef` 的 `<sqls>` 新增 `sqlIntervalLiteral` 元素（与 `<dateLiteral>` 同构），retention 落 `DialectSqls`，accessor `IDialect.getSqlIntervalLiteral()`（未配置返回 null）。**WI1 不填任何逐方言模板内容、不消费模板改写 interval 输出——逐方言模板内容的唯一 owner 是 roadmap WI7b**（占位符形态、各方言正确输出用例均由 WI7b 裁定与交付）。

### 集合下标表达式：显式报错（2026-10-03 WI1 BUG-14）

EQL 文法 `sqlCollectionAccessExpr`（`select o.roles[1].x()` 一类输入）在生成器中**显式报错** `nop.err.eql.collection-access-not-support`（echo 与执行路径同门），不再静默产出垃圾 SQL。裁定依据：该文法的方括号内容是**集合过滤语义**（`where` + `order by`，非下标取值），编译管线对该节点没有 lowering，"机械回显"没有任何数据库能接受；显式失败遵循"静默降级会改变语义必须显式报错"的平台原则。写作替代：集合语义请改写为关联属性 join 或集合操作符（`_some`/`_all` 走另一条 AST 路径，不受影响）。今天文法层即失败的输入（`o.roles[]`、`o.roles[order by a].x()`）保持解析期响亮失败。

### 方言敏感输出点登记表（roadmap 级 EC 第 4 条）

本表登记 EQL 编译管线中"按方言能力分叉的输出点"：generator/transform 位置 + 能力标志 + 不支持时的错误码。**每行都有对应的"该方言编译期报错"负向测试。**

| 输出点 | 位置 | 能力标志 / 通路 | 不支持时错误码 | 负向测试 |
|--------|------|----------------|---------------|---------|
| 集合下标表达式 | `AstToEqlGenerator.visitSqlCollectionAccessExpr`（echo/执行同门） | 无 lowering 通路（语义不支持翻译） | `nop.err.eql.collection-access-not-support` | `TestEqlCollectionAccessError` |
| `for update` 锁文本 | `AstToSqlGenerator.printForUpdate`（回显路径 `AstToEqlGenerator` 硬编码不动） | `getForUpdateSql` 空模板即不支持（如 duckdb `<forUpdate x:override="replace"/>`） | `nop.err.eql.dialect-not-support-feature`（`feature=forUpdate`） | `TestDuckDbDialect.testForUpdateEqlPathFailsLoudly`、`TestEqlForUpdateDialectGate` |
| LATERAL 子查询表源 | `AstToSqlGenerator.visitSqlSubqueryTableSource` | `supportLateral` | `nop.err.eql.dialect-not-support-feature` | `TestEqlLateralSubqueryTableSource.testUnsupportedDialectThrowsAtCompileTime` |
| ILIKE+ESCAPE 组合 | `AstToSqlGenerator.visitSqlLikeExpr` | 函数改写通路无挂载点（无方言门） | `nop.err.eql.dialect-not-support-feature`（`feature=ilike+escape`） | `TestEqlLikeEscapeSql.testIlikeWithEscapeExplicitlyRejected` |
| 窗口 frame ROWS/RANGE/GROUPS | `EqlTransformVisitor` | `supportWindowFrameRows/Range/Groups` | `nop.err.eql.dialect-not-support-feature` | `TestDialectWindowSqlSnapshot` |
| UPDATE RETURNING | `EqlTransformVisitor.visitSqlUpdate` | `supportReturningForUpdate` | `nop.err.eql.dialect-not-support-feature` | `TestEqlCompileSql.testUpdateReturningAllChecksDialect` |

## 相关文档

- `orm-model-design.md` — ORM 模型设计规范（stdSqlType / stdDataType、主键、关系、字典）
- `model-first-development.md` — VARCHAR precision 自动选择、代码生成链路
- `05-examples/sql-lib-and-mapper.java` — sql-lib 文件结构、XPL 参数化规则、Mapper 接口、**rowType DTO 封装**
- `orm-model-design.md` — Sql-Lib Mapper 自动生成（`tagSet="mapper"`）
- `04-reference/debugging-checklist.md` — EQL / SQL-lib 调试排障速查
- `logical-deletion.md` — 逻辑删除（`deleted` / `delVersion`）
