# 创建 Request / Response DTO

## 适用场景

- BizModel 方法参数超过 3 个。
- 返回值包含多个字段，不适合直接返回实体。

## AI 决策提示

- 多参数输入优先 `@RequestBean`。
- 多字段输出优先 `@DataBean`。
- DTO 默认放在最贴近复用边界的位置：BizModel 与 Processor 共用时常见于 `*-dao/.../dto/`，API message bean 常见于 `*-api/.../messages` 或 `.../beans`。
- 如果周边模块已经大量使用 `ExtensibleBean` + `@PropMeta`，优先跟随该风格，而不是另起一套 DTO 风格。

## 最小模板

```java
@DataBean
public class SubmitOrderRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<String> cartIds;
    private String addressId;
}
```

```java
@DataBean
public class SubmitOrderResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String orderId;
    private BigDecimal totalPrice;
}
```

```java
@BizMutation
public SubmitOrderResult submitOrder(@RequestBean SubmitOrderRequest request,
                                     IServiceContext context) {
    // ...
}
```

## 默认约束

1. 添加 `@DataBean`。
2. 提供标准 getter / setter。
3. 如果这是局部 DTO 且需要跨边界序列化，或周边代码已经统一实现 `Serializable`，再补 `Serializable`。
4. 不要默认使用 Lombok `@Data` 代替手写访问器。

## 什么时候跟随 `ExtensibleBean` 风格

| 场景 | 默认做法 |
|------|---------|
| 当前任务里的局部 DTO | 普通 `@DataBean` + getter / setter；按需要补 `Serializable` |
| 模块内已有成体系的 API message bean | 跟随现有 `ExtensibleBean` + `@PropMeta` 风格 |

不要为了一个简单本地 DTO 额外引入复杂 message bean 模式；只有周边代码已经明确采用这套风格时再跟随。

## 常见坑

1. 用 `Map<String, Object>` 表达复杂返回值。
2. DTO 放在 service 模块里到处散落。
3. 一个 DTO 混入多个业务场景的所有字段。

## 相关文档

- `../02-core-guides/dto-json-and-message-beans.md`
- `../02-core-guides/service-layer.md`
- `./write-bizmodel-method.md`
