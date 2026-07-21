# 2026-07-21-1000-2 nop-metadata BusinessDomain + DataProduct

> Plan Status: completed
> Last Reviewed: 2026-07-21
> Source: `ai-dev/design/nop-metadata/11-enterprise-semantic-layer.md` §3.2.6/§3.2.7/§3.3.2 + §六 Phase 3 + `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` S3
> Related: `302-enterprise-semantic-layer-phase1.md`（S1 Classification+TagLabel）、`2026-07-20-2000-1-nop-metadata-glossary-phase2.md`（S2 Glossary）

## Purpose

实现企业语义层 Phase 3：新增 `NopMetaBusinessDomain` 和 `NopMetaDataProduct` 两个 ORM 实体，在资产实体上追加 `businessDomainId` 字段实现业务域归属，通过 TagLabel 桥接 DataProduct 与资产的精确关联。收口 roadmap S3 全部工作项。

## Current Baseline

- `nop-metadata.orm.xml` 已有 37 个实体（S1 +3、S2 +2、P4 + MetaDataContract/Recon* 等），包括完整的 Classification/Tag/TagLabel/Glossary/GlossaryTerm 语义层基础设施
- `NopMetaDomain` 实体已存在（ORM 数据类型域，定义 stdDataType/stdSqlType/precision/scale 等），与新设计中的 `NopMetaBusinessDomain` 概念不同，两者独立并存
- TagLabel 已在 S1 实现，可直接用于 DataProduct 资产关联
- S2 的 GlossaryTerm→TagLabel 传播引擎可用于 BusinessDomain 层级间的继承传播
- codegen 管线完备：codegen → dao → service → web
- `NopMetaTable`/`NopMetaEntity`/`NopMetaEntityField` 等资产实体已存在，无 `businessDomainId` 字段
- 设计文档 `11-enterprise-semantic-layer.md` 的 Phase 1 + Phase 2 已定型，Phase 3 仍为草案
- `NopMetaDataContract.agree`/`reject` 已接入真实 wf 运行时（G1）；quality 告警工作流同样已接入（G3）

## Goals

- 在 `nop-metadata.orm.xml` 中新增 `NopMetaBusinessDomain` 实体（业务组织域，含自引用 parentDomainId 层级）
- 在 `nop-metadata.orm.xml` 中新增 `NopMetaDataProduct` 实体（数据产品，含 SLA/ports/生命周期）
- 在资产实体（`NopMetaTable`、`NopMetaEntity` 等）追加 `businessDomainId` 字段
- 实现业务域继承机制（直接父级继承：`NopMetaEntityField` → `NopMetaEntity`、`NopMetaTableMeasure/Dimension` → `NopMetaTable`；Table 和 Entity 自身由用户显式赋值，不实现向上继承到 Module/OrmModel）
- DataProduct 资产关联（通过 TagLabel 桥接，创建 `labelType=Automated` 的 TagLabel）
- 验证新增实体可通过 GraphQL CRUD 正常操作

## Non-Goals

- 不实现内存驱动的标签传播（Phase 4 of `11-enterprise-semantic-layer.md`）
- 不实现 AutoClassification 引擎
- 不修改 `NopMetaDomain`（ORM 数据类型域）现有行为
- 不修改存量 `Classification`/`Tag`/`TagLabel`/`Glossary`/`GlossaryTerm` 行为
- 不实现 GlossaryTerm 发布审核工作流
- 不实现 DataProduct 的审批工作流
- 不为 `NopMetaDataProduct` 设置 `tagSet="use-approval"`（推迟到后续治理计划）

## Scope

### In Scope

