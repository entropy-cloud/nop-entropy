# Adversarial Review: nop-code — Open Findings

> **日期**: 2026-06-06（第 12 轮对抗性审查）
> **模块**: nop-code（13 个子模块）
> **审查类型**: 开放式对抗性审查
> **发现来源视角**: 授权契约考古学家 + 计算复杂度攻击者 + 多语言一致性审计者 + 死代码检测逻辑审计者
> **审查范围**: BizModel 授权配置一致性、图分析算法复杂度（CriticalNodeAnalyzer/KnowledgeGapAnalyzer/GraphDiffer）、DeadCodeDetector 逻辑、triggerFullIndex 语言覆盖、EntryPointScorer 评分语义

## 去重确认

已审阅以下历史报告：
- r11 (2026-06-06d): AR-158~AR-169（缓存引用泄漏、WEIGHT_EXTERNAL 死权重、guessExtension 误匹配、增量索引搜索引擎不同步等）
- r10 (2026-06-06c): AR-145~AR-157（SpringEvent listener 覆盖、Java Record 未入 symbolMap、Python `__init__.py` 等）
- r9 (2026-06-06b): AR-124~AR-144（增量索引路径失效、view 字段名不匹配、并发锁不完整等）
- r8 (2026-06-06): AR-94~AR-123（零事务、Leiden directed、Python TSNode、glob 匹配等）
- r7 (2026-06-02): AR-88~AR-93
- deep-audit-2026-06-06: 21 维度全覆盖

**仍存在的问题（未修复确认）**：
- AR-98~AR-123（单例节点丢弃、startsWith 错配、cascadeDelete 缺失、persistFlows OOME 等）
- AR-124~AR-169（增量索引路径失效、并发锁、搜索引擎不同步、缓存引用泄漏等）

本轮聚焦于此前审查从未覆盖的领域：**BizModel 授权配置与 action-auth.xml 的一致性**、**图分析算法的计算复杂度保护**、**DeadCodeDetector 全量逻辑审计**、**triggerFullIndex 多语言覆盖**。

---

### [AR-170] `@Auth(permissions = "code-query"/"code-source-read")` 与 action-auth.xml 定义的权限字符串完全不匹配——31 个方法可能对非 admin 用户不可访问

- **文件**: 
  - `nop-code/nop-code-service/.../entity/NopCodeIndexBizModel.java:105,136,186,196,232,238,278,296`
  - `nop-code/nop-code-service/.../entity/NopCodeSymbolBizModel.java:51,58,67,102,117,130,139,148,156,164,176,188,196,210,227,237`
  - `nop-code/nop-code-service/.../entity/NopCodeFileBizModel.java:35,43,53,59,65,76,82`
  - `nop-code/nop-code-web/.../auth/nop-code.action-auth.xml:14,23,31,42,53,65`
- **证据片段**:
  ```java
  // NopCodeIndexBizModel.java:105 — 使用 "code-query" 权限
  @BizQuery
  @Auth(permissions = "code-query")
  public IncrementalStatus getIncrementalStatus(@Name("indexId") String indexId) {...}
  
  // NopCodeIndexBizModel.java:278 — 使用 "code-source-read" 权限
  @BizQuery
  @Auth(permissions = "code-source-read")
  public String exportGraph(...) {...}
  ```
  ```xml
  <!-- nop-code.action-auth.xml — 只定义了实体级权限 -->
  <permissions>NopCodeIndex:query</permissions>     <!-- 非 "code-query" -->
  <permissions>NopCodeFile:query</permissions>      <!-- 非 "code-query" -->
  <permissions>NopCodeSymbol:query</permissions>    <!-- 非 "code-query" -->
  <!-- 无任何 "code-source-read" 定义 -->
  ```
