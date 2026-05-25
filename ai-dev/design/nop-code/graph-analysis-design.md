# nop-code 图分析增强设计

**日期**：2026-05-25
**范围**：`nop-code-graph` 模块功能增强
**状态**：**目标架构**（社区检测/入口点/影响分析已在 core 中实现基础版本；Hub/Bridge/知识缺口/导出/对比均未实现）
**归属模块**：`nop-code-graph`（依赖 `nop-code-core`，当前仍在 core 中）

## 灵感来源

code-review-graph v2.3.3：
- `communities.py` — Leiden + 超大社区分裂 + 批量 O(E) 内聚度 + 架构概览
- `tools/` — Hub（度中心性）/ Bridge（介数中心性）节点 + 知识缺口 + 意外连接
- `exports.py` — GraphML / Neo4j Cypher / Obsidian Vault / SVG
- `graph_diff.py` — 图快照对比

## 模块定位

`nop-code-graph` 在已有基础上（社区检测、入口点评分、影响分析）增强以下能力。所有增强保持与 core 层的清晰边界：graph 只操作 CallGraph / SymbolTable / CommunityDetectionResult 等抽象。

---

## 一、社区检测增强

### 超大社区分裂

超过总节点 25% 的社区递归执行 Leiden 分裂，直到所有社区占比低于阈值或达到最大递归深度。

分辨率参数随图大小反向缩放：`resolution = max(0.05, 1 / log10(n))`，大图自动产出更细粒度的社区。

### 架构概览

跨社区耦合分析：
1. 统计每对社区间的边数（跨社区 CALLS / IMPORTS_FROM 边）
2. 耦合度 = 跨社区边数 / min(社区A大小, 社区B大小)
3. 超过阈值的社区对输出高耦合警告

### 批量内聚度计算

单次遍历所有边计算所有社区的内聚度：`cohesion = internal_edges / (internal_edges + external_edges)`，时间复杂度 O(E)。

### 社区命名

自动生成社区标签：
1. 提取最常见目录前缀
2. 若某 Class 占 >40% 成员 → 用类名
3. 否则提取最高频关键词（过滤常见词）
4. 格式：`{prefix}-{keyword}`

---

## 二、Hub / Bridge 节点分析

### Hub 节点（度中心性）

度中心性最高的节点 = 架构热点，被大量代码依赖或依赖大量代码。

与已有 `EntryPointScorer` 的关系：Hub 近似于 God Node（高 calleeCount），但新增出度维度（高 outDegree = 依赖太多）。

### Bridge 节点（介数中心性）

介数中心性最高的节点 = 架构瓶颈，删除会导致图断裂。

使用 JGraphT 的 `BetweennessCentrality` 计算（已依赖 jgrapht）。

### GraphQL API

归属 `NopCodeIndexBizModel`：

```
NopCodeIndex__getCriticalNodes(indexId, topN) → CriticalNodesResult
```

---

## 三、知识缺口分析

### 检测项

| 缺口类型 | 检测方法 | 依赖 |
|---------|---------|------|
| 孤立节点 | 无 CALLS / INHERITS / IMPORTS 边的符号 | CallGraph |
| 薄弱社区 | 内聚度低于阈值的社区 | CommunityDetector |
| 未测试热点 | 高 criticality 执行流中无 TESTED_BY 边的符号 | FlowDetector + TESTED_BY 边 |

依赖 TESTED_BY 边类型（见边类型补充，通过 `nop_code_usage.kind` 字段扩展）。

---

## 四、图导出

### 支持格式

| 格式 | 用途 | 实现 |
|------|------|------|
| **GraphML** | Gephi / yEd / Cytoscape | JGraphT 原生 `GraphMLExporter`（已依赖） |
| **Mermaid** | Markdown 嵌入、文档生成 | 字符串模板 |
| **JSON** | 前端可视化、API 交互 | CallGraph + SymbolTable 直接序列化 |

`communityView=true` 时按社区聚合输出超节点 + 跨社区边。

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

1. 快照 = 当前图状态的不可变记录（节点集 + 边集 + 社区映射）
2. 差异 = 两个快照的集合运算

```
GraphSnapshot:
  timestamp: Long
  nodes: Set<String>
  edges: Set<EdgeKey>
  communityMap: Map<String, String>     // nodeId → communityId

GraphDiff:
  addedNodes, removedNodes: Set<String>
  addedEdges, removedEdges: Set<EdgeKey>
  communityChanges: [CommunityChange]
```

### 与可逆计算的关系

图快照对比是 Nop 可逆计算原理在代码图谱上的应用——基线版本与目标版本的差集。

### GraphQL API

归属 `NopCodeIndexBizModel`：

```
NopCodeIndex__diffGraph(indexId, baselineCommitish, targetCommitish) → GraphDiff
```

---

## 六、意外连接发现（远期）

**灵感来源**：CRG 的复合惊奇评分——跨社区、跨语言、边缘-枢纽、跨测试边界的非显而易见连接。

**状态**：远期规划。需要跨社区边检测 + 多维度评分，核心数据已具备（CallGraph + CommunityDetector），但评分权重需要调优。可作为 `KnowledgeGapAnalyzer` 的扩展。
