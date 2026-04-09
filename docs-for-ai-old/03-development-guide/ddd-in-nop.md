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

    // 手动构造订单项DTO列表
    List<OrderItemDTO> items = buildOrderItems(request, products);

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
    updateEntity(order, context);
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

// ✅ 一般我们使用这些常量（实体创建建议走BizModel/ICrudBiz的newEntity）
Order order = orderBiz.newEntity();
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
// 注意：NopORM不使用JPA的@Entity/@ManyToOne等注解来建模。
// 实体与关联关系在orm.xml中定义，然后由代码生成器生成实体类（通常无需手写注解）。
// 业务代码一般直接使用生成的实体类，并在其上补充只读帮助函数（若需要）。
public class Order extends OrmEntity {

    private String id;

    // ✅ 推荐：String类型 + 使用Constants/字典
    private String status;  // "1", "2", "3", "4", ...

    private String orderType;
    private Integer payType;

    // ✅ 只读帮助函数（领域事实）
    public boolean canBeCancelled() {
        return OrderConstants.CANCELLED.equals(this.status)
            || OrderConstants.PENDING.equals(this.status);
    }
}
```

对应的`orm.xml`（示意，结构以`/nop/schema/orm/orm.xdef`与`/nop/schema/orm/entity.xdef`为准）：

```xml
<orm x:schema="/nop/schema/orm/orm.xdef"
         xmlns:x="/nop/schema/xdsl.xdef">
    <entities>
        <entity name="Order" tableName="mall_order">
            <columns>
                <column name="id" code="id" stdDomain="string" primary="true"/>
                <column name="status" code="status" stdDomain="string" domain="mall_order_status"/>
                <column name="orderType" code="order_type" stdDomain="string" domain="mall_order_type"/>
                <column name="payType" code="pay_type" stdDomain="int" domain="mall_pay_type"/>
            </columns>

            <relations>
                <to-one name="user" refEntityName="User">
                    <join>
                        <on leftProp="userId" rightProp="id"/>
                    </join>
                </to-one>

                <to-many name="items" refEntityName="OrderItem">
                    <join>
                        <on leftProp="id" rightProp="orderId"/>
                    </join>
                </to-many>
            </relations>
        </entity>
    </entities>
</orm>
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
// 注意：实体字段/关联通常由orm.xml建模并生成，这里只演示只读帮助函数应如何编写。
public class Order extends OrmEntity {

    private String id;

    private String status;
    private BigDecimal totalAmount;

    // === 关联关系（如user/items）由orm.xml定义并生成对应的导航属性/方法 ===
    // 示例：getUser()/getItems() 均为生成代码提供（懒加载按需触发）

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
        updateEntity(order, context);
        return order;
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
    // 注意：一般不直接new实体实现类，避免Delta把实现类定制为 LogisticsInfoEx 等导致不一致
    LogisticsInfo logisticsInfoEntity = newEntity("LogisticsInfo");
        logisticsInfoEntity.setOrderId(orderId);
        logisticsInfoEntity.setCompany(logistics.getCompany());
        logisticsInfoEntity.setTrackingNo(logistics.getTrackingNo());
        // ... 更多逻辑

        // 保存物流信息（通过关联自动保存）
        order.setLogisticsInfo(logisticsInfoEntity);

        updateEntity(order, context);
        return order;
    }
}
```

**BizModel方法可以被定制**：

```java
// 可以通过Delta机制定制订单的取消逻辑
// @delta/tenantA/Order.xbiz
<biz x:schema="/nop/schema/biz/xbiz.xdef"
         xmlns:x="/nop/schema/xdsl.xdef">
    <actions>
        <!-- .xbiz 中通过 actions/query|mutation 等节点定义或覆盖后端动作 -->
        <!-- 覆盖Java BizModel上已有的cancel方法：不要写arg/return（可通过Java方法反射获取） -->
        <mutation name="cancel">
            <source>
                <!-- 租户A有自己的取消规则（示意：实际可用xpl/bo/task等） -->
                <!-- ... -->
            </source>
        </mutation>
    </actions>
