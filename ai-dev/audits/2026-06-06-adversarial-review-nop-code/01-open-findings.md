# Adversarial Review: nop-code — Open Findings

> **日期**: 2026-06-06（第 8 轮对抗性审查）
> **模块**: nop-code（13 个子模块）
> **审查类型**: 开放式对抗性审查
> **发现来源视角**: 事务边界追踪者 + 图算法攻击者 + 代码考古学家 + 多语言适配器审计
> **审查范围**: codegen 模板、图算法、语言适配器、事务边界、ORM 模型、IoC 配置

## 去重确认

已审阅以下历史报告：
- r7 (2026-06-02): AR-88~93（@Auth 缺失、evictOverflow 无限循环、嵌套属性删除等）
- r6 (2026-06-01 deep audit): 04-11 callType 字典不匹配、16-01 集成测试 @Disabled 等
- r5 (2026-05-31 刷新): AR-75~76（resolveQualifiedNamesToIds OOME、缓存降级为空）
- r4 (2026-05-31): AR-59~74（三重复制、language 硬编码、bfsCollect 深度 1 等）

**已修复确认**：
- AR-88/AR-89（@Auth 系统性缺失）→ **已修复**。`NopCodeSymbolBizModel` 所有 17 个方法现在均有 `@Auth` 注解。
- AR-90（detectDeadCode 为 @BizQuery）→ **已修复**。现在为 `@BizMutation` + `@Auth(roles = "admin")`。

本轮发现均为新视角切入的新问题。

---

### [AR-94] 全模块零 `runInTransaction`——所有写操作在 `runInSession` 下无原子性保证

- **文件**: `nop-code-service/.../impl/CodeIndexService.java:315, 333, 568, 1654`
- **证据片段**:
  ```java
  // indexDirectory (line 315):
  return ormTemplate.runInSession(session -> {
      ensureIndexEntity(indexId, resolvedPath, session);
      persistInSession(indexId, resolvedPath, finalResult, session);
      return finalResult.getFileResults().size();
  });

  // deleteIndex (line 568):
  ormTemplate.runInSession(session -> {
      deleteEntitiesPaged(session, NopCodeUsage.class, "indexId", indexId);
      // ... 10 more entity types ...
      daoProvider.daoFor(NopCodeIndex.class).deleteEntityById(indexId);
      return null;
  });

  // persistFlows (line 1654):
  ormTemplate.runInSession(session -> {
      // DELETE all existing flows + memberships
      // INSERT all new flows + memberships
      return null;
  });
  ```
- **严重程度**: P0
- **现状**: nop-code 模块中所有 5 个写操作边界均使用 `ormTemplate.runInSession`，没有任何 `runInTransaction` 调用。`runInSession` 提供 ORM Session 上下文但不保证原子事务。`persistInSession` 保存 file、symbol、call、inheritance、annotationUsage、usage、dependency、semanticEdge 八种实体类型（数千条记录）。如果在第 500 个 symbol 保存时发生异常（如约束冲突），已 flush 的前 499 个实体已提交到数据库，后续实体丢失。
- **风险**:
  1. `indexDirectory`/`indexFile` 部分提交导致索引状态不一致（有 file 记录但缺少对应的 symbol/call 记录）
  2. `deleteIndex` 中途失败留下孤儿数据（Usage 已删除但 Symbol 仍在）
  3. `persistFlows` 在删除旧 flow 后插入新 flow 前失败——所有 flow 数据永久丢失
  4. `triggerIncrementalIndex` 在删除变更文件记录后分析失败——原始数据已被删除
- **建议**: 对所有多实体写操作改用 `ormTemplate.runInTransaction(txn -> { ... })` 或在 session 内手动管理事务边界。对于 `deleteIndex` 这种大批量操作，可按实体类型分事务执行，每个事务内保证原子性。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-95] CommunityDetector 构建 Leiden Network 时传错 `directed=true`——社区检测结果质量受损

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/community/CommunityDetector.java:576`
- **证据片段**:
  ```java
  // Lines 394-397: edges already converted to undirected pairs
  int minIdx = Math.min(callerIdx, calleeIdx);
  int maxIdx = Math.max(callerIdx, calleeIdx);
  edgeList.add(new int[]{minIdx, maxIdx});

  // Line 576: but Network constructed with directed=true
  Network network = new Network(nNodes, true, edges, false, false);
  ```
- **严重程度**: P1
- **现状**: 边在第 394-397 行已转为无向对 `(minIdx, maxIdx)`，但第 576 行构建 CWTS `Network` 时传了 `directed=true`。CWTS Leiden 库在 `directed=true` 时将每条边视为从 `minIdx → maxIdx` 的有向边。由于 `minIdx` 始终小于 `maxIdx`，所有边都从低编号节点指向高编号节点。这意味着高编号节点没有任何出边，Leiden 算法在有向网络上运行，社区划分结果受节点编号的任意顺序影响。
- **风险**: 社区检测结果质量显著低于正确配置下的结果。具体表现为：(a) 模块度(modularity)偏低；(b) 社区边界受节点插入顺序影响而非纯图结构；(c) 与其他工具（如 Gephi）的结果不可比。
- **建议**: 改为 `new Network(nNodes, false, edges, false, false)` 以正确声明无向网络。
- **信心水平**: 确定
- **发现来源视角**: 图算法攻击者

---

### [AR-96] Python 装饰器提取因 TSNode.equals() 引用比较而失效

- **文件**: `nop-code/nop-code-lang-python/src/main/java/io/nop/code/lang/python/PythonCodeFileAnalyzer.java:250`
- **证据片段**:
  ```java
  // Line 248-254:
  for (int i = 0; i < parent.getChildCount(); i++) {
      TSNode child = parent.getChild(i);
      if (child != null && child.equals(defNode)) {  // uses Object.equals() → reference identity
          defIndex = i;
          break;
      }
  }
  ```
- **严重程度**: P1
- **现状**: `TSNode` 类未覆盖 `Object.equals()`，`child.equals(defNode)` 退化为引用比较 (`==`)。由于 tree-sitter 每次调用 `getChild()` 返回新的 wrapper 对象，而 `defNode` 来自不同的调用链（`walkNode → visitClassDefinition/visitFunctionDefinition`），引用比较几乎永远失败。`defIndex` 始终为 `-1`，第 257-284 行的回溯查找装饰器循环的起始条件 `defIndex - 1` 变为 `-2`，循环体永远不执行。同时 `visitDecorator()` 方法（line 231-234）是空操作，`walkBlockChildren` 跳过 decorator 节点。三条路径全部失效——Python 装饰器在 parent-sibling 模式下实质上从未被提取。
- **风险**: Python 代码的注解（如 `@dataclass`、`@pytest.fixture`、`@abstractmethod`）完全缺失。影响：(a) `findByAnnotation` 查询对 Python 无效；(b) 注解驱动的语义边(`AnnotationPatternExtractor`)遗漏 Python 代码；(c) 入口点检测可能遗漏 `@pytest` 等标记的测试方法。
- **建议**: 将 `child.equals(defNode)` 替换为 `TSNode.eq(child, defNode)`（tree-sitter 提供的静态比较方法）。
- **信心水平**: 确定
- **发现来源视角**: 多语言适配器审计

---

### [AR-97] SpringEventSynthesizer 将每个 publisher 误关联到全部事件类型——大量虚假边

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/heuristic/SpringEventSynthesizer.java:126-130`
- **证据片段**:
  ```java
  if (callee != null && PUBLISH_EVENT_METHOD.equals(callee.getName())) {
      for (String eventType : knownEventTypes) {   // links to ALL event types
          result.computeIfAbsent(eventType, k -> new ArrayList<>())
                  .add(new PublishPoint(sym.getId()));
      }
  }
  ```
- **严重程度**: P1
- **现状**: 当发现一个 `publishEvent` 调用者时，代码将其关联到 `knownEventTypes` 中的**所有**事件类型，而非仅关联到该调用者实际发布的事件类型。设有 P 个 publisher、L 个 listener、E 种事件类型，此代码产生 P×L×E 条边（每对 publisher-listener 在每个事件类型下都产生边），而非正确的 P×L_per_type 条。
- **风险**: 语义边图充满虚假关联。(a) 社区检测将这些虚假边视为真实连接，社区边界被污染；(b) 影响分析误判调用关系；(c) 对大型代码库（如 20 个 publisher、50 个 listener、5 种事件类型），产生 5,000 条虚假边而实际仅约 100 条。
- **建议**: 检查 publisher 调用参数以确定具体发布的事件类型（如 `publishEvent(new OrderCreatedEvent(...))`），然后仅关联该事件类型的 listener。
- **信心水平**: 很可能（需要验证 `publishEvent` 调用的参数是否在 AST 层面可解析）
- **发现来源视角**: 图算法攻击者

