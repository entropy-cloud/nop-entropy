# Adversarial Review: nop-code (2026-05-29)

> **审查类型**: 开放式对抗性审查
> **目标模块**: nop-code（含 nop-code-core, nop-code-service, nop-code-dao, nop-code-graph, nop-code-flow, nop-code-lang-java, nop-code-lang-python, nop-code-lang-typescript, nop-code-web, nop-code-app）
> **审查日期**: 2026-05-29
> **审查方法**: 代码驱动的开放探索，从异常信号和模式入手，不绑定固定维度。起始视角：异常路径侦探 + 死代码清道夫 + 10x 规模运维者。
> **去重基线**: 已读取 `ai-dev/analysis/2026-05-25-code-review-graph-vs-nop-code.md`（下称"CRG 对比分析"）。该分析确认了 7 个 Bug（CallHierarchy ID 不一致、NopCodeUsage 未填充、Python/TS 适配器未注册、sourceCode 返回 null、indexId 硬编码 "test"、每次图查询全量重建、File ID 碰撞风险）和 5 个文档未覆盖的重要问题。本次审查已验证这些问题是否仍然存在，并在发现新问题时标注。

---

## 总评

nop-code 模块有清晰的分层架构设计（core → graph → flow → service）和合理的接口抽象（`ICodeIndexService`、`ILanguageAdapter`、`IImportResolver` 等）。但从实现质量看，模块处于**功能原型到生产就绪之间的过渡阶段**：核心索引和图分析管线能工作，但存在多个导致功能静默失效的 Bug、严重的原生内存泄漏、图查询全量加载无缓存的性能瓶颈，以及新增的 nop-code-flow 子模块有多处未完成实现。

**最值得关注的 3 个方向**：

1. **数据一致性：resolveQualifiedNamesToIds 破坏类型层级链路**（AR-01）。这是一个在首次全量索引后静默失效的 Bug——`getTypeHierarchy` 对项目内部继承链永远返回空结果。它是已确认 Bug 中影响面最广的一个，且之前未被报告。
2. **原生内存泄漏：TSTree 未关闭**（AR-02）。Python 和 TypeScript 适配器在每次文件分析时都泄漏 tree-sitter 原生内存。在大规模索引场景下会导致进程 OOM。
3. **nop-code-flow 功能未完成**（AR-06, AR-07, AR-08, AR-10）。FlowDetector、ChangeAnalyzer、DeadCodeDetector 存在多处硬编码常量、未实现功能、以及符号到文件路径映射的 Bug，表明这个子模块尚未达到可用状态。

## 本次审查的盲区自评

- **并发压测场景**：未构造多线程并发索引+查询的压力测试。`synchronized` 序列化问题（AR-11）在生产负载下的实际影响需实测。
- **大型代码库端到端验证**：未在 50 万+ 符号的真实项目上运行完整索引+图分析管线。性能问题（全量加载、N+1 查询）的实际数值影响需实测。
- **前端页面和 Web 层**：`nop-code-web` 的 view.xml 页面配置未逐一审查。
- **nop-code-app 启动和部署**：GraalVM native image 配置（reflect-config.json）的完整性仅做了文件级检查，未实际构建和运行 native image。

---

## 第 1 轮发现

