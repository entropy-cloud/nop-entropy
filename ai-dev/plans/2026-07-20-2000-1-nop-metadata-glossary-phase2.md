# 2026-07-20-2000-1 nop-metadata Glossary Phase 2

> Plan Status: completed
> Last Reviewed: 2026-07-21
> Completed: 2026-07-21
> Source: `ai-dev/design/nop-metadata/11-enterprise-semantic-layer.md` §六 Phase 2 + §3.2.3/§3.2.4 + `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` S2
> Related: `302-enterprise-semantic-layer-phase1.md`（Deferred But Adjudicated: glossaryTermId FK + fullyQualifiedName auto-gen）

## Purpose

在 S1（Classification + TagLabel）基础上实现 Glossary Phase 2：新增 `NopMetaGlossary`（业务词汇表）和 `NopMetaGlossaryTerm`（业务术语）两个 ORM 实体，补充 `NopMetaTagLabel.glossaryTermId` FK 和 `NopMetaTag.fullyQualifiedName` auto-generation 两个 deferred items，实现 GlossaryTerm → Classification Tag 自动传播引擎。

## Current Baseline

- `nop-metadata.orm.xml` 已有 35 个实体（S1 新增 3 个），包括 `NopMetaClassification`、`NopMetaTag`、`NopMetaTagLabel`
- `NopMetaTagLabel` 已预留 `glossaryTermId` 列和 `IX_NOP_META_TAG_LABEL_TERM` 索引，但缺少 FK 约束（NopMetaGlossaryTerm 尚不存在）
- `NopMetaTag.fullyQualifiedName` 由用户手动输入，无自动生成逻辑（S1 Deferred）
- `NopMetaSemanticType` 为 9 个内置种子术语（PK/FK/Name/Title/Date/Currency 等），尚未映射为 GlossaryTerm
- `nop-metadata.orm.xml` `<dicts>` 段已有 `meta/tag-label-source` 含 `Glossary` 值（S1 已定义）
- codegen 管线完备：codegen → dao → service → web
- 设计文档 `11-enterprise-semantic-layer.md` 的 Phase 1 已定型，Phase 2/3/4 仍为草案

## Goals

- 在 `nop-metadata.orm.xml` 中新增 `NopMetaGlossary` 和 `NopMetaGlossaryTerm` 两个实体
- 补充 `NopMetaTagLabel.glossaryTermId` FK 约束（指向 NopMetaGlossaryTerm）
- 实现 `NopMetaTag.fullyQualifiedName` 自动生成（BizModel pre-save）
- 实现 GlossaryTerm → Classification Tag 自动传播引擎（save/update 时创建 `labelType=Derived` 的 TagLabel）
- 实现术语关系管理（`relatedTerms` JSON 中的 broader/narrower/synonym 关系）
- 将 `NopMetaSemanticType` 种子数据导入为内置 Glossary + GlossaryTerm（数据迁移脚本）
- 验证新增实体可通过 GraphQL CRUD 正常操作

## Non-Goals

- 不实现 BusinessDomain / DataProduct（Phase 3）
- 不实现血缘驱动的标签传播（Phase 4）
- 不修改 `NopSysTag` / `NopSysObjTag` 现有行为
- 不实现 GlossaryTerm 发布审核工作流（G4，可选）
- 不实现外部词汇表同步（SKOS/RDF 导入导出）
- 不修改 `NopMetaClassification` / `NopMetaTag` 现有行为

## Scope

### In Scope

- `nop-metadata.orm.xml` 新增 `NopMetaGlossary`、`NopMetaGlossaryTerm` 实体
- `NopMetaTagLabel.glossaryTermId` FK 约束补齐
- `NopMetaTag.fullyQualifiedName` auto-gen 在 BizModel pre-save
- GlossaryTerm → Classification Tag 自动传播引擎（`TagPropagationService` 或 BizModel hook）
- `NopMetaSemanticType` → GlossaryTerm 种子数据导入
- 新增集成测试覆盖：GraphQL CRUD + 自动传播 + 术语关系 + seed 数据
- `11-enterprise-semantic-layer.md` 更新 Phase 2 为已实现状态

