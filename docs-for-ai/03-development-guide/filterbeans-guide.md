# FilterBeans 使用指南

## 概述

`FilterBeans`是Nop平台提供的过滤条件构建工具类，用于创建结构化的查询条件树。它与QueryBean配合使用，构建复杂的过滤条件，支持逻辑运算（AND/OR/NOT）、比较运算、范围查询、模糊查询等。

**位置**：`io.nop.api.core.beans.FilterBeans`

### ⚠️ 重要：使用 PROP_NAME_ 常量

Nop 平台为每个实体类自动生成 `PROP_NAME_` 常量，用于引用属性名。

**推荐做法**：
```java
// ✅ 使用 PROP_NAME_ 常量（类型安全、重构友好）
FilterBeans.eq(LitemallAddress.PROP_NAME_userId, userId);
FilterBeans.eq(LitemallAddress.PROP_NAME_isDefault, true);
```

**不推荐做法**：
```java
// ❌ 使用硬编码字符串（容易拼写错误、无法重构）
FilterBeans.eq("userId", userId);
FilterBeans.eq("isDefault", true);
```

**优势**：
- ✅ 类型安全：编译时检查
- ✅ 重构友好：IDE 可以自动重命名
- ✅ 避免拼写错误
- ✅ 代码可读性更好

## 核心概念

### TreeBean结构
FilterBeans创建的所有条件都是`TreeBean`类型，它是一个树形结构：
- 节点表示条件操作符（如eq, and, or等）
- 属性表示条件参数（如name, value, min, max等）
- 子节点表示嵌套的条件

```java
// 示例条件树
FilterBeans.and(
    FilterBeans.eq("status", 1),
    FilterBeans.or(
        FilterBeans.gt("score", 60),
        FilterBeans.like("name", "test")
    )
)

// 对应的逻辑：(status = 1) AND ((score > 60) OR (name LIKE '%test%'))
```

## 比较运算

### 等于运算

```java
// 基本等于
FilterBeans.eq("status", 1);

// 等于null
FilterBeans.eq("deletedAt", null);

// 等于字符串
FilterBeans.eq("name", "admin");

// 等于日期
FilterBeans.eq("createTime", LocalDateTime.now());
```

### 不等于运算

```java
// 不等于
FilterBeans.ne("status", 0);

// 不等于null
FilterBeans.ne("deletedAt", null);

// 不等于字符串
FilterBeans.ne("name", "guest");
```

### 大于运算

```java
// 大于
FilterBeans.gt("age", 18);

// 大于日期
FilterBeans.gt("createTime", startDate);
```

### 大于等于运算

```java
// 大于等于
FilterBeans.ge("score", 60);

// 大于等于日期
FilterBeans.ge("createTime", startDate);
```

### 小于运算

```java
// 小于
FilterBeans.lt("age", 60);

// 小于日期
FilterBeans.lt("createTime", endDate);
```

### 小于等于运算

```java
// 小于等于
FilterBeans.le("count", 10);

// 小于等于日期
FilterBeans.le("createTime", endDate);
```

## 集合运算

### IN运算

```java
// 包含于列表
FilterBeans.in("id", Arrays.asList(1, 2, 3));

// 包含于字符串列表
FilterBeans.in("status", Arrays.asList("active", "pending"));

// 包含于枚举列表
FilterBeans.in("type", Arrays.asList(Type.A, Type.B, Type.C));
```

### NOT IN运算

```java
// 不包含于列表
FilterBeans.notIn("id", Arrays.asList(1, 2, 3));

// 排除特定状态
FilterBeans.notIn("status", Arrays.asList("deleted", "blocked"));
```

## 范围查询

### BETWEEN运算

```java
// 数值范围
FilterBeans.between("age", 18, 30);

// 日期范围
FilterBeans.between("createTime", startDate, endDate);

// 时间范围
FilterBeans.between("createDate", LocalDate.now().minusDays(7), LocalDate.now());
```

### 扩展的范围运算

```java
// 日期范围（专用）
FilterBeans.betweenOp(FILTER_OP_DATE_BETWEEN, "createTime", startDate, endDate);

// 日期时间范围（专用）
FilterBeans.betweenOp(FILTER_OP_DATETIME_BETWEEN, "createTime", startDateTime, endDateTime);
```

