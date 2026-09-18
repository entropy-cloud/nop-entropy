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
with independent closure evidence recorded in its `## Closure

Status Note: Closed 2026-09-19 after a three-round closure arc: (1) first
audit correctly DENIED while the plan was unexecuted (upstream dependency
unsatisfied); (2) after full execution (six AUTH reports, all phases), the
second audit DENIED on one genuine evidence defect — my hollow-scan "exit 0"
for nop-biz-auth-core was a false negative from a short module name (empty
scan of a nonexistent path); (3) remediation executed via fix batch 3
(roadmap item 12, ILoginService guard-wording per the auditor's prescription,
disposition + log correction landed) and this combined re-audit returned
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
