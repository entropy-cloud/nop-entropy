# GraphQL服务开发指南

## 概述

Nop平台的GraphQL服务基于BizModel设计，提供了将Java类转换为GraphQL API的能力。通过简单的注解和继承，即可创建完整的GraphQL服务，包括查询、变更、订阅等。

**核心注解**：`@BizModel`, `@BizQuery`, `@BizMutation`, `@BizAction`
**基类**：`CrudBizModel<T>`（提供内置CRUD操作）

## 基本BizModel

### 简单BizModel

```java
import io.nop.api.core.annotations.biz.BizModel;

@BizModel
public class UserBizModel {
    // BizModel会自动转换为GraphQL类型和服务
}
```

### 继承CrudBizModel

```java
import io.nop.biz.crud.CrudBizModel;
import io.nop.api.core.annotations.biz.BizModel;

@BizModel("NopAuthUser")
public class UserBizModel extends CrudBizModel<NopAuthUser> {
    public UserBizModel() {
        setEntityName(NopAuthUser.class.getName());
    }
}
```

## 查询方法

### @BizQuery注解

```java
import io.nop.api.core.annotations.biz.BizQuery;

@BizQuery
public User getUserById(String userId) {
    return dao().getEntityById(userId);
}
```

### 使用QueryBean查询

```java
@BizQuery
public PageBean<User> findUsers(QueryBean query) {
    return findPage(query, 1, 20);
}
```

### 查找第一条记录

```java
@BizQuery
public User findFirstUser() {
    return dao().findFirstByExample(new User());
}
```

### 查询记录数

```java
@BizQuery
public long countUsers(QueryBean query) {
    return findCount(query);
}
```

## 变更方法

### @BizMutation注解

```java
import io.nop.api.core.annotations.biz.BizMutation;

@BizMutation
public User createUser(User user) {
    return save(user);
}
```

### 更新实体

```java
@BizMutation
public User updateUser(User user) {
    User existingUser = dao().requireEntityById(user.getId());
    existingUser.setName(user.getName());
    existingUser.setEmail(user.getEmail());
    dao().saveEntity(existingUser);
    return existingUser;
}
```

### 删除实体

```java
@BizMutation
public void deleteUser(String userId) {
    User user = dao().requireEntityById(userId);
    dao().deleteEntity(user);
}
```

### 事务操作

```java
import io.nop.dao.txn.ITransactionTemplate;

@BizMutation
public void updateUserWithAudit(String userId, String newName) {
    txn(() -> {
        User user = dao().requireEntityById(userId);
        String oldName = user.getName();

        // 更新用户
        user.setName(newName);
        dao().saveEntity(user);

        // 记录审计日志
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setOldName(oldName);
        log.setNewName(newName);
        auditLogDao.saveEntity(log);
    });
}
```

## 动作方法

### @BizAction注解

```java
import io.nop.api.core.annotations.biz.BizAction;

@BizAction
public void resetUserPassword(String userId, String newPassword) {
    User user = dao().requireEntityById(userId);
    user.setPassword(passwordEncoder.encode(newPassword));
    dao().saveEntity(user);
}
```

## 参数定义

### 基本参数

```java
@BizQuery
public User findUserById(String userId) {
    return dao().getEntityById(userId);
}
```

### Optional参数

```java
import io.nop.api.core.annotations.core.Optional;

@BizQuery
public List<User> findUsers(String keyword, @Optional String status) {
    QueryBean query = new QueryBean();
    query.setFilter(FilterBeans.and(
        FilterBeans.contains("name", keyword),
        status != null ? FilterBeans.eq("status", status) : FilterBeans.alwaysTrue()
    ));
    return dao().findAllByQuery(query);
}
```

### Map参数

```java
@BizMutation
public User saveUser(Map<String, Object> data) {
    User user = ConvertHelper.toBean(data, User.class);
    dao().saveEntity(user);
    return user;
}
```

## 返回类型

### 基本返回类型

```java
@BizQuery
public User getUserById(String userId) {
    return dao().getEntityById(userId);
}

@BizQuery
public List<User> getAllUsers() {
    return dao().findAllByExample(new User());
}

@BizQuery
public PageBean<User> findUsers(QueryBean query) {
    return findPage(query, pageNo, pageSize);
}
```

### 自定义返回类型

```java
@BizQuery
public UserDetail getUserDetail(String userId) {
    User user = dao().requireEntityById(userId);

    UserDetail detail = new UserDetail();
    detail.setUser(user);
    detail.setRoles(user.getRoles());
    detail.setDepartments(user.getDepartments());

    return detail;
}
```

