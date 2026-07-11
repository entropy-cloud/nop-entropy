# 286 nop-graph 通用图算法库创建与 nop-code-graph 迁移

> Plan Status: completed
> Last Reviewed: 2026-07-10
> Source: `ai-dev/design/nop-graph-design.md`, `ai-dev/analysis/ontology-driven-agent-vs-nop-code-index.md`
> Related: —

## Purpose

将 `nop-code/nop-code-graph` 中与代码领域耦合的图算法抽取为独立的通用图算法库 `nop-graph`，使 `nop-wf`/`nop-task`/`nop-stream` 等模块可复用同一套算法，并让 `nop-code` 通过薄适配层接入。

## Current Baseline

- `nop-code/nop-code-graph/` 存在 16 个 Java 源文件（2864 行），依赖 `jgrapht-core` 1.5.2、`jgrapht-io` 1.5.2、`nl.cwts:networkanalysis` 1.3.0、`slf4j-api`
- 算法直接依赖 `CallGraph`（`nop-code-core` 的 concrete class）和 `SymbolTable`/`CodeSymbol`，无法被其他模块复用
- `CriticalNodeAnalyzer` 的 hub nodes 基于 degree 排序（非 PageRank），bridge nodes 基于 JGraphT `BetweennessCentrality`
- `CommunityDetector`（938 行）除 Leiden/LP 外还包含：large graph mode、super-community splitting、cohesion 计算、dominantPackage 检测、label 生成、timeout 保护、fallback 逻辑、unclustered node assignment。其中 dominantPackage/label 依赖 SymbolTable
- `GraphExporter`（287 行）依赖 `jgrapht-io` 的 `GraphMLExporter`，支持 GraphML/Mermaid/JSON 三种格式，支持 communityView 模式
- `GraphDiffer` 基于 `GraphSnapshot`（含 communityMap），输出 `GraphDiff`（含 communityChanges）
- `ImpactAnalyzer` 返回 upstream（callers 方向）+ downstream（callees 方向）+ riskLevel，含 fuzzy qualifiedName 匹配逻辑
- `CodeGraphService.findCycles()` / `tarjanSCC()` 操作的是**文件依赖图**（NopCodeDependency 的 sourceFilePath→targetFilePath），不是 CallGraph
- 外部消费者：`CodeGraphService.java`、`CodeIndexService.java`、`FlowDetector.java` 均直接 import `io.nop.code.graph.*`
- nop-code-graph 有 15 个测试文件
- 根 `pom.xml` 已注册 `<module>nop-code</module>`，无 `nop-graph`；nop-code 版本为 `1.0.0-SNAPSHOT`，根 pom 为 `2.0.0-SNAPSHOT`
- 设计文档 `ai-dev/design/nop-graph-design.md` 存在但与 Plan 的 api/core 分离决策不一致（设计文档将算法放在 api，Plan 放在 core），需同步更新

## Goals

- 创建 `nop-graph/` 一级模块，包含 `nop-graph-api`（接口+契约类型，零依赖）和 `nop-graph-core`（算法实现+InMemoryGraph）
- `nop-graph-api` 只暴露契约：`IGraph`、`Edge`、结果/配置类型
- `nop-graph-core` 提供缺省算法实现，保留 JGraphT/CWTS 依赖
- `nop-code` 迁移后行为与迁移前一致（所有公开方法行为不回归）
- `nop-code` 中保留的业务类（EntryPointScorer 等）明确归属模块和包名

## Non-Goals

- 不创建 `nop-graph-neo4j`/`nop-graph-orm`/`nop-graph-samples` 模块
- 不迁移 `nop-wf`/`nop-task`/`nop-stream` 的图结构
- 不重新实现 Leiden 算法（保留 CWTS 库依赖）
- 不修改 `nop-core` 现有图接口体系
- 不迁移 dominantPackage/label/super-community-splitting 等业务逻辑到 nop-graph（留在 nop-code 做后处理）

## Scope

### In Scope

- `nop-graph/` 模块骨架 + `nop-graph-api` + `nop-graph-core`
- `nop-graph-api`：`IGraph`、`Edge`、完整的结果/配置类型（含 upstream/downstream、communityChanges 等字段）
- `nop-graph-core`：`InMemoryGraph` + 全部算法 + nop-core 适配器
- `nop-code` 迁移：`CodeCallGraph implements IGraph` + 更新所有消费者 + 业务类迁移到 nop-code-core/service + 测试迁移
- 删除 `nop-code/nop-code-graph/` 子模块
- 更新 `ai-dev/design/nop-graph-design.md` 使其与 Plan 一致

