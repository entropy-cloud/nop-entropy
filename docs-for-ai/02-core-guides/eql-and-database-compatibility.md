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

> ⚠️ 本节及下面两节（TUMBLE、frame 能力开关）标题中的 WI 编号均指 **`nop-stream-sql` roadmap**（窗口/TUMBLE 能力的 owner），与 **EQL 跨数据库兼容 roadmap** 的 WI 编号（其 WI3=方言配置纠错，2026-10-04 落地）无关，阅读时勿混用。

语法层（grammar）现接受完整分析窗口形态：`OVER (PARTITION BY ...)` 单子句、`OVER ()` 空参、frame 三单位 `ROWS`/`RANGE`/`GROUPS`（含 `BETWEEN ... AND ...` 与单 bound 简写、`UNBOUNDED PRECEDING|FOLLOWING`、`CURRENT ROW`、`expr PRECEDING|FOLLOWING`）、命名窗口 `WINDOW w AS (...)` 子句与 `OVER w` 引用。**方言层可用性另算**：窗口函数能否翻译到目标数据库取决于方言函数登记与能力开关（翻译期不支撑时抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`），不以语法层接受度为准。

**迁移说明（新关键字）**：`RANGE` / `WINDOW` / `GROUPS` / `ROW` / `PRECEDING` / `FOLLOWING` / `UNBOUNDED` 已新增为关键字，并同时登记为非保留字（unreserved word）——既有查询把它们用作列名 / 别名的写法不受影响；但 framed 窗口表达式中应避免以这些词做标识符。

**覆盖审计（2026-10-04 EQL 兼容 roadmap WI7b，窗口半区核验收口）**：`OVER` 带/不带 partition 与 frame 的语法层、方言 SQL 文本（window/frames golden，10+5 方言）与 H2 真库执行三层覆盖均已就位；WI7b 补齐"不带 partition 仅 order by（± frame）"的编译文本断言（`TestDialectWindowSqlSnapshot.testOverWithoutPartitionText`），产品行为零改动。裁定与审计结论见 WI7b Phase 0 裁定文档（开发过程设计归档）§7。

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
| `supportNullsFirst` | 是否原生接受 `NULLS FIRST/LAST` 子句 | **已接线**（2026-10-04 WI7）：accessor `IDialect.isSupportNullsFirst()` + 消费点 `AstToSqlGenerator.visitSqlOrderByItem`（子句输出唯一门）。现值：duckdb/h2/oracle/postgresql 显式 `true`；其余方言（含 mysql/tdengine 等未配置者）= 模型 null → accessor false（保守缺省）。门行为：true 按请求打印子句；false 时显式请求按缺省序推导"一致省略/冲突报错"（见「defaultNullsFirst」小节）。dialect.xdef javadoc 中「不支持时用 `order by if(isnull(f),0,1), f` 模拟」的设想**不落地**（不支持方言一律"一致省略或响亮报错"，无表达式注入）。与 `defaultNullsFirst`（缺省 NULL 序**事实**）正交，见下方「defaultNullsFirst」小节 |
| `supportDeleteTableAlias` | DELETE 语句是否允许表别名 | 全部方言 `false`（继承 default）。WI10b 行为契约：EQL **接受并归一化**别名（可解析、输出抑制、语义无损），标志为能力声明，勿按 flag 建打印分支或编译期拒绝门（见「UPDATE/DELETE 表别名」小节） |
| `supportUpdateTableAlias` | UPDATE 语句是否允许表别名 | 同上（`supportDeleteTableAlias` 行） |
| `supportILike` | 是否支持 `ILIKE` 大小写不敏感匹配 | H2 显式 `true`；PostgreSQL/DuckDB 继承 default 的 `false`（PG 原生支持 ILIKE，但该标志的现值是 false——消费方以 `ilike` **函数注册**为准，见「ilike 函数注册」小节；翻标志会引入假阴性）。WI10b 裁定：**勿将本标志接为第二道门**（PG 实证：flag=false 且 ilike 函数已注册可用，接标志即破坏两个已收口方言）；标志与注册互不强约束由 `TestDialectDeadFlagDisposition` 锁定 |
| `supportSequence` | 是否支持数据库序列 | Oracle 支持，MySQL 不支持。WI10b 软弃用标注（xdef `@deprecated`：预留未接线、保留配置兼容、勿新增消费依赖——序列 SQL 供给走 `getCreateSequenceSql`/`getSequenceNextValSql`） |
| `supportReturningForUpdate` | **仅指 UPDATE 上返回投影列表形式（无 `INTO` 宿主变量）的 `RETURNING`**（不是"该库有没有 RETURNING"）。值现状（WI3 纠错后）：postgresql `true`；mysql/oracle/h2 及其衍生 mariadb/oracle-reverse/h2gis `false`；duckdb `false`（其 RETURNING 仅 INSERT/DELETE/MERGE）；dm `true` `[unverified]`（DM8 无一手文档）。mysql5.7/mariadb 另有版本门表达，见「集合算子与 RETURNING 版本门」小节 | PostgreSQL 支持；MySQL/Oracle（投影列表形式）/H2 不支持 |
| `supportFullJoin` | 是否支持 `FULL JOIN`/`FULL OUTER JOIN` 表源 | MySQL/MariaDB 显式 `false`（已确认无 FULL JOIN，roadmap WI7b 证据）；TDengine ≥3.3.0.0、MSSQL、PG、H2 等继承 default 的 `true` `[文档]`。已接线编译期门（见下文 FULL JOIN 小节） |
| `supportRightJoin` | 是否支持 RIGHT JOIN | 全部方言继承 default 的 `true`。**已接线编译期门**（2026-10-04 WI10b，`checkJoinTypeFeature` RIGHT_JOIN 分支，见「RIGHT JOIN 表源」小节）；负向载体 = 合成方言（无任何真实方言应配 false 的一手证据） |
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

### `FULL JOIN` 表源与 `supportFullJoin` 编译期门（2026-10-03 WI2 BUG-6）

`full join` 与 `full outer join` 均可解析（文法 `fullJoin_: FULL OUTER? JOIN`，修复前 `full outer join` 解析失败）；回显路径无条件输出 `full join`（`SqlJoinType.FULL_JOIN.getText()`，OUTER 不回显——与 left/right outer join 的既有回显同族）。执行路径经编译期能力门：`EqlTransformVisitor.checkJoinTypeFeature` 在 join 表源转换遍历点消费 `dialect.isSupportFullJoin()`，`false` 时编译期报错 `nop.err.eql.dialect-not-support-feature`（`feature=supportFullJoin`）——**绝不静默改写或丢弃 join 类型**（该标志修复前是零消费死标志：文法不认 `OUTER`、accessor 不存在）。

接线链：`mysql.dialect.xml`/`mariadb.dialect.xml` 显式 `supportFullJoin="false"`（两家已确认无 FULL JOIN，roadmap WI7b 证据；mariadb 显式化取自文档性，运行时值与继承相同）→ `DialectFeatures`（xdef 已声明、生成基类已有 getter，零生成）→ `IDialect.isSupportFullJoin()`/`DialectImpl`（`Boolean.TRUE.equals(...)`，照 window frame 先例）→ 编译期门。`default.dialect.xml` 的 `true` 不动：TDengine ≥3.3.0.0、MSSQL（`FULL OUTER JOIN`）等确认支持者继承它。

能力矩阵（证据等级逐行标注）：

| 方言 | `supportFullJoin` | 证据等级 |
|------|-------------------|---------|
| MySQL | `false`（显式） | `[文档]`（无 FULL JOIN 语法） |
| MariaDB | `false`（显式） | `[文档]` |
| TDengine | `true`（继承 default） | `[文档]`（3.3.0.0 起支持） |
| MSSQL | `true`（继承 default） | `[文档]`（支持 `FULL OUTER JOIN`） |
| PostgreSQL / H2 / DuckDB / Oracle | `true`（继承 default） | `[文档]` |
| DB2 / DM8 | `true`（继承 default，未逐库复核 FULL JOIN 语法） | `[unverified]` |

回归测试：`TestEqlFullJoinParse`（解析红→绿 + `full join` 既有形态守护）、`TestEqlFullJoinDialectGate`（负向错误码 + feature param、嵌套过门、flag 两值行为分叉=接线验证）、`TestDialectFullJoinFlag`（nop-dao，XML→accessor 链逐方言断言）。

### `IN` 列表长度门禁（2026-10-04 WI10b，WI10a 设计定稿）

单个 `IN` 列表的元素个数有全局上限 **1000**，超限在**编译/构建期显式报错**，不做任何改写、截断或静默放行。唯一硬依据：Oracle `IN` 表达式列表上限 1000（`ORA-01795`）；其余方言无一手上限引证（unverified 不进契约），故全方言统一阈值。

| 契约面 | 内容 |
|--------|------|
| 阈值 | 1000，唯一硬依据 `ORA-01795` |
| 配置项 | `nop.core.sql.in-list-max-size`（`CoreConfigs.CFG_CORE_SQL_IN_LIST_MAX_SIZE`，Integer，默认 1000）。**只可调高、无关闭取值**：最小有效值 1，≤0 的配置会拒绝所有 IN 列表（算术上不存在"关闭"）；**调高阈值 = 部署方自担其数据库实际上限的取证责任** |
| EQL 编译路径错误码 | `nop.err.eql.in-list-too-large`，param：`itemCount`（实际元素数）/`maxItems`（阈值）/`stmtName`（语句名）/`dialect`（方言名）。挂载点 `EqlTransformVisitor#visitSqlInValuesExpr`（编译管线，`EqlCompiler#compile` 入口必经；缓存路径下随首次编译执行一次） |
| SqlBuilder 集合展开路径错误码 | `nop.err.core.sql.in-list-too-large`，param：`itemCount`/`maxItems`/`name`（语句名，未设置时不带）。挂载点 `SQL.SqlBuilder#spreadParams`（集合展开全局入口，覆盖 `in()`/`paramEx()`/filter bean 的 `in`/`notIn` 与 INSERT VALUES 直调）；**计数口径 = Collection 顶层元素个数**（标量集合时==`?` 数；行组 `Object[]`/嵌套 Collection 按 1 计） |
| `NOT IN` | 与 `IN` 同为 `SqlInValuesExpr` 节点，同阈值同错误码，无单独放宽 |
| 门禁方言无关 | 不读任何方言能力——超限与否不因目标库改变；字面量与 `:param` marker 统一计数 |
| 回显边界 | `EqlASTNode#toSqlString()` 回显路径**不设门**：忠实转储零截断（既是既有契约，也保证触发门禁时用户能 dump 完整 SQL 定位） |

**迁移说明（这是有意的破坏性变更，WI10a 设计 §3.2 裁定）**：

1. **哪些既有查询会开始失败**：>1000 元素的 IN 列表——两个失败面：EQL 编译路径（`.eql`/`<eql>` 查询，编译期报错）与 filter 展开路径（filter bean 的 `in`/`notIn` 传 >1000 元素集合，SQL 构建期报错）。升级前在无 1000 硬限的方言（MySQL/PG 等）上正确运行的此类查询将开始失败。
2. **升级前评估步骤**：部署到预发环境，按上述两个错误码做一次全量回归盘点，命中查询即为需改造对象（应用侧分批查询，或按下条调高阈值）。
3. **阈值调高出口**：配置 `nop.core.sql.in-list-max-size`（如 `1500`）即可零代码迁移既有合法查询；不存在关闭检查的取值。过渡期 warn-only 被设计明确拒绝（"只打印不设门 = 净回归"）。

回归测试：`TestEqlInListLengthGate`（nop-orm-eql，5 例：阈值内/超阈值码+param/NOT IN 同阈值/回显零截断/配置调高通路）、`TestSqlBuilderInListLengthGate`（nop-core，7 例：filter bean 端到端/直调/空集合 `(NULL)` 既有行为不变/配置通路/行组计数口径）。

### `RIGHT JOIN` 表源与 `supportRightJoin` 编译期门（2026-10-04 WI10b）

`right join`/`right outer join` 的执行路径经 `EqlTransformVisitor.checkJoinTypeFeature` 的 RIGHT_JOIN 分支（与 FULL JOIN 同门同层）：`supportRightJoin=false` 时编译期报错 `nop.err.eql.dialect-not-support-feature`（`feature=supportRightJoin`），绝不静默改写或丢弃 join 类型。全部既有方言 XML 继承 default 的 `true`（RIGHT JOIN 为各库全支持的标准能力）⇒ 该门只对显式配 false 的部署生效，负向载体用合成方言。回归：`TestEqlRightJoinDialectGate`（nop-orm-eql）+ `TestDialectDeadFlagDisposition`（nop-dao，配置断言）。

### 行值构造器 `(a,b)` 与 `supportRowValueConstructor` 编译期门（2026-10-04 WI10b）

`(a, b)` 行组表达式（独立值表达式 / `(a,b) = (1,'x')` 行值比较 / 作 IN 列表元素）在执行路径经 `EqlTransformVisitor.visitSqlMultiValueExpr` 门：`supportRowValueConstructor=false` 时编译期报错 `nop.err.eql.dialect-not-support-feature`（`feature=supportRowValueConstructor`）。与 IN 列表长度门正交（不同 AST 节点：`SqlMultiValueExpr` vs `SqlInValuesExpr`）。全部既有方言继承 default 的 `true`（零 BC）；回显路径不设门。回归：`TestEqlRowValueConstructorGate`。

### UPDATE/DELETE 表别名：接受并归一化（2026-10-04 WI10b 行为契约）

`update tbl o set o.col = …` / `delete from tbl o where …` 的别名写法在 EQL 中**被接受**（文法可解析、transform 期 `makeTableAlias` 注册进 table scope，`o.col` 正常解析），打印期**统一抑制别名输出**——单表 UPDATE/DELETE 下语义无损（flag 值不改变输出，无按 flag 的打印分支或编译期拒绝）。`supportUpdateTableAlias`/`supportDeleteTableAlias` 两标志保留为能力声明（全部方言现值 false），**勿按 flag 在打印器分支输出**（=静默抑制，裁定不成立）也**勿设编译期拒绝门**（全方言 false ⇒ 拒绝语义无损写法，纯 BC 破坏零收益）。未来多表 UPDATE/DELETE 文法 WI 立项使别名语义承载化时，必须以编译期能力门接线两标志。回归：`TestEqlTableAliasSuppressionContract`（flag 两值输出逐字相同锁定）。

### `SqlTypeExpr.characterSet`/`collate`：显式拒绝防护（2026-10-03 WI2 BUG-15）

