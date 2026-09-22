# Security Audit Plan 2 — Authentication And Authorization Controls Assurance Audit

> Plan Status: completed
> Last Reviewed: 2026-09-18
> Mission: security-audit
> Work Item: 2. 认证授权审计 (`nop-auth`, `nop-biz-auth-core`)
> Source: `ai-dev/backlog/security-audit-roadmap.md` (item 2, stages AUTH-01..AUTH-06)
> Related: `ai-dev/plans/security-audit/2026-09-17-0831-1-core-framework-security-controls-audit.md`
> (dependency gate), `ai-dev/plans/328-security-hardening-remediation-planning.md`,
> `ai-dev/plans/333-auth-browser-boundary.md` (completed hardening, re-verified here),
> `docs-for-ai/02-core-guides/auth-and-permissions.md`,
> `docs-for-ai/03-modules/nop-auth.md`

## Purpose

Read-only control-effectiveness audit of the authentication and authorization
surface (roadmap item 2): verify that the documented JWT purpose-isolation,
password-policy, MFA, session-management, RBAC and SSO contracts are actually
enforced in live code and tests, and produce severity-classified findings
reports ready for roadmap item 9 consolidation. Audit phase only — no product
changes (roadmap rule 不过早修复; fixes happen in dynamically generated
Phase 3 items).

## Current Baseline

Verified against live repo on 2026-09-17:

- Roadmap item 2 is `todo`; item 1 has draft plan
  `ai-dev/plans/security-audit/2026-09-17-0831-1-core-framework-security-controls-audit.md`
  (reviewed again 2026-09-18: still draft). Activation approves this plan's
  specification only; execution waits for item 1 to be `done` with its owner
  plan `completed`, independent closure evidence, and durable CORE reports.
  An active upstream plan or scratch inventory alone does not satisfy the
  roadmap dependency.
- Landed hardening to be verified (not rebuilt) per plan 333 closure
  (completed 2026-08-08): JWT per-purpose KID split keys + `typ` claim + iss/aud
  checks + legacy grace window (`TestJwtAuthTokenProvider`,
  `TestJwtHelper` in `nop-biz-auth-core`); admin-skip default unified `false`
  (`DefaultActionAuthChecker`, `NopAuthConfigs`); `servicePublic` SYS =
  anonymous principal, tenant header not trusted
  (`AuthHttpServerFilter`, DR-1b); strict relative-redirect + `__Host-` cookie
  defaults (DR-1c); password baseline min 12 + upper/lower/digit/special
  (`DefaultPasswordPolicy`, `TestPasswordPolicyBaseline`).
- MFA stack (plans W4-W15, A2/A3 audits; all completed per
  `ai-dev/backlog/nop-credential-mfa-roadmap.md`): two-phase login
  (`LoginServiceImpl.checkMfaRequired`/`mfaVerifyAsync`), challenge stores
  (db/local/redis), `MfaFactorVerifier`, WebAuthn W14, email factor + trusted
  device W15, restricted sessions W13, MFA CRUD lockdown
  (`TestMfaCrudLockdownE2E` et al.).
- Known gaps from roadmap "已知安全缺口" that THIS plan must verify and report
  (not fix): `nop.auth.enable-action-auth=false` default;
  `nop.auth.skip-check-for-admin=false` default behavior confirmation; JWT
  enc-key empty default; password policy override path; default seed user
  `nop/123` (deployment-configuration constraint per plan 328, not a code
  defect).
- Conditional residuals with open ownership that must NOT be re-litigated as
  new findings (record as pointers only): W14 design §七 deferred items
  (`ai-dev/design/nop-auth/02-mfa-phase2-design.md` 7.1-7.7), W8 store batch
  cleanup trigger, watch-only items in
  `ai-dev/audits/2026-08/2026-08-18-1244-deep-audit-nop-auth/adjudication.md`.
- Scope boundary: `AuthHttpServerFilter`, `AuthFilterConfig` and
  `LoginServiceImpl` live in `nop-biz-auth-core` / `nop-auth-service`; GraphQL
  engine auth checking is roadmap item 4 (API-02), tenant isolation is item 1
  (CORE-05). This plan audits the auth modules' own enforcement, not the
  GraphQL engine or ORM layer.

## Goals

- AUTH-01: Verify JWT token lifecycle security — per-purpose key separation,
  `typ`/`iss`/`aud` enforcement on every consumer, expiry, legacy grace
  window bounds, and enc-key empty-default handling.
