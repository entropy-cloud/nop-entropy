# nop-code 搜索集成设计

**日期**：2026-05-25（更新于 2026-09-23）
**范围**：`nop-code-service` 与 `nop-search` 的集成
**状态**：**双路径已实现且生产默认装配已落地**（2026-09-28，N4.1）：`nop-code-app` 依赖 `nop-search-lucene`，autoconfig 注册 `nopSearchEngine`，`@Inject` 按类型注入 `CodeIndexService.setSearchEngine`——注入时走 `SearchType.TEXT`，未部署引擎时降级 DB LIKE（双路径均由测试钉住：`TestCodeSearchEngineAssembly` 引擎 e2e、`TestCodeSearchFallbackLike` 降级三分支）。执行 N4.1 时发现并修复 `LuceneSearchEngine` topic 守卫缺陷（连字符 topic 被拒导致引擎同步静默全灭，见 plan `ai-dev/plans/nop-code/14-n4-1-search-engine-default-assembly.md`）。向量嵌入生产实现已落地（N4.2，2026-09-28，见「向量嵌入」节）；混合搜索查询面已贯通（N4.3，2026-09-28：`CodeSearchService.mapSearchType` 将 searchType=TEXT/VECTOR/HYBRID 映射到引擎 SearchType，legacy 值引擎路径归一 TEXT）

## 决策

集成 `nop-search` 模块，不自建搜索引擎。nop-search 已提供 Lucene BM25 全文搜索 + KNN 向量搜索 + RRF 混合搜索 + FilterBean 高级过滤 + 高亮，完全覆盖需求。

**拒绝了什么**：
- 自建 Lucene 集成 → nop-search 已封装
- Elasticsearch → 嵌入式 Lucene 对单机代码索引足够
- SQLite FTS5 → 与 Nop 平台无关

## 灵感来源

code-review-graph v2.3.3 的搜索设计：FTS5 BM25 + 4 种向量嵌入 Provider + Reciprocal Rank Fusion 混合搜索 + 查询感知 kind boosting。

## nop-search 能力确认

nop-search（`nop-search-api` + `nop-search-lucene`）已覆盖：

| 能力 | nop-search | CRG 等价物 |
|------|-----------|------------|
| 全文搜索 | Lucene BM25 + StandardQueryParser + 代码友好分词器 | FTS5 BM25 |
| 向量搜索 | Lucene KnnFloatVectorQuery + COSINE | KNN 向量搜索 |
| 混合搜索 | RRF（k=60）融合文本+向量 | RRF 混合搜索 |
| 高亮 | Lucene Highlighter（title/content/summary） | 无 |
| 标签过滤 | matchAllTags AND/OR + FilterBean 高级过滤 | kind boosting |
| 向量自动生成 | `autoGenerateEmbedding` + `ITextEmbedding` 接口 | 4 种 Provider |
| GraphQL 暴露 | `SearchEngineBizModel` 自动注册 | MCP tools |

## 集成契约

### 依赖

`nop-code-service` 添加 `nop-search-api` 依赖（仅接口层，无实现耦合）。搜索引擎实现由部署时注入。

### 索引同步

`CodeIndexService.saveFileResultInSession()` 中同步调用 `ISearchEngine.addDoc()`：

```
topic = "nop-code-" + indexId           // 按 indexId 隔离
SearchableDoc.id = symbolId
SearchableDoc.title = qualifiedName
SearchableDoc.content = documentation + " " + signature
SearchableDoc.tagSet = {kind.name(), language}
SearchableDoc.autoGenerateEmbedding = true   // 依赖 ITextEmbedding 实现
```

### 查询改造

`searchCode` 方法改为调用 `ISearchEngine.search()`：

```
输入: SearchRequest(topic="nop-code-"+indexId, query, searchType=SearchType.TEXT, limit, tags, filter)
处理: ISearchEngine.search(request)
输出: List<CodeSearchResultDTO>（从 SearchHit 转换）
```

> 当前实现用 `SearchType.TEXT`（全文）；切换为 `HYBRID`（文本 + 向量 RRF）需先注入嵌入实现。

### 降级策略

若 `ISearchEngine` bean 未注入（无 `nop-search-lucene` 依赖），fallback 到现有 DB LIKE 查询。通过 Nop IoC 的 `@Inject @Optional` 机制实现。

## 向量嵌入

`ITextEmbedding` 实现由 `nop-ai` 模块或外部 API 提供，nop-code 不关心具体实现。`autoGenerateEmbedding=true` 时，索引过程自动调用嵌入生成向量。

**生产实现已落地（N4.2，2026-09-28）**：nop-ai-core 的 `AiModelTextEmbedding`（bean `nopAiTextEmbedding`）桥接 K1 的 `IEmbeddingModel`（`EmbeddingServiceImpl`）。`LuceneSearchEngine.setTextEmbedding` 已加 `@Inject`+`@Nullable`——classpath 含 nop-ai-core 时 by-type 自动注入（索引侧 `autoGenerateEmbedding` 与查询侧 `parseQueryVector` 即用真实嵌入）；无 nop-ai-core 的部署注入 null，引擎回退 hash 模拟（仅测试语义）。失败语义 = 异常上抛（fail-loud），不静默降级。nop-code 侧消费（`CodeSearchService` 暴露 VECTOR/HYBRID 查询面）归 N4.3。

nop-search 还支持离线模式（无向量时仅文本搜索），无需嵌入也能工作。
