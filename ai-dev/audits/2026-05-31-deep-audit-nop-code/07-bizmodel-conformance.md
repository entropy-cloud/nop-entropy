# Audit Dimension 07: BizModel Conformance — nop-code

**BizModel classes audited**: 11 (all extend CrudBizModel<T>, all have setEntityName() in constructor)
**Non-BizModel service**: CodeIndexService.java (3033 lines) — plain @Inject-managed service

---

### [维度07-01] @BizLoader Missing `forType` on DTO-Based ContextSource (NopCodeSymbolBizModel)

- **File**: `nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:96-120`
- **Evidence Snippet**:
```java
@BizLoader
public List<AnnotationUsageDTO> usages(
        @ContextSource SymbolDTO symbol,
        @Name("indexId") String indexId,
        @Name("limit") int limit) {
```
- **Severity**: P1
- **Current State**: Two `@BizLoader` methods use `@ContextSource SymbolDTO` but do not declare `forType = SymbolDTO.class`. Without `forType`, the platform defaults to the BizModel's entity type (NopCodeSymbol).
- **Risk**: ClassCastException or silently non-firing loaders. Sister class NopCodeFileBizModel correctly uses `forType`.
- **Recommendation**: Add `forType = SymbolDTO.class` to both @BizLoader annotations.
- **Confidence**: Likely
- **False Positive Exclusion**: The issue is specifically the missing `forType` when @ContextSource type differs from entity type. NopCodeFileBizModel correctly uses `forType` for the same pattern.
- **Review Status**: Not reviewed

---

### [维度07-02] `IncrementalStatus` Inner Class Missing `@DataBean` Annotation

- **File**: `nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:247-302`
- **Evidence Snippet**:
```java
@BizQuery
public IncrementalStatus getIncrementalStatus(@Name("indexId") String indexId) {
    return incrementalStatusMap.get(indexId);
}

public static class IncrementalStatus {
    private String indexId;
    private String mode;
    // ... manual getters/setters
}
```
- **Severity**: P3
- **Current State**: IncrementalStatus is returned from @BizQuery but lacks @DataBean. All 35+ other DTOs in the module use @DataBean.
- **Risk**: GraphQL serialization may not properly recognize the type's schema.
- **Recommendation**: Add @DataBean or extract to /api/dto/ package.
- **Confidence**: Certain
- **False Positive Exclusion**: Missing annotation directly affects GraphQL schema generation.
- **Review Status**: Not reviewed

---

### [维度07-03] In-Memory `incrementalStatusMap` Has No Eviction/Cleanup Policy

- **File**: `nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:33`
- **Evidence Snippet**:
```java
private final Map<String, IncrementalStatus> incrementalStatusMap = new java.util.concurrent.ConcurrentHashMap<>();
```
- **Severity**: P2
- **Current State**: Unbounded ConcurrentHashMap that grows with each unique indexId. Only cleaned when deleteIndex is called.
- **Risk**: Slow memory leak in long-running server. Status lost on JVM restart.
- **Recommendation**: Persist status in NopCodeIndex entity's status/extData, or add time-based eviction.
- **Confidence**: Certain
- **False Positive Exclusion**: Concrete unbounded memory growth issue in stateful singleton BizModel.
- **Review Status**: Not reviewed

---

### [维度07-04] CodeIndexService `deleteIndex` Manually Cascades Deletes, Duplicating ORM cascadeDelete

