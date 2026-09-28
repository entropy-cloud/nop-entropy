# 决策：nop-code 数据库图后端选型（IGraph 第二实现路径）

> Status: adopted（2026-09-28）
> Decided in: plan `ai-dev/plans/nop-code/18-n6-1-graph-db-backend-decision.md`（roadmap N6.1，Item Type: Decision）
> Consumers: N6.2（IGraph 数据库实现）；N6.3/N6.4（集群索引构建的存储面）

## 1. 背景

`IGraph`（nop-graph-api）仅承诺局部遍历（`getOutEdges`/`getInEdges` + `Edge.attrs`）。当前唯一实现 `CodeCallGraph` 是内存适配器（全量加载后投影 CALLS 边）。N6.2 将实现数据库版 IGraph，需要先裁定存储路径与下推边界。

已成立的相关事实（2026-09-28 live 核对）：

| 事实 | 来源 |
|------|------|
| 参考部署 DB 为 MySQL/H2（quarkus-jdbc-mysql + quarkus-jdbc-h2），无 PostgreSQL 依赖 | nop-code-app/pom.xml |
| 外部图数据库部署已被拒绝（Neo4j Cypher 导出否决记录维持） | graph-analysis-design.md §导出、00-vision |
| 全局算法（Leiden/Betweenness/PageRank/入口点评分）无法由递归 CTE 求得，已在索引期物化至 `nop_code_graph_metric`，查询期物化优先 | N1.2/N1.3（已交付） |
| `IGraph` 契约只有局部遍历 + `Edge.attrs`，无全局算法接口 | nop-graph-api |
| 边数据面为四张关系表（calls/inheritance/annotation/semantic），`nop_code_call` 有 callerId/calleeId 双向索引 | nop-code.orm.xml、CodeRelationGraphLoader |
| ORM 层（nop-orm/nop-db-migration）以可移植 SQL 为目标 | 平台基建 |

## 2. 选项对比

| 选项 | 采纳 | 理由 |
|------|------|------|
| **A. 可移植 SQL：普通表 + 递归 CTE 下推局部遍历** | ✅ **采纳** | 与参考部署（MySQL 8 递归 CTE / H2 / PG 均支持）零冲突；无新增运维面；IGraph 的点查语义可直接由现有双向索引承载，有界深度遍历由 CTE 承载；四表即边存储，不需要专有图结构；ORM 可移植性约束不被破坏 |
| B. PostgreSQL ltree 路径索引 | ❌ 拒绝 | 绑定 PG 扩展，与参考部署（MySQL/H2）直接冲突；ltree 优化的是**路径物化型**查询（树/层级），而图遍历的核心是邻居点查 + 环路安全的可变深度游走——双向索引 + CTE 已覆盖该语义；为非关键收益引入平台级 DB 绑定不成立 |
| C. Apache AGE（PG Cypher 扩展） | ❌ 拒绝 | 同样绑定 PG + 需要独立图存储引擎与 Cypher 运行时，运维复杂度最高；nop-code 的图查询面（局部遍历 + 索引期物化的全局算法）用不到 Cypher 表达力；引入第二查询语言违背平台单一 SQL 栈 |
| D. 外部图数据库（Neo4j 等） | ❌ 拒绝（维持既有否决） | graph-analysis-design.md 已否决 Neo4j 导出——nop-code 定位嵌入式/随应用部署的索引服务，外部图数据库部署与产品形态冲突 |

## 3. 裁定

**N6.2 的 IGraph 数据库实现采用选项 A：可移植 SQL（复用现有四张关系表 + 递归 CTE），不引入任何 PostgreSQL 专属扩展或独立图存储。**

### 3.1 局部下推 vs 全局物化的边界（钉死）

- **下推（DB 内完成）**：`getOutEdges`/`getInEdges` 点查（直接命中现有双向索引）；深度 ≤ N 的有界遍历（递归 CTE，N 为查询参数，默认小值）。
- **不下推（应用层完成）**：无界遍历（环路安全要求 visited 状态管理，超出 IGraph 单方法契约）；全局算法（Leiden/Betweenness/PageRank/入口点）——维持 N1.2 裁定，索引期物化、查询期只读。

### 3.2 对 N6.2 的实现约束

1. 基于现有四表实现，**不新增专有图结构**（新表仅在 CTE 被证实不足时按升级路径引入，需新裁定）。
2. 实现类落点与装配随 N6.2 的 plan 细化；行为等价基线为内存实现（同一数据集上局部遍历结果一致）。
3. CTE 方言差异（MySQL 8 / H2 / PG）以 ORM 可移植写法承载；方言特化须显式判定而非静默降级。

### 3.3 升级路径（非当前承诺）

若生产 DB 迁移至 PostgreSQL 且有界遍历 CTE 在真实规模下性能不足：ltree（路径物化加速层级查询）可作为**增量优化**重评；AGE 仅当出现真实 Cypher 级查询需求时重评。两者均需新裁定记录，不构成本决策的默认预期。
