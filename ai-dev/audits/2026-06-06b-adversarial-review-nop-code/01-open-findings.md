# Adversarial Review: nop-code — Open Findings

> **日期**: 2026-06-06（第 9 轮对抗性审查）
> **模块**: nop-code（13 个子模块）
> **审查类型**: 开放式对抗性审查
> **发现来源视角**: 增量索引链路审计者 + 前端-后端契约考古学家 + 并发安全侦探 + 查询正确性审计者 + 性能模式考古学家
> **审查范围**: 增量索引完整链路、view.xml/xmeta/BizModel 对齐、并发锁机制、搜索评分、TypeScript 适配器、ORM 约束完整性

## 去重确认

已审阅以下历史报告：
- r8 (2026-06-06): AR-94~AR-123（零事务、Leiden directed、Python TSNode、glob 匹配、语义边性能、缓存截断等）
- r7 (2026-06-02): AR-88~AR-93（@Auth 缺失、evictOverflow 无限循环等）
- deep-audit-2026-06-06: 21 维度全覆盖

**已修复确认（自 r8 以来）**：
- AR-88/AR-89（@Auth 系统性缺失）→ **已修复**。所有方法现在有 `@Auth` 注解。
- AR-90（detectDeadCode 为 @BizQuery）→ **已修复**。现在为 `@BizMutation` + `@Auth(roles = "admin")`。
- AR-94（全模块零事务）→ **已修复**。所有写操作现在使用 `transactionTemplate.runInTransaction`。
- AR-95（Leiden directed=true）→ **已修复**。现在为 `new Network(nNodes, false, edges, false, false)`。
- AR-96（Python TSNode.equals 引用比较）→ **已修复**。现在使用 `TSNode.eq(child, defNode)`。
- AR-112（glob 匹配 Pattern.quote 失效）→ **已修复**。改用直接 `.replace(".", "\\.").replace("*", ".*")`。

**仍存在的问题（未修复确认）**：
- AR-97（SpringEventSynthesizer 全关联）、AR-98（单例节点丢弃）、AR-99（startsWith 错配）
- AR-100（cascadeDelete 缺失）、AR-101~AR-111（persistFlows OOME、线程泄漏、CallGraph 线程安全、脏会话偏移、缓存截断、依赖图重复加载、缓存刷新竞态）
- AR-113~AR-123（注解 O(N²)、imports 丢失、ID 不一致、双缓存竞态、EdgeKey NPE、KnowledgeGap NPE、git 错误静默、死代码参数、评分无区分度、BC 无超时）

本轮发现均为新视角切入的新问题。

---

### [AR-124] 增量索引路径比较失效——每次都退化为全量重建

- **文件**: `nop-code/nop-code-service/.../impl/CodeIndexService.java:710-717` + `IncrementalDetector.java:36-44`
- **证据片段**:
  ```java
  // CodeIndexService.java:710-717
  IFingerprintStore store = new OrmFingerprintStore(daoProvider, ormTemplate, pathMapper);
  List<FileFingerprint> previousFingerprints = store.loadFingerprints(indexId);
  List<IResource> currentResources = collectResourcesFromVfs(vfs, vfsPath);
  IncrementalDetector detector = new IncrementalDetector();
  ChangeSet changes = detector.detectResourceChanges(previousFingerprints, currentResources);

  // IncrementalDetector.java:38-44 — 比较逻辑
  Map<String, FileFingerprint> prevMap = new HashMap<>();
  for (FileFingerprint fp : previous) {
      prevMap.put(fp.getFilePath(), fp);       // ← pathMapper(DB相对路径) → 仍是相对路径
  }
  Map<String, IResource> currentMap = new HashMap<>();
  for (IResource r : currentResources) {
      currentMap.put(r.getPath(), r);          // ← VFS 绝对路径如 /vfs/root/com/Foo.java
  }
  ```
- **严重程度**: P0
- **现状**: `OrmFingerprintStore.loadFingerprints` 对 DB 中的 `filePath`（如 `com/example/Foo.java`）执行 `pathMapper.apply()`。DB 存的是相对路径（由 `ProjectAnalyzer` 在 `indexDirectory` 中通过 `resource.getStdPath().substring(vfsPath.length())` 生成），不含 VFS 前缀。`pathMapper`（由 `buildPathMapper(vfsPath)` 构建）只剥离以 `vfsPath` 开头的路径，对已无前缀的相对路径不匹配，直接返回原值。而 `collectResourcesFromVfs` 返回的 `IResource.getPath()` 是 VFS 绝对路径（如 `/vfs/root/com/example/Foo.java`）。`IncrementalDetector` 用精确字符串匹配比较两者——**永远不等**。
- **风险**: 增量索引永远认为"全部文件已删除 + 全部文件新增"，等同于每次全量重建。指纹存储、增量检测的全部逻辑形同虚设。对大型项目，每次增量索引的耗时与全量索引一致，完全丧失增量优势。
- **建议**: 在 `triggerIncrementalIndex` 中，对 `currentResources` 统一应用 `pathMapper` 后再传入 `detectResourceChanges`，或将 `pathMapper` 传入 `IncrementalDetector` 用于实时映射。
- **信心水平**: 确定
- **发现来源视角**: 增量索引链路审计者

