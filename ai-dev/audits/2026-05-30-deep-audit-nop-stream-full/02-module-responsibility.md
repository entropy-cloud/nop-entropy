# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] WindowAggregationOperator.java 混合窗口处理逻辑与自定义 JSON 序列化

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java:543-665`
- **证据片段**:
```java
// 行 543-559
private Map<String, Object> serializeWindowState(Map<WindowKey<K, W>, ACC> state) {
    // ... JsonTool.stringify(wk.key) + "#" + JsonTool.stringify(wk.window) ...
}

// 行 592-619
private void deserializeWindowState(Map<String, Object> data, Class<?> keyClass, Class<?> windowClass,
                                    Map<WindowKey<K, W>, ACC> target) throws Exception {
    // ... Class.forName(accType).getDeclaredConstructor().newInstance() ...
}
```
- **严重程度**: P2
- **现状**: 该文件（825 行）混合了窗口聚合核心逻辑与约 200 行自定义 JSON 序列化/反序列化逻辑（占 25%）。
- **风险**: 序列化格式变更时必须修改算子核心文件，维护耦合。
- **建议**: 将序列化方法提取到独立的 `WindowAggregationStateSerializer` 类中。
- **信心水平**: 确定
- **误报排除**: 200 行非核心逻辑与算子核心混合在同一文件中，职责不单一。
- **复核状态**: 未复核

---

### 无额外问题的检查项

| 检查项 | 结论 |
|--------|------|
| 其余 >500 行文件 | 职责单一，无混合（NFACompiler/NFA/Pattern/WindowOperator 等复杂度来源于领域逻辑） |
| 子模块边界 | 依赖方向正确，无越权 import |
| _gen/ 生成文件 | 无手写修改痕迹，4 个文件均为代码生成 |

---

## 维度复核结论

（待复核）

## 最终保留项

（待复核后填写）
