# BizModel 编写指南

## 概述

BizModel 是 Nop 平台中业务逻辑的核心载体。本文档详细说明 BizModel 的编写规范、参数/返回类型约定、数据访问方式以及复杂逻辑的拆分策略。

## 基本结构

### 最简 BizModel

```java
@BizModel("LitemallCart")
public class LitemallCartBizModel extends CrudBizModel<LitemallCart> 
        implements ILitemallCartBiz {
    
    public LitemallCartBizModel() {
        setEntityName(LitemallCart.class.getName());
    }
    
    // CrudBizModel 已提供：findPage, get, save, update, delete 等内置方法
    // 无需重复实现简单 CRUD
}
```

### 接口定义

只有当方法需要被**其他 BizModel 调用**时，才需要在接口中定义：

```java
// 接口定义在 dao 模块
public interface ILitemallCartBiz extends ICrudBiz<LitemallCart> {
    
    // ✅ 只有被其他 BizModel 调用的方法才需要在接口中定义
    // 例如：OrderBizModel 需要调用 clearCart
    void clearCart(IServiceContext context);
    
    CartSummary getCartSummary(IServiceContext context);
    
    // ❌ 不需要把所有 BizModel 方法都放在接口中
    // checkout、updateQuantity 等如果只通过 GraphQL/REST 调用，
    // 直接在 BizModel 类中定义即可
}
```

**规则**：
- **需要跨 BizModel 调用** → 在接口中定义
- **只通过 GraphQL/REST 调用** → 直接在 BizModel 类中定义，无需接口方法

### BizQuery 和 BizMutation 注解

**无论方法是在接口还是 BizModel 类中定义**，只要需要通过 REST/GraphQL 调用，都必须添加 `@BizQuery` 或 `@BizMutation` 注解：

```java
@BizModel("LitemallCart")
public class LitemallCartBizModel extends CrudBizModel<LitemallCart> 
        implements ILitemallCartBiz {
    
    // ✅ 查询操作：使用 @BizQuery
    @BizQuery
    public CartCheckoutResult checkout(@Name("cartIds") List<String> cartIds,
                                       IServiceContext context) {
        // ...
    }
    
    // ✅ 修改操作：使用 @BizMutation
    @BizMutation
    public LitemallCart updateQuantity(@Name("cartId") String cartId,
                                       @Name("number") Integer number,
                                       IServiceContext context) {
        // ...
    }
    
    // ✅ 实现接口方法（被其他 BizModel 调用），也需要注解才能被 GraphQL 调用
    @Override
    @BizMutation
    public void clearCart(IServiceContext context) {
        // ...
    }
    
    // ❌ 没有 @BizQuery/@BizMutation 的方法不能通过 GraphQL/REST 调用
    // 只能被 Java 代码直接调用
    public void internalHelper() {
        // ...
    }
}
```

### DTO 定义位置

在 IXXBiz 接口中用到的 DTO 类，直接在 **dao 模块**中定义：

```
app-mall-dao/
└── src/main/java/app/mall/dao/
    ├── biz/                    # Biz 接口
    │   └── ILitemallCartBiz.java
    ├── dto/                    # DTO 类（@DataBean）
    │   ├── CartCheckoutResult.java
    │   └── CartSummary.java
    └── entity/                 # 实体类
        └── LitemallCart.java
```

## 参数类型规范

### 简单参数

使用 `@Name` 注解标记参数名：

```java
@BizMutation
public LitemallCart updateQuantity(@Name("cartId") String cartId,
                                   @Name("number") Integer number,
                                   IServiceContext context) {
    // ...
}
```

### 复杂参数

对于复杂参数，应定义专门的 Request 类（使用 `@DataBean` 注解）：

```java
@DataBean
public class CreateOrderRequest implements Serializable {
    private String userId;
    private List<OrderItemRequest> items;
    private String remark;
    // getters/setters
}

@DataBean
public class OrderItemRequest implements Serializable {
    private String productId;
    private Integer quantity;
    // getters/setters
}

@BizMutation
public Order createOrder(@Name("request") CreateOrderRequest request,
                         IServiceContext context) {
    // ...
}
```

### CRUD 内置方法

`save()`、`update()` 等 CRUD 内置方法使用 `Map<String, Object>` 作为参数，配合 XMeta 进行校验：

```java
// 这是内置方法，无需手动实现
// 调用方式：LitemallCart__save(data: { goodsId: "1", number: 2 })
// XMeta 会自动校验字段类型和约束
```

## 返回类型规范

### 返回实体

修改操作通常返回实体本身：

