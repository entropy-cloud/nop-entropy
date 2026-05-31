# 维度 16：测试覆盖 + 维度 17：代码风格 + 维度 21：单元测试有效性

## 第 1 轮（初审）

### [维度21-01] TestCacheEviction 名实不符，零保护力

- **文件**: `nop-code-flow/.../TestCacheEviction.java:18-29`
- **证据片段**:
  ```java
  void testFlowDetectorEvictionAfterMaxEntries() {
      FlowDetector detector = new FlowDetector();
      for (int i = 0; i < 25; i++) {
          detector.invalidateCache("idx_" + i);  // 从未填充缓存
      }
      assertTrue(detector.listFlows("idx_0").isEmpty());  // 当然为空
  }
  ```
- **严重程度**: P2
- **现状**: 从未调用 detectFlows() 填充缓存，测试名承诺验证驱逐但实际不涉及。保护力为零。
- **风险**: 驱逐逻辑完全删掉测试仍通过。
- **建议**: 重写测试：先填充缓存超过 maxEntries，再验证 LRU 驱逐行为。
- **信心水平**: 确定
- **误报排除**: 命中反模式 P-8（无效的负面测试）。
- **复核状态**: 未复核

### [维度21-02] TestDocKeywordExtractor 截断测试未测试截断

- **文件**: `nop-code-graph/.../TestDocKeywordExtractor.java:86-100`
- **证据片段**: 5500 个符号各只有 1 个关键词，断言 isEmpty()——因为关键词不足（<2），不是截断。
- **严重程度**: P2
- **现状**: 测试名是截断但实际测试的是"单关键词不产生边"。保护力为零。
- **建议**: 创建 >MAX_SYMBOLS 个符号，每个含 >=2 个关键词，验证截断行为。
- **信心水平**: 确定
- **误报排除**: 命中反模式 P-8。
- **复核状态**: 未复核

### [维度16-01] CriticalNodeAnalyzer / KnowledgeGapAnalyzer 覆盖极薄

- **文件**: `TestCriticalNodeAnalyzer.java`（37行，2个测试），`TestKnowledgeGapAnalyzer.java`（28行，1个测试）
- **严重程度**: P3
- **现状**: CriticalNodeAnalyzer 仅测空图和单节点自环。KnowledgeGapAnalyzer 仅测空图返回空。无法区分正确实现与错误实现。
- **建议**: 增加多节点枢纽检测、桥检测、阈值行为等测试。
- **信心水平**: 确定
- **误报排除**: 这两个分析器是核心图分析能力。
- **复核状态**: 未复核

### [维度16-02] GraphDiffer 缺少边/社区差异测试

- **文件**: `TestGraphDiffer.java`（62行，3个测试）
- **严重程度**: P3
- **现状**: 仅测节点新增和删除，缺少边差异、社区归属变化测试。
- **建议**: 补充边和社区差异测试。
- **信心水平**: 确定
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度17-01] 全部测试文件 import 分组顺序系统性违反约定

- **文件**: nop-code 下全部测试文件（约 60 个）
- **证据片段**: 实际顺序 `io.nop.* → jakarta.* → third-party → java.*`，与约定 `java.* → jakarta.* → third-party → io.nop.*` 完全相反。主代码文件正确。
- **严重程度**: P3
- **现状**: 测试文件 import 顺序系统性违反约定。
- **建议**: 使用 IDE 自动排序修复。
- **信心水平**: 确定
- **误报排除**: 主代码文件正确，仅测试文件有问题。
- **复核状态**: 未复核

### [维度21-03] TestNopCodeSymbolBizModel.testFindReferencedBy 仅 assertNotNull

- **文件**: `TestNopCodeSymbolBizModel.java:257-261`
- **证据片段**: `assertNotNull(refs)` — 唯一断言。
- **严重程度**: P3
- **现状**: 核心 API 测试仅验证非 null，不验证内容。
- **建议**: 至少验证返回的引用包含已知引用。
- **信心水平**: 确定
- **误报排除**: 命中反模式 P-5（过度使用 assertNotNull）。
- **复核状态**: 未复核

### [维度21-04] 反射测试私有方法（computeCohesion, pathMatchesQualifiedName）

- **文件**: `TestCohesionConsistency.java:59-61`, `TestChangeAnalyzerPathMatching.java:49-57`
- **证据片段**: `getDeclaredMethod("computeCohesion", ...).setAccessible(true)`
- **严重程度**: P3
- **现状**: 测试直接耦合到私有方法签名。重命名即断。
- **建议**: 通过公共 API 验证内聚值。
- **信心水平**: 确定
- **误报排除**: 命中反模式 P-4。
- **复核状态**: 未复核