CAST 类型表达式的 `character set`/`collate` 子句（文法 `characterSet_`/`collateClause_`）被写入 `SqlTypeExpr.characterSet`/`collate` 字段后**没有任何消费者**：`AstToEqlGenerator.visitSqlTypeExpr` 只打印 name/precision/scale，`AstToSqlGenerator.visitSqlCastExpr` 绕过 `SqlTypeExpr` 渲染 ⇒ 字段有值即被静默丢弃（BUG-1 同型的静默丢弃隐患）。防护（裁定=候选 A，解析期拒绝）：手写访问器 `SqlTypeExpr_characterSet`/`SqlTypeExpr_collate` **无条件抛** `nop.err.eql.sql-type-clause-not-support`（param `value`）——访问器被调用即等价于"文法分支可达"，显式拒绝而非吞字段继续编译。

不可达性与可达面（token 分叉证据）：`characterSet_` 期望 `STRING` token——parser token 表有 `STRING=358` 而 lexer token 表止于 `BIT_NUM_=357`（`EqlParser.java`/`EqlLexer.java`），该 token 无任何 lexer 规则可产生 ⇒ 任何形态（含 `character set 'utf8'`）都解析失败；`collateClause_` 期望 `STRING_`（带引号字面量），unquoted 形态（`collate xx`）词法为 `IDENTIFIER_` ⇒ 解析失败；**quoted 形态 `collate 'xx'` 今天即可解析**——防护前它是一条活的最小静默丢弃通路，现在解析期响亮拒绝。与「`LIKE … ESCAPE`」小节同属"回显/生成不消费的 AST 字段"治理面：ESCAPE 是执行路径消费、回显缺失（deferred）；本节两字段是双向零消费（显式拒绝）。

若未来立项实现 `CAST … CHARACTER SET/COLLATE` 的真实语义，必须先修文法（`STRING` token 分叉）并按生成管线 plan-first 走新 plan，同时移除访问器守卫并通过 `TestEqlSqlTypeClauseGuard` 审视。回归测试：`TestEqlSqlTypeClauseGuard`（不可达固化 3 例 + 拒绝路径手构 context 3 例 + 拒绝无条件性 1 例）。

### CAST 目标类型：别名与 UUID（2026-10-04 WI9a）

`cast(x as <type>)` 的目标类型接受面由两层共同决定：**文法层** `dataTypeName_`（显式备选 + `sqlIdentifier_` 单词兜底）与**语义层** `StdSqlType.fromStdName` 的 `stdNameMap` 键集（闸门 A：手写 `EqlASTBuildVisitor.SqlTypeExpr_name`，未登记报 `nop.err.eql.invalid-sql-type`，param `sqlType` + `allowedNames`）。

**接受的别名/新成员（WI9a 落地）**：`text`→CLOB、`number`→DECIMAL（2026-10-04 G-3 增补裁决修订，原 NUMERIC 无任何方言映射）、`bool`→BOOLEAN、`uuid`（新枚举成员）。大小写不敏感（构建期 `toUpperCase` 归一）；`getNames()` 键集 = 25 枚举名 + `INT`/`TEXT`/`NUMBER`/`BOOL` 别名 + `UUID`。

**方言渲染（按 `dialect.stdToNativeSqlType` 链路实测推导，非直觉）**：

| 拼写 | h2 | postgresql | oracle | mssql | duckdb | 无映射方言（响亮报错） |
|------|----|-----------|--------|-------|--------|------|
| `text` | CLOB | TEXT | CLOB | **报错**（无 CLOB 映射） | **报错**（自有映射表无 CLOB） | `nop.err.dao.dialect.std-data-type-not-supported` |
| `number` | NUMERIC（非 DECIMAL！） | NUMERIC | NUMBER | DECIMAL | DECIMAL | 同上 |
| `bool` | BOOLEAN | BOOLEAN | CHAR(1) | BIT | BOOLEAN | 同上 |
| `uuid` | UUID | UUID | **报错**（无原生 UUID） | UNIQUEIDENTIFIER（code 渲染） | UUID | mysql/oracle/tdengine/db2 + 手工方言 |

一致性：同义拼写同方言输出逐字一致（`text`≡`clob`、`number`≡`decimal`、`bool`≡`boolean`）。**注意** h2 上 `number` 渲染 `NUMERIC`（h2 的 std DECIMAL 唯一映射条目是 native NUMERIC）——期望文本必须按链路推导，不得按别名拼写直觉填。bare decimal 形态（无 precision/scale）因 `SqlTypeExpr.getScale()` 缺省 0 渲染为 `XXX(-1,0)`（既有行为，别名与原名同型）。

**拒绝面（不弱化）**：未知拼写（如 `nosuchtype`）仍响亮报 `nop.err.eql.invalid-sql-type` 且 `allowedNames` 含全部新键；别名已放行但方言无映射时**生成期响亮报错**（不静默降级/改写）。回归：`TestEqlCastTypeAliasParse`（解析层）、`TestEqlCastTypeAliasCompile`（别名全链 + 一致性/不等价两类用例）、`TestEqlCastUuidDialectCompile`（UUID 四正三负载体）、`TestEqlCastBooleanParse`/`TestEqlCastBooleanCompile`（WI2 BOOLEAN 底线）。

**文法收窄（2026-10-04 WI9a BUG-11，候选 A）**：`dataTypeName_` 删除 14 个"解析可过、AST 必拒"的死备选——错误形态从"解析过 + `nop.err.eql.invalid-sql-type`"迁移为"解析失败 + `nop.err.antlr.common.parse-fail`"（同为响亮失败）。收窄后文法接受集 = 12 个活显式备选 ∪ `sqlIdentifier_` 单词兜底；`stdNameMap` 仅 `DOUBLE`/`ARRAY`/`ANY` 三者为关键字 token 且无显式备选（无拼写入口，收窄前即如此；键集口径：WI9a 时点 30 键，WI9b 后 32 键——WI9a 裁定文档 §九曾自记"34"系算术误记，2026-10-05 实测勘误）。14 项对照（完整版见 WI9a Phase 0 裁定文档 §五）：

| 被移除拼写 | 收窄前 | 收窄后 | 语义等价拼写 |
|-----------|--------|--------|------------|
| `character` / `character varying` | 解析过 + `nop.err.eql.invalid-sql-type` | `nop.err.antlr.common.parse-fail` | `char` / `varchar` |
| `national character (varying)` / `national char (varying)` / `nchar` | 同上 | 同上 | 无（std 闭集无 national charset 成员） |
| `bit` | 同上 | 同上 | `boolean`（BIT 的布尔语义等价） |
| `bit varying` | 同上 | 同上 | 无 |
| `dec` | 同上 | 同上 | `decimal`（或别名 `number`） |
| `double precision` | 同上 | 同上 | 无（`DOUBLE` 为关键字 token 且不在 `unreservedWord_`——拼写缺口收窄前已存在，follow-up 登记） |
| `interval` | 同上 | 同上 | 无（INTERVAL 走 interval 字面量语法） |
| `time with time zone` / `timestamp with time zone` | 同上 | 同上 | 无（非 LOCAL 变体维持删除态；`WITH LOCAL TIME ZONE` 拼写已由 WI9b 落地，见下节） |

守护测试：`TestEqlCastTypeNarrowingParse`（红绿锚 + 兜底/活备选不变组 + `stdNameMap` 穷举式"文法放行 ⇒ 语义必过或响亮拒"证明）；`TestEqlSqlTypeClauseGuard`（`characterSet_`/`collateClause_` 守卫不受收窄影响）。

### `dataTypeName_` LOCAL 时区拼写与 `StdSqlType` 时区成员（2026-10-05 WI9b）

`dataTypeName_` 新增两个多词备选 `TIME WITH LOCAL TIME ZONE` / `TIMESTAMP WITH LOCAL TIME ZONE`（标准 SQL:1999 拼写；全历史首次存在——与 WI9a 删除的非 LOCAL 备选是两个名字集，非 LOCAL 维持删除态）。`StdSqlType` 的 `TIME_WITH_LOCAL_TIME_ZONE` / `TIMESTAMP_WITH_LOCAL_TIME_ZONE` 落活：键集 **30 → 32**；成员形态 = `Types.OTHER` + `StdDataType.TIME/TIMESTAMP` + `(false,false)`，static 块循环后三处显式重钉（`fromJdbcType(OTHER)`、`fromStdDataTYpe(TIME/TIMESTAMP)`、`fromJavaClass` 既有结果零翻转；`fromJdbcType(2013/2014)` 保持 null——两标准码在真实驱动上表示保偏移类型，不挂给 LOCAL 成员）。

- **闸门 A 多词名称归一化**：手写 `EqlASTBuildVisitor.SqlTypeExpr_name` 对跨多 token 的名称从 token span 重建含空白原文、空白串转 `_` 后查表（ANTLR `getText()` 无分隔符拼接，不适配则多词备选会"解析过 + 语义必拒"）；AST `SqlTypeExpr.name` 存归一化枚举名；单 token 名称行为逐字节不变。
- **CAST 对照表（2026-10-05 实测终稿，裁定文档 §5.4）**：

| 新增拼写 | 修复前 | 修复后 | 受限/等价拼写 → 生成 SQL |
|---|---|---|---|
| `time with local time zone` | 解析失败 `nop.err.antlr.common.parse-fail`（offending=with） | 解析过 → AST 名 `TIME_WITH_LOCAL_TIME_ZONE`；**支持方言空集**（全方言无原生宿主），生成期响亮报 `nop.err.dao.dialect.std-data-type-not-supported` | `time_with_local_time_zone` 同链同输出；`time(3) with local time zone`（precision 前置）解析失败 |
| `timestamp with local time zone` | 同上 | 解析过 → AST 名 `TIMESTAMP_WITH_LOCAL_TIME_ZONE`；oracle 渲染 `TIMESTAMP WITH LOCAL TIME ZONE`；h2 等无宿主方言响亮报同错误码 | `timestamp_with_local_time_zone` 同链同输出；`timestamp(6) with local time zone`（precision 前置）解析失败；后置 `… local time zone(6)` 可解析、precision 入 AST 但按 `(false,false)` 不渲染（与 `date(3)`/`boolean(6)` 同型既有行为） |
| （非 LOCAL 注记行）`time with time zone` / `timestamp with time zone` | 维持 WI9a 收窄后删除态 | 不变（不复活 BUG-11 死备选） | 无 |

- **载体矩阵**（裁定文档 §六）：`TIMESTAMP_WITH_LOCAL_TIME_ZONE` "支持"载体 = oracle（`<sqlDataType name="TIMESTAMP WITH LOCAL TIME ZONE" stdSqlType="TIMESTAMP_WITH_LOCAL_TIME_ZONE"/>`，零撞名纯新增键）；真实 Oracle 建表往返 `[未取证]`（CI 真库仅 H2，H2 2.4.240 实测不支持 LOCAL 形态建表——替代证据 = 方言模型渲染/反查断言）。EC ② 迁移结论 = **`No migration required`**（全仓 114 个 orm.xml 与资源对两个时区 std 类型 0 引用，三口径实测）。
- 守护测试：`TestEqlCastTimezoneTypeParse`（红绿锚 + 非 LOCAL 不复活 + precision 两形态 + `ARG_ALLOWED_NAMES` 32 键）、`TestEqlCastTimezoneTypeCompile`（端到端渲染/响亮报错 + 一致性）、`TestStdSqlTypeTimezoneMembers`（三 map 不变量）、`TestTimezoneTypeGateBWiring`（闸门 B → `AddColumnExecutor` 接线）。

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

**方言模板（2026-10-04 WI7b 收口，执行路径消费）**：执行 SQL 的 interval 输出由 `AstToSqlGenerator.visitSqlIntervalExpr` 按方言模板 `sqlIntervalLiteral`（`<sqls>` 子元素，retention `DialectSqls`，accessor `IDialect.getSqlIntervalLiteral()`）渲染——占位符契约 = 命名 marker `{value}`（接收 value 子表达式渲染文本，纯文本拼接不二次转义）+ `{unit}`（接收单位规范名如 `DAY`）。裁定正文 = WI7b Phase 0 裁定文档（开发过程设计归档）§1/§2。

- **未配置模板的方言维持既有裸输出** ` interval <expr> <UNIT>`（deprecated 语义不变，BC）；**已配置方言上非字面量 value**（参数 marker、列引用等）**编译期显式报错** `nop.err.eql.dialect-not-support-feature`（`feature=intervalNonLiteralValue`）——回落裸输出会继续产出非法文本（"只打印不设门 = 净回归"）；模板含未知 marker 在方言加载期报错 `nop.err.dao.dialect.invalid-interval-literal-template`（`DialectImpl` 构造期校验）。
- **H2/PG 裸输出非法的探针结论**：`INTERVAL '1' DAY` 执行成功 / 裸 `INTERVAL 1 DAY` 语法错（expected "-, +, string"）/ `INTERVAL '1 DAY'` 数据错 22007（H2 2.3.232 与 2.4.240 双版本；探针 transcript 完整归档于开发过程审计归档，可复跑脚本同批归档）。
- **逐方言模板矩阵**：

| 方言 | 模板 | 证据 |
|---|---|---|
| h2 / h2gis（继承） | `INTERVAL '{value}' {unit}` | `[实测]` 探针 |
| postgresql / postgis（继承） | `INTERVAL '{value}' {unit}` | `[文档]` |
| duckdb | `INTERVAL {value} {unit}`（显式覆盖继承的 pg 引号形态） | `[文档]` DuckDB 文档首形态 value 不加引号 |
| mysql / mariadb / mysql5.7（继承） | `INTERVAL {value} {unit}` | `[文档]` `INTERVAL expr unit`，value 不加引号（与 h2/PG 相反） |
| oracle / oracle-reverse（继承） | `INTERVAL '{value}' {unit}` | `[文档]` `INTERVAL '1' DAY` 形态 |
| mssql | 不配置（T-SQL 无 INTERVAL 字面量） | `[文档]` |
| dm / tdengine / db2 / es | 不配置 | `[未取证]` 红线 / 不可加载 / 伪方言 |

- 端到端用例：`TestEqlSqlIntervalTemplate`（h2 真库执行 + 三形态文本断言 + BC/非字面量/坏 marker 负向）。TUMBLE 伪表函数渲染（`visitSqlTumbleTableSource`）刻意逐字输出、不经模板（owner = nop-stream-sql roadmap WI20）。

### 集合下标表达式：显式报错（2026-10-03 WI1 BUG-14）

EQL 文法 `sqlCollectionAccessExpr`（`select o.roles[1].x()` 一类输入）在生成器中**显式报错** `nop.err.eql.collection-access-not-support`（echo 与执行路径同门），不再静默产出垃圾 SQL。裁定依据：该文法的方括号内容是**集合过滤语义**（`where` + `order by`，非下标取值），编译管线对该节点没有 lowering，"机械回显"没有任何数据库能接受；显式失败遵循"静默降级会改变语义必须显式报错"的平台原则。写作替代：集合语义请改写为关联属性 join 或集合操作符（`_some`/`_all` 走另一条 AST 路径，不受影响）。今天文法层即失败的输入（`o.roles[]`、`o.roles[order by a].x()`）保持解析期响亮失败。

