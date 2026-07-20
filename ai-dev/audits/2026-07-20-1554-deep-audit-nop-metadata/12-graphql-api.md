# 维度 12：GraphQL 与 API 层 — 审计报告

> 初审结果（待复核）

## 审计结论：通过（轻微发现）

GraphQL API 层整体健康。方法注解、参数注解、BizModel↔I*Biz 一致性全部正确。

## 发现

### [维度12-01] FieldSelectionBean 参数已声明但首版未使用

- **文件**: `NopMetaTableBizModel.java:509,558,600`
- **说明**: `queryTableData`, `queryJoinData`, `queryAggregation` 声明了 `@Optional @Name("selection") FieldSelectionBean selection` 但首版未使用。代码注释标注为"计划维度12-01"。
- **严重程度**: P3

### [维度12-02] 21 个方法返回 Map<String,Object> 绕过 GraphQL selection

- **说明**: 同维度03-03，Map 返回类型导致 GraphQL selection 机制无法按需选择字段。
- **严重程度**: P2