### [AR-01] resolveQualifiedNamesToIds 破坏类型层级查询

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1989-1991, 263, 1163, 1183`
- **证据片段**:
  ```java
  // :1989 — 将 superTypeId 从 qualified name 解析为 UUID
  CodeSymbol resolved = symbolTable.getByQualifiedName(superTypeId);
  if (resolved != null) {
      inh.setSuperTypeId(resolved.getId()); // :1991 — 写入 UUID 替换 qualified name
  }

  // :263 — entityToInheritance 把已变成 UUID 的 superTypeId 当做 qualified name 传出
  inh.setSuperTypeQualifiedName(entity.getSuperTypeId());

  // :1163 — buildTypeHierarchy 用 qualified name 查 SymbolTable，但现在是 UUID，查不到
  CodeSymbol symbol = table.getByQualifiedName(qualifiedName);
  ```
- **严重程度**: P0
- **现状**: 首次全量索引后，`resolveQualifiedNamesToIds` 将 `NopCodeInheritance.superTypeId` 从 `com.foo.Bar`（qualified name）替换为 UUID。之后 `buildTypeHierarchy` 通过 `entityToInheritance` 拿到的 `superTypeQualifiedName` 是 UUID，`getByQualifiedName` 无法匹配，导致 `getTypeHierarchy` 对所有项目内部继承链返回空结果。
- **风险**: 类型层级浏览、IDE 式继承导航功能完全失效。调用者看到空结果但无错误提示，可能误判为项目无继承关系。
- **建议**: 方案 A：在 `resolveQualifiedNamesToIds` 中同时保留 qualified name（不覆盖，用新字段如 `resolvedSuperTypeId`）。方案 B：在 `buildTypeHierarchy` 中用 ID 而非 qualified name 做递归。方案 B 更简单。
- **信心水平**: 确定（逐行代码追踪确认）
- **发现来源视角**: 异常路径侦探
- **去重**: CRG 对比分析 §八 确认了 "CallHierarchy ID 不一致"但未报告此类型层级变体。经验证，call hierarchy 的主路径（symbol.getId()）实际是正确的，但 type hierarchy 确实受此 Bug 影响。

### [AR-02] TSTree 原生对象未关闭导致内存泄漏

- **文件**: `nop-code-lang-python/src/main/java/io/nop/code/lang/python/PythonCodeFileAnalyzer.java:47`, `nop-code-lang-typescript/src/main/java/io/nop/code/lang/typescript/analyzer/TypeScriptCodeFileAnalyzer.java:59`
- **证据片段**:
  ```java
  // PythonCodeFileAnalyzer.java:47
  TSTree tree = parser.parseString(null, sourceCode);
  // ... 使用 tree.getRootNode() ...
  // 方法结束，tree.close() 从未被调用
  ```
- **严重程度**: P0
- **现状**: Python 和 TypeScript 语言适配器在 `analyze()` 方法中创建 `TSTree` 对象但从不调用 `tree.close()`。`TSTree` 持有 tree-sitter 的原生堆内存（C 层 `TSTree*`），不关闭会导致原生内存泄漏。
- **风险**: 索引包含 1 万个 Python/TypeScript 文件的项目时，泄漏的原生内存可达数百 MB 至数 GB，导致进程 OOM 或被系统 OOM Killer 终止。
- **建议**: 使用 try-with-resources 或 try-finally 确保 `tree.close()` 始终被调用。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者
- **去重**: CRG 对比分析未报告此问题。新发现。

### [AR-03] indexFile 不刷新分析缓存导致后续图查询返回过时结果

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:364-376`
- **证据片段**:
  ```java
  // :364-376 — indexFile 方法
  public CodeFileAnalysisResult indexFile(String indexId, String filePath, String sourceCode) {
      // ... 分析和持久化 ...
      return result; // 没有 invalidateAnalysisCache(indexId)
  }

  // :335 — indexDirectory 方法（对比）
  invalidateAnalysisCache(indexId); // 正确刷新了缓存

  // :1801 — triggerIncrementalIndex 方法（对比）
  invalidateAnalysisCache(indexId); // 也正确刷新了
  ```
- **严重程度**: P1
- **现状**: `indexFile()` 持久化新数据到 DB 但不调用 `invalidateAnalysisCache(indexId)`。后续的图分析查询（`detectCommunities`、`getTypeHierarchy`、`getCallHierarchy`、`getImpactAnalysis`）会返回基于旧数据的缓存结果。
- **风险**: 单文件索引后立即查询图分析会得到过时结果，且无任何警告。在 CI/CD 流水线中（先索引单个变更文件，再查影响分析）会给出错误的影响范围。
- **建议**: 在 `indexFile` 方法返回前添加 `invalidateAnalysisCache(indexId)`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探
- **去重**: CRG 对比分析 §八.6 确认"每次图查询全量重建"，但未发现 `indexFile` 遗漏缓存刷新的具体路径。新发现。