```java
@BizMutation
public Order cancel(@Name("orderId") String orderId, IServiceContext context) {
    Order order = requireEntity(orderId, "update", context);
    order.setStatus(OrderConstants.CANCELLED);
    return update(order, context);  // 返回 Order
}
```

### 返回自定义 DTO

对于需要返回多个字段的场景，定义 `@DataBean` 注解的 DTO：

```java
@DataBean
public class CartCheckoutResult implements Serializable {
    private List<LitemallCart> cartGoods;
    private BigDecimal goodsPrice;
    private BigDecimal freightPrice;
    private BigDecimal orderPrice;
    // getters/setters
}

@BizQuery
public CartCheckoutResult checkout(@Name("cartIds") List<String> cartIds,
                                   IServiceContext context) {
    // ...
    CartCheckoutResult result = new CartCheckoutResult();
    result.setCartGoods(carts);
    result.setGoodsPrice(goodsPrice);
    result.setFreightPrice(freightPrice);
    result.setOrderPrice(orderPrice);
    return result;
}
```

### ❌ 避免：使用 Map 作为返回类型

```java
// ❌ 错误：不要用 Map 作为返回类型
@BizQuery
public Map<String, Object> checkout(...) {
    Map<String, Object> result = new HashMap<>();
    result.put("cartGoods", carts);
    // GraphQL 无法正确推断 Map 的字段类型
    return result;
}
```

## 数据访问规范

### 实体获取

| 方法 | 用途 | 数据权限 |
|------|------|---------|
| `requireEntity(id, action, context)` | 获取实体，不存在则抛异常 | ✅ 自动检查 |
| `getEntity(id, action, context)` | 获取实体，不存在返回 null | ✅ 自动检查 |
| `get(id, ignoreUnknown, context)` | 简单获取 | ✅ 自动检查 |
| `batchGet(ids, ignoreUnknown, context)` | 批量获取 | ✅ 自动检查 |

```java
// ✅ 推荐：使用 CrudBizModel 方法
Order order = requireEntity(orderId, "update", context);

// ❌ 避免：直接使用 dao()
Order order = dao().getEntityById(orderId);  // 绕过数据权限检查
```

### 查询列表

```java
// ✅ 推荐：使用 doFindList
QueryBean query = new QueryBean();
query.addFilter(FilterBeans.eq("userId", userId));
List<LitemallCart> carts = doFindList(query, null, null, context);

// ❌ 避免：直接使用 dao()
List<LitemallCart> carts = dao().findListByQuery(query);  // 绕过数据权限检查
```

### 保存/更新

```java
// 场景1：新创建实体，使用 Map 数据 + save()
Map<String, Object> data = new HashMap<>();
data.put("userId", userId);
data.put("goodsId", goodsId);
return save(data, context);

// 场景2：通过 requireEntity 获取实体，属性已业务校验 → 直接用 updateEntity()
Order order = requireEntity(orderId, "update", context);
order.setStatus(OrderConstants.CANCELLED);
order.setCancelTime(LocalDateTime.now());
updateEntity(order, null, context);  // ✅ 推荐：实体已存在且属性已校验
return order;

// 场景3：通过 Map 接收前端数据 → 使用 update(map)
Map<String, Object> data = new HashMap<>();
data.put("id", cartId);
data.put("number", number.shortValue());
return update(data, context);  // ✅ 推荐：前端传入数据，需要框架校验

// ❌ 避免：直接使用 dao()
dao().updateEntity(order);  // 绕过数据权限检查、验证、回调
```

**选择 updateEntity 还是 update(map)**：

| 场景 | 推荐方法 | 原因 |
|------|---------|------|
| 实体已通过 `requireEntity` 获取 | `updateEntity(entity, null, context)` | 避免不必要的 Map 转换 |
| 属性已在业务代码中校验 | `updateEntity(entity, null, context)` | 无需重复校验 |
| 前端传入 Map 数据 | `update(data, context)` | 需要 XMeta 校验和类型转换 |
| 需要触发 defaultPrepareUpdate | `update(data, context)` | 框架自动处理扩展点 |

**action 参数说明**：

`updateEntity(entity, action, context)` 的 `action` 参数用于区分不同的业务场景：

```java
// ✅ action 为 null 时，默认使用 "update"
updateEntity(order, null, context);

// ✅ 指定 action，用于区分不同的业务场景（如应用不同的数据权限）
updateEntity(order, "approve", context);  // 审核场景
updateEntity(order, "publish", context);  // 发布场景
```

**注意**：`updateEntity` 返回 `void`，不返回实体对象。

### 批量操作

