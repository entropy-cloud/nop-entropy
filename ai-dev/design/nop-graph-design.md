# nop-graph 设计文档

> 设计日期: 2026-07-10
> 状态: Draft
> 作者: AI 分析

---

## 1. 动机

### 1.1 现状问题

`nop-code` 中图算法（原 `nop-code-graph` 子模块）直接耦合了代码领域的 `CallGraph` 和 `SymbolTable`，导致：

- 其他模块（`nop-wf`、`nop-task`、`nop-stream`）无法复用这些算法
- 算法实现混入了 `CodeSymbol`、`ExtDataHelper` 等代码领域概念
- `CriticalNodeAnalyzer` 内部将整个 `CallGraph` 复制到 JGraphT 数据结构才能计算 Betweenness
- 单元测试需要启动 DAO/数据库，因为算法并非纯函数

### 1.2 目标

- 将图算法抽取为纯数学计算，与数据源解耦
- 按需提供多个存储后端（InMemory、Neo4j、ORM Lazy）
- `nop-code-graph` 变为薄适配层
- `nop-wf`、`nop-task`、`nop-stream` 等模块可复用同一套算法

---

## 2. 模块结构

```
nop-graph/                              ← 一级目录
├── pom.xml                             ← pom packaging, parent=nop-entropy
│
├── nop-graph-api                       ← 接口 + 契约类型, 零外部依赖
│   ├── pom.xml                         ← 无任何第三方依赖
│   └── src/main/java/io/nop/graph/api/
│       ├── IGraph.java                 ← 核心图接口 (String ID, Edge 边)
│       ├── Edge.java                   ← 通用边类型 (source/target/weight/type)
│       ├── PathQuery.java              ← 路径表达式配置 (不含执行逻辑)
│       ├── ImpactResult.java           ← 影响分析结果 (含 upstream/downstream)
│       ├── ImpactConfig.java           ← 影响分析配置
│       ├── CommunityResult.java        ← 社区检测结果
│       ├── CommunityInfo.java          ← 单个社区信息
│       ├── LeidenConfig.java           ← Leiden 算法配置
│       ├── GraphDiff.java              ← 图差分结果 (含 communityChanges)
│       ├── BfsResult.java              ← BFS 遍历结果
│       └── CriticalNodeResult.java     ← 关键节点分析结果 (hub + bridge)
│
├── nop-graph-core                      ← 算法实现 + 内置图实现
│   ├── pom.xml                         ← 依赖 nop-graph-api + nop-core + JGraphT + CWTS
│   └── src/main/java/io/nop/graph/
│       ├── impl/
│       │   └── InMemoryGraph.java      ← HashMap 实现
│       ├── adapter/
│       │   └── DirectedGraphAdapter.java ← nop-core IDirectedGraphView 适配器
│       └── algorithm/
│           ├── Bfs.java                ← BFS 遍历
│           ├── PageRank.java           ← 节点中心性
│           ├── TarjanSCC.java          ← 强连通分量
│           ├── TopologicalSort.java    ← 拓扑排序
│           ├── ImpactPropagator.java   ← 影响传播 (双向 BFS + 权重)
│           ├── LeidenDetector.java     ← 社区发现 (Leiden, CWTS)
│           ├── LabelPropagation.java   ← 社区发现 (LP)
│           ├── BetweennessCentrality.java ← 介数中心性 (JGraphT)
│           ├── PathQueryExecutor.java  ← PathQuery 执行器
│           ├── GraphDiffer.java        ← 图快照差分
│           └── GraphExporter.java      ← GraphML / Mermaid / JSON 导出
│
├── nop-graph-neo4j                     ← Neo4j 适配器 (可选)
│   ├── pom.xml                         ← 依赖 nop-graph-api + neo4j-java-driver
│   └── Neo4jGraph.java                 ← 委托到 Cypher
│
├── nop-graph-orm                       ← ORM 懒加载适配器 (可选)
│   ├── pom.xml                         ← 依赖 nop-graph-api + nop-dao
│   └── OrmLazyGraph.java               ← 按需 DB 查询
│
└── nop-graph-samples                   ← 使用示例 + 基准测试
    ├── pom.xml
    └── samples/
        ├── RoadGraphSample.java        ← 道路导航图
        └── CodeGraphSample.java        ← 代码调用图
```

