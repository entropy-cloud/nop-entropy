# 新增 BizLoader 字段

## 适用场景

- 你想给现有返回对象增加计算字段或关联字段
- 你希望避免 N+1 查询

## AI 决策提示

- ✅ 普通扩展字段使用 `@BizLoader`
- ✅ 需要兼容已有 API 时，优先 `autoCreateField = true`
- ✅ 批量加载优先于循环单条查询

## 最小闭环

### 1. 单对象加载

```java
@BizLoader(forType = Order.class)
public String loadDisplayName(Order order) {
    return order.getOrderNo() + "-" + order.getUserId();
}
```

### 2. 批量加载

```java
@BizLoader(forType = Order.class)
public Map<Order, List<OrderItem>> batchLoadItems(List<Order> orders) {
    // 批量查询并组装结果
}
```

### 3. Delta 中增加字段

```java
@BizLoader(autoCreateField = true, forType = LoginResult.class)
@LazyLoad
public String location(@ContextSource LoginResult result, IServiceContext context) {
    return "loc:" + result.getUserInfo().getUserId();
}
```

## 常见坑

- ❌ 在 loader 里循环单条查询，制造 N+1
- ❌ 扩展字段直接改原始返回 DTO，而不是优先走 Delta/BizLoader
- ❌ 忘记 `forType`

## 相关文档

- `03-development-guide/bizmodel-guide.md`
- `12-tasks/extend-api-with-delta-bizloader.md`
