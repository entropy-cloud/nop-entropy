# 维度 07：BizModel 规范遵循

## 第 1 轮（初审）

### [维度07-01] NopCodeIndexBizModel 聚集了跨 6 个领域的 24 个自定义方法，成为 God BizModel

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:56-301`
- **证据片段**:
```java
@BizMutation public String triggerFullIndex(...)
@BizMutation public int triggerIncrementalIndex(...)
@BizMutation public int indexDirectory(...)
@BizMutation public FileAnalysisDTO indexFile(...)
@BizMutation public boolean deleteIndex(...)
@BizQuery public CommunityDetectionResultDTO detectCommunities(...)
@BizQuery public GraphAnalysisResultDTO getGraphAnalysis(...)
@BizQuery public CriticalNodeResultDTO getCriticalNodes(...)
@BizQuery public KnowledgeGapResultDTO getKnowledgeGaps(...)
@BizQuery public DepGraphDTO getDeps(...)
@BizQuery public DepGraphDTO getReverseDeps(...)
@BizQuery public List<List<String>> findCycles(...)
@BizQuery public DepGraphDTO getDepGraph(...)
@BizQuery public List<String> findDependentFiles(...)
@BizQuery public ImpactResultDTO getImpactAnalysis(...)
@BizMutation public List<ExecutionFlow> detectFlows(...)
@BizQuery public List<ExecutionFlow> listFlows(...)
@BizQuery public ExecutionFlow getFlow(...)
@BizQuery public List<ExecutionFlow> getAffectedFlows(...)
@BizQuery public ChangeAnalysisResult analyzeChanges(...)
@BizQuery public String exportGraph(...)
@BizQuery public GraphDiffDTO diffGraph(...)
```
- **严重程度**: P2
- **现状**: NopCodeIndexBizModel 包含 24 个自定义方法，横跨索引管理、图分析、依赖分析、影响分析、执行流、图导出/对比共 6 个领域。该 BizModel 的聚合根是 NopCodeIndex，但大部分方法操作的对象是 Symbol、Dependency、Call、Flow 等其他实体。
- **风险**: 违反"方法应该属于它所操作的聚合根"规则。类膨胀到 361 行且 8 个其他 BizModel 是空壳，方法归属不对称。
- **建议**: 将方法按领域拆分到对应 BizModel：图分析→NopCodeSemanticEdgeBizModel，依赖分析→NopCodeDependencyBizModel，流分析→NopCodeFlowBizModel。
- **信心水平**: 确定
- **误报排除**: 平台允许少量编排方法放在入口 BizModel，但 6 个领域 20+ 方法不属于"少量"。
- **复核状态**: 未复核

### [维度07-02] CodeIndexService 使用了平台禁止的 *Service 命名

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:102`
- **证据片段**:
```java
public class CodeIndexService implements ICodeIndexService {
```
```xml
<bean id="io.nop.code.service.impl.CodeIndexService" ioc:default="true"
      class="io.nop.code.service.impl.CodeIndexService"/>
```
- **严重程度**: P2
- **现状**: `CodeIndexService`（1932 行）通过 NopIoC 注册为 bean，被 BizModel 注入。类名以 `Service` 结尾，违反平台"不要在 Nop 模块中创建 `*Service` / `*Controller` 类"的规定。同时 `impl/` 下还有 3 个辅助类也使用 `*Service` 命名。
- **风险**: 与平台其他模块命名风格不一致，容易误导使用者按 Spring 模式使用。
- **建议**: 重命名为 `CodeIndexProcessor`，辅助类重命名为 `*Helper`。
- **信心水平**: 很可能
- **误报排除**: 平台不禁止 Processor 类承载复杂逻辑，只是要求命名一致。
- **复核状态**: 未复核