## 字符串匹配

### 包含

```java
// 包含字符串
FilterBeans.contains("name", "test");

// 注意：SQL中会转换为 LIKE '%test%'
```

### 以...开头

```java
// 以指定字符串开头
FilterBeans.startsWith("name", "A");

// 注意：SQL中会转换为 LIKE 'A%'
```

### 以...结尾

```java
// 以指定字符串结尾
FilterBeans.endsWith("email", "@gmail.com");

// 注意：SQL中会转换为 LIKE '%@gmail.com'
```

### LIKE运算

```java
// 自定义LIKE模式
FilterBeans.likeOp(FILTER_OP_LIKE, "name", "te%t");

// 注意：百分号和下划线是通配符
// %: 匹配任意多个字符
// _: 匹配单个字符
```

### 正则表达式

```java
// 使用正则表达式
FilterBeans.regex("email", "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

// 注意：正则表达式语法可能因数据库而异
```

## 空值判断

### IS NULL运算

```java
// 字段为NULL
FilterBeans.isNull("deletedAt");

// 查询未删除的记录
FilterBeans.isNull("deletedTime");
```

### IS NOT NULL运算

```java
// 字段不为NULL
FilterBeans.notNull("createTime");

// 查询有创建时间的记录
FilterBeans.notNull("updateTime");
```

### IS EMPTY运算

```java
// 字段为空（字符串或集合）
FilterBeans.isEmpty("name");

// 注意：这会转换为 (field IS NULL OR field = '')
```

### IS NOT EMPTY运算

```java
// 字段不为空
FilterBeans.isNotEmpty("name");

// 注意：这会转换为 (field IS NOT NULL AND field <> '')
```

### IS BLANK运算

```java
// 字段为空白（包含空字符串、NULL、只有空格等）
FilterBeans.isBlank("name");

// 注意：这会转换为 (field IS NULL OR TRIM(field) = '')
```

### IS NOT BLANK运算

```java
// 字段不为空白
FilterBeans.notBlank("name");

// 注意：这会转换为 (field IS NOT NULL AND TRIM(field) <> '')
```

## 逻辑运算

### AND运算

**⚠️ 重要**：`FilterBeans.and()` 使用 **varargs 参数**，直接传递多个条件，不要传递 `List`。

```java
// ✅ 正确：直接传递多个条件
FilterBeans.and(
    FilterBeans.eq("status", 1),
    FilterBeans.gt("age", 18)
);

// ✅ 正确：多个条件
FilterBeans.and(
    FilterBeans.eq("status", 1),
    FilterBeans.gt("age", 18),
    FilterBeans.notNull("email")
);

// ❌ 错误：不要传递 List
// List<TreeBean> conditions = Arrays.asList(...);
// FilterBeans.and(conditions);  // 编译错误

// ✅ 正确：如果需要从 List 构建，转换为数组
List<TreeBean> conditions = Arrays.asList(
    FilterBeans.eq("status", 1),
    FilterBeans.gt("age", 18)
);
FilterBeans.and(conditions.toArray(new TreeBean[0]));
```

### OR运算
```java
// 两个条件OR
FilterBeans.or(
    FilterBeans.eq("status", 1),
    FilterBeans.eq("status", 2)
);

// 多个条件OR
FilterBeans.or(
    FilterBeans.eq("type", "A"),
    FilterBeans.eq("type", "B"),
    FilterBeans.eq("type", "C")
);
```

### NOT运算

```java
// 否定条件
FilterBeans.not(FilterBeans.eq("status", -1));

// 否定复合条件
FilterBeans.not(FilterBeans.and(
    FilterBeans.eq("status", -1),
    FilterBeans.eq("deleted", true)
));
```

### 复杂逻辑组合

```java
// 组合逻辑：(status = 1) AND ((type = 'A') OR (type = 'B'))
FilterBeans.and(
    FilterBeans.eq("status", 1),
    FilterBeans.or(
        FilterBeans.eq("type", "A"),
        FilterBeans.eq("type", "B")
    )
);

// 复杂逻辑：((status = 1) OR (status = 2)) AND ((age > 18) OR (vip = true))
FilterBeans.and(
    FilterBeans.or(
        FilterBeans.eq("status", 1),
        FilterBeans.eq("status", 2)
    ),
    FilterBeans.or(
        FilterBeans.gt("age", 18),
        FilterBeans.eq("vip", true)
    )
);
```