### `ilike` 函数注册与消费（2026-10-04 EQL 兼容 roadmap WI3 第 9 项）

`supportILike` 标志只是声明位，**真正的消费通路是 `ilike` 函数注册**：执行路径（`AstToSqlGenerator.visitSqlLikeExpr`）在 `ignoreCase` 时取 `dialect.getFunction("ilike")`，取不到抛 `nop.err.eql.not-support-ilike`——静默降级为大小写敏感 LIKE 会改变查询语义，不允许。

已注册 `ilike` 模板的方言（2026-10-04 现状）：**oracle**（`regexp_like({0},{1},'i')`，既有）、**h2** 与 **postgresql**（`{0} ilike {1}` 原生算子直出，WI3 注册）；经继承 h2gis（经 h2）、postgis/duckdb（经 postgresql）同样命中。未注册：mysql/mssql 等（ilike 查询编译期报错）。⚠️ 不要为 PG 翻 `supportILike` 标志（其继承值为 false 且保持——翻标志会引入假阴性，假阴性=编译期报错而非错误 SQL，标志与注册解耦是刻意的）。

### mysql5.7 窗口函数 mixin 归属（2026-10-04 EQL 兼容 roadmap WI3 第 7 项）

`window-expr-support.dialect.xml` 的 10 个窗口函数（rank/dense_rank/row_number/lead/lag/first_value/last_value/nth_value/percent_rank/cume_dist）**mariadb 与 mysql 合法继承**（MariaDB 10.2+、MySQL 8.0+ 均有窗口函数），**mysql5.7 已内联逐个 `x:override="remove"`**（MySQL 5.7 无窗口函数；裁定=候选 B，泄漏由 `TestMysql57WindowFunctionsRemoved` 的动态守卫拒绝——mixin 未来新增函数会使守卫转红）。**不要"顺手修正" mariadb**。

### `defaultNullsFirst` 与 `supportNullsFirst`：正交两标志（2026-10-04 EQL 兼容 roadmap WI3 第 10/11 项；WI7 接线收口）

- `defaultNullsFirst`（dialect 根属性）= 缺省升序下 NULL 是否排前的**事实**；`supportNullsFirst`（features 标志）= 是否原生接受 `NULLS FIRST/LAST` **子句**。两者正交、各司其职，不是重复标志（duckdb `defaultNullsFirst=false` + `supportNullsFirst=true` 同时成立且都正确）。
- 现值（2026-10-04）：`defaultNullsFirst` 显式配置 mysql/mssql/dm/h2=`true`，tdengine/duckdb/postgresql/oracle=`false`；未配置（如 db2）= 模型 null → accessor false（保守缺省）。两个 accessor 均已接线：`IDialect.isDefaultNullsFirst()`（WI3）+ `IDialect.isSupportNullsFirst()`（WI7）。
- **语法接受（2026-10-04 WI7 Phase 3）**：`order by <expr> [asc|desc] [nulls first|nulls last]` 可解析（`sqlOrderByItem` 单备选内扩展；`NULLS` 为显式大小写不敏感 token——执行期实测修订：plan 设想的"unreservedWord_ 加词零 lexer 改动"产生的是无 lexer 规则的隐式 token=永不可达，同 BUG-15 `STRING` 形态；修正=Literals.g4 显式 `NULLS : N U L L S;`，`NULLS=353` 仅其后 5 个 Literals token 序号顺延 +1、零 token 删除）。`nulls` 收编 `unreservedWord_` 后仍可作列名/别名（`sqlIdentifier_` 通路）。回显打印 `nulls first`/`nulls last`（继承路径，随文法激活首次可达）。
- **`NULLS FIRST/LAST` 门行为（2026-10-04 WI7，执行路径 `AstToSqlGenerator.visitSqlOrderByItem`；回显路径不做方言判断）**：
  1. 未指定 `nullsFirst` → 输出与既有行为逐字节一致（缺省**不主动补子句**）；
  2. `isSupportNullsFirst()=true`（duckdb/h2/oracle/postgresql）→ 按请求打印 `nulls first`/`nulls last`；
  3. false 且显式请求 → 缺省序推导（ASC=`isDefaultNullsFirst()`，DESC=镜像）：请求与该方向缺省序**一致则省略子句**（行为等价）、**不一致则编译期报错** `nop.err.eql.dialect-not-support-feature`（`feature=nullsFirst(first|last)`）。
- **一手文档准入集**：缺省序推导（"一致省略"）仅在方言缺省 NULL 序已有一手文档引证时成立——mysql/mysql5.7/mariadb（MySQL 手册"NULL values are presented first if you do ORDER BY ... ASC and last if you do ORDER BY ... DESC"）、mssql（NULL 视为最低值）、h2（`LOW`/`HIGH` 方向相对）。**dm/tdengine（XML 有配置但无一手文档，红线 `[未取证]`）与 db2（未配置 + 文档不可得）两方向显式请求一律报错**——配置值是登记事实但不作等价性主张（裁定正文 = EQL 兼容 roadmap WI7 的 Phase 0 裁定文档 §三，登记于设计裁定目录）。
- ⚠️ **单个布尔不足以表达真实 NULL 序（能力上限，WI7 锁定证据、不解决）**：
  - **DuckDB-vs-PG 的 DESC 缺省分叉**：DuckDB 官方文档明写 "DuckDB keeps `NULLS LAST` even for `DESC` ordering, whereas PostgreSQL places `NULL`s first on `DESC`"——两库 `defaultNullsFirst` 均 `false`（ASC 缺省相同），**DESC 缺省不同**，且单个布尔不可表达。两库 `supportNullsFirst` 均 `true`，显式子句可表达任一语义（parity 用例：`order by … desc nulls last` 双库都输出 `nulls last`）。
  - **H2 的更强证据**：除方向相对的 `LOW`/`HIGH`（"NULL values are considered as smaller than other values **during sorting**"）外还有方向无关的 `FIRST`/`LAST` 设置（"sorted before other values, no matter if ascending or descending order is used"）。
  - 表达力扩展（如 per-dialect 方向模型字段）是方言模型 schema 变更，未立项；出现 DESC 缺省序敏感的跨库一致性需求时先登记 roadmap。dm/tdengine 的 DESC 行为 `[未取证]`（红线：不计入覆盖率）。

### 集合算子与 RETURNING 版本门（2026-10-04 EQL 兼容 roadmap WI3 第 1/6 项 + 门形态）

**`INTERSECT`/`EXCEPT` 版本门**：dialect `<sqls>` 支持 `intersectKeyword`/`exceptKeyword` 属性（后者既有、前者 WI3 新增），值面契约：

| 配置 | 行为 |
|------|------|
| 未配置（模型 null） | 回落缺省关键字 `intersect`/`except`（与未引入门前逐字节一致） |
| 显式 `none`（哨兵） | 该方言声明**无该集合算子**：编译含 `INTERSECT [ ALL]`/`EXCEPT [ ALL]` 的查询时抛 `nop.err.eql.dialect-not-support-feature`（`feature=intersectKeyword`/`exceptKeyword`），不静默产出目标库不支持的 SQL |
| 其他非空值 | 原样用作关键字（如 Oracle 的 `exceptKeyword="minus"`） |

⚠️ 空串**不能**表达"显式不支持"——空串经 xdef string 属性绑定实测被规范化为 null、与未配置不可区分（2026-10-04 实测），故哨兵用 `none`。

落地配置：`mysql5.7` 与 `mariadb` 显式 `intersectKeyword="none" exceptKeyword="none"`（MySQL 8.0.31 起才有两算子；MariaDB 10.3 起，MDEV-10141；`INTERSECT ALL/DISTINCT` 自 10.5）；`mysql`（floor 8.0.31 已含）不配置；`oracle` 的 `minus` 既有用法不变。

**`UPDATE … RETURNING` 版本门**：`supportReturningForUpdate` 已按真实语义（仅指 UPDATE 投影列表形式）纠错——mysql/oracle/h2 `false`（MySQL 全文法无 RETURNING；Oracle 仅允许 `INTO` 宿主变量形式；H2 无 RETURNING）；mariadb 显式 `false`（`UPDATE … RETURNING` 自 **MariaDB 13.0** 且仅单表）。

**升级路径（部署级 delta 覆盖）**：≥MariaDB 10.3（集合算子）/ ≥10.5（INTERSECT ALL）/ ≥13.0（UPDATE RETURNING）以及 MySQL 5.7 之外的旧版部署，在自有 delta 方言 XML 中显式覆盖——集合算子配 `intersectKeyword="intersect" exceptKeyword="except"`（或删除哨兵配置）、RETURNING 配 `supportReturningForUpdate="true"`。模式 = **声明式保守值 + 编译期报错 + delta 显式覆盖**，与 G-1 裁决一致。

### `stringAgg`：无法无损归一的三处分叉

字符串聚合在各库是三个独立维度的分叉，EQL **不做跨方言归一**（无 `stringAgg` 归一通路），需要排序语义时按目标库原生写法走 `<sql>`：

1. **排序语义**：PG `string_agg(expr, delim ORDER BY x)`（排序在函数内）、MSSQL `STRING_AGG(expr, delim) WITHIN GROUP (ORDER BY x)`、MySQL `GROUP_CONCAT(expr ORDER BY x SEPARATOR d)`、Oracle `LISTAGG(expr, delim) WITHIN GROUP (ORDER BY x)`——同一语义四种句法位置。
2. **`WITHIN GROUP`**：MSSQL/Oracle 必需（MSSQL 不带它则排序不确定），PG/MySQL 无此子句。
3. **分隔符**：MySQL 用 `SEPARATOR` 关键字、其余用第二参数；且 NULL/空组的回退行为（MySQL `GROUP_CONCAT` 全 NULL 时返回 NULL、MSSQL `STRING_AGG` 返回 NULL）与超长截断行为（MySQL `group_concat_max_len` 默认 1024 静默截断）各库不同。

### `regexpLike` 支持面（证据两栏，2026-10-04 对账）

| 已确认有 | 已确认无 | `[未取证]`（不得计入覆盖率） |
|---------|---------|------------------------------|
| PostgreSQL / MariaDB / H2 | MSSQL / TDengine | Oracle / DuckDB / DB2 / DM8 / MySQL 的 operator 页 |

备注：oracle.dialect.xml 的 `ilike` 模板以 `regexp_like` 为改写目标（函数注册先例），但 `regexpLike` 作为独立函数的支持面按上表口径维持 Oracle 未取证，不以方言文件内的使用推断官方支持。

### 跨库语义陷阱与配置建议（17 条，源自跨库调研报告 Part 9）

以下是"能跑但结果不同"的一类——**大多无法靠补语法解决**，靠配置约定与代码评审防范（按调研报告 9.1–9.17 编号）：

| # | 陷阱 | 差异要点 | 建议 |
|---|------|---------|------|
| 9.1 | Collation 决定 `=`/`ORDER BY`/`LIKE` 大小写敏感性 | MySQL 默认 `_ci` 匹配 `'ABC'='abc'`，PG 默认敏感，MSSQL 默认 CI——同一段 EQL 返回行数可不同 | 统一列 collation 约定；大小写不敏感匹配用 `ilike`（仅注册方言）或显式 `lower()` |
| 9.2 | NULL 排序默认值 4 类 | 见 `defaultNullsFirst` 小节 | 不依赖缺省序，显式排序键 |
| 9.3 | Oracle 空字符串即 NULL | 破坏基于 `coalesce` 的 lowering | 平台已默认 `auto_convert_empty_string_to_null`（见上文），勿关闭 |
| 9.4 | 隐式类型转换 | `where intCol = '1'`：MySQL 数值上下文、PG 报错、Oracle 隐式 TO_NUMBER | 参数类型与列类型对齐，勿传字符串比数值列 |
| 9.5 | 日期范围边界 | MySQL `DATE` 起点 1000-01-01，Oracle DATE 含时间跨 BC | 历史日期范围查询按目标库核对 |
| 9.6 | TIMESTAMP vs TIMESTAMPTZ | 各库时区语义不同；default 字面量模板无时区分量 | 时间戳列统一 UTC 约定；涉时区用 `TIMESTAMP(6) WITH TIME ZONE` 类映射 |
| 9.7 | 标识符长度上限 | MySQL 64 / Oracle 12.2 前 30 / PG 63 | 长实体/属性名（`T`+entity+prop 拼接）评审长度 |
| 9.8 | 关键字遮蔽/保留字冲突（双向） | 224 个 EQL 保留词不能作标识符；`unreservedWord_` 放行词与各方言保留字冲突（DuckDB 26 个等） | 列名避开 EQL 保留词与各方言保留词；方言 `<reservedKeywords>` 负责生成侧 quote |
| 9.9 | `CHAR` 尾部空格补齐 | Oracle/MySQL 的 CHAR 比较忽略尾随空格 | 定长字符串谨慎使用 |
| 9.10 | 整除与取模符号 | `5 % -2`：MySQL 截断 −1，PG floor 1 | 负数取模避免跨库依赖 |
| 9.11 | 聚合溢出与返回类型 | `SUM(int)` PG 报错/MySQL 回绕；`AVG(int)` MySQL 返回 int | 数值聚合列用 DECIMAL/BIGINT |
| 9.12 | 布尔↔整数转换 | `WHERE boolCol = 1` PG 非法 | 布尔列用布尔字面量（经 `trueString/falseString` 方言形态） |
| 9.13 | `IN` 列表长度上限 | Oracle 1000 硬上限、MSSQL 参数 2100 | **平台已设门禁（WI10b）**：单 IN 列表 >1000 元素编译/构建期显式报错（见「`IN` 列表长度门禁」小节）；大列表改分批查询或调高 `nop.core.sql.in-list-max-size`（自担取证责任） |
| 9.14 | `UNION` 类型归并与列数 | 各库归一规则不同 | union 两侧投影显式同型 |
| 9.15 | 字符串/Unicode 长度语义 | Oracle VARCHAR2 默认按字节；MSSQL nvarchar 按 UTF-16 单元（emoji 算 2） | 多语言列预留长度；VARCHAR precision 富余 |
| 9.16 | 反斜杠转义分叉 | `escapeSlash` 各库不同（MySQL/DM/TDengine 转义） | 字面量含反斜杠时依赖方言 `escapeSlash` 配置，勿手写双重转义 |
| 9.17 | 递归 CTE 深度 | MySQL 默认 1000、MSSQL 需 hint、PG 无限 | 递归 CTE 设上限并捕获错误 |

### 方言敏感输出点登记表（roadmap 级 EC 第 4 条）

本表登记 EQL 编译管线中"按方言能力分叉的输出点"：generator/transform 位置 + 能力标志 + 不支持时的错误码。**每行都有对应的"该方言编译期报错"负向测试。**

