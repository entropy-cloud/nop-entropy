# BizModel 编写指南

BizModel 是 Nop 平台业务逻辑的核心载体。本文档规定编写规范、参数/返回类型约定、数据访问方式及复杂逻辑拆分策略。

## 📦 必要 Import 列表

以下是 BizModel 开发中最常用的 import，建议在开发时优先添加：

```java
// ===== 核心注解 =====
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Name;

// ===== 服务上下文 =====
import io.nop.core.context.IServiceContext;

// ===== 依赖注入（二选一）=====
import jakarta.inject.Inject;  // ✅ 推荐（与 Spring/Jakarta 标准一致）
// import io.nop.api.core.annotations.inject.Inject;  // 也可用

// ===== CRUD 基类 =====
import io.nop.biz.crud.CrudBizModel;

// ===== 查询构建 =====
import io.nop.api.core.beans.query.QueryBean;
import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.api.core.beans.FilterBeans.and;
import static io.nop.api.core.beans.FilterBeans.or;

// ===== 异常处理 =====
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.ErrorCode;

// ===== DTO 标记 =====
import io.nop.api.core.annotations.data.DataBean;

// ===== 字段选择（GraphQL）=====
import io.nop.api.core.beans.FieldSelectionBean;
```

### Processor 中额外的 Import

```java
// Processor 需要通过 Biz 接口访问其他实体
import app.mall.biz.ILitemallOrderBiz;     // 替换为实际的 Biz 接口
import app.mall.biz.ILitemallCartBiz;
```

---

## ⚠️ 必须规则（强制要求）

### IXXBiz 和 BizModel 方法规范

以下规则适用于所有 `IXXBiz` 接口和 `BizModel` 类中的业务方法：

#### 1. 所有非 private 方法必须具有以下注解之一

| 注解 | 用途 | 事务 |
|------|------|------|
| `@BizQuery` | 查询操作（只读） | 无事务 |
| `@BizMutation` | 修改操作（新增/更新/删除） | 自动开启事务 |
| `@BizAction` | 动作方法（通用操作） | 自动开启事务 |

> **说明**：仅内部调用的辅助方法应标记为 `private`，不需要注解。

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    // ✅ 正确：public 方法有注解
    @BizQuery
    public Order getOrder(@Name("orderId") String orderId, IServiceContext context) { ... }

    @BizMutation
    public Order cancel(@Name("orderId") String orderId, IServiceContext context) { ... }

    // ✅ 正确：private 辅助方法不需要注解
    private void validateOrder(Order order) { ... }

    // ❌ 错误：public 方法缺少注解
    public void helperMethod(...) { ... }
}
```

#### 2. 最后一个参数必须是 `IServiceContext`

所有对外暴露的业务方法（带 BizQuery/BizMutation/BizAction 注解的方法），最后一个参数必须是 `IServiceContext`。

```java
// ✅ 正确：最后一个参数是 IServiceContext
@BizMutation
public Order cancel(@Name("orderId") String orderId, IServiceContext context) { ... }

// ✅ 正确：多参数时 IServiceContext 在最后
@BizMutation
public Order updateStatus(@Name("orderId") String orderId,
                          @Name("status") Integer status,
                          IServiceContext context) { ... }

// ❌ 错误：缺少 IServiceContext 参数
@BizMutation
public Order cancel(@Name("orderId") String orderId) { ... }
```

#### 3. 所有业务参数必须使用 `@Name` 注解

除了 `IServiceContext`、`FieldSelectionBean` 等框架参数外，所有业务参数都必须使用 `@Name` 注解指定参数名。

```java
// ✅ 正确：所有业务参数都有 @Name
@BizMutation
public LitemallCart updateQuantity(@Name("cartId") String cartId,
                                   @Name("number") Integer number,
                                   IServiceContext context) { ... }

// ✅ 正确：使用 @RequestBean 封装多参数
@BizMutation
public SubmitOrderResult submitOrder(@RequestBean SubmitOrderRequest request,
                                     IServiceContext context) { ... }

