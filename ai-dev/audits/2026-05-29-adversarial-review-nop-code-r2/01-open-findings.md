# 对抗性审查报告（第 2 轮）：nop-code 模块

**审查日期**: 2026-05-29
**审查方法**: 开放式发现导向（adversarial review），无预设维度
**审查范围**: nop-code 全模块 14 个子模块，重点深挖 graph 算法、语言分析器、服务层事务/数据一致性
**去重基线**: `ai-dev/audits/2026-05-29-adversarial-review-nop-code/01-open-findings.md`（第 1 轮，AR-01 至 AR-27）、`2026-05-25-deep-audit-nop-code-full/`、`2026-05-29-deep-audit-nop-code-full/`
**启发式视角**: 新人开发者（语言分析器完整性）+ 异常路径侦探（BFS/数据一致性）+ 组合爆炸测试者（图算法边界）

---

## 发现总览

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 2 | TypeScript 调用分析全死(1)、反向依赖 BFS 永远深度 1(1) |
| P1 | 5 | 增量索引数据损坏(2)、缓存失效缺失(1)、流分析文件计数错误(1)、Python 嵌套函数不可见(1) |
| P2 | 9 | 注解边去重失败(1)、导出格式错误(2)、图算法边界问题(3)、服务层数据泄漏(3) |
| P3 | 4 | 导入解析缺陷(1)、Record 名字缺失(1)、pathMapper 重复应用(1)、Tarjan 递归栈溢出(1) |

---

## P0 发现

### [AR-28] TypeScript `walkNodeForCalls` 从未被调用——所有 TS 文件的调用分析结果始终为空

- **文件**: `nop-code-lang-typescript/src/main/java/io/nop/code/lang/typescript/analyzer/TypeScriptCodeFileAnalyzer.java:426-467,205-228,230-255`
- **证据片段**:
  ```java
  // walkNodeForCalls 定义在 line 426，只在 line 464 自递归调用
  private void walkNodeForCalls(TSNode node, String source,
                                CodeSymbol callerSymbol, CodeFileAnalysisResult result) {
      // ... 处理 call_expression 节点 ...
      walkNodeForCalls(child, source, callerSymbol, result);  // line 464: 仅自递归
  }

  // handleFunctionDeclaration (line 205-228) 和 handleMethodDefinition (line 230-255) 都:
  // 1. 创建 symbol
  // 2. processDecorators
  // 3. return —— 从不调用 walkNodeForCalls
  ```
- **严重程度**: P0
- **现状**: `walkNodeForCalls` 是死代码。它既不被 `handleFunctionDeclaration` 调用，也不被 `handleMethodDefinition`、`handleClassDeclaration` 或 `walkNode` 调用。TypeScript 分析产物的 `result.getCalls()` 始终为空列表。对比 Java 分析器（`visit(MethodCallExpr)` 由 visitor 自动触发）和 Python 分析器（`visitFunctionDefinition` 显式调用 `walkBlockForCalls`），TypeScript 是唯一不提取方法调用的语言适配器。
- **风险**: TypeScript 项目的调用图完全为空 → 调用层次查询、执行流追踪、关键节点分析、死代码检测在 TypeScript 项目上全部返回空结果或错误结果。
- **建议**: 在 `handleFunctionDeclaration` 和 `handleMethodDefinition` 的 symbol 创建后，获取 body 节点并调用 `walkNodeForCalls(body, source, symbol, result)`。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者（"TypeScript 和 Java 分析器做同一件事，但结果为什么不同？"）

---

