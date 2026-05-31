# 维度 05：生成管线完整性

> 注：nop-stream-cep 是唯一有代码生成的子模块，使用 XDEF schema → XDSL codegen → Java Model 管线。

## 第 1 轮（初审）

### 检查范围

管线追踪：`pattern.xdef` → `precompile/gen-cep-xdsl.xgen` → `_gen/_CepPatternModel.java` 等生成基类 → `CepPatternModel.java` 等手写扩展 → `CepPatternBuilder.java` 桥接到 Pattern API。

### 管线完整性评估：通过

| 环节 | 文件 | 结论 |
|------|------|------|
| XDEF 源模型 | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/pattern.xdef` | 完整，4 个模型 |
| Codegen 脚本 | `nop-stream-cep/precompile/gen-cep-xdsl.xgen` | 正确 |
| 生成基类 (x4) | `_gen/_CepPattern*.java` | 与 xdef 一致 |
| 手写扩展 (x4) | `CepPattern*.java` | 正确扩展+实现接口 |
| 枚举 (x2) | `FollowKind.java`, `AfterMatchSkipStrategyKind.java` | 与 xdef/Builder 一致 |
| 桥接 Builder | `CepPatternBuilder.java` | 正确使用所有模型类 |
| Maven 插件 | `exec-maven-plugin` + `CodeGenTask` | 配置正确 |

### [维度05-01] XML → Model 加载路径无测试覆盖

- **文件**: `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/model/builder/` 下所有测试
- **证据片段**: 所有 3 个测试类（TestCepPatternBuilder, TestCepPatternBuilderModel, TestCepPatternBuilderTypeCheck）均通过 Java API 手工构建 CepPatternModel，无 XML round-trip 测试。
- **严重程度**: P3
- **现状**: XDSL 框架的 XML 反序列化能力（xpl-fn 解析、多态 type 鉴别、xdef:ref 继承）未经过 XML round-trip 测试。
- **风险**: 如果 xdef schema 定义与 XML 加载器行为不一致，运行时通过 XML 加载时才暴露。
- **建议**: 增加 1-2 个 XML round-trip 测试，验证 `.pattern.xml` 文件能正确解析为 CepPatternModel。
- **信心水平**: 很可能
- **误报排除**: 当前无 XML 使用场景，但 XDSL 模型的 XML 加载是平台标准能力，应有测试保护。
- **复核状态**: 未复核

---

## 维度复核结论

（待复核）

## 子项复核结论

（待复核）

## 最终保留项

（待复核后填写）