// ❌ 错误：业务参数缺少 @Name
@BizMutation
public void updateQuantity(String cartId, Integer number, IServiceContext context) { ... }
```

### 完整示例

```java
@BizModel("LitemallOrder")
public class LitemallOrderBizModel extends CrudBizModel<LitemallOrder>
        implements ILitemallOrderBiz {

    // ✅ 查询方法
    @BizQuery
    public List<LitemallOrder> getOrdersByUser(
            @Name("userId") String userId,
            FieldSelectionBean selection,
            IServiceContext context) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("userId", userId));
        return doFindList(query, selection, context);
    }

    // ✅ 修改方法
    @BizMutation
    public LitemallOrder cancel(
            @Name("orderId") String orderId,
            IServiceContext context) {
        LitemallOrder order = requireEntity(orderId, "update", context);
        order.setOrderStatus(AppMallDaoConstants.ORDER_STATUS_CANCEL);
        updateEntity(order, null, context);
        return order;
    }

    // ✅ 使用 @RequestBean 封装复杂参数
    @BizMutation
    public SubmitOrderResult submitOrder(
            @RequestBean SubmitOrderRequest request,
            IServiceContext context) {
        // ...
    }

    // ✅ private 辅助方法不需要注解和 IServiceContext
    private void validateOrderAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NopException(AppMallErrors.ERR_INVALID_AMOUNT);
        }
    }
}
```

## IXXBiz 接口使用场景

### 何时定义 IXXBiz 接口

| 场景 | 是否需要接口 | 原因 |
|------|-------------|------|
| 方法需要被**其他 BizModel 调用** | ✅ 需要 | 通过接口注入，实现解耦 |
| 只通过 GraphQL/REST 调用 | ❌ 不需要 | 直接在 BizModel 类中定义即可 |
| 需要在 Delta 模块中覆盖 | ✅ 需要 | 便于 Delta 扩展 |

### 接口定义规范

```java
// dao 模块中的接口
public interface ILitemallOrderBiz extends ICrudBiz<LitemallOrder> {

    // ✅ 被其他 BizModel 调用的方法
    LitemallOrder cancel(@Name("orderId") String orderId, IServiceContext context);

    // ✅ 跨聚合访问的方法
    List<LitemallOrder> getOrdersByUser(@Name("userId") String userId,
                                         FieldSelectionBean selection,
                                         IServiceContext context);

    // ❌ 不要定义只通过 GraphQL 调用的方法
    // SubmitOrderResult submitOrder(@RequestBean SubmitOrderRequest request,
    //                               IServiceContext context);
}
```

### BizModel 之间的调用

```java
@BizModel("LitemallCart")
public class LitemallCartBizModel extends CrudBizModel<LitemallCart> {

    // ✅ 正确：通过接口注入
    @Inject
    protected ILitemallOrderBiz orderBiz;

    @BizMutation
    public void checkout(@Name("cartId") String cartId, IServiceContext context) {
        // 通过接口调用其他 BizModel 的方法
        LitemallOrder order = orderBiz.cancel(orderId, context);
    }

    // ❌ 错误：直接注入 BizModel 类
    // @Inject
    // protected LitemallOrderBizModel orderBizModel;
}
```

---

## 基本结构

### 最简 BizModel

```java
@BizModel("LitemallCart")
public class LitemallCartBizModel extends CrudBizModel<LitemallCart>
        implements ILitemallCartBiz {

    public LitemallCartBizModel() {
        setEntityName(LitemallCart.class.getName());
    }
    // CrudBizModel 已提供：findPage, get, save, update, delete 等
}
```

### 接口定义规则

| 场景 | 处理方式 |
|------|---------|
| 方法需要被**其他 BizModel 调用** | 在接口中定义 |
| 只通过 GraphQL/REST 调用 | 直接在 BizModel 类中定义 |

```java
// dao 模块中的接口
public interface ILitemallCartBiz extends ICrudBiz<LitemallCart> {
    void clearCart(IServiceContext context);           // 被其他 BizModel 调用
    CartSummary getCartSummary(IServiceContext context);
    // checkout、updateQuantity 等若只通过 GraphQL 调用，无需放在接口中
}
```

### 注解规则

| 注解 | 用途 |
|------|------|
| `@BizQuery` | 查询操作 |
| `@BizMutation` | 修改操作（自动开启事务，无需 `@Transactional`） |
| 无注解 | 仅内部调用，不暴露为 GraphQL/REST |

```java
@BizModel("LitemallCart")
public class LitemallCartBizModel extends CrudBizModel<LitemallCart>
        implements ILitemallCartBiz {

    @BizQuery
    public CartCheckoutResult checkout(@Name("cartIds") List<String> cartIds,
                                       IServiceContext context) { ... }

    @BizMutation
    public LitemallCart updateQuantity(@Name("cartId") String cartId,
                                       @Name("number") Integer number,
                                       IServiceContext context) { ... }

    @Override
    @BizMutation  // 接口方法也需要注解才能被 GraphQL 调用
    public void clearCart(IServiceContext context) { ... }

    // 无注解 - 仅内部调用
    private void internalHelper() { ... }
}
```

### DTO 位置

```
app-mall-dao/src/main/java/app/mall/dao/
├── biz/                    # Biz 接口
├── dto/                    # DTO 类（@DataBean）
└── entity/                 # 实体类
```

## 参数类型

| 参数数量 | 推荐方式 |
|---------|---------|
| 1-3 个 | `@Name` 单独传参 |
| 4+ 个 | `@RequestBean` 封装为 Request 类 |

```java
// @Name - 少量参数
@BizMutation
public LitemallCart updateQuantity(@Name("cartId") String cartId,
                                   @Name("number") Integer number,
                                   IServiceContext context) { ... }