### [AR-04] CallGraph 返回内部可变列表，外部修改可破坏图完整性

- **文件**: `nop-code-core/src/main/java/io/nop/code/core/graph/CallGraph.java:17-23`
- **证据片段**:
  ```java
  public List<String> getCallees(String nodeId) {
      return forwardEdges.getOrDefault(nodeId, Collections.emptyList());
  }

  public List<String> getCallers(String nodeId) {
      return reverseEdges.getOrDefault(nodeId, Collections.emptyList());
  }
  ```
- **严重程度**: P2
- **现状**: 当节点存在边时，`getOrDefault` 返回的是内部 `ArrayList` 的直接引用（可变）。任何调用者通过 `getCallees().add(...)` 或 `.clear()` 都会修改图的内部状态。
- **风险**: 当前调用者碰巧没有修改返回的列表，但这是隐性契约而非显式保障。如果未来代码（如新增的图分析算法）无意修改返回列表，会导致不可预测的图数据损坏，且极难调试。
- **建议**: 返回 `Collections.unmodifiableList(...)` 包装。
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫（追踪 CallGraph 的所有调用者时发现）

### [AR-05] CallGraph 允许重复边，膨胀所有基于度的分析结果

- **文件**: `nop-code-core/src/main/java/io/nop/code/core/graph/CallGraph.java:12-14`
- **证据片段**:
  ```java
  public void addEdge(String caller, String callee) {
      forwardEdges.computeIfAbsent(caller, k -> new ArrayList<>()).add(callee);
      reverseEdges.computeIfAbsent(callee, k -> new ArrayList<>()).add(caller);
  }
  ```
- **严重程度**: P2
- **现状**: `addEdge` 不检查重复。如果同一 caller→callee 对被添加多次（例如 ORM 中存在重复记录），`forwardEdges` 和 `reverseEdges` 都会包含重复项。所有基于 `getCallees().size()` 和 `getCallers().size()` 的分析（入口点评分、影响分析、社区检测的度计算）都会被膨胀。
- **风险**: 影响 `EntryPointScorer`（度中心性评分偏高）、`CommunityDetector`（社区内聚度计算偏差）、`ImpactAnalyzer`（blast radius 偏大）。如果 DB 中存在重复调用记录（无唯一约束），问题会被放大。
- **建议**: 改用 `Set<String>` 作为边的值类型，或在 `addEdge` 中做重复检查。
- **信心水平**: 很可能（取决于 DB 数据是否有重复记录）
- **发现来源视角**: 10x 规模运维者（追踪数据一致性时发现）
- **去重**: CRG 对比分析 §八.7 提到"File ID 碰撞风险"但未涉及 CallGraph 边去重。新发现。

### [AR-06] TypeScript 调用提取完全未实现（walkNodeForCalls 是死代码）

- **文件**: `nop-code-lang-typescript/src/main/java/io/nop/code/lang/typescript/analyzer/TypeScriptCodeFileAnalyzer.java:428-469`
- **证据片段**:
  ```java
  // :428-469 — 方法定义存在
  private void walkNodeForCalls(TSNode node, String sourceCode, ...) {
      // ... 完整的实现逻辑 ...
  }

  // 但在 handleFunctionDeclaration、handleMethodDefinition 中
  // 没有任何地方调用 walkNodeForCalls
  ```
