> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata
> Dimension: 07 — BizModel 规范遵循

## 第 1 轮（初审）

### [维度07-001] NopMetaSearchBizModel: 无对应实体的"伪 BizModel"

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/search/NopMetaSearchBizModel.java`
- **严重程度**: P2
- **现状**: `@BizModel("NopMetaSearch")` 没有对应的 ORM 实体（`NopMetaSearch` 实体不存在），没有对应的 `*.xmeta` 文件，也不扩展 `CrudBizModel`。
- **风险**: 违反 service-layer.md 规定的"每个 @BizModel 必须对应一个有 xmeta 的实体"。
- **建议**: 将搜索方法移至现有实体 BizModel（如 NopMetaTableBizModel），或创建带对应 xmeta 的 `@DataBean` 虚拟实体。
- **信心水平**: 确定
- **误报排除**: 这不是 Nop 平台的标准模式。其他 BizModel 都有对应的实体和 xmeta。
- **复核状态**: 未复核

### [维度07-002] NopMetaDataContractBizModel @BizMutation 内冗余事务包裹

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataContractBizModel.java:43,76`
- **证据片段**:
  ```java
  @BizMutation
  public NopMetaDataContract approve(@Name("id") String id, IServiceContext context) {
      return txn().runInTransaction(txn -> {   // 冗余：@BizMutation 已包裹事务
          ...
      });
  }
  ```
- **严重程度**: P2
- **现状**: `approve()` 和 `reject()` 方法在 `@BizMutation` 内部使用 `txn().runInTransaction(...)` 创建了嵌套事务包裹。
- **风险**: 嵌套事务虽不会报错，但违反了平台约定，且可能导致事务边界混淆。
- **建议**: 移除显式的 `txn().runInTransaction(...)` 包裹，让 `@BizMutation` 提供的事务边界工作。
- **信心水平**: 确定
- **误报排除**: 这不是"平台标准模式"。service-layer.md 明确警告不要这样写。
- **复核状态**: 未复核

### [维度07-003] 多个 BizModel 方法使用 dao().getEntityById() 替代 requireEntity()，可能绕过数据权限

- **文件**: 8 个 BizModel 文件中的约 20 个方法
  - `NopMetaDataContractBizModel.java:44,76,102-106,112-116,122-126,133`
  - `NopMetaTableBizModel.java:127,187,211,241,268`
  - `NopMetaQualityRuleBizModel.java:133,200,377`
  - `NopMetaModuleBizModel.java:386,442`
  - `NopMetaReconciliationConfigBizModel.java:99-100,107-108`
- **严重程度**: P2
- **现状**: 上述方法直接调用 `dao().getEntityById(id)` 并手动抛异常，而非使用 `requireEntity(id, actionName, context)`。部分方法（如 `releaseModule`）调用了 `checkDataAuth`，但大多数未调用数据权限检查。
- **风险**: 绕过 `CrudBizModel` 的统一流程，可能绕过数据权限检查。
- **建议**: 将 `dao().getEntityById(id)` + null 检查 + 手动抛出替换为 `requireEntity(id, actionName, context)`。
- **信心水平**: 确定
- **误报排除**: service-layer.md 明确将 `dao().getEntityById()` 列为反模式："绕过 CrudBizModel 的统一流程"。
- **复核状态**: 未复核

### [维度07-004] NopMetaTableBizModel.queryJoinData/queryAggregation 使用 List<Map<String, Object>> 丧失类型安全

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTableBizModel.java:245-283`
- **证据片段**:
  ```java
  List<Map<String, Object>> itemsList = (List<Map<String, Object>>) items;
  result.setItems(itemsList);
  ```
- **严重程度**: P2
- **现状**: DTO 对象包含未类型化的 `List<Map<String, Object>>`，丧失了平台能施加给 GraphQL 返回类型的所有类型安全性。
- **风险**: GraphQL 查询无法进行字段级验证，客户端可能接收到意料之外的结构。
- **建议**: 将 `QueryJoinDataResultDTO` / `AggregationResultDTO` 中的 `List<Map<String, Object>>` 替换为类型化的 `@DataBean` 容器类。
- **信心水平**: 确定
- **误报排除**: service-layer.md 明确说："不要把复杂返回值做成 Map<String, Object>"。
- **复核状态**: 未复核

### [维度07-005] INopMetaDataContractBiz 接口遗漏 checkContractReadOnly 声明

- **文件**: `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/INopMetaDataContractBiz.java`
- **严重程度**: P2
- **现状**: `checkContractReadOnly` 被注解为 `@BizQuery` 并且是 public 的（在 `NopMetaDataContractBizModel.java:164`），但在 `INopMetaDataContractBiz` 接口上缺少声明。
- **建议**: 将 `checkContractReadOnly` 方法签名添加到 `INopMetaDataContractBiz` 接口。
- **信心水平**: 确定
- **误报排除**: service-layer.md 明确规则："BizModel 上新增的每一个 public 方法，都必须在对应的 I*Biz 接口上声明"。
- **复核状态**: 未复核

### [维度07-006] INopMetaTagLabelBiz 接口遗漏 propagateTags/suggestTags 声明

- **文件**: `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/INopMetaTagLabelBiz.java`
- **严重程度**: P2
- **现状**: `propagateTags`（`@BizMutation`）和 `suggestTags`（`@BizMutation`）是 public 且存在于 `NopMetaTagLabelBizModel.java:48,56`，但 `INopMetaTagLabelBiz` 接口缺少对应声明。
- **建议**: 将这两个方法添加到 `INopMetaTagLabelBiz` 接口。
- **信心水平**: 确定
- **误报排除**: 与 F5 同类型问题，独立文件。
- **复核状态**: 未复核

### [维度07-007] NopMetaLineageEdgeBizModel.recordLineage 通过 dao().saveEntity() 绕过 CrudBizModel 统一持久化

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeBizModel.java:76-98`
- **证据片段**:
  ```java
  NopMetaLineageEdge edge = dao().newEntity();
  // ... 设置字段 ...
  dao().saveEntity(edge);
  ```
- **严重程度**: P3
- **现状**: 在 `@BizMutation recordLineage` 内部使用裸 `dao().saveEntity(entity)`，绕过 CrudBizModel 的 save 管道。
- **建议**: 将批量处理提取到一个 Processor 中，或添加 `saveBatch` `@BizMutation` 接受 `List<RecordLineageDTO>`。
- **信心水平**: 很可能
- **误报排除**: 批量插入是该方法的本质要求，属于边界场景灰色地带。
- **复核状态**: 未复核

### [维度07-008] NopMetaDataContractBizModel 已废弃方法存留

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataContractBizModel.java:102-128`
- **严重程度**: P3
- **现状**: `activateContract`、`deprecateContract`、`retireContract` 已被 `@Deprecated` 注解但仍存留。接口与实现之间的 `@Name` 参数名不一致。
- **建议**: 下一步移除这些已废弃的方法。
- **信心水平**: 确定
- **误报排除**: 已废弃代码增加维护负担，有真实清理价值。
- **复核状态**: 未复核
