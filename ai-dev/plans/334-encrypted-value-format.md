# 334 Encrypted-Value Format Hardening

> Plan Status: draft
> Review Hold: Draft review ran (2026-08-07). Format/scope/closure sections are sound; one Major reference defect fixed (`ConfigStarter.newVmValueEnhancer` -> actual `newValueEnhancer`). NOT promoted to active: implementation is gated on user authorization of upstream decision records DR-2a (versioned per-message-IV contract) and DR-2b (global vs scoped compatibility boundary) — these change persisted ciphertext semantics and cannot be guessed at review time. Re-promote to active once the user records `approved` for both DRs.
> Last Reviewed: 2026-08-07
> Source: `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md` (DR-2a, DR-2b)
> Related: `ai-dev/plans/328-security-hardening-remediation-planning.md`
> Predecessor: Plan 328 Phase 1 froze the decision records this plan consumes.

## Purpose

Resolve the AES encrypted-value finding (M-7) by introducing a self-describing, per-message
IV ciphertext format with a modern key-derivation function, while preserving read
compatibility with legacy ciphertext. This plan changes persisted ciphertext semantics and
is **blocked at draft until the user approves the versioned per-message-IV contract and the
compatibility boundary** (DR-2a, DR-2b).

## User-Authorization Gate (BLOCKER)

This plan changes persisted ciphertext semantics. It MUST remain `Plan Status: draft` with
a blocked implementation slice until the user records an explicit disposition for:

- DR-2a (encrypted-value format: version marker, per-message IV, concurrency ownership,
  legacy detection, KDF, fail-closed behavior, compatibility scope).
- DR-2b (compatibility boundary: global `AESTextCipher` change vs scoped
  ORM-binder + config-enhancer change).

Until then, no Phase below may start. The legacy-read/migration decision and the regression
fixture contract (Phase 1) are hard prerequisites to any verification change.

## Current Baseline

See Plan 328 analysis DR-2a/DR-2b for verified source anchors. Summary:

- `AESTextCipher` default ctor pins a static `DEFAULT_GCM_IV` (12-byte) reused across every
  encryption; `generateIv()` exists but is never called by the default ctor,
  `DefaultOrmColumnBinderEnhancer`, or `ConfigStarter.newValueEnhancer`.
- `concatIv` prepends the (still static) IV; there is no version marker in the output.
- `buildSecretKey` uses single-pass `md5(encKey + saltKey)` — not PBKDF2/Argon2/scrypt.
- `decrypt` already fails closed (wraps exceptions in `NopException`) for tampered /
  truncated / unknown-format values.
- Consumers: `DefaultOrmColumnBinderEnhancer` (ORM `@enc` columns) and `ConfigStarter`
  config-value enhancer are the two framework consumers; the inventory of every
  `ITextCipher`/`IStreamCipher` consumer is Phase 1 work.

## Goals

- Every newly encrypted value uses a fresh random IV and a self-describing version marker.
- A modern KDF (chosen in DR-2a) derives the key, keyed off the version marker.
- Legacy ciphertext remains readable (read-only support or lazy migration per DR-2b/DR-2a).
- Tampered, truncated, and unknown-version values fail closed (preserve current behavior).

## Non-Goals

- Key-rotation tooling or secret-manager integration (deployment responsibility).
- Encrypting values that are not currently encrypted (no new `@enc` columns).
- Changing the `IStreamCipher` byte-stream contract beyond what the version marker requires.

## Scope

### In Scope

- `AESTextCipher` (defaults, ctor, `encrypt`/`decrypt`, `generateIv`, `concatIv`).
- `DefaultOrmColumnBinderEnhancer` wiring; `ConfigStarter.newValueEnhancer` wiring.
- Legacy ciphertext detection, read, and migration.
- The compatibility-boundary decision (global vs scoped).

### Out Of Scope

- Authentication/JWT (Plan 333).
- AI Bash isolation (Plan 335) and AI HTTP SSRF (Plan 336).
- Generated (`_`-prefixed) files.

## Execution Plan

> All Phases are `blocked` until the User-Authorization Gate is satisfied.

### Phase 1 - Consumer Inventory, Legacy Fixtures, And Compatibility Boundary

Status: blocked (pending DR-2a + DR-2b user approval)
Targets: `nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/impl/AESTextCipher.java`,
`nop-persistence/nop-orm/.../DefaultOrmColumnBinderEnhancer.java`,
`nop-core-framework/nop-config/.../ConfigStarter.java`, every `ITextCipher`/`IStreamCipher` consumer

- Item Types: `Decision | Proof`

- [ ] Inventory every `ITextCipher` / `IStreamCipher` consumer in the repo; record the list
  as the compatibility-boundary input for DR-2b.
