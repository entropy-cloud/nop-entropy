# Audit Dimension 12: GraphQL and API Layer — nop-code

## 无 P1+ 发现

检查内容：查询/变更映射、分页、FieldSelectionBean、手动序列化绕过、硬编码 SQL。
- @BizQuery/@BizMutation/@BizLoader 正确映射
- 分页通过手动 PageBean 构造（委托 CodeIndexService 自定义查询逻辑）
- GraphQL selection 由 @BizLoader 自然处理
- 无硬编码 SQL