// @RequestBean - 多参数
@DataBean
public class SubmitOrderRequest implements Serializable {
    private List<String> cartIds;
    private String addressId;
    private String couponId;
    // 支持嵌套：private List<OrderItemRequest> items;
}

@BizMutation
public SubmitOrderResult submitOrder(@RequestBean SubmitOrderRequest request,
                                     IServiceContext context) {
    List<String> cartIds = request.getCartIds();
    // ...
}
```

**CRUD 内置方法**（`save`, `update`）使用 `Map<String, Object>` 参数，通过 XMeta 校验。

## 返回类型

| 场景 | 返回类型 |
|------|---------|
| 修改操作 | 返回实体本身 |
| 需要多个字段 | 定义 `@DataBean` DTO |

```java
// ✅ 返回实体
@BizMutation
public Order cancel(@Name("orderId") String orderId, IServiceContext context) {
    Order order = requireEntity(orderId, "update", context);
    order.setStatus(OrderConstants.CANCELLED);
    return update(order, context);
}

// ✅ 返回 DTO
@DataBean
public class CartCheckoutResult implements Serializable {
    private List<LitemallCart> cartGoods;
    private BigDecimal orderPrice;
}

// ❌ 避免：Map 作为返回类型（GraphQL 无法推断类型）
public Map<String, Object> checkout(...) { ... }
```

## 数据访问

### 实体获取

| 方法 | 用途 | 数据权限 |
|------|------|---------|
| `requireEntity(id, action, context)` | 获取实体，不存在抛异常 | ✅ |
| `getEntity(id, action, context)` | 获取实体，不存在返回 null | ✅ |
| `get(id, ignoreUnknown, context)` | 简单获取 | ✅ |
| `batchGet(ids, ignoreUnknown, context)` | 批量获取 | ✅ |

```java
// ✅ 推荐
Order order = requireEntity(orderId, "update", context);

// ❌ 绕过数据权限
Order order = dao().getEntityById(orderId);
```

### 查询列表

```java
// ✅ 推荐
QueryBean query = new QueryBean();
query.addFilter(FilterBeans.eq("userId", userId));
List<LitemallCart> carts = doFindList(query, null, null, context);

// ❌ 绕过数据权限
List<LitemallCart> carts = dao().findListByQuery(query);
```

### 保存/更新选择

| 场景 | 方法 | 原因 |
|------|------|------|
| 新建实体，前端数据 | `save(data, context)` | XMeta 校验 |
| 实体已通过 `requireEntity` 获取 | `updateEntity(entity, action, context)` | 避免重复校验 |
| 前端传入 Map 数据 | `update(data, context)` | 需要框架校验 |

```java
// 场景1：新建
Map<String, Object> data = new HashMap<>();
data.put("userId", userId);
return save(data, context);

// 场景2：实体已存在且已校验
Order order = requireEntity(orderId, "update", context);
order.setStatus(OrderConstants.CANCELLED);
updateEntity(order, null, context);  // action=null 默认 "update"
return order;