---

### [AR-125] NopCodeSymbol.view.xml GraphQL selection 引用 SymbolDTO 不存在的字段 `isStatic` / `isAbstract`

- **文件**: `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/pages/NopCodeSymbol/NopCodeSymbol.view.xml:71`
- **证据片段**:
  ```xml
  <initApi url="@query:NopCodeSymbol__getBySymbolId?id=$id&amp;indexId=$indexId"
    gql:selection="id,name,qualifiedName,kind,accessModifier,signature,returnType,
    line,endLine,deprecated,isStatic,isAbstract"/>
  ```
  而 `SymbolDTO` 中 Java Bean 属性名推导：`isStaticFlag()` → `staticFlag`、`isAbstractFlag()` → `abstractFlag`。
- **严重程度**: P1
- **现状**: 视图详情页请求 GraphQL 字段 `isStatic` 和 `isAbstract`，但 `SymbolDTO` 暴露的属性名是 `staticFlag` 和 `abstractFlag`（Java Bean 规范：`is` + `StaticFlag` → `staticFlag`）。GraphQL 引擎在返回时无法匹配这两个字段名，前端渲染时始终得到 null。
- **风险**: 符号详情表单中「是否静态」「是否抽象」两列永远显示为空/null。用户看到空白字段但不报错——**静默数据丢失**。
- **建议**: 将 view 中 `isStatic` 改为 `staticFlag`，`isAbstract` 改为 `abstractFlag`。或在 `SymbolDTO` 中增加 `isStatic`/`isAbstract` 计算属性作为别名。
- **信心水平**: 确定
- **发现来源视角**: 前端-后端契约考古学家

---

### [AR-126] indexFile 不更新 NopCodeIndex 统计计数——Dashboard 数据漂移

- **文件**: `nop-code/nop-code-service/.../impl/CodeIndexService.java:332-347`
- **证据片段**:
  ```java
  public CodeFileAnalysisResult indexFile(String indexId, String filePath, String sourceCode) {
      ...
      transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn ->
              ormTemplate.runInSession(session -> {
                  ensureIndexEntity(indexId, null, session);
                  persistSingleFileInSession(indexId, result, session);
                  return null;    // ← 没有更新 NopCodeIndex 的 fileCount/symbolCount
              }));
      invalidateAnalysisCache(indexId);
      return result;
  }
  ```
  对比 `triggerIncrementalIndex`（行 768）：
  ```java
  updateIndexStats(indexId);  // ← 存在
  ```
- **严重程度**: P1
- **现状**: `persistInSession`（全量索引路径）会显式设置 `indexEntity.setFileCount(...)` 和 `indexEntity.setSymbolCount(...)`，`triggerIncrementalIndex` 在完成后调用 `updateIndexStats(indexId)`。但 `indexFile`（单文件索引路径）两者都不做。`updateIndexStats` 方法存在（行 1538-1553），可以通过 count 查询准确更新，但 `indexFile` 未调用。
- **风险**: 通过 `indexFile` API 索引文件后，`getStats` 返回的 `fileCount`/`symbolCount` 停留在上一次全量/增量索引时的值。Dashboard 页面显示的统计数据漂移，用户无法信任。
- **建议**: 在 `persistSingleFileInSession` 之后添加 `updateIndexStats(indexId)` 调用。
- **信心水平**: 确定
- **发现来源视角**: 前端-后端契约考古学家

---

### [AR-127] deleteIndex 在并发 indexDirectory 持锁时移除锁对象——允许并发写入

- **文件**: `nop-code/nop-code-service/.../impl/CodeIndexService.java:311-328, 597`
- **证据片段**:
  ```java
  // indexDirectory (line 311-328)
  ReentrantLock lock = indexLocks.computeIfAbsent(indexId, k -> new ReentrantLock());
  lock.lock();
  try { ... } finally { lock.unlock(); }

  // deleteIndex (line 597) — 在 transaction 之后、无锁保护下移除
  indexLocks.remove(indexId);
  ```
