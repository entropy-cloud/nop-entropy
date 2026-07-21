# 维度 04：ORM 模型与实体设计

## 第 1 轮（初审）

### [维度04-01] NopMetaTable 上重叠的唯一键

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:1287-1291`
- **证据代码片段**:
  ```xml
  <unique-key name="uk_meta_table_module_schema" columns="metaModuleId,tableName,schema"
              displayName="表名+模块+schema唯一约束"/>
  <unique-key name="UK_NOP_META_TABLE_MODULE_NAME" columns="metaModuleId,tableName"
              i18n-en:displayName="Unique Table Name Per Module"/>
  ```
- **严重程度**: P2
- **现状**: `NopMetaTable` 声明了两个唯一键，第二个 (`UK_NOP_META_TABLE_MODULE_NAME`) 的列集是第一个 (`uk_meta_table_module_schema`) 的严格前缀子集，导致约束重叠。命名规范也不一致（小写蛇形 vs 大写下划线）。
- **风险**: 重叠唯一键会产生额外的索引维护开销。在批量插入场景中增加不必要的索引维护成本。
- **建议**: 审查业务需求，只保留一个 UK。统一 UK 命名风格为 `UK_NOP_META_TABLE_*`。
- **信心水平**: 高
- **误报排除**: 重叠唯一键会产生可量化的运行时开销，不是主观判断。
- **复核状态**: 未复核

### [维度04-02] NopMetaTagLabel 缺少防止重复标注的唯一约束

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:3188-3283`
- **证据代码片段**:
  ```xml
  <entity className="io.nop.metadata.dao.entity.NopMetaTagLabel" ...>
      <columns>
          <column code="TAG_ID" .../>
          <column code="GLOSSARY_TERM_ID" .../>
          <column code="ENTITY_TYPE" mandatory="true" .../>
          <column code="ENTITY_ID" mandatory="true" .../>
      </columns>
      <!-- unique-keys is absent -->
  </entity>
  ```
- **严重程度**: P2
- **现状**: `NopMetaTagLabel` 使用多态模式（`entityType` + `entityId`）将标签和词汇表术语关联到任意资产，但没有任何唯一键防止重复标注。同一标签对同一资产可被标注多次。
- **风险**: 数据语义完整性依赖应用程序层面的去重逻辑，但该逻辑未在 ORM 模型中强制保障。导入场景、并发竞态条件或 UI 错误可能导致重复标注。
- **建议**: 新增两个唯一键：(a) `(entityType, entityId, tagId)` 和 (b) `(entityType, entityId, glossaryTermId)`，允许 `tagId` 和 `glossaryTermId` 为 NULL。
- **信心水平**: 高
- **误报排除**: 真实约束缺失，可能在生产环境中导致数据损坏。
- **复核状态**: 未复核

### [维度04-03] 未使用的字典定义

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:104-125`
- **证据代码片段**:
  ```xml
  <dict label="检查点动作类型" name="meta/checkpoint-action-type" valueType="string">
      <option code="STORE" label="存储结果" value="store"/>
      <option code="WEBHOOK" label="Webhook投递" value="webhook"/>
      <option code="NOTIFY" label="消息通知" value="notify"/>
  </dict>
  <dict label="质量评分趋势方向" name="meta/quality-trend-direction" valueType="string">
      <option code="IMPROVING" label="改善" value="improving"/>
      <option code="STABLE" label="稳定" value="stable"/>
      <option code="DEGRADING" label="恶化" value="degrading"/>
  </dict>
  ```
- **严重程度**: P3
- **现状**: 字典 `meta/checkpoint-action-type`、`meta/quality-trend-direction` 和 `meta/reconciliation-status` 在 `<dicts>` 部分定义，但没有任何实体列通过 `ext:dict` 引用它们。
- **风险**: 死代码增加维护成本，误导新开发者，且字典会注册到全局字典缓存影响启动时间。
- **建议**: 删除未使用的字典定义，或添加注释说明用于程序化字典查找。
- **信心水平**: 高
- **误报排除**: 字典定义带维护成本，会注册全局缓存。
- **复核状态**: 未复核

### [维度04-04] 未使用的域定义 `userName`

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:197`
- **证据代码片段**:
  ```xml
  <domains>
      <domain name="userName" precision="50" stdSqlType="VARCHAR"/>
  </domains>
  ```
