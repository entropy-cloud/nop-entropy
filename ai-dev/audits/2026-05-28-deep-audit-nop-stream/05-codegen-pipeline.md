# 维度 05: 生成管线完整性

## 适用性
适用（有限）

## 检查范围
- nop-stream-cep/src/main/java/io/nop/stream/cep/model/_gen/ 下 4 个生成文件
- nop-stream-cep/precompile/gen-cep-xdsl.xgen
- nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/pattern.xdef

## 发现

本模块仅有 CEP 子模块使用了 XDSL 代码生成，生成了 4 个 `_CepPattern*Model.java` 文件。生成管线完整：xdef 定义 → xgen 模板 → _gen 输出。手写的 `CepPatternPartModel.java` 正确继承自 `_CepPatternPartModel.java`。

零实质性发现。生成代码不应作为审计对象（按审计规则第4条），且手写扩展文件结构合理。

## 维度总结
生成管线有限且完整。CEP 子模块使用 pattern.xdef 生成 4 个模型类，手写扩展层正常。无问题。
