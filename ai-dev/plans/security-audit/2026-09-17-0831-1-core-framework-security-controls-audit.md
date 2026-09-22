# Security Audit Plan 1 — Core Framework Controls Assurance Audit

> Plan Status: completed
> Last Reviewed: 2026-09-18
> Mission: security-audit
> Work Item: 1. 核心框架安全审计 (`nop-core-framework`, `nop-persistence`, `nop-kernel`)
> Source: `ai-dev/backlog/security-audit-roadmap.md` (item 1, stages CORE-01..CORE-05)
> Related: `ai-dev/plans/328-security-hardening-remediation-planning.md`,
> `ai-dev/plans/334-encrypted-value-format.md`,
> `ai-dev/plans/345-xpath-xtransform-hardening.md`,
> `ai-dev/plans/344-check-audit-p1-p2-p3-remediation.md`,
> `docs-for-ai/02-core-guides/auth-and-permissions.md`,
> `docs-for-ai/02-core-guides/tenant-model.md`,
> `docs-for-ai/04-reference/safe-api-reference.md`
> Naming: date-prefixed file names are the established convention inside
> `ai-dev/plans/security-audit/` (mission subdirectory); guide rule 21's
> `NN-` numbering applies to the `ai-dev/plans/` root only.

## Purpose

Verify (read-only assurance audit) that the security controls of the framework
core — key management, plugin artifact integrity, IoC/config value injection,
ORM/EQL SQL generation, and tenant isolation — actually enforce their documented
contracts, and produce a structured findings report (severity, location,
recommendation) that becomes the baseline for later remediation plans. This plan
is the owner of roadmap item 1 (CORE-01..CORE-05) and must be closed before
roadmap items 2-8 can proceed to consolidation.

This is an **audit-phase plan**: read-only analysis plus audit-report
deliverables. Per the roadmap rule "不过早修复", no product code is changed in
this plan; findings land in the report and are remediated only in Phase 3 of
the roadmap (dynamically generated after consolidation item 9).

## Current Baseline

Verified against live repo on 2026-09-17, re-baselined 2026-09-18 after
adversarial draft review:

- Roadmap item 1 is `todo` and this plan is its owner (still `draft`).
  Sibling plans now exist in `ai-dev/plans/security-audit/`: the item 2 auth
  plan (`active`, Phase 1 blocked on this item) and the item 3 credential
  plan (`draft`, also gated on item 1). Roadmap item 2 is `planned`; items
  3-8 remain `todo`.
- Landed controls to be verified (not rebuilt): `IKeyManager` /
  `DefaultKeyManager` / `CompositeKeyManager` /
  `nop-core-framework/nop-security/src/main/java/io/nop/security/key/`
  (`IKeyManager.java:8` — certificate/private-key lookup contract);
  `HttpPluginResourceResolver` SHA256 chain (header → `.sha256` →
  `expectedHashes` map, fail-fast `ERR_PLUGIN_SHA256_MISMATCH` /
  `ERR_PLUGIN_CHECKSUM_NOT_AVAILABLE`, documented in
  `docs-for-ai/03-modules/nop-plugin.md:141-167`); `AESTextCipher` v1
  per-message-IV format (plan 334 completed 2026-08-08, DR-2a/DR-2b approved);
  `@sec:` config decryption chain (`ConfigStarter` →
  `DefaultConfigValueEnhancer` → `AESTextCipher`,
  `docs-for-ai/02-core-guides/ioc-and-config.md:234-274`); PMD security rules
  `HardCodedCryptoKey` / `InsecureCryptoIv` (`pmd-ruleset.xml`, root
  `pom.xml:469-533` qa profile).