// 场景3：前端数据
Map<String, Object> data = new HashMap<>();
data.put("id", cartId);
data.put("number", number.shortValue());
return update(data, context);

// ❌ 绕过权限、验证、回调
dao().updateEntity(order);
```

**action 参数**：`updateEntity(entity, "approve", context)` 用于区分业务场景（如审核、发布），应用不同数据权限。

### 批量操作

```java
// 批量更新
Map<String, Object> data = new HashMap<>();
data.put("checked", true);
updateByQuery(query, data, context);

// 批量删除
batchDelete(ids, context);
```

## DataLoader / @BizLoader

`@BizLoader` 用于定义关联数据加载和扩展字段，解决 GraphQL 查询中的 N+1 问题。

### 基本用法

| 场景 | 方法签名 | 说明 |
|------|----------|------|
| 单对象加载 | `T loadXxx(Entity entity)` | 加载单个实体的关联数据 |
| 批量加载 | `Map<Entity, T> batchLoadXxx(List<Entity> entities)` | 批量加载，避免 N+1 |
| 扩展字段 | 配合 `@LazyLoad` | 按需计算，不破坏兼容性 |

```java
@BizModel("DemoUser")
public class DemoUserBizModel extends CrudBizModel<DemoUser> {

    // 单对象加载
    @BizLoader(forType = DemoUser.class)
    public List<DemoRole> loadRoles(DemoUser user) {
        return user.getRoles();
    }

    // 批量加载 - 解决 N+1 问题
    @BizLoader(forType = DemoUser.class)
    public Map<DemoUser, List<DemoRole>> batchLoadRoles(List<DemoUser> users) {
        List<String> userIds = users.stream()
            .map(DemoUser::getUserId)
            .collect(Collectors.toList());

        // 批量查询
        List<DemoRole> roles = roleDao().findByUserIds(userIds);
        Map<String, List<DemoRole>> rolesByUserId = roles.stream()
            .collect(Collectors.groupingBy(DemoRole::getUserId));

        // 构建返回结果
        Map<DemoUser, List<DemoRole>> result = new HashMap<>();
        for (DemoUser user : users) {
            result.put(user, rolesByUserId.getOrDefault(user.getUserId(), Collections.emptyList()));
        }
        return result;
    }

    // 扩展字段 - 计算属性
    @BizLoader(forType = DemoUser.class)
    public String loadDisplayName(DemoUser user) {
        return user.getUserName() + "(" + user.getEmail() + ")";
    }
}
```

### Delta 扩展字段

通过 Delta 机制为既有 API 增加字段，不修改原代码：

```java
@BizModel("LoginApi")
public class LoginApiBizModelDelta {

    @BizLoader(autoCreateField = true, forType = LoginResult.class)
    @LazyLoad
    public String location(@ContextSource LoginResult result, IServiceContext context) {
        return "loc:" + result.getUserInfo().getUserId();
    }
}
```

**关键参数**：
- `autoCreateField = true`：允许自动创建字段
- `forType = Xxx.class`：挂载到指定输出类型
- `@LazyLoad`：只有 selection 明确请求时才计算

### 相关类

- `io.nop.api.core.annotations.biz.BizLoader`
- `io.nop.api.core.annotations.biz.ContextSource`
- `io.nop.api.core.annotations.core.LazyLoad`

## 常量定义

| 类型 | 位置 | 方式 |
|------|------|------|
| 数据库字段枚举 | orm.xml dict | codegen 自动生成 |
| 业务规则常量 | 手动定义常量类 | 如免运费金额、超时时间 |

```xml
<!-- orm.xml -->
<dicts>
    <dict name="mall/order-status" valueType="int">
        <option code="CREATED" value="101"/>
        <option code="CANCEL" value="102"/>
        <option code="PAY" value="201"/>
    </dict>
</dicts>
<entity name="LitemallOrder">
    <columns>
        <column name="orderStatus" ext:dict="mall/order-status"/>
    </columns>
</entity>
```

```java
// codegen 生成（运行 mvn install 后）
public interface _AppMallDaoConstants {
    int ORDER_STATUS_CREATED = 101;
    int ORDER_STATUS_CANCEL = 102;
}

