# Complex Query Examples

## 概述

本文档提供Nop Platform复杂查询的示例，展示如何使用QueryBean和FilterBeans构建各种查询场景，包括多条件查询、关联查询、聚合查询、分页查询等。

## 多条件查询

### 1. AND条件组合

```java
@BizQuery
public List<DemoUser> findUsersWithAnd(UserSearchRequest request) {
    QueryBean query = new QueryBean();

    List<TreeBean> filters = new ArrayList<>();

    // 用户名
    if (StringHelper.isNotEmpty(request.getKeyword())) {
        filters.add(FilterBeans.contains("userName", request.getKeyword()));
    }

    // 邮箱
    if (StringHelper.isNotEmpty(request.getEmail())) {
        filters.add(FilterBeans.eq("email", request.getEmail()));
    }

    // 状态
    if (request.getStatus() != null) {
        filters.add(FilterBeans.eq("status", request.getStatus()));
    }

    // 创建时间范围
    if (request.getStartTime() != null && request.getEndTime() != null) {
        filters.add(FilterBeans.between("createTime",
            request.getStartTime(),
            request.getEndTime()
        ));
    }

    if (!filters.isEmpty()) {
        query.setFilter(FilterBeans.and(filters));
    }

    query.setOrderField("createTime");
    query.setOrderDesc(true);

    return dao().findAllByQuery(query);
}
```

### 2. OR条件组合

```java
@BizQuery
public List<DemoUser> findUsersWithOr(UserSearchRequest request) {
    QueryBean query = new QueryBean();

    // 用户名或邮箱包含关键词
    if (StringHelper.isNotEmpty(request.getKeyword())) {
        query.setFilter(FilterBeans.or(
            FilterBeans.contains("userName", request.getKeyword()),
            FilterBeans.contains("email", request.getKeyword())
        ));
    }

    // 状态
    if (request.getStatuses() != null && !request.getStatuses().isEmpty()) {
        List<TreeBean> statusFilters = request.getStatuses().stream()
            .map(status -> FilterBeans.eq("status", status))
            .collect(Collectors.toList());
        query.setFilter(FilterBeans.or(statusFilters));
    }

    return dao().findAllByQuery(query);
}
```

### 3. 嵌套条件

```java
@BizQuery
public List<Order> findComplexOrders(OrderSearchRequest request) {
    QueryBean query = new QueryBean();

    List<TreeBean> filters = new ArrayList<>();

    // 条件1: (用户名包含关键词 AND 状态匹配)
    if (StringHelper.isNotEmpty(request.getKeyword()) && request.getStatus() != null) {
        filters.add(FilterBeans.and(
            FilterBeans.contains("orderNo", request.getKeyword()),
            FilterBeans.eq("status", request.getStatus())
        ));
    }

    // 条件2: 金额范围
    if (request.getMinAmount() != null && request.getMaxAmount() != null) {
        filters.add(FilterBeans.between("amount",
            request.getMinAmount(),
            request.getMaxAmount()
        ));
    }

    // 条件3: 创建时间大于某个时间
    if (request.getMinCreateTime() != null) {
        filters.add(FilterBeans.ge("createTime", request.getMinCreateTime()));
    }

    if (!filters.isEmpty()) {
        query.setFilter(FilterBeans.and(filters));
    }

    return dao().findAllByQuery(query);
}
```

## 关联查询

### 1. 一对一关联

