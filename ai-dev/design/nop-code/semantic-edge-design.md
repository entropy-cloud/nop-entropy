# nop-code 语义边（Semantic Edge）设计

**日期**：2026-05-03（更新于 2026-05-25）
**范围**：`nop-code-core` + `nop-ai` 集成
**状态**：**目标架构**（核心模型和确定性提取器待实现）
**目标**：在确定性 AST 分析的基础上，引入 LLM 辅助的语义边提取，发现跨文件/跨模块的非结构化概念关联
**灵感来源**：graphify 项目的 INFERRED 边机制

---

## 一、背景与动机

### 1.1 现状

nop-code 已实现的分析能力全部基于**确定性 AST 提取**：

| 能力 | 边类型 |
|------|--------|
| 调用图 | `calls`（caller → callee） |
| 继承图 | `extends` / `implements` |
| 注解使用 | `annotated_by` |
| 符号包含 | `contains` |
| 导入关系 | `imports` |

这些边都是 **EXTRACTED**（从源码直接提取，100% 确定性）。

### 1.2 缺失

纯结构分析无法发现以下关系：

| 场景 | 示例 | 纯 AST 能否发现 |
|------|------|----------------|
| 两个函数解决同一问题但无调用关系 | `validateEmail()` 和 `checkEmailFormat()` | ❌ |
| 代码与设计文档描述同一概念 | `JwtAuthFilter` ↔ 设计文档中的 "认证拦截" 章节 | ❌ |
| 同一设计模式的不同实现 | `StrategyFactory` 和 `PluginRegistry` 都是策略模式 | ❌ |
| 跨语言概念关联 | Java `UserService` ↔ TypeScript `UserServiceClient` | ❌ |

这些关系需要**语义理解**，即 LLM 推理。

---

## 二、设计目标

1. **在 nop-code-core 中定义语义边模型**，不依赖任何 LLM 实现
2. **提供确定性语义边的基础实现**（基于名称相似度、文档关键词匹配等，不依赖 LLM）
3. **预留 LLM 增强接口**，未来通过 `nop-ai` 模块接入
4. **语义边持久化到数据库**，通过 GraphQL 查询
5. **社区检测和影响分析感知语义边**，语义边参与聚类

---

## 三、核心模型

### 3.1 置信度分级

| 级别 | 值 | 含义 |
|------|---|------|
| EXTRACTED | 10 | 确定性提取（AST / 配置解析直接得到） |
| INFERRED | 20 | 语义推断（LLM 或算法推断，有置信分数） |
| AMBIGUOUS | 30 | 不确定（需人工审核） |

### 3.2 语义边属性

语义边 `CodeSemanticEdge` 包含以下关键信息：

- **端点**：sourceSymbolId / targetSymbolId / directed（有向或无向）
- **关系**：relationType（见 3.3） / confidence（置信度级别） / confidenceScore（0.0-1.0）
- **解释**：rationale（为什么存在） / whyNot（AMBIGUOUS 时的不确定原因）
- **来源**：extractorId（产生这条边的提取器，如 "name-sim" / "doc-keyword" / "llm-claude"）
- **扩展**：extData（JSON，额外数据）

### 3.3 语义边关系类型

预定义关系类型，存 `relationType` 字段：

| 类别 | 类型 |
|------|------|
| 语义相似 | `semantically_similar_to`（无向）、`conceptually_related_to`（无向）、`solves_same_problem` |
| 设计模式 | `implements_pattern`、`alternative_of` |
| 文档关联 | `documented_by`、`rationale_for` |
| 跨语言 | `cross_language_peer` |
| LLM 自定义 | 任意 relationType，需在白名单内或带 `llm_` 前缀 |

---

## 四、语义边提取器

### 4.1 提取器接口（`ISemanticEdgeExtractor`）

每个提取器提供：
- extractorId：唯一标识
- extract(SymbolTable, CallGraph) → List<CodeSemanticEdge>
- requiresLlm()：是否需要 LLM（用于成本估算和跳过决策）
- estimatedTokens(SymbolTable)：预估 token 消耗

### 4.2 确定性提取器（不依赖 LLM）

| 提取器 | extractorId | 算法 |
|--------|------------|------|
| 名称相似度 | `name-sim` | 标准化符号名 → Levenshtein/Jaccard 相似度 → 超阈值生成 `semantically_similar_to` 边 |
| 文档关键词 | `doc-keyword` | 提取文档关键词 → 交集超阈值生成 `conceptually_related_to` 边 |
| 注解模式 | `annotation-pattern` | 使用相同注解的符号 → `conceptually_related_to` 边 |