```java
// ✅ 批量更新
Map<String, Object> data = new HashMap<>();
data.put("checked", true);
updateByQuery(query, data, context);

// ✅ 批量删除
batchDelete(ids, context);
```

## 常量定义规范

### 数据库字段枚举常量：通过 orm.xml + codegen 生成

对于数据库字段的枚举值（如订单状态、优惠券类型等），应该通过 orm.xml 的 dict 定义，由 codegen 自动生成常量：

**1. 在 orm.xml 中定义 dict：**

```xml
<dicts>
    <dict name="mall/order-status" valueType="int">
        <option code="CREATED" label="未付款" value="101"/>
        <option code="CANCEL" label="已取消" value="102"/>
        <option code="PAY" label="已付款" value="201"/>
        <option code="SHIP" label="已发货" value="301"/>
    </dict>
    
    <dict name="mall/coupon-type" valueType="int">
        <option code="COMMON" label="通用券" value="0"/>
        <option code="REGISTER" label="注册赠券" value="1"/>
        <option code="CODE" label="兑换码" value="2"/>
    </dict>
</dicts>

<entity name="LitemallOrder">
    <columns>
        <column name="orderStatus" ext:dict="mall/order-status"/>
    </columns>
</entity>
```

**2. 运行 codegen 自动生成常量：**

```bash
cd xxx-codegen && mvn install
```

**3. 生成的常量类（`_AppMallDaoConstants.java`）：**

```java
public interface _AppMallDaoConstants {
    int ORDER_STATUS_CREATED = 101;
    int ORDER_STATUS_CANCEL = 102;
    int ORDER_STATUS_PAY = 201;
    int ORDER_STATUS_SHIP = 301;
    
    int COUPON_TYPE_COMMON = 0;
    int COUPON_TYPE_REGISTER = 1;
    int COUPON_TYPE_CODE = 2;
}
```

**4. 在代码中使用：**

```java
import static app.mall.dao.AppMallDaoConstants.*;

// ✅ 使用生成的常量
if (order.getOrderStatus() == ORDER_STATUS_PAY) {
    // 已付款，可以发货
}
```

### 其他业务常量：手动定义

对于与数据库字段无关的业务常量，可以在专门的常量类中手动定义：

```java
// AppMallConstants.java - 非数据库字段相关的业务常量
public interface AppMallConstants {
    /** 免运费最低金额 */
    BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("88");
    
    /** 默认运费 */
    BigDecimal DEFAULT_FREIGHT = new BigDecimal("10");
    
    /** 订单超时时间（分钟） */
    int ORDER_TIMEOUT_MINUTES = 30;
    
    /** 购物车商品数量上限 */
    int CART_MAX_ITEMS = 99;
}
```

### ❌ 错误做法

```java
// ❌ 不要在 Entity 中手动定义数据库字段枚举常量
public class LitemallCoupon extends _LitemallCoupon {
    public static final int STATUS_NORMAL = 0;  // 应该由 codegen 生成
    public static final int STATUS_EXPIRED = 1;
}

// ❌ 不要在 Entity 中定义业务常量
public class LitemallOrder extends _LitemallOrder {
    public static final BigDecimal FREE_SHIPPING = new BigDecimal("88");  // 应该放在专门的常量类中
}
```

### 总结

| 常量类型 | 定义位置 | 示例 |
|---------|---------|------|
| 数据库字段枚举 | orm.xml dict + codegen | 订单状态、优惠券类型、售后状态 |
| 业务规则常量 | 手动定义的常量类 | 免运费金额、超时时间、数量限制 |

## 事务管理

### 自动事务

`@BizMutation` 方法自动开启事务，**无需额外使用** `@Transactional`：

```java
// ✅ 正确：无需手动开启事务
@BizMutation
public Order cancel(@Name("orderId") String orderId, IServiceContext context) {
    Order order = requireEntity(orderId, "update", context);
    order.setStatus(OrderConstants.CANCELLED);
    return update(order, context);
    // 框架自动在方法执行前开启事务，执行后提交事务
}
```

### 事务后回调

需要在事务提交后执行的操作（如发送通知）：

```java
@BizMutation
public Order ship(@Name("orderId") String orderId, IServiceContext context) {
    Order order = requireEntity(orderId, "update", context);
    order.setStatus(OrderConstants.SHIPPED);
    order = update(order, context);
    
    // 事务提交后执行
    Order finalOrder = order;
    txn().afterCommit(null, () -> {
        sendShippingNotification(finalOrder);
    });
    
    return order;
}
```

## 跨聚合访问

当需要访问其他聚合根时，应通过 BizModel 接口而非直接使用 `daoProvider()`：

