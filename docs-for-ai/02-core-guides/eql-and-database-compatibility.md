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

**迁移说明（新关键字）**：`RANGE` / `WINDOW` / `GROUPS` / `ROW` / `PRECEDING` / `FOLLOWING` / `UNBOUNDED` 已新增为关键字，并同时登记为非保留字（unreserved word）——既有查询把它们用作列名 / 别名的写法不受影响；但 framed 窗口表达式中应避免以这些词做标识符。`LATERAL` 亦已登记为非保留字（见下节）。

#### LATERAL 关联子查询表源（2026-10-04 修复，plan 2307 V1）

语法层接受 `FROM t1, LATERAL (SELECT ... WHERE ... = t1.col) alias` 形态（`sqlSubqueryTableSource` 的 `lateral=LATERAL?` 前缀，AST 字段 `SqlSubqueryTableSource.lateral`）。语义分析阶段该标记是**生效**的：写 `LATERAL` 时子查询可引用同层级 FROM 项（`EqlTransformVisitor` 在此情形把当前作用域换成同级 `tableScope`）；不写 `LATERAL` 时引用同级别名会报 `nop.err.eql.unknown-alias`。

**修复前的缺陷**：输出器从不打印 `LATERAL` 关键字，导致 `from t1, lateral (...) x` 被编译成缺关键字的 `from t1, (...) x`——语义分析认了 LATERAL、输出没认的静默错误，在 PostgreSQL / MySQL 8 / Oracle 等库上直接语法报错。现已由 `AstToEqlGenerator#visitSqlSubqueryTableSource` 输出该关键字修复。

**方言可用性**：本框架**不**为 LATERAL 设立专用能力位，因此不存在编译期门禁——产物在**不支持 LATERAL 的数据库上由数据库自身报错**（响亮失败，非静默错误）。已实测 **H2 2.3.232 完全不支持 `LATERAL` 关键字**（逗号连接与 `JOIN LATERAL` 两种写法均报 `Function "LATERAL" not found`），且逗号连接的派生表**不**支持隐式 lateral（报 `Column ... not found`）。⇒ **H2 上不要写 LATERAL**；需要关联子查询语义时，用相关标量子查询（`(SELECT ... WHERE ... = t1.col)`）或 `EXISTS`，二者在 H2 与 PostgreSQL 上均可执行且语义等价。

**迁移说明**：`LATERAL` 关键字此前**未**登记为非保留字，即 `select lateral from t` 会解析失败。现已登记（与 `TUMBLE` / `RANGE` / `WINDOW` 同一范式），故 `lateral` 可正常用作列名 / 表别名，既有查询不受影响。

#### CROSS JOIN 与 join 过滤条件（2026-10-04 修复，plan 2307 V2）

语法层接受 `CROSS JOIN`：`from A a cross join B b`（等价于逗号连接的笛卡尔积）。CARTESIAN 语义在 EQL 中有两条等价写法——逗号连接与 `CROSS JOIN`——产出等价结果。

**修复前的缺陷**：`CROSS` 登记为非保留字（允许 `cross` 作标识符）且表别名可选，导致 `from AppUser u cross join AppRole r` 被 ANTLR 误解析——`cross` 被吃成表别名（`from AppUser as cross join ...`），而 `cross join` 只剩下一个裸 `JOIN`。后果是源查询里对 `u` 的任何引用都报 `nop.err.eql.unknown-column-name`，一条本应合法的查询无法执行；多表链式写法还会额外产生幻觉别名（`a cross join b as cross join c`）。

**CROSS JOIN 的过滤条件去向（重要语义）**：`CROSS JOIN` 不接受 `ON` 子句。租户 / 逻辑删除 / 实体固定过滤器因此**统一落到 `WHERE`**，而不是 `ON`——`cross join T on (...)` 是非法 SQL（实测 H2 / PostgreSQL 均拒绝）。最终产物形如：