- **严重程度**: P1
- **现状**: Thread A 持有 `lock L1` 执行 `indexDirectory("idx1")`。Thread B 执行 `deleteIndex("idx1")`，在事务提交后从 `indexLocks` map 中移除 L1。Thread C 执行 `indexDirectory("idx1")`，`computeIfAbsent` 创建新 `lock L2`，与 Thread A 并发执行写入操作。同时 `triggerIncrementalIndex` 和 `indexFile` 完全不使用锁（见 AR-128），因此绕过锁机制的路径更多。
- **风险**: 并发的 index 与 delete 操作导致数据不一致——delete 删除了记录，index 又写入，中间状态不可预测。产生孤儿实体或唯一约束冲突。
- **建议**: `deleteIndex` 应先获取同一把锁再执行删除，或者不移除锁对象（让 GC 处理无引用的锁），或者用 `ConcurrentHashMap.compute` 原子化 "获取锁+删除" 操作。
- **信心水平**: 确定
- **发现来源视角**: 并发安全侦探

---

### [AR-128] triggerIncrementalIndex 和 indexFile 缺少并发保护

- **文件**: `nop-code/nop-code-service/.../impl/CodeIndexService.java:697-777, 332-347`
- **证据片段**:
  ```java
  // indexDirectory (line 311) — 有锁
  ReentrantLock lock = indexLocks.computeIfAbsent(indexId, k -> new ReentrantLock());

  // triggerIncrementalIndex (line 697) — 无锁
  public int triggerIncrementalIndex(String indexId, String vfsPath, String manifestPath) {
      validatePath(vfsPath);
      // ... no indexLocks usage ...
  }

  // indexFile (line 332) — 无锁
  public CodeFileAnalysisResult indexFile(String indexId, ...) {
      // ... no indexLocks usage ...
  }
  ```
- **严重程度**: P1
- **现状**: `indexDirectory` 通过 `indexLocks` 保护并发，但 `triggerIncrementalIndex` 和 `indexFile` 完全不使用锁。同一 indexId 的 `indexDirectory + triggerIncrementalIndex` 或 `indexFile + indexDirectory` 可以同时执行。
- **风险**: 并发写入导致数据不一致（重复实体、孤儿记录、唯一约束冲突）。
- **建议**: 所有三个索引方法共享同一套 `indexLocks` 机制。提取公共的锁获取/释放逻辑。
- **信心水平**: 确定
- **发现来源视角**: 并发安全侦探

---

### [AR-129] findReferencedBy 只返回第一个同名符号的引用

- **文件**: `nop-code/nop-code-service/.../impl/CodeQueryService.java:569-579`
- **证据片段**:
  ```java
  List<NopCodeSymbol> symbols = symbolDao.findAllByQuery(symbolQuery);
  if (symbols.isEmpty()) return Collections.emptyList();
  String symbolId = symbols.get(0).getId();  // 只取第一个！
  ```
- **严重程度**: P1
- **现状**: `qualifiedName` 在索引内不保证唯一（例如同名的内部类、泛型擦除后的方法签名、不同文件中的 private 方法同名）。但代码只取查询结果的第一个符号 ID 来查找引用，其余同名符号的引用被完全忽略。
- **风险**: 用户查找 `com.example.Handler.process` 的所有引用，如果存在多个同名符号（不同文件、不同重载），只返回其中一个的引用，遗漏大量实际引用。
- **建议**: 遍历所有匹配的符号，合并去重引用结果。或者如果业务上 `(indexId, qualifiedName)` 确实唯一，在 ORM 层添加唯一约束明确约束。
- **信心水平**: 很可能
- **发现来源视角**: 查询正确性审计者

---

### [AR-130] getModuleDigest 加载全部符号忽略 dirPath 过滤——大量无用数据加载

- **文件**: `nop-code/nop-code-service/.../impl/CodeQueryService.java:246-252`
- **证据片段**:
  ```java
  // line 236-240 — 文件查询有 dirPath 过滤
  if (dirPath != null && !dirPath.isEmpty()) {
      fileQuery.addFilter(FilterBeans.startsWith("filePath", dirPath));
  }

  // line 246-252 — 符号查询完全没有 dirPath 过滤！
  QueryBean symQuery = new QueryBean();
  symQuery.addFilter(FilterBeans.eq("indexId", indexId));
  if (!includePrivate) {
      symQuery.addFilter(FilterBeans.ne("accessModifier", "PRIVATE"));
  }
  symQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
  List<NopCodeSymbol> allSymbols = symbolDao.findAllByQuery(symQuery);  // ALL symbols!
  ```
  对比 `getPublicSurface`（行 318）正确实现：
  ```java
  symQuery.addFilter(FilterBeans.in("fileId", fileIds));  // ← 正确过滤
  ```
- **严重程度**: P2
- **现状**: `getModuleDigest` 的文件查询应用了 `dirPath` 过滤，但符号查询加载了**整个索引**的全部符号。虽然在内存中通过 `symbolsByFileId` 关联只取 `files` 列表中文件对应的符号（结果正确），但对大型索引（10 万+ 符号），每次请求都全量加载。`getPublicSurface` 方法用 `FilterBeans.in("fileId", fileIds)` 正确过滤了符号查询。
- **风险**: 大型索引下 `getModuleDigest` 性能极差。对 10 万符号的索引，每次请求都从 DB 加载全部符号到内存。
- **建议**: 与 `getPublicSurface` 保持一致，在符号查询中添加 `FilterBeans.in("fileId", fileIds)`。
- **信心水平**: 确定
- **发现来源视角**: 查询正确性审计者

