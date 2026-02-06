# 领域驱动设计（DDD）在Nop平台中的实践

## 核心差异：MyBatis vs NopORM

在Nop平台中，开发业务代码需要采用**领域驱动设计（DDD）**的思想。与传统基于MyBatis的命令脚本式开发相比，Nop平台采用以**实体为聚合根**的对象化编程模式，通过实体关联来获取数据，并以纯对象方式操作业务逻辑。

## 核心差异：MyBatis vs NopORM

### 传统MyBatis开发模式

```java
// ❌ 传统MyBatis：命令脚本式开发
// 1. 手动编写SQL/MyBatis XML
// 2. 手动处理关联关系（分多次查询或JOIN）
// 3. 手动构造DTO对象
// 4. 手动调用Mapper保存数据

// 示例：下单逻辑
public OrderDTO placeOrder(PlaceOrderRequest request) {
    // 手动查询用户
    User user = userMapper.selectById(request.getUserId());

    // 手动查询商品
    List<Product> products = productMapper.selectBatchIds(request.getProductIds());

    // 手动计算总价
    BigDecimal total = products.stream()
        .map(p -> p.getPrice())
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    // 手动创建订单DTO
    OrderDTO order = new OrderDTO();
    order.setUserId(user.getId());
    order.setTotal(total);
    order.setItems(items);
    // ... 更多手动操作

    // 手动保存订单
    orderMapper.insert(order);

    // 手动保存订单项
    for (OrderItemDTO item : order.getItems()) {
        orderItemMapper.insert(item);
    }

    return order;
}
```

**问题**：
- 业务逻辑散落在SQL/XML/Service层
- 关联关系需要手动处理
- 数据权限、验证等逻辑重复编写
- 代码冗长，难以维护

### NopORM对象化编程模式

```java
// ✅ Nop平台：实体为聚合根，纯对象方式操作
@BizMutation
public Order placeOrder(@Name("request") PlaceOrderRequest request,
                      IServiceContext context) {
    // 1. 获取聚合根（自动加载关联 + 数据权限检查）
    // ✅ 推荐：使用this.requireEntity()或requireEntity()，会自动检查数据权限
    Order order = requireEntity(request.getOrderId(), "read", context);
    // ❌ 避免：使用dao().requireEntity()可能绕过数据权限检查

    // 2. 通过关联获取数据（懒加载，按需加载）
    User user = order.getUser();  // 自动加载
    List<OrderItem> items = order.getItems();  // 自动加载

    // 3. 纯对象方式操作
    for (OrderItem item : items) {
        Product product = item.getProduct();  // 自动加载

        // 修改实体属性，会被自动跟踪
        item.setPrice(product.getPrice());
        item.setStatus(ItemStatus.CONFIRMED);
    }

    // 4. 自动计算总价
    order.calculateTotal();  // 实体内部方法

    // 5. 保存：调用CrudBizModel基类方法
    // 会自动：
    // - 保存主实体
    // - 保存关联实体（items等）
    // - ✅ 执行数据权限检查（关键！）
    // - 触发验证回调
    // ✅ 注意：如果已经获取了实体，直接传递实体对象，不需要EntityData.make()
    update(order, context);
}
```

**优势**：
- 代码简洁，业务逻辑集中
- 关联关系自动处理
- 数据权限、验证等逻辑由框架统一处理
- 符合领域模型，易于理解和维护

## 聚合根的职责划分

### 核心原则：实体字段不应该使用enum

**重要设计原则**：**实体上的字段不应该使用enum类型**。

**为什么？**

1. **Enum的值在编译期确定**
   ```java
   // ❌ 不推荐：使用enum
   public enum OrderStatus {
       PENDING,
       PAID,
       SHIPPED,
       CANCELLED
   }
   
   // 问题：编译期确定了这4个值，无法在不修改源码的情况下添加新状态
   // 如果业务需要增加"已退款"（REFUNDED）状态，必须修改源码重新编译
   ```

2. **业务扩展需要动态添加枚举值**
   ```java
   // 场景：租户A需要自定义订单状态
   // 某户A的订单状态可能包含："已审核"（AUDITED）、"已拒绝"（REJECTED）
   
   // 如果使用enum，租户A无法在不修改源码的情况下添加自定义状态
   // 需要重新编译、部署整个应用
   ```

3. **Constants支持动态扩展**
   ```java
    // ✅ 推荐：使用Constants
    // 注意：每个模块有一个核心的Constants类，所有这个模块的常量都定义在其中
    // OrderConstants 只是订单模块常量的一个示例
    public class OrderConstants {
        // 代码生成器根据数据库字典定义生成
        public static final String PENDING = "1";
        public static final String PAID = "2";
        public static final String SHIPPED = "3";
        public static final String CANCELLED = "4";
        public static final String AUDITED = "5";  // 租户A可以添加
        public static final String REJECTED = "6";  // 租户A可以添加
    }
   
   // ✅ 租户A可以在数据库中添加自定义字典项
   // 应用运行时动态加载字典值
   // 无需修改源码，支持定制
   ```

### Nop平台的Constants生成机制

Nop平台提供了xxx-codengen代码生成器，会根据orm.xml中的dict定义生成Constants类：

#### 1. 数据库字典表（nop_sys_dict + nop_sys_dict_item）

```xml
<!-- nop_sys_dict：字典定义 -->
<entity name="NopSysDict" table="nop_sys_dict">
    <field name="dictCode" type="string" length="50" primary="true"/>
    <field name="dictName" type="string" length="100"/>
    <field name="dictType" type="string" length="20"/>
    <field name="description" type="string" length="500"/>
</entity>

<!-- nop_sys_dict_item：字典项定义 -->
<entity name="NopSysDictItem" table="nop_sys_dict_item">
    <field name="itemId" type="string" length="32" primary="true"/>
    <field name="dictId" type="string" length="32"/>
    <field name="itemCode" type="string" length="50"/>
    <field name="itemName" type="string" length="100"/>
    <field name="itemValue" type="string" length="200"/>
    <field name="status" type="int" defaultValue="1"/>
</entity>
```

#### 2. 代码生成器生成的Constants类

