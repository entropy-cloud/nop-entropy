# 03 K3 RAG 摄取-检索管线（nop-ai-rag）

> Plan Status: draft
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/knowledge-rag-roadmap.md` K3；前置 K1（Embedding 客户端）+ K2（向量库驱动）已完成；nop-code N7.2（GraphRAG 集成）的管线前置
> Related: `ai-dev/plans/knowledge-rag/01-k1-embedding-api-client.md`、`02-k2-vector-store-driver.md`

## Purpose

落地 knowledge-rag K3：在 nop-ai-rag 内实现摄取（ingest）→ 切分 → 嵌入 → 索引 → 检索（hybrid retrieve）→ 合成（synthesize）的最小完整管线，暴露 `@BizQuery`/`@BizMutation` API。使 nop-code N7.2（GraphRAG 集成契约裁定）可启动。

## Current Baseline

- K1 完成：`EmbeddingServiceImpl implements IEmbeddingModel`（bean `nopAiEmbeddingModel`），OpenAI 兼容 + 限流/重试/failover。
- K2 完成：`PgVectorStore` + `InMemoryVectorStore` 实现 `IVectorStore` SPI（tenant+collection 隔离）；两实现均不注册 default bean。
- nop-ai-rag 已有：`NopAiRagErrors`、`vector/` 包（K2）；依赖 nop-ai-core + test junit + postgresql(test+optional)。
- 切分原语在档：`IAiTextSplitter`（nop-ai-core commons/splitter）。
- `IChatService`/`IEmbeddingModel` 为 nop-ai-api/nop-ai-core 接口。
- `NopAiKnowledge` 实体（K3 交付词说"先用既有"——v1 管线用 InMemory/外部向量库，不新增 ORM 实体）。
- text→embedding 转换归 K3（K2 A1 裁定）。

## Adjudications（审查 #1-#5 裁定——决定类签名/装配/schema）

- **A1（bean 装配）**：`rag-defaults.beans.xml` 注册 `InMemoryVectorStore` 为 `ioc:default="true"` + `ioc:type=IVectorStore`（开发/测试缺省），生产通过 Delta 替换为 `PgVectorStore`。Embedding/Chat 已有 nop-ai-core default bean（`nopAiEmbeddingModel`/`nopChatService`），直接注入。三管线服务 + BizModel 注册为 bean（注入上述 SPI）。
- **A2（score 来源）**：`RagSearchService` 在检索后**重算 cosine**（query.vector × result.vector，A1“结果始终带向量”）。不改 `IVectorStore` SPI。
- **A3（hybrid 裁定）**：v1 = **纯向量检索**（K2 契约）。roadmap 交付词 "hybrid retrieve" 的 BM25+RRF 部分显式 deferred 至 K8（多库扇出与分数融合）——Deferred But Adjudicated 登记。
- **A4（xbiz 裁定）**：Java `@BizModel` + bean 注册即暴露 GraphQL（WorkflowServiceImpl 先例）——**不建 xbiz 文件**（避免重复定义/遮蔽/死文件）。
- **A5（ingestion 形态）**：v1 同步 `@BizMutation`（nop-job 异步编排归 K12 后继）。
- **A6（tenantId）**：v1 三个 action 不暴露 tenant 参数（内部归 `_default_`），K13 引入租户维度时扩展签名。
- **A7（SplitOptions）**：代码内 `new SimpleTextSplitter()` + `SplitOptions.create(512)`（缺省 maxContentSize）。
- **A8（ChatOptions）**：synthesize 不设 ChatOptions（透传 null → IChatService 缺省），缺省模型由 llm.xml default 配置决定。

## Goals

- G1：`RagIngestService`：输入文档 → splitter 切分 → `IEmbeddingModel.embedAll` → `IVectorStore.store`；每个 chunk 保留 source 文档 id + 段序号。
- G2：`RagSearchService`：查询文本 → embed → `IVectorStore.search` → top-K 结果（含 content/metadata/score）。
- G3：`RagSynthesizeService`：检索结果 + 查询文本 → `IChatService`（prompt = 上下文 + 问题）→ 合成回答。
- G4：`NopAiRagBizModel`（`@BizModel`）：`@BizMutation ingestDocument(indexId, docId, content)`、`@BizQuery search(indexId, query, topK)`、`@BizQuery synthesize(indexId, query, topK)`。
- G5：bean 装配（`rag-defaults.beans.xml`，A1）+ 测试（A4：不建 xbiz，Java @BizModel 即暴露）（InMemoryVectorStore + MockChatService/StubEmbeddingModel 确定性全链路）。

## Non-Goals

- chunk 持久化 ORM 实体/编辑/版本回滚（K4）；rerank/MMR（K5/K6）；父子分块（K7）；多库扇出（K8）；citation 回写（K9）；OCR/连接器（K10/K12）。
- pgvector 集成测试（K2 deferred watch-only）。

## Scope

### In Scope

- `nop-ai/nop-ai-rag`：`ingest/RagIngestService`、`search/RagSearchService`、`synthesize/RagSynthesizeService`、`biz/NopAiRagBizModel`、`rag-defaults.beans.xml`（不注册 default——消费方显式装配，但 BizModel 需注册供 GraphQL 发现）、xbiz 文件、pom 增 nop-graphql 相关 test 依赖。
- tests：全链路 InMemory + Stub。

### Out Of Scope

- K4-K12/K18；nop-app-erp 接线；web/app 模块；hybrid 检索 BM25+RRF（A3 deferred 至 K8）；chunk 持久化（K4）。

## Execution Plan

### Phase 1 - 管线三服务 + BizModel + 测试

Status: planned
Targets: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/rag/`（ingest/search/synthesize/biz 四包）、`rag-defaults.beans.xml`、`src/test/**`

