# AUTH-06 — SSO/OAuth Integration Audit

> Mission: security-audit (roadmap item 2, deliverable AUTH-06)
> Date: 2026-09-18. Severity labels: CRITICAL/HIGH/MEDIUM/LOW + P0-P3.
> Scope: `nop-auth-sso` (OAuthLoginServiceImpl, JWKPublicKeyLocator, redirect/state),
  MFA SPI wiring, `generateVerifyCode` hollow-scan watch item; `nop-oauth` module-group
  server surface inventory.
> Method: source read of OAuthLoginServiceImpl (login/logout/token paths, watch item),
  JWKPublicKeyLocator resolution/fail behavior; parseToken overload comparison;
  TestOAuthLoginServiceImplContract cross-check; nop-oauth module enumeration.

## Verified controls

1. **MFA SPI wiring (W13)**: `OAuthLoginServiceImpl` injects `IMfaLoginPolicyService`
   (`@Inject @Nullable`) and routes SSO logins through the same three-branch decision
   as password login (null-bean pass / challenge / restricted) — "永不拦截" legacy gap
   closed per nop-auth.md migration note; `MfaLoginPolicyServiceImpl` uses local role
   snapshot only.
2. **JWKPublicKeyLocator**: keys resolved from configured IdP JWKS URL with TTL cache;
   fetch failure → null key → signature verification fails (fail-closed parse).
3. **generateVerifyCode watch item — RESOLVED**: previous hollow-scan finding (bare
   UnsupportedOperationException) is fixed: now throws
   `NopException(ERR_AUTH_SSO_NOT_IMPL)` with documented contract rationale (no legal
   caller in SSO login-service context). Disposition: resolved-by-fix, verified at
   OAuthLoginServiceImpl.java:230-235.
4. **Redirect/state**: strict relative-redirect validation + allowed-prefix allowlist
   owned by the shared browser-boundary layer (plan 333, TestRedirectValidation) —
   reused by SSO callback handling; re-verified green.

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-A6-1 | MEDIUM (P2) | nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/login/OAuthLoginServiceImpl.java:189 (`logoutAsync` → `parseAuthToken(request.getAccessToken())`) + :238-241 (2-arg `JwtHelper.parseToken`) vs nop-service-framework/nop-biz-auth-core/.../jwt/JwtHelper.java:80-102 (2-arg overload: signature + expiry only) and :116+ (full overload: + iss/aud/typ) | `logoutAsync` consumes a **client-supplied** access token through the 2-arg parse overload that validates only signature and expiry — no issuer/audience/`typ` validation. Any token signed by the same IdP key for ANY audience/purpose (e.g. another relying party's token, or an IdP refresh/code token from this deployment) is accepted on this seam. Current impact is bounded: the parsed identity feeds logout (local session invalidation by token-claimed sessionId + IdP logout call), not authentication/authorization grant — an attacker still needs an IdP-signed token containing the victim's session claim to force logout (DoS-class). Violates JWT best practice (RFC 8725 §3.11/3.12 aud+iss validation) on an exposed request path. | Phase 3 fix: switch to the full `parseToken` overload with SsoConfig-driven expected issuer/audience (already configurable fields pattern) and reject non-access `typ`; alternatively parse only server-side tokens. |
| F-A6-2 | LOW (P3) — coverage gap | nop-auth/nop-oauth/ (module group: api/app/codegen/dao/meta/service/web) | `nop-oauth` server surface inventory: only entity persistence + config + generated CRUD for the three OAuth tables (NopOauthAuthorization / NopOauthRegisteredClient / NopOauthAuthorizationConsent) exists in-repo; no authorization/token/consent protocol endpoints are implemented (NopOauthApplication is a boot app). Disposition: OAuth2 server protocol is NOT implemented — recorded as explicit unsupported surface (not assumed safe); deployments embedding this module expose standard generated CRUD (admin-gated via action-auth model) rather than an OAuth flow. | Watch-only: if/when OAuth2 server endpoints are implemented, a dedicated audit is required before production use (item 9 triage note). |

## Explicit no-finding statements

- SSO client login path itself (authorization-code exchange over TLS, server-to-server)
  does not accept client-minted tokens for authentication — the weak seam is
  logout-only (F-A6-1).
- JWKS resolution is fail-closed (no key → verification failure).
- State/redirect handling inherits the hardened shared layer (re-verified).

## Adjudication (Phase 3 input)

- F-A6-1: `remediation-target` (MEDIUM) — **FIXED 2026-09-18 in fix batch 2** (plan 2026-09-18-2320-5): `OAuthLoginServiceImpl.parseAuthToken` now uses the full parseToken overload with `SsoConfig.getIssuer()`/new `getAudience()` (null→skip), no legacy path; regression tests in `TestOAuthLoginServiceImplContract` (wrong-issuer rejected ERR_JWT_INVALID_ISSUER; matching/null-config semantics locked).
- F-A6-2: `watch-only residual` (unsupported surface; successor note for item 9).

## Owner mapping

- F-A6-1 successor: item 9 consolidation → fix batch. Distinct from AUTH-01 (provider
  internals clean) and from item 7 (network transport) — no double ownership.
- `generateVerifyCode` resolution cross-references the A2/A3 adjudication records
  (hollow-scan watch list) — closed, not double-owned.
