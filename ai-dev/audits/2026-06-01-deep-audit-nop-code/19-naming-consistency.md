# 维度 19：命名与术语一致性

## 第 1 轮（初审）

**检查范围**：ORM 实体名、BizModel 类名、字段名、错误码前缀的一致性。

## 零发现

1. ORM 实体名 → BizModel 类名一致（NopCodeIndex → NopCodeIndexBizModel）。
2. 字段名一致（ORM qualifiedName = 核心模型 qualifiedName）。
3. 错误码前缀一致（nop.err.code.*）。

注意：GraphExporter 内联 ErrorCode 已在维度 09 报告（09-03），不重复。

## 最终保留项

无。