- **严重程度**: P1
- **现状**: TypeScript 适配器定义了 `walkNodeForCalls` 方法（428-469行），但从未从任何代码路径调用。对比 Python 适配器正确地在 `walkBlockForCalls` 中调用了等效方法。结果：**TypeScript 文件永远不会产生调用边（calls）**。索引 TypeScript 项目后，调用图、调用层级、影响分析全部为空。
- **风险**: TypeScript 项目的代码图谱功能完全失效。任何依赖调用图的下游分析（执行流追踪、死代码检测、影响分析）对 TypeScript 无效。
- **建议**: 在 `handleFunctionDeclaration` 和 `handleMethodDefinition` 的末尾调用 `walkNodeForCalls`。
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫
- **去重**: CRG 对比分析未报告此问题。新发现。

### [AR-07] FlowDetector.flowCache 无界增长

- **文件**: `nop-code-flow/src/main/java/io/nop/code/flow/FlowDetector.java:64, 121`
- **证据片段**:
  ```java
  // :64
  private final Map<String, List<ExecutionFlow>> flowCache = new ConcurrentHashMap<>();

  // :121 — 唯一的写操作
  flowCache.put(indexId, flows);
  // 无任何 remove / clear / evict 操作
  ```
- **严重程度**: P2
- **现状**: `flowCache` 是 `ConcurrentHashMap`，每次 `detectFlows` 调用都会写入。整个类中没有任何清理、逐出、大小限制或 TTL 机制。在长期运行的服务器中，缓存会无限增长。
- **风险**: 每个索引的执行流数据（包含所有流路径和符号列表）会永久驻留内存。如果索引被删除后重建（新 indexId），旧数据仍然保留。
- **建议**: 添加 `removeFlows(String indexId)` 方法并在索引删除时调用。或使用带 TTL 的缓存（如 Caffeine）。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者
- **去重**: 新发现。

### [AR-08] ChangeAnalyzer.affectedFlows 永远为空列表

- **文件**: `nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java:232`
- **证据片段**:
  ```java
  // :232
  affected.setAffectedFlows(Collections.emptyList());
  ```
- **严重程度**: P2
- **现状**: `buildAffectedSymbol` 方法计算了风险评分（flow participation、community crossing 等），但 `affectedFlows` 字段被硬编码为空列表。`IChangeAnalyzer` 接口不接受 `FlowDetector` 或 flow 数据作为输入，因此无法跨引用执行流分析。
- **风险**: 变更分析报告中的"受影响的执行流"字段永远为空。API 消费者无法通过此接口获取变更的流级影响。
- **建议**: 在 `ChangeAnalyzer` 中注入或传入 `FlowDetector`，在风险计算完成后查询 `getAffectedFlows`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探
- **去重**: 新发现。

### [AR-09] FlowDetector 硬编码 ".java" 扩展名，Python/TS 文件路径匹配失败

- **文件**: `nop-code-flow/src/main/java/io/nop/code/flow/FlowDetector.java:188-189, 199-203`
- **证据片段**:
  ```java
  // :188-189
  private String symbolIdToFilePath(String symbolId, ...) {
      // ... 始终以 ".java" 结尾
      return packagePath + className + ".java";
  }

  // :199-203
  private String qualifiedNameToFilePath(String qualifiedName) {
      // ... 始终以 ".java" 结尾
      return packagePath + simpleName + ".java";
  }
  ```
- **严重程度**: P1
- **现状**: `FlowDetector` 的文件路径推导方法（`symbolIdToFilePath`、`qualifiedNameToFilePath`）始终添加 `.java` 扩展名。`getAffectedFlows` 使用这些推导出的路径匹配变更文件，Python（`.py`）和 TypeScript（`.ts`/`.tsx`）文件永远无法匹配。
- **风险**: 跨语言项目中，非 Java 文件的变更不会触发受影响执行流的更新。影响分析对 Python/TS 项目完全无效。
- **建议**: 从符号的 `extData`（`filePath` 字段）或 `language` 属性推导正确的文件扩展名。
- **信心水平**: 确定
- **发现来源视角**: 模型攻击者（追踪多语言路径时发现）
- **去重**: 新发现。