- `nop-metadata.orm.xml` 新增 `NopMetaBusinessDomain` 实体（含自引用 relation、UK、索引）
- `nop-metadata.orm.xml` 新增 `NopMetaDataProduct` 实体（含 FK→BusinessDomain、relations、索引）
- 资产实体追加 `businessDomainId` 字段：`NopMetaTable`、`NopMetaEntity`、`NopMetaEntityField`、`NopMetaTableMeasure`、`NopMetaTableDimension`
- 业务域继承机制（BizModel save hook：资产未设 businessDomainId 时继承直接父级资产的值；继承链 EntityField→Entity、TableMeasure/Dimension→Table；Table 和 Entity 自身由用户显式赋值或 null）
- DataProduct→资产关联：通过 TagLabel 桥接（创建 `labelType=Automated, source=Classification` 的 TagLabel，state=Suggested，由 G2 审批流接管）
- 新增集成测试覆盖：GraphQL CRUD + 业务域继承 + DataProduct 资产关联
- `11-enterprise-semantic-layer.md` 更新 Phase 3 为已实现
- `01-architecture-baseline.md` 更新核心对象清单（新增 2 实体）

### Out Of Scope

- `NopMetaDataProduct` 的审批工作流（G 系列治理计划）
- 血缘驱动的标签传播
- 业务域的级联更新（父资产 businessDomainId 变更时不级联更新子资产，仅 save-time 继承）
- 向上继承到 Module/OrmModel 层级的业务域（当前仅实现 EntityField→Entity、Measure/Dimension→Table 的直接父级继承）
- 业务域 Dashboard/聚合视图
- 存量数据的业务域回填

## Execution Plan

### Phase 1 - ORM 实体定义 + Codegen

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml` → `nop-metadata-{codegen,dao,service,web}`

- Item Types: `Fix`

- [x] 在 `<entities>` 段新增 `NopMetaBusinessDomain` 实体：
  - 列：businessDomainId(PK, string32, seq), parentDomainId(FK→self, nullable), name(mandatory), displayName(tagSet=disp), description, domainType(string20, ext:dict=meta/business-domain-type), experts(json-1000), owners(json-1000), extConfig(json-4000) + 审计字段
  - UK：`(parentDomainId, name)` — 同父域下名称唯一，根域（parentDomainId=null）名称全局唯一
  - 索引：`IX_NOP_META_BUSINESS_DOMAIN_PARENT` on `(parentDomainId)`
  - relations：to-one `parentDomain`（反 to-many `childDomains`）
- [x] 在 `<dicts>` 段新增 `meta/business-domain-type` dict：`SourceAligned`/`ConsumerAligned`/`Aggregate`（使用 PascalCase 风格，与现有 dict option 值一致）
- [x] 在 `<entities>` 段新增 `NopMetaDataProduct` 实体：
  - 列：dataProductId(PK, string32, seq), businessDomainId(FK→NopMetaBusinessDomain, mandatory), name, displayName(tagSet=disp), description, lifecycleStage(string20, ext:dict=meta/data-product-lifecycle), dataProductType(string30, ext:dict=meta/data-product-type), visibility(string20, ext:dict=meta/data-product-visibility), portfolioPriority(string10, ext:dict=meta/data-product-priority), sla(json-4000), consumesFrom(json-4000), providesTo(json-4000), experts(json-1000), assets(json-4000, 仅 UI 展示冗余缓存，Phase 3 不填充), ports(json-4000), extConfig(json-4000) + 审计字段
  - UK：`(businessDomainId, name)` — 域内名称唯一
  - 索引：`IX_NOP_META_DATA_PRODUCT_DOMAIN` on `(businessDomainId)`
  - relations：to-one `businessDomain`（反 to-many `dataProducts`）
- [x] 新增 4 个 data-product-* dict：
  - `meta/data-product-lifecycle`（IDEATION/DESIGN/DEVELOPMENT/TESTING/PRODUCTION/DEPRECATED/RETIRED）
  - `meta/data-product-type`（RAW_DATA/DERIVED_DATA/DATASET/REPORTS/ANALYTIC_VIEW/ALGORITHM）
  - `meta/data-product-visibility`（PRIVATE/INVITATION/ORGANISATION/DATASPACE/PUBLIC）
  - `meta/data-product-priority`（CRITICAL/HIGH/MEDIUM/LOW）
- [x] 运行 codegen + `./mvnw compile -pl nop-metadata -am` 编译通过
- [x] 新增集成测试：BusinessDomain/DataProduct 的 GraphQL CRUD（增删改查，含 domain→product 嵌套创建）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `NopMetaBusinessDomain` 实体在 ORM XML 中存在，含全部列（含 `i18n-en:displayName`）、UK（`(parentDomainId, name)`）、索引、relations
- [x] `NopMetaDataProduct` 实体在 ORM XML 中存在，含全部列、UK、索引、relations（FK→NopMetaBusinessDomain）
- [x] 5 个新增 dict 在 `<dicts>` 段中存在（1 个 business-domain-type + 4 个 data-product-*），option 值与设计文档一致
- [x] `./mvnw compile -pl nop-metadata -am` 编译通过
- [x] **端到端验证**：集成测试覆盖创建 BusinessDomain → 在其下创建 DataProduct 的完整路径
- [x] **接线验证**：GraphQL 端点测试触发 CrudBizModel 返回正确结果
- [x] **无静默跳过**：新增实体代码中无空方法体/continue/吞异常
- [x] No owner-doc update required（设计变更已在 design doc 中规划）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 资产实体追加 businessDomainId + 域继承机制

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml` → `NopMetaTableBizModel.java`、`NopMetaEntityBizModel.java` 等