```sql
select ... from SIMS_COLLEGE as c cross join SIMS_CLASS as s
where (c.TENANT_ID = ?) and (s.TENANT_ID = ?)
```

这与「笛卡尔积后再过滤」等价，**租户隔离不因CROSS JOIN 而失效**。其余 join 类型（`JOIN` / `LEFT JOIN` / `RIGHT JOIN` / `FULL JOIN`）的缺省过滤仍然写在 `ON` 中，且**实体 join 漏写 `ON` 仍会报 `nop.err.eql.join-no-condition`**——CROSS JOIN 是该校验唯一豁免的 join 类型。

**已知边界**：`from a cross join b on <cond>`（给 CROSS JOIN 写 `on`）被解释为「a 的别名是 `cross` + 带 `on` 的普通 join」，而非 CROSS JOIN。`CROSS JOIN ... ON ...` 本就不是标准 SQL 写法。链式 `a cross join b cross join c` 产出 `a cross join b join c`（末尾的无条件 `JOIN` 语义等价于 CROSS JOIN），不是逐字round-trip。

#### 不被支持的写法（2026-10-04，plan 2307 V3 / V4）

EQL 的语法与 AST 有一条硬约束：**一个构造要么能被打印成正确的目标 SQL，要么必须在解析期被拒绝**。不允许"能解析、但不输出关键字 / 输出畸形标识符 / 编译产物为空"这种中间态——它把错误从编译期推迟到执行期，且往往以"看起来跑通了"的形态出现。

**集合聚合访问 `coll[where orderBy].func(args)` 已从语法中移除**。该构造无输出器、无语义分析，解析成功后会被打印成 `where collx > 1 > 2`（`[...]`、`.func()` 与主节点全部丢失）。它的语义是**聚合量词**（`count`/`sum`），与集合算子 `_some`/`_all` 的**存在性量词**不等价，因此不能降级映射——`count(coll) > 2` 与 `exists(coll)` 语义不同。现在该写法是**解析错误**。

需要集合上的存在性判断时用 `_some` / `_all`：

```
// 存在至少一个满足条件的成员
where o.roles._some.userId = 'x'
// 存在全部成员都满足条件（NOT EXISTS 反例）
where o.roles._all.userId = 'x'
```

需要聚合值时，把聚合下推到 SQL 表达式或子查询，不要指望 `coll[...].count()`。

**事务控制语句 `commit` / `rollback` 显式失败**。事务边界归属 `ITransactionTemplate` / 连接层，不属 EQL 编译器职责。编译这两条语句会抛 `nop.err.eql.not-support-transaction-statement`，错误信息指向正确 API。此前它们能解析但编译产物是**空字符串**且不报任何错误（静默无操作）。

#### ORDER BY 空序 `NULLS FIRST/LAST`（2026-10-04 plan 2308 支持）

EQL 的 `ORDER BY` 项现在接受空序修饰：`order by o.col [asc|desc] [nulls first|nulls last]`。
省略子句时产物与从前逐字一致（不写即不输出）。

**输出策略只看一个能力位 `supportNullsFirst`**，分两条路：

- **true** → 原样输出 `NULLS FIRST` / `NULLS LAST` 子句。
- **false** → 改写为「`CASE WHEN` 排序键 + 原排序列」的前置同级键。

**⚠️ 方言矩阵（每格的证据来源不同，不要当成同质结论）**

| 方言 | `supportNullsFirst` | 走哪条路 | 证据 |
|---|---|---|---|
| h2 | `true` | 原生 | 实测：H2 2.3.232 真实执行四方向组合核对行序 |
| postgresql（含 postgis） | `true` | 原生 | 实测：PostgreSQL 16.15 同上 |
| duckdb | `true` | 原生 | 官方文档 + 本仓库既有声明（**探针脚本未含 duckdb 分支，非本计划实测**）；**但 ASC/DESC 都不翻转缺省**（见下方警告） |
| oracle（含 oracle-reverse） | `true` | 原生 | 官方文档明确支持；**本仓库未跑容器实测** |
| mysql | `false` | CASE 改写 | 实测：MySQL 8.0.46 **不接受** `NULLS` 子句 |
| mysql5.7 / mariadb | `false` | CASE 改写 | 保守缺省（未实测，继承/对齐 mysql） |
| dm / tdengine / es | `false` | CASE 改写 | 保守缺省（无达梦/tdengine 环境，未实测） |
| db2 | `false` | CASE 改写 | 保守缺省（未实测；DB2 T-SQL 系无 `NULLS` 子句） |
| mssql | `false` | CASE 改写 | 保守缺省（未实测；SQL Server 无 `NULLS FIRST/LAST` 语法） |

> **未实测为何仍安全**：`false` 走 CASE 改写在**任何**方言上都语义正确，只是 SQL 略冗长。
> 这正是该能力位被设计成「支持/不支持」而非「四模型枚举」的原因——不确定时填 `false` 不会算错。

**⚠️ 不要自己推断「请求值 == 方言缺省就省略子句」**。DuckDB 是反例：它 `ASC` 与 `DESC`
**都不翻转**空序缺省（`ASC`/`DESC` 一律 NULLS LAST）。若按「DESC 默认 NULLS FIRST」这类
单布尔模型去省略，`order by x desc nulls first` 会被判成「缺省即满足」而省略，
DuckDB 实际返回 NULLS LAST——静默给出错误行序。因此本框架**不做省略优化**，
`defaultNullsFirst` 这个历史属性全程不读。

**三类位置会响亮失败**（抛 `nop.err.eql.nulls-order-not-supported`，不静默忽略）：

| 场景 | `reason` |
|---|---|
| 改写路径遇到窗口上下文（`OVER (...)` / 命名窗口 `WINDOW w AS (...)` 内） | `window-not-supported` |
| 改写路径遇到非纯列引用（函数、算术、`order by 1` 序号等无法安全复制的表达式） | `expression-not-duplicable` |
| 改写路径遇到 `SELECT DISTINCT`（**无论排序列是否已投影**） | `distinct-not-supported` |

原生方言下这三类均放行（数据库自身能处理，不需要改写）。多键 `ORDER BY` 时，
CASE 键插在**紧邻其排序项**的位置，不改变键间优先级。

> **为什么 `DISTINCT` 是整类拒绝而不是「排序列已投影即可」**：实测 PostgreSQL 16，
> `select distinct v from t order by (case when v is null then 0 else 1 end), v` 即便 `v`
> 已在投影列表中仍报 `for SELECT DISTINCT, ORDER BY expressions must appear in select list`
> ——注入的 `CASE` 是一个**新的** ORDER BY 表达式，不在 select list 里。
> 所以「排序列已投影」只是必要条件，不足以让改写合法。

**`ORDER BY ... COLLATE` 仍不支持**。注意区分：`CAST(x AS type COLLATE y)` 中的
`COLLATE` 是**支持的**，它属于类型修饰而非排序规则。

**另一条通道（当前有缺陷，勿照搬本页描述）**：bean 路径
`QueryBean.orderBy(...)` / `OrderFieldBean.nullsFirst`（`DaoQueryHelper` / `SQL.SqlBuilder`）
是**独立于 EQL 的另一套实现，完全没有方言门控**——`SQL.SqlBuilder` 全类 0 处 dialect 引用，
空序修饰无条件拼接。因此**在 MySQL / MariaDB / DM / tdengine / SQL Server 上经该路径写
`nullsFirst` 会产出语法错误的 SQL**。`nop-metadata` 的聚合查询路径（`AggregationHelper`）
只对 `"MySQL"` 做了特判，其余方言同样无条件拼接。
**在修复前，请只在 EQL 通道使用空序修饰。** 修复归属
`ai-dev/plans/2309-bean-orderby-nulls-first-gating.md`。

#### TUMBLE 时间切片伪表函数（2026-10-03 WI17 扩展，stream-only）

语法层现接受 `TUMBLE(表名.时间列, INTERVAL <n> <单位>)` 作为 FROM 表源的一个变体（`sqlTumbleTableSource`，AST 类 `SqlTumbleTableSource`），固定单位 `SECOND`/`MINUTE`/`HOUR`/`DAY`/`WEEK`（`MICROSECOND` 仅整毫秒接受）。时间列以 bigint epoch-millis 承载。INTERVAL 字面量必须为正的固定时长——非字面量 / 非正数 / 日历单位（`MONTH`/`QUARTER`/`YEAR`）提取时长时 fail-fast（`nop.err.eql.invalid-interval-value`）。

**范围限定**：TUMBLE 是流执行目标的 stream-only 语法（时间切片窗口，D4 裁定），ORM/RDBMS 编译通道不支持该表源——作用域注册与表元数据解析均 fail-fast（`nop.err.eql.table-source-not-resolved`），`toSQL()` 的 TUMBLE 渲染归 WI20（round-trip 不对称在案：勿用 toSQL 断言 TUMBLE 查询）。`TUMBLE` 关键字同时登记为非保留字——既有查询把 `tumble` 用作列名 / 表名的写法不受影响。`HOP`/`SESSION` 伪表函数未进入语法层。

**frame 能力开关（2026-10-02 WI2）**：frame 三单位分别对应方言能力位 `supportWindowFrameRows` / `supportWindowFrameRange` / `supportWindowFrameGroups`（dialect `<features>` 下发，default.dialect.xml 集中缺省 `false`）；翻译期未启用抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`。各方言启用值以 WI3 实跑矩阵产出为准，勿凭文档宣示开启。**已实测开启（2026-10-02 WI3 实跑矩阵，H2 2.4.240 与 PostgreSQL 16 七组全 PASS）**：h2 与 postgresql 三能力位全开；h2gis（经 h2）、postgis 与 duckdb（经 postgresql）经 x:extends 继承同为全开。 **未开启（frame 查询翻译期 fail-fast，断言覆盖）**：dm、mariadb、mssql、mysql、mysql5.7、oracle 共 6 个。

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
| `supportNullsFirst` | 是否支持 `NULLS FIRST/LAST` | 支持则原样输出；不支持则改写成 `CASE WHEN` 排序键 |
| `supportDeleteTableAlias` | DELETE 语句是否允许表别名 | MySQL 不允许 |
| `supportUpdateTableAlias` | UPDATE 语句是否允许表别名 | MySQL 不允许 |
| `supportILike` | 是否支持 `ILIKE` 大小写不敏感匹配 | PostgreSQL 支持 |
| `supportSequence` | 是否支持数据库序列 | Oracle 支持，MySQL 不支持 |
| `supportReturningForUpdate` | UPDATE 是否支持 `RETURNING` 子句 | PostgreSQL 支持 |
| `supportFullJoin` / `supportRightJoin` | 是否支持 FULL / RIGHT JOIN | 部分数据库不支持 |
| `supportWithAsClause` | 是否支持 CTE（WITH AS） | MySQL 8.0+ 支持 |
| `supportBatchUpdate` | 是否支持批量更新 | 大部分数据库支持 |

> 完整标志列表见 `_DialectFeatures.java`。这些标志由 dialect 模型文件（`_vfs/nop/dao/dialect/*.dialect.xml`）配置，开发时不需要手工干预。

## 相关文档

- `orm-model-design.md` — ORM 模型设计规范（stdSqlType / stdDataType、主键、关系、字典）
- `model-first-development.md` — VARCHAR precision 自动选择、代码生成链路
- `05-examples/sql-lib-and-mapper.java` — sql-lib 文件结构、XPL 参数化规则、Mapper 接口、**rowType DTO 封装**
- `orm-model-design.md` — Sql-Lib Mapper 自动生成（`tagSet="mapper"`）
- `04-reference/debugging-checklist.md` — EQL / SQL-lib 调试排障速查
- `logical-deletion.md` — 逻辑删除（`deleted` / `delVersion`）