```java
@BizQuery
public List<Order> findOrdersWithUser(OrderSearchRequest request) {
    QueryBean query = new QueryBean();

    // 添加关联表
    query.setSources(List.of(
        new QuerySourceBean()
            .setAlias("o")
            .setEntityName("DemoOrder"),
        new QuerySourceBean()
            .setAlias("u")
            .setEntityName("DemoUser")
            .setJoinType("LEFT")
            .setOnFilter(FilterBeans.eq("o.userId", "u.userId"))
    ));

    // 添加查询条件
    List<TreeBean> filters = new ArrayList<>();

    if (StringHelper.isNotEmpty(request.getKeyword())) {
        filters.add(FilterBeans.or(
            FilterBeans.contains("o.orderNo", request.getKeyword()),
            FilterBeans.contains("u.userName", request.getKeyword())
        ));
    }

    if (!filters.isEmpty()) {
        query.setFilter(FilterBeans.and(filters));
    }

    query.setOrderField("o.createTime");
    query.setOrderDesc(true);

    return dao().findAllByQuery(query);
}
```

### 2. 一对多关联

```java
@BizQuery
public List<Order> findOrdersWithItems(OrderSearchRequest request) {
    QueryBean query = new QueryBean();

    // 添加关联表
    query.setSources(List.of(
        new QuerySourceBean()
            .setAlias("o")
            .setEntityName("DemoOrder"),
        new QuerySourceBean()
            .setAlias("i")
            .setEntityName("DemoOrderItem")
            .setJoinType("LEFT")
            .setOnFilter(FilterBeans.eq("o.orderId", "i.orderId"))
    ));

    // 查询条件
    if (request.getOrderId() != null) {
        query.setFilter(FilterBeans.eq("o.orderId", request.getOrderId()));
    }

    query.setOrderField("o.createTime");
    query.setOrderDesc(true);

    List<Order> orders = dao().findAllByQuery(query);

    // 预加载订单项
    if (!orders.isEmpty()) {
        List<String> orderIds = orders.stream()
            .map(Order::getOrderId)
            .collect(Collectors.toList());
        List<OrderItem> items = orderItemDao.batchGetItemsByOrderIds(orderIds);

        // 关联订单项到订单
        Map<String, List<OrderItem>> itemMap = items.stream()
            .collect(Collectors.groupingBy(OrderItem::getOrderId));

        for (Order order : orders) {
            order.setItems(itemMap.get(order.getOrderId()));
        }
    }

    return orders;
}
```

### 3. 多层关联

```java
@BizQuery
public List<Order> findOrdersWithUserAndItems(OrderSearchRequest request) {
    QueryBean query = new QueryBean();

    // 添加多个关联表
    query.setSources(List.of(
        new QuerySourceBean()
            .setAlias("o")
            .setEntityName("DemoOrder"),
        new QuerySourceBean()
            .setAlias("u")
            .setEntityName("DemoUser")
            .setJoinType("LEFT")
            .setOnFilter(FilterBeans.eq("o.userId", "u.userId")),
        new QuerySourceBean()
            .setAlias("i")
            .setEntityName("DemoOrderItem")
            .setJoinType("LEFT")
            .setOnFilter(FilterBeans.eq("o.orderId", "i.orderId")),
        new QuerySourceBean()
            .setAlias("p")
            .setEntityName("DemoProduct")
            .setJoinType("LEFT")
            .setOnFilter(FilterBeans.eq("i.productId", "p.productId"))
    ));

    // 查询条件
    if (request.getUserName() != null) {
        query.setFilter(FilterBeans.contains("u.userName", request.getUserName()));
    }

    query.setOrderField("o.createTime");
    query.setOrderDesc(true);

    return dao().findAllByQuery(query);
}
```

## 聚合查询

### 1. 统计查询

```java
@BizQuery
public Map<String, Object> getUserStatistics(UserSearchRequest request) {
    QueryBean query = new QueryBean();

    // 添加查询条件
    if (request.getStatus() != null) {
        query.setFilter(FilterBeans.eq("status", request.getStatus()));
    }

    // 添加聚合字段
    query.setAggregates(List.of(
        new QueryAggregateFieldBean()
            .setAlias("userCount")
            .setFunction("COUNT")
            .setField("*"),
        new QueryAggregateFieldBean()
            .setAlias("avgAge")
            .setFunction("AVG")
            .setField("age")
    ));

    // 执行查询
    List<Map<String, Object>> results = ormTemplate.queryForList(
        query.getFilter() != null ? "SELECT COUNT(*) as userCount, AVG(age) as avgAge FROM demo_user WHERE status = ?" :
            "SELECT COUNT(*) as userCount, AVG(age) as avgAge FROM demo_user",
        request.getStatus()
    );

    if (!results.isEmpty()) {
        return results.get(0);
    }

    return new HashMap<>();
}
```