## 完整示例

### 用户管理BizModel

```java
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.biz.crud.CrudBizModel;
import io.nop.dao.txn.ITransactionTemplate;
import jakarta.inject.Inject;

@BizModel("NopAuthUser")
public class UserBizModel extends CrudBizModel<NopAuthUser> {

    @Inject
    private ITransactionTemplate txnTemplate;

    public UserBizModel() {
        setEntityName(NopAuthUser.class.getName());
    }

    // 查询方法
    @BizQuery
    public PageBean<NopAuthUser> findUsers(QueryBean query) {
        return findPage(query);
    }

    @BizQuery
    public NopAuthUser getUserByOpenId(String openId) {
        NopAuthUser example = new NopAuthUser();
        example.setOpenId(openId);
        return dao().findFirstByExample(example);
    }

    // 变更方法
    @BizMutation
    public NopAuthUser createUser(NopAuthUser user) {
        // 默认值
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());

        return save(user);
    }

    @BizMutation
    public void updateUser(NopAuthUser user) {
        NopAuthUser existingUser = dao().requireEntityById(user.getId());

        // 只更新允许的字段
        existingUser.setName(user.getName());
        existingUser.setEmail(user.getEmail());
        existingUser.setPhone(user.getPhone());

        dao().saveEntity(existingUser);
    }

    @BizMutation
    public void changePassword(@Name("userId") String userId,
                          @Name("oldPassword") String oldPassword,
                          @Name("newPassword") String newPassword) {
        txnTemplate.runInTransaction(txn -> {
            NopAuthUser user = dao().requireEntityById(userId);

            // 验证旧密码
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                throw new NopException(MyErrors.ERR_PASSWORD_INCORRECT);
            }

            // 验证新密码
            passwordPolicy.checkPassword(newPassword);

            // 更新密码
            user.setPassword(passwordEncoder.encode(newPassword));
            dao().saveEntity(user);
        });
    }

    // 动作方法
    @BizAction
    public void resetPassword(String userId, String newPassword) {
        NopAuthUser user = dao().requireEntityById(userId);

        passwordPolicy.checkPassword(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        dao().saveEntity(user);
    }

    @BizAction
    public void activateUser(String userId) {
        NopAuthUser user = dao().requireEntityById(userId);
        user.setStatus(1);
        dao().saveEntity(user);
    }

    @BizAction
    public void deactivateUser(String userId) {
        NopAuthUser user = dao().requireEntityById(userId);
        user.setStatus(0);
        dao().saveEntity(user);
    }
}
```

## GraphQL Schema生成

### 自动生成类型

Nop平台会根据以下信息自动生成GraphQL Schema：
1. **@BizModel注解**：生成GraphQL Object Type
2. **实体类**：从XMeta或实体注解生成字段定义
3. **方法签名**：生成GraphQL Field

### 生成的Schema示例

```graphql
type NopAuthUser {
  id: ID!
  name: String
  email: String
  status: Int
  createTime: DateTime
  updateTime: DateTime
}

type UserBizModel {
  findUsers(offset: Int, limit: Int, filter: FilterBean): PageBean
  getUserById(userId: ID): NopAuthUser
  getUserByOpenId(openId: String): NopAuthUser
  createUser(user: NopAuthUserInput): NopAuthUser
  updateUser(user: NopAuthUserInput): Void
  changePassword(userId: ID, oldPassword: String, newPassword: String): Void
  resetPassword(userId: ID, newPassword: String): Void
  activateUser(userId: ID): Void
  deactivateUser(userId: ID): Void
}
```

## GraphQL查询示例

### 查询单个用户

```graphql
query {
  getUserById(userId: "123") {
    id
    name
    email
    status
  }
}
```

### 分页查询用户

```graphql
query {
  findUsers(offset: 0, limit: 20) {
    items {
      id
      name
      email
    }
    total
  }
}
```

### 条件查询

```graphql
query {
  findUsers(offset: 0, limit: 20, filter: {
    op: "AND",
    children: [
      { name: "status", value: 1 },
      { name: "name", op: "contains", value: "test" }
    ]
  }) {
    items {
      id
      name
    }
  }
}
```

### GraphQL变更示例

### 创建用户

```graphql
mutation {
  createUser(user: {
    name: "Alice"
    email: "alice@example.com"
    password: "password123"
  }) {
    id
    name
    email
  }
}
```

### 更新用户

