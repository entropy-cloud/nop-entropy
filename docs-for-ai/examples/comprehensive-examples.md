# 综合示例指南

## 概述

本文档提供Nop Platform的综合开发示例，涵盖CRUD操作、复杂查询、GraphQL API、事务处理等常见场景。通过实际代码示例展示Nop平台的核心功能和最佳实践。

## 1. 完整CRUD示例

### 实体定义

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

    // Getter and Setter方法
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

### BizModel实现

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
     */
    @BizQuery
    public DemoUser getUserById(@Name("userId") String userId) {
        return dao().requireEntityById(userId);
    }

    /**
     * 分页查询用户
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

    private void validateUser(DemoUser user) {
        if (StringHelper.isEmpty(user.getUserName())) {
            throw new NopException(ERR_USER_NAME_REQUIRED);
        }
        if (StringHelper.isEmpty(user.getEmail())) {
            throw new NopException(ERR_EMAIL_REQUIRED);
        }
    }

    private boolean emailExists(String email) {
        DemoUser example = new DemoUser();
        example.setEmail(email);
        return dao().findFirstByExample(example) != null;
    }
}
```

## 2. GraphQL API示例

### 基本查询

```graphql
# 单个实体查询
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

# 列表查询
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

# 条件查询
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

### 变更操作

```graphql
# 创建实体
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

# 更新实体
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

# 删除实体
mutation DeleteUser {
  DemoUser {
    deleteUser(userId: "001")
  }
}
```

## 3. 复杂查询示例

### 多条件查询

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

### OR条件组合

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

### 关联查询

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

## 4. 事务处理示例

### 简单事务

```java
@BizMutation
@Transactional
public Order createOrder(@Name("order") Order order, 
                        @Name("items") List<OrderItem> items) {
    
    // 1. 验证订单
    validateOrder(order);
    
    // 2. 保存订单
    order.setOrderId(IdGenerator.nextId());
    order.setCreateTime(new Date());
    order.setStatus(OrderStatus.PENDING);
    Order savedOrder = orderDao.saveEntity(order);
    
    // 3. 保存订单项
    for (OrderItem item : items) {
        item.setOrderId(savedOrder.getOrderId());
        item.setItemId(IdGenerator.nextId());
        orderItemDao.saveEntity(item);
    }
    
    // 4. 更新库存
    updateInventory(items);
    
    return savedOrder;
}
```

### 嵌套事务

```java
@BizMutation
@Transactional
public Order processOrder(@Name("orderId") String orderId) {
    Order order = orderDao.requireEntityById(orderId);
    
    try {
        // 1. 验证订单状态
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new NopException(ERR_ORDER_STATUS_INVALID);
        }
        
        // 2. 处理支付
        processPayment(order);
        
        // 3. 更新库存
        updateInventoryForOrder(order);
        
        // 4. 更新订单状态
        order.setStatus(OrderStatus.COMPLETED);
        order.setUpdateTime(new Date());
        orderDao.updateEntity(order);
        
        return order;
        
    } catch (Exception e) {
        // 事务回滚
        order.setStatus(OrderStatus.FAILED);
        order.setUpdateTime(new Date());
        orderDao.updateEntity(order);
        throw e;
    }
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
private void processPayment(Order order) {
    Payment payment = new Payment();
    payment.setPaymentId(IdGenerator.nextId());
    payment.setOrderId(order.getOrderId());
    payment.setAmount(order.getTotalAmount());
    payment.setStatus(PaymentStatus.PROCESSING);
    
    paymentDao.saveEntity(payment);
    
    // 调用支付网关
    PaymentResult result = paymentGateway.process(payment);
    
    if (result.isSuccess()) {
        payment.setStatus(PaymentStatus.SUCCESS);
    } else {
        payment.setStatus(PaymentStatus.FAILED);
        throw new NopException(ERR_PAYMENT_FAILED)
                .param("error", result.getErrorMessage());
    }
    
    paymentDao.updateEntity(payment);
}
```

## 5. 错误处理示例

### 自定义异常

```java
@BizMutation
@Transactional
public DemoUser createUser(@Name("user") DemoUser user) {
    // 验证输入
    if (StringHelper.isEmpty(user.getUserName())) {
        throw new NopException(ERR_USER_NAME_REQUIRED)
                .param("field", "userName")
                .param("value", user.getUserName());
    }
    
    if (StringHelper.isEmpty(user.getEmail())) {
        throw new NopException(ERR_EMAIL_REQUIRED)
                .param("field", "email")
                .param("value", user.getEmail());
    }
    
    // 检查邮箱是否已存在
    if (emailExists(user.getEmail())) {
        throw new NopException(ERR_EMAIL_ALREADY_EXISTS)
                .param("email", user.getEmail())
                .param("suggestion", "请使用其他邮箱地址");
    }
    
    // 业务逻辑处理
    user.setUserId(IdGenerator.nextId());
    user.setStatus(1);
    user.setCreateTime(new Date());
    
    return dao().saveEntity(user);
}
```

### 异常码定义

```java
public interface ErrorCodes {
    String ERR_USER_NAME_REQUIRED = "nop.err.demo.user-name-required";
    String ERR_EMAIL_REQUIRED = "nop.err.demo.email-required";
    String ERR_EMAIL_ALREADY_EXISTS = "nop.err.demo.email-already-exists";
    String ERR_ORDER_STATUS_INVALID = "nop.err.demo.order-status-invalid";
    String ERR_PAYMENT_FAILED = "nop.err.demo.payment-failed";
}
```

## 6. 性能优化示例

### 批量操作

```java
@BizMutation
@Transactional
public BatchResult batchCreateUsers(@Name("users") List<DemoUser> users) {
    BatchResult result = new BatchResult();
    
    for (DemoUser user : users) {
        try {
            validateUser(user);
            
            if (emailExists(user.getEmail())) {
                result.addFailed(user.getEmail(), "邮箱已存在");
                continue;
            }
            
            user.setUserId(IdGenerator.nextId());
            user.setStatus(1);
            user.setCreateTime(new Date());
            
            dao().saveEntity(user);
            result.addSuccess(user.getEmail());
            
        } catch (Exception e) {
            result.addFailed(user.getEmail(), e.getMessage());
        }
    }
    
    return result;
}
```

### 缓存使用

```java
@BizQuery
@Cacheable(cacheName = "demo_user_cache")
public DemoUser getUserById(@Name("userId") String userId) {
    return dao().requireEntityById(userId);
}

@BizMutation
@CacheEvict(cacheName = "demo_user_cache", key = "#user.userId")
public DemoUser updateUser(@Name("user") DemoUser user) {
    DemoUser existing = dao().requireEntityById(user.getUserId());
    existing.setUserName(user.getUserName());
    existing.setEmail(user.getEmail());
    existing.setUpdateTime(new Date());
    
    return dao().updateEntity(existing);
}
```

## 7. 前端集成示例

### GraphQL查询调用

```javascript
// 查询用户列表
const queryUsers = async (keyword, status, pageNo, pageSize) => {
  const query = `
    query SearchUsers($keyword: String, $status: Int, $pageNo: Int, $pageSize: Int) {
      DemoUser {
        findUsers(keyword: $keyword, status: $status, pageNo: $pageNo, pageSize: $pageSize) {
          pageNo
          pageSize
          totalCount
          items {
            userId
            userName
            email
            status
            createTime
          }
        }
      }
    }
  `;
  
  const variables = { keyword, status, pageNo, pageSize };
  
  const response = await fetch('/graphql', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query, variables }),
  });
  
  const result = await response.json();
  return result.data.DemoUser.findUsers;
};

// 创建用户
const createUser = async (user) => {
  const mutation = `
    mutation CreateUser($user: DemoUserInput) {
      DemoUser {
        createUser(user: $user) {
          userId
          userName
          email
          phone
          status
          createTime
        }
      }
    }
  `;
  
  const variables = { user };
  
  const response = await fetch('/graphql', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query: mutation, variables }),
  });
  
  const result = await response.json();
  return result.data.DemoUser.createUser;
};
```

## 总结

本综合示例指南涵盖了Nop Platform的核心开发场景，包括：

1. **完整CRUD实现**：实体定义、BizModel实现、增删改查操作
2. **GraphQL API**：查询、变更、参数处理、错误处理
3. **复杂查询**：多条件查询、关联查询、分页查询
4. **事务处理**：简单事务、嵌套事务、异常处理
5. **性能优化**：批量操作、缓存使用、查询优化
6. **前端集成**：GraphQL调用、错误处理、数据绑定

通过遵循这些示例和最佳实践，可以快速构建高质量、高性能的Nop Platform应用。