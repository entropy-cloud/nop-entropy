# 审核维度 19：命名与术语一致性

## 第 1 轮（初审）

**结论：未发现中高等级问题。**

- 实体名在 ORM/BizModel/接口/IoC bean 中完全一致（11 个实体均遵循标准模式）
- 错误码前缀一致（全部使用 nop.err.code.*）
- bean 命名与类名对应

### [维度19-01] ERR_CODE_INVALID_GIT_REF 在 core 和 service 层重复定义

- **文件**: `NopCodeCoreErrors.java:19` 和 `NopCodeErrors.java:41`
- **严重程度**: P3
- **现状**: 两个文件定义了完全相同的错误码字符串 nop.err.code.invalid-git-ref，service 层的从未被使用。
- **建议**: 删除 NopCodeErrors 中的重复定义。
- **复核状态**: 未复核
