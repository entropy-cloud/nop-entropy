# 维度19：命名与术语一致性

## 第 1 轮（初审）

**结论**: 命名整体一致，发现 1 个轻微术语混用。

### [维度19-01] "super class" vs "super type" 术语混用

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L266（NopCodeSymbol.superClassName）、L550（NopCodeInheritance.superTypeId）
- **严重程度**: P3
- **现状**: superClassName 存简单类名，superTypeId 存 ID 引用。语义不同是合理的，但 "class" 和 "type" 混用可能造成理解困惑。
- **建议**: 如 ORM 模型做较大变更，可统一为 superTypeName 或 superClassId。当前不影响功能。
- **信心水平**: 有趣的猜测
- **误报排除**: 两者存储的数据类型不同（名称 vs ID），术语差异是合理的。
- **复核状态**: 未复核

## 通过项

1. 实体名在 ORM 和 Java 之间完全一致
2. 字段名 snake_case/camelCase 转换一致
3. 错误码前缀 nop.err.code.* 与模块名一致
4. bean 名称与类名有合理对应
