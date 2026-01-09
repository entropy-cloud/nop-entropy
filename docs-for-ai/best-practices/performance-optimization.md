# Nop Platform Performance Optimization Guide

## 概述

本文档提供Nop Platform性能优化的最佳实践和策略，帮助开发者构建高性能的应用系统。性能优化涉及多个层面，包括数据库、查询、缓存、事务等。

## 数据库优化

### 1. 索引优化

**原则**: 为常用查询字段添加索引

```java
@Entity(table = "nop_auth_user", indexes = {
    @Index(name = "idx_user_email", columns = {"email"}),
    @Index(name = "idx_user_status", columns = {"status", "createTime"})
})
public class User implements IOrmEntity {
    // ...
}
```

**索引使用场景**:
- WHERE条件字段
- JOIN连接字段
- ORDER BY排序字段
- GROUP BY分组字段

**注意事项**:
- 不要为所有字段添加索引（影响插入性能）
- 复合索引注意字段顺序
- 定期分析和重建索引

### 2. 批量操作

使用批量方法代替循环操作：

```java
// ✅ 推荐：批量操作
List<User> users = Arrays.asList(user1, user2, user3);
dao().batchSaveEntities(users);

// ❌ 不推荐：循环操作
for (User user : users) {
    dao().saveEntity(user);
}
```

**批量方法**:
- `batchSaveEntities()`: 批量保存
- `batchUpdateByIds()`: 批量更新
- `batchDeleteEntities()`: 批量删除
- `batchGetEntitiesByIds()`: 批量查询

### 3. 字段选择

只查询需要的字段：

```java
QueryBean query = new QueryBean();
query.setFilter(FilterBeans.eq("status", 1));

// ✅ 推荐：只查询需要的字段
query.setFields(Arrays.asList("userId", "userName", "email"));

// ❌ 不推荐：查询所有字段
List<User> users = dao().findAllByQuery(query);
```

### 4. 分页优化

合理设置分页大小：

```java
// ✅ 推荐：合理的分页大小
int pageSize = 20; // 前端列表
List<User> users = dao().findAllByQuery(query);

// ❌ 不推荐：过大或过小的分页大小
int pageSize = 1000; // 可能导致内存溢出
int pageSize = 1; // 增加数据库查询次数
```

**推荐分页大小**:
- 列表页面：10-50
- 表格页面：50-200
- 导出页面：500-2000

## 查询优化

### 1. 避免N+1查询

使用JOIN或批量加载：

```java
// ❌ 问题代码：N+1查询
List<Order> orders = dao().findAllByQuery(query);
for (Order order : orders) {
    User user = order.getUser(); // 每次都查询数据库
    System.out.println(user.getName());
}

// ✅ 优化方案1：JOIN查询
QueryBean query = new QueryBean();
query.setSources(List.of(
    new QuerySourceBean()
        .setAlias("o")
        .setEntityName("NopOrder"),
    new QuerySourceBean()
        .setAlias("u")
        .setEntityName("NopAuthUser")
        .setJoinType("LEFT")
        .setOnFilter(FilterBeans.eq("o.userId", "u.userId"))
));
List<Order> orders = dao().findAllByQuery(query);

// ✅ 优化方案2：批量加载
List<Order> orders = dao().findAllByQuery(query);
List<String> userIds = orders.stream()
    .map(Order::getUserId)
    .collect(Collectors.toList());
Map<String, User> userMap = userDao.batchGetEntitiesByIds(userIds)
    .stream()
    .collect(Collectors.toMap(User::getUserId, Function.identity()));
```

### 2. 使用合适的数据结构

根据场景选择查询方式：

```java
// 简单等值查询：使用Example
User example = new User();
example.setStatus(1);
List<User> users = dao().findAllByExample(example);

// 复杂条件查询：使用QueryBean
QueryBean query = new QueryBean();
query.setFilter(FilterBeans.and(
    FilterBeans.eq("status", 1),
    FilterBeans.contains("userName", keyword)
));
List<User> users = dao().findAllByQuery(query);

// 高性能查询：使用原生SQL
List<User> users = ormTemplate.queryList(
    "SELECT * FROM nop_auth_user WHERE status = ? AND user_name LIKE ?",
    1, "%" + keyword + "%"
);
```

### 3. 条件查询优化

动态构建查询条件：

```java
@BizQuery
public List<User> findUsers(UserFilter filter) {
    QueryBean query = new QueryBean();

    // 动态添加过滤条件
    List<TreeBean> filters = new ArrayList<>();
    if (StringHelper.isNotEmpty(filter.getKeyword())) {
        filters.add(FilterBeans.or(
            FilterBeans.contains("userName", filter.getKeyword()),
            FilterBeans.contains("email", filter.getKeyword())
        ));
    }
    if (filter.getStatus() != null) {
        filters.add(FilterBeans.eq("status", filter.getStatus()));
    }
    if (filter.getMinCreateTime() != null) {
        filters.add(FilterBeans.ge("createTime", filter.getMinCreateTime()));
    }

    if (!filters.isEmpty()) {
        query.setFilter(FilterBeans.and(filters));
    }

    return dao().findAllByQuery(query);
}
```

