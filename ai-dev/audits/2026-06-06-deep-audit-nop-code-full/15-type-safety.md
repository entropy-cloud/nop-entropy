# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] tarjanSCC 使用 Object[] 模拟递归调用栈，缺乏类型安全

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java:725-732, 747-748`
- **证据片段**:
  ```java
  Deque<Object[]> callStack = new ArrayDeque<>();
  callStack.push(new Object[]{startNode, 0, false});
  String v = (String) frame[0];
  int edgeIdx = (Integer) frame[1];
  boolean returning = (Boolean) frame[2];
  ```
- **严重程度**: P3
- **现状**: `tarjanSCC()` 方法使用 `Object[]` 数组（长度 3）模拟递归调用栈帧，通过强制类型转换访问元素。
- **风险**: 如果数组元素顺序或类型意外变更，编译器不会报错，仅在运行时出现 ClassCastException。
- **建议**: 引入局部 record 替代 `Object[]`，如 `record TarjanFrame(String node, int edgeIdx, boolean returning) {}`。
- **信心水平**: 高
- **误报排除**: 这是真实的类型安全薄弱点，`Object[]` 完全可以用类型安全的 record 替代。
- **复核状态**: 未复核

### [维度15-02] JSON 动态解析使用 instanceof + unchecked cast 模式

- **文件**: ExtDataHelper.java, CodeQueryService.java, JavaFileAnalyzer.java, SpringEventSynthesizer.java（共约 8 处）
- **严重程度**: 信息级（可接受）
- **现状**: `JsonTool.parse()` 返回 `Object`，通过 `instanceof Map` 检查后 unchecked cast。这是 Nop 平台动态边界的标准模式。
- **建议**: 无需改动。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| [维度15-01] | P3 | CodeGraphService.java | tarjanSCC 用 Object[] 模拟栈帧缺乏类型安全 |
