# Adversarial Review: nop-code — Open Findings

> **日期**: 2026-06-06（第 10 轮对抗性审查）
> **模块**: nop-code（13 个子模块）
> **审查类型**: 开放式对抗性审查
> **发现来源视角**: 并发安全侦探 + 安全审计者 + 数据完整性审计者 + ORM 契约考古学家 + 资源泄漏侦探
> **审查范围**: 并发模型、BizModel 安全、ORM 级联配置、图算法线程安全、缓存封装完整性

## 去重确认

已审阅以下历史报告：
- r9 (2026-06-06b): AR-124~AR-144（增量索引路径、view 字段名、锁竞态、查询性能等）
- r8 (2026-06-06): AR-94~AR-123（零事务、Leiden directed、glob 匹配等）
- r7 (2026-06-02): AR-88~AR-93（@Auth 缺失、evictOverflow 等）
- deep-audit-2026-06-06: 21 维度全覆盖

**已修复确认（自 r9 以来）**：
- AR-124 (P0): 增量索引路径比较失效 → **已修复**。现在使用 `MappedPathResource` 包装路径
- AR-125 (P1): view.xml GraphQL selection 字段名不匹配 → **已修复**。现在使用 `staticFlag`/`abstractFlag`
- AR-126 (P1): indexFile 不更新统计 → **已修复**。现在调用 `updateIndexStats(indexId)`
- AR-128 (P1): triggerIncrementalIndex/indexFile 无锁保护 → **已修复**。三个索引方法均使用 `withIndexLock`
- AR-129 (P1): findReferencedBy 只取首条 → **已修复**。现在收集所有匹配的 symbolIds
- AR-130 (P2): getModuleDigest 全量加载符号 → **已修复**。现在使用 `FilterBeans.in("fileId", fileIds)` 过滤
- AR-131 (P2): 字典缺失 → **已修复**。`call_direction.dict.yaml` 和 `hierarchy_direction.dict.yaml` 已创建
- AR-133 (P2): persistSingleFileInSession 使用单文件符号表 → **已修复**。现在加载全局符号表
- AR-137 (P2): tarjanSCC 递归 StackOverflow → **已修复**。改为迭代实现
- AR-138 (P2): 搜索评分区分大小写 → **已修复**。现在使用 `toLowerCase()` 比较
- AR-139 (P2): NopCodeDependency 缺少唯一约束 → **已修复**。添加了 `uk_dependency_unique(indexId, dependencyKeyHash)`
- AR-140 (P2): TS getNodeText 重编码 → **已修复**。现在在 `analyze` 开始时一次性编码 `byte[]`
- AR-143 (P3): batchOutline N+1 → **已修复**。改为批量查询
- AR-144 (P3): statusMap 驱逐策略 → **已修复**。改用 access-order LinkedHashMap + removeEldestEntry

**仍存在的问题（未修复确认）**：
- AR-127 (P1): deleteIndex 在并发持锁时移除锁对象 → **仍存在**。`indexLocks.remove(indexId)` 仍在锁释放后执行
- AR-132 (P2): entityToInheritance ID/QN 混淆 → **部分修复**。CodeIndexService 已修复，但 CodeGraphService 仍有独立的 `entityToInheritance` 方法直接映射 `superTypeId` → `superTypeQualifiedName`
- AR-134 (P2): OrmFingerprintStore.saveFingerprints N+1 → **仍存在**
- AR-135 (P2): buildFilePathCache 加载 CLOB → **仍存在**
- AR-136 (P2): collectRelevantInheritances 截断 → **改善**（添加了 WARN 日志），但仍截断
- AR-141 (P2): TS 全限定名包含 src/ 前缀 → **仍存在**（设计限制）
- AR-142 (P3): usageCount 死字段 → **仍存在**
- AR-97~AR-123 中的多个问题仍未修复

本轮发现均为新视角切入的新问题。

---

### [AR-145] CallGraph.getAllNodeIds() 和 getForwardMap() 缺少同步——与 addEdge 的数据竞争

