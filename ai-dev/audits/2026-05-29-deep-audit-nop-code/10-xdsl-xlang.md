# 维度 10+11：XDSL 与 XLang 正确性 / XMeta 与 BizModel 对齐

**审计日期**: 2026-05-29

## 第 1 轮（初审）

**总结**: nop-code 模块的 XDSL 文件质量良好。x:schema 引用、x:extends 继承链、beans.xml class 路径、dict 值对齐均无问题。

### 无问题区域（已验证）

| 检查项 | 结论 |
|--------|------|
| x:schema 引用正确性 | 全部正确 |
| x:extends 继承链 | 全部正确（*.xmeta extends _*.xmeta 等）|
| beans.xml class 路径与 Java 类路径一致 | 全部一致 |
| dict 枚举值三方对齐（Java枚举/ORM dict/yaml dict）| 完全对齐 |
| ORM 反向关系自动推导 | 正确（管线标准行为）|
| BizModel 注解自动注册 action | 标准模式 |
| NopCodeFile.xmeta sourceCode published=false | 正确控制大字段暴露 |

### [维度10-01] NopCodeSemanticEdge 关系缺少 refPropName/refDisplayName

- **文件**: `nop-code/model/nop-code.orm.xml:857-873`
- **证据片段**:
  ```xml
  <to-one name="sourceSymbol" refEntityName="io.nop.code.dao.entity.NopCodeSymbol" tagSet="pub">
  <!-- 无 refDisplayName 和 refPropName -->
  ```
- **严重程度**: P3
- **现状**: 缺少 refPropName 意味着 NopCodeSymbol 不会有反向集合属性。设计意图合理。
- **建议**: 若追求完整性可补充 refDisplayName，但非必要。
- **信心水平**: 90%
- **误报排除**: NopCodeSymbol 不需要 semanticEdge 反向集合，通过专门服务方法查询。
- **复核状态**: 未复核

### [维度11-01] NopCodeFileBizModel @BizLoader forType=DTO 无对应 xmeta prop

- **文件**: `NopCodeFileBizModel.java:55-89`, `NopCodeFile.xmeta`
- **证据片段**:
  ```java
  @BizLoader(forType = CodeFileAnalysisResult.class)
  public List<CodeSymbol> symbols(@ContextSource CodeFileAnalysisResult file) { ... }
  ```
  xmeta 中只有 sourceCode prop，其他三个 loader（symbols/types/outline）无 prop。
- **严重程度**: P3
- **现状**: forType 指向非实体类 DTO，loader 为该 DTO 服务而非实体，不需要 xmeta prop。
- **建议**: 设计意图清晰，可保持现状。
- **信心水平**: 90%
- **误报排除**: forType 指向非实体类是合法用法。
- **复核状态**: 未复核

### [维度11-02] NopCodeSymbolBizModel @BizLoader usages 与 xmeta 关系 prop 重名

- **文件**: `NopCodeSymbolBizModel.java:96-108`, `NopCodeSymbol.xmeta`
- **严重程度**: P3
- **现状**: usages loader 返回 List<AnnotationUsageDTO>，而 xmeta 中 usages 是 NopCodeUsage 实体集合。BizModel loader 会覆盖 ORM 关系映射。
- **建议**: 确认这是有意设计。如是有意在 xmeta 标注。
- **信心水平**: 85%
- **误报排除**: BizModel loader 覆盖 xmeta 关系 prop 是 Nop 平台标准行为。
- **复核状态**: 未复核