---

### [AR-131] view.xml 引用不存在的字典文件——UI 下拉框为空

- **文件**:
  - `nop-code/nop-code-web/.../call-hierarchy/call-hierarchy.view.xml:17`
  - `nop-code/nop-code-web/.../type-hierarchy/type-hierarchy.view.xml:17`
- **证据片段**:
  ```xml
  <!-- call-hierarchy.view.xml:17 -->
  <cell id="direction" label="方向" domain="string">
      <schema dict="code/call_direction"/>
  </cell>

  <!-- type-hierarchy.view.xml:17 -->
  <cell id="direction" label="方向" domain="string">
      <schema dict="code/hierarchy_direction"/>
  </cell>
  ```
  dict 目录 `/dict/code/` 下只有：`call_type`、`reference_kind`、`symbol_kind`、`relation_type`、`index_status`、`language`、`access_modifier`。
- **严重程度**: P2
- **现状**: `code/call_direction.dict.yaml` 和 `code/hierarchy_direction.dict.yaml` 不存在。调用链和类型层级查询页面的「方向」下拉框为空，用户无法通过 UI 选择合法方向。
- **风险**: 用户无法从 UI 选择 `callers`/`callees`/`both` 方向，需要手动输入参数。
- **建议**: 创建 `call_direction.dict.yaml`（值：`callers`/`callees`/`both`）和 `hierarchy_direction.dict.yaml`（值：`super`/`sub`/`both`）。
- **信心水平**: 确定
- **发现来源视角**: 前端-后端契约考古学家

---

### [AR-132] entityToInheritance 将已解析的符号 ID 错误映射为 `superTypeQualifiedName`

- **文件**: `nop-code/nop-code-service/.../impl/CodeIndexService.java:265-273`
- **证据片段**:
  ```java
  private CodeInheritance entityToInheritance(NopCodeInheritance entity) {
      CodeInheritance inh = new CodeInheritance();
      inh.setId(entity.getId());
      inh.setSubTypeId(entity.getSubTypeId());
      inh.setSuperTypeQualifiedName(entity.getSuperTypeId());  // ← 已是 ID，不是 QN!
      ...
  }
  ```
  在 `resolveQualifiedNamesToIds` 中 `superTypeId` 已从 QN 替换为 UUID。
- **严重程度**: P2
- **现状**: `resolveQualifiedNamesToIds` 将 `NopCodeInheritance.superTypeId` 从全限定名替换为符号 ID。但 `entityToInheritance` 从 DB 读回时仍将 `superTypeId` 映射到 `CodeInheritance.superTypeQualifiedName`。`CodeGraphService.buildTypeHierarchyResult` 随后将这个 ID 当作全限定名传入 `symbolTable.getByQualifiedName()`，查不到任何结果。
- **风险**: 类型层级查询丢失部分继承关系边。由于 `buildTypeHierarchyResult` 的查询包含 `in("subTypeId", batchIds)` 路径，子→父方向的查询仍然工作，但父→子方向受影响。
- **建议**: 要么不在 `resolveQualifiedNamesToIds` 中替换 `superTypeId`（保持原始 QN），要么在 `entityToInheritance` 中通过 ID 从 symbolTable 反查 QN。或者增加独立的 `superTypeId`（存 ID）和 `superTypeQualifiedName`（存 QN）字段。
- **信心水平**: 很可能
- **发现来源视角**: 查询正确性审计者

---

### [AR-133] persistSingleFileInSession 使用单文件符号表解析全局限定名——跨文件引用永不解析

- **文件**: `nop-code/nop-code-service/.../impl/CodeIndexService.java:1068-1070`
- **证据片段**:
  ```java
  private void persistSingleFileInSession(String indexId, CodeFileAnalysisResult result,
                                          IOrmSession session) {
      saveFileResultInSession(indexId, result, session);
      ...
      SymbolTable symbolTable = buildSymbolTableFromResult(result);  // 仅当前文件的符号
      resolveQualifiedNamesToIds(indexId, symbolTable, session);     // 跨文件引用无法解析
  }
  ```
  对比全量路径（行 854）:
  ```java
  resolveQualifiedNamesToIds(indexId, result.getGlobalSymbolTable(), session);  // 所有文件的符号
  ```
