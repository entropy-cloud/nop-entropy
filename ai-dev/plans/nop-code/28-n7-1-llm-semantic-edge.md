# 28 N7.1 语义边 LLM 增强（LlmSemanticEdgeExtractor）

> Plan Status: draft
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N7.1；`ai-dev/design/nop-code/semantic-edge-design.md` §4.3；先例模块 nop-code-core 确定性提取器（NameSimilarityExtractor 等）
> Related: N4.2（Embedding 客户端已完成——LLM 调用经 nop-ai IChatService）

## Purpose

落地 roadmap N7.1：`LlmSemanticEdgeExtractor` 实现 `ISemanticEdgeExtractor`——用 LLM 从符号表中选取候选对并推断语义关系。异步执行不阻塞 AST 管线；SHA256 缓存避免重复调用；成本预算（maxTokensPerProject）控制费用。

## Current Baseline

- `ISemanticEdgeExtractor`（nop-code-core semantic/）：getExtractorId/extract(SymbolTable, CallGraph)/requiresLlm()/estimatedTokens(SymbolTable)/extractFromFileResults。
- `CodeSemanticEdge`：sourceSymbolId/targetSymbolId/relationType(SemanticRelationType)/confidence(EdgeConfidence)/confidenceScore/rationale/extractorId。
- `SemanticRelationType` 8 值（SEMANTICALLY_SIMILAR_TO 等）。
- `EdgeConfidence.INFERRED` 用于 LLM 推断边。
- 确定性提取器 3 个已实现（NameSimilarityExtractor/DocKeywordExtractor/AnnotationPatternExtractor）。
- nop-ai IChatService：call(ChatRequest, ICancelToken) → ChatResponse.outputText()。
- **nop-code-service pom 当前不依赖 nop-ai-api**（审查 B1 实证）——Phase 1 需新增该依赖。
- `ProjectAnalyzer` 代码路径：`if (!extractor.requiresLlm())` 无条件跳过 LLM 提取器（审查 B2 实证）——**v1 接线裁定 = CodeIndexService 分析完成后显式调用**（不在 nop-code-core 改动）。

## Goals

- G1：`LlmSemanticEdgeExtractor implements ISemanticEdgeExtractor`（nop-code-service，M1 裁定：v1 候选对 = qn 前缀相同 + CallGraph 边界启发式，社区边界选取 deferred 至 successor）；extractorId="llm-infer"（m1 裁定，Phase 2 回写 design §4.3）、requiresLlm()=true、LLM prompt 构造、JSON 解析、置信度过滤。
- G1-b（B2 接线裁定）：`CodeIndexService` 在分析完成后显式调用 LLM 提取器（配置开关 `nop.code.llm-semantic.enabled`，缺省 false——生产环境可选启用）。
- G2：`LlmEdgeCache`（SHA256 键缓存 LLM 结果避免重复调用）。
- G3：`LlmEdgeBudget`（maxTokensPerProject 成本预算控制，超限停止调用）。
- G4：测试：Stub IChatService（确定性 JSON 回答）→ 候选选取/prompt 构造/JSON 解析/置信度过滤/缓存命中不重复调用/预算超限停止。

## Non-Goals

- 嵌入向量语义搜索（那是 K3 管线的事）；pgvector 集成；异步 BatchExecutor（v1 同步调用，异步由消费方包装）。

## Scope

### In Scope

- `nop-code/nop-code-service`：`semantic/LlmSemanticEdgeExtractor`、`semantic/LlmEdgeCache`、`semantic/LlmEdgeBudget`。
- `nop-code/nop-code-service` tests。

### Out Of Scope

- nop-code-core 改动（SPI 接口不变）；异步执行器；GraphQL API 暴露。

## Execution Plan

### Phase 1 - 实现 + 测试