### [AR-10] ChangeAnalyzer.pathMatchesQualifiedName 映射逻辑有严重缺陷

- **文件**: `nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java:179-193`
- **证据片段**:
  ```java
  // :182 — 将完整 qualified name（含方法名）转为路径
  String dotted = qualifiedName.replace('.', '/');
  int idx = normalized.lastIndexOf(dotted);

  // :192 — 对 inner class 的匹配
  String dollarName = simpleName.replace('.', '$');
  normalized.contains(dollarName) // simpleName="process"，匹配任何包含"process"的路径
  ```
- **严重程度**: P1
- **现状**: `pathMatchesQualifiedName` 有两个问题：(1) 对 `com.example.Service.process` 生成路径 `com/example/Service/process`，但实际文件是 `com/example/Service.java`，永远不匹配。(2) `dollarName` 对无点号的 simpleName 就是 simpleName 本身，导致 `normalized.contains("process")` 匹配任何路径中含"process"的文件，产生大量误匹配。
- **风险**: 变更分析的核心映射逻辑失效：真正的变更文件找不到对应符号（假阴性），同时不相关的文件被误匹配（假阳性）。变更风险评分基于错误的数据。
- **建议**: 重写为基于 `extData.filePath` 的精确匹配。符号表中的 `extData` 已包含 `filePath` 字段，应优先使用。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探
- **去重**: 新发现。

### [AR-11] CodeIndexService 粗粒度 synchronized 阻塞跨索引并发

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:109, 300, 313, 326, 333`
- **证据片段**:
  ```java
  // :109
  private final ConcurrentHashMap<String, AnalysisCache> analysisCacheMap = new ConcurrentHashMap<>();

  // :300 — synchronized(this) 阻塞所有索引的读取
  private synchronized SymbolTable getOrRebuildSymbolTable(String indexId) { ... }

  // :333 — synchronized(this) 阻塞所有索引的写入
  public synchronized CodeIndexAnalysisResult indexDirectory(...) { ... }
  ```
- **严重程度**: P2
- **现状**: `analysisCacheMap` 是 `ConcurrentHashMap`，但所有缓存方法（`getOrRebuildSymbolTable`、`getOrRebuildCallGraph`、`invalidateAnalysisCache`）和 `indexDirectory` 都用 `synchronized(this)` 保护。这意味着：(1) `ConcurrentHashMap` 的无锁优势被完全抵消；(2) 索引 A 的写入会阻塞索引 B 的读取。
- **风险**: 在多租户或多项目场景下（nop-code 设计上支持多索引），一个项目的索引操作会阻塞所有其他项目的图分析查询。
- **建议**: 改为按 indexId 粒度加锁（如 `ConcurrentHashMap` + `computeIfAbsent` 模式），或使用 `Striped` 锁。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者
- **去重**: CRG 对比分析 §八.3 提到"线程安全"但未详细描述此粗粒度锁问题。补充新细节。

### [AR-12] deleteIndex 全量加载后删除，大索引存在 OOME 风险

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1321-1406`
- **证据片段**:
  ```java
  // :1321-1406 — 对 10 个实体表，每个都 findAllByQuery 全量加载到内存
  List<NopCodeSymbol> symbols = symbolDao.findAllByQuery(query);
  // 然后 batchDeleteEntities 逐批删除
  batchDeleteEntities(symbols, symbolDao);
  ```
- **严重程度**: P2
- **现状**: `deleteIndex` 对每个实体类型先 `findAllByQuery` 加载全部记录到内存，再批量删除。对包含 50 万符号的索引，仅 Symbol 实体就会消耗大量堆内存。
- **风险**: 删除大型索引时可能触发 OOME。即使不 OOME，大量对象也会增加 GC 压力。
- **建议**: 使用分页批量删除（`LIMIT + OFFSET` 或 `WHERE id IN (SELECT ... LIMIT)`），避免全量加载。
- **信心水平**: 很可能（取决于索引大小）
- **发现来源视角**: 10x 规模运维者
- **去重**: 新发现。

