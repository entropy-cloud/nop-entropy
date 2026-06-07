# Adversarial Review: nop-code — Open Findings

> **日期**: 2026-06-06（第 13 轮对抗性审查）
> **模块**: nop-code（13 个子模块）
> **审查类型**: 开放式对抗性审查
> **发现来源视角**: ORM 模型 + 实体关系审计 + CodeIndexService 全量审计 + BizModel 事务边界追踪
> **审查范围**: ORM 模型实体关系一致性、CodeIndexService 全量逻辑审计（deleteIndex/saveFileResultInSession/resolveQualifiedNamesToIds）、BizModel 事务边界与缓存失效时序

## 去重确认

已审阅以下历史报告：
- r12 (2026-06-06e): AR-170~AR-175（BizModel 授权配置不一致、triggerFullIndex 硬编码 Java、diffGraph 双 Leiden、BetweennessCentrality 无保护、KnowledgeGap 出边遗漏、DeadCode contains 误匹配）
- r11 (2026-06-06d): AR-158~AR-169（缓存引用泄漏、WEIGHT_EXTERNAL 死权重、guessExtension 误匹配、增量索引搜索引擎不同步等）
- r10 (2026-06-06c): AR-145~AR-157（SpringEvent listener 覆盖、Java Record 未入 symbolMap、Python `__init__.py` 等）
- r9 (2026-06-06b): AR-124~AR-144
- r8 (2026-06-06): AR-94~AR-123
- deep-audit-2026-06-06: 21 维度全覆盖

**仍存在的问题（未修复确认）**：
- AR-98~AR-123（单例节点丢弃、startsWith 错配、cascadeDelete 缺失、persistFlows OOME 等）
- AR-124~AR-169（增量索引路径失效、并发锁、搜索引擎不同步、缓存引用泄漏等）
- AR-170~AR-175（授权配置漂移、triggerFullIndex 仅 Java、图算法无保护等）

本轮从两个此前审查从未覆盖的全新视角切入：**ORM 模型实体关系与业务逻辑一致性** 和 **CodeIndexService 内部持久化/事务/缓存的完整调用链**。所有发现均为新问题。

---

### [AR-176] NopCodeSemanticEdge 使用 `useLogicalDelete="true"` 但 `deleteIndex`/`deleteFileRecords` 期望物理删除——重建索引后幽灵边持续存在

- **文件**: 
  - `nop-code/model/nop-code.orm.xml:925`
  - `nop-code/nop-code-service/.../impl/CodeIndexService.java:556,1434-1435`
- **证据片段**:
  ```xml
  <!-- nop-code.orm.xml:925 -->
  <entity ... tableName="nop_code_semantic_edge" 
           useLogicalDelete="true" deleteFlagProp="delFlag">
  ```
  ```java
  // CodeIndexService.java:556 — deleteIndex assumes physical delete
  deleteEntitiesPaged(session, NopCodeSemanticEdge.class, "indexId", indexId);
  
  // CodeIndexService.java:1434-1435 — incremental deleteFileRecords also
  deleteRelationalBySymbolIds(NopCodeSemanticEdge.class, "sourceSymbolId", symbolIds);
  deleteRelationalBySymbolIds(NopCodeSemanticEdge.class, "targetSymbolId", symbolIds);
  ```
  `deleteEntitiesPaged` 内部调用 `dao.batchDeleteEntities(batch)` (line 577)。对于 `useLogicalDelete="true"` 的实体，Nop ORM 框架会将 `batchDeleteEntities` 转换为软删除（设置 `delFlag=true`），而非物理删除行。
- **严重程度**: P1
- **现状**: `NopCodeSemanticEdge` 是整个 ORM 模型中**唯一**使用 `useLogicalDelete` 的实体（其他 10 个实体都不使用）。但 `deleteIndex` 和 `deleteFileRecords` 使用与所有其他实体相同的删除模式（`deleteEntitiesPaged`/`deleteRelationalBySymbolIds`）。当删除索引后重建，或增量更新文件时，旧的语义边只是被标记为 `delFlag=true`，仍然占据 DB 空间。

  更严重的是，`deleteFileRecords`（增量索引路径）按 `sourceSymbolId` 和 `targetSymbolId` 查找要删除的边。但 `findAllByQuery` 对逻辑删除的实体默认会过滤掉 `delFlag=true` 的行。这意味着如果一个文件被多次增量更新，每次只删除最新一轮的边，之前的"已软删除"边仍然存在于 DB 中（只是被查询过滤掉）。但 `uk_semantic_edge_unique` 唯一约束（`indexId,sourceSymbolId,targetSymbolId,relationType`）**不会**被软删除的行违反（因为物理行仍然存在），导致重建索引时无法插入同样的边——唯一约束冲突。
