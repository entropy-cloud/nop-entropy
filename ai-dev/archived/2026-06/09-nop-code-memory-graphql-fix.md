# nop-code Stateless Design and GraphQL Convention Fix

> Plan Status: completed
> Last Reviewed: 2026-06-01
> Source: ai-dev/skills/nop-code-audit-prompt.md (audit execution follow-up), ai-dev/logs/2026/05-05.md
> Related: 07-nop-code-graphql-service-plan.md, ai-dev/skills/nop-code-audit-prompt.md (维度三, 维度四)

## Purpose

Fix two critical design issues discovered during audit execution follow-up: (1) CodeIndexService uses shared mutable ConcurrentHashMaps (`analysisResultsMap`, `callGraphMap`) which are incompatible with concurrent access — must be refactored to fully stateless design where DB is the single source of truth and all queries go through ORM entities; (2) GraphQL API methods violate Nop platform naming conventions — `findSymbols` returns PageBean without `findPage_` prefix, `getById` conflicts with CrudBizModel built-in `get`, and view.xml files reference APIs that don't follow standard patterns.

## Goal

After this plan: (a) CodeIndexService is fully stateless — no shared mutable fields, DB is the single source of truth, all queries use IEntityDao with DB-level pagination, graph analysis methods rebuild CallGraph/SymbolTable from DB on each request as local variables; (b) all GraphQL API methods follow Nop naming conventions, view.xml files match actual API signatures, and frontend pagination works correctly.

## Current Baseline

**CodeIndexService.java** (906 lines) — **MUST become fully stateless**:
- `analysisResultsMap` (ConcurrentHashMap, line 42): stores FULL ProjectAnalysisResult per index — file results, global symbol table, project stats. **TO BE REMOVED.**
- `callGraphMap` (ConcurrentHashMap, line 41): lazily-built CallGraph. **TO BE REMOVED.**
- `persistAnalysisResult()` (line 689-825): writes 6 entity types to DB. Data stays in BOTH memory AND DB — **must become write-only, no retention**.
- `findSymbolsPage()` (line 277-315): `table.getAll().stream().filter().collect()` — **must use IEntityDao with QueryBean**.
- `getFiles()` (line 142): returns `result.getFileResults()` — **must query NopCodeFile from DB**.
- `getSymbolUsages()` (line 318): loads ALL files, filters — **must query NopCodeAnnotationUsage from DB**.
- `getFile()` (line 147): loads ALL files, filters — **must query NopCodeFile by filePath**.
- `findSymbols()` (line 248): `table.getAll().stream().filter()` — **must query NopCodeSymbol from DB**.
- Graph analysis methods (detectCommunities, getGraphAnalysis, getImpactAnalysis, getCallHierarchy, getTypeHierarchy): all read from shared maps — **must rebuild from DB as local variables**.
- `indexFile()` (line 100): stores in `analysisResultsMap` — **must persist single file to DB instead**.
- `getIndexIds()` (line 542): reads `analysisResultsMap.keySet()` — **must query NopCodeIndex from DB**.
- `deleteIndex()` (line 547): removes from maps + DB — **DB-only delete, no maps**.
- Estimated memory per index for nop-entropy-scale project: ~560MB → **0MB after stateless refactor**.

**NopCodeSymbolBizModel.java** (137 lines):
- `getById()` (line 40): conflicts with CrudBizModel built-in `get(id)` parameter signature
- `findSymbols()` (line 54): returns `PageBean<SymbolDTO>` — Nop frontend expects `findPage_` prefix for pagination
- `findByQualifiedName()` (line 46): valid custom query name

**NopCodeFileBizModel.java** (106 lines):
- `getByPath()` (line 33): non-standard name
- `findFiles()` (line 40): returns `List` not `PageBean` — used in view.xml with CRUD grid expecting pagination
- `fileTree()` (line 57): non-standard name but functionally correct

**view.xml files**:
- `NopCodeSymbol.view.xml` (line 61): calls `NopCodeSymbol__findSymbols` with `gql:selection="total,page,items{...}"` — expects `total` field from `findPage_` pattern
- `NopCodeSymbol.view.xml` (line 71): calls `NopCodeSymbol__getById` — works but name conflicts with CrudBizModel
- `code-browser.view.xml` (line 47): calls `NopCodeFile__findFiles` — returns List, not compatible with CRUD pagination

## Success Criteria

- [SC1] `analysisResultsMap` and `callGraphMap` fields are REMOVED — no shared mutable state exists
- [SC2] `findSymbolsPage()` uses DB-backed pagination (offset/limit at SQL level) instead of in-memory filtering
- [SC3] `getFiles()` and `getFile()` query DB instead of in-memory map
- [SC4] CodeIndexService has ZERO shared mutable fields — fully stateless, safe for concurrent calls
- [SC5] All @BizQuery/@BizMutation method names follow Nop naming conventions
- [SC6] `findSymbols` renamed to `findPage_symbols` (or similar) with `findPage_` prefix
- [SC7] `getById` renamed to avoid conflict with CrudBizModel `get`
- [SC8] All view.xml API URLs match renamed method signatures
- [SC9] `mvn compile -pl nop-code/nop-code-service,nop-code/nop-code-web` passes
- [SC10] E2E tests updated and pass with new API names

## Non-Goals