```graphql
mutation {
  updateUser(user: {
    id: "123"
    name: "Bob"
    email: "bob@example.com"
  })
}
```

### 执行动作

```graphql
mutation {
  activateUser(userId: "123")
}
```

## 字段选择

### 查询指定字段

```graphql
query {
  findUsers(offset: 0, limit: 20) {
    items {
      id
      name
      # 只查询需要的字段
    }
    total
  }
}
```

### 关联字段

```graphql
query {
  findUsers(offset: 0, limit: 10) {
    items {
      id
      name
      # 查询关联字段
      roles {
        id
        name
      }
      department {
        id
        name
      }
    }
  }
}
```

## 错误处理

### 业务异常返回

当BizModel方法抛出`NopException`时，GraphQL会自动返回错误：

```graphql
mutation {
  changePassword(userId: "123", oldPassword: "wrong", newPassword: "new") {
    # 如果密码错误，这里返回null
  }
}

# 响应
{
  "data": {
    "changePassword": null
  },
  "errors": [
    {
      "message": "密码错误",
      "extensions": {
        "code": "my.err.user.password-incorrect",
        "status": 400,
        "params": {
          "userId": "123"
        }
      }
    }
  ]
}
```

## 权限控制

### 使用@BizLoader

```java
import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.core.ContextSource;

@BizModel
public class DocumentBizModel extends CrudBizModel<Document> {

    // 只有有权限的用户才能加载文档
    @BizLoader
    @GraphQLReturn(bizObjName = "NopAuthUser")
    public NopAuthUser owner(@ContextSource Document document) {
        return document.getOwner();
    }
}
```

## 最佳实践

### 1. 命名约定

```java
// 推荐的命名
@BizQuery
public User getUserById(String id) {}

@BizQuery
public PageBean<User> findUsers(QueryBean query) {}

@BizMutation
public User createUser(User user) {}

@BizMutation
public void updateUser(User user) {}

@BizAction
public void activateUser(String id) {}
```

### 2. 参数验证

```java
@BizMutation
public User createUser(User user) {
    // 验证必填字段
    if (StringHelper.isEmpty(user.getName())) {
        throw new NopException(MyErrors.ERR_NAME_REQUIRED);
    }

    // 验证格式
    if (!isValidEmail(user.getEmail())) {
        throw new NopException(MyErrors.ERR_INVALID_EMAIL);
    }

    return save(user);
}
```

### 3. 事务管理

```java
@BizMutation
public void complexOperation(String userId) {
    txn(() -> {
        // 多步骤操作在一个事务中
        User user = dao().requireEntityById(userId);
        dao().saveEntity(user);

        Order order = createOrder(user);
        orderDao.saveEntity(order);

        AuditLog log = createAuditLog(user);
        auditLogDao.saveEntity(log);
    });
}
```

### 4. 性能优化

```java
// 推荐：使用字段选择减少数据传输
@BizQuery
public List<User> findUsers(QueryBean query, FieldSelectionBean selection) {
    return findList(query, selection);
}

// 推荐：使用批量操作
@BizMutation
public void batchUpdateStatus(List<String> userIds, Integer newStatus) {
    txn(() -> {
        List<User> users = dao().batchGetEntitiesByIds(userIds);
        for (User user : users) {
            user.setStatus(newStatus);
        }
        dao().batchSaveEntities(users);
    });
}
```

## 常见问题

### Q1: 如何在GraphQL查询中处理分页？

**答案**: 使用QueryBean的`offset`和`limit`参数进行分页查询：

```java
@BizQuery
public PageBean<User> findUsers(@Name("query") QueryBean query) {
    return dao().findPageByQuery(query);
}
```

在GraphQL查询中：

```graphql
query {
  users(query: {offset: 0, limit: 10}) {
    items {
      userId
      userName
    }
    totalCount
  }
}
```

### Q2: 如何在GraphQL变更中处理事务？

**答案**: 使用`@Transactional`注解或`txn()`方法：

```java
@BizMutation
@Transactional
public User updateUser(@Name("userId") String userId, @Name("user") User user) {
    User existing = dao().getEntityById(userId);
    existing.setUserName(user.getUserName());
    return dao().updateEntity(existing);
}
```

或者使用`txn()`方法进行嵌套事务：

```java
@BizMutation
public void complexUpdate(@Name("data") Data data) {
    txn(() -> {
        // 事务逻辑
    });
}
```

### Q3: GraphQL查询返回null怎么办？

**答案**: 检查以下几点：

