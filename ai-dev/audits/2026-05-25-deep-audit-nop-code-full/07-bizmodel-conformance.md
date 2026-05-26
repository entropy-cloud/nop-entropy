# 维度07：BizModel 规范遵循

## 第 1 轮（初审）

### [维度07-01] CodeIndexApi 是无 ORM 实体、无 xmeta 的伪 BizModel 接口

- **文件**: `nop-code/nop-code-api/src/main/java/io/nop/code/api/CodeIndexApi.java:12-13`
- **证据片段**:
```java
@BizModel("NopCodeIndexApi")
public interface CodeIndexApi {
    @BizMutation
    ApiResponse<String> fullIndex(ApiRequest<Map<String, Object>> request);
    @BizQuery
    ApiResponse<List<Map<String, Object>>> searchCode(ApiRequest<Map<String, Object>> request);
}
```
- **严重程度**: P1
- **现状**: `CodeIndexApi` 声明为 `@BizModel` 接口，但没有对应 ORM 实体、没有 xmeta、没有实现类、所有方法使用 `Map<String, Object>` 代替类型安全结构。
- **风险**: 违反三个规则：伪 BizModel（无 xmeta）、Map 反模式、无实现类。
- **建议**: 删除此接口，或将其功能迁移到 `NopCodeIndexBizModel` 中已有方法。
- **误报排除**: 直接违反 service-layer.md 中"每个 @BizModel 必须对应一个有 xmeta 的实体"规则。
- **复核状态**: 未复核

---

### [维度07-02] NopCodeIndexBizModel 使用内存 Map 管理增量索引状态，多实例不可靠

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:34`
- **证据片段**:
```java
@BizModel("NopCodeIndex")
public class NopCodeIndexBizModel extends CrudBizModel<NopCodeIndex> implements INopCodeIndexBiz {
    private final Map<String, IncrementalStatus> incrementalStatusMap = new LinkedHashMap<>();
    
    @BizMutation
    public String triggerFullIndex(@Name("indexId") String indexId, @Name("projectPath") String projectPath) {
        int fileCount = codeIndexService.indexDirectory(indexId, projectPath, "**/*.java");
        IncrementalStatus status = new IncrementalStatus();
        status.setIndexId(indexId);
        incrementalStatusMap.put(indexId, status);
    }
}
```
- **严重程度**: P2
- **现状**: 使用实例字段 `LinkedHashMap<String, IncrementalStatus>` 存储增量索引状态。JVM 重启后状态丢失，集群部署中各节点状态不一致。
- **风险**: 状态不可持久化、集群不一致、`IncrementalStatus` 未标注 `@DataBean`。
- **建议**: 利用 `NopCodeIndex` ORM 实体本身存储状态字段。
- **误报排除**: 不是平台标准查询模式（`doFindList`/`doFindPage`），而是手动维护内存 Map。
- **复核状态**: 未复核

---

### [维度07-03] NopCodeSymbolBizModel 的 @BizLoader 使用 DTO 而非实体作为 @ContextSource

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:96-120`
- **证据片段**:
```java
@BizLoader
public List<AnnotationUsageDTO> usages(
        @ContextSource SymbolDTO symbol,
        @Name("indexId") String indexId,
        @Name("limit") int limit) {
    return codeIndexService.getSymbolUsages(indexId, symbol.getId(), limit > 0 ? limit : 20)
            .stream().map(AnnotationUsageDTO::fromCodeAnnotationUsage).collect(Collectors.toList());
}
```
- **严重程度**: P2
- **现状**: `@ContextSource` 接收 `SymbolDTO`（DTO）而非 ORM 实体 `NopCodeSymbol`，与 BizModel 泛型参数不匹配。
- **风险**: 与平台 `GraphQLExecutor.fetchSelections()` 的标准字段获取流程不一致。
- **建议**: 改为 `@ContextSource NopCodeSymbol`，或将这些方法改为标准 `@BizQuery`。
- **误报排除**: `@BizLoader` + `@ContextSource` 的接收类型与 BizModel 实体类型不匹配。
- **复核状态**: 未复核

---

### [维度07-04] NopCodeIndexBizModel 承担过多不属于 NopCodeIndex 聚合根的职责

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:110-247`
- **证据片段**:
```java
@BizQuery
public List<List<String>> findCycles(@Name("indexId") String indexId, @Name("minSize") @Optional Integer minSize) {...}
@BizQuery
public DepGraphDTO getDepGraph(@Name("indexId") String indexId, ...) {...}
@BizQuery
public List<ExecutionFlow> detectFlows(@Name("indexId") String indexId) {...}
@BizQuery
public ChangeAnalysisResult analyzeChanges(...) {...}
@BizQuery
public DeadCodeReport detectDeadCode(@Name("indexId") String indexId) {...}
```
- **严重程度**: P2
- **现状**: 23 个自定义方法涵盖 7 个不同领域，依赖图/执行流/变更分析等操作的主要实体不是 `NopCodeIndex`。对应 BizModel（`NopCodeDependencyBizModel`、`NopCodeFlowBizModel`）是空壳。
- **风险**: 调用方需要通过不直观的 BizModel 名字查找方法。
- **建议**: 将方法按操作对象重新分配到对应的 BizModel。
- **误报排除**: 对应 BizModel 已经存在但是空壳，不是"编排级操作归到某个 BizModel"的合理场景。
- **复核状态**: 未复核

---

### [维度07-05] CodeIndexService（2784 行）承担了本应由 Processor 或多个服务拆分的职责

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1-2784`
- **证据片段**:
```java
public class CodeIndexService implements ICodeIndexService {
    private final Map<String, AnalysisCache> analysisCacheMap = new HashMap<>();
    protected final LanguageAdapterRegistry registry;
    protected final ProjectAnalyzer analyzer;
    @Inject protected IDaoProvider daoProvider;
    @Inject protected IOrmTemplate ormTemplate;
}
```
- **严重程度**: P2
- **现状**: 2784 行巨型类，实现 50+ 方法，涵盖索引管理、图分析、依赖分析、执行流、搜索引擎等。直接使用 `daoProvider.daoFor()` 67 次。
- **风险**: 可维护性和可测试性急剧下降，功能变更容易意外影响其他功能。
- **建议**: 按领域拆分为多个 Processor。
- **误报排除**: 违反 service-layer.md "不要在 Nop 模块中创建 *Service 类"的规则。
- **复核状态**: 未复核