- AUTH-02: Verify password-policy enforcement — baseline defaults on
  `DefaultPasswordPolicy`, bean wiring, override path, and that seeded-user
  bypass (`encodePassword` direct-write) is documented-only, not exploitable
  through supported flows.
- AUTH-03: Verify MFA timing/flow integrity — two-phase login challenge
  discipline (scene/verifiedAt), fail-count/cooldown, replay protection
  (TOTP window, WebAuthn signCount), trusted-device exemption boundaries.
- AUTH-04: Verify session management — user-context caches
  (`LocalUserContextCache`/`DaoUserContextCache`), cookie attributes,
  auto-refresh half-life logic, logout invalidation semantics.
- AUTH-05: Verify RBAC bypass risk — action-auth default-off implications,
  admin skip-check single source of truth, service-public SYS principal
  scope, data-auth checker fail-closed behavior.
- AUTH-06: Verify SSO/OAuth integration security — `OAuthLoginServiceImpl`
  MFA SPI wiring, `JWKPublicKeyLocator` key resolution, redirect/state
  handling, hollow-scan watch item `generateVerifyCode`
  (`OAuthLoginServiceImpl.java:230-235`) re-verification.
- Produce `ai-dev/audits/security-audit/` reports for AUTH-01..AUTH-06 with
  severity-classified findings (CRITICAL/HIGH/MEDIUM/LOW), each with source
  anchor, rationale, remediation suggestion.

## Non-Goals

- No product code changes, no config hardening — remediation happens only in
  dynamically generated roadmap Phase 3 items.
- No re-audit of GraphQL engine auth checking (item 4 / API-02), ORM tenant
  isolation (item 1 / CORE-05), or credential storage (item 3).
- No re-adjudication of W14 §七 deferred features (attestation trust chain,
  external MFA providers, batch cleanup triggers) — pointer references only.
- No exploit development or payload reproduction; control verification is by
  code-path tracing, existing-test inspection, and focused test runs.
- No dependency CVE scanning (plan 328 non-blocking follow-up, separate).

## Scope

### In Scope

- Modules: `nop-auth/` (nop-auth-service, nop-auth-sso, nop-auth-dao,
  nop-auth-web, nop-oauth, nop-auth-api), `nop-service-framework/nop-biz-auth-core/`,
  `nop-service-framework/nop-biz-auth-api/`.
- `ai-dev/audits/security-audit/` report files for AUTH-01..AUTH-06.
- Audit evidence collection: targeted greps (Java + `_vfs` DSL resources such
  as `auth-service.beans.xml`, dicts, xmeta), QA static analysis runs,
  focused reading of control classes, and runs of existing auth test suites.
- Cross-cutting verification mandated by the roadmap: `@cfg:` secret-leak
  sampling in auth modules; `_gen/` generated-code spot check for auth
  modules.

### Out Of Scope

- `nop-service-framework/nop-graphql/`, AI tools, network clients,
  file/credential modules — owned by roadmap items 4, 6, 7, 8.
- Remediation of any confirmed finding (roadmap Phase 3).
- Modifying any `_`-prefixed generated file or `_gen/` output.
- New security feature design (W14 §七 backlog stays in design doc).

## Execution Rules And Evidence Contract

- This review changes the plan only; no execution checkbox is satisfied by
  draft approval. At execution entry, synchronize only roadmap item 2 to
  `planned`, preserving unrelated workspace edits. Capture starting git status
  and compare task-owned changes against that baseline, not a clean-worktree
  assumption. Do not commit unless explicitly requested.
- Before collecting evidence, read `ai-dev/audits/README.md` and the owner
  documents in Related completely. Reports use the mission output directory
  `ai-dev/audits/security-audit/`; each report includes a summary and both
  CRITICAL/HIGH/MEDIUM/LOW and P0/P1/P2/P3 severity labels respectively.
- Each report records revision, reviewed configuration, exact commands, exit
  codes, test counts and skipped suites, scope exclusions, coverage matrix,
  source anchors, and sanitized results. Never persist real secret values,
  tokens, credentials, or sensitive request/response bodies in logs or reports.
- Audit discovery is `Proof`/`Decision`; any confirmed defect or owner-doc
  drift becomes a `Fix` record owned explicitly by
  `ai-dev/backlog/security-audit-roadmap.md`, item 9 (consolidation and creation
  of Phase 3 remediation plans). Record finding ID, affected owner doc/module,
  remediation direction, and verification expectation. This is an explicit
  handoff outside this read-only plan, not a non-blocking follow-up or a claim
  that the defect was repaired. Bare `deferred to consolidation` is insufficient.
