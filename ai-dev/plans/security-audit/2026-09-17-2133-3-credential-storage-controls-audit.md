# Security Audit Plan 3 — Credential Storage Controls Assurance Audit

> Plan Status: active
> Last Reviewed: 2026-09-18
> Mission: security-audit
> Work Item: 3. 凭证存储审计 (`nop-credential`)
> Source: `ai-dev/backlog/security-audit-roadmap.md` (item 3, stages CRED-01..CRED-04)
> Related: `ai-dev/plans/security-audit/2026-09-17-0831-1-core-framework-security-controls-audit.md`
> (dependency gate), `ai-dev/plans/2026-08-17-0447-1-credential-phase2-security-audit.md`
> (A1-audit precedent), `docs-for-ai/03-modules/nop-credential.md`,
> `docs-for-ai/04-reference/safe-api-reference.md`

## Execution Rules And Evidence Contract

- Before collecting evidence, read `ai-dev/audits/README.md` and
  `docs-for-ai/03-modules/nop-credential.md` completely. Reports use the mission
  output directory `ai-dev/audits/security-audit/`; each report includes a summary
  and BOTH CRITICAL/HIGH/MEDIUM/LOW and P0/P1/P2/P3 severity labels
  (P0=CRITICAL … P3=LOW, satisfying the audits-README P-scale rule).
- Never persist real secret values, keys, tokens, or sensitive payloads in logs
  or reports.
- Audit discovery is `Proof`/`Decision`; any confirmed defect or owner-doc drift
  becomes a `Fix` record owned by roadmap item 9, with finding ID, affected
  module/doc, remediation direction, and verification expectation. Bare
  `deferred to consolidation` without these fields is insufficient.
- At execution entry, synchronize only roadmap item 3 to `planned`; capture the
  starting git status and assert task-owned changes against that baseline
  (parallel fix batches may hold uncommitted product edits — preserve them).
  Do not commit unless explicitly requested.
- Historical no-fix decisions apply only while their assumptions still match
  live evidence; new regressions must be reported, not suppressed.

## Purpose

Read-only control-effectiveness audit of the credential storage surface
(roadmap item 3): verify that the documented encryption delegation, Vault KMS
fail-closed startup wiring, credential-level RBAC, and usage-registry
integrity contracts are actually enforced in live code and tests, and produce
severity-classified findings reports ready for roadmap item 9 consolidation.
Audit phase only — no product changes (roadmap rule 不过早修复; fixes happen in
dynamically generated Phase 3 items).

## Current Baseline

Verified against live repo on 2026-09-17, re-baselined 2026-09-18 after
adversarial review:

- Roadmap item 3 is `todo` with this plan as owner (still `draft`). Dependency
  gate (plan-2-strength, satisfied as of 2026-09-18): item 1 is `done`, its
  owner plan `2026-09-17-0831-1-core-framework-security-controls-audit.md` is
  `completed` with independent closure evidence recorded in `## Closure`, and
  the five durable CORE reports exist under `ai-dev/audits/security-audit/`.
  Item 2's audit is in progress in parallel (AUTH reports landed); item 3
  depends only on item 1 per the roadmap dependency graph.
- CRED-01 encryption delegation to verify (not rebuild): `AESTextCipher`
  (AES/GCM/NoPadding, 12B random IV per message, GCM tag 16B,
  PBKDF2WithHmacSHA256 65536/256-bit, `v1:` versioned format default;
  `nop-kernel/nop-commons/.../AESTextCipher.java`), `cv1:{keyId}:{v1}` wrapper
  with inner `v1:` enforcement, keyId charset single source
  (`ICredentialKeyProvider.KEY_ID_PATTERN`), ciphertext-param truncation
  (16 chars), unknown-keyId fail-closed (`CredentialCipher.java`,
  `DefaultCredentialKeyProvider.java` with non-local provider guard
  D3-03 + blank-passphrase reject D5-06). Historical owner plan 334
  (`encrypted-value-format`) is completed; A1-audit precedent
  `2026-08-17-0447-1-credential-phase2-security-audit.md` completed with
  standing deferred D5-02 (reencryptAll skips soft-deleted rows,
  out-of-scope improvement) and D5-04 (cv1 has no keyId AEAD binding,
  watch-only residual; cv2 evolution successor no).
