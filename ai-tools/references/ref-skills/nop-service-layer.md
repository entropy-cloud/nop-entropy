# nop-service-layer Skill

## Skill 概述

**名称**: nop-service-layer（服务层设计）

**定位**: 根据XMeta领域模型和自然语言需求，设计BizModel服务层和业务方法，实现业务逻辑封装

**输入**:
- 领域模型（`{module}.xmeta.xml`）
- 自然语言需求（用户故事、功能描述）
- 业务规则列表

**输出**:
- `{module}.xbiz.xml`（BizModel服务定义）
- `{module}-api.xml`（API定义，可选）

**能力**:
- 需求理解与领域建模
- 实现BizModel服务类设计
- 设计业务方法和扩展点
- 集成CrudBizModel的CRUD能力
- 通过@BizQuery、@BizMutation、@BizAction注解服务方法
- 支持事务管理和数据权限控制

**依赖**:
- Nop平台服务层开发指南（docs-for-ai/getting-started/service/service-layer-development.md）
- Nop平台BizModel基类文档
- GraphQL服务开发指南（docs-for-ai/getting-started/api/graphql-guide.md）

## 核心原则

### 1. BizModel作为领域逻辑容器
- **职责封装**：BizModel封装一个业务领域的所有操作
- **协议中性**：BizModel使用POJO，不依赖特定协议
- **可测试性**：BizModel方法可以直接单元测试

### 2. CRUD优先使用内置方法
- **内置方法**：`findPage()`、`save()`、`update()`、`delete()`、`findList()`等
- **避免重复实现**：不要在BizModel中重新实现CRUD逻辑
- **扩展机制**：通过重写`defaultPrepare*`等回调方法添加自定义逻辑

### 3. View DDD原则
- **实体只包含数据属性**：不在实体上添加业务方法
- **领域逻辑通过get方法暴露**：如`order.getItems()`
- **易变逻辑放在XMeta**：通过`getter`、`domain`、`computed`等属性

### 4. 注解驱动服务设计
- **@BizModel**：标记领域模型类
- **@BizQuery**：标记查询方法
- **@BizMutation**：标记变更方法
- **@BizAction**：标记动作方法

### 5. 事务管理
- **@Transactional**：自动开启事务
- **回调扩展**：`afterSave()`、`afterUpdate()`等

### 6. 数据权限控制
- **字段级权限**：在XMeta中定义数据权限
- **权限检查**：使用`checkDataAuth()`方法
- **数据脱敏**：通过`visibleOn`、`mask`属性控制字段可见性

## 工作流程

### 阶段1：需求分析
1. **理解业务场景**
   - 识别核心业务对象（实体、聚合根）
   - 理解业务流程和数据流转
   - 提取业务规则和约束

2. **识别扩展点**
   - 哪些功能需要定制化实现
   - 哪些逻辑需要特殊处理

3. **生成服务列表**
   - 列出需要实现的服务方法
   - 每个服务方法标记`@BizQuery`或`@BizMutation`

### 阶段2：BizModel设计
1. **继承CrudBizModel**
   ```java
   @BizModel("{module}")
   public class {module}ServiceModel extends CrudBizModel<{module}Entity> {
       @Override
       public String getEntityName() {
           return "{module}";
       }
   }
   ```

2. **定义数据属性**
   - 通过`@Data`注解注入的域对象
   - 提供便捷的getter方法

3. **设计业务方法**
   - 根据需求设计服务方法
   - 使用注解标记方法类型
   - 正确使用事务和异常处理

4. **定义查询方法**
   - 使用`@BizQuery`注解
   - 使用`QueryBean`封装查询条件
   - 避免N+1查询问题

### 阶段3：XMeta配置
1. **生成.xmeta.xml**
   - 为每个实体配置元数据
   - 定义实体、字段、关系

2. **定义domain属性**
   - 使用`@Domain`注解标记语义
   - 定义computed属性计算衍生数据
   - 定义transformIn/transformOut进行数据转换

3. **定义数据权限**
   - 使用`@Auth`注解标记权限
   - 配置字段级的读取、更新、删除权限

### 阶段4：服务验证
- 确保生成的代码符合Nop平台规范
- 验证是否使用了内置CRUD方法
- 验证是否遵循View DDD原则
- 验证事务管理是否正确

## AI推理策略