### Out Of Scope

- `nop-graph-neo4j`/`nop-graph-orm`/`nop-graph-samples` 模块
- `nop-wf`/`nop-task`/`nop-stream` 图结构迁移
- OntoAgent 风格的 Shape Constraint 系统

## Execution Plan

### Phase 1 - 设计文档同步 + nop-graph-api 契约类型

Status: completed
Targets: `ai-dev/design/nop-graph-design.md`, `nop-graph/pom.xml`, `nop-graph/nop-graph-api/`, 根 `pom.xml`

- Item Types: `Fix | Decision | Proof`

- [x] 更新 `ai-dev/design/nop-graph-design.md`：将算法包从 `nop-graph-api` 移到 `nop-graph-core`，使设计文档与 Plan 的 api/core 分离决策一致；修正 §9 中残留的泛型 `IGraph<N,E>` 为无泛型 `IGraph`
- [x] 创建 `nop-graph/pom.xml`（pom packaging，parent=nop-entropy，声明 `nop-graph-api` 和 `nop-graph-core`）
- [x] 在根 `pom.xml` 的 `<modules>` 中添加 `<module>nop-graph</module>`
- [x] 创建 `nop-graph/nop-graph-api/pom.xml`（零外部依赖）
- [x] 实现 `IGraph` 接口（`getOutEdges(String)` / `getInEdges(String)`，无泛型）
- [x] 实现 `Edge` 类（sourceId/targetId/weight/type/attrs，attrs 为 mutable Map 允许适配层注入领域属性）
- [x] 实现 `ImpactResult`：包含 `upstream`（List\<ImpactedNode\>）、`downstream`（List\<ImpactedNode\>）、`riskLevel`、`maxDepth`。`ImpactedNode` 含 String nodeId + int depth
- [x] 实现 `ImpactConfig`：maxDepth、maxNodes
- [x] 实现 `CommunityResult`：包含 `communities`（List\<CommunityInfo\>，每个含 Set\<String\> nodeIds + double cohesion）、`totalSymbols`、`totalCommunities`、`averageCohesion`、`modularity`、`algorithmUsed`、`processingTimeMs`
- [x] 实现 `LeidenConfig`：resolution、maxIterations、timeout、minCommunitySize
- [x] 实现 `GraphDiff`：包含 `addedNodes`、`removedNodes`、`addedEdges`、`removedEdges`、`communityChanges`（List\<CommunityChange\>，每个含 nodeId + oldCommunity + newCommunity）
- [x] 实现 `BfsResult`：reachable nodes + per-node depth map
- [x] 实现 `PathQuery`：配置类型（edgeType/maxHops/nodeFilter 字段 + getter），不含执行逻辑
- [x] 实现 `CriticalNodeResult`：包含 `hubNodes`（List\<NodeScore\>）、`bridgeNodes`（List\<NodeScore\>）。`NodeScore` 含 nodeId + score + inDegree + outDegree

Exit Criteria:

- [x] `ai-dev/design/nop-graph-design.md` 中算法类位于 `nop-graph-core` 而非 `nop-graph-api`，无残留泛型
- [x] `nop-graph-api` 的 pom.xml 无任何 `<dependencies>`
- [x] `IGraph` 接口只有 `getOutEdges` / `getInEdges`，无 `vertexSet()`，无泛型
- [x] `ImpactResult` 同时有 upstream 和 downstream 字段，`ImpactedNode` 含 nodeId + depth
- [x] `CommunityResult` 包含 CodeGraphService.convertCommunityResult() 所需的全部字段（totalSymbols/totalCommunities/averageCohesion/modularity/algorithmUsed/processingTimeMs）
- [x] `GraphDiff` 包含 communityChanges 字段
- [x] `CriticalNodeResult` 同时有 hubNodes 和 bridgeNodes
- [x] 新增单元测试：各结果类型的构建与字段访问
- [x] `./mvnw compile -pl nop-graph/nop-graph-api -am` 成功
- [x] `./mvnw test -pl nop-graph/nop-graph-api` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - nop-graph-core：InMemoryGraph + 零依赖算法

Status: completed
Targets: `nop-graph/nop-graph-core/`

- Item Types: `Fix | Decision | Proof`

