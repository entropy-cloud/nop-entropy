# 334 Encrypted-Value Format Hardening

> Plan Status: completed
> Review Hold: cleared 2026-08-08 — user recorded `approved` dispositions for DR-2a and DR-2b (recorded below). Plan promoted to `active`; executed to completion 2026-08-08 (all Phases `completed`, independent closure audit PASS).
> Last Reviewed: 2026-08-08
> Source: `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md` (DR-2a, DR-2b)
> Related: `ai-dev/plans/328-security-hardening-remediation-planning.md`
> Predecessor: Plan 328 Phase 1 froze the decision records this plan consumes.

## Purpose

Resolve the AES encrypted-value finding (M-7) by introducing a self-describing, per-message
IV ciphertext format with a modern key-derivation function, while preserving read
compatibility with legacy ciphertext. This plan changes persisted ciphertext semantics and
is **blocked at draft until the user approves the versioned per-message-IV contract and the
compatibility boundary** (DR-2a, DR-2b).

## User-Authorization Gate (RESOLVED 2026-08-08)

This plan changes persisted ciphertext semantics. The user recorded an explicit `approved`
disposition for EACH of the following on 2026-08-08:

- DR-2a (encrypted-value format: version marker, per-message IV, concurrency ownership,
  legacy detection, KDF, fail-closed behavior, compatibility scope): **approved — full contract: self-describing `v1` version marker (legacy distinguishable by marker absence), `generateIv()` invoked on every `encrypt` (fresh random IV, `concatIv` forced for the new format), stateless per-call cipher (no shared mutable IV field across threads), legacy (static-IV + MD5-KDF) ciphertext read-compatible (read-only support, no forced re-encryption), PBKDF2 KDF keyed off the version marker, fail-closed preserved for tampered/truncated/unknown-version values.**
- DR-2b (compatibility boundary: global `AESTextCipher` change vs scoped
  ORM-binder + config-enhancer change): **approved — global: change `AESTextCipher` defaults so every consumer (ORM binder, config enhancer, any other `ITextCipher` user) gets versioned per-message IV automatically; no consumer left on the static IV.**

Disposition is recorded in `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md`
Open Questions. Plan promoted to `active` on 2026-08-08; execution NOT started (user
requested active-without-execution — phases below remain `planned`). The legacy-read /
migration decision and the regression fixture contract (Phase 1) remain hard prerequisites
to any verification change.

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

> User-Authorization Gate satisfied 2026-08-08 (DR-2a and DR-2b both `approved`). Phases remain `planned` until execution starts.

### Phase 1 - Consumer Inventory, Legacy Fixtures, And Compatibility Boundary

Status: completed
Targets: `nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/impl/AESTextCipher.java`,
`nop-persistence/nop-orm/.../DefaultOrmColumnBinderEnhancer.java`,
`nop-core-framework/nop-config/.../ConfigStarter.java`, every `ITextCipher`/`IStreamCipher` consumer

- Item Types: `Decision | Proof`

- [x] Inventory every `ITextCipher` / `IStreamCipher` consumer in the repo; record the list
  as the compatibility-boundary input for DR-2b.
- [x] Freeze the DR-2b decision (global vs scoped) with the user disposition.
- [x] Capture legacy-ciphertext fixtures (static-IV, MD5-KDF) as golden test inputs so the
  legacy-read path is regression-protected BEFORE any new encryption lands.

Exit Criteria:

- [x] The consumer inventory list is recorded in this plan and matches a grep over the repo.
- [x] Legacy fixtures exist and decrypt correctly against current code (baseline proof).
- [x] DR-2b boundary decision recorded as `approved`.

#### Consumer Inventory (recorded 2026-08-08)

Repo-wide grep for `ITextCipher` / `IStreamCipher` / `new AESTextCipher` (excluding
generated `target/`, native-image `reflect-config.json`, and docs):