- **风险**: (a) 重建索引时唯一约束冲突导致索引失败；(b) DB 中累积大量软删除的幽灵边，随增量轮次增长；(c) `deleteIndex` 不真正清除数据，只是软删除。
- **建议**: (a) 移除 `NopCodeSemanticEdge` 上的 `useLogicalDelete="true"` 和 `delFlag` 列，使其与其他所有实体一致；(b) 或在 `deleteIndex` 和 `deleteFileRecords` 中对 `NopCodeSemanticEdge` 使用物理删除（直接 SQL 或绕过逻辑删除机制）。
- **信心水平**: 很可能（取决于 Nop ORM 对逻辑删除实体的 `batchDeleteEntities` 和唯一约束处理方式）
- **发现来源视角**: ORM 模型 + 实体关系审计

---

### [AR-177] getProjectFilePaths 加载全部 NopCodeFile 实体（含 CLOB sourceCode）仅为提取路径——10 万文件索引时 OOM

- **文件**: `nop-code/nop-code-service/.../impl/CodeIndexService.java:1371-1384`
- **证据片段**:
  ```java
  private Set<String> getProjectFilePaths(String indexId) {
      IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
      QueryBean q = new QueryBean();
      q.addFilter(FilterBeans.eq("indexId", indexId));
      List<NopCodeFile> files = fileDao.findAllByQuery(q);  // 加载全部实体，含 CLOB
      Set<String> paths = new HashSet<>();
      for (NopCodeFile f : files) {
          if (f.getFilePath() != null) {
              paths.add(f.getFilePath());
          }
      }
      return paths;
  }
  ```
  `NopCodeFile` 实体包含 `sourceCode`（CLOB）和 `imports`（8KB JSON）。此方法仅需要 `filePath`，但加载了全部实体字段。对于 10 万文件的平均 5KB 源代码，总内存开销 ≈ 550MB。
  
  此方法在 `saveFileResultInSession` 中被每个文件的依赖解析调用（line 1333），在 `indexDirectory` 的全量索引路径中第一个文件持久化就触发全量加载。
- **严重程度**: P1
- **现状**: `getProjectFilePaths` 对 `NopCodeFile` 执行无分页、无字段选择的 `findAllByQuery`。ORM 层加载所有列，包括 `sourceCode` CLOB。在 `saveFileResultInSession` 中，`cachedProjectFilePaths` 变量初始为 null（line 1060），第一次调用时触发全量加载，后续同一文件的调用使用缓存。但全量索引时，第一个文件的 `saveFileResultInSession` 就触发全量加载。
  
  对比 `CodeSearchService.buildFilePathCache`（AR-168），有同样的问题但有 `MAX_QUERY_RESULTS=10000` 限制。此处无任何限制。
- **风险**: 10 万文件索引的全量索引路径在第一个文件持久化时就 OOM。即使不 OOM，大量内存占用导致 GC 压力极大。
- **建议**: (a) 使用投影查询仅查询 `filePath` 字段；(b) 或将路径集合缓存到 `CodeCacheManager` 中；(c) 至少添加分页和限制。
- **信心水平**: 确定
- **发现来源视角**: CodeIndexService 全量审计

---

### [AR-178] resolveQualifiedNamesToIds 对已解析的记录无幂等保护——增量索引重复处理全部继承/注解记录

- **文件**: `nop-code/nop-code-service/.../impl/CodeIndexService.java:908-956`
- **证据片段**:
  ```java
  private void resolveQualifiedNamesToIds(String indexId, SymbolTable symbolTable, IOrmSession session) {
      // ... 遍历全部 NopCodeInheritance 记录
      for (NopCodeInheritance inh : inhBatch) {
          String superTypeId = inh.getSuperTypeId();  // 可能是 QN 或已解析的 ID
          if (superTypeId != null) {
              CodeSymbol resolved = symbolTable.getByQualifiedName(superTypeId);
              if (resolved != null) {
                  inh.setSuperTypeId(resolved.getId());  // 覆盖写入
              }
          }
      }
  ```
