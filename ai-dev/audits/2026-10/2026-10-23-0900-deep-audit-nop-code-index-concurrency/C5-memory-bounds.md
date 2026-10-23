# C5 维度初审：内存有界性与大数据量（nop-code 索引单元）

- **审计单元**：nop-code-core（51f）+ nop-code-flow（12f）+ nop-code-service（53f），全单元 116f / 16,898 行
- **维度**：C5（内存有界性 / 无界物化）
- **批次**：全单元单批（一次覆盖，无分批）
- **盲审声明**：本审计为盲审。审计过程中未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何既有文件；对 CL-002 线索（LlmEdgeCache）仅依据任务描述中的独立提及自行取证，未接触前审结论。
- **方法论**：`ai-dev/skills/deep-audit-prompts.md` 共享提示词前缀（L298-374）+ `ai-dev/skills/concurrency-resource-audit-prompt.md` C5 正文（L221-247）
- **基线**：`_tmp/cra-baseline-nop-code-index.txt`（2026-10-23T01:37:54Z @ 7b0ccc89d4），仅用于圈定长驻站点清单（CodeCacheManager / GraphMetricMaterializer / CodeGraphService 等 15 站点），未作计数结论
- **CL 前缀**：维度集外线索自 CL-101 起编号
- **初审性质**：本文件全部条目为初审线索，非最终事实；每条须经独立复核后才可立案收敛。

---

## 一、无界风险清单（路径 × 增长源 × 声明上界 × 判定）

判定口径：有界 = 存在可指认的上界机制（常量上限/LRU/TTL/截断标志/分页批次）；比例有界 = 无固定上限但占用与索引数据规模成线性比例（瞬时、随请求释放）；无界 = 找不到任何上界机制或上界机制未生效。

