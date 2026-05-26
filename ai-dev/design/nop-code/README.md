# nop-code 设计文档索引

## 定位

nop-code 是 Nop 平台的**多语言代码索引与语义分析服务**，为 AI 辅助代码分析提供结构化索引。AI 层通过 GraphQL 访问，不需要独立的 MCP 服务。

## 阅读顺序

| # | 文档 | 职责 |
|---|------|------|
| 1 | `language-agnostic-code-index-design.md` | **架构基线**：模块结构、依赖关系、通用模型、核心接口、边类型 |
| 2 | `query-api-design.md` | **查询 API**：GraphQL API 归属策略、核心查询接口定义 |
| 3 | `search-integration-design.md` | **搜索集成**：nop-search 集成方案 |
| 4 | `graph-analysis-design.md` | **图分析增强**：社区检测增强、Hub/Bridge、知识缺口、导出、对比 |
| 5 | `flow-analysis-design.md` | **流级分析**：执行流追踪、风险评分变更分析、死代码检测 |
| 6 | `semantic-edge-design.md` | **语义边**：LLM 辅助语义边提取、社区检测集成 |

另外 `nop-code/design/ai-code-index-graphql-design.md`（1493 行）是最初的 GraphQL Schema 设计文档。

## 模块结构

```
nop-code-core          ← 模型 + 接口 + 图数据结构 + 增量检测（依赖 jgrapht-core）
    ↑
nop-code-graph         ← 图算法（社区检测、入口点、影响分析、导出、对比）【规划中，算法当前在 core】
    ↑
nop-code-flow          ← 流级分析（执行流、变更分析、死代码）【规划中】
    ↑
nop-code-service       ← BizModel 编排（聚合所有模块 + nop-search-api）

nop-code-lang-java/python/typescript  ← 语言适配器（依赖 core；python/typescript 仅有骨架）
nop-code-dao / meta / web / app       ← 标准 Nop 分层
```

## 实现状态

- ✅ `nop-code-core`：已实现（通用模型、图数据结构、分析算法）
- ✅ `nop-code-lang-java`：已实现（JavaParser + SymbolSolver，覆盖 Java 17）
- ✅ `nop-code-lang-python`：已实现（tree-sitter-python，符号/继承/装饰器/调用提取）
- ✅ `nop-code-lang-typescript`：已实现（tree-sitter-typescript，符号/继承/装饰器提取，暂无调用图）
- ✅ `nop-code-graph`：已实现（社区检测 Leiden/LabelPropagation、入口点评分、影响分析、Hub/Bridge、知识缺口、GraphML/Mermaid/JSON 导出、图快照对比）
- ✅ `nop-code-flow`：已实现（执行流追踪、风险评分变更分析、死代码检测）
- ✅ `nop-code-api`：已实现（CodeIndexApi 接口 + 5 个通用 API，由 CodeIndexService 2800+ 行实现支撑）
- ✅ `nop-code-service`：已实现（全部 query-api-design.md 定义的 GraphQL API，nop-search 双路径集成）
- ⏳ `语义边`：目标架构（semantic-edge-design.md），核心模型和确定性提取器待实现
- ⏳ `nop-search 全功能集成`：双路径已实现（有搜索引擎时用 HYBRID 搜索，否则 DB LIKE），向量嵌入和混合搜索待部署时注入

## 设计文档约定

- 每个文档自洽，不引用 analysis 或 plan 文档作为决策依据
- 记录灵感来源但不依赖外部文档理解设计
- 区分核心功能和次级功能，不写执行计划
- 单文档不超过 20KB
