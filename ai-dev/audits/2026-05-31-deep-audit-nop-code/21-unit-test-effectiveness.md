# Audit Dimension 21: Unit Test Effectiveness — nop-code

### [21-01] CallGraph.addEdge 允许重复边 — 无测试检测

- **File**: `nop-code-core/src/main/java/io/nop/code/core/graph/CallGraph.java:12-14`
- **Evidence Snippet**:
```java
public void addEdge(String caller, String callee) {
    forwardEdges.computeIfAbsent(caller, k -> new ArrayList<>()).add(callee);
    reverseEdges.computeIfAbsent(callee, k -> new ArrayList<>()).add(caller);
}
```
- **Severity**: P1
- **Current State**: addEdge 无条件追加，允许重复边。下游算法（CommunityDetector、ImpactAnalyzer、CriticalNodeAnalyzer）的边计数将被放大。无测试调用两次 addEdge("A","B") 并验证去重。
- **Risk**: 静默数据损坏——所有基于边计数的算法产生膨胀结果。
- **Recommendation**: 使用 LinkedHashSet 替代 ArrayList。
- **Confidence**: High
- **False Positive Exclusion**: ArrayList 数据结构明确，TestCallGraph 仅57行无重复边测试。
- **Review Status**: Not reviewed

---

### [21-02] ChangeAnalyzer 核心逻辑零测试覆盖

- **File**: `nop-code-flow/src/test/java/io/nop/code/flow/TestChangeAnalyzer.java:57-130`
- **Evidence Snippet**:
```java
ChangeAnalysisResult result = analyzer.analyzeChanges(
        "test-idx", "nonexistent~1", "nonexistent~2", st, cg);
```
- **Severity**: P1
- **Current State**: 所有5个测试使用不存在的 git refs，parseGitDiff 始终返回空 map。核心的 diff-to-symbol 映射逻辑（pathMatchesQualifiedName、overlapsAnyRange）从未被测试执行。
- **Risk**: 变更分析管道的核心逻辑零有效测试覆盖。
- **Recommendation**: 重构使 diff 解析可注入，用预构建的 diff 数据直接测试映射逻辑。
- **Confidence**: High
- **False Positive Exclusion**: 所有5个测试方法都使用 nonexistent~1/~2，parseGitDiff 必然返回空。
- **Review Status**: Not reviewed

---

### [21-03] TestCacheEviction 使用 assertTrue(true) — 零保护力

- **File**: `nop-code-flow/src/test/java/io/nop/code/flow/TestCacheEviction.java:24-26`
- **Evidence Snippet**:
```java
assertTrue(true, "Multiple invalidate calls should not throw");
```
- **Severity**: P1
- **Current State**: 永真断言，不验证任何缓存行为。
- **Risk**: 缓存驱逐逻辑损坏时不会被发现。
- **Recommendation**: 重写为填充缓存、验证驱逐。
- **Confidence**: High
- **False Positive Exclusion**: assertTrue(true) 字面量是无操作断言。
- **Review Status**: Not reviewed

---

### [21-04] GraphDiffer/CriticalNodeAnalyzer/KnowledgeGapAnalyzer 零测试文件

- **File**: `nop-code-graph/src/main/java/io/nop/code/graph/diff/GraphDiffer.java`, `critical/CriticalNodeAnalyzer.java`, `knowledge/KnowledgeGapAnalyzer.java`
- **Severity**: P1
- **Current State**: 3个生产类无专用测试文件。CriticalNodeAnalyzer 使用 BetweennessCentrality 算法但无验证。
- **Risk**: 图构造或分数提取 bug 不会被检测。
- **Recommendation**: 添加专用测试类。
- **Confidence**: High
- **False Positive Exclusion**: glob 搜索确认无匹配测试文件。
- **Review Status**: Not reviewed

---

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 摘要 |
|------|---------|---------|------|
| 21-01 | P1 | CallGraph.java + TestCallGraph.java | 允许重复边，无测试检测 |
| 21-02 | P1 | TestChangeAnalyzer.java | 核心逻辑零覆盖（使用不存在的 git refs） |
| 21-03 | P1 | TestCacheEviction.java | assertTrue(true) 零保护力 |
| 21-04 | P1 | GraphDiffer/CriticalNodeAnalyzer/KnowledgeGapAnalyzer | 零测试文件 |