| 输出点 | 位置 | 能力标志 / 通路 | 不支持时错误码 | 负向测试 |
|--------|------|----------------|---------------|---------|
| 集合下标表达式 | `AstToEqlGenerator.visitSqlCollectionAccessExpr`（echo/执行同门） | 无 lowering 通路（语义不支持翻译） | `nop.err.eql.collection-access-not-support` | `TestEqlCollectionAccessError` |
| `for update` 锁文本 | `AstToSqlGenerator.printForUpdate`（回显路径 `AstToEqlGenerator` 硬编码不动） | `getForUpdateSql` 空模板即不支持（如 duckdb `<forUpdate x:override="replace"/>`） | `nop.err.eql.dialect-not-support-feature`（`feature=forUpdate`） | `TestDuckDbDialect.testForUpdateEqlPathFailsLoudly`、`TestEqlForUpdateDialectGate` |
| `for update nowait` / `for update skip locked` 锁后缀（WI12 交付①②） | `AstToSqlGenerator.printForUpdate`（消费 `SqlQuerySelect.nowait`/`skipLocked`；文法 `sqlQuerySelect` 分离标签 `nowait=NOWAIT \| skipLocked=skipLocked_`；回显路径硬编码不动） | `<sqls>` 的 `<forUpdateNowait>`/`<forUpdateSkipLocked>` 配置（presence = 能力 + 文本二合一；h2(+h2gis)/oracle(+oracle-reverse)/postgresql(+postgis)/mysql(+mariadb) 已配置 [实测-jar]/[文档]，mysql5.7/duckdb 继承链显式空覆盖关断，mssql/db2/dm/tdengine 未配置即响亮） | `nop.err.eql.dialect-not-support-feature`（`feature=forUpdateNowait`/`forUpdateSkipLocked`）；矛盾输入（nowait && skipLocked、或任一与 forUpdate=false 组合）= `IllegalArgumentException` | `TestEqlForUpdateLockVariants`、`TestH2ForUpdateLockVariants`（含 mssql/duckdb/mysql5.7 负向 + H2 真库执行）、`TestEqlNowaitWiring`、`TestEqlNowaitH2Dialect` |
| LATERAL 子查询表源 | `AstToSqlGenerator.visitSqlSubqueryTableSource` | `supportLateral` | `nop.err.eql.dialect-not-support-feature` | `TestEqlLateralSubqueryTableSource.testUnsupportedDialectThrowsAtCompileTime` |
| FULL JOIN 表源 | `EqlTransformVisitor.checkJoinTypeFeature`（join 表源转换遍历点） | `supportFullJoin` | `nop.err.eql.dialect-not-support-feature`（`feature=supportFullJoin`） | `TestEqlFullJoinDialectGate`、`TestDialectFullJoinFlag` |
| RIGHT JOIN 表源（WI10b） | `EqlTransformVisitor.checkJoinTypeFeature`（同方法 RIGHT_JOIN 分支） | `supportRightJoin` | `nop.err.eql.dialect-not-support-feature`（`feature=supportRightJoin`） | `TestEqlRightJoinDialectGate` |
| 行值构造器 `(a,b)`（WI10b） | `EqlTransformVisitor.visitSqlMultiValueExpr`（transform 遍历点，覆盖比较/独立值/IN 行组三形态） | `supportRowValueConstructor` | `nop.err.eql.dialect-not-support-feature`（`feature=supportRowValueConstructor`） | `TestEqlRowValueConstructorGate` |
| IN 列表长度（EQL 路径，WI10b） | `EqlTransformVisitor.visitSqlInValuesExpr`（编译管线；**无方言门**——无条件计数） | 无能力标志（阈值 `nop.core.sql.in-list-max-size`，默认 1000） | `nop.err.eql.in-list-too-large` | `TestEqlInListLengthGate` |
| IN 列表长度（SqlBuilder 集合展开路径，WI10b） | `SQL.SqlBuilder#spreadParams`（集合展开全局入口；**无方言门**；计数=Collection 顶层元素数） | 无能力标志（同一配置项） | `nop.err.core.sql.in-list-too-large` | `TestSqlBuilderInListLengthGate`（filter bean `in` 端到端） |
| CAST 类型 characterSet/collate 子句 | `EqlASTBuildVisitor.SqlTypeExpr_characterSet/collate`（解析期守卫，字段双向零消费） | 无消费通路（显式拒绝，见 BUG-15 小节） | `nop.err.eql.sql-type-clause-not-support` | `TestEqlSqlTypeClauseGuard` |
| CAST 目标类型别名/UUID（WI9a） | `AstToSqlGenerator.visitSqlCastExpr` → `dialect.stdToNativeSqlType`（**无方言能力标志**——std 类型映射表驱动，无映射即响亮报错） | 无能力标志（方言 `<sqlDataType>` 映射表；std 键集闸门= `StdSqlType.fromStdName`） | `nop.err.eql.invalid-sql-type`（未登记拼写）/ `nop.err.dao.dialect.std-data-type-not-supported`（无映射方言） | `TestEqlCastTypeAliasCompile`、`TestEqlCastUuidDialectCompile` |
| ILIKE+ESCAPE 组合 | `AstToSqlGenerator.visitSqlLikeExpr` | 函数改写通路无挂载点（无方言门） | `nop.err.eql.dialect-not-support-feature`（`feature=ilike+escape`） | `TestEqlLikeEscapeSql.testIlikeWithEscapeExplicitlyRejected` |
| 窗口 frame ROWS/RANGE/GROUPS | `EqlTransformVisitor` | `supportWindowFrameRows/Range/Groups` | `nop.err.eql.dialect-not-support-feature` | `TestDialectWindowSqlSnapshot` |
| UPDATE RETURNING | `EqlTransformVisitor.visitSqlUpdate` | `supportReturningForUpdate`（仅指投影列表形式，WI3 纠错后 mysql/oracle/h2/mariadb/duckdb 均拒） | `nop.err.eql.dialect-not-support-feature`（`feature=supportReturningForUpdate`） | `TestEqlCompileSql.testUpdateReturningAllChecksDialect`、`TestEqlUpdateReturningDialectGate` |
| INTERSECT/EXCEPT 集合算子 | `AstToSqlGenerator.getIntercectKeyword/getExceptKeyword`（哨兵 `none` 门） | `intersectKeyword`/`exceptKeyword` 配置（`none` = 无该算子） | `nop.err.eql.dialect-not-support-feature`（`feature=intersectKeyword/exceptKeyword`） | `TestEqlSetOperatorDialectGate`、`TestDialectSetOperatorKeywords` |
| SQL-92 函数语法（POSITION/SUBSTRING/EXTRACT/TRIM 三段式，WI5） | 构建期归一化进 `SqlRegularFunction`（`EqlASTBuildVisitor.visitSqlRegularFunction_position/substring/extract/trim*`），方言解析走 `EqlTransformVisitor.visitSqlRegularFunction` 函数表通路 | 方言函数表注册（`dialect.getFunction(归一化名)`；TRIM 带 chars=trimChars/ltrimChars/rtrimChars 模板仅注册已验证方言） | `nop.err.eql.unknown-function`（funcName=归一化名） | `TestEqlPositionFunctionDialect.testUnknownDialectThrowsLoudUnknownFunction`（SUBSTRING/EXTRACT/TRIM 各自同类） |
| 分页两标准形态 `FETCH FIRST`/`OFFSET..FETCH NEXT`（WI7） | 构建期归一化进既有 `SqlLimit{limit, offset}`（生成 visit 逐字段赋值），lowering 走 `AstToSqlGenerator.visitSqlQuerySelect` → `dialect.getPaginationHandler().buildPageExpr`（**无方言能力标志**——输出形态由方言 `paginationHandler` 配置决定） | `paginationHandler` 配置（LimitOffset 族 / OffsetFetch 族，见「分页两语法形态」小节矩阵） | 文法层解析错误（不完整形态，如 `OFFSET n ROWS` 无 FETCH）；lowering 无失败分支 | `TestEqlPaginationFetchParse`、`TestEqlPaginationFetchCompile` |
| `ORDER BY … NULLS FIRST/LAST` 子句（WI7） | `AstToSqlGenerator.visitSqlOrderByItem`（执行路径唯一门；回显路径不设门） | `supportNullsFirst`（true=按请求打印）+ `defaultNullsFirst`（false 时缺省序推导，仅一手文档已证方言集允许"一致省略"） | `nop.err.eql.dialect-not-support-feature`（`feature=nullsFirst(first|last)`——冲突或方言缺省序无一手文档） | `TestEqlNullsFirstDialectGate`、`TestDefaultNullsFirstFlag`（accessor 双侧契约） |
| 归一化日期时间函数族 `lastday/weekofyear/dayofweek/datetrunc/datediff`（WI6） | 构建期即普通函数调用（函数名小写化既有通路），方言解析走 `EqlTransformVisitor.visitSqlRegularFunction` 函数表通路（零文法/AST 改动） | 方言函数表注册（native 或 template；仅注册一手取证方言，不进 default——字符串单位约定下裸关键字方言 cell 不落） | `nop.err.eql.unknown-function`（funcName=归一化名） | `TestEqlLastDayDialect.testUnregisteredDialectThrowsLoud`（其余函数各自同类） |
| INTERVAL 字面量执行输出（WI7b） | `AstToSqlGenerator.visitSqlIntervalExpr` | `sqlIntervalLiteral` 模板配置（`<sqls>` 子元素）；未配置 = 既有裸输出（BC，不报错）；已配置 + 非字面量 value = 编译期报错；坏 marker = 加载期报错 | 已配置+非字面量：`nop.err.eql.dialect-not-support-feature`（`feature=intervalNonLiteralValue`）；坏 marker：`nop.err.dao.dialect.invalid-interval-literal-template` | `TestEqlSqlIntervalTemplate` |
| GROUP BY 分组操作符 ROLLUP/CUBE（WI7b） | `EqlTransformVisitor.visitSqlGroupingOp`（transform 门）+ `AstToEqlGenerator.visitSqlGroupingOp`（打印，identity lowering） | `supportRollup` / `supportCube`（分立两标志；仅 GROUP BY 位置合法，非 GROUP BY 位置走函数表兜底） | `nop.err.eql.dialect-not-support-feature`（`feature=rollup|cube`）；非 GROUP BY 位置 `nop.err.eql.unknown-function` | `TestEqlGroupingOpsDialect` |
| `GROUPING()` 函数（WI7b） | `EqlTransformVisitor.visitSqlRegularFunction`（既有函数表通路，零新门） | 方言函数表注册（`<native name="grouping" realName="GROUPING">`，仅注册 ROLLUP/CUBE 能力集方言；元数 minArgCount/maxArgCount 按方言） | `nop.err.eql.unknown-function`（未注册方言）；`nop.err.eql.func-too-many/few-args`（元数越界） | `TestEqlGroupingOpsDialect` |
| `IS [NOT] DISTINCT FROM` 谓词（WI8） | 构建期归一化进既有节点（`EqlASTBuildVisitor.visitSqlBinaryExpr_isNotDistinctFrom` → `SqlBinaryExpr(operator=SAFE_EQ)`；`visitSqlNotExpr_isDistinctFrom` → `SqlNotExpr` 包装），方言渲染走 `AstToSqlGenerator.printSafeEq` 三分支（**无方言能力标志**——名称集合，`DOCUMENTED_DIRECTIONAL_NULL_ORDER` 先例）；Oracle 三节点展开走 WI4 管线 `transformAfterAnalyze`（Phase 3 默认注入 transformer） | 名称集合：mysql 族（mysql/mysql5.7/mariadb）=`<=>`；native 集（postgresql/postgis/h2/h2gis/duckdb/mssql）=`IS [NOT] DISTINCT FROM` 方向文本直出；其余方言（oracle 无 transformer/db2/dm/tdengine/手构）=响亮报错 | `nop.err.eql.dialect-not-support-feature`（`feature=is[not] distinct from`） | `TestEqlNullSafeDistinctParse`、`TestEqlNullSafeDistinctDialect`（含手构方言负向 + oracle 无 transformer 负向） |

### AST 改写管线（`IEqlAstTransformer`，2026-10-04 WI4 接线）

`IEqlAstTransformer` 是 EQL 编译管线中的**固定编译环节**（不是可选组件）：所有 EQL 编译最终经由唯一生产入口 `LoadedOrmModel.compileSql`，transformer 解析点唯一——入参为 null 时回落 `env.getDefaultAstTransformer()`；缓存安全（非默认实例绕过缓存）由该方法体强制，契约载于 `ILoadedOrmModel.compileSql` javadoc。

**默认注入链（配置方式）**：实现 `IEqlAstTransformer` → 配置 `SessionFactoryConfig.eqlAstTransformer`（beans.xml 属性注入）→ `OrmSessionFactoryBean.init()` 时一次性注入 `SessionFactoryImpl.defaultAstTransformer` → 编译时 `LoadedOrmModel` 回落使用。未配置时生产 `defaultAstTransformer` 为 null，管线处于"已接线、未配置"的合法态，行为与未引入管线前一致。

**时机槽位（两个钩子的分工，WI8 直接消费）**：

| 槽位 | 安全条件 | 用途 |
|------|---------|------|
| `transformBeforeAnalyze` | 无限制（改写产物会被完整分析管线处理：参数编号、实体解析、隐式 join、类型解析） | 引入新参数/实体引用的 lowering 的**固定槽位** |
| `transformAfterAnalyze` | 仅**参数保持型 + 实体引用保持型**纯语法改写（不新增 `SqlParameter`、不新增实体引用、不改变既有参数相对顺序；新增未解析节点会被生成器的 resolvedExprMeta 解引用拒绝） | 需要方言判定的改写（querySpace 在 analyze 中途才确定，after 槽位可从已解析表源 `getResolvedTableMeta().getQuerySpace()` 推导方言，禁止用默认 querySpace 猜测） |

**缓存策略（迁移说明——选型结论）**：`QueryPlanCacheKey`（name/sqlText/disableLogicalDelete/allowUnderscoreName/enableFilter 五字段）**不含 transformer**。已选方案 = **非默认 transformer 绕过缓存**：显式传入与 `env.getDefaultAstTransformer()` 不同实例的 transformer 时，`useCache` 按 false 处理（不读不写），仍完整编译并返回正确结果。不变式 = 缓存中只存在默认 transformer 编译的计划。被拒备选 = transformer 语义指纹纳入缓存键（需跨模块接口变更且无真实场景；出现"高频编译 + 必须非默认 transformer 且需要计划缓存"场景时另立 plan，照 `enableFilter` 先例扩键维度）。

**init 期常量契约**：`defaultAstTransformer` 是 per-session-factory 常量（init 时注入一次；全仓唯一 setter 调用点在 `init()`）。若 init 之后更换默认 transformer，缓存中旧默认的存量计划立即过期——该场景出现时必须清缓存（`clearQueryPlanCache`）或重开缓存键裁定。配套假设：同一 transformer 实例对相同输入产出相同改写（确定性）；非确定 transformer 不满足缓存前提。