- CRED-02 Vault KMS startup wiring to verify: material-delivery model
  (startup fetch → in-memory, runtime zero hosted calls, no local fallback),
  same-name gated bean override (`app-kms-vault.beans.xml` with
  `ioc:condition if-property key-provider=vault`), module-missing guard
  (`TestKeyProviderModuleMissingGuard`), D3-01 active-key-id conflict
  fail-closed, D3-05 request timeout, migration-keys WARN-only; runtime token
  rotation does not exist by design — the audit target is fail-closed
  semantics, not rotation machinery.
- CRED-03 credential RBAC to verify: `NopCredentialAuth` grant/revoke
  (admin-only, idempotent, unique (credentialId, roleId), physical delete),
  provider-side consumption matrix rows 4/5/6 in
  `CredentialProviderImpl.assertRoleAuthForPlaintext` (default-open,
  service-trust for no-user-context, role-intersection with admin NOT
  auto-exempt), engine-channel exemption, BizModel
  `NopCredentialAuthBizModel` two-layer defense + seven bypass mutations
  disabled, action-auth delta `roles="admin,nop-admin"` (D4-02 dual-source
  alignment).
- CRED-04 usage-registry integrity to verify: `NopCredentialUsage` is a
  binding/reference-count registry (columns usageId/credentialId/consumerRef/
  createTime; unique (credentialId, consumerRef)) — NOT an access audit trail
  (no accessor identity / lastUsedAt columns). Audit scope = binding
  integrity (D6-03 fail-closed pre-validation, idempotent register/unregister,
  admin-only query face with 7 mutations disabled per D4-06, delete
  interception `prepareDeleteWithUsageCheck`), not tamper-evidence of access
  logs.
- Known deferred items that must NOT be re-litigated here (pointer-only):
  D5-02/D5-04 from A1-audit (ownership recorded there, no re-open triggers
  armed); phase2-design §七 deferred rows (PKCE → re-adjudicated by A1-audit;
  context-loss alert → A1-audit evaluated, not done); W16 watch anchors
  (feishu captured-resolved-values tests pin current semantics). Cross-cutting
  A1 deferred items revive only via demand-justified successor plans.
- Known supported-baseline gaps from the roadmap that THIS plan must verify
  and report (not fix): `nop.credential.master-keys` config handling as
  deployment-configuration constraint (plan 328 precedent); test/development
  default secrets are deployment constraints, not framework defects.
- QA static analysis (`./mvnw checkstyle:check -pl nop-credential -Pqa`,
  `./mvnw pmd:check -pl nop-credential -Pqa`,
  `./mvnw compile com.github.spotbugs:spotbugs-maven-plugin:check -pl nop-credential -Pqa`
  — compile prefix required: spotbugs analyzes bytecode; the bare
  `spotbugs:check` prefix does not resolve in this repo, use the full GAV)
  is report-only (`failOnViolation=false`): a passing exit code alone proves
  nothing — this plan requires reading report output and asserting zero NEW
  violations against the current baseline.
- Test-command note: run module test commands sequentially. The roadmap's
  `-T 1C` cross-cutting baseline is covered at the mission level by the
  full-reactor run performed for the parallel fix batches; this plan's own
  gates use the sequential module commands above (deviation recorded here).

## Goals

- CRED-01: Verify encryption-implementation delegation — `CredentialCipher`
  delegates to `AESTextCipher` for all crypto primitives (no hand-rolled
  crypto), `cv1:` wrapping preserves inner `v1:` AEAD guarantees, key material
  handling in `DefaultCredentialKeyProvider` is fail-closed (blank rejection,
  non-local guard, unknown active key), and no plaintext/ciphertext leakage
  path exists through error params or logs.
