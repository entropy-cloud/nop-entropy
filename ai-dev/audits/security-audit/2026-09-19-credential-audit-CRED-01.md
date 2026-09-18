# CRED-01 — Encryption Implementation Audit

> Mission: security-audit (roadmap item 3, deliverable CRED-01)
> Date: 2026-09-19. Severity labels: CRITICAL/HIGH/MEDIUM/LOW + P0-P3 (P3=LOW).
> Scope: CredentialCipher delegation, cv1 format, keyId handling, key-provider guards.
> Method: full-read anchors in CredentialCipher/DefaultCredentialKeyProvider; test
  cross-check (TestCredentialCipher, TestDefaultCredentialKeyProvider).

## Verified controls

1. **Delegation, no hand-rolled crypto**: `encrypt` → `cipher.encrypt` (AESTextCipher,
   L99-100); `decrypt` unwraps `cv1:{keyId}:` then hands the inner payload to
   `AESTextCipher.decrypt` — no independent primitive anywhere in the module.
2. **Inner v1: enforcement (D5-05)**: decrypt rejects payloads not starting `v1:` —
   cv1-wrapped legacy bare payloads fail closed.
3. **keyId single source**: `CV1_KEY_ID_PATTERN = ICredentialKeyProvider.KEY_ID_PATTERN`
   (L48-53); encrypt validates keyId before use (L94); unknown keyId at decrypt fails
   closed via provider `getKey` error.
4. **Ciphertext leakage bound**: every error param carries at most 16 chars +
   length marker (`truncateCiphertext`, L59-72, applied at L124).
5. **Key-provider guards**: DefaultCredentialKeyProvider D3-03 (non-local value while
   acting as local provider → refuse), D5-06 (blank passphrase reject) — reviewer-verified
   L86-90/L126-128; `reencrypt-page-size >= 1` startup guard (D3-02).
6. **AESTextCipher primitive weakness** (empty-encKey silently derivable key) owned and
   fixed at CORE-03/F-C3-1 (batch 1 WARN); credential deployments supply master-keys
   (empty default = deployment constraint, plan-328).

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| (none new) | — | — | No new finding. D5-02/D5-04 remain A1-audit-owned deferrals (pointer only). | — |

## Explicit no-finding statements

- No plaintext/ciphertext value exceeds the 16-char truncation in any error param or log
  (single truncate helper applied at the only error-param site).
- No secret literal in module main code or `_vfs` resources (sweep clean).
- `getCredential`/`getCredentialData` not exposed as BizModel/GraphQL methods; xmeta
  `data` published=false + BizModel query rows force-null `data` (D1-01 two-layer).

## Adjudication (Phase 3 input)

- No remediation targets; D5-02/D5-04 pointer-only (A1-audit ownership).

## Owner mapping

- Primitive crypto weakness: CORE-03 (batch 1, fixed). Module surface clean here.