## 缓存优化

### 1. 二级缓存

对频繁访问但不常修改的数据启用缓存：

```java
@Entity
@Cacheable(
    cacheName = "userCache",
    cacheTimeout = 3600,  // 缓存1小时
    maxSize = 1000        // 最多缓存1000个对象
)
public class User implements IOrmEntity {
    // ...
}
```

**适用场景**:
- 系统配置数据
- 字典数据
- 用户权限数据
- 不经常修改的实体

**不适用场景**:
- 频繁修改的数据
- 大对象数据
- 实时性要求高的数据

### 2. 查询缓存

对重复查询启用缓存：

```java
@BizQuery
@Cacheable(cacheName = "userQueryCache", timeout = 300)
public User getUser(String userId) {
    return dao().getEntityById(userId);
}
```

### 3. 缓存失效

正确处理缓存失效：

```java
@BizMutation
@Transactional
public User updateUser(User user) {
    User updated = dao().saveEntity(user);

    // 手动失效缓存
    cacheManager.remove("userCache", user.getUserId());

    return updated;
}
```

## 事务优化

### 1. 事务边界

事务边界尽可能小：

```java
// ✅ 推荐：小事务
@BizMutation
@Transactional
public void updateUser(String userId, String newName) {
    User user = dao().getEntityById(userId);
    user.setName(newName);
    dao().saveEntity(user);
}

// ❌ 不推荐：大事务
@BizMutation
@Transactional
public void processOrder(String orderId) {
    // 1. 查询订单
    Order order = dao().getEntityById(orderId);

    // 2. 更新库存
    updateInventory(order);

    // 3. 创建物流
    createShipping(order);

    // 4. 发送通知
    sendNotification(order);

    // 5. 记录日志
    logOrder(order);
    // 事务时间过长
}
```

### 2. 避免在事务中执行IO操作

```java
// ❌ 错误：在事务中执行IO
@BizMutation
@Transactional
public void createUser(User user) {
    dao().saveEntity(user);
    sendEmail(user);  // IO操作在事务中
}

// ✅ 正确：事务提交后执行IO
@BizMutation
@Transactional
public void createUser(User user) {
    dao().saveEntity(user);
}

@AfterTransactionCommit
public void afterCreateUser(User user) {
    sendEmail(user);  // 事务提交后执行
}
```

### 3. 选择合适的事务传播级别

```java
// 查询方法：不使用事务
@BizQuery
public User getUser(String userId) {
    return dao().getEntityById(userId);
}

// 写操作：使用默认事务
@BizMutation
@Transactional
public User createUser(User user) {
    return dao().saveEntity(user);
}

// 独立事务：使用REQUIRES_NEW
@BizMutation
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void logOperation(OperationLog log) {
    dao().saveEntity(log);
}
```

## ORM优化

### 1. 懒加载vs急加载

根据场景选择加载策略：

```java
// 场景1：只查询用户基本信息，不需要部门信息
User user = dao().getEntityById("1");
// dept对象不加载

// 场景2：需要部门信息
User user = dao().getEntityById("1");
dao().loadProps(user, Arrays.asList("dept"));
// dept对象预加载
```

### 2. 批量加载关联

```java
// ❌ 问题：N+1查询
List<Order> orders = dao().findAllByQuery(query);
for (Order order : orders) {
    List<OrderItem> items = order.getItems(); // 每次都查询
    processItems(items);
}

// ✅ 优化：批量加载
List<Order> orders = dao().findAllByQuery(query);
List<String> orderIds = orders.stream()
    .map(Order::getOrderId)
    .collect(Collectors.toList());
Map<String, List<OrderItem>> itemMap = orderItemDao
    .batchGetItemsByOrderIds(orderIds);

for (Order order : orders) {
    List<OrderItem> items = itemMap.get(order.getOrderId());
    processItems(items);
}
```

### 3. 使用DTO

对于复杂查询，使用DTO减少数据传输：

```java
@BizQuery
public List<UserDTO> getUserSummary(QueryBean query) {
    // 只返回需要的字段
    query.setFields(Arrays.asList("userId", "userName", "status"));
    List<User> users = dao().findAllByQuery(query);

    // 转换为DTO
    return users.stream()
        .map(user -> new UserDTO(
            user.getUserId(),
            user.getUserName(),
            user.getStatus()
        ))
        .collect(Collectors.toList());
}
```

## GraphQL优化

### 1. 字段选择

GraphQL查询时只选择需要的字段：