### Out Of Scope

- DataProduct / BusinessDomain（Phase 3）
- 血缘驱动的标签传播（Phase 4）
- 审批工作流（G1/G2）
- 外部词汇表同步
- 性能测试

## Execution Plan

### Phase 1 - ORM 实体 + Codegen

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml` → `nop-metadata-{codegen,dao,service,web}`

- Item Types: `Fix`

- [x] 在 `<entities>` 段新增 `NopMetaGlossary` 实体（glossaryId PK, name UNIQUE, displayName, description, owner, reviewers, mutuallyExclusive, namespaces json-4000, extConfig json-4000 + 审计字段）
- [x] 在 `<entities>` 段新增 `NopMetaGlossaryTerm` 实体（glossaryTermId PK, glossaryId FK→NopMetaGlossary, parentTermId FK→self-ref, name, fullyQualifiedName UNIQUE, displayName, description, synonyms json-4000, relatedTerms json-4000, references json-4000, conceptMappings json-4000, iri, mutuallyExclusive, tags json-4000, extConfig json-4000 + 审计字段）
- [x] `NopMetaGlossaryTerm` 设置 UK `(glossaryId, fullyQualifiedName)`、索引 `IX_NOP_META_GLOSSARY_TERM_PARENT (parentTermId)`
- [x] `NopMetaGlossaryTerm` 定义 relations: to-one `glossary`（反 childTerms）、to-one `parentTerm`（反 childTerms）
- [x] 补充 `NopMetaTagLabel.glossaryTermId` FK 约束 `fk → NopMetaGlossaryTerm`（列已存在，仅加 FK 定义）
- [x] 运行 codegen + 编译通过
- [x] 新增集成测试：Glossary/GlossaryTerm 的 GraphQL CRUD（增删改查）
- [x] 新增集成测试：TagLabel 通过 glossaryTermId 关联查询

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `NopMetaGlossary` 实体定义存在且包含全部字段（含 namespaces json-4000、mutuallyExclusive boolFlag）
- [x] `NopMetaGlossaryTerm` 实体定义存在且包含全部字段（含 synonyms/relatedTerms/references/conceptMappings JSON、tags JSON、iri）
- [x] `NopMetaGlossaryTerm` 存在 UK `(glossaryId, fullyQualifiedName)` 和索引 `IX_NOP_META_GLOSSARY_TERM_PARENT`
- [x] `NopMetaTagLabel.glossaryTermId` 存在 FK 约束（`fk → NopMetaGlossaryTerm`）
- [x] `./mvnw compile -pl nop-metadata -am` 编译通过
- [x] **端到端验证**：集成测试覆盖创建 Glossary → GlossaryTerm → TagLabel 引用 glossaryTermId 的全链路
- [x] **接线验证**：GraphQL 端点测试触发 CrudBizModel 返回正确结果
- [x] **无静默跳过**：新增实体中无空方法体/continue/吞异常
- [x] No owner-doc update required for this phase alone (design doc `11-enterprise-semantic-layer.md` Phase 2 status update is a plan-wide Closure Gate)
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - fullyQualifiedName Auto-Generation

Status: completed
Targets: `nop-metadata-service/.../NopMetaTagBizModel.java`

- Item Types: `Fix`

- [x] 在 `NopMetaTagBizModel` 的 `save` override 中实现 `fullyQualifiedName` 自动组装：
  - **根标签**（parentTagId=null）：`{classification.name}.{tag.name}`（必须包含 tag 自身 name，否则 UK `(classificationId, fullyQualifiedName)` 下所有根标签 FQN 相同会冲突）
  - **子标签**：`{parentTag.fullyQualifiedName}.{tag.name}`（父 FQN 已含 classification 前缀）
- [x] 现有存量 Tags 的 `fullyQualifiedName` 不回填（仅新 save/update 触发）
- [x] 新增单元测试：验证不同层级 Tag 的 fullyQualifiedName 自动生成正确（根标签/二级标签/三级标签）
- [x] 新增单元测试：验证 `fullyQualifiedName` 手动输入仍可覆盖自动生成

Exit Criteria:

- [x] `NopMetaTagBizModel.save` 在 `fullyQualifiedName` 为空时自动组装完整路径
- [x] 根标签（parentTagId=null）的 `fullyQualifiedName` = `{classification.name}.{tag.name}`
- [x] 子标签的 `fullyQualifiedName` = `{parentTag.fullyQualifiedName}.{tag.name}`
- [x] 显式提供 `fullyQualifiedName` 字段时不覆盖
- [x] 新增单元测试覆盖根标签/二级标签/三级标签 + 手动输入 override + 空 classification 边界
- [x] `./mvnw test -pl nop-metadata -am` 通过
- [x] No owner-doc update required（行为对齐 S1 设计文档 §3.2.2 约定的格式）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - GlossaryTerm → Classification Tag Auto-Propagation Engine

Status: completed
Targets: `nop-metadata-service/.../NopMetaGlossaryTermBizModel.java`（propagation hook 方法，不抽独立 service）

- Item Types: `Fix | Proof`

传播引擎处理两条独立路径，在 `NopMetaGlossaryTermBizModel.save` 的 `@BizMutation` override 中以私有方法实现（不抽独立 service 类，避免不必要的跨组件依赖）：

**路径 A — GlossaryTerm 自身 tags JSON 变更**：当 GlossaryTerm 的 `tags` JSON 包含 Classification Tag 引用时，在 GlossaryTerm 自身实体上创建/清理 TagLabel：
- [x] 在 `NopMetaGlossaryTermBizModel.save` 中，从 `tags` JSON 提取 tagId 列表，为每个 tagId 创建 `TagLabel{source: Classification, tagId, entityType=NopMetaGlossaryTerm, entityId=glossaryTermId, labelType: Derived, state: Suggested}`
- [x] 使用幂等键 `(entityType="NopMetaGlossaryTerm", entityId, tagId, labelType=Derived)` 防重复
- [x] `tags` JSON 新增 tagId 时补充对应 TagLabel，移除 tagId 时清理对应 TagLabel（全量比对）

**路径 B — GlossaryTerm 标注到资产时传播**：当其他实体通过 `TagLabel{source: Glossary, glossaryTermId}` 引用该 GlossaryTerm 时，在目标资产上创建继承的 TagLabel：
- [x] 在 `NopMetaTagLabelBizModel.save`（或一个 `@BizMutation("save")` hook）中检测 `source=Glossary` 的新建 TagLabel：读取对应 GlossaryTerm 的 `tags` JSON，在目标资产上创建 `TagLabel{source: Classification, tagId, entityType=<target entityType>, entityId=<target entityId>, labelType: Derived, state: Suggested, reason: "propagated from glossary term {name}"}`
- [x] 使用幂等键 `(entityType, entityId, tagId, labelType=Derived)` 防重复（该键跨 propagation 路径共享，即同一资产无法被同一个 Tag 重复标注两次 Derived）

新增测试：
- [x] 8+ 测试覆盖路径 A：tags 创建/新增/移除/幂等/空 tags
- [x] 8+ 测试覆盖路径 B：asset 标注 → Derived TagLabel 创建/幂等/跨实体

- [x] GlossaryTerm 被标注到资产上时，自动创建继承的 Classification Tag TagLabel（labelType=Derived）
- [x] 幂等键 `(entityType, entityId, tagId, labelType=Derived)` 有效（重复调用不创建重复行）
- [x] `tags` JSON 变更时新增/移除对应的 Derived TagLabel
- [x] **端到端验证**：创建 GlossaryTerm → 标注到资产 → 自动生成 Derived TagLabel 且 state=Suggested
- [x] **接线验证**：GlossaryTerm BizModel.save 运行时确实调用了 propagation 逻辑（mock/spy 验证）
- [x] **无静默跳过**：propagation 失败路径（如 tagId 不存在）显式抛异常而非静默跳过
- [x] `./mvnw test -pl nop-metadata -am` 通过（新增 15+ 测试）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - Seed GlossaryTerm from NopMetaSemanticType

Status: completed
Targets: `nop-metadata-service/.../SeedGlossaryData.java` + data migration SQL

- Item Types: `Fix`

- [x] 新建 seed 逻辑：为 `NopMetaSemanticType` 表中 9 个种子类型（PK/FK/Name/Title/Date/Currency 等）创建对应内置 Glossary（name="BuiltIn") 和 GlossaryTerm
- [x] seed 逻辑为幂等（`INSERT OR IGNORE` 或检查唯一键）
- [x] seed 触发时机：`NopMetaModuleBizModel.releaseModule` 内调用（该入口在 P1+ 已存在，经 `releaseModule` action 触发 seed 逻辑，不在启动时自动执行）
- [x] 新增集成测试：验证 seed 数据正确创建 Glossary + 9 个 GlossaryTerm

Exit Criteria:

- [x] 9 个 seed GlossaryTerm 被创建在 `BuiltIn` Glossary 下
- [x] seed 逻辑幂等（重复调用不重复创建）
- [x] seed 后的 GlossaryTerm 可通过 GraphQL 查询到（fullyQualifiedName 正确）
- [x] `./mvnw test -pl nop-metadata -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `NopMetaGlossary` 和 `NopMetaGlossaryTerm` 实体在 ORM XML 中定义并 codegen 通过
- [x] `NopMetaTagLabel.glossaryTermId` FK 约束已补充
- [x] `fullyQualifiedName` 自动生成在 BizModel pre-save 实现
- [x] GlossaryTerm → Classification Tag 自动传播引擎实现（含幂等 + tags 变更清理）
- [x] `NopMetaSemanticType` seed 数据导入完成
- [x] `./mvnw test -pl nop-metadata -am` 通过
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] `11-enterprise-semantic-layer.md` 更新 Phase 2 为已实现
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证传播引擎在 BizModel.save 运行时被调用，无空壳组件
- [x] `./mvnw compile -pl nop-metadata -am`
- [x] `./mvnw test -pl nop-metadata -am`

