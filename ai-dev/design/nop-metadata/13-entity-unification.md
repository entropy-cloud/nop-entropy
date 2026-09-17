# nop-metadata 实体归一设计（删除 NopMetaEntity，功能归一 NopMetaEntity）

**日期**：2026-09-14
**范围**：`nop-metadata` 模块 ORM 模型、dao/service/api/meta/web 全部子模块
**状态**：active（计划 2261 Phase 1 交付物）
**裁定依据**：NopORM 不变式——实体 = 物理表设计 + 关联语义标注，任一物理表都有对应的 ORM 模型，任一 ORM 模型本质上都对应一个物理表设计（外部表 = querySpace 路由实体、SQL 视图 = sqlText 列实体）。独立"逻辑表"概念冗余。

---

## 一、设计结论

1. **NopMetaEntity 系 5 实体（Table/Measure/Dimension/Filter/Join）删除**，全部功能归一到 NopMetaEntity 及其作用域子实体。
2. **NopMetaEntity 新增类型判别列 `entityKind`**：`PHYSICAL`（物理表，原 entity）/ `SQL_VIEW`（SQL 视图，原 entityKind=sql）/ `EXTERNAL`（外部表，原 entityKind=external），默认 PHYSICAL，mandatory；dict `meta/entity-kind` 替代 `meta/table-type`。
3. **NopMetaEntity 新增两列**：`sourceSql`（mediumtext，sensitive；SQL_VIEW 的来源 SELECT）；`externalColumns`（mediumtext，sensitive；EXTERNAL 的列结构 JSON，**格式与原 Table.buildSql 的 serializeColumns 输出完全一致**，保证字段解析器兼容迁移）。
4. **4 张子表改名换挂点**：`NopMetaEntityMeasure→NopMetaEntityMeasure`、`NopMetaEntityDimension→NopMetaEntityDimension`、`NopMetaEntityFilter→NopMetaEntityFilter`、`NopMetaEntityJoin→NopMetaEntityJoin`；挂点列 `metaEntityId→metaEntityId`；`NopMetaEntityJoin` 删除 leftEntityId/rightEntityId 端点（纯实体端点），`validateJoinSide` 的 table 分支删除。
5. **9 个非子表 to-one FK 全部改指 NopMetaEntity**（Catalog/ProfilingRule/ProfilingResult/DataContract/ReconciliationConfig/ReconciliationResult/QualityScore 的 `metaEntityId→metaEntityId`；LineageEdge `sourceEntityId/targetEntityId→sourceEntityId/targetEntityId`）；`NopMetaModule` 反向 to-many `tables→entities`。
6. **对外 API 以实体为中心改名**（动作名与 DTO 名），`INopMetaEntityBiz` 并入 `INopMetaEntityBiz`；错误码常量保持不变（契约稳定）。
7. **4 类字符串软引用全部切换**为 NopMetaEntity/entity：搜索短名、TagLabel 与 DataProduct 的 entityType、ModelChangedEvent、QualityRule 的 quality-entity-type dict 值。
8. **syncExternalTables 与 OrmModelImporter 只产出 NopMetaEntity**：双写消失；多 schema 共存由"实体名同步生成规则 + dbSchema 描述列"承载（见 §3.2）。

## 二、目标模型

### 2.1 NopMetaEntity 列变化（增量）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| entityKind | string(20) | mandatory, ext:dict=`meta/entity-kind` | `PHYSICAL` / `SQL_VIEW` / `EXTERNAL`；存量物理实体默认 PHYSICAL |
| sourceSql | string | domain=mediumtext, tagSet="sensitive", nullable | 仅 SQL_VIEW：来源 SELECT 语句（原 Table.sourceSql） |
| externalColumns | string | domain=mediumtext, tagSet="sensitive", nullable | 仅 EXTERNAL：列结构 JSON，格式 = 原 `serializeColumns` 输出（columnName/stdDataType/precision 等），`MetaEntityFieldResolver` 的 external 分派逻辑原样迁移读取此列 |