### 1. 需求理解推理
- **第一步**：提取核心业务概念
  - 使用正则表达式或关键词识别实体、聚合根
- **第二步**：理解业务关系
  - 识别"一对一"、"一对多"、"多对多"等关系模式
- **第三步**：识别业务规则
  - 识别状态转换规则、金额计算规则等

### 2. 服务方法设计推理
- **方法职责**：判断应该定义为查询方法还是变更方法
- **参数设计**：根据业务场景设计合理的参数列表
- **返回类型**：根据业务语义选择合适的返回类型

### 3. View DDD遵循推理
- **属性放置**：判断应该放在实体上还是XMeta中
  - **get方法暴露**：判断哪些数据应该通过getter暴露
- **业务逻辑位置**：复杂逻辑应该放在XMeta的computed属性中

### 4. 性能优化推理
- **批量操作**：对于批量更新，使用`dao().batchSaveEntities()`
- **查询优化**：预加载关联数据，使用`batchLoadProps()`
- **缓存策略**：合理使用`@Cacheable`注解

### 5. 并发控制推理
- **分布式事务**：使用`@Transactional`注解
- **TCC模式**：识别需要跨服务的业务场景
- **Saga模式**：设计长事务流程

## 常见模式

### 1. CRUD服务
```java
@BizModel("NopAuthUser")
public class NopAuthUserServiceModel extends CrudBizModel<NopAuthUser> {
    // 查询方法
    @BizQuery
    public NopAuthUser findActiveUsers(@ContextSource context) {
        return dao().findPageByExample(context.buildExample(
            new NopAuthUser().setStatus(1)
        ), 
            context.getFieldSelection("id", "name"));
    }
    
    @BizQuery
    public NopAuthUser getUserByOpenId(@Name("openId") String openId) {
        return dao().findFirstByExample(context.buildExample(
            new NopAuthUser().setOpenId(openId)
        ));
    }
    
    // 变更方法
    @BizMutation
    public NopAuthUser updateUserStatus(@Name("userId") String userId, 
                                    @Name("status") Integer status) {
        return update(context.buildDataMap(
            new NopAuthUser().setId(userIdGenerator.generateUserId()),
            new NopAuthUser().setStatus(status),
            new NopAuthUser().setUpdateTime(LocalDateTime.now())
        ));
    }
    
    @BizMutation
    @Transactional
    public void resetUserPassword(String userId, String newPassword) {
        txn(() -> {
            NopAuthUser user = dao().getEntityById(userId);
            if (user == null) {
                throw new NopException(Errors.ERR_BIZ_OBJECT_NOT_FOUND);
            }
            passwordPolicy.checkPassword(newPassword);
            user.setPassword(passwordEncoder.encode(newPassword));
            dao().saveEntity(user);
        });
    }
}
```

### 2. 复杂业务服务
```java
@BizModel("Order")
public class OrderServiceModel extends CrudBizModel<Order> {
    @Inject
    private OrderBizModel orderBizModel;
    
    // 使用@Data注入领域对象
    @Data
    private Order order;
    
    @BizMutation
    @Transactional
    public Order createOrder(@Name("order") Order order, 
                                    @Name("items") List<OrderItem> items,
                                    @Name("user") User user) {
        // 设置订单号
        order.setOrderNo(orderBizModel.generateOrderNo());
        
        // 计算总金额
        BigDecimal totalAmount = calculateTotalAmount(order, items);
        order.setTotalAmount(totalAmount);
        
        // 设置订单状态和用户信息
        order.setUser(user);
        order.setCreateTime(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING.name());
        
        // 保存订单
        Order savedOrder = save(order, items);
        
        // 保存订单项
        List<OrderItem> savedItems = saveOrderItems(order, items);
        
        return savedOrder;
    }
    
    @BizAction
    public void approveOrder(@Name("orderId") String orderId) {
        Order order = dao().getEntityById(orderId);
        order.setStatus(OrderStatus.APPROVED.name());
        dao().saveEntity(order);
    }
    
    // 辅助方法
    private BigDecimal calculateTotalAmount(Order order, List<OrderItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            BigDecimal itemTotal = item.getProductPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(itemTotal);
        }
        return total;
    }
    
    private List<OrderItem> saveOrderItems(Order order, List<OrderItem> items) {
        List<OrderItem> savedItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            item.setOrderId(order.getId());
            item.setOrderNo(orderBizModel.generateOrderItemNo());
            savedItems.add(item);
        }
        
        dao().batchSaveEntities(savedItems);
        return savedItems;
    }
    
    // 覆盖父类CRUD
    @Override
    protected void defaultPrepareSave(EntityData<Order> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);
        // 自动设置审计字段
        entityData.setEntity(orderBizModel.createOrder(context.buildDataMap()));
    }
    
    @Override
    protected void defaultPrepareUpdate(EntityData<Order> entityData, IServiceContext context) {
        super.defaultPrepareUpdate(entityData, context);
        // 自动设置更新时间
        entityData.setEntity(orderBizModel.createOrder(context.buildDataMap()));
    }
}
```