```java
// ✅ 代码生成器根据字典自动生成
public class OrderConstants {
    // PENDING -> 待付款
    public static final String PENDING = "1";
    
    // PAID -> 已付款
    public static final String PAID = "2";
    
    // SHIPPED -> 已发货
    public static final String SHIPPED = "3";
    
    // CANCELLED -> 已取消
    public static final String CANCELLED = "4";
    
    // ✅ 动态添加的状态也会生成到这里
    // 租户A可以在数据库字典中添加"AUDITED" -> "5"
    // 重新生成代码后，AUDITED会出现在Constants中
}

// ✅ 一般我们使用这些常量
Order order = new Order();
order.setStatus(OrderConstants.PENDING);  // 使用常量
```

### 对比表：Enum vs Constants

| 维度 | Enum | Constants (推荐） |
|------|------|----------------|
| **值定义** | 编译期确定 | 从数据库字典生成，可动态扩展 |
| **定制能力** | ❌ 无法在不修改源码的情况下定制 | ✅ 支持在数据库中添加字典项，重新生成代码 |
| **业务扩展** | ❌ 需要修改源码重新编译 | ✅ 只需修改数据库字典，代码生成 |
| **热部署** | ❌ 需要重新编译部署 | ✅ 支持热部署（配置字典） |
| **租户定制** | ❌ 不同租户需要不同的枚举值时很难处理 | ✅ 每个租户可以有自定义字典项 |

### 正确的实体设计

```java
// ✅ 推荐：实体字段使用String类型，配合Constants
@Entity
@Table(name = "mall_order")
public class Order extends OrmEntity {

    @Id
    private String id;

    // ✅ 推荐：String类型 + 使用Constants
    private String status;  // "1", "2", "3", "4", "5", "6"...

    private OrderType orderType; // 普通类型
    private Integer payType;  // 支付类型

    // ✅ 只读帮助函数（领域事实）
    public boolean canBeCancelled() {
        return OrderConstants.CANCELLED.equals(this.status)
            || OrderConstants.PENDING.equals(this.status);
    }

    // ❌ 避免：enum类型字段
    // private OrderStatus status;  // enum，值编译期确定
    // private OrderType orderType;  // enum
}
```

### BizModel中的使用

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    @BizMutation
    public Order updateStatus(@Name("orderId") String orderId,
                            @Name("status") String status,
                            IServiceContext context) {
        // ✅ 推荐：使用Constants
        Order order = this.requireEntity(orderId, "update", context);
        
        // 验证状态值（确保是合法的字典项）
        if (!isValidStatus(status)) {
            throw new NopException(ERR_INVALID_STATUS).param("status", status);
        }
        
        // 使用Constants常量比较
        if (OrderConstants.PAID.equals(order.getStatus())) {
            throw new NopException(ERR_ORDER_CANNOT_UPDATE_PAID);
        }
        
        // 设置新状态
        order.setStatus(status);
        return update(order, context);
    }
}
```

### 为什么这样设计？

1. **支持动态扩展**
   - 业务可以在不修改源码的情况下，通过数据库字典添加新的状态值
   - 代码生成器重新生成Constants类，包含新增的字典项
   - 应用热部署新的Constants类，无需重新编译业务代码

2. **支持租户定制**
   - 租户A可以有自己的自定义订单状态字典
   - 租户B可以有不同的自定义状态值
   - Delta机制支持为不同租户定制字典配置

3. **符合"不修改源码定制"原则**
   - 实体类（稳定部分）不需要修改
   - Constants类（生成代码）可以通过代码生成工具重新生成
   - BizModel类（业务逻辑）可以通过Delta定制
   - 分离稳定部分和可定制部分

4. **数据库驱动设计**
   - 枚举值定义在数据库字典表中，数据即文档
   - 字典管理后台可以动态添加/修改枚举值
   - 权限控制、版本控制都有数据库管理支持

### 什么时候可以使用Enum？

```java
// ✅ 只有在以下情况下可以使用enum：

// 1. 技术层面的枚举（不变，不会因业务需要变化）
public enum OrmConstants {
    XDSL_SCHEMA_ORM,
    EXT_BASE_PACKAGE_NAME,
    TAG_SYS,
    TAG_EDIT,
    TAG_EAGER,
}

// 2. 框架层面的枚举（不变，平台定义）
public enum Tag {
    SYS,
    EDIT,
    EAGER,
    MANY_TO_MANY
}

// ❌ 避免：业务领域的枚举（会因业务需求变化）
// public enum OrderStatus { PENDING, PAID, SHIPPED }
// public enum OrderType { NORMAL, PRESALE, GROUP_BUY }
```

---

## 聚合根的职责划分

### 核心原则：只读帮助函数 vs 可定制业务逻辑

Nop平台中，聚合根（实体）和BizModel的职责有明确的划分：

#### 1. 聚合根实体（Entity）：只读帮助函数

**职责**：
- ✅ 提供只读的帮助函数，简化数据获取
- ✅ 反映领域模型中最稳定的关系
- ❌ **不应该**包含可定制的业务逻辑

**为什么？**
- 实体类是数据结构，是"稳定"的部分
- 实体类不能被Delta定制机制覆盖
- 任何可能定制的逻辑放在实体上会导致"可定制性陷阱"

**只读帮助函数示例**：

```java
// ✅ 推荐：聚合根只提供只读帮助函数
@Entity
@Table(name = "mall_order")
public class Order extends OrmEntity {

    @Id
    private String id;

    private String status;
    private BigDecimal totalAmount;

    // === 关联关系（稳定的关系） ===
    @ManyToOne
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;