**旧参数使用边界（迁移说明）**：7-arg `compileSql` 的 `astTransformer` 参数**保留**（测试/工具 opt-in 路径在用）。**生产代码不应 per-call 传入 transformer**，统一走 factory 默认配置；显式传入视为测试/工具行为，且非默认实例不参与计划缓存。合同由 `ILoadedOrmModel.compileSql` javadoc 载明（原"调用方必须保证语义一致"的转嫁措辞已删除——缓存安全由代码强制，不再依赖调用方纪律）。

**WI8 消费指引**：`IS DISTINCT FROM` 类方言展开放 `transformAfterAnalyze`（展开式只重排/包装既有操作数节点，满足参数保持条件），方言经已解析表源推导；引入新参数/实体的改写放 `transformBeforeAnalyze` 且必须方言无关。证明测试先例：`nop-persistence/nop-orm/src/test/java/io/nop/orm/TestEqlAstTransformerWiring.java`（factory 默认配置端到端 + 缓存绕过）。

### SQL-92 标准函数语法（2026-10-04 WI5：POSITION/SUBSTRING/EXTRACT/TRIM + 遮蔽放开）

EQL 接受四个 SQL-92 标准语法形态，构建期归一化为**普通函数调用**（复用既有 `SqlRegularFunction` + 方言函数表解析：未注册 lowering 目标的方言编译期 `ERR_EQL_UNKNOWN_FUNCTION` 响亮报错，不允许静默按原文输出）。裁定与逐方言证据：EQL 跨库兼容 roadmap WI5（执行计划与裁定正文归 ai-dev 设计域，此处只保留源码锚点：归一化实现 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTBuildVisitor.java` 的 `visitSqlRegularFunction_*` 组，文法 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `sqlExpr_special`）。覆盖标注口径：`[实测]`=CI 真库（H2 in-mem）执行证据；`[文档]`=一手文档引证；`[未取证]`=无一手文档，不计入覆盖率。

#### POSITION(x IN y) → `instr(y, x)`

调用点换序在构建期完成（`instr(str, substr)` 参数序：主串在前）；回显输出归一化形态 `instr(...)`。

| 方言 | 输出 | 证据 |
|------|------|------|
| h2 / h2gis / mysql / mariadb / mysql5.7 / oracle / oracle-reverse / dm / tdengine / es / db2 | `instr(o.name,'b')`（native） | `[实测]`（h2，`TestEqlPositionFunctionDialect.testH2LowersToInstrWithSwappedArgs` + `TestDialect.testFunctions` 既有执行）/ dm、tdengine、db2、es 标注 `[未取证]` |
| duckdb / postgis | `strpos(o."name",'b')`（realName=strpos，同序） | `[文档]`（DuckDB strpos(haystack, needle)）/ `[实测]`（duckdb，testDuckDbLowersToStrposRealName） |
| postgresql | `strpos(o.name,'b')`（realName=strpos） | `[文档]`（PG 15 string functions：strpos(string, substring)） |
| mssql | `charindex('b',o.name)`（模板 `charindex({1},{0})`，模板内换序） | `[文档]`（MSSQL CHARINDEX(find, within)） |

既有逗号形态 `position(x,y)` 行为不变：解析通过，函数表无 `position` 注册 → 编译期响亮报错。`position(a in (b,c))`（IN 列表竞争）固定按特殊语法解析（ANTLR 备选序取 `sqlExpr_special`）。

#### SUBSTRING(x FROM a [FOR b]) → `substr(x, a[, b])`

FOR 缺省 2 参、否则 3 参，复用方言函数表 `substr` 的 `minArgCount="2" maxArgCount="3"` 两元多态；回显输出归一化形态 `substr(...)`。

| 方言 | 输出 | 证据 |
|------|------|------|
| h2 / h2gis / mysql / mariadb / mysql5.7 / oracle / oracle-reverse / dm / tdengine / es / db2 | `substr(o.name,2)` / `substr(o.name,2,3)`（native） | `[实测]`（h2，`TestEqlSubstringFunctionDialect` 2/3 参两用例 + `TestDialect.testFunctions` 既有执行）/ dm、tdengine、db2、es 标注 `[未取证]` |
| mssql | `substring(o.name,2,3)`（realName=substring；MSSQL `SUBSTRING(expr,start,len)` 语义同序） | `[文档]`（MSSQL SUBSTRING）/ `[实测]`（mssql 模型编译文本，testMssqlRealNameSubstring） |
| duckdb / postgresql / postgis | `substr(...)`（native 同名；DuckDB/PG 均有 `substr(string, start[, length])`；PG 同语义亦可用 `substring(a from b for c)` 原生形态） | `[文档]`（PG 15 / DuckDB string functions） |

既有逗号形态 `substring(x,a,b)` 行为不变：解析通过（函数名归一为 `substring`），函数表注册名是 `substr` → 编译期响亮报 `ERR_EQL_UNKNOWN_FUNCTION(substring)`（守护用例锁定，不静默改道）。
**语义差异登记（不等价类）**：负起点索引——H2/MySQL `substr(str, -n)` 自串尾倒数（default.dialect.xml 描述明言"start 为负则相对末尾"）；MSSQL `SUBSTRING` 起点必须 ≥1（负值/0 行为不同）。EQL 归一化只做无损换形，不合并方言语义，跨库依赖负起点需自行核对目标库。

#### EXTRACT(f FROM d) → 逐字段函数 `f(d)`

归一化为**与字段同名的小写函数**：`EXTRACT(YEAR FROM d)` → `year(d)`、`EXTRACT(QUARTER FROM d)` → `quarter(d)`（默认形态钉死为逐字段函数；逗号形态 `extract(f,d)` 是另一条既有路径，行为不变）。字段规则仅放行 `YEAR/MONTH/DAY/HOUR/MINUTE/SECOND/QUARTER` 七个（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `extractField__` 规则）；`WEEK`/`MICROSECOND` 等无注册函数的字段在文法层即拒绝（响亮），扩展字段须先注册同名函数。

| 字段 | default（h2/h2gis/mysql/mariadb/mysql5.7/dm/tdengine/es/db2 继承） | mssql | oracle / oracle-reverse | postgresql / postgis / duckdb | 证据 |
|------|------|------|------|------|------|
| year | `year(d)` native（既有） | `datepart(year,d)` | `extract(YEAR from d)` | `extract(year from d)` | `[实测]` h2 + `[文档]` |
| month/day/hour/minute/second | `month(d)` 等 native（WI5 注册） | `datepart(f,d)` ×5 | `extract(F from d)` ×5 | `extract(f from d)` ×5 | `[实测]` h2（六字段 testSql 真库执行，`TestDialect.testFunctions` + `TestEqlExtractFunctionDialect.testH2AllSixNewFields`）；mssql/oracle/pg `[文档]`（MySQL 8.0 日期函数、MSSQL DATEPART、Oracle EXTRACT、PG 15 datetime functions） |
| quarter | `quarter(d)` native（WI5 注册） | `datepart(quarter,d)` | `to_number(to_char(d,'Q'))`（Oracle EXTRACT 无 QUARTER 字段） | `extract(quarter from d)` | `[实测]` h2（H2 真库 QUARTER 执行）；其余 `[文档]` |

`dm`/`tdengine`/`db2`/`es` 经 default 继承获得注册，实际函数行为 `[未取证]`（与既有 `year` 同等待遇，不计入覆盖率）。**语义差异登记（不等价类）**：Oracle `extract(SECOND from ts)` 含小数秒，MySQL/`second()` 0-59——跨库需要整数秒时自行截断。

#### TRIM 标准三段式（两档）

无 chars 档按方向归一化既有单参函数（全体方言零新增注册）；带 chars 档归一化新规范函数三件套 **`trimChars/ltrimChars/rtrimChars(str, chars)`**（主串在前），方向进逐方言 `<template>`。**硬性禁令：带 chars 形态在未注册模板的方言上编译期 `ERR_EQL_UNKNOWN_FUNCTION` 响亮报错——不允许静默丢 chars 参数、不允许降级单参 trim。** 单参 `trim(x)` 不新增可解析入口（仍走普通函数路径，行为逐字不变）。

| 方言 | 无 chars（BOTH/LEADING/TRAILING） | 带 chars（trimChars/ltrimChars/rtrimChars） | 证据 |
|------|------|------|------|
| h2 / h2gis | `trim(y)` / `ltrim(y)` / `rtrim(y)` | `TRIM(BOTH {chars} FROM {str})` 等标准模板 | `[实测]`（h2 真库 testSql + `TestEqlTrimFunctionDialect`）/ `[文档]`（H2 TRIM 标准全形态） |
| mysql / mariadb / mysql5.7 | 同上 | 同标准模板（`TRIM(BOTH remstr FROM str)`） | `[文档]`（MySQL 8.0 String Functions） |
| postgresql / postgis | 同上 | 同标准模板 | `[文档]`（PG 15 TRIM） |
| oracle / oracle-reverse | 同上 | 同标准模板 | `[文档]`（Oracle TRIM） |
| mssql | 同上 | 同标准模板（**MSSQL 2022 起**支持 LEADING/TRAILING/BOTH characters FROM；G-1 承诺基线恰为 2022） | `[文档]`（MSSQL 2022 TRIM） |
| duckdb | 同上 | 函数形态 `trim/ltrim/rtrim({str}, {chars})`（DuckDB 无关键字形态，显式覆盖防继承 PG 关键字模板） | `[文档]`（DuckDB trim/ltrim/rtrim 双参签名）/ `[实测]`（编译文本 testDuckDbFunctionFormTemplate） |
| dm / tdengine / es / db2 | 同上（单参 15/15 既有） | **未注册 → 编译期响亮报错**（dm/tdengine/db2 `[未取证]`，es pseudo） | `[实测]`（负向：testCharsFormFailsLoudOnUnregisteredDialect） |

**语义差异登记（不等价类）**：chars 参数是**字符集合**语义（标准 SQL/MySQL/H2/PG/Oracle/DuckDB 一致——`trimChars('xy', s)` 移除串两端任意 'x' 或 'y' 字符）；无 chars 的 `trim(s)` 各库默认修剪空格。EQL 原样 lowering 不做语义合并。

#### 关键字遮蔽放开：`day/hour/minute/second/quarter`（2026-10-04 WI5 Phase 5）

五个日期词加入 `unreservedWord_`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`），在**函数名位置与标识符位置（列名/别名）可解析**；`nop-persistence/nop-orm-eql/model/antlr/Keyword.g4` 词法 token 不动（不删不改不重排），`YEAR|MONTH` 既有成员不变。行为口径：

| 位置 | 修复前 | 修复后 |
|------|------|------|
| 函数名 `day(x)` | 解析失败（`nop.err.antlr.common.parse-fail`） | 可解析 → transform 层：已注册方言（EXTRACT 字段函数覆盖的 15 方言）lowering `day(x)`；未注册方言 `ERR_EQL_UNKNOWN_FUNCTION` 响亮报错 |
| 标识符 `o.day` | 解析失败 | 可解析，回显照常 |
| `month()/year()` | 能解析（`unreservedWord_` 既有成员） | 行为不变（`month` 现亦为 EXTRACT 字段注册函数，与 `day` 同待遇） |
| `interval 1 day` / `interval '3 hours'` | 可解析 | 逐字不变（`intervalUnit_` 上下文只有该规则可达，`TestEqlIntervalLiteralParse` 全量回归） |

无参形态 `day()` 照常解析（`sqlRegularFunction` 的 args 可选，与所有函数一致），响亮失败发生在 transform 层（元数校验 `ERR_EQL_FUNC_TOO_FEW_ARGS` / 未注册 `ERR_EQL_UNKNOWN_FUNCTION`）。`day()` 等归一化日期函数族的注册 owner 是 WI6；`WEEK` 不在放开清单（roadmap WI5 只点名五词，观察项）。既有 7 个语料文件对这些词 0 命中；WI0 语料基线棘轮的唯一 drift 是 2 条 parse-error 诊断转储的 expected-token 集合新增五词（解析结论与回显文本逐字不变，快照已照棘轮规程重生成并复核）。

### 归一化日期时间函数族（2026-10-04 WI6：dayofweek/weekofyear/dateAdd/dateDiff/dateTrunc + lastDay）

EQL 归一化日期时间函数（普通 `SqlRegularFunction` 通道：函数名构建期小写化，`lastDay(d)` 查表名 `lastday`；未注册方言编译期 `ERR_EQL_UNKNOWN_FUNCTION` 响亮报错，不静默降级）。裁定与逐 cell 一手引证归 ai-dev 设计域（WI6 Phase 0 裁定文档，2026-10-04）。覆盖标注口径与 WI5 相同：`[实测]`=CI 真库（H2 in-mem）执行证据；`[文档]`=一手文档引证；`[未取证]`=无一手文档（dm/tdengine/db2/es，不计入覆盖率）。

**单位参数约定**：`dateTrunc(unit, d)` / `dateAdd(unit, n, d)` / `dateDiff(unit, d1, d2)` 的 unit 采用**字符串字面量**（如 `'month'`）。字符串单位可无损到达 pg/duckdb/h2；**裸关键字语法位方言不可达**（mysql `DATE_ADD` unit 明文为 keyword；mssql `DATEDIFF` 明文 "The datepart value can't be specified in a variable, nor as a quoted string like 'month'"，`DATEADD`/`DATETRUNC` 无字符串接受证据；oracle interval 单位/`TRUNC` fmt 词表无法经 fixed template 翻译）——这些 cell 一律不注册（响亮）。

#### lastDay(d) → `lastday`（返回 d 所在月份最后一天的日期值）

| 方言 | 输出 | 证据 |
|------|------|------|
| h2 / h2gis | `LAST_DAY(d)`（native realName，`<testSql>LAST_DAY(DATE '2026-02-10')</testSql>` 真库执行） | `[实测]`（h2 `TestDialect.testFunctions` → 2026-02-28 + `TestEqlLastDayDialect.testH2RealDbExecution`）/ `[文档]`（H2 functions.html：返回 date） |
| mysql / mariadb / mysql5.7 | `LAST_DAY(d)`（native） | `[文档]`（MySQL 8.0："returns the corresponding value for the last day of the month"） |
| oracle / oracle-reverse | `LAST_DAY(d)`（native） | `[文档]`（Oracle 21c sqlrf LAST_DAY） |
| postgresql / postgis / duckdb | `CAST(date_trunc('month', d) + INTERVAL '1 month' - INTERVAL '1 day' AS DATE)`（模板，精确算术） | `[文档]`（PG 15 date_trunc(text, ...)）+ DuckDB 嵌入式 scratch 同型表达式实跑 = 2026-02-28（一次性取证，非门禁面） |
| mssql | `EOMONTH(d)`（模板） | `[文档]`（T-SQL EOMONTH；CI 无 MSSQL 真库，渲染文本断言为替代证据） |
| dm / tdengine / db2 / es | **未注册 → 编译期响亮报错**（`ERR_EQL_UNKNOWN_FUNCTION(lastday)`，负向用例锁定） | `[未取证]` |

