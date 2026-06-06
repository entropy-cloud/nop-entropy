# nop-code — Code Indexing & Analysis Module

## 子模块职责

| Submodule | Responsibility |
|-----------|---------------|
| `nop-code-core` | Core models: `CodeSymbol`, `CodeFileAnalysisResult`, `SymbolTable`, `CallGraph`, `EdgeProvenance`. Interfaces: `ICodeFileAnalyzer`, `ILanguageAdapter`, `ISemanticEdgeExtractor`. |
| `nop-code-lang-java` | Java source analyzer using JavaParser + Symbol Solver. Route extraction for Spring annotations. |
| `nop-code-lang-python` | Python source analyzer. Import resolution via `PythonImportResolver`. |
| `nop-code-lang-typescript` | TypeScript/JavaScript source analyzer. Import resolution via `TypeScriptImportResolver`. |
| `nop-code-graph` | Graph analysis: community detection (Louvain), critical-node scoring, impact analysis, graph diff, dependency cycles. |
| `nop-code-flow` | Flow detection: execution flows, dead-code detection, change analysis. Interfaces: `IFlowDetector`, `IChangeAnalyzer`, `IDeadCodeDetector`. |
| `nop-code-dao` | Generated ORM entities and DAO layer (from `nop-code.orm.xml`). |
| `nop-code-service` | Business logic. `CodeIndexService` (implements `ICodeIndexService`): full/incremental indexing, persistence, flow/graph orchestration. |
| `nop-code-web` | Web/API layer. |
| `nop-code-api` | API DTOs: `IndexStatsDTO`, `CodeSearchResultDTO`, `DepGraphDTO`, etc. |
| `nop-code-meta` | Metadata: xmeta definitions, ORM model (`nop-code.orm.xml`), i18n resources, dict files. |

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
- **NopCodeUsage** — unified reference tracking (CALL, ANNOTATES, EXTENDS, TESTED_BY, etc.)
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

All dicts defined in `nop-code/model/nop-code.orm.xml` and materialized in `nop-code-meta/_vfs/dict/code/`:

- `code/symbol_kind` — CLASS, INTERFACE, ENUM, ANNOTATION_TYPE, METHOD, FUNCTION, FIELD, etc.
- `code/access_modifier` — PUBLIC, PROTECTED, PRIVATE, PACKAGE_PRIVATE, INTERNAL
- `code/reference_kind` — READ, WRITE, CALL, TYPE_REFERENCE, EXTENDS, IMPLEMENTS, ANNOTATES, IMPORTS, OVERRIDES
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
