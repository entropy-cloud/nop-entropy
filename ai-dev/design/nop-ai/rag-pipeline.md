# RAG 管线设计（K3）

**日期**：2026-09-28
**范围**：`nop-ai/nop-ai-rag`（ingest/search/synthesize 三服务 + NopAiRagBizModel）
**状态**：active（K3 已落地）
**来源**：`ai-dev/backlog/knowledge-rag-roadmap.md` K3；执行计划 `ai-dev/plans/knowledge-rag/03-k3-rag-pipeline.md`

---

## 一、管线架构

```
ingest:  doc → IAiTextSplitter(512) → IEmbeddingModel.embedAll → IVectorStore.store
search:  query → IEmbeddingModel.embed(query) → IVectorStore.search → 重算 cosine → top-K hits
synthesize: search → context 拼接 → IChatService.call → answer
```

## 二、A1-A8 契约裁定

| 裁定 | 内容 |
|------|------|
| A1 bean 装配 | rag-defaults 注册 InMemoryVectorStore(ioc:default+ioc:type=IVectorStore)；生产 Delta 替换 PgVectorStore。Embedding/Chat 注入 nop-ai-core default bean |
| A2 score 来源 | RagSearchService 重算 cosine(query.vector × result.vector)；不改 IVectorStore SPI |
| A3 hybrid | v1 纯向量检索；BM25+RRF deferred 至 K8 |
| A4 xbiz | Java @BizModel + bean 即暴露；不建 xbiz 文件 |
| A5 ingestion | v1 同步 @BizMutation；nop-job 异步归 K12 |
| A6 tenantId | v1 三 action 不暴露 tenant（归 _default_）；K13 扩展 |
| A7 SplitOptions | 代码内 SimpleTextSplitter + create(512) |
| A8 ChatOptions | synthesize 透传 null（缺省模型由 llm.xml default 决定） |

## 三、chunk metadata 契约

每 chunk 的 VectorData Metadata 记录：`docId`（源文档 ID）、`chunkIndex`（段序号 0-based）、`content`（chunk 文本）、`indexId`（索引 ID）。检索命中后可回传来源。

## 四、synthesize prompt 契约

```
Based on the following context, answer the question.

Context:
[1] <chunk 1 content>

[2] <chunk 2 content>

Question: <query>

Answer:
```

## 五、score 来源

RagSearchService 在 IVectorStore.search 返回后，对每条 VectorData 重算 cosine(query.vector, result.vector)。不改 IVectorStore SPI（K2 A1 保证结果始终带向量）。

## 六、extension points

- hybrid 检索（BM25+RRF）→ K8
- chunk 持久化 ORM → K4
- rerank → K5
- 流式 synthesize → optimization candidate
