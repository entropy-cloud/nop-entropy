# 维度 12：GraphQL 与 API 层

## 审计范围

全模块 @BizQuery/@BizMutation 映射、分页查询、硬编码 SQL 搜索。

## 第 1 轮（初审）发现

**零发现。**

- 所有 @BizMutation 方法正确注解，框架自动注册为 GraphQL mutation
- 无自定义 @BizQuery 方法，查询全部继承 CrudBizModel 标准 findPage/findList
- 全模块搜索硬编码 SQL/HQL：零命中
- 全模块无 @BizLoader 使用

## 最终保留项

无发现。
