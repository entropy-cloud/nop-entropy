# 302 企业语义层 Phase 1 — Classification + TagLabel

> Plan Status: active
> Last Reviewed: 2026-07-20
> Source: `ai-dev/design/nop-metadata/11-enterprise-semantic-layer.md`
> Related: `292-nop-metadata-implementation-roadmap.md`

## Purpose

在企业语义层设计中，Phase 1 落地 Classification（分类体系）、Tag（分类标签）和 TagLabel（统一语义桥接）三个核心 ORM 实体，通过 Nop codegen 自动生成全部 CRUD API 和 UI 页面，为后续 Glossary / Domain / DataProduct 阶段提供基础设施。

## Current Baseline

- `nop-metadata/model/nop-metadata.orm.xml` 已定义 32 个实体，无 Classification/Tag/TagLabel
- `NopMetaSemanticType` 已存在（表 `nop_meta_semantic_type`），通过字符串引用被 `NopMetaEntityField.semanticType` 使用
- `NopSysTag` / `NopSysObjTag` 已存在于 `nop-sys` 模块，扁平无层级/分组
- `nop-metadata` 的 dict 定义在 `nop-metadata.orm.xml` 的 `<dicts>` 段中
- codegen 管线已完备：`nop-metadata-codegen` → `nop-metadata-meta` → `nop-metadata-dao` → `nop-metadata-service` → `nop-metadata-web`
- 设计文档 `11-enterprise-semantic-layer.md` 已 draft 完成，定义了 Phase 1 的实体规格（含 4 个 dict 的 option 值）

## Goals

- 在 `nop-metadata.orm.xml` 中新增 `NopMetaClassification`、`NopMetaTag`、`NopMetaTagLabel` 三个实体
- 新增 4 个 dict：`meta/tag-label-source`、`meta/tag-label-type`、`meta/tag-label-state`、`meta/tag-provider`
- 新增实体的 `fullyQualifiedName` 字段由用户**手动输入**，格式约定为 `{Classification.name}.{parentTag.fullyQualifiedName}`（自动生成推迟到后续 Phase）
- `NopMetaClassification.provider` 列使用 `ext:dict=meta/tag-provider`
- 运行 codegen，生成 DAO 实体类、BizModel、xmeta、i18n、Web 页面
- 验证新增实体可通过 GraphQL CRUD 正常操作（增删改查）
- 验证 TagLabel 可按 `entityType + entityId` 查询
- 存量 `NopSysTag`、`NopMetaSemanticType`、`tagSet` 字段不受影响

## Non-Goals

- 不实现 Glossary / GlossaryTerm（Phase 2）
- 不实现 BusinessDomain / DataProduct（Phase 3）
- 不实现 GlossaryTerm → Classification Tag 自动传播（Phase 2）
- 不实现 `fullyQualifiedName` 自动生成（推迟到后续 Phase）
- 不实现血缘驱动标签传播（Phase 4）
- 不修改 `NopSysTag` / `NopSysObjTag` 现有行为
- 不修改 `NopMetaSemanticType` 现有行为
- 不迁移 `tagSet` 存量数据到 TagLabel

## Scope

### In Scope

- `nop-metadata.orm.xml` 新增 3 个实体（含列、索引、唯一键、relations） + 4 个 dict
- `nop-metadata-codegen`、`nop-metadata-dao`、`nop-metadata-service`、`nop-metadata-web` 的 codegen 重新生成
- 验证：GraphQL CRUD 可用（新增集成测试）
- 验证：按 `entityType + entityId` 查询 TagLabel（新增集成测试）
- `11-enterprise-semantic-layer.md` 更新为 Phase 1 已实现的最终状态

### Out Of Scope

- 任何自定义 BizModel action（由 CrudBizModel 自动提供即可）
- 前端页面定制（codegen 生成的 _gen 页面即可，不写自定义 view.xml）
- `fullyQualifiedName` 自动生成逻辑
- 性能测试
- 数据迁移脚本

## Execution Plan

### Phase 1 - 新增 ORM 实体 + Codegen

Status: planned
Targets: `nop-metadata/model/nop-metadata.orm.xml` → `nop-metadata-{codegen,dao,service,web}`

- Item Types: `Fix`