| # | 路径 | 增长源 | 声明上界 / 机制 | 判定 | 关联发现 |
|---|------|--------|----------------|------|---------|
| 1 | `CodeCacheManager.analysisCacheMap`（service/impl/CodeCacheManager.java:71） | indexId 基数 | MAX_CACHE_ENTRIES=20，access-order LinkedHashMap LRU + TTL 1h（L42-43,96-111） | 有界 | — |
| 2 | `CodeCacheManager.rebuildSymbolTable`（:192-221） | nop_code_symbol 行数 | MAX_CACHE_SYMBOLS=100000 分页加载 + `setTruncated(true)` 降级 | 有界（截断降级） | — |
| 3 | `CodeCacheManager.rebuildCallGraph`（:223-252） | nop_code_call 行数 | MAX_CACHE_EDGES=500000 分页加载 + truncation | 有界（截断降级） | — |
| 4 | `CodeCacheManager.rebuildDependencies`（:254-270） | nop_code_dependency 行数 | **无任何上限**（同文件 symbol/edge 均有 MAX_* 守卫，此处缺失） | **无界** | C5-004 |
| 5 | `LlmEdgeCache.cache`（service/semantic/LlmEdgeCache.java:14） | 去重 prompt 键基数（源自符号对组合） | **无上限、无 TTL、无淘汰**，进程生命周期 | **无界**（当前无生产装配点，仅测试引用） | C5-007 |
| 6 | `FlowDetector.flowCache` / `symbolFilePathCache`（flow/FlowDetector.java:60-61） | indexId 基数 | MAX_CACHE_ENTRIES=20 evictOverflow（:506-512，任意键淘汰非 LRU） | 有界 | — |
| 7 | `FlowDetector.detectFlows` 单索引 flows 内容（:125-146） | 入口点数 × 每入口 BFS 闭包规模 | 无（Σ|pathNodeIds| ≈ 入口数 × 可达集，密集图下二次放大，随 `listFlows`/`detectFlows` 全量返回） | 比例有界（最坏二次放大，建议关注） | — |
| 8 | `CodeRelationGraphLoader.load`（service/graph/CodeRelationGraphLoader.java:48-57,180-197） | 4 张关系表全行（calls/inheritance/annotation/semantic） | **无界**——Javadoc L27-29 自述 "Deliberately unbounded"，中间产物 List<CodeEdgeData> + CodeRelationGraph 双份驻留（~3x 行放大） | **无界**（每请求全量物化，无并发叠加护栏） | C5-001 |
| 9 | `GraphMetricStore.findByMetric`（service/graph/GraphMetricStore.java:158-165） | 单 metric family 行数（≈调用图节点数） | **无 LIMIT**（同文件 hasMaterialized 有 setLimit(1)，形成对照） | 比例有界（瞬时）——但与 #8 同栈叠加 | C5-001 |
| 10 | `GraphMetricMaterializer.materialize` rows 列表（service/impl/GraphMetricMaterializer.java:96-105） | ≈3N metric 行（COMMUNITY/PAGE_RANK/HUB 各每节点一行）+ ENTRY_POINT 每 method/constructor 一行 | 无行数上限；BETWEENNESS 有 BETWEENNESS_MAX_NODES=10000 跳过守卫（:62,185-189）；Leiden 仅 `<2` 下限守卫、无上限守卫 | 比例有界（构建期一次） | C5-005 |
| 11 | `GraphMetricMaterializer.replaceRows` 插入侧 session（:290-292） | rows 全量 ORM 实体驻留 session | 删除侧有 flush+clear（:285-286），**插入侧无 flush/evict** | 无界（session 级，随 rows 增长） | C5-005 |
| 12 | `ProjectAnalyzer.analyzeProject` fileResults（core/analyzer/ProjectAnalyzer.java:129,136-144,177-186） | 仓库文件数 × 每文件分析结果（**含全文 sourceCode**，JavaFileAnalyzer.java:148 写入；指纹构建读取，ProjectAnalyzer.java:635-638） | MAX_FILE_SIZE_BYTES / MAX_FILE_COUNT 常量声明于 :54-55 但**全文件零引用（死守卫）**；BatchQueue 消费端 `fileResults.add(result)` 全量累积，"避免一次性加载"的 Javadoc（:121-122）与实现不符 | **无界**（输入面仓库大小不可控） | C5-002 |
| 13 | `ProjectAnalyzer.analyzeProject(resourceLoader, vfsPath, filePattern, batchCallback)` "streaming" 变体（:204-285） | 同上 | 同上（allFileResults 全量累积，L206 日志自称 streaming） | **无界** | C5-002 |
| 14 | `NameSimilarityExtractor.extract` edges（service/graph/NameSimilarityExtractor.java:41-84） | 5000 符号两两对的阈值通过数 | 输入侧 MAX_SYMBOLS=5000（:26,36-38）；**输出侧无 MAX_EDGES 上限**（12.5M 对上限） | 无界（输出侧） | C5-003 |
| 15 | `DocKeywordExtractor.extract` edges（service/graph/DocKeywordExtractor.java:41-97） | 同上 | 输入侧 MAX_SYMBOLS=5000（:31,52-56）；输出侧无上限 | 无界（输出侧） | C5-003 |
| 16 | `AnnotationPatternExtractor.extractFromFileResults`（service/graph/AnnotationPatternExtractor.java:90-110） | 注释符号对 | MAX_EDGES=50000 + MAX_SYMBOLS_PER_ANNOTATION=100（:28-29,86-92），达上限即 return | 有界（对照 #14/#15 的缺失惯例） | — |
| 17 | `CodeIndexService.persistInSession` 语义边保存循环（service/impl/CodeIndexService.java:1278-1300） | result.getSemanticEdges() 全量实体 | 文件/符号侧有 BatchQueue(500)+flush+evictAll（:1262-1268）；**语义边与启发式边循环（:1306-1357）无 flush/evict** | 无界（session 级） | C5-003 |
| 18 | `CodeIndexService.persistSingleFileInSession` mergedTable（:1584-1592） | 每个增量变更文件复制全量全局符号表 | 无（O(变更文件数 × 全局符号数) 瞬时分配） | 比例有界（最坏二次放大） | C5-006 |
| 19 | `CodeQueryService.findSymbols` / `getSymbolUsages` limit 分支（service/impl/CodeQueryService.java:486-489,542-545） | indexId 全表 | `if (limit > 0) qb.setLimit(limit)`——limit<=0 时**无兜底上限**；同文件 findReferences 有 `limit > 0 ? limit : MAX_QUERY_RESULTS` 兜底（:754），biz 层当前恒传正值（NopCodeSymbolBizModel.java:96,117） | 条件无界（内部 API 面可触达） | C5-008 |
| 20 | `CodeQueryService.findReferences`（:754-755） | usage 行数 | usageCap 默认 MAX_QUERY_RESULTS=10000 + warnIfCapped | 有界 | — |
| 21 | `CodeSearchService` 全部查询（impl/CodeSearchService.java:97,151,174,199,229,363） | 搜索命中 | limit 默认 50；批量 BATCH_QUERY_LIMIT=1000；候选 limit*2 / limit*3 | 有界 | — |
| 22 | `CodeGraphService.getDepGraph`（impl/CodeGraphService.java:1139-1175） | 全部 dependency 行 → DepEdgeDTO | **无 limit 参数、无截断**，随 GraphQL 响应全量物化 | 无界（响应面） | C5-009 |
| 23 | `CodeGraphService.detectCommunities` in-memory 兜底（:99-122 → runCommunityDetection :749-759） | 调用图全节点集 | **无 MAX_NODES_FOR_COMMUNITY_DETECTION 守卫**；同文件 getCriticalNodes（:446）与 diffGraph（:506）有 10000 节点守卫——守卫不一致 | 比例有界（守卫不一致） | — |
| 24 | `ShardBuildCoordinator.loadLedger` / `beginShardBuild`（impl/ShardBuildCoordinator.java:46-72,139-143） | shardIds 列表长度（调用方输入） | 无上限；ledger 行 = shardIds.size() 单 session 保存无 flush；未在 biz 层暴露（仅内部 API） | 条件无界（内部面，比例于调用方输入） | — |
| 25 | `CodeIndexService.indexLocks` / `rebuildDebounceMap` / `incrementalAffectedFilesMap`（:123,159-164） | indexId 基数 | 锁/Long 按索引基数累积（量级极小）；affectedFiles 列表按索引替换、deleteIndex 时移除（:911,1103） | 有界（按索引基数） | — |
| 26 | `InMemoryFingerprintStore.store`（core/incremental/InMemoryFingerprintStore.java:10） | indexId × 文件数 | 类内无上限；但生产装配为 OrmFingerprintStore（CodeIndexService.java:249-251），本类仅测试引用 | 无影响（未装配） | — |
| 27 | `ClusterWorkspaceManager.gitOutput/gitWithCredential` readAllBytes（service/cluster/ClusterWorkspaceManager.java:146,188） | git 进程 stdout+stderr 合并流 | 无上限读取；git 输出实际为行级诊断，量小——但读取先于 waitFor，静默挂起时 60s 超时永不生效 | 低风险（内存）→ 挂死维度线索 | CL-101 |
| 28 | `IncrementalDetector` / `ChangeAnalyzer` diff 解析（flow/ChangeAnalyzer.java:155-238） | 变更文件/行区间 | BufferedReader 流式解析 + truncated 截断标志（:238） | 有界（好实践，对照 #12） | — |
| 29 | `SymbolTable` / `CallGraph` / `CodeRelationGraph`（core/graph/*） | 索引符号/边数 | 与缓存条目同界（#2/#3 的 MAX_CACHE_* 间接封顶；分析期与索引规模成比例） | 比例有界 | — |
| 30 | `CodeCacheManager` 峰值叠加验算 | 20 entries × (100k symbols + 500k edges + 无界 deps) | 单条上限存在（#2/#3），全局峰值 = 20 × 单条上界 ≈ 数百 MB～GB 级（堆预算需部署侧确认）；deps 项见 C5-004 | 有界但峰值高（叠加 #4 后无界） | C5-004 |

---

## 二、发现立案

### C5-001 公开图查询路径每请求全量物化 4 表边集，无上限且无并发叠加护栏

- **文件**：`nop-code/nop-code-service/src/main/java/io/nop/code/service/graph/CodeRelationGraphLoader.java`；消费方 `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java`；数据源 `nop-code/nop-code-service/src/main/java/io/nop/code/service/graph/GraphMetricStore.java`
- **行号**：CodeRelationGraphLoader.java L26-57、L180-197；CodeGraphService.java L308-325（getSurprisingConnections→L321）、L339-367（getExplorationQuestions→L351）、L370-387（exportGraphWiki→L383）；GraphMetricStore.java L158-165
- **证据**：

```java
// CodeRelationGraphLoader.java L26-29
 * Loads all four relation tables (calls / inheritance / annotation usages / semantic edges)
 * in batches and maps them to {@link CodeEdgeData}. Deliberately unbounded: global scoring
 * (surprise connections, question generation) needs the complete edge set of an index; memory
 * guards are the domain of result materialization and the invariant-loop gates.
```

```java
// CodeGraphService.java L321（三个 biz 动作同型，均为请求路径）
CodeRelationGraph graph = CodeRelationGraphLoader.load(indexId, daoProvider, filePathResolver);
Map<String, Integer> communities = metricStore.loadCommunities(indexId);
```

```java
// GraphMetricStore.java L158-165（无 setLimit，对照同文件 L48 的 setLimit(1)）
private List<NopCodeGraphMetric> findByMetric(String indexId, String metricType) {
    IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
    QueryBean query = new QueryBean();
    query.addFilter(FilterBeans.and(
            FilterBeans.eq("indexId", indexId),
            FilterBeans.eq("metricType", metricType)));
    return dao.findPageByQuery(query);
}
```

- **严重程度**：P1
- **现状**：`getSurprisingConnections` / `getExplorationQuestions` / `exportGraphWiki` 均为 `NopCodeIndexBizModel` 暴露的 biz 动作（NopCodeIndexBizModel.java L161/171/180），每次请求调用 `CodeRelationGraphLoader.load` 把 4 张关系表全行加载为 `List<CodeEdgeData>`，再由 `CodeRelationGraph` 构造器二次物化为 out/in 邻接表 + 每边 LinkedHashMap attrs（放大 ≈3 倍），构造期间两份并存；同栈还叠加 `metricStore.loadCommunities`（全 COMMUNITY 行无 LIMIT）与 `getOrRebuildSymbolTable`。输出侧 topN/maxCommunities 有界，但中间产物无任何上限。
- **风险**：单请求占用 ≈ 4 表全行 × ~3 倍放大；按本模块自身封顶（MAX_CACHE_EDGES=500k calls）推算大索引下单请求可达数百 MB，N 个并发请求线性叠加即可 OOM。Javadoc 声称 "memory guards are the domain of result materialization and the invariant-loop gates"，但这三个服务路径上不存在任何可指认的中间产物守卫——注释声称的防线缺失本身即契约漂移，会误导后续开发者在此路径上继续叠加无守卫消费。
- **建议**：为 loader 增加行数/边数上限（对齐 AnnotationPatternExtractor 的 MAX_EDGES 惯例）+ 截断降级标志；或对三个图查询动作加信号量/单飞（per-index in-flight 合并）限制并发物化；`GraphMetricStore.findByMetric` 补充分页或显式上限常量；修正 Javadoc 使守卫描述与实况一致。
- **信心水平**：高（调用链、biz 暴露面、无守卫事实均已逐一取证）
- **误报排除**：为什么不是"框架约定大于配置"或"设计如此"：Javadoc 自述 deliberately unbounded 并把守卫责任指给别处，而别处（biz 层/服务层）并无该守卫——这是"声明防线与实况不符"，不是"有意的无界设计"；亦非生成产物问题（全部手写代码）。
- **复核状态**：已复核（2026-10-23 独立复核，判定：保留 P1——证据链三环逐环重演属实，见文末「维度复核结论」）

### C5-002 ProjectAnalyzer 输入面守卫全部失效：死常量 + 伪流式 + 全仓库源码驻留

- **文件**：`nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java`
- **行号**：L53-55（死常量）、L121-144（Javadoc 声称避免全量加载 vs 消费端全量累积）、L163-187（readText + 累积）、L204-285（"streaming" 变体同型）；关联 `CodeFileAnalysisResult.sourceCode`（core/model/CodeFileAnalysisResult.java L14,35-39）与 `JavaFileAnalyzer.java` L148（写入点，单元外佐证）
- **证据**：

```java
// ProjectAnalyzer.java L53-55：两个 MAX_* 守卫常量声明后全文件零引用
private static final int DEFAULT_BATCH_SIZE = 1000;
private static final long MAX_FILE_SIZE_BYTES = 1024 * 1024;
private static final int MAX_FILE_COUNT = 50000;
```

```java
// L121-122 Javadoc vs L134-144 实现：BatchQueue 消费端仍全量累积
 * 基于 VFS 资源加载器的项目分析。使用 depthIterator 惰性遍历文件系统，
 * 用 BatchQueue 缓冲分析结果，避免一次性加载所有文件到内存。
...
BatchQueue<CodeFileAnalysisResult> batchQueue = new BatchQueue<>(batchSize, batch -> {
    for (CodeFileAnalysisResult result : batch) {
        fileResults.add(result);          // ← "缓冲"去向是只增不减的全量列表
```

```java
// L177：每文件全文读入（无 MAX_FILE_SIZE_BYTES 检查）
String sourceCode = resource.readText();
CodeFileAnalysisResult result = fileAnalyzer.analyze(relativePath, sourceCode);
```

- **严重程度**：P1
- **现状**：`indexDirectory`（CodeIndexService.java L354）走该路径后，`fileResults` 持有每个文件的完整 `sourceCode` 字符串（JavaFileAnalyzer 写入、L635-638 指纹构建读取，生命周期贯穿整个巨事务直至 persist 完成），叠加全局 SymbolTable、calls、semanticEdges。单文件大小与文件总数均无强制上限：`MAX_FILE_SIZE_BYTES`（1MB）与 `MAX_FILE_COUNT`（50000）是声明了却从未使用的死常量。带 `batchCallback` 的变体（L204-285）日志自称 "streaming project analysis"，实现与第一个变体完全同型。
- **风险**：输入面（仓库）大小不可控——单个超大文件（如误提交的构建产物、minified bundle）或超大规模仓库直接全量进堆；死常量使"声明数据规模包络"形同虚设，且误导后续开发者以为存在文件级护栏（共享前缀第 11 条口径：声明值与实现脱节属契约漂移）。
- **建议**：让两个常量生效（readText 前检查 `resource.length()`，遍历中检查 fileCount 并以 NopException fail-fast 或截断+truncated 标志降级）；`analyzeProject` 完成指纹/落库后显式清空 `sourceCode`（`result.setSourceCode(null)`）以缩短驻留；修正 L121-122 与 L206 的"流式/避免全量加载"表述，或将 BatchQueue 真正改为滑动窗口（若下游确需全量则删除该缓冲并如实注释）。
- **信心水平**：高（常量零引用已用 grep 全单元复核：仅 L54-55 两处出现）
- **误报排除**：为什么不是"巨事务是有意设计"（L1301-1303 注释确实自认巨事务）：本发现不针对"单事务写十万行"这一已自认的持久化形态，而针对 (a) 声明守卫死代码、(b) 注释与实现相反、(c) sourceCode 全程驻留三点可量化的输入面无界；三者均不因"巨事务是既定取舍"而消解。
- **复核状态**：已复核（2026-10-23 独立复核，判定：保留 P1，附部署包络边界注，见文末「维度复核结论」）

### C5-003 语义提取器输出无上限，且语义边/启发式边在巨事务 session 内无 flush 物化

- **文件**：`nop-code/nop-code-service/src/main/java/io/nop/code/service/graph/NameSimilarityExtractor.java`、`DocKeywordExtractor.java`、对照 `AnnotationPatternExtractor.java`；落库点 `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**：NameSimilarityExtractor.java L41-84（无输出上限）；DocKeywordExtractor.java L41-97（同）；AnnotationPatternExtractor.java L28-29、L86-92（MAX_EDGES 对照惯例）；CodeIndexService.java L1278-1300（语义边 session.save 循环）、L1306-1357（启发式边 session.save 循环）
- **证据**：

```java
// NameSimilarityExtractor.java L44-78：5000 输入上限，输出 edges 无任何计数上限
for (int i = 0; i < symbols.size(); i++) {
    ...
    for (int j = i + 1; j < symbols.size(); j++) {
        ...
        if (similarity >= SIMILARITY_THRESHOLD) {
            seen.add(edgeKey);
            ...
            edges.add(edge);              // ← 无 MAX_EDGES 守卫
```

```java
// AnnotationPatternExtractor.java L90-92：同族提取器已有输出上限（对照）
for (int i = 0; i < symbolIds.size(); i++) {
    for (int j = i + 1; j < symbolIds.size(); j++) {
        if (edges.size() >= MAX_EDGES) return edges;
```

```java
// CodeIndexService.java L1278-1300：巨事务内逐条 save，无 flush/evict
// Persist semantic edges
if (result.getSemanticEdges() != null && !result.getSemanticEdges().isEmpty()) {
    for (CodeSemanticEdge edge : result.getSemanticEdges()) {
        ...
        session.save(edgeEntity);         // ← 对比 L1262-1268 文件/符号侧 BatchQueue(500)+flush+evictAll
```

- **严重程度**：P2
- **现状**：两个 O(N²) 提取器只封了输入侧（MAX_SYMBOLS=5000），输出侧 edges 列表无上限——重名/近名密集的仓库（重复模式生成代码、同名方法）可产生远超必要规模的边（理论上限 12.5M 对）。这些边连同启发式边在 `indexDirectory` 的单巨事务内逐条 `session.save`，无 flush/evict 分批，全部实体驻留 ORM session 持久化上下文；而同方法内文件/符号保存路径已有 BatchQueue(500)+flush+evictAll 的正确范式（L1262-1268），`resolveQualifiedNamesToIds`（L1417-1460）亦逐批 flush+evictAll。
- **风险**：session 级实体累积放大堆占用并拉长巨事务；边数失控同时放大 DB 行数与后续 C5-001 读路径的全量物化规模。模块自身在 AnnotationPatternExtractor 已确立"输出侧也要封顶"的惯例，两个同族类缺失属守卫不对称。
- **建议**：为两个提取器补 `MAX_EDGES`（对齐 50000 惯例）并在达到上限时截断 + warn；语义边/启发式边保存循环改用与文件/符号侧一致的 BatchQueue + flush + evictAll 分批。
- **信心水平**：高（三文件对照关系与两条保存循环均已逐行核对）
- **误报排除**：为什么不是"阈值 0.7 已足够抑制"：jaccard 相似度对同名/近名符号对恒为 1.0/高值，抑制失效是数据形态问题而非阈值保证；且输入上限（MAX_SYMBOLS）与输出上限（MAX_EDGES）在同模块并存的事实本身证明作者认为两者都需要。
- **复核状态**：已复核（2026-10-23 独立复核，判定：保留 P2，见文末「维度复核结论」）

### C5-004 CodeCacheManager.rebuildDependencies 无上限，与同文件 symbol/edge 守卫不对称

- **文件**：`nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeCacheManager.java`
- **行号**：L44-46（MAX_CACHE_* 常量组）、L173-186（缓存入口）、L254-270（无上限重建）
- **证据**：

```java
// L44-46：常量组里没有 dependencies 的上限
private static final int BATCH_SIZE = 5000;
private static final int MAX_CACHE_SYMBOLS = 100000;
private static final int MAX_CACHE_EDGES = 500000;
```

```java
// L254-270：循环翻页全量累积，无 MAX 检查、无 truncation 标志
private List<NopCodeDependency> rebuildDependencies(String indexId, IDaoProvider daoProvider) {
    IEntityDao<NopCodeDependency> dao = daoProvider.daoFor(NopCodeDependency.class);
    List<NopCodeDependency> result = new ArrayList<>();
    long offset = 0;
    while (true) {
        ...
        result.addAll(batch);
        if (batch.size() < BATCH_SIZE) break;
        offset += BATCH_SIZE;
    }
    return result;
}
```

- **严重程度**：P2
- **现状**：同一缓存条目（AnalysisCache）内 SymbolTable 封顶 10 万、CallGraph 封顶 50 万边，均带截断降级；dependencies 列表无对应 `MAX_CACHE_DEPENDENCIES`，行数不受限地驻留缓存（TTL 1h、LRU 20 条目）。消费方 `getDepGraph`（CodeGraphService.java L1139）与 `findDependentFiles` 直接全量展开。
- **风险**：依赖密集仓库下单条缓存条目占用无界；与 C5-001 的峰值叠加验算（清单 #30）组合后，"20 entries × 单条上界"的峰值推导失效——上界机制被 deps 一项击穿。
- **建议**：补 `MAX_CACHE_DEPENDENCIES` + 截断标志（对齐 #2/#3 模式）；或改存投影（sourceFilePath/targetFilePath/importStatement 精简结构）降低单行驻留成本。
- **信心水平**：高（全文件仅 3 个 MAX_* 常量，deps 路径无检查为直接事实）
- **误报排除**：为什么不是"依赖表天然比符号表小所以不需要"：依赖行数与文件数/imports 数成比例，与符号表同为输入规模函数；同文件作者已对更小量纲的量设上限（如 edges 50 万 vs symbols 10 万），缺失处更可能是遗漏而非容量论证。
- **复核状态**：已复核（2026-10-23 独立复核，判定：保留 P2，见文末「维度复核结论」）

### C5-005 GraphMetricMaterializer.replaceRows 插入侧无 flush/evict，全量 metric 实体驻留单 session

- **文件**：`nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/GraphMetricMaterializer.java`
- **行号**：L270-295（replaceRows：删除侧 :285-286 有 flush+clear，插入侧 :290-292 无）
- **证据**：

```java
// L274-292：同方法内删除/插入两侧的会话管理不对称
while (true) { ...                          // 删除侧
    for (NopCodeGraphMetric row : batch) { session.delete(row); }
    session.flush();
    session.clear();                        // ← 删除侧有 flush+clear
    ...
}
for (NopCodeGraphMetric row : rows) {
    dao.saveEntity(row);                    // ← 插入侧无 flush/evict，rows 全量驻留 session
}
```

- **严重程度**：P2
- **现状**：`materialize`（L96-105）一次性在内存构建 ≈3N+M 行（COMMUNITY/PAGE_RANK/HUB 每调用图节点一行 + ENTRY_POINT 每 method/constructor 一行 + BETWEENNESS ≤10k），`replaceRows` 在单事务单 session 内先分批删除（有 flush+clear）再把全部新行逐条 saveEntity，无任何 flush/evict。该方法在写路径（indexDirectory 失效后）与**读路径自愈**（CodeGraphService.ensureMaterializedForGraphMetrics L347-356、detectCommunities L112-121、getCriticalNodes L439-443）都会触发。
- **风险**：大图下 session 持久化上下文累积数十万实体；该物化在查询请求路径上被自愈触发，与 C5-001 同栈叠加请求并发。
- **建议**：插入侧按 DELETE_BATCH_SIZE 同规格分批 flush+evict（或复用 session.clear 语义），与删除侧对称。
- **信心水平**：高（同方法内两侧不对称为直接代码事实）
- **误报排除**：为什么不是"dao.saveEntity 自带批处理"：删除侧在同 session 上显式 flush/clear 的事实表明作者认定此处需要手工会话管理；若 saveEntity 足以控制驻留，删除侧的 flush+clear 即为冗余——两者不能同时成立。
- **复核状态**：已复核（2026-10-23 独立复核，判定：保留 P2，见文末「维度复核结论」）

### C5-006 增量持久化路径每文件全量复制全局符号表（O(变更文件数 × 全局符号数) 瞬时放大）

- **文件**：`nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**：L1564-1608（persistSingleFileInSession：L1584-1592 全表复制）、调用点 L1183-1193（BatchQueue 消费端逐文件调用）
- **证据**：

```java
// L1584-1592：每个变更文件都重建一份"全局符号表 + 本文件符号"的合并副本
SymbolTable mergedTable = new SymbolTable();
for (CodeSymbol sym : globalTable.getAll()) {
    mergedTable.add(sym);
}
for (CodeSymbol sym : fileSymbolTable.getAll()) {
    ...
    mergedTable.add(sym);
}
resolveQualifiedNamesToIds(indexId, mergedTable, session);
```

```java
// L1183-1193：增量批次内逐文件调用（BATCH_SIZE 分批，但批内每文件仍各复制一次全表）
BatchQueue<CodeFileAnalysisResult> batchQueue = new BatchQueue<>(BATCH_SIZE, batch -> {
    for (CodeFileAnalysisResult result : batch) {
        persistSingleFileInSession(indexId, result, session);
    }
```

- **严重程度**：P2
- **现状**：`triggerIncrementalIndex` 的持久化阶段对每个变更文件调用 `persistSingleFileInSession`，其中 mergedTable 复制整个缓存全局符号表（至多 10 万符号），只为给单文件的占位 qualified name 做解析；`SymbolTable.getAll()` 每次返回防御性快照（SymbolTable.java L43-47），复制成本无法复用。F 个变更文件 × S 个符号 = O(F×S) 次符号插入与哈希。
- **风险**：大批量增量（如分支合并后数千文件）时瞬时分配与 CPU 双重二次放大；GC 压力下拖长巨事务。
- **建议**：把 mergedTable 提升为批次级（BatchQueue 消费端每批构建一次，批内文件共享）或直接复用缓存内 globalTable（addToSymbolTableCache 已做合并）做只读解析。
- **信心水平**：中高（复制代码与调用链为直接事实；实际量纲取决于变更批规模，未见现成压测数据）
- **误报排除**：为什么不是"防御性快照是并发正确性要求所以成本必然"：WP-5 AR-155/158 注释要求的是**读取**隔离，不要求每次解析重建全表副本；批次级共享快照同样满足隔离语义。
- **复核状态**：已复核（2026-10-23 独立复核，判定：保留 P2，不采纳第 1 轮复核提示 2 的降 P3 预案，见文末「维度复核结论」）

### C5-007 LlmEdgeCache 进程生命周期无界缓存（当前未装配，休眠缺陷）

- **文件**：`nop-code/nop-code-service/src/main/java/io/nop/code/service/semantic/LlmEdgeCache.java`
- **行号**：L9-19（类主体与缓存字段）
- **证据**：

```java
// L9-19：键为 SHA256(prompt)，值为 LLM 响应全文；无上限、无 TTL、无淘汰
 * N7.1 LLM 结果缓存：SHA256(prompt) 为键，ConcurrentHashMap 存储。
 * v1 纯内存（进程生命周期），缓存持久化 deferred。
public class LlmEdgeCache {
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public String getOrCompute(String promptKey, Supplier<String> compute) {
        String key = sha256(promptKey);
        return cache.computeIfAbsent(key, k -> compute.get());
```

- **严重程度**：P3
- **现状**：缓存无任何上界机制，键空间由符号对组合驱动（LlmSemanticEdgeExtractor.buildPrompt 对每对候选符号生成不同 prompt）。全仓 grep 证实主代码仅本单元 semantic 包三个类互相引用，`CodeIndexService.discoverSemanticExtractors`（CodeIndexService.java L285-292）只注册三个非 LLM 提取器，无 beans.xml 装配——当前为休眠代码。
- **风险**：一旦该提取器按 N7.1 规划接入（discoverSemanticExtractors 加一行即可），缓存即变为进程生命周期单调增长：每键驻留完整 LLM 响应文本（含 rationale 长文本），键基数随索引内容增长，长驻进程无法回收。
- **建议**：接入前先加容量上限 + LRU/TTL（对齐 CodeCacheManager 惯例），或在类注释中显式标注"接入前必须补上界"的前置条件。
- **信心水平**：高（未装配结论经全仓 main-source grep 复核，仅测试目录引用）
- **误报排除**：为什么不是"未使用代码不算发现"：类位于 main source set 且为 public，是 N7.1 明确规划的接入面（类注释自述 v1/deferred 演进意图）；按共享前缀"以 live code 为准"，当前判 P3 而非更高，正是对"未装配"事实的如实降级。
- **复核状态**：已复核（2026-10-23 独立复核，判定：保留 P3，见文末「维度复核结论」）

### C5-008 CodeQueryService.findSymbols / getSymbolUsages 的 limit<=0 分支无兜底上限

- **文件**：`nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java`
- **行号**：L468-489（findSymbols，:486 为问题分支）、L537-546（getSymbolUsages，:542 为问题分支）；对照 L754（findReferences 的兜底惯例）
- **证据**：

```java
// L486-489：limit<=0 时查询无上限
if (limit > 0) qb.setLimit(limit);
return symbolDao.findAllByQuery(qb).stream()
        .map(CodeSymbolConverter::toCodeSymbol)
        .collect(Collectors.toList());
```

```java
// L754-755：同文件 findReferences 的正确范式（对照）
int usageCap = limit > 0 ? limit : CodeIndexService.MAX_QUERY_RESULTS;
qb.setLimit(usageCap);
```

- **严重程度**：P3
- **现状**：两个查询方法在 `limit<=0` 时构造无 LIMIT 查询并全量物化为 List；同文件 findReferences 已确立"limit 非正时取 MAX_QUERY_RESULTS 兜底 + warnIfCapped"的范式。当前唯一 biz 调用方（NopCodeSymbolBizModel.java L96、L117）恒传正值，风险仅在 `ICodeIndexService` 内部/编程式调用面。
- **风险**：后续调用方遗漏归一化时，一次 `limit=0` 调用即触发全索引符号/注释行物化（10 万级实体 × 转换对象）。
- **建议**：两处对齐 findReferences 范式（`limit > 0 ? limit : MAX_QUERY_RESULTS` + warnIfCapped）。
- **信心水平**：高（分支行为与对照范式均为直接代码事实；触达面已核实当前为正）
- **误报排除**：为什么不是" biz 层已兜底所以无需报"：biz 层兜底是调用侧巧合而非服务侧契约，同文件已存在服务侧兜底范式证明该层自我要求的一致性标准，此为偏离而非风格差异。
- **复核状态**：已复核（2026-10-23 独立复核，判定：保留 P3，见文末「维度复核结论」）

### C5-009 getDepGraph 全量依赖图随响应物化，无 limit 参数与截断

- **文件**：`nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java`
- **行号**：L1139-1175（getDepGraph）；数据源为 C5-004 的无界缓存（L1142）
- **证据**：

```java
// L1142-1160：取全量 deps，逐行构造 DTO，无 limit/topN/截断
List<NopCodeDependency> deps = cacheManager.getOrRebuildDependencies(indexId, daoProvider);
List<DepEdgeDTO> edges = new ArrayList<>();
Map<String, int[]> degreeMap = new LinkedHashMap<>();
for (NopCodeDependency dep : deps) {
    if (!includeExternal && !Boolean.TRUE.equals(dep.getResolved())) {
        continue;
    }
    ...
    edges.add(edge);
```

- **严重程度**：P3
- **现状**：`getDepGraph` 为 biz 动作（NopCodeIndexBizModel.java L275），将索引全部依赖行展开为 DepGraphDTO（nodes+edges 全量）随 GraphQL 响应返回；无 limit 参数。对照同文件 `getReverseDeps`（L1118-1128）有 `limit` 截断、其余图查询有 topN。
- **风险**：响应体与峰值内存随依赖行数线性增长且与 C5-004 叠加；大索引下单请求即可产生巨型响应。
- **建议**：增加 `limit`/`topN` 参数（对齐 getReverseDeps），或默认仅返回度数 Top-K 节点与其邻边。
- **信心水平**：高
- **误报排除**：为什么不是"依赖图接口本来就该返回全图"：同文件同族接口（getReverseDeps/getSurprisingConnections/getExplorationQuestions）均带 limit/topN，全量返回无惯例支撑，属该接口独有的守卫缺失。
- **复核状态**：已复核（2026-10-23 独立复核，判定：保留 P3；getReverseDeps "假对照"经 R2-004 修正获独立确认，见文末「维度复核结论」）

---

## 三、维度集外强信号线索（CL 附记）

### CL-101 git 子进程 readAllBytes 先于 waitFor，静默挂起时 60s 超时永不生效（C7 挂死可检测性）

- **文件**：`nop-code/nop-code-service/src/main/java/io/nop/code/service/cluster/ClusterWorkspaceManager.java` L131-162（gitWithCredential）、L176-205（gitOutput）；证据片段见清单 #27 路径 L145-147、L186-188：`String output = new String(process.getInputStream().readAllBytes(), ...)` 位于 `process.waitFor(GIT_TIMEOUT_MILLIS, ...)` 之前。
- **说明**：readAllBytes 阻塞至 EOF；git 进程若在无输出的状态下挂起（网络挂起、credential helper 卡住、文件锁），readAllBytes 永不返回，GIT_TIMEOUT_MILLIS=60000 的超时分支不可达。属 C7（挂死检测）与 C6（进程/流释放）维度，内存面风险低。信心：高。建议复核方向：异步读 + waitFor(timeout) 主导，或 PA/流拷贝线程 + 超时 kill。

### CL-102 缓存重建 offset 分页与并发索引写竞态，可缓存撕裂快照并驻留 TTL 1h（C1/C3 一致性）

- **文件**：`nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeCacheManager.java` L192-252（rebuildSymbolTable/rebuildCallGraph 的 offset 翻页）；`CodeIndexService.java`（写路径持 `withIndexLock`，而缓存重建仅持 CodeCacheManager 内部 lock，两锁不相交）。
- **说明**：offset 分页期间若同 indexId 发生 deleteFileRecords/重插（非确定性 ID），翻页会跳行/重行，得到的部分快照被放入缓存并存活至 TTL（3600s）或下次写失效；`getOrRebuildSymbolTable` 的调用方（图查询、影响分析）在无索引锁的读路径上触发重建。属 C1/C3 并发一致性维度，非内存面。信心：中（竞态窗口真实存在，但触发需读写并发，且 invalidation 在写完成后仍会执行一次——撕裂快照仅在"读重建与写交错且写前已失效"的窗口内驻留）。建议复核方向：重建与写共用 indexLock，或重建改 keyset/快照分页。

---

## 四、复核提示

1. 本初审全部条目为线索；C5-001/C5-002 两个 P1 的判级依赖"公开 biz 面 × 输入不可控"前提，复核时请确认部署包络（是否已有网关/配额层抵消并发叠加）。
2. C5-006 的实际量纲缺少压测数据，判级可能下调至 P3。
3. 清单 #7（FlowDetector 二次放大）与 #23（detectCommunities 守卫不一致）本轮记表不立案，复核如确认可并入后续批次。
4. 计数口径：清单 30 行覆盖全部"随输入增长"容器盘点点；查询面口径为 `grep -rn "findPageByQuery\|findAllByQuery" nop-code/nop-code-service/src/main`（53 处，全部逐一核对 setLimit 或兜底，无遗漏项）。

---

# C5 深挖第 2 轮

- **日期**: 2026-10-22（第 2 轮合并批，因 C5-001/002 同族放大与收尾问题触发）
- **本轮范围**: (1) 公开查询面「每请求全量物化」站点逐 @BizQuery 清扫（NopCodeIndexBizModel 28 个动作 + NopCodeSymbolBizModel 13 个 + NopCodeFileBizModel 4 个逐一过）；(2) GraphMetricStore finder LIMIT 面全清单；(3) analyzeProject 生产调用链与触发面守卫收口
- **盲审声明**: 仅读取第 1 轮 C1/C5 两文件；未读取其他维度文件及 plans/bugs/lessons/backlog。

## 本轮结论总览

逐 @BizQuery 清扫后，第 1 轮 C5-001 的「每请求全量物化」同族在公开查询面上还有 **4 组未登记站点**：物化读装配路径（R2-001，P2）、getPublicSurface 无 LIMIT 查询（R2-002，P2）、fileTree 全量 CLOB 物化（R2-003，P2）、依赖图邻接族与搜索路径投影物化（R2-004/005，P3）。C5-002 的生产调用链收口与触发面守卫结论见 R2-006（P3）。第 1 轮清单 #23（detectCommunities 守卫不一致）按第 1 轮复核提示 3 的约定在本轮并入立案（R2-007，P3）。

---

### [C5-R2-001] 物化读装配路径每请求全 family 加载：GraphMetricStore 7/8 finder 无 LIMIT + 全量符号实体 chunk 物化

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/graph/GraphMetricStore.java:158-165`（finder 本体）；消费方 `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java:132-144`（assembleCommunitiesFromMetrics）、`246-260`（assembleGraphAnalysisFromMetrics）、`828-836`（assembleCriticalNodesFromMetrics）
- **证据片段**:

```java
// GraphMetricStore.java L158-165：除 hasMaterialized(setLimit(1)) 外全部 finder 共用此无 LIMIT 查询
private List<NopCodeGraphMetric> findByMetric(String indexId, String metricType) {
    QueryBean query = new QueryBean();
    query.addFilter(FilterBeans.and(
            FilterBeans.eq("indexId", indexId),
            FilterBeans.eq("metricType", metricType)));
    return dao.findPageByQuery(query);          // 无 setLimit
}
```

```java
// CodeGraphService.java L144（assembleCommunitiesFromMetrics）：按全体社区成员符号 id 再物化全量符号实体
Map<String, NopCodeSymbol> symbolsById = loadSymbolsByIds(membership.keySet());
// L259-260（assembleGraphAnalysisFromMetrics）：按全体 ENTRY_POINT 行的 symbolId 物化全量符号实体
Map<String, NopCodeSymbol> symbolsById = loadSymbolsByIds(
        entryPoints.stream().map(NopCodeGraphMetric::getSymbolId).collect(Collectors.toSet()));
```

- **严重程度**: P2
- **并发场景**: GraphQL worker 线程池，多个 `NopCodeIndex_detectCommunities` / `getGraphAnalysis` / `getCriticalNodes` 请求并发；与 C5-001 三动作同栈时同一请求内叠加。
- **触发时序**: 请求 T1 进入 detectCommunities → hasMaterialized 命中 → 装配路径：loadSummary(1 行) + loadCommunities(全体 COMMUNITY 行 ≈ 调用图节点数 N) + loadCommunityInfo + `loadSymbolsByIds(membership.keySet())` 分 500/chunk 物化全部 N 个 NopCodeSymbol 实体 → 单请求驻留 O(N) metric 行 + O(N) 实体；T2..Tn 并发请求线性叠加。请求结束即释放（瞬时比例有界，无显式上限常量）。
- **现状**: `findByMetric` 无 LIMIT，7 个公共 finder（loadCommunities/loadScores/loadHubs/loadCommunityInfo/loadSummary/loadEntryPoints/loadBetweenness）全部全 family 物化；其中 loadCommunities/loadHubs（≈调用图全节点）、loadScores(PAGE_RANK)、loadEntryPoints（全部 METHOD/CONSTRUCTOR 符号，通常是最大 metric family）四族行数仅受图规模约束。第 1 轮 C5-001 只登记了 loadCommunities 一处叠加；本轮补全为：detectCommunities/getGraphAnalysis/getCriticalNodes 三个 @BizQuery 的**物化读装配路径**每请求执行同一模式，且 loadSymbolsByIds 把 family 行数放大为等量符号实体（含 qualifiedName/signature 等字符串字段）。
- **风险**: 按缓存自身封顶（MAX_CACHE_SYMBOLS=100000）推算，单请求物化读可达数万实体 + 等 metric 行（数十 MB 量级）；三个查询动作均为仅 `NopCodeIndex:query` 权限的公开面，并发叠加与 C5-001 同机制（无 in-flight 合并、无信号量）。 magnitude 低于 C5-001（无 4 表边集 + 3 倍邻接放大），故降 P2。
- **建议**: `findByMetric` 增加显式上限常量（对齐 GraphMetricMaterializer.BETWEENNESS_MAX_NODES 的 10000 惯例）或按 rankNo setLimit；装配路径的 loadSymbolsByIds 改为按 topN 截断后的 symbolId 集合查询（godNodes/isolated 输出本就 limit 截断，无需全量实体）；或对三个装配动作复用 C5-001 建议的 per-index 单飞护栏。
- **失效面枚举**: GraphMetricStore 全部公共 finder 逐一判定——`hasMaterialized`(setLimit(1) ○)、`loadCommunities`(无 LIMIT ●，调用点 L135/322/352/384)、`loadScores`(无 LIMIT ●，PAGE_RANK 全节点；BETWEENNESS 族受物化器 ≤10000 跳过守卫间接封顶)、`loadHubs`(无 LIMIT ●，L257/835)、`loadCommunityInfo`(无 LIMIT，行数=社区数，量纲小 ○)、`loadSummary`(单行 ○)、`loadEntryPoints`(无 LIMIT ●，L253)、`loadBetweenness`(无 LIMIT，受 ≤10000 物化守卫间接封顶 ○)。4 个 ● finder 均在公开查询请求路径上。
- **信心水平**: 高（finder 与调用点全部逐一核对）
- **误报排除**: 为什么不是 C5-001 的重复登记——C5-001 的失效层是 CodeRelationGraphLoader 的 4 张关系表全量边集物化；本条失效层是物化读装配路径（metric family 行 + 符号实体 chunk 物化），两个失效层共用同一根因（公开查询面中间产物无上限），按合并裁定口径属「同族相邻」的独立失效面，修复验证须同时覆盖。
- **复核状态**: 已复核（2026-10-23 独立复核，判定：保留 P2，见文末「维度复核结论」）

### [C5-R2-002] getPublicSurface 符号查询完全无 LIMIT：公开 biz 面 publicSurface 全索引 PUBLIC 符号物化

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java:384-403`；biz 暴露 `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:154-160`
- **证据片段**:

```java
// CodeQueryService.java L384-403：symQuery 无任何 setLimit（对照同文件其余查询全部有 MAX_QUERY_RESULTS）
IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
QueryBean symQuery = new QueryBean();
symQuery.addFilter(FilterBeans.eq("indexId", indexId));
symQuery.addFilter(FilterBeans.in("fileId", fileIds));
symQuery.addFilter(FilterBeans.eq("accessModifier", "PUBLIC"));
List<PublicAPIDTO> result = new ArrayList<>();
for (NopCodeSymbol sym : symbolDao.findAllByQuery(symQuery)) {   // ← 全文件唯一无 LIMIT 的 findAllByQuery
    if (!allowedKinds.contains(sym.getKind())) continue;
    PublicAPIDTO dto = new PublicAPIDTO();
    ...
    dto.setSignature(sym.getSignature());
    dto.setDocumentation(sym.getDocumentation());   // 两个潜在长文本字段随行物化
```

- **严重程度**: P2
- **并发场景**: GraphQL worker 线程池，`NopCodeSymbol_publicSurface` 并发请求。
- **触发时序**: 请求携带 dirPath（可为空）→ 文件侧投影查询有界（≤10000 文件）→ 符号侧查询 `indexId + in(fileIds≤10000) + PUBLIC` **无 LIMIT** → 全索引 PUBLIC 符号行（每个含 signature/documentation 文本）物化为 PublicAPIDTO 列表随响应返回；并发请求线性叠加。
- **现状**: 本轮对 CodeQueryService 全部 17 个查询方法逐一核对 setLimit：16 个有 `MAX_QUERY_RESULTS=10000` 上限或 `limit>0` 兜底（含第 1 轮 C5-008 登记的两个 limit<=0 分支），**仅 getPublicSurface 的符号查询连兜底上限都没有**——不是 C5-008 的"limit<=0 分支缺失兜底"，而是无条件无界。文件查询侧作者特意用了投影（L363-365 注释 "avoid loading CLOB sourceCode"），符号侧却无任何上限与截断标志。
- **风险**: 大索引（数万 PUBLIC 符号）单请求即可物化数十万行级 DTO；dirPath 为空时即"整个索引的公共 API 面"随单次 GraphQL 响应物化，与 C5-001/R2-001 共享同一无护栏并发叠加机制。
- **建议**: 对齐 findReferences 范式：`symQuery.setLimit(MAX_QUERY_RESULTS)` + `warnIfCapped`；若业务确需全量，改分页 API（对齐 findSymbolsPage）。
- **失效面枚举**: CodeQueryService 查询方法 × 上限判定（本轮全量核对）——getFiles(L140 有界●CLOB 见 R2-003)、getFile(L154 ○)、getFileSourceCode(L165 ○)、getFileSymbols(L177 ○)、getModuleDigest(L285/313 ○投影)、getPublicSurface(L391 **无 LIMIT ●本条**)、findFilesPage(L434 ○)、findSymbols(L486 条件有界=C5-008)、findSymbolsPage(L526 ○)、getSymbolUsages(L542 条件有界=C5-008)、getTypeOutline(L617 ○)、batchGetTypeOutlines(L649/666/672 ○)、findReferencedBy(L754 ○兜底范式)、findByAnnotation(L831/840/861 ○)、findImplementations(L884/938 ○)。全文件仅 1 处无界。
- **信心水平**: 高（全文件逐方法核对，无遗漏）
- **误报排除**: 为什么不是 C5-008 的重复——C5-008 是"limit 参数 ≤0 时缺兜底"（有参数化上限机制、仅分支缺失）；本条是**完全不存在上限机制**（无 limit 参数、无兜底常量），属更重一级的守卫缺失，且 biz 层 publicSurface 不做任何归一化（无 limit 入参可归一）。
- **复核状态**: 已复核（2026-10-23 独立复核，判定：保留 P2，见文末「维度复核结论」）

### [C5-R2-003] fileTree 查询路径每请求全量物化 ≤10000 行文件实体含 sourceCode CLOB，违背同文件投影惯例

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java:135-146`（getFiles）、`222-274`（getFileTree 仅用 path/packageName）、`77-95`（entityToFileResult L84 复制 sourceCode）；biz 暴露 `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeFileBizModel.java:55-59`
- **证据片段**:

```java
// CodeQueryService.java L138-145：getFiles 加载全实体（含 CLOB），无投影
QueryBean query = new QueryBean();
query.addFilter(FilterBeans.eq("indexId", indexId));
query.setLimit(CodeIndexService.MAX_QUERY_RESULTS);
List<NopCodeFile> files = fileDao.findAllByQuery(query);     // 实体含 sourceCode CLOB
warnIfCapped(files.size(), CodeIndexService.MAX_QUERY_RESULTS, "getFiles:" + indexId);
return files.stream().map(this::entityToFileResult)...        // L84: result.setSourceCode(entity.getSourceCode())
```

```java
// 对照：同文件已有投影惯例（L289-290 注释自证 CLOB 代价认知）
// Projection: avoid loading CLOB sourceCode for a digest that only needs scalars
List<Map<String, Object>> fileRows = fileDao.selectFieldsByQuery(fileQuery);
```

- **严重程度**: P2
- **并发场景**: GraphQL worker 线程池，`NopCodeFile_fileTree` 并发请求。
- **触发时序**: 请求 fileTree(indexId) → getFiles 加载 ≤10000 个 NopCodeFile 实体（`nop-code/model/nop-code.orm.xml` 中 SOURCE_CODE 为 CLOB 列、无 lazy 标记，随实体默认加载）→ entityToFileResult 将每个 CLOB 复制进 CodeFileAnalysisResult → getFileTree 仅读取 packageName/filePath 构建树 → CLOB 全程驻留至响应完成。
- **现状**: fileTree 的返回类型 FileTreeNode 只需路径与包名两个标量，但数据获取路径把 ≤10000 个源码全文载入内存。同文件 getModuleDigest（L289）与 getPublicSurface（L364）对同类"只要标量"的场景已确立 `selectFieldsByQuery` 投影惯例并写明理由，getFiles/getFileTree 未纳入该惯例。第 1 轮清单 #21 将 CodeSearchService 全部查询记为有界，未覆盖本路径。
- **风险**: 按平均源文件 10-50KB 计，单请求 CLOB 驻留 100MB-500MB 量级；有 MAX_QUERY_RESULTS 上界（10000）故非无界，但单位权重使该上界的实际保护力远低于同文件其他查询；并发叠加下与 R2-001 同机制放大。响应序列化面不含 sourceCode（GraphQL selection 不选即不输出），纯内存浪费。
- **建议**: getFileTree 改用 `selectFieldsByQuery` 投影（id/filePath/packageName 三字段）后自行组树；或给 getFiles 增加投影重载，fileTree 走投影版。
- **失效面枚举**: entityToFileResult 的全部调用方——getFiles（本条 ●）、getFile（单行 ○）、getFileSourceCode（直取 sourceCode 是业务语义 ○）、findFilesPage（分页 limit≤20 默认 ○）。投影惯例对照面：getModuleDigest ○、getPublicSurface 文件侧 ○、findReferencedBy 文件侧 ○（L771-772）。
- **信心水平**: 高（CLOB 映射、调用链、同文件投影惯例均为直接代码事实）
- **误报排除**: 为什么不是"有界就不用报"——共享前缀口径要求对照声明包络验算实际峰值；10000 行 × CLOB 的组合使名义上限失去保护意义，且同文件两处注释自证作者已认定 CLOB 需投影规避，属惯例偏离而非风格差异。
- **复核状态**: 已复核（2026-10-23 独立复核，判定：保留 P2，见文末「维度复核结论」）

### [C5-R2-004] 依赖图查询族每请求全量物化邻接表：getDeps/findCycles/findDependentFiles 与 getReverseDeps 的输入侧无界（C5-009 同族清扫）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java:1109-1116`（getDeps）、`1118-1128`（getReverseDeps）、`1130-1137`（findCycles）、`1181-1218`（findDependentFiles）、`1220-1262`（buildForwardAdjacency/buildReverseAdjacency/buildForwardStringAdjacency）
- **证据片段**:

```java
// L1118-1127：getReverseDeps 的 limit 是 BFS 之后的输出截断，输入侧邻接表已全量物化
DepGraphDTO getReverseDeps(String indexId, String filePath, int depth, int limit) {
    Map<String, List<DepEdgeDTO>> adj = buildReverseAdjacency(indexId);  // ← 全依赖表 → DepEdgeDTO 邻接表
    Set<String> visited = new HashSet<>();
    List<DepEdgeDTO> resultEdges = new ArrayList<>();
    bfsCollect(filePath, adj, depth, visited, resultEdges, DepEdgeDTO::getSource);
    if (limit > 0 && resultEdges.size() > limit) {
        resultEdges = resultEdges.subList(0, limit);     // 事后截断，物化成本已支出
    }
```

- **严重程度**: P3
- **并发场景**: GraphQL worker 线程池，4 个 @BizQuery（getDeps/getReverseDeps/findCycles/findDependentFiles）并发。
- **触发时序**: 任一依赖图查询 → `getOrRebuildDependencies`（全依赖行，C5-004 无上限缓存）→ 逐行构造 DepEdgeDTO 装入全图邻接 Map → BFS/SCC 仅消费其中局部 → 邻接表随请求结束释放。findCycles 额外对全图跑 Tarjan SCC。
- **现状**: 第 1 轮 C5-009 只登记了 getDepGraph 的响应面无 limit；本轮 F3 清扫补全：同族 4 个 biz 动作全部先全量物化邻接结构再计算，其中 getReverseDeps 虽有 limit 参数但仅为输出侧后截断（第 1 轮 C5-009 将其作为"对照"引用，实为同族假对照）；findDependentFiles 的 BFS 有 hops<2 界但输入邻接表仍全量。数据源叠加 C5-004（deps 缓存无上限）。
- **风险**: 单请求瞬时驻留 ≈ 全依赖行 × DepEdgeDTO（4 字段/行）×2（邻接 + 结果集）；findCycles 的 SCC 额外 O(V+E)。比例有界（随依赖表线性）、随请求释放，量纲小于 R2-001/002，判 P3。
- **建议**: 与 C5-009 合并修复：邻接构建前对 deps 行数设上限或按起点剪枝加载（按 targetFilePath/sourceFilePath 过滤的定向查询替代全图邻接）；getReverseDeps 的 limit 前移为查询侧截断。
- **失效面枚举**: getOrRebuildDependencies 全部 5 个消费点——getDepGraph L1141（C5-009 已立案）、getDeps→buildForwardAdjacency L1221（●本条）、getReverseDeps→buildReverseAdjacency L1237（●本条）、findCycles→buildForwardStringAdjacency L1253（●本条）、findDependentFiles L1184（●本条）。
- **信心水平**: 高
- **误报排除**: 为什么不是"与 C5-009 重复立案"——C5-009 的失效层是响应物化（DepGraphDTO 随 GraphQL 返回）；本条失效层是中间产物邻接表物化（含"有 limit 参数"的 getReverseDeps 输入侧同样无界），修复工单应合并但失效面必须分列验证。
- **复核状态**: 已复核（2026-10-23 独立复核，判定：保留 P3，见文末「维度复核结论」）

### [C5-R2-005] CodeSearchService 每请求全文件表投影物化：buildFilePathCache/filterByLanguage 无缓存逐请求翻全表

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeSearchService.java:219-242`（buildFilePathCache）、`350-377`（filterByLanguage）；调用点 L111/153/176/201 与 L162/185/215
- **证据片段**:

```java
// L219-241：每次搜索请求翻全 NopCodeFile 表（id+filePath 投影），无缓存
private Map<String, String> buildFilePathCache(String indexId) {
    ...
    long offset = 0;
    while (true) {
        fq.setOffset(offset);
        fq.setLimit(BATCH_QUERY_LIMIT);            // 1000/页翻到表尾
        List<Map<String, Object>> rows = fileDao.selectFieldsByQuery(fq);
        ...
        if (rows.size() < BATCH_QUERY_LIMIT) break;
        offset += BATCH_QUERY_LIMIT;
    }
    return cache;
}
```

- **严重程度**: P3
- **并发场景**: GraphQL worker 线程池，`NopCodeSymbol_searchCode` 并发请求（引擎路径 L111、DB 三路径 L153/176/201 全部触发）。
- **触发时序**: 每次 searchCode → 命中 ≤limit*3 候选（有界）→ **另起全文件表翻页**（1000/页直至表尾）构建 id→path Map → 仅为解析 ≤150 个候选的 filePath；language 过滤时再翻一次全表（按 language 过滤的 path 集合）。
- **现状**: 每请求两次全表投影扫描（fileId→path + language→paths），Map 驻留与文件数成比例且随请求释放；投影无 CLOB，单行权重小。第 1 轮清单 #21 记"CodeSearchService 全部查询有界"仅核对了结果集 limit，未覆盖这两个辅助全表扫描。
- **风险**: 大索引（万级文件）下每请求 2×N 行扫描的固定开销与瞬时 Map 驻留；并发下 DB 读放大显著（纯性能/读放大问题，非 OOM 主风险）。
- **建议**: filePathCache 按 indexId 做短 TTL 缓存（对齐模块内 CodeCacheManager 惯例），或按候选 fileId 精确 `in` 查询替代全表翻页；filterByLanguage 改为对候选 filePath 集合做定向 language 查询。
- **失效面枚举**: buildFilePathCache 调用点 4 处（searchViaEngine L111 / searchBySymbolName L153 / searchFullText L176 / searchCombined L201）全部同型 ●；filterByLanguage 调用点 3 处（L162/185/215）同型 ●；searchViaEngine 的 DB 降级路径 L139 亦汇入 searchCombined ●。
- **信心水平**: 高
- **误报排除**: 为什么不是"有界投影不用报"——投影行数无独立上限（随文件表全量），且为每请求重复支出；按 C9 口径属可量化读放大（2×文件表行/请求），因本单元为 C5 批次判 P3 并建议转 C9 复核量化。
- **复核状态**: 已复核（2026-10-23 独立复核，判定：保留 P3，见文末「维度复核结论」）

### [C5-R2-006] analyzeProject 生产调用链收口：全量构建触发面仅 2 个 admin mutation，"streaming" 变体即生产路径且回调为空实现，触发面无规模守卫（C5-002 触发层补全）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:346-377`（indexDirectory，L354 唯一 analyzeProject 调用点）、`356`（no-op 回调）；biz 面 `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:56-79`（triggerFullIndex）、`121-128`（indexDirectory）
- **证据片段**:

```java
// CodeIndexService.java L354-356：生产全量构建走的是"streaming"变体，回调为空实现
ProjectAnalysisResult result = analyzer.analyzeProject(
        VirtualFileSystem.instance(), resolvedPath, filePattern,
        batch -> {});            // ← streaming 变体的 batchCallback 传空：批回调价值（逐批消费）被放弃
```

```java
// NopCodeIndexBizModel.java L121-127：触发面守卫 = admin 角色 + 服务层路径校验，无任何规模守卫
@BizMutation
@Auth(roles = "admin")
public int indexDirectory(
        @Name("indexId") String indexId,
        @Name("directoryPath") String directoryPath,
        @Name("filePattern") @Optional String filePattern) {
    return codeIndexService.indexDirectory(indexId, directoryPath, filePattern);
```

- **严重程度**: P3
- **并发场景**: admin 触发的全量构建（同步巨事务），与查询流量并发（见 C1-002/CL-102）。
- **触发时序**: `triggerFullIndex` 或 `indexDirectory`（均 @Auth(roles="admin")）→ checkWriteAccess + validatePath + validateLocalPath（权限/路径校验，无文件数/单文件大小/仓库规模预检）→ withIndexLock → ProjectAnalyzer "streaming" 变体全量分析（C5-002 已立案的输入面无界）→ 巨事务持久化。
- **现状**: 本轮收口结论：(a) `analyzeProject` 全单元 main 源码仅 1 个调用点（CodeIndexService L354），且调用的是带 BatchCallback 的 "streaming" 变体——第 1 轮 C5-002 所述两条路径中**只有 streaming 变体在生产链上**，其回调被传空实现 `batch -> {}`，"流式"消费价值不存在，全量累积是生产行为的直接事实；(b) 不带回调的变体、两个 analyzeIncremental 变体、analyzeFiles 在 main 源码零调用方（仅测试可达）；(c) 触发面 = triggerFullIndex + indexDirectory 两个 admin mutation，全仓 grep 无定时任务/集群 worker/启动钩子等其他触发点（ShardBuildCoordinator/beginShardBuild/indexFileSet 零装配零调用，与 C1-003/CL-006 结论一致）；(d) 触发面守卫仅有鉴权与路径校验，MAX_FILE_SIZE_BYTES/MAX_FILE_COUNT 死常量（C5-002 已立案）之外**无任何规模守卫**。
- **风险**: 立案价值在契约面：C5-002 的修复（激活死常量）只需覆盖 L354 一个调用点即可闭合生产面（简化修复验证）；同时确认不存在"定时/自动触发"放大源，运营侧可通过 admin 权限收敛输入面。风险本体已由 C5-002 承载，本条不重复计级。
- **建议**: 随 C5-002 修复一并处理：在 indexDirectory 触发面（服务层入口）增加规模预检（文件数/累计字节数超限 fail-fast），使守卫位于"触发面"而非仅"分析器内部"；L356 的空回调 either 移除（改用非 streaming 变体）或实现真正的分批持久化（与 C5-002 建议的滑动窗口二选一）。
- **失效面枚举**: ProjectAnalyzer 全部分析入口 × 生产可达性——`analyzeProject(loader,path,pattern)`（零调用方）、`analyzeProject(loader,path,pattern,callback)`（●生产路径，1 调用点）、`analyzeProject(String)`（零 main 调用方）、`analyzeIncremental(String,ProjectAnalysisResult)`（零）、`analyzeIncremental(String,String,IFingerprintStore)`（零）、`analyzeIncremental(String,String)`（零）、`analyzeFiles`（仅被零调用方的 analyzeIncremental 使用）。生产面Closure：1 个调用点。
- **信心水平**: 高（调用点与装配面经全仓 main-source grep 复核）
- **误报排除**: 为什么死变体不单独立案——共享前缀口径下未装配代码按"休眠"降级（同 C5-007 先例）；本条立案对象是"生产触发面无规模守卫 + streaming 名实不符"的契约层缺陷，不是死代码清理建议。
- **复核状态**: 已复核（2026-10-23 独立复核，判定：保留 P3，见文末「维度复核结论」）

### [C5-R2-007] 社区检测节点守卫不一致：runCommunityDetection 无守卫，4 个调用面不设防而 3 处设 10000 守卫；diffGraph 单侧守卫非对称（第 1 轮清单 #23 并入立案）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java:744-754`（runCommunityDetection 本体无守卫）；无守卫调用面 L121（detectCommunities 兜底）、L476（getKnowledgeGaps）、L495（exportGraph communityView）、L521（diffGraph target 侧）；有守卫对照 L446（getCriticalNodes）、L506-510（diffGraph baseline 侧）、L841（assembleCriticalNodesFromMetrics）
- **证据片段**:

```java
// L500-521：diffGraph 对 baseline 侧设 10000 守卫并提前返回，target 侧同型调用无守卫
CallGraph baselineCallGraph = cacheManager.getOrRebuildCallGraph(baselineIndexId, ...);
SymbolTable baselineSymbolTable = cacheManager.getOrRebuildSymbolTable(baselineIndexId, ...);
if (baselineSymbolTable.size() > MAX_NODES_FOR_COMMUNITY_DETECTION) {
    LOG.warn("Baseline graph too large for community detection diff ...");
    return new GraphDiffDTO();
}
...
CallGraph targetCallGraph = cacheManager.getOrRebuildCallGraph(targetIndexId, ...);
SymbolTable targetSymbolTable = cacheManager.getOrRebuildSymbolTable(targetIndexId, ...);
Set<String> targetNodes = targetCallGraph.getAllNodeIds();
CommunityResult targetCommunities = runCommunityDetection(targetCallGraph);   // ← 无守卫
```

- **严重程度**: P3
- **并发场景**: GraphQL worker 线程池，4 个无守卫 @BizQuery 并发（detectCommunities 兜底路径/getKnowledgeGaps/exportGraph/diffGraph）。
- **触发时序**: 大索引（符号数 >10000，缓存侧可达 MAX_CACHE_SYMBOLS=100000）→ 请求命中物化装配失败或未物化 → 兜底 runCommunityDetection 对全调用图（≤50 万边）跑 Leiden（config 有 setTimeoutMs(60000) 时间守卫，无节点数守卫）→ 全图节点/边结构的计算中间体驻留至完成或超时。
- **现状**: `MAX_NODES_FOR_COMMUNITY_DETECTION=10000` 守卫在 getCriticalNodes 与 diffGraph-baseline 生效，但 `runCommunityDetection` 本体及其另 4 个调用点不经过任何守卫。第 1 轮清单 #23 记"守卫不一致"未立案（复核提示 3 约定并入后续批次），本轮补入 diffGraph **单侧守卫**这一非对称缺陷：同方法内 baseline 超限返回空 diff，target 超限却照常计算——同一接口对两个同类输入执行不同的规模策略。
- **风险**: 守卫不一致使 10000 常量形同部分实现：超大图在 detectCommunities 兜底/getKnowledgeGaps/exportGraph/diffGraph-target 上仍执行 O(V·E) 级 Leiden，60s 超时兜住挂死但不兜内存峰值；行为不可预期（同一大图 detectCommunities 兜底算、getCriticalNodes 跳过）误导调用方容量规划。
- **建议**: 守卫下沉至 runCommunityDetection 本体（超限返回空结果 + warn，对齐 getCriticalNodes 的 skip-and-degrade 语义）；diffGraph 补 target 侧同款守卫。
- **失效面枚举**: runCommunityDetection 全部调用点——detectCommunities L121（无守卫●）、getKnowledgeGaps L476（无守卫●）、exportGraph L495（无守卫●）、diffGraph baseline L513（有守卫○，守卫在调用方 L506）、diffGraph target L521（无守卫●非对称）。守卫生效面：getCriticalNodes L446、diffGraph L506、assembleCriticalNodesFromMetrics L841。
- **信心水平**: 高
- **误报排除**: 为什么现在立案而非继续记表——第 1 轮复核提示 3 明示"复核如确认可并入后续批次"；本轮已逐调用点取证且发现 diffGraph 单侧守卫这一第 1 轮未见的非对称缺陷，属新增结构事实而非机械重复。Leiden 的 60000ms timeout 是挂死守卫而非内存守卫，不构成误报排除。
- **复核状态**: 已复核（2026-10-23 独立复核，判定：保留 P3，见文末「维度复核结论」）

## 本轮清扫表终止信号

- 公开查询面（NopCodeIndexBizModel 28 动作 + NopCodeSymbolBizModel 13 动作 + NopCodeFileBizModel 4 动作 + 13 个 CrudBizModel 标准面）已逐动作核对中间产物与结果集上限：无界/重物化站点已全部登记于 C5-001/C5-008/C5-009/R2-001~R2-005；其余动作（getStats/getIncrementalStatus/getFlow/listFlows/getAffectedFlows/analyzeChanges/showSymbol/getByPath/searchFiles/findSymbolsPage 等）均分页或有界有截断标志。
- GraphMetricStore 8 finder、CodeQueryService 17 查询方法、CodeSearchService 全路径、GraphMetricMaterializer 写读两侧、ProjectAnalyzer 7 个分析入口均已全量清点（各失效面枚举字段内给出逐一判定）。
- **未发现新的高价值问题。深挖结束。**

---

# C5 维度复核结论（独立复核）

- **日期**: 2026-10-23（CRA WI 6.3 Phase 4，C1+C5 维度独立复核子代理）
- **复核方法**: 逐条重读 live 源码 file:line（基线 @ 7b0ccc89d4，与初审同版无漂移）；两个 P1 按"证据链与生产可达性逐环重演"执行；判级对照第 1 轮复核提示 1/2 的预案逐项裁决。不修改初审既有内容，仅追加本节与 R2 状态行回填。

## 逐条裁定

### C5-001（P1）→ **保留，维持 P1**

- 证据链三环逐环重演：(a) **守卫声称不存在**——Javadoc L26-29 "Deliberately unbounded... memory guards are the domain of result materialization and the invariant-loop gates" 属实，且声称的防线确实缺失：`batched()`（L180-197）全量累积 4 表、`CodeRelationGraph` 构造器二次物化 out/in 邻接、下游 `SurprisingConnectionAnalyzer` 无任何 MAX/limit/cap 常量（grep 无命中）、请求路径无 in-flight 合并或信号量；(b) **公开面触发链**——`getSurprisingConnections`/`getExplorationQuestions`/`exportGraphWiki` 均为 NopCodeIndexBizModel 公开 @BizQuery（L161/171/180，`NopCodeIndex:query` 权限）→ loader.load（CodeGraphService L321/351/383）+ `metricStore.loadCommunities` 无 LIMIT + `getOrRebuildSymbolTable` 同栈；(c) **并发叠加**——GraphQL worker 并发线性叠加无护栏属实。P1 维持（公开查询权限 × 输入随索引规模 × 中间产物零守卫 × 无并发护栏四要素齐备）。

### C5-002（P1）→ **保留，维持 P1（登记 P1 下限边界注）**

- **死常量零引用**核准：全单元 grep `MAX_FILE_SIZE_BYTES|MAX_FILE_COUNT` 仅 ProjectAnalyzer L54-55 两处声明，零使用。**伪流式**核准：两变体 BatchQueue 消费端均 `fileResults.add(result)`/`allFileResults.add(result)` 全量累积（L134-138/L210-214），"避免一次性加载"（L121-122）与 "streaming"（L205）注释与实现相反；`resource.readText()`（L176/261）无大小检查。**生产触发链**核准：`analyzeProject` 全单元唯一生产调用点 CodeIndexService L354（streaming 变体 + 空回调 `batch -> {}`）← triggerFullIndex（NopCodeIndexBizModel L56-79）与 indexDirectory（L121-128）双 @Auth(roles="admin") mutation（R2-006 收口吻合）。
- **判级复核**：触发面 admin-only + withIndexLock 单飞，属 P1 判级的边界情形；维持 P1 的依据：输入面（仓库内容）本质不可控（误提交的构建产物/minified bundle 单文件即可触发 readText 全量进堆 → 全进程 OOM，故障域非本请求）、守卫死代码 + 注释名实不符构成契约漂移（共享前缀口径）。**边界注**：若部署包络确认存在仓库白名单/配额前置层，可复议降 P2（对应第 1 轮复核提示 1 的部署包络确认项，移交收尾裁决时一并确认）。

### C5-003（P2）→ **保留，维持 P2**

- 两提取器输出侧无上限核准（NameSimilarity L41-84 / DocKeyword L41-97，均仅 MAX_SYMBOLS=5000 输入封顶）；AnnotationPatternExtractor 对照惯例核准（L28-29 MAX_EDGES=50000 + L92 达上限 return）；语义边保存循环无 flush/evict（CodeIndexService L1273-1297）与启发式边循环（synthesizeAndPersistHeuristicEdges L1302-1357）对照文件/符号侧 BatchQueue(500)+flush+evictAll（L1261-1269）全部属实。

### C5-004（P2）→ **保留，维持 P2**

- 常量组 L44-46 仅 MAX_CACHE_SYMBOLS/EDGES 无 deps 上限、`rebuildDependencies`（L254-270）无截断标志、消费面 `getDepGraph`（L1140-1175）/`findDependentFiles`（L1181-1218）全量展开属实。与 C5-001 峰值叠加击穿"20×单条上界"推导的论证成立。

### C5-005（P2）→ **保留，维持 P2**

- `replaceRows` 删除侧分批 flush+clear（L285-287）vs 插入侧 `dao.saveEntity` 循环无 flush/evict（L290-292）的不对称为直接代码事实；rows 全量内存构建（materialize L97-105）；读路径自愈触发面核准（ensureMaterializedForGraphMetrics L347-356、detectCommunities L112-121、getCriticalNodes L439-443）——物化确实在查询请求路径上被触发，与 C5-001 同栈叠加的论证成立。

### C5-006（P2）→ **保留，维持 P2（不采纳第 1 轮复核提示 2 的降 P3 预案）**

- mergedTable 每变更文件全量复制全局符号表（L1585-1592）+ BatchQueue 消费端逐文件调用（L1183-1188）属实；`getAll()` 防御性快照致复制成本不可复用属实。维持 P2 依据：大索引（封顶 10 万符号）×大增量批（分支合并千文件级）为具体可达场景，O(F×S) 瞬时分配与 CPU 放大发生在持 indexLock 的巨事务内；修复成本极低（批次级提升）。无压测数据不足以支持降级。

### C5-007（P3）→ **保留，维持 P3**

- 无界 CHM（L14-19）+ 休眠状态核准：主代码仅 semantic 包内互引（LlmSemanticEdgeExtractor 构造参数）、无 beans.xml 装配、`discoverSemanticExtractors`（L288-294）仅注册 3 个非 LLM 提取器。与 CL-002 同站点、裁定一致（接入前补上界的前置条件口径统一）。

### C5-008（P3）→ **保留，维持 P3**

- `findSymbols` L486 / `getSymbolUsages` L542 的 `if (limit > 0) qb.setLimit(limit)` 无兜底 + `findReferences` L754 兜底范式对照属实；biz 层恒传正核准（findSymbolsPage `limit > 0 ? limit : 20`、usages 同型）——风险确仅存于内部/编程式调用面。P3 恰当。

### C5-009（P3）→ **保留，维持 P3**

- `getDepGraph`（L1140-1175）无 limit/topN/截断、biz 暴露 L275 属实；原文以 getReverseDeps 为"对照"被 R2-004 修正为假对照（limit 是 BFS 后输出截断，输入侧邻接已全量物化）——该修正在复核中独立确认（L1123-1125 subList 在 bfsCollect 之后），不构成对本条的驳回（getDepGraph 本体的响应面无界事实不变）。

### [C5-R2-001]（P2）→ **保留**

- `findByMetric`（L158-165）无 LIMIT + 对照 L48 `setLimit(1)` 属实；7 finder 失效面枚举抽查吻合（loadCommunities/loadScores/loadHubs/loadEntryPoints 无 LIMIT）；三装配路径 `loadSymbolsByIds` 全量符号实体 chunk 物化（L144/L259/L836）属实；biz 面 detectCommunities L208 / getGraphAnalysis L225 / getCriticalNodes L321 均查询权限。与 C5-001 的失效层分立（关系表边集 vs metric family 行+符号实体）成立，非重复登记。

### [C5-R2-002]（P2）→ **保留**

- `symQuery` 无任何 setLimit（L391-403）+ 文件侧投影对照（L360-366 "avoid loading CLOB sourceCode"）+ biz publicSurface 无 limit 入参（NopCodeSymbolBizModel L154-160，query 权限）全部属实。与 C5-008 的分立（有参数化机制缺分支 vs 完全无上限机制）成立。

### [C5-R2-003]（P2）→ **保留**

- `getFiles`（L134-147）全实体 + setLimit(10000) → `entityToFileResult` L84 复制 sourceCode → `getFileTree`（L222 起）仅用 packageName/path 属实；`SOURCE_CODE` 为 CLOB 列（nop-code.orm.xml L248-249，无 lazy 标记）属实；同文件投影惯例对照（getPublicSurface L363-366）属实。名义上限 × CLOB 权重使保护力失效的论证成立。

### [C5-R2-004]（P3）→ **保留**

- 4 消费点全量邻接物化属实：getDeps→buildForwardAdjacency（L1110/L1220-1234）、getReverseDeps→buildReverseAdjacency（L1119/L1236-1250）、findCycles→buildForwardStringAdjacency（L1252-1262）、findDependentFiles（L1181-1218，hops<2 属实）；getReverseDeps limit 为输出侧后截断（L1123-1125）属实。P3（比例有界、随请求释放）恰当。

### [C5-R2-005]（P3）→ **保留**

- `buildFilePathCache`（L219-242）每请求翻全表 + 调用点 L111/153/176/201、`filterByLanguage` L162/185/215 属实；投影无 CLOB、随请求释放。P3 + 建议转 C9 量化读放大的口径恰当。

### [C5-R2-006]（P3）→ **保留**

- 唯一生产调用点 L354 + 空回调 `batch -> {}` + 双 admin mutation + 无规模预检全部属实；契约面价值（C5-002 修复仅需覆盖 1 个调用点即可闭合生产面）成立。失效面枚举（7 分析入口×生产可达性）抽查无矛盾。

### [C5-R2-007]（P3）→ **保留**

- `runCommunityDetection`（L744-754）仅 `<2` 下限 + setTimeoutMs(60000) 无节点数守卫属实；4 个无守卫调用点（detectCommunities 兜底 L121、getKnowledgeGaps L476、exportGraph L495、diffGraph target L521）+ 3 个守卫生效面（getCriticalNodes L446、diffGraph baseline L506-510、assembleCriticalNodesFromMetrics L841）+ diffGraph 单侧守卫非对称全部逐行核准。60s 超时是挂死守卫非内存守卫的辨析成立。

## CL 线索裁定（CL-101/102）

| CL | 裁定 | 复核要点 |
|---|---|---|
| CL-101 | **确认** | `readAllBytes()` 先于 `waitFor(GIT_TIMEOUT_MILLIS)` 双站点核准（gitWithCredential L146-147、gitOutput L186-188）：输出静默挂起时 readAllBytes 永阻塞，60s 超时分支不可达。与 C1-CL-003/C6-001/C7 git 面同族（去重归 Phase 4/6，dispatch-log 已登记该重复族） |
| CL-102 | **确认** | rebuild offset 翻页（L192-252）仅持 cacheManager 内部锁、写路径持 indexLock、两锁域不相交属实；与 C1-002 同窗口机制（写前失效→读重建回填 pre-commit 快照→提交后无再失效），撕裂快照驻留至 TTL 推理成立；信心"中"恰当 |

## 批次覆盖核验（三源码根痕迹）

- **nop-code-core**：ProjectAnalyzer/CodeFileAnalysisResult（C5-002）、CodeRelationGraph/CodeEdgeData（C5-001 关联）、InMemoryFingerprintStore（清单 #26）——痕迹在案。
- **nop-code-flow**：FlowDetector（清单 #6/#7）、ChangeAnalyzer（清单 #28）、IncrementalDetector（#28 关联）——痕迹在案。
- **nop-code-service**：impl 6 类 + graph 包（GraphMetricStore/CodeRelationGraphLoader/3 提取器）+ semantic（LlmEdgeCache）+ cluster（ClusterWorkspaceManager）+ entity 3 biz model + dao entity/orm 模型（nop-code.orm.xml CLOB 核验）——痕迹在案。
- **单元计数核准**：nop-code-service 实测 53f/11,193 行与审计单元声明精确吻合；core 51f、flow 12f 与基线 roots（`_tmp/cra-baseline-nop-code-index.txt`）一致。**覆盖核验通过。**

## 最终保留项

- **立案保留 16/16**：C5-001（P1）、C5-002（P1，附部署包络边界注）、C5-003~006（P2×4）、C5-007~009（P3×3）、R2-001~003（P2×3）、R2-004~007（P3×4）。**无驳回、无降级、无升级。**
- **CL 确认 2/2**：CL-101、CL-102 确认。
- 判级分布：P1×2 + P2×7 + P3×7（与初审一致）。