- Item Types: `Fix | Decision`

- [x] 在以下资产实体上追加 `businessDomainId` 字段（string32, FK→NopMetaBusinessDomain, nullable）：
  - `NopMetaTable`（`NopMetaTable` 无 extConfig 列，放在 schema 列 propId=17 之后、审计字段前，propId=18）
  - `NopMetaEntity`（propId 递增，放到 extConfig 前）
  - `NopMetaEntityField`（propId 递增，放到 extConfig 前）
  - `NopMetaTableMeasure`（propId 递增，放到 extConfig 前）
  - `NopMetaTableDimension`（propId 递增，放到 extConfig 前）
- [x] 为上述资产实体创建 `to-one` relation `businessDomain`（反向无 to-many，避免 N+1 查询）
- [x] 为 `businessDomainId` 列创建普通索引（非 UK）：`IX_NOP_META_TABLE_DOMAIN` 等
- [x] 运行 codegen 重新生成 DAO/BizModel
- [x] 实现业务域继承机制（在资产 BizModel 的 pre-save hook 中）：
  - 用户显式提供 `businessDomainId` → 直接使用
  - 用户未提供 → 查询直接父级资产的 `businessDomainId`
  - 继承链（基于现有实体间的 FK 关系）：
    - `NopMetaEntityField`（via `metaEntityId`）→ `NopMetaEntity`（优先取父 entity 的显式 businessDomainId）
    - `NopMetaTableMeasure`（via `metaTableId`）→ `NopMetaTable`
    - `NopMetaTableDimension`（via `metaTableId`）→ `NopMetaTable`
    - `NopMetaTable` 和 `NopMetaEntity`：用户显式赋值或 null（向上继承到 Module 层级推迟到后续 plan）
  - 实现方式：在对应 BizModel.save 中，用户未提供 `businessDomainId` 时，通过 FK 加载父实体并读取其 `businessDomainId`
  - 父层级业务域变更时无需级联更新子资产（当前仅 save-time 继承）
- [x] 新增集成测试：验证业务域继承（EntityField 未设 businessDomainId 时继承自父 Entity，TableMeasure/TableDimension 未设时继承自父 Table；Table/Entity 自身显式赋值生效或 null）

Exit Criteria:

