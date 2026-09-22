# API-02 — Action Auth Checking (Engine Side) Audit

> Mission: security-audit (roadmap item 4, deliverable API-02)
> Date: 2026-09-19. Auditor: ZCode session (plan 2026-09-18-2340-6, Phase 2).
> Severity labels: CRITICAL/HIGH/MEDIUM/LOW + P0-P3.
> Scope: `GraphQLActionAuthChecker` invocation points (document + RPC + subscription
> paths), `ActionAuthMeta` propagation from `@Auth`/unannotated biz actions,
> publicAccess semantics, fail behavior when the checker bean is absent, and engine
> enforcement when `nop.auth.enable-action-auth=true`. Checker-bean internals
> (`DefaultActionAuthChecker`) owned by AUTH-05 (link-only).
> Method: full read of GraphQLActionAuthChecker / GraphQLExecutor / GraphQLEngine /
> ReflectionBizModelBuilder; wiring verified in biz-defaults.beans.xml; repo-wide
  grep of every `setActionAuthChecker` production site.

## Verified controls (anchor-verified)

1. **Single front-door invocation, all five HTTP entries**: `GraphQLActionAuthChecker.
   INSTANCE.check(context)` runs before any fetcher on the document path
   (`GraphQLExecutor.executeAsync`, nop-service-framework/nop-graphql/nop-graphql-core/
   src/main/java/io/nop/graphql/core/engine/GraphQLExecutor.java:155) and the RPC path
   (`executeOneAsync` L72 — reached by `/r/`, `/p/`, `/jsonrpc` via `executeRpcAsync`;
   JsonRpcService.java:115-116), and on both subscription entries
   (`GraphQLEngine.subscribeGraphQL` GraphQLEngine.java:683, `subscribeRpc` L746).
   `/px/` intentionally does not check locally — authorization is delegated to the
   target service (API-04).
2. **Top-level (action) deny is fail-closed**: deny → `ERR_AUTH_NO_PERMISSION` throw;
   anonymous user on non-public action → `ERR_AUTH_NO_USER_CONTEXT` throw
   (GraphQLActionAuthChecker.java:90-106).
3. **Field-level deny**: default throw `ERR_AUTH_NO_PERMISSION_FOR_FIELD`;
   `auth.isSkipWhenNoAuth()` → silent field pruning from the selection bean
   (GraphQLActionAuthChecker.java:107-115, prune at L79) — nested sub-selections of a
   denied field are not fetched (recursion only on allow, L76-77).
4. **ActionAuthMeta always present on biz actions**: `ReflectionBizModelBuilder.
   buildActionField` nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/
   io/nop/graphql/core/reflection/ReflectionBizModelBuilder.java:360-366 — `@Auth`
   maps publicAccess/roles/permissions/skipWhenNoAuth; **unannotated actions get
   publicAccess=false + derived permission `{bizObj}:{opType}|{bizObj}:{name}`**, i.e.
   "not annotated" is not "public".
5. **publicAccess semantics**: `isAllowAccess` (GraphQLActionAuthChecker.java:118-145):
   auth==null → allow (meta-less biz objects); publicAccess → allow (no user context
   needed); otherwise role match or permission-set satisfaction required; anonymous
   (null userContext) → deny.
6. **Engine-side gating verified when enabled**: `enableActionAuth` is wired
   `@cfg:nop.auth.enable-action-auth|false` on bean `nopGraphQLEngine`
   (nop-service-framework/nop-biz/src/main/resources/_vfs/nop/biz/beans/biz-defaults.
   beans.xml:13); when true, the injected `IActionAuthChecker` is propagated to every
   execution context (`GraphQLEngine.newGraphQLContextFromContext` L362-366) and
   enforced at the points in (1). The only production setter of the context checker is
   this engine site (repo-wide grep; child-context copy in ServiceContextImpl.java:124
   preserves it across async boundaries).
7. **enable-action-auth default false**: engine-side confirmation of the opt-in
   authorization model already classified by AUTH-05 as a deployment-configuration
   constraint per plan-328 precedent (no re-litigation here; engine side agrees: with
   the flag off, `GraphQLActionAuthChecker.check` is a no-op because the context
   checker is null, GraphQLActionAuthChecker.java:37-39).

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-API2-1 | LOW (P3) | GraphQLActionAuthChecker.java:37-39 & 118-124; GraphQLEngine.java:362-366 | **Fail-open on checker-bean absence even when the flag is on**: with `nop.auth.enable-action-auth=true` but no `IActionAuthChecker` bean registered (e.g., nop-auth-service absent from the deployment), the checker stays null and ALL action/field authorization is silently skipped — no warning, no startup validation. Not a bypass of a configured control (the checker module is genuinely absent), but an operator-expectation hazard on misconfiguration. Fix handoff: fail-loud option — log WARN at engine init or fail bean initialization when `enableActionAuth && actionAuthChecker == null`. | Fix-phase candidate: startup guard in `GraphQLEngine.init()`; item 9 deployment checklist: verify checker bean presence when enabling action auth. |

## Explicit no-finding statements

- No execution path bypasses the checker when a checker is present and the flag is
  on: all five entries funnel through the three invocation points (verified item 1);
  the internal `fetchResult`/`fetchResultWithSelection` helper (GraphQLEngine.java:
  846-861) post-processes already-authorized in-process results and validates
  selections against published fields (RpcSelectionSetBuilder) — not a client entry.
- No code path treats an unannotated action as public (publicAccess=false default
  with derived permission, verified control 4).
- Field-level pruning never returns a denied field's data: prune happens on the
  selection bean before fetch, and nested selections recurse only on allow.
- No double ownership: checker semantics/defaults (skip-check-for-admin,
  enable-action-auth classification) remain AUTH-05; JWT/token validation remains
  AUTH-01; this report owns only the engine invocation/propagation layer.

## Adjudication (Phase 3 input)

- F-API2-1: `remediation-target` (LOW/P3) — defense-in-depth startup guard; safe to
  batch into the fix phase or the item 9 deployment checklist.

## Owner mapping

- F-API2-1 successor owner: fix-phase triage (engine module nop-graphql-core) with
  item 9 checklist entry.
- **Coverage gap (plan-mandated record)**: engine-side action-auth assertion
  (`enableActionAuth=true` + checker → top-level deny / field prune) has NO dedicated
  graphql-core or nop-biz test (grep `setEnableActionAuth`/`newGraphQLContextFromContext`
  in tests: zero hits). Per the plan evidence contract this is recorded as an explicit
  unsatisfied coverage item, not as passing. The auth-module E2E suites (AUTH-03,
  engine-routed) provide consumer evidence only.