---

### [AR-98] CommunityDetector.splitSuperCommunities 丢弃单例节点——clusteredSymbols 计数偏差

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/community/CommunityDetector.java:515-516, 464-466`
- **证据片段**:
  ```java
  // Line 515-516 (inside recursiveSplit):
  for (Set<String> cluster : clustering.getClusters()) {
      if (cluster.size() < 2) continue;   // singleton nodes SILENTLY DROPPED
      // ...
  }

  // Line 464-466 (recomputes count):
  result.setClusteredSymbols(finalCommunities.stream()
          .mapToInt(Community::getSymbolCount).sum());
  ```
- **严重程度**: P1
- **现状**: `recursiveSplit` 使用 Label Propagation 拆分大型社区后，仅保留大小 ≥ 2 的子社区（line 516）。落在大小为 1 的簇中的节点被静默丢弃。然后在第 464-466 行重新计算 `clusteredSymbols`，该值低于实际处理的节点数。对于拆分一个 100 节点的社区，如果产生 3 个子社区（各 30 节点）和 10 个单例，最终 `clusteredSymbols` 少了 10。
- **风险**: (a) 返回给调用方的社区覆盖统计不准确；(b) 被丢弃的节点在后续分析中"消失"，永远不会被关联到任何社区；(c) `KnowledgeGapAnalyzer` 依赖社区检测结果检测孤立节点，丢弃的节点会被误判为原始图中的孤立节点。
- **建议**: 将单例节点收集到 "unclustered" 列表，或合并到最近的社区。
- **信心水平**: 确定
- **发现来源视角**: 图算法攻击者

---

### [AR-99] ImpactAnalyzer.findSymbolByQualifiedName 使用 startsWith 匹配——返回错误符号

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/impact/ImpactAnalyzer.java:315-327`
- **证据片段**:
  ```java
  CodeSymbol bestMatch = null;
  for (CodeSymbol symbol : symbolTable.getAll()) {   // O(N) scan
      if (symbol.getQualifiedName() != null &&
          symbol.getQualifiedName().startsWith(withoutParams)) {
          if (symbol.getQualifiedName().equals(withoutParams)) {
              return symbol;  // exact match - good
          }
          if (bestMatch == null) {
              bestMatch = symbol;  // first prefix match - WRONG
          }
      }
  }
  return bestMatch;
  ```
- **严重程度**: P2
- **现状**: 当精确匹配 `Foo.handle` 不存在时（因为实际的 qualified name 带参数如 `Foo.handle(Request)`），`startsWith("Foo.handle")` 也会匹配 `Foo.handlerChain`、`Foo.handleBatch` 等。如果精确匹配未找到，返回第一个前缀匹配的符号，可能是完全无关的方法。
- **风险**: 影响分析对错误符号运行 BFS，结果完全不准确。调用方看到的受影响符号列表与实际无关。
- **建议**: 精确匹配失败时，不应用 `startsWith` 回退，或在 `startsWith` 匹配后检查下一个字符是否为 `(` 或字符串结束。
- **信心水平**: 确定
- **发现来源视角**: 图算法攻击者

---

### [AR-100] NopCodeFile 和 NopCodeSymbol 的 ORM 子关系缺少 cascadeDelete——手动删除路径外有孤儿风险

- **文件**: `nop-code/model/nop-code.orm.xml:228-245, 357-416`
- **证据片段**:
  ```xml
  <!-- NopCodeFile relations (lines 228-245) - NO cascadeDelete -->
  <to-many name="symbols" refEntityName="...NopCodeSymbol" refPropName="file">
      <join><on leftProp="id" rightProp="fileId"/></join>
  </to-many>
  <to-many name="usages" refEntityName="...NopCodeUsage" refPropName="file">
      <join><on leftProp="id" rightProp="fileId"/></join>
  </to-many>
  <to-many name="calls" refEntityName="...NopCodeCall" refPropName="file">
      <join><on leftProp="id" rightProp="fileId"/></join>
  </to-many>

  <!-- NopCodeSymbol has 10 to-many relations - ALL lack cascadeDelete -->
  ```
- **严重程度**: P2
- **现状**: `NopCodeIndex` 的所有 9 个子关系都正确设置了 `cascadeDelete="true"`，但 `NopCodeFile` 的 3 个子关系（symbols、usages、calls）和 `NopCodeSymbol` 的全部 10 个子关系（children、members、usages、annotations、callees、callers、superTypes、subTypes 等）均缺少 `cascadeDelete`。服务层代码通过 `deleteFileRecords()` 手动处理了部分情况，但：(a) `deleteFileRecords` 不删除 `NopCodeSemanticEdge`（AR-60/AR-73 已报告）；(b) 如果通过 ORM 的 `dao.deleteEntity(fileEntity)` 删除文件（绕过 `deleteFileRecords`），子实体不会被级联删除。
- **风险**: 任何绕过服务层的删除路径（如管理界面直接删除、批处理脚本、未来代码重构）都会产生孤儿记录。
- **建议**: 为 `NopCodeFile` 的 `symbols`、`usages`、`calls` 关系添加 `cascadeDelete="true"`。为 `NopCodeSymbol` 的关键子关系（`children`、`members`、`usages`、`annotations`、`callees`、`callers`）添加 `cascadeDelete="true"`。
- **信心水平**: 确定
- **发现来源视角**: 代码考古学家

---

### [AR-101] persistFlows 无分页加载全部 flow + 嵌套加载 membership——大型索引 OOME

- **文件**: `nop-code-service/.../impl/CodeIndexService.java:1653-1665`
- **证据片段**:
  ```java
  ormTemplate.runInSession(session -> {
      IEntityDao<NopCodeFlow> flowDao = daoProvider.daoFor(NopCodeFlow.class);
      QueryBean deleteQuery = new QueryBean();
      deleteQuery.addFilter(FilterBeans.eq("indexId", indexId));
      List<NopCodeFlow> existing = flowDao.findAllByQuery(deleteQuery);  // NO LIMIT
      for (NopCodeFlow existingFlow : existing) {                         // N iterations
          IEntityDao<NopCodeFlowMembership> membershipDao = daoProvider.daoFor(NopCodeFlowMembership.class);
          QueryBean mQuery = new QueryBean();
          mQuery.addFilter(FilterBeans.eq("flowId", existingFlow.getId()));
          membershipDao.batchDeleteEntities(membershipDao.findAllByQuery(mQuery));  // M per flow
      }
      flowDao.batchDeleteEntities(existing);
  ```
- **严重程度**: P2
- **现状**: `persistFlows` 先加载全部 flow（无分页、无 limit），然后对每个 flow 再加载其全部 membership。这是 O(N×M) 的内存模式（N=flow 数量，M=每个 flow 的 membership 数量）。对于大型项目（如 1000 个 flow、每个 20 个 membership），一次性加载 20,000+ 实体到内存。同时 `deleteIndex` 中的 flow 删除（line 573）也有相同的无分页问题。
- **风险**: 大型索引（如分析整个 JDK 或大型 monorepo）时，`persistFlows` 可能 OOME。
- **建议**: 改用 `deleteEntitiesPaged` 模式（与 `deleteIndex` 中其他实体类型的删除方式一致）。先分页删除 membership（通过 `flow.indexId` 嵌套过滤或批量 `IN` 子句），再分页删除 flow。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-102] CommunityDetector.runWithTimeout 超时后泄漏线程

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/community/CommunityDetector.java:741-749`
- **证据片段**:
  ```java
  private static <T> T runWithTimeout(Callable<T> task, long timeoutMs) throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
          Future<T> future = executor.submit(task);
          return future.get(timeoutMs, TimeUnit.MILLISECONDS);
      } finally {
          executor.shutdownNow();
      }
  }
  ```
- **严重程度**: P2
- **现状**: 每次超时调用创建新的 `SingleThreadExecutor`（即新 Thread）。`shutdownNow()` 仅中断线程——如果 Leiden 算法不响应中断（CPU 密集型计算通常不检查中断标志），线程继续运行。反复超时调用会泄漏线程。调用方捕获 `Exception` 后回退到 Label Propagation，但泄漏的线程继续消耗 CPU。
- **风险**: 配置了超时的社区检测在大型图上可能反复超时，每次泄漏一个线程。长期运行的服务实例中线程数持续增长。
- **建议**: 使用共享 `ExecutorService` + 协作式取消检查（在算法循环中检查 `Thread.interrupted()`），或使用 `ForkJoinPool.managedBlock`。
- **信心水平**: 很可能
- **发现来源视角**: 图算法攻击者

---

### [AR-103] CallGraph 核心数据结构非线程安全——并行文件解析时可能损坏

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/graph/CallGraph.java:7-19`
- **证据片段**:
  ```java
  public class CallGraph {
      private final Map<String, List<String>> forwardEdges = new HashMap<>();
      private final Map<String, List<String>> reverseEdges = new HashMap<>();
      private final Set<String> edgeKeys = new HashSet<>();

      public void addEdge(String caller, String callee) {
          String edgeKey = caller + "->" + callee;
          if (!edgeKeys.add(edgeKey)) {
              return;
          }
          forwardEdges.computeIfAbsent(caller, k -> new ArrayList<>()).add(callee);
          reverseEdges.computeIfAbsent(callee, k -> new ArrayList<>()).add(caller);
      }
  ```
