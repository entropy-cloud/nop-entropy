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
private IPasswordEncoder passwordEncoder;

@Inject
private IUserIdGenerator userIdGenerator;

@Inject
private IPasswordPolicy passwordPolicy;
```

### 3. 实现业务方法

**示例**：
```java
@BizQuery
public User getUserByOpenId(String openId) {
    return dao().findFirst(User.OPEN_ID.eq(openId));
}

@BizMutation
public void resetUserPassword(String userId, String newPassword) {
    txn(() -> {
        User user = dao().findById(userId);
        if (user == null) {
            throw new NopException(Errors.ERR_BIZ_OBJECT_NOT_FOUND)
                    .param(ARG_OBJ_NAME, "User")
                    .param(ARG_OBJ_ID, userId);
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        dao().save(user);
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
    private IPasswordEncoder passwordEncoder;
    
    @Inject
    private IUserIdGenerator userIdGenerator;
    
    @Inject
    private IPasswordPolicy passwordPolicy;
    
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
    return dao().findFirst(NopAuthUser.OPEN_ID.eq(openId));
}

@BizMutation
@Transactional
public void resetUserPassword(String userId, String newPassword) {
    NopAuthUser user = dao().findById(userId);
    if (user == null) {
        throw new NopException(Errors.ERR_BIZ_OBJECT_NOT_FOUND)
                .param(ARG_OBJ_NAME, "NopAuthUser")
                .param(ARG_OBJ_ID, userId);
    }
    passwordPolicy.checkPassword(newPassword);
    user.setPassword(passwordEncoder.encode(newPassword));
    dao().save(user);
}

@BizMutation
@Transactional
public void changeSelfPassword(String oldPassword, String newPassword) {
    // 实现修改当前用户密码的逻辑
}
```

**扩展点实现**：
```java
@Override
protected void defaultPrepareSave(NopAuthUser user) {
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
    return dao().findList(User.STATUS.eq(1));
}
```

### 2. 变更方法

**@BizMutation**：标记变更方法（新增、更新、删除）

**示例**：
```java
@BizMutation
public User createUser(User user) {
    return save(user);
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

1. **线程安全**：所有业务组件都是线程安全的
2. **事务管理**：默认情况下，变更方法会自动开启事务
3. **数据权限**：自动处理数据权限过滤
4. **异常处理**：业务异常会自动转换为GraphQL错误
5. **测试**：业务逻辑需要进行单元测试和集成测试

## 示例：完整的BizModel

```java
@BizModel
public class OrderBizModel extends CrudBizModel<Order> {
    @Inject
    private OrderItemDao orderItemDao;
    
    @Inject
    private ProductDao productDao;
    
    @Inject
    private IPricingService pricingService;
    
    public OrderBizModel() {
        setEntityName(Order.class.getName());
    }
    
    @BizQuery
    public Order findOrderById(String orderId) {
        return dao().findById(orderId);
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
        
        // 保存订单项
        for (OrderItem item : items) {
            item.setOrderId(savedOrder.getId());
            orderItemDao.save(item);
        }
        
        return savedOrder;
    }
    
    @Override
    protected void defaultPrepareSave(Order order) {
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