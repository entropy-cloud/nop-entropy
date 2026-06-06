# 维度 19：命名与术语一致性 — nop-code 模块

## 第 1 轮（初审）

### [维度19-01] CodeRelationType 与 CodeUsageKind 枚举概念重叠

- **文件**: `CodeRelationType.java` 和 `CodeUsageKind.java`
- **严重程度**: P3
- **现状**: 两个枚举都对"代码实体间关系类型"建模，可能造成混淆。
- **建议**: 文档说明何时使用哪个枚举，或在合理时合并。
- **信心水平**: 很可能
- **复核状态**: 未复核

### 正面发现

- 错误码前缀一致使用 `nop.err.code.*`
