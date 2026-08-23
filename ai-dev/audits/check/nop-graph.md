# nop-graph 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-graph
- 文件数: 27（src/main/java；任务预估约 30，实际 api 14 + core 13，另有 3 个测试文件不在范围）
- 覆盖范围声明: 27 个主代码文件全部逐行深读（nop-graph-api 14 个、nop-graph-core 13 个），无遗漏。对 TarjanSCC / PageRank / Bfs / TopologicalSort / ImpactPropagator / LeidenDetector / GraphDiffer / PathQueryExecutor 手工推演了小规模用例（2-环、链+环、自环、孤立点、空集、maxDepth/maxNodes 边界）。对关键第三方行为做了可执行验证：CWTS Network 1.3.0 无向图双向边计数、JGraphT 1.5.2 重复 addEdge / SimpleGraph 自环行为（临时验证程序，已删除）。为确认触发路径现实性，读取了调用方 nop-code 的 CodeGraphService 与 CodeCallGraph 适配器（只读，未修改）。grep 扫描了空 catch、bare RuntimeException、printStackTrace、System.out、synchronized，均无命中（除 InMemoryGraph 自身方法级 synchronized）。测试代码（TestAlgorithms / TestCommunityAlgorithms / TestApiTypes）仅用于解释缺陷未被发现的原因，不在审计范围。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 4 |
| P2 | 7 |
| P3 | 10 |

## 发现列表

### [P0] GraphDiffer 边差分完全失效：Edge 无 equals/hashCode，两图实例 diff 时所有边被判为新增+删除

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/GraphDiffer.java:49-53`，根因在 `nop-graph/nop-graph-api/src/main/java/io/nop/graph/api/Edge.java`
- **维度**: D1
- **证据**:
```java
// GraphDiffer.java
Set<Edge> addedEdges = new LinkedHashSet<>(targetEdges);
addedEdges.removeAll(baselineEdges);
Set<Edge> removedEdges = new LinkedHashSet<>(baselineEdges);
removedEdges.removeAll(targetEdges);

