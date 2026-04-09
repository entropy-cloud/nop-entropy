# 新增 BizLoader 字段

## 适用场景

- 想给返回对象增加计算字段或关联字段。
- 希望避免 GraphQL 查询里的 N+1 问题。

## AI 决策提示

- 普通扩展字段优先用 `@BizLoader`。
- 批量加载优先于循环单条查询。
- 如果字段本身还不存在，优先看 `extend-api-with-delta-bizloader.md`。

## 最小闭环

### 1. 给已有类型补 loader

```java
@BizLoader(forType = Order.class)
public String displayName(@ContextSource Order order, IServiceContext context) {
    return order.getOrderNo() + "-" + order.getUserId();
}
```

### 2. 关联数据优先批量加载

```java
@BizLoader(forType = Order.class)
public Map<Order, List<OrderItem>> batchLoadItems(List<Order> orders) {
    // 批量查询并组装结果
}
```

### 3. 只在 selection 请求该字段时再计算

对于昂贵字段，优先配合 `@LazyLoad`。

## 常见坑

1. 在 loader 里循环单条查询，制造 N+1。
2. 扩展字段时先改原始 DTO 或生成物，而不是先考虑 BizLoader / Delta。
3. 忘记 `forType` 或字段名不匹配。

## 相关文档

- `./extend-api-with-delta-bizloader.md`
- `../02-core-guides/api-and-graphql.md`
- `../02-core-guides/service-layer.md`