### 2.1 依赖拓扑

```
nop-graph-api          (zero deps, pure Java 11+)
       ↑                    ↑                    ↑
nop-graph-core      nop-graph-neo4j       nop-graph-orm
       ↑                    ↑                    ↑
       └────────────────────┼────────────────────┘
                            │
                    (domain modules)
                    nop-code, nop-wf,
                    nop-task, nop-stream
```

---

## 3. 核心接口设计

### 3.1 设计原则

**接口屏蔽一切实现细节。调用者只看得到 String 和 Edge。没有泛型，没有类型参数，没有实现特定的类型泄露。**

### 3.2 `IGraph`

```java
package io.nop.graph;

/**
 * 通用只读有向图接口。
 *
 * 节点以 String ID 标识。算法返回的节点也是 String ID，
 * 调用者通过外部属性存储 enrich 名称、路径等信息。
 *
 * 边为通用 Edge 类型，携带 sourceId/targetId/weight/type。
 * 领域特定属性由 Edge.attrs 承载，算法不读取。
 *
 * 所有实现返回同一类型，调用者无需感知底层是
 * InMemory、Neo4j 还是 ORM。
 */
public interface IGraph {
    List<Edge> getOutEdges(String nodeId);
    List<Edge> getInEdges(String nodeId);
}
```

### 3.3 `Edge`

```java
/**
 * 通用边。所有实现返回同一类型。
 *
 * 只携带拓扑信息（sourceId/targetId）和算法需要的属性
 *（weight/type）。领域特定属性通过 attrs 扩展，算法不读取。
 */
public class Edge {
    private final String sourceId;
    private final String targetId;
    private final double weight;
    private final String type;
    private Map<String, Object> attrs;

    public Edge(String sourceId, String targetId) {
        this(sourceId, targetId, 1.0, null);
    }

    public Edge(String sourceId, String targetId,
                double weight, String type) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.weight = weight;
        this.type = type;
    }

    // getters...
}
```

### 3.4 各实现内部不同、对外一致

| 实现 | 内部 | getOutEdges("abc") 的执行路径 |
|------|------|------------------------------|
| `InMemoryGraph` | `Map<String,List<Edge>>` | `HashMap.get("abc")` → O(1) |
| `Neo4jGraph` | Cypher | `MATCH (n {id:'abc'})-[r]->(m)` → 映射为 Edge |
| `OrmLazyGraph` | SQL | `SELECT * FROM call WHERE caller_id='abc'` → 映射为 Edge |

调用者无感：

```java
IGraph graph;

// 测试
graph = new InMemoryGraph();
// 或生产
graph = new OrmLazyGraph(daoProvider, indexId);
// 或已有 Neo4j
graph = new Neo4jGraph(neo4jDriver, "CALLS");

// 以下代码完全相同
List<Edge> edges = graph.getOutEdges("UserService.createUser");
for (Edge e : edges) {
    System.out.println(e.getTargetId() + " type=" + e.getType());
}
```

### 3.5 算法类

静态方法，不需要泛型：

```java
Set<String> reachable = Bfs.traverse(graph, "start-123", 3);
Map<String, Double> ranks = PageRank.compute(graph, allNodes, 20);
List<Set<String>> sccs = TarjanSCC.compute(graph, allNodes);
List<String> sorted = TopologicalSort.sort(graph, allNodes);
ImpactResult impact = ImpactPropagator.propagate(
    graph, "start-123", ImpactConfig.create().maxDepth(3));
CommunityResult communities = CommunityDetector.leiden(graph, allNodes);
Set<String> matched = PathQuery.create()
    .edgeType("CALLS").maxHops(5)
    .nodeFilter(id -> attrStore.get(id).sensitivity().equals("restricted"))
    .execute(graph, "start-123");
```

需要全图的算法（PageRank、Leiden、TarjanSCC）要求调用者传入节点集。需要节点属性的算法通过外部 `Function<String, Attrs>` 注入。

---

## 4. 各存储后端的实现策略