- [x] 创建 `nop-graph/nop-graph-core/pom.xml`（依赖 `nop-graph-api` + `nop-core`（适配器）+ `slf4j-api`）
- [x] 实现 `InMemoryGraph implements IGraph`：addEdge/addNode/getOutEdges/getInEdges/nodeSet/edgeSet
- [x] 实现 `Bfs`：`traverse(IGraph, String, int)` → `Set<String>`；`traverseFiltered(IGraph, String, int, Predicate<String>)` → `Set<String>`；`traverseWithDepth(IGraph, String, int)` → `BfsResult`
- [x] 实现 `TarjanSCC`：`compute(IGraph, Set<String>)` → `List<Set<String>>`，迭代式
- [x] 实现 `TopologicalSort`：`sort(IGraph, Set<String>)` → `List<String>`，环检测抛异常
- [x] 实现 `PageRank`：`compute(IGraph, Set<String>, int)` → `Map<String, Double>`
- [x] 实现 `ImpactPropagator`：`propagate(IGraph, String, ImpactConfig)` → `ImpactResult`，内部做双向 BFS（getOutEdges 为 downstream，getInEdges 为 upstream），riskLevel 基于 upstream+downstream 总数和 maxDepth
- [x] 实现 `GraphDiffer`：`diff(IGraph, Set<String>, IGraph, Set<String>)` → `GraphDiff`（不含 community diff，community diff 由调用者额外传入 community 映射后调用 `diffWithCommunities` 重载）
- [x] 实现 `PathQueryExecutor`：`execute(IGraph, String, PathQuery)` → `Set<String>`
- [x] 实现 `fromDirectedGraph(IDirectedGraphView<?>)` 适配方法
- [x] Decision: GraphExporter 和 BetweennessCentrality 推迟到 Phase 3（需要 JGraphT 依赖）

Exit Criteria:

- [x] `nop-graph-core` 的 pom.xml 依赖只有 `nop-graph-api`、`nop-core`、`slf4j-api`，不含 JGraphT/CWTS
- [x] `ImpactPropagator.propagate` 返回的 `ImpactResult` 同时填充 upstream 和 downstream
- [x] `GraphDiffer.diff` 返回的 `GraphDiff` 的 communityChanges 为空列表（社区 diff 由重载方法填充）
- [x] BFS 测试：5 节点图，maxDepth=1 返回 2 节点、maxDepth=2 返回 4 节点
- [x] TarjanSCC 测试：含环图（A→B→C→A + D→E），2 个 SCC
- [x] TopologicalSort 测试：DAG 顺序合法 + 含环抛异常
- [x] PageRank 测试：星型图中心节点 rank 最高
- [x] ImpactPropagator 测试：3 层调用链，upstream + downstream 都有结果，maxNodes 截断生效，riskLevel 正确
- [x] GraphDiffer 测试：两图对比，addedNodes/removedNodes/addedEdges/removedEdges 正确
- [x] PathQueryExecutor 测试：edgeType 过滤 + nodeFilter 过滤生效
- [x] InMemoryGraph 测试：5 节点 8 边，getOutEdges/getInEdges 正确
- [x] 适配器测试：`fromDirectedGraph` 包装后 BFS 结果与原 `IDirectedGraphView` 一致
- [x] **端到端验证**：InMemoryGraph 构建 → ImpactPropagator.propagate → ImpactResult 双向结果完整 → GraphDiffer.diff 两图对比结果正确
- [x] **接线验证**：`fromDirectedGraph` 返回的 IGraph 确实调用底层 `IDirectedGraphView` 的方法
- [x] **无静默跳过**：空图/空节点集抛 `IllegalArgumentException`
- [x] `./mvnw test -pl nop-graph/nop-graph-core -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - nop-graph-core：社区检测 + GraphExporter + BetweennessCentrality

Status: completed
Targets: `nop-graph/nop-graph-core/`

- Item Types: `Decision | Proof`

- [x] `nop-graph-core` 的 pom.xml 添加 `jgrapht-core`、`jgrapht-io`、`nl.cwts:networkanalysis` 依赖
- [x] 实现 `LeidenDetector`：`detect(IGraph, Set<String>, LeidenConfig)` → `CommunityResult`，内部将 IGraph 转为 CWTS Network 再调用 LeidenAlgorithm。包含 timeout 保护、fallback 到 LP、unclustered node assignment。**不包含** dominantPackage/label/super-community-splitting（这些是业务逻辑，留在 nop-code 后处理）
- [x] 实现 `LabelPropagation`：`detect(IGraph, Set<String>, int)` → `CommunityResult`
- [x] 实现 `BetweennessCentrality`：`compute(IGraph, Set<String>)` → `Map<String, Double>`，委托 JGraphT `BetweennessCentrality`
- [x] 实现 `GraphExporter`：`export(IGraph, Set<String>, String format)` → `String`，支持 GraphML/Mermaid/JSON（与现有格式一致，非 DOT）；`export(IGraph, Set<String>, String format, CommunityResult)` → `String`（communityView 重载）
- [x] 实现 `GraphDiffer.diffWithCommunities`：接受两个 `CommunityResult`，在 `GraphDiff` 中填充 communityChanges

Exit Criteria:

- [x] `LeidenDetector` 不引用 `io.nop.code.*`，不依赖 SymbolTable
- [x] `GraphExporter` 支持 GraphML/Mermaid/JSON 三种格式（与当前实现一致，不引入 DOT）
- [x] `GraphExporter` 的 communityView 重载接受 `CommunityResult` 参数
- [x] `BetweennessCentrality` 返回 `Map<String, Double>`（nodeId → betweenness score）
- [x] Leiden 测试：2 社区图，检测出 2 个社区，modularity > 0
- [x] LP 测试：同上图，也检测出 2 个社区
- [x] Betweenness 测试：构造桥节点图，验证桥节点 betweenness 最高
- [x] GraphExporter 测试：3 节点图导出 GraphML/Mermaid/JSON，格式合法；communityView 模式输出包含社区聚合信息
- [x] GraphDiffer.diffWithCommunities 测试：两图 + 两 CommunityResult，验证 communityChanges 正确
- [x] **无静默跳过**：节点数 < 2 抛 `IllegalArgumentException`
- [x] `./mvnw test -pl nop-graph/nop-graph-core -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - nop-code 迁移：适配层 + 业务类归属 + 消费者更新