---

### [维度07-06] NopCodeIndexBizModel 和 NopCodeSymbolBizModel 的查询方法绕过 CrudBizModel 标准查询流程

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:48-94`
- **证据片段**:
```java
@BizQuery
public SymbolDTO getBySymbolId(@Name("id") String id, @Name("indexId") String indexId) {
    CodeSymbol symbol = codeIndexService.getSymbolById(indexId, id);
    return symbol != null ? SymbolDTO.fromCodeSymbol(symbol) : null;
}
```
- **严重程度**: P3
- **现状**: 继承了 `CrudBizModel<NopCodeSymbol>`，但所有查询方法都委托给 `codeIndexService`，没有使用 `doFindList()`/`doFindPage()` 等标准 API。
- **风险**: 丧失平台自动提供的查询预处理、权限过滤等特性。
- **建议**: 评估哪些查询可以直接走 ORM，应优先使用 `doFindList`/`doFindPage`。
- **误报排除**: 该模块是分析型模块，查询对象是内存中的分析结果。架构选择可理解。
- **复核状态**: 未复核

---

### [维度07-07] NopCodeIndexBizModel 的 IncrementalStatus 内部类缺少 @DataBean 注解

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:248-303`
- **证据片段**:
```java
public static class IncrementalStatus {
    private String indexId;
    private String mode;
    private int fileCount;
    // 无 @DataBean 注解
}
```
- **严重程度**: P3
- **现状**: 通过 `@BizQuery` 方法返回给前端，但缺少 `@DataBean` 注解。
- **风险**: 平台在序列化/GraphQL 注册时可能无法正确识别该类型。
- **建议**: 添加 `@DataBean` 注解。
- **误报排除**: 通过 BizQuery 方法返回给 GraphQL 前端的公开类型需要 `@DataBean` 注册。
- **复核状态**: 未复核

---

### [维度07-08] NopCodeSymbolBizModel 的 @BizLoader 方法要求调用方传入 indexId 参数

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:96-108`
- **证据片段**:
```java
@BizLoader
public List<AnnotationUsageDTO> usages(
        @ContextSource SymbolDTO symbol,
        @Name("indexId") String indexId,
        @Name("limit") int limit) {
    if (indexId == null) throw new NopException(NopCodeErrors.ERR_CODE_INDEX_ID_REQUIRED);
}
```
- **严重程度**: P3
- **现状**: `@BizLoader` 方法要求必填 `indexId` 参数，不符合 Loader "给定父对象自动派生字段值"的惯例。
- **建议**: 将 `indexId` 通过 `IServiceContext` 传递，或改为标准 `@BizQuery` 方法。
- **误报排除**: 标准 Loader 不需要额外必填查询参数。
- **复核状态**: 未复核

---

### [维度07-09] CodeIndexApi 接口使用 Map<String, Object> 代替类型安全的请求/响应结构

- **文件**: `nop-code/nop-code-api/src/main/java/io/nop/code/api/CodeIndexApi.java:15-28`
- **证据片段**:
```java
@BizMutation
ApiResponse<String> fullIndex(ApiRequest<Map<String, Object>> request);
@BizQuery
ApiResponse<List<Map<String, Object>>> searchCode(ApiRequest<Map<String, Object>> request);
```
- **严重程度**: P2
- **现状**: 全部 5 个方法使用 `Map<String, Object>`，但模块已存在大量类型安全的 DTO（SymbolDTO、TypeHierarchyDTO 等）。
- **风险**: 丧失编译时类型检查，与 service-layer.md 规则直接冲突。
- **建议**: 使用已有 DTO 创建类型安全的 API 方法。
- **误报排除**: 模块对外 API 接口的公开签名应使用类型安全的 DTO。
- **复核状态**: 未复核

---

### [维度07-10] CodeIndexService 类命名违反 Nop 平台约定

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:92`
- **证据片段**:
```java
public class CodeIndexService implements ICodeIndexService { ... }
```
- **严重程度**: P3
- **现状**: 类命名为 `*Service`，违反 service-layer.md 明确规则："不要在 Nop 模块中创建 *Service 类"。
- **建议**: 重命名为 `CodeIndexProcessor`。
- **误报排除**: 平台文档中明确列为"不要这样做"的模式。
- **复核状态**: 未复核

---

## 审计总结

| 严重程度 | 数量 | 发现编号 |
|---------|------|---------|
| P1 | 1 | 07-01 |
| P2 | 4 | 07-02, 07-04, 07-05, 07-09 |
| P3 | 5 | 07-03, 07-06, 07-07, 07-08, 07-10 |
| **总计** | **10** | |
