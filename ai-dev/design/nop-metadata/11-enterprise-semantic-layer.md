# nop-metadata 企业语义层设计

**日期**：2026-07-20（更新于 2026-07-20，Phase 1 定型）
**范围**：`nop-metadata` 模块 ORM 模型 + `nop-sys` 标签系统
**状态**：已实现（Phase 1），Phase 2/3/4 仍为草案
**灵感来源**：OpenMetadata Classification/Glossary/Domain/DataProduct + DataHub Tags/GlossaryTerms/Domains

---

## 一、设计结论

1. **新增五个核心实体**：`NopMetaClassification`（分类体系）、`NopMetaTag`（分类标签）、`NopMetaGlossary`（业务词汇表）、`NopMetaGlossaryTerm`（业务术语）、`NopMetaDataProduct`（数据产品）
2. **新增统一桥接实体 `NopMetaTagLabel`**：将 Classification Tag 和 Glossary Term 的标注统一为一张表，挂载到任意元数据资产上，支持来源追踪（{source, labelType, state} 三要素，吸收自 OpenMetadata TagLabel）
3. **新增 `NopMetaBusinessDomain`**（业务组织域），与现有的 ORM 域定义 `NopMetaDomain` 明确区分，避免命名冲突
5. **存量兼容**：`NopMetaSemanticType` 保留为内置种子术语（导入时自动创建对应 Glossary + GlossaryTerm）；`NopSysTag` 保留为**轻量正交标签**（不参与 TagLabel 体系）；`tagSet` 字符串字段保留为兼容层
6. **所有新实体通过 Nop CrudBizModel 自动暴露 GraphQL CRUD API**，无需手写 service 层
7. **分四个 Phase 实现**，每个 Phase 可独立发布

---

## 二、背景与动机

### 当前痛点

| 痛点 | 表现 |
|------|------|
| **技术元数据和业务语义脱节** | 工程师知道字段叫 `cust_id`，但业务不知道这是"客户编号" |
| **无统一分类体系** | `NopSysTag` 扁平无层级，无法表达"PII.Sensitive.Phone"这种嵌套语义 |
| **无业务词汇表** | 不同团队对同一概念命名不一致（客户/顾客/Customer），没有权威定义 |
| **无组织域归属** | 无法回答"这张表属于哪个业务域？谁是专家？" |
| **无数据产品概念** | 无法将一组资产打包为"客户360视图"这样的可消费产品，附带 SLA |
| **语义标注不可追踪** | 标注是人工还是自动推导？是已确认还是待审查？没有记录 |

### 目标

在技术元数据（表/字段/度量/维度/血缘/质量）之上增加一层**业务语义层**，让非技术人员能用业务语言发现、理解、治理数据资产。

---

## 附：新增 Dict 定义

Phase 1 新增以下 4 个 dict，在 `nop-metadata.orm.xml` 的 `<dicts>` 段中声明：

### meta/tag-label-source

| option value | label | i18n-en:label | 说明 |
|-------------|-------|---------------|------|
| `Classification` | 分类标签 | Classification | 来自 Classification 体系 |
| `Glossary` | 业务术语 | Glossary | 来自业务词汇表 |

### meta/tag-label-type

| option value | label | i18n-en:label | 说明 |
|-------------|-------|---------------|------|
| `Manual` | 手动标注 | Manual | 用户手动添加 |
| `Propagated` | 血缘传播 | Propagated | 通过血缘关系自动传播 |
| `Automated` | 自动识别 | Automated | 通过规则/ML 自动识别 |
| `Derived` | 派生标注 | Derived | 通过其他标注派生（如 GlossaryTerm 关联的 Classification Tag） |
| `Generated` | 系统生成 | Generated | 系统自动生成 |

### meta/tag-label-state

| option value | label | i18n-en:label | 说明 |
|-------------|-------|---------------|------|
| `Suggested` | 建议 | Suggested | 待确认 |
| `Confirmed` | 已确认 | Confirmed | 已审查确认 |

### meta/tag-provider