- **严重程度**: P1
- **现状**: 三个 BizModel 中共有 26 个方法使用 `@Auth(permissions = "code-query")`，5 个方法使用 `@Auth(permissions = "code-source-read")`。但 `nop-code.action-auth.xml` 中定义的权限字符串是 `NopCodeIndex:query`、`NopCodeFile:query`、`NopCodeSymbol:query` 等实体级格式。两种权限命名完全不匹配：
  - `code-query` ≠ `NopCodeIndex:query` / `NopCodeSymbol:query` / `NopCodeFile:query`
  - `code-source-read` 在 action-auth.xml 中根本不存在
  
  这意味着如果 Nop 授权框架严格匹配权限字符串，**所有使用 `code-query` 和 `code-source-read` 的方法对非 admin 用户不可访问**。只有 `@Auth(roles = "admin")` 的方法（10 个分析类方法 + 3 个写操作）可以正常工作。

  这与 AR-155（BizModel 授权不一致）是不同维度的问题：AR-155 关注的是"分析查询用 admin roles 而非 permissions"，而本发现关注的是"permissions 字符串本身可能从未被授予"。
- **风险**: (a) 所有只读查询 API 对非 admin 用户返回 403；(b) 代码搜索、符号查询、文件浏览、层级查询等核心功能在标准部署下不可用；(c) 如果 Nop 框架对未定义权限默认放行，则权限检查形同虚设——两者都是问题。
- **建议**: (a) 将 `@Auth(permissions = "code-query")` 统一为实体级格式（如 `NopCodeIndex:query`），或在 action-auth.xml 中添加 `code-query` 权限定义；(b) 为 `code-source-read` 添加 action-auth 条目或改为已有权限。
- **信心水平**: 很可能（取决于 Nop 授权框架对未匹配权限字符串的处理方式）
- **发现来源视角**: 授权契约考古学家

---

### [AR-171] triggerFullIndex 硬编码 `"**/*.java"`——完全忽略 Python/TypeScript 文件

- **文件**: `nop-code/nop-code-service/.../entity/NopCodeIndexBizModel.java:61`
- **证据片段**:
  ```java
  @BizMutation
  @Auth(roles = "admin")
  public String triggerFullIndex(
          @Name("indexId") String indexId,
          @Name("projectPath") String projectPath) {
      int fileCount = codeIndexService.indexDirectory(indexId, projectPath, "**/*.java");
      //                                                                          ^^^^^^^^^^^
      // 硬编码仅 Java 文件，Python (.py) 和 TypeScript (.ts) 文件被完全忽略
      ...
  }
  ```
  对比 `indexDirectory` 方法（line 116）允许自定义 filePattern：
  ```java
  String pattern = filePattern != null ? filePattern : "**/*.java";
  ```
  以及 `LanguageAdapterRegistry` 注册了三种语言适配器（Java、Python、TypeScript）。
- **严重程度**: P1
- **现状**: nop-code 模块投入了大量代码实现多语言支持：
  - `nop-code-lang-java`：Java 文件分析器
  - `nop-code-lang-python`：Python 文件分析器
  - `nop-code-lang-typescript`：TypeScript 文件分析器
  - `LanguageAdapterRegistry`：注册所有语言适配器
  - `ProjectAnalyzer`：按文件扩展名自动选择适配器
  
  但 `triggerFullIndex`（前端 Dashboard 的主要入口点）硬编码 `**/*.java`，**只索引 Java 文件**。用户在一个同时包含 Java 和 Python/TypeScript 的项目中调用 `triggerFullIndex`，只会看到 Java 符号。Python 和 TypeScript 的类、函数、调用关系完全消失。
  
  `indexDirectory` 方法允许传入自定义模式，但默认值也是 `"**/*.java"`。
- **风险**: (a) 多语言项目（如 Java 后端 + TypeScript 前端）只有后端被索引；(b) 用户可能不知道 Python/TS 文件被跳过，导致死代码检测和影响分析结果不完整；(c) 与模块声称的多语言支持能力矛盾。
- **建议**: (a) `triggerFullIndex` 应收集所有注册语言的文件扩展名，构建包含所有语言的 glob 模式（如 `"**/*.java,*.py,*.ts,*.tsx"`）；(b) 或在 `ProjectAnalyzer.indexDirectory` 内部自动按注册语言过滤文件，而非依赖外部 glob 模式。
- **信心水平**: 确定
- **发现来源视角**: 多语言一致性审计者

---

### [AR-172] diffGraph 对两个索引分别运行完整 Leiden 社区检测——大型索引时同步查询超时