### 2. 分组统计

```java
@BizQuery
public List<Map<String, Object>> getUserStatsByStatus() {
    String sql = "" +
        "SELECT status, " +
        "COUNT(*) as userCount, " +
        "AVG(age) as avgAge, " +
        "MAX(create_time) as latestCreateTime " +
        "FROM demo_user " +
        "GROUP BY status " +
        "ORDER BY status";

    List<Map<String, Object>> results = ormTemplate.queryForList(sql);

    return results;
}
```

### 3. 子查询

```java
@BizQuery
public List<User> findUsersWithRecentOrders() {
    // 使用子查询：查找最近有订单的用户
    String sql = "" +
        "SELECT * FROM demo_user " +
        "WHERE user_id IN (" +
        "  SELECT DISTINCT user_id " +
        "  FROM demo_order " +
        "  WHERE create_time >= ?" +
        ") " +
        "ORDER BY user_name";

    List<User> users = ormTemplate.queryList(
        sql,
        DateHelper.addDays(new Date(), -30) // 30天前
    );

    return users;
}
```

## 分页查询

### 1. 基础分页

```java
@BizQuery
public PageBean<DemoUser> findUsers(UserSearchRequest request) {
    QueryBean query = new QueryBean();

    // 添加查询条件
    if (StringHelper.isNotEmpty(request.getKeyword())) {
        query.setFilter(FilterBeans.contains("userName", request.getKeyword()));
    }

    // 设置分页参数
    int pageNo = request.getPageNo() != null ? request.getPageNo() : 1;
    int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;

    query.setOffset((pageNo - 1) * pageSize);
    query.setLimit(pageSize);

    // 查询总数
    int totalCount = dao().countByQuery(query);

    // 查询数据
    List<DemoUser> items = dao().findAllByQuery(query);

    // 构建分页结果
    return new PageBean<>(items, totalCount, pageNo, pageSize);
}
```

### 2. 游标分页

```java
@BizQuery
public List<DemoUser> findUsersWithCursor(String cursor, int limit) {
    QueryBean query = new QueryBean();

    // 使用游标
    if (StringHelper.isNotEmpty(cursor)) {
        query.setFilter(FilterBeans.gt("userId", cursor));
    }

    // 设置限制
    query.setLimit(limit);

    // 按userId排序
    query.setOrderField("userId");
    query.setOrderDesc(false);

    return dao().findAllByQuery(query);
}
```

### 3. 滚动分页

```java
@BizQuery
public ScrollResult<DemoUser> scrollUsers(UserSearchRequest request) {
    QueryBean query = new QueryBean();

    // 添加查询条件
    if (StringHelper.isNotEmpty(request.getKeyword())) {
        query.setFilter(FilterBeans.contains("userName", request.getKeyword()));
    }

    // 设置初始偏移量
    int offset = request.getOffset() != null ? request.getOffset() : 0;
    int pageSize = 100; // 每次滚动100条

    query.setOffset(offset);
    query.setLimit(pageSize + 1); // 多查询一条作为判断是否有更多数据

    // 查询数据
    List<DemoUser> items = dao().findAllByQuery(query);

    // 判断是否还有更多数据
    boolean hasMore = items.size() > pageSize;

    if (hasMore) {
        items = items.subList(0, pageSize);
    }

    return new ScrollResult<>(items, hasMore, offset + pageSize);
}
```

## 字段选择

### 1. 指定查询字段