| option value | label | i18n-en:label | 说明 |
|-------------|-------|---------------|------|
| `system` | 系统内置 | System | 平台预置，不可删除但可禁用 |
| `user` | 用户自定义 | User | 用户自行创建，可删除 |

---

## 三、核心设计

### 3.1 概念分层

```mermaid
flowchart BT
    subgraph Technical Layer
        NopMetaTable
        NopMetaEntity
        NopMetaEntityField
        NopReportDefinition
    end

    subgraph Semantic Bridge
        NopMetaTagLabel
    end

    subgraph Business Layer
        NopMetaClassification --> NopMetaTag
        NopMetaGlossary --> NopMetaGlossaryTerm
        NopMetaBusinessDomain
        NopMetaDataProduct
    end

    NopMetaTagLabel --> NopMetaClassification
    NopMetaTagLabel --> NopMetaGlossary
    NopMetaTagLabel -.-> Technical Layer
    NopMetaBusinessDomain -.-> Technical Layer
    NopMetaDataProduct --> NopMetaBusinessDomain
```

### 3.2 实体定义

以下实体定义采用 Nop ORM model-first 风格。所有实体遵循以下约定：
- PK 统一使用 `string` + `tagSet="seq"`（与 `nop-metadata.orm.xml` 现有实体一致）
- 审计字段（`version`, `createdBy`, `createTime`, `updatedBy`, `updateTime`, `remark`）每个实体自动追加
- 扩展配置使用 `extConfig`（`domain="json-4000"`，与 Nop 平台现有习惯一致）
- 所有实体通过 CrudBizModel 自动暴露 GraphQL CRUD

#### 3.2.1 NopMetaClassification（分类体系）

Classification 是标签的命名空间容器。它控制标签的互斥语义和自动识别配置。**灵感来源**: OpenMetadata Classification。

**ORM 列设计**（与 `nop-metadata.orm.xml` 风格一致）：

| 属性 | 类型 | 约束 | 说明 |
|------|------|------|------|
| classificationId | string(32) | PK, tagSet=seq | 主键 |
| name | string(100) | UNIQUE, mandatory | 标识符，如 "PII"、"Tier" |
| displayName | string(200) | tagSet=disp | 显示名，如 "敏感数据" |
| description | string(1000) | | 描述 |
| mutuallyExclusive | byte(TINYINT) | mandatory, domain=boolFlag | true=分类语义（实体只能选一个标签，如 tier1/tier2/tier3）；false=标注语义（实体可有多个标签，如 newCustomer + atRisk） |
| provider | string(20) | mandatory, ext:dict=`meta/tag-provider` | `system` \| `user`。system 分类不可删除但可 disabled。**灵感来源**: OpenMetadata providerType |
| disabled | byte(TINYINT) | domain=boolFlag | 禁用标志。system 级分类禁用代替删除 |
| autoClassificationConfig | string | domain=json-4000 | 自动识别配置。**灵感来源**: OpenMetadata autoClassificationConfig。包含 `{enabled, conflictResolution, minimumConfidence, requireExplicitMatch}` |
| extConfig | string | domain=json-4000 | 扩展配置 |

**Relations**：
- `to-many tags` → `NopMetaTag`（反向 `to-one classification` 在 NopMetaTag 上定义）

**Delta 定制**：在多租户场景下，平台预置 System Classification（如 "PII"、"Tier"），租户通过 Delta 机制扩展自己的分类。Delta 继承 base 分类的 `mutuallyExclusive` 和 `provider`，但可覆盖 `disabled` 和 `autoClassificationConfig`。

#### 3.2.2 NopMetaTag（分类标签）

标签是 Classification 下的具体分类项，支持层级（自引用 parentTagId）。**灵感来源**: OpenMetadata Tag。

