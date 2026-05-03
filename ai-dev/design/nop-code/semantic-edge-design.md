# nop-code 语义边（Semantic Edge）设计

**日期**：2026-05-03
**范围**：`nop-code-core` + `nop-ai` 集成
**目标**：在确定性 AST 分析的基础上，引入 LLM 辅助的语义边提取，发现跨文件/跨模块的非结构化概念关联
**灵感来源**：graphify 项目（`~/ai/graphify`）的 INFERRED 边机制

---

## 一、背景与动机

### 1.1 现状

nop-code 已实现的分析能力全部基于**确定性 AST 提取**：

| 能力 | 实现位置 | 边类型 |
|------|----------|--------|
| 调用图 | `CallGraph` | `calls`（caller → callee） |
| 继承图 | `CodeInheritance` | `extends` / `implements` |
| 注解使用 | `CodeAnnotationUsage` | `annotated_by` |
| 符号包含 | `CodeSymbol.parentId` | `contains` |
| 导入关系 | `CodeFileAnalysisResult.imports` | `imports` |

这些边都是 **EXTRACTED**（从源码直接提取，100% 确定性）。

### 1.2 缺失

纯结构分析无法发现以下关系：

| 场景 | 示例 | 纯 AST 能否发现 |
|------|------|----------------|
| 两个函数解决同一问题但无调用关系 | `validateEmail()` 和 `checkEmailFormat()` 都验证邮箱 | ❌ |
| 代码与设计文档描述同一概念 | `JwtAuthFilter` ↔ 设计文档中的 "认证拦截" 章节 | ❌ |
| 同一设计模式的不同实现 | `StrategyFactory` 和 `PluginRegistry` 都是策略模式 | ❌ |
| 配置项与使用处的语义映射 | `timeout.ms=5000` ↔ `RetryPolicy` 中的超时逻辑 | ❌ |
| 跨语言概念关联 | Java `UserService` ↔ TypeScript `UserServiceClient` | ❌ |

这些关系需要**语义理解**，即 LLM 推理。

### 1.3 graphify 的做法

graphify 用 Claude subagent 并行提取语义边：

```
extract.py (AST, 确定性)  →  EXTRACTED 边
Claude subagent (LLM)     →  INFERRED 边 (confidence_score: 0.0-1.0)
                            →  AMBIGUOUS 边 (不确定)
```

每条边标记 `confidence: EXTRACTED | INFERRED | AMBIGUOUS`，让消费者知道什么是确定的、什么是推测的。

---

## 二、设计目标

1. **在 nop-code-core 中定义语义边模型**，不依赖任何 LLM 实现
2. **提供确定性语义边的基础实现**（基于名称相似度、文档关键词匹配等，不依赖 LLM）
3. **预留 LLM 增强接口**，未来通过 `nop-ai` 模块接入
4. **语义边持久化到数据库**，通过 GraphQL 查询
5. **社区检测和影响分析感知语义边**，语义边参与聚类

---

## 三、核心模型

### 3.1 CodeEdgeConfidence（置信度枚举）

```java
package io.nop.code.core.model;

/**
 * 边的置信度
 * 
 * 灵感来自 graphify 的 EXTRACTED/INFERRED/AMBIGUOUS 标签
 */
public enum CodeEdgeConfidence {
    EXTRACTED(10, "确定性提取"),     // AST / 配置解析直接得到
    INFERRED(20, "语义推断"),       // LLM 或算法推断，有置信分数
    AMBIGUOUS(30, "不确定"),        // 需要人工审核
    ;

    private final int value;
    private final String label;

    CodeEdgeConfidence(int value, String label) {
        this.value = value;
        this.label = label;
    }
}
```

### 3.2 CodeSemanticEdge（语义边模型）

