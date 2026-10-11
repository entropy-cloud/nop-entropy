# nop-ai-rag — RAG 实现落点模块（IVectorStore SPI 实现）

> 落点裁定：P3-MA3-003 + 04-rag-module-position.md §四（触发条件已由 K3 达成，§八 登记）。

## 本模块的定位

- **IVectorStore SPI 的实现落点**（K2 起，plan knowledge-rag/02）：
  - `io.nop.ai.rag.vector.PgVectorStore` — pgvector JDBC 驱动（生产后端；集成方注入 `javax.sql.DataSource`）
  - `io.nop.ai.rag.vector.InMemoryVectorStore` — 内存参考实现（K3 管线测试 / 无 pgvector 环境开发）
- 两实现均**不注册 IoC default bean**（保 SPI 边界语义），装配归消费方（K3/集成方）。
- 契约裁定（A1-A4）见 `ai-dev/design/nop-ai/vector-store.md`。

## 使用约束

- **RAG 域 SPI 已随 plan 2421（roadmap WI4，2026-10-11）迁入本模块**：`io.nop.ai.core.api.vectorstore.IVectorStore` 族、`CosineSimilarity`/`RelevanceScore`、classifier 族、`AiModelTextEmbedding` 桥接——**包名保留 `io.nop.ai.core.*`**（模块归属变更、包名不变，零 API 破坏；重启条件：未来 major 版本 API 清理时改 `io.nop.ai.rag.*`，动因含消除 `io.nop.ai.core.api.embedding` 的跨模块 split package）。
- `IEmbeddingModel`/`EmbeddingOptions` 留守 nop-ai-core（LLM 内核输出 SPI——`EmbeddingServiceImpl` implements 它且依赖内核，接口外迁即 core→rag 倒置，plan 2421 排除性裁定）。
- 本模块的代码**不得引入 nop-ai 模块组之外的实现耦合**（原「只允许实现 nop-ai-core 的 SPI 契约」约束随 SPI 迁入调整为：跨模块依赖仅限 nop-ai-core 的留守面 + nop-search-api/nop-search-lucene 桥接面）。
- pgvector JDBC 驱动（org.postgresql:postgresql）为 test+optional scope，生产部署需自带。