**语义差异登记（不等价类）**：postgresql 模板对 `timestamptz` 输入按会话 TimeZone 截断（时变），原生 `LAST_DAY` 族（h2/mysql/oracle/mssql `EOMONTH`）无此时变——输入类型域钉死 DATE/TIMESTAMP（无时区）；跨库传 timestamptz 需先显式转无时区类型。

#### weekofyear(d) → ISO-8601 周号（1-53）

| 方言 | 输出 | 证据 |
|------|------|------|
| h2 / h2gis | `ISO_WEEK(d)`（模板，`<testSql>` 真库执行） | `[实测]`（h2 `TestDialect.testFunctions` → 7 + `TestEqlWeekOfYearDialect`）/ `[文档]`（H2 functions.html ISO_WEEK ISO 定义）。⚠️ H2 无 `WEEKOFYEAR` 函数 `[实测]`；`WEEK(d)` 明文 "uses the current system locale"（弃用） |
| mysql / mariadb / mysql5.7 | `WEEKOFYEAR(d)`（native 同名） | `[文档]`（MySQL 8.0：`equivalent to WEEK(date,3)`，mode 3 = ISO 8601） |
| postgresql / postgis / duckdb | `extract(week from d)`（模板，继承面一体） | `[文档]`（PG 15："The number of the ISO 8601 week-numbering week"）/ DuckDB scratch `extract(week)`=7 |
| oracle / oracle-reverse | `to_number(to_char(d,'IW'))`（模板） | `[文档]`（Oracle 21c 格式模型：`'IW'` = ISO 8601 周号，territory 无关） |
| mssql | `DATEPART(iso_week,d)`（模板） | `[文档]`（T-SQL DATEPART；`iso_week` 不属 SET DATEFIRST 影响面——文档明文仅 week/weekday 受影响）。CI 无 MSSQL 真库 |
| dm / tdengine / db2 / es | **未注册 → 编译期响亮报错**（`ERR_EQL_UNKNOWN_FUNCTION(weekofyear)`） | `[未取证]` |

**语义差异登记（不等价类）**：各库**非 ISO 周**形态差异——mysql `WEEK(d)` 默认 mode 受 `default_week_format` 变量影响（会话依赖）、mssql `DATEPART(week,d)` 受 `SET DATEFIRST` 影响：本归一化**只落 ISO 周**（周一起点 + 含 1 月 4 日周为 W01），不落"locale 周"。跨年边界（12 月末/1 月初）行为见 ISO 定义（`TestEqlWeekOfYearDialect.testH2RealDbIsoWeekBoundaries`：2025-12-29 与 2026-01-01 同属 2026-W01）。

#### dayofweek(d) → ISO-8601 星期几（1=Monday..7=Sunday）

规范计法裁定 = **ISO-8601**（ODBC 计法被拒：h2 `DAY_OF_WEEK` 明文 locale-specific、oracle `TO_CHAR 'D'` 明文 NLS territory 依赖、mssql `DATEPART(weekday)` 受 `SET DATEFIRST` 影响——三个会话漂移点均一手文档明文）。

| 方言 | 输出 | 证据 |
|------|------|------|
| h2 / h2gis | `ISO_DAY_OF_WEEK(d)`（模板，`<testSql>` 真库执行） | `[实测]`（h2 `TestDialect.testFunctions` → 2 + `TestEqlDayOfWeekDialect.testH2RealDbIsoValuePoints`）/ `[文档]`（H2："ISO day of the week (1 means Monday)"） |
| postgresql / postgis / duckdb | `extract(isodow from d)`（模板，继承面一体） | `[文档]`（PG 15："Monday (1) to Sunday (7)... matches the ISO 8601 day of the week numbering"）/ DuckDB scratch `isodow`/`extract(isodow)`=2 |
| mysql / mariadb / mysql5.7 | `(1 + WEEKDAY(d))`（模板） | `[文档]`（MySQL 8.0：WEEKDAY "(0 = Monday, … 6 = Sunday)" ⇒ +1 = ISO）。原生 `DAYOFWEEK` = ODBC 1=Sunday，**值域与归一化分叉，不采用** |
| oracle / oracle-reverse | `TRUNC(d) - TRUNC(d,'IW') + 1`（模板） | `[文档]`（'IW' = ISO 周一，territory 无关；TRUNC(date) 官方示例含 'iw'；DATE-DATE 减法=天数）。`TO_CHAR 'D'` = NLS territory 依赖，弃用 |
| mssql | `((DATEDIFF(DAY, 0, d) % 7) + 7) % 7 + 1`（模板） | `[文档]`（0 = 1900-01-01 星期一；DAY datepart 不属 SET DATEFIRST 影响面；`+7)%7` 修正 1900 前负模）。`DATEPART(weekday)` DATEFIRST 依赖，弃用。CI 无 MSSQL 真库 |
| dm / tdengine / db2 / es | **未注册 → 编译期响亮报错**（`ERR_EQL_UNKNOWN_FUNCTION(dayofweek)`） | `[未取证]` |

**语义差异登记（不等价类，原生同名词值域对照）**：mysql `DAYOFWEEK`=ODBC（1=Sunday..7）、duckdb `dayofweek`=0=Sunday..6、pg `extract(dow)`=0=Sunday..6、H2 `DAY_OF_WEEK`=locale 依赖、oracle `TO_CHAR 'D'`=territory 依赖——**各方言原生 `dayofweek` 同名词≠同值**；EQL 归一化 `dayofweek` 钉死 ISO 值域（h2 真库三点：周一=1/周二=2/周日=7，`testH2RealDbIsoValuePoints`）。

#### dateTrunc(unit, d) → `datetrunc`（unit = 字符串字面量）

**规范单位词表 = pg/duckdb/h2 交集**：`year/quarter/month/week/day/hour/minute/second`（大小写不敏感 `[实测]`）。pg 另有 decade/century/millennium/millisecond 等超集词，**不在规范词表**（规范词表钉死交集，超集按方言实际支持范围登记）。

| 方言 | 输出 | 证据 |
|------|------|------|
| h2 / h2gis | `date_trunc('month', d)`（模板 `date_trunc({0},{1})`，`<testSql>` 真库执行） | `[实测]`（h2 `TestDialect.testFunctions` + `TestEqlDateTruncDialect.testH2RealDbTruncationSemantics`：month→2026-02-01、week→2026-02-09 周一）/ `[文档]`（H2 DATE_TRUNC(datetimeField, dateAndTime)——**带引号字符串单位被接受** `[实测]`，参数序 (unit, value) 与 pg 一致） |
| postgresql / postgis / duckdb | `date_trunc({0},{1})`（同一模板形态，继承面一体） | `[文档]`（PG 15：`date_trunc ( text , timestamp )`，示例引号字符串）/ DuckDB scratch `date_trunc('month', ...)` 通过 |
| mysql / mariadb / mysql5.7 | **未注册 → 编译期响亮报错**（`ERR_EQL_UNKNOWN_FUNCTION(datetrunc)`，负向用例锁定） | `[文档]`（无原生 date_trunc；DATE_FORMAT/MAKEDATE 构造式非无损等价——约束 3 不落） |
| oracle / oracle-reverse | 同上（未注册 → 响亮） | `[文档]`（TRUNC fmt 词表 'MM'/'IW'/'Q' 与归一化词表不同，fixed template 无法按运行时单位值翻译） |
| mssql | 同上（未注册 → 响亮） | `[文档]`（DATETRUNC SQL Server 2022 起存在但 datepart 无字符串接受证据；DATEDIFF 明文拒绝引号字符串 datepart） |
| dm / tdengine / db2 / es | 同上（未注册 → 响亮） | `[未取证]` |

**语义差异登记（不等价类）**：pg `date_trunc` 对 `timestamptz` 输入按会话 TimeZone 截断（时变）；h2/duckdb 对无时区 timestamp 无此时变——timestamptz 场景跨库需自行显式时区处理。周起点：三方 week 截断均为 ISO 周一 `[实测]`（h2 2026-02-10 → 2026-02-09）。

#### dateAdd(unit,n,d) / dateDiff(unit,d1,d2)（字符串单位；dateDiff 锚点 = 边界跨越）

**dateDiff 归一化语义钉死"边界跨越"**（crossed unit boundaries——h2/duckdb 共有语义；例：`dateDiff('MONTH', 2026-01-31, 2026-03-10)` = 2，"完整单位数"语义下为 1）。

| 函数 | 方言 | 输出 | 证据 |
|------|------|------|------|
| `dateadd` | h2 / h2gis | `DATEADD('MONTH',-1,d)`（**WI6 之前既有注册，行为不变**——守护用例逐字节锁定） | `[实测]`（h2 真库 `TestEqlDateAddDateDiffDialect.testH2RealDbAddDiffSemantics`） |
| `datediff` | h2 / h2gis | `DATEDIFF('MONTH',d1,d2)`（既有注册，边界跨越明文） | `[实测]`（同上 + H2 functions.html "number of crossed unit boundaries"） |
| `datediff` | duckdb | `date_diff('month',d1,d2)`（native realName=date_diff；`datediff` 为 DuckDB 官方别名） | `[文档]`（DuckDB："The number of part boundaries between starttimestamp and endtimestamp"——与锚点同语义同参序）/ DuckDB 嵌入式 scratch 实测=1 |
| `dateadd`/`datediff` | mysql / mariadb / mysql5.7 | **未注册 → 编译期响亮报错** | `[文档]`（`DATE_ADD` unit 明文 keyword；`DATEDIFF` 仅天 2 参；`TIMESTAMPDIFF` 完整单位数+keyword unit——语义与语法双不可达） |
| `dateadd`/`datediff` | postgresql / postgis | 同上（未注册 → 响亮） | `[文档]`（interval 算术/`date-date` 减法需运行时单位分支或仅天——fixed template 不可为） |
| `dateadd`/`datediff` | oracle / oracle-reverse | 同上（未注册 → 响亮） | `[文档]`（DS/`NUMTODSINTERVAL` 与 YM/`NUMTOYMINTERVAL` 按单位域双函数分叉，无单模板无损表达） |
| `dateadd`/`datediff` | mssql | 同上（未注册 → 响亮） | `[文档]`（datepart 明文拒绝引号字符串/无字符串接受证据） |
| `dateadd`/`datediff` | dm / tdengine / db2 / es | 同上（未注册 → 响亮） | `[未取证]` |

**语义差异登记（不等价类）**：`dateDiff` 三类语义分叉——边界跨越（h2/duckdb/mssql，归一化锚点）vs 完整单位数（mysql `TIMESTAMPDIFF`）vs 仅天（mysql `DATEDIFF` 2 参 / pg `date-date`）。`dateAdd` 的 interval 算术替代写法（`o.createTime + interval 1 day`）的逐方言模板 owner = WI7b（`sqlIntervalLiteral`）。

### GROUP BY 分组操作符 `ROLLUP(...)`/`CUBE(...)` 与函数 `GROUPING()`（2026-10-04 WI7b）

**语法位**：`group by rollup(a,b)` / `group by cube(a,b)`，支持与普通项混排（`group by a, rollup(b,c)`）。落地形态 = 新 AST 节点 `SqlGroupingOp`（`type: ROLLUP|CUBE` + `args`，`EqlASTKind` ordinal 67 尾部追加）+ `sqlGroupByItem` 前置 label 备选（Literals.g4 词表显式 `ROLLUP`/`CUBE` token + `unreservedWord_` 双登记——标识符位置 `rollup`/`cube` 仍可解析）。`GROUPING SETS` 为**永久否决**（roadmap ledger：MySQL/MariaDB 全版本均无），不注册、无第三语法位。

**放置规则**：ROLLUP/CUBE **仅 GROUP BY 位置合法**——非 GROUP BY 位置的 `rollup(x)`/`cube(x)` 经函数名通路解析为 `SqlRegularFunction`，而三词**永不进入函数表** ⇒ 编译期 `nop.err.eql.unknown-function` 响亮（放置排他 = "GROUP BY 之外无注册"）。

**方言能力门**：`supportRollup` / `supportCube` 两个正交标志（mysql 函数式 ROLLUP 8.4.3+ 与 CUBE 永无的 successor 触发条件不同，故不分立为不合一）；消费点 `EqlTransformVisitor.visitSqlGroupingOp`，不支持时编译期报 `nop.err.eql.dialect-not-support-feature`（`feature=rollup|cube`）。

| 方言 | ROLLUP(...) | CUBE(...) | GROUPING() | 证据 |
|---|---|---|---|---|
| postgresql / postgis / duckdb（继承） | 支持 | 支持 | 支持（1..64 位掩码多参） | `[文档]` |
| oracle / oracle-reverse（继承） | 支持 | 支持 | 支持（1..1 单参） | `[文档]`（23ai） |
| mssql | 支持 | 支持 | 支持（1..1 单参；多列区分用 GROUPING_ID——不注册） | `[文档]`（MS Learn `GROUPING ( <column_expression> )`） |
| h2 / h2gis | 编译期报错 | 编译期报错 | 未注册 → 报错 | `[实测]`（真库探针：H2 2.3.232/2.4.240 × DEFAULT/Oracle/PG 模式全部 `Function "ROLLUP"/"CUBE"/"GROUPING" not found` 90022；transcript 完整归档于开发过程审计归档） |
| mysql / mysql5.7 | 编译期报错（函数式 ROLLUP 8.4.3 才有，超 G-1 基线 8.0.31；successor = 基线上调需求先走 G-1 门） | 编译期报错（全版本无 CUBE，永久口径） | 未注册 → 报错（8.0+ 有 GROUPING，但 EQL 无法为其生成 super-aggregate 行 ⇒ 死能力不注册） | `[文档]` |
| mariadb | 编译期报错（无函数式 ROLLUP） | 编译期报错（无 CUBE，永久口径） | 未注册 → 报错（死能力不注册） | `[文档]` |
| dm / tdengine | 编译期报错 | 编译期报错 | 未注册 → 报错 | `[未取证]` 红线（继承 default=false） |
| db2 | 不可加载（无函数表 → unknown-function） | 同 | 同 | WI3 另案 |

**`grouping()` 注册面**（零文法——函数表 + 既有元数通路 `nop.err.eql.func-too-many/few-args`）：`<native name="grouping" realName="GROUPING">` 落 postgresql/oracle/mssql 三处 + 继承展开共 6 方言；元数按方言分档（pg 系 1..64 位掩码、oracle/mssql 1..1）。**真库证据分派（roadmap EC ⑦）**：h2 = 负向载体（探针 `[实测]` + 负向用例）；oracle/postgresql/duckdb/mssql = 一手文档引证 + 文本断言替代证据（`TestEqlGroupingOpsDialect`）；dm/tdengine `[未取证]` 不计覆盖率。`grouping` 语义级放置校验（参数须引用 GROUP BY 项）**裁定不做**（unknown-function + 元数双闸已兜底；表达式等价比较成本高、防范围蠕动——裁定文档 §4）。

