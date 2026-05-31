# 审核维度 12：GraphQL 与 API 层

## 第 1 轮（初审）

**结论：未发现问题。**

- @BizQuery/@BizMutation 映射正确
- 自定义分页使用 PageBean 返回（数据源非 ORM 时自定义分页合理）
- 所有查询通过 QueryBean/FilterBeans，无硬编码 SQL
- RPC 路径和 GraphQL 路径一致
- incrementalStatusMap 使用 ConcurrentHashMap，有 evict 限制（最多 20 条）