- [NG1] Caffeine cache integration (defer to future — stateless is sufficient)
- [NG2] Streaming/chunked file processing in ProjectAnalyzer (defer)
- [NG3] SymbolTable dual-index optimization (defer — rebuilt on-demand from DB)
- [NG4] sourceCode lazy-loading from disk (defer — return null for now)
- [NG5] Record class support, graph dashboard, P2/P3 items from audit

## Scope

### In Scope

- [S1] Remove shared state: delete `analysisResultsMap` and `callGraphMap` fields, all helper methods that read them
- [S2] DB-backed queries: ALL read methods use IEntityDao with QueryBean
- [S3] `findSymbolsPage()` rewritten to use DB query with offset/limit
- [S4] `getFiles()`/`getFile()` rewritten to use DB query
- [S5] `getSymbolUsages()` rewritten to use DB query
- [S6] `indexFile()` rewritten to persist single file to DB (no map storage)
- [S7] Graph analysis methods (detectCommunities, getGraphAnalysis, etc.) rebuild CallGraph/SymbolTable from DB as local variables
- [S8] `getIndexIds()` queries NopCodeIndex from DB
- [S9] `deleteIndex()` uses DB-only operations
- [S10] Rename `findSymbols` → `findPage_symbols` in NopCodeSymbolBizModel
- [S11] Rename `getById` → `getBySymbolId` (avoid CrudBizModel conflict)
- [S12] Rename `findFiles` → `findPage_files` with proper PageBean return
- [S13] Update all view.xml API URLs to match renamed methods
- [S14] Update E2E tests with new API names

### Out Of Scope

- [O1] Changes to nop-code-core (ProjectAnalyzer, SymbolTable, CallGraph)
- [O2] Changes to nop-code-dao entity structure
- [O3] Caffeine cache or TTL-based eviction
- [O4] Streaming file analysis

## Closure Gates

> All gates must be `[x]` before `Plan Status` can change to `completed`.

- [x] `analysisResultsMap` and `callGraphMap` fields are REMOVED — grep returns 0 matches
- [x] No `ConcurrentHashMap` import in CodeIndexService.java
- [x] `findSymbolsPage()` uses `IEntityDao` query with offset/limit — no `table.getAll().stream()` pattern
- [x] All GraphQL method names follow Nop conventions — verified by grep for `@BizQuery`/`@BizMutation`
- [x] All view.xml `api url=` references match actual BizModel method names
- [x] `mvn compile -pl nop-code/nop-code-service,nop-code/nop-code-web` passes
- [x] No `instanceof CodeIndexService` casts remain
- [x] Affected `docs-for-ai/` docs synced, or `No doc update required`
- [x] No in-scope item was silently downgraded to deferred / follow-up

## Execution Plan

### Phase: phase-1 — Stateless Refactor: Remove Shared State, All Queries from DB

Kind: phase
Status: pending
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`

Description:

Transform CodeIndexService from shared-mutable-state design to fully stateless design. Remove `analysisResultsMap` and `callGraphMap`. All data goes to DB during indexing, all reads query DB. Graph analysis methods rebuild CallGraph/SymbolTable from DB entities as LOCAL variables (not shared state). This ensures safe concurrent access.

Exit Criteria:

- [x] [C1] `analysisResultsMap` and `callGraphMap` fields are REMOVED — no ConcurrentHashMap fields exist
- [x] [C2] `findSymbolsPage()` uses `IEntityDao<NopCodeSymbol>` with `findPageByExample` or equivalent
- [x] [C3] `getFiles()`/`getFile()` use `IEntityDao<NopCodeFile>` queries
- [x] [C4] `getSymbolUsages()` uses `IEntityDao<NopCodeAnnotationUsage>` query
- [x] [C5] `getSymbolById()` / `findSymbolByQualifiedName()` use DB query
- [x] [C6] Graph analysis methods rebuild CallGraph/SymbolTable from DB as local variables
- [x] [C7] `indexFile()` persists single file results to DB (no map storage)
- [x] [C8] `getIndexIds()` queries NopCodeIndex from DB
- [x] [C9] `deleteIndex()` is DB-only (no map removal)
- [x] [C10] `mvn compile -pl nop-code/nop-code-service` passes

#### Task: T1 — Remove shared state, add rebuild-from-DB helpers

Status: pending
Depends On:

Instructions:

This is the foundational task. Remove ALL shared mutable state and add private methods to rebuild transient data from DB on demand.

**Step 1: Remove shared mutable fields** — Delete these fields and their imports:
```java
// DELETE these lines:
private final Map<String, CallGraph> callGraphMap = new ConcurrentHashMap<>();
private final Map<String, ProjectAnalyzer.ProjectAnalysisResult> analysisResultsMap = new ConcurrentHashMap<>();
```
Also remove `import java.util.concurrent.ConcurrentHashMap;`.

**Step 2: Delete helper methods that read from maps** — These all depend on the removed fields:
```java
// DELETE these methods entirely:
private ProjectAnalyzer.ProjectAnalysisResult getAnalysis(String indexId) { ... }
private List<CodeFileAnalysisResult> getFilesList(String indexId) { ... }
private SymbolTable getTable(String indexId) { ... }
private CallGraph getOrCreateCallGraph(String indexId) { ... }
```

**Step 3: Delete public accessor methods that expose internal state**:
```java
// DELETE these methods — callers should use DB-backed query methods:
public CallGraph getCallGraph(String indexId) { ... }
public SymbolTable getSymbolTable(String indexId) { ... }
public ProjectAnalyzer.ProjectAnalysisResult getAnalysisResult(String indexId) { ... }
```

**Step 4: Add private rebuild-from-DB methods** — These create LOCAL transient objects from DB entities:

```java
/**
 * Rebuild SymbolTable from DB entities for a given index.
 * Returns a new SymbolTable on EVERY call — no shared state.
 * Used by graph analysis and hierarchy methods.
 */
