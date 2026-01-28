# QueryBean 使用指南

## 概述

`QueryBean`是Nop平台用于构建结构化查询的核心类，支持复杂条件查询、排序、分页、字段选择、聚合查询等功能。它是整个平台查询API的通用数据结构，被IEntityDao、CrudBizModel、GraphQL等广泛使用。

**位置**：`io.nop.api.core.beans.query.QueryBean`

## 核心属性

| 属性名 | 类型 | 描述 |
|--------|------|------|
| `name` | String | 查询名称，用于标识不同的查询 |
| `offset` | long | 分页偏移量，从0开始 |
| `limit` | int | 每页记录数 |
| `cursor` | String | 游标分页的游标值 |
| `findPrev` | boolean | 是否查找前一页 |
| `distinct` | boolean | 是否去重 |
| `fields` | List<QueryFieldBean> | 要查询的字段列表 |
| `aggregates` | List<QueryAggregateFieldBean> | 聚合字段列表 |
| `sourceName` | String | 数据源名称（实体名） |
| `joins` | List<QuerySourceBean> | 关联查询配置 |
| `leftJoinProps` | List<String> | 左连接属性列表 |
| `filter` | TreeBean | 过滤条件 |
| `orderBy` | List<OrderFieldBean> | 排序条件列表 |
| `groupBy` | List<GroupFieldBean> | 分组字段列表 |
| `timeout` | Integer | 查询超时时间（毫秒） |
| `disableLogicalDelete` | boolean | 是否禁用逻辑删除 |

## 基本使用

### 创建QueryBean

```java
// 方式1：空构造
QueryBean query = new QueryBean();

// 方式2：指定数据源
QueryBean query = new QueryBean("NopAuthUser");
```

### 设置过滤条件

```java
// 简单过滤
query.setFilter(FilterBeans.eq("status", 1));

// 复合条件（AND）
query.setFilter(FilterBeans.and(
    FilterBeans.eq("status", 1),
    FilterBeans.gt("createTime", startTime)
));

// 复合条件（OR）
query.setFilter(FilterBeans.or(
    FilterBeans.eq("status", 1),
    FilterBeans.eq("status", 2)
));
```

### 设置排序

```java
// 单个字段排序
query.addOrderField(OrderFieldBean.forField("createTime", true)); // 降序

// 多个字段排序
query.setOrderBy(Arrays.asList(
    OrderFieldBean.forField("status", false),  // 升序
    OrderFieldBean.forField("createTime", true)  // 降序
));
```

### 设置分页

```java
// 方式1：使用offset和limit
query.setOffset(0);   // 起始位置
query.setLimit(20);   // 每页大小

// 方式2：使用分页对象（CrudBizModel的findPage方法会处理）
// 在CrudBizModel中，直接传递pageNo和pageSize给findPage方法即可
```

### 执行查询

```java
// 通过IEntityDao执行
List<NopAuthUser> users = dao.findAllByQuery(query);
List<NopAuthUser> page = dao.findPageByQuery(query);

// 通过CrudBizModel执行（返回PageBean）
PageBean<NopAuthUser> result = bizModel.findPage(query, 1, 20);
```

## 过滤条件构建

### FilterBeans 工具方法

| 方法 | 描述 | 示例 |
|------|------|------|
| `eq(name, value)` | 等于 | `FilterBeans.eq("name", "admin")` |
| `ne(name, value)` | 不等于 | `FilterBeans.ne("status", 0)` |
| `gt(name, value)` | 大于 | `FilterBeans.gt("age", 18)` |
| `ge(name, value)` | 大于等于 | `FilterBeans.ge("score", 60)` |
| `lt(name, value)` | 小于 | `FilterBeans.lt("price", 100)` |
| `le(name, value)` | 小于等于 | `FilterBeans.le("count", 10)` |
| `in(name, values)` | 包含于 | `FilterBeans.in("id", Arrays.asList(1,2,3))` |
| `notIn(name, values)` | 不包含于 | `FilterBeans.notIn("status", Arrays.asList(0,1))` |
| `between(name, min, max)` | 范围查询 | `FilterBeans.between("age", 18, 30)` |
| `startsWith(name, value)` | 以...开头 | `FilterBeans.startsWith("name", "A")` |
| `endsWith(name, value)` | 以...结尾 | `FilterBeans.endsWith("email", "@gmail.com")` |
| `contains(name, value)` | 包含 | `FilterBeans.contains("name", "test")` |
| `isNull(name)` | 为空 | `FilterBeans.isNull("deletedAt")` |
| `notNull(name)` | 不为空 | `FilterBeans.notNull("createdAt")` |
| `and(filters)` | 逻辑与 | `FilterBeans.and(filter1, filter2)` |
| `or(filters)` | 逻辑或 | `FilterBeans.or(filter1, filter2)` |
| `not(filter)` | 逻辑非 | `FilterBeans.not(filter)` |

### 复杂条件示例

