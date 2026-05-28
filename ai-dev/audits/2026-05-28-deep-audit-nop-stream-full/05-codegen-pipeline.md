# 维度 05：生成管线完整性

## 零发现说明

**检查范围**: 搜索 nop-stream/ 下 codegen 脚本和生成管线。

**结论**: nop-stream 不使用 Nop 平台的标准代码生成管线（model → codegen → dao → meta → service → web）。该模块唯一的代码生成是 nop-stream-cep 中由 pattern.xdef 生成的 4 个 _gen 文件（_CepPatternModel 等），这些通过 nop-kernel/nop-xdefs 的 XDSL 机制在构建时生成，已验证生成文件正确且无手写修改。

此维度的大部分检查项不适用于 nop-stream。