### [AR-29] 反向依赖 BFS 始终访问 `edge.getTarget()`（即自身）——永远不会穿越超过深度 1

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1682-1701`
- **证据片段**:
  ```java
  // buildReverseAdjacency (line 1648-1665):
  // key = dep.getTargetFilePath()，即按 target 索引
  adj.computeIfAbsent(dep.getTargetFilePath(), k -> new ArrayList<>()).add(edge);

  // bfsCollect (line 1682-1701):
  // 从 start 节点 BFS，对每条边访问 edge.getTarget()
  List<DepEdgeDTO> edges = adj.getOrDefault(node, Collections.emptyList());
  for (DepEdgeDTO edge : edges) {
      result.add(edge);
      if (!visited.contains(edge.getTarget())) {   // ← edge.getTarget() == 当前节点自身
          visited.add(edge.getTarget());
          queue.add(new String[]{edge.getTarget(), ...});
      }
  }
  ```
- **严重程度**: P0
- **现状**: `buildReverseAdjacency` 按 `targetFilePath` 索引边，所以 `adj.get(B)` 返回所有 target=B 的边（即 A→B, C→B）。BFS 从 B 出发，遍历这些边，但下一步访问 `edge.getTarget()`，即 B 自身（已 visited）。BFS 永远不会走到 A 或 C。对正向 BFS（`buildForwardAdjacency`），`edge.getTarget()` 是正确的下一跳；但 `bfsCollect` 被两种邻接表复用，不区分方向。
- **风险**: `getReverseDeps(indexId, filePath, depth=5)` 只返回直接依赖者，depth 参数无效。下游循环依赖检测（`tarjanSCC` 用反向邻接表）可能漏报间接循环。
- **建议**: `bfsCollect` 需要感知遍历方向：正向时访问 `edge.getTarget()`，反向时访问 `edge.getSource()`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

## P1 发现

### [AR-30] `deleteFileRecords` 不删除 `NopCodeUsage` 记录——增量重索引后产生幽灵引用

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:2318-2339`
- **证据片段**:
  ```java
  private void deleteFileRecords(String indexId, List<?> filePaths) {
      // 删除了: NopCodeCall, NopCodeSymbol, NopCodeDependency,
      //         NopCodeAnnotationUsage, NopCodeInheritance
      deleteEntitiesByFilter(NopCodeCall.class, "fileId", fileId);
      deleteEntitiesByFilter(NopCodeSymbol.class, "fileId", fileId);
      deleteEntitiesByFilter(NopCodeDependency.class, "sourceFilePath", filePath);
      deleteRelationalBySymbolIds(NopCodeAnnotationUsage.class, "annotatedSymbolId", symbolIds);
      deleteRelationalBySymbolIds(NopCodeInheritance.class, "subTypeId", symbolIds);
      // ❌ 缺少: NopCodeUsage 的删除
  }
  ```
