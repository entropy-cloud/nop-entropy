# 维度 10：XDSL 与 XLang 正确性

## 第 1 轮（初审）

### [维度10-01] Dead code / incorrect instanceof in CepPatternBuilder.buildGroupPattern()

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:64-71`
- **证据片段**:
  ```java
  if (nextModel instanceof CepPatternPartModel) {       // <-- ALWAYS TRUE
      pattern = buildFollow(pattern, followKind, nextModel.getName());
  } else {                                                // <-- DEAD CODE
      pattern = buildFollowGroup(pattern, followKind, (CepPatternGroupModel) nextModel);
  }
  ```
- **严重程度**: P1
- **现状**: `CepPatternPartModel` 是 `CepPatternSingleModel` 和 `CepPatternGroupModel` 的共同抽象基类。因此 `instanceof CepPatternPartModel` 对任何有效模型实例**永远为 true**，使 else 分支（调用 buildFollowGroup）成为死代码。正确应为 `instanceof CepPatternSingleModel`（与同文件第42行的正确模式一致）。
- **风险**: 当 `CepPatternGroupModel` 作为 next 步骤出现在模式链中时，buildFollow() 仅使用 group 的 name 字符串，静默丢弃 group 的全部内部结构（子部件、起始点、条件、跳过策略）。嵌套 group 模式产生不正确的 CEP 行为。现有测试仅使用 CepPatternSingleModel 实例，从未触发此代码路径。
- **建议**: 将第67行的 `instanceof CepPatternPartModel` 改为 `instanceof CepPatternSingleModel`。
- **误报排除**: 不是代码风格问题。这是一个导致嵌套 group 模式功能错误的实际 bug，造成模型结构静默丢失。同文件第42行使用了正确的 instanceof 模式，此处是明确的逻辑错误。
- **复核状态**: 已保留（独立复核确认：类层次结构验证，同文件第42行正确模式对比，功能影响确认）

## 维度复核结论

- [维度10-01]: **保留 P1** — 独立复核确认 instanceof 检查永远为 true，嵌套 group 模式结构被静默丢弃，是实际逻辑 bug
- [维度10-02]: **保留 P3** — typo 确认
- [维度10-03]: **保留 P3** — 缺失命名空间确认

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 10-01 | P1 | CepPatternBuilder.java:67 | instanceof CepPatternPartModel 应为 CepPatternSingleModel，嵌套 group 结构丢失 |
| 10-02 | P3 | resource-spec.xdef:2 | cpuCors 拼写错误 |
| 10-03 | P3 | resource-spec.xdef | 缺失 xmlns:xdef 命名空间 |

### [维度10-02] Typo cpuCors in resource-spec.xdef

- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/resource-spec.xdef:2`
- **证据片段**:
  ```xml
  <resource-spec xdef:bean-package="io.nop.cluster.cluster.resources" cpuCors="0">
  ```
- **严重程度**: P3
- **现状**: 属性名拼写为 cpuCors（缺少字母 e），而对应 Java 类 ResourceSpec 使用 cpuCores。
- **风险**: 当前无影响（resource-spec.xdef 未生成模型代码且未被使用），但若激活会导致属性名不匹配。
- **建议**: 修正为 cpuCores。
- **误报排除**: 虽然当前休眠，但作为 schema 定义文件中的拼写错误应被修正以防未来问题。
- **复核状态**: 未复核

### [维度10-03] Missing xmlns:xdef namespace declaration in resource-spec.xdef

- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/resource-spec.xdef`
- **严重程度**: P3
- **现状**: 文件使用 xdef:unique-attr 但未声明 xmlns:xdef="/nop/schema/xdef.xdef"。对比 pattern.xdef 和 stream.xdef 均正确声明了命名空间。
- **风险**: 独立加载时可能导致 XML 解析错误。当前可能通过从 stream.xdef 的继承解析，暂不影响运行。
- **建议**: 添加命名空间声明。
- **误报排除**: 虽然当前休眠，但作为 XDef schema 文件的语法缺陷应被修正。
- **复核状态**: 未复核

### 已验证正确的项目

pattern.xdef 的 xdef:bean-package、xdef:bean-sub-type-prop、多态子类型处理、枚举引用、生成代码与 schema 一致性均正确无误。
