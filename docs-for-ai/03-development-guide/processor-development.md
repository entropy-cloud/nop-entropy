# Processor 开发指南

Processor 是 Nop 平台中用于封装复杂业务逻辑的组件，通常用于处理超过 50 行的复杂方法、需要跨多个 BizModel 复用的逻辑，或涉及外部服务调用的场景。

## 概述

### 何时使用 Processor

| 场景 | 是否使用 Processor |
|------|-------------------|
| BizModel 方法超过 50 行 | ✅ 是 |
| 需要在多个 BizModel 间复用 | ✅ 是 |
| 涉及外部服务调用（支付、库存、风控） | ✅ 是 |
| 业务规则复杂且可能变化 | ✅ 是 |
| 简单的 CRUD 操作 | ❌ 否（直接在 BizModel 中） |
| 只读的状态查询 | ❌ 否（放在 Entity 中） |

### 架构层次

```
BizModel.method()
    └── Processor.process(request, context)    // 与 Method 一一对应
            ├── step1(request, context)          // protected 方法
            ├── step2(request, context)
            │       └── Step.execute(...)        // 子函数内调用 Step（跨 Processor 复用）
            └── step3(request, context)
```

---

## 必要 Import 列表

```java
// ===== 依赖注入 =====
import jakarta.inject.Inject;

// ===== 服务上下文 =====
import io.nop.core.context.IServiceContext;

// ===== 查询构建 =====
import io.nop.api.core.beans.query.QueryBean;
import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.api.core.beans.FilterBeans.and;

// ===== 异常处理 =====
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.ErrorCode;

// ===== Biz 接口（根据实际项目）=====
import app.mall.biz.ILitemallOrderBiz;
import app.mall.biz.ILitemallCartBiz;
import app.mall.biz.ILitemallAddressBiz;
// ...
```

---

## 完整示例

### 1. 定义 DTO

> **详细规范**: 参考 [DTO 编码规范](../04-core-components/dto-standards.md)

DTO 放在 `dao` 模块，结构概览：

```java
// app-mall-dao/src/main/java/app/mall/dao/dto/
@DataBean
public class SubmitOrderRequest implements Serializable {
    private List<String> cartIds;
    private String addressId;
    private String couponId;
    private String message;
    // getter/setter...
}

@DataBean
public class SubmitOrderResult implements Serializable {
    private String orderId;
    private boolean paid;
    // getter/setter...
}
```

### 2. 创建 Processor