- **文件**: `nop-code/nop-code-core/.../graph/CallGraph.java:27-54`
- **证据片段**:
  ```java
  // addEdge — 有 synchronized
  public synchronized void addEdge(String caller, String callee) { ... }

  // getCallees — 有 synchronized
  public synchronized List<String> getCallees(String nodeId) { ... }

  // getAllNodeIds — 没有 synchronized！
  public Set<String> getAllNodeIds() {              // line 46
      Set<String> all = new HashSet<>(forwardEdges.keySet());  // 读非 volatile 的 HashMap
      all.addAll(reverseEdges.keySet());
      return all;
  }

  // getForwardMap — 没有 synchronized！
  public Map<String, List<String>> getForwardMap() {  // line 52
      return Collections.unmodifiableMap(forwardEdges);
  }
  ```
- **严重程度**: P0
- **现状**: `addEdge()`、`getCallees()`、`getCallers()` 均标记为 `synchronized`，但 `getAllNodeIds()` 和 `getForwardMap()` 未同步。当 `CodeCacheManager` 在一个线程重建 CallGraph 时（通过 `rebuildCallGraph` 中的 `edgeConsumer.accept(callGraph, entity)` 调用 `addEdge`），另一个线程可能同时调用 `getAllNodeIds()` 或 `getForwardMap()` 读取 `forwardEdges`/`reverseEdges`。Java HashMap 在并发读写时可导致无限循环、数据丢失或 `ConcurrentModificationException`。
- **风险**: 在重建缓存期间，并发的图查询请求可导致 JVM 挂起（HashMap 无限循环）或返回不完整数据。
- **建议**: 将 `getAllNodeIds()` 和 `getForwardMap()` 添加 `synchronized` 关键字。
- **信心水平**: 确定
- **发现来源视角**: 并发安全侦探

---

### [AR-146] 8 个空 BizModel 暴露完整 CRUD 接口且无 @Auth——任意数据篡改风险

- **文件**: `nop-code/nop-code-service/.../entity/` 下的 8 个 BizModel
- **证据片段**:
  ```java
  // NopCodeCallBizModel.java:7-12 — 所有 8 个 BizModel 结构相同
  @BizModel("NopCodeCall")
  public class NopCodeCallBizModel extends CrudBizModel<NopCodeCall> implements INopCodeCallBiz {
      public NopCodeCallBizModel() {
          setEntityName(NopCodeCall.class.getName());
      }
  }
  ```
  受影响的 BizModel：
  - `NopCodeUsageBizModel` → `NopCodeUsage`
  - `NopCodeInheritanceBizModel` → `NopCodeInheritance`
  - `NopCodeCallBizModel` → `NopCodeCall`
  - `NopCodeAnnotationUsageBizModel` → `NopCodeAnnotationUsage`
  - `NopCodeFlowBizModel` → `NopCodeFlow`
  - `NopCodeFlowMembershipBizModel` → `NopCodeFlowMembership`
  - `NopCodeSemanticEdgeBizModel` → `NopCodeSemanticEdge`
  - `NopCodeDependencyBizModel` → `NopCodeDependency`
- **严重程度**: P1
- **现状**: 这些 BizModel 继承了 `CrudBizModel` 的全部 CRUD 方法（`findPage`、`get`、`save`、`update`、`delete`、`saveOrUpdate`、`deleteByQuery`、`updateByQuery`），但没有添加任何 `@Auth` 注解。这些实体正常情况下只由内部索引服务写入，但通过 GraphQL/REST 端点完全对外暴露。任何已认证用户可以调用 `NopCodeCall__delete`、`NopCodeDependency__save` 等操作直接修改索引数据。
- **风险**: 恶意用户可以删除调用图边、篡改依赖关系、修改继承关系，导致代码分析结果完全不可信。
- **建议**: 在每个 BizModel 的 xmeta 中限制写操作的权限（或覆盖 `save`、`update`、`delete` 方法添加 `@Auth(roles = "admin")`），或至少在 xmeta 中设置 `published="false"` 隐藏不需要的 mutation 端点。
- **信心水平**: 确定
- **发现来源视角**: 安全审计者

