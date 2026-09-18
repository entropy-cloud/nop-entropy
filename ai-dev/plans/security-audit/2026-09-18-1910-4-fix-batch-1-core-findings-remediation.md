# Security Audit Fix Batch 1 — Core Framework Findings Remediation

> Plan Status: completed
> Last Reviewed: 2026-09-18
> Mission: security-audit
> Work Item: 10. 修复批次1 — item 1 核心框架审计发现修复（roadmap Phase 3 动态项）
> Source: `ai-dev/audits/security-audit/2026-09-18-core-audit-CORE-01.md` .. `CORE-05.md`
> (adjudicated findings), `ai-dev/backlog/security-audit-roadmap.md`
> Related: `ai-dev/plans/security-audit/2026-09-17-0831-1-core-framework-security-controls-audit.md`
> (completed audit plan), `ai-dev/plans/344-check-audit-p1-p2-p3-remediation.md` (P2-9 precedent)
> Naming: date-prefixed name follows the `ai-dev/plans/security-audit/` mission-subdirectory
> convention (see plan 1 header note; guide rule 21 applies to the plans root only).

## Purpose

Remediate every `remediation-target` finding from the completed item-1 core
framework audit, each with a regression test, and sync owner docs. Adjudicated
items are NOT re-litigated. This plan is the first roadmap Phase 3 (fix phase)
batch.

**Authorization note**: the roadmap's default ordering generates fix items only
after consolidation item 9 (which waits for audits 2-8). On 2026-09-18 the user
explicitly instructed 自动修复所有问题 — pulling this fix batch forward for
item-1 findings. The deviation is recorded here now and will be recorded in the
roadmap Phase 3 block (Phase 2 of this plan) and the daily log (per-phase
entries). Audits 2-8 and consolidation item 9 remain pending per the roadmap;
their findings will produce later fix batches.

## Current Baseline

Verified against live repo on 2026-09-18 (branch audit/security, HEAD 54a6393a05):

- Item-1 audit is closed (`done`); five reports exist under
  `ai-dev/audits/security-audit/`; findings adjudicated with owner = item 9
  consolidation. This batch consumes the `remediation-target` subset now under
  user authorization.
- F-C3-1 anchors (MEDIUM): `AESTextCipher.java:82` `encKey` defaults from
  `nop.crypt.default-enc-key` (`""` per CommonConfigs.java:64);
  `buildSecretKey()` L206-214 (legacy `MD5(encKey+saltKey)`),
  `buildV1SecretKey()` L227-248 (PBKDF2 with `DEFAULT_V1_SALT` fixed
  package-private constant, L67-68) — no warning anywhere on the empty-key
  path; `ConfigStarter.java:461-470`
  only calls `setEncKey` when non-empty; default wiring
  `nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml:36`
  injects no cipher key. Class currently imports no Logger. Both key builders
  have cache-hit early-returns; a `secretKey(SecretKeySpec)` direct-injection
  builder exists (L197-200, zero main-code callers repo-wide).
- F-C1-1 (LOW): `CompositeKeyManager.java` resolves first-match-wins with no
  duplicate-ID diagnostic and no ordering Javadoc.
- F-C1-2 (LOW): `DefaultKeyManager.java:19` defaults `storeType` to JKS; the
  field is private with NO setter — storeType is currently not configurable
  via beans.xml at all (reviewer-verified: no `setStoreType` repo-wide).
- F-C1-3 (LOW): `DefaultKeyManager.destroy()` (L39-43) nulls the KeyStore but
  never zeroes the `storePassword` char[].
- F-C4-1 (LOW): `FilterBeanToSQLTransformer.java:78-81` gates identifier
  validation on the caller-side `checkVarName` flag; `SqlBuilder` interpolates
  raw strings. All in-repo main-code callers pass `true`
  (DaoQueryHelper.java:210, FilterSqlHelper.java:27); the `false` constructor
  path has zero main-code and (to be re-verified) zero test callers.
- F-C4-2 (LOW owner-doc drift): `docs-for-ai/02-core-guides/tenant-model.md:84`
  comment uses fictional API `dao().executeNativeSql("...")`.
- Test baselines: `TestAesEncryptedValueFormat` / `TestTextCipher`
  (io.nop.commons.crypt, plain JUnit), `TestKeyManager` /
  `TestKeySetHelperJwks` (io.nop.security.key, CoreInitialization /
  plain JUnit), no dedicated `FilterBeanToSQLTransformer` test class located
  yet (to be confirmed in Phase 1; if absent, create one).