- CRED-02: Verify Vault KMS integration fail-closed semantics end to end —
  startup material fetch (auth/404/invalid/unreachable/timeout all refuse
  startup), gated bean override actually resolves at runtime (wiring evidence
  via `TestVaultKeyProviderWiring`), module-missing guard blocks "configured
  vault but running local", migration keys stay decrypt-only, and runtime
  `getKey` is pure in-memory with no hosted-side interaction.
- CRED-03: Verify credential-level RBAC enforcement — grant/revoke idempotent
  admin-only channel, consumption matrix rows 4/5/6 including admin
  non-exemption, user-level ownership precedence over role grants,
  engine-channel exemption bounded to the two adjudicated call sites
  (`beginOAuthFlow`, `saveCredential` oauth2 group-write), two-layer defense
  (BizModel + provider) wiring intact.
- CRED-04: Verify usage-registry binding integrity — D6-03 fail-closed
  pre-validation on register, idempotent register/unregister, unique
  constraint as deletion-bypass guard, admin-only query face with all 7
  standard mutations disabled, reference-count delete interception, and
  confirm no code path deletes usage rows outside the adjudicated channels.
- Produce `ai-dev/audits/security-audit/` reports for CRED-01..CRED-04 with
  severity-classified findings (CRITICAL/HIGH/MEDIUM/LOW), each with source
  anchor, rationale, and remediation suggestion, ready for roadmap item 9
  consolidation.

## Non-Goals

- No product code changes, no config hardening — remediation happens only in
  dynamically generated roadmap Phase 3 items.
- No re-adjudication of A1-audit deferred items D5-02/D5-04, phase2-design §七
  rows, or W16 watch anchors — pointer references only; their successor
  ownership is already recorded.
- No re-audit of `@sec:` config encryption (IoC/config chain is roadmap item 1
  / CORE-03), nop-auth login/MFA/session (item 2), or consumer-side
  integration/metadata resolution logic beyond the provider boundary (W16
  plans closed those; consumer modules belong to items 6-8).
- No exploit development or payload reproduction; control verification is by
  code-path tracing, existing-test inspection, and focused test runs.
- No dependency CVE scanning (plan 328 non-blocking follow-up, separate).
- No redesign of the ownership/RBAC/OAuth engine semantics — only their
  security-relevant invariants are in scope.

## Scope

### In Scope

- Modules: `nop-credential/` (api, dao, meta, service, kms-vault, web, app,
  codegen, deploy SQL), with boundary-crossing reads into
  `nop-kernel/nop-commons` `AESTextCipher` (reused primitive, read-only) and
  consumer-side resolution seams (`nop-integration/nop-integration-api`
  `CredentialResolutionSupport`, nop-ai `IAiModelCredentialResolver`) only
  where CRED-04 binding integrity is affected.
- `ai-dev/audits/security-audit/` report files for CRED-01..CRED-04.
- Audit evidence collection: targeted greps (Java + `_vfs` resources such as
  `credential-defaults.beans.xml`, `app-kms-vault.beans.xml`,
  `nop-credential.action-auth.xml`, xmeta, ORM model), QA static analysis
  runs (report-only, must read outputs), focused reading of control classes,
  and runs of existing credential test suites.
- Cross-cutting verification mandated by the roadmap: `@cfg:`/`@sec:`
  secret-leak sampling in nop-credential modules and `_gen/` generated-code
  spot check for nop-credential.

### Out Of Scope

- `nop-auth/`, `nop-service-framework/`, AI tools, network clients,
  file modules — owned by roadmap items 2, 4, 6, 7, 8.
- Consumer modules' own audit (nop-ai models, integration channels, metadata
  datasources) — owned by roadmap items 6-8; this plan stops at the
  provider/SPI boundary.
- Remediation of any confirmed finding (roadmap Phase 3).
- Modifying any `_`-prefixed generated file or `_gen/` output.
- New security feature design (A1 deferred backlog stays where recorded).

## Execution Plan

### Phase 1 - Dependency Gate And Evidence Collection

Status: planned (execution held until parallel fix batch 2 commits, to keep
the starting-git-status baseline for task-owned-change assertion clean;
dependency gate itself is satisfied as of 2026-09-18)
Targets: `nop-credential/` (read-only), `_tmp/security-audit/` scratch outputs