- **文件**: `nop-code/nop-code-service/.../impl/CodeGraphService.java:170-190`
- **证据片段**:
  ```java
  GraphDiffDTO diffGraph(String baselineIndexId, String targetIndexId) {
      // ... baseline ...
      CallGraph baselineCallGraph = cacheManager.getOrRebuildCallGraph(baselineIndexId, ...);
      SymbolTable baselineSymbolTable = cacheManager.getOrRebuildSymbolTable(baselineIndexId, ...);
      CommunityDetector.CommunityDetectionResult baselineCommunities =
              new CommunityDetector().detectCommunities(baselineCallGraph, baselineSymbolTable);  // ← 第 1 次 Leiden
      GraphSnapshot baseline = GraphDiffer.buildSnapshot(baselineCallGraph, baselineCommunities);
      
      // ... target ...
      CallGraph targetCallGraph = cacheManager.getOrRebuildCallGraph(targetIndexId, ...);
      SymbolTable targetSymbolTable = cacheManager.getOrRebuildSymbolTable(targetIndexId, ...);
      CommunityDetector.CommunityDetectionResult targetCommunities =
              new CommunityDetector().detectCommunities(targetCallGraph, targetSymbolTable);  // ← 第 2 次 Leiden
      GraphSnapshot target = GraphDiffer.buildSnapshot(targetCallGraph, targetCommunities);
      
      return new GraphDiffer().diff(baseline, target);
  }
  ```
- **严重程度**: P2
- **现状**: `diffGraph` 在一次 GraphQL 查询中执行两次完整的 Leiden 社区检测。Leiden 算法复杂度为 O(N log N) 到 O(N²)（取决于图结构）。对于 10 万符号的索引：
  - 每次 `getOrRebuildCallGraph` 从 DB 加载全部调用边（AR-109 已报告无缓存）
  - 每次 `getOrRebuildSymbolTable` 从 DB 加载全部符号
  - 每次 `detectCommunities` 运行 Leiden 算法
  
  总执行时间 = 2 × (DB 加载 + Leiden 计算)。如果单次 Leiden 需要 30 秒，总时间超过 60 秒，远超典型 HTTP/GraphQL 超时。
  
  同一模式也出现在 `getKnowledgeGaps`（line 146-148）和 `exportGraph` with `communityView=true`（line 164-166）——每次 GraphQL 查询都触发完整的 Leiden 运行。
- **风险**: (a) 大型索引的 `diffGraph`、`getKnowledgeGaps`、`exportGraph` 查询几乎必然超时；(b) 服务端线程被长时间占用，影响其他请求；(c) 反复调用可能造成 DB 压力累积。
- **建议**: (a) 将社区检测结果缓存到 `CodeCacheManager`（与 SymbolTable/CallGraph 统一管理）；(b) 或将社区检测改为异步后台任务，结果持久化到 DB；(c) 至少在调用前检查图大小，超过阈值时拒绝或降级。
- **信心水平**: 确定
- **发现来源视角**: 计算复杂度攻击者

---

