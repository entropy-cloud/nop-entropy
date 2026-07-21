# 维度 07：BizModel 规范遵循

## 第 1 轮（初审）

### [维度07-01] BizModel 方法以 `Map<String, Object>` 替代 `@DataBean` DTO 返回类型（批量违反）

- **文件**: 多个 BizModel 文件（NopMetaTableBizModel, NopMetaDataSourceBizModel, NopMetaQualityRuleBizModel, NopMetaLineageEdgeBizModel, NopMetaQualityCheckpointBizModel, NopMetaQualityScoreBizModel, NopMetaDataContractBizModel）
- **行号范围**: ~20 个 @BizQuery/@BizMutation 方法均返回 Map<String, Object>
- **证据代码片段**（以 NopMetaTableBizModel.profileTable 为例）:
  ```java
  @BizMutation
  public Map<String, Object> profileTable(@Name("metaTableId") String metaTableId,
                                           @Optional @Name("schemaPattern") String schemaPattern,
                                           @Optional @Name("columns") String columns,
                                           IServiceContext context) {
      // ...
      return buildResultMap(row, snapshot);
  }
  ```
- **严重程度**: P1
- **现状**: 模块内有 24 个 `@DataBean` DTO 类已在 `nop-metadata-service/.../dto/` 包下定义，但所有 BizModel 的自定义动作方法均返回 `Map<String, Object>`，未使用这些 DTO。DTO 仅被测试引用，处于"悬空"状态。
- **风险**: 违反平台规范 `service-layer.md` 明文规则。`Map<String, Object>` 缺乏编译期类型安全，GraphQL 端无法获得确定的 schema 类型定义。24 个已定义 DTO 成为维护负债。
- **建议**: 将 BizModel 的自定义 action 方法返回类型从 `Map<String, Object>` 改为对应的 `@DataBean` DTO。
- **信心水平**: 高
- **误报排除**: 平台规范允许汇总统计等场景使用 `@DataBean` DTO，模块已对齐此方向（DTO 已定义），只是实现层尚未切换。
- **复核状态**: 未复核

### [维度07-02] BizModel 的 public 方法未同步到 I*Biz 接口

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataContractBizModel.java`, `NopMetaQualityResultBizModel.java`
- **行号范围**: NopMetaDataContractBizModel:37-64, NopMetaQualityResultBizModel:26-51
- **证据代码片段**:
  ```java
  // NopMetaDataContractBizModel
  @BizMutation
  public NopMetaDataContract approve(@Name("id") String id, IServiceContext context) { ... }
  
  @BizMutation
  public NopMetaDataContract reject(@Name("id") String id, IServiceContext context) { ... }
  ```
  INopMetaDataContractBiz 接口未声明 approve/reject。INopMetaQualityResultBiz 接口完全为空（仅继承 ICrudBiz），未声明 approve/reject。
- **严重程度**: P2
- **现状**: `NopMetaDataContractBizModel.approve()`/`reject()` 和 `NopMetaQualityResultBizModel.approve()`/`reject()` 是 `@BizMutation` public 方法，但对应 I*Biz 接口未声明。跨模块通过接口注入的调用方将无法调用这些方法。
- **风险**: 跨模块调用方若通过 `@Inject INopMetaDataContractBiz` 访问，由于 BizProxyFactoryBean 只识别接口上的方法，调用 approve/reject 会抛异常。
- **建议**: 在接口中新增方法声明，或将 BizModel 方法改为非 public。
- **信心水平**: 高
- **误报排除**: activateContract/deprecateContract/retireContract 已在接口中声明，只有 approve/reject 缺失。
- **复核状态**: 未复核

### [维度07-03] queryAggregation 和 queryJoinData 参数数超过 5 个未使用 @RequestBean

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTableBizModel.java:510-569`
- **证据代码片段**:
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
- **严重程度**: P2
- **现状**: `queryAggregation` 有 11 个参数，`queryJoinData` 有 7 个参数，远超规范建议的 1-5 个参数阈值。
- **风险**: 参数扩展不灵活，新增字段必须修改方法签名影响所有调用方。
- **建议**: 为 queryAggregation 和 queryJoinData 创建 @DataBean 请求 DTO，将参数聚合为单一 @RequestBean。
- **信心水平**: 高
- **误报排除**: queryAggregation 的 11 个参数明确超限。
- **复核状态**: 未复核