- Item Types: `Fix`

- [ ] `RagIngestService`：注入 `IAiTextSplitter`（SimpleTextSplitter 缺省）、`IEmbeddingModel`、`IVectorStore`；`ingest(indexId, docId, content)` → split → embed(batch) → store(chunk metadata: docId/chunkIndex/content)；返回 chunk 数
- [ ] `RagSearchService`：注入 `IEmbeddingModel`、`IVectorStore`；`search(indexId, query, topK)` → embed query → store.search → 结果列表（score/content/metadata）
- [ ] `RagSynthesizeService`：注入 `IChatService`、`RagSearchService`；`synthesize(indexId, query, topK)` → search → 上下文拼接 → chat → 回答文本
- [ ] `NopAiRagBizModel`（`@BizModel`）：三个 `@BizQuery`/`@BizMutation` 委托
- [ ] `rag-defaults.beans.xml`（A1）：InMemoryVectorStore(ioc:default+ioc:type=IVectorStore) + 三管线服务 bean + BizModel bean（注入 nopAiEmbeddingModel/nopChatService nop-ai-core default bean）
- [ ] `NopAiRagErrors` 增错误码：ERR_AI_RAG_EMPTY_DOCUMENT/ERR_AI_RAG_EMPTY_QUERY/ERR_AI_RAG_NO_RESULTS
- [ ] 测试（纯 JUnit，Rule #25 同 Phase）：Stub IEmbeddingModel + Stub IChatService + InMemoryVectorStore → **经 NopAiRagBizModel action 入口驱动**（Rule #23 接线验证）→ ingest 多文档 → search top-K 排序正确（A2 重算 cosine）→ synthesize 非空回答；空文档/空查询 fail-loud；bean 装配冒烟（IoC 容器加载 rag-defaults 断言 BizModel 可解析）

Exit Criteria:

- [ ] **端到端验证**：经 BizModel action 入口 ingest 多 chunk → search 命中 → synthesize 非空回答全链路（Stub 驱动）
- [ ] **接线验证**：BizModel bean 装配冒烟 + 三服务委托非空壳
- [ ] `./mvnw test -pl nop-ai/nop-ai-rag -am -T 1C` 全绿
- [ ] No owner-doc update required（Phase 2 统一新建 rag-pipeline.md）
- [ ] `ai-dev/logs/` 条目已更新

### Phase 2 - xbiz + 文档 + roadmap

Status: planned
Targets: docs-for-ai、roadmap、04-rag-module-position

- Item Types: `Fix`

- [ ] 无 xbiz（A4 裁定：Java @BizModel + bean 即暴露）
- [ ] owner doc `ai-dev/design/nop-ai/rag-pipeline.md`（NEW）：管线架构/A1-A6 裁定/chunk metadata 契约/synthesize prompt 契约/score 来源（A2）/hybrid deferred 至 K8（A3）/tenantId v1 裁定（A6）/SplitOptions 缺省/extension points
- [ ] `docs-for-ai/03-modules/nop-ai.md` RAG 管线节；`04-rag-module-position.md` 增注 K3 落地
- [ ] knowledge-rag roadmap K3 todo→ready→done（closure audit 后）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；logs

Exit Criteria:

- [ ] checker exit 0；roadmap K3 done
- [ ] `ai-dev/logs/` 条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] K3 交付词逐项：ingest→chunk→embed→index→retrieve→synthesize 全链路 + BizModel API + 测试
- [ ] `scan-hollow-implementations --module nop-ai/nop-ai-rag --severity high` 零 high finding
- [ ] **Anti-Hollow Check**：closure audit 验证 BizModel→三服务→IVectorStore/IChatService 调用链连通 + 无空方法体
- [ ] owner doc rag-pipeline.md 在档
- [ ] `check-plan-checklist --strict` 对本 plan 退出码 0
- [ ] `check-doc-links --strict` 退出码 0
- [ ] `./mvnw compile -pl nop-ai/nop-ai-rag -am -o` 通过
- [ ] 独立子 agent closure-audit 完成并记录证据
- [ ] `./mvnw test -pl nop-ai/nop-ai-rag -am -T 1C` 全绿

## Deferred But Adjudicated

### chunk 持久化 ORM 实体

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: K4 明确承接
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/knowledge-rag-roadmap.md` K4

### hybrid 检索（BM25 + vector RRF 融合）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: v1 纯向量检索（A3 裁定）；hybrid BM25+RRF 依赖 nop-search HYBRID 集成，归 K8（多库扇出与分数融合）
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/knowledge-rag-roadmap.md` K8

## Non-Blocking Follow-ups

- 流式 synthesize、batch ingest API（optimization candidate）。

## Closure

Status Note: （closure 时填写）
Completed: （closure 时填写）

Closure Audit Evidence:

- Reviewer / Agent: （closure 时填写）
- Evidence: （closure 时填写）

Follow-up:

- （closure 时填写）