</biz>
```

说明：`.xbiz`文件的结构不是`<BizModel><BizMethod>`，而是以`<biz ...>`为根节点，具体结构以`/nop/schema/biz/xbiz.xdef`为准。

补充：

- 如果是**覆盖Java BizModel中已有的方法**（例如Java里已定义`cancel`），一般**不写**`<arg>`/`<return>`，这些信息可以通过反射得到。
- 如果是**新增action**（Java里没有对应方法），需要按`xbiz.xdef`要求补全`<arg>`/`<return>`配置。

例如新增一个内部action（示意）：

```xml
<biz x:schema="/nop/schema/biz/xbiz.xdef"
         xmlns:x="/nop/schema/xdsl.xdef">
    <actions>
        <action name="validateAndNormalize">
            <arg name="orderId" type="string" mandatory="true"/>
            <return type="string"/>
            <source>
                <!-- ... -->
            </source>
        </action>
    </actions>
</biz>
```
可参考真实示例：

- `nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/model/NopAuthUser/_NopAuthUser.xbiz`
- `nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/model/NopAuthUser/NopAuthUser.xbiz`

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
// 注意：这里不使用JPA注解。实体/关联建模在orm.xml中完成。
public class Order extends OrmEntity {

    private String id;

    // ✅ 推荐：String类型 + 使用Constants
    private String status;  // "1", "2", "3", "4", "5", "6"...

    private String orderType;  // 订单类型
    private Integer payType;  // 支付类型

    // === 关联关系（稳定的领域结构） ===
    // user/items 由orm.xml定义并生成关联导航

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
<biz x:schema="/nop/schema/biz/xbiz.xdef"
         xmlns:x="/nop/schema/xdsl.xdef">
    <actions>
        <!-- 覆盖Java BizModel上已有的cancel方法：不要写arg/return（可通过Java方法反射获取） -->
        <mutation name="cancel">
            <source>
                <!-- 租户A有自己的取消逻辑（示意） -->
                <!-- ... -->
            </source>
        </mutation>
    </actions>
</biz>

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
    // 这里不使用JPA注解。user/items等关联由orm.xml定义并生成导航方法。
    // 业务代码把它当成可导航的领域语言即可：order.getUser(), order.getItems()

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

> 说明：这里说“类似JPA”指的是**使用体验**（关联导航、懒加载、脏检查、级联保存等）。
> NopORM的实体/关联定义来自`orm.xml`模型文件，实体代码通常由生成器产生，不需要手工写JPA注解。

### 关联关系的自动处理

NopORM与JPA非常相似，支持自动的关联关系处理：

```java
public class Order extends OrmEntity {
    private String id;

    // user/items/tags 等关联由 orm.xml 模型定义：
    // - 运行时按需懒加载
    // - 代码生成器提供 getUser()/getItems()/getTags() 等导航能力
}
```

对应的`orm.xml`关联定义示意：

```xml
<orm x:schema="/nop/schema/orm/orm.xdef"
         xmlns:x="/nop/schema/xdsl.xdef">
    <entities>
        <entity name="Order" tableName="mall_order">
            <columns>
                <column name="id" code="id" stdDomain="string" primary="true"/>
            </columns>

            <relations>
                <to-one name="user" refEntityName="User">
                    <join>
                        <on leftProp="userId" rightProp="id"/>
                    </join>
                </to-one>

                <to-many name="items" refEntityName="OrderItem" cascadeDelete="true">
                    <join>
                        <on leftProp="id" rightProp="orderId"/>
                    </join>
                </to-many>

                <to-many name="tags" refEntityName="OrderTag">
                    <join>
                        <on leftProp="id" rightProp="orderId"/>
                    </join>
                </to-many>
            </relations>
        </entity>
    </entities>