**端到端用例**：`TestEqlGroupingOpsDialect`（parse AST 断言 + 4 方言文本 + 胜者守护 + 全负向 + 约束 4 对照）。

### `count(*) FILTER (WHERE p)`：文档引导替代写法（2026-10-04 WI7b，不做语法）

**引导写法**：需要"按条件计数"时，用条件聚合替代 FILTER 语法——

```
sum(case when <predicate> then 1 else 0 end)
```

**空分组提醒（语义差异，不等价类）**：分组内无行满足 `<predicate>` 时，`sum(...)` 返回 **NULL**（空集求和），而 `count(*) FILTER` 语义返回 **0**——需要 0 语义时套 `coalesce(sum(case when p then 1 else 0 end), 0)`。

**方言支持矩阵**（roadmap ledger 三态；H2 旁证 = WI7b 探针 + `h2.dialect.xml` 保留字表仅存 `FILTER` 关键字）：

| 三态 | 方言 | 证据 |
|---|---|---|
| 已确认有 | PostgreSQL / DuckDB / SQLite | `[文档]` |
| 已确认无 | MySQL / MariaDB / MSSQL / Oracle（其 3 处 `FILTER` 均为 analytic view 的 `FILTER FACT`） | `[文档]` |
| 未取证 | DB2（IBM 文档 403/JS 门禁）、TDengine（无一手文档，红线）、H2（h2database.com 五页零 `FILTER`） | `[未取证]`（不计入"已确认"） |

**为什么不进 EQL 语法**：跨多数方言无损 lowering 不成立（roadmap 约束 3——MySQL/MariaDB/MSSQL/Oracle 全部确认无，做了就是把多数用户推向编译期报错的语法糖）；roadmap WI7b EC ④ 明文"只做文档引导"。写法引导在全方言可编译可执行（普通 `case` 聚合，无方言门）。

### 分页两语法形态：`FETCH FIRST` / `OFFSET..FETCH NEXT`（2026-10-04 WI7）


EQL 接受三种分页输入形态，解析期**归一化**进既有 `SqlLimit{limit, offset}`（`FETCH FIRST n ROWS ONLY` → `limit=n, offset=null`；`OFFSET n ROWS FETCH NEXT m ROWS ONLY` → `offset=n, limit=m`），**不保留输入形态**（AST 无"语法形态"字段——输出形态由方言 `paginationHandler` 配置决定，`IPaginationHandler.buildPageExpr(limit, offset, sql)` 无形态参数）。回显一律输出规范化 `limit x [offset y]` 文本（与 LIMIT 形态同口径，回显文本可重解析语义不变）。

| EQL 输入形态 | LimitOffset 族输出（default/es/h2/h2gis/mysql*/mariadb/dm/tdengine/db2/pg*/duckdb* 等继承） | OffsetFetch 族输出（oracle/oracle-reverse、mssql） |
|------|------|------|
| `limit n [offset m]`（既有） | `LIMIT n [OFFSET m]` | `OFFSET m ROWS FETCH NEXT n ROWS ONLY`（无 offset 时 `OFFSET 0 ROWS FETCH FIRST n ROWS ONLY`） |
| `FETCH FIRST n ROWS ONLY`（WI7 新增） | `LIMIT n` | `OFFSET 0 ROWS FETCH FIRST n ROWS ONLY`（SQL 标准要求 FETCH 前置 OFFSET，handler 自动补桥接） |
| `OFFSET m ROWS FETCH NEXT n ROWS ONLY`（WI7 新增） | `LIMIT n OFFSET m` | `OFFSET m ROWS FETCH NEXT n ROWS ONLY` |

`n`/`m` 均可为字面量或 `?` 参数 marker。证据等级：h2/oracle 双族输出矩阵 `[实测]`（`TestEqlPaginationFetchParse` 12 例 + `TestEqlPaginationFetchCompile` 12 例，含 marker 形态、同语义两形态一致性、跨族形态差异登记）；⚠️ `OFFSET n ROWS`（无 FETCH 的独立 offset 形态）**不接受**、`FETCH FIRST … WITH TIES` 不接受（roadmap 未登记，出现需求先登记 roadmap）；dm/tdengine 的输出继承 default（`LIMIT/OFFSET` 文本），该文本在两库的合法性 `[未取证]`（红线：不计入覆盖率）。h2 自身的 OffsetFetch 配置处于注释态（`h2.dialect.xml:20`，H2 REGULAR 模式两种输出都合法，是否切换属方言契约变更——登记为观察项，现状 = LimitOffset）。

### NULL 三值语义谓词：`IS [NOT] DISTINCT FROM` / `IS [NOT] TRUE/FALSE/UNKNOWN`（2026-10-04 WI8）

EQL 语义钉死 = **标准 SQL 严格三值**（裁定正文 = WI8 Phase 0 裁定文档（开发过程设计归档，2026-10-04）§二/§五；真值表即验收规格——`a IS NOT TRUE` 在 `a IS NULL` 时为 TRUE，**不得降级为二元比较** `a<>0`/`NOT a`，后者在 NULL 行为 NULL 丢语义）。解析期构建归一化，零新 AST 节点（`ast/_gen` 零 diff）：

| EQL 输入 | 规范 AST | 回显（`toSqlString`） |
|---|---|---|
| `a IS NOT DISTINCT FROM b` | `SqlBinaryExpr(operator=SAFE_EQ)`（左右保序） | `a <=> b` |
| `a IS DISTINCT FROM b` | `SqlNotExpr(SqlBinaryExpr(SAFE_EQ))` | `not ( a <=> b )` |
| `a IS [NOT] UNKNOWN` | `SqlIsNullExpr`（`UNKNOWN ≡ NULL` 三值精确等价，直用 `not` prop） | `a is [not] null`（与手写 `IS [NOT] NULL` 逐字节一致） |
| `a IS [NOT] TRUE/FALSE` | 安全词表展开式：`a IS TRUE ≡ a IS NOT NULL AND (a = TRUE)`；`a IS NOT TRUE ≡ NOT(...)`；FALSE 同构 | 展开式（与手写等价式回显逐字节一致） |

**`IS [NOT] TRUE/FALSE/UNKNOWN` 真值表规格（EQL 语义 = 标准 SQL 严格三值，验收规格）**：

| 操作数 a | `a IS TRUE` | `a IS NOT TRUE` | `a IS FALSE` | `a IS NOT FALSE` | `a IS UNKNOWN` | `a IS NOT UNKNOWN` | `a<>TRUE`（禁止降级） |
|---|---|---|---|---|---|---|---|
| TRUE | TRUE | FALSE | FALSE | TRUE | FALSE | TRUE | FALSE |
| FALSE | FALSE | TRUE | TRUE | FALSE | FALSE | TRUE | TRUE |
| NULL | FALSE | **TRUE** | FALSE | TRUE | TRUE | FALSE | **NULL**（失配行） |
| 2（非布尔） | FALSE | TRUE | FALSE | TRUE | FALSE | TRUE | TRUE |

⚠️ **`a IS NOT TRUE` 不得降级为二元比较**（`a<>0`/`NOT a`/truthy 裸条件）：`a IS NULL` 行展开式为 TRUE 而 `a<>0` 为 NULL。展开式复制操作数两份（guard 支 + 等值支）⇒ **操作数含参数标记 `?` 时编译期响亮报错** `nop.err.eql.null-predicate-operand-has-param-marker`（复制会改变参数契约；`IS [NOT] UNKNOWN` 单引用不设门）。

**`IS [NOT] TRUE/FALSE/UNKNOWN` 逐方言矩阵**（展开式为全方言安全形态，无渲染门；证据等级：`[实测]`=`TestEqlNullSafeBooleanPredDialect`）：

| 方言 | 输出 | 证据/分叉登记 |
|---|---|---|
| mysql / mysql5.7 / mariadb | 展开式，布尔字面量渲染 `1`/`0` | `[实测]`（文本）+ `[实测]`（h2 执行矩阵跨方言语义一致性）；⚠️ **MySQL 原生 `IS TRUE` 为数值真值语义**（`2 IS TRUE`=TRUE），EQL 展开式 `2 = 1` 恒 FALSE=钉死严格语义——固有分叉登记约束 4「不等价」 |
| postgresql / postgis / h2 / h2gis / duckdb / mssql | 展开式，布尔字面量渲染 `true`/`false`（h2）/方言形态 | `[实测]`（h2 文本+真库执行）；⚠️ **H2 原生 `2 IS TRUE` 亦为 truthy**（探针实测返回 TRUE）且 `2 = true` 为类型错误（90110）⇒ EQL 展开式在 h2 对非布尔操作数 = **响亮类型错误**（既非原生 truthy 也非钉死 FALSE——响亮分叉登记） |
| oracle / db2 / dm / tdengine | 同样输出展开式（`1`/`0` 形态，安全词表全方言可执行） | 展开式可执行是 EQL 构造事实 `[实测]`（文本面）；各方言原生 `IS TRUE` 行为 `[未取证]`（Oracle 23ai 简写 unverified、DB2/dm/tdengine 无一手文档）——**不主张原生等价**，unverified 纪律不计入覆盖率 |


**`IS [NOT] DISTINCT FROM` 逐方言输出矩阵**（`AstToSqlGenerator.printSafeEq` 渲染三分支，必选件——`printOperator` 全局无方言分支，不覆写则全方言输出 `<=>` 非法 SQL；裁定 §四.1）：

| 方言 | `a IS NOT DISTINCT FROM b` 输出 | `a IS DISTINCT FROM b` 输出 | 证据 |
|---|---|---|---|
| mysql / mysql5.7 / mariadb | `a <=> b` | `not (a <=> b)`（`<=>` 恒二值，`NOT` 良定义） | `[实测]`（`TestEqlNullSafeDistinctDialect` 文本断言，MySQL 手册 `<=>` `[文档]`） |
| postgresql / postgis / h2 / h2gis / duckdb / mssql | `a is not distinct from b` | `a is distinct from b`（**方向文本直出，无 NOT 包装**） | `[实测]`（文本断言；h2 真执行随 Phase 3 端到端补） |
| oracle（**无** transformer 配置时） | 编译期响亮报错 | 同左 | `[实测]`（`TestEqlNullSafeDistinctDialect.testOracleWithoutTransformerFailsLoud`）；生产 beans.xml 已默认注入 transformer，此分支仅存在于显式剔除配置的场景 |
| oracle / oracle-reverse（生产默认 transformer） | **三支全二值 guard 式**展开：`(a IS NOT NULL AND b IS NOT NULL AND a<>b) OR (a IS NULL AND b IS NOT NULL) OR (a IS NOT NULL AND b IS NULL)` | **两支式**展开：`(a IS NULL AND b IS NULL) OR (a IS NOT NULL AND b IS NOT NULL AND a=b)`——**禁止 naive NOT 包装**（单/双 NULL 行 NOT(NULL)=UNKNOWN 丢行） | `[实测]`（`TestEqlNullSafePredTransformerWiring` 工厂默认注入端到端逐字断言 + `testForbiddenNaiveWrapperLosesRowsExecutedOnH2` H2 执行推导）；公式 3VL 验算=验收不变式（裁定 §二.1） |
| db2 / dm / tdengine / sqlite（手构载体）/ 无名方言 | 编译期响亮报错（unverified 纪律——db2 原生格 `[未取证]`（IBM 文档不可达），不计入覆盖率；sqlite 3.39 起原生支持 `[文档]`，EC ④ 版本门负向载体 = 手构 `DialectModel`） | 同左 | `[实测]`（`testUnsupportedConstructedDialectFailsLoud`） |

⚠️ **三节点展开 vs `<=>` 的固有差异（约束 4「不等价」登记，`TestEqlNullSafePredTransformerWiring.testMysqlFormDivergenceVsOraclePinnedForm` + `testForbiddenNaiveWrapperLosesRowsExecutedOnH2`）**：`<=>` 恒二值（`NULL <=> NULL` = TRUE），标准谓词嵌套外层 NOT 时按三值逻辑；EQL 按方言钉死各自正确形态（mysql 直出 `not (a<=>b)`；oracle 展开式禁用 naive NOT 包装——`NOT((a=b) OR (a IS NULL AND b IS NULL))` 在单操作数 NULL 行产生 UNKNOWN 丢行（H2 执行实测 0 行 vs 钉死式 1 行），属禁止形态①）。

**Oracle 展开 transformer（WI4 管线第一个真实生产消费者）**：`io.nop.orm.eql.compile.OracleEqlAstTransformer`（`transformAfterAnalyze` 槽位；方言经已解析表源 → querySpace → `context.getDialectForQuerySpace` 推导，与渲染侧同源；EQL 管线单 querySpace 强制之外的多 querySpace 防御性响亮）。经 `orm-defaults.beans.xml` 默认注入 `SessionFactoryConfig.eqlAstTransformer`（缓存正向参与=`TestEqlNullSafePredTransformerWiring.testOracleExpansionBothDirectionsViaDefaultFactoryConfig` 同键同实例 + transformer 不重复调用；非默认绕过方向=既有 `TestEqlAstTransformerWiring` 不回退）。展开复制操作数（guard 支独立实例）⇒ 操作数含参数标记 `?` 时响亮报错 `nop.err.eql.null-predicate-operand-has-param-marker`（与 IS TRUE/FALSE 构建期展开同一错误码）。`IS [NOT] TRUE/FALSE/UNKNOWN` 不经 transformer（构建期展开已全方言覆盖）。

### `ORDER BY … COLLATE`：显式移出（2026-10-04 WI7 裁定，不落地）

EQL **不接受** `ORDER BY … COLLATE`（文法无赋值路径；`collateClause_` 是 WI2 BUG-15 登记的永久不可达分支）。裁定 = 显式移出 + successor（裁定正文见 EQL 兼容 roadmap WI7 的 Phase 0 裁定文档 §五）：约束 3（不满足"能无损 lowering"的项一律不做）+ 排序原则（接受语法却无法可靠 lowering = BUG-1 同型静默错误风险）。`SqlOrderByItem.collate` 字段保留在冻结生成模型（删除属 `EqlAST.xjava` 变更且无行为收益），继续计入"零生成器消费 AST 字段"清单。

各库 collation 作用域差异（为什么不做的证据面；逐行标证据等级）：