既有列全部保留：`tableName`（物理表名/视图名/外部表名）、`querySpace`、`dbCatalog`/`dbSchema`（承接原 dbSchema 语义）、`businessDomainId`、`className` 等。原 `NopMetaEntity.baseEntityId` 的配对语义消失（实体自身即唯一行）。

### 2.2 子实体改名与 Join 端点收敛

| 原实体（Table 系，已更名） | 新实体 | 变化 |
|---|---|---|
| 原 Table 系 Measure（更名） | NopMetaEntityMeasure | 宿主列统一为 `metaEntityId`；UK (metaEntityId, measureName)；entityFieldId 引用保持 |
| 原 Table 系 Dimension（更名） | NopMetaEntityDimension | 同上模式；UK (metaEntityId, dimensionName) |
| 原 Table 系 Filter（更名） | NopMetaEntityFilter | 同上模式；UK (metaEntityId, filterName) |
| 原 Table 系 Join（更名） | NopMetaEntityJoin | 宿主列统一为 `metaEntityId`；**删除 left/right 旧表端点列与关系**，端点仅 leftEntityId/rightEntityId；UK (metaEntityId, alias)；`validateJoinSide` 删除旧表端点分支（端点校验简化为非空实体） |

### 2.3 FK 改造清单（9 非子表 to-one + Module 反向）

| 实体.关系 | 原 | 新 |
|---|---|---|
| NopMetaCatalog 旧表关系（已更名） | → NopMetaEntity | `metaEntityId` → NopMetaEntity（关系名 `metaEntity`） |
| NopMetaProfilingRule 旧表关系（已更名） | 同上 | 同上模式 |
| NopMetaProfilingResult 旧表关系（已更名） | 同上 | 同上模式 |
| NopMetaDataContract 旧表关系（已更名） | 同上 | 同上模式 |
| NopMetaReconciliationConfig 旧表关系（已更名） | 同上 | 同上模式 |
| NopMetaReconciliationResult 旧表关系（已更名） | 同上 | 同上模式 |
| NopMetaQualityScore 旧表关系（已更名） | 同上 | 同上模式 |
| NopMetaLineageEdge.sourceEntity / targetTable | → NopMetaEntity | `sourceEntityId` / `targetEntityId`（关系名 `sourceEntity` / `targetEntity`） |
| NopMetaModule.tables（to-many 反向） | → NopMetaEntity | `entities` → NopMetaEntity（经 NopMetaOrmModel 链或直接挂点，以 ORM 实现成本最小者为准——Entity 无 metaModuleId 直列，Module 反向关系经 OrmModel 中转声明，见 §3.4） |

### 2.4 字符串软引用迁移映射

| 引用点 | 原值 | 新值 |
|---|---|---|
| 搜索索引短名（NopMetaEntityBizModel.save/delete、NopMetaModuleBizModel 级联清理与建索引、NopMetaIndexBuilder 分派、NopMetadataHelper tagSet） | `"MetaEntity"` | `"NopMetaEntity"`（与既有 `"MetaEntity"` 索引面合并——原实体短名索引保留，Table 短名删除） |
| TagLabel.entityType（LineageTagPropagationProcessor/AutoClassificationProcessor） | `"NopMetaEntity"` | `"NopMetaEntity"` |
| DataProduct LINKABLE_ASSET_TYPES | {NopMetaEntity, NopMetaEntity, NopMetaEntityField, NopMetaEntityMeasure, NopMetaEntityDimension} | {NopMetaEntity, NopMetaEntityField, NopMetaEntityMeasure, NopMetaEntityDimension} |
| ModelChangedEvent entityType | `"NopMetaEntity"` | `"NopMetaEntity"` |
| QualityRule entityType（dict `meta/quality-entity-type`） | `table` | `entity`（label 同步改"实体"）；MetaQualityRuleExecutor 内部分派同步 |

### 2.5 API 改名映射（GraphQL 面）