### [AR-173] CriticalNodeAnalyzer.computeBridgeNodes 运行 BetweennessCentrality（O(V×E)）无大小检查和超时保护

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/critical/CriticalNodeAnalyzer.java:75-121`
- **证据片段**:
  ```java
  private List<CriticalNodeResult.NodeScore> computeBridgeNodes(
          CallGraph callGraph, SymbolTable symbolTable, int topN) {
      Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
      
      for (String node : callGraph.getAllNodeIds()) {
          graph.addVertex(node);
      }
      for (String caller : callGraph.getAllNodeIds()) {
          for (String callee : callGraph.getCallees(caller)) {
              try {
                  graph.addEdge(caller, callee);  // JGraphT 有向图构建
              } catch (IllegalArgumentException e) {
                  // 忽略重复边
              }
          }
      }
      
      BetweennessCentrality<String, DefaultEdge> bc = new BetweennessCentrality<>(graph, false);
      Map<String, Double> scores = bc.getScores();  // O(V × E) —— 无界计算
      ...
  }
  ```
- **严重程度**: P2
- **现状**: JGraphT 的 `BetweennessCentrality` 使用 Brandes 算法，复杂度为 O(V × E)。对于 V=100,000 节点、E=500,000 边的图，计算量为 500 亿次基本操作。构建 JGraphT 图本身也需要遍历所有节点和边。
  
  与 `CommunityDetector`（有 `runWithTimeout` 超时保护）不同，`CriticalNodeAnalyzer` 没有任何超时或大小限制机制。`getCriticalNodes` 是一个同步 GraphQL 查询，会阻塞调用线程直到计算完成。
  
  `CodeGraphService.getCriticalNodes`（line 125-138）直接调用此方法，没有预处理检查。
- **风险**: 大型索引调用 `getCriticalNodes` 时，服务端线程被长时间阻塞（可能数分钟甚至数十分钟），影响整个应用的可用性。与 AR-102（CommunityDetector 线程泄漏）属于同一类问题，但无任何保护。
- **建议**: (a) 在计算前检查 `callGraph.getAllNodeIds().size()`，超过阈值（如 50,000）时拒绝或降级为仅 hub 分析（hub 分析是 O(E) 的）；(b) 使用 `runWithTimeout` 包装（与 CommunityDetector 一致）；(c) 考虑使用近似算法（如 RA-Brandes）替代精确计算。
- **信心水平**: 确定
- **发现来源视角**: 计算复杂度攻击者

---

### [AR-174] KnowledgeGapAnalyzer.computeCohesion 只统计出边——与 AR-106 相同 bug 的不同实例

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/knowledge/KnowledgeGapAnalyzer.java:76-96`
- **证据片段**:
  ```java
  private double computeCohesion(List<String> symbolIds, CallGraph callGraph) {
      if (symbolIds == null || symbolIds.size() < 2) return 1.0;
      
      Set<String> memberSet = new HashSet<>(symbolIds);
      int internalEdges = 0;
      int externalEdges = 0;
      
      for (String node : memberSet) {
          List<String> callees = callGraph.getCallees(node);  // ← 只统计出边
          for (String callee : callees) {
              if (memberSet.contains(callee)) {
                  internalEdges++;
              } else {
                  externalEdges++;
              }
          }
          // ← 完全忽略 getCallers(node) 的入边
      }
      
      int total = internalEdges + externalEdges;
      return total == 0 ? 1.0 : (double) internalEdges / total;
  }
  ```
  对比 AR-106 报告的 `CommunityDetector.calculateCohesion`（line 768-789）完全相同的模式。
- **严重程度**: P2
- **现状**: `KnowledgeGapAnalyzer.computeCohesion` 与 `CommunityDetector.calculateCohesion`（AR-106）使用完全相同的出边遗漏模式。当社区内的符号 A 被外部符号 C 调用时，该入边不被统计为外部边，凝聚力被高估。
  
  这导致 `detectWeakCommunities` 的阈值判断不准确：对外部有大量调用者的社区（如公共工具类），凝聚力被高估，本应被标记为"弱社区"的社区被误判为"健康社区"。
  
  这个问题在同一代码库中至少存在两处，可能是一个 copy-paste 模式。
- **风险**: `getKnowledgeGaps` API 返回的"弱社区"列表不完整。对外部依赖密集的核心模块（如被全项目调用的 `StringUtils`），其所在社区不会被标记为弱社区。
- **建议**: 与 AR-106 统一修复：同时统计 `getCallees` 和 `getCallers`，或提取公共的 `CohesionCalculator` 工具类避免重复代码。
- **信心水平**: 确定
- **发现来源视角**: 死代码检测逻辑审计者

---

### [AR-175] DeadCodeDetector.isPotentiallyDynamic 使用 `contains` 匹配全限定名中的"listener"/"handler"等子串——大面积降低死代码检测灵敏度

- **文件**: `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/DeadCodeDetector.java:349-359`
- **证据片段**:
  ```java
  private boolean isPotentiallyDynamic(CodeSymbol symbol) {
      ...
      String qName = symbol.getQualifiedName();
      if (qName != null) {
          String lower = qName.toLowerCase();
          if (lower.contains("listener")       // "ChangeListenerFactory" → 匹配
                  || lower.contains("handler")  // "BroadcastHandlerChain" → 匹配
                  || lower.contains("callback") // "CallbackInvoker" → 匹配
                  || lower.contains("hook")      // "WebhookDispatcher" → 匹配
                  || lower.contains("observer")  // "ObserverPatternDemo" → 匹配
                  || lower.contains("subscriber")) { // "EventSubscriberRegistry" → 匹配
              return true;
          }
      }
      return false;
  }
  ```