- Historical no-fix decisions apply only while their assumptions still match
  live evidence. New regressions, changed exposure, or failed assumptions must
  be reported and adjudicated, not suppressed by prior ownership. Severity is
  evidence-based; owner-doc drift is not automatically MEDIUM.
- Failed or unexecuted required verification blocks the affected phase and
  closure. Record the failed module, current diagnostics, and intended suites
  that never ran; fix only environment/setup within authorized scope and rerun.
  Do not waive a gate as pre-existing based solely on plan 333. If remediation
  or a verification-policy change is required, stop for owner approval; no
  product edits, gate weakening, or silent test skips are authorized here.
- No new test required in any phase: this plan changes audit documentation
  only and runs existing tests. Coverage gaps remain explicit limitations,
  never evidence of a clean control. No new component wiring is introduced.

## Execution Plan

### Phase 1 - Dependency Gate And Evidence Collection

Status: completed

Blocker resolved (2026-09-18): upstream plan
`2026-09-17-0831-1-core-framework-security-controls-audit.md` is `completed`
with independent closure evidence recorded in its Closure section, roadmap
item 1 is `done`, and the five durable CORE reports exist (commit 54a6393a05).
The dependency gate was satisfied; auth evidence collection ran 2026-09-18.
See [execution log](../../logs/2026/09-18.md).
Targets: `nop-auth/`, `nop-service-framework/nop-biz-auth-core/` (read-only),
`_tmp/security-audit/` scratch outputs

- Item Types: `Proof | Follow-up`

- [x] Dependency gate: confirm roadmap item 1 plan
  `2026-09-17-0831-1-core-framework-security-controls-audit.md` is completed,
  item 1 is done, and independent closure evidence plus durable CORE reports
  exist. Record paths and results in the daily log. Until then mark Phase 1
  blocked and stop evidence collection; never infer readiness from activation.
- [x] Build the auth control inventory: enumerate classes implementing
  login/session/JWT/password/MFA/RBAC/SSO controls with paths and roles
  (start from anchors in `docs-for-ai/02-core-guides/auth-and-permissions.md`
  "源码锚点" and `docs-for-ai/03-modules/nop-auth.md`), writing
  `_tmp/security-audit/auth-inventory.md`.
- [x] Run QA static analysis for the auth modules and capture output into
  `_tmp/security-audit/`: `./mvnw checkstyle:check -Pqa -pl nop-auth,nop-service-framework/nop-biz-auth-core -am`
  and `./mvnw pmd:check -Pqa -pl nop-auth,nop-service-framework/nop-biz-auth-core -am`;
  also run `./mvnw compile spotbugs:check -Pqa -pl nop-auth,nop-service-framework/nop-biz-auth-core -am`.
  Confirm Maven wrapper reactor selection includes all intended child modules;
  record security-rule hits as candidates and retain exit codes for all checks.
- [x] Generated-code spot check: sample auth `_gen/` classes and generated
  xmeta/action-auth resources against source models and retained overrides.
  Record sampled paths and sensitive-field/CRUD exposure verdicts; never edit
  generated files.
- [x] Tenant-boundary sampling: trace auth-owned principal-to-tenant binding,
  session/cache partitioning and user/role lookups, linking ORM guarantees to
  CORE-05 rather than repeating its implementation audit.
- [x] Secret-leak sweep: grep `@cfg:`/`@sec:` keys matching
  password/secret/key/token across auth modules AND their `_vfs` resources
  (`.beans.xml`, dicts, `application.yaml` samples); flag keys whose values
  are logged or embedded in error params.
- [x] Enumerate config-default inventory for the roadmap's four known gaps
  (`enable-action-auth`, `skip-check-for-admin`, JWT enc-key, password
  policy) with live file anchors for Phase 2 adjudication.
- [x] Existing-test coverage map: list the auth test files (e.g.
  `TestJwtAuthTokenProvider`, `TestPasswordPolicyBaseline`,
  `TestMfaLoginE2E`, `TestMfaCrudLockdownE2E`, `TestOAuthLoginServiceImplContract`)
  and which control each covers; note uncovered control paths.

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] Dependency gate satisfied: item 1 done, owner plan completed, closure
  evidence and durable CORE reports verified and linked in the daily log.