- **严重程度**: P2
- **现状**: `indexFile`（单文件 API）和 `triggerIncrementalIndex`（增量索引）使用 `persistSingleFileInSession`，其 `resolveQualifiedNamesToIds` 只能看到当前文件的符号表。继承关系的 `superTypeId`（如 `extends BaseClass`）如果 `BaseClass` 在另一个文件中定义，则永远不会被解析为符号 ID。
- **风险**: 通过 `indexFile` API 逐文件索引或增量索引的项目，跨文件的继承关系、注解引用的 ID 永远保留为全限定名字符串。类型层级查询和按注解查询的部分结果丢失。只有执行一次全量 `indexDirectory` 后才会全部修正。
- **建议**: 在 `persistSingleFileInSession` 中从 DB 加载已有的全局符号表（通过 `getOrRebuildSymbolTable`），或至少在完成后延迟执行一次全局解析。
- **信心水平**: 确定
- **发现来源视角**: 增量索引链路审计者

---

### [AR-134] OrmFingerprintStore.saveFingerprints 存在 N+1 查询

- **文件**: `nop-code/nop-code-service/.../incremental/OrmFingerprintStore.java:36-72`
- **证据片段**:
  ```java
  for (FileFingerprint fp : fingerprints) {
      String canonicalPath = pathMapper.apply(fp.getFilePath());
      String entityId = ...;
      NopCodeFile existing = findByIndexAndPath(fileDao, indexId, canonicalPath);  // 每条记录一次 DB 查询
      ...
  }

  // findByIndexAndPath (line 122-128)
  private NopCodeFile findByIndexAndPath(...) {
      QueryBean query = new QueryBean();
      query.addFilter(FilterBeans.eq("indexId", indexId));
      query.addFilter(FilterBeans.eq("filePath", filePath));
      List<NopCodeFile> results = fileDao.findAllByQuery(query);  // 全表扫描
      return results.isEmpty() ? null : results.get(0);
  }
  ```
- **严重程度**: P2
- **现状**: 每个指纹调用 `findByIndexAndPath` 执行一次 DB 查询。且 `findByIndexAndPath` 使用 `findAllByQuery` 加载完整实体（含 sourceCode CLOB）。对于 10000 个文件，就是 10000 次 DB 查询，每次可能加载 KB 级的 CLOB 数据。
- **风险**: 增量索引保存指纹阶段极慢。大型项目（10K+ 文件）可能需要数十秒甚至数分钟。
- **建议**: 批量加载该 indexId 的所有文件到 `Map<String, NopCodeFile>`（只查 id 和 filePath 列），然后在内存中查找。
- **信心水平**: 确定
- **发现来源视角**: 性能模式考古学家

---

### [AR-135] buildFilePathCache 每次搜索全量加载含 sourceCode CLOB 的文件表

- **文件**: `nop-code/nop-code-service/.../impl/CodeSearchService.java:188-199`
- **证据片段**:
  ```java
  private Map<String, String> buildFilePathCache(String indexId) {
      IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
      QueryBean fq = new QueryBean();
      fq.addFilter(FilterBeans.eq("indexId", indexId));
      fq.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
      List<NopCodeFile> files = fileDao.findAllByQuery(fq);  // 加载全部文件含 CLOB
      Map<String, String> cache = new HashMap<>();
      for (NopCodeFile f : files) {
          cache.put(f.getId(), f.getFilePath());  // 只用了 id 和 filePath
      }
      return cache;
  }
  ```
- **严重程度**: P2
- **现状**: `buildFilePathCache` 在 4 个搜索路径（`searchBySymbolName`、`searchFullText`、`searchCombined`、`searchViaEngine`）中每次搜索都调用。`findAllByQuery` 加载完整 `NopCodeFile` 实体（包含 `sourceCode` CLOB 字段），但只使用了 `id` 和 `filePath` 两个字段。
- **风险**: 高频搜索场景下产生严重内存压力和 DB 负载。对于含大型文件的索引，每次搜索可能加载数十 MB 的 sourceCode 数据到内存后立即丢弃。
- **建议**: 使用只查 `id` 和 `filePath` 的投影查询，或在服务层缓存此映射（TTL 缓存或与 `CodeCacheManager` 集成）。
- **信心水平**: 确定
- **发现来源视角**: 性能模式考古学家

---

### [AR-136] collectRelevantInheritances 在 MAX_QUERY_RESULTS 处静默截断——类型层次不完整

- **文件**: `nop-code/nop-code-service/.../impl/CodeGraphService.java:218-226`
- **证据片段**:
  ```java
  QueryBean q = new QueryBean();
  q.addFilter(FilterBeans.eq("indexId", indexId));
  q.addFilter(FilterBeans.or(
      FilterBeans.in("subTypeId", new ArrayList<>(batchIds)),
      FilterBeans.in("superTypeId", new ArrayList<>(batchQns))
  ));
  q.setLimit(CodeIndexService.MAX_QUERY_RESULTS);  // 10000
  for (NopCodeInheritance inh : inhDao.findAllByQuery(q)) {
      result.add(entityToInheritance(inh));
      if (visitedIds.add(subId)) idQueue.add(subId);  // 用截断后的数据继续 BFS
  }
  ```