- **严重程度**: P2
- **现状**: `saveFileResultInSession`（line 1190）将 `inheritance.setSuperTypeId(inh.getSuperTypeQualifiedName())` 写入——使用 QN。`resolveQualifiedNamesToIds` 随后将 QN 替换为 ID。但在增量索引时：
  - `saveReplacingExisting` 在实体已存在时用新实体的所有属性覆盖现有实体
  - 新继承实体的 `superTypeId` 是 QN，覆盖了已解析的 ID
  - `resolveQualifiedNamesToIds` 重新处理**全部**继承记录
  
  对于 10 万符号的索引，假设有 5 万继承关系，每个变更文件触发一次全量扫描。批量变更 100 个文件时，总继承记录处理量 = 100 × 50,000 = 5,000,000 次 `getByQualifiedName` 调用。
- **风险**: 大型索引的增量索引性能严重退化。每个变更文件触发 O(total_inheritances + total_annotations) 的处理量。
- **建议**: (a) 在 `saveReplacingExisting` 中保留已解析的 ID（如果旧值的格式是 ID 而非 QN，不覆盖）；(b) 或在增量路径中只对新记录执行 `resolveQualifiedNamesToIds`；(c) 将继承记录的解析状态持久化。
- **信心水平**: 很可能
- **发现来源视角**: CodeIndexService 全量审计

---

### [AR-179] NopCodeFlowMembership 缺少 indexId 列——deleteIndex 必须逐个 Flow 删除 Membership，大型索引删除慢

- **文件**: 
  - `nop-code/model/nop-code.orm.xml:871-920`
  - `nop-code/nop-code-service/.../impl/CodeIndexService.java:543-548`
- **证据片段**:
  ```xml
  <!-- nop-code.orm.xml — NopCodeFlowMembership 无 indexId 列 -->
  <entity ... tableName="nop_code_flow_membership">
      <columns>
          <column name="id" .../>
          <column name="flowId" .../>    <!-- 只有 flowId，没有 indexId -->
          <column name="symbolId" .../>
      </columns>
  ```
  ```java
  // CodeIndexService.java:543-548 — deleteIndex 逐个 Flow 删除 Membership
  IEntityDao<NopCodeFlow> flowDao = daoProvider.daoFor(NopCodeFlow.class);
  QueryBean flowQuery = new QueryBean();
  flowQuery.addFilter(FilterBeans.eq("indexId", indexId));
  for (NopCodeFlow flow : flowDao.findAllByQuery(flowQuery)) {  // 逐个 Flow
      deleteEntitiesPaged(session, NopCodeFlowMembership.class, "flowId", flow.getId());
  }
  ```
- **严重程度**: P2
- **现状**: `NopCodeFlowMembership` 是 11 个实体中唯一缺少 `indexId` 的关联实体。`deleteIndex` 无法直接按 `indexId` 删除 membership，必须先查询所有 `NopCodeFlow`（有 `indexId`），然后逐个 `flowId` 执行 `deleteEntitiesPaged`。对于 5000 个 Flow（`MAX_FLOWS_PER_INDEX`），需要 5000 次 `deleteEntitiesPaged` 调用。每次调用执行一次 `findAllByQuery` + `batchDeleteEntities` + `flush` + `evictAll`。

  同时，`NopCodeFlow` 的 `memberships` 关系定义了 `cascadeDelete="true"`（line 852），但 `deleteIndex` 不使用 ORM 的级联删除，而是手动逐表删除。如果 ORM 的 `cascadeDelete` 在 `batchDeleteEntities` 中被触发，则删除 Flow 时可能已经删除了 membership，代码会做重复工作或产生冲突。

  此外，`findAllByQuery(flowQuery)` 对 `NopCodeFlow` 无分页限制，大型索引可能加载所有 Flow 到内存。
- **风险**: 删除大型索引耗时极长（5000+ 次 DB 往返），且可能在 `cascadeDelete` 和手动删除之间产生冲突。
- **建议**: (a) 为 `NopCodeFlowMembership` 添加 `indexId` 列（反规范化），与所有其他关联实体一致；(b) 或在 `deleteIndex` 中利用 `cascadeDelete` 关系，删除 Flow 时自动删除 Membership。
- **信心水平**: 确定
- **发现来源视角**: ORM 模型 + 实体关系审计

---

### [AR-180] buildInheritanceIndex 和 loadExistingEdgeKeys 查询限制 MAX_QUERY_RESULTS(10000)——大型索引启发式边去重失效

