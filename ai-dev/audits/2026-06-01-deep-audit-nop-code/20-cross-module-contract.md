# 维度 20：跨模块契约一致性

## 第 1 轮（初审）

### [维度20-01] ICodeIndexService 接口依赖 nop-code-flow 模块的具体返回类型

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java:8-10`
- **证据片段**:
  ```java
  import io.nop.code.flow.ChangeAnalysisResult;
  import io.nop.code.flow.DeadCodeReport;
  import io.nop.code.flow.ExecutionFlow;
  ```
- **严重程度**: P2
- **现状**: ICodeIndexService（service API 层）直接 import 了 nop-code-flow 的具体类型，引入反向依赖。
- **风险**: 如果 flow 模块修改这些类结构，即使接口签名没变也会导致编译失败。
- **建议**: 在 API 层定义 DTO 接口或将 flow 相关方法抽到独立接口。
- **信心水平**: 很可能
- **误报排除**: 不是"看起来不优雅"，是编译级别的模块耦合。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 20-01 | P2 | ICodeIndexService.java:8-10 | 接口依赖 flow 模块具体类型，引入反向依赖 |