---

### [AR-147] FlowDetector.listFlows/getAffectedFlows 返回可变缓存引用——数据完整性风险

- **文件**: `nop-code/nop-code-flow/.../FlowDetector.java:163-169`
- **证据片段**:
  ```java
  // listFlows — 直接返回缓存中的 ArrayList 引用
  public List<ExecutionFlow> listFlows(String indexId) {
      return flowCache.getOrDefault(indexId, Collections.emptyList());  // line 164
  }

  // getAffectedFlows — 同样直接引用
  public List<ExecutionFlow> getAffectedFlows(String indexId, List<String> changedFilePaths) {
      List<ExecutionFlow> allFlows = flowCache.getOrDefault(indexId, Collections.emptyList());  // line 169
      ...
      for (ExecutionFlow flow : allFlows) {  // line 177 — 遍历可变列表
  ```
- **严重程度**: P1
- **现状**: `flowCache` 是 `ConcurrentHashMap<String, List<ExecutionFlow>>`。`listFlows()` 返回的列表是 `detectFlows()` 中创建的 `new ArrayList<>(...)` 的直接引用。调用方可以 `listFlows().add(...)` 或 `listFlows().clear()` 修改缓存内容。同时，`getAffectedFlows` 在遍历 `allFlows` 时如果另一个线程调用 `detectFlows` 替换了缓存中的列表（`flowCache.put(indexId, new ArrayList<>(...))`），当前遍历的列表虽然不会被修改（因为 ArrayList 引用不变），但返回的结果可能与缓存最新状态不一致。
- **风险**: 调用方意外修改缓存列表导致后续查询返回错误数据。BizModel 层将此列表序列化为 GraphQL 响应时，如果序列化过程中列表被修改，可能抛出 `ConcurrentModificationException`。
- **建议**: 返回 `Collections.unmodifiableList(new ArrayList<>(flowCache.getOrDefault(indexId, Collections.emptyList())))`，或使用防御性拷贝。
- **信心水平**: 很可能
- **发现来源视角**: 并发安全侦探

---

### [AR-148] CallGraph.getForwardMap() 通过不可变 Map 包装暴露可变内部列表

- **文件**: `nop-code/nop-code-core/.../graph/CallGraph.java:52-54`
- **证据片段**:
  ```java
  public Map<String, List<String>> getForwardMap() {
      return Collections.unmodifiableMap(forwardEdges);  // Map 不可变，但值是可变 ArrayList
  }
  ```
- **严重程度**: P1
- **现状**: `Collections.unmodifiableMap(forwardEdges)` 只阻止对 Map 结构的修改（put/remove），但每个 value 是可变的 `ArrayList<String>`。调用方可以 `getForwardMap().get("symbolId").add("injected")` 直接修改图的内部数据。虽然 `addEdge()` 创建了新 `ArrayList`，但这些列表的引用被直接存储在 `forwardEdges` 中。
- **风险**: 图分析算法（社区检测、关键节点分析、影响分析）的结果可能被意外或恶意修改，导致后续分析使用被污染的图数据。
- **建议**: 返回深拷贝或将每个 value 包装为 `Collections.unmodifiableList(...)`。
- **信心水平**: 确定
- **发现来源视角**: 数据完整性审计者

---

### [AR-149] NopCodeFile ORM 缺少 cascadeDelete 到子实体——单文件删除导致孤儿记录