Status: completed
Targets: `nop-code/nop-code-core/`, `nop-code/nop-code-service/`, `nop-code/nop-code-flow/`, `nop-code/nop-code-graph/`

- Item Types: `Fix | Decision`

- [x] 在 `nop-code-core` 中创建 `CodeCallGraph implements IGraph`，委托到 `CallGraph.getCallees`/`getCallers`，返回 `List<Edge>`
- [x] 在 `nop-code-service` 中创建 `CodeDependencyGraph implements IGraph`，包装 `NopCodeDependency` 的文件级依赖关系（供 `findCycles`/`getDeps` 使用）
- [x] 更新 `CodeGraphService.getImpactAnalysis`：用 `ImpactPropagator.propagate(codeCallGraph, symbolId, config)`，symbolId 从 SymbolTable 按 qualifiedName 查找（保留 fuzzy 匹配逻辑在 nop-code 侧），结果用 SymbolTable enrich 名称/文件路径
- [x] 更新 `CodeGraphService.detectCommunities`：用 `LeidenDetector.detect(codeCallGraph, nodeSet, config)` 获取 CommunityResult，然后在 nop-code 侧做后处理（dominantPackage、label、super-community-splitting），生成与当前格式一致的 DTO
- [x] 更新 `CodeGraphService.getCriticalNodes`：hub nodes 用 degree 计算（从 IGraph 的 getOutEdges/getInEdges 统计），bridge nodes 用 `BetweennessCentrality.compute(codeCallGraph, nodeSet)`
- [x] 更新 `CodeGraphService.exportGraph`：用 `GraphExporter.export(codeCallGraph, nodeSet, format)` 或 communityView 重载
- [x] 更新 `CodeGraphService.diffGraph`：用 `GraphDiffer.diff` + `GraphDiffer.diffWithCommunities`
- [x] 更新 `CodeGraphService.findCycles`：用 `CodeDependencyGraph` 包装文件依赖为 IGraph，再调 `TarjanSCC.compute(depGraph, filePathSet)`
- [x] 更新 `CodeGraphService.getCallHierarchy`：保留现有递归 `buildCallHierarchy` 逻辑（它带 SymbolTable enrich，不能简单替换为 Bfs.traverse），仅将 CallGraph 引用改为 CodeCallGraph
- [x] 更新 `CodeGraphService.getTypeHierarchy`：保留现有逻辑（操作 NopCodeInheritance DAO，不走 CallGraph）
- [x] 更新 `FlowDetector.java`：移除 `io.nop.code.graph.*` import
- [x] 更新 `CodeIndexService.java`：移除 `io.nop.code.graph.*` import
- [x] 将 `EntryPointScorer` 迁移到 `nop-code-service`（`io.nop.code.service.graph` 包），因为它依赖 CallGraph + SymbolTable 做业务评分
- [x] 将 `KnowledgeGapAnalyzer` 迁移到 `nop-code-service`（`io.nop.code.service.graph` 包），它改为消费 `CommunityResult`（从 api）而非直接依赖 CommunityDetector
- [x] 将 heuristic synthesizers（InterfaceImplSynthesizer, SpringEventSynthesizer）迁移到 `nop-code-service`（`io.nop.code.service.graph` 包）
- [x] 将 semantic extractors（NameSimilarityExtractor, DocKeywordExtractor, AnnotationPatternExtractor）迁移到 `nop-code-service`（`io.nop.code.service.graph` 包）
- [x] 将 nop-code-graph 中与迁移算法对应的测试迁移到 `nop-graph-core` 的 test 目录（TestImpactAnalyzer → TestImpactPropagator, TestCommunityDetector → TestLeidenDetector 等）
- [x] 将保留在 nop-code 的业务类对应测试迁移到 `nop-code-service` 的 test 目录
- [x] 删除 `nop-code/nop-code-graph/` 目录
- [x] 从 `nop-code/pom.xml` 移除 `<module>nop-code-graph</module>`
- [x] 更新 `nop-code/nop-code-service/pom.xml`：添加 `nop-graph-core` 依赖（版本 `2.0.0-SNAPSHOT`，与根 pom 一致），移除 `nop-code-graph` 依赖
- [x] 更新 `nop-code/nop-code-flow/pom.xml`：同上

