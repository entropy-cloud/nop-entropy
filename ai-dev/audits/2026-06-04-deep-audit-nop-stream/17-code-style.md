# 维度 17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] WindowOperator.java 含重复/废弃 Javadoc 块

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:670-679`
- **严重程度**: P3
- **证据片段**:
  ```java
  // line 670-676: 废弃的 Javadoc（描述 drops all state）
  /**
   * Drops all state for the given window and calls {@link Trigger#clear(...)}.
   */
  // line 677-679: 实际方法的 Javadoc
  /**
   * Emits the contents of the given window using the {@link InternalWindowFunction}.
   */
  ```
- **建议**: 删除 670-676 行废弃 Javadoc。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度17-02] WindowOperator.java 含未使用的 import

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:39,41`
- **严重程度**: P3
- **现状**: `AggregateFunction`（第 41 行）和 `TaskStateSnapshot`（第 39 行）从未使用。
- **建议**: 删除未使用 import。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度17-03] 多文件含冗余的非 static import + static import 同一类

- **文件**: 
  - `WindowAggregationOperator.java:17-18` (NopStreamErrors)
  - `NFA.java:52-53` (NopCepErrors)
- **严重程度**: P3
- **现状**: 同时导入了 `NopStreamErrors`（非 static）和 `static NopStreamErrors.*`。非 static 未使用。
- **建议**: 删除非 static import。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度17-04] GraphModelCheckpointExecutor.java 使用 FQN 而非 import

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:159-171,521`
- **严重程度**: P3
- **证据片段**:
  ```java
  io.nop.stream.core.graph.StreamGraphGenerator graphGenerator = new io.nop.stream.core.graph.StreamGraphGenerator();
  java.util.List<io.nop.stream.core.transformation.Transformation<?>> sinkList = new java.util.ArrayList<>();
  ```
- **建议**: 添加 import 使用短名。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度17-05] 主源码使用通配符 import（10+ 文件）

- **文件**: WindowAggregationOperator.java, CheckpointPlan.java, PartitionedPlanGenerator.java, JdbcCheckpointStorage.java 等
- **严重程度**: P3
- **现状**: 使用 `import java.util.*;` 通配符，代码审查时难以确定实际依赖。
- **建议**: 展开通配符 import。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度17-06] LOG 字段声明风格不一致

- **文件**: WindowOperator.java, AbstractStreamOperator.java (FQN) vs WindowAggregationOperator.java (import)
- **严重程度**: P3
- **现状**: 部分文件用完全限定名声明 LOG，其他通过 import 使用短名。
- **建议**: 统一使用 import + 短名风格。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度17-07] WindowOperator.java 空的 else 块

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:571-572,628-629`
- **严重程度**: P3
- **证据片段**:
  ```java
  if (stateWindow == null) {
      return;
  } else {
  }
  ```
- **建议**: 移除空 else 块。
- **信心水平**: 确定
- **复核状态**: 未复核

## 维度复核结论

待复核。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 17-01 | P3 | WindowOperator.java | 废弃 Javadoc 块 |
| 17-02 | P3 | WindowOperator.java | 未使用 import |
| 17-03 | P3 | WindowAggregationOperator.java, NFA.java | 冗余非 static import |
| 17-04 | P3 | GraphModelCheckpointExecutor.java | FQN 替代 import |
| 17-05 | P3 | 10+ 文件 | 通配符 import |
| 17-06 | P3 | 多文件 | LOG 声明风格不一致 |
| 17-07 | P3 | WindowOperator.java | 空 else 块 |