- Item Types: `Proof | Follow-up`

- [ ] Dependency gate: confirm roadmap item 1 is `done`, its owner plan is
  `completed` with independent closure evidence, and the five durable CORE
  reports exist under `ai-dev/audits/security-audit/`; record paths and
  results in the daily log before proceeding (plan-2-strength gate).
- [ ] Build the credential control inventory: enumerate classes implementing
  cipher/key-provider/ownership/RBAC/usage/OAuth-engine controls with paths
  and roles (start from anchors in `docs-for-ai/03-modules/nop-credential.md`
  "源码锚点" table), writing `_tmp/security-audit/credential-inventory.md`.
- [ ] Run QA static analysis for nop-credential and capture report output into
  `_tmp/security-audit/`: checkstyle, PMD, SpotBugs (all with `-Pqa`);
  since all are report-only, read the reports and register any security-rule
  hit as a candidate finding or explicitly disposition it with reason.
- [ ] Secret-leak sweep: grep `@cfg:`/`@sec:` keys and string literals matching
  password/secret/key/token/passphrase across nop-credential modules AND
  their `_vfs` resources (`.beans.xml`, xmeta, ORM model, deploy SQL samples);
  flag keys whose values are logged or embedded in error params.
- [ ] Config-default inventory: enumerate `nop.credential.*` config keys with
  live defaults (`master-keys`, `admin-roles`, `key-provider`,
  `reencrypt-page-size`, `oauth.*`, `vault.*`) and file anchors for Phase 2
  adjudication.
- [ ] Existing-test coverage map: list the credential test files
  (`TestCredentialCipher`, `TestDefaultCredentialKeyProvider`,
  `TestCredentialProviderImpl`, `TestCredentialProviderRbacAuth`,
  `TestNopCredentialAuthBizModel`,
  `TestNopCredentialUsageBizModelMutationsDisabled`,
  `TestVaultKeyProviderWiring`, `TestKeyProviderModuleMissingGuard`,
  `NopCredentialActionAuthDeltaTest` et al.) and which control each covers;
  note uncovered control paths.

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] Dependency-gate status of item 1 recorded in daily log with plan path.
- [ ] Credential control inventory exists under `_tmp/security-audit/`, every
  entry backed by an existing repo path, covering CRED-01..CRED-04 control
  classes.
- [ ] QA report outputs captured and read; each security-rule hit registered
  as candidate finding or explicitly dispositioned with reason.
- [ ] Secret-leak sweep covers Java AND `_vfs` DSL resources of nop-credential;
  every flagged key has disposition (leak / safe) with evidence anchor.
- [ ] Config-default inventory complete with live anchors (file:line) ready
  for Phase 2 classification.
- [ ] Test-coverage map lists all control areas; every area maps to at least
  one existing test or an explicit gap note.
- [ ] No task-owned product change relative to the recorded starting git
  status (only `_tmp/`, `ai-dev/audits/`, `ai-dev/plans/security-audit/`,
  roadmap status block, and `ai-dev/logs/` changes are task-owned;
  pre-existing unrelated or parallel-batch edits are preserved, not reverted).
- [ ] No owner-doc update required: read-only evidence collection changes no
  live baseline.
- [ ] `ai-dev/logs/` entry records phase completion with command results.

### Phase 2 - Control-Effectiveness Review And Findings (CRED-01..CRED-04)

Status: planned
Targets: `ai-dev/audits/security-audit/` reports

- Item Types: `Proof | Decision | Follow-up`

- [ ] CRED-01 review: verify `CredentialCipher` delegates all primitives to
  `AESTextCipher` (no independent crypto), `cv1:` parse rejects malformed
  wrappers and enforces inner `v1:`, keyId charset enforcement is single
  source, ciphertext truncation applies to every error param, unknown-keyId
  and unknown-active-key paths fail closed; cross-check against
  `TestCredentialCipher` / `TestDefaultCredentialKeyProvider` assertions.