// 使用
import static app.mall.dao.AppMallDaoConstants.*;
if (order.getOrderStatus() == ORDER_STATUS_PAY) { ... }
```

```java
// 手动定义的业务常量
public interface AppMallConstants {
    BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("88");
    int ORDER_TIMEOUT_MINUTES = 30;
}
```

**❌ 错误**：在 Entity 中手动定义数据库字段枚举常量。

## 事务管理

- `@BizMutation` **自动开启事务**，无需 `@Transactional`
- 事务后回调：`txn().afterCommit(null, () -> { ... })`

## 跨聚合访问

```java
// ❌ 绕过数据权限
LitemallGoods goods = daoProvider().daoFor(LitemallGoods.class).getEntityById(id);

// ✅ 通过 BizModel 接口
@Inject
protected ILitemallGoodsBiz goodsBiz;

LitemallGoods goods = goodsBiz.requireEntity(id, "read", context);
```

## 复杂逻辑拆分：Processor + Step

### 拆分时机

- 单个方法超过 50 行
- 需要在多个 BizModel 间复用
- 涉及外部服务调用（支付、库存、风控）
- 业务规则复杂且可能变化

### 层次结构

```
BizModel.method()
    └── Processor.process(context)      // 与 Method 一一对应
            ├── step1(context)            // protected 方法
            ├── step2(context)
            │       └── Step.execute()    // 子函数内调用 Step（跨 Processor 复用时）
            └── step3(context)
```

**核心规则**：
1. Processor 与 Method 一一对应，命名：`{EntityName}{MethodName}Processor`
2. 优先用 protected 方法，支持继承扩展
3. 只有跨多个 Processor 复用的步骤才抽象为 Step
4. **Processor 不能直接调用 Step**，必须通过 protected 子函数间接调用
5. 所有方法都传递 IServiceContext

### 命名规范

| 组件 | 命名格式 | 示例 |
|------|---------|------|
| Processor | `{EntityName}{MethodName}Processor` | `LitemallOrderSubmitProcessor` |
| Step | `{功能域}{场景}Step` | `InventoryDeductStep`（单一场景） |

**❌ 错误**：`PaymentProcessor`（功能域）、`InventoryStep`（多种操作）

**✅ 正确**：`LitemallOrderSubmitProcessor`、`InventoryDeductStep`、`InventoryRestoreStep`

### 完整示例

```java
// 1. BizModel
@BizModel("LitemallOrder")
public class LitemallOrderBizModel extends CrudBizModel<LitemallOrder> {

    @Inject
    protected LitemallOrderSubmitProcessor orderSubmitProcessor;

    @BizMutation
    public SubmitOrderResult submitOrder(@RequestBean SubmitOrderRequest request,
                                         IServiceContext context) {
        return orderSubmitProcessor.process(request, context);
    }
}

// 2. Processor - 与方法一一对应
public class LitemallOrderSubmitProcessor {

    @Inject
    protected ILitemallAddressBiz addressBiz;

    @Inject
    protected InventoryDeductStep inventoryDeductStep;  // 跨 Processor 复用的 Step

    public SubmitOrderResult process(SubmitOrderRequest request, IServiceContext context) {
        LitemallAddress address = validateAddress(request.getAddressId(), context);
        List<LitemallOrderGoods> orderGoods = processCartItems(request.getCartIds(), context);
        calculatePrice(orderGoods, context);
        deductInventory(orderGoods, context);  // ✅ 通过子函数调用 Step
        return buildResult(orderGoods, context);
    }

    protected LitemallAddress validateAddress(String addressId, IServiceContext context) {
        return addressBiz.requireEntity(addressId, "read", context);
    }

    protected List<LitemallOrderGoods> processCartItems(List<String> cartIds,
                                                         IServiceContext context) { ... }

    protected void calculatePrice(List<LitemallOrderGoods> orderGoods,
                                   IServiceContext context) { ... }

    protected void deductInventory(List<LitemallOrderGoods> orderGoods,
                                    IServiceContext context) {
        inventoryDeductStep.execute(orderGoods, context);
    }

    protected SubmitOrderResult buildResult(List<LitemallOrderGoods> orderGoods,
                                             IServiceContext context) { ... }
}

// 3. Step - 单一场景，跨 Processor 复用
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

// 扩展示例
public class LitemallOrderSubmitExProcessor extends LitemallOrderSubmitProcessor {