## 特殊运算

### LENGTH运算

```java
// 字段长度等于
FilterBeans.lengthOp(FILTER_OP_LENGTH, "name", 5);

// 长度范围
FilterBeans.lengthBetween("name", 3, 10);
```

### UTF8_LENGTH运算

```java
// UTF8字节长度范围
FilterBeans.utf8LengthBetween("content", 10, 100);
```

### SQL运算

```java
// 直接使用SQL表达式（需要谨慎使用，有SQL注入风险）
FilterBeans.sql("createTime > ?", startDate);
```

### 常量运算

```java
// 总是为true（不过滤）
FilterBeans.alwaysTrue();

// 总是为false（不返回任何记录）
FilterBeans.alwaysFalse();
```

## 字段比较

### 比较两个字段

```java
// 字段等于字段
FilterBeans.relation("startTime", "endTime");

// 字段大于字段
FilterBeans.propCompareOp(FILTER_OP_GT, "endTime", "startTime");

// 字段不等于字段
FilterBeans.ne("status", "archivedStatus");
```

## 动态条件构建

### 根据参数动态构建

```java
public QueryBean buildQuery(String keyword, Integer status, Date startDate) {
    List<TreeBean> filters = new ArrayList<>();

    // 动态添加keyword过滤
    if (StringHelper.isNotEmpty(keyword)) {
        filters.add(FilterBeans.or(
            FilterBeans.contains("name", keyword),
            FilterBeans.contains("email", keyword),
            FilterBeans.contains("phone", keyword)
        ));
    }

    // 动态添加status过滤
    if (status != null) {
        filters.add(FilterBeans.eq("status", status));
    }

    // 动态添加日期范围
    if (startDate != null) {
        filters.add(FilterBeans.ge("createTime", startDate));
    }

    // 组合条件
    TreeBean filter = filters.isEmpty()
        ? FilterBeans.alwaysTrue()
        : FilterBeans.and(filters);

    QueryBean query = new QueryBean();
    query.setFilter(filter);
    return query;
}
```

### 递归构建嵌套条件

```java
public TreeBean buildFilterTree(List<FilterCondition> conditions) {
    if (conditions.isEmpty()) {
        return FilterBeans.alwaysTrue();
    }

    // 将条件分组
    List<TreeBean> andFilters = new ArrayList<>();
    List<TreeBean> orFilters = new ArrayList<>();

    for (FilterCondition cond : conditions) {
        TreeBean filter = buildSingleFilter(cond);
        if (cond.isAnd()) {
            andFilters.add(filter);
        } else {
            orFilters.add(filter);
        }
    }

    // 组合条件
    TreeBean andFilter = andFilters.isEmpty()
        ? FilterBeans.alwaysTrue()
        : (andFilters.size() == 1 ? andFilters.get(0) : FilterBeans.and(andFilters));

    TreeBean orFilter = orFilters.isEmpty()
        ? FilterBeans.alwaysTrue()
        : (orFilters.size() == 1 ? orFilters.get(0) : FilterBeans.or(orFilters));

    return FilterBeans.and(andFilter, orFilter);
}

private TreeBean buildSingleFilter(FilterCondition cond) {
    switch (cond.getOperator()) {
        case "eq":
            return FilterBeans.eq(cond.getField(), cond.getValue());
        case "gt":
            return FilterBeans.gt(cond.getField(), cond.getValue());
        case "contains":
            return FilterBeans.contains(cond.getField(), cond.getValue());
        // ... 其他操作符
        default:
            throw new IllegalArgumentException("Unknown operator: " + cond.getOperator());
    }
}
```

## 实际应用示例

### 示例1：用户搜索

