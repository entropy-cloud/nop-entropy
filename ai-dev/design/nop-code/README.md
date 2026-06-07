# nop-code 设计文档

> Status: active
> Created: 2026-05-02
> Updated: 2026-06-07（按 AGE owner-doc 模式重组）

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，从高层设计原则到分项设计逐层展开：

1. **愿景层** — 定位、成功标准、约束、non-goals、设计不变量
2. **架构基线层** — 模块划分、通用代码模型、核心接口、边类型
3. **查询层** — GraphQL API 归属策略、核心查询接口定义
4. **分析层** — 图分析增强、流级分析、语义边
5. **集成层** — nop-search 集成

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

---

## 阅读顺序

**必读路径**（理解定位 → 架构 → 查询接口）：

1. `00-vision.md` — 设计原则、约束、non-goals
2. `01-architecture-baseline.md` — 架构基线、模块划分、通用模型
3. `query-api-design.md` — GraphQL API 接口定义

**按需深入**：

4. `graph-analysis-design.md` — 社区检测、关键节点、图导出
5. `flow-analysis-design.md` — 执行流、变更分析、死代码检测
6. `semantic-edge-design.md` — 语义边模型和提取器
7. `search-integration-design.md` — nop-search 集成

---

## 附录

### 实现状态

- ✅ `nop-code-core`：已实现（通用模型、图数据结构、分析算法）
- ✅ `nop-code-lang-java`：已实现（JavaParser + SymbolSolver，覆盖 Java 17）
- ✅ `nop-code-lang-python`：已实现（tree-sitter-python，符号/继承/装饰器/调用提取）
- ✅ `nop-code-lang-typescript`：已实现（tree-sitter-typescript，符号/继承/装饰器提取，暂无调用图）
- ✅ `nop-code-graph`：已实现（社区检测 Leiden/LabelPropagation、入口点评分、影响分析、Hub/Bridge、知识缺口、GraphML/Mermaid/JSON 导出、图快照对比）
- ✅ `nop-code-flow`：已实现（执行流追踪、风险评分变更分析、死代码检测）
- ✅ `nop-code-api`：已实现（CodeIndexApi 接口 + 5 个通用 API，由 CodeIndexService 2800+ 行实现支撑）
- ✅ `nop-code-service`：已实现（全部 query-api-design.md 定义的 GraphQL API，nop-search 双路径集成）
- ✅ `语义边`：核心模型已实现（CodeSemanticEdge + ISemanticEdgeExtractor + 3 个确定性提取器），ORM 表已生成，BizModel 已生成
- ⏳ `语义边 LLM 集成`：远期，依赖 nop-ai 模块
- ⏳ `nop-search 全功能集成`：双路径已实现（有搜索引擎时用 HYBRID 搜索，否则 DB LIKE），向量嵌入和混合搜索待部署时注入

### 设计文档约定

- 每个文档自洽，不引用 analysis 或 plan 文档作为决策依据
- 记录灵感来源但不依赖外部文档理解设计
- 区分核心功能和次级功能，不写执行计划
- 单文档不超过 20KB

### 历史文档

- 最初的 GraphQL Schema 设计已合并到 `query-api-design.md`（含完整 schema 定义）