| 属性 | 类型 | 约束 | 说明 |
|------|------|------|------|
| tagId | string(32) | PK, tagSet=seq | |
| classificationId | string(32) | FK → NopMetaClassification, mandatory | 所属分类 |
| parentTagId | string(32) | FK → NopMetaTag | 父标签，支持层级 |
| name | string(100) | mandatory | 标签名，如 "Sensitive"、"Tier1" |
| fullyQualifiedName | string(500) | UNIQUE, mandatory | 全限定名，如 "PII.Sensitive.Phone"。Phase 1 手动输入，格式约定见下文 |
| displayName | string(200) | tagSet=disp | 显示名 |
| description | string(1000) | | |
| deprecated | byte(TINYINT) | domain=boolFlag | 废弃标志 |
| mutuallyExclusive | byte(TINYINT) | domain=boolFlag | **层级互斥**：子标签是否互斥。扩展自 OpenMetadata Tag 的层级互斥 |
| extConfig | string | domain=json-4000 | 扩展配置，可包含 `recognizers`、`autoClassificationPriority` |

`fullyQualifiedName` 在 Phase 1 由用户**手动输入**，格式约定为 `{Classification.name}.{parentTag.fullyQualifiedName}`。自动生成推迟到后续 Phase（可在 BizModel 的 pre-save 中实现，不涉及 ORM 层变更）。

**NopMetaTag 与 NopSysTag 的关系**：

| 维度 | NopSysTag | NopMetaTag |
|------|-----------|------------|
| 归属 | 无命名空间 | 属于某个 Classification |
| 层级 | 扁平 | 支持父子层级 |
| 互斥 | 无 | 支持（分类级 + 标签级） |
| 语义标注 | 不参与 TagLabel | 通过 TagLabel 标注到资产 |
| 上下文 | 通用轻量标签（非元数据上下文） | 元数据语义标注 |
| 迁移方向 | 保留，不废弃 | 新增首选路径 |

#### 3.2.3 NopMetaGlossary（业务词汇表）

Glossary 是业务术语的集合。与 Classification 的区别：Classification 是技术层面的分类标注，Glossary 是业务层面的概念定义。**灵感来源**: OpenMetadata Glossary。

| 属性 | 类型 | 约束 | 说明 |
|------|------|------|------|
| glossaryId | string(32) | PK, tagSet=seq | |
| name | string(100) | UNIQUE, mandatory | 标识符，如 "FinancialGlossary" |
| displayName | string(200) | tagSet=disp | 显示名，如 "金融词汇表" |
| description | string(1000) | | |
| owner | string(50) | | 负责人 |
| reviewers | string | domain=json-1000 | 审查人列表 |
| mutuallyExclusive | byte(TINYINT) | domain=boolFlag | 子术语互斥（词汇表级的默认值，可被术语级覆盖） |
| namespaces | string | domain=json-4000 | 命名空间前缀→IRI 映射。**灵感来源**: OpenMetadata Glossary namespaces。用于外部本体导入/导出的 RDF/OWL 往返。如 `[{prefix:"hcp", namespace:"http://example.com/ontology/hcp#"}]` |
| extConfig | string | domain=json-4000 | |

#### 3.2.4 NopMetaGlossaryTerm（业务术语）

核心业务概念定义。远不止一个标签——包含同义词、相关术语关系、外部概念映射等。**灵感来源**: OpenMetadata GlossaryTerm。

