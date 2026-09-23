# nop-code 设计文档

> Status: active
> Created: 2026-05-02
> Updated: 2026-09-23（增补集群索引 / 无状态 / 存储抽象 / 框架适配原则）

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，从高层设计原则到分项设计逐层展开：

1. **愿景层** — 定位、成功标准、约束、non-goals、设计不变量
2. **架构基线层** — 模块划分、通用代码模型、核心接口、边类型
3. **查询层** — GraphQL API 归属策略、核心查询接口定义
4. **分析层** — 图分析增强、流级分析、语义边
5. **集成层** — nop-search 集成

---

## 架构演进方向（2026-09-23）

基于 10 个开源 code graph 项目调研，nop-code 明确以下架构方向（括号内为现状）：

| 决策 | 内容 | 详见 |
|------|------|------|
| **不引入 MCP** | GraphQL 已是通用 API 暴露协议，新增 MCP 是冗余 | `00-vision.md`、`docs-for-ai/02-core-guides/api-and-graphql.md` |
| **存储抽象（已落地）** | 复用现有 `IGraph`（`nop-graph-api`）作为存储抽象；`CallGraph`/`SymbolTable` 是内存实现，`CodeCallGraph` 是适配器 | `01-architecture-baseline.md` §4.4.1 |
| **集群索引 + 无状态（目标）** | 查询路径无状态、全局算法结果索引期物化；当前为 `CodeCacheManager` 堆内缓存 | `00-vision.md` §一 |
| **框架适配不入核心（目标）** | 框架模式经 SPI 可插拔加载；当前 `JavaFileAnalyzer` 含硬编码 Spring，待迁出 | `00-vision.md` §三 约束 9 |
| **利用数据库图索引（待决策）** | 评估 PostgreSQL ltree/CTE 或 Apache AGE；需先定生产 DB 与可移植性；全局算法无法由 CTE 求得 | `01-architecture-baseline.md` §4.4.1 |

---

## 愿景层

- `00-vision.md`
  - 产品定位、成功标准、不可违反的约束、显式 non-goals、设计收敛路径、必须由人决策的决策点、核心取舍、设计不变量、拒绝了什么

## 架构基线层

- `01-architecture-baseline.md`
  - 模块划分与依赖关系、模块拆分决策、通用代码模型（CodeSymbol / CodeSymbolKind / CodeAccessModifier / CodeLanguage / CodeFileAnalysisResult）、核心接口（ICodeFileAnalyzer / ILanguageAdapter / IProjectAnalyzer / 分析算法接口）、边类型定义、实现状态

## 查询层

- `query-api-design.md`
  - GraphQL API 归属策略（按聚合根分配）
  - 核心查询接口（文件大纲、模块摘要、符号定位、类型层级、调用层级、依赖图、代码搜索等）
  - 索引操作 API、图分析操作 API
  - 错误处理约定、设计决策

## 分析层

- `graph-analysis-design.md`
  - 社区检测（Leiden + LabelPropagation + 超大社区分裂）
  - 关键节点分析（Hub 度中心性 / Bridge 介数中心性）
  - 知识缺口分析（孤立节点、薄弱社区）
  - 图导出（GraphML / Mermaid / JSON）
  - 图快照对比

- `graph-discovery-and-export-design.md`
  - 意外连接发现（复合惊奇评分：置信度 / 跨文件类型 / 跨目录 / 跨社区 / 边缘→枢纽 / 语义相似度加权）
  - 图谱问题生成（未决边 / 桥接节点 / 待验证推断边 / 孤立节点 / 低内聚社区 → AI 探索引导）
  - 图谱 Wiki 导出（Markdown：index.md + 社区文章 + 枢纽节点文章）
  - 自动重建触发（VCS 事件驱动，与集群无状态架构对齐）
  - 显式拒绝 Hypergraph / 本地 watch / MCP 暴露

- `flow-analysis-design.md`
  - 执行流追踪（入口点检测 + BFS 前向追踪 + 五维关键度评分）
  - 风险评分变更分析（git diff 行级映射 + 五维风险评分）
  - 死代码检测（排除规则 + 置信度分级）