| 算法 | InMemoryGraph | Neo4jGraph | OrmLazyGraph |
|------|---------------|------------|--------------|
| BFS | HashMap 遍历 | `MATCH (n)-[:T*1..d]->(m)` | 分批取 → 内存 BFS |
| PageRank | 内存迭代 | `CALL gds.pageRank.stream()` | 不支持（需全图） |
| Leiden | 内存 (CWTS库) | `CALL gds.leiden.stream()` | 不支持 |
| TarjanSCC | 内存迭代 | 取全部边 → 应用层 Tarjan | 不支持 |
| ImpactPropagator | BFS 出边 | `MATCH (n)-[:T*1..d]->(m)` | 分批取 → 内存 BFS |
| PathQuery | BFS + Predicate 过滤 | Cypher WHERE 子句 | 分批取 → 内存过滤 |

---

## 5. `nop-code` 迁移方案

### 5.1 当前架构

```
nop-code/nop-code-core/
  CallGraph (class) + SymbolTable (class) ← 耦合
       ↑
nop-code/nop-code-graph/ (已删除)
  ImpactAnalyzer → 依赖 CallGraph + SymbolTable
  CommunityDetector → 依赖 CallGraph + SymbolTable
  CriticalNodeAnalyzer → 依赖 CallGraph + SymbolTable + JGraphT
```

### 5.2 迁移后架构

```
nop-graph/nop-graph-api/
  IGraph ← 接口 (无泛型)
  Edge / ImpactResult / CommunityResult / GraphDiff ← 契约类型

nop-graph/nop-graph-core/
  Bfs / PageRank / TarjanSCC / ImpactPropagator / LeidenDetector ← 算法
  BetweennessCentrality / GraphExporter / GraphDiffer / InMemoryGraph

nop-code/nop-code-core/
  CodeCallGraph implements IGraph ← 薄适配层
  // 算法迁移到 nop-graph-core，删除 nop-code-graph
```

### 5.3 适配层示例

```java
// ORM 懒加载实现（在 nop-code 或 nop-graph-orm 中）
public class CodeCallGraph implements IGraph {
    private final IDaoProvider daoProvider;
    private final String indexId;

    @Override
    public List<Edge> getOutEdges(String symbolId) {
        return daoProvider.daoFor(NopCodeCall.class)
            .findAllByQuery(QueryBean.create()
                .addFilter(FilterBeans.eq("callerId", symbolId)))
            .stream()
            .map(this::toEdge)
            .collect(Collectors.toList());
    }

    private Edge toEdge(NopCodeCall call) {
        Edge e = new Edge(call.getCallerId(), call.getCalleeId());
        e.setAttrs(Map.of("line", call.getLineNumber(),
                          "confidence", call.getConfidence()));
        return e;
    }
}

// 使用: 无需预加载全图
IGraph graph = new CodeCallGraph(daoProvider, indexId);
ImpactResult result = ImpactPropagator.propagate(
    graph, "UserService.createUser",
    ImpactConfig.create().maxDepth(3));
for (String id : result.getImpacted()) {
    CodeSymbol sym = symbolTable.getById(id);
    System.out.println(sym.getName() + " at depth " + result.getDepth(id));
}
```

```java
// 内存实现（在 nop-graph-core 中，用于需要全图的算法）
InMemoryGraph memGraph = new InMemoryGraph();
for (NopCodeCall call : allCalls) {
    memGraph.addEdge(call.getCallerId(), call.getCalleeId(),
                     1.0, "CALLS");
}
Map<String, Double> ranks = PageRank.compute(memGraph,
    memGraph.nodeSet(), 20);
CommunityResult communities = CommunityDetector.leiden(
    memGraph, memGraph.nodeSet());
```

### 5.4 原 `nop-code-graph` 移入 `nop-graph-core` 的算法

| 原文件 | 迁移目标 |
|---------|---------|
| `ImpactAnalyzer` | nop-graph-core: `ImpactPropagator` |
| `CommunityDetector` | nop-graph-core: `LeidenDetector` + `LabelPropagation` |
| `GraphDiffer` | nop-graph-core: `GraphDiffer` |
| `GraphExporter` | nop-graph-core: `GraphExporter` |
| `CriticalNodeAnalyzer` | nop-graph-core: `PageRank` (hub) + `BetweennessCentrality` (bridge) |
| `EntryPointScorer` | 留在 nop-code（业务规则） |
| `KnowledgeGapAnalyzer` | 留在 nop-code（依赖社区结果做业务判断） |

---

## 6. `nop-graph-neo4j` 示例