```java
package io.nop.code.core.model;

/**
 * 语义边 — 连接两个符号的非结构化关系
 * 
 * 与 CallGraph 的 calls 边不同，语义边是：
 * 1. 可能有方向，也可能无向（semantically_similar_to）
 * 2. 有置信度（EXTRACTED / INFERRED / AMBIGUOUS）
 * 3. 有推断来源（哪个算法或 LLM 产生的）
 * 4. 有可读的 why 解释（人可理解的原因）
 */
@DataBean
public class CodeSemanticEdge {
    private String id;
    
    // ===== 端点 =====
    private String sourceSymbolId;       // 源符号 ID
    private String targetSymbolId;       // 目标符号 ID
    private boolean directed;            // true=有向边，false=无向边
    
    // ===== 关系 =====
    private String relationType;         // 关系类型（见 3.3）
    private CodeEdgeConfidence confidence;  // 置信度
    private double confidenceScore;      // 0.0-1.0，INFERRED 时有意义
    
    // ===== 解释 =====
    private String rationale;            // 为什么认为存在这条边（人可读）
    private String whyNot;               // 为什么可能不存在（AMBIGUOUS 时）
    
    // ===== 来源 =====
    private String extractorId;          // 产生这条边的提取器 ID
    // "ast"          = AST 确定性提取
    // "name-sim"     = 名称相似度算法
    // "doc-keyword"  = 文档关键词匹配
    // "llm-claude"   = Claude LLM 推断
    // "llm-gpt"      = GPT LLM 推断
    
    // ===== 位置 =====
    private String sourceFile;           // 源符号所在文件
    private String targetFile;           // 目标符号所在文件
    
    // ===== 扩展 =====
    private String extData;              // JSON：额外数据
}
```

### 3.3 语义边关系类型

```java
// 预定义关系类型，存 relationType 字段
// 命名规则：小写+下划线，与 graphify 保持一致

// --- 语义相似 ---
"semantically_similar_to"    // 语义相似（无向）
"conceptually_related_to"    // 概念相关（无向）
"solves_same_problem"        // 解决同一问题

// --- 设计模式 ---
"implements_pattern"          // 实现了某个设计模式
"alternative_of"             // 互斥替代方案

// --- 文档关联 ---
"documented_by"              // 被文档描述
"rationale_for"              // 设计原因（代码 ↔ 文档）

// --- 跨语言 ---
"cross_language_peer"         // 跨语言对等（Java ↔ TypeScript）

// --- LLM 自定义 ---
// LLM 可输出任意 relationType，只要在白名单内或带有 "llm_" 前缀
```

---

## 四、语义边提取器接口

### 4.1 ISemanticEdgeExtractor

```java
package io.nop.code.core.analyzer;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSemanticEdge;
import java.util.List;

/**
 * 语义边提取器接口
 * 
 * 多种实现：
 * - 确定性算法（名称相似度、文档匹配）
 * - LLM 推断（通过 nop-ai）
 */
public interface ISemanticEdgeExtractor {
    
    /**
     * 提取器 ID
     * 例: "name-sim", "doc-keyword", "llm-claude"
     */
    String getExtractorId();
    
    /**
     * 从已有符号表和调用图中提取语义边
     * 
     * @param symbolTable 全局符号表（已由 ProjectAnalyzer 构建）
     * @param callGraph   全局调用图（已由 ProjectAnalyzer 构建）
     * @return 语义边列表
     */
    List<CodeSemanticEdge> extract(SymbolTable symbolTable, CallGraph callGraph);
    
    /**
     * 该提取器是否需要 LLM（用于成本估算和跳过决策）
     */
    boolean requiresLlm();
    
    /**
     * 预估 token 消耗（仅 requiresLlm()=true 时有意义）
     * @return 预估 input token 数，-1 表示无法预估
     */
    long estimatedTokens(SymbolTable symbolTable);
}
```

### 4.2 确定性提取器（不依赖 LLM）

