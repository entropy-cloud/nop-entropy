# 维度 12：GraphQL 与 API 层

## 第 1 轮（初审）

## 发现数量：0

所有检查项均通过：

| 检查项 | 结论 |
|--------|------|
| @BizMutation → GraphQL mutation 映射 | 8 个方法正确映射，方法名即 mutation 名称 |
| @BizQuery → GraphQL query 映射 | 无自定义 @BizQuery，标准查询由 CrudBizModel 提供 |
| 分页查询 QueryBean + doFindPage | CrudBizModel 和 Store 层均使用 QueryBean + FilterBeans |
| 手动序列化绕过 selection | 未发现，JSON 工具仅用于内部存储 |
