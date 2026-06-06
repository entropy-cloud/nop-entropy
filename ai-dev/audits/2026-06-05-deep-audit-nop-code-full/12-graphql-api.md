# 维度 12：GraphQL 与 API 层 — nop-code 模块

## 第 1 轮（初审）

### [维度12-01] FieldSelectionBean 完全未使用 — 大字段总是完整返回

- **文件**: 所有 BizModel 类
- **证据片段**: nop-code-service 中搜索 `FieldSelectionBean` 返回零匹配。
- **严重程度**: P2
- **现状**: sourceCode BizLoader 总是返回完整源代码（可能数十KB），即使客户端只需文件元信息。
- **建议**: 对大字段使用 FieldSelectionBean 按需装载。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度12-02] NopCodeFileBizModel.getByPath 返回 core 层 POJO 而非 ORM entity

- **文件**: `NopCodeFileBizModel.java:34-40`
- **严重程度**: P3
- **现状**: 返回 CodeFileAnalysisResult（core POJO），绕过 xmeta 字段过滤。
- **建议**: 设计权衡，可在文档中说明。
- **信心水平**: 确定
- **复核状态**: 未复核

### 清洁区域

- 所有查询使用 QueryBean + FilterBeans，无硬编码 SQL
- 无手动序列化绕过 GraphQL
