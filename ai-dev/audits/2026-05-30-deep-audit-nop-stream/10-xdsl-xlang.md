# 维度 10：XDSL 与 XLang 正确性

## 第 1 轮（初审）

### [维度10-01] CepPatternBuilder 中 instanceof 检查错误导致 group 子模式处理为死代码

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:65-72`
- **证据片段**:
  ```java
  CepPatternPartModel nextModel = groupModel.requirePart(next); // 行65
  FollowKind followKind = partModel.getFollowKind();             // 行66
  // 行68 — BUG: instanceof 检查始终为 true
  if (nextModel instanceof CepPatternPartModel) {                // 行68
      pattern = buildFollow(pattern, followKind, nextModel.getName()); // 行69
  } else {                                                        // 行70
      // 死代码：永远不会执行
      pattern = buildFollowGroup(pattern, followKind, (CepPatternGroupModel) nextModel); // 行71
  }
  ```
- **严重程度**: P0
- **现状**: `requirePart()` 返回类型为 `CepPatternPartModel`。`CepPatternSingleModel` 和 `CepPatternGroupModel` 都继承自 `CepPatternPartModel`，因此 `nextModel instanceof CepPatternPartModel` **始终为 true**，else 分支（`buildFollowGroup`）是死代码。

  **对比同文件第 43 行的正确模式**：
  ```java
  // 第 43 行 — 正确: 区分 single 和 group
  if (partModel instanceof CepPatternSingleModel) {
      pattern = Pattern.begin(start, getAfterMatchSkipStrategy(groupModel));
      pattern = buildSinglePattern(pattern, (CepPatternSingleModel) partModel);
  } else {
      pattern = buildGroupPattern((ICepPatternGroupModel) partModel);
      pattern = Pattern.begin(pattern, getAfterMatchSkipStrategy(groupModel));
  }
  ```

  **影响**：当一个 group 子模式（`CepPatternGroupModel`）出现在 pattern 序列中间时（如 `<single next="g"/><group name="g">...</group>`），`buildFollowGroup` 永远不会被调用。group 被错误地当作 single 处理，仅注册了名称而丢失了内部 pattern 步骤。
- **风险**:
  1. 任何包含中间 group 步骤的 CEP pattern XML 模型在运行时会产生错误的 NFA
  2. pattern 的 `<group>` 子元素在序列中间被静默忽略，无错误提示
  3. 现有测试仅覆盖 `CepPatternSingleModel`，未发现此问题
- **建议**: 将第 68 行改为 `if (nextModel instanceof CepPatternSingleModel)`，与第 43 行保持一致。补充针对 `CepPatternGroupModel` 在序列中间的测试用例。
- **信心水平**: 确定
- **误报排除**: 这是可证明的逻辑错误——类型继承关系由 xdef 的 `xdef:bean-sub-type-prop="type"` 确定。同文件第 43 行使用了正确的 `instanceof CepPatternSingleModel` 检查，第 68 行属于复制粘贴遗漏。
- **复核状态**: 已保留（独立复核确认 P0 成立）

## 维度复核结论

独立复核已确认此 P0 发现：
1. `CepPatternSingleModel` 和 `CepPatternGroupModel` 都继承自 `CepPatternPartModel`，继承链无歧义
2. `requirePart()` 返回 `CepPatternPartModel`，`instanceof CepPatternPartModel` 永远为 true
3. else 分支（`buildFollowGroup`）确实是死代码
4. 当 group 子模式出现在序列中间时，会导致 NFA 编译错误

## XDSL 相关背景

nop-stream 中不存在 XDSL 实例文件（无 XML 文件使用 `x:schema` 引用 xdef schema）。CEP pattern 模型通过 Java API 编程式构建。`pattern.xdef` 定义在 `nop-kernel/nop-xdefs` 中，与 `_gen/` 下的生成类完全一致。SPI 文件路径正确。

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 10-01 | P0 | CepPatternBuilder.java:68 | instanceof CepPatternPartModel 始终为 true，group 子模式处理为死代码 |
