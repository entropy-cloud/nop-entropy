# nop-code — Code Indexing & Analysis Module

## 子模块职责

| Submodule | Responsibility |
|-----------|---------------|
| `nop-code-core` | Core models: `CodeSymbol`, `CodeFileAnalysisResult`, `SymbolTable`, `CallGraph`, `EdgeProvenance`. Interfaces: `ICodeFileAnalyzer`, `ILanguageAdapter`, `ISemanticEdgeExtractor`. |
| `nop-code-lang-java` | Java source analyzer using JavaParser + Symbol Solver. Contains hardcoded Spring route extraction (`[legacy]` — to be migrated out of core; the target is pluggable SPI-loaded framework patterns). |
| `nop-code-lang-python` | Python source analyzer. Import resolution via `PythonImportResolver`. |
| `nop-code-lang-typescript` | TypeScript/JavaScript source analyzer. Import resolution via `TypeScriptImportResolver`. |
| `nop-code-flow` | Flow detection: execution flows, dead-code detection, change analysis. Interfaces: `IFlowDetector`, `IChangeAnalyzer`, `IDeadCodeDetector`. Contains hardcoded Spring entry-point patterns (`[legacy]` — to be migrated out of core). |
| `nop-code-codegen` | 代码生成层。 |
| `nop-code-dao` | Generated ORM entities and DAO layer (from `nop-code.orm.xml`). |
| `nop-code-service` | Business logic. `CodeIndexService` (implements `ICodeIndexService`): full/incremental indexing, persistence, flow/graph orchestration. |
| `nop-code-web` | Web/API layer. |
| `nop-code-app` | 应用打包层。 |
| `nop-code-api` | API DTOs: `IndexStatsDTO`, `CodeSearchResultDTO`, `DepGraphDTO`, etc. |
| `nop-code-meta` | Metadata: xmeta definitions, ORM model (`nop-code.orm.xml`), i18n resources, dict files. |

> 图分析能力（community detection/Leiden、critical-node scoring、impact analysis、graph diff、dependency cycles）不在本模块，而在**独立的顶层模块 `nop-graph/`**（`nop-graph-api` + `nop-graph-core`），见 `../01-repo-map/module-groups.md`。`nop-code` 的子模块清单中**没有** `nop-code-graph`。`nop-code` 通过 `io.nop.graph.api.IGraph` 抽象消费该库（`CodeCallGraph` 是适配器）。

## 核心 API 清单

### ICodeIndexService (main service interface)

Located in `nop-code-service`. Key methods:

| Method | Type | Description |
|--------|------|-------------|
| `indexDirectory` | Mutation | Full index of a VFS directory |
| `indexFile` | Mutation | Index a single file |
| `triggerIncrementalIndex` | Mutation | Detect changes and re-index modified files |
| `deleteIndex` | Mutation | Delete an index and all associated entities |
| `getFiles`, `getFile`, `getFileSourceCode` | Query | File-level queries |
| `getFileSymbols`, `getFileTypes`, `getFileOutline` | Query | Symbol-level queries |
| `findSymbols`, `findSymbolsPage`, `getSymbolById` | Query | Symbol search |
| `findReferencedBy`, `getSymbolSourceCode` | Query | Reference & source queries |
| `getTypeHierarchy`, `getCallHierarchy` | Query | Hierarchy traversal |
| `detectCommunities`, `getCriticalNodes` | Query | Graph analysis |
| `getImpactAnalysis`, `exportGraph`, `diffGraph` | Query | Impact & diff |
| `getDeps`, `getReverseDeps`, `findCycles` | Query | Dependency analysis |
| `detectFlows`, `listFlows`, `getFlow` | Query/Mutation | Execution flow |
| `analyzeChanges`, `detectDeadCode` | Query | Change & dead code |
| `searchCode` | Query | Full-text search (via `ISearchEngine`) |

### Key BizModels

- **NopCodeIndexBizModel** (`@BizModel("NopCodeIndex")`) — exposes `ICodeIndexService` as GraphQL/REST via `@BizQuery`/`@BizMutation`. Admin-only mutations, `code-query` permission for reads.
- **NopCodeSymbolBizModel** — symbol-specific CRUD and queries.