| 属性 | 类型 | 约束 | 说明 |
|------|------|------|------|
| glossaryTermId | string(32) | PK, tagSet=seq | |
| glossaryId | string(32) | FK → NopMetaGlossary, mandatory | 所属词汇表 |
| parentTermId | string(32) | FK → NopMetaGlossaryTerm | 父术语，支持层级 |
| name | string(200) | mandatory | 术语名，如 "Customer" |
| fullyQualifiedName | string(500) | UNIQUE | 全限定名，如 "FinancialGlossary.Customer.VIP" |
| displayName | string(200) | tagSet=disp | 显示名，如 "客户" |
| description | string(1000) | | |
| synonyms | string | domain=json-4000 | 同义词列表。**灵感来源**: OpenMetadata synonyms。如 `["顾客", "客户方", "customer"]` |
| relatedTerms | string | domain=json-4000 | 相关术语关系。如 `[{glossaryTermId, relationType: broader\|narrower\|synonym\|relatedTo}]` |
| references | string | domain=json-4000 | 外部引用。如 `[{url, description, source}]`。**灵感来源**: OpenMetadata termReference |
| conceptMappings | string | domain=json-4000 | SKOS 概念映射。如 `[{conceptIri, mappingType: exactMatch\|closeMatch\|broaderMatch\|...}]`。**灵感来源**: OpenMetadata conceptMapping |
| iri | string(500) | | 规范 IRI。用于 RDF/OWL 导出往返 |
| mutuallyExclusive | byte(TINYINT) | domain=boolFlag | 子术语互斥（覆盖 Glossary 级默认值） |
| tags | string | domain=json-4000 | 关联的 Classification Tag 列表。**核心语义**：当 GlossaryTerm 被标注到资产上时，tags 中的 Tag 自动传播（生成 `labelType=Derived` 的 TagLabel 行）。**灵感来源**: OpenMetadata GlossaryTerm.tags |
| extConfig | string | domain=json-4000 | |

**GlossaryTerm → Classification Tag 自动传播行为**：

```
当 GlossaryTerm "客户"（关联 Classification Tag "PII.Sensitive"）被标注到某列上时：
  1. 生成 TagLabel{source: Glossary, glossaryTermId: "客户", labelType: Manual}
  2. 自动生成 TagLabel{source: Classification, tagId: "PII.Sensitive", labelType: Derived,
     state: Suggested, reason: "propagated from glossary term 客户"}

当资产 Owner 确认自动传播的 TagLabel：
  3. TagLabel{tagId: "PII.Sensitive"} 的 state 由 Suggested → Confirmed
```

传播行为仅在**当前 Term 级别**触发，子 Term 不自动继承父 Term 的 tags。

#### 3.2.5 NopMetaTagLabel（统一桥接）

**核心设计创新**（吸收自 OpenMetadata TagLabel）：用一张实体表统一所有语义标注，挂载到任意元数据资产。**Nop 平台实现差异**：OpenMetadata 的 TagLabel 是 JSON type（非独立实体），Nop 版本是 ORM 实体，带来 FK 约束、索引、CrudBizModel 自动暴露的优势。

| 属性 | 类型 | 约束 | 说明 |
|------|------|------|------|
| tagLabelId | string(32) | PK, tagSet=seq | |
| source | string(20) | mandatory, ext:dict `meta/tag-label-source` | `Classification` \| `Glossary`。**灵感来源**: OpenMetadata TagLabel.TagSource |
| tagId | string(32) | FK → NopMetaTag（source=Classification 时） | |
| glossaryTermId | string(32) | FK → NopMetaGlossaryTerm（source=Glossary 时） | |
| labelType | string(20) | mandatory, ext:dict `meta/tag-label-type` | `Manual` \| `Propagated` \| `Automated` \| `Derived` \| `Generated`。**全量吸收** OpenMetadata labelType |
| state | string(20) | mandatory, ext:dict `meta/tag-label-state` | `Suggested` \| `Confirmed`。**全量吸收** OpenMetadata state |
| entityType | string(100) | mandatory | 被标注资产类型，如 `"MetaTable"`、`"MetaEntityField"` |
| entityId | string(32) | mandatory | 被标注资产 PK |
| appliedBy | string(50) | | 标注人（或系统） |
| appliedAt | timestamp | | 标注时间 |
| reason | string(1000) | | 标注理由。**灵感来源**: OpenMetadata TagLabel.reason |
| metadata | string | domain=json-4000 | 扩展元数据，如自动分类的 recognizer 信息。**灵感来源**: OpenMetadata TagLabel.metadata |
| extConfig | string | domain=json-4000 | |

**索引设计**：
- `IX_NOP_META_TAG_LABEL_ENTITY` on `(entityType, entityId)` — 按资产查询所有标注
- `IX_NOP_META_TAG_LABEL_TAG` on `tagId` — 按标签反向查询
- `IX_NOP_META_TAG_LABEL_TERM` on `glossaryTermId` — 按术语反向查询
- `IX_NOP_META_TAG_LABEL_STATE` on `state` — 筛选待确认标注