- **严重程度**: P2
- **现状**: `CallGraph` 使用 `HashMap` + `ArrayList`，无任何同步机制。`HashMap.computeIfAbsent` 在并发写入时可能损坏内部桶链表。如果 `ProjectAnalyzer` 并行解析多个文件（每个文件解析后向同一个 `CallGraph` 添加边），数据结构会损坏。
- **风险**: 并发场景下 `CallGraph` 内部状态不一致，可能表现为：(a) 丢失边；(b) `ConcurrentModificationException`；(c) HashMap 无限循环（桶链表成环）。
- **建议**: 如果 `ProjectAnalyzer` 确实并行调用 `addEdge`，改用 `ConcurrentHashMap` + 同步 `ArrayList` 操作，或文档标注为单线程使用。
- **信心水平**: 很可能（取决于 `ProjectAnalyzer` 是否并行调用）
- **发现来源视角**: 图算法攻击者

---

### [AR-104] 代码生成模板 `{rel.keyProp}` 缺少 `$`——生成错误 Javadoc 注释

- **文件**: `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm-entity/{!entityModel.notGenCode}{entityModel.classPackagePath}/_gen/_{entityModel.simpleClassName}.java.xgen:322`
- **证据片段**:
  ```
  /* ${rel.displayName}。 refPropName: ${rel.refPropName}, keyProp: {rel.keyProp} */
  ```
  生成产物（`_NopCodeSymbol.java`）中的实际输出：
  ```java
  /* 符号所属索引。 refPropName: indexId, keyProp: {rel.keyProp} */  // literal string!
  ```
- **严重程度**: P3
- **现状**: 模板变量 `{rel.keyProp}` 缺少 `$` 前缀，被当作字面字符串而非模板变量。所有生成的实体类中，关系常量的 Javadoc 注释里 `keyProp:` 后面始终是字面文本 `{rel.keyProp}` 而非实际值。影响 nop-code 及所有使用此模板的模块。
- **风险**: Javadoc 误导开发者。如果有人根据注释中的 `keyProp` 来理解关系结构，会得到错误信息。
- **建议**: 修改模板为 `${rel.keyProp}`。
- **信心水平**: 确定
- **发现来源视角**: 代码考古学家

---

### [AR-105] NopCodeAnnotationUsage.annotatedSymbolId 缺少 NOT NULL——空值绕过唯一约束

- **文件**: `nop-code/model/nop-code.orm.xml:668, 714`
- **证据片段**:
  ```xml
  <!-- Line 668: annotatedSymbolId is nullable -->
  <column code="ANNOTATED_SYMBOL_ID" displayName="被注解符号ID" domain="codeId"
          name="annotatedSymbolId" propId="4" stdDataType="string" stdSqlType="VARCHAR"/>

  <!-- Line 714: but part of unique key -->
  <unique-keys>
      <key name="uk_annotation_usage_unique" columns="indexId,annotationTypeId,annotatedSymbolId"/>
  </unique-keys>
  ```
- **严重程度**: P2
- **现状**: `annotatedSymbolId` 是"被注解符号 ID"——语义上必须指向一个存在的符号，但 ORM 定义中未标记 `mandatory="true"`。同时它是唯一约束 `uk_annotation_usage_unique` 的一部分。在大多数数据库中，NULL 值不参与唯一约束比较，意味着 `(indexId=X, annotationTypeId=Y, annotatedSymbolId=NULL)` 可以插入任意多行而不违反唯一约束。
- **风险**: (a) 如果 `saveFileResultInSession` 在某些边界条件下产生了 `annotatedSymbolId=null` 的记录，唯一约束不会阻止重复插入；(b) 数据库中可能出现无意义的注解使用记录。
- **建议**: 添加 `mandatory="true"`。
- **信心水平**: 确定
- **发现来源视角**: 代码考古学家

---

