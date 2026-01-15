# 服务层开发指南

## 概述

Nop平台服务层基于BizModel设计，提供了CrudBizModel基类用于快速实现CRUD操作，同时支持复杂业务逻辑的扩展。

## 核心组件

### 1. BizModel - 业务模型

**定义**：标记业务模型的注解，用于将Java类转换为GraphQL API
**位置**：`io.nop.biz.api.BizModel`
**作用**：
- 标记业务模型类
- 自动生成GraphQL类型和操作
- 支持业务逻辑封装

**使用示例**：
```java
@BizModel
public class UserBizModel {
    // 业务方法
}
```

### 2. CrudBizModel - CRUD业务模型基类

**定义**：提供通用CRUD操作的抽象基类
**位置**：`io.nop.biz.crud.CrudBizModel`
**核心功能**：
- 内置CRUD操作实现
- 事务管理支持
- 数据权限控制
- 业务扩展点

**内置方法**：
- `findCount()`：查询记录总数
- `findPage()`：分页查询
- `findFirst()`：查询第一条记录
- `findList()`：列表查询
- `save()`：保存实体
- `update()`：更新实体
- `delete()`：删除实体

**扩展点**：
- `defaultPrepareSave()`：保存前处理
- `defaultPrepareQuery()`：查询前处理
- `defaultPrepareUpdate()`：更新前处理
- `defaultPrepareDelete()`：删除前处理
- `checkDataAuth()`：数据权限检查
- `afterSave()`：保存后处理

## 开发流程

### 1. 继承CrudBizModel

**示例**：
```java
@BizModel
public class UserBizModel extends CrudBizModel<User> {
    public UserBizModel() {
        setEntityName(User.class.getName());
    }
    
    // 业务方法
}
```

### 2. 注入业务组件

**示例**：
```java
@Inject
protected IPasswordEncoder passwordEncoder;

@Inject
protected IUserIdGenerator userIdGenerator;

@Inject
protected IPasswordPolicy passwordPolicy;
```

### 3. 实现业务方法

**示例**：
```java
@BizQuery
public User getUserByOpenId(String openId) {
    // 使用Example查询
    User example = new User();
    example.setOpenId(openId);
    return dao().findFirstByExample(example);
}

@BizMutation
public void resetUserPassword(String userId, String newPassword) {
    txn(() -> {
        User user = dao().requireEntityById(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        dao().saveEntity(user);
    });
}
```

### 4. 重写扩展点

**示例**：
```java
@Override
protected void defaultPrepareSave(User user) {
    passwordPolicy.checkPassword(user.getPassword());
    user.setPassword(passwordEncoder.encode(user.getPassword()));
    
    if (StringHelper.isEmpty(user.getId())) {
        user.setId(userIdGenerator.generateUserId());
        user.setCreateTime(LocalDateTime.now());
        user.setStatus(1);
    }
}
```

## 示例：NopAuthUserBizModel

### 1. 核心实现

**定义**：
```java
@BizModel
public class NopAuthUserBizModel extends CrudBizModel<NopAuthUser> {
    @Inject
    protected IPasswordEncoder passwordEncoder;
    
    @Inject
    protected IUserIdGenerator userIdGenerator;
    
    @Inject
    protected IPasswordPolicy passwordPolicy;
    
    public NopAuthUserBizModel() {
        setEntityName(NopAuthUser.class.getName());
    }
    
    // 业务方法...
}
```

**业务方法**：
```java
@BizQuery
public NopAuthUser getUserByOpenId(String openId) {
    NopAuthUser example = new NopAuthUser();
    example.setOpenId(openId);
    return dao().findFirstByExample(example);
}

@BizMutation
public void resetUserPassword(String userId, String newPassword) {
    NopAuthUser user = dao().getEntityById(userId);
    if (user == null) {
        throw new NopException(Errors.ERR_BIZ_OBJECT_NOT_FOUND)
                .param(ARG_OBJ_NAME, "NopAuthUser")
                .param(ARG_OBJ_ID, userId);
    }
    passwordPolicy.checkPassword(newPassword);
    user.setPassword(passwordEncoder.encode(newPassword));
    dao().saveEntity(user);
}

@BizMutation
public void changeSelfPassword(String oldPassword, String newPassword) {
    // 实现修改当前用户密码的逻辑
}

@BizMutation
@Transactional
public void resetUserPassword(String userId, String newPassword) {
    NopAuthUser user = dao().requireEntityById(userId);
    passwordPolicy.checkPassword(newPassword);
    user.setPassword(passwordEncoder.encode(newPassword));
    dao().saveEntity(user);
}

@BizMutation
public void changeSelfPassword(String oldPassword, String newPassword) {
    // 实现修改当前用户密码的逻辑
}
```

