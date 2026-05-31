# 维度 10：XDSL 与 XLang 正确性

## 第 1 轮（初审）

### [维度10-01] CepPatternBuilder: 非起始步骤的 where/until 条件被静默丢弃

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:69-70`
- **证据片段**:
  ```java
  // 第44-46行：起始步骤正确调用 buildSinglePattern
  if (partModel instanceof CepPatternSingleModel) {
      pattern = Pattern.begin(start, getAfterMatchSkipStrategy(groupModel));
      pattern = buildSinglePattern(pattern, (CepPatternSingleModel) partModel);
  }
  // 第69-70行：后续步骤仅调用 buildFollow，缺失 buildSinglePattern
  if (nextModel instanceof CepPatternSingleModel) {
      pattern = buildFollow(pattern, followKind, nextModel.getName());
  }
  ```
- **严重程度**: P2
- **现状**: buildSinglePattern() 在起始步骤（第46行）被正确调用，负责将 where 和 until 属性应用到 Pattern 对象。但在 do-while 循环中处理后续步骤时（第69-70行），只调用了 buildFollow()，从未调用 buildSinglePattern()，导致非起始步骤的 where/until 条件永远不会被应用。
- **影响**: 当通过 XDSL 模型定义 CEP 模式时，仅第一步的过滤条件生效。后续步骤的 where 条件被静默忽略，Pattern 将无条件匹配任何事件。
- **建议**: 在第70行之后添加对 buildSinglePattern 的调用。
- **信心水平**: 确定
- **误报排除**: 已对照 pattern.xdef 确认 single 元素定义了 where 和 until 子元素，但 CepPatternBuilder 对非起始步骤未使用这些字段。
- **复核状态**: 已保留（独立复核确认）

### [维度10-02] resource-spec.xdef: 缺少 xmlns:xdef 命名空间声明

- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/resource-spec.xdef`
- **证据片段**:
  ```xml
  <resource-spec x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef"
                 cpuCores="decimal" taskOffHeapMemory="decimal" managedMemory="decimal">
      <extend-resource name="!string" xdef:unique-attr="name" value="decimal"/>
  </resource-spec>
  ```
- **严重程度**: P3
- **现状**: 使用了 xdef:unique-attr 但根元素未声明 xmlns:xdef 命名空间。
- **风险**: 独立加载此 xdef 文件时可能导致 XML 命名空间解析错误。当前通过 stream.xdef 的 xdef:ref 引用，可能继承了命名空间声明。
- **建议**: 添加 xmlns:xdef="/nop/schema/xdef.xdef" 声明。
- **信心水平**: 很可能
- **误报排除**: pattern.xdef 和 stream.xdef 均正确声明了该命名空间。
- **复核状态**: 未复核

### [维度10-03] XDSL 反序列化路径零测试覆盖

- **文件**: `nop-stream/nop-stream-cep/src/test/` 目录整体
- **严重程度**: P3
- **现状**: 所有测试通过 Java 代码直接构造 CepPatternModel，没有任何测试通过 XDSL 框架解析 XML 文件。
- **风险**: 如果 xdef 定义与 Java 模型之间存在映射问题，现有测试无法发现。
- **建议**: 添加至少一个测试从 XML 解析 pattern 模型。
- **信心水平**: 确定
- **误报排除**: CepPatternBuilder 的代码路径测试存在，但 XDSL 反序列化路径确实未覆盖。
- **复核状态**: 未复核

## 维度复核结论

| 编号 | 判定 | 原因 |
|------|------|------|
| 10-01 | **保留 P2** | 已确认功能性 bug，buildSinglePattern 确实只在起始步骤被调用 |
| 10-02 | 保留 P3 | 命名空间声明确实缺失 |
| 10-03 | 保留 P3 | XML 解析路径确实无测试覆盖 |

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 10-01 | **P2** | CepPatternBuilder.java:69-70 | 非起始步骤 where/until 条件被静默丢弃 |
| 10-02 | P3 | resource-spec.xdef | 缺少 xmlns:xdef 声明 |
| 10-03 | P3 | 测试目录 | XDSL XML 反序列化零测试覆盖 |