```java
// app-mall-service/src/main/java/app/mall/service/processor/LitemallOrderSubmitProcessor.java
package app.mall.service.processor;

import app.mall.biz.ILitemallAddressBiz;
import app.mall.biz.ILitemallCartBiz;
import app.mall.biz.ILitemallOrderBiz;
import app.mall.dao.dto.SubmitOrderRequest;
import app.mall.dao.dto.SubmitOrderResult;
import app.mall.dao.entity.LitemallAddress;
import app.mall.dao.entity.LitemallCart;
import app.mall.dao.entity.LitemallOrder;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static io.nop.api.core.beans.FilterBeans.eq;
import static app.mall.dao.AppMallDaoConstants.ORDER_STATUS_CREATED;

public class LitemallOrderSubmitProcessor {

    // ===== 常量 =====
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("88");
    private static final BigDecimal DEFAULT_FREIGHT = new BigDecimal("10");

    // ===== 依赖注入 =====
    @Inject
    protected ILitemallAddressBiz addressBiz;

    @Inject
    protected ILitemallCartBiz cartBiz;

    @Inject
    protected ILitemallOrderBiz orderBiz;

    // ===== 错误码定义 =====
    private static final ErrorCode ERR_ADDRESS_REQUIRED =
            ErrorCode.define("nop.err.mall.order.address-required", "请选择收货地址");

    private static final ErrorCode ERR_CART_EMPTY =
            ErrorCode.define("nop.err.mall.order.cart-empty", "购物车中没有商品");

    // ===== 主入口方法 =====
    public SubmitOrderResult process(SubmitOrderRequest request, IServiceContext context) {
        String userId = context.getUserId().toString();

        // 1. 验证收货地址
        LitemallAddress address = validateAddress(request.getAddressId(), userId, context);

        // 2. 获取购物车商品
        List<LitemallCart> carts = getCarts(request.getCartIds(), userId, context);
        if (carts.isEmpty()) {
            throw new NopException(ERR_CART_EMPTY);
        }

        // 3. 计算价格
        BigDecimal goodsPrice = calculateGoodsPrice(carts);
        BigDecimal freightPrice = calculateFreight(goodsPrice);
        BigDecimal orderPrice = goodsPrice.add(freightPrice);

        // 4. 创建订单
        LitemallOrder order = createOrder(userId, address, goodsPrice, freightPrice, 
                                          orderPrice, request.getMessage(), context);

        // 5. 创建订单商品
        createOrderGoods(order, carts, context);

        // 6. 清空购物车
        clearCart(carts, context);

        // 7. 返回结果
        SubmitOrderResult result = new SubmitOrderResult();
        result.setOrderId(order.getId());
        return result;
    }

    // ===== protected 子方法（可被子类覆盖）=====
    
    protected LitemallAddress validateAddress(String addressId, String userId, 
                                               IServiceContext context) {
        if (addressId == null || addressId.isEmpty()) {
            throw new NopException(ERR_ADDRESS_REQUIRED);
        }
        LitemallAddress address = addressBiz.requireEntity(addressId, "read", context);
        if (!userId.equals(address.getUserId())) {
            throw new NopException(ERR_ADDRESS_REQUIRED).param("addressId", addressId);
        }
        return address;
    }

    protected List<LitemallCart> getCarts(List<String> cartIds, String userId, 
                                          IServiceContext context) {
        if (cartIds == null || cartIds.isEmpty()) {
            // 获取所有选中的购物车商品
            QueryBean query = new QueryBean();
            query.addFilter(eq("userId", userId));
            query.addFilter(eq("checked", true));
            return cartBiz.findList(query, null, context);
        }

        // 按指定ID获取
        List<LitemallCart> carts = new ArrayList<>();
        for (String cartId : cartIds) {
            LitemallCart cart = cartBiz.get(cartId, true, context);
            if (cart != null && userId.equals(cart.getUserId())) {
                carts.add(cart);
            }
        }
        return carts;
    }

    protected BigDecimal calculateGoodsPrice(List<LitemallCart> carts) {
        BigDecimal total = BigDecimal.ZERO;
        for (LitemallCart cart : carts) {
            BigDecimal price = cart.getPrice() != null ? cart.getPrice() : BigDecimal.ZERO;
            int number = cart.getNumber() != null ? cart.getNumber() : 0;
            total = total.add(price.multiply(new BigDecimal(number)));
        }
        return total;
    }

    protected BigDecimal calculateFreight(BigDecimal goodsPrice) {
        if (goodsPrice.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
            return BigDecimal.ZERO;
        }
        return DEFAULT_FREIGHT;
    }

    protected LitemallOrder createOrder(String userId, LitemallAddress address,
                                         BigDecimal goodsPrice, BigDecimal freightPrice,
                                         BigDecimal orderPrice, String message,
                                         IServiceContext context) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("orderSn", generateOrderSn());
        data.put("orderStatus", (short) ORDER_STATUS_CREATED);
        data.put("consignee", address.getName());
        data.put("mobile", address.getTel());
        data.put("address", buildFullAddress(address));
        data.put("message", message);
        data.put("goodsPrice", goodsPrice);
        data.put("freightPrice", freightPrice);
        data.put("orderPrice", orderPrice);
        data.put("actualPrice", orderPrice);
        data.put("addTime", LocalDateTime.now());
        data.put("deleted", false);

        return orderBiz.save(data, context);
    }

    protected String generateOrderSn() {
        String dateStr = LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = new Random().nextInt(900000) + 100000;
        return dateStr + random;
    }

    protected String buildFullAddress(LitemallAddress address) {
        StringBuilder sb = new StringBuilder();
        if (address.getProvince() != null) sb.append(address.getProvince());
        if (address.getCity() != null) sb.append(address.getCity());
        if (address.getCounty() != null) sb.append(address.getCounty());
        if (address.getAddressDetail() != null) sb.append(address.getAddressDetail());
        return sb.toString();
    }

    protected void createOrderGoods(LitemallOrder order, List<LitemallCart> carts, 
                                     IServiceContext context) {
        // 实现省略...
    }

    protected void clearCart(List<LitemallCart> carts, IServiceContext context) {
        Set<String> cartIds = new HashSet<>();
        for (LitemallCart cart : carts) {
            cartIds.add(cart.getId());
        }
        if (!cartIds.isEmpty()) {
            cartBiz.batchDelete(cartIds, context);
        }
    }
}
```

### 3. 在 BizModel 中注入 Processor

```java
@BizModel("LitemallOrder")
public class LitemallOrderBizModel extends CrudBizModel<LitemallOrder>
        implements ILitemallOrderBiz {

    @Inject
    protected LitemallOrderSubmitProcessor orderSubmitProcessor;

    public LitemallOrderBizModel() {
        setEntityName(LitemallOrder.class.getName());
    }

    @BizMutation
    public SubmitOrderResult submitOrder(@RequestBean SubmitOrderRequest request,
                                          IServiceContext context) {
        return orderSubmitProcessor.process(request, context);
    }
}
```

