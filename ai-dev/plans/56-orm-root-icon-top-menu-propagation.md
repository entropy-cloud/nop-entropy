# 56 ORM Root Icon Top Menu Propagation

> Plan Status: completed
> Last Reviewed: 2026-05-28
> Source: User requested that the ORM root node also support `ext:icon` so generated module root menus such as `nop-auth` TOPM receive semantic icons instead of the hard-coded fallback.
> Related: `ai-dev/plans/53-orm-menu-icon-propagation.md`, `ai-dev/plans/55-orm-xml-icon-rollout-and-checker.md`

## Purpose

Land root-level ORM icon propagation from source `model/*.orm.xml` into generated ORM web TOPM resources, and verify the behavior on `nop-auth` where the module root menu should no longer use the hard-coded default icon.

## Current Baseline

- Entity-level `ext:icon` already propagates through `model/*.orm.xml -> xmeta -> _*.action-auth.xml` for generated SUBM resources.
- The ORM web auth template still hard-codes the generated TOPM icon as `ion:grid-outline` in `nop-kernel/nop-codegen/.../_{moduleName}.action-auth.xml.xgen`.
- The live ORM web auth template only shows `metaInfos` / `objMeta` usage; the plan must therefore explicitly verify whether root ORM metadata is already present in template scope and, if not, land the required wiring before consuming root `ext:icon`.
- Source ORM files such as `nop-auth/model/nop-auth.orm.xml` do not yet set `ext:icon` on the root `<orm>` node.
- `nop-auth/nop-auth-web/.../_nop-auth.action-auth.xml` therefore still emits `icon="ion:grid-outline"` for the generated TOPM root resource.

## Goals

- Allow generated ORM TOPM resources to read a semantic icon from the root `<orm>` node `ext:icon` when present.
- Add a root `ext:icon` to `nop-auth/model/nop-auth.orm.xml` and verify the generated TOPM menu uses it.
- Update owner docs and daily log for the new root-level convention.

## Non-Goals

- Repository-wide rollout of root `ext:icon` to every ORM module in the same change.
- Changing generated menu display names, route paths, or non-icon auth behavior.
- Hand-editing generated `*.action-auth.xml` outputs.

## Scope

### In Scope

- `nop-kernel/nop-codegen` ORM web auth template for generated TOPM resources.
- Any immediate template-context wiring required to expose root ORM metadata to that generated TOPM template.
- `nop-auth/model/nop-auth.orm.xml` root `<orm>` metadata.
- Focused verification on regenerated `nop-auth` auth output.
- Owner doc and daily log updates required by the new root-level contract.

### Out Of Scope

- Manual auth overlays such as non-generated `nop-auth.action-auth.xml` files.
- Generated SUBM icon behavior already covered by Plan 53.
- Additional module root icon rollout beyond evidence needed for `nop-auth`.

## Execution Plan

### Phase 1 - Root Icon Propagation And Verification

Status: completed
Targets: `nop-kernel/nop-codegen`, `nop-auth/model/nop-auth.orm.xml`, `docs-for-ai/02-core-guides/model-first-development.md`, `ai-dev/logs/2026/05-28.md`

- Item Types: `Fix | Decision | Proof`

- [x] Update the generated ORM web TOPM template so it prefers the root ORM model `ext:icon` and falls back only when the root icon is absent.
- [x] Replace direct ORM-model loading in `orm-web` with a `*-meta` generated module-level meta file so `*-web` only depends on `*-meta` outputs.
- [x] Add a semantic root `ext:icon` to `nop-auth/model/nop-auth.orm.xml`.
- [x] Regenerate the affected `nop-auth` outputs via Maven so the generated TOPM menu reflects the source ORM root icon.
- [x] Update the owner doc to state that root `<orm ext:icon>` drives the generated TOPM icon while entity `ext:icon` continues to drive generated SUBM icons.
- [x] Update the daily log with the new propagation rule and verification evidence.

Exit Criteria:

- [x] Generated ORM TOPM resources use root `<orm ext:icon>` when present and retain the existing fallback when absent.
- [x] The ORM web auth generation path proves root ORM metadata is available to the TOPM template through `*-meta` generated `module-meta.json`.
- [x] `nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/auth/_nop-auth.action-auth.xml` no longer emits `icon="ion:grid-outline"` for `test-orm-nop-auth` and instead uses the root ORM icon.
- [x] **端到端验证**：`nop-auth/model/nop-auth.orm.xml -> nop-auth-meta/.../_module-meta.json -> nop-auth-web generated _nop-auth.action-auth.xml` is observed after regeneration with the root TOPM icon updated from the source ORM root metadata.
- [x] **接线验证**：the runtime generation path proves the ORM web template reads module-level meta from `loadDeltaJson("/{moduleId}/model/module-meta.json")` rather than a hard-coded constant or direct DAO-side ORM dependency.
- [x] **无静默跳过**：after regeneration, `nop-rule` still has no root `ext:icon`, `nop-rule-meta/.../_module-meta.json` emits `icon: null`, and `nop-rule-web/.../_nop-rule.action-auth.xml` continues to emit fallback `ion:grid-outline`.
- [x] `docs-for-ai/02-core-guides/model-first-development.md` updated for the new root-level contract.
- [x] `ai-dev/logs/2026/05-28.md` updated.

## Closure Gates

- [x] Template propagation for root `<orm ext:icon>` is landed.
- [x] `nop-auth` source ORM root icon is landed and verified in generated TOPM output.
- [x] Any in-scope template-context wiring required for root ORM metadata exposure is landed and verified.
- [x] Focused `./mvnw install -pl nop-auth/nop-auth-meta,nop-auth/nop-auth-web,nop-rule/nop-rule-meta,nop-rule/nop-rule-web -am -DskipTests -T 1C` verification passes.
- [x] Focused `./mvnw test -pl nop-auth -am -T 1C` passes, or an explicitly justified equivalent repo-required test baseline is recorded.
- [x] Owner doc sync for the new root icon contract is landed.
- [x] No in-scope defect or contract drift is deferred to follow-up.
- [x] Textual consistency audit completed across `Plan Status`, phase `Status`, phase `Exit Criteria`, `Closure Gates`, and `ai-dev/logs/2026/05-28.md` before closure.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` passes after plan/log/doc updates.
- [x] Independent closure audit completed by a separate subagent and recorded in the final report.

## Closure

Status Note: Root `<orm ext:icon>` now propagates through `*-meta` generated `module-meta.json` into ORM web TOPM resources. `nop-auth` emits `shield`; `nop-rule` retains fallback `ion:grid-outline`.

Closure Audit Evidence:

- Reviewer / Agent: `ses_191215ba6ffeEKo8hIemq7W8ru` (first audit: implementation PASS, closure evidence incomplete); `ses_1911d69edffeP0wvZVh8u7p0jt` (second audit: PASS)
- Evidence: second independent audit verified live `module-meta` generation, `orm-web` consumption, `nop-auth` shield propagation, `nop-rule` fallback retention, and install/test/doc-check/log consistency.

Follow-up:

- No code follow-up required for the propagation path. Future modules can opt in by setting root `<orm ext:icon>`.
