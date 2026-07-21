# 维度07：BizModel 规范遵循 — 第1轮（初审）

> 审计模块: nop-metadata

## 发现清单

### [维度07-01] NopMetaSearchBizModel：无对应 ORM 实体和 xmeta 的"伪 BizModel"

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/search/NopMetaSearchBizModel.java:28-29`
- **证据片段**:
  ```java
  @BizModel("NopMetaSearch")
  public class NopMetaSearchBizModel {
      @Inject
      protected NopMetaIndexBuilder indexBuilder;
      @Inject
      protected NopMetaSearchService searchService;
      // 不继承 CrudBizModel, 不 implements 任何 I*Biz
  ```
- **严重程度**: P1
- **现状**: `NopMetaSearchBizModel` 使用 `@BizModel("NopMetaSearch")` 但没有对应的 ORM 实体、xmeta、I*Biz 接口。该项目另外 39 个 `@BizModel` 类全部有对应实体和 xmeta。`searchMetadata` 为 `@BizQuery`，`rebuildSearchIndex` 为 `@BizMutation`，前端 GraphQL 调用时因缺少 xmeta 定义而失败。
- **风险**: 违反 `service-layer.md` "每个 @BizModel 必须对应有 xmeta 的实体"规则。GraphQL schema 无法构建 object definition。浏览器页面调用失败。
- **建议**: (1) 创建轻量 `NopMetaSearch.xmeta` 声明字段；或 (2) 去掉 `@BizModel` 注解，改为 Processor 注入到 `NopMetaTableBizModel`。
- **信心水平**: 确定
- **误报排除**: `service-layer.md` 明确禁止无 xmeta 的 @BizModel。模块另外 39 个 BizModel 都遵守此规则。
- **复核状态**: 未复核

### [维度07-02] NopMetaLineageEdgeBizModel.recordLineage 以 `Map<String, Object>` 代替类型安全 DTO

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeBizModel.java:123-125`
- **证据片段**:
  ```java
  @BizMutation
  public LineageRecordResultDTO recordLineage(@Name("edges") List<Map<String, Object>> edges,
                                                IServiceContext context) {
      Map<String, Object> m = edges.get(i);
      String sourceTableId = readString(m, "sourceTableId");
  ```
- **严重程度**: P2
- **现状**: 接受 `List<Map<String, Object>>` 作为输入，手工按 key 取值映射到实体属性。
- **风险**: 缺乏编译期类型检查，拼错 key 静默 null；GraphQL schema 无法推导字段；无法利用 xmeta 校验。
- **建议**: 定义 `@DataBean` DTO（如 `LineageEdgeInputDTO`），改为 `List<LineageEdgeInputDTO>`。
- **信心水平**: 确定
- **误报排除**: edges 有 9 个潜在字段结构体，远超 `@Name` 适用范围。
- **复核状态**: 未复核

### [维度07-03] NopMetaModuleBizModel.importOrmModels 返回 `Map<String, Object>`

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaModuleBizModel.java:361-386`
- **证据片段**:
  ```java
  @BizMutation
  public List<Map<String, Object>> importOrmModels(@Name("paths") List<String> paths, IServiceContext context) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("path", path);
      result.put("metaModuleId", module.getMetaModuleId());
  ```
- **严重程度**: P2
- **现状**: 返回 `List<Map<String, Object>>`，同一接口中其他方法都返回类型安全的实体或 DTO。
- **风险**: GraphQL schema 无法推导内部结构，前端只能非类型安全消费。
- **建议**: 定义 `ImportOrmModelResultDTO` 并返回 `List<ImportOrmModelResultResultDTO>`。
- **信心水平**: 确定
- **误报排除**: 返回数据包含 4-5 个固定字段，完全符合 DTO 场景。
- **复核状态**: 未复核

### [维度07-04] NopMetaDataSourceBizModel 直接使用 `dao().getEntityById()` 绕过 requireEntity

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java:120,175,254`
- **证据片段**:
  ```java
  @BizMutation
  public TestConnectionResultDTO testConnection(@Name("dataSourceId") String dataSourceId, IServiceContext context) {
      NopMetaDataSource dataSource = dao().getEntityById(dataSourceId);  // 跳过 requireEntity
  }
  ```
  同样模式在 `syncExternalTables` 和 `collectCatalog` 中。
- **严重程度**: P2
- **现状**: 3 个 `@BizMutation` 方法使用 `dao().getEntityById()` 而非 `requireEntity()`，跳过数据权限检查和元数据过滤。
- **风险**: 当存在数据权限需求时，这些方法不会应用权限过滤，可能导致越权访问。
- **建议**: 替换为 `requireEntity(dataSourceId, actionName, context)`。
- **信心水平**: 确定
- **误报排除**: dataSourceId 是当前操作的主实体 ID，操作是对该数据源的 mutation，应该经过权限校验。
- **复核状态**: 未复核

### [维度07-05] NopMetaLineageEdgeBizModel 批量操作直接使用 DAO

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeBizModel.java:766-805,847-857`
- **证据片段**:
  ```java
  private void upsertSqlParseEdge(String sourceTableId, String targetTableId) {
      NopMetaLineageEdge edge = dao().findFirstByQuery(q);
      if (edge == null) {
          edge = dao().newEntity();
          dao().saveEntity(edge);
      } else {
          dao().updateEntity(edge);
      }
  }
  ```
- **严重程度**: P3
- **现状**: `upsertSqlParseEdge`、`deleteMeasureParseEdges` 等方法直接使用 `dao().saveEntity()`/`dao().deleteEntity()` 进行批量持久化。
- **风险**: 跳过 CrudBizModel 统一流程，但属于批量内部操作，非对外 API。
- **建议**: 当前可接受（边界场景），未来可封装成 Processor 类。
- **信心水平**: 很可能
- **误报排除**: 批量 lineage upsert 是 `service-layer.md` 认可的边界场景（第 254 行）。
- **复核状态**: 未复核

### [维度07-06] NopMetaDictBizModel 和 NopMetaTagBizModel 正确遵循规范（正向）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDictBizModel.java:18-23`, `NopMetaTagBizModel.java:18-26`
- **严重程度**: 无（正向发现）
- **现状**: 两个 BizModel 正确继承 `CrudBizModel<T>`、实现 `I*Biz` 接口、构造函数调用 `setEntityName()`、使用 `@Inject protected` 字段。
- **复核状态**: 未复核

### [维度07-07] @Inject protected 字段使用正确（正向）

- **文件**: `NopMetaTableBizModel.java:127-134`, `NopMetaDataSourceBizModel.java:77-81`
- **严重程度**: 无（正向发现）
- **现状**: 所有 `@Inject` 字段均为 `protected` 可见性，符合 NopIoC 规范。
- **复核状态**: 未复核

## 总结

| # | 发现 | 严重程度 | 核心违规 |
|---|------|---------|---------|
| 07-01 | NopMetaSearchBizModel 是无 xmeta 的伪 BizModel | P1 | 违反"每个 @BizModel 必须有 xmeta" |
| 07-02 | recordLineage 使用 Map<String, Object> 输入 | P2 | 违反"不要 Map 代替类型安全结构" |
| 07-03 | importOrmModels 返回 Map<String, Object> | P2 | 违反"多字段返回优先 @DataBean" |
| 07-04 | DataSourceBizModel 绕过 requireEntity() | P2 | 违反 CrudBizModel 统一流程 |
| 07-05 | LineageEdgeBizModel 批量操作直接 DAO | P3 | 边界场景 |