- **严重程度**: P1
- **现状**: `saveFileResultInSession` 为 CALLS、ANNOTATES、EXTENDS/IMPLEMENTS、TESTED_BY 关系创建 `NopCodeUsage` 实体。但增量索引路径调用 `deleteFileRecords` 时，`NopCodeUsage` 从未被清理。重新索引后，旧 usage 记录与新记录共存，`findReferencedBy` 返回已不存在符号的幽灵引用。
- **风险**: 每次增量索引累积孤儿 usage 行。长期运行的索引的数据质量持续退化。
- **建议**: 在 `deleteFileRecords` 中添加 `deleteEntitiesByFilter(NopCodeUsage.class, "fileId", fileId)` 或基于 symbolIds 删除。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-31] `indexFile` 从不调用 `invalidateAnalysisCache`——返回过时的图/层次数据

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:364-376`
- **证据片段**:
  ```java
  @Override
  public CodeFileAnalysisResult indexFile(String indexId, String filePath, String sourceCode) {
      // ... 分析 ...
      ormTemplate.runInSession(session -> {
          persistSingleFileInSession(indexId, result, session);
          return null;
      });
      return result;
      // ❌ 缺少: invalidateAnalysisCache(indexId)
  }
  ```
- **严重程度**: P1
- **现状**: `indexDirectory` 在开头调用 `invalidateAnalysisCache(indexId)` 使缓存失效，但 `indexFile`（单文件索引）不调用。索引新文件后，内存中的 `SymbolTable` 和 `CallGraph` 是过时的。后续调用 `getTypeHierarchy`、`getCallHierarchy`、`detectCommunities`、`getCriticalNodes`、`detectDeadCode` 等返回不包含新文件符号的旧数据。
- **风险**: 单文件索引后所有图分析结果不包含最新数据，但用户不知道。
- **建议**: 在 `indexFile` 的 `ormTemplate.runInSession` 之后添加 `invalidateAnalysisCache(indexId)`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-32] `FlowDetector.extractFileKey` 返回包名而非类名——fileSpread 因子始终为 0

- **文件**: `nop-code-flow/src/main/java/io/nop/code/flow/FlowDetector.java:373-390`
- **证据片段**:
  ```java
  private String extractFileKey(CodeSymbol symbol) {
      String qn = symbol.getQualifiedName();
      // 对 "com.example.UserService.save":
      int lastDot = qn.lastIndexOf('.');       // → "save" 前的 dot
      if (lastDot > 0) {
          int prevDot = qn.lastIndexOf('.', lastDot - 1);  // → "UserService" 前的 dot
          if (prevDot > 0) {
              return qn.substring(0, prevDot);  // → "com.example" ← 返回包名！
          }
      }
      return qn;
  }
  ```
- **严重程度**: P1
- **现状**: 对于 `"com.example.UserService.save"`，方法返回 `"com.example"`（包名）而非 `"com.example.UserService"`（类名）。同一包下所有类被计为一个"文件"。`computeCriticality` 中 `fileSpread = Math.min((fileCount - 1) / 4.0, 1.0)`，单包项目 fileCount=1，fileSpread=0，文件传播因子被完全抵消。
- **风险**: 流分析的关键性评分中 fileSpread 维度对所有非多包项目恒为 0。FlowStats.fileCount 数值不可信。
- **建议**: 返回 `qn.substring(0, lastDot)` 获取全限定类名。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-33] Python 嵌套函数/类定义在函数内不可见——`visitFunctionDefinition` 不调用 `walkBlockChildren`

- **文件**: `nop-code-lang-python/src/main/java/io/nop/code/lang/python/PythonCodeFileAnalyzer.java:210-213` vs `149-152`
- **证据片段**:
  ```java
  // visitClassDefinition (line 149-152) — 正确:
  TSNode block = getFirstChildByType(node, "block").orElse(null);
  if (block != null) {
      walkBlockChildren(block, source, modulePrefix, symbol, result);  // ✅ 递归发现子符号
  }

  // visitFunctionDefinition (line 210-213) — 缺失:
  TSNode block = getFirstChildByType(node, "block").orElse(null);
  if (block != null) {
      walkBlockForCalls(block, source, symbol, result);  // 只提取调用，不提取子符号
  }
  // ❌ 从不调用 walkBlockChildren → 嵌套 function_definition / class_definition 被跳过
  ```
- **严重程度**: P1
- **现状**: Python 支持在函数内定义函数和类（闭包、工厂模式、装饰器等），但 `visitFunctionDefinition` 只调用 `walkBlockForCalls` 提取调用关系，不调用 `walkBlockChildren` 提取子定义。所有函数内嵌套的函数和类完全不可见。
- **风险**: Python 闭包、嵌套类、工厂函数不会出现在索引中。影响代码搜索、符号浏览和死代码检测。
- **建议**: 在 `visitFunctionDefinition` 的 `walkBlockForCalls` 之后添加 `walkBlockChildren(block, source, modulePrefix, symbol, result)`。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

## P2 发现

### [AR-34] `AnnotationPatternExtractor` 无向边去重失败——不同注解产生语义重复边

- **文件**: `nop-code-graph/src/main/java/io/nop/code/graph/semantic/AnnotationPatternExtractor.java:66-89`
- **证据片段**:
  ```java
  List<String> symbolIds = new ArrayList<>(entry.getValue()); // HashSet → ArrayList，顺序不确定
  for (int i = 0; i < symbolIds.size(); i++) {
      for (int j = i + 1; j < symbolIds.size(); j++) {
          String edgeKey = id1 + "|" + id2;  // 顺序取决于迭代顺序
          if (seen.add(edgeKey)) {
              // edge.setDirected(false) ← 声明为无向
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: `symbolIds` 从 `HashSet` 转换而来，迭代顺序不确定。两个注解 A 和 B 共享符号 X、Y 时：注解 A 可能产生 key `"X|Y"`，注解 B 可能产生 key `"Y|X"`。两者都通过 `seen.add()` 检查，创建两条语义重复的无向边。
- **风险**: 下游图分析（社区检测、语义搜索）看到重复边，权重被不当放大。
- **建议**: 规范化 key 为 `min(id1,id2) + "|" + max(id1,id2)`。
- **信心水平**: 很可能
- **发现来源视角**: 组合爆炸测试者

---

### [AR-35] `AnnotationPatternExtractor` O(N²) 对生成无上限——广用注解可 OOM

- **文件**: `nop-code-graph/src/main/java/io/nop/code/graph/semantic/AnnotationPatternExtractor.java:70-89`
- **证据片段**:
  ```java
  for (int i = 0; i < symbolIds.size(); i++) {
      for (int j = i + 1; j < symbolIds.size(); j++) {
          // 无上限检查
          edges.add(edge);
      }
  }
  ```
- **严重程度**: P2
- **现状**: 如果 `@Inject` 标注了 5000 个符号，产生 `5000*4999/2 = 12,497,500` 条边。无采样、无上限、无警告。对比 `NameSimilarityExtractor` 有 5000 符号上限和 `DocKeywordExtractor`（AR-14 已报告），这里是第三个同类问题但未在之前的审计中被发现。
- **风险**: 大型项目的语义边提取阶段 OOM 或运行数小时。
- **建议**: 添加上限（如 5000 符号）或采样策略。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-36] `GraphExporter.escapeJson` 不完整——Tab 和控制字符产生无效 JSON

- **文件**: `nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java:257-260`
- **证据片段**:
  ```java
  private String escapeJson(String s) {
      if (s == null) return "";
      return s.replace("\\", "\\\\").replace("\"", "\\\"")
              .replace("\n", "\\n").replace("\r", "\\r");
      // 缺少: \t → \\t, \b, \f, U+0000-U+001F → \uXXXX
  }
  ```
- **严重程度**: P2
- **现状**: 方法名含 Tab 的函数（如 `\t` 在注释中泄漏到名称）导出后产生无效 JSON。RFC 8259 要求 U+0000 至 U+001F 必须转义。
- **风险**: JSON 导出文件无法被任何标准 JSON 解析器解析。
- **建议**: 使用 `JsonTool.stringify()` 或补充完整的转义逻辑。
- **信心水平**: 确定
- **发现来源视角**: 代码生成受害者

---

### [AR-37] `GraphExporter.sanitizeMermaidId` 产生 ID 冲突——不同节点合并为同一 Mermaid 节点

- **文件**: `nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java:243-245`
- **证据片段**:
  ```java
  private String sanitizeMermaidId(String id) {
      return id.replaceAll("[^a-zA-Z0-9_]", "_");
  }
  ```
- **严重程度**: P2
- **现状**: `"foo.bar"` 和 `"foo_bar"` 都映射到 `"foo_bar"`。Mermaid 图中两个不同的类/方法被合并为一个节点。
- **风险**: 社区检测结果可视化产生误导性图表。
- **建议**: 添加后缀消歧（如冲突时追加序号）。
- **信心水平**: 很可能
- **发现来源视角**: 代码生成受害者

---

### [AR-38] `splitSuperCommunities` 用未过滤的总符号数计算阈值——大型图上超级社区分割永远不触发

- **文件**: `nop-code-graph/src/main/java/io/nop/code/graph/community/CommunityDetector.java:441-445`
- **证据片段**:
  ```java
  int totalNodes = result.getTotalSymbols();  // ← 包含被过滤掉的低度数节点
  int maxSize = (int) Math.ceil(totalNodes * superThreshold);
  ```
- **严重程度**: P2
- **现状**: `totalSymbols` 是过滤前的符号总数。低度数节点被过滤后，实际参与聚类的节点可能只有总数的 10%。25% 的阈值基于未过滤总数计算，意味着即使单个社区包含 90%+ 的活跃节点，也达不到分割阈值。
- **风险**: 大型图中超级社区不会被分割，社区检测质量下降。
- **建议**: 使用 `result.getNodesProcessed()` 或 `result.getClusteredSymbols()` 作为阈值基数。
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者

---

### [AR-39] `CommunityDetector.runWithTimeout` 线程泄漏——`shutdownNow()` 不等待任务终止

- **文件**: `nop-code-graph/src/main/java/io/nop/code/graph/community/CommunityDetector.java:740-748`
- **证据片段**:
  ```java
  private static <T> T runWithTimeout(Callable<T> task, long timeoutMs) throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
          Future<T> future = executor.submit(task);
          return future.get(timeoutMs, TimeUnit.MILLISECONDS);
      } finally {
          executor.shutdownNow();  // 不等待线程实际终止
      }
  }
  ```
- **严重程度**: P2
- **现状**: 超时后 `shutdownNow()` 中断线程但不等待其终止。如果 Leiden 算法在紧密循环中不检查中断标志，线程继续运行。每次超时调用泄漏一个线程。
- **风险**: 多次超时后累积活跃线程，最终耗尽线程资源。
- **建议**: 改用 `executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS)`。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-40] `ChangeAnalyzer.pathMatchesQualifiedName` 无法匹配方法级符号到文件

- **文件**: `nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java:172-186`
- **证据片段**:
  ```java
  private boolean pathMatchesQualifiedName(String filePath, String qualifiedName) {
      String dotted = qualifiedName.replace('.', '/');
      int idx = normalized.lastIndexOf(dotted);
      if (idx >= 0) return true;
      // 对方法符号 "com.example.UserService.save":
      // dotted = "com/example/UserService/save" — 不在 "UserService.java" 中 (失败)
      // simpleName = "save" — 不在 "UserService.java" 中 (失败)
  }
  ```
- **严重程度**: P2
- **现状**: 方法级符号的 qualified name 包含方法名后缀（如 `.save`），转换为路径后无法匹配文件路径（如 `UserService.java`）。只有类级和包级符号能被匹配。由于大多数变更影响方法级符号，变更分析的大多数"受影响符号"不会被识别。
- **风险**: `analyzeChanges()` 的 affectedSymbols 列表严重不完整，风险评分和行动建议不可靠。
- **建议**: 从 qualified name 中提取类级别前缀后再匹配。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-41] `getSymbolById` 忽略 `indexId`——跨索引数据泄漏

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:632-637`
- **证据片段**:
  ```java
  @Override
  public CodeSymbol getSymbolById(String indexId, String symbolId) {
      IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
      NopCodeSymbol entity = symbolDao.getEntityById(symbolId);  // indexId 未使用
      return entity != null ? entityToCodeSymbol(entity) : null;
  }
  ```
- **严重程度**: P2
- **现状**: `indexId` 参数被接收但从未用于查询。`getEntityById` 只按主键加载，如果两个索引的 symbol ID 碰撞（`OrmFingerprintStore` 使用简单的 hashCode 方案时可能），会返回错误索引的数据。
- **风险**: 多索引场景下数据隔离被打破。
- **建议**: 添加 `WHERE indexId = ? AND id = ?` 过滤条件。
- **信心水平**: 很可能
- **发现来源视角**: 组合爆炸测试者

---

### [AR-42] `filterByLanguage` 用 `removeIf` 原地修改传入列表——调用方数据被意外截断

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:2946`
- **证据片段**:
  ```java
  private List<CodeSearchResultDTO> filterByLanguage(List<CodeSearchResultDTO> results,
                                                       String indexId, String language,
                                                       Map<String, String> filePathCache) {
      // ...
      results.removeIf(dto -> !matchingPaths.contains(dto.getFilePath())); // 修改调用方的列表
      return results;
  }
  ```
