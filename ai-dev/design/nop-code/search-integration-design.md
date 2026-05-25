# nop-code 搜索集成设计

**日期**：2026-05-25
**范围**：`nop-code-service` 与 `nop-search` 的集成
**状态**：**目标架构**（当前 searchCode 为纯 DB LIKE 查询，nop-search 集成未实现）

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
输入: SearchRequest(topic="nop-code-"+indexId, query, searchType=HYBRID, limit, tags, filter)
处理: ISearchEngine.search(request)
输出: List<CodeSearchResultDTO>（从 SearchHit 转换）
```

### 降级策略

若 `ISearchEngine` bean 未注入（无 `nop-search-lucene` 依赖），fallback 到现有 DB LIKE 查询。通过 Nop IoC 的 `@Inject @Optional` 机制实现。

## 向量嵌入

`ITextEmbedding` 实现由 `nop-ai` 模块或外部 API 提供，nop-code 不关心具体实现。`autoGenerateEmbedding=true` 时，索引过程自动调用嵌入生成向量。

nop-search 还支持离线模式（无向量时仅文本搜索），无需嵌入也能工作。