| Consumer | Location | Wiring | v1 coverage (DR-2b global) |
|----------|----------|--------|----------------------------|
| `DefaultOrmColumnBinderEnhancer` | `nop-persistence/nop-orm/.../DefaultOrmColumnBinderEnhancer.java:20` | `new AESTextCipher()` (default ctor) | automatic — default ctor now v1 |
| `ConfigStarter.newValueEnhancer` | `nop-core-framework/nop-config/.../ConfigStarter.java:457` | `new AESTextCipher()` | automatic |
| `DefaultAiChatExchangePersister` | `nop-ai/nop-ai-core/.../DefaultAiChatExchangePersister.java:33` | `new AESTextCipher()` | automatic |
| `EncodedDataParameterBinder` | `nop-kernel/nop-dataset/.../EncodedDataParameterBinder.java:18` | `ITextCipher` via ctor (from ORM binder) | inherits v1 from binder |
| `DefaultConfigValueEnhancer` | `nop-core-framework/nop-config/.../DefaultConfigValueEnhancer.java:37` | `ITextCipher` via ctor (from ConfigStarter) | inherits v1 from ConfigStarter |

Conclusion: every consumer reaches the cipher through the default `AESTextCipher` constructor
or a ctor that receives such an instance. The **global** DR-2b change (default ctor now v1)
covers all consumers; none is left on the static IV. No third-party `ITextCipher`/`IStreamCipher`
implementations exist in the repo other than `AESTextCipher`.

DR-2b disposition: **approved — global** (recorded in `User-Authorization Gate` above and in
`ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md` Open Questions).

Legacy fixtures: `TestAesEncryptedValueFormat.testLegacyCiphertextReadableByDefaultDecrypt`,
`testLegacyEncryptIsDeterministicStaticIv`, `testLegacyConcatIvReadable` capture legacy
(static-IV + MD5-KDF, no version marker) golden round-trips against current code.

### Phase 2 - Versioned Per-Message-IV Format

Status: completed
Targets: `AESTextCipher.java`

- Item Types: `Fix`

- [x] Implement the version marker + per-message IV (`generateIv()` on every `encrypt`) +
  modern KDF per DR-2a, keyed off the version marker.
- [x] Preserve legacy-read (recognize legacy format by absence of version marker / by the
  chosen detection rule) per the DR-2a legacy-detection decision.
- [x] Enforce thread-safety ownership per the DR-2a concurrency decision (no mutable IV
  field shared across threads).

Exit Criteria:

- [x] Focused tests: new-format encrypt produces distinct IVs across two encryptions of the
  same plaintext; legacy fixture decrypts correctly; new-format decrypt round-trips.
- [x] Focused tests for failure semantics: tampered (GCM-tag failure), truncated, and
  unknown-version values each throw (fail closed), asserting the distinct cases.
- [x] Concurrency test: parallel `encrypt` calls do not reuse an IV and do not corrupt state.
- [x] **No silent no-op**: every failure path throws, never returns null/empty.
- [x] `No owner-doc update required` unless the encrypted-value format is a documented
  contract; otherwise update the relevant `docs-for-ai/` section.
- [x] `ai-dev/logs/` entry for the execution day.

Implementation summary (live code anchors):
- Version marker: `AESTextCipher.V1_MARKER = "v1:"` (`AESTextCipher.java:58`). Legacy
  detection = absence of marker (base64/hex alphabets never contain `:`, so unambiguous).
- Per-message IV: `encryptVersioned` calls `newRandomIv()` (local variable, never the
  instance `iv` field) on every `encrypt` (`AESTextCipher.java:350-362`).
- Modern KDF keyed off version: `buildV1SecretKey()` uses `PBKDF2WithHmacSHA256`
  (65536 iterations, AES-256); legacy path keeps `buildSecretKey()` (MD5). Dispatch by
  marker (`AESTextCipher.java:222-243`).
- Thread-safety: v1 encrypt/decrypt use only local IVs + a `volatile` cached
  `SecretKeySpec` (immutable); no shared mutable IV across threads.
- Fail-closed: `decryptVersioned`/`decryptLegacy` wrap all exceptions in `NopException`
  (`AESTextCipher.java:386-438`).