```java
// 复杂的AND条件
TreeBean filter = FilterBeans.and(
    FilterBeans.eq("status", 1),
    FilterBeans.or(
        FilterBeans.startsWith("name", "A"),
        FilterBeans.startsWith("name", "B")
    ),
    FilterBeans.between("createTime", startDate, endDate)
);

query.setFilter(filter);

// 嵌套条件
TreeBean nestedFilter = FilterBeans.and(
    FilterBeans.eq("department.id", deptId),
    FilterBeans.or(
        FilterBeans.eq("status", 1),
        FilterBeans.and(
            FilterBeans.eq("status", 2),
            FilterBeans.ge("createTime", activeDate)
        )
    )
);
```

## 字段选择

### 选择特定字段

```java
// 添加要查询的字段
query.addField(QueryFieldBean.forField("id"));
query.addField(QueryFieldBean.forField("name"));
query.addField(QueryFieldBean.forField("email"));

// 或使用字段列表
query.setFields(Arrays.asList(
    QueryFieldBean.forField("id"),
    QueryFieldBean.forField("name"),
    QueryFieldBean.forField("email")
));
```

### 字段属性

```java
QueryFieldBean field = new QueryFieldBean();
field.setName("userName");
field.setAlias("name");  // 字段别名
field.setAggFunc("COUNT");  // 聚合函数
field.setDistinct(true);  // 是否去重
```

## 聚合查询

### 基本聚合

```java
// 添加聚合字段
QueryAggregateFieldBean countAgg = new QueryAggregateFieldBean();
countAgg.setName("id");
countAgg.setAggFunc("COUNT");
countAgg.setAlias("total");

QueryAggregateFieldBean sumAgg = new QueryAggregateFieldBean();
sumAgg.setName("amount");
sumAgg.setAggFunc("SUM");
sumAgg.setAlias("totalAmount");

query.setAggregates(Arrays.asList(countAgg, sumAgg));
```

### 分组聚合

```java
// 设置分组字段
query.setGroupBy(Arrays.asList(
    GroupFieldBean.forField("department"),
    GroupFieldBean.forField("status")
));

// 执行聚合查询
List<Map<String, Object>> result = dao.selectFieldsByQuery(query);
```

## 关联查询

### 左连接关联属性

```java
// 左连接加载关联属性
query.setLeftJoinProps(Arrays.asList("roles", "departments"));

// 执行查询时会自动关联加载这些属性
List<NopAuthUser> users = dao.findAllByQuery(query);
```

### 关联查询（joins属性）

```java
// 定义关联查询
QuerySourceBean join = new QuerySourceBean();
join.setName("dept");
join.setJoinType("LEFT_JOIN");
join.setCondition(FilterBeans.eq("dept.id", "department.id"));

query.setJoins(Arrays.asList(join));
```

## 游标分页

### 使用cursor分页

```java
// 第一页
query.setCursor(null);
query.setLimit(20);
query.setFindPrev(false);

List<NopAuthUser> page1 = dao.findNext(query);

// 获取下一页的cursor
String nextCursor = page1.get(0).getCursorValue(); // 假设有cursorValue方法

// 第二页
query.setCursor(nextCursor);
List<NopAuthUser> page2 = dao.findNext(query);
```

### 查找前一页

```java
query.setFindPrev(true);
List<NopAuthUser> prevPage = dao.findPrev(query);
```

## 完整示例

### 示例1：用户列表查询

```java
@BizQuery
public PageBean<NopAuthUser> findUsers(QueryBean query) {
    // 设置默认过滤条件
    TreeBean filter = query.getFilter();
    if (filter == null) {
        filter = FilterBeans.alwaysTrue();
    }

    query.setFilter(FilterBeans.and(
        filter,
        FilterBeans.ne("status", -1)  // 排除删除用户
    ));

    // 设置默认排序
    if (query.getOrderBy() == null || query.getOrderBy().isEmpty()) {
        query.setOrderBy(Arrays.asList(
            OrderFieldBean.forField("status", false),
            OrderFieldBean.forField("createTime", true)
        ));
    }

    return findPage(query, 1, 20);
}
```

### 示例2：条件筛选和排序

```java
@BizQuery
public List<NopAuthUser> searchUsers(
    String keyword,
    Integer status,
    String orderBy,
    boolean desc
) {
    QueryBean query = new QueryBean();

    // 构建过滤条件
    List<TreeBean> filters = new ArrayList<>();

    if (StringHelper.isNotEmpty(keyword)) {
        filters.add(FilterBeans.or(
            FilterBeans.contains("name", keyword),
            FilterBeans.contains("email", keyword),
            FilterBeans.contains("phone", keyword)
        ));
    }

    if (status != null) {
        filters.add(FilterBeans.eq("status", status));
    }

    if (!filters.isEmpty()) {
        query.setFilter(FilterBeans.and(filters));
    }

    // 设置排序
    if (StringHelper.isNotEmpty(orderBy)) {
        query.addOrderField(OrderFieldBean.forField(orderBy, desc));
    }

    // 设置最大返回数量
    query.setLimit(100);

    return dao().findAllByQuery(query);
}
```