```java
public QueryBean buildUserSearchQuery(UserSearchRequest request) {
    List<TreeBean> filters = new ArrayList<>();

    // 用户名搜索
    if (StringHelper.isNotEmpty(request.getUserName())) {
        filters.add(FilterBeans.contains("userName", request.getUserName()));
    }

    // 邮箱搜索
    if (StringHelper.isNotEmpty(request.getEmail())) {
        filters.add(FilterBeans.contains("email", request.getEmail()));
    }

    // 状态过滤
    if (request.getStatus() != null) {
        filters.add(FilterBeans.eq("status", request.getStatus()));
    }

    // 创建时间范围
    if (request.getStartDate() != null && request.getEndDate() != null) {
        filters.add(FilterBeans.between(
            "createTime",
            request.getStartDate(),
            request.getEndDate()
        ));
    }

    // 组合条件
    QueryBean query = new QueryBean();
    query.setFilter(filters.isEmpty()
        ? FilterBeans.alwaysTrue()
        : FilterBeans.and(filters)
    );

    // 设置排序
    if (StringHelper.isNotEmpty(request.getOrderBy())) {
        query.addOrderField(OrderFieldBean.forField(
            request.getOrderBy(),
            request.isDesc()
        ));
    }

    return query;
}
```

### 示例2：复杂的多条件组合

```java
public QueryBean buildOrderQuery(OrderQueryRequest request) {
    List<TreeBean> filters = new ArrayList<>();

    // 订单状态过滤
    if (CollectionHelper.isNotEmpty(request.getStatusList())) {
        filters.add(FilterBeans.in("status", request.getStatusList()));
    }

    // 金额范围
    if (request.getMinAmount() != null && request.getMaxAmount() != null) {
        filters.add(FilterBeans.between(
            "amount",
            request.getMinAmount(),
            request.getMaxAmount()
        ));
    }

    // 关键词搜索（订单号或客户名）
    if (StringHelper.isNotEmpty(request.getKeyword())) {
        filters.add(FilterBeans.or(
            FilterBeans.contains("orderNo", request.getKeyword()),
            FilterBeans.contains("customerName", request.getKeyword())
        ));
    }

    // 已支付或已取消
    filters.add(FilterBeans.or(
        FilterBeans.eq("status", "PAID"),
        FilterBeans.eq("status", "CANCELLED")
    ));

    // 未删除
    filters.add(FilterBeans.isNull("deletedAt"));

    // 组合条件
    QueryBean query = new QueryBean();
    query.setFilter(FilterBeans.and(filters));

    return query;
}
```

### 示例3：动态字段过滤

```java
public QueryBean buildDynamicFilter(Map<String, Object> filterParams) {
    List<TreeBean> filters = new ArrayList<>();

    for (Map.Entry<String, Object> entry : filterParams.entrySet()) {
        String field = entry.getKey();
        Object value = entry.getValue();

        if (value == null) {
            // 忽略null值，或使用isNull
            continue;
        }

        if (value instanceof Collection) {
            // 集合类型使用IN
            filters.add(FilterBeans.in(field, (Collection<?>) value));
        } else if (value instanceof String) {
            String strValue = (String) value;
            if (strValue.contains("%")) {
                // 包含通配符使用LIKE
                filters.add(FilterBeans.like(field, strValue));
            } else {
                // 普通字符串使用等于
                filters.add(FilterBeans.eq(field, strValue));
            }
        } else if (value instanceof Number) {
            // 数值类型使用等于
            filters.add(FilterBeans.eq(field, value));
        } else {
            // 其他类型也使用等于
            filters.add(FilterBeans.eq(field, value));
        }
    }

    QueryBean query = new QueryBean();
    query.setFilter(filters.isEmpty()
        ? FilterBeans.alwaysTrue()
        : FilterBeans.and(filters)
    );

    return query;
}
```

## 性能优化建议

### 1. 合理使用索引

```java
// 推荐：使用索引友好的条件
FilterBeans.eq("status", 1);
FilterBeans.between("createTime", start, end);

// 避免：对索引字段使用函数
// FilterBeans.eq("DATE(createTime)", today);  // 会导致索引失效
```

### 2. 控制条件复杂度

```java
// 推荐：平铺条件
FilterBeans.and(
    FilterBeans.eq("a", 1),
    FilterBeans.eq("b", 2),
    FilterBeans.eq("c", 3),
    FilterBeans.eq("d", 4),
    FilterBeans.eq("e", 5)
);

// 避免：过深的嵌套
FilterBeans.and(
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
);
```

### 3. 优先使用精确匹配

```java
// 推荐：精确匹配
FilterBeans.eq("status", 1);

// 次选：范围匹配
FilterBeans.between("age", 18, 30);

// 避免：模糊匹配（除非必要）
FilterBeans.contains("name", "test");  // 会导致全表扫描
```

