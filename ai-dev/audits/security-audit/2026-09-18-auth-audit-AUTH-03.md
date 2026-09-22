# AUTH-03 — MFA Flow Integrity Audit

> Mission: security-audit (roadmap item 2, deliverable AUTH-03)
> Date: 2026-09-18. Severity labels: CRITICAL/HIGH/MEDIUM/LOW + P0-P3.
> Scope: two-phase login challenge discipline, factor verification funnel, TOTP/SMS/
> email/WebAuthn replay protection, trusted-device boundaries, sensitive-table CRUD
> guards, restricted sessions.
> Method: anchor verification in LoginServiceImpl / MfaFactorVerifier / stores;
  nop-auth.md W4-W15 contract cross-check; A2/A3 audit adjudication cross-reference;
  25+ MFA test-class inventory mapping.

## Verified controls (anchor-verified)

1. **Challenge scene/verifiedAt discipline** (A2-audit D2-F2 successor-B): login-level
   `mfaVerifyAsync` accepts only `scene ∈ {login, null}` with `verifiedAt == null`;
   operation/webauthn-register/webauthn-unbind/channel-proof/webauthn-add tokens are
   rejected with `ERR_AUTH_MFA_CHALLENGE_EXPIRED` without consumption
   (LoginServiceImpl:598-625, comments cite the adjudication).
2. **Shared verification funnel**: `MfaFactorVerifier` is the single factor-check
   component for login/binding/operation paths; unknown mfaType fail-closed; TOTP
   `lastVerifiedWindow` advanced on ANY successful verification (no cross-scene 30s
   replay); documented exception: recovery codes stay in login-level branch only.
3. **Store atomicity**: consume = conditional DELETE + affected-row; incrFailCount =
   atomic SQL increment (db); Redis CAS via SETNX/derived ticket key — one-time
   semantics across local/db/redis (TestDbCodeStoreSendRace, TestRedisCodeStoreCasRace).
4. **WebAuthn**: cryptoChallenge from server-side challenge payload (client cannot
   self-mint); signCount monotonic via conditional `UPDATE ... WHERE SIGN_COUNT < ?`;
   credential lifecycle physical-delete on factor invalidation (A2 D3-F1); RP config
   missing → explicit fail-closed error.
5. **Trusted-device boundaries**: fingerprint = device-id + UA + Accept-Language
   SHA-256 (privacy-bounded, documented threat model); operation-level MFA never
   exempted; recovery-code logins never register devices; fixed window no-renewal;
   revocation matrix implemented (unbind/confirm/reset remove all rows).
6. **Sensitive-table CRUD lockdown**: `MfaSensitiveTableBizModel` overrides all
   inherited write actions to `ERR_AUTH_MFA_CRUD_DISABLED` for 8 MFA tables;
   TestMfaCrudLockdownE2E asserts per-table via GraphQL entry.
7. **TOTP bind/unbind failure caps**: persisted atomic counters with REQUIRES_NEW
   transaction, bind-token invalidation + cooldown windows
   (`nop.auth.mfa.totp-verify-max-fails`=5, cooldown 300s defaults).
8. **Restricted sessions (W13)**: `mfaRestricted` flag persisted pre-session; executor
   + checker dual-touchpoint interception with whitelist; channel-proof enrollment
   attack guard live.

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| (none new) | — | — | No new finding. All A2/A3 adjudicated items verified landed and regression-tested; W14 §七 condition-triggered deferrals remain pointer-only (not re-litigated per Non-Goals). | — |

## Explicit no-finding statements

- No factor-verification path bypasses `MfaFactorVerifier` except the documented
  recovery-code login branch (by design).
- No challenge/code store permits double-consume (conditional-write semantics verified
  in all three backends).
- MFA secrets (TOTP) encrypted at rest; setting.phone excluded from published output
  (`not-pub` → `published=false` chain — verified in doc + tagSet).

## Adjudication (Phase 3 input)

- No remediation targets. W14 §七 residuals (attestation trust chain, external MFA
  providers, batch cleanup) stay deferred with recorded design-doc ownership.

## Owner mapping

- MFA CRUD GraphQL entry enforcement crosses into item 4 (GraphQL executor
  interception points) — item 4 re-verifies the executor side; auth-module side fully
  owned here. No double ownership.