- **文件**: `nop-code/nop-code-service/.../impl/CodeIndexService.java:865-896`
- **证据片段**:
  ```java
  // buildInheritanceIndex (line 872):
  query.setLimit(MAX_QUERY_RESULTS);  // 10,000
  List<NopCodeInheritance> inheritances = inhDao.findAllByQuery(query);
  
  // loadExistingEdgeKeys (line 891):
  query.setLimit(MAX_QUERY_RESULTS);  // 10,000
  for (NopCodeCall call : callDao.findAllByQuery(query)) {
      keys.add(indexId + ":" + call.getCallerId() + ":" + call.getCalleeId());
  }
  ```
  这两个方法在 `synthesizeAndPersistHeuristicEdges` 中调用，用于启发式边（`InterfaceImplSynthesizer`、`SpringEventSynthesizer`）的去重。当 `indexId` 下的记录超过 10,000 条时，只加载前 10,000 条。
- **严重程度**: P2
- **现状**: `buildInheritanceIndex` 构建接口→实现类的映射，用于 `InterfaceImplSynthesizer` 推断接口方法调用。如果索引有 15,000 条继承关系，后 5,000 条被截断，部分接口的实现类丢失。`InterfaceImplSynthesizer` 无法为这些接口生成调用边。

  `loadExistingEdgeKeys` 加载已有调用边的去重集合。截断导致已有边不在集合中，启发式合成器会重新生成这些边。如果 `saveReplacingExisting` 的去重判断依赖边 key 查找而非唯一约束，可能产生重复边。
- **风险**: 大型索引（>10,000 继承关系或 >10,000 调用边）的启发式边合成不完整或产生重复边。接口方法调用图不完整影响所有下游分析（影响分析、死代码检测、社区检测）。
- **建议**: (a) 使用分页循环替代 `setLimit(MAX_QUERY_RESULTS)`（与 `rebuildSymbolTable` 一致）；(b) 或将继承索引和边去重集合缓存到 `CodeCacheManager` 中。
- **信心水平**: 确定
- **发现来源视角**: CodeIndexService 全量审计

---

### [AR-181] indexFile 的 `invalidateAnalysisCache` 在锁释放之后执行——缓存失效与 DB 提交之间存在竞态窗口

- **文件**: `nop-code/nop-code-service/.../impl/CodeIndexService.java:314-332`
- **证据片段**:
  ```java
  // indexFile (line 314-332):
  public CodeFileAnalysisResult indexFile(String indexId, String filePath, String sourceCode) {
      withIndexLock(indexId, () -> {
          transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn ->
                  ormTemplate.runInSession(session -> {
                      ensureIndexEntity(indexId, null, session);
                      persistSingleFileInSession(indexId, result, session);
                      return null;
                  }));
          updateIndexStats(indexId);
      });
      invalidateAnalysisCache(indexId);  // ← 事务已提交、锁已释放，在外部执行
      return result;
  }
  ```
  对比 `indexDirectory`（line 294-310）：
  ```java
  invalidateAnalysisCache(indexId);  // ← 事务之前，也在锁外
  return withIndexLock(indexId, () -> { ... });
  ```
  `indexFile` 先提交事务并释放锁，然后才清除缓存。在 `withIndexLock` 释放后、`invalidateAnalysisCache` 执行前，其他线程可能读取到旧的缓存数据（不含新写入的符号）。
- **严重程度**: P2
- **现状**: 两个索引入口的缓存失效时序不一致：
  - `indexDirectory`: 事务前清除 → 索引期间查询重建缓存 → 事务提交后缓存与 DB 一致
  - `indexFile`: 事务后清除 → 事务提交后、缓存清除前有时间窗口，查询返回旧缓存数据
  - `triggerIncrementalIndex`（line 660）: 事务前清除，与 `indexDirectory` 一致
  
  在并发场景下，`indexFile` 提交后、缓存清除前的窗口中，`getOrRebuildSymbolTable` 返回不含新符号的旧 SymbolTable。
- **风险**: 短暂的缓存不一致窗口：新提交的符号对查询不可见（直到缓存被清除并重建）。
- **建议**: 将 `invalidateAnalysisCache(indexId)` 移到 `withIndexLock` 块内部、事务提交之后但在释放锁之前执行，确保缓存失效的原子性。
- **信心水平**: 确定
- **发现来源视角**: BizModel 事务边界追踪

---

### [AR-182] NopCodeIndexBizModel 的 incrementalStatusMap 使用 LRU 但无容量同步——服务重启后状态丢失

