# GraphQL API Examples

## 概述

本文档提供Nop Platform GraphQL API的完整示例，展示如何定义查询、变更、订阅等操作，以及如何处理参数、错误、分页等场景。

## 基本查询

### 1. 单个实体查询

```graphql
query GetUser {
  DemoUser {
    getUser(userId: "001") {
      userId
      userName
      email
      phone
      status
      createTime
    }
  }
}
```

**Java实现**:
```java
@BizQuery
public DemoUser getUser(@Name("userId") String userId) {
    return dao().getEntityById(userId);
}
```

### 2. 列表查询

```graphql
query GetUsers {
  DemoUser {
    findUsers(status: 1, pageNo: 1, pageSize: 20) {
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

**Java实现**:
```java
@BizQuery
public PageBean<DemoUser> findUsers(@Name("status") Integer status,
                                     @Name("pageNo") Integer pageNo,
                                     @Name("pageSize") Integer pageSize) {
    QueryBean query = new QueryBean();
    if (status != null) {
        query.setFilter(FilterBeans.eq("status", status));
    }

    return findPage(query, pageNo, pageSize);
}
```

### 3. 条件查询

```graphql
query SearchUsers {
  DemoUser {
    findUsers(keyword: "zhang", status: 1, pageNo: 1, pageSize: 20) {
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

**Java实现**:
```java
@BizQuery
public PageBean<DemoUser> findUsers(@Name("keyword") String keyword,
                                     @Name("status") Integer status,
                                     @Name("pageNo") Integer pageNo,
                                     @Name("pageSize") Integer pageSize) {
    QueryBean query = new QueryBean();

    List<TreeBean> filters = new ArrayList<>();
    if (StringHelper.isNotEmpty(keyword)) {
        filters.add(FilterBeans.or(
            FilterBeans.contains("userName", keyword),
            FilterBeans.contains("email", keyword)
        ));
    }
    if (status != null) {
        filters.add(FilterBeans.eq("status", status));
    }

    if (!filters.isEmpty()) {
        query.setFilter(FilterBeans.and(filters));
    }

    return findPage(query, pageNo, pageSize);
}
```

## 变更操作

### 1. 创建实体

```graphql
mutation CreateUser {
  DemoUser {
    createUser(user: {
      userName: "zhangsan"
      email: "zhangsan@example.com"
      phone: "13800138000"
    }) {
      userId
      userName
      email
      phone
      status
      createTime
    }
  }
}
```

**Java实现**:
```java
@BizMutation
@Transactional
public DemoUser createUser(@Name("user") DemoUser user) {
    // 验证输入
    validateUser(user);

    // 设置默认值
    user.setUserId(IdGenerator.nextId());
    user.setStatus(1); // 正常状态
    user.setCreateTime(new Date());
    user.setUpdateTime(new Date());

    // 保存用户
    return dao().saveEntity(user);
}
```

### 2. 更新实体

```graphql
mutation UpdateUser {
  DemoUser {
    updateUser(user: {
      userId: "001"
      userName: "lisi"
      email: "lisi@example.com"
      phone: "13800138001"
    }) {
      userId
      userName
      email
      phone
      updateTime
    }
  }
}
```

**Java实现**:
```java
@BizMutation
@Transactional
public DemoUser updateUser(@Name("user") DemoUser user) {
    if (StringHelper.isEmpty(user.getUserId())) {
        throw new NopException(ERR_USER_ID_REQUIRED);
    }

    // 获取现有用户
    DemoUser existing = dao().requireEntityById(user.getUserId());

    // 更新字段
    if (StringHelper.isNotEmpty(user.getUserName())) {
        existing.setUserName(user.getUserName());
    }
    if (StringHelper.isNotEmpty(user.getEmail())) {
        existing.setEmail(user.getEmail());
    }
    if (StringHelper.isNotEmpty(user.getPhone())) {
        existing.setPhone(user.getPhone());
    }

    existing.setUpdateTime(new Date());

    return dao().saveEntity(existing);
}
```

### 3. 删除实体

```graphql
mutation DeleteUser {
  DemoUser {
    deleteUser(userId: "001") {
      userId
      userName
      email
      __typename
    }
  }
}
```

**Java实现**:
```java
@BizMutation
@Transactional
public void deleteUser(@Name("userId") String userId) {
    DemoUser user = dao().requireEntityById(userId);
    dao().deleteEntity(user);
}
```

### 4. 批量更新

```graphql
mutation BatchUpdateUserStatus {
  DemoUser {
    batchUpdateStatus(userIds: ["001", "002", "003"], status: 0) {
      __typename
    }
  }
}
```

**Java实现**:
```java
@BizMutation
@Transactional
public int batchUpdateStatus(@Name("userIds") List<String> userIds,
                           @Name("status") Integer status) {
    if (userIds == null || userIds.isEmpty()) {
        throw new NopException(ERR_INVALID_INPUT)
                .param("field", "userIds");
    }

    List<DemoUser> users = dao().batchGetEntitiesByIds(userIds);

    for (DemoUser user : users) {
        user.setStatus(status);
        user.setUpdateTime(new Date());
    }

    return dao().batchSaveEntities(users);
}
```

## 复杂查询

### 1. 关联查询

```graphql
query GetOrdersWithUser {
  Order {
    findOrders(status: 1, pageNo: 1, pageSize: 20) {
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

**Java实现**:
```java
@BizQuery
public PageBean<Order> findOrders(@Name("status") Integer status,
                                  @Name("pageNo") Integer pageNo,
                                  @Name("pageSize") Integer pageSize) {
    QueryBean query = new QueryBean();

    // 添加关联查询
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

    if (status != null) {
        query.setFilter(FilterBeans.eq("o.status", status));
    }

    query.setOrderField("o.createTime");
    query.setOrderDesc(true);

    return findPage(query, pageNo, pageSize);
}
```

### 2. 字段选择

```graphql
query GetUsersWithFields {
  DemoUser {
    findUsers(pageNo: 1, pageSize: 20) {
      items {
        userId
        userName
        email
        # 只查询需要的字段
      }
      totalCount
    }
  }
}
```

**Java实现**:
```java
@BizQuery
public PageBean<DemoUser> findUsers(@Name("pageNo") Integer pageNo,
                                     @Name("pageSize") Integer pageSize) {
    QueryBean query = new QueryBean();

    // 只查询需要的字段
    query.setFields(List.of("userId", "userName", "email"));

    query.setOrderField("createTime");
    query.setOrderDesc(true);

    return findPage(query, pageNo, pageSize);
}
```

### 3. 聚合查询

```graphql
query GetUserStatistics {
  DemoUser {
    getUserStatistics {
      totalUsers
      activeUsers
      inactiveUsers
      avgAge
    }
  }
}
```

**Java实现**:
```java
@BizQuery
public Map<String, Object> getUserStatistics() {
    QueryBean query = new QueryBean();

    // 添加聚合字段
    query.setAggregates(List.of(
        new QueryAggregateFieldBean()
            .setAlias("userCount")
            .setFunction("COUNT")
            .setField("*"),
        new QueryAggregateFieldBean()
            .setAlias("activeCount")
            .setFunction("COUNT")
            .setField("*"),
            new QueryAggregateFieldBean()
            .setAlias("avgAge")
            .setFunction("AVG")
            .setField("age")
    ));

    // 查询条件
    query.setFilter(FilterBeans.eq("status", 1));

    return dao().aggregateByQuery(query);
}
```

## 参数传递

### 1. 基本类型

```graphql
query {
  DemoUser {
    getUser(userId: "001", status: 1)
  }
}
```

### 2. 复杂对象

```graphql
query {
  DemoUser {
    createUser(user: {
      userName: "zhangsan"
      profile: {
        firstName: "San"
        lastName: "Zhang"
        age: 30
      }
    })
  }
}
```

**Java实现**:
```java
@BizMutation
@Transactional
public DemoUser createUser(@Name("user") DemoUser user) {
    // 对象类型：DemoUser包含UserProfile类型
    return dao().saveEntity(user);
}
```

### 3. 列表参数

```graphql
mutation BatchUpdateUsers {
  DemoUser {
    batchUpdateStatus(userIds: ["001", "002", "003"], status: 0)
  }
}
```

**Java实现**:
```java
@BizMutation
@Transactional
public int batchUpdateStatus(@Name("userIds") List<String> userIds,
                           @Name("status") Integer status) {
    // 数组类型：@Name("userIds") String[]
    List<String> idArray = userIds;

    List<DemoUser> users = dao().batchGetEntitiesByIds(idArray);

    for (DemoUser user : users) {
        user.setStatus(status);
        user.setUpdateTime(new Date());
    }

    return dao().batchSaveEntities(users);
}
```

## 错误处理

### 1. 业务错误

```graphql
mutation {
  DemoUser {
    createUser(user: {userName: "existing@example.com"}) {
      __typename
    }
  }
}
```

**错误响应**:
```json
{
  "data": {
    "DemoUser": {
      "createUser": null,
      "__typename": "DemoUser"
    }
  },
  "errors": [
    {
      "message": "邮箱已存在",
      "path": ["DemoUser", "createUser"],
      "extensions": {
        "code": "nop.err.user:email-exists",
        "args": {
          "email": "existing@example.com"
        }
      }
    }
  ]
}
```

### 2. 参数验证错误

```graphql
mutation {
  DemoUser {
    updateUser(user: {userId: "001", userName: ""})
  }
}
```

**错误响应**:
```json
{
  "data": {
    "DemoUser": {
      "updateUser": null,
      "__typename": "DemoUser"
    }
  },
  "errors": [
    {
      "message": "用户名不能为空",
      "path": ["DemoUser", "updateUser"],
      "extensions": {
        "code": "nop.err.user:name-required",
        "args": {
          "field": "userName"
        }
      }
    }
  ]
}
```

### 3. 资源不存在错误

```graphql
query {
  DemoUser {
    getUser(userId: "999")
  }
}
```

**错误响应**:
```json
{
  "data": {
    "DemoUser": {
      "getUser": null,
      "__typename": "DemoUser"
    }
  },
  "errors": [
    {
      "message": "用户不存在",
      "path": ["DemoUser", "getUser"],
      "extensions": {
        "code": "nop.err.user:not-found",
        "args": {
          "userId": "999"
        }
      }
    }
  ]
}
```

## 订阅操作

### 1. 实时订阅

```graphql
subscription {
  Message {
    onNewMessage(userId: "001") {
      messageId
      senderId
      receiverId
      content
      createTime
    }
  }
}
```

**Java实现**:
```java
@BizSubscription
public Publisher<Message> onNewMessage(@Name("userId") String userId) {
    // 返回Publisher供GraphQL订阅
    return messageStream.filter(msg ->
        msg.getReceiverId().equals(userId)
    );
}
```

### 2. 批量更新订阅

```graphql
subscription {
  User {
    onUserUpdates(userIds: ["001", "002", "003"]) {
      userId
      userName
      email
      status
      __typename
    }
  }
}
```

## 片段查询

### 1. 使用Fragments

```graphql
fragment UserFields on User {
  userId
  userName
  email
}

query GetUsers {
  DemoUser {
    findUsers(pageNo: 1, pageSize: 20) {
      items {
        ...UserFields
        status
        createTime
      }
    }
  }
}

query GetUser {
  DemoUser {
    getUser(userId: "001") {
      ...UserFields
      phone
      status
    }
  }
}
```

### 2. 内联片段

```graphql
query {
  DemoUser {
    findUsers(pageNo: 1, pageSize: 20) {
      items {
        userId
        userName
        ... on User {
          email
          status
        }
      }
    }
  }
}
```

### 3. 组合片段

```graphql
fragment UserWithProfile on User {
  userId
  userName
  email
  profile {
    firstName
    lastName
    age
  }
}

query GetUsers {
  DemoUser {
    findUsers(pageNo: 1, pageSize: 20) {
      items {
        ...UserWithProfile
        status
        createTime
      }
    }
  }
}
```

## 完整业务场景

### 1. 用户注册流程

```graphql
mutation RegisterUser {
  DemoUser {
    register(user: {
      userName: "zhangsan"
      email: "zhangsan@example.com"
      password: "password123"
    }) {
      userId
      userName
      email
      status
    }
  }
}
```

### 2. 使用DataLoader的复杂关联查询场景

```graphql
# 复杂关联查询 - 使用DataLoader优化性能
query GetUserDashboard {
  DemoUser {
    findUsers(pageNo: 1, pageSize: 10) {
      items {
        userId
        userName
        email
        # 关联角色信息 - DataLoader批量加载
        roles {
          roleId
          roleName
          # 角色权限 - 嵌套DataLoader
          permissions {
            permId
            permName
            resource
          }
        }
        # 用户统计信息 - DataLoader扩展字段
        statistics {
          orderCount
          totalAmount
          lastLoginTime
        }
        # 显示名称 - DataLoader计算字段
        displayName
        # 状态描述 - DataLoader转换字段
        statusText
      }
    }
  }
}
```

> **Java 实现**：参见 [BizModel 编写指南 - DataLoader 章节](../03-development-guide/bizmodel-guide.md#dataloader--bizloader)

### 2. 订单创建流程

```graphql
mutation CreateOrder {
  Order {
    createOrder(order: {
      userId: "001"
      items: [
        {productId: "p001", quantity: 2}
        {productId: "p002", quantity: 1}
      ]
      payment: {
        type: "ALIPAY"
        amount: 300.00
      }
    }) {
      orderId
      orderNo
      status
      user {
        userId
        userName
      }
      items {
        itemId
        productId
        quantity
        price
      }
      payment {
        paymentId
        status
      }
    }
  }
}
```

### 3. 复杂搜索场景

```graphql
query SearchOrders {
  Order {
    searchOrders(request: {
      keyword: "订单"
      status: 1
      startTime: "2024-01-01T00:00:00Z"
      endTime: "2024-12-31T23:59:59Z"
      minAmount: 100
      maxAmount: 10000
      pageNo: 1
      pageSize: 20
      sortBy: "createTime"
      sortOrder: "DESC"
    }) {
      pageNo
      pageSize
      totalCount
      items {
        orderId
        orderNo
        amount
        status
        createTime
        user {
          userId
          userName
        }
      }
    }
  }
}
```

## 性能优化

### 1. 使用字段选择

```graphql
query {
  DemoUser {
    findUsers(pageNo: 1, pageSize: 20) {
      # 只查询需要的字段
      items {
        userId
        userName
      }
      totalCount
    }
  }
}
```

### 2. 使用批量查询

```graphql
query BatchGetUsers {
  DemoUser {
    batchGetUsers(userIds: ["001", "002", "003"]) {
      userId
      userName
      email
    }
  }
}
```

### 3. DataLoader机制解决N+1查询问题

GraphQL查询中常见的N+1查询问题可以通过@BizLoader注解和DataLoader机制解决：

```graphql
# 查询用户及其角色信息
query GetUsersWithRoles {
  DemoUser {
    findUsers(pageNo: 1, pageSize: 10) {
      items {
        userId
        userName
        email
        # 关联角色信息 - 使用DataLoader避免N+1查询
        roles {
          roleId
          roleName
        }
        # 扩展字段 - 使用DataLoader动态计算
        displayName
      }
    }
  }
}
```

> **Java 实现**：参见 [BizModel 编写指南 - DataLoader 章节](../03-development-guide/bizmodel-guide.md#dataloader--bizloader)

### 4. 查询优化最佳实践

```graphql
# 优化后的查询示例
query OptimizedUserQuery {
  DemoUser {
    findUsers(
      pageNo: 1
      pageSize: 20
      # 使用过滤条件减少数据量
      status: 1
      # 只查询需要的字段
      fields: ["userId", "userName", "email"]
    ) {
      items {
        userId
        userName
        email
        # 关联数据使用DataLoader
        roles {
          roleId
          roleName
        }
        # 扩展字段
        displayName
      }
      totalCount
    }
  }
}
```

### 5. 限制返回数量

```graphql
query {
  DemoUser {
    findUsers(pageNo: 1, pageSize: 20) {
      items {
        userId
        userName
      }
      totalCount
    }
  }
}
```

## 安全考虑

### 1. 权限验证

GraphQL API会自动进行权限验证：

```graphql
mutation DeleteUser {
  DemoUser {
    deleteUser(userId: "001")
  }
}
```

如果当前用户没有删除权限，会返回权限错误。

### 2. 参数验证

所有参数都会自动验证：

```graphql
query {
  DemoUser {
    getUser(userId: "")
  }
}
```

如果userId为空，会返回参数错误。

### 3. 查询深度限制

Nop Platform有查询深度限制：

```graphql
query DeepQuery {
  Order {
    findOrders {
      items {
        user {
          orders {
            items {
              # 多层嵌套可能超过限制
            }
          }
        }
      }
    }
  }
}
```

## 测试工具

### 1. GraphQL Playground

如果运行框架/依赖启用了 GraphQL UI（有的 Quarkus 配置可能提供 `/q/graphql-ui`），可以直接在浏览器里调试 GraphQL。

### 2. 图形化查询

使用Altair、GraphiQL等工具：

```graphql
# Altair示例
query GetUser($userId: String!) {
  DemoUser {
    getUser(userId: $userId) {
      userId
      userName
      email
    }
  }
}

# 变量
{
  "userId": "001"
}
```

### 3. 自动化测试

使用 Nop 平台的测试框架（继承 `JunitBaseTestCase` 并注入 `IGraphQLEngine`）：

```java
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.IGraphQLEngine;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.test.core.annotation.NopTestConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true)
public class GraphQLTest extends JunitBaseTestCase {

    @Inject
    protected IGraphQLEngine graphQLEngine;

    @Test
    public void testGetUser() {
        // 1. 构建 GraphQL 请求
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery("{ DemoUser { getUser(userId: \"001\") { userId userName } } }");

        // 2. 创建执行上下文
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);

        // 3. 执行查询，返回 GraphQLResponseBean
        GraphQLResponseBean response = graphQLEngine.executeGraphQL(context);

        // 4. 验证没有错误
        assertFalse(response.hasError());

        // 5. 验证返回数据
        Map<String, Object> data = (Map<String, Object>) response.getData();
        Map<String, Object> demoUser = (Map<String, Object>) data.get("DemoUser");
        Map<String, Object> user = (Map<String, Object>) demoUser.get("getUser");

        assertEquals("001", user.get("userId"));
        assertEquals("testuser", user.get("userName"));
    }
}
```

## 相关文档

- [API 开发指南](../03-development-guide/api-development.md)
- [GraphQL 架构](../02-architecture/graphql-architecture.md)
- [BizModel 编写指南](../03-development-guide/bizmodel-guide.md)（含 DataLoader）
- [后端架构](../02-architecture/backend-architecture.md)

## 总结

本示例展示了Nop Platform GraphQL API的完整使用场景：

1. **基本查询**: 单个实体、列表查询、条件查询
2. **变更操作**: 创建、更新、删除、批量更新
3. **复杂查询**: 关联查询、字段选择、聚合查询
4. **参数传递**: 基本类型、复杂对象、列表参数
5. **错误处理**: 业务错误、参数错误、资源不存在
6. **订阅操作**: 实时订阅、批量更新订阅
7. **Fragments**: 使用片段复用查询
8. **性能优化**: 字段选择、批量查询、限制数量
9. **安全考虑**: 权限验证、参数验证、查询深度限制
10. **测试工具**: Playground、图形化工具、自动化测试

通过这些示例，可以快速理解和使用Nop Platform的GraphQL API。