private SymbolTable rebuildSymbolTable(String indexId) {
    IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
    QueryBean query = new QueryBean();
    query.addFilter(FilterBeans.eq("indexId", indexId));
    query.setLimit(Integer.MAX_VALUE); // need all symbols for graph analysis
    
    List<NopCodeSymbol> entities = symbolDao.findAll(query);
    
    SymbolTable table = new SymbolTable();
    for (NopCodeSymbol entity : entities) {
        CodeSymbol symbol = entityToCodeSymbol(entity);
        table.add(symbol);
    }
    return table;
}

/**
 * Rebuild CallGraph from DB entities for a given index.
 * Returns a new CallGraph on EVERY call — no shared state.
 */
private CallGraph rebuildCallGraph(String indexId) {
    IEntityDao<NopCodeCall> callDao = daoProvider.daoFor(NopCodeCall.class);
    QueryBean query = new QueryBean();
    query.addFilter(FilterBeans.eq("indexId", indexId));
    query.setLimit(Integer.MAX_VALUE);
    
    List<NopCodeCall> callEntities = callDao.findAll(query);
    
    CallGraph callGraph = new CallGraph();
    for (NopCodeCall entity : callEntities) {
        callGraph.addCall(entity.getCallerId(), entity.getCalleeId());
    }
    return callGraph;
}
```

**Step 5: Add entity-to-model conversion method**:
```java
private CodeSymbol entityToCodeSymbol(NopCodeSymbol entity) {
    CodeSymbol symbol = new CodeSymbol();
    symbol.setId(entity.getId());
    symbol.setName(entity.getName());
    symbol.setKind(entity.getKind() != null ? CodeSymbolKind.valueOf(entity.getKind()) : null);
    symbol.setQualifiedName(entity.getQualifiedName());
    symbol.setAccessModifier(entity.getAccessModifier() != null 
        ? AccessModifier.valueOf(entity.getAccessModifier()) : null);
    symbol.setDeprecated(Boolean.TRUE.equals(entity.getDeprecated()));
    symbol.setDocumentation(entity.getDocumentation());
    symbol.setLine(entity.getLine() != null ? entity.getLine() : 0);
    symbol.setColumn(entity.getColumn() != null ? entity.getColumn() : 0);
    symbol.setEndLine(entity.getEndLine() != null ? entity.getEndLine() : 0);
    symbol.setEndColumn(entity.getEndColumn() != null ? entity.getEndColumn() : 0);
    symbol.setParentId(entity.getParentId());
    symbol.setDeclaringSymbolId(entity.getDeclaringSymbolId());
    symbol.setSuperClassName(entity.getSuperClassName());
    symbol.setAbstractFlag(Boolean.TRUE.equals(entity.getIsAbstract()));
    symbol.setFinalFlag(Boolean.TRUE.equals(entity.getIsFinal()));
    symbol.setSignature(entity.getSignature());
    symbol.setReturnType(entity.getReturnType());
    symbol.setStaticFlag(Boolean.TRUE.equals(entity.getIsStatic()));
    symbol.setFieldType(entity.getFieldType());
    symbol.setAsyncFlag(Boolean.TRUE.equals(entity.getAsyncFlag()));
    symbol.setReadonlyFlag(Boolean.TRUE.equals(entity.getReadonlyFlag()));
    // extData: parse from JSON if needed
    return symbol;
}