- **文件**: `nop-code/nop-code-service/.../entity/NopCodeIndexBizModel.java:44-50`
- **证据片段**:
  ```java
  private final Map<String, IncrementalStatus> incrementalStatusMap = Collections.synchronizedMap(
          new LinkedHashMap<String, IncrementalStatus>(16, 0.75f, true) {
              @Override
              protected boolean removeEldestEntry(Map.Entry<String, IncrementalStatus> eldest) {
                  return size() > MAX_STATUS_ENTRIES;  // 20
              }
          });
  ```
- **严重程度**: P3
- **现状**: `incrementalStatusMap` 是内存中的 LRU 缓存（最大 20 条），用于 `getIncrementalStatus` 查询。状态在以下情况下丢失：
  1. 超过 20 个索引时最老的状态被 LRU 淘汰
  2. 服务重启后全部状态丢失
  
  这不是 bug（设计如此），但用户可能在长期运行或管理多个索引时困惑于状态消失。
- **风险**: 前端 Dashboard 无法显示超过 20 个索引的增量索引状态，且服务重启后状态完全丢失。
- **建议**: 将索引状态持久化到 `NopCodeIndex` 实体的扩展字段中，或增大 LRU 容量。
- **信心水平**: 确定
- **发现来源视角**: BizModel 事务边界追踪

---

## 总评

本轮从两个全新视角切入：**ORM 模型 + 实体关系审计** 和 **CodeIndexService 全量审计 + 事务边界追踪**。

**最值得关注的 3 个方向**：

1. **NopCodeSemanticEdge 逻辑删除与物理删除不匹配**（AR-176）——唯一使用 `useLogicalDelete` 的实体，但删除逻辑按物理删除处理。重建索引时可能触发唯一约束冲突，或 DB 中累积大量软删除幽灵记录。这是一个 ORM 模型与业务逻辑的不一致——其他 10 个实体都不使用逻辑删除。

2. **getProjectFilePaths 加载全量 CLOB 实体**（AR-177）——全量索引路径中，第一个文件的持久化就触发全表扫描，加载包含 CLOB `sourceCode` 的所有文件实体。10 万文件索引可能直接 OOM。这是一个隐藏在依赖解析子路径中的性能炸弹。

3. **resolveQualifiedNamesToIds 的增量性能退化**（AR-178）——增量索引时每个变更文件触发对全部继承/注解记录的重新扫描和处理。100 个变更文件 × 50,000 继承记录 = 500 万次查找。这是增量索引线性退化而非增量处理的根因之一。

**正面发现**：
- NopCodeIndexBizModel 的权限字符串已从 r12 的 `code-query` 修正为实体级格式
- ORM 模型的关系定义（9 个 to-many 从 NopCodeIndex 出发，全部有 `cascadeDelete`）设计合理
- `CodeCacheManager` 有良好的锁保护和 TTL 过期机制
- `persistFlows` 有 `MAX_FLOWS_PER_INDEX=5000` 的截断保护
- `deleteEntitiesPaged` 使用分页批量删除，避免单次加载过多实体

## 本次审查的盲区自评

1. **Nop ORM 逻辑删除的运行时行为**：未验证 `batchDeleteEntities` 对 `useLogicalDelete` 实体是否真的只做软删除。需要查看 Nop ORM 框架的 `DaoEntityHelper` 或类似实现。
2. **saveReplacingExisting 的精确语义**：`orm_initedValues()` 返回的是所有设值字段还是仅修改的字段？影响 AR-178 的严重程度判断。
3. **getProjectFilePaths 的实际 SQL**：未验证 Nop ORM 是否对 CLOB 字段延迟加载。如果 CLOB 是 lazily loaded，则 AR-177 的影响降低。
4. **NopCodeFlow cascadeDelete 与手动删除的交互**：ORM 级联删除是否在 `batchDeleteEntities` 中被触发？如果是，AR-179 中的手动删除 membership 是冗余的。
5. **端到端运行验证**：所有发现基于静态代码分析。

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | — |
| P1      | 2    | 语义边逻辑删除不匹配（AR-176）、getProjectFilePaths CLOB OOM（AR-177） |
| P2      | 4    | resolveQualifiedNamesToIds 增量性能（AR-178）、FlowMembership 缺 indexId（AR-179）、继承/边查询截断（AR-180）、缓存失效时序（AR-181） |
| P3      | 1    | incrementalStatusMap LRU 容量（AR-182） |