- Module test suites green as of 2026-09-18 (nop-commons within
  `-pl nop-kernel/nop-core -am` chain, nop-security within
  `-pl nop-core-framework -am` chain, nop-core green, nop-persistence green —
  `_tmp/security-audit/phase2-tests.log`).

## Goals

- F-C3-1a: one-time-per-instance WARN (English, message-code style) from
  `AESTextCipher` when key derivation runs with an empty enc-key, covering both
  v1 and legacy paths; test-visible via a package-private accessor.
- F-C1-1: `CompositeKeyManager` documents first-match-wins ordering in Javadoc
  and WARNs (listing duplicated IDs) when `getCertificateIds()` aggregate
  contains duplicates.
- F-C1-2: `DefaultKeyManager` Javadoc on the `storeType` field recommends
  PKCS12 for new deployments and notes the field is currently fixed at JKS
  (no setter — not configurable via beans.xml); default unchanged
  (documentation route per audit remediation option B; rationale in Deferred
  section).
- F-C1-3: `destroy()` zeroes `storePassword` before nulling references.
- F-C4-1: unconditional SQL-metacharacter blacklist inside
  `validateVarName` (applies even when `checkVarName=false`) so the
  trusted-path cannot interpolate quotes/whitespace/semicolons/parens/comment
  markers; reuse `ERR_SQL_FILTER_INVALID_FIELD_NAME`.
- F-C4-2: tenant-model.md:84 example replaced with the real native-SQL seam.
- F-C3-1c: `docs-for-ai/02-core-guides/ioc-and-config.md` `@sec:` section gains
  a production-prerequisite note (configure enc-key; empty key now WARNs).
- F-C5-1: roadmap assigns nop-dyn re-verification to item 4's scope; roadmap
  Phase 3 block records this batch as item 10 with the user-authorization note.

## Non-Goals

- No re-litigation of F-C2-1 (plugin expectedHashes priority — plan-344 P2-9
  recorded deferral) and F-C3-PMD-1 (false positive).
- No fail-closed/strict-mode redesign of the cipher (audit remediation offered
  WARN-or-strict; this batch ships the WARN route only — see Deferred).
- No change to JKS default storeType (documentation route chosen).
- No auth/credential/API findings (items 2-8 own them; not yet audited).
- No change to `orm-defaults.beans.xml` wiring (deployment supplies keys; the
  WARN makes the weak default visible instead).

## Scope

### In Scope

- `nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/impl/AESTextCipher.java`
  (+ new same-package test)
- `nop-core-framework/nop-security/src/main/java/io/nop/security/key/CompositeKeyManager.java`,
  `DefaultKeyManager.java` (+ tests in io.nop.security.key)
- `nop-kernel/nop-core/src/main/java/io/nop/core/lang/sql/FilterBeanToSQLTransformer.java`
  (+ test in io.nop.core.lang.sql)
- `docs-for-ai/02-core-guides/ioc-and-config.md`,
  `docs-for-ai/02-core-guides/tenant-model.md`
- `ai-dev/backlog/security-audit-roadmap.md` (Phase 3 block + item 4 scope note)
- `ai-dev/logs/2026/09-18.md`

### Out Of Scope

- JWT enc-key / password policy gaps (roadmap item 2 baseline gaps — auth plan owns)
- nop-credential Vault chain (item 3)
- Plugin strict-hash mode (F-C2-1 precedent)
- F-C5-2 watch-only residual (reassessment note only; see Deferred)

## Execution Plan

### Phase 1 - Product Code Hardening With Regression Tests

Status: completed
Targets: AESTextCipher.java, CompositeKeyManager.java, DefaultKeyManager.java,
FilterBeanToSQLTransformer.java + test files

- Item Types: `Fix | Proof`

- [x] F-C3-1a: add slf4j Logger + `volatile boolean emptyKeyWarned` guard to
  AESTextCipher; in `buildSecretKey()` and `buildV1SecretKey()`, AFTER the
  cache-hit early-return (so direct `secretKey(SecretKeySpec)` injection is
  never mis-warned) and BEFORE the derivation, when
  `StringHelper.isEmpty(encKey)`, emit the WARN
  (`nop.crypt.empty-enc-key: ... configure nop.config.encrypt-key or
  nop.crypt.default-enc-key ...`); concurrent first-derivation may emit the
  WARN twice — tolerated (idempotent noise); add package-private
  `boolean wasEmptyKeyWarned()`.
- [x] New test `io.nop.commons.crypto.impl.TestAESTextCipherEmptyKeyWarn`
  (plain JUnit, same package): empty-key instance flips accessor after first
  encrypt (v1 path); legacy path covered via instance A
  (`versionedFormat(false)`) encrypting, then a FRESH empty-key instance B
  decrypting A's ciphertext and asserting B's flag flips (decrypting garbage
  directly would throw on GCM tag — use A→B construction); configured-key
  instance never flips; accessor stable across repeated calls.