### 4. 配置 Bean

在 `beans.xml` 中注册 Processor：

```xml
<!-- _service.beans.xml 或 app-service.beans.xml -->
<beans>
    <!-- 其他 Bean 定义... -->

    <!-- Processor Bean -->
    <bean id="litemallOrderSubmitProcessor"
          class="app.mall.service.processor.LitemallOrderSubmitProcessor"/>
</beans>
```

**Bean ID 命名规范**：
- 使用首字母小写的类名：`LitemallOrderSubmitProcessor` → `litemallOrderSubmitProcessor`
- 或者使用功能描述：`orderSubmitProcessor`

---

## Processor vs BizModel 方法对比

| 特性 | BizModel 方法 | Processor |
|------|--------------|-----------|
| 事务 | `@BizMutation` 自动开启 | 由调用方 BizModel 控制 |
| 数据访问 | 使用 `doFindList`、`requireEntity` | 通过 Biz 接口：`biz.findList`、`biz.requireEntity` |
| 可定制性 | 通过 Delta/xbiz | 通过继承覆盖 protected 方法 |
| 复用性 | 仅在单个 BizModel 内 | 可注入到多个 BizModel |

---

## Step 的使用

当某个步骤需要在**多个 Processor** 间复用时，抽象为 Step：

```java
// Step - 单一场景，跨 Processor 复用
public class InventoryDeductStep {

    @Inject
    protected ILitemallGoodsProductBiz productBiz;

    public void execute(List<LitemallOrderGoods> orderGoods, IServiceContext context) {
        for (LitemallOrderGoods item : orderGoods) {
            deductForItem(item, context);
        }
    }

    protected void deductForItem(LitemallOrderGoods item, IServiceContext context) {
        LitemallGoodsProduct product = productBiz.get(item.getProductId(), false, context);
        if (product != null && item.getNumber() != null) {
            product.setNumber(product.getNumber() - item.getNumber().intValue());
            productBiz.updateEntity(product, null, context);
        }
    }
}
```

在 Processor 中通过 protected 方法调用 Step：

```java
public class LitemallOrderSubmitProcessor {

    @Inject
    protected InventoryDeductStep inventoryDeductStep;

    public SubmitOrderResult process(SubmitOrderRequest request, IServiceContext context) {
        // ...
        deductInventory(orderGoods, context);  // ✅ 通过子函数调用 Step
        // ...
    }

    // ✅ 正确：通过 protected 方法调用 Step
    protected void deductInventory(List<LitemallOrderGoods> orderGoods, 
                                    IServiceContext context) {
        inventoryDeductStep.execute(orderGoods, context);
    }
}
```

---

## 命名规范

| 组件 | 命名格式 | 示例 |
|------|---------|------|
| Processor | `{EntityName}{MethodName}Processor` | `LitemallOrderSubmitProcessor` |
| Step | `{功能域}{场景}Step` | `InventoryDeductStep`、`InventoryRestoreStep` |
| Request DTO | `{MethodName}Request` | `SubmitOrderRequest` |
| Result DTO | `{MethodName}Result` | `SubmitOrderResult` |

**❌ 错误示例**：
- `PaymentProcessor`（功能域，场景不明确）
- `InventoryStep`（多种操作混在一起）

**✅ 正确示例**：
- `LitemallOrderPayProcessor`（明确的业务场景）
- `InventoryDeductStep`、`InventoryRestoreStep`（单一职责）

---

## 常见错误

### 1. 在 Processor 中使用 `doFindList`

```java
// ❌ 错误：doFindList 是 protected 方法，无法通过接口调用
List<LitemallCart> carts = cartBiz.doFindList(query, null, context);

// ✅ 正确：使用 findList
List<LitemallCart> carts = cartBiz.findList(query, null, context);
```

### 2. 直接使用 `dao().xxx()`

```java
// ❌ 错误：绕过数据权限
LitemallCart cart = dao().getEntityById(cartId);

// ✅ 正确：通过 Biz 接口
LitemallCart cart = cartBiz.get(cartId, true, context);
```

### 3. 忘记注册 Bean

```xml
<!-- ❌ 错误：Processor 没有注册 -->
<!-- 编译通过，但运行时 @Inject 注入为 null -->

<!-- ✅ 正确：在 beans.xml 中注册 -->
<bean id="orderSubmitProcessor"
      class="app.mall.service.processor.OrderSubmitProcessor"/>
```

---

## 相关文档

- [BizModel 编写指南](./bizmodel-guide.md)
- [DTO 编码规范](../04-core-components/dto-standards.md)
- [ErrorCode 定义规范](../04-core-components/error-codes.md)