// Edge.java —— 未重写 equals/hashCode，沿用 Object 身份语义
public class Edge {
    private final String sourceId;
    private final String targetId;
    ...
```
- **现状**: `Edge` 未重写 `equals`/`hashCode`，集合运算退化为对象身份比较。`collectEdges` 从 `graph.getOutEdges(node)` 收集边，而真实调用链（nop-code 的 `CodeCallGraph.getOutEdges`，见 `nop-code/nop-code-core/src/main/java/io/nop/code/core/graph/CodeCallGraph.java:32`）每次调用 `new Edge(nodeId, callee, 1.0, "CALLS")` 创建新对象；即使同为 `InMemoryGraph`，`addEdge` 也每次 `new Edge(...)`。因此 baseline 与 target 两个图中"相同的逻辑边"永远不相等，`removeAll` 全部失配。
- **风险**: 任何两个图实例做 diff（含两个内容完全相同的图），`addedEdges` = target 全部边、`removedEdges` = baseline 全部边，`GraphDiff.isEmpty()` 恒为 false。触发路径现实：`CodeGraphService.diffGraph(baselineIndexId, targetIndexId)`（`nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java:252`）正是把两个不同 `CallGraph` 包成 `CodeCallGraph` 传入。现有测试 `testGraphDiffer` 只断言了节点差分（String 有 equals），未断言边差分，故缺陷被掩盖。
- **建议**: 为 `Edge` 基于 sourceId/targetId/weight/type 实现 `equals`/`hashCode`（attrs 不参与）；或 GraphDiffer 改用 `sourceId + "->" + targetId` 字符串键做差分。同时为"两个相同内容图实例 diff 返回空 diff"补回归测试。
- **误报排除**: 已确认 `Edge.java` 全文无 equals/hashCode；已确认调用方 `CodeCallGraph` 每次返回新 Edge 实例；已确认测试未覆盖边断言。唯一不触发场景是同一 `InMemoryGraph` 实例自 diff（共享 Edge 对象），现实中 diff 语义是比较两个快照，不成立。

---
> **处置（fix-ai-check 分支，2026-08-22）**: 确认属实，已修复。为 `Edge` 基于身份字段 sourceId/targetId/weight/type 手写实现 `equals`/`hashCode`（mutable 的 attrs 不参与，javadoc 已注明值语义契约），`GraphDiffer` 的 `Set<Edge>` 差分随之恢复值语义；全仓库核查确认无依赖对象身份语义的 Edge 容器/序列化用法。测试：`nop-graph-core` `TestAlgorithms#testGraphDifferIdenticalGraphs`（修复前两个相同拓扑图实例 diff 返回 2 条边全部 added+removed、isEmpty()=false）、`TestAlgorithms#testGraphDifferEdgeChange`（修复前改一条边报告 2 删除 2 新增）。模块全部测试通过（nop-graph 30/30）。

### [P1] PathQueryExecutor.edgeMatches 只检查 start 的直接出边，edgeType 过滤对多跳节点全部错误拒绝

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/PathQueryExecutor.java:39-53`
- **维度**: D1
- **证据**:
```java
return Bfs.traverseFiltered(graph, start, maxHops,
        node -> effectiveFilter.test(node) && edgeMatches(graph, start, node, edgeType));

private static boolean edgeMatches(IGraph graph, String from, String to, String edgeType) {
    if (edgeType == null) {
        return true;
    }
    for (Edge edge : graph.getOutEdges(from)) {
        if (edge.getTargetId().equals(to) && edgeType.equals(edge.getType())) {
```
- **现状**: 边类型过滤被塞进节点谓词实现，`from` 参数硬编码传 `start`。BFS 扩展到 2 跳及以后节点时，判断的却是"start 是否有直达该节点且类型匹配的边"，而不是"路径上的边类型是否匹配"。例如 `A→B(type=call), B→C(type=call)`，`execute(graph,"A",query.setEdgeType("call"))` 中 C 会因 A 无直达 C 的边被拒绝且不入队，返回 `{B}` 而非 `{B,C}`。
- **风险**: 只要 edgeType 非 null 且结果超过 1 跳，返回集就是错的。当前 nop-code 未调用该类，属公共 API 的功能性错误，未来接入即产生错误分析结论。另有次生问题：每个候选节点都线性扫描 `getOutEdges(start)`，复杂度 O(E·deg(start))。
- **建议**: 边类型过滤应在 BFS 扩展边时进行（给 `Bfs.traverseFiltered` 增加边谓词重载），而不是事后按节点回查 start 的出边。
- **误报排除**: 已通读 PathQueryExecutor 全文并手工推演 2 跳用例确认；`from` 实参在唯一调用点硬编码为 `start`，无其他传参路径。

### [P1] Bfs.traverseFiltered 返回集包含被 nodeFilter 拒绝的节点（过滤语义反转）

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/Bfs.java:125-137`
- **维度**: D1, D8
- **证据**:
```java
for (Edge edge : graph.getOutEdges(nodeId)) {
    String target = edge.getTargetId();
    if (!visited.contains(target)) {
        visited.add(target);                    // 无条件加入 visited
        if (nodeFilter == null || nodeFilter.test(target)) {
            queue.add(new String[]{target, String.valueOf(depth + 1)});  // 仅控制是否展开
        }
    }
}
...
visited.remove(start);
return visited;                                 // 被拒绝的节点也在返回集中
```
- **现状**: nodeFilter 未通过的节点仍被加入 `visited` 并随返回集输出，filter 只影响"是否继续展开"。方法名 `traverseFiltered` 与参数语义（`nodeFilter`）都指向"返回满足条件的节点"，实际返回的是"可达且不超过深度的所有节点（含不满足条件者）"。
- **风险**: 调用者拿到包含不匹配节点的结果集。直接受害方即 PathQueryExecutor（其"返回匹配的节点 ID 集合"的 javadoc 契约因此双重失真）。两个缺陷叠加使路径查询功能整体不可用。
- **建议**: 明确语义并统一实现：若 filter 是遍历剪枝 + 结果过滤，应只把通过 filter 的 target 放入返回集（可用单独的 result 集合与 visited 区分）；若 filter 仅是剪枝，应在 javadoc 明示并改名（如 traversePruned）。
- **误报排除**: 已逐行推演：A→B(filter 拒绝) 时返回 `{B}`。非臆测。

### [P1] PageRank 权重处理未归一化：weight≠1 时 rank 总和不为 1 且可指数发散

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/PageRank.java:59-68`
- **维度**: D1
- **证据**:
```java
for (Edge edge : graph.getInEdges(node)) {
    String source = edge.getSourceId();
    if (ranks.containsKey(source)) {
        int sourceOutDegree = graph.getOutEdges(source).size();
        if (sourceOutDegree > 0) {
            double weight = edge.getWeight();
            rank += DAMPING_FACTOR * ranks.get(source) * weight / sourceOutDegree;
        }
    }
}
```
- **现状**: 贡献公式为 `0.85 · rank(source) · weight / sourceOutDegree`。权重没有按出边权重和归一化（标准加权 PageRank 应为 `rank(source) · w / Σw_out`）。当所有边 weight=2 时，每轮迭代 rank 总和 ≈ 0.85·2 + 0.15 = 1.85，随迭代指数膨胀；weight=0.5 时系统性衰减。另外子图语义下出边流向 `nodes` 集合外的 rank 直接蒸发（`sourceOutDegree` 按全图出度计，出边全在集合外的节点既不算 dangling 也不回收），rank 总和持续小于 1，javadoc 未说明该近似。
- **风险**: 只要调用者通过 `addEdge(src, tgt, w, type)` 或 Edge 构造设置非 1 权重，评分即错误甚至溢出为 Infinity，排序结论失真。当前 nop-code 未调用 PageRank，属公共算法 API 的潜在错误结论。
- **建议**: 按 `w / Σ(出边权重和)` 归一化；子图场景对出边全部指向集合外的节点按 dangling 处理，或在 javadoc 明示局部 PageRank 近似语义。
- **误报排除**: 已手工推演 weight=2 的迭代总量（1.85×发散）与 weight=1 的正确退化；已确认 `Edge.getWeight` 默认 1.0、无其他归一化代码。

### [P1] LeidenDetector 有向图转无向网络时保留双向边对，CWTS 中同一逻辑边权重被加倍

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/LeidenDetector.java:60-73`
- **维度**: D1
- **证据**:
```java
Set<String> seenEdges = new HashSet<>();
for (String node : indexNodeMap) {
    for (Edge edge : graph.getOutEdges(node)) {
        String target = edge.getTargetId();
        if (nodeIndexMap.containsKey(target)) {
            int src = nodeIndexMap.get(node);
            int tgt = nodeIndexMap.get(target);
            String key = src + "_" + tgt;
            if (src != tgt && seenEdges.add(key)) {
                edgeList.add(new int[]{src, tgt});
            }
        }
    }
}
...
Network network = new Network(nNodes, false, edges, false, false);
```
- **现状**: 去重键是 `"src_tgt"`，有向边 A→B 与 B→A 生成两个不同键、两条边都进入 edgeList，然后喂给 `directed=false` 的 CWTS Network。经可执行验证（CWTS networkanalysis 1.3.0）：无向 Network 同时接收 (0,1) 与 (1,0) 时 `nEdges=2`、`neighbors(0)=[1,1]`、权重 `[1.0,1.0]`，即同一逻辑边被计为两条平行边、总权重 2。
- **风险**: 代码调用图中互调（A 调 B 且 B 调 A）极常见，这类双向耦合在 Leiden 模块度优化中的权重被系统性放大为单向的 2 倍，社区划分与 modularity 数值被偏置。`LeidenDetector.detect` 被 `CodeGraphService.detectCommunities` 现实调用，影响 nop-code 社区检测结论。此外 `Edge.weight` 在转换中被完全丢弃，调用者设置的权重无效果。
- **建议**: 无向化时以 `min(src,tgt) + "_" + max(src,tgt)` 归一化去重键；如需支持权重，将边权传入 CWTS Network 的 edgeWeights。
- **误报排除**: 已用 CWTS jar 实测确认双边计入行为；代码中无其他合并逻辑（`src != tgt` 只排除自环）。

### [P2] Leiden 失败降级到 LabelPropagation 后，algorithmUsed 仍被硬编码上报为 "LEIDEN"

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/LeidenDetector.java:75-84`（结合 135-139）
- **维度**: D8
- **证据**:
```java
} catch (Exception e) {
    LOG.warn("Leiden algorithm failed, falling back to LabelPropagation", e);
    return LabelPropagation.detect(toIGraph(graph, indexNodeMap), ...);  // algorithmUsed="LABEL_PROPAGATION"
}
...
return new CommunityResult(result.getCommunities(),
        nodes.size(),
        result.getTotalCommunities(),
        result.getAverageCohesion(),
        result.getModularity(),
        "LEIDEN",                        // 无条件覆盖
        System.currentTimeMillis() - startTime);
```
- **现状**: `runLeiden` 异常或空社区时降级跑 LabelPropagation，但外层 `detect` 重建 CommunityResult 时把 `algorithmUsed` 硬编码为 `"LEIDEN"`。降级结果（modularity=0.0、算法特性不同）被错误标注为 Leiden 产出。
- **风险**: 使用方（如 nop-code 的 `runCommunityDetection`）依据 algorithmUsed 判断结果来源时被误导，排障时也会误判实际执行路径。
- **建议**: 直接返回 `runLeiden` 的 CommunityResult，只在外层补充 processingTime（为 result 重建时透传 `result.getAlgorithmUsed()`）。
- **误报排除**: 已确认 fallback 返回值与外层重建的字段来源；`"LEIDEN"` 字面量在 detect 中无条件使用。

### [P2] ImpactPropagator javadoc 声称"支持权重和深度衰减"，实现为纯 BFS，无任何权重/衰减逻辑

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/ImpactPropagator.java:23-24`（对照 59-101）
- **维度**: D8
- **证据**:
```java
 * 支持权重和深度衰减，以及 maxNodes 限制。
 ...
for (Edge edge : edges) {
    String neighbor = forward ? edge.getTargetId() : edge.getSourceId();
    if (!visited.contains(neighbor)) {
        visited.add(neighbor);
        impacted.add(new ImpactedNode(neighbor, depth + 1));
```
- **现状**: `traceDirection` 从不读取 `edge.getWeight()`，ImpactedNode 也只有 nodeId/depth 两个字段，无权重、无衰减分值。javadoc 承诺的能力不存在。另 `evaluateRisk` 的阈值（50/20/5、深度 5/3）为硬编码魔数，无文档说明。
- **风险**: 使用方按文档预期加权影响分时落空；风险等级不可配置。该方法被 `CodeGraphService.getImpactAnalysis` 现实调用，风险等级直接影响对外输出。
- **建议**: 修正 javadoc 为纯 BFS 语义，或补齐权重衰减实现；evaluateRisk 阈值提为可配置常量并注释依据。
- **误报排除**: 已通读全文确认无 weight 引用（grep 验证）。

### [P2] PathQuery.minHops 配置被 PathQueryExecutor 完全忽略

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/PathQueryExecutor.java:28-41`，定义于 `nop-graph/nop-graph-api/src/main/java/io/nop/graph/api/PathQuery.java:13`
- **维度**: D8
- **证据**:
```java
String edgeType = query.getEdgeType();
Predicate<String> nodeFilter = query.getNodeFilter();
int maxHops = query.getMaxHops();
// getMinHops() 在整个 nop-graph 主代码中零引用（grep 验证，仅 api 测试访问 getter）
```
- **现状**: `PathQuery.minHops`（默认 1）是公开配置项，执行器只使用 maxHops。
- **风险**: 调用者设置 `setMinHops(2)` 期望过滤掉 1 跳节点，实际静默无效，得到包含 1 跳节点的结果。
- **建议**: 在 execute 中基于 BFS 深度过滤（可复用 `BfsResult.getDepth`），或在 minHops 未实现前从 API 中移除该配置避免契约欺骗。
- **误报排除**: grep 全模块确认 `getMinHops` 无主代码调用。

### [P2] DirectedGraphAdapter 使用 raw type，非 String 顶点图静默失效

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/adapter/DirectedGraphAdapter.java:27-36`
- **维度**: D8
- **证据**:
```java
@SuppressWarnings({"unchecked", "rawtypes"})
public static IGraph fromDirectedGraph(IDirectedGraphView graph) {
    ...
    return new IGraph() {
        @Override
        public List<Edge> getOutEdges(String nodeId) {
            List<? extends io.nop.core.model.graph.IEdge<?>> edges = graph.getOutwardEdges(nodeId);
```
- **现状**: `IDirectedGraphView<V,E>` 被用作 raw type，查询时把 String nodeId 直接传给 `getOutwardEdges(V)`。javadoc 声称"顶点类型 V 通过 String.valueOf 转为 String ID"，该转换只对出边 source/target 方向成立；若底层图顶点类型非 String（如 Integer），`Map<V,…>.get("1")` 之类查询静默返回空，整个适配器无结果、无报错。
- **风险**: 接入 V≠String 的 nop-core 图视图时所有算法返回空结果，属于静默错误。
- **建议**: 签名改为 `fromDirectedGraph(IDirectedGraphView<V,?> graph, Function<V,String> idMapper)`，在顶点映射层显式转换。
- **误报排除**: 已核对 `IDirectedGraphView` 泛型签名（nop-core `IDirectedGraphView.java:39`）；确认无任何 V→String 的查询侧转换逻辑。

### [P2] TopologicalSort 用 ArrayList.remove(0) 作队列，复杂度 O(V²)

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/TopologicalSort.java:52-73`
- **维度**: D6
- **证据**:
```java
List<String> queue = new ArrayList<>();
...
while (!queue.isEmpty()) {
    String node = queue.remove(0);   // ArrayList 头部弹出，每次 O(n) 搬移
```
- **现状**: Kahn 算法本应 O(V+E)，`ArrayList.remove(0)` 每次触发 System.arraycopy，整体退化为 O(V²)。
- **风险**: 模块定位明确"未来供 nop-wf/nop-task 复用"，拓扑排序是工作流核心操作，大图（万级节点）时性能明显劣化。
- **建议**: 改用 `ArrayDeque<String>`（offer/poll）。
- **误报排除**: 已确认 queue 声明为 ArrayList 且唯一出队方式为 remove(0)；结果正确性推演无问题（含重边、自环抛环异常），纯性能问题。

### [P2] TarjanSCC/PageRank 每帧/每边重复调用 getOutEdges，无缓存适配器下退化为 O(deg²)/节点

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/TarjanSCC.java:77`，`nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/PageRank.java:49,62`
- **维度**: D6
- **证据**:
```java
// TarjanSCC.strongconnect —— 每次弹出帧（含所有 returning 帧）都重新取边
List<Edge> outEdges = graph.getOutEdges(v);

// PageRank —— 每次迭代先全局扫一遍出度，再对每条入边重复取 source 出度
int outDegree = graph.getOutEdges(node).size();          // L49
int sourceOutDegree = graph.getOutEdges(source).size();  // L62，处于双层循环内
```
- **现状**: IGraph 契约未承诺缓存。`InMemoryGraph.getOutEdges` 每次防御性拷贝 O(deg)；`CodeCallGraph`/`DirectedGraphAdapter` 每次全新构建 Edge 列表。TarjanSCC 对节点 v 调用 getOutEdges 的次数 = 1 + 递归子节点数 ≤ 1+deg(v)，总开销 Σ(1+deg)·deg，稠密图从 O(V+E) 退化一个因子；PageRank 每迭代 E 次 getOutEdges(source)，总计 iterations·E·avgDeg。
- **风险**: 大图分析（nop-code 的调用图）下 CPU 与分配压力放大；TarjanSCC 正确性不受影响（已推演），仅性能。
- **建议**: 算法入口先做一次邻接快照（`Map<String,List<Edge>>` 或 int 索引压缩），迭代过程中不再触图。
- **误报排除**: 已统计 TarjanSCC 帧推送路径（父帧 returning 次数=子节点数）；PageRank 双重循环内调用已逐行确认。

### [P2] GraphExporter Mermaid 输出：sanitizeMermaidId 造成节点 ID 冲突合并，label 未转义引号

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/GraphExporter.java:120-124, 272-279`
- **维度**: D1, D5
- **证据**:
```java
sb.append("    ").append(sanitizeMermaidId(node))
        .append("[\"").append(shortId(node)).append("\"] --> ")   // shortId 未转义 " 与换行
        ...

private static String sanitizeMermaidId(String id) {
    return id.replaceAll("[^a-zA-Z0-9_]", "_");
}

private static String shortId(String id) {
    int dot = id.lastIndexOf('.');
    return dot > 0 ? id.substring(dot + 1) : id;
}
```
- **现状**: 节点 ID 中所有非 `[a-zA-Z0-9_]` 字符折叠为 `_`，`a.b`、`a_b`、`a$b` 映射到同一 Mermaid 节点 ID，两个不同源节点被合并渲染为一条；label（shortId）直接拼入双引号内，未转义 `"`、换行等，会破坏 Mermaid 语法。代码符号 ID 常含 `.`、`$`、`<>`，冲突概率高。
- **风险**: `exportGraph` 被 `CodeGraphService` 现实调用，Mermaid 视图渲染错误/无法渲染。
- **建议**: sanitize 冲突时可追加稳定数字后缀（如按 nodes 顺序编号），label 复用类似 escapeJson 的转义（至少处理 `"` 与换行）。
- **误报排除**: 已通读 exportMermaid 全分支确认无其他转义步骤；`sanitizeMermaidId` 的字符类确实折叠所有差异字符。

### [P3] PageRank 的 CONVERGENCE_THRESHOLD 是死常量，无收敛判定

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/PageRank.java:17`
- **维度**: D8, D6
- **证据**:
```java
private static final double CONVERGENCE_THRESHOLD = 1e-6;
```
- **现状**: 全模块 grep 仅此一处出现，迭代固定跑满 `iterations` 次，从不比较前后差值。
- **风险**: 暗示了未实现的收敛语义；固定迭代在大图上浪费算力，小迭代数下精度不足。
- **建议**: 实现收敛早停（每轮计算 max|new-old| < 阈值时退出）或删除常量。
- **误报排除**: grep 验证零引用。

### [P3] Bfs 用 String[] 携带深度经 Integer.parseInt 往返；traverseWithDepth 对 maxDepth<=0 的行为与 traverse 不一致

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/Bfs.java:37-44, 65-76`
- **维度**: D8, D6
- **证据**:
```java
queue.add(new String[]{start, "0"});
...
int depth = Integer.parseInt(current[1]);
```
- **现状**: 深度以字符串编码进 `String[]`，每次出队 parseInt、入队 String.valueOf，产生无谓分配与解析。`traverse` 对 `maxDepth<=0` 返回空集，`traverseWithDepth` 无该检查，返回 `{start:0}`（起点 depth=0 ≥ maxDepth 直接剪枝），同一类参数两个方法语义不一致。
- **风险**: 无功能错误（推演确认 BFS 层序正确、visited.remove(start) 正确排除起点），仅可读性/一致性问题。
- **建议**: 队列元素改为 `record NodeDepth(String id, int depth)` 或并行队列；traverseWithDepth 补 maxDepth<=0 的显式处理并在 javadoc 说明。
- **误报排除**: 已逐行推演三个遍历方法的层序与去重行为，无正确性问题。

### [P3] 三处 catch(IllegalArgumentException) 注释 "duplicate edge" 为死代码/语义误导

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/BetweennessCentrality.java:44-48`，`nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/LabelPropagation.java:46-50`，`nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/GraphExporter.java:219-223`
- **维度**: D4
- **证据**:
```java
try {
    jgraph.addEdge(node, target);
} catch (IllegalArgumentException e) {
    // duplicate edge, skip
}
```
- **现状**: 经可执行验证（JGraphT 1.5.2）：`DefaultDirectedGraph`/`SimpleGraph` 对已存在的顶点对 `addEdge` 返回 null 而不抛异常，"duplicate edge" 分支实际不可达。LabelPropagation 使用 SimpleGraph 时真正会抛 IllegalArgumentException 的是自环（"loops not allowed"），被该 catch 吞掉——行为上跳过自环对社区检测无害，但注释错误描述了被吞异常的来源，掩盖真实控制流。
- **风险**: 维护性误导；未来 JGraphT 行为变化时无测试保护。
- **建议**: 删除 try-catch，改为判断 `addEdge(...) == null` 处理重边；LabelPropagation 若有意忽略自环，显式 `if (!node.equals(target))` 并注释。
- **误报排除**: 已用 JGraphT 1.5.2 jar 实测 `e2=false, edgeSetSize=1`（返回 null 不抛）与 SimpleGraph 自环抛 "loops not allowed"。

### [P3] LeidenDetector：toIGraph 恒等死函数；catch(Exception) 过宽吞编程错误；超时后工作线程不响应中断继续占 CPU

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/LeidenDetector.java:191-193, 135-139, 195-203`
- **维度**: D4, D2
- **证据**:
```java
private static IGraph toIGraph(IGraph graph, List<String> nodes) {
    return graph;      // 恒等
}
...
} catch (Exception e) {                       // NPE 等编程错误也降级
    LOG.warn("Leiden algorithm failed, falling back to LabelPropagation", e);
    return LabelPropagation.detect(...);
}
...
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
- **现状**: toIGraph 是无操作的占位函数；fallback 捕获所有 Exception（含本模块自身的 NPE/CCE），编程错误被静默降级为 LabelPropagation 结果；CWTS findClustering 为 CPU 密集循环不检查中断位，超时 shutdownNow 后该线程仍持续运行至自然结束，同时 fallback 的 LabelPropagation 并行占用 CPU。
- **风险**: 缺陷被降级掩盖（难排查）；超时场景瞬时双倍 CPU。均非数据错误。
- **建议**: 删除 toIGraph；catch 收窄为预期异常类型（如 CWTS 内部异常 + TimeoutException），其余抛出；在文档标注超时语义为"尽力中断"。
- **误报排除**: 三段代码均已通读；CWTS 中断响应未实测（保守表述为"可能持续运行至自然结束"）。

### [P3] minCommunitySize 过滤后 CommunityResult 统计字段与社区内容不一致

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/LeidenDetector.java:78-84, 159-161`
- **维度**: D8
- **证据**:
```java
if (clusterNodes.size() < config.getMinCommunitySize()) {
    continue;   // 小社区整体丢弃，其节点不出现在任何返回社区中
}
...
return new CommunityResult(result.getCommunities(),
        nodes.size(),                     // totalSymbols 仍为全量节点数
        result.getTotalCommunities(), ...);
```
- **现状**: 设置 minCommunitySize>0 时，被过滤小社区的节点不在任何 `CommunityInfo` 中，但 `totalSymbols` 仍按全量 nodes 计数（Leiden 的 modularity 也是过滤前全量聚类计算的）。
- **风险**: 使用方按"totalSymbols = 社区覆盖节点数"理解时产生偏差；当前默认 minCommunitySize=0 无影响。
- **建议**: 要么把 totalSymbols 改为实际覆盖节点数，要么在 javadoc 说明过滤语义。
- **误报排除**: 代码路径确认（convertClustering 过滤 → detect 透传 nodes.size()）。

### [P3] GraphExporter communityView 中 Mermaid/JSON 路径为未入社区节点生成 "comm_-1" 幽灵边，与 GraphML 路径行为不一致

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/GraphExporter.java:98-101, 146-149`（对照 244-246）
- **维度**: D8
- **证据**:
```java
// Mermaid / JSON 路径：
int srcComm = nodeToCommunity.getOrDefault(node, -1);
...
if (srcComm != tgtComm) {
    edges.add("comm_" + srcComm + " --> comm_" + tgtComm);   // 可能出现 comm_-1

// GraphML 路径（buildCommunityGraph）：
Integer srcComm = nodeToCommunity.get(node);
if (srcComm != null && tgtComm != null && !srcComm.equals(tgtComm)) {  // 未入社区直接跳过
```
- **现状**: 节点不在任何社区（如 minCommunitySize 过滤后）时，Mermaid/JSON 输出引用未声明的 `comm_-1` 节点，而 GraphML 路径静默跳过同样的边，三种格式行为不一致。
- **风险**: 渲染/解析端出现来源不明的 -1 社区引用；无数据损坏。
- **建议**: 三条路径统一（建议都对 null 跳过）。
- **误报排除**: 已对照两种路径的 null 处理代码。

### [P3] GraphExporter 的 NopException 使用未注册的字符串错误码，且无 .param() 上下文

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/GraphExporter.java:80`
- **维度**: D7
- **证据**:
```java
throw new NopException("nop.err.graph.export-failed", e, false, true);
```
- **现状**: 使用 `(String errorCode, ...)` 构造而非 `ErrorCode` 常量；`nop.err.graph.export-failed` 在全仓库错误码资源中无定义（grep 零命中）；未携带 format 等参数上下文（平台规范建议 `.param(...)`）。cause 已保留，未丢失。
- **风险**: 错误码无法本地化、无法在错误码字典中追溯；与"公共 API 用 NopException + ErrorCode"的平台两档策略不完全一致。
- **建议**: 定义 `GraphErrorCode` 枚举/常量并按规范构造；至少补充 `.param("format", format)`。
- **误报排除**: 已 grep 全仓库 properties 资源确认错误码未定义；已核对 NopException 构造器签名确实存在该 4 参重载（编译无问题）。

### [P3] GraphDiffer.diffWithCommunities 未校验 communityMap 参数，null 时抛裸 NPE

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/GraphDiffer.java:70-88`
- **维度**: D4
- **证据**:
```java
public static GraphDiff diffWithCommunities(IGraph baselineGraph, Set<String> baselineNodes,
                                             Map<String, Integer> baselineCommunityMap, ...) {
    GraphDiff baseDiff = diff(baselineGraph, baselineNodes, targetGraph, targetNodes);  // 只校验4个参数
    ...
    Integer oldCommunity = baselineCommunityMap.get(node);   // communityMap 为 null 时 NPE
```
- **现状**: `diff` 对 4 个图/节点参数有 IllegalArgumentException 校验，`diffWithCommunities` 新增的两个 communityMap 参数无校验，null 时在循环内抛 NullPointerException。
- **风险**: 错误信息不友好，与同类的参数校验风格不一致。
- **建议**: 补 null 校验（或允许 null 时跳过社区比较）。
- **误报排除**: 已通读方法确认无 null 防护。

### [P3] TarjanSCC 结果可包含 nodes 集合之外的节点，javadoc 未说明

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/TarjanSCC.java:32-51`
- **维度**: D8
- **证据**:
```java
/**
 * @param nodes 要分析的节点集
 * @return SCC 列表，每个 SCC 是一个节点 ID 集合
 */
public static List<Set<String>> compute(IGraph graph, Set<String> nodes) {
    ...
    strongconnect(graph, node, ...);   // 会沿出边递归到集合外节点并纳入 SCC
```
- **现状**: 算法从 nodes 出发但跟随全部出边，SCC 可包含 nodes 外节点（如 nodes={A}，A→B→A 时返回 {A,B}）。语义本身合理（SCN 需完整），但 "@param nodes 要分析的节点集" 未表达"以 nodes 为起点的可达子图"语义。
- **风险**: 使用方按"结果 ⊆ nodes"假设处理时会意外。
- **建议**: javadoc 明确"从 nodes 出发可达的子图上计算 SCC，分量可能包含集合外节点"。算法本身已推演正确（2-环/链+环/自环/孤立点均通过）。
- **误报排除**: 已手工推演 nodes={A}+回边用例确认行为。

### [P3] LeidenDetector 使用无种子 Random，社区检测结果跨运行不可复现

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/LeidenDetector.java:103-108`
- **维度**: D1
- **证据**:
```java
LeidenAlgorithm leiden = new LeidenAlgorithm(
        config.getResolution(),
        config.getMaxIterations(),
        0.01,
        new Random()          // 未固定种子
);
```
- **现状**: LeidenConfig 无随机种子配置，`new Random()` 每次调用不同。
- **风险**: 同一图两次 `detectCommunities` 可能给出不同社区划分（Leiden 随机性是算法固有，但分析工具通常期望可复现输出以便 diff 与回归）。
- **建议**: LeidenConfig 增加 `seed` 选项（默认固定值，如 42），传入 `new Random(seed)`。
- **误报排除**: 已确认 LeidenAlgorithm(double,int,double,Random) 构造存在且 Random 直接影响算法随机步。

## 补充说明（无缺陷项，供参考）

- TarjanSCC 迭代式实现经多组用例推演正确：2-环、A→环 结构、自环（单节点 SCC）、孤立点、重边均正确；`lowLink.get(v).equals(index.get(v))` 用 equals 而非 == 比较 Integer，避开了装箱陷阱；`returning` 帧的 edgeIdx-1 回溯逻辑正确。
- InMemoryGraph 全方法 synchronized + 防御性拷贝，单一实例的构建与查询并发安全（未做快照隔离，遍历期间他线程加边不保证一致性，符合只读图契约的常规理解）。
- 模块无外部输入面（D5）：无网络/文件/反序列化入口；唯一 IO 为 GraphExporter 内存字符串输出。escapeJson 转义正确。
- D7 其他项：模块无 beans.xml、无 @Inject/@InjectValue、无 private 字段注入问题、无 bare RuntimeException、无空 catch、无 printStackTrace；异常风格统一为 IllegalArgumentException（模块内部），符合两档策略的模块内部档。
- nop-graph-api 零运行时依赖（仅 test-scope junit），与模块定位声明一致。
