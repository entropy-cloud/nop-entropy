# CRED-03 — Credential Ownership / RBAC Audit

> Mission: security-audit (roadmap item 3, deliverable CRED-03)
> Date: 2026-09-19. Severity labels: CRITICAL/HIGH/MEDIUM/LOW + P0-P3.
> Scope: per-method consumption matrix, ownership precedence, engine-channel exemption
  bounds, two-layer defense, grant/revoke admin face.
> Method: anchor verification in CredentialProviderImpl / NopCredentialAuthBizModel /
  NopCredentialBizModel / CredentialOwnership; test cross-check
  (TestCredentialProviderOwnership, TestCredentialProviderRbacAuth,
  TestNopCredentialOwnershipBizModel, TestNopCredentialBizModel; the
  twoLayerDefenseEndToEndViaGraphQLAndProvider E2E lives in
  TestNopCredentialAuthBizModel L512 — corrected 2026-09-19 per closure audit).

## Verified controls

1. **Order: delFlag → ownership → role-auth → decrypt** (CredentialProviderImpl L126-129,
   L519-531): deleted credentials report deleted before ownership judgment (no ownership
   oracle); fail-closed throughout (never null).
2. **Per-method matrix (W11 §5.3)**: user-scope owner-unique; system-scope tighten-able via
   NopCredentialAuth; admin NOT auto-exempt at the plaintext exit (rows 4/5/6 semantics);
   admin determination from `nop.credential.admin-roles` (default admin,nop-admin).
3. **Engine-channel exemption bounded**: engine entry points limited to
   `engineGetDecryptedFields` / `engineUpdateTokenFields` / `engineUpdateInLock`
   (class doc L66-70, cross-ref L580) — consumed exactly by the OAuth flow
   (`beginOAuthFlow`/callback/refresh) and `saveCredential` group-write, the two
   adjudicated user-context-reachable sites; repo grep shows no third consumer.
4. **Two-layer defense**: BizModel row filtering + write classification (structural, not
   data-auth) + provider-layer matrix; `twoLayerDefenseEndToEndViaGraphQLAndProvider`
   E2E asserts both layers (plus 7 bypass mutations disabled; 6 inheritance actions
   closed per owner doc).
5. **Grant/revoke face**: admin-only, idempotent, unique (credentialId, roleId), physical
   delete, credential-delete cascade cleanup; seven standard mutations disabled.
6. **@MfaRequired annotations** on the four sensitive actions (C1b) verified by
   `TestCredentialMfaRequiredAnnotations`.

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| (none new) | — | — | No new finding. | — |

## Explicit no-finding statements

- No plaintext exit bypasses the matrix (engine channel bounded and adjudicated).
- No user-context-free path reaches plaintext for user-scope credentials (service-trust
  rows apply only to system scope).

## Adjudication (Phase 3 input)

- No remediation targets.

## Owner mapping

- Action-auth enforcement mechanics (GraphQL engine side): item 4 re-verifies; auth-module
  checkers: item 2 (done). Module surface clean here.