    @Override
    protected void deductInventory(List<LitemallOrderGoods> orderGoods,
                                    IServiceContext context) {
        List<LitemallOrderGoods> filtered = filterGoods(orderGoods);
        inventoryDeductStep.execute(filtered, context);
        logDeduction(filtered, context);
    }
}
```

### 配置

```xml
<!-- _service.beans.xml -->
<beans>
    <bean id="litemallOrderSubmitProcessor"
          class="app.mall.service.processor.LitemallOrderSubmitProcessor"/>
    <bean id="inventoryDeductStep"
          class="app.mall.service.step.InventoryDeductStep"/>
</beans>
```

### 拆分策略总结

| 场景 | 处理方式 |
|------|---------|
| BizModel 方法超过 50 行 | 拆分为 Processor |
| Processor 内部步骤 | protected 方法 |
| 步骤跨 Processor 复用 | 抽象为 Step（单一场景） |
| 步骤仅单个 Processor 使用 | protected 方法，不提取 Step |
| Step 有多种操作 | 拆分为多个 Step，每个只做一件事 |

## 完整 BizModel 示例

```java
@BizModel("LitemallCart")
public class LitemallCartBizModel extends CrudBizModel<LitemallCart>
        implements ILitemallCartBiz {

    @Inject
    protected ILitemallGoodsProductBiz productBiz;

    public LitemallCartBizModel() {
        setEntityName(LitemallCart.class.getName());
    }

    @BizMutation
    public LitemallCart updateQuantity(@Name("cartId") String cartId,
                                       @Name("number") Integer number,
                                       IServiceContext context) {
        if (number == null || number < 1 || number > 999) {
            throw new NopException(AppMallErrors.ERR_CART_QUANTITY_INVALID)
                    .param("min", 1).param("max", 999);
        }

        String userId = context.getUserId().toString();
        LitemallCart cart = requireEntity(cartId, "update", context);

        if (!userId.equals(cart.getUserId())) {
            throw new NopException(AppMallErrors.ERR_CART_NOT_OWNER).param("cartId", cartId);
        }

        LitemallGoodsProduct product = productBiz.get(cart.getProductId(), false, context);
        if (product != null && number > product.getNumber()) {
            throw new NopException(AppMallErrors.ERR_CART_STOCK_INSUFFICIENT)
                    .param("available", product.getNumber());
        }

        cart.setNumber(number.shortValue());
        updateEntity(cart, "update", context);
        return cart;
    }

    @BizQuery
    public CartCheckoutResult checkout(@Name("cartIds") List<String> cartIds,
                                       IServiceContext context) {
        String userId = context.getUserId().toString();

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("userId", userId));
        query.addFilter(FilterBeans.eq("checked", true));
        List<LitemallCart> carts = doFindList(query, null, null, context);

        BigDecimal goodsPrice = carts.stream()
            .map(c -> c.getPrice().multiply(new BigDecimal(c.getNumber())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal freight = goodsPrice.compareTo(new BigDecimal("88")) < 0
            ? new BigDecimal("10") : BigDecimal.ZERO;

        CartCheckoutResult result = new CartCheckoutResult();
        result.setCartGoods(carts);
        result.setGoodsPrice(goodsPrice);
        result.setFreightPrice(freight);
        result.setOrderPrice(goodsPrice.add(freight));
        return result;
    }

    @Override
    @BizMutation
    public void clearCart(IServiceContext context) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("userId", context.getUserId()));
        deleteByQuery(query, context);
    }
}
```

## 常见错误

| 错误 | 正确做法 |
|------|---------|
| `dao().getEntityById(id)` | `requireEntity(id, "update", context)` |
| `@BizMutation @Transactional` | 只用 `@BizMutation`（已自动事务） |
| `Map<String, Object>` 作为返回类型 | 定义 `@DataBean` DTO |
| `daoProvider().daoFor(X.class).getEntityById(id)` | 通过 BizModel 接口访问 |
| `@Name("cartId") Integer cartId`（ID 类型不匹配） | 参数类型与实体 ID 一致 |

## 相关文档

- [DDD 在 Nop 中的实践](./ddd-in-nop.md)
- [服务层开发指南](./service-layer.md)
- [CRUD 开发指南](./crud-development.md)
- [DTO 编码规范](../04-core-components/enum-dto-standards.md)
- [完整示例](../08-examples/crud-example.md)
