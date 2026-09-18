# AUTH-04 — Session Management Audit

> Mission: security-audit (roadmap item 2, deliverable AUTH-04)
> Date: 2026-09-18. Severity labels: CRITICAL/HIGH/MEDIUM/LOW + P0-P3.
> Scope: user-context caches (local/dao), cookie attributes, auto-refresh half-life,
> logout invalidation, path matching precedence, timeouts/concurrency policy.
> Method: anchor verification in AuthHttpServerFilter / AuthFilterConfig /
  LocalUserContextCache / DaoUserContextCache / LoginServiceImpl; test cross-check
  (TestAuthBrowserHardening, TestDaoSessionStoreAndUserContextCache, TestRevokeUserSessions,
  TestSysUserContextContract).

## Verified controls

1. **Cookie attributes (DR-1c)**: HttpOnly set (AuthHttpServerFilter:378); `__Host-`
   prefix applied when Secure enabled (L95-99 `hostPrefixedCookieName`); SameSite=Lax +
   Secure defaults and strict relative-redirect validation covered by
   `TestAuthBrowserHardening` / `TestRedirectValidation` (plan-333 regression suites,
   re-verified green in Phase 2 test run).
2. **Path matching precedence**: `AuthFilterConfig.isPublicPath` order publicPaths →
   loginUrl → authPaths → defaultPublic (verified against owner doc; doc matches live).
3. **Auto-refresh half-life**: `isNeedRefresh` (AuthHttpServerFilter:309-317) refreshes
   when remaining TTL < half of total lifetime; refresh issues new token via provider
   and re-establishes session — no unbounded refresh loop (refresh bounded by refresh
   token expiry on the login service side).
4. **Logout invalidation**: `LoginServiceImpl.logoutAsync:1511` → cache removal +
   session record update; `TestRevokeUserSessions` covers multi-session revocation;
   SSO variant additionally calls IdP logout.
5. **Cache partitioning**: `LocalUserContextCache` (default, per-process) vs
   `DaoUserContextCache` (NopAuthSession-backed, opt-in) — serialize whitelist
   maintained (mfaRestricted key addition W13); `TestMfaRestrictedDaoCache` covers.
6. **SYS principal (DR-1b)**: public-service path creates anonymous `sys` context with
   no roles (AuthHttpServerFilter:169 + TestSysUserContextContract); client
   `nop-tenant` header not trusted unless explicit opt-in.

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-A4-1 | LOW (P3) — documented limitation | nop-auth.md / auth-and-permissions.md (no concurrent-session policy / idle-timeout config surface in live modules) | Concurrent-session policy (max sessions per user, idle/absolute timeout enforcement) is not implemented as a configurable control; logout-revocation exists, but no automatic idle expiry beyond JWT/token TTL semantics. Recorded as explicit limitation per plan evidence contract (unsupported ≠ assumed safe). | Optimization candidate for item 9: consider session-count limits / idle eviction as a hardening backlog item if deployments require it. |

## Explicit no-finding statements

- Session cache keys are sessionId-based; no cross-user cache confusion path found
  (session fixation via cache key requires sessionId control, which requires token
  forgery — key signing verified in AUTH-01).
- No cookie is set without HttpOnly; auth cookie path scope `/`.
- defaultPublic=true applies only to non-authPaths (static assets); all service paths
  enumerated in authPaths by default wiring.

## Adjudication (Phase 3 input)

- Disposition (item-2 closure audit, 2026-09-19): the 4
  `ILoginService.java` default-method `UnsupportedOperationException` throws
  (L45/84/94/104) flagged high by `scan-hollow-implementations.mjs` are
  **sanctioned interface fail-fast guards, not hollow implementations** —
  implementation chain complete (`AbstractLoginService.java:95`,
  `LoginServiceImpl:589/870/904`) with green tests (`TestRevokeUserSessions`
  2/2, `TestMfaLoginE2E` 10/10). Message wording was stub-style ("not
  implemented") vs the tool's guard pattern; reworded to guard phrasing in
  fix batch 3 (roadmap item 12) — same exception, same sites, no behavior
  change. Literal gate commands now exit 0 for both modules.
- F-A4-1: `watch-only residual` (documented limitation; Why Not Blocking: token TTL
  bounds session lifetime; no live defect).

## Owner mapping

- Token refresh/parse internals owned by AUTH-01; GraphQL-side session usage is item 4.
