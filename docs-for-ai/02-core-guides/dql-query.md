# DQL 查询：结构化维度查询

## 三层查询体系

Nop ORM 提供三种查询方式，按抽象层级从低到高：

| 方式 | SQL 类型 | 描述 | 执行器 |
|------|---------|------|--------|
| `<sql>` 原生 SQL | `sql` | 原生 SQL 文本，透传 JDBC 执行 | `IJdbcTemplate` |
| `<eql>` 实体查询 | `eql` | EQL 文本，实体/属性名自动映射 + JOIN 展开 | `IOrmSession` |
| `<query>` DQL 查询 | `query` | QueryBean 结构化查询模型，支持主子表维度对齐 | `MdxQueryExecutor` |

DQL（`<query>` 方式）是最上层抽象：用 `QueryBean` 对象描述"查什么、怎么聚合、按什么维度对齐"，框架自动拆分为多个子查询、执行后内存合并。

---

## QueryBean：DQL 的结构化表示

`io.nop.api.core.beans.query.QueryBean` 是 DQL 的核心模型：

```java
public class QueryBean {
    String sourceName;          // 主实体名 (FROM)
    List<QueryFieldBean> fields; // SELECT 字段列表
    List<QuerySourceBean> joins; // 显式 JOIN 声明
    TreeBean filter;             // WHERE 条件树
    List<OrderFieldBean> orderBy;
    List<GroupFieldBean> groupBy;
    List<String> dimFields;      // 维度对齐字段
    long offset, int limit;      // 分页
    boolean distinct;
    Integer timeout;
    boolean disableLogicalDelete;
}
```

每个 SELECT 字段由 `QueryFieldBean` 描述：

```java
public class QueryFieldBean {
    String name;      // 字段名（属性名）
    String owner;     // 所属者（空=主表，非空=子表别名或关联属性名）
    String aggFunc;   // 聚合函数: count/sum/avg/min/max
    String alias;     // 别名（覆盖 label）
    String label;     // 显示名（默认同 name）
}
```

---

## 三种使用方式

### 方式一：Java API 编程构建

通过 `IOrmTemplate.findListByQuery(QueryBean)` 直接执行：

```java
@Inject
IOrmTemplate ormTemplate;

QueryBean query = new QueryBean();
query.setSourceName("SimsCollege");
query.addField("shortName");
query.addField(QueryFieldBean.forField("id").aggFunc("count").alias("classCount")
    .owner("simsClasses"));
query.setLimit(20);
query.addOrderField("shortName", true);

List<Map<String, Object>> list = ormTemplate.findListByQuery(query);
```

这个 DQL 等价于：

```
-- 对主表 SimsCollege 分页查询
SELECT o.SHORT_NAME, ... FROM SIMS_COLLEGE o ORDER BY o.SHORT_NAME ASC
-- 对子表 simsClasses，按 collegeId 维度对齐后聚合
SELECT c.COLLEGE_ID, COUNT(c.ID) FROM SIMS_CLASSES c
WHERE c.COLLEGE_ID IN (:pageDimValues) GROUP BY c.COLLEGE_ID
```

`owner="simsClasses"` 中的 `simsClasses` 是 `SimsCollege` ORM 模型上一对多关系的属性名。框架自动从 ORM 模型获取关联配置（JOIN 条件、目标实体）。

### 方式二：sql-lib.xml `<query>` 标签

在 `*.sql-lib.xml` 中声明命名查询：

```xml
<!-- 位置: resources/_vfs/nop/auth/sql/NopAuthUser.sql-lib.xml -->
<sqls>
    <query name="queryGroupWithDeptCount" sqlMethod="findList">
        <source>
            <sourceName>NopAuthUser</sourceName>
            <fields>
                <field name="groupId"/>
                <field name="name"/>
                <field owner="deptMappings" name="deptId"
                       aggFunc="count" alias="deptCount"/>
            </fields>
            <filter>
                <c:if test="${not empty status}">
                    <eq name="status" value="${status}"/>
                </c:if>
            </filter>
            <orderBy>
                <field name="groupId" desc="false"/>
            </orderBy>
        </source>
    </query>
</sqls>
```

通过 `SqlLibManager` 或 Mapper 接口调用：

