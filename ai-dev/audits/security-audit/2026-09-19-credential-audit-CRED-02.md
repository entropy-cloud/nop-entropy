# CRED-02 — Vault KMS Integration Audit

> Mission: security-audit (roadmap item 3, deliverable CRED-02)
> Date: 2026-09-19. Severity labels: CRITICAL/HIGH/MEDIUM/LOW + P0-P3.
> Scope: material-delivery startup semantics, gated bean override, module-missing guard,
  migration keys, runtime purity.
> Method: full-read fail matrix in VaultCredentialKeyProvider (L160-294); wiring anchors
  (app-kms-vault.beans.xml, credential-defaults.beans.xml); test cross-check
  (TestVaultCredentialKeyProvider, TestVaultKeyProviderWiring, TestKeyProviderModuleMissingGuard,
  TestLocalKeyProviderWithKmsModulePresent, TestKeyProviderGuardMasterKeysResidual).

## Verified controls

1. **Complete startup fail matrix** (live-verified L188-294): config-missing,
   no-key-configured, master-keys-residual, key-mapping-invalid ×3, active-key-conflict
   (D3-01 dual-source), unknown-active-key, migration-key-invalid ×3 + migration-key-active,
   unreachable/auth-failed(401/403)/key-not-found(404)/material-invalid (incl. isBlank
   D5-06), unknown-key-id at runtime — all throw at bean init ⇒ application refuses to
   start. No local fallback anywhere (degradation = attack path, explicitly excluded).
2. **Request timeout (D3-05)**: startup fetch bounded (`vault.request-timeout`, default
   10s; <=0 falls back to default — no indefinite hang).
3. **Gated same-name override wiring**: `app-kms-vault.beans.xml` defines the SAME bean id
   `nopCredentialKeyProvider` gated on `if-property key-provider=vault`; default bean's
   missing-bean condition excludes when override registers (reviewer-verified wiring).
4. **Module-missing guard**: `key-provider=vault` without the KMS module ⇒ default
   provider's @PostConstruct guard throws `key-provider-module-missing` — structurally
   blocks "configured vault but running local" false security.
5. **Runtime purity**: post-init `getKey` is in-memory map lookup; zero hosted calls after
   startup (material delivery model).
6. **Migration window discipline**: migration-keys decrypt-only, active-key exclusion,
   per-start WARN audit; runtime rotation = new keyId + reencryptAll (keyId→material
   immutability invariant documented as config prohibition).

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| (none new) | — | — | No new finding. | — |

## Explicit no-finding statements

- Vault token never logged or echoed (sweep + code path: token only in request header).
- No runtime secret fetch path exists (material delivered once at startup).
- No TLS downgrade option introduced by the module (uses shared IHttpClient config; NET-01
  owns client transport posture).

## Adjudication (Phase 3 input)

- No remediation targets.

## Owner mapping

- HTTP transport security: item 7 (NET-01) link-only. Module-internal surface clean here.