| 原（INopMetaEntityBiz / NopMetaEntity BizModel） | 新（INopMetaEntityBiz / NopMetaEntity BizModel） |
|---|---|
| resolveEntityFields | resolveEntityFields |
| queryData | queryData（内部按 entityKind 三路分派） |
| queryAggregation | 保持不变 |
| queryJoinData | 保持不变 |
| profileEntity | profileEntity |
| createSqlView | createSqlView |
| previewSqlFields | 保持不变 |
| save / delete（CRUD + 事件 + 索引） | NopMetaEntityBizModel 既有 CRUD 之上合并事件与索引逻辑 |

DTO 改名：`ResolveEntityFieldsResultDTO→ResolveEntityFieldsResultDTO`、`ResolvedEntityFieldDTO→ResolvedEntityFieldDTO`、`QueryEntityDataResultDTO→QueryEntityDataResultDTO`、`CreateSqlViewResultDTO→CreateSqlViewResultDTO`；`AggregationResultDTO`、`QueryJoinDataResultDTO`、`PreviewSqlFieldsResultDTO`、`SqlViewFieldDTO`、`ProfileResultDTO`、`ProfilingColumnStatsDTO` 保持原名。**错误码常量全部保持不变**（`ERR_SQL_VIEW_TABLE_EXISTS` 等，契约稳定，仅必要文案微调）。

### 2.6 执行链改名与分派键

| 原类 | 新类 |
|---|---|
| 原 Table 系查询执行器 | MetaEntityQueryExecutor |
| MetaEntityFieldResolver | MetaEntityFieldResolver |
| MetaEntityReferenceResolver（tableref） | MetaEntityReferenceResolver |
| MetaEntityProfiler（profiling） | MetaEntityProfiler |
| NopMetaEntityQueryAction | NopMetaEntityQueryAction（放 `service/entity/`，由 NopMetaEntityBizModel 组合） |

分派键：`entityKind(entity/sql/external)` → `entityKind(PHYSICAL/SQL_VIEW/EXTERNAL)`；`isEntityTable()/isSqlTable()/isExternalTable()` → `isPhysical()/isSqlView()/isExternal()`。7 路聚合分派结构不变（Entity/External/Sql × 同库/跨库 JOIN 组合），仅分派键换列；unsupported entityKind 显式抛错（保持现有 fail-fast 语义）。

## 三、关键机制裁定

### 3.1 多 schema 共存（原 R4.2 能力保持）

原机制：`NopMetaEntity.dbSchema` + 4 列 UK (metaModuleId, tableName, isDelta, dbSchema) + `normalizeSchemaForMatch`。新机制：

- **匹配键 = (ormModelId, entityName)**；`dbSchema` 仅作元数据描述列，**不进 UK**；
- syncExternalTables 生成实体名规则：目标 schema 与数据源默认 schema 相同或为空 → `entityName = tableName`；否则 → `entityName = {schema}_{tableName}`（分隔符 `_`，schema 名中的非法 Java 标识符字符同样替换为 `_`，冲突时追加序号）；
- upsert 幂等键随之变为 (ormModelId, entityName)；`TestNopMetaEntityMultiSchemaUpsert`/`TestNopMetaEntityConcurrentNullSchemaUpsert` 迁移后按新规则断言（同 table 名异 schema → 两行实体，entityName 不同、dbSchema 不同）。

被拒绝的替代：dbSchema 进 UK——Java 实体名本身必须唯一，UK 含 schema 会让同模块下同名实体共存，破坏 ORM 注册表唯一性前提。

### 3.2 syncExternalTables 建模

系统模块 `nop/meta-external`（status=RELEASED）下 ensure 唯一 `NopMetaOrmModel` 行（作为外部实体的模型容器），外部实体行挂其下；`querySpace`、`dbSchema`（原 dbSchema）、`externalColumns`（原 buildSql JSON）随行写入。per-(module, table, schema) 锁与 REQUIRES_NEW 隔离语义保持（锁键的 table 维度改为 entityName）。

### 3.3 OrmModelImporter

`buildEntityTable` 双写删除；导入仅产出 NopMetaOrmModel/NopMetaEntity/Field/Relation/UniqueKey/Index/Domain/Dict。导入一致性测试（`TestNopMetaModuleImportConsistency`）改为断言"实体行存在、无表行"。