    // === 只读帮助函数（领域语言的一部分） ===
    /**
     * 计算订单总金额
     * 注意：这是一个纯函数，只读取关联数据，不修改状态
     */
    public BigDecimal calculateTotal() {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream()
            .map(OrderItem::getTotalPrice)  // 调用子表的只读帮助函数
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 判断订单是否可取消
     * 注意：只读取状态，不执行任何业务逻辑
     */
    public boolean canBeCancelled() {
        return OrderConstants.PENDING.equals(this.status)
            || OrderConstants.PAID.equals(this.status);
    }

    /**
     * 获取主商品
     * 注意：只根据关联关系计算，不执行查询
     */
    public OrderItem getMainItem() {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
            .filter(item -> item.isMain())
            .findFirst()
            .orElse(null);
    }

    // ❌ 避免在实体上：可定制的业务逻辑
    // public void cancel() {
    //     // 取消逻辑可能因租户不同而不同，不应该在实体上
    //     this.status = OrderConstants.CANCELLED;
    // }
}
```

**只读帮助函数的特征**：
- ✅ **纯函数**：不修改实体状态
- ✅ **只读操作**：只读取关联数据或字段值
- ✅ **领域语言**：表达稳定的领域概念
- ✅ **不可定制**：这些是最基础的领域事实

---

#### 2. BizModel：可定制业务逻辑

**职责**：
- ✅ 包含所有可定制的业务逻辑
- ✅ 修改操作（设置字段值 + 业务规则）
- ✅ 复杂的业务流程
- ✅ 可以通过Delta机制定制

**为什么？**
- BizModel方法可以被Delta定制机制覆盖
- 支持不同租户、不同场景的业务逻辑定制
- 符合"差量编程"理念

**BizModel示例**：

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    /**
     * 取消订单
     * 注意：这是可定制的业务逻辑，放在BizModel中
     */
    @BizMutation
    public Order cancel(@Name("orderId") String orderId,
                    IServiceContext context) {
        // 1. 获取订单
        Order order = this.requireEntity(orderId, "update", context);

        // 2. 检查是否可以取消（使用实体的只读帮助函数）
        if (!order.canBeCancelled()) {
            throw new NopException(ERR_ORDER_CANNOT_CANCEL)
                .param("orderId", orderId)
                .param("status", order.getStatus());
        }

        // 3. 设置字段值（简单修改）
        order.setStatus(OrderConstants.CANCELLED);
        order.setCancelTime(LocalDateTime.now());
        order.setCancelReason("user_cancel");

        // 4. 更新（触发数据权限检查、验证等）
        return update(order, context);
    }

    /**
     * 发货
     * 注意：可能包含复杂逻辑，放在BizModel中
     */
    @BizMutation
    public Order ship(@Name("orderId") String orderId,
                   @Name("logisticsInfo") LogisticsInfoRequest logistics,
                   IServiceContext context) {
        Order order = this.requireEntity(orderId, "update", context);

        // 检查订单状态（使用实体的只读帮助函数）
        if (!order.canBeShipped()) {
            throw new NopException(ERR_ORDER_CANNOT_SHIP);
        }

        // 设置字段值
        order.setStatus(OrderConstants.SHIPPED);
        order.setShipTime(LocalDateTime.now());

        // 复杂逻辑：创建物流记录
        LogisticsInfo logisticsInfoEntity = new LogisticsInfo();
        logisticsInfoEntity.setOrderId(orderId);
        logisticsInfoEntity.setCompany(logistics.getCompany());
        logisticsInfoEntity.setTrackingNo(logistics.getTrackingNo());
        // ... 更多逻辑

        // 保存物流信息（通过关联自动保存）
        order.setLogisticsInfo(logisticsInfoEntity);

        return update(order, context);
    }
}
```

**BizModel方法可以被定制**：

```java
// 可以通过Delta机制定制订单的取消逻辑
// @delta/tenantA/Order.xbiz
<BizModel>
  <BizMethod name="cancel">
    <!-- 租户A有自己的取消规则 -->
    <Step name="custom-cancel-check" />
    <Step name="cancel" />
  </BizMethod>
</BizModel>
```

---

#### 3. 职责划分对比表

| 方面 | 实体（Entity） | BizModel |
|------|----------------|----------|
| **只读帮助函数** | ✅ 可以（领域语言的一部分） | - |
| **简单计算属性** | ✅ 可以（calculateTotal等） | - |
| **状态查询函数** | ✅ 可以（canBeCancelled等） | - |
| **修改操作** | ❌ 避免（设置字段值） | ✅ 应该在这里 |
| **复杂业务逻辑** | ❌ 避免任何可定制逻辑 | ✅ 应该在这里 |
| **流程编排** | ❌ 避免 | ✅ 应该在这里 |
| **定制能力** | ❌ 不可定制（稳定部分） | ✅ 可以通过Delta定制 |

---

#### 4. 设计原则

**实体设计原则**：
1. ✅ 只包含稳定的领域概念和关系
2. ✅ 只读帮助函数（纯函数，不修改状态）
3. ❌ **避免**任何可能定制的业务逻辑
4. ❌ **避免**修改操作（设置字段值）

**BizModel设计原则**：
1. ✅ 包含所有可定制的业务逻辑
2. ✅ 修改操作（设置字段值 + 业务规则验证）
3. ✅ 可以通过Delta机制定制和覆盖
4. ✅ 复杂流程和编排

**判断逻辑应该放在哪里的规则**：

```
如果逻辑满足以下条件之一，放在BizModel：
├─ 可能因租户/场景不同而变化
├─ 需要调用外部服务（RPC、消息队列等）
├─ 涉及多个实体的协同
├─ 可能被定制（需要支持不同实现）
├─ 不是纯粹的领域事实（不是稳定的）
└─ 修改状态或执行写操作

如果逻辑满足以下所有条件，可以放在实体：
├─ 纯函数（不修改状态）
├─ 只读操作（只读取字段或关联）
├─ 表达稳定的领域事实
├─ 不会因场景不同而变化
└─ 不会被定制
```

---

#### 5. 修正后的实体示例

**之前的问题示例**：

```java
// ❌ 错误：在实体上包含可定制的业务逻辑
@Entity
public class Order extends OrmEntity {
    // ... 字段定义

    // ❌ 这是可定制的逻辑，不应该在实体上
    public void cancel() {
        this.status = OrderConstants.CANCELLED;
        this.setCancelTime(LocalDateTime.now());
        // 不同租户可能有不同的取消规则
    }

    // ❌ 这是复杂业务逻辑，不应该在实体上
    public void ship(LogisticsInfo logistics) {
        this.status = OrderConstants.SHIPPED;
        // ... 复杂逻辑
    }
}
```

**修正后的正确示例**：

```java
// ✅ 正确：实体只提供只读帮助函数（领域事实）
@Entity
@Table(name = "mall_order")
public class Order extends OrmEntity {

    @Id
    private String id;

    // ✅ 推荐：String类型 + 使用Constants
    private String status;  // "1", "2", "3", "4", "5", "6"...

    private String orderType;  // 订单类型
    private Integer payType;  // 支付类型

    // === 关联关系（稳定的领域结构） ===
    @ManyToOne
    private User user;

    @OneToMany(mappedBy = "order")
    private List<OrderItem> items;

    // === 只读帮助函数（领域事实，稳定） ===
    /**
     * 判断订单是否可取消
     * 注意：只读取状态，不执行任何业务逻辑
     */
    public boolean canBeCancelled() {
        // ✅ 推荐：使用Constants
        return OrderConstants.PENDING.equals(this.status)
            || OrderConstants.PAID.equals(this.status);
    }

    /**
     * 判断订单是否可发货
     * 注意：只读取状态，不执行任何业务逻辑
     */
    public boolean canBeShipped() {
        // ✅ 推荐：使用Constants
        return OrderConstants.PAID.equals(this.status);
    }

    /**
     * 计算订单总金额
     * 注意：这是一个纯函数，只读取关联数据，不修改状态
     */
    public BigDecimal calculateTotal() {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream()
            .map(OrderItem::getTotalPrice)  // 调用子表的只读帮助函数
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

**BizModel中的修改操作**：

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    // ✅ 可定制的取消逻辑
    @BizMutation
    public Order cancel(@Name("orderId") String orderId, IServiceContext context) {
        Order order = this.requireEntity(orderId, "update", context);

        // 使用实体的只读帮助函数
        if (!order.canBeCancelled()) {
            throw new NopException(ERR_ORDER_CANNOT_CANCEL);
        }

        // 设置字段值
        order.setStatus(OrderConstants.CANCELLED);
        order.setCancelTime(LocalDateTime.now());
        order.setCancelReason("user_cancel");

        return update(order, context);
    }
}
```

---

#### 6. 与Delta定制机制的关系

Nop平台的Delta定制机制支持对BizModel的定制，但不支持对实体的定制：

```java
// ✅ 可以：通过Delta定制BizModel方法
// @delta/tenantA/Order.xbiz
<BizModel>
  <BizMethod name="cancel">
    <!-- 租户A有自己的取消逻辑 -->
    <Step name="tenantA-cancel-check" />
    <Step name="tenantA-cancel-action" />
  </BizMethod>
</BizModel>

// ❌ 不可以：通过Delta定制实体方法
// 实体类的方法不能被覆盖，因为实体类是稳定的
```

**为什么设计如此**：
- 实体类是领域模型中最稳定的关系，不应该被定制
- BizModel是业务逻辑的实现层，应该支持定制
- 这种分离保证了领域模型的稳定性和业务逻辑的灵活性

| 职责 | 传统DDD | Nop平台 |
|------|---------|---------|
| **核心价值** | 维护一致性和事务边界 | 成为**领域语言载体**和**统一的信息访问地图** |
| **主要职责** | 封装所有行为 | 提供**结构性充血**（领域语义、关联导航） |
| **业务逻辑** | 放在聚合根方法中 | 由**流程编排引擎**（NopTaskFlow）处理 |
| **数据访问** | 通过Repository | 通过**实体关联**（类似JPA的懒加载） |

#### 结构充血 vs 行为充血（职责分离）

Nop平台采用**职责分离**的设计，将"结构"与"行为"彻底分开：

**1. 实体（Entity）：充"结构"的血**
   - 提供领域语言载体（稳定的领域概念）
   - 提供只读帮助函数（领域事实）
   - ❌ **避免**任何可定制的业务逻辑
   - ❌ **避免**修改操作（设置字段值）

**2. BizModel：充"行为"的血**
   - 包含所有可定制的业务逻辑
   - 包含修改操作（设置字段值 + 业务规则验证）
   - 可以通过Delta机制定制
   - ✅ 支持：复杂流程和编排

```java
// ✅ 聚合根充的是"结构"和"领域语言"的血
public class Order extends OrmEntity {
    // 1. 关联关系（稳定的领域结构）
    @ManyToOne
    private User user;

    @OneToMany(mappedBy = "order")
    private List<OrderItem> items;

    // 2. 只读帮助函数（领域事实，稳定）
    public BigDecimal calculateTotal() {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream()
            .map(OrderItem::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean canBeCancelled() {
        return OrderConstants.PENDING.equals(this.status)
            || OrderConstants.PAID.equals(this.status);
    }

    /**
     * 判断订单是否可发货
     * 注意：只读取状态，不执行任何业务逻辑
     */
    public boolean canBeShipped() {
        // ✅ 推荐：使用Constants
        return OrderConstants.PAID.equals(this.status);
    }

    // ❌ 避免在实体上：可定制的业务逻辑
    // 以下逻辑可能因租户不同而变化，不应该在实体上
    // public void cancel() {
    //     this.status = OrderConstants.CANCELLED;
    //     this.setCancelTime(LocalDateTime.now());
    // }
}
```

**核心要点**：
- 聚合根提供**领域语义**：`order.user.name`、`order.canBeCancelled()`
- 聚合根提供**信息导航**：通过关联获取相关数据
- 聚合根提供**只读帮助函数**：`order.calculateTotal()`（纯函数，领域事实）
- ✅ **复杂业务逻辑由BizModel处理**，不在聚合根内部
- ✅ **修改操作由BizModel处理**（可定制），不在实体上

**BizModel vs 实体**：

```java
// 实体：只读帮助函数
public class Order extends OrmEntity {
    // ✅ 只读帮助函数（领域事实）
    public boolean canBeCancelled() {
        return OrderConstants.PENDING.equals(this.status);
    }
}

// BizModel：可定制的修改操作
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    // ✅ 可定制的取消逻辑
    @BizMutation
    public Order cancel(@Name("orderId") String orderId, IServiceContext context) {
        Order order = this.requireEntity(orderId, "update", context);

        // 使用实体的只读帮助函数
        if (!order.canBeCancelled()) {
            throw new NopException(ERR_ORDER_CANNOT_CANCEL);
        }

        // 设置字段值（修改操作）
        order.setStatus(OrderConstants.CANCELLED);
        order.setCancelTime(LocalDateTime.now());

        return update(order, context);
    }
}
```

## NopORM类似JPA的自动持久化

### 关联关系的自动处理

NopORM与JPA非常相似，支持自动的关联关系处理：

```java
public class Order extends OrmEntity {
    private String id;

    // 一对一关联
    @ManyToOne
    private User user;

    // 一对多关联
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;

    // 多对多关联（通过中间实体）
    @OneToMany(mappedBy = "order")
    private List<OrderTag> tags;

    // Getter方法自动处理懒加载
    public User getUser() {
        // 第一次访问时自动加载
        return user;
    }

    public List<OrderItem> getItems() {
        // 第一次访问时自动加载
        return items;
    }
}
```

### 自动保存和更新

**添加到关联集合的实体会被自动保存**（适用于子表无数据权限要求的场景）：

```java
@BizMutation
public void addOrderItem(@Name("orderId") String orderId,
                        @Name("productId") String productId,
                        IServiceContext context) {
    // 1. 获取订单（使用this.requireEntity()确保数据权限检查）
    Order order = this.requireEntity(orderId, "update", context);

    // 2. 创建订单项
    OrderItem item = new OrderItem();
    item.setOrder(order);
    item.setProductId(productId);
    item.setPrice(new BigDecimal("100"));

    // 3. 添加到关联集合
    // 关键：添加后会被自动保存！
    order.getItems().add(item);

    // 4. 保存订单
    // 框架会自动：
    // - 保存order（主实体）
    // - 保存item（关联实体）
    // - 更新关联关系
    update(order, context);
    // 不需要单独调用 dao().save(item)
}

### 操作子表：通过ICrudBiz调用实体方法

**重要场景**：如果子表有独立的数据权限要求，应该通过ICrudBiz接口的实体方法来操作：

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    @Inject
    protected ICrudBiz<OrderItem> orderItemBiz;  // 子表的BizModel

    @BizMutation
    public void addOrderItem(@Name("orderId") String orderId,
                            @Name("productId") String productId,
                            @Name("quantity") Integer quantity,
                            IServiceContext context) {
        // 1. 获取订单
        Order order = this.requireEntity(orderId, "read", context);

        // 2. 获取商品
        Product product = this.requireEntity(productId, "read", context);

        // 3. 创建订单项
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setPrice(product.getPrice());

        // 4. ✅ 使用ICrudBiz的saveEntity方法保存子表
        // 关键：saveEntity()会自动执行数据权限检查！
        // 与dao().saveEntity(item)不同，后者绕过了数据权限检查
        orderItemBiz.saveEntity(item, context);
    }

    @BizMutation
    public void updateOrderItem(@Name("itemId") String itemId,
                            @Name("quantity") Integer quantity,
                            IServiceContext context) {
        // 1. 获取订单项
        OrderItem item = orderItemBiz.requireEntity(itemId, "update", context);

        // 2. 修改属性
        item.setQuantity(quantity);

        // 3. ✅ 使用ICrudBiz的updateEntity方法更新子表
        // 关键：updateEntity()会自动执行数据权限检查！
        orderItemBiz.updateEntity(item, context);
    }

    @BizMutation
    public void deleteOrderItem(@Name("itemId") String itemId,
                            IServiceContext context) {
        // 1. 获取订单项
        OrderItem item = orderItemBiz.requireEntity(itemId, "delete", context);

        // 2. ✅ 使用ICrudBiz的deleteEntity方法删除子表
        // 关键：deleteEntity()会自动执行数据权限检查！
        orderItemBiz.deleteEntity(item, context);
    }
}
```

### ICrudBiz实体方法与dao()方法的区别

**ICrudBiz实体方法**（saveEntity/updateEntity/deleteEntity）：
- ✅ **自动执行数据权限检查**
- ✅ **自动执行唯一性检查**
- ✅ **自动执行字段验证**
- ✅ **自动处理级联删除**
- ✅ **自动处理关联引用检查**

**直接调用dao()方法**：
- ❌ **绕过数据权限检查**
- ❌ **绕过唯一性检查**
- ❌ **绕过字段验证**
- ❌ **绕过扩展点回调**

**使用原则**：

| 场景 | 使用方法 | 原因 |
|------|---------|------|
| **主表操作** | `this.requireEntity()` / `this.save()` / `this.update()` | 继承自CrudBizModel，自动数据权限 |
| **子表无数据权限要求** | 自动持久化（关联集合） | 通过主表保存时自动保存子表 |
| **子表有数据权限要求** | `orderItemBiz.saveEntity()` / `updateEntity()` / `deleteEntity()` | **必须通过ICrudBiz确保数据权限检查** |
| **查询子表** | `orderItemBiz.findList()` / `findPage()` | 确保查询时的数据权限过滤 |

**重要说明**：

```java
// ❌ 错误：子表有数据权限要求时直接调用dao()
orderItemBiz.dao().saveEntity(item, context);
// 问题：绕过了数据权限检查，可能导致安全问题

// ✅ 正确：通过ICrudBiz的实体方法
orderItemBiz.saveEntity(item, context);
// 正确：自动执行数据权限检查
```

**修改实体属性会被自动更新**：

```java
@BizMutation
public void updateOrderStatus(@Name("orderId") String orderId,
                          @Name("status") String status,
                          IServiceContext context) {
    // 1. 获取订单（使用this.requireEntity()确保数据权限检查）
    Order order = this.requireEntity(orderId, "update", context);

    // 2. 修改属性
    // 框架自动跟踪脏属性（dirtyProps）
    order.setStatus(status);
    order.setUpdateTime(LocalDateTime.now());

    // 3. 保存
    // 框架自动生成UPDATE语句，只更新修改的字段
    // ✅ 同时执行数据权限检查
    update(order, context);
}
```

### 脏检查（Dirty Checking）

NopORM内置了脏检查机制：

```java
public class OrmEntity {
    // 跟踪所有修改过的属性
    protected Set<String> dirtyProps = new HashSet<>();

    // 自动记录修改前后的值
    public void setStatus(String status) {
        dirtyProps.add("status");
        this.status = status;
    }

    // 保存时只更新修改的字段
    public void flush() {
        if (dirtyProps.contains("status")) {
            // 生成：UPDATE orders SET status = ? WHERE id = ?
            executeUpdate("status", status);
        }
        dirtyProps.clear();
    }
}
```

## 数据量控制：聚合范围

### 聚合根的范围

聚合根应该包含数据量在一定范围内的关联集合（比如几百行）：

```java
// ✅ 推荐：小范围聚合（几百行）
public class Order extends OrmEntity {
    @OneToMany(mappedBy = "order")
    private List<OrderItem> items;  // 一般几十行，最多几百行

    @ManyToOne
    private User user;  // 单个对象

    @ManyToOne
    private Address shippingAddress;  // 单个对象
}

// ⚠️ 避免：大数据量聚合
public class Order extends OrmEntity {
    // ❌ 不推荐：将所有订单历史放在聚合根中
    // 可能是几万行数据，导致性能问题
    @OneToMany(mappedBy = "order")
    private List<OrderHistory> histories;

    // ✅ 推荐：大数据量通过查询获取
    public List<OrderHistory> getHistories(IServiceContext context) {
        QueryBean query = new QueryBean();
        query.setFilter(FilterBeans.eq("orderId", this.getId()));
        return dao().findList(query, null, context);
    }
}
```

### 多层关联查询

通过实体关联获取数据：

```java
// 示例：获取订单的用户及其地址
Order order = dao().requireEntity(orderId, "read", context);

// 自动加载关联（懒加载）
User user = order.getUser();
Address address = user.getDefaultAddress();  // 自动加载

// 继续导航
String cityName = address.getCity().getName();  // 自动加载

// 等价于传统SQL：
// SELECT o.*, u.*, a.*, c.*
// FROM orders o
// LEFT JOIN users u ON o.user_id = u.id
// LEFT JOIN addresses a ON u.default_address_id = a.id
// LEFT JOIN cities c ON a.city_id = c.id
// WHERE o.id = ?
```

## CrudBizModel基类的使用

对于主实体（需要考虑数据权限等），应该调用CrudBizModel基类的方法：

### 基类方法对比

| 操作 | 直接dao()调用 | CrudBizModel方法 | 推荐使用 | 数据权限检查 |
|------|----------------|-------------------|-----------|-------------|
| 查询单个 | `dao().getEntityById()` | `getEntity()` / `requireEntity()` | ✅ `requireEntity()` | ✅ **自动执行** |
| 查询列表 | `dao().findListByQuery()` | `findList()` / `doFindList()` | ✅ `findList()` | ✅ **自动执行** |
| 保存数据 | `dao().saveEntity()` | `save()` / `doSave()` | ✅ `save()` | ✅ **自动执行** |
| 更新数据 | `dao().updateEntity()` | `update()` / `doUpdate()` | ✅ `update()` | ✅ **自动执行** |
| 删除数据 | `dao().deleteEntity()` | `delete()` / `doDelete()` | ✅ `delete()` | ✅ **自动执行** |

**重要说明**：
- 直接调用dao()方法会**绕过数据权限检查**，可能导致数据安全问题
- 使用CrudBizModel方法确保数据权限、验证、扩展点等逻辑正确执行

### 为什么使用CrudBizModel方法？

```java
// ❌ 不推荐：直接调用dao()方法
public void updateOrder(Order order, IServiceContext context) {
    // 问题：绕过了数据权限检查、验证、回调等
    dao().updateEntity(order);
}

// ✅ 推荐：使用CrudBizModel基类方法（主表）
public void updateOrder(@Name("data") Map<String, Object> data,
                      IServiceContext context) {
    // 框架自动处理：
    // 1. ✅ 数据权限检查（关键！）
    // 2. 字段验证
    // 3. 唯一性检查
    // 4. 触发defaultPrepareUpdate回调
    // 5. 脏检查，只更新修改的字段
    // 6. 触发afterEntityChange回调
    update(data, context);
}

// ✅ 推荐：子表有数据权限要求时，通过ICrudBiz调用实体方法
public void updateOrderItem(@Name("itemId") String itemId,
                          @Name("data") Map<String, Object> data,
                          IServiceContext context) {
    // 1. 获取子表实体
    OrderItem item = orderItemBiz.requireEntity(itemId, "update", context);

    // 2. 修改属性
    // 修改实体会自动保存，但如果修改了有可能导致数据权限变化或者唯一键冲突的属性，则需要调用updateEntity
    // 或直接通过buildEntityForSave构建新实体，然后调用saveEntity
    assignToEntity(item, data, context);

    // 3. ✅ 使用ICrudBiz的updateEntity方法，确保数据权限检查
    orderItemBiz.updateEntity(item, context);
}
```

**数据权限机制说明**：

CrudBizModel和ICrudBiz内置了数据权限检查机制：

```java
// service-layer.md中的说明
// - `getEntity(id, action, ignoreUnknown, includeLogicalDeleted, context)`
//   相比于`dao().getEntity()`增加了数据权限检查和元数据过滤

// - `requireEntity(id, action, context)`
//   getEntity之后验证返回实体非空，并执行数据权限检查

// - `saveEntity(entity, context)` - ICrudBiz接口方法
//   包含数据权限检查、唯一性检查、状态机初始化等逻辑

// - `updateEntity(entity, context)` - ICrudBiz接口方法
//   包含数据权限检查、唯一性检查等逻辑

// - `deleteEntity(entity, context)` - ICrudBiz接口方法
//   包含数据权限检查、关联引用检查、级联删除等逻辑
```

**关键点**：
- 使用`this.requireEntity(id, "read", context)`而不是`dao().requireEntity(id, "read", context)`
- 使用`this.get(id, context)`而不是`dao().getEntityById(id)`
- 使用`save()`、`update()`等方法而不是直接调用dao()

### doXXX方法的使用

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    // ✅ 使用doSave()：有更多控制权
    @Override
    protected Order doSave(EntityData<Order> entityData,
                       PrepareActionCallback<Order> prepareCallback,
                       IServiceContext context) {
        // 可以添加自定义逻辑
        Order order = entityData.getEntity();

        // 调用父类方法，触发所有扩展点
        return super.doSave(entityData, prepareCallback, context);
    }

