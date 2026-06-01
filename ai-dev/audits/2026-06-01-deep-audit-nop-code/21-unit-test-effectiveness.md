# 维度21：单元测试有效性 -- nop-code 模块审计报告

## 第 1 轮（初审）

### [维度21-01] TestCodeIndexService 过度使用 assertNotNull

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestCodeIndexService.java`
- **证据片段**:
  ```java
  // testCallGraph()
  Object callGraph = response.getData();
  assertNotNull(callGraph);  // 仅检查非 null
  // testSymbolCount()
  assertTrue(stats.getSymbolCount() > 0);  // 仅检查 > 0
  ```
- **严重程度**: P2
- **命中反模式**: P-5（过度使用 assertNotNull）
- **现状**: 大部分测试方法使用 `assertNotNull` 或 `assertTrue(count > 0)` 模式。如果分析器只返回 1 个符号或空数据，大部分测试仍会通过。
- **风险**: 测试保护力弱，无法捕获语义错误。
- **建议**: 添加对预期符号数量、调用关系结构等的具体断言。
- **信心水平**: 85%
- **误报排除**: "改错验证"确认核心逻辑改错后测试仍通过。
- **复核状态**: 未复核

### [维度21-02] TestChangeAnalyzer 测试覆盖不足

- **文件**: `nop-code/nop-code-flow/src/test/java/io/nop/code/flow/TestChangeAnalyzer.java`
- **严重程度**: P2
- **命中反模式**: P-3（只测 happy path）+ P-5（过度使用 assertNotNull）
- **现状**: 测试使用 `nonexistent~1`/`nonexistent~2` 作为 git refs，git diff 总是失败/返回空，无法测试真正的变更分析逻辑。`testRiskScoringDimensionsPopulated` 只验证非 null。
- **风险**: 变更分析核心逻辑未被有效测试。
- **建议**: 使用真实的 git 变更数据或 mock GitService 来测试。
- **信心水平**: 85%
- **误报排除**: "改错验证"确认返回空结果时测试仍通过。
- **复核状态**: 未复核

## 正面发现

- **TestCriticalNodeAnalyzer**: 质量优秀，精确验证了 inDegree、outDegree、betweenness 等指标。
- **TestImpactAnalyzer**: 精确验证了特定深度的符号可达性。
- **TestDeadCodeDetector**: 包含 AR-68 回归测试系列，验证了注解读取修复。
- **TestGraphDiffer**: 精确验证了节点增删、边增删等变化检测。
- **TestCohesionConsistency**: 跨模块一致性检查，验证两个模块使用相同内聚度公式。
- **TestEntryPointScorer**: 精确验证了入口点得分计算。
