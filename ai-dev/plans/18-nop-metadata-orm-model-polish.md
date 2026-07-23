# 18 nop-metadata ORM Model Polish & Code Cleanup

> Plan Status: active
> Execution Order: 3
> Last Reviewed: 2026-07-23
> Source:
>   - `ai-dev/audits/2026-07-23-0714-multi-audit-nop-metadata/04-orm-model.md` (维度04-001~007)
>   - `ai-dev/audits/2026-07-23-0714-open-audit-nop-metadata.md` (NF-03, 09-07)
>   - `ai-dev/audits/2026-07-23-0714-multi-audit-nop-metadata/summary.md` (维度09-07)

## Purpose

Polish the ORM model definition for consistency and correctness, eliminate unused/empty code artifacts, and standardize ErrorCode naming conventions across the nop-metadata module.

## Current Baseline

- **维度04-001** (P2): `NopMetaSemanticType` has redundant non-unique index `IX_NOP_META_SEM_TYPE_NAME` on the same column as `UK_NOP_META_SEM_TYPE_NAME` (unique key).
- **维度04-002** (P2): `NopMetaDataSource` missing index on `status` column — a high-frequency filter column.
- **维度04-003** (P2): `NopMetaDataProduct` comment says "报表定义（预留）" instead of "数据产品（Data Product）定义" — copied from another entity.
- **维度04-004** (P3): `NopMetaQualityCheckpoint.extConfig` missing explicit `stdDomain="json"` declaration (other entities have it).
- **维度04-005** (P3): Three dicts (`meta/checkpoint-action-type`, `meta/reconciliation-status`, `meta/quality-trend-direction`) defined in `<dicts>` but never referenced by any column's `ext:dict`.
- **维度04-006** (P3): Dict values use inconsistent case styles across entities (UPPERCASE, lowercase, PascalCase, kebab-case).
- **维度04-007** (P3): `NopMetaModule.baseModuleId` self-referencing cascade behavior lacks design-intent documentation comment.
- **NF-03** (P3): `NopMetadataConfigs` class exists but is empty — no methods, no fields, no purpose.
- **09-07** (P2): ErrorCode names systemically use hyphens (`-`) instead of dots (`.`) as sub-domain separators throughout nop-metadata ErrorCode constants.

## Goals

- Remove redundant database index.
- Add missing index on high-frequency filter column.
- Fix misleading entity comments.
- Align column-level domain declarations for consistency.
- Clean up unused dict definitions or document why they exist.
- Standardize dict value case styles to prevent runtime comparison bugs.
- Document cascade behavior for self-referencing entities.
- Remove empty `NopMetadataConfigs` class.
- Standardize ErrorCode naming separator (hyphens → dots) or update docs to acknowledge the convention divergence.

## Non-Goals

- Not adding new indexes beyond the identified missing ones.
- Not restructuring ORM model relationships or adding new entities.
- Not changing Java code behavior outside ErrorCode constant renames and empty class removal.
- Not touching `_dao.beans.xml` or workflow resources (see `16-nop-metadata-build-infrastructure-fixes.md`).

## Scope

### In Scope

- ORM model file: `nop-metadata/model/nop-metadata.orm.xml` (维度04-001~007)
- Empty class: `NopMetadataConfigs.java` (NF-03)
- ErrorCode naming: all `NopMetadataErrors.java` and domain-specific ErrorCode files (09-07)

### Out Of Scope

- BizModel method fixes (see `17-nop-metadata-bizmodel-compliance-remediation.md`)
- Build infrastructure issues (see `16-nop-metadata-build-infrastructure-fixes.md`)
- DTO migration (see `307-nop-metadata-dto-migration-data-auth.md`)
- Interface completeness (see `308-nop-metadata-interface-contract-gaps.md`)

## Execution Plan

### Phase 1 — ORM Model Index & Comment Fixes (维度04-001~004)

