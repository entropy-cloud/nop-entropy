# 对抗性审查报告（第 4 轮）：nop-code 模块

**审查日期**: 2026-06-01
**审查方法**: 开放式发现导向（adversarial review），无预设维度
**审查范围**: nop-code 全模块 14 个子模块，聚焦之前 3 轮审查自评承认的盲区
**去重基线**: 
- 第 1 轮: `ai-dev/audits/2026-05-29-adversarial-review-nop-code/01-open-findings.md`（AR-01 至 AR-27）
- 第 2 轮: `ai-dev/audits/2026-05-29-adversarial-review-nop-code-r2/01-open-findings.md`（AR-28 至 AR-46）
- 第 3 轮: `ai-dev/audits/2026-05-29-adversarial-review-nop-code-r3/01-open-findings.md`（AR-47 至 AR-58）
- 深度审计: `2026-05-25-deep-audit-nop-code-full/`、`2026-05-29-deep-audit-nop-code-full/`
**启发式视角**: 异常路径侦探（跨层数据流断裂）+ 新人开发者（"这段代码真的能执行到吗？"）+ 10x 规模运维者（大规模数据静默截断）

---

## 之前已报告问题的修复状态

| AR编号 | 描述 | 状态 |
|--------|------|------|
| AR-01 | resolveQualifiedNamesToIds 破坏类型层级查询 | **部分修复** — buildTypeHierarchy 有双重匹配 workaround（ID+QN），但数据模型层面 superTypeId 仍从 QN 被覆盖为 ID |
| AR-02 | TSTree 未关闭导致原生内存泄漏 | **已修复** — try-with-resources / 手动 close |
| AR-03 | 增量分析退化为全量分析 | **已修复** — triggerIncrementalIndex 实现了真正的增量路径 |
| AR-09 | FlowDetector HashMap 线程不安全 | **仍存在** |
| AR-10 | Tree-sitter 原生内存泄漏 | **已修复** |
| AR-28 | TypeScript walkNodeForCalls 死代码 | **仍存在** |
| AR-29 | 反向依赖 BFS 深度始终为 1 | **已修复** |
| AR-30 | deleteFileRecords 缺少 NopCodeUsage 删除 | **部分修复** — 删除了本文件的 NopCodeUsage，但跨文件引用仍遗漏（详见 AR-66） |
| AR-31 | indexFile 不调用 invalidateAnalysisCache | **已修复** |
| AR-32 | extractFileKey 返回包名而非类名 | **仍存在** |
| AR-47 | VFS 索引路径跳过语义边持久化 | **已修复** |
| AR-48 | DeadCodeDetector 框架排除逻辑全死代码 | **已修复** |
| AR-49 | testGap 常量膨胀 | **仍存在** — `double testGap = 1.0;` 硬编码 |

---

## 发现总览

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 1 | extData filePath 从未写入导致 4 个分析功能失效(1) |
| P1 | 3 | 外键删除顺序错误(1)、缓存超限静默返回不完整数据(1)、跨文件 Call 引用未清理(1) |
| P2 | 5 | Lock 泄漏(1)、数据重建丢失关系(1)、全量加载(1)、xmeta 死配置(1)、线程池泄漏(1) |
| P3 | 0 | — |
| **总计** | **9** | AR-59 ~ AR-67 |

---

## P0 发现