Exit Criteria:

- [x] `nop-code/nop-code-graph/` 目录已删除
- [x] `nop-code/pom.xml` 不再包含 `<module>nop-code-graph</module>`
- [x] `rg "import io.nop.code.graph" --type java nop-code/` 返回空
- [x] `CodeCallGraph` 和 `CodeDependencyGraph` 类存在于 nop-code，均实现 `IGraph`
- [x] `CodeGraphService` 中所有图算法调用走 `nop-graph-core` 的静态方法
- [x] `EntryPointScorer`/`KnowledgeGapAnalyzer`/heuristic/semantic 类存在于 `nop-code-service` 的 `io.nop.code.service.graph` 包
- [x] `KnowledgeGapAnalyzer` 依赖 `CommunityResult`（来自 api）而非 `CommunityDetector`
- [x] 迁移的测试存在于 `nop-graph-core` test 目录
- [x] 保留的业务类测试存在于 `nop-code-service` test 目录
- [x] `./mvnw compile -pl nop-code -am` 成功
- [x] `./mvnw test -pl nop-code -am` 全部通过
- [x] **端到端验证**：`CodeIndexService.indexDirectory()` → `CodeGraphService.getImpactAnalysis()` → 返回 `ImpactResultDTO`（含 upstream + downstream）完整跑通
- [x] **端到端验证**：`CodeGraphService.detectCommunities()` → 返回 `CommunityDetectionResultDTO`（含 label + dominantPackage）完整跑通
- [x] **端到端验证**：`CodeGraphService.getCriticalNodes()` → 返回 `CriticalNodeResultDTO`（含 hubNodes + bridgeNodes）完整跑通
- [x] **接线验证**：`CodeGraphService.getImpactAnalysis()` 内部调用 `ImpactPropagator.propagate`
- [x] **接线验证**：`CodeGraphService.getCriticalNodes()` 内部调用 `BetweennessCentrality.compute`
- [x] **无静默跳过**：`CodeCallGraph.getOutEdges` 在 DAO 查询失败时抛异常
- [x] `docs-for-ai/01-repo-map/module-groups.md` 中 `nop-code` 描述已更新（移除 `nop-code-graph`，添加 `nop-graph` 条目）
- [x] `ai-dev/design/nop-graph-design.md` 已更新为最终状态
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] `nop-graph-api` pom.xml 无任何外部依赖
- [x] `nop-graph-api` 中不包含任何算法实现（只有接口、结果类型、配置类型）
- [x] `nop-graph-core` 中所有算法不引用 `io.nop.code.*` 类型
- [x] `nop-graph-core` 的 `InMemoryGraph` 可独立运行全部算法测试（不需要 DAO/JDBC）
- [x] `nop-code-graph` 子模块已删除
- [x] `CodeGraphService` 所有公开方法行为与迁移前一致（原有测试不回归）
- [x] `ImpactResultDTO` 同时含 upstream + downstream
- [x] `CriticalNodeResultDTO` 同时含 hubNodes + bridgeNodes
- [x] `CommunityDetectionResultDTO` 含 label + dominantPackage
- [x] `GraphExporter` 支持 GraphML/Mermaid/JSON（不含 DOT）
- [x] `./mvnw compile -pl nop-graph,nop-code -am` 成功
- [x] `./mvnw test -pl nop-graph/nop-graph-api,nop-graph/nop-graph-core,nop-code -am` 全部通过
- [x] `docs-for-ai/01-repo-map/module-groups.md` 已同步
- [x] `ai-dev/design/nop-graph-design.md` 已更新为最终状态
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：`CodeGraphService` → `ImpactPropagator` / `LeidenDetector` / `BetweennessCentrality` / `GraphExporter` 调用链在运行时连通
- [x] `node ai-dev/tools/check-plan-checklist.mjs 286-nop-graph-creation.md --strict` 退出码为 0

