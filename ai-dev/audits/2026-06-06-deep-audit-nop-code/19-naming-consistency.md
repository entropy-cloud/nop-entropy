# 维度 19：命名与术语一致性

## 审计日期
2026-06-06

## 第 1 轮（初审）

### [维度19-01] NopCodeSemanticEdge.relationType 使用错误 dict（与维度04-01重复）

- **严重程度**: P1（已在维度04报告）
- **现状**: code/relation_type dict 仅含 EXTENDS/IMPLEMENTS，但存储 SemanticRelationType 枚举名。
- **建议**: 创建 code/semantic_relation_type dict。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度19-02] 错误码前缀一致

- **现状**: 错误码统一使用 nop.err.code.* 前缀，跨 NopCodeCoreErrors 和 NopCodeErrors。命名一致。

### [维度19-03] provenance 字段无 dict 映射

- **严重程度**: P3
- **现状**: EdgeProvenance 枚举名直接存为字符串，无 dict 定义。
- **建议**: 添加 code/provenance dict 用于 UI 显示标签。
- **信心水平**: 可能
- **复核状态**: 未复核
