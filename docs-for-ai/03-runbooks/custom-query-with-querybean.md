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

## 常见坑

1. 在普通 BizModel 中直接回退到 `dao().findAllByQuery(query)`。
2. 把查询 DTO 设计得过重，而不是先用 `QueryBean` 与基础参数表达。

## 相关文档

- `../02-core-guides/service-layer.md`
- `../04-reference/safe-api-reference.md`