- [x] Auth control inventory exists under `_tmp/security-audit/`, every entry
  backed by an existing repo path, covering AUTH-01..AUTH-06 control classes.
- [x] Checkstyle, PMD and SpotBugs outputs captured for intended modules;
  required checks exit 0 and each security-rule hit is registered or
  dispositioned with evidence. Nonzero checks block, even if pre-existing.
- [x] Generated-code and auth tenant-boundary sampling have path-level verdicts
  mapped into AUTH-03/04/05 and cross-owner references where applicable.
- [x] Secret-leak sweep covers Java AND `_vfs` DSL resources of auth modules;
  every flagged key has disposition (leak / safe) with evidence anchor.
- [x] Config-default inventory for the four known gaps complete with live
  anchors (file:line) ready for Phase 2 classification.
- [x] Test-coverage map lists all control areas; every area maps to at least
  one existing test or an explicit gap note.
- [x] No task-owned product change relative to recorded starting git status;
  only scratch evidence, reports, this plan, item-2 roadmap state and daily
  log are changed by this task. Pre-existing unrelated changes are preserved.
- [x] No owner-doc update required: read-only evidence collection changes no
  live baseline.
- [x] `ai-dev/logs/` entry records phase completion with command results.

### Phase 2 - Control-Effectiveness Review And Findings (AUTH-01..AUTH-06)

Status: completed
Targets: `ai-dev/audits/security-audit/` reports

- Item Types: `Proof | Decision | Follow-up`

- [x] AUTH-01 review: trace token issue/consume paths (`JwtAuthTokenProvider`,
  `JwtHelper`, `AuthHttpServerFilter.parseAuthToken`,
  `LoginServiceImpl` refresh paths); confirm purpose isolation, KID/key
  handling, expiry, grace-window bounds, and enc-key empty-default behavior;
  cross-check against `TestJwtAuthTokenProvider` assertions; include supported
  key rotation/retirement semantics, not just purpose-key separation.
- [x] AUTH-02 review: verify `DefaultPasswordPolicy` bean wiring
  (`auth-core-defaults.beans.xml`), baseline enforcement reachability from
  registration/change/admin-reset/recovery flows, reset authorization and
  one-time expiry semantics, encoder/hash/salt wiring and stored password
  exposure, and classify the seeded-user direct-write path. Password hashes
  belong here; credential-vault encryption remains item 3.
- [x] AUTH-03 review: verify MFA challenge discipline end to end — scene
  acceptance matrix, verifiedAt consumption, fail-count persistence and
  cooldowns, TOTP window update, WebAuthn signCount monotonicity, trusted
  device exemption entry conditions; confirm `MfaFactorVerifier` is the
  shared verification funnel with explicitly documented exceptions such as
  recovery codes. Include SMS/email expiry, resend limits and atomic consume
  across local/db/redis stores, recovery-code lifecycle, binding/unbinding,
  operation tickets, restricted-session policy and sensitive-table CRUD guards.
- [x] AUTH-04 review: verify user-context cache semantics (session fixation
  via cache key, expiry, serialize whitelist), auto-refresh half-life
  refresh loop, logout invalidation, and cookie attribute application
  points (auth + state cookies), idle/absolute timeouts, concurrent-session
  policy and its absence where unsupported. Cover HTTP path matching and
  precedence through `AuthFilterConfig` and the auth filter's actual wiring.
- [x] AUTH-05 review: verify admin skip-check has one source of truth and
  default-off; verify `servicePublic` SYS principal cannot satisfy
  admin-gated permissions; verify data-auth checker fail-closed default
  (`有规则但用户角色不匹配` → 拒绝) and action-auth off-default blast radius
  documented as deployment constraint.
- [x] AUTH-06 review: verify OAuth login MFA SPI wiring
  (`MfaLoginPolicyServiceImpl`), `JWKPublicKeyLocator` resolution fail
  behavior, redirect/state validation, and disposition the
  `generateVerifyCode` watch item with current evidence. Separately inventory
  `nop-oauth` server endpoints and client-registration/authorization/consent
  controls; map each implemented surface to a review outcome or explicit
  unsupported/coverage-gap disposition, without treating SSO-client review as
  coverage of the OAuth server.