**扩展点实现**：
```java
@Override
protected void defaultPrepareSave(EntityData<NopAuthUser> entityData, IServiceContext context) {
    NopAuthUser user = entityData.getEntity();
    if (user.isNew()) {
        if (StringHelper.isEmpty(user.getId())) {
            user.setId(userIdGenerator.generateUserId());
        }
        if (StringHelper.isEmpty(user.getOpenId())) {
            user.setOpenId(userIdGenerator.generateOpenId());
        }
        user.setStatus(1);
    }

    if (user.isChanged(NopAuthUser.PASSWORD)) {
        passwordPolicy.checkPassword(user.getPassword());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
    }

    user.setUpdateTime(LocalDateTime.now());
}
```

## 业务方法注解

### 1. 查询方法

**@BizQuery**：标记查询方法

**示例**：
```java
@BizQuery
public List<User> findActiveUsers() {
    // 使用QueryBean查询
    QueryBean query = new QueryBean();
    query.setFilter(FilterBeans.eq("status", 1));
    return dao().findAllByQuery(query);
}
```

### 2. 变更方法

**@BizMutation**：标记变更方法（新增、更新、删除）

**示例**：
```java
@BizMutation
public User createUser(User user) {
    return (User) save(Collections.singletonMap("entity", user));
}
```

### 3. 动作方法

**@BizAction**：标记动作方法（非CRUD操作）

**示例**：
```java
@BizAction
public void approveUser(String userId) {
    // 审批用户逻辑
}
```

## 最佳实践

1. **优先使用内置方法**：对于简单的CRUD操作，优先使用内置方法
2. **合理使用事务**：在需要保证数据一致性的场景下，使用事务管理
3. **业务逻辑封装**：将复杂业务逻辑封装在BizModel中
4. **数据权限控制**：实现`checkDataAuth()`方法，进行数据权限控制
5. **异常处理**：使用NopException抛出业务异常，提供清晰的错误信息
6. **事务边界**：事务边界尽可能小，避免在事务中执行耗时操作

## 注意事项

1. **避免在BizModel中直接使用HttpSession等Web层对象**
2. **不要在BizModel中直接访问数据库，使用dao()方法获取DAO对象**
3. **不要在BizModel中手动管理事务，使用@Transactional注解**
4. **不要在BizModel中手动处理异常，抛出NopException即可**

## 常见问题

### Q1: 如何在BizModel中调用其他BizModel的方法？

**答案**: 使用依赖注入注入其他BizModel：

```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {

    @Inject
    protected OrderBizModel orderBizModel;

    @BizQuery
    public List<Order> getUserOrders(@Name("userId") String userId) {
        return orderBizModel.findOrdersByUser(userId);
    }
}
```

### Q2: 如何在BizModel中进行批量操作？

**答案**: 使用DAO的批量方法：

```java
@BizMutation
public void batchUpdateStatus(@Name("userIds") List<String> userIds,
                             @Name("status") Integer status) {
    txn(() -> {
        List<User> users = dao().batchGetEntitiesByIds(userIds);
        for (User user : users) {
            user.setStatus(status);
        }
        dao().batchSaveEntities(users);
    });
}
```

### Q3: 如何在BizModel中处理复杂的业务逻辑？

**答案**: 将复杂逻辑拆分为多个私有方法，保持主方法的清晰：

```java
@BizMutation
@Transactional
public Order createOrder(@Name("order") Order order,
                        @Name("items") List<OrderItem> items) {
    // 验证订单
    validateOrder(order, items);

    // 计算价格
    calculatePrice(order, items);

    // 保存订单
    return saveOrder(order, items);
}

private void validateOrder(Order order, List<OrderItem> items) {
    // 验证逻辑
}

private void calculatePrice(Order order, List<OrderItem> items) {
    // 计算逻辑
}

private Order saveOrder(Order order, List<OrderItem> items) {
    // 保存逻辑
}
```

### Q4: 如何在BizModel中实现条件逻辑？

**答案**: 使用if-else或switch语句处理条件逻辑：

```java
@BizQuery
public List<Order> findOrders(@Name("status") String status,
                              @Name("userId") String userId) {
    QueryBean query = QueryBean.forQuery(Order.class);

    if (status != null && !status.isEmpty()) {
        query.filter(Order.PROP_NAME_status, FilterBean.eq(status));
    }

    if (userId != null && !userId.isEmpty()) {
        query.filter(Order.PROP_NAME_userId, FilterBean.eq(userId));
    }

    return dao().findAllByQuery(query);
}
```

