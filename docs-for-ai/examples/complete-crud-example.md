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
    private Integer status;  // 1-正常，0-禁用

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

### 2. DAO接口

```java
public interface IDemoUserDao extends IOrmEntityDao<DemoUser> {
    // 自定义查询方法
    List<DemoUser> findByEmail(String email);
    List<DemoUser> findByStatus(Integer status);
}
```

### 3. DAO实现

```java
@Repository
public class DemoUserDao extends OrmEntityDao<DemoUser> implements IDemoUserDao {

    @Override
    public List<DemoUser> findByEmail(String email) {
        DemoUser example = new DemoUser();
        example.setEmail(email);
        return findAllByExample(example);
    }

    @Override
    public List<DemoUser> findByStatus(Integer status) {
        DemoUser example = new DemoUser();
        example.setStatus(status);
        return findAllByExample(example);
    }
}
```

## BizModel实现

### 1. 用户服务

```java
@BizModel("DemoUser")
public class DemoUserBizModel extends CrudBizModel<DemoUser> {

    private static final Logger LOG = LoggerFactory.getLogger(DemoUserBizModel.class);

    @Inject
    private IDemoUserDao userDao;

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
        DemoUser created = userDao.saveEntity(user);
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
    public DemoUser getUser(@Name("userId") String userId) {
        DemoUser user = userDao.getEntityById(userId);
        if (user == null) {
            throw new NopException(ERR_USER_NOT_FOUND)
                .param("userId", userId);
        }
        return user;
    }

    /**
     * 根据邮箱获取用户
     *
     * @param email 邮箱
     * @return 用户信息
     */
    @BizQuery
    public DemoUser getUserByEmail(@Name("email") String email) {
        List<DemoUser> users = userDao.findByEmail(email);
        if (users.isEmpty()) {
            throw new NopException(ERR_USER_NOT_FOUND)
                .param("email", email);
        }
        return users.get(0);
    }

    /**
     * 获取用户列表
     *
     * @param status 用户状态
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    @BizQuery
    public PageBean<DemoUser> findUsers(@Name("status") Integer status,
                                     @Name("pageNo") Integer pageNo,
                                     @Name("pageSize") Integer pageSize) {
        QueryBean query = new QueryBean();

        if (status != null) {
            query.setFilter(FilterBeans.eq("status", status));
        }

        query.setOrderField("createTime");
        query.setOrderDesc(true);

        return findPage(query, pageNo, pageSize);
    }

    // ==================== Update ====================

    /**
     * 更新用户信息
     *
     * @param user 用户信息
     * @return 更新后的用户
     */
    @BizMutation
    @Transactional
    public DemoUser updateUser(@Name("user") DemoUser user) {
        // 1. 验证输入
        if (StringHelper.isEmpty(user.getUserId())) {
            throw new NopException(ERR_USER_ID_REQUIRED);
        }

        // 2. 获取现有用户
        DemoUser existing = userDao.requireEntityById(user.getUserId());

        // 3. 更新字段
        if (StringHelper.isNotEmpty(user.getUserName())) {
            existing.setUserName(user.getUserName());
        }
        if (StringHelper.isNotEmpty(user.getEmail())) {
            existing.setEmail(user.getEmail());
        }
        if (StringHelper.isNotEmpty(user.getPhone())) {
            existing.setPhone(user.getPhone());
        }
        if (user.getStatus() != null) {
            existing.setStatus(user.getStatus());
        }
        existing.setUpdateTime(new Date());

        // 4. 保存更新
        DemoUser updated = userDao.saveEntity(existing);
        LOG.info("User updated: userId={}, email={}", updated.getUserId(), updated.getEmail());

        return updated;
    }

    /**
     * 批量更新用户状态
     *
     * @param userIds 用户ID列表
     * @param status 新状态
     * @return 更新的用户数量
     */
    @BizMutation
    @Transactional
    public int batchUpdateStatus(@Name("userIds") List<String> userIds,
                               @Name("status") Integer status) {
        if (userIds == null || userIds.isEmpty()) {
            throw new NopException(ERR_INVALID_INPUT)
                .param("field", "userIds");
        }

        // 1. 批量获取用户
        List<DemoUser> users = userDao.batchGetEntitiesByIds(userIds);

        // 2. 更新状态
        for (DemoUser user : users) {
            user.setStatus(status);
            user.setUpdateTime(new Date());
        }

        // 3. 批量保存
        userDao.batchSaveEntities(users);

        LOG.info("Users status updated: count={}, status={}", users.size(), status);
        return users.size();
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
        // 1. 获取用户
        DemoUser user = userDao.requireEntityById(userId);

        // 2. 删除用户
        userDao.deleteEntity(user);
        LOG.info("User deleted: userId={}, email={}", userId, user.getEmail());
    }

    /**
     * 批量删除用户
     *
     * @param userIds 用户ID列表
     * @return 删除的用户数量
     */
    @BizMutation
    @Transactional
    public int batchDeleteUsers(@Name("userIds") List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new NopException(ERR_INVALID_INPUT)
                .param("field", "userIds");
        }

        // 1. 批量获取用户
        List<DemoUser> users = userDao.batchGetEntitiesByIds(userIds);

        // 2. 批量删除
        userDao.batchDeleteEntities(users);

        LOG.info("Users deleted: count={}", users.size());
        return users.size();
    }

    // ==================== Helper Methods ====================

    /**
     * 验证用户信息
     */
    private void validateUser(DemoUser user) {
        if (user == null) {
            throw new NopException(ERR_INVALID_INPUT)
                .param("field", "user");
        }

        // 验证用户名
        if (StringHelper.isEmpty(user.getUserName())) {
            throw new NopException(ERR_NAME_REQUIRED)
                .param("field", "userName");
        }
        if (user.getUserName().length() > 50) {
            throw new NopException(ERR_NAME_TOO_LONG)
                .param("field", "userName")
                .param("length", user.getUserName().length())
                .param("maxLength", 50);
        }

        // 验证邮箱
        if (StringHelper.isEmpty(user.getEmail())) {
            throw new NopException(ERR_EMAIL_REQUIRED)
                .param("field", "email");
        }
        if (!isValidEmail(user.getEmail())) {
            throw new NopException(ERR_INVALID_EMAIL)
                .param("email", user.getEmail());
        }

        // 验证手机号
        if (StringHelper.isNotEmpty(user.getPhone()) && !isValidPhone(user.getPhone())) {
            throw new NopException(ERR_INVALID_PHONE)
                .param("phone", user.getPhone());
        }
    }

    /**
     * 检查邮箱是否存在
     */
    private boolean emailExists(String email) {
        List<DemoUser> users = userDao.findByEmail(email);
        return !users.isEmpty();
    }

    /**
     * 验证邮箱格式
     */
    private boolean isValidEmail(String email) {
        return StringHelper.matches(email,
            "^[A-Za-z0-9+_.+-]+@[A-Za-z0-9-]+\\.[A-Za-z]{2,}$"
        );
    }

    /**
     * 验证手机号格式
     */
    private boolean isValidPhone(String phone) {
        return StringHelper.matches(phone, "^1[3-9]\\d{9}$");
    }
}
```

