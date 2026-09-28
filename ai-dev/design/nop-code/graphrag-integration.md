# GraphRAG 集成契约裁定（N7.2）

**日期**：2026-09-28
**裁定**：nop-code 图数据通过 **GraphWikiExporter Markdown 输出 → K3 RagIngestService.ingest** 桥接进 RAG 管线。语义边文本也可直接摄取。v1 不做自动同步/CallGraph 直通。

## 数据源选取

| 图数据源 | RAG 可摄取 | 桥接方式 |
|---------|-----------|---------|
| GraphWikiExporter 社区文章（Markdown） | ✅ | `exportWiki → 文章列表 → RagIngestService.ingest(indexId, docId, content)` |
| 语义边 rationale 文本 | ✅ | 直接作为 ingest content |
| 符号 signature + documentation | ✅ | 已在 nop-code 索引管线中覆盖（NopCodeSymbol 表 → searchCode） |
| CallGraph 原始边 | ❌ | 结构化数据非文本，不适 RAG ingest |
| ORM 表直通 | ❌ | nop-dao 和 RAG 管线分属不同架构层 |

## 排除项

- 自动化同步管线（v1 手动触发；自动同步需 nop-job + 变更检测 → optimization candidate）
- 子图结构化导出为 Knowledge Graph triple

## Extension points

- pgvector 生产 RAG 后端替换 InMemoryVectorStore（Delta 裁定）
- 子图 triple 导出（semantic edges → RDF/property graph）