### [AR-106] CommunityDetector.calculateCohesion 仅统计出边——高估有外部入边节点的凝聚力

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/community/CommunityDetector.java:768-789`
- **证据片段**:
  ```java
  for (String node : cluster) {
      List<String> callees = callGraph.getCallees(node);  // only outgoing edges!
      if (callees != null) {
          for (String callee : callees) {
              if (cluster.contains(callee)) {
                  internalEdges++;
              } else {
                  externalEdges++;
              }
          }
      }
  }
  ```
- **严重程度**: P3
- **现状**: 凝聚力计算仅统计出边（`getCallees`），忽略入边（`getCallers`）。如果外部节点 C 调用社区内的节点 A，该入边不被统计为外部边。这导致：对外部有很多调用者的社区（如公共工具类），凝聚力被高估。
- **风险**: 社区凝聚力指标不可靠。`splitSuperCommunities` 使用凝聚力阈值做拆分决策，高估的凝聚力可能导致本应拆分的大型社区未被拆分。
- **建议**: 同时统计入边和出边，或文档标注为"出边凝聚力"。
- **信心水平**: 确定
- **发现来源视角**: 图算法攻击者

---

## 总评

本轮审查从事务边界和图算法两个全新视角切入，发现了 nop-code 模块最严重的结构性问题之一：**整个模块的 5 个写操作边界全部使用 `runInSession` 而非 `runInTransaction`，没有任何原子性保证**（AR-94）。这意味着任何写入过程中的异常都会导致部分提交的数据不一致。这是之前 7 轮审查均未覆盖的领域（此前关注点在并发安全、权限、数据完整性，但从未系统检查事务边界）。

图算法层面发现了 Leiden 社区检测的两个正确性问题（AR-95 错误的 `directed` 标志、AR-98 单例节点丢弃）和一个语义边生成的系统性问题（AR-97 SpringEventSynthesizer 的 N×M 虚假边）。三者叠加意味着当前社区检测结果的质量显著低于算法本身的能力。

语言适配器层面发现了 Python 装饰器提取的完全失效（AR-96），这是一个功能正确性问题而非风格问题——`TSNode.equals()` 的引用比较语义是一个容易忽视但影响深远的 API 陷阱。

**本次审查最值得关注的 3 个方向**：

1. **事务原子性缺失**（AR-94）——影响所有写操作的可靠性，是最紧迫的修复项。
2. **社区检测算法正确性**（AR-95 + AR-98）——影响所有依赖社区划分的下游分析。
3. **Python 装饰器提取失效**（AR-96）——影响多语言支持的完整性。

## 本次审查的盲区自评

1. **view.xml / 前端页面**：未审查 nop-code-web 模块的前端配置和页面定义。
2. **Beans.xml IoC 注入正确性**：虽然发现了一些线索（如 `CodeQueryService.ormTemplate` 存而不用），但未系统检查所有 beans.xml 配置是否与实际注入匹配。
3. **端到端运行验证**：所有发现基于静态代码分析，未实际运行测试验证（如 AR-94 的部分提交是否在特定数据库 dialect 下实际发生）。
4. **Delta 定制层**：未检查是否有 Delta 文件覆盖了本报告中涉及的关键代码路径。
5. **TypeScript 适配器深度**：聚焦于 Python 适配器的 TSNode 问题，TypeScript 适配器可能存在类似的细微 bug。

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | 全模块零事务（AR-94） |
| P1      | 4    | Leiden directed 标志（AR-95）、Python 装饰器失效（AR-96）、SpringEvent 虚假边（AR-97）、社区拆分丢节点（AR-98） |
| P2      | 4    | startsWith 错配（AR-99）、cascadeDelete 缺失（AR-100）、persistFlows OOME（AR-101）、线程泄漏（AR-102）、CallGraph 非线程安全（AR-103）、NOT NULL 缺失（AR-105） |
| P3      | 2    | 模板 Javadoc（AR-104）、凝聚力统计偏倚（AR-106） |

---

## 深挖第 2 轮追加

> **审查类型**: 开放式对抗性审查（深挖轮次）
> **起始视角**: 数据一致性审计者 + ORM 会话语义侦探 + 性能模式考古学家
> **去重基线**: 已读取本文件第 1 轮发现（AR-94~AR-106）以及 `ai-dev/audits/` 下所有历史 nop-code 审查报告。

### 去重确认

已确认以下第 1 轮发现与历史报告的关系：
- AR-94（零事务）→ 全新发现，此前 7 轮审查从未系统检查事务边界
- AR-95（directed 标志）→ 全新发现，与 AR-69（Tarjan 递归）同文件但不同问题
- AR-96（Python 装饰器 TSNode.equals）→ 全新发现，此前审查未深入 tree-sitter API 语义
- AR-97（SpringEventSynthesizer 全关联）→ 全新发现
- AR-98（单例节点丢弃）→ 全新发现

本轮深挖聚焦于：第 1 轮盲区中提到的 ORM 会话语义、依赖图查询的重复加载模式、以及缓存降级的 API 透明性。

---

### [AR-107] resolveQualifiedNamesToIds 使用脏会话偏移分页——修改已加载实体后 offset 可能漂移导致跳过记录或重复处理

- **文件**: `nop-code-service/.../impl/CodeIndexService.java:944-987`
- **证据片段**:
  ```java
  // Lines 944-965 — Inheritance resolution
  long inhOffset = 0;
  while (true) {
      QueryBean inhQuery = new QueryBean();
      inhQuery.addFilter(FilterBeans.eq("indexId", indexId));
      inhQuery.setOffset(inhOffset);
      inhQuery.setLimit(BATCH_SIZE);
      List<NopCodeInheritance> inhBatch = inhDao.findAllByQuery(inhQuery);
      if (inhBatch.isEmpty()) break;
      for (NopCodeInheritance inh : inhBatch) {
          String superTypeId = inh.getSuperTypeId();
          if (superTypeId != null) {
              CodeSymbol resolved = symbolTable.getByQualifiedName(superTypeId);
              if (resolved != null) {
                  inh.setSuperTypeId(resolved.getId()); // ← 脏实体
              }
          }
      }
      if (inhBatch.size() < BATCH_SIZE) break;
      inhOffset += BATCH_SIZE;
  }

  // Lines 967-987 — 同样模式应用于 AnnotationUsage
  ```
- **严重程度**: P1
- **现状**: `resolveQualifiedNamesToIds` 在 `persistInSession` 的 `runInSession` 回调内被调用（line 847）。它在分页循环中加载 `NopCodeInheritance` 和 `NopCodeAnnotationUsage` 实体，并将 `superTypeId` / `annotationTypeId` 从 qualified name 修改为 UUID（`inh.setSuperTypeId(resolved.getId())`）。这些修改使实体变为脏状态（dirty），但 session 中没有 flush。

  **问题**：Nop ORM session 使用 offset-based 分页。如果脏实体在分页查询之间被 flush，结果集的排序/偏移可能改变（取决于数据库引擎的查询计划），导致：
  - (a) 某些记录被跳过（未解析其 qualified name）
  - (b) 某些记录被重复处理（虽然 `setSuperTypeId` 是幂等的，但浪费性能）

  这与 AR-01（resolveQualifiedNamesToIds 破坏类型层级）形成叠加效应：即使 AR-01 的根因被修复（不再覆盖 qualified name），如果偏移漂移导致部分记录未被处理，类型层级仍然不完整。
- **风险**: `NopCodeInheritance` 中部分记录的 `superTypeId` 保持为 qualified name 而非 UUID，导致后续的类型层级查询对这部分继承链返回空结果。影响不可预测——取决于数据库引擎和索引状态。
- **建议**: (a) 在每个 batch 处理完后调用 `session.flush()` + `session.evictAll()`，确保脏实体不会影响下一批查询；(b) 或者改用 cursor-based 迭代而非 offset-based 分页；(c) 或者在内存中先收集所有需要修改的记录 ID 和目标值，然后再批量更新。
- **信心水平**: 很可能（取决于 Nop ORM session 对脏实体的 flush 时机和数据库查询计划是否受未提交修改影响）
- **发现来源视角**: ORM 会话语义侦探

---

### [AR-108] CodeCacheManager 超过 MAX_CACHE_SYMBOLS 时返回不完整的 SymbolTable/CallGraph，但调用方无法区分完整与部分数据

- **文件**: `nop-code-service/.../impl/CodeCacheManager.java:148-151, 178-181`
- **证据片段**:
  ```java
  // Line 148-151:
  if (totalLoaded >= MAX_CACHE_SYMBOLS) {
      LOG.warn("Symbol cache for index {} exceeded MAX_CACHE_SYMBOLS({}), returning partial data ({} symbols loaded)",
              indexId, MAX_CACHE_SYMBOLS, totalLoaded);
      return table; // ← 返回不完整的 SymbolTable，无任何标记
  }

  // Line 178-181:
  if (totalLoaded >= MAX_CACHE_EDGES) {
      LOG.warn("Call graph cache for index {} exceeded MAX_CACHE_EDGES({}), returning partial data ({} edges loaded)",
              indexId, MAX_CACHE_EDGES, totalLoaded);
      return callGraph; // ← 同样
  }
  ```
- **严重程度**: P1
- **现状**: `CodeCacheManager.rebuildSymbolTable` 和 `rebuildCallGraph` 在加载量超过 `MAX_CACHE_SYMBOLS`（100,000）或 `MAX_CACHE_EDGES`（500,000）时，仅打印 `LOG.warn` 然后返回已加载的部分数据。`SymbolTable` 和 `CallGraph` 类没有 `isPartial()` 或 `isTruncated()` 标记。

  **所有下游消费者无法感知数据不完整**：
  - `ImpactAnalyzer.analyzeImpact` 基于部分 SymbolTable 做 BFS → 影响范围偏小
  - `CommunityDetector.detectCommunities` 基于部分 CallGraph 做社区检测 → 社区边界错误
  - `DeadCodeDetector.detectDeadCode` 基于部分数据标记死代码 → 大量假阳性（实际被使用的符号被误判为死代码，因为引用它们的调用边不在图中）
  - `FlowDetector.detectFlows` 基于部分数据构建执行流 → 流路径断裂
  - `getTypeHierarchy` / `getCallHierarchy` → 返回不完整的层级树

  `LOG.warn` 是唯一的信号，但 API 调用者（GraphQL 客户端、BizModel 方法）无法访问日志，且返回类型不携带截断信息。
- **风险**: 对于大型代码库（>100K 符号或 >500K 调用边），所有图分析功能静默返回不完整结果。用户看到的死代码报告、社区检测结果、影响分析结果、类型层级等全部不准确，但无任何 API 级别的错误或警告。
- **建议**: (a) 在 `SymbolTable` 和 `CallGraph` 中添加 `truncated` 标记字段；(b) 在 `CodeCacheManager` 的公共方法返回值中携带截断信息；(c) 在 BizModel 层面，当检测到截断时向 API 响应中添加警告字段；(d) 或者改为抛出明确的异常，让调用方知道数据不可用。
- **信心水平**: 确定
- **发现来源视角**: 数据一致性审计者

---

### [AR-109] CodeGraphService 中 3 个方法各自独立全量加载 NopCodeDependency——同一个查询操作可能触发 3 次相同的全表扫描

- **文件**: `nop-code-service/.../impl/CodeGraphService.java:593-644`
- **证据片段**:
  ```java
  // buildForwardAdjacency (line 593-610):
  private Map<String, List<DepEdgeDTO>> buildForwardAdjacency(String indexId) {
      IEntityDao<NopCodeDependency> dao = daoProvider.daoFor(NopCodeDependency.class);
      QueryBean q = new QueryBean();
      q.addFilter(FilterBeans.eq("indexId", indexId));
      List<NopCodeDependency> deps = dao.findAllByQuery(q);  // ← 全量加载
      ...
  }

  // buildReverseAdjacency (line 612-629): 同样全量加载
  // buildForwardStringAdjacency (line 631-644): 同样全量加载
  ```
  调用关系：
  ```
  getDeps(indexId) → buildForwardAdjacency(indexId)  // 第 1 次
  getReverseDeps(indexId) → buildReverseAdjacency(indexId)  // 第 2 次
  findCycles(indexId) → buildForwardStringAdjacency(indexId)  // 第 3 次
  ```
- **严重程度**: P2
- **现状**: `CodeGraphService` 的 3 个私有方法（`buildForwardAdjacency`、`buildReverseAdjacency`、`buildForwardStringAdjacency`）各自独立调用 `dao.findAllByQuery(q)` 全量加载同一个 indexId 的所有 `NopCodeDependency` 记录。它们的区别仅在如何从相同数据构建不同的内存数据结构。

  如果前端页面同时发起 `getDeps` 和 `getReverseDeps` 查询（如依赖关系可视化页面），会触发 2 次完全相同的全表扫描。对 10,000 个依赖记录，这意味着 20,000 行数据库读取 + 20,000 个实体对象创建。

  与 `CodeCacheManager`（对 SymbolTable 和 CallGraph 做了缓存）不同，依赖图数据没有任何缓存机制。
- **风险**: 依赖图查询性能与查询次数线性增长，无缓存、无去重。在频繁查询场景下（如持续刷新的依赖关系页面），数据库压力累积。
- **建议**: (a) 提取公共的 `loadAllDependencies(indexId)` 方法，一次加载后传递给各构建方法；(b) 或者将依赖图数据也纳入 `CodeCacheManager` 的缓存体系；(c) 或者至少在方法内用一次加载构建 forward + reverse 两个 map。
- **信心水平**: 确定
- **发现来源视角**: 性能模式考古学家

---

### [AR-110] indexFile 在 session 提交后才刷新缓存——并发查询可能拿到过时的缓存数据

- **文件**: `nop-code-service/.../impl/CodeIndexService.java:326-339`
- **证据片段**:
  ```java
  public CodeFileAnalysisResult indexFile(String indexId, String filePath, String sourceCode) {
      ICodeFileAnalyzer fileAnalyzer = registry.getAnalyzer(filePath);
      // ...
      CodeFileAnalysisResult result = fileAnalyzer.analyze(filePath, sourceCode);

      ormTemplate.runInSession(session -> {
          ensureIndexEntity(indexId, null, session);
          persistSingleFileInSession(indexId, result, session);
          return null;
      });
      invalidateAnalysisCache(indexId);  // ← 在 session 之后，而非之前
      return result;
  }

  // 对比 indexDirectory (line 303-305):
  invalidateAnalysisCache(indexId);  // ← 在 session 之前
  ReentrantLock lock = indexLocks.computeIfAbsent(...);
  ```
- **严重程度**: P2
- **现状**: `indexFile` 的缓存刷新在 `runInSession` 回调**之后**（line 338）。`indexDirectory` 和 `triggerIncrementalIndex` 的缓存刷新在 session 之前（line 305, `invalidateAnalysisCache(indexId)`）。

  **两种顺序都有问题**：
  - `indexFile`（后刷新）：在 `runInSession` 执行期间（如果 session 还未提交），另一个线程可能读到旧的缓存数据。
  - `indexDirectory`（前刷新）：在 session 开始前清空缓存，但新数据尚未写入数据库，此时其他线程的缓存重建会读到不完整的数据。

  两种顺序都存在竞态窗口。但 `indexFile` 的后刷新模式问题更明显：如果 `runInSession` 成功提交但 `invalidateAnalysisCache` 因异常（如 `NopException`）未执行，缓存将永久包含过时数据直到 TTL 到期（1 小时）。
- **风险**: `indexFile` 后，立即发起的图分析查询（如 `detectCommunities`、`getImpactAnalysis`）可能返回不包含新索引文件的过时结果，且无任何警告。
- **建议**: 统一在 session 内（session 提交前）执行 `invalidateAnalysisCache`，或在 session 回调的 finally 块中执行。同时 `indexDirectory` 的前刷新顺序也应调整为 session 内执行。
- **信心水平**: 很可能
- **发现来源视角**: 数据一致性审计者

---

### [AR-111] CodeGraphService.getDepGraph/findDependentFiles/findCycles 使用 findAllByQuery 无分页——大型索引依赖图可能 OOME

- **文件**: `nop-code-service/.../impl/CodeGraphService.java:510, 557, 597, 616, 635`
- **证据片段**:
  ```java
  // Line 510 (getDepGraph):
  List<NopCodeDependency> deps = dao.findAllByQuery(q);
  // Full dependency list needed for graph building

  // Line 557 (findDependentFiles):
  List<NopCodeDependency> allDeps = depDao.findAllByQuery(depQuery);

  // Line 597 (buildForwardAdjacency):
  List<NopCodeDependency> deps = dao.findAllByQuery(q);

  // Line 616 (buildReverseAdjacency):
  List<NopCodeDependency> deps = dao.findAllByQuery(q);

  // Line 635 (buildForwardStringAdjacency):
  List<NopCodeDependency> deps = dao.findAllByQuery(q);
  ```
- **严重程度**: P2
- **现状**: 5 处代码均使用 `findAllByQuery` 无分页加载 `NopCodeDependency` 全量数据。对于分析整个 nop-entropy 仓库（约 3000+ Java 文件，每个文件平均 5-10 个 import），依赖记录可达 15,000-30,000 条。虽然 `NopCodeDependency` 实体较轻量（不含 `sourceCode` CLOB），但与 AR-109 叠加（同一请求可能触发 2-3 次全量加载），内存峰值可达 60,000-90,000 个实体对象。

  注释 `// Full dependency list needed for graph building`（line 511）表明这是有意为之的设计选择。对于内存中的图算法（Tarjan SCC、BFS），确实需要完整数据。但缺少任何大小检查或分页降级机制。