- **严重程度**: P2
- **现状**: `removeIf` 直接修改传入的列表引用。如果调用方在其他分支重用该列表（如 combined search 的 fullText 和 symbol 结果合并路径），部分结果会被意外删除。
- **建议**: 创建新列表而非原地修改，或在方法签名中明确标注破坏性语义。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

## P3 发现

### [AR-43] `PythonImportResolver` 对 `import os, sys, json` 只保留第一个模块

- **文件**: `nop-code-core/src/main/java/io/nop/code/core/resolver/PythonImportResolver.java:59-62`
- **证据片段**:
  ```java
  int commaIdx = trimmed.indexOf(',');
  if (commaIdx > 0) {
      trimmed = trimmed.substring(0, commaIdx);  // "import os, sys, json" → 只保留 "os"
  }
  ```
- **严重程度**: P3
- **现状**: Python 允许 `import os, sys, json` 一行导入多个模块，但解析器在第一个逗号处截断。`sys` 和 `json` 的依赖关系丢失。
- **建议**: 按逗号分割，为每个模块创建独立的 `CodeFileDependency`。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

### [AR-44] Java `RecordDeclaration` 的 qualified name 缺少包名前缀

- **文件**: `nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java:240-253`
- **证据片段**:
  ```java
  // RecordDeclaration 手动构造 qualified name:
  if (currentTypeSymbol != null) {
      symbol.setQualifiedName(currentTypeSymbol.getQualifiedName() + "." + decl.getNameAsString());
  } else {
      symbol.setQualifiedName(decl.getNameAsString());  // ← 顶层 record 缺少包名！
  }
  // 其他类型声明通过 createSymbolFromTypeDecl → decl.getFullyQualifiedName() 包含包名
  ```