- `semantic-edge-design.md`
  - 语义边模型（置信度分级 / 关系类型 / 提取器接口）
  - 确定性提取器（名称相似度、文档关键词、注解模式）
  - LLM 增强提取器（远期，依赖 nop-ai）
  - 社区检测和影响分析感知语义边

## 集成层

- `search-integration-design.md`
  - nop-search 集成方案（索引同步、查询改造、降级策略）
  - 向量嵌入（依赖 nop-ai）

## 验收层

- `ai-e2e-acceptance-design.md`
  - 以 nop-entropy 自身为索引对象的端到端 AI 验收
  - 场景 A：Nop 平台知识获取；场景 B：应用开发辅助
  - 评分 rubric、对照基线、成功判据、失败回灌闭环

---

## 阅读顺序

**必读路径**（理解定位 → 架构 → 查询接口）：

1. `00-vision.md` — 设计原则、约束、non-goals
2. `01-architecture-baseline.md` — 架构基线、模块划分、通用模型
3. `query-api-design.md` — GraphQL API 接口定义

**按需深入**：

4. `graph-analysis-design.md` — 社区检测、关键节点、图导出
5. `graph-discovery-and-export-design.md` — 意外连接、问题生成、Wiki 导出、自动重建
6. `flow-analysis-design.md` — 执行流、变更分析、死代码检测
7. `semantic-edge-design.md` — 语义边模型和提取器
8. `search-integration-design.md` — nop-search 集成
9. `ai-e2e-acceptance-design.md` — AI 端到端验收（压轴）

---

## 附录

### 实现状态

- ✅ `nop-graph`（顶层共享库）：已实现（`IGraph`/`Edge`/结果类型；Leiden/LabelPropagation/介数中心性/Bfs/PageRank/TarjanSCC/影响传播/GraphExporter/GraphDiffer）
- ✅ `nop-code-core`：已实现（通用模型、`CallGraph`/`SymbolTable` 内存结构、`CodeCallGraph` 适配器、`EntryPointScorer`、增量检测）
- ✅ `nop-code-lang-java`：已实现（JavaParser + SymbolSolver，覆盖 Java 17；含 Spring 路由提取 `[legacy，待迁出核心]`）
- ✅ `nop-code-lang-python`：已实现（tree-sitter-python，符号/继承/装饰器/调用提取）
- ✅ `nop-code-lang-typescript`：已实现（tree-sitter-typescript，符号/继承/装饰器提取，暂无调用图）
- ✅ `nop-code-flow`：已实现（执行流追踪、风险评分变更分析、死代码检测）
- ✅ `nop-code-meta`：已实现（xmeta 全套 + ORM 模型 + dict + i18n）
- ✅ `nop-code-codegen`：已实现（代码生成层）
- ✅ `nop-code-api`：已实现（生成的 per-entity CRUD API + DTO；服务接口 `ICodeIndexService`）
- ✅ `nop-code-service`：已实现（全部 query-api-design.md 定义的 GraphQL API；nop-search 双路径：可注入 `ISearchEngine`，默认无引擎时降级 DB LIKE）
- ✅ `语义边（确定性）`：核心模型已实现（CodeSemanticEdge + ISemanticEdgeExtractor + 3 提取器），ORM 表已生成，BizModel 已生成
- ✅ `启发式调用边合成`：已实现（InterfaceImplSynthesizer / SpringEventSynthesizer，产出 INFERRED CodeMethodCall）
- ⏳ `语义边 LLM 集成`：远期，依赖 nop-ai 模块；当前确定性提取器只产出 EXTRACTED 边
- ⏳ `nop-search 向量/混合`：双路径已实现，向量嵌入与 RRF 混合搜索待部署时注入
- ⏳ `全局算法结果持久化 / 查询路径无状态`：当前每次查询重算并堆内缓存，待迁移

### 设计文档约定

- 每个文档自洽，不引用 analysis 或 plan 文档作为决策依据
- 记录灵感来源但不依赖外部文档理解设计
- 区分核心功能和次级功能，不写执行计划
- 单文档不超过 20KB

### 历史文档

- 最初的 GraphQL Schema 设计已合并到 `query-api-design.md`（含完整 schema 定义）