**为什么不是多对多关联表而是单表**：
- 统一查询：`WHERE entityType=? AND entityId=?` 即可获取所有语义标注，不分来源
- 统一传播：血缘传播引擎只需要处理一张表
- 统一审计：状态变更（Suggested→Confirmed）的工作流统一管理
- 资产类型有限（约 10 种），`entityType+entityId` 复合索引查询效率足够

#### 3.2.6 NopMetaBusinessDomain（业务组织域）

有界上下文，对应业务单元或职能域。与现有的 `NopMetaDomain`（ORM 数据类型域）是不同概念，两者独立并存。**灵感来源**: OpenMetadata Domain。

| 属性 | 类型 | 约束 | 说明 |
|------|------|------|------|
| businessDomainId | string(32) | PK, tagSet=seq | |
| parentDomainId | string(32) | FK → NopMetaBusinessDomain | 父域（子域） |
| name | string(100) | UNIQUE, mandatory | 如 "Marketing"、"Payments" |
| displayName | string(200) | tagSet=disp | 如 "营销域"、"支付域" |
| description | string(1000) | | |
| domainType | string(20) | ext:dict | `Source-aligned` \| `Consumer-aligned` \| `Aggregate`。**灵感来源**: OpenMetadata Domain.domainType |
| experts | string | domain=json-1000 | 专家列表。**灵感来源**: OpenMetadata Domain.experts |
| owners | string | domain=json-1000 | 负责人 |
| extConfig | string | domain=json-4000 | |

**资产到业务域的归属**：在资产实体上直接加 `businessDomainId` 字段（如 `NopMetaTable.businessDomainId`、`NopMetaEntity.businessDomainId` 等）。资产层级（Service → Database → Schema → Table → Column）的继承机制见 §3.3.2。

#### 3.2.7 NopMetaDataProduct（数据产品）

**灵感来源**: OpenMetadata DataProduct。

| 属性 | 类型 | 约束 | 说明 |
|------|------|------|------|
| dataProductId | string(32) | PK, tagSet=seq | |
| businessDomainId | string(32) | FK → NopMetaBusinessDomain, mandatory | 所属业务域 |
| name | string(100) | UNIQUE within domain, mandatory | |
| displayName | string(200) | tagSet=disp | |
| description | string(1000) | | |
| lifecycleStage | string(20) | ext:dict | `IDEATION` \| `DESIGN` \| `DEVELOPMENT` \| `TESTING` \| `PRODUCTION` \| `DEPRECATED` \| `RETIRED` |
| dataProductType | string(30) | ext:dict | `RAW_DATA` \| `DERIVED_DATA` \| `DATASET` \| `REPORTS` \| `ANALYTIC_VIEW` \| `ALGORITHM` |
| visibility | string(20) | ext:dict | `PRIVATE` \| `INVITATION` \| `ORGANISATION` \| `DATASPACE` \| `PUBLIC` |
| portfolioPriority | string(10) | ext:dict | `CRITICAL` \| `HIGH` \| `MEDIUM` \| `LOW` |
| sla | string | domain=json-4000 | SLA 定义。包含 `{tier, availability, responseTime, dataFreshness, dataQuality}`。**灵感来源**: OpenMetadata DataProduct.slaDefinition |
| consumesFrom | string | domain=json-4000 | 依赖的其他 DataProduct 列表。**灵感来源**: OpenMetadata DataProduct.consumesFrom |
| providesTo | string | domain=json-4000 | 被哪些 DataProduct 消费 |
| experts | string | domain=json-1000 | |
| assets | string | domain=json-4000 | 包含的资产引用列表（冗余，便于 UI 展示）。不替代 TagLabel 精确关联 |
| ports | string | domain=json-4000 | 数据端口定义。**灵感来源**: OpenMetadata DataProduct.dataProductPort。含 `[{name, portType, protocol, format, endpoint}]` |
| extConfig | string | domain=json-4000 | |