- [x] Write one report per deliverable under `ai-dev/audits/security-audit/`
  (naming pattern: `{date}-auth-audit-AUTH-0N` where N=1..6), each with:
  scope, method, findings table (ID, severity, source anchor, description,
  remediation suggestion), and explicit "no finding" statements where
  controls verified clean.
- [x] Cross-check every confirmed finding against plan 333 closure state and
  the A2/A3 audit adjudications so no finding is double-owned or silently
  dropped; record owner mapping in each report.

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] Six reports exist under `ai-dev/audits/security-audit/` (one per
  AUTH-01..AUTH-06), each with findings table and repo-resolvable anchors.
- [x] Every Phase 1 inventory entry appears in exactly one report
  classification; no control path left unclassified.
- [x] Each finding carries severity rationale and remediation suggestion
  concrete enough for a Phase 3 fix plan without re-analysis.
- [x] Every confirmed finding has explicit Fix ownership and handoff fields
  required by the Evidence Contract; no live defect or doc drift is labeled
  a mere follow-up or fixed by report publication.
- [x] Existing focused auth tests pass:
  `./mvnw test -pl nop-service-framework/nop-biz-auth-core,nop-auth/nop-auth-service,nop-auth/nop-auth-sso -am`.
  Fresh Surefire results prove the coverage-map suites actually ran, including
  `TestOAuthLoginServiceImplContract`; skipped/unexecuted suites block evidence.
- [x] End-to-end evidence follows a supported login entry through MFA/session
  issuance to an authorized operation and logout/invalidation outcome using
  existing integration tests and live call-chain review. Record entry, wired
  beans, output assertions and uncovered boundaries; component existence alone
  does not prove enforcement. GraphQL internals remain item 4 ownership.
- [x] AUTH-01..06 coverage matrix explicitly accounts for key rotation,
  password storage/reset, SMS/email, session concurrency/timeouts, generated
  exposure, auth tenant binding, and both SSO-client and `nop-oauth` server
  surfaces. Unsupported behavior is a documented limitation, not assumed safe.
- [x] No owner-doc update required: this phase writes audit evidence, not
  normative guidance; any owner-doc drift discovered is recorded as a
  severity-justified "owner-doc drift" Fix record with explicit item-9
  ownership instead of a silent doc edit or non-blocking follow-up.
- [x] `ai-dev/logs/` entry updated.

### Phase 3 - Adjudication, Roadmap Sync, And Closure

Status: completed
Targets: `ai-dev/backlog/security-audit-roadmap.md`, this plan

- Item Types: `Decision | Proof`

- [x] Adjudicate every finding: assign CRITICAL/HIGH/MEDIUM/LOW, re-check
  classifications against plan 328 precedent (test/development defaults are
  deployment-configuration constraints, not framework defects) and A2/A3
  adjudication records; mark each finding `remediation-target` or
  `adjudicated-no-fix` with reason.
- [x] Verify item 2 was synchronized to `planned` at execution entry and
  prepare its closure handoff; do not mark it done in this phase or change
  other item statuses. The final closure pass owns the done transition.
- [x] Ensure report files are self-contained (no `_tmp/`-only evidence; copy
  needed scratch data into `ai-dev/audits/security-audit/` reports) so item 9
  consolidation can consume them.

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] Every finding across the six reports has a final adjudication status
  with recorded reason; zero findings remain "pending".
- [x] Roadmap item 2 is `planned`, with no task-owned changes to other item
  statuses; the post-audit `done` transition remains a Closure Gate, avoiding
  a cycle between Phase 3 completion and independent closure audit.
- [x] All audit evidence referenced by reports lives in durable repository
  `ai-dev/audits/security-audit/` files, not solely in `_tmp/`; report publication
  does not authorize a git commit.
- [x] No owner-doc update required (adjudication changes no supported runtime
  behavior); roadmap status block is the only dynamic state touched.
- [x] `ai-dev/logs/` entry updated.

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] All six deliverables (AUTH-01..AUTH-06 reports) exist, are
  severity-classified, and contain repo-resolvable source anchors.
- [x] Every confirmed in-scope finding has recorded adjudication and
  remediation owner; nothing silently deferred.
- [x] Roadmap item 2 marked `done` strictly after independent closure audit.
- [x] Independent sub-agent closure audit completed with evidence recorded in
  `## Closure` (per guide rules 12/18/19).
- [x] Anti-Hollow check: closure audit verified each finding's anchor
  re-verified against the live repo and no "finding" is a placeholder or
  stale conclusion copied from prior plans without re-verification.