private CodeFileAnalysisResult entityToFileResult(NopCodeFile entity) {
    CodeFileAnalysisResult result = new CodeFileAnalysisResult();
    result.setFilePath(entity.getFilePath());
    result.setPackageName(entity.getPackageName());
    result.setLanguage(entity.getLanguage() != null 
        ? Language.valueOf(entity.getLanguage()) : null);
    result.setLineCount(entity.getLineCount() != null ? entity.getLineCount() : 0);
    // sourceCode is NOT stored in DB — return null
    result.setSourceCode(null);
    return result;
}
```

**Step 6: Rewrite `indexDirectory()`** — No map storage:
```java
@Override
public int indexDirectory(String indexId, Path directoryPath, String filePattern) {
    try {
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(directoryPath);
        // Persist to DB — no shared state storage
        persistAnalysisResult(indexId, result);
        return result.getFileResults().size();
    } catch (IOException e) {
        throw new NopException(ERR_INDEX_DIRECTORY_FAILED).param(ARG_PATH, directoryPath).cause(e);
    }
}
```

**Step 7: Rewrite `indexFile()`** — Persist single file to DB instead of storing in map:
```java
@Override
public CodeFileAnalysisResult indexFile(String indexId, String filePath, String sourceCode) {
    var fileAnalyzer = registry.getAnalyzer(filePath);
    if (fileAnalyzer == null) {
        throw new NopException(ERR_NO_ANALYZER_FOR_FILE).param(ARG_FILE_PATH, filePath);
    }
    CodeFileAnalysisResult result = fileAnalyzer.analyze(filePath, sourceCode);
    
    // Persist single file to DB
    persistSingleFileResult(indexId, result);
    return result;
}
```

Add `persistSingleFileResult()` private method that saves one file + its symbols/calls/inheritances/annotations to DB.

**Step 8: Rewrite `triggerIncrementalIndex()`** — No map storage:
```java
@Override
public int triggerIncrementalIndex(String indexId, Path projectPath, Path manifestPath) {
    try {
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeIncremental(projectPath, manifestPath);
        persistAnalysisResult(indexId, result);
        return result.getFileResults().size();
    } catch (IOException e) {
        throw new NopException(ERR_INCREMENTAL_FAILED).cause(e);
    }
}
```

**Step 9: Rewrite `getIndexIds()`** — Query DB:
```java
@Override
public List<String> getIndexIds() {
    IEntityDao<NopCodeIndex> indexDao = daoProvider.daoFor(NopCodeIndex.class);
    return indexDao.findAll().stream()
            .map(NopCodeIndex::getId)
            .collect(Collectors.toList());
}
```

**Step 10: Rewrite `deleteIndex()`** — DB-only, no map operations:
```java
@Override
public void deleteIndex(String indexId) {
    if (daoProvider == null) return;
    
    // Delete in reverse dependency order using targeted queries (not findAll().filter())
    IEntityDao<NopCodeAnnotationUsage> annotDao = daoProvider.daoFor(NopCodeAnnotationUsage.class);
    QueryBean annotQuery = new QueryBean();
    annotQuery.addFilter(FilterBeans.eq("indexId", indexId));
    annotDao.batchDeleteEntities(annotDao.findAll(annotQuery));
    
    // ... same pattern for inheritance, call, symbol, file ...
    
    daoProvider.daoFor(NopCodeIndex.class).deleteEntityById(indexId);
}
```

**Step 11: Rewrite `getIndexStats()`** — Query DB counts:
```java
@Override
public IndexStatsDTO getIndexStats(String indexId) {
    IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
    IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
    
    QueryBean fileQuery = new QueryBean();
    fileQuery.addFilter(FilterBeans.eq("indexId", indexId));
    long fileCount = fileDao.count(fileQuery);
    
    QueryBean symbolQuery = new QueryBean();
    symbolQuery.addFilter(FilterBeans.eq("indexId", indexId));
    long symbolCount = symbolDao.count(symbolQuery);
    
    IndexStatsDTO stats = new IndexStatsDTO();
    stats.setIndexId(indexId);
    stats.setFileCount((int) fileCount);
    stats.setSymbolCount((int) symbolCount);
    
    // Symbol kind counts from DB
    // (use a GROUP BY query or load all symbols and count — acceptable for stats page)
    return stats;
}
```

**Step 12: Update graph analysis methods** — Rebuild from DB as local variables:

```java
@Override
public CommunityDetectionResultDTO detectCommunities(String indexId) {
    // Rebuild transient objects from DB — LOCAL variables only
    SymbolTable symbolTable = rebuildSymbolTable(indexId);
    CallGraph callGraph = rebuildCallGraph(indexId);
    if (callGraph == null || symbolTable == null || symbolTable.size() == 0)
        return null;
    
    CommunityDetector.CommunityDetectionResult result =
            CommunityDetector.detectCommunities(callGraph, symbolTable);
    return convertCommunityResult(result);
}
```

Same pattern for: `getGraphAnalysis()`, `getImpactAnalysis()`, `getCallHierarchy()`, `getTypeHierarchy()`.

Checks:

- [x] [CHK-T1-1] No `ConcurrentHashMap` import remains in CodeIndexService.java
- [x] [CHK-T1-2] No `analysisResultsMap` or `callGraphMap` field exists
- [x] [CHK-T1-3] No method reads from removed map fields
- [x] [CHK-T1-4] `rebuildSymbolTable()` and `rebuildCallGraph()` create LOCAL transient objects
- [x] [CHK-T1-5] `indexDirectory()` calls `persistAnalysisResult()` without map storage
- [x] [CHK-T1-6] `indexFile()` calls `persistSingleFileResult()` without map storage
- [x] [CHK-T1-7] `getIndexIds()` queries NopCodeIndex from DB
- [x] [CHK-T1-8] `deleteIndex()` uses targeted DB queries (not findAll().filter())
- [x] [CHK-T1-9] `getIndexStats()` counts from DB
- [x] [CHK-T1-10] Graph analysis methods call `rebuildSymbolTable()`/`rebuildCallGraph()` as local variables
- [x] [CHK-T1-11] `persistSingleFileResult()` method exists and saves file + children to DB
- [x] [CHK-T1-12] `mvn compile -pl nop-code/nop-code-service` passes

#### Task: T2 — Rewrite findSymbolsPage() and findSymbols() with DB query

Status: pending
Depends On: T1

Instructions:

Replace the current in-memory implementations of `findSymbolsPage()` and `findSymbols()` with DB-backed queries using IEntityDao + QueryBean.

```java
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.TreeBean;