```java
@BizQuery
public List<Map<String, Object>> findUsersWithFields() {
    QueryBean query = new QueryBean();

    // 只查询需要的字段
    query.setFields(List.of("userId", "userName", "email", "status"));

    return dao().findAllByQuery(query);
}
```

### 2. 动态字段选择

```java
@BizQuery
public List<Map<String, Object>> findUsersWithDynamicFields(UserSearchRequest request) {
    QueryBean query = new QueryBean();

    // 根据请求动态选择字段
    List<String> fields = new ArrayList<>();
    if (request.isIncludeUserId()) {
        fields.add("userId");
    }
    if (request.isIncludeUserName()) {
        fields.add("userName");
    }
    if (request.isIncludeEmail()) {
        fields.add("email");
    }

    if (!fields.isEmpty()) {
        query.setFields(fields);
    }

    // 添加查询条件
    if (StringHelper.isNotEmpty(request.getKeyword())) {
        query.setFilter(FilterBeans.contains("userName", request.getKeyword()));
    }

    return dao().findAllByQuery(query);
}
```

### 3. 排除字段

```java
@BizQuery
public List<Map<String, Object>> findUsersWithoutFields() {
    String sql = "" +
        "SELECT userId, userName, email, status " +
        "FROM demo_user";

    List<Map<String, Object>> users = ormTemplate.queryForList(sql);

    return users;
}
```

## 排序查询

### 1. 单字段排序

```java
@BizQuery
public List<DemoUser> findUsersSorted(UserSearchRequest request) {
    QueryBean query = new QueryBean();

    // 添加查询条件
    if (StringHelper.isNotEmpty(request.getKeyword())) {
        query.setFilter(FilterBeans.contains("userName", request.getKeyword()));
    }

    // 添加排序
    query.setOrderField(request.getSortField());
    query.setOrderDesc(request.isSortDesc());

    return dao().findAllByQuery(query);
}
```

### 2. 多字段排序

```java
@BizQuery
public List<DemoUser> findUsersMultiSorted(UserSearchRequest request) {
    QueryBean query = new QueryBean();

    // 添加查询条件
    if (StringHelper.isNotEmpty(request.getKeyword())) {
        query.setFilter(FilterBeans.contains("userName", request.getKeyword()));
    }

    // 添加多个排序
    List<OrderFieldBean> orderFields = new ArrayList<>();
    orderFields.add(new OrderFieldBean("status", false)); // 状态升序
    orderFields.add(new OrderFieldBean("createTime", true)); // 创建时间降序

    query.setOrderFields(orderFields);

    return dao().findAllByQuery(query);
}
```

## 动态查询

### 1. 根据查询类型动态构建

```java
@BizQuery
public List<DemoUser> dynamicQuery(QueryCondition condition) {
    QueryBean query = new QueryBean();

    switch (condition.getOperator()) {
        case "eq":
            query.setFilter(FilterBeans.eq(condition.getField(), condition.getValue()));
            break;
        case "ne":
            query.setFilter(FilterBeans.ne(condition.getField(), condition.getValue()));
            break;
        case "gt":
            query.setFilter(FilterBeans.gt(condition.getField(), condition.getValue()));
            break;
        case "lt":
            query.setFilter(FilterBeans.lt(condition.getField(), condition.getValue()));
            break;
        case "like":
            query.setFilter(FilterBeans.contains(condition.getField(), (String) condition.getValue()));
            break;
        default:
            throw new NopException(ERR_INVALID_OPERATOR)
                .param("operator", condition.getOperator());
    }

    return dao().findAllByQuery(query);
}
```

### 2. 基于配置动态查询