**资产关联方式**：DataProduct 到资产的精确关联通过 TagLabel 完成（创建 `TagLabel{source: Classification, tagId: DataProductTag}`）。`assets` JSON 字段仅作为 UI 展示的冗余缓存。

### 3.3 核心逻辑

#### 3.3.1 GlossaryTerm → Classification Tag 自动传播

见 §3.2.4 的行为描述。实现路径：
- NopMetaGlossaryTerm 的 `tags` JSON 在 save/update 时触发传播引擎
- 传播引擎创建/更新 `TagLabel{source: Classification, labelType: Derived}`
- 使用 `(entityType, entityId, tagId, labelType=Derived)` 幂等键防重复

#### 3.3.2 资产层级上的业务域继承

这是"资产自身层级"的继承（Service → Database → Schema → Table → Column），与"业务域层级"（Parent BusinessDomain → Child BusinessDomain）正交：

```
资产通过 businessDomainId 直接归属：
  Table 未设 businessDomainId → 继承 Schema.businessDomainId
  Schema 未设 → 继承 Database.businessDomainId
  Database 未设 → 继承 DatabaseService.businessDomainId
  仅 Table 显式设了 → 使用显式值
```

**实现方式**：在 BizModel 的 `save` 重写中实现继承逻辑，不存储在 ORM 层。

#### 3.3.3 标签传播（血缘驱动）

血缘传播是可选增强（Phase 3+）：

```
当 Column A 的血缘指向 Column B（transformType=DIRECT）：
  Column B 上的 TagLabel（labelType=Manual 或 state=Confirmed）
  → 在 Column A 上生成 TagLabel（labelType=Propagated）
```

### 3.4 存量兼容

| 存量元素 | 与新设计的关系 |
|---------|--------------|
| `NopMetaSemanticType` | 保留。作为内置 GlossaryTerm 种子数据（PK/FK/Name/Title/Date/Currency 等），导入时自动创建对应 Glossary + GlossaryTerm |
| `NopSysTag` + `NopSysObjTag` | **保留不变**。用于非元数据上下文的快速轻量标注，不参与 TagLabel 体系。未来新元数据实体的语义标注应走 TagLabel + NopMetaTag |
| `tagSet` 字符串字段 | 保留为兼容层。所有 ORM 实体的 `tagSet` 字段保持不动。新增后台任务将 `tagSet` 内容迁移到 TagLabel（可选） |
| `NopMetaDict` | 独立于语义层。字典仍是枚举值定义，不改为 Glossary |

### 3.5 Nop Delta 定制对语义层的影响

**场景：SaaS 多租户元数据目录**。

Nop 的 Delta 机制允许在一个模块基础上通过 `x:extends` 继承并覆盖。对于企业语义层：

| 组件 | Delta 定制方式 |
|------|--------------|
| **Classification** | 平台预置 System Classification（`provider=system`），租户通过 Delta 继承 base 模块，新增自己的 Classification（`provider=user`） |
| **Tag** | 租户在继承的 Classification 下新增自己的 Tag，或在租户专属 Classification 下全量新建 |
| **Glossary** | 类似。平台预置行业级 Glossary（如金融词汇），租户 Delta 新增专属术语 |
| **TagLabel** | 租户数据的 TagLabel 天然隔离（通过 `entityType+entityId` 指向属于该租户的资产）。不依赖 Delta，而是依赖数据权限 |
| **BusinessDomain** | 每个租户维护自己的业务域层级 |

**实现约束**：
- System Classification（`provider=system`）不可删除，但可通过 `disabled` 禁用
- Delta 继承时，子模块可新增 Tag 但不能删除 base 模块的 Tag（Nop Delta 原则：只增不减）

---

## 四、拒绝了什么

### 4.1 直接复用 NopSysTag 扩展层级

**拒绝理由**：`NopSysTag` 缺少 tag 分组（Classification）、互斥语义、自动识别配置、deprecated 标志等字段，扩展后与元数据标签系统耦合过深。同时 NopSysTag 已广泛应用于非元数据上下文，改变其语义会破坏现有功能。新增 `NopMetaTag` 更清晰。

