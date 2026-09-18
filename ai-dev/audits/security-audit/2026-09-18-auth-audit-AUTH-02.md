# AUTH-02 — Password Policy Enforcement Audit

> Mission: security-audit (roadmap item 2, deliverable AUTH-02)
> Date: 2026-09-18. Severity labels: CRITICAL/HIGH/MEDIUM/LOW + P0-P3.
> Scope: `DefaultPasswordPolicy` + wiring + enforcement reachability (registration /
> change / admin reset / recovery), encoder chain, seeded-user direct-write path.
> Method: full read of auth-core-defaults.beans.xml wiring + DefaultPasswordPolicy;
  anchor verification in LoginServiceImpl / NopAuthUserBizModel; TestPasswordPolicyBaseline
  cross-check; password-hash storage exposure check (hash fields stay in auth domain;
  credential-vault encryption is item 3).

## Verified controls

1. **Encoder chain**: `CompositePasswordEncoder` = SHA256(salt+pwd) then BCrypt
   (auth-core-defaults.beans.xml:36-41) — verified wiring; `TestPasswordEncoder` covers.
2. **Policy bean wiring**: `nopPasswordPolicy` (auth-core-defaults.beans.xml:20-33)
   with config-driven properties. **Live defaults: min-length=8, upper=0, lower=0,
   digits=0, special=1** — the strong baseline (12 + four classes) is opt-in via
   config, explicitly documented in `TestPasswordPolicyBaseline:13` ("强基线是 opt-in，
   auth-core-defaults.beans.xml 的缺省值已放宽").
3. **Enforcement reachability**: `checkAllowedPassword` is invoked on
   change-self-password / admin reset paths (NopAuthUserBizModel:1987-1991
   `resetUserPassword` is admin-gated and `@MfaRequired`-annotated per nop-auth.md
   annotation list; change-self-password same family). Seeded default user
   (`nop/123`) is written via `encodePassword` direct-write, bypassing policy by
   design (deployment-configuration constraint, plan-328 adjudication; doc + beans
   comment both state it).
4. **Reset authorization**: admin reset requires admin role + operation-level MFA
   (annotation); bind-token expiry logic at NopAuthUserBizModel:1639-1647.

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-A2-1 | MEDIUM (P2) — owner-doc drift | docs-for-ai/02-core-guides/auth-and-permissions.md L420-428 & config table ("默认基线（DR-1d）：最少 12 位…各至少 1 个"; table defaults 12/1/1/1/1) vs live wiring nop-service-framework/nop-biz-auth-core/src/main/resources/_vfs/nop/auth/beans/auth-core-defaults.beans.xml:28-32 (8/0/0/0/1) | Owner doc claims the DR-1d strong baseline is the DEFAULT; live default is relaxed (min 8 + special-char only). TestPasswordPolicyBaseline documents the relaxation as intentional, but the guide was never updated. An operator reading the guide believes users cannot set `password` class-only weak passwords when they can (e.g. `password!` = 9 chars, 1 special, no upper/digit passes live default). Fix ownership: item 9 → Phase 3 doc fix (or re-tighten default, owner's choice — evidence suggests intentional relaxation, so doc fix is the shape). | Phase 3: update auth-and-permissions.md baseline section + config table to the live defaults (8/special=1) and present 12/four-class as the recommended production override. |
| F-A2-2 | LOW (P3) — deployment constraint verification | nop-auth.md "默认用户" (nop/123, allow-create-default-user=true) | Roadmap known gap "默认种子用户 nop/123" verified live: auto-created when user table empty, direct-write bypasses policy. Classified deployment-configuration constraint per plan 328 (not a framework defect); production deployments must disable creation and/or reset the seed user. | Deployment checklist entry for item 9 consolidation (documentation). |

## Explicit no-finding statements

- No password or hash literal is logged, embedded in error params, or persisted outside
  auth tables in the audited modules (secret-leak sweep + hash-field checks clean).
- No plaintext password storage: encoder chain hashes before persist; MFA setting
  secrets (TOTP) use AESTextCipher encryption (W-series verified; key-management
  weakness owned by CORE-03 F-C3-1, already fixed in batch 1).

## Adjudication (Phase 3 input)

- F-A2-1: `remediation-target` (MEDIUM, doc fix) — **FIXED 2026-09-18 in fix batch 2** (plan 2026-09-18-2320-5, user-authorized early fix phase): auth-and-permissions.md baseline section now states live defaults (8/special=1) with the strong baseline as recommended production override. Verified against auth-core-defaults.beans.xml:28-32.
- F-A2-2: `adjudicated deployment constraint` (plan-328 precedent; pointer only).

## Owner mapping

- F-A2-1 successor: item 9 consolidation (doc fix). Not owned by plan 333 (browser
  boundary) nor credential item 3. No double ownership.