```java
// 通过 Mapper 接口
@SqlLibMapper("/nop/auth/sql/NopAuthUser.sql-lib.xml")
public interface NopAuthUserMapper {
    List<Map<String, Object>> queryGroupWithDeptCount(
        @Name("status") Integer status);
}

// 或通过 SqlLibManager
SqlLibManager.getInstance().invoke(
    "io.nop.auth.sql.NopAuthUser.queryGroupWithDeptCount",
    range, context);
```

### 方式三：GraphQL/REST 通过 CrudBizModel

前端或外部调用：

```graphql
query {
    NopAuthUser__findList(query: {
        filter: {
            status: { $eq: 1 }
        },
        orderBy: ["userName desc"],
        limit: 20
    }) {
        items { userId userName nickName }
    }
}
```

`QueryBean` 参数可通过 GraphQL 变量传递完整的结构化查询。

---

## 主子表维度对齐（DQL 核心）

当 `QueryBean.fields` 中某个字段设置了 `owner`，该字段属于子表。`MdxQueryExecutor` 自动处理主子表分离执行 + 内存合并。

### 执行流程

```
QueryBean (含 owner 字段)
  → MdxQuerySplitter.split() 拆分为主查询 + N 个子查询
    → 主查询: sourceName + 无 owner 字段 → SQL → JDBC 执行 → 结果集
    → 子查询1: owner=关联属性 → 自动获取 ORM 关联配置 → SQL → JDBC 执行
    → 子查询N: ...
      → 内存 Hash Join: 按 dimFields 维度字段做对齐
        → 主表每行按 dimValue 建立 Map<dimValue, Map<String,Object>>
        → 子表按 dimValue 匹配后，字段值合并到主表行
```

### 主查询属性透传

`MdxQuerySplitter.getMainQuery` 把 `QueryBean` 复制成一个新 bean（子查询不共享原查询状态），因此**只有显式复制的属性才会到达 SQL**。`DaoQueryHelper.queryToSelectFieldsSql` 会消费 `distinct`、`leftJoinProps`、`disableLogicalDelete`、`name`；任何一个漏抄都会让主表查询语义静默丢失（例如 `distinct` 漏抄会退化成普通 `SELECT`，结果出现重复行）。新增 `QueryBean` 属性时必须同步补进 `getMainQuery`。

透传清单：`name`、`sourceName`、`filter`、`cursor`、`groupBy`、`orderBy`、`limit`、`offset`、`distinct`、`leftJoinProps`、`disableLogicalDelete`。

`groupBy` 是**复制**而非透传：`QueryBean.addGroupField` 会就地追加到列表上，直接透传引用会改写调用方的集合。

### 结果字段名必须唯一

主表和子表字段最终写入**同一个** `Map<String, Object>`，而 `QueryFieldBean.getLabel()` 不含 owner 信息：

| 情况 | label |
|------|-------|
| `mainField("deptName")` / `subField("d", "deptName")` | 都是 `deptName` |
| `mainField("x").count()` / `subField("d", "x").count()` | 都是 `count(x)` |
| 任一侧带 `alias(...)` | 以 alias 为准，不冲突 |

label 冲突会静默互相覆盖（旧实现里子表值覆盖主表值）。`MdxQuerySplitter` 现在直接抛 `nop.err.orm.query-duplicate-field-label`，**给其中一个字段设置 `alias` 即可**。

### 不支持的字段属性

`QueryFieldBean.expression` / `QueryFieldBean.formula` / `QueryBean.aggregates` 在 MDX 通道没有任何消费方，生成的 SQL 里也不会出现。设置其中任意一个会抛 `nop.err.orm.query-unsupported-field-attr`，避免调用方误以为做了字段级计算。

### 分页优化

主表先分页，子表仅查询当前页的维度值，避免子表扫描全量数据。该过滤**只影响扫描范围**，能否合并完全由 `dimIndex.get(dimValue)` 的精确匹配决定，因此允许生成偏宽的 SQL，但不允许偏窄。

维度过滤由 `MdxQueryExecutor.buildDimFilter` 生成，三个约束：