### [AR-59] symbol.extData 从未写入 filePath——4 个下游分析组件的文件路径关联全部失效

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1579-1601`
- **证据片段**:
  ```java
  // enrichSymbolsWithAnnotations (line 1579-1601):
  private void enrichSymbolsWithAnnotations(CodeFileAnalysisResult file) {
      // ... 构建 symbolAnnotations 映射 ...
      for (CodeSymbol sym : file.getSymbols()) {
          List<String> annots = symbolAnnotations.get(sym.getId());
          if (annots != null && !annots.isEmpty()) {
              sym.setExtData(ExtDataHelper.setAnnotations(sym.getExtData(), annots));
              // ← 只写入 annotations，从未写入 filePath
          }
      }
  }
  ```
  全仓库搜索 `ExtDataHelper.setFilePath` 在生产代码中返回 **0 结果**。只有测试文件手动设置了 `extData` 中的 `filePath`。

  4 个下游组件全部调用 `ExtDataHelper.extractFilePath()` 读取 filePath，永远返回 null：
  ```java
  // DeadCodeDetector.java:362
  return ExtDataHelper.extractFilePath(symbol.getExtData());   // → null
  
  // FlowDetector.java:523
  return ExtDataHelper.extractFilePath(symbol.getExtData());   // → null
  
  // ChangeAnalyzer.java:232
  return ExtDataHelper.extractFilePath(symbol.getExtData());   // → null
  
  // ImpactAnalyzer.java:334
  return ExtDataHelper.extractFilePath(symbol.getExtData());   // → null
  ```
- **严重程度**: P0
- **现状**: `enrichSymbolsWithAnnotations` 是唯一向 symbol 的 `extData` 写入数据的生产代码，但它只写入 `annotations`。4 个下游分析组件（DeadCodeDetector、FlowDetector、ChangeAnalyzer、ImpactAnalyzer）都通过 `ExtDataHelper.extractFilePath()` 从 `extData` 读取 `filePath`，但该字段从未被写入。结果：死代码报告的 filePath 为 null、执行流变更分析无法匹配变更文件、影响分析的 `ImpactedSymbolDTO.filePath` 永远为 null。测试通过是因为测试代码手动设置了 `extData` 中的 `filePath`。
- **风险**: 4 个核心分析功能的文件路径关联在生产环境中完全失效。用户无法从任何 API 返回值中发现这个问题——结果只是"缺少 filePath"而非报错。
- **建议**: 在 `enrichSymbolsWithAnnotations` 中同时写入 filePath：
  ```java
  sym.setExtData(ExtDataHelper.setFilePath(sym.getExtData(), file.getFilePath()));
  ```
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（"enrichSymbolsWithAnnotations 是唯一写入 extData 的地方，但它写了什么？"）

---

## P1 发现

### [AR-60] deleteIndex 外键删除顺序错误——NopCodeSemanticEdge 在 NopCodeSymbol 之后删除

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:483-497`
- **证据片段**:
  ```java
  // deleteIndex 中的删除顺序:
  deleteEntitiesPaged(session, NopCodeUsage.class, "indexId", indexId);           // line 483
  // ... Flow + FlowMembership ...
  deleteEntitiesPaged(session, NopCodeAnnotationUsage.class, "indexId", indexId); // line 491
  deleteEntitiesPaged(session, NopCodeInheritance.class, "indexId", indexId);     // line 492
  deleteEntitiesPaged(session, NopCodeCall.class, "indexId", indexId);            // line 493
  deleteEntitiesPaged(session, NopCodeSymbol.class, "indexId", indexId);          // line 494 ← 删 Symbol
  deleteEntitiesPaged(session, NopCodeFile.class, "indexId", indexId);            // line 495
  deleteEntitiesPaged(session, NopCodeDependency.class, "indexId", indexId);      // line 496
  deleteEntitiesPaged(session, NopCodeSemanticEdge.class, "indexId", indexId);    // line 497 ← 在 Symbol 之后！
  ```
  ORM 模型定义了 NopCodeSemanticEdge → NopCodeSymbol 的外键：
  ```xml
  <to-one name="sourceSymbol" refEntityName="...NopCodeSymbol">
      <join><on leftProp="sourceSymbolId" rightProp="id"/></join>
  </to-one>
  <to-one name="targetSymbol" refEntityName="...NopCodeSymbol">
      <join><on leftProp="targetSymbolId" rightProp="id"/></join>
  </to-one>
  ```