- **文件**: `nop-code/model/nop-code.orm.xml:247-264`
- **证据片段**:
  ```xml
  <!-- NopCodeFile — symbols 关系缺少 cascadeDelete -->
  <to-many displayName="符号" name="symbols" refEntityName="io.nop.code.dao.entity.NopCodeSymbol"
           refPropName="file">
      <join><on leftProp="id" rightProp="fileId"/></join>
      <!-- 无 cascadeDelete="true" -->
  </to-many>
  <to-many displayName="引用" name="usages" refEntityName="io.nop.code.dao.entity.NopCodeUsage"
           refPropName="file">
      <join><on leftProp="id" rightProp="fileId"/></join>
      <!-- 无 cascadeDelete="true" -->
  </to-many>
  <to-many displayName="调用" name="calls" refEntityName="io.nop.code.dao.entity.NopCodeCall"
           refPropName="file">
      <join><on leftProp="id" rightProp="fileId"/></join>
      <!-- 无 cascadeDelete="true" -->
  </to-many>
  ```
  对比 `NopCodeSymbol`（line 395+）的所有子关系都有 `cascadeDelete="true"`。
- **严重程度**: P1
- **现状**: `deleteFileRecords` 方法在增量索引期间手动删除文件的子实体（调用 `deleteEntitiesByFilter`），但 ORM 层的 `cascadeDelete` 缺失意味着：(1) 任何通过 ORM 直接删除 `NopCodeFile` 的操作（例如通过 `NopCodeFileBizModel` 的 CRUD delete）不会级联删除子实体；(2) `deleteEntitiesByFilter` 只处理了 `NopCodeCall`、`NopCodeSymbol`、`NopCodeUsage`、`NopCodeDependency`，但未处理 `NopCodeAnnotationUsage`（该实体通过 `annotatedSymbolId` 关联到符号而非直接关联文件）。
- **风险**: 通过 BizModel CRUD 端点删除文件（见 AR-146）或未来任何代码直接删除 NopCodeFile 实体时，其关联的符号、调用、用法记录成为孤儿。`NopCodeAnnotationUsage` 在 `deleteFileRecords` 中通过 `deleteRelationalBySymbolIds` 处理，但如果 `findSymbolIdsByFileId` 返回空（文件符号已通过其他方式删除），注解用法也会残留。
- **建议**: 在 ORM 模型中为 `NopCodeFile.symbols`、`NopCodeFile.usages`、`NopCodeFile.calls` 添加 `cascadeDelete="true"`。
- **信心水平**: 确定
- **发现来源视角**: ORM 契约考古学家

---

### [AR-150] NopCodeSymbol.usages 关系缺少 cascadeDelete——符号删除后 NopCodeUsage 孤儿

- **文件**: `nop-code/model/nop-code.orm.xml:388-393`
- **证据片段**:
  ```xml
  <!-- NopCodeSymbol — usages 关系无 cascadeDelete -->
  <to-many displayName="引用" name="usages" refEntityName="io.nop.code.dao.entity.NopCodeUsage"
           refPropName="symbol">
      <join><on leftProp="id" rightProp="symbolId"/></join>
      <!-- 无 cascadeDelete="true" -->
  </to-many>

  <!-- 对比: annotations 关系有 cascadeDelete -->
  <to-many displayName="注解" name="annotations" ... cascadeDelete="true">
  <to-many displayName="调用者" name="callees" ... cascadeDelete="true">
  <to-many displayName="被调用" name="callers" ... cascadeDelete="true">
  ```
- **严重程度**: P1
- **现状**: `NopCodeSymbol` 有 7 个 `to-many` 子关系，其中 6 个（`annotations`、`flowMemberships`、`callees`、`callers`、`superTypes`、`subTypes`）设置了 `cascadeDelete="true"`，但 `usages` 关系遗漏了。当符号被删除时（例如增量索引中的 `deleteEntitiesByFilter(NopCodeSymbol.class, "fileId", fileId)`），其关联的 `NopCodeUsage` 记录不会被级联删除。
- **风险**: 增量索引删除文件符号后，`findReferencedBy` 查询返回的引用指向不存在的 `symbolId`，`getSymbolUsages` 返回孤儿用法记录。
- **建议**: 添加 `cascadeDelete="true"` 到 `NopCodeSymbol.usages` 关系。
- **信心水平**: 确定
- **发现来源视角**: ORM 契约考古学家

