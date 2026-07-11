# 深度对比分析：OntoAgent 与 Nop 代码索引机制

> 分析日期：2026-07-10
> 分析范围：
> - `~/ai/ontology-driven-agent` — 本体驱动的知识图谱引擎 (Python, LangGraph, Neo4j, ChromaDB)
> - `nop-code/` — Nop 实验性代码索引平台 (Java, ORM, 多语言适配器, 图算法)
> - `nop-ai/nop-ai-skills/nop-ai-code-analyzer/` — Nop AI 代码分析 Skill (单文件解析)

---

## 目录

1. [项目定位与目标](#1-项目定位与目标)
2. [整体架构对比](#2-整体架构对比)
3. [数据模型对比](#3-数据模型对比)
4. [分析管线(Pipeline)对比](#4-分析管线pipeline对比)
5. [图算法与分析能力](#5-图算法与分析能力)
6. [语义增强机制](#6-语义增强机制)
7. [增量更新机制](#7-增量更新机制)
8. [多语言支持](#8-多语言支持)
9. [存储与查询](#9-存储与查询)
10. [AI/Agent 集成方式](#10-aic2a0agent-集成方式)
11. [总结：两种代码理解哲学](#11-总结两种代码理解哲学)

---

## 1. 项目定位与目标

### OntoAgent

构建代码知识图谱，提供给开发者一个**自然语言交互的代码理解界面**。

```
源码 → AST → 知识图谱(Neo4j + ChromaDB) → LangGraph Agent → 对话式查询/操作
```

- **输出**: 知识图谱 (节点+关系+向量嵌入)
- **消费方式**: Agent 对话 (`ask` 命令/Web UI/MCP Server)
- **价值主张**: "用自然语言理解任何代码库"
- **开发状态**: 成熟项目，114+ 源文件，1714 测试

### Nop Code Index

构建代码索引数据库，给 Nop 平台提供一个**结构化代码查询 API**。

```
源码 → AST → ORM 数据库(MySQL/PostgreSQL) → GraphQL API + 图算法服务
```

- **输出**: 关系型数据库 (10 张表: file/symbol/call/inheritance/usage/flow...)
- **消费方式**: GraphQL API (`CodeIndexService`/`CodeSearchService`/`CodeGraphService`)
- **价值主张**: "给 Nop 平台提供多语言代码智能"
- **开发状态**: WIP 实验模块 (`module-groups.md` 标注为 experiment)，14 子模块

### 定位核心差异

| 维度 | OntoAgent | Nop Code Index |
|------|-----------|---------------|
| **目标用户** | 开发者 (通过 Agent 对话) | Nop 平台模块 (通过 API) |
| **核心消费方式** | 自然语言对话 | GraphQL 编程接口 |
| **存储引擎** | Neo4j (原生图) + ChromaDB (向量) | 关系型 ORM (MySQL/PostgreSQL) |
| **AI 角色** | **核心** (语义提取、Agent 推理) | **可选** (搜索可接 SEARCH_ENGINE) |
| **输出粒度** | 概念级 (CodeEntity/ConceptEntity) | 符号级 (Symbol/File/Call) |
| **成熟度** | 生产级 | 实验/WIP |

---

## 2. 整体架构对比

### OntoAgent 架构

```
┌────────────────────────────────────────────────────────────┐
│  API Layer: CLI / MCP / FastAPI Web                        │
├────────────────────────────────────────────────────────────┤
│  Agent Layer: LangGraph ReAct + 13 tools + Intent Router    │
├────────────────────────────────────────────────────────────┤
│  Pipeline Layer                      Execution Layer       │
│  Builder(7-stage)                    ActionExecutor        │
│  IncrementalUpdater                  ShapeEvaluator        │
│  ImpactPropagator                    ApprovalGate          │
│  ConceptAligner                      FunctionRunner        │
│  ModuleClustering                                        │
├────────────────────────────────────────────────────────────┤
│  Parsing Layer: tree-sitter AST (Java/Python/Doc)         │
│  Extractor: Relation / Semantic(LLM) / Capability         │
├────────────────────────────────────────────────────────────┤
│  Store Layer: Neo4jGraphStore + ChromaStore               │
├────────────────────────────────────────────────────────────┤
│  Domain Layer: Schema(13 entities, 26 relations)          │
│               Shapes / Approval / Provenance              │
└────────────────────────────────────────────────────────────┘
```

**关键特征**:
- 图原生存储，实体和关系都是一等公民
- Agent 层直接暴露给最终用户
- LLM 语义提取是管线核心环节
- 分层严格单向依赖

### Nop Code Index 架构

```
┌────────────────────────────────────────────────────────────┐
│  API Layer: GraphQL BizModel + CodeIndex/CodeSearch       │
│             Service Interfaces (ICodeIndexService)          │
├────────────────────────────────────────────────────────────┤
│  Service Layer: CodeIndexService / CodeSearchService       │
│                 CodeGraphService / CodeQueryService         │
│                 CodeCacheManager (SymbolTable+CallGraph)   │
├────────────────────────────────────────────────────────────┤
│  Analysis Layer: ProjectAnalyzer (语言无关管线)            │
│  ├── LanguageAdapterRegistry → ILanguageAdapter           │
│  │   ├── Java: JavaFileAnalyzer (javaparser + tree-sitter)│
│  │   ├── Python: PythonFileAnalyzer (tree-sitter)        │
│  │   └── TypeScript: TypeScriptCodeFileAnalyzer(ts)      │
│  ├── ICodeFileAnalyzer (per-file AST → CodeFileAnalysis)  │
│  ├── SymbolTable (全局符号表, qualified name→Symbol)      │
│  └── CallGraph (调用图, caller→callee)                    │
├────────────────────────────────────────────────────────────┤
│  Graph Layer (nop-code-graph):                              │
│  CommunityDetector / ImpactAnalyzer / CriticalNodeAnalyzer │
│  KnowledgeGapAnalyzer / GraphDiffer / GraphExporter        │
│  Semantic: NameSimilarity / DocKeyword / AnnotationPattern │
│  Heuristic: InterfaceImpl / SpringEvent Synthesizers      │
├────────────────────────────────────────────────────────────┤
│  Flow Layer (nop-code-flow):                               │
│  FlowDetector / DeadCodeDetector / ChangeAnalyzer          │
├────────────────────────────────────────────────────────────┤
│  Store Layer: ORM 数据库 (10 表)                           │
│  NopCodeFile / NopCodeSymbol / NopCodeCall /               │
│  NopCodeInheritance / NopCodeUsage / NopCodeAnnotation...│
└────────────────────────────────────────────────────────────┘
```

**关键特征**:
- 关系型存储，图结构在应用层重建 (`SymbolTable` + `CallGraph`)
- API 层直接暴露给其他 Nop 模块
- 图谱分析完全基于静态/启发式算法，无需 LLM
- 语言适配器插件化设计

### 架构哲学对比

| 维度 | OntoAgent | Nop Code Index |
|------|-----------|---------------|
| **设计中心** | 知识图谱 (节点+关系) | 符号表 (Symbol) |
| **层间依赖** | strict one-way | Nop 平台组件化 |
| **AI/非AI** | AI 密集型 (LLM 核心) | 静态分析密集型 (启发式) |
| **扩展方式** | 新增实体/解析器/提取器 | 新增 ILanguageAdapter/ISemanticExtractor |
| **数据流** | 源码 → AST → KG → Agent → 用户 | 源码 → AST → ORM → API → 模块 |
| **端到端交付** | Agent 对话 | GraphQL 接口 |

---

## 3. 数据模型对比

### OntoAgent Domain Model

13 个实体类型，26 种关系类型，统一在 Neo4j 图结构下：

```
Entity (接口/基类: abstract) ── 节点
├── CodeEntity (function/class/interface/module/file/enum/record/field)
├── ConceptEntity (business_concept/design_pattern/api_contract/data_model/process)
├── DocEntity (readme/module_doc/api_doc/comment/wiki)
├── ResourceEntity (image/diagram/pdf/config)
├── ModuleEntity (聚类结果)
├── ChangeSetEntity (git commit)
├── LogEntity (ERROR/WARN/INFO/DEBUG)
├── AlertEntity (error_spike/latency/service_down)
├── ServiceEntity (running/stopped/degraded)
├── DataAsset (public/internal/confidential/restricted)
├── ComplianceItem (critical/high/medium/low)
├── CapabilityEntity (business capability)
└── ProcessEntity (business flow)

Relation ── 边
├── 结构: calls / extends / implements / imports / contains
├── 语义: semantic_impact / describes / illustrates / derived_from
├── 变更: changed_in / affects
├── 运维: triggered_by / logs_from / runs_as / service_depends_on
├── 业务: processes_data / subject_to / governed_by / calls_service
│         publishes_to / consumed_by
└── 能力: produces / consumes / composes_into / realized_by
          precedes / equivalent_to

每个关系带: provenance_source + confidence + timestamp
```

### Nop Code Index ORM Model

10 张关系表，符号中心化：

```
NopCodeIndex (索引根) ───→ 引用所有表
    │
NopCodeFile (源文件)
    │ path / package / language / lines / imports(JSON) / source(CLOB) / md5
    │
NopCodeSymbol (统一符号表) ←── 核心
    │ kind(CLASS/METHOD/FIELD/...), qualifiedName, accessModifier,
    │ line/col, modifiers(10种bit flag), signature, returnType,
    │ fieldType, extData(JSON), declaringSymbolId(父符号)
    │
NopCodeCall (调用关系)
    │ callerId → calleeId (line/col, callType, confidence, context)
    │
NopCodeInheritance (类型继承)
    │ subTypeId ← superTypeId (EXTENDS/IMPLEMENTS)
    │
NopCodeAnnotationUsage (注解使用)
    │ annotationTypeQName ← annotatedSymbolId (attributes JSON)
    │
NopCodeUsage (符号引用)
    │ READ/WRITE/CALL/EXTENDS/IMPLEMENTS/ANNOTATED_BY
    │
NopCodeDependency (文件依赖)
    │ sourceFile → targetFile (importStatement, resolved)
    │
NopCodeSemanticEdge (语义关系, 启发式)
    │ SEMANTICALLY_SIMILAR_TO / CONCEPTUALLY_RELATED_TO
    │ ALTERNATIVE_OF / CROSS_LANGUAGE_PEER
    │
NopCodeFlow / NopCodeFlowMembership (执行流)
    │ detected execution pipelines
```

### 数据模型对比

| 维度 | OntoAgent | Nop Code Index |
|------|-----------|---------------|
| **存储形式** | 原生图 (节点+边) | 关系型 (10 表) |
| **实体粒度** | 概念级 (CodeEntity/ConceptEntity) | **符号级** (Symbol: 20 种 kind) |
| **关系类型** | 26 种 (结构化定义) | 8 种 (call/inherit/usage/annotation/dependency/semantic/flow) |
| **实体属性** | 类型特有 (CodeEntity.file_path/lines/parameters) | 统一 (extData JSON 灵活扩展) |
| **非代码实体** | 是 (概念/文档/资源/资产/合规/告警...) | **否** (纯代码) |
| **业务语义** | 强 (DataAsset/ComplianceItem/ProcessEntity) | 弱 (通过 SemanticEdge 启发式) |
| **数据校验** | dataclass `__post_init__` 约束验证 | ORM 字段约束 |
| **向量嵌入** | 是 (ChromaDB) | 否 (设计上未集成) |

**核心分歧**: OntoAgent 追求**概念级理解** (函数背后的业务概念、数据资产、合规要求)，Nop Code Index 追求**符号级精确性** (每一个符号的声明位置、引用关系、类型层次)。前者适合"这是什么"的语义问答，后者适合"谁调用了谁"的结构查询。

---

## 4. 分析管线(Pipeline)对比

### OntoAgent 7-Stage Builder

`OntoAgentBuilder.build()`:

| 阶段 | 功能 | 关键组件 | 是否核心 | AI 依赖 |
|------|------|---------|---------|---------|
| 1 | Parse | tree-sitter AST 解析 (Java/Python + Doc) | 是 | 否 |
| 2 | Structural Write | 批量 MERGE CodeEntity/DocEntity + 关系 | 是 | 否 |
| 2.5 | Doc-Code Link | 文档→代码 DESCRIBES 关系 | 是 | 否 |
| 2.6 | Business Ontology | 加载 DataAsset/ComplianceItem | 否 | 否 |
| 2.7 | Capability Extraction | 从 API 入口点反向提取 CapabilityEntity | 否 | 否 |
| 3 | **Semantic Extraction** | LLM 概念提取 + 语义关系 + ConceptAligner | 可降级 | **是** |
| 4 | Module Clustering | Label Propagation 社区发现 | 可降级 | 否 |
| 5 | Vector Index | ChromaDB 向量嵌入 | 可降级 | 否 |

**管线特征**:
- 7 阶段严格顺序执行
- 阶段 1-2 是"硬"分析(AST)，阶段 3 是"软"分析(LLM)
- Stage 3 是 OntoAgent 的差异化能力——LLM 提取 "semantic_impact"、"derived_from" 等语义关系
- 可降级 (degradable) 阶段在资源不足时可跳过

**IncrementalUpdater.update()**:
```
1. Change Detector: git diff → SHA256 缓存 → ChangeType 分类
2. Impact Propagator: 双向 BFS 传播影响 → 严重度评分
3. Selective Regeneration: 重解析变更文件 + 重链接影响实体
4. Integrity Check: 图一致性验证
```

### Nop Code Index `ProjectAnalyzer.analyzeProject()`

```
1. File Discovery: 目录遍历, 按扩展名委派给 ILanguageAdapter
2. Per-file Analysis: ICodeFileAnalyzer.analyze(path, code):
   ├── AST 解析 (javaparser/tree-sitter)
   ├── 提取 CodeSymbol (qualifiedName, location, modifiers)
   ├── 提取 CodeMethodCall (caller→callee, line/col)
   ├── 提取 CodeInheritance (extends/implements)
   ├── 提取 CodeAnnotationUsage
   ├── 提取 CodeRouteInfo (框架路由)
   └── 提取 imports 列表
3. 构建全局 SymbolTable (qualified name → Symbol 索引)
4. 构建全局 CallGraph (bidirectional forward/reverse)
5. 运行 ISemanticEdgeExtractor 插件:
   ├── NameSimilarityExtractor (名称相似)
   ├── DocKeywordExtractor (文档关键词)
   └── AnnotationPatternExtractor (注解模式)
6. 运行 IHeuristicEdgeSynthesizer 插件:
   ├── InterfaceImplSynthesizer (接口→实现推断)
   └── SpringEventSynthesizer (Spring 事件发布/订阅)
7. 持久化: ORM 批量插入 (persistInSession)
```

**管线特征**:
- 两步走: 分析(Per-file) → 全局(跨文件关系构建)
- **无 LLM 参与**: 所有分析基于 AST + 启发式规则
- 语言适配器插件化: 每种语言实现 `ILanguageAdapter`
- 语义边是可选的启发式插件，不是管线核心

### 管线对比

| 维度 | OntoAgent Pipeline | Nop Code Index Pipeline |
|------|-------------------|------------------------|
| **阶段数** | 7 (严格顺序) | 2+ (文件级 → 全局) |
| **核心引擎** | tree-sitter (解析) + LLM (语义) | javaparser/tree-sitter (解析) |
| **语义来源** | **LLM 提取** (semantic_impact 等) | **启发式规则** (名称/关键词/注解) |
| **概念提取** | LLM 逆向工程 (CapabilityEntity) | 无 |
| **模块聚类** | Label Propagation 社区发现 | Leiden/Label Propagation (graph 子模块) |
| **向量索引** | ChromaDB (所有实体嵌入) | 无 |
| **确定性** | 中 (AST 部分确定, LLM 部分不确定) | **高** (完全确定性分析) |
| **可降级** | 是 (阶段 3/4/5 可跳过) | 否 (所有步骤必须) |
| **增量** | git diff + BFS 影响传播 | 文件指纹 (MD5+timestamp) |

---

## 5. 图算法与分析能力

### OntoAgent Graph Algorithms

| 算法 | 组件 | 用途 |
|------|------|------|
| 双向 BFS 传播 | `ImpactPropagator` | 变更影响分析 (权重矩阵: calls 0.9, extends 0.8, imports 0.5) |
| Label Propagation | `ModuleClustering` | 模块社区发现 |
| 4-step 概念对齐 | `ConceptAligner` | exact → alias → vector → graph 匹配 |
| Shape 路径编译 | `PathCompiler` | PathExpression → Cypher MATCH |
| 向量语义搜索 | `ChromaStore` | 实体相似度搜索 |

**影响传播权重矩阵**:
```
Relation    | ADDED | DELETED | SIGNATURE | BODY
calls       | 0.9   | 1.0     | 0.9       | 0.7
implements  | 0.8   | 1.0     | 0.8       | 0.5
extends     | 0.8   | 0.9     | 0.9       | 0.6
imports     | 0.5   | 0.6     | 0.5       | 0.3
score = max(score, propagated_score * weight * decay_at_depth)
```

### Nop Code Index Graph Algorithms (`nop-code-graph`)

| 算法 | 组件 | 算法 | 用途 |
|------|------|------|------|
| 社区检测 | `CommunityDetector` | **Leiden + Label Propagation** | 检测模块/类社区 |
| 影响分析 | `ImpactAnalyzer` | BFS | 符号变更影响范围 |
| 关键节点 | `CriticalNodeAnalyzer` | **PageRank / Betweenness** | 架构重要节点识别 |
| 入口点评分 | `EntryPointScorer` | 启发式打分 | API 表面、main 方法识别 |
| 知识缺口 | `KnowledgeGapAnalyzer` | 孤立节点 + 弱社区 | 理解不足的代码区域 |
| 图谱差分 | `GraphDiffer` | 结构差异对比 | 两次索引快照 diff |
| 图谱导出 | `GraphExporter` | GraphML / DOT / JSON | 可视化 |
| 死代码检测 | `DeadCodeDetector` (flow) | 可达性分析 | 不可达符号 |
| 执行流检测 | `FlowDetector` (flow) | 路径遍历 | 请求处理管线 |

### 语义边提取 (Semantic Edge Extractor)

Nop 的启发式语义分析：

| 提取器 | 规则 | 用途 |
|--------|------|------|
| `NameSimilarityExtractor` | 跨模块名称相似的符号 | 可能的概念关联 |
| `DocKeywordExtractor` | 文档注释中共享的关键词 | 隐含的语义关联 |
| `AnnotationPatternExtractor` | 相同的注解使用模式 | 框架层面的关联 |
| `InterfaceImplSynthesizer` | 接口→实现推断 (即使无直接引用) | 补充继承关系 |
| `SpringEventSynthesizer` | 事件发布/订阅推断 | Spring 架构理解 |

### 图算法对比

| 维度 | OntoAgent | Nop Code Index |
|------|-----------|---------------|
| **图算法数量** | 4 | 10+ |
| **执行引擎** | **Neo4j 内置** (Cypher 查询) | **应用层** (Java 重建图结构) |
| **社区检测** | Label Propagation | Leiden + Label Propagation |
| **中心性分析** | 无 | PageRank / Betweenness |
| **扩散传播** | 权重+深度衰减 BFS | BFS |
| **图谱差分** | 无 | GraphDiffer (快照对比) |
| **死代码检测** | 无 | DeadCodeDetector |
| **入口点发现** | entry_point_rules.py | EntryPointScorer |
| **语义提取** | **LLM 驱动** (灵活性高, 成本高) | **启发式规则** (确定性高, 范围窄) |

**关键差异**: OntoAgent 的图算法依赖 Neo4j 的图遍历能力 (在数据库中执行)，Nop Code Index 的图算法在 Java 应用层重建内存图再计算。前者适合复杂路径查询 (如 Shape 的 PathExpression→Cypher)，后者适合全局分析 (PageRank/社区检测)。

---

## 6. 语义增强机制

### OntoAgent: LLM 驱动的语义提取

#### 语义关系提取 (SemanticExtractor)

```
tree-sitter 提取的结构关系 (calls/extends/imports)
      +
LLM 提取的语义关系 (semantic_impact/derived_from/describes)
      =
完整知识图谱
```

LLM 提示模板设计为从源代码和文档中提取：
- 代码间的语义影响 (semantic_impact)
- 文档对代码的描述关系 (describes)
- 概念之间的推导关系 (derived_from)

#### 概念对齐器 (ConceptAligner, 4-step)

```
Step 1: 精确匹配 (exact name match)
Step 2: 别名匹配 (aliases, 同义词)
Step 3: 向量匹配 (ChromaDB 语义相似度)
Step 4: 图匹配 (Neo4j 图结构相似度)
```

#### 能力提取 (CapabilityExtractor)

从 API 入口点反向推理业务能力：
```
HTTP API / RPC endpoint → 请求处理链 → 业务操作 → CapabilityEntity
```

### Nop Code Index: 启发式规则驱动的语义分析

#### 名称相似匹配 (NameSimilarityExtractor)

```
跨模块相同/相似名称的符号 → SEMANTICALLY_SIMILAR_TO 边
"UserService" 和 "UserRepository" → CONCEPTUALLY_RELATED_TO
```

#### 文档关键词匹配 (DocKeywordExtractor)

```
共享 Javadoc/TSDoc 中的关键词 → 语义关联
关键词频率统计 → 权重计算
```

#### 框架模式推断 (Heuristic Synthesizers)

```
InterfaceImplSynthesizer:
  未显式引用的接口→实现关系 → 隐式推断

SpringEventSynthesizer:
  ApplicationEventPublisher.publishEvent() → @EventListener
  事件类型匹配 → 发布/订阅关系
```

### 语义增强对比

| 维度 | OntoAgent (LLM 驱动) | Nop Code Index (启发式) |
|------|---------------------|----------------------|
| **语义来源** | LLM (GPT/Ollama) | 静态规则 + 模式匹配 |
| **覆盖范围** | 无限 (LLM 可识别任意模式) | 有限 (规则的并集) |
| **准确性** | 中 (LLM 幻觉/遗漏) | **高** (确定性规则) |
| **成本** | **高** (每次分析调用 LLM) | 低 (纯计算) |
| **可解释性** | 低 (黑盒 LLM) | **高** (规则可追溯) |
| **跨语言语义** | 是 (LLM 理解多语言) | 是 (语言无关的启发式) |
| **业务概念提取** | 是 (CapabilityExtractor) | **否** |
| **概念对齐** | 4-step 多模态融合 | 简单名称匹配 |

---

## 7. 增量更新机制

### OntoAgent IncrementalUpdater

```
1. Change Detection:
   git diff HEAD~1 → 变更文件列表
   SHA256 缓存 → 未变更文件跳过

2. Impact Propagation:
   Neo4j 图上双向 BFS 传播
   权重矩阵 × 深度衰减 = 影响分数

3. Selective Regeneration:
   重解析已变更文件 (增量解析)
   重链接受影响的实体

4. Integrity Check:
   图一致性验证 (悬挂引用/重复节点)
```

### Nop Code Index IncrementalDetector

```
1. 文件指纹:
   MD5 + 最后修改时间戳
   存储在中 NopCodeFile.md5

2. `triggerIncrementalIndex()`:
   CodeIndexService → IncrementalDetector
   对比指纹 → 标记新增/修改/删除文件
   仅重新索引有变化的文件 + 受影响的符号

3. 缓存机制:
   CodeCacheManager 缓存 SymbolTable + CallGraph
   DB 查询重建缓存 (缓存未命中时)
```

### 增量对比

| 维度 | OntoAgent | Nop Code Index |
|------|-----------|---------------|
| **检测方法** | git diff + SHA256 | 文件 MD5 + timestamp |
| **影响传播** | **双向 BFS 图传播** (权重矩阵) | 无传播 (全量替换) |
| **选择性重建** | 重解析+重链接影响实体 | 仅重建变更文件 |
| **一致性检查** | 图一致性验证 | 无 (ORM 外键保证引用) |
| **回滚** | 无 | 无 |
| **增量粒度** | 文件级 + 图影响级 | 文件级 |

---

## 8. 多语言支持

### OntoAgent

| 语言 | 解析器 | 实现 |
|------|--------|------|
| Python | tree-sitter-python | `PythonParser` (28K, 最成熟) |
| Java | tree-sitter-java | `JavaParser` (44K) |
| 文档 | tree-sitter / 正则 | `DocParser` (Markdown/注释) |

**特点**: 每种语言一个继承 `BaseParser` 的具体类。模板方法模式：`parse()` 调用钩子 `extract_functions()` / `extract_classes()`。

### Nop Code Index

| 语言 | 适配器 | 解析器 | 技术 |
|------|--------|--------|------|
| Java | `JavaLanguageAdapter` | `JavaFileAnalyzer` | **javaparser + tree-sitter** |
| Python | `PythonLanguageAdapter` | `PythonFileAnalyzer` | tree-sitter |
| TypeScript | `TypeScriptLanguageAdapter` | `TypeScriptCodeFileAnalyzer` | tree-sitter |

**特点**: `ILanguageAdapter` 接口标准化 + `LanguageAdapterRegistry` 按文件后缀注册。每种语言的 `ICodeFileAnalyzer` 统一返回 `CodeFileAnalysisResult`。

### 多语言对比

| 维度 | OntoAgent | Nop Code Index |
|------|-----------|---------------|
| **适配模式** | 继承 `BaseParser` (模板方法) | 实现 `ILanguageAdapter`+`ICodeFileAnalyzer` |
| **Java 解析** | tree-sitter-java | javaparser (符号解析) + tree-sitter |
| **跨语言统一模型** | Entity 基类 + entity_type 字段 | `CodeSymbol` 统一 + `CodeSymbolKind` 枚举 |
| **语言数量** | 2 + 文档 | 3 (Java/Python/TS) |
| **解析深度** | 函数/类/接口/模块 | 符号级 (20 种 kind) + 调用+继承+注解 |

---

## 9. 存储与查询

### OntoAgent: 双 Store

#### Neo4jGraphStore (图存储)
- **查询语言**: Cypher
- **操作**: `query(cypher, params)`, `get_node(id)`, `get_relations(source_id/target_id)`, `merge_node()`, `merge_relation()`
- **索引**: Neo4j 原生索引
- **事务**: Neo4j 原生事务

#### ChromaStore (向量存储)
- **嵌入模型**: OllamaEmbeddingFunction
- **操作**: `search(query_text, n_results)`, `add_documents()`, `delete_document()`
- **存储**: ChromaDB (本地文件系统)

**查询示例**:
```python
# Agent 通过 graph_query 工具
neo4j.query("MATCH (n:CodeEntity) WHERE n.name CONTAINS $name
             RETURN n.name, n.file_path LIMIT 10", {"name": "User"})

# 语义搜索
chroma.search("用户登录流程", n_results=5)
```

### Nop Code Index: ORM + 缓存

#### 关系型数据库
- **ORM 框架**: Nop ORM
- **表结构**: 10 张表 (NopCodeFile/Symbol/Call/Inheritance/...)
- **查询**: GraphQL (BizModel) + 程序式 API

#### 内存缓存
- **SymbolTable**: `Map<qualifiedName, CodeSymbol>` 按 ID 和 qualified name 索引
- **CallGraph**: `Map<symbolId, Set<symbolId>>` (前向+反向)
- **重建策略**: DB 查询 → 内存重建 (缓存未命中)

**查询示例**:
```java
// 编程式查询
codeIndexService.findSymbolByQualifiedName("io.nop.core.lang.xml.XNode");
codeSearchService.search("XNode", CodeSearchMode.SYMBOL_NAME);
codeGraphService.getCallers(symbolId, depth);
codeQueryService.querySymbols(queryBean);
```

### 存储对比

| 维度 | OntoAgent | Nop Code Index |
|------|-----------|---------------|
| **主存储** | Neo4j (原生图) | 关系型 ORM |
| **辅助存储** | ChromaDB (向量) | 内存缓存 (SymbolTable+CallGraph) |
| **查询语言** | Cypher + 语义搜索 | GraphQL + Java API |
| **图遍历效率** | **高** (Neo4j 原生) | 中 (ORM JOIN + 应用层) |
| **复杂路径查询** | **强** (Cypher MATCH 路径表达式) | 弱 (需应用层多步处理) |
| **全文搜索** | 无 | 内置 (FULL_TEXT mode) |
| **搜索后端** | ChromaDB 语义搜索 | DB 模糊匹配 / SEARCH_ENGINE(Lucene) |
| **一致性** | 无内置 | ORM 事务 + 缓存失效 |

---

## 10. AI/Agent 集成方式

### OntoAgent: Agent 是一等公民

```
KG 构建完成
     ↓
Agent 暴露 13 个工具给最终用户
     │
     ├── semantic_search(query)    ← ChromaDB 向量搜索
     ├── graph_query(cypher)       ← Neo4j Cypher 查询
     ├── get_context(entity)       ← 实体 360° 视图
     ├── impact_analysis(entity)   ← BFS 影响传播
     ├── list_concepts()            ← 概念实体枚举
     ├── get_module_tree()          ← 聚类模块树
     ├── detect_changes()           ← Git 变更
     ├── express_intent()           ← 受约束的操作
     └── explore_ontology()         ← 本体 Shape 浏览
```

**Agent 如何消费分析结果**:
1. 用户问 "这个类的调用链" → Agent 选 `graph_query`
2. 用户问 "重构这个函数的影响" → Agent 选 `impact_analysis`
3. 用户问 "相关概念" → Agent 选 `semantic_search`
4. 系统提示词包含完整的 Schema (9 实体 15 关系)

### Nop Code Index: API 是一等公民

```
索引构建完成
     ↓
服务层暴露 GraphQL + Java API 给其他 Nop 模块
     │
     ├── ICodeIndexService.indexFile(path)    ← 索引文件
     ├── ICodeIndexService.indexDirectory()   ← 索引项目
     ├── CodeSearchService.search(query, mode) ← 符号/全文搜索
     ├── CodeQueryService.querySymbols()       ← 符号查询
     └── CodeGraphService.getReachableSymbols() ← 图可达分析
```

**其他模块如何消费分析结果**:
1. **nop-ai-agent** 可通过工具调用 `CodeSearchService`
2. **nop-ai-coder** 可通过 `CodeQueryService` 获取已有符号信息
3. **开发者** 通过 GraphQL IDE 查询

**但注意**: Nop Code Index 本身**不提供 Agent**。它提供的是基础设施，Agent 层由 `nop-ai-agent` 模块提供。

### AI 集成对比

| 维度 | OntoAgent | Nop Code Index |
|------|-----------|---------------|
| **Agent 内置** | **是** (LangGraph ReAct) | 否 (无内置 Agent) |
| **用户交互** | 自然语言对话 | GraphQL/Java API |
| **Agent 工具来源** | 同模块 (13 tools) | **nop-ai-agent** + **nop-ai-toolkit** |
| **查询入口** | Cypher + ChromaDB | GraphQL + SearchService |
| **Schema 暴露** | 在 System Prompt 中 | 在 GraphQL Schema 中 |
| **安全约束** | Shape (本体约束) | 无 (由 Agent 层提供) |

---

## 11. 总结：两种代码理解哲学

### OntoAgent 哲学: "理解语义"

```
源码 ≠ 字符串; 源码 = 知识
知识 = 实体(概念) + 关系(语义)
理解 = KG 构建(结构化) + LLM 提取(非结构化)
交付 = Agent 对话(自然语言)
```

| 选择 | 理由 |
|------|------|
| 用 Neo4j (原生图) | 关系是代码理解的核心 |
| 用 LLM 提取语义 | 静态分析无法捕捉业务概念 |
| 用 Agent 对话 | 开发者最自然的交互方式 |
| 用 Shape 约束 | 运行时保护基于数据语义 |
| 用 ChromaDB 向量 | 模糊搜索 + 概念对齐 |

**最佳场景**: 大型遗留代码库的语义理解、"这个模块是做什么的"、"修改 X 会影响谁"

### Nop Code Index 哲学: "精确符号"

```
源码 = 符号 + 关系
符号 = 位置 + 类型 + 修饰符 + 限定名
关系 = 调用 + 继承 + 引用 + 依赖
交付 = API (GraphQL + Java)
```

| 选择 | 理由 |
|------|------|
| 用 ORM (关系型) | 与 Nop 平台基础设施一致 |
| 用启发式规则 | 确定性分析，可测试，可审计 |
| 用 API 交付 | 模块化集成，不绑定交互方式 |
| 用 PageRank/Betweenness | 精确的图论分析 |
| 用内存缓存 | 高性能图算法分析 |

**最佳场景**: IDE 级别的符号导航、"谁调用了这个方法"、"这个类继承了谁"、"死代码检测"

### 定位光谱

```
                  语义理解 ←────────────────────→ 精确符号
                       │                          │
                   OntoAgent                Nop Code Index
                       │                          │
                LLM 密集型              静态分析密集型
                Agent 面向终端用户       API 面向平台模块
                概念级实体              符号级实体
                Neo4j + ChromaDB        ORM + 内存缓存
                Cypher + 语义搜索       GraphQL + 搜索服务
                完全增量               全量重建
```

### 相互借鉴

| OntoAgent 可学自 Nop Code Index | Nop Code Index 可学自 OntoAgent |
|--------------------------------|---------------------------------|
| 符号级精度 (qualified name 消歧) | LLM 语义提取 (业务概念发现) |
| 确定性分析 (启发式规则匹配) | Neo4j 图原生存储 (关系查询性能) |
| 图算法广度 (PageRank/Betweenness) | 概念对齐 (多步融合匹配) |
| 语言适配器模式 (ILanguageAdapter) | 向量搜索集成 (ChromaDB) |
| 增量文件指纹 (MD5 + timestamp) | 影响传播 (BFS 权重矩阵) |
| 死代码检测 (可达性分析) | 变更差分 (git diff + SHA256) |
| 注解模式分析 (Spring 事件推断) | 能力/入口点发现 |

### 综合建议

对于 Nop 平台而言，最合理的代码理解策略是**分层**：

```
Layer 1: nop-code (精确符号索引)
    └── 提供: 符号查找、调用链、继承层次、死代码检测
    └── 消费: GraphQL API / nop-ai-agent 工具

Layer 2: LLM 语义层 (可集成 LLM 提取到 nop-code-semantic 之类)
    └── 提供: 业务概念识别、语义关系、能力提取
    └── 可选: 仅在需要时启用 (高成本)

Layer 3: nop-ai-agent (Agent 交互)
    └── 提供: 自然语言接口、安全约束、操作执行
    └── 消费: Layer 1 + Layer 2 的数据
```

这样既保留了 `nop-code` 的精确性和确定性，又借鉴了 OntoAgent 的语义理解和 Agent 交互能力。

---

*本文档基于截至 2026-07-10 的代码版本分析。*
