# DYN-01 — nop-dyn `runWithoutTenantId` Standing Re-Verification

> Mission: security-audit (roadmap item 4, deliverable DYN-01 — successor of
> CORE-05 F-C5-1, attached to item 4 per plan 2026-09-18-2340-6)
> Date: 2026-09-19. Auditor: ZCode session (plan 2026-09-18-2340-6, Phase 2).
> Severity labels: CRITICAL/HIGH/MEDIUM/LOW + P0-P3.
> Scope: re-verify that the three `nop-dyn` `runWithoutTenantId` call sites remain
> justified and scoped; confirm no new sites appeared since CORE-05.
> Method: repo-wide grep over nop-dyn main sources; full re-read of each site with
  surrounding guards; ORM-layer tenant mechanism owned by CORE-05 (link-only).

## Verified sites (live anchors, 2026-09-19)

| # | Site | Guard / justification | Verdict |
|---|------|----------------------|---------|
| 1 | nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:83 | `@SingleSession @PostConstruct init()` bootstrap probe of module metadata (`daoFor(NopDynModule.class).isUseTenant()`) — metadata read, not tenant business data; result only sets `useTenant` flag and registers tenant resource providers. | JUSTIFIED, scoped (unchanged since CORE-05 site 2) |
| 2 | nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynOrmModelProvider.java:64 | Shared base ORM model load only when tenant empty **or** `ResourceTenantManager.isInitializingTenant()` (re-entrancy guard for tenant model init). | JUSTIFIED, guarded (unchanged since CORE-05 site 3) |
| 3 | nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynOrmModelProvider.java:70 | Base-model layer load (`doGetSharedOrmModel`) before tenant overlay composition (`loadDynamicOrmModel` merges tenant modules on top). | JUSTIFIED, base-layer (unchanged since CORE-05 site 4) |

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| (none new) | — | — | All three sites remain justified and scoped; repo-wide grep over nop-dyn main sources returns exactly these 3 hits — no new escape-hatch use since CORE-05 (2026-09-18). | — |

## Explicit no-finding statements

- No `runWithoutTenantId` usage exists in nop-dyn outside the three audited sites
  (main + test grep; test usages are fixture setup, not production paths).
- None of the three sites reads or writes tenant-scoped business rows: site 1 reads
  module metadata; sites 2-3 load the shared (tenant-independent) base ORM model that
  tenant overlays are composed onto.
- Tenant data isolation itself (EQL predicate injection, save/load fill, cache
  partitioning) is CORE-05 scope and was verified fail-closed there — not re-audited
  here per Non-Goals.

## Adjudication (Phase 3 input)

- No remediation targets. CORE-05 F-C5-1's ask ("assign a standing owner") is
  satisfied by this deliverable: item 4 now owns the standing re-verification, and
  this report is the first executed instance. Successor cadence: re-grep on any
  future nop-dyn change (cheap check — suggest adding to the fix-phase triage
  checklist rather than a dedicated plan).

## Owner mapping

- Standing owner: roadmap item 4 (this deliverable); future instances ride the item 9
  consolidation cycle.
- No overlap: ORM-layer tenant enforcement remains CORE-05; auth-side tenant context
  propagation remains item 2 (AUTH-02/AUTH-05).
- Adjacent in-scope observation recorded under API-03 (F-API3-2): nop-dyn
  `NopDynSql.source` default-published posture — visibility, not tenant isolation.