```java
package io.nop.code.core.analyzer.semantic;

/**
 * 名称相似度语义边提取器
 * 
 * 算法：
 * 1. 收集所有 METHOD/FUNCTION 符号
 * 2. 对符号名称做标准化（去前后缀、转小写、拆 camelCase/snake_case）
 * 3. 计算 Levenshtein 距离 / Jaccard 词集合相似度
 * 4. 超过阈值的符号对生成 semantically_similar_to 边
 * 5. 置信度 EXTRACTED（因为是确定性算法），confidenceScore=相似度分数
 */
public class NameSimilarityExtractor implements ISemanticEdgeExtractor {
    // extractorId = "name-sim"
    // requiresLlm() = false
}

/**
 * 文档关键词语义边提取器
 * 
 * 算法：
 * 1. 收集所有有 documentation 的符号
 * 2. 提取文档中的关键词（TF-IDF 或简单分词）
 * 3. 两个符号的文档关键词交集超过阈值 → conceptually_related_to 边
 */
public class DocKeywordExtractor implements ISemanticEdgeExtractor {
    // extractorId = "doc-keyword"
    // requiresLlm() = false
}

/**
 * 注解模式提取器
 * 
 * 算法：
 * 1. 发现使用相同注解的符号（如 @EventListener, @Transactional）
 * 2. 这些符号在概念上属于同一关注点 → conceptually_related_to
 */
public class AnnotationPatternExtractor implements ISemanticEdgeExtractor {
    // extractorId = "annotation-pattern"
    // requiresLlm() = false
}
```

### 4.3 LLM 增强提取器（Phase 2，依赖 nop-ai）

```java
package io.nop.code.core.analyzer.semantic;

/**
 * LLM 语义边提取器
 * 
 * 通过 nop-ai 模块调用 LLM，批量分析符号对。
 * 
 * 策略：
 * 1. 从符号表中选择候选对（同一社区内、跨社区边界、高入口点分数）
 * 2. 构造 prompt：给出两个符号的签名+文档+调用上下文
 * 3. LLM 返回 JSON：是否有语义关系、关系类型、置信度、原因
 * 4. 只接受 confidence_score >= 阈值的边
 * 
 * 成本控制：
 * - 批量处理（每 prompt 分析 N 对）
 * - SHA256 缓存（同 graphify 的 cache 机制）
 * - 可配置 maxTokensPerProject
 */
public class LlmSemanticExtractor implements ISemanticEdgeExtractor {
    // extractorId = "llm-claude" 或 "llm-gpt"
    // requiresLlm() = true
    
    // 依赖 nop-ai 的 IChatService 或类似接口
}
```

---

## 五、集成到现有分析管线

### 5.1 ProjectAnalyzer 扩展

```java
// ProjectAnalyzer.analyzeProject() 扩展

public class ProjectAnalysisResult {
    // ... 现有字段 ...
    
    /**
     * 语义边列表（新增）
     */
    private List<CodeSemanticEdge> semanticEdges = new ArrayList<>();
}

// ProjectAnalyzer 新增：
public ProjectAnalysisResult analyzeProject(Path projectRoot) {
    // Phase 1-3: 现有 AST 分析 → symbolTable + callGraph
    
    // Phase 4: 语义边提取（新增）
    List<ISemanticEdgeExtractor> extractors = getSemanticExtractors();
    for (ISemanticEdgeExtractor extractor : extractors) {
        if (extractor.requiresLlm() && !llmEnabled) continue;
        
        List<CodeSemanticEdge> edges = extractor.extract(symbolTable, callGraph);
        result.getSemanticEdges().addAll(edges);
    }
}
```

### 5.2 社区检测感知语义边

```java
// CommunityDetector 扩展：
// 将 semanticEdges 中的边加入 CallGraph，参与 Leiden 聚类

public static CommunityDetectionResult detectCommunities(
        CallGraph callGraph,
        SymbolTable symbolTable,
        List<CodeSemanticEdge> semanticEdges,  // 新增
        CommunityConfig config) {
    
    // 将语义边加入调用图（权重按 confidenceScore 缩放）
    for (CodeSemanticEdge edge : semanticEdges) {
        if (edge.isDirected()) {
            callGraph.addEdge(edge.getSourceSymbolId(), edge.getTargetSymbolId());
        } else {
            callGraph.addEdge(edge.getSourceSymbolId(), edge.getTargetSymbolId());
            callGraph.addEdge(edge.getTargetSymbolId(), edge.getSourceSymbolId());
        }
    }
    
    // 然后正常执行 Leiden / LabelPropagation
}
```