### [维度07-04] public 方法缺少 @Biz* 注解

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityRuleBizModel.java:375-393`
- **证据代码片段**:
  ```java
  public QualityRuleJudgment judgeByRuleId(String ruleId) {
      NopMetaQualityRule rule = dao().getEntityById(ruleId);
      if (rule == null) {
          throw new NopException(NopMetadataErrors.ERR_QUALITY_RULE_NOT_FOUND)
              .param("qualityRuleId", ruleId);
      }
      // ...
  }
  ```
- **严重程度**: P3
- **现状**: `judgeByRuleId` 是 public 方法但未标注 @BizQuery/@BizMutation/@BizAction，返回内部类型 QualityRuleJudgment（不可序列化），且无 I*Biz 接口声明。
- **风险**: 暴露在类的外部 API 表面但无法被 BizModel 路由框架发现。
- **建议**: 将方法改为 protected 或添加 @BizAction 注解并在接口中声明。
- **信心水平**: 中
- **误报排除**: 该方法可能是为工作流回调预留的入口。
- **复核状态**: 未复核

### [维度07-05] raw 实现类注入替代接口注入（有记录）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityCheckpointBizModel.java:81-83`
- **证据代码片段**:
  ```java
  @Inject
  protected NopMetaQualityScoreBizModel scoreBizModel;
  ```
- **严重程度**: P3
- **现状**: 注入 raw 实现类而非 `INopMetaQualityScoreBiz` 接口。注释中明确说明是为避免 BizProxy 事务隔离问题。
- **风险**: 跨模块可替换性降低。
- **建议**: 已在计划中跟踪，Phase 重评估 BizProxy 事务隔离问题后切回接口注入。
- **信心水平**: 高
- **误报排除**: 代码注释明确记录此偏离是已知设计决策且有后续计划。
- **复核状态**: 未复核

### [维度07-06] 直接 DAO 操作绕过安全 API

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeBizModel.java:169-170`
- **证据代码片段**:
  ```java
  for (NopMetaLineageEdge edge : parsed) {
      dao().saveEntity(edge);
  }
  ```
- **严重程度**: P3
- **现状**: 多处直接调用 `dao().saveEntity()`/`dao().updateEntity()`/`dao().deleteEntity()` 而非通过 CrudBizModel 的安全 API。
- **风险**: 绕过数据权限检查、实体变更事件发布等统一流程。
- **建议**: 对顶层 @BizMutation 中的批量写入考虑使用 CrudBizModel 安全方法，或在代码注释中说明选择直接 DAO 的原因。
- **信心水平**: 中
- **误报排除**: 批量 upsert 场景走 super.save() 会逐条触发 CRUD 全流程，可能不是预期行为。部分直接 DAO 调用是合理选型。
- **复核状态**: 未复核

## 深挖第 2 轮追加

### [维度07-07] NopMetaDataProductBizModel 的 3 个 public 方法未同步到空接口 INopMetaDataProductBiz

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataProductBizModel.java:40-114`
- **证据代码片段**:
  ```java
  @BizMutation
  public NopMetaTagLabel linkAsset(@Name("dataProductId") String dataProductId, ...)
  @BizMutation
  public boolean unlinkAsset(@Name("dataProductId") String dataProductId, ...)
  @BizQuery
  public List<NopMetaTagLabel> getLinkedAssets(@Name("dataProductId") String dataProductId, ...)
  ```
  接口侧：
  ```java
  public interface INopMetaDataProductBiz extends ICrudBiz<NopMetaDataProduct>{
  }
  ```
- **严重程度**: P2
- **现状**: `INopMetaDataProductBiz` 接口完全为空，仅继承 `ICrudBiz`。但 `NopMetaDataProductBizModel` 定义了 3 个自定义 public 方法（`linkAsset`、`unlinkAsset`、`getLinkedAssets`），接口上未声明。与 [维度07-02] 同类型。
- **风险**: 跨模块通过接口注入的调用方无法访问这些方法。
- **建议**: 在接口中新增方法声明。
- **信心水平**: 高
- **误报排除**: 接口为空不是设计意图——BizModel 定义的自定义方法非 CRUD 覆盖。
- **复核状态**: 未复核
