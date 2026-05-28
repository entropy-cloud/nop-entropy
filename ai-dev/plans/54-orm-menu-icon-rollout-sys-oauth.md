# 54 ORM Menu Icon Rollout For sys/oauth

> Plan Status: in progress
> Last Reviewed: 2026-05-28
> Source: Follow-up to Plan 53 after live repo scan found generated `_nop-sys.action-auth.xml` and `_nop-oauth.action-auth.xml` still using fallback `blocks` icons.
> Related: `ai-dev/plans/53-orm-menu-icon-propagation.md`

## Purpose

Roll the new ORM `ext:icon` convention into the next batch of built-in modules that still expose default generated menu icons.

## Current Baseline

- Plan 53 already landed the generation chain from ORM `entity ext:icon` into generated `xmeta` and generated `_*.action-auth.xml`.
- `nop-sys/nop-sys-web/.../_nop-sys.action-auth.xml` still contains many `icon="blocks"` submenu entries, indicating missing entity icon metadata.
- `nop-auth/nop-oauth/nop-oauth-web/.../_nop-oauth.action-auth.xml` also still contains `icon="blocks"` submenu entries.
- The owner doc for the convention already exists in `docs-for-ai/02-core-guides/model-first-development.md`; this plan mainly applies the convention to additional live modules.

## Goals

- Add semantic entity-level `ext:icon` metadata to the menu-generating ORM entities in `nop-sys` and `nop-oauth`.
- Regenerate affected outputs so the generated auth resources stop using the generic fallback icon.
- Verify the rollout with focused build/test evidence and updated logs.

## Non-Goals

- Repository-wide icon retrofit across every module.
- Frontend icon runtime changes.
- Hand-tuning handwritten auth override files beyond what regeneration already produces.

## Scope

### In Scope

- `nop-sys/model/nop-sys.orm.xml`
- `nop-auth/nop-oauth/model/nop-oauth.orm.xml`
- Generated `nop-sys` and `nop-oauth` xmeta/auth outputs
- Daily log and plan evidence for this rollout

### Out Of Scope

- Other modules still using fallback icons after this batch
- Additional doc contract changes unless the convention itself changes again

## Execution Plan

### Phase 1 - Land Semantic Icons In Models

Status: completed
Targets: `nop-sys/model/nop-sys.orm.xml`, `nop-auth/nop-oauth/model/nop-oauth.orm.xml`

- Item Types: `Fix | Decision`

- [x] Identify menu-generating entities in `nop-sys` and `nop-oauth` that still rely on fallback icons.
- [x] Add semantic kebab-case icon names to those entities via `ext:icon`.
- [x] Keep mappings aligned with supported `nop-chaos-next` naming conventions.

Exit Criteria:

- [x] `nop-sys.orm.xml` stores semantic icon names for entities that generate current admin menus.
- [x] `nop-oauth.orm.xml` stores semantic icon names for entities that generate current admin menus.
- [x] **端到端验证**：model source now contains the icon metadata needed for generation.
- [x] **接线验证**：the chosen entities correspond to entries currently showing fallback `blocks` icons in generated auth files.
- [x] **无静默跳过**：unconfigured entities, if any remain outside scope, are explicit scope exclusions rather than accidental omissions.
- [x] No owner-doc update required beyond existing Plan 53 convention docs.
- [ ] `ai-dev/logs/` 对应日期条目已更新.

### Phase 2 - Regenerate And Verify Outputs

Status: completed
Targets: `nop-sys` generated outputs, `nop-oauth` generated outputs, focused build/test evidence

- Item Types: `Fix | Proof`

- [x] Regenerate affected modules through the normal Maven path.
- [x] Inspect generated xmeta/auth outputs to confirm entity-specific icons replaced fallback `blocks` values.
- [x] Record focused verification results.

Exit Criteria:

- [x] Generated `nop-sys` action-auth output shows entity-specific submenu icons instead of generic fallback values.
- [x] Generated `nop-oauth` action-auth output shows entity-specific submenu icons instead of generic fallback values.
- [x] **端到端验证**：from ORM entity metadata to generated auth resource output is verified for both modules.
- [x] **接线验证**：generated xmeta contains `ext:icon`, and generated auth files consume those values.
- [x] **无静默跳过**：no touched generated menu entry still uses fallback `blocks` because of missing in-scope model metadata.
- [x] No owner-doc update required.
- [ ] `ai-dev/logs/` 对应日期条目已更新.

## Deferred But Adjudicated

- Other modules with fallback icons remain outside this rollout and can be handled in later batches.

## Closure Gates

- [ ] Phase 1 checklist fully complete.
- [ ] Phase 2 checklist fully complete.
- [x] Focused verification command(s) for the touched modules pass.
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` passes after log updates.
- [ ] Independent closure audit completed by a separate subagent and recorded in the final report.

## Closure

Status Note: Pending final log sync, doc link check, and independent closure audit.

Closure Audit Evidence:

- Reviewer / Agent: pending
- Evidence: pending

Follow-up:

- no remaining plan-owned work once closure audit confirms the regenerated sys/oauth outputs