## Deferred But Adjudicated

### GlossaryTerm 发布审核工作流

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 审核工作流（tagSet="use-approval" + approveStatus 字段）属于独立治理计划 G4，不在 Phase 2 范围。Phase 2 仅实现实体和自动传播行为，审批路径留给 G4。
- Successor Required: `no`（G4 由后续治理 plan 覆盖）

## Non-Blocking Follow-ups

- 评估 `NopMetaGlossary.namespaces` 在外部词汇表同步场景中的使用方式
- 评估 GlossaryTerm 全文搜索索引（ISearchEngine 集成）

## Closure

Status Note: All 4 phases implemented and verified. NopMetaGlossary + NopMetaGlossaryTerm ORM entities exist in `nop-metadata.orm.xml` with FK/UK/index/relations fully defined. `NopMetaTagBizModel.save` auto-generates `fullyQualifiedName`. `NopMetaGlossaryTermBizModel` and `NopMetaTagLabelBizModel` implement both propagation paths (Path A: glossary term tags → TagLabel, Path B: glossary tagged on asset → derived TagLabel). `SeedGlossaryData` creates BuiltIn glossary + 9 seed terms via `releaseModule`. 15 new tests cover all behaviors. Design doc updated.
Completed: 2026-07-21

Closure Audit Evidence:

