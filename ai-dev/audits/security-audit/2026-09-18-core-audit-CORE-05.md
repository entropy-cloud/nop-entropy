# CORE-05 — Tenant Isolation Enforcement Audit

> Mission: security-audit (roadmap item 1, deliverable CORE-05)
> Date: 2026-09-18. Auditor: ZCode session (plan 2026-09-17-0831, Phase 2).
> Scope: tenant fill/filter across EQL compile, SQL generation, load/save, session
> cache, DAO queries; every `runWithoutTenantId()` call site; native-SQL tenant
> posture.
> Contract: docs-for-ai/02-core-guides/tenant-model.md.

## Verified enforcement points (every documented entry point)

1. **EQL compile-time filter injection**: `EqlTransformVisitor`
   (nop-persistence/nop-orm-eql, EqlTransformVisitor.java:326-347) — when
   `entityModel.isUseTenant()`, a `tenantColumn = :param` predicate is compiled into
   the SQL AST with `TenantParamBuilder` supplying the value at execution;
   `useTenantModel` tracked at L667/961/1427.
2. **SQL generation tenant marker**: `GenSqlHelper`
   (nop-persistence/nop-orm/sql/GenSqlHelper.java:468) renders the tenant predicate
   as a provider-backed marker (`MARKER_TENANT_ID` →
   `ContextProvider.currentTenantId()`) — value bound as a parameter, not spliced.
3. **Load/save fill — fail-closed**: `EntityPersisterImpl.processTenantId`
   (EntityPersisterImpl.java:427-452), called from all five save/load queue sites
   (L225/254/293/307/327): uninitialized tenant prop is filled from context; empty
   tenant + null context → `ERR_ORM_MISSING_TENANT_ID` (throw, L442-444);
   entity tenant ≠ context tenant → `ERR_ORM_NOT_ALLOW_PROCESS_ENTITY_IN_OTHER_TENANT`
   (throw, L447-449). No silent cross-tenant write path.
4. **ID generation fill — fail-closed**: `OrmEntityIdGenerator.initTenantId`
   (OrmEntityIdGenerator.java:106-121): missing context tenant →
   `ERR_ORM_MISSING_TENANT_ID`; mismatch → cross-tenant error.
5. **Session cache partitioning**: `TenantOrmSessionEntityCache`
   (TenantOrmSessionEntityCache.java) maintains a per-tenant
   `Map<String, OrmSessionEntityCache>`; only `globalUniqueId` entities and empty
   tenant share the common cache. Entity-level cache key includes tenant
   (`EntityPersisterImpl.getCacheKey`, L568-583: `tenantId + ":" + orm_id()`).
6. **Error paths fail closed**: both tenant errors are thrown before any persistence
   action; no catch-and-continue wrapper exists on these paths.

## `runWithoutTenantId()` call sites — complete audit (4 main-code sites)

| # | Site | Justification | Verdict |
|---|---|---|---|
| 1 | nop-kernel/nop-core/.../ResourceComponentManager.java:114 | Component model loading for paths where `!ResourceTenantManager.supportTenant(path)` — non-tenant resources are global artifacts; tenant-aware paths keep context. | JUSTIFIED, scoped by explicit supportTenant gate. |
| 2 | nop-dyn/.../DynCodeGen.java:83 | `@SingleSession` init probes module metadata (`daoFor(NopDynModule.class).isUseTenant()`) — bootstrap probe must run outside tenant context. | JUSTIFIED (metadata probe; consumer module nop-dyn). |
| 3 | nop-dyn/.../DynOrmModelProvider.java:64 | Shared base ORM model load when tenant empty or `isInitializingTenant()` (re-entrancy guard for tenant model init). | JUSTIFIED (shared base model, guarded). |
| 4 | nop-dyn/.../DynOrmModelProvider.java:70 | Base model load before tenant overlay composition (`doGetSharedOrmModel`). | JUSTIFIED (base-model layer, not tenant data). |

No unjustified or unscoped escape-hatch use found. Sites 2-4 sit in consumer module
`nop-dyn` (outside the three audited groups and outside roadmap items 2-8 module
lists) — recorded for item 9 consolidation to assign a standing owner.

## Native-SQL tenant posture

- Framework-generated SQL (persist drivers, collection loaders) operates on tenant
  entities through EQL-compiled/AST-generated SQL that includes the tenant predicate
  (point 1-2 above).
- Hand-written native SQL (`IOrmSession.execute*`, sql-lib) bypasses compile-time
  tenant filtering **by documented design** (tenant-model.md: native SQL is the
  explicit escape hatch). In-scope framework code passes only metadata/constants/
  config-trust SQL through this seam (CORE-04 classification); application-level
  native-SQL tenant predicates are consumer-audit scope (items 4-8).

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-C5-1 | LOW | nop-dyn call sites 2-4 above | The three `runWithoutTenantId` sites in `nop-dyn` are individually justified (verified this audit) but `nop-dyn` is not in any roadmap item 2-8 module list — no successor audit owns its ongoing re-verification. | Consolidation (item 9) should attach nop-dyn to an audit item (suggest item 4 service-framework scope extension or a note in the fix-phase triage). |
| F-C5-2 | LOW | EntityPersisterImpl.java:432-436 | On load, an uninitialized tenant prop is `orm_internalSet` to the CURRENT context tenant before row materialization — correct for the proxy-load flow, but the fill relies on subsequent comparisons (L447) to catch mismatches; a direct-by-PK load of another tenant's row relies on the tenant predicate in generated SQL (present via EQL path) plus this post-check rather than a load-time SQL predicate assertion. | Watch-only residual: consider asserting the tenant predicate exists on generated load SQL for tenant entities in a focused test (optimization candidate for the fix phase test batch). |

## Explicit no-finding statements

- Every documented tenant entry point (EQL compile, SQL generation, save fill, load
  fill, id generation, session cache, entity cache key) enforces tenant context.
- Cross-tenant operations fail closed with dedicated error codes; no silent
  fall-through path found.
- All four `runWithoutTenantId` sites carry explicit, code-verifiable justifications.

## Adjudication (Phase 3 input)

- F-C5-1: `remediation-target` (LOW) — roadmap bookkeeping for item 9.
- F-C5-2: `watch-only residual` (Why Not Blocking Closure: enforcement exists via
  compile-time predicate + post-load comparison; the residual is a test-assertion
  nicety, not a live defect).

## Owner mapping

- F-C5-1 successor owner: roadmap item 9 consolidation.
- No overlap: item 2 owns auth-level tenant context propagation (login/impersonation);
  item 4 owns BizModel data-permission layer above the ORM. ORM-layer isolation is
  fully owned here.
