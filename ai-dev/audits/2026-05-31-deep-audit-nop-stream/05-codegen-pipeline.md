# 维度 05：生成管线完整性 — N/A

nop-stream 不使用标准的 model→codegen→dao→meta→service→web 生成管线。

唯一的代码生成产物是 `nop-stream-cep` 中的 `_gen/*.java` 文件（CepPatternModel 等），由 XDSL xdef 生成。这是标准 XLang 模型生成，不需要完整的 CRUD 生成管线。

**检查范围**：确认无标准生成管线，仅有 XDSL 模型生成（属于维度 10 范畴）。

## 最终保留项

无发现。本维度不适用于此模块。
