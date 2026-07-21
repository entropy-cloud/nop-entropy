# 维度04：ORM 模型与实体设计 — 第1轮（初审）

> 审计模块: nop-metadata
> 源模型: nop-metadata/model/nop-metadata.orm.xml

## 发现清单

### [维度04-01] 缺失 `meta/change-source` 字典定义导致运行时校验失败

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:2814`
- **证据片段**:
  ```xml
  <column code="CHANGE_SOURCE" displayName="变更来源" mandatory="true" name="changeSource"
          precision="30" propId="6" stdDataType="string" stdSqlType="VARCHAR"
          i18n-en:displayName="Change Source" ext:dict="meta/change-source"/>
  ```
  NopMetaModelChangedEvent 实体引用了 `ext:dict="meta/change-source"`，但 `<dicts>` 段中未定义此字典。
- **严重程度**: P1
- **现状**: 字段标记为 `mandatory="true"` 且引用了不存在的字典。模型加载时可能校验失败。
- **风险**: (1) 启动期可能校验失败；(2) 若惰性加载，运行时字典下拉不可用；(3) 该实体是审计链路入口，影响全链路。
- **建议**: 在 `<dicts>` 段补充 `meta/change-source` 字典（可选值：MANUAL, IMPORT, SYNC, API, UI_EDIT 等），或将 `ext:dict` 移除。
- **信心水平**: 确定
- **误报排除**: 源模型 XML 中引用了未定义的字典名称，同一文件定义了 30+ 个 `meta/*` 字典，均无 `meta/change-source`。
- **复核状态**: 未复核

### [维度04-02] NopMetaGlossary 到 NopMetaGlossaryTerm 缺失 cascadeDelete

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:2917-2923`
- **证据片段**:
  ```xml
  <!-- Glossary -> GlossaryTerm (NO cascadeDelete) -->
  <to-many displayName="术语集" name="childTerms"
           refEntityName="...NopMetaGlossaryTerm" refPropName="glossary"
           tagSet="pub,ref-pub">
  ```
  对比 NopMetaClassification 到 NopMetaTag（line 3088-3090）设置了 `cascadeDelete="true"`。
- **严重程度**: P2
- **现状**: 删除 Glossary 时，下属的 GlossaryTerm 不会级联删除。GlossaryTerm 的 `glossaryId` 为 `mandatory="true"`，若有 FK 约束则删除被拒绝。
- **风险**: 不一致的设计；若批量删除词汇表，残存术语行占用存储。
- **建议**: 追加 `cascadeDelete="true"`，与 Classification->Tag 保持一致。若有意保留，需注释说明理由。
- **信心水平**: 确定
- **误报排除**: 两个同层级的容器-子项关系，语义一致但级联行为不一致。
- **复核状态**: 未复核

### [维度04-03] NopMetaDataSource 唯一键命名风格不统一

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:399-402`
- **证据片段**:
  ```xml
  <unique-key name="UK_NOP_META_DS_QUERY_SPACE" columns="querySpace"/>
  <unique-key name="uk_meta_datasource_name" columns="name"/>
  ```
  同一实体的两个唯一键，一个使用 `UK_` 大写，另一个使用 `uk_` 小写。全文件 35 个唯一键中仅此一处使用小写。
- **严重程度**: P2
- **现状**: 同一张表的两约束命名风格分裂。
- **风险**: 工具链依赖约束名做版本迁移时，小写命名降低信任度。
- **建议**: 统一为 `UK_NOP_META_DS_NAME`。
- **信心水平**: 确定
- **误报排除**: 同一实体、同一文件内的命名约定不一致。
- **复核状态**: 未复核

### [维度04-04] NopMetaTagLabel 缺少唯一约束

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:3192-3287`
- **证据片段**:
  ```xml
  <!-- NopMetaTagLabel 定义，unique-keys 段缺失 -->
  ```
- **严重程度**: P2
- **现状**: 统一语义桥接表（Tag/GlossaryTerm 标注到资产）无任何唯一约束。
- **风险**: 同一标签可被重复标注到同一资产上，产生冗余行。
- **建议**: 添加 `UK_NOP_META_TAG_LABEL_ENTITY_TAG` 和 `UK_NOP_META_TAG_LABEL_ENTITY_TERM`。
- **信心水平**: 确定
- **误报排除**: 同类桥接表在其他 Nop 模块中都有唯一约束。
- **复核状态**: 未复核

### [维度04-05] NopMetaTable 的 baseEntityId 列缺乏外键关系定义

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:1260-1261`
- **证据片段**:
  ```xml
  <column code="BASE_ENTITY_ID" displayName="主要实体ID" name="baseEntityId"
          precision="32" stdDataType="string" stdSqlType="VARCHAR"/>
  ```
  精度（32）和命名强烈暗示引用 NopMetaEntity，但关系段无 `to-one` 关联。
- **严重程度**: P3
- **现状**: 存储的纯字符串字段，与目标实体间无 ORM 关系。
- **建议**: 添加 `to-one` 关系，或在注释中明确说明用途。
- **信心水平**: 很可能
- **误报排除**: 手写源模型中的列，命名和类型暗示 FK 但缺少关系定义。
- **复核状态**: 未复核

### [维度04-06] NopMetaEntityRelation 的 CASCADE_DELETE 与 AUTO_CASCADE_DELETE 双字段冗余

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:820-824`
- **证据片段**:
  ```xml
  <column code="CASCADE_DELETE" domain="boolFlag" name="cascadeDelete"/>
  <column code="AUTO_CASCADE_DELETE" domain="boolFlag" name="autoCascadeDelete"/>
  ```
- **严重程度**: P3
- **现状**: 两个 boolFlag 字段语义高度重叠（手动 vs 自动级联删除）。
- **风险**: 应用层需要合并逻辑，UI 展示两个复选框，增加认知负荷。
- **建议**: 删除 `AUTO_CASCADE_DELETE`，将自动推断逻辑移到应用层。
- **信心水平**: 确定
- **误报排除**: 字段设计重复导致数据冗余和业务逻辑二义性。
- **复核状态**: 未复核

### [维度04-07] NopMetaTable 的 metaSchema 列缺少对应 dict 或 domain

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:1283-1284`
- **证据片段**:
  ```xml
  <column code="META_SCHEMA" name="metaSchema" precision="100" stdDataType="string"/>
  ```
  参与组合索引 `IX_NOP_META_TABLE_MODULE_SCHEMA_NAME`，但无 dict 或 domain。
- **严重程度**: P3
- **现状**: 裸 VARCHAR 字段，无 domain 约束或 dict 定义，作为索引第二部分。
- **建议**: 若 schema 名称枚举值，添加 dict；否则评估其作为索引列的必要性。
- **信心水平**: 很可能
- **误报排除**: 参与索引设计的字段但缺乏约束保护。
- **复核状态**: 未复核

## 基线合规性总结

| 检查项 | 状态 |
|--------|------|
| 主键设计（VARCHAR(32) + tagSet="seq"） | 全部合规（39/39 实体） |
| 审计字段 | 全部合规 |
| displayName 本地化（i18n-en:displayName） | 全部合规 |
| 表名 snake_case + `nop_` 前缀 | 全部合规 |
| 域（domain）使用 | 基本合规 |
| 字典定义完整性 | 1 处缺失（meta/change-source） |
| 关系定义完整性 | 1 处缺失（baseEntityId） |
| 关系级联一致性 | 1 处不一致（Glossary->Term） |
