# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] ImpactAnalyzer 使用 String[] 替代已有 BfsNode 类型

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/impact/ImpactAnalyzer.java:238-269`
- **证据片段**:
```java
Queue<String[]> queue = new LinkedList<>();
queue.add(new String[]{startId, "0"});
String[] current = queue.poll();
String nodeId = current[0];
int depth = Integer.parseInt(current[1]);
```
- **严重程度**: P2
- **现状**: 使用 String[] 作为 (nodeId, depth) pair，通过 Integer.parseInt 还原深度值。项目已有 BfsNode record。
- **建议**: 改为 Queue<BfsNode>。
- **信心水平**: 高
- **复核状态**: 未复核

### [维度15-02] Community.getSymbolIds() 返回不可变列表，后续 add 将抛异常

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/community/CommunityDetector.java:536-556`
- **严重程度**: P2
- **现状**: getSymbolIds() 在 symbolIds 为 null 时返回 Collections.emptyList()（不可变），但 recursiveSplit 中调用 .add(node)。
- **建议**: 改为返回 new ArrayList<>() 或确保 symbolIds 永远不为 null。
- **信心水平**: 高
- **复核状态**: 未复核

### [维度15-03] SpringEventSynthesizer 未抑制 unchecked cast 警告

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/heuristic/SpringEventSynthesizer.java:99-103`
- **严重程度**: P3
- **复核状态**: 未复核

### [维度15-04] KnowledgeGapResult.IsolatedSymbol.kind 使用 String 而非枚举

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/knowledge/KnowledgeGapResult.java:24-60`
- **严重程度**: P3
- **建议**: 改为 CodeSymbolKind 枚举类型。
- **复核状态**: 未复核

### [维度15-05] GraphDiff 全文件使用全限定类名而非 import

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/diff/GraphDiff.java:1-49`
- **严重程度**: P3
- **建议**: 统一使用 import 语句。
- **复核状态**: 未复核

### [维度15-06] GraphSnapshot.EdgeKey.equals() 未使用 Objects.equals

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/diff/GraphSnapshot.java:53-57`
- **严重程度**: P3
- **建议**: 使用 Objects.equals 避免 NPE。
- **复核状态**: 未复核

### [维度15-07] ExtDataHelper.getAnnotations @SuppressWarnings 作用域过大

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/util/ExtDataHelper.java:40-63`
- **严重程度**: P3
- **复核状态**: 未复核

### [维度15-08] CodeIndexService.getIndexStats 使用弱类型 Map

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:559-567`
- **严重程度**: P3
- **现状**: 框架 API 限制，null 处理已到位。
- **复核状态**: 未复核

## 维度复核结论

（待复核）