- [x] F-C1-1: CompositeKeyManager class Javadoc (first-match-wins, order =
  constructor list order); `getCertificateIds()` computes duplicates and WARNs
  with the duplicated IDs.
- [x] F-C1-3: DefaultKeyManager.destroy() zeroes storePassword (if non-null)
  then nulls it; Javadoc notes the wipe. Test construction (reviewer M-2):
  capture the char[] field reference via reflection BEFORE destroy, then
  after destroy assert the CAPTURED array is all `'\u0000'` (same memory —
  verifiable regardless of the field being nulled).
- [x] F-C1-2: Javadoc on the storeType field recommending PKCS12 for new
  deployments and noting the field is currently fixed at JKS with no setter
  (default unchanged, compat rationale in Javadoc).
- [x] Tests in `io.nop.security.key` (extend TestKeyManager or new
  TestCompositeKeyManager): duplicate-ID aggregation returns all IDs without
  throw; first-match resolution returns the first manager's certificate for a
  shared ID; destroy() zeroes password per the capture-then-assert
  construction above.
- [x] F-C4-1: add SQL-metacharacter blacklist (whitespace, `'`, `"`, `;`, `(`,
  `)`, backslash, backtick, `[`, `]`, `#`, `--`, `/*`) checked unconditionally
  in `validateVarName` AFTER the existing checkVarName-gated isValidPropPath
  check (both checks throw ERR_SQL_FILTER_INVALID_FIELD_NAME); keep null-safe.
  (All blacklist chars are non-identifier chars, so the checkVarName=true
  path's behavior is strictly unchanged — reviewer-verified against
  isValidPropPath.)
- [x] Transformer tests (no existing transformer test class — create new
  `TestFilterBeanToSQLTransformerMetachar` in io.nop.core.lang.sql, plain
  JUnit):
  checkVarName=false + name `"a;drop"` throws; checkVarName=false + owner
  `"o(1)"` throws; checkVarName=false + legit name `"o.status"` passes and
  renders parameterized SQL; checkVarName=true still throws for invalid
  prop-path (existing behavior locked).
- [x] Pre-edit safety grep: confirm no caller passes names containing
  metacharacters through `checkVarName=false` (repo-wide constructor grep +
  any `validateVarName` overrides). [Executed 2026-09-18: zero `false`
  constructors repo-wide (main + test), zero subclasses/overrides.]
- [x] F-C5-2 evidence note (for closure record): `TestEqlTenantPropJoin:136,144`
  already asserts `NOP_TENANT_ID = ?` occurrences in compiled EQL SELECT SQL
  (join ON + WHERE); the by-PK load-SQL assertion is still missing — residual
  stays with item 9 (do NOT claim resolved-by-evidence beyond select path).

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] All four code changes compile (`./mvnw test-compile -pl
  nop-kernel/nop-commons,nop-core-framework/nop-security,nop-kernel/nop-core -am`).
- [x] Every fix has a test that verifies the new behavior (warn-flag flip,
  duplicate aggregation + first-match lock, password zeroing, metachar
  rejection on the false path) — not just absence of error (rule: Test-Mandated
  Feature / bug-fix coverage).
- [x] `./mvnw test -pl nop-kernel/nop-commons,nop-core-framework/nop-security,nop-kernel/nop-core,nop-persistence/nop-orm -am`
  passes (nop-orm included because DaoQueryHelper/FilterSqlHelper consume the
  transformer).
- [x] Style: imports grouped io.nop.* → third-party → java.*; no unused imports.
- [x] No silent no-op introduced; no behavior removed (WARN and blacklist are
  additive).
- [x] Owner-doc update deferred to Phase 2 by design (ioc-and-config.md +
  tenant-model.md items listed there).
- [x] `ai-dev/logs/` entry updated.

### Phase 2 - Documentation And Governance Sync

Status: completed
Targets: ioc-and-config.md, tenant-model.md, security-audit-roadmap.md

- Item Types: `Fix | Decision`

- [x] F-C3-1c: in ioc-and-config.md `@sec:` section, add a short
  production-prerequisite note: enc-key must be configured
  (`nop.config.encrypt-key` / `nop.crypt.default-enc-key`; salt recommended);
  empty key keeps dev-mode working but logs a one-time WARN and offers no
  confidentiality against anyone with source access.