```java
@BizQuery
public List<DemoUser> queryByConfig(QueryConfig config) {
    QueryBean query = new QueryBean();

    // 从配置读取查询条件
    List<TreeBean> filters = new ArrayList<>();

    if (config.getCondition("userName") != null) {
        QueryCondition userNameCond = config.getCondition("userName");
        filters.add(buildFilter("userName", userNameCond));
    }

    if (config.getCondition("email") != null) {
        QueryCondition emailCond = config.getCondition("email");
        filters.add(buildFilter("email", emailCond));
    }

    if (config.getCondition("status") != null) {
        QueryCondition statusCond = config.getCondition("status");
        filters.add(buildFilter("status", statusCond));
    }

    if (!filters.isEmpty()) {
        query.setFilter(FilterBeans.and(filters));
    }

    // 从配置读取排序
    if (config.getSortField() != null) {
        query.setOrderField(config.getSortField());
        query.setOrderDesc(config.isSortDesc());
    }

    return dao().findAllByQuery(query);
}

private TreeBean buildFilter(String field, QueryCondition condition) {
    switch (condition.getOperator()) {
        case "eq":
            return FilterBeans.eq(field, condition.getValue());
        case "like":
            return FilterBeans.contains(field, (String) condition.getValue());
        case "gt":
            return FilterBeans.gt(field, condition.getValue());
        default:
            return FilterBeans.eq(field, condition.getValue());
    }
}
```

## 原生SQL查询

### 1. 复杂原生SQL

```java
@BizQuery
public List<Map<String, Object>> findUsersWithNativeSQL() {
    // 使用原生SQL进行复杂查询
    String sql = "" +
        "SELECT u.user_id, u.user_name, u.email, u.status, " +
        "COUNT(o.order_id) as order_count, " +
        "MAX(o.create_time) as last_order_time " +
        "FROM demo_user u " +
        "LEFT JOIN demo_order o ON u.user_id = o.user_id " +
        "WHERE u.status = ? " +
        "GROUP BY u.user_id, u.user_name, u.email, u.status " +
        "HAVING COUNT(o.order_id) > ? " +
        "ORDER BY last_order_time DESC " +
        "LIMIT ?";

    List<Map<String, Object>> results = ormTemplate.queryForList(
        sql,
        1, // status
        3,  // 最小订单数
        100  // 限制
    );

    return results;
}
```

### 2. 批量操作原生SQL

```java
@BizMutation
@Transactional
public int batchUpdateStatus(List<String> userIds, Integer newStatus) {
    // 使用原生SQL进行批量更新
    String sql = "" +
        "UPDATE demo_user " +
        "SET status = ?, update_time = ? " +
        "WHERE user_id IN (";

    // 构建IN子句
    String placeholders = userIds.stream()
        .map(id -> "?")
        .collect(Collectors.joining(", "));
    sql += placeholders + ")";

    // 准备参数
    List<Object> params = new ArrayList<>();
    params.add(newStatus);
    params.add(new Date());
    params.addAll(userIds);

    // 执行批量更新
    int updated = ormTemplate.executeSql(sql, params.toArray());

    return updated;
}
```

## GraphQL复杂查询

### 1. 多条件查询

```graphql
query {
  DemoUser {
    findUsers(
      keyword: "zhang"
      status: 1
      startTime: "2024-01-01T00:00:00Z"
      endTime: "2024-12-31T23:59:59Z"
      pageNo: 1
      pageSize: 20
    ) {
      pageNo
      pageSize
      totalCount
      items {
        userId
        userName
        email
        status
      }
    }
  }
}
```

### 2. 关联查询

```graphql
query {
  Order {
    findOrdersWithUser(
      userName: "zhangsan"
      pageNo: 1
      pageSize: 20
    ) {
      pageNo
      pageSize
      totalCount
      items {
        orderId
        orderNo
        user {
          userId
          userName
          email
        }
        items {
          itemId
          productName
          quantity
          price
        }
      }
    }
  }
}
```

## 性能优化

### 1. 使用索引

确保查询字段有索引：

```java
@Entity(table = "demo_user", indexes = {
    @Index(name = "idx_user_name", columns = {"user_name"}),
    @Index(name = "idx_user_email", columns = {"email"}),
    @Index(name = "idx_user_status_create_time", columns = {"status", "create_time"})
})
public class DemoUser implements IOrmEntity {
    // ...
}
```