- Tenant isolation is documented as a hard ORM-layer guarantee
  (`docs-for-ai/02-core-guides/tenant-model.md`): EQL compile-time filter
  (`EqlTransformVisitor`), SQL generation (`GenSqlHelper`), load/save fill
  (`EntityPersisterImpl.processTenantId()`,
  `OrmEntityIdGenerator.initTenantId()`), session cache partitioning
  (`TenantOrmSessionEntityCache`), cross-tenant error
  `ERR_ORM_NOT_ALLOW_PROCESS_ENTITY_IN_OTHER_TENANT`; escape hatch
  `ContextProvider.runWithoutTenantId()`; native SQL bypasses EQL compile-time
  filtering by design.
- Deferred adjudications that must NOT be re-litigated here: XLang DSL
  sandboxing (plan 328: trusted developer-controlled artifacts, sandbox only if
  a non-developer DSL editing contract appears); plugin P2-9 strict-hash mode
  and P2-6 cross-thread constructor-ring (plan 344 audit reports, adjudicated
  暂缓 with recorded reasons); plan 333/335/336 auth/browser, Bash isolation,
  and HTTP SSRF boundaries are separate owners (roadmap items 2/6/7 own their
  re-verification).
- Known supported-baseline gaps recorded in the roadmap (audit must verify, not
  fix): `nop.auth.enable-action-auth=false` and admin skip-check defaults
  (item 2 scope); JWT enc-key empty default and weak password policy (item 2
  scope); test/development default secrets reclassified as deployment
  configuration constraints (plan 328 Phase 1).
- Framework QA static analysis (`./mvnw checkstyle:check -Pqa`,
  `./mvnw pmd:check -Pqa`, spotbugs) is available repo-wide with generated
  code excluded; it is evidence input for this audit, not a substitute for
  manual control-effectiveness review.

## Goals

- CORE-01: Verify key-management control effectiveness — `IKeyManager`
  implementations resolve keys/certificates only through configured sources,
  private keys are not exposed through public seams, and key-set rotation
  semantics match the documented contract. Contract carrier: `IKeyManager`
  Javadoc plus implementation behavior and focused tests
  (`docs-for-ai/03-modules/` has no dedicated nop-security owner doc).
- CORE-02: Verify plugin artifact integrity end to end — download → cache →
  SHA256 verify → classloader load, including that no code path loads an
  unverified artifact (cache reuse, `.sha256`-missing legacy cache, and
  skip-cache-verify escape hatch included).
- CORE-03: Verify IoC/config injection safety — `@cfg:` / `@sec:` value
  resolution does not leak secrets into logs/error params, and bean wiring
  cannot silently bypass the documented configuration precedence.
- CORE-04: Analyze the ORM/EQL injection surface — EQL compilation, SQL
  generation, native-SQL escape hatches, and order-by/column interpolation
  paths; classify each dynamic-SQL entry point as parameterized, validated, or
  vulnerable.
- CORE-05: Verify tenant-isolation enforcement on every documented entry point,
  including the `runWithoutTenantId()` call sites (each must be justified and
  scoped) and native-SQL usage that bypasses compile-time tenant filters.
- Produce `ai-dev/audits/security-audit/` reports for all five deliverables
  with severity-classified findings (CRITICAL/HIGH/MEDIUM/LOW), each with
  source anchor, rationale, and remediation suggestion, ready for roadmap
  item 9 consolidation.

## Non-Goals

- No product code changes, no fixes, no configuration hardening — remediation
  happens only in dynamically generated roadmap Phase 3 items.
- No re-adjudication of deferred items owned elsewhere (XLang DSL sandboxing,
  plugin P2-9/P2-6, auth/browser boundary plan 333, Bash isolation plan 335,
  HTTP SSRF plan 336, dependency CVE scanning).
- No redesign of the plugin state machine, coeffect/reconcile semantics, or
  HMR lifecycle — only their security-relevant invariants (load/activate
  guardrails, unload guard, failure-threshold pause) are in scope.
- No authentication/session/MFA/credential-storage audit (roadmap items 2 and
  3 own those).
- No exhaustive framework-wide code-quality audit; only security-relevant
  control paths in the three target module groups.

## Scope

### In Scope