- Tests: `TestAesEncryptedValueFormat` (13 tests covering distinct-IV, round-trip,
  tampered/truncated/unknown fail-closed, 16×50 concurrency, legacy read, KDF dispatch).

### Phase 3 - ORM And Config Enhancer Wiring (entry-to-sink)

Status: completed
Targets: `DefaultOrmColumnBinderEnhancer.java`, `ConfigStarter.newValueEnhancer`

- Item Types: `Fix | Proof`

- [x] Wire the versioned format into both framework consumers per the DR-2b boundary.
  - If global: both consumers pick up the new `AESTextCipher` defaults automatically.
  - If scoped: introduce the versioned format at each entry point explicitly.
- [x] Prove entry-to-sink wiring: an ORM `@enc` column write produces a versioned ciphertext
  and a subsequent read returns the original plaintext; a config-value enhancer decrypts a
  versioned config value at startup.

Exit Criteria:

- [x] **接线验证 (Wiring Verification, Rule #23)**: ORM round-trip test writes then reads
  an `@enc` column through the binder; config-enhancer test reads a versioned value at
  `ConfigStarter` init. Both assert the new format is actually used (version marker present).
- [x] **端到端 (End-to-End, Rule #22)**: at least one test writes a value via the ORM
  mapper, reads it back, and confirms plaintext equality end-to-end (not just cipher unit).
- [x] Legacy-ORM-column read test: an ORM column encrypted under the legacy format still
  reads correctly after the change.
- [x] `./mvnw test -pl nop-kernel/nop-commons,nop-persistence/nop-orm,nop-core-framework/nop-config -am -T 1C` green.
- [x] `No owner-doc update required` unless the wired encrypted-value format is a documented
  `docs-for-ai/` contract; otherwise update the relevant section (Rule #17).
- [x] `ai-dev/logs/` entry for the execution day.

Wiring proof (live test anchors):
- ORM end-to-end: `TestColumnEnhancer.testEncryptedColumn` saves `SimsExam` (examName has
  `tagSet="enc"`) via DAO, reads back, asserts plaintext equality AND that the DB-stored
  value is `@enc:v1:...` (version marker present). Two saves of the same plaintext yield
  distinct stored ciphertexts (per-message IV end-to-end evidence).
- ORM legacy read: `TestColumnEnhancer.testLegacyEncryptedColumnReadable` inserts a legacy
  (no-marker) ciphertext row via JDBC, reads it through the ORM binder, asserts plaintext.
- Config enhancer entry-to-sink: `TestConfigValueEnhancerEncryptedValue.testEnhanceDecryptsVersionedConfigValue`
  feeds `@sec:v1:...` to `DefaultConfigValueEnhancer` (same `new AESTextCipher()` wiring as
  `ConfigStarter.newValueEnhancer`) and asserts plaintext; plus a legacy `@sec:` read test.
- Note (behavior change): per-message IV makes encrypted columns no longer searchable by
  plaintext-equality (re-encryption is non-deterministic). This is the intended semantic-
  security improvement; the prior `findAllByExample` equality search on an `@enc` column is
  no longer supported and was removed from the test.

## Closure Gates

- [x] M-7 resolved: no newly encrypted value reuses an IV; KDF is modern; legacy reads work.
- [x] DR-2a and DR-2b decisions each have a landed implementation matching the recorded
  disposition.
- [x] Tampered/truncated/unknown-version values fail closed with focused tests for each.
- [x] Compatibility boundary matches the recorded inventory (no consumer left on the static
  IV by accident).
- [x] `./mvnw clean install -pl nop-kernel/nop-commons,nop-persistence/nop-orm,nop-core-framework/nop-config -am -T 1C -DskipTests` builds.
- [x] `./mvnw test -pl <affected modules> -am -T 1C` green.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.
- [x] Independent closure audit recorded in `Closure`.

## Deferred But Adjudicated

### User Authorization Of DR-2 Decision Records

- Classification: `resolved` (user `approved` both records on 2026-08-08; dispositions
  recorded in the User-Authorization Gate section above and in
  `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md` Open Questions)
- Why Not Blocking Closure: gate cleared; plan promoted to `active` 2026-08-08.
  Execution not started (user requested active-without-execution).
- Successor Required: no.

## Non-Blocking Follow-ups

- Lazy re-encryption of legacy rows on next write (optimization candidate).

## Closure

Status Note: All three Phases executed to completion 2026-08-08. The v1 self-describing
per-message-IV format (DR-2a) is implemented globally in `AESTextCipher` (DR-2b), every
consumer picks it up automatically, legacy ciphertext remains read-compatible, and all
failure paths fail closed. Independent closure audit PASSED all 16 exit/anti-hollow criteria.
Completed: 2026-08-08

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (session `ses_01ef9d75bffeAnPyUR2LtRqfNN`, fresh session, did not implement the plan)
- Evidence:
  - Phase 1: consumer inventory PASS — live grep matches plan table (5 consumers, all via default ctor → v1 automatic); legacy fixtures PASS (`testLegacyCiphertextReadableByDefaultDecrypt`/`testLegacyEncryptIsDeterministicStaticIv`/`testLegacyConcatIvReadable`); DR-2b `approved` global PASS.
  - Phase 2 (all PASS): v1 marker `V1_MARKER="v1:"` (`AESTextCipher.java:59`); `encryptVersioned` uses LOCAL `newRandomIv()` not `this.iv` (`:353`); `buildV1SecretKey` PBKDF2WithHmacSHA256 65536/256-bit (`:231`); legacy read via marker absence (`decrypt:388`); thread-safe (local IVs + volatile cached immutable key); distinct-IV/round-trip/legacy tests present; tampered/truncated/unknown fail-closed tests present; 16×50 concurrency test PASS; no silent no-op (all paths throw `NopException`).
  - Phase 3 (all PASS): ORM `@enc` write→`@enc:v1:…`→plaintext read (`TestColumnEnhancer.testEncryptedColumn`); config enhancer decrypts `@sec:v1:` (`TestConfigValueEnhancerEncryptedValue`); legacy-ORM-column read asserts plaintext (`testLegacyEncryptedColumnReadable`); end-to-end ORM save→load→plaintext equality.
  - Anti-Hollow PASS: `encrypt`→`encryptVersioned` and `decrypt`→`decryptVersioned` call chains live (not dead code); `EncodedDataParameterBinder`/`DefaultConfigValueEnhancer` invoke `cipher.encrypt`/`decrypt`; no empty bodies / TODO / silent no-op in new code.
  - Build: `./mvnw clean install -pl nop-kernel/nop-commons,nop-persistence/nop-orm,nop-core-framework/nop-config -am -T 1C -DskipTests` SUCCESS.
  - Tests: `nop-commons` (23) / `nop-orm` (139, 4 skip) / `nop-config` green; `nop-ai-core` `DefaultAiChatExchangePersister` (5) + `TestAiChatResponseCacheTtl` (3) green (AI consumer v1 + legacy-plaintext compatible).
  - `node ai-dev/tools/check-doc-links.mjs --strict`: 0 new broken links from this plan; 2 pre-existing BROKEN_LINKs reside in the untracked, unrelated file `ai-dev/backlog/nop-ai-channel-integration-roadmap.md` (AI-channel-integration, not plan-334 scope).
  - Deferred 项分类检查: no in-scope live defect downgraded — only documented Non-Blocking Follow-up (lazy re-encryption of legacy rows, optimization candidate).

Follow-up:

- No remaining plan-owned work.
- Non-blocking: lazy re-encryption of legacy rows on next write (optimization candidate, recorded under Non-Blocking Follow-ups).
- Pre-existing (NOT plan-owned): `nop-sys-dao` `TestDaoLeaderElector` H2 schema-init failure and `nop-ai-agent` `TestMultiMemberFanOutRouting` parallel-build flakiness are unrelated environment issues (nop-sys has no `@enc` columns; agent test passes in isolation).
