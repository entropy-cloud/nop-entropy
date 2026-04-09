# 创建 Request / Response DTO

## 适用场景

- BizModel 方法参数超过 3 个
- 返回值包含多个字段，不适合直接返回实体

## AI 决策提示

- ✅ 多参数输入：优先 `@RequestBean`
- ✅ 多字段输出：优先 `@DataBean`
- ✅ DTO 放在 `*-dao/.../dto/` 或项目约定的位置
- ❌ 不要用 `Map<String, Object>` 作为复杂返回类型

## 最小闭环

### 1. 定义请求 DTO

```java
@DataBean
public class SubmitOrderRequest implements Serializable {
    private List<String> cartIds;
    private String addressId;
    private String couponId;
}
```

### 2. 定义返回 DTO

```java
@DataBean
public class SubmitOrderResult implements Serializable {
    private String orderId;
    private BigDecimal totalPrice;
    private List<OrderItemDto> items;
}
```

### 3. 在 BizModel 中使用

```java
@BizMutation
public SubmitOrderResult submitOrder(@RequestBean SubmitOrderRequest request,
                                     IServiceContext context) {
    // ...
}
```

## 快速判断表

| 场景 | 推荐 |
|------|------|
| 1 到 3 个简单参数 | `@Name` |
| 4 个及以上参数 | `@RequestBean` |
| 返回实体本身 | 直接返回实体 |
| 返回多个聚合字段 | `@DataBean` DTO |

## 常见坑

- ❌ 用 `Map<String, Object>` 表达复杂返回结果
- ❌ Request DTO 没有 `@DataBean`
- ❌ DTO 放在 service 层随意散落，导致复用性差

## 相关文档

- `03-development-guide/bizmodel-guide.md`
- `12-tasks/write-bizmodel-method.md`
