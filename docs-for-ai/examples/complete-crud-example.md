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
     * @param user 用户信息
     * @return 创建的用户
     */
    @BizMutation
    @Transactional
    public DemoUser createUser(@Name("user") DemoUser user) {
        // 1. 验证输入
        validateUser(user);

        // 2. 检查邮箱是否已存在
        if (emailExists(user.getEmail())) {
            throw new NopException(ERR_EMAIL_ALREADY_EXISTS)
                    .param("email", user.getEmail());
        }

        // 3. 设置默认值
        user.setUserId(IdGenerator.nextId());
        user.setStatus(1); // 正常状态
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());

        // 4. 保存用户
        DemoUser created = save(user);
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
    public DemoUser getUserById(@Name("userId") String userId) {
        return dao().requireEntityById(userId);
    }

    /**
     * 根据邮箱获取用户
     *
     * @param email 邮箱地址
     * @return 用户信息
     */
    @BizQuery
    public DemoUser getUserByEmail(@Name("email") String email) {
        DemoUser example = new DemoUser();
        example.setEmail(email);
        return dao().findFirstByExample(example);
    }

    /**
     * 分页查询用户
     *
     * @param query 查询条件
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    @BizQuery
    public PageBean<DemoUser> findUsers(@Name("query") QueryBean query,
                                     @Name("pageNo") Integer pageNo,
                                     @Name("pageSize") Integer pageSize) {
        return findPage(query, pageNo, pageSize);
    }

    // ==================== Update ====================

    /**
     * 更新用户
     *
     * @param user 用户信息
     * @return 更新后的用户
     */
    @BizMutation
    @Transactional
    public DemoUser updateUser(@Name("user") DemoUser user) {
        // 1. 验证输入
        validateUser(user);

        // 2. 检查用户是否存在
        DemoUser existingUser = dao().requireEntityById(user.getUserId());
        existingUser.setUserName(user.getUserName());
        existingUser.setPhone(user.getPhone());
        existingUser.setUpdateTime(new Date());

        // 3. 更新用户
        dao().updateEntity(existingUser);
        LOG.info("User updated: userId={}", user.getUserId());

        return existingUser;
    }

    // ==================== Delete ====================

    /**
     * 删除用户
     *
     * @param userId 用户ID
     */
    @BizMutation
    @Transactional
    public void deleteUser(@Name("userId") String userId) {
        // 1. 检查用户是否存在
        DemoUser user = dao().requireEntityById(userId);

        // 2. 删除用户
        dao().deleteEntity(user);
        LOG.info("User deleted: userId={}", userId);
    }

    // ==================== 辅助方法 ====================

    /**
     * 验证用户信息
     *
     * @param user 用户信息
     */
    private void validateUser(DemoUser user) {
        if (StringHelper.isEmpty(user.getUserName())) {
            throw new NopException(ERR_USER_NAME_REQUIRED);
        }
        if (StringHelper.isEmpty(user.getEmail())) {
            throw new NopException(ERR_EMAIL_REQUIRED);
        }
        if (!isValidEmail(user.getEmail())) {
            throw new NopException(ERR_EMAIL_INVALID)
                        .param("email", user.getEmail());
        }
    }

    /**
     * 检查邮箱是否已存在
     *
     * @param email 邮箱地址
     * @return 是否已存在
     */
    private boolean emailExists(String email) {
        DemoUser example = new DemoUser();
        example.setEmail(email);
        DemoUser existingUser = dao().findFirstByExample(example);
        return existingUser != null;
    }

    /**
     * 验证邮箱格式
     *
     * @param email 邮箱地址
     * @return 是否有效
     */
    private boolean isValidEmail(String email) {
        // 简化的邮箱验证
        return email != null && email.matches("^[A-Za-z0-9+._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
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
    public List<DemoUser> findUsersByStatus(@Name("status") Integer status) {
        DemoUser example = new DemoUser();
        example.setStatus(status);
        return dao().findAllByExample(example);
    }

    /**
     * 根据邮箱模糊查询
     *
     * @param emailKeyword 邮箱关键词
     * @return 用户列表
     */
    @BizQuery
    public List<DemoUser> searchUsersByEmail(@Name("emailKeyword") String emailKeyword) {
        QueryBean query = new QueryBean();
        query.setFilter(FilterBeans.contains("email", emailKeyword));
        return dao().findAllByQuery(query);
    }

    /**
     * 批量更新用户状态
     *
     * @param userIds 用户ID列表
     * @param status 状态
     */
    @BizMutation
    @Transactional
    public void batchUpdateStatus(@Name("userIds") List<String> userIds,
                                @Name("status") Integer status) {
        // 1. 批量获取用户
        List<DemoUser> users = dao().batchGetEntitiesByIds(userIds);

        // 2. 更新状态
        for (DemoUser user : users) {
            user.setStatus(status);
            user.setUpdateTime(new Date());
        }

        // 3. 批量保存
        dao().batchSaveEntities(users);
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
  createUser(user: {
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
  updateUser(user: {
    userId: "001"
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
  deleteUser(userId: "001")
}
```

### 6. 自定义查询

```graphql
query {
  findUsersByStatus(status: 1) {
    userId
    userName
    email
  }
}
```

## 最佳实践

1. **直接使用 CrudBizModel**：继承 CrudBizModel 并使用 dao() 方法，无需单独定义 DAO 接口
2. **使用内置 CRUD 方法**：对于标准 CRUD 操作，使用 CrudBizModel 的内置方法
3. **添加自定义查询**：在 BizModel 中添加自定义查询方法
4. **使用事务**：对于需要多步操作的方法，添加 @Transactional 注解
5. **异常处理**：抛出 NopException 并提供清晰的错误信息
6. **参数验证**：在方法开始处验证输入参数
7. **日志记录**：使用 SLF4J 记录关键操作

## 测试

```java
@SpringBootTest
public class DemoUserBizModelTest {

    @Inject
    private DemoUserBizModel userBizModel;

    @Test
    public void testCreateUser() {
        DemoUser user = new DemoUser();
        user.setUserName("test");
        user.setEmail("test@example.com");
        user.setPhone("13800138000");

        DemoUser created = userBizModel.createUser(user);

        assertNotNull(created);
        assertNotNull(created.getUserId());
        assertEquals("test", created.getUserName());
    }

    @Test
    public void testGetUserById() {
        DemoUser user = userBizModel.getUserById("001");

        assertNotNull(user);
        assertEquals("001", user.getUserId());
    }

    @Test
    public void testUpdateUser() {
        DemoUser user = userBizModel.getUserById("001");
        user.setUserName("updated");

        DemoUser updated = userBizModel.updateUser(user);

        assertNotNull(updated);
        assertEquals("updated", updated.getUserName());
    }

    @Test
    public void testDeleteUser() {
        userBizModel.deleteUser("001");

        // 验证删除后无法查询到
        DemoUser user = userBizModel.getUserById("001");
        assertNull(user);
    }
}
```

## 相关文档

- [IEntityDao使用指南](../getting-started/dao/entitydao-usage.md)
- [服务层开发指南](../getting-started/service/service-layer-development.md)
- [QueryBean使用指南](../getting-started/dao/querybean-guide.md)
- [事务管理指南](../getting-started/core/transaction-guide.md)
- [异常处理指南](../getting-started/core/exception-guide.md)