### ❌ 错误方式

```java
// 直接使用 daoProvider() 绕过了数据权限检查
LitemallGoods goods = daoProvider().daoFor(LitemallGoods.class)
        .getEntityById(goodsId);
```

### ✅ 正确方式

```java
@BizModel("LitemallCart")
public class LitemallCartBizModel extends CrudBizModel<LitemallCart> {
    
    @Inject
    @Named("biz_LitemallGoods")
    protected ILitemallGoodsBiz goodsBiz;
    
    @Inject
    @Named("biz_LitemallGoodsProduct")
    protected ILitemallGoodsProductBiz productBiz;
    
    @BizMutation
    public LitemallCart addToCart(@Name("goodsId") String goodsId,
                                  @Name("productId") String productId,
                                  @Name("number") Integer number,
                                  IServiceContext context) {
        // 通过 BizModel 接口访问其他聚合，确保数据权限检查
        LitemallGoods goods = goodsBiz.requireEntity(goodsId, "read", context);
        LitemallGoodsProduct product = productBiz.requireEntity(productId, "read", context);
        // ...
    }
}
```

## 复杂逻辑拆分：Processor

### 何时拆分

当 BizModel 方法出现以下情况时，应考虑拆分 Processor：
1. 单个方法超过 50 行
2. 需要在多个 BizModel 间复用
3. 涉及外部服务调用（支付、库存、风控等）
4. 业务规则复杂且可能变化

### Processor 定义

```java
// PaymentProcessor.java
public class PaymentProcessor {
    
    @Inject
    PayService payService;
    
    @Inject
    MallLogManager logManager;
    
    /**
     * 处理退款
     */
    public void processRefund(LitemallAftersale aftersale, IServiceContext context) {
        LitemallOrder order = aftersale.getOrder();
        
        // 1. 调用支付服务
        PayRefundRequestBean request = new PayRefundRequestBean();
        request.setOutTradeNo(order.getOrderSn());
        request.setOutRefundNo("refund_" + order.getOrderSn());
        request.setTotalFee(order.getActualPrice());
        request.setRefundFee(aftersale.getAmount());
        
        payService.refund(ApiRequest.build(request)).get();
        
        // 2. 更新状态
        aftersale.setStatus((short) AFTERSALE_STATUS_REFUND);
        aftersale.setHandleTime(DateHelper.currentDateTime());
        
        // 3. 记录日志
        logManager.logOrderSucceed("退款", 
            "订单编号 " + order.getOrderSn() + " 售后编号 " + aftersale.getAftersaleSn());
    }
}
```

### Processor 配置

在 `beans.xml` 中配置：

```xml
<!-- app-service.beans.xml -->
<beans>
    <bean id="paymentProcessor" class="app.mall.service.processor.PaymentProcessor" />
</beans>
```

### 在 BizModel 中使用

```java
@BizModel("LitemallAftersale")
public class LitemallAftersaleBizModel extends CrudBizModel<LitemallAftersale> {
    
    @Inject
    PaymentProcessor paymentProcessor;
    
    @BizMutation
    public void refund(@Name("id") String id, IServiceContext context) {
        LitemallAftersale entity = get(id, false, context);
        
        if (entity.getStatus() != AFTERSALE_STATUS_APPROVED) {
            throw new NopException(ERR_AFTERSALE_NOT_ALLOW_REFUND);
        }
        
        // 委托给 Processor 处理复杂逻辑
        paymentProcessor.processRefund(entity, context);
    }
}
```

## 完整示例

### 购物车 BizModel