</orm>
```

### 自动保存和更新

**添加到关联集合的实体会被自动保存**（适用于子表无数据权限要求的场景）：

> 注意：一般不建议直接`new OrderItem()`这类ORM实体。
> 因为实体实现类可能被Delta定制（例如从`OrderItem`定制为`OrderItemEx`），
> 更稳妥的方式是通过BizModel/ICrudBiz提供的`newEntity(...)`来创建。

```java
@BizMutation
public void addOrderItem(@Name("orderId") String orderId,
                        @Name("productId") String productId,
                        IServiceContext context) {
    // 1. 获取订单（使用this.requireEntity()确保数据权限检查）
    Order order = this.requireEntity(orderId, "update", context);

    // 2. 创建订单项
    OrderItem item = newEntity("OrderItem");
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
    // ✅ 有子表权限/独立约束时，优先用子表Biz来创建实体
    OrderItem item = orderItemBiz.newEntity();
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
// ❌ 避免：不要通过dao()去保存ORM实体（也不要假设ICrudBiz暴露dao()）
// 问题：容易绕过数据权限检查、唯一性检查、扩展点回调等治理机制

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
// ✅ 推荐：主子表关系 - 直接使用 orm.xml 定义的关联属性
// items/user/shippingAddress 等关联由 orm.xml 模型定义并生成导航方法
public class Order extends OrmEntity {
    // 对于主子表（如 Order-OrderItem），直接使用 getItems()
    // 框架自动懒加载，且能利用 N+1 优化（batchLoadProps）
    
    // ✅ 小数据量关联（几十到几百行）：直接用关联属性
    public BigDecimal calculateTotal() {
        return getItems().stream()
            .map(OrderItem::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    // ✅ 判断逻辑：直接用关联属性
    public boolean hasItems() {
        return !getItems().isEmpty();
    }
}
```

### requireBiz 的使用场景

`requireBiz` + `findList`/`findCount` 主要用于**非聚合内**的查询，或**计数/存在性检查**：

```java
public class Order extends OrmEntity {
    
    // ✅ 场景1：计数 - 不需要返回数据，只返回数量
    public long getIncompleteTaskCount() {
        IServiceContext context = IServiceContext.requireCtx();
        IOrderTaskBiz taskBiz = requireBiz(IOrderTaskBiz.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("orderId", this.getId()));
        query.addFilter(FilterBeans.notEq("status", "COMPLETED"));
        return taskBiz.findCount(query, context);
    }
    
    // ✅ 场景2：存在性检查 - 检查是否有符合条件的记录
    public boolean hasActiveShipping() {
        IServiceContext context = IServiceContext.requireCtx();
        IShippingBiz shippingBiz = requireBiz(IShippingBiz.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("orderId", this.getId()));
        query.addFilter(FilterBeans.eq("status", "IN_PROGRESS"));
        return shippingBiz.findCount(query, context) > 0;
    }
    
    // ✅ 场景3：分页查询 - 大数据量时按需加载部分数据
    public PageBean<OrderLog> getRecentLogs(int pageNo, int pageSize) {
        IServiceContext context = IServiceContext.requireCtx();
        IOrderLogBiz logBiz = requireBiz(IOrderLogBiz.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("orderId", this.getId()));
        query.addOrderBy(OrderFieldBean.desc("createTime"));
        return logBiz.findPage(query, null, context);
    }
}
```

### ⚠️ 重要原则

```java
// ❌ 避免：对主子表使用 requireBiz + findList
// 如果 OrderItem 是 Order 的子表，直接用 getItems() 即可
public class Order extends OrmEntity {
    // ❌ 错误：主子表不应该这样查询
    public List<OrderItem> getItemsWrong() {
        IServiceContext context = IServiceContext.requireCtx();
        IOrderItemBiz itemBiz = requireBiz(IOrderItemBiz.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("orderId", this.getId()));
        return itemBiz.findList(query, null, context);  // 多此一举
    }
    
    // ✅ 正确：直接使用 getItems()，由 orm.xml 中的 to-many 关系自动生成
    // 无需重载，直接调用即可
    // getItems() 框架自动懒加载，且支持批量预加载优化
}
```

### 业务层面的数据量控制

如果关联数据量真的很大（超过几百行），应该在**业务层面规避**，而不是通过技术手段解决：

```java
// ❌ 错误思路：试图通过 requireBiz 加载大量数据
public List<OrderHistory> getAllHistories() {
    // 即使分页，如果业务上需要一次性处理所有历史，仍然会有问题
    // 几万条历史记录不应该一次性加载到内存
}

// ✅ 正确思路：业务层面控制
// 1. 限制时间范围：只查询最近30天的历史
// 2. 限制返回字段：只返回必要的摘要信息
// 3. 分批处理：使用批处理框架处理大数据量
// 4. 异步导出：大数据量导出为文件，而不是内存处理
```

**关键原则**：
- 主子表关系（聚合内）：直接用 `getItems()` 等关联属性
- 聚合外查询、计数、存在性检查：用 `requireBiz` + `findCount`/`findPage`
- 大数据量场景：业务层面规避，避免一次性加载

### 多层关联查询

通过实体关联获取数据：

```java
// 示例：获取订单的用户及其地址
Order order = orderBiz.requireEntity(orderId, "read", context);

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
    // 走BizModel的update/updateEntity确保扩展点与治理逻辑执行
    update(order, context);
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

### doXXX方法：一般不建议复写

`doSave/doUpdate/doDelete`属于**更底层**的执行入口，一般情况下不建议直接复写它们。

- 常见定制：优先使用`defaultPrepareSave/defaultPrepareUpdate/defaultValidate`等**扩展点**
- 只有在确实需要改变执行流程（例如变更prepare回调的组合方式、绕开/替换某个环节）时，才考虑复写`doXXX`

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    // ✅ 推荐：复写 defaultPrepareSave/defaultPrepareUpdate 等扩展点
    @Override
    protected void defaultPrepareSave(EntityData<Order> entityData,
                                      IServiceContext context) {
        super.defaultPrepareSave(entityData, context);

        Order order = entityData.getEntity();
        // 在保存前统一补默认值/派生字段
        if (order.getStatus() == null) {
            order.setStatus(OrderConstants.DRAFT);
        }
        order.calculateTotal();
    }
}
```

另外，`doSave/doUpdate`调用时本身允许传入`PrepareActionCallback`，所以在某些场景下，你也可以**不复写**`defaultPrepareSave`，而是在调用`doSave`时传入不同的回调函数（例如在某个特定入口临时追加一段prepare逻辑），从而把“定制”限制在调用点。

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
// 注意：实体/字段/关联在 orm.xml 中定义并生成。
// 这里展示的 Order 类可以理解为“生成的实体类（或其可扩展部分）+ 只读帮助函数”。
public class Order extends OrmEntity {
    private String id;

    // user/items 等关联由 orm.xml 定义并生成导航能力：getUser()/getItems()

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

对应的`orm.xml`模型片段（示意）：

```xml
<orm x:schema="/nop/schema/orm/orm.xdef"
         xmlns:x="/nop/schema/xdsl.xdef">
    <entities>
        <entity name="Order" tableName="mall_order">
            <columns>
                <column name="id" code="id" stdDomain="string" primary="true"/>
                <column name="status" code="status" stdDomain="string" domain="mall_order_status"/>
                <column name="totalAmount" code="total_amount" stdDomain="decimal"/>
            </columns>

            <relations>
                <to-one name="user" refEntityName="User">
                    <join>
                        <on leftProp="userId" rightProp="id"/>
                    </join>
                </to-one>

                <to-many name="items" refEntityName="OrderItem">
                    <join>
                        <on leftProp="id" rightProp="orderId"/>
                    </join>
                </to-many>
            </relations>
        </entity>
    </entities>
</orm>
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
    Order order = newEntity();
    order.setUser(user);
    order.setStatus(OrderConstants.DRAFT);
    order.setTotalAmount(BigDecimal.ZERO);

    // 3. 创建订单项并关联
    // 注意：关联集合属性一般是“始终存在且与owner绑定”的，不能用setItems(list)整体替换集合实例。
    // 只能通过 order.getItems().add/addAll 这类方式修改集合内容。
    for (CreateOrderItemRequest itemReq : request.getItems()) {
        Product product = this.requireEntity(itemReq.getProductId(), "read", context);

        OrderItem item = newEntity("OrderItem");
        item.setOrder(order);  // 设置关联
        item.setProduct(product);  // 设置关联
        item.setQuantity(itemReq.getQuantity());
        item.setPrice(product.getPrice());

        order.getItems().add(item);
    }

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

### Q0: Nop平台的DDD为什么强调“结构充血”，而不是把所有行为都放进聚合根？

**答案**：这是Nop平台在平台化、可演化、可定制场景下对DDD实践的一个关键取舍。

- **结构充血（Entity负责）**：让聚合根成为**领域语言载体**与**信息访问地图**（可导航的领域表达式），提供稳定的关联关系与只读帮助函数（领域事实）。
- **行为外置（BizModel/Flow负责）**：把**易变的业务流程/策略**上移到`BizModel`或流程编排（如`NopTaskFlow`）中，便于通过Delta机制按租户/场景覆盖与扩展。

这种拆分的目标，是避免把聚合根做成“上帝对象”，同时最大化利用平台的差量定制能力：**稳定的结构放在不可覆盖的实体上；可变的行为放在可覆盖的BizModel/流程上**。

### Q1: 如何避免加载过多数据到内存？

**答案**：**关键是控制取到内存中的数据量，而不是"用关联还是用查询"**。

```java
// ❌ 错误：两种方式都会加载所有数据到内存
// 方式1：通过懒加载关联（不使用分页）
Order order = this.requireEntity(orderId, "read", context);
List<OrderLog> logs = order.getLogs();  // 会加载所有logs，可能几千条

// 方式2：通过查询（不使用分页）
QueryBean query = new QueryBean();
query.addFilter(FilterBeans.eq("orderId", orderId));
List<OrderLog> logs = findList(query, null, context);  // 也会加载所有logs

// ✅ 正确：大数据量必须使用分页
// 方式1：使用findPage
QueryBean query = new QueryBean();
query.addFilter(FilterBeans.eq("orderId", orderId));
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
// 批量加载属于ORM能力，一般由BizModel/DAO层统一处理（不要在实体方法里做）
// 这里写成伪代码示意：
dao().batchLoadProps(orders, Arrays.asList("user"));  // 一次批量查询
```

### Q2: 如何避免N+1查询？

**答案**：使用批量加载或分页查询。

```java
// 方式1：使用批量加载
// 避免循环中查询关联属性
List<Order> orders = findList(query, null, context);
// 同上：批量加载建议在BizModel层集中处理，而不是放在实体方法里
dao().batchLoadProps(orders, List.of("user", "items"));  // 批量加载关联

// 方式2：使用分页查询（大数据量）
QueryBean query = new QueryBean();
query.setOffset(0);
query.setLimit(20);
List<Order> orders = findList(query, null, context);  // 每次只加载20条
```

### Q3: 什么时候使用doSave/doUpdate？

**答案**：大多数情况下不需要。

- **常规扩展**（补默认值、计算派生字段、统一校验、权限/过滤等）：复写`defaultPrepareSave/defaultPrepareUpdate/defaultValidate`即可
- **更底层控制**（确实要调整执行路径/prepare回调的组合方式）：才考虑`doSave/doUpdate`

```java
// 使用save()：标准保存
return save(data, context);

// 如果确实需要更精细的控制：调用doSave并传入不同的prepareCallback
// （示意：在调用点临时追加一段prepare逻辑，而不是复写defaultPrepareSave）
return doSave(entityData,
    (prepareCtx) -> {
        // 这里可以叠加/替换某些prepare逻辑
        // ...
    },
    context);
```

### Q4: 如何处理并发更新？

**答案**：使用乐观锁。

在NopORM中，乐观锁通常通过在`orm.xml`中声明版本字段（如`version`/`rev`等）来实现，更新时会把版本号带到`WHERE`条件中。

`orm.xml`示例（示意）：

```xml
<orm x:schema="/nop/schema/orm/orm.xdef"
         xmlns:x="/nop/schema/xdsl.xdef">
    <entities>
        <!-- versionProp 表示哪个属性作为版本号参与乐观锁控制 -->
        <entity name="Order" tableName="mall_order" versionProp="version">
            <columns>
                <column name="id" code="id" stdDomain="string" primary="true"/>
                <column name="version" code="version" stdDomain="int"/>
            </columns>
        </entity>
    </entities>
</orm>
```

当发生并发冲突时，框架会自动抛出异常，业务层可以决定重试或提示用户。

### Q5: 不变量（Invariants）与业务策略（Policy）应该怎么放，才不会导致“贫血模型/事务脚本”？

**答案**：区分“结构性不变量”和“可变业务策略”，并将它们放在不同层。

- **结构性不变量（少且稳定）**：例如金额不能为负、数量不能为负、状态机的基础约束等。这类约束适合放在**实体的只读语义/基础校验**与**保存/更新扩展点**（如`defaultPrepareSave`、`defaultValidate`等）中统一兜底。
- **业务策略（多且易变）**：例如VIP可透支额度、营销折扣、风控规则、跨实体协作流程等，应放在**BizModel方法**或**流程编排**中（并尽量拆成小步骤/小函数），以便定制和演化。

实践上的判断规则（与前文“实体 vs BizModel”一致）：

- 会因租户/场景变化、会调用外部服务、跨多个实体协同、或需要编排多步骤的——放BizModel/流程
- 只表达稳定领域事实、纯函数、只读、不需要定制的——可放实体

### Q6: 复杂查询会不会反过来“逼迫领域模型变形”？

**答案**：原则上不需要。Nop体系鼓励通过多道防线隔离“查询形状”对写模型/领域内核的影响。

1. **协议层按需投影**：在GraphQL场景下，字段选择（selection）可以让输出按需裁剪，避免为了接口返回而手写DTO/强行改领域模型。
2. **服务编排层隔离查询逻辑**：把跨聚合、复杂的查询封装在`BizModel`的专用方法或独立的领域服务中，而不是污染实体。
3. **读写分离（可选）**：当查询与写模型差异极大、或性能要求极高时，可以通过事件同步维护读模型，实现更彻底的CQRS分离。

### Q7: 流程编排（NopTaskFlow/步骤链）如何避免“流程脚本化/意大利面”？

**答案**：通过“步骤（Step）单一职责 + 可替换能力（Kit）+ 上下文（Context）约束”来治理。

- **每个步骤只做一件事**：要么校验一个局部约束，要么执行一次明确的状态变换；避免在单一步骤里堆大量if-else。
- **外部能力用Kit封装**：促销、库存、风控、三方接口等“易变能力”通过接口抽象隔离，步骤调用Kit而不是直接耦合实现。
- **上下文字段集中管理**：Context/黑板模式能避免参数爆炸，但要约束命名与生命周期，避免随意塞值导致不可读。

### Q8: 为什么文档强调“契约在模型中（XMeta/字典），而不是靠手写DTO/enum维持”？

**答案**：Nop更倾向把契约上移到统一模型（如`XMeta`、字典/元数据）中，运行时根据请求做无损投影和裁剪。

- 手写DTO/enum通常带来大量胶水代码和版本演化成本。
- 契约中心化后，接口形状和枚举集合更容易随模型演化，并通过差量机制做定制。

**注意**：实体字段使用`String + Constants/字典`后，类型安全主要靠**模型约束 + 运行时校验**（例如在BizModel mutation入口校验字典合法性），而不是靠Java编译期enum。

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