### 5.3 影响分析感知语义边

```java
// ImpactAnalyzer 扩展：
// 影响传播不仅走 calls 边，也走语义边

// 如果 A → B 有 calls 边 AND semantically_similar_to 边
// 则 A 变更对 B 的影响风险更高
```

---

## 六、数据库持久化

### 6.1 新增表：nop_code_semantic_edge

```xml
<entity name="NopCodeSemanticEdge" tableName="nop_code_semantic_edge">
    <column name="SID" type="VARCHAR(32)" primary="true"/>
    <column name="INDEX_ID" type="VARCHAR(32)"/>          -- 所属索引
    <column name="SOURCE_SYMBOL_ID" type="VARCHAR(200)"/> -- 源符号
    <column name="TARGET_SYMBOL_ID" type="VARCHAR(200)"/> -- 目标符号
    <column name="DIRECTED" type="BOOLEAN" default="true"/>
    <column name="RELATION_TYPE" type="VARCHAR(50)"/>     -- 关系类型
    <column name="CONFIDENCE" type="INTEGER"/>            -- 10=EXTRACTED, 20=INFERRED, 30=AMBIGUOUS
    <column name="CONFIDENCE_SCORE" type="DOUBLE"/>       -- 0.0-1.0
    <column name="RATIONALE" type="VARCHAR(500)"/>        -- 为什么
    <column name="EXTRACTOR_ID" type="VARCHAR(50)"/>      -- 提取器 ID
    <column name="EXT_DATA" type="VARCHAR(65535)"/>       -- JSON
    <!-- 通用字段 -->
    <column name="CREATED_BY" type="VARCHAR(50)"/>
    <column name="CREATE_TIME" type="DATETIME"/>
    <column name="DEL_FLAG" type="INTEGER" default="0"/>
</entity>
```

### 6.2 dict 定义

```xml
<dict label="边置信度" name="code/edge_confidence" valueType="int">
    <option code="EXTRACTED" label="确定性提取" value="10"/>
    <option code="INFERRED" label="语义推断" value="20"/>
    <option code="AMBIGUOUS" label="不确定" value="30"/>
</dict>

<dict label="语义边关系类型" name="code/semantic_relation" valueType="string">
    <option code="semantically_similar_to" label="语义相似"/>
    <option code="conceptually_related_to" label="概念相关"/>
    <option code="solves_same_problem" label="解决同一问题"/>
    <option code="implements_pattern" label="实现设计模式"/>
    <option code="alternative_of" label="互斥替代"/>
    <option code="documented_by" label="被文档描述"/>
    <option code="rationale_for" label="设计原因"/>
    <option code="cross_language_peer" label="跨语言对等"/>
</dict>
```

---

## 七、GraphQL API 扩展

### 7.1 查询语义边

```graphql
# 查询符号的语义关联
query {
  NopCodeSymbol__get(id: "sym_123") {
    name
    qualifiedName
    semanticEdges(direction: BOTH, relationTypes: ["semantically_similar_to"]) {
      items {
        relationType
        confidence
        confidenceScore
        rationale
        targetSymbol { name qualifiedName }
      }
    }
  }
}

# 查询两个符号之间的语义路径
query {
  NopCodeSemanticEdge__findSemanticPath(
    sourceSymbolId: "sym_auth_filter"
    targetSymbolId: "sym_token_validator"
    maxDepth: 3
  ) {
    path { symbolId relationType confidence }
    totalScore
  }
}

# 查询所有 INFERRED 边（需人工审核）
query {
  NopCodeSemanticEdge__findPage(
    query: { confidence: INFERRED, confidenceScore_ge: 0.7 }
    orderBy: { field: "confidenceScore", desc: true }
  ) {
    items {
      sourceSymbol { name qualifiedName }
      targetSymbol { name qualifiedName }
      relationType
      confidenceScore
      rationale
    }
  }
}
```