---

### [AR-151] CodeGraphService.entityToInheritance 仍将 superTypeId 直接映射为 superTypeQualifiedName

- **文件**: `nop-code/nop-code-service/.../impl/CodeGraphService.java:371-379`
- **证据片段**:
  ```java
  // CodeGraphService 中独立的 entityToInheritance
  private CodeInheritance entityToInheritance(NopCodeInheritance entity) {
      CodeInheritance inh = new CodeInheritance();
      inh.setId(entity.getId());
      inh.setSubTypeId(entity.getSubTypeId());
      inh.setSuperTypeQualifiedName(entity.getSuperTypeId());  // ← 直接映射，可能是 ID 而非 QN
      inh.setRelationType(entity.getRelationType() != null
              ? CodeRelationType.valueOf(entity.getRelationType()) : null);
      return inh;
  }
  ```
  对比 CodeIndexService 中已修复的版本（line 259-271）：
  ```java
  // CodeIndexService — 已修复：通过 entity.getSuperType() 反查 QN
  String superTypeQN = null;
  if (entity.getSuperTypeId() != null && entity.getSuperType() != null) {
      superTypeQN = entity.getSuperType().getQualifiedName();
  }
  inh.setSuperTypeQualifiedName(superTypeQN != null ? superTypeQN : entity.getSuperTypeId());
  ```
- **严重程度**: P1
- **现状**: AR-132 在 r9 中报告了 ID/QN 混淆问题。CodeIndexService 中的 `entityToInheritance` 已修复（通过 `entity.getSuperType().getQualifiedName()` 反查），但 CodeGraphService 有自己独立的 `entityToInheritance` 方法，仍然直接将 `superTypeId`（在 `resolveQualifiedNamesToIds` 执行后可能是符号 ID）映射为 `superTypeQualifiedName`。
- **风险**: `getTypeHierarchy` 使用 `buildTypeHierarchy` 中的 `allInheritances` 列表来查找父类。如果 `superTypeQualifiedName` 存的是 UUID 而非全限定名，`table.getByQualifiedName(uuid)` 返回 null，导致类型层级树中的父类节点无法展开。
- **建议**: 统一 CodeGraphService 中的 `entityToInheritance` 与 CodeIndexService 的修复一致，通过 `entity.getSuperType()` 反查全限定名。
- **信心水平**: 确定
- **发现来源视角**: ORM 契约考古学家

---

### [AR-152] CommunityDetector.runWithTimeout 在超时后不等待线程终止——潜在线程泄漏

- **文件**: `nop-code/nop-code-graph/.../community/CommunityDetector.java:787-795`
- **证据片段**:
  ```java
  private static <T> T runWithTimeout(Callable<T> task, long timeoutMs) throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
          Future<T> future = executor.submit(task);
          return future.get(timeoutMs, TimeUnit.MILLISECONDS);
      } finally {
          executor.shutdownNow();
          // 无 awaitTermination — 线程可能继续运行
      }
  }
  ```
- **严重程度**: P2
- **现状**: `shutdownNow()` 中断线程但不等待其终止。如果社区检测算法在超时时处于密集计算中，线程可能不响应中断（例如在 `calculateCohesion` 的循环中），继续占用 CPU 和内存。每次超时调用都会创建一个新线程，多次超时后可能积累多个僵尸线程。
- **风险**: 在大型代码库上反复触发社区检测超时，可能积累多个未终止的计算线程，消耗 CPU 和内存。
- **建议**: 在 `shutdownNow()` 后添加 `executor.awaitTermination(5, TimeUnit.SECONDS)`，或使用共享线程池。
- **信心水平**: 很可能
- **发现来源视角**: 资源泄漏侦探

---

### [AR-153] CommunityDetector.recursiveSplit 只考虑出边——未聚类节点分配不完整

