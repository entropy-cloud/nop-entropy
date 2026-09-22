# AUTH-05 — RBAC Bypass Risk Audit

> Mission: security-audit (roadmap item 2, deliverable AUTH-05)
> Date: 2026-09-18. Severity labels: CRITICAL/HIGH/MEDIUM/LOW + P0-P3.
> Scope: action-auth default-off implications, admin skip-check single source,
  servicePublic SYS principal scope, data-auth fail-closed behavior.
> Method: full read of DefaultActionAuthChecker / DefaultDataAuthChecker core paths;
  NopAuthConfigs anchors; auth-service.beans.xml wiring; TestSysUserContextContract /
  TestCheckFieldAuth cross-check.

## Verified controls

1. **skip-check-for-admin single source of truth**: `DefaultActionAuthChecker` reads
   `CFG_AUTH_SKIP_CHECK_FOR_ADMIN` (NopAuthConfigs.java:77, default `false`) directly —
   no `@InjectValue` inline fallback (class Javadoc documents the H-2 history: dual
   defaults previously caused admin bypass; unified in plan 333). Admin/nop-admin roles
   skip permission checks ONLY when the flag is explicitly enabled.
2. **enable-action-auth default false**: wired at auth-service.beans.xml:11
   (`@cfg:nop.auth.enable-action-auth|false`) consumed by SiteMapProviderImpl:65/222
   (menu filtering + permission mapping). Blast radius: with action auth off,
   `isPermitted` succeeds for authenticated users — the platform's authorization model
   is opt-in. **Verified live default; classified deployment-configuration constraint
   per plan-328 precedent** (roadmap known gap #1) — documented, not a framework
   defect; production must enable.
3. **SYS principal cannot satisfy admin gates**: anonymous `sys` context carries no
   roles (TestSysUserContextContract asserts the contract); with skip-check=false
   (default), `sys` cannot pass `isPermitted` for permission-gated resources even when
   action auth is enabled; and with action auth disabled, servicePublic exposure is the
   deployment's explicit choice.
4. **Data-auth fail-closed**: `DefaultDataAuthChecker.isPermitted` — no rules for
   bizObj → allow (documented); rules exist but no matching role → DENY (L187-188);
   `getFilter` in the same state throws `ERR_AUTH_NO_DATA_AUTH` (L205-206). Matches
   owner-doc table exactly.
5. **Role matching by roleId**: SiteMapProvider.containsRole matches role IDs (doc
   warning about roleId-vs-name verified consistent with code).

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| (none new) | — | — | Known gaps (enable-action-auth=false, skip-check-for-admin=false) verified live and classified as deployment constraints per plan-328. No bypass beyond documented opt-in model found. | Deployment checklist entries for item 9 (enable action-auth in production; keep skip-check-for-admin=false). |

## Explicit no-finding statements

- No code path grants admin privileges without either explicit config
  (skip-check=true) or admin role membership.
- Data-auth checker never silently falls through to allow when rules exist and no
  role matches (fail-closed verified).
- SYS principal scope bounded (no roles, no tenant header trust).

## Adjudication (Phase 3 input)

- Both known gaps: `adjudicated deployment constraints` (plan-328 precedent), with
  pointers recorded for item 9 deployment hardening checklist.

## Owner mapping

- GraphQL engine's `GraphQLActionAuthChecker` invocation path is item 4 (API-02);
  ORM-layer data-auth filter provider is item 1/CORE-05-adjacent (link-only). Auth-module
  checkers fully owned here.