### 示例3：统计查询

```java
@BizQuery
public Map<String, Object> getUserStats(String departmentId) {
    QueryBean query = new QueryBean("NopAuthUser");

    // 设置过滤条件
    if (StringHelper.isNotEmpty(departmentId)) {
        query.setFilter(FilterBeans.eq("departmentId", departmentId));
    } else {
        query.setFilter(FilterBeans.ne("status", -1));
    }

    // 设置聚合字段
    query.setAggregates(Arrays.asList(
        new QueryAggregateFieldBean("id", "COUNT", "totalUsers"),
        new QueryAggregateFieldBean("id", "COUNT", "activeUsers")
            .condition(FilterBeans.eq("status", 1))
    ));

    // 执行查询
    List<Map<String, Object>> result = dao().selectFieldsByQuery(query);

    return result.isEmpty() ? new HashMap<>() : result.get(0);
}
```

## 性能优化建议

### 1. 合理使用字段选择

```java
// 只查询需要的字段，减少数据传输
query.setFields(Arrays.asList(
    QueryFieldBean.forField("id"),
    QueryFieldBean.forField("name"),
    QueryFieldBean.forField("status")
));
```

### 2. 避免过深的嵌套条件

```java
// 不推荐：过深的嵌套
query.setFilter(FilterBeans.and(
    FilterBeans.eq("a", 1),
    FilterBeans.and(
        FilterBeans.eq("b", 2),
        FilterBeans.and(
            FilterBeans.eq("c", 3),
            FilterBeans.and(
                FilterBeans.eq("d", 4),
                FilterBeans.eq("e", 5)
            )
        )
    )
));

// 推荐：平铺条件
query.setFilter(FilterBeans.and(
    FilterBeans.eq("a", 1),
    FilterBeans.eq("b", 2),
    FilterBeans.eq("c", 3),
    FilterBeans.eq("d", 4),
    FilterBeans.eq("e", 5)
));
```

### 3. 使用索引友好的条件

```java
// 推荐：使用范围查询
query.setFilter(FilterBeans.between("createTime", start, end));

// 避免：对索引字段使用函数
// query.setFilter(FilterBeans.eq("DATE(createTime)", today));  // 会导致索引失效
```

### 4. 合理设置分页大小

```java
// 推荐的每页大小：20-100
query.setLimit(50);

// 避免过大的分页
// query.setLimit(10000);  // 会导致内存和性能问题
```

## 与其他组件的关系

| 组件 | 使用方式 | 场景 |
|------|---------|------|
| IEntityDao | `dao.findAllByQuery(query)` | 直接使用DAO执行查询 |
| IOrmTemplate | `orm.findAllByQuery(query)` | 在事务中执行查询 |
| CrudBizModel | `findPage(query, pageNo, pageSize)` | 业务层的分页查询 |
| GraphQL | `QueryBean`作为查询参数 | GraphQL查询的输入参数 |

## 常见问题

### Q1: QueryBean和Example查询有什么区别？

**A**:
- **QueryBean**: 功能强大，支持复杂条件、排序、分页、聚合、字段选择等
- **Example查询**: 简单易用，适合等值查询，通过实体对象构建条件

### Q2: 如何查询已删除的记录？

**A**: 设置`disableLogicalDelete`属性
```java
query.setDisableLogicalDelete(true);
```

### Q3: 如何实现动态字段选择？

**A**: 根据前端传来的字段列表动态构建
```java
List<String> fields = getRequestedFields(request);
query.setFields(fields.stream()
    .map(QueryFieldBean::forField)
    .collect(Collectors.toList()));
```

### Q4: 如何处理NULL值的过滤条件？

**A**: 使用专门的isNull/notNull方法
```java
// 查询为NULL的记录
FilterBeans.isNull("deletedAt");

// 查询不为NULL的记录
FilterBeans.notNull("deletedAt");

// 注意：不要使用eq("field", null)
// FilterBeans.eq("deletedAt", null);  // 错误
```

## 相关文档

- [FilterBeans使用指南](./filterbeans-guide.md) - 过滤条件详解
- [IEntityDao使用指南](./entitydao-usage.md) - 数据访问接口详解
- [服务层开发指南](../service/service-layer-development.md) - BizModel开发详解
- [GraphQL服务开发指南](../api/graphql-guide.md) - GraphQL API开发
- [数据处理指南](./data-processing.md) - 数据处理指南

## 总结

QueryBean是Nop平台查询API的核心，提供了：
1. **统一的查询结构**：跨组件使用同一查询模型
2. **丰富的查询能力**：条件、排序、分页、聚合、字段选择
3. **灵活的扩展性**：支持复杂的业务查询场景

在开发中，推荐：
- 简单等值查询使用Example
- 复杂查询使用QueryBean
- 性能敏感场景使用原生SQL
