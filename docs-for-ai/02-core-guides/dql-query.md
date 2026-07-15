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

### 分页优化

主表先分页，子表仅查询当前页的维度值（`IN` 过滤），避免子表扫描全量数据：

```java
// MdxQueryExecutor.java:164-180
if (filterSub) {
    if (query.getDimFields().size() == 1) {
        query.addFilter(FilterBeans.in(query.getDimFields().get(0), dimIndex.keySet()));
    } else {
        // 复合维度用 OR (AND ...) 拼装
    }
}
```

### 维度字段自动推断

- 主表无显式 `dimFields` 且无聚合 → 以主键为维度字段
- 主表有 `GROUP BY` → 以 `GROUP BY` 字段为维度字段
- 子表维度字段从 `dimFields` + ORM 关联配置自动推导

---

## 显式 JOIN 声明

对于非 ORM 模型关联的表，用 `QuerySourceBean` 显式声明 JOIN：

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
query.addJoin(join);
```

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