- **严重程度**: P3
- **现状**: 顶层 record 的 qualified name 只有类名（如 `MyRecord`），不是全限定名（`com.example.MyRecord`）。与 class/interface/enum/annotation 的行为不一致。
- **建议**: 使用 `decl.getFullyQualifiedName()` 统一处理。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

### [AR-45] `OrmFingerprintStore.loadFingerprints` 对已映射路径重复应用 `pathMapper`

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/incremental/OrmFingerprintStore.java:83`
- **证据片段**:
  ```java
  // saveFingerprints (line 41): pathMapper 已应用
  // loadFingerprints (line 83): 再次应用
  fp.setFilePath(pathMapper.apply(entity.getFilePath()));
  ```
- **严重程度**: P3
- **现状**: 路径经过 triple-application：`save` 时映射一次，`load` 时又映射一次。当前映射器是无操作（或幂等），但行为概念上错误。
- **建议**: `loadFingerprints` 中不再重复映射。
- **信心水平**: 确定

---

### [AR-46] Tarjan SCC 使用递归 DFS——大型依赖图上 StackOverflow

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1748-1777`
- **严重程度**: P3
- **现状**: 大型单体仓库的线性依赖链可能数千文件深，递归 DFS 超过默认栈深度。
- **建议**: 改为迭代实现。
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者

