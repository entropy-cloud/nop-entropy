# CRED-04 — Usage Registry Integrity Audit

> Mission: security-audit (roadmap item 3, deliverable CRED-04)
> Date: 2026-09-19. Severity labels: CRITICAL/HIGH/MEDIUM/LOW + P0-P3.
> Scope: binding-registry semantics, D6-03 pre-validation, idempotency, delete
  interception, mutation surface closure.
> Method: anchor verification in NopCredentialBizModel / NopCredentialUsageBizModel /
  ICredentialProvider SPI; ORM model check; test cross-check
  (TestNopCredentialUsageBizModelMutationsDisabled, TestCredentialProviderImpl,
  TestCredentialMigrationSupport).

## Verified controls

1. **Registry semantics confirmed**: `nop_credential_usage` columns
   usageId/credentialId/consumerRef (+createTime) with unique
   (credentialId, consumerRef) — a binding/reference registry, NOT an access trail
   (no accessor identity / lastUsedAt columns); lastUsedAt lives on NopCredential.
   Scope of this audit = binding integrity, per plan baseline.
2. **D6-03 fail-closed pre-validation**: registerUsage validates credential exists and is
   not soft-deleted BEFORE inserting (not-found/deleted same error codes as read path) —
   misconfigured credentialId surfaces at binding time, no dangling references.
3. **Idempotency**: register/unregister idempotent (unique-key upsert/delete semantics);
   migration reverse-lookup (`findCredentialIdByConsumerRef`) treats soft-deleted as
   `deleted=true` for explicit operator handling.
4. **Delete interception**: `NopCredentialBizModel.doDelete` →
   `prepareDeleteWithUsageCheck` (L785/797) rejects deletion while reference count > 0.
5. **Mutation surface closed**: 7 standard mutations disabled on the usage BizModel
   (D4-06); rows written only via the SPI channel (register/unregister); query face
   admin-only.
6. **W16 consumer registration**: family integrations register with catch-all WARN at
   startup (misconfig does not block boot; the security boundary is consumption-time
   fail-closed resolution) — semantics as adjudicated.

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| (none new) | — | — | No new finding. | — |

## Explicit no-finding statements

- No code path deletes usage rows outside register/unregister SPI channels (repo grep;
  the only physical deletes are idempotent unregister + credential-delete cascade owned
  by the module).
- No usage-row write path bypasses D6-03 pre-validation.

## Adjudication (Phase 3 input)

- No remediation targets. A1-audit deferred items remain pointer-only.

## Owner mapping

- Consumer-side binding hygiene (integration/metadata families): items 6-8 consumer
  audits re-verify their registration call sites. Module surface clean here.
