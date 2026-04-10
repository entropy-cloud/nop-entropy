# 自定义查询

## 适用场景

- 标准 `findPage` / `findList` 不足以表达过滤条件。
- 需要按请求动态拼装查询树。

## AI 决策提示

- 只要能表达，优先使用 `QueryBean + FilterBeans`。
- 最终仍然优先交给 `CrudBizModel.doFindList()` / `doFindPage()` 执行。
- 不要把原始 DAO 查询当成普通 BizModel 默认方案。

## 最小模板

```java
QueryBean query = new QueryBean();
query.addFilter(FilterBeans.eq("status", 1));
query.setLimit(20);
return doFindList(query, selection, context);
```

## 动态拼装过滤条件

推荐把过滤条件拼装放到一个小函数里，再交给 `doFindList()` / `doFindPage()` 执行，这样更容易复用和测试。

## 为什么优先 `doFindList()` / `doFindPage()`

交给 `CrudBizModel` 执行，不只是少写代码，还会保留这些默认能力：

1. 数据权限过滤追加。
2. XMeta / objMeta 上的默认 filter 与 orderBy 合并。
3. page size 上限控制。
4. filter 转换、map-to-prop 转换与表达式解析。
5. 如果当前排序条件不具备唯一性，会自动补主键排序，保证分页结果稳定。

如果你直接回退到底层 DAO，这些默认行为就需要自己补齐。

## 常见过滤写法

```java
QueryBean query = new QueryBean();
query.addFilter(FilterBeans.and(
    FilterBeans.eq("status", status),
    FilterBeans.contains("name", keyword)
));
query.setLimit(20);
return doFindPage(query, selection, context);
```

常用模式：

1. 精确匹配：`FilterBeans.eq(...)`
2. 多值匹配：`FilterBeans.in(...)`
3. 组合条件：`FilterBeans.and(...)` / `or(...)`
4. 模糊匹配：`FilterBeans.contains(...)`

## `leftJoinProps` 的边界

如果查询用了 `leftJoinProps`，它并不是完全自由输入：

1. 会受允许列表约束。
2. 会受数量上限限制。

所以普通 BizModel 不要把它当作无限制的通用查询逃生口。

## 常见坑

1. 在普通 BizModel 中直接回退到 `dao().findAllByQuery(query)`。
2. 把查询 DTO 设计得过重，而不是先用 `QueryBean` 与基础参数表达。

## 相关文档

- `../02-core-guides/service-layer.md`
- `../04-reference/safe-api-reference.md`