- **SQL NULL 保留为 `null`**。维度键不做 `NULL → ""` 归一，否则 NULL 会与真正的空字符串共享同一个键，且空字符串被写进 `IN` 列表时数值列会直接报错。NULL 用 `isNull` 表达，单维度与复合维度两条分支行为一致。
- **`IN` 列表分片**。`nop.core.sql.in-list-max-size`（默认 1000）由 `SQL.spreadParams` 全局拦截，超出即抛 `nop.err.sql.in-list-too-large`；主表行数不受分页约束，必须按上限切片后 `OR` 合并。
- **主表结果为空时跳过全部子查询**，不发无意义的 SQL。

### 维度字段自动推断

- 主表无显式 `dimFields` 且无聚合 → 以主键为维度字段
- 主表有 `GROUP BY` → 以 `GROUP BY` 字段为维度字段
- 主表字段**全是聚合字段**且既无 `GROUP BY` 也无显式 `dimFields` → 全局聚合（只产出一行），**不推断维度**。此时不能同时声明子表字段，否则抛 `nop.err.orm.query-no-dim-fields`
- 关联属性子表：从 ORM 关联配置的 JOIN 条件推导（见下节「关联属性子表」）
- 显式 JOIN 子表：按 `子表 dimFields → join conditions → 非聚合字段` 顺序回退（见下节「显式 JOIN 声明」）

主表聚合且**显式**指定 `dimFields` 时，缺失的维度列会被补进 `SELECT` 并自动加入 `GROUP BY`。两者必须成对出现：只补 `SELECT` 不补 `GROUP BY` 会生成 `select count(x), t.id from t` 这类非法 SQL（H2/PostgreSQL/MySQL `ONLY_FULL_GROUP_BY` 均直接报错）。

### existsByQuery 只判定主查询

`IOrmTemplate.existsByQuery`（`MdxQueryExecutor.exists`）只执行拆分后的**主查询**。MDX 通道里子表字段是**投影**（补全主表行的列），不是行过滤条件，因此子表的 `filter`/聚合结果不参与「主表是否存在命中行」的判定。需要按子表条件排除主表行时，把条件写进主查询的 `filter`，或改用 `findListByQuery` 后自行判断。

---

## 显式 JOIN 声明

对于非 ORM 模型关联的表，用 `QuerySourceBean` 显式声明 JOIN。

`QueryBean` 没有 `addJoin(QuerySourceBean)` 重载，声明方式二选一：

- `query.leftJoin(sourceName, alias, leftJoinFields, rightJoinFields)` — 走 `QueryBean.addJoin`，关联键以 `conditions` 形式生成
- `query.setJoins(List.of(join))` — 直接构造 `QuerySourceBean`，自行设置 `dimFields` / `conditions`

```java
QueryBean query = new QueryBean();
query.setSourceName("SimsCollege");
query.addField(QueryFieldBean.forField("shortName"));
query.addField(QueryFieldBean.forField("deptId").aggFunc("count")
    .alias("deptCount").owner("deptAlias"));

// 显式声明 JOIN
QuerySourceBean join = new QuerySourceBean();
join.setAlias("deptAlias");
join.setSourceName("SimsDepartment");
join.setDimFields(Arrays.asList("collegeId"));
join.setFilter(FilterBeans.eq("status", 1));
query.setJoins(List.of(join));
```

### 显式 JOIN 子表的维度字段来源

`MdxQuerySplitter.resolveSubDimFields` 按优先级依次尝试：

| 优先级 | 来源 | 说明 |
|--------|------|------|
| 1 | `join.dimFields` | 调用方显式指定子表维度列 |
| 2 | `join.conditions` | `leftJoin`/`rightJoin`/`innerJoin` 生成的 `leftField → rightField` 配对 |
| 3 | 子表非聚合字段 | 全部字段都是聚合字段时为空 |

三条约束：

- **conditions 分支按主表维度顺序重排**，不是照抄声明顺序。`mainQuery.dimFields[i]` 对应到 `conditions` 中 `leftField == dimFields[i]` 的那条的 `rightField`，保证 `MdxQueryExecutor.buildDimValue` 构造的位置键在主子表两侧一一对应。
- **conditions 必须覆盖全部主表维度字段**。有维度字段找不到配对时无法按位置对齐，返回 `null`，由 `split()` 抛出 `nop.err.orm.dim-fields-mismatch`。
- **子表字段全是聚合字段且未声明维度列时**没有可推导的维度，抛 `nop.err.orm.query-no-dim-fields`，而不是产出错位结果。此时必须显式设置 `join.dimFields`，或改用 `leftJoin(...)` 走 conditions 分支。