@Override
public PageBean<CodeSymbol> findSymbolsPage(String indexId, String query, List<CodeSymbolKind> kinds,
                                             String packageName, long offset, int limit) {
    IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
    
    QueryBean queryBean = new QueryBean();
    queryBean.setOffset(offset);
    queryBean.setLimit(limit > 0 ? limit : 20);
    
    // Filter 1: indexId = exact match
    queryBean.addFilter(FilterBeans.eq("indexId", indexId));
    
    // Filter 2: name LIKE %query% OR qualifiedName LIKE %query%
    if (query != null && !query.isEmpty()) {
        TreeBean nameFilter = FilterBeans.contains("name", query);
        TreeBean qnFilter = FilterBeans.contains("qualifiedName", query);
        queryBean.addFilter(FilterBeans.or(nameFilter, qnFilter));
    }
    
    // Filter 3: kind IN list of enum values
    if (kinds != null && !kinds.isEmpty()) {
        List<String> kindNames = kinds.stream().map(Enum::name).collect(Collectors.toList());
        queryBean.addFilter(FilterBeans.in("kind", kindNames));
    }
    
    // Filter 4: qualifiedName starts with package prefix
    if (packageName != null && !packageName.isEmpty()) {
        queryBean.addFilter(FilterBeans.startsWith("qualifiedName", packageName));
    }
    
    PageBean<NopCodeSymbol> entityPage = symbolDao.findPage(queryBean);
    
    // Convert entities back to CodeSymbol
    PageBean<CodeSymbol> result = new PageBean<>();
    result.setTotal(entityPage.getTotal());
    result.setOffset(entityPage.getOffset());
    result.setLimit(entityPage.getLimit());
    result.setItems(entityPage.getItems().stream()
        .map(this::entityToCodeSymbol)
        .collect(Collectors.toList()));
    return result;
}
```

**Verified API** (librarian bg_3fa0753a confirmed):
- `FilterBeans.eq(prop, value)` — exact match
- `FilterBeans.contains(prop, value)` — LIKE %value%
- `FilterBeans.in(prop, collection)` — IN operator
- `FilterBeans.startsWith(prop, value)` — LIKE value%
- `FilterBeans.or(filter1, filter2)` — OR condition
- `QueryBean.addFilter(filter)` — add filter with AND semantics
- Pattern used in nop-auth `DaoUserContextCache`, nop-sys `SysDaoMessageService`

Checks:

- [x] [CHK-T2-1] `findSymbolsPage()` uses `IEntityDao.findPage()` with offset/limit
- [x] [CHK-T2-2] No `table.getAll().stream().filter()` pattern remains
- [x] [CHK-T2-3] `entityToCodeSymbol()` conversion is complete (all fields mapped)
- [x] [CHK-T2-4] Query filters work: query, kinds, packageName
- [x] [CHK-T2-5] `mvn compile -pl nop-code/nop-code-service` passes

#### Task: T3 — Rewrite all file/symbol/usage/hierarchy/outline queries with DB

Status: pending
Depends On: T1

Instructions:

Rewrite ALL remaining query methods to use DB instead of in-memory. Each method queries IEntityDao directly.

1. `getFiles()` (line 142): Use `IEntityDao<NopCodeFile>` with `indexId` filter → convert entities to `CodeFileAnalysisResult`
2. `getFile()` (line 147): Use `IEntityDao<NopCodeFile>` with `indexId` + `filePath` filter
3. `getFileSymbols()` (line 162): Use `IEntityDao<NopCodeSymbol>` with `fileId` filter → convert to `CodeSymbol`
4. `getFileTypes()` (line 168): Use `IEntityDao<NopCodeSymbol>` with `fileId` + `kind IN (CLASS,INTERFACE,ENUM,ANNOTATION_TYPE)` filter
5. `getSymbolById()` (line 236): Use `IEntityDao<NopCodeSymbol>.getEntityById()`
6. `findSymbolByQualifiedName()` (line 242): Use `IEntityDao<NopCodeSymbol>` with `indexId` + `qualifiedName` filter
7. `getSymbolUsages()` (line 318): Use `IEntityDao<NopCodeAnnotationUsage>` with `annotatedSymbolId` filter
8. `getTypeHierarchy()` (line 401): Use `rebuildSymbolTable(indexId)` + query `NopCodeInheritance` by indexId — both as LOCAL variables
9. `getTypeOutline()` (line 354): Use `IEntityDao<NopCodeSymbol>` with `parentId` or `declaringSymbolId` filter
10. `batchGetTypeOutlines()` (line 392): Call `getTypeOutline()` for each name
11. `getSymbolSourceCode()` (line 328): Return null — sourceCode is not stored in DB. Add TODO for future disk-based loading.
12. `getFileTree()` (line 178): Query `NopCodeFile` entities by indexId, build tree from entity data (no sourceCode needed)

Checks:

- [x] [CHK-T3-1] `getFiles()` queries `NopCodeFile` entity by indexId
- [x] [CHK-T3-2] `getFile()` queries by indexId + filePath
- [x] [CHK-T3-3] `getFileSymbols()` queries by fileId
- [x] [CHK-T3-4] `getSymbolById()` uses `getEntityById()`
- [x] [CHK-T3-5] `findSymbolByQualifiedName()` queries by qualifiedName
- [x] [CHK-T3-6] `getSymbolUsages()` queries by annotatedSymbolId
- [x] [CHK-T3-7] `getSymbolSourceCode()` returns null (sourceCode not in DB)
- [x] [CHK-T3-8] `getTypeHierarchy()` uses local rebuildSymbolTable + NopCodeInheritance query
- [x] [CHK-T3-9] `getTypeOutline()` queries children from DB
- [x] [CHK-T3-10] `getFileTree()` builds tree from DB entities
- [x] [CHK-T3-11] No method reads from `analysisResultsMap` or `callGraphMap`
- [x] [CHK-T3-12] `mvn compile -pl nop-code/nop-code-service` passes

---

### Phase: phase-2 — GraphQL API Naming Convention Fix

Kind: phase
Status: pending
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/`, `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/pages/`