```java
@BizModel("LitemallCart")
public class LitemallCartBizModel extends CrudBizModel<LitemallCart> 
        implements ILitemallCartBiz {
    
    @Inject
    @Named("biz_LitemallGoods")
    protected ILitemallGoodsBiz goodsBiz;
    
    @Inject
    @Named("biz_LitemallGoodsProduct")
    protected ILitemallGoodsProductBiz productBiz;
    
    public LitemallCartBizModel() {
        setEntityName(LitemallCart.class.getName());
    }
    
    private String requireUserId(IServiceContext context) {
        Object userIdObj = context.getUserId();
        if (userIdObj == null) {
            throw newError(AppMallErrors.ERR_CART_NOT_OWNER)
                    .param("reason", "User not logged in");
        }
        return userIdObj.toString();
    }
    
    @BizMutation
    public LitemallCart updateQuantity(@Name("cartId") String cartId,
                                       @Name("number") Integer number,
                                       IServiceContext context) {
        // 1. 验证
        if (number == null || number < 1 || number > 999) {
            throw newError(AppMallErrors.ERR_CART_QUANTITY_INVALID)
                    .param("min", 1)
                    .param("max", 999);
        }
        
        // 2. 获取并验证所有权
        String userId = requireUserId(context);
        LitemallCart cart = requireEntity(cartId, "update", context);
        
        if (!userId.equals(cart.getUserId())) {
            throw newError(AppMallErrors.ERR_CART_NOT_OWNER)
                    .param("cartId", cartId);
        }
        
        // 3. 检查库存
        if (number > cart.getNumber()) {
            LitemallGoodsProduct product = productBiz.get(cart.getProductId(), false, context);
            if (product != null && number > product.getNumber()) {
                throw newError(AppMallErrors.ERR_CART_STOCK_INSUFFICIENT)
                        .param("goodsName", cart.getGoodsName())
                        .param("available", product.getNumber());
            }
        }
        
        // 4. 更新
        Map<String, Object> data = new HashMap<>();
        data.put("id", cartId);
        data.put("number", number.shortValue());
        return update(data, context);
    }
    
    @BizQuery
    public CartCheckoutResult checkout(@Name("cartIds") List<String> cartIds,
                                       IServiceContext context) {
        String userId = requireUserId(context);
        
        // 1. 获取购物车项
        List<LitemallCart> carts;
        if (cartIds == null || cartIds.isEmpty()) {
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq("userId", userId));
            query.addFilter(FilterBeans.eq("checked", true));
            carts = doFindList(query, null, null, context);
        } else {
            carts = new ArrayList<>();
            for (LitemallCart cart : batchGet(cartIds, false, context)) {
                if (userId.equals(cart.getUserId())) {
                    carts.add(cart);
                }
            }
        }
        
        if (carts.isEmpty()) {
            throw newError(AppMallErrors.ERR_CART_EMPTY);
        }
        
        // 2. 计算价格
        BigDecimal goodsPrice = BigDecimal.ZERO;
        for (LitemallCart cart : carts) {
            if (cart.getNumber() != null) {
                goodsPrice = goodsPrice.add(
                    cart.getPrice().multiply(new BigDecimal(cart.getNumber())));
            }
        }
        
        BigDecimal freightPrice = goodsPrice.compareTo(new BigDecimal("88")) < 0 
            ? new BigDecimal("10") 
            : BigDecimal.ZERO;
        
        // 3. 返回结果
        CartCheckoutResult result = new CartCheckoutResult();
        result.setCartGoods(carts);
        result.setGoodsPrice(goodsPrice);
        result.setFreightPrice(freightPrice);
        result.setOrderPrice(goodsPrice.add(freightPrice));
        return result;
    }
    
    @BizMutation
    public void clearCart(IServiceContext context) {
        String userId = requireUserId(context);
        
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("userId", userId));
        
        Map<String, Object> data = new HashMap<>();
        data.put("deleted", true);
        updateByQuery(query, data, context);
    }
}
```

## 常见错误

### 1. 直接调用 dao() 绕过数据权限

```java
// ❌ 错误
LitemallCart cart = dao().getEntityById(cartId);

// ✅ 正确
LitemallCart cart = requireEntity(cartId, "update", context);
```

### 2. 使用 @Transactional 多余

```java
// ❌ 错误：@BizMutation 已自动开启事务
@BizMutation
@Transactional
public Order cancel(...) { }

// ✅ 正确
@BizMutation
public Order cancel(...) { }
```

### 3. 使用 Map 作为返回类型

```java
// ❌ 错误
public Map<String, Object> checkout(...) { }

// ✅ 正确
public CartCheckoutResult checkout(...) { }
```

### 4. 跨聚合直接访问 dao

```java
// ❌ 错误
LitemallGoods goods = daoProvider().daoFor(LitemallGoods.class).getEntityById(id);

// ✅ 正确
@Inject @Named("biz_LitemallGoods")
ILitemallGoodsBiz goodsBiz;

LitemallGoods goods = goodsBiz.requireEntity(id, "read", context);
```

### 5. 参数类型与实体不匹配

```java
// ❌ 错误：实体 ID 是 String，参数却是 Integer
public LitemallCart updateQuantity(@Name("cartId") Integer cartId, ...)

// ✅ 正确
public LitemallCart updateQuantity(@Name("cartId") String cartId, ...)
```

## 相关文档

- [DDD 在 Nop 中的实践](./ddd-in-nop.md)
- [服务层开发指南](./service-layer.md)
- [CRUD 开发指南](./crud-development.md)
- [DTO 编码规范](../04-core-components/enum-dto-standards.md)
- [完整示例](../08-examples/crud-example.md)