```graphql
# ✅ 推荐：只查询需要的字段
query {
  User {
    getUser(userId: "1") {
      userId
      userName
      email
    }
  }
}

# ❌ 不推荐：查询所有字段
query {
  User {
    getUser(userId: "1") {
      userId
      userName
      email
      phone
      address
      # ...
    }
  }
}
```

### 2. 批量查询

使用GraphQL DataLoader避免N+1：

```java
@Component
public class UserDataLoader implements IGraphQLDataLoader {
    @Override
    public List<User> load(List<String> userIds) {
        return userDao.batchGetEntitiesByIds(userIds);
    }
}
```

```graphql
query {
  Order {
    getOrders(userId: "1") {
      orderId
      orderNo
      user {
        userId
        userName  # 使用DataLoader批量加载
      }
    }
  }
}
```

### 3. 分页查询

使用分页避免一次性加载过多数据：

```graphql
query {
  User {
    findUsers(pageNo: 1, pageSize: 20) {
      pageNo
      pageSize
      totalCount
      items {
        userId
        userName
      }
    }
  }
}
```

## 并发优化

### 1. 读写分离

配置主从数据库：

```yaml
# application.yaml
datasource:
  master:
    url: jdbc:mysql://master-host:3306/nop
    username: root
    password: password
  slave:
    url: jdbc:mysql://slave-host:3306/nop
    username: root
    password: password
```

```java
// 查询操作使用从库
@BizQuery
public User getUser(String userId) {
    return slaveDao.getEntityById(userId);
}

// 写操作使用主库
@BizMutation
@Transactional
public User createUser(User user) {
    return masterDao.saveEntity(user);
}
```

### 2. 连接池优化

配置合适的连接池参数：

```yaml
datasource:
  hikari:
    maximum-pool-size: 20
    minimum-idle: 5
    idle-timeout: 600000
    max-lifetime: 1800000
    connection-timeout: 30000
```

## 监控和诊断

### 1. 慢查询日志

记录慢查询：

```java
@Component
public class SlowQueryInterceptor implements IOrmInterceptor {
    @Override
    public void afterQuery(String sql, long costTime) {
        if (costTime > 1000) { // 超过1秒
            log.warn("Slow query: {} ({}ms)", sql, costTime);
        }
    }
}
```

### 2. 性能监控

使用Micrometer监控性能：

```java
@Component
public class PerformanceMonitor {
    private final MeterRegistry registry;

    @BizQuery
    public User getUser(String userId) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return dao().getEntityById(userId);
        } finally {
            sample.stop(Timer.builder("user.get")
                .description("Time to get user")
                .register(registry));
        }
    }
}
```

### 3. 缓存命中率

监控缓存命中率：

```java
@Component
public class CacheMonitor {
    public void printCacheStats() {
        CacheStats stats = cacheManager.getStats("userCache");
        log.info("Cache hit rate: {}%", stats.getHitRate() * 100);
        log.info("Cache size: {}", stats.getSize());
    }
}
```

## 性能测试

### 1. 压力测试

使用JMeter进行压力测试：

1. 创建测试计划
2. 模拟多用户并发访问
3. 监控响应时间、吞吐量
4. 识别性能瓶颈

### 2. 性能分析

使用JProfiler或VisualVM分析：

1. 启动性能分析
2. 执行典型场景
3. 分析热点方法
4. 优化慢代码

## 常见性能问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| N+1查询 | 循环中查询关联数据 | 使用JOIN或批量加载 |
| 大事务 | 事务包含过多操作 | 减小事务边界 |
| 缺少索引 | 常用查询字段无索引 | 添加索引 |
| 过度缓存 | 缓存了不常修改的数据 | 合理使用缓存 |
| 循环查询 | 在循环中执行数据库查询 | 改用批量查询 |
| 深层关联 | 多层JOIN查询 | 优化查询或使用缓存 |
| 全表扫描 | 查询条件不使用索引 | 添加索引或优化查询 |

## 相关文档

- [ORM架构文档](../architecture/backend/orm-architecture.md)
- [IEntityDao使用指南](../getting-started/dao/ientitydao-usage.md)
- [QueryBean使用指南](../getting-started/dao/querybean-guide.md)
- [事务管理指南](../getting-started/core/transaction-guide.md)
- [缓存配置](../getting-started/core/cache-guide.md)

## 总结

Nop Platform性能优化是一个系统工程，需要从多个层面综合考虑：

1. **数据库优化**: 索引、批量操作、字段选择
2. **查询优化**: 避免N+1、使用合适的数据结构
3. **缓存优化**: 合理使用二级缓存和查询缓存
4. **事务优化**: 控制事务边界、避免事务中IO
5. **ORM优化**: 合理选择加载策略、使用DTO
6. **监控诊断**: 记录慢查询、性能监控、持续优化

遵循这些最佳实践，可以构建高性能、可扩展的Nop Platform应用系统。

---

**文档版本**: 1.0
**最后更新**: 2025-01-09
**作者**: AI Assistant (Sisyphus)