- **风险**: 对于非常大的 monorepo（>10,000 文件，>50,000 依赖），依赖图查询可能 OOME 或导致 GC 压力。
- **建议**: (a) 添加大小上限检查（如 `countByQuery` 先计数，超过阈值则拒绝或警告）；(b) 将依赖图数据纳入 `CodeCacheManager` 缓存，避免重复加载；(c) 与 AR-109 的修复结合——一次加载后所有构建方法共享。
- **信心水平**: 很可能
- **发现来源视角**: 性能模式考古学家

---

## 深挖第 2 轮追加 — 总评

本轮深挖从 ORM 会话语义和性能模式角度切入，发现了一个与 AR-94（零事务）相互关联的会话一致性问题：`resolveQualifiedNamesToIds` 的脏会话偏移分页可能在部分记录上静默失败（AR-107）。这意味着即使 AR-94 被修复（加入事务），AR-107 的偏移漂移问题仍会导致部分继承关系和注解使用未被正确解析。

更严重的是 `CodeCacheManager` 的静默截断（AR-108）——这是之前 AR-76（超限降级为空）的修复引入的新问题。之前是降级为完全空的缓存（至少没有错误数据），现在改为返回部分数据（有不完整的数据，但调用方无法感知），实际上比之前**更危险**，因为部分数据比空数据更容易导致错误的决策。

依赖图查询的性能模式（AR-109、AR-111）在之前审查中从未被关注，因为审查焦点一直在索引管线和图算法上。这些全量加载问题与 `CodeCacheManager` 的缓存覆盖范围不一致——SymbolTable 和 CallGraph 有缓存，但 DependencyGraph 没有。

**本次深挖最值得关注的 2 个方向**：

1. **缓存截断不可见**（AR-108）——影响所有图分析 API 的可靠性，需要 API 层面的改进。
2. **脏会话偏移分页**（AR-107）——与 AR-01 和 AR-94 叠加，使类型层级解析的正确性更加脆弱。

## 本次深挖的盲区自评

1. **Nop ORM 引擎内部行为**：未验证 `session.flush()` 在 offset 分页查询之间的实际行为——AR-107 的严重程度取决于 Nop ORM 是否在查询时自动 flush 脏实体。
2. **xmeta 字段级安全**：未检查 xmeta 中的字段级权限是否与 BizModel 的 @Auth 注解一致。
3. **搜索引擎集成**：`CodeSearchService` 和 `ISearchEngine` 的交互未深入审查。
4. **OrmFingerprintStore**：增量索引的指纹存储实现未深入审查。

## 按严重程度分布表（含深挖追加）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | 全模块零事务（AR-94） |
| P1      | 6    | +脏会话偏移分页（AR-107）、缓存截断不可见（AR-108） |
| P2      | 7    | +依赖图 3x 重复加载（AR-109）、缓存刷新竞态（AR-110）、依赖图 OOME（AR-111） |
| P3      | 2    | 模板 Javadoc（AR-104）、凝聚力统计偏倚（AR-106） |

---

## 深挖第 3 轮追加