## API 暴露策略

**nop-code 通过 GraphQL（BizModel）统一暴露 API，不引入 MCP 协议。**

原因：
1. Nop 平台的 BizModel + `IGraphQLEngine` 已是通用 API 暴露层，天然支持所有 AI 客户端（Claude Code、Cursor、Codex、OpenCode 等）通过 GraphQL 查询消费数据。
2. GraphQL 支持嵌套查询（`@BizLoader`），单次请求可获取符号+源码+引用+层级的完整上下文，对 AI 代理的 token 效率优于 MCP 的扁平工具调用。
3. GraphQL 内置权限控制（`@Auth` 注解），MCP 无认证机制。
4. 新增 MCP Server 会引入不必要的协议适配层和运维复杂度。

AI 客户端通过标准 GraphQL 客户端库或 HTTP POST 直接调用 nop-code 的 GraphQL endpoint 即可。所有 38+ BizModel 方法均可通过 GraphQL 查询访问。参见 `02-core-guides/api-and-graphql.md`。

## 架构原则

nop-code 的目标定位是**集群式代码索引服务**（非单机本地工具）。下表中"现状"列说明哪些已落地、哪些是目标：

| 原则 | 含义 | 现状 |
|------|------|------|
| **存储形式由接口屏蔽** | `io.nop.graph.api.IGraph` 是存储抽象边界；`CallGraph`/`SymbolTable` 是内存结构，`CodeCallGraph` 是适配器。切换后端只需换 `IGraph` 实现 | ✅ 已落地（`IGraph` 在 `nop-graph-api`） |
| **通用图算法下沉** | 社区检测、中心性、BFS、导出等通用算法在顶层 `nop-graph/`；nop-code 通过 `IGraph` 消费 | ✅ 已落地 |
| **查询路径无状态** | 目标：查询不在 JVM 内存中全量重建图；全局算法结果索引期物化、查询期只读 | ⏳ 目标（当前用 `CodeCacheManager` 堆内缓存 + 全量 rebuild） |
| **利用数据库图能力** | 目标：评估 PostgreSQL 图索引（`ltree`/递归 CTE）或 Apache AGE；需先定生产 DB 与可移植性（参考应用当前 MySQL/H2）。全局算法无法由递归 CTE 求得 | ⏳ 待决策 |
| **框架适配不入核心** | 目标：框架模式经 SPI（`IEntryPointPatternProvider`）可插拔加载，DSL 为远期选项 | ⏳ 目标（当前 `JavaFileAnalyzer`/`FlowDetector` 含硬编码 Spring） |
| **索引构建可分布式** | 目标：索引构建作为批处理可并行/分片；每个 indexId 是独立隔离单元 | ⏳ 目标（当前单节点） |

## 实体关系

```
NopCodeIndex
 ├──< NopCodeFile (indexId)          [cascade delete]
 │     ├──< NopCodeSymbol (fileId)
 │     ├──< NopCodeUsage (fileId)
 │     └──< NopCodeCall (fileId)
 ├──< NopCodeSymbol (indexId)        [cascade delete]
 │     ├──< NopCodeUsage (symbolId)
 │     ├──< NopCodeAnnotationUsage (annotatedSymbolId)
 │     ├──< NopCodeFlow (entryPointId)
 │     ├──< NopCodeFlowMembership (symbolId)
 │     ├──< NopCodeCall (callerId / calleeId)
 │     └──< NopCodeInheritance (subTypeId / superTypeId)
 ├──< NopCodeDependency (indexId)    [cascade delete]
 ├──< NopCodeFlow (indexId)          [cascade delete]
 │     └──< NopCodeFlowMembership (flowId)
 ├──< NopCodeUsage (indexId)         [cascade delete]
 ├──< NopCodeCall (indexId)          [cascade delete]
 ├──< NopCodeInheritance (indexId)   [cascade delete]
 ├──< NopCodeAnnotationUsage (indexId) [cascade delete]
 └──< NopCodeSemanticEdge (indexId)  [cascade delete, logical delete]
```