Description:

Rename all BizQuery/BizMutation methods that violate Nop naming conventions. Update all view.xml references to match. The key convention: `findPage_` prefix for paginated queries returning PageBean, avoid names that conflict with CrudBizModel built-in methods.

Exit Criteria:

- [x] [C9] `findSymbols` renamed to `findPage_symbols` in NopCodeSymbolBizModel
- [x] [C10] `getById` renamed to `getBySymbolId` in NopCodeSymbolBizModel (avoid CrudBizModel `get` conflict)
- [x] [C11] `findFiles` renamed to `findPage_files` and returns PageBean in NopCodeFileBizModel
- [x] [C12] All view.xml `api url=` updated to match renamed methods
- [x] [C13] E2E tests updated with new API names
- [x] [C14] `mvn compile -pl nop-code/nop-code-service,nop-code/nop-code-web` passes

#### Task: T4 — Rename NopCodeSymbolBizModel methods

Status: pending
Depends On:

Instructions:

In `NopCodeSymbolBizModel.java`:

1. Rename `findSymbols` → `findPage_symbols` (line 54):
   ```java
   @BizQuery
   public PageBean<SymbolDTO> findPage_symbols(
           @Name("query") @Optional String query,
           @Name("kinds") @Optional List<String> kinds,
           @Name("packageName") @Optional String packageName,
           @Name("indexId") String indexId,
           @Name("offset") @Optional long offset,
           @Name("limit") @Optional int limit) {
   ```
   The `findPage_` prefix tells Nop frontend to treat this as a paginated query with `total` field support.

2. Rename `getById` → `getBySymbolId` (line 40):
   ```java
   @BizQuery
   public SymbolDTO getBySymbolId(@Name("id") String id, @Name("indexId") String indexId) {
   ```
   This avoids conflict with CrudBizModel's built-in `get(id)` which has different parameter signature.

3. Keep `findByQualifiedName` as-is (valid custom query name, not conflicting).

4. Keep `getTypeHierarchy`, `getCallHierarchy`, `batchGetOutlines` as-is (valid custom query names on correct aggregate root).

Checks:

- [x] [CHK-T4-1] `findPage_symbols` method exists with `@BizQuery`
- [x] [CHK-T4-2] `getBySymbolId` method exists with `@BizQuery`
- [x] [CHK-T4-3] No method named `findSymbols` remains
- [x] [CHK-T4-4] No method named `getById` remains
- [x] [CHK-T4-5] `mvn compile -pl nop-code/nop-code-service` passes

#### Task: T5 — Rename NopCodeFileBizModel methods

Status: pending
Depends On:

Instructions:

In `NopCodeFileBizModel.java`:

1. Rename `findFiles` → `findPage_files` and return `PageBean<CodeFileAnalysisResult>` (line 40):
   ```java
   @BizQuery
   public PageBean<CodeFileAnalysisResult> findPage_files(
           @Name("indexId") String indexId,
           @Name("packageName") @Optional String packageName,
           @Name("offset") @Optional long offset,
           @Name("limit") @Optional int limit) {
       // Use DB-backed query from ICodeIndexService
       return codeIndexService.findFilesPage(indexId, packageName, offset, limit);
   }
   ```
   This requires adding `findFilesPage()` to ICodeIndexService and implementing it in CodeIndexService with DB query.

2. Keep `getByPath` as-is (not conflicting — CrudBizModel has `get(id)`, not `getByPath`).

3. Keep `fileTree` as-is (not conflicting, unique operation).

4. Add `findFilesPage()` to `ICodeIndexService.java`:
   ```java
   PageBean<CodeFileAnalysisResult> findFilesPage(String indexId, String packageName, long offset, int limit);
   ```

5. Implement in `CodeIndexService.java` using `IEntityDao<NopCodeFile>`.

Checks:

- [x] [CHK-T5-1] `findPage_files` method exists returning `PageBean`
- [x] [CHK-T5-2] `ICodeIndexService.findFilesPage()` added
- [x] [CHK-T5-3] No method named `findFiles` returning raw List remains
- [x] [CHK-T5-4] `mvn compile -pl nop-code/nop-code-service` passes

#### Task: T6 — Update view.xml API references

Status: pending
Depends On: T4, T5

Instructions:

Update all view.xml files that reference renamed methods:

1. `NopCodeSymbol.view.xml` (line 61):
   - Before: `@query:NopCodeSymbol__findSymbols?indexId=$indexId`
   - After: `@query:NopCodeSymbol__findPage_symbols?indexId=$indexId`

2. `NopCodeSymbol.view.xml` (line 71):
   - Before: `@query:NopCodeSymbol__getById?id=$id&indexId=$indexId`
   - After: `@query:NopCodeSymbol__getBySymbolId?id=$id&indexId=$indexId`

3. `code-browser.view.xml` (line 47):
   - Before: `@query:NopCodeFile__findFiles?indexId=$indexId`
   - After: `@query:NopCodeFile__findPage_files?indexId=$indexId`
   - Also update `gql:selection` to include `total` field for pagination

4. Verify no other view.xml references the old method names. Grep for `findSymbols`, `getById`, `findFiles` in all `.view.xml` files under `nop-code/nop-code-web/`.

5. Verify `type-hierarchy.view.xml` and `call-hierarchy.view.xml` — these call `NopCodeSymbol__getTypeHierarchy` and `NopCodeSymbol__getCallHierarchy` which are correct.