### Q5: 如何在BizModel中处理分页查询？

**答案**: 使用QueryBean的offset和limit参数：

```java
@BizQuery
public PageBean<Order> findPage(@Name("query") QueryBean query,
                                @Name("pageNo") Integer pageNo,
                                @Name("pageSize") Integer pageSize) {
    if (pageNo == null || pageNo <= 0) {
        pageNo = 1;
    }
    if (pageSize == null || pageSize <= 0) {
        pageSize = 20;
    }

    query.setOffset((pageNo - 1) * pageSize);
    query.setLimit(pageSize);

    return dao().findPageByQuery(query);
}
```

### Q6: 如何在BizModel中处理权限检查？

**答案**: 在方法开始处进行权限检查：

```java
@BizMutation
public void updateUserStatus(@Name("userId") String userId,
                            @Name("status") Integer status) {
    // 检查权限
    if (!hasPermission("user:update")) {
        throw new NopException(ERR_FORBIDDEN);
    }

    // 验证用户
    User user = dao().getEntityById(userId);
    if (user == null) {
        throw new NopException(ERR_ENTITY_NOT_FOUND);
    }

    // 更新状态
    user.setStatus(status);
    dao().updateEntity(user);
}
```

## 总结

BizModel是Nop平台服务层的核心组件，它封装了业务逻辑，为GraphQL和REST API提供服务。

**关键要点**：

1. **继承CrudBizModel**: 自动获得完整的CRUD功能
2. **使用注解定义服务**: @BizModel、@BizQuery、@BizMutation
3. **依赖注入**: 使用@Inject注入其他服务
4. **事务管理**: 使用@Transactional注解管理事务
5. **异常处理**: 抛出NopException，框架自动处理

遵循这些最佳实践，可以构建清晰、可维护的服务层代码。

## 相关文档

- [IEntityDao使用指南](../dao/entitydao-usage.md)
- [QueryBean使用指南](../dao/querybean-guide.md)
- [事务管理指南](../core/transaction-guide.md)
- [异常处理指南](../core/exception-guide.md)
- [GraphQL服务开发指南](../api/graphql-guide.md)

## 示例：完整的BizModel

```java
@BizModel
public class OrderBizModel extends CrudBizModel<Order> {
    @Inject
    IDaoProvider daoProvider;

    @Inject
    IPricingService pricingService;
    
    public OrderBizModel() {
        setEntityName(Order.class.getName());
    }
    
    @BizQuery
    public Order findOrderById(String orderId) {
        return dao().requireEntityById(orderId);
    }
    
    @BizQuery
    public PageBean<Order> findOrders(OrderQuery query) {
        return dao().findPage(query, query.getPageNo(), query.getPageSize());
    }
    
    @BizMutation
    @Transactional
    public Order createOrder(Order order, List<OrderItem> items) {
        // 价格计算
        BigDecimal totalAmount = pricingService.calculateTotalAmount(items);
        order.setTotalAmount(totalAmount);

        // 保存订单
        Order savedOrder = save(order);

        // 保存订单项 - 通过 IDaoProvider 获取 DAO
        IEntityDao<OrderItem> orderItemDao = daoProvider.daoFor(OrderItem.class);
        for (OrderItem item : items) {
            item.setOrderId(savedOrder.getId());
            orderItemDao.saveEntity(item);
        }

        return savedOrder;
    }

    @Override
    protected void defaultPrepareSave(EntityData<Order> entityData, IServiceContext context) {
        Order order = entityData.getEntity();
        if (order.isNew()) {
            order.setOrderNo(generateOrderNo());
            order.setCreateTime(LocalDateTime.now());
            order.setStatus(OrderStatus.PENDING.name());
        }
        order.setUpdateTime(LocalDateTime.now());
    }

        return savedOrder;
    }

    @Override
    protected void defaultPrepareSave(EntityData<Order> entityData, IServiceContext context) {
        Order order = entityData.getEntity();
        if (order.isNew()) {
            order.setOrderNo(generateOrderNo());
            order.setCreateTime(LocalDateTime.now());
            order.setStatus(OrderStatus.PENDING.name());
        }
        order.setUpdateTime(LocalDateTime.now());
    }
    
    private String generateOrderNo() {
        // 生成订单号逻辑
        return StringHelper.generateUUID().substring(0, 16);
    }
}
```