    // ✅ 使用doUpdate()：有更多控制权
    @Override
    protected Order doUpdate(EntityData<Order> entityData,
                         PrepareActionCallback<Order> prepareCallback,
                         IServiceContext context) {
        // 可以添加自定义逻辑
        Order order = entityData.getEntity();

        // 修改实体属性
        order.setUpdateTime(LocalDateTime.now());

        // 调用父类方法
        return super.doUpdate(entityData, prepareCallback, context);
    }
}
```

## 扩展点的使用

### defaultPrepareSave - 保存前处理

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    @Override
    protected void defaultPrepareSave(EntityData<Order> entityData,
                                IServiceContext context) {
        super.defaultPrepareSave(entityData, context);

        Order order = entityData.getEntity();

        // 自动设置默认值
        if (order.getStatus() == null) {
            order.setStatus(OrderConstants.DRAFT);
        }

        // 自动计算字段
        order.calculateTotal();
    }
}
```

### defaultPrepareQuery - 查询前处理

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    @Override
    protected void defaultPrepareQuery(QueryBean query,
                                  IServiceContext context) {
        super.defaultPrepareQuery(query, context);

        // 添加默认过滤条件：只查询当前用户的订单
        Integer userId = context.getUserContext().getUserId();
        if (userId != null) {
            query.addFilter(FilterBeans.eq("userId", userId));
        }
    }
}
```

## 完整示例：下单业务

### 定义实体（聚合根）

```java
// app.mall.dao.entity.Order.java
@Entity
@Table(name = "mall_order")
public class Order extends OrmEntity {