---

## 去重说明

以下第 1 轮审查已报告的问题经确认仍存在但未重新展开：
- AR-02 (VFS 文件过滤器反转) 仍存在，代码未变更
- AR-03 (增量分析退化) 仍存在
- AR-07 (hashCode 碰撞实体 ID) 仍存在，`OrmFingerprintStore` 的 ID 方案与 `CodeIndexService` 不兼容（本次 AR-30/AR-31 补充了此不兼容性的具体后果）

---

## 总评

本次第 2 轮对抗性审查在第 1 轮（AR-01 至 AR-27）的基础上，发现了之前维度审计和对抗性审查均未触及的两个系统性盲区：

1. **语言分析器功能完备性（AR-28, AR-33）**：之前的审计集中在 ORM/IoC/安全/性能等"平台合规"维度，但没有深入检查每个语言适配器的实际功能输出。TypeScript 的调用提取完全死代码（AR-28），Python 的嵌套函数/类不可见（AR-33）——这意味着这两个语言的分析结果在核心功能层面就存在结构性缺陷。这不是代码风格问题，而是功能缺失。

2. **图遍历算法方向性（AR-29）**：`bfsCollect` 被正向和反向邻接表复用，但只实现了正向遍历逻辑。这是一个经典的"复用抽象泄漏"——一个看似通用的 BFS 实际上隐含了方向假设。

这两个 P0 的共同特征是：**它们在"看起来能工作"的层面完全正常**——TypeScript 分析器不报错只是返回空结果，反向依赖查询返回结果只是深度不够。这正是"灯下黑"的典型模式。

## 本次审查盲区自评

1. **图算法数学正确性**：Louvain/Leiden 社区检测的算法实现未做数学验证。
2. **并发压力测试**：所有并发问题基于代码推理，未实际验证。
3. **前端/GraphQL 运行时**：view.xml 和 page.yaml 的运行时行为未覆盖。
4. **跨语言结果对比**：三个语言适配器的功能输出未做系统性的横向对比验证。
5. **Delta 定制与升级兼容性**：未检查 Delta 文件与基础产品升级后的冲突风险。
