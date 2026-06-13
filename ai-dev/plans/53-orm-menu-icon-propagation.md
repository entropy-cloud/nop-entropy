# 53 ORM Menu Icon Propagation

> Plan Status: completed
> Last Reviewed: 2026-05-28
> Source: User request to propagate ORM `ext:icon` into generated `action-auth.xml`, plus live repo inspection of codegen templates and `nop-chaos-next` icon naming docs.

## Purpose

Propagate entity-level icon metadata from ORM models into generated menu auth resources, and land semantic Lucide-style icon names for `nop-auth` menu entries.

## Current Baseline

- ORM web auth generation currently hardcodes submenu icons to `ant-design:appstore-twotone` in `nop-kernel/nop-codegen/.../_{moduleName}.action-auth.xml.xgen`.
- Generated `objMeta` already carries selected explicit attributes such as `ext:model="orm"`, but it does not forward entity-level `ext:icon` from `model/*.orm.xml`.
- `action-auth` generation already reads `objMeta['ext:model']`, so once icon metadata reaches `objMeta`, the auth template can consume it without new runtime wiring.
- `nop-chaos-next` documents configuration icon names as kebab-case Lucide-oriented names, with runtime fallback support for FontAwesome aliases.

## Goals

- Preserve ORM entity `ext:icon` on generated object metadata.
- Generate submenu `icon` values from `objMeta['ext:icon']` with a stable default fallback.
- Assign semantic kebab-case icon names to the `nop-auth` entities that appear in generated menus.
- Verify the generation chain with focused build/test evidence and keep repo docs/logs in sync.

## Non-Goals

- Retrofitting semantic icons across every module in the repository.
- Changing the frontend icon renderer contract beyond using already supported kebab-case names.
- Redesigning the whole auth/menu model or replacing existing manual overrides in non-generated files.

## Scope

### In Scope

- Codegen templates that generate xmeta and action-auth files from ORM entities.
- `nop-auth/model/nop-auth.orm.xml` entity metadata for generated menu resources.
- Required owner-doc and daily-log updates for the changed generation convention.

### Out Of Scope

- Non-ORM menu generation templates unless needed for parity in the touched path.
- Frontend rendering changes in `nop-chaos-next`.
- Bulk icon cleanup in existing handwritten `*.action-auth.xml` overrides.

## Execution Plan

### Phase 1 - Propagate Entity Icon Metadata

Status: completed
Targets: `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/meta/.../_{entityModel.shortName}.xmeta.xgen`, `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/codegen/xlib/meta-gen.xlib`, `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm-web/src/main/resources/_vfs/{moduleId}/auth/_{moduleName}.action-auth.xml.xgen`

- Item Types: `Fix | Proof`

- [x] Update xmeta generation so entity-level `ext:icon` is emitted onto generated objMeta.
- [x] Update ORM action-auth generation to consume `objMeta['ext:icon']` with a default fallback.
- [x] Keep the change minimal and limited to the existing generation chain.

Exit Criteria:

- [x] Generated objMeta contract includes entity icon metadata for ORM entities.
- [x] Generated action-auth submenu icon resolves from objMeta instead of a hardcoded single value.
- [x] **端到端验证**：从 `nop-auth.orm.xml` 的 `ext:icon` 到生成的 `_nop-auth.action-auth.xml` 的路径已验证。
- [x] **接线验证**：确认 `action-auth` 模板运行时读取的是生成后 objMeta 中的 `ext:icon`。
- [x] **无静默跳过**：未配置 `ext:icon` 时走显式默认图标，而不是生成空值。
- [x] `docs-for-ai/` owner doc updated for the new generation convention.
- [x] `ai-dev/logs/` 对应日期条目已更新.

### Phase 2 - Land Semantic Icons For nop-auth

Status: completed
Targets: `nop-auth/model/nop-auth.orm.xml`, generated `nop-auth` auth outputs, related docs/logs

- Item Types: `Fix | Decision | Proof`

- [x] Assign semantic kebab-case icon names to the `nop-auth` entities that generate menu entries.
- [x] Regenerate affected outputs through the normal build path and inspect the generated auth resource file.
- [x] Record the icon naming decision against `nop-chaos-next` conventions.

Exit Criteria:

- [x] `nop-auth.orm.xml` stores semantic entity icon names instead of UI-library-specific class strings.
- [x] Generated `nop-auth` action-auth output shows the selected entity icons.
- [x] **端到端验证**：从 ORM model regeneration to generated auth resource output is verified.
- [x] **接线验证**：confirmed generated submenu resources now differ by entity icon where configured.
- [x] **无静默跳过**：entities without explicit icons still generate the default fallback icon.
- [x] `docs-for-ai/` owner doc updated if the model/codegen convention changed.
- [x] `ai-dev/logs/` 对应日期条目已更新.

## Deferred But Adjudicated

- No cross-module icon retrofit in this plan. Other modules can adopt `ext:icon` later without blocking this closure.

## Closure Gates

- [x] Phase 1 checklist fully complete.
- [x] Phase 2 checklist fully complete.
- [x] Focused verification command(s) for the touched generation path pass.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` passes after doc/log updates.
- [x] Independent closure audit completed by a separate subagent and recorded in the final report.

## Closure

Status Note: Plan completed — ORM ext:icon propagated through codegen to action-auth menus, semantic Lucide-style icon names assigned to nop-auth entities.

Closure Audit Evidence:

- Reviewer / Agent: automated (closure-verify)
- Evidence:
  - Phase 1: ORM entity ext:icon preserved on generated objMeta
  - Phase 2: action-auth submenu icons generated from objMeta['ext:icon'] with stable fallback
  - nop-auth entities assigned semantic kebab-case Lucide-oriented icon names
  - Generation chain verified with build/test evidence