    @Id
    private String id;

    // 关联用户
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // 关联订单项
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;

    // 订单状态
    private String status;

    // 总金额
    private BigDecimal totalAmount;

    // 业务方法
    public BigDecimal calculateTotal() {
        return items.stream()
            .map(OrderItem::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

### 定义BizModel

```java
// app.mall.service.entity.OrderBizModel.java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    /**
     * 创建订单
     * 使用纯对象方式操作，自动处理关联保存
     */
@BizMutation
public Order createOrder(@Name("request") CreateOrderRequest request,
                         IServiceContext context) {
    // 1. 获取用户（使用this.requireEntity()确保数据权限检查）
    User user = this.requireEntity(request.getUserId(), "read", context);

    // 2. 创建订单实体
    Order order = new Order();
    order.setUser(user);
    order.setStatus(OrderConstants.DRAFT);
    order.setTotalAmount(BigDecimal.ZERO);

    // 3. 创建订单项并关联
    List<OrderItem> items = new ArrayList<>();
    for (CreateOrderItemRequest itemReq : request.getItems()) {
        Product product = this.requireEntity(itemReq.getProductId(), "read", context);

        OrderItem item = new OrderItem();
        item.setOrder(order);  // 设置关联
        item.setProduct(product);  // 设置关联
        item.setQuantity(itemReq.getQuantity());
        item.setPrice(product.getPrice());

        items.add(item);
    }

    order.setItems(items);  // 设置关联集合

    // 4. 计算总价
    order.calculateTotal(); 

    // 5. 保存（自动保存主实体和关联实体）
    // ✅ 同时执行数据权限检查
    // ✅ 注意：如果已经获取了实体，直接传递实体对象，不需要EntityData.make()
    return save(order, context);
}

/**
 * 更新订单状态
 */
@BizMutation
public Order updateOrderStatus(@Name("orderId") String orderId,
                               @Name("status") String status,
                               IServiceContext context) {
    // 1. 获取订单（使用this.requireEntity()自动检查数据权限）
    Order order = this.requireEntity(orderId, "update", context);

    // 2. 修改状态
    order.setStatus(status);
    order.setUpdateTime(LocalDateTime.now());

    // 3. 保存（自动脏检查）
    return update(order, context);
}
}
```

## 常见问题

### Q1: 如何避免加载过多数据到内存？

**答案**：**关键是控制取到内存中的数据量，而不是"用关联还是用查询"**。

```java
// ❌ 错误：两种方式都会加载所有数据到内存
// 方式1：通过懒加载关联（不使用分页）
Order order = this.requireEntity(orderId, "read", context);
List<OrderLog> logs = order.getLogs();  // 会加载所有logs，可能几千条

// 方式2：通过查询（不使用分页）
QueryBean query = new QueryBean();
query.setFilter(FilterBeans.eq("orderId", orderId));
List<OrderLog> logs = dao().findList(query, null, context);  // 也会加载所有logs

// ✅ 正确：大数据量必须使用分页
// 方式1：使用findPage
QueryBean query = new QueryBean();
query.setFilter(FilterBeans.eq("orderId", orderId));
PageBean<OrderLog> logs = findPage(query, 1, 20, null, context);  // 只加载20条

// 方式2：使用QueryBean分页参数
query.setOffset(0);
query.setLimit(20);
List<OrderLog> logs = findList(query, null, context);  // 只加载20条
```

**数据量控制原则**：

| 场景 | 推荐方式 | 数据量控制 |
|------|---------|-----------|
| **小数据量（< 100行）** | 懒加载关联或无分页查询 | 可直接加载到内存 |
| **中等数据量（100-500行）** | 懒加载或分页查询 | 根据业务选择 |
| **大数据量（> 500行）** | **必须分页** | findPage()或设置limit |

**批量加载优化**：

当需要加载多个对象的关联属性时，使用批量加载避免N+1查询：

```java
// ❌ 低效：N+1查询
List<Order> orders = findList(query, null, context);
for (Order order : orders) {
    User user = order.getUser();  // 每个order单独查询user
}

// ✅ 高效：批量加载
List<Order> orders = findList(query, null, context);
dao().batchLoadProps(orders, Arrays.asList("user"));  // 一次批量查询
```

### Q2: 如何避免N+1查询？

**答案**：使用批量加载或分页查询。

```java
// 方式1：使用批量加载
// 避免循环中查询关联属性
List<Order> orders = findList(query, null, context);
dao().batchLoadProps(orders, List.of("user", "items"));  // 批量加载关联

// 方式2：使用分页查询（大数据量）
QueryBean query = new QueryBean();
query.setOffset(0);
query.setLimit(20);
List<Order> orders = findList(query, null, context);  // 每次只加载20条
```

### Q3: 什么时候使用doSave/doUpdate？

**答案**：需要精细控制时使用。

```java
// 使用save()：标准保存
return save(data, context);

// 使用doSave()：需要额外控制
@Override
protected Order doSave(EntityData<Order> entityData,
                   PrepareActionCallback<Order> prepareCallback,
                   IServiceContext context) {
    // 自定义逻辑
    Order order = entityData.getEntity();
    order.setCreateTime(LocalDateTime.now());

    // 调用父类
    return super.doSave(entityData, prepareCallback, context);
}
```

### Q4: 如何处理并发更新？

**答案**：使用乐观锁。

```java
@Entity
@Table(name = "mall_order")
@OptimisticLocking  // 启用乐观锁
public class Order extends OrmEntity {
    @Id
    private String id;

    @Version  // 版本号字段
    private Integer version;

    // ...
}
```

当发生并发冲突时，框架会自动抛出异常，业务层可以决定重试或提示用户。

## 总结

在Nop平台中采用DDD开发业务代码的核心要点：

1. **以实体为聚合根**：实体作为领域语言的载体，提供丰富的关联导航
2. **纯对象方式操作**：通过实体属性和关联关系来表达业务逻辑
3. **自动持久化**：NopORM类似JPA，自动保存关联实体、自动更新修改字段
4. **数据量控制**：**关键是控制取到内存的数据量**，小数据量可用懒加载或无分页，大数据量**必须分页**
5. **✅ 使用CrudBizModel方法**：对于主实体，使用基类的get/save/update等方法，确保**数据权限**、验证等逻辑生效
6. **✅ 子表有数据权限要求时使用ICrudBiz实体方法**：通过`saveEntity()`/`updateEntity()`/`deleteEntity()`等方法，确保子表操作也执行数据权限检查
7. **避免直接调用dao()方法**：直接调用dao()会绕过数据权限检查、验证、扩展点等机制
8. **利用扩展点**：通过defaultPrepareSave等扩展点添加自定义逻辑
9. **懒加载机制**：NopORM默认使用懒加载，关联属性按需加载
10. **✅ 实体字段不应该使用enum**：实体字段使用String类型，配合数据库字典生成Constants类，支持动态扩展和租户定制

**数据量控制关键**：
- NopORM关联属性默认懒加载，首次访问时才加载数据
- 无论通过关联还是查询，不使用分页都会加载全部数据到内存
- 大数据量（> 500行）**必须使用分页**：`findPage()`或QueryBean的`offset/limit`

**数据权限控制关键**：
- 主表：使用`this.requireEntity()`/`this.save()`/`this.update()`等方法
- 子表无数据权限要求：自动持久化（关联集合）
- 子表有数据权限要求：**必须通过ICrudBiz的实体方法**（`saveEntity()`/`updateEntity()`/`deleteEntity()`）

**字段类型控制关键**：
- ✅ **实体字段使用String类型**，不要使用enum
- ✅ 使用代码生成器根据数据库字典生成的Constants类
- ✅ 支持在不修改源码的情况下动态扩展枚举值
- ✅ 支持租户定制（不同租户可以有不同的字典值）

这种开发模式让代码更加简洁、易于维护，同时充分利用了Nop平台的ORM能力、数据权限保护机制和动态扩展能力。

**关键提醒**：在BizModel中应使用`this.requireEntity()`、`this.get()`等方法，而不是`dao().requireEntity()`、`dao().getEntityById()`等方法，以确保数据权限检查机制正常工作。

## 重要设计说明：使用CrudBizModel内置方法，不增加新的服务方法

### 核心原则

**增删改查尽量直接使用CrudBizModel上的方法，不用增加新的服务方法**

CrudBizModel基类已经提供了完整的增删改查方法：
- `get()` / `requireEntity()` - 获取单个实体（包含数据权限检查）
- `findList()` / `findPage()` / `findFirst()` - 查询列表（包含数据权限检查）
- `save()` / `update()` / `delete()` - 保存/更新/删除实体（包含数据权限检查、验证、回调）
- `batchDelete()` / `batchModify()` - 批量操作

### 何时需要增加新的服务方法？

只有在以下情况才需要在BizModel中增加新的服务方法（`@BizMutation`）：

1. **包含复杂业务流程**：
   ```java
   @BizMutation
   public Order processOrder(@Name("orderId") String orderId, IServiceContext context) {
       // 1. 获取订单
       Order order = requireEntity(orderId, "read", context);
       
       // 2. 复杂业务流程（检查库存、验证用户、创建发货单等）
       if (!checkStock(order)) {
           throw new NopException(ERR_STOCK_NOT_AVAILABLE);
       }
       if (!validateUser(order)) {
           throw new NopException(ERR_USER_INVALID);
       }
       createShippingInfo(order);
       notifyUser(order);
       
       // 3. 更新订单
       order.setStatus(OrderConstants.SHIPPED);
       return update(order, context);
   }
   ```

2. **需要调用外部服务**：
   ```java
   @BizMutation
   public Order syncPaymentStatus(@Name("orderId") String orderId, IServiceContext context) {
       Order order = requireEntity(orderId, "update", context);
       
       // 调用外部支付服务
       PaymentStatus status = paymentService.queryStatus(order.getPaymentNo());
       order.setPaymentStatus(status);
       
       return update(order, context);
   }
   ```

3. **需要特殊的事务处理**：
   ```java
   @BizMutation
   @Transaction(propagation = Propagation.REQUIRES_NEW)
   public Order batchProcess(@Name("orderIds") List<String> orderIds, 
                            IServiceContext context) {
       // 特殊的事务配置
       // 批量处理逻辑
   }
   ```

### 不需要增加新方法的常见场景

以下场景直接使用CrudBizModel内置方法即可：

| 场景 | 使用方法 | 不需要 |
|------|---------|--------|
| 获取订单 | `get(id, context)` 或 `requireEntity(id, "read", context)` | 不需要新增 `@BizMutation getOrder(id)` |
| 保存订单 | `save(order, context)` | 不需要新增 `@BizMutation saveOrder(order)` |
| 更新订单状态 | `update(order, context)` | 不需要新增 `@BizMutation updateStatus(id, status)` |
| 删除订单 | `delete(id, context)` 或 `requireEntity(id, "delete", context)` | 不需要新增 `@BizMutation deleteOrder(id)` |
| 查询订单列表 | `findPage(query, pageNo, pageSize, null, context)` | 不需要新增 `@BizMutation findOrders(query)` |
| 更新订单项 | `orderItemBiz.updateEntity(item, context)` | 不需要新增 `@BizMutation updateItem(data)` |

### 为什么这样设计？

1. **一致性**：所有BizModel使用统一的方法调用，代码风格一致
2. **自动特性**：内置方法自动包含数据权限检查、验证、扩展点等
3. **维护性**：减少重复代码，降低维护成本
4. **可扩展性**：内置方法可以通过Delta机制定制
5. **前端友好**：内置方法可以直接通过GraphQL调用，前端无需记忆自定义方法名

## 相关文档

- [ORM架构](../02-architecture/orm-architecture.md) - NopORM的详细设计
- [数据访问层](../03-development-guide/data-access.md) - IEntityDao使用指南
- [服务层开发](../03-development-guide/service-layer.md) - BizModel开发详解
- [复杂业务开发](../03-development-guide/complex-business-development.md) - 复杂业务场景处理