| 库 | 表达式级 `COLLATE` | collation 作用域 | 证据等级 |
|----|-------------------|-----------------|---------|
| PostgreSQL | `expr COLLATE "collation"`（标准位置） | 表达式/列定义/库默认三层 | `[文档]` |
| MySQL | 支持 `expr COLLATE collation`，但子句位置语义与标准不同（且 collation 与 charset 绑定） | 表达式/列/表/库四层 | `[文档]` |
| MSSQL | 表达式级 COLLATE 与列/库级 collation 作用域纠缠（.cast. 场景行为受限） | 列定义/库级为主 | `[文档]`（作用域纠缠点 `[未取证]` 逐案核实） |
| Oracle | 无表达式级 `COLLATE` 子句（12.2+ 有列级 collation）；排序定制走 `NLSSORT(expr, 'NLS_SORT=…')` 函数改写体系 | 列级（12.2+）/`NLSSORT` 函数 | `[文档]` |
| DuckDB / H2 / DM8 / TDengine / DB2 | 未逐个取证 | — | `[未取证]`（红线：不计入覆盖率） |

语义改写通路各库分叉（`NLSSORT` 函数 vs 子句 vs 表达式后缀）⇒ 跨库无损归一不成立。successor = roadmap 单独调研项（触发条件 = "按表达式 collation 排序"的真实产品需求）。

### 死标志台账：14 个存量方言能力标志的三档处置（2026-10-04 WI10b）

roadmap BUG-13 点名的 14 个存量死标志，WI10b Phase 0 终裁后全部落到明确处置（裁定正文：EQL 兼容 roadmap WI10b 的 Phase 0 裁定文档；本表为 live 对账口径）：

| 标志 | 最终档位 | 消费点 / 证据 | 负向用例 / 证明测试 |
|------|---------|--------------|-------------------|
| `supportFullJoin` | 已处置（WI2 交付） | `EqlTransformVisitor.checkJoinTypeFeature` 消费 | `TestEqlFullJoinDialectGate` |
| `supportRightJoin` | **接线**（WI10b） | `checkJoinTypeFeature` RIGHT_JOIN 分支 + `IDialect.isSupportRightJoin` | `TestEqlRightJoinDialectGate`（合成方言负向） |
| `supportRowValueConstructor` | **接线**（WI10b） | `EqlTransformVisitor.visitSqlMultiValueExpr` 门 | `TestEqlRowValueConstructorGate`（合成方言负向） |
| `supportOutputForUpdate` | 档 2：补 accessor + 测试证明（候选 B） | 无运行时消费点（v1 不做 MSSQL OUTPUT lowering；豁免与升级触发条件见裁定文档 §2.7） | `TestDialectDeadFlagDisposition.testOutputForUpdateOnlyMssqlTrue` |
| `supportUpdateTableAlias` | 改档：档 2 行为契约登记 | 无新消费点——EQL 实际行为 = 接受并归一化（见「UPDATE/DELETE 表别名」小节）；多表文法立项时必须接线 | `TestEqlTableAliasSuppressionContract`（flag 两值输出逐字相同） |
| `supportDeleteTableAlias` | 改档：档 2 行为契约登记 | 同上 | 同上 |
| `supportUpdateFromJoin` | 改档：档 2 契约登记 | EQL 文法暂无 UPDATE..FROM（解析失败=响亮）；文法 WI 立项时必须接线 | `TestEqlDeadFlagGrammarContract.testUpdateFromClauseIsLoudParseError` |
| `supportDeleteFromJoin` | 改档：档 2 契约登记 + 补 accessor | EQL 文法暂无多表 DELETE（响亮）；同上触发条件 | `TestEqlDeadFlagGrammarContract`（multi-table delete / DELETE..USING 两例） |
| `supportTruncateTable` | 改档：档 2 能力声明 | EQL **永不**消费（TRUNCATE 进 EQL 为 roadmap ledger 永久否决项）；保留为 JDBC/DAO 层能力声明 | `TestDialectDeadFlagDisposition.testTruncateTableConfigUnchanged` |
| `supportILike` | 改档：档 2 契约登记（**禁止接标志**） | 无标志消费点；ilike 真实门 = 函数表（见「ilike 函数注册」小节）。⚠️ 台账口径：**不得登记为"已接线"**——WI3 交付的是函数注册，不是标志接线 | `TestDialectDeadFlagDisposition.testILikeFlagAndFunctionRegistrationIndependent` |
| `supportSavePoint` | 软弃用 | 无消费点（xdef `@deprecated`：预留未接线、保留配置兼容、勿新增消费依赖） | `TestDialectDeadFlagDisposition.testSoftDeprecatedFlagsStillBind`（配置加载回归） |
| `supportTransaction` | 软弃用 | 同上 | 同上 |
| `supportSequence` | 软弃用 | `isSupportSequence` 唯一调用方为测试；序列 SQL 供给走 `getCreateSequenceSql`/`getSequenceNextValSql` | 同上 |
| `supportNullsFirst` | **接线**（2026-10-04 WI7，successor 移交兑现） | `IDialect.isSupportNullsFirst` + 消费点 `AstToSqlGenerator.visitSqlOrderByItem`（子句输出唯一门，三分形态：缺省不补/请求即打印/一致省略或冲突报错）；门行为与文档准入集见「defaultNullsFirst 与 supportNullsFirst」小节 | `TestEqlNullsFirstDialectGate`（行为矩阵）、`TestDefaultNullsFirstFlag`（accessor 双侧契约：true 侧 duckdb/h2/postgresql/oracle、false 侧 mysql/tdengine 未配置→null→false） |

配置断言总入口：`TestDialectDeadFlagDisposition`（nop-dao，9 例——accessor 链 × 既有 XML 值逐一锁定，方言 XML 零改值）。

### DAO 批量 upsert 原语（2026-10-05 WI11：`IJdbcTemplate.upsertBatch` + `UpsertStatements`）

跨库 upsert 的正路是 **DAO 层原语**（roadmap「明确不做的事」：UPSERT 不进 EQL 语法层），不是各消费方私有实现，也不是"INSERT 撞键后补 UPDATE"的两段式。原语三件套（均在 `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/`，可 grep）：

| 面 | 载体 | 职责 |
|---|---|---|
| 执行面 | `IJdbcTemplate.upsertBatch(querySpace, sqlName, tableName, columns, conflictColumns, updateColumns, rows, policy)`（default 方法） | 按 querySpace 方言生成单语句原子 upsert 并执行；native 路径 = 单 REQUIRED 事务内逐行执行同一条语句文本（批级一致性 = 事务原子性）；无原生方言按 `policy` 显式分流 |
| 静态面 | `UpsertStatements`（final 工具类） | ① `resolveUpsertStyle(IDialect)`：方言名三族前缀（`postgresql`/`pg`、`mysql`/`mariadb`、`h2`，大小写不敏感）→ `UpsertStyle`，无原生支持返回 **null = 显式契约信号**（调用方必须分支，有测试断言）；② `buildNativeUpsertSqlText(...)`：单语句 upsert 文本（key-only 空更新集三形态）；③ `isDuplicateKeyException(IDialect, Throwable)`：三段重复键判定（下文） |
| 策略 | `UpsertPolicy`（枚举，**必填无默认值**） | `NATIVE_ONLY` = 无原生方言时抛 `nop.err.dao.sql.feature-not-supported`（响亮）；`FALLBACK_INSERT_UPDATE` = 逐行"INSERT 单独事务 → 判重 → UPDATE 新事务"回落 |

方言策略矩阵（证据等级列）：

| 方言（名前缀） | upsert 语句形态 | duplicate-key 登记（翻译面） | 证据等级 |
|---|---|---|---|
| PostgreSQL（`postgresql`/`pg`） | `INSERT .. ON CONFLICT (kcols) DO UPDATE SET c = EXCLUDED.c`；key-only → `DO NOTHING` | `23505` | 形态单测 `[实测]`；语法 `[文档]`；真库 `[未取证]` |
| MySQL/MariaDB（`mysql`/`mariadb`） | `INSERT .. ON DUPLICATE KEY UPDATE c = VALUES(c)`；key-only → 首个冲突键自赋值 no-op | `1062` | 形态单测 `[实测]`；语法 `[文档]`；真库 `[未取证]` |
| H2（`h2`） | `MERGE INTO t (cols) KEY (kcols) VALUES (..)`（2.4.240 真执行实证；PG 形态 `ON CONFLICT .. EXCLUDED` 在 H2 2.3.232 有语法错误坑，故不用） | `23001,23505` | **真库实测**（CI 载体，`TestUpsertPrimitiveH2`） |
| 其余全部方言（mssql/oracle/db2/dm/tdengine/duckdb/…） | 无原生分支：`NATIVE_ONLY` 抛错 / `FALLBACK_INSERT_UPDATE` 两事务回落 | 逐方言 XML `<errorCodes>`（8 个方言登记） | DB2 `-803` = 结构证据 `[实测]`（`TestDialect#testDb2DuplicateKeyTranslationChain`，翻译链单测）+ 真库 `[未取证]`；DM/TDengine `[未取证]`（红线：无一手文档） |

错误翻译复用契约（零新增错误码、零翻译注册改动）：

- 冲突类异常的归一化仍走既有链：真执行 `SQLException` → `DialectSQLExceptionTranslator`（方言 XML `<errorCodes>` vendorCode/SQLState 查表）→ `nop.err.dao.sql.duplicate-key`。
- 原语回落路径的判重谓词 = **三段判定**（`UpsertStatements.isDuplicateKeyException`）：① 翻译器优先（8 方言登记面精确归一化，含旧决策表漏判的 H2 `23001`、DB2 `-803`、DM、TDengine）；② **SQLState-23505 无条件兜底**（未登记方言；保住 oracle 方言模型 + H2 后端的碰撞路径——`TestJdbcCheckpointStorage#testGenericDialectFallbackSeparateTransactionUpsert` 回归锚）；③ SQLState-23000 + 已知 vendorCode ∈ {1062, 1022, 2627, 2601, 1}（方言模型缺 `<errorCodes>` 时不退化；**MySQL 1022 为旧决策表独有，显式保留**）。`getCause()` 链与 JDBC `getNextException()` 链都走。非 duplicate 一律原样上抛（false negative 方向失败 = 安全方向）。
- 既有 8 文件 / 11 处"INSERT 撞键后判错误码"调用点（`DbLoginAttemptStore`/`DbSendCodeRateLimiter`/`DbEmailCodeStore`/`DbSmsCodeStore`/`DaoBatchStateStore`/`RetryEngineImpl`/`SysSequenceGenerator`/`SysDaoResourceLockManager`）**不受影响**，两段式模式仍是合法用法。

事务语义与**不保证项**（显式化，勿夸大）：

- native 路径批级原子（单事务逐行）；**FALLBACK 路径仅行级原子**（行粒度两事务——整批单事务回落会在混合批上静默丢新键行，禁）。
- 回落 INSERT→UPDATE 两事务之间存在窗口（非原子），无原生方言上的能力上限；一致性硬需求用原生三族方言。
- 冲突键列集须真实对应表上唯一约束（原语不校验 DDL；H2/PG 上无唯一键时 `MERGE`/`ON CONFLICT` 的行为差异属建表契约）。
- 方言解析失败（querySpace 无方言）= 类型化错误 `nop.err.dao.unknown-query-space`（两种策略一致——`executeUpdate` 本身无条件解析方言，回落无意义）。

迁移说明（`IUpsertHandler` 2026-08-22 删除 → WI11 重引入）：

- **为什么旧 handler 挂载形态不再采用**：旧 `IUpsertHandler`/`MysqlUpsertHandler`（commit `3f1111078d`，2026-08-22 删除）唯一实现恒返回 null、全仓零调用方——空壳；且 `nop-dao` 无 codegen 管线、`_DialectModel.upsertHandler`（冻结 String 字段、全仓零消费者）无装配通路，handler 挂载形态无法复活为有行为的契约。`dialect.xdef` 的 `<upsertHandler>` 元素保留仅为配置兼容（同文件 javadoc 已注记，勿新增消费依赖）。
- **新原语与旧接口的差异**：能力挂在 `IJdbcTemplate`（消费方已有引用即可用，default 方法零实现方破坏）而非 dialect 模型配置；策略显式必选；重复键判定复用翻译链而非独立决策表；连同真库集成测试一起交付（删除注记的硬要求）。
- **升级影响**：旧接口零既有消费者 ⇒ 零迁移面；无原生 upsert 方言的行为变化点 = 原来各消费方私有的 GENERIC 回落/响亮拒绝语义现由 `UpsertPolicy` 显式表达（nop-stream 两私有枚举已下线迁移，`JdbcCheckpointStorage` 三使用点 = FALLBACK_INSERT_UPDATE 行为等价，`JdbcTwoPhaseCommitSink` keyed 路径 = 静态面文本 + 原响亮拒绝）。

测试载体（全部真实执行，无 mock）：`TestUpsertStatements`（静态面 10 例：三方言形态/key-only/保留字转义/三段判定逐段隔离/双链遍历/结构化字段纪律）、`TestUpsertPrimitiveH2`（H2 真库 9 例：插入/更新/冲突两腿/混合批/NATIVE_ONLY 响亮/key-only 拒绝/check 约束不误判）、`TestDialect#testDb2DuplicateKeyTranslationChain`（DB2 `-803` 结构证据）。

### `MERGE`/`UPDATE..FROM`：显式移出（2026-10-05 WI11 裁定，EQL 语法层不做）

EQL 语法层**不做** `MERGE` 语句语法（与「明确不做的事」UPSERT 不进语法层同批裁定；跨库 upsert 语义由上文 DAO 批量 upsert 原语承载——那是 JDBC 语句文本生成，不经 EQL 文法，两者不矛盾）。successor = 未来 EQL `MERGE` 语法 WI（plan-first：`.g4` 生成管线）；触发条件 = 出现 EQL 层 `MERGE` 语义（条件 upsert / `WHEN MATCHED THEN DELETE` / `BY SOURCE`）的真实产品需求 + 最低版本门裁决（已知分叉：PG 15 `MERGE` 无 `BY SOURCE` 需 17；MSSQL `MERGE` 强制终止 `;`，error 10713；Oracle `WHEN MATCHED THEN DELETE` 三版文档确认存在）。`UPDATE..FROM` 半行维持 WI10b 改档结论（EQL 文法无此语法 = 解析响亮失败 + 契约登记，见「死标志台账」`supportUpdateFromJoin` 行），不重开。

## 相关文档

- `orm-model-design.md` — ORM 模型设计规范（stdSqlType / stdDataType、主键、关系、字典）
- `model-first-development.md` — VARCHAR precision 自动选择、代码生成链路
- `05-examples/sql-lib-and-mapper.java` — sql-lib 文件结构、XPL 参数化规则、Mapper 接口、**rowType DTO 封装**
- `orm-model-design.md` — Sql-Lib Mapper 自动生成（`tagSet="mapper"`）
- `04-reference/debugging-checklist.md` — EQL / SQL-lib 调试排障速查
- `logical-deletion.md` — 逻辑删除（`deleted` / `delVersion`）