- **严重程度**: P1
- **现状**: NopCodeSemanticEdge 有指向 NopCodeSymbol 的外键（sourceSymbolId、targetSymbolId），但 SemanticEdge 的删除在 Symbol 删除之后。如果数据库启用外键约束（如生产 MySQL InnoDB），删除 Symbol 时会触发外键约束违反。
- **风险**: 在启用 FK 检查的数据库上 `deleteIndex` 会失败。H2 可能不强制检查，掩盖了问题。
- **建议**: 将 NopCodeSemanticEdge 删除移到 NopCodeSymbol 之前。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-61] CodeCacheManager 超限时静默返回不完整数据——所有图分析结果不可信

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeCacheManager.java:79-107,109-137`
- **证据片段**:
  ```java
  // rebuildSymbolTable (line 79-107):
  if (totalLoaded >= MAX_CACHE_SYMBOLS) {  // MAX_CACHE_SYMBOLS = 100000
      LOG.warn("Symbol cache for index {} exceeded MAX_CACHE_SYMBOLS({}), returning partial data",
              indexId, MAX_CACHE_SYMBOLS, totalLoaded);
      return table;  // 返回不完整的符号表！
  }
  
  // rebuildCallGraph (line 109-137):
  if (totalLoaded >= MAX_CACHE_EDGES) {  // MAX_CACHE_EDGES = 500000
      LOG.warn("Call graph cache for index {} exceeded MAX_CACHE_EDGES({}), returning partial data",
              indexId, MAX_CACHE_EDGES, totalLoaded);
      return callGraph;  // 返回不完整的调用图！
  }
  ```
- **严重程度**: P1
- **现状**: 超限后返回不完整数据，仅输出 WARN 日志。以下分析全部在**不完整数据**上运行：社区检测 (`detectCommunities`)、图分析 (`getGraphAnalysis`)、影响分析 (`getImpactAnalysis`)、关键节点 (`getCriticalNodes`)、死代码检测 (`detectDeadCode`)、执行流检测 (`detectFlows`)。无任何机制通知调用方数据不完整。
- **风险**: 对大型项目（>10 万符号），所有图分析结果静默失效。API 正常返回，但结果不包含完整数据集的符号。
- **建议**: 在所有 DTO 结果中添加 `partialData` 标志，或抛出异常强制调用方处理。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-66] deleteFileRecords 不清理跨文件的 Call 引用——增量索引产生孤儿记录

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1204-1228`
- **证据片段**:
  ```java
  private void deleteFileRecords(String indexId, List<String> filePaths) {
      for (String filePath : filePaths) {
          String fileId = generateFileId(indexId, filePath);
          List<String> symbolIds = findSymbolIdsByFileId(fileId);
          
          deleteEntitiesByFilter(NopCodeCall.class, "fileId", fileId);         // 只删本文件的 call
          deleteEntitiesByFilter(NopCodeSymbol.class, "fileId", fileId);
          deleteEntitiesByFilter(NopCodeUsage.class, "fileId", fileId);        // 只删本文件的 usage
          deleteEntitiesByFilter(NopCodeDependency.class, "sourceFilePath", filePath);
          deleteRelationalBySymbolIds(NopCodeAnnotationUsage.class, "annotatedSymbolId", symbolIds);
          deleteRelationalBySymbolIds(NopCodeInheritance.class, "subTypeId", symbolIds);
          deleteRelationalBySymbolIds(NopCodeSemanticEdge.class, "sourceSymbolId", symbolIds);
          deleteRelationalBySymbolIds(NopCodeSemanticEdge.class, "targetSymbolId", symbolIds);
          deleteRelationalBySymbolIds(NopCodeFlowMembership.class, "symbolId", symbolIds);
          // ❌ 缺少: 其他文件中 calleeId 指向这些 symbolIds 的 NopCodeCall
          // ❌ 缺少: 其他文件中引用这些 symbolIds 的 NopCodeUsage (如果 fileId 不是当前文件)
      }
  }
  ```
- **严重程度**: P1
- **现状**: 文件 A 的方法 `UserService.getUser()`（symbolId=`s1`）被文件 B 调用，产生 `NopCodeCall(callerId=s_b, calleeId=s1, fileId=fileB)`。当文件 A 被增量重索引时，`s1` 被删除，但 `fileB` 中 `calleeId=s1` 的 call 记录仍然存在，指向已删除的符号。`NopCodeUsage` 也有类似问题——跨文件引用的 usage 不在当前文件的 `fileId` 过滤范围内。
- **风险**: 增量索引累积孤儿记录，污染调用层次查询、影响分析等后续分析结果。
- **建议**: 在 `deleteFileRecords` 中增加基于 `symbolIds` 清理其他文件的引用：
  ```java
  deleteRelationalBySymbolIds(NopCodeCall.class, "calleeId", symbolIds);
  deleteRelationalBySymbolIds(NopCodeUsage.class, "targetSymbolId", symbolIds);
  ```
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

## P2 发现

