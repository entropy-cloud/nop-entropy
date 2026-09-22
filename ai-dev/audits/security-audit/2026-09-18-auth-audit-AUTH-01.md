# AUTH-01 — JWT Token Lifecycle Controls Audit

> Mission: security-audit (roadmap item 2, deliverable AUTH-01)
> Date: 2026-09-18. Auditor: ZCode session (plan 2026-09-17-1958, Phase 2).
> Severity labels: CRITICAL/HIGH/MEDIUM/LOW and P0/P1/P2/P3 (P0=CRITICAL … P3=LOW).
> Scope: `nop-biz-auth-core` JWT issuance/parsing, purpose isolation, key handling,
> legacy grace window, enc-key empty default; consumer `AuthHttpServerFilter.parseAuthToken`.
> Method: full read of JwtAuthTokenProvider + JwtHelper parseToken overloads; anchor
> verification in AuthHttpServerFilter; cross-check TestJwtAuthTokenProvider / TestJwtHelper.

## Verified controls

1. **Purpose-isolated keys (DR-1a/H-1)**: `JwtAuthTokenProvider.ensureKeys()` derives
   three keys via `JwtHelper.hmacKey(encKey, salt)` with distinct salts
   (`nop-access`/`nop-refresh`/`nop-code`), KID-tagged (L30-32, L87-101). All three
   consumers (`parseAuthToken`/`parseRefreshToken`/`parseAccessCode`, L130-148) use the
   full `JwtHelper.parseToken` overload that validates signature + expiry + issuer +
   audience + `typ` per purpose — cross-purpose token reuse is rejected.
2. **Legacy grace window bounded**: `legacyTokenGraceSeconds` field defaults to `0`
   (L38, no initializer override anywhere in default wiring); legacy single-key tokens
   are only accepted within the window and only when `encKey` is configured
   (`legacyKey` derived only in the non-empty branch, L99).
3. **Key rotation/retirement semantics**: keys derived deterministically from
   `encKey` + per-purpose salt — rotation = changing `encKey` (all purposes rotate
   together, old tokens fail signature). No runtime rotation API exists; retirement is
   restart/roll. (Constraint recorded, not a defect.)
4. **Empty enc-key default**: `encKey` field has no default (L35, null). When empty,
   `ensureKeys` uses per-instance random UUIDs (L91-94) — tokens cannot be forged from
   a static key, restart invalidates all issued tokens, and multi-instance deployments
   break (each instance verifies only its own tokens). Fail-safe but operationally
   disruptive; **classified deployment-configuration constraint per plan-328
   precedent** (roadmap known gap #3 verified live).

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| (none) | — | — | No new finding. Known gap (enc-key empty default) verified live and classified as deployment constraint, consistent with plan-328. | Deployment docs: configure `nop.auth.jwt.enc-key` (or wiring equivalent) for any persistent/multi-instance deployment. |

## Explicit no-finding statements

- Every JWT consumer in the primary auth path (biz-auth-core filter → provider) uses
  the iss/aud/typ-validating overload; no consumer accepts foreign-purpose tokens.
- No static/default signing key exists in code (empty encKey → random per instance).
- Token expiry enforced on every parse (`NopExpiredJwtException` path).
- Tests: `TestJwtAuthTokenProvider`, `TestJwtHelper` cover purpose rejection, grace
  window, and KID selection (plan-333 closure regression suites).

## Adjudication (Phase 3 input)

- No remediation targets. enc-key empty default: `adjudicated deployment constraint`
  (plan-328 precedent); pointer for item 9 to include in deployment hardening checklist.

## Owner mapping

- No new findings to own. JWT/browser boundary hardening owned by plan 333 (completed,
  re-verified here). GraphQL-side token consumption is item 4 (API-02) scope.