- **文件**: `nop-code/nop-code-graph/.../community/CommunityDetector.java:553-558`
- **证据片段**:
  ```java
  List<String> callees = callGraph.getCallees(node);   // 只取出边
  if (callees != null) {
      for (String callee : callees) {
          if (memberId.equals(callee)) connections++;
      }
  }
  // 无 callGraph.getCallers(node) — 入边被忽略
  ```
- **严重程度**: P2
- **现状**: 当将未聚类节点分配到最佳匹配子社区时，只计算该节点到子社区成员的出边（`getCallees`）。一个被大量调用但不主动调用其他节点的工具方法（高入边、零出边）会被错误地分配到无关社区或保持未聚类状态。
- **风险**: 社区检测质量下降——工具类和基础设施代码（被广泛引用但不主动调用其他代码）可能无法正确归入任何社区。
- **建议**: 同时考虑 `callGraph.getCallers(node)` 的入边连接数，取两个方向的加权平均。
- **信心水平**: 很可能
- **发现来源视角**: 数据完整性审计者

---

### [AR-154] buildFilePathCache 仍加载含 sourceCode CLOB 的完整文件实体

- **文件**: `nop-code/nop-code-service/.../impl/CodeSearchService.java:195-206`
- **证据片段**:
  ```java
  private Map<String, String> buildFilePathCache(String indexId) {
      IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
      QueryBean fq = new QueryBean();
      fq.addFilter(FilterBeans.eq("indexId", indexId));
      fq.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
      List<NopCodeFile> files = fileDao.findAllByQuery(fq);  // 加载完整实体含 CLOB
      Map<String, String> cache = new HashMap<>();
      for (NopCodeFile f : files) {
          cache.put(f.getId(), f.getFilePath());  // 只用了 id 和 filePath
      }
      return cache;
  }
  ```
- **严重程度**: P2
- **现状**: AR-135 在 r9 中报告了此问题，仍未修复。`buildFilePathCache` 在 4 个搜索路径中每次调用都全量加载 `NopCodeFile` 实体（含 `sourceCode` CLOB 字段），但只使用 `id` 和 `filePath`。每次搜索请求的内存开销与索引中所有文件的源代码总大小成正比。
- **风险**: 高频搜索场景下产生严重内存压力。含 1000 个文件、平均 50KB 源代码的索引，每次搜索加载约 50MB 数据后立即丢弃。
- **建议**: 使用投影查询只查 `id` 和 `filePath` 列，或在 `CodeCacheManager` 中缓存此映射。
- **信心水平**: 确定
- **发现来源视角**: 性能模式考古学家

---

### [AR-155] SymbolTable 非线程安全但在 persistSingleFileInSession 中被并发修改

- **文件**: `nop-code/nop-code-core/.../graph/SymbolTable.java:11-47` + `nop-code/nop-code-service/.../impl/CodeIndexService.java:1025-1036`
- **证据片段**:
  ```java
  // SymbolTable — 使用 plain HashMap，无同步
  private final Map<String, CodeSymbol> byQualifiedName = new HashMap<>();
  private final Map<String, CodeSymbol> byId = new HashMap<>();
  public void add(CodeSymbol symbol) {  // 无 synchronized
      if (symbol.getQualifiedName() != null) {
          byQualifiedName.put(symbol.getQualifiedName(), symbol);
      }
  }

  // CodeIndexService.persistSingleFileInSession — 修改缓存中的全局 SymbolTable
  SymbolTable globalTable = getOrRebuildSymbolTable(indexId);  // 从缓存获取
  if (globalTable != null) {
      for (CodeSymbol sym : fileSymbolTable.getAll()) {
          globalTable.add(sym);  // 修改缓存中的 HashMap
      }
  }
  ```
