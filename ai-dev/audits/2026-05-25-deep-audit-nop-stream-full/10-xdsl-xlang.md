# 维度 10：XDSL 与 XLang 正确性

## 第 1 轮（初审）

### 检查范围

| 文件 | 检查内容 |
|------|---------|
| `nop-stream-cep/precompile/gen-cep-xdsl.xgen` | 代码生成脚本 |
| `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/pattern.xdef` | XDef 定义文件 |
| `nop-stream-cep/src/main/java/io/nop/stream/cep/model/_gen/*.java` | 4 个生成类 |
| `nop-stream-cep/src/main/java/io/nop/stream/cep/model/CepPattern*.java` | 4 个手写扩展类 |
| `nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java` | 消费模型类的核心逻辑 |
| `nop-stream-cep/src/main/java/io/nop/stream/cep/NopCepErrors.java` | 错误码定义 |

---

### [维度10-01] CepPatternBuilder 中 `instanceof CepPatternPartModel` 恒为 true，导致 group 模式的 follow 分支为死代码

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:64-71`
- **证据片段**:
  ```java
  CepPatternPartModel nextModel = groupModel.requirePart(next);
  FollowKind followKind = partModel.getFollowKind();
  
  if (nextModel instanceof CepPatternPartModel) {     // 始终为 true
      pattern = buildFollow(pattern, followKind, nextModel.getName());
  } else {
      pattern = buildFollowGroup(pattern, followKind, (CepPatternGroupModel) nextModel);  // 死代码
  }
  ```
- **严重程度**: P0
- **现状**: `requirePart()` 返回类型声明为 `CepPatternPartModel`，`CepPatternSingleModel` 和 `CepPatternGroupModel` 均继承自它。因此 `instanceof CepPatternPartModel` 恒为 true，`else` 分支（`buildFollowGroup`）永远不会执行。
- **风险**: 当 pattern XML 中 part 后面跟着 `<group>` 类型子模式时，group 内部的子模式被完全忽略，NFA 构建的语义与 XML 定义不符。
- **建议**: 将 `instanceof CepPatternPartModel` 改为 `instanceof CepPatternSingleModel`，与第 42 行的分支逻辑保持一致。
- **误报排除**: 对比同一文件第 42 行 `instanceof CepPatternSingleModel` 的正确写法，第 67 行明显是笔误。
- **复核状态**: 未复核

---

### [维度10-02] CepPatternBuilder 在整个代码库中无任何引用，XDSL 模型到 Pattern 的转换路径从未被调用

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:26-28`
- **证据片段**:
  ```java
  public class CepPatternBuilder {
      public Pattern buildFromModel(CepPatternModel patternModel) {
          Pattern pattern = buildGroupPattern(patternModel);
  ```
  全项目搜索 `CepPatternBuilder` 仅在此文件自身找到类声明，无任何调用方。
- **严重程度**: P2
- **现状**: XDSL 模型到运行时 Pattern 的转换桥梁完全没有消费者和测试覆盖。
- **风险**: P0 级 instanceof bug（维度10-01）从未被测试捕获。整条 XDSL 模型链路可能是半成品。
- **建议**: 为 `buildFromModel` 添加测试覆盖；如为未完成功能，标注 `@Deprecated`。
- **误报排除**: 核心转换路径无消费者，且其中存在 P0 级 bug。
- **复核状态**: 未复核

---

### [维度10-03] 生成类中 `_type` 字段的 setType/getType 被手写子类硬编码遮蔽

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/model/_gen/_CepPatternSingleModel.java:42-56` 和 `CepPatternSingleModel.java:17-19`
- **证据片段**:
  ```java
  // 生成代码
  public void setType(java.lang.String value){ this._type = value; }
  
  // 手写覆盖
  public String getType() { return "single"; }
  ```
- **严重程度**: P2
- **现状**: 手写类的 `getType()` 返回硬编码常量，忽略 `_type` 字段的值。`setType()` 写入的值成为"幽灵状态"。
- **风险**: `cloneInstance` 链路中 `setType(this.getType())` 设置的值与实际不一致。序列化往返可能不一致。
- **建议**: 不覆盖 `getType()` 而依赖框架 `setType()` 设置正确值，或同时覆盖 `setType()` 为 no-op。
- **误报排除**: 涉及 XDSL 框架的 `bean-sub-type-prop` 机制与 Java 继承覆盖之间的语义冲突。
- **复核状态**: 未复核

---

## 深挖第 2 轮追加

### [维度10-04] buildSinglePattern 仅对 start 部分调用——后续单模式的 where/until 条件被静默丢弃 (P0)
### [维度10-05] until 在 oneOrMore 之前调用——同时定义两者时必然抛出 MalformedPatternException (P1)
### [维度10-06] IntRangeBean 的 offset/limit 语义与 Pattern.times() 的 min/max 语义不匹配 (P1)
### [维度10-07] buildFollow 的 switch 无 default 分支——新增 FollowKind 枚举值时静默跳过 (P2)

详见子 agent 原始输出。每个发现均包含完整证据片段、严重程度、现状、风险、建议、误报排除。

深挖结束，共新增 4 个发现（P0×1, P1×2, P2×1）。CepPatternBuilder 存在严重功能缺陷。

---

## 维度复核结论

| 编号 | 复核结论 | 理由 |
|------|---------|------|
| 10-01 | **保留 P0** | instanceof CepPatternPartModel 恒为 true，功能性致命缺陷 |
| 10-02 | **保留 P2** | 全仓库零引用确认 |
| 10-03 | **保留 P2** | 字段遮蔽确实存在 |
| 10-04 | **保留 P0** | 后续 part 的 where/until 条件完全丢失 |
| 10-05 | **保留 P1** | until/oneOrMore 执行顺序错误 |
| 10-06 | **保留 P1** | IntRangeBean 语义与 times 期望不匹配 |
| 10-07 | **保留 P2** | 防御性编程缺失 |
