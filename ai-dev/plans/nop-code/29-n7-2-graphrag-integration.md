# 29 N7.2 GraphRAG 集成契约裁定（Decision）

> Plan Status: draft
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N7.2；前置：K3 完成（RAG 管线已落地）；N1.2 done（图度量物化）
> Related: K3（管线消费方）、N9.x（端到端验收）

## Purpose

裁定 nop-code 图数据（社区/子图/导出面）暴露给 RAG 管线（K3 的 RagSearchService/RagSynthesizeService）的集成形态：哪些图数据作为 RAG 检索上下文、通过什么接口桥接、不做哪些。产出裁定记录 + design 增注 + 最小接线测试。

## Current Baseline

- K3 完成：`RagIngestService`/`RagSearchService`/`RagSynthesizeService` + `NopAiRagBizModel`（ingest/search/synthesize 三 action）在 nop-ai-rag。
- nop-code 侧已有：`GraphWikiExporter`（社区文章 Markdown 导出）、`GraphSnapshotService`（子图快照）、`CodeSemanticEdge`（确定性 + LLM 语义边）、`CommunityDetector` + `GraphMetricMaterializer`（社区度量物化）。
- RAG 管线消费向量数据（text→embedding→cosine search），不直接消费图结构。

## Goals

- G1：裁定记录 `ai-dev/design/nop-code/graphrag-integration.md`（NEW）：哪些图数据可 RAG 摄取（社区 Wiki 文章/语义边 rationale/符号文档）、桥接接口形态（GraphWikiExporter 输出 → RagIngestService.ingest）、不做哪些（原始 CallGraph/ORM 表直通）。
- G2：最小接线测试：GraphWikiExporter 导出 → RagIngestService.ingest → RagSearchService.search 命中（证明桥接可行）。
- G3：design 增注（graphrag-integration.md 即裁定记录）。

## Non-Goals

- K3 管线代码改动（桥接在消费方完成）；pgvector 集成；自动化同步管线（v1 手动触发）。

## Execution Plan

### Phase 1 - 裁定 + 接线测试

Status: planned
Targets: `ai-dev/design/nop-code/graphrag-integration.md`（NEW）、`nop-code/nop-code-service/src/test/java/io/nop/code/service/graph/TestGraphRagIntegration.java`

- Item Types: `Decision`、`Proof`

- [ ] 裁定记录 `graphrag-integration.md`：数据源选取（GraphWikiExporter 社区文章 / 语义边 rationale / 符号 signature+doc）、桥接接口（`GraphWikiExporter.exportWiki → Markdown 文章列表 → RagIngestService.ingest(docId, content)`）、排除项（CallGraph 原始边、ORM 表直通、自动同步）
- [ ] 最小接线测试（K2 InMemoryVectorStore + Stub IEmbeddingModel + Stub IChatService + nop-code GraphWikiExporter → K3 RagIngestService/RagSearchService）：GraphWikiExporter 导出社区文章 → ingest → search "reversible computation" 命中相关文章
- [ ] nop-code-service pom 已有 nop-ai-rag 依赖？若无需加（test scope 足够）

Exit Criteria:

- [ ] 接线测试全绿（GraphWiki→ingest→search 全链路走通）
- [ ] 裁定记录在档（含排除项/extension points）
- [ ] `ai-dev/logs/` 条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] N7.2 交付词逐项：集成形态裁定 + design 增注 + 最小接线证明
- [ ] `check-plan-checklist --strict` 对本 plan 退出码 0
- [ ] `check-doc-links --strict` 退出码 0
- [ ] 独立子 agent closure-audit 完成并记录证据
- [ ] 接线测试全绿

## Deferred But Adjudicated

### 自动化同步管线

- Classification: `optimization candidate`
- Why Not Blocking Closure: v1 手动触发；自动同步需要 nop-job + 变更检测，另立 plan。
- Successor Required: `no`

## Non-Blocking Follow-ups

- pgvector 集成（生产 RAG 后端替换 InMemory）；子图结构化导出为 Knowledge Graph triple（optimization candidate）。

## Closure

Status Note: （closure 时填写）
Completed: （closure 时填写）

Closure Audit Evidence:

- Reviewer / Agent: （closure 时填写）
- Evidence: （closure 时填写）

Follow-up:

- （closure 时填写）