> **审查类型**: 开放式对抗性审查（独立第 3 轮）
> **起始视角**: 搜索功能审计者 + 语义边提取器性能审计 + BizModel API 契约考古学家 + 图算法 DoS 攻击者
> **去重基线**: 已读取本文件第 1-2 轮发现（AR-94~AR-111）以及 `ai-dev/audits/` 下所有历史 nop-code 审查报告（2026-05-25 至 2026-06-06 的 deep audit 和 adversarial review）。

### 去重确认

第 1-2 轮发现（AR-94~AR-111）均 STILL EXISTS——自上次审查以来代码无相关变更。本轮聚焦于：
- CodeSearchService / CodeQueryService / CodeGraphService 中此前未覆盖的查询和搜索逻辑
- 三个语义边提取器的性能和正确性
- BizModel 层的 API 契约和输入验证
- ORM 持久化路径中的 ID 生成一致性

所有发现均为新视角切入的新问题。

---

### [AR-112] CodeSearchService.filterByFilePattern glob 匹配完全失效——Pattern.quote 阻止了通配符替换

- **文件**: `nop-code/nop-code-service/.../impl/CodeSearchService.java:243-249`
- **证据片段**:
  ```java
  private List<CodeSearchResultDTO> filterByFilePattern(List<CodeSearchResultDTO> results, String filePattern) {
      if (filePattern == null || filePattern.isEmpty()) return results;
      String pattern = java.util.regex.Pattern.quote(filePattern)
              .replace("\\*", ".*").replace("\\?", ".");
      return results.stream()
              .filter(r -> r.getFilePath() != null && r.getFilePath().matches(pattern))
              .collect(Collectors.toList());
  }
  ```
- **严重程度**: P1
- **现状**: `Pattern.quote("*.java")` 产生 `\Q*.java\E`。后续 `.replace("\\*", ".*")` 在 `\Q...\E` 包裹的字符串中查找字面量 `\*`，但 `\Q...\E` 内的 `\*` 不存在（被 quote 后变成了 `\Q*\E` 形式的一部分），所以 replace 是空操作。最终正则模式为 `\Q*.java\E`，仅匹配字面字符串 `*.java`。所有 glob 模式（`*.java`、`src/**/*.ts`、`*Test.java`）的搜索过滤功能完全失效。

  此方法被 4 个搜索路径调用（line 104, 132, 155, 185），影响所有带 `filePattern` 参数的搜索请求。

  已通过独立 Java 测试验证：
  ```
  Pattern: \Q*.java\E
  Match 'Foo.java': false    ← 应该为 true
  Match 'Bar.txt': false
  ```
- **风险**: 用户传入 `filePattern="*.java"` 时期望仅返回 Java 文件，实际返回空结果（无文件名匹配字面量 `*.java`），搜索结果被静默截断为零。如果 filePattern 恰好是某个文件的确切路径（不含通配符），则 `Pattern.quote` 会正确匹配，但这种场景下 glob 本身就没有意义。
- **建议**: 修复为先替换通配符再 quote 特殊字符：
  ```java
  String pattern = filePattern.replace(".", "\\.").replace("*", ".*").replace("?", ".");
  ```
  或使用 `PathMatcher` / `FilenameUtils.wildcardMatch` 等成熟 glob 实现。
- **信心水平**: 确定（已通过独立测试验证）
- **发现来源视角**: 搜索功能审计者

---

### [AR-113] AnnotationPatternExtractor 无 MAX_SYMBOLS 上限——常见注解可触发 O(N²) 爆炸

- **文件**: `nop-code/nop-code-graph/.../semantic/AnnotationPatternExtractor.java:36-89`
- **证据片段**:
  ```java
  public List<CodeSemanticEdge> extractFromFileResults(...) {
      Map<String, Set<String>> annotationToSymbols = new HashMap<>();
      for (CodeFileAnalysisResult file : fileResults) {
          for (CodeAnnotationUsage usage : file.getAnnotationUsages()) {
              annotationToSymbols.computeIfAbsent(annType, k -> new HashSet<>()).add(symbolId);
          }
      }
      // Skip common annotations...
      for (Map.Entry<String, Set<String>> entry : annotationToSymbols.entrySet()) {
          List<String> symbolIds = new ArrayList<>(entry.getValue());
          if (symbolIds.size() < 2) continue;
          for (int i = 0; i < symbolIds.size(); i++) {      // O(N²) per annotation
              for (int j = i + 1; j < symbolIds.size(); j++) {
                  // create edge
              }
          }
      }
  }
  ```
- **严重程度**: P1
- **现状**: 与 `DocKeywordExtractor`（有 `MAX_SYMBOLS=5000` 上限）和 `NameSimilarityExtractor`（同样有上限）不同，`AnnotationPatternExtractor` 对每种注解的符号列表无大小上限。虽然跳过了 `@Override`、`@Deprecated`、`@SuppressWarnings`、`@FunctionalInterface`，但**未跳过** `@Entity`、`@Service`、`@Component`、`@Inject`、`@Autowired`、`@Test` 等常见注解。

  对一个使用 Spring 的中型项目：
  - `@Autowired` 可能标注 2,000 个字段 → C(2000,2) = 1,999,000 条边
  - `@Service` 可能标注 500 个类 → C(500,2) = 124,750 条边
  - 如果有 5 种常见注解各标注 1,000+ 符号 → 总计 ~5,000,000 条 `CodeSemanticEdge` 对象

  每条 `CodeSemanticEdge` 包含 UUID 生成（line 76）、HashMap 操作和字符串拼接，内存和时间消耗巨大。
- **风险**: (a) 语义边提取阶段 OOM 或长时间挂起；(b) 生成的百万级无用边（`@Autowired` 并不意味着两个类概念相关）污染后续图分析；(c) 数据库持久化百万条 `NopCodeSemanticEdge` 记录导致索引体积暴增。
- **建议**: (a) 添加 `MAX_SYMBOLS_PER_ANNOTATION` 上限（如 100），超过时跳过该注解；(b) 将 `@Service`、`@Component`、`@Inject`、`@Autowired`、`@Test`、`@Repository`、`@Configuration`、`@Bean` 加入 skipAnnotations 集合；(c) 添加全局 MAX_EDGES 上限。
- **信心水平**: 确定
- **发现来源视角**: 语义边提取器性能审计

---

### [AR-114] DocKeywordExtractor 和 NameSimilarityExtractor 内循环重复计算——O(N²) 时间基础上再乘以解析成本

- **文件**:
  - `nop-code/nop-code-graph/.../semantic/DocKeywordExtractor.java:53-89`
  - `nop-code/nop-code-graph/.../semantic/NameSimilarityExtractor.java:39-76`
- **证据片段** (DocKeywordExtractor):
  ```java
  for (int i = 0; i < withDocs.size(); i++) {
      Set<String> keywords1 = extractKeywords(withDocs.get(i).getDocumentation()); // 计算一次
      for (int j = i + 1; j < withDocs.size(); j++) {
          Set<String> keywords2 = extractKeywords(withDocs.get(j).getDocumentation()); // 每次内循环重新解析！
          ...
      }
  }
  ```
- **严重程度**: P2
- **现状**: 两个提取器的外循环正确地只计算一次 `keywords1`/`name1`，但内循环中 `keywords2`/`name2` 在**每次内循环迭代**时重新从原始字符串解析。对于 `MAX_SYMBOLS=5000`，外循环执行 4,999 次（i=0 时内循环 4,999 次，i=1 时内循环 4,998 次...），但 `extractKeywords` 被调用总次数为 `5000 + 4999 + 4998 + ... + 1 = 12,502,500` 次（而非最优的 5,000 次）。NameSimilarityExtractor 的 `normalizeName` 同理，每次调用执行多次 `replaceAll`。

  实际 CPU 开销 = 理论最低开销的 ~2,500 倍。
- **风险**: 语义边提取成为索引管线的性能瓶颈。对 5,000 个符号，预期耗时可能从秒级膨胀到分钟级。
- **建议**: 在嵌套循环前预计算所有关键词集/规范化名称，存入数组：
  ```java
  Set<String>[] keywordSets = new Set[withDocs.size()];
  for (int i = 0; i < withDocs.size(); i++) {
      keywordSets[i] = extractKeywords(withDocs.get(i).getDocumentation());
  }
  ```
- **信心水平**: 确定
- **发现来源视角**: 语义边提取器性能审计

---

### [AR-115] CodeQueryService.entityToFileResult 未恢复 imports 字段——getFileOutline 永远返回空 imports