### 4.2 使用 extConfig JSON 存所有语义信息

**拒绝理由**：extConfig 无法表达结构化关系（如同义词、概念映射的 IRI），无法建立 FK 约束和索引，查询效率低。关键语义信息应该用实体+列表达。**边界明确**：`extConfig` 用于扩展配置，不用于核心语义字段。

### 4.3 用多对多关联表代替 TagLabel 单表

每个资产类型建一张关联表（`NopMetaTableTag`、`NopMetaEntityFieldTag`...）：

**拒绝理由**：单表带来统一的查询/传播/审计体验。资产类型是有限的（约 10 种），`entityType+entityId` 复合索引查询效率足够。如果未来需要物理分表，可以加 `tagLabelShard` 字段做物理分片，不影响应用层。

### 4.4 把 GlossaryTerm 和 Classification Tag 合并

**拒绝理由**：两者语义不同——Classification 用于技术分类（PII/Sensitive），Glossary 用于业务定义（客户/账户余额）。合并会丢失 `mutuallyExclusive` 语义和自动识别配置。OpenMetadata 也是分开的。

### 4.5 在资产实体上直接加 tags 字段（JSON array）

**拒绝理由**：丧失来源追踪（手动/自动/传播）、状态（建议/确认）、时间线和审计能力。

### 4.6 把业务域命名为 NopMetaDomain

**拒绝理由**：`NopMetaDomain` 已在 `02-gap-analysis.md` 和 `01-architecture-baseline.md` 中用于定义 ORM 数据类型域（stdDataType/stdSqlType/precision/scale 等）。如果重载为业务组织域会造成读者混淆。**已采纳**：新实体命名 `NopMetaBusinessDomain`，现有 `NopMetaDomain` 保持不动。

---

## 五、与已有设计的关系

| 文档 | 关系 |
|------|------|
| `00-vision.md` | 补充 Vision：元数据目录的管理范围从技术元数据扩展到企业语义层。更新 Non-goals：不替代专业数据治理平台，但提供治理所需的结构化语义数据 |
| `01-architecture-baseline.md` | **并无关**：现有 `§2.4` 的 `MetaDomain`（ORM 数据类型域）与本设计的 `NopMetaBusinessDomain`（业务组织域）名称相似但概念不同，两者独立并存。更新"二、核心对象"部分新增 6 个实体 |
| `02-gap-analysis.md` | Gap 4（标签体系）从此前的 ⚠️ 状态升级为完整的语义层设计。Gap 4 的 `MetaTag` 和 `MetaEntityTag` 设计建议被本设计的 NopMetaClassification + NopMetaTag + NopMetaTagLabel 替代 |
| `04-data-governance.md` | 治理范围扩展：从域定义/字典扩展为包含 Classification/Glossary/BusinessDomain/DataProduct |
| `10-event-model.md` | TagLabel 的 state 变更（Suggested→Confirmed）应触发生成事件 |
| `01-architecture-baseline.md §2.4 NopMetaDomain` | **无冲突**：ORM 数据类型域 `NopMetaDomain` 与业务组织域 `NopMetaBusinessDomain` 并存，无需迁移 |

---

## 六、实现路径

### Phase 1: Classification + TagLabel（核心）— ✅ 已实现

新增实体：`NopMetaClassification`、`NopMetaTag`、`NopMetaTagLabel`
- 新增 4 个 dict：`meta/tag-label-source`、`meta/tag-label-type`、`meta/tag-label-state`、`meta/tag-provider`
- TagLabel 的 CRUD + 按资产查询（CrudBizModel 自动提供）
- 存量 `NopSysTag` + `tagSet` 兼容
- **依赖**：ORM 模型变更 → codegen 重新生成 DAO/BizModel/Web
- **实现规模**：约 3 个 ORM 实体 + 4 个 dict + 0 个手写 service
- **实现证据**：`nop-metadata.orm.xml` `<dicts>` 段新增 4 个 dict，`<entities>` 段新增 3 个实体；codegen 生成 DAO 实体类、BizModel、xmeta、i18n、Web 页面；集成测试 `TestNopMetaClassificationTagLabelCrud` 覆盖 GraphQL CRUD 和 entityType+entityId 查询

