# Complete CRUD Example

## 概述

本文档提供一个完整的CRUD（Create、Read、Update、Delete）功能实现示例，展示如何使用Nop Platform构建标准的增删改查功能。

## 实体定义

### 1. 用户实体

```java
@Entity(table = "demo_user")
@Cacheable(cacheName = "demo_user_cache")
public class DemoUser implements IOrmEntity {
    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "user_name", mandatory = true)
    private String userName;

    @Column(name = "email", mandatory = true, unique = true)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "status", mandatory = true)
    private Integer status; // 1-正常，0-禁用

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "update_time")
    private Date updateTime;

    // Getter and Setter
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
```

## BizModel实现

```java
@BizModel("DemoUser")
public class DemoUserBizModel extends CrudBizModel<DemoUser> {

    private static final Logger LOG = LoggerFactory.getLogger(DemoUserBizModel.class);

    public DemoUserBizModel() {
        setEntityName(DemoUser.class.getName());
    }

    // ==================== Create ====================

    /**
     * 创建用户
     *
     * @param data 用户数据（Map形式，通过XMeta校验）
     * @return 创建的用户
     */
    @BizMutation
    public DemoUser createUser(@Name("data") Map<String, Object> data,
                               IServiceContext context) {
        // 1. 检查邮箱是否已存在
        String email = (String) data.get("email");
        if (emailExists(email)) {
            throw new NopException(ERR_EMAIL_ALREADY_EXISTS)
                    .param("email", email);
        }

        // 2. 保存用户（框架自动设置审计字段：createTime, createBy等）
        DemoUser created = save(data, context);
        LOG.info("User created: userId={}, email={}", created.getUserId(), created.getEmail());

        return created;
    }

    // ==================== Read ====================

    /**
     * 根据用户ID获取用户
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    @BizQuery
    public DemoUser getUserById(@Name("userId") String userId,
                                 IServiceContext context) {
        // 使用 requireEntity 确保数据权限检查
        return requireEntity(userId, "read", context);
    }

    /**
     * 根据邮箱获取用户
     *
     * @param email 邮箱地址
     * @return 用户信息
     */
    @BizQuery
    public DemoUser getUserByEmail(@Name("email") String email,
                                    IServiceContext context) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("email", email));
        return doFindFirst(query, null, context);
    }

    /**
     * 分页查询用户
     *
     * @param query 查询条件
     * @param selection 字段选择
     * @param context 服务上下文
     * @return 分页结果
     */
    @BizQuery
    public PageBean<DemoUser> findUsers(@Name("query") QueryBean query,
                                         FieldSelectionBean selection,
                                         IServiceContext context) {
        return findPage(query, selection, context);
    }

    // ==================== Update ====================

    /**
     * 更新用户
     *
     * @param data 用户数据
     * @return 更新后的用户
     */
    @BizMutation
    public DemoUser updateUser(@Name("data") Map<String, Object> data,
                               IServiceContext context) {
        String userId = (String) data.get("userId");
        if (userId == null) {
            throw new NopException(ERR_USER_ID_REQUIRED);
        }

        // 使用 requireEntity 验证存在性和权限
        DemoUser existingUser = requireEntity(userId, "update", context);

        // 更新用户（框架自动设置 updateTime, updateBy）
        return update(data, context);
    }

    // ==================== Delete ====================

    /**
     * 删除用户
     *
     * @param userId 用户ID
     */
    @BizMutation
    public void deleteUser(@Name("userId") String userId,
                           IServiceContext context) {
        // 使用 requireEntity 验证存在性和权限，然后删除
        delete(userId, context);
        LOG.info("User deleted: userId={}", userId);
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查邮箱是否已存在
     */
    private boolean emailExists(String email) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("email", email));
        return findCount(query, null) > 0;
    }
}
```

## 自定义查询扩展

如果需要自定义查询方法，可以在 CrudBizModel 中添加：

```java
@BizModel("DemoUser")
public class DemoUserBizModel extends CrudBizModel<DemoUser> {

    public DemoUserBizModel() {
        setEntityName(DemoUser.class.getName());
    }

    /**
     * 根据状态查询用户
     *
     * @param status 用户状态
     * @return 用户列表
     */
    @BizQuery
    public List<DemoUser> findUsersByStatus(@Name("status") Integer status,
                                            FieldSelectionBean selection,
                                            IServiceContext context) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("status", status));
        return doFindList(query, selection, null, context);
    }

    /**
     * 根据邮箱模糊查询
     *
     * @param emailKeyword 邮箱关键词
     * @return 用户列表
     */
    @BizQuery
    public List<DemoUser> searchUsersByEmail(@Name("emailKeyword") String emailKeyword,
                                              FieldSelectionBean selection,
                                              IServiceContext context) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.contains("email", emailKeyword));
        return doFindList(query, selection, null, context);
    }

    /**
     * 批量更新用户状态
     *
     * @param userIds 用户ID列表
     * @param status 状态
     */
    @BizMutation
    public void batchUpdateStatus(@Name("userIds") List<String> userIds,
                                  @Name("status") Integer status,
                                  IServiceContext context) {
        // 使用 batchUpdate 方法，自动处理数据权限
        Map<String, Object> data = new HashMap<>();
        data.put("status", status);
        batchUpdate(new HashSet<>(userIds), data, false, context);
    }
}
```