- [ ] CRED-02 review: trace `VaultCredentialKeyProvider.init()` failure matrix
  (unreachable/auth-failed/key-not-found/material-invalid/config-conflict →
  startup refusal, no local fallback); verify gated same-name bean override
  resolves at runtime and default-bean exclusion works (rerun
  `TestVaultKeyProviderWiring` as evidence, not rewrite); verify
  module-missing guard (`TestKeyProviderModuleMissingGuard`), migration-keys
  decrypt-only + WARN, request-timeout fallback, and runtime `getKey`
  in-memory purity (no hosted calls after startup).
- [ ] CRED-03 review: trace `CredentialProviderImpl.getCredential` /
  `getCredentialData` order (delFlag → ownership → role-auth → decrypt);
  verify rows 4/5/6 semantics incl. admin non-exemption and user-level
  precedence; verify grant/revoke admin-only + idempotent + scope≠system
  rejection; verify engine-channel exemption is bounded to the two adjudicated
  user-context-reachable call sites and no third call site exists; verify
  two-layer defense wiring via `twoLayerDefenseEndToEndViaGraphQLAndProvider`
  and action-auth delta D4-02 alignment.
- [ ] CRED-04 review: verify D6-03 pre-validation (not-found/deleted
  fail-closed on register), register/unregister idempotency, unique
  constraint protection against double-registration races, admin-only usage
  query face + 7 disabled mutations (D4-06), delete interception via
  `prepareDeleteWithUsageCheck`, and enumerate all usage-row write paths to
  confirm none bypasses the SPI channel.
- [ ] Write one report per deliverable under `ai-dev/audits/security-audit/`
  (naming pattern: `{date}-credential-audit-CRED-0N` where N=1..4), each
  with: scope, method, findings table (ID, severity, source anchor,
  description, remediation suggestion), and explicit "no finding" statements
  where controls verified clean.
- [ ] Cross-check every confirmed finding against A1-audit adjudications and
  W16 closure state so no finding is double-owned or silently dropped;
  record owner mapping in each report.

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] Four reports exist under `ai-dev/audits/security-audit/` (one per
  CRED-01..CRED-04), each with findings table and repo-resolvable anchors.
- [ ] Every Phase 1 inventory entry appears in exactly one report
  classification; no control path left unclassified.
- [ ] Each finding carries severity rationale and remediation suggestion
  concrete enough for a Phase 3 fix plan without re-analysis.
- [ ] Owner mapping recorded for every confirmed finding with explicit Fix
  handoff fields (finding ID, affected module/doc, remediation direction,
  verification expectation, successor owner); no in-scope confirmed
  live defect downgraded to follow-up (Anti-Slacking Rule; bare
  `deferred to consolidation` is insufficient).
- [ ] **端到端验证**（Minimum Rules #22）: existing end-to-end wiring tests pass
  as evidence — `twoLayerDefenseEndToEndViaGraphQLAndProvider`
  (CRED-03), `TestVaultKeyProviderWiring` (CRED-02), and the save→encrypt→
  SPI-consume path covered by `TestCredentialProviderImpl` — run via
  `./mvnw test -pl nop-credential/nop-credential-service -am` and
  `./mvnw test -pl nop-credential/nop-credential-kms-vault -am`
  (sequentially, no `-T 1C`; pre-existing environmental failures must be
  triaged and recorded as pre-existing if they reproduce; unit-level suites
  must be green), results recorded in daily log.
- [ ] **接线验证**（Minimum Rules #23）: runtime wiring confirmed — Vault gated
  bean override actually replaces the default bean under
  `key-provider=vault` (evidence: `TestVaultKeyProviderWiring`), and the
  module-missing guard fails startup (evidence:
  `TestKeyProviderModuleMissingGuard`).
- [ ] No owner-doc update required: this phase writes audit evidence, not
  normative guidance; any owner-doc drift discovered is recorded as a
  MEDIUM "owner-doc drift" finding instead of silent doc edit.
- [ ] `ai-dev/logs/` entry updated.

### Phase 3 - Adjudication, Roadmap Sync, And Closure