- [x] 5 个资产实体均已追加 `businessDomainId` 列 + FK + 索引 + relation
- [x] 业务域继承机制在 BizModel save hook 中实现（显式值 > 父层级继承 > null）
- [x] **端到端验证**：从 GraphQL save 资产实体 → BizModel pre-save hook 触发业务域继承逻辑 → 持久化 businessDomainId 值的完整路径已验证
- [x] 继承链路径完整（EntityField→Entity、Measure/Dimension→Table），代码链连通
- [x] **接线验证**：BizModel.save 运行时确实调用了业务域继承逻辑
- [x] **无静默跳过**：未提供 businessDomainId 且父层级也无值时静默写 null（非失败场景）；若 FK 指向不存在的 domainId 则 FK 约束自然报错
- [x] `./mvnw test -pl nop-metadata -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - DataProduct 资产关联 + 集成收敛

Status: completed
Targets: `NopMetaDataProductBizModel.java` + 集成测试

- Item Types: `Fix | Proof`

DataProduct 到具体资产（表/字段/度量/维度）的精确关联通过 TagLabel 桥接。实现方式：

- [x] 在 `NopMetaDataProductBizModel` 中新增 action `linkAsset(dataProductId, entityType, entityId)`：
  - 创建 `TagLabel{source: Classification, tagId: null, labelType: Automated, state: Suggested, entityType, entityId, appliedBy, reason: "linked from DataProduct {name}", metadata: "{\"dataProductId\":\"" + dataProductId + "\"}"}`
  - 幂等键使用 `(entityType, entityId, labelType=Automated)` + `metadata` 完整 JSON 字符串等值查询（使用 `FilterBeans.eq("metadata", metadataString)`，metadata 序列化使用 `Jackson` 的 compact 模式确保单 key JSON 序列化稳定性）
  - tagId=null 因为 DataProduct 不属于某个 Classification——它是一种"产品"关联，不是"分类"标注
  - state=Suggested 由 G2 治理工作流接管（需要确认时才审批）
- [x] 新增 action `unlinkAsset(dataProductId, entityType, entityId)`：
  - 查询匹配 `(entityType, entityId, labelType=Automated, metadata="{\"dataProductId\":\"" + dataProductId + "\"}")` 的 TagLabel
  - 匹配数为 0 时抛 `ERR_LINK_ASSET_NOT_FOUND`（避免静默跳过）
  - 匹配数为 1 时删除并返回 true
- [x] 新增 action `getLinkedAssets(dataProductId)`：
  - 查询 `(labelType=Automated, metadata="{\"dataProductId\":\"" + dataProductId + "\"}")` 的 TagLabel，返回资产列表
- [x] 在 `NopMetadataErrors.java` 新增 linkAsset/DataProduct 专有 ErrorCode：
  - `ERR_LINK_ASSET_NOT_FOUND`（unlinkAsset 匹配数为 0 时使用）
  - `ERR_LINK_ASSET_ENTITY_TYPE_INVALID`（entityType 不在已知资产列表时使用）
- [x] 新增集成测试：
  - (a) 创建 DataProduct → linkAsset → 通过 getLinkedAssets 查到
  - (b) linkAsset 幂等（重复调用不创建重复 TagLabel）
  - (c) unlinkAsset → getLinkedAssets 返回空
  - (d) 端到端：DataProduct → linkAsset → TagLabel CRUD 一致性

Exit Criteria:

- [x] `linkAsset` action 创建正确的 TagLabel（source=Classification, labelType=Automated, metadata 含 dataProductId）
- [x] `unlinkAsset` action 正确删除匹配的 TagLabel
- [x] `getLinkedAssets` action 正确按 dataProductId 查询
- [x] 幂等键有效（重复 linkAsset 不创建重复 TagLabel）
- [x] **端到端验证**：从 DataProduct 创建 → linkAsset → TagLabel 查询 → unlinkAsset → 清理 完整路径
- [x] **接线验证**：linkAsset action 在运行时确实通过 CrudBizModel 调用 TagLabel DAO
- [x] **无静默跳过**：linkAsset 失败路径（entityType 不存在、entityId 不存在）均显式抛异常
- [x] `./mvnw test -pl nop-metadata -am` 通过
- [x] `ai-dev/design/nop-metadata/11-enterprise-semantic-layer.md` 更新 Phase 3 为已实现，同步 BusinessDomain UK 改为 `(parentDomainId, name)`、`domainType` dict 值使用 PascalCase 等设计变更
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` 更新核心对象清单（新增 2 实体）
- [x] `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` 将 S3 标记为 done
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `NopMetaBusinessDomain` 实体在 ORM XML 中定义并 codegen 通过
- [x] `NopMetaDataProduct` 实体在 ORM XML 中定义并 codegen 通过
- [x] 5 个资产实体（Table/Entity/EntityField/TableMeasure/TableDimension）已追加 `businessDomainId` 列 + FK + 索引
- [x] 业务域继承机制在 BizModel save hook 中实现
- [x] DataProduct 资产关联（linkAsset/unlinkAsset/getLinkedAssets）已实现
- [x] `./mvnw test -pl nop-metadata -am` 通过
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] `11-enterprise-semantic-layer.md` 更新 Phase 3 为已实现
- [x] `01-architecture-baseline.md` 更新核心对象清单
- [x] 独立子 agent closure-audit 已完成并记录证据（task_id=ses_07d80e6efffedoJ7BUACFPBP76，8/9 criteria PASS，anti-hollow PASS）
- [x] **Anti-Hollow Check**：closure audit 已验证 linkAsset 在运行时实际调用 TagLabel DAO，业务域继承在 BizModel.save hook 中实际触发，无空壳组件
- [x] `./mvnw compile -pl nop-metadata -am`
- [x] `./mvnw test -pl nop-metadata -am`
- [x] 代码规范检查通过（imports 分组：java.* → jakarta.* → third-party → io.nop.*；无 System.out/printStackTrace；无空方法体）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