- Modules: `nop-core-framework/` (subgroups security, plugin, ioc, config,
  boot, log), `nop-persistence/` (nop-dao, nop-orm, nop-orm-eql,
  nop-orm-model, nop-orm-drivers and sibling persistence modules),
  `nop-kernel/` (nop-core resource/VFS layers consumed by the above; XLang
  codegen only insofar as generated-code security output is affected, see
  cross-cutting concerns in the roadmap).
- `ai-dev/audits/security-audit/` report files for CORE-01..CORE-05.
- Audit evidence collection: targeted greps (Java + XLang DSL resources),
  static analysis runs, focused reading of control classes and their test
  coverage, and run of existing ORM/IoC/plugin test suites to confirm controls
  behave as documented.
- Cross-cutting verification mandated by the roadmap: `@cfg:` secret-leak
  sampling and `_gen/` generated-code security spot checks for the three
  target module groups.

### Out Of Scope

- `nop-auth/`, `nop-service-framework/` (incl. GraphQL auth checking), AI
  tools, network/HTTP clients, file/credential modules — owned by roadmap
  items 2-8.
- Remediation of any confirmed finding (roadmap Phase 3).
- Third-party dependency CVE scanning (explicit plan 328 follow-up, separate).
- Modifying any `_`-prefixed generated file or `_gen/` output; any generated
  file change required by a later fix plan must regenerate from source models.

## Execution Plan

### Phase 1 - Evidence Collection And Static Analysis (CORE-01..CORE-05 inputs)

Status: completed
Targets: `nop-core-framework/`, `nop-persistence/`, `nop-kernel/` (read-only),
`_tmp/security-audit/` scratch outputs

- Item Types: `Proof | Follow-up`

- [x] Build the control inventory: enumerate every class that participates in
  key management (`io.nop.security.key`), plugin artifact resolution
  (`io.nop.plugin.manager.resolver`, `PluginClassLoader`), config value
  enhancement (`ConfigStarter`, `DefaultConfigValueEnhancer`,
  `AESTextCipher` usage sites), SQL generation (`GenSqlHelper`,
  `EqlTransformVisitor`, native-SQL entry points), and tenant fill/filter
  (tenant anchors in `docs-for-ai/02-core-guides/tenant-model.md`).
- [x] Run repo static analysis for the three module groups and capture output
  into `_tmp/security-audit/`:
  `./mvnw checkstyle:check -Pqa -pl nop-core-framework,nop-persistence,nop-kernel -am`,
  `./mvnw pmd:check -Pqa -pl nop-core-framework,nop-persistence,nop-kernel -am`,
  and
  `./mvnw spotbugs:check -Pqa -pl nop-core-framework,nop-persistence,nop-kernel -am`.
  The qa profile sets `failOnViolation=false` (checkstyle/pmd) and
  `failOnError=false` (spotbugs), so a green build does NOT mean zero
  findings — parse each module's checkstyle-result.xml, pmd.xml, and
  spotbugs report under the module target directory for violations; record
  any PMD security-rule hits (`HardCodedCryptoKey`, `InsecureCryptoIv`) as
  candidate findings.
- [x] Targeted secret-leak sweep: grep for `@cfg:` keys matching
  password/secret/key/token/encrypt across the three module groups and their
  `_vfs` DSL resources (include `.beans.xml`, `.xbiz`, `.xlib`, `.orm.xml` —
  plan 346 lesson: XLang DSL resources must be covered by greps, not only
  Java); flag keys whose values are logged, embedded in error params, or
  written to persistence.
- [x] Native-SQL / dynamic-SQL sweep: enumerate call sites of the real
  native-SQL entry points — `IOrmSession.executeUpdate` / `executeQuery` /
  `executeStatement` accepting `io.nop.core.lang.sql.SQL`,
  `JdbcQueryExecutor.executeQuerySql` / `executeUpdateSql`, `new SQL(`
  builder concatenation sites, and sql-lib native SQL items — plus
  `runWithoutTenantId` usage; record each with module, file, and caller
  context for Phase 2 classification. (`executeNativeSql` in
  `docs-for-ai/02-core-guides/tenant-model.md:84` is documentation
  pseudocode, not a real API — do not grep for it.)