- **File**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1359-1431`
- **Evidence Snippet**:
```java
ormTemplate.runInSession(session -> {
    IEntityDao<NopCodeUsage> usageDao = daoProvider.daoFor(NopCodeUsage.class);
    QueryBean usageQuery = new QueryBean();
    usageQuery.addFilter(FilterBeans.eq("indexId", indexId));
    usageDao.batchDeleteEntities(usageDao.findAllByQuery(usageQuery));
    session.flush();
    // ... repeats for all 9 child entities
    daoProvider.daoFor(NopCodeIndex.class).deleteEntityById(indexId);
});
```
- **Severity**: P2
- **Current State**: ORM model defines cascadeDelete=true on all 9 relations, but deleteIndex manually deletes each child individually — redundant and fragile.
- **Risk**: New child entities with cascadeDelete will be missed by manual code.
- **Recommendation**: Simplify to single deleteEntityById and let ORM cascade handle it.
- **Confidence**: Likely
- **False Positive Exclusion**: ORM configuration and manual code can drift apart — structural maintenance risk.
- **Review Status**: Not reviewed

---

### [维度07-05] CodeIndexService Is a 3033-Line Monolithic Service Class

- **File**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1-3033`
- **Evidence Snippet**:
```java
public class CodeIndexService implements ICodeIndexService {
    // 3033 lines, 212 direct DAO calls
    // entity conversion, indexing, file/symbol queries,
    // search, type/hierarchy, graph analysis,
    // dependency graph, flow detection, semantic edges,
    // incremental indexing
}
```
- **Severity**: P2
- **Current State**: Single class handles all data access, entity conversion, graph analysis, flow detection, search, and more. 40+ methods on ICodeIndexService.
- **Risk**: High maintenance cost, difficult to test individual concerns in isolation.
- **Recommendation**: Decompose into domain-focused services (SymbolQueryService, GraphAnalysisService, etc.) with CodeIndexService as facade.
- **Confidence**: Certain
- **False Positive Exclusion**: 212 direct DAO calls in a single class with no separation of concerns is a concrete maintenance and testability risk.
- **Review Status**: Not reviewed

---

### [维度07-06] @BizLoader Trivial Pass-Through in NopCodeFileBizModel

- **File**: `nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeFileBizModel.java:55-73`
- **Evidence Snippet**:
```java
@BizLoader(forType = CodeFileAnalysisResult.class)
public List<CodeSymbol> symbols(@ContextSource CodeFileAnalysisResult file) {
    return file.getSymbols();
}

@BizLoader(forType = CodeFileAnalysisResult.class)
public String sourceCode(@ContextSource CodeFileAnalysisResult file) {
    return file.getSourceCode();
}
```
- **Severity**: P3
- **Current State**: Two @BizLoader methods simply return fields already on the @ContextSource DTO.
- **Risk**: Redundant runtime overhead.
- **Recommendation**: Remove if fields already serialized by default.
- **Confidence**: Likely
- **False Positive Exclusion**: Identity-pass-through @BizLoader methods are structurally redundant when fields already exist on the DTO.
- **Review Status**: Not reviewed

---

### [维度07-07] `searchCode` and `findPage_symbols` Have Many @Name Parameters — Consider @RequestBean

- **File**: `nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:62-94,181-192`
- **Evidence Snippet**:
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
- **Severity**: P3
- **Current State**: Two methods with 6 @Name parameters each.
- **Risk**: Adding parameters requires changing method signature and GraphQL schema simultaneously.
- **Recommendation**: Consider @RequestBean for better extensibility.
- **Confidence**: Interesting guess
- **False Positive Exclusion**: 6 parameters is at the boundary where @RequestBean becomes beneficial.
- **Review Status**: Not reviewed

---

## Non-Findings (Explicitly Excluded)

| Pattern | Why Not Flagged |
|---------|----------------|
| BizModel returning entity objects | Standard Nop platform pattern |
| @Inject on protected fields | Standard Nop IoC pattern |
| CodeIndexService is not @BizModel | Correct — plain service injected into BizModels |
| All BizModels extend CrudBizModel<T> | Confirmed correct |
| All BizModels call setEntityName() | Confirmed correct |
| No Map<String,Object> in BizModel services | Confirmed clean |

## Summary by Severity

| Severity | Count | Finding IDs |
|----------|-------|-------------|
| P1 | 1 | 07-01 |
| P2 | 3 | 07-03, 07-04, 07-05 |
| P3 | 3 | 07-02, 07-06, 07-07 |

## 维度复核结论

（待复核）

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 摘要 |
|------|---------|---------|------|
| 07-01 | P1 | NopCodeSymbolBizModel.java | @BizLoader 缺少 forType 声明 |
| 07-03 | P2 | NopCodeIndexBizModel.java | incrementalStatusMap 无驱逐策略 |
| 07-04 | P2 | CodeIndexService.java | 手动级联删除与 ORM cascadeDelete 重复 |
| 07-05 | P2 | CodeIndexService.java | 3033 行巨型服务类 |
| 07-02 | P3 | NopCodeIndexBizModel.java | IncrementalStatus 缺少 @DataBean |
| 07-06 | P3 | NopCodeFileBizModel.java | 冗余 pass-through @BizLoader |
| 07-07 | P3 | NopCodeSymbolBizModel.java | 多参数方法建议用 @RequestBean |