## GraphQL API使用

### 1. 查询用户

```graphql
query {
  getUserById(userId: "001") {
    userId
    userName
    email
    phone
    status
    createTime
    updateTime
  }
}
```

### 2. 分页查询用户

```graphql
query {
  findUsers(pageNo: 1, pageSize: 20, query: {
    filter: {
      op: "AND",
      children: [
        { name: "status", op: "eq", value: "1" }
      ]
    }
    orderBy: [
      { name: "createTime", desc: true }
    ]
  }) {
    items {
      userId
      userName
      email
    }
    total
  }
}
```

### 3. 创建用户

```graphql
mutation {
  DemoUser__save(data: {
    userName: "zhangsan"
    email: "zhangsan@example.com"
    phone: "13800138000"
  }) {
    userId
    userName
    email
  }
}
```

### 4. 更新用户

```graphql
mutation {
  DemoUser__update(data: {
    id: "001"
    userName: "zhangsan-updated"
    phone: "13800138001"
  }) {
    userId
    userName
    phone
    updateTime
  }
}
```

### 5. 删除用户

```graphql
mutation {
  DemoUser__delete(id: "001")
}
```

### 6. 自定义查询

```graphql
query {
  DemoUser__findUsersByStatus(status: 1) {
    userId
    userName
    email
  }
}
```

## 最佳实践

1. **使用 CrudBizModel 内置方法**：`requireEntity()`, `doFindList()`, `save()`, `update()`, `delete()` 等，确保数据权限检查
2. **避免直接调用 dao()**：直接调用 dao() 会绕过数据权限检查、验证、回调等机制
3. **参数使用 Map**：CRUD 操作使用 `Map<String, Object>` 作为参数，通过 XMeta 进行校验
4. **无需 @Transactional**：`@BizMutation` 已自动开启事务，无需额外添加
5. **异常处理**：抛出 NopException 并提供清晰的错误信息和参数
6. **审计字段自动设置**：`createTime`, `updateTime`, `createBy`, `updateBy` 由框架自动设置
7. **日志记录**：使用 SLF4J 记录关键操作

## 测试

使用 `nop-autotest` 进行自动化测试：

```java
@NopTestConfig
public class DemoUserBizModelTest extends JunitAutoTestCase {

    @Inject
    protected IGraphQLExecutor graphQLExecutor;

    @EnableSnapshot
    @Test
    public void testCreateUser() {
        ContextProvider.getOrCreateContext().setUserId("1");

        Map<String, Object> data = new HashMap<>();
        data.put("userName", "test");
        data.put("email", "test@example.com");
        data.put("phone", "13800138000");

        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(data);

        IGraphQLExecutionContext context = graphQLExecutor.newRpcContext(
            GraphQLOperationType.mutation, "DemoUser__save", request);
        Object result = FutureHelper.syncGet(graphQLExecutor.executeRpcAsync(context));
        
        output("response.json5", result);
    }

    @EnableSnapshot
    @Test
    public void testGetUserById() {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setFieldSelection(FieldSelectionBean.fromPropNames("userId", "userName", "email"));

        IGraphQLExecutionContext context = graphQLExecutor.newRpcContext(
            GraphQLOperationType.query, "DemoUser__get", request);
        context.setStringArg("id", "1");
        
        Object result = FutureHelper.syncGet(graphQLExecutor.executeRpcAsync(context));
        output("response.json5", result);
    }

    @EnableSnapshot
    @Test
    public void testUpdateUser() {
        ContextProvider.getOrCreateContext().setUserId("1");

        Map<String, Object> data = new HashMap<>();
        data.put("id", "1");
        data.put("userName", "updated");

        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(data);

        IGraphQLExecutionContext context = graphQLExecutor.newRpcContext(
            GraphQLOperationType.mutation, "DemoUser__update", request);
        Object result = FutureHelper.syncGet(graphQLExecutor.executeRpcAsync(context));
        
        output("response.json5", result);
    }

    @EnableSnapshot
    @Test
    public void testDeleteUser() {
        ContextProvider.getOrCreateContext().setUserId("1");

        ApiRequest<Object> request = new ApiRequest<>();

        IGraphQLExecutionContext context = graphQLExecutor.newRpcContext(
            GraphQLOperationType.mutation, "DemoUser__delete", request);
        context.setStringArg("id", "1");
        
        Object result = FutureHelper.syncGet(graphQLExecutor.executeRpcAsync(context));
        output("response.json5", result);
    }
}
```

## 相关文档

- [BizModel 编写指南](../03-development-guide/bizmodel-guide.md)
- [CRUD 开发指南](../03-development-guide/crud-development.md)
- [服务层开发指南](../03-development-guide/service-layer.md)
- [DDD 在 Nop 中的实践](../03-development-guide/ddd-in-nop.md)
- [QueryBean 使用指南](../03-development-guide/querybean-guide.md)
- [DTO 编码规范](../04-core-components/enum-dto-standards.md)