```java
public class Neo4jGraph implements IGraph {
    private final Driver driver;
    private final String edgeType;

    @Override
    public List<Edge> getOutEdges(String nodeId) {
        try (Session s = driver.session()) {
            return s.run(
                "MATCH (n {id: $id})-[r:" + edgeType + "]->(m) " +
                "RETURN m.id AS target, r.weight AS weight",
                Parameters.parameters("id", nodeId))
               .list(r -> new Edge(nodeId,
                    r.get("target", String.class),
                    r.get("weight", 1.0),
                    edgeType));
        }
    }

    @Override
    public List<Edge> getInEdges(String nodeId) {
        try (Session s = driver.session()) {
            return s.run(
                "MATCH (n)<-[r:" + edgeType + "]-(m {id: $id}) " +
                "RETURN m.id AS source, r.weight AS weight",
                Parameters.parameters("id", nodeId))
               .list(r -> new Edge(
                    r.get("source", String.class),
                    nodeId,
                    r.get("weight", 1.0),
                    edgeType));
        }
    }
}

// Cypher 驱动的 PathQuery（按需走 Neo4j 深度遍历）
// MaxHops=0 表示无限深度
Set<String> result = PathQuery.create()
    .edgeType("CALLS").maxHops(5)
    .nodeFilter(id -> attrStore.get(id).sensitivity().equals("restricted"))
    .execute(neo4jGraph, "start-123");
// execute() 内部如果发现 graph 是 Neo4jGraph，走 Cypher 递归
// 否则走通用 Bfs.traverseFiltered
```

---

## 7. 与 nop-core 现有图接口的关系

`nop-core` (`io.nop.core.model.graph`) 已有可变内存图接口体系。`nop-graph-api` 的设计独立于它：

| 对比维度 | nop-core 图 | nop-graph-api |
|---------|-------------|---------------|
| 定位 | 可变内存图数据结构 | 只读算法查询抽象 |
| 节点语义 | 顶点对象 (`V`) | 顶点 **ID** (`String`) |
| 边语义 | `IEdge<V>` (source/target 返回 V 对象) | `Edge` (source/target 返回 String ID) |
| vertexSet() | 必须暴露 | **不暴露** |
| 算法 | BFS/DFS/TopoSort 内建 | PageRank/Leiden/ImpactPropagator/GraphDiff |
| 依赖 | nop-core 内部 | 零外部依赖 |

两者正交。`nop-graph-core` 提供单向适配器：

```java
public static IGraph fromDirectedGraph(IDirectedGraphView<?> graph) {
    return new IGraph() {
        @Override
        public List<Edge> getOutEdges(String nodeId) {
            List<? extends IEdge<?>> edges = graph.getOutwardEdges(nodeId);
            return edges.stream().map(e -> new Edge(
                String.valueOf(e.getSource()),
                String.valueOf(e.getTarget())
            )).collect(Collectors.toList());
        }

        @Override
        public List<Edge> getInEdges(String nodeId) {
            // ...
        }
    };
}
```

---

## 8. 依赖管理与发布

| 模块 | 定位 | 依赖 |
|------|------|------|
| `nop-graph-api` | **必须发布**，公共 API | 零依赖 |
| `nop-graph-core` | **必须发布**，零依赖场景使用 | nop-graph-api |
| `nop-graph-neo4j` | 可选，按需引入 | nop-graph-api + neo4j-java-driver |
| `nop-graph-orm` | 可选，按需引入 | nop-graph-api + nop-dao |

所有模块受根 `pom.xml` 的 `<modules>` 管控，参与统一版本发布。

---

## 9. 后续步骤

1. ~~创建 `nop-graph/nop-graph-api`：`IGraph` + `Edge` + 契约类型~~ → Phase 1
2. ~~创建 `nop-graph/nop-graph-core`：算法实现 + `InMemoryGraph`~~ → Phase 2/3
3. ~~迁移原 nop-code-graph 算法到 `nop-graph-core`~~ → Phase 3/4
4. ~~`nop-code` 删除原 nop-code-graph，改为 `CodeCallGraph implements IGraph`~~ → Phase 4
5. 整合 `nop-wf` / `nop-task` 的图结构统一使用 `IGraph`（Deferred）
6. 可选：`nop-graph-neo4j` 适配器（Deferred）
