# 维度 12：GraphQL 与 API 层

## 第 1 轮（初审）

**检查范围**：分页查询（findPage_files, findPage_symbols）、SQL 生成路径、RPC/GraphQL 路径一致性。

## 零发现

1. 分页正确使用 QueryBean + countByQuery + findPageByQuery 模式。
2. 无硬编码 SQL，全部通过 QueryBean + FilterBeans 参数化构建。
3. GraphQL 路径遵循 `BizModel名__方法名` 约定，一致。

## 最终保留项

无。
