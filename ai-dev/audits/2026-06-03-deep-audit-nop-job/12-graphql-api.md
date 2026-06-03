# 维度 12：GraphQL 与 API 层

## 第 1 轮（初审）

### 结论：GraphQL API 层合规，无发现

1. 所有自定义方法为 @BizMutation，查询由 CrudBizModel 基类提供。
2. 无硬编码 SQL 或手动序列化绕过 GraphQL selection。
3. jobParams 使用 graphql:jsonComponentProp 注解，由框架处理 JSON 组件的 GraphQL 选择集。
4. 所有数据访问通过 QueryBean + FilterBeans 构建参数化查询。
