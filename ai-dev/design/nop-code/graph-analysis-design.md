# nop-code 图分析增强设计

**日期**：2026-05-25
**范围**：`nop-code-graph` 模块功能增强
**状态**：**已实现**
**归属模块**：`nop-code-graph`（依赖 `nop-code-core`）

## 灵感来源

code-review-graph v2.3.3：
- `communities.py` — Leiden + 超大社区分裂 + 批量内聚度 + 架构概览
- `tools/` — Hub（度中心性）/ Bridge（介数中心性）节点 + 知识缺口
- `exports.py` — GraphML / Mermaid / JSON
- `graph_diff.py` — 图快照对比

## 模块定位

`nop-code-graph` 提供社区检测、关键节点分析、知识缺口检测、图导出、图对比五种图级分析能力。所有能力保持与 core 层的清晰边界：graph 只操作 CallGraph / SymbolTable / CommunityDetectionResult 等抽象。

---

## 一、社区检测

### 算法选择

支持两种算法，通过 `CommunityConfig.algorithm` 选择：
- **Leiden**（默认）：使用 cwts LeidenAlgorithm 库，适合高质量社区检测
- **Label Propagation**：使用 JGraphT LabelPropagationClustering，作为 Leiden 失败时的 fallback

大图模式（节点 > 10,000）自动启用优化：过滤低度节点（degree < 2），减少迭代次数。

### 超大社区分裂

超过总节点 25% 的社区递归执行 Label Propagation 分裂，最大递归深度 3。分裂时分辨率参数随子图大小反向缩放：`resolution = max(0.05, 1 / log10(n))`。

### 社区命名

自动生成社区标签：提取社区中最常见的包名，取最后两段作为标签（如 `service.impl`）。无包名信息时退化为 `cluster_{size}`。

### 批量内聚度计算

每个社区的内聚度 = `internalEdges / (cluster.size() * (cluster.size() - 1))`，遍历社区成员的调用关系计算。

---

## 二、关键节点分析（Hub / Bridge）

### Hub 节点（度中心性）

遍历 CallGraph 所有边，统计每个节点的入度和出度。`totalDegree = inDegree + outDegree`，按 totalDegree 降序取 topN。Hub 节点 = 架构热点，被大量代码依赖或依赖大量代码。

### Bridge 节点（介数中心性）

将 CallGraph 转为 JGraphT `DefaultDirectedGraph`，使用 `BetweennessCentrality`（normalize=false）计算介数中心性。按分数降序取 topN。Bridge 节点 = 架构瓶颈，删除会导致图断裂。

### GraphQL API

归属 `NopCodeIndexBizModel`：

```
NopCodeIndex__getCriticalNodes(indexId, topN) → CriticalNodeResultDTO
```

---

## 三、知识缺口分析

### 检测项

| 缺口类型 | 检测方法 |
|---------|---------|
| 孤立节点 | 无 callees 且无 callers 的符号（完全无 CALLS 边） |
| 薄弱社区 | 内聚度低于阈值（默认 0.1）的社区 |

薄弱社区内聚度计算：`internalEdges / (internalEdges + externalEdges)`，遍历社区成员的调用关系。

**未测试热点**检测依赖 TESTED_BY 边类型，当前未实现（需 `nop_code_usage.kind` 字段扩展）。

### GraphQL API

归属 `NopCodeIndexBizModel`：

```
NopCodeIndex__getKnowledgeGaps(indexId) → KnowledgeGapResultDTO
```

---

## 四、图导出

### 支持格式

| 格式 | 用途 | 实现 |
|------|------|------|
| **GraphML** | Gephi / yEd / Cytoscape | JGraphT `GraphMLExporter` |
| **Mermaid** | Markdown 嵌入、文档生成 | 字符串模板，`graph LR` 方向 |
| **JSON** | 前端可视化、API 交互 | 手工序列化 nodes + edges 数组 |

`communityView=true` 时按社区聚合输出超节点 + 跨社区边，三种格式均支持。

**拒绝了什么**：
- Neo4j Cypher → nop-code 使用嵌入式存储，无 Neo4j 部署
- Obsidian Vault → 特定工具格式，价值有限
- SVG → Mermaid 在 Markdown 渲染器中自动生成图

### GraphQL API

归属 `NopCodeIndexBizModel`：

```
NopCodeIndex__exportGraph(indexId, format, communityView) → String
```

---

## 五、图快照对比

### 核心语义

快照 = 当前图状态的不可变记录（节点集 + 边集 + 社区映射），通过 `GraphDiffer.buildSnapshot` 从 CallGraph + CommunityDetectionResult 构建。

差异 = 两个快照的集合运算（set-based comparison）：
- `addedNodes = target.nodes - baseline.nodes`
- `removedNodes = baseline.nodes - target.nodes`
- `addedEdges = target.edges - baseline.edges`
- `removedEdges = baseline.edges - target.edges`
- 社区变更 = 比较每个节点的 communityMap 差异

### API 设计

BizModel 层使用两个 `indexId` 参数而非 git commitish：

```
NopCodeIndex__diffGraph(baselineIndexId, targetIndexId) → GraphDiffDTO
```

这对应于比较两次不同索引的图状态（例如不同版本的代码库索引）。

### 与可逆计算的关系

图快照对比是 Nop 可逆计算原理在代码图谱上的应用——基线索引与目标索引的差集。

---

## 六、意外连接发现（远期）

**灵感来源**：CRG 的复合惊奇评分——跨社区、跨语言、边缘-枢纽、跨测试边界的非显而易见连接。

**状态**：远期规划。核心数据已具备（CallGraph + CommunityDetector），但评分权重需要调优。可作为 `KnowledgeGapAnalyzer` 的扩展。