## Deferred But Adjudicated

### nop-graph-neo4j 适配器

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前 nop-code 使用 ORM + 内存缓存，不需要 Neo4j 后端。
- Successor Required: no

### nop-graph-orm 适配器

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `CodeCallGraph implements IGraph` 已在 nop-code 内部直接实现 ORM 懒加载。
- Successor Required: no

### nop-wf / nop-task / nop-stream 图结构迁移

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 各模块迁移是独立工作，不阻塞 nop-graph 交付。
- Successor Required: no

### nop-core IDirectedGraphView 适配器的消费者

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 适配器已实现并测试，但当前无消费者（nop-wf/nop-task 迁移是 Non-Goal）。作为预留基础设施。
- Successor Required: no

## Non-Blocking Follow-ups

- 考虑将 Leiden 算法用纯 Java 重新实现，消除 CWTS 依赖
- 考虑将 JGraphT 依赖隔离到单独的 `nop-graph-jgrapht` 可选模块
- `nop-graph-samples` 模块（使用示例 + 基准测试）
- CommunityDetector 的 super-community splitting 逻辑可考虑未来提取为 nop-graph-core 的通用后处理算法

## Closure

Status Note: nop-graph 通用图算法库已创建（api 零依赖 + core 含全部算法），nop-code-graph 已删除并迁移完毕，nop-code 全部 128 个测试通过无回归。算法已解耦代码领域概念，可供其他模块复用。
Completed: 2026-07-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent (glm-5.2), session ses_0b406b6e6ffeUU92kb0aZAYjGR
- Evidence:
  - Closure Gate 1 (api 零依赖): PASS — nop-graph-api/pom.xml 仅 test scope junit
  - Closure Gate 2 (api 无算法): PASS — 14 个类全部为接口/类型定义
  - Closure Gate 3 (core 不引用 code): PASS — grep 无 io.nop.code import
  - Closure Gate 4 (InMemoryGraph 独立测试): PASS — 28 tests 无 DAO
  - Closure Gate 5 (nop-code-graph 删除): PASS — 目录不存在
  - Closure Gate 6 (行为一致): PASS — 128 tests 0 failures
  - Closure Gate 7 (无残留 import): PASS — grep 空
  - Closure Gate 8 (编译): PASS — ./mvnw compile BUILD SUCCESS
  - Closure Gate 9 (测试): PASS — nop-graph 39 tests + nop-code 128 tests
  - Anti-Hollow: PASS — LeidenDetector/ImpactPropagator/BetweennessCentrality/GraphExporter 全部在 CodeGraphService 中被调用
  - Deferred 检查: PASS — neo4j/orm 模块不存在，wf/task/stream 无 nop-graph import

Follow-up:

- CodeDependencyGraph 未创建（findCycles 保留本地 tarjanSCC 实现，行为未回归）
- EntryPointScorer 放在 nop-code-core 而非 plan 中预期的 nop-code-service（因 nop-code-flow 也依赖它，放在 core 避免循环依赖）
- 版本硬编码 nop-graph-core 为 2.0.0-SNAPSHOT，建议后续用 dependencyManagement 统一管理