- [ ] Freeze the DR-2b decision (global vs scoped) with the user disposition.
- [ ] Capture legacy-ciphertext fixtures (static-IV, MD5-KDF) as golden test inputs so the
  legacy-read path is regression-protected BEFORE any new encryption lands.

Exit Criteria:

- [ ] The consumer inventory list is recorded in this plan and matches a grep over the repo.
- [ ] Legacy fixtures exist and decrypt correctly against current code (baseline proof).
- [ ] DR-2b boundary decision recorded as `approved`.

### Phase 2 - Versioned Per-Message-IV Format

Status: blocked (pending Phase 1)
Targets: `AESTextCipher.java`

- Item Types: `Fix`

- [ ] Implement the version marker + per-message IV (`generateIv()` on every `encrypt`) +
  modern KDF per DR-2a, keyed off the version marker.
- [ ] Preserve legacy-read (recognize legacy format by absence of version marker / by the
  chosen detection rule) per the DR-2a legacy-detection decision.
- [ ] Enforce thread-safety ownership per the DR-2a concurrency decision (no mutable IV
  field shared across threads).

Exit Criteria:

- [ ] Focused tests: new-format encrypt produces distinct IVs across two encryptions of the
  same plaintext; legacy fixture decrypts correctly; new-format decrypt round-trips.
- [ ] Focused tests for failure semantics: tampered (GCM-tag failure), truncated, and
  unknown-version values each throw (fail closed), asserting the distinct cases.
- [ ] Concurrency test: parallel `encrypt` calls do not reuse an IV and do not corrupt state.
- [ ] **No silent no-op**: every failure path throws, never returns null/empty.
- [ ] `No owner-doc update required` unless the encrypted-value format is a documented
  contract; otherwise update the relevant `docs-for-ai/` section.
- [ ] `ai-dev/logs/` entry for the execution day.

### Phase 3 - ORM And Config Enhancer Wiring (entry-to-sink)

Status: blocked (pending Phase 2)
Targets: `DefaultOrmColumnBinderEnhancer.java`, `ConfigStarter.newValueEnhancer`

- Item Types: `Fix | Proof`

- [ ] Wire the versioned format into both framework consumers per the DR-2b boundary.
  - If global: both consumers pick up the new `AESTextCipher` defaults automatically.
  - If scoped: introduce the versioned format at each entry point explicitly.
- [ ] Prove entry-to-sink wiring: an ORM `@enc` column write produces a versioned ciphertext
  and a subsequent read returns the original plaintext; a config-value enhancer decrypts a
  versioned config value at startup.

Exit Criteria:

- [ ] **接线验证 (Wiring Verification, Rule #23)**: ORM round-trip test writes then reads
  an `@enc` column through the binder; config-enhancer test reads a versioned value at
  `ConfigStarter` init. Both assert the new format is actually used (version marker present).
- [ ] **端到端 (End-to-End, Rule #22)**: at least one test writes a value via the ORM
  mapper, reads it back, and confirms plaintext equality end-to-end (not just cipher unit).
- [ ] Legacy-ORM-column read test: an ORM column encrypted under the legacy format still
  reads correctly after the change.
- [ ] `./mvnw test -pl nop-kernel/nop-commons,nop-persistence/nop-orm,nop-core-framework/nop-config -am -T 1C` green.
- [ ] `ai-dev/logs/` entry for the execution day.

## Closure Gates

- [ ] M-7 resolved: no newly encrypted value reuses an IV; KDF is modern; legacy reads work.
- [ ] DR-2a and DR-2b decisions each have a landed implementation matching the recorded
  disposition.
- [ ] Tampered/truncated/unknown-version values fail closed with focused tests for each.
- [ ] Compatibility boundary matches the recorded inventory (no consumer left on the static
  IV by accident).
- [ ] `./mvnw clean install -pl nop-kernel/nop-commons,nop-persistence/nop-orm,nop-core-framework/nop-config -am -T 1C -DskipTests` builds.
- [ ] `./mvnw test -pl <affected modules> -am -T 1C` green.
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.
- [ ] Independent closure audit recorded in `Closure`.

## Deferred But Adjudicated

### User Authorization Of DR-2 Decision Records

- Classification: `blocked (not a residual — a hard prerequisite)`
- Why Not Blocking Closure Of Plan 328: this is a successor plan; Plan 328 closed on
  having created this draft.
- Successor Required: this plan IS the successor; it activates only after user `approved`.

## Non-Blocking Follow-ups

- Lazy re-encryption of legacy rows on next write (optimization candidate).

## Closure

Status Note: Draft created by Plan 328 Phase 2. Blocked at draft pending user authorization
of DR-2a and DR-2b. No implementation has begun.
Completed: N/A

Closure Audit Evidence:

- Reviewer / Agent: N/A (draft, not yet completed)
- Evidence: N/A

Follow-up:

- User must record `approved`/`rejected`/`deferred` for DR-2a and DR-2b before this plan
  can be promoted to `active`.
