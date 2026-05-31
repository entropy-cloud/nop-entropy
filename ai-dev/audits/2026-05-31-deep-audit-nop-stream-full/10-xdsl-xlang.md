# 维度 10：XDSL 与 XLang 正确性

## 第 1 轮（初审）

### [维度10-01] resource-spec.xdef 缺少 xmlns:xdef 命名空间声明

- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/resource-spec.xdef:1-3`
- **证据片段**:
  ```xml
  <resource-spec x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef"
                 cpuCores="decimal" taskHeapMemory="decimal" taskOffheapMemory="decimal" managedMemory="decimal">
      <extend-resource name="!string" xdef:unique-attr="name" value="decimal"/>
  </resource-spec>
  ```
- **严重程度**: P1
- **现状**: 文件使用 `xdef:unique-attr="name"` 但从未声明 `xmlns:xdef="/nop/schema/xdef.xdef"`。xdef: 前缀未声明。
- **风险**: XML 解析器在强制命名空间规范时会拒绝此文件。`xdef:unique-attr` 语义会丢失，`<extend-resource>` 元素不会被正确去重。
- **建议**: 添加 `xmlns:xdef="/nop/schema/xdef.xdef"` 到根元素，与 pattern.xdef、stream.xdef 的模式一致。
- **信心水平**: 确定
- **误报排除**: 项目中 88+ 个 xdef 文件中，其他所有使用 xdef: 前缀属性的文件都声明了 xmlns:xdef。此文件是唯一例外。
- **复核状态**: 未复核

### [维度10-02] stream.xdef / resource-spec.xdef 未接入代码生成管线

- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:1-15`
- **严重程度**: P2
- **现状**: stream.xdef 定义了 StreamNode schema，但无 precompile .xgen 脚本、无生成 Java 模型类。手写的 StreamNode 不继承 AbstractComponentModel，字段类型与 xdef 不一致（xdef 定义 id 为 String，手写为 int）。
- **风险**: xdef 定义可能与手写代码漂移不同步。如果用户创建引用 stream.xdef 的 XDSL 实例，模型对象与手写 StreamNode 不匹配。
- **建议**: 添加 gen-stream-xdsl.xgen 脚本并用手写代码替代，或标记 stream.xdef/resource-spec.xdef 为未激活。
- **信心水平**: 很可能
- **误报排除**: 搜索了所有 .xgen 文件（仅 gen-cep-xdsl.xgen），所有 _gen/*.java（仅 CepPattern*），所有 Java 中对 stream.xdef 的引用（零）。
- **复核状态**: 未复核

### [维度10-03] CEP pattern 无 XDSL 实例文件 — 模型仅通过 Java API 构造

- **文件**: `nop-stream/nop-stream-cep/` (整个模块)
- **严重程度**: P3
- **现状**: pattern.xdef schema 完整且生成了正确的 Java 模型类，但无实际 XML pattern 定义文件。所有 CEP 模式通过 Java 代码构造。
- **风险**: XDSL XML 加载路径未被测试。用户通过 XML 定义 pattern 的路径未验证。
- **建议**: 添加至少一个测试验证从 XML 文件加载 CepPatternModel。
- **信心水平**: 确定
- **误报排除**: 搜索了所有 test/resources/（不存在）、所有 XML 文件（仅 pom.xml）、所有 XDSL 加载模式（零）。
- **复核状态**: 未复核

## 零发现检查项

| 检查项 | 结果 |
|--------|------|
| pattern.xdef 命名空间 | 正确 |
| xdef:ref 使用 | 正确 |
| 生成代码与 xdef 一致 | 是 |
| 枚举引用 | 正确 |
| 继承层次 | 正确 |
| precompile 脚本 | 正确 |
| Java 类路径 | 正确 |
| 错误码一致性 | 正确 |
| 不存在资源引用 | 无 |

## 维度复核结论

（待复核）

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 10-01 | P1 | kernel/xdefs/.../resource-spec.xdef | 缺少 xmlns:xdef 命名空间声明 |
| 10-02 | P2 | kernel/xdefs/.../stream.xdef | xdef 定义未接入代码生成管线 |
| 10-03 | P3 | nop-stream-cep/ | pattern.xdef 无 XDSL 实例文件测试 |
