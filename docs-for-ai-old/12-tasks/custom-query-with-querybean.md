# 用 QueryBean 写自定义查询（FilterBeans/分页/排序）

## 适用场景

- 内置 `findPage/findList` 无法表达你的过滤条件或业务规则
- 你需要根据请求动态拼装查询条件

## AI 决策提示

- ✅ 只要能表达：优先使用 `CrudBizModel` 内置 `doFindPage/doFindList` 等能力
- ✅ 查询条件用 `QueryBean` + `FilterBeans`
- ✅ 入参用 `Map`（request）或 `QueryBean` 本身，不要自定义 DTO

## 最小闭环

参考类：`io.nop.graphql.core.engine.MyEntityBizModel`

```java

## 进阶：需要“拼装过滤条件”时怎么做

上面的示例是“直接消费 QueryBean”。如果你确实需要在 BizModel 内部根据 request 动态拼接过滤条件：

- 优先用 `FilterBeans` 来构建 filter 树
- 把拼装逻辑放在一个小函数里（便于复用/测试）
- 最终仍然交给 `CrudBizModel.doFindPage/doFindList` 执行（如果你的 BizModel 继承自 `CrudBizModel`）

## 相关类

- `io.nop.api.core.beans.query.QueryBean`
- `io.nop.api.core.beans.FilterBeans`
- `io.nop.biz.crud.CrudBizModel`（`doFindPage` 方法）
- 示例：`io.nop.graphql.core.engine.MyEntityBizModel`
