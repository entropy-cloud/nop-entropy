# Audit Dimension 02: Module Responsibility — nop-code

### [02-01] CodeIndexService God Object — 3032 行，职责混合

- **File**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1-3032`
- **Evidence Snippet**:
```java
public class CodeIndexService implements ICodeIndexService {
    private final Map<String, AnalysisCache> analysisCacheMap = new ConcurrentHashMap<>();
    protected final LanguageAdapterRegistry registry;
    protected final ProjectAnalyzer analyzer;
    protected final Map<String, IImportResolver> importResolvers = new HashMap<>();
    @Inject protected IDaoProvider daoProvider;
    @Inject protected IOrmTemplate ormTemplate;
    protected ISearchEngine searchEngine;
```
- **Severity**: P1
- **Current State**: 单体服务混合了文件索引、CRUD查询（80+次daoProvider.daoFor()调用）、图分析协调、流分析协调、搜索引擎集成、缓存、语言适配器注册、批处理持久化。ICodeIndexService 接口178行覆盖9个功能分组。
- **Risk**: 任何区域变更可能破坏其他区域；测试困难；高认知负荷。
- **Recommendation**: 分解为 CodeIndexingService、CodeQueryService、CodeGraphService、CodeFlowService。
- **Confidence**: High
- **False Positive Exclusion**: 3032行，比任何其他服务层文件大10倍+。
- **Review Status**: Not reviewed

---

**其他维度 02 检查项**：核心/图/流/lang 模块中的大文件（CommunityDetector 891行, ProjectAnalyzer 861行等）是纯算法库，职责单一，不构成问题。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 摘要 |
|------|---------|---------|------|
| 02-01 | P1 | CodeIndexService.java | 3032行 God Object，9种职责混合 |