### [AR-13] searchViaEngine 忽略 searchType 参数，硬编码 HYBRID

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:728, 747, 755`
- **证据片段**:
  ```java
  // :728 — searchType 未传入
  return searchViaEngine(indexId, query, language, filePattern, limit);

  // :755 — 硬编码 HYBRID
  req.setSearchType(SearchType.HYBRID);
  ```
- **严重程度**: P2
- **现状**: `searchCode` 接受 `searchType` 参数（`SYMBOL_NAME`、`FULL_TEXT`、`COMBINED`），但当搜索引擎可用时委托给 `searchViaEngine`，后者完全忽略 searchType，硬编码为 `HYBRID`。
- **风险**: API 契约不一致。调用者期望 `SYMBOL_NAME` 搜索只匹配符号名称，实际获得混合搜索结果。在精确搜索场景（如按方法名查找）中，结果中会混入无关的全文匹配。
- **建议**: 将 `searchType` 传入 `searchViaEngine` 并映射到对应的 `SearchType` 枚举值。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探
- **去重**: 新发现。

### [AR-14] NopCodeIndex 实体无数据库索引

- **文件**: `nop-code/model/nop-code.orm.xml:91-171`
- **严重程度**: P2
- **现状**: `NopCodeIndex` 是所有其他实体的根聚合，所有查询都按 `indexId` 过滤。但 `NopCodeIndex` 自身没有定义任何 `<indexes>`。按 `name`、`rootPath`、`status` 查询索引都会全表扫描。
- **风险**: 随索引数量增长，索引管理查询（如"按名称查找索引"）越来越慢。
- **建议**: 添加索引：`name`（唯一）、`rootPath`、`status`。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者
- **去重**: 新发现。

### [AR-15] NopCodeFile.SOURCE_CODE 列使用 VARCHAR(524288)，超出多数数据库行大小限制

- **文件**: `nop-code/model/nop-code.orm.xml:192-193`
- **严重程度**: P2
- **现状**: `sourceCode` 列定义为 `stdSqlType="VARCHAR" precision="524288"`（512KB）。MySQL InnoDB 最大行大小约 65KB，超出部分会导致行溢出或建表失败。
- **风险**: 在 MySQL 上建表可能失败，或者长源文件写入时被静默截断。
- **建议**: 改用 `CLOB`/`TEXT` 类型（`stdSqlType="CLOB"` 或 `stdDomain="longString"`）。
- **信心水平**: 很可能（取决于目标数据库）
- **发现来源视角**: 模型攻击者
- **去重**: 新发现。

### [AR-16] NopCodeCall / NopCodeSemanticEdge 缺少唯一约束，允许重复边

- **文件**: `nop-code/model/nop-code.orm.xml:469-534 (Call), 810-875 (SemanticEdge)`
- **严重程度**: P2
- **现状**: `NopCodeCall` 无 `(indexId, callerId, calleeId, fileId, line)` 唯一约束；`NopCodeSemanticEdge` 无 `(indexId, sourceSymbolId, targetSymbolId, relationType)` 唯一约束。重复插入会产生重复记录。
- **风险**: 与 AR-05 联动——如果 DB 中存在重复记录，`rebuildCallGraph` 会为每条重复记录添加一条边，膨胀所有基于度的分析结果。
- **建议**: 添加唯一约束。在 `saveReplacingExisting` 逻辑中也应基于这些键做去重。
- **信心水平**: 很可能
- **发现来源视角**: 数据一致性追踪（从 AR-05 向上追踪到 ORM 层）
- **去重**: 新发现。

### [AR-17] FlowDetector.testGap 和 ChangeAnalyzer.computeTestCoverageGap 硬编码为常量

- **文件**: `nop-code-flow/src/main/java/io/nop/code/flow/FlowDetector.java:315`, `nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java:264-276`
- **证据片段**:
  ```java
  // FlowDetector.java:315
  double testGap = 1.0; // 永远是最大惩罚

  // ChangeAnalyzer.java:264-276
  double testGap = DEFAULT_TEST_COVERAGE_GAP; // 0.30，永远是固定值
  ```
- **严重程度**: P3
- **现状**: 两处的测试覆盖缺口指标都是硬编码常量，不使用任何实际的测试覆盖数据。`FlowDetector` 的 `testGap` 永远是 1.0（最大值），`ChangeAnalyzer` 永远是 0.30（构造器和测试符号除外）。
- **风险**: 关键度评分和变更风险评分中的"测试覆盖缺口"维度是恒定值，使这些评分在该维度上失去区分力。不同代码路径的风险评分差异仅来自其他维度。
- **建议**: 集成测试覆盖数据（如 JaCoCo XML）或至少基于 `TESTED_BY` 边做启发式判断。短期可考虑在文档中明确标注此维度为常量。
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫（追踪硬编码值时发现）
- **去重**: 新发现。

### [AR-18] ImpactAnalyzer 和 DeadCodeDetector 静默吞掉异常

- **文件**: `nop-code-graph/src/main/java/io/nop/code/graph/impact/ImpactAnalyzer.java:343`, `nop-code-flow/src/main/java/io/nop/code/flow/DeadCodeDetector.java:378`
- **证据片段**:
  ```java
  // ImpactAnalyzer.java:343
  catch (Exception e) {
      return null; // 无任何日志
  }

  // DeadCodeDetector.java:378
  catch (Exception e) {
      return null; // 无任何日志
  }
  ```
- **严重程度**: P2
- **现状**: 两个关键分析方法在 `catch (Exception)` 块中返回 `null` 且不记录任何日志。调用者无法区分"正常返回空"和"异常导致返回空"。
- **风险**: 如果这些方法因数据损坏或 API 变更开始频繁异常，问题完全不可见。图分析和死代码检测静默返回空结果，无任何错误追踪能力。
- **建议**: 至少添加 `LOG.warn("...", e)` 日志。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探
- **去重**: 新发现。

### [AR-19] NopCodeIndexBizModel.incrementalStatusMap 无界增长

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:33`
- **严重程度**: P3
- **现状**: `ConcurrentHashMap<String, IncrementalStatus>` 在 `triggerFullIndex` 和 `triggerIncrementalIndex` 时写入，但仅在 `deleteIndex` 时清理。如果索引被重建而非删除+重建，旧 status 条目永不清理。
- **风险**: 长期运行后内存占用缓慢增长。每个条目较小，实际影响有限。
- **建议**: 使用带 TTL 的缓存，或在索引状态变为 READY 后自动过期。
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者
- **去重**: 新发现。