### [维度07-03] NopCodeSymbolBizModel.findPage_symbols 绕过 CrudBizModel 查询管线

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:66-99`
- **证据片段**:
```java
@BizQuery
@Auth(permissions = "code-query")
public PageBean<SymbolDTO> findPage_symbols(
        @Name("query") @Optional String query,
        @Name("kinds") @Optional List<String> kinds,
        @Name("packageName") @Optional String packageName,
        @Name("indexId") String indexId,
        @Name("offset") @Optional long offset,
        @Name("limit") @Optional int limit) {
    // 手动解析 kinds 参数
    List<CodeSymbolKind> kindList = null;
    if (kinds != null) {
        kindList = kinds.stream()
                .map(k -> {
                    try { return Enum.valueOf(CodeSymbolKind.class, k); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    PageBean<CodeSymbol> page = codeIndexService.findSymbolsPage(indexId, query, kindList,
            packageName, offset, limit > 0 ? limit : 20);
    // 手动构建 PageBean<SymbolDTO>
    ...
}
```
- **严重程度**: P2
- **现状**: 该方法使用 6 个 `@Name` 参数手动构建查询，直接操作 DAO，完全绕过了 CrudBizModel 的 `doFindPage()` 管线。
- **风险**: 绕过 CrudBizModel 的查询预处理管线（包括自动权限过滤），返回类型与 xmeta 定义脱节。
- **建议**: 使用 `doFindPage(query, selection, context)` 并通过 QueryBean 的 filter 机制传入条件。
- **信心水平**: 很可能
- **误报排除**: 该方法查询 core 层 CodeSymbol 而非 ORM 实体 NopCodeSymbol，但这恰恰说明查询链路设计存在问题。
- **复核状态**: 未复核

### [维度07-04] nop-code-api 模块放置了约 30 个局部 DTO 但无外部 RPC 接口

- **文件**: `nop-code/nop-code-api/src/main/java/io/nop/code/api/dto/`（目录下 31 个 DTO 文件）
- **证据片段**:
```
CommunityDetectionResultDTO.java, GraphAnalysisResultDTO.java, ImpactResultDTO.java,
DepGraphDTO.java, CallHierarchyDTO.java, TypeHierarchyDTO.java,
CriticalNodeResultDTO.java, KnowledgeGapResultDTO.java, GraphDiffDTO.java,
CodeSearchResultDTO.java, IndexStatsDTO.java, FileTreeNode.java,
SymbolInfoDTO.java, ReferenceDTO.java, ... 等 16 个 DTO
// 但无外部 RPC Service Interface
```
- **严重程度**: P3
- **现状**: `nop-code-api` 模块包含约 30 个 `@DataBean` DTO 类，但没有外部 RPC Service Interface。按平台规定，`*-api/` 只放外部系统调用本模块的接口和消息类。
- **风险**: 当其他模块依赖 `nop-code-api` 时，会引入所有 30 个 DTO 的传递依赖。与平台其他模块的 api 模块结构不一致。
- **建议**: 将 BizModel 方法专用的局部 DTO 移至 `nop-code-service/.../dto/`。
- **信心水平**: 很可能
- **误报排除**: 平台的 `*-api/` 约定是跨模块通用的架构约束。
- **复核状态**: 未复核

### [维度07-05] NopCodeFileBizModel 返回 core 层模型对象与 xmeta 定义的 ORM 实体类型不匹配

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeFileBizModel.java:34-50`
- **证据片段**:
```java
@BizQuery
@Auth(permissions = "code-query")
public CodeFileAnalysisResult getByPath(
        @Name("filePath") String filePath,
        @Name("indexId") String indexId) {
    return codeIndexService.getFile(indexId, filePath);
}

@BizQuery
@Auth(permissions = "code-query")
public PageBean<CodeFileAnalysisResult> findPage_files(
        @Name("indexId") String indexId,
        @Name("packageName") @Optional String packageName,
        @Name("offset") @Optional long offset,
        @Name("limit") @Optional int limit) {
    return codeIndexService.findFilesPage(indexId, packageName, offset, limit > 0 ? limit : 20);
}
```
- **严重程度**: P3
- **现状**: `NopCodeFileBizModel` 继承 `CrudBizModel<NopCodeFile>`，但自定义查询方法返回的是 core 层的 `CodeFileAnalysisResult`（非 ORM 实体、非 `@DataBean` DTO）。
- **风险**: xmeta 中定义的字段控制对 `CodeFileAnalysisResult` 对象可能不生效，因为字段可见性控制依赖于 ORM 实体的 propId 映射。
- **建议**: 将返回类型改为 `NopCodeFile` 实体或 `@DataBean` DTO。
- **信心水平**: 很可能
- **误报排除**: `CodeFileAnalysisResult` 不是 `@DataBean` DTO，它是 core 层模型对象，且该 BizModel 明确继承了 `CrudBizModel<NopCodeFile>`。
- **复核状态**: 未复核

### [维度07-06] 8 个 BizModel 是空壳继承，暴露了关系实体的 CRUD

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeUsageBizModel.java:7-12`（及另外 7 个同类文件）
- **证据片段**:
```java
@BizModel("NopCodeUsage")
public class NopCodeUsageBizModel extends CrudBizModel<NopCodeUsage> implements INopCodeUsageBiz{
    public NopCodeUsageBizModel(){
        setEntityName(NopCodeUsage.class.getName());
    }
}
```
- **严重程度**: P3
- **现状**: 8 个 BizModel（Usage, Call, Inheritance, Dependency, Flow, FlowMembership, SemanticEdge, AnnotationUsage）仅包含构造函数调用 `setEntityName()`，没有任何自定义方法。这些实体是关系/边数据，通常不应直接通过 GraphQL CRUD 暴露。
- **风险**: 对关系/边实体暴露标准 CRUD 允许外部客户端直接创建、修改、删除调用关系等数据，可能破坏图数据完整性。
- **建议**: 评估这些实体的 CRUD 是否需要对外暴露。如不需要可移除这些空壳 BizModel。
- **信心水平**: 很可能
- **误报排除**: 这些实体是关系/边数据而非独立聚合根，空壳与 NopCodeIndexBizModel 的方法膨胀形成结构性矛盾。
- **复核状态**: 未复核