### 关联属性子表的维度对齐

关联属性子表（`owner` 是一对多属性名）从 ORM 关联配置推导维度列，同样按**主表维度顺序**定位：

1. 遍历 `rel.getJoin()`，命中的 `join.leftProp` 按它在 `mainQuery.dimFields` 中的下标写入子表维度列。
2. 关联条件没有覆盖的维度字段，退化为 `<refPropName>.<dimField>` 属性路径。

第 2 步的属性路径是**合法**的：`DaoQueryHelper` 产出 `o.group.name` 这类文本后，EQL 编译器会把它展开成真实的 join：

```sql
-- 产出：select count(o.deptId), o.group.name from NopAuthGroupDept o group by o.group.name
-- 实际执行：
select count(o.DEPT_ID) as c1, t1.NAME as c2
from NOP_AUTH_GROUP_DEPT as o
  join NOP_AUTH_GROUP as t1 on (o.GROUP_ID = t1.GROUP_ID) and (t1.DEL_FLAG = 0)
group by t1.NAME
```

按主表下标定位（而不是照抄 `rel.getJoin()` 顺序）是必要的：`rel.getJoin()` 会被重排为**关联目标实体**的主键顺序，主表维度默认是**本实体**的主键顺序，两者不一致时位置键对不上，子表数据会全部静默丢失。

### 重复维度值的处理

主表不做聚合时 `getMainQuery` 不加 `GROUP BY`，因此用户指定的 `dimFields` 若不唯一，主表结果里会出现多行共享同一个维度键。`dimIndex` 是 `Map<维度值, List<行>>`，子表数据会回填到**所有**匹配行，而不是只保留最后一行。

等价于 sql-lib.xml：

```xml
<query name="collegeWithDeptCount" sqlMethod="findList">
    <source>
        <sourceName>SimsCollege</sourceName>
        <fields>
            <field name="shortName"/>
            <field owner="deptAlias" name="deptId" aggFunc="count" alias="deptCount"/>
        </fields>
        <joins>
            <source alias="deptAlias" sourceName="SimsDepartment" dimFields="collegeId">
                <filter>
                    <eq name="status" value="1"/>
                </filter>
            </source>
        </joins>
    </source>
</query>
```

---

## FilterBeans：条件树构建

`io.nop.api.core.beans.FilterBeans` 提供静态工厂方法构建过滤树：

```java
// 精确匹配
FilterBeans.eq("status", 1)

// 范围
FilterBeans.gt("amount", 100)
FilterBeans.between("createTime", start, end)

// 集合
FilterBeans.in("kind", Arrays.asList("A", "B"))

// 模糊
FilterBeans.contains("name", "keyword")
FilterBeans.like("code", "ABC%")

// 组合
FilterBeans.and(
    FilterBeans.eq("status", 1),
    FilterBeans.or(
        FilterBeans.contains("name", "admin"),
        FilterBeans.contains("name", "root")
    )
)

// 自定义 SQL 片段
FilterBeans.sql(SQL.begin().sql("o.status IN (1,2,3)").end())
```

### 在 GraphQL 中传递 filter

```graphql
query {
    NopAuthUser__findPage(query: {
        filter: {
            "$and": [
                {"status": {"$eq": 1}},
                {"$or": [
                    {"userName": {"$contains": "admin"}},
                    {"nickName": {"$contains": "admin"}}
                ]}
            ]
        },
        orderBy: ["userName desc"],
        limit: 20
    }) {
        items { userId userName status }
        total
    }
}
```

前后端 filter 命名约定见 `03-runbooks/custom-query-with-querybean.md`。

> **查询字段 filter 运算符白名单**：缺省只允许 `eq`/`in`/`dateBetween`/`dateTimeBetween`（`ObjMetaBasedFilterValidator.DEFAULT_ALLOW_FILTER_OP`，`nop-biz`）。日期范围用 `dateBetween`（value=`[min,max]`）；需要 `le`/`ge`/`like` 等时在 xmeta prop 上声明 `allowFilterOp`。详见 `api-and-graphql.md`「查询字段 filter 运算符白名单」。

