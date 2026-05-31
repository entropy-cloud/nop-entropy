# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] TestCriticalNodeAnalyzer 和 TestKnowledgeGapAnalyzer 测试过于薄弱

- **文件**: `nop-code/nop-code-graph/src/test/java/io/nop/code/graph/critical/TestCriticalNodeAnalyzer.java:12-36`
- **证据片段**:
  ```java
  @Test
  void testConstructionAndAnalyzeWithEmptyGraph() {
      CriticalNodeAnalyzer analyzer = new CriticalNodeAnalyzer();
      CallGraph callGraph = new CallGraph();
      SymbolTable symbolTable = new SymbolTable();
      CriticalNodeResult result = analyzer.analyze(callGraph, symbolTable, 5);
      assertNotNull(result);
      assertEquals(0, result.getTotalNodes());
  }
  ```
- **严重程度**: P2
- **现状**: 只有空图和单节点自环测试，核心算法（中心度计算、桥节点检测）完全没有被验证。
- **风险**: 核心算法 bug 无法被捕获。
- **建议**: 构造 5+ 节点图验证 hubNodes 和 bridgeNodes。
- **信心水平**: 确定
- **误报排除**: 空图测试对算法逻辑无保护力。
- **复核状态**: 未复核

### [维度16-02] TestGraphDiffer 缺少边变更测试

- **文件**: `nop-code/nop-code-graph/src/test/java/io/nop/code/graph/diff/TestGraphDiffer.java:17-61`
- **严重程度**: P2
- **现状**: 只测了节点增删，GraphDiffer 的 `getAddedEdges()`/`getRemovedEdges()` 从未被验证。
- **建议**: 增加边级别差异检测测试。
- **信心水平**: 确定
- **误报排除**: getAddedEdges/getRemovedEdges 是公共 API 但无测试覆盖。
- **复核状态**: 未复核

### [维度16-03] TestCacheEviction 测试逻辑无效——从不会命中缓存

- **文件**: `nop-code/nop-code-flow/src/test/java/io/nop/code/flow/TestCacheEviction.java:17-29`
- **证据片段**:
  ```java
  @Test
  void testFlowDetectorEvictionAfterMaxEntries() {
      FlowDetector detector = new FlowDetector();
      for (int i = 0; i < 25; i++) {
          detector.invalidateCache("idx_" + i);
      }
      assertTrue(detector.listFlows("idx_0").isEmpty());
  }
  ```
- **严重程度**: P1
- **现状**: 只调用了 invalidateCache，从未调用 detectFlows 填充缓存。测试无法区分"驱逐正确"和"缓存从未填充"。
- **风险**: 驱逐逻辑如果有 bug 也不会被发现。
- **建议**: 先 detectFlows 填充 25+ 缓存条目，再验证超 maxEntries 后旧条目被驱逐。
- **信心水平**: 确定
- **误报排除**: 测试名称是"eviction"但实际未测试驱逐行为。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 16-01 | P2 | TestCriticalNodeAnalyzer.java | 仅测空图，核心算法未覆盖 |
| 16-02 | P2 | TestGraphDiffer.java | 缺少边变更测试 |
| 16-03 | P1 | TestCacheEviction.java | 测试逻辑无效，从未填充缓存 |