- [x] Generated-code spot check: sample `_gen/` outputs of the three module
  groups and confirm no new injection/secret-hardcoding pattern is introduced
  beyond what source models declare.

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] Control inventory file exists under `_tmp/security-audit/` listing every
  control class with path and role, and each inventory entry is backed by an
  existing file path in the repo.
- [x] Static-analysis outputs for the three module groups are captured and
  each PMD security-rule hit is either registered as a candidate finding or
  explicitly dispositioned with a reason.
- [x] Secret-leak sweep covers Java sources AND XLang DSL resources of the
  three module groups; every flagged key has a disposition (leak / safe) with
  evidence anchor.
- [x] Native-SQL and `runWithoutTenantId` sweeps produce complete site lists
  (no unclassified site remains) that Phase 2 consumes.
- [x] Generated-code spot check has a recorded verdict per module group.
- [x] No product file was modified in this phase (verifiable via
  `git status --short` showing only `_tmp/`, `ai-dev/audits/`, and this plan
  file as new/changed).
- [x] No owner-doc update required: read-only evidence collection changes no
  live baseline.
- [x] `ai-dev/logs/` entry records phase completion with command outputs.

### Phase 2 - Control-Effectiveness Review And Findings (CORE-01..CORE-05)

Status: completed
Targets: `ai-dev/audits/security-audit/` reports

- Item Types: `Proof | Decision | Follow-up`

- [x] CORE-01 key-management review: trace `IKeyManager` consumers
  (certificate resolution, private-key access), verify `DefaultKeyManager` /
  `CompositeKeyManager` only surface keys from configured sources, key IDs
  cannot collide across sources unexpectedly, and document any path where a
  private key is exposed to callers that should not hold it.
- [x] CORE-02 plugin integrity review: verify the SHA256 chain (header →
  `.sha256` → `expectedHashes`) is enforced on download, on cache hit, and on
  legacy `.sha256`-missing cache; verify `skip-cache-verify` is the only
  bypass and is explicit; verify `PluginClassLoader` loads only the verified
  jar; check unload guard (`ERR_PLUGIN_NOT_DEACTIVATED`) and failure-threshold
  pause cannot be exploited to leave stale classes loaded.
- [x] CORE-03 IoC/config review: verify `@sec:` decryption happens only in the
  documented enhancer path and that decrypted values are not logged; verify
  `@Inject`/`@InjectValue` usage cannot inject into private fields or bypass
  configuration precedence in security-relevant beans.
- [x] CORE-04 injection-surface review: classify every native-SQL and
  dynamic-SQL site from Phase 1 as parameterized / validated / vulnerable;
  verify EQL compile-time protections (identifier quoting, tenant filter
  injection) cannot be bypassed via expression-based identifiers or DSL
  resources.
- [x] CORE-05 tenant-isolation review: verify each documented fill/filter
  entry point (save, load, session cache, collection load, EQL compile, SQL
  generation, DAO queries) enforces tenant context; audit every
  `runWithoutTenantId()` call site for justification; verify cross-tenant
  error paths fail closed; confirm native-SQL consumers within scope either
  include tenant predicates or are documented as deliberately unfiltered with
  an approved rationale.
- [x] Write one report per deliverable under `ai-dev/audits/security-audit/`
  (naming pattern: `{date}-core-audit-CORE-0N` where N=1..5), each with:
  scope, method, findings table (ID, severity CRITICAL/HIGH/MEDIUM/LOW, source
  anchor, description, remediation suggestion), and explicit "no finding"
  statements where controls verified clean.
- [x] Cross-check every confirmed finding against the plan-328 durable
  baseline, plans 333-336 ownership, and the sibling security-audit plans
  (item 2 auth, item 3 credential) so no finding is double-owned or silently
  dropped; record owner mapping in each report.

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] Five reports exist under `ai-dev/audits/security-audit/` (one per
  CORE-01..CORE-05), each containing a findings table with severities and
  source anchors resolvable to live files.