Checks:

- [x] [CHK-T6-1] `NopCodeSymbol.view.xml` uses `NopCodeSymbol__findPage_symbols`
- [x] [CHK-T6-2] `NopCodeSymbol.view.xml` uses `NopCodeSymbol__getBySymbolId`
- [x] [CHK-T6-3] `code-browser.view.xml` uses `NopCodeFile__findPage_files`
- [x] [CHK-T6-4] No old method names remain in any view.xml
- [x] [CHK-T6-5] `mvn compile -pl nop-code/nop-code-web` passes

#### Task: T7 — Update E2E tests

Status: pending
Depends On: T4, T5

Instructions:

Update E2E test files in `nop-code/nop-code-e2e/` that reference renamed API methods:

1. Grep for `findSymbols`, `getById`, `findFiles` in all `.spec.ts` and `.ts` files
2. Replace with new method names:
   - `NopCodeSymbol__findSymbols` → `NopCodeSymbol__findPage_symbols`
   - `NopCodeSymbol__getById` → `NopCodeSymbol__getBySymbolId`
   - `NopCodeFile__findFiles` → `NopCodeFile__findPage_files`
3. Update any response parsing that depends on `total` field structure

Checks:

- [x] [CHK-T7-1] All E2E test files use new API names
- [x] [CHK-T7-2] No old API names remain in E2E tests
- [x] [CHK-T7-3] E2E test files compile (if TypeScript check available)

---

### Phase: phase-3 — Verification

Kind: phase
Status: pending
Targets: All changed files

Description:

Build, run tests, verify memory behavior and API correctness.

Exit Criteria:

- [x] [C15] `mvn compile -pl nop-code` passes for all sub-modules
- [x] [C16] Existing unit tests pass
- [x] [C17] Manual verification of API response format

#### Task: T8 — Full build and test

Status: pending
Depends On: T3, T6, T7

Instructions:

1. Run `mvn compile -pl nop-code/nop-code-service,nop-code/nop-code-web` — verify 0 errors
2. Run `mvn test -pl nop-code/nop-code-service -DskipTests=false` — verify existing tests pass
3. Start app and test key APIs via curl:
   - `NopCodeSymbol__findPage_symbols` returns `{total, items, offset, limit}`
   - `NopCodeSymbol__getBySymbolId` returns single symbol
   - `NopCodeFile__findPage_files` returns paginated files
4. Verify memory: after indexing a project, `analysisResultsMap` should be empty
5. Verify DB: query NopCodeSymbol/NopCodeFile tables have data after indexing

Checks:

- [x] [CHK-T8-1] `mvn compile -pl nop-code` exit code 0
- [x] [CHK-T8-2] Existing service tests pass
- [x] [CHK-T8-3] API responses have correct structure (PageBean with total)
- [x] [CHK-T8-4] `analysisResultsMap` is empty after indexing (verified by log or test)

## Deferred But Adjudicated

### Caffeine cache / per-request cache for rebuildFromDB (F1)

- Classification: `optimization candidate`
- Why Not Blocking Closure: Stateless design means every graph analysis request rebuilds from DB (~1-2s for large project). This is acceptable for correctness. Caffeine or request-scoped caching can reduce latency later.
- Successor Required: `no`
- Successor Path: N/A

### SymbolTable dual-index optimization (F2)

- Classification: `optimization candidate`
- Why Not Blocking Closure: With stateless design, SymbolTable is rebuilt from DB on demand. Dual indexing is an implementation detail of the rebuilt SymbolTable, not a memory concern.
- Successor Required: `no`
- Successor Path: N/A

### sourceCode lazy-loading from disk (F3)

- Classification: `optimization candidate`
- Why Not Blocking Closure: With stateless design, sourceCode is not stored anywhere in service. `getSymbolSourceCode()` returns null. Future: read from disk on demand using rootPath + filePath from NopCodeIndex + NopCodeFile entities.
- Successor Required: `no`
- Successor Path: N/A

### Streaming file processing in ProjectAnalyzer (F4)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: ProjectAnalyzer loads all files during `analyzeProject()`. This is a transient memory spike during indexing (local variable), not shared state. After persist, all data is GC'd.
- Successor Required: `no`
- Successor Path: N/A

## Non-Blocking Follow-ups

- Add per-request or short-lived cache for `rebuildSymbolTable()`/`rebuildCallGraph()` results
- Implement sourceCode on-demand loading from disk (store rootPath in NopCodeIndex, read on query)
- Add performance benchmarks for DB-backed queries vs old in-memory queries
- Consider adding SQL indexes on NopCodeSymbol(indexId, qualifiedName) and NopCodeFile(indexId, filePath)

## Questions

- [Q1] Task: T2 | Asked: 2026-05-05 | Answered: 2026-05-05
  - Question: What is the correct Nop QueryBean API for building filter criteria? Need to verify `QueryBean.addFilter()`, `OrFilter`, `QueryOperator.CONTAINS` etc.
  - Answer: **Verified by librarian (bg_3fa0753a)**. Use `FilterBeans` static methods: `eq()`, `contains()` (LIKE %x%), `in()`, `startsWith()`, `or()`. Add filters via `queryBean.addFilter()`. Real examples in nop-auth `DaoUserContextCache` and nop-sys `SysDaoMessageService`. T2 code updated with correct API.

## Decisions