Status: planned
Targets: `ai-dev/backlog/security-audit-roadmap.md`, this plan

- Item Types: `Decision | Proof`

- [ ] Adjudicate every finding: assign CRITICAL/HIGH/MEDIUM/LOW, re-check
  classifications against plan 328 precedent (test/development defaults are
  deployment-configuration constraints, not framework defects) and A1-audit
  adjudication records; mark each finding `remediation-target` or
  `adjudicated-no-fix` with reason.
- [ ] Update roadmap Work Items block: mark item 3 `planned` when this plan
  activates, then `done` only after closure audit passes; do not touch other
  item statuses.
- [ ] Ensure report files are self-contained (no `_tmp/`-only evidence; copy
  needed scratch data into `ai-dev/audits/security-audit/` reports) so item 9
  consolidation can consume them.

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] Every finding across the four reports has a final adjudication status
  with recorded reason; zero findings remain "pending".
- [ ] Roadmap Work Items block reflects exactly the item-3 status transitions
  (`todo` → `planned` on activation, `done` only post-closure-audit) and no
  other item statuses changed.
- [ ] All audit evidence referenced by reports lives in committed
  `ai-dev/audits/security-audit/` files, not solely in `_tmp/`.
- [ ] No owner-doc update required (adjudication changes no supported runtime
  behavior); roadmap status block is the only dynamic state touched.
- [ ] `ai-dev/logs/` entry updated.

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] All four deliverables (CRED-01..CRED-04 reports) exist, are
  severity-classified, and contain repo-resolvable source anchors.
- [ ] Every confirmed in-scope finding has recorded adjudication and
  remediation owner; nothing silently deferred.
- [ ] Roadmap item 3 marked `done` strictly after independent closure audit.
- [ ] Independent sub-agent closure audit completed with evidence recorded in
  `## Closure` (per guide rules 12/18/19).
- [ ] Anti-Hollow check: closure audit verified each finding's anchor
  re-verified against the live repo and no "finding" is a placeholder or
  stale conclusion copied from prior plans without re-verification.
- [ ] Textual consistency check: `Plan Status`, per-phase Status, per-phase
  Exit Criteria, Closure Gates, and daily log all agree.
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/security-audit/2026-09-17-2133-3-credential-storage-controls-audit.md --strict`
  exits 0 before `Plan Status` flips to `completed` (repo-root relative path;
  guide Minimum Rule 26).
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0 (doc-only
  plan; build/test gates omitted per guide's pure-doc-plan allowance — test
  runs in Phase 2 are audit evidence, not code changes).

## Deferred But Adjudicated

### Re-adjudication of A1-audit deferred items (D5-02 soft-deleted-row skip, D5-04 cv1 keyId binding)

- Classification: `watch-only residual`
- Why Not Blocking Closure: adjudicated by
  `ai-dev/plans/2026-08-17-0447-1-credential-phase2-security-audit.md` with
  recorded reasons and no re-open triggers armed; this plan records pointers
  only and re-verifies current live behavior where CRED findings overlap.
- Successor Required: `no`
- Successor Path: `N/A`

### Default master-keys configuration as deployment constraint

- Classification: `watch-only residual`
- Why Not Blocking Closure: reclassified as deployment-configuration
  constraint by plan 328 (user-approved); this plan still verifies the live
  handling and reports actual behavior (including the D3-03 non-local guard).
- Successor Required: `no`
- Successor Path: `N/A`

## Non-Blocking Follow-ups

- Capture reusable audit grep patterns into `ai-dev/skills/` if broadly
  applicable to later module audits (items 4-8).
- If CRED-04 uncovers usage-registry test-coverage gaps (not live defects),
  record them as `optimization candidate` for the fix phase.

## Closure

Status Note: (pending — audit-phase plan; closure evidence recorded here when
the independent closure audit completes.)

Closure Audit Evidence:

- Reviewer / Agent: (pending)
- Evidence: (pending)

Follow-up:

- Findings feed roadmap item 9 consolidation; no direct remediation work
  owned by this plan beyond report completion.