### [AR-62] indexLocks ConcurrentHashMap 永不清理——索引删除后 Lock 对象泄漏

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:98,235`
- **证据片段**:
  ```java
  // line 98:
  private final ConcurrentHashMap<String, ReentrantLock> indexLocks = new ConcurrentHashMap<>();
  
  // line 235:
  ReentrantLock lock = indexLocks.computeIfAbsent(indexId, k -> new ReentrantLock());
  ```
  `deleteIndex` (line 471-502) 中没有 `indexLocks.remove(indexId)` 调用。
- **严重程度**: P2
- **现状**: 每次创建新 index 都会通过 `computeIfAbsent` 添加一个 lock entry，但删除 index 后 entry 永远留在 map 中。
- **风险**: 长期运行的应用频繁创建/删除索引会导致内存泄漏。单个 entry 很小（ReentrantLock），但会无限累积。
- **建议**: 在 `deleteIndex` 中添加 `indexLocks.remove(indexId)`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-63] entityToFileResult 数据重建丢失所有关系数据——GraphQL API 返回不完整的文件信息

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java:36-45`
- **证据片段**:
  ```java
  private CodeFileAnalysisResult entityToFileResult(NopCodeFile entity) {
      CodeFileAnalysisResult result = new CodeFileAnalysisResult();
      result.setFilePath(entity.getFilePath());
      result.setPackageName(entity.getPackageName());
      result.setLanguage(...);
      result.setLineCount(...);
      result.setSourceCode(entity.getSourceCode());
      return result;
      // symbols, calls, inheritances, annotationUsages, imports, semanticEdges 全部丢失
  }
  ```
- **严重程度**: P2
- **现状**: `getFiles()`、`findFilesPage()`、`getFile()` 返回的 `CodeFileAnalysisResult` 中，`symbols`、`calls`、`inheritances`、`annotationUsages`、`imports` 全部为空列表/null。
- **风险**: GraphQL API 返回的文件数据缺少符号信息，前端展示不完整。
- **建议**: 要么在 DTO 中明确标注这些字段不可用，要么在 `entityToFileResult` 中查询并填充关联数据。
- **信心水平**: 确定

---

