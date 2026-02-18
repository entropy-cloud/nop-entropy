# DTO 编码规范

DTO (Data Transfer Object) 用于在 BizModel/Processor 与前端之间传递复杂数据结构。

## 概述

### 何时使用 DTO

| 场景 | 是否使用 DTO |
|------|-------------|
| 方法参数超过 3 个 | ✅ 是，使用 `@RequestBean` |
| 需要返回多个字段 | ✅ 是，定义 Result DTO |
| 参数结构复杂（嵌套、列表） | ✅ 是 |
| 简单的单参数方法 | ❌ 否，直接使用 `@Name` |
| 返回单个实体 | ❌ 否，直接返回实体 |

### DTO 位置

```
app-mall-dao/src/main/java/app/mall/dao/
├── biz/                    # Biz 接口 (ILitemallOrderBiz.java)
├── dto/                    # DTO 类
│   ├── SubmitOrderRequest.java
│   └── SubmitOrderResult.java
└── entity/                 # 实体类 (LitemallOrder.java)
```

**原则**：DTO 放在 `dao` 模块，因为 Processor 和 BizModel 都需要访问。

---

## 基本结构

### Request DTO

```java
package app.mall.dao.dto;

import io.nop.api.core.annotations.data.DataBean;
import java.io.Serializable;
import java.util.List;

/**
 * 订单提交请求
 */
@DataBean
public class SubmitOrderRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 购物车ID列表，空表示全部选中
     */
    private List<String> cartIds;

    /**
     * 收货地址ID
     */
    private String addressId;

    /**
     * 优惠券ID
     */
    private String couponId;

    /**
     * 用户优惠券ID
     */
    private String userCouponId;

    /**
     * 订单留言
     */
    private String message;

    // ===== Getter/Setter =====
    
    public List<String> getCartIds() {
        return cartIds;
    }

    public void setCartIds(List<String> cartIds) {
        this.cartIds = cartIds;
    }

    public String getAddressId() {
        return addressId;
    }

    public void setAddressId(String addressId) {
        this.addressId = addressId;
    }

    // ... 其他 getter/setter
}
```

### Result DTO

```java
package app.mall.dao.dto;

import io.nop.api.core.annotations.data.DataBean;
import java.io.Serializable;

/**
 * 订单提交结果
 */
@DataBean
public class SubmitOrderResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单ID
     */
    private String orderId;

    /**
     * 是否已支付（订单金额为0时直接跳过支付）
     */
    private boolean paid;

    /**
     * 团购链接ID（参与团购时返回）
     */
    private String grouponLinkId;

    // ===== Getter/Setter =====

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public String getGrouponLinkId() {
        return grouponLinkId;
    }

    public void setGrouponLinkId(String grouponLinkId) {
        this.grouponLinkId = grouponLinkId;
    }
}
```

---

## 必要规范

### 1. 必须添加 `@DataBean` 注解

```java
// ✅ 正确
@DataBean
public class SubmitOrderRequest implements Serializable { ... }

// ❌ 错误：缺少注解
public class SubmitOrderRequest implements Serializable { ... }
```

### 2. 必须实现 `Serializable`

```java
// ✅ 正确
@DataBean
public class SubmitOrderRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    // ...
}

// ❌ 错误：未实现 Serializable
@DataBean
public class SubmitOrderRequest { ... }
```

### 3. 添加 `serialVersionUID`

```java
// ✅ 正确
private static final long serialVersionUID = 1L;

// ❌ 错误：缺少 serialVersionUID
// （编译器会警告，运行时可能出问题）
```

### 4. 提供 Getter/Setter

```java
// ✅ 正确：标准 getter/setter
private String orderId;

public String getOrderId() {
    return orderId;
}

public void setOrderId(String orderId) {
    this.orderId = orderId;
}

// ❌ 错误：使用 Lombok @Data（与 Nop 序列化机制可能冲突）
@Data
public class SubmitOrderRequest { ... }
```

---

## 复杂类型

### 嵌套 DTO

```java
@DataBean
public class SubmitOrderRequest implements Serializable {

    private String addressId;
    
    /**
     * 订单商品列表
     */
    private List<OrderItemRequest> items;

    // getter/setter...
}

/**
 * 订单商品请求
 */
@DataBean
public class OrderItemRequest implements Serializable {

    private String goodsId;
    private String productId;
    private Integer quantity;

    // getter/setter...
}
```