### Phase 2: Glossary

新增实体：`NopMetaGlossary`、`NopMetaGlossaryTerm`
- GlossaryTerm → Classification Tag 自动传播
- 术语关系管理（broader/narrower/synonym）
- 自动传播引擎的 BizModel 实现（重写 save）
- **依赖**：Phase 1

### Phase 3: BusinessDomain + DataProduct

新增实体：`NopMetaBusinessDomain`、`NopMetaDataProduct`
- 新增资产实体的 `businessDomainId` 字段（`NopMetaTable`、`NopMetaEntity` 等）
- 业务域继承机制（§3.3.2）
- DataProduct 资产关联（通过 TagLabel 桥接）
- 无需迁移现有 `NopMetaDomain`，两者名称不同概念也不同
- **依赖**：Phase 1

### Phase 4: 传播引擎增强（可选）

- 血缘驱动的 TagLabel 自动传播（§3.3.3）
- AutoClassification 引擎（规则/ML-based tag 推荐）
- GlossaryTerm ↔ 外部词汇表同步（SKOS/RDF）
- **依赖**：Phase 2

---

## 七、Nop 平台特有约束与设计映射

| Nop 平台能力 | 对本设计的影响 |
|-------------|--------------|
| **ORM model-first** | 所有实体必须在 `nop-metadata.orm.xml` 中声明，codegen 生成 Java POJO + DAO + BizModel |
| **CrudBizModel 自动暴露** | 新增实体不需要手写 GraphQL service。所有 CRUD（find/findPage/findList/get/save/update/delete）自动可用 |
| **Delta 定制** | 多租户场景下 Classification/Glossary 的继承扩展（见 §3.5） |
| **extConfig JSON** | 每个实体自带的扩展 JSON，对应 OpenMetadata 的 `extension`。本设计仅用于扩展配置，不替代核心语义字段 |
| **tagSet 字符串** | 保留为轻量标签兼容层，不参与 TagLabel 体系 |
| **IoC 注入** | 新增 BizModel 通过 `@Inject` 注入。注意 NopIoC 不支持 `private` 字段注入，使用 `protected` |
| **ISearchEngine** | Phase 1 后可通过 SearchableDoc 索引 Classification/Tag/GlossaryTerm，支持语义搜索 |
| **NopSysEvent** | TagLabel state 变更（Suggested→Confirmed）应通过 NopSysEvent 发布 |

---

## 八、与 OpenMetadata 设计差异总结

| 维度 | OpenMetadata | Nop 平台实现 | 理由 |
|------|-------------|-------------|------|
| TagLabel 存储 | JSON type（嵌入在资产实体中） | ORM 实体（独立表） | Nop ORM 实体获得 FK 约束、索引、自动 CRUD |
| Domain 映射 | 单一 Domain 实体，资产通过 `domain` 字段归属 | 新实体 `NopMetaBusinessDomain`（与现有 ORM 数据类型域 `NopMetaDomain` 独立并存） | 两者概念不同（业务域 vs 数据类型域），独立命名 |
| DataProduct 资产关联 | 直接资产引用列表（已 `@deprecated`） | 通过 TagLabel 桥接 | 统一桥接模式，资产引用可追踪/可审计 |
| Glossary 外部词汇表 | `namespaces`（前缀→IRI） | `NopMetaGlossary.namespaces` JSON | 全量吸收 |
| 自动分类 | `autoClassificationConfig` 在 Classification 和 Tag 级别 | 首版仅在 Classification 级别 | 简化首版实现，后续可扩展到 Tag 级别 |
| Entity 版本化 | 内置 entityHistory | 无独立版本化（元数据版本管理在 MetaModule 级别） | 对齐 nop-metadata 已有模块级版本策略 |