- **严重程度**: P3
- **现状**: `userName` 域在 `<domains>` 中定义但无任何实体列通过 `domain="userName"` 引用。所有用户标识符列都使用内联定义而非通过域抽象。
- **风险**: 该域的存在会误导开发人员认为它被使用。如果未来需要更改用户名的精度，需要逐个修改列而非修改域定义。
- **建议**: 删除未使用的 `userName` 域，或将现有内联定义的用户名列重构为使用该域。
- **信心水平**: 高
- **误报排除**: 域是抽象机制，声明但未使用的域产生维护陷阱。
- **复核状态**: 未复核

### [维度04-05] NopMetaDataSource 在常见查询列上缺少索引

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:357-402`
- **证据代码片段**:
  ```xml
  <entity className="io.nop.metadata.dao.entity.NopMetaDataSource" ...>
      <columns>
          <column code="NAME" mandatory="true" .../>
          <column code="DATASOURCE_TYPE" mandatory="true" .../>
          <column code="STATUS" mandatory="true" .../>
      </columns>
      <!-- No <indexes> block -->
  </entity>
  ```
- **严重程度**: P3
- **现状**: `NopMetaDataSource` 仅有 `querySpace` 上的唯一键，`name`、`datasourceType` 和 `status` 列上无任何索引。
- **风险**: 按 `name` 查询（数据源管理中最常见的查找模式）和执行按类型/状态的筛选将执行全表扫描。
- **建议**: 增加 `IX_NOP_META_DATA_SOURCE_NAME` 在 `name` 上，和 `IX_NOP_META_DATA_SOURCE_TYPE_STATUS` 在 `(datasourceType, status)` 上。
- **信心水平**: 中
- **误报排除**: `name` 是必填业务标识符，`status` 和 `datasourceType` 是筛选器列，属于高频查询条件。
- **复核状态**: 未复核

### [维度04-06] NopMetaDataProduct 上的注释与实体不匹配

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:3358-3362`
- **证据代码片段**:
  ```xml
  <!-- =====================================================
       NopMetaReportDefinition:
       报表定义（预留）
       ===================================================== -->
  <entity className="io.nop.metadata.dao.entity.NopMetaDataProduct" ...>
  ```
- **严重程度**: P3
- **现状**: 位于 `NopMetaDataProduct` 实体之前的注释块引用了不同的实体名称（`NopMetaReportDefinition`），是复制粘贴遗留。
- **风险**: 误导开发人员以为存在 `NopMetaReportDefinition` 实体。
- **建议**: 将注释修正为 `NopMetaDataProduct: 数据产品`。
- **信心水平**: 高
- **误报排除**: 客观错误。
- **复核状态**: 未复核

### [维度04-07] NopMetaDataContract 上的双状态字段模式

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:2412-2494`
- **证据代码片段**:
  ```xml
  <column code="STATUS" mandatory="true" name="status"
          stdDataType="string" ext:dict="meta/contract-status"/>
  <column code="APPROVE_STATUS" name="approveStatus" precision="20"
          stdDataType="string" ext:dict="wf/approve-status"/>
  ```
- **严重程度**: P3
- **现状**: `NopMetaDataContract` 包含两个独立状态字段：`status`（DRAFT/ACTIVE/DEPRECATED/RETIRED）和 `approveStatus`（审批状态），引入了状态一致性问题。
- **风险**: 契约可能处于不一致的状态（如 `approveStatus=APPROVED` 但 `status=DRAFT`）。缺少记录状态机的文档。
- **建议**: 将批准转化为状态转换（DRAFT→PENDING_APPROVAL→ACTIVE→DEPRECATED），删除独立 `approveStatus` 字段；或添加文档记录允许的状态组合。
- **信心水平**: 中
- **误报排除**: 缺乏记录的状态机是潜在维护问题。
- **复核状态**: 未复核

## 深挖第 2 轮追加

### [维度04-08] NopMetaGlossaryTerm 上重叠的唯一键

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:2990-2994`
- **证据代码片段**:
  ```xml
  <unique-key name="UK_NOP_META_GLOSSARY_TERM_FQN" columns="fullyQualifiedName" .../>
  <unique-key name="UK_NOP_META_GLOSSARY_TERM_G_FQN" columns="glossaryId,fullyQualifiedName" .../>
  ```
