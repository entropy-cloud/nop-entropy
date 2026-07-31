# nop-ai-rag — RAG 实现落点模块（SPI 扩展点，当前为空占位）

> 裁定：P3-MA3-003（MA3.1 审计，第九批承接）——**保留空模块 + 文档化**，见 `ai-dev/design/nop-ai/04-rag-module-position.md`。

## 本模块的定位

- 本模块是 **IVectorStore / IEmbeddingModel 的预期实现落点**（RAG 检索增强的向量存储与 Embedding 集成）。
- **当前为空占位模块**：无 Java 源码、无依赖、无资源，仅 parent pom 引用（`nop-ai/pom.xml` modules）。
- 全仓库无任何模块依赖本模块；MA5.1/MA5.2 审计将其排除在空壳扫描外（"IVectorStore/IEmbeddingModel 预期实现模块"）。

## 为什么保留（P3-MA3-003 裁定）

1. **与 P1-MA5-003 SPI 裁定兼容**：`IVectorStore` / `IEmbeddingModel`（nop-ai-core）已裁定为 **SPI 扩展点契约**——平台无生产实现属设计意图，集成方提供实现。本模块是声明好的实现落点；删除它不会减少任何运行时依赖，但会丢失"RAG 实现放这里"的结构声明。
2. **空模块成本可忽略**：仅一个 pom.xml，无编译/测试成本；父 pom 保留该 module 的构建开销可忽略。
3. **拒绝 InMemory 最小实现**：当前零消费方；在无消费者需求时补实现 = 投机代码（空壳风险，违反 Anti-Hollow 原则）。SPI 裁定明确"无生产实现属设计意图"。
4. **拒绝从父 pom 移除**：移除 = 结构声明丢失 + 未来实现 RAG 时需重建模块 + 父 pom churn；与审计"placeholder for future work → 加 README 说明意图"的建议一致。

## 使用约束

- 本模块的代码（未来实现）**只允许实现 nop-ai-core 的 SPI 契约**（`IVectorStore` / `IEmbeddingModel`），不得引入 nop-ai 模块组之外的实现耦合。
- 集成方在消费 SPI 前应使用本模块（或自己的实现模块）提供实现；SPI 契约见 `io.nop.ai.core.api.embedding.IVectorStore` / `IEmbeddingModel` javadoc。
- 迁移触发条件：出现第一个真实 RAG 消费方（需要向量检索能力）时，在本模块落 InMemory 或存储后端实现。