### 3.4 Module 反向关系

Entity 无 metaModuleId 直列（归属链 Entity→OrmModel→Module）。Module 的反向 to-many `entities` 经 `NopMetaOrmModel.metaModuleId` 中转声明（ORM 支持经中间实体反向），或以查询动作替代反向关系——取实现成本最小者，在 Phase 2 以编译与现有模块级联删除测试为准。

### 3.5 补充裁定（深检第 1 轮后，2026-09-17）

- **存量库升级路径**：无 nop_meta_table→nop_meta_entity 迁移脚本——dev 阶段（2.0.0-SNAPSHOT，无生产部署）按重建语义处理，与计划 Deferred 首条一致。
- **SQL 视图/外部实体的 manifest 归属**：createSqlView/sync 产出的实体行复用模块 OrmModel 容器（或惰性创建），进入 generateManifest 的 moduleEntities——裁定为预期行为（实体属模块内容，应随模块导出/导入）。
- **外部实体名碰撞**：{schema}_{tableName} 碰撞按追加序号 _2.._32 消解，耗尽抛 ERR_SYNC_ENTITY_NAME_EXHAUSTED；schema 非法标识符字符清洗为 _。

### 3.6 数据迁移裁定

开发阶段（2.0.0-SNAPSHOT，无生产部署）：开发库按新 DDL 重建；保留型数据仅 TagLabel 需要时以一次性 UPDATE（`entityType='NopMetaEntity'→'NopMetaEntity'`）处理，其余（目录/画像/对账/血缘/质量行）随重建重放。deploy/sql 三方言 DDL 同步重生成/修订（nop_meta_entity 系删除、实体新列补齐）。

## 四、拒绝了什么

| 替代方案 | 拒绝理由 |
|---|---|
| Measure/Dimension/Filter/Join 折叠为 NopMetaEntityField 上的注解列 | 语义有损（一个字段可派生多指标、维度含粒度/层级、过滤器是 TreeBean 结构），且 UK/显示名/格式等元数据无处安放；概念缩减的对象是"表"，不是 BI 语义 |
| Join 并入 NopMetaEntityRelation（模型声明关联） | Relation 是模型期声明的外键关联（随 ORM 导入），Join 是查询期 BI 关联配置（含 alias/joinType/跨源），生命周期与变更主体不同；合并会让导入覆盖用户 BI 配置 |
| dbSchema 进 Entity UK | 见 §3.1 |
| 保留 NopMetaEntity 仅删双写 | 违背裁定：双树的存在本身就是结构债（baseEntityId 无 FK 纽带、跨切面属性双挂） |
| 错误码随动作改名 | 错误码是已发布契约，改名无收益 |

## 五、测试迁移分组（Phase 3 执行清单，79 文件）

聚合执行 16（helper 改实体夹具）、字段解析 5、SQL/Entity 执行 4、外部同步 3、血缘 5、质量 7、画像 5、对账 4、Join 1、CRUD/守卫 10、搜索 3、标注 3、数据产品 3、契约 1、事件/传播 3、不变式 3（注意 `TestLimitNegativeValueInvariant` 等以字符串反射引用 `NopMetaEntityBizModel#queryData`，迁移为 `NopMetaEntityBizModel#queryData`）、tableref 1、连接 1（无 MetaEntity 语义，仅文件路径归属）。每个被删除/合并的测试类在计划分组清单登记处置。

## 六、与已有设计的关系

- `00-vision.md` / `11-enterprise-semantic-layer.md`：Non-goals 与语义层设计中"逻辑表"表述随本设计修订（Phase 4）。
- `docs-for-ai/03-modules/nop-metadata.md`：API 契约段改 `INopMetaEntityBiz` 新动作名（Phase 4）。
- feature/nop-ontology 分支的 nop-ontology 数据模型设计文档（本分支未合入）：其"对象类型 ↔ ORM 实体"绑定形态与本设计一致；其 roadmap WI19 的 MetaEntity 分支废弃由本计划落地。