- [x] F-C4-2: fix tenant-model.md:84 comment to use the real seam
  (`IOrmSession.executeUpdate/executeQuery` with `io.nop.core.lang.sql.SQL`),
  preserving the "不经过 EQL 编译" semantics; verify no other doc references
  `executeNativeSql`.
- [x] F-C5-1 + roadmap: add item 10 to the roadmap Phase 3 work-item block
  (`修复批次1`) with initial status `todo` flipping to `planned` once this
  plan passes adversarial review and is activated (status-vocabulary
  alignment); add user-authorization note (fix phase pulled forward under
  explicit instruction before items 2-8 complete); extend item 4 Module/area
  with `nop-dyn` re-verification (from CORE-05 F-C5-1).

Exit Criteria:

- [x] Both docs edited; `node ai-dev/tools/check-doc-links.mjs --strict` exit 0.
- [x] Roadmap contains item 10 + authorization note + item-4 nop-dyn scope note;
  no other work-item statuses changed.
- [x] `ai-dev/logs/` entry updated.

### Phase 3 - Verification And Closure

Status: completed
Targets: this plan, roadmap, daily log

- Item Types: `Proof`

- [x] Run the full verification command from Phase 1 (or document per-module
  equivalents) and record results in the daily log.
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/security-audit/2026-09-18-1910-4-fix-batch-1-core-findings-remediation.md --strict`
  exit 0 (pre-completion). NOTE: invoke with the repo-root RELATIVE path —
  the tool joins `process.cwd()` with the argument, so an absolute path
  fails with "Plan file not found"; its default scan also does not recurse
  into `plans/security-audit/`, so the explicit path is required.
- [x] Independent fresh-session closure audit (subagent) verifying each fix in
  live code + tests; write evidence into `## Closure`.
- [x] Textual consistency pass; roadmap item 10 → `done` only after closure
  audit; commit with mission format `security(audit): ...`.

Exit Criteria:

- [x] Closure audit ALLOW recorded in plan `## Closure` (reviewer id, per-fix
  verification, tool exit codes).
- [x] All Closure Gates checked; `Plan Status: completed`.
- [x] Roadmap item 10 `done`; other items untouched.
- [x] Committed; daily log closure entry written.

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] All remediation-target findings from item-1 audit (F-C3-1, F-C1-1/2/3,
  F-C4-1, F-C4-2, F-C5-1) are addressed in this plan (fixed here or explicitly
  adjudicated below); zero dropped.
- [x] Every code fix carries a regression test that fails without the fix's
  behavior.
- [x] Affected-module tests pass (`nop-commons`, `nop-security`, `nop-core`,
  `nop-orm` chain).
- [x] Owner docs synced (ioc-and-config.md, tenant-model.md) or explicitly
  N/A.
- [x] Roadmap Phase 3 block + item 4 scope updated; no other statuses touched.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0.
- [x] `node ai-dev/tools/check-plan-checklist.mjs` (repo-root RELATIVE path to
  this plan, see Phase 3 note) `--strict` exit 0.
- [x] Independent sub-agent closure audit completed with evidence recorded in
  `## Closure`.
- [x] Anti-Hollow check: closure audit verified each fix is actually wired
  (WARN site reachable from both key-derivation paths, after the cache-hit
  early-returns; blacklist inside `validateVarName` reached by
  `visitCompareOp`; destroy() wipe verified by direct test call — no live
  beans.xml lifecycle wiring exists for DefaultKeyManager anywhere in the
  repo, so `@PreDestroy` annotation + direct-call test is the verifiable
  scope) and no empty-body/no-op was introduced.
- [x] Textual consistency: Plan Status, phase statuses, checklists, gates,
  roadmap, daily log all agree.

## Deferred But Adjudicated

### F-C2-1 expectedHashes strict-hash mode

- Classification: `out-of-scope improvement` (adjudicated-no-fix at audit time)
- Why Not Blocking Closure: plan-344 P2-9 recorded deferral with rationale
  (hash priority was an explicit design-05 W7 decision); re-litigation
  prohibited by plan 1 Non-Goals; pointer remains with item 9 consolidation.
- Successor Required: no
- Successor Path: N/A (item 9 triage)

### Cipher fail-closed / strict mode (beyond F-C3-1a WARN)

- Classification: `optimization candidate`
- Why Not Blocking Closure: WARN makes the weak default visible; fail-closed
  would break the documented development mode and every existing test that
  relies on empty-key dev defaults (plan-328: test/development defaults are
  deployment constraints, not framework defects). A strict flag is a config
  surface decision for the consolidation phase.
- Successor Required: no
- Successor Path: item 9 triage