### 枚举字段

```java
@DataBean
public class QueryOrderRequest implements Serializable {

    /**
     * 订单状态（可选）
     */
    private OrderStatus status;

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}

public enum OrderStatus {
    CREATED(101),
    PAID(201),
    SHIPPED(301);

    private final int code;

    OrderStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
```

### BigDecimal 字段

```java
@DataBean
public class PriceInfo implements Serializable {

    /**
     * 商品总价
     */
    private BigDecimal goodsPrice;

    /**
     * 运费
     */
    private BigDecimal freightPrice;

    /**
     * 订单总价
     */
    private BigDecimal orderPrice;

    // getter/setter...
}
```

---

## 使用方式

### 在 BizModel 中使用

```java
@BizModel("LitemallOrder")
public class LitemallOrderBizModel extends CrudBizModel<LitemallOrder> {

    /**
     * 提交订单
     * 
     * @param request 订单提交请求
     * @param context 服务上下文
     * @return 订单提交结果
     */
    @BizMutation
    public SubmitOrderResult submitOrder(
            @RequestBean SubmitOrderRequest request,
            IServiceContext context) {
        
        // 访问请求字段
        List<String> cartIds = request.getCartIds();
        String addressId = request.getAddressId();
        
        // 构建结果
        SubmitOrderResult result = new SubmitOrderResult();
        result.setOrderId(order.getId());
        result.setPaid(false);
        return result;
    }
}
```

### GraphQL 调用

```graphql
mutation {
  LitemallOrder__submitOrder(request: {
    addressId: "123"
    cartIds: ["1", "2"]
    couponId: "456"
    message: "请尽快发货"
  }) {
    orderId
    paid
    grouponLinkId
  }
}
```

---

## 命名规范

| 类型 | 命名格式 | 示例 |
|------|---------|------|
| Request DTO | `{Action}Request` | `SubmitOrderRequest`、`UpdateCartRequest` |
| Result DTO | `{Action}Result` | `SubmitOrderResult`、`CheckoutResult` |
| 嵌套 DTO | `{Action}{Item}Request` | `SubmitOrderItemRequest` |
| 查询 DTO | `{Entity}QueryRequest` | `OrderQueryRequest` |

---

## 与 Entity 的区别

| 特性 | Entity | DTO |
|------|--------|-----|
| 位置 | `dao/entity/` | `dao/dto/` |
| 映射 | 数据库表 | 无 |
| 注解 | `@BizObjName` | `@DataBean` |
| 用途 | 数据持久化 | 数据传输 |
| 字段 | 与数据库一致 | 按需定义 |

---

## 常见错误

### 1. DTO 放错位置

```java
// ❌ 错误：放在 service 模块（Processor 和其他 BizModel 无法访问）
// app-mall-service/.../dto/SubmitOrderRequest.java

// ✅ 正确：放在 dao 模块
// app-mall-dao/.../dto/SubmitOrderRequest.java
```

### 2. 使用 Map 替代 DTO

```java
// ❌ 错误：使用 Map，GraphQL 无法推断类型
@BizMutation
public Map<String, Object> submitOrder(@RequestBean Map<String, Object> request,
                                        IServiceContext context) { ... }

// ✅ 正确：使用强类型 DTO
@BizMutation
public SubmitOrderResult submitOrder(@RequestBean SubmitOrderRequest request,
                                      IServiceContext context) { ... }
```

### 3. DTO 字段过多

```java
// ❌ 错误：一个 DTO 包含所有可能的字段
@DataBean
public class OrderRequest {
    // 提交订单字段
    private String addressId;
    // 取消订单字段
    private String cancelReason;
    // 支付字段
    private String paymentMethod;
    // ...
}

// ✅ 正确：按场景拆分 DTO
@DataBean
public class SubmitOrderRequest {
    private String addressId;
    // 仅包含提交订单相关字段
}

@DataBean
public class CancelOrderRequest {
    private String cancelReason;
    // 仅包含取消订单相关字段
}
```

---

## 相关文档

- [BizModel 编写指南](./bizmodel-guide.md)
- [Processor 开发指南](./processor-development.md)
