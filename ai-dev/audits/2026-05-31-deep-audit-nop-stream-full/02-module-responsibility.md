# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-02] WindowAggregationOperator 混合算子逻辑与序列化（P2）

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java` (834行)
- **现状**: ~180 行序列化逻辑可提取为独立类

### [维度02-06] GraphModelCheckpointExecutor 编排逻辑集中（P2）

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java` (807行)
- **现状**: 两个大型 public static 方法各自承担完整编排流程

### [维度02-01] 空模块占位符缺乏实现路线图（P3）

- **文件**: api, checkpoint, flow, flink 的 pom.xml
- **现状**: 仅有一行 "placeholder" 注释

### [维度02-03] WindowOperator onEventTime/onProcessingTime 代码重复（P3）

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java` (1099行)
- **现状**: 两个方法几乎完全相同

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 02-02 | P2 | WindowAggregationOperator.java | 混合算子与序列化逻辑 |
| 02-06 | P2 | GraphModelCheckpointExecutor.java | 编排逻辑过于集中 |
| 02-01 | P3 | 空模块 pom.xml | 缺乏实现路线图 |
| 02-03 | P3 | WindowOperator.java | onEventTime/onProcessingTime 重复 |