---

## CrudBizModel 路径 vs 直接 IOrmTemplate

| 维度 | CrudBizModel (doFindList/doFindPage) | IOrmTemplate.findListByQuery |
|------|--------------------------------------|-----------------------------|
| 数据权限 | ✅ 自动追加 | ❌ 需手动处理 |
| XMeta 默认 filter/orderBy | ✅ 自动合并 | ❌ 无 |
| page size 上限控制 | ✅ | ❌ |
| filter 转换/表达式解析 | ✅ | ❌ |
| 主键排序补全（分页稳定） | ✅ | ❌ |
| 主子表 DQL（owner 字段） | ✅（委托给 ormTemplate） | ✅ 原生支持 |

**规则**：优先走 `CrudBizModel.doFindList()` / `doFindPage()`，只在无权限/Meta 依赖的场景下直接使用 `IOrmTemplate.findListByQuery()`。

---

## QueryBean 原地修改

`CrudBizModel` 的 `findPage`/`findList`/`findCount` 等方法**不会**对传入的 `QueryBean` 做防御性拷贝。执行过程中会原地修改（追加过滤、排序、limit 裁剪等）。如需复用，先克隆：

```java
QueryBean copy = query.cloneInstance();
```

---

## 完整代码示例

### 示例 1：单表聚合查询

```java
QueryBean query = new QueryBean();
query.setSourceName("NopAuthUser");
query.addField(QueryFieldBean.forField("groupId"));
query.addField(QueryFieldBean.forField("userId").aggFunc("count").alias("userCount"));
query.addFilter(FilterBeans.eq("status", 1));
query.addOrderField("groupId", true);
query.setLimit(100);

List<Map<String, Object>> result = ormTemplate.findListByQuery(query);
```

### 示例 2：主子表维度对齐

```java
QueryBean query = new QueryBean();
query.setSourceName("SimsCollege");
query.addField("shortName");
query.addField("collegeId");
query.addField(QueryFieldBean.forField("studentId").aggFunc("count")
    .alias("studentCount").owner("simsStudents"));

List<Map<String, Object>> list = ormTemplate.findListByQuery(query);
```

### 示例 3：通过 CrudBizModel 执行多表 DQL

在 BizModel 中：

```java
@BizQuery
public List<Map<String, Object>> queryCollegeStats(
    @Optional @Name("query") QueryBean query,
    FieldSelectionBean selection, IServiceContext context) {

    query.addField(QueryFieldBean.forField("studentId").aggFunc("count")
        .alias("studentCount").owner("simsStudents"));
    return doFindList(query, this::invokeDefaultPrepareQuery, selection, context);
}
```

---

## 相关文档

- `eql-and-database-compatibility.md` — EQL 语法与数据库兼容性
- `03-runbooks/custom-query-with-querybean.md` — QueryBean 自定义查询（FilterBeans、`filter_`前缀）
- `service-layer.md` — BizModel 查询方法（doFindList/doFindPage）
- `04-reference/safe-api-reference.md` — QueryBean API 安全参考
- `logical-deletion.md` — 逻辑删除在 QueryBean 中的处理

### 实现锚点

| 锚点 | 路径 |
|------|------|
| QueryBean | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/query/QueryBean.java` |
| FilterBeans | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/FilterBeans.java` |
| IOrmTemplate (QueryBean methods) | `nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmTemplate.java:190-202` |
| MdxQueryExecutor | `nop-persistence/nop-orm/src/main/java/io/nop/orm/mdx/MdxQueryExecutor.java` |
| MdxQuerySplitter | `nop-persistence/nop-orm/src/main/java/io/nop/orm/mdx/MdxQuerySplitter.java` |
| DaoQueryHelper | `nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/DaoQueryHelper.java` |
| QuerySqlItemModel | `nop-persistence/nop-orm/src/main/java/io/nop/orm/sql_lib/QuerySqlItemModel.java` |
| sql-lib.xdef | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/sql-lib.xdef` |