## GraphQL API使用

### 1. 查询用户

```graphql
query {
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

### 2. 创建用户

```graphql
mutation {
  DemoUser {
    createUser(user: {
      userName: "zhangsan"
      email: "zhangsan@example.com"
      phone: "13800138000"
    }) {
      userId
      userName
      email
      createTime
    }
  }
}
```

### 3. 更新用户

```graphql
mutation {
  DemoUser {
    updateUser(user: {
      userId: "001"
      userName: "lisi"
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

### 4. 删除用户

```graphql
mutation {
  DemoUser {
    deleteUser(userId: "001")
  }
}
```

### 5. 查询用户列表

```graphql
query {
  DemoUser {
    findUsers(status: 1, pageNo: 1, pageSize: 20) {
      pageNo
      pageSize
      totalCount
      items {
        userId
        userName
        email
        phone
        status
        createTime
      }
    }
  }
}
```

## 完整业务场景

### 场景1：用户注册流程

```java
@BizModel("UserRegistration")
public class UserRegistrationBizModel extends CrudBizModel<DemoUser> {

    @Inject
    private IDemoUserDao userDao;

    @Inject
    private IEmailService emailService;

    /**
     * 完成用户注册流程
     *
     * @param userName 用户名
     * @param email 邮箱
     * @param password 密码
     * @return 注册的用户
     */
    @BizMutation
    @Transactional
    public DemoUser register(@Name("userName") String userName,
                          @Name("email") String email,
                          @Name("password") String password) {
        // 1. 创建用户
        DemoUser user = new DemoUser();
        user.setUserName(userName);
        user.setEmail(email);
        user.setPassword(encryptPassword(password));
        user.setStatus(0); // 等待激活

        DemoUser created = userDao.saveEntity(user);

        // 2. 发送激活邮件
        sendActivationEmail(created);

        // 3. 返回创建的用户
        return created;
    }

    /**
     * 激活用户
     */
    @BizMutation
    @Transactional
    public DemoUser activateUser(@Name("userId") String userId) {
        // 1. 获取用户
        DemoUser user = userDao.requireEntityById(userId);

        // 2. 激活用户
        user.setStatus(1);
        user.setUpdateTime(new Date());
        userDao.saveEntity(user);

        // 3. 发送欢迎邮件
        sendWelcomeEmail(user);

        return user;
    }

    private void sendActivationEmail(DemoUser user) {
        EmailMessage message = new EmailMessage();
        message.setTo(user.getEmail());
        message.setSubject("激活您的账户");
        message.setContent("请点击以下链接激活：http://example.com/activate?userId=" + user.getUserId());
        emailService.send(message);
    }

    private void sendWelcomeEmail(DemoUser user) {
        EmailMessage message = new EmailMessage();
        message.setTo(user.getEmail());
        message.setSubject("欢迎注册");
        message.setContent("欢迎" + user.getUserName() + "，您的账户已激活！");
        emailService.send(message);
    }

    private String encryptPassword(String password) {
        // 密码加密逻辑
        return password; // 实际项目中应该加密
    }
}
```

### 场景2：批量导入用户

```java
@BizModel("UserImport")
public class UserImportBizModel extends CrudBizModel<DemoUser> {

    @Inject
    private IDemoUserDao userDao;

    /**
     * 批量导入用户
     *
     * @param users 用户列表
     * @return 导入结果
     */
    @BizMutation
    @Transactional
    public ImportResult importUsers(@Name("users") List<DemoUser> users) {
        ImportResult result = new ImportResult();
        result.setTotal(users.size());

        List<DemoUser> successList = new ArrayList<>();
        List<ImportError> errorList = new ArrayList<>();

        for (DemoUser user : users) {
            try {
                // 验证用户
                validateUser(user);

                // 检查邮箱是否存在
                if (emailExists(user.getEmail())) {
                    errorList.add(new ImportError(user, ERR_EMAIL_ALREADY_EXISTS));
                    result.setFailed(result.getFailed() + 1);
                    continue;
                }

                // 设置默认值
                user.setUserId(IdGenerator.nextId());
                user.setStatus(1);
                user.setCreateTime(new Date());
                user.setUpdateTime(new Date());

                // 添加到成功列表
                successList.add(user);
                result.setSuccess(result.getSuccess() + 1);
            } catch (Exception e) {
                errorList.add(new ImportError(user, e.getMessage()));
                result.setFailed(result.getFailed() + 1);
            }
        }

        // 批量保存成功的用户
        if (!successList.isEmpty()) {
            userDao.batchSaveEntities(successList);
        }

        result.setErrors(errorList);
        return result;
    }
}
```

## 测试用例

### 1. 单元测试

```java
@ExtendWith(JunitExtension.class)
public class DemoUserBizModelTest {

    @InjectMocks
    private DemoUserBizModel userBizModel;

    @Mock
    private IDemoUserDao userDao;

    @Test
    public void testCreateUser() {
        // Given
        DemoUser user = new DemoUser();
        user.setUserName("testuser");
        user.setEmail("test@example.com");

        // When
        when(userDao.saveEntity(any(DemoUser.class))).thenReturn(user);
        DemoUser created = userBizModel.createUser(user);

        // Then
        assertNotNull(created);
        assertEquals("testuser", created.getUserName());
        verify(userDao).saveEntity(user);
    }

    @Test
    public void testGetUser() {
        // Given
        String userId = "test-001";
        DemoUser user = new DemoUser();
        user.setUserId(userId);
        user.setUserName("testuser");

        // When
        when(userDao.getEntityById(userId)).thenReturn(user);
        DemoUser found = userBizModel.getUser(userId);

        // Then
        assertNotNull(found);
        assertEquals(userId, found.getUserId());
        verify(userDao).getEntityById(userId);
    }

    @Test
    public void testUpdateUser() {
        // Given
        String userId = "test-001";
        DemoUser existing = new DemoUser();
        existing.setUserId(userId);
        existing.setUserName("oldname");

        DemoUser update = new DemoUser();
        update.setUserId(userId);
        update.setUserName("newname");

        when(userDao.requireEntityById(userId)).thenReturn(existing);
        when(userDao.saveEntity(any(DemoUser.class))).thenReturn(update);

        // When
        DemoUser updated = userBizModel.updateUser(update);

        // Then
        assertEquals("newname", updated.getUserName());
        verify(userDao).saveEntity(any(DemoUser.class));
    }

    @Test
    public void testDeleteUser() {
        // Given
        String userId = "test-001";
        DemoUser user = new DemoUser();
        user.setUserId(userId);

        when(userDao.requireEntityById(userId)).thenReturn(user);

        // When
        userBizModel.deleteUser(userId);

        // Then
        verify(userDao).deleteEntity(user);
    }
}
```

### 2. 集成测试

```java
@ExtendWith(JunitExtension.class)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class DemoUserIntegrationTest {

    @Inject
    private DemoUserBizModel userBizModel;

    @Inject
    private IDemoUserDao userDao;

    @Test
    public void testCreateUserFlow() {
        // Given
        DemoUser user = new DemoUser();
        user.setUserName("integration-test");
        user.setEmail("integration@example.com");

        // When
        DemoUser created = userBizModel.createUser(user);

        // Then
        assertNotNull(created);
        assertNotNull(created.getUserId());

        // 验证数据库
        DemoUser fromDb = userDao.getEntityById(created.getUserId());
        assertEquals(created.getUserName(), fromDb.getUserName());
    }

    @Test
    public void testUpdateUserFlow() {
        // Given
        DemoUser user = new DemoUser();
        user.setUserName("integration-test");
        user.setEmail("integration@example.com");

        // When: 创建用户
        DemoUser created = userBizModel.createUser(user);

        // When: 更新用户
        created.setUserName("updated-name");
        DemoUser updated = userBizModel.updateUser(created);

        // Then: 验证数据库
        DemoUser fromDb = userDao.getEntityById(updated.getUserId());
        assertEquals("updated-name", fromDb.getUserName());
    }
}
```

## 常见问题

### Q1: 如何处理并发更新？

A: 使用乐观锁：
```java
@Entity(table = "demo_user")
@VersionColumn(name = "version")
public class DemoUser implements IOrmEntity {
    private Integer version;
    // ...
}
```

### Q2: 如何实现软删除？

A: 添加删除标记：
```java
@Column(name = "deleted")
private Integer deleted; // 0-正常，1-已删除

@BizMutation
@Transactional
public void deleteUser(String userId) {
    DemoUser user = userDao.requireEntityById(userId);
    user.setDeleted(1); // 软删除
    user.setUpdateTime(new Date());
    userDao.saveEntity(user);
}
```

### Q3: 如何批量操作？

A: 使用批量方法：
```java
// 批量查询
List<DemoUser> users = userDao.batchGetEntitiesByIds(userIds);

// 批量保存
userDao.batchSaveEntities(users);

// 批量删除
userDao.batchDeleteEntities(users);
```

## 相关文档

- [服务层开发指南](../getting-started/service/service-layer-development.md)
- [IEntityDao使用指南](../getting-started/dao/ientitydao-usage.md)
- [事务管理指南](../getting-started/core/transaction-guide.md)
- [异常处理指南](../getting-started/core/exception-guide.md)
- [GraphQL服务开发指南](../getting-started/api/graphql-guide.md)

## 总结

本示例展示了如何使用Nop Platform实现完整的CRUD功能：

1. **实体定义**: 使用@Entity注解定义数据模型
2. **DAO层**: 实现自定义查询方法
3. **BizModel层**: 提供CRUD方法和业务逻辑
4. **事务管理**: 使用@Transactional注解管理事务
5. **异常处理**: 使用NopException统一异常处理
6. **GraphQL API**: 自动生成GraphQL查询和变更
7. **测试**: 编写单元测试和集成测试

遵循这些模式和最佳实践，可以构建稳定、可维护的CRUD功能。

---

**文档版本**: 1.0
**最后更新**: 2025-01-09
**作者**: AI Assistant (Sisyphus)