- **严重程度**: P2
- **现状**: 与 [维度04-01] 同模式。如果 `fullyQualifiedName` 全局唯一（第一个 UK 强制保证），复合 UK 永远无法被违反。反之如果 FQN 仅在词汇表内唯一，则全局 UK 过于严格。
- **风险**: 冗余索引维护开销。
- **建议**: 根据业务语义保留一个 UK，移除另一个。
- **信心水平**: 高
- **误报排除**: 超集/子集关系是确定的。
- **复核状态**: 未复核

### [维度04-09] NopMetaTag 上重叠的唯一键

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:3145-3149`
- **证据代码片段**:
  ```xml
  <unique-key name="UK_NOP_META_TAG_FQN" columns="fullyQualifiedName" .../>
  <unique-key name="UK_NOP_META_TAG_CLS_FQN" columns="classificationId,fullyQualifiedName" .../>
  ```
- **严重程度**: P2
- **现状**: 与 [维度04-08] 完全相同模式，超集/子集 UK 重叠。
- **建议**: 根据业务语义保留一个 UK。
- **信心水平**: 高
- **复核状态**: 未复核

### [维度04-10] NopMetaBusinessDomain 在自引用外键 parentDomainId 上缺少索引

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:3294-3356`
- **证据代码片段**:
  ```xml
  <entity name="NopMetaBusinessDomain" ...>
      <column name="parentDomainId" .../>
      <!-- No <indexes> block -->
  </entity>
  ```
- **严重程度**: P3
- **现状**: `NopMetaBusinessDomain` 是树形结构实体，包含自引用外键 `parentDomainId`，但缺少索引。对比之下 `NopMetaGlossaryTerm.parentTermId` 和 `NopMetaTag.parentTagId` 都有索引。
- **风险**: 按父域查询子域（`WHERE parentDomainId = ?`）将全表扫描。
- **建议**: 添加 `IX_NOP_META_BUSINESS_DOMAIN_PARENT` 索引在 `parentDomainId` 上。
- **信心水平**: 高
- **误报排除**: 同类树形实体已建索引，此实体为遗漏。
- **复核状态**: 未复核

### [维度04-11] NopMetaLineageEdge 缺少防止重复边数据的唯一约束

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:1831-1916`
- **证据代码片段**:
  ```xml
  <entity name="NopMetaLineageEdge" ...>
      <column name="sourceTableId" .../>
      <column name="targetTableId" .../>
      <column name="pipelineId" .../>
      <!-- No <unique-keys> block -->
  </entity>
  ```
- **严重程度**: P3
- **现状**: `NopMetaLineageEdge` 定义了三个外键索引，但没有任何 `<unique-keys>`。相同数据流向（相同源表+目标表+管道+源列+目标列）可被重复插入。
- **风险**: 血缘图谱出现冗余边，影响下游分析准确性。
- **建议**: 添加 UK `(sourceTableId, targetTableId, pipelineId, sourceColumn, targetColumn)` 防止重复边。
- **信心水平**: 高
- **误报排除**: 血缘边实体需要业务唯一约束。
- **复核状态**: 未复核

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:2412-2494`
- **证据代码片段**:
  ```xml
  <column code="STATUS" mandatory="true" name="status"
          stdDataType="string" ext:dict="meta/contract-status"/>
  <column code="APPROVE_STATUS" name="approveStatus" precision="20"
          stdDataType="string" ext:dict="wf/approve-status"/>
  ```
- **严重程度**: P3
- **现状**: `NopMetaDataContract` 包含两个独立状态字段：`status`（DRAFT/ACTIVE/DEPRECATED/RETIRED）和 `approveStatus`（审批状态），引入了状态一致性问题。
- **风险**: 契约可能处于不一致的状态（如 `approveStatus=APPROVED` 但 `status=DRAFT`）。缺少记录状态机的文档。
- **建议**: 将批准转化为状态转换（DRAFT→PENDING_APPROVAL→ACTIVE→DEPRECATED），删除独立 `approveStatus` 字段；或添加文档记录允许的状态组合。
- **信心水平**: 中
- **误报排除**: 缺乏记录的状态机是潜在维护问题。
- **复核状态**: 未复核