- **严重程度**: P2
- **现状**: 继承关系查询设置了 `MAX_QUERY_RESULTS = 10000` 的 limit。如果某个 BFS 层级的继承关系超过 10000 条，结果被截断。截断后的数据被用于下一轮 BFS 扩展，导致类型层次不完整。无任何警告日志。
- **风险**: 大型项目的类型层次查询返回不完整结果。用户看到截断的继承树。
- **建议**: 使用分页加载替代硬限制，或至少在截断时记录 WARN 日志。
- **信心水平**: 确定
- **发现来源视角**: 查询正确性审计者

---

### [AR-137] tarjanSCC 递归深度无上限——StackOverflow 风险

- **文件**: `nop-code/nop-code-service/.../impl/CodeGraphService.java:712-741`
- **证据片段**:
  ```java
  private void tarjanDFS(String v, Map<String, List<String>> adj, int[] index,
                         ..., List<List<String>> result) {
      nodeIndex.put(v, index[0]);
      ...
      for (String w : adj.getOrDefault(v, Collections.emptyList())) {
          if (!nodeIndex.containsKey(w)) {
              tarjanDFS(w, adj, ...);  // 无界递归
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: Tarjan SCC 算法使用递归 DFS。递归深度等于图中的最长路径。对 10K+ 节点的依赖图，最长路径可能达到数千，触发 `StackOverflowError`。
- **风险**: 大型项目调用 `findCycles` 时 JVM 崩溃。
- **建议**: 改为迭代实现（显式栈），或设置最大递归深度并优雅降级。
- **信心水平**: 很可能
- **发现来源视角**: 性能模式考古学家

---

### [AR-138] 搜索评分函数区分大小写——搜索质量差

- **文件**: `nop-code/nop-code-service/.../impl/CodeSearchService.java:213-241`
- **证据片段**:
  ```java
  private double scoreSymbolNameMatch(String query, String name, String qualifiedName) {
      if (name != null && name.equals(query)) return 1.0;      // 区分大小写
      if (name != null && name.startsWith(query)) return 0.8;  // 区分大小写
      if (name != null && name.contains(query)) return 0.6;    // 区分大小写
      ...
  }
  ```
- **严重程度**: P2
- **现状**: 所有评分函数使用 `equals`/`startsWith`/`contains` 进行区分大小写的字符串比较。搜索 `user` 无法精确匹配 `User` 类名。对 Python/TypeScript 等不区分大小写的语言影响更严重。
- **风险**: 搜索结果排序不合理——精确匹配被排在不相关结果之后。用户体验差。
- **建议**: 使用 `toLowerCase()` 后比较，保留大小写完全一致时的额外加分。
- **信心水平**: 确定
- **发现来源视角**: 查询正确性审计者

---

### [AR-139] NopCodeDependency 缺少唯一约束——允许重复依赖边

- **文件**: `nop-code/model/nop-code.orm.xml:731-767`
- **证据片段**:
  ```xml
  <!-- NopCodeDependency — 无 unique-keys 定义 -->
  <entity ... tableName="nop_code_dependency">
      <indexes>
          <index name="ix_nop_code_dependency_index_id_source_file_path">
              <column name="indexId"/><column name="sourceFilePath"/>
          </index>
          <index name="ix_nop_code_dependency_index_id_target_file_path">
              <column name="indexId"/><column name="targetFilePath"/>
          </index>
      </indexes>
      <!-- 注意：没有 <unique-keys> -->
  </entity>
  ```
- **严重程度**: P2
- **现状**: 依赖表没有唯一约束。同一个 `(indexId, sourceFilePath, targetFilePath, importStatement)` 组合可被插入多次。`saveFileResultInSession` 中的 `usedDepIds` 防重复逻辑仅限单文件范围（每次调用重置），多次调用 `indexFile` 对同一文件索引会产生重复依赖。
- **风险**: 依赖图出现重复边，`getDepGraph`、`findCycles`、`getDeps` 等方法结果被重复数据污染。
- **建议**: 添加 `<unique-key columns="indexId,sourceFilePath,targetFilePath,importStatement" name="uk_dependency_unique"/>`。
- **信心水平**: 很可能
- **发现来源视角**: 性能模式考古学家

---

### [AR-140] TypeScript 适配器 getNodeText 每次调用重新编码整个源文件为 byte[]

- **文件**: `nop-code/nop-code-lang-typescript/.../TypeScriptCodeFileAnalyzer.java:507-518`
- **证据片段**:
  ```java
  private String getNodeText(TSNode node, String source) {
      int startByte = node.getStartByte();
      int endByte = node.getEndByte();
      if (startByte >= endByte) return "";
      byte[] bytes = source.getBytes(StandardCharsets.UTF_8);  // 每次调用都重新编码!
      if (endByte > bytes.length) endByte = bytes.length;
      return new String(bytes, startByte, endByte - startByte, StandardCharsets.UTF_8);
  }
  ```
- **严重程度**: P2
- **现状**: tree-sitter 使用字节偏移量，需要 `byte[]`。但 `getNodeText` 每次调用执行 `source.getBytes(StandardCharsets.UTF_8)`。对 100KB 的文件，AST 遍历调用此方法数百次，每次创建 100KB 的 `byte[]`。
- **风险**: TypeScript 文件分析性能严重下降，频繁 GC 压力。
- **建议**: 在 `analyze` 方法开始时一次性编码为 `byte[]`，将 `bytes` 作为参数传递。
- **信心水平**: 确定
- **发现来源视角**: 性能模式考古学家

---

### [AR-141] TypeScript 适配器 buildQualifiedPrefix 使用原始文件路径生成全限定名

- **文件**: `nop-code/nop-code-lang-typescript/.../TypeScriptCodeFileAnalyzer.java:591-601`
- **证据片段**:
  ```java
  private String buildQualifiedPrefix(String filePath) {
      if (filePath == null || filePath.isEmpty()) return "";
      String normalized = filePath.replace('\\', '/');
      int dotIdx = normalized.lastIndexOf('.');
      if (dotIdx > 0) normalized = normalized.substring(0, dotIdx);
      return normalized.replace('/', '.');
  }
  ```
- **严重程度**: P2
- **现状**: 将 `src/utils/helpers.ts` 转为 `src.utils.helpers`，然后类 `Foo` 的 `qualifiedName` 变为 `src.utils.helpers.Foo`。这不是有效的 TypeScript 全限定名。Java 适配器正确地从 `package` 语句推断全限定名。
- **风险**: TypeScript 符号的 `qualifiedName` 与 Java/Python 适配器的命名约定不兼容。跨语言索引中 Java 和 TypeScript 符号之间无法建立关系。`findSymbolByQualifiedName` 查询 TypeScript 符号时需要知道完整的文件路径前缀。
- **建议**: 从 `tsconfig.json` 或 `package.json` 推断 `baseUrl`/`rootDir`，计算相对路径作为前缀。
- **信心水平**: 很可能
- **发现来源视角**: 增量索引链路审计者

---

### [AR-142] usageCount 在 xmeta/ORM 中定义但从未被赋值——死元数据

- **文件**: `nop-code/nop-code-meta/.../_NopCodeSymbol.xmeta:75-78`
- **证据片段**:
  ```xml
  <prop name="usageCount" displayName="使用次数" propId="14" queryable="true" sortable="true"
        insertable="true" updatable="true">
      <schema type="java.lang.Integer"/>
  </prop>
  ```
  `CodeIndexService` 全文无 `setUsageCount` 调用。
- **严重程度**: P3
- **现状**: `NopCodeSymbol` 的 ORM 实体有 `usageCount` 字段，xmeta 也暴露了它为 `queryable`、`sortable`，但索引管线从未设置此字段。DB 中所有记录的 `USAGE_COUNT` 列均为 NULL 或 0。
- **风险**: UI 上的「使用次数」列永远显示空/0，按 `usageCount` 排序无效。
- **建议**: 要么在 `saveFileResultInSession` 中计算并设置 usageCount，要么从 xmeta 中标记为 `published="false"`。
- **信心水平**: 确定
- **发现来源视角**: 前端-后端契约考古学家

---

### [AR-143] batchGetTypeOutlines 对每个 qualifiedName 独立查库（N+1）

- **文件**: `nop-code/nop-code-service/.../impl/CodeQueryService.java:562-567`
- **证据片段**:
  ```java
  List<TypeOutlineDTO> batchGetTypeOutlines(String indexId, List<String> qualifiedNames) {
      return qualifiedNames.stream()
              .map(qn -> getTypeOutline(indexId, qn))  // 每个 qn 一次 DB 查询
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
  }
  ```
- **严重程度**: P3
- **现状**: 批量接口对每个 `qualifiedName` 调用 `getTypeOutline`，后者执行 2 次 DB 查询（查找符号 + 查找子符号）。对于 50 个类型名，就是 100 次 DB 查询。
- **风险**: 批量 API 性能差，随输入规模线性退化。
- **建议**: 预加载所有匹配的符号，然后批量查询子符号（用 `in("parentId", ids)`）。
- **信心水平**: 确定
- **发现来源视角**: 性能模式考古学家

---

### [AR-144] NopCodeIndexBizModel.incrementalStatusMap 使用无序迭代器驱逐

- **文件**: `nop-code/nop-code-service/.../entity/NopCodeIndexBizModel.java:336-343`
- **证据片段**:
  ```java
  private void evictStatusMap() {
      while (incrementalStatusMap.size() > MAX_STATUS_ENTRIES) {
          String key = incrementalStatusMap.keySet().iterator().next();
          if (key != null) {
              incrementalStatusMap.remove(key);
          }
      }
  }
  ```
- **严重程度**: P3
- **现状**: `ConcurrentHashMap.keySet().iterator()` 不保证任何顺序。驱逐可能移除刚插入的条目。与 LRU/LFU 等合理驱逐策略相比，这种随机驱逐可能导致「刚触发的增量索引状态被立即丢弃」。
- **风险**: 客户端调用 `getIncrementalStatus(indexId)` 可能返回 null（状态被其他索引操作驱逐），前端无法显示最近一次索引的完成状态。
- **建议**: 使用 `LinkedHashMap` + accessOrder 或 Guava Cache 实现 LRU 驱逐。
- **信心水平**: 确定
- **发现来源视角**: 并发安全侦探

---

## 总评

本轮审查从增量索引完整链路、前端-后端契约对齐、并发锁机制三个全新视角切入，发现了 nop-code 模块的一个架构级缺陷和多个系统性问题。

**最严重的发现是 AR-124（增量索引路径比较失效）**——这是一个 P0 级别的架构缺陷。`OrmFingerprintStore.loadFingerprints` 返回的路径格式（相对路径）与 `collectResourcesFromVfs` 返回的资源路径格式（VFS 绝对路径）不匹配，导致 `IncrementalDetector.detectResourceChanges` 的字符串精确比较永远失败。结果是每次增量索引都退化为全量重建，增量索引的全部优化逻辑形同虚设。这个 bug 存在于整个增量索引功能的入口路径，是一个典型的"路径格式约定不一致"问题。

前端-后端契约层面发现了两个问题：view.xml 中的 GraphQL selection 字段名（`isStatic`/`isAbstract`）与 SymbolDTO 实际暴露的属性名（`staticFlag`/`abstractFlag`）不匹配（AR-125），以及两个 view.xml 引用了不存在的字典文件（AR-131）。这些是纯前端配置问题，不会导致错误但会导致功能失效（静默数据丢失）。

并发安全层面发现 `indexLocks` 的保护范围不完整：只有 `indexDirectory` 使用锁，`triggerIncrementalIndex` 和 `indexFile` 完全不使用锁（AR-128），且 `deleteIndex` 在事务完成后移除锁对象导致竞态（AR-127）。

**本次审查最值得关注的 3 个方向**：

1. **增量索引完全失效**（AR-124）——每次增量索引等于全量重建，修复简单但影响巨大
2. **并发锁机制不完整**（AR-127 + AR-128）——三个索引方法只有一个有锁保护，deleteIndex 的锁移除存在竞态
3. **前端-后端契约漂移**（AR-125 + AR-131）——view.xml 中的字段名和字典引用与实际不匹配

**已修复确认**：自上一轮审查以来，6 个问题已修复（AR-88/89 @Auth、AR-90 detectDeadCode 分类、AR-94 零事务、AR-95 Leiden directed、AR-96 Python TSNode、AR-112 glob 匹配），显示团队在积极修复审计发现。

## 本次审查的盲区自评

1. **OrmFingerprintStore 的 deleteByPaths / deleteByIndex 路径**：仅审计了 saveFingerprints 和 loadFingerprints，未深入检查删除路径的正确性。
2. **CodeSearchService 的 searchViaEngine（搜索引擎集成）**：未深入检查 `ISearchEngine` 的实现和交互。
3. **nop-code-codegen 模板**：未审查代码生成模板的正确性（AR-104 模板 Javadoc 问题是否已修复）。
4. **NopCodeSemanticEdge 相关查询**：语义边的存储和查询路径未被覆盖。
5. **端到端运行验证**：所有发现基于静态代码分析，未实际运行测试验证。

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | 增量索引路径比较失效（AR-124） |
| P1      | 4    | view 字段名不匹配（AR-125）、indexFile 不更新统计（AR-126）、deleteIndex 锁竞态（AR-127）、triggerIncrementalIndex/indexFile 无锁（AR-128）、findReferencedBy 只取第一个（AR-129） |
| P2      | 10   | getModuleDigest 全量加载（AR-130）、view 字典缺失（AR-131）、ID/QN 混淆（AR-132）、单文件跨引用（AR-133）、指纹 N+1（AR-134）、搜索加载 CLOB（AR-135）、继承截断（AR-136）、Tarjan 递归（AR-137）、搜索大小写（AR-138）、依赖无唯一约束（AR-139）、TS getNodeText 重编码（AR-140）、TS 全限定名（AR-141） |
| P3      | 3    | usageCount 死字段（AR-142）、batchOutline N+1（AR-143）、statusMap 驱逐策略（AR-144） |