### 4. 合理使用NULL判断

```java
// 推荐：使用专门的NULL判断方法
FilterBeans.isNull("deletedAt");
FilterBeans.notNull("createTime");

// 避免：使用eq判断NULL
// FilterBeans.eq("deletedAt", null);  // 可能不工作
```

## 与QueryBean的集成

### 设置到QueryBean

```java
// 方式1：直接设置
QueryBean query = new QueryBean();
query.setFilter(FilterBeans.and(
    FilterBeans.eq("status", 1),
    FilterBeans.gt("createTime", startDate)
));

// 方式2：逐步构建
QueryBean query = new QueryBean();
List<TreeBean> filters = new ArrayList<>();
filters.add(FilterBeans.eq("status", 1));
filters.add(FilterBeans.gt("createTime", startDate));
query.setFilter(FilterBeans.and(filters));

// 方式3：追加过滤条件
if (query.getFilter() == null) {
    query.setFilter(FilterBeans.eq("status", 1));
} else {
    query.setFilter(FilterBeans.and(
        query.getFilter(),
        FilterBeans.eq("status", 1)
    ));
}
```

## 常见问题

### Q1: 如何处理NULL值的比较？

**A**:
```java
// 查询NULL值
FilterBeans.isNull("field");

// 查询非NULL值
FilterBeans.notNull("field");

// 不要使用eq("field", null)
// FilterBeans.eq("field", null);  // 错误，不会工作
```

### Q2: 如何实现"包含但不包含某些"？

**A**:
```java
// 使用AND和NOT IN组合
FilterBeans.and(
    FilterBeans.in("id", Arrays.asList(1, 2, 3, 4, 5)),
    FilterBeans.notIn("id", Arrays.asList(2, 4))
);

// 等价于：id IN (1,2,3,4,5) AND id NOT IN (2,4)
// 结果：1, 3, 5
```

### Q3: 如何实现动态的AND/OR逻辑？

**A**:
```java
public TreeBean buildDynamicFilter(List<FilterCondition> conditions, boolean andOr) {
    if (conditions.isEmpty()) {
        return FilterBeans.alwaysTrue();
    }

    List<TreeBean> filters = conditions.stream()
        .map(this::buildSingleFilter)
        .collect(Collectors.toList());

    return andOr ? FilterBeans.and(filters) : FilterBeans.or(filters);
}
```

### Q4: LIKE查询如何避免SQL注入？

**A**:
```java
// 不推荐：直接拼接LIKE模式
// String pattern = "%" + userInput + "%";  // 有SQL注入风险

// 推荐：使用contains方法（会自动转义）
FilterBeans.contains("name", userInput);

// 或者使用正则表达式（更安全）
FilterBeans.regex("email", "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
```

### Q5: 如何查询枚举类型？

**A**:
```java
// 使用枚举值
FilterBeans.eq("status", UserStatus.ACTIVE);

// 使用枚举字符串
FilterBeans.eq("status", "ACTIVE");

// 使用IN查询多个枚举值
FilterBeans.in("status", Arrays.asList(
    UserStatus.ACTIVE,
    UserStatus.PENDING
));
```

## 相关文档

- [QueryBean使用指南](./querybean-guide.md) - 查询对象详解
- [IEntityDao使用指南](./data-access.md) - 数据访问接口详解
- [服务层开发指南](./service-layer.md) - BizModel开发详解
- [GraphQL服务开发指南](../api/graphql-guide.md) - GraphQL API开发
- [数据处理指南](./data-processing.md) - 数据处理指南

## 总结

FilterBeans提供了完整的查询条件构建能力：
1. **丰富的比较运算**：eq, ne, gt, ge, lt, le, between等
2. **集合运算**：in, notIn
3. **字符串匹配**：contains, startsWith, endsWith, like, regex
4. **空值判断**：isNull, notNull, isEmpty, isNotEmpty等
5. **逻辑运算**：and, or, not
6. **灵活的动态构建**：支持根据参数动态构建条件

在实际开发中：
- 简单查询使用FilterBeans快速构建
- 复杂查询组合多个条件
- 动态查询根据参数条件构建
- 注意性能优化，合理使用索引