- **严重程度**: P2
- **现状**: `persistSingleFileInSession` 从 `CodeCacheManager` 获取全局 `SymbolTable` 后，调用 `globalTable.add(sym)` 向其中的 `HashMap` 插入新符号。虽然 `CodeCacheManager` 的 `getOrRebuildSymbolTable` 使用 `ReentrantLock` 保护缓存访问，但返回的 `SymbolTable` 对象本身无锁保护。如果两个并发的 `indexFile` 调用（同一个 indexId）获取了同一个 `globalTable` 引用并同时调用 `add`，会导致 `HashMap` 的并发修改问题。
- **风险**: 当前被 `withIndexLock` 限制了同一 indexId 的并发写入，所以实际并发修改风险较低。但如果锁机制被绕过（例如 Bug），或未来引入异步索引，则会出现 `HashMap` 并发修改异常或数据丢失。
- **建议**: 将 `SymbolTable.add()` 添加 `synchronized`，或将 `byQualifiedName` 和 `byId` 改为 `ConcurrentHashMap`。
- **信心水平**: 很可能
- **发现来源视角**: 并发安全侦探

---

### [AR-156] ImpactAnalyzer.evaluateRisk 使用默认配置下不可达的深度阈值

- **文件**: `nop-code/nop-code-graph/.../impact/ImpactAnalyzer.java:293-301`
- **证据片段**:
  ```java
  // evaluateRisk 使用硬编码深度阈值
  if (totalImpacted > 50 || maxDepth > 5) {        // line 293 — maxDepth 默认 3，永不触发
      level = RiskLevel.CRITICAL;
  } else if (totalImpacted > 20 || maxDepth > 3) { // line 295 — maxDepth=3 时 3>3 为 false
      level = RiskLevel.HIGH;
  }
  ```
  而默认 `ImpactConfig.maxDepth = 3`，BFS 遍历在 `depth >= maxDepth` 时停止（line 256）。
- **严重程度**: P2
- **现状**: `evaluateRisk` 将 `maxDepth > 5` 和 `maxDepth > 3` 作为风险升级条件。但 BFS 遍历严格限制深度不超过 `maxDepth`，因此当使用默认配置（`maxDepth=3`）时，`maxDepth > 3` 为 false，`maxDepth > 5` 更不可能。风险评级只取决于 `totalImpacted` 数量，深度条件形同虚设。只有当用户手动设置 `maxDepth=10` 时深度条件才有意义，但此时 `maxDepth > 5` 恒为 true（10 > 5），直接跳到 CRITICAL，跳过了 MEDIUM/HIGH 的区分。
- **风险**: 影响分析的风险评级不准确——深层影响（多跳但每跳只影响 1-2 个符号）与浅层广泛影响（少量跳但每跳影响很多符号）无法区分。
- **建议**: 使用 `maxDepth` 的相对阈值（如 `actualMaxDepthReached > maxDepth * 0.8`），或将深度阈值参数化为配置项。
- **信心水平**: 确定
- **发现来源视角**: 数据完整性审计者

---

### [AR-157] FlowDetector.evictOverflow 仍使用无序驱逐——仍存在（AR-91 残留）

- **文件**: `nop-code/nop-code-flow/.../FlowDetector.java:570-576`
- **证据片段**:
  ```java
  private void evictOverflow(Map<String, ?> cache) {
      while (cache.size() > MAX_CACHE_ENTRIES) {
          String key = cache.keySet().stream().findFirst().orElse(null);
          if (key == null) break;
          cache.remove(key);
      }
  }
  ```
- **严重程度**: P3
- **现状**: AR-91 在 r7 中报告了 `evictOverflow` 可能无限循环的问题。无限循环已修复（使用 `stream().findFirst()` + 显式 `cache.remove(key)` 替代了 `Iterator.remove()`），但驱逐策略仍为无序。`ConcurrentHashMap.keySet().stream().findFirst()` 返回任意条目——可能刚缓存的活跃索引状态被驱逐，而很久没用的冷条目保留。
- **风险**: 最近触发的索引操作的状态可能被立即驱逐，导致 `getAffectedFlows` 回退到慢速路径或返回空结果。
- **建议**: 使用与 `NopCodeIndexBizModel.incrementalStatusMap` 相同的 access-order LinkedHashMap + removeEldestEntry 模式。
- **信心水平**: 确定
- **发现来源视角**: 并发安全侦探