- [x] Every Phase 1 native-SQL / `runWithoutTenantId` site appears in exactly
  one report classification; no site is left unclassified.
- [x] Each finding carries a severity rationale and a remediation suggestion
  concrete enough for a Phase 3 fix plan to consume without re-analysis.
- [x] Owner mapping recorded: every confirmed finding names the successor
  remediation owner (or `deferred to consolidation` for item 9 triage), and
  no in-scope confirmed live defect is downgraded to a follow-up (Anti-Slacking
  Rule).
- [x] Existing focused tests covering audited controls pass:
  `./mvnw test -pl nop-core-framework/nop-plugin -am`,
  `./mvnw test -pl nop-persistence -am`, and
  `./mvnw test -pl nop-kernel/nop-core -am` (or documented module equivalents),
  with results recorded in the daily log.
- [x] No owner-doc update required: this phase writes audit evidence, not
  normative guidance; if any review reveals owner-doc drift (documented
  contract ≠ live behavior), record it as a MEDIUM finding "owner-doc drift"
  instead of silently editing the doc.
- [x] `ai-dev/logs/` entry updated.

### Phase 3 - Adjudication, Roadmap Sync, And Closure

Status: completed
Targets: `ai-dev/backlog/security-audit-roadmap.md`, this plan

- Item Types: `Decision | Proof`

- [x] Adjudicate every finding: assign CRITICAL/HIGH/MEDIUM/LOW per the
  roadmap severity order, re-check classifications against plan 328 precedent
  (test/development defaults are deployment-configuration constraints, not
  framework defects), and mark each finding `remediation-target` or
  `adjudicated-no-fix` with reason.
- [x] Update roadmap Work Items block: item 1 moves `todo` → `planned` at
  plan activation (before Phase 1 starts, already applied when Phase 3 runs)
  and `planned` → `done` only after closure audit passes; do not touch items
  2-9 statuses (they are other plans' work).
- [x] Feed the finding summary into roadmap item 9's future consolidation
  input by ensuring report files are self-contained (no `_tmp/`-only
  evidence; copy any needed scratch data into the reports).

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] Every finding in all five reports has a final adjudication status with
  recorded reason; zero findings remain "pending".
- [x] Roadmap Work Items block shows item 1 `done` at closure (following the
  documented `todo` → `planned` transition at activation and `planned` →
  `done` after closure audit) and no other item statuses changed by this
  plan.
- [x] All audit evidence referenced by the reports lives in
  `ai-dev/audits/security-audit/` (committed files), not solely in `_tmp/`.
- [x] No owner-doc update required (adjudication changes no supported
  runtime behavior); roadmap status block is the only dynamic state touched.
- [x] `ai-dev/logs/` entry updated.

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] All five deliverables (CORE-01..CORE-05 reports) exist, are
  severity-classified, and contain repo-resolvable source anchors.
- [x] Every confirmed in-scope finding has a recorded adjudication and
  remediation owner; nothing silently deferred.
- [x] Roadmap item 1 marked `done` strictly after independent closure audit.
- [x] Independent sub-agent closure audit completed with evidence recorded in
  `## Closure` (per guide rules 12/18/19).