### 3. 扩展点设计
- **defaultPrepareSave**：设置订单号
- **calculateTotalAmount**：计算总金额
- **业务规则验证**：检查商品库存、用户信用等

## 常见错误

### 1. 直接使用DAO
**错误代码**：
```java
@BizModel("User")
public class UserServiceModel {
    @Inject
    private IUserDao dao;
    
    // ❌ 错误：直接在业务逻辑中调用dao
    @BizMutation
    public void updateUser(String userId, String name) {
        // ❌ 错误做法
        User user = dao().requireEntityById(userId);
        user.setName(name);
        dao().saveEntity(user);
    }
}
```

### 2. 在实体上添加业务方法
**错误代码**：
```java
@Entity
public class Order {
    // ❌ 错误：在实体上添加业务方法
    public BigDecimal calculateTotalAmount() {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : this.items) {
            total = total.add(item.getProductPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        }
        return total;
    }
}
```

### 3. 不使用内置方法
**错误代码**：
```java
@BizModel("Order")
public class OrderServiceModel extends CrudBizModel<Order> {
    @Override
    public Order save(@Name("order") Order order, 
                                     @Name("items") List<OrderItem> items) {
        // ❌ 错误：重新实现CRUD逻辑
        Order savedOrder = new Order();
        for (OrderItem item : items) {
            savedOrder.addItem(item);
        }
        
        dao().saveEntity(savedOrder);
        dao().batchSaveEntities(savedOrder.getItems());
        return savedOrder;
    }
}
```

### 4. View DDD违反
**错误代码**：
```java
public class Order {
    // ❌ 错误：在实体上添加业务方法
    public void setStatus(OrderStatus status) {
        // ❌ 违反View DDD原则
    }
    
    public void setTotalAmount(BigDecimal totalAmount) {
        // ❌ 违反View DDD原则
    }
}
```

## 最佳实践

### 1. 优先使用内置CRUD方法
- 对于标准的CRUD操作，优先使用`findPage()`、`save()`等方法
- 对于特殊场景，通过重写`defaultPrepare*`回调添加逻辑

### 2. 正确使用@Data注解
- 使用`@Data`注解注入领域对象，方便访问
- 通过`@Data`获取实体实例，而不是直接查询数据库

### 3. 遵循View DDD原则
- 实体只包含数据属性和get方法
- 易变业务逻辑通过XMeta的computed属性实现
- 不在实体上添加任何业务方法或业务规则

### 4. 合理使用事务
- 对于需要事务保证的操作，使用`@Transactional`注解
- 对于不需要事务的查询方法，不添加注解

### 5. 数据权限控制
- 在XMeta中定义字段的`visibleOn`和`mask`属性
- 在BizModel中实现`checkDataAuth()`方法进行权限检查
- 对于敏感字段，使用`mask`属性控制输出

## 验证点

### 1. 生成代码验证
- [ ] BizModel是否正确继承CrudBizModel
- [ ] 是否正确使用内置方法
- [ ] 是否通过重写方法扩展功能

### 2. View DDD遵循验证
- [ ] 实体是否只包含数据属性
- [ ] 是否不在实体上添加业务方法
- [ ] 易变逻辑是否通过XMeta实现
- [ ] 是否正确使用get方法

### 3. 事务管理验证
- [ ] 变更方法是否使用`@Transactional`注解
- [ ] 是否在事务方法中直接调用dao.save()

### 4. 数据权限验证
- [ ] 是否在XMeta中定义权限
- [ ] 是否在BizModel中实现`checkDataAuth()`

## 下一步工作

当前skill完成数据库建模、DDD建模、服务层设计。下一个skill将使用这些产物生成服务层代码和配置。

