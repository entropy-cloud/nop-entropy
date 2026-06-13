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

EQL 支持标准 SQL 的全部子句：`SELECT` / `FROM` / `WHERE` / `GROUP BY` / `HAVING` / `ORDER BY` / 子查询 / 窗口函数。

与 JPQL 的区别：JPQL 需要手写 `JOIN FETCH`，EQL 直接点属性名即可自动展开关联表。

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
| `supportWithAsClause` | 是否支持 CTE（WITH AS） | MySQL 8.0+ 支持 |
| `supportBatchUpdate` | 是否支持批量更新 | 大部分数据库支持 |

> 完整标志列表见 `_DialectFeatures.java`。这些标志由 dialect 模型文件（`_vfs/nop/dao/dialect/*.dialect.xml`）配置，开发时不需要手工干预。

## 相关文档

- `orm-model-design.md` — ORM 模型设计规范（stdSqlType / stdDataType、主键、关系、字典）
- `model-first-development.md` — VARCHAR precision 自动选择、代码生成链路
- `05-examples/sql-lib-and-mapper.java` — sql-lib 文件结构、XPL 参数化规则、Mapper 接口
- `04-reference/debugging-checklist.md` — EQL / SQL-lib 调试排障速查
- `logical-deletion.md` — 逻辑删除（delFlag / delVersion）