### [AR-64] findImplementations 全量加载所有符号到内存——单次查询加载整个索引

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java:682-691`
- **证据片段**:
  ```java
  IEntityDao<NopCodeSymbol> symDaoForInh = daoProvider.daoFor(NopCodeSymbol.class);
  Map<String, String> idToQn = new HashMap<>();
  QueryBean allSymForInhQuery = new QueryBean();
  allSymForInhQuery.addFilter(FilterBeans.eq("indexId", indexId));
  allSymForInhQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);  // 10000
  for (NopCodeSymbol sym : symDaoForInh.findAllByQuery(allSymForInhQuery)) {
      if (sym.getQualifiedName() != null) {
          idToQn.put(sym.getId(), sym.getQualifiedName());
      }
  }
  ```
- **严重程度**: P2
- **现状**: 为了查找一个接口的实现类，加载了整个索引的全部符号（上限 10000）到内存，只为构建 ID→QN 映射。
- **风险**: 大索引上每次 `findImplementations` 调用都是全表扫描。
- **建议**: 改用数据库 JOIN 查询或分批按需加载。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-65] NopCodeSymbol.xmeta 虚拟查询属性是死配置

- **文件**: `nop-code-meta/src/main/resources/_vfs/nop/code/model/NopCodeSymbol/NopCodeSymbol.xmeta` (全部 15 行)
- **证据片段**:
  ```xml
  <prop name="query" displayName="名称关键词" queryable="true" insertable="false" updatable="false">
      <schema type="java.lang.String" precision="200"/>
  </prop>
  <prop name="kinds" displayName="符号类型" queryable="true" insertable="false" updatable="false">
      <schema type="java.util.List&lt;java.lang.String&gt;" precision="200"/>
  </prop>
  <prop name="packageName" displayName="包名" queryable="true" insertable="false" updatable="false">
      <schema type="java.lang.String" precision="500"/>
  </prop>
  ```
  BizModel 使用自定义 `findPage_symbols` 方法直接处理这些参数，完全绕过了标准 CRUD 查询管道：
  ```java
  PageBean<SymbolDTO> findPage_symbols(
          @Name("query") @Optional String query,
          @Name("kinds") @Optional List<String> kinds,
          @Name("packageName") @Optional String packageName, ...)
  ```
- **严重程度**: P2
- **现状**: 3 个虚拟属性在 xmeta 中定义为 `queryable="true"`，但框架的 CRUD 查询管道不会处理它们。`findPage_symbols` 完全绕过标准查询机制，直接读取参数。如果未来有人通过标准 CRUD API（`NopCodeSymbol__findPage`）使用这些虚拟属性查询，将得到不正确的结果。
- **风险**: xmeta 定义与实际查询逻辑不一致，误导依赖 schema 的开发者。
- **建议**: 要么删除这些死属性定义，要么在 `_NopCodeSymbol.xmeta` 的 gen-extends 中正确实现查询逻辑。
- **信心水平**: 确定

---

### [AR-67] CommunityDetector.runWithTimeout 线程池不保证关闭——超时后线程泄漏

- **文件**: `nop-code-graph/src/main/java/io/nop/code/graph/community/CommunityDetector.java:741-749`
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
  （注：此问题在第 2 轮 AR-39 中已报告，确认仍存在，但修复未执行。）
- **严重程度**: P2
- **现状**: `shutdownNow()` 中断线程但不等待其终止。Leiden 算法是 CPU 密集计算，通常不响应中断。每次超时调用泄漏一个线程。
- **风险**: 多次超时后累积活跃线程，最终耗尽线程资源。
- **建议**: 改用 `executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS)`，或使用 daemon 线程工厂。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

## 去重说明

以下前 3 轮审查已报告的问题经确认仍存在但未重新展开：

- AR-09 (FlowDetector HashMap 线程不安全) 仍存在
- AR-28 (TypeScript walkNodeForCalls 死代码) 仍存在
- AR-30 (deleteFileRecords 缺少 NopCodeUsage 删除) 部分修复，跨文件引用仍未清理（详见 AR-66）
- AR-32 (extractFileKey 返回包名而非类名) 仍存在
- AR-39 (runWithTimeout 线程泄漏) 仍存在（等同 AR-67）
- AR-49 (testGap 常量膨胀) 仍存在

---

## 总评

本次第 4 轮对抗性审查在之前 3 轮（AR-01 至 AR-58）和 2 次深度审计的基础上，聚焦审查了之前自评承认的盲区。核心发现可以用一句话概括：**nop-code 的 symbol extData 数据管线存在一条关键断裂——filePath 从未写入但被 4 个组件读取**。

1. **extData filePath 断裂（AR-59）**：这是本轮最重要的发现。`enrichSymbolsWithAnnotations` 是唯一向 symbol 的 extData 写入数据的生产代码，但它只写入 `annotations`。4 个下游分析组件全部通过 `ExtDataHelper.extractFilePath()` 读取 `filePath`，结果永远为 null。这个问题的根因是"数据写入方和数据读取方之间的契约没有以任何形式表达"——写入方不知道读取方需要 `filePath`，读取方不知道写入方不提供它。测试通过是因为测试代码手动设置了 extData 中的 filePath，形成了"测试真实但生产不真实"的假象。

2. **增量索引数据完整性（AR-60 + AR-66）**：`deleteFileRecords` 的清理粒度不够——只删除当前文件的关联数据，不清理其他文件中对已删除符号的跨文件引用（NopCodeCall 的 calleeId、NopCodeUsage 的 targetSymbolId）。同时 `deleteIndex` 的外键删除顺序在启用 FK 检查的数据库上会失败。这两个问题在持续增量索引场景下导致数据逐步腐化。

3. **大规模数据静默截断（AR-61）**：`CodeCacheManager` 的硬限制导致超限后所有图分析在不完整数据上运行，仅有一条 WARN 日志。API 正常返回，调用方无法感知结果不完整。

## 本次审查盲区自评

| 维度 | 覆盖程度 | 说明 |
|------|----------|------|
| BizLoader 方法数据重建 | ✅ 已覆盖 | 发现 AR-63 (entityToFileResult 丢失关系数据) |
| NopCodeSymbol.xmeta 虚拟查询属性 | ✅ 已覆盖 | 发现 AR-65 (死配置) |
| 图算法数学正确性 | ✅ 已覆盖 | Leiden 调用外部库，参数构造正确 |
| ISearchEngine 集成 | ⚠️ 部分覆盖 | 验证了 fallback 路径，未测试实际搜索引擎运行时 |
| 并发压测场景 | ⚠️ 部分覆盖 | 发现 AR-62 (lock 泄漏)，未做实际并发测试 |
| 前端页面和 Web 层 | ✅ 已覆盖 | 检查了 view.xml 和 action-auth.xml，结构合理 |
| CodeIndexService 巨型类 | ✅ 已覆盖 | 深入阅读了全部 1634 行 |
| GraphQL schema 与 BizModel 对齐 | ⚠️ 部分覆盖 | 检查了方法签名，未验证运行时 schema 生成 |
| nop-code-codegen 代码生成模板 | ❌ 未覆盖 | 模板文件较大且生成结果已通过编译验证 |

---

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 1 | 数据管线断裂(1) |
| P1 | 3 | 删除顺序错误(1)、静默截断(1)、跨文件引用遗漏(1) |
| P2 | 5 | 资源泄漏(2)、数据重建不完整(1)、全量加载(1)、死配置(1) |
| P3 | 0 | — |
| **总计** | **9** | AR-59 ~ AR-67 |