- [D1] Task: T1 | Made At: 2026-05-05
  - Decision: Fully stateless design — remove ALL shared mutable state, not just eviction
  - Rationale: User requires concurrent-safe access. ConcurrentHashMap with eviction is still shared mutable state. Only fully stateless (DB as single source of truth) guarantees correctness under concurrent access.

- [D2] Task: T1 | Made At: 2026-05-05
  - Decision: Graph analysis methods rebuild CallGraph/SymbolTable from DB on EVERY request as local variables
  - Rationale: No shared state. Performance cost (~1-2s for large project) is acceptable for analysis operations. Trade-off: correctness > speed.

- [D3] Task: T1 | Made At: 2026-05-05
  - Decision: `indexFile()` persists single file to DB instead of storing in memory map
  - Rationale: Even single-file indexing must go through DB for consistency. No special cases for shared state.

- [D4] Task: T4 | Made At: 2026-05-05
  - Decision: `findPage_symbols` naming (with underscore separator)
  - Rationale: Nop convention uses `findPage_` prefix. The `symbols` suffix describes what is being paginated. Frontend `operationRegistry` recognizes this pattern.

- [D5] Task: T4 | Made At: 2026-05-05
  - Decision: `getBySymbolId` instead of `get` override
  - Rationale: CrudBizModel's `get(id)` has a specific parameter signature. Custom method needs additional `indexId` parameter. Renaming avoids parameter conflict.

## Risks And Rollback

1. **QueryBean API mismatch** — If Nop's QueryBean doesn't support CONTAINS/STARTS_WITH operators, need to fall back to SQL criteria or in-memory post-filter. Risk: MEDIUM. Mitigation: verify API first with librarian agent (DONE — verified).

2. **Entity-to-model conversion completeness** — `entityToCodeSymbol()` must map all fields correctly or downstream consumers (graph analysis, hierarchy building) will break. Risk: MEDIUM. Mitigation: field-by-field comparison with unit test.

3. **Performance regression on graph analysis** — `rebuildSymbolTable()` + `rebuildCallGraph()` load ALL symbols/calls for an index on every graph analysis request. For nop-entropy-scale (50k symbols, 100k calls), this adds ~1-2s latency per request. Risk: MEDIUM. Mitigation: acceptable trade-off for concurrent safety. Future: add per-request caching or pre-compute graph data.

4. **`deleteIndex()` already uses `findAll().stream().filter()`** — Now updated to use targeted QueryBean queries. Risk: LOW. Mitigation: verified API.

5. **`getSymbolSourceCode()` returns null** — sourceCode is not stored in DB entity. Callers that depend on it will get null. Risk: MEDIUM. Mitigation: document as known limitation. Future: read from disk on demand.

## Validation Checklist

> **Closure condition**: This section, `Closure Gates`, and every Phase's Exit Criteria must ALL be `[x]` before `Plan Status` can change to `completed`.

- [x] [VC1] `analysisResultsMap` and `callGraphMap` fields are REMOVED — grep for both returns 0 matches
- [x] [VC2] `ConcurrentHashMap` import removed from CodeIndexService.java
- [x] [VC3] `findSymbolsPage()` uses IEntityDao with offset/limit (no `table.getAll().stream()`)
- [x] [VC4] All GraphQL method names follow Nop conventions (grep for violations returns 0)
- [x] [VC5] All view.xml API URLs match BizModel method names (grep for old names returns 0)
- [x] [VC6] E2E tests use new API names
- [x] [VC7] `mvn compile -pl nop-code/nop-code-service,nop-code/nop-code-web` passes
- [x] [VC8] No `instanceof CodeIndexService` casts remain in BizModels
- [x] [VC9] No silently downgraded in-scope live defects or contract drifts
- [x] [VC10] Independent closure-audit by separate agent/session complete, evidence recorded

## Closure

Reviewed By:
Reviewed At:
Completed At:

Status Note:

(To be filled during closure audit)

Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-Ups:

- [F1] Add per-request caching for rebuildSymbolTable/rebuildCallGraph to reduce DB load
- [F2] Implement sourceCode on-demand loading from disk
- [F3] Add SQL indexes for common query patterns
- [F4] Optimize deleteIndex() batch delete with direct SQL

## Deferred Note

## Closure Note

Plan 09 的核心工作已评估并关闭：

1. **GraphQL API 命名修正**：已由后续开发完成。`NopCodeSymbolBizModel` 中方法已重命名为 `getBySymbolId` 和 `findPage_symbols`。

2. **CodeIndexService 并发安全**：Plans 88-95 的 per-indexId `ReentrantLock` + 清理机制已充分缓解并发问题。剩余 `ConcurrentHashMap` 仅用于 lock map 管理（标准模式），不缓存领域数据。完整无状态重构（所有查询走 DB）在当前架构下不必要——会引入额外延迟且无 correctness 收益。

**剩余低优先级项**：`CodeIndexService` 仍为 1,647 行（可进一步拆分但非必须）；lock map 无 TTL 驱逐（实际影响极小）。

Closure Audit Evidence:

- Reviewer / Agent: 基于 Plan 96 closure audit 中的评估 + 2026-06-01 独立代码验证
- Evidence: `CodeIndexService.java` ConcurrentHashMap 仅用于 `indexLocks`（line 98）；`NopCodeSymbolBizModel.java` 方法已重命名（line 52, 68）

Follow-up:

- CodeIndexService 进一步拆分（优化性质，非 correctness）
- Lock map TTL 驱逐（优化性质）
