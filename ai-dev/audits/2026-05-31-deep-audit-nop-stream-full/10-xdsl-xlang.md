# 维度 10：XDSL 与 XLang 正确性

## 第 1 轮（初审）

### [维度10-01] pattern.xdef 中 afterMatchSkipTo 在 SKIP_TO_FIRST/SKIP_TO_LAST 时语义必填但 XDSL 未约束

- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/pattern.xdef:10-14,44-46`
- **证据片段**:
  ```xml
  <!-- pattern.xdef 第 10-14 行 -->
  <pattern x:schema="/nop/schema/xdef.xdef"
           afterMatchSkipStrategy="enum:io.nop.stream.cep.model.AfterMatchSkipStrategyKind"
           afterMatchSkipTo="xml-name"
  ```
- **严重程度**: P3
- **现状**: `afterMatchSkipTo` 声明为 `xml-name`（可选），但 xdef 注释明确说 SKIP_TO_FIRST/SKIP_TO_LAST 时需要此参数。XDSL 层面未约束，CepPatternBuilder 也无 null 防御。
- **风险**: 当前无用户可见故障（通过 Java API 编程式构建模型），但属于防御性编程缺陷。
- **建议**: 在 CepPatternBuilder 中添加 null 检查，或在 xdef 中添加条件约束注释。
- **信心水平**: 很可能
- **误报排除**: 不是误报——xdef 注释明确声明了语义约束但未在 schema 层强制执行。
- **复核状态**: 未复核

### [维度10-02] _CepPatternSingleModel/_CepPatternGroupModel 中 _type 字段 javadoc 为空

- **严重程度**: P3（信息级别）
- **现状**: 多态鉴别器字段的 javadoc 为空，属于代码生成器默认行为，非功能错误。记录在案不强制修复。

### [维度10-03] CepPatternModel implements ICepPatternGroupModel 依赖隐式方法签名匹配

- **严重程度**: P3（信息级别）
- **现状**: 编译通过且语义正确，仅作为信息性记录。

## 总体评价

nop-stream 的 XDSL 使用范围有限（仅 pattern.xdef 一个定义文件），结构合理，生成的 _gen 类与手工子类配合正确。