### JKS → PKCS12 default switch (F-C1-2 code route)

- Classification: `watch-only residual`
- Why Not Blocking Closure: zero in-repo consumers (dormant surface); JDK
  keystore compat mode blurs the boundary; documentation route chosen to avoid
  breaking JKS deployments on upgrade.
- Successor Required: no
- Successor Path: N/A

### F-C5-2 tenant load-SQL predicate test assertion

- Classification: `watch-only residual`
- Why Not Blocking Closure: enforcement exists (compile-time predicate +
  post-load check, verified in CORE-05); residual is a test-assertion nicety.
  Evidence precision (reviewer m-2): `TestEqlTenantPropJoin:136,144` already
  asserts `NOP_TENANT_ID = ?` in compiled EQL SELECT SQL (join ON + WHERE) —
  the EQL select path IS asserted; the by-PK load-SQL assertion (audit's
  literal scope) is still missing and stays with item 9.
- Successor Required: no
- Successor Path: item 9 triage

### F-C4-1 render-boundary residual (SqlBuilder direct-call path)

- Classification: `watch-only residual`
- Why Not Blocking Closure: this plan closes the `checkVarName=false`
  transformer bypass (unconditional blacklist in `validateVarName`, covering
  all owner/name/valueName interpolation points in the transformer). The
  audit's second hole — a future caller interpolating raw strings directly
  via `SqlBuilder.append/.sql` — remains open by design (render-time
  validation would break the builder's legitimate literal usage); residual
  owned by item 9 consolidation.
- Successor Required: no
- Successor Path: item 9 triage

## Non-Blocking Follow-ups

- Item 9 consolidation will merge this batch's outcome with items 2-8 findings.

## Closure

Status Note: Fix-batch-1 closed after independent closure audit returned
ALLOW. All remediation-target findings from the item-1 audit landed in live
code with regression tests; docs and roadmap synced; adjudicated items
(F-C2-1, strict-mode, JKS code-route, F-C5-2, SqlBuilder render-boundary
residual) recorded with reasons and successor owners.
Completed: 2026-09-18

Closure Audit Evidence:

- Reviewer / Agent: independent fresh-session closure auditor subagent
  (agent_177a8e3e, 32 tool uses against live repo), verdict **ALLOW**.
- Per-fix verification (all PASS, live-source anchors re-read): AESTextCipher
  WARN after cache early-returns in both buildSecretKey/buildV1SecretKey;
  CompositeKeyManager Javadoc + duplicate-ID WARN; DefaultKeyManager
  destroy() fill('\0')+null and storeType Javadoc; FilterBeanToSQLTransformer
  unconditional blacklist (note: implemented BEFORE the isValidPropPath check
  rather than after as plan text said — same error code and param, observably
  equivalent; auditor confirmed non-deviation).
- Test verification (all PASS, non-hollow assertions confirmed):
  TestAESTextCipherEmptyKeyWarn 3/3 (incl. A→B legacy construction),
  TestCompositeKeyManager 2/2, TestKeyManager.testDestroyZeroesStorePassword
  (capture-before-destroy reflection), TestFilterBeanToSQLTransformerMetachar
  4/4; `_tmp/security-audit/fixbatch1-tests.log` EXIT:0, zero failing lines,
  full 4-module reactor green.
- Anti-Hollow: WARN reachable end-to-end via public encrypt()/decrypt()
  (tests trigger through public API, not direct build calls); blacklist
  covers all three interpolation points (owner/name/valueName) in
  visitCompareOp; auditor independently re-ran the safety grep (2 main
  callers, both true; zero false constructors outside the new test); no
  empty-body/no-op introduced.
- Docs/roadmap: ioc-and-config.md production note verified;
  tenant-model.md real-API seam verified; docs-for-ai has zero remaining
  `executeNativeSql` references; roadmap item 10 + auth note + item-4 nop-dyn
  scope verified; other item statuses untouched.
- Deferred honesty: all five deferred/adjudicated entries verified with
  reasons (incl. plan-344 P2-9 precedent re-confirmed at plans/344:83;
  F-C5-2 evidence not overstated).
- `node ai-dev/tools/check-plan-checklist.mjs <this-plan relative path>
  --strict` exit 0 (auditor-run and re-run post-completion).
- `node ai-dev/tools/check-doc-links.mjs --strict` exit 0 (auditor-run).

Follow-up:

- Items 2-8 audits and item 9 consolidation remain on the roadmap; residual
  watch items (SqlBuilder render boundary, by-PK load-SQL test assertion,
  cipher strict mode, plugin strict-hash mode) stay with item 9 triage.
  No remaining plan-owned work.