- [x] Anti-Hollow check: closure audit verified the audit reports are grounded
  in live code paths (each finding's anchor re-verified against the repo) and
  that no "finding" is a placeholder or copied stale conclusion from prior
  plans without re-verification.
- [x] Textual consistency check: `Plan Status`, per-phase Status, per-phase
  Exit Criteria, Closure Gates, and daily log all agree.
- [x] `node ai-dev/tools/check-plan-checklist.mjs <this-plan-file> --strict`
  exits 0 before `Plan Status` flips to `completed` (guide Minimum Rule 26;
  the tool also verifies Closure Evidence is written into this file).
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0 (doc-only
  plan; build/test gates omitted per guide's pure-doc-plan allowance — test
  runs in Phase 2 are audit evidence, not code changes).

## Deferred But Adjudicated

### Re-verification of plans 333/335/336 controls (auth boundary, Bash isolation, HTTP SSRF)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: those boundaries belong to roadmap items 2, 6,
  and 7 respectively; this plan only records pointer references where CORE
  findings overlap, avoiding double ownership.
- Successor Required: `no`
- Successor Path: `N/A`

### Dependency CVE scan

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: recorded as a non-blocking follow-up by plan 328;
  requires separate tooling decisions independent of this audit.
- Successor Required: `no`
- Successor Path: `N/A`

## Non-Blocking Follow-ups

- If the CORE-02 plugin review surfaces test-coverage gaps (not live defects),
  record them here as `optimization candidate` for the fix phase.
- Capture any reusable audit grep patterns into `ai-dev/skills/` if they
  prove broadly applicable to later module audits.

## Closure

Status Note: Audit-phase plan closed after independent closure audit returned
ALLOW. All three phases (evidence collection, control-effectiveness review with
five durable reports, adjudication with owner mapping) verified against live
repo by a fresh-session subagent; zero CRITICAL/HIGH findings; every finding
adjudicated (`remediation-target` / `adjudicated-no-fix` / `watch-only
residual`) with recorded reasons and successor owner (roadmap item 9
consolidation). No product code was changed (audit-phase plan; roadmap rule
不过早修复).
Completed: 2026-09-18

Closure Audit Evidence:

- Reviewer / Agent: independent closure auditor subagent, fresh session
  (agent_8219ab52-e8b5-449b-8f06-7810c134cba7), 45 tool uses against live
  repo, verdict **ALLOW**.
- Exit Criteria: all 20 checked criteria across Phases 1-3 re-verified PASS
  with independent evidence (report files, `git status` cleanliness,
  `_tmp/security-audit/phase2-tests.log` 3× exit 0 with zero failing lines,
  per-report finding/adjudication count).
- Closure Gates: all 8 pre-verified PASS by the auditor (gate 4 satisfied by
  this evidence record itself; gates 7-8 tool exits below).
- Anti-Hollow check: 10 anchor spot-checks all consistent with live code
  (AESTextCipher DEFAULT_V1_SALT/DEFAULT_IV/encKey-empty default,
  ConfigStarter no-warn path, orm-defaults.beans.xml:36 wiring,
  HttpPluginResourceResolver hash priority, FilterBeanToSQLTransformer
  validate-before-interpolate, isValidPropPath charset,
  EntityPersisterImpl fail-closed tenant checks, EQL tenant predicate) + 2
  no-finding statement replays (IKeyManager zero consumers — grep zero
  matches; checkVarName=true at both call sites — stronger than claimed).
  No placeholder or stale-conclusion findings detected.
- Deferred-classification honesty: F-C2-1 prior adjudication confirmed in
  plan 344 (P2-9 recorded deferral); F-C5-2 residual rationale verified
  (enforcement exists in code); no in-scope live defect downgraded.
- `node ai-dev/tools/check-plan-checklist.mjs <this-plan-file> --strict`
  exit 0 (verified pre- and post-completion).
- `node ai-dev/tools/check-doc-links.mjs --strict` exit 0 (0 errors; the one
  plan-file warning was eliminated by rephrasing the backticked path).
- Known deviation notes recorded in daily log: spotbugs prefix needed full
  GAV invocation; baseline contained 4 pre-existing unrelated product-file
  edits (timestamped 12h before Phase 1, documented in log).

Follow-up:

- Findings feed roadmap item 9 consolidation (F-C3-1 MEDIUM + 7 LOW
  remediation-targets; F-C2-1 pointer to plan-344 P2-9; F-C5-1 asks item 9
  to assign an owner for nop-dyn); no direct remediation work owned by this
  plan beyond report completion.
