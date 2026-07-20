# 维度 07：BizModel 规范遵循 — 审计报告

> 初审子 agent 输出，待复核

## 发现条目

### [维度07-01] 公开的 @BizMutation 方法未在 I*Biz 接口上声明

- **文件**: 
  - `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/INopMetaReconciliationConfigBiz.java:9-11`
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaReconciliationConfigBizModel.java:98-100`
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaReconciliationResultBizModel.java:77-81, 106-109`
- **证据**:
  ```java
  // INopMetaReconciliationConfigBiz 仅扩展 ICrudBiz，无自定义方法
  public interface INopMetaReconciliationConfigBiz extends ICrudBiz<NopMetaReconciliationConfig>{
  }
  
  // NopMetaReconciliationConfigBizModel 有 @BizMutation 方法但接口未声明
  @BizMutation
  public NopMetaReconciliationResult executeReconciliation(@Name("configId") String configId,
                                                             IServiceContext context) {
  ```
  同样模式也出现在 `INopMetaReconciliationResultBiz` 中——`confirmMatch` 和 `batchConfirmMatches` 两个 `@BizMutation` 方法未在接口声明。
- **严重程度**: P1
- **现状**: 跨模块调用方若通过代理的 I*Biz 接口调用这些方法，将收到 unsupported-method 错误。GraphQL 调用不受影响（@BizMutation 仍在实现类上），但后端 Java→Java 的 `@Inject INopMetaReconciliationConfigBiz` 调用将失败。
- **风险**: 违反强制规则——"BizModel 上新增的每一个 public 方法，都必须在对应的 I*Biz 接口上声明。"
- **建议**: 向 `INopMetaReconciliationConfigBiz` 添加 `executeReconciliation`，向 `INopMetaReconciliationResultBiz` 添加 `confirmMatch`/`batchConfirmMatches`。
- **信心水平**: 高
- **误报排除**: 这不是"看起来不优雅"问题，是实际运行时故障。代理路由需要接口方法。

### [维度07-02] queryAggregation 和 queryJoinData 超出 @Name 参数限制

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTableBizModel.java:590-601, 551-558`
- **证据**:
  ```java
  @BizQuery
  public Map<String, Object> queryAggregation(@Name("metaTableId") String metaTableId,
                                                 @Name("measures") List<String> measures,
                                                 @Name("dimensions") List<String> dimensions,
                                                 @Optional @Name("filter") TreeBean filter,
                                                 @Optional @Name("joinId") String joinId,
                                                 @Optional @Name("limit") Long limit,
                                                 @Optional @Name("offset") Long offset,
                                                 @Optional @Name("having") TreeBean having,
                                                 @Optional @Name("orderBy") List<OrderFieldBean> orderBy,
                                                 @Optional @Name("selection") FieldSelectionBean selection,
                                                 IServiceContext context) {
  ```
  `queryAggregation` 有 10 个 `@Name` 参数（不含 context），远超 5 个的限制。
- **严重程度**: P2
- **现状**: 参数超过 5 个仍使用 @Name 而非 @RequestBean + @DataBean DTO。
- **风险**: 参数顺序错误风险增加；语义上属于不同输入类的参数混杂在一起；缺乏输入值的运行时验证。
- **建议**: 将输入参数分别封装为 `@DataBean` 注解的请求 DTO 类（如 `MetaAggregationRequest`、`MetaJoinRequest`），使用 `@RequestBean` 注入。
- **信心水平**: 高

### [维度07-03] 广泛使用 Map<String, Object> 作为返回值

- **文件**: 多个 BizModel 文件
- **证据**: 多达 14 个 @BizQuery/@BizMutation 方法返回 `Map<String, Object>`：
  - `NopMetaQualityScoreBizModel.computeQualityScore`
  - `NopMetaDataSourceBizModel` 的 4 个 @BizMutation 方法（testConnection, syncExternalTables, collectCatalog 等）
  - `NopMetaDataContractBizModel.checkContract`
  - `NopMetaTableBizModel` 的多个方法（profileTable, createSqlTable, previewSqlFields, resolveTableFields, queryTableData, queryJoinData, queryAggregation）
  - `NopMetaLineageEdgeBizModel` 的多个方法（recordLineage, extractLineageFromSql, extractColumnLineageFromSql, extractMeasureLineage）
  - `NopMetaQualityRuleBizModel.executeQualityRule / executeQualityRulesForDataSource`
  - `NopMetaQualityCheckpointBizModel.executeCheckpoint`
  - `NopMetaProfilingRuleBizModel.executeProfilingRule`
- **严重程度**: P2
- **现状**: `service-layer.md` 明确将 `Map<String, Object>` 列为反模式。某些确实需要动态结构（如 `testConnection`），但有多个方法具有完全固定的模式（如 `computeQualityScore`、`checkContract`）。
- **风险**: GraphQL schema 将暴露为 JSON 标量，客户端无法进行类型安全的字段选择或使用自动补全。缺乏文档化的返回合约。
- **建议**: 对具有固定模式的方法引入 @DataBean DTO。从 `computeQualityScore`、`checkContract` 和 `executeProfilingRule` 等具有最少返回键的方法开始。
- **信心水平**: 高

### [维度07-04] 具体 BizModel 类注入违背接口契约原则

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityCheckpointBizModel.java:76-77`
- **证据**:
  ```java
  @Inject
  protected NopMetaQualityScoreBizModel scoreBizModel;  // 具体类
  ```
  对比同一模块的正确模式：
  ```java
  // NopMetaReconciliationConfigBizModel.java
  @Inject
  protected INopMetaTableBiz tableBizModel;  // 接口
  ```
- **严重程度**: P3
- **现状**: 注入具体类而非接口，与模块内其他注入模式不一致。虽然代码注释说明这是设计决策，但理由存在矛盾。
- **风险**: 如果模块内出现另一个 NopMetaQualityScoreBizModel 实现（例如用于测试或 delta 定制），具体类注入将无法利用多态性。
- **建议**: 改为 `@Inject protected INopMetaQualityScoreBiz scoreBizModel`，前提是测试确认这不引入事务隔离后退问题。
- **信心水平**: 中

## 合规确认

| 检查项 | 结果 |
|--------|------|
| 32 个 BizModel 均有对应的 ORM 实体 | ✅ |
| 均继承 CrudBizModel<T> | ✅ |
| 构造函数调用 setEntityName() | ✅ |
| 标准查询使用 @BizQuery | ✅ |
| 标准修改使用 @BizMutation | ✅ |
| 自定义方法使用 IServiceContext context | ✅ |
| 参数使用 @Name | ⚠️ 见 #07-02 |
| 返回实体类型（平台标准） | ✅ |
| 无 *Service/*Controller 命名 | ✅ |
| 无伪 BizModel | ✅ |