- **文件**: `nop-code/nop-code-service/.../impl/CodeQueryService.java:37-46, 160`
- **证据片段**:
  ```java
  private CodeFileAnalysisResult entityToFileResult(NopCodeFile entity) {
      CodeFileAnalysisResult result = new CodeFileAnalysisResult();
      result.setFilePath(entity.getFilePath());
      result.setPackageName(entity.getPackageName());
      result.setLanguage(entity.getLanguage() != null
              ? CodeLanguage.valueOf(entity.getLanguage()) : null);
      result.setLineCount(entity.getLineCount() != null ? entity.getLineCount() : 0);
      result.setSourceCode(entity.getSourceCode());
      return result;  // ← imports 从未设置
  }

  // line 160:
  outline.setImports(file.getImports());  // ← 始终为 null
  ```
- **严重程度**: P1
- **现状**: `NopCodeFile.imports` 在 ORM 中以 JSON 字符串存储（`JsonOrmComponent` 类型），在 `CodeIndexService.saveFileResultInSession` 中通过 `fileEntity.setImports(JsonTool.stringify(file.getImports()))` 写入。但 `entityToFileResult()` 在从实体构造 `CodeFileAnalysisResult` 时，完全忽略了 `entity.getImports()` 字段。结果是：
  - `getFile()` → `getFiles()` → `getFileOutline()` → `getFileTree()` 全部返回 `imports=null`
  - `getFileOutline()` 的 `outline.setImports(file.getImports())` 设置的是 null
  - GraphQL API 返回的文件信息中 imports 字段永远为空
- **风险**: 前端代码浏览器无法显示文件的 import 列表。依赖 import 信息的下游分析（如模块依赖推断）在查询路径下失效。
- **建议**: 在 `entityToFileResult()` 中添加：
  ```java
  String importsJson = entity.getImports();
  if (importsJson != null && !importsJson.isEmpty()) {
      result.setImports(JsonTool.parseList(importsJson, String.class));
  }
  ```
- **信心水平**: 确定
- **发现来源视角**: 搜索功能审计者

---

### [AR-116] OrmFingerprintStore 与 CodeIndexService 使用不同的文件 ID 生成算法——增量索引路径创建孤立实体

- **文件**:
  - `nop-code/nop-code-service/.../incremental/OrmFingerprintStore.java:42-44`
  - `nop-code/nop-code-service/.../impl/CodeIndexService.java:1798-1800`
- **证据片段**:
  ```java
  // OrmFingerprintStore:
  String entityId = indexId + "_" + DigestHelper.sha256Hex(
          canonicalPath.getBytes(StandardCharsets.UTF_8)).substring(0, 16);

  // CodeIndexService:
  private String generateFileId(String indexId, String filePath) {
      return DigestHelper.sha256Hex((indexId + ":" + filePath).getBytes(StandardCharsets.UTF_8)).substring(0, 36);
  }
  ```
- **严重程度**: P1
- **现状**: 两种完全不同的 ID 生成策略：
  1. `CodeIndexService.generateFileId` = `SHA-256(indexId + ":" + filePath)[0:36]` — 主索引路径
  2. `OrmFingerprintStore` = `indexId + "_" + SHA-256(filePath)[0:16]` — 增量指纹路径

  如果增量索引在主索引之前运行（`OrmFingerprintStore.saveFingerprints` 在 `CodeIndexService.indexDirectory` 之前被调用），`findByIndexAndPath` 查询找不到已存在的文件实体，于是 `OrmFingerprintStore` 以自己的 ID 算法创建一个 `NopCodeFile` 实体。当 `CodeIndexService` 随后运行时，它以不同的 ID 创建同一文件的**另一个** `NopCodeFile` 实体。结果：同一 `indexId + filePath` 组合在数据库中有两条记录，违反唯一约束（如果有的话）或产生孤立记录。

  主索引 → 增量索引的顺序不会触发此问题（`findByIndexAndPath` 找到已有实体后复用）。问题仅在增量索引先于主索引时出现。
- **风险**: (a) 同一文件两条 `NopCodeFile` 记录导致数据不一致；(b) 删除索引时 `OrmFingerprintStore` 创建的孤儿记录可能残留（因为 `deleteIndex` 只删除与 `CodeIndexService` ID 关联的子实体）。
- **建议**: 统一使用 `CodeIndexService.generateFileId` 算法，或提取为共享工具方法。
- **信心水平**: 很可能（取决于运行顺序；如果增量索引始终在主索引之后运行则不触发）
- **发现来源视角**: 代码考古学家

---

### [AR-117] FlowDetector.detectFlows 非原子双缓存写入——symbolFilePathCache 和 flowCache 可能不一致

- **文件**: `nop-code/nop-code-flow/.../FlowDetector.java:96-97, 133-134`
- **证据片段**:
  ```java
  public List<ExecutionFlow> detectFlows(String indexId, ...) {
      Map<String, String> filePathMap = new HashMap<>();
      // ... build filePathMap ...
      symbolFilePathCache.put(indexId, filePathMap);   // ← 第 1 个 put

      // ... 40+ 行计算逻辑 ...

      flowCache.put(indexId, flows);                     // ← 第 2 个 put（非原子）
  }
  ```
- **严重程度**: P2
- **现状**: `symbolFilePathCache` 在 line 96 填充，`flowCache` 在 line 133 填充。两个 put 操作之间有约 37 行代码（包括入口点评分、BFS 遍历等），期间另一个线程可以调用 `getAffectedFlows(indexId)`。此时 `symbolFilePathCache` 有数据但 `flowCache` 无数据（返回空列表），导致 `isFlowAffected` 的路径匹配逻辑基于空 flow 列表返回 false，所有变更都被判定为不影响任何 flow。
- **风险**: 并发场景下增量索引后立即查询受影响 flow，结果可能为空。
- **建议**: 将两个 put 操作移到同一代码块，或使用 `ConcurrentHashMap.putAll` 的原子替代（如先构建完整结果再一次性写入两个缓存）。
- **信心水平**: 很可能
- **发现来源视角**: 并发审计者

---

### [AR-118] GraphSnapshot.EdgeKey.equals() 未防御 null source/target——HashSet 操作可能 NPE

- **文件**: `nop-code/nop-code-graph/.../diff/GraphSnapshot.java:52-58`
- **证据片段**:
  ```java
  @Override
  public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      EdgeKey edgeKey = (EdgeKey) o;
      return source.equals(edgeKey.source) && target.equals(edgeKey.target);
      // ← 如果 source 或 target 为 null → NullPointerException
  }
  ```
- **严重程度**: P2
- **现状**: `EdgeKey` 的构造函数不验证 `source`/`target` 非空。如果任何调用方创建了 source 或 target 为 null 的 `EdgeKey`，`HashSet.contains()`、`HashSet.removeAll()` 等 `GraphDiffer` 的核心操作将抛出 NPE，导致整个 diff 比较崩溃。

  当前调用链（`GraphDiffer.buildSnapshot` → `callGraph.getCallees(id)` → 创建 EdgeKey）中，如果 `callGraph` 的 node ID 是有效字符串，EdgeKey 的 source/target 不应为 null。但这是一个脆弱的隐式假设。
- **风险**: 如果图数据中引入了 null node ID（如因 CallGraph 并发问题 AR-103），`GraphDiffer` 会 NPE 崩溃而非报告错误。
- **建议**: 在 `EdgeKey` 构造函数中添加 `Objects.requireNonNull` 或在 `equals` 中使用 `Objects.equals`。
- **信心水平**: 很可能（取决于 null 是否实际可达）
- **发现来源视角**: 图算法 DoS 攻击者

---

### [AR-119] KnowledgeGapAnalyzer.computeCohesion 未防御 getCallees 返回 null——与 CommunityDetector 不一致

- **文件**: `nop-code/nop-code-graph/.../knowledge/KnowledgeGapAnalyzer.java:74-75`
- **证据片段**:
  ```java
  List<String> callees = callGraph.getCallees(node);
  for (String callee : callees) {   // ← NPE if callees is null
  ```
- **严重程度**: P2
- **现状**: `CommunityDetector.calculateCohesion` (line 775) 有 `if (callees != null)` 防护，但 `KnowledgeGapAnalyzer.computeCohesion` (line 74) 直接 for-each 遍历，无 null 检查。`CallGraph.getCallees` 在节点无出边时返回 null（而非空列表），因为内部存储是 `Map<String, List<String>>` + `computeIfAbsent` 模式，但 `get` 对不存在的 key 返回 null。

  孤立节点（无调用关系）是 `KnowledgeGapAnalyzer` 的正常输入场景。
- **风险**: 对任何孤立节点调用 `computeCohesion` 时 NPE 崩溃。
- **建议**: 添加 `if (callees == null) continue;` 或修改 `CallGraph.getCallees` 返回空列表。
- **信心水平**: 确定
- **发现来源视角**: 图算法 DoS 攻击者