### [AR-20] JavaFileAnalyzer 非线程安全——JavaParser 实例被多线程共享

- **文件**: `nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java:64, 73`
- **证据片段**:
  ```java
  // :64 — 实例字段
  private final JavaParser javaParser = new JavaParser();

  // :73 — 构造函数中配置
  CombinedTypeSolver typeSolver = new CombinedTypeSolver(reflectionTypeSolver);
  javaParser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));
  ```
- **严重程度**: P2
- **现状**: `JavaFileAnalyzer` 的 `JavaParser` 实例是字段级单例。`CodeIndexService` 使用 `ExecutorService` 并行调用 `analyze()`。JavaParser 的内部状态（包括 `SymbolSolver` 和 `ParserConfiguration`）在并发访问时可能不一致。
- **风险**: 并行索引 Java 项目时可能出现解析错误、符号解析失败、或偶发的 NPE。由于是竞态条件，难以复现和调试。
- **建议**: 每次调用创建新的 `JavaParser` 实例（`JavaParser` 构造开销小），或使用 `ThreadLocal`。
- **信心水平**: 很可能（JavaParser 文档未保证线程安全）
- **发现来源视角**: 10x 规模运维者
- **去重**: 新发现。

### [AR-21] ChangeAnalyzer.parseGitDiff 不设置工作目录