Status: planned
Targets: `nop-metadata/model/nop-metadata.orm.xml`

- Item Types: `Fix`

- [ ] Remove redundant index `IX_NOP_META_SEM_TYPE_NAME` from `NopMetaSemanticType` (维度04-001)
- [ ] Add index `IX_NOP_META_DATA_SOURCE_STATUS` on `status` column for `NopMetaDataSource` (维度04-002)
- [ ] Fix `NopMetaDataProduct` comment: replace "报表定义（预留）" with "数据产品（Data Product）定义" (维度04-003)
- [ ] Add `stdDomain="json"` to `NopMetaQualityCheckpoint.extConfig` column declaration (维度04-004)

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 冗余索引已删除
- [ ] 缺失索引已添加
- [ ] 注释已更正
- [ ] `stdDomain` 声明已对齐
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] `No owner-doc update required` (ORM 模型是源码，不是文档)
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — Dict & Documentation Cleanup (维度04-005~007)

Status: planned
Targets: `nop-metadata/model/nop-metadata.orm.xml`

- Item Types: `Fix`, `Decision`

- [ ] **维度04-005**: For each of the 3 unreferenced dicts:
  - Determine if they are referenced from Java code via `DictProvider` → if so, keep and document; if not, remove from ORM model
  - Document the decision in the model comment
- [ ] **维度04-006**: Standardize dict value case styles:
  - Choose a convention (prefer `UPPER_SNAKE_CASE` or `lower_case`)
  - Normalize all dict values across all entities in the ORM model
  - Verify consistency with existing code that references dict values (update code `equals()` calls if needed)
- [ ] **维度04-007**: Add XML comment on `NopMetaModule.baseModuleId` explaining why `cascade-delete` is not set and the design intent for the self-referencing relationship

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 未引用 dict 已处理（移除或保留+注释说明）
- [ ] dict value 大小写风格已统一
- [ ] 自引用级联行为已有文档注释
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] `./mvnw test -pl nop-metadata -am` 通过（验证代码引用 dict 值的一致性）
- [ ] **无静默跳过**: dict 值变更后，所有代码级引用已同步更新；无残留的大小写敏感 `equals()` 调用
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — Empty Class & ErrorCode Naming Cleanup (NF-03, 09-07)

Status: planned
Targets: `NopMetadataConfigs.java`, `NopMetadataErrors.java` and domain-specific ErrorCode files

- Item Types: `Fix`

- [ ] **NF-03**: Delete empty `NopMetadataConfigs` class (verify no imports reference it)
- [ ] **09-07**: Decide on ErrorCode naming strategy:
  - Option A: Standardize to dot-separated (`nop-metadata.err.xxx.yyy`) per platform convention
  - Option B: Update `docs-for-ai/` to acknowledge hyphen-separated as an allowed convention for nop-metadata
  - Execute chosen option
- [ ] Update `docs-for-ai/02-core-guides/error-handling.md` or module owner docs if Option B

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `NopMetadataConfigs` 已删除（或确认无引用后删除）
- [ ] ErrorCode 命名约定已裁定且执行（Option A 或 B）
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] `./mvnw test -pl nop-metadata -am` 通过
- [ ] 如果选择了 Option B：`docs-for-ai/02-core-guides/error-handling.md` 已更新
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 所有 in-scope ORM 模型一致性问题已修复
- [ ] 冗余索引已删除，缺失索引已添加
- [ ] dict 定义已清理（未引用项已处理，大小写已统一）
- [ ] ErrorCode 命名约定已裁定并执行
- [ ] 空 `NopMetadataConfigs` 已移除
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**: closure audit 已验证无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile -pl nop-metadata -am`
- [ ] `./mvnw test -pl nop-metadata -am`

## Deferred But Adjudicated

None.

## Non-Blocking Follow-ups

- 其他维度（05, 09, 11, 16）的发现将在 future audit cycles 中处理

## Closure

Status Note:
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- No remaining plan-owned work.