Status: planned
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/semantic/`、`src/test/**`

- Item Types: `Fix`

- [ ] `LlmEdgeBudget`：maxTokensPerProject 构造参数；`tryConsume(int estimatedTokens) → boolean`；`remaining()`
- [ ] `LlmEdgeCache`：`getOrCompute(String promptKey, Supplier<String>) → String`，SHA256(prompt) 为键，ConcurrentHashMap
- [ ] `LlmSemanticEdgeExtractor`：构造注入 `IChatService` + `LlmEdgeBudget` + `LlmEdgeCache` + `confidenceThreshold`（缺省 0.7）；extract() 从 SymbolTable.getAll() 取符号、候选对选取 = qn 前缀匹配（同包/同类内）+ CallGraph 互补边（M1 裁定：社区边界 deferred）；prompt 下发 qn、解析回 getByQualifiedName 回查 symbol.getId()；JSON relation 限定 8 值枚举白名单（M2：未知值 WARN+跳过）；置信度 >= threshold 生成 CodeSemanticEdge(INFERRED, sourceSymbolId=symbol.getId(), directed=false, indexId)；预算耗尽停止
- [ ] service 接线：`CodeIndexService` 增 `@InjectValue("@cfg:nop.code.llm-semantic.enabled|false")` 开关 + `LlmSemanticEdgeExtractor` 可选注入（`@Inject @Nullable`），分析完成后开关启用时显式调用并合并边
- [ ] 测试：Stub IChatService 返回确定性 JSON → 验证提取边数量/关系类型/置信度/rationale；缓存命中不重复调 LLM；预算耗尽停止；JSON 解析失败跳过该对（WARN）；confidenceScore < threshold 过滤；未知 relation 跳过（M2）

Exit Criteria:

- [ ] **端到端验证**：SymbolTable → LlmSemanticEdgeExtractor → CodeSemanticEdge 边列表完整走通
- [ ] `./mvnw test -pl nop-code/nop-code-service -am -T 1C` 全绿
- [ ] 无静默跳过：JSON 解析失败跳过该对（WARN 日志），不吞
- [ ] `ai-dev/logs/` 条目已更新

### Phase 2 - 文档 + roadmap

Status: planned
Targets: docs-for-ai、roadmap、semantic-edge-design.md 增注

- Item Types: `Fix`

- [ ] semantic-edge-design.md §4.3 增注 "已实现（N7.1）"
- [ ] docs-for-ai/03-modules/nop-code.md 增 LLM 语义边提取节
- [ ] roadmap N7.1 todo→done + 汇总
- [ ] check-doc-links exit 0；logs 条目

Exit Criteria:

- [ ] checker exit 0；roadmap N7.1 done
- [ ] logs 条目已更新

## Closure Gates

> **关闭条件**：所有条目勾选为 `[x]` 后才能 `Plan Status: completed`。

- [ ] N7.1 交付词逐项核对：LLM 语义关系抽取器 + 异步接口设计 + 成本预算 + 缓存 + 测试
- [ ] `scan-hollow-implementations --module nop-code/nop-code-service --severity high` 零 high finding
- [ ] `check-plan-checklist --strict` 对本 plan 退出码 0
- [ ] `check-doc-links --strict` 退出码 0
- [ ] Anti-Hollow Check：调用链连通（extract→prompt→chat→parse→edge）
- [ ] 独立子 agent closure-audit 完成并记录证据
- [ ] `./mvnw test -pl nop-code/nop-code-service -am -T 1C` 全绿

## Deferred But Adjudicated

### 异步 BatchExecutor

- Classification: `optimization candidate`
- Why Not Blocking Closure: v1 同步调用已可用；LlmSemanticEdgeExtractor 线程安全无共享可变状态，调用方可自行包装 CompletableFuture（roadmap "异步" 交付词以此满足——测试证明线程安全）。
- Successor Required: `no`

### 缓存持久化

- Classification: `optimization candidate`
- Why Not Blocking Closure: v1 纯内存 ConcurrentHashMap（进程生命周期内有效）；design §八 "SHA256 + 数据库持久化" 的持久化部分 deferred——重启后重调 LLM 的费用影响有限。
- Successor Required: `no`

## Non-Blocking Follow-ups

- GraphQL API 暴露 LLM 提取 action（optimization candidate）。

## Closure

Status Note: （closure 时填写）
Completed: （closure 时填写）

Closure Audit Evidence:

- Reviewer / Agent: （closure 时填写）
- Evidence: （closure 时填写）

Follow-up:

- （closure 时填写）