- **文件**: `nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java:85-88`
- **证据片段**:
  ```java
  ProcessBuilder pb = new ProcessBuilder("git", "diff", baseline + ".." + target);
  // 未设置 pb.directory()，使用 JVM 当前工作目录
  ```
- **严重程度**: P2
- **现状**: `parseGitDiff` 运行 `git diff` 但不设置 `ProcessBuilder.directory()`，导致命令在 JVM 的当前工作目录执行。如果 CWD 不是 git 仓库（常见于服务端部署），`git diff` 会失败且结果为空。
- **风险**: 在非开发环境的部署中，变更分析功能静默失效。不报错，只是返回空结果。
- **建议**: 接受 `workingDirectory` 参数并设置 `pb.directory(new File(workingDirectory))`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探
- **去重**: 新发现。

### [AR-22] GraalVM reflect-config.json 缺少 nop-code-flow 数据类

- **文件**: `nop-code-app/src/main/resources/META-INF/native-image/io.github.entropy-cloud/nop-code-app/reflect-config.json`
- **严重程度**: P2
- **现状**: `reflect-config.json` 未注册 `ExecutionFlow`、`ExecutionFlow$FlowStats`、`ChangeAnalysisResult` 及其内部类、`DeadCodeReport` 及其内部类。这些类带有 `@DataBean` 注解，框架会通过反射创建实例。
- **风险**: 如果 nop-code-app 以 GraalVM native image 运行，序列化/反序列化这些类时会抛出运行时异常。
- **建议**: 在 reflect-config.json 中注册所有 `@DataBean` 类及其内部类。
- **信心水平**: 很可能
- **发现来源视角**: 组合爆炸测试者（native image + flow 分析组合）
- **去重**: 新发现。

---

## 已知未修复问题确认（来自 CRG 对比分析）

以下 CRG 对比分析 §八 确认的 Bug 经本次审查验证**仍然存在**：

| # | Bug | 状态 | 本次补充 |
|---|-----|------|---------|
| 1 | CallHierarchy ID 不一致 | **已纠正**：call hierarchy 的主路径实际正确（用 symbol.getId()），但 type hierarchy 确实受 resolveQualifiedNamesToIds 影响 → 合并到 AR-01 |
| 2 | NopCodeUsage 未填充 | **仍存在** | `findReferencedBy` 读空表 |
| 3 | Python/TS 适配器未注册 | **仍存在** | `CodeIndexService` 构造函数仅注册 JavaLanguageAdapter |
| 4 | sourceCode 返回 null | **仍存在** | `entityToFileResult()` 显式设为 null |
| 5 | indexId 硬编码 "test" | **已纠正**：仅存在于测试代码中，生产代码未硬编码 | CRG 分析 §八.5 的表述过于宽泛 |
| 6 | 每次图查询全量重建 | **仍存在** | 无缓存，但 AR-03 补充了 `indexFile` 遗漏缓存刷新的问题 |
| 7 | File ID 碰撞风险 | **仍存在** | 已从 `Math.abs(hashCode())` 改为 SHA-256 截断，但碰撞风险未消除 |

---

## 严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 2    | 数据一致性破坏（AR-01）、原生内存泄漏（AR-02） |
| P1      | 4    | 功能缺失（AR-06, AR-09）、缓存不一致（AR-03）、映射逻辑缺陷（AR-10） |
| P2      | 12   | 性能（AR-04/05/11/12/14/15/16）、静默失败（AR-08/13/18/21）、线程安全（AR-20）、GraalVM（AR-22） |
| P3      | 2    | 硬编码指标（AR-17）、内存缓慢增长（AR-19） |

---

*审查结束。如果需要深挖某个方向，可以追加第 2 轮。*