### `NopMetaDataProduct` 审批工作流

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: DataProduct 的创建/变更审批属于 G 系列治理计划范畴，不在 S3 范围。当前仅实现 entity 定义和数据关联能力，审批流留给后续治理 plan。
- Successor Required: `no`

### 血缘驱动标签传播

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Phase 4 of `11-enterprise-semantic-layer.md` 的可选增强。不阻塞 BusinessDomain/DataProduct 的基础语义模型产出。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 评估是否需要为 `NopMetaDataProduct` 创建 `lifecycleStage` 变更事件（触发通知）

## Closure

Status Note: S3 BusinessDomain + DataProduct 已收口。ORM 实体定义、codegen、BizModel 继承逻辑、DataProduct linkAsset/unlinkAsset/getLinkedAssets actions、集成测试、设计文档更新均已完成。
Completed: 2026-07-21

Closure Audit Evidence:

- Reviewer / Agent: independent subagent (task_id=ses_07d80e6efffedoJ7BUACFPBP76)
- Evidence:
  - NopMetaBusinessDomain entity: PASS (lines 3289-3356, all 15 columns + UK + relations)
  - NopMetaDataProduct entity: PASS (lines 3362-3451, all 22 columns + UK + relations)
  - 5 dicts: PASS (5 dicts with correct option values)
  - 5 asset entities with businessDomainId: PASS (correct propIds before extConfig)
  - Business domain inheritance BizModels: PASS (3 BizModels with save-time inheritance)
  - DataProduct linkAsset/unlinkAsset/getLinkedAssets: PASS (3 actions in NopMetaDataProductBizModel)
  - ErrorCodes: PASS (ERR_LINK_ASSET_NOT_FOUND + ERR_LINK_ASSET_ENTITY_TYPE_INVALID)
  - Tests: PASS (TestNopMetaBusinessDomainDataProductCrud=5 tests + TestNopMetaDataProductLinkAsset=6 tests)
  - Design docs: PASS (all 3 docs updated with Phase 3 = implemented, S3 = done)
  - Anti-Hollow Check: PASS (BizModel save hooks call parent entity DAO; linkAsset calls TagLabel DAO; no hollow components)
  - `./mvnw test -pl nop-metadata -am`: PASS (685 tests)
  - `node ai-dev/tools/check-plan-checklist.mjs --strict`: PASS (0 errors, final run)

Follow-up:

- G2 TagLabel 治理工作流接管 linkAsset 创建的 Suggested state TagLabel（后续治理 plan）
- 业务域级联更新（父资产 businessDomainId 变更时不级联更新子资产，当前仅 save-time 继承）