### 2. 避免SELECT *

只查询需要的字段：

```java
// ✅ 推荐
QueryBean query = new QueryBean();
query.setFields(List.of("userId", "userName", "email"));

// ❌ 不推荐
// SELECT * FROM demo_user
```

### 3. 使用LIMIT

限制查询结果数量：

```java
QueryBean query = new QueryBean();
query.setLimit(100); // 最多返回100条
```

## 常见场景

### 场景1：用户列表搜索

```java
@BizQuery
public PageBean<DemoUser> searchUsers(UserSearchRequest request) {
    QueryBean query = new QueryBean();

    List<TreeBean> filters = new ArrayList<>();

    // 关键词搜索（用户名或邮箱）
    if (StringHelper.isNotEmpty(request.getKeyword())) {
        filters.add(FilterBeans.or(
            FilterBeans.contains("userName", request.getKeyword()),
            FilterBeans.contains("email", request.getKeyword())
        ));
    }

    // 状态过滤
    if (request.getStatus() != null) {
        filters.add(FilterBeans.eq("status", request.getStatus()));
    }

    // 创建时间范围
    if (request.getStartTime() != null && request.getEndTime() != null) {
        filters.add(FilterBeans.between("createTime",
            request.getStartTime(),
            request.getEndTime()
        ));
    }

    if (!filters.isEmpty()) {
        query.setFilter(FilterBeans.and(filters));
    }

    // 排序
    query.setOrderField("createTime");
    query.setOrderDesc(true);

    // 分页
    int pageNo = request.getPageNo() != null ? request.getPageNo() : 1;
    int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;

    return findPage(query, pageNo, pageSize);
}
```

### 场景2：订单统计分析

```java
@BizQuery
public OrderStatistics getOrderStatistics(OrderStatsRequest request) {
    // 使用原生SQL进行复杂统计
    String sql = "" +
        "SELECT " +
        "  DATE(create_time) as order_date, " +
        "  COUNT(*) as order_count, " +
        "  SUM(amount) as total_amount, " +
        "  AVG(amount) as avg_amount " +
        "FROM demo_order " +
        "WHERE create_time BETWEEN ? AND ? " +
        "GROUP BY DATE(create_time) " +
        "ORDER BY order_date";

    List<Map<String, Object>> results = ormTemplate.queryForList(
        sql,
        request.getStartTime(),
        request.getEndTime()
    );

    OrderStatistics stats = new OrderStatistics();
    stats.setDailyStats(results);

    // 计算总计
    BigDecimal totalAmount = results.stream()
        .map(row -> (BigDecimal) row.get("total_amount"))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    stats.setTotalAmount(totalAmount);

    stats.setTotalOrders(results.stream()
        .mapToInt(row -> ((Number) row.get("order_count")).intValue())
        .sum());

    return stats;
}
```

## 相关文档

- [QueryBean使用指南](../03-development-guide/querybean-guide.md)
- [FilterBeans使用指南](../03-development-guide/filterbeans-guide.md)
- [IEntityDao使用指南](../03-development-guide/entitydao-usage.md)
- [完整CRUD示例](./complete-crud-example.md)

## 总结

本示例展示了Nop Platform复杂查询的各种场景：

1. **多条件查询**: AND、OR、嵌套条件
2. **关联查询**: 一对一、一对多、多层关联
3. **聚合查询**: 统计、分组、子查询
4. **分页查询**: 基础分页、游标分页、滚动分页
5. **字段选择**: 指定字段、动态字段、排除字段
6. **排序查询**: 单字段排序、多字段排序
7. **动态查询**: 基于类型、基于配置
8. **原生SQL**: 复杂查询、批量操作
9. **GraphQL查询**: 复杂查询语法

通过这些示例，可以应对各种复杂的查询场景，构建高效的数据访问层。

---

**作者**: AI Assistant (Sisyphus)