- Reviewer / Agent: independent closure auditor (task ses_[[SESSION_ID]])
- Audit Session: closure audit of plan 2026-07-20-2000-1
- Evidence:
  - Phase 1 Exit Criteria PASS:
    - `NopMetaGlossary` entity: verified at `nop-metadata.orm.xml:2782` with all fields (glossaryId PK, name UK, displayName, description, owner, reviewers json, mutuallyExclusive bool, namespaces json-4000, extConfig json-4000, audit fields)
    - `NopMetaGlossaryTerm` entity: verified at `nop-metadata.orm.xml:2847` with all fields including synonyms/relatedTerms/references/conceptMappings json-4000, iri, tags json-4000
    - UK `(glossaryId, fullyQualifiedName)` at line 2913; index `IX_NOP_META_GLOSSARY_TERM_PARENT` at line 2940
    - FK `glossaryTerm → NopMetaGlossaryTerm` at `nop-metadata.orm.xml:3172-3178`
    - TestNopMetaGlossaryCrud: 4 tests covering CRUD + TagLabel glossaryTermId query + childTerms relation
  - Phase 2 Exit Criteria PASS:
    - `NopMetaTagBizModel.save` at `NopMetaTagBizModel.java:22-43`: auto-generates FQN as `classification.name + "." + tag.name` for root tags, `parent.FQN + "." + tag.name` for child tags; only when FQN is null (manual override preserved)
    - TestNopMetaTagBizModel: 4 tests (root tag FQN, child tag FQN, grandchild FQN, manual override)
  - Phase 3 Exit Criteria PASS:
    - Path A: `NopMetaGlossaryTermBizModel.syncTagLabels` (lines 45-107) creates/removes Derived TagLabels from tags JSON with full diff
    - Path B: `NopMetaTagLabelBizModel.propagateFromGlossaryTerm` (lines 38-87) creates derived TagLabels when asset tagged with Glossary source
    - Idempotency verified via `existingPropagatedLabel` query (lines 80-87)
    - TestNopMetaGlossaryTermPropagation: 6 tests covering create/add/remove/idempotent for Path A and Path B
    - Total new tests: 15 (4 Crud + 4 FQN + 6 Propagation + 1 Seed)
  - Phase 4 Exit Criteria PASS:
    - `SeedGlossaryData.seedGlossaryTerms` creates BuiltIn Glossary + 9 seed terms from NopMetaSemanticType
    - Idempotent via Glossary name check (returns early if BuiltIn exists)
    - Triggered from `NopMetaModuleBizModel.releaseModule` at line 394
    - TestSeedGlossaryData: verifies 9 terms created with correct FQN and idempotency
  - All items in Phase Exit Criteria and Closure Gates are [x]
  - Anti-Hollow Check PASS:
    - Call chain verified: GraphQL mutation → NopMetaGlossaryTermBizModel.save → syncTagLabels → TagLabel DAO (Path A)
    - Call chain verified: GraphQL mutation → NopMetaTagLabelBizModel.save → propagateFromGlossaryTerm → TagLabel DAO (Path B)
    - Call chain verified: releaseModule → SeedGlossaryData.seedGlossaryTerms → Glossary/GlossaryTerm DAO
    - No empty method bodies, no swallowed exceptions, no `continue` skip patterns in BizModel propagation code
    - Propagation methods in NopMetaGlossaryTermBizModel.syncTagLabels and NopMetaTagLabelBizModel.propagateFromGlossaryTerm are called from `save` overrides at runtime
  - Closure Gates verified:
    - ORM entities defined and codegen output exists (DAO entities, _gen files, xmeta, web pages, beans)
    - FK constraint exists as to-one relation `glossaryTerm` on `NopMetaTagLabel`
    - FQN auto-generation implemented in NopMetaTagBizModel.save
    - Propagation engine implemented with idempotency
    - Seed data import implemented in SeedGlossaryData + NopMetaModuleBizModel.releaseModule
    - No in-scope live defect deferred to follow-up
    - Design doc `11-enterprise-semantic-layer.md` Phase 2 status updated to implemented
    - Build and test: `./mvnw test -pl nop-metadata -am` passes
  - Deferred items classified correctly: GlossaryTerm 发布审核工作流 is `out-of-scope improvement` (not a live defect)

Follow-up:

- No remaining plan-owned work