Key relationship entities:
- **NopCodeCall** — method call edges (caller → callee)
- **NopCodeDependency** — file-level import dependencies (source → target file path)
- **NopCodeInheritance** — type hierarchy (extends/implements)
- **NopCodeSemanticEdge** — semantic relationships (similar, related, pattern-based); supports logical delete
- **NopCodeFlow / NopCodeFlowMembership** — execution flow tracking with criticality scores
- **NopCodeUsage** — unified reference tracking (CALL, ANNOTATES, EXTENDS, IMPLEMENTS, IMPORTS, READ, WRITE, TYPE_REFERENCE, TYPE_OF, INSTANTIATES, OVERRIDES). Note: `TESTED_BY`/`REFERENCES` are planned extensions, not yet in `CodeUsageKind`.
- **NopCodeAnnotationUsage** — annotation usage on symbols

## 配置项说明

| Config | Description |
|--------|-------------|
| `allowedLocalRoot` | Restricts local filesystem paths that can be indexed. Set via `setAllowedLocalRoot()`. Must be configured to prevent path traversal. |
| `ISearchEngine` | Injectable search backend. When present, symbols are synced for full-text search. Optional. |
| `IFlowDetector` | Injectable execution flow detection strategy. Required for `detectFlows()`. |
| `IChangeAnalyzer` | Injectable change analysis strategy. Requires `IFlowDetector`. Used for git-based change impact. |
| `IDeadCodeDetector` | Injectable dead code detection strategy. Required for `detectDeadCode()`. |
| `IFingerprintStore` | Pluggable fingerprint storage for incremental indexing. Defaults to `OrmFingerprintStore`. |

## Dict Definitions

All dicts defined in `nop-code/model/nop-code.orm.xml` and materialized in `nop-code-meta/src/main/resources/_vfs/dict/code/`:

- `code/symbol_kind` — CLASS, INTERFACE, ENUM, ANNOTATION_TYPE, METHOD, FUNCTION, FIELD, etc.
- `code/access_modifier` — PUBLIC, PROTECTED, PRIVATE, PACKAGE_PRIVATE, INTERNAL
- `code/reference_kind` — READ, WRITE, CALL, TYPE_REFERENCE, EXTENDS, IMPLEMENTS, ANNOTATES, IMPORTS, OVERRIDES, TYPE_OF, INSTANTIATES
- `code/index_status` — CREATED, INDEXING, READY, ERROR, COMPLETED, DETECTED
- `code/language` — JAVA, PYTHON, TYPESCRIPT, JAVASCRIPT
- `code/call_type` — CONSTRUCTOR (+ free-text return types)
- `code/relation_type` — EXTENDS, IMPLEMENTS
- `code/semantic_relation_type` — SEMANTICALLY_SIMILAR_TO, CONCEPTUALLY_RELATED_TO, SOLVES_SAME_PROBLEM, etc.
- `code/provenance` — AST_EXTRACTION, SYMBOL_SOLVER, HEURISTIC, FRAMEWORK_INFERENCE, MANUAL

## Field Name Mapping (CodeIndexService)

The `ExecutionFlow` domain model and `NopCodeFlow` ORM entity use different field names:

| ExecutionFlow (domain) | NopCodeFlow (entity) | Notes |
|------------------------|---------------------|-------|
| `criticality` | `overallScore` | Criticality score stored as overall score |
| `entryPointSymbolId` | `entryPointId` | Entry point FK uses shorter name |

See `entityToExecutionFlow()` and `persistFlows()` in `CodeIndexService` for the bidirectional mapping.

## NopCodeUsage Unique Key Evaluation

Unique key: `uk_usage_unique(indexId, symbolId, fileId, kind, line, column)`.

The `column` column is nullable (`mandatory` not set, defaults to nullable). In standard SQL, NULL ≠ NULL, so two rows with identical values but both having `column = NULL` would **not** violate the unique constraint. In practice, the Java analyzer always sets `column` from `expr.getRange()`, which may be absent for some AST nodes. To guarantee deduplication, application-level dedup is applied via deterministic ID generation (SHA-256 hash of indexId + kind + callerId + fileId + line).