1. **实体不存在**: 确保数据库中存在对应的记录
2. **权限问题**: 检查是否有访问权限
3. **字段映射**: 确保字段名和GraphQL Schema一致
4. **返回类型**: 确保返回类型与声明类型一致

```java
@BizQuery
public User getUser(@Name("userId") String userId) {
    if (userId == null || userId.isEmpty()) {
        throw new NopException(ERR_ARG_INVALID)
            .param(ARG_USER_ID, userId);
    }

    User user = dao().getEntityById(userId);
    if (user == null) {
        throw new NopException(ERR_ENTITY_NOT_FOUND)
            .param(ARG_ENTITY_ID, userId);
    }
    return user;
}
```

### Q4: 如何在GraphQL中处理复杂查询？

**答案**: 使用QueryBean和FilterBeans构建复杂查询：

```java
@BizQuery
public List<User> searchUsers(
    @Name("name") String name,
    @Name("status") Integer status,
    @Name("minAge") Integer minAge
) {
    QueryBean query = QueryBean.forQuery(NopAuthUser.class);

    // 添加过滤条件
    FilterBean filter = FilterBean.and();
    if (name != null) {
        filter.add(FilterBean.contains(NopAuthUser.PROP_NAME_userName, name));
    }
    if (status != null) {
        filter.add(FilterBean.eq(NopAuthUser.PROP_NAME_status, status));
    }
    if (minAge != null) {
        filter.add(FilterBean.ge(NopAuthUser.PROP_NAME_age, minAge));
    }

    query.setFilter(filter);

    return dao().findAllByQuery(query);
}
```

### Q5: 如何在GraphQL中处理文件上传？

**答案**: 使用GraphQL multipart请求或REST API上传文件，然后在GraphQL查询中引用文件：

```java
@BizMutation
public String uploadFile(@Name("file") FilePart file) {
    // 处理文件上传
    String fileId = fileService.saveFile(file);
    return fileId;
}

@BizQuery
public FileInfo getFileInfo(@Name("fileId") String fileId) {
    return fileService.getFileInfo(fileId);
}
```

### Q6: 如何在GraphQL中处理实时数据？

**答案**: 使用GraphQL Subscription：

```java
@BizSubscription
public Publisher<UserEvent> onUserChanged(@Name("userId") String userId) {
    return eventPublisher.subscribe(userId);
}
```

在GraphQL客户端：

```graphql
subscription {
  userChanged(userId: "user-001") {
    eventType
    userId
    userName
  }
}
```

### Q7: 如何在GraphQL中处理错误？

**答案**: 使用NopException抛出业务异常，框架会自动转换为GraphQL错误：

```java
@BizMutation
public User createUser(@Name("user") User user) {
    // 验证用户名是否已存在
    if (isUserExists(user.getUserName())) {
        throw new NopException(ERR_USER_ALREADY_EXISTS)
            .param(ARG_USER_NAME, user.getUserName());
    }

    return dao().saveEntity(user);
}
```

错误响应会包含错误码和详细信息：

```json
{
  "errors": [
    {
      "message": "用户名已存在: testuser",
      "extensions": {
        "errorCode": "ERR_USER_ALREADY_EXISTS",
        "params": {
          "userName": "testuser"
        }
      }
    }
  ]
}
```

## 相关文档

- [服务层开发指南](../service/service-layer-development.md) - BizModel开发详解
- [IEntityDao使用指南](../dao/entitydao-usage.md) - 数据访问接口详解
- [QueryBean使用指南](../dao/querybean-guide.md) - 查询对象详解
- [FilterBeans使用指南](../dao/filterbeans-guide.md) - 过滤条件详解
- [事务管理指南](../core/transaction-guide.md) - 事务管理完整指南
- [异常处理指南](../core/exception-guide.md) - 异常处理完整指南
- [API模型设计](./api-model-design.md) - API模型设计指南

## 总结

Nop平台的GraphQL服务开发提供了：

1. **简单的注解驱动**：通过`@BizModel`, `@BizQuery`, `@BizMutation`等注解定义服务
2. **自动Schema生成**：根据Java类和方法自动生成GraphQL Schema
3. **内置CRUD支持**：继承`CrudBizModel`即可获得完整的CRUD功能
4. **灵活的查询支持**：支持QueryBean和FilterBeans构建复杂查询
5. **事务管理**：集成事务管理机制
6. **错误处理**：自动将`NopException`转换为GraphQL错误响应

在实际开发中：
- 使用清晰的命名约定
- 进行参数验证
- 合理使用事务
- 注意性能优化