- [x] Textual consistency check: `Plan Status`, per-phase Status, per-phase
  Exit Criteria, Closure Gates, and daily log all agree.
- [x] Roadmap verification baseline `./mvnw test -T 1C` passes after each
  completed phase; phase-level QA and focused tests remain mandatory audit
  evidence despite this being a doc-only change. Any failed gate blocks
  closure pending repair or an explicit owner-approved policy decision.
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-auth --severity high`
  and `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-service-framework/nop-biz-auth-core --severity high`
  exit 0; record actual results and disposition candidates without treating
  nonzero output as a pass.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.
- [x] After independent audit verifies completed phases and records results,
  mark roadmap item 2 done, synchronize closure evidence/log/status text and
  check satisfied gates immediately. Run
  `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/security-audit/2026-09-17-1958-2-auth-authorization-controls-audit.md --strict`
  with exit 0 before setting Plan Status completed; rerun after the final
  status update. Do not run execution tasks simply to satisfy this checker
  during draft review.

## Deferred But Adjudicated

### Re-audit of W14 design §七 deferred features (attestation chain, external MFA, batch cleanup)

- Classification: `watch-only residual`
- Why Not Blocking Closure: condition-triggered items already adjudicated in
  `ai-dev/design/nop-auth/02-mfa-phase2-design.md` (7.1-7.7) with Successor
  Required: no; this plan only records pointers where AUTH findings overlap.
- Successor Required: `no`
- Successor Path: `N/A`

### Default seed user and development-only defaults

- Classification: `watch-only residual`
- Why Not Blocking Closure: reclassified as deployment-configuration
  constraints by plan 328 (user-approved); production requirements are
  documentation/configuration responsibility, not framework-code defects.
  This plan still verifies the live defaults and reports actual behavior.
- Successor Required: `no`
- Successor Path: `N/A`

## Non-Blocking Follow-ups

- Capture reusable audit grep patterns into `ai-dev/skills/` if broadly
  applicable to later module audits (items 3-8).
- If AUTH-04 uncovers session-cache test-coverage gaps (not live defects),
  record them as `optimization candidate` for the fix phase.

## Closure

Status Note: Closed 2026-09-19 after a three-round closure arc: (1) first
audit correctly DENIED while the plan was unexecuted (upstream dependency
unsatisfied); (2) after full execution (six AUTH reports, all phases), the
second audit DENIED on one genuine evidence defect — the earlier hollow-scan
"exit 0" for nop-biz-auth-core was a false negative from a short module name
(empty scan of a nonexistent path); (3) remediation executed via fix batch 3
(roadmap item 12, ILoginService guard-wording per the auditor's prescription,
disposition + log correction landed) and the combined re-audit returned
ALLOW for both objects.
Completed: 2026-09-19

Closure Audit Evidence:

- Reviewer / Agent: independent fresh-session closure auditors — round 2
  (agent_ca33e54b, DENY with 4 remediation items), round 3 combined re-audit
  (agent_ec387612, **ALLOW** for item 2 and fix batch 3).
- Round-2 DENY remediation all PASS: (B1) literal hollow-scan command exits 0
  with guard-wording verified at L45/84/94/104 + anti-false-negative
  cross-validation; (B2) AUTH-04 disposition cites the complete
  implementation chain (AbstractLoginService:95, LoginServiceImpl:589/870/904)
  with green tests (TestRevokeUserSessions 2/2, TestMfaLoginE2E 10/10);
  (B3) authorization chain Fix-record → fix batch 3 recorded in three places;
  (B4) 09-18 log CORRECTED with root cause and lesson.
- Round-3 also re-verified: six AUTH reports on disk with 5 findings all
  adjudicated (F-A2-1/F-A6-1 FIXED-in-batch-2; F-A2-2 deployment constraint;
  F-A4-1/F-A6-2 watch-only); roadmap item 2 was `planned` pre-closure;
  `_tmp/security-audit/full-reactor-tests3.log` FULL-EXIT:0 BUILD SUCCESS
  (2926 suites, zero failures — satisfies the `./mvnw test -T 1C` gate);
  check-plan-checklist and check-doc-links exit 0.
- Roadmap item 2 `planned → done` executed strictly AFTER this evidence was
  written (last step per auditor ordering).

Follow-up:

- Findings feed roadmap item 9 consolidation; F-A4-1/F-A6-2 residuals stay
  with item 9 triage. No remaining plan-owned work.