- [ ] 在 `nop-metadata.orm.xml` 的 `<dicts>` 段新增 4 个 dict：`meta/tag-label-source`、`meta/tag-label-type`、`meta/tag-label-state`、`meta/tag-provider`（option 值见设计文档 §附）
- [ ] 在 `nop-metadata.orm.xml` 的 `<entities>` 段新增 `NopMetaClassification` 实体（含列、unique-keys、indexes、relations；`provider` 列使用 `ext:dict=meta/tag-provider`）
- [ ] 新增 `NopMetaTag` 实体（含自引用 parentTagId FK 的 to-one relation、classificationId FK 的 to-one relation、`(classificationId, fullyQualifiedName)` unique-key、indexes）
- [ ] 新增 `NopMetaTagLabel` 实体（含 4 个索引、`tagId` FK 和 `glossaryTermId` FK 的 to-one relation，注意 glossaryTermId 的 FK 在 Phase 2 补充约束）
- [ ] 在 `NopMetaEntityField` 中已有 `semanticType` 字段，确认不修改
- [ ] 运行 `./mvnw clean install -DskipTests -pl nop-metadata-codegen,nop-metadata-dao -am && ./mvnw clean install -DskipTests -pl nop-metadata-service,nop-metadata-web -am` 编译通过
- [ ] 运行 `./mvnw test -pl nop-metadata-service -am` 测试通过
- [ ] 新增集成测试：验证 Classification/Tag/TagLabel 的 GraphQL CRUD 操作（增删改查）
- [ ] 新增集成测试：验证 TagLabel 按 `entityType + entityId` 查询
- [ ] 确认无空壳代码：新增实体中无空方法体/continue/吞异常
- [ ] 更新 `11-enterprise-semantic-layer.md`：将 Phase 1 标记为已实现，移除草案状态，明确 Phase 2/3/4 仍为草案

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `nop-metadata.orm.xml` 中新增 3 个实体定义已存在且风格与现有实体一致（含 propId 递增、`i18n-en:displayName`、`ext:icon`）
- [ ] 4 个新增 dict 在 `<dicts>` 段中存在，option 值与设计文档一致
- [ ] `NopMetaClassification` 包含：classificationId(PK, string32, seq, tagSet=seq), name(UNIQUE, mandatory), displayName(tagSet=disp), description, mutuallyExclusive(boolFlag, mandatory), provider(mandatory, ext:dict=meta/tag-provider), disabled(boolFlag), autoClassificationConfig(json-4000), extConfig(json-4000) + 审计字段；relation `to-many tags` → NopMetaTag
- [ ] `NopMetaTag` 包含：tagId(PK, string32, seq), classificationId(FK, mandatory), parentTagId(FK, self-ref), name, fullyQualifiedName(UNIQUE, mandatory), displayName(tagSet=disp), description, deprecated(boolFlag), mutuallyExclusive(boolFlag), extConfig(json-4000) + 审计字段
- [ ] `NopMetaTag` 存在 UK `(classificationId, fullyQualifiedName)`
- [ ] `NopMetaTag` 存在 `<relations>`: to-one `classification` 和 to-one `parentTag`（含反向 to-many children）
- [ ] `NopMetaTagLabel` 包含：tagLabelId(PK, string32, seq), source(mandatory, ext:dict=meta/tag-label-source), tagId(FK→NopMetaTag, nullable), glossaryTermId(nullable, FK 待 Phase 2 补充), labelType(mandatory, ext:dict=meta/tag-label-type), state(mandatory, ext:dict=meta/tag-label-state), entityType(string100, mandatory), entityId(string32, mandatory), appliedBy, appliedAt(timestamp), reason(string1000), metadata(json-4000), extConfig(json-4000) + 审计字段
- [ ] TagLabel 上存在 4 个索引：`IX_NOP_META_TAG_LABEL_ENTITY (entityType, entityId)`、`IX_NOP_META_TAG_LABEL_TAG (tagId)`、`IX_NOP_META_TAG_LABEL_TERM (glossaryTermId)`、`IX_NOP_META_TAG_LABEL_STATE (state)`
- [ ] TagLabel 存在 `<relations>`: to-one `tag` 和 to-one `glossaryTerm`（glossaryTerm FK 约束暂缺）；`entityType` 使用实体简写名（如 `"NopMetaEntityField"`），与 GraphQL type 名一致
- [ ] `./mvnw compile -pl nop-metadata-codegen -am && ./mvnw compile -pl nop-metadata-dao,nop-metadata-service,nop-metadata-web -am` 编译通过（分两步确保 codegen postcompile 先生成代码，下游模块再编译）
- [ ] `./mvnw test -pl nop-metadata-service -am` 通过
- [ ] **端到端验证**：新增集成测试覆盖（a）创建 Classification → 在其下创建 Tag → 用 TagLabel 标注到 MetaEntityField（b）按 entityType+entityId 查询到该 TagLabel
- [ ] **接线验证**：新增集成测试触发 GraphQL endpoint，验证 CrudBizModel 确实被调用并返回正确结果
- [ ] **无静默跳过**：新增实体代码中无空方法体/continue/吞异常；`UnsupportedOperationException` 仅在明确声明的暂缓功能处使用
- [ ] `ai-dev/design/nop-metadata/11-enterprise-semantic-layer.md` 更新为 Phase 1 已实现的最终状态
- [ ] `ai-dev/logs/2026/07-20.md` 已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 所有 in-scope 3 个实体及 4 个 dict 已在 ORM XML 中定义并 codegen 通过
- [ ] 所有存量兼容项（NopSysTag、NopMetaSemanticType、tagSet）未受影响
- [ ] `./mvnw compile && ./mvnw test -pl nop-metadata-service -am` 通过
- [ ] 受影响的 owner docs 已同步：`11-enterprise-semantic-layer.md` Phase 1 已标记为已实现
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：closure audit 验证（a）集成测试的端到端路径连通，（b）无空方法体/静默跳过
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### TagLabel glossaryTermId FK 约束

- Classification: `optimization candidate`
- Why Not Blocking Closure: `glossaryTermId` 列和索引已存在，但 Glossary 实体尚未定义，此时 FK 指向的表还不存在。当前设计为 Phase 2 预留了列和索引和 relation 定义，但在 `<columns>` 中不设 FK 约束（`fk` 属性暂缺），Phase 2 引进 GlossaryTerm 时再补充。
- Successor Required: `yes`
- Successor Path: `Phase 2 plan`

### fullyQualifiedName 自动生成

- Classification: `optimization candidate`
- Why Not Blocking Closure: Phase 1 依赖用户手动输入 `fullyQualifiedName`，格式约定为 `{Classification.name}.{parentTag.fullyQualifiedName}`。自动生成逻辑不影响 ORM 结构和 API 契约。
- Successor Required: `yes`
- Successor Path: 后续 Phase（可在 BizModel 的 pre-save 中实现）

## Non-Blocking Follow-ups

- 无

## Closure

Status Note: *待 closure audit 后填写*
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- no remaining plan-owned work