---

## 八、实现优先级

### Phase 1：确定性语义边（不依赖 LLM）

| 步骤 | 内容 | 工作量 |
|------|------|--------|
| 1 | `CodeEdgeConfidence` 枚举 + `CodeSemanticEdge` 模型 | 0.5d |
| 2 | `ISemanticEdgeExtractor` 接口 | 0.5d |
| 3 | `NameSimilarityExtractor`（名称相似度） | 1d |
| 4 | `DocKeywordExtractor`（文档关键词） | 1d |
| 5 | `AnnotationPatternExtractor`（注解模式） | 0.5d |
| 6 | `ProjectAnalyzer` 集成语义边提取 | 0.5d |
| 7 | `nop_code_semantic_edge` ORM 表 + BizModel | 1d |
| 8 | 社区检测 + 影响分析感知语义边 | 1d |
| **总计** | | **~6d** |

### Phase 2：LLM 增强（依赖 nop-ai）

| 步骤 | 内容 | 工作量 |
|------|------|--------|
| 9 | `LlmSemanticExtractor` 骨架 + prompt 设计 | 2d |
| 10 | 批量分析策略（候选对选择、批处理） | 1d |
| 11 | SHA256 缓存（避免重复 LLM 调用） | 0.5d |
| 12 | 成本控制（token 预算、跳过决策） | 0.5d |
| 13 | GraphQL API 完善 | 1d |
| **总计** | | **~5d** |

---

## 九、与 graphify 的关键差异

| 方面 | graphify | nop-code |
|------|----------|----------|
| **边模型** | dict `{source, target, relation, confidence}` | 强类型 `CodeSemanticEdge`（12 字段） |
| **持久化** | 文件系统 `graph.json` | ORM 表 + GraphQL API |
| **确定性提取** | 只有 AST（calls, imports, contains） | AST + 名称相似度 + 文档关键词 + 注解模式 |
| **LLM 集成** | Claude subagent 直接调用 | 通过 `nop-ai` 服务层抽象（可切换模型） |
| **缓存** | SHA256 文件缓存 | SHA256 + 数据库持久化 |
| **聚类参与** | 语义边直接参与 Leiden | 同样参与，且可按 confidenceScore 加权 |
| **成本控制** | 无限制（用户自担） | token 预算 + 可配置跳过 |

---

## 十、设计决策

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 语义边是否参与聚类 | 是 / 否 / 可配置 | **可配置**（默认参与） | 语义边可能引入噪音，应允许关闭 |
| 确定性边是否算语义边 | 独立体系 / 统一 | **统一** | 简化消费端逻辑，通过 confidence 区分 |
| LLM 提取是否阻塞管线 | 同步 / 异步 | **异步**（写入后触发） | LLM 调用耗时长，不应阻塞 AST 分析 |
| 关系类型是否枚举 | 枚举 / 自由字符串 | **预定义 + "llm_" 前缀** | 平衡类型安全与 LLM 灵活性 |
| 语义边是否有向 | 全部有向 / 混合 | **混合**（directed 字段控制） | `semantically_similar_to` 天然无向 |

---

## 附录 A：graphify 参考

分析源码位置：`~/ai/graphify/graphify/`

| 文件 | 关键机制 |
|------|----------|
| `extract.py` | AST 提取 + call graph second pass 产生 INFERRED calls 边 |
| `analyze.py` | `_surprise_score()` 综合评分（confidence + file-type + cross-repo + cross-community + peripheral-hub） |
| `build.py` | `build_merge()` 增量合并语义节点到已有图 |
| `cache.py` | SHA256 文件缓存（`cache/semantic/` 独立于 `cache/ast/`） |

**核心启发**：
1. **三级置信度**（EXTRACTED / INFERRED / AMBIGUOUS）让消费者知道边有多可靠
2. **语义相似边参与聚类** — graphify 的关键设计："The graph structure is the similarity signal"
3. **复合惊喜评分** — 多维度打分找非显而易见的连接
4. **SHA256 缓存** — 避免重复 LLM 调用
