# 维度 12：GraphQL 与 API 层

## 第 1 轮（初审）

未发现问题。

### 检查范围

- NopCodeIndexBizModel: 7 @BizQuery + 5 @BizMutation
- NopCodeSymbolBizModel: 12 @BizQuery + 1 @BizMutation + 2 @BizLoader
- NopCodeFileBizModel: 3 @BizQuery + 4 @BizLoader
- 全部 48 个注解方法

### 检查结果

1. **@BizQuery/@BizMutation 映射**: 全部 48 个注解方法正确使用，框架自动映射为 GraphQL query/mutation。参数均通过 @Name 声明。
2. **分页查询**: 正确使用 PageBean<T> 返回类型，通过 QueryBean + findPageByQuery 实现。
3. **无手动序列化/反序列化**: 所有查询结果直接返回 DTO 对象。
4. **无硬编码 SQL/HQL**: 全部使用 QueryBean + FilterBeans 参数化查询。