---

## 总评

本轮审查从并发安全、BizModel 安全、ORM 级联配置三个全新视角切入，发现了 nop-code 模块的核心数据结构线程安全缺陷和一系列安全/数据完整性问题。

**最严重的发现是 AR-145（CallGraph 线程安全不一致）**——`addEdge()`、`getCallees()`、`getCallers()` 都有 `synchronized` 保护，但 `getAllNodeIds()` 和 `getForwardMap()` 没有同步。当 `CodeCacheManager` 在后台重建缓存时，并发的图查询请求会触发 HashMap 的数据竞争。这是一个 API 设计不一致的问题——类的一半方法是线程安全的，另一半不是，调用者很容易误认为整个类是线程安全的。

安全层面发现了 8 个空 BizModel（AR-146）暴露了完整的 CRUD mutation 端点（save、update、delete、deleteByQuery），没有任何 `@Auth` 保护。虽然这些实体正常由内部服务写入，但 GraphQL/REST 端点对外暴露后，任何已认证用户都可以直接修改索引数据。

ORM 层面发现了两个关键的 `cascadeDelete` 缺失：`NopCodeFile` 到其子实体（AR-149）和 `NopCodeSymbol.usages`（AR-150）。当通过非 `deleteFileRecords` 路径删除文件或符号时（例如 BizModel CRUD 端点），子实体成为孤儿。

AR-151 发现 AR-132 的修复不完整——CodeIndexService 已修复但 CodeGraphService 的独立 `entityToInheritance` 方法仍然直接映射 `superTypeId` 为 `superTypeQualifiedName`，导致类型层级查询在 `resolveQualifiedNamesToIds` 执行后无法正确展开父类。

**本次审查最值得关注的 3 个方向**：

1. **核心数据结构线程安全不一致**（AR-145 + AR-155）——CallGraph 和 SymbolTable 的并发模型不完整，在缓存重建期间可能导致 JVM 级别的故障
2. **BizModel CRUD 端点安全缺口**（AR-146）——8 个实体的 mutation 端点完全无保护
3. **ORM 级联删除缺失**（AR-149 + AR-150）——单文件/符号删除路径导致孤儿记录

**已修复确认**：自 r9 以来，14 个问题已修复，显示团队在积极修复审计发现。增量索引路径比较（AR-124 P0）已通过 `MappedPathResource` 方案修复，事务边界、锁机制、查询性能等关键问题均已解决。

## 本次审查的盲区自评

1. **CodeSearchService 的搜索引擎集成路径**：未深入验证 `ISearchEngine` 的实现是否正确处理了 topic 同步和错误恢复。
2. **nop-code-codegen 模板**：未审查代码生成模板的正确性。
3. **端到端运行验证**：所有发现基于静态代码分析，未实际运行测试验证并发问题。
4. **CodeCacheManager.lock 的性能影响**：所有缓存操作使用单一 `ReentrantLock`，在高并发场景下可能成为瓶颈，但未量化评估。
5. **TypeScript/Python 语言适配器的细节**：只审查了 TS 的 `getNodeText` 性能问题，未深入检查 Python 适配器的 AST 解析正确性。

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | CallGraph 线程安全不一致（AR-145） |
| P1      | 6    | BizModel 安全缺口（AR-146）、FlowDetector 可变引用（AR-147）、CallGraph 可变内部（AR-148）、ORM 级联缺失（AR-149/150）、ID/QN 混淆残留（AR-151） |
| P2      | 4    | 线程泄漏（AR-152）、算法缺陷（AR-153）、CLOB 加载残留（AR-154）、SymbolTable 线程安全（AR-155）、风险评级失效（AR-156） |
| P3      | 1    | evictOverflow 无序驱逐残留（AR-157） |