- **严重程度**: P2
- **现状**: `isPotentiallyDynamic` 将死代码置信度从 0.95 降为 0.6（从"确定死代码"降级为"可疑"）。`contains` 子串匹配对全限定名产生大量误匹配：
  - `com.example.ListenerFactory.create()` → 含 "listener" → confidence=0.6
  - `com.example.BroadcastHandlerChain.dispatch()` → 含 "handler" → confidence=0.6
  - `com.example.WebhookDispatcher.process()` → 含 "hook" → confidence=0.6
  - `com.example.CallbackInvoker.invoke()` → 含 "callback" → confidence=0.6
  
  在使用这些命名模式的代码库中，大量正常方法被误判为"可能被动态调用"，死代码报告的信噪比显著降低。

  这与 AR-160（guessExtension contains 误匹配）和 AR-161（extData contains 注解匹配）属于同一模式：使用 `String.contains()` 做精确语义匹配。
- **风险**: 死代码检测报告的"suspiciousSymbols"列表膨胀，淹没真正需要关注的死代码。开发者对报告失去信任。
- **建议**: (a) 检查类名而非全限定名：提取最后一个 `.` 之后的部分进行匹配；(b) 使用单词边界匹配（如 `lower.matches(".*\\b(listener|handler|callback)\\b.*")`）；(c) 或只匹配实现了特定接口（如 `EventListener`、`Handler`）的类。
- **信心水平**: 确定
- **发现来源视角**: 死代码检测逻辑审计者

---

## 总评

本轮审查从授权配置一致性、计算复杂度保护、多语言覆盖一致性三个全新视角切入，发现了一个此前 11 轮审查从未触及的跨层次问题：**BizModel 的 `@Auth` 权限字符串与 action-auth.xml 定义完全不匹配**（AR-170）。这是一个典型的"代码和配置分别演化"问题——BizModel 使用自由格式的 `code-query`/`code-source-read`，而 action-auth.xml 使用实体级格式 `NopCodeIndex:query`——两者从未对齐。如果授权框架严格匹配，所有查询 API 对非 admin 用户不可访问；如果默认放行，权限检查形同虚设。

**最值得关注的 3 个方向**：

1. **授权配置漂移**（AR-170）——31 个方法的权限可能从未生效。这是一个跨配置和代码的系统性问题，不是单文件 bug。
2. **triggerFullIndex 硬编码 Java-only**（AR-171）——与模块声称的多语言支持（3 个语言适配器、LanguageAdapterRegistry）矛盾。前端主入口点完全忽略 Python/TypeScript 文件。
3. **图分析算法无计算保护**（AR-172 + AR-173）——diffGraph/getKnowledgeGaps/getCriticalNodes 对大型索引的计算量无上限，可在 GraphQL 查询中阻塞服务端线程。

**正面发现**：DeadCodeDetector 的整体架构合理（排除框架入口点、测试代码、ORM 实体、Python dunder 方法等），ExtDataHelper 的 JSON 解析有良好的错误处理，CallGraph/SymbolTable 已添加 truncated 标记（响应 AR-108）。

## 本次审查的盲区自评

1. **Nop 授权框架运行时行为**：未验证 `@Auth(permissions = "...")` 对未定义权限的实际处理方式（拒绝 vs 放行）。
2. **MethodCallFilter 上下文语义**：`CodeMethodCall.context` 字段的实际值格式未完全验证（简单名 vs 全限定名），影响 `isJavaLangType` 的判断。
3. **EntryPointScorer 评分阈值**：默认阈值（entryPointThreshold=2.0, maxCallerCountForEntry=3）对实际项目的适用性未量化。
4. **ProjectAnalyzer 内部细节**：`ProjectAnalyzer`（794 行）的批量处理、内存管理、错误恢复等细节未深入审查。
5. **端到端运行验证**：所有发现基于静态代码分析。

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | — |
| P1      | 2    | 权限字符串不匹配（AR-170）、triggerFullIndex 仅 Java（AR-171） |
| P2      | 4    | diffGraph 双 Leiden（AR-172）、BetweennessCentrality 无保护（AR-173）、KnowledgeGap 出边遗漏（AR-174）、DeadCode contains 误匹配（AR-175） |
| P3      | 0    | — |