确定性边 confidence=EXTRACTED，confidenceScore=算法相似度分数。

### 4.3 LLM 增强提取器（远期，依赖 nop-ai）

`LlmSemanticExtractor`（extractorId = "llm-claude" 或 "llm-gpt"）：
- 从符号表选择候选对（同一社区内、跨社区边界、高入口点分数）
- 构造 prompt → LLM 返回关系 JSON
- 只接受 confidenceScore >= 阈值的边
- **异步执行**，不阻塞 AST 分析管线
- **成本控制**：批量处理、SHA256 缓存、可配置 maxTokensPerProject

---

## 五、集成到现有分析管线

### 5.1 ProjectAnalyzer 扩展

`ProjectAnalysisResult` 新增 `semanticEdges` 字段。分析管线在 AST 分析完成后，遍历已注册的 `ISemanticEdgeExtractor`（跳过 `requiresLlm()=true` 且 LLM 未启用的提取器）。

### 5.2 社区检测感知语义边

语义边按 confidenceScore 加权后加入 CallGraph，参与 Leiden 聚类。可通过配置关闭。

### 5.3 影响分析感知语义边

影响传播不仅走 calls 边，也走语义边。若两个符号同时有 calls 边和 `semantically_similar_to` 边，影响风险更高。

---

## 六、数据库持久化

新增 `nop_code_semantic_edge` 表，核心字段：SID / INDEX_ID / SOURCE_SYMBOL_ID / TARGET_SYMBOL_ID / DIRECTED / RELATION_TYPE / CONFIDENCE / CONFIDENCE_SCORE / RATIONALE / EXTRACTOR_ID / EXT_DATA + 通用字段（CREATED_BY, CREATE_TIME, DEL_FLAG）。

dict 定义：`code/edge_confidence`（EXTRACTED=10, INFERRED=20, AMBIGUOUS=30）和 `code/semantic_relation`（8 种预定义关系类型）。

---

## 七、GraphQL API

### 7.1 归属

- `findSemanticPath` → `NopCodeSymbolBizModel`（输入是符号 ID，计算两符号间的语义路径）
- `findPage` → `NopCodeSemanticEdgeBizModel`（CRUD 查询，需有 xmeta）

### 7.2 关键查询

- **符号语义关联**：通过 BizLoader `semanticEdges` 加载符号的关联语义边
- **语义路径**：`NopCodeSymbol__findSemanticPath(sourceSymbolId, targetSymbolId, maxDepth)` → 路径 + 总分
- **审核 INFERRED 边**：`NopCodeSemanticEdge__findPage(confidence=INFERRED, confidenceScore_ge=0.7)` → 待人工审核列表

---

## 八、与 graphify 的关键差异

| 方面 | graphify | nop-code |
|------|----------|----------|
| 边模型 | dict `{source, target, relation, confidence}` | 强类型 `CodeSemanticEdge` |
| 持久化 | 文件系统 `graph.json` | ORM 表 + GraphQL API |
| 确定性提取 | 只有 AST | AST + 名称相似度 + 文档关键词 + 注解模式 |
| LLM 集成 | Claude subagent 直接调用 | 通过 `nop-ai` 服务层抽象 |
| 缓存 | SHA256 文件缓存 | SHA256 + 数据库持久化 |
| 聚类参与 | 语义边直接参与 Leiden | 同样参与，可按 confidenceScore 加权 |
| 成本控制 | 无限制 | token 预算 + 可配置跳过 |

---

## 九、设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 语义边是否参与聚类 | **可配置**（默认参与） | 语义边可能引入噪音，应允许关闭 |
| 确定性边是否算语义边 | **统一** | 简化消费端逻辑，通过 confidence 区分 |
| LLM 提取是否阻塞管线 | **异步**（写入后触发） | LLM 调用耗时长，不应阻塞 AST 分析 |
| 关系类型是否枚举 | **预定义 + "llm_" 前缀** | 平衡类型安全与 LLM 灵活性 |
| 语义边是否有向 | **混合**（directed 字段控制） | `semantically_similar_to` 天然无向 |
| `findSemanticPath` 归属 | `NopCodeSymbolBizModel`（非独立 BizModel） | 语义路径是符号级计算，NopCodeSemanticEdgeBizModel 只负责 CRUD |

---

## 附录：graphify 参考

graphify 的核心启发：
1. **三级置信度**（EXTRACTED / INFERRED / AMBIGUOUS）让消费者知道边有多可靠
2. **语义相似边参与聚类** — "The graph structure is the similarity signal"
3. **复合惊喜评分** — 多维度打分找非显而易见的连接
4. **SHA256 缓存** — 避免重复 LLM 调用