---

### [AR-120] ChangeAnalyzer.parseGitDiff 合并 stderr 到 stdout——git 错误被静默忽略

- **文件**: `nop-code/nop-code-flow/.../ChangeAnalyzer.java:130-131`
- **证据片段**:
  ```java
  pb.redirectErrorStream(true);
  Process process = pb.start();
  ```
- **严重程度**: P2
- **现状**: `redirectErrorStream(true)` 将 git 的 stderr 合并到 stdout。diff 解析器使用正则匹配 `^diff --git`、`^---`、`^\+\+\+` 等模式。如果 git 执行失败（如无效 ref、仓库不存在、权限错误），错误信息（以 `fatal:` 或 `error:` 开头）不匹配任何解析模式，被静默忽略。最终结果是一个空的变更集，与"无变更"无法区分。

  调用方（`NopCodeIndexBizModel.analyzeChanges`）将空变更集作为正常结果返回给 GraphQL 客户端。
- **风险**: 用户看到"无变更"但实际是 git 命令失败。在 CI/CD 集成中可能导致增量索引跳过所有文件变更。
- **建议**: 分离 stdout/stderr，检查 stderr 内容或进程退出码（`process.waitFor()`），非零退出码时抛出异常。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-121] CommunityDetector.runLabelPropagationAlgorithm 接收 null 的 nodeIndexMap 参数——死代码 + 潜在 NPE

- **文件**: `nop-code/nop-code-graph/.../community/CommunityDetector.java:624-626`
- **证据片段**:
  ```java
  private static CommunityDetectionResult runLabelPropagationAlgorithm(
          Map<String, Integer> nodeIndexMap,   // ← 从未被使用
          List<String> indexNodeMap,
          ...
  ) {
  ```
- **严重程度**: P2
- **现状**: `nodeIndexMap` 参数在方法体内从未被引用。在 `runLeidenAlgorithm` 的 fallback 路径中（line 604, 617），传入的 `nodeIndexMap` 参数为 `null`。如果未来代码添加了对 `nodeIndexMap` 的使用，将立即 NPE 崩溃。
- **风险**: (a) 接口误导维护者以为 nodeIndexMap 被使用；(b) 如果添加使用则立即崩溃。
- **建议**: 移除 `nodeIndexMap` 参数，或传入实际值（从 Leiden fallback 上下文构建）。
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫

---

### [AR-122] ChangeAnalyzer.computeFlowParticipation 和 computeTestCoverageGap 始终返回常量——评分维度无区分度

- **文件**: `nop-code/nop-code-flow/.../ChangeAnalyzer.java:320-326, 348-360`
- **证据片段**:
  ```java
  // computeFlowParticipation:
  double score = (!callees.isEmpty() || !callers.isEmpty()) ? 0.15 : 0.0;
  return Math.min(score, CAP_FLOW_PARTICIPATION);  // CAP=0.25, score 永远是 0.15 或 0.0

  // computeTestCoverageGap:
  if (lower.contains("test") || lower.contains("spec")) {
      return MIN_TEST_COVERAGE_GAP;  // 0.05
  }
  return DEFAULT_TEST_COVERAGE_GAP;  // 0.30 — 所有非测试符号相同
  ```
- **严重程度**: P3
- **现状**: `computeFlowParticipation` 只区分"有边"和"无边"两种情况，所有有边的符号得到相同分数 0.15。`computeTestCoverageGap` 只区分"名称含 test/spec"和"其他"，所有非测试符号得到相同分数 0.30。两个权重合计占总风险评分的 25%（`WEIGHT_TEST_GAP=0.15` + `WEIGHT_DEPTH=0.10`），但这些维度无法区分不同符号的风险高低。
- **风险**: 变更影响评估对绝大多数符号给出相同的风险分数，降低了该功能对用户的价值。
- **建议**: 如果当前无法实现真正的 flow participation / test coverage 计算，应标注为 TODO 并在 API 文档中说明这些维度当前为占位符。
- **信心水平**: 确定
- **发现来源视角**: 代码考古学家

---

### [AR-123] CriticalNodeAnalyzer.computeHubNodes/computeBridgeNodes 使用 BetweennessCentrality 无超时——大图可长时间阻塞

- **文件**: `nop-code/nop-code-graph/.../critical/CriticalNodeAnalyzer.java:87-88`
- **证据片段**:
  ```java
  BetweennessCentrality<String, DefaultEdge> bc = new BetweennessCentrality<>(graph, false);
  Map<String, Double> scores = bc.getScores();
  ```
- **严重程度**: P2
- **现状**: `BetweennessCentrality` 的时间复杂度为 O(V×E)。在 CommunityDetector 中，类似的高复杂度算法有 `runWithTimeout` 超时保护（虽然有线程泄漏问题 AR-102），但 `CriticalNodeAnalyzer` 无任何超时或大小检查机制。对 10 万节点 + 50 万边的调用图，计算可能需要数小时。
- **风险**: 对大型索引调用关键节点分析时，请求可能长时间阻塞，前端超时后端仍在计算。
- **建议**: 添加图大小检查（如 `if (allNodes.size() > 10000)` 则拒绝或采样），或复用 CommunityDetector 的超时模式（并修复 AR-102）。
- **信心水平**: 确定
- **发现来源视角**: 图算法 DoS 攻击者

---

## 深挖第 3 轮追加 — 总评

本轮从搜索/查询功能和语义边提取器两个全新视角切入，发现了此前 8 轮审查从未覆盖的领域：

1. **CodeSearchService 的 glob 匹配完全失效**（AR-112）——这是最意外的新发现。`Pattern.quote` + `.replace("\\*", ".*")` 的组合看起来合理但实际上完全无效。这种"看起来正确但实际错误"的模式是典型的灯下黑问题。4 个搜索路径全部受影响，意味着所有带文件过滤的搜索请求实质上无过滤。

2. **语义边提取器的性能炸弹**（AR-113、AR-114）——`AnnotationPatternExtractor` 无上限的 O(N²) 配对与 `DocKeywordExtractor`/`NameSimilarityExtractor` 的冗余内循环计算叠加，使得语义边提取可能成为整个索引管线的性能瓶颈。对使用 Spring 的中型项目（`@Autowired` 标注 2000+ 字段），`AnnotationPatternExtractor` 可能生成 200 万条无用边。

3. **文件查询路径的数据丢失**（AR-115）——`entityToFileResult` 遗漏了 `imports` 字段的反序列化，这是一个简单的遗漏但影响面广：所有文件查询 API 的 imports 字段永远为空。

4. **ID 生成不一致**（AR-116）——两个独立的 ID 生成算法可能在特定运行顺序下创建重复实体，虽然触发条件有限，但与 AR-94（零事务）叠加后风险放大。

**本次审查最值得关注的 3 个方向**：

1. **glob 匹配失效**（AR-112）——影响所有文件过滤搜索，修复简单但影响面大。
2. **AnnotationPatternExtractor O(N²) 无上限**（AR-113）——可能导致索引管线 OOM。
3. **imports 字段丢失**（AR-115）——API 契约违约，前端功能失效。

## 本次审查的盲区自评

1. **xmeta 字段定义与 BizModel 返回值对齐**：发现了内部类型泄漏的线索但未深入验证每个 xmeta 文件是否正确定义了 GraphQL schema。
2. **view.xml / 前端页面**：未审查 nop-code-web 模块的前端配置。
3. **Beans.xml IoC 注入正确性**：验证了 BizModel 注入但未检查 CodeCacheManager/CodeQueryService 等服务 bean 的所有注入路径。
4. **CodeIndexService.saveFileResultInSession 深度**：1904 行的上帝类中仍有大量未审查的代码路径（如 `deleteFileRecords`、`persistInSession` 的完整逻辑）。
5. **端到端运行验证**：所有发现基于静态代码分析，未实际运行测试验证（如 AR-119 的 NPE 是否能被测试触发）。

## 按严重程度分布表（含第 3 轮追加）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | 全模块零事务（AR-94） |
| P1      | 10   | +glob 匹配失效（AR-112）、注解 O(N²) 爆炸（AR-113）、imports 字段丢失（AR-115）、ID 生成不一致（AR-116） |
| P2      | 13   | +语义边冗余计算（AR-114）、双缓存竞态（AR-117）、EdgeKey NPE（AR-118）、KnowledgeGap NPE（AR-119）、git 错误静默（AR-120）、死代码参数（AR-121）、BC 无超时（AR-123） |
| P3      | 3    | +评分维度无区分度（AR-122） |